package com.auction.client.controller;

import com.auction.client.navigation.SceneNavigator;
import com.auction.client.service.ServerConnector;
import com.auction.shared.model.AuctionSession;
import com.auction.shared.model.User;
import com.auction.shared.network.Response;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Controller cho Admin Panel - quản lý users và phiên đấu giá.
 */
public class AdminController {

    // Users Table
    @FXML private TableView<User> usersTable;
    @FXML private TableColumn<User, Integer> colUserId;
    @FXML private TableColumn<User, String> colUsername;
    @FXML private TableColumn<User, String> colEmail;
    @FXML private TableColumn<User, String> colRole;

    // Auctions Table
    @FXML private TableView<AuctionSession> auctionsTable;
    @FXML private TableColumn<AuctionSession, Integer> colAuctionId;
    @FXML private TableColumn<AuctionSession, String> colAuctionItem;
    @FXML private TableColumn<AuctionSession, String> colAuctionStatus;
    @FXML private TableColumn<AuctionSession, Double> colAuctionPrice;

    @FXML private Label statsLabel;
    @FXML private Label messageLabel;
    @FXML private Button confirmActionBtn;
    @FXML private Button cancelActionBtn;

    private final ObservableList<User> usersList = FXCollections.observableArrayList();
    private final ObservableList<AuctionSession> auctionsList = FXCollections.observableArrayList();
    private final ServerConnector connector = ServerConnector.getInstance();
    private Runnable pendingAction;

    private record AdminData(List<User> users, List<AuctionSession> auctions) {}

