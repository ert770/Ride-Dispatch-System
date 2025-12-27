package com.uber.repository;

import com.uber.model.Order;
import com.uber.model.OrderStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 訂單儲存庫 (In-Memory)
 */
@Repository
public class OrderRepository {
    
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    
    public Order save(Order order) {
        orders.put(order.getOrderId(), order);
        return order;
    }
    
    public Optional<Order> findById(String orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }
    
    public List<Order> findAll() {
        return List.copyOf(orders.values());
    }
    
    public List<Order> findByStatus(OrderStatus status) {
        return orders.values().stream()
                .filter(o -> o.getStatus() == status)
                .collect(Collectors.toList());
    }
    
    public List<Order> findByPassengerId(String passengerId) {
        return orders.values().stream()
                .filter(o -> o.getPassengerId().equals(passengerId))
                .collect(Collectors.toList());
    }
    
    public List<Order> findByDriverId(String driverId) {
        return orders.values().stream()
                .filter(o -> driverId.equals(o.getDriverId()))
                .collect(Collectors.toList());
    }
    
    public void deleteAll() {
        orders.clear();
    }
    
    public int count() {
        return orders.size();
    }
}
