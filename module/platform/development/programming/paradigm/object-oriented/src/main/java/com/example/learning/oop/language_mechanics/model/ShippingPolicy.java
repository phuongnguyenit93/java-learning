package com.example.learning.oop.language_mechanics.model;

/** Nominal Java interface: different implementations share one observable contract. */
public interface ShippingPolicy {
    long quoteVnd(int weightGrams);
}
