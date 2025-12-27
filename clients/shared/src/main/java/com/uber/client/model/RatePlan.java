package com.uber.client.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 費率方案
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatePlan {
    private VehicleType vehicleType;
    private Double baseFare;
    private Double perKmRate;
    private Double perMinRate;
    private Double minFare;
}
