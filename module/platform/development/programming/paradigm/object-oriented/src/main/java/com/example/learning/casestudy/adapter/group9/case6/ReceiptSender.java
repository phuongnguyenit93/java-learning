package com.example.learning.casestudy.adapter.group9.case6;

import com.example.learning.casestudy.domain.group9.case6.Receipt;
import java.util.HashSet;
import java.util.Set;

public final class ReceiptSender {
    public boolean failNext = true;
    public final Set<String> delivered = new HashSet<>();
    public void send(Receipt receipt) {
        if (delivered.contains(receipt.requestId())) return;
        if (failNext) {
            failNext = false;
            throw new IllegalStateException("Email unavailable");
        }
        delivered.add(receipt.requestId());
    }
}
