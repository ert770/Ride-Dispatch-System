package com.uber.service;

import com.uber.exception.BusinessException;
import com.uber.model.*;
import com.uber.repository.DriverRepository;
import com.uber.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 訂單服務 - 核心業務邏輯
 * 
 * 重要：accept 操作使用 synchronized 確保併發安全 (H2)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final DriverRepository driverRepository;
    private final AuditService auditService;
    private final FareService fareService;
    
    // 用於 accept 操作的鎖
    private final ReentrantLock acceptLock = new ReentrantLock();
    
    /**
     * 建立叫車請求
     */
    public Order createOrder(String passengerId, Location pickup, 
                            Location dropoff, VehicleType vehicleType) {
        // 驗證上下車點不可相同
        if (pickup.getX() == dropoff.getX() && pickup.getY() == dropoff.getY()) {
            throw new BusinessException("INVALID_REQUEST", "上車地點與下車地點不可相同");
        }
        
        double distance = pickup.distanceTo(dropoff);
        double estimatedFare = fareService.calculateEstimatedFare(vehicleType, distance);
        
        Order order = Order.builder()
                .orderId(UUID.randomUUID().toString())
                .passengerId(passengerId)
                .status(OrderStatus.PENDING)
                .vehicleType(vehicleType)
                .pickupLocation(pickup)
                .dropoffLocation(dropoff)
                .estimatedFare(estimatedFare)
                .distance(distance)
                .createdAt(Instant.now())
                .build();
        
        orderRepository.save(order);
        
        auditService.logSuccess(order.getOrderId(), "CREATE", "PASSENGER", 
                passengerId, null, "PENDING");
        
        log.info("Order created: {}", order.getOrderId());
        return order;
    }
    
    /**
     * 接受訂單 (H2: 併發安全)
     * 
     * 使用 ReentrantLock 確保同一時間只有一位司機能成功接單
     */
    public Order acceptOrder(String orderId, String driverId) {
        acceptLock.lock();
        try {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "訂單不存在"));
            
            // H4: 冪等性 - 若同一司機已接此單，直接回傳成功
            if (order.getStatus() == OrderStatus.ACCEPTED && 
                driverId.equals(order.getDriverId())) {
                log.info("Idempotent accept - order already accepted by same driver");
                return order;
            }
            
            // 檢查狀態是否為 PENDING
            if (order.getStatus() != OrderStatus.PENDING) {
                String reason = order.getStatus() == OrderStatus.ACCEPTED 
                        ? "ORDER_ALREADY_ACCEPTED" : "INVALID_STATE";
                String message = order.getStatus() == OrderStatus.ACCEPTED 
                        ? "此訂單已被其他司機接受" : "訂單狀態不允許接單操作";
                
                auditService.logFailure(orderId, "ACCEPT", "DRIVER", 
                        driverId, order.getStatus().name(), reason);
                
                if (order.getStatus() == OrderStatus.ACCEPTED) {
                    throw new BusinessException(reason, message, 409);
                } else {
                    throw new BusinessException(reason, message);
                }
            }
            
            // 檢查司機狀態
            Driver driver = driverRepository.findById(driverId)
                    .orElseThrow(() -> new BusinessException("DRIVER_NOT_FOUND", "司機不存在"));
            
            if (driver.getStatus() != DriverStatus.ONLINE) {
                auditService.logFailure(orderId, "ACCEPT", "DRIVER", 
                        driverId, "PENDING", "DRIVER_OFFLINE");
                throw new BusinessException("DRIVER_OFFLINE", "司機不在線");
            }
            
            if (driver.isBusy()) {
                auditService.logFailure(orderId, "ACCEPT", "DRIVER", 
                        driverId, "PENDING", "DRIVER_BUSY");
                throw new BusinessException("DRIVER_BUSY", "司機正在忙碌");
            }
            
            // 執行接單
            order.setStatus(OrderStatus.ACCEPTED);
            order.setDriverId(driverId);
            order.setAcceptedAt(Instant.now());
            orderRepository.save(order);
            
            // 更新司機狀態
            driver.setBusy(true);
            driver.setCurrentOrderId(orderId);
            driverRepository.save(driver);
            
            auditService.logSuccess(orderId, "ACCEPT", "DRIVER", 
                    driverId, "PENDING", "ACCEPTED");
            
            log.info("Order {} accepted by driver {}", orderId, driverId);
            return order;
            
        } finally {
            acceptLock.unlock();
        }
    }
    
    /**
     * 開始行程
     */
    public Order startTrip(String orderId, String driverId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "訂單不存在"));
        
        // H4: 冪等性
        if (order.getStatus() == OrderStatus.ONGOING) {
            return order;
        }
        
        if (order.getStatus() != OrderStatus.ACCEPTED) {
            throw new BusinessException("INVALID_STATE", "訂單狀態不允許開始行程");
        }
        
        if (!driverId.equals(order.getDriverId())) {
            throw new BusinessException("NOT_ASSIGNED_DRIVER", "您不是此訂單的指派司機", 403);
        }
        
        order.setStatus(OrderStatus.ONGOING);
        order.setStartedAt(Instant.now());
        orderRepository.save(order);
        
        auditService.logSuccess(orderId, "START", "DRIVER", 
                driverId, "ACCEPTED", "ONGOING");
        
        log.info("Trip started for order {}", orderId);
        return order;
    }
    
    /**
     * 完成行程
     */
    public Order completeTrip(String orderId, String driverId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "訂單不存在"));
        
        // H4: 冪等性
        if (order.getStatus() == OrderStatus.COMPLETED) {
            return order;
        }
        
        if (order.getStatus() != OrderStatus.ONGOING) {
            throw new BusinessException("INVALID_STATE", "訂單狀態不允許完成行程");
        }
        
        if (!driverId.equals(order.getDriverId())) {
            throw new BusinessException("NOT_ASSIGNED_DRIVER", "您不是此訂單的指派司機", 403);
        }
        
        // 計算實際車資
        Instant startTime = order.getStartedAt();
        Instant endTime = Instant.now();
        int duration = (int) ((endTime.toEpochMilli() - startTime.toEpochMilli()) / 60000);
        
        double fare = fareService.calculateFare(
                order.getVehicleType(), 
                order.getDistance(), 
                duration
        );
        
        order.setStatus(OrderStatus.COMPLETED);
        order.setCompletedAt(endTime);
        order.setDuration(duration);
        order.setActualFare(fare);
        orderRepository.save(order);
        
        // 釋放司機
        driverRepository.findById(driverId).ifPresent(driver -> {
            driver.setBusy(false);
            driver.setCurrentOrderId(null);
            driverRepository.save(driver);
        });
        
        auditService.logSuccess(orderId, "COMPLETE", "DRIVER", 
                driverId, "ONGOING", "COMPLETED");
        
        log.info("Trip completed for order {}, fare: {}", orderId, fare);
        return order;
    }
    
    /**
     * 取消訂單
     */
    public Order cancelOrder(String orderId, String cancelledBy) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "訂單不存在"));
        
        // H4: 冪等性
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return order;
        }
        
        // 驗證只有訂單擁有者可以取消
        if (!cancelledBy.equals(order.getPassengerId())) {
            throw new BusinessException("FORBIDDEN", "您無權取消此訂單", 403);
        }

        if (order.getStatus() != OrderStatus.PENDING &&
            order.getStatus() != OrderStatus.ACCEPTED) {
            throw new BusinessException("INVALID_STATE", "此訂單狀態無法取消");
        }
        
        String previousState = order.getStatus().name();
        double cancelFee = 0;
        
        // 已接單取消需計算取消費
        if (order.getStatus() == OrderStatus.ACCEPTED) {
            cancelFee = fareService.getCancelFee(order.getVehicleType());
            
            // 釋放司機
            if (order.getDriverId() != null) {
                driverRepository.findById(order.getDriverId()).ifPresent(driver -> {
                    driver.setBusy(false);
                    driver.setCurrentOrderId(null);
                    driverRepository.save(driver);
                });
            }
        }
        
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(Instant.now());
        order.setCancelledBy(cancelledBy);
        order.setCancelFee(cancelFee);
        orderRepository.save(order);
        
        auditService.logSuccess(orderId, "CANCEL", "PASSENGER", 
                cancelledBy, previousState, "CANCELLED");
        
        log.info("Order {} cancelled", orderId);
        return order;
    }
    
    /**
     * 查詢訂單
     */
    public Order getOrder(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "訂單不存在"));
    }
    
    /**
     * 取得所有待派單訂單
     */
    public java.util.List<Order> getPendingOrders() {
        return orderRepository.findByStatus(OrderStatus.PENDING);
    }
    
    /**
     * 取得所有訂單
     */
    public java.util.List<Order> getAllOrders() {
        return orderRepository.findAll();
    }
}
