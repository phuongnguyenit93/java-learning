package com.example.learning.casestudy.application.group3.case13;

public class Broken {
    public String state ="NEW";
    public void pay(Runnable notify) {
        state ="PAID";
        notify.run();
    }
}
