package com.example.learning.casestudy.service;

import org.springframework.stereotype.Service;
import com.example.learning.casestudy.adapter.group3.case13.Publisher;
import com.example.learning.casestudy.application.group3.case13.Broken;
import com.example.learning.casestudy.application.group3.case14.Checkout;
import com.example.learning.casestudy.application.group3.case14.Report;
import com.example.learning.casestudy.application.group3.case7.Transfer;
import com.example.learning.casestudy.domain.group3.case1.Order1;
import com.example.learning.casestudy.domain.group3.case1.Status1;
import com.example.learning.casestudy.domain.group3.case11.ImmutableExpiry;
import com.example.learning.casestudy.domain.group3.case12.Order12;
import com.example.learning.casestudy.domain.group3.case12.Status12;
import com.example.learning.casestudy.domain.group3.case13.Store;
import com.example.learning.casestudy.domain.group3.case14.Line;
import com.example.learning.casestudy.domain.group3.case14.PricePolicy;
import com.example.learning.casestudy.domain.group3.case2.Invoice2;
import com.example.learning.casestudy.domain.group3.case3.Cart;
import com.example.learning.casestudy.domain.group3.case4.Safe4;
import com.example.learning.casestudy.domain.group3.case5.Profile;
import com.example.learning.casestudy.domain.group3.case6.DateRange;
import com.example.learning.casestudy.domain.group3.case8.Account;
import com.example.learning.casestudy.domain.group3.case8.BadAccount;
import com.example.learning.casestudy.domain.group3.case9.Invoice9;
import com.example.learning.casestudy.domain.group3.case9.State;
import com.example.learning.casestudy.dto.group3.case5.AddressView;
import com.example.learning.casestudy.model.group3.case1.BadOrder;
import com.example.learning.casestudy.model.group3.case10.Bad10;
import com.example.learning.casestudy.model.group3.case10.Item10;
import com.example.learning.casestudy.model.group3.case10.Safe10;
import com.example.learning.casestudy.model.group3.case11.Bad11;
import com.example.learning.casestudy.model.group3.case11.Safe11;
import com.example.learning.casestudy.model.group3.case12.Bad12;
import com.example.learning.casestudy.model.group3.case2.Bad2;
import com.example.learning.casestudy.model.group3.case3.BadCart;
import com.example.learning.casestudy.model.group3.case4.Bad4;
import com.example.learning.casestudy.model.group3.case5.Bad5;
import com.example.learning.casestudy.model.group3.case6.BadRange;
import com.example.learning.casestudy.model.group3.case7.Bad7;
import com.example.learning.casestudy.model.group3.case9.Bad9;
import com.example.learning.casestudy.port.group3.case8.Fees;
import com.example.learning.casestudy.utils.group3.case1.Case1Utils;
import com.example.learning.casestudy.utils.group3.case10.Case10Utils;
import com.example.learning.casestudy.utils.group3.case11.Case11Utils;
import com.example.learning.casestudy.utils.group3.case12.Case12Utils;
import com.example.learning.casestudy.utils.group3.case13.Case13Utils;
import com.example.learning.casestudy.utils.group3.case14.Case14Utils;
import com.example.learning.casestudy.utils.group3.case2.Case2Utils;
import com.example.learning.casestudy.utils.group3.case3.Case3Utils;
import com.example.learning.casestudy.utils.group3.case4.Case4Utils;
import com.example.learning.casestudy.utils.group3.case5.Case5Utils;
import com.example.learning.casestudy.utils.group3.case6.Case6Utils;
import com.example.learning.casestudy.utils.group3.case7.Case7Utils;
import com.example.learning.casestudy.utils.group3.case8.Case8Utils;
import com.example.learning.casestudy.utils.group3.case9.Case9Utils;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Service("caseStudyGroup3Service")
public class Group3Service {
    public void executeCase1() {
        BadOrder bad = new BadOrder();
        bad.status = Status1.DELIVERED;
        Case1Utils.check(bad.status == Status1.DELIVERED);
        Order1 good = new Order1();
        Case1Utils.fails(good::deliver);
        Case1Utils.fails(()->good.pay(false));
        Case1Utils.check(good.state() == Status1.CREATED);
        good.pay(true);
        good.deliver();
        Case1Utils.check(good.state() == Status1.DELIVERED);
        Case1Utils.fails(good::deliver);
        System.out.println("PASS");
    }

