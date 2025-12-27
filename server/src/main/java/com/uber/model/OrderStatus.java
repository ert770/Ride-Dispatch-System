package com.uber.model;

/**
 * 訂單狀態列舉
 */
public enum OrderStatus {
    PENDING,    // 等待接單
    ACCEPTED,   // 已接單
    ONGOING,    // 行程中
    COMPLETED,  // 已完成
    CANCELLED   // 已取消
}
