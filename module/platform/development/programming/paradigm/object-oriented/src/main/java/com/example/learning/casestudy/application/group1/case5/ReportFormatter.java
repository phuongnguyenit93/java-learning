package com.example.learning.casestudy.application.group1.case5;

import com.example.learning.casestudy.model.group1.case5.Sale;
import java.util.List;

public final class ReportFormatter {
    public String format(List<Sale> sales) {
        StringBuilder out = new StringBuilder("id,cents\n");
        for (Sale s : sales) out.append(s.id()).append(',')
                                .append(s.cents()).append('\n');
        return out.toString();
    }
}
