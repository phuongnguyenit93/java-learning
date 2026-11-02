package com.example.learning.casestudy.service;

import com.example.learning.casestudy.adapter.group2.case4.Client;
import com.example.learning.casestudy.adapter.group2.case4.FakeSocket;
import com.example.learning.casestudy.application.group2.case2.Builder2;
import com.example.learning.casestudy.application.group2.case8.Builder8;
import com.example.learning.casestudy.domain.group2.case1.Money;
import com.example.learning.casestudy.domain.group2.case1.Payment1;
import com.example.learning.casestudy.domain.group2.case11.Sessions;
import com.example.learning.casestudy.domain.group2.case12.CacheKey;
import com.example.learning.casestudy.domain.group2.case13.Item;
import com.example.learning.casestudy.domain.group2.case13.Order;
import com.example.learning.casestudy.domain.group2.case14.Payment14;
import com.example.learning.casestudy.domain.group2.case2.CustomerId;
import com.example.learning.casestudy.domain.group2.case2.Phone;
import com.example.learning.casestudy.domain.group2.case3.BadChild;
import com.example.learning.casestudy.domain.group2.case3.GoodChild;
import com.example.learning.casestudy.domain.group2.case5.AppConfig;
import com.example.learning.casestudy.domain.group2.case6.Config;
import com.example.learning.casestudy.domain.group2.case8.TransferRequest;
import com.example.learning.casestudy.domain.group2.case9.Settings;
import com.example.learning.casestudy.dto.group2.case14.PaymentDto;
import com.example.learning.casestudy.model.group2.case1.BadPayment;
import com.example.learning.casestudy.model.group2.case10.BadCatalog;
import com.example.learning.casestudy.model.group2.case10.Catalog;
import com.example.learning.casestudy.model.group2.case10.Product;
import com.example.learning.casestudy.model.group2.case11.CachedPrincipal;
import com.example.learning.casestudy.model.group2.case12.MutableKey;
import com.example.learning.casestudy.model.group2.case14.BrokenPayment;
import com.example.learning.casestudy.model.group2.case2.Customer2;
import com.example.learning.casestudy.model.group2.case2.LegacyUser;
import com.example.learning.casestudy.model.group2.case6.BrokenConfig;
import com.example.learning.casestudy.model.group2.case7.Customer7;
import com.example.learning.casestudy.model.group2.case7.Legacy;
import com.example.learning.casestudy.model.group2.case8.BadRequest;
import com.example.learning.casestudy.model.group2.case9.MutableSettings;
import com.example.learning.casestudy.runtime.CaseStudyJvmIsolation;
import com.example.learning.casestudy.utils.group2.case1.Case1Utils;
import com.example.learning.casestudy.utils.group2.case10.Case10Utils;
import com.example.learning.casestudy.utils.group2.case11.Case11Utils;
import com.example.learning.casestudy.utils.group2.case12.Case12Utils;
import com.example.learning.casestudy.utils.group2.case13.Case13Utils;
import com.example.learning.casestudy.utils.group2.case14.Case14Utils;
import com.example.learning.casestudy.utils.group2.case2.Case2Utils;
import com.example.learning.casestudy.utils.group2.case3.Case3Utils;
import com.example.learning.casestudy.utils.group2.case4.Case4Utils;
import com.example.learning.casestudy.utils.group2.case5.Case5Utils;
import com.example.learning.casestudy.utils.group2.case6.Case6Utils;
import com.example.learning.casestudy.utils.group2.case7.Case7Utils;
import com.example.learning.casestudy.utils.group2.case8.Case8Utils;
import com.example.learning.casestudy.utils.group2.case9.Case9Utils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service("caseStudyGroup2Service")
public class Group2Service {
    public void executeCase1() {
        BadPayment bad = new BadPayment(-500);
        Case1Utils.check(bad.amount() < 0);
        // Phản ví dụ: dữ liệu sai vẫn được tạo.
        Case1Utils.reject(() -> new Money(-500));
        Case1Utils.reject(() -> new Money(0));
        Case1Utils.reject(() -> new Payment1("", new Money(100)));
        Payment1 valid = new Payment1("TX-1", new Money(500));
        Case1Utils.check(valid.money().minorUnits() == 500);
        System.out.println("PASS: invalid state rejected at construction");
    }

    public void executeCase2() {
        LegacyUser bad = new LegacyUser("091234", "Lan", "C-42",
        "HN", "Q1", "Street", "1", "VN", "x@y", "L", "vi", "");
        Case2Utils.check(bad.id().startsWith("09"));
        // Nhầm vị trí nhưng Java chấp nhận.
        try {
            new Builder2().phone(new Phone("091234")).build();
            throw new AssertionError("missing id accepted");
        } catch (IllegalStateException expected) {
        }
        Customer2 ok = new Builder2().id(new CustomerId("C-42"))
            .phone(new Phone("091234")).build();
        Case2Utils.check(ok.id().value().equals("C-42"));
        // new Builder().id(new Phone("091234")); // Không biên dịch.
        System.out.println("PASS: typed fields and required Builder state");
    }

