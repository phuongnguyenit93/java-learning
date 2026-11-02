package com.example.learning.casestudy.application.group10.case7;


import com.example.learning.casestudy.domain.group10.case7.Event7;


public class BrokenConsumer {

    public int stock=10,emails;

    public void handle(Event7 e){
        stock-=e.units();
        emails++;
    }

}