    @FXML
    public void initialize() {
        // Users table
        colUserId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        colRole.setCellValueFactory(new PropertyValueFactory<>("role"));
        colRole.setCellFactory(col -> new TableCell<User, String>() {
            @Override
            protected void updateItem(String role, boolean empty) {
                super.updateItem(role, empty);
                setText(empty || role == null ? "" : displayRole(role));
            }
        });
        usersTable.setItems(usersList);
        usersTable.setPlaceholder(tablePlaceholder("Chưa có người dùng để hiển thị."));

        // Auctions table
        colAuctionId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colAuctionItem.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        colAuctionStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colAuctionPrice.setCellValueFactory(new PropertyValueFactory<>("currentHighestBid"));
        colAuctionStatus.setCellFactory(col -> new TableCell<AuctionSession, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                setText(empty || status == null ? "" : displayStatus(status));
            }
        });
        colAuctionPrice.setCellFactory(col -> new TableCell<AuctionSession, Double>() {
            @Override
            protected void updateItem(Double val, boolean empty) {
                super.updateItem(val, empty);
                setText(empty || val == null ? "" : String.format("%,.0f VNĐ", val));
            }
        });

        auctionsTable.setItems(auctionsList);
        auctionsTable.setPlaceholder(tablePlaceholder("Chưa có phiên đấu giá để hiển thị."));
        clearPendingAction();

        if (Boolean.getBoolean("auction.ui.smokeTest")) {
            statsLabel.setText("0 người dùng | 0 phiên đấu giá | 0 đang diễn ra");
            showMessage("UI smoke test: bỏ qua tải dữ liệu quản trị.", true);
            return;
        }
        refreshData();
    }

    @FXML
    private void refreshData() {
        usersList.clear();
        auctionsList.clear();
        showMessage("Đang tải dữ liệu quản trị...", true);

        CompletableFuture
                .supplyAsync(() -> new AdminData(connector.getAllUsers(), connector.getAdminAuctions()))
                .thenAccept(data -> Platform.runLater(() -> {
                    usersList.setAll(data.users() == null ? List.of() : data.users());
                    auctionsList.setAll(data.auctions() == null ? List.of() : data.auctions());
                    long activeCount = auctionsList.stream().filter(a -> "RUNNING".equals(a.getStatus())).count();
                    statsLabel.setText(String.format("%d người dùng | %d phiên đấu giá | %d đang diễn ra",
                            usersList.size(), auctionsList.size(), activeCount));
                    showMessage("Đã cập nhật dữ liệu quản trị.", true);
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> showMessage("Không tải được dữ liệu quản trị.", false));
                    return null;
                });
        if (System.currentTimeMillis() >= 0) return;

        List<User> users = connector.getAllUsers();
        usersList.addAll(users);

        List<AuctionSession> auctions = connector.getAdminAuctions();
        auctionsList.addAll(auctions);

        long activeCount = auctions.stream().filter(a -> "RUNNING".equals(a.getStatus())).count();
        statsLabel.setText(String.format("%d người dùng | %d phiên đấu giá | %d đang diễn ra",
                users.size(), auctions.size(), activeCount));
    }

    @FXML
    private void handleDeleteUser() {
        User selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showMessage("Vui lòng chọn người dùng cần xóa!", false);
            return;
        }
        if ("ADMIN".equals(selected.getRole())) {
            showMessage("Không thể xóa tài khoản Admin!", false);
            return;
        }

        setPendingAction("Xác nhận xóa người dùng '" + selected.getUsername() + "'?", () -> {
            runAdminAction(() -> connector.deleteUser(selected.getId()), "Đã xóa người dùng.");
            if (System.currentTimeMillis() >= 0) return;
            Response res = connector.deleteUser(selected.getId());
            if (res != null && "SUCCESS".equals(res.getStatus())) {
                refreshData();
                showMessage("Đã xóa người dùng.", true);
            } else {
                showMessage("Lỗi: " + (res != null ? res.getMessage() : ""), false);
            }
        });
    }

    @FXML
    private void handleCancelAuction() {
        AuctionSession selected = auctionsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showMessage("Vui lòng chọn phiên đấu giá cần hủy!", false);
            return;
        }
        if (!"RUNNING".equals(selected.getStatus()) && !"OPEN".equals(selected.getStatus())) {
            showMessage("Chỉ có thể hủy phiên chưa diễn ra hoặc đang diễn ra!", false);
            return;
        }

        setPendingAction("Xác nhận hủy phiên đấu giá #" + selected.getId() + "?", () -> {
            runAdminAction(() -> connector.cancelAuction(selected.getId()), "Đã hủy phiên đấu giá.");
            if (System.currentTimeMillis() >= 0) return;
            Response res = connector.cancelAuction(selected.getId());
            if (res != null && "SUCCESS".equals(res.getStatus())) {
                refreshData();
                showMessage("Đã hủy phiên đấu giá.", true);
            } else {
                showMessage("Lỗi: " + (res != null ? res.getMessage() : ""), false);
            }
        });
    }

    @FXML
    private void handleMarkAuctionPaid() {
        AuctionSession selected = auctionsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showMessage("Vui lòng chọn phiên đấu giá cần đánh dấu đã thanh toán!", false);
            return;
        }
        if (!"FINISHED".equals(selected.getStatus())) {
            showMessage("Chỉ có thể đánh dấu đã thanh toán cho phiên đã kết thúc!", false);
            return;
        }

        setPendingAction("Xác nhận đánh dấu đã thanh toán cho phiên #" + selected.getId() + "?", () -> {
            runAdminAction(() -> connector.markAuctionPaid(selected.getId()), "Đã đánh dấu phiên là đã thanh toán.");
            if (System.currentTimeMillis() >= 0) return;
            Response res = connector.markAuctionPaid(selected.getId());
            if (res != null && "SUCCESS".equals(res.getStatus())) {
                refreshData();
                showMessage("Đã đánh dấu phiên là đã thanh toán.", true);
            } else {
                showMessage("Lỗi: " + (res != null ? res.getMessage() : ""), false);
            }
        });
    }

    @FXML
    private void handleConfirmAction() {
        if (pendingAction != null) {
            Runnable action = pendingAction;
            clearPendingAction();
            action.run();
        }
    }

    @FXML
    private void handleCancelAction() {
        clearPendingAction();
        showMessage("Đã hủy thao tác.", true);
    }

    @FXML
    private void handleExit() {
        SceneNavigator.showDashboard();
    }

    private void runAdminAction(Supplier<Response> action, String successMessage) {
        showMessage("Đang xử lý thao tác quản trị...", true);
        CompletableFuture
                .supplyAsync(action)
                .thenAccept(res -> Platform.runLater(() -> {
                    if (res != null && "SUCCESS".equals(res.getStatus())) {
                        refreshData();
                        showMessage(successMessage, true);
                    } else {
                        showMessage("Lỗi: " + (res != null ? res.getMessage() : "Máy chủ không phản hồi."), false);
                    }
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> showMessage("Không thực hiện được thao tác quản trị.", false));
                    return null;
                });
    }

    private void setPendingAction(String message, Runnable action) {
        pendingAction = action;
        messageLabel.setText(message);
        messageLabel.getStyleClass().setAll("inline-message", "inline-message-warning");
        confirmActionBtn.setManaged(true);
        confirmActionBtn.setVisible(true);
        cancelActionBtn.setManaged(true);
        cancelActionBtn.setVisible(true);
    }

    private void clearPendingAction() {
        pendingAction = null;
        if (messageLabel != null) {
            messageLabel.setText("");
            messageLabel.getStyleClass().setAll("inline-message");
        }
        if (confirmActionBtn != null) {
            confirmActionBtn.setManaged(false);
            confirmActionBtn.setVisible(false);
        }
        if (cancelActionBtn != null) {
            cancelActionBtn.setManaged(false);
            cancelActionBtn.setVisible(false);
        }
    }

    private void showMessage(String msg, boolean success) {
        messageLabel.setText(msg);
        messageLabel.getStyleClass().setAll("inline-message", success ? "inline-message-success" : "inline-message-error");
    }

    private Label tablePlaceholder(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("empty-state-hint");
        return label;
    }

    private String displayRole(String role) {
        return "ADMIN".equalsIgnoreCase(role) ? "Quản trị" : "Người dùng";
    }

    private String displayStatus(String status) {
        return switch (status == null ? "" : status.toUpperCase()) {
            case "RUNNING" -> "Đang diễn ra";
            case "OPEN" -> "Chưa diễn ra";
            case "FINISHED" -> "Đã kết thúc";
            case "PAID" -> "Đã thanh toán";
            case "CANCELED" -> "Đã hủy";
            default -> "Không xác định";
        };
    }
}
