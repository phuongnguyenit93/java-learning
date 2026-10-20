package com.example.learning.oop.inheritance.model;

/**
 * A superclass protects inherited state and exposes a stable salary contract;
 * every concrete employee provides its own calculation by dynamic dispatch.
 */
public abstract class Employee {
    private final String employeeId;
    private final String employeeName;

    protected Employee(String employeeId, String employeeName) {
        if (employeeId == null || employeeId.isBlank()
                || employeeName == null || employeeName.isBlank()) {
            throw new IllegalArgumentException("Employee ID and name must not be blank");
        }
        this.employeeId = employeeId.trim();
        this.employeeName = employeeName.trim();
    }

    public final String employeeId() {
        return employeeId;
    }

    public final String employeeName() {
        return employeeName;
    }

    public abstract long monthlyPayVnd();

    public abstract String calculation();

    public static final class Salaried extends Employee {
        private final long monthlySalaryVnd;

        public Salaried(String id, String name, long monthlySalaryVnd) {
            super(id, name);
            if (monthlySalaryVnd < 0) {
                throw new IllegalArgumentException("monthlySalaryVnd must not be negative");
            }
            this.monthlySalaryVnd = monthlySalaryVnd;
        }

        @Override
        public long monthlyPayVnd() {
            return monthlySalaryVnd;
        }

        @Override
        public String calculation() {
            return "FIXED_MONTHLY";
        }
    }

    public static final class Hourly extends Employee {
        private final long hourlyRateVnd;
        private final int workedHours;

        public Hourly(String id, String name, long hourlyRateVnd, int workedHours) {
            super(id, name);
            if (hourlyRateVnd < 0 || workedHours < 0 || workedHours > 744) {
                throw new IllegalArgumentException("hourlyRateVnd must be nonnegative and hours 0..744");
            }
            this.hourlyRateVnd = hourlyRateVnd;
            this.workedHours = workedHours;
        }

        @Override
        public long monthlyPayVnd() {
            return Math.multiplyExact(hourlyRateVnd, workedHours);
        }

        @Override
        public String calculation() {
            return "HOURLY_RATE_TIMES_HOURS";
        }
    }
}
