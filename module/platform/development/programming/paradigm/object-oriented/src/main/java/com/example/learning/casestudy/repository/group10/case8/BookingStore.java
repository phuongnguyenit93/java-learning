package com.example.learning.casestudy.repository.group10.case8;



public class BookingStore {

    public boolean reserved;

    public void reserve(){
        reserved=true;
    }

    public void release(){
        reserved=false;
    }

}
