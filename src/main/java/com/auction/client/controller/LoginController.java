package com.auction.client.controller;

import com.auction.client.navigation.SceneNavigator;
import com.auction.client.service.ServerConnector;
import com.auction.shared.network.Response;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.event.ActionEvent;

public class LoginController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label messageLabel;

    @FXML
    private void goToRegister() {
        SceneNavigator.showRegister();
    }

    @FXML
    private void handleBackHome() {
        SceneNavigator.showDashboard();
    }

    @FXML
    private void handleLogin(ActionEvent event) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Vui lòng nhập đầy đủ thông tin!");
            return;
        }

        // Đảm bảo kết nối tới máy chủ
        ServerConnector connector = ServerConnector.getInstance();
        if (!connector.isConnected()) {
            if (!connector.connect()) {
                showError("Không thể kết nối tới máy chủ! Hãy kiểm tra máy chủ.");
                return;
            }
        }

        Response res = connector.login(username, password);

        if (res != null && "SUCCESS".equals(res.getStatus())) {
            messageLabel.setTextFill(Color.GREEN);
            messageLabel.setText("Đăng nhập thành công!");

            try {
                SceneNavigator.showDashboard();
            } catch (Exception e) {
                e.printStackTrace();
                showError("Đăng nhập thành công nhưng không mở được trang chính.");
            }
        } else {
            showError(res != null ? res.getMessage() : "Không thể kết nối tới máy chủ!");
        }
    }

    private void showError(String message) {
        messageLabel.setTextFill(Color.RED);
        messageLabel.setText(message);
    }
}
