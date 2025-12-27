package com.uber.service;

import com.uber.exception.BusinessException;
import com.uber.model.*;
import com.uber.repository.AuditLogRepository;
import com.uber.repository.DriverRepository;
import com.uber.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 狀態機單元測試 - 專門測試狀態轉換規則
 *
 * 測試案例依據 Phase 4B 工作計畫的要求：
 * UT-SM-01: PENDING -> ACCEPTED 合法
 * UT-SM-02: PENDING -> ONGOING 非法
 * UT-SM-03: COMPLETED 不可變更
 * UT-SM-04: CANCELLED 不可變更
 */
@DisplayName("狀態機單元測試 (State Machine Unit Tests)")
class StateMachineTest {

    private OrderService orderService;
    private OrderRepository orderRepository;
    private DriverRepository driverRepository;
    private AuditService auditService;
    private FareService fareService;

    @BeforeEach
    void setUp() {
        orderRepository = new OrderRepository();
        driverRepository = new DriverRepository();
        AuditLogRepository auditLogRepository = new AuditLogRepository();
        auditService = new AuditService(auditLogRepository);
        fareService = new FareService();
        fareService.initRatePlans();

        orderService = new OrderService(orderRepository, driverRepository, auditService, fareService);
    }

    // =========================================================================
    // UT-SM-01: PENDING -> ACCEPTED 合法轉換測試
    // =========================================================================

    @Nested
    @DisplayName("UT-SM-01: PENDING -> ACCEPTED 合法轉換")
    class PendingToAcceptedTests {

        @Test
        @DisplayName("UT-SM-01-01: 正常流程 PENDING -> ACCEPTED 應成功")
        void testPendingToAccepted_NormalFlow_ShouldSucceed() {
            // Given - 建立 PENDING 訂單
            Location pickup = new Location(10.0, 20.0);
            Location dropoff = new Location(30.0, 40.0);
            Order order = orderService.createOrder("passenger-1", pickup, dropoff, VehicleType.STANDARD);

            assertEquals(OrderStatus.PENDING, order.getStatus(), "訂單初始狀態應為 PENDING");

            // 建立上線司機
            Driver driver = Driver.builder()
                    .driverId("driver-1")
                    .name("Test Driver")
                    .status(DriverStatus.ONLINE)
                    .busy(false)
                    .vehicleType(VehicleType.STANDARD)
                    .location(new Location(15.0, 25.0))
                    .build();
            driverRepository.save(driver);

            // When - 司機接單
            Order acceptedOrder = orderService.acceptOrder(order.getOrderId(), "driver-1");

            // Then - 驗證狀態轉換
            assertEquals(OrderStatus.ACCEPTED, acceptedOrder.getStatus(), "訂單狀態應轉換為 ACCEPTED");
            assertEquals("driver-1", acceptedOrder.getDriverId(), "應記錄司機 ID");
            assertNotNull(acceptedOrder.getAcceptedAt(), "應記錄接單時間");

            // 驗證 Repository 中的資料
            Order savedOrder = orderRepository.findById(order.getOrderId()).orElseThrow();
            assertEquals(OrderStatus.ACCEPTED, savedOrder.getStatus(), "Repository 中的訂單狀態應為 ACCEPTED");
        }

