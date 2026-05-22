package com.auction.server.auth;

import com.auction.server.dao.UserDAO;
import com.auction.server.dao.VerificationCodeDAO;
import com.auction.server.email.EmailSender;
import com.auction.server.security.PasswordHasher;
import com.auction.shared.model.User;
import com.auction.shared.network.Response;

import java.security.SecureRandom;
import java.sql.Timestamp;

public class PasswordResetService {
    private static final long OTP_TTL_MILLIS = 10L * 60L * 1000L;
    private static final String NEUTRAL_REQUEST_MESSAGE =
            "Nếu tài khoản tồn tại và có email, mã khôi phục sẽ được gửi đến email.";

    private final UserDAO userDAO;
    private final VerificationCodeDAO verificationCodeDAO;
    private final EmailSender emailSender;
    private final SessionRegistry sessionRegistry;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService() {
        this(new UserDAO(), new VerificationCodeDAO(), new EmailSender(), SessionRegistry.getInstance());
    }

    PasswordResetService(
            UserDAO userDAO,
            VerificationCodeDAO verificationCodeDAO,
            EmailSender emailSender,
            SessionRegistry sessionRegistry
    ) {
        this.userDAO = userDAO;
        this.verificationCodeDAO = verificationCodeDAO;
        this.emailSender = emailSender;
        this.sessionRegistry = sessionRegistry;
    }

    public Response requestReset(String loginIdentifier) {
        String identifier = safe(loginIdentifier);
        if (identifier.isBlank()) {
            return new Response("ERROR", "Vui lòng nhập tên đăng nhập hoặc email.", null);
        }

        User user = userDAO.findByLoginIdentifier(identifier);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return new Response("SUCCESS", NEUTRAL_REQUEST_MESSAGE, null);
        }

        String code = generateCode();
        Timestamp expiresAt = new Timestamp(System.currentTimeMillis() + OTP_TTL_MILLIS);
        boolean created = verificationCodeDAO.createPasswordResetCode(
                user.getId(),
                user.getEmail(),
                PasswordHasher.hash(code),
                expiresAt
        );
        if (!created) {
            return new Response("ERROR", "Không thể tạo mã khôi phục mật khẩu.", null);
        }

        EmailSender.SendResult sendResult = emailSender.sendPasswordResetCode(user.getEmail(), code);
        return sendResult.success()
                ? new Response("SUCCESS", NEUTRAL_REQUEST_MESSAGE, null)
                : new Response("ERROR", sendResult.message(), null);
    }

    public Response confirmReset(String loginIdentifier, String code, String newPassword) {
        String identifier = safe(loginIdentifier);
        String safeCode = safe(code);
        String password = newPassword == null ? "" : newPassword;
        if (identifier.isBlank()) {
            return new Response("ERROR", "Vui lòng nhập tên đăng nhập hoặc email.", null);
        }
        if (!safeCode.matches("\\d{6}")) {
            return new Response("ERROR", "Mã OTP phải gồm 6 chữ số.", null);
        }
        if (password.length() < 6) {
            return new Response("ERROR", "Mật khẩu mới phải có ít nhất 6 ký tự.", null);
        }

        User user = userDAO.findByLoginIdentifier(identifier);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return new Response("ERROR", "Mã OTP không hợp lệ hoặc đã hết hạn.", null);
        }

        VerificationCodeDAO.VerificationCode verificationCode =
                verificationCodeDAO.findActivePasswordResetCode(user.getId(), user.getEmail());
        if (verificationCode == null) {
            return new Response("ERROR", "Chưa có mã khôi phục hoặc mã đã hết hiệu lực.", null);
        }
        if (verificationCode.expiresAt().getTime() < System.currentTimeMillis()) {
            verificationCodeDAO.markUsed(verificationCode.id());
            return new Response("ERROR", "Mã OTP đã hết hạn. Hãy gửi lại mã mới.", null);
        }
        if (verificationCode.attempts() >= verificationCode.maxAttempts()) {
            verificationCodeDAO.markUsed(verificationCode.id());
            return new Response("ERROR", "Bạn đã nhập sai quá số lần cho phép. Hãy gửi lại mã mới.", null);
        }
        if (!PasswordHasher.verify(safeCode, verificationCode.codeHash())) {
            verificationCodeDAO.incrementAttempts(verificationCode.id());
            return new Response("ERROR", "Mã OTP không đúng.", null);
        }

        boolean updated = userDAO.updatePasswordHash(user.getId(), PasswordHasher.hash(password));
        if (!updated) {
            return new Response("ERROR", "Không thể cập nhật mật khẩu mới.", null);
        }

        verificationCodeDAO.markUsed(verificationCode.id());
        sessionRegistry.revokeUserSessions(user.getId());
        return new Response("SUCCESS", "Đổi mật khẩu thành công. Hãy đăng nhập lại.", null);
    }

    private String generateCode() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
