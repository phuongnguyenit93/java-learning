package com.example.learning.casestudy.repository.group12.case5;

import com.example.learning.casestudy.port.group12.case5.Repository;
import java.util.ArrayList;
import java.util.List;

public class MemoryRepository implements Repository {
    public final List<String> writes = new ArrayList<>();
    public void update(String value) {
        writes.add(value);
    }
}
