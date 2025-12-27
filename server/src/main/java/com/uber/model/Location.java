package com.uber.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * 2D 座標位置
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Location {
    private double x;
    private double y;
    
    /**
     * 計算與另一點的歐幾里得距離
     */
    public double distanceTo(Location other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
