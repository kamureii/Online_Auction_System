package com.auction.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Trỏ đường dẫn tới file giao diện do Thành viên 1 thiết kế
        // Đảm bảo file Login.fxml nằm trong thư mục src/main/resources/views/
        URL fxmlLocation = getClass().getResource("/views/Login.fxml");
        if (fxmlLocation == null) {
            System.err.println("❌ LỖI: Không tìm thấy file Login.fxml trong resources/views!");
            return;
        }

        Parent root = FXMLLoader.load(fxmlLocation);
        primaryStage.setTitle("Phần mềm Đấu Giá Trực Tuyến - Client");
        primaryStage.setScene(new Scene(root));
        primaryStage.setResizable(false);
        primaryStage.sizeToScene();
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}