        @Test
        @DisplayName("UT-SM-01-02: 多個司機搶單，只有第一個成功")
        void testPendingToAccepted_MultipleDrivers_OnlyFirstSucceeds() {
            // Given - 建立 PENDING 訂單
            Location pickup = new Location(10.0, 20.0);
            Location dropoff = new Location(30.0, 40.0);
            Order order = orderService.createOrder("passenger-1", pickup, dropoff, VehicleType.STANDARD);

            // 建立多個司機
            for (int i = 1; i <= 3; i++) {
                Driver driver = Driver.builder()
                        .driverId("driver-" + i)
                        .name("Driver " + i)
                        .status(DriverStatus.ONLINE)
                        .busy(false)
                        .vehicleType(VehicleType.STANDARD)
                        .location(new Location(15.0, 25.0))
                        .build();
                driverRepository.save(driver);
            }

            // When - 第一個司機接單成功
            Order acceptedOrder = orderService.acceptOrder(order.getOrderId(), "driver-1");
            assertEquals(OrderStatus.ACCEPTED, acceptedOrder.getStatus());
            assertEquals("driver-1", acceptedOrder.getDriverId());

            // Then - 第二個司機接單應失敗 (訂單已被接受)
            BusinessException ex2 = assertThrows(BusinessException.class, () ->
                orderService.acceptOrder(order.getOrderId(), "driver-2")
            );
            assertEquals("ORDER_ALREADY_ACCEPTED", ex2.getCode());
            assertEquals(409, ex2.getHttpStatus());

            // 第三個司機接單也應失敗
            BusinessException ex3 = assertThrows(BusinessException.class, () ->
                orderService.acceptOrder(order.getOrderId(), "driver-3")
            );
            assertEquals("ORDER_ALREADY_ACCEPTED", ex3.getCode());

            // 驗證最終狀態
            Order finalOrder = orderRepository.findById(order.getOrderId()).orElseThrow();
            assertEquals(OrderStatus.ACCEPTED, finalOrder.getStatus());
            assertEquals("driver-1", finalOrder.getDriverId(), "應該只有第一個司機被分配");
        }

        @Test
        @DisplayName("UT-SM-01-03: PENDING -> ACCEPTED 需要符合前置條件（司機在線）")
        void testPendingToAccepted_RequiresDriverOnline() {
            // Given - 建立 PENDING 訂單
            Location pickup = new Location(10.0, 20.0);
            Location dropoff = new Location(30.0, 40.0);
            Order order = orderService.createOrder("passenger-1", pickup, dropoff, VehicleType.STANDARD);

            // 建立離線司機
            Driver driver = Driver.builder()
                    .driverId("driver-1")
                    .name("Test Driver")
                    .status(DriverStatus.OFFLINE)
                    .busy(false)
                    .vehicleType(VehicleType.STANDARD)
                    .location(new Location(15.0, 25.0))
                    .build();
            driverRepository.save(driver);

            // When & Then - 離線司機接單應失敗
            BusinessException ex = assertThrows(BusinessException.class, () ->
                orderService.acceptOrder(order.getOrderId(), "driver-1")
            );
            assertEquals("DRIVER_OFFLINE", ex.getCode());

            // 訂單狀態應保持 PENDING
            Order unchangedOrder = orderRepository.findById(order.getOrderId()).orElseThrow();
            assertEquals(OrderStatus.PENDING, unchangedOrder.getStatus());
        }

        @Test
        @DisplayName("UT-SM-01-04: PENDING -> ACCEPTED 需要符合前置條件（司機閒置）")
        void testPendingToAccepted_RequiresDriverNotBusy() {
            // Given - 建立 PENDING 訂單
            Location pickup = new Location(10.0, 20.0);
            Location dropoff = new Location(30.0, 40.0);
            Order order = orderService.createOrder("passenger-1", pickup, dropoff, VehicleType.STANDARD);

            // 建立忙碌司機
            Driver driver = Driver.builder()
                    .driverId("driver-1")
                    .name("Test Driver")
                    .status(DriverStatus.ONLINE)
                    .busy(true)
                    .vehicleType(VehicleType.STANDARD)
                    .location(new Location(15.0, 25.0))
                    .build();
            driverRepository.save(driver);

            // When & Then - 忙碌司機接單應失敗
            BusinessException ex = assertThrows(BusinessException.class, () ->
                orderService.acceptOrder(order.getOrderId(), "driver-1")
            );
            assertEquals("DRIVER_BUSY", ex.getCode());

            // 訂單狀態應保持 PENDING
            Order unchangedOrder = orderRepository.findById(order.getOrderId()).orElseThrow();
            assertEquals(OrderStatus.PENDING, unchangedOrder.getStatus());
        }
    }

    // =========================================================================
    // UT-SM-02: PENDING -> ONGOING 非法轉換測試
    // =========================================================================

    @Nested
    @DisplayName("UT-SM-02: PENDING -> ONGOING 非法轉換")
    class PendingToOngoingTests {

