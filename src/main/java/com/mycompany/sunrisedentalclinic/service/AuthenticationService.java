package com.mycompany.sunrisedentalclinic.service;

import com.mycompany.sunrisedentalclinic.dao.UserDAO;
import com.mycompany.sunrisedentalclinic.model.User;
import com.mycompany.sunrisedentalclinic.util.PasswordUtil;

public class AuthenticationService {

    private final UserDAO dao = new UserDAO();

    public User login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return null;
        }
        try {
            User u = dao.authenticate(username.trim(), PasswordUtil.hashPassword(password));
            return u != null && u.active() ? u : null;
        } catch (Exception e) {
            throw new RuntimeException("Login failed: " + e.getMessage(), e);
        }
    }
}
