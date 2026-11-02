package com.example.learning.casestudy.domain.group7.case6;

public final class Customer6 {
    private final Wallet wallet;
    public Customer6(Wallet wallet){
        this.wallet = wallet;

    }
    public boolean canPurchase(long cents){
        return wallet.canCover(cents);

    }
}
