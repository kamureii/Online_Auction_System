package com.auction.client.controller;

import com.auction.client.navigation.SceneNavigator;
import com.auction.client.service.ServerConnector;
import com.auction.shared.dto.RuntimeStatusDTO;
import com.auction.shared.network.Response;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.event.ActionEvent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class LoginController {
    private static final int LOGIN_TIMEOUT_SECONDS = 20;

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Label messageLabel;

    @FXML
    private void goToRegister() {
        SceneNavigator.showRegister();
    }

    @FXML
    private void handleBackHome() {
        SceneNavigator.showDashboard();
    }

    @FXML
    private void handleLogin(ActionEvent event) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Vui lòng nhập đầy đủ thông tin!");
            return;
        }

        setFormDisabled(true);
        showInfo("Đang đăng nhập...");

        CompletableFuture
                .supplyAsync(() -> ServerConnector.getInstance().login(username, password))
                .completeOnTimeout(
                        new Response("ERROR", "Không thể đăng nhập lúc này. Hãy thử lại sau.", null),
                        LOGIN_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS
                )
                .whenComplete((res, error) -> Platform.runLater(() -> {
                    setFormDisabled(false);
                    if (error != null) {
                        showError("Không thể đăng nhập lúc này.");
                        return;
                    }
                    if (res != null && "SUCCESS".equals(res.getStatus())) {
                        showSuccess("Đăng nhập thành công!");
                        SceneNavigator.showDashboard();
                        return;
                    }
                    showError(res != null ? res.getMessage() : "Không thể kết nối tới server!");
                }));
    }

    @FXML
    private void handleForgotPassword() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Quên mật khẩu");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        TextField identifierField = new TextField(usernameField.getText() == null ? "" : usernameField.getText().trim());
        identifierField.setPromptText("Tên đăng nhập hoặc email");

        TextField codeField = new TextField();
        codeField.setPromptText("Mã OTP 6 số");

        PasswordField newPasswordField = new PasswordField();
        newPasswordField.setPromptText("Mật khẩu mới");

        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Nhập lại mật khẩu mới");

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(420);
        Label environmentLabel = new Label("Đang kiểm tra cấu hình gửi OTP...");
        environmentLabel.setWrapText(true);
        environmentLabel.setMaxWidth(420);
        environmentLabel.getStyleClass().add("inline-message");

        Button sendCodeButton = new Button("Gửi mã OTP");
        Button resetButton = new Button("Đổi mật khẩu");
        resetButton.getStyleClass().add("login-button");

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.add(new Label("Tài khoản/email"), 0, 0);
        form.add(identifierField, 1, 0);
        form.add(new Label("Mã OTP"), 0, 1);
        form.add(codeField, 1, 1);
        form.add(new Label("Mật khẩu mới"), 0, 2);
        form.add(newPasswordField, 1, 2);
        form.add(new Label("Nhập lại"), 0, 3);
        form.add(confirmPasswordField, 1, 3);

        HBox actions = new HBox(10, sendCodeButton, resetButton);
        VBox content = new VBox(14, form, environmentLabel, actions, statusLabel);
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);
        loadPasswordResetRuntimeStatus(environmentLabel);

        sendCodeButton.setOnAction(e -> {
            String identifier = identifierField.getText() == null ? "" : identifierField.getText().trim();
            if (identifier.isBlank()) {
                setStatus(statusLabel, Color.RED, "Vui lòng nhập tên đăng nhập hoặc email.");
                return;
            }
            runResetAction(
                    () -> ServerConnector.getInstance().requestPasswordReset(identifier),
                    response -> {
                        if (isSuccess(response)) {
                            setStatus(statusLabel, Color.GREEN, response.getMessage());
                        } else {
                            setStatus(statusLabel, Color.RED, responseMessage(response, "Không thể gửi mã OTP."));
                        }
                    },
                    statusLabel,
                    "Đang gửi mã OTP...",
                    sendCodeButton,
                    resetButton
            );
        });

        resetButton.setOnAction(e -> {
            String identifier = identifierField.getText() == null ? "" : identifierField.getText().trim();
            String code = codeField.getText() == null ? "" : codeField.getText().trim();
            String newPassword = newPasswordField.getText();
            String confirmPassword = confirmPasswordField.getText();

            if (identifier.isBlank() || code.isBlank() || newPassword.isBlank() || confirmPassword.isBlank()) {
                setStatus(statusLabel, Color.RED, "Vui lòng nhập đầy đủ thông tin.");
                return;
            }
            if (newPassword.length() < 6) {
                setStatus(statusLabel, Color.RED, "Mật khẩu mới phải có ít nhất 6 ký tự.");
                return;
            }
            if (!newPassword.equals(confirmPassword)) {
                setStatus(statusLabel, Color.RED, "Mật khẩu nhập lại không khớp.");
                return;
            }

            runResetAction(
                    () -> ServerConnector.getInstance().confirmPasswordReset(identifier, code, newPassword),
                    response -> {
                        if (isSuccess(response)) {
                            dialog.close();
                            showSuccess(response.getMessage());
                            passwordField.clear();
                        } else {
                            setStatus(statusLabel, Color.RED, responseMessage(response, "Không thể đổi mật khẩu."));
                        }
                    },
                    statusLabel,
                    "Đang đổi mật khẩu...",
                    sendCodeButton,
                    resetButton
            );
        });

        dialog.showAndWait();
    }

    private void runResetAction(
            Supplier<Response> task,
            Consumer<Response> onComplete,
            Label statusLabel,
            String loadingMessage,
            Button... buttons
    ) {
        setStatus(statusLabel, Color.GRAY, loadingMessage);
        setButtonsDisabled(true, buttons);
        CompletableFuture
                .supplyAsync(task)
                .completeOnTimeout(
                        new Response("ERROR", "Dịch vụ xác thực không phản hồi kịp thời.", null),
                        LOGIN_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS
                )
                .whenComplete((response, error) -> Platform.runLater(() -> {
                    setButtonsDisabled(false, buttons);
                    if (error != null) {
                        setStatus(statusLabel, Color.RED, "Không thể kết nối dịch vụ xác thực.");
                        return;
                    }
                    onComplete.accept(response);
                }));
    }

    private void loadPasswordResetRuntimeStatus(Label target) {
        CompletableFuture
                .supplyAsync(() -> ServerConnector.getInstance().getRuntimeStatus())
                .thenAccept(status -> Platform.runLater(() -> showOtpRuntimeStatus(target, status)))
                .exceptionally(error -> {
                    Platform.runLater(() -> setStatus(target, Color.GRAY,
                            "Không đọc được trạng thái SMTP; vẫn có thể gửi yêu cầu OTP nếu server đã cấu hình."));
                    return null;
                });
    }

    private void showOtpRuntimeStatus(Label target, RuntimeStatusDTO status) {
        if (status == null || status.getSmtpStatus() == null) {
            setStatus(target, Color.GRAY,
                    "Không đọc được trạng thái SMTP; vẫn có thể gửi yêu cầu OTP nếu server đã cấu hình.");
            return;
        }
        String smtpStatus = status.getSmtpStatus();
        Color color = "CONFIGURED".equalsIgnoreCase(smtpStatus)
                ? Color.GREEN
                : ("MOCK".equalsIgnoreCase(smtpStatus) ? Color.ORANGE : Color.RED);
        setStatus(target, color, status.getSmtpMessage());
    }

    private boolean isSuccess(Response response) {
        return response != null && "SUCCESS".equals(response.getStatus());
    }

    private String responseMessage(Response response, String fallback) {
        return response != null && response.getMessage() != null && !response.getMessage().isBlank()
                ? response.getMessage()
                : fallback;
    }

    private void setButtonsDisabled(boolean disabled, Button... buttons) {
        for (Button button : buttons) {
            if (button != null) {
                button.setDisable(disabled);
            }
        }
    }

    private void showError(String message) {
        messageLabel.setTextFill(Color.RED);
        messageLabel.setText(message);
    }

    private void showSuccess(String message) {
        messageLabel.setTextFill(Color.GREEN);
        messageLabel.setText(message);
    }

    private void showInfo(String message) {
        messageLabel.setTextFill(Color.GRAY);
        messageLabel.setText(message);
    }

    private void setStatus(Label label, Color color, String message) {
        label.setTextFill(color);
        label.setText(message);
    }

    private void setFormDisabled(boolean disabled) {
        usernameField.setDisable(disabled);
        passwordField.setDisable(disabled);
        if (loginButton != null) {
            loginButton.setDisable(disabled);
        }
    }
}
