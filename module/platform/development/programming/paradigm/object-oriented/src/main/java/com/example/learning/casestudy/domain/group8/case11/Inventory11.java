package com.example.learning.casestudy.domain.group8.case11;

import java.util.Set;

public final class Inventory11 {
    private final Set<String> available;
    public Inventory11(Set<String> available) { this.available = Set.copyOf(available); }
    public boolean canShip(String sku) { return available.contains(sku); }
}
