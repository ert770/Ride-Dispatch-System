package com.uber.service;

import com.uber.model.*;
import com.uber.repository.DriverRepository;
import com.uber.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 配對演算法服務
 * 
 * 功能：
 * 1. 為訂單找到最佳匹配司機
 * 2. 為司機提供可接訂單列表
 * 
 * 配對規則：
 * 1. 司機必須為 ONLINE 且非 Busy
 * 2. 車種必須匹配
 * 3. 距離最近優先
 * 4. 距離相同時，ID 較小者優先 (tie-break)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MatchingService {
    
    private final DriverRepository driverRepository;
    private final OrderRepository orderRepository;
    
    // 預設搜尋半徑 (可由管理員配置)
    private double searchRadius = 10.0; // km
    
    /**
     * 為指定訂單找到最佳匹配司機
     * 
     * @param order 訂單
     * @return 最佳匹配的司機，若無則返回 empty
     */
    public Optional<Driver> findBestDriver(Order order) {
        if (order == null || order.getPickupLocation() == null) {
            return Optional.empty();
        }
        
        Location pickupLocation = order.getPickupLocation();
        VehicleType requiredType = order.getVehicleType();
        
        List<DriverCandidate> candidates = driverRepository.findAll().stream()
                // 篩選條件: ONLINE 且非 Busy
                .filter(driver -> driver.getStatus() == DriverStatus.ONLINE)
                .filter(driver -> !driver.isBusy())
                // 篩選車種
                .filter(driver -> driver.getVehicleType() == requiredType)
                // 檢查是否有位置資訊
                .filter(driver -> driver.getLocation() != null)
                // 計算距離並建立候選者
                .map(driver -> new DriverCandidate(
                        driver, 
                        driver.getLocation().distanceTo(pickupLocation)))
                // 過濾搜尋半徑範圍內的司機
                .filter(candidate -> candidate.distance <= searchRadius)
                .collect(Collectors.toList());
        
        if (candidates.isEmpty()) {
            log.info("No matching driver found for order {} with vehicle type {}", 
                    order.getOrderId(), requiredType);
            return Optional.empty();
        }
        
        // 排序: 距離優先，相同距離時 ID 較小者優先
        candidates.sort(Comparator
                .comparingDouble((DriverCandidate c) -> c.distance)
                .thenComparing(c -> c.driver.getDriverId()));
        
        Driver bestDriver = candidates.get(0).driver;
        log.info("Best driver found for order {}: {} (distance: {})", 
                order.getOrderId(), bestDriver.getDriverId(), candidates.get(0).distance);
        
        return Optional.of(bestDriver);
    }
    
    /**
     * 取得司機可接的訂單列表
     * 
     * @param driver 司機
     * @return 可接訂單列表，按距離排序
     */
    public List<Order> getAvailableOrders(Driver driver) {
        if (driver == null || driver.getLocation() == null) {
            return List.of();
        }
        
        if (driver.getStatus() != DriverStatus.ONLINE) {
            return List.of();
        }
        
        if (driver.isBusy()) {
            return List.of();
        }
        
        Location driverLocation = driver.getLocation();
        VehicleType driverVehicleType = driver.getVehicleType();
        
        return orderRepository.findByStatus(OrderStatus.PENDING).stream()
                // 過濾車種匹配的訂單
                .filter(order -> order.getVehicleType() == driverVehicleType)
                // 過濾有上車點的訂單
                .filter(order -> order.getPickupLocation() != null)
                // 過濾距離範圍內的訂單
                .filter(order -> {
                    double distance = driverLocation.distanceTo(order.getPickupLocation());
                    return distance <= searchRadius;
                })
                // 按距離排序
                .sorted(Comparator
                        .comparingDouble((Order o) -> driverLocation.distanceTo(o.getPickupLocation()))
                        .thenComparing(Order::getOrderId))
                .collect(Collectors.toList());
    }
    
    /**
     * 取得所有可用司機列表
     * 
     * @param vehicleType 車種篩選，null 表示所有車種
     * @return 可用司機列表
     */
    public List<Driver> getAvailableDrivers(VehicleType vehicleType) {
        return driverRepository.findAll().stream()
                .filter(driver -> driver.getStatus() == DriverStatus.ONLINE)
                .filter(driver -> !driver.isBusy())
                .filter(driver -> vehicleType == null || driver.getVehicleType() == vehicleType)
                .collect(Collectors.toList());
    }
    
    /**
     * 設定搜尋半徑
     * 
     * @param radius 新的搜尋半徑
     */
    public void setSearchRadius(double radius) {
        if (radius <= 0) {
            throw new IllegalArgumentException("搜尋半徑必須大於 0");
        }
        this.searchRadius = radius;
        log.info("Search radius updated to: {}", radius);
    }
    
    /**
     * 取得目前搜尋半徑
     */
    public double getSearchRadius() {
        return searchRadius;
    }
    
    /**
     * 計算訂單與司機之間的距離
     */
    public double calculateDistance(Order order, Driver driver) {
        if (order == null || driver == null || 
            order.getPickupLocation() == null || driver.getLocation() == null) {
            return Double.MAX_VALUE;
        }
        return driver.getLocation().distanceTo(order.getPickupLocation());
    }
    
    /**
     * 內部類: 司機候選者
     */
    private static class DriverCandidate {
        final Driver driver;
        final double distance;
        
        DriverCandidate(Driver driver, double distance) {
            this.driver = driver;
            this.distance = distance;
        }
    }
}
