package com.example.learning.casestudy.service;

import com.example.learning.casestudy.application.group1.case1.HospitalUseCase;
import com.example.learning.casestudy.application.group1.case10.LegacyManager;
import com.example.learning.casestudy.application.group1.case2.Provider;
import com.example.learning.casestudy.application.group1.case2.SimplifiedRenderer;
import com.example.learning.casestudy.application.group1.case5.ReportFormatter;
import com.example.learning.casestudy.application.group1.case5.SendReport;
import com.example.learning.casestudy.application.group1.case6.Shipping;
import com.example.learning.casestudy.domain.group1.case1.AppointmentBook;
import com.example.learning.casestudy.domain.group1.case1.Patient;
import com.example.learning.casestudy.domain.group1.case10.PricingPolicy;
import com.example.learning.casestudy.domain.group1.case11.Order11;
import com.example.learning.casestudy.domain.group1.case11.Status11;
import com.example.learning.casestudy.domain.group1.case3.Account;
import com.example.learning.casestudy.domain.group1.case3.BadAccount;
import com.example.learning.casestudy.domain.group1.case4.CustomerId;
import com.example.learning.casestudy.domain.group1.case4.SalesCustomer;
import com.example.learning.casestudy.domain.group1.case4.SupportCustomer;
import com.example.learning.casestudy.domain.group1.case6.Express;
import com.example.learning.casestudy.domain.group1.case6.Standard;
import com.example.learning.casestudy.domain.group1.case7.Payment;
import com.example.learning.casestudy.domain.group1.case7.Status7;
import com.example.learning.casestudy.domain.group1.case8.Employee;
import com.example.learning.casestudy.dto.group1.case11.OrderDto;
import com.example.learning.casestudy.model.group1.case10.Quote;
import com.example.learning.casestudy.model.group1.case12.Row;
import com.example.learning.casestudy.model.group1.case2.Order2;
import com.example.learning.casestudy.model.group1.case5.Sale;
import com.example.learning.casestudy.port.group1.case1.Messenger;
import com.example.learning.casestudy.port.group1.case5.EmailGateway;
import com.example.learning.casestudy.port.group1.case5.SalesRepository;
import com.example.learning.casestudy.utils.group1.case1.Case1Utils;
import com.example.learning.casestudy.utils.group1.case10.Case10Utils;
import com.example.learning.casestudy.utils.group1.case11.Case11Utils;
import com.example.learning.casestudy.utils.group1.case12.Case12Utils;
import com.example.learning.casestudy.utils.group1.case2.Case2Utils;
import com.example.learning.casestudy.utils.group1.case3.Case3Utils;
import com.example.learning.casestudy.utils.group1.case4.Case4Utils;
import com.example.learning.casestudy.utils.group1.case5.Case5Utils;
import com.example.learning.casestudy.utils.group1.case6.Case6Utils;
import com.example.learning.casestudy.utils.group1.case7.Case7Utils;
import com.example.learning.casestudy.utils.group1.case8.Case8Utils;
import com.example.learning.casestudy.utils.group1.case9.Case9Utils;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service("caseStudyGroup1Service")
public class Group1Service {
    public void executeCase1() {
        Patient p = new Patient("P-1");
        AppointmentBook book = new AppointmentBook();
        List<String> sent = new ArrayList<>();
        Messenger fake = sent::add;
        HospitalUseCase oldFee = new HospitalUseCase(book, fee -> fee, fake);
        Case1Utils.check(oldFee.book(p, "9:00", 100_000) == 100_000);
        HospitalUseCase newFee = new HospitalUseCase(book, fee -> fee + 20_000, fake);
        Case1Utils.check(newFee.book(p, "9:00", 100_000) == 120_000);
        Case1Utils.check("9:00".equals(book.slotFor("P-1")));
        Case1Utils.check(sent.equals(List.of("P-1", "P-1")));
        System.out.println("PASS: phí thay đổi, lịch hẹn vẫn đúng");

    }

    public void executeCase2() {
        Provider oldFlow = new Provider();
        SimplifiedRenderer newFlow = new SimplifiedRenderer();
        for (Order2 o : new Order2[] {
                new Order2("A", 0), new Order2("B", 1_250)
        }) {
            Case2Utils.check(oldFlow.render(o).equals(newFlow.render(o)));
        }
        Case2Utils.rejects(oldFlow::render);
        Case2Utils.rejects(newFlow::render);
        System.out.println("PASS: hành vi được giữ, bớt hai tầng chuyển tiếp");

    }

    public void executeCase3() {
        BadAccount broken = new BadAccount();
        broken.balance = -50; // Caller bỏ qua mọi utility method.
        Case3Utils.check(broken.balance < 0);

        Account safe = new Account(100);
        Case3Utils.rejects(() -> safe.withdraw(150));
        Case3Utils.check(safe.balance() == 100); // Lỗi không làm đổi state.
        safe.withdraw(60);
        Case3Utils.check(safe.balance() == 40);
        safe.deposit(10);
        Case3Utils.check(safe.balance() == 50);
        Case3Utils.rejects(() -> new Account(-1));
        System.out.println("PASS: invariant số dư không âm");

    }

    public void executeCase4() {
        CustomerId id = new CustomerId("C-1");
        SalesCustomer sales = new SalesCustomer(id);
        SupportCustomer support = new SupportCustomer(id);
        sales.earnPoints(25);
        support.markPriority();
        Case4Utils.check(sales.id().equals(support.id()));
        Case4Utils.check(sales.points() == 25);
        Case4Utils.check(support.isPriority());
        System.out.println("PASS: cùng identity, hai trách nhiệm độc lập");

    }

