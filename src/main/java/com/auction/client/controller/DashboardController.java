package com.auction.client.controller;

import com.auction.client.service.ServerConnector;
import com.auction.shared.model.AuctionSession;
import com.auction.shared.model.User;
import com.auction.shared.network.Response;
import com.auction.shared.observer.AuctionEvent;
import com.auction.shared.observer.AuctionEventListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.util.List;
import java.util.Optional;

/**
 * Controller cho Dashboard - hiển thị danh sách phiên đấu giá.
 * Implements AuctionEventListener để nhận realtime update.
 */
public class DashboardController implements AuctionEventListener {

    @FXML private TableView<AuctionSession> auctionTable;
    @FXML private TableColumn<AuctionSession, String> colItemName;
    @FXML private TableColumn<AuctionSession, Double> colCurrentBid;
    @FXML private TableColumn<AuctionSession, String> colStatus;
    @FXML private TableColumn<AuctionSession, String> colEndTime;
    @FXML private Label userInfoLabel;
    @FXML private Button addItemBtn;
    @FXML private Button adminBtn;

    private final ObservableList<AuctionSession> auctionList = FXCollections.observableArrayList();
    private final ServerConnector connector = ServerConnector.getInstance();

    @FXML
    public void initialize() {
        // Cấu hình bảng
        colItemName.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        colCurrentBid.setCellValueFactory(new PropertyValueFactory<>("currentHighestBid"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colEndTime.setCellValueFactory(new PropertyValueFactory<>("endTime"));

        // Format cột giá
        colCurrentBid.setCellFactory(column -> new TableCell<AuctionSession, Double>() {
            @Override
            protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                setText(empty || price == null ? "" : String.format("%,.0f VNĐ", price));
            }
        });

        // Format cột trạng thái với màu sắc
        colStatus.setCellFactory(column -> new TableCell<AuctionSession, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText("");
                    setStyle("");
                } else {
                    setText(status);
                    switch (status) {
                        case "RUNNING": setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;"); break;
                        case "FINISHED": setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;"); break;
                        case "OPEN": setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold;"); break;
                        case "CANCELED": setStyle("-fx-text-fill: #95a5a6;"); break;
                        default: setStyle(""); break;
                    }
                }
            }
        });

        // Hiển thị thông tin user
        User currentUser = ServerConnector.currentUser;
        if (currentUser != null) {
            userInfoLabel.setText("👤 " + currentUser.getFullName() + " | Vai trò: " + currentUser.getRole());

            // Phân quyền: chỉ SELLER mới thấy nút đăng bán
            if (!"SELLER".equals(currentUser.getRole())) {
                addItemBtn.setVisible(false);
                addItemBtn.setManaged(false);
            }

            // Chỉ ADMIN mới thấy nút quản trị
            if (!"ADMIN".equals(currentUser.getRole())) {
                adminBtn.setVisible(false);
                adminBtn.setManaged(false);
            }
        }

        // Load dữ liệu
        refreshAuctionList();
        auctionTable.setItems(auctionList);

        // Double-click mở phòng đấu giá
        auctionTable.setOnMouseClicked(event -> {
            if (event.getButton().equals(MouseButton.PRIMARY) && event.getClickCount() == 2) {
                AuctionSession selected = auctionTable.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    openAuctionRoom(selected);
                }
            }
        });

        // Đăng ký lắng nghe event (Observer)
        connector.addEventListener(this);
    }

    private void refreshAuctionList() {
        auctionList.clear();
        List<AuctionSession> data = connector.getAuctions();
        auctionList.addAll(data);
    }

    @FXML
    private void handleRefresh() {
        refreshAuctionList();
    }

    @FXML
    private void handleOpenAddForm() {
        Dialog<ItemFormData> dialog = new Dialog<>();
        dialog.setTitle("Đăng Bán Sản Phẩm");
        dialog.setHeaderText("Nhập thông tin sản phẩm muốn đấu giá");

        ButtonType postButtonType = new ButtonType("Đăng bán", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(postButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField nameField = new TextField();
        TextField descriptionField = new TextField();
        TextField startingPriceField = new TextField();
        TextField minIncrementField = new TextField();
        TextField auctionDaysField = new TextField();
        ComboBox<String> categoryBox = new ComboBox<>();
        categoryBox.getItems().addAll("ELECTRONICS", "ART", "VEHICLE", "OTHER");
        categoryBox.setValue("ELECTRONICS");

        grid.add(new Label("Tên sản phẩm:"), 0, 0); grid.add(nameField, 1, 0);
        grid.add(new Label("Mô tả:"), 0, 1); grid.add(descriptionField, 1, 1);
        grid.add(new Label("Danh mục:"), 0, 2); grid.add(categoryBox, 1, 2);
        grid.add(new Label("Giá khởi điểm (VNĐ):"), 0, 3); grid.add(startingPriceField, 1, 3);
        grid.add(new Label("Bước giá tối thiểu (VNĐ):"), 0, 4); grid.add(minIncrementField, 1, 4);
        grid.add(new Label("Thời gian đấu giá (ngày):"), 0, 5); grid.add(auctionDaysField, 1, 5);

        minIncrementField.setText("10000");
        auctionDaysField.setText("1");

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == postButtonType) {
                return new ItemFormData(nameField.getText(), descriptionField.getText(),
                        categoryBox.getValue(), startingPriceField.getText(),
                        minIncrementField.getText(), auctionDaysField.getText());
            }
            return null;
        });

        Optional<ItemFormData> result = dialog.showAndWait();
        result.ifPresent(data -> {
            try {
                if (data.name.trim().isEmpty()) {
                    showAlert(Alert.AlertType.ERROR, "Vui lòng nhập tên sản phẩm!");
                    return;
                }
                double price = Double.parseDouble(data.startingPrice);
                double minInc = Double.parseDouble(data.minIncrement);
                int days = Integer.parseInt(data.auctionDays);
                if (price <= 0 || minInc <= 0 || days <= 0) {
                    showAlert(Alert.AlertType.ERROR, "Giá, bước giá và số ngày phải lớn hơn 0!");
                    return;
                }

                int sellerId = ServerConnector.currentUser != null ? ServerConnector.currentUser.getId() : 1;
                Response res = connector.addProduct(data.name, data.description, data.category, price, minInc, sellerId, days);

                if (res != null && "SUCCESS".equals(res.getStatus())) {
                    showAlert(Alert.AlertType.INFORMATION, "Đăng bán sản phẩm thành công!");
                    refreshAuctionList();
                } else {
                    showAlert(Alert.AlertType.ERROR, "Lỗi: " + (res != null ? res.getMessage() : "Không phản hồi"));
                }
            } catch (NumberFormatException e) {
                showAlert(Alert.AlertType.ERROR, "Vui lòng nhập số hợp lệ!");
            }
        });
    }

    @FXML
    private void handleOpenAdmin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/AdminPanel.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("Quản Trị Hệ Thống");
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleLogout() {
        connector.removeEventListener(this);
        ServerConnector.currentUser = null;
        // Ngắt kết nối cũ và tạo kết nối mới cho lần login tiếp
        connector.disconnect();

        try {
            Parent root = FXMLLoader.load(getClass().getResource("/views/Login.fxml"));
            Stage stage = (Stage) auctionTable.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.sizeToScene();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void openAuctionRoom(AuctionSession session) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/AuctionRoom.fxml"));
            Parent root = loader.load();

            AuctionController controller = loader.getController();
            controller.setAuctionData(session);

            Stage stage = new Stage();
            stage.setTitle("Phòng Đấu Giá - " + session.getItemName());
            stage.setScene(new Scene(root));
            stage.setResizable(false);
            stage.setOnCloseRequest(e -> controller.cleanup());
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showAlert(Alert.AlertType type, String content) {
        Alert alert = new Alert(type);
        alert.setContentText(content);
        alert.show();
    }

    // ========================= Observer Pattern =========================
    @Override
    public void onBidUpdate(AuctionEvent event) { refreshAuctionList(); }
    @Override
    public void onAuctionEnded(AuctionEvent event) { refreshAuctionList(); }
    @Override
    public void onAuctionStarted(AuctionEvent event) { refreshAuctionList(); }
    @Override
    public void onItemListUpdated(AuctionEvent event) { refreshAuctionList(); }
    @Override
    public void onAuctionExtended(AuctionEvent event) { refreshAuctionList(); }

    // ========================= Inner class =========================
    private static class ItemFormData {
        String name, description, category, startingPrice, minIncrement, auctionDays;
        ItemFormData(String name, String desc, String cat, String price, String minInc, String days) {
            this.name = name; this.description = desc; this.category = cat;
            this.startingPrice = price; this.minIncrement = minInc; this.auctionDays = days;
        }
    }
}
