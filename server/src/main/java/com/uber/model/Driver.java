package com.uber.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;

/**
 * 司機實體
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Driver {
    
    private String driverId;
    private String name;
    private String phone;
    private String vehiclePlate;
    
    private DriverStatus status;
    private VehicleType vehicleType;
    private Location location;
    
    private boolean busy; // 是否有進行中的訂單
    private String currentOrderId; // 當前訂單 ID
    
    private Instant lastUpdatedAt;
}
