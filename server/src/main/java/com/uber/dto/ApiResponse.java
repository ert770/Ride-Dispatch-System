package com.uber.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;

/**
 * 統一 API 回應格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    
    private boolean success;
    private T data;
    private ErrorInfo error;
    private Instant timestamp;
    
    /**
     * 成功回應
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * 錯誤回應
     */
    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(new ErrorInfo(code, message))
                .timestamp(Instant.now())
                .build();
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ErrorInfo {
        private String code;
        private String message;
    }
}
