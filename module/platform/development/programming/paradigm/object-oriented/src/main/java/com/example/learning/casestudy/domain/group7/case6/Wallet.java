package com.example.learning.casestudy.domain.group7.case6;

public final class Wallet {
    private final long cents;
    public Wallet(long cents){
        this.cents = cents;

    }
    public boolean canCover(long amount){
        return amount>=0 && cents>=amount;

    }
}
