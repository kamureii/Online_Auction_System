package com.auction.client;

import com.auction.client.navigation.SceneNavigator;
import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class MainApp extends Application {
    private static final String APP_ICON = "/assets/bidshift-taskbar-icon.png";

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Phần mềm Đấu giá Trực tuyến - Client");
        primaryStage.setResizable(true);
        applyAppIcon(primaryStage);
        SceneNavigator.init(primaryStage);
        SceneNavigator.showDashboard();
    }

    private void applyAppIcon(Stage primaryStage) {
        var iconUrl = MainApp.class.getResource(APP_ICON);
        if (iconUrl != null) {
            primaryStage.getIcons().setAll(new Image(iconUrl.toExternalForm()));
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
