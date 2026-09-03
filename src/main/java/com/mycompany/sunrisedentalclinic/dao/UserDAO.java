package com.mycompany.sunrisedentalclinic.dao;

import com.mycompany.sunrisedentalclinic.model.User;
import com.mycompany.sunrisedentalclinic.util.DBConnection;
import com.mycompany.sunrisedentalclinic.util.PasswordUtil;
import java.sql.*;
import java.util.*;

public class UserDAO {

    public User authenticate(String username, String passwordHash) throws SQLException {
        String sql = "SELECT user_id,username,full_name,email,role,status FROM users WHERE username=? AND password=?";
        try (Connection c = DBConnection.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, username);
            p.setString(2, passwordHash);
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? map(r) : null;
            }
        }
    }

    public List<User> findAll() throws SQLException {
        List<User> out = new ArrayList<>();
        try (Connection c = DBConnection.getConnection(); PreparedStatement p = c.prepareStatement("SELECT user_id,username,full_name,email,role,status FROM users ORDER BY user_id"); ResultSet r = p.executeQuery()) {
            while (r.next()) {
                out.add(map(r));
            }
        }
        return out;
    }

    public void create(String username, String rawPassword, String fullName, String email, String role) throws SQLException {
        String sql = "INSERT INTO users(username,password,full_name,email,role,status) VALUES(?,?,?,?,?,1)";
        try (Connection c = DBConnection.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, username);
            p.setString(2, PasswordUtil.hashPassword(rawPassword));
            p.setString(3, fullName);
            p.setString(4, email);
            p.setString(5, role);
            p.executeUpdate();
        }
    }

    public void update(int id, String username, String rawPassword, String fullName, String email, String role, boolean active) throws SQLException {
        boolean changePassword = rawPassword != null && !rawPassword.isBlank();
        String sql = changePassword
                ? "UPDATE users SET username=?,password=?,full_name=?,email=?,role=?,status=? WHERE user_id=?"
                : "UPDATE users SET username=?,full_name=?,email=?,role=?,status=? WHERE user_id=?";
        try (Connection c = DBConnection.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            int i = 1;
            p.setString(i++, username);
            if (changePassword) {
                p.setString(i++, PasswordUtil.hashPassword(rawPassword));
            }
            p.setString(i++, fullName);
            p.setString(i++, email);
            p.setString(i++, role);
            p.setBoolean(i++, active);
            p.setInt(i, id);
            p.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        try (Connection c = DBConnection.getConnection(); PreparedStatement p = c.prepareStatement("DELETE FROM users WHERE user_id=?")) {
            p.setInt(1, id);
            p.executeUpdate();
        }
    }

    private User map(ResultSet r) throws SQLException {
        return new User(r.getInt("user_id"), r.getString("username"), r.getString("full_name"),
                r.getString("email"), r.getString("role"), r.getBoolean("status"));
    }
}
