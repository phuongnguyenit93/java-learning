package com.example.learning.oop.inheritance.service;

import com.example.learning.oop.inheritance.dto.InheritanceExperimentDto.PayrollRequest;
import com.example.learning.oop.inheritance.dto.InheritanceExperimentDto.PayrollResponse;
import com.example.learning.oop.inheritance.model.Employee;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public final class PayrollInheritanceService {
    public PayrollResponse calculate(PayrollRequest request) {
        if (request == null || request.kind() == null || request.kind().isBlank()) {
            throw new IllegalArgumentException("kind must be SALARIED or HOURLY");
        }
        String kind = request.kind().trim().toUpperCase(Locale.ROOT);
        Employee employee = switch (kind) {
            case "SALARIED" -> {
                if (request.hourlyRateVnd() != 0 || request.workedHours() != 0) {
                    throw new IllegalArgumentException("Salaried employees must not supply hourly fields");
                }
                yield new Employee.Salaried(request.employeeId(), request.employeeName(),
                        request.monthlySalaryVnd());
            }
            case "HOURLY" -> {
                if (request.monthlySalaryVnd() != 0) {
                    throw new IllegalArgumentException("Hourly employees must not supply monthlySalaryVnd");
                }
                yield new Employee.Hourly(request.employeeId(), request.employeeName(),
                        request.hourlyRateVnd(), request.workedHours());
            }
            default -> throw new IllegalArgumentException("Unknown employee kind: " + request.kind());
        };
        // The reference type is Employee; runtime overrides calculate the wage.
        return new PayrollResponse(employee.employeeId(), employee.employeeName(), kind,
                employee.getClass().getSimpleName(), Employee.class.getSimpleName(),
                employee.calculation(), employee.monthlyPayVnd(), true);
    }
}
