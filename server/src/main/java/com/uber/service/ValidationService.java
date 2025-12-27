package com.uber.service;

import com.uber.exception.BusinessException;
import com.uber.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 驗證服務 - 提供各種業務規則驗證
 * 
 * 此類包含多個驗證方法以增加系統的循環複雜度 (Cyclomatic Complexity)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationService {
    
    /**
     * 驗證訂單建立請求
     */
    public void validateCreateOrderRequest(String passengerId, Location pickup, 
                                           Location dropoff, VehicleType vehicleType) {
        // 驗證乘客 ID
        if (passengerId == null || passengerId.trim().isEmpty()) {
            throw new BusinessException("INVALID_REQUEST", "乘客 ID 不可為空");
        }
        
        // 驗證 pickup 座標
        if (pickup == null) {
            throw new BusinessException("INVALID_REQUEST", "上車地點不可為空");
        }
        
        if (!isValidCoordinate(pickup.getX(), pickup.getY())) {
            throw new BusinessException("INVALID_REQUEST", "上車地點座標無效");
        }
        
        // 驗證 dropoff 座標
        if (dropoff == null) {
            throw new BusinessException("INVALID_REQUEST", "下車地點不可為空");
        }
        
        if (!isValidCoordinate(dropoff.getX(), dropoff.getY())) {
            throw new BusinessException("INVALID_REQUEST", "下車地點座標無效");
        }
        
        // 驗證兩地點不可相同
        if (pickup.getX() == dropoff.getX() && pickup.getY() == dropoff.getY()) {
            throw new BusinessException("INVALID_REQUEST", "上車地點與下車地點不可相同");
        }
        
        // 驗證距離
        double distance = pickup.distanceTo(dropoff);
        if (distance < 0.1) {
            throw new BusinessException("INVALID_REQUEST", "行程距離過短");
        }
        
        if (distance > 200.0) {
            throw new BusinessException("INVALID_REQUEST", "行程距離過長（超過 200 公里）");
        }
        
        // 驗證車種
        if (vehicleType == null) {
            throw new BusinessException("INVALID_REQUEST", "車種類型不可為空");
        }
    }
    
    /**
     * 驗證司機註冊請求
     */
    public void validateDriverRegistration(String driverId, String name, 
                                          String phone, String vehiclePlate, 
                                          VehicleType vehicleType) {
        // 驗證司機 ID
        if (driverId == null || driverId.trim().isEmpty()) {
            throw new BusinessException("INVALID_REQUEST", "司機 ID 不可為空");
        }
        
        if (driverId.length() > 50) {
            throw new BusinessException("INVALID_REQUEST", "司機 ID 過長");
        }
        
        // 驗證姓名
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException("INVALID_REQUEST", "姓名不可為空");
        }
        
        if (name.length() < 2 || name.length() > 50) {
            throw new BusinessException("INVALID_REQUEST", "姓名長度必須在 2-50 字元之間");
        }
        
        // 驗證電話
        if (phone == null || phone.trim().isEmpty()) {
            throw new BusinessException("INVALID_REQUEST", "電話不可為空");
        }
        
        if (!isValidPhoneNumber(phone)) {
            throw new BusinessException("INVALID_REQUEST", "電話格式無效");
        }
        
        // 驗證車牌
        if (vehiclePlate == null || vehiclePlate.trim().isEmpty()) {
            throw new BusinessException("INVALID_REQUEST", "車牌號碼不可為空");
        }
        
        if (!isValidVehiclePlate(vehiclePlate)) {
            throw new BusinessException("INVALID_REQUEST", "車牌格式無效");
        }
        
        // 驗證車種
        if (vehicleType == null) {
            throw new BusinessException("INVALID_REQUEST", "車種類型不可為空");
        }
    }
    
    /**
     * 驗證位置更新請求
     */
    public void validateLocationUpdate(Location location) {
        if (location == null) {
            throw new BusinessException("INVALID_REQUEST", "位置資訊不可為空");
        }
        
        if (!isValidCoordinate(location.getX(), location.getY())) {
            throw new BusinessException("INVALID_REQUEST", "座標無效");
        }
    }
    
    /**
     * 驗證訂單狀態轉換
     */
    public void validateOrderStateTransition(OrderStatus from, OrderStatus to) {
        if (from == null || to == null) {
            throw new BusinessException("INVALID_REQUEST", "訂單狀態不可為空");
        }
        
        // 終止狀態不可再轉換
        if (from == OrderStatus.COMPLETED || from == OrderStatus.CANCELLED) {
            throw new BusinessException("INVALID_STATE", "終止狀態不可變更");
        }
        
        // 檢查合法轉換
        boolean isValidTransition = switch (from) {
            case PENDING -> to == OrderStatus.ACCEPTED || to == OrderStatus.CANCELLED;
            case ACCEPTED -> to == OrderStatus.ONGOING || to == OrderStatus.CANCELLED;
            case ONGOING -> to == OrderStatus.COMPLETED;
            default -> false;
        };
        
        if (!isValidTransition) {
            throw new BusinessException("INVALID_STATE", 
                String.format("不允許從 %s 轉換到 %s", from, to));
        }
    }
    
    /**
     * 驗證司機狀態轉換
     */
    public void validateDriverStateTransition(DriverStatus from, DriverStatus to) {
        if (from == null || to == null) {
            throw new BusinessException("INVALID_REQUEST", "司機狀態不可為空");
        }
        
        // 所有狀態都可以轉換（OFFLINE <-> ONLINE）
        // 但 BUSY 狀態由訂單系統控制
    }
    
    /**
     * 驗證訂單可接單條件
     */
    public void validateOrderAcceptable(Order order) {
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "訂單不存在");
        }
        
        if (order.getStatus() != OrderStatus.PENDING) {
            if (order.getStatus() == OrderStatus.ACCEPTED) {
                throw new BusinessException("ORDER_ALREADY_ACCEPTED", 
                    "此訂單已被其他司機接受", 409);
            }
            throw new BusinessException("INVALID_STATE", "訂單狀態不允許接單操作");
        }
        
        // 檢查訂單是否過期（超過30分鐘）
        if (order.getCreatedAt() != null) {
            long minutesOld = ChronoUnit.MINUTES.between(order.getCreatedAt(), Instant.now());
            if (minutesOld > 30) {
                throw new BusinessException("ORDER_EXPIRED", "訂單已過期");
            }
        }
    }
    
    /**
     * 驗證司機可接單條件
     */
    public void validateDriverCanAccept(Driver driver) {
        if (driver == null) {
            throw new BusinessException("DRIVER_NOT_FOUND", "司機不存在");
        }
        
        if (driver.getStatus() != DriverStatus.ONLINE) {
            throw new BusinessException("DRIVER_OFFLINE", "司機不在線");
        }
        
        if (driver.isBusy()) {
            throw new BusinessException("DRIVER_BUSY", "司機正在忙碌");
        }
        
        // 驗證司機位置
        if (driver.getLocation() == null) {
            throw new BusinessException("INVALID_STATE", "司機位置未設定");
        }
    }
    
    /**
     * 驗證司機與訂單的匹配度
     */
    public void validateDriverOrderMatch(Driver driver, Order order) {
        if (driver == null || order == null) {
            throw new BusinessException("INVALID_REQUEST", "司機或訂單資訊不完整");
        }
        
        // 驗證車種匹配
        if (driver.getVehicleType() != order.getVehicleType()) {
            throw new BusinessException("VEHICLE_TYPE_MISMATCH", "車種不匹配");
        }
        
        // 驗證距離（司機離乘客不能太遠）
        if (driver.getLocation() != null && order.getPickupLocation() != null) {
            double distance = driver.getLocation().distanceTo(order.getPickupLocation());
            if (distance > 50.0) {
                throw new BusinessException("TOO_FAR", "司機距離過遠（超過 50 公里）");
            }
        }
    }
    
    /**
     * 驗證取消訂單條件
     */
    public void validateCancelOrder(Order order, String userId) {
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "訂單不存在");
        }
        
        // 終止狀態不可取消
        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw new BusinessException("INVALID_STATE", "已完成的訂單不可取消");
        }
        
        if (order.getStatus() == OrderStatus.CANCELLED) {
            // 冪等性：已取消則直接返回
            return;
        }
        
        // 行程中不可取消
        if (order.getStatus() == OrderStatus.ONGOING) {
            throw new BusinessException("INVALID_STATE", "行程進行中不可取消");
        }
        
        // 驗證用戶權限（只有乘客可以取消）
        if (!order.getPassengerId().equals(userId)) {
            throw new BusinessException("FORBIDDEN", "只有訂單擁有者可以取消訂單", 403);
        }
    }
    
    /**
     * 驗證費率計畫
     */
    public void validateRatePlan(RatePlan ratePlan) {
        if (ratePlan == null) {
            throw new BusinessException("INVALID_REQUEST", "費率計畫不可為空");
        }
        
        if (ratePlan.getVehicleType() == null) {
            throw new BusinessException("INVALID_REQUEST", "車種類型不可為空");
        }
        
        // 驗證基本費用
        if (ratePlan.getBaseFare() < 0) {
            throw new BusinessException("INVALID_REQUEST", "基本費用不可為負數");
        }
        
        if (ratePlan.getBaseFare() > 500) {
            throw new BusinessException("INVALID_REQUEST", "基本費用過高");
        }
        
        // 驗證每公里費用
        if (ratePlan.getPerKmRate() < 0) {
            throw new BusinessException("INVALID_REQUEST", "每公里費用不可為負數");
        }
        
        if (ratePlan.getPerKmRate() > 100) {
            throw new BusinessException("INVALID_REQUEST", "每公里費用過高");
        }
        
        // 驗證每分鐘費用
        if (ratePlan.getPerMinRate() < 0) {
            throw new BusinessException("INVALID_REQUEST", "每分鐘費用不可為負數");
        }
        
        if (ratePlan.getPerMinRate() > 50) {
            throw new BusinessException("INVALID_REQUEST", "每分鐘費用過高");
        }
        
        // 驗證最低費用
        if (ratePlan.getMinFare() < 0) {
            throw new BusinessException("INVALID_REQUEST", "最低費用不可為負數");
        }
        
        if (ratePlan.getMinFare() > ratePlan.getBaseFare()) {
            // 最低費用應該高於或等於基本費用
            if (ratePlan.getMinFare() < ratePlan.getBaseFare()) {
                throw new BusinessException("INVALID_REQUEST", "最低費用應該高於基本費用");
            }
        }
        
        // 驗證取消費用
        if (ratePlan.getCancelFee() < 0) {
            throw new BusinessException("INVALID_REQUEST", "取消費用不可為負數");
        }
        
        if (ratePlan.getCancelFee() > ratePlan.getMinFare()) {
            throw new BusinessException("INVALID_REQUEST", "取消費用過高");
        }
    }
    
    /**
     * 驗證座標有效性
     */
    private boolean isValidCoordinate(double x, double y) {
        // 假設座標範圍為 -180 到 180（經緯度範圍）
        return x >= -180 && x <= 180 && y >= -90 && y <= 90;
    }
    
    /**
     * 驗證電話號碼格式
     */
    private boolean isValidPhoneNumber(String phone) {
        // 移除空格和連字符
        String cleaned = phone.replaceAll("[\\s-]", "");
        
        // 檢查長度（8-15位數字）
        if (cleaned.length() < 8 || cleaned.length() > 15) {
            return false;
        }
        
        // 檢查是否全為數字或以 + 開頭
        return cleaned.matches("\\+?\\d+");
    }
    
    /**
     * 驗證車牌格式
     */
    private boolean isValidVehiclePlate(String plate) {
        // 移除空格和連字符
        String cleaned = plate.replaceAll("[\\s-]", "");
        
        // 檢查長度（4-10字元）
        if (cleaned.length() < 4 || cleaned.length() > 10) {
            return false;
        }
        
        // 檢查是否包含字母和數字
        boolean hasLetter = cleaned.matches(".*[A-Za-z].*");
        boolean hasDigit = cleaned.matches(".*\\d.*");
        
        return hasLetter && hasDigit;
    }
    
    /**
     * 驗證訂單完整性
     */
    public boolean isOrderComplete(Order order) {
        if (order == null) {
            return false;
        }
        
        boolean hasOrderId = order.getOrderId() != null && !order.getOrderId().trim().isEmpty();
        boolean hasPassengerId = order.getPassengerId() != null && !order.getPassengerId().trim().isEmpty();
        boolean hasStatus = order.getStatus() != null;
        boolean hasVehicleType = order.getVehicleType() != null;
        boolean hasPickup = order.getPickupLocation() != null;
        boolean hasDropoff = order.getDropoffLocation() != null;
        
        return hasOrderId && hasPassengerId && hasStatus && 
               hasVehicleType && hasPickup && hasDropoff;
    }
    
    /**
     * 驗證司機完整性
     */
    public boolean isDriverComplete(Driver driver) {
        if (driver == null) {
            return false;
        }
        
        boolean hasDriverId = driver.getDriverId() != null && !driver.getDriverId().trim().isEmpty();
        boolean hasName = driver.getName() != null && !driver.getName().trim().isEmpty();
        boolean hasPhone = driver.getPhone() != null && !driver.getPhone().trim().isEmpty();
        boolean hasPlate = driver.getVehiclePlate() != null && !driver.getVehiclePlate().trim().isEmpty();
        boolean hasVehicleType = driver.getVehicleType() != null;
        boolean hasStatus = driver.getStatus() != null;
        
        return hasDriverId && hasName && hasPhone && hasPlate && 
               hasVehicleType && hasStatus;
    }
}

