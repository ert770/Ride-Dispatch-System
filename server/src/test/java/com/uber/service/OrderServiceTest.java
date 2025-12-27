package com.uber.service;

import com.uber.exception.BusinessException;
import com.uber.model.*;
import com.uber.repository.DriverRepository;
import com.uber.repository.OrderRepository;
import com.uber.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OrderService 單元測試
 * 
 * 驗證 Issue #1 的所有驗收標準：
 * - createOrder() 建立新訂單
 * - acceptOrder() 司機接單（含併發控制 H2）
 * - startTrip() 開始行程
 * - completeTrip() 完成行程
 * - cancelOrder() 取消訂單
 * - 狀態轉換符合 docs/state-machine.md 規範
 */
class OrderServiceTest {
    
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
    // createOrder() 測試
    // =========================================================================
    
    @Nested
    @DisplayName("createOrder() - 建立訂單")
    class CreateOrderTests {
        
        @Test
        @DisplayName("UT-C01: 成功建立訂單")
        void testCreateOrder_Success() {
            // Given
            Location pickup = new Location(10.0, 20.0);
            Location dropoff = new Location(30.0, 40.0);
            
            // When
            Order order = orderService.createOrder("passenger-1", pickup, dropoff, VehicleType.STANDARD);
            
            // Then
            assertNotNull(order);
            assertNotNull(order.getOrderId());
            assertEquals(OrderStatus.PENDING, order.getStatus());
            assertEquals("passenger-1", order.getPassengerId());
            assertEquals(VehicleType.STANDARD, order.getVehicleType());
            assertTrue(order.getEstimatedFare() > 0);
            assertNotNull(order.getCreatedAt());
        }
        
        @Test
        @DisplayName("UT-C02: 上下車點相同應拒絕")
        void testCreateOrder_SameLocation_ShouldFail() {
            // Given
            Location location = new Location(10.0, 20.0);
            
            // When & Then
            BusinessException ex = assertThrows(BusinessException.class, () -> 
                orderService.createOrder("passenger-1", location, location, VehicleType.STANDARD)
            );
            assertEquals("INVALID_REQUEST", ex.getCode());
        }
    }
    
    // =========================================================================
    // acceptOrder() 測試
    // =========================================================================
    
    @Nested
    @DisplayName("acceptOrder() - 接受訂單")
    class AcceptOrderTests {
        
        private Order pendingOrder;
        private Driver onlineDriver;
        
        @BeforeEach
        void createTestData() {
            // 建立待派單訂單
            Location pickup = new Location(10.0, 20.0);
            Location dropoff = new Location(30.0, 40.0);
            pendingOrder = orderService.createOrder("passenger-1", pickup, dropoff, VehicleType.STANDARD);
            
            // 建立上線司機
            onlineDriver = Driver.builder()
                    .driverId("driver-1")
                    .name("Test Driver")
                    .status(DriverStatus.ONLINE)
                    .busy(false)
                    .vehicleType(VehicleType.STANDARD)
                    .location(new Location(15.0, 25.0))
                    .build();
            driverRepository.save(onlineDriver);
        }
        
        @Test
        @DisplayName("UT-A01: PENDING → ACCEPTED 合法轉換")
        void testAcceptOrder_PendingToAccepted_Success() {
            // When
            Order result = orderService.acceptOrder(pendingOrder.getOrderId(), "driver-1");
            
            // Then
            assertEquals(OrderStatus.ACCEPTED, result.getStatus());
            assertEquals("driver-1", result.getDriverId());
            assertNotNull(result.getAcceptedAt());
        }
        
        @Test
        @DisplayName("UT-A02: H4 冪等性 - 同一司機重複接單應成功")
        void testAcceptOrder_Idempotent_SameDriver() {
            // Given - 第一次接單
            orderService.acceptOrder(pendingOrder.getOrderId(), "driver-1");
            
            // When - 第二次接單（同一司機）
            Order result = orderService.acceptOrder(pendingOrder.getOrderId(), "driver-1");
            
            // Then
            assertEquals(OrderStatus.ACCEPTED, result.getStatus());
            assertEquals("driver-1", result.getDriverId());
        }
        
