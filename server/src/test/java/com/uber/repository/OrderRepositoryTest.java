package com.uber.repository;

import com.uber.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OrderRepository 測試 - 提升分支覆蓋率
 */
@DisplayName("OrderRepository 測試")
class OrderRepositoryTest {

    private OrderRepository repository;

    @BeforeEach
    void setUp() {
        repository = new OrderRepository();
    }

    @Test
    @DisplayName("儲存並查詢訂單")
    void testSaveAndFind() {
        Order order = Order.builder()
                .orderId("o1")
                .passengerId("p1")
                .status(OrderStatus.PENDING)
                .vehicleType(VehicleType.STANDARD)
                .pickupLocation(new Location(25, 121))
                .dropoffLocation(new Location(26, 122))
                .createdAt(Instant.now())
                .build();

        repository.save(order);

        Optional<Order> found = repository.findById("o1");
        assertTrue(found.isPresent());
        assertEquals("p1", found.get().getPassengerId());
    }

    @Test
    @DisplayName("查詢不存在的訂單")
    void testFindNotExist() {
        Optional<Order> found = repository.findById("non-existent");
        assertFalse(found.isPresent());
    }

    @Test
    @DisplayName("更新訂單")
    void testUpdate() {
        Order order = Order.builder()
                .orderId("o1")
                .passengerId("p1")
                .status(OrderStatus.PENDING)
                .vehicleType(VehicleType.STANDARD)
                .pickupLocation(new Location(25, 121))
                .dropoffLocation(new Location(26, 122))
                .createdAt(Instant.now())
                .build();

        repository.save(order);

        order.setStatus(OrderStatus.ACCEPTED);
        order.setDriverId("d1");
        order.setAcceptedAt(Instant.now());
        repository.save(order);

        Optional<Order> found = repository.findById("o1");
        assertTrue(found.isPresent());
        assertEquals(OrderStatus.ACCEPTED, found.get().getStatus());
        assertEquals("d1", found.get().getDriverId());
    }

    @Test
    @DisplayName("查詢所有訂單")
    void testFindAll() {
        Order o1 = Order.builder()
                .orderId("o1")
                .passengerId("p1")
                .status(OrderStatus.PENDING)
                .vehicleType(VehicleType.STANDARD)
                .pickupLocation(new Location(25, 121))
                .dropoffLocation(new Location(26, 122))
                .createdAt(Instant.now())
                .build();

        Order o2 = Order.builder()
                .orderId("o2")
                .passengerId("p2")
                .status(OrderStatus.ACCEPTED)
                .vehicleType(VehicleType.PREMIUM)
                .pickupLocation(new Location(27, 123))
                .dropoffLocation(new Location(28, 124))
                .createdAt(Instant.now())
                .build();

        repository.save(o1);
        repository.save(o2);

        List<Order> all = repository.findAll();
        assertEquals(2, all.size());
    }

    @Test
    @DisplayName("空的 repository 應返回空列表")
    void testFindAllEmpty() {
        List<Order> all = repository.findAll();
        assertTrue(all.isEmpty());
    }

    @Test
    @DisplayName("根據狀態查詢訂單")
    void testFindByStatus() {
        Order o1 = Order.builder()
                .orderId("o1")
                .passengerId("p1")
                .status(OrderStatus.PENDING)
                .vehicleType(VehicleType.STANDARD)
                .pickupLocation(new Location(25, 121))
                .dropoffLocation(new Location(26, 122))
                .createdAt(Instant.now())
                .build();

        Order o2 = Order.builder()
                .orderId("o2")
                .passengerId("p2")
                .status(OrderStatus.PENDING)
                .vehicleType(VehicleType.PREMIUM)
                .pickupLocation(new Location(27, 123))
                .dropoffLocation(new Location(28, 124))
                .createdAt(Instant.now())
                .build();

        Order o3 = Order.builder()
                .orderId("o3")
                .passengerId("p3")
                .status(OrderStatus.ACCEPTED)
                .vehicleType(VehicleType.STANDARD)
                .pickupLocation(new Location(29, 125))
                .dropoffLocation(new Location(30, 126))
                .createdAt(Instant.now())
                .build();

        repository.save(o1);
        repository.save(o2);
        repository.save(o3);

        List<Order> pending = repository.findByStatus(OrderStatus.PENDING);
        assertEquals(2, pending.size());
        assertTrue(pending.stream().allMatch(o -> o.getStatus() == OrderStatus.PENDING));
    }

