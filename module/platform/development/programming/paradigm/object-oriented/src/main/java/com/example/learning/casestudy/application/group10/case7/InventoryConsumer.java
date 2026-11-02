package com.example.learning.casestudy.application.group10.case7;


import com.example.learning.casestudy.domain.group10.case7.Event7;

import java.util.HashSet;

import java.util.Set;


public class InventoryConsumer {

    public int stock=10,emails;

    public final Set<String> processed=new HashSet<>();

    public void handle(Event7 e){

        if(e.eventId()==null||e.eventId().isBlank()||e.units()<=0)
            throw new IllegalArgumentException();

        if(!processed.add(e.eventId()))return;

        stock-=e.units();

        emails++;

    }

}
