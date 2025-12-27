package com.uber.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uber.client.api.ApiClient;
import com.uber.client.model.*;
import com.uber.client.util.Theme;
import com.uber.client.util.UIUtils;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 管理後台主控制器
 */
public class MainController {
    
    private final BorderPane root;
    private final ApiClient apiClient;
    private final ObjectMapper objectMapper;
    private Timeline pollingTimeline;
    
    // 側邊欄按鈕
    private Button ordersBtn;
    private Button driversBtn;
    private Button auditBtn;
    private Button settingsBtn;
    
    // 內容區域
    private StackPane contentPane;
    
    // 訂單頁面
    private VBox ordersPage;
    private TableView<OrderRow> ordersTable;
    private ObservableList<OrderRow> ordersData;
    private ComboBox<String> orderStatusFilter;
    
    // 司機頁面
    private VBox driversPage;
    private TableView<DriverRow> driversTable;
    private ObservableList<DriverRow> driversData;
    
    // 審計日誌頁面
    private VBox auditPage;
    private TableView<AuditRow> auditTable;
    private ObservableList<AuditRow> auditData;
    private TextField orderIdFilter;
    private ComboBox<String> actionFilter;
    
    // 設定頁面
    private VBox settingsPage;
    
    // 統計數據
    private Label totalOrdersLabel;
    private Label pendingOrdersLabel;
    private Label completedOrdersLabel;
    private Label onlineDriversLabel;
    
    public MainController() {
        this.apiClient = new ApiClient();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.root = new BorderPane();
        
        initUI();
        showOrdersPage();
        startPolling();
    }
    
    public BorderPane getRoot() {
        return root;
    }
    
    private void initUI() {
        root.setStyle("-fx-background-color: " + Theme.BG_DARK + ";");
        
        // 側邊欄
        VBox sidebar = createSidebar();
        root.setLeft(sidebar);
        
        // 頂部欄
        HBox topBar = createTopBar();
        root.setTop(topBar);
        
        // 內容區域
        contentPane = new StackPane();
        contentPane.setStyle("-fx-background-color: " + Theme.BG_DARK + ";");
        root.setCenter(contentPane);
        
        // 創建各頁面
        createOrdersPage();
        createDriversPage();
        createAuditPage();
        createSettingsPage();
    }
    
