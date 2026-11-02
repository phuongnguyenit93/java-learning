package com.example.learning.casestudy.service;

import com.example.learning.casestudy.adapter.group8.case7.SqlAdapter;
import com.example.learning.casestudy.application.group8.case10.ProfileService;
import com.example.learning.casestudy.application.group8.case11.Billing;
import com.example.learning.casestudy.application.group8.case11.Coordinator;
import com.example.learning.casestudy.application.group8.case12.Reader;
import com.example.learning.casestudy.application.group8.case13.CheckoutCalculator;
import com.example.learning.casestudy.application.group8.case2.ApprovalPolicy;
import com.example.learning.casestudy.application.group8.case3.Checkout3;
import com.example.learning.casestudy.application.group8.case4.Reporter;
import com.example.learning.casestudy.application.group8.case5.NotifyOrder;
import com.example.learning.casestudy.application.group8.case5.QuoteItem;
import com.example.learning.casestudy.application.group8.case6.Editor;
import com.example.learning.casestudy.application.group8.case6.Viewer;
import com.example.learning.casestudy.application.group8.case7.Approval;
import com.example.learning.casestudy.application.group8.case8.InvoiceService;
import com.example.learning.casestudy.application.group8.case9.Checkout9;
import com.example.learning.casestudy.domain.group8.case11.Inventory11;
import com.example.learning.casestudy.domain.group8.case13.FixedOff;
import com.example.learning.casestudy.domain.group8.case13.NoDiscount;
import com.example.learning.casestudy.domain.group8.case13.PercentOff;
import com.example.learning.casestudy.domain.group8.case2.Order2;
import com.example.learning.casestudy.domain.group8.case3.Event;
import com.example.learning.casestudy.domain.group8.case7.Order7;
import com.example.learning.casestudy.domain.group8.case8.InvoiceCalculator;
import com.example.learning.casestudy.model.group8.case10.RequestContext;
import com.example.learning.casestudy.port.group8.case2.OrderRepository;
import com.example.learning.casestudy.port.group8.case3.Ledger;
import com.example.learning.casestudy.port.group8.case3.Mailer;
import com.example.learning.casestudy.port.group8.case3.Outbox;
import com.example.learning.casestudy.port.group8.case3.Pricing;
import com.example.learning.casestudy.port.group8.case3.Stock;
import com.example.learning.casestudy.port.group8.case3.UnitOfWork;
import com.example.learning.casestudy.port.group8.case7.OrderReader;
import com.example.learning.casestudy.port.group8.case9.Catalog;
import com.example.learning.casestudy.port.group8.case9.Orders;
import com.example.learning.casestudy.repository.group8.case12.PrimaryStore;
import com.example.learning.casestudy.repository.group8.case12.ReadOnlyMirror;
import com.example.learning.casestudy.repository.group8.case4.Storage;
import com.example.learning.casestudy.repository.group8.case6.LegacyReadReplica;
import com.example.learning.casestudy.repository.group8.case6.ReadWriteStore;
import com.example.learning.casestudy.utils.group8.case1.Case1Utils;
import com.example.learning.casestudy.utils.group8.case10.Case10Utils;
import com.example.learning.casestudy.utils.group8.case13.Case13Utils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.stereotype.Service;

@Service("caseStudyGroup8Service")
public class Group8Service {
    public void executeCase1() {
        Map<String, Set<String>> before = Map.of(
        "orders", Set.of("sql"),
        "sql", Set.of("orders"));
        Map<String, Set<String>> after = Map.of(
        "orders", Set.of("contracts"),
        "sql", Set.of("contracts"),
        "contracts", Set.of());
        assert Case1Utils.cyclic(before);
        assert !Case1Utils.cyclic(after);
    }

    public void executeCase2() {
        OrderRepository fake = id -> switch (id) {
            case "small" -> Optional.of(new Order2(id, 9_000));
            case "large" -> Optional.of(new Order2(id, 250_000));
            default -> Optional.empty();
        };
        ApprovalPolicy policy = new ApprovalPolicy(fake);
        assert !policy.requiresReview("small");
        assert policy.requiresReview("large");
    }

