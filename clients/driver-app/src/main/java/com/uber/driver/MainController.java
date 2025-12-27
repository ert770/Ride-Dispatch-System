package com.uber.driver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 司機端主控制器
 */
public class MainController {
    
    private final BorderPane root;
    private final ApiClient apiClient;
    private final ObjectMapper objectMapper;
    
    private Driver currentDriver;
    private Order currentOrder;
    private Timeline pollingTimeline;
    
    // Views
    private VBox loginView;
    private VBox mainView;
    private VBox orderView;
    
    // Login Components
    private TextField driverIdField;
    private TextField nameField;
    private TextField phoneField;
    private TextField vehiclePlateField;
    private ComboBox<VehicleType> vehicleTypeCombo;
    private TextField locationXField;
    private TextField locationYField;
    
    // Main View Components
    private Label statusLabel;
    private Label locationLabel;
    private ToggleButton onlineToggle;
    private VBox offersListBox;
    
    // Order View Components  
    private Label orderStatusLabel;
    private Label passengerLabel;
    private Label routeLabel;
    private Label fareLabel;
    private Button actionBtn;
    
    public MainController() {
        this.apiClient = new ApiClient();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.root = new BorderPane();
        
        initUI();
        showLoginView();
    }
    
    public BorderPane getRoot() {
        return root;
    }
    
    private void initUI() {
        root.setStyle("-fx-background-color: " + Theme.BG_DARK + ";");
        createLoginView();
        createMainView();
        createOrderView();
    }
    
