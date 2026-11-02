package com.example.learning.casestudy.application.group8.case3;

import com.example.learning.casestudy.domain.group8.case3.Event;
import com.example.learning.casestudy.domain.group8.case3.Order3;
import com.example.learning.casestudy.port.group8.case3.Ledger;
import com.example.learning.casestudy.port.group8.case3.Outbox;
import com.example.learning.casestudy.port.group8.case3.Pricing;
import com.example.learning.casestudy.port.group8.case3.Stock;
import com.example.learning.casestudy.port.group8.case3.UnitOfWork;

public record Checkout3(Pricing pricing, Stock stock, Ledger ledger,
Outbox outbox, UnitOfWork uow) {
    public void place(String id, long base) {
        long total = pricing.total(base);
        uow.inTransaction(() -> {
            stock.reserve(id);
            ledger.post(new Order3(id, total));
            outbox.enqueue(new Event("ORDER_PLACED", id));
        });
    }
}
