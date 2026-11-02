package com.example.learning.casestudy.service;

import org.springframework.stereotype.Service;
import com.example.learning.casestudy.adapter.group9.case1.BatchController;
import com.example.learning.casestudy.adapter.group9.case1.WebController;
import com.example.learning.casestudy.adapter.group9.case5.VendorAdapter;
import com.example.learning.casestudy.adapter.group9.case5.VendorSdk;
import com.example.learning.casestudy.adapter.group9.case6.PaymentGateway;
import com.example.learning.casestudy.adapter.group9.case6.ReceiptSender;
import com.example.learning.casestudy.application.group9.case1.PricingService;
import com.example.learning.casestudy.application.group9.case10.ConfigBuilder;
import com.example.learning.casestudy.application.group9.case12.AuditDecorator12;
import com.example.learning.casestudy.application.group9.case12.TrimDecorator;
import com.example.learning.casestudy.application.group9.case3.Application;
import com.example.learning.casestudy.application.group9.case3.ProductionFactory;
import com.example.learning.casestudy.application.group9.case3.StagingFactory;
import com.example.learning.casestudy.application.group9.case4.AuditDecorator4;
import com.example.learning.casestudy.application.group9.case6.CheckoutFacade;
import com.example.learning.casestudy.application.group9.case7.Bus7;
import com.example.learning.casestudy.application.group9.case8.Bus8;
import com.example.learning.casestudy.application.group9.case8.Subscription;
import com.example.learning.casestudy.application.group9.case9.ImportTemplate;
import com.example.learning.casestudy.application.group9.case9.TrimImporter;
import com.example.learning.casestudy.domain.group9.case1.Plan;
import com.example.learning.casestudy.domain.group9.case10.ClientConfig;
import com.example.learning.casestudy.domain.group9.case11.Phase;
import com.example.learning.casestudy.domain.group9.case11.Session;
import com.example.learning.casestudy.domain.group9.case12.Request;
import com.example.learning.casestudy.domain.group9.case13.Shipping;
import com.example.learning.casestudy.domain.group9.case2.Config;
import com.example.learning.casestudy.domain.group9.case2.Environment;
import com.example.learning.casestudy.domain.group9.case3.Env;
import com.example.learning.casestudy.domain.group9.case6.Result;
import com.example.learning.casestudy.domain.group9.case6.Status;
import com.example.learning.casestudy.model.group9.case3.DatabaseClient;
import com.example.learning.casestudy.model.group9.case3.MessagingClient;
import com.example.learning.casestudy.port.group9.case12.Handler;
import com.example.learning.casestudy.repository.group9.case9.Store;
import com.example.learning.casestudy.utils.group9.case13.Case13Utils;
import com.example.learning.casestudy.utils.group9.case2.Case2Utils;
import com.example.learning.casestudy.utils.group9.case3.Case3Utils;
import com.example.learning.casestudy.utils.group9.case5.Case5Utils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service("caseStudyGroup9Service")
public class Group9Service {
    public void executeCase1() {
        PricingService service = new PricingService();
        WebController web = new WebController(service);
        BatchController batch = new BatchController(service);
        for (Plan plan : Plan.values())
            if (web.quote(plan) != batch.quote(plan))
                throw new AssertionError("Duplicated selection diverged");
        if (web.quote(Plan.VIP) != 900 || batch.quote(Plan.STAFF) != 800)
            throw new AssertionError();
    }

    public void executeCase2() {
        Config test = new Config(Environment.TEST, "http://test.internal");
        Config prod = new Config(Environment.PRODUCTION, "https://prod.internal");
        Case2Utils.contract(test, "http://test.internal");
        Case2Utils.contract(prod, "https://prod.internal");
        Case2Utils.contract(test, "http://test.internal");
    }

    public void executeCase3() {
        if (Case3Utils.build(new StagingFactory()).message().env() != Env.STAGING)
            throw new AssertionError();
        if (Case3Utils.build(new ProductionFactory()).db().env() != Env.PRODUCTION)
            throw new AssertionError();
        try {
            new Application(new DatabaseClient(Env.STAGING),
                            new MessagingClient(Env.PRODUCTION));
            throw new AssertionError("Mixed clients accepted");
        } catch (IllegalArgumentException expected) {}
    }

    public void executeCase4() {
        List<String> events = new ArrayList<>();
        com.example.learning.casestudy.port.group9.case4.Service success = new AuditDecorator4(() -> "OK", events);
        if (!"OK".equals(success.execute())) throw new AssertionError();
        IllegalArgumentException original = new IllegalArgumentException("invalid");
        com.example.learning.casestudy.port.group9.case4.Service failure = new AuditDecorator4(() -> { throw original; }, events);
        try {
            failure.execute();
            throw new AssertionError("Expected original exception");
        } catch (IllegalArgumentException caught) {
            if (caught != original) throw new AssertionError("Exception changed");
        }
        if (!events.equals(List.of("success", "failed:IllegalArgumentException")))
            throw new AssertionError("Audit mismatch");
    }

