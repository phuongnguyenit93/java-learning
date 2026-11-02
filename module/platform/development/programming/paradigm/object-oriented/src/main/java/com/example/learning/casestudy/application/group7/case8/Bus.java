package com.example.learning.casestudy.application.group7.case8;

import com.example.learning.casestudy.port.group7.case8.Listener;
import java.util.ArrayList;
import java.util.List;

public final class Bus {
    public final List<Listener> listeners = new ArrayList<>();
    public AutoCloseable subscribe(Listener listener){
        listeners.add(listener);
        return ()->listeners.remove(listener);

    }
    public void publish(String value){
        for (Listener l:List.copyOf(listeners))l.onEvent(value);

    }
    public int listenerCount(){
        return listeners.size();

    }
}
