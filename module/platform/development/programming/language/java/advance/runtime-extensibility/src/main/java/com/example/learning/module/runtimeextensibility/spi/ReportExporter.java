package com.example.learning.module.runtimeextensibility.spi;

public interface ReportExporter {

    String format();

    String export(String report);
}
