package com.example.learning.casestudy.application.group8.case6;

import com.example.learning.casestudy.port.group8.case6.ReadStore;
import java.util.Optional;

public record Viewer(ReadStore store) {
    public Optional<String> view(String id) { return store.get(id); }
}
