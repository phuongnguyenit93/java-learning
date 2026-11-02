package com.example.learning.casestudy.domain.group6.case1;

import com.example.learning.casestudy.port.group6.case1.EmployeeReport;

public record Designer(String name) implements EmployeeReport {
    public String report() { return "DESIGN:" + name; }
}
