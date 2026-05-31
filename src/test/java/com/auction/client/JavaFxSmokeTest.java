package com.auction.client;

import com.auction.client.service.ServerConnector;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaFxSmokeTest {
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
    void requireJavaFxToolkit() {
        Assumptions.assumeTrue(toolkitAvailable,
                () -> "JavaFX toolkit is not available in this environment: " + startupError);
    }

    @Test
    void loginViewLoadsWithCredentialsAndActionButton() throws Exception {
        Parent root = loadView("/views/Login.fxml");

        assertTrue(findNodes(root, TextInputControl.class).size() >= 2);
        assertTrue(findNodes(root, Button.class).stream().anyMatch(button -> "Đăng nhập".equals(button.getText())));
    }

    @Test
    void registerViewLoadsWithRequiredAccountFields() throws Exception {
        Parent root = loadView("/views/Register.fxml");

        assertTrue(findNodes(root, TextInputControl.class).size() >= 5);
        assertTrue(findNodes(root, Button.class).stream().anyMatch(button -> "Đăng ký".equals(button.getText())));
    }

    @Test
    void dashboardViewLoadsWithMarketplaceShellAndAiLauncher() throws Exception {
        Parent root = loadView("/views/Dashboard.fxml");

        assertNotNull(root.lookup(".market-root"));
        assertNotNull(root.lookup(".ai-chat-launcher"));
        assertTrue(findNodes(root, Button.class).stream().anyMatch(button -> "Đăng bán".equals(button.getText())));
    }

    @Test
    void auctionRoomViewLoadsBidAndAutoBidControls() throws Exception {
        Parent root = loadView("/views/AuctionRoom.fxml");

        assertNotNull(root.lookup(".auction-root"));
        assertTrue(findNodes(root, Button.class).stream().anyMatch(button -> "Trả giá".equals(button.getText())));
        assertTrue(findNodes(root, Button.class).stream().anyMatch(button -> button.getText().contains("Bật tự động")));
    }

    @Test
    void adminPanelViewLoadsManagementTables() throws Exception {
        Parent root = loadView("/views/AdminPanel.fxml");

        assertNotNull(root.lookup(".admin-root"));
        assertTrue(findNodes(root, TableView.class).size() >= 2);
        assertTrue(findNodes(root, Button.class).stream().anyMatch(button -> "Làm mới".equals(button.getText())));
    }

    private Parent loadView(String resourcePath) throws Exception {
        FutureTask<Parent> task = new FutureTask<>(() -> {
            URL resource = JavaFxSmokeTest.class.getResource(resourcePath);
            assertNotNull(resource, "Missing FXML resource: " + resourcePath);
            Parent root = FXMLLoader.load(resource);
            if (!root.getStyleClass().contains("theme-light") && !root.getStyleClass().contains("theme-dark")) {
                root.getStyleClass().add("theme-light");
            }
            new Scene(root);
            root.applyCss();
            root.layout();
            return root;
        });
        Platform.runLater(task);
        Parent root = task.get(10, TimeUnit.SECONDS);
        WaitForAsyncUtils.waitForFxEvents();
        return root;
    }

    private <T extends Node> List<T> findNodes(Parent root, Class<T> type) {
        List<T> matches = new ArrayList<>();
        visit(root, type, matches);
        return matches;
    }

    private <T extends Node> void visit(Node node, Class<T> type, List<T> matches) {
        if (type.isInstance(node)) {
            matches.add(type.cast(node));
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                visit(child, type, matches);
            }
        }
    }
}
