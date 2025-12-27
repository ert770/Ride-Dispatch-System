package com.uber.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uber.dto.*;
import com.uber.model.*;
import com.uber.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AdminController 整合測試
 * 
 * Issue #17: 驗證 AdminController REST API 完整性
 */
@WebMvcTest(AdminController.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private DriverService driverService;

    @MockitoBean
    private AuditService auditService;

    @MockitoBean
    private FareService fareService;

    private Order sampleOrder;
    private Driver sampleDriver;
    private AuditLog sampleAuditLog;
    private RatePlan sampleRatePlan;

    @BeforeEach
    void setUp() {
        sampleOrder = Order.builder()
                .orderId("order-123")
                .passengerId("passenger-001")
                .driverId("driver-456")
                .status(OrderStatus.COMPLETED)
                .vehicleType(VehicleType.STANDARD)
                .pickupLocation(new Location(25.5, 30.2))
                .dropoffLocation(new Location(45.8, 60.1))
                .estimatedFare(150.00)
                .actualFare(185.50)
                .distance(8.5)
                .duration(15)
                .createdAt(Instant.now().minusSeconds(3600))
                .acceptedAt(Instant.now().minusSeconds(3500))
                .startedAt(Instant.now().minusSeconds(3400))
                .completedAt(Instant.now())
                .build();

        sampleDriver = Driver.builder()
                .driverId("driver-456")
                .name("王大明")
                .phone("0912-345-678")
                .vehiclePlate("ABC-1234")
                .vehicleType(VehicleType.STANDARD)
                .status(DriverStatus.OFFLINE)
                .busy(false)
                .lastUpdatedAt(Instant.now())
                .build();

        sampleAuditLog = AuditLog.builder()
                .id("audit-001")
                .timestamp(Instant.now())
                .orderId("order-123")
                .action("ACCEPT")
                .actorType("DRIVER")
                .actorId("driver-456")
                .previousState("PENDING")
                .newState("ACCEPTED")
                .success(true)
                .build();

        sampleRatePlan = RatePlan.builder()
                .vehicleType(VehicleType.STANDARD)
                .baseFare(50.0)
                .perKmRate(15.0)
                .perMinRate(3.0)
                .minFare(70.0)
                .cancelFee(30.0)
                .build();
    }

    @Nested
    @DisplayName("GET /api/admin/orders - 取得所有訂單")
    class GetAllOrdersTests {

        @Test
        @DisplayName("成功取得所有訂單含分頁資訊")
        void getAllOrders_Success() throws Exception {
            when(orderService.getAllOrders()).thenReturn(List.of(sampleOrder));

            mockMvc.perform(get("/api/admin/orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.orders").isArray())
                    .andExpect(jsonPath("$.data.orders[0].orderId").value("order-123"))
                    .andExpect(jsonPath("$.data.pagination.page").value(0))
                    .andExpect(jsonPath("$.data.pagination.size").value(20))
                    .andExpect(jsonPath("$.data.pagination.totalElements").value(1));
        }

        @Test
        @DisplayName("支援狀態篩選")
        void getAllOrders_WithStatusFilter() throws Exception {
            Order pendingOrder = Order.builder()
                    .orderId("order-pending")
                    .status(OrderStatus.PENDING)
                    .vehicleType(VehicleType.STANDARD)
                    .build();

            when(orderService.getAllOrders()).thenReturn(List.of(sampleOrder, pendingOrder));

            mockMvc.perform(get("/api/admin/orders")
                            .param("status", "COMPLETED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.pagination.totalElements").value(1));
        }

        @Test
        @DisplayName("無效狀態參數時忽略篩選")
        void getAllOrders_WithInvalidStatus() throws Exception {
            when(orderService.getAllOrders()).thenReturn(List.of(sampleOrder));

            mockMvc.perform(get("/api/admin/orders")
                            .param("status", "INVALID_STATUS"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.pagination.totalElements").value(1));
        }

        @Test
        @DisplayName("空字串狀態參數時忽略篩選")
        void getAllOrders_WithEmptyStatus() throws Exception {
            when(orderService.getAllOrders()).thenReturn(List.of(sampleOrder));

            mockMvc.perform(get("/api/admin/orders")
                            .param("status", ""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.pagination.totalElements").value(1));
        }

        @Test
        @DisplayName("訂單列表為空時分頁正確")
        void getAllOrders_EmptyList() throws Exception {
            when(orderService.getAllOrders()).thenReturn(List.of());

            mockMvc.perform(get("/api/admin/orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.orders").isEmpty())
                    .andExpect(jsonPath("$.data.pagination.totalElements").value(0))
                    .andExpect(jsonPath("$.data.pagination.totalPages").value(0));
        }

        @Test
        @DisplayName("分頁超出範圍時回傳空列表")
        void getAllOrders_PageOutOfBounds() throws Exception {
            when(orderService.getAllOrders()).thenReturn(List.of(sampleOrder));

            mockMvc.perform(get("/api/admin/orders")
                            .param("page", "10")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.orders").isEmpty())
                    .andExpect(jsonPath("$.data.pagination.totalElements").value(1));
        }

        @Test
        @DisplayName("支援分頁")
        void getAllOrders_WithPagination() throws Exception {
            when(orderService.getAllOrders()).thenReturn(List.of(sampleOrder));

            mockMvc.perform(get("/api/admin/orders")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.pagination.page").value(0))
                    .andExpect(jsonPath("$.data.pagination.size").value(10));
        }
    }

    @Nested
    @DisplayName("GET /api/admin/orders/{orderId} - 取得訂單詳情")
    class GetOrderDetailTests {

        @Test
        @DisplayName("成功取得訂單詳情")
        void getOrderDetail_Success() throws Exception {
            when(orderService.getOrder("order-123")).thenReturn(sampleOrder);

            mockMvc.perform(get("/api/admin/orders/order-123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.orderId").value("order-123"))
                    .andExpect(jsonPath("$.data.pickupLocation").exists())
                    .andExpect(jsonPath("$.data.dropoffLocation").exists());
        }

        @Test
        @DisplayName("訂單無司機時不包含driverId欄位")
        void getOrderDetail_WithoutDriver() throws Exception {
            Order orderWithoutDriver = Order.builder()
                    .orderId("order-no-driver")
                    .passengerId("passenger-001")
                    .status(OrderStatus.PENDING)
                    .vehicleType(VehicleType.STANDARD)
                    .pickupLocation(new Location(25.5, 30.2))
                    .dropoffLocation(new Location(45.8, 60.1))
                    .estimatedFare(150.00)
                    .distance(8.5)
                    .createdAt(Instant.now())
                    .build();

            when(orderService.getOrder("order-no-driver")).thenReturn(orderWithoutDriver);

            mockMvc.perform(get("/api/admin/orders/order-no-driver"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.driverId").doesNotExist())
                    .andExpect(jsonPath("$.data.fare").doesNotExist())
                    .andExpect(jsonPath("$.data.duration").doesNotExist());
        }

        @Test
        @DisplayName("已取消訂單包含取消資訊")
        void getOrderDetail_CancelledOrder() throws Exception {
            Order cancelledOrder = Order.builder()
                    .orderId("order-cancelled")
                    .passengerId("passenger-001")
                    .driverId("driver-456")
                    .status(OrderStatus.CANCELLED)
                    .vehicleType(VehicleType.STANDARD)
                    .pickupLocation(new Location(25.5, 30.2))
                    .dropoffLocation(new Location(45.8, 60.1))
                    .estimatedFare(150.00)
                    .distance(8.5)
                    .createdAt(Instant.now())
                    .cancelledAt(Instant.now())
                    .cancelledBy("passenger-001")
                    .cancelFee(30.0)
                    .build();

            when(orderService.getOrder("order-cancelled")).thenReturn(cancelledOrder);

            mockMvc.perform(get("/api/admin/orders/order-cancelled"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.cancelledAt").exists())
                    .andExpect(jsonPath("$.data.cancelledBy").value("passenger-001"))
                    .andExpect(jsonPath("$.data.cancelFee").value(30.0));
        }
    }

    @Nested
    @DisplayName("GET /api/admin/drivers - 取得所有司機")
    class GetAllDriversTests {

        @Test
        @DisplayName("成功取得所有司機")
        void getAllDrivers_Success() throws Exception {
            when(driverService.getAllDrivers()).thenReturn(List.of(sampleDriver));

            mockMvc.perform(get("/api/admin/drivers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.drivers").isArray())
                    .andExpect(jsonPath("$.data.drivers[0].driverId").value("driver-456"))
                    .andExpect(jsonPath("$.data.count").value(1));
        }

        @Test
        @DisplayName("支援狀態篩選")
        void getAllDrivers_WithStatusFilter() throws Exception {
            Driver onlineDriver = Driver.builder()
                    .driverId("driver-online")
                    .name("線上司機")
                    .status(DriverStatus.ONLINE)
                    .vehicleType(VehicleType.STANDARD)
                    .lastUpdatedAt(Instant.now())
                    .build();

            Driver offlineDriver = Driver.builder()
                    .driverId("driver-offline")
                    .name("離線司機")
                    .status(DriverStatus.OFFLINE)
                    .vehicleType(VehicleType.STANDARD)
                    .lastUpdatedAt(Instant.now())
                    .build();

            when(driverService.getAllDrivers()).thenReturn(List.of(onlineDriver, offlineDriver));

            mockMvc.perform(get("/api/admin/drivers")
                            .param("status", "ONLINE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.count").value(1));
        }

        @Test
        @DisplayName("無效狀態參數時忽略篩選")
        void getAllDrivers_WithInvalidStatus() throws Exception {
            when(driverService.getAllDrivers()).thenReturn(List.of(sampleDriver));

            mockMvc.perform(get("/api/admin/drivers")
                            .param("status", "INVALID_STATUS"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.count").value(1));
        }

        @Test
        @DisplayName("空字串狀態參數時忽略篩選")
        void getAllDrivers_WithEmptyStatus() throws Exception {
            when(driverService.getAllDrivers()).thenReturn(List.of(sampleDriver));

            mockMvc.perform(get("/api/admin/drivers")
                            .param("status", ""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.count").value(1));
        }

        @Test
        @DisplayName("司機無選填欄位時正確處理")
        void getAllDrivers_WithMinimalFields() throws Exception {
            Driver minimalDriver = Driver.builder()
                    .driverId("driver-minimal")
                    .name("最小司機")
                    .status(DriverStatus.OFFLINE)
                    .vehicleType(VehicleType.STANDARD)
                    .busy(false)
                    .lastUpdatedAt(Instant.now())
                    .build();

            when(driverService.getAllDrivers()).thenReturn(List.of(minimalDriver));

            mockMvc.perform(get("/api/admin/drivers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.drivers[0].phone").doesNotExist())
                    .andExpect(jsonPath("$.data.drivers[0].vehiclePlate").doesNotExist())
                    .andExpect(jsonPath("$.data.drivers[0].location").doesNotExist())
                    .andExpect(jsonPath("$.data.drivers[0].currentOrderId").doesNotExist());
        }
    }

    @Nested
    @DisplayName("GET /api/admin/audit-logs - 取得 Audit Log")
    class GetAuditLogsTests {

        @Test
        @DisplayName("成功取得所有 Audit Log")
        void getAuditLogs_Success() throws Exception {
            when(auditService.getAllLogs()).thenReturn(List.of(sampleAuditLog));

            mockMvc.perform(get("/api/admin/audit-logs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.logs").isArray())
                    .andExpect(jsonPath("$.data.logs[0].orderId").value("order-123"))
                    .andExpect(jsonPath("$.data.logs[0].action").value("ACCEPT"))
                    .andExpect(jsonPath("$.data.count").value(1));
        }

        @Test
        @DisplayName("支援 orderId 篩選")
        void getAuditLogs_WithOrderIdFilter() throws Exception {
            when(auditService.getLogsByOrderId("order-123")).thenReturn(List.of(sampleAuditLog));

            mockMvc.perform(get("/api/admin/audit-logs")
                            .param("orderId", "order-123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.logs[0].orderId").value("order-123"));
        }

        @Test
        @DisplayName("空字串 orderId 時查詢所有 log")
        void getAuditLogs_WithEmptyOrderId() throws Exception {
            when(auditService.getAllLogs()).thenReturn(List.of(sampleAuditLog));

            mockMvc.perform(get("/api/admin/audit-logs")
                            .param("orderId", ""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.count").value(1));
        }

        @Test
        @DisplayName("支援 action 篩選")
        void getAuditLogs_WithActionFilter() throws Exception {
            AuditLog createLog = AuditLog.builder()
                    .id("audit-002")
                    .action("CREATE")
                    .orderId("order-123")
                    .build();

            when(auditService.getAllLogs()).thenReturn(List.of(sampleAuditLog, createLog));

            mockMvc.perform(get("/api/admin/audit-logs")
                            .param("action", "ACCEPT"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.count").value(1));
        }

        @Test
        @DisplayName("空字串 action 時不篩選")
        void getAuditLogs_WithEmptyAction() throws Exception {
            when(auditService.getAllLogs()).thenReturn(List.of(sampleAuditLog));

            mockMvc.perform(get("/api/admin/audit-logs")
                            .param("action", ""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.count").value(1));
        }

        @Test
        @DisplayName("失敗的 audit log 包含 failureReason")
        void getAuditLogs_WithFailureReason() throws Exception {
            AuditLog failedLog = AuditLog.builder()
                    .id("audit-failed")
                    .timestamp(Instant.now())
                    .orderId("order-123")
                    .action("ACCEPT")
                    .actorType("DRIVER")
                    .actorId("driver-789")
                    .previousState("PENDING")
                    .newState("PENDING")
                    .success(false)
                    .failureReason("Order already accepted")
                    .build();

            when(auditService.getAllLogs()).thenReturn(List.of(failedLog));

            mockMvc.perform(get("/api/admin/audit-logs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.logs[0].failureReason").value("Order already accepted"));
        }
    }

    @Nested
    @DisplayName("GET /api/admin/accept-stats/{orderId} - 取得搶單統計")
    class GetAcceptStatsTests {

        @Test
        @DisplayName("成功取得搶單統計")
        void getAcceptStats_Success() throws Exception {
            Map<String, Long> stats = Map.of(
                    "totalAttempts", 5L,
                    "successCount", 1L,
                    "failureCount", 4L
            );
            when(auditService.getAcceptStats("order-123")).thenReturn(stats);

            mockMvc.perform(get("/api/admin/accept-stats/order-123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalAttempts").value(5))
                    .andExpect(jsonPath("$.data.successCount").value(1));
        }
    }

    @Nested
    @DisplayName("GET /api/admin/rate-plans - 取得費率設定")
    class GetRatePlansTests {

        @Test
        @DisplayName("成功取得所有費率")
        void getRatePlans_Success() throws Exception {
            when(fareService.getAllRatePlans()).thenReturn(List.of(sampleRatePlan));

            mockMvc.perform(get("/api/admin/rate-plans"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.ratePlans").isArray())
                    .andExpect(jsonPath("$.data.ratePlans[0].vehicleType").value("STANDARD"))
                    .andExpect(jsonPath("$.data.ratePlans[0].baseFare").value(50.0));
        }
    }

    @Nested
    @DisplayName("PUT /api/admin/rate-plans/{vehicleType} - 更新費率設定")
    class UpdateRatePlanTests {

        @Test
        @DisplayName("成功更新費率")
        void updateRatePlan_Success() throws Exception {
            RatePlan updatedPlan = RatePlan.builder()
                    .vehicleType(VehicleType.STANDARD)
                    .baseFare(55.0)
                    .perKmRate(16.0)
                    .perMinRate(3.5)
                    .minFare(75.0)
                    .cancelFee(35.0)
                    .build();

            when(fareService.updateRatePlan(eq(VehicleType.STANDARD), any(RatePlan.class)))
                    .thenReturn(updatedPlan);

            mockMvc.perform(put("/api/admin/rate-plans/STANDARD")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updatedPlan)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.vehicleType").value("STANDARD"))
                    .andExpect(jsonPath("$.data.baseFare").value(55.0))
                    .andExpect(jsonPath("$.data.updatedAt").exists());
        }
    }

    @Nested
    @DisplayName("GET /api/admin/stats - 系統統計數據")
    class GetSystemStatsTests {

        @Test
        @DisplayName("成功取得系統統計數據")
        void getSystemStats_Success() throws Exception {
            when(orderService.getAllOrders()).thenReturn(List.of(sampleOrder));
            when(driverService.getAllDrivers()).thenReturn(List.of(sampleDriver));

            mockMvc.perform(get("/api/admin/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.orders.total").value(1))
                    .andExpect(jsonPath("$.data.orders.completed").value(1))
                    .andExpect(jsonPath("$.data.drivers.total").value(1))
                    .andExpect(jsonPath("$.data.totalRevenue").value(185.50))
                    .andExpect(jsonPath("$.data.generatedAt").exists());
        }
    }
}
