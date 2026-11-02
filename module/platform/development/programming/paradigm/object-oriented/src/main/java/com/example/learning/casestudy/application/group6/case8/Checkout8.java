package com.example.learning.casestudy.application.group6.case8;

import com.example.learning.casestudy.port.group6.case8.Pricing;
import java.util.Objects;

public class Checkout8 {
    private Pricing pricing;
    public Checkout8(Pricing pricing) { this.pricing = Objects.requireNonNull(pricing); }
    public void choose(Pricing next) { pricing = Objects.requireNonNull(next); }
    public int total(int cents) { return pricing.apply(cents); }
}
