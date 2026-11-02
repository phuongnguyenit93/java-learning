package com.example.learning.casestudy.domain.group13.case2;

import java.util.concurrent.atomic.AtomicBoolean;

public final class Job {
    private final AtomicBoolean completed = new AtomicBoolean();
    public void finish() {
        completed.set(true);
    }
    public boolean isCompleted() {
        return completed.get();
    }
}
