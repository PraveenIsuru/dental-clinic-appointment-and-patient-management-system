package com.mycompany.sunrisedentalclinic.model;

public record User(int userId, String username, String fullName, String email,
        String role, boolean active) {

    @Override
    public String toString() {
        return fullName + " (" + role + ")";
    }
}