        @Test
        @DisplayName("UT-SM-02-01: PENDING 訂單直接開始行程應失敗")
        void testPendingToOngoing_DirectTransition_ShouldFail() {
            // Given - 建立 PENDING 訂單
            Location pickup = new Location(10.0, 20.0);
            Location dropoff = new Location(30.0, 40.0);
            Order order = orderService.createOrder("passenger-1", pickup, dropoff, VehicleType.STANDARD);

            assertEquals(OrderStatus.PENDING, order.getStatus());

            // When & Then - 嘗試直接開始行程應失敗
            BusinessException ex = assertThrows(BusinessException.class, () ->
                orderService.startTrip(order.getOrderId(), "driver-1")
            );
            assertEquals("INVALID_STATE", ex.getCode());
            assertTrue(ex.getMessage().contains("不允許") || ex.getMessage().contains("狀態"));

            // 訂單狀態應保持 PENDING
            Order unchangedOrder = orderRepository.findById(order.getOrderId()).orElseThrow();
            assertEquals(OrderStatus.PENDING, unchangedOrder.getStatus());
        }

        @Test
        @DisplayName("UT-SM-02-02: PENDING 狀態跳過 ACCEPTED 應失敗")
        void testPendingToOngoing_SkipAccepted_ShouldFail() {
            // Given - 建立 PENDING 訂單
            Location pickup = new Location(10.0, 20.0);
            Location dropoff = new Location(30.0, 40.0);
            Order order = orderService.createOrder("passenger-1", pickup, dropoff, VehicleType.STANDARD);

            // 建立司機
            Driver driver = Driver.builder()
                    .driverId("driver-1")
                    .name("Test Driver")
                    .status(DriverStatus.ONLINE)
                    .busy(false)
                    .vehicleType(VehicleType.STANDARD)
                    .location(new Location(15.0, 25.0))
                    .build();
            driverRepository.save(driver);

            // When & Then - 未接單就開始行程應失敗
            BusinessException ex = assertThrows(BusinessException.class, () ->
                orderService.startTrip(order.getOrderId(), "driver-1")
            );
            assertEquals("INVALID_STATE", ex.getCode());

            // 訂單應保持 PENDING
            Order unchangedOrder = orderRepository.findById(order.getOrderId()).orElseThrow();
            assertEquals(OrderStatus.PENDING, unchangedOrder.getStatus());
            assertNull(unchangedOrder.getDriverId(), "不應有司機被分配");
            assertNull(unchangedOrder.getStartedAt(), "不應有開始時間");
        }

        @Test
        @DisplayName("UT-SM-02-03: 正確流程應為 PENDING -> ACCEPTED -> ONGOING")
        void testCorrectFlow_PendingToAcceptedToOngoing() {
            // Given - 建立 PENDING 訂單
            Location pickup = new Location(10.0, 20.0);
            Location dropoff = new Location(30.0, 40.0);
            Order order = orderService.createOrder("passenger-1", pickup, dropoff, VehicleType.STANDARD);

            Driver driver = Driver.builder()
                    .driverId("driver-1")
                    .name("Test Driver")
                    .status(DriverStatus.ONLINE)
                    .busy(false)
                    .vehicleType(VehicleType.STANDARD)
                    .location(new Location(15.0, 25.0))
                    .build();
            driverRepository.save(driver);

            // When - 正確的流程
            // Step 1: PENDING -> ACCEPTED
            Order acceptedOrder = orderService.acceptOrder(order.getOrderId(), "driver-1");
            assertEquals(OrderStatus.ACCEPTED, acceptedOrder.getStatus());

            // Step 2: ACCEPTED -> ONGOING (這才是合法的)
            Order ongoingOrder = orderService.startTrip(order.getOrderId(), "driver-1");

            // Then - 驗證最終狀態
            assertEquals(OrderStatus.ONGOING, ongoingOrder.getStatus());
            assertNotNull(ongoingOrder.getStartedAt());
        }
    }

    // =========================================================================
    // UT-SM-03: COMPLETED 不可變更測試
    // =========================================================================

    @Nested
    @DisplayName("UT-SM-03: COMPLETED 不可變更")
    class CompletedImmutableTests {

        private Order completedOrder;

