package com.example.learning.casestudy.adapter.group8.case7;

import com.example.learning.casestudy.domain.group8.case7.Order7;
import com.example.learning.casestudy.port.group8.case7.OrderReader;
import java.util.Optional;

public final class SqlAdapter implements OrderReader {
    public Optional<Order7> find(String id) {
        return id.equals("O1") ? Optional.of(new Order7(id, 120_000)) : Optional.empty();
    }
}
