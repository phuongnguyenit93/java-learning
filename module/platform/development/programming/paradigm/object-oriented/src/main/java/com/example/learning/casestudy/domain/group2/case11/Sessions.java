package com.example.learning.casestudy.domain.group2.case11;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class Sessions {
    private final Map<String,State> sessions = new HashMap<>();
    public void create(String token, String user, Instant expiry) {
        sessions.put(token, new State(user, expiry));
    }
    public String authorize(String token, Instant now) {
        State s = sessions.get(token);
        if (s == null || s.revoked || !now.isBefore(s.expiresAt))
            throw new SecurityException("Session expired or revoked");
        return s.userId;
    }
    public void logout(String token) {
        State s = sessions.get(token);
        if (s != null) s.revoked = true;
    }
}