    public void executeCase3() {
        BadChild broken = new BadChild();
        Case3Utils.check(broken.observed == null);
        Case3Utils.check("ready".equals(broken.token()));
        // Sau constructor mới sẵn sàng.
        GoodChild safe = new GoodChild("A");
        Case3Utils.check("A:ready".equals(safe.tokenAfterConstruction()));
        System.out.println("PASS: no virtual dispatch from base constructor");
    }

    public void executeCase4() {
        FakeSocket leaked = new FakeSocket();
        Case4Utils.reject(() -> Case4Utils.broken(leaked, false));
        Case4Utils.check(!leaked.closed);
        // Phản ví dụ: leak được mô phỏng.
        leaked.close();
        // Dọn tài nguyên mô phỏng sau kiểm chứng.
        FakeSocket failed = new FakeSocket();
        Case4Utils.reject(() -> Case4Utils.safe(failed, false));
        Case4Utils.check(failed.closed);
        FakeSocket success = new FakeSocket();
        try (Client client = Case4Utils.safe(success, true)) {
            Case4Utils.check(!success.closed);
            // Client còn sở hữu socket.
        }
        Case4Utils.check(success.closed);
        // Đóng khi hết vòng đời Client.
        System.out.println("PASS: failure and success both close owned resources");
    }

    public void executeCase5() {
        if (!CaseStudyJvmIsolation.isChildProcess()) {
            CaseStudyJvmIsolation.execute(Group2Service.class, "executeCase5");
            return;
        }
        System.clearProperty("demo.case5.url");
        try {
            Case5Utils.url();
            throw new AssertionError("static initializer unexpectedly succeeded");
        } catch (ExceptionInInitializerError expected) {
            Case5Utils.check(expected.getCause() instanceof IllegalStateException);
        }
        try {
            Case5Utils.load(Map.of());
            throw new AssertionError("missing config accepted");
        } catch (IllegalArgumentException expected) {
            Case5Utils.check(expected.getMessage().contains("app.url"));
        }
        AppConfig ok = Case5Utils.load(Map.of("APP_URL", "https://example.test"));
        Case5Utils.check(ok.url().equals("https://example.test"));
        System.out.println("PASS: explicit bootstrap validation");
    }

    public void executeCase6() {
        ConcurrentHashMap<String,BrokenConfig> broken = new ConcurrentHashMap<>();
        BrokenConfig incomplete = new BrokenConfig();
        broken.put("api", incomplete);
        Case6Utils.check(broken.get("api").endpoint == null);
        // Đọc giữa hai bước.
        incomplete.endpoint = "https://service.test";
        ConcurrentHashMap<String,Config> safe = new ConcurrentHashMap<>();
        Config ready = new Config("https://service.test");
        safe.put("api", ready);
        // Publish sau constructor thành công.
        Case6Utils.check(safe.get("api").endpoint().equals("https://service.test"));
        try {
            new Config("");
            throw new AssertionError("invalid config accepted");
        } catch (IllegalArgumentException expected) {
        }
        Case6Utils.check(safe.size() == 1);
        System.out.println("PASS: cache contains only validated immutable values");
    }

    public void executeCase7() {
        // new Legacy("C-1", null); // Compile error: ambiguous constructors.
        Legacy label = new Legacy("C-1", (String)null);
        Legacy rank = new Legacy("C-1", (Integer)null);
        Case7Utils.check(label.mode.equals("label") && rank.mode.equals("rank"));
        Customer7 a = Case7Utils.withLabel("C-1", null);
        Customer7 b = Case7Utils.withRank("C-1", null);
        Case7Utils.check(a.rank() == null && b.label() == null);
        System.out.println("PASS: named factories remove constructor ambiguity");
    }

    public void executeCase8() {
        BadRequest bad = new BadRequest(null, 0);
        Case8Utils.check(bad.destination() == null && bad.amount() == 0);
        Case8Utils.reject(() -> new Builder8().amount(100).build());
        Case8Utils.reject(() -> new Builder8().destination("ACC-1").build());
        TransferRequest ok = new Builder8().destination("ACC-1")
            .amount(100).build();
        Case8Utils.check(ok.destination().equals("ACC-1") && ok.amount() == 100);
        try {
            new TransferRequest(null, -1);
            throw new AssertionError("Invalid direct construction succeeded");
        } catch (IllegalArgumentException expected) {
        }
        System.out.println("PASS: missing data rejected before external call");
    }

