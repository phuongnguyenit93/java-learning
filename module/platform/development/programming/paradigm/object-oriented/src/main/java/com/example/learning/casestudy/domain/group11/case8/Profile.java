package com.example.learning.casestudy.domain.group11.case8;

import java.util.List;

public final class Profile {
    private final List<Address> addresses;
    public Profile(List<Address> input) {
        addresses = input.stream().map(Address::new).toList();
        // deep copy elements
    }
    public Profile(Profile other) {
        this(other.addresses);
    }
    public void relocateFirst(String city) {
        addresses.get(0).city = city;
    }
    public String firstCity() {
        return addresses.get(0).city;
    }
}
