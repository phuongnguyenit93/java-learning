package com.example.learning.casestudy.application.group10.case9;


import com.example.learning.casestudy.domain.group10.case9.Ticket;

import java.time.Duration;

import java.time.Instant;


public class CancelTicket {

    private final Duration cutoff=Duration.ofHours(24);

    public Ticket cancel(Ticket ticket,Instant now){

        if(ticket.cancelled())throw new IllegalStateException("already cancelled");

        if(now.isAfter(ticket.departure().minus(cutoff)))
            throw new IllegalStateException("cutoff passed");

        return ticket.cancel();

    }

}
