package com.auction.server.handler;

import com.auction.server.dao.UserDAO;
import com.auction.shared.model.User;
import com.google.gson.Gson;

import java.util.concurrent.TimeUnit;

/**
 * Trạng thái của một kết nối socket: ai đang đăng nhập, từ IP nào.
 * Người dùng được nạp lại từ DB định kỳ để việc xoá/khoá/đổi quyền có hiệu lực mà không cần chờ đăng nhập lại.
 */
public class RequestContext {
    public static final Gson GSON = new Gson();
    private static final long USER_REFRESH_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(30);

    private final String remoteIp;
    private final UserDAO userDAO;
    private volatile User currentUser;
    private volatile long userLoadedAt;

    public RequestContext(String remoteIp) {
        this(remoteIp, new UserDAO());
    }

    RequestContext(String remoteIp, UserDAO userDAO) {
        this.remoteIp = remoteIp == null ? "unknown" : remoteIp;
        this.userDAO = userDAO;
    }

    public String remoteIp() {
        return remoteIp;
    }

    public User currentUser() {
        return currentUser;
    }

    public int currentUserId() {
        User user = currentUser;
        return user == null ? -1 : user.getId();
    }

    public boolean isAuthenticated() {
        return currentUser != null;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        this.userLoadedAt = System.currentTimeMillis();
    }

    public void logout() {
        this.currentUser = null;
    }

    public boolean hasRole(String role) {
        User user = currentUser;
        return user != null && role.equalsIgnoreCase(user.getRole());
    }

    /** Nạp lại user từ DB khi bản đang giữ đã cũ. @return false nếu tài khoản không còn tồn tại. */
    public boolean refreshIfStale() {
        User user = currentUser;
        if (user == null) {
            return false;
        }
        if (System.currentTimeMillis() - userLoadedAt < USER_REFRESH_INTERVAL_MILLIS) {
            return true;
        }
        User fresh = userDAO.getUserById(user.getId());
        if (fresh == null) {
            currentUser = null;
            return false;
        }
        setCurrentUser(fresh);
        return true;
    }

    /** Nạp lại ngay lập tức (sau khi hồ sơ hoặc trạng thái xác thực email thay đổi). */
    public void reloadUser() {
        User user = currentUser;
        if (user == null) {
            return;
        }
        User fresh = userDAO.getUserById(user.getId());
        if (fresh != null) {
            setCurrentUser(fresh);
        }
    }

    /** Email chưa xác thực trong bản cache có thể đã được xác thực từ máy khác, nên kiểm tra lại DB. */
    public boolean hasVerifiedEmail() {
        User user = currentUser;
        if (user == null) {
            return false;
        }
        if (user.isEmailVerified()) {
            return true;
        }
        reloadUser();
        user = currentUser;
        return user != null && user.isEmailVerified();
    }
}
