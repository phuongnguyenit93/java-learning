package com.example.learning.casestudy.application.group7.case9;

import com.example.learning.casestudy.adapter.group7.case9.SharedLogger;

public final class Service9 implements AutoCloseable {
    private final SharedLogger logger; // Borrowed, not owned.
    private boolean closed;
    public Service9(SharedLogger logger){
        this.logger = logger;

    }
    public void handle(){
        if (closed)throw new IllegalStateException("service closed");
        logger.log("handled");

    }
    @Override public void close(){
        closed = true;

    }
}
