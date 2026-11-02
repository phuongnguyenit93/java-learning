package com.example.learning.casestudy.service;

import com.example.learning.casestudy.application.group12.case2.TransferService;
import com.example.learning.casestudy.application.group12.case4.OldOrderService;
import com.example.learning.casestudy.application.group12.case4.RefactoredOrderService;
import com.example.learning.casestudy.application.group12.case5.NewEditor;
import com.example.learning.casestudy.application.group12.case5.OldEditor;
import com.example.learning.casestudy.application.group12.case6.Legacy;
import com.example.learning.casestudy.application.group12.case6.Router;
import com.example.learning.casestudy.application.group12.case7.RefactoredService;
import com.example.learning.casestudy.domain.group12.case1.After;
import com.example.learning.casestudy.domain.group12.case1.Before;
import com.example.learning.casestudy.domain.group12.case2.Account;
import com.example.learning.casestudy.domain.group12.case3.BranchCalculator;
import com.example.learning.casestudy.domain.group12.case3.RuleCalculator;
import com.example.learning.casestudy.domain.group12.case4.Order4;
import com.example.learning.casestudy.domain.group12.case4.Outcome;
import com.example.learning.casestudy.domain.group12.case5.User;
import com.example.learning.casestudy.domain.group12.case6.Request;
import com.example.learning.casestudy.domain.group12.case7.Order7;
import com.example.learning.casestudy.domain.group12.case8.PricingCalculator;
import com.example.learning.casestudy.port.group12.case7.Listener;
import com.example.learning.casestudy.port.group12.case7.Store7;
import com.example.learning.casestudy.port.group12.case8.DiscountPolicy;
import com.example.learning.casestudy.repository.group12.case2.SpyRepository;
import com.example.learning.casestudy.repository.group12.case4.MemoryStore;
import com.example.learning.casestudy.repository.group12.case5.MemoryRepository;
import com.example.learning.casestudy.utils.group12.case1.Case1Utils;
import com.example.learning.casestudy.utils.group12.case3.Case3Utils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service("caseStudyGroup12Service")
public class Group12Service {
    public void executeCase1() {
        Case1Utils.contract(800, 1000, 200);
        Case1Utils.contract(0, 100, 100);
        for (boolean after : new boolean[] {
            false, true
        }) {
            try {
                if (after) new After().payable(100, 200);
                else new Before().payable(100, 200);
                throw new AssertionError("Expected validation error");
            } catch (IllegalArgumentException expected) {
            }
        }

    }

    public void executeCase2() {
        SpyRepository spy = new SpyRepository();
        Account account = new Account(1000);
        TransferService service = new TransferService(spy);
        service.withdraw(account, 250);
        if (spy.saves != 1) throw new AssertionError("Interaction contract");
        if (account.balance() != 750 || spy.persistedBalance != 750)
        throw new AssertionError("State invariant");
        try {
            service.withdraw(account, 800);
            throw new AssertionError("Invalid withdrawal accepted");
        } catch (IllegalArgumentException expected) {
        }
        if (spy.saves != 1 || account.balance() != 750)
        throw new AssertionError("Failure must not persist or mutate");

    }

    public void executeCase3() {
        Case3Utils.contract(new BranchCalculator());
        Case3Utils.contract(new RuleCalculator());

    }

    public void executeCase4() {
        for (Order4 order : List.of(new Order4(1000, 200), new Order4(0, 0))) {
            MemoryStore oldStore = new MemoryStore(), newStore = new MemoryStore();
            Outcome before = new OldOrderService(oldStore).checkout(order);
            Outcome after = new RefactoredOrderService(newStore).checkout(order);
            if (!before.equals(after) || !oldStore.saved.equals(newStore.saved))
            throw new AssertionError("Behavior changed");
        }
        MemoryStore store = new MemoryStore();
        try {
            new RefactoredOrderService(store).checkout(new Order4(100, 200));
            throw new AssertionError("Bad order accepted");
        } catch (IllegalArgumentException expected) {
        }
        if (!store.saved.isEmpty()) throw new AssertionError("Wrote invalid order");

    }

    public void executeCase5() {
        MemoryRepository legacy = new MemoryRepository(), modern = new MemoryRepository();
        OldEditor before = new OldEditor(legacy);
        NewEditor after = new NewEditor(modern::update);
        for (int i = 0; i < 2; i++) {
            try {
                if (i == 0) before.update(new User(false), "secret");
                else after.update(new User(false), "secret");
                throw new AssertionError("Unauthorized edit allowed");
            } catch (SecurityException expected) {
            }
        }
        if (!legacy.writes.isEmpty() || !modern.writes.isEmpty())
        throw new AssertionError("Denied call performed write");
        before.update(new User(true), "allowed");
        after.update(new User(true), "allowed");
        if (!legacy.writes.equals(modern.writes)
        || !modern.writes.equals(List.of("allowed")))
        throw new AssertionError("Permission contract changed");

    }

    public void executeCase6() {
        Legacy legacy = new Legacy();
        Router candidate = new Router();
        Map<Request, String> golden = new LinkedHashMap<>();
        golden.put(new Request("BUY", 1000, false), "PAID:1000");
        golden.put(new Request("BUY", 1000, true), "PAID:900");
        golden.put(new Request("BUY", 0, true), "PAID:0");
        golden.put(new Request("REFUND", 500, false), "REFUNDED:500");
        golden.put(new Request("BUY", -1, false), "ERR:NEGATIVE");
        golden.put(new Request("OTHER", 10, false), "ERR:UNKNOWN");
        for (var entry : golden.entrySet()) {
            String expected = entry.getValue();
            if (!expected.equals(legacy.process(entry.getKey())))
            throw new AssertionError("Golden baseline drifted");
            if (!expected.equals(candidate.process(entry.getKey())))
            throw new AssertionError("Incremental refactor regressed: " + entry.getKey());
        }

    }

    public void executeCase7() {
        List<String> success = new ArrayList<>();
        Store7 goodStore = order -> {
            success.add("commit");
            order.committed = true;
        };
        Listener cb = order -> {
            success.add("callback");
            if (!order.committed) throw new AssertionError("Callback before commit");
        };
        new RefactoredService(goodStore, cb, success).execute(new Order7(), "ok");
        if (!success.equals(List.of("validate", "commit", "callback")))
        throw new AssertionError("Wrong event order");
        List<String> failure = new ArrayList<>();
        Store7 broken = order -> {
            failure.add("commit");
            throw new IllegalStateException("Store unavailable");
        };
        new RefactoredService(broken, order -> failure.add("callback"), failure);
        try {
            new RefactoredService(broken, order -> failure.add("callback"), failure)
            .execute(new Order7(), "ok");
            throw new AssertionError("Expected persistence error");
        } catch (IllegalStateException expected) {
        }
        if (!failure.equals(List.of("validate", "commit")))
        throw new AssertionError("Callback fired on failure");

    }

    public void executeCase8() {
        DiscountPolicy fake = subtotal -> subtotal / 10;
        PricingCalculator calculator = new PricingCalculator(fake);
        if (calculator.total(1000) != 900) throw new AssertionError();
        if (calculator.total(0) != 0) throw new AssertionError();
        try {
            calculator.total(-1);
            throw new AssertionError("Must reject negatives");
        } catch (IllegalArgumentException expected) {
        }
        PricingCalculator badPolicy = new PricingCalculator(subtotal -> subtotal + 1);
        try {
            badPolicy.total(100);
            throw new AssertionError("Must reject invalid collaborator");
        } catch (IllegalStateException expected) {
        }

    }
}