    private VBox createSidebar() {
        VBox sidebar = new VBox(8);
        sidebar.setStyle("-fx-background-color: #1A1A1A;");
        sidebar.setPadding(new Insets(20, 10, 20, 10));
        sidebar.setPrefWidth(200);
        
        // Logo
        Label logo = new Label("🚗 Uber Admin");
        logo.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 18));
        logo.setTextFill(Color.WHITE);
        logo.setPadding(new Insets(0, 0, 30, 10));
        
        // 導航按鈕
        ordersBtn = createNavButton("📋 訂單管理", true);
        ordersBtn.setOnAction(e -> showOrdersPage());
        
        driversBtn = createNavButton("🚗 司機管理", false);
        driversBtn.setOnAction(e -> showDriversPage());
        
        auditBtn = createNavButton("📝 審計日誌", false);
        auditBtn.setOnAction(e -> showAuditPage());
        
        settingsBtn = createNavButton("⚙️ 費率設定", false);
        settingsBtn.setOnAction(e -> showSettingsPage());
        
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        
        // 版本資訊
        Label versionLabel = new Label("v1.0.0");
        versionLabel.setTextFill(Color.web(Theme.TEXT_SECONDARY));
        versionLabel.setFont(Font.font("Microsoft JhengHei", 11));
        versionLabel.setPadding(new Insets(0, 0, 0, 10));
        
        sidebar.getChildren().addAll(logo, ordersBtn, driversBtn, auditBtn, settingsBtn, spacer, versionLabel);
        return sidebar;
    }
    
    private Button createNavButton(String text, boolean active) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setStyle(getNavButtonStyle(active));
        return btn;
    }
    
    private String getNavButtonStyle(boolean active) {
        String bgColor = active ? "#333333" : "transparent";
        String textColor = active ? "white" : "#B0B0B0";
        return "-fx-background-color: " + bgColor + ";" +
               "-fx-text-fill: " + textColor + ";" +
               "-fx-font-size: 14px;" +
               "-fx-padding: 12 16;" +
               "-fx-background-radius: 8;" +
               "-fx-cursor: hand;";
    }
    
    private void setActiveNav(Button activeBtn) {
        ordersBtn.setStyle(getNavButtonStyle(activeBtn == ordersBtn));
        driversBtn.setStyle(getNavButtonStyle(activeBtn == driversBtn));
        auditBtn.setStyle(getNavButtonStyle(activeBtn == auditBtn));
        settingsBtn.setStyle(getNavButtonStyle(activeBtn == settingsBtn));
    }
    
    private HBox createTopBar() {
        HBox topBar = new HBox(20);
        topBar.setStyle("-fx-background-color: #1A1A1A; -fx-border-color: #333333; -fx-border-width: 0 0 1 0;");
        topBar.setPadding(new Insets(15, 25, 15, 25));
        topBar.setAlignment(Pos.CENTER_LEFT);
        
        // 統計卡片
        totalOrdersLabel = createStatLabel("總訂單", "0", Theme.PRIMARY);
        pendingOrdersLabel = createStatLabel("待處理", "0", Theme.WARNING);
        completedOrdersLabel = createStatLabel("已完成", "0", Theme.SUCCESS);
        onlineDriversLabel = createStatLabel("上線司機", "0", "#9C27B0");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // 刷新按鈕
        Button refreshBtn = new Button("🔄 刷新");
        refreshBtn.setStyle("""
            -fx-background-color: #2A2A2A;
            -fx-text-fill: white;
            -fx-padding: 8 16;
            -fx-background-radius: 6;
            -fx-cursor: hand;
            """);
        refreshBtn.setOnAction(e -> refreshCurrentPage());
        
        topBar.getChildren().addAll(
            totalOrdersLabel, pendingOrdersLabel, completedOrdersLabel, 
            onlineDriversLabel, spacer, refreshBtn
        );
        return topBar;
    }
    
    private Label createStatLabel(String title, String value, String color) {
        VBox box = new VBox(2);
        box.setStyle("-fx-background-color: " + Theme.BG_CARD + "; -fx-background-radius: 8; -fx-padding: 12 20;");
        
        Label titleLabel = new Label(title);
        titleLabel.setTextFill(Color.web(Theme.TEXT_SECONDARY));
        titleLabel.setFont(Font.font("Microsoft JhengHei", 11));
        
        Label valueLabel = new Label(value);
        valueLabel.setTextFill(Color.web(color));
        valueLabel.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 20));
        
        box.getChildren().addAll(titleLabel, valueLabel);
        
        // 返回一個包含 VBox 的 Label 作為容器（簡化處理）
        // 實際上我們將返回值存在 userData 中
        valueLabel.setUserData(box);
        return valueLabel;
    }
    
    @SuppressWarnings("unchecked")
    private void createOrdersPage() {
        ordersPage = new VBox(20);
        ordersPage.setPadding(new Insets(25));
        
        // 標題和過濾器
        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);
        
        Label titleLabel = new Label("📋 訂單管理");
        titleLabel.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 24));
        titleLabel.setTextFill(Color.WHITE);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Label filterLabel = new Label("狀態:");
        filterLabel.setTextFill(Color.web(Theme.TEXT_SECONDARY));
        
        orderStatusFilter = new ComboBox<>();
        orderStatusFilter.getItems().addAll("全部", "PENDING", "ACCEPTED", "ONGOING", "COMPLETED", "CANCELLED");
        orderStatusFilter.setValue("全部");
        orderStatusFilter.setStyle("""
            -fx-background-color: #2A2A2A;
            -fx-border-color: #444444;
            -fx-border-radius: 6;
            -fx-background-radius: 6;
            """);
        orderStatusFilter.setOnAction(e -> loadOrders());
        
        header.getChildren().addAll(titleLabel, spacer, filterLabel, orderStatusFilter);
        
        // 表格
        ordersData = FXCollections.observableArrayList();
        ordersTable = new TableView<>(ordersData);
        ordersTable.setStyle("-fx-background-color: " + Theme.BG_CARD + ";");
        
        TableColumn<OrderRow, String> idCol = new TableColumn<>("訂單 ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("orderId"));
        idCol.setPrefWidth(150);
        
        TableColumn<OrderRow, String> passengerCol = new TableColumn<>("乘客");
        passengerCol.setCellValueFactory(new PropertyValueFactory<>("passengerId"));
        passengerCol.setPrefWidth(120);
        
        TableColumn<OrderRow, String> driverCol = new TableColumn<>("司機");
        driverCol.setCellValueFactory(new PropertyValueFactory<>("driverId"));
        driverCol.setPrefWidth(120);
        
        TableColumn<OrderRow, String> statusCol = new TableColumn<>("狀態");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(100);
        statusCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    String color = switch (item) {
                        case "PENDING" -> Theme.WARNING;
                        case "ACCEPTED" -> Theme.INFO;
                        case "ONGOING" -> Theme.SUCCESS;
                        case "COMPLETED" -> Theme.TEXT_SECONDARY;
                        case "CANCELLED" -> Theme.ERROR;
                        default -> Theme.TEXT_PRIMARY;
                    };
                    setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
                }
            }
        });
        
        TableColumn<OrderRow, String> vehicleCol = new TableColumn<>("車種");
        vehicleCol.setCellValueFactory(new PropertyValueFactory<>("vehicleType"));
        vehicleCol.setPrefWidth(80);
        
        TableColumn<OrderRow, String> fareCol = new TableColumn<>("車資");
        fareCol.setCellValueFactory(new PropertyValueFactory<>("fare"));
        fareCol.setPrefWidth(100);
        
        TableColumn<OrderRow, String> createdCol = new TableColumn<>("建立時間");
        createdCol.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        createdCol.setPrefWidth(160);
        
        ordersTable.getColumns().addAll(idCol, passengerCol, driverCol, statusCol, vehicleCol, fareCol, createdCol);
        VBox.setVgrow(ordersTable, Priority.ALWAYS);
        
        ordersPage.getChildren().addAll(header, ordersTable);
    }
    
    private void createDriversPage() {
        driversPage = new VBox(20);
        driversPage.setPadding(new Insets(25));
        
        // 標題
        Label titleLabel = new Label("🚗 司機管理");
        titleLabel.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 24));
        titleLabel.setTextFill(Color.WHITE);
        
        // 表格
        driversData = FXCollections.observableArrayList();
        driversTable = new TableView<>(driversData);
        driversTable.setStyle("-fx-background-color: " + Theme.BG_CARD + ";");
        
        TableColumn<DriverRow, String> idCol = new TableColumn<>("司機 ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("driverId"));
        idCol.setPrefWidth(150);
        
        TableColumn<DriverRow, String> nameCol = new TableColumn<>("姓名");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(120);
        
        TableColumn<DriverRow, String> phoneCol = new TableColumn<>("電話");
        phoneCol.setCellValueFactory(new PropertyValueFactory<>("phone"));
        phoneCol.setPrefWidth(130);
        
        TableColumn<DriverRow, String> plateCol = new TableColumn<>("車牌");
        plateCol.setCellValueFactory(new PropertyValueFactory<>("vehiclePlate"));
        plateCol.setPrefWidth(100);
        
        TableColumn<DriverRow, String> vehicleCol = new TableColumn<>("車種");
        vehicleCol.setCellValueFactory(new PropertyValueFactory<>("vehicleType"));
        vehicleCol.setPrefWidth(80);
        
        TableColumn<DriverRow, String> statusCol = new TableColumn<>("狀態");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(80);
        statusCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    String color = "ONLINE".equals(item) ? Theme.SUCCESS : Theme.TEXT_SECONDARY;
                    setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
                }
            }
        });
        
        TableColumn<DriverRow, String> busyCol = new TableColumn<>("忙碌");
        busyCol.setCellValueFactory(new PropertyValueFactory<>("busy"));
        busyCol.setPrefWidth(60);
        
        TableColumn<DriverRow, String> locationCol = new TableColumn<>("位置");
        locationCol.setCellValueFactory(new PropertyValueFactory<>("location"));
        locationCol.setPrefWidth(100);
        
        driversTable.getColumns().addAll(idCol, nameCol, phoneCol, plateCol, vehicleCol, statusCol, busyCol, locationCol);
        VBox.setVgrow(driversTable, Priority.ALWAYS);
        
        driversPage.getChildren().addAll(titleLabel, driversTable);
    }
    
    private void createAuditPage() {
        auditPage = new VBox(20);
        auditPage.setPadding(new Insets(25));
        
        // 標題和過濾器
        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);
        
        Label titleLabel = new Label("📝 審計日誌");
        titleLabel.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 24));
        titleLabel.setTextFill(Color.WHITE);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Label orderIdLabel = new Label("訂單 ID:");
        orderIdLabel.setTextFill(Color.web(Theme.TEXT_SECONDARY));
        
        orderIdFilter = new TextField();
        orderIdFilter.setPromptText("輸入訂單 ID");
        orderIdFilter.setPrefWidth(150);
        orderIdFilter.setStyle("""
            -fx-background-color: #2A2A2A;
            -fx-text-fill: white;
            -fx-border-color: #444444;
            -fx-border-radius: 6;
            -fx-background-radius: 6;
            -fx-padding: 6 10;
            """);
        
        Label actionLabel = new Label("操作:");
        actionLabel.setTextFill(Color.web(Theme.TEXT_SECONDARY));
        
        actionFilter = new ComboBox<>();
        actionFilter.getItems().addAll("全部", "CREATE", "ACCEPT", "START", "COMPLETE", "CANCEL");
        actionFilter.setValue("全部");
        actionFilter.setStyle("""
            -fx-background-color: #2A2A2A;
            -fx-border-color: #444444;
            -fx-border-radius: 6;
            -fx-background-radius: 6;
            """);
        
        Button searchBtn = new Button("🔍 搜尋");
        searchBtn.setStyle("""
            -fx-background-color: #1976D2;
            -fx-text-fill: white;
            -fx-padding: 6 16;
            -fx-background-radius: 6;
            -fx-cursor: hand;
            """);
        searchBtn.setOnAction(e -> loadAuditLogs());
        
        header.getChildren().addAll(titleLabel, spacer, orderIdLabel, orderIdFilter, actionLabel, actionFilter, searchBtn);
        
        // 表格
        auditData = FXCollections.observableArrayList();
        auditTable = new TableView<>(auditData);
        auditTable.setStyle("-fx-background-color: " + Theme.BG_CARD + ";");
        
        TableColumn<AuditRow, String> timeCol = new TableColumn<>("時間");
        timeCol.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        timeCol.setPrefWidth(160);
        
        TableColumn<AuditRow, String> orderIdCol = new TableColumn<>("訂單 ID");
        orderIdCol.setCellValueFactory(new PropertyValueFactory<>("orderId"));
        orderIdCol.setPrefWidth(150);
        
        TableColumn<AuditRow, String> actionCol = new TableColumn<>("操作");
        actionCol.setCellValueFactory(new PropertyValueFactory<>("action"));
        actionCol.setPrefWidth(100);
        
        TableColumn<AuditRow, String> actorCol = new TableColumn<>("執行者");
        actorCol.setCellValueFactory(new PropertyValueFactory<>("actor"));
        actorCol.setPrefWidth(150);
        
        TableColumn<AuditRow, String> prevCol = new TableColumn<>("原狀態");
        prevCol.setCellValueFactory(new PropertyValueFactory<>("previousState"));
        prevCol.setPrefWidth(100);
        
        TableColumn<AuditRow, String> newCol = new TableColumn<>("新狀態");
        newCol.setCellValueFactory(new PropertyValueFactory<>("newState"));
        newCol.setPrefWidth(100);
        
        TableColumn<AuditRow, String> successCol = new TableColumn<>("成功");
        successCol.setCellValueFactory(new PropertyValueFactory<>("success"));
        successCol.setPrefWidth(60);
        successCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    String color = "✅".equals(item) ? Theme.SUCCESS : Theme.ERROR;
                    setStyle("-fx-text-fill: " + color + ";");
                }
            }
        });
        
        TableColumn<AuditRow, String> reasonCol = new TableColumn<>("失敗原因");
        reasonCol.setCellValueFactory(new PropertyValueFactory<>("failureReason"));
        reasonCol.setPrefWidth(150);
        
        auditTable.getColumns().addAll(timeCol, orderIdCol, actionCol, actorCol, prevCol, newCol, successCol, reasonCol);
        VBox.setVgrow(auditTable, Priority.ALWAYS);
        
        auditPage.getChildren().addAll(header, auditTable);
    }
    
    private void createSettingsPage() {
        settingsPage = new VBox(20);
        settingsPage.setPadding(new Insets(25));
        
        Label titleLabel = new Label("⚙️ 費率設定");
        titleLabel.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 24));
        titleLabel.setTextFill(Color.WHITE);
        
        // 費率卡片
        HBox cardsBox = new HBox(20);
        cardsBox.setAlignment(Pos.TOP_LEFT);
        
        cardsBox.getChildren().addAll(
            createRateCard(VehicleType.STANDARD, "$50", "$15/km", "$3/min", "$70"),
            createRateCard(VehicleType.PREMIUM, "$80", "$25/km", "$5/min", "$120"),
            createRateCard(VehicleType.XL, "$100", "$30/km", "$6/min", "$150")
        );
        
        // 說明
        Label noteLabel = new Label("💡 費率變更將在下一筆新訂單生效");
        noteLabel.setTextFill(Color.web(Theme.TEXT_SECONDARY));
        noteLabel.setFont(Font.font("Microsoft JhengHei", 14));
        noteLabel.setPadding(new Insets(20, 0, 0, 0));
        
        settingsPage.getChildren().addAll(titleLabel, cardsBox, noteLabel);
    }
    
    private VBox createRateCard(VehicleType type, String base, String perKm, String perMin, String min) {
        VBox card = new VBox(16);
        card.setStyle("-fx-background-color: " + Theme.BG_CARD + "; -fx-background-radius: 12;");
        card.setPadding(new Insets(24));
        card.setPrefWidth(280);
        
        // 標題
        String emoji = switch (type) {
            case STANDARD -> "🚗";
            case PREMIUM -> "🚘";
            case XL -> "🚐";
        };
        
        Label titleLabel = new Label(emoji + " " + type.getDisplayName());
        titleLabel.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 20));
        titleLabel.setTextFill(Color.WHITE);
        
        // 費率項目
        VBox ratesBox = new VBox(12);
        ratesBox.getChildren().addAll(
            createRateRow("基本車資", base),
            createRateRow("每公里", perKm),
            createRateRow("每分鐘", perMin),
            createRateRow("最低車資", min)
        );
        
        // 編輯按鈕
        Button editBtn = new Button("✏️ 編輯");
        editBtn.setMaxWidth(Double.MAX_VALUE);
        editBtn.setStyle("""
            -fx-background-color: #2A2A2A;
            -fx-border-color: #1976D2;
            -fx-border-width: 1;
            -fx-text-fill: #1976D2;
            -fx-padding: 10 20;
            -fx-background-radius: 8;
            -fx-border-radius: 8;
            -fx-cursor: hand;
            """);
        editBtn.setOnAction(e -> UIUtils.showInfo("提示", "費率編輯功能尚未實作"));
        
        card.getChildren().addAll(titleLabel, ratesBox, editBtn);
        return card;
    }
    
    private HBox createRateRow(String label, String value) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        
        Label labelText = new Label(label);
        labelText.setTextFill(Color.web(Theme.TEXT_SECONDARY));
        labelText.setFont(Font.font("Microsoft JhengHei", 14));
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Label valueText = new Label(value);
        valueText.setTextFill(Color.web(Theme.SUCCESS));
        valueText.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 16));
        
        row.getChildren().addAll(labelText, spacer, valueText);
        return row;
    }
    
    private void showOrdersPage() {
        setActiveNav(ordersBtn);
        contentPane.getChildren().clear();
        contentPane.getChildren().add(ordersPage);
        loadOrders();
    }
    
    private void showDriversPage() {
        setActiveNav(driversBtn);
        contentPane.getChildren().clear();
        contentPane.getChildren().add(driversPage);
        loadDrivers();
    }
    
    private void showAuditPage() {
        setActiveNav(auditBtn);
        contentPane.getChildren().clear();
        contentPane.getChildren().add(auditPage);
        loadAuditLogs();
    }
    
    private void showSettingsPage() {
        setActiveNav(settingsBtn);
        contentPane.getChildren().clear();
        contentPane.getChildren().add(settingsPage);
        loadRatePlans();
    }
    
    private void refreshCurrentPage() {
        if (contentPane.getChildren().contains(ordersPage)) {
            loadOrders();
        } else if (contentPane.getChildren().contains(driversPage)) {
            loadDrivers();
        } else if (contentPane.getChildren().contains(auditPage)) {
            loadAuditLogs();
        } else if (contentPane.getChildren().contains(settingsPage)) {
            loadRatePlans();
        }
    }
    
    @SuppressWarnings("unchecked")
    private void loadOrders() {
        String status = orderStatusFilter.getValue();
        String statusParam = "全部".equals(status) ? null : status;
        
        apiClient.getAllOrders(statusParam, 0, 100)
            .whenComplete((response, error) -> {
                Platform.runLater(() -> {
                    if (error != null) {
                        return;
                    }
                    
                    if (response.isSuccess()) {
                        Map<String, Object> data = response.getData();
                        List<Map<String, Object>> orders = (List<Map<String, Object>>) data.get("orders");
                        
                        ordersData.clear();
                        int pending = 0, completed = 0;
                        
                        if (orders != null) {
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                .withZone(ZoneId.systemDefault());
                            
                            for (Map<String, Object> order : orders) {
                                String orderStatus = (String) order.get("status");
                                if ("PENDING".equals(orderStatus)) pending++;
                                if ("COMPLETED".equals(orderStatus)) completed++;
                                
                                Object fareObj = order.get("fare");
                                if (fareObj == null) fareObj = order.get("estimatedFare");
                                String fareStr = fareObj != null ? 
                                    String.format("$%.0f", ((Number) fareObj).doubleValue()) : "--";
                                
                                String createdAt = "--";
                                Object createdAtObj = order.get("createdAt");
                                if (createdAtObj != null) {
                                    try {
                                        Instant instant = Instant.parse(createdAtObj.toString());
                                        createdAt = formatter.format(instant);
                                    } catch (Exception e) {
                                        createdAt = createdAtObj.toString();
                                    }
                                }
                                
                                ordersData.add(new OrderRow(
                                    (String) order.get("orderId"),
                                    (String) order.get("passengerId"),
                                    order.get("driverId") != null ? (String) order.get("driverId") : "--",
                                    orderStatus,
                                    order.get("vehicleType") != null ? (String) order.get("vehicleType") : "--",
                                    fareStr,
                                    createdAt
                                ));
                            }
                        }
                        
                        // 更新統計
                        totalOrdersLabel.setText(String.valueOf(orders != null ? orders.size() : 0));
                        pendingOrdersLabel.setText(String.valueOf(pending));
                        completedOrdersLabel.setText(String.valueOf(completed));
                    }
                });
            });
    }
    
    @SuppressWarnings("unchecked")
    private void loadDrivers() {
        apiClient.getAllDrivers()
            .whenComplete((response, error) -> {
                Platform.runLater(() -> {
                    if (error != null) {
                        return;
                    }
                    
                    if (response.isSuccess()) {
                        Map<String, Object> data = response.getData();
                        List<Map<String, Object>> drivers = (List<Map<String, Object>>) data.get("drivers");
                        
                        driversData.clear();
                        int online = 0;
                        
                        if (drivers != null) {
                            for (Map<String, Object> driver : drivers) {
                                String status = (String) driver.get("status");
                                if ("ONLINE".equals(status)) online++;
                                
                                Map<String, Object> location = (Map<String, Object>) driver.get("location");
                                String locationStr = location != null ?
                                    String.format("(%.0f, %.0f)", 
                                        ((Number) location.get("x")).doubleValue(),
                                        ((Number) location.get("y")).doubleValue()) : "--";
                                
                                Object busyObj = driver.get("busy");
                                boolean busy = busyObj instanceof Boolean ? (Boolean) busyObj : false;
                                
                                driversData.add(new DriverRow(
                                    (String) driver.get("driverId"),
                                    driver.get("name") != null ? (String) driver.get("name") : "--",
                                    driver.get("phone") != null ? (String) driver.get("phone") : "--",
                                    driver.get("vehiclePlate") != null ? (String) driver.get("vehiclePlate") : "--",
                                    driver.get("vehicleType") != null ? (String) driver.get("vehicleType") : "--",
                                    status,
                                    busy ? "是" : "否",
                                    locationStr
                                ));
                            }
                        }
                        
                        onlineDriversLabel.setText(String.valueOf(online));
                    }
                });
            });
    }
    
    @SuppressWarnings("unchecked")
    private void loadAuditLogs() {
        String orderId = orderIdFilter.getText().trim();
        String action = actionFilter.getValue();
        
        String orderIdParam = orderId.isEmpty() ? null : orderId;
        String actionParam = "全部".equals(action) ? null : action;
        
        apiClient.getAuditLogs(orderIdParam, actionParam)
            .whenComplete((response, error) -> {
                Platform.runLater(() -> {
                    if (error != null) {
                        return;
                    }
                    
                    if (response.isSuccess()) {
                        Map<String, Object> data = response.getData();
                        List<Map<String, Object>> logs = (List<Map<String, Object>>) data.get("logs");
                        
                        auditData.clear();
                        
                        if (logs != null) {
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                .withZone(ZoneId.systemDefault());
                            
                            for (Map<String, Object> log : logs) {
                                String timestamp = "--";
                                Object timestampObj = log.get("timestamp");
                                if (timestampObj != null) {
                                    try {
                                        Instant instant = Instant.parse(timestampObj.toString());
                                        timestamp = formatter.format(instant);
                                    } catch (Exception e) {
                                        timestamp = timestampObj.toString();
                                    }
                                }
                                
                                String actor = String.format("%s (%s)", 
                                    log.get("actorId") != null ? log.get("actorId") : "--",
                                    log.get("actorType") != null ? log.get("actorType") : "--");
                                
                                Object successObj = log.get("success");
                                boolean success = successObj instanceof Boolean ? (Boolean) successObj : false;
                                
                                auditData.add(new AuditRow(
                                    timestamp,
                                    (String) log.get("orderId"),
                                    (String) log.get("action"),
                                    actor,
                                    log.get("previousState") != null ? (String) log.get("previousState") : "--",
                                    log.get("newState") != null ? (String) log.get("newState") : "--",
                                    success ? "✅" : "❌",
                                    log.get("failureReason") != null ? (String) log.get("failureReason") : ""
                                ));
                            }
                        }
                    }
                });
            });
    }
    
    private void loadRatePlans() {
        // 費率從後端載入（目前使用預設值）
    }
    
    private void startPolling() {
        pollingTimeline = new Timeline(new KeyFrame(Duration.seconds(2), e -> {
            if (contentPane.getChildren().contains(ordersPage)) {
                loadOrders();
            }
            loadDrivers(); // 用於更新統計數據
        }));
        pollingTimeline.setCycleCount(Timeline.INDEFINITE);
        pollingTimeline.play();
    }
    
    public void shutdown() {
        if (pollingTimeline != null) {
            pollingTimeline.stop();
        }
    }
    
    // ============ Data Classes ============
    
    public static class OrderRow {
        private final SimpleStringProperty orderId;
        private final SimpleStringProperty passengerId;
        private final SimpleStringProperty driverId;
        private final SimpleStringProperty status;
        private final SimpleStringProperty vehicleType;
        private final SimpleStringProperty fare;
        private final SimpleStringProperty createdAt;
        
        public OrderRow(String orderId, String passengerId, String driverId, 
                       String status, String vehicleType, String fare, String createdAt) {
            this.orderId = new SimpleStringProperty(orderId);
            this.passengerId = new SimpleStringProperty(passengerId);
            this.driverId = new SimpleStringProperty(driverId);
            this.status = new SimpleStringProperty(status);
            this.vehicleType = new SimpleStringProperty(vehicleType);
            this.fare = new SimpleStringProperty(fare);
            this.createdAt = new SimpleStringProperty(createdAt);
        }
        
        public String getOrderId() { return orderId.get(); }
        public String getPassengerId() { return passengerId.get(); }
        public String getDriverId() { return driverId.get(); }
        public String getStatus() { return status.get(); }
        public String getVehicleType() { return vehicleType.get(); }
        public String getFare() { return fare.get(); }
        public String getCreatedAt() { return createdAt.get(); }
    }
    
    public static class DriverRow {
        private final SimpleStringProperty driverId;
        private final SimpleStringProperty name;
        private final SimpleStringProperty phone;
        private final SimpleStringProperty vehiclePlate;
        private final SimpleStringProperty vehicleType;
        private final SimpleStringProperty status;
        private final SimpleStringProperty busy;
        private final SimpleStringProperty location;
        
        public DriverRow(String driverId, String name, String phone, String vehiclePlate,
                        String vehicleType, String status, String busy, String location) {
            this.driverId = new SimpleStringProperty(driverId);
            this.name = new SimpleStringProperty(name);
            this.phone = new SimpleStringProperty(phone);
            this.vehiclePlate = new SimpleStringProperty(vehiclePlate);
            this.vehicleType = new SimpleStringProperty(vehicleType);
            this.status = new SimpleStringProperty(status);
            this.busy = new SimpleStringProperty(busy);
            this.location = new SimpleStringProperty(location);
        }
        
        public String getDriverId() { return driverId.get(); }
        public String getName() { return name.get(); }
        public String getPhone() { return phone.get(); }
        public String getVehiclePlate() { return vehiclePlate.get(); }
        public String getVehicleType() { return vehicleType.get(); }
        public String getStatus() { return status.get(); }
        public String getBusy() { return busy.get(); }
        public String getLocation() { return location.get(); }
    }
    
    public static class AuditRow {
        private final SimpleStringProperty timestamp;
        private final SimpleStringProperty orderId;
        private final SimpleStringProperty action;
        private final SimpleStringProperty actor;
        private final SimpleStringProperty previousState;
        private final SimpleStringProperty newState;
        private final SimpleStringProperty success;
        private final SimpleStringProperty failureReason;
        
        public AuditRow(String timestamp, String orderId, String action, String actor,
                       String previousState, String newState, String success, String failureReason) {
            this.timestamp = new SimpleStringProperty(timestamp);
            this.orderId = new SimpleStringProperty(orderId);
            this.action = new SimpleStringProperty(action);
            this.actor = new SimpleStringProperty(actor);
            this.previousState = new SimpleStringProperty(previousState);
            this.newState = new SimpleStringProperty(newState);
            this.success = new SimpleStringProperty(success);
            this.failureReason = new SimpleStringProperty(failureReason);
        }
        
        public String getTimestamp() { return timestamp.get(); }
        public String getOrderId() { return orderId.get(); }
        public String getAction() { return action.get(); }
        public String getActor() { return actor.get(); }
        public String getPreviousState() { return previousState.get(); }
        public String getNewState() { return newState.get(); }
        public String getSuccess() { return success.get(); }
        public String getFailureReason() { return failureReason.get(); }
    }
}
