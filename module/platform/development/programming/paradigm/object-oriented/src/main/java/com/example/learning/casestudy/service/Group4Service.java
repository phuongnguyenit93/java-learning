package com.example.learning.casestudy.service;

import com.example.learning.casestudy.adapter.group4.case1.VendorAAdapter;
import com.example.learning.casestudy.adapter.group4.case1.VendorBAdapter;
import com.example.learning.casestudy.adapter.group4.case10.A10;
import com.example.learning.casestudy.adapter.group4.case10.B10;
import com.example.learning.casestudy.adapter.group4.case2.MemoryLookup;
import com.example.learning.casestudy.adapter.group4.case2.RemoteLookup;
import com.example.learning.casestudy.adapter.group4.case5.CatalogMirror;
import com.example.learning.casestudy.adapter.group4.case7.FormatterImpl;
import com.example.learning.casestudy.adapter.group4.case9.A9;
import com.example.learning.casestudy.adapter.group4.case9.B9;
import com.example.learning.casestudy.application.group4.case1.Checkout;
import com.example.learning.casestudy.application.group4.case11.DbWorkflow;
import com.example.learning.casestudy.application.group4.case11.FileWorkflow;
import com.example.learning.casestudy.application.group4.case11.Workflow;
import com.example.learning.casestudy.application.group4.case3.ReportingService;
import com.example.learning.casestudy.application.group4.case6.SubmitOrder;
import com.example.learning.casestudy.application.group4.case7.Formatter;
import com.example.learning.casestudy.application.group4.case7.LegacyForwarder;
import com.example.learning.casestudy.domain.group4.case4.AuditOnly;
import com.example.learning.casestudy.domain.group4.case4.Combined;
import com.example.learning.casestudy.dto.group4.case8.TransferCommand;
import com.example.learning.casestudy.model.group4.case10.MutableItem;
import com.example.learning.casestudy.model.group4.case5.Product;
import com.example.learning.casestudy.port.group4.case3.InvoiceAdmin;
import com.example.learning.casestudy.port.group4.case3.InvoiceReader;
import com.example.learning.casestudy.port.group4.case5.ProductReader;
import com.example.learning.casestudy.repository.group4.case3.Store;
import com.example.learning.casestudy.repository.group4.case5.MemoryStore;
import com.example.learning.casestudy.utils.group4.case1.Case1Utils;
import com.example.learning.casestudy.utils.group4.case10.Case10Utils;
import com.example.learning.casestudy.utils.group4.case11.Case11Utils;
import com.example.learning.casestudy.utils.group4.case2.Case2Utils;
import com.example.learning.casestudy.utils.group4.case3.Case3Utils;
import com.example.learning.casestudy.utils.group4.case4.Case4Utils;
import com.example.learning.casestudy.utils.group4.case5.Case5Utils;
import com.example.learning.casestudy.utils.group4.case6.Case6Utils;
import com.example.learning.casestudy.utils.group4.case7.Case7Utils;
import com.example.learning.casestudy.utils.group4.case8.Case8Utils;
import com.example.learning.casestudy.utils.group4.case9.Case9Utils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service("caseStudyGroup4Service")
public class Group4Service {
    public void executeCase1() {
        Checkout withA = new Checkout(new VendorAAdapter());
        Checkout withB = new Checkout(new VendorBAdapter());
        Case1Utils.check(!withA.pay("O1", 1_500));
        Case1Utils.check(withB.pay("O1", 1_500)); // Checkout không đổi khi thay adapter.
        try {
            withB.pay("", 100);
            throw new AssertionError("invalid order accepted");
        } catch (IllegalArgumentException expected) { }
        System.out.println("PASS: domain port independent of vendor");
    }

    public void executeCase2() {
        Case2Utils.contract(new MemoryLookup());
        Case2Utils.contract(new RemoteLookup());
        System.out.println("PASS: identical Optional semantics");
    }

    public void executeCase3() {
        Store store = new Store();
        InvoiceReader readOnly = store;
        ReportingService reporting = new ReportingService(readOnly);
        Case3Utils.check(reporting.report("I1").equals("Invoice:I1"));
        // readOnly.delete("I1"); // Không biên dịch: Reader không có delete.
        InvoiceAdmin admin = store;
        admin.delete("I1");
        try {
            reporting.report("I1");
            throw new AssertionError("deleted invoice was readable");
        } catch (IllegalStateException expected) { }
        System.out.println("PASS: read-only contract limits capabilities");
    }

