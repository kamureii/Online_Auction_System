package com.auction.server.handler;

import com.auction.server.auth.SessionRegistry;
import com.auction.server.dao.UserDAO;
import com.auction.server.handler.ActionRouter.Access;
import com.auction.server.security.SecurityPolicies;
import com.auction.shared.dto.LoginDTO;
import com.auction.shared.dto.RegisterDTO;
import com.auction.shared.factory.UserFactory;
import com.auction.shared.model.User;
import com.auction.shared.network.Response;
import com.auction.shared.validation.AccountValidator;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/** LOGIN, REGISTER, AUTH_SOCKET, LOGOUT, PING. */
public final class AuthActions {
    private static final Logger log = LoggerFactory.getLogger(AuthActions.class);
    private static final String PUBLIC_REGISTRATION_ROLE = "USER";

    private final UserDAO userDAO;
    private final SessionRegistry sessions;

    public AuthActions(UserDAO userDAO, SessionRegistry sessions) {
        this.userDAO = userDAO;
        this.sessions = sessions;
    }

    public void register(ActionRouter router) {
        router.register("PING", Access.PUBLIC, (ctx, payload) -> new Response("SUCCESS", "PONG", null));
        router.register("LOGIN", Access.PUBLIC, this::login);
        router.register("REGISTER", Access.PUBLIC, this::registerAccount);
        router.register("AUTH_SOCKET", Access.PUBLIC, this::authSocket);
        router.register("LOGOUT", Access.USER, this::logout);
    }

    private Response login(RequestContext ctx, String payload) {
        LoginDTO login = RequestContext.GSON.fromJson(payload, LoginDTO.class);
        String identifier = login == null ? null : login.getLoginIdentifier();
        String password = login == null ? null : login.getPassword();
        if (identifier == null || identifier.isBlank() || password == null || password.isEmpty()) {
            return new Response("ERROR", "Vui lòng nhập tên đăng nhập và mật khẩu.", null);
        }

        String accountKey = identifier.trim();
        long lockedMillis = Math.max(
                SecurityPolicies.LOGIN_BY_ACCOUNT.remainingLockMillis(accountKey),
                SecurityPolicies.LOGIN_BY_IP.remainingLockMillis(ctx.remoteIp()));
        if (lockedMillis > 0) {
            long minutes = Math.max(1, TimeUnit.MILLISECONDS.toMinutes(lockedMillis + 59_999));
            return new Response("ERROR", "Quá nhiều lần đăng nhập sai. Vui lòng thử lại sau " + minutes + " phút.", null);
        }

        User user;
        try {
            user = userDAO.loginUser(accountKey, password);
        } catch (IllegalStateException e) {
            log.error("Lỗi đăng nhập: {}", e.getMessage());
            return new Response("ERROR", "Không thể đăng nhập lúc này. Vui lòng kiểm tra cấu hình máy chủ.", null);
        }

        if (user == null) {
            SecurityPolicies.LOGIN_BY_ACCOUNT.recordFailure(accountKey);
            SecurityPolicies.LOGIN_BY_IP.recordFailure(ctx.remoteIp());
            return new Response("ERROR", "Sai tài khoản hoặc mật khẩu!", null);
        }

        SecurityPolicies.LOGIN_BY_ACCOUNT.recordSuccess(accountKey);
        ctx.setCurrentUser(user);
        return new Response("SUCCESS", "Đăng nhập thành công!", userPayloadWithToken(user));
    }

    private String userPayloadWithToken(User user) {
        SessionRegistry.SessionToken session = sessions.createSession(user);
        JsonObject json = RequestContext.GSON.toJsonTree(user).getAsJsonObject();
        json.addProperty("sessionToken", session.token());
        return json.toString();
    }

    private Response registerAccount(RequestContext ctx, String payload) {
        if (!SecurityPolicies.REGISTER_BY_IP.tryAcquire(ctx.remoteIp())) {
            return new Response("ERROR", "Bạn đã tạo quá nhiều tài khoản. Vui lòng thử lại sau.", null);
        }
        RegisterDTO data = RequestContext.GSON.fromJson(payload, RegisterDTO.class);
        if (data == null) {
            return new Response("ERROR", "Vui lòng điền đầy đủ thông tin!", null);
        }
        String error = AccountValidator.validateRegistration(
                data.getFullname(), data.getEmail(), data.getUsername(), data.getPassword());
        if (error != null) {
            return new Response("ERROR", error, null);
        }

        String username = data.getUsername().trim();
        String email = data.getEmail().trim();
        if (userDAO.isUsernameExists(username)) {
            return new Response("ERROR", "Tên đăng nhập đã tồn tại!", null);
        }
        if (userDAO.isEmailExists(email)) {
            return new Response("ERROR", "Email đã được sử dụng!", null);
        }

        User newUser = UserFactory.createNewUser(
                publicRegistrationRole(data.getRole()), username, email, data.getPassword(), data.getFullname().trim());
        return userDAO.registerUser(newUser)
                ? new Response("SUCCESS", "Đăng ký tài khoản thành công!", null)
                : new Response("ERROR", "Đăng ký thất bại! Vui lòng thử lại.", null);
    }

    /** Đăng ký công khai luôn tạo USER; không bao giờ tin vai trò client gửi lên. */
    public static String publicRegistrationRole(String requestedRole) {
        return PUBLIC_REGISTRATION_ROLE;
    }

    /** Đăng nhập lại socket (sau khi mất kết nối) bằng token phiên đã nhận lúc LOGIN. */
    private Response authSocket(RequestContext ctx, String payload) {
        String token = payload == null ? "" : payload.trim();
        return sessions.validate(token)
                .map(snapshot -> userDAO.getUserById(snapshot.getId()))
                .map(fresh -> {
                    ctx.setCurrentUser(fresh);
                    return new Response("SUCCESS", "Socket đã xác thực.", RequestContext.GSON.toJson(fresh));
                })
                .orElseGet(() -> new Response("ERROR", "Session không hợp lệ hoặc đã hết hạn.", null));
    }

    private Response logout(RequestContext ctx, String payload) {
        String token = payload == null ? "" : payload.trim();
        if (!token.isEmpty()) {
            sessions.revoke(token);
        }
        ctx.logout();
        return new Response("SUCCESS", "Đã đăng xuất.", null);
    }
}
