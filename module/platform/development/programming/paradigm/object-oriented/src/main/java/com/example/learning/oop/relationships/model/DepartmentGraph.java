package com.example.learning.oop.relationships.model;

import com.example.learning.oop.relationships.dto.RelationshipDtos.DepartmentInput;
import com.example.learning.oop.relationships.dto.RelationshipDtos.DepartmentView;
import com.example.learning.oop.relationships.dto.RelationshipDtos.EmployeeAssignment;
import com.example.learning.oop.relationships.dto.RelationshipDtos.EmployeeInput;
import com.example.learning.oop.relationships.dto.RelationshipDtos.TransferAction;
import com.example.learning.oop.relationships.dto.RelationshipDtos.TransferEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bidirectional association: e.department == d iff d.members contains e.
 * The same identity is never held by two departments; rejected moves do not
 * detach the employee from its original department.
 */
public final class DepartmentGraph {
    private final Map<String, Department> departments = new LinkedHashMap<>();
    private final Map<String, Employee> employees = new LinkedHashMap<>();

    public DepartmentGraph(List<DepartmentInput> departmentInputs, List<EmployeeInput> employeeInputs) {
        for (DepartmentInput input : RelationshipInputs.bounded("departments", departmentInputs, 30, false)) {
            String code = RelationshipInputs.nonBlank("department code", input.code());
            if (input.capacity() < 1 || input.capacity() > 1000
                    || departments.putIfAbsent(code, new Department(code, input.capacity())) != null) {
                throw new IllegalArgumentException("Invalid or duplicate department: " + code);
            }
        }
        for (EmployeeInput input : RelationshipInputs.bounded("employees", employeeInputs, 100, true)) {
            String id = RelationshipInputs.nonBlank("employee id", input.id());
            if (employees.putIfAbsent(id, new Employee(id)) != null) {
                throw new IllegalArgumentException("Duplicate employee: " + id);
            }
        }
        // First validate all references before applying the initial links.
        for (EmployeeInput input : employeeInputs) {
            if (input.departmentCode() != null && !departments.containsKey(input.departmentCode())) {
                throw new IllegalArgumentException("Unknown initial department: " + input.departmentCode());
            }
        }
        for (EmployeeInput input : employeeInputs) {
            if (input.departmentCode() != null
                    && !move(employees.get(input.id().trim()), departments.get(input.departmentCode()))) {
                throw new IllegalArgumentException("Initial department exceeds capacity");
            }
        }
        requireInvariant();
    }

    /** Pre-validates the action list; operations rejected by business rules return events. */
    public List<TransferEvent> transfer(List<TransferAction> actions) {
        List<TransferAction> checked = RelationshipInputs.bounded("actions", actions, 40, false);
        for (TransferAction action : checked) {
            String id = RelationshipInputs.nonBlank("employeeId", action.employeeId());
            if (!employees.containsKey(id)) throw new IllegalArgumentException("Unknown employee: " + id);
            if (action.targetDepartmentCode() != null
                    && !departments.containsKey(action.targetDepartmentCode())) {
                throw new IllegalArgumentException("Unknown target department: " + action.targetDepartmentCode());
            }
        }
        List<TransferEvent> events = new ArrayList<>();
        for (TransferAction action : checked) {
            Employee employee = employees.get(action.employeeId().trim());
            Department next = departments.get(action.targetDepartmentCode());
            String previous = employee.department == null ? null : employee.department.code;
            boolean accepted = move(employee, next);
            requireInvariant();
            events.add(new TransferEvent(events.size() + 1, employee.id, previous,
                    next == null ? null : next.code, accepted,
                    accepted ? (previous == null && next == null ? "UNCHANGED" :
                            previous != null && previous.equals(action.targetDepartmentCode())
                                    ? "UNCHANGED" : "MOVED") : "DEPARTMENT_FULL"));
        }
        return List.copyOf(events);
    }

    private boolean move(Employee employee, Department next) {
        if (employee.department == next) return true;
        if (next != null && next.members.size() == next.capacity) return false;
        Department previous = employee.department;
        if (previous != null) previous.members.remove(employee);
        employee.department = next;
        if (next != null) next.members.add(employee);
        return true;
    }

    public boolean invariantHolds() {
        for (Department d : departments.values()) {
            if (d.members.size() > d.capacity) return false;
            for (Employee e : d.members) if (e.department != d) return false;
        }
        for (Employee e : employees.values()) {
            for (Department d : departments.values()) {
                if ((e.department == d) != d.members.contains(e)) return false;
            }
        }
        return true;
    }

    private void requireInvariant() {
        if (!invariantHolds()) throw new IllegalStateException("Bidirectional association inconsistent");
    }

    public List<DepartmentView> departments() {
        return departments.values().stream().sorted(Comparator.comparing(d -> d.code))
                .map(d -> new DepartmentView(d.code, d.capacity,
                        d.members.stream().map(e -> e.id).sorted().toList())).toList();
    }

    public List<EmployeeAssignment> employees() {
        return employees.values().stream().sorted(Comparator.comparing(e -> e.id))
                .map(e -> new EmployeeAssignment(e.id, e.department == null ? null : e.department.code))
                .toList();
    }

    private static final class Employee {
        private final String id;
        private Department department;
        private Employee(String id) { this.id = id; }
    }

    private static final class Department {
        private final String code;
        private final int capacity;
        private final Set<Employee> members = new LinkedHashSet<>();
        private Department(String code, int capacity) {
            this.code = code;
            this.capacity = capacity;
        }
    }
}
