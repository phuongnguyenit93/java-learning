package com.example.learning.casestudy.repository.group4.case3;

import com.example.learning.casestudy.port.group4.case3.InvoiceAdmin;
import com.example.learning.casestudy.port.group4.case3.InvoiceReader;

public final class Store implements InvoiceReader, InvoiceAdmin {
    private boolean exists = true;
    public String read(String id) {
        if (!exists) throw new IllegalStateException("deleted");
        return "Invoice:" + id;
    }
    public void delete(String id) { exists = false; }
}