    public void executeCase2() {
        Bad2 bad = new Bad2();
        bad.setDiscount(20);
        try {
            bad.setTotal(-1);
        }
        catch (IllegalArgumentException expected) {
        }
        Case2Utils.check(bad.total != bad.subtotal-bad.discount);
        Invoice2 good = new Invoice2();
        good.changeDiscount(20);
        Case2Utils.check(good.total() == 80);
        try {
            good.changeDiscount(120);
            throw new AssertionError();
        }
        catch (IllegalArgumentException expected) {
        }
        Case2Utils.check(good.total() == 80);
        good.changeDiscount(100);
        Case2Utils.check(good.total() == 0);
        System.out.println("PASS");
    }

    public void executeCase3() {
        BadCart bad = new BadCart();
        bad.items().clear();
        Case3Utils.check(bad.items().isEmpty());
        Cart c = new Cart();
        try {
            c.add("A", 3, 2);
            throw new AssertionError();
        }
        catch (IllegalArgumentException expected) {
        }
        c.add("A", 1, 2);
        try {
            c.items().clear();
            throw new AssertionError();
        }
        catch (UnsupportedOperationException expected) {
        }
        c.checkout();
        try {
            c.add("B", 1, 2);
            throw new AssertionError();
        }
        catch (IllegalStateException expected) {
        }
        Case3Utils.check(c.items().size() == 1);
        System.out.println("PASS");
    }

    public void executeCase4() {
        byte[] a = {
            1, 2, 3
        }
        ;
        Bad4 bad = new Bad4(a);
        bad.data()[0] = 8;
        Case4Utils.check(a[0] == 8);
        byte[] source = {
            1, 2, 3
        }
        ;
        Safe4 safe = new Safe4(source);
        source[0] = 7;
        safe.snapshot()[1] = 9;
        Case4Utils.check(Arrays.equals(safe.snapshot(), new byte[] {
            1, 2, 3
        }
        ));
        System.out.println("PASS");
    }

    public void executeCase5() {
        Bad5 bad = new Bad5();
        bad.getAddress().street ="";
        Case5Utils.check(bad.getAddress().street.isBlank());
        Profile good = new Profile();
        AddressView before = good.address();
        try {
            good.change("");
            throw new AssertionError();
        }
        catch (IllegalArgumentException expected) {
        }
        Case5Utils.check(good.address().street().equals("Main"));
        good.change("Second");
        Case5Utils.check(good.address().street().equals("Second"));
        Case5Utils.check(before.street().equals("Main"));
        System.out.println("PASS");
    }

    public void executeCase6() {
        LocalDate first = LocalDate.of(2026, 11, 1);
        LocalDate earlier = LocalDate.of(2026, 10, 30);
        BadRange broken = new BadRange(first, earlier);
        Case6Utils.check(broken.end().isBefore(broken.start()));
        try {
            new DateRange(first, earlier);
            throw new AssertionError();
        }
        catch (IllegalArgumentException expected) {
        }
        DateRange ok = new DateRange(earlier, first);
        Case6Utils.check(ok.end().isAfter(ok.start()));
        Case6Utils.check(new DateRange(first, first).start().equals(first));
        System.out.println("PASS");
    }

    public void executeCase7() {
        Bad7 bad = new Bad7();
        bad.batch(2000);
        Case7Utils.check(bad.sent == 2000);
        Transfer service = new Transfer();
        Runnable web = ()->service.execute(2000);
        Runnable batch = ()->service.execute(2000);
        Case7Utils.rejected(web);
        Case7Utils.rejected(batch);
        Case7Utils.check(service.sent() == 0);
        service.execute(500);
        Case7Utils.check(service.sent() == 500);
        System.out.println("PASS");
    }

    public void executeCase8() {
        Fees broken = a-> {
            throw new IllegalArgumentException("fee");
        }
        ;
        BadAccount bad = new BadAccount();
        try {
            bad.withdraw(40, broken);
        }
        catch (IllegalArgumentException expected) {
        }
        Case8Utils.check(bad.balance == 60);
        Account good = new Account();
        try {
            good.withdraw(40, broken);
        }
        catch (IllegalArgumentException expected) {
        }
        Case8Utils.check(good.balance() == 100);
        try {
            good.withdraw(90, a->20);
        }
        catch (IllegalArgumentException expected) {
        }
        Case8Utils.check(good.balance() == 100);
        good.withdraw(40, a->5);
        Case8Utils.check(good.balance() == 55);
        System.out.println("PASS");
    }

