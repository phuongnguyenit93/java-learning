package com.example.learning.casestudy.application.group8.case6;

import com.example.learning.casestudy.port.group8.case6.WriteStore;

public record Editor(WriteStore store) {
    public void edit(String id, String value) { store.put(id, value); }
}
