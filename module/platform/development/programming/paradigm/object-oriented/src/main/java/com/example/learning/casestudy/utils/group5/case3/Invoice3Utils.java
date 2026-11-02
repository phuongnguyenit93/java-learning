package com.example.learning.casestudy.utils.group5.case3;

import com.example.learning.casestudy.domain.group5.case3.Invoice;
import java.util.List;

public final class Invoice3Utils {
    public static Invoice create(String id, List<String> rows) {
        Invoice result = new Invoice(id, rows);
        result.validate();
        // sau constructor: rows đã được gán
        return result;
    }
}
