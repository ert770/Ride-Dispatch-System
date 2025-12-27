package com.uber.dto;

import com.uber.model.Location;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 司機上線請求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverOnlineRequest {
    
    @NotNull(message = "X 座標不可為空")
    private Double x;

    @NotNull(message = "Y 座標不可為空")
    private Double y;

    // 輔助方法：轉換為 Location 對象
    public Location getLocation() {
        return new Location(x, y);
    }
}