    public void executeCase3() {
        List<String> calls = new ArrayList<>();
        List<Event> pending = new ArrayList<>();
        Pricing price = base -> base + 100;
        Stock stock = id -> calls.add("stock:" + id);
        Ledger ledger = order -> calls.add("ledger:" + order.cents());
        Outbox outbox = pending::add;
        UnitOfWork fakeTx = Runnable::run; // chỉ kiểm luồng, không mô phỏng rollback
        Checkout3 checkout = new Checkout3(price, stock, ledger, outbox, fakeTx);
        checkout.place("O1", 900);
        Mailer failingMail = event -> {
            throw new IllegalStateException("SMTP down");
        };
        try {
            failingMail.send(pending.get(0));
            throw new AssertionError("expected mail failure");
        } catch (IllegalStateException expected) {
        }
        assert calls.equals(List.of("stock:O1", "ledger:1000"));
        assert pending.equals(List.of(new Event("ORDER_PLACED", "O1")));
    }

    public void executeCase4() {
        Storage storage = new Storage();
        Reporter reporter = new Reporter(storage);
        assert reporter.show("A").equals("visible");
        assert storage.find("A").isPresent();
    }

    public void executeCase5() {
        List<String> sent = new ArrayList<>();
        QuoteItem pricing = new QuoteItem(sku -> 8000, sku -> sku.equals("A"));
        NotifyOrder notifications = new NotifyOrder(
        (user, content) -> sent.add(user + ":" + content));
        assert pricing.quote("A") == 8000;
        try {
            pricing.quote("B");
            throw new AssertionError("expected stock error");
        } catch (IllegalStateException expected) {
        }
        notifications.completed("customer-1");
        assert sent.equals(List.of("customer-1:order completed"));
    }

    public void executeCase6() {
        assert new Viewer(new LegacyReadReplica()).view("A").orElseThrow().equals("old");
        ReadWriteStore db = new ReadWriteStore();
        new Editor(db).edit("B", "new");
        assert new Viewer(db).view("B").orElseThrow().equals("new");
    }

    public void executeCase7() {
        OrderReader adapter = new SqlAdapter();
        Approval approval = new Approval(adapter);
        assert approval.large("O1");
        assert !new Approval(id -> Optional.of(new Order7(id, 500))).large("O2");
        // Approval không new SqlAdapter() và không import class infra.
    }

    public void executeCase8() {
        var service = new InvoiceService(new InvoiceCalculator());
        assert service.issue(0) == 0;
        assert service.issue(10_000) == 11_000;
        try {
            service.issue(-1);
            throw new AssertionError("expected validation");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void executeCase9() {
        Map<String,Integer> saved = new HashMap<>();
        Catalog fakeCatalog = sku -> 1500;
        Orders fakeOrders = saved::put;
        Checkout9 checkout = new Checkout9(fakeCatalog, fakeOrders);
        checkout.place("O1", "SKU", 3);
        assert saved.get("O1") == 4500;
        try {
            new Checkout9(null, fakeOrders);
            throw new AssertionError("missing dependency must fail early");
        } catch (NullPointerException expected) {
        }
    }

    public void executeCase10() {
        try {
            ProfileService singleton = new ProfileService();
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                CountDownLatch start = new CountDownLatch(1);
                Future<String> a = pool.submit(() -> {
                    start.await();
                    return singleton.profileFor(new RequestContext("alice"));
                });
                Future<String> b = pool.submit(() -> {
                    start.await();
                    return singleton.profileFor(new RequestContext("bob"));
                });
                start.countDown();
                assert a.get().equals("profile:alice");
                assert b.get().equals("profile:bob");
            } finally {
                pool.shutdownNow();
            }
        } catch (Exception failure) {
            Case10Utils.<RuntimeException>rethrow(failure);
        }
    }

    public void executeCase11() {
        Billing billing = new Billing(sku -> 2000);
        Inventory11 inventory = new Inventory11(Set.of("A"));
        Coordinator coordinator = new Coordinator(billing, inventory);
        assert coordinator.purchase("A", 3) == 6000;
        try {
            coordinator.purchase("B", 3);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {
        }
    }

    public void executeCase12() {
        assert new Reader(new ReadOnlyMirror()).lookup("A").orElseThrow().equals("mirror");
        PrimaryStore primary = new PrimaryStore();
        primary.save("B", "primary");
        primary.rebuildIndex();
        assert new Reader(primary).lookup("B").orElseThrow().equals("primary");
        assert primary.rebuildCount() == 1;
        // new ReadOnlyMirror().save("X", "bad"); // compile error: no Writable capability
    }

    public void executeCase13() {
        Case13Utils.contract(new NoDiscount());
        Case13Utils.contract(new PercentOff(20));
        Case13Utils.contract(new FixedOff(1_500));
        assert new CheckoutCalculator(new NoDiscount()).total(10_000) == 10_000;
        assert new CheckoutCalculator(new PercentOff(20)).total(10_000) == 8_000;
        assert new CheckoutCalculator(new FixedOff(1_500)).total(10_000) == 8_500;
    }
}
