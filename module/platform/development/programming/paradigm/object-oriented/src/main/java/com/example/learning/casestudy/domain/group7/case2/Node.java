package com.example.learning.casestudy.domain.group7.case2;

import java.util.ArrayList;
import java.util.List;

public final class Node {
    public final String id;
    public Node parent;
    public final List<Node> children =  new ArrayList<>();
    public Node(String id) {
        this.id = id;

    }
    public void moveTo(Node newParent) {
        if (newParent == this) throw new IllegalArgumentException("self");
        for (Node x = newParent; x != null; x = x.parent)
        if (x == this) throw new IllegalArgumentException("cycle");
        if (parent != null) parent.children.remove(this);
        parent = newParent;
        if (newParent != null) newParent.children.add(this);

    }
}