    @Test
    @DisplayName("根據乘客ID查詢訂單")
    void testFindByPassengerId() {
        Order o1 = Order.builder()
                .orderId("o1")
                .passengerId("p1")
                .status(OrderStatus.PENDING)
                .vehicleType(VehicleType.STANDARD)
                .pickupLocation(new Location(25, 121))
                .dropoffLocation(new Location(26, 122))
                .createdAt(Instant.now())
                .build();

        Order o2 = Order.builder()
                .orderId("o2")
                .passengerId("p1")
                .status(OrderStatus.ACCEPTED)
                .vehicleType(VehicleType.PREMIUM)
                .pickupLocation(new Location(27, 123))
                .dropoffLocation(new Location(28, 124))
                .createdAt(Instant.now())
                .build();

        Order o3 = Order.builder()
                .orderId("o3")
                .passengerId("p2")
                .status(OrderStatus.PENDING)
                .vehicleType(VehicleType.STANDARD)
                .pickupLocation(new Location(29, 125))
                .dropoffLocation(new Location(30, 126))
                .createdAt(Instant.now())
                .build();

        repository.save(o1);
        repository.save(o2);
        repository.save(o3);

        List<Order> p1Orders = repository.findByPassengerId("p1");
        assertEquals(2, p1Orders.size());
        assertTrue(p1Orders.stream().allMatch(o -> o.getPassengerId().equals("p1")));
    }

    @Test
    @DisplayName("根據司機ID查詢訂單")
    void testFindByDriverId() {
        Order o1 = Order.builder()
                .orderId("o1")
                .passengerId("p1")
                .driverId("d1")
                .status(OrderStatus.ACCEPTED)
                .vehicleType(VehicleType.STANDARD)
                .pickupLocation(new Location(25, 121))
                .dropoffLocation(new Location(26, 122))
                .createdAt(Instant.now())
                .build();

        Order o2 = Order.builder()
                .orderId("o2")
                .passengerId("p2")
                .driverId("d1")
                .status(OrderStatus.ONGOING)
                .vehicleType(VehicleType.PREMIUM)
                .pickupLocation(new Location(27, 123))
                .dropoffLocation(new Location(28, 124))
                .createdAt(Instant.now())
                .build();

        Order o3 = Order.builder()
                .orderId("o3")
                .passengerId("p3")
                .driverId("d2")
                .status(OrderStatus.COMPLETED)
                .vehicleType(VehicleType.STANDARD)
                .pickupLocation(new Location(29, 125))
                .dropoffLocation(new Location(30, 126))
                .createdAt(Instant.now())
                .build();

        repository.save(o1);
        repository.save(o2);
        repository.save(o3);

        List<Order> d1Orders = repository.findByDriverId("d1");
        assertEquals(2, d1Orders.size());
        assertTrue(d1Orders.stream().allMatch(o -> o.getDriverId().equals("d1")));
    }

    @Test
    @DisplayName("查詢不存在的乘客訂單")
    void testFindByPassengerIdNotExist() {
        List<Order> orders = repository.findByPassengerId("non-existent");
        assertTrue(orders.isEmpty());
    }

    @Test
    @DisplayName("查詢不存在的司機訂單")
    void testFindByDriverIdNotExist() {
        List<Order> orders = repository.findByDriverId("non-existent");
        assertTrue(orders.isEmpty());
    }

    @Test
    @DisplayName("刪除所有訂單")
    void testDeleteAll() {
        Order order = Order.builder()
                .orderId("o1")
                .passengerId("p1")
                .status(OrderStatus.PENDING)
                .vehicleType(VehicleType.STANDARD)
                .pickupLocation(new Location(25, 121))
                .dropoffLocation(new Location(26, 122))
                .createdAt(Instant.now())
                .build();

        repository.save(order);
        assertEquals(1, repository.findAll().size());

        repository.deleteAll();
        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    @DisplayName("處理 null 值")
    void testNullHandling() {
        // Test findByStatus with null
        List<Order> nullStatus = repository.findByStatus(null);
        assertTrue(nullStatus.isEmpty());

        // Test findByPassengerId with null
        List<Order> nullPassenger = repository.findByPassengerId(null);
        assertTrue(nullPassenger.isEmpty());

        // Test findByDriverId with null
        List<Order> nullDriver = repository.findByDriverId(null);
        assertTrue(nullDriver.isEmpty());
    }
}

