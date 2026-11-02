package com.example.learning.casestudy.domain.group2.case11;

import java.time.Instant;

final class State {
    final String userId;
    final Instant expiresAt;
    boolean revoked;
    public State(String userId, Instant expiresAt) {
        this.userId = userId;
        this.expiresAt = expiresAt;
    }
}
