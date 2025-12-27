package com.uber.service;

import com.uber.model.Driver;
import com.uber.model.Location;
import com.uber.model.Order;
import com.uber.model.OrderStatus;
import com.uber.model.VehicleType;
import com.uber.repository.DriverRepository;
import com.uber.repository.OrderRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * H4 冪等性控制整合測試
 * 
 * 測試場景:
 * - 重複執行相同操作不產生副作用
 * - 所有操作都應保持冪等性
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IdempotencyH4Test {

    @Autowired
    private OrderService orderService;

    @Autowired
    private DriverService driverService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DriverRepository driverRepository;

    @BeforeEach
    void setUp() {
        // 清空資料
        orderRepository.deleteAll();
        driverRepository.deleteAll();
    }

    @Nested
    @DisplayName("Accept 操作冪等性測試")
    class AcceptIdempotencyTests {

        @Test
        @DisplayName("H4: 同一司機重複接單應返回成功")
        void testAccept_SameDriver_Idempotent() {
            // Given: 建立訂單和司機
            Order order = orderService.createOrder(
                    "passenger-001",
                    new Location(10, 10),
                    new Location(20, 20),
                    VehicleType.STANDARD
            );
            String orderId = order.getOrderId();
            
            driverService.registerDriver("driver-001", "Driver 1", "0900-000-001", 
                    "ABC-001", VehicleType.STANDARD);
            driverService.goOnline("driver-001", new Location(5, 5));
            
            // When: 第一次接單
            Order accepted1 = orderService.acceptOrder(orderId, "driver-001");
            
            // Then: 第二次接單應該返回相同結果，不拋錯
            Order accepted2 = orderService.acceptOrder(orderId, "driver-001");
            
            assertEquals(accepted1.getStatus(), accepted2.getStatus());
            assertEquals(accepted1.getDriverId(), accepted2.getDriverId());
            assertEquals(OrderStatus.ACCEPTED, accepted2.getStatus());
        }

        @Test
        @DisplayName("H4: 同一司機連續多次接單")
        void testAccept_SameDriver_MultipleTimes() {
            // Given
            Order order = orderService.createOrder(
                    "passenger-002",
                    new Location(0, 0),
                    new Location(10, 10),
                    VehicleType.PREMIUM
            );
            
            driverService.registerDriver("driver-002", "Driver 2", "0900-000-002", 
                    "ABC-002", VehicleType.PREMIUM);
            driverService.goOnline("driver-002", new Location(1, 1));
            
            // When: 連續 5 次接單
            List<Order> results = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                results.add(orderService.acceptOrder(order.getOrderId(), "driver-002"));
            }
            
            // Then: 所有結果應該一致
            for (Order result : results) {
                assertEquals(OrderStatus.ACCEPTED, result.getStatus());
                assertEquals("driver-002", result.getDriverId());
            }
        }

        @Test
        @DisplayName("H4: 併發重複接單")
        void testAccept_SameDriver_ConcurrentIdempotent() throws InterruptedException {
            // Given
            Order order = orderService.createOrder(
                    "passenger-003",
                    new Location(5, 5),
                    new Location(15, 15),
                    VehicleType.STANDARD
            );
            
            driverService.registerDriver("driver-003", "Driver 3", "0900-000-003", 
                    "ABC-003", VehicleType.STANDARD);
            driverService.goOnline("driver-003", new Location(3, 3));
            
            // When: 同一司機 5 個執行緒同時接單
            ExecutorService executor = Executors.newFixedThreadPool(5);
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);
            
            for (int i = 0; i < 5; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        orderService.acceptOrder(order.getOrderId(), "driver-003");
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    }
                });
            }
            
            startLatch.countDown();
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            
            // Then: 所有請求都應成功（冪等性）
            assertEquals(5, successCount.get(), "所有重複請求都應成功");
            assertEquals(0, errorCount.get(), "不應有錯誤");
            
            // 驗證訂單狀態正確
            Order finalOrder = orderService.getOrder(order.getOrderId());
            assertEquals(OrderStatus.ACCEPTED, finalOrder.getStatus());
            assertEquals("driver-003", finalOrder.getDriverId());
        }
    }

    @Nested
    @DisplayName("Start 操作冪等性測試")
    class StartIdempotencyTests {

        @Test
        @DisplayName("H4: 重複開始行程應返回成功")
        void testStart_Idempotent() {
            // Given: 建立已接單的訂單
            Order order = orderService.createOrder(
                    "passenger-004",
                    new Location(0, 0),
                    new Location(10, 10),
                    VehicleType.STANDARD
            );
            
            driverService.registerDriver("driver-004", "Driver 4", "0900-000-004", 
                    "ABC-004", VehicleType.STANDARD);
            driverService.goOnline("driver-004", new Location(0, 0));
            orderService.acceptOrder(order.getOrderId(), "driver-004");
            
            // When: 重複開始行程
            Order started1 = orderService.startTrip(order.getOrderId(), "driver-004");
            Order started2 = orderService.startTrip(order.getOrderId(), "driver-004");
            
            // Then: 兩次結果應一致
            assertEquals(OrderStatus.ONGOING, started1.getStatus());
            assertEquals(OrderStatus.ONGOING, started2.getStatus());
            assertEquals(started1.getStartedAt(), started2.getStartedAt());
        }

        @Test
        @DisplayName("H4: 併發開始行程")
        void testStart_ConcurrentIdempotent() throws InterruptedException {
            // Given
            Order order = orderService.createOrder(
                    "passenger-005",
                    new Location(5, 5),
                    new Location(20, 20),
                    VehicleType.PREMIUM
            );
            
            driverService.registerDriver("driver-005", "Driver 5", "0900-000-005", 
                    "ABC-005", VehicleType.PREMIUM);
            driverService.goOnline("driver-005", new Location(4, 4));
            orderService.acceptOrder(order.getOrderId(), "driver-005");
            
            // When: 3 個執行緒同時開始行程
            ExecutorService executor = Executors.newFixedThreadPool(3);
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger(0);
            
            for (int i = 0; i < 3; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        orderService.startTrip(order.getOrderId(), "driver-005");
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        // 不應發生
                        e.printStackTrace();
                    }
                });
            }
            
            startLatch.countDown();
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            
            // Then
            assertEquals(3, successCount.get(), "所有重複請求都應成功");
            
            Order finalOrder = orderService.getOrder(order.getOrderId());
            assertEquals(OrderStatus.ONGOING, finalOrder.getStatus());
        }
    }

    @Nested
    @DisplayName("Complete 操作冪等性測試")
    class CompleteIdempotencyTests {

        @Test
        @DisplayName("H4: 重複完成行程應返回成功")
        void testComplete_Idempotent() throws InterruptedException {
            // Given
            Order order = orderService.createOrder(
                    "passenger-006",
                    new Location(0, 0),
                    new Location(10, 10),
                    VehicleType.STANDARD
            );
            
            driverService.registerDriver("driver-006", "Driver 6", "0900-000-006", 
                    "ABC-006", VehicleType.STANDARD);
            driverService.goOnline("driver-006", new Location(0, 0));
            orderService.acceptOrder(order.getOrderId(), "driver-006");
            orderService.startTrip(order.getOrderId(), "driver-006");
            
            // 等待一小段時間讓行程有時間
            Thread.sleep(100);
            
            // When: 重複完成行程
            Order completed1 = orderService.completeTrip(order.getOrderId(), "driver-006");
            Order completed2 = orderService.completeTrip(order.getOrderId(), "driver-006");
            
            // Then
            assertEquals(OrderStatus.COMPLETED, completed1.getStatus());
            assertEquals(OrderStatus.COMPLETED, completed2.getStatus());
            assertEquals(completed1.getActualFare(), completed2.getActualFare());
        }

        @Test
        @DisplayName("H4: 完成後司機狀態正確")
        void testComplete_DriverStatusCorrect() {
            // Given
            Order order = orderService.createOrder(
                    "passenger-007",
                    new Location(0, 0),
                    new Location(5, 5),
                    VehicleType.XL
            );
            
            driverService.registerDriver("driver-007", "Driver 7", "0900-000-007", 
                    "ABC-007", VehicleType.XL);
            driverService.goOnline("driver-007", new Location(0, 0));
            orderService.acceptOrder(order.getOrderId(), "driver-007");
            orderService.startTrip(order.getOrderId(), "driver-007");
            orderService.completeTrip(order.getOrderId(), "driver-007");
            
            // When: 再次完成
            orderService.completeTrip(order.getOrderId(), "driver-007");
            
            // Then: 司機應該不是 busy
            Driver driver = driverService.getDriver("driver-007");
            assertFalse(driver.isBusy());
            assertNull(driver.getCurrentOrderId());
        }
    }

    @Nested
    @DisplayName("Cancel 操作冪等性測試")
    class CancelIdempotencyTests {

        @Test
        @DisplayName("H4: 重複取消訂單應返回成功")
        void testCancel_Idempotent() {
            // Given
            Order order = orderService.createOrder(
                    "passenger-008",
                    new Location(0, 0),
                    new Location(10, 10),
                    VehicleType.STANDARD
            );
            
            // When: 重複取消
            Order cancelled1 = orderService.cancelOrder(order.getOrderId(), "passenger-008");
            Order cancelled2 = orderService.cancelOrder(order.getOrderId(), "passenger-008");
            
            // Then
            assertEquals(OrderStatus.CANCELLED, cancelled1.getStatus());
            assertEquals(OrderStatus.CANCELLED, cancelled2.getStatus());
        }

        @Test
        @DisplayName("H4: 已接單後取消的冪等性")
        void testCancel_AfterAccept_Idempotent() {
            // Given
            Order order = orderService.createOrder(
                    "passenger-009",
                    new Location(0, 0),
                    new Location(10, 10),
                    VehicleType.PREMIUM
            );
            
            driverService.registerDriver("driver-009", "Driver 9", "0900-000-009", 
                    "ABC-009", VehicleType.PREMIUM);
            driverService.goOnline("driver-009", new Location(0, 0));
            orderService.acceptOrder(order.getOrderId(), "driver-009");
            
            // When: 重複取消
            Order cancelled1 = orderService.cancelOrder(order.getOrderId(), "passenger-009");
            Order cancelled2 = orderService.cancelOrder(order.getOrderId(), "passenger-009");
            
            // Then
            assertEquals(OrderStatus.CANCELLED, cancelled1.getStatus());
            assertEquals(OrderStatus.CANCELLED, cancelled2.getStatus());
            assertEquals(cancelled1.getCancelFee(), cancelled2.getCancelFee());
            
            // 驗證司機已釋放
            Driver driver = driverService.getDriver("driver-009");
            assertFalse(driver.isBusy());
        }

        @Test
        @DisplayName("H4: 併發取消")
        void testCancel_ConcurrentIdempotent() throws InterruptedException {
            // Given
            Order order = orderService.createOrder(
                    "passenger-010",
                    new Location(0, 0),
                    new Location(15, 15),
                    VehicleType.STANDARD
            );
            
            // When: 5 個執行緒同時取消
            ExecutorService executor = Executors.newFixedThreadPool(5);
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger(0);
            
            for (int i = 0; i < 5; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        orderService.cancelOrder(order.getOrderId(), "passenger-010");
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        // 不應發生
                    }
                });
            }
            
            startLatch.countDown();
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            
            // Then
            assertEquals(5, successCount.get());
            
            Order finalOrder = orderService.getOrder(order.getOrderId());
            assertEquals(OrderStatus.CANCELLED, finalOrder.getStatus());
        }
    }

    @Nested
    @DisplayName("完整流程冪等性測試")
    class FullFlowIdempotencyTests {

        @Test
        @DisplayName("H4: 完整 Happy Path 每步驟都支援冪等性")
        void testFullFlow_Idempotent() throws InterruptedException {
            // 建立訂單 (CREATE 本身冪等性透過 UUID 保證)
            Order order = orderService.createOrder(
                    "passenger-full",
                    new Location(0, 0),
                    new Location(20, 20),
                    VehicleType.STANDARD
            );
            
            driverService.registerDriver("driver-full", "Full Driver", "0900-000-999", 
                    "FULL-001", VehicleType.STANDARD);
            driverService.goOnline("driver-full", new Location(2, 2));
            
            // 重複接單
            orderService.acceptOrder(order.getOrderId(), "driver-full");
            orderService.acceptOrder(order.getOrderId(), "driver-full");
            assertEquals(OrderStatus.ACCEPTED, orderService.getOrder(order.getOrderId()).getStatus());
            
            // 重複開始行程
            orderService.startTrip(order.getOrderId(), "driver-full");
            orderService.startTrip(order.getOrderId(), "driver-full");
            assertEquals(OrderStatus.ONGOING, orderService.getOrder(order.getOrderId()).getStatus());
            
            Thread.sleep(100);
            
            // 重複完成行程
            orderService.completeTrip(order.getOrderId(), "driver-full");
            orderService.completeTrip(order.getOrderId(), "driver-full");
            
            Order finalOrder = orderService.getOrder(order.getOrderId());
            assertEquals(OrderStatus.COMPLETED, finalOrder.getStatus());
            assertNotNull(finalOrder.getActualFare());
            assertTrue(finalOrder.getActualFare() > 0);
        }
    }
}
