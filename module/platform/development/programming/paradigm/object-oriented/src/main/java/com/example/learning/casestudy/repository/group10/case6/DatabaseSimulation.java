package com.example.learning.casestudy.repository.group10.case6;


import com.example.learning.casestudy.domain.group10.case6.Event6;

import java.util.ArrayList;

import java.util.HashMap;

import java.util.List;

import java.util.Map;


public class DatabaseSimulation {

    public final Map<String,String> orders=new HashMap<>();

    public final List<Event6> outbox=new ArrayList<>();

    public void commit(String orderId,boolean valid){

        if(!valid)throw new IllegalArgumentException("rollback simulated");

        orders.put(orderId,"PAID");

        outbox.add(new Event6("evt-"+orderId,orderId));

    }

}
