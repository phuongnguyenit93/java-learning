package com.example.learning.casestudy.service;

import com.example.learning.casestudy.adapter.group7.case9.SharedLogger;
import com.example.learning.casestudy.application.group7.case10.PricingService;
import com.example.learning.casestudy.application.group7.case11.Service11;
import com.example.learning.casestudy.application.group7.case3.Directory;
import com.example.learning.casestudy.application.group7.case5.Factory;
import com.example.learning.casestudy.application.group7.case6.Checkout;
import com.example.learning.casestudy.application.group7.case8.Bus;
import com.example.learning.casestudy.application.group7.case8.Tab;
import com.example.learning.casestudy.application.group7.case9.Service9;
import com.example.learning.casestudy.domain.group7.case1.Address;
import com.example.learning.casestudy.domain.group7.case1.Customer1;
import com.example.learning.casestudy.domain.group7.case1.Order1;
import com.example.learning.casestudy.domain.group7.case11.RequestConfig;
import com.example.learning.casestudy.domain.group7.case12.Memberships;
import com.example.learning.casestudy.domain.group7.case13.Customer13;
import com.example.learning.casestudy.domain.group7.case13.Order13;
import com.example.learning.casestudy.domain.group7.case2.Node;
import com.example.learning.casestudy.domain.group7.case3.Employee;
import com.example.learning.casestudy.domain.group7.case4.Child;
import com.example.learning.casestudy.domain.group7.case4.Parent;
import com.example.learning.casestudy.domain.group7.case5.Customer5;
import com.example.learning.casestudy.domain.group7.case5.CustomerId;
import com.example.learning.casestudy.domain.group7.case5.Order5;
import com.example.learning.casestudy.domain.group7.case5.OrderId;
import com.example.learning.casestudy.domain.group7.case6.Customer6;
import com.example.learning.casestudy.domain.group7.case6.Wallet;
import com.example.learning.casestudy.domain.group7.case7.Cart;
import com.example.learning.casestudy.dto.group7.case4.ParentDto;
import com.example.learning.casestudy.model.group7.case11.MutableConfig;
import com.example.learning.casestudy.model.group7.case7.BadCart;
import com.example.learning.casestudy.port.group7.case10.PriceRepository;
import com.example.learning.casestudy.repository.group7.case13.Customers;
import com.example.learning.casestudy.utils.group7.case1.Case1Utils;
import com.example.learning.casestudy.utils.group7.case10.Case10Utils;
import com.example.learning.casestudy.utils.group7.case11.Case11Utils;
import com.example.learning.casestudy.utils.group7.case12.Case12Utils;
import com.example.learning.casestudy.utils.group7.case13.Case13Utils;
import com.example.learning.casestudy.utils.group7.case2.Case2Utils;
import com.example.learning.casestudy.utils.group7.case3.Case3Utils;
import com.example.learning.casestudy.utils.group7.case4.Case4Utils;
import com.example.learning.casestudy.utils.group7.case5.Case5Utils;
import com.example.learning.casestudy.utils.group7.case6.Case6Utils;
import com.example.learning.casestudy.utils.group7.case7.Case7Utils;
import com.example.learning.casestudy.utils.group7.case8.Case8Utils;
import com.example.learning.casestudy.utils.group7.case9.Case9Utils;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service("caseStudyGroup7Service")
public class Group7Service {
    public void executeCase1() {
        Customer1 customer = new Customer1("C1", new Address("Số 10"));
        Order1 order = new Order1("O1", customer.id, customer.currentAddress());
        customer.changeAddress(new Address("Số 20"));
        Case1Utils.check(customer.currentAddress().street().equals("Số 20"));
        Case1Utils.check(order.shippingAtPurchase().street().equals("Số 10"));
        Case1Utils.check(order.customerId().equals("C1"));
        System.out.println("PASS: historical shipping snapshot");

    }

    public void executeCase2() {
        Node a = new Node("A"), b = new Node("B"), child = new Node("C");
        child.moveTo(a);
        Case2Utils.check(a.children.contains(child) && child.parent == a);
        child.moveTo(b);
        Case2Utils.check(!a.children.contains(child));
        Case2Utils.check(b.children.contains(child) && child.parent == b);
        try {
            b.moveTo(child);
            throw new AssertionError("cycle accepted");

        } catch (IllegalArgumentException expected) {

        }
        Case2Utils.check(b.parent == null && child.parent == b);
        child.moveTo(null);
        Case2Utils.check(child.parent == null && b.children.isEmpty());
        System.out.println("PASS: reparent consistent");

    }

    public void executeCase3() {
        Directory d = new Directory();
        d.addDepartment("OLD");
        d.addDepartment("NEW");
        d.hire(new Employee("E1", "Lan"), "OLD");
        try {
            d.deleteDepartment("OLD");
            throw new AssertionError();

        } catch (IllegalStateException expected){

        }
        d.transfer("E1", "OLD", "NEW");
        d.deleteDepartment("OLD");
        Case3Utils.check(d.employees.containsKey("E1"));
        Case3Utils.check(d.departments.get("NEW").contains("E1"));
        Case3Utils.check(!d.departments.containsKey("OLD"));
        System.out.println("PASS: employee lifecycle independent");

    }

    public void executeCase4() {
        Parent p = new Parent("P");
        Child c = new Child("C", p);
        p.children.add(c);
        Case4Utils.check(c.parent == p && p.children.contains(c)); // Cycle object graph.
        ParentDto dto = Case4Utils.toDto(p);
        Case4Utils.check(dto.childIds().equals(List.of("C")));
        Case4Utils.check(Case4Utils.toJsonLike(dto).equals("{\"id\":\"P\",\"childIds\":[\"C\"]}"));
        System.out.println("PASS: exported DTO is acyclic");

    }

