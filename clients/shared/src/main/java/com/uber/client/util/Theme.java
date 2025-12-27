package com.uber.client.util;

import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;

/**
 * 主題常數和樣式定義
 */
public class Theme {
    
    // 主要顏色
    public static final String PRIMARY = "#1976D2";           // 藍色
    public static final String PRIMARY_DARK = "#1565C0";
    public static final String PRIMARY_LIGHT = "#42A5F5";
    
    public static final String SECONDARY = "#FF9800";         // 橘色
    public static final String SECONDARY_DARK = "#F57C00";
    
    public static final String SUCCESS = "#4CAF50";           // 綠色
    public static final String WARNING = "#FFC107";           // 黃色
    public static final String ERROR = "#F44336";             // 紅色
    public static final String INFO = "#2196F3";              // 淺藍色
    
    // 背景顏色
    public static final String BG_DARK = "#121212";
    public static final String BG_CARD = "#1E1E1E";
    public static final String BG_HOVER = "#2A2A2A";
    public static final String BG_LIGHT = "#FAFAFA";
    
    // 文字顏色
    public static final String TEXT_PRIMARY = "#FFFFFF";
    public static final String TEXT_SECONDARY = "#B0B0B0";
    public static final String TEXT_DARK = "#212121";
    
    // 邊框
    public static final String BORDER = "#333333";
    public static final String BORDER_LIGHT = "#E0E0E0";
    
    // 字體大小
    public static final int FONT_SMALL = 12;
    public static final int FONT_NORMAL = 14;
    public static final int FONT_LARGE = 18;
    public static final int FONT_XLARGE = 24;
    public static final int FONT_TITLE = 32;
    
    // 間距
    public static final int SPACING_SMALL = 8;
    public static final int SPACING_NORMAL = 16;
    public static final int SPACING_LARGE = 24;
    
    // 圓角
    public static final int RADIUS_SMALL = 4;
    public static final int RADIUS_NORMAL = 8;
    public static final int RADIUS_LARGE = 16;
    
    /**
     * 取得漸層背景
     */
    public static LinearGradient getGradientBackground() {
        return new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#1a1a2e")),
                new Stop(1, Color.web("#16213e")));
    }
    
    /**
     * 取得按鈕漸層
     */
    public static LinearGradient getButtonGradient() {
        return new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web(PRIMARY)),
                new Stop(1, Color.web(PRIMARY_DARK)));
    }
    
    /**
     * 基礎樣式表
     */
    public static String getBaseStyles() {
        return """
            .root {
                -fx-font-family: 'Microsoft JhengHei', 'Segoe UI', sans-serif;
                -fx-background-color: #121212;
            }
            
            .label {
                -fx-text-fill: #FFFFFF;
            }
            
            .text-field, .password-field {
                -fx-background-color: #2A2A2A;
                -fx-text-fill: #FFFFFF;
                -fx-border-color: #444444;
                -fx-border-radius: 8;
                -fx-background-radius: 8;
                -fx-padding: 12;
                -fx-font-size: 14px;
            }
            
            .text-field:focused, .password-field:focused {
                -fx-border-color: #1976D2;
                -fx-effect: dropshadow(gaussian, rgba(25, 118, 210, 0.3), 8, 0, 0, 0);
            }
            
            .button {
                -fx-background-color: linear-gradient(to bottom, #1976D2, #1565C0);
                -fx-text-fill: #FFFFFF;
                -fx-font-size: 14px;
                -fx-font-weight: bold;
                -fx-padding: 12 24;
                -fx-background-radius: 8;
                -fx-cursor: hand;
            }
            
            .button:hover {
                -fx-background-color: linear-gradient(to bottom, #2196F3, #1976D2);
                -fx-effect: dropshadow(gaussian, rgba(33, 150, 243, 0.4), 12, 0, 0, 2);
            }
            
            .button:pressed {
                -fx-background-color: #1565C0;
            }
            
            .button-secondary {
                -fx-background-color: #2A2A2A;
                -fx-border-color: #1976D2;
                -fx-border-width: 2;
            }
            
            .button-secondary:hover {
                -fx-background-color: #333333;
            }
            
            .button-success {
                -fx-background-color: linear-gradient(to bottom, #4CAF50, #388E3C);
            }
            
            .button-success:hover {
                -fx-background-color: linear-gradient(to bottom, #66BB6A, #4CAF50);
            }
            
            .button-danger {
                -fx-background-color: linear-gradient(to bottom, #F44336, #D32F2F);
            }
            
            .button-danger:hover {
                -fx-background-color: linear-gradient(to bottom, #EF5350, #F44336);
            }
            
            .button-warning {
                -fx-background-color: linear-gradient(to bottom, #FF9800, #F57C00);
            }
            
            .card {
                -fx-background-color: #1E1E1E;
                -fx-background-radius: 12;
                -fx-padding: 20;
                -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.3), 10, 0, 0, 4);
            }
            
            .card:hover {
                -fx-background-color: #252525;
            }
            
            .title {
                -fx-font-size: 28px;
                -fx-font-weight: bold;
                -fx-text-fill: #FFFFFF;
            }
            
            .subtitle {
                -fx-font-size: 16px;
                -fx-text-fill: #B0B0B0;
            }
            
            .table-view {
                -fx-background-color: #1E1E1E;
                -fx-border-color: #333333;
            }
            
            .table-view .column-header {
                -fx-background-color: #252525;
            }
            
            .table-view .column-header .label {
                -fx-text-fill: #FFFFFF;
                -fx-font-weight: bold;
            }
            
            .table-row-cell {
                -fx-background-color: #1E1E1E;
            }
            
            .table-row-cell:hover {
                -fx-background-color: #2A2A2A;
            }
            
            .table-row-cell:selected {
                -fx-background-color: #1976D2;
            }
            
            .table-cell {
                -fx-text-fill: #FFFFFF;
            }
            
            .scroll-pane {
                -fx-background-color: transparent;
            }
            
            .scroll-pane > .viewport {
                -fx-background-color: transparent;
            }
            
            .combo-box {
                -fx-background-color: #2A2A2A;
                -fx-border-color: #444444;
                -fx-border-radius: 8;
                -fx-background-radius: 8;
            }
            
            .combo-box .list-cell {
                -fx-text-fill: #FFFFFF;
                -fx-background-color: #2A2A2A;
            }
            
            .combo-box-popup .list-view {
                -fx-background-color: #2A2A2A;
            }
            
            .combo-box-popup .list-cell:hover {
                -fx-background-color: #3A3A3A;
            }
            
            .status-pending {
                -fx-text-fill: #FFA500;
            }
            
            .status-accepted {
                -fx-text-fill: #2196F3;
            }
            
            .status-ongoing {
                -fx-text-fill: #4CAF50;
            }
            
            .status-completed {
                -fx-text-fill: #9E9E9E;
            }
            
            .status-cancelled {
                -fx-text-fill: #F44336;
            }
            """;
    }
}
