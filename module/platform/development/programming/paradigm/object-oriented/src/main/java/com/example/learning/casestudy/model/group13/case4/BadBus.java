package com.example.learning.casestudy.model.group13.case4;

import java.util.ArrayList;
import java.util.List;

public final class BadBus {
    public final List<Runnable> listeners = new ArrayList<>();
    public synchronized void publish(){
        for (Runnable r:listeners)r.run(); // Callback vẫn giữ monitor.
    }
}
