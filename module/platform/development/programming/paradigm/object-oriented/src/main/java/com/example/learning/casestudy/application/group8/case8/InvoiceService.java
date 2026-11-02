package com.example.learning.casestudy.application.group8.case8;

import com.example.learning.casestudy.domain.group8.case8.InvoiceCalculator;

public final class InvoiceService {
    private final InvoiceCalculator calculator;
    public InvoiceService(InvoiceCalculator calculator) { this.calculator = calculator; }
    public long issue(long subtotal) { return calculator.total(subtotal); }
}
