package com.uber.passenger;

import com.uber.client.api.ApiClient;
import com.uber.client.model.*;
import com.uber.client.util.Theme;
import com.uber.client.util.UIUtils;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;


/**
 * 乘客端主控制器
 */
public class MainController {
    
    private final BorderPane root;
    private final ApiClient apiClient;
    private final String passengerId;
    
    private Order currentOrder;
    private Timeline pollingTimeline;
    
    // UI Components
    private VBox homeView;
    private VBox orderView;
    
    // Home View Components
    private TextField pickupXField;
    private TextField pickupYField;
    private TextField dropoffXField;
    private TextField dropoffYField;
    private ComboBox<VehicleType> vehicleTypeCombo;
    private Label estimatedFareLabel;
    private Button createOrderBtn;
    
    // Order View Components
    private Label orderStatusLabel;
    private Label driverInfoLabel;
    private Label pickupLabel;
    private Label dropoffLabel;
    private Label fareLabel;
    private VBox tripProgressBox;
    private Button cancelBtn;
    
    public MainController() {
        this.apiClient = new ApiClient();
        this.passengerId = "passenger-" + System.currentTimeMillis() % 1000;
        this.root = new BorderPane();
        
        initUI();
        showHomeView();
    }
    
    public BorderPane getRoot() {
        return root;
    }
    
    private void initUI() {
        root.setStyle("-fx-background-color: " + Theme.BG_DARK + ";");
        createHomeView();
        createOrderView();
    }
    
