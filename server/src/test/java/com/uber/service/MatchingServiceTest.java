package com.uber.service;

import com.uber.model.*;
import com.uber.repository.DriverRepository;
import com.uber.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MatchingService 單元測試
 * 
 * 測試覆蓋:
 * - UT-M01: 僅回傳上線司機
 * - UT-M02: 排除忙碌司機
 * - UT-M03: 車種篩選正確
 * - UT-M04: 距離最近者優先
 * - UT-M05: ID 小者勝出 (tie-break)
 */
@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private MatchingService matchingService;

    private Driver onlineDriver1;
    private Driver onlineDriver2;
    private Driver offlineDriver;
    private Driver busyDriver;
    private Order pendingOrder;

    @BeforeEach
    void setUp() {
        // 建立上線司機 1 (位置: 0,0)
        onlineDriver1 = Driver.builder()
                .driverId("driver-001")
                .name("Driver 1")
                .status(DriverStatus.ONLINE)
                .vehicleType(VehicleType.STANDARD)
                .location(new Location(0, 0))
                .busy(false)
                .lastUpdatedAt(Instant.now())
                .build();

        // 建立上線司機 2 (位置: 5,5)
        onlineDriver2 = Driver.builder()
                .driverId("driver-002")
                .name("Driver 2")
                .status(DriverStatus.ONLINE)
                .vehicleType(VehicleType.STANDARD)
                .location(new Location(5, 5))
                .busy(false)
                .lastUpdatedAt(Instant.now())
                .build();

        // 建立離線司機
        offlineDriver = Driver.builder()
                .driverId("driver-003")
                .name("Driver 3")
                .status(DriverStatus.OFFLINE)
                .vehicleType(VehicleType.STANDARD)
                .location(new Location(1, 1))
                .busy(false)
                .lastUpdatedAt(Instant.now())
                .build();

        // 建立忙碌司機
        busyDriver = Driver.builder()
                .driverId("driver-004")
                .name("Driver 4")
                .status(DriverStatus.ONLINE)
                .vehicleType(VehicleType.STANDARD)
                .location(new Location(1, 1))
                .busy(true)
                .currentOrderId("order-xxx")
                .lastUpdatedAt(Instant.now())
                .build();

        // 建立待處理訂單 (上車點: 2,2)
        pendingOrder = Order.builder()
                .orderId("order-001")
                .passengerId("passenger-001")
                .status(OrderStatus.PENDING)
                .vehicleType(VehicleType.STANDARD)
                .pickupLocation(new Location(2, 2))
                .dropoffLocation(new Location(10, 10))
                .estimatedFare(100.0)
                .distance(11.3)
                .createdAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("findBestDriver 測試")
    class FindBestDriverTests {

        @Test
        @DisplayName("UT-M01: 僅回傳上線司機")
        void testMatch_OnlineDriverOnly() {
            // Given: 1 上線司機, 1 離線司機
            when(driverRepository.findAll()).thenReturn(List.of(onlineDriver1, offlineDriver));

            // When
            Optional<Driver> result = matchingService.findBestDriver(pendingOrder);

            // Then: 回傳上線司機
            assertTrue(result.isPresent());
            assertEquals("driver-001", result.get().getDriverId());
        }

        @Test
        @DisplayName("UT-M02: 排除忙碌司機")
        void testMatch_NonBusyOnly() {
            // Given: 1 上線非忙碌司機, 1 上線忙碌司機
            when(driverRepository.findAll()).thenReturn(List.of(onlineDriver1, busyDriver));

            // When
            Optional<Driver> result = matchingService.findBestDriver(pendingOrder);

            // Then: 回傳非忙碌司機
            assertTrue(result.isPresent());
            assertEquals("driver-001", result.get().getDriverId());
        }

        @Test
        @DisplayName("UT-M03: 車種篩選正確")
        void testMatch_VehicleTypeFilter() {
            // Given: 更改訂單車種為 PREMIUM
            pendingOrder.setVehicleType(VehicleType.PREMIUM);
            
            // 建立 PREMIUM 車種司機
            Driver premiumDriver = Driver.builder()
                    .driverId("driver-premium")
                    .name("Premium Driver")
                    .status(DriverStatus.ONLINE)
                    .vehicleType(VehicleType.PREMIUM)
                    .location(new Location(3, 3))
                    .busy(false)
                    .build();
            
            when(driverRepository.findAll()).thenReturn(List.of(onlineDriver1, premiumDriver));

            // When
            Optional<Driver> result = matchingService.findBestDriver(pendingOrder);

            // Then: 僅回傳 PREMIUM 車種的司機
            assertTrue(result.isPresent());
            assertEquals("driver-premium", result.get().getDriverId());
        }

        @Test
        @DisplayName("UT-M04: 距離最近者優先")
        void testMatch_DistanceSort() {
            // Given: 兩個上線司機，driver-001 (0,0) 比 driver-002 (5,5) 更近於 (2,2)
            when(driverRepository.findAll()).thenReturn(List.of(onlineDriver2, onlineDriver1));

            // When
            Optional<Driver> result = matchingService.findBestDriver(pendingOrder);

            // Then: 回傳距離較近的 driver-001
            assertTrue(result.isPresent());
            assertEquals("driver-001", result.get().getDriverId());
        }

        @Test
        @DisplayName("UT-M05: ID 小者勝出 (tie-break)")
        void testMatch_TieBreakById() {
            // Given: 兩個距離相同的司機 (相同位置 1,1)
            Driver tieDriver1 = Driver.builder()
                    .driverId("driver-aaa")
                    .status(DriverStatus.ONLINE)
                    .vehicleType(VehicleType.STANDARD)
                    .location(new Location(1, 1))
                    .busy(false)
                    .build();
            
            Driver tieDriver2 = Driver.builder()
                    .driverId("driver-bbb")
                    .status(DriverStatus.ONLINE)
                    .vehicleType(VehicleType.STANDARD)
                    .location(new Location(1, 1))
                    .busy(false)
                    .build();
            
            when(driverRepository.findAll()).thenReturn(List.of(tieDriver2, tieDriver1));

            // When
            Optional<Driver> result = matchingService.findBestDriver(pendingOrder);

            // Then: 回傳 ID 較小的 driver-aaa
            assertTrue(result.isPresent());
            assertEquals("driver-aaa", result.get().getDriverId());
        }

        @Test
        @DisplayName("無符合條件司機時返回 empty")
        void testMatch_NoMatchingDriver() {
            // Given: 只有離線司機
            when(driverRepository.findAll()).thenReturn(List.of(offlineDriver));

            // When
            Optional<Driver> result = matchingService.findBestDriver(pendingOrder);

            // Then: 返回 empty
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("訂單為 null 時返回 empty")
        void testMatch_NullOrder() {
            Optional<Driver> result = matchingService.findBestDriver(null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("司機超出搜尋半徑時不會被選中")
        void testMatch_OutOfRadius() {
            // Given: 司機位於遠處 (100, 100)，超出預設搜尋半徑
            Driver farDriver = Driver.builder()
                    .driverId("driver-far")
                    .status(DriverStatus.ONLINE)
                    .vehicleType(VehicleType.STANDARD)
                    .location(new Location(100, 100))
                    .busy(false)
                    .build();
            
            when(driverRepository.findAll()).thenReturn(List.of(farDriver));

            // When
            Optional<Driver> result = matchingService.findBestDriver(pendingOrder);

            // Then: 無符合條件的司機
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("getAvailableOrders 測試")
    class GetAvailableOrdersTests {

        @Test
        @DisplayName("正確取得可接訂單列表")
        void testGetAvailableOrders_Success() {
            // Given
            Order order1 = Order.builder()
                    .orderId("order-001")
                    .status(OrderStatus.PENDING)
                    .vehicleType(VehicleType.STANDARD)
                    .pickupLocation(new Location(1, 1))
                    .build();
            
            Order order2 = Order.builder()
                    .orderId("order-002")
                    .status(OrderStatus.PENDING)
                    .vehicleType(VehicleType.STANDARD)
                    .pickupLocation(new Location(2, 2))
                    .build();
            
            when(orderRepository.findByStatus(OrderStatus.PENDING))
                    .thenReturn(List.of(order1, order2));

            // When
            List<Order> orders = matchingService.getAvailableOrders(onlineDriver1);

            // Then: 按距離排序，order-001 (1,1) 較近
            assertEquals(2, orders.size());
            assertEquals("order-001", orders.get(0).getOrderId());
            assertEquals("order-002", orders.get(1).getOrderId());
        }

        @Test
        @DisplayName("離線司機無法取得訂單")
        void testGetAvailableOrders_OfflineDriver() {
            List<Order> orders = matchingService.getAvailableOrders(offlineDriver);
            assertTrue(orders.isEmpty());
        }

        @Test
        @DisplayName("忙碌司機無法取得訂單")
        void testGetAvailableOrders_BusyDriver() {
            List<Order> orders = matchingService.getAvailableOrders(busyDriver);
            assertTrue(orders.isEmpty());
        }

        @Test
        @DisplayName("只匹配相同車種訂單")
        void testGetAvailableOrders_VehicleTypeFilter() {
            // Given: 一個 STANDARD 訂單, 一個 PREMIUM 訂單
            Order standardOrder = Order.builder()
                    .orderId("order-standard")
                    .status(OrderStatus.PENDING)
                    .vehicleType(VehicleType.STANDARD)
                    .pickupLocation(new Location(1, 1))
                    .build();
            
            Order premiumOrder = Order.builder()
                    .orderId("order-premium")
                    .status(OrderStatus.PENDING)
                    .vehicleType(VehicleType.PREMIUM)
                    .pickupLocation(new Location(1, 1))
                    .build();
            
            when(orderRepository.findByStatus(OrderStatus.PENDING))
                    .thenReturn(List.of(standardOrder, premiumOrder));

            // When: STANDARD 車種司機查詢
            List<Order> orders = matchingService.getAvailableOrders(onlineDriver1);

            // Then: 只有 STANDARD 訂單
            assertEquals(1, orders.size());
            assertEquals("order-standard", orders.get(0).getOrderId());
        }
    }

    @Nested
    @DisplayName("getAvailableDrivers 測試")
    class GetAvailableDriversTests {

        @Test
        @DisplayName("取得所有可用司機")
        void testGetAvailableDrivers_All() {
            when(driverRepository.findAll())
                    .thenReturn(List.of(onlineDriver1, onlineDriver2, offlineDriver, busyDriver));

            List<Driver> drivers = matchingService.getAvailableDrivers(null);

            // 只有 onlineDriver1 和 onlineDriver2 是可用的
            assertEquals(2, drivers.size());
        }

        @Test
        @DisplayName("按車種篩選可用司機")
        void testGetAvailableDrivers_ByVehicleType() {
            Driver premiumDriver = Driver.builder()
                    .driverId("driver-premium")
                    .status(DriverStatus.ONLINE)
                    .vehicleType(VehicleType.PREMIUM)
                    .busy(false)
                    .build();
            
            when(driverRepository.findAll())
                    .thenReturn(List.of(onlineDriver1, premiumDriver));

            List<Driver> drivers = matchingService.getAvailableDrivers(VehicleType.PREMIUM);

            assertEquals(1, drivers.size());
            assertEquals("driver-premium", drivers.get(0).getDriverId());
        }
    }

    @Nested
    @DisplayName("搜尋半徑設定測試")
    class SearchRadiusTests {

        @Test
        @DisplayName("成功設定搜尋半徑")
        void testSetSearchRadius_Success() {
            matchingService.setSearchRadius(20.0);
            assertEquals(20.0, matchingService.getSearchRadius());
        }

        @Test
        @DisplayName("設定無效搜尋半徑時拋出例外")
        void testSetSearchRadius_Invalid() {
            assertThrows(IllegalArgumentException.class, () -> {
                matchingService.setSearchRadius(0);
            });
            assertThrows(IllegalArgumentException.class, () -> {
                matchingService.setSearchRadius(-5);
            });
        }
    }

    @Nested
    @DisplayName("距離計算測試")
    class DistanceCalculationTests {

        @Test
        @DisplayName("UT-D01: 正確計算歐幾里得距離")
        void testDistance_GeneralCase() {
            double distance = matchingService.calculateDistance(pendingOrder, onlineDriver1);
            // 點 (0,0) 到 (2,2) 的距離 = sqrt(8) ≈ 2.83
            assertEquals(Math.sqrt(8), distance, 0.01);
        }

        @Test
        @DisplayName("UT-D02: 相同位置返回 0")
        void testDistance_SamePoint() {
            onlineDriver1.setLocation(new Location(2, 2));
            double distance = matchingService.calculateDistance(pendingOrder, onlineDriver1);
            assertEquals(0.0, distance, 0.001);
        }

        @Test
        @DisplayName("UT-D03: 距離對稱性 d(A,B) == d(B,A)")
        void testDistance_Symmetry() {
            Order orderA = Order.builder()
                    .pickupLocation(new Location(0, 0))
                    .build();
            Order orderB = Order.builder()
                    .pickupLocation(new Location(3, 4))
                    .build();
            
            Driver driverA = Driver.builder()
                    .location(new Location(0, 0))
                    .build();
            Driver driverB = Driver.builder()
                    .location(new Location(3, 4))
                    .build();

            double distAB = matchingService.calculateDistance(orderB, driverA);
            double distBA = matchingService.calculateDistance(orderA, driverB);
            
            assertEquals(distAB, distBA, 0.001);
        }

        @Test
        @DisplayName("null 輸入返回 MAX_VALUE")
        void testDistance_NullInputs() {
            assertEquals(Double.MAX_VALUE, matchingService.calculateDistance(null, onlineDriver1));
            assertEquals(Double.MAX_VALUE, matchingService.calculateDistance(pendingOrder, null));
        }
    }
}
