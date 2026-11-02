package com.example.learning.casestudy.repository.group8.case12;

import com.example.learning.casestudy.port.group8.case12.Searchable;
import java.util.Optional;

public final class ReadOnlyMirror implements Searchable {
    public Optional<String> find(String key) {
        return key.equals("A") ? Optional.of("mirror") : Optional.empty();
    }
}
