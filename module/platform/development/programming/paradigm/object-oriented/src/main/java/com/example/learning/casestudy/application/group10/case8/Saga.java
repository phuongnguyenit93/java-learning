package com.example.learning.casestudy.application.group10.case8;


import com.example.learning.casestudy.repository.group10.case8.BillingStore;

import com.example.learning.casestudy.repository.group10.case8.BookingStore;


public class Saga {

    private final BookingStore booking;

    private final BillingStore billing;

    public Saga(BookingStore a,BillingStore b){
        booking=a;
        billing=b;
    }

    public boolean execute(boolean paymentAvailable){

        booking.reserve();

        try {

            billing.charge(paymentAvailable);

            return true;

        }
        catch(RuntimeException error) {

            booking.release();
            // Compensation cho bước đã thành công.
            return false;

        }

    }

}
