package com.example.learning.casestudy.domain.group9.case11;

import com.example.learning.casestudy.domain.group9.case11.Phase;
import com.example.learning.casestudy.port.group9.case11.StatePolicy;

public final class ActivePolicy implements StatePolicy {
    public Phase transition(Phase next) {
        if (next != Phase.CLOSED) throw new IllegalStateException("Illegal transition");
        return Phase.CLOSED;
    }
}
