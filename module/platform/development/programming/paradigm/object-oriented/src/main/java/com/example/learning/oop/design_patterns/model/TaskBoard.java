package com.example.learning.oop.design_patterns.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Request-local receiver of commands, with immutable task values and guarded edits. */
public final class TaskBoard {
    public record Task(String id, String title, boolean done) {
        public Task {
            if (id == null || !id.matches("[A-Za-z0-9-]{2,24}")) {
                throw new IllegalArgumentException("task id must be 2..24 letters/digits/hyphens");
            }
            if (title == null || title.isBlank() || title.length() > 100) {
                throw new IllegalArgumentException("title must contain 1..100 characters");
            }
            title = title.strip();
        }
    }

    private final Map<String, Task> tasks = new LinkedHashMap<>();
    public List<Task> snapshot() { return List.copyOf(tasks.values()); }

    public void add(String id, String title) {
        Task task = new Task(id, title, false);
        if (tasks.containsKey(id)) throw new IllegalStateException("duplicate task id");
        if (tasks.size() >= 20) throw new IllegalStateException("task capacity of 20 exceeded");
        tasks.put(id, task);
    }

    public boolean isDone(String id) {
        Task task = require(id);
        return task.done();
    }

    public void setDone(String id, boolean done) {
        Task old = require(id);
        if (old.done() == done) throw new IllegalStateException("task state already " + done);
        tasks.put(id, new Task(id, old.title(), done));
    }

    public void remove(String id) {
        Task old = require(id);
        tasks.remove(old.id());
    }

    private Task require(String id) {
        Task task = tasks.get(id);
        if (task == null) throw new IllegalStateException("unknown task id");
        return task;
    }
}
