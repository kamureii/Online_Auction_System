package com.auction.client.controller;

import com.auction.client.navigation.SceneNavigator;
import com.auction.client.service.ServerConnector;
import com.auction.shared.network.Response;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.event.ActionEvent;

public class RegisterController {
    @FXML private TextField fullnameField;
    @FXML private TextField emailField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label messageLabel;

    @FXML
    private void goToLogin() {
        SceneNavigator.showLogin();
    }

    @FXML
    private void handleBackHome() {
        SceneNavigator.showDashboard();
    }

    @FXML
    private void handleRegister(ActionEvent event) {
        String fullname = fullnameField.getText().trim();
        String email = emailField.getText().trim();
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        // Validation
        if (fullname.isEmpty() || email.isEmpty() || username.isEmpty()
                || password.isEmpty() || confirmPassword.isEmpty()) {
            showError("Vui lòng điền đầy đủ tất cả các trường!");
            return;
        }

        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            showError("Email không hợp lệ!");
            return;
        }

        if (username.length() < 3) {
            showError("Tên đăng nhập phải có ít nhất 3 ký tự!");
            return;
        }

        if (password.length() < 6) {
            showError("Mật khẩu phải có ít nhất 6 ký tự!");
            return;
        }

        if (!password.equals(confirmPassword)) {
            showError("Mật khẩu xác nhận không khớp!");
            return;
        }

        // Gửi request
        ServerConnector connector = ServerConnector.getInstance();
        if (!connector.isConnected()) {
            connector.connect();
        }

        Response res = connector.register(username, email, password, fullname);

        if (res != null && "SUCCESS".equals(res.getStatus())) {
            messageLabel.setTextFill(Color.GREEN);
            messageLabel.setText("Đăng ký thành công! Đang chuyển về đăng nhập...");

            javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(1));
            pause.setOnFinished(e -> SceneNavigator.showLogin());
            pause.play();
        } else {
            showError(res != null ? res.getMessage() : "Đăng ký thất bại!");
        }
    }

    private void showError(String message) {
        messageLabel.setTextFill(Color.RED);
        messageLabel.setText(message);
    }
}
