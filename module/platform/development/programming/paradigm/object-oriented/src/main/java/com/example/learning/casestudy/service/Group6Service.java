package com.example.learning.casestudy.service;

import com.example.learning.casestudy.adapter.group6.case13.LibraryAdapter;
import com.example.learning.casestudy.adapter.group6.case13.LibrarySession;
import com.example.learning.casestudy.adapter.group6.case13.LibraryUtility;
import com.example.learning.casestudy.adapter.group6.case5.EmailSender;
import com.example.learning.casestudy.adapter.group6.case5.PushSender;
import com.example.learning.casestudy.adapter.group6.case5.SmsSender;
import com.example.learning.casestudy.application.group6.case12.BankFlow;
import com.example.learning.casestudy.application.group6.case12.CardFlow;
import com.example.learning.casestudy.application.group6.case12.Checkout12;
import com.example.learning.casestudy.application.group6.case5.Dispatcher;
import com.example.learning.casestudy.application.group6.case8.Checkout8;
import com.example.learning.casestudy.domain.group6.case1.Designer;
import com.example.learning.casestudy.domain.group6.case1.Developer;
import com.example.learning.casestudy.domain.group6.case1.Intern;
import com.example.learning.casestudy.domain.group6.case10.Combined;
import com.example.learning.casestudy.domain.group6.case11.Player;
import com.example.learning.casestudy.domain.group6.case11.State;
import com.example.learning.casestudy.domain.group6.case12.Order;
import com.example.learning.casestudy.domain.group6.case2.Kind;
import com.example.learning.casestudy.domain.group6.case2.Transaction;
import com.example.learning.casestudy.domain.group6.case3.ConfigurableTax;
import com.example.learning.casestudy.domain.group6.case3.DirectTax;
import com.example.learning.casestudy.domain.group6.case4.CsvRecord;
import com.example.learning.casestudy.domain.group6.case4.PdfRecord;
import com.example.learning.casestudy.domain.group6.case5.Channel;
import com.example.learning.casestudy.domain.group6.case6.CachedRulesQuote;
import com.example.learning.casestudy.domain.group6.case6.LocalQuote;
import com.example.learning.casestudy.domain.group6.case7.Delivery;
import com.example.learning.casestudy.domain.group6.case9.Circle;
import com.example.learning.casestudy.domain.group6.case9.Square;
import com.example.learning.casestudy.port.group6.case1.EmployeeReport;
import com.example.learning.casestudy.port.group6.case4.Exportable;
import com.example.learning.casestudy.port.group6.case5.NotificationSender;
import com.example.learning.casestudy.port.group6.case7.CustomerPolicy;
import com.example.learning.casestudy.port.group6.case7.Promotion;
import com.example.learning.casestudy.port.group6.case7.Shipping;
import com.example.learning.casestudy.port.group6.case8.Pricing;
import com.example.learning.casestudy.utils.group6.case1.Case1Utils;
import com.example.learning.casestudy.utils.group6.case2.Case2Utils;
import com.example.learning.casestudy.utils.group6.case3.Case3Utils;
import com.example.learning.casestudy.utils.group6.case4.Case4Utils;
import com.example.learning.casestudy.utils.group6.case6.Case6Utils;
import com.example.learning.casestudy.utils.group6.case7.Case7Utils;
import com.example.learning.casestudy.utils.group6.case9.Case9Utils;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service("caseStudyGroup6Service")
public class Group6Service {
    public void executeCase1() {
        var people = List.<EmployeeReport>of(new Developer("An"), new Designer("Binh"));
        if (!Case1Utils.aggregate(people).equals("DEV:An|DESIGN:Binh")) throw new AssertionError();
        var expanded = List.<EmployeeReport>of(new Developer("An"), new Designer("Binh"), new Intern("Chi"));
        if (!Case1Utils.aggregate(expanded).endsWith("INTERN:Chi")) throw new AssertionError();
    }

    public void executeCase2() {
        Transaction card = new Transaction(Kind.CARD, 1000);
        if (!Case2Utils.controller(card).equals(Case2Utils.service(card))) throw new AssertionError();
        if (!Case2Utils.service(card).equals(Case2Utils.ui(card))) throw new AssertionError();
        if (Case2Utils.ui(card).payableCents() != 1050) throw new AssertionError();
    }

