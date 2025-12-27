package com.uber.client.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;

/**
 * 訂單資料模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    private String orderId;
    private String passengerId;
    private String driverId;
    
    private OrderStatus status;
    private VehicleType vehicleType;
    
    private Location pickupLocation;
    private Location dropoffLocation;
    
    private Double estimatedFare;
    private Double actualFare;
    private Double distance;
    private Integer duration;
    
    private Instant createdAt;
    private Instant acceptedAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant cancelledAt;
    
    private String cancelledBy;
    private Double cancelFee;
    
    // 額外的顯示用欄位
    private String driverName;
    private String driverPhone;
    private String vehiclePlate;
}
