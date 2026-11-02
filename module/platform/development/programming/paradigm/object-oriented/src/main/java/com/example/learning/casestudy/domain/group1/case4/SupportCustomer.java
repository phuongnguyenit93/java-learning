package com.example.learning.casestudy.domain.group1.case4;

import com.example.learning.casestudy.domain.group1.case4.CustomerId;

public final class SupportCustomer {
    private final CustomerId id;
    private boolean priorityCase;
    public SupportCustomer(CustomerId id) { this.id = id; }
    public void markPriority() { priorityCase = true; }
    public boolean isPriority() { return priorityCase; }
    public CustomerId id() { return id; }
}