        @BeforeEach
        void setupCompletedOrder() {
            // 建立一個完整流程的已完成訂單
            Location pickup = new Location(10.0, 20.0);
            Location dropoff = new Location(30.0, 40.0);
            Order order = orderService.createOrder("passenger-1", pickup, dropoff, VehicleType.STANDARD);

            Driver driver = Driver.builder()
                    .driverId("driver-1")
                    .name("Test Driver")
                    .status(DriverStatus.ONLINE)
                    .busy(false)
                    .vehicleType(VehicleType.STANDARD)
                    .location(new Location(15.0, 25.0))
                    .build();
            driverRepository.save(driver);

            // 完整流程：PENDING -> ACCEPTED -> ONGOING -> COMPLETED
            order = orderService.acceptOrder(order.getOrderId(), "driver-1");
            order = orderService.startTrip(order.getOrderId(), "driver-1");
            completedOrder = orderService.completeTrip(order.getOrderId(), "driver-1");

            assertEquals(OrderStatus.COMPLETED, completedOrder.getStatus());
        }

        @Test
        @DisplayName("UT-SM-03-01: COMPLETED 訂單不可被取消")
        void testCompleted_CannotBeCancelled() {
            // When & Then - 嘗試取消已完成訂單應失敗
            String orderId = completedOrder.getOrderId();
            BusinessException ex = assertThrows(BusinessException.class, () ->
                orderService.cancelOrder(orderId, "passenger-1")
            );
            assertEquals("INVALID_STATE", ex.getCode());
            assertNotNull(ex.getMessage(), "應有錯誤訊息");

            // 訂單狀態應保持 COMPLETED
            Order unchangedOrder = orderRepository.findById(orderId).orElseThrow();
            assertEquals(OrderStatus.COMPLETED, unchangedOrder.getStatus());
        }

        @Test
        @DisplayName("UT-SM-03-02: COMPLETED 訂單不可重新接單")
        void testCompleted_CannotBeReaccepted() {
            // Given - 建立另一個司機
            Driver driver2 = Driver.builder()
                    .driverId("driver-2")
                    .name("Another Driver")
                    .status(DriverStatus.ONLINE)
                    .busy(false)
                    .vehicleType(VehicleType.STANDARD)
                    .location(new Location(20.0, 30.0))
                    .build();
            driverRepository.save(driver2);

            // When & Then - 嘗試重新接單應失敗
            String orderId = completedOrder.getOrderId();
            BusinessException ex = assertThrows(BusinessException.class, () ->
                orderService.acceptOrder(orderId, "driver-2")
            );
            assertEquals("INVALID_STATE", ex.getCode());

            // 訂單狀態應保持 COMPLETED，司機應保持原始
            Order unchangedOrder = orderRepository.findById(orderId).orElseThrow();
            assertEquals(OrderStatus.COMPLETED, unchangedOrder.getStatus());
            assertEquals("driver-1", unchangedOrder.getDriverId());
        }

        @Test
        @DisplayName("UT-SM-03-03: COMPLETED 訂單不可重新開始")
        void testCompleted_CannotBeRestarted() {
            // When & Then - 嘗試重新開始應失敗
            String orderId = completedOrder.getOrderId();
            BusinessException ex = assertThrows(BusinessException.class, () ->
                orderService.startTrip(orderId, "driver-1")
            );
            assertEquals("INVALID_STATE", ex.getCode());

            // 訂單狀態應保持 COMPLETED
            Order unchangedOrder = orderRepository.findById(orderId).orElseThrow();
            assertEquals(OrderStatus.COMPLETED, unchangedOrder.getStatus());
        }

        @Test
        @DisplayName("UT-SM-03-04: COMPLETED 是終止狀態，所有欄位應保持不變")
        void testCompleted_AllFieldsRemainUnchanged() {
            // Given - 記錄完成時的所有欄位
            String originalDriverId = completedOrder.getDriverId();
            double originalFare = completedOrder.getActualFare();
            var originalCompletedAt = completedOrder.getCompletedAt();

            // When - 嘗試任何非法操作（都會失敗）
            String orderId = completedOrder.getOrderId();
            assertThrows(BusinessException.class, () ->
                orderService.cancelOrder(orderId, "passenger-1")
            );

            // Then - 所有欄位應保持不變
            Order unchangedOrder = orderRepository.findById(orderId).orElseThrow();
            assertEquals(OrderStatus.COMPLETED, unchangedOrder.getStatus());
            assertEquals(originalDriverId, unchangedOrder.getDriverId());
            assertEquals(originalFare, unchangedOrder.getActualFare());
            assertEquals(originalCompletedAt, unchangedOrder.getCompletedAt());
            assertNotNull(unchangedOrder.getActualFare(), "實際費用應已計算");
            assertTrue(unchangedOrder.getActualFare() > 0, "實際費用應大於0");
        }
    }

