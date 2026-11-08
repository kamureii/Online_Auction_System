package com.auction.client.controller;

import com.auction.client.navigation.SceneNavigator;
import com.auction.client.service.ServerConnector;
import com.auction.shared.validation.AccountValidator;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;

import java.util.concurrent.CompletableFuture;

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

        String error = validateInput(fullname, email, username, password, confirmPassword);
        if (error != null) {
            showError(error);
            return;
        }

        setFormDisabled(true);
        messageLabel.setTextFill(Color.GRAY);
        messageLabel.setText("Đang tạo tài khoản...");
        CompletableFuture
                .supplyAsync(() -> ServerConnector.getInstance().register(username, email, password, fullname))
                .thenAccept(res -> Platform.runLater(() -> {
                    setFormDisabled(false);
                    if (res != null && "SUCCESS".equals(res.getStatus())) {
                        messageLabel.setTextFill(Color.GREEN);
                        messageLabel.setText("Đăng ký thành công! Đang chuyển về đăng nhập...");

                        javafx.animation.PauseTransition pause =
                                new javafx.animation.PauseTransition(javafx.util.Duration.seconds(1));
                        pause.setOnFinished(e -> SceneNavigator.showLogin());
                        pause.play();
                    } else {
                        showError(res != null ? res.getMessage() : "Đăng ký thất bại!");
                    }
                }))
                .exceptionally(failure -> {
                    Platform.runLater(() -> {
                        setFormDisabled(false);
                        showError("Không thể đăng ký lúc này.");
                    });
                    return null;
                });
    }

    private void showError(String message) {
        messageLabel.setTextFill(Color.RED);
        messageLabel.setText(message);
    }

    /** Dùng chung quy tắc với server ({@link AccountValidator}); server vẫn kiểm tra lại. */
    private String validateInput(String fullname, String email, String username,
                                 String password, String confirmPassword) {
        if (confirmPassword.isEmpty()) {
            return "Vui lòng điền đầy đủ tất cả các trường!";
        }
        String error = AccountValidator.validateRegistration(fullname, email, username, password);
        if (error != null) {
            return error;
        }
        if (!password.equals(confirmPassword)) {
            return "Mật khẩu xác nhận không khớp!";
        }
        return null;
    }

    private void setFormDisabled(boolean disabled) {
        fullnameField.setDisable(disabled);
        emailField.setDisable(disabled);
        usernameField.setDisable(disabled);
        passwordField.setDisable(disabled);
        confirmPasswordField.setDisable(disabled);
    }
}
