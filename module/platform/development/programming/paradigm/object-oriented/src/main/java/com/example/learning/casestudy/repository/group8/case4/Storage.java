package com.example.learning.casestudy.repository.group8.case4;

import com.example.learning.casestudy.model.group8.case4.Item;
import com.example.learning.casestudy.port.group8.case4.AdminRepository;
import com.example.learning.casestudy.port.group8.case4.ReadOnlyRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class Storage implements ReadOnlyRepository, AdminRepository {
    private final Map<String, Item> data = new HashMap<>();
    public Storage() { data.put("A", new Item("A", "visible")); }
    public Optional<Item> find(String id) { return Optional.ofNullable(data.get(id)); }
    public void delete(String id) { data.remove(id); }
}
