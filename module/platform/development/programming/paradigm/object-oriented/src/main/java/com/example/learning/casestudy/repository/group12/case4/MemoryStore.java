package com.example.learning.casestudy.repository.group12.case4;

import com.example.learning.casestudy.domain.group12.case4.Outcome;
import com.example.learning.casestudy.port.group12.case4.Store4;
import java.util.ArrayList;
import java.util.List;

public class MemoryStore implements Store4 {
    public final List<Outcome> saved = new ArrayList<>();
    public void save(Outcome out) {
        saved.add(out);
    }
}