    // =========================================================================
    // UT-SM-04: CANCELLED 不可變更測試
    // =========================================================================

    @Nested
    @DisplayName("UT-SM-04: CANCELLED 不可變更")
    class CancelledImmutableTests {

        @Test
        @DisplayName("UT-SM-04-01: CANCELLED 訂單不可被接單")
        void testCancelled_CannotBeAccepted() {
            // Given - 建立並取消訂單
            Location pickup = new Location(10.0, 20.0);
            Location dropoff = new Location(30.0, 40.0);
            Order order = orderService.createOrder("passenger-1", pickup, dropoff, VehicleType.STANDARD);
            Order cancelledOrder = orderService.cancelOrder(order.getOrderId(), "passenger-1");

            assertEquals(OrderStatus.CANCELLED, cancelledOrder.getStatus());

            // 建立司機
            Driver driver = Driver.builder()
                    .driverId("driver-1")
                    .name("Test Driver")
                    .status(DriverStatus.ONLINE)
                    .busy(false)
                    .vehicleType(VehicleType.STANDARD)
                    .location(new Location(15.0, 25.0))
                    .build();
            driverRepository.save(driver);

            // When & Then - 嘗試接單應失敗
            String orderId = cancelledOrder.getOrderId();
            BusinessException ex = assertThrows(BusinessException.class, () ->
                orderService.acceptOrder(orderId, "driver-1")
            );
            assertEquals("INVALID_STATE", ex.getCode());
            assertNotNull(ex.getMessage(), "應有錯誤訊息");

            // 訂單狀態應保持 CANCELLED
            Order unchangedOrder = orderRepository.findById(orderId).orElseThrow();
            assertEquals(OrderStatus.CANCELLED, unchangedOrder.getStatus());
        }

        @Test
        @DisplayName("UT-SM-04-02: CANCELLED 訂單不可開始行程")
        void testCancelled_CannotBeStarted() {
            // Given - 建立並取消訂單
            Location pickup = new Location(10.0, 20.0);
            Location dropoff = new Location(30.0, 40.0);
            Order order = orderService.createOrder("passenger-1", pickup, dropoff, VehicleType.STANDARD);
            Order cancelledOrder = orderService.cancelOrder(order.getOrderId(), "passenger-1");

            // When & Then - 嘗試開始行程應失敗
            String orderId = cancelledOrder.getOrderId();
            BusinessException ex = assertThrows(BusinessException.class, () ->
                orderService.startTrip(orderId, "driver-1")
            );
            assertEquals("INVALID_STATE", ex.getCode());

            // 訂單狀態應保持 CANCELLED
            Order unchangedOrder = orderRepository.findById(orderId).orElseThrow();
            assertEquals(OrderStatus.CANCELLED, unchangedOrder.getStatus());
        }

        @Test
        @DisplayName("UT-SM-04-03: CANCELLED 訂單重複取消應保持冪等（不拋出異常）")
        void testCancelled_IdempotentCancellation() {
            // Given - 建立並取消訂單
            Location pickup = new Location(10.0, 20.0);
            Location dropoff = new Location(30.0, 40.0);
            Order order = orderService.createOrder("passenger-1", pickup, dropoff, VehicleType.STANDARD);
            Order cancelledOrder = orderService.cancelOrder(order.getOrderId(), "passenger-1");

            assertEquals(OrderStatus.CANCELLED, cancelledOrder.getStatus());
            double originalCancelFee = cancelledOrder.getCancelFee();

            // When - 重複取消（冪等性）
            Order result = orderService.cancelOrder(cancelledOrder.getOrderId(), "passenger-1");

            // Then - 應成功返回，狀態保持 CANCELLED
            assertEquals(OrderStatus.CANCELLED, result.getStatus());
            assertEquals(originalCancelFee, result.getCancelFee(), "取消費用不應改變");

            // Repository 中的資料也應保持不變
            Order unchangedOrder = orderRepository.findById(cancelledOrder.getOrderId()).orElseThrow();
            assertEquals(OrderStatus.CANCELLED, unchangedOrder.getStatus());
        }

