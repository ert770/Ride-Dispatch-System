package com.uber.client.api;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;

/**
 * 統一 API 回應格式
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private ErrorInfo error;
    private Instant timestamp;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorInfo {
        private String code;
        private String message;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getErrorMessage() {
        return error != null ? error.getMessage() : "未知錯誤";
    }
    
    public String getErrorCode() {
        return error != null ? error.getCode() : "UNKNOWN_ERROR";
    }
}
