package com.example.learning.module.runtimeextensibility.provider;

import com.example.learning.module.runtimeextensibility.experiment.ProviderConstructionProbe;
import com.example.learning.module.runtimeextensibility.spi.ReportExporter;

public final class PdfReportExporter implements ReportExporter {

    public PdfReportExporter() {
        ProviderConstructionProbe.providerConstructed();
    }

    @Override
    public String format() {
        return "pdf";
    }

    @Override
    public String export(String report) {
        return "PDF:" + report;
    }
}
