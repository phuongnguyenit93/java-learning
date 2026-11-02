package com.example.learning.casestudy.application.group7.case8;

import java.util.ArrayList;
import java.util.List;

public final class Tab implements AutoCloseable {
    public final List<String> received = new ArrayList<>();
    private final AutoCloseable subscription;
    public Tab(Bus bus){
        subscription = bus.subscribe(received::add);

    }
    @Override public void close() throws Exception {
        subscription.close();

    }
}
