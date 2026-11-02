package com.example.learning.casestudy.application.group12.case4;

import com.example.learning.casestudy.domain.group12.case4.Order4;
import com.example.learning.casestudy.domain.group12.case4.Outcome;
import com.example.learning.casestudy.domain.group12.case4.Pricing;
import com.example.learning.casestudy.domain.group12.case4.Validator;
import com.example.learning.casestudy.port.group12.case4.Store4;

public class RefactoredOrderService {
    private final Validator validator = new Validator();
    private final Pricing pricing = new Pricing();
    private final Store4 store;
    public RefactoredOrderService(Store4 store) {
        this.store = store;
    }
    public Outcome checkout(Order4 order) {
        validator.verify(order);
        Outcome result = new Outcome(pricing.total(order), "PAID");
        store.save(result);
        return result;
    }
}
