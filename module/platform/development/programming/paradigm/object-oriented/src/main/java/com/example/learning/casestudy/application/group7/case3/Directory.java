package com.example.learning.casestudy.application.group7.case3;

import com.example.learning.casestudy.domain.group7.case3.Employee;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class Directory {
    public final Map<String, Employee> employees =  new HashMap<>();
    public final Map<String, Set<String>> departments =  new HashMap<>();
    public void addDepartment(String id) {
        departments.put(id, new HashSet<>());

    }
    public void hire(Employee e, String dept) {
        if (!departments.containsKey(dept))throw new IllegalArgumentException();
        employees.put(e.id(), e);
        departments.get(dept).add(e.id());

    }
    public void transfer(String employeeId, String from, String to) {
        if (!employees.containsKey(employeeId) || !departments.containsKey(to)
        || !departments.get(from).contains(employeeId))
        throw new IllegalArgumentException();
        departments.get(from).remove(employeeId);
        departments.get(to).add(employeeId);

    }
    public void deleteDepartment(String id) {
        Set<String> members = departments.get(id);
        if (members == null)throw new IllegalArgumentException();
        if (!members.isEmpty())throw new IllegalStateException("reassign first");
        departments.remove(id);

    }
}
