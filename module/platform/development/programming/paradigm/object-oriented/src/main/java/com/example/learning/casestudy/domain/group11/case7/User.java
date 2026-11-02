package com.example.learning.casestudy.domain.group11.case7;

import java.util.Map;

public final class User {
    private final String id, role, accessToken, cardNumber;
    public User(String id, String role, String token, String pan) {
        this.id = id;
        this.role = role;
        accessToken = token;
        cardNumber = pan;
    }
    public Map<String, String> safeLogFields() {
        return Map.of("userId", id, "role", role);
    }
    @Override
    public String toString() {
        return "User{userId=" + id + ", role=" + role + "}";
    }
}
