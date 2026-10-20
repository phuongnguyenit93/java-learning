package com.example.learning.oop.lifecycle.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Constructor, static factory and builder all enforce the same invariants.
 * Copies caller-owned collections so enrollment state cannot be mutated through aliases.
 */
public final class CourseEnrollment {
    private final String courseCode;
    private final int capacity;
    private final List<String> students;

    public CourseEnrollment(String courseCode, int capacity) {
        this(courseCode, capacity, List.of());
    }

    public CourseEnrollment(String courseCode, int capacity, List<String> students) {
        if (courseCode == null || !courseCode.matches("[A-Za-z0-9_-]{1,40}")) {
            throw new IllegalArgumentException("courseCode must be 1-40 letters, digits, '_' or '-'");
        }
        if (capacity < 1 || capacity > 100) {
            throw new IllegalArgumentException("capacity must be between 1 and 100");
        }
        if (students == null || students.size() > capacity) {
            throw new IllegalArgumentException("students must be present and fit capacity");
        }
        ArrayList<String> copy = new ArrayList<>(students.size());
        HashSet<String> unique = new HashSet<>();
        for (String student : students) {
            if (student == null || !student.matches("[A-Za-z0-9_-]{1,40}")) {
                throw new IllegalArgumentException("Each student ID must be 1-40 letters, digits, '_' or '-'");
            }
            if (!unique.add(student)) {
                throw new IllegalArgumentException("Duplicate student ID: " + student);
            }
            copy.add(student);
        }
        this.courseCode = courseCode;
        this.capacity = capacity;
        this.students = List.copyOf(copy);
    }

    public static CourseEnrollment of(String courseCode, int capacity, List<String> students) {
        return new CourseEnrollment(courseCode, capacity, students);
    }

    public static Builder builder(String courseCode, int capacity) {
        return new Builder(courseCode, capacity);
    }

    public String courseCode() {
        return courseCode;
    }

    public int capacity() {
        return capacity;
    }

    public List<String> students() {
        return students;
    }

    public int availableSeats() {
        return capacity - students.size();
    }

    public static final class Builder {
        private final String courseCode;
        private final int capacity;
        private final List<String> students = new ArrayList<>();

        private Builder(String courseCode, int capacity) {
            this.courseCode = courseCode;
            this.capacity = capacity;
        }

        public Builder addStudent(String studentId) {
            students.add(studentId);
            return this;
        }

        public CourseEnrollment build() {
            return new CourseEnrollment(courseCode, capacity, students);
        }
    }
}
