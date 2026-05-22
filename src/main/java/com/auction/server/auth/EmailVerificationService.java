package com.auction.server.auth;

import com.auction.server.dao.UserDAO;
import com.auction.server.dao.VerificationCodeDAO;
import com.auction.server.email.EmailSender;
import com.auction.server.security.PasswordHasher;
import com.auction.shared.model.User;
import com.auction.shared.network.Response;

import java.security.SecureRandom;
import java.sql.Timestamp;

public class EmailVerificationService {
    private static final long OTP_TTL_MILLIS = 10L * 60L * 1000L;

    private final UserDAO userDAO;
    private final VerificationCodeDAO verificationCodeDAO;
    private final EmailSender emailSender;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailVerificationService() {
        this(new UserDAO(), new VerificationCodeDAO(), new EmailSender());
    }

    EmailVerificationService(UserDAO userDAO, VerificationCodeDAO verificationCodeDAO, EmailSender emailSender) {
        this.userDAO = userDAO;
        this.verificationCodeDAO = verificationCodeDAO;
        this.emailSender = emailSender;
    }

    public Response requestEmailVerification(int userId) {
        User user = userDAO.getUserById(userId);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return new Response("ERROR", "Tài khoản chưa có email hợp lệ.", null);
        }
        if (user.isEmailVerified()) {
            return new Response("SUCCESS", "Email đã được xác thực trước đó.", null);
        }

        String code = generateCode();
        Timestamp expiresAt = new Timestamp(System.currentTimeMillis() + OTP_TTL_MILLIS);
        boolean created = verificationCodeDAO.createEmailCode(userId, user.getEmail(), PasswordHasher.hash(code), expiresAt);
        if (!created) {
            return new Response("ERROR", "Không thể tạo mã xác thực email.", null);
        }

        EmailSender.SendResult sendResult = emailSender.sendVerificationCode(user.getEmail(), code);
        return sendResult.success()
                ? new Response("SUCCESS", sendResult.message(), null)
                : new Response("ERROR", sendResult.message(), null);
    }

    public Response confirmEmailVerification(int userId, String code) {
        String safeCode = code == null ? "" : code.trim();
        if (!safeCode.matches("\\d{6}")) {
            return new Response("ERROR", "Mã OTP phải gồm 6 chữ số.", null);
        }

        User user = userDAO.getUserById(userId);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return new Response("ERROR", "Tài khoản chưa có email hợp lệ.", null);
        }
        if (user.isEmailVerified()) {
            return new Response("SUCCESS", "Email đã được xác thực.", null);
        }

        VerificationCodeDAO.VerificationCode verificationCode =
                verificationCodeDAO.findActiveEmailCode(userId, user.getEmail());
        if (verificationCode == null) {
            return new Response("ERROR", "Chưa có mã xác thực hoặc mã đã hết hiệu lực.", null);
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

        verificationCodeDAO.markUsed(verificationCode.id());
        boolean verified = userDAO.markEmailVerified(userId);
        return verified
                ? new Response("SUCCESS", "Xác thực email thành công.", null)
                : new Response("ERROR", "Không thể cập nhật trạng thái xác thực email.", null);
    }

    private String generateCode() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }
}