        @Test
        @DisplayName("UT-A03: 訂單已被其他司機接受應回傳 409")
        void testAcceptOrder_AlreadyAcceptedByOther_ShouldFail() {
            // Given - 第一位司機接單
            orderService.acceptOrder(pendingOrder.getOrderId(), "driver-1");
            
            // 建立第二位司機
            Driver driver2 = Driver.builder()
                    .driverId("driver-2")
                    .name("Test Driver 2")
                    .status(DriverStatus.ONLINE)
                    .busy(false)
                    .vehicleType(VehicleType.STANDARD)
                    .location(new Location(20.0, 30.0))
                    .build();
            driverRepository.save(driver2);
            
            // When & Then
            BusinessException ex = assertThrows(BusinessException.class, () -> 
                orderService.acceptOrder(pendingOrder.getOrderId(), "driver-2")
            );
            assertEquals("ORDER_ALREADY_ACCEPTED", ex.getCode());
            assertEquals(409, ex.getHttpStatus());
        }
        
        @Test
        @DisplayName("UT-A04: 離線司機不可接單")
        void testAcceptOrder_OfflineDriver_ShouldFail() {
            // Given
            onlineDriver.setStatus(DriverStatus.OFFLINE);
            driverRepository.save(onlineDriver);
            
            // When & Then
            BusinessException ex = assertThrows(BusinessException.class, () -> 
                orderService.acceptOrder(pendingOrder.getOrderId(), "driver-1")
            );
            assertEquals("DRIVER_OFFLINE", ex.getCode());
        }
        
        @Test
        @DisplayName("UT-A05: 忙碌司機不可接單")
        void testAcceptOrder_BusyDriver_ShouldFail() {
            // Given
            onlineDriver.setBusy(true);
            driverRepository.save(onlineDriver);
            
            // When & Then
            BusinessException ex = assertThrows(BusinessException.class, () -> 
                orderService.acceptOrder(pendingOrder.getOrderId(), "driver-1")
            );
            assertEquals("DRIVER_BUSY", ex.getCode());
        }
    }
    
    // =========================================================================
    // startTrip() 測試
    // =========================================================================
    
    @Nested
    @DisplayName("startTrip() - 開始行程")
    class StartTripTests {
        
        private Order acceptedOrder;
        
        @BeforeEach
        void createTestData() {
            // 建立已接單訂單
            Location pickup = new Location(10.0, 20.0);
            Location dropoff = new Location(30.0, 40.0);
            acceptedOrder = orderService.createOrder("passenger-1", pickup, dropoff, VehicleType.STANDARD);
            
            Driver driver = Driver.builder()
                    .driverId("driver-1")
                    .name("Test Driver")
                    .status(DriverStatus.ONLINE)
                    .busy(false)
                    .vehicleType(VehicleType.STANDARD)
                    .location(new Location(15.0, 25.0))
                    .build();
            driverRepository.save(driver);
            
            acceptedOrder = orderService.acceptOrder(acceptedOrder.getOrderId(), "driver-1");
        }
        
        @Test
        @DisplayName("UT-S01: ACCEPTED → ONGOING 合法轉換")
        void testStartTrip_AcceptedToOngoing_Success() {
            // When
            Order result = orderService.startTrip(acceptedOrder.getOrderId(), "driver-1");
            
            // Then
            assertEquals(OrderStatus.ONGOING, result.getStatus());
            assertNotNull(result.getStartedAt());
        }
        
        @Test
        @DisplayName("UT-S02: H4 冪等性 - 重複開始行程應成功")
        void testStartTrip_Idempotent() {
            // Given
            orderService.startTrip(acceptedOrder.getOrderId(), "driver-1");
            
            // When
            Order result = orderService.startTrip(acceptedOrder.getOrderId(), "driver-1");
            
            // Then
            assertEquals(OrderStatus.ONGOING, result.getStatus());
        }
        
