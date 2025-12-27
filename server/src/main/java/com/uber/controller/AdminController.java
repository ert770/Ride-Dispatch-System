package com.uber.controller;

import com.uber.dto.ApiResponse;
import com.uber.model.*;
import com.uber.service.AuditService;
import com.uber.service.DriverService;
import com.uber.service.FareService;
import com.uber.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理員 API Controller
 * 
 * 實作 Issue #17: 完成 AdminController REST API
 * 
 * 端點:
 * - GET /api/admin/orders          : 取得所有訂單 (支援分頁和狀態篩選)
 * - GET /api/admin/orders/{orderId}: 取得單一訂單詳情
 * - GET /api/admin/drivers         : 取得所有司機
 * - GET /api/admin/audit-logs      : 取得 Audit Log (支援篩選)
 * - GET /api/admin/accept-stats/{orderId}: 取得搶單統計 (H2 驗證用)
 * - GET /api/admin/rate-plans      : 取得費率設定
 * - PUT /api/admin/rate-plans/{vehicleType}: 更新費率設定
 * - GET /api/admin/stats           : 系統統計數據
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    
    private final OrderService orderService;
    private final DriverService driverService;
    private final AuditService auditService;
    private final FareService fareService;
    
    /**
     * 取得所有訂單 (支援分頁和狀態篩選)
     * GET /api/admin/orders
     */
    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAllOrders(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        List<Order> orders = orderService.getAllOrders();
        
        // 狀態篩選
        if (status != null && !status.isEmpty()) {
            try {
                OrderStatus orderStatus = OrderStatus.valueOf(status.toUpperCase());
                orders = orders.stream()
                        .filter(o -> o.getStatus() == orderStatus)
                        .toList();
            } catch (IllegalArgumentException e) {
                // 無效的狀態參數，忽略篩選
            }
        }
        
        int totalElements = orders.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        
        // 分頁
        int start = page * size;
        int end = Math.min(start + size, orders.size());
        List<Order> pagedOrders = start < orders.size() 
                ? orders.subList(start, end) 
                : List.of();
        
        // 轉換為 API 回應格式
        List<Map<String, Object>> orderList = pagedOrders.stream()
                .map(this::buildOrderSummary)
                .collect(Collectors.toList());
        
        Map<String, Object> pagination = new HashMap<>();
        pagination.put("page", page);
        pagination.put("size", size);
        pagination.put("totalElements", totalElements);
        pagination.put("totalPages", totalPages);
        
        Map<String, Object> response = new HashMap<>();
        response.put("orders", orderList);
        response.put("pagination", pagination);
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    /**
     * 取得單一訂單詳情
     * GET /api/admin/orders/{orderId}
     */
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOrderDetail(
            @PathVariable String orderId) {
        Order order = orderService.getOrder(orderId);
        return ResponseEntity.ok(ApiResponse.success(buildOrderDetail(order)));
    }
    
    /**
     * 取得所有司機
     * GET /api/admin/drivers
     */
    @GetMapping("/drivers")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAllDrivers(
            @RequestParam(required = false) String status) {
        List<Driver> drivers = driverService.getAllDrivers();
        
        // 狀態篩選
        if (status != null && !status.isEmpty()) {
            try {
                DriverStatus driverStatus = DriverStatus.valueOf(status.toUpperCase());
                drivers = drivers.stream()
                        .filter(d -> d.getStatus() == driverStatus)
                        .toList();
            } catch (IllegalArgumentException e) {
                // 無效的狀態參數，忽略篩選
            }
        }
        
        List<Map<String, Object>> driverList = drivers.stream()
                .map(this::buildDriverSummary)
                .collect(Collectors.toList());
        
        Map<String, Object> response = new HashMap<>();
        response.put("drivers", driverList);
        response.put("count", driverList.size());
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    /**
     * 取得 Audit Log (支援 orderId 和 action 篩選)
     * GET /api/admin/audit-logs
     */
    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAuditLogs(
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) String action) {
        
        List<AuditLog> logs;
        if (orderId != null && !orderId.isEmpty()) {
            logs = auditService.getLogsByOrderId(orderId);
        } else {
            logs = auditService.getAllLogs();
        }
        
        // Action 篩選
        if (action != null && !action.isEmpty()) {
            logs = logs.stream()
                    .filter(log -> log.getAction().equalsIgnoreCase(action))
                    .collect(Collectors.toList());
        }
        
        List<Map<String, Object>> logList = logs.stream()
                .map(this::buildAuditLogResponse)
                .collect(Collectors.toList());
        
        Map<String, Object> response = new HashMap<>();
        response.put("logs", logList);
        response.put("count", logList.size());
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    /**
     * 取得搶單統計 (H2 驗證用)
     * GET /api/admin/accept-stats/{orderId}
     */
    @GetMapping("/accept-stats/{orderId}")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getAcceptStats(@PathVariable String orderId) {
        Map<String, Long> stats = auditService.getAcceptStats(orderId);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }
    
    /**
     * 取得所有費率
     * GET /api/admin/rate-plans
     */
    @GetMapping("/rate-plans")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRatePlans() {
        List<RatePlan> ratePlans = fareService.getAllRatePlans();
        
        List<Map<String, Object>> ratePlanList = ratePlans.stream()
                .map(this::buildRatePlanResponse)
                .collect(Collectors.toList());
        
        Map<String, Object> response = new HashMap<>();
        response.put("ratePlans", ratePlanList);
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    /**
     * 更新費率
     * PUT /api/admin/rate-plans/{vehicleType}
     */
    @PutMapping("/rate-plans/{vehicleType}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateRatePlan(
            @PathVariable VehicleType vehicleType,
            @RequestBody RatePlan ratePlan) {
        RatePlan updated = fareService.updateRatePlan(vehicleType, ratePlan);
        
        Map<String, Object> response = buildRatePlanResponse(updated);
        response.put("updatedAt", Instant.now());
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    /**
     * 系統統計數據
     * GET /api/admin/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSystemStats() {
        List<Order> allOrders = orderService.getAllOrders();
        List<Driver> allDrivers = driverService.getAllDrivers();
        
        // 訂單統計
        long pendingCount = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.PENDING).count();
        long acceptedCount = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.ACCEPTED).count();
        long ongoingCount = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.ONGOING).count();
        long completedCount = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.COMPLETED).count();
        long cancelledCount = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).count();
        
        // 收入統計
        double totalRevenue = allOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.COMPLETED)
                .mapToDouble(Order::getActualFare)
                .sum();
        
        // 司機統計
        long onlineDrivers = allDrivers.stream().filter(d -> d.getStatus() == DriverStatus.ONLINE).count();
        long busyDrivers = allDrivers.stream().filter(Driver::isBusy).count();

        Map<String, Object> orderStats = new HashMap<>();
        orderStats.put("total", allOrders.size());
        orderStats.put("pending", pendingCount);
        orderStats.put("accepted", acceptedCount);
        orderStats.put("ongoing", ongoingCount);
        orderStats.put("completed", completedCount);
        orderStats.put("cancelled", cancelledCount);
        
        Map<String, Object> driverStats = new HashMap<>();
        driverStats.put("total", allDrivers.size());
        driverStats.put("online", onlineDrivers);
        driverStats.put("busy", busyDrivers);
        
        Map<String, Object> response = new HashMap<>();
        response.put("orders", orderStats);
        response.put("drivers", driverStats);
        response.put("totalRevenue", Math.round(totalRevenue * 100.0) / 100.0);
        response.put("generatedAt", Instant.now());
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    // ========== 私有方法 ==========
    
    private Map<String, Object> buildOrderSummary(Order order) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("orderId", order.getOrderId());
        summary.put("passengerId", order.getPassengerId());
        summary.put("status", order.getStatus().name());
        summary.put("vehicleType", order.getVehicleType().name());
        summary.put("createdAt", order.getCreatedAt());
        
        if (order.getDriverId() != null) {
            summary.put("driverId", order.getDriverId());
        }
        if (order.getActualFare() != null && order.getActualFare() > 0) {
            summary.put("fare", order.getActualFare());
        }
        if (order.getCompletedAt() != null) {
            summary.put("completedAt", order.getCompletedAt());
        }
        if (order.getCancelledAt() != null) {
            summary.put("cancelledAt", order.getCancelledAt());
        }
        
        return summary;
    }
    
    private Map<String, Object> buildOrderDetail(Order order) {
        Map<String, Object> detail = buildOrderSummary(order);
        detail.put("pickupLocation", order.getPickupLocation());
        detail.put("dropoffLocation", order.getDropoffLocation());
        detail.put("estimatedFare", order.getEstimatedFare());
        detail.put("distance", order.getDistance());
        
        if (order.getAcceptedAt() != null) {
            detail.put("acceptedAt", order.getAcceptedAt());
        }
        if (order.getStartedAt() != null) {
            detail.put("startedAt", order.getStartedAt());
        }
        if (order.getDuration() != null && order.getDuration() > 0) {
            detail.put("duration", order.getDuration());
        }
        if (order.getCancelFee() != null && order.getCancelFee() > 0) {
            detail.put("cancelFee", order.getCancelFee());
        }
        if (order.getCancelledBy() != null) {
            detail.put("cancelledBy", order.getCancelledBy());
        }
        
        return detail;
    }
    
    private Map<String, Object> buildDriverSummary(Driver driver) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("driverId", driver.getDriverId());
        summary.put("name", driver.getName());
        summary.put("status", driver.getStatus().name());
        summary.put("vehicleType", driver.getVehicleType().name());
        summary.put("busy", driver.isBusy());
        
        if (driver.getPhone() != null) {
            summary.put("phone", driver.getPhone());
        }
        if (driver.getVehiclePlate() != null) {
            summary.put("vehiclePlate", driver.getVehiclePlate());
        }
        if (driver.getLocation() != null) {
            summary.put("location", driver.getLocation());
        }
        if (driver.getCurrentOrderId() != null) {
            summary.put("currentOrderId", driver.getCurrentOrderId());
        }
        summary.put("lastUpdatedAt", driver.getLastUpdatedAt());
        
        return summary;
    }
    
    private Map<String, Object> buildAuditLogResponse(AuditLog log) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", log.getId());
        response.put("timestamp", log.getTimestamp());
        response.put("orderId", log.getOrderId());
        response.put("action", log.getAction());
        response.put("actorType", log.getActorType());
        response.put("actorId", log.getActorId());
        response.put("previousState", log.getPreviousState());
        response.put("newState", log.getNewState());
        response.put("success", log.isSuccess());
        
        if (log.getFailureReason() != null) {
            response.put("failureReason", log.getFailureReason());
        }
        
        return response;
    }
    
    private Map<String, Object> buildRatePlanResponse(RatePlan ratePlan) {
        Map<String, Object> response = new HashMap<>();
        response.put("vehicleType", ratePlan.getVehicleType().name());
        response.put("baseFare", ratePlan.getBaseFare());
        response.put("perKmRate", ratePlan.getPerKmRate());
        response.put("perMinRate", ratePlan.getPerMinRate());
        response.put("minFare", ratePlan.getMinFare());
        response.put("cancelFee", ratePlan.getCancelFee());
        return response;
    }
}
