package com.uber.service;

import com.uber.model.AuditLog;
import com.uber.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 審計日誌服務
 */
@Service
@RequiredArgsConstructor
public class AuditService {
    
    private final AuditLogRepository auditLogRepository;
    
    /**
     * 記錄成功操作
     */
    public void logSuccess(String orderId, String action, String actorType, 
                          String actorId, String previousState, String newState) {
        AuditLog log = AuditLog.builder()
                .id(UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .orderId(orderId)
                .action(action)
                .actorType(actorType)
                .actorId(actorId)
                .previousState(previousState)
                .newState(newState)
                .success(true)
                .build();
        
        auditLogRepository.save(log);
    }
    
    /**
     * 記錄失敗操作
     */
    public void logFailure(String orderId, String action, String actorType,
                          String actorId, String previousState, String failureReason) {
        AuditLog log = AuditLog.builder()
                .id(UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .orderId(orderId)
                .action(action)
                .actorType(actorType)
                .actorId(actorId)
                .previousState(previousState)
                .newState(previousState) // 狀態未改變
                .success(false)
                .failureReason(failureReason)
                .build();
        
        auditLogRepository.save(log);
    }
    
    public List<AuditLog> getLogsByOrderId(String orderId) {
        return auditLogRepository.findByOrderId(orderId);
    }
    
    public List<AuditLog> getAllLogs() {
        return auditLogRepository.findAll();
    }
    
    public Map<String, Long> getAcceptStats(String orderId) {
        long success = auditLogRepository.countSuccessByOrderIdAndAction(orderId, "ACCEPT");
        long failure = auditLogRepository.countFailureByOrderIdAndAction(orderId, "ACCEPT");
        return Map.of("success", success, "failure", failure);
    }
}
