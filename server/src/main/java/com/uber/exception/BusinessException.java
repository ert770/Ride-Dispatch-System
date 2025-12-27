package com.uber.exception;

import lombok.Getter;

/**
 * 業務邏輯例外
 */
@Getter
public class BusinessException extends RuntimeException {
    
    private final String code;
    private final int httpStatus;
    
    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
        this.httpStatus = 400;
    }
    
    public BusinessException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }
}
