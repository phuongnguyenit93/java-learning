package com.example.learning.casestudy.application.group7.case6;

import com.example.learning.casestudy.domain.group7.case6.Customer6;

public final class Checkout {
    public String decision(Customer6 c, long cents){
        return c.canPurchase(cents)?"APPROVED":"DECLINED";

    }
}
