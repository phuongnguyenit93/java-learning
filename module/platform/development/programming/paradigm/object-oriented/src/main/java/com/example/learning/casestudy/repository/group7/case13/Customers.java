package com.example.learning.casestudy.repository.group7.case13;

import com.example.learning.casestudy.domain.group7.case13.Customer13;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

public final class Customers {
    private final Map<String, Customer13> records = new HashMap<>();
    public void save(Customer13 c){
        records.put(c.id(), c);

    }
    public Customer13 find(String id){
        Customer13 c = records.get(id);
        if (c == null)throw new NoSuchElementException(id);
        return c;

    }
}
