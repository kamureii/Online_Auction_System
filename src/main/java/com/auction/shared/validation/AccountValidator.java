package com.auction.shared.validation;

import java.util.regex.Pattern;

/**
 * Quy tắc kiểm tra thông tin tài khoản, dùng chung cho client (báo lỗi sớm) và server (nguồn tin cậy).
 */
public final class AccountValidator {
    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 128;

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_.]{3,50}$");
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private AccountValidator() {}

    /** @return thông báo lỗi, hoặc null nếu hợp lệ. */
    public static String validateRegistration(String fullName, String email, String username, String password) {
        if (isBlank(fullName) || isBlank(email) || isBlank(username) || isBlank(password)) {
            return "Vui lòng điền đầy đủ thông tin!";
        }
        if (fullName.trim().length() > 100) {
            return "Họ tên không được vượt quá 100 ký tự!";
        }
        String emailError = validateEmail(email);
        if (emailError != null) {
            return emailError;
        }
        if (!USERNAME.matcher(username.trim()).matches()) {
            return "Tên đăng nhập gồm 3-50 ký tự chữ, số, dấu chấm hoặc gạch dưới!";
        }
        return validatePassword(password);
    }

    public static String validateEmail(String email) {
        String value = email == null ? "" : email.trim();
        if (value.length() > 100 || !EMAIL.matcher(value).matches()) {
            return "Email không hợp lệ!";
        }
        return null;
    }

    public static String validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            return "Mật khẩu phải có ít nhất " + MIN_PASSWORD_LENGTH + " ký tự!";
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            return "Mật khẩu không được vượt quá " + MAX_PASSWORD_LENGTH + " ký tự!";
        }
        if (password.isBlank()) {
            return "Mật khẩu không hợp lệ!";
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
