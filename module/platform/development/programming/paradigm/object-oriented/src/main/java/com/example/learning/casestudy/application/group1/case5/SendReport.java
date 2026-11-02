package com.example.learning.casestudy.application.group1.case5;

import com.example.learning.casestudy.application.group1.case5.ReportFormatter;
import com.example.learning.casestudy.port.group1.case5.EmailGateway;
import com.example.learning.casestudy.port.group1.case5.SalesRepository;

public final class SendReport {
    private final SalesRepository repository;
    private final EmailGateway email;
    private final ReportFormatter formatter;
    public SendReport(SalesRepository r, EmailGateway e, ReportFormatter f) {
        repository = r;
        email = e;
        formatter = f;
    }
    public void execute() { email.send(formatter.format(repository.findAll())); }
}
