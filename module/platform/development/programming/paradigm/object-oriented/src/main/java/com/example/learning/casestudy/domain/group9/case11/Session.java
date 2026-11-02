package com.example.learning.casestudy.domain.group9.case11;

import com.example.learning.casestudy.domain.group9.case11.ActivePolicy;
import com.example.learning.casestudy.domain.group9.case11.Phase;
import com.example.learning.casestudy.port.group9.case11.StatePolicy;

public final class Session {
    private Phase phase = Phase.NEW;
    private int attempts;
    private final StatePolicy activePolicy = new ActivePolicy();
    public void activate() {
        if (phase != Phase.NEW) throw new IllegalStateException();
        phase = Phase.ACTIVE;
    }
    public void attempt() {
        if (phase != Phase.ACTIVE) throw new IllegalStateException();
        attempts++;
    }
    public void close() {
        if (phase != Phase.ACTIVE) throw new IllegalStateException();
        phase = activePolicy.transition(Phase.CLOSED);
    }
    public int attempts() { return attempts; }
    public Phase phase() { return phase; }
}