    private void createHomeView() {
        homeView = new VBox(20);
        homeView.setPadding(new Insets(30));
        homeView.setAlignment(Pos.TOP_CENTER);
        
        // Header
        Label titleLabel = new Label("🚕 叫車服務");
        titleLabel.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 28));
        titleLabel.setTextFill(Color.WHITE);
        
        Label subtitleLabel = new Label("隨時隨地，安全出行");
        subtitleLabel.setFont(Font.font("Microsoft JhengHei", 14));
        subtitleLabel.setTextFill(Color.web(Theme.TEXT_SECONDARY));
        
        VBox headerBox = new VBox(8, titleLabel, subtitleLabel);
        headerBox.setAlignment(Pos.CENTER);
        
        // 上車地點
        VBox pickupCard = createLocationCard("📍 上車地點", true);
        
        // 下車地點
        VBox dropoffCard = createLocationCard("🎯 下車地點", false);
        
        // 車種選擇
        VBox vehicleCard = createVehicleCard();
        
        // 預估車資
        VBox fareCard = new VBox(10);
        fareCard.setStyle("-fx-background-color: " + Theme.BG_CARD + "; -fx-background-radius: 12;");
        fareCard.setPadding(new Insets(20));
        
        Label fareTitle = new Label("💰 預估車資");
        fareTitle.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 16));
        fareTitle.setTextFill(Color.WHITE);
        
        estimatedFareLabel = new Label("--");
        estimatedFareLabel.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 32));
        estimatedFareLabel.setTextFill(Color.web(Theme.SUCCESS));
        
        fareCard.getChildren().addAll(fareTitle, estimatedFareLabel);
        fareCard.setAlignment(Pos.CENTER);
        
        // 叫車按鈕
        createOrderBtn = new Button("🚗 立即叫車");
        createOrderBtn.setMaxWidth(Double.MAX_VALUE);
        createOrderBtn.setStyle("""
            -fx-background-color: linear-gradient(to bottom, #FF9800, #F57C00);
            -fx-text-fill: white;
            -fx-font-size: 18px;
            -fx-font-weight: bold;
            -fx-padding: 16 32;
            -fx-background-radius: 12;
            -fx-cursor: hand;
            """);
        createOrderBtn.setOnAction(e -> createOrder());
        
        // 輸入變更時計算預估車資
        pickupXField.textProperty().addListener((o, old, n) -> calculateEstimate());
        pickupYField.textProperty().addListener((o, old, n) -> calculateEstimate());
        dropoffXField.textProperty().addListener((o, old, n) -> calculateEstimate());
        dropoffYField.textProperty().addListener((o, old, n) -> calculateEstimate());
        vehicleTypeCombo.valueProperty().addListener((o, old, n) -> calculateEstimate());
        
        homeView.getChildren().addAll(headerBox, pickupCard, dropoffCard, vehicleCard, fareCard, createOrderBtn);
    }
    
    private VBox createLocationCard(String title, boolean isPickup) {
        VBox card = new VBox(12);
        card.setStyle("-fx-background-color: " + Theme.BG_CARD + "; -fx-background-radius: 12;");
        card.setPadding(new Insets(20));
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 16));
        titleLabel.setTextFill(Color.WHITE);
        
        HBox coordBox = new HBox(12);
        coordBox.setAlignment(Pos.CENTER_LEFT);
        
        Label xLabel = new Label("X:");
        xLabel.setTextFill(Color.web(Theme.TEXT_SECONDARY));
        xLabel.setFont(Font.font("Microsoft JhengHei", 14));
        
        TextField xField = new TextField();
        xField.setPromptText("0-100");
        xField.setPrefWidth(100);
        xField.setStyle("""
            -fx-background-color: #2A2A2A;
            -fx-text-fill: white;
            -fx-border-color: #444444;
            -fx-border-radius: 8;
            -fx-background-radius: 8;
            -fx-padding: 10;
            """);
        
        Label yLabel = new Label("Y:");
        yLabel.setTextFill(Color.web(Theme.TEXT_SECONDARY));
        yLabel.setFont(Font.font("Microsoft JhengHei", 14));
        
        TextField yField = new TextField();
        yField.setPromptText("0-100");
        yField.setPrefWidth(100);
        yField.setStyle(xField.getStyle());
        
        if (isPickup) {
            pickupXField = xField;
            pickupYField = yField;
            // 預設值
            pickupXField.setText("20");
            pickupYField.setText("30");
        } else {
            dropoffXField = xField;
            dropoffYField = yField;
            // 預設值
            dropoffXField.setText("60");
            dropoffYField.setText("80");
        }
        
        coordBox.getChildren().addAll(xLabel, xField, yLabel, yField);
        card.getChildren().addAll(titleLabel, coordBox);
        
        return card;
    }
    
    private VBox createVehicleCard() {
        VBox card = new VBox(12);
        card.setStyle("-fx-background-color: " + Theme.BG_CARD + "; -fx-background-radius: 12;");
        card.setPadding(new Insets(20));
        
        Label titleLabel = new Label("🚙 選擇車種");
        titleLabel.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 16));
        titleLabel.setTextFill(Color.WHITE);
        
        vehicleTypeCombo = new ComboBox<>();
        vehicleTypeCombo.getItems().addAll(VehicleType.values());
        vehicleTypeCombo.setValue(VehicleType.STANDARD);
        vehicleTypeCombo.setMaxWidth(Double.MAX_VALUE);
        vehicleTypeCombo.setStyle("""
            -fx-background-color: #2A2A2A;
            -fx-border-color: #444444;
            -fx-border-radius: 8;
            -fx-background-radius: 8;
            -fx-padding: 8;
            """);
        
        // 車種說明
        HBox vehicleInfo = new HBox(20);
        vehicleInfo.setAlignment(Pos.CENTER);
        vehicleInfo.setPadding(new Insets(10, 0, 0, 0));
        
        vehicleInfo.getChildren().addAll(
            createVehicleOption("🚗", "標準", "$15/km"),
            createVehicleOption("🚘", "尊榮", "$25/km"),
            createVehicleOption("🚐", "大型", "$30/km")
        );
        
        card.getChildren().addAll(titleLabel, vehicleTypeCombo, vehicleInfo);
        
        return card;
    }
    
    private VBox createVehicleOption(String emoji, String name, String price) {
        VBox box = new VBox(4);
        box.setAlignment(Pos.CENTER);
        
        Label emojiLabel = new Label(emoji);
        emojiLabel.setFont(Font.font(24));
        
        Label nameLabel = new Label(name);
        nameLabel.setTextFill(Color.WHITE);
        nameLabel.setFont(Font.font("Microsoft JhengHei", 12));
        
        Label priceLabel = new Label(price);
        priceLabel.setTextFill(Color.web(Theme.TEXT_SECONDARY));
        priceLabel.setFont(Font.font("Microsoft JhengHei", 10));
        
        box.getChildren().addAll(emojiLabel, nameLabel, priceLabel);
        return box;
    }
    
    private void createOrderView() {
        orderView = new VBox(20);
        orderView.setPadding(new Insets(30));
        orderView.setAlignment(Pos.TOP_CENTER);
        
        // Header
        Label titleLabel = new Label("📋 訂單詳情");
        titleLabel.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 24));
        titleLabel.setTextFill(Color.WHITE);
        
        // 狀態卡片
        VBox statusCard = new VBox(12);
        statusCard.setStyle("-fx-background-color: " + Theme.BG_CARD + "; -fx-background-radius: 12;");
        statusCard.setPadding(new Insets(20));
        statusCard.setAlignment(Pos.CENTER);
        
        Label statusTitle = new Label("訂單狀態");
        statusTitle.setTextFill(Color.web(Theme.TEXT_SECONDARY));
        statusTitle.setFont(Font.font("Microsoft JhengHei", 14));
        
        orderStatusLabel = new Label("等待中...");
        orderStatusLabel.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 24));
        orderStatusLabel.setTextFill(Color.web(Theme.WARNING));
        
        statusCard.getChildren().addAll(statusTitle, orderStatusLabel);
        
        // 行程進度
        tripProgressBox = new VBox(8);
        tripProgressBox.setStyle("-fx-background-color: " + Theme.BG_CARD + "; -fx-background-radius: 12;");
        tripProgressBox.setPadding(new Insets(20));
        createTripProgress();
        
        // 司機資訊
        VBox driverCard = new VBox(12);
        driverCard.setStyle("-fx-background-color: " + Theme.BG_CARD + "; -fx-background-radius: 12;");
        driverCard.setPadding(new Insets(20));
        
        Label driverTitle = new Label("🚗 司機資訊");
        driverTitle.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 16));
        driverTitle.setTextFill(Color.WHITE);
        
        driverInfoLabel = new Label("等待司機接單...");
        driverInfoLabel.setTextFill(Color.web(Theme.TEXT_SECONDARY));
        driverInfoLabel.setFont(Font.font("Microsoft JhengHei", 14));
        driverInfoLabel.setWrapText(true);
        
        driverCard.getChildren().addAll(driverTitle, driverInfoLabel);
        
        // 路線資訊
        VBox routeCard = new VBox(12);
        routeCard.setStyle("-fx-background-color: " + Theme.BG_CARD + "; -fx-background-radius: 12;");
        routeCard.setPadding(new Insets(20));
        
        Label routeTitle = new Label("📍 路線資訊");
        routeTitle.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 16));
        routeTitle.setTextFill(Color.WHITE);
        
        pickupLabel = new Label("上車: --");
        pickupLabel.setTextFill(Color.web(Theme.SUCCESS));
        pickupLabel.setFont(Font.font("Microsoft JhengHei", 14));
        
        dropoffLabel = new Label("下車: --");
        dropoffLabel.setTextFill(Color.web(Theme.ERROR));
        dropoffLabel.setFont(Font.font("Microsoft JhengHei", 14));
        
        routeCard.getChildren().addAll(routeTitle, pickupLabel, dropoffLabel);
        
        // 車資資訊
        VBox fareCard = new VBox(12);
        fareCard.setStyle("-fx-background-color: " + Theme.BG_CARD + "; -fx-background-radius: 12;");
        fareCard.setPadding(new Insets(20));
        fareCard.setAlignment(Pos.CENTER);
        
        Label fareTitleLabel = new Label("💰 車資");
        fareTitleLabel.setTextFill(Color.web(Theme.TEXT_SECONDARY));
        fareTitleLabel.setFont(Font.font("Microsoft JhengHei", 14));
        
        fareLabel = new Label("--");
        fareLabel.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 28));
        fareLabel.setTextFill(Color.web(Theme.SUCCESS));
        
        fareCard.getChildren().addAll(fareTitleLabel, fareLabel);
        
        // 取消按鈕
        cancelBtn = new Button("❌ 取消訂單");
        cancelBtn.setMaxWidth(Double.MAX_VALUE);
        cancelBtn.setStyle("""
            -fx-background-color: linear-gradient(to bottom, #F44336, #D32F2F);
            -fx-text-fill: white;
            -fx-font-size: 16px;
            -fx-font-weight: bold;
            -fx-padding: 14 28;
            -fx-background-radius: 12;
            -fx-cursor: hand;
            """);
        cancelBtn.setOnAction(e -> cancelOrder());
        
        // 返回首頁按鈕（完成或取消後顯示）
        Button backBtn = new Button("🏠 返回首頁");
        backBtn.setMaxWidth(Double.MAX_VALUE);
        backBtn.setVisible(false);
        backBtn.setManaged(false);
        backBtn.setStyle("""
            -fx-background-color: #2A2A2A;
            -fx-border-color: #1976D2;
            -fx-border-width: 2;
            -fx-text-fill: white;
            -fx-font-size: 16px;
            -fx-font-weight: bold;
            -fx-padding: 14 28;
            -fx-background-radius: 12;
            -fx-cursor: hand;
            """);
        backBtn.setOnAction(e -> {
            currentOrder = null;
            showHomeView();
        });
        
        orderView.getChildren().addAll(
            titleLabel, statusCard, tripProgressBox, 
            driverCard, routeCard, fareCard, 
            cancelBtn, backBtn
        );
    }
    
    private void createTripProgress() {
        tripProgressBox.getChildren().clear();
        
        Label title = new Label("🚀 行程進度");
        title.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 16));
        title.setTextFill(Color.WHITE);
        
        HBox progressRow = new HBox(8);
        progressRow.setAlignment(Pos.CENTER);
        
        progressRow.getChildren().addAll(
            createProgressStep("建立", true),
            createProgressLine(false),
            createProgressStep("接單", false),
            createProgressLine(false),
            createProgressStep("行駛", false),
            createProgressLine(false),
            createProgressStep("完成", false)
        );
        
        tripProgressBox.getChildren().addAll(title, progressRow);
    }
    
    private VBox createProgressStep(String label, boolean active) {
        VBox box = new VBox(4);
        box.setAlignment(Pos.CENTER);
        
        Circle circle = new Circle(12);
        circle.setFill(active ? Color.web(Theme.PRIMARY) : Color.web("#444444"));
        circle.setStroke(active ? Color.web(Theme.PRIMARY_LIGHT) : Color.web("#666666"));
        circle.setStrokeWidth(2);
        
        Label text = new Label(label);
        text.setTextFill(active ? Color.WHITE : Color.web(Theme.TEXT_SECONDARY));
        text.setFont(Font.font("Microsoft JhengHei", 11));
        
        box.getChildren().addAll(circle, text);
        return box;
    }
    
    private Region createProgressLine(boolean active) {
        Region line = new Region();
        line.setPrefWidth(30);
        line.setPrefHeight(3);
        line.setStyle("-fx-background-color: " + (active ? Theme.PRIMARY : "#444444") + ";");
        return line;
    }
    
    private void updateTripProgress(OrderStatus status) {
        tripProgressBox.getChildren().clear();
        
        Label title = new Label("🚀 行程進度");
        title.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 16));
        title.setTextFill(Color.WHITE);
        
        HBox progressRow = new HBox(8);
        progressRow.setAlignment(Pos.CENTER);
        
        int step = switch (status) {
            case PENDING -> 1;
            case ACCEPTED -> 2;
            case ONGOING -> 3;
            case COMPLETED -> 4;
            case CANCELLED -> 0;
        };
        
        progressRow.getChildren().addAll(
            createProgressStep("建立", step >= 1),
            createProgressLine(step >= 2),
            createProgressStep("接單", step >= 2),
            createProgressLine(step >= 3),
            createProgressStep("行駛", step >= 3),
            createProgressLine(step >= 4),
            createProgressStep("完成", step >= 4)
        );
        
        tripProgressBox.getChildren().addAll(title, progressRow);
    }
    
    private void showHomeView() {
        stopPolling();
        root.setCenter(homeView);
    }
    
    private void showOrderView() {
        root.setCenter(orderView);
        startPolling();
    }
    
    private void calculateEstimate() {
        try {
            double pickupX = Double.parseDouble(pickupXField.getText());
            double pickupY = Double.parseDouble(pickupYField.getText());
            double dropoffX = Double.parseDouble(dropoffXField.getText());
            double dropoffY = Double.parseDouble(dropoffYField.getText());
            
            double distance = Math.sqrt(Math.pow(dropoffX - pickupX, 2) + Math.pow(dropoffY - pickupY, 2));
            VehicleType type = vehicleTypeCombo.getValue();
            
            double baseFare = switch (type) {
                case STANDARD -> 50;
                case PREMIUM -> 80;
                case XL -> 100;
            };
            
            double perKm = switch (type) {
                case STANDARD -> 15;
                case PREMIUM -> 25;
                case XL -> 30;
            };
            
            double minFare = switch (type) {
                case STANDARD -> 70;
                case PREMIUM -> 120;
                case XL -> 150;
            };
            
            double fare = Math.max(baseFare + distance * perKm, minFare);
            estimatedFareLabel.setText(String.format("$%.0f", fare));
            
        } catch (NumberFormatException e) {
            estimatedFareLabel.setText("--");
        }
    }
    
    private void createOrder() {
        try {
            double pickupX = Double.parseDouble(pickupXField.getText());
            double pickupY = Double.parseDouble(pickupYField.getText());
            double dropoffX = Double.parseDouble(dropoffXField.getText());
            double dropoffY = Double.parseDouble(dropoffYField.getText());
            
            // 驗證座標
            if (pickupX < 0 || pickupX > 100 || pickupY < 0 || pickupY > 100 ||
                dropoffX < 0 || dropoffX > 100 || dropoffY < 0 || dropoffY > 100) {
                UIUtils.showError("錯誤", "座標必須在 0-100 範圍內");
                return;
            }
            
            if (pickupX == dropoffX && pickupY == dropoffY) {
                UIUtils.showError("錯誤", "上車地點和下車地點不可相同");
                return;
            }
            
            Location pickup = new Location(pickupX, pickupY);
            Location dropoff = new Location(dropoffX, dropoffY);
            VehicleType vehicleType = vehicleTypeCombo.getValue();
            
            createOrderBtn.setDisable(true);
            createOrderBtn.setText("建立中...");
            
            apiClient.createOrder(passengerId, pickup, dropoff, vehicleType)
                .whenComplete((response, error) -> {
                    Platform.runLater(() -> {
                        createOrderBtn.setDisable(false);
                        createOrderBtn.setText("🚗 立即叫車");
                        
                        if (error != null) {
                            UIUtils.showError("連線錯誤", "無法連接伺服器: " + error.getMessage());
                            return;
                        }
                        
                        if (response.isSuccess()) {
                            currentOrder = response.getData();
                            updateOrderView();
                            showOrderView();
                        } else {
                            UIUtils.showError("建立失敗", response.getErrorMessage());
                        }
                    });
                });
                
        } catch (NumberFormatException e) {
            UIUtils.showError("錯誤", "請輸入有效的座標數值");
        }
    }
    
    private void cancelOrder() {
        if (currentOrder == null) return;
        
        UIUtils.showConfirm("確認取消", "確定要取消此訂單嗎？\n可能會產生取消費用。")
            .thenAccept(confirmed -> {
                if (confirmed) {
                    cancelBtn.setDisable(true);
                    
                    apiClient.cancelOrder(currentOrder.getOrderId(), passengerId, "乘客取消")
                        .whenComplete((response, error) -> {
                            Platform.runLater(() -> {
                                cancelBtn.setDisable(false);
                                
                                if (error != null) {
                                    UIUtils.showError("連線錯誤", error.getMessage());
                                    return;
                                }
                                
                                if (response.isSuccess()) {
                                    currentOrder = response.getData();
                                    updateOrderView();
                                    UIUtils.showInfo("已取消", "訂單已成功取消");
                                } else {
                                    UIUtils.showError("取消失敗", response.getErrorMessage());
                                }
                            });
                        });
                }
            });
    }
    
    private void updateOrderView() {
        if (currentOrder == null) return;
        
        OrderStatus status = currentOrder.getStatus();
        
        // 更新狀態
        orderStatusLabel.setText(status.getDisplayName());
        orderStatusLabel.setTextFill(Color.web(status.getColor()));
        
        // 更新進度
        updateTripProgress(status);
        
        // 更新司機資訊
        if (currentOrder.getDriverId() != null) {
            String driverInfo = String.format(
                "司機: %s\n電話: %s\n車牌: %s",
                currentOrder.getDriverName() != null ? currentOrder.getDriverName() : currentOrder.getDriverId(),
                currentOrder.getDriverPhone() != null ? currentOrder.getDriverPhone() : "未知",
                currentOrder.getVehiclePlate() != null ? currentOrder.getVehiclePlate() : "未知"
            );
            driverInfoLabel.setText(driverInfo);
            driverInfoLabel.setTextFill(Color.WHITE);
        } else {
            driverInfoLabel.setText("等待司機接單...");
            driverInfoLabel.setTextFill(Color.web(Theme.TEXT_SECONDARY));
        }
        
        // 更新路線
        pickupLabel.setText("上車: " + currentOrder.getPickupLocation());
        dropoffLabel.setText("下車: " + currentOrder.getDropoffLocation());
        
        // 更新車資
        Double fare = currentOrder.getActualFare() != null ? 
            currentOrder.getActualFare() : currentOrder.getEstimatedFare();
        fareLabel.setText(fare != null ? String.format("$%.0f", fare) : "--");
        
        // 更新取消按鈕
        boolean canCancel = status == OrderStatus.PENDING || status == OrderStatus.ACCEPTED;
        cancelBtn.setVisible(canCancel);
        cancelBtn.setManaged(canCancel);
        
        // 完成或取消時顯示返回按鈕
        boolean isFinished = status == OrderStatus.COMPLETED || status == OrderStatus.CANCELLED;
        Button backBtn = (Button) orderView.getChildren().get(orderView.getChildren().size() - 1);
        backBtn.setVisible(isFinished);
        backBtn.setManaged(isFinished);
        
        if (isFinished) {
            stopPolling();
        }
    }
    
    private void startPolling() {
        if (pollingTimeline != null) {
            pollingTimeline.stop();
        }
        
        pollingTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> pollOrderStatus()));
        pollingTimeline.setCycleCount(Timeline.INDEFINITE);
        pollingTimeline.play();
    }
    
    private void stopPolling() {
        if (pollingTimeline != null) {
            pollingTimeline.stop();
            pollingTimeline = null;
        }
    }
    
    private void pollOrderStatus() {
        if (currentOrder == null) return;
        
        apiClient.getOrder(currentOrder.getOrderId())
            .whenComplete((response, error) -> {
                Platform.runLater(() -> {
                    if (error == null && response.isSuccess()) {
                        currentOrder = response.getData();
                        updateOrderView();
                    }
                });
            });
    }
    
    public void shutdown() {
        stopPolling();
    }
}
