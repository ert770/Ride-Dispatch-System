package com.uber.client.model;

/**
 * 車輛類型
 */
public enum VehicleType {
    STANDARD("標準"),
    PREMIUM("尊榮"),
    XL("大型");
    
    private final String displayName;
    
    VehicleType(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}