        @Test
        @DisplayName("UT-SM-04-04: ACCEPTED 後取消的訂單不可恢復")
        void testCancelled_AfterAccepted_CannotBeRestored() {
            // Given - 建立訂單並接單後取消
            Location pickup = new Location(10.0, 20.0);
            Location dropoff = new Location(30.0, 40.0);
            Order order = orderService.createOrder("passenger-1", pickup, dropoff, VehicleType.STANDARD);

            Driver driver = Driver.builder()
                    .driverId("driver-1")
                    .name("Test Driver")
                    .status(DriverStatus.ONLINE)
                    .busy(false)
                    .vehicleType(VehicleType.STANDARD)
                    .location(new Location(15.0, 25.0))
                    .build();
            driverRepository.save(driver);

            order = orderService.acceptOrder(order.getOrderId(), "driver-1");
            Order cancelledOrder = orderService.cancelOrder(order.getOrderId(), "passenger-1");

            assertEquals(OrderStatus.CANCELLED, cancelledOrder.getStatus());
            assertTrue(cancelledOrder.getCancelFee() > 0, "已接單後取消應有取消費");

            // When & Then - 嘗試重新接單應失敗
            String orderId = cancelledOrder.getOrderId();
            BusinessException ex = assertThrows(BusinessException.class, () ->
                orderService.acceptOrder(orderId, "driver-1")
            );
            assertEquals("INVALID_STATE", ex.getCode());

            // 訂單應保持 CANCELLED
            Order unchangedOrder = orderRepository.findById(orderId).orElseThrow();
            assertEquals(OrderStatus.CANCELLED, unchangedOrder.getStatus());
        }

        @Test
        @DisplayName("UT-SM-04-05: CANCELLED 是終止狀態，所有欄位應凍結")
        void testCancelled_AllFieldsFrozen() {
            // Given - 建立並取消訂單
            Location pickup = new Location(10.0, 20.0);
            Location dropoff = new Location(30.0, 40.0);
            Order order = orderService.createOrder("passenger-1", pickup, dropoff, VehicleType.STANDARD);
            Order cancelledOrder = orderService.cancelOrder(order.getOrderId(), "passenger-1");

            // 記錄取消時的狀態
            String originalPassengerId = cancelledOrder.getPassengerId();
            double originalCancelFee = cancelledOrder.getCancelFee();
            var originalCancelledAt = cancelledOrder.getCancelledAt();

            // When - 嘗試任何操作（都會失敗或保持冪等）
            orderService.cancelOrder(cancelledOrder.getOrderId(), "passenger-1");

            // Then - 所有欄位應保持不變
            Order unchangedOrder = orderRepository.findById(cancelledOrder.getOrderId()).orElseThrow();
            assertEquals(OrderStatus.CANCELLED, unchangedOrder.getStatus());
            assertEquals(originalPassengerId, unchangedOrder.getPassengerId());
            assertEquals(originalCancelFee, unchangedOrder.getCancelFee());
            assertEquals(originalCancelledAt, unchangedOrder.getCancelledAt());
        }
    }

    // =========================================================================
    // 綜合狀態機測試
    // =========================================================================

    @Nested
    @DisplayName("綜合狀態機測試")
    class ComprehensiveStateMachineTests {

        @Test
        @DisplayName("UT-SM-05: 完整的訂單生命週期測試")
        void testCompleteOrderLifecycle() {
            // Given - 準備環境
            Location pickup = new Location(10.0, 20.0);
            Location dropoff = new Location(30.0, 40.0);

            Driver driver = Driver.builder()
                    .driverId("driver-1")
                    .name("Test Driver")
                    .status(DriverStatus.ONLINE)
                    .busy(false)
                    .vehicleType(VehicleType.STANDARD)
                    .location(new Location(15.0, 25.0))
                    .build();
            driverRepository.save(driver);

            // When & Then - 測試完整流程
            // Step 1: CREATE -> PENDING
            Order order = orderService.createOrder("passenger-1", pickup, dropoff, VehicleType.STANDARD);
            assertEquals(OrderStatus.PENDING, order.getStatus());

            // Step 2: PENDING -> ACCEPTED
            order = orderService.acceptOrder(order.getOrderId(), "driver-1");
            assertEquals(OrderStatus.ACCEPTED, order.getStatus());

            // Step 3: ACCEPTED -> ONGOING
            order = orderService.startTrip(order.getOrderId(), "driver-1");
            assertEquals(OrderStatus.ONGOING, order.getStatus());

            // Step 4: ONGOING -> COMPLETED
            order = orderService.completeTrip(order.getOrderId(), "driver-1");
            assertEquals(OrderStatus.COMPLETED, order.getStatus());

            // Step 5: COMPLETED 是終止狀態
            String orderId = order.getOrderId();
            assertThrows(BusinessException.class, () ->
                orderService.cancelOrder(orderId, "passenger-1")
            );
        }

