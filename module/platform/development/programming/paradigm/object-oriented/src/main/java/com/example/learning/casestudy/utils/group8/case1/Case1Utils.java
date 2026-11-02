package com.example.learning.casestudy.utils.group8.case1;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class Case1Utils {
    private Case1Utils() {
    }

    public static boolean cyclic(Map<String, Set<String>> edges) {
        Set<String> visiting = new HashSet<>(), visited = new HashSet<>();
        for (String module : edges.keySet())
            if (walk(module, edges, visiting, visited)) return true;
        return false;
    }

    public static boolean walk(String node, Map<String, Set<String>> edges,
    Set<String> visiting, Set<String> visited) {
        if (visiting.contains(node)) return true;
        if (visited.contains(node)) return false;
        visiting.add(node);
        for (String child : edges.getOrDefault(node, Set.of()))
            if (walk(child, edges, visiting, visited)) return true;
        visiting.remove(node);
        visited.add(node);
        return false;
    }
}
