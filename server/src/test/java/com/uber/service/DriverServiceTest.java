package com.uber.service;

import com.uber.exception.BusinessException;
import com.uber.model.*;
import com.uber.repository.DriverRepository;
import com.uber.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DriverService 單元測試
 * 
 * 驗證 Issue #4 的所有驗收標準：
 * - goOnline() - 司機上線
 * - goOffline() - 司機下線
 * - updateLocation() - 更新司機位置
 * - getOffers() - 取得可接訂單列表
 * - 司機狀態管理 (ONLINE/OFFLINE/BUSY)
 */
class DriverServiceTest {
    
    private DriverService driverService;
    private DriverRepository driverRepository;
    private OrderRepository orderRepository;
    
    @BeforeEach
    void setUp() {
        driverRepository = new DriverRepository();
        orderRepository = new OrderRepository();
        driverService = new DriverService(driverRepository, orderRepository);
    }
    
    // =========================================================================
    // goOnline() 測試
    // =========================================================================
    
    @Nested
    @DisplayName("goOnline() - 司機上線")
    class GoOnlineTests {
        
        @Test
        @DisplayName("UT-D01: 新司機上線成功")
        void testGoOnline_NewDriver_Success() {
            // Given
            Location location = new Location(10.0, 20.0);
            
            // When
            Driver result = driverService.goOnline("driver-1", location);
            
            // Then
            assertNotNull(result);
            assertEquals("driver-1", result.getDriverId());
            assertEquals(DriverStatus.ONLINE, result.getStatus());
            assertEquals(10.0, result.getLocation().getX());
            assertEquals(20.0, result.getLocation().getY());
        }
        
        @Test
        @DisplayName("UT-D02: 已存在司機上線成功")
        void testGoOnline_ExistingDriver_Success() {
            // Given - 先註冊司機
            driverService.registerDriver("driver-1", "John", "0912345678", 
                    "ABC-1234", VehicleType.PREMIUM);
            Location location = new Location(30.0, 40.0);
            
            // When
            Driver result = driverService.goOnline("driver-1", location);
            
            // Then
            assertEquals(DriverStatus.ONLINE, result.getStatus());
            assertEquals(VehicleType.PREMIUM, result.getVehicleType());
            assertEquals(30.0, result.getLocation().getX());
        }
    }
    
    // =========================================================================
    // goOffline() 測試
    // =========================================================================
    
    @Nested
    @DisplayName("goOffline() - 司機下線")
    class GoOfflineTests {
        
        @Test
        @DisplayName("UT-D03: 閒置司機下線成功")
        void testGoOffline_NotBusy_Success() {
            // Given
            driverService.goOnline("driver-1", new Location(10.0, 20.0));
            
            // When
            Driver result = driverService.goOffline("driver-1");
            
            // Then
            assertEquals(DriverStatus.OFFLINE, result.getStatus());
        }
        
        @Test
        @DisplayName("UT-D04: 忙碌司機無法下線")
        void testGoOffline_Busy_ShouldFail() {
            // Given
            driverService.goOnline("driver-1", new Location(10.0, 20.0));
            Driver driver = driverRepository.findById("driver-1").get();
            driver.setBusy(true);
            driverRepository.save(driver);
            
            // When & Then
            BusinessException ex = assertThrows(BusinessException.class, () -> 
                driverService.goOffline("driver-1")
            );
            assertEquals("DRIVER_BUSY", ex.getCode());
        }
        
        @Test
        @DisplayName("UT-D05: 不存在司機下線失敗")
        void testGoOffline_NotFound_ShouldFail() {
            // When & Then
            BusinessException ex = assertThrows(BusinessException.class, () -> 
                driverService.goOffline("non-existent")
            );
            assertEquals("DRIVER_NOT_FOUND", ex.getCode());
        }
    }
    
    // =========================================================================
    // updateLocation() 測試
    // =========================================================================
    
    @Nested
    @DisplayName("updateLocation() - 更新司機位置")
    class UpdateLocationTests {
        
        @Test
        @DisplayName("UT-D06: 更新位置成功")
        void testUpdateLocation_Success() {
            // Given
            driverService.goOnline("driver-1", new Location(10.0, 20.0));
            Location newLocation = new Location(50.0, 60.0);
            
            // When
            Driver result = driverService.updateLocation("driver-1", newLocation);
            
            // Then
            assertEquals(50.0, result.getLocation().getX());
            assertEquals(60.0, result.getLocation().getY());
        }
        
