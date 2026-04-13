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
import javafx.scene.input.MouseEvent; // xử lý click chuột vào Label

public class RegisterController {
    @FXML
    private TextField fullnameField;

    @FXML
    private TextField emailField;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private Label messageLabel;

    // Chuyển về màn hình Login khi click vào chữ
    @FXML
    private void goToLogin(MouseEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/views/Login.fxml"));
            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setScene(new Scene(root));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Hàm xử lý khi bấm nút "Đăng ký"
    @FXML
    private void handleRegister(ActionEvent event) {
        // Lấy dữ liệu từ các trường
        String fullname = fullnameField.getText().trim();
        String email = emailField.getText().trim();
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        // === VALIDATION ===

        // Kiểm tra trường rỗng
        if (fullname.isEmpty() || email.isEmpty() || username.isEmpty()
                || password.isEmpty() || confirmPassword.isEmpty()) {
            showError("Vui lòng điền đầy đủ tất cả các trường!");
            return;
        }

        // Kiểm tra định dạng email
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            showError("Email không hợp lệ! Vui lòng nhập đúng định dạng.");
            return;
        }

        // Kiểm tra độ dài username
        if (username.length() < 3) {
            showError("Tên đăng nhập phải có ít nhất 3 ký tự!");
            return;
        }

        // Kiểm tra độ dài mật khẩu
        if (password.length() < 6) {
            showError("Mật khẩu phải có ít nhất 6 ký tự!");
            return;
        }

        // Kiểm tra xác nhận mật khẩu
        if (!password.equals(confirmPassword)) {
            showError("Mật khẩu xác nhận không khớp!");
            return;
        }

        // === GỬI REQUEST LÊN SERVER ===
        ServerConnector server = new ServerConnector();
        Response res = server.register(username, email, password, fullname);

        //Hiện đăng kí thành công
        if (res != null && res.getStatus().equals("SUCCESS")) {
            messageLabel.setTextFill(Color.GREEN);
            messageLabel.setText("Đăng ký thành công! Đang chuyển về trang đăng nhập...");
            System.out.println("SUCCESS");

            try {
                // Đợi để thấy chữ "Đăng ký thành công" rồi mới chuyển
                javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(0.8));
                pause.setOnFinished(e -> {
                    try {
                        Parent root = FXMLLoader.load(getClass().getResource("/views/Login.fxml"));
                        Stage stage = (Stage) usernameField.getScene().getWindow();
                        stage.setScene(new Scene(root));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });
                pause.play();

            } catch (Exception e) {
                e.printStackTrace();
            }

        } else {
            // Hiển thị thông báo lỗi cụ thể từ server nếu có
            showError(res != null ? res.getMessage() : "Đăng ký thất bại! Vui lòng thử lại.");
        }
    }

    // Helper method hiển thị lỗi
    private void showError(String message) {
        messageLabel.setTextFill(Color.RED);
        messageLabel.setText(message);
    }

    // Hàm chuyển về màn hình Login khi bấm nút
    @FXML
    private void goToLoginAction(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/views/Login.fxml"));
            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setScene(new Scene(root));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
