package com.example.learning.casestudy.utils.group1.case12;

import com.example.learning.casestudy.model.group1.case12.Row;
import java.util.List;

public final class Case12Utils {
    private Case12Utils() {}

    public static String quoteCsv(String field) {
        if (field == null) throw new IllegalArgumentException("customer");
        return "\"" + field.replace("\"", "\"\"") + "\"";
    }

    public static String export(List<Row> rows) {
        StringBuilder out = new StringBuilder("customer,amount\r\n");
        for (Row row : rows) {
            out.append(quoteCsv(row.customer())).append(',')
               .append(row.amount()).append("\r\n");
        }
        return out.toString();
    }

    public static void check(boolean ok) { if (!ok) throw new AssertionError(); }
}
