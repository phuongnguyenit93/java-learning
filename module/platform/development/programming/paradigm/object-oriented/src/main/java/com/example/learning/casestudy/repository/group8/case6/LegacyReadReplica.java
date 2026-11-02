package com.example.learning.casestudy.repository.group8.case6;

import com.example.learning.casestudy.port.group8.case6.ReadStore;
import java.util.Optional;

public final class LegacyReadReplica implements ReadStore {
    public Optional<String> get(String id) {
        return id.equals("A") ? Optional.of("old") : Optional.empty();
    }
}
