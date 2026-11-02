package com.example.learning.casestudy.domain.group3.case9;

public record State(int subtotal, int discount) {
    public State {
        if (subtotal<0 || discount<0 || discount>subtotal) throw new IllegalArgumentException();
    }
    public int total() {
        return subtotal-discount;
    }
}
