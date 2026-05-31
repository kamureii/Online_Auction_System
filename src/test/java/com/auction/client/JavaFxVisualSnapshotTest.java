package com.auction.client;

import com.auction.client.controller.DashboardController;
import com.auction.client.controller.AuctionController;
import com.auction.client.service.ServerConnector;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaFxVisualSnapshotTest {
    private static final double SNAPSHOT_WIDTH = 1366;
    private static final double SNAPSHOT_HEIGHT = 768;
    private static boolean toolkitAvailable;
    private static Throwable startupError;

    @BeforeAll
    static void startJavaFxToolkit() {
        System.setProperty("auction.ui.smokeTest", "true");
        ServerConnector.currentUser = null;
        try {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(latch::countDown);
            toolkitAvailable = latch.await(10, TimeUnit.SECONDS);
        } catch (IllegalStateException alreadyStarted) {
            toolkitAvailable = true;
        } catch (Throwable error) {
            startupError = error;
            toolkitAvailable = false;
        }
    }

    @BeforeEach
    void requireSnapshotModeAndToolkit() {
        Assumptions.assumeTrue(Boolean.getBoolean("auction.ui.snapshots"),
                "Set -Dauction.ui.snapshots=true to export JavaFX visual snapshots.");
        Assumptions.assumeTrue(toolkitAvailable,
                () -> "JavaFX toolkit is not available in this environment: " + startupError);
    }

    @Test
    void exportMainScreenSnapshots() throws Exception {
        Path outputDir = Path.of("target", "ui-snapshots");
        Files.createDirectories(outputDir);

        double narrowWidth = 1180;
        double narrowHeight = 760;
        List<SnapshotTarget> targets = List.of(
                new SnapshotTarget("login", "/views/Login.fxml", "theme-light", ""),
                new SnapshotTarget("register", "/views/Register.fxml", "theme-light", ""),
                new SnapshotTarget("dashboard-light", "/views/DashboardLight.fxml", "theme-light", ""),
                new SnapshotTarget("dashboard-dark", "/views/DashboardDark.fxml", "theme-dark", ""),
                new SnapshotTarget("account-profile", "/views/DashboardLight.fxml", "theme-light", "account-profile"),
                new SnapshotTarget("account-cart", "/views/DashboardLight.fxml", "theme-light", "account-cart"),
                new SnapshotTarget("account-profile-dark", "/views/DashboardDark.fxml", "theme-dark", "account-profile"),
                new SnapshotTarget("account-cart-dark", "/views/DashboardDark.fxml", "theme-dark", "account-cart"),
                new SnapshotTarget("checkout-address", "/views/DashboardLight.fxml", "theme-light", "checkout-address"),
                new SnapshotTarget("checkout-atm", "/views/DashboardLight.fxml", "theme-light", "checkout-atm"),
                new SnapshotTarget("checkout-invoice", "/views/DashboardLight.fxml", "theme-light", "checkout-invoice"),
                new SnapshotTarget("checkout-invoice-dark", "/views/DashboardDark.fxml", "theme-dark", "checkout-invoice"),
                new SnapshotTarget("seller-orders", "/views/DashboardLight.fxml", "theme-light", "seller-orders"),
                new SnapshotTarget("account-notifications", "/views/DashboardLight.fxml", "theme-light", "account-notifications"),
                new SnapshotTarget("add-item-modal", "/views/DashboardLight.fxml", "theme-light", "add-item"),
                new SnapshotTarget("auction-room-light", "/views/AuctionRoomLight.fxml", "theme-light", ""),
                new SnapshotTarget("auction-room-dark", "/views/AuctionRoomDark.fxml", "theme-dark", ""),
                new SnapshotTarget("auction-room-long-title", "/views/AuctionRoomLight.fxml", "theme-light", "long-title"),
                new SnapshotTarget("dashboard-light-1180", "/views/DashboardLight.fxml", "theme-light", "",
                        narrowWidth, narrowHeight),
                new SnapshotTarget("dashboard-auth-1180", "/views/DashboardLight.fxml", "theme-light", "home-auth",
                        narrowWidth, narrowHeight),
                new SnapshotTarget("account-cart-1180", "/views/DashboardLight.fxml", "theme-light", "account-cart",
                        narrowWidth, narrowHeight),
                new SnapshotTarget("checkout-invoice-1180", "/views/DashboardLight.fxml", "theme-light", "checkout-invoice",
                        narrowWidth, narrowHeight),
                new SnapshotTarget("auction-room-long-title-1180", "/views/AuctionRoomLight.fxml", "theme-light", "long-title",
                        narrowWidth, narrowHeight),
                new SnapshotTarget("admin-panel", "/views/AdminPanel.fxml", "theme-light", ""));

        for (SnapshotTarget target : targets) {
            Path image = outputDir.resolve(target.name() + ".png");
            renderSnapshot(target, image);
            assertTrue(Files.isRegularFile(image), "Snapshot was not written: " + image);
            assertTrue(Files.size(image) > 8_000, "Snapshot looks empty or truncated: " + image);
        }
    }

    private void renderSnapshot(SnapshotTarget target, Path outputPath) throws Exception {
        FutureTask<WritableImage> task = new FutureTask<>(() -> {
            ServerConnector.currentUser = null;
            URL resource = JavaFxVisualSnapshotTest.class.getResource(target.resourcePath());
            assertNotNull(resource, "Missing FXML resource: " + target.resourcePath());
            FXMLLoader loader = new FXMLLoader(resource);
            Parent root = loader.load();
            root.getStyleClass().removeAll("theme-light", "theme-dark");
            root.getStyleClass().add(target.themeClass());
            Scene scene = new Scene(root, target.width(), target.height());
            if (!target.dashboardState().isBlank() && loader.getController() instanceof DashboardController controller) {
                controller.renderVisualSnapshotState(target.dashboardState());
            } else if (!target.dashboardState().isBlank()
                    && loader.getController() instanceof AuctionController controller) {
                controller.renderVisualSnapshotState(target.dashboardState());
            }
            root.applyCss();
            root.resize(target.width(), target.height());
            root.layout();
            return scene.snapshot(null);
        });
        Platform.runLater(task);
        WritableImage snapshot = task.get(10, TimeUnit.SECONDS);
        WaitForAsyncUtils.waitForFxEvents();
        writePng(snapshot, outputPath.toFile());
    }

    private void writePng(WritableImage image, File output) throws Exception {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        PixelReader reader = image.getPixelReader();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                buffered.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        ImageIO.write(buffered, "png", output);
    }

    private record SnapshotTarget(String name, String resourcePath, String themeClass, String dashboardState,
                                  double width, double height) {
        private SnapshotTarget(String name, String resourcePath, String themeClass, String dashboardState) {
            this(name, resourcePath, themeClass, dashboardState, SNAPSHOT_WIDTH, SNAPSHOT_HEIGHT);
        }
    }
}