        @Test
        @DisplayName("UT-S03: 非指派司機不可開始行程")
        void testStartTrip_NotAssignedDriver_ShouldFail() {
            // When & Then
            BusinessException ex = assertThrows(BusinessException.class, () -> 
                orderService.startTrip(acceptedOrder.getOrderId(), "driver-other")
            );
            assertEquals("NOT_ASSIGNED_DRIVER", ex.getCode());
            assertEquals(403, ex.getHttpStatus());
        }
        
        @Test
        @DisplayName("UT-S04: PENDING 狀態不可開始行程")
        void testStartTrip_FromPending_ShouldFail() {
            // Given - 建立 PENDING 訂單
            Location pickup = new Location(10.0, 20.0);
            Location dropoff = new Location(30.0, 40.0);
            Order pendingOrder = orderService.createOrder("passenger-2", pickup, dropoff, VehicleType.STANDARD);
            
            // When & Then
            BusinessException ex = assertThrows(BusinessException.class, () -> 
                orderService.startTrip(pendingOrder.getOrderId(), "driver-1")
            );
            assertEquals("INVALID_STATE", ex.getCode());
        }
    }
    
    // =========================================================================
    // completeTrip() 測試
    // =========================================================================
    
    @Nested
    @DisplayName("completeTrip() - 完成行程")
    class CompleteTripTests {
        
        private Order ongoingOrder;
        
        @BeforeEach
        void createTestData() {
            // 建立行程中訂單
            Location pickup = new Location(10.0, 20.0);
            Location dropoff = new Location(30.0, 40.0);
            ongoingOrder = orderService.createOrder("passenger-1", pickup, dropoff, VehicleType.STANDARD);
            
            Driver driver = Driver.builder()
                    .driverId("driver-1")
                    .name("Test Driver")
                    .status(DriverStatus.ONLINE)
                    .busy(false)
                    .vehicleType(VehicleType.STANDARD)
                    .location(new Location(15.0, 25.0))
                    .build();
            driverRepository.save(driver);
            
            ongoingOrder = orderService.acceptOrder(ongoingOrder.getOrderId(), "driver-1");
            ongoingOrder = orderService.startTrip(ongoingOrder.getOrderId(), "driver-1");
        }
        
        @Test
        @DisplayName("UT-CP01: ONGOING → COMPLETED 合法轉換")
        void testCompleteTrip_OngoingToCompleted_Success() {
            // When
            Order result = orderService.completeTrip(ongoingOrder.getOrderId(), "driver-1");
            
            // Then
            assertEquals(OrderStatus.COMPLETED, result.getStatus());
            assertNotNull(result.getCompletedAt());
            assertTrue(result.getActualFare() > 0);
        }
        
        @Test
        @DisplayName("UT-CP02: H4 冪等性 - 重複完成行程應成功")
        void testCompleteTrip_Idempotent() {
            // Given
            orderService.completeTrip(ongoingOrder.getOrderId(), "driver-1");
            
            // When
            Order result = orderService.completeTrip(ongoingOrder.getOrderId(), "driver-1");
            
            // Then
            assertEquals(OrderStatus.COMPLETED, result.getStatus());
        }
        
        @Test
        @DisplayName("UT-CP03: 非指派司機不可完成行程")
        void testCompleteTrip_NotAssignedDriver_ShouldFail() {
            // When & Then
            BusinessException ex = assertThrows(BusinessException.class, () -> 
                orderService.completeTrip(ongoingOrder.getOrderId(), "driver-other")
            );
            assertEquals("NOT_ASSIGNED_DRIVER", ex.getCode());
            assertEquals(403, ex.getHttpStatus());
        }
    }
    
    // =========================================================================
    // cancelOrder() 測試
    // =========================================================================
    
    @Nested
    @DisplayName("cancelOrder() - 取消訂單")
    class CancelOrderTests {
        
