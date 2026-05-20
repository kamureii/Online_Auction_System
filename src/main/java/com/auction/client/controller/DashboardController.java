package com.auction.client.controller;

import com.auction.client.navigation.SceneNavigator;
import com.auction.client.service.ServerConnector;
import com.auction.client.ui.AiChatWidget;
import com.auction.client.util.VietnamAddressData;
import com.auction.shared.dto.PaymentProfileDTO;
import com.auction.shared.dto.ProfileDTO;
import com.auction.shared.model.AuctionSession;
import com.auction.shared.model.CartItem;
import com.auction.shared.model.Notification;
import com.auction.shared.model.User;
import com.auction.shared.network.Response;
import com.auction.shared.observer.AuctionEvent;
import com.auction.shared.observer.AuctionEventListener;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.DoubleBinding;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class DashboardController implements AuctionEventListener {
    private static final String HERO_LIGHT_RESOURCE = "/assets/bidshift-hero-light.png";
    private static final String HERO_DARK_RESOURCE = "/assets/bidshift-hero-dark.png";
    private static final double HERO_IMAGE_SOURCE_WIDTH = 1672;
    private static final double HERO_IMAGE_SOURCE_HEIGHT = 941;
    private static final double HERO_IMAGE_RATIO = HERO_IMAGE_SOURCE_HEIGHT / HERO_IMAGE_SOURCE_WIDTH;
    private static final double HERO_IMAGE_MAX_WIDTH = 1672;
    private static final double HERO_IMAGE_SIDE_GUTTER = 88;
    private static final double HERO_IMAGE_MIN_WIDTH = 320;

    @FXML
    private StackPane rootStack;
    @FXML
    private BorderPane dashboardPane;
    @FXML
    private StackPane contentHost;
    @FXML
    private StackPane overlayHost;
    @FXML
    private StackPane notificationPopoverHost;
    @FXML
    private Label toastLabel;
    @FXML
    private TextField searchField;
    @FXML
    private HBox searchBar;
    @FXML
    private ImageView brandLogoImage;
    @FXML
    private Button themeToggleBtn;
    @FXML
    private Button addItemBtn;
    @FXML
    private Button notificationBtn;
    @FXML
    private Button adminBtn;
    @FXML
    private Button accountButton;

    private final ObservableList<AuctionSession> auctionList = FXCollections.observableArrayList();
    private final ServerConnector connector = ServerConnector.getInstance();
    private String selectedCategory = "ALL";
    private String selectedStatus = "ALL";
    private boolean isDarkMode;
    private boolean showingAccountHub;
    private List<CartItem> preloadedCartItems;
    private final List<FeaturedCountdown> featuredCountdowns = new ArrayList<>();
    private Timeline featuredCountdownTimeline;

    @FXML
    public void initialize() {
        isDarkMode = SceneNavigator.isDarkMode();
        updateThemeToggleButton();
        hideOverlay();
        hideNotificationPopover();
        AiChatWidget.attachTo(rootStack);
        setupHeader();
        searchField.textProperty().addListener((obs, oldValue, newValue) -> {
            if (!showingAccountHub)
                renderHome();
        });
        connector.addEventListener(this);
        Platform.runLater(this::refreshAuctionList);
    }

    private void setupHeader() {
        User currentUser = ServerConnector.currentUser;
        boolean loggedIn = currentUser != null;
        accountButton.setText(
                loggedIn ? nullToText(currentUser.getUsername(), currentUser.getFullName())
                        : "Đăng nhập / Đăng ký   →");
        addItemBtn.setVisible(loggedIn);
        addItemBtn.setManaged(loggedIn);
        boolean admin = loggedIn && "ADMIN".equalsIgnoreCase(currentUser.getRole());
        adminBtn.setVisible(admin);
        adminBtn.setManaged(admin);
        updateBrandLogo();
        updateThemeToggleButton();
        updateNotificationBadge();
    }

    private void updateThemeToggleButton() {
        if (themeToggleBtn != null) {
            themeToggleBtn.setText(isDarkMode ? "☀" : "☾");
            themeToggleBtn.setAccessibleText(isDarkMode ? "Chuyển sang light mode" : "Chuyển sang dark mode");
        }
    }

    private void updateBrandLogo() {
        if (brandLogoImage == null)
            return;
        String logoPath = isDarkMode ? "/assets/bidshift-logo-dark.png" : "/assets/bidshift-logo.png";
        var url = getClass().getResource(logoPath);
        if (url != null) {
            brandLogoImage.setImage(new Image(url.toExternalForm()));
        }
    }

    @FXML
    private void handleHome() {
        hideNotificationPopover();
        selectedCategory = "ALL";
        selectedStatus = "ALL";
        refreshAuctionList();
    }

    @FXML
    private void handleShowAllAssets() {
        setFilter("ALL", "ALL");
    }

    @FXML
    private void handleCategoryElectronics() {
        setFilter("ELECTRONICS", "ALL");
    }

    @FXML
    private void handleCategoryArt() {
        setFilter("ART", "ALL");
    }

    @FXML
    private void handleCategoryVehicle() {
        setFilter("VEHICLE", "ALL");
    }

    @FXML
    private void handleCategoryOther() {
        setFilter("OTHER", "ALL");
    }

    @FXML
    private void handleUpcomingAuctions() {
        setFilter(selectedCategory, "OPEN");
    }

    @FXML
    private void handleRunningAuctions() {
        setFilter(selectedCategory, "RUNNING");
    }

    @FXML
    private void handleEndedAuctions() {
        setFilter(selectedCategory, "ENDED");
    }

    private void setFilter(String category, String status) {
        hideNotificationPopover();
        selectedCategory = category;
        selectedStatus = status;
        refreshAuctionList();
    }

    @FXML
    private void handleRefresh() {
        hideNotificationPopover();
        refreshAuctionList();
        showToast("Danh sách đấu giá đã được làm mới.", true);
    }

    @FXML
    private void handleAbout() {
        hideNotificationPopover();
        stopFeaturedCountdowns();
        selectedCategory = "ALL";
        selectedStatus = "ALL";
        showingAccountHub = false;
        contentHost.getChildren().setAll(wrapScroll(new VBox(18,
                sectionTitle("Giới thiệu BidShift"),
                infoPanel(
                        "BidShift là sàn đấu giá trực tuyến nơi mỗi tài khoản có thể đăng bán hoặc tham gia trả giá theo từng phòng. Vai trò được xác định trong phiên đấu giá, không khóa cứng ở tài khoản."),
                infoPanel(
                        "Cảm hứng sản phẩm: dịch chuyển cuộc đấu giá bằng từng bid, giống như một bit shift nhưng dành cho đấu giá."))));
    }

    @FXML
    private void handleContact() {
        hideNotificationPopover();
        stopFeaturedCountdowns();
        showingAccountHub = false;
        contentHost.getChildren().setAll(wrapScroll(new VBox(18,
                sectionTitle("Liên hệ"),
                infoPanel("Email: support@bidshift.local\nHotline: 024.0000.0000\nĐịa chỉ: Hà Nội, Việt Nam"))));
    }

    @FXML
    private void handleThemeToggle() {
        SceneNavigator.toggleTheme();
        leaveDashboard();
        SceneNavigator.showDashboard();
    }

    @FXML
    private void handleToggleSearch() {
        boolean visible = !searchBar.isVisible();
        searchBar.setVisible(visible);
        searchBar.setManaged(visible);
        if (visible)
            searchField.requestFocus();
    }

    @FXML
    private void handleLanguage() {
        hideNotificationPopover();
        showToast("Ngôn ngữ hiện tại: Tiếng Việt.", true);
    }

    @FXML
    private void handleAccountAction() {
        hideNotificationPopover();
        if (ServerConnector.currentUser == null) {
            SceneNavigator.showLogin();
            return;
        }
        showAccountHub("profile");
    }

    @FXML
    private void handleNotifications() {
        if (notificationPopoverHost != null && notificationPopoverHost.isVisible()) {
            hideNotificationPopover();
        } else {
            showNotificationPopover();
        }
    }

    @FXML
    private void handleOpenAdmin() {
        hideNotificationPopover();
        User currentUser = ServerConnector.currentUser;
        if (currentUser == null || !"ADMIN".equalsIgnoreCase(currentUser.getRole())) {
            showToast("Chỉ tài khoản ADMIN mới mở được khu quản trị.", false);
            return;
        }
        leaveDashboard();
        SceneNavigator.show("/views/AdminPanel.fxml", "Quản trị hệ thống - BidShift", 1120, 760, null);
    }

    @FXML
    private void handleOpenAddForm() {
        hideNotificationPopover();
        if (!requireLogin("Đăng nhập để đăng bán tài sản."))
            return;
        TextField nameField = new TextField();
        nameField.setPromptText("Tên sản phẩm");
        TextArea descriptionField = new TextArea();
        descriptionField.setPromptText("Mô tả, tình trạng, điểm nổi bật");
        descriptionField.setPrefRowCount(4);
        ComboBox<String> categoryBox = new ComboBox<>();
        categoryBox.getItems().addAll("Điện tử", "Nghệ thuật", "Xe cộ", "Khác");
        categoryBox.setValue("Nghệ thuật");
        TextField startingPriceField = new TextField();
        startingPriceField.setPromptText("Giá khởi điểm");
        TextField minIncrementField = new TextField("10000");
        TextField auctionDaysField = new TextField("1");
        TextField imagePathField = new TextField();
        imagePathField.setEditable(false);
        imagePathField.setPromptText("Chưa chọn ảnh");
        StackPane preview = new StackPane(imagePreviewPlaceholder("Chưa chọn ảnh"));
        preview.getStyleClass().add("form-image-preview");
        imagePathField.textProperty().addListener((obs, oldValue, newValue) -> updatePreview(preview, newValue));

        VBox form = new VBox(12,
                fieldBlock("Tên sản phẩm", nameField),
                fieldBlock("Mô tả", descriptionField),
                fieldBlock("Danh mục", categoryBox),
                new HBox(12,
                        fieldBlock("Giá khởi điểm", startingPriceField),
                        fieldBlock("Bước giá tối thiểu", minIncrementField),
                        fieldBlock("Số ngày đấu giá", auctionDaysField)),
                fieldBlock("Ảnh sản phẩm", imagePickerRow(imagePathField, preview)),
                preview);
        form.getStyleClass().add("inline-form");

        Button post = new Button("Đăng phiên đấu giá");
        post.getStyleClass().add("primary-button");
        post.setOnAction(e -> submitNewItem(new ItemFormData(
                nameField.getText(), descriptionField.getText(), categoryCodeFromDisplay(categoryBox.getValue()),
                startingPriceField.getText(), minIncrementField.getText(), auctionDaysField.getText(),
                imagePathField.getText())));

        showOverlay("Đăng bán sản phẩm", "Bạn sẽ tạo phòng với tư cách Người bán.", form, List.of(post, closeButton()));
    }

    private void refreshAuctionList() {
        String category = selectedCategory;
        String status = selectedStatus;
        CompletableFuture
                .supplyAsync(() -> connector.getPublicAuctions(category, status))
                .thenAccept(auctions -> Platform.runLater(() -> {
                    auctionList.setAll(auctions);
                    showingAccountHub = false;
                    setupHeader();
                    renderHome();
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> showToast("Không tải được danh sách đấu giá.", false));
                    return null;
                });
    }

    private <T> void loadAccountData(StackPane body, String message, Supplier<T> loader, Consumer<T> renderer) {
        body.getChildren().setAll(emptyBlock("Đang tải dữ liệu", message));
        CompletableFuture
                .supplyAsync(loader)
                .thenAccept(result -> Platform.runLater(() -> renderer.accept(result)))
                .exceptionally(error -> {
                    Platform.runLater(() -> body.getChildren().setAll(
                            emptyBlock("Không tải được dữ liệu", "Vui lòng thử lại sau.")));
                    return null;
                });
    }

    private void runResponseTask(Button button, String busyText, Supplier<Response> task, Consumer<Response> onDone) {
        String originalText = button.getText();
        button.setDisable(true);
        button.setText(busyText);
        CompletableFuture
                .supplyAsync(task)
                .thenAccept(response -> Platform.runLater(() -> {
                    button.setText(originalText);
                    button.setDisable(false);
                    onDone.accept(response);
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        button.setText(originalText);
                        button.setDisable(false);
                        showToast("Không thể kết nối máy chủ lúc này.", false);
                    });
                    return null;
                });
    }

    private void renderHome() {
        stopFeaturedCountdowns();
        VBox content = new VBox(26);
        content.getStyleClass().add("home-content");
        content.getChildren().add(createHero());

        List<AuctionSession> filtered = filteredAuctions();
        if (!"ALL".equals(selectedStatus) || !"ALL".equals(selectedCategory) || !query().isBlank()) {
            content.getChildren().add(createAuctionSection("Kết quả đấu giá", filtered));
        } else {
            content.getChildren().add(createMarketplaceSnapshot(filtered));
            content.getChildren().add(createAuctionSection("Phiên đang diễn ra", byStatus(filtered, "RUNNING")));
            content.getChildren().add(createAuctionSection("Tài sản sắp được đấu giá", byStatus(filtered, "OPEN")));
            content.getChildren().add(createAuctionSection("Tài sản đã đấu giá", ended(filtered)));
            content.getChildren().add(createNewsPreview());
        }
        contentHost.getChildren().setAll(wrapScroll(content));
        startFeaturedCountdowns();
    }

    private Node createHero() {
        StackPane hero = new StackPane();
        hero.getStyleClass().add("bidshift-hero");
        hero.setAlignment(Pos.CENTER);
        hero.setMaxWidth(Double.MAX_VALUE);
        hero.getChildren().add(createFullHeroImage());
        return hero;
    }

    private Node createFullHeroImage() {
        String resource = isDarkMode ? HERO_DARK_RESOURCE : HERO_LIGHT_RESOURCE;
        var url = getClass().getResource(resource);
        if (url == null) {
            return heroImageFallback();
        }

        ImageView imageView = new ImageView(new Image(
                url.toExternalForm(),
                HERO_IMAGE_SOURCE_WIDTH,
                HERO_IMAGE_SOURCE_HEIGHT,
                true,
                true));
        imageView.setPreserveRatio(false);
        imageView.setSmooth(true);
        imageView.setMouseTransparent(true);
        imageView.getStyleClass().add("hero-full-image");

        StackPane frame = new StackPane(imageView);
        frame.getStyleClass().add("hero-full-image-frame");
        frame.setAlignment(Pos.CENTER);
        frame.setMinWidth(HERO_IMAGE_MIN_WIDTH);
        frame.setMaxWidth(HERO_IMAGE_MAX_WIDTH);

        DoubleBinding heroWidth = new DoubleBinding() {
            {
                bind(contentHost.widthProperty());
            }

            @Override
            protected double computeValue() {
                double availableWidth = contentHost.getWidth() - HERO_IMAGE_SIDE_GUTTER;
                if (availableWidth <= 0) {
                    availableWidth = HERO_IMAGE_MAX_WIDTH;
                }
                return Math.max(HERO_IMAGE_MIN_WIDTH, Math.min(HERO_IMAGE_MAX_WIDTH, availableWidth));
            }
        };
        DoubleBinding heroHeight = heroWidth.multiply(HERO_IMAGE_RATIO);

        frame.prefWidthProperty().bind(heroWidth);
        frame.prefHeightProperty().bind(heroHeight);
        frame.minHeightProperty().bind(heroHeight);
        frame.maxHeightProperty().bind(heroHeight);
        imageView.fitWidthProperty().bind(heroWidth);
        imageView.fitHeightProperty().bind(heroHeight);

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(heroWidth);
        clip.heightProperty().bind(heroHeight);
        clip.setArcWidth(36);
        clip.setArcHeight(36);
        frame.setClip(clip);
        return frame;
    }

    private Node heroImageFallback() {
        VBox fallback = new VBox(10);
        fallback.setAlignment(Pos.CENTER);
        fallback.getStyleClass().add("hero-image-fallback");
        Label title = new Label("BidShift");
        title.getStyleClass().add("hero-image-fallback-title");
        Label message = new Label("Không tìm thấy hero asset.");
        message.getStyleClass().add("hero-image-fallback-message");
        fallback.getChildren().addAll(title, message);
        return fallback;
    }

    private Node createMarketplaceSnapshot(List<AuctionSession> sessions) {
        HBox row = new HBox(24);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().add("home-showcase-row");

        VBox stats = new VBox(14);
        stats.getStyleClass().add("platform-stats-panel");
        Label statsTitle = new Label("Thống kê nền tảng");
        statsTitle.getStyleClass().add("home-panel-title");
        stats.getChildren().add(statsTitle);

        HBox metrics = new HBox(0);
        metrics.getStyleClass().add("platform-metric-row");
        metrics.getChildren().addAll(
                metricTile("◎", "128K+", "Nhà đầu tư"),
                metricDivider(),
                metricTile("⌁", Math.max(9450, sessions.size()) + "+", "Phiên đấu giá"),
                metricDivider(),
                metricTile("$", "2.6B+", "USD giá trị giao dịch"),
                metricDivider(),
                metricTile("◌", "150+", "Quốc gia & vùng lãnh thổ"));
        stats.getChildren().add(metrics);

        VBox featured = new VBox(14);
        featured.getStyleClass().add("featured-auctions-panel");
        HBox heading = new HBox(10);
        heading.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Phiên đấu giá nổi bật");
        title.getStyleClass().add("home-panel-title");
        Region spacer = new Region();
        Button seeAll = new Button("Xem tất cả  →");
        seeAll.getStyleClass().add("featured-see-all");
        seeAll.setOnAction(e -> handleShowAllAssets());
        HBox.setHgrow(spacer, Priority.ALWAYS);
        heading.getChildren().addAll(title, spacer, seeAll);

        FlowPane cards = new FlowPane(14, 14);
        cards.getStyleClass().add("featured-auction-grid");
        cards.getChildren().addAll(
                createShowcaseAuctionCard("villa", "ĐANG DIỄN RA", true, "Biệt thự Oceanview Đà Nẵng",
                        "Giá hiện tại", "$1,250,000", "Kết thúc sau", "02 : 15 : 32"),
                createShowcaseAuctionCard("watch", "SẮP DIỄN RA", false, "Đồng hồ Patek Philippe",
                        "Giá khởi điểm", "$85,000", "Bắt đầu sau", "01 : 05 : 12"),
                createShowcaseAuctionCard("car", "ĐANG DIỄN RA", true, "Mercedes-Benz S 500 2022",
                        "Giá hiện tại", "$72,500", "Kết thúc sau", "00 : 45 : 18"));
        featured.getChildren().addAll(heading, cards);

        HBox.setHgrow(featured, Priority.ALWAYS);
        row.getChildren().addAll(stats, featured);
        return row;
    }

    private Separator metricDivider() {
        Separator divider = new Separator(Orientation.VERTICAL);
        divider.getStyleClass().add("platform-metric-divider");
        return divider;
    }

    private Node metricTile(String icon, String value, String label) {
        VBox tile = new VBox(8);
        tile.getStyleClass().add("platform-metric-tile");
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("platform-metric-icon");
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("platform-metric-value");
        Label labelText = new Label(label);
        labelText.setWrapText(true);
        labelText.getStyleClass().add("platform-metric-label");
        tile.getChildren().addAll(iconLabel, valueLabel, labelText);
        HBox.setHgrow(tile, Priority.ALWAYS);
        return tile;
    }

    private Node createShowcaseAuctionCard(String visualType, String statusText, boolean live, String title,
            String priceLabelText, String priceText, String timeLabelText, String timeText) {
        VBox card = new VBox(10);
        card.getStyleClass().add("featured-auction-card");
        card.setPrefWidth(268);

        StackPane media = new StackPane();
        media.getStyleClass().addAll("featured-auction-media", "featured-auction-media-" + visualType);
        media.setPrefSize(248, 112);
        media.getChildren().add(auctionVisual(visualType));

        Label status = new Label(statusText);
        status.getStyleClass().add(live ? "status-running" : "status-ended");
        StackPane.setAlignment(status, Pos.TOP_LEFT);
        StackPane.setMargin(status, new Insets(9));
        media.getChildren().add(status);

        Label name = new Label(title);
        name.setWrapText(true);
        name.getStyleClass().add("featured-auction-title");

        Label priceLabel = new Label(priceLabelText);
        priceLabel.getStyleClass().add("featured-auction-meta");
        Label price = new Label(priceText);
        price.getStyleClass().add(live ? "featured-auction-price" : "featured-auction-price-dark");
        VBox priceBox = new VBox(2, priceLabel, price);

        Label timeLabel = new Label(timeLabelText);
        timeLabel.getStyleClass().add("featured-auction-meta");
        Label time = new Label(timeText);
        time.getStyleClass().add("featured-auction-time");
        VBox timeBox = new VBox(2, timeLabel, time);
        timeBox.setAlignment(Pos.CENTER_RIGHT);

        Region spacer = new Region();
        HBox footer = new HBox(8, priceBox, spacer, timeBox);
        footer.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(spacer, Priority.ALWAYS);

        card.getChildren().addAll(media, name, footer);
        return card;
    }

    private Node auctionVisual(String type) {
        Pane art = new Pane();
        art.getStyleClass().add("auction-visual");
        art.setPrefSize(248, 112);
        switch (type) {
            case "watch" -> buildWatchVisual(art);
            case "car" -> buildCarVisual(art);
            default -> buildVillaVisual(art);
        }
        return art;
    }

    private void buildVillaVisual(Pane art) {
        Rectangle sky = visualRect(0, 0, 248, 112, 0, "auction-villa-sky");
        Rectangle sun = visualRect(186, 18, 32, 32, 32, "auction-villa-sun");
        Rectangle water = visualRect(0, 86, 248, 26, 0, "auction-villa-water");
        Rectangle house = visualRect(54, 48, 128, 38, 4, "auction-villa-house");
        Rectangle roof = visualRect(44, 42, 148, 12, 3, "auction-villa-roof");
        Rectangle window1 = visualRect(68, 58, 28, 20, 2, "auction-villa-window");
        Rectangle window2 = visualRect(104, 58, 28, 20, 2, "auction-villa-window");
        Rectangle window3 = visualRect(140, 58, 28, 20, 2, "auction-villa-window");
        Line palm = new Line(210, 86, 220, 34);
        palm.getStyleClass().add("auction-villa-palm");
        art.getChildren().addAll(sky, sun, water, house, roof, window1, window2, window3, palm);
    }

    private void buildWatchVisual(Pane art) {
        Rectangle strap = visualRect(112, 0, 25, 112, 10, "auction-watch-strap");
        Circle face = new Circle(124, 56, 39);
        face.getStyleClass().add("auction-watch-face");
        Circle inner = new Circle(124, 56, 30);
        inner.getStyleClass().add("auction-watch-inner");
        Line hand1 = new Line(124, 56, 124, 34);
        hand1.getStyleClass().add("auction-watch-hand");
        Line hand2 = new Line(124, 56, 145, 62);
        hand2.getStyleClass().add("auction-watch-hand");
        art.getChildren().addAll(strap, face, inner, hand1, hand2);
    }

    private void buildCarVisual(Pane art) {
        Rectangle road = visualRect(0, 84, 248, 28, 0, "auction-car-road");
        Rectangle body = visualRect(58, 52, 134, 32, 7, "auction-car-body");
        Rectangle cabin = visualRect(92, 34, 74, 28, 8, "auction-car-cabin");
        Rectangle window1 = visualRect(101, 40, 28, 14, 3, "auction-car-window");
        Rectangle window2 = visualRect(134, 40, 24, 14, 3, "auction-car-window");
        Circle wheel1 = new Circle(86, 86, 11);
        wheel1.getStyleClass().add("auction-car-wheel");
        Circle wheel2 = new Circle(166, 86, 11);
        wheel2.getStyleClass().add("auction-car-wheel");
        art.getChildren().addAll(road, body, cabin, window1, window2, wheel1, wheel2);
    }

    private Rectangle visualRect(double x, double y, double width, double height, double radius, String styleClass) {
        Rectangle rect = new Rectangle(x, y, width, height);
        rect.setArcWidth(radius);
        rect.setArcHeight(radius);
        rect.getStyleClass().add(styleClass);
        return rect;
    }

    private Node createFeaturedAuctionCard(AuctionSession session) {
        VBox card = new VBox(9);
        card.getStyleClass().add("featured-auction-card");
        card.setPrefWidth(214);

        StackPane media = new StackPane();
        media.getStyleClass().add("featured-auction-media");
        media.setPrefSize(214, 90);
        ImageView image = createImageView(session.getItemImagePath(), 214, 90);
        media.getChildren().add(image != null ? image : fallbackImageLabel(session.getItemCategory()));
        Label status = new Label(displayStatus(session.getStatus()));
        status.getStyleClass().add(statusClass(session.getStatus()));
        StackPane.setAlignment(status, Pos.TOP_LEFT);
        StackPane.setMargin(status, new Insets(8));
        media.getChildren().add(status);

        Label name = new Label(nullToText(session.getItemName(), "Tài sản đấu giá"));
        name.setWrapText(true);
        name.getStyleClass().add("featured-auction-title");

        Label priceLabel = new Label("Giá hiện tại");
        priceLabel.getStyleClass().add("featured-auction-meta");
        Label price = new Label(String.format("%,.0f VNĐ", session.getCurrentHighestBid()));
        price.getStyleClass().add("featured-auction-price");
        VBox priceBox = new VBox(2, priceLabel, price);

        Timestamp relevantTime = featuredCountdownTarget(session);
        Label timeLabel = new Label(featuredCountdownLabel(session, relevantTime));
        timeLabel.getStyleClass().add("featured-auction-meta");
        Label time = new Label(featuredCountdownText(session, relevantTime));
        time.getStyleClass().add("featured-auction-time");
        registerFeaturedCountdown(session, relevantTime, timeLabel, time);
        VBox timeBox = new VBox(2, timeLabel, time);
        timeBox.setAlignment(Pos.CENTER_RIGHT);

        Region spacer = new Region();
        HBox footer = new HBox(10, priceBox, spacer, timeBox);
        footer.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(spacer, Priority.ALWAYS);

        card.getChildren().addAll(media, name, footer);
        card.setOnMouseClicked(event -> {
            if (event.getButton().equals(MouseButton.PRIMARY))
                openAuctionRoom(session);
        });
        return card;
    }

    private Timestamp featuredCountdownTarget(AuctionSession session) {
        return switch (nullToText(session.getStatus(), "").toUpperCase(Locale.ROOT)) {
            case "RUNNING" -> session.getEndTime();
            case "OPEN" -> session.getStartTime();
            default -> session.getEndTime();
        };
    }

    private String featuredCountdownLabel(AuctionSession session, Timestamp target) {
        if (isFeaturedCountdownActive(session, target)) {
            return "RUNNING".equalsIgnoreCase(session.getStatus()) ? "Còn lại" : "Bắt đầu sau";
        }
        return "Mốc thời gian";
    }

    private String featuredCountdownText(AuctionSession session, Timestamp target) {
        if (isFeaturedCountdownActive(session, target)) {
            return formatCountdown(target.getTime() - System.currentTimeMillis());
        }
        return formatShortDate(target);
    }

    private boolean isFeaturedCountdownActive(AuctionSession session, Timestamp target) {
        if (session == null || target == null || target.getTime() <= System.currentTimeMillis())
            return false;
        String status = nullToText(session.getStatus(), "").toUpperCase(Locale.ROOT);
        return "RUNNING".equals(status) || "OPEN".equals(status);
    }

    private void registerFeaturedCountdown(AuctionSession session, Timestamp target, Label label, Label value) {
        if (isFeaturedCountdownActive(session, target)) {
            featuredCountdowns.add(new FeaturedCountdown(session, target, label, value));
        }
    }

    private void startFeaturedCountdowns() {
        if (featuredCountdowns.isEmpty())
            return;
        updateFeaturedCountdowns();
        featuredCountdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> updateFeaturedCountdowns()));
        featuredCountdownTimeline.setCycleCount(Animation.INDEFINITE);
        featuredCountdownTimeline.play();
    }

    private void stopFeaturedCountdowns() {
        if (featuredCountdownTimeline != null) {
            featuredCountdownTimeline.stop();
            featuredCountdownTimeline = null;
        }
        featuredCountdowns.clear();
    }

    private void updateFeaturedCountdowns() {
        featuredCountdowns.forEach(countdown -> {
            countdown.label.setText(featuredCountdownLabel(countdown.session, countdown.target));
            countdown.value.setText(featuredCountdownText(countdown.session, countdown.target));
        });
    }

    private String formatCountdown(long millis) {
        long totalSeconds = Math.max(0, millis / 1000);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    private String compactMoney(double value) {
        if (value >= 1_000_000_000d) {
            return String.format(Locale.US, "%.1fB+", value / 1_000_000_000d);
        }
        if (value >= 1_000_000d) {
            return String.format(Locale.US, "%.1fM+", value / 1_000_000d);
        }
        return String.format(Locale.US, "%,.0f+", value);
    }

    private String formatShortDate(Timestamp timestamp) {
        return timestamp == null ? "--:--" : new SimpleDateFormat("HH:mm dd/MM").format(timestamp);
    }

    private Node createAuctionSection(String title, List<AuctionSession> sessions) {
        VBox section = new VBox(14);
        Label heading = sectionTitle(title);
        FlowPane list = new FlowPane(16, 16);
        list.getStyleClass().add("auction-section-grid");
        if (sessions.isEmpty()) {
            list.getChildren().add(emptyBlock("Không có dữ liệu", "Các phiên phù hợp sẽ xuất hiện tại đây."));
        } else {
            sessions.stream().limit(8).forEach(session -> list.getChildren().add(createAuctionRowCard(session)));
        }
        section.getChildren().addAll(heading, list);
        return section;
    }

    private Node createAuctionRowCard(AuctionSession session) {
        HBox card = new HBox(13);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("auction-list-card");
        card.setPrefWidth(360);
        card.setMinHeight(126);

        StackPane media = new StackPane();
        media.getStyleClass().add("auction-list-media");
        media.setPrefSize(118, 96);
        ImageView image = createImageView(session.getItemImagePath(), 118, 96);
        media.getChildren().add(image != null ? image : fallbackImageLabel(session.getItemCategory()));

        VBox info = new VBox(6);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label name = new Label(nullToText(session.getItemName(), "Tài sản đấu giá"));
        name.setWrapText(true);
        name.getStyleClass().add("auction-list-title");
        Label desc = new Label(nullToText(session.getItemDescription(), ""));
        desc.setWrapText(true);
        desc.getStyleClass().add("auction-list-desc");
        desc.setMaxHeight(34);
        Label price = new Label(String.format("%,.0f VNĐ", session.getCurrentHighestBid()));
        price.getStyleClass().add("auction-price");
        Label status = new Label(displayStatus(session.getStatus()));
        status.getStyleClass().add(statusClass(session.getStatus()));
        HBox meta = new HBox(8, price, status);
        meta.setAlignment(Pos.CENTER_LEFT);
        info.getChildren().addAll(name, desc, meta);

        card.getChildren().addAll(media, info);
        card.setOnMouseClicked(event -> {
            if (event.getButton().equals(MouseButton.PRIMARY))
                openAuctionRoom(session);
        });
        return card;
    }

    private Node createNewsPreview() {
        VBox box = new VBox(12);
        box.getStyleClass().add("news-preview");
        box.getChildren().add(sectionTitle("Tin tức & thông báo mới nhất"));
        box.getChildren().addAll(
                infoPanel("Thông báo đấu giá: Cập nhật các phiên tài sản mới nhất trên BidShift."),
                infoPanel(
                        "Hướng dẫn: Hoàn thiện hồ sơ cá nhân và phương thức thanh toán trước khi tham gia các phiên giá trị cao."));
        return box;
    }

    private void showAccountHub(String initialSection) {
        stopFeaturedCountdowns();
        if (!requireLogin("Đăng nhập để mở thông tin tài khoản."))
            return;
        showingAccountHub = true;
        BorderPane hub = new BorderPane();
        hub.getStyleClass().add("account-hub");
        StackPane body = new StackPane();
        body.getStyleClass().add("account-body");

        VBox side = new VBox(10);
        side.getStyleClass().add("account-sidebar");
        Button profile = sidebarButton("Thông tin cá nhân");
        Button payment = sidebarButton("Phương thức thanh toán");
        Button notifications = sidebarButton("Thông báo");
        Button auctions = sidebarButton("Đấu giá của tôi");
        Button history = sidebarButton("Lịch sử đấu giá");
        Button sellerOrders = sidebarButton("Đơn bán");
        Button documents = sidebarButton("Tài liệu của tôi");
        Button cart = sidebarButton("Giỏ hàng");
        Button logout = sidebarButton("Đăng xuất");
        List<Button> buttons = List.of(profile, payment, notifications, auctions, history, sellerOrders, documents,
                cart, logout);

        profile.setOnAction(e -> {
            selectSidebar(buttons, profile);
            renderProfile(body);
        });
        payment.setOnAction(e -> {
            selectSidebar(buttons, payment);
            renderPayment(body);
        });
        notifications.setOnAction(e -> {
            selectSidebar(buttons, notifications);
            renderNotifications(body);
        });
        auctions.setOnAction(e -> {
            selectSidebar(buttons, auctions);
            renderMyAuctions(body);
        });
        history.setOnAction(e -> {
            selectSidebar(buttons, history);
            renderHistory(body);
        });
        sellerOrders.setOnAction(e -> {
            selectSidebar(buttons, sellerOrders);
            renderSellerOrders(body);
        });
        documents.setOnAction(e -> {
            selectSidebar(buttons, documents);
            renderDocuments(body);
        });
        cart.setOnAction(e -> {
            selectSidebar(buttons, cart);
            renderCart(body);
        });
        logout.setOnAction(e -> handleLogout());

        side.getChildren().addAll(buttons);
        hub.setLeft(side);
        hub.setCenter(body);
        contentHost.getChildren().setAll(hub);

        switch (initialSection) {
            case "payment" -> payment.fire();
            case "notifications" -> notifications.fire();
            case "auctions" -> auctions.fire();
            case "history" -> history.fire();
            case "seller-orders" -> sellerOrders.fire();
            case "documents" -> documents.fire();
            case "cart" -> cart.fire();
            default -> profile.fire();
        }
    }

    private void renderProfile(StackPane body) {
        loadAccountData(body, "Đang tải hồ sơ cá nhân...", connector::getProfile,
                profile -> renderProfileLoaded(body, profile));
    }

    private void renderProfileLoaded(StackPane body, ProfileDTO profile) {
        if (profile == null && ServerConnector.currentUser != null) {
            profile = new ProfileDTO();
            profile.setId(ServerConnector.currentUser.getId());
            profile.setUsername(ServerConnector.currentUser.getUsername());
            profile.setEmail(ServerConnector.currentUser.getEmail());
            profile.setFullName(ServerConnector.currentUser.getFullName());
            profile.setRole(ServerConnector.currentUser.getRole());
            profile.setLegitPoints(ServerConnector.currentUser.getLegitPoints());
            profile.setEmailVerified(ServerConnector.currentUser.isEmailVerified());
        }
        if (profile == null) {
            body.getChildren().setAll(emptyBlock("Không tải được hồ sơ", "Hãy đăng nhập lại để tiếp tục."));
            return;
        }

        TextField fullName = new TextField(nullToText(profile.getFullName(), ""));
        TextField username = new TextField(nullToText(profile.getUsername(), ""));
        username.setDisable(true);
        TextField email = new TextField(nullToText(profile.getEmail(), ""));
        TextField phone = new TextField(nullToText(profile.getPhone(), ""));
        TextField birthDate = new TextField(nullToText(profile.getBirthDate(), ""));
        ComboBox<String> gender = comboBox(List.of("Nam", "Nữ", "Khác"), profile.getGender());
        TextField citizenId = new TextField(nullToText(profile.getCitizenId(), ""));
        TextField address = new TextField(nullToText(profile.getAddress(), ""));
        ComboBox<String> city = comboBox(VietnamAddressData.provinceNames(), profile.getCity());
        ComboBox<String> district = comboBox(VietnamAddressData.districtNames(city.getValue()), profile.getDistrict());
        ComboBox<String> ward = comboBox(VietnamAddressData.wardNames(city.getValue(), district.getValue()),
                profile.getWard());

        city.valueProperty().addListener((obs, oldValue, newValue) -> {
            setComboItems(district, VietnamAddressData.districtNames(newValue), "");
            setComboItems(ward, List.of(), "");
        });
        district.valueProperty().addListener((obs, oldValue, newValue) -> setComboItems(ward,
                VietnamAddressData.wardNames(city.getValue(), newValue), ""));

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(12);
        grid.add(fieldBlock("Họ và tên", fullName), 0, 0);
        grid.add(fieldBlock("Tên đăng nhập", username), 1, 0);
        grid.add(fieldBlock("Email", email), 2, 0);
        grid.add(fieldBlock("Số điện thoại", phone), 0, 1);
        grid.add(fieldBlock("Ngày sinh", birthDate), 1, 1);
        grid.add(fieldBlock("Giới tính", gender), 2, 1);
        grid.add(fieldBlock("CCCD", citizenId), 0, 2);
        grid.add(fieldBlock("Tỉnh/Thành phố", city), 1, 2);
        grid.add(fieldBlock("Quận/Huyện", district), 2, 2);
        grid.add(fieldBlock("Xã/Phường", ward), 0, 3);
        Node addressBlock = fieldBlock("Địa chỉ nhà", address);
        grid.add(addressBlock, 1, 3, 2, 1);

        Label trust = new Label(String.format("Trạng thái: %s | %.0f điểm uy tín",
                profile.isEmailVerified() ? "email đã xác thực" : "email chưa xác thực",
                profile.getLegitPoints()));
        trust.getStyleClass().add("profile-trust");
        Node emailVerification = emailVerificationBlock(profile, body);
        Button save = new Button("Cập nhật");
        save.getStyleClass().add("primary-button");
        ProfileDTO finalProfile = profile;
        save.setOnAction(e -> {
            finalProfile.setFullName(fullName.getText());
            finalProfile.setEmail(email.getText());
            finalProfile.setPhone(phone.getText());
            finalProfile.setBirthDate(birthDate.getText());
            finalProfile.setGender(selectedValue(gender));
            finalProfile.setCitizenId(citizenId.getText());
            finalProfile.setAddress(address.getText());
            finalProfile.setCity(selectedValue(city));
            finalProfile.setDistrict(selectedValue(district));
            finalProfile.setWard(selectedValue(ward));
            if (save.getText().length() >= 0) {
                runResponseTask(save, "Đang cập nhật...", () -> connector.updateProfile(finalProfile), res -> {
                    showToast(res != null && "SUCCESS".equals(res.getStatus())
                            ? "Đã cập nhật hồ sơ."
                            : (res != null ? res.getMessage() : "Không cập nhật được hồ sơ."),
                            res != null && "SUCCESS".equals(res.getStatus()));
                    setupHeader();
                });
                return;
            }
            Response res = connector.updateProfile(finalProfile);
            showToast(res != null && "SUCCESS".equals(res.getStatus())
                    ? "Đã cập nhật hồ sơ."
                    : (res != null ? res.getMessage() : "Không cập nhật được hồ sơ."),
                    res != null && "SUCCESS".equals(res.getStatus()));
            setupHeader();
        });

        VBox page = accountPage("Thông tin cá nhân", trust, grid, emailVerification, save);
        body.getChildren().setAll(wrapScroll(page));
    }

    private Node emailVerificationBlock(ProfileDTO profile, StackPane body) {
        Label status = new Label(profile.isEmailVerified()
                ? "Email đã được xác thực."
                : "Email chưa xác thực. Sau khi đổi email, hãy bấm Cập nhật trước rồi gửi mã xác thực.");
        status.getStyleClass().add("account-note");

        TextField codeField = new TextField();
        codeField.setPromptText("Nhập mã OTP 6 số");
        codeField.setDisable(profile.isEmailVerified());

        Button sendCode = new Button(profile.isEmailVerified() ? "Đã xác thực" : "Gửi mã email");
        sendCode.getStyleClass().add("secondary-button");
        sendCode.setDisable(profile.isEmailVerified());
        sendCode.setOnAction(e -> {
            if (sendCode.getText().length() >= 0) {
                runResponseTask(sendCode, "Đang gửi...", connector::requestEmailVerification, response -> {
                    boolean success = response != null && "SUCCESS".equals(response.getStatus());
                    showToast(response != null ? response.getMessage() : "Không gửi được mã xác thực email.", success);
                });
                return;
            }
            Response response = connector.requestEmailVerification();
            boolean success = response != null && "SUCCESS".equals(response.getStatus());
            showToast(response != null ? response.getMessage() : "Không gửi được mã xác thực email.", success);
        });

        Button confirm = new Button("Xác nhận mã");
        confirm.getStyleClass().add("primary-button");
        confirm.setDisable(profile.isEmailVerified());
        confirm.setOnAction(e -> {
            String code = codeField.getText();
            if (confirm.getText().length() >= 0) {
                runResponseTask(confirm, "Đang xác nhận...", () -> connector.confirmEmailVerification(code),
                        response -> {
                            boolean success = response != null && "SUCCESS".equals(response.getStatus());
                            showToast(response != null ? response.getMessage() : "Không xác thực được email.", success);
                            if (success) {
                                renderProfile(body);
                            }
                        });
                return;
            }
            Response response = connector.confirmEmailVerification(codeField.getText());
            boolean success = response != null && "SUCCESS".equals(response.getStatus());
            showToast(response != null ? response.getMessage() : "Không xác thực được email.", success);
            if (success) {
                renderProfile(body);
            }
        });

        HBox controls = new HBox(10, sendCode, codeField, confirm);
        controls.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(codeField, Priority.ALWAYS);

        VBox block = new VBox(8, status, controls);
        block.getStyleClass().add("profile-trust");
        return block;
    }

    private void renderPayment(StackPane body) {
        loadAccountData(body, "Đang tải phương thức thanh toán...", connector::getPaymentProfile,
                payment -> renderPaymentLoaded(body, payment));
    }

    private void renderPaymentLoaded(StackPane body, PaymentProfileDTO payment) {
        if (payment == null) {
            payment = new PaymentProfileDTO();
            if (ServerConnector.currentUser != null) {
                payment.setUserId(ServerConnector.currentUser.getId());
            }
        }
        TextField account = new TextField(nullToText(payment.getBankAccountNumber(), ""));
        TextField bank = new TextField(nullToText(payment.getBankName(), ""));
        TextField expiry = new TextField(nullToText(payment.getCardExpiry(), ""));
        PasswordField csv = new PasswordField();
        csv.setPromptText("Không lưu sau khi kiểm tra");
        TextField owner = new TextField(nullToText(payment.getAccountOwnerName(), ""));

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(12);
        grid.add(fieldBlock("STK", account), 0, 0);
        grid.add(fieldBlock("Tên ngân hàng", bank), 1, 0);
        grid.add(fieldBlock("Ngày hết hạn", expiry), 0, 1);
        grid.add(fieldBlock("Mã CSV/CVV", csv), 1, 1);
        grid.add(fieldBlock("Tên chủ sở hữu tài khoản", owner), 0, 2, 2, 1);

        Label note = new Label("CSV/CVV chỉ dùng như dữ liệu nhập tạm thời và không được lưu trong hệ thống.");
        note.getStyleClass().add("account-note");
        Button save = new Button("Cập nhật");
        save.getStyleClass().add("primary-button");
        PaymentProfileDTO editablePayment = payment;
        save.setOnAction(e -> {
            editablePayment.setBankAccountNumber(account.getText());
            editablePayment.setBankName(bank.getText());
            editablePayment.setCardExpiry(expiry.getText());
            editablePayment.setAccountOwnerName(owner.getText());
            if (save.getText().length() >= 0) {
                runResponseTask(save, "Đang cập nhật...", () -> connector.updatePaymentProfile(editablePayment),
                        res -> {
                            showToast(res != null && "SUCCESS".equals(res.getStatus())
                                    ? "Đã cập nhật phương thức thanh toán."
                                    : (res != null ? res.getMessage() : "Không cập nhật được thanh toán."),
                                    res != null && "SUCCESS".equals(res.getStatus()));
                            csv.clear();
                        });
                return;
            }
            Response res = connector.updatePaymentProfile(editablePayment);
            showToast(res != null && "SUCCESS".equals(res.getStatus())
                    ? "Đã cập nhật phương thức thanh toán."
                    : (res != null ? res.getMessage() : "Không cập nhật được thanh toán."),
                    res != null && "SUCCESS".equals(res.getStatus()));
            csv.clear();
        });

        VBox page = accountPage("Phương thức thanh toán", note, grid, save);
        body.getChildren().setAll(wrapScroll(page));
    }

    private void renderNotifications(StackPane body) {
        loadAccountData(body, "Đang tải thông báo...", connector::getNotifications,
                notifications -> renderNotificationsLoaded(body, notifications));
    }

    private void renderNotificationsLoaded(StackPane body, List<Notification> notifications) {
        if (notifications == null) {
            notifications = List.of();
        }
        VBox list = new VBox(10);
        if (notifications.isEmpty()) {
            list.getChildren().add(
                    emptyBlock("Không có thông báo", "Cập nhật kết quả đấu giá và thanh toán sẽ xuất hiện tại đây."));
        }
        for (Notification notification : notifications) {
            Label title = new Label(nullToText(notification.getTitle(), "Thông báo"));
            title.getStyleClass().add("notification-title");
            Label time = new Label(formatDate(notification.getCreatedAt()));
            time.getStyleClass().add("notification-time");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox header = new HBox(10, title, spacer, time);
            Label message = new Label(nullToText(notification.getMessage(), ""));
            message.setWrapText(true);
            message.getStyleClass().add("notification-message");
            VBox row = new VBox(7, header, message);
            row.getStyleClass().add(notification.isRead() ? "notification-row" : "notification-row-unread");
            if ("SELLER_CANCELLED".equals(notification.getType()) && notification.getReferenceId() > 0) {
                Button relist = new Button("Đăng lại sản phẩm");
                relist.getStyleClass().add("secondary-button");
                relist.setOnAction(e -> showRelistPanel(notification.getReferenceId()));
                row.getChildren().add(relist);
            }
            list.getChildren().add(row);
        }
        Button markRead = new Button("Đánh dấu đã đọc");
        markRead.getStyleClass().add("secondary-button");
        markRead.setOnAction(e -> {
            connector.markNotificationsRead();
            updateNotificationBadge();
            renderNotifications(body);
        });
        body.getChildren().setAll(wrapScroll(accountPage("Thông báo", list, markRead)));
    }

    private void showNotificationPopover() {
        if (notificationPopoverHost == null)
            return;
        notificationPopoverHost.getChildren().setAll(createNotificationPopover());
        notificationPopoverHost.setManaged(true);
        notificationPopoverHost.setVisible(true);
    }

    private Node createNotificationPopover() {
        VBox panel = new VBox(12);
        panel.getStyleClass().add("notification-popover");

        Label title = new Label("Thông báo");
        title.getStyleClass().add("notification-popover-title");
        Button close = new Button("×");
        close.getStyleClass().add("notification-popover-close");
        close.setOnAction(e -> hideNotificationPopover());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, title, spacer, close);
        header.setAlignment(Pos.CENTER_LEFT);
        panel.getChildren().add(header);

        if (ServerConnector.currentUser == null) {
            Label guestText = new Label("Đăng nhập để xem thông báo về kết quả đấu giá, thanh toán và sản phẩm.");
            guestText.setWrapText(true);
            guestText.getStyleClass().add("notification-popover-empty");
            Button login = new Button("Đăng nhập");
            login.getStyleClass().add("secondary-button");
            login.setOnAction(e -> {
                hideNotificationPopover();
                SceneNavigator.showLogin();
            });
            panel.getChildren().addAll(guestText, login);
            return panel;
        }

        List<Notification> notifications = connector.getNotifications();
        if (notifications.isEmpty()) {
            Label empty = new Label("Chưa có thông báo mới.");
            empty.getStyleClass().add("notification-popover-empty");
            panel.getChildren().add(empty);
        } else {
            notifications.stream().limit(5).map(this::notificationPreviewRow).forEach(panel.getChildren()::add);
        }

        Button markRead = new Button("Đánh dấu đã đọc");
        markRead.getStyleClass().add("secondary-button");
        markRead.setMaxWidth(Double.MAX_VALUE);
        markRead.setOnAction(e -> {
            connector.markNotificationsRead();
            updateNotificationBadge();
            showNotificationPopover();
        });
        Button viewAll = new Button("Xem tất cả");
        viewAll.getStyleClass().add("ghost-button");
        viewAll.setMaxWidth(Double.MAX_VALUE);
        viewAll.setOnAction(e -> {
            hideNotificationPopover();
            showAccountHub("notifications");
        });
        panel.getChildren().addAll(new HBox(10, markRead, viewAll));
        return panel;
    }

    private Node notificationPreviewRow(Notification notification) {
        Label title = new Label(nullToText(notification.getTitle(), "Thông báo"));
        title.getStyleClass().add("notification-title");
        Label time = new Label(formatDate(notification.getCreatedAt()));
        time.getStyleClass().add("notification-time");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, title, spacer, time);
        header.setAlignment(Pos.CENTER_LEFT);

        Label message = new Label(compactText(nullToText(notification.getMessage(), ""), 118));
        message.setWrapText(true);
        message.getStyleClass().add("notification-message");

        VBox row = new VBox(5, header, message);
        row.getStyleClass().add(notification.isRead() ? "notification-popover-row" : "notification-popover-row-unread");
        return row;
    }

    private void hideNotificationPopover() {
        if (notificationPopoverHost == null)
            return;
        notificationPopoverHost.getChildren().clear();
        notificationPopoverHost.setManaged(false);
        notificationPopoverHost.setVisible(false);
    }

    private void renderMyAuctions(StackPane body) {
        loadAccountData(body, "Đang tải các phiên liên quan đến bạn...",
                () -> List.of(connector.getMyAuctions(), connector.getJoinedAuctions()),
                lists -> renderMyAuctionsLoaded(body, lists));
    }

    private void renderMyAuctionsLoaded(StackPane body, List<List<AuctionSession>> lists) {
        TabPane tabs = new TabPane();
        tabs.getStyleClass().add("account-tabs");
        Tab owned = new Tab("Đấu giá của bạn", auctionListView(connector.getMyAuctions()));
        Tab joined = new Tab("Đấu giá đã tham gia", auctionListView(connector.getJoinedAuctions()));
        owned.setClosable(false);
        joined.setClosable(false);
        tabs.getTabs().addAll(owned, joined);
        body.getChildren().setAll(wrapScroll(accountPage("Đấu giá của tôi", tabs)));
    }

    private void renderHistory(StackPane body) {
        loadAccountData(body, "Đang tải lịch sử đấu giá...", connector::getAuctionHistory,
                history -> renderHistoryLoaded(body, history));
    }

    private void renderHistoryLoaded(StackPane body, List<AuctionSession> history) {
        if (history == null) {
            history = List.of();
        }
        long won = history.stream().filter(a -> a.getWinnerId() == ServerConnector.currentUser.getId()).count();
        double total = history.stream()
                .filter(a -> a.getWinnerId() == ServerConnector.currentUser.getId())
                .mapToDouble(AuctionSession::getCurrentHighestBid)
                .sum();
        HBox stats = new HBox(12,
                statBox(String.valueOf(history.size()), "Phiên đã tham gia"),
                statBox(String.valueOf(won), "Tài sản trúng đấu giá"),
                statBox(String.format("%,.0f đ", total), "Tổng giá trị đã thắng"));
        body.getChildren().setAll(wrapScroll(accountPage("Lịch sử đấu giá", stats, auctionListView(history))));
    }

    private void renderSellerOrders(StackPane body) {
        loadAccountData(body, "Đang tải đơn bán...", connector::getSellerOrders,
                orders -> renderSellerOrdersLoaded(body, orders));
    }

    private void renderSellerOrdersLoaded(StackPane body, List<CartItem> orders) {
        if (orders == null) {
            orders = List.of();
        }
        VBox list = new VBox(12);
        list.getStyleClass().add("panel-list");
        if (orders.isEmpty()) {
            list.getChildren().add(emptyBlock("Chưa có đơn bán",
                    "Khi người thắng đấu giá checkout, đơn cần giao sẽ xuất hiện tại đây."));
        } else {
            for (CartItem item : orders) {
                list.getChildren().add(sellerOrderRow(body, item));
            }
        }
        body.getChildren().setAll(wrapScroll(accountPage("Đơn bán", list)));
    }

    private Node sellerOrderRow(StackPane body, CartItem item) {
        StackPane imageWrapper = new StackPane();
        imageWrapper.getStyleClass().add("cart-image-wrapper");
        ImageView image = createImageView(item.getImagePath(), 82, 82);
        imageWrapper.getChildren().add(image != null ? image : imagePreviewPlaceholder("Chưa có ảnh"));

        Label name = new Label(nullToText(item.getItemName(), "Sản phẩm"));
        name.setWrapText(true);
        name.getStyleClass().add("cart-title");
        Label buyer = new Label("Người mua: " + nullToText(item.getBidderName(), "-"));
        buyer.getStyleClass().add("cart-details");
        Label price = new Label(String.format("%,.0f VNĐ", item.getWinningPrice()));
        price.getStyleClass().add("cart-price");
        Label payment = new Label(displayPaymentMethod(item.getPaymentMethod()));
        payment.getStyleClass().add("status-pill");
        Label delivery = new Label(displayDeliveryStatus(item.getDeliveryStatus()));
        delivery.getStyleClass().add(deliveryStatusClass(item.getDeliveryStatus()));
        Label address = new Label("Giao đến: " + nullToText(item.getShippingAddress(), "-"));
        address.setWrapText(true);
        address.getStyleClass().add("cart-details");
        Label tracking = new Label(
                isBlank(item.getTrackingCode()) ? "Mã vận đơn: -" : "Mã vận đơn: " + item.getTrackingCode());
        tracking.getStyleClass().add("cart-due");

        VBox info = new VBox(6, name, buyer, new HBox(8, price, payment, delivery), address, tracking);
        HBox.setHgrow(info, Priority.ALWAYS);

        VBox actions = new VBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);
        String deliveryStatus = nullToText(item.getDeliveryStatus(), "");
        if ("WAITING_SHIPMENT".equals(deliveryStatus)) {
            TextField trackingInput = new TextField();
            trackingInput.setPromptText("Mã vận đơn");
            trackingInput.setMaxWidth(180);
            Button ship = new Button("Bắt đầu giao");
            ship.getStyleClass().add("secondary-button");
            ship.setOnAction(e -> runResponseTask(ship, "Đang cập nhật...",
                    () -> connector.updateDeliveryStatus(item.getId(), "SHIPPING", trackingInput.getText()),
                    res -> {
                        boolean ok = res != null && "SUCCESS".equals(res.getStatus());
                        showToast(ok ? "Đã chuyển đơn sang đang giao."
                                : (res != null ? res.getMessage() : "Không thể cập nhật đơn."), ok);
                        renderSellerOrders(body);
                    }));
            actions.getChildren().addAll(trackingInput, ship);
        } else if ("SHIPPING".equals(deliveryStatus)) {
            Button delivered = new Button("Đã giao");
            delivered.getStyleClass().add("primary-button");
            delivered.setOnAction(e -> runResponseTask(delivered, "Đang cập nhật...",
                    () -> connector.updateDeliveryStatus(item.getId(), "DELIVERED", item.getTrackingCode()),
                    res -> {
                        boolean ok = res != null && "SUCCESS".equals(res.getStatus());
                        showToast(ok ? "Đơn đã được đánh dấu đã giao."
                                : (res != null ? res.getMessage() : "Không thể cập nhật đơn."), ok);
                        renderSellerOrders(body);
                    }));
            actions.getChildren().add(delivered);
        }

        HBox row = new HBox(14, imageWrapper, info, actions);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("cart-row");
        return row;
    }

    private void renderDocuments(StackPane body) {
        VBox docs = new VBox(12,
                documentCard("CCCD", "Thông tin CCCD được lưu trong hồ sơ cá nhân.", "Theo dõi"),
                documentCard("Xác thực thanh toán", "Tên chủ tài khoản phải trùng với tên thật trong hồ sơ.",
                        "Đã cấu hình"),
                documentCard("Quy chế tham gia", "Placeholder cho tài liệu/quy chế đấu giá của người dùng.",
                        "Bản nháp"));
        body.getChildren().setAll(wrapScroll(accountPage("Tài liệu của tôi", docs)));
    }

    private void renderCart(StackPane body) {
        if (ServerConnector.currentUser != null) {
            loadAccountData(body, "Đang tải giỏ hàng...", connector::getCart, items -> {
                preloadedCartItems = items;
                body.getChildren().setAll(wrapScroll(accountPage("Giỏ hàng", cartContent())));
                preloadedCartItems = null;
            });
            return;
        }
        body.getChildren().setAll(wrapScroll(accountPage("Giỏ hàng", cartContent())));
    }

    private Node auctionListView(List<AuctionSession> sessions) {
        VBox list = new VBox(12);
        list.getStyleClass().add("account-auction-list");
        if (sessions.isEmpty()) {
            list.getChildren().add(emptyBlock("Không có dữ liệu", "Các phiên liên quan đến bạn sẽ xuất hiện tại đây."));
        } else {
            sessions.forEach(session -> list.getChildren().add(createWideAuctionRow(session)));
        }
        return list;
    }

    private Node createWideAuctionRow(AuctionSession session) {
        HBox row = (HBox) createAuctionRowCard(session);
        row.setPrefWidth(760);
        row.getStyleClass().add("auction-wide-row");
        return row;
    }

    private Node cartContent() {
        List<CartItem> items = preloadedCartItems != null ? preloadedCartItems : connector.getCart();
        if (items == null) {
            items = List.of();
        }
        VBox rows = new VBox(12);
        rows.getStyleClass().add("panel-list");
        List<CheckBox> checks = new ArrayList<>();
        if (items.isEmpty()) {
            rows.getChildren().add(emptyBlock("Giỏ hàng đang trống", "Sản phẩm bạn thắng đấu giá sẽ xuất hiện ở đây."));
            return rows;
        }
        for (CartItem item : items) {
            CheckBox check = new CheckBox();
            check.setUserData(item);
            boolean payable = "PENDING".equals(item.getStatus());
            check.setDisable(!payable);
            checks.add(check);

            StackPane imageWrapper = new StackPane();
            imageWrapper.getStyleClass().add("cart-image-wrapper");
            ImageView image = createImageView(item.getImagePath(), 82, 82);
            imageWrapper.getChildren().add(image != null ? image : imagePreviewPlaceholder("Chưa có ảnh"));

            Label name = new Label(nullToText(item.getItemName(), "Sản phẩm"));
            name.getStyleClass().add("cart-title");
            Label details = new Label(nullToText(item.getItemDescription(), ""));
            details.setWrapText(true);
            details.getStyleClass().add("cart-details");
            Label price = new Label(String.format("%,.0f VNĐ", item.getWinningPrice()));
            price.getStyleClass().add("cart-price");
            Label status = new Label(displayPaymentStatus(item.getStatus()));
            status.getStyleClass().add(payable ? "status-pill-warning" : "status-pill");
            Label due = new Label("Hạn " + formatDate(item.getPaymentDueAt()));
            due.getStyleClass().add("cart-due");
            HBox meta = new HBox(8, price, status, due);
            if (!payable) {
                Label payment = new Label(displayPaymentMethod(item.getPaymentMethod()));
                payment.getStyleClass().add("status-pill");
                Label delivery = new Label(displayDeliveryStatus(item.getDeliveryStatus()));
                delivery.getStyleClass().add(deliveryStatusClass(item.getDeliveryStatus()));
                meta.getChildren().addAll(payment, delivery);
            }
            VBox info = new VBox(6, name, details, meta);
            if (!payable) {
                Label address = new Label("Giao đến: " + nullToText(item.getShippingAddress(), "-"));
                address.setWrapText(true);
                address.getStyleClass().add("cart-details");
                Label tracking = new Label(
                        isBlank(item.getTrackingCode()) ? "Mã vận đơn: -" : "Mã vận đơn: " + item.getTrackingCode());
                tracking.getStyleClass().add("cart-due");
                info.getChildren().addAll(address, tracking);
            }
            HBox.setHgrow(info, Priority.ALWAYS);
            HBox row = new HBox(14, check, imageWrapper, info);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("cart-row");
            rows.getChildren().add(row);
        }
        Button checkout = new Button("Thanh toán mục đã chọn");
        checkout.getStyleClass().add("primary-button");
        checkout.setOnAction(e -> showCheckoutPanel(checks));
        rows.getChildren().add(checkout);
        return rows;
    }

    private void showCheckoutPanel(List<CheckBox> checks) {
        List<Integer> selected = checks.stream()
                .filter(CheckBox::isSelected)
                .filter(check -> !check.isDisabled())
                .map(check -> ((CartItem) check.getUserData()).getId())
                .toList();
        if (selected.isEmpty()) {
            showToast("Hãy chọn ít nhất một sản phẩm chưa thanh toán.", false);
            return;
        }
        ComboBox<String> method = new ComboBox<>();
        method.getItems().addAll("COD - Thanh toán khi nhận hàng", "Chuyển khoản ngân hàng");
        method.setValue("COD - Thanh toán khi nhận hàng");
        TextArea address = new TextArea();
        address.setPromptText("Địa chỉ giao hàng");
        address.setPrefRowCount(4);
        VBox form = new VBox(12, fieldBlock("Phương thức thanh toán", method),
                fieldBlock("Địa chỉ giao hàng", address));
        Button pay = new Button("Xác nhận thanh toán");
        pay.getStyleClass().add("primary-button");
        pay.setOnAction(e -> {
            String checkoutAddress = address.getText();
            if (isBlank(checkoutAddress)) {
                showToast("Vui lòng nhập địa chỉ giao hàng.", false);
                return;
            }
            String paymentCode = paymentCodeFromDisplay(method.getValue());
            if (pay.getText().length() >= 0) {
                runResponseTask(pay, "Đang thanh toán...",
                        () -> connector.checkout(selected, paymentCode, checkoutAddress), res -> {
                            boolean ok = res != null && "SUCCESS".equals(res.getStatus());
                            showToast(ok ? "Thanh toán thành công."
                                    : (res != null ? res.getMessage() : "Không thể thanh toán."), ok);
                            hideOverlay();
                            showAccountHub("cart");
                        });
                return;
            }
            Response res = connector.checkout(selected, paymentCode, checkoutAddress);
            boolean ok = res != null && "SUCCESS".equals(res.getStatus());
            showToast(ok ? "Thanh toán thành công." : (res != null ? res.getMessage() : "Không thể thanh toán."), ok);
            hideOverlay();
            showAccountHub("cart");
        });
        showOverlay("Thanh toán", "Hoàn tất đơn thắng đấu giá.", form, List.of(pay, closeButton()));
    }

    private void submitNewItem(ItemFormData data) {
        try {
            User currentUser = ServerConnector.currentUser;
            if (currentUser == null) {
                showToast("Bạn cần đăng nhập để đăng bán.", false);
                return;
            }
            if (isBlank(data.name)) {
                showToast("Vui lòng nhập tên sản phẩm.", false);
                return;
            }
            double startingPrice = parsePositiveMoney(data.startingPrice, "giá khởi điểm");
            double minIncrement = parsePositiveMoney(data.minIncrement, "bước giá");
            int days = parseDays(data.auctionDays);

            if (data.name.length() >= 0) {
                CompletableFuture
                        .supplyAsync(() -> connector.addProduct(data.name.trim(), safeText(data.description),
                                data.category,
                                startingPrice, minIncrement, currentUser.getId(), days, safeText(data.imagePath)))
                        .thenAccept(res -> Platform.runLater(() -> {
                            boolean ok = res != null && "SUCCESS".equals(res.getStatus());
                            if (ok) {
                                hideOverlay();
                                refreshAuctionList();
                            }
                            showToast(ok ? "Phiên đấu giá đã được đăng."
                                    : (res != null ? res.getMessage() : "Máy chủ không phản hồi."), ok);
                        }))
                        .exceptionally(error -> {
                            Platform.runLater(() -> showToast("Không thể đăng bán lúc này.", false));
                            return null;
                        });
                return;
            }

            Response res = connector.addProduct(data.name.trim(), safeText(data.description), data.category,
                    startingPrice, minIncrement, currentUser.getId(), days, safeText(data.imagePath));
            boolean ok = res != null && "SUCCESS".equals(res.getStatus());
            if (ok) {
                hideOverlay();
                refreshAuctionList();
            }
            showToast(ok ? "Phiên đấu giá đã được đăng." : (res != null ? res.getMessage() : "Máy chủ không phản hồi."),
                    ok);
        } catch (IllegalArgumentException e) {
            showToast(e.getMessage(), false);
        }
    }

    private void showRelistPanel(int auctionId) {
        AuctionSession auction = connector.getAuctionDetail(auctionId);
        if (auction == null) {
            showToast("Không tải được phiên đã hủy.", false);
            return;
        }
        TextField name = new TextField(auction.getItemName());
        TextArea details = new TextArea(auction.getItemDescription());
        details.setPrefRowCount(4);
        TextField imagePath = new TextField(auction.getItemImagePath());
        imagePath.setEditable(false);
        StackPane preview = new StackPane();
        preview.getStyleClass().add("form-image-preview");
        updatePreview(preview, imagePath.getText());
        imagePath.textProperty().addListener((obs, oldValue, newValue) -> updatePreview(preview, newValue));
        VBox form = new VBox(12,
                fieldBlock("Tên sản phẩm", name),
                fieldBlock("Mô tả", details),
                fieldBlock("Ảnh sản phẩm", imagePickerRow(imagePath, preview)),
                preview);
        Button relist = new Button("Đăng lại");
        relist.getStyleClass().add("primary-button");
        relist.setOnAction(e -> {
            Response res = connector.relistAuction(auctionId, name.getText(), details.getText(), imagePath.getText());
            boolean ok = res != null && "SUCCESS".equals(res.getStatus());
            hideOverlay();
            showToast(ok ? "Sản phẩm đã được đăng lại."
                    : (res != null ? res.getMessage() : "Không thể đăng lại sản phẩm."), ok);
            refreshAuctionList();
        });
        showOverlay("Đăng lại sản phẩm", "Chỉnh nội dung trước khi đưa phiên quay lại sàn.", form,
                List.of(relist, closeButton()));
    }

    private void openAuctionRoom(AuctionSession session) {
        User currentUser = ServerConnector.currentUser;
        if (currentUser != null && currentUser.getId() != session.getSellerId()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Tham gia phòng đấu giá");
            alert.setHeaderText("Bạn sẽ tham gia phòng với tư cách là \"Người đấu giá\"");
            alert.setContentText(nullToText(session.getItemName(), "Phiên đấu giá"));
            ButtonType agree = new ButtonType("Đồng ý", ButtonBar.ButtonData.OK_DONE);
            ButtonType cancel = new ButtonType("Không đồng ý", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(agree, cancel);
            if (alert.showAndWait().orElse(cancel) != agree)
                return;
            Response res = connector.joinAuction(session.getId());
            if (res == null || !"SUCCESS".equals(res.getStatus())) {
                showToast(res != null ? res.getMessage() : "Không thể tham gia phòng.", false);
                return;
            }
        }
        leaveDashboard();
        String auctionRoomView = SceneNavigator.isDarkMode()
                ? "/views/AuctionRoomDark.fxml"
                : "/views/AuctionRoomLight.fxml";
        SceneNavigator.show(auctionRoomView,
                "BidShift - " + nullToText(session.getItemName(), "Phiên đấu giá"),
                1180, 720,
                (AuctionController controller) -> controller.setAuctionData(session));
        SceneNavigator.getStage().setMaximized(true);
    }

    private void handleLogout() {
        selectedCategory = "ALL";
        selectedStatus = "ALL";
        ServerConnector.currentUser = null;
        setupHeader();
        showingAccountHub = false;
        renderHome();
        showToast("Đã đăng xuất.", true);
        CompletableFuture
                .runAsync(connector::logout)
                .thenRun(this::refreshAuctionList);
    }

    private void leaveDashboard() {
        stopFeaturedCountdowns();
        connector.removeEventListener(this);
    }

    private static final class FeaturedCountdown {
        private final AuctionSession session;
        private final Timestamp target;
        private final Label label;
        private final Label value;

        private FeaturedCountdown(AuctionSession session, Timestamp target, Label label, Label value) {
            this.session = session;
            this.target = target;
            this.label = label;
            this.value = value;
        }
    }

    private List<AuctionSession> filteredAuctions() {
        String q = query();
        return auctionList.stream()
                .filter(session -> q.isBlank()
                        || contains(session.getItemName(), q)
                        || contains(session.getItemDescription(), q)
                        || contains(displayCategory(session.getItemCategory()), q))
                .toList();
    }

    private List<AuctionSession> byStatus(List<AuctionSession> source, String status) {
        return source.stream().filter(a -> status.equalsIgnoreCase(a.getStatus())).toList();
    }

    private List<AuctionSession> ended(List<AuctionSession> source) {
        return source.stream().filter(a -> List.of("FINISHED", "PAID", "CANCELED")
                .contains(nullToText(a.getStatus(), "").toUpperCase(Locale.ROOT))).toList();
    }

    private String query() {
        return searchField == null || searchField.getText() == null ? ""
                : searchField.getText().trim().toLowerCase(Locale.ROOT);
    }

    private boolean requireLogin(String message) {
        if (ServerConnector.currentUser != null)
            return true;
        showToast(message, false);
        SceneNavigator.showLogin();
        return false;
    }

    private void updateNotificationBadge() {
        if (notificationBtn == null)
            return;
        notificationBtn.setText(null);
        notificationBtn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        if (ServerConnector.currentUser == null) {
            notificationBtn.setGraphic(notificationGraphic(3));
            return;
        }
        List<Notification> notifications = connector.getNotifications();
        long unread = notifications.stream().filter(n -> !n.isRead()).count();
        notificationBtn.setGraphic(notificationGraphic(unread > 0 ? unread : 3));
    }

    private Node notificationGraphic(long unread) {
        Label bell = new Label("🔔");
        bell.getStyleClass().add("navbar-bell-icon");
        StackPane graphic = new StackPane(bell);
        graphic.getStyleClass().add("navbar-bell-graphic");
        if (unread > 0) {
            Label badge = new Label(String.valueOf(Math.min(unread, 9)));
            badge.getStyleClass().add("navbar-bell-badge");
            StackPane.setAlignment(badge, Pos.TOP_RIGHT);
            StackPane.setMargin(badge, new Insets(-8, -9, 0, 0));
            graphic.getChildren().add(badge);
        }
        return graphic;
    }

    private ScrollPane wrapScroll(Node content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("market-scroll");
        return scroll;
    }

    private VBox accountPage(String title, Node... nodes) {
        VBox page = new VBox(18);
        page.getStyleClass().add("account-page");
        page.getChildren().add(sectionTitle(title));
        page.getChildren().addAll(nodes);
        return page;
    }

    private Button sidebarButton(String text) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add("account-sidebar-button");
        return button;
    }

    private void selectSidebar(List<Button> buttons, Button active) {
        buttons.forEach(button -> button.getStyleClass().remove("account-sidebar-button-active"));
        active.getStyleClass().add("account-sidebar-button-active");
    }

    private Label sectionTitle(String text) {
        Label title = new Label(text);
        title.getStyleClass().add("section-title-large");
        return title;
    }

    private Node infoPanel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("info-panel");
        return label;
    }

    private Node statBox(String value, String label) {
        VBox box = new VBox(8);
        box.getStyleClass().add("history-stat");
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("history-stat-value");
        Label labelText = new Label(label);
        labelText.setWrapText(true);
        labelText.getStyleClass().add("history-stat-label");
        box.getChildren().addAll(valueLabel, labelText);
        return box;
    }

    private Node documentCard(String title, String details, String status) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("document-card");
        VBox text = new VBox(4, new Label(title), new Label(details));
        text.getChildren().get(0).getStyleClass().add("cart-title");
        text.getChildren().get(1).getStyleClass().add("cart-details");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label tag = new Label(status);
        tag.getStyleClass().add("status-pill-warning");
        row.getChildren().addAll(text, spacer, tag);
        return row;
    }

    private VBox fieldBlock(String label, Node field) {
        Label text = new Label(label);
        text.getStyleClass().add("input-label");
        VBox box = new VBox(6, text, field);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private ComboBox<String> comboBox(List<String> options, String selectedValue) {
        ComboBox<String> comboBox = new ComboBox<>();
        comboBox.setMaxWidth(Double.MAX_VALUE);
        setComboItems(comboBox, options, selectedValue);
        return comboBox;
    }

    private void setComboItems(ComboBox<String> comboBox, List<String> options, String selectedValue) {
        List<String> values = new ArrayList<>(options == null ? List.of() : options);
        String normalizedSelected = nullToText(selectedValue, "");
        if (!normalizedSelected.isBlank()
                && values.stream().noneMatch(value -> value.equalsIgnoreCase(normalizedSelected))) {
            values.add(0, normalizedSelected);
        }
        comboBox.setItems(FXCollections.observableArrayList(values));
        if (normalizedSelected.isBlank()) {
            comboBox.getSelectionModel().clearSelection();
        } else {
            comboBox.getSelectionModel().select(normalizedSelected);
        }
    }

    private String selectedValue(ComboBox<String> comboBox) {
        return comboBox.getValue() == null ? "" : comboBox.getValue();
    }

    private HBox imagePickerRow(TextField imagePathField, StackPane preview) {
        Button browse = new Button("Chọn ảnh");
        browse.getStyleClass().add("secondary-button");
        browse.setOnAction(e -> chooseImage(imagePathField, preview));
        HBox row = new HBox(10, imagePathField, browse);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("image-picker-row");
        HBox.setHgrow(imagePathField, Priority.ALWAYS);
        return row;
    }

    private void chooseImage(TextField imagePathField, StackPane preview) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn ảnh sản phẩm");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Tệp ảnh", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp"));
        File selected = chooser.showOpenDialog(SceneNavigator.getStage());
        if (selected != null) {
            imagePathField.setText(selected.getAbsolutePath());
            updatePreview(preview, selected.getAbsolutePath());
        }
    }

    private Node emptyBlock(String title, String hint) {
        VBox empty = new VBox(8);
        empty.getStyleClass().add("empty-state-container");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("empty-state");
        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().add("empty-state-hint");
        hintLabel.setWrapText(true);
        empty.getChildren().addAll(titleLabel, hintLabel);
        return empty;
    }

    private Label fallbackImageLabel(String category) {
        Label fallback = new Label(displayCategory(category));
        fallback.getStyleClass().add("image-fallback");
        return fallback;
    }

    private Label imagePreviewPlaceholder(String text) {
        Label placeholder = new Label(text);
        placeholder.getStyleClass().add("image-fallback");
        return placeholder;
    }

    private ImageView createImageView(String path, double width, double height) {
        if (path == null || path.isBlank())
            return null;
        try {
            File file = new File(path);
            Image image = file.exists() ? new Image(file.toURI().toString(), width, height, true, true)
                    : new Image(path, width, height, true, true);
            if (image.isError())
                return null;
            ImageView view = new ImageView(image);
            view.setFitWidth(width);
            view.setFitHeight(height);
            view.setPreserveRatio(false);
            return view;
        } catch (Exception e) {
            return null;
        }
    }

    private ImageView createResourceImageView(String resourcePath, double width, double height) {
        var url = getClass().getResource(resourcePath);
        if (url == null) {
            return null;
        }
        Image image = new Image(url.toExternalForm(), width, height, true, true);
        if (image.isError()) {
            return null;
        }
        ImageView view = new ImageView(image);
        view.setFitWidth(width);
        view.setFitHeight(height);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        return view;
    }

    private void showOverlay(String title, String subtitle, Node content, List<Button> actions) {
        overlayHost.getChildren().clear();
        overlayHost.setManaged(true);
        overlayHost.setVisible(true);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("inline-panel-title");
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("inline-panel-subtitle");
        subtitleLabel.setWrapText(true);
        Button exit = new Button("Đóng");
        exit.getStyleClass().add("ghost-button");
        exit.setOnAction(e -> hideOverlay());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(12, new VBox(4, titleLabel, subtitleLabel), spacer, exit);
        header.setAlignment(Pos.CENTER_LEFT);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("inline-panel-scroll");
        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.getChildren().addAll(actions);
        VBox panel = new VBox(16, header, new Separator(), scroll, footer);
        panel.getStyleClass().add("inline-panel");
        overlayHost.getChildren().add(panel);
    }

    private Button closeButton() {
        Button close = new Button("Đóng");
        close.getStyleClass().add("ghost-button");
        close.setOnAction(e -> hideOverlay());
        return close;
    }

    private void hideOverlay() {
        if (overlayHost == null)
            return;
        overlayHost.getChildren().clear();
        overlayHost.setManaged(false);
        overlayHost.setVisible(false);
    }

    private void updatePreview(StackPane preview, String imagePath) {
        preview.getChildren().clear();
        ImageView image = createImageView(imagePath, 360, 180);
        preview.getChildren().add(image != null ? image : imagePreviewPlaceholder("Chưa chọn ảnh"));
    }

    private void showToast(String message, boolean success) {
        if (toastLabel == null)
            return;
        toastLabel.setText(message);
        toastLabel.getStyleClass().setAll("toast", success ? "toast-success" : "toast-error");
        toastLabel.setManaged(true);
        toastLabel.setVisible(true);
        PauseTransition delay = new PauseTransition(Duration.seconds(3));
        delay.setOnFinished(e -> {
            toastLabel.setManaged(false);
            toastLabel.setVisible(false);
        });
        delay.play();
    }

    private double parsePositiveMoney(String raw, String label) {
        String input = safeText(raw).replace(",", "").replace(".", "").trim();
        try {
            double value = Double.parseDouble(input);
            if (!Double.isFinite(value) || value <= 0)
                throw new NumberFormatException();
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Vui lòng nhập " + label + " hợp lệ và lớn hơn 0.");
        }
    }

    private int parseDays(String raw) {
        try {
            int days = Integer.parseInt(safeText(raw).trim());
            if (days < 1 || days > 30)
                throw new NumberFormatException();
            return days;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Số ngày đấu giá phải từ 1 đến 30.");
        }
    }

    private String displayCategory(String category) {
        if (category == null || category.isBlank())
            return "Khác";
        if ("ALL".equalsIgnoreCase(category))
            return "Tất cả";
        return switch (category.toUpperCase(Locale.ROOT)) {
            case "ELECTRONICS" -> "Điện tử";
            case "ART" -> "Nghệ thuật";
            case "VEHICLE" -> "Xe cộ";
            default -> "Khác";
        };
    }

    private String categoryCodeFromDisplay(String category) {
        if (category == null || category.isBlank())
            return "OTHER";
        return switch (category.trim().toLowerCase(Locale.ROOT)) {
            case "điện tử", "dien tu", "electronics" -> "ELECTRONICS";
            case "nghệ thuật", "nghe thuat", "art" -> "ART";
            case "xe cộ", "xe co", "vehicle" -> "VEHICLE";
            default -> "OTHER";
        };
    }

    private String displayStatus(String status) {
        return switch (nullToText(status, "").toUpperCase(Locale.ROOT)) {
            case "RUNNING" -> "Đang diễn ra";
            case "OPEN" -> "Chưa diễn ra";
            case "PAID" -> "Đã thanh toán";
            case "CANCELED" -> "Đã hủy";
            case "FINISHED" -> "Đã kết thúc";
            default -> "Không xác định";
        };
    }

    private String statusClass(String status) {
        return switch (nullToText(status, "").toUpperCase(Locale.ROOT)) {
            case "RUNNING" -> "status-running";
            case "OPEN" -> "status-upcoming";
            default -> "status-ended";
        };
    }

    private String displayPaymentStatus(String status) {
        return switch (nullToText(status, "").toUpperCase(Locale.ROOT)) {
            case "PENDING" -> "Chờ thanh toán";
            case "PAID" -> "Đã thanh toán";
            case "CANCELED" -> "Đã hủy";
            default -> "Không xác định";
        };
    }

    private String displayPaymentMethod(String method) {
        return switch (nullToText(method, "").toUpperCase(Locale.ROOT)) {
            case "COD" -> "COD";
            case "BANK_TRANSFER" -> "Chuyển khoản";
            default -> "Chưa chọn";
        };
    }

    private String displayDeliveryStatus(String status) {
        return switch (nullToText(status, "").toUpperCase(Locale.ROOT)) {
            case "WAITING_PAYMENT" -> "Chờ thanh toán";
            case "WAITING_SHIPMENT" -> "Chờ giao hàng";
            case "SHIPPING" -> "Đang giao";
            case "DELIVERED" -> "Đã giao";
            default -> "Chưa cập nhật";
        };
    }

    private String deliveryStatusClass(String status) {
        return switch (nullToText(status, "").toUpperCase(Locale.ROOT)) {
            case "SHIPPING" -> "status-running";
            case "DELIVERED" -> "status-pill";
            default -> "status-pill-warning";
        };
    }

    private String paymentCodeFromDisplay(String value) {
        String normalized = nullToText(value, "").toLowerCase(Locale.ROOT);
        return normalized.contains("chuyển") || normalized.contains("khoản") ? "BANK_TRANSFER" : "COD";
    }

    private String formatDate(Timestamp timestamp) {
        return timestamp == null ? "-" : new SimpleDateFormat("dd/MM/yyyy HH:mm").format(timestamp);
    }

    private boolean contains(String value, String q) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(q);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String compactText(String value, int maxLength) {
        String text = safeText(value).replaceAll("\\s+", " ");
        if (text.length() <= maxLength)
            return text;
        return text.substring(0, Math.max(0, maxLength - 1)).trim() + "…";
    }

    private String nullToText(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    @Override
    public void onBidUpdate(AuctionEvent event) {
        if (!showingAccountHub)
            refreshAuctionList();
    }

    @Override
    public void onAuctionEnded(AuctionEvent event) {
        if (!showingAccountHub)
            refreshAuctionList();
    }

    @Override
    public void onAuctionStarted(AuctionEvent event) {
        if (!showingAccountHub)
            refreshAuctionList();
    }

    @Override
    public void onItemListUpdated(AuctionEvent event) {
        if (!showingAccountHub)
            refreshAuctionList();
    }

    @Override
    public void onAuctionExtended(AuctionEvent event) {
        if (!showingAccountHub)
            refreshAuctionList();
    }

    private record ItemFormData(String name, String description, String category, String startingPrice,
            String minIncrement, String auctionDays, String imagePath) {
    }
}
