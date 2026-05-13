package com.auction.server.auth;

import com.auction.shared.model.User;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class SessionRegistry {
    private static final Duration DEFAULT_TTL = Duration.ofHours(8);
    private static final SessionRegistry INSTANCE = new SessionRegistry(DEFAULT_TTL);

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration ttl;

    public SessionRegistry(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Session TTL must be positive.");
        }
        this.ttl = ttl;
    }

    public static SessionRegistry getInstance() {
        return INSTANCE;
    }

    public SessionToken createSession(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User is required.");
        }
        user.setPassword(null);
        String token = createToken();
        long expiresAt = System.currentTimeMillis() + ttl.toMillis();
        sessions.put(token, new Session(user, expiresAt));
        return new SessionToken(token, user, expiresAt);
    }

    public Optional<User> validate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        Session session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (session.expiresAt() <= System.currentTimeMillis()) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(session.user());
    }

    public boolean revoke(String token) {
        return token != null && sessions.remove(token) != null;
    }

    public void clearExpired() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    }

    int activeSessionCount() {
        clearExpired();
        return sessions.size();
    }

    private String createToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record Session(User user, long expiresAt) {}

    public record SessionToken(String token, User user, long expiresAt) {}
}
