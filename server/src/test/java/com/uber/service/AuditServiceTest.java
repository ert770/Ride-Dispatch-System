package com.uber.service;

import com.uber.model.AuditLog;
import com.uber.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AuditService 單元測試
 * 
 * 驗證 Issue #7 的所有驗收標準：
 * - 記錄所有訂單操作
 * - 記錄成功和失敗的操作
 * - 支援按 orderId 查詢
 */
class AuditServiceTest {
    
    private AuditService auditService;
    private AuditLogRepository auditLogRepository;
    
    @BeforeEach
    void setUp() {
        auditLogRepository = new AuditLogRepository();
        auditService = new AuditService(auditLogRepository);
    }
    
    // =========================================================================
    // logSuccess() 測試
    // =========================================================================
    
    @Nested
    @DisplayName("logSuccess() - 記錄成功操作")
    class LogSuccessTests {
        
        @Test
        @DisplayName("UT-A01: 記錄成功操作")
        void testLogSuccess_RecordsCorrectly() {
            // When
            auditService.logSuccess("order-1", "CREATE", "PASSENGER", 
                    "passenger-1", null, "PENDING");
            
            // Then
            List<AuditLog> logs = auditService.getAllLogs();
            assertEquals(1, logs.size());
            
            AuditLog log = logs.get(0);
            assertNotNull(log.getId());
            assertNotNull(log.getTimestamp());
            assertEquals("order-1", log.getOrderId());
            assertEquals("CREATE", log.getAction());
            assertEquals("PASSENGER", log.getActorType());
            assertEquals("passenger-1", log.getActorId());
            assertEquals("PENDING", log.getNewState());
            assertTrue(log.isSuccess());
            assertNull(log.getFailureReason());
        }
        
        @Test
        @DisplayName("UT-A02: 記錄多個成功操作")
        void testLogSuccess_MultipleOperations() {
            // When
            auditService.logSuccess("order-1", "CREATE", "PASSENGER", 
                    "passenger-1", null, "PENDING");
            auditService.logSuccess("order-1", "ACCEPT", "DRIVER", 
                    "driver-1", "PENDING", "ACCEPTED");
            auditService.logSuccess("order-1", "START", "DRIVER", 
                    "driver-1", "ACCEPTED", "ONGOING");
            
            // Then
            List<AuditLog> logs = auditService.getLogsByOrderId("order-1");
            assertEquals(3, logs.size());
        }
    }
    
    // =========================================================================
    // logFailure() 測試
    // =========================================================================
    
    @Nested
    @DisplayName("logFailure() - 記錄失敗操作")
    class LogFailureTests {
        
        @Test
        @DisplayName("UT-A03: 記錄失敗操作")
        void testLogFailure_RecordsCorrectly() {
            // When
            auditService.logFailure("order-1", "ACCEPT", "DRIVER", 
                    "driver-2", "ACCEPTED", "ORDER_ALREADY_ACCEPTED");
            
            // Then
            List<AuditLog> logs = auditService.getAllLogs();
            assertEquals(1, logs.size());
            
            AuditLog log = logs.get(0);
            assertNotNull(log.getId());
            assertNotNull(log.getTimestamp());
            assertEquals("order-1", log.getOrderId());
            assertEquals("ACCEPT", log.getAction());
            assertEquals("DRIVER", log.getActorType());
            assertEquals("driver-2", log.getActorId());
            assertEquals("ACCEPTED", log.getPreviousState());
            assertEquals("ACCEPTED", log.getNewState()); // 狀態未改變
            assertFalse(log.isSuccess());
            assertEquals("ORDER_ALREADY_ACCEPTED", log.getFailureReason());
        }
        
        @Test
        @DisplayName("UT-A04: 記錄多個失敗操作 (H2 搶單)")
        void testLogFailure_MultipleRaceConditions() {
            // Given - 模擬 H2 搶單場景
            auditService.logSuccess("order-1", "ACCEPT", "DRIVER", 
                    "driver-1", "PENDING", "ACCEPTED"); // 成功
            
            // When - 9 個司機失敗
            for (int i = 2; i <= 10; i++) {
                auditService.logFailure("order-1", "ACCEPT", "DRIVER", 
                        "driver-" + i, "ACCEPTED", "ORDER_ALREADY_ACCEPTED");
            }
            
            // Then
            Map<String, Long> stats = auditService.getAcceptStats("order-1");
            assertEquals(1L, stats.get("success"));
            assertEquals(9L, stats.get("failure"));
        }
    }
    
    // =========================================================================
    // 查詢功能測試
    // =========================================================================
    
    @Nested
    @DisplayName("查詢功能測試")
    class QueryTests {
        
        @BeforeEach
        void createTestData() {
            // 建立測試資料
            auditService.logSuccess("order-1", "CREATE", "PASSENGER", 
                    "passenger-1", null, "PENDING");
            auditService.logSuccess("order-1", "ACCEPT", "DRIVER", 
                    "driver-1", "PENDING", "ACCEPTED");
            auditService.logSuccess("order-2", "CREATE", "PASSENGER", 
                    "passenger-2", null, "PENDING");
            auditService.logFailure("order-2", "ACCEPT", "DRIVER", 
                    "driver-1", "PENDING", "DRIVER_BUSY");
        }
        
        @Test
        @DisplayName("UT-A05: 按 orderId 查詢")
        void testGetLogsByOrderId() {
            // When
            List<AuditLog> order1Logs = auditService.getLogsByOrderId("order-1");
            List<AuditLog> order2Logs = auditService.getLogsByOrderId("order-2");
            
            // Then
            assertEquals(2, order1Logs.size());
            assertEquals(2, order2Logs.size());
        }
        
        @Test
        @DisplayName("UT-A06: 取得所有日誌")
        void testGetAllLogs() {
            // When
            List<AuditLog> allLogs = auditService.getAllLogs();
            
            // Then
            assertEquals(4, allLogs.size());
        }
        
        @Test
        @DisplayName("UT-A07: 取得 Accept 統計")
        void testGetAcceptStats() {
            // When
            Map<String, Long> order1Stats = auditService.getAcceptStats("order-1");
            Map<String, Long> order2Stats = auditService.getAcceptStats("order-2");
            
            // Then
            assertEquals(1L, order1Stats.get("success"));
            assertEquals(0L, order1Stats.get("failure"));
            
            assertEquals(0L, order2Stats.get("success"));
            assertEquals(1L, order2Stats.get("failure"));
        }
    }
}
