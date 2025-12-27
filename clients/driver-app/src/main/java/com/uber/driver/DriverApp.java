package com.uber.driver;

import com.uber.client.util.Theme;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * 司機端應用程式入口
 */
public class DriverApp extends Application {
    
    private static final int WINDOW_WIDTH = 420;
    private static final int WINDOW_HEIGHT = 750;
    
    @Override
    public void start(Stage primaryStage) {
        MainController controller = new MainController();
        Scene scene = new Scene(controller.getRoot(), WINDOW_WIDTH, WINDOW_HEIGHT);
        scene.getStylesheets().add("data:text/css," + Theme.getBaseStyles().replace("\n", " "));
        
        primaryStage.setTitle("🚗 Uber 司機端");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.setOnCloseRequest(e -> {
            controller.shutdown();
        });
        primaryStage.show();
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}
