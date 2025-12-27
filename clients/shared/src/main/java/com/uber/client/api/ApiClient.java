package com.uber.client.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uber.client.model.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST API 客戶端
 * 提供與後端伺服器通訊的所有 HTTP 操作
 */
public class ApiClient {
    
    private static final String DEFAULT_BASE_URL = "http://localhost:8080/api";
    
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public ApiClient() {
        this(DEFAULT_BASE_URL);
    }
    
    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    
    // ============ Passenger API ============
    
    /**
     * 建立訂單
     */
    public CompletableFuture<ApiResponse<Order>> createOrder(String passengerId, 
            Location pickup, Location dropoff, VehicleType vehicleType) {
        Map<String, Object> body = Map.of(
            "passengerId", passengerId,
            "pickupLocation", Map.of("x", pickup.getX(), "y", pickup.getY()),
            "dropoffLocation", Map.of("x", dropoff.getX(), "y", dropoff.getY()),
            "vehicleType", vehicleType.name()
        );
        return post("/orders", body, new TypeReference<ApiResponse<Order>>() {});
    }
    
    /**
     * 查詢訂單
     */
    public CompletableFuture<ApiResponse<Order>> getOrder(String orderId) {
        return get("/orders/" + orderId, new TypeReference<ApiResponse<Order>>() {});
    }
    
    /**
     * 取消訂單
     */
    public CompletableFuture<ApiResponse<Order>> cancelOrder(String orderId, String cancelledBy, String reason) {
        Map<String, Object> body = Map.of(
            "cancelledBy", cancelledBy,
            "reason", reason != null ? reason : ""
        );
        return put("/orders/" + orderId + "/cancel", body, new TypeReference<ApiResponse<Order>>() {});
    }
    
    // ============ Driver API ============
    
    /**
     * 司機上線
     */
    public CompletableFuture<ApiResponse<Driver>> goOnline(String driverId, Location location) {
        Map<String, Object> body = Map.of(
            "location", Map.of("x", location.getX(), "y", location.getY())
        );
        return put("/drivers/" + driverId + "/online", body, new TypeReference<ApiResponse<Driver>>() {});
    }
    
    /**
     * 司機下線
     */
    public CompletableFuture<ApiResponse<Driver>> goOffline(String driverId) {
        return put("/drivers/" + driverId + "/offline", null, new TypeReference<ApiResponse<Driver>>() {});
    }
    
    /**
     * 更新司機位置
     */
    public CompletableFuture<ApiResponse<Driver>> updateLocation(String driverId, Location location) {
        Map<String, Object> body = Map.of(
            "x", location.getX(),
            "y", location.getY()
        );
        return put("/drivers/" + driverId + "/location", body, new TypeReference<ApiResponse<Driver>>() {});
    }
    
    /**
     * 取得可接訂單列表
     */
    public CompletableFuture<ApiResponse<Map<String, Object>>> getOffers(String driverId) {
        return get("/drivers/" + driverId + "/offers", new TypeReference<ApiResponse<Map<String, Object>>>() {});
    }
    
    /**
     * 接受訂單
     */
    public CompletableFuture<ApiResponse<Order>> acceptOrder(String orderId, String driverId) {
        Map<String, Object> body = Map.of("driverId", driverId);
        return put("/orders/" + orderId + "/accept", body, new TypeReference<ApiResponse<Order>>() {});
    }
    
    /**
     * 開始行程
     */
    public CompletableFuture<ApiResponse<Order>> startTrip(String orderId, String driverId) {
        Map<String, Object> body = Map.of("driverId", driverId);
        return put("/orders/" + orderId + "/start", body, new TypeReference<ApiResponse<Order>>() {});
    }
    
    /**
     * 完成行程
     */
    public CompletableFuture<ApiResponse<Order>> completeTrip(String orderId, String driverId) {
        Map<String, Object> body = Map.of("driverId", driverId);
        return put("/orders/" + orderId + "/complete", body, new TypeReference<ApiResponse<Order>>() {});
    }
    
    /**
     * 取得司機資訊
     */
    public CompletableFuture<ApiResponse<Driver>> getDriver(String driverId) {
        return get("/drivers/" + driverId, new TypeReference<ApiResponse<Driver>>() {});
    }
    
    /**
     * 註冊司機
     */
    public CompletableFuture<ApiResponse<Driver>> registerDriver(String driverId, String name, 
            String phone, String vehiclePlate, VehicleType vehicleType) {
        Map<String, Object> body = Map.of(
            "driverId", driverId,
            "name", name,
            "phone", phone,
            "vehiclePlate", vehiclePlate,
            "vehicleType", vehicleType.name()
        );
        return post("/drivers", body, new TypeReference<ApiResponse<Driver>>() {});
    }
    
    // ============ Admin API ============
    
    /**
     * 取得所有訂單
     */
    public CompletableFuture<ApiResponse<Map<String, Object>>> getAllOrders(String status, int page, int size) {
        String query = String.format("?page=%d&size=%d", page, size);
        if (status != null && !status.isEmpty()) {
            query += "&status=" + status;
        }
        return get("/admin/orders" + query, new TypeReference<ApiResponse<Map<String, Object>>>() {});
    }
    
    /**
     * 取得所有司機
     */
    public CompletableFuture<ApiResponse<Map<String, Object>>> getAllDrivers() {
        return get("/admin/drivers", new TypeReference<ApiResponse<Map<String, Object>>>() {});
    }
    
    /**
     * 取得審計日誌
     */
    public CompletableFuture<ApiResponse<Map<String, Object>>> getAuditLogs(String orderId, String action) {
        StringBuilder query = new StringBuilder("?");
        if (orderId != null && !orderId.isEmpty()) {
            query.append("orderId=").append(orderId).append("&");
        }
        if (action != null && !action.isEmpty()) {
            query.append("action=").append(action);
        }
        return get("/admin/audit-logs" + query, new TypeReference<ApiResponse<Map<String, Object>>>() {});
    }
    
    /**
     * 取得費率設定
     */
    public CompletableFuture<ApiResponse<Map<String, Object>>> getRatePlans() {
        return get("/admin/rate-plans", new TypeReference<ApiResponse<Map<String, Object>>>() {});
    }
    
    /**
     * 更新費率設定
     */
    public CompletableFuture<ApiResponse<RatePlan>> updateRatePlan(VehicleType vehicleType, 
            double baseFare, double perKmRate, double perMinRate, double minFare) {
        Map<String, Object> body = Map.of(
            "baseFare", baseFare,
            "perKmRate", perKmRate,
            "perMinRate", perMinRate,
            "minFare", minFare
        );
        return put("/admin/rate-plans/" + vehicleType.name(), body, 
                new TypeReference<ApiResponse<RatePlan>>() {});
    }
    
    // ============ HTTP 方法 ============
    
    private <T> CompletableFuture<T> get(String path, TypeReference<T> typeRef) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .GET()
                .build();
        
        return sendRequest(request, typeRef);
    }
    
    private <T> CompletableFuture<T> post(String path, Object body, TypeReference<T> typeRef) {
        try {
            String jsonBody = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            
            return sendRequest(request, typeRef);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    private <T> CompletableFuture<T> put(String path, Object body, TypeReference<T> typeRef) {
        try {
            String jsonBody = body != null ? objectMapper.writeValueAsString(body) : "{}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            
            return sendRequest(request, typeRef);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    private <T> CompletableFuture<T> sendRequest(HttpRequest request, TypeReference<T> typeRef) {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    try {
                        return objectMapper.readValue(response.body(), typeRef);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to parse response: " + response.body(), e);
                    }
                });
    }
}
