package com.auction.server.handler;

import com.auction.server.auth.EmailVerificationService;
import com.auction.server.auth.SessionRegistry;
import com.auction.server.dao.UserDAO;

/** Lắp ráp bảng định tuyến đầy đủ cho socket server. */
public final class ServerRouter {
    private ServerRouter() {}

    public static ActionRouter create() {
        UserDAO userDAO = new UserDAO();
        SessionRegistry sessions = SessionRegistry.getInstance();

        ActionRouter router = new ActionRouter();
        new AuthActions(userDAO, sessions).register(router);
        new ItemActions().register(router);
        new AuctionActions(userDAO).register(router);
        new BidActions(userDAO).register(router);
        new AccountActions(userDAO, new EmailVerificationService()).register(router);
        new OrderActions(userDAO).register(router);
        new AdminActions(userDAO, sessions).register(router);
        return router;
    }
}
