package com.example.learning.casestudy.domain.group1.case8;

import java.time.LocalDate;
import java.util.Objects;

public final class Employee {
    final String employeeId;
    final String fullName;
    final LocalDate birthDate;
    public Employee(String id, String name, LocalDate birth) {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("id");
        employeeId = id;
        fullName = Objects.requireNonNull(name);
        birthDate = Objects.requireNonNull(birth);
    }
    @Override public boolean equals(Object other) {
        return this == other || (other instanceof Employee e
                 && employeeId.equals(e.employeeId));
    }
    @Override public int hashCode() { return employeeId.hashCode(); }
}
