package com.auction.client.controller;

import com.auction.client.navigation.SceneNavigator;
import com.auction.client.service.ServerConnector;
import com.auction.client.ui.AiChatWidget;
import com.auction.shared.model.AuctionSession;
import com.auction.shared.model.Bid;
import com.auction.shared.network.Response;
import com.auction.shared.observer.AuctionEvent;
import com.auction.shared.observer.AuctionEventListener;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

import java.io.File;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Controller phòng đấu giá trực tuyến.
 * - Countdown timer thực
 * - Realtime bid update (Observer)
 * - Lịch sử bid
 * - Biểu đồ giá (LineChart)
 * - Đấu giá tự động
 */
public class AuctionController implements AuctionEventListener {

    // UI Elements
    @FXML private Label productNameLabel;
    @FXML private Label currentPriceLabel;
    @FXML private Label timerLabel;
    @FXML private Label statusLabel;
    @FXML private Label messageLabel;
    @FXML private Label productDetailsLabel;
    @FXML private Label bidCountLabel;
    @FXML private Label roomRoleLabel;
    @FXML private StackPane rootStack;
    @FXML private TextFlow guestLoginPrompt;
    @FXML private Text guestLoginLink;
    @FXML private ImageView productImageView;
    @FXML private TextField bidAmountField;
    @FXML private Button bidButton;
    @FXML private Button backButton;
    @FXML private HBox bidPanel;
    @FXML private Separator bidSeparator;

    @FXML private ListView<Bid> bidHistoryFeed;
    @FXML private LineChart<String, Number> priceChart;
    @FXML private CategoryAxis xAxis;
    @FXML private NumberAxis yAxis;

    // Đấu giá tự động
    @FXML private TextField maxBidField;
    @FXML private TextField bidIncrementField;
    @FXML private Button autoBidButton;
    @FXML private Button cancelAutoBidButton;
    @FXML private Label autoBidStatusLabel;
    @FXML private VBox autoBidPanel;

    private final ServerConnector connector = ServerConnector.getInstance();
    private final ObservableList<Bid> bidHistoryList = FXCollections.observableArrayList();
    private XYChart.Series<String, Number> priceSeries;

    private int currentAuctionId;
    private int currentSellerId;
    private double currentPrice;
    private long endTimeMillis;
    private Timeline countdownTimeline;

    @FXML
    public void initialize() {
        // Cấu hình bảng lịch sử bid
        setupBidHistoryTable();
        AiChatWidget.attachTo(rootStack);

        // Cấu hình biểu đồ
        priceSeries = new XYChart.Series<>();
        priceSeries.setName("Giá đấu");
        priceChart.getData().add(priceSeries);
        priceChart.setCreateSymbols(true);
        priceChart.setAnimated(true); // Bật hiệu ứng mượt mà
        
        yAxis.setTickLabelFormatter(new NumberAxis.DefaultFormatter(yAxis, null, " VNĐ"));

        // Đăng ký Observer
        connector.addEventListener(this);
    }

