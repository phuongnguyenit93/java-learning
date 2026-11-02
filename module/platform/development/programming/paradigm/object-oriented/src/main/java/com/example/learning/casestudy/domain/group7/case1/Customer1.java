package com.example.learning.casestudy.domain.group7.case1;

public final class Customer1 {
    public final String id;
    private Address current;
    public Customer1(String id, Address address) {
        this.id = id;
        current = address;

    }
    public Address currentAddress() {
        return current;

    }
    public void changeAddress(Address next) {
        current = next;

    }
}
