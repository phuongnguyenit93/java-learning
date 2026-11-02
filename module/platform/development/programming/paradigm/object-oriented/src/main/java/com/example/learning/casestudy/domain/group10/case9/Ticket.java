package com.example.learning.casestudy.domain.group10.case9;


import java.time.Instant;


public record Ticket(String id,Instant departure,boolean cancelled){

    public Ticket cancel(){
        return new Ticket(id,departure,true);
    }

}
