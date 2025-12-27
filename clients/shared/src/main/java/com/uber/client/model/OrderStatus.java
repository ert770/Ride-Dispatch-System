package com.uber.client.model;

/**
 * 訂單狀態
 */
public enum OrderStatus {
    PENDING("等待中", "#FFA500"),      // 橘色
    ACCEPTED("已接單", "#2196F3"),     // 藍色
    ONGOING("進行中", "#4CAF50"),      // 綠色
    COMPLETED("已完成", "#9E9E9E"),    // 灰色
    CANCELLED("已取消", "#F44336");    // 紅色
    
    private final String displayName;
    private final String color;
    
    OrderStatus(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getColor() {
        return color;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}