        @Test
        @DisplayName("UT-D07: 不存在司機更新位置失敗")
        void testUpdateLocation_NotFound_ShouldFail() {
            // When & Then
            BusinessException ex = assertThrows(BusinessException.class, () -> 
                driverService.updateLocation("non-existent", new Location(10.0, 20.0))
            );
            assertEquals("DRIVER_NOT_FOUND", ex.getCode());
        }
    }
    
    // =========================================================================
    // getOffers() 測試
    // =========================================================================
    
    @Nested
    @DisplayName("getOffers() - 取得可接訂單")
    class GetOffersTests {
        
        @BeforeEach
        void createTestOrders() {
            // 建立測試訂單
            Order order1 = Order.builder()
                    .orderId("order-1")
                    .passengerId("passenger-1")
                    .status(OrderStatus.PENDING)
                    .vehicleType(VehicleType.STANDARD)
                    .pickupLocation(new Location(15.0, 25.0))
                    .dropoffLocation(new Location(50.0, 50.0))
                    .build();
            
            Order order2 = Order.builder()
                    .orderId("order-2")
                    .passengerId("passenger-2")
                    .status(OrderStatus.PENDING)
                    .vehicleType(VehicleType.STANDARD)
                    .pickupLocation(new Location(12.0, 22.0))
                    .dropoffLocation(new Location(60.0, 60.0))
                    .build();
            
            Order order3 = Order.builder()
                    .orderId("order-3")
                    .passengerId("passenger-3")
                    .status(OrderStatus.PENDING)
                    .vehicleType(VehicleType.PREMIUM)
                    .pickupLocation(new Location(11.0, 21.0))
                    .dropoffLocation(new Location(70.0, 70.0))
                    .build();
            
            orderRepository.save(order1);
            orderRepository.save(order2);
            orderRepository.save(order3);
        }
        
        @Test
        @DisplayName("UT-D08: 取得訂單成功 - 篩選車種")
        void testGetOffers_FilterByVehicleType() {
            // Given
            driverService.goOnline("driver-1", new Location(10.0, 20.0));
            
            // When
            List<Order> offers = driverService.getOffers("driver-1");
            
            // Then - 只有 STANDARD 訂單
            assertEquals(2, offers.size());
            assertTrue(offers.stream().allMatch(o -> o.getVehicleType() == VehicleType.STANDARD));
        }
        
        @Test
        @DisplayName("UT-D09: 取得訂單成功 - 距離排序")
        void testGetOffers_SortedByDistance() {
            // Given
            driverService.goOnline("driver-1", new Location(10.0, 20.0));
            
            // When
            List<Order> offers = driverService.getOffers("driver-1");
            
            // Then - order-2 距離較近 (12,22 vs 15,25)
            assertEquals("order-2", offers.get(0).getOrderId());
            assertEquals("order-1", offers.get(1).getOrderId());
        }
        
        @Test
        @DisplayName("UT-D10: 離線司機無法取得訂單")
        void testGetOffers_OfflineDriver_ShouldFail() {
            // Given
            driverService.registerDriver("driver-1", "John", "0912345678", 
                    "ABC-1234", VehicleType.STANDARD);
            
            // When & Then
            BusinessException ex = assertThrows(BusinessException.class, () -> 
                driverService.getOffers("driver-1")
            );
            assertEquals("DRIVER_OFFLINE", ex.getCode());
        }
        
        @Test
        @DisplayName("UT-D11: 忙碌司機回傳空列表")
        void testGetOffers_BusyDriver_ReturnsEmpty() {
            // Given
            driverService.goOnline("driver-1", new Location(10.0, 20.0));
            Driver driver = driverRepository.findById("driver-1").get();
            driver.setBusy(true);
            driverRepository.save(driver);
            
            // When
            List<Order> offers = driverService.getOffers("driver-1");
            
            // Then
            assertTrue(offers.isEmpty());
        }
    }
    
    // =========================================================================
    // registerDriver() 測試
    // =========================================================================
    
    @Nested
    @DisplayName("registerDriver() - 註冊司機")
    class RegisterDriverTests {
        
        @Test
        @DisplayName("UT-D12: 註冊司機成功")
        void testRegisterDriver_Success() {
            // When
            Driver result = driverService.registerDriver("driver-1", "John Doe", 
                    "0912345678", "ABC-1234", VehicleType.PREMIUM);
            
            // Then
            assertNotNull(result);
            assertEquals("driver-1", result.getDriverId());
            assertEquals("John Doe", result.getName());
            assertEquals("0912345678", result.getPhone());
            assertEquals("ABC-1234", result.getVehiclePlate());
            assertEquals(VehicleType.PREMIUM, result.getVehicleType());
            assertEquals(DriverStatus.OFFLINE, result.getStatus());
            assertFalse(result.isBusy());
        }
    }
}
