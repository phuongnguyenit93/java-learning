package com.example.learning.casestudy.repository.group10.case8;



public class BillingStore {

    public boolean charged;

    public void charge(boolean available){

        if(!available)throw new IllegalStateException("payment unavailable");

        charged=true;

    }

}
