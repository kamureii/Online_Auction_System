package com.auction.client;

import com.auction.client.service.ServerConnector;
import com.auction.client.navigation.SceneNavigator;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Kết nối tới máy chủ
        boolean connected = ServerConnector.getInstance().connect();
        if (!connected) {
            System.err.println("Lỗi: Không thể kết nối tới máy chủ! Hãy chạy ServerMain trước.");
        }

        primaryStage.setResizable(true);
        SceneNavigator.init(primaryStage);
        SceneNavigator.showDashboard();

        // Ngắt kết nối khi đóng ứng dụng
        primaryStage.setOnCloseRequest(event -> {
            ServerConnector.getInstance().disconnect();
            Platform.exit();
            System.exit(0);
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
