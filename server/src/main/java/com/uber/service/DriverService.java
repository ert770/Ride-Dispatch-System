package com.uber.service;

import com.uber.exception.BusinessException;
import com.uber.model.*;
import com.uber.repository.DriverRepository;
import com.uber.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 司機服務
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DriverService {
    
    private final DriverRepository driverRepository;
    private final OrderRepository orderRepository;
    
    /**
     * 司機上線
     */
    public Driver goOnline(String driverId, Location location) {
        Driver driver = driverRepository.findById(driverId)
                .orElse(Driver.builder()
                        .driverId(driverId)
                        .name("Driver " + driverId)
                        .vehicleType(VehicleType.STANDARD)
                        .build());
        
        driver.setStatus(DriverStatus.ONLINE);
        driver.setLocation(location);
        driver.setLastUpdatedAt(Instant.now());
        
        driverRepository.save(driver);
        log.info("Driver {} is now online at ({}, {})", driverId, location.getX(), location.getY());
        return driver;
    }
    
    /**
     * 司機下線
     */
    public Driver goOffline(String driverId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new BusinessException("DRIVER_NOT_FOUND", "司機不存在"));
        
        if (driver.isBusy()) {
            throw new BusinessException("DRIVER_BUSY", "有進行中的訂單，無法下線");
        }
        
        driver.setStatus(DriverStatus.OFFLINE);
        driver.setLastUpdatedAt(Instant.now());
        
        driverRepository.save(driver);
        log.info("Driver {} is now offline", driverId);
        return driver;
    }
    
    /**
     * 更新司機位置
     */
    public Driver updateLocation(String driverId, Location location) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new BusinessException("DRIVER_NOT_FOUND", "司機不存在"));
        
        driver.setLocation(location);
        driver.setLastUpdatedAt(Instant.now());
        
        driverRepository.save(driver);
        return driver;
    }
    
    /**
     * 取得可接訂單列表 (配對演算法)
     * 
     * 篩選規則:
     * 1. 訂單狀態為 PENDING
     * 2. 車種符合司機車種
     * 
     * 排序規則:
     * 1. 距離最近優先
     * 2. 距離相同則 orderId 較小者優先 (tie-break)
     */
    public List<Order> getOffers(String driverId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new BusinessException("DRIVER_NOT_FOUND", "司機不存在"));
        
        if (driver.getStatus() != DriverStatus.ONLINE) {
            throw new BusinessException("DRIVER_OFFLINE", "司機不在線，無法取得訂單");
        }
        
        if (driver.isBusy()) {
            return List.of(); // 忙碌中不顯示新訂單
        }
        
        Location driverLocation = driver.getLocation();
        VehicleType driverVehicleType = driver.getVehicleType();
        
        return orderRepository.findByStatus(OrderStatus.PENDING).stream()
                .filter(order -> order.getVehicleType() == driverVehicleType)
                .sorted(Comparator
                        .comparingDouble((Order o) -> driverLocation.distanceTo(o.getPickupLocation()))
                        .thenComparing(Order::getOrderId))
                .collect(Collectors.toList());
    }
    
    /**
     * 取得司機資訊
     */
    public Driver getDriver(String driverId) {
        return driverRepository.findById(driverId)
                .orElseThrow(() -> new BusinessException("DRIVER_NOT_FOUND", "司機不存在"));
    }
    
    /**
     * 取得所有司機
     */
    public List<Driver> getAllDrivers() {
        return driverRepository.findAll();
    }
    
    /**
     * 註冊/更新司機資料
     */
    public Driver registerDriver(String driverId, String name, String phone, 
                                 String vehiclePlate, VehicleType vehicleType) {
        Driver driver = Driver.builder()
                .driverId(driverId)
                .name(name)
                .phone(phone)
                .vehiclePlate(vehiclePlate)
                .vehicleType(vehicleType)
                .status(DriverStatus.OFFLINE)
                .busy(false)
                .lastUpdatedAt(Instant.now())
                .build();
        
        driverRepository.save(driver);
        log.info("Driver registered: {}", driverId);
        return driver;
    }
}
