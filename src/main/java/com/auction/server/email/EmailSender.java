package com.auction.server.email;

import com.auction.shared.config.AppConfig;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

public class EmailSender {
    public SendResult sendVerificationCode(String to, String code) {
        return sendCode(
                to,
                code,
                "Mã xác thực email BidShift",
                "Mã xác thực email của bạn là: " + code + "\n\nMã này hết hạn sau 10 phút.",
                "[EMAIL OTP MOCK]"
        );
    }

    public SendResult sendPasswordResetCode(String to, String code) {
        return sendCode(
                to,
                code,
                "Mã khôi phục mật khẩu BidShift",
                "Mã khôi phục mật khẩu của bạn là: " + code + "\n\nMã này hết hạn sau 10 phút.",
                "[PASSWORD RESET OTP MOCK]"
        );
    }

    private SendResult sendCode(String to, String code, String subject, String text, String mockLabel) {
        SmtpConfig config = SmtpConfig.fromEnvironment();
        if (!config.isConfigured()) {
            if (config.mockConsole()) {
                System.out.println(mockLabel + " To: " + to + " | Code: " + code);
                return new SendResult(true, false,
                        "SMTP chưa được cấu hình. Mã OTP đã được in trong console server.");
            }
            System.err.println(mockLabel + " SMTP is not configured; OTP was not logged.");
            return new SendResult(false, false,
                    "SMTP chưa được cấu hình. Vui lòng cấu hình SMTP để gửi OTP.");
        }

        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.host", config.host());
            props.put("mail.smtp.port", String.valueOf(config.port()));
            props.put("mail.smtp.starttls.enable", String.valueOf(config.startTls()));
            props.put("mail.smtp.ssl.enable", String.valueOf(config.ssl()));

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(config.username(), config.password());
                }
            });

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(config.from()));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            message.setText(text);
            Transport.send(message);
            return new SendResult(true, true, "Mã OTP đã được gửi đến email của bạn.");
        } catch (MessagingException e) {
            System.err.println("Lỗi gửi email OTP: " + e.getMessage());
            return new SendResult(false, false, "Không gửi được email OTP. Vui lòng kiểm tra cấu hình SMTP.");
        }
    }

    public record SendResult(boolean success, boolean mailboxDelivery, String message) {}

    private record SmtpConfig(
            String host,
            int port,
            String username,
            String password,
            String from,
            boolean startTls,
            boolean ssl,
            boolean mockConsole
    ) {
        static SmtpConfig fromEnvironment() {
            String host = config("auction.smtp.host", "AUCTION_SMTP_HOST", "");
            int port = parsePort(config("auction.smtp.port", "AUCTION_SMTP_PORT", "587"));
            String username = config("auction.smtp.user", "AUCTION_SMTP_USER", "");
            String password = config("auction.smtp.password", "AUCTION_SMTP_PASSWORD", "");
            String from = config("auction.smtp.from", "AUCTION_SMTP_FROM", username);
            boolean ssl = Boolean.parseBoolean(config("auction.smtp.ssl", "AUCTION_SMTP_SSL", "false"));
            boolean startTls = Boolean.parseBoolean(config("auction.smtp.starttls", "AUCTION_SMTP_STARTTLS", "true"));
            boolean mockConsole = Boolean.parseBoolean(config(
                    "auction.email.mockConsole",
                    "AUCTION_EMAIL_MOCK_CONSOLE",
                    "false"
            ));
            return new SmtpConfig(host, port, username, password, from, startTls, ssl, mockConsole);
        }

        boolean isConfigured() {
            return !host.isBlank() && !username.isBlank() && !password.isBlank() && !from.isBlank();
        }

        private static String config(String propertyName, String envName, String defaultValue) {
            return AppConfig.get(propertyName, envName, defaultValue);
        }

        private static int parsePort(String value) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return 587;
            }
        }
    }
}
