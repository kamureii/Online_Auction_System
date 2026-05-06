package com.auction.client.controller;

import com.auction.client.service.ServerConnector;
import com.auction.shared.network.Response;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class LoginController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label messageLabel;

    @FXML
    private void goToRegister() {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/views/Register.fxml"));
            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleLogin(ActionEvent event) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Vui lòng nhập đầy đủ thông tin!");
            return;
        }

        // Đảm bảo kết nối tới server
        ServerConnector connector = ServerConnector.getInstance();
        if (!connector.isConnected()) {
            if (!connector.connect()) {
                showError("Không thể kết nối tới server! Hãy kiểm tra server.");
                return;
            }
        }

        Response res = connector.login(username, password);

        if (res != null && "SUCCESS".equals(res.getStatus())) {
            messageLabel.setTextFill(Color.GREEN);
            messageLabel.setText("Đăng nhập thành công!");

            try {
                Parent root = FXMLLoader.load(getClass().getResource("/views/Dashboard.fxml"));
                Stage stage = (Stage) usernameField.getScene().getWindow();
                stage.setScene(new Scene(root));
                stage.setResizable(false);
                stage.sizeToScene();
                stage.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            showError(res != null ? res.getMessage() : "Không thể kết nối tới server!");
        }
    }

    private void showError(String message) {
        messageLabel.setTextFill(Color.RED);
        messageLabel.setText(message);
    }
}
