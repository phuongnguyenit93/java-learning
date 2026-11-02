package com.example.learning.casestudy.application.group8.case12;

import com.example.learning.casestudy.port.group8.case12.Searchable;
import java.util.Optional;

public record Reader(Searchable search) {
    public Optional<String> lookup(String key) { return search.find(key); }
}
