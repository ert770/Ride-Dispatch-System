package com.uber.admin;

import com.uber.client.util.Theme;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * 管理後台應用程式入口
 */
public class AdminApp extends Application {
    
    private static final int WINDOW_WIDTH = 1200;
    private static final int WINDOW_HEIGHT = 800;
    
    @Override
    public void start(Stage primaryStage) {
        MainController controller = new MainController();
        Scene scene = new Scene(controller.getRoot(), WINDOW_WIDTH, WINDOW_HEIGHT);
        scene.getStylesheets().add("data:text/css," + Theme.getBaseStyles().replace("\n", " "));
        
        primaryStage.setTitle("📊 Uber 管理後台");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.setOnCloseRequest(e -> {
            controller.shutdown();
        });
        primaryStage.show();
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}
