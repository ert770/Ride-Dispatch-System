package com.uber.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * 審計日誌
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
    
    private String id;
    private Instant timestamp;
    
    private String orderId;
    private String action;        // CREATE, ACCEPT, START, COMPLETE, CANCEL
    
    private String actorType;     // PASSENGER, DRIVER, ADMIN, SYSTEM
    private String actorId;
    
    private String previousState;
    private String newState;
    
    private boolean success;
    private String failureReason; // 失敗原因代碼
    
    private Map<String, Object> metadata;
}
