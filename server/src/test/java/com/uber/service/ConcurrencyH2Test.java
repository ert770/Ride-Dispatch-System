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
 * H2 搶單併發控制整合測試
 * 
 * 測試場景:
 * - CT-H2: 10 個司機同時搶單，僅 1 人成功，其餘回傳 409
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConcurrencyH2Test {

    @Autowired
    private OrderService orderService;

    @Autowired
    private DriverService driverService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        // 清空資料
        orderRepository.deleteAll();
        driverRepository.deleteAll();
    }

    @Test
    @DisplayName("CT-H2: 10 司機同時搶單，僅 1 人成功")
    void testConcurrentAccept_OnlyOneSucceeds() throws InterruptedException {
        // Given: 建立 1 筆 PENDING 訂單
        Order order = orderService.createOrder(
                "passenger-001",
                new Location(10, 10),
                new Location(20, 20),
                VehicleType.STANDARD
        );
        
        String orderId = order.getOrderId();
        
        // 建立 10 個上線司機
        int driverCount = 10;
        List<String> driverIds = new ArrayList<>();
        for (int i = 0; i < driverCount; i++) {
            String driverId = "driver-" + String.format("%03d", i);
            driverService.registerDriver(driverId, "Driver " + i, "0900-000-00" + i, 
                    "ABC-" + i, VehicleType.STANDARD);
            driverService.goOnline(driverId, new Location(i, i));
            driverIds.add(driverId);
        }
        
        // When: 10 個執行緒同時接單
        ExecutorService executor = Executors.newFixedThreadPool(driverCount);
        CountDownLatch startLatch = new CountDownLatch(1);       // 同步開始
        CountDownLatch doneLatch = new CountDownLatch(driverCount); // 等待完成
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger otherErrorCount = new AtomicInteger(0);
        
        List<Future<?>> futures = new ArrayList<>();
        
        for (String driverId : driverIds) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await(); // 等待同時開始
                    orderService.acceptOrder(orderId, driverId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    String message = e.getMessage();
                    if (message != null && (message.contains("已被其他司機接受") || 
                            message.contains("CONFLICT") ||
                            message.contains("409"))) {
                        conflictCount.incrementAndGet();
                    } else if (message != null && message.contains("INVALID_STATE")) {
                        conflictCount.incrementAndGet(); // 也算是衝突
                    } else {
                        System.err.println("Unexpected error: " + e.getMessage());
                        otherErrorCount.incrementAndGet();
                    }
                } finally {
                    doneLatch.countDown();
                }
            }));
        }
        
        // 發出開始訊號
        startLatch.countDown();
        
        // 等待所有執行緒完成
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        
        // Then: 驗證結果
        assertTrue(completed, "所有執行緒應在時限內完成");
        assertEquals(1, successCount.get(), "應僅有 1 位司機成功接單");
        assertEquals(driverCount - 1, conflictCount.get(), 
                "應有 " + (driverCount - 1) + " 位司機收到衝突錯誤");
        assertEquals(0, otherErrorCount.get(), "不應有其他錯誤");
        
        // 驗證訂單狀態
        Order finalOrder = orderService.getOrder(orderId);
        assertEquals(OrderStatus.ACCEPTED, finalOrder.getStatus());
        assertNotNull(finalOrder.getDriverId());
        
        // 驗證只有一個司機的狀態變成 busy
        long busyCount = driverRepository.findAll().stream()
                .filter(Driver::isBusy)
                .count();
        assertEquals(1, busyCount, "應僅有 1 位司機狀態為 busy");
    }

    @Test
    @DisplayName("CT-H2: 較少司機併發測試")
    void testConcurrentAccept_ThreeDrivers() throws InterruptedException {
        // Given: 建立訂單和 3 個司機
        Order order = orderService.createOrder(
                "passenger-002",
                new Location(5, 5),
                new Location(15, 15),
                VehicleType.PREMIUM
        );
        
        String orderId = order.getOrderId();
        
        for (int i = 0; i < 3; i++) {
            String driverId = "premium-driver-" + i;
            driverService.registerDriver(driverId, "Premium " + i, "0911-111-111" + i, 
                    "PMD-" + i, VehicleType.PREMIUM);
            driverService.goOnline(driverId, new Location(i, i));
        }
        
        // When
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    orderService.acceptOrder(orderId, "premium-driver-" + idx);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 預期其他司機會失敗
                }
            });
        }
        
        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        
        // Then
        assertEquals(1, successCount.get());
    }

    @Test
    @DisplayName("CT-H2: 多訂單併發測試")
    void testConcurrentAccept_MultipleOrders() throws InterruptedException {
        // Given: 建立 3 筆訂單，每筆各有 3 個司機搶
        int orderCount = 3;
        int driversPerOrder = 3;
        
        List<String> orderIds = new ArrayList<>();
        
        for (int i = 0; i < orderCount; i++) {
            Order order = orderService.createOrder(
                    "passenger-" + i,
                    new Location(i * 10, i * 10),
                    new Location(i * 10 + 5, i * 10 + 5),
                    VehicleType.STANDARD
            );
            orderIds.add(order.getOrderId());
            
            // 為每筆訂單建立專屬司機
            for (int j = 0; j < driversPerOrder; j++) {
                String driverId = "driver-" + i + "-" + j;
                driverService.registerDriver(driverId, "Driver " + i + "-" + j, 
                        "0900-" + i + j, "XYZ-" + i + j, VehicleType.STANDARD);
                driverService.goOnline(driverId, new Location(i * 10, i * 10));
            }
        }
        
        // When: 每筆訂單的司機們同時搶單
        ExecutorService executor = Executors.newFixedThreadPool(orderCount * driversPerOrder);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger totalSuccess = new AtomicInteger(0);
        
        for (int i = 0; i < orderCount; i++) {
            final int orderIdx = i;
            final String orderId = orderIds.get(i);
            
            for (int j = 0; j < driversPerOrder; j++) {
                final int driverIdx = j;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        orderService.acceptOrder(orderId, "driver-" + orderIdx + "-" + driverIdx);
                        totalSuccess.incrementAndGet();
                    } catch (Exception e) {
                        // 預期失敗
                    }
                });
            }
        }
        
        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        
        // Then: 每筆訂單應僅有 1 人成功，共 3 人成功
        assertEquals(orderCount, totalSuccess.get(), 
                "每筆訂單應有 1 人成功，共 " + orderCount + " 人成功");
    }

    @Test
    @DisplayName("CT-H2: AuditLog 記錄正確")
    void testConcurrentAccept_AuditLog() throws InterruptedException {
        // Given
        Order order = orderService.createOrder(
                "passenger-audit",
                new Location(0, 0),
                new Location(10, 10),
                VehicleType.XL
        );
        
        String orderId = order.getOrderId();
        
        for (int i = 0; i < 5; i++) {
            String driverId = "xl-driver-" + i;
            driverService.registerDriver(driverId, "XL Driver " + i, "0922-222-222" + i, 
                    "XL-" + i, VehicleType.XL);
            driverService.goOnline(driverId, new Location(i, i));
        }
        
        // When
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch startLatch = new CountDownLatch(1);
        
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    orderService.acceptOrder(orderId, "xl-driver-" + idx);
                } catch (Exception e) {
                    // 預期部分失敗
                }
            });
        }
        
        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        
        // Then: 驗證 AuditLog
        var stats = auditService.getAcceptStats(orderId);
        
        assertEquals(1L, stats.get("success"), "應記錄 1 次成功");
        assertEquals(4L, stats.get("failure"), "應記錄 4 次失敗");
    }
}
