package com.example.learning.casestudy.domain.group5.case3;

import java.util.List;

public final class Invoice extends BaseDocument {
    private final List<String> rows;
    public Invoice(String id, List<String> rows) {
        super(id);
        this.rows = List.copyOf(rows);
    }
    public void validate() {
        if (id().isBlank() || rows.isEmpty())
            throw new IllegalArgumentException("invoice incomplete");
    }
    public int rowCount() {
        return rows.size();
    }
}
