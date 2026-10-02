package com.example.learning.module.runtimeextensibility.provider;

import com.example.learning.module.runtimeextensibility.experiment.ProviderConstructionProbe;
import com.example.learning.module.runtimeextensibility.spi.ReportExporter;

public final class CsvReportExporter implements ReportExporter {

    public CsvReportExporter() {
        ProviderConstructionProbe.providerConstructed();
    }

    @Override
    public String format() {
        return "csv";
    }

    @Override
    public String export(String report) {
        return "CSV:" + report;
    }
}
