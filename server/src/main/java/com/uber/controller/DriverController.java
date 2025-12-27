package com.uber.controller;

import com.uber.dto.ApiResponse;
import com.uber.dto.DriverOnlineRequest;
import com.uber.dto.RegisterDriverRequest;
import com.uber.model.Driver;
import com.uber.model.Location;
import com.uber.model.Order;
import com.uber.service.DriverService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 司機 API Controller
 * 
 * 實作 Issue #16: 完成 DriverController REST API
 * 
 * 端點:
 * - POST   /api/drivers                    : 註冊司機
 * - GET    /api/drivers/{driverId}         : 取得司機資訊
 * - GET    /api/drivers                    : 取得所有司機
 * - PUT    /api/drivers/{driverId}/online  : 司機上線
 * - PUT    /api/drivers/{driverId}/offline : 司機下線
 * - PUT    /api/drivers/{driverId}/location: 更新位置
 * - GET    /api/drivers/{driverId}/offers  : 取得可接訂單
 */
@RestController
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
public class DriverController {
    
    private final DriverService driverService;
    
    /**
     * 註冊司機
     * POST /api/drivers
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> registerDriver(
            @Valid @RequestBody RegisterDriverRequest request) {
        Driver driver = driverService.registerDriver(
                request.getDriverId(),
                request.getName(),
                request.getPhone(),
                request.getVehiclePlate(),
                request.getVehicleType()
        );
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(buildDriverResponse(driver)));
    }
    
    /**
     * 司機上線
     * PUT /api/drivers/{driverId}/online
     */
    @PutMapping("/{driverId}/online")
    public ResponseEntity<ApiResponse<Map<String, Object>>> goOnline(
            @PathVariable String driverId,
            @Valid @RequestBody DriverOnlineRequest request) {
        Driver driver = driverService.goOnline(driverId, request.getLocation());
        return ResponseEntity.ok(ApiResponse.success(buildDriverResponse(driver)));
    }
    
    /**
     * 司機下線
     * PUT /api/drivers/{driverId}/offline
     */
    @PutMapping("/{driverId}/offline")
    public ResponseEntity<ApiResponse<Map<String, Object>>> goOffline(@PathVariable String driverId) {
        Driver driver = driverService.goOffline(driverId);
        return ResponseEntity.ok(ApiResponse.success(buildDriverResponse(driver)));
    }
    
    /**
     * 更新位置
     * PUT /api/drivers/{driverId}/location
     */
    @PutMapping("/{driverId}/location")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateLocation(
            @PathVariable String driverId,
            @RequestBody Location location) {
        Driver driver = driverService.updateLocation(driverId, location);
        
        Map<String, Object> response = new HashMap<>();
        response.put("driverId", driver.getDriverId());
        response.put("location", driver.getLocation());
        response.put("updatedAt", driver.getLastUpdatedAt());
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    /**
     * 取得可接訂單列表
     * GET /api/drivers/{driverId}/offers
     */
    @GetMapping("/{driverId}/offers")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOffers(@PathVariable String driverId) {
        List<Order> offers = driverService.getOffers(driverId);
        
        // 轉換為精簡的 offer 格式
        List<Map<String, Object>> offerList = offers.stream()
                .map(order -> {
                    Map<String, Object> offer = new HashMap<>();
                    offer.put("orderId", order.getOrderId());
                    offer.put("pickupLocation", order.getPickupLocation());
                    offer.put("dropoffLocation", order.getDropoffLocation());
                    offer.put("vehicleType", order.getVehicleType().name());
                    offer.put("distance", order.getDistance());
                    offer.put("estimatedFare", order.getEstimatedFare());
                    offer.put("createdAt", order.getCreatedAt());
                    return offer;
                })
                .collect(Collectors.toList());
        
        Map<String, Object> response = new HashMap<>();
        response.put("offers", offerList);
        response.put("count", offerList.size());
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    /**
     * 取得司機資訊
     * GET /api/drivers/{driverId}
     */
    @GetMapping("/{driverId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDriver(@PathVariable String driverId) {
        Driver driver = driverService.getDriver(driverId);
        return ResponseEntity.ok(ApiResponse.success(buildDriverResponse(driver)));
    }
    
    /**
     * 取得所有司機
     * GET /api/drivers
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAllDrivers(
            @RequestParam(required = false) String status) {
        List<Driver> drivers = driverService.getAllDrivers();
        
        // 若有指定狀態，則篩選
        if (status != null && !status.isEmpty()) {
            drivers = drivers.stream()
                    .filter(d -> d.getStatus().name().equalsIgnoreCase(status))
                    .toList();
        }
        
        List<Map<String, Object>> driverList = drivers.stream()
                .map(this::buildDriverResponse)
                .collect(Collectors.toList());
        
        Map<String, Object> response = new HashMap<>();
        response.put("drivers", driverList);
        response.put("count", driverList.size());
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    /**
     * 建構司機回應資料
     */
    private Map<String, Object> buildDriverResponse(Driver driver) {
        Map<String, Object> response = new HashMap<>();
        response.put("driverId", driver.getDriverId());
        response.put("name", driver.getName());
        response.put("status", driver.getStatus().name());
        response.put("vehicleType", driver.getVehicleType().name());
        response.put("busy", driver.isBusy());
        response.put("updatedAt", driver.getLastUpdatedAt());
        
        if (driver.getPhone() != null) {
            response.put("phone", driver.getPhone());
        }
        if (driver.getVehiclePlate() != null) {
            response.put("vehiclePlate", driver.getVehiclePlate());
        }
        if (driver.getLocation() != null) {
            response.put("location", driver.getLocation());
        }
        if (driver.getCurrentOrderId() != null) {
            response.put("currentOrderId", driver.getCurrentOrderId());
        }
        
        return response;
    }
}