    public void executeCase5() {
        if (!"APPROVED:v-1".equals(Case5Utils.checkout(new VendorAdapter(new VendorSdk()))))
            throw new AssertionError();
    }

    public void executeCase6() {
        PaymentGateway p = new PaymentGateway();
        ReceiptSender s = new ReceiptSender();
        CheckoutFacade facade = new CheckoutFacade(p, s);
        Result first = facade.checkout("order-7", 1000);
        if (first.status() != Status.PAID_RECEIPT_PENDING) throw new AssertionError();
        Result retried = facade.checkout("order-7", 1000);
        if (retried.status() != Status.COMPLETED) throw new AssertionError();
        if (p.charges.size() != 1 || s.delivered.size() != 1)
            throw new AssertionError("Double charge or duplicate receipt");
    }

    public void executeCase7() {
        Bus7 bus = new Bus7();
        List<String> received = new ArrayList<>();
        bus.register(e -> received.add("A:" + e));
        bus.register(e -> { throw new IllegalStateException("broken subscriber"); });
        bus.register(e -> received.add("C:" + e));
        List<RuntimeException> failures = bus.publish("event-1");
        if (!received.equals(List.of("A:event-1", "C:event-1")))
            throw new AssertionError("Delivery interrupted");
        if (failures.size() != 1
            || !(failures.get(0) instanceof IllegalStateException))
            throw new AssertionError("Failed subscriber not reported");
    }

    public void executeCase8() {
        Bus8 bus = new Bus8();
        List<String> notifications = new ArrayList<>();
        Consumer<String> listener = notifications::add;
        for (int open = 0; open < 3; open++) {
            try (Subscription tab = bus.subscribe(listener)) {
                if (tab != bus.subscribe(listener)) throw new AssertionError();
                if (bus.count() != 1) throw new AssertionError();
                bus.publish("open-" + open);
            }
            if (bus.count() != 0) throw new AssertionError("Listener leaked");
        }
        if (!notifications.equals(List.of("open-0", "open-1", "open-2")))
            throw new AssertionError("Duplicate notifications");
    }

    public void executeCase9() {
        Store store = new Store();
        ImportTemplate importer = new TrimImporter(store);
        try {
            importer.execute("  ");
            throw new AssertionError("Invalid input accepted");
        } catch (IllegalArgumentException expected) {}
        if (!store.saved.isEmpty()) throw new AssertionError("Write before validation");
        importer.execute("  hello  ");
        if (!store.saved.equals(List.of("hello"))) throw new AssertionError();
    }

    public void executeCase10() {
        ClientConfig valid = new ConfigBuilder()
            .tls(true).plainHttp(false).host("api.example").build();
        if (!valid.useTls() || valid.allowPlainHttp()) throw new AssertionError();
        try {
            new ConfigBuilder().tls(true).plainHttp(true)
                .host("api.example").build();
            throw new AssertionError("Conflicting options accepted");
        } catch (IllegalStateException expected) {}
    }

    public void executeCase11() {
        Session first = new Session(), second = new Session();
        first.activate(); second.activate();
        first.attempt(); first.attempt();
        if (first.attempts() != 2 || second.attempts() != 0)
            throw new AssertionError("State leaked between sessions");
        first.close();
        if (first.phase() != Phase.CLOSED || second.phase() != Phase.ACTIVE)
            throw new AssertionError("Transition affected another session");
    }

    public void executeCase12() {
        List<String> trace = new ArrayList<>();
        IllegalArgumentException original = new IllegalArgumentException("bad payload");
        Handler terminal = request -> { throw original; };
        Handler chain = new AuditDecorator12(new TrimDecorator(terminal, trace), trace);
        try {
            chain.handle(new Request(" bad "));
            throw new AssertionError("Expected failure");
        } catch (IllegalStateException wrapper) {
            if (wrapper.getCause() != original) throw new AssertionError("Lost cause");
        }
        if (!trace.equals(List.of("AuditDecorator12", "TrimDecorator")))
            throw new AssertionError("Unknown wrapper sequence");
    }

    public void executeCase13() {
        if (Case13Utils.fee(Shipping.PICKUP, 500) != 0) throw new AssertionError();
        if (Case13Utils.fee(Shipping.STANDARD, 800) != 50) throw new AssertionError();
        if (Case13Utils.fee(Shipping.STANDARD, 1000) != 0) throw new AssertionError();
        if (Case13Utils.fee(Shipping.EXPRESS, 1000) != 150) throw new AssertionError();
        try {
            Case13Utils.fee(null, 100);
            throw new AssertionError("Missing input check");
        } catch (IllegalArgumentException expected) {}
    }
}
