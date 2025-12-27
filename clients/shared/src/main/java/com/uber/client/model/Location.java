package com.uber.client.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 座標位置
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Location {
    private double x;
    private double y;
    
    @Override
    public String toString() {
        return String.format("(%.1f, %.1f)", x, y);
    }
}
