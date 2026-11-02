package com.example.learning.casestudy.utils.group6.case1;

import com.example.learning.casestudy.port.group6.case1.EmployeeReport;
import java.util.List;

public final class Case1Utils {
    private Case1Utils() {}

    public static String aggregate(List<EmployeeReport> people) {
        return String.join("|", people.stream().map(EmployeeReport::report).toList());
    }
}