        @Test
        @DisplayName("UT-X01: PENDING → CANCELLED 合法轉換（無取消費）")
        void testCancelOrder_FromPending_Success() {
            // Given
            Location pickup = new Location(10.0, 20.0);
            Location dropoff = new Location(30.0, 40.0);
            Order order = orderService.createOrder("passenger-1", pickup, dropoff, VehicleType.STANDARD);
            
            // When
            Order result = orderService.cancelOrder(order.getOrderId(), "passenger-1");
            
            // Then
            assertEquals(OrderStatus.CANCELLED, result.getStatus());
            assertEquals(0.0, result.getCancelFee());
            assertNotNull(result.getCancelledAt());
        }
        
        @Test
        @DisplayName("UT-X02: ACCEPTED → CANCELLED 合法轉換（有取消費）")
        void testCancelOrder_FromAccepted_WithFee() {
            // Given
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
            
            // When
            Order result = orderService.cancelOrder(order.getOrderId(), "passenger-1");
            
            // Then
            assertEquals(OrderStatus.CANCELLED, result.getStatus());
            assertTrue(result.getCancelFee() > 0);
        }
        
        @Test
        @DisplayName("UT-X03: ONGOING 狀態不可取消")
        void testCancelOrder_FromOngoing_ShouldFail() {
            // Given
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
            order = orderService.startTrip(order.getOrderId(), "driver-1");
            String orderId = order.getOrderId();
            
            // When & Then
            BusinessException ex = assertThrows(BusinessException.class, () -> 
                orderService.cancelOrder(orderId, "passenger-1")
            );
            assertEquals("INVALID_STATE", ex.getCode());
        }
        
        @Test
        @DisplayName("UT-X04: H4 冪等性 - 重複取消應成功")
        void testCancelOrder_Idempotent() {
            // Given
            Location pickup = new Location(10.0, 20.0);
            Location dropoff = new Location(30.0, 40.0);
            Order order = orderService.createOrder("passenger-1", pickup, dropoff, VehicleType.STANDARD);
            orderService.cancelOrder(order.getOrderId(), "passenger-1");
            
            // When
            Order result = orderService.cancelOrder(order.getOrderId(), "passenger-1");
            
            // Then
            assertEquals(OrderStatus.CANCELLED, result.getStatus());
        }
    }
    
    // =========================================================================
    // 狀態機非法轉換測試
    // =========================================================================
    
    @Nested
    @DisplayName("狀態機 - 非法轉換拒絕")
    class IllegalStateTransitionTests {
        
        @Test
        @DisplayName("UT-SM01: COMPLETED 狀態不可變更")
        void testCompletedState_IsTerminal() {
            // Given - 建立已完成訂單
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
            order = orderService.startTrip(order.getOrderId(), "driver-1");
            order = orderService.completeTrip(order.getOrderId(), "driver-1");
            String orderId = order.getOrderId();
            
            // When & Then - 嘗試取消已完成訂單
            BusinessException ex = assertThrows(BusinessException.class, () -> 
                orderService.cancelOrder(orderId, "passenger-1")
            );
            assertEquals("INVALID_STATE", ex.getCode());
        }
        
        @Test
        @DisplayName("UT-SM02: CANCELLED 狀態不可變更")
        void testCancelledState_IsTerminal() {
            // Given - 建立已取消訂單
            Location pickup = new Location(10.0, 20.0);
            Location dropoff = new Location(30.0, 40.0);
            Order order = orderService.createOrder("passenger-1", pickup, dropoff, VehicleType.STANDARD);
            order = orderService.cancelOrder(order.getOrderId(), "passenger-1");
            String orderId = order.getOrderId();
            
            Driver driver = Driver.builder()
                    .driverId("driver-1")
                    .name("Test Driver")
                    .status(DriverStatus.ONLINE)
                    .busy(false)
                    .vehicleType(VehicleType.STANDARD)
                    .location(new Location(15.0, 25.0))
                    .build();
            driverRepository.save(driver);
            
            // When & Then - 嘗試接受已取消訂單
            BusinessException ex = assertThrows(BusinessException.class, () -> 
                orderService.acceptOrder(orderId, "driver-1")
            );
            assertEquals("INVALID_STATE", ex.getCode());
        }
    }
}
