package com.example.learning.casestudy.application.group2.case2;

import com.example.learning.casestudy.domain.group2.case2.CustomerId;
import com.example.learning.casestudy.domain.group2.case2.Phone;
import com.example.learning.casestudy.model.group2.case2.Customer2;

public final class Builder2 {
    private CustomerId id;
    private Phone phone;
    public Builder2 id(CustomerId value) {
        id = value;
        return this;
    }
    public Builder2 phone(Phone value) {
        phone = value;
        return this;
    }
    public Customer2 build() {
        if (id == null || phone == null)
            throw new IllegalStateException("Required fields");
        return new Customer2(id, phone);
    }
}
