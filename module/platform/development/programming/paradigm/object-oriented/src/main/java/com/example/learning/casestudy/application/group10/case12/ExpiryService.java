package com.example.learning.casestudy.application.group10.case12;


import java.time.Clock;

import java.time.Instant;

import java.time.LocalDate;


public class ExpiryService {

    private final Clock clock;

    public ExpiryService(Clock clock){
        this.clock=clock;
    }

    public boolean expired(Instant deadline){

        if(deadline==null)throw new IllegalArgumentException("deadline");

        return !Instant.now(clock).isBefore(deadline);

    }

    public LocalDate today(){
        return LocalDate.now(clock);
    }

}
