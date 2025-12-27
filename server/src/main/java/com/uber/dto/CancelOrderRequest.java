package com.uber.dto;

import lombok.Data;

/**
 * 取消訂單請求 DTO
 */
@Data
public class CancelOrderRequest {
    
    private String cancelledBy;
    
    private String reason;
}