        @Test
        @DisplayName("UT-SM-06: 測試所有非法狀態轉換")
        void testAllIllegalTransitions() {
            // Test 1: PENDING -> ONGOING (非法)
            Order order1 = orderService.createOrder("passenger-1",
                new Location(10.0, 20.0), new Location(30.0, 40.0), VehicleType.STANDARD);
            String order1Id = order1.getOrderId();
            assertThrows(BusinessException.class, () ->
                orderService.startTrip(order1Id, "driver-1")
            );

            // Test 2: PENDING -> COMPLETED (非法)
            Order order2 = orderService.createOrder("passenger-2",
                new Location(10.0, 20.0), new Location(30.0, 40.0), VehicleType.STANDARD);
            String order2Id = order2.getOrderId();
            assertThrows(BusinessException.class, () ->
                orderService.completeTrip(order2Id, "driver-1")
            );

            // Test 3: ONGOING -> CANCELLED (非法)
            Driver driver = Driver.builder()
                    .driverId("driver-1")
                    .name("Test Driver")
                    .status(DriverStatus.ONLINE)
                    .busy(false)
                    .vehicleType(VehicleType.STANDARD)
                    .location(new Location(15.0, 25.0))
                    .build();
            driverRepository.save(driver);

            Order order3 = orderService.createOrder("passenger-3",
                new Location(10.0, 20.0), new Location(30.0, 40.0), VehicleType.STANDARD);
            order3 = orderService.acceptOrder(order3.getOrderId(), "driver-1");
            order3 = orderService.startTrip(order3.getOrderId(), "driver-1");
            String order3Id = order3.getOrderId();

            assertThrows(BusinessException.class, () ->
                orderService.cancelOrder(order3Id, "passenger-3")
            );
        }

        @Test
        @DisplayName("UT-SM-07: 驗證狀態轉換的時間戳記錄")
        void testStateTransitionTimestamps() {
            // Given
            Location pickup = new Location(10.0, 20.0);
            Location dropoff = new Location(30.0, 40.0);

            Driver driver = Driver.builder()
                    .driverId("driver-1")
                    .name("Test Driver")
                    .status(DriverStatus.ONLINE)
                    .busy(false)
                    .vehicleType(VehicleType.STANDARD)
                    .location(new Location(15.0, 25.0))
                    .build();
            driverRepository.save(driver);

            // When - 執行完整流程
            Order order = orderService.createOrder("passenger-1", pickup, dropoff, VehicleType.STANDARD);
            assertNotNull(order.getCreatedAt(), "應記錄建立時間");

            order = orderService.acceptOrder(order.getOrderId(), "driver-1");
            assertNotNull(order.getAcceptedAt(), "應記錄接單時間");

            order = orderService.startTrip(order.getOrderId(), "driver-1");
            assertNotNull(order.getStartedAt(), "應記錄開始時間");

            order = orderService.completeTrip(order.getOrderId(), "driver-1");
            assertNotNull(order.getCompletedAt(), "應記錄完成時間");

            // Then - 驗證時間順序
            assertTrue(order.getCreatedAt().isBefore(order.getAcceptedAt()) ||
                      order.getCreatedAt().equals(order.getAcceptedAt()));
            assertTrue(order.getAcceptedAt().isBefore(order.getStartedAt()) ||
                      order.getAcceptedAt().equals(order.getStartedAt()));
            assertTrue(order.getStartedAt().isBefore(order.getCompletedAt()) ||
                      order.getStartedAt().equals(order.getCompletedAt()));
        }
    }
}

