package com.uber.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 費率設定
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatePlan {
    
    private VehicleType vehicleType;
    private double baseFare;      // 基本費
    private double perKmRate;     // 每公里費率
    private double perMinRate;    // 每分鐘費率
    private double minFare;       // 最低車資
    private double cancelFee;     // 取消費 (已接單後取消)
}
