package com.uber.service;

import com.uber.model.RatePlan;
import com.uber.model.VehicleType;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 車資計算服務
 */
@Service
public class FareService {
    
    private final Map<VehicleType, RatePlan> ratePlans = new EnumMap<>(VehicleType.class);
    
    @PostConstruct
    public void initRatePlans() {
        // 初始化預設費率
        ratePlans.put(VehicleType.STANDARD, RatePlan.builder()
                .vehicleType(VehicleType.STANDARD)
                .baseFare(50.0)
                .perKmRate(15.0)
                .perMinRate(3.0)
                .minFare(70.0)
                .cancelFee(30.0)
                .build());
        
        ratePlans.put(VehicleType.PREMIUM, RatePlan.builder()
                .vehicleType(VehicleType.PREMIUM)
                .baseFare(80.0)
                .perKmRate(25.0)
                .perMinRate(5.0)
                .minFare(120.0)
                .cancelFee(50.0)
                .build());
        
        ratePlans.put(VehicleType.XL, RatePlan.builder()
                .vehicleType(VehicleType.XL)
                .baseFare(100.0)
                .perKmRate(30.0)
                .perMinRate(6.0)
                .minFare(150.0)
                .cancelFee(60.0)
                .build());
    }
    
    /**
     * 計算預估車資 (僅距離)
     */
    public double calculateEstimatedFare(VehicleType vehicleType, double distance) {
        RatePlan plan = ratePlans.get(vehicleType);
        double fare = plan.getBaseFare() + (distance * plan.getPerKmRate());
        return Math.max(fare, plan.getMinFare());
    }
    
    /**
     * 計算實際車資 (距離 + 時間)
     */
    public double calculateFare(VehicleType vehicleType, double distance, int durationMinutes) {
        RatePlan plan = ratePlans.get(vehicleType);
        double fare = plan.getBaseFare() 
                + (distance * plan.getPerKmRate()) 
                + (durationMinutes * plan.getPerMinRate());
        return Math.round(Math.max(fare, plan.getMinFare()) * 100.0) / 100.0;
    }
    
    /**
     * 取得取消費
     */
    public double getCancelFee(VehicleType vehicleType) {
        return ratePlans.get(vehicleType).getCancelFee();
    }
    
    /**
     * 取得所有費率
     */
    public List<RatePlan> getAllRatePlans() {
        return List.copyOf(ratePlans.values());
    }
    
    /**
     * 更新費率
     */
    public RatePlan updateRatePlan(VehicleType vehicleType, RatePlan newPlan) {
        newPlan.setVehicleType(vehicleType);
        ratePlans.put(vehicleType, newPlan);
        return newPlan;
    }
    
    /**
     * 取得指定車種費率
     */
    public RatePlan getRatePlan(VehicleType vehicleType) {
        return ratePlans.get(vehicleType);
    }
}
