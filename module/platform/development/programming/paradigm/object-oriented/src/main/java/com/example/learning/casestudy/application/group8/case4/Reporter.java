package com.example.learning.casestudy.application.group8.case4;

import com.example.learning.casestudy.port.group8.case4.ReadOnlyRepository;

public record Reporter(ReadOnlyRepository data) {
    public String show(String id) { return data.find(id).orElseThrow().value(); }
    // data.delete(id); // compile error: ReadOnlyRepository không có delete
}
