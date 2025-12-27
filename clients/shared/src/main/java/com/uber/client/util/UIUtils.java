package com.uber.client.util;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * UI 工具類
 */
public class UIUtils {
    
    /**
     * 顯示錯誤對話框
     */
    public static void showError(String title, String content) {
        runOnFxThread(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }
    
    /**
     * 顯示資訊對話框
     */
    public static void showInfo(String title, String content) {
        runOnFxThread(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }
    
    /**
     * 顯示成功對話框
     */
    public static void showSuccess(String content) {
        showInfo("成功", content);
    }
    
    /**
     * 顯示確認對話框
     */
    public static CompletableFuture<Boolean> showConfirm(String title, String content) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        runOnFxThread(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            Optional<ButtonType> result = alert.showAndWait();
            future.complete(result.isPresent() && result.get() == ButtonType.OK);
        });
        return future;
    }
    
    /**
     * 在 JavaFX 執行緒上執行
     */
    public static void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
    
    /**
     * 處理非同步 API 呼叫的結果
     */
    public static <T> void handleApiResponse(CompletableFuture<T> future, 
            Consumer<T> onSuccess, Consumer<Throwable> onError) {
        future.whenComplete((result, error) -> {
            runOnFxThread(() -> {
                if (error != null) {
                    onError.accept(error);
                } else {
                    onSuccess.accept(result);
                }
            });
        });
    }
    
    /**
     * 格式化金額
     */
    public static String formatCurrency(Double amount) {
        if (amount == null) return "-";
        return String.format("$%.0f", amount);
    }
    
    /**
     * 格式化距離
     */
    public static String formatDistance(Double km) {
        if (km == null) return "-";
        return String.format("%.1f km", km);
    }
    
    /**
     * 格式化時間
     */
    public static String formatDuration(Integer minutes) {
        if (minutes == null) return "-";
        if (minutes < 60) {
            return minutes + " 分鐘";
        }
        return (minutes / 60) + " 小時 " + (minutes % 60) + " 分鐘";
    }
}
