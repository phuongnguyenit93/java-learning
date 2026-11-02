package com.example.learning.casestudy.application.group10.case6;


import com.example.learning.casestudy.domain.group10.case6.Event6;

import com.example.learning.casestudy.repository.group10.case6.DatabaseSimulation;

import java.util.HashSet;

import java.util.List;

import java.util.Set;


public class Dispatcher {

    final Set<String> sent=new HashSet<>();

    public void sendPending(DatabaseSimulation db,List<String> deliveries){

        for(Event6 e:db.outbox){

            if(sent.add(e.id()))deliveries.add(e.orderId());

        }

    }

}
