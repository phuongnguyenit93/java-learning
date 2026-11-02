package com.example.learning.casestudy.domain.group7.case12;

import java.util.HashSet;
import java.util.Set;

public final class Memberships {
    private final Set<Membership> edges = new HashSet<>();
    public void add(String group, String item){
        if (group == null || group.isBlank() || item == null || item.isBlank())
        throw new IllegalArgumentException("key");
        edges.add(new Membership(group, item));

    }
    public void remove(String group, String item){
        edges.remove(new Membership(group, item));

    }
    public Set<String> itemsOf(String group){
        Set<String> r = new HashSet<>();
        for (Membership m:edges)if (m.groupId().equals(group))r.add(m.itemId());
        return Set.copyOf(r);

    }
    public Set<String> groupsOf(String item){
        Set<String> r = new HashSet<>();
        for (Membership m:edges)if (m.itemId().equals(item))r.add(m.groupId());
        return Set.copyOf(r);

    }
}
