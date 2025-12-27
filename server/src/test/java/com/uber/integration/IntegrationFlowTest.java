package com.uber.integration;

import com.uber.dto.CreateOrderRequest;
import com.uber.dto.DriverOnlineRequest;
import com.uber.dto.RegisterDriverRequest;
import com.uber.model.*;
import com.uber.repository.AuditLogRepository;
import com.uber.repository.DriverRepository;
import com.uber.repository.OrderRepository;
import com.uber.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 整合測試 - 完整流程驗證
 *
 * 測試目標：驗證各模組 (Controller -> Service -> Repository) 間的協作與 API 合約
 *
 * 測試案例：
 * IT-FLOW-01: 建立訂單 -> 司機接單 -> 開始行程 -> 完成行程
 * IT-FLOW-02: 建立訂單 -> 乘客取消
 * IT-FLOW-03: 建立訂單 -> 司機接單 -> 乘客取消
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("整合測試 (Integration Testing)")
class IntegrationFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private DriverService driverService;

    @Autowired
    private FareService fareService;

    @BeforeEach
    void setUp() {
        // 注意：Repository 使用 ConcurrentHashMap，測試之間會共享資料
        // 為確保測試獨立性，每個測試使用不同的 ID

        // 初始化費率計畫
        fareService.initRatePlans();
    }

    // =========================================================================
    // IT-FLOW-01: 完整訂單流程（建立 -> 接單 -> 開始 -> 完成）
    // =========================================================================

    @Nested
    @DisplayName("IT-FLOW-01: 建立訂單 -> 司機接單 -> 開始行程 -> 完成行程")
    class CompleteOrderFlowTests {

        @Test
        @DisplayName("IT-FLOW-01-01: Happy Path - 完整流程成功")
        void testCompleteOrderFlow_HappyPath() throws Exception {
            // ===== Step 1: Passenger 建立訂單 =====
            CreateOrderRequest createRequest = CreateOrderRequest.builder()
                    .passengerId("passenger-001")
                    .pickupX(25.033)
                    .pickupY(121.565)
                    .dropoffX(25.042)
                    .dropoffY(121.520)
                    .vehicleType(VehicleType.STANDARD)
                    .build();

            MvcResult createResult = mockMvc.perform(post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isCreated())  // 201 Created
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.orderId").exists())
                    .andExpect(jsonPath("$.data.status").value("PENDING"))
                    .andExpect(jsonPath("$.data.passengerId").value("passenger-001"))
                    .andExpect(jsonPath("$.data.vehicleType").value("STANDARD"))
                    .andExpect(jsonPath("$.data.estimatedFare").exists())
                    .andReturn();

            String responseBody = createResult.getResponse().getContentAsString();
            String orderId = objectMapper.readTree(responseBody)
                    .get("data").get("orderId").asText();

            assertNotNull(orderId, "訂單 ID 應該存在");

            // 驗證資料庫狀態
            Order createdOrder = orderRepository.findById(orderId).orElseThrow();
            assertEquals(OrderStatus.PENDING, createdOrder.getStatus());

            // ===== Step 2: Driver 註冊並上線 =====
            RegisterDriverRequest registerRequest = RegisterDriverRequest.builder()
                    .driverId("driver-001")
                    .name("張三")
                    .phone("0912345678")
                    .vehiclePlate("ABC-1234")
                    .vehicleType(VehicleType.STANDARD)
                    .build();

            mockMvc.perform(post("/api/drivers")  // 修正端點
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isCreated())  // 201 Created
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.driverId").value("driver-001"))
                    .andExpect(jsonPath("$.data.status").value("OFFLINE"));

            // Driver 上線
            DriverOnlineRequest onlineRequest = DriverOnlineRequest.builder()
                    .x(25.035)
                    .y(121.560)
                    .build();

            mockMvc.perform(put("/api/drivers/driver-001/online")  // 改用 PUT
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(onlineRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("ONLINE"));

            // ===== Step 3: Driver 接單 =====
            String acceptRequest = "{\"driverId\":\"driver-001\"}";
            mockMvc.perform(put("/api/orders/" + orderId + "/accept")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(acceptRequest))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                    .andExpect(jsonPath("$.data.driverId").value("driver-001"))
                    .andExpect(jsonPath("$.data.acceptedAt").exists());

            // 驗證資料庫狀態
            Order acceptedOrder = orderRepository.findById(orderId).orElseThrow();
            assertEquals(OrderStatus.ACCEPTED, acceptedOrder.getStatus());
            assertEquals("driver-001", acceptedOrder.getDriverId());

            Driver driver = driverRepository.findById("driver-001").orElseThrow();
            assertTrue(driver.isBusy(), "司機應該處於忙碌狀態");
            assertEquals(orderId, driver.getCurrentOrderId());

            // ===== Step 4: Driver 開始行程 =====
            String startRequest = "{\"driverId\":\"driver-001\"}";
            mockMvc.perform(put("/api/orders/" + orderId + "/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(startRequest))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("ONGOING"))
                    .andExpect(jsonPath("$.data.startedAt").exists());

            // 驗證資料庫狀態
            Order ongoingOrder = orderRepository.findById(orderId).orElseThrow();
            assertEquals(OrderStatus.ONGOING, ongoingOrder.getStatus());
            assertNotNull(ongoingOrder.getStartedAt());

            // ===== Step 5: Driver 完成行程 =====
            String completeRequest = "{\"driverId\":\"driver-001\"}";
            mockMvc.perform(put("/api/orders/" + orderId + "/complete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(completeRequest))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.data.completedAt").exists())
                    .andExpect(jsonPath("$.data.fare").exists())
                    .andExpect(jsonPath("$.data.fare").isNumber());

            // 驗證最終狀態
            Order completedOrder = orderRepository.findById(orderId).orElseThrow();
            assertEquals(OrderStatus.COMPLETED, completedOrder.getStatus());
            assertNotNull(completedOrder.getCompletedAt());
            assertTrue(completedOrder.getActualFare() > 0, "實際費用應大於 0");

            // 驗證司機狀態恢復
            Driver finalDriver = driverRepository.findById("driver-001").orElseThrow();
            assertFalse(finalDriver.isBusy(), "司機應該恢復閒置狀態");
            assertNull(finalDriver.getCurrentOrderId());

            // 驗證稽核日誌
            var auditLogs = auditLogRepository.findByOrderId(orderId);
            assertTrue(auditLogs.size() >= 4, "應該有至少 4 筆稽核記錄 (CREATE, ACCEPT, START, COMPLETE)");
        }

        @Test
        @DisplayName("IT-FLOW-01-02: 驗證時間戳順序正確")
        void testCompleteOrderFlow_TimestampOrder() throws Exception {
            // 建立並完成一個完整訂單流程
            String orderId = createAndCompleteOrder("passenger-002", "driver-002");

            // 驗證時間戳順序
            Order order = orderRepository.findById(orderId).orElseThrow();

            assertNotNull(order.getCreatedAt());
            assertNotNull(order.getAcceptedAt());
            assertNotNull(order.getStartedAt());
            assertNotNull(order.getCompletedAt());

            // 時間順序應為: created <= accepted <= started <= completed
            assertTrue(order.getCreatedAt().compareTo(order.getAcceptedAt()) <= 0,
                    "建立時間應早於或等於接單時間");
            assertTrue(order.getAcceptedAt().compareTo(order.getStartedAt()) <= 0,
                    "接單時間應早於或等於開始時間");
            assertTrue(order.getStartedAt().compareTo(order.getCompletedAt()) <= 0,
                    "開始時間應早於或等於完成時間");
        }

        @Test
        @DisplayName("IT-FLOW-01-03: 驗證費用計算正確")
        void testCompleteOrderFlow_FareCalculation() throws Exception {
            // 建立訂單
            CreateOrderRequest createRequest = CreateOrderRequest.builder()
                    .passengerId("passenger-003")
                    .pickupX(25.033)
                    .pickupY(121.565)
                    .dropoffX(25.042)
                    .dropoffY(121.520)
                    .vehicleType(VehicleType.STANDARD)
                    .build();

            MvcResult createResult = mockMvc.perform(post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isCreated())  // 201 Created
                    .andReturn();

            String orderId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                    .get("data").get("orderId").asText();

            double estimatedFare = objectMapper.readTree(createResult.getResponse().getContentAsString())
                    .get("data").get("estimatedFare").asDouble();

            // 完成流程
            registerAndOnlineDriver("driver-003", VehicleType.STANDARD);
            acceptAndCompleteOrder(orderId, "driver-003");

            // 驗證實際費用
            Order order = orderRepository.findById(orderId).orElseThrow();
            assertTrue(order.getActualFare() > 0, "實際費用應大於 0");
            assertTrue(order.getActualFare() >= estimatedFare * 0.8,
                    "實際費用不應低於預估費用太多");
        }
    }

    // =========================================================================
    // IT-FLOW-02: 建立訂單 -> 乘客取消
    // =========================================================================

    @Nested
    @DisplayName("IT-FLOW-02: 建立訂單 -> 乘客取消")
    class OrderCancellationByPassengerTests {

        @Test
        @DisplayName("IT-FLOW-02-01: PENDING 狀態取消 - 無取消費")
        void testCancelOrder_FromPending_NoCancellationFee() throws Exception {
            // ===== Step 1: Passenger 建立訂單 =====
            CreateOrderRequest createRequest = CreateOrderRequest.builder()
                    .passengerId("passenger-101")
                    .pickupX(25.033)
                    .pickupY(121.565)
                    .dropoffX(25.042)
                    .dropoffY(121.520)
                    .vehicleType(VehicleType.STANDARD)
                    .build();

            MvcResult createResult = mockMvc.perform(post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isCreated())  // 201 Created
                    .andReturn();

            String orderId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                    .get("data").get("orderId").asText();

            // ===== Step 2: Passenger 立即取消訂單 =====
            String cancelRequest = "{\"cancelledBy\":\"passenger-101\"}";
            mockMvc.perform(put("/api/orders/" + orderId + "/cancel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(cancelRequest))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.data.cancelledAt").exists())
                    .andExpect(jsonPath("$.data.cancelFee").value(0.0));

            // 驗證資料庫狀態
            Order cancelledOrder = orderRepository.findById(orderId).orElseThrow();
            assertEquals(OrderStatus.CANCELLED, cancelledOrder.getStatus());
            assertEquals(0.0, cancelledOrder.getCancelFee(), "PENDING 狀態取消應無取消費");
            assertNotNull(cancelledOrder.getCancelledAt());

            // 驗證稽核日誌
            var auditLogs = auditLogRepository.findByOrderId(orderId);
            assertTrue(auditLogs.stream().anyMatch(log ->
                "CANCEL".equals(log.getAction()) && log.isSuccess()));
        }

        @Test
        @DisplayName("IT-FLOW-02-02: 重複取消應保持冪等")
        void testCancelOrder_Idempotent() throws Exception {
            // 建立訂單
            String orderId = createOrder("passenger-102", VehicleType.STANDARD);

            // 第一次取消
            String cancelRequest = "{\"cancelledBy\":\"passenger-102\"}";
            mockMvc.perform(put("/api/orders/" + orderId + "/cancel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(cancelRequest))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"));

            // 第二次取消（應該成功，保持冪等）
            mockMvc.perform(put("/api/orders/" + orderId + "/cancel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(cancelRequest))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"));

            // 驗證只有一次取消記錄
            Order order = orderRepository.findById(orderId).orElseThrow();
            assertEquals(OrderStatus.CANCELLED, order.getStatus());
        }

        @Test
        @DisplayName("IT-FLOW-02-03: 非訂單擁有者不可取消")
        void testCancelOrder_NotOwner_ShouldFail() throws Exception {
            // 建立訂單
            String orderId = createOrder("passenger-103", VehicleType.STANDARD);

            // 其他乘客嘗試取消
            String cancelRequest = "{\"cancelledBy\":\"passenger-999\"}";
            mockMvc.perform(put("/api/orders/" + orderId + "/cancel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(cancelRequest))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

            // 驗證訂單狀態未改變
            Order order = orderRepository.findById(orderId).orElseThrow();
            assertEquals(OrderStatus.PENDING, order.getStatus());
        }
    }

    // =========================================================================
    // IT-FLOW-03: 建立訂單 -> 司機接單 -> 乘客取消
    // =========================================================================

    @Nested
    @DisplayName("IT-FLOW-03: 建立訂單 -> 司機接單 -> 乘客取消")
    class OrderCancellationAfterAcceptTests {

        @Test
        @DisplayName("IT-FLOW-03-01: ACCEPTED 狀態取消 - 需付取消費")
        void testCancelOrder_FromAccepted_WithCancellationFee() throws Exception {
            // ===== Step 1: Passenger 建立訂單 =====
            String orderId = createOrder("passenger-201", VehicleType.STANDARD);

            // ===== Step 2: Driver 註冊、上線並接單 =====
            registerAndOnlineDriver("driver-201", VehicleType.STANDARD);

            String acceptRequest = "{\"driverId\":\"driver-201\"}";
            mockMvc.perform(put("/api/orders/" + orderId + "/accept")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(acceptRequest))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("ACCEPTED"));

            // 驗證司機狀態變更
            Driver driver = driverRepository.findById("driver-201").orElseThrow();
            assertTrue(driver.isBusy());

            // ===== Step 3: Passenger 取消訂單 =====
            String cancelRequest = "{\"cancelledBy\":\"passenger-201\"}";
            mockMvc.perform(put("/api/orders/" + orderId + "/cancel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(cancelRequest))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.data.cancelledAt").exists())
                    .andExpect(jsonPath("$.data.cancelFee").isNumber());

            // 驗證資料庫狀態
            Order cancelledOrder = orderRepository.findById(orderId).orElseThrow();
            assertEquals(OrderStatus.CANCELLED, cancelledOrder.getStatus());
            assertTrue(cancelledOrder.getCancelFee() > 0, "ACCEPTED 狀態取消應有取消費");

            // 驗證司機狀態恢復
            Driver releasedDriver = driverRepository.findById("driver-201").orElseThrow();
            assertFalse(releasedDriver.isBusy(), "取消後司機應恢復閒置狀態");
            assertNull(releasedDriver.getCurrentOrderId());
        }

        @Test
        @DisplayName("IT-FLOW-03-02: ONGOING 狀態不可取消")
        void testCancelOrder_FromOngoing_ShouldFail() throws Exception {
            // 建立訂單並進行到 ONGOING 狀態
            String orderId = createOrder("passenger-202", VehicleType.STANDARD);
            registerAndOnlineDriver("driver-202", VehicleType.STANDARD);

            String driverRequest = "{\"driverId\":\"driver-202\"}";
            mockMvc.perform(put("/api/orders/" + orderId + "/accept")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(driverRequest))
                    .andExpect(status().isOk());

            mockMvc.perform(put("/api/orders/" + orderId + "/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(driverRequest))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("ONGOING"));

            // 嘗試取消應失敗
            String cancelRequest = "{\"cancelledBy\":\"passenger-202\"}";
            mockMvc.perform(put("/api/orders/" + orderId + "/cancel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(cancelRequest))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));

            // 驗證訂單狀態未改變
            Order order = orderRepository.findById(orderId).orElseThrow();
            assertEquals(OrderStatus.ONGOING, order.getStatus());
        }

        @Test
        @DisplayName("IT-FLOW-03-03: 取消後司機可接其他訂單")
        void testCancelOrder_DriverCanAcceptNewOrder() throws Exception {
            // 建立第一筆訂單並接單
            String orderId1 = createOrder("passenger-203", VehicleType.STANDARD);
            registerAndOnlineDriver("driver-203", VehicleType.STANDARD);

            String driverRequest = "{\"driverId\":\"driver-203\"}";
            mockMvc.perform(put("/api/orders/" + orderId1 + "/accept")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(driverRequest))
                    .andExpect(status().isOk());

            // 乘客取消第一筆訂單
            String cancelRequest = "{\"cancelledBy\":\"passenger-203\"}";
            mockMvc.perform(put("/api/orders/" + orderId1 + "/cancel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(cancelRequest))
                    .andExpect(status().isOk());

            // 建立第二筆訂單
            String orderId2 = createOrder("passenger-204", VehicleType.STANDARD);

            // 驗證司機可以接第二筆訂單
            String driver2Request = "{\"driverId\":\"driver-203\"}";
            mockMvc.perform(put("/api/orders/" + orderId2 + "/accept")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(driver2Request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                    .andExpect(jsonPath("$.data.driverId").value("driver-203"));

            // 驗證司機狀態
            Driver driver = driverRepository.findById("driver-203").orElseThrow();
            assertTrue(driver.isBusy());
            assertEquals(orderId2, driver.getCurrentOrderId());
        }

        @Test
        @DisplayName("IT-FLOW-03-04: 驗證取消費計算正確")
        void testCancelOrder_CancelFeeCalculation() throws Exception {
            // 建立並接單
            String orderId = createOrder("passenger-205", VehicleType.PREMIUM);
            registerAndOnlineDriver("driver-205", VehicleType.PREMIUM);

            String acceptRequest = "{\"driverId\":\"driver-205\"}";
            mockMvc.perform(put("/api/orders/" + orderId + "/accept")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(acceptRequest))
                    .andExpect(status().isOk());

            // 取消訂單
            String cancelRequest = "{\"cancelledBy\":\"passenger-205\"}";
            MvcResult cancelResult = mockMvc.perform(put("/api/orders/" + orderId + "/cancel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(cancelRequest))
                    .andExpect(status().isOk())
                    .andReturn();

            double cancelFee = objectMapper.readTree(cancelResult.getResponse().getContentAsString())
                    .get("data").get("cancelFee").asDouble();

            // 驗證取消費應該是 PREMIUM 車種的取消費（根據 FareService）
            RatePlan premiumPlan = fareService.getRatePlan(VehicleType.PREMIUM);
            assertEquals(premiumPlan.getCancelFee(), cancelFee, 0.01,
                    "取消費應符合費率計畫");
        }
    }

    // =========================================================================
    // API 合約驗證測試
    // =========================================================================

    @Nested
    @DisplayName("API 合約驗證")
    class ApiContractTests {

        @Test
        @DisplayName("API-01: 建立訂單回應格式驗證")
        void testCreateOrderResponseFormat() throws Exception {
            CreateOrderRequest request = CreateOrderRequest.builder()
                    .passengerId("passenger-api-01")
                    .pickupX(25.033)
                    .pickupY(121.565)
                    .dropoffX(25.042)
                    .dropoffY(121.520)
                    .vehicleType(VehicleType.STANDARD)
                    .build();

            mockMvc.perform(post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())  // 201 Created 是正確的 RESTful 回應
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.success").isBoolean())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").exists())
                    .andExpect(jsonPath("$.data.orderId").isString())
                    .andExpect(jsonPath("$.data.status").isString())
                    .andExpect(jsonPath("$.data.passengerId").isString())
                    .andExpect(jsonPath("$.data.vehicleType").isString())
                    .andExpect(jsonPath("$.data.pickupLocation").exists())
                    .andExpect(jsonPath("$.data.dropoffLocation").exists())
                    .andExpect(jsonPath("$.data.estimatedFare").isNumber())
                    .andExpect(jsonPath("$.data.estimatedDistance").isNumber())
                    .andExpect(jsonPath("$.data.createdAt").isString());
        }

        @Test
        @DisplayName("API-02: 錯誤回應格式驗證")
        void testErrorResponseFormat() throws Exception {
            // 使用無效的座標建立訂單
            CreateOrderRequest request = CreateOrderRequest.builder()
                    .passengerId("passenger-api-02")
                    .pickupX(25.033)
                    .pickupY(121.565)
                    .dropoffX(25.033)  // 相同座標
                    .dropoffY(121.565)
                    .vehicleType(VehicleType.STANDARD)
                    .build();

            mockMvc.perform(post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error").exists())
                    .andExpect(jsonPath("$.error.code").isString())
                    .andExpect(jsonPath("$.error.message").isString());
        }

        @Test
        @DisplayName("API-03: 接單回應包含完整訂單資訊")
        void testAcceptOrderResponseFormat() throws Exception {
            String orderId = createOrder("passenger-api-03", VehicleType.STANDARD);
            registerAndOnlineDriver("driver-api-03", VehicleType.STANDARD);

            String acceptRequest = "{\"driverId\":\"driver-api-03\"}";
            mockMvc.perform(put("/api/orders/" + orderId + "/accept")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(acceptRequest))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.orderId").value(orderId))
                    .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                    .andExpect(jsonPath("$.data.driverId").value("driver-api-03"))
                    .andExpect(jsonPath("$.data.acceptedAt").exists())
                    .andExpect(jsonPath("$.data.pickupLocation").exists())
                    .andExpect(jsonPath("$.data.dropoffLocation").exists());
        }
    }

    // =========================================================================
    // 輔助方法
    // =========================================================================

    /**
     * 建立訂單
     */
    private String createOrder(String passengerId, VehicleType vehicleType) throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .passengerId(passengerId)
                .pickupX(25.033)
                .pickupY(121.565)
                .dropoffX(25.042)
                .dropoffY(121.520)
                .vehicleType(vehicleType)
                .build();

        MvcResult result = mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())  // 201 Created
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("orderId").asText();
    }

    /**
     * 註冊並上線司機
     */
    private void registerAndOnlineDriver(String driverId, VehicleType vehicleType) throws Exception {
        RegisterDriverRequest registerRequest = RegisterDriverRequest.builder()
                .driverId(driverId)
                .name("司機" + driverId)
                .phone("0912345678")
                .vehiclePlate(driverId + "-PLATE")
                .vehicleType(vehicleType)
                .build();

        mockMvc.perform(post("/api/drivers")  // 修正：端點是 /api/drivers
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());  // 201 Created

        DriverOnlineRequest onlineRequest = DriverOnlineRequest.builder()
                .x(25.035)
                .y(121.560)
                .build();

        mockMvc.perform(put("/api/drivers/" + driverId + "/online")  // 改用 PUT
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(onlineRequest)))
                .andExpect(status().isOk());
    }

    /**
     * 接單並完成訂單
     */
    private void acceptAndCompleteOrder(String orderId, String driverId) throws Exception {
        String acceptRequest = "{\"driverId\":\"" + driverId + "\"}";
        mockMvc.perform(put("/api/orders/" + orderId + "/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content(acceptRequest))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/orders/" + orderId + "/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(acceptRequest))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/orders/" + orderId + "/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(acceptRequest))
                .andExpect(status().isOk());
    }

    /**
     * 建立並完成一個完整訂單流程
     */
    private String createAndCompleteOrder(String passengerId, String driverId) throws Exception {
        String orderId = createOrder(passengerId, VehicleType.STANDARD);
        registerAndOnlineDriver(driverId, VehicleType.STANDARD);
        acceptAndCompleteOrder(orderId, driverId);
        return orderId;
    }
}

