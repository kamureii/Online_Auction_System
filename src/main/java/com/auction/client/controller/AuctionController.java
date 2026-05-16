package com.auction.client.controller;

import com.auction.client.service.ServerConnector;
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
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;

/**
 * Controller phòng đấu giá trực tuyến.
 * - Countdown timer thực
 * - Realtime bid update (Observer)
 * - Lịch sử bid
 * - Biểu đồ giá (LineChart)
 * - Auto-bid
 */
public class AuctionController implements AuctionEventListener {

    // UI Elements
    @FXML private Label productNameLabel;
    @FXML private Label currentPriceLabel;
    @FXML private Label timerLabel;
    @FXML private Label statusLabel;
    @FXML private Label messageLabel;
    @FXML private TextField bidAmountField;
    @FXML private Button bidButton;

    // Bid History Table
    @FXML private TableView<Bid> bidHistoryTable;
    @FXML private TableColumn<Bid, String> colBidder;
    @FXML private TableColumn<Bid, Double> colAmount;
    @FXML private TableColumn<Bid, Timestamp> colTime;

    // Price Chart
    @FXML private LineChart<String, Number> priceChart;
    @FXML private CategoryAxis xAxis;
    @FXML private NumberAxis yAxis;

    // Auto-bid
    @FXML private TextField maxBidField;
    @FXML private TextField bidIncrementField;
    @FXML private Button autoBidButton;
    @FXML private Button cancelAutoBidButton;
    @FXML private Label autoBidStatusLabel;

    private final ServerConnector connector = ServerConnector.getInstance();
    private final ObservableList<Bid> bidHistoryList = FXCollections.observableArrayList();
    private XYChart.Series<String, Number> priceSeries;

    private int currentAuctionId;
    private double currentPrice;
    private long endTimeMillis;
    private Timeline countdownTimeline;

    @FXML
    public void initialize() {
        // Cấu hình bảng lịch sử bid
        colBidder.setCellValueFactory(new PropertyValueFactory<>("bidderName"));
        colAmount.setCellValueFactory(new PropertyValueFactory<>("bidAmount"));
        colTime.setCellValueFactory(new PropertyValueFactory<>("bidTime"));

        colAmount.setCellFactory(col -> new TableCell<Bid, Double>() {
            @Override
            protected void updateItem(Double val, boolean empty) {
                super.updateItem(val, empty);
                setText(empty || val == null ? "" : String.format("%,.0f VNĐ", val));
            }
        });

        colTime.setCellFactory(col -> new TableCell<Bid, Timestamp>() {
            private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss dd/MM");
            @Override
            protected void updateItem(Timestamp val, boolean empty) {
                super.updateItem(val, empty);
                setText(empty || val == null ? "" : sdf.format(val));
            }
        });

        bidHistoryTable.setItems(bidHistoryList);

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
        this.currentPrice = session.getCurrentHighestBid();
        this.endTimeMillis = session.getEndTime().getTime();

        productNameLabel.setText(session.getItemName());
        currentPriceLabel.setText(String.format("%,.0f VNĐ", currentPrice));
        statusLabel.setText("Trạng thái: " + session.getStatus());

        boolean isActive = "RUNNING".equals(session.getStatus());
        bidButton.setDisable(!isActive);
        bidAmountField.setDisable(!isActive);

        if (!isActive) {
            timerLabel.setText("ĐÃ KẾT THÚC");
            timerLabel.setTextFill(Color.RED);
            if (session.getWinnerName() != null && !session.getWinnerName().isEmpty()) {
                messageLabel.setText("🏆 Người thắng: " + session.getWinnerName());
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
        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateTimer()));
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.play();
    }

    private void updateTimer() {
        long remaining = endTimeMillis - System.currentTimeMillis();
        if (remaining <= 0) {
            timerLabel.setText("00:00:00");
            timerLabel.setTextFill(Color.RED);
            bidButton.setDisable(true);
            bidAmountField.setDisable(true);
            statusLabel.setText("Trạng thái: FINISHED");
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
            int userId = ServerConnector.currentUser != null ? ServerConnector.currentUser.getId() : 1;

            Response res = connector.placeBid(currentAuctionId, userId, bidAmount);

            if (res != null && "SUCCESS".equals(res.getStatus())) {
                showMessage("Đặt giá thành công!", Color.GREEN);
                bidAmountField.clear();
            } else {
                showMessage(res != null ? res.getMessage() : "Lỗi kết nối!", Color.RED);
            }
        } catch (NumberFormatException e) {
            showMessage("Vui lòng nhập số hợp lệ!", Color.RED);
        }
    }

    // ========================= AUTO-BID =========================

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
            int userId = ServerConnector.currentUser != null ? ServerConnector.currentUser.getId() : 1;

            if (maxBid <= currentPrice) {
                autoBidStatusLabel.setText("Giá tối đa phải lớn hơn giá hiện tại!");
                autoBidStatusLabel.setTextFill(Color.RED);
                return;
            }

            Response res = connector.setAutoBid(currentAuctionId, userId, maxBid, increment);
            if (res != null && "SUCCESS".equals(res.getStatus())) {
                autoBidStatusLabel.setText("✅ Auto-bid đang hoạt động");
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
        int userId = ServerConnector.currentUser != null ? ServerConnector.currentUser.getId() : 1;
        Response res = connector.cancelAutoBid(currentAuctionId, userId);
        if (res != null && "SUCCESS".equals(res.getStatus())) {
            autoBidStatusLabel.setText("❌ Auto-bid đã hủy");
            autoBidStatusLabel.setTextFill(Color.GRAY);
        }
    }

    // ========================= BID HISTORY =========================

    private void loadBidHistory() {
        bidHistoryList.clear();
        List<Bid> history = connector.getBidHistory(currentAuctionId);
        bidHistoryList.addAll(history);

        // Cập nhật biểu đồ từ lịch sử
        priceSeries.getData().clear();
        List<Bid> reversed = new java.util.ArrayList<>(history);
        Collections.reverse(reversed);
        for (Bid bid : reversed) {
            long bidTime = bid.getBidTime() != null ? bid.getBidTime().getTime() : System.currentTimeMillis();
            priceSeries.getData().add(new XYChart.Data<>(formatTime(bidTime), bid.getBidAmount()));
        }
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

            showMessage("💰 " + event.getBidderName() + " trả giá " +
                    String.format("%,.0f VNĐ", event.getNewPrice()), Color.BLUE);
        }
    }

    @Override
    public void onAuctionEnded(AuctionEvent event) {
        if (event.getAuctionId() == currentAuctionId) {
            bidButton.setDisable(true);
            bidAmountField.setDisable(true);
            timerLabel.setText("ĐÃ KẾT THÚC");
            timerLabel.setTextFill(Color.RED);
            statusLabel.setText("Trạng thái: FINISHED");
            if (countdownTimeline != null) countdownTimeline.stop();

            showMessage("🏆 Phiên kết thúc! Người thắng: " + event.getWinnerName(), Color.GOLD);
        }
    }

    @Override
    public void onAuctionStarted(AuctionEvent event) {
        if (event.getAuctionId() == currentAuctionId) {
            bidButton.setDisable(false);
            bidAmountField.setDisable(false);
            statusLabel.setText("Trạng thái: RUNNING");
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
            showMessage("⏰ Phiên được gia hạn (Anti-sniping)!", Color.ORANGE);
        }
    }

    // ========================= UTILITY =========================

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
