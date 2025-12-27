package com.uber.repository;

import com.uber.model.AuditLog;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 審計日誌儲存庫 (In-Memory)
 */
@Repository
public class AuditLogRepository {
    
    private final List<AuditLog> logs = new CopyOnWriteArrayList<>();
    
    public AuditLog save(AuditLog auditLog) {
        logs.add(auditLog);
        return auditLog;
    }
    
    public List<AuditLog> findAll() {
        return new ArrayList<>(logs);
    }
    
    public List<AuditLog> findByOrderId(String orderId) {
        return logs.stream()
                .filter(log -> orderId.equals(log.getOrderId()))
                .collect(Collectors.toList());
    }
    
    public List<AuditLog> findByAction(String action) {
        return logs.stream()
                .filter(log -> action.equals(log.getAction()))
                .collect(Collectors.toList());
    }
    
    public List<AuditLog> findByOrderIdAndAction(String orderId, String action) {
        return logs.stream()
                .filter(log -> orderId.equals(log.getOrderId()))
                .filter(log -> action.equals(log.getAction()))
                .collect(Collectors.toList());
    }
    
    public long countSuccessByOrderIdAndAction(String orderId, String action) {
        return logs.stream()
                .filter(log -> orderId.equals(log.getOrderId()))
                .filter(log -> action.equals(log.getAction()))
                .filter(AuditLog::isSuccess)
                .count();
    }
    
    public long countFailureByOrderIdAndAction(String orderId, String action) {
        return logs.stream()
                .filter(log -> orderId.equals(log.getOrderId()))
                .filter(log -> action.equals(log.getAction()))
                .filter(log -> !log.isSuccess())
                .count();
    }
    
    public void deleteAll() {
        logs.clear();
    }
    
    public int count() {
        return logs.size();
    }
}
