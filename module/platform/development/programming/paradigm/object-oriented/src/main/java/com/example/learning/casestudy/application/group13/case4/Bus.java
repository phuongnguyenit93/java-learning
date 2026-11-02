package com.example.learning.casestudy.application.group13.case4;

import com.example.learning.casestudy.port.group13.case4.Listener;
import java.util.ArrayList;
import java.util.List;

public final class Bus {
    private final Object mutex = new Object();
    private final List<Listener> listeners = new ArrayList<>();
    public void subscribe(Listener l){
        synchronized(mutex){
            listeners.add(l);
        }
    }
    public void unsubscribe(Listener l){
        synchronized(mutex){
            listeners.remove(l);
        }
    }
    public void publish(String value){
        List<Listener> snapshot;
        synchronized(mutex){
            snapshot = List.copyOf(listeners);
        }
        for (Listener l:snapshot)l.accept(value);
    }
    public boolean holdingBusLock(){
        return Thread.holdsLock(mutex);
    }
    public int count(){
        synchronized(mutex){
            return listeners.size();
        }
    }
}
