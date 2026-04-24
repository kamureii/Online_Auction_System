package com.auction.client.controller;

import com.auction.client.service.ServerConnector;
import com.auction.shared.network.Response;
import com.auction.shared.model.Item;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.util.Pair;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public class DashboardController {

    @FXML private TableView<Item> productTable;
    @FXML private TableColumn<Item, String> colName;
    @FXML private TableColumn<Item, Double> colPrice;

    private ObservableList<Item> productList = FXCollections.observableArrayList();

    // Khởi tạo ServerConnector để gọi dữ liệu
    private ServerConnector serverConnector = new ServerConnector();

    @FXML
    public void initialize() {
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("currentPrice"));

        //Gọi dữ liệu từ ServerConnector thay vì loadMockData() tại chỗ
        refreshProductList();

        productTable.setItems(productList);

        productTable.setOnMouseClicked(event -> {
            if (event.getButton().equals(MouseButton.PRIMARY) && event.getClickCount() == 2) {
                Item selectedProduct = productTable.getSelectionModel().getSelectedItem();
                if (selectedProduct != null) {
                    handleSwitchToDetail(selectedProduct);
                }
            }
        });
    }

    private void refreshProductList() {
        productList.clear();
        List<Item> rawData = serverConnector.getProducts();
        productList.addAll(rawData);
    }

    @FXML
    private void handleOpenAddForm() {
        Dialog<ItemFormData> dialog = new Dialog<>();
        dialog.setTitle("Đăng bán sản phẩm");
        dialog.setHeaderText("Nhập thông tin sản phẩm muốn đấu giá");

        ButtonType postButtonType = new ButtonType("Đăng bán", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(postButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField nameField = new TextField();
        TextField descriptionField = new TextField();
        TextField startingPriceField = new TextField();
        TextField minimumStepField = new TextField();
        TextField auctionDurationField = new TextField();

        grid.add(new Label("Tên sản phẩm:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Mô tả sản phẩm:"), 0, 1);
        grid.add(descriptionField, 1, 1);
        grid.add(new Label("Giá khởi điểm (VNĐ):"), 0, 2);
        grid.add(startingPriceField, 1, 2);
        grid.add(new Label("Bước giá tối thiểu (VNĐ):"), 0, 3);
        grid.add(minimumStepField, 1, 3);
        grid.add(new Label("Thời gian đấu giá (ngày):"), 0, 4);
        grid.add(auctionDurationField, 1, 4);

        // Set default values
        minimumStepField.setText("10000");
        auctionDurationField.setText("1");

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == postButtonType) {
                return new ItemFormData(
                    nameField.getText(),
                    descriptionField.getText(),
                    startingPriceField.getText(),
                    minimumStepField.getText(),
                    auctionDurationField.getText()
                );
            }
            return null;
        });

        Optional<ItemFormData> result = dialog.showAndWait();

        result.ifPresent(data -> {
            try {
                // Validate input fields
                if (data.getName().trim().isEmpty() || data.getDescription().trim().isEmpty()) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setContentText("Vui lòng điền đầy đủ tên và mô tả sản phẩm!");
                    alert.show();
                    return;
                }

                String name = data.getName().trim();
                String description = data.getDescription().trim();
                double startingPrice = Double.parseDouble(data.getStartingPrice());
                int minimumStep = Integer.parseInt(data.getMinimumStep());
                int auctionDuration = Integer.parseInt(data.getAuctionDuration());

                if (startingPrice <= 0 || minimumStep <= 0 || auctionDuration <= 0) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setContentText("Giá và bước giá phải lớn hơn 0, thời gian đấu giá phải lớn hơn 0!");
                    alert.show();
                    return;
                }

                int ownerId = (ServerConnector.currentUser != null) ? ServerConnector.currentUser.getId() : 1;
                Timestamp endTime = new Timestamp(System.currentTimeMillis() + (auctionDuration * 86400000L)); // auctionDuration days

                Item newItem = new Item(name, description, startingPrice, startingPrice, minimumStep, ownerId, endTime);
                System.out.println("Sending item to server: " + name + ", Price: " + startingPrice);
                Response response = serverConnector.addProduct(newItem);

                // Chỉ nạp lại bảng nếu đăng bán thành công
                if (response != null && "SUCCESS".equals(response.getStatus())) {
                    refreshProductList();
                    System.out.println("Đăng bán thành công: " + name);
                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                    successAlert.setContentText("Đăng bán sản phẩm thành công!");
                    successAlert.show();
                } else {
                    String errorMsg = (response != null ? response.getMessage() : "Không nhận được phản hồi từ server");
                    System.err.println("Đăng bán thất bại: " + name + " - " + errorMsg);
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Lỗi Đăng Bán");
                    alert.setHeaderText("Đăng bán sản phẩm thất bại");
                    alert.setContentText("Lỗi: " + errorMsg + "\n\nVui lòng kiểm tra:\n- Kết nối đến server\n- Kết nối đến database\n- Thông tin sản phẩm");
                    alert.show();
                }
            } catch (NumberFormatException e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Lỗi Định Dạng");
                alert.setHeaderText("Định dạng số không hợp lệ");
                alert.setContentText("Vui lòng nhập số hợp lệ cho giá và bước giá!");
                alert.show();
                e.printStackTrace();
            } catch (Exception e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Lỗi Hệ Thống");
                alert.setHeaderText("Lỗi không xác định");
                alert.setContentText("Chi tiết lỗi: " + e.getMessage());
                alert.show();
                e.printStackTrace();
            }
        });
    }

    private void handleSwitchToDetail(Item product) {
        System.out.println("Đang chuyển sang chi tiết sản phẩm: " + product.getName());
        try {
            AuctionController myController = new AuctionController();
            myController.loadAuctionRoom(product.getId(), product.getName(), product.getCurrentPrice());
        } catch (Exception e) {
            e.printStackTrace();
        }
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

    // Data class for the upload item form
    public static class ItemFormData {
        private String name;
        private String description;
        private String startingPrice;
        private String minimumStep;
        private String auctionDuration;

        public ItemFormData(String name, String description, String startingPrice, String minimumStep, String auctionDuration) {
            this.name = name;
            this.description = description;
            this.startingPrice = startingPrice;
            this.minimumStep = minimumStep;
            this.auctionDuration = auctionDuration;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getStartingPrice() { return startingPrice; }
        public String getMinimumStep() { return minimumStep; }
        public String getAuctionDuration() { return auctionDuration; }
    }
}