    /**
     * Thiết lập dữ liệu phiên đấu giá.
     */
    public void setAuctionData(AuctionSession session) {
        this.currentAuctionId = session.getId();
        this.currentSellerId = session.getSellerId();
        this.currentPrice = session.getCurrentHighestBid();
        this.endTimeMillis = session.getEndTime().getTime();

        productNameLabel.setText(session.getItemName());
        if (productDetailsLabel != null) productDetailsLabel.setText(session.getItemDescription() == null ? "" : session.getItemDescription());
        if (bidCountLabel != null) bidCountLabel.setText(session.getBidCount() + " lượt đấu giá");
        setProductImage(session.getItemImagePath());
        currentPriceLabel.setText(String.format("%,.0f VNĐ", currentPrice));
        statusLabel.setText("Trạng thái: " + displayStatus(session.getStatus()));
        applyStatusStyle(session.getStatus());

        boolean isActive = "RUNNING".equals(session.getStatus());
        boolean loggedIn = ServerConnector.currentUser != null;
        boolean isSeller = loggedIn && ServerConnector.currentUser.getId() == currentSellerId;
        boolean canBid = isActive && loggedIn && !isSeller;
        setBidPanelsVisible(!isSeller);
        setBidInputsDisabled(!canBid);

        if (isSeller) {
            showStandardMessage();
            configureRoleBadge("Bạn là Người bán của phiên này", true);
            messageLabel.setText("Bạn đang xem phiên với tư cách Người bán. Khối trả giá đã được ẩn.");
            messageLabel.setTextFill(Color.web("#b78636"));
        } else if (!loggedIn) {
            showGuestLoginPrompt();
            configureRoleBadge("Khách đang xem phiên đấu giá", false);
        } else {
            showStandardMessage();
            configureRoleBadge("Bạn tham gia với tư cách Người đấu giá", false);
            messageLabel.setText("Bạn đang ở phòng với tư cách Người đấu giá.");
            messageLabel.setTextFill(Color.web("#0f7a61"));
        }

        if (!isActive) {
            timerLabel.setText(timerInactiveText(session.getStatus()));
            timerLabel.setTextFill(Color.RED);
            if (session.getWinnerName() != null && !session.getWinnerName().isEmpty()) {
                messageLabel.setText("Người thắng: " + session.getWinnerName());
                messageLabel.setTextFill(Color.GOLD);
            }
        } else {
            startCountdown();
        }

        // Load lịch sử bid
        loadBidHistory();

        // Thêm điểm đầu tiên vào biểu đồ
        if (priceSeries.getData().isEmpty()) {
            priceSeries.getData().add(new XYChart.Data<>(formatTime(System.currentTimeMillis()), currentPrice));
        }
    }

    // ========================= COUNTDOWN TIMER =========================

