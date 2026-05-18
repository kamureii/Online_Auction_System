package com.auction.client.navigation;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.function.Consumer;

public final class SceneNavigator {
    private static Stage stage;
    private static boolean darkMode;

    private SceneNavigator() {}

    public static void init(Stage primaryStage) {
        stage = primaryStage;
    }

    public static Stage getStage() {
        if (stage == null) {
            throw new IllegalStateException("SceneNavigator has not been initialized.");
        }
        return stage;
    }

    public static void setDarkMode(boolean enabled) {
        darkMode = enabled;
        Scene scene = getStage().getScene();
        if (scene != null && scene.getRoot() != null) {
            applyTheme(scene.getRoot());
        }
    }

    public static boolean isDarkMode() {
        return darkMode;
    }

    public static void showLogin() {
        show("/views/Login.fxml", "Đăng nhập - BidShift", 680, 560, null);
    }

    public static void showRegister() {
        show("/views/Register.fxml", "Đăng ký - BidShift", 760, 660, null);
    }

    public static void showDashboard() {
        show("/views/Dashboard.fxml", "BidShift", 1240, 780, null);
    }

    public static <T> T show(String fxml, String title, double minWidth, double minHeight, Consumer<T> initializer) {
        try {
            FXMLLoader loader = new FXMLLoader(SceneNavigator.class.getResource(fxml));
            Parent root = loader.load();
            applyTheme(root);

            Stage currentStage = getStage();
            currentStage.setTitle(title);
            currentStage.setMinWidth(minWidth);
            currentStage.setMinHeight(minHeight);
            currentStage.setScene(new Scene(root));
            currentStage.sizeToScene();
            currentStage.centerOnScreen();
            currentStage.show();

            T controller = loader.getController();
            if (initializer != null && controller != null) {
                initializer.accept(controller);
            }
            return controller;
        } catch (IOException e) {
            throw new IllegalStateException("Không thể mở giao diện: " + fxml, e);
        }
    }

    private static void applyTheme(Parent root) {
        if (darkMode) {
            if (!root.getStyleClass().contains("theme-dark")) {
                root.getStyleClass().add("theme-dark");
            }
        } else {
            root.getStyleClass().remove("theme-dark");
        }
    }
}
