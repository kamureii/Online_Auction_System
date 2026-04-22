package com.auction.client.controller;

import com.auction.client.service.ServerConnector;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.util.Pair;

import java.util.List;
import java.util.Optional;

// import com.auction.client.connector.ServerConnector;

public class DashboardController {

    @FXML private TableView<Product> productTable;
    @FXML private TableColumn<Product, String> colName;
    @FXML private TableColumn<Product, Double> colPrice;

    private ObservableList<Product> productList = FXCollections.observableArrayList();

    // Khởi tạo ServerConnector để gọi dữ liệu
    private ServerConnector serverConnector = new ServerConnector();

    @FXML
    public void initialize() {
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("price"));

        //Gọi dữ liệu từ ServerConnector thay vì loadMockData() tại chỗ
        refreshProductList();

        productTable.setItems(productList);

        productTable.setOnMouseClicked(event -> {
            if (event.getButton().equals(MouseButton.PRIMARY) && event.getClickCount() == 2) {
                Product selectedProduct = productTable.getSelectionModel().getSelectedItem();
                if (selectedProduct != null) {
                    handleSwitchToDetail(selectedProduct);
                }
            }
        });
    }

    private void refreshProductList() {
        productList.clear();

        // Lấy list String từ ServerConnector
        List<String> rawData = serverConnector.getProducts();

        // Chuyển đổi từ String (data thô) sang Object Product để hiển thị lên bảng
        for (String item : rawData) {
            //Tách chuỗi để lấy tên và giá
            productList.add(new Product(item, 0.0));
        }
    }

    @FXML
    private void handleOpenAddForm() {
        Dialog<Pair<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Đăng bán sản phẩm");
        dialog.setHeaderText("Nhập thông tin sản phẩm muốn đấu giá");

        ButtonType postButtonType = new ButtonType("Đăng bán", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(postButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField nameField = new TextField();
        TextField priceField = new TextField();

        grid.add(new Label("Tên sản phẩm:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Giá khởi điểm (VNĐ):"), 0, 1);
        grid.add(priceField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == postButtonType) {
                return new Pair<>(nameField.getText(), priceField.getText());
            }
            return null;
        });

        Optional<Pair<String, String>> result = dialog.showAndWait();

        result.ifPresent(data -> {
            try {
                String name = data.getKey();
                // Gửi dữ liệu sang ServerConnector để xử lý thay vì add trực tiếp vào list
                // serverConnector.addProduct(name, data.getValue());

                // Sau đó nạp lại bảng
                refreshProductList();

                System.out.println("Đã gửi yêu cầu đăng bán: " + name);
            } catch (Exception e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setContentText("Lỗi hệ thống!");
                alert.show();
            }
        });
    }

    private void handleSwitchToDetail(Product product) {
        System.out.println("Đang chuyển sang chi tiết sản phẩm: " + product.getName());
    }

    public static class Product {
        private String name;
        private double price;

        public Product(String name, double price) {
            this.name = name;
            this.price = price;
        }

        public String getName() {
            return name;
        }
        public double getPrice() {
            return price;
        }
        public void setPrice(double price) {
            this.price = price;
        }
    }
}
