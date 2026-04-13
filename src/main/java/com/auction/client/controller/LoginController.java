package com.auction.client.controller;

import com.auction.client.service.ServerConnector;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.event.ActionEvent;

// Import để chuyển màn hình
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

// Import Response trả về từ server
import com.auction.shared.network.Response;

public class LoginController {
    @FXML
    private TextField usernameField; // Liên kết với fx:id trong FXML
    @FXML
    private PasswordField passwordField; // Liên kết với ô nhập password trong FXML
    @FXML
    private Label messageLabel; //Hiện thông báo (thành công / lỗi)

    @FXML
    private void goToRegister() {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/views/Register.fxml"));

            Stage stage = (Stage) usernameField.getScene().getWindow();

            // Thay đổi Scene
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) {
            System.err.println("Lỗi khi chuyển sang màn hình Đăng ký: " + e.getMessage());
            e.printStackTrace();
        }
    }
    // Hàm xử lý khi bấm nút "Đăng nhập"
    @FXML
    private void handleLogin(ActionEvent event){

        // Lấy dữ liệu người dùng nhập
        String username = usernameField.getText();
        String password = passwordField.getText();
        ServerConnector server = new ServerConnector();
        Response res = server.login(username, password);

// Kiểm tra kết quả trả về
        if (res.getStatus().equals("SUCCESS")) {

            // Hiện chữ xanh
            messageLabel.setTextFill(Color.GREEN);
            messageLabel.setText("Thành công");

            try{ // Chuyển sang màn Dashboard
                Parent root = FXMLLoader.load(getClass().getResource("/views/Dashboard.fxml"));
                Stage stage = (Stage) usernameField.getScene().getWindow();
                stage.setScene(new Scene(root));
            }
            catch (Exception e){
                e.printStackTrace();
            }
        } else {    // Hiện lỗi màu đỏ
            messageLabel.setTextFill(Color.RED);
            messageLabel.setText("Sai tài khoản hoặc mật khẩu");
        }
    }
}
