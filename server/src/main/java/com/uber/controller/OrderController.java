package com.uber.controller;

import com.uber.dto.AcceptOrderRequest;
import com.uber.dto.ApiResponse;
import com.uber.dto.CancelOrderRequest;
import com.uber.dto.CreateOrderRequest;
import com.uber.model.Order;
import com.uber.model.OrderStatus;
import com.uber.service.FareService;
import com.uber.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 訂單 API Controller
 * 
 * 實作 Issue #15: 完成 OrderController REST API
 * 
 * 端點:
 * - POST   /api/orders              : 建立叫車請求
 * - GET    /api/orders/{orderId}    : 查詢訂單狀態
 * - PUT    /api/orders/{orderId}/accept   : 接受訂單
 * - PUT    /api/orders/{orderId}/start    : 開始行程
 * - PUT    /api/orders/{orderId}/complete : 完成行程
 * - PUT    /api/orders/{orderId}/cancel   : 取消訂單
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    
    private final OrderService orderService;
    private final FareService fareService;
    
    /**
     * 建立叫車請求
     * POST /api/orders
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(
                request.getPassengerId(),
                request.getPickupLocation(),
                request.getDropoffLocation(),
                request.getVehicleType()
        );
        
        Map<String, Object> response = buildOrderResponse(order);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }
    
    /**
     * 查詢訂單狀態
     * GET /api/orders/{orderId}
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOrder(@PathVariable String orderId) {
        Order order = orderService.getOrder(orderId);
        Map<String, Object> response = buildOrderResponse(order);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    /**
     * 接受訂單
     * PUT /api/orders/{orderId}/accept
     * 
     * ⚠️ 重點 API - H2 搶單併發控制
     */
    @PutMapping("/{orderId}/accept")
    public ResponseEntity<ApiResponse<Map<String, Object>>> acceptOrder(
            @PathVariable String orderId,
            @Valid @RequestBody AcceptOrderRequest request) {
        Order order = orderService.acceptOrder(orderId, request.getDriverId());
        
        Map<String, Object> response = new HashMap<>();
        response.put("orderId", order.getOrderId());
        response.put("status", order.getStatus().name());
        response.put("driverId", order.getDriverId());
        response.put("acceptedAt", order.getAcceptedAt());
        response.put("pickupLocation", order.getPickupLocation());
        response.put("dropoffLocation", order.getDropoffLocation());
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    /**
     * 開始行程
     * PUT /api/orders/{orderId}/start
     */
    @PutMapping("/{orderId}/start")
    public ResponseEntity<ApiResponse<Map<String, Object>>> startTrip(
            @PathVariable String orderId,
            @Valid @RequestBody AcceptOrderRequest request) {
        Order order = orderService.startTrip(orderId, request.getDriverId());
        
        Map<String, Object> response = new HashMap<>();
        response.put("orderId", order.getOrderId());
        response.put("status", order.getStatus().name());
        response.put("startedAt", order.getStartedAt());
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    /**
     * 完成行程
     * PUT /api/orders/{orderId}/complete
     */
    @PutMapping("/{orderId}/complete")
    public ResponseEntity<ApiResponse<Map<String, Object>>> completeTrip(
            @PathVariable String orderId,
            @Valid @RequestBody AcceptOrderRequest request) {
        Order order = orderService.completeTrip(orderId, request.getDriverId());
        
        // 計算費用明細
        var ratePlan = fareService.getRatePlan(order.getVehicleType());
        double baseFare = ratePlan.getBaseFare();
        double distanceFare = order.getDistance() * ratePlan.getPerKmRate();
        double timeFare = order.getDuration() * ratePlan.getPerMinRate();
        
        Map<String, Object> fareBreakdown = new HashMap<>();
        fareBreakdown.put("baseFare", baseFare);
        fareBreakdown.put("distanceFare", Math.round(distanceFare * 100.0) / 100.0);
        fareBreakdown.put("timeFare", Math.round(timeFare * 100.0) / 100.0);
        fareBreakdown.put("total", order.getActualFare());
        
        Map<String, Object> response = new HashMap<>();
        response.put("orderId", order.getOrderId());
        response.put("status", order.getStatus().name());
        response.put("completedAt", order.getCompletedAt());
        response.put("fare", order.getActualFare());
        response.put("distance", order.getDistance());
        response.put("duration", order.getDuration());
        response.put("fareBreakdown", fareBreakdown);
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    /**
     * 取消訂單
     * PUT /api/orders/{orderId}/cancel
     */
    @PutMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cancelOrder(
            @PathVariable String orderId,
            @RequestBody CancelOrderRequest request) {
        Order order = orderService.cancelOrder(orderId, request.getCancelledBy());
        
        Map<String, Object> response = new HashMap<>();
        response.put("orderId", order.getOrderId());
        response.put("status", order.getStatus().name());
        response.put("cancelledAt", order.getCancelledAt());
        response.put("cancelledBy", order.getCancelledBy());
        response.put("cancelFee", order.getCancelFee());
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    /**
     * 建構訂單回應資料
     */
    private Map<String, Object> buildOrderResponse(Order order) {
        Map<String, Object> response = new HashMap<>();
        response.put("orderId", order.getOrderId());
        response.put("passengerId", order.getPassengerId());
        response.put("status", order.getStatus().name());
        response.put("vehicleType", order.getVehicleType().name());
        response.put("pickupLocation", order.getPickupLocation());
        response.put("dropoffLocation", order.getDropoffLocation());
        response.put("estimatedFare", order.getEstimatedFare());
        response.put("estimatedDistance", order.getDistance());
        response.put("createdAt", order.getCreatedAt());
        
        // 條件性欄位
        if (order.getDriverId() != null) {
            response.put("driverId", order.getDriverId());
        }
        if (order.getAcceptedAt() != null) {
            response.put("acceptedAt", order.getAcceptedAt());
        }
        if (order.getStartedAt() != null) {
            response.put("startedAt", order.getStartedAt());
        }
        if (order.getCompletedAt() != null) {
            response.put("completedAt", order.getCompletedAt());
            response.put("fare", order.getActualFare());
            response.put("duration", order.getDuration());
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            response.put("cancelledAt", order.getCancelledAt());
            response.put("cancelledBy", order.getCancelledBy());
            response.put("cancelFee", order.getCancelFee());
        }
        
        return response;
    }
}
