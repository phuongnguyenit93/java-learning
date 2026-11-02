package com.example.learning.casestudy.domain.group11.case6;

import java.util.ArrayList;
import java.util.List;

public final class Node {
    final String id;
    Node parent;
    final List<Node> children = new ArrayList<>();
    public Node(String id) {
        this.id = id;
    }
    public void attach(Node child) {
        children.add(child);
        child.parent = this;
    }
    @Override
    public String toString() {
        return "Node{id='" + id + "', parentId=" +
        (parent == null ? "null" : parent.id) +
        ", childCount=" + children.size() + "}";
    }
}
