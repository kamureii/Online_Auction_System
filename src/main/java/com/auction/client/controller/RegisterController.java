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
    private Label messageLabel;

    // Chuyển về màn hình Login khi click vào chữ
    @FXML
    private void goToLogin(MouseEvent event) {
        try {
            // Lấy Stage và lưu trạng thái phóng to
            Stage stage = (Stage) usernameField.getScene().getWindow();
            boolean isMax = stage.isMaximized(); // Nhớ trạng thái

            Parent root = FXMLLoader.load(getClass().getResource("/views/Login.fxml"));

            stage.setScene(new Scene(root));
            stage.setMaximized(isMax); // Áp dụng lại trạng thái
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Hàm xử lý khi bấm nút "Đăng ký"
    @FXML
    private void handleRegister(ActionEvent event) {
        // Lấy thêm dữ liệu từ các trường mới
        String fullname = fullnameField.getText();
        String email = emailField.getText();
        String username = usernameField.getText();
        String password = passwordField.getText();

        ServerConnector server = new ServerConnector();
        Response res = server.register(username, password, email, fullname);

        //Hiện đăng kí thành công
        if (res != null && res.getStatus().equals("SUCCESS")) {
            messageLabel.setTextFill(Color.GREEN);
            messageLabel.setText("Đăng ký thành công");
            System.out.println("SUCCESS ");

            try {
                // Đợi để thấy chữ "Đăng ký thành công" rồi mới chuyển
                javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(0.8));
                pause.setOnFinished(e -> {
                    try {
                        // Lấy Stage và lưu trạng thái trước khi chuyển sau khi pause
                        Stage stage = (Stage) usernameField.getScene().getWindow();
                        boolean isMax = stage.isMaximized(); // MỚI: Nhớ trạng thái

                        Parent root = FXMLLoader.load(getClass().getResource("/views/Login.fxml"));

                        stage.setScene(new Scene(root));
                        stage.setMaximized(isMax); // MỚI: Áp dụng lại
                        stage.show();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });
                pause.play();

            } catch (Exception e) {
                e.printStackTrace();
            }

        } else {
            messageLabel.setTextFill(Color.RED);
            // Hiển thị thông báo lỗi cụ thể từ server nếu có
            messageLabel.setText(res != null ? res.getMessage() : "Đăng ký thất bại");
        }
    }
}