    public void executeCase5() {
        List<String> mailbox = new ArrayList<>();
        SalesRepository fakeRepo = () -> List.of(new Sale("S1", 900));
        ReportFormatter formatter = new ReportFormatter();
        new SendReport(fakeRepo, mailbox::add, formatter).execute();
        Case5Utils.check(mailbox.equals(List.of("id,cents\nS1,900\n")));

        EmailGateway failing = ignored -> {
            throw new IllegalStateException("SMTP unavailable");
        };
        try {
            new SendReport(fakeRepo, failing, formatter).execute();
            throw new AssertionError("Expected transport failure");
        } catch (IllegalStateException expected) {
            Case5Utils.check("SMTP unavailable".equals(expected.getMessage()));
        }
        Case5Utils.check("id,cents\nS1,900\n".equals(formatter.format(fakeRepo.findAll())));
        System.out.println("PASS: formatter không phụ thuộc mail");

    }

    public void executeCase6() {
        Shipping baseline = new Shipping(Map.of("standard", new Standard()));
        Case6Utils.check("STD:15000".equals(baseline.dispatch("standard", 1_000)));
        Case6Utils.rejects(() -> baseline.dispatch("unknown", 1_000));

        // Thêm Express bằng registration, không sửa Shipping.dispatch.
        Shipping extended = new Shipping(Map.of(
            "standard", new Standard(), "express", new Express()));
        Case6Utils.check("STD:15000".equals(extended.dispatch("standard", 1_000)));
        Case6Utils.check("EXP:35000".equals(extended.dispatch("express", 1_000)));
        Case6Utils.rejects(() -> extended.dispatch("express", 6_000));
        System.out.println("PASS: kênh mới không đổi policy cũ");

    }

    public void executeCase7() {
        Payment p = new Payment("P-3");
        Case7Utils.check(!Case7Utils.toResponse(p).completed());
        p.confirm();
        Case7Utils.check(Case7Utils.toRow(p).dbStatus().equals("PAID"));
        Case7Utils.check(Case7Utils.toResponse(p).completed());
        Case7Utils.check(Case7Utils.toView(Case7Utils.toResponse(p)).title().equals("Đã thanh toán"));
        Case7Utils.check(p.status() == Status7.PAID);
        System.out.println("PASS: domain khác biểu diễn lưu trữ/API/UI");

    }

    public void executeCase8() {
        LocalDate d = LocalDate.of(1995, 1, 1);
        Employee a = new Employee("E-1", "An", d);
        Employee b = new Employee("E-2", "An", d);
        Employee sameIdentity = new Employee("E-1", "An", d);
        Case8Utils.check(!a.equals(b)); // Khác ID dù giống tên/ngày sinh.
        Case8Utils.check(a.equals(sameIdentity));
        Case8Utils.check(a.hashCode() == sameIdentity.hashCode());
        Set<Employee> employees = new HashSet<>(List.of(a,b,sameIdentity));
        Case8Utils.check(employees.size() == 2);
        Case8Utils.check(employees.contains(new Employee("E-2", "An", d)));
        System.out.println("PASS: employeeId là identity");

    }

    public void executeCase9(String[] args) {
        if (args.length != 1)
            throw new IllegalArgumentException("use: cpu|allocation");
        long result = switch (args[0]) {
            case "cpu" -> Case9Utils.compute(2_000_000);
            case "allocation" -> Case9Utils.allocate(2_000_000);
            default -> throw new IllegalArgumentException(args[0]);
        };
        if (result <= 0) throw new AssertionError("invalid workload");
        System.out.println("workload completed; result=" + result);

    }

    public void executeCase10() {
        LegacyManager oldWay = new LegacyManager();
        PricingPolicy policy = new PricingPolicy();
        Quote expected = oldWay.calculate(1_000, 2, 500);
        Case10Utils.check(expected.equals(policy.quote(1_000, 2, 500)));
        Case10Utils.check(expected.total() == 1_500);
        try {
            policy.quote(1_000, 2, 2_001);
            throw new AssertionError("discount accepted");
        } catch (IllegalArgumentException expectedError) {
            Case10Utils.check(expectedError.getMessage().contains("discount"));
        }
        System.out.println("PASS: đúng business rule, không cần pass-through");

    }

    public void executeCase11() {
        Order11 order = new Order11("O-42");
        OrderDto before = Case11Utils.toDto(order);
        order.markPaid();
        OrderDto after = Case11Utils.toDto(order);
        Case11Utils.check(before.id().equals(after.id()));
        Case11Utils.check(before.status().equals("NEW"));
        Case11Utils.check(after.status().equals("PAID"));
        Case11Utils.check(order.id().equals("O-42")); // Identity không đổi.
        try {
            order.markPaid();
            throw new AssertionError("double payment accepted");
        } catch (IllegalStateException expected) { }
        Case11Utils.check(order.status() == Status11.PAID);
        System.out.println("PASS: entity giữ identity qua lifecycle");

    }

    public void executeCase12() {
        String actual = Case12Utils.export(List.of(
            new Row("An", 100),
            new Row("B, \"VIP\"", 200),
            new Row("Dòng 1\nDòng 2", 300)
        ));
        String expected = "customer,amount\r\n"
            + "\"An\",100\r\n"
            + "\"B, \"\"VIP\"\"\",200\r\n"
            + "\"Dòng 1\nDòng 2\",300\r\n";
        Case12Utils.check(actual.equals(expected));
        try {
            Case12Utils.export(java.util.Collections.singletonList(new Row(null, 1)));
            throw new AssertionError("null accepted");
        } catch (IllegalArgumentException expectedError) { }
        System.out.println("PASS: CSV quoted, escaped, no factory required");

    }
}
