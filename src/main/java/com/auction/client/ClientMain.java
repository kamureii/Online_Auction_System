package com.auction.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ClientMain extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/views/Login.fxml"));

        primaryStage.setTitle("Hệ thống Đấu giá");
        //phóng to toàn màn hình
        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.setResizable(true);
        primaryStage.setMaximized(true);

        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}