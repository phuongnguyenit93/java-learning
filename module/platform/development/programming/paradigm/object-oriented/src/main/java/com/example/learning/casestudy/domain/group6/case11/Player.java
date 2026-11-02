package com.example.learning.casestudy.domain.group6.case11;

import com.example.learning.casestudy.domain.group6.case11.State;

public class Player {
    private State state = State.IDLE;
    public State state() { return state; }
    public void change(State next) {
        boolean allowed = switch (state) {
            case IDLE -> next == State.RUNNING || next == State.DEAD;
            case RUNNING -> next == State.IDLE || next == State.JUMPING || next == State.DEAD;
            case JUMPING -> next == State.RUNNING || next == State.DEAD;
            case DEAD -> false;
        };
        if (!allowed) throw new IllegalStateException(state + " -> " + next);
        state = next;
    }
}
