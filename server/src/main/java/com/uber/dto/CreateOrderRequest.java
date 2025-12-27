package com.uber.dto;

import com.uber.model.Location;
import com.uber.model.VehicleType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 建立訂單請求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {
    
    @NotNull(message = "乘客 ID 不可為空")
    private String passengerId;
    
    @NotNull(message = "上車地點不可為空")
    private Double pickupX;

    @NotNull(message = "上車地點不可為空")
    private Double pickupY;

    @NotNull(message = "下車地點不可為空")
    private Double dropoffX;

    @NotNull(message = "下車地點不可為空")
    private Double dropoffY;

    @NotNull(message = "車種不可為空")
    private VehicleType vehicleType;

    // 輔助方法：轉換為 Location 對象
    public Location getPickupLocation() {
        if (pickupX == null || pickupY == null) {
            return null;
        }
        return new Location(pickupX, pickupY);
    }

    public Location getDropoffLocation() {
        if (dropoffX == null || dropoffY == null) {
            return null;
        }
        return new Location(dropoffX, dropoffY);
    }
}
