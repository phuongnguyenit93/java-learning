package com.example.learning.casestudy.domain.group6.case4;

import com.example.learning.casestudy.port.group6.case4.Exportable;

public record PdfRecord(String id) implements Exportable {
    public String export() { return "PDF:" + id; }
}