    public void executeCase9() {
        if (!CaseStudyJvmIsolation.isChildProcess()) {
            CaseStudyJvmIsolation.execute(Group2Service.class, "executeCase9");
            return;
        }
        MutableSettings userA = Case9Utils.settings();
        MutableSettings userB = Case9Utils.settings();
        userA.theme = "dark";
        userB.theme = "orange";
        Case9Utils.check(userA == userB && userA.theme.equals("orange"));
        // Leak.
        Settings a = Case9Utils.forUser("dark");
        Settings b = Case9Utils.forUser("orange");
        Case9Utils.check(a.theme().equals("dark"));
        Case9Utils.check(b.theme().equals("orange"));
        Case9Utils.check(a != b);
        System.out.println("PASS: immutable per-user settings independent");
    }

    public void executeCase10() {
        Product item = new Product("SKU-1", 100);
        List<Product> source = new ArrayList<>(List.of(item));
        BadCatalog bad = new BadCatalog(source);
        Catalog safe = new Catalog(source);
        item.price = 999;
        source.clear();
        Case10Utils.check(bad.products.get(0).price == 999);
        // Alias bị lộ.
        Case10Utils.check(safe.products().get(0).price() == 100);
        Case10Utils.check(safe.products().size() == 1);
        try {
            safe.products().clear();
            throw new AssertionError("mutable snapshot");
        } catch (UnsupportedOperationException expected) {
        }
        System.out.println("PASS: copied list and immutable elements");
    }

    public void executeCase11() {
        Instant now = Instant.parse("2026-10-28T00:00:00Z");
        CachedPrincipal cached = new CachedPrincipal("alice");
        Sessions service = new Sessions();
        service.create("demo-token", "alice", now.plusSeconds(60));
        Case11Utils.check(service.authorize("demo-token", now).equals("alice"));
        service.logout("demo-token");
        Case11Utils.check(cached.userId().equals("alice"));
        // Stale principal còn tồn tại.
        Case11Utils.deny(() -> service.authorize("demo-token", now));
        service.create("token-2", "alice", now.plusSeconds(60));
        Case11Utils.deny(() -> service.authorize("token-2", now.plusSeconds(61)));
        System.out.println("PASS: revoked and expired sessions rejected");
    }

    public void executeCase12() {
        Map<MutableKey,String> broken = new HashMap<>();
        MutableKey key = new MutableKey("A");
        broken.put(key, "entry");
        key.id = "B";
        Case12Utils.check(broken.get(key) == null);
        // Hash bucket khác.
        Case12Utils.check(broken.size() == 1);
        // Entry không tự biến mất.
        Map<CacheKey,String> safe = new HashMap<>();
        CacheKey oldKey = new CacheKey("A");
        safe.put(oldKey, "entry");
        Case12Utils.check("entry".equals(safe.get(new CacheKey("A"))));
        String value = safe.remove(oldKey);
        safe.put(new CacheKey("B"), value);
        Case12Utils.check(!safe.containsKey(oldKey));
        Case12Utils.check("entry".equals(safe.get(new CacheKey("B"))));
        System.out.println("PASS: immutable hash key and deliberate remap");
    }

    public void executeCase13() {
        Order original = new Order(new ArrayList<>(List.of(new Item("A", 1))));
        Order shallow = original.shallowCopy();
        Order deep = original.deepCopy();
        Case13Utils.check(original.items != shallow.items);
        // List khác.
        Case13Utils.check(original.items.get(0) == shallow.items.get(0));
        // Item chung.
        shallow.items.get(0).quantity = 5;
        Case13Utils.check(original.items.get(0).quantity == 5);
        // Bị ảnh hưởng.
        Case13Utils.check(deep.items.get(0).quantity == 1);
        // Bản copy sâu giữ nguyên.
        deep.items.get(0).quantity = 9;
        Case13Utils.check(original.items.get(0).quantity == 5);
        System.out.println("PASS: graph-owned items independently copied");
    }

    public void executeCase14() {
        BrokenPayment bad = new BrokenPayment();
        bad.reference = "TX";
        bad.amount = -1;
        // Nhập liệu trực tiếp bypass validation.
        Case14Utils.check(bad.amount < 0);
        PaymentDto fromUntrustedInput = new PaymentDto("TX", -1);
        Case14Utils.reject(() -> Case14Utils.fromDto(fromUntrustedInput));
        Case14Utils.reject(() -> Case14Utils.fromDto(new PaymentDto("", 20)));
        Payment14 ok = Case14Utils.fromDto(new PaymentDto("TX", 20));
        Case14Utils.check(ok.amountMinor() == 20 && ok.reference().equals("TX"));
        System.out.println("PASS: DTO-to-domain boundary enforces invariant");
    }
}
