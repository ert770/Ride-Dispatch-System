package com.uber.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 接單請求 DTO
 */
@Data
public class AcceptOrderRequest {
    
    @NotNull(message = "司機 ID 不可為空")
    private String driverId;
}
