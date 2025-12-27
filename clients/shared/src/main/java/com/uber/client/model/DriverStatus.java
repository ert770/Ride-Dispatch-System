package com.uber.client.model;

/**
 * 司機狀態
 */
public enum DriverStatus {
    OFFLINE("離線", "#9E9E9E"),  // 灰色
    ONLINE("上線中", "#4CAF50"); // 綠色
    
    private final String displayName;
    private final String color;
    
    DriverStatus(String displayName, String color) {
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