    public void executeCase9() {
        Bad9 bad = new Bad9();
        bad.firstStep();
        Case9Utils.check(bad.subtotal == 100 && bad.discount == 20);
        Invoice9 good = new Invoice9();
        State before = good.snapshot();
        good.update(120, 20);
        State after = good.snapshot();
        Case9Utils.check(before.total() == 100 && after.total() == 100);
        Case9Utils.check(after.subtotal() == 120 && after.discount() == 20);
        try {
            good.update(10, 20);
            throw new AssertionError();
        }
        catch (IllegalArgumentException expected) {
        }
        Case9Utils.check(good.snapshot() == after);
        System.out.println("PASS");
    }

    public void executeCase10() {
        Item10 item = new Item10("A", 2);
        List<Item10> input = new ArrayList<>(List.of(item));
        Bad10 bad = new Bad10(input);
        Safe10 safe = new Safe10(input);
        item.count = 9;
        input.clear();
        Case10Utils.check(bad.items.get(0).count == 9);
        Case10Utils.check(safe.items.size() == 1 && safe.items.get(0).count() == 2);
        try {
            safe.items.clear();
            throw new AssertionError();
        }
        catch (UnsupportedOperationException expected) {
        }
        System.out.println("PASS");
    }

    public void executeCase11() {
        Date input = new Date(1000);
        Bad11 bad = new Bad11(input);
        input.setTime(2000);
        Case11Utils.check(bad.date().getTime() == 2000);
        bad.date().setTime(3000);
        Case11Utils.check(bad.date().getTime() == 3000);
        Date copyInput = new Date(1000);
        Safe11 safe = new Safe11(copyInput);
        copyInput.setTime(2000);
        safe.date().setTime(4000);
        Case11Utils.check(safe.date().getTime() == 1000);
        Case11Utils.check(new ImmutableExpiry(Instant.ofEpochMilli(1000)) .deadline().toEpochMilli() == 1000);
        System.out.println("PASS");
    }

    public void executeCase12() {
        Bad12 bad = new Bad12();
        bad.state = Status12.CANCELLED;
        Case12Utils.check(bad.state == Status12.CANCELLED);
        Order12 unpaid = new Order12();
        unpaid.cancel();
        Case12Utils.check(unpaid.status() == Status12.CANCELLED && !unpaid.refundNeeded());
        Order12 paid = new Order12();
        paid.pay();
        paid.cancel();
        Case12Utils.check(paid.refundNeeded());
        Order12 shipped = new Order12();
        shipped.pay();
        shipped.ship();
        try {
            shipped.cancel();
            throw new AssertionError();
        }
        catch (IllegalStateException expected) {
        }
        Case12Utils.check(shipped.status() == Status12.SHIPPED);
        System.out.println("PASS");
    }

    public void executeCase13() {
        Broken bad = new Broken();
        try {
            bad.pay(()-> {
                throw new IllegalStateException();
            }
            );
        }
        catch (IllegalStateException expected) {
        }
        Case13Utils.check(bad.state.equals("PAID"));
        Store s = new Store();
        try {
            s.pay(false);
        }
        catch (IllegalArgumentException expected) {
        }
        Case13Utils.check(s.state.equals("NEW") && s.outbox.isEmpty());
        s.pay(true);
        Publisher pub = new Publisher();
        try {
            pub.run(s, e-> {
                throw new IllegalStateException();
            }
            );
        }
        catch (IllegalStateException expected) {
        }
        List<String> delivered = new ArrayList<>();
        pub.run(s, e->delivered.add(e.id()));
        pub.run(s, e->delivered.add(e.id()));
        Case13Utils.check(delivered.equals(List.of("e1")));
        System.out.println("PASS");
    }

    public void executeCase14() {
        Line line = new Line(1000, 2, 250);
        long oldReport = line.unitPrice()*line.qty();
        PricePolicy policy = new PricePolicy();
        long checkout = new Checkout(policy).charge(line);
        long report = new Report(policy).total(line);
        Case14Utils.check(oldReport != checkout);
        Case14Utils.check(checkout == 1750 && checkout == report);
        try {
            policy.quote(new Line(100, 1, 200));
            throw new AssertionError();
        }
        catch (IllegalArgumentException expected) {
        }
        System.out.println("PASS");
    }
}