    private void createLoginView() {
        loginView = new VBox(20);
        loginView.setPadding(new Insets(30));
        loginView.setAlignment(Pos.TOP_CENTER);
        
        // Header
        Label titleLabel = new Label("🚗 司機註冊 / 登入");
        titleLabel.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 24));
        titleLabel.setTextFill(Color.WHITE);
        
        Label subtitleLabel = new Label("開始接單賺錢");
        subtitleLabel.setFont(Font.font("Microsoft JhengHei", 14));
        subtitleLabel.setTextFill(Color.web(Theme.TEXT_SECONDARY));
        
        VBox headerBox = new VBox(8, titleLabel, subtitleLabel);
        headerBox.setAlignment(Pos.CENTER);
        
        // 司機 ID
        VBox idCard = createInputCard("🆔 司機編號", "輸入司機 ID");
        driverIdField = (TextField) ((VBox) idCard).getChildren().get(1);
        driverIdField.setText("driver-" + (System.currentTimeMillis() % 1000));
        
        // 姓名
        VBox nameCard = createInputCard("👤 姓名", "輸入姓名");
        nameField = (TextField) ((VBox) nameCard).getChildren().get(1);
        nameField.setText("王司機");
        
        // 電話
        VBox phoneCard = createInputCard("📱 電話", "輸入電話號碼");
        phoneField = (TextField) ((VBox) phoneCard).getChildren().get(1);
        phoneField.setText("0912-345-678");
        
        // 車牌
        VBox plateCard = createInputCard("🚙 車牌號碼", "輸入車牌");
        vehiclePlateField = (TextField) ((VBox) plateCard).getChildren().get(1);
        vehiclePlateField.setText("ABC-1234");
        
        // 車種
        VBox vehicleCard = new VBox(8);
        vehicleCard.setStyle("-fx-background-color: " + Theme.BG_CARD + "; -fx-background-radius: 12;");
        vehicleCard.setPadding(new Insets(16));
        
        Label vehicleLabel = new Label("🚘 車種");
        vehicleLabel.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 14));
        vehicleLabel.setTextFill(Color.WHITE);
        
        vehicleTypeCombo = new ComboBox<>();
        vehicleTypeCombo.getItems().addAll(VehicleType.values());
        vehicleTypeCombo.setValue(VehicleType.STANDARD);
        vehicleTypeCombo.setMaxWidth(Double.MAX_VALUE);
        vehicleTypeCombo.setStyle("""
            -fx-background-color: #2A2A2A;
            -fx-border-color: #444444;
            -fx-border-radius: 8;
            -fx-background-radius: 8;
            """);
        
        vehicleCard.getChildren().addAll(vehicleLabel, vehicleTypeCombo);
        
        // 初始位置
        VBox locationCard = new VBox(12);
        locationCard.setStyle("-fx-background-color: " + Theme.BG_CARD + "; -fx-background-radius: 12;");
        locationCard.setPadding(new Insets(16));
        
        Label locationTitle = new Label("📍 初始位置");
        locationTitle.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 14));
        locationTitle.setTextFill(Color.WHITE);
        
        HBox coordBox = new HBox(12);
        coordBox.setAlignment(Pos.CENTER_LEFT);
        
        Label xLabel = new Label("X:");
        xLabel.setTextFill(Color.web(Theme.TEXT_SECONDARY));
        
        locationXField = new TextField("25");
        locationXField.setPrefWidth(80);
        locationXField.setStyle("""
            -fx-background-color: #2A2A2A;
            -fx-text-fill: white;
            -fx-border-color: #444444;
            -fx-border-radius: 8;
            -fx-background-radius: 8;
            -fx-padding: 8;
            """);
        
        Label yLabel = new Label("Y:");
        yLabel.setTextFill(Color.web(Theme.TEXT_SECONDARY));
        
        locationYField = new TextField("35");
        locationYField.setPrefWidth(80);
        locationYField.setStyle(locationXField.getStyle());
        
        coordBox.getChildren().addAll(xLabel, locationXField, yLabel, locationYField);
        locationCard.getChildren().addAll(locationTitle, coordBox);
        
        // 登入按鈕
        Button loginBtn = new Button("🚀 開始接單");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setStyle("""
            -fx-background-color: linear-gradient(to bottom, #4CAF50, #388E3C);
            -fx-text-fill: white;
            -fx-font-size: 18px;
            -fx-font-weight: bold;
            -fx-padding: 16 32;
            -fx-background-radius: 12;
            -fx-cursor: hand;
            """);
        loginBtn.setOnAction(e -> registerAndLogin());
        
        ScrollPane scrollPane = new ScrollPane();
        VBox content = new VBox(16, headerBox, idCard, nameCard, phoneCard, plateCard, vehicleCard, locationCard, loginBtn);
        content.setPadding(new Insets(10));
        scrollPane.setContent(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");
        
        loginView.getChildren().add(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
    }
    
    private VBox createInputCard(String title, String placeholder) {
        VBox card = new VBox(8);
        card.setStyle("-fx-background-color: " + Theme.BG_CARD + "; -fx-background-radius: 12;");
        card.setPadding(new Insets(16));
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 14));
        titleLabel.setTextFill(Color.WHITE);
        
        TextField field = new TextField();
        field.setPromptText(placeholder);
        field.setMaxWidth(Double.MAX_VALUE);
        field.setStyle("""
            -fx-background-color: #2A2A2A;
            -fx-text-fill: white;
            -fx-border-color: #444444;
            -fx-border-radius: 8;
            -fx-background-radius: 8;
            -fx-padding: 12;
            """);
        
        card.getChildren().addAll(titleLabel, field);
        return card;
    }
    
    private void createMainView() {
        mainView = new VBox(16);
        mainView.setPadding(new Insets(20));
        
        // Header
        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: " + Theme.BG_CARD + "; -fx-background-radius: 12;");
        header.setPadding(new Insets(16));
        
        VBox driverInfo = new VBox(4);
        Label driverNameLabel = new Label("🚗 司機模式");
        driverNameLabel.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 18));
        driverNameLabel.setTextFill(Color.WHITE);
        
        statusLabel = new Label("離線中");
        statusLabel.setFont(Font.font("Microsoft JhengHei", 14));
        statusLabel.setTextFill(Color.web(Theme.TEXT_SECONDARY));
        
        locationLabel = new Label("位置: --");
        locationLabel.setFont(Font.font("Microsoft JhengHei", 12));
        locationLabel.setTextFill(Color.web(Theme.TEXT_SECONDARY));
        
        driverInfo.getChildren().addAll(driverNameLabel, statusLabel, locationLabel);
        HBox.setHgrow(driverInfo, Priority.ALWAYS);
        
        // 上線/下線切換
        onlineToggle = new ToggleButton("上線");
        onlineToggle.setStyle("""
            -fx-background-color: #2A2A2A;
            -fx-text-fill: white;
            -fx-font-size: 14px;
            -fx-padding: 12 24;
            -fx-background-radius: 20;
            -fx-border-color: #4CAF50;
            -fx-border-radius: 20;
            -fx-border-width: 2;
            """);
        onlineToggle.setOnAction(e -> toggleOnline());
        
        header.getChildren().addAll(driverInfo, onlineToggle);
        
        // 訂單列表
        Label offersTitle = new Label("📋 可接訂單");
        offersTitle.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 18));
        offersTitle.setTextFill(Color.WHITE);
        
        offersListBox = new VBox(12);
        offersListBox.setPadding(new Insets(10, 0, 10, 0));
        
        Label emptyLabel = new Label("目前沒有可接的訂單\n請保持上線狀態");
        emptyLabel.setTextFill(Color.web(Theme.TEXT_SECONDARY));
        emptyLabel.setFont(Font.font("Microsoft JhengHei", 14));
        emptyLabel.setAlignment(Pos.CENTER);
        emptyLabel.setMaxWidth(Double.MAX_VALUE);
        offersListBox.getChildren().add(emptyLabel);
        
        ScrollPane scrollPane = new ScrollPane(offersListBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        
        // 登出按鈕
        Button logoutBtn = new Button("🔚 登出");
        logoutBtn.setMaxWidth(Double.MAX_VALUE);
        logoutBtn.setStyle("""
            -fx-background-color: #2A2A2A;
            -fx-border-color: #F44336;
            -fx-border-width: 2;
            -fx-text-fill: #F44336;
            -fx-font-size: 14px;
            -fx-padding: 12 24;
            -fx-background-radius: 8;
            -fx-border-radius: 8;
            """);
        logoutBtn.setOnAction(e -> logout());
        
        mainView.getChildren().addAll(header, offersTitle, scrollPane, logoutBtn);
    }
    
    private void createOrderView() {
        orderView = new VBox(20);
        orderView.setPadding(new Insets(30));
        orderView.setAlignment(Pos.TOP_CENTER);
        
        // Header
        Label titleLabel = new Label("📦 進行中訂單");
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
        
        orderStatusLabel = new Label("--");
        orderStatusLabel.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 24));
        orderStatusLabel.setTextFill(Color.web(Theme.PRIMARY));
        
        statusCard.getChildren().addAll(statusTitle, orderStatusLabel);
        
        // 乘客資訊
        VBox passengerCard = new VBox(12);
        passengerCard.setStyle("-fx-background-color: " + Theme.BG_CARD + "; -fx-background-radius: 12;");
        passengerCard.setPadding(new Insets(20));
        
        Label passengerTitle = new Label("👤 乘客資訊");
        passengerTitle.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 16));
        passengerTitle.setTextFill(Color.WHITE);
        
        passengerLabel = new Label("--");
        passengerLabel.setTextFill(Color.web(Theme.TEXT_SECONDARY));
        passengerLabel.setFont(Font.font("Microsoft JhengHei", 14));
        
        passengerCard.getChildren().addAll(passengerTitle, passengerLabel);
        
        // 路線資訊
        VBox routeCard = new VBox(12);
        routeCard.setStyle("-fx-background-color: " + Theme.BG_CARD + "; -fx-background-radius: 12;");
        routeCard.setPadding(new Insets(20));
        
        Label routeTitle = new Label("📍 路線資訊");
        routeTitle.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 16));
        routeTitle.setTextFill(Color.WHITE);
        
        routeLabel = new Label("上車: --\n下車: --");
        routeLabel.setTextFill(Color.WHITE);
        routeLabel.setFont(Font.font("Microsoft JhengHei", 14));
        
        routeCard.getChildren().addAll(routeTitle, routeLabel);
        
        // 車資
        VBox fareCard = new VBox(12);
        fareCard.setStyle("-fx-background-color: " + Theme.BG_CARD + "; -fx-background-radius: 12;");
        fareCard.setPadding(new Insets(20));
        fareCard.setAlignment(Pos.CENTER);
        
        Label fareTitleLabel = new Label("💰 預估車資");
        fareTitleLabel.setTextFill(Color.web(Theme.TEXT_SECONDARY));
        
        fareLabel = new Label("--");
        fareLabel.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 28));
        fareLabel.setTextFill(Color.web(Theme.SUCCESS));
        
        fareCard.getChildren().addAll(fareTitleLabel, fareLabel);
        
        // 操作按鈕
        actionBtn = new Button("🚗 開始行程");
        actionBtn.setMaxWidth(Double.MAX_VALUE);
        actionBtn.setStyle("""
            -fx-background-color: linear-gradient(to bottom, #4CAF50, #388E3C);
            -fx-text-fill: white;
            -fx-font-size: 18px;
            -fx-font-weight: bold;
            -fx-padding: 16 32;
            -fx-background-radius: 12;
            -fx-cursor: hand;
            """);
        actionBtn.setOnAction(e -> performAction());
        
        // 取消按鈕
        Button cancelBtn = new Button("❌ 取消訂單");
        cancelBtn.setMaxWidth(Double.MAX_VALUE);
        cancelBtn.setStyle("""
            -fx-background-color: linear-gradient(to bottom, #F44336, #D32F2F);
            -fx-text-fill: white;
            -fx-font-size: 14px;
            -fx-padding: 12 24;
            -fx-background-radius: 8;
            -fx-cursor: hand;
            """);
        cancelBtn.setOnAction(e -> cancelOrder());
        
        orderView.getChildren().addAll(
            titleLabel, statusCard, passengerCard, 
            routeCard, fareCard, actionBtn, cancelBtn
        );
    }
    
    private void showLoginView() {
        stopPolling();
        root.setCenter(loginView);
    }
    
    private void showMainView() {
        root.setCenter(mainView);
        startPolling();
    }
    
    private void showOrderView() {
        root.setCenter(orderView);
    }
    
    private void registerAndLogin() {
        String driverId = driverIdField.getText().trim();
        String name = nameField.getText().trim();
        String phone = phoneField.getText().trim();
        String plate = vehiclePlateField.getText().trim();
        VehicleType vehicleType = vehicleTypeCombo.getValue();
        
        if (driverId.isEmpty() || name.isEmpty() || phone.isEmpty() || plate.isEmpty()) {
            UIUtils.showError("錯誤", "請填寫所有欄位");
            return;
        }
        
        try {
            double x = Double.parseDouble(locationXField.getText());
            double y = Double.parseDouble(locationYField.getText());
            
            if (x < 0 || x > 100 || y < 0 || y > 100) {
                UIUtils.showError("錯誤", "座標必須在 0-100 範圍內");
                return;
            }
            
            Location location = new Location(x, y);
            
            // 先嘗試註冊，然後上線
            apiClient.registerDriver(driverId, name, phone, plate, vehicleType)
                .whenComplete((response, error) -> {
                    Platform.runLater(() -> {
                        if (error != null) {
                            // 可能已存在，嘗試直接上線
                            goOnline(driverId, location);
                        } else if (response.isSuccess()) {
                            currentDriver = response.getData();
                            goOnline(driverId, location);
                        } else {
                            // 可能已存在，嘗試直接上線
                            goOnline(driverId, location);
                        }
                    });
                });
                
        } catch (NumberFormatException e) {
            UIUtils.showError("錯誤", "請輸入有效的座標數值");
        }
    }
    
    private void goOnline(String driverId, Location location) {
        apiClient.goOnline(driverId, location)
            .whenComplete((response, error) -> {
                Platform.runLater(() -> {
                    if (error != null) {
                        UIUtils.showError("連線錯誤", "無法連接伺服器: " + error.getMessage());
                        return;
                    }
                    
                    if (response.isSuccess()) {
                        currentDriver = response.getData();
                        updateMainView();
                        showMainView();
                    } else {
                        UIUtils.showError("上線失敗", response.getErrorMessage());
                    }
                });
            });
    }
    
    private void toggleOnline() {
        if (currentDriver == null) return;
        
        if (onlineToggle.isSelected()) {
            // 上線
            Location location = currentDriver.getLocation();
            if (location == null) {
                location = new Location(25, 35);
            }
            
            apiClient.goOnline(currentDriver.getDriverId(), location)
                .whenComplete((response, error) -> {
                    Platform.runLater(() -> {
                        if (error == null && response.isSuccess()) {
                            currentDriver = response.getData();
                            updateMainView();
                        } else {
                            onlineToggle.setSelected(false);
                            UIUtils.showError("上線失敗", 
                                error != null ? error.getMessage() : response.getErrorMessage());
                        }
                    });
                });
        } else {
            // 下線
            apiClient.goOffline(currentDriver.getDriverId())
                .whenComplete((response, error) -> {
                    Platform.runLater(() -> {
                        if (error == null && response.isSuccess()) {
                            currentDriver = response.getData();
                            updateMainView();
                        } else {
                            onlineToggle.setSelected(true);
                        }
                    });
                });
        }
    }
    
    private void updateMainView() {
        if (currentDriver == null) return;
        
        DriverStatus status = currentDriver.getStatus();
        boolean isOnline = status == DriverStatus.ONLINE;
        
        statusLabel.setText(isOnline ? "🟢 上線中" : "⚫ 離線中");
        statusLabel.setTextFill(Color.web(isOnline ? Theme.SUCCESS : Theme.TEXT_SECONDARY));
        
        if (currentDriver.getLocation() != null) {
            locationLabel.setText("位置: " + currentDriver.getLocation());
        }
        
        onlineToggle.setSelected(isOnline);
        onlineToggle.setText(isOnline ? "下線" : "上線");
        String bgColor = isOnline ? "#4CAF50" : "#2A2A2A";
        String borderColor = isOnline ? "#4CAF50" : "#666666";
        onlineToggle.setStyle(
            "-fx-background-color: " + bgColor + ";" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 14px;" +
            "-fx-padding: 12 24;" +
            "-fx-background-radius: 20;" +
            "-fx-border-color: " + borderColor + ";" +
            "-fx-border-radius: 20;" +
            "-fx-border-width: 2;"
        );
    }
    
    @SuppressWarnings("unchecked")
    private void refreshOffers() {
        if (currentDriver == null || currentDriver.getStatus() != DriverStatus.ONLINE) {
            return;
        }
        
        if (currentDriver.isBusy()) {
            // 已有訂單，輪詢訂單狀態
            if (currentOrder != null) {
                apiClient.getOrder(currentOrder.getOrderId())
                    .whenComplete((response, error) -> {
                        Platform.runLater(() -> {
                            if (error == null && response.isSuccess()) {
                                currentOrder = response.getData();
                                updateOrderView();
                                
                                // 訂單完成或取消，返回主畫面
                                if (currentOrder.getStatus() == OrderStatus.COMPLETED ||
                                    currentOrder.getStatus() == OrderStatus.CANCELLED) {
                                    currentOrder = null;
                                    currentDriver.setBusy(false);
                                    showMainView();
                                }
                            }
                        });
                    });
            }
            return;
        }
        
        apiClient.getOffers(currentDriver.getDriverId())
            .whenComplete((response, error) -> {
                Platform.runLater(() -> {
                    if (error == null && response.isSuccess()) {
                        Map<String, Object> data = response.getData();
                        List<Map<String, Object>> offers = (List<Map<String, Object>>) data.get("offers");
                        updateOffersList(offers != null ? offers : new ArrayList<>());
                    }
                });
            });
    }
    
    private void updateOffersList(List<Map<String, Object>> offers) {
        offersListBox.getChildren().clear();
        
        if (offers.isEmpty()) {
            Label emptyLabel = new Label("📭 目前沒有可接的訂單\n請保持上線狀態等待派單");
            emptyLabel.setTextFill(Color.web(Theme.TEXT_SECONDARY));
            emptyLabel.setFont(Font.font("Microsoft JhengHei", 14));
            emptyLabel.setAlignment(Pos.CENTER);
            emptyLabel.setMaxWidth(Double.MAX_VALUE);
            emptyLabel.setStyle("-fx-padding: 40 20;");
            offersListBox.getChildren().add(emptyLabel);
            return;
        }
        
        for (Map<String, Object> offer : offers) {
            VBox card = createOfferCard(offer);
            offersListBox.getChildren().add(card);
        }
    }
    
    private VBox createOfferCard(Map<String, Object> offer) {
        VBox card = new VBox(12);
        card.setStyle("-fx-background-color: " + Theme.BG_CARD + "; -fx-background-radius: 12;");
        card.setPadding(new Insets(16));
        
        String orderId = (String) offer.get("orderId");
        
        // 訂單 ID
        Label idLabel = new Label("🆔 " + orderId);
        idLabel.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 12));
        idLabel.setTextFill(Color.web(Theme.TEXT_SECONDARY));
        
        // 路線
        Map<String, Object> pickup = (Map<String, Object>) offer.get("pickupLocation");
        Map<String, Object> dropoff = (Map<String, Object>) offer.get("dropoffLocation");
        
        String pickupStr = String.format("(%.0f, %.0f)", 
            ((Number) pickup.get("x")).doubleValue(), 
            ((Number) pickup.get("y")).doubleValue());
        String dropoffStr = String.format("(%.0f, %.0f)", 
            ((Number) dropoff.get("x")).doubleValue(), 
            ((Number) dropoff.get("y")).doubleValue());
        
        HBox routeBox = new HBox(8);
        routeBox.setAlignment(Pos.CENTER_LEFT);
        
        Label pickupLabel = new Label("📍 " + pickupStr);
        pickupLabel.setTextFill(Color.web(Theme.SUCCESS));
        
        Label arrow = new Label("→");
        arrow.setTextFill(Color.WHITE);
        
        Label dropoffLabel = new Label("🎯 " + dropoffStr);
        dropoffLabel.setTextFill(Color.web(Theme.ERROR));
        
        routeBox.getChildren().addAll(pickupLabel, arrow, dropoffLabel);
        
        // 距離和車資
        HBox infoBox = new HBox(16);
        infoBox.setAlignment(Pos.CENTER_LEFT);
        
        Object distanceObj = offer.get("distance");
        Object fareObj = offer.get("estimatedFare");
        
        double distance = distanceObj instanceof Number ? ((Number) distanceObj).doubleValue() : 0;
        double fare = fareObj instanceof Number ? ((Number) fareObj).doubleValue() : 0;
        
        Label distanceLabel = new Label(String.format("📏 %.1f km", distance));
        distanceLabel.setTextFill(Color.web(Theme.TEXT_SECONDARY));
        
        Label fareLabel = new Label(String.format("💰 $%.0f", fare));
        fareLabel.setTextFill(Color.web(Theme.SUCCESS));
        fareLabel.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 16));
        
        infoBox.getChildren().addAll(distanceLabel, fareLabel);
        
        // 接單按鈕
        Button acceptBtn = new Button("🚗 接單");
        acceptBtn.setMaxWidth(Double.MAX_VALUE);
        acceptBtn.setStyle("""
            -fx-background-color: linear-gradient(to bottom, #FF9800, #F57C00);
            -fx-text-fill: white;
            -fx-font-size: 14px;
            -fx-font-weight: bold;
            -fx-padding: 12 24;
            -fx-background-radius: 8;
            -fx-cursor: hand;
            """);
        acceptBtn.setOnAction(e -> acceptOrder(orderId));
        
        card.getChildren().addAll(idLabel, routeBox, infoBox, acceptBtn);
        return card;
    }
    
    private void acceptOrder(String orderId) {
        if (currentDriver == null) return;
        
        apiClient.acceptOrder(orderId, currentDriver.getDriverId())
            .whenComplete((response, error) -> {
                Platform.runLater(() -> {
                    if (error != null) {
                        UIUtils.showError("連線錯誤", error.getMessage());
                        return;
                    }
                    
                    if (response.isSuccess()) {
                        currentOrder = response.getData();
                        currentDriver.setBusy(true);
                        updateOrderView();
                        showOrderView();
                        UIUtils.showSuccess("接單成功！");
                    } else {
                        String errorCode = response.getErrorCode();
                        if ("ORDER_ALREADY_ACCEPTED".equals(errorCode)) {
                            UIUtils.showError("搶單失敗", "此訂單已被其他司機接受");
                        } else {
                            UIUtils.showError("接單失敗", response.getErrorMessage());
                        }
                        refreshOffers();
                    }
                });
            });
    }
    
    private void updateOrderView() {
        if (currentOrder == null) return;
        
        OrderStatus status = currentOrder.getStatus();
        
        orderStatusLabel.setText(status.getDisplayName());
        orderStatusLabel.setTextFill(Color.web(status.getColor()));
        
        passengerLabel.setText("乘客 ID: " + currentOrder.getPassengerId());
        
        routeLabel.setText(String.format("上車: %s\n下車: %s", 
            currentOrder.getPickupLocation(), 
            currentOrder.getDropoffLocation()));
        
        Double fare = currentOrder.getActualFare() != null ? 
            currentOrder.getActualFare() : currentOrder.getEstimatedFare();
        fareLabel.setText(fare != null ? String.format("$%.0f", fare) : "--");
        
        // 更新操作按鈕
        switch (status) {
            case ACCEPTED:
                actionBtn.setText("🚗 開始行程");
                actionBtn.setStyle("""
                    -fx-background-color: linear-gradient(to bottom, #4CAF50, #388E3C);
                    -fx-text-fill: white;
                    -fx-font-size: 18px;
                    -fx-font-weight: bold;
                    -fx-padding: 16 32;
                    -fx-background-radius: 12;
                    -fx-cursor: hand;
                    """);
                actionBtn.setDisable(false);
                break;
            case ONGOING:
                actionBtn.setText("✅ 完成行程");
                actionBtn.setStyle("""
                    -fx-background-color: linear-gradient(to bottom, #2196F3, #1976D2);
                    -fx-text-fill: white;
                    -fx-font-size: 18px;
                    -fx-font-weight: bold;
                    -fx-padding: 16 32;
                    -fx-background-radius: 12;
                    -fx-cursor: hand;
                    """);
                actionBtn.setDisable(false);
                break;
            case COMPLETED:
                actionBtn.setText("🎉 行程已完成");
                actionBtn.setDisable(true);
                break;
            case CANCELLED:
                actionBtn.setText("❌ 訂單已取消");
                actionBtn.setDisable(true);
                break;
            default:
                break;
        }
    }
    
    private void performAction() {
        if (currentOrder == null || currentDriver == null) return;
        
        OrderStatus status = currentOrder.getStatus();
        
        if (status == OrderStatus.ACCEPTED) {
            // 開始行程
            apiClient.startTrip(currentOrder.getOrderId(), currentDriver.getDriverId())
                .whenComplete((response, error) -> {
                    Platform.runLater(() -> {
                        if (error != null) {
                            UIUtils.showError("錯誤", error.getMessage());
                            return;
                        }
                        
                        if (response.isSuccess()) {
                            currentOrder = response.getData();
                            updateOrderView();
                        } else {
                            UIUtils.showError("操作失敗", response.getErrorMessage());
                        }
                    });
                });
        } else if (status == OrderStatus.ONGOING) {
            // 完成行程
            apiClient.completeTrip(currentOrder.getOrderId(), currentDriver.getDriverId())
                .whenComplete((response, error) -> {
                    Platform.runLater(() -> {
                        if (error != null) {
                            UIUtils.showError("錯誤", error.getMessage());
                            return;
                        }
                        
                        if (response.isSuccess()) {
                            currentOrder = response.getData();
                            updateOrderView();
                            
                            Double fare = currentOrder.getActualFare();
                            UIUtils.showSuccess(String.format("行程完成！\n車資: $%.0f", fare != null ? fare : 0));
                            
                            // 2 秒後返回主畫面
                            new Timeline(new KeyFrame(Duration.seconds(2), e -> {
                                currentOrder = null;
                                currentDriver.setBusy(false);
                                showMainView();
                            })).play();
                        } else {
                            UIUtils.showError("操作失敗", response.getErrorMessage());
                        }
                    });
                });
        }
    }
    
    private void cancelOrder() {
        if (currentOrder == null || currentDriver == null) return;
        
        UIUtils.showConfirm("確認取消", "確定要取消此訂單嗎？")
            .thenAccept(confirmed -> {
                if (confirmed) {
                    apiClient.cancelOrder(currentOrder.getOrderId(), currentDriver.getDriverId(), "司機取消")
                        .whenComplete((response, error) -> {
                            Platform.runLater(() -> {
                                if (error == null && response.isSuccess()) {
                                    currentOrder = null;
                                    currentDriver.setBusy(false);
                                    showMainView();
                                    UIUtils.showInfo("已取消", "訂單已取消");
                                } else {
                                    UIUtils.showError("取消失敗", 
                                        error != null ? error.getMessage() : response.getErrorMessage());
                                }
                            });
                        });
                }
            });
    }
    
    private void logout() {
        if (currentDriver != null && currentDriver.getStatus() == DriverStatus.ONLINE) {
            apiClient.goOffline(currentDriver.getDriverId());
        }
        
        currentDriver = null;
        currentOrder = null;
        showLoginView();
    }
    
    private void startPolling() {
        if (pollingTimeline != null) {
            pollingTimeline.stop();
        }
        
        pollingTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> refreshOffers()));
        pollingTimeline.setCycleCount(Timeline.INDEFINITE);
        pollingTimeline.play();
    }
    
    private void stopPolling() {
        if (pollingTimeline != null) {
            pollingTimeline.stop();
            pollingTimeline = null;
        }
    }
    
    public void shutdown() {
        stopPolling();
        if (currentDriver != null && currentDriver.getStatus() == DriverStatus.ONLINE) {
            apiClient.goOffline(currentDriver.getDriverId());
        }
    }
}
