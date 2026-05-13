package com.auction.server.auth;

import com.auction.server.dao.UserDAO;
import com.auction.shared.model.User;

import java.util.Optional;

public class LoginService {
    private final UserDAO userDAO;

    public LoginService() {
        this(new UserDAO());
    }

    LoginService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    public Optional<User> authenticate(String loginIdentifier, String password) {
        if (loginIdentifier == null || loginIdentifier.isBlank() || password == null || password.isBlank()) {
            return Optional.empty();
        }

        User user = userDAO.loginUser(loginIdentifier.trim(), password);
        if (user == null) {
            return Optional.empty();
        }
        user.setPassword(null);
        return Optional.of(user);
    }
}
