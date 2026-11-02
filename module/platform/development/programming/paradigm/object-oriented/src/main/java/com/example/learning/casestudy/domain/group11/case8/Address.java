package com.example.learning.casestudy.domain.group11.case8;

public final class Address {
    public String city;
    public Address(String city) {
        this.city = city;
    }
    public Address(Address other) {
        this.city = other.city;
    }
}
