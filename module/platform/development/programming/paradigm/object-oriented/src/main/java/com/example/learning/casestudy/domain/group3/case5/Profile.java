package com.example.learning.casestudy.domain.group3.case5;

import com.example.learning.casestudy.dto.group3.case5.AddressView;

public class Profile {
    private String street ="Main";
    public AddressView address() {
        return new AddressView(street);
    }
    public void change(String value) {
        if (value == null || value.isBlank())throw new IllegalArgumentException();
        street = value;
    }
}
