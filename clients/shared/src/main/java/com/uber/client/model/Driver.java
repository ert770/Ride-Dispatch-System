package com.uber.client.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;

/**
 * 司機資料模型
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
    
    private boolean busy;
    private String currentOrderId;
    
    private Instant lastUpdatedAt;
}
