package com.auction.client.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class AuctionController {
    @FXML
    private Label productNameLabel;  // Tên sản phẩm

    @FXML
    private Label currentPriceLabel; // Giá hiện tại

    @FXML
    private Label timerLabel;        // Đồng hồ

    @FXML
    private TextField bidAmountField; // Ô nhập tiền

    @FXML
    private Button bidButton;         // Nút bấm

    // Biến lưu giá tiền hiện tại để tính toán
    private double currentPrice = 0;


    @FXML
    public void initialize() {
        System.out.println("Giao diện Phòng Đấu Giá đã lên sóng!");
    }

    // Hàm nhận dữ liệu từ ngoài vào
    public void setData(String name, double price) {
        this.currentPrice = price;
        
        if (productNameLabel != null) {
            productNameLabel.setText(name);
        }
        if (currentPriceLabel != null) {
            currentPriceLabel.setText(String.format("%,.0f VNĐ", price));
        }
    }

    // xử lý nút trả giá
    @FXML
    public void handlePlaceBid(ActionEvent event) {
        try {
            String input = bidAmountField.getText().replace(",", "").replace(".", "");
            double bidAmount = Double.parseDouble(input);
            if (bidAmount > currentPrice) { //ktra giá tiền
                currentPrice = bidAmount; // Cập nhật giá mới
                currentPriceLabel.setText(String.format("%,.0f VNĐ", currentPrice));
                System.out.println("Cập nhật giá thành công: " + currentPrice);
                bidAmountField.clear(); 
            } else {
                System.out.println("Lỗi: Số tiền phải lớn hơn giá hiện tại!");
            }
        } catch (NumberFormatException e) {
            System.out.println("Lỗi: Vui lòng chỉ nhập số hợp lệ!");
        }
    }

    // Hàm mở form đấu giá
    public void loadAuctionRoom(String productName, double currentPrice) throws Exception {
        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/views/AuctionRoom.fxml")
        );
        Parent root = loader.load();

        AuctionController controller = loader.getController();
        controller.setData(productName, currentPrice);

        Scene scene = new Scene(root);
        Stage stage = new Stage();
        stage.setTitle("Phòng Đấu Giá Trực Tuyến - " + productName); 
        stage.setScene(scene);
        stage.show();
    }
}