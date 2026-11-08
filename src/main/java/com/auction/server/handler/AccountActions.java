package com.auction.server.handler;

import com.auction.server.auth.EmailVerificationService;
import com.auction.server.dao.NotificationDAO;
import com.auction.server.dao.UserDAO;
import com.auction.server.handler.ActionRouter.Access;
import com.auction.server.security.SecurityPolicies;
import com.auction.shared.dto.PaymentProfileDTO;
import com.auction.shared.dto.ProfileDTO;
import com.auction.shared.model.Notification;
import com.auction.shared.network.Response;
import com.auction.shared.validation.AccountValidator;
import com.google.gson.JsonObject;

import java.util.List;

/** Hồ sơ, xác thực email, thông tin thanh toán và thông báo của chính người dùng đang đăng nhập. */
public final class AccountActions {
    private final UserDAO userDAO;
    private final EmailVerificationService emailVerification;

    public AccountActions(UserDAO userDAO, EmailVerificationService emailVerification) {
        this.userDAO = userDAO;
        this.emailVerification = emailVerification;
    }

    public void register(ActionRouter router) {
        router.register("GET_PROFILE", Access.USER, this::getProfile);
        router.register("UPDATE_PROFILE", Access.USER, this::updateProfile);
        router.register("REQUEST_EMAIL_VERIFICATION", Access.USER, this::requestEmailVerification);
        router.register("CONFIRM_EMAIL_VERIFICATION", Access.USER, this::confirmEmailVerification);
        router.register("GET_PAYMENT_PROFILE", Access.USER, this::getPaymentProfile);
        router.register("UPDATE_PAYMENT_PROFILE", Access.USER, this::updatePaymentProfile);
        router.register("GET_NOTIFICATIONS", Access.USER, this::getNotifications);
        router.register("MARK_NOTIFICATIONS_READ", Access.USER, this::markNotificationsRead);
    }

    private Response getProfile(RequestContext ctx, String payload) {
        ProfileDTO profile = userDAO.getProfile(ctx.currentUserId());
        return profile != null
                ? new Response("SUCCESS", "Lấy hồ sơ thành công!", RequestContext.GSON.toJson(profile))
                : new Response("ERROR", "Không tìm thấy hồ sơ!", null);
    }

    private Response updateProfile(RequestContext ctx, String payload) {
        ProfileDTO profile = RequestContext.GSON.fromJson(payload, ProfileDTO.class);
        if (profile == null) {
            return new Response("ERROR", "Yêu cầu không hợp lệ.", null);
        }
        String emailError = AccountValidator.validateEmail(profile.getEmail());
        if (emailError != null) {
            return new Response("ERROR", emailError, null);
        }
        String newEmail = profile.getEmail().trim();
        if (!newEmail.equalsIgnoreCase(ctx.currentUser().getEmail()) && userDAO.isEmailExists(newEmail)) {
            return new Response("ERROR", "Email đã được sử dụng!", null);
        }
        if (!userDAO.updateProfile(ctx.currentUserId(), profile)) {
            return new Response("ERROR", "Không thể cập nhật hồ sơ!", null);
        }
        ctx.reloadUser();
        return new Response("SUCCESS", "Cập nhật hồ sơ thành công!", null);
    }

    private Response requestEmailVerification(RequestContext ctx, String payload) {
        if (!SecurityPolicies.EMAIL_OTP_BY_USER.tryAcquire(String.valueOf(ctx.currentUserId()))) {
            return new Response("ERROR", "Bạn yêu cầu mã quá nhanh. Vui lòng thử lại sau ít phút.", null);
        }
        return emailVerification.requestEmailVerification(ctx.currentUserId());
    }

    private Response confirmEmailVerification(RequestContext ctx, String payload) {
        JsonObject obj = JsonPayload.parse(payload);
        Response response = emailVerification.confirmEmailVerification(
                ctx.currentUserId(), JsonPayload.string(obj, "code", ""));
        if ("SUCCESS".equals(response.getStatus())) {
            ctx.reloadUser();
        }
        return response;
    }

    private Response getPaymentProfile(RequestContext ctx, String payload) {
        PaymentProfileDTO profile = userDAO.getPaymentProfile(ctx.currentUserId());
        return new Response("SUCCESS", "Lấy thông tin thanh toán thành công!", RequestContext.GSON.toJson(profile));
    }

    private Response updatePaymentProfile(RequestContext ctx, String payload) {
        PaymentProfileDTO profile = RequestContext.GSON.fromJson(payload, PaymentProfileDTO.class);
        return userDAO.updatePaymentProfile(ctx.currentUserId(), profile)
                ? new Response("SUCCESS", "Cập nhật thanh toán thành công!", null)
                : new Response("ERROR", "Không thể cập nhật thanh toán!", null);
    }

    private Response getNotifications(RequestContext ctx, String payload) {
        List<Notification> notifications = NotificationDAO.getByUser(ctx.currentUserId());
        return new Response("SUCCESS", "Lấy thông báo thành công!", RequestContext.GSON.toJson(notifications));
    }

    private Response markNotificationsRead(RequestContext ctx, String payload) {
        return NotificationDAO.markAllRead(ctx.currentUserId())
                ? new Response("SUCCESS", "Đã đánh dấu thông báo đã đọc!", null)
                : new Response("ERROR", "Không thể đánh dấu thông báo!", null);
    }
}
