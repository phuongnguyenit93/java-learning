package com.example.learning.casestudy.adapter.group7.case9;

public final class SharedLogger implements AutoCloseable {
    private boolean closed;
    private int count;
    public void log(String text){
        if (closed)throw new IllegalStateException("closed");
        count++;

    }
    public int count(){
        return count;

    }
    public boolean closed(){
        return closed;

    }
    @Override public void close(){
        closed = true;

    }
}
