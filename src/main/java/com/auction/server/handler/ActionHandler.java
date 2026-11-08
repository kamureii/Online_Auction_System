package com.auction.server.handler;

import com.auction.shared.network.Response;

@FunctionalInterface
public interface ActionHandler {
    Response handle(RequestContext ctx, String payload) throws Exception;
}
