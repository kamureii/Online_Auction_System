package com.auction.server.handler;

import com.auction.shared.network.Response;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Bảng định tuyến action → handler, kèm kiểm soát quyền truy cập và xử lý lỗi tập trung.
 */
public class ActionRouter {
    private static final Logger log = LoggerFactory.getLogger(ActionRouter.class);

    public enum Access { PUBLIC, USER, ADMIN }

    private record Route(Access access, ActionHandler handler) {}

    private final Map<String, Route> routes = new HashMap<>();

    public ActionRouter register(String action, Access access, ActionHandler handler) {
        if (routes.putIfAbsent(action, new Route(access, handler)) != null) {
            throw new IllegalStateException("Action đã được đăng ký: " + action);
        }
        return this;
    }

    public Response dispatch(RequestContext ctx, String action, String payload) {
        Route route = routes.get(action);
        if (route == null) {
            return new Response("ERROR", "Action không hợp lệ.", null);
        }
        if (route.access() != Access.PUBLIC) {
            if (!ctx.isAuthenticated()) {
                return new Response("ERROR", "Vui lòng đăng nhập trước khi thực hiện chức năng này.", null);
            }
            if (!ctx.refreshIfStale()) {
                return new Response("ERROR", "Phiên đăng nhập không còn hiệu lực. Vui lòng đăng nhập lại.", null);
            }
            if (route.access() == Access.ADMIN && !ctx.hasRole("ADMIN")) {
                return new Response("ERROR", "Không có quyền thực hiện chức năng này. Cần vai trò: ADMIN", null);
            }
        }
        try {
            return route.handler().handle(ctx, payload);
        } catch (JsonParseException | IllegalArgumentException | NullPointerException | ClassCastException
                 | IllegalStateException e) {
            log.warn("Request {} không hợp lệ: {}", action, e.toString());
            return new Response("ERROR", "Yêu cầu không hợp lệ.", null);
        } catch (Exception e) {
            log.error("Lỗi xử lý {}: {}", action, e.getMessage(), e);
            return new Response("ERROR", "Không thể xử lý yêu cầu lúc này.", null);
        }
    }
}