    private void startCountdown() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }
        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateTimer()));
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.play();
    }

    private void updateTimer() {
        long remaining = endTimeMillis - System.currentTimeMillis();
        if (remaining <= 0) {
            timerLabel.setText("00:00:00");
            timerLabel.setTextFill(Color.RED);
            setBidInputsDisabled(true);
            statusLabel.setText("Trạng thái: Đã kết thúc");
            applyStatusStyle("FINISHED");
            if (countdownTimeline != null) countdownTimeline.stop();
            return;
        }

        long hours = remaining / 3600000;
        long minutes = (remaining % 3600000) / 60000;
        long seconds = (remaining % 60000) / 1000;

        String timeStr = String.format("%02d:%02d:%02d", hours, minutes, seconds);
        timerLabel.setText(timeStr);

        // Đổi màu khi gần hết giờ
        if (remaining <= 30000) {
            timerLabel.setTextFill(Color.RED);
        } else if (remaining <= 60000) {
            timerLabel.setTextFill(Color.ORANGE);
        } else {
            timerLabel.setTextFill(Color.web("#27ae60"));
        }
    }

    // ========================= BIDDING =========================

    @FXML
    public void handlePlaceBid() {
        try {
            String input = bidAmountField.getText().replace(",", "").replace(".", "").trim();
            if (input.isEmpty()) {
                showMessage("Vui lòng nhập số tiền!", Color.RED);
                return;
            }

            double bidAmount = Double.parseDouble(input);
            if (!Double.isFinite(bidAmount) || bidAmount <= 0) {
                showMessage("Số tiền phải lớn hơn 0!", Color.RED);
                return;
            }
            if (ServerConnector.currentUser == null) {
                showMessage("Bạn cần đăng nhập trước khi trả giá.", Color.RED);
                return;
            }
            int userId = ServerConnector.currentUser.getId();
            if (userId == currentSellerId) {
                showMessage("Người bán không thể trả giá trong phiên của chính mình.", Color.RED);
                return;
            }

            Response res = connector.placeBid(currentAuctionId, userId, bidAmount);

            if (res != null && "SUCCESS".equals(res.getStatus())) {
                showMessage("Trả giá thành công!", Color.GREEN);
                bidAmountField.clear();
            } else {
                showMessage(res != null ? res.getMessage() : "Lỗi kết nối!", Color.RED);
            }
        } catch (NumberFormatException e) {
            showMessage("Vui lòng nhập số hợp lệ!", Color.RED);
        }
    }

    // ========================= ĐẤU GIÁ TỰ ĐỘNG =========================

    @FXML
    public void handleSetAutoBid() {
        try {
            String maxStr = maxBidField.getText().replace(",", "").replace(".", "").trim();
            String incStr = bidIncrementField.getText().replace(",", "").replace(".", "").trim();

            if (maxStr.isEmpty() || incStr.isEmpty()) {
                autoBidStatusLabel.setText("Vui lòng nhập đầy đủ!");
                autoBidStatusLabel.setTextFill(Color.RED);
                return;
            }

            double maxBid = Double.parseDouble(maxStr);
            double increment = Double.parseDouble(incStr);
            if (!Double.isFinite(maxBid) || !Double.isFinite(increment) || maxBid <= 0 || increment <= 0) {
                autoBidStatusLabel.setText("Giá và bước giá phải lớn hơn 0!");
                autoBidStatusLabel.setTextFill(Color.RED);
                return;
            }
            if (ServerConnector.currentUser == null) {
                autoBidStatusLabel.setText("Bạn cần đăng nhập trước khi bật đấu giá tự động!");
                autoBidStatusLabel.setTextFill(Color.RED);
                return;
            }
            int userId = ServerConnector.currentUser.getId();
            if (userId == currentSellerId) {
                autoBidStatusLabel.setText("Người bán không thể bật đấu giá tự động trong phiên của chính mình.");
                autoBidStatusLabel.setTextFill(Color.RED);
                return;
            }

            if (maxBid <= currentPrice) {
                autoBidStatusLabel.setText("Giá tối đa phải lớn hơn giá hiện tại!");
                autoBidStatusLabel.setTextFill(Color.RED);
                return;
            }

            Response res = connector.setAutoBid(currentAuctionId, userId, maxBid, increment);
            if (res != null && "SUCCESS".equals(res.getStatus())) {
                autoBidStatusLabel.setText("Đấu giá tự động đang hoạt động");
                autoBidStatusLabel.setTextFill(Color.GREEN);
            } else {
                autoBidStatusLabel.setText("Lỗi: " + (res != null ? res.getMessage() : ""));
                autoBidStatusLabel.setTextFill(Color.RED);
            }
        } catch (NumberFormatException e) {
            autoBidStatusLabel.setText("Số không hợp lệ!");
            autoBidStatusLabel.setTextFill(Color.RED);
        }
    }

    @FXML
    public void handleCancelAutoBid() {
        if (ServerConnector.currentUser == null) {
            autoBidStatusLabel.setText("Bạn cần đăng nhập trước khi tắt đấu giá tự động!");
            autoBidStatusLabel.setTextFill(Color.RED);
            return;
        }
        int userId = ServerConnector.currentUser.getId();
        Response res = connector.cancelAutoBid(currentAuctionId, userId);
        if (res != null && "SUCCESS".equals(res.getStatus())) {
            autoBidStatusLabel.setText("Đã tắt đấu giá tự động");
            autoBidStatusLabel.setTextFill(Color.GRAY);
        }
    }

    @FXML
    public void handleBackToDashboard() {
        cleanup();
        SceneNavigator.showDashboard();
    }

    @FXML
    public void handleExit() {
        handleBackToDashboard();
    }

    @FXML
    public void handleGuestLogin() {
        cleanup();
        SceneNavigator.showLogin();
    }

    // ========================= BID HISTORY =========================

    private void loadBidHistory() {
        bidHistoryList.clear();
        List<Bid> history = connector.getBidHistory(currentAuctionId);
        bidHistoryList.addAll(history);

        if (!bidHistoryList.isEmpty()) {
            bidHistoryFeed.scrollTo(0);
        }

        // Cập nhật biểu đồ từ lịch sử
        priceSeries.getData().clear();
        List<Bid> reversed = new java.util.ArrayList<>(history);
        Collections.reverse(reversed);
        for (Bid bid : reversed) {
            long bidTime = bid.getBidTime() != null ? bid.getBidTime().getTime() : System.currentTimeMillis();
            priceSeries.getData().add(new XYChart.Data<>(formatTime(bidTime), bid.getBidAmount()));
        }
    }

    private void setupBidHistoryTable() {
        bidHistoryFeed.setItems(bidHistoryList);
        bidHistoryFeed.setCellFactory(listView -> new ListCell<Bid>() {
            @Override
            protected void updateItem(Bid bid, boolean empty) {
                super.updateItem(bid, empty);
                if (empty || bid == null) {
                    setGraphic(null);
                    setText(null);
                    getStyleClass().removeAll("bid-history-cell");
                } else {
                    HBox box = new HBox(10);
                    box.setPadding(new Insets(10));
                    box.getStyleClass().add("bid-history-row");

                    VBox content = new VBox(5);
                    content.setPadding(new Insets(10));
                    content.getStyleClass().add("bid-history-bubble");

                    Label nameLabel = new Label(bid.getBidderName() != null ? bid.getBidderName() : "Người dùng #" + bid.getUserId());
                    nameLabel.getStyleClass().add("bid-history-name");

                    Label amountLabel = new Label(String.format("%,.0f VNĐ", bid.getBidAmount()));
                    amountLabel.getStyleClass().add("bid-history-amount");

                    Label timeLabel = new Label(bid.getBidTime() != null ? new SimpleDateFormat("HH:mm:ss").format(bid.getBidTime()) : "");
                    timeLabel.getStyleClass().add("bid-history-time");

                    boolean isCurrentUser = ServerConnector.currentUser != null && bid.getUserId() == ServerConnector.currentUser.getId();

                    if (isCurrentUser) {
                        content.getStyleClass().add("bid-history-bubble-own");
                        amountLabel.getStyleClass().add("bid-history-amount-own");
                        box.setAlignment(Pos.CENTER_RIGHT);
                        nameLabel.setText("Bạn");
                    } else {
                        box.setAlignment(Pos.CENTER_LEFT);
                    }

                    content.getChildren().addAll(nameLabel, amountLabel, timeLabel);
                    box.getChildren().add(content);
                    setGraphic(box);
                }
            }
        });
    }



    // ========================= Observer Pattern =========================

    @Override
    public void onBidUpdate(AuctionEvent event) {
        if (event.getAuctionId() == currentAuctionId) {
            currentPrice = event.getNewPrice();
            currentPriceLabel.setText(String.format("%,.0f VNĐ", currentPrice));

            // Thêm điểm mới vào biểu đồ (realtime)
            // Refresh lịch sử bid
            loadBidHistory();

            showMessage(event.getBidderName() + " vừa trả giá " +
                    String.format("%,.0f VNĐ", event.getNewPrice()), Color.BLUE);
        }
    }

    @Override
    public void onAuctionEnded(AuctionEvent event) {
        if (event.getAuctionId() == currentAuctionId) {
            setBidInputsDisabled(true);
            timerLabel.setText("ĐÃ KẾT THÚC");
            timerLabel.setTextFill(Color.RED);
            statusLabel.setText("Trạng thái: Đã kết thúc");
            applyStatusStyle("FINISHED");
            if (countdownTimeline != null) countdownTimeline.stop();

            showMessage("Phiên kết thúc. Người thắng: " + event.getWinnerName(), Color.GOLD);
        }
    }

    @Override
    public void onAuctionStarted(AuctionEvent event) {
        if (event.getAuctionId() == currentAuctionId) {
            boolean canBid = ServerConnector.currentUser != null && ServerConnector.currentUser.getId() != currentSellerId;
            setBidInputsDisabled(!canBid);
            statusLabel.setText("Trạng thái: Đang diễn ra");
            applyStatusStyle("RUNNING");
            startCountdown();
        }
    }

    @Override
    public void onItemListUpdated(AuctionEvent event) {
        // Không cần xử lý trong AuctionRoom
    }

    @Override
    public void onAuctionExtended(AuctionEvent event) {
        if (event.getAuctionId() == currentAuctionId) {
            endTimeMillis = event.getNewEndTime();
            showMessage("Phiên được gia hạn chống trả giá phút cuối.", Color.ORANGE);
        }
    }

    // ========================= UTILITY =========================

    private void setBidInputsDisabled(boolean disabled) {
        if (bidButton != null) bidButton.setDisable(disabled);
        if (bidAmountField != null) bidAmountField.setDisable(disabled);
        if (maxBidField != null) maxBidField.setDisable(disabled);
        if (bidIncrementField != null) bidIncrementField.setDisable(disabled);
        if (autoBidButton != null) autoBidButton.setDisable(disabled);
        if (cancelAutoBidButton != null) cancelAutoBidButton.setDisable(disabled);
    }

    private void setBidPanelsVisible(boolean visible) {
        setManagedVisible(bidPanel, visible);
        setManagedVisible(bidSeparator, visible);
        setManagedVisible(autoBidPanel, visible);
    }

    private void setManagedVisible(javafx.scene.Node node, boolean visible) {
        if (node == null) return;
        node.setManaged(visible);
        node.setVisible(visible);
    }

    private void configureRoleBadge(String text, boolean seller) {
        if (roomRoleLabel == null) return;
        roomRoleLabel.setText(text);
        roomRoleLabel.getStyleClass().setAll("seller-view-badge", seller ? "seller-view-badge-owner" : "seller-view-badge-viewer");
        roomRoleLabel.setManaged(true);
        roomRoleLabel.setVisible(true);
    }

    private void showGuestLoginPrompt() {
        if (messageLabel != null) {
            messageLabel.setManaged(false);
            messageLabel.setVisible(false);
        }
        if (guestLoginPrompt != null) {
            guestLoginPrompt.setManaged(true);
            guestLoginPrompt.setVisible(true);
        }
    }

    private void showStandardMessage() {
        if (guestLoginPrompt != null) {
            guestLoginPrompt.setManaged(false);
            guestLoginPrompt.setVisible(false);
        }
        if (messageLabel != null) {
            messageLabel.setManaged(true);
            messageLabel.setVisible(true);
        }
    }

    private String displayStatus(String status) {
        return switch (nullToText(status).toUpperCase(Locale.ROOT)) {
            case "RUNNING" -> "Đang diễn ra";
            case "OPEN" -> "Chưa diễn ra";
            case "PAID" -> "Đã thanh toán";
            case "CANCELED" -> "Đã hủy";
            case "FINISHED" -> "Đã kết thúc";
            default -> "Không xác định";
        };
    }

    private String timerInactiveText(String status) {
        return switch (nullToText(status).toUpperCase(Locale.ROOT)) {
            case "OPEN" -> "CHƯA DIỄN RA";
            case "PAID" -> "ĐÃ THANH TOÁN";
            case "CANCELED" -> "ĐÃ HỦY";
            default -> "ĐÃ KẾT THÚC";
        };
    }

    private void applyStatusStyle(String status) {
        if (statusLabel == null) return;
        String styleClass = switch (nullToText(status).toUpperCase(Locale.ROOT)) {
            case "RUNNING" -> "status-running";
            case "OPEN" -> "status-upcoming";
            default -> "status-ended";
        };
        statusLabel.getStyleClass().setAll("status-pill", styleClass);
    }

    private String nullToText(String value) {
        return value == null ? "" : value.trim();
    }

    private void showMessage(String msg, Color color) {
        if (messageLabel != null) {
            messageLabel.setText(msg);
            messageLabel.setTextFill(color);
        }
    }

    private String formatTime(long millis) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        return sdf.format(new java.util.Date(millis));
    }

    private void setProductImage(String path) {
        if (productImageView == null || path == null || path.isBlank()) return;
        try {
            File file = new File(path);
            Image image = file.exists() ? new Image(file.toURI().toString(), 500, 210, true, true) : new Image(path, 500, 210, true, true);
            if (!image.isError()) productImageView.setImage(image);
        } catch (Exception ignored) {
        }
    }

    /**
     * Dọn dẹp khi đóng cửa sổ - hủy đăng ký Observer.
     */
    public void cleanup() {
        connector.removeEventListener(this);
        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }
    }
}
