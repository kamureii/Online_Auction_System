package com.auction.client.navigation;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.prefs.Preferences;
import java.util.function.Consumer;

public final class SceneNavigator {
    private static final String THEME_PREF_KEY = "bidshift.theme";
    private static final String THEME_LIGHT = "light";
    private static final String THEME_DARK = "dark";
    private static final String DASHBOARD_LIGHT_FXML = "/views/DashboardLight.fxml";
    private static final String DASHBOARD_DARK_FXML = "/views/DashboardDark.fxml";
    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(SceneNavigator.class);

    private static Stage stage;
    private static boolean darkMode;

    private SceneNavigator() {}

    public static void init(Stage primaryStage) {
        stage = primaryStage;
        darkMode = loadInitialDarkMode();
    }

    public static Stage getStage() {
        if (stage == null) {
            throw new IllegalStateException("SceneNavigator has not been initialized.");
        }
        return stage;
    }

    public static void setDarkMode(boolean enabled) {
        setDarkMode(enabled, true);
    }

    public static boolean toggleTheme() {
        setDarkMode(!darkMode);
        return darkMode;
    }

    private static void setDarkMode(boolean enabled, boolean persist) {
        darkMode = enabled;
        if (persist) {
            PREFERENCES.put(THEME_PREF_KEY, enabled ? THEME_DARK : THEME_LIGHT);
        }
        if (stage != null) {
            Scene scene = stage.getScene();
            if (scene != null && scene.getRoot() != null) {
                applyTheme(scene.getRoot());
            }
        }
    }

    public static boolean isDarkMode() {
        return darkMode;
    }

    public static void showLogin() {
        show("/views/Login.fxml", "Đăng nhập - Đấu Giá Trực Tuyến", 680, 560, null);
    }

    public static void showRegister() {
        show("/views/Register.fxml", "Đăng ký - Đấu Giá Trực Tuyến", 760, 720, null);
    }

    public static void showDashboard() {
        show(darkMode ? DASHBOARD_DARK_FXML : DASHBOARD_LIGHT_FXML, "Sàn Đấu Giá Trực Tuyến", 1180, 760, null);
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
        root.getStyleClass().removeAll(THEME_LIGHT, THEME_DARK);
        root.getStyleClass().add(darkMode ? THEME_DARK : THEME_LIGHT);
    }

    private static boolean loadInitialDarkMode() {
        String savedTheme = PREFERENCES.get(THEME_PREF_KEY, "");
        if (THEME_DARK.equals(savedTheme)) {
            return true;
        }
        if (THEME_LIGHT.equals(savedTheme)) {
            return false;
        }
        return systemPrefersDark();
    }

    private static boolean systemPrefersDark() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (!osName.contains("win")) {
            return false;
        }
        try {
            Process process = new ProcessBuilder("reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                    "/v", "AppsUseLightTheme")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor();
            return output.contains("0x0");
        } catch (Exception ignored) {
            return false;
        }
    }
}
