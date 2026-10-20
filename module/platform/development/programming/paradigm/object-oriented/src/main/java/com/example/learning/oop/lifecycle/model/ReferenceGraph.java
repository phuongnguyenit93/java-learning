package com.example.learning.oop.lifecycle.model;

import com.example.learning.oop.lifecycle.dto.LifecycleDtos.NodeInput;
import com.example.learning.oop.lifecycle.dto.LifecycleDtos.ReferenceSnapshot;
import com.example.learning.oop.lifecycle.dto.LifecycleDtos.RootAction;
import com.example.learning.oop.lifecycle.dto.LifecycleDtos.RootStep;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Traverses a real object graph from explicitly selected logical roots.
 * This is NOT a JVM GC observation: the request-local node index also holds references.
 */
public final class ReferenceGraph {
    private static final class Node {
        private final String id;
        private final List<Node> links = new ArrayList<>();

        private Node(String id) {
            this.id = id;
        }
    }

    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Set<String> roots = new LinkedHashSet<>();

    public ReferenceGraph(List<NodeInput> inputs, List<String> initialRoots) {
        if (inputs == null || inputs.isEmpty() || inputs.size() > 15) {
            throw new IllegalArgumentException("nodes must contain 1-15 entries");
        }
        if (initialRoots == null || initialRoots.size() > 15) {
            throw new IllegalArgumentException("initialRoots must contain at most 15 entries");
        }
        for (NodeInput input : inputs) {
            if (input == null) {
                throw new IllegalArgumentException("nodes cannot contain null");
            }
            String id = input.id();
            if (id == null || !id.matches("[A-Za-z0-9_-]{1,40}")) {
                throw new IllegalArgumentException("node ID must be 1-40 letters, digits, '_' or '-'");
            }
            if (nodes.putIfAbsent(id, new Node(id)) != null) {
                throw new IllegalArgumentException("Duplicate node ID: " + id);
            }
        }
        for (NodeInput input : inputs) {
            if (input.links() == null || input.links().size() > 15) {
                throw new IllegalArgumentException("Each node requires 0-15 links");
            }
            Node owner = nodes.get(input.id());
            for (String linkId : input.links()) {
                owner.links.add(requireNode(linkId));
            }
        }
        for (String root : initialRoots) {
            requireNode(root);
            if (!roots.add(root)) {
                throw new IllegalArgumentException("Duplicate initial root: " + root);
            }
        }
    }

    public int nodeCount() {
        return nodes.size();
    }

    public void apply(RootStep step) {
        if (step == null || step.action() == null) {
            throw new IllegalArgumentException("Every step requires a root action");
        }
        requireNode(step.nodeId());
        if (step.action() == RootAction.RETAIN) {
            if (!roots.add(step.nodeId())) {
                throw new IllegalArgumentException("Root already retained: " + step.nodeId());
            }
        } else if (!roots.remove(step.nodeId())) {
            throw new IllegalArgumentException("Root was not retained: " + step.nodeId());
        }
    }

    public ReferenceSnapshot snapshot(int step) {
        Set<String> reachable = new HashSet<>();
        Deque<Node> stack = new ArrayDeque<>();
        for (String root : roots) {
            stack.push(nodes.get(root));
        }
        while (!stack.isEmpty()) {
            Node current = stack.pop();
            if (reachable.add(current.id)) {
                current.links.forEach(stack::push);
            }
        }
        List<String> retainedIds = new ArrayList<>();
        List<String> otherIds = new ArrayList<>();
        for (String id : nodes.keySet()) {
            (reachable.contains(id) ? retainedIds : otherIds).add(id);
        }
        return new ReferenceSnapshot(step, List.copyOf(roots),
                List.copyOf(retainedIds), List.copyOf(otherIds));
    }

    private Node requireNode(String id) {
        Node node = nodes.get(id);
        if (node == null) {
            throw new IllegalArgumentException("Unknown node ID: " + id);
        }
        return node;
    }
}