    public void executeCase3() {
        Case3Utils.contract(new DirectTax());
        Case3Utils.contract(new ConfigurableTax(new BigDecimal("0.10")));
    }

    public void executeCase4() {
        List<Exportable> records = List.of(new PdfRecord("a"), new CsvRecord("b"));
        if (!"PDF:a|CSV:b".equals(Case4Utils.exportAll(records))) throw new AssertionError();
        // Sai: for (RecordView r : records) ((PdfRecord) r).export();
        // Cast trên sẽ thất bại với CsvRecord.
    }

    public void executeCase5() {
        Map<Channel, NotificationSender> initial = Map.of(
        Channel.EMAIL, new EmailSender(), Channel.SMS, new SmsSender());
        Dispatcher before = new Dispatcher(initial);
        Map<Channel, NotificationSender> expanded = new EnumMap<>(initial);
        expanded.put(Channel.PUSH, new PushSender());
        Dispatcher after = new Dispatcher(expanded);
        for (Channel c : initial.keySet())
        if (!before.dispatch(c, "hi").equals(after.dispatch(c, "hi")))
        throw new AssertionError("Existing channel regressed");
        if (!"push:hi".equals(after.dispatch(Channel.PUSH, "hi"))) throw new AssertionError();
    }

    public void executeCase6() {
        Case6Utils.contract(new LocalQuote());
        Case6Utils.contract(new CachedRulesQuote());
    }

    public void executeCase7() {
        Promotion sale = cents -> cents - 100;
        CustomerPolicy vip = cents -> cents * 9 / 10;
        Shipping digital = () -> 0;
        if (Case7Utils.checkout(1000, true, Delivery.DIGITAL, sale, vip, digital) != 810)
        throw new AssertionError();
        try {
            Case7Utils.checkout(1000, true, Delivery.PHYSICAL, sale, vip, digital);
            throw new AssertionError("Invalid combination was accepted");
        } catch (IllegalArgumentException expected) {}
    }

    public void executeCase8() {
        Pricing regular = amount -> amount;
        Pricing sale = amount -> amount * 9 / 10;
        Checkout8 checkout = new Checkout8(regular);
        Checkout8 sameOwner = checkout;
        if (checkout.total(1000) != 1000) throw new AssertionError();
        checkout.choose(sale);
        if (checkout != sameOwner || checkout.total(1000) != 900)
        throw new AssertionError("Runtime strategy switch failed");
        checkout.choose(regular);
        if (checkout.total(1000) != 1000) throw new AssertionError();
    }

    public void executeCase9() {
        String output = Case9Utils.render(List.of(new Circle(3), new Square(2)));
        if (!"circle:3|square:2".equals(output)) throw new AssertionError();
        // Tránh: if (shape.getClass().getSimpleName().equals("Circle")) ...
    }

    public void executeCase10() {
        Combined c = new Combined();
        if (!"primary+secondary".equals(c.label())) throw new AssertionError();
    }

    public void executeCase11() {
        Player p = new Player();
        p.change(State.RUNNING);
        p.change(State.JUMPING);
        if (p.state() != State.JUMPING) throw new AssertionError();
        try {
            p.change(State.IDLE);
            throw new AssertionError("Illegal transition accepted");
        } catch (IllegalStateException expected) {}
        if (p.state() != State.JUMPING) throw new AssertionError("State mutated on failure");
    }

    public void executeCase12() {
        if (!"CARD:100".equals(new Checkout12(new CardFlow()).execute(new Order(100, null))))
        throw new AssertionError();
        if (!"BANK:R1".equals(new Checkout12(new BankFlow()).execute(new Order(100, "R1"))))
        throw new AssertionError();
        try {
            new Checkout12(new BankFlow()).execute(new Order(100, null));
            throw new AssertionError("Missing bank reference was accepted");
        } catch (IllegalArgumentException expected) {}
    }

    public void executeCase13() {
        LibrarySession session = new LibrarySession();
        try (session) {
            LibraryAdapter adapter = new LibraryAdapter(new LibraryUtility());
            if (!"lib:abc".equals(adapter.execute(session, " abc ")))
            throw new AssertionError();
        }
        if (session.isActive()) throw new AssertionError("Session leaked");
    }
}