    public void executeCase5() {
        Customer5 customer = new Customer5(new CustomerId("C1"));
        Order5 order = new Factory().createOrder(new OrderId("O1"), customer);
        Case5Utils.check(order.customerId().equals(customer.id()));
        Case5Utils.check(order.id().value().equals("O1"));
        try {
            new Factory().createOrder(new OrderId("O2"), null);
            throw new AssertionError("null customer accepted");

        } catch (IllegalArgumentException expected){

        }
        System.out.println("PASS: independent valid aggregates");

    }

    public void executeCase6() {
        Customer6 c = new Customer6(new Wallet(1_000));
        Checkout service = new Checkout();
        Case6Utils.check(service.decision(c, 800).equals("APPROVED"));
        Case6Utils.check(service.decision(c, 1_200).equals("DECLINED"));
        Case6Utils.check(service.decision(c, -1).equals("DECLINED"));
        System.out.println("PASS: caller does not inspect Wallet internals");

    }

    public void executeCase7() {
        BadCart bad = new BadCart();
        bad.items().addAll(List.of("A", "B", "C"));
        Case7Utils.check(bad.items().size() == 3); // Bỏ qua giới hạn.
        Cart cart = new Cart(2);
        cart.addItem("A");
        cart.addItem("B");
        try {
            cart.addItem("C");
            throw new AssertionError();

        } catch (IllegalArgumentException expected){

        }
        try {
            cart.snapshot().add("X");
            throw new AssertionError();

        } catch (UnsupportedOperationException expected){

        }
        Case7Utils.check(cart.snapshot().equals(List.of("A", "B")));
        System.out.println("PASS: capacity invariant enforced");

    }

    public void executeCase8() {
        try {
            Bus bus = new Bus();
            Tab tab = new Tab(bus);
            bus.publish("first");
            Case8Utils.check(tab.received.equals(List.of("first")));
            Case8Utils.check(bus.listenerCount() == 1);
            tab.close();
            bus.publish("second");
            Case8Utils.check(bus.listenerCount() == 0);
            Case8Utils.check(tab.received.equals(List.of("first")));
            System.out.println("PASS: unsubscribe prevents retained subscription");

        } catch (Exception sourceException) {
            throw Case8Utils.propagate(sourceException);

        }

    }

    public void executeCase9() {
        SharedLogger logger = new SharedLogger();
        Service9 a = new Service9(logger), b = new Service9(logger);
        a.handle();
        a.close();
        Case9Utils.check(!logger.closed());
        b.handle();
        Case9Utils.check(logger.count() == 2);
        b.close();
        logger.close();
        Case9Utils.check(logger.closed());
        System.out.println("PASS: shared collaborator closed only by owner");

    }

    public void executeCase10() {
        PriceRepository fake = sku->"A".equals(sku)?300:500;
        PricingService service = new PricingService(fake);
        Case10Utils.check(service.total("A", 2) == 600);
        Case10Utils.check(service.total("B", 1) == 500);
        try {
            service.total("A", 0);
            throw new AssertionError();

        } catch (IllegalArgumentException expected){

        }
        PricingService invalid = new PricingService(sku->-1);
        try {
            invalid.total("A", 1);
            throw new AssertionError();

        } catch (IllegalStateException expected){

        }
        System.out.println("PASS: pure unit test without a database");

    }

    public void executeCase11() {
        MutableConfig shared = new MutableConfig();
        MutableConfig requestA = shared, requestB = shared;
        requestB.timeout = 99;
        Case11Utils.check(requestA.timeout == 99); // Lỗi alias.
        RequestConfig snapshotA = new RequestConfig(10);
        RequestConfig snapshotB = new RequestConfig(99);
        Service11 a = new Service11(snapshotA), b = new Service11(snapshotB);
        Case11Utils.check(a.timeout() == 10 && b.timeout() == 99);
        try {
            new RequestConfig(-1);
            throw new AssertionError();

        } catch (IllegalArgumentException expected){

        }
        System.out.println("PASS: per-request immutable config");

    }

    public void executeCase12() {
        Memberships m = new Memberships();
        m.add("G1", "I1");
        m.add("G2", "I1");
        m.add("G1", "I1");
        Case12Utils.check(m.groupsOf("I1").equals(Set.of("G1", "G2")));
        Case12Utils.check(m.itemsOf("G1").equals(Set.of("I1")));
        m.remove("G1", "I1");
        Case12Utils.check(m.groupsOf("I1").equals(Set.of("G2")));
        Case12Utils.check(m.itemsOf("G1").isEmpty());
        try {
            m.add("", "I1");
            throw new AssertionError();

        } catch (IllegalArgumentException expected){

        }
        System.out.println("PASS: many-to-many membership consistent");

    }

    public void executeCase13() {
        Customers registry = new Customers();
        registry.save(new Customer13("C1", "Lan"));
        Customer13 staleCopy = registry.find("C1");
        Order13 a = new Order13("O1", "C1"), b = new Order13("O2", "C1");
        registry.save(new Customer13("C1", "Lan Nguyễn"));
        Case13Utils.check(staleCopy.name().equals("Lan")); // Bản copy không tự sync.
        Case13Utils.check(registry.find(a.customerId()).name().equals("Lan Nguyễn"));
        Case13Utils.check(registry.find(b.customerId()).name().equals("Lan Nguyễn"));
        System.out.println("PASS: shared identity resolves current source");

    }
}
