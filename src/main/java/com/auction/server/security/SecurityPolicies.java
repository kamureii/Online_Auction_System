package com.auction.server.security;

import java.util.concurrent.TimeUnit;

/**
 * Các giới hạn tần suất dùng chung cho socket server và REST API.
 */
public final class SecurityPolicies {
    /** Khoá đăng nhập theo tên tài khoản: 5 lần sai trong 15 phút thì khoá 5 phút. */
    public static final LoginThrottle LOGIN_BY_ACCOUNT =
            new LoginThrottle(5, TimeUnit.MINUTES.toMillis(15), TimeUnit.MINUTES.toMillis(5));
    /** Khoá đăng nhập theo IP: 20 lần sai trong 15 phút thì khoá 5 phút. */
    public static final LoginThrottle LOGIN_BY_IP =
            new LoginThrottle(20, TimeUnit.MINUTES.toMillis(15), TimeUnit.MINUTES.toMillis(5));

    public static final RateLimiter REGISTER_BY_IP = new RateLimiter(10, TimeUnit.HOURS.toMillis(1));
    public static final RateLimiter PASSWORD_RESET_BY_ACCOUNT = new RateLimiter(3, TimeUnit.MINUTES.toMillis(10));
    public static final RateLimiter PASSWORD_RESET_BY_IP = new RateLimiter(10, TimeUnit.HOURS.toMillis(1));
    public static final RateLimiter PASSWORD_RESET_CONFIRM_BY_IP = new RateLimiter(20, TimeUnit.MINUTES.toMillis(10));
    public static final RateLimiter EMAIL_OTP_BY_USER = new RateLimiter(3, TimeUnit.MINUTES.toMillis(10));
    public static final RateLimiter CHAT_BY_USER = new RateLimiter(20, TimeUnit.MINUTES.toMillis(1));

    private SecurityPolicies() {}
}
