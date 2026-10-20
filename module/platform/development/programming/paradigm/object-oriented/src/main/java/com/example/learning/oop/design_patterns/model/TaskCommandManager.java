package com.example.learning.oop.design_patterns.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Command history owns stacks; Observer sees only successfully committed actions.
 * All objects belong to one HTTP request; no external notifications are sent.
 */
public final class TaskCommandManager {
    public interface Command {
        String id();
        String kind();
        void execute();
        void undo();
    }

    public static final class AddTask implements Command {
        private final TaskBoard board;
        private final String id;
        private final String title;
        public AddTask(TaskBoard board, String id, String title) {
            this.board = Objects.requireNonNull(board);
            this.id = id;
            this.title = title;
        }
        @Override public String id() { return id; }
        @Override public String kind() { return "ADD"; }
        @Override public void execute() { board.add(id, title); }
        @Override public void undo() { board.remove(id); }
    }

    public static final class CompleteTask implements Command {
        private final TaskBoard board;
        private final String id;
        private boolean previouslyDone;
        public CompleteTask(TaskBoard board, String id) {
            this.board = Objects.requireNonNull(board);
            this.id = id;
        }
        @Override public String id() { return id; }
        @Override public String kind() { return "COMPLETE"; }
        @Override public void execute() {
            previouslyDone = board.isDone(id);
            board.setDone(id, true);
        }
        @Override public void undo() { board.setDone(id, previouslyDone); }
    }

    @FunctionalInterface
    public interface Observer {
        void onChanged(String event);
    }

    public static final class Events {
        private final Map<String, Observer> observers = new LinkedHashMap<>();
        private final List<String> deliveries = new ArrayList<>();
        public void subscribe(String name, Observer observer) {
            if (name == null || name.isBlank() || observer == null || observers.putIfAbsent(name, observer) != null) {
                throw new IllegalArgumentException("unique observer name and callback required");
            }
        }
        public void unsubscribe(String name) {
            if (observers.remove(name) == null) throw new IllegalStateException("observer not registered");
        }
        public List<String> deliveries() { return List.copyOf(deliveries); }

        private void publish(String event) {
            for (var listener : List.copyOf(observers.entrySet())) {
                try {
                    listener.getValue().onChanged(event);
                    deliveries.add(listener.getKey() + ":" + event);
                } catch (RuntimeException ex) {
                    // In this demo, one failed optional listener does not prevent others.
                    deliveries.add(listener.getKey() + ":FAILED:" + event);
                }
            }
        }
    }

    private final Deque<Command> undo = new ArrayDeque<>();
    private final Deque<Command> redo = new ArrayDeque<>();
    private final Events events;

    public TaskCommandManager(Events events) { this.events = Objects.requireNonNull(events); }

    public void execute(Command command) {
        Objects.requireNonNull(command);
        if (undo.size() >= 20) throw new IllegalStateException("history limit of 20 reached");
        command.execute();
        undo.push(command);
        redo.clear();
        events.publish("EXECUTE_" + command.kind() + ":" + command.id());
    }

    public void undo() {
        if (undo.isEmpty()) throw new IllegalStateException("undo stack is empty");
        Command command = undo.peek();
        command.undo();
        undo.pop();
        redo.push(command);
        events.publish("UNDO_" + command.kind() + ":" + command.id());
    }

    public void redo() {
        if (redo.isEmpty()) throw new IllegalStateException("redo stack is empty");
        Command command = redo.peek();
        command.execute();
        redo.pop();
        undo.push(command);
        events.publish("REDO_" + command.kind() + ":" + command.id());
    }

    public int undoDepth() { return undo.size(); }
    public int redoDepth() { return redo.size(); }
}
