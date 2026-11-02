package com.example.learning.casestudy.application.group9.case9;

import com.example.learning.casestudy.repository.group9.case9.Store;

public abstract class ImportTemplate {
    private final Store store;
    public ImportTemplate(Store store) { this.store = store; }
    public final void execute(String raw) {
        if (raw == null || raw.isBlank())
            throw new IllegalArgumentException("Missing input");
        String result = transform(raw);
        if (result == null || result.isBlank())
            throw new IllegalArgumentException("Invalid output");
        store.save(result);
    }
    protected abstract String transform(String raw);
}
