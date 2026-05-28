package com.auction.client.controller;

import com.auction.client.navigation.SceneNavigator;
import com.auction.client.service.ServerConnector;
import com.auction.client.ui.RealtimeToast;
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
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.io.File;
import java.sql.Timestamp;
import java.util.ArrayList;
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
    @FXML private StackPane rootStack;
    @FXML private Label productNameLabel;
    @FXML private Label currentPriceLabel;
    @FXML private Label timerLabel;
    @FXML private Label statusLabel;
    @FXML private Label messageLabel;
    @FXML private Label productDetailsLabel;
    @FXML private Label bidCountLabel;
    @FXML private Label chartBidCountLabel;
    @FXML private Label roomRoleLabel;
    @FXML private Label roomCodeLabel;
    @FXML private Label minIncrementLabel;
    @FXML private Label timerSummaryLabel;
    @FXML private Label openingPriceLabel;
    @FXML private Label chartCurrentPriceLabel;
    @FXML private ImageView productImageView;
    @FXML private StackPane productImageFrame;
    @FXML private VBox productImageFallback;
    @FXML private TextFlow guestLoginPrompt;
    @FXML private Text guestLoginLink;
    @FXML private TextField bidAmountField;
    @FXML private Button bidButton;
    @FXML private Button exitButton;
    @FXML private HBox bidPanel;
    @FXML private Separator bidSeparator;

    // Bid History Table
    @FXML private TableView<Bid> bidHistoryTable;
    @FXML private TableColumn<Bid, String> colBidder;
    @FXML private TableColumn<Bid, Double> colAmount;
    @FXML private TableColumn<Bid, Timestamp> colTime;
    @FXML private ListView<Bid> bidHistoryFeed;

    // Price Chart
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
    private double startingPrice;
    private double minIncrement;
    private int currentBidCount;
    private long endTimeMillis;
    private Timeline countdownTimeline;
    private static final List<String> AUCTION_STATE_CLASSES = List.of(
            "auction-state-success",
            "auction-state-error",
            "auction-state-warning",
            "auction-state-info",
            "auction-state-muted");

    @FXML
    public void initialize() {
        // Cấu hình bảng lịch sử bid
        if (colBidder != null) colBidder.setCellValueFactory(new PropertyValueFactory<>("bidderName"));
        if (colAmount != null) colAmount.setCellValueFactory(new PropertyValueFactory<>("bidAmount"));
        if (colTime != null) colTime.setCellValueFactory(new PropertyValueFactory<>("bidTime"));

        if (colAmount != null) colAmount.setCellFactory(col -> new TableCell<Bid, Double>() {
            @Override
            protected void updateItem(Double val, boolean empty) {
                super.updateItem(val, empty);
                setText(empty || val == null ? "" : String.format("%,.0f VNĐ", val));
            }
        });

        if (colTime != null) colTime.setCellFactory(col -> new TableCell<Bid, Timestamp>() {
            private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss dd/MM");
            @Override
            protected void updateItem(Timestamp val, boolean empty) {
                super.updateItem(val, empty);
                setText(empty || val == null ? "" : sdf.format(val));
            }
        });

        if (bidHistoryTable != null) {
            bidHistoryTable.setItems(bidHistoryList);
        }
        if (bidHistoryFeed != null) {
            bidHistoryFeed.setItems(bidHistoryList);
            bidHistoryFeed.setCellFactory(list -> new ListCell<>() {
                @Override
                protected void updateItem(Bid bid, boolean empty) {
                    super.updateItem(bid, empty);
                    if (empty || bid == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    setText(null);
                    setGraphic(createBidHistoryRow(bid, getIndex()));
                }
            });
        }

        // Cấu hình biểu đồ
        priceSeries = new XYChart.Series<>();
        priceSeries.setName("Giá đấu");
        priceChart.setLegendVisible(true);
        priceChart.getData().add(priceSeries);
        priceChart.setCreateSymbols(true);
        priceChart.setAnimated(false);
        xAxis.setTickLabelRotation(0);
        yAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number value) {
                double amount = value == null ? 0 : value.doubleValue();
                if (amount >= 1_000_000) {
                    return String.format(Locale.US, "%.1fM", amount / 1_000_000).replace(".0", "");
                }
                if (amount >= 1_000) {
                    return String.format(Locale.US, "%.0fK", amount / 1_000);
                }
                return String.format(Locale.US, "%.0f", amount);
            }

            @Override
            public Number fromString(String string) {
                return 0;
            }
        });

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
        this.startingPrice = session.getStartingPrice() > 0 ? session.getStartingPrice() : currentPrice;
        this.minIncrement = session.getMinIncrement() > 0 ? session.getMinIncrement() : 500_000;
        this.currentBidCount = Math.max(0, session.getBidCount());
        this.endTimeMillis = session.getEndTime().getTime();

        productNameLabel.setText(fallbackText(session.getItemName(), "iP17"));
        productDetailsLabel.setText(fallbackText(session.getItemDescription(),
                "iP17 – Smartphone cao cấp với hiệu năng vượt trội, dung lượng lớn, camera chuyên nghiệp và thiết kế sang trọng. Bảo hành chính hãng 12 tháng."));
        roomCodeLabel.setText(roomCode(session));
        minIncrementLabel.setText(formatMoney(minIncrement));
        if (bidIncrementField != null && bidIncrementField.getText().isBlank()) {
            bidIncrementField.setText(String.format(Locale.US, "%,.0f", minIncrement));
        }
        loadProductImage(session.getItemImagePath());
        updatePriceLabels();
        updateBidCountLabels();
        statusLabel.setText("Trạng thái: " + session.getStatus());
        applyStatusStyle(session.getStatus());

        boolean isActive = "RUNNING".equals(session.getStatus());
        boolean loggedIn = ServerConnector.currentUser != null;
        boolean isSeller = loggedIn && ServerConnector.currentUser.getId() == currentSellerId;
        boolean canBid = isActive && loggedIn && !isSeller;
        bidButton.setDisable(!canBid);
        bidAmountField.setDisable(!canBid);
        maxBidField.setDisable(!canBid);
        bidIncrementField.setDisable(!canBid);
        autoBidButton.setDisable(!canBid);
        cancelAutoBidButton.setDisable(!canBid);

        if (isSeller) {
            messageLabel.setText("Bạn đang ở phòng với tư cách Người bán.");
            setLabelState(messageLabel, "auction-state-warning");
            configureRoleBadge("Người bán", true);
        } else if (!loggedIn) {
            messageLabel.setText("Đăng nhập để tham gia phòng với tư cách Người đấu giá.");
            setLabelState(messageLabel, "auction-state-muted");
            setManagedVisible(guestLoginPrompt, true);
        } else {
            messageLabel.setText("Bạn đang ở phòng với tư cách Người đấu giá.");
            setLabelState(messageLabel, "auction-state-success");
            configureRoleBadge("Người đấu giá", false);
            setManagedVisible(guestLoginPrompt, false);
        }

        if (!isActive) {
            setTimerText(timerInactiveText(session.getStatus()));
            setTimerState("auction-state-error");
            if (session.getWinnerName() != null && !session.getWinnerName().isEmpty()) {
                messageLabel.setText("🏆 Người thắng: " + session.getWinnerName());
                setLabelState(messageLabel, "auction-state-warning");
            }
        } else {
            startCountdown();
        }

        // Load lịch sử bid
        loadBidHistory();

        // Thêm điểm đầu tiên vào biểu đồ
        if (priceSeries.getData().isEmpty()) {
            seedChartFallback();
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
            setTimerText("00:00:00");
            setTimerState("auction-state-error");
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
        setTimerText(timeStr);

        // Đổi màu khi gần hết giờ
        if (remaining <= 30000) {
            setTimerState("auction-state-error");
        } else if (remaining <= 60000) {
            setTimerState("auction-state-warning");
        } else {
            setTimerState("auction-state-success");
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
                setLabelState(autoBidStatusLabel, "auction-state-error");
                return;
            }

            double maxBid = Double.parseDouble(maxStr);
            double increment = Double.parseDouble(incStr);
            if (!Double.isFinite(maxBid) || !Double.isFinite(increment) || maxBid <= 0 || increment <= 0) {
                autoBidStatusLabel.setText("Giá và bước giá phải lớn hơn 0!");
                setLabelState(autoBidStatusLabel, "auction-state-error");
                return;
            }
            if (ServerConnector.currentUser == null) {
                autoBidStatusLabel.setText("Bạn cần đăng nhập trước khi bật đấu giá tự động!");
                setLabelState(autoBidStatusLabel, "auction-state-error");
                return;
            }
            int userId = ServerConnector.currentUser.getId();
            if (userId == currentSellerId) {
                autoBidStatusLabel.setText("Người bán không thể bật đấu giá tự động trong phiên của chính mình.");
                setLabelState(autoBidStatusLabel, "auction-state-error");
                return;
            }

            if (maxBid <= currentPrice) {
                autoBidStatusLabel.setText("Giá tối đa phải lớn hơn giá hiện tại!");
                setLabelState(autoBidStatusLabel, "auction-state-error");
                return;
            }

            Response res = connector.setAutoBid(currentAuctionId, userId, maxBid, increment);
            if (res != null && "SUCCESS".equals(res.getStatus())) {
                autoBidStatusLabel.setText("Đấu giá tự động đang hoạt động");
                setLabelState(autoBidStatusLabel, "auction-state-success");
                setAutoBidActive(true);
            } else {
                autoBidStatusLabel.setText("Lỗi: " + (res != null ? res.getMessage() : ""));
                setLabelState(autoBidStatusLabel, "auction-state-error");
                setAutoBidActive(false);
            }
        } catch (NumberFormatException e) {
            autoBidStatusLabel.setText("Số không hợp lệ!");
            setLabelState(autoBidStatusLabel, "auction-state-error");
            setAutoBidActive(false);
        }
    }

    @FXML
    public void handleCancelAutoBid() {
        if (ServerConnector.currentUser == null) {
            autoBidStatusLabel.setText("Bạn cần đăng nhập trước khi tắt đấu giá tự động!");
            setLabelState(autoBidStatusLabel, "auction-state-error");
            return;
        }
        int userId = ServerConnector.currentUser.getId();
        Response res = connector.cancelAutoBid(currentAuctionId, userId);
        if (res != null && "SUCCESS".equals(res.getStatus())) {
            autoBidStatusLabel.setText("Đã tắt đấu giá tự động");
            setLabelState(autoBidStatusLabel, "auction-state-muted");
            setAutoBidActive(false);
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
        currentBidCount = Math.max(currentBidCount, history.size());
        updateBidCountLabels();

        // Cập nhật biểu đồ từ lịch sử
        priceSeries.getData().clear();
        List<Bid> reversed = new ArrayList<>(history);
        Collections.reverse(reversed);
        for (Bid bid : reversed) {
            long bidTime = bid.getBidTime() != null ? bid.getBidTime().getTime() : System.currentTimeMillis();
            priceSeries.getData().add(new XYChart.Data<>(formatTime(bidTime), bid.getBidAmount()));
        }
        if (priceSeries.getData().isEmpty()) {
            seedChartFallback();
        }
    }

    // ========================= Observer Pattern =========================

    @Override
    public void onBidUpdate(AuctionEvent event) {
        if (event.getAuctionId() == currentAuctionId) {
            currentPrice = event.getNewPrice();
            currentBidCount++;
            updatePriceLabels();
            updateBidCountLabels();

            // Thêm điểm mới vào biểu đồ (realtime)
            // Refresh lịch sử bid
            loadBidHistory();
            RealtimeToast.showBid(rootStack, event.getBidderName(), event.getNewPrice());

            showMessage(event.getBidderName() + " vừa trả giá " +
                    String.format("%,.0f VNĐ", event.getNewPrice()), Color.BLUE);
        }
    }

    @Override
    public void onAuctionEnded(AuctionEvent event) {
        if (event.getAuctionId() == currentAuctionId) {
            setBidInputsDisabled(true);
            setTimerText("ĐÃ KẾT THÚC");
            setTimerState("auction-state-error");
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

    private void setAutoBidActive(boolean active) {
        if (autoBidPanel == null) return;
        autoBidPanel.getStyleClass().remove("auto-bid-panel-active");
        if (active) {
            autoBidPanel.getStyleClass().add("auto-bid-panel-active");
        }
        if (autoBidButton != null) {
            autoBidButton.setDisable(active);
        }
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
            case "RUNNING" -> "auction-status-badge-running";
            case "OPEN" -> "auction-status-badge-upcoming";
            default -> "auction-status-badge-ended";
        };
        statusLabel.getStyleClass().setAll("auction-status-badge", styleClass);
    }

    private String nullToText(String value) {
        return value == null ? "" : value.trim();
    }

    private String fallbackText(String value, String fallback) {
        String safe = nullToText(value);
        return safe.isBlank() ? fallback : safe;
    }

    private String formatMoney(double amount) {
        return String.format("%,.0f VNĐ", amount);
    }

    private String roomCode(AuctionSession session) {
        String name = fallbackText(session.getItemName(), "IP17")
                .replaceAll("[^A-Za-z0-9]", "")
                .toUpperCase(Locale.ROOT);
        if (name.length() > 4) {
            name = name.substring(0, 4);
        }
        return name + "-" + String.format("%04d", session.getId());
    }

    private void updatePriceLabels() {
        String current = formatMoney(currentPrice);
        if (currentPriceLabel != null) currentPriceLabel.setText(current);
        if (chartCurrentPriceLabel != null) chartCurrentPriceLabel.setText(current);
        if (openingPriceLabel != null) openingPriceLabel.setText(formatMoney(startingPrice > 0 ? startingPrice : currentPrice));
    }

    private void updateBidCountLabels() {
        String text = currentBidCount + " lượt";
        if (bidCountLabel != null) bidCountLabel.setText(text);
        if (chartBidCountLabel != null) chartBidCountLabel.setText(text);
    }

    private void setTimerText(String text) {
        if (timerLabel != null) timerLabel.setText(text);
        if (timerSummaryLabel != null) timerSummaryLabel.setText(text);
    }

    private void loadProductImage(String imagePath) {
        Image image = imageFromPath(imagePath);
        boolean hasImage = image != null && !image.isError();
        if (productImageView != null) {
            productImageView.setImage(hasImage ? image : null);
            productImageView.setVisible(hasImage);
            productImageView.setManaged(hasImage);
        }
        setManagedVisible(productImageFallback, !hasImage);
    }

    private Image imageFromPath(String imagePath) {
        String safePath = nullToText(imagePath);
        if (safePath.isBlank()) {
            return null;
        }
        try {
            File file = new File(safePath);
            if (file.exists()) {
                return new Image(file.toURI().toString(), 640, 460, true, true);
            }
            var resource = getClass().getResource(safePath.startsWith("/") ? safePath : "/" + safePath);
            if (resource != null) {
                return new Image(resource.toExternalForm(), 640, 460, true, true);
            }
            return new Image(safePath, 640, 460, true, true);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Node createBidHistoryRow(Bid bid, int index) {
        HBox row = new HBox(14);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("bid-history-row");
        row.setPadding(new Insets(0, 16, 0, 16));

        String bidder = fallbackText(bid.getBidderName(), "Người dùng #" + bid.getUserId());
        Label avatar = new Label(initials(bidder));
        avatar.getStyleClass().addAll("bid-history-avatar", avatarClass(index));

        Label name = new Label(bidder);
        name.getStyleClass().add("bid-history-name");
        HBox bidderBox = new HBox(11, avatar, name);
        bidderBox.setAlignment(Pos.CENTER_LEFT);
        bidderBox.setPrefWidth(260);

        Label amount = new Label(formatMoney(bid.getBidAmount()));
        amount.getStyleClass().add("bid-history-amount");
        amount.setPrefWidth(190);

        Label time = new Label(bid.getBidTime() == null ? "--:--:--" : formatTime(bid.getBidTime().getTime()));
        time.getStyleClass().add("bid-history-time");
        time.setPrefWidth(160);

        Label status = new Label(statusText(index));
        status.getStyleClass().addAll("bid-status-chip", statusClass(index));
        HBox.setHgrow(status, Priority.ALWAYS);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().addAll(bidderBox, amount, time, spacer, status);
        return row;
    }

    private String initials(String name) {
        String[] parts = fallbackText(name, "ND").split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!part.isBlank()) {
                builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            }
            if (builder.length() == 2) break;
        }
        return builder.length() == 0 ? "ND" : builder.toString();
    }

    private String avatarClass(int index) {
        return switch (Math.floorMod(index, 4)) {
            case 0 -> "bid-history-avatar-red";
            case 1 -> "bid-history-avatar-purple";
            case 2 -> "bid-history-avatar-blue";
            default -> "bid-history-avatar-orange";
        };
    }

    private String statusText(int index) {
        if (index == 0) return "↗  Đang dẫn";
        if (index == 1) return "↘  Bị vượt";
        return "✓  Hợp lệ";
    }

    private String statusClass(int index) {
        if (index == 0) return "bid-status-leading";
        if (index == 1) return "bid-status-outbid";
        return "bid-status-valid";
    }

    private void seedChartFallback() {
        double end = currentPrice > 0 ? currentPrice : 20_000_000;
        double start = startingPrice > 0 ? startingPrice : Math.max(1_000_000, end * 0.75);
        double[] factors = {0, 0, 0.10, 0.10, 0.20, 0.25, 0.38, 0.50, 0.72, 1.00};
        String[] times = {"13:30", "13:35", "13:40", "13:55", "14:00", "14:10", "14:15", "14:20", "14:25", "14:30"};
        priceSeries.getData().clear();
        for (int i = 0; i < times.length; i++) {
            double price = start + (end - start) * factors[i];
            priceSeries.getData().add(new XYChart.Data<>(times[i], price));
        }
    }

    private void setTimerState(String stateClass) {
        setLabelState(timerLabel, stateClass);
        setLabelState(timerSummaryLabel, stateClass);
    }

    private void setLabelState(Label label, String stateClass) {
        if (label == null) {
            return;
        }
        label.getStyleClass().removeAll(AUCTION_STATE_CLASSES);
        if (stateClass != null && !stateClass.isBlank()) {
            label.getStyleClass().add(stateClass);
        }
        label.setTextFill(null);
    }

    private String stateClassForColor(Color color) {
        if (Color.RED.equals(color)) {
            return "auction-state-error";
        }
        if (Color.GREEN.equals(color)) {
            return "auction-state-success";
        }
        if (Color.ORANGE.equals(color) || Color.GOLD.equals(color)) {
            return "auction-state-warning";
        }
        if (Color.BLUE.equals(color)) {
            return "auction-state-info";
        }
        if (Color.GRAY.equals(color)) {
            return "auction-state-muted";
        }
        return "auction-state-muted";
    }

    private void showMessage(String msg, Color color) {
        if (messageLabel != null) {
            messageLabel.setText(msg);
            setLabelState(messageLabel, stateClassForColor(color));
        }
    }

    private String formatTime(long millis) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        return sdf.format(new java.util.Date(millis));
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
