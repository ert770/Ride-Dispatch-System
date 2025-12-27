package com.uber.client.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;

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
    private String action;
    private String actorType;
    private String actorId;
    private String previousState;
    private String newState;
    private boolean success;
    private String failureReason;
}
