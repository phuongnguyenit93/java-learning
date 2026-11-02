package com.example.learning.casestudy.application.group4.case3;

import com.example.learning.casestudy.port.group4.case3.InvoiceReader;

public final class ReportingService {
    private final InvoiceReader reader;
    public ReportingService(InvoiceReader reader) { this.reader = reader; }
    public String report(String id) { return reader.read(id); }
}
