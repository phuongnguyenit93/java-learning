package com.example.learning.casestudy.model.group13.case8;

public final class BadPool {
    public static final MutableContext SHARED = new MutableContext();
    public static MutableContext forUser(String user){
        SHARED.user = user;
        return SHARED; // Quên clear roles.
    }
}
