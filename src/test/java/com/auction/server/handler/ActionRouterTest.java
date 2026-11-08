package com.auction.server.handler;

import com.auction.server.dao.UserDAO;
import com.auction.shared.model.Admin;
import com.auction.shared.model.RegularUser;
import com.auction.shared.network.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionRouterTest {
    private final ActionRouter router = new ActionRouter()
            .register("PUBLIC_OK", ActionRouter.Access.PUBLIC, (ctx, p) -> new Response("SUCCESS", "ok", null))
            .register("USER_OK", ActionRouter.Access.USER, (ctx, p) -> new Response("SUCCESS", "ok", null))
            .register("ADMIN_OK", ActionRouter.Access.ADMIN, (ctx, p) -> new Response("SUCCESS", "ok", null))
            .register("BROKEN", ActionRouter.Access.PUBLIC, (ctx, p) -> { throw new IllegalStateException("boom"); })
            .register("CRASH", ActionRouter.Access.PUBLIC, (ctx, p) -> { throw new Exception("db down"); });

    /** UserDAO giả: luôn trả về chính user đang giữ, nên không cần DB. */
    private static final class StubUserDao extends UserDAO {
        @Override
        public com.auction.shared.model.User getUserById(int id) {
            return null;
        }
    }

    private RequestContext anonymous() {
        return new RequestContext("127.0.0.1", new StubUserDao());
    }

    private RequestContext loggedIn(boolean admin) {
        RequestContext ctx = anonymous();
        ctx.setCurrentUser(admin
                ? new Admin(1, "root", null, "Root", "root@example.com")
                : new RegularUser(2, "bob", null, "Bob", "bob@example.com"));
        return ctx;
    }

    @Test
    void publicActionsWorkWithoutLogin() {
        assertEquals("SUCCESS", router.dispatch(anonymous(), "PUBLIC_OK", null).getStatus());
    }

    @Test
    void userActionsRequireLogin() {
        assertEquals("ERROR", router.dispatch(anonymous(), "USER_OK", null).getStatus());
        assertEquals("SUCCESS", router.dispatch(loggedIn(false), "USER_OK", null).getStatus());
    }

    @Test
    void adminActionsRejectNormalUsers() {
        assertEquals("ERROR", router.dispatch(loggedIn(false), "ADMIN_OK", null).getStatus());
        assertEquals("SUCCESS", router.dispatch(loggedIn(true), "ADMIN_OK", null).getStatus());
    }

    @Test
    void unknownActionIsRejected() {
        assertEquals("ERROR", router.dispatch(loggedIn(true), "NOPE", null).getStatus());
    }

    @Test
    void handlerFailuresBecomeGenericErrorsWithoutLeakingDetails() {
        Response broken = router.dispatch(anonymous(), "BROKEN", null);
        Response crash = router.dispatch(anonymous(), "CRASH", null);

        assertEquals("ERROR", broken.getStatus());
        assertEquals("ERROR", crash.getStatus());
        assertTrue(!broken.getMessage().contains("boom"));
        assertTrue(!crash.getMessage().contains("db down"));
    }

    @Test
    void duplicateRegistrationIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> router.register("PUBLIC_OK", ActionRouter.Access.PUBLIC, (ctx, p) -> null));
    }
}
