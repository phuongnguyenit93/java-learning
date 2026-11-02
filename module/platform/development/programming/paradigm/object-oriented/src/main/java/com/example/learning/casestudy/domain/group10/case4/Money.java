package com.example.learning.casestudy.domain.group10.case4;


import java.math.BigDecimal;

import java.math.RoundingMode;


public record Money(String currency,BigDecimal amount){

    public Money {

        if(currency==null||currency.isBlank()||amount==null)
            throw new IllegalArgumentException();

        amount=amount.setScale(2,RoundingMode.HALF_UP);

    }

    public Money plus(Money other){

        if(!currency.equals(other.currency))
            throw new IllegalArgumentException("currency mismatch");

        return new Money(currency,amount.add(other.amount));

    }

}