    public void executeCase4() {
        Case4Utils.check(new AuditOnly().label().equals("Audit"));
        Case4Utils.check(new Combined().label().equals("Payment / Audit"));
        System.out.println("PASS: explicit default resolution");
    }

    public void executeCase5() {
        ProductReader mirror = new CatalogMirror();
        Case5Utils.check(mirror.find("A").isPresent());
        Case5Utils.check(mirror.find("B").isEmpty());
        // mirror.save(new Product("C")); // Không biên dịch.
        MemoryStore writable = new MemoryStore();
        writable.save(new Product("C"));
        Case5Utils.check(writable.find("C").orElseThrow().sku().equals("C"));
        System.out.println("PASS: read and write capabilities separated");
    }

    public void executeCase6() {
        List<String> calls = new ArrayList<>();
        SubmitOrder ok = new SubmitOrder(
            id -> calls.add("save:" + id),
            id -> calls.add("event:" + id),
            id -> calls.add("mail:" + id));
        ok.submit("O1");
        Case6Utils.check(calls.equals(List.of("save:O1","event:O1","mail:O1")));
        SubmitOrder failing = new SubmitOrder(
            id -> calls.add("save:" + id),
            id -> { throw new IllegalStateException("queue down"); },
            id -> calls.add("mail:" + id));
        try {
            failing.submit("O2");
            throw new AssertionError("missing event failure");
        } catch (IllegalStateException expected) { }
        Case6Utils.check(!calls.contains("mail:O2"));
        System.out.println("PASS: dependencies replaced by fakes");
    }

    public void executeCase7() {
        LegacyForwarder old = new LegacyForwarder(new FormatterImpl());
        Formatter simple = new Formatter();
        for (String input : new String[]{"hello", "  world "})
            Case7Utils.check(old.execute(input).equals(simple.format(input)));
        try {
            simple.format(" ");
            throw new AssertionError("blank accepted");
        } catch (IllegalArgumentException expected) { }
        System.out.println("PASS: no speculative forwarding contract needed");
    }

    public void executeCase8() {
        try {
            Case8Utils.brokenExecute(Map.of("amount", "100"));
            throw new AssertionError("type mismatch accepted");
        } catch (ClassCastException expected) { }
        try {
            Case8Utils.brokenExecute(Map.of("amunt", 100L));
            throw new AssertionError("missing key accepted");
        } catch (NullPointerException expected) { }
        Case8Utils.check(Case8Utils.execute(new TransferCommand("ACC1", 100L)) == 100L);
        try {
            new TransferCommand("", 100L);
            throw new AssertionError("invalid destination accepted");
        } catch (IllegalArgumentException expected) { }
        // Case8Utils.execute(new TransferCommand("ACC1", "100")); // Không biên dịch.
        System.out.println("PASS: typed command has explicit schema");
    }

    public void executeCase9() {
        Case9Utils.contract(new A9());
        Case9Utils.contract(new B9());
        System.out.println("PASS: consistent null precondition");
    }

    public void executeCase10() {
        MutableItem a=new MutableItem("A",2);
        MutableItem b=new MutableItem("B",2);
        Case10Utils.contract(new A10(new ArrayList<>(List.of(a))),a);
        Case10Utils.contract(new B10(new ArrayList<>(List.of(b))),b);
        System.out.println("PASS: both implementations honor snapshot contract");
    }

    public void executeCase11() {
        List<String> db=new ArrayList<>();
        new DbWorkflow(db).execute("ok");
        Case11Utils.check(db.equals(List.of("validate","db:ok","done")));
        List<String> file=new ArrayList<>();
        Workflow flow=new FileWorkflow(file);
        try {
            flow.execute("");
            throw new AssertionError("blank accepted");
        } catch(IllegalArgumentException expected) {}
        Case11Utils.check(file.isEmpty()); // Ghi chưa xảy ra khi validation lỗi.
        flow.execute("x");
        Case11Utils.check(file.equals(List.of("validate","file:x","done")));
        System.out.println("PASS: invariant sequence cannot be overridden");
    }
}
