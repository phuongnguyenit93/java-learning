package com.example.learning.casestudy.domain.group10.case10;


import com.example.learning.casestudy.domain.group10.case10.Status;


public class Order10 {

    private Status status=Status.NEW;

    public Status status(){
        return status;
    }

    public void recordPayment(){

        if(status!=Status.NEW)throw new IllegalStateException("cannot pay");

        status=Status.PAID;

    }

    public void refund(){

        if(status!=Status.PAID)throw new IllegalStateException("not paid");

        status=Status.REFUNDED;

    }

}
