<a id="back-to-top"></a>

# Kiểm thử container

## Menu
- [Kiểm thử lớp nghiệp vụ bằng đối tượng giả mà không mở context](#container-testing-001)
- [Kiểm thử lắp ghép bằng AnnotationConfigApplicationContext tối thiểu](#container-testing-002)
- [Spring TestContext với @SpringJUnitConfig và @ContextConfiguration](#container-testing-003)
- [Kiểm chứng tên, kiểu và ứng viên được tiêm](#container-testing-004)
- [Kiểm chứng số thể hiện singleton, prototype và provider](#container-testing-005)
- [Kiểm chứng thứ tự callback khởi tạo, hủy và đóng context](#container-testing-006)
- [Đổi profile, nguồn thuộc tính và kiểm tra chuyển đổi giá trị](#container-testing-007)
- [Kiểm chứng phát sự kiện và nhận thông điệp theo Locale](#container-testing-008)
- [Cô lập trạng thái, bộ nhớ đệm context và khi nào cần @DirtiesContext](#container-testing-009)

## <a id="container-testing-001">Kiểm thử lớp nghiệp vụ bằng đối tượng giả mà không mở context</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử lớp nghiệp vụ bằng đối tượng giả mà không mở Spring context

## 1. Mục đích và phạm vi

Một service được đăng ký với Spring qua `@Service`, `@Component` hoặc `@Bean` vẫn có thể là một **đối tượng Java thông thường**. Nếu business logic của service sử dụng constructor injection với những dependency rõ ràng, ta có thể kiểm thử logic bằng `new Service(fakeDependencies...)`, **không cần khởi động `ApplicationContext`**.

Đó là lợi ích quan trọng của IoC và Dependency Injection: business code ít phụ thuộc vào cách object được tạo hoặc được lắp ghép. Spring Reference về Unit Testing khuyến nghị kiểm thử POJO với `new`, kết hợp stub/mock của DAO/repository để cô lập logic và tránh phải mở container.

Ví dụ định hướng:

```java
public final class CheckoutService {
    private final PricePort prices;
    private final OrderPort orders;

    public CheckoutService(PricePort prices, OrderPort orders) {
        this.prices = prices;
        this.orders = orders;
    }

    public void checkout(String sku) {
        // Business rules được thực thi qua hai cổng phụ thuộc.
    }
}
```

Đây là **class fragment**, cần các interface `PricePort`/`OrderPort` và phần triển khai method. Unit test có thể tự tạo một `FakePricePort` và `FakeOrderPort` rồi truyền qua constructor. Không cần Spring quét class, không cần `@ExtendWith(SpringExtension.class)`, không cần `@SpringBootTest`.

**Kết luận trọng tâm:** container chịu trách nhiệm *composition/wiring/lifecycle*, còn service chịu trách nhiệm *business behavior*. Unit test không mở context kiểm business behavior; test tích hợp mở context để kiểm việc Spring lắp ghép và những chức năng do proxy/infrastructure mang lại.

### Kết quả kỳ vọng sau bài học

Bạn sẽ:
- Phân biệt unit test không context, unit test với mock framework và Spring integration test.
- Thiết kế service theo ports/interfaces và constructor injection để dễ thay dependency.
- Viết fake/stub/spy tự tạo bằng Java, không gọi API ngoài và không dùng DB thật.
- Kiểm output, state, số lượt gọi, thứ tự tác dụng phụ, exception paths và tính độc lập của mỗi test.
- Kiểm `Clock`, ID generator, thời gian và dữ liệu ngẫu nhiên một cách deterministic.
- Hiểu khi nào Mockito hữu ích và khi nào fake thuần Java dễ đọc hơn.
- Không nhầm tests business logic với việc đã test `@Transactional`, `@Cacheable`, `@Async`, method security, BeanPostProcessor hoặc `@Configuration`.
- Biết cách bổ sung test context đúng tầng khi logic phụ thuộc thật vào Spring container.

## 2. Vì sao “không mở context” lại là mục tiêu có ích?

Một full-context test có thể cần:
- Tải `@Configuration`, chạy `@ComponentScan`, tìm `@Bean`.
- Resolve dependencies, lifecycle callbacks và BeanPostProcessors.
- Khởi tạo datasource, connection pool, HTTP client, message listeners hoặc caches.
- Xử lý properties, profiles, conditional beans và proxies.
- Dùng Spring TestContext caching nếu được thiết lập và key cấu hình phù hợp.

Điều này **cần thiết** khi yêu cầu của test là xác minh chính Spring wiring hoặc infrastructure. Nhưng nếu chỉ muốn kiểm “số lượng âm phải bị từ chối”, mở cả ứng dụng làm test chậm hơn, khó chẩn đoán hơn và thêm khả năng lỗi vì hệ thống ngoài.

Với pure unit test:

```text
Test
  |-- new FakePricePort()
  |-- new FakeOrderPort()
  |-- new RecordingReceiptPort()
  |-- Clock.fixed(...)
  +-- new OrderService(fakePrice, fakeOrders, fakeReceipts, clock, fixedIds)
                 |
                 +-- chạy business logic thuần Java
```

Không có node `ApplicationContext`, bean registry, DI scanner, context cache, SpringExtension hay container refresh ở đây. Các objects do chính test tạo bằng `new`.

### Không mở context không có nghĩa không dùng JUnit

JUnit 5/JUnit Jupiter là **test runner và assertion framework**; nó không yêu cầu Spring ApplicationContext. Mockito cũng là mocking library có thể chạy không cần context. Điều quyết định mở context là **Spring TestContext extension hoặc thao tác tạo context** (chẳng hạn `@SpringBootTest`, `@SpringJUnitConfig`, `@ExtendWith(SpringExtension.class)` với config phù hợp), không phải sự xuất hiện của `@Test`.

Một `@Test` thông thường có thể chỉ làm:

```java
@Test
void rejectsNegativeQuantity() {
    OrderService sut = new OrderService(fakePrices, fakeStore,
            fakeReceipts, fixedClock, fixedIds);
    assertThrows(IllegalArgumentException.class,
            () -> sut.place("BOOK", -1));
}
```

Đây là **JUnit method fragment**, các dependencies/ports cần khai trong project; không có annotation Spring nào để khởi động context.

## 3. Phân biệt SUT, collaborator, fake, stub, mock và spy

Trong test, **SUT** (*System Under Test*) là object chứa logic cần kiểm, ví dụ `OrderService`. Mọi dependency bên ngoài SUT được gọi là collaborator. Test double là đối tượng thay collaborator thật trong test.

| Thuật ngữ | Mục đích | Dạng thường gặp |
| --- | --- | --- |
| Dummy | Chỉ điền tham số bắt buộc nhưng test path không sử dụng | Object/implementation tối giản |
| Stub | Trả dữ liệu đã kiểm soát để dẫn SUT tới một nhánh logic | `FakePricePort` trả giá cố định |
| Fake | Implementation đơn giản nhưng có hành vi, thường ở RAM | `InMemoryOrderStore` lưu orders |
| Spy (thủ công) | Ghi nhận lệnh gọi/arguments để test kiểm sau | `RecordingReceiptPort.sent` |
| Mock (behavior verification) | Kiểm invocation/interaction contract, có thể dùng Mockito | `verify(port).send(order)` |
| Real implementation | Dependency thật; thích hợp test tích hợp | JDBC repository, SMTP adapter |

**Không cần cưỡng ép một class vào đúng một nhãn.** Một fake store có thể vừa lưu data vừa đếm method calls; nó hoạt động như fake lẫn spy. Điều quan trọng là **tại sao test dùng nó và điều gì được kiểm**.

### Fake không nhất thiết là Mockito mock

Một implementation bằng tay:

```java
final class FakePricePort implements PricePort {
    @Override
    public Optional<BigDecimal> findPrice(String sku) {
        return Optional.of(new BigDecimal("19.90"));
    }
}
```

là một stub/fake không phụ thuộc thư viện mocking. Trong nhiều tình huống nghiệp vụ, nó dễ đọc hơn một chuỗi `when(...).thenReturn(...)` dài.

Ngược lại Mockito hữu ích khi:
- Interface có nhiều methods mà test chỉ dùng một.
- Cần kiểm chính xác lượt gọi với arguments.
- Cần mô phỏng exception path nhanh.
- Team đã thống nhất Mockito và không muốn xây bộ fake có trạng thái ngày càng phức tạp.

### Tránh over-mocking

Một test mock mọi collaborator và kiểm đúng từng lời gọi implementation-private có thể vỡ khi refactor dù outcome nghiệp vụ vẫn đúng. Ưu tiên kiểm:
1. **Kết quả trả về** và các business invariants.
2. **State/change** quan sát được qua port.
3. **Tương tác thật sự là contract**, ví dụ “không gửi biên nhận khi lưu đơn thất bại”.

Không nên verify một getter được gọi đúng ba lần trừ khi đó thật sự là yêu cầu performance hoặc protocol.

## 4. Constructor injection khiến kiểm thử dễ dàng

Thiết kế thuận test:

```java
public final class OrderService {
    private final PricePort prices;
    private final OrderPort orders;
    private final ReceiptPort receipts;

    public OrderService(PricePort prices, OrderPort orders,
                        ReceiptPort receipts) {
        this.prices = Objects.requireNonNull(prices);
        this.orders = Objects.requireNonNull(orders);
        this.receipts = Objects.requireNonNull(receipts);
    }
}
```

Đây là **class fragment**. Mọi dependency bắt buộc lộ ra trên constructor. Test có thể truyền fake implementations; production có thể truyền Spring beans thông qua `@Bean`, `@Service` hoặc configuration khác mà **không đổi logic SUT**.

So với field injection:

```java
@Service
public class OrderService {
    @Autowired
    private OrderRepository repository;
}
```

Đây là fragment và là một phản ví dụ về khả năng test thuần Java: `new OrderService()` để `repository == null` nếu không có Spring inject. Bạn có thể dùng reflection để set field nhưng test trở nên phụ thuộc private implementation details và làm class có khả năng tồn tại ở trạng thái không hợp lệ. **Ưu tiên constructor injection** khi dependencies là bắt buộc.

### Production có cần annotate constructor không?

Trong Spring Framework, một bean có duy nhất constructor phù hợp thường có thể được constructor-autowired mà không cần `@Autowired`. Do đó core logic có thể hoàn toàn không import Spring:

```java
public OrderService(PricePort prices, OrderPort orders,
                    ReceiptPort receipts) { /* ... */ }
```

Fragment. Nếu class được khai `@Service`, test vẫn có thể `new` class trực tiếp; annotation trên type thường không tự khởi động Spring khi bạn sử dụng Java constructor.

### Ports và adapters

```text
                  business layer
                  +-----------+
                  | OrderSvc  |
                  +--+-----+--+
                     |     |
             PricePort     OrderPort
                 ^             ^
                 |             |
           Production API   JDBC Adapter
                 ^
             Test Stub
                           Test FakeStore
```

Business service phụ thuộc vào **interfaces do domain/application layer định nghĩa**; implementation cụ thể là chi tiết của production/test adapter. Điều này giúp unit test không phải giả lập toàn bộ JDBC hay HTTP protocol để kiểm business rule.

## 5. Nguồn không xác định: thời gian, UUID, random, môi trường

Một test khó lặp thường do business code gọi trực tiếp:
- `Instant.now()` hoặc `LocalDateTime.now()`.
- `UUID.randomUUID()`.
- `new Random()` với seed thay đổi.
- `System.getenv(...)` hoặc `System.getProperty(...)`.
- Network/DB/timezone/locale thật.
- Static global caches và singletons dùng chung state.

Trong Java 17, tiêm `java.time.Clock` là cách có sẵn để kiểm thời gian:

```java
Clock clock = Clock.fixed(
        Instant.parse("2026-01-02T03:04:05Z"),
        ZoneOffset.UTC);

Instant observed = clock.instant();
```

Dùng `Supplier<String>` làm cổng sinh ID:

```java
Supplier<String> idSupplier = () -> "ORD-001";
```

Đây đều là Java fragments cần imports. Production có thể truyền `Clock.systemUTC()` và `() -> UUID.randomUUID().toString()`, trong khi test truyền fixed instances. Nhờ vậy assertions kiểm đúng timestamp và ID, không phụ thuộc thời điểm test chạy.

### Timezone và BigDecimal

- Dùng `Instant`/`Clock` cho sự kiện tuyệt đối khi phù hợp; timezone dùng để biểu diễn local time.
- Dùng `BigDecimal(String)` cho giá tiền, tránh `new BigDecimal(19.90)` từ binary floating-point.
- `BigDecimal.equals` xét cả scale; `19.9` và `19.90` khác nhau theo equals nhưng `compareTo` bằng nhau.
- Trong ví dụ, giá `"19.90"` nhân số lượng nguyên giữ scale 2, nên `new BigDecimal("39.80")` có thể `equals` đúng.

Không ép mọi phép so sánh tiền dùng double + tolerance khi domain thật đòi decimal arithmetic.

## 6. Quyết định ranh giới một bài unit test

Một unit test tốt phải trả lời **một hành vi cụ thể**, không cần kiểm toàn bộ hệ thống.

Ví dụ requirements:
1. SKU hợp lệ, quantity dương, có giá → tính tiền đúng, lưu một order, phát một receipt request.
2. Quantity ≤ 0 → ném `IllegalArgumentException`, không gọi các ports.
3. SKU trống → fail fast, không gọi ports.
4. SKU không tìm thấy → ném domain exception, không lưu/gửi.
5. Giá âm → từ chối dữ liệu hỏng, không lưu/gửi.
6. Repository lưu lỗi → propagate failure, không gọi receipt sender.
7. Sender lỗi sau save → xác định rõ state đã lưu; **unit test không tạo giao dịch** và không giả vờ rollback.
8. Mỗi test dùng fixtures mới → không leak state giữa các tests.

Đây là những **observable behaviors**. Test có thể kiểm thứ tự bằng cách xác định sender chỉ được gọi nếu save hoàn tất, chứ không cần verify mọi local calculation.

### Test method nên độc lập

Mỗi method test nên tự tạo `Fixture` hoặc setup qua `@BeforeEach`; không nên có một static in-memory store dùng chung mà test A để lại rows cho test B. Shared mutable test state dẫn đến flaky tests khi test runner đổi thứ tự hoặc chạy parallel.

## 7. Ví dụ Java 17+ hoàn chỉnh, không cần Spring và không cần thư viện test

Ví dụ này chạy bằng **JDK 17+** và lệnh `javac`/`java`. Nó không import Spring, JUnit, Mockito hoặc kết nối database. Để người học có thể thử ngay mà không cần Maven download dependencies, tất cả code nằm trong **một file** `src/Main.java`.

Mã gồm:
- `OrderService`: SUT có business rules và constructor injection.
- `PricePort`, `OrderPort`, `ReceiptPort`: interfaces cho production/test adapters.
- `FakePrices`: stub giá trong memory, đếm lookups.
- `FakeStore`: repository fake có thể chủ động fail trước save.
- `FakeReceipts`: spy/fake ghi nhận sends hoặc fail trước khi ghi nhận.
- `Clock.fixed` và ID supplier: tránh time/random nondeterminism.
- Tám tests với assertion helpers tự viết, có output **chính xác**.
- `Main.main` chỉ test, không mở IoC container.

### 7.1. Cấu trúc thư mục

```text
plain-business-unit-tests/
└── src/
    └── Main.java
```

### 7.2. Mã đầy đủ — src/Main.java

```java
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class Main {

    public record Order(
            String id,
            String sku,
            int quantity,
            BigDecimal total,
            Instant createdAt) {
    }

    public interface PricePort {
        Optional<BigDecimal> findPrice(String sku);
    }

    public interface OrderPort {
        void save(Order order);
    }

    public interface ReceiptPort {
        void send(Order order);
    }

    public static final class UnknownSkuException
            extends RuntimeException {
        public UnknownSkuException(String sku) {
            super("Unknown SKU: " + sku);
        }
    }

    public static final class OrderService {
        private final PricePort prices;
        private final OrderPort orders;
        private final ReceiptPort receipts;
        private final Clock clock;
        private final Supplier<String> ids;

        public OrderService(
                PricePort prices,
                OrderPort orders,
                ReceiptPort receipts,
                Clock clock,
                Supplier<String> ids) {
            this.prices = Objects.requireNonNull(prices);
            this.orders = Objects.requireNonNull(orders);
            this.receipts = Objects.requireNonNull(receipts);
            this.clock = Objects.requireNonNull(clock);
            this.ids = Objects.requireNonNull(ids);
        }

        public Order place(String sku, int quantity) {
            if (sku == null || sku.isBlank()) {
                throw new IllegalArgumentException(
                        "SKU must not be blank");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException(
                        "Quantity must be positive");
            }

            BigDecimal unitPrice = prices.findPrice(sku)
                    .orElseThrow(() -> new UnknownSkuException(sku));
            if (unitPrice.signum() < 0) {
                throw new IllegalStateException(
                        "Price cannot be negative");
            }

            Order order = new Order(
                    ids.get(),
                    sku,
                    quantity,
                    unitPrice.multiply(BigDecimal.valueOf(quantity)),
                    clock.instant());

            orders.save(order);
            receipts.send(order);
            return order;
        }
    }

    public static final class FakePrices implements PricePort {
        final Map<String, BigDecimal> prices = new HashMap<>();
        int lookups;

        @Override
        public Optional<BigDecimal> findPrice(String sku) {
            lookups++;
            return Optional.ofNullable(prices.get(sku));
        }
    }

    public static final class FakeStore implements OrderPort {
        final List<Order> saved = new ArrayList<>();
        int saveAttempts;
        boolean fail;

        @Override
        public void save(Order order) {
            saveAttempts++;
            if (fail) {
                throw new IllegalStateException("Store unavailable");
            }
            saved.add(order);
        }
    }

    public static final class FakeReceipts implements ReceiptPort {
        final List<Order> sent = new ArrayList<>();
        int sendAttempts;
        boolean fail;

        @Override
        public void send(Order order) {
            sendAttempts++;
            if (fail) {
                throw new IllegalStateException("Sender unavailable");
            }
            sent.add(order);
        }
    }

    public static final class Fixture {
        final FakePrices prices = new FakePrices();
        final FakeStore orders = new FakeStore();
        final FakeReceipts receipts = new FakeReceipts();
        final Clock fixedClock = Clock.fixed(
                Instant.parse("2026-01-02T03:04:05Z"),
                ZoneOffset.UTC);

        final OrderService sut = new OrderService(
                prices, orders, receipts,
                fixedClock, () -> "ORD-001");

        Fixture() {
            prices.prices.put("BOOK", new BigDecimal("19.90"));
        }
    }

    private static void check(boolean condition, String explanation) {
        if (!condition) {
            throw new AssertionError(explanation);
        }
    }

    private static <T extends Throwable> T expectThrows(
            Class<T> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable actual) {
            if (type.isInstance(actual)) {
                return type.cast(actual);
            }
            throw new AssertionError(
                    "Expected " + type.getSimpleName()
                    + " but caught " + actual.getClass().getSimpleName(),
                    actual);
        }
        throw new AssertionError(
                "Expected " + type.getSimpleName()
                + " but nothing was thrown");
    }

    private static void testSuccess() {
        Fixture f = new Fixture();
        Order order = f.sut.place("BOOK", 2);

        check(order.id().equals("ORD-001"), "fixed order id");
        check(order.sku().equals("BOOK"), "SKU");
        check(order.quantity() == 2, "quantity");
        check(order.total().equals(new BigDecimal("39.80")),
                "total 39.80");
        check(order.createdAt().equals(
                Instant.parse("2026-01-02T03:04:05Z")),
                "fixed instant");
        check(f.prices.lookups == 1, "one price lookup");
        check(f.orders.saveAttempts == 1, "one save attempt");
        check(f.orders.saved.equals(List.of(order)),
                "saved correct order");
        check(f.receipts.sendAttempts == 1,
                "one send attempt");
        check(f.receipts.sent.equals(List.of(order)),
                "receipt for same order");
    }

    private static void testInvalidQuantity() {
        Fixture f = new Fixture();
        expectThrows(IllegalArgumentException.class,
                () -> f.sut.place("BOOK", 0));
        check(f.prices.lookups == 0, "do not query prices");
        check(f.orders.saveAttempts == 0, "do not save");
        check(f.receipts.sendAttempts == 0, "do not send");
    }

    private static void testBlankSku() {
        Fixture f = new Fixture();
        expectThrows(IllegalArgumentException.class,
                () -> f.sut.place("  ", 1));
        check(f.prices.lookups == 0, "no lookup for blank SKU");
        check(f.orders.saved.isEmpty(), "no saved orders");
        check(f.receipts.sent.isEmpty(), "no receipts");
    }

    private static void testUnknownSku() {
        Fixture f = new Fixture();
        UnknownSkuException error =
                expectThrows(UnknownSkuException.class,
                        () -> f.sut.place("MISSING", 1));
        check(error.getMessage().equals("Unknown SKU: MISSING"),
                "meaningful domain error");
        check(f.prices.lookups == 1, "one attempted lookup");
        check(f.orders.saveAttempts == 0, "no order save");
        check(f.receipts.sendAttempts == 0, "no receipt");
    }

    private static void testNegativePrice() {
        Fixture f = new Fixture();
        f.prices.prices.put("BOOK", new BigDecimal("-1.00"));
        expectThrows(IllegalStateException.class,
                () -> f.sut.place("BOOK", 1));
        check(f.orders.saveAttempts == 0, "no invalid save");
        check(f.receipts.sendAttempts == 0, "no invalid send");
    }

    private static void testStoreFailure() {
        Fixture f = new Fixture();
        f.orders.fail = true;
        IllegalStateException error =
                expectThrows(IllegalStateException.class,
                        () -> f.sut.place("BOOK", 1));
        check(error.getMessage().equals("Store unavailable"),
                "storage error propagated");
        check(f.orders.saveAttempts == 1, "attempted save");
        check(f.orders.saved.isEmpty(), "failure before save");
        check(f.receipts.sendAttempts == 0, "no receipt on failure");
    }

    private static void testReceiptFailure() {
        Fixture f = new Fixture();
        f.receipts.fail = true;
        IllegalStateException error =
                expectThrows(IllegalStateException.class,
                        () -> f.sut.place("BOOK", 1));
        check(error.getMessage().equals("Sender unavailable"),
                "sender error propagated");
        check(f.orders.saved.size() == 1,
                "order remains in in-memory fake");
        check(f.receipts.sendAttempts == 1, "send attempted");
        check(f.receipts.sent.isEmpty(), "not recorded as sent");
    }

    private static void testIndependentFixtures() {
        Fixture first = new Fixture();
        Fixture second = new Fixture();

        first.sut.place("BOOK", 1);
        check(first.orders.saved.size() == 1, "first has order");
        check(second.orders.saved.isEmpty(), "second stays empty");
        check(second.receipts.sent.isEmpty(),
                "second had no side effect");
    }

    public static void main(String[] args) {
        testSuccess();
        System.out.println("PASS success");
        testInvalidQuantity();
        System.out.println("PASS invalid quantity");
        testBlankSku();
        System.out.println("PASS blank SKU");
        testUnknownSku();
        System.out.println("PASS unknown SKU");
        testNegativePrice();
        System.out.println("PASS negative price");
        testStoreFailure();
        System.out.println("PASS store failure");
        testReceiptFailure();
        System.out.println("PASS receipt failure");
        testIndependentFixtures();
        System.out.println("PASS isolated fixtures");
        System.out.println("RESULT 8/8");
    }
}
```

### 7.3. Biên dịch và chạy từ thư mục gốc

Linux/macOS:

```bash
mkdir -p out
javac -encoding UTF-8 -d out src/Main.java
java -cp out Main
```

Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force -Path out | Out-Null
javac -encoding UTF-8 -d out src/Main.java
java -cp out Main
```

Lệnh chỉ cần JDK 17+, không cần thêm JAR nào. **Các dòng stdout nếu tất cả assertions pass:**

```text
PASS success
PASS invalid quantity
PASS blank SKU
PASS unknown SKU
PASS negative price
PASS store failure
PASS receipt failure
PASS isolated fixtures
RESULT 8/8
```

Nếu một assertion hoặc exception expectation thất bại, `AssertionError` sẽ dừng chương trình; khi đó **không** xuất hiện `RESULT 8/8`. Cách viết helper `check` cố ý không dựa Java keyword `assert`, vì Java assertions mặc định có thể bị tắt nếu không truyền `-ea`.

### 7.4. Vì sao đây là unit test dù chỉ dùng main()?

Một runner `main()` không có báo cáo JUnit phong phú nhưng vẫn thực hiện các phép thử độc lập qua controlled collaborators. `Main` **không đăng ký bean nào**, không tạo Spring context, không truy cập production DB/network và không mock framework. Với CI thực, nên dùng JUnit 5 để runner nhận diện tests, tạo reports và chạy từng case riêng; mục 11 minh họa chuyển sang JUnit mà không thay ý tưởng thiết kế.

### 7.5. Những điều cố tình không được “chứng minh” bởi demo

- Không có real transaction/rollback.
- Không có `@Transactional`/`@Async`/`@Cacheable` interceptor.
- Không kiểm bean scanning, qualifiers, profiles hay constructor autowiring của Spring.
- Không kiểm Spring proxy, `@PostConstruct` hay lifecycle hooks.
- Không chứng minh dữ liệu thực sự được durable persisted.
- Không có thứ tự concurrency, thread-safety hay race simulation.
- Không chứng minh email đã được gửi: `FakeReceipts.sent` chỉ ghi lời gọi giả.

Đó **không phải thiếu sót của một test SUT đúng phạm vi**. Mỗi guarantee nằm ở tầng riêng và cần test tương ứng.

## 8. Đọc kỹ kịch bản thành công

`Fixture` khởi tạo một lần cho mỗi test:
- Giá BOOK = `19.90`.
- `Clock.fixed` trả đúng `2026-01-02T03:04:05Z`.
- ID supplier trả `ORD-001`.
- Fake store và fake receipt sender bắt đầu trống.

`place("BOOK",2)`:
1. SKU không blank, quantity dương.
2. `prices.findPrice("BOOK")` trả `19.90`.
3. Tổng tiền = `19.90 × 2 = 39.80` dưới dạng BigDecimal.
4. Tạo `Order("ORD-001","BOOK",2,39.80,fixedInstant)`.
5. `orders.save(order)` ghi **một** record vào fake store.
6. `receipts.send(order)` ghi **cùng order object/value** vào fake sender.
7. Method trả order.

Test kiểm cả giá trị và effects nhưng **không verify mọi helper getter call**. Nếu cách implementation thay đổi mà hợp đồng kết quả vẫn giữ, test vẫn pass.

### Tại sao List.of(order) phù hợp?

Java record `Order` có value-based `equals` trên các components. `f.orders.saved.equals(List.of(order))` kiểm vừa số lượng, vừa order value. Vì `BigDecimal.equals` xét scale, example có cố ý dùng literals nhất quán.

### Một giá trị fixed ID lặp cho các fixtures có sao không?

Mỗi test có fake store **mới**, nên `ORD-001` không va chạm giữa tests. Production phải dùng ID policy duy nhất phù hợp; unit test fixed ID chỉ phục vụ determinism. Nếu test cùng fixture đặt nhiều orders, cần supplier sinh một sequence deterministic thay vì luôn trả một ID.

## 9. Test lỗi: đừng chỉ assert có exception

Khi quantity không hợp lệ:

```java
expectThrows(IllegalArgumentException.class,
        () -> f.sut.place("BOOK", 0));
```

Fragment này nằm trong full demo. Assertion về exception **chưa đủ** nếu yêu cầu là không gây side effect. Do đó test còn kiểm:
- `prices.lookups == 0`.
- `orders.saveAttempts == 0`.
- `receipts.sendAttempts == 0`.

Nếu trong tương lai SUT bắt đầu gọi repository trước khi validate quantity, test sẽ fail vì vi phạm fail-fast contract. Đây là **interaction verification có ý nghĩa nghiệp vụ**, không phải over-specification.

### Unknown SKU

`FakePrices` trả `Optional.empty()` nếu không có key `MISSING`. SUT chuyển thành `UnknownSkuException`. Test kiểm chính xác domain error, một lần lookup, và không save/send.

### Repository failure

Fake store ném `IllegalStateException("Store unavailable")` **trước khi thêm vào list**. SUT không catch và không gọi receipt port. Test kiểm:
- Exception truyền về caller.
- Có đúng một lần attempt save.
- Fake store vẫn empty.
- Fake sender không bị gọi.

Đừng dựng fake lưu order rồi ném lỗi nhưng lại khẳng định “repository không lưu gì”; fail placement của fake phải nhất quán với scenario được mô phỏng.

### Sender failure là bài học về giới hạn unit test

Ở ví dụ, `OrderService` save trước rồi send. Nếu fake sender ném exception, fake store **đã lưu một order**. Đây là kết quả **đúng với code hiện tại**, không phải bug của test.

Trong production:
- Nếu `OrderPort` là JDBC repository có transaction thật, behavior rollback khi sender throw phụ thuộc `@Transactional` được intercept, transaction manager, exception policy và resource state.
- Nếu receipt được gửi qua dịch vụ ngoài, transaction DB không tự hoàn tác gửi email/HTTP side effect.
- Unit test fake không tạo ra transaction và không cam kết compensation.
- Nếu nghiệp vụ đòi reliable eventual notification, xem kiến trúc transactional outbox/queue và integration tests phù hợp.
- Nên đặc tả rõ “save succeeds but notification fails” được xử lý bằng retry, outbox, state machine hay trả lỗi theo contract.

**Không viết test fake rồi kết luận `@Transactional` rollback hoạt động.** Điều này cần test context/proxy và real transactional resource.

## 10. Phân biệt kiểm state và kiểm interaction

Hai trường phái thường được nhắc:
- **State-based testing:** kiểm state/output sau khi SUT chạy.
- **Interaction-based testing:** kiểm collaborator được gọi thế nào.

Ví dụ state:

```java
check(order.total().equals(new BigDecimal("39.80")),
        "expected total");
check(f.orders.saved.size() == 1, "one order saved");
```

Ví dụ interaction:

```java
check(f.receipts.sendAttempts == 1,
        "receipt port called once");
```

Đây là fragments từ full demo. Số lượt gọi rất quan trọng với một số effects (charge payment, gửi mail, audit record), nhưng ít quan trọng đối với pure getter hay calculator. Cố verify cả thứ tự tất cả private method calls làm test dễ gãy khi refactor.

### Test thứ tự bằng event recorder

Khi thứ tự thực sự là contract, có thể dùng một shared recorder:

```java
List<String> calls = new ArrayList<>();

OrderPort store = order -> calls.add("save");
ReceiptPort sender = order -> calls.add("send");

// Khi tạo OrderService với hai ports này và một price port phù hợp:
// sut.place("BOOK", 1);
// assert calls.equals(List.of("save", "send"));
```

Đoạn này là **test fragment**, cần PricePort/Clock/idSupplier và `sut`. Nó hữu ích khi “save trước send” là yêu cầu. Nếu không là invariant, đừng khiến tests phụ thuộc incidental implementation order.

### Call counts không luôn nói lên thành công

Trong full demo:
- `FakeStore.saveAttempts == 1` nhưng store có thể fail trước ghi.
- `FakeReceipts.sendAttempts == 1` nhưng sender có thể fail trước ghi nhận.
- `sent.size() == 0` không có nghĩa production email chắc chắn chưa được phía ngoài nhận; đó chỉ là semantics của fake.

Tên fields `attempts` và `sent` giúp tránh đồng nhất “đã gọi” với “đã hoàn tất tác dụng phụ”.

## 11. Chuyển sang JUnit 5 mà vẫn không mở context

JUnit Jupiter quản lý lifecycle tests và tạo reports, nhưng **không tự dựng Spring context**. Nếu muốn dùng Maven, bạn có thể **giữ `Main.java` từ mục 7**, chuyển nó vào `src/main/java/Main.java` rồi thêm file `src/test/java/OrderServiceJUnitTest.java`. Tất cả nằm trong unnamed/default package của ví dụ học tập; production nên tổ chức package riêng.

Cấu trúc:

```text
plain-business-unit-tests/
├── pom.xml
├── src/main/java/Main.java
└── src/test/java/OrderServiceJUnitTest.java
```

### 11.1. pom.xml cho Java 17 và JUnit Jupiter

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>edu.example</groupId>
    <artifactId>plain-business-unit-tests</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.11.4</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.2</version>
            </plugin>
        </plugins>
    </build>
</project>
```

Không cần `spring-context`, `spring-test`, Spring Boot hoặc Mockito để chạy bộ test này; JUnit 5 là dependency test-scoped.

### 11.2. JUnit tests dùng lại fakes từ Main.java

```java
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

final class OrderServiceJUnitTest {

    @Test
    void placesOrderWithFixedClockAndPrice() {
        Main.Fixture f = new Main.Fixture();

        Main.Order actual = f.sut.place("BOOK", 2);

        assertEquals(new BigDecimal("39.80"), actual.total());
        assertEquals("ORD-001", actual.id());
        assertEquals(1, f.orders.saved.size());
        assertEquals(1, f.receipts.sent.size());
    }

    @Test
    void invalidQuantityDoesNotTouchPorts() {
        Main.Fixture f = new Main.Fixture();

        assertThrows(IllegalArgumentException.class,
                () -> f.sut.place("BOOK", 0));

        assertEquals(0, f.prices.lookups);
        assertEquals(0, f.orders.saveAttempts);
        assertEquals(0, f.receipts.sendAttempts);
    }

    @Test
    void storeErrorPreventsReceiptCall() {
        Main.Fixture f = new Main.Fixture();
        f.orders.fail = true;

        assertThrows(IllegalStateException.class,
                () -> f.sut.place("BOOK", 1));

        assertTrue(f.orders.saved.isEmpty());
        assertEquals(0, f.receipts.sendAttempts);
    }

    @Test
    void senderErrorLeavesFakeStoreState() {
        Main.Fixture f = new Main.Fixture();
        f.receipts.fail = true;

        assertThrows(IllegalStateException.class,
                () -> f.sut.place("BOOK", 1));

        assertEquals(1, f.orders.saved.size());
        assertEquals(0, f.receipts.sent.size());
    }
}
```

Đây là **file JUnit class hoàn chỉnh** dùng các nested public types và package-private fixture fields trong `Main.java` cùng default package. Test runner JUnit Jupiter chạy bốn methods trên mà **không có SpringExtension**.

Lệnh:

```bash
mvn test
```

**Kỳ vọng ở Maven Surefire summary/report:** `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0` cho class này. Thứ tự tests và các dòng stdout/log build của Maven không phải output cố định, nên không cung cấp một block console verbatim. Báo cáo XML thường nằm trong `target/surefire-reports`.

### 11.3. Tại sao main() tests và JUnit tests cùng tồn tại?

Hai lựa chọn phục vụ mục đích khác nhau:
- `java -cp out Main`: không cần thư viện, in 8 PASS lines deterministic.
- `mvn test`: CI/IDE nhận diện test methods, báo cáo JUnit chuẩn, cho phép chạy riêng từng test, có assert methods phong phú.

Ở project thật, nên tách code service/ports trong `src/main/java` và test fakes/fixtures trong `src/test/java`; giữ cả tám plain tests trong Main chỉ phục vụ bản demo tối giản. Nếu chuyển hoàn toàn sang JUnit, hãy xóa test harness `main()` khỏi production artifact và viết các `@Test` tương ứng.

## 12. Mockito có bắt buộc không?

**Không.** Mockito là công cụ tạo test doubles; nó không phải tiền đề cho unit test tốt và không bắt buộc khi viết ứng dụng Spring.

Một ví dụ JUnit/Mockito:

```java
PricePort prices = mock(PricePort.class);
OrderPort orders = mock(OrderPort.class);
ReceiptPort receipts = mock(ReceiptPort.class);

when(prices.findPrice("BOOK"))
        .thenReturn(Optional.of(new BigDecimal("19.90")));

OrderService sut = new OrderService(
        prices, orders, receipts, fixedClock, () -> "ORD-001");

Order order = sut.place("BOOK", 2);

verify(orders).save(order);
verify(receipts).send(order);
verifyNoMoreInteractions(orders, receipts);
```

Đây là **fragment**: cần Mockito static imports (ví dụ `org.mockito.Mockito.*`), types/variables tương ứng, và Mockito dependency. `verifyNoMoreInteractions` có thể làm test giòn nếu sau này một phương thức harmless được thêm; chỉ dùng khi “không có thêm interaction” là một invariant.

### @Mock với MockitoExtension

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceMockitoTest {
    @Mock PricePort prices;
    @Mock OrderPort orders;
    @Mock ReceiptPort receipts;

    @Test
    void testOrder() {
        // new OrderService(prices, orders, receipts, clock, ids)
    }
}
```

Đây là **JUnit/Mockito fragment**, cần dependency `mockito-junit-jupiter`, imports và setup clock/ids. `MockitoExtension` là extension của Mockito, **không phải `SpringExtension`**; chạy nó không mở ApplicationContext.

### Mockito @InjectMocks không phải Spring autowiring test

`@InjectMocks` cố construct/inject fields theo quy tắc của Mockito, **không** chạy Spring bean postprocessors, `@Qualifier`/`@Primary` resolution, `@Configuration`, `@Value` conversion hay proxy. Khi mục tiêu là test business logic, explicit constructor `new SUT(...)` thường làm dependencies rõ hơn `@InjectMocks`.

### Khi nào fake thủ công tốt hơn?

- Port nhỏ, trạng thái đáng hiểu, ví dụ repository in-memory.
- Muốn mô tả nghiệp vụ bằng cấu trúc dữ liệu thay vì stubbing DSL.
- Cần mô phỏng state transitions phức tạp nhưng deterministic.
- Muốn test nhanh với không dependency ngoài.
- Cần object làm việc qua nhiều method theo business contract.

Khi fake phải bắt chước một database thật với transactions, isolation hoặc SQL semantics, đó có thể là **một fake giả tạo quá lớn**. Nên chuyển một phần kiểm thử sang integration test database thật thay vì mô phỏng toàn bộ RDBMS.

## 13. Không dùng @SpringBootTest chỉ để lấy một service

Một trường hợp thường gặp:

```java
@SpringBootTest
class PriceTest {
    @Autowired PriceService priceService;

    @Test
    void calculatesDiscount() {
        // Test một phép tính thuần nghiệp vụ.
    }
}
```

Đây là **fragment**. Nếu phép tính có thể thực thi khi tự tạo `PriceService(fakeDeps)`, mở full Boot context làm test phụ thuộc:
- Application configuration đầy đủ.
- Environment/profiles.
- Bean lifecycle của toàn app.
- DataSource/message consumers/network nếu config bật.
- Bean scanning và test-context cache state.

Một unit test nên thay bằng `new PriceService(...)` và fakes. Tuy nhiên, không cực đoan rằng mọi `@SpringBootTest` đều sai: nó phù hợp khi kiểm luồng end-to-end qua bean graph hoặc Boot auto-configuration thật.

### @SpringJUnitConfig và @ContextConfiguration dùng khi nào?

Spring TestContext với `@SpringJUnitConfig`/`@ContextConfiguration` là giải pháp integration hoặc slice wiring test khi bạn cần:
- Kiểm constructor autowiring/qualifiers/conditional beans thực.
- Kiểm BeanPostProcessors, proxy advice, lifecycle, application events.
- Kiểm conversion/placeholder/Environment.
- Dùng Spring transactional tests với transaction infrastructure.
- Kiểm cấu hình context hierarchy hoặc custom scope.

Nó có thể tạo **context nhỏ**, không nhất thiết full Boot app. Nhưng vẫn là **một integration-style test có container**, khác hẳn yêu cầu bài unit test không context.

### Bảng chọn cấp kiểm thử

| Câu hỏi test | Cấp nên dùng |
| --- | --- |
| Giá × số lượng ra bao nhiêu? | Java/JUnit unit test, fake price port |
| Số lượng âm có bị chặn trước khi gọi repository? | Unit test với spy fake |
| Khi lưu lỗi có gửi notification không? | Unit test |
| `@Autowired` constructor có chọn đúng bean không? | Spring context integration test |
| `@Value` lấy đúng property source không? | Spring context/slice test |
| `@Transactional` có rollback JDBC thật không? | Context + transaction manager + database |
| `@Cacheable` có được intercept qua proxy không? | Context + cache manager |
| `@Async` thật sự chạy trên executor không? | Context + async infrastructure |
| BeanPostProcessor có bọc đúng bean không? | Context lifecycle test |
| REST endpoint mapping/serialization hoạt động? | Web slice/MockMvc hoặc integration test |
| Hệ thống gửi được message tới broker? | Integration/contract test |
| JDBC query/constraint chạy như database thật? | Database integration test |

## 14. @Transactional: nơi unit test dễ tự đánh lừa nhất

Một Spring service:

```java
@Service
public class CheckoutService {
    @Transactional
    public void submit(...) {
        repository.save(...);
    }
}
```

Đây là fragment. Khi bạn viết:

```java
CheckoutService sut = new CheckoutService(fakeRepository);
sut.submit(...);
```

bạn **không có Spring transaction proxy**. Annotation `@Transactional` chỉ là metadata nếu không có interception infrastructure. Fake repository cũng không thực hiện JDBC commit/rollback.

Unit test vẫn có ích để kiểm:
- Input validation.
- Giá trị muốn lưu.
- Các business events/commands muốn phát.
- Lỗi được truyền/biến đổi theo service contract.

Nhưng không thể khẳng định:
- JDBC transaction được mở.
- Propagation `REQUIRES_NEW` hoạt động.
- Runtime rollback policy khớp exception types.
- Isolation/readOnly/timeout được áp.
- `@TransactionalEventListener` đợi đúng commit phase.
- Database rollback làm các rows biến mất.

**Phải kiểm riêng qua context/proxy và transactional resource thật.** Unit test SUT và test integration không cạnh tranh nhau; chúng xác minh các phần khác nhau của cùng một feature.

## 15. @Cacheable, @Async, @Scheduled, security và proxy boundary

Tương tự transaction, một object tự `new` không tự hưởng infrastructure annotations:

- `@Cacheable`: không có cache interceptor nếu không có Spring AOP proxy/cache infrastructure.
- `@Async`: không có async interceptor; self/plain Java method invocation diễn ra theo thread caller.
- `@Scheduled`: scheduler không tự đăng ký callback từ object do test tạo.
- Method security: annotation không tự kiểm quyền khi gọi vào raw object.
- `@EventListener`: object không được listener processor đưa vào ApplicationEventMulticaster.
- `@PostConstruct`: tự `new` không gọi `CommonAnnotationBeanPostProcessor`; callback không tự được kích hoạt.
- `@Value`/`@Autowired` fields: không có processor inject vào instance.

Do đó một pure unit test **không nên kiểm rằng các annotation đó được Spring áp đúng**. Nếu business logic cốt lõi bị nhúng vào annotated method, unit test logic bằng `new`; bổ sung context-level test cho wiring/proxy semantics.

### Self-invocation vẫn là giới hạn ngay cả khi có context

Nếu `outer()` của target gọi `this.inner()`, proxy-based advice trên inner thường bị bypass. Một unit test không context không thể cho bạn bằng chứng mọi call path đã được intercept. Integration test phải gọi qua Spring proxy đúng entrypoint, và khi cần kiểm propagation hãy dùng data/transaction state thật thay vì chỉ kiểm boolean transaction active.

## 16. Thiết kế test seam cho API khó fake

Không phải lúc nào dependency cũng đã là một interface đẹp.

### Static method và thời gian

Nếu service gọi `Instant.now()` trực tiếp, ưu tiên refactor để inject `Clock`. Việc mock static bằng bytecode framework có thể hữu ích tạm thời, nhưng cần cân nhắc maintainability và module/toolchain compatibility.

### HTTP client

Bọc HTTP client trong port chuyên trách:

```java
interface AddressLookup {
    Optional<Address> find(String customerId);
}
```

Đây là fragment với class `Address` do domain định nghĩa. Unit test service dùng fake `AddressLookup`; adapter HTTP được test riêng bằng mock server/contract test.

### Database

Bọc JDBC/JPA repository bằng domain-oriented interface, ví dụ `OrderPort.save(Order)`. Unit test dùng fake in-memory; integration test repository với database thật xác minh schema/SQL/transactions.

### Message broker

Service có thể phụ thuộc `EventPublisher` interface. Unit test spy ghi event object, test messaging adapter riêng qua broker contract. Nhưng **“đã gọi publish”** trong unit test không có nghĩa broker thật ack/consumer xử lý.

### Environment và cấu hình

Thay vì business service gọi `Environment.getProperty(...)` nhiều nơi, inject immutable typed options/config vào constructor. Unit test tự `new Options(...)`. Nếu buộc test class phụ thuộc `Environment` API, Spring có `MockEnvironment` (module `spring-test`) dùng ngoài context, nhưng đó vẫn là một Spring-specific type và không cần thiết cho logic thuần.

## 17. Fake fidelity: đừng để fake che bug ở adapter thật

Fake có thể giản lược quá mức:
- In-memory `Map` không áp SQL unique constraint mặc định.
- Không giả lập DB isolation, retries, deadlocks, rollback.
- Không serialize/deserialze JSON hay kiểm schema compatibility.
- Không có network timeouts, TLS, authentication.
- Không tái tạo message broker duplicate delivery.
- Có thể xử lý case-insensitive keys khác database thật.
- Không áp entity lifecycle hoặc ORM lazy loading.
- Không có concurrency/thread safety contract tương đương adapter thật.

Điều này **không làm fake vô dụng**. Nó chỉ chỉ ra phải viết **contract tests** và integration tests cho adapter ở tầng khác.

### Contract test giúp fake và adapter thống nhất

Nếu `OrderPort` quy định `save(order)` chỉ chấp nhận ID duy nhất, bạn có thể viết một suite kiểm behavior chung:
- Fake store implementation.
- JDBC adapter trên test database.
- Production adapter trên môi trường kiểm thử khi có điều kiện.

Các tests chạy cùng scenarios nhưng **không nhất thiết cùng setup**. Mục tiêu là giảm khoảng cách semantics giữa fake và real implementation. Tuy vậy transaction isolation và DB-specific behavior vẫn cần integration test chuyên biệt.

### Khi fake không cần mô phỏng production

Trong `FakeStore` của demo, failure được throw trước khi thêm order. Test chỉ cần mô phỏng “save thất bại trước persistence” để kiểm SUT không gọi receipt. Nó không cần có SQL engine, connection pool hay transaction rollback.

Nếu requirement đổi thành “khi timeout, database có thể đã commit nhưng caller không nhận ack”, fake cần một scenario khác, và integration/saga recovery test tương ứng. Không cố ép một boolean `fail` mô phỏng mọi fault của distributed systems.

## 18. Đọc assertion failures và cách tổ chức fixture

Một fixture tốt:
- Tên collaborator theo vai trò nghiệp vụ.
- Default data nhỏ, hợp lệ.
- Có method để chỉnh từng tình huống.
- Không dùng static shared mutable state.
- Không tự test fake implementation thay vì SUT behavior.
- Dễ nhận biết điều gì là arrange, act, assert.

Dạng Arrange–Act–Assert:

```java
// Arrange
Fixture f = new Fixture();
f.orders.fail = true;

// Act
IllegalStateException error =
        expectThrows(IllegalStateException.class,
                () -> f.sut.place("BOOK", 1));

// Assert
check(error.getMessage().equals("Store unavailable"),
        "error propagated");
check(f.receipts.sendAttempts == 0, "receipt not called");
```

Đây là fragment lấy từ demo. Một tên test giàu ý nghĩa là `whenRepositorySaveFails_doesNotSendReceipt` thay vì `test2`.

### Thói quen kiểm test có thật sự có giá trị

Thử mutation nhỏ:
- Bỏ câu validate quantity → test invalid quantity fail.
- Đổi phép nhân thành phép cộng → test total fail.
- Gọi receipt trước save → test store failure fail.
- Nuốt UnknownSkuException và trả null → test unknown SKU fail.
- Dùng `Instant.now()` thay fixed clock → timestamp test fail.
- Reuse static fake store → isolation test hoặc test chạy thứ tự khác có thể fail.

Một test hữu ích phải phát hiện thay đổi sai **liên quan đến contract**, không chỉ pass vì nó không assert gì. Trong CI, mutation testing có thể lượng hóa chất lượng test, nhưng không thay review logic.

## 19. Concurrency và tính bất biến trong unit tests

Ví dụ dùng `ArrayList`/`HashMap` cho fakes, **không thread-safe**. Điều đó hợp lý cho các tests tuần tự trong một thread như `Main`. Nếu SUT có concurrency:
- Fake phải hỗ trợ concurrency theo contract.
- Test dùng `CountDownLatch`, barrier, `CompletableFuture` hoặc executor có timeout hữu hạn.
- Không assert thread scheduling ngẫu nhiên.
- Không dùng `Thread.sleep(100)` để đoán background task đã chạy.
- Kiểm data race/duplicate operation bằng state/atomic counters đúng.
- Chạy stress/integration tests bổ sung cho behavior ngoài JVM giả.

### Immutable results giảm gánh nặng kiểm thử

`Order` là record với các components cố định. `BigDecimal` và `Instant` immutable; danh tính và thời gian đã được cấp lúc tạo. Các objects bất biến ít nguy cơ state bị thay đổi giữa save/send assertions.

Fake collections vẫn mutable để ghi calls, nhưng đó là **test code có chủ đích** và được tạo mới mỗi test.

### Không ràng buộc test vào thực thi song song của CI

JUnit có thể được cấu hình chạy tests concurrent. Shared fakes/lists giữa test methods khi đó gây race. `new Fixture()` trong từng `@Test` ngăn state contamination; nếu một singleton external service được test, phải có isolation/cleanup và concurrency policy phù hợp.

## 20. Khi nào phải mở Spring context?

Khi mục tiêu không còn là logic thuần của một object mà là **hợp đồng Spring wiring/infrastructure**:
1. `@Configuration` tạo đúng beans.
2. Profiles/conditionals áp đúng.
3. `@Qualifier`/`@Primary` chọn đúng candidate.
4. `BeanPostProcessor` thực thi injection/wrapping đúng.
5. `@Transactional` hoạt động qua proxy và JDBC rollback.
6. `@Async` tạo executor invocation đúng, errors được quản lý.
7. `@Cacheable` cache hits/misses qua proxy.
8. `@EventListener`/`@TransactionalEventListener` chạy theo event/transaction phases.
9. Lifecycle `@PostConstruct`/`@PreDestroy` đúng.
10. Bean scopes, request/session scoped proxies và context hierarchy đúng.

Test có thể dùng một `AnnotationConfigApplicationContext` tạo thủ công trong integration test, hoặc Spring TestContext với `@SpringJUnitConfig`, `@ContextConfiguration`, `@ExtendWith(SpringExtension.class)`. Nếu cần full Boot auto-config thì dùng `@SpringBootTest` khi đúng phạm vi.

### Context caching không biến integration test thành unit test

Spring TestContext **cache** các context theo configuration key để nhiều tests dùng chung context, giúp giảm khởi tạo lặp. Nhưng nó vẫn khởi động một context và có các lifecycle/integration semantics. Cache hit không xóa chi phí design, shared state hay requirement mock external resources. <escape>Context caching</escape> là tối ưu hiệu năng của integration suite, **không phải lý do để dùng context trong mọi business assertion**.

### Một test pyramid hợp lý

```text
                 E2E / full system
               (ít, nhưng quan trọng)
              ------------------------
                Integration / slices
                (wiring, DB, proxy)
             -------------------------
                 Plain unit tests
             (business rules, fake ports,
                 nhanh, cô lập)
```

Không phải một tỷ lệ cứng 70/20/10 áp dụng cho mọi dự án. Chọn tầng dựa vào failure mode quan trọng: có code cần nhiều integration tests, có pure domain core cần phần lớn unit tests.

## 21. Lỗi thường gặp và cách khắc phục

| Triệu chứng | Nguyên nhân | Cách khắc phục |
| --- | --- | --- |
| Unit test mất nhiều giây chỉ để kiểm phép tính | Dùng `@SpringBootTest` không cần thiết | `new SUT(fakeDeps)` |
| Test `new Service()` bị field null | Field injection đòi container | Constructor injection |
| Test dựa thời gian hiện tại bị flaky | `Instant.now()` trong SUT | Inject fixed `Clock` |
| Test ID thay đổi mỗi lần | UUID/random trực tiếp | Inject `Supplier<String>` |
| Fake store từ test trước còn dữ liệu | Static/shared mutable state | New fixture mỗi test |
| Mockito mock trả null ngoài ý muốn | Chưa stub call/argument | Stub rõ hoặc dùng fake phù hợp |
| Spy verify tất cả private steps làm test giòn | Overspecification | Verify business contract |
| Test fake pass nhưng SQL schema fail | Fake không có DB constraints | JDBC integration test |
| Test fake pass nhưng transaction không rollback | Không có proxy/DB transaction | Context+DB integration test |
| `@Cacheable` không được kiểm | Test raw object bằng `new` | Proxy-aware integration test |
| `@Async` không tạo thread mới | Không có async infrastructure | Context/executor test |
| @Autowired qualifiers sai nhưng unit pass | Unit không test wiring | Context wiring integration test |
| Test có JUnit nhưng nghĩ Spring đã start | Nhầm `@Test` với SpringExtension | Hiểu runner vs container |
| Test method order làm đổi kết quả | Shared test state | Isolate fixtures |
| Test exception pass nhưng đã gọi sender | Chỉ assert throws | Kiểm side-effect attempts |
| Price 19.90 cho total 39.8 so không đúng | BigDecimal scale | Dùng String decimal/compareTo khi phù hợp |
| Test dùng `assert` nhưng pass cả khi sai | JVM không bật assertions | JUnit asserts hoặc custom check |
| Test fake notifier ghi send thành công rồi throw | Fake fault placement không nhất quán | Xác định fail-before/fail-after semantics |
| Test receipt failure giả vờ rollback save | Không có transaction | Tách business semantics và integration test |
| Context test không được cache | Config keys khác, JVM forks | Dùng TestContext caching đúng config |
| Mockito @InjectMocks tạo object nhưng không có proxy | Mockito không phải Spring | Test annotation behavior ở context |
| Unit test phụ thuộc localhost/real HTTP | Không cô lập adapter | Port fake + adapter integration |
| Fake ngày càng dài/phức tạp như database | Overfaking | Chuyển một số tests sang real adapter |
| Async test dùng sleep cố định | Timing nondeterministic | Latches/Futures với timeout |

## 22. Checklist áp dụng và câu hỏi tự đánh giá

**Checklist:**

- [ ] Xác định SUT là business object, không phải toàn application.
- [ ] Tạo SUT bằng `new`, không khởi động context nếu test chỉ cần business rules.
- [ ] Dùng constructor injection cho required dependencies.
- [ ] Khai ports/interfaces nhỏ theo nghiệp vụ thay vì tạo phụ thuộc trực tiếp vào JDBC/HTTP SDK.
- [ ] Mỗi test dùng fakes/fixtures mới, không dùng shared mutable state.
- [ ] Stub values cần kiểm soát; không dùng dữ liệu ngẫu nhiên không seed.
- [ ] Inject fixed `Clock` và deterministic ID supplier.
- [ ] Dùng `BigDecimal` đúng scale/semantic cho monetary calculations.
- [ ] Kiểm happy path, invalid input, missing data và collaborator failure.
- [ ] Assertion cả output, state và side effects thực sự là business contract.
- [ ] Kiểm không gọi downstream port khi upstream đã thất bại.
- [ ] Không verify incidental private implementation details.
- [ ] Giữ fake đủ đơn giản, không mô phỏng RDBMS quá sâu.
- [ ] Nhớ Mockito và JUnit không tự mở Spring context.
- [ ] Không nhầm Mockito `@InjectMocks` với Spring `@Autowired`.
- [ ] Không kết luận transaction/proxy/cache/async/lifecycle từ raw object tests.
- [ ] Viết context integration tests cho phần annotations/infrastructure quan trọng.
- [ ] Viết adapter integration/contract tests cho SQL, HTTP, broker.
- [ ] Không assert exact Maven logs/time-dependent output.
- [ ] Có mutation/regression checks cho những business invariants quan trọng.
- [ ] Không in secrets hoặc dữ liệu cá nhân thật trong test fixtures/logs.
- [ ] CI tách unit tests nhanh khỏi integration tests cần external resources.

**Câu hỏi tự đánh giá:**

1. Vì sao DI giúp test service không cần Spring?
2. `@Test` của JUnit có tự refresh ApplicationContext không?
3. SUT viết tắt từ gì?
4. Fake khác stub và spy thế nào?
5. Constructor injection tốt hơn field injection cho unit test ở điểm nào?
6. Thời gian hiện tại nên tiêm bằng type Java nào?
7. Có nên dùng `UUID.randomUUID()` trực tiếp nếu cần output deterministic không?
8. `BigDecimal("19.90")` nhân 2 cho total nào?
9. Cần kiểm điều gì khi quantity âm?
10. Tại sao Unknown SKU không được gọi repository save?
11. Khi save thất bại, receipt port có được gọi không?
12. Khi receipt port ném sau save, fake store có tự rollback không?
13. Một fake store có bảo đảm transactional semantics của real JDBC không?
14. Vì sao gọi một mock đúng một lần không có nghĩa downstream request đã thành công?
15. Mockito `@InjectMocks` có chạy Spring BeanPostProcessor không?
16. `@SpringBootTest` hữu ích khi nào?
17. `@SpringJUnitConfig` thường mở loại test nào?
18. Spring context caching có làm test thành pure unit không?
19. Tại sao test `@Transactional` rollback cần integration test?
20. `@Cacheable` trên raw object được tạo bằng new có được intercept không?
21. Thử nghiệm @Async có thể dùng gì thay Thread.sleep?
22. Test fixture dùng static mutable list có nguy cơ gì?
23. Khi mock behavior verify quá chi tiết, refactor có thể gây ra điều gì?
24. Mỗi test phải pass riêng lẻ và khi chạy cả suite vì sao?
25. Khi nào nên chuyển fake phức tạp thành test real adapter?

**Đáp án ngắn:** (1) Collaborators được cấp qua constructor, có thể thay bằng fake. (2) Không. (3) System Under Test. (4) Fake có behavior giản lược, stub trả giá trị kiểm soát, spy ghi calls. (5) Không cần container/reflection để cấp required dependency. (6) `Clock`. (7) Không, hãy inject ID supplier. (8) `39.80`. (9) Exception và không chạm các ports. (10) Business rule chặn trước save. (11) Không. (12) Không. (13) Không. (14) Verify chỉ kiểm invocation. (15) Không. (16) Khi cần Boot application wiring/auto-config thật. (17) Context integration-style. (18) Không. (19) Cần proxy và transaction manager/database thật. (20) Không. (21) Latches/Futures với timeout. (22) Test order/race/leak state. (23) False failures khi behavior không đổi. (24) Test isolation/repeatability. (25) Khi semantics của fake quá khác adapter thật hoặc critical constraints cần chứng minh.

## 23. Nguồn tham khảo chính thức

- Spring Framework Reference — Unit Testing (POJO và mocks, không cần container): https://docs.spring.io/spring-framework/reference/testing/unit.html
- Spring Framework Reference — Introduction to Testing (IoC giúp testable object layers): https://docs.spring.io/spring-framework/reference/testing.html
- Spring Framework Reference — Integration Testing: https://docs.spring.io/spring-framework/reference/testing/integration.html
- Spring Framework Reference — Spring TestContext Framework: https://docs.spring.io/spring-framework/reference/testing/testcontext-framework.html
- Spring Framework Reference — Context Caching: https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/caching.html
- Spring Framework Reference — Dependency Injection: https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html
- Spring Framework Reference — Declarative Transaction Management: https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative.html
- Spring Framework Reference — Spring AOP Proxying Mechanisms: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- JUnit 5 User Guide: https://junit.org/junit5/docs/current/user-guide/
- Mockito Reference/Documentation: https://site.mockito.org/
- Oracle Java 17 API — Clock: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/Clock.html
- Oracle Java 17 API — BigDecimal: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="container-testing-002">Kiểm thử lắp ghép bằng AnnotationConfigApplicationContext tối thiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử lắp ghép bằng AnnotationConfigApplicationContext tối thiểu

## 1. Mục tiêu của wiring test

Spring Framework Core Container thực hiện nhiều việc mà unit test tạo object bằng `new` không tự kiểm tra: đọc `@Configuration`, xử lý `@Bean`, chọn bean theo type/qualifier/profile, gọi factory methods, giải quyết constructor arguments, áp BeanPostProcessors và thực thi lifecycle.

**Kiểm thử lắp ghép** (*wiring test*) dùng một `ApplicationContext` **thật nhưng tối thiểu** để xác nhận rằng một nhóm beans có thể được Spring khởi tạo và sử dụng đúng, thay vì chỉ xác nhận Java classes biên dịch được.

Ví dụ một service:

~~~java
public final class CheckoutService {
    private final PricePolicy pricing;

    public CheckoutService(PricePolicy pricing) {
        this.pricing = pricing;
    }
}
~~~

Đây là snippet, yêu cầu interface `PricePolicy`. Unit test có thể truyền fakePricePolicy bằng `new`, nhưng không cho biết Spring sẽ chọn được PricePolicy bean nào, liệu có hai candidates gây ambiguity, hoặc bean cần thiết bị `@Profile` loại bỏ hay không.

> **Quy tắc cốt lõi:** Dựng context rỗng → chọn Environment/profiles → đăng ký **chỉ các configuration classes cần thiết** → `refresh()` → assert bean wiring → `close()`.

Chủ đề thuộc **Kiểm thử container** của **Spring Framework — core-container**. Bài này tập trung vào `AnnotationConfigApplicationContext` thuần Spring, không yêu cầu Spring Boot, servlet server, database hoặc `@SpringBootTest`.

## 2. Các cấp độ kiểm thử khác nhau

| Loại | Câu hỏi cần kiểm chứng | Container |
| --- | --- | --- |
| Unit test | `CheckoutService(fakePolicy)` có tính toán đúng không? | Không |
| Minimal wiring test | `@Bean` có đăng ký đủ dependencies và chọn đúng implementation không? | Context nhỏ, tạo/đóng trong test |
| Spring TestContext | Cùng bean graph có được inject vào JUnit tests, áp profiles và reuse context đúng không? | Context thật do `spring-test` quản lý |
| Spring Boot test slice / full integration | MVC/JPA/auto-configuration hay service ngoài hoạt động đúng không? | Infrastructure đúng mục tiêu test |

Bài này kiểm **wiring trực tiếp** bằng `AnnotationConfigApplicationContext`, ví dụ thiếu `PricePolicy` thì fail khi refresh eager `CheckoutService`. Unit test ở bài trước vẫn là nơi kiểm business rules, còn TestContext ở bài tiếp theo cung cấp JUnit integration/caching. Không cần tải full application chỉ để chứng minh một bean graph nhỏ được lắp ghép đúng.

## 3. AnnotationConfigApplicationContext hoạt động như thế nào?

`AnnotationConfigApplicationContext` nằm trong `org.springframework.context.annotation`. Nó hỗ trợ Java configuration dựa trên `@Configuration` và `@Bean` cùng annotation infrastructure phù hợp.

Hai cách sử dụng:

**Constructor tiện lợi — refresh ngay:**

~~~java
try (AnnotationConfigApplicationContext context =
         new AnnotationConfigApplicationContext(MyConfig.class)) {
    MyService service = context.getBean(MyService.class);
}
~~~

Đây là snippet với các classes giả định `MyConfig` và `MyService`.

**Constructor rỗng — kiểm soát startup:**

~~~java
try (AnnotationConfigApplicationContext context =
         new AnnotationConfigApplicationContext()) {

    context.getEnvironment().setActiveProfiles("test");
    context.register(MyConfig.class);
    context.refresh();

    MyService service = context.getBean(MyService.class);
}
~~~

Với wiring test, cách thứ hai thường tốt hơn vì test có thể thiết lập active profiles, properties, registration order **trước `refresh()`**.

### Lưu ý refresh

`AnnotationConfigApplicationContext` kế thừa `GenericApplicationContext` và không được dùng như một context có thể `refresh()` lặp nhiều lần để reset trạng thái. Muốn đổi kịch bản test, tạo context mới.

## 4. Vòng đời trong một test

~~~text
new AnnotationConfigApplicationContext()
      |
      v
getEnvironment().setActiveProfiles(...)
      |
      v
register(ConfigA.class, ConfigB.class)
      |
      v
refresh()
  -> bean factory processors
  -> bean post processors
  -> dependency resolution
  -> eager singleton initialization
      |
      v
getBean(), containsBeanDefinition(), assertions
      |
      v
close() / try-with-resources
~~~

`register` thêm metadata, **không tự bảo đảm bean instance đã được tạo**. `refresh` mới kích hoạt lifecycle của ApplicationContext. `getBean` giúp kiểm tra kết quả thật, không chỉ tên definition.

Đừng gọi `getBean` trên context còn chưa refresh rồi kỳ vọng nó hoạt động như context đã active.

## 5. Thế nào là một context tối thiểu?

Một test chỉ cần kiểm tra CheckoutService thường đăng ký:

- CoreConfig định nghĩa CheckoutService.
- Một repository in-memory để tránh database ngoài.
- Một PricePolicy test implementation.
- Infrastructure cần thiết nếu đúng chức năng đang test.

Nó thường không cần component-scan toàn ứng dụng, HTTP server, controller, scheduled jobs, database production hay nhiều auto-configurations.

### Tránh hai thái cực

**Quá ít:** chỉ new CheckoutService với fake objects; Spring không resolve dependencies nào.

**Quá nhiều:** scan cả root package của ứng dụng để test một constructor; import external APIs, email clients, database pools và dozens of unrelated beans.

Cách đúng là **đủ thật để kiểm tra wiring contract nhưng giới hạn phạm vi dependencies**.

## 6. Đăng ký configuration cụ thể thay vì scan quá rộng

~~~java
AnnotationConfigApplicationContext context =
    new AnnotationConfigApplicationContext();

context.register(CoreConfig.class, TestPolicyConfig.class);
context.refresh();
~~~

Đây là snippet cần các classes cấu hình thích hợp.

`@ComponentScan("com.example")` có thể kéo nhiều beans ngoài dự kiến vào test. Nếu mục tiêu là kiểm tra component scanning, hãy cố ý test scan với package hẹp. Nếu mục tiêu là kiểm tra constructor injection qua `@Bean`, dùng explicit register sẽ đơn giản và chính xác hơn.

### Không đổi production path một cách tùy tiện

Nếu production app dùng `@Import` hoặc bean post processors đặc biệt, minimal wiring test phải load những elements đủ để test behavior thực sự được kiểm tra. Không được khẳng định một test không dùng scanning đã xác minh được production component-scanning behavior.

## 7. Bean method parameter injection

~~~java
@Configuration(proxyBeanMethods = false)
public class CoreConfig {

    @Bean
    OrderRepository orderRepository() {
        return new OrderRepository();
    }

    @Bean
    CheckoutService checkoutService(
            OrderRepository repository,
            PricePolicy policy) {
        return new CheckoutService(repository, policy);
    }
}
~~~

Đây là snippet cần các domain classes. Spring inject `OrderRepository` và `PricePolicy` vào factory method, không đòi bạn tự gọi những methods khác trong config.

Nếu PricePolicy không được đăng ký, container thường fail khi `checkoutService` eager singleton được khởi tạo. Nếu hai policies cùng type và không có candidate-selection policy, có thể gặp `NoUniqueBeanDefinitionException`.

### proxyBeanMethods=false

Vì factory methods của ví dụ không gọi nhau trực tiếp mà sử dụng parameters, không cần cấu hình inter-bean method interception. Nếu production code dựa vào full `@Configuration` method proxying thì minimal test cần giữ đúng behavior production thay vì đổi sang false vô điều kiện.

## 8. Profiles được chọn trước refresh

Spring `@Profile` quyết định điều kiện registration theo environment. Một config:

~~~java
@Configuration(proxyBeanMethods = false)
@Profile("test")
class TestPolicyConfig {
    @Bean
    PricePolicy testPolicy() {
        return new FlatPricePolicy("test", 12);
    }
}
~~~

Nếu active profile là `test` trước refresh, bean có thể được register. Nếu active profile là `prod`, config này không match.

Một test có thể tạo hai contexts riêng, với `test` và `prod`, cùng CoreConfig nhưng implementations khác.

**Đặt profile trước `refresh()`.** Đổi profile sau khi context đã active không tự xây lại bean graph.

### Vì sao không dựa vào default profile?

Default profile hoạt động khi không có active profile. Môi trường dev/CI có thể đặt `spring.profiles.active` khiến kết quả test phụ thuộc máy chạy. Để deterministic, test nên chủ động `setActiveProfiles("test")`.

## 9. Environment/property source trong wiring test

Nếu cần test cấu hình qua `Environment`:

~~~java
MapPropertySource source = new MapPropertySource(
    "test-values",
    Map.of("service.timeout.ms", "250"));

context.getEnvironment()
    .getPropertySources().addFirst(source);
~~~

Đây là snippet cần `MapPropertySource` và `java.util.Map`, chạy trước refresh.

Với một bean có `@Value` placeholders, cần xác nhận annotation/configuration processing phù hợp. Spring Boot `@ConfigurationProperties` binding không tự trở thành một phần của bare `AnnotationConfigApplicationContext` nếu chưa đăng ký infrastructure cần thiết.

Với bài wiring không liên quan properties, dùng một test settings bean đơn giản thay vì kéo infrastructure cấu hình lớn.

## 10. Assertions phù hợp cho container

Một số câu hỏi kiểm tra:

~~~java
assertTrue(context.containsBeanDefinition("checkoutService"));
assertNotNull(context.getBean(CheckoutService.class));
assertSame(
    context.getBean(CheckoutService.class),
    context.getBean(CheckoutService.class));
~~~

Đây là JUnit 5 snippets, cần static imports `org.junit.jupiter.api.Assertions`.

- `containsBeanDefinition` hỏi local BeanDefinition registry.
- `getBean(Type)` yêu cầu một bean instance phù hợp.
- `assertSame` xác nhận singleton identity trong context.
- `getBeansOfType(Type)` phù hợp khi muốn kiểm tra tập candidates cùng type.

Đừng hard-code `getBeanDefinitionCount() == 3` chỉ vì bạn định nghĩa ba business beans: Spring thường có internal configuration/infrastructure definitions. Kiểm tra **business bean names/types**, không dựa vào đếm toàn bộ nội bộ container.

## 11. Negative test: thiếu dependency phải fail

Ví dụ giả định `CoreConfig` khai báo CheckoutService cần PricePolicy nhưng không có policy config:

~~~java
try (AnnotationConfigApplicationContext context =
         new AnnotationConfigApplicationContext()) {

    context.register(CoreConfig.class);

    assertThrows(
        BeanCreationException.class,
        context::refresh);
}
~~~

Đây là JUnit 5 snippet; cần imports `BeanCreationException` và `assertThrows`.

Exception thực tế có thể là subtype `UnsatisfiedDependencyException`, với nested cause về no bean candidate. Assert subtype ổn định hoặc kiểm tra causal chain, không so sánh nguyên văn toàn bộ exception message.

### Negative test có giá trị gì?

Nó chứng minh wiring test phát hiện một lỗi mà unit test với injected fake bỏ qua. Khi thiếu collaborator bắt buộc, tốt hơn để context **fail-fast** thay vì chạy với một fallback tự chế chưa được yêu cầu.

## 12. Lỗi lúc refresh và lỗi lúc getBean

| Kiểu bean | Thời điểm lỗi phổ biến |
| --- | --- |
| Eager singleton thiếu dependency | `refresh()` |
| Lazy singleton thiếu dependency | Khi dependency được dùng / `getBean()` |
| Prototype thiếu dependency | Mỗi lần yêu cầu instance |
| FactoryBean product có lỗi | Khi factory/product được yêu cầu |
| Method nghiệp vụ lỗi | Khi gọi method |
| Request/session scoped bean | Khi scope được dùng nhưng không active |

Vì vậy một test chỉ assert `refresh()` thành công **chưa đủ** nếu đối tượng cần test là lazy hoặc prototype. Khi đó hãy gọi `getBean(Target.class)` trong điều kiện scope tương ứng.

### ScopeNotActiveException

Standalone minimal context không tự mở HTTP request scope. Nếu test web scopes, hãy dùng web test infrastructure thích hợp; không kỳ vọng request/session beans hoạt động trong một context non-web rỗng.

## 13. Context isolation và singleton ownership

Nếu tạo hai contexts độc lập:

~~~java
try (AnnotationConfigApplicationContext a = open("test");
     AnnotationConfigApplicationContext b = open("test")) {

    assertNotSame(
        a.getBean(OrderRepository.class),
        b.getBean(OrderRepository.class));
}
~~~

Đây là snippet từ project ở mục 15, với `open` helper và JUnit `assertNotSame`.

Một singleton là singleton **trong phạm vi BeanFactory tương ứng**, không phải singleton toàn JVM. Hai contexts có thể có hai repository instances khác nhau.

Cẩn thận: static mutable state hoặc external database cùng kết nối vẫn có thể làm tests ảnh hưởng nhau. Context isolation không tự cô lập toàn bộ môi trường ngoài container.

## 14. Cleanup bằng try-with-resources

`AnnotationConfigApplicationContext` có lifecycle `close()` và có thể dùng trong try-with-resources:

~~~java
try (AnnotationConfigApplicationContext context =
         new AnnotationConfigApplicationContext()) {

    context.register(CoreConfig.class, TestPolicyConfig.class);
    context.getEnvironment().setActiveProfiles("test");
    context.refresh();

    // assertions
}
~~~

Khi thoát block, Java gọi close kể cả nếu assertion ném exception. Điều này giúp container gọi singleton destroy callbacks, dừng managed lifecycle components và giảm state leaks.

Nó không tự rollback mọi external side effect. Nếu test tạo temp files, mở raw threads ngoài container hoặc sửa JVM system properties, cần cleanup tương ứng.

## 15. Ví dụ hoàn chỉnh Java 17 + Spring Context 6.1.13 + JUnit Jupiter

Project này dùng **hai configs cho hai profiles** nhưng chỉ một core wiring graph.

- CoreConfig cung cấp OrderRepository, CheckoutService, LifecycleProbe.
- TestPolicyConfig cung cấp PricePolicy cho profile test.
- ProdPolicyConfig cung cấp PricePolicy cho profile prod.
- Test code dùng mỗi context một profile và tự close.
- Negative test không đăng ký PricePolicy để đảm bảo refresh báo lỗi.
- Lifecycle test xác nhận `@Bean(destroyMethod)` hoạt động.

### 15.1. Cấu trúc

~~~text
minimal-container-wiring/
├── pom.xml
└── src/
    ├── main/java/com/example/minimal/MinimalContainerDemo.java
    └── test/java/com/example/minimal/MinimalContainerTest.java
~~~

### 15.2. pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>minimal-container-wiring</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.1.13</version>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.11.1</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.3.0</version>
                <configuration>
                    <mainClass>com.example.minimal.MinimalContainerDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 15.3. src/main/java/com/example/minimal/MinimalContainerDemo.java

~~~java
package com.example.minimal;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

public class MinimalContainerDemo {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext test = open("test");
             AnnotationConfigApplicationContext prod = open("prod")) {

            CheckoutService testService =
                test.getBean(CheckoutService.class);
            CheckoutService prodService =
                prod.getBean(CheckoutService.class);

            OrderRepository testRepo =
                test.getBean(OrderRepository.class);
            OrderRepository prodRepo =
                prod.getBean(OrderRepository.class);

            System.out.println("test.policy=" +
                test.getBean(PricePolicy.class).name());
            System.out.println("test.receipt=" +
                testService.place("A-1", 2));
            System.out.println("test.saved=" +
                testRepo.exists("A-1"));
            System.out.println("test.singleton=" +
                (testService == test.getBean(CheckoutService.class)));

            System.out.println("prod.policy=" +
                prod.getBean(PricePolicy.class).name());
            System.out.println("prod.saved.before=" +
                prodRepo.exists("A-1"));
            System.out.println("prod.receipt=" +
                prodService.place("A-1", 2));

            System.out.println("repositories.isolated=" +
                (testRepo != prodRepo));
        }
    }

    public static AnnotationConfigApplicationContext open(
            String profile) {

        AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext();

        context.getEnvironment().setActiveProfiles(profile);
        context.register(
            CoreConfig.class,
            TestPolicyConfig.class,
            ProdPolicyConfig.class);
        context.refresh();
        return context;
    }

    public interface PricePolicy {
        String name();
        int quote(int quantity);
    }

    public record FlatPricePolicy(
            String name, int unitPrice) implements PricePolicy {

        @Override
        public int quote(int quantity) {
            return Math.multiplyExact(unitPrice, quantity);
        }
    }

    public static final class OrderRepository {
        private final Set<String> ids =
            ConcurrentHashMap.newKeySet();

        public void save(String id) {
            ids.add(id);
        }

        public boolean exists(String id) {
            return ids.contains(id);
        }
    }

    public static final class CheckoutService {
        private final OrderRepository repository;
        private final PricePolicy policy;

        public CheckoutService(
                OrderRepository repository, PricePolicy policy) {
            this.repository = repository;
            this.policy = policy;
        }

        public String place(String id, int quantity) {
            if (quantity <= 0) {
                throw new IllegalArgumentException(
                    "Quantity must be positive");
            }

            int total = policy.quote(quantity);
            repository.save(id);
            return "accepted:" + id + ":" + total;
        }
    }

    public static final class LifecycleProbe {
        private boolean closed;

        public void markClosed() {
            closed = true;
        }

        public boolean isClosed() {
            return closed;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class CoreConfig {

        @Bean
        public OrderRepository orderRepository() {
            return new OrderRepository();
        }

        @Bean
        public CheckoutService checkoutService(
                OrderRepository repository, PricePolicy policy) {
            return new CheckoutService(repository, policy);
        }

        @Bean(destroyMethod = "markClosed")
        public LifecycleProbe lifecycleProbe() {
            return new LifecycleProbe();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Profile("test")
    public static class TestPolicyConfig {

        @Bean
        public PricePolicy testPricePolicy() {
            return new FlatPricePolicy("test", 12);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Profile("prod")
    public static class ProdPolicyConfig {

        @Bean
        public PricePolicy prodPricePolicy() {
            return new FlatPricePolicy("prod", 20);
        }
    }
}
~~~

### 15.4. src/test/java/com/example/minimal/MinimalContainerTest.java

~~~java
package com.example.minimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

class MinimalContainerTest {

    @Test
    void testProfileWiresBeans() {
        try (AnnotationConfigApplicationContext context =
                 MinimalContainerDemo.open("test")) {

            MinimalContainerDemo.CheckoutService checkout =
                context.getBean(
                    MinimalContainerDemo.CheckoutService.class);
            MinimalContainerDemo.OrderRepository repo =
                context.getBean(
                    MinimalContainerDemo.OrderRepository.class);

            assertNotNull(checkout);
            assertTrue(context.containsBeanDefinition("checkoutService"));
            assertEquals("test",
                context.getBean(
                    MinimalContainerDemo.PricePolicy.class).name());
            assertEquals("accepted:A-1:24",
                checkout.place("A-1", 2));
            assertTrue(repo.exists("A-1"));
            assertSame(checkout,
                context.getBean(
                    MinimalContainerDemo.CheckoutService.class));
        }
    }

    @Test
    void productionProfileSelectsProdPolicy() {
        try (AnnotationConfigApplicationContext context =
                 MinimalContainerDemo.open("prod")) {

            assertEquals("prod",
                context.getBean(
                    MinimalContainerDemo.PricePolicy.class).name());
            assertEquals("accepted:B-1:40",
                context.getBean(
                    MinimalContainerDemo.CheckoutService.class)
                    .place("B-1", 2));
        }
    }

    @Test
    void independentContextsHaveIndependentRepositories() {
        try (AnnotationConfigApplicationContext first =
                 MinimalContainerDemo.open("test");
             AnnotationConfigApplicationContext second =
                 MinimalContainerDemo.open("test")) {

            MinimalContainerDemo.OrderRepository firstRepo =
                first.getBean(
                    MinimalContainerDemo.OrderRepository.class);
            MinimalContainerDemo.OrderRepository secondRepo =
                second.getBean(
                    MinimalContainerDemo.OrderRepository.class);

            assertNotSame(firstRepo, secondRepo);
            firstRepo.save("ONLY-FIRST");
            assertTrue(firstRepo.exists("ONLY-FIRST"));
            assertFalse(secondRepo.exists("ONLY-FIRST"));
        }
    }

    @Test
    void missingPolicyFailsAtRefresh() {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext()) {

            context.register(MinimalContainerDemo.CoreConfig.class);

            assertThrows(
                BeanCreationException.class,
                context::refresh);
        }
    }

    @Test
    void closingContextInvokesDestroyCallback() {
        AnnotationConfigApplicationContext context =
            MinimalContainerDemo.open("test");

        MinimalContainerDemo.LifecycleProbe probe =
            context.getBean(
                MinimalContainerDemo.LifecycleProbe.class);

        try {
            assertFalse(probe.isClosed());
        } finally {
            context.close();
        }

        assertTrue(probe.isClosed());
    }
}
~~~

### 15.5. Chạy và kiểm tra Maven tests

Yêu cầu Java 17+ và Maven 3.9.x. Từ thư mục chứa pom.xml:

~~~bash
mvn -q test
~~~

Có **5 JUnit Jupiter tests** dự kiến pass, không có failures/errors. Maven quiet output có thể không in danh sách tests, nhưng Surefire tạo báo cáo dưới thư mục `target/surefire-reports`, ví dụ `com.example.minimal.MinimalContainerTest.txt`.

Không lấy một dòng console Maven tùy phiên bản làm contract cố định. Điều cần assert là **5 tests được chạy, 0 failures, 0 errors**.

### 15.6. Chạy main và stdout xác định

~~~bash
mvn -q compile exec:java
~~~

Những dòng `System.out.println` của chương trình:

~~~text
test.policy=test
test.receipt=accepted:A-1:24
test.saved=true
test.singleton=true
prod.policy=prod
prod.saved.before=false
prod.receipt=accepted:A-1:40
repositories.isolated=true
~~~

Maven hoặc logging có thể in thêm dòng khác.

### 15.7. Giải thích output

**test.policy=test**: Active profile test chọn đúng TestPolicyConfig.

**test.receipt=accepted:A-1:24**: FlatPricePolicy test có unitPrice=12; quantity 2 → total 24; CheckoutService được inject repository và policy.

**test.saved=true**: OrderRepository trong test context ghi ID A-1.

**test.singleton=true**: Cùng CheckoutService bean instance được trả về khi lookup nhiều lần trong một BeanFactory.

**prod.policy=prod**: Active profile prod chọn ProdPolicyConfig, không chọn TestPolicyConfig.

**prod.saved.before=false**: Prod context có repository riêng, không thấy A-1 của test context.

**prod.receipt=accepted:A-1:40**: UnitPrice prod=20; quantity 2 → total 40.

**repositories.isolated=true**: Hai independent contexts tạo hai repository singletons khác nhau.

## 16. Diễn giải năm test cases

1. **testProfileWiresBeans**: context refresh, chọn đúng test policy, business call chạy đúng, repository được inject, singleton identity ổn định.
2. **productionProfileSelectsProdPolicy**: cùng core wiring graph nhưng profile khác, kết quả khác như mong đợi.
3. **independentContextsHaveIndependentRepositories**: singleton scope bị giới hạn ở BeanFactory; dữ liệu in-memory không tràn giữa test contexts.
4. **missingPolicyFailsAtRefresh**: CoreConfig không có PricePolicy, việc tạo eager CheckoutService fail-fast.
5. **closingContextInvokesDestroyCallback**: `@Bean(destroyMethod="markClosed")` khiến LifecycleProbe chuyển trạng thái khi context.close.

Đây là kiểm thử container thật, dù class test không dùng `@SpringJUnitConfig` hay Spring TestContext Framework.

## 17. Negative test giúp phát hiện cấu hình sai

Ngoài thiếu dependency, nên kiểm tra:

- Hai PricePolicy beans cùng active profile, không `@Primary` hoặc qualifier: candidate ambiguity.
- Profile không tồn tại: CoreConfig cần policy nhưng không có policy config nào match.
- `@Bean` method có constructor dependency sai type.
- Duplicate bean name trong một registry với overriding policy không cho phép.
- Circular constructor dependency.
- Bean initialization method ném exception.
- Type mismatch do factory method return object không phù hợp.

Có thể viết `assertThrows(BeanCreationException.class, context::refresh)` với eager singleton scenarios. Với lazy/prototype beans, gọi `getBean` mới kích hoạt lỗi.

## 18. Candidate selection: @Primary và @Qualifier

Nếu có hai beans cùng type:

~~~java
@Bean
@Primary
PricePolicy firstPolicy() {
    return new FlatPricePolicy("first", 10);
}

@Bean
PricePolicy secondPolicy() {
    return new FlatPricePolicy("second", 20);
}
~~~

Đây là snippets minh họa trong một config class có imports `Bean`, `Primary`.

Spring có candidate-selection rules cho DI theo type. Một wiring test có thể assert consumer được inject đúng policy.

`@Qualifier` giúp chọn theo qualifier/name, nhưng không thay cho việc xác định đúng active profile/config scope. Nếu chỉ muốn một implementation, tốt hơn là register duy nhất config phù hợp.

## 19. Test singleton, prototype và lazy

Các assertions hữu ích:

~~~java
assertTrue(context.isSingleton("checkoutService"));

CheckoutService a = context.getBean(CheckoutService.class);
CheckoutService b = context.getBean(CheckoutService.class);
assertSame(a, b);
~~~

Với prototype beans, cùng method `getBean` sẽ thường tạo instances riêng; có thể assert `isPrototype` và `assertNotSame`.

Với lazy singleton, `containsSingleton(beanName)` phản ánh liệu instance đã nằm trong singleton cache, **khác** `containsBeanDefinition`. Lưu ý lazy bean có thể được tạo sớm nếu một eager singleton phụ thuộc nó.

Không cố ép bare context kiểm thử request/session scopes khi không có scope infrastructure phù hợp.

## 20. Lifecycle và cleanup

`close()` gọi singleton destruction callbacks theo lifecycle container. Không nên để tests giữ context đến khi JVM exit, đặc biệt nếu bean mở executors, file handles hoặc sockets.

Try-with-resources tốt cho positive tests. Với negative startup test, cũng giữ context trong try-with-resources để tránh rò rỉ nếu một phần startup đã hoàn thành.

Với custom prototype beans, Spring không tự hủy toàn bộ instances lúc context đóng. Test cleanup cần tách theo scope và ownership.

## 21. @Configuration(proxyBeanMethods=false) và test fidelity

AnnotationConfigApplicationContext xử lý `@Configuration` metadata thực. Khi `proxyBeanMethods=true` (mặc định), full config class có thể được enhanced để inter-bean Java method calls tuân theo container semantics.

Trong project, `@Bean` methods chỉ nhận dependencies qua method parameters nên `proxyBeanMethods=false` phù hợp và tránh unnecessary proxying.

Tuy nhiên khi production config gọi trực tiếp các `@Bean` methods, đổi flag trong test có thể làm behavior khác production. Nguyên tắc **test đúng đường wiring được chạy trên production** quan trọng hơn một lời khuyên tối ưu hóa chung.

## 22. @ComponentScan có thuộc bài test không?

Nếu mục tiêu là **kiểm tra scanning**, test phải thực sự sử dụng component scan và xác nhận những `@Component` classes phù hợp được register.

Nếu mục tiêu là kiểm tra `@Bean` method parameters và interface resolution, explicit register configs là ít nhiễu hơn.

Không dùng một test không có component scan để kết luận package scanning production chắc chắn hoạt động. Hai contracts khác nhau, nên có thể có hai test slices khác nhau.

## 23. registerBean cho fake dependencies

Bên cạnh `register(Config.class)`, `AnnotationConfigApplicationContext` (qua GenericApplicationContext) hỗ trợ programmatic bean registration:

~~~java
context.registerBean(
    "pricePolicy",
    PricePolicy.class,
    () -> new FlatPricePolicy("test", 12));
~~~

Đây là snippet sử dụng classes của demo, gọi **trước refresh**.

Cách này thuận tiện khi chỉ cần một fake/stub nhỏ. Nhưng nếu mục tiêu test là chính `@Configuration` hoặc `@Profile` trên policy bean, việc thay config bằng registerBean có thể làm test bỏ qua feature cần xác nhận.

**Tối thiểu không có nghĩa thay đổi behavior dưới test thành đường code khác.**

## 24. So với @SpringJUnitConfig của Spring Test

Khi cần TestContext quản lý JUnit injection và cache, có thể viết:

~~~java
@SpringJUnitConfig(classes = {CoreConfig.class, TestPolicyConfig.class})
@ActiveProfiles("test")
class AnotherWiringTest {
    @Autowired CheckoutService checkout;

    @Test
    void hasService() {
        assertNotNull(checkout);
    }
}
~~~

Đây là snippet cần `spring-test`, JUnit Jupiter, test configuration và imports. Nó khác demo **manual context** của bài này: TestContext cung cấp `SpringExtension`, setup declarative và cache; manual context cho phép chủ động `register → refresh → assert → close`, dễ kiểm nhiều context/exception trong một method. Hai hướng đều tạo context thật. Hãy chọn theo mức độ tái sử dụng configuration và mục tiêu assertion; bài tiếp theo giải thích đầy đủ hai annotation TestContext.

## 25. Caching và rò rỉ trạng thái

`new AnnotationConfigApplicationContext(...)` trong `try-with-resources` tạo context riêng cho một test và đóng khi kết thúc. `@SpringJUnitConfig` thường dùng TestContext cache để tiết kiệm `refresh()`; singleton mutable trong cached context vì thế **có thể tồn tại qua nhiều methods/classes**.

Bài này ưu tiên isolation có ranh giới dễ nhìn ở demo nhỏ. Nếu cần reuse context trên suite lớn, dùng Spring TestContext và reset fixtures một cách deterministic; chỉ dùng `@DirtiesContext` khi context bị thay đổi không thể reset đáng tin. Bài cuối chương sẽ kiểm chứng cache key, các chế độ dirties và tác dụng của chúng bằng test riêng.

## 26. Vấn đề ngoài Spring container

Context mới không tự cô lập `System.properties`, static mutable state, JVM timezone/locale, files, DB, broker hoặc thread pools bên ngoài Spring. Chúng có thể khiến một wiring test bị nhiễu dù bean definitions được dựng mới hoàn toàn.

Trong ví dụ này, ưu tiên `MapPropertySource` trên `Environment` **của chính context** và fake adapters có ownership rõ. Nếu test bắt buộc sửa global property, lưu trạng thái cũ và phục hồi trong `finally`. TestContext caching cũng không giải quyết những nguồn state nằm ngoài container; phần isolation chuyên sâu trình bày chiến lược tương ứng.

## 27. Kiểm thử AOP, transaction và Boot-specific behavior

`AnnotationConfigApplicationContext` tối thiểu không tự kích hoạt mọi infrastructure:

- Transaction manager và `@EnableTransactionManagement`.
- Spring MVC DispatcherServlet.
- Security filters.
- JPA DataSource/EntityManager.
- Spring Boot auto-configurations.
- `@ConfigurationProperties` binding kiểu Boot.
- Async executors và schedulers.

Nếu test cần xác nhận `@Transactional` advice, hãy đăng ký transaction manager và proxy infrastructure phù hợp, hoặc dùng integration test scope lớn hơn. Chỉ có `@Transactional` annotation trên method **không bảo đảm transaction thật đã bắt đầu**.

Nếu cần test auto-config/web/JPA, một Boot slice/full test thường đúng mục đích hơn minimal container test.

## 28. Test thất bại: cách điều tra

1. Kiểm tra `refresh` có được gọi đúng một lần không.
2. Xác nhận configuration classes được register.
3. Xác nhận active profiles **trước refresh**.
4. Đọc nested root cause của BeanCreationException.
5. Dùng `containsBeanDefinition` cho application bean name.
6. Dùng `getBeansOfType` để nhận diện multiple candidates.
7. Nếu missing bean, quyết định test slice thiếu dependency hay production config sai.
8. Nếu circular dependency, sửa graph thay vì tạm bật allowCircularReferences.
9. Nếu bean lazy/prototype, kích hoạt `getBean` cần thiết.
10. Nếu vấn đề chỉ ở full app, bổ sung integration test cao hơn.

Không assert cố định toàn bộ exception message vì Spring minor versions có thể đổi wording.

## 29. Các lỗi phổ biến

| Hiện tượng | Nguyên nhân thường gặp |
| --- | --- |
| NoSuchBeanDefinitionException | Thiếu config, profile không match |
| NoUniqueBeanDefinitionException | Nhiều candidates theo type |
| BeanCurrentlyInCreationException | Constructor/setter dependency cycle |
| BeanCreationException | Factory/constructor/init failure |
| ScopeNotActiveException | Scope không hoạt động trong non-web context |
| Test passes nhưng production fail | Slice thiếu infrastructure/production configs |
| getBean trước refresh fail | Context chưa active |
| refresh lần hai fail | GenericApplicationContext chỉ refresh một lần |
| Dữ liệu lẫn giữa tests | Static/global state hoặc context reuse |
| Destroy callback không chạy | Chưa close hoặc scope khác singleton |
| @Value sai | Properties/placeholder infrastructure chưa đúng |
| @Profile không đúng | Chọn profile quá muộn |
| Scan quá nhiều beans | @ComponentScan scope quá rộng |
| Proxy advice không hoạt động | Thiếu AOP/transaction setup trong test |

## 30. Bài tập mở rộng

**Bài 1:** Thêm JUnit test quantity=0. Assert `IllegalArgumentException`, repository không lưu order mới.

**Bài 2:** Tạo context với `CoreConfig` và `TestPolicyConfig` nhưng đặt active profile prod. Assert lỗi missing PricePolicy tại refresh.

**Bài 3:** Đăng ký hai PricePolicy beans cùng profile, không primary. Quan sát exception khi CheckoutService cần một PricePolicy.

**Bài 4:** Dùng `@Primary` cho một candidate và kiểm tra CheckoutService thực sự dùng policy đúng.

**Bài 5:** Thêm `@Bean(initMethod="initialize", destroyMethod="markClosed")` và test lifecycle trước/ sau close.

**Bài 6:** Thử một context có `@ComponentScan` package hẹp để kiểm tra Spring tự phát hiện component type, đối chiếu explicit register.

**Bài 7:** Thêm `MapPropertySource` vào context trước refresh, inject property vào bean có `@Value` và assert giá trị.

Các bài tập yêu cầu chỉnh project; stdout tại mục 15.6 áp dụng cho source nguyên bản.

## 31. Checklist trước khi merge

- [ ] Context thực sự sử dụng AnnotationConfigApplicationContext?
- [ ] Chỉ register config classes cần thiết?
- [ ] Profiles được set trước refresh?
- [ ] Properties được set trước refresh nếu có?
- [ ] Test xác minh DI thực chứ không chỉ dùng new?
- [ ] Có positive test và negative missing-dependency test?
- [ ] Có test selection giữa các profiles?
- [ ] Không hard-code beanDefinitionCount?
- [ ] Có kiểm tra singleton identity?
- [ ] Có kiểm tra context isolation?
- [ ] Các contexts được close đúng?
- [ ] Lifecycle callbacks test theo đúng scope?
- [ ] Không kết nối production external systems?
- [ ] Không chia sẻ static mutable state?
- [ ] Test lazies/prototypes có chủ động gọi getBean?
- [ ] Đã phân biệt wiring test và unit test?
- [ ] Nếu cần Boot/AOP/transaction, đã có test layer phù hợp?
- [ ] JUnit 5 và Surefire discover đúng test class?
- [ ] Context code match production config semantics?
- [ ] Mục tiêu cần kiểm tra đã được assertions bảo vệ?

## 32. Kết luận

`AnnotationConfigApplicationContext` cho phép dựng một **Spring container thật nhưng nhỏ** để kiểm chứng beans được lắp ghép đúng. Khai báo environment/profile trước refresh, chỉ đăng ký config cần thiết, assert bean identity/type/behavior và đóng context sau test.

Nó giúp phát hiện những lỗi unit tests với mocks dễ bỏ sót: **missing/ambiguous dependencies, profile mismatch, @Bean factory wiring, singleton scope, isolation và lifecycle**.

Khi cần test fixtures được inject, context caching hay declarative transaction support, dùng Spring TestContext. Khi cần Spring Boot auto-configuration, MVC, JPA hoặc full application behavior, dùng layer integration thích hợp. Một minimal wiring test hữu ích khi **đúng phạm vi**, chứ không phải vì nó thay thế mọi kiểu kiểm thử khác.

## 33. Tài liệu tham khảo chính thức

- [Spring Framework 6.1 — AnnotationConfigApplicationContext](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/annotation/AnnotationConfigApplicationContext.html): constructors, register, refresh, close.
- [Spring Framework 6.1 — GenericApplicationContext](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/support/GenericApplicationContext.html): registration và one-refresh contract.
- [Spring Framework Reference — Java-based Container Configuration](https://docs.spring.io/spring-framework/reference/core/beans/java.html): @Configuration, @Bean và method injection.
- [Spring Framework Reference — Environment Abstraction](https://docs.spring.io/spring-framework/reference/core/beans/environment.html): active/default profiles và property sources.
- [Spring Framework Reference — TestContext Context Management](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management.html): integration context lifecycle.
- [Spring Framework Reference — Context Caching](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/caching.html): cache/reuse trade-off.
- [Spring Framework Reference — Environment Profiles in Tests](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/env-profiles.html): @ActiveProfiles.
- [Spring Framework 6.1 — BeanCreationException](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/beans/factory/BeanCreationException.html): dependency wiring failure.
- [JUnit 5 User Guide](https://docs.junit.org/5.11.1/user-guide/): Jupiter @Test, assertions, lifecycle.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="container-testing-003">Spring TestContext với @SpringJUnitConfig và @ContextConfiguration</a>

<details>
<summary>Xem chi tiết</summary>

# Spring TestContext với @SpringJUnitConfig và @ContextConfiguration

## 1. Tổng quan: kiểm thử container Spring thực sự

Một unit test thuần Java có thể kiểm tra một service bằng cách gọi `new Service(fakeRepository)`. Nhưng khi cần xác nhận **Spring Core Container** có thật sự đăng ký BeanDefinitions, chọn đúng candidates, inject dependencies, xử lý `@Configuration`, áp profile, đọc property sources và chạy lifecycle hay không, cần một `ApplicationContext` thật trong test. Spring TestContext Framework được thiết kế cho việc đó, tích hợp với JUnit Jupiter, JUnit 4 và TestNG mà không bắt test class phải kế thừa một base class Spring.

Trong JUnit Jupiter, hai kiểu khai báo phổ biến là:

~~~java
@SpringJUnitConfig(TestConfig.class)
class GreetingTests {
    // Các @Test methods...
}
~~~

và phiên bản tường minh tương đương:

~~~java
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class GreetingTests {
    // Các @Test methods...
}
~~~

**`@SpringJUnitConfig` là composed annotation kết hợp `@ExtendWith(SpringExtension.class)` của JUnit Jupiter và `@ContextConfiguration` của Spring TestContext.** Vì vậy cả hai đều có thể nạp `ApplicationContext` rồi inject bean vào test; khác biệt chủ yếu ở cách biểu diễn metadata và việc có sẵn `SpringExtension`.

Bài tập trung vào **Spring Framework 6.2.19, Java 17 và JUnit Jupiter 5**, không dựa vào tự động cấu hình của Spring Boot.

### Mục tiêu học tập

Sau khi hoàn thành, bạn có thể:

1. Giải thích chu trình `JUnit Jupiter → SpringExtension → TestContextManager → ApplicationContext`.
2. Chọn `@SpringJUnitConfig` hoặc cặp `@ExtendWith(SpringExtension.class) + @ContextConfiguration` đúng.
3. Hiểu `@ContextConfiguration(classes=...)`, `locations`, `initializers`, `loader`, `inheritLocations` và `inheritInitializers`.
4. Phân biệt annotation `value` của `@SpringJUnitConfig` với `value` của `@ContextConfiguration`.
5. Đăng ký test configuration bằng `@Configuration`/`@Bean` và dùng dependency injection vào field, constructor, method parameter.
6. Phối hợp `@ActiveProfiles`, `@TestPropertySource`, `@DynamicPropertySource` và thứ tự ưu tiên cấu hình.
7. Hiểu Spring TestContext **cache ApplicationContext** dựa trên cấu hình và khi nào dùng `@DirtiesContext`.
8. Tránh hiểu nhầm `@Transactional` và cleanup test data.
9. Tổ chức test suites để nhanh, độc lập, lặp lại ổn định trên CI.
10. Chạy dự án Maven mẫu với hai test classes, cùng một cấu hình Spring, **4 JUnit tests** và những assertion cụ thể.

## 2. Những thành phần tham gia

| Thành phần | Nhiệm vụ |
| --- | --- |
| JUnit Jupiter | Khám phá, lập lịch, chạy `@Test`/`@BeforeEach`/`@AfterEach` |
| `SpringExtension` | Cầu nối JUnit Jupiter và Spring TestContext |
| `TestContextManager` | Quản lý test context và gọi `TestExecutionListener` callbacks |
| `TestContext` | Giữ thông tin test class, instance, method, context metadata |
| `@ContextConfiguration` | Mô tả nguồn cấu hình `ApplicationContext` cho integration test |
| `@SpringJUnitConfig` | Gom SpringExtension với ContextConfiguration |
| `SmartContextLoader` | Nạp JavaConfig, XML hoặc nguồn cấu hình phù hợp |
| `MergedContextConfiguration` | Mô tả cấu hình đã hợp nhất, dùng trong context loading/cache |
| `ApplicationContext` | Spring IoC container thực sự được kiểm thử |
| `DependencyInjectionTestExecutionListener` | Injection beans vào test instances khi thích hợp |
| `@ActiveProfiles` | Chỉ định profiles dùng khi tạo test context |
| `@TestPropertySource` | Thêm properties/files dành riêng cho test |
| `@DirtiesContext` | Báo rằng context đã bị thay đổi cần bỏ khỏi cache |
| `@ContextHierarchy` | Khai báo nhiều context levels khi cần hierarchy |
| `@SpringJUnitWebConfig` | Biến thể composed annotation cho web context |

### Luồng hoạt động khái niệm

~~~text
Maven Surefire → JUnit Jupiter
                      │
                      ▼
             SpringExtension
                      │
                      ▼
             TestContextManager
                      │
          ┌───────────┴────────────┐
          │                        │
          ▼                        ▼
  MergedContextConfiguration   TestExecutionListeners
          │                        │
          ▼                        ├── prepare test instance
  Context cache lookup             ├── before/after test method
          │                        └── cleanup/context state
          ├── cache hit: reuse
          └── cache miss:
                 SmartContextLoader
                       ▼
               load/refresh ApplicationContext
                       ▼
                 inject test dependencies
                       ▼
                 invoke test methods
~~~

Sơ đồ không hàm ý Spring tạo một context mới cho **mỗi `@Test`**. Trên thực tế, khả năng cache context là một lợi ích lớn khi kiểm thử nhiều classes có cấu hình giống nhau.

## 3. @SpringJUnitConfig: hai annotations thành một

Một JUnit Jupiter test có thể viết:

~~~java
import org.junit.jupiter.api.Test;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(TestConfig.class)
class ServiceIntegrationTests {

    @Test
    void usesSpringContainer() {
        // Assertions...
    }
}
~~~

`@SpringJUnitConfig(TestConfig.class)` gồm:

- `@ExtendWith(SpringExtension.class)`: đăng ký JUnit Jupiter extension hỗ trợ Spring.
- `@ContextConfiguration(classes = TestConfig.class)`: chọn Java configuration class để tạo context.

Không cần thêm lần nữa `@ExtendWith(SpringExtension.class)` chỉ để “chắc chắn Spring hoạt động”. Việc khai báo trùng không giúp test chính xác hơn và có thể gây nhiễu khi review. Tương tự, test class không phải extend `AbstractJUnit4SpringContextTests`: đó là JUnit 4-oriented support class, không cần cho JUnit Jupiter.

### Cú pháp giá trị ngắn gọn

~~~java
@SpringJUnitConfig(TestConfig.class)
class ExampleTests { }
~~~

tương đương về mục đích với:

~~~java
@SpringJUnitConfig(classes = TestConfig.class)
class ExampleTests { }
~~~

Nhưng **đừng nhầm thuộc tính `value` giữa hai annotations**:

| Annotation | `value` alias tới | Ví dụ |
| --- | --- | --- |
| `@SpringJUnitConfig` | `classes` | `@SpringJUnitConfig(TestConfig.class)` |
| `@ContextConfiguration` | `locations` | `@ContextConfiguration("classpath:example/context.xml")` |

Vì vậy `@ContextConfiguration(TestConfig.class)` **không phải cú pháp thay thế hợp lệ** cho `classes = TestConfig.class`. Khi dùng `@ContextConfiguration` trực tiếp, chỉ định `classes` tường minh cho Java configuration class.

### Khi nào dùng @SpringJUnitConfig?

- Test thuần Spring Framework với JUnit Jupiter.
- Muốn giảm boilerplate cho JavaConfig integration tests.
- Test class có thể cần injection từ container.
- Test configuration metadata được thể hiện rõ ngay trên class.
- Không cần xử lý đặc biệt khiến việc ghi `@ExtendWith` riêng trở nên có ích.

Cả hai cách được hỗ trợ; chọn một convention thống nhất để đọc code dễ hơn.

## 4. @ContextConfiguration có nhiệm vụ gì?

`@ContextConfiguration` mô tả cách Spring TestContext **nạp ApplicationContext cho test**. Annotation này được Spring TestContext xử lý; nó **không tự đăng ký JUnit Jupiter extension**.

~~~java
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ServiceTestConfig.class)
class ServiceTests {
}
~~~

Nếu thiếu `@ExtendWith(SpringExtension.class)` (hoặc một composed annotation/extension registration khác có hiệu lực), JUnit Jupiter không tự sử dụng Spring TestContext chỉ vì thấy `@ContextConfiguration`. Field `@Autowired` trên test class có thể không được inject và `@Test` methods chạy bằng JUnit thuần.

### Các thuộc tính chính

| Thuộc tính | Ý nghĩa |
| --- | --- |
| `classes` | Mảng component/configuration classes, thường có `@Configuration` |
| `locations` | Resource locations, thường XML hoặc Groovy configuration files |
| `value` | Alias của `locations` |
| `initializers` | Các `ApplicationContextInitializer` classes |
| `loader` | Loại `ContextLoader` hoặc `SmartContextLoader` được sử dụng |
| `inheritLocations` | Có kế thừa classes/locations từ test superclasses hoặc enclosing classes hay không |
| `inheritInitializers` | Có kế thừa initializers từ superclasses/enclosing classes hay không |
| `name` | Tên level trong `@ContextHierarchy` khi dùng phân cấp |

Các defaults và khả năng phối hợp phụ thuộc `ContextLoader`. Với loader mặc định hiện đại, `DelegatingSmartContextLoader` có thể xử lý JavaConfig/XML tương ứng. Với web context, Spring dùng bộ loader thích hợp khi có `@WebAppConfiguration`.

### Không nhầm annotation với Spring @Configuration

- `@Configuration` nằm trên class **cấu hình Spring beans**.
- `@ContextConfiguration` nằm trên **test class**, chỉ cho TestContext cách dựng context.
- `@SpringJUnitConfig` nằm trên test class, kết hợp nguồn context với JUnit extension.
- `@Import` nằm trên Spring configuration class để include bean configurations.
- `@TestConfiguration` là annotation có trong Spring Boot testing, **không phải điều kiện bắt buộc của Spring Framework Core**.

## 5. Cách nạp context từ JavaConfig

~~~java
@Configuration(proxyBeanMethods = false)
class TestConfig {

    @Bean
    Clock clock() {
        return Clock.fixed(
                Instant.parse("2026-01-01T00:00:00Z"),
                ZoneOffset.UTC);
    }

    @Bean
    OrderService orderService(Clock clock) {
        return new OrderService(clock);
    }
}
~~~

Đây là snippet; cần imports `Clock`, `Instant`, `ZoneOffset`, `@Bean`, `@Configuration` và class `OrderService`. Khi test class khai báo:

~~~java
@SpringJUnitConfig(classes = TestConfig.class)
class OrderServiceTests {

    @Autowired OrderService service;

    @Test
    void usesFixedClock() {
        // Assertions dựa vào Clock cố định.
    }
}
~~~

TestContext tạo context chứa `Clock` và `OrderService`; Spring resolve `Clock` khi gọi `@Bean orderService(Clock clock)`.

### Dùng nhiều classes

~~~java
@ContextConfiguration(
    classes = {InfrastructureConfig.class, ServiceConfig.class})
~~~

Tập classes được dùng tạo **một context** với cả hai cấu hình, không tạo hai contexts cha-con riêng. Nếu cần hierarchy, dùng `@ContextHierarchy` một cách rõ ràng.

### Không cần component scanning

Nếu chỉ dùng `@Bean` methods, không cần `@ComponentScan`. Điều này giúp test fixture nhỏ và tránh vô tình load mọi beans của production application.

### Default detection

Nếu bỏ cả `classes` và `locations`, Spring TestContext có thể thử phát hiện static nested `@Configuration` class hoặc các default configuration resource theo convention. Cách này có lợi cho test nhanh nhưng dễ nhầm khi refactor; test quan trọng nên ghi explicit `classes` để tránh đổi configuration không chủ ý.

## 6. Nạp XML/resource locations khi cần

Với một dự án có XML configuration:

~~~java
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:example/test-beans.xml")
class LegacyIntegrationTests {
    // ...
}
~~~

Đây là snippet; cần file `src/test/resources/example/test-beans.xml` và BeanDefinitions hợp lệ.

Một XML tối thiểu có thể:

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">
    <bean id="clock"
          class="java.time.Clock"
          factory-method="systemUTC"/>
</beans>
~~~

Khi dùng loader mặc định phù hợp với `locations`, test context nạp bean `clock` từ XML. Đừng nhầm `@ContextConfiguration(value = "...")` (alias cho `locations`) với `@SpringJUnitConfig(value = TestConfig.class)` (alias cho `classes`).

### JavaConfig hay XML?

- Dùng `classes` khi beans được khai báo qua `@Configuration` hoặc annotated component classes.
- Dùng `locations` khi đang kiểm thử XML legacy hoặc tài nguyên cấu hình được hỗ trợ.
- Không trộn hai loại theo một loader bất kỳ rồi giả định nó luôn biết cách load; hãy dùng `SmartContextLoader` phù hợp và kiểm tra docs của loader.
- Không dùng production context rộng chỉ để test một bean nhỏ nếu có thể dựng configuration rút gọn chính xác.

## 7. SpringExtension và JUnit Jupiter lifecycle

SpringExtension hỗ trợ các Jupiter extension contracts cho test instance preparation, parameter resolution và lifecycle callback integration.

~~~text
JUnit tạo test instance
        |
        v
SpringExtension / TestContextManager
        |
        +-- tra hoặc load ApplicationContext
        +-- inject dependencies theo TestExecutionListeners
        |
        v
@BeforeEach
        |
        v
@Test method
        |
        v
@AfterEach
        |
        v
Spring test callbacks / cache management
~~~

Sơ đồ này chỉ là phần nhìn đơn giản; các before/after callbacks của `TestExecutionListener` có thể diễn ra tại nhiều mốc quanh test method/execution. Không tự suy thứ tự chính xác giữa tất cả Jupiter extensions từ sơ đồ minh họa.

### TestExecutionListener là gì?

TestContext có các `TestExecutionListener` mặc định cho dependency injection, transaction handling, SQL scripts, test context dirtying và những concerns khác nếu classpath/support được cung cấp.

Nếu tùy biến `@TestExecutionListeners`, hãy hiểu thuộc tính `mergeMode`. Một khai báo tùy biến không cẩn thận có thể **thay thế** default listeners và làm `@Autowired` vào test instance hoặc transaction semantics không còn như mong đợi.

### @BeforeAll và @BeforeEach

JUnit Jupiter mặc định tạo một test instance **mỗi test method** (`PER_METHOD`). Field injection do SpringExtension xảy ra theo lifecycle phù hợp. Nếu dùng `@TestInstance(PER_CLASS)`, cùng test instance được tái sử dụng; cần cẩn thận với mutable state, `@DirtiesContext` và constructor injection.

## 8. Dependency injection vào test instance

Một cách phổ biến:

~~~java
@SpringJUnitConfig(TestConfig.class)
class GreetingTests {

    @Autowired
    GreetingService greetingService;

    @Test
    void greets() {
        assertEquals("Hello, Lan", greetingService.greet("Lan"));
    }
}
~~~

Đây là snippet với `GreetingService`/imports phù hợp.

**`@Autowired` trên test field không nhất thiết đồng nghĩa production bean đang bị field injection.** Đây là injection vào **test instance** do TestContext hỗ trợ. Trong production, constructor injection vẫn thường rõ ràng hơn.

### Injection vào test method parameter

~~~java
@Test
void greets(@Autowired GreetingService service) {
    assertEquals("Hello, Lan", service.greet("Lan"));
}
~~~

SpringExtension cung cấp `ParameterResolver` cho annotated parameters hoặc types được hỗ trợ. Method parameter injection tránh lưu bean reference lâu hơn mức cần thiết.

### ApplicationContext parameter

~~~java
@Test
void hasContext(@Autowired ApplicationContext context) {
    assertNotNull(context.getBean(GreetingService.class));
}
~~~

SpringExtension hỗ trợ injection cho `ApplicationContext` và các kiểu liên quan. Ghi `@Autowired` explicit có thể làm mục đích rõ ràng hơn trong ví dụ.

### Constructor injection của test class

Có thể dùng:

~~~java
@SpringJUnitConfig(TestConfig.class)
class ConstructorInjectedTests {

    private final GreetingService service;

    @Autowired
    ConstructorInjectedTests(GreetingService service) {
        this.service = service;
    }

    @Test
    void greets() {
        // ...
    }
}
~~~

`@TestConstructor(autowireMode = ALL)` là một cách khác để yêu cầu Spring autowire toàn bộ constructor, tùy cấu hình/annotation của Spring TestContext.

**Cẩn trọng:** Khi một test constructor được Spring quản lý toàn bộ, các `ParameterResolver` khác (JUnit `TestInfo`, `RepetitionInfo`, extensions tùy biến) có thể không còn xử lý các tham số của constructor đó theo cách bạn mong. Đừng trộn các chiến lược constructor injection vô kế hoạch.

## 9. Ví dụ đầy đủ: Java 17 + Spring TestContext + JUnit Jupiter

Đây là dự án Maven tối thiểu chạy được với **Java 17**, **Spring Framework 6.2.19**, **JUnit Jupiter 5.12.2** và Maven Surefire. Không Spring Boot, database hoặc mock framework.

Sẽ có **hai JUnit test classes** mô tả **cùng một Spring configuration**:

- `GreetingSpringJUnitConfigTests` dùng `@SpringJUnitConfig(TestConfig.class)`.
- `GreetingContextConfigurationTests` dùng `@ExtendWith(SpringExtension.class)` + `@ContextConfiguration(classes = TestConfig.class)`.
- Cả hai có `@ActiveProfiles("test")` và `@TestPropertySource(properties = "greeting.prefix=Xin chào")`.
- Bean `GreetingService` nhận prefix từ `Environment` khi được tạo.
- Tổng cộng **bốn `@Test` methods** kiểm tra greeting, profile, field injection, method parameter injection và context lookup.

### Cấu trúc thư mục

~~~text
spring-testcontext-demo/
├── pom.xml
└── src/
    ├── main/
    │   └── java/
    │       └── example/
    │           └── GreetingService.java
    └── test/
        └── java/
            └── example/
                ├── TestConfig.java
                ├── GreetingSpringJUnitConfigTests.java
                └── GreetingContextConfigurationTests.java
~~~

### pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-testcontext-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.19</version>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-test</artifactId>
            <version>6.2.19</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.12.2</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.3</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

**Vì sao cần spring-test?** `SpringExtension`, `@SpringJUnitConfig`, `@ContextConfiguration`, TestContextManager và các listeners nằm trong module `spring-test`. Chỉ thêm `spring-context` sẽ không đưa các test-support classes đó vào classpath.

**Vì sao cần JUnit Jupiter?** `@Test`/`@ExtendWith` và các assertions/extension contracts nằm trong JUnit Jupiter. Maven Surefire chạy JUnit Platform provider phù hợp khi dependencies/plugin hỗ trợ.

### src/main/java/example/GreetingService.java

~~~java
package example;

import java.util.Objects;

public final class GreetingService {

    private final String prefix;

    public GreetingService(String prefix) {
        this.prefix = Objects.requireNonNull(prefix);
    }

    public String greet(String name) {
        return prefix + ", " + Objects.requireNonNull(name);
    }
}
~~~

Class này **không bắt buộc import Spring**. Đây là một lợi ích của dependency injection: business class có thể thuần Java, còn wiring thuộc `@Configuration`.

### src/test/java/example/TestConfig.java

~~~java
package example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class TestConfig {

    @Bean
    public GreetingService greetingService(Environment environment) {
        String prefix = environment.getProperty(
                "greeting.prefix", "Hello");
        return new GreetingService(prefix);
    }
}
~~~

`TestConfig` nằm trong `src/test/java`, vì chỉ cần cho tests. `Environment` được Spring context cung cấp; `@TestPropertySource` đã thêm key `greeting.prefix` trước khi bean được tạo. Bean là singleton theo default.

### src/test/java/example/GreetingSpringJUnitConfigTests.java

~~~java
package example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringJUnitConfig(TestConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "greeting.prefix=Xin chào")
class GreetingSpringJUnitConfigTests {

    @Autowired
    GreetingService service;

    @Autowired
    ApplicationContext context;

    @Test
    void injectsServiceAndUsesTestProperty() {
        assertEquals("Xin chào, Lan", service.greet("Lan"));
        assertSame(service, context.getBean(GreetingService.class));
    }

    @Test
    void activatesTestProfileAndEnvironmentProperty() {
        Environment environment = context.getEnvironment();
        assertTrue(environment.acceptsProfiles("test"));
        assertEquals("Xin chào",
                environment.getProperty("greeting.prefix"));
    }
}
~~~

Đây là hai `@Test` methods. `@SpringJUnitConfig` đã đăng ký `SpringExtension`; không cần `@ExtendWith(SpringExtension.class)` thêm lần nữa.

### src/test/java/example/GreetingContextConfigurationTests.java

~~~java
package example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "greeting.prefix=Xin chào")
class GreetingContextConfigurationTests {

    @Autowired
    GreetingService service;

    @Test
    void injectsBeanViaExplicitExtension() {
        assertNotNull(service);
        assertEquals("Xin chào, Minh", service.greet("Minh"));
    }

    @Test
    void injectsMethodParameters(
            @Autowired GreetingService parameterService,
            @Autowired ApplicationContext context) {

        assertSame(service, parameterService);
        assertSame(service, context.getBean(GreetingService.class));
    }
}
~~~

Test class thứ hai là cách viết tường minh với cùng Spring configuration. `@Autowired` trên Jupiter test method parameters được SpringExtension hỗ trợ thông qua parameter resolution.

### Chạy kiểm thử

~~~bash
mvn test
~~~

Để chạy từng class:

~~~bash
mvn -Dtest=GreetingSpringJUnitConfigTests test
mvn -Dtest=GreetingContextConfigurationTests test
~~~

### Kết quả kiểm chứng

Nếu Maven tải được các dependencies và chạy JDK 17 tương thích, **kỳ vọng**:

~~~text
GreetingSpringJUnitConfigTests: 2 tests passed
GreetingContextConfigurationTests: 2 tests passed
Total: 4 tests, 0 failures, 0 errors, 0 skipped
~~~

Đây là **tóm tắt logic của kết quả mong đợi**, không phải một trích dẫn nguyên văn cố định từ console Surefire. Surefire có thể in theo từng class, thay đổi thứ tự tests, plugin/version và log output. Các file `target/surefire-reports/*.txt` hoặc `*.xml` xác nhận số lượng thực tế.

### Giải thích vì sao bốn tests pass

1. Test class đầu dùng `@SpringJUnitConfig(TestConfig.class)` nên JUnit đăng ký SpringExtension và Spring biết source JavaConfig.
2. Test class thứ hai dùng cặp annotations tường minh, có hiệu quả tương ứng.
3. `TestConfig` khai báo một bean `GreetingService` lấy prefix từ Spring `Environment`.
4. `@TestPropertySource` của cả hai test classes thêm `greeting.prefix=Xin chào` vào child test environment với precedence cao.
5. `@ActiveProfiles("test")` khiến active profile `test` hiện diện và assertion `acceptsProfiles("test")` đúng.
6. `DependencyInjectionTestExecutionListener` inject service/context vào test instances.
7. Jupiter method injection dùng SpringExtension để resolve `GreetingService` và `ApplicationContext`.
8. Default singleton scope làm `context.getBean(GreetingService.class)` trỏ cùng instance đang injected trong test cùng context.
9. Cả hai tests cùng metadata cấu hình nên TestContext **có thể tái sử dụng** một cached `ApplicationContext` trong cùng test process, nhưng ví dụ **không assert** identity của contexts giữa hai classes vì đó không phải nghiệp vụ và phụ thuộc cách test runner/cache được cấu hình.
10. Code không cần Spring Boot; `spring-test` và `spring-context` là đủ cho loại Core Container test này.

## 10. So sánh @SpringJUnitConfig và @ContextConfiguration

| Khía cạnh | @SpringJUnitConfig | @ContextConfiguration |
| --- | --- | --- |
| Có sẵn SpringExtension cho Jupiter | Có, là composed annotation | Không, cần extension registration khác |
| Chỉ định JavaConfig classes | `classes` hoặc `value` | `classes` |
| Chỉ định XML/resource locations | `locations` | `locations` hoặc `value` |
| Chỉ định context initializers | Có | Có |
| Tùy chọn inheritLocations | Có | Có |
| Tùy chọn inheritInitializers | Có | Có |
| Tùy chọn context loader | Có | Có |
| Dùng với JUnit Jupiter | Thiết kế trực tiếp cho Jupiter | Dùng với SpringExtension |
| Dùng trong TestNG hoặc JUnit 4 | Không phải composed annotation phù hợp | Annotation cấu hình generic của TestContext |
| Phạm vi bean graph | Theo context được load | Theo context được load |
| Hỗ trợ cache/TestPropertySource | Có qua TestContext | Có qua TestContext |

**Chọn `@SpringJUnitConfig` làm mặc định cho Spring Framework + JUnit Jupiter**, trừ khi team có custom composed annotations hoặc cần biểu đạt extension registration tường minh để phối hợp các extensions đặc thù.

### Cú pháp sai thường thấy

~~~java
@ContextConfiguration(TestConfig.class) // Sai: value là resource locations.
class BadTest { }
~~~

~~~java
@ContextConfiguration(classes = TestConfig.class)
class MissingExtensionTest {
    @Autowired GreetingService service;
}
~~~

Ở trường hợp thứ hai, annotation không đủ để JUnit Jupiter kích hoạt Spring TestContext. Nếu không có `SpringExtension` từ nơi khác, field có thể vẫn null.

~~~java
@SpringJUnitConfig(TestConfig.class)
@ExtendWith(SpringExtension.class) // Thừa trong hầu hết trường hợp.
class RedundantTest { }
~~~

Không cần trùng extension declaration chỉ vì muốn nhấn mạnh sử dụng Spring.

## 11. @ContextConfiguration với ApplicationContextInitializer

`@ContextConfiguration(initializers=...)` cho phép can thiệp vào `ConfigurableApplicationContext` **trước khi refresh**. Đây là một điểm mở rộng thích hợp để đưa property source đặc biệt, cấu hình profile hoặc đăng ký metadata trước bean instantiation.

Ví dụ:

~~~java
import java.util.Map;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

public final class SpecialInitializer implements
        ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource(
                        "specialTestSettings",
                        Map.<String, Object>of(
                                "greeting.prefix", "Chào thử nghiệm")));
    }
}
~~~

Dùng với:

~~~java
@SpringJUnitConfig(
        classes = TestConfig.class,
        initializers = SpecialInitializer.class)
class InitializedContextTests {
}
~~~

**Đây là ví dụ mở rộng riêng**, không thuộc bốn `@Test` methods ở mục 9. Khi bạn đặt một initializer mới, cấu hình context/cache key có thể khác; test nào có `@TestPropertySource` inlined cùng property name còn chịu precedence của test properties.

### Initializer khác BeanFactoryPostProcessor

- `ApplicationContextInitializer` nhận `ConfigurableApplicationContext` và can thiệp trước refresh.
- `BeanFactoryPostProcessor` có nhiệm vụ can thiệp BeanDefinitions sau khi chúng đã đăng ký và trước ordinary singleton instantiation.
- `BeanPostProcessor` can thiệp bean instances khi chúng được tạo/khởi tạo.

Không gọi bất kỳ hook nào trong ba loại này là “before all” của JUnit; đó là những lifecycle layers khác nhau.

### Thứ tự initializers

Nếu có nhiều initializers, Spring có thể sắp xếp theo `Ordered`/`@Order` trước khi gọi. Nếu hai initializers đều sửa một property source, thứ tự có thể ảnh hưởng kết quả. Đừng dựa vào thứ tự khai báo class nếu semantics của extension không hứa điều đó.

## 12. @ActiveProfiles: chọn đúng BeanDefinitions

Spring `@Profile` và `@ActiveProfiles` làm việc với `Environment`:

~~~java
@Configuration
class ProfiledConfig {

    @Bean
    @Profile("test")
    Gateway gateway() {
        return new InMemoryGateway();
    }

    @Bean
    @Profile("prod")
    Gateway productionGateway() {
        return new RemoteGateway();
    }
}
~~~

Đây là snippet; cần interface và implementations tương ứng. Khi test class mang `@ActiveProfiles("test")`, `gateway()` được đăng ký thay vì bean chỉ active ở `prod`.

### Profile không phải JUnit tag

- `@ActiveProfiles("test")` điều chỉnh Spring `Environment` dùng để chọn beans.
- `@Tag("integration")` là JUnit Jupiter metadata cho test selection/filtering.
- `@Disabled`/`@EnabledIf` điều chỉnh execution, không mặc định đổi active Spring profiles.
- `@TestPropertySource(properties=...)` cung cấp values; không tự biến string `"test"` thành active profile.

### Profile có trong cache key

Hai test classes có `@ActiveProfiles("test")` và `@ActiveProfiles("prod")` **không nên giả định dùng chung ApplicationContext**. Active profiles là một phần của cache key và tạo bean graphs có thể khác nhau.

## 13. @TestPropertySource và ưu tiên cấu hình

~~~java
@SpringJUnitConfig(TestConfig.class)
@TestPropertySource(properties = "greeting.prefix=Xin chào")
class PropertyTests { }
~~~

`@TestPropertySource` có thể khai báo:

- `locations`/`value` trỏ tới file test properties.
- `properties` là các cặp key-value inline.
- `inheritLocations` và `inheritProperties` để điều khiển kế thừa trong test hierarchy.
- `factory`/`encoding` và các options khác tùy Spring version.

### Thứ tự ưu tiên

Theo Spring Framework TestContext Reference 6.2:

1. Properties từ `@DynamicPropertySource` có precedence cao hơn `@TestPropertySource`.
2. Inline `@TestPropertySource(properties=...)` có precedence cao hơn `@TestPropertySource(locations=...)`.
3. Test properties thường có precedence cao hơn system/environment properties và sources do application nạp bằng `@PropertySource`.

~~~text
Higher precedence
  @DynamicPropertySource
  inline @TestPropertySource(properties = ...)
  file @TestPropertySource(locations = ...)
  system/application property sources (tùy chain)
Lower precedence
~~~

Đây là quan hệ ưu tiên thường dùng trong TestContext; implementation/loaders tùy biến có thể mở rộng chain. Đừng áp một quy tắc từ Spring Boot `application.yml` lên plain Spring Framework mà không xem nguồn runtime thực tế.

### Đọc từ file

~~~java
@TestPropertySource(
        locations = "classpath:example/integration-test.properties")
~~~

và file `src/test/resources/example/integration-test.properties`:

~~~properties
greeting.prefix=Xin chào
service.timeout=1500
~~~

Nếu khai báo `@TestPropertySource` không args, Spring có thể tìm default test-specific properties theo convention; nếu không có file phù hợp, lỗi có thể xảy ra. Khi cần reproducibility, chỉ định path rõ ràng.

### Vì sao @Value có thể khác kết quả Environment.getProperty?

`Environment.getProperty(...)` đọc resolver tại thời điểm gọi. Một `@Value("${greeting.prefix}")` trên singleton thường resolve lúc bean được tạo; thay property nguồn sau refresh không tự re-inject field. Test config phải được thiết lập trước context load nếu kỳ vọng ảnh hưởng bean definitions và singleton values.

## 14. @DynamicPropertySource cho giá trị chỉ có khi runtime test bắt đầu

Một tích hợp dùng Testcontainers có thể không biết port trước khi container thử nghiệm khởi chạy. Spring TestContext hỗ trợ:

~~~java
@DynamicPropertySource
static void serviceProperties(DynamicPropertyRegistry registry) {
    registry.add("service.host", () -> "localhost");
    registry.add("service.port", () -> 54321);
}
~~~

Đây là snippet tĩnh với values minh họa; cần import `DynamicPropertySource`, `DynamicPropertyRegistry`, và host/port trong ứng dụng thật lấy từ runtime resource.

Các methods `@DynamicPropertySource` phải `static`, có một parameter `DynamicPropertyRegistry`; values thường cung cấp qua `Supplier<?>`. Phương thức này **không** tự start container bên ngoài, không đồng nghĩa `@ContextConfiguration`.

### Cache implications

Dynamic property contributors có thể tham gia `contextCustomizers` của TestContext cache key. Khi subclasses dùng chung method dynamic source nhưng actual values khác nhau, cache reuse có thể làm thấy stale resources; cân nhắc `@DirtiesContext` hoặc tách configuration/caching policies rõ.

### Tránh hardcoded random ports trong test

Nếu port được cấp tự động, lấy từ container/server đã khởi chạy. Một số demo cứng port để minh họa API, nhưng production tests phải chịu được chạy song song và CI với port collisions.

## 15. Context caching: vì sao các tests không refresh cho mỗi method?

`SpringExtension` sử dụng cache `ApplicationContext` tĩnh **theo JVM process**; nếu hai test classes có cùng `MergedContextConfiguration` và entry còn trong cache, chúng có thể dùng lại context. Key phụ thuộc nhiều yếu tố chứ không chỉ `@Configuration` class: `locations/classes`, context initializers/customizers, loader, hierarchy, `@ActiveProfiles`, `@TestPropertySource` và web resource base path.

Vì vậy hai test classes ở mục 9 có thể chia sẻ bean instances khi metadata tương đương, nhưng một class đổi active profile hoặc inline property thường sẽ nhận cache key khác. Cache mặc định Spring 6.2 có giới hạn 32 contexts theo cơ chế LRU và không chia sẻ qua forked JVMs. Để debug, bật logger `org.springframework.test.context.cache` ở DEBUG; **assert behavior của beans**, đừng làm một test phụ thuộc cache hit count. Bài `@DirtiesContext` chuyên sâu minh họa cache hit/eviction và giới hạn isolation.

## 16. @DirtiesContext: khi test làm bẩn container

~~~java
@SpringJUnitConfig(TestConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MutatingConfigurationTests {
    // Test này thật sự làm thay đổi context không thể reset
}
~~~

`@DirtiesContext` báo TestContext loại cached context và đóng nó khi tới mốc đã chọn. Dùng cho test sửa BeanDefinitions, lifecycle infrastructure hoặc singleton state không thể phục hồi an toàn; **đừng đánh dấu mọi class** chỉ vì một test sửa một fixture có thể reset trong `@BeforeEach`/`@AfterEach`.

Với annotation ở cấp class, mặc định là `AFTER_CLASS`; ở method mặc định `AFTER_METHOD`. Cả BEFORE modes và các mode cho hierarchy đều có sẵn. Nếu dùng JUnit `@TestInstance(PER_CLASS)` và một context bị dirtied giữa methods, constructor-injected `final` fields có thể còn trỏ vào beans của context đã đóng; với cấu hình này, Spring khuyến nghị field/setter injection để có thể re-inject. Bài cuối chương phân tích mode, shared state, test parallel và isolation ở độ sâu cần thiết.

## 17. Test instance vs Spring bean: hai khái niệm riêng

JUnit Jupiter tạo **test class instance** và gọi methods. `TestConfig` đăng ký **Spring beans**. Mặc định test class bản thân không phải một `@Component` bean; SpringExtension thực hiện injection vào test instance qua TestContext listeners.

~~~text
JUnit test instance
  GreetingSpringJUnitConfigTests
      ├─ field service ------► Spring bean GreetingService
      └─ field context ------► cached ApplicationContext

Spring ApplicationContext
      └─ BeanDefinition greetingService
            └─ singleton GreetingService
~~~

### Vòng đời khác nhau

- JUnit test instance thường PER_METHOD.
- Spring singleton bean thường sống theo cached context qua nhiều test methods/classes.
- `@BeforeEach` chạy mỗi test method; constructor `GreetingService` không nhất thiết chạy mỗi test method.
- Một field mutable của Spring singleton có thể lưu state từ test trước nếu không reset; test isolation phải được chủ động thiết kế.
- `@DirtiesContext` thay context cache state, không phải annotation yêu cầu Jupiter tạo test instance mới.

### Không gọi close trên cached context

Khi `@Autowired ApplicationContext` vào test, **không tự `((ConfigurableApplicationContext)context).close()`** trong `@AfterEach` để “dọn dẹp”. Context thuộc lifecycle manager/cache của TestContext, có thể còn được các tests khác sử dụng. Dùng `@DirtiesContext` đúng khi cần.

## 18. Kế thừa @ContextConfiguration và các test hierarchies

Một test base class có thể khai báo common configuration:

~~~java
@ContextConfiguration(classes = SharedTestConfig.class)
abstract class AbstractIntegrationTests {
}

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = FeatureTestConfig.class)
class FeatureIntegrationTests extends AbstractIntegrationTests {
}
~~~

Mặc định `inheritLocations = true`: component classes hoặc resource locations từ superclass/enclosing class có thể được kế thừa và mở rộng bởi test subclass. Nếu muốn thay thế hoàn toàn configuration class/location inheritance:

~~~java
@ContextConfiguration(
    classes = StandaloneTestConfig.class,
    inheritLocations = false)
~~~

### Context hierarchy khác class inheritance

Kế thừa Java test class configuration không đồng nghĩa tạo context cha-con. Nếu muốn thật sự có root và child `ApplicationContext`, dùng `@ContextHierarchy` và đặt `@ContextConfiguration(name=..., classes=...)` cho từng cấp:

~~~java
@ContextHierarchy({
    @ContextConfiguration(
            name = "root", classes = RootConfig.class),
    @ContextConfiguration(
            name = "child", classes = FeatureConfig.class)
})
~~~

Đây là snippet để đặt trên test class, cần hai config classes. TestContext quản lý context hierarchy đúng nghĩa, với bean lookup child→parent và event/lifecycle semantics tương ứng. Không dùng `classes={RootConfig.class, FeatureConfig.class}` rồi tự gọi đó là hierarchy; tập classes đó nạp một context.

### Initializer inheritance

`inheritInitializers = true` mặc định cho phép nối initializers từ base test và subclass. Điều này độc lập với `inheritLocations`. Khi initializers sửa các properties cùng tên, cần test expected precedence/order, đặc biệt khi custom initializers dùng `@Order` hoặc `Ordered`.

## 19. @Nested tests và enclosing configuration

JUnit Jupiter `@Nested` classes được Spring TestContext hỗ trợ kế thừa configuration từ enclosing test class theo default `INHERIT`, có thể thay bằng:

~~~java
@NestedTestConfiguration(
        NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
~~~

Cú pháp trên là snippet với import `NestedTestConfiguration`. Khi dùng nested classes:

- Biết liệu child nested class kế thừa `@SpringJUnitConfig`/`@ContextConfiguration`, profiles và test properties không.
- Xem `@NestedTestConfiguration` để kiểm soát INHERIT/OVERRIDE.
- Nếu mỗi nested class có profile khác, có thể tạo các context cache keys khác nhau.
- Không suy rằng một static nested `@Configuration` nằm trong test class luôn chỉ được dùng khi mình mong; default configuration detection có convention riêng.

### Test nested class không phải ApplicationContext child

Tên “nested” trong JUnit nói tới **Java test class nesting**. Nó không tự tạo `@ContextHierarchy` parent-child containers. Đây là hai loại hierarchy khác nhau.

## 20. Test bean overrides: tránh động chạm production config

Spring Framework 6.2 có hỗ trợ override test beans như `@TestBean`, `@MockitoBean` và `@MockitoSpyBean` trong `spring-test`, giúp thay collaborator thật bằng implementation hoặc mock dùng cho kiểm thử.

Ví dụ ý tưởng:

~~~java
@SpringJUnitConfig(TestConfig.class)
class CheckoutTests {

    @MockitoBean
    PaymentGateway paymentGateway;

    // ...
}
~~~

Đây là snippet cần `PaymentGateway` interface và Mockito dependencies cần thiết. Không phải một phần của Maven demo vì demo cố ý **không cài Mockito**.

### Cache context khi override beans

Test bean overrides có thể tham gia `contextCustomizers` của cache key. Hai tests cùng `classes=TestConfig.class` nhưng mock/override declarations khác nhau có thể **không dùng cùng cached context**. Không gộp chúng một cách máy móc.

### Khi nào dùng @TestBean?

Khi cần thay bean mà không muốn load network/database collaborator thật, có thể dùng `@TestBean` với factory method của test. Hãy đọc kỹ bean name, enforce override rules và wiring expectation; không dùng arbitrary `registerSingleton` sau refresh để sửa cấu hình rồi mong TestContext tự biết invalidating cache.

### Mock không chứng minh toàn bộ integration hoạt động

Mock một `PaymentGateway` kiểm tra wiring/logic của service quanh port, không xác nhận provider thật có HTTP/timeout/serialization behavior đúng. Có thể kết hợp module-level tests dùng mocks với ít integration tests chạy real adapters.

## 21. Transactional tests và @Rollback

TestContext cũng tích hợp transaction support khi application có `PlatformTransactionManager` và appropriate listeners/configuration.

~~~java
@SpringJUnitConfig(DatabaseTestConfig.class)
@Transactional
class RepositoryIntegrationTests {
    @Test
    void persistsAndRollsBack() {
        // ...
    }
}
~~~

Đây là snippet: **không chạy độc lập** nếu chưa có DataSource/TransactionManager/database/repository. Với transactional test-managed transactions, Spring thường rollback khi test kết thúc, có thể dùng `@Commit` hoặc `@Rollback(false)` cho use case đặc biệt.

**Không nhầm transaction do test quản lý với mọi transaction trong application code.** Work ở thread khác, transaction propagation semantics, asynchronous callbacks và external side effects không chắc được rollback cùng test.

### Tránh database contamination

- Chọn test data riêng/transaction isolation phù hợp.
- Reset state những components nằm ngoài transaction boundary.
- Không dùng `@DirtiesContext` như cách mặc định để dọn rows trong database; nó chủ yếu reset Spring context.
- Test concurrency/async phải hiểu transaction thread binding.

## 22. Lỗi thường gặp: triệu chứng và nguyên nhân

| Triệu chứng | Nguyên nhân khả dĩ | Hướng khắc phục |
| --- | --- | --- |
| `@Autowired` trên test field vẫn null | Thiếu SpringExtension khi chỉ có @ContextConfiguration | Thêm @ExtendWith hoặc dùng @SpringJUnitConfig |
| `NoSuchBeanDefinitionException` | Bean không được register trong test context | Thêm đúng @Configuration/@Bean/@Import |
| `NoUniqueBeanDefinitionException` | Nhiều beans cùng type | @Qualifier/@Primary/test config nhỏ |
| Cấu hình thật bị nạp ngoài ý muốn | ComponentScan hoặc @Import quá rộng | Test-specific config explicit |
| Bean @Profile("test") vắng | Test chưa @ActiveProfiles hoặc set sai thời điểm | Khai báo @ActiveProfiles("test") |
| Property prefix không đúng | @TestPropertySource thiếu/khác key, precedence | Kiểm tra environment property sources |
| JUnit chạy nhưng Spring context không tạo | Chưa đăng ký extension | @SpringJUnitConfig/cặp annotations đúng |
| Test method parameter không resolve | Thiếu SpringExtension hoặc @Autowired/qualifier | Kiểm tra ParameterResolver support |
| Constructor test không inject | Chưa annotate @Autowired/@TestConstructor | Chọn one mode rõ |
| JUnit extension conflict | Nhiều resolvers cùng claim parameter | Tách annotations/cấu hình constructors |
| Không tìm thấy XML | Sai locations path hoặc resource không package | Dùng classpath path đúng |
| Không tìm thấy static nested @Configuration | Default detection khác dự kiến | Chỉ định classes explicit |
| Context refresh chậm | Cấu hình quá rộng, cache misses nhiều | Thu nhỏ graph, đồng nhất cache keys |
| Context init lỗi liên tiếp bị skip | TestContext failure threshold/cache policy | Sửa root failure, đọc cause chain |
| Test pass riêng nhưng fail theo suite | Shared mutable singleton/cache/database state | Reset fixtures, transaction isolation |
| @DirtiesContext làm suite rất chậm | Reload mọi lần | Dùng khi thật sự thay cấu hình context |
| PER_CLASS test giữ bean đã destroy | Constructor injection + DirtiesContext | Field/setter reinjection hoặc đổi lifecycle |
| Context đã bị close | Test tự close cached context | Gỡ close, dùng @DirtiesContext |
| Test XML khác JavaConfig | Loader/config types khác | Xác nhận SmartContextLoader |
| Hai classes không chia sẻ context | Profiles/properties/initializers/customizers khác | So cache key |
| Profiles khác nhau nhưng kỳ vọng singleton chung | Context cache key khác | Thiết kế fixture phù hợp |
| @TestPropertySource empty fail | Không thấy default .properties file | Khai báo locations/properties explicit |
| Test dynamic port sai | Port fixed/hardcoded hoặc stale cache | @DynamicPropertySource theo runtime resource |
| @MockitoBean không compile | Thiếu Mockito hoặc Spring test version hỗ trợ | Kiểm tra dependency/version cụ thể |
| Test framework code nạp sai version | JUnit/Spring jars xung đột | Dependency tree/compatible BOM |

## 23. Chẩn đoán lỗi có hệ thống

### Bước 1: Xác nhận lớp test có SpringExtension

Kiểm tra một trong:

~~~java
@SpringJUnitConfig(TestConfig.class)
~~~

hoặc:

~~~java
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
~~~

Đừng bắt đầu debug `@Autowired` field khi JUnit chưa hề kết nối Spring.

### Bước 2: Xác nhận test config thật sự chứa bean

Trong test:

~~~java
@Test
void knowsBean(@Autowired ApplicationContext context) {
    assertTrue(context.containsBean("greetingService"));
}
~~~

Đây là snippet cần static import `assertTrue`, đúng bean name và TestContext support.

### Bước 3: Xác nhận Environment và profiles

~~~java
@Test
void properties(@Autowired ApplicationContext context) {
    System.out.println(context.getEnvironment()
            .getProperty("greeting.prefix"));
}
~~~

Đây là diagnostic snippet; tránh log secrets. Kiểm tra effective property sources, không chỉ value từ một file mà bạn *tưởng* đã được nạp.

### Bước 4: Đọc root cause thay vì chỉ thông báo trên cùng

`Failed to load ApplicationContext` là thông điệp chung. Đọc nested `BeanCreationException`, `NoSuchBeanDefinitionException`, `UnsatisfiedDependencyException`, initialization callbacks và resource errors. Khi lỗi nạp context, không phải mọi test method sẽ được chạy.

### Bước 5: Kiểm tra test context cache

Khi một test pass riêng nhưng fail trong suite, quan sát shared mutable state, context cache key và `@DirtiesContext`. Bật DEBUG cho cache category khi cần, không in toàn bộ beans/secrets.

## 24. Khi nào dùng unit test thuần và khi nào dùng TestContext?

| Mục tiêu | Test hợp lý |
| --- | --- |
| Pure function/algorithm | JUnit thuần, không Spring context |
| Service có collaborators dễ fake | JUnit + constructor injection bằng tay/mocks |
| Xác nhận `@Configuration/@Bean` wiring | Spring TestContext |
| Xác nhận `@Profile`/properties | Spring TestContext với profiles/test properties |
| Xác nhận BeanPostProcessor/lifecycle/proxy | Spring TestContext, config thực tương ứng |
| Kiểm thử HTTP routing/servlet | Spring MVC/web test support, web context |
| Kiểm thử ứng dụng Boot auto-configuration | Boot test slices hoặc `@SpringBootTest` khi cần |
| Database transaction/repository | Integration test có test transaction/DB |
| Kiểm thử cross-process messaging | End-to-end/integration environment thích hợp |

Một bài test Spring context có chi phí khởi tạo; đừng bọc mọi phép cộng trừ trong context. Ngược lại, unit test tạo object bằng `new` không chứng minh Spring thực sự autowire đúng.

### @SpringBootTest không phải @SpringJUnitConfig

`@SpringBootTest` thuộc module Spring Boot test và có semantics khác, như tìm `@SpringBootConfiguration` và dùng Boot bootstrapper. Bài này đang nói về **Spring Framework TestContext thuần**, nơi `spring-test` và `spring-context` đủ cho ví dụ.

## 25. Chiến lược tối ưu tốc độ và độ tin cậy CI

1. Gom nhiều `@Test` methods dùng cùng config vào cùng class hoặc chia classes giữ metadata thật sự giống nhau.
2. Dùng test-specific JavaConfig nhỏ thay cho quét toàn ứng dụng.
3. Giảm số tổ hợp `@ActiveProfiles` và `@TestPropertySource` ngẫu nhiên không cần thiết.
4. Không sử dụng `@DirtiesContext` cho mỗi test nếu chỉ cần reset dữ liệu.
5. Tránh đặt `Thread.sleep(...)` làm cơ chế chờ dependency/context initialization.
6. Không rely vào một static mutable fake repository dùng chung khi tests có thể chạy song song.
7. Tách resource-heavy integration suites để tránh làm unit test pipeline quá chậm.
8. Nếu dùng Testcontainers, quản lý lifecycle và dynamic properties rõ.
9. Đảm bảo plugin build chạy JUnit Platform, không dùng Maven Surefire quá cũ bỏ sót Jupiter tests.
10. Giữ Java/Spring/JUnit versions phù hợp; với Spring 6.2 cần Java 17+.
11. Không chạy JVM fork-per-test nếu muốn hưởng lợi context cache giữa classes.
12. Khi có ngoại lệ context load, đọc các reports `target/surefire-reports` và root causes.
13. Dùng `@Tag` và Maven profiles hợp lý để chọn phạm vi test, không nhầm với @ActiveProfiles.
14. Tránh hard-code timezone, default Locale và random ports trong assertions.
15. Với các tests có transactions, xác định rollback thật sự áp dụng cho operations nào.
16. Xác thực beans được tạo trong context dự kiến, nhất là khi có parent-child contexts.

## 26. Những câu hỏi tự kiểm tra

**Câu 1.** `@SpringJUnitConfig(TestConfig.class)` gồm hai annotations nào?

**Trả lời:** `@ExtendWith(SpringExtension.class)` và `@ContextConfiguration` với component classes tương ứng.

**Câu 2.** Chỉ dùng `@ContextConfiguration(classes=TestConfig.class)` trong JUnit Jupiter có đủ để inject @Autowired test fields không?

**Trả lời:** Không, trừ khi SpringExtension được đăng ký bằng cơ chế khác. Dùng cặp annotations hoặc @SpringJUnitConfig.

**Câu 3.** Trong `@SpringJUnitConfig(TestConfig.class)`, giá trị ngắn gọn là class hay locations?

**Trả lời:** Classes. Trong @ContextConfiguration, `value` lại alias cho locations.

**Câu 4.** Spring tạo một context mới cho mỗi @Test method không?

**Trả lời:** Không mặc định. TestContext có cache, và nhiều tests có cùng cấu hình có thể chia sẻ context.

**Câu 5.** ActiveProfiles có tham gia cache key không?

**Trả lời:** Có. Profiles khác thường tạo context configuration keys khác nhau.

**Câu 6.** @TestPropertySource có ưu tiên hơn system properties thông thường không?

**Trả lời:** Có, trong context loader tiêu chuẩn. Inline properties ưu tiên hơn test property files; dynamic properties có precedence cao hơn test properties.

**Câu 7.** `@DirtiesContext` có phải dùng để xóa database rows sau mỗi test không?

**Trả lời:** Không. Nó đánh dấu cached Spring context bị bẩn; database cleanup/rollback là concern khác.

**Câu 8.** @SpringJUnitConfig có bắt buộc cho JUnit Jupiter không?

**Trả lời:** Không. @ExtendWith(SpringExtension.class) + @ContextConfiguration cũng hợp lệ, và có thể dùng composed annotations tương đương.

**Câu 9.** Test class có tự là Spring bean không?

**Trả lời:** Không mặc định. JUnit sở hữu test instance, TestContext inject Spring beans vào instance đó.

**Câu 10.** `@ContextConfiguration(classes={A.class,B.class})` có tạo hierarchy root-child không?

**Trả lời:** Không. Nó nạp nhiều classes vào một context; cần @ContextHierarchy cho hierarchy thật.

**Câu 11.** Test constructor injection luôn an toàn với @TestInstance(PER_CLASS) + @DirtiesContext sau mỗi method không?

**Trả lời:** Không. Constructor chỉ chạy một lần cho PER_CLASS, có thể giữ reference tới bean của context đã đóng.

**Câu 12.** @ActiveProfiles("test") có giống @Tag("test") không?

**Trả lời:** Không. ActiveProfiles chọn Spring bean config, Tag chọn/nhóm JUnit tests.

**Câu 13.** @DynamicPropertySource cần method dạng gì?

**Trả lời:** Thường là static method nhận một DynamicPropertyRegistry, đăng ký key + Supplier value.

**Câu 14.** Có nên dùng @SpringBootTest để kiểm thử duy nhất một pure business method không?

**Trả lời:** Không cần. JUnit unit test nhỏ sẽ đơn giản và nhanh hơn.

**Câu 15.** Khi test báo Failed to load ApplicationContext, bước nào quan trọng?

**Trả lời:** Đọc root cause cùng config classes/profiles/property sources; không chỉ đổi annotations hoặc thêm @DirtiesContext.

## 27. Checklist triển khai

1. Đúng test framework JUnit Jupiter và Spring Framework/JDK version?
2. Có dependency `spring-test`, `spring-context`, `junit-jupiter`?
3. Maven Surefire/JUnit Platform chạy được test classes *Tests?
4. Dùng @SpringJUnitConfig hoặc @ExtendWith + @ContextConfiguration đúng?
5. Đã hiểu value alias khác nhau giữa hai annotations?
6. `classes` trỏ đúng JavaConfig/test fixture cần thiết?
7. Nếu XML, `locations` đúng classpath resource?
8. Nếu có initializers, chúng chạy trước refresh và không conflict?
9. Test instance @Autowired fields/parameters được SpringExtension xử lý?
10. Constructor injection của test class có autowire mode rõ?
11. Active profiles được cấu hình trước context loading?
12. Test properties được khai báo đúng precedence?
13. Dynamic properties là supplier đúng lifecycle?
14. Không suy mỗi @Test có ApplicationContext mới?
15. Context cache keys không bị phân mảnh vô ích?
16. Có tránh @DirtiesContext khi chỉ cần reset state?
17. Có xác định PER_METHOD/PER_CLASS lifecycle và stale injection?
18. Có tránh đóng cached ApplicationContext thủ công?
19. Nếu dùng hierarchy, đã dùng @ContextHierarchy thay vì hiểu nhầm classes array?
20. Nếu dùng @Nested, đã xét enclosing annotation inheritance?
21. Nếu override beans, có test candidate và cache customizers?
22. Nếu dùng @Transactional, có transaction manager và hiểu rollback boundary?
23. Nếu sử dụng mocks, integration coverage của adapter thật vẫn đầy đủ?
24. Nếu tests parallel, shared singleton state an toàn hoặc reset?
25. Khi lỗi, đã đọc root causes/Surefire reports/cache logs?
26. Test chính xác bean output và Spring wiring, không chỉ assert context != null?
27. Đã cân bằng unit vs integration tests theo mục đích?

## 28. Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — TestContext Framework](https://docs.spring.io/spring-framework/reference/6.2/testing/testcontext-framework.html)
- [Spring Framework 6.2 — TestContext Support Classes, SpringExtension](https://docs.spring.io/spring-framework/reference/6.2/testing/testcontext-framework/support-classes.html)
- [Spring Framework 6.2 — JUnit Jupiter Testing Annotations, @SpringJUnitConfig](https://docs.spring.io/spring-framework/reference/6.2/testing/annotations/integration-junit-jupiter.html)
- [Spring Framework 6.2 — Context Configuration with Component Classes](https://docs.spring.io/spring-framework/reference/6.2/testing/testcontext-framework/ctx-management/javaconfig.html)
- [Spring Framework 6.2 — Context Configuration and Context Loaders](https://docs.spring.io/spring-framework/reference/6.2/testing/testcontext-framework/ctx-management.html)
- [Spring Framework 6.2 — Context Caching](https://docs.spring.io/spring-framework/reference/6.2/testing/testcontext-framework/ctx-management/caching.html)
- [Spring Framework 6.2 — Test Property Sources](https://docs.spring.io/spring-framework/reference/6.2/testing/testcontext-framework/ctx-management/property-sources.html)
- [Spring Framework 6.2 — Dynamic Property Sources](https://docs.spring.io/spring-framework/reference/6.2/testing/testcontext-framework/ctx-management/dynamic-property-sources.html)
- [Spring Framework 6.2 — Spring Testing Annotations](https://docs.spring.io/spring-framework/reference/6.2/testing/annotations/integration-spring.html)
- [Spring Framework 6.2 — Dependency Injection in Tests](https://docs.spring.io/spring-framework/reference/6.2/testing/testcontext-framework/fixture-di.html)
- [Spring Framework 6.2 — Context Hierarchies](https://docs.spring.io/spring-framework/reference/6.2/testing/testcontext-framework/ctx-management/hierarchies.html)
- [Spring Framework 6.2 Javadoc — @SpringJUnitConfig](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/test/context/junit/jupiter/SpringJUnitConfig.html)
- [Spring Framework 6.2 Javadoc — @ContextConfiguration](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/test/context/ContextConfiguration.html)
- [Spring Framework 6.2 Javadoc — SpringExtension](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/test/context/junit/jupiter/SpringExtension.html)
- [Spring Framework 6.2 Javadoc — TestContextManager](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/test/context/TestContextManager.html)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="container-testing-004">Kiểm chứng tên, kiểu và ứng viên được tiêm</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm chứng tên, kiểu và ứng viên được tiêm trong Spring Core Container

## 1. Mục tiêu

Kiểm thử Spring container không chỉ là gọi `context.getBean(...)` rồi thấy object khác null. Khi một ứng dụng có nhiều implementation của cùng một interface, test phải phân biệt chính xác:

- **Tên bean** nào tồn tại trong container, tên nào chỉ là alias.
- **Type** nào được BeanFactory công bố và những definitions nào có thể match một interface hoặc generic signature.
- **Autowire candidates** nào thực sự đủ điều kiện để Spring tiêm vào một dependency cụ thể.
- **Quy tắc ưu tiên** nào khiến Spring chọn một bean giữa nhiều candidates.
- **Object nào** thật sự được tiêm vào consumer, đặc biệt khi có proxies, scopes hoặc parent-child contexts.

Bài này thuộc nhóm **Kiểm thử container** của module **Spring Framework - core-container**, tập trung chủ đề **Kiểm chứng tên, kiểu và ứng viên được tiêm**.

~~~text
              +-------------------------------+
              |          BeanFactory          |
              |                               |
              | fastPort     Port @Primary     |
              | auditPort    Port @Qualifier   |
              | backupPort   Port @Fallback    |
              | internalPort Port autowire=false
              +-------------------------------+
                         |          |
               Type enumeration   Autowiring Port
                         |          |
                         v          v
                  Bốn bean names  Một bean được chọn
                                  theo eligibility,
                                  qualifier, priority
~~~

Với cấu hình tương ứng, `getBeanNamesForType(Port.class)` có thể nhìn thấy cả `internalPort`, còn injection point `Port port` có thể chọn `fastPort` nhờ `@Primary`. **Type match không tự động đồng nghĩa đủ điều kiện autowire.**

Sau khi học, bạn có thể viết unit/integration tests xác minh canonical names/aliases, types, generic metadata, `@Primary`, `@Fallback`, `@Qualifier`, `@Bean(autowireCandidate=false)`, `@Bean(defaultCandidate=false)`, tham số `-parameters`, injected collections, object identity, negative cases và context isolation.

## 2. Ba câu hỏi hoàn toàn khác nhau

### 2.1. Bean name có tồn tại không?

~~~java
boolean visible = context.containsBean("fastPort");
boolean localDefinition =
        context.containsBeanDefinition("fastPort");
~~~

Đây là snippet dùng `AnnotationConfigApplicationContext`. `containsBean` hỏi bean name có thể lookup từ context, còn `containsBeanDefinition` kiểm tra một **local BeanDefinition** trên registry implementation. Bean có thể chỉ nằm ở parent factory, hoặc là một manually registered singleton không có BeanDefinition theo cơ chế thông thường.

### 2.2. Bean có type phù hợp không?

~~~java
Class<?> declaredOrPredicted = context.getType("fastPort");
String[] names = context.getBeanNamesForType(Port.class);
~~~

`getType` cố xác định type bean được công bố, đôi khi từ metadata trước khi instance tồn tại. `getBeanNamesForType` là câu hỏi về **inventory của những beans match type**, không phải thuật toán hoàn chỉnh để chọn một dependency cần injection.

### 2.3. Bean nào thực sự được tiêm?

~~~java
public Checkout(Port port) {
    this.port = port;
}
~~~

Spring không đơn giản lấy entry đầu tiên trong kết quả `getBeanNamesForType`. Nó lọc autowire eligibility, generic/qualifier conditions rồi giải quyết một candidate duy nhất theo các rules như `@Primary`, `@Fallback`, tên parameter và priority.

| API hoặc phép thử | Trả lời câu hỏi | Không chứng minh |
| --- | --- | --- |
| `containsBean(name)` | Bean name khả kiến | Bean eligible để tiêm |
| `containsBeanDefinition(name)` | Local definition tồn tại | Bean đã instantiate |
| `getAliases(name)` | Alias của canonical bean | Alias là bean độc lập |
| `getType(name)` | Type dự đoán/được biết | getBean sẽ construct thành công |
| `getBeanNamesForType(T)` | Local bean names match type | Từng bean eligible với @Autowired |
| `getBeansOfType(T)` | Local bean instances match type | Injected Map sẽ giống hệt |
| `getBean(name,T)` | Bean đích danh match required type | Consumer cũng chọn bean ấy |
| `getBean(T)` | Resolve một bean theo type | Danh sách mọi type matches |
| `isAutowireCandidate` | Eligibility cho dependency descriptor | Candidate là duy nhất |
| Consumer field/constructor | Object thực sự được inject | Không tồn tại contenders khác |

Một test mạnh phối hợp tất cả thông tin cần thiết; không chỉ assert `getBean(...) != null`.

## 3. Bean name, alias và BeanDefinition

### 3.1. Canonical name và alias

~~~java
@Bean(name = {"fastPort", "fastAlias"})
public Port fastPort() {
    return new SimplePort("FAST");
}
~~~

Tên đầu là primary/canonical bean name theo `@Bean` contract; tên sau là alias. Một alias không tạo BeanDefinition hoặc một instance mới.

~~~java
Port first = context.getBean("fastPort", Port.class);
Port second = context.getBean("fastAlias", Port.class);

boolean identical = first == second;
~~~

Với bean singleton thông thường, `identical` là true. Nếu scope là prototype hoặc lookup đi qua một scoped proxy, cần test scope semantics, không luôn dùng `==` làm tiêu chuẩn.

### 3.2. API kiểm chứng

~~~java
context.containsBean("fastAlias");
context.containsBeanDefinition("fastAlias");
context.getAliases("fastPort");
context.getBeanDefinitionNames();
~~~

Một alias có thể lookup thành công nhưng `containsBeanDefinition("fastAlias")` false; `getBeanDefinitionNames()` không nhân đôi canonical name cho alias.

### 3.3. Tên mặc định dễ thay đổi

`@Bean` không có name explicit thường lấy tên method. `@Component` dùng stereotype name hoặc `AnnotationBeanNameGenerator`. Nếu refactor tên method/class làm test thay đổi, hãy cân nhắc explicit name cho public integration contract. Đừng hardcode tên infrastructure beans do framework tạo khi chúng không phải API bạn cần kiểm chứng.

### 3.4. Parent/child context

`child.containsBean("parentOnly")` có thể true nếu bean ở parent; `child.containsBeanDefinition("parentOnly")` vẫn false. `getBeanNamesForType` của `ListableBeanFactory` **chỉ liệt kê local**. Muốn tìm cả ancestors, dùng `BeanFactoryUtils.beanNamesForTypeIncludingAncestors(child, Type.class)`, có xử lý **same-name shadowing**. Một child bean cùng tên che parent bean, kể cả nếu type khác.

## 4. Type matching không phải full dependency resolution

~~~java
String[] names = context.getBeanNamesForType(Port.class);
Map<String, Port> byType = context.getBeansOfType(Port.class);
~~~

Hai lời gọi tìm local type matches. `getBeansOfType` trả objects, do đó có thể tạo instances/lazy beans và kích hoạt scope callbacks. `getBeanNamesForType` thường dựa vào BeanDefinition metadata; với `FactoryBean`, việc dự đoán product type phụ thuộc `getObjectType()` và type metadata.

### 4.1. Hạn chế eager initialization

~~~java
String[] names = context.getBeanNamesForType(
        Port.class, true, false);
~~~

Tham số thứ hai `includeNonSingletons` xác định có xét các beans không phải singleton; tham số cuối `allowEagerInit=false` hạn chế eager factory init chỉ để khám phá type. Điều này có thể làm kết quả thiếu những factory products chưa được biết trước.

### 4.2. Type được công bố so với concrete class

~~~java
@Bean
public PaymentGateway gateway() {
    return new DefaultPaymentGateway();
}
~~~

Tại thời điểm dự đoán type trước creation, Spring có thể chỉ thấy `PaymentGateway` từ return signature. Sau khi instance tạo, exposed proxy class còn có thể khác raw implementation. Nếu cần type lookup theo concrete type, hãy khai báo `@Bean` return type đủ chính xác khi business contract cho phép; nhưng consumer vẫn nên inject interface phù hợp.

### 4.3. FactoryBean

Một `FactoryBean<Report>` có product type `Report`. `context.getBean("reportFactory")` thường trả product; `context.getBean("&reportFactory")` trả FactoryBean object. `getType("reportFactory")` và `getType("&reportFactory")` có semantics khác nhau. Khi test FactoryBean autowiring, bắt đầu từ `getObjectType()` và generic metadata, đừng coi factory object và product cùng type.

### 4.4. Type query vẫn thấy bean không eligible

Một BeanDefinition có `autowireCandidate=false` vẫn tồn tại trong BeanFactory và có type. Nó có thể được query theo name hoặc `getBeansOfType`, nhưng bị loại khỏi annotation-based autowiring tương ứng. Đây là lý do phải test cả **registry inventory** và **injected dependencies**.

## 5. Autowire eligibility: @Bean(autowireCandidate=false)

~~~java
@Bean(name = "internalPort", autowireCandidate = false)
public Port internalPort() {
    return new InternalPort();
}
~~~

Đây là snippet cấu hình, `Port` và `InternalPort` là types do ứng dụng định nghĩa.

Cờ `autowireCandidate=false` khiến bean không được chọn để tiêm theo type qua `@Autowired`, bao gồm collection autowiring thông thường. Nhưng bean vẫn có thể được tìm trực tiếp theo name.

~~~java
Port internal = context.getBean("internalPort", Port.class);
~~~

Câu trên có thể thành công ngay cả khi `@Autowired Port` sẽ không chọn internalPort. Đừng đồng nhất “không được tiêm” với “bean bị xóa khỏi context”.

### 5.1. Truy vấn definition metadata

~~~java
boolean flag =
        context.getDefaultListableBeanFactory()
                .getBeanDefinition("internalPort")
                .isAutowireCandidate();
~~~

Khi test, assert `flag == false` và bổ sung assertion rằng consumer injected list/map không chứa `internalPort`. Chỉ assert flag false không chứng minh consumer thực sự nhận đúng bean khác.

### 5.2. Khác @Bean(defaultCandidate=false) từ Spring 6.2

~~~java
@Bean(name = "specialPort", defaultCandidate = false)
@Qualifier("special")
public Port specialPort() {
    return new SpecialPort();
}
~~~

Trong Spring 6.2, `defaultCandidate=false` loại bean khỏi các injection points chỉ đòi plain type khi không có qualifier indication, **nhưng** bean vẫn có thể match một injection point đã có qualifier phù hợp. Đây là khác biệt quan trọng với `autowireCandidate=false`: cờ thứ hai loại hẳn khỏi type autowiring.

Để test, dùng hai consumer fixtures:

~~~java
public PlainConsumer(Port port) { ... }

public SpecialConsumer(@Qualifier("special") Port port) { ... }
~~~

Đây là snippets, không phải classes đầy đủ. Kiểm tra plain consumer không nhận specialPort nếu có candidate khác, còn special consumer nhận nó.

## 6. @Primary và lựa chọn single candidate

`@Primary` đánh dấu preferred bean trong số nhiều eligible candidates cho injection point cần một bean:

~~~java
@Bean
@Primary
public Port fastPort() {
    return new FastPort();
}
~~~

~~~java
public Checkout(Port port) {
    this.port = port;
}
~~~

Nếu chỉ một candidate đủ điều kiện được đánh dấu primary sau type/qualifier filtering, Spring chọn nó.

### 6.1. @Primary không lọc injected collections

`List<Port>`, `Port[]` và `Map<String,Port>` vẫn có thể chứa những eligible Port beans khác bên cạnh primary. Vì vậy kiểm tra single injection và collection injection là hai assertions riêng.

### 6.2. Khi có hai primaries?

Nếu hai beans đều eligible và primary cho cùng injection point, Spring có thể ném `NoUniqueBeanDefinitionException` trong cause chain của `UnsatisfiedDependencyException`. Khi test, bắt failure của một isolated context thay vì cho cả test suite bị chặn bởi configuration cố tình sai.

### 6.3. Qualifier lọc trước khi primary thắng

Nếu injection point `@Qualifier("audit") Port`, primary fastPort **không** được chọn khi nó không match qualifier. Một primary bean không vượt qua được type/qualifier mismatch.

## 7. @Fallback từ Spring Framework 6.2

`@Fallback` đánh dấu candidates có priority thấp hơn trong single-valued injection.

~~~java
@Bean
public Port mainPort() {
    return new MainPort();
}

@Bean
@Fallback
public Port backupPort() {
    return new BackupPort();
}
~~~

Khi còn đúng một **regular** candidate và các candidates khác đều fallback, mainPort được chọn cho `Port` dependency dù không có `@Primary`.

Nếu còn **hai regular candidates**, chỉ thêm một fallback không giải ambiguity giữa hai regular. Fallback không đồng nghĩa conditional creation: fallback bean vẫn có thể khởi tạo và được tiêm vào collections/maps/provider streams.

### 7.1. Dùng khi nào?

Một implementation dự phòng dành cho môi trường test/development hoặc một adapter ít ưu tiên có thể dùng `@Fallback`. Tuy nhiên đừng dùng nó thay cho `@Profile` hoặc `@Conditional` để kiểm soát bean có tồn tại hay không. Nếu production chỉ nên có một adapter, hãy test profile/condition và candidate selection độc lập.

### 7.2. Tại sao cần context riêng khi test fallback?

Nếu một context có cả `@Primary` và `@Fallback`, primary có thể khiến test single injection luôn pass, không chứng minh fallback policy hoạt động. Dùng context riêng chứa một regular candidate và một fallback candidate để xác nhận.

## 8. @Qualifier và semantic candidate filtering

~~~java
@Bean("auditPort")
@Qualifier("audit")
public Port auditPort() {
    return new AuditPort();
}
~~~

Consumer:

~~~java
public AuditConsumer(@Qualifier("audit") Port port) {
    this.port = port;
}
~~~

`@Autowired` trước hết dựa vào type và candidate eligibility; qualifier tiếp tục **thu hẹp** tập matches. Một `String` bean có name `audit` không thể bị ép thành Port chỉ vì qualifier trùng.

### 8.1. Bean name có thể là qualifier fallback

`@Qualifier("auditPort")` có thể match bean cùng name trong tập candidates cùng type. Nhưng tốt hơn sử dụng ý nghĩa như `"audit"`, `"persistent"` hay `"offline"` khi nhiều implementations dùng chung một đặc tính. Những labels này có thể tồn tại ổn định khi tên bean đổi.

### 8.2. Qualifier không nhất thiết unique

Hai bean cùng `@Qualifier("external")` đều có thể được inject vào `@Qualifier("external") List<Port>`. Do đó qualifier là **tiêu chí filtering**, không mặc định unique ID.

### 8.3. Custom qualifiers

~~~java
@Target({
    ElementType.FIELD, ElementType.PARAMETER,
    ElementType.TYPE, ElementType.METHOD
})
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface Offline {
}
~~~

Đây là snippet annotation, cần Java annotation imports và Spring `Qualifier`. Với qualifier có attributes, test phải so cả attribute values thay vì chỉ annotation presence.

## 9. Field/parameter-name fallback và -parameters

Khi nhiều beans match type mà không có primary, fallback marker hoặc qualifier quyết định khác, Spring có thể dùng **field name/parameter name** để match candidate bean name/alias.

~~~java
public Checkout(Port bluePort) {
    this.port = bluePort;
}
~~~

Trong Spring Framework từ 6.1, Java method/constructor parameter name matching cần compiler flag `-parameters`. Từ 6.2 có fast-path cho bean name matching khi không bị các điều kiện type/qualifier/primary/fallback khác override.

Maven compiler cấu hình:

~~~xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.13.0</version>
    <configuration>
        <parameters>true</parameters>
    </configuration>
</plugin>
~~~

### 9.1. Cẩn thận với rename

Nếu test vô tình dựa vào parameter name `bluePort`, đổi tên tham số khi refactor có thể làm wiring trở nên ambiguous hoặc chọn khác. Với contracts quan trọng, dùng `@Qualifier("blue")` hay @Bean explicit name rõ ràng.

### 9.2. @Resource không giống @Autowired

`@Resource(name="bluePort")` (Jakarta/JSR-250) nhấn mạnh name-based resolution; `@Autowired` về cơ bản type-driven với qualifier narrowing và parameter-name fallback. Không coi hai cơ chế tương đương trong mọi tình huống, đặc biệt khi bean name trùng nhưng type khác.

## 10. Generic metadata và ResolvableType

~~~java
public interface Store<T> {
    T load();
}
~~~

Hai factory methods:

~~~java
@Bean
public Store<String> textStore() {
    return () -> "hello";
}

@Bean
public Store<Integer> countStore() {
    return () -> 42;
}
~~~

Java type erasure không xóa toàn bộ generic signature metadata trên factory methods. Spring `ResolvableType` có thể match một bean yêu cầu `Store<String>` mà không chọn `Store<Integer>`.

~~~java
ResolvableType strings =
        ResolvableType.forClassWithGenerics(
                Store.class, String.class);

String[] matches =
        context.getBeanNamesForType(strings);
~~~

**Đừng chỉ assert positive.** Một bài test mạnh xác nhận `textStore` có trong result và `countStore` không có. Nếu @Bean return type bị đổi thành raw `Store` hoặc `Object`, generic matching có thể mất độ chính xác và regression sẽ được phát hiện.

### 10.1. @Bean return type đầy đủ

Khi một factory method trả interface quá rộng, Spring có thể không dự đoán được concrete subclass hoặc generic arguments từ method body trước khi bean được tạo. Đặt return type phù hợp với API cần tiêm, nhưng không gian lận về implementation thực tế. Việc Spring AOT phân tích ahead-of-time làm điểm này còn quan trọng hơn.

### 10.2. Generic qualifiers không thay bean name

Generic signature tham gia type matching, không tạo thêm bean names. Một bean `textStore` vẫn có canonical name đó dù query là `Store.class` hay `ResolvableType Store<String>`.

## 11. List, Map và ObjectProvider

Với:

~~~java
public Aggregator(
        List<Port> ports,
        Map<String, Port> byName) {
    this.ports = ports;
    this.byName = byName;
}
~~~

Spring có thể inject tất cả **eligible** Port beans trong context vào list và map. Map keys là bean names, không phải aliases riêng hay arbitrary field names.

### So sánh hai tập kết quả

~~~java
Map<String, Port> inventory =
        context.getBeansOfType(Port.class);

Map<String, Port> injected =
        context.getBean(Aggregator.class).byName();
~~~

Nếu `internalPort` bị `autowireCandidate=false`, inventory có thể chứa key đó, trong khi injected map không chứa. Đây là kiểm thử phân biệt **type inventory** với **selection eligibility** rất có giá trị.

### Thứ tự

`@Order`, `Ordered` và một số priority metadata có thể ảnh hưởng thứ tự collections; nhưng nếu test chỉ cần xác nhận **tập bean names**, sort keys/array names hoặc so sánh sets. Không dựa vào thứ tự khai báo @Bean methods để quyết định business behavior.

### ObjectProvider

`ObjectProvider<Port>` hỗ trợ `getIfAvailable`, `getIfUnique`, `stream`, `orderedStream` và deferred retrieval. Nó có thể dùng qualifier constraints của injection point và bean autowire eligibility. Không khẳng định `ObjectProvider.stream()` luôn giống raw `getBeansOfType`. Nếu nghiệp vụ dùng provider, test **lúc provider được gọi**, không chỉ lúc provider được inject.

### Optional dependencies

Với required `@Autowired Port`, thiếu candidate khiến bean creation fail. `Optional<Port>` và `ObjectProvider<Port>` có cách biểu thị absence. Không lạm dụng optional injection để che một dependency thật sự bắt buộc; test phải thể hiện rõ expected behavior khi không có bean.

## 12. Identity của bean đã tiêm và proxy

Một service có thể nhận Spring AOP proxy thay vì raw implementation. Khi so sánh:

~~~java
Port expected = context.getBean("fastPort", Port.class);
Checkout actual = context.getBean(Checkout.class);

boolean same = actual.port() == expected;
~~~

Với singleton exposed object, identity `==` xác nhận consumer cầm đúng bean reference. Nhưng với prototype/scoped proxies, equality/identity và target lifecycle có thể khác; đôi khi nên assert behavior và scoped contract.

### getClass và getType

Một JDK dynamic proxy implement interface nhưng không extends concrete target class. `bean instanceof ConcretePort` có thể false dù `bean instanceof Port` true. Đừng ép test phải thấy concrete runtime class khi chính contract là interface injection.

### Test đúng điều muốn chứng minh

- Nếu cần xác nhận **bean identity**, so reference tương ứng trên cùng scope.
- Nếu cần xác nhận **behavior**, gọi contract method và assert output.
- Nếu cần xác nhận **AOP advice**, chạy integration test qua proxy, không chỉ unit test raw target.
- Nếu cần xác nhận **scope**, dựng scope-aware fixture, không kết luận từ một lần getBean.

## 13. Ví dụ runnable Java 17 + Maven + Spring 6.2.9

Demo có **hai contexts độc lập**:

1. `PrimaryConfig` đăng ký Port beans theo canonical name/alias, @Primary, @Qualifier, @Fallback và autowireCandidate=false; verify type inventory, actual injected map và generics.
2. `FallbackConfig` đăng ký một regular Port cùng một fallback Port; verify regular thắng khi không có @Primary.

### 13.1. Cây thư mục

~~~text
bean-selection-demo/
├── pom.xml
└── src/main/java/demo/
    └── BeanSelectionDemo.java
~~~

### 13.2. pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>bean-selection-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <maven.compiler.parameters>true</maven.compiler.parameters>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.9</version>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <parameters>true</parameters>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 13.3. src/main/java/demo/BeanSelectionDemo.java

~~~java
package demo;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Fallback;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ResolvableType;

public class BeanSelectionDemo {

    public interface Port {
        String code();
    }

    public record SimplePort(String code) implements Port {
    }

    public interface Store<T> {
        T load();
    }

    public record Selection(
            Port chosen,
            Port audit,
            Map<String, Port> injectedMap) {
    }

    public record FallbackSelection(Port chosen) {
    }

    private static String sorted(String[] names) {
        return Arrays.stream(names)
                .sorted()
                .collect(Collectors.joining(","));
    }

    private static String sortedKeys(Map<String, ?> values) {
        return values.keySet().stream()
                .sorted()
                .collect(Collectors.joining(","));
    }

    @Configuration(proxyBeanMethods = false)
    public static class PrimaryConfig {

        @Bean(name = {"fastPort", "fastAlias"})
        @Primary
        public Port fastPort() {
            return new SimplePort("FAST");
        }

        @Bean("auditPort")
        @Qualifier("audit")
        public Port auditPort() {
            return new SimplePort("AUDIT");
        }

        @Bean("backupPort")
        @Fallback
        public Port backupPort() {
            return new SimplePort("BACKUP");
        }

        @Bean(name = "internalPort", autowireCandidate = false)
        public Port internalPort() {
            return new SimplePort("INTERNAL");
        }

        @Bean("textStore")
        public Store<String> textStore() {
            return () -> "hello";
        }

        @Bean("countStore")
        public Store<Integer> countStore() {
            return () -> 42;
        }

        @Bean
        public Selection selection(
                Port plain,
                @Qualifier("audit") Port audit,
                Map<String, Port> eligible) {
            return new Selection(plain, audit, eligible);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class FallbackConfig {

        @Bean("standardPort")
        public Port standardPort() {
            return new SimplePort("STANDARD");
        }

        @Bean("emergencyPort")
        @Fallback
        public Port emergencyPort() {
            return new SimplePort("EMERGENCY");
        }

        @Bean
        public FallbackSelection fallbackSelection(Port plain) {
            return new FallbackSelection(plain);
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             PrimaryConfig.class)) {

            System.out.println("types="
                    + sorted(context.getBeanNamesForType(Port.class)));

            System.out.println("alias.visible="
                    + context.containsBean("fastAlias"));

            System.out.println("alias.notDefinition="
                    + !context.containsBeanDefinition("fastAlias"));

            System.out.println("alias.sameInstance="
                    + (context.getBean("fastAlias", Port.class)
                            == context.getBean("fastPort", Port.class)));

            System.out.println("internal.typeVisible="
                    + Arrays.asList(context.getBeanNamesForType(
                            Port.class)).contains("internalPort"));

            System.out.println("internal.autowireCandidate="
                    + context.getDefaultListableBeanFactory()
                            .getBeanDefinition("internalPort")
                            .isAutowireCandidate());

            Selection selected = context.getBean(Selection.class);

            System.out.println("injected.single="
                    + selected.chosen().code());

            System.out.println("injected.audit="
                    + selected.audit().code());

            System.out.println("injected.mapKeys="
                    + sortedKeys(selected.injectedMap()));

            System.out.println("injected.excludesInternal="
                    + !selected.injectedMap().containsKey("internalPort"));

            ResolvableType strings =
                    ResolvableType.forClassWithGenerics(
                            Store.class, String.class);

            ResolvableType integers =
                    ResolvableType.forClassWithGenerics(
                            Store.class, Integer.class);

            System.out.println("generic.text="
                    + sorted(context.getBeanNamesForType(strings)));

            System.out.println("generic.count="
                    + sorted(context.getBeanNamesForType(integers)));

            System.out.println("typeLookup.seesInternal="
                    + context.getBeansOfType(Port.class)
                            .containsKey("internalPort"));
        }

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             FallbackConfig.class)) {

            FallbackSelection selected =
                    context.getBean(FallbackSelection.class);

            System.out.println("fallback.single="
                    + selected.chosen().code());
        }
    }
}
~~~

### 13.4. Chạy

Trong thư mục `bean-selection-demo` có JDK 17+ và Maven:

~~~bash
java -version
mvn -version
mvn -q compile exec:java -Dexec.mainClass=demo.BeanSelectionDemo
~~~

Stdout **do chương trình in** (không tính Maven/Spring logs):

~~~text
types=auditPort,backupPort,fastPort,internalPort
alias.visible=true
alias.notDefinition=true
alias.sameInstance=true
internal.typeVisible=true
internal.autowireCandidate=false
injected.single=FAST
injected.audit=AUDIT
injected.mapKeys=auditPort,backupPort,fastPort
injected.excludesInternal=true
generic.text=textStore
generic.count=countStore
typeLookup.seesInternal=true
fallback.single=STANDARD
~~~

### 13.5. Giải thích

| Dòng | Cơ chế |
| --- | --- |
| `types` | Mọi bean types Port, kể cả internalPort, được inventory lookup nhìn thấy |
| `alias.visible` | fastAlias tham chiếu tới fastPort |
| `alias.notDefinition` | Alias không có BeanDefinition riêng |
| `alias.sameInstance` | Hai tên trỏ cùng singleton |
| `internal.typeVisible` | autowireCandidate=false không làm bean biến mất khỏi type inventory |
| `internal.autowireCandidate=false` | Metadata BeanDefinition ghi flag đúng |
| `injected.single=FAST` | @Primary ưu tiên fastPort cho Port plain |
| `injected.audit=AUDIT` | @Qualifier("audit") chọn đúng auditPort |
| `injected.mapKeys` | Map injection có eligible ports, bao gồm @Fallback |
| `injected.excludesInternal=true` | internalPort không đủ điều kiện tiêm |
| `generic.text` | Store<String> khớp textStore |
| `generic.count` | Store<Integer> khớp countStore |
| `typeLookup.seesInternal=true` | getBeansOfType khác autowire filtering |
| `fallback.single=STANDARD` | Regular bean thắng fallback trong context độc lập |

Phương thức `selection(Port plain,...)` và `fallbackSelection(Port plain)` không có parameter names trùng bean names, giúp demo không vô tình dựa vào parameter-name fallback. Output sắp xếp names trước khi in để tránh dựa vào thứ tự enumeration.

### 13.6. Các bài thử

**Bỏ @Primary:** Trong PrimaryConfig còn fastPort và auditPort là hai regular candidates cho `Port plain`; backupPort là fallback. Tạo Selection có thể gây ambiguity. Kiểm chứng `NoUniqueBeanDefinitionException` trong cause chain.

**Bỏ @Qualifier("audit"):** `Selection.audit` có thể nhận fastPort nhờ @Primary, khiến test chuyên biệt bắt lỗi cấu hình.

**Bỏ autowireCandidate=false:** injectedMap xuất hiện thêm internalPort; dù single injection vẫn FAST nhờ primary, test collection sẽ bắt regression.

**Bỏ @Fallback ở FallbackConfig:** standardPort và emergencyPort thành hai regular Port candidates, khiến `Port plain` ambiguous.

**Thay generic Store<String> thành raw Store:** Type matching qua ResolvableType có thể mất specificity; chạy test positive và negative.

**Đổi alias fastAlias:** `containsBean("fastAlias")` false hoặc singleton identity test fail; đây là thay đổi API name contract.

## 14. Low-level: DependencyDescriptor và eligibility

Đôi khi cần kiểm tra xem một bean cụ thể có eligible đối với field/parameter injection theo qualifier không. `DefaultListableBeanFactory` có:

~~~java
boolean isAutowireCandidate(
        String beanName,
        DependencyDescriptor descriptor);
~~~

Đây là API signature rút gọn. Ví dụ field:

~~~java
public class AuditClient {
    @Autowired
    @Qualifier("audit")
    private Port port;
}
~~~

Tạo descriptor:

~~~java
Field field = AuditClient.class.getDeclaredField("port");

DependencyDescriptor descriptor =
        new DependencyDescriptor(field, true);

boolean candidate =
        factory.isAutowireCandidate("auditPort", descriptor);
~~~

Đây là **test snippet**, cần imports Java `Field`, Spring `DependencyDescriptor`, `Autowired`, `Qualifier`; `factory` là `DefaultListableBeanFactory` của fixture. Nó trả lời eligibility cho injection point, nhưng **không bảo đảm chỉ có một candidate**. Nếu hai beans đều match `@Qualifier("audit")`, cả hai có thể eligible và single injection vẫn ambiguous.

Để chứng minh cái nào thực sự được tiêm, test phải đọc dependency **trên consumer** sau BeanFactory resolution.

## 15. Negative tests: không có hoặc quá nhiều ứng viên

### Missing bean

~~~java
@Configuration
class MissingConfig {

    @Bean
    NeedsPort needsPort(Port port) {
        return new NeedsPort(port);
    }
}
~~~

Đây là snippet; `NeedsPort` class do ứng dụng định nghĩa. Nếu không có eligible Port, `refresh()` hoặc bean creation sẽ throw, thường outer `UnsatisfiedDependencyException` với `NoSuchBeanDefinitionException` trong cause chain.

### Ambiguous beans

~~~java
@Configuration
class AmbiguousConfig {

    @Bean
    Port alphaPort() {
        return () -> "ALPHA";
    }

    @Bean
    Port betaPort() {
        return () -> "BETA";
    }

    @Bean
    NeedsPort needsPort(Port plain) {
        return new NeedsPort(plain);
    }
}
~~~

Không có primary/qualifier/fallback và parameter `plain` không match bean names, nên single Port dependency có thể fail với `NoUniqueBeanDefinitionException`. Đừng viết test “nếu có hai beans thì Spring chọn cái được khai báo trước”.

### Sai type dù đúng name

~~~java
context.getBean("auditPort", String.class);
~~~

Với auditPort là Port chứ không String, typed lookup có thể ném `BeanNotOfRequiredTypeException`. Name lookup không làm Spring bỏ required type constraint.

### Một context riêng cho negative cases

Dựng context riêng cho từng failure fixture. Không thêm bean thiếu/dependency sai vào context của success test, vì nó có thể khiến toàn bộ refresh thất bại trước khi assertions chạy.

### Bắt cause chain

Framework có thể bọc một `NoSuchBeanDefinitionException` hoặc `NoUniqueBeanDefinitionException` trong `UnsatisfiedDependencyException`/`BeanCreationException`. Tests ổn định nên check exception cause chain hoặc expectation theo API phù hợp thay vì match một log string cố định.

## 16. JUnit + Spring TestContext

Ví dụ mục 13 là một executable container integration check chạy bằng main. Trong dự án thật, Spring TestContext Framework tích hợp JUnit Jupiter qua `spring-test`.

~~~java
@SpringJUnitConfig(BeanSelectionDemo.PrimaryConfig.class)
class BeanSelectionTest {

    @Autowired
    BeanSelectionDemo.Selection selection;

    @Test
    void choosesExpectedBeans() {
        assertEquals("FAST", selection.chosen().code());
        assertEquals("AUDIT", selection.audit().code());
        assertFalse(
                selection.injectedMap().containsKey("internalPort"));
    }
}
~~~

Đây là **test snippet**, không thuộc source file runnable mục 13. Nó cần dependencies `org.springframework:spring-test:6.2.9`, JUnit Jupiter version tương thích, imports `SpringJUnitConfig`, `Autowired`, `Test` và `Assertions`, cùng JUnit-compatible Maven Surefire configuration.

### Khi nào unit test thuần Java đủ?

Nếu consumer chỉ làm nghiệp vụ với interface:

~~~java
Port fake = () -> "FAKE";
Checkout checkout = new Checkout(fake);
~~~

Một unit test như vậy kiểm tra constructor/object behavior mà không cần Spring. Nhưng nó **không** chứng minh qualifier/primary/bean name selection. Để test container wiring, phải dựng container thực.

### @MockitoBean và mock boundaries

Spring Framework 6.2 cung cấp `@MockitoBean`/`@MockitoSpyBean` trong spring-test bean override support. Khi replace một definition bằng mock, candidate set có thể khác cấu hình production. Test với mock hữu ích cho consumer logic nhưng không thay thế real-context wiring test. `@MockBean` là annotation bên Spring Boot có lịch sử/version semantics riêng; không coi chúng tương đương không cần migration review.

### TestContext caching

TestContext có thể cache application contexts. Nếu một test mutate bean definitions hoặc environment sau refresh trong shared cached context, tests khác có thể bị ảnh hưởng. Dùng separate fixtures hoặc `@DirtiesContext` khi thực sự cần thay state container.

## 17. Scope, lifecycle, profiles và proxies

### Lazy/prototype

BeanDefinition có thể tồn tại trước instance. `containsBeanDefinition`/`getType` true không chứng minh constructor đã chạy. Prototype `getBean` có thể trả instance mới mỗi lần, nên assertion `==` không phù hợp nếu mục tiêu là type/qualifier policy.

### Request/session

Web scopes cần một active request/session đúng hạ tầng. `getBeanNamesForType` có thể thấy metadata, trong khi lấy instance khi scope không active sẽ fail. Test dependency qua scoped proxy ở một web-aware context, không dùng bare AnnotationConfigApplicationContext rồi suy luận lỗi là missing BeanDefinition.

### Profile và @Conditional

`@Profile("prod")` có thể làm bean hoàn toàn không được đăng ký trong dev context. Test profiles riêng, đặt active profile **trước refresh** hoặc `@ActiveProfiles` với TestContext. Không coi bean bị loại bởi condition giống `autowireCandidate=false`: một cái vắng khỏi registry, cái kia tồn tại nhưng không eligible.

### Proxy

Spring AOP JDK proxy expose interface nhưng không phải concrete target class. Khi test, inject/test interface `Port` thay vì ép concrete class khi configuration có thể bật proxy. AOP advice cũng không tự chạy khi unit test new raw implementation. Nếu yêu cầu là đúng bean proxy, kiểm chứng qua context.

### Parent-child hierarchy

Type listing APIs của ListableBeanFactory chỉ local; `getBean(name)` có thể fallback lên parent. Khi cùng bean name ở child/parent, child che parent ngay cả khi type khác. Dùng `BeanFactoryUtils...IncludingAncestors` nếu cần inventory đúng theo hierarchy.

## 18. Checklist kiểm chứng

- [ ] Xác định đúng context/bean factory test đang dùng.
- [ ] Assert canonical names và aliases đúng.
- [ ] Không coi alias là BeanDefinition riêng.
- [ ] Phân biệt name visibility với BeanDefinition local.
- [ ] Assert required type và generic matching.
- [ ] Phân biệt getBeanNamesForType với injected candidates.
- [ ] Kiểm tra autowireCandidate và defaultCandidate metadata.
- [ ] Kiểm tra @Primary chỉ chọn single candidate phù hợp.
- [ ] Kiểm tra @Fallback với context không có primary.
- [ ] Kiểm tra @Qualifier lọc type candidates đúng.
- [ ] Không vô tình dựa vào parameter names nếu chưa kiểm soát -parameters.
- [ ] Assert collection/map injected keys khi eligibility quan trọng.
- [ ] Sort names hoặc so sánh sets khi thứ tự không là contract.
- [ ] Assert consumer đang giữ đúng exposed bean identity khi scope phù hợp.
- [ ] Có positive và negative tests với generic types.
- [ ] Test thiếu bean và nhiều candidates bằng fixtures riêng.
- [ ] Không dùng mock-overridden context làm bằng chứng production wiring duy nhất.
- [ ] Kiểm tra profiles/conditions trên những contexts độc lập.
- [ ] Hiểu FactoryBean và proxy type prediction.
- [ ] Không gọi getBeansOfType hàng loạt khi không cần tạo instances.
- [ ] Test nguyên nhân gốc thay vì khớp message exceptions.
- [ ] Tách wiring integration tests khỏi pure unit tests.

## 19. Câu hỏi tự kiểm tra

**1. containsBean("fastAlias") true có nghĩa fastAlias là bean definition thứ hai?**  
Không. Nó có thể là alias của fastPort.

**2. getBeanNamesForType(Port.class) có chỉ trả beans có thể @Autowired không?**  
Không. Type inventory và autowire candidate eligibility là hai lớp logic.

**3. autowireCandidate=false có xóa bean khỏi context không?**  
Không. Bean vẫn có thể được truy xuất đích danh.

**4. defaultCandidate=false khác autowireCandidate=false thế nào?**  
defaultCandidate=false vẫn cho phép qualifier-constrained injection, còn autowireCandidate=false loại khỏi type autowiring.

**5. @Primary có lọc bean khác khỏi Map<String,Port> không?**  
Không; eligible beans khác vẫn có thể được tiêm vào map.

**6. @Fallback có phải chỉ khởi tạo bean khi primary lỗi không?**  
Không. Đây là ưu tiên trong autowiring, không phải retry/health check.

**7. Vì sao @Qualifier("audit") không chọn bean String tên audit?**  
Qualifier lọc trong type matches; không tự đổi Java type.

**8. Một @Qualifier có phải unique ID không?**  
Không. Nhiều beans có thể chia sẻ qualifier trong collection injection.

**9. Tên tham số constructor có thể quyết định bean được inject không?**  
Có trong điều kiện fallback matching phù hợp. Với Java Spring 6.1+, cần compiler -parameters.

**10. Generic Store<String> và Store<Integer> có thể phân biệt không?**  
Có, khi generic signature/BeanDefinition metadata đủ cụ thể, qua ResolvableType.

**11. getBeansOfType và injected Map<String,T> có luôn cùng keys?**  
Không, vì map injection lọc eligibility, qualifiers và các điều kiện tương ứng.

**12. Tại sao cần kiểm tra object reference trên consumer?**  
Để chứng minh resolution thực sự tiêm instance mong muốn, không chỉ bean tồn tại.

**13. Nếu hai regular candidates cùng type và không ưu tiên thì sao?**  
Single-valued injection có thể fail với NoUniqueBeanDefinitionException.

**14. Spring TestContext với @MockitoBean có chứng minh production wiring không?**  
Chưa chắc. Override có thể làm thay đổi candidate set.

**15. getType thành công có chứng minh getBean sẽ khởi tạo được không?**  
Không. Bean construction/dependencies/scopes vẫn có thể lỗi.

**16. Vì sao phải test parent-child riêng?**  
Lookup theo name có parent fallback còn type listing chỉ local, với same-name shadowing.

## Tài liệu chính thức

- [Spring Framework 6.2 — Using @Autowired](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired.html): injection points, collection/map injection, constructor resolution.
- [Spring Framework 6.2 — Fine-tuning with @Primary and @Fallback](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired-primary.html).
- [Spring Framework 6.2 — Fine-tuning with Qualifiers](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired-qualifiers.html): qualifier narrowing, name fallback, -parameters và fast-path.
- [Spring Framework 6.2 — Autowiring Collaborators](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-autowire.html): autowire-candidate, default-candidate semantics.
- [Spring Framework 6.2 — Generics as Autowiring Qualifiers](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/generics-as-qualifiers.html).
- [Spring Framework 6.2 Javadoc — Bean](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/context/annotation/Bean.html).
- [Spring Framework 6.2 Javadoc — Fallback](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/context/annotation/Fallback.html).
- [Spring Framework 6.2 Javadoc — DefaultListableBeanFactory](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/beans/factory/support/DefaultListableBeanFactory.html).
- [Spring Framework 6.2 Javadoc — ListableBeanFactory](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/beans/factory/ListableBeanFactory.html).
- [Spring Framework 6.2 Javadoc — BeanFactory](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/beans/factory/BeanFactory.html).
- [Spring Framework 6.2 Javadoc — ResolvableType](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/core/ResolvableType.html).
- [Spring Framework 6.2 Javadoc — DependencyDescriptor](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/beans/factory/config/DependencyDescriptor.html).
- [Spring Framework 6.2 — TestContext Framework](https://docs.spring.io/spring-framework/reference/6.2/testing/testcontext-framework.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="container-testing-005">Kiểm chứng số thể hiện singleton, prototype và provider</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm chứng số thể hiện singleton, prototype và provider trong Spring Core Container

## 1. Mục tiêu bài học

Một bài kiểm thử Spring Container không nên chỉ xác nhận `getBean()` trả về object. Nó cần chứng minh **bao nhiêu instance đã được tạo, cùng reference hay khác reference, ở thời điểm nào**, và vì sao kết quả khác nhau giữa `singleton`, `prototype` và `ObjectProvider<T>`.

Ba quy tắc nền tảng:

1. **Singleton scope** (mặc định): **một object instance cho một bean definition trong một Spring IoC container**; những lần lấy lại cùng bean trả cùng singleton instance, trừ trường hợp đặc biệt như scoped proxy, `FactoryBean` hoặc cấu hình khác có semantics riêng.
2. **Prototype scope**: mỗi lần container thực sự được yêu cầu cung cấp prototype bean, nó thường **tạo một instance mới**, bao gồm lúc inject prototype vào một bean khác.
3. **ObjectProvider<T>**: đây **không phải bean scope thứ ba**. Nó là một handle để resolve target bean **mỗi lần gọi `getObject()`**. Target là singleton thì provider trả singleton đang được chia sẻ; target là prototype thì provider có thể tạo instance mới ở mỗi lần gọi.

Vấn đề thường khiến test sai: **inject prototype trực tiếp vào một singleton chỉ resolve dependency một lần khi singleton được tạo**. Sau đó singleton giữ chính reference prototype ban đầu. Muốn mỗi lần xử lý có prototype mới, inject `ObjectProvider<PrototypeType>` rồi gọi `getObject()` khi cần.

Bài thuộc nhóm *Kiểm thử container*: mục tiêu cuối cùng không chỉ hiểu định nghĩa mà còn viết **tests đáng tin** với `AnnotationConfigApplicationContext`, JUnit 5, constructor counters và identity assertions. Ví dụ dùng Java 17 và Spring Framework 6.2.18, không phụ thuộc Spring Boot.

## 2. “Số thể hiện” chính xác là gì?

Cần tách ít nhất bốn con số:

- **Số BeanDefinitions**: số công thức đăng ký bean; đo bằng `getBeanDefinitionCount()` hoặc inspect registration, không phải số objects đã tạo.
- **Số constructor invocations**: số lần một constructor của class mục tiêu đã chạy trong kịch bản. Có thể đo qua `AtomicInteger` hoặc test factory.
- **Số distinct object references**: số object thực sự khác nhau theo Java reference identity `==`, `assertSame` và `assertNotSame`.
- **Số lần lookup**: số lần code gọi `getBean`, `provider.getObject`, inject dependency hoặc gọi factory methods. Lookup count có thể lớn hơn, bằng hoặc ít hơn construction count tùy scope/caching.

Không đồng nhất:

```text
BeanDefinition count == constructor count == getBean call count
```

Đó **không phải một bất biến đúng** của Spring.

Ví dụ một BeanDefinition singleton có thể được tra mười lần nhưng chỉ tạo một instance. Prototype BeanDefinition có thể tồn tại dù chưa có một instance nào được tạo; mỗi lần getBean mới có thể tạo thêm một.

## 3. Bảng tiên đoán kết quả

Giả sử trong **một** context có chính xác một bean name `sharedService` singleton và một bean name `taskToken` prototype:

| Thao tác | Bean scope đích | Instance được trả |
| --- | --- | --- |
| `getBean("sharedService")` lần đầu | singleton | Tạo hoặc lấy singleton #1 |
| `getBean("sharedService")` lần hai | singleton | Vẫn singleton #1 |
| `getBean("taskToken")` lần đầu | prototype | Prototype #1 |
| `getBean("taskToken")` lần hai | prototype | Prototype #2 |
| Inject `TaskToken` vào singleton `DirectClient` | prototype | Một TaskToken được tạo khi `DirectClient` khởi tạo |
| Dùng lại `DirectClient.token()` | reference đã inject | Vẫn một TaskToken cũ |
| Inject `ObjectProvider<TaskToken>` vào singleton | provider handle | Chưa cần tạo TaskToken mới chỉ vì giữ handle |
| Gọi provider `getObject()` hai lần | prototype | Hai TaskToken mới |
| Gọi `ObjectProvider<SharedService>.getObject()` hai lần | singleton | Cùng SharedService #1 |

Bảng dùng số thứ tự **theo thứ tự gọi giả định**, không phải một quy tắc rằng Spring tự gán ID 1/2/3 cho mọi bean. Trong demo, tự viết constructor counter để in ID xác định.

## 4. Singleton là per container và per bean definition

Theo Spring Reference, singleton scope là mặc định và định nghĩa **một instance cho một bean definition trên một IoC container**.

```java
@Bean
SharedService sharedService() {
    return new SharedService();
}
```

Đây là mảnh Java configuration minh họa.

Trong một context:

```java
SharedService first =
        context.getBean("sharedService", SharedService.class);

SharedService second =
        context.getBean("sharedService", SharedService.class);

boolean same = first == second;
```

Với một singleton bean bình thường, `same` đúng.

**Không nhầm Spring singleton với GoF Singleton pattern**:
- GoF Singleton thường giới hạn instance theo class/classloader và singleton accessor do class tự quản lý.
- Spring singleton là theo **bean registry + bean name/definition**.
- Hai bean definitions khác nhau cùng class có thể tạo **hai singleton instances khác nhau**.
- Hai ApplicationContexts độc lập, mỗi nơi có một definition `sharedService`, thường sở hữu **hai singleton instances**.
- Child context tra parent singleton có thể lấy **cùng parent instance** nếu không có local bean shadowing.

Một test `assertSame` đối với *cùng bean name trong cùng factory* là chính xác hơn việc giả định một Java class “chỉ được phép có một object trên JVM”.

## 5. Prototype tạo lúc được yêu cầu

Bean prototype:

```java
@Bean
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
TaskToken taskToken() {
    return new TaskToken();
}
```

Mảnh này cần các imports `@Bean`, `@Scope` và `ConfigurableBeanFactory`.

Với calls:

```java
TaskToken first =
        context.getBean("taskToken", TaskToken.class);

TaskToken second =
        context.getBean("taskToken", TaskToken.class);

boolean distinct = first != second;
```

Trong điều kiện bình thường, `distinct` là true. Mỗi lookup yêu cầu bean prototype nên Spring gọi factory method và tạo object mới.

Điều này **không có nghĩa** Spring tạo mọi prototype ngay khi `refresh()`:
- `preInstantiateSingletons()` không eager-create tất cả prototypes đơn lẻ.
- Prototype có thể được tạo **gián tiếp** khi một non-lazy singleton cần nó lúc khởi tạo.
- Một prototype bean có thể chưa từng được tạo nếu ứng dụng chưa yêu cầu nó.

Bởi vậy “prototype count sau refresh bằng 0” **không luôn đúng**. Nó phụ thuộc graph injection, không chỉ scope annotation.

## 6. Singleton inject một prototype trực tiếp

Đây là trường hợp cần test nhiều nhất:

```java
public record DirectClient(TaskToken token) {
}
```

Và cấu hình:

```java
@Bean
DirectClient directClient(TaskToken token) {
    return new DirectClient(token);
}
```

Đây là hai mảnh Java minh họa thuộc một ứng dụng có bean `TaskToken` prototype.

Spring tạo `DirectClient` singleton mặc định một lần. Khi resolve factory method argument `TaskToken token`, nó yêu cầu một **prototype instance**. Reference đó được giữ trong `DirectClient`.

Sau này:

```java
DirectClient client =
        context.getBean(DirectClient.class);

TaskToken firstUse = client.token();
TaskToken secondUse = client.token();

boolean sameCapturedReference = firstUse == secondUse;
```

`sameCapturedReference` là true. Bạn **không** có prototype mới mỗi lần đọc field, vì container không tự chạy lại injection cho một singleton đã tạo.

Dùng `@Scope("prototype")` trên TaskToken là đúng nhưng **chưa đủ** nếu business method của singleton cần một stateful TaskToken mới mỗi lần thực thi.

## 7. ObjectProvider giải quyết việc lấy dependency theo nhu cầu

Spring `ObjectProvider<T>`:

```java
public final class ProviderClient {
    private final ObjectProvider<TaskToken> tokens;

    public ProviderClient(ObjectProvider<TaskToken> tokens) {
        this.tokens = tokens;
    }

    public TaskToken nextToken() {
        return tokens.getObject();
    }
}
```

Đây là mảnh minh họa. Import `org.springframework.beans.factory.ObjectProvider`.

Trong configuration:

```java
@Bean
ProviderClient providerClient(
        ObjectProvider<TaskToken> tokens) {
    return new ProviderClient(tokens);
}
```

Lúc tạo `ProviderClient` singleton, Spring inject **provider handle**. Handler này có thể được giữ lâu dài, còn bean target được resolve khi gọi `getObject()`.

Nếu TaskToken là prototype:

```java
TaskToken a = providerClient.nextToken();
TaskToken b = providerClient.nextToken();

boolean distinct = a != b; // true
```

Nếu `T` trong `ObjectProvider<T>` là singleton:
- Provider vẫn truy vấn BeanFactory trên mỗi call.
- BeanFactory singleton cache trả **cùng instance**.
- Vì thế **ObjectProvider không tự cam kết mỗi lần trả object mới**; scope đích mới quyết định.

## 8. Sơ đồ vòng đời so sánh

```text
refresh()
   |
   +-- create SharedService singleton once
   |
   +-- create DirectClient singleton
   |       |
   |       +-- resolve TaskToken prototype (#1)
   |       |
   |       +-- store TaskToken #1 forever in this DirectClient
   |
   +-- create ProviderClient singleton
           |
           +-- store ObjectProvider<TaskToken>
           +-- NO TaskToken from handle construction alone

getBean(TaskToken) -> TaskToken #2
getBean(TaskToken) -> TaskToken #3

provider.getObject() -> TaskToken #4
provider.getObject() -> TaskToken #5

getBean(SharedService) -> shared #1
getBean(SharedService) -> shared #1 again
```

Số #1–#5 trong sơ đồ đúng **theo thứ tự của ví dụ mẫu**. Nếu thay đổi thứ tự gọi, IDs thay đổi nhưng các bất biến *singleton same/prototype distinct/provider honors scope* vẫn đúng.

## 9. ObjectProvider API nào nên kiểm thử?

`ObjectProvider<T>` của Spring 6.2.18 có những methods:

| Method | Ý nghĩa và hậu quả |
| --- | --- |
| `getObject()` | Phải có candidate phù hợp, có thể instantiate; thiếu bean thường báo lỗi |
| `getIfAvailable()` | Trả bean nếu available, có thể null nếu không có; vẫn có thể instantiate |
| `getIfUnique()` | Trả nếu có candidate unique theo resolution semantics; có thể null |
| `getIfAvailable(Supplier<T>)` | Dùng fallback object nếu không có candidate |
| `getIfUnique(Supplier<T>)` | Dùng fallback nếu không có unique candidate |
| `ifAvailable(Consumer<T>)` | Thực thi callback nếu resolve được một bean |
| `ifUnique(Consumer<T>)` | Thực thi callback khi unique |
| `stream()` | Stream các beans matching; khi consume có thể instantiate |
| `orderedStream()` | Giống stream với thứ tự theo common order comparator |
| `iterator()` | Iterate các matching bean instances |

Đặc biệt:
- `getIfAvailable()` không biến một configuration lỗi thành an toàn hoàn toàn; nếu creation thất bại nó vẫn có thể ném `BeansException`.
- `getIfUnique()` không đồng nghĩa “nếu số bean definitions đúng 1” trong mọi trường hợp: `@Primary`, fallback/default candidate và qualifier semantics có thể ảnh hưởng lựa chọn.
- `stream()` của provider **không** có nghĩa “tạo một prototype vô hạn”. Nó liệt kê các matching bean definitions/candidates và resolve instance khi duyệt.
- Với một prototype definition duy nhất, gọi provider stream và collect thường trả một entry đại diện một instance; **không** tạo nhiều entry chỉ vì prototype có thể tạo nhiều instances.

Đối với việc cần **một prototype mới trên mỗi business call**, dùng `getObject()` từ provider rõ ràng hơn dùng `stream()`.

## 10. Kiểm chứng bằng identity thay vì equals

Một số domain classes/records override `equals` theo giá trị. Hai prototypes khác instances nhưng cùng fields có thể `equals() == true`.

Sai nếu mục tiêu là chứng minh scope:

```java
assertNotEquals(a, b);
```

Đây là mảnh JUnit minh họa; nó không đáng tin cho object có value-based equality.

Đúng:

```java
assertNotSame(a, b);
```

Tương tự, dùng `assertSame(singletonA, singletonB)` khi muốn chứng minh singleton được chia sẻ. Trong Java CLI, dùng toán tử identity `==` / `!=` để in boolean.

Các IDs tự tăng của ví dụ cung cấp bằng chứng phụ trợ về construction, không thay thế identity assertions.

## 11. Constructor counter là một dụng cụ đo, không phải định nghĩa scope

Thêm `AtomicInteger` vào constructor:

```java
public final class TaskToken {
    static final AtomicInteger CREATED =
            new AtomicInteger();

    private final int id;

    public TaskToken() {
        id = CREATED.incrementAndGet();
    }

    public int id() {
        return id;
    }
}
```

Đây là mảnh class minh họa, cần import `AtomicInteger`.

Ý nghĩa:
- Một lần constructor chạy → counter tăng một.
- Hai object bình thường được constructor tạo → ids khác nhau.
- Không nên dùng static counter làm state production của bean.
- Nếu application tự `new TaskToken()` bên ngoài Spring, counter vẫn tăng dù không có BeanFactory lookup.
- Với AOP subclass proxies/Objenesis/FactoryBean hoặc constructor delegation phức tạp, đếm constructor không phải luôn cùng “số beans Spring quản lý”.

Vì vậy bộ test chính cần **assertSame/assertNotSame và BeanFactory scope APIs**; constructor counters giúp giải thích thời điểm tạo trong demo được kiểm soát.

## 12. isSingleton, isPrototype, containsSingleton

Spring có nhiều introspection methods:

```java
boolean singleton =
        context.isSingleton("sharedService");

boolean prototype =
        context.isPrototype("taskToken");

boolean cached =
        context.getBeanFactory()
                .containsSingleton("sharedService");
```

Đây là các mảnh minh họa; cần đúng context.

| Method | Câu hỏi |
| --- | --- |
| `isSingleton(name)` | Bean được expose theo singleton semantics không? |
| `isPrototype(name)` | Bean được expose theo prototype semantics không? |
| `containsSingleton(name)` | Có một singleton object đã registered/cached dưới name không? |
| `containsBeanDefinition(name)` | Có definition local dưới name không? |

**Không nhầm metadata với cache state**:
- Một singleton BeanDefinition `@Lazy` có thể `isSingleton=true` nhưng `containsSingleton=false` trước lần tạo đầu.
- Một prototype BeanDefinition có `isPrototype=true`; `containsSingleton` thường false ngay cả sau nhiều lần `getBean`.
- Một manually registered singleton `registerSingleton` có object cached, nhưng không bắt buộc có BeanDefinition.
- Với `FactoryBean`, `isSingleton(name)` nói về product semantics, `&name` nói về raw factory; cần kiểm tra theo đúng name.

Đừng dùng `getBeanDefinitionCount()` để đếm singleton objects.

## 13. Những cấp độ singleton dễ bị nhầm

### Một definition, một context

`@Bean SharedService sharedService()` mặc định singleton. Nhiều consumer lấy lại bean name đó → một shared instance.

### Hai definitions cùng class

```java
@Bean("primaryClient")
Client clientOne() {
    return new Client();
}

@Bean("secondaryClient")
Client clientTwo() {
    return new Client();
}
```

Các method minh họa này tạo **hai singleton definitions** khác nhau, thường **hai instances** khác nhau. Cả hai vẫn là singleton theo từng definition.

### Hai contexts độc lập

`new AnnotationConfigApplicationContext(AppConfig.class)` hai lần tạo hai BeanFactories. Mỗi context có singleton cache riêng; cùng bean name giữa hai contexts không có nghĩa cùng reference.

### Parent–child context

Nếu singleton bean thuộc parent và child không shadow local name, child có thể delegate lookup lên parent và nhận **cùng parent instance**. Nếu child định nghĩa cùng bean name, child local bean có thể shadow ancestor.

### ObjectProvider<Singleton>

Provider **không tạo lại singleton** mỗi lần getObject. Nó vẫn dựa vào container cache và bean definition.

## 14. Prototype lifecycle và hủy bean

Spring tạo, inject và thực hiện initialization callbacks cho prototype bean khi nó được requested. Nhưng **không tự quản lý full destruction lifecycle** của các prototype instances sau khi giao cho client.

Ví dụ:

```java
public final class WorkerResource
        implements AutoCloseable {

    @Override
    public void close() {
        // release resources
    }
}
```

Nếu `WorkerResource` được tạo theo prototype scope:
- Client phải quyết định khi nào tài nguyên không còn cần.
- Không mặc định `ApplicationContext.close()` tự gọi `destroy()`/`@PreDestroy` cho mọi prototype đã được lấy ra trước đó.
- Nếu mỗi provider call tạo một resource mới, client cần cleanup **cho từng instance**.

Thiết kế tốt thường là xử lý tác vụ bằng try-with-resources nếu bean thực sự được expose dưới API `AutoCloseable` và ownership rõ ràng. Với container-managed lifecycle đặc thù, có thể cân nhắc custom scope hoặc một owner/factory chịu trách nhiệm cleanup.

**Đừng biến singleton giữ prototype tài nguyên thành lỗ rò rỉ lâu dài** chỉ vì quên ownership.

## 15. Ưu tiên injection theo constructor

Với singleton:
```java
public final class JobRunner {
    private final ObjectProvider<TaskToken> tokenProvider;

    public JobRunner(ObjectProvider<TaskToken> tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    public void runOnce() {
        TaskToken token = tokenProvider.getObject();
        // process with a token independent from other runs
    }
}
```

Đây là mảnh minh họa.

Ưu điểm:
- Dependency rõ.
- Dễ thay provider bằng fake trong unit test.
- Không yêu cầu service cầm reference trực tiếp tới `ApplicationContext`.
- Provider target vẫn tôn trọng scope và candidate resolution.
- Không cần annotation field injection trên private mutable field.

Một trường hợp khác, nếu đối tượng chỉ là stateless singleton service, hãy inject trực tiếp service thay vì giữ provider không cần thiết. Provider là công cụ quản lý **thời điểm resolution**, không phải thứ mọi dependency đều cần.

## 16. @Lookup và scoped proxy có thể thay provider không?

**`@Lookup` method injection** yêu cầu Spring override một method trên bean instance để lấy dependency mới theo mỗi call:

```java
@Lookup
protected TaskToken createToken() {
    return null; // Spring override khi bean được tạo đúng cơ chế.
}
```

Đây là mảnh minh họa, **không phải runnable code độc lập**. Method injection đòi class/method proxyable/overrideable và BeanFactory tạo bean theo cách hỗ trợ lookup method overriding. Đặc biệt với objects được trả từ `@Bean` factory methods theo cách không cho Spring subclass/override như mong đợi, `@Lookup` có giới hạn được Spring Reference nêu rõ.

**Scoped proxy** giữ một proxy reference cố định tới scoped target. Với prototype target, mỗi method invocation có thể resolve target theo prototype semantics, nhưng việc đó khác việc provider trả **explicit new target object** cho caller: trả proxy có thể tạo target mới mỗi method call chứ không hẳn giữ một task object xuyên suốt workflow.

**ObjectProvider** thường rõ ràng nhất khi logic cần giữ một instance prototype cụ thể cho một operation:

```java
TaskToken token = provider.getObject();
doStepA(token);
doStepB(token);
```

Cả hai steps dùng **cùng token instance**. Đối với scoped proxy prototype, mỗi method call trên proxy có thể đi tới target mới; semantics cần được kiểm tra đúng use case.

## 17. Ví dụ hoàn chỉnh Java 17 + Spring Framework 6.2.18

Demo có:
- `SharedService` singleton với constructor counter.
- `TaskToken` prototype với counter và ID tăng.
- `DirectClient` singleton nhận một TaskToken trực tiếp.
- `ProviderClient` singleton nhận `ObjectProvider<TaskToken>`.
- `AnnotationConfigApplicationContext` tạo non-lazy singleton beans khi `refresh()`.
- Các câu `System.out` với output **xác định**, không dựa vào object identity hash hoặc thứ tự duyệt map.

Chúng ta **chủ động có một prototype được tạo khi context refresh** vì `DirectClient` singleton cần nó. Bài test kiểm chứng điều này thay vì suy rằng prototype count phải luôn bằng 0 lúc startup.

### 17.1. Cấu trúc thư mục

```text
spring-scope-count-test/
├── settings.gradle
├── build.gradle
└── src/
    ├── main/java/demo/ScopeProviderDemo.java
    └── test/java/demo/ScopeProviderTest.java
```

### 17.2. settings.gradle

```groovy
rootProject.name = 'spring-scope-count-test'
```

### 17.3. build.gradle

```groovy
plugins {
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.18'

    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'demo.ScopeProviderDemo'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

Có hai tasks:
- `gradle run --quiet` chạy demo in output.
- `gradle test` chạy JUnit assertions.

Phần `junit-platform-launcher` trong test runtime giúp Gradle/JUnit Platform tích hợp ổn định trong môi trường có version resolution phù hợp. Không cần `spring-boot-starter-test` vì đây là bài Spring Core Container thuần.

### 17.4. src/main/java/demo/ScopeProviderDemo.java

```java
package demo;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

public class ScopeProviderDemo {

    public static final class Counts {
        public static final AtomicInteger SHARED =
                new AtomicInteger();
        public static final AtomicInteger TOKENS =
                new AtomicInteger();

        public static void reset() {
            SHARED.set(0);
            TOKENS.set(0);
        }
    }

    public static final class SharedService {
        private final int id;

        public SharedService() {
            id = Counts.SHARED.incrementAndGet();
        }

        public int id() {
            return id;
        }
    }

    public static final class TaskToken {
        private final int id;

        public TaskToken() {
            id = Counts.TOKENS.incrementAndGet();
        }

        public int id() {
            return id;
        }
    }

    public record DirectClient(TaskToken token) {
    }

    public static final class ProviderClient {
        private final ObjectProvider<TaskToken> provider;

        public ProviderClient(
                ObjectProvider<TaskToken> provider) {
            this.provider = provider;
        }

        public TaskToken nextToken() {
            return provider.getObject();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {

        @Bean
        public SharedService sharedService() {
            return new SharedService();
        }

        @Bean
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        public TaskToken taskToken() {
            return new TaskToken();
        }

        @Bean
        public DirectClient directClient(TaskToken token) {
            return new DirectClient(token);
        }

        @Bean
        public ProviderClient providerClient(
                ObjectProvider<TaskToken> provider) {
            return new ProviderClient(provider);
        }
    }

    public static void main(String[] args) {
        Counts.reset();

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             AppConfig.class)) {

            System.out.println("refresh.sharedCreated="
                    + Counts.SHARED.get());
            System.out.println("refresh.tokensCreated="
                    + Counts.TOKENS.get());

            SharedService sharedOne =
                    context.getBean(SharedService.class);
            SharedService sharedTwo =
                    context.getBean(SharedService.class);

            System.out.println("singleton.same="
                    + (sharedOne == sharedTwo));
            System.out.println("singleton.createdAfterGet="
                    + Counts.SHARED.get());

            DirectClient direct =
                    context.getBean(DirectClient.class);

            TaskToken directFirst = direct.token();
            TaskToken directSecond = direct.token();

            System.out.println("direct.capturedId="
                    + directFirst.id());
            System.out.println("direct.same="
                    + (directFirst == directSecond));
            System.out.println("direct.tokenCount="
                    + Counts.TOKENS.get());

            TaskToken one =
                    context.getBean(TaskToken.class);
            TaskToken two =
                    context.getBean(TaskToken.class);

            System.out.println("prototype.ids="
                    + one.id() + "," + two.id());
            System.out.println("prototype.same="
                    + (one == two));
            System.out.println("prototype.tokenCount="
                    + Counts.TOKENS.get());

            int beforeHandle = Counts.TOKENS.get();

            ObjectProvider<TaskToken> handle =
                    context.getBeanProvider(TaskToken.class);

            System.out.println("provider.handleCreatedBean="
                    + (Counts.TOKENS.get() > beforeHandle));

            ProviderClient client =
                    context.getBean(ProviderClient.class);

            TaskToken fromProviderOne =
                    client.nextToken();
            TaskToken fromProviderTwo =
                    client.nextToken();

            System.out.println("provider.ids="
                    + fromProviderOne.id() + ","
                    + fromProviderTwo.id());
            System.out.println("provider.same="
                    + (fromProviderOne == fromProviderTwo));
            System.out.println("provider.tokenCount="
                    + Counts.TOKENS.get());

            System.out.println("provider.directIsDifferent="
                    + (directFirst != fromProviderOne));

            ObjectProvider<SharedService> sharedProvider =
                    context.getBeanProvider(SharedService.class);

            System.out.println("providerOfSingleton.same="
                    + (sharedProvider.getObject()
                            == sharedProvider.getObject()));

            System.out.println("providerOfSingleton.count="
                    + Counts.SHARED.get());

            System.out.println("scope.sharedSingleton="
                    + context.isSingleton("sharedService"));
            System.out.println("scope.tokenPrototype="
                    + context.isPrototype("taskToken"));
            System.out.println("cache.tokenSingleton="
                    + context.getBeanFactory()
                            .containsSingleton("taskToken"));

            // handle chỉ chứng minh provider được lấy ra;
            // không gọi handle.getObject() để giữ output ổn định.
            if (handle == null) {
                throw new AssertionError("Unexpected null provider");
            }
        }
    }
}
```

### 17.5. Chạy demo

Cần JDK 17+ và Gradle 8+:

```bash
gradle run --quiet
```

### 17.6. Stdout dự kiến

```text
refresh.sharedCreated=1
refresh.tokensCreated=1
singleton.same=true
singleton.createdAfterGet=1
direct.capturedId=1
direct.same=true
direct.tokenCount=1
prototype.ids=2,3
prototype.same=false
prototype.tokenCount=3
provider.handleCreatedBean=false
provider.ids=4,5
provider.same=false
provider.tokenCount=5
provider.directIsDifferent=true
providerOfSingleton.same=true
providerOfSingleton.count=1
scope.sharedSingleton=true
scope.tokenPrototype=true
cache.tokenSingleton=false
```

Đây là **20 dòng chương trình tự in**, không bao gồm logging của Gradle/Spring. Static counters được reset **trước khi tạo context**. Các số ID dựa trên đúng số lần constructor của `TaskToken` chạy trong cấu hình nhỏ này. Không sử dụng ngẫu nhiên, luồng chạy đồng thời hoặc truy cập network.

### 17.7. Giải thích thứ tự

**Sau refresh:** một `SharedService` được tạo, và một `TaskToken` được tạo để inject vào `DirectClient` singleton. `ProviderClient` chỉ nhận provider handle nên **không tạo TaskToken khác** lúc khởi tạo.

**Singleton lookup:** `sharedOne == sharedTwo` là true; constructor counter vẫn 1.

**Direct injection:** `direct.token()` lặp lại cùng reference `TaskToken#1`, và không tự tăng counter.

**Prototype lookup:** hai `context.getBean(TaskToken.class)` tạo Token #2 và #3. Hai references khác nhau.

**Provider handle:** `context.getBeanProvider(TaskToken.class)` không tự lấy TaskToken instance trong thí nghiệm; counter vẫn 3.

**Provider.getObject:** `ProviderClient.nextToken()` gọi provider.getObject hai lần; tạo #4 và #5; count lên 5.

**Provider của singleton:** dù gọi hai lần `getObject()`, vẫn trả cùng `SharedService#1`.

**Scope và cache:** `isPrototype("taskToken")` true, còn `containsSingleton("taskToken")` false, vì prototype instances không được cất như một shared singleton object.

## 18. Bộ JUnit 5 tests độc lập

Các tests bên dưới dùng **context thật**, không mock Spring container. Mỗi test tạo context mới trong `try-with-resources` và reset counters trước đó. Điều này giúp assertions không dựa vào thứ tự thực thi giữa các test methods.

### 18.1. src/test/java/demo/ScopeProviderTest.java

```java
package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import demo.ScopeProviderDemo.AppConfig;
import demo.ScopeProviderDemo.Counts;
import demo.ScopeProviderDemo.DirectClient;
import demo.ScopeProviderDemo.ProviderClient;
import demo.ScopeProviderDemo.SharedService;
import demo.ScopeProviderDemo.TaskToken;

class ScopeProviderTest {

    private AnnotationConfigApplicationContext openContext() {
        Counts.reset();
        return new AnnotationConfigApplicationContext(
                AppConfig.class);
    }

    @Test
    void singletonIsSharedWithinOneContext() {
        try (var context = openContext()) {
            assertEquals(1, Counts.SHARED.get());

            SharedService first =
                    context.getBean(SharedService.class);
            SharedService second =
                    context.getBean(SharedService.class);

            assertSame(first, second);
            assertEquals(1, Counts.SHARED.get());
            assertTrue(context.isSingleton("sharedService"));
        }
    }

    @Test
    void directPrototypeDependencyIsCapturedOnce() {
        try (var context = openContext()) {
            // DirectClient singleton đã được khởi tạo lúc refresh.
            assertEquals(1, Counts.TOKENS.get());

            DirectClient client =
                    context.getBean(DirectClient.class);

            TaskToken firstUse = client.token();
            TaskToken secondUse = client.token();

            assertSame(firstUse, secondUse);
            assertEquals(1, firstUse.id());
            assertEquals(1, Counts.TOKENS.get());
        }
    }

    @Test
    void prototypeGetBeanReturnsDifferentInstances() {
        try (var context = openContext()) {
            assertEquals(1, Counts.TOKENS.get());

            TaskToken first = context.getBean(TaskToken.class);
            TaskToken second = context.getBean(TaskToken.class);

            assertNotSame(first, second);
            assertEquals(2, first.id());
            assertEquals(3, second.id());
            assertEquals(3, Counts.TOKENS.get());
            assertTrue(context.isPrototype("taskToken"));
            assertFalse(context.getBeanFactory()
                    .containsSingleton("taskToken"));
        }
    }

    @Test
    void providerResolvesANewPrototypeEachCall() {
        try (var context = openContext()) {
            assertEquals(1, Counts.TOKENS.get());

            ProviderClient client =
                    context.getBean(ProviderClient.class);

            // Lấy ProviderClient không tự tạo TaskToken mới.
            assertEquals(1, Counts.TOKENS.get());

            TaskToken first = client.nextToken();
            TaskToken second = client.nextToken();

            assertNotSame(first, second);
            assertEquals(2, first.id());
            assertEquals(3, second.id());
            assertEquals(3, Counts.TOKENS.get());
        }
    }

    @Test
    void providerHandleDoesNotImmediatelyConstructTarget() {
        try (var context = openContext()) {
            int before = Counts.TOKENS.get();

            ObjectProvider<TaskToken> provider =
                    context.getBeanProvider(TaskToken.class);

            assertEquals(before, Counts.TOKENS.get());

            TaskToken created = provider.getObject();

            assertEquals(before + 1, Counts.TOKENS.get());
            assertEquals(before + 1, created.id());
        }
    }

    @Test
    void providerOfSingletonStillReturnsSameInstance() {
        try (var context = openContext()) {
            ObjectProvider<SharedService> provider =
                    context.getBeanProvider(SharedService.class);

            SharedService first = provider.getObject();
            SharedService second = provider.getObject();

            assertSame(first, second);
            assertEquals(1, Counts.SHARED.get());
        }
    }
}
```

### 18.2. Chạy test

```bash
gradle test
```

Kết quả mong đợi: **6 tests pass** khi chạy riêng tuần tự theo mặc định cấu hình JUnit/Gradle của dự án. Trình chạy có thể không in cụ thể tên mọi test trên console mặc định; dùng Gradle HTML/XML test reports nếu cần xác nhận từng case.

**Không** nên bật parallel test execution rồi để tất cả methods tùy ý reset cùng một static counter: tests trong ví dụ chia sẻ static counters của class. Nếu muốn chạy song song, mỗi test cần isolated counter state hoặc không chia sẻ static counters giữa contexts.

### 18.3. Vì sao mỗi test tạo context mới?

Mỗi context có singleton registry riêng. Nếu dùng chung context giữa các tests:
- Một test có thể đã tạo prototype #2, #3.
- Test khác kỳ vọng count ban đầu = 1 sẽ sai.
- Test fail do shared mutable counter/order, **không phải do Spring scope semantics**.

Tạo context mới và reset counts rõ trước test giúp logic deterministic. Dùng `try-with-resources` bảo đảm đóng context, không giữ stale references và tránh rò lifecycle resources qua tests.

## 19. Test lifecycle của prototype không có nghĩa test số lượng destruction

Nếu `TaskToken` triển khai `DisposableBean`, Spring vẫn **không tự gọi destruction callback cho các prototype instances đã cấp cho caller** khi chỉ đóng context.

Kiểm chứng nâng cao:

```java
public static class DisposableTask
        implements DisposableBean {

    static final AtomicInteger DESTROYED =
            new AtomicInteger();

    @Override
    public void destroy() {
        DESTROYED.incrementAndGet();
    }
}
```

Đây là class minh họa chưa chứa @Bean config và test runner đầy đủ. Khi prototype được tạo nhiều lần, đóng context không nên tự đồng nhất với `DESTROYED == createdCount`.

Khác với singleton, prototype lifecycle ownership sau creation được giao cho client. Muốn kiểm chứng resource cleanup, test code phải mô tả **ai gọi close/destroy và khi nào**, không suy từ việc count các instances.

## 20. ObjectProvider khác ObjectFactory và Jakarta Provider

- `ObjectFactory<T>` (Spring) là API đơn giản có `getObject()`.
- `ObjectProvider<T>` extends `ObjectFactory<T>` và cung cấp optional/stream/iteration APIs.
- `jakarta.inject.Provider<T>` là JSR-330/Jakarta Inject provider abstraction, có `get()` và semantics phụ thuộc injection framework; khi Spring hỗ trợ dependency injection đó, scope của target vẫn được Spring BeanFactory quyết định.
- `Supplier<T>` của Java tự nó không phải Spring BeanFactory provider; nếu developer truyền `() -> new TaskToken()`, behavior hoàn toàn do lambda quyết định.

`ObjectProvider<T>` được ưu tiên trong demo vì là API Spring trực tiếp và các methods được Spring 6.2 Javadoc mô tả rõ. Nếu muốn giảm framework coupling ở application service, có thể tự định nghĩa domain factory interface và inject adapter sử dụng Spring provider.

## 21. Kiểm thử provider khi thiếu bean hoặc nhiều candidates

### Không có matching bean

```java
ObjectProvider<OptionalFeature> provider =
        context.getBeanProvider(OptionalFeature.class);

OptionalFeature absent = provider.getIfAvailable();
```

Đây là mảnh minh họa; nếu không có matching bean thì `absent` có thể null. `provider.getObject()` không tương đương: thường ném `NoSuchBeanDefinitionException` nếu không có bean.

### Nhiều beans cùng type

Nếu có hai candidates không có selection rule phù hợp, `getObject()` có thể ném `NoUniqueBeanDefinitionException`. `getIfUnique()` có thể trả null, tùy resolution semantics. `@Primary`, `@Qualifier`, fallback/default candidates và beans trong parent context có thể ảnh hưởng resolution.

### Không dùng provider như “chữa mọi lỗi”

Provider chỉ hoãn **thời điểm tra cứu**, không làm thiếu bean trở thành bean có thật hoặc giải mọi ambiguity. Bài kiểm thử đúng phải test cả happy path lẫn error path khi module có optional/multiple beans.

## 22. Provider với scope khác prototype

`ObjectProvider<T>` tôn trọng scope target. Ví dụ:

| Scope đích | Provider.getObject() được gọi nhiều lần |
| --- | --- |
| singleton | Cùng singleton bean instance |
| prototype | Thường instance mới mỗi lần call |
| request | Cùng request-scoped bean trong một HTTP request; sang request khác có thể khác |
| session | Cùng session-scoped bean trong session; khác session có thể khác |
| custom scope | Theo chính contract của custom scope implementation |

Cần web-aware ApplicationContext cho request/session scopes. Trong console demo không có request/session active, nên không đưa những scopes đó vào chương trình chạy. Khi request scope chưa active, provider call vẫn có thể ném `ScopeNotActiveException` lúc resolve target; nhận provider handle không tự tạo request.

## 23. Nếu bean thực tế là proxy thì kiểm chứng bằng gì?

Một proxy có thể thay đổi quan sát:
- `getBean("name")` trả **proxy reference** chứ không trả raw target.
- Singleton proxy có thể là cùng reference qua các lookups.
- Scoped proxy có thể là cùng proxy object nhưng delegate tới target scope thay đổi theo request/method.
- CGLIB subclass proxy có thể tác động constructor instrumentation behavior tùy cấu hình.

Vì thế khi mục tiêu test scope của **target**, cần tách **proxy instance identity** và **target lifecycle**:
- Dùng simple classes không proxy trong bài scope cơ bản.
- Với scoped proxy tests, đo target IDs trong callback/method hoặc kiểm tra active scope.
- Với transaction/AOP proxies, `AopUtils.isAopProxy` hữu ích cho diagnostics nhưng không cho bạn biết có bao nhiêu raw target instances được tạo chỉ từ proxy reference identity.

Đây là lý do demo không bật `@EnableAspectJAutoProxy` hay scoped proxies: giảm variables và kiểm chứng chính xác quy tắc scope.

## 24. FactoryBean cũng có hai tầng singleton

`FactoryBean<T>` có thể cung cấp product objects qua `getObject()` và báo product shared bằng `isSingleton()`.

Không nhầm:
- Scope của **FactoryBean object** do BeanFactory quản lý.
- Singleton/prototype semantics của **product object** do FactoryBean mô tả và cache behavior liên quan.
- `getBean("factoryName")` thường trả product; `getBean("&factoryName")` trả raw FactoryBean.

Ví dụ đơn giản của bài scope không dùng FactoryBean để tránh đếm nhầm một factory object là product.

Nếu test một BeanFactory đặc biệt có factory bean, cần instrument **factory constructor**, **getObject()**, **product constructor** riêng. Chỉ đếm một AtomicInteger trên factory class không thể kết luận số products đã được tạo.

## 25. Anti-pattern khiến số instance bị hiểu sai

| Anti-pattern | Vì sao sai | Cách sửa |
| --- | --- | --- |
| `@Scope("prototype")` rồi inject trực tiếp vào singleton và chờ luôn mới | Dependency chỉ resolve một lần lúc tạo singleton | Inject provider/factory |
| `assertNotEquals` để chứng minh prototype | Equals có thể so theo value | `assertNotSame` |
| Tính constructor counter không reset giữa tests | Test order ảnh hưởng kết quả | Tạo context độc lập, reset trước test |
| So sánh object hashCode | Hashcode có thể override/collision | Identity `==` / `assertSame` |
| Gọi getBeanDefinitionCount để đếm instances | Definitions không phải objects | Instrument/identity checks |
| Nhìn `containsSingleton=false` rồi nói bean không tồn tại | Lazy/prototype không cached | `containsBean`, `isPrototype`, bean definition query |
| Dùng `provider.stream()` và mong vô hạn prototypes | Stream liệt kê candidates | `getObject` mỗi operation |
| Dùng provider của singleton nhưng mong object mới | Provider tuân target scope | Đổi target scope hoặc explicit factory |
| Dùng `@Lookup` trên final class/method | Cannot override | Provider constructor injection |
| Tạo objects với `new` trong test thay vì BeanFactory | Bỏ qua scope container | Test context thực cho integration |
| Dùng cùng context/static counter trong tests parallel | Mutable state gây race | Isolate counters hoặc disable parallel |
| Đóng context rồi nghĩ prototypes auto destroyed | Prototype cleanup không container-managed | Explicit cleanup |
| Dùng scoped proxy và chỉ test proxy identity | Proxy có thể che target switching | Đo target identity per invocation |
| Trộn FactoryBean object count và product count | Factory khác product | Hai counters và `&` lookup |
| Dùng `@Bean(proxyBeanMethods=false)` rồi gọi trực tiếp config method | Direct method invocation không phải BeanFactory lookup | Lấy bean từ context/provider |
| Thay đổi @Scope mà không rebuild context | Already created instances vẫn tồn tại | Mở context mới và test |

## 26. Kiểm thử hai ApplicationContexts

Một test minh họa lý do singleton không global JVM:

```java
Counts.reset();

try (var first =
             new AnnotationConfigApplicationContext(AppConfig.class);
     var second =
             new AnnotationConfigApplicationContext(AppConfig.class)) {

    SharedService fromOne =
            first.getBean(SharedService.class);
    SharedService fromTwo =
            second.getBean(SharedService.class);

    assertNotSame(fromOne, fromTwo);
}
```

Đây là mảnh test minh họa đặt trong class có imports phù hợp. Ở đây mỗi context tạo own singleton bean definition và own singleton instance; sau hai context `Counts.SHARED.get()` dự kiến là 2.

Trong parent–child hierarchy, lookup lên parent có thể lấy cùng parent singleton nhưng child không tự copy. Nếu child cũng định nghĩa một bean cùng name, local shadowing khiến instance có thể khác.

## 27. Một số câu hỏi kiểm tra hiểu biết

**Hỏi:** Bean prototype được đăng ký nhưng chưa ai gọi, có tự tạo ngay không?

**Đáp:** Không mặc định. Một prototype sẽ được tạo khi được yêu cầu, ví dụ qua `getBean` hoặc để inject vào singleton khác. `refresh` không có nghĩa tất cả prototype definitions được instantiate.

**Hỏi:** Inject prototype vào singleton và gọi method singleton mười lần có tạo mười prototypes không?

**Đáp:** Không, nếu singleton giữ reference trực tiếp. Nó thường có một prototype instance được inject lúc singleton tạo. Cần provider/factory/lookup mechanism nếu muốn tạo theo operation.

**Hỏi:** `ObjectProvider<Singleton>.getObject()` gọi mười lần có mười singleton không?

**Đáp:** Không. Nó resolve target theo scope singleton và thường trả cùng object cached.

**Hỏi:** Có thể có hai singleton objects cùng class trong một context không?

**Đáp:** Có, nếu chúng thuộc hai bean definitions/names khác nhau hoặc những trường hợp registration đặc biệt. Singleton không có nghĩa một instance duy nhất cho cả Java class.

**Hỏi:** Nếu `TaskToken` là record với cùng content, dùng `equals` để test prototype được không?

**Đáp:** Không đủ. Dùng Java identity `!=` hoặc JUnit `assertNotSame`; hai objects có thể bằng giá trị.

**Hỏi:** Spring tự destroy mọi prototype instance khi context đóng chứ?

**Đáp:** Không. Spring quản lý initialization, nhưng theo Reference, destruction lifecycle của prototype sau khi giao cho client không tự hoàn tất như singleton.

**Hỏi:** Nếu tạo `ObjectProvider` có tức đã tạo target bean?

**Đáp:** Không mặc định. Provider là handle; target được resolve khi gọi retrieval APIs như `getObject()`. Tuy nhiên infrastructure/type discovery có thể có các side effects riêng trong trường hợp nâng cao; bài demo kiểm soát để chứng minh rõ.

## 28. Checklist hoàn thành bài và áp dụng thực tế

1. Đã xác định bean names và scopes chính xác.
2. Biết singleton là per definition/per context, không phải toàn JVM.
3. Có counter tăng trong constructor của target type cần đo.
4. Có `assertSame` cho hai lookups singleton trong cùng context.
5. Có `assertNotSame` cho hai lookups prototype.
6. Đã kiểm chứng một prototype được tạo khi inject vào singleton.
7. Đã chứng minh direct-injected prototype reference giữ nguyên qua nhiều calls.
8. Đã chứng minh provider handle không mặc định instantiate target.
9. Đã chứng minh provider của prototype tạo target mới mỗi getObject.
10. Đã chứng minh provider của singleton vẫn trả cùng singleton.
11. Đã kiểm tra `isPrototype`/`isSingleton` và cache state riêng.
12. Mỗi test có context độc lập và có cleanup.
13. Test chạy tuần tự hoặc shared state đã được cô lập.
14. Nếu prototype giữ resource, đã xác định owner cleanup.
15. Nếu có AOP/FactoryBean/scoped proxy, đã tách proxy/product/target counting.
16. Không dựa vào `equals` hoặc hashcode để kiểm chứng identity.
17. Không dùng global static counters trong production code.
18. Đã test optional/multiple providers nếu module có extension points.
19. Đã đối chiếu số constructor calls với injection graph thực sự.
20. Nếu thay scope/configuration, đã tạo lại context trước khi assert.

## 29. Tài liệu chính thức

1. [Spring Framework 6.2 Reference — Bean Scopes](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-scopes.html): singleton, prototype, injection prototype vào singleton, lifecycle và scopes.
2. [Spring Framework 6.2 Reference — Method Injection](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-method-injection.html): lấy prototype mới theo nhu cầu từ singleton.
3. [Spring Framework 6.2.18 Javadoc — ObjectProvider](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/beans/factory/ObjectProvider.html): getObject/getIfAvailable/getIfUnique/stream và BeanFactory-bound dynamic resolution.
4. [Spring Framework 6.2.18 Javadoc — ObjectFactory](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/beans/factory/ObjectFactory.html): factory access abstraction.
5. [Spring Framework 6.2.18 Javadoc — ConfigurableBeanFactory](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/beans/factory/config/ConfigurableBeanFactory.html): singleton/prototype scope names và singleton registry.
6. [Spring Framework 6.2.18 Javadoc — BeanFactory](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/beans/factory/BeanFactory.html): getBean/isSingleton/isPrototype/getBeanProvider.
7. [Spring Framework 6.2.18 Javadoc — AnnotationConfigApplicationContext](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/context/annotation/AnnotationConfigApplicationContext.html): Java configuration context and lifecycle.
8. [JUnit Jupiter User Guide](https://docs.junit.org/5.11.4/user-guide/): `@Test`, assertions and test lifecycle.
9. [Spring Framework 6.2 Reference — Bean Lifecycle](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-nature.html): initialization/destruction callbacks.
10. [Spring Framework 6.2.18 Javadoc — FactoryBean](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/beans/factory/FactoryBean.html): product singleton semantics vs factory instance.

**Kết luận:** Singleton được **chia sẻ trong phạm vi một bean definition và container**; prototype được **tạo mới khi thực sự được yêu cầu**. Một singleton inject prototype **trực tiếp** giữ nguyên prototype reference được resolve lúc khởi tạo, còn một singleton inject `ObjectProvider<Prototype>` có thể gọi `getObject()` nhiều lần để nhận những prototype instances mới. Provider **không thay đổi scope**: `ObjectProvider<Singleton>` vẫn trả singleton. Hãy kiểm chứng bằng identity assertions, constructor counters, independent contexts và lifecycle ownership rõ ràng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="container-testing-006">Kiểm chứng thứ tự callback khởi tạo, hủy và đóng context</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm chứng thứ tự callback khởi tạo, hủy và đóng context trong Spring Core Container

## 1. Mục tiêu và các nguyên tắc kiểm thử

Khi Spring IoC Container tạo một bean, constructor chạy **trước** khi quá trình injection và initialization hoàn tất. Khi ứng dụng dừng, `ApplicationContext.close()` không chỉ gọi một method `close()` đơn giản trên object; Spring phát event đóng context, điều phối các `Lifecycle` components và hủy những **singleton beans mà container sở hữu**.

Vì vậy, chỉ thấy log `@PostConstruct` hoặc `@PreDestroy` chưa đủ để bảo đảm một ứng dụng quản lý tài nguyên đúng. Các lỗi thực tế gồm worker còn chạy khi datasource đã bị destroy, một `BeanPostProcessor` can thiệp sai pha, một test mong `@PreDestroy` chạy ở prototype bean, hay một `@PostConstruct` dùng collaborator chưa initialize xong.

Bài này giải thích và kiểm chứng:

- **Creation:** instantiate → populate/inject dependencies → `Aware` callbacks.
- **Initialization:** `BeanPostProcessor` trước init, `@PostConstruct`, `InitializingBean.afterPropertiesSet()`, custom `initMethod`, `BeanPostProcessor` sau init.
- **Refresh:** hoàn tất normal singleton initialization, xử lý `SmartInitializingSingleton`/ `SmartLifecycle`, publish `ContextRefreshedEvent`.
- **Normal shutdown:** publish `ContextClosedEvent`, gọi `LifecycleProcessor.onClose()` dừng running components, destroy managed singleton beans.
- **Destruction:** `@PreDestroy`, `DisposableBean.destroy()`, custom `destroyMethod`; dependent beans destroy trước dependencies.
- **Test reliability:** kiểm tra quan hệ trước–sau **có contract**, không cố định toàn bộ thứ tự log giữa các beans độc lập.

> **Quy tắc:** Kiểm thử thứ tự callback nên ghi trace và assert **partial order**. Không dùng thứ tự lines trong console như hợp đồng khi Spring không bảo đảm relative order giữa các processors hoặc singleton beans độc lập.

Các ví dụ dùng **Java 17, Spring Framework 6.2 và Jakarta annotations**. Demo chính là một chương trình Maven hoàn chỉnh, chạy không cần Spring Boot hay web server.

## 2. Bốn tầng lifecycle không được nhầm lẫn

| Tầng | Thành phần | Câu hỏi cần kiểm thử |
|---|---|---|
| Tạo instance | Constructor, factory method, `@Autowired` | Dependency có được inject trước init không? |
| Khởi tạo bean | `Aware`, BPP, `@PostConstruct`, `InitializingBean`, custom init | Có đúng thứ tự trong **một bean** không? |
| Vận hành context | `SmartLifecycle`, `LifecycleProcessor`, context events | Auto-start, explicit start/stop và phase có đúng không? |
| Hủy | `ContextClosedEvent`, stop signal, destroy callbacks | Có đóng đủ resources và đúng thứ tự dependency không? |

**`refresh()` khác `start()`:** Một context chuẩn khi refresh có thể auto-start các `SmartLifecycle` beans có `isAutoStartup()==true`. Nhưng `refresh()` không tự phát `ContextStartedEvent`. `ContextStartedEvent` thường xuất phát từ `context.start()` tường minh.

**`stop()` khác `close()`:** `context.stop()` phát `ContextStoppedEvent` và điều phối lifecycle stop, nhưng không phải một lệnh destroy tất cả singletons. `context.close()` là bước shutdown cuối cùng, gồm event, lifecycle stop và singleton destruction. Đừng viết test `@PreDestroy` dựa riêng vào `context.stop()`.

**`@PostConstruct` khác `SmartLifecycle.start()`:** `@PostConstruct` chạy trong lifecycle khởi tạo **bean đó**, còn `SmartLifecycle.start()` dùng để bật thành phần vận hành (worker, queue consumer, scheduler) sau khi container đã tạo beans.

## 3. Timeline khởi tạo một managed singleton

```text
Container chọn BeanDefinition / factory method
    |
    v
Instantiate bean (constructor)
    |
    v
Populate properties / @Autowired fields, setters
    |
    v
Aware callbacks (BeanNameAware, BeanFactoryAware, ...)
    |
    v
BeanPostProcessor.beforeInitialization(...)
    |-- @PostConstruct được kích hoạt trong một BPP
    |-- custom BPP-before theo thứ tự processor registration
    |
    v
InitializingBean.afterPropertiesSet()
    |
    v
Custom init method (@Bean(initMethod = "..."))
    |
    v
BeanPostProcessor.afterInitialization(...)
    |-- có thể tạo proxy
    |
    v
Singleton sẵn sàng, expose cho clients
```

Đây là **sơ đồ khái quát**, không phải toàn bộ implementation; `InstantiationAwareBeanPostProcessor`, definition merging, constructor resolution, AOP early reference và circular dependency có thể tạo thêm bước.

### 3.1. Constructor và dependency injection

Nếu bean có no-arg constructor rồi dùng field injection, lúc constructor chạy những annotated fields có thể **chưa được inject**. `@PostConstruct` thường chạy sau population nên phù hợp để validate dependencies đã tồn tại.

```java
public class CacheConsumer {
    @Autowired
    private CacheRepository repository;

    @PostConstruct
    public void validate() {
        if (repository == null) {
            throw new IllegalStateException("CacheRepository missing");
        }
    }
}
```

Đây là snippet tích hợp; cần classes, registration và imports `org.springframework.beans.factory.annotation.Autowired`, `jakarta.annotation.PostConstruct`. Với constructor injection, dependency bắt buộc được truyền ngay vào constructor và có thể lưu trong `final` field.

### 3.2. Aware callbacks

`BeanNameAware`, `BeanClassLoaderAware`, `BeanFactoryAware` được container cấp những thông tin tương ứng; các context-aware interfaces như `ApplicationContextAware` được xử lý qua processors thích hợp. Đừng gộp chúng với post-init AOP callback: nhiều `Aware` calls chạy **trước** `InitializingBean.afterPropertiesSet()`.

Một test nên assert `BeanNameAware.setBeanName("tracked")` được gọi và tên đúng, không khóa cứng toàn bộ thứ tự giữa mọi `Aware` interfaces của framework.

## 4. Kết hợp ba cơ chế init trên cùng bean

Spring Framework Reference 6.2 quy định với **các callback methods khác tên**:

1. `jakarta.annotation.PostConstruct`.
2. `InitializingBean.afterPropertiesSet()`.
3. Custom configured init method, ví dụ `@Bean(initMethod="customInit")`.

```java
public final class MyService implements InitializingBean {

    @PostConstruct
    public void annotationInit() {
        // 1. @PostConstruct
    }

    @Override
    public void afterPropertiesSet() {
        // 2. InitializingBean
    }

    public void customInit() {
        // 3. BeanDefinition init method
    }
}
```

Đây là snippet tích hợp, cần bean configuration có `initMethod="customInit"` và imports. Khi một method được chỉ định bởi **nhiều lifecycle mechanisms cùng tên**, Spring có xử lý tránh gọi trùng ở những cases được tài liệu mô tả. Để test rõ thứ tự, dùng ba method names riêng biệt.

### 4.1. @PostConstruct thực chất nằm trong một BPP

`CommonAnnotationBeanPostProcessor` / `InitDestroyAnnotationBeanPostProcessor` là hạ tầng nhận diện `@PostConstruct`. Vì vậy, `@PostConstruct` được gọi trong đường **BeanPostProcessor-before-initialization**.

Nếu còn một custom BPP-before:

```java
@Override
public Object postProcessBeforeInitialization(
        Object bean, String name) {
    return bean;
}
```

không nên assert custom BPP-before luôn đứng ngay trước `@PostConstruct`. **Relative ordering giữa các BPPs** phụ thuộc `PriorityOrdered`, `Ordered`, registration order và internal post-processor policies.

Những relations đáng test:

- `BeanNameAware` trước init.
- Custom BPP-before trước `afterPropertiesSet()`.
- `@PostConstruct` trước `afterPropertiesSet()`.
- `afterPropertiesSet()` trước custom init.
- Custom init trước BPP-after.

### 4.2. Một custom init không đi qua AOP advice trên chính nó

Trong đường tạo một Spring AOP bean, các init callbacks thường được gọi trên **raw target** trước khi proxy của bean hoàn chỉnh được expose. Đừng dựa vào `@Transactional`, `@Async` hoặc caching proxy advice trên một call nội bộ ở `@PostConstruct`/custom init. Khi cần chạy logic phải đi qua proxy, thiết kế một điểm kích hoạt **sau context refresh**, dùng một orchestration bean hoặc event handler phù hợp.

### 4.3. Đừng dùng @PostConstruct cho tác vụ dài

Spring Reference cảnh báo initialization có thể chạy trong singleton creation lock. Logic này nên validate config hoặc chuẩn bị state cục bộ; tránh blocking network calls hoặc gọi `getBean` tùy tiện.

Nếu thực sự cần work sau khi tất cả normal singletons sẵn sàng, xem `SmartInitializingSingleton.afterSingletonsInstantiated()` hoặc `ContextRefreshedEvent`. Nếu cần stop/restart được, dùng `SmartLifecycle`.

## 5. BeanPostProcessor trước và sau initialization

`BeanPostProcessor` có hooks:

```java
Object postProcessBeforeInitialization(Object bean, String beanName);
Object postProcessAfterInitialization(Object bean, String beanName);
```

BPP-before chạy sau property population và trước init callbacks như `InitializingBean`/custom init. BPP-after chạy sau những callbacks đó. **Object được trả về có thể là proxy hoặc wrapper**, không nhất thiết là instance ban đầu.

### 5.1. Tránh assertion sai về identity

Một test AOP có thể thấy:

```java
Object exposed = context.getBean("paymentService");
```

trả proxy thay vì raw object. Nếu muốn bảo đảm advice hoạt động, test **behavior qua `exposed`** và dùng `AopUtils.isAopProxy` khi cần. Không assert `exposed == rawTarget` như một yêu cầu chung.

### 5.2. Registration của BPP

Trong Java Config, nên tạo custom BPP bằng `static @Bean` khi phù hợp:

```java
@Bean
public static BeanPostProcessor monitor() {
    return new MyMonitoringBeanPostProcessor();
}
```

Snippet tích hợp, cần processor class. Static method cho phép Spring chuẩn bị processor sớm mà không phải tạo instance của configuration class trước khi BPP infrastructure đầy đủ.

Nếu BPP cần dependency constructor, dependency này có thể được tạo sớm và **không eligible for all BeanPostProcessors**. Một recorder đơn giản không cần AOP/injection là lựa chọn tốt cho integration test.

## 6. Thứ tự hủy trong một managed singleton

Spring Reference 6.2 xác nhận với callback methods khác tên:

1. `jakarta.annotation.PreDestroy`.
2. `DisposableBean.destroy()`.
3. Custom configured destroy method, ví dụ `@Bean(destroyMethod="customDestroy")`.

```java
public final class ResourceHolder implements DisposableBean {

    @PreDestroy
    public void preDestroy() {
        // 1
    }

    @Override
    public void destroy() {
        // 2
    }

    public void customDestroy() {
        // 3
    }
}
```

Đây là snippet tích hợp với Jakarta/Spring imports và `@Bean` configuration. Trong test, assert ba events **đúng một lần** và có thứ tự nói trên.

### 6.1. Khi nào Spring suy luận destroy method?

Với `@Bean`, Spring có thể suy luận public `close()` hoặc `shutdown()` của object làm destroy callback. Khi object được quản lý bởi một hệ thống ngoài hoặc một `AutoCloseable` không nên đóng qua Spring, dùng `@Bean(destroyMethod="")` để vô hiệu hóa inferred method nếu phù hợp.

Trong ví dụ kiểm chứng, khai báo custom `destroyMethod` rõ để không phụ thuộc tên method được suy luận.

### 6.2. Prototype không tự destroy bằng close context

Container quản lý tạo và initialization prototype, nhưng **không tự hủy từng prototype** sau khi caller nhận instance. Vì vậy:

```java
PrototypeResource resource =
        context.getBean(PrototypeResource.class);
context.close();
```

không phải bằng chứng rằng `@PreDestroy` trên `resource` sẽ chạy. Snippet này chỉ minh họa với `PrototypeResource` có scope prototype. Caller chịu trách nhiệm cleanup hoặc tự dùng API thích hợp như `AutowireCapableBeanFactory.destroyBean`.

### 6.3. External object cũng không tự thành context-owned singleton

`new ExternalWorker()`, `autowireBean(existing)`, `initializeBean(existing,name)` hoặc `registerSingleton(name,existing)` không có nghĩa mọi callback/lifecycle của object ấy tương đương bean được tạo từ `BeanDefinition`. Phải xác định ownership và cleanup contract; đừng xây test close context dựa trên một object Spring không thực sự quản lý đầy đủ.

## 7. Close pipeline: sự kiện, dừng, hủy

Trong một **normal close** của `AbstractApplicationContext` active:

```text
context.close()
    |
    +--> publish ContextClosedEvent
    |
    +--> LifecycleProcessor.onClose()
    |      +--> stop SmartLifecycle / Lifecycle running beans
    |      +--> respect phases and depends-on
    |
    +--> destroy managed singletons
    |      +--> @PreDestroy
    |      +--> DisposableBean.destroy
    |      +--> custom destroy method
    |      +--> dependent before dependency
    |
    +--> release BeanFactory/context infrastructure
```

`ContextClosedEvent` báo rằng **đang bắt đầu đóng context**, không phải rằng mọi bean đã bị destroy. Listener có thể vẫn nhìn thấy các singleton cần thiết cho shutdown.

### 7.1. Close không tự phát ContextStoppedEvent

`context.stop()` có thể publish `ContextStoppedEvent` trong explicit lifecycle stop. `context.close()` publish `ContextClosedEvent` rồi stop components như một phần closure. **Không assert `ContextStoppedEvent` luôn có khi gọi `close()`.**

### 7.2. Stop-before-destroy có ngoại lệ

Spring Javadoc nêu: stop notifications **không được đảm bảo** luôn chạy trước destruction. Nếu refresh thất bại/aborted, hoặc một context hot-refresh trong lifecycle bất thường, Spring có thể destroy partially created beans **mà không có stop signal trước**.

Vì vậy test `stop < destroy` phải nêu rõ scenario: context refresh thành công, worker đã running, sau đó gọi `close()` bình thường. Một test thất bại lúc startup cần kỳ vọng khác.

### 7.3. Context.close và idempotence

`AbstractApplicationContext.close()` có cơ chế đảm bảo shutdown procedure không được thực thi lặp tùy tiện cho cùng active context. Trong tests có thể gọi close hai lần rồi kiểm tra callback count không tăng; đó là một regression assertion hữu ích. Tuy nhiên luôn dùng `try-with-resources` hoặc `finally` để tài nguyên được dọn kể cả test ném exception.

### 7.4. JVM shutdown hook

Ngoài web container, có thể dùng `ConfigurableApplicationContext.registerShutdownHook()` để nhờ JVM close context khi process shutdown. Nhưng test thứ tự lifecycle **nên gọi close tường minh**, không đợi JVM kết thúc rồi suy thời điểm bất đồng bộ của shutdown hooks.

Khi sử dụng nhiều shutdown hooks, **JVM không bảo đảm thứ tự giữa các hooks**. Không test rằng “Spring hook luôn chạy trước custom hook” trừ khi bạn thiết kế một orchestration hook duy nhất.

## 8. SmartLifecycle: auto-start, phase và callback stop

`SmartLifecycle` mở rộng `Lifecycle` và `Phased`. Nó hỗ trợ:

```java
boolean isAutoStartup();
void start();
void stop(Runnable callback);
boolean isRunning();
int getPhase();
```

Một bean có `isAutoStartup()==true` sẽ được lifecycle processor tự khởi động ở refresh theo các constraints. Plain `Lifecycle` không mặc định auto-start khi refresh; `context.start()` có thể kích hoạt.

### 8.1. Phase start thấp trước, stop cao trước

```text
brokerConnection: phase = -100
messageConsumer:  phase = +100
```

Start: broker → consumer. Stop: consumer → broker. Khi cả hai có cùng phase, Spring không cam kết một strict order giữa chúng; hãy khai báo dependency hoặc phase khác nếu thứ tự thực sự quan trọng.

Theo Spring 6.2, `SmartLifecycle` mặc định có phase `Integer.MAX_VALUE`; lifecycle processor xem một bean implement `Lifecycle` nhưng không SmartLifecycle như phase `0`.

### 8.2. depends-on là quan hệ mạnh hơn heuristic ordering

Nếu `MessageConsumer` có bean dependency rõ tới `BrokerConnection`, Spring startup/shutdown lifecycle processor có thể sử dụng quan hệ ấy để bảo đảm dependency được start trước dependent và stop sau dependent. **Đừng kỳ vọng ngẫu nhiên theo thứ tự khai báo `@Bean` methods.**

### 8.3. SmartLifecycle.stop(Runnable) phải gọi callback

```java
@Override
public void stop(Runnable callback) {
    running = false;
    callback.run();
}
```

Đây là snippet method trên class implement `SmartLifecycle`. Khi shutdown bất đồng bộ, chỉ chạy callback **khi đã thật sự stop xong**; nếu quên callback, `DefaultLifecycleProcessor` có thể chờ tới timeout theo phase. Trong Spring 6.2, mặc định timeout mỗi shutdown phase là 30 giây (có API tùy biến).

Lưu ý: lifecycle processor gọi `stop(Runnable)` đối với SmartLifecycle, không nhất thiết gọi trực tiếp `stop()`. Trong test, ghi trace ở callback variant hoặc delegate qua common stop implementation.

### 8.4. SmartLifecycle và @Lazy

Đừng kỳ vọng `@Lazy` luôn ngăn auto-start `SmartLifecycle`. Lifecycle processor có thể instantiate bean để gọi `start()` lúc context refresh. Nếu bean không nên chạy trong test, dùng `isAutoStartup=false` hoặc test profile/configuration thích hợp.

## 9. Thứ tự destroy dependent và dependency

Một managed bean phụ thuộc bean khác thường phải được hủy **trước** bean dependency.

```text
BusinessWorker --> ConnectionPool

Shutdown:
  BusinessWorker.destroy()
  ConnectionPool.destroy()
```

Nếu worker bị hủy sau pool, nó có thể cố flush pending messages qua một connection đã đóng. Spring tracks dependencies để hỗ trợ order ngược trong destruction phase.

### 9.1. @Bean parameters thể hiện dependency thật

```java
@Bean
BusinessWorker worker(ConnectionPool pool) {
    return new BusinessWorker(pool);
}
```

Đây là snippet tích hợp, yêu cầu types. Dependency được resolve khi tạo `worker`, nên Spring biết quan hệ `worker -> pool`. Trong test, assert `worker.destroy < pool.destroy`.

### 9.2. Không assert thứ tự hai beans độc lập

Nếu bean X và Y không có edge dependency, Spring có thể hủy theo một thứ tự implementation-specific (thường liên quan registration order), nhưng đừng khóa cả log thành “X rồi Y” làm behavioral contract chung.

### 9.3. Lưu ý parent-child contexts

Parent và child `ApplicationContext` có BeanFactories và lifecycle scopes riêng. Đóng child thường destroy child-owned singletons, **không destroy parent-owned singletons** chỉ vì child inject được parent bean. Đóng child trước parent là chiến lược phổ biến để tránh child còn đang dùng dependency đã bị parent hủy.

## 10. Ví dụ Maven Java 17 + Spring Framework 6.2, có assertion thật

Ví dụ sau là một **chương trình độc lập**, không phải tập snippets rời rạc. Nó tạo một `AnnotationConfigApplicationContext`, một `Tracked` bean dùng đủ ba init và destroy mechanisms, custom BPP, `SmartLifecycle` worker, context event listeners, và một cặp dependent/dependency để xác nhận destroy order.

Trace được ghi trong một recorder `Trace` đơn giản. Output của chương trình **chỉ in các nhóm assertion PASS** thay vì in toàn bộ sequence; điều này tránh dựa vào một thứ tự không được Spring bảo đảm giữa BPP-before và `@PostConstruct`, hoặc giữa những bean không có dependency.

### 10.1. Cấu trúc

```text
spring-lifecycle-check/
├── pom.xml
└── src/main/java/example/LifecycleOrderCheck.java
```

Yêu cầu JDK 17+ và Maven 3.9+.

### 10.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-lifecycle-check</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.0</version>
        </dependency>
        <dependency>
            <groupId>jakarta.annotation</groupId>
            <artifactId>jakarta.annotation-api</artifactId>
            <version>2.1.1</version>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
            </plugin>
        </plugins>
    </build>
</project>
```

### 10.3. src/main/java/example/LifecycleOrderCheck.java

```java
package example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationListener;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;

public final class LifecycleOrderCheck {

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(Config.class);
            context.refresh();

            Trace trace = context.getBean(Trace.class);
            List<String> running = trace.snapshot();

            // Init checks on one managed singleton:
            before(running, "tracked.construct", "tracked.beanNameAware");
            before(running, "tracked.beanNameAware", "tracked.bppBefore");
            before(running, "tracked.bppBefore", "tracked.afterPropertiesSet");
            before(running, "tracked.postConstruct", "tracked.afterPropertiesSet");
            before(running, "tracked.afterPropertiesSet", "tracked.customInit");
            before(running, "tracked.customInit", "tracked.bppAfter");
            System.out.println("INIT_ORDER=PASS");

            // finishRefresh starts SmartLifecycle before publishing event:
            before(running, "tracked.bppAfter", "worker.start");
            before(running, "worker.start", "context.refreshed");
            System.out.println("REFRESH_ORDER=PASS");

            context.close();
            List<String> stopped = trace.snapshot();

            before(stopped, "context.refreshed", "context.closed");
            before(stopped, "context.closed", "worker.stop");
            before(stopped, "worker.stop", "tracked.preDestroy");
            before(stopped, "tracked.preDestroy", "tracked.disposableDestroy");
            before(stopped, "tracked.disposableDestroy", "tracked.customDestroy");
            System.out.println("CLOSE_ORDER=PASS");

            // Dependent service must be destroyed before its repository:
            before(stopped, "consumer.destroy", "repository.destroy");
            System.out.println("DEPENDENCY_ORDER=PASS");

            // Closing an already closed context should not re-fire callbacks:
            context.close();
            if (!stopped.equals(trace.snapshot())) {
                throw new AssertionError("double destruction after close");
            }
            System.out.println("CLOSE_IDEMPOTENT=PASS");
        }
    }

    private static void before(
            List<String> events, String earlier, String later) {

        if (Collections.frequency(events, earlier) != 1
                || Collections.frequency(events, later) != 1
                || events.indexOf(earlier) >= events.indexOf(later)) {
            throw new AssertionError(
                    "Unexpected ordering " + earlier + " -> " + later
                    + "; trace=" + events);
        }
    }

    public static final class Trace {
        private final List<String> events = new ArrayList<>();

        public synchronized void add(String value) {
            events.add(value);
        }

        public synchronized List<String> snapshot() {
            return List.copyOf(events);
        }
    }

    public static final class Tracked
            implements BeanNameAware, InitializingBean, DisposableBean {

        private final Trace trace;

        public Tracked(Trace trace) {
            this.trace = trace;
            trace.add("tracked.construct");
        }

        @Override
        public void setBeanName(String name) {
            if (!"tracked".equals(name)) {
                throw new IllegalStateException("Wrong bean name: " + name);
            }
            trace.add("tracked.beanNameAware");
        }

        @PostConstruct
        public void postConstruct() {
            trace.add("tracked.postConstruct");
        }

        @Override
        public void afterPropertiesSet() {
            trace.add("tracked.afterPropertiesSet");
        }

        public void customInit() {
            trace.add("tracked.customInit");
        }

        @PreDestroy
        public void preDestroy() {
            trace.add("tracked.preDestroy");
        }

        @Override
        public void destroy() {
            trace.add("tracked.disposableDestroy");
        }

        public void customDestroy() {
            trace.add("tracked.customDestroy");
        }
    }

    public static final class ProbeBpp implements BeanPostProcessor {
        private final Trace trace;

        public ProbeBpp(Trace trace) {
            this.trace = trace;
        }

        @Override
        public Object postProcessBeforeInitialization(
                Object bean, String beanName) {
            if (bean instanceof Tracked) {
                trace.add("tracked.bppBefore");
            }
            return bean;
        }

        @Override
        public Object postProcessAfterInitialization(
                Object bean, String beanName) {
            if (bean instanceof Tracked) {
                trace.add("tracked.bppAfter");
            }
            return bean;
        }
    }

    public static final class Worker implements SmartLifecycle {
        private final Trace trace;
        private boolean running;

        public Worker(Trace trace) {
            this.trace = trace;
        }

        @Override
        public boolean isAutoStartup() {
            return true;
        }

        @Override
        public int getPhase() {
            return 100;
        }

        @Override
        public void start() {
            running = true;
            trace.add("worker.start");
        }

        @Override
        public void stop() {
            if (running) {
                running = false;
                trace.add("worker.stop");
            }
        }

        @Override
        public void stop(Runnable callback) {
            stop();
            callback.run();
        }

        @Override
        public boolean isRunning() {
            return running;
        }
    }

    public static final class RefreshedListener
            implements ApplicationListener<ContextRefreshedEvent> {
        private final Trace trace;

        public RefreshedListener(Trace trace) {
            this.trace = trace;
        }

        @Override
        public void onApplicationEvent(ContextRefreshedEvent event) {
            trace.add("context.refreshed");
        }
    }

    public static final class ClosedListener
            implements ApplicationListener<ContextClosedEvent> {
        private final Trace trace;

        public ClosedListener(Trace trace) {
            this.trace = trace;
        }

        @Override
        public void onApplicationEvent(ContextClosedEvent event) {
            trace.add("context.closed");
        }
    }

    public static final class Repository implements DisposableBean {
        private final Trace trace;

        public Repository(Trace trace) {
            this.trace = trace;
        }

        @Override
        public void destroy() {
            trace.add("repository.destroy");
        }
    }

    public static final class Consumer implements DisposableBean {
        private final Repository repository;
        private final Trace trace;

        public Consumer(Repository repository, Trace trace) {
            this.repository = repository;
            this.trace = trace;
        }

        @Override
        public void destroy() {
            trace.add("consumer.destroy");
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        public Trace trace() {
            return new Trace();
        }

        @Bean
        public static BeanPostProcessor probeBpp(Trace trace) {
            return new ProbeBpp(trace);
        }

        @Bean(initMethod = "customInit",
              destroyMethod = "customDestroy")
        public Tracked tracked(Trace trace) {
            return new Tracked(trace);
        }

        // Dependency argument forces Tracked to initialize before Worker.
        @Bean
        public Worker worker(Tracked tracked, Trace trace) {
            return new Worker(trace);
        }

        @Bean
        public RefreshedListener refreshedListener(Trace trace) {
            return new RefreshedListener(trace);
        }

        @Bean
        public ClosedListener closedListener(Trace trace) {
            return new ClosedListener(trace);
        }

        @Bean
        public Repository repository(Trace trace) {
            return new Repository(trace);
        }

        // Dependency relationship is registered in the BeanFactory.
        @Bean
        public Consumer consumer(Repository repository, Trace trace) {
            return new Consumer(repository, trace);
        }
    }
}
```

### 10.4. Lệnh chạy

Tại thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=example.LifecycleOrderCheck
```

**Stdout dự kiến từ chương trình** (không bao gồm log khác của Maven/Spring):

```text
INIT_ORDER=PASS
REFRESH_ORDER=PASS
CLOSE_ORDER=PASS
DEPENDENCY_ORDER=PASS
CLOSE_IDEMPOTENT=PASS
```

Nếu callback order sai, chương trình ném `AssertionError` kèm toàn bộ trace để người học tìm vị trí lệch, thay vì âm thầm in PASS.

### 10.5. Giải thích assertions của INIT_ORDER

`Tracked` constructor nhận `Trace` rồi ghi `tracked.construct`. BeanFactory gọi `BeanNameAware.setBeanName("tracked")` trước các init callbacks; custom BPP ghi `tracked.bppBefore`.

`@PostConstruct` do annotation BPP xử lý; `afterPropertiesSet()` do `InitializingBean` contract; `customInit()` do `@Bean(initMethod = "customInit")`; cuối cùng custom BPP-after ghi `tracked.bppAfter`.

**Không có assertion buộc `tracked.bppBefore` đứng trước hoặc sau `tracked.postConstruct`**, vì đó là relative order giữa hai BPP-before processors và không cần cho yêu cầu giáo dục chính.

### 10.6. Giải thích REFRESH_ORDER

`worker(Tracked tracked, Trace trace)` khai báo `Tracked` là một dependency của Worker, nên `Tracked` được initialize trước khi Worker được tạo.

`Worker` implement `SmartLifecycle`, có `isAutoStartup()=true` và `getPhase()=100`, nên lifecycle processor start nó trong `finishRefresh()`. `ContextRefreshedEvent` được publish sau onRefresh startup logic. Test chỉ assert `tracked.bppAfter < worker.start < context.refreshed`, không giả định tất cả event listeners phải chạy theo bean registration order.

### 10.7. Giải thích CLOSE_ORDER

`context.close()` phát `ContextClosedEvent`, `ClosedListener` ghi `context.closed`. Sau đó lifecycle processor gọi `Worker.stop(Runnable)`, implementation delegate qua `stop()` rồi chạy callback, nên ghi `worker.stop`.

Container tiếp tục singleton destruction; `Tracked` ghi `preDestroy` → `disposableDestroy` → `customDestroy`. Vì vậy các assertions kiểm tra cả ba callback cùng thứ tự và worker dừng trước khi bean bị hủy trong **normal shutdown**.

### 10.8. Giải thích DEPENDENCY_ORDER

`Consumer` được tạo qua `consumer(Repository repository, Trace trace)`, khai báo dependency tới `Repository`. Container destroy dependent `Consumer` trước dependency `Repository` để tránh consumer dùng một repository đã bị hủy. Test không assert `Tracked` destroy trước hay sau `Consumer` vì giữa hai beans này không có quan hệ trực tiếp.

### 10.9. Vì sao dùng trace recorder thay cho System.out trong callback?

Logging hoặc `System.out.println` xảy ra cùng lúc với Spring logs, dễ bị interleave, đặc biệt khi tests chạy song song. Recorder thu event theo danh sách, sau đó test đối chiếu các **constraints đúng contract**. Recorder dùng synchronized để tránh race khi worker components trong ứng dụng thật có thêm threads, dù demo này chạy tuần tự.

### 10.10. Tại sao static @Bean BPP có tham số Trace?

Custom `BeanPostProcessor` cần được đăng ký sớm. `static @Bean` tránh tạo `Config` instance quá sớm. `Trace` được tạo sớm để dùng làm dependency cho BPP; nó là một recorder đơn giản và không đòi nhận chính custom BPP. Trong production, tránh bắt BPP phụ thuộc vào business beans có lifecycle/AOP phức tạp.

## 11. Chuyển demo thành JUnit 5 integration test

Một Spring Core lifecycle test có thể dùng JUnit 5 để assert invariants thay vì main program. Đặc biệt, nhiều failures chỉ xuất hiện khi test chạy full context với annotation infrastructure, chứ không xuất hiện trong unit test gọi `new MyService()`.

### 11.1. Dùng context độc lập cho từng test

Pattern:

```java
@Test
void closesManagedSingletonsInOrder() {
    var context = new AnnotationConfigApplicationContext();
    context.register(Config.class);
    context.refresh();
    Trace trace = context.getBean(Trace.class);

    context.close();

    List<String> events = trace.snapshot();
    assertTrue(events.indexOf("tracked.preDestroy")
            < events.indexOf("tracked.disposableDestroy"));
    assertTrue(events.indexOf("tracked.disposableDestroy")
            < events.indexOf("tracked.customDestroy"));
}
```

Đây là **JUnit-style snippet** cần imports JUnit 5, các classes `Config`, `Trace` của demo, và một lifecycle cleanup guard. Trong production-quality tests, đặt `context.close()` trong `finally` hoặc `try-with-resources` để bảo đảm resource cleanup cả khi assertion trước close fail.

### 11.2. Test ngược: context.stop không destroy

Trong một context riêng:

```java
context.refresh();
context.stop();
assertTrue(context.isActive());
// singleton destroy callbacks chưa phải contract của stop()
context.close();
```

Đây là snippet tích hợp, cần context có lifecycle-aware beans. Có thể kiểm tra `ContextStoppedEvent` xuất hiện khi explicit stop và destroy callbacks không chạy trước close.

Tránh gọi `context.start()` và `context.stop()` trong chính demo chính nếu muốn trace không có thêm start/stop events. Tách mỗi scenario vào một test context để giữ assertions rõ.

### 11.3. Test SmartLifecycle phase

Tạo hai `SmartLifecycle` beans:

```text
source:   phase = -10
consumer: phase =  20
```

Assert `source.start < consumer.start`, `consumer.stop < source.stop`. Nếu cả hai cùng phase, test **không** nên đòi strict ordering, trừ khi có `depends-on` relation.

### 11.4. Test callback count

Trace có thể giúp assert:
- `@PostConstruct` một lần cho mỗi instance (trong lifetime bình thường).
- `@PreDestroy` một lần cho managed singleton khi closed.
- Custom init/destroy một lần.
- Closing context lại không tạo thêm destroy callbacks.
- ContextRefreshedEvent và ContextClosedEvent có số lần phù hợp với scenario.

Nếu có prototype, test **từng instance riêng**, không dùng singleton count expectation.

## 12. Context caching và @DirtiesContext

Spring TestContext Framework có **context cache** để tiết kiệm startup. Với `@SpringJUnitConfig` hoặc `@SpringBootTest`, context có thể được dùng lại qua nhiều test methods/classes.

Khi kiểm thử `@PreDestroy` hoặc `ContextClosedEvent`, đừng viết test trông đợi cleanup xảy ra ngay sau từng method nếu context đang được cache. Bạn có thể:

1. Tạo một `AnnotationConfigApplicationContext` tách biệt và close explicit.
2. Dùng `@DirtiesContext` nếu test thật sự phải buộc Spring TestContext loại context cache; hiểu class/method mode.
3. Tách tests startup/shutdown có side effects khỏi tests nghiệp vụ cần context cache.
4. Không phụ thuộc global shutdown của toàn bộ test suite để assert một callback count.

**Không phải test nào cũng nên dùng `@DirtiesContext`**, vì nó tốn tài nguyên và làm chậm suite. Chỉ dùng khi test sửa state context hoặc cần lifecycle isolation.

## 13. Đối chiếu với ApplicationContextRunner và Spring Boot

Spring Boot có `ApplicationContextRunner` để test auto-configuration theo properties/classpath. Nó hữu ích khi lifecycle phụ thuộc conditional beans và muốn chạy context isolated rồi tự cleanup sau callback.

Tuy nhiên đây là **API Spring Boot test**, không phải `spring-context` thuần. Đối với module Spring Framework Core Container, ví dụ dùng `AnnotationConfigApplicationContext` là đủ để minh họa lifecycle contracts mà không mang Boot auto-configuration vào.

Khi dùng Boot, startup/shutdown có thể thêm listeners và infrastructure beans. Vì vậy đừng copy toàn bộ trace của một plain Spring Framework context rồi assert y hệt trong Boot app; chỉ assert những **relations** thuộc beans và contract bạn đang kiểm thử.

## 14. Các tình huống dễ gây false positive

| Tình huống | Vì sao test có thể sai? | Cách cải thiện |
|---|---|---|
| In log rồi nhìn bằng mắt | Dễ bỏ sót event/đếm trùng | Dùng trace + assertions |
| Chụp toàn bộ logs | Nhiều beans độc lập không có strict order | Assert partial order |
| Gọi `new Service()` trong test | Không có container processors | Tạo context thật khi test Spring lifecycle |
| Plain `BeanFactory` không cài annotation BPP | `@PostConstruct` không được xử lý | Dùng ApplicationContext hoặc đăng ký processors |
| Dùng prototype rồi assert destroy khi close | Prototype không auto-destroy | Caller cleanup |
| Gọi `stop()` rồi assert `@PreDestroy` | Stop không destroy | Assert `close()` |
| Chỉ assert ContextClosedEvent cuối cùng | Event trước destruction | Assert event trước stop/destroy |
| Assert ContextStoppedEvent khi close | Close không đồng nghĩa explicit stop | Dùng đúng event |
| Quên `SmartLifecycle.stop(Runnable)` callback | Shutdown phase có thể timeout | Luôn gọi callback sau stop |
| @Lazy SmartLifecycle vẫn start | Auto-start cần bean instance | Test `isAutoStartup`/profile |
| BPP thay proxy | `== rawObject` không còn đúng | Test exposed proxy/behavior |
| Init callback gọi service khác ngay | Có thể gây circular init/deadlock | Hoãn work đến post-refresh/lifecycle |
| Hai BPP-before cùng được quan sát | Thứ tự BPP registration có thể khác | Assert từng contract riêng |
| Bean name khác kỳ vọng | Factory method/alias name | Assert actual beanName rõ |
| Context được cache giữa tests | Destroy chưa chạy sau method | Tạo context isolated |
| Close gọi nhiều lần | Callback count bị test hiểu sai | Kiểm tra idempotence |
| Dependency destroy order bị đoán | Không có BeanFactory registered relation | Khai báo dependency rõ qua DI |
| Shutdown hook order bị đoán | JVM không bảo đảm thứ tự hooks | Close explicit trong tests |
| Test chạy parallel | Trace global bị trộn giữa contexts | Recorder riêng mỗi context/test |
| Startup fail giữa chừng | Không phải normal shutdown | Test failure cleanup riêng |
| Event listener chặn lâu | Close bị kéo dài | Tránh blocking listener |
| Callback throw exception | Có thể log error/không cleanup như kỳ vọng | Test resource cleanup và cause chain |

## 15. Những giới hạn và trường hợp mở rộng

### 15.1. BeanPostProcessor có thể chạy nhiều lần trên nhiều beans

BPP callbacks được gọi trên những beans do factory tương ứng tạo, không chỉ business bean. Nếu test đếm số lần **tổng thể** `postProcessAfterInitialization` được gọi, count có thể đổi khi Spring thêm infrastructure beans. Trong demo, BPP chỉ ghi event nếu `bean instanceof Tracked`, giúp test bền hơn.

### 15.2. FactoryBean và sản phẩm

`FactoryBean<T>` là một managed factory bean; sản phẩm từ `getObject()` có riêng semantics creation/post-processing. Đừng suy rằng lifecycle của factory bean và product luôn giống nhau. Khi test, phân biệt `getBean("name")` cho product và `getBean("&name")` cho factory.

### 15.3. Parent-child context

Parent bean lấy qua child vẫn do **parent BeanFactory** quản lý. Đóng child không tự destroy parent-owned singleton. Các post-processors thuộc context nào xử lý beans context đó; khi test lifecycle across hierarchy, phải tạo/close cả hai contexts theo thứ tự được thiết kế.

### 15.4. AOT/native modes

Với Spring AOT, bean definitions/code generation có thể được quyết định tại build time, nhưng application instances/lifecycle callbacks vẫn chủ yếu xảy ra ở runtime. Tuy nhiên nếu bạn dùng custom dynamic registration hoặc reflection, hãy chạy tests cho đúng AOT/native deployment mode để đảm bảo callback metadata được generated/hinted đầy đủ.

### 15.5. Concurrent shutdown

Khi một worker thực sự dừng bất đồng bộ, `stop(Runnable)` callback có thể được gọi từ thread khác. Recorder trong integration test nên thread-safe; assertions chỉ chạy sau khi lifecycle processor hoàn tất việc chờ stop phase hoặc test đã đồng bộ. Tránh `Thread.sleep(1000)` làm căn cứ ordering, vì nó tạo flaky tests.

### 15.6. Shutdown theo phases và timeout

`DefaultLifecycleProcessor` quản lý phase groups. Nếu worker không gọi stop callback, đóng context có thể chờ tới timeout; test cần có upper timeout nhưng không nên phụ thuộc một con số rất ngắn trên CI. Nếu cần điều chỉnh, cung cấp bean tên `lifecycleProcessor` thuộc đúng type, nhưng không thay một policy global chỉ để làm các tests “nhanh”.

## 16. Quy trình debug sự cố lifecycle

1. **Xác định context type**: `AnnotationConfigApplicationContext`, `GenericApplicationContext`, Boot context hay plain BeanFactory?
2. **Xác định bean scope**: singleton, prototype, request, session hoặc custom?
3. **Xác định object owner**: container tạo từ BeanDefinition hay code ngoài register singleton?
4. **Đọc đầy đủ trace** trên bean mục tiêu: constructor, inject, Aware, init/BPP, lifecycle start, event, close, stop, destroy.
5. **Kiểm tra registered BPPs**: annotation processors và AOP proxy creators đã được kích hoạt chưa?
6. **Kiểm tra callback method names**: có trùng giữa `@PostConstruct`, interface và custom init/destroy không?
7. **Kiểm tra dependencies**: có `@DependsOn`, constructor param hoặc setter relationship thực sự không?
8. **Kiểm tra phase**: explicit `getPhase()` và `isAutoStartup()` phù hợp chưa?
9. **Kiểm tra `stop(Runnable)`**: callback được gọi khi stop xong chưa?
10. **Phân biệt close với stop** và `ContextClosedEvent` với `ContextStoppedEvent`.
11. **Đọc log startup failure**: normal shutdown order có thể không áp dụng khi refresh abort.
12. **Kiểm tra test context caching**: context có thực sự close trong test không?
13. **Kiểm tra proxy identity**: callback có chạy trên raw target trước proxy hay qua exposed proxy?
14. **Kiểm tra cleanup exceptions**: có resource leak dù vài callback khác thành công không?
15. **Viết regression test nhỏ nhất** chỉ assert các constraints rõ trong docs.

## 17. Best practices

- Dùng **constructor injection** cho required dependencies để bean graph và invariants minh bạch.
- Ưu tiên `@PostConstruct`/`@PreDestroy` cho simple callback logic, tránh ép business code implement Spring lifecycle interfaces khi không cần.
- Nếu cần custom `initMethod`/`destroyMethod`, dùng method names riêng và explicit metadata trong bài test.
- Không làm network/database calls tốn thời gian trong `@PostConstruct` khi có thể hoãn tới lifecycle sau refresh.
- Dùng `SmartInitializingSingleton` hoặc `ContextRefreshedEvent` cho hoạt động cần normal singletons sẵn sàng; dùng `SmartLifecycle` cho thành phần vận hành cần stop được.
- Implement `SmartLifecycle.stop(Runnable)` chuẩn: không quên callback, không gọi nó trước khi workers đã dừng.
- Đặt `getPhase()` explicit khi thứ tự giữa components quan trọng.
- Đăng ký dependency bằng constructor/`@Bean` parameters/`@DependsOn` thay vì chỉ trông đợi order.
- Dùng một recorder trace tách biệt cho mỗi test context, không dùng global static list qua nhiều tests.
- Test shutdown bằng **explicit close** và `try-with-resources`; không trông chờ JVM hooks.
- Assert callback presence, count và ordering **cho một bean** hoặc **giữa beans có dependency**, không chụp nguyên logs.
- Với proxies, test exposed behavior thay vì raw object identity.
- Không assert prototype destruction qua context.close; gắn ownership cho caller.
- Tách normal shutdown tests khỏi failed refresh cleanup tests.
- Với Boot TestContext caching, tạo isolated context khi bắt buộc quan sát context close.
- Test với cùng Spring version/Jakarta annotations/runtime topology được deploy.

## 18. Câu hỏi tự kiểm tra

**1. Constructor có chạy sau @Autowired field injection không?**  
Không. Constructor chạy trước property/field injection.

**2. @PostConstruct chạy trước hay sau InitializingBean.afterPropertiesSet()?**  
Trước.

**3. Custom @Bean(initMethod) chạy lúc nào nếu cùng bean có @PostConstruct và InitializingBean?**  
Sau `@PostConstruct` và `afterPropertiesSet()`, nếu callbacks khác tên.

**4. BeanPostProcessor.postProcessAfterInitialization có thể trả proxy không?**  
Có. Test không nên bắt object identity với raw bean.

**5. Có nên assert custom BPP-before luôn trước @PostConstruct không?**  
Không khi chưa kiểm soát processor ordering; cả hai thuộc vùng BPP-before của pipeline.

**6. @PreDestroy, DisposableBean.destroy, custom destroy order là gì?**  
`@PreDestroy` → `DisposableBean.destroy()` → custom destroy.

**7. Gọi context.stop có tự chạy @PreDestroy không?**  
Không theo contract; stop không phải destruction.

**8. Khi context.close bình thường, ContextClosedEvent có được publish trước singleton destruction không?**  
Có.

**9. ContextClosedEvent có đồng nghĩa toàn bộ resources đã đóng xong không?**  
Không. Nó là sự kiện ở đầu quá trình close.

**10. ContextStoppedEvent có nhất thiết xuất hiện khi close không?**  
Không.

**11. SmartLifecycle bean có auto-start trong refresh không?**  
Có thể, khi `isAutoStartup()` trả true và lifecycle processor xử lý.

**12. Plain Lifecycle có được auto-start chỉ nhờ refresh không?**  
Không theo mặc định.

**13. SmartLifecycle phase âm/positive ảnh hưởng gì?**  
Phase nhỏ start trước, stop sau; phase lớn start sau, stop trước.

**14. Vì sao stop(Runnable) cần gọi callback.run?**  
Để lifecycle processor biết shutdown của component/phase đã hoàn thành.

**15. Dependent và dependency hủy theo thứ tự nào?**  
Dependent trước dependency.

**16. Hai beans độc lập có bắt buộc destroy theo thứ tự tên alphabet không?**  
Không.

**17. Prototype có auto-destroy khi đóng context không?**  
Không thông thường; caller quản lý.

**18. Bean tạo bằng new có tự nhận @PostConstruct không?**  
Không. Phải qua lifecycle processing của Spring.

**19. Một @Bean AutoCloseable có thể được suy luận destroy method không?**  
Có thể, ví dụ public `close()`; kiểm tra `destroyMethod` policy.

**20. Tại sao test @PreDestroy với @SpringBootTest đôi khi không thấy callback sau test method?**  
Vì TestContext có thể cache context, chưa đóng tại thời điểm đó.

**21. Stop trước destroy có bảo đảm trong mọi failure path không?**  
Không. Startup abort có thể destroy mà chưa gửi stop.

**22. Vì sao nên assert partial order thay vì toàn bộ trace?**  
Để chỉ kiểm tra những quan hệ được Spring contract bảo đảm, tránh flaky tests.

**23. Khi nào nên dùng SmartInitializingSingleton?**  
Khi muốn callback sau khi regular singleton pre-instantiation hoàn tất, tránh chạy work trong singleton initialization lock.

**24. Runtime shutdown hook có thể thay cho explicit close trong integration test không?**  
Không nên; explicit close có kết quả xác định và tránh JVM-hook ordering uncertainty.

**25. Dấu hiệu bài test lifecycle tốt là gì?**  
Kiểm tra đúng callback **được gọi, gọi bao nhiêu lần, theo thứ tự nào có bảo đảm**, và resource thực sự đã được giải phóng trong context scope đúng.

## 19. Nguồn tham khảo chính thức

- [Spring Framework 6.2 — Customizing the Nature of a Bean](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-nature.html): initialization/destruction callback order, SmartLifecycle, phases, shutdown hooks.
- [Spring Framework 6.2 — Using @PostConstruct and @PreDestroy](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/postconstruct-and-predestroy-annotations.html): Jakarta annotation processor.
- [Spring Framework 6.2 API — BeanPostProcessor](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/config/BeanPostProcessor.html): before/after initialization contract.
- [Spring Framework 6.2 API — AbstractApplicationContext](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/AbstractApplicationContext.html): refresh, start, stop, close, doClose and destroyBeans.
- [Spring Framework 6.2 API — SmartLifecycle](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/SmartLifecycle.html): auto-start, default phase, stop(Runnable) semantics.
- [Spring Framework 6.2 API — DefaultLifecycleProcessor](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/DefaultLifecycleProcessor.html): phase-aware startup/shutdown.
- [Spring Framework 6.2 API — ContextClosedEvent](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/event/ContextClosedEvent.html): event raised on context closure.
- [Spring Framework 6.2 — Receiving Lifecycle Callbacks in @Bean Definitions](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/bean-annotation.html): initMethod, destroyMethod and inferred close/shutdown.
- [Spring Framework 6.2 — Container Extension Points](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-extension.html): BPP registration/ordering.
- [Spring Framework 6.2 — Bean Scopes](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-scopes.html): singleton/prototype destruction ownership.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="container-testing-007">Đổi profile, nguồn thuộc tính và kiểm tra chuyển đổi giá trị</a>

<details>
<summary>Xem chi tiết</summary>

# Đổi profile, nguồn thuộc tính và kiểm tra chuyển đổi giá trị trong Spring Core Container

## 1. Phạm vi và mục tiêu bài học

Một bài kiểm thử container tốt không chỉ hỏi “Spring có tạo được bean không?” mà cần kiểm chứng **bean graph được chọn theo profile**, **giá trị cuối cùng được đọc từ nguồn thuộc tính nào**, và **chuỗi cấu hình được chuyển thành kiểu Java mong muốn ra sao**. Ba vấn đề liên quan nhưng có các quy tắc độc lập:

1. **Profile selection**: `@Profile` quyết định bean definition/configuration có đủ điều kiện tham gia container; trong test, `@ActiveProfiles` đặt active profiles trước khi tạo `ApplicationContext`.
2. **Property source precedence**: `Environment` duyệt các `PropertySource` theo thứ tự ưu tiên. Một key có thể nằm trong nhiều nguồn; nguồn đứng trước thắng. Spring TestContext bổ sung `@TestPropertySource` và `@DynamicPropertySource` để kiểm soát riêng môi trường thử nghiệm.
3. **Type conversion**: Giá trị `"7"`, `"true"` hoặc `"8100:8102"` trong property source là chuỗi, nhưng consumer có thể cần `int`, `boolean` hoặc một record domain như `PortWindow`. `ConversionService` đảm nhiệm chuyển đổi, song **ConversionService của Environment và của BeanFactory không phải tự động cùng một instance**.

Bài này tập trung vào **Spring Framework 6.2.19, Java 17 và Spring TestContext + JUnit Jupiter**, không phụ thuộc Spring Boot. Tài liệu giải thích khái niệm, timing, cấu hình, cơ chế precedence, conversion, lỗi thường gặp và một dự án Maven có **hai lớp integration test chạy theo hai profiles**.

### Bạn cần làm được gì?

- Đặt `@ActiveProfiles("dev")` và `@ActiveProfiles("prod")` để kiểm chứng những bean definitions khác nhau trong từng context.
- Phân biệt `@Profile` với `@Conditional`, `@Tag` của JUnit và tên file properties.
- Dùng `@PropertySource` cho cấu hình ứng dụng và `@TestPropertySource` cho giá trị thử nghiệm.
- Dự đoán giá trị thắng khi cùng key có mặt ở inline test, file test, app property source và initializer.
- Dùng `ConfigurableEnvironment.getPropertySources()` để xem thứ tự các nguồn mà không làm rò rỉ secret values.
- Kiểm thử built-in conversions như `String → Integer`, `String → Boolean` và conversion lỗi.
- Viết `Converter<String, PortWindow>`, đăng ký cho `Environment` và cho `BeanFactory` có chủ đích.
- Kiểm chứng `@Value("${sample.window}") PortWindow` nhận một domain value hợp lệ.
- Hiểu vì sao thay property/profile sau `refresh()` không tự đổi singleton hoặc đánh giá lại `@Profile`.
- Quản lý Spring TestContext cache để tests chạy được theo cả profiles mà không mang theo trạng thái giữa tests.

## 2. Bức tranh tổng thể

~~~text
JUnit Jupiter test
   |
   +-- @SpringJUnitConfig(classes = DemoTestConfig.class,
   |                     initializers = EnvironmentBootstrap.class)
   |
   +-- @ActiveProfiles("dev" hoặc "prod")
   |
   +-- @TestPropertySource(
   |       locations = "classpath:demo/file-overrides.properties",
   |       properties = {"sample.mode=...", ...})
   |
   v
SpringExtension / TestContextManager
   |
   +-- MergedContextConfiguration
   |       classes, initializers, activeProfiles,
   |       propertySourceDescriptors, propertySourceProperties, ...
   |
   +-- ApplicationContext cache key
   |       dev và prod là cấu hình khác nhau
   |
   v
ApplicationContext (mỗi cấu hình được load/refresh)
   |
   +-- Environment
   |      [inline test values]
   |      [test file values]
   |      [@PropertySource application defaults]
   |      [initializer fallback]
   |      [system/environment sources khác]
   |
   +-- @Profile("dev") / @Profile("prod")
   |      chọn BeanDefinitions khi tạo context
   |
   +-- BeanFactory
          @Bean("conversionService") tùy biến convert khi inject
          @Bean Settings(@Value ...)
                 |
                 v
          Settings(String mode, boolean enabled,
                   int retries, PortWindow window)
~~~

Sơ đồ chỉ biểu diễn quan hệ ưu tiên đáng chú ý ở ví dụ. **Thứ tự chính xác trong một runtime có thể chịu ảnh hưởng những source khác** như system properties, web context hay Spring Boot config data. Trong ví dụ, các giá trị được thiết kế để có tính xác định; hãy assert **effective values**, đừng mặc định tên/mọi index của `PropertySource` luôn cố định giữa các loaders.

## 3. Profile trong Spring là điều kiện tạo bean, không phải giá trị cấu hình

`Environment` cung cấp active/default profiles. Annotation `@Profile` có thể giới hạn việc đăng ký một `@Configuration` class hoặc `@Bean` method.

~~~java
@Configuration(proxyBeanMethods = false)
class RoutingConfiguration {

    @Bean
    @Profile("dev")
    StoragePort developmentStorage() {
        return new InMemoryStorage();
    }

    @Bean
    @Profile("prod")
    StoragePort productionStorage() {
        return new RemoteStorage();
    }
}
~~~

Đây là snippet cần định nghĩa `StoragePort`, `InMemoryStorage` và `RemoteStorage`. Với active profile `dev`, bean `developmentStorage` đủ điều kiện; `productionStorage` không được đăng ký từ method tương ứng. Với `prod` thì ngược lại.

### Profile và property key không tương đương

- `@Profile("prod")` là một điều kiện bean registration.
- `@Value("${sample.mode}")` lấy giá trị chuỗi từ `PropertySources`.
- `@TestPropertySource(properties = "sample.mode=prod")` **không tự kích hoạt profile prod**.
- `@ActiveProfiles("prod")` **không tự đặt `sample.mode=prod`**.
- `@Tag("prod")` của JUnit là metadata để lọc test execution, không đổi Spring `Environment`.

Có thể cố ý dùng cùng các chữ “dev/prod” cho profile và property mode, nhưng chúng vẫn phải được cấu hình và kiểm thử riêng.

### Profile expression

`@Profile` hỗ trợ biểu thức như:

~~~java
@Profile("dev | test")
@Profile("!prod")
@Profile("cloud & eu")
~~~

Đây là các ví dụ độc lập; `@Profile` được dùng một annotation trên một bean/configuration thích hợp. Khi kết hợp `&` và `|` nên dùng dấu ngoặc theo cú pháp Spring profile expressions để tránh ambiguity. Không suy rằng profile expressions là SpEL tùy ý hoặc property placeholders.

### Default profile

Nếu **không có active profile**, Spring có cơ chế default profiles (thường bao gồm tên `default`, trừ khi thay đổi). `@ActiveProfiles("dev")` thiết lập active set có chủ đích; không nên mong mọi `@Profile("default")` bean tiếp tục có mặt khi active profile cụ thể đã được đặt.

## 4. @ActiveProfiles phải được thiết lập trước refresh

Spring TestContext xử lý `@ActiveProfiles` khi xây `MergedContextConfiguration` và thiết lập môi trường trước khi load/refresh context.

~~~java
@SpringJUnitConfig(TestConfig.class)
@ActiveProfiles("dev")
class DevelopmentTests { }
~~~

~~~java
@SpringJUnitConfig(TestConfig.class)
@ActiveProfiles("prod")
class ProductionTests { }
~~~

**Hai classes có profiles khác nhau thường có context cache keys khác nhau.** Điều này hữu ích: DevTests không vô tình reuse một context đã dựng với prod-only beans.

### Những cách đổi profile

| Cách | Áp dụng phù hợp | Lưu ý |
| --- | --- | --- |
| `@ActiveProfiles("dev")` | Integration test theo profile cố định | Cấu hình ở class level, trước context load |
| `@ActiveProfiles({"dev", "feature-x"})` | Tổ hợp profiles | Kiểm tra bean graph của tổ hợp |
| `@ActiveProfiles(resolver = ...)` | Resolve profiles programmatically trước context creation | Complexity cao hơn; cache key phải nhất quán |
| `ConfigurableEnvironment.setActiveProfiles(...)` trước `refresh()` | Tạo context bằng code thuần Java | Gọi sau refresh không tạo lại bean graph |
| `spring.profiles.active` property | Cơ chế environment/profile configuration tùy bootstrap | Trong TestContext, `@ActiveProfiles` có quy tắc ưu tiên riêng |

**Đừng đổi profile bên trong một @Test rồi kỳ vọng @Profile beans biến mất/xuất hiện lập tức.** Conditions được đánh giá khi xử lý cấu hình/bean definitions; `ApplicationContext` đã refresh không tự rescan graph chỉ do active profiles thay đổi.

### Profile inheritance

`@ActiveProfiles` hỗ trợ kế thừa từ superclass hoặc enclosing test class theo metadata/TestContext rules. Nếu một base test có `@ActiveProfiles("dev")` và subclass bổ sung `prod`, hãy kiểm tra `inheritProfiles` và tình trạng nhiều profiles đồng thời. Nếu mục đích là chạy độc lập hai graphs, test classes riêng và khai báo chính xác sẽ dễ hiểu hơn.

## 5. PropertySource là gì?

`PropertySource<?>` ánh xạ một key tới một value thông qua một nguồn dữ liệu. `ConfigurableEnvironment.getPropertySources()` trả một `MutablePropertySources` được Spring duyệt **theo thứ tự**.

~~~text
Properties in source A (priority higher):
  sample.mode=inline
  sample.retries=7

Properties in source B:
  sample.mode=file
  sample.origin=file

Properties in source C:
  sample.mode=defaults
  sample.origin=defaults
  sample.fallback=from-config

Effective lookups:
  sample.mode     -> inline
  sample.retries  -> 7
  sample.origin   -> file
  sample.fallback -> from-config
~~~

Nếu một key có mặt ở nguồn ưu tiên cao hơn, Spring dùng value của nguồn đó. Đây **không phải một phép deep merge những Map objects** theo từng property source name; chỉ là property resolution qua ordered sources.

### StandardEnvironment

Với một ứng dụng Java non-web, `StandardEnvironment` thường có `systemProperties` và `systemEnvironment` mặc định. Nguồn do test context/initializer thêm có thể nằm trước hoặc sau chúng tùy API. `MutablePropertySources.addFirst(...)` ưu tiên cao; `addLast(...)` thêm cuối.

~~~java
environment.getPropertySources().addFirst(
        new MapPropertySource(
                "testOverrides", Map.of("sample.mode", "custom")));
~~~

Đây là snippet với `ConfigurableEnvironment`, `MapPropertySource` và `Map`. **Không chỉnh system properties toàn JVM** nếu một test chỉ cần override một key; chúng có thể làm các tests chạy song song ảnh hưởng nhau.

### PropertySource name collision

`MutablePropertySources` quản lý các sources có **tên**. Thêm source trùng tên có semantics replacement/reordering tùy operation, không phải mọi trường hợp merge từng key từ hai maps. Trong test, đặt tên rõ như `fixtureFallbacks`, `fileOverrides` để dễ debug.

## 6. @PropertySource, @TestPropertySource và @DynamicPropertySource

Ba công cụ có điểm tham gia khác nhau:

| Cơ chế | Nơi khai báo | Mục đích |
| --- | --- | --- |
| `@PropertySource("classpath:...")` | `@Configuration` | Thêm properties thuộc ứng dụng/module vào Environment |
| `@TestPropertySource(locations=...)` | Test class qua TestContext | Nạp properties file chỉ dành cho test |
| `@TestPropertySource(properties=...)` | Test class | Override inline test-specific values |
| `@DynamicPropertySource` | Static method trong test class | Thêm runtime-derived values qua `DynamicPropertyRegistry` |
| `ApplicationContextInitializer` | TestContext config | Thay đổi ConfigurableApplicationContext/Environment trước refresh |
| `MapPropertySource` | Đăng ký bằng code | Thêm nguồn dữ liệu rõ tên và vị trí |
| `@Value` | Bean injection point | Tiêu thụ property, **không tự đăng ký source** |

Với Spring TestContext 6.2 sử dụng loader tiêu chuẩn, ưu tiên thông thường:

~~~text
Higher precedence
   @DynamicPropertySource values
   @TestPropertySource(properties = "...")      (inline)
   @TestPropertySource(locations = "...")       (test files)
   application/system sources                  (tùy context)
   @PropertySource và nguồn do addLast(...)      (thứ tự tùy thời điểm đăng ký)
Lower precedence
~~~

Các nguồn application/system có thể được sắp xếp khác nhau. Trong ví dụ, inline/file test được ưu tiên so với application defaults. `initializerFallback` cung cấp một khóa riêng; không dựa vào thứ tự tương đối giữa initializer và `@PropertySource`.

### Quy tắc quan trọng về thời điểm

`@TestPropertySource` được xử lý khi TestContext load context, trước khi test methods chạy. `@PropertySource` được xử lý trong `@Configuration` class processing khi refresh. `@DynamicPropertySource` là một cách khai báo suppliers tại test initialization; khi source được resolve, supplier cung cấp value.

Nếu application service là singleton đã được tạo, đổi một source sau đó **không tự cập nhật `@Value` fields hoặc gọi lại `@Bean` method**. Hãy dựng context khác với profile/override mới trong test thay vì mutate context cache.

## 7. Test property precedence: từng tầng rõ ràng

Giả sử có bốn nguồn:

**Nguồn A – inline tại test:**

~~~properties
sample.mode=inline-dev
sample.enabled=true
sample.retries=7
sample.window=8100:8102
~~~

**Nguồn B – file test:**

~~~properties
sample.mode=file-mode
sample.origin=file-origin
sample.enabled=false
sample.retries=5
sample.window=7200:7202
~~~

**Nguồn C – file application defaults:**

~~~properties
sample.mode=config-mode
sample.origin=config-origin
sample.enabled=false
sample.retries=2
sample.window=7100:7102
sample.fallback=from-config
~~~

**Nguồn D – initializer fallback:**

~~~properties
sample.mode=initializer-mode
sample.initializerOnly=hello-initializer
~~~

Khi các sources được thêm theo thứ tự ưu tiên tương ứng, kết quả:

| Key | Effective value | Giải thích |
| --- | --- | --- |
| `sample.mode` | `inline-dev` | Inline thắng file test/config/initializer |
| `sample.enabled` | `true` | Inline thắng `false` trong file |
| `sample.retries` | `7` | Inline thắng 5 và 2 |
| `sample.window` | `8100:8102` | Inline thắng các giá trị khác |
| `sample.origin` | `file-origin` | Không có inline, file test thắng config |
| `sample.fallback` | `from-config` | Chỉ được khai báo ở defaults.properties; không phụ thuộc vị trí initializer |
| `sample.initializerOnly` | `hello-initializer` | Chỉ initializer có key này |

**Không bao giờ kiểm tra precedence chỉ bằng `environment.containsProperty(key)`.** Việc “có key” không cho biết nguồn nào thắng hoặc conversion có hoạt động không. Assertions nên kiểm tra exact effective value **và** converted type cần thiết.

### @TestPropertySource default discovery

Nếu viết `@TestPropertySource` rỗng, TestContext có thể tìm file properties theo convention dựa trên tên test class. Nếu file vắng, context loading có thể fail. Khi muốn test tái lập ổn định, luôn ghi explicit location hoặc inline properties.

### Tệp XML/YAML

`@TestPropertySource` hỗ trợ các format file properties/XML theo loader/factory; với YAML/JSON không tự giả định parser mặc định đọc được như Spring Boot config data. Spring 6.1+ hỗ trợ `PropertySourceFactory` tùy biến và resource patterns cho một số use cases. Trong bài này dùng file `.properties` tiêu chuẩn.

## 8. @Value: khi String được tiêm vào kiểu đích

Ví dụ:

~~~java
@Bean
Settings settings(
        @Value("${sample.mode}") String mode,
        @Value("${sample.enabled}") boolean enabled,
        @Value("${sample.retries}") int retries,
        @Value("${sample.window}") PortWindow window) {

    return new Settings(mode, enabled, retries, window);
}
~~~

Cơ chế có hai bước:

1. Resolve `${...}` placeholder từ property sources thành value (thường chuỗi).
2. Nếu Java parameter type không phải `String`, dùng type conversion của BeanFactory để chuyển sang `boolean`, `int` hoặc `PortWindow`.

Built-in conversions hỗ trợ nhiều kiểu phổ biến, nhưng record tùy biến như `PortWindow` cần một converter có chủ đích nếu không có conversion path mặc định.

### Default placeholder

~~~java
@Value("${sample.retries:3}")
int retries;
~~~

Khi không có `sample.retries`, placeholder có default text `"3"`; sau đó converter chuyển sang `int`. Giá trị mặc định không bảo đảm thành công nếu chuỗi thực có mặt nhưng malformed, ví dụ `sample.retries=seven`.

### Placeholder và SpEL

`@Value("${sample.retries}")` là property placeholder. `@Value("#{2 * 3}")` là SpEL expression. Không dùng SpEL thay cho mọi type conversion, và không nhầm `{0}` trong `MessageFormat` với `${...}`.

## 9. Environment.getProperty(key, Class<T>) có bộ convert riêng

`ConfigurableEnvironment` mở rộng `ConfigurablePropertyResolver`, hỗ trợ:

~~~java
String raw = env.getProperty("sample.retries");
Integer parsed = env.getProperty("sample.retries", Integer.class);
Boolean active = env.getProperty("sample.enabled", Boolean.class);
~~~

Các calls dùng conversion service của **Environment property resolver**. Để bổ sung converter:

~~~java
env.getConversionService()
        .addConverter(new PortWindowConverter());
~~~

Đây là snippet; `env` phải là `ConfigurableEnvironment`, và `PortWindowConverter` implement `Converter<String,PortWindow>`.

### Vì sao không phải cứ có bean tên conversionService thì Environment tự biết?

Spring có một **BeanFactory conversion service** có thể được đặt bằng bean `conversionService` khi context refresh. `Environment` cũng có `ConfigurableConversionService` dùng cho `getProperty(key, Type.class)`. Hai thành phần có các API/configuration lifecycles riêng, không có lời hứa rằng đăng ký converter vào một nơi sẽ tự động cập nhật nơi kia.

| Consumer | Conversion mechanism chính | Chỗ cấu hình thích hợp |
| --- | --- | --- |
| `env.getProperty("sample.window", PortWindow.class)` | Environment property resolver conversion service | `env.getConversionService().addConverter(...)` |
| `@Value("${sample.window}") PortWindow window` trong bean | BeanFactory type converter/conversion service | Bean `conversionService`, BeanFactory setup |
| `conversionService.convert(...)` trực tiếp | Đúng ConversionService instance caller có | Registry của instance đó |
| Spring MVC binding | MVC-specific conversion/binding setup | MVC formatter/converter infrastructure |
| Spring Boot `@ConfigurationProperties` | Boot configuration binding infrastructure | Boot binding conversion rules |

**Đừng dùng kiểm thử Environment conversion thành công để kết luận `@Value` custom type chắc chắn thành công**, hoặc ngược lại. Ví dụ hoàn chỉnh đăng ký cùng `PortWindowConverter` ở cả hai nơi rồi kiểm tra cả hai đường.

## 10. Viết converter an toàn: String → PortWindow

Giả sử một property biểu diễn dải cổng:

~~~properties
sample.window=8100:8102
~~~

Ta chọn record có hai cổng, ràng buộc 1–65535 và `start <= end`. Converter phải:

1. Xác thực đúng hai thành phần với separator `:`.
2. Trim khoảng trắng.
3. Parse số nguyên, không im lặng chấp nhận text ngoài grammar.
4. Validate range và thứ tự tăng dần.
5. Ném lỗi rõ ràng khi malformed.

~~~java
public record PortWindow(int start, int end) {
    public PortWindow {
        if (start < 1 || end > 65535 || start > end) {
            throw new IllegalArgumentException(
                    "Invalid port window");
        }
    }
}
~~~

~~~java
public final class PortWindowConverter
        implements Converter<String, PortWindow> {

    @Override
    public PortWindow convert(String source) {
        String[] parts = source.split(":", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "Expected start:end");
        }

        try {
            return new PortWindow(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Port values must be integers", ex);
        }
    }
}
~~~

Đây là snippets; chương trình hoàn chỉnh và các imports ở mục 14. Dùng `split(":", -1)` để giữ thành phần rỗng (ví dụ `"8000:"`), giúp parser không vô tình coi input thiếu value là hợp lệ.

### Converter không quyết định precedence

Converter nhận value **sau khi** resolver đã chọn nguồn property thắng. Nếu inline test ghi `sample.window=8100:8102` và file ghi `7200:7202`, converter chỉ thấy `8100:8102`. Không viết converter đi đọc tiếp source khác để “fallback” khi một value malformed trừ khi đó là một policy rõ ràng.

### Lỗi conversion nên làm test fail

Nếu property `sample.window=bad-value`, ngôn ngữ cấu hình đã sai. Tests nên fail với conversion/bean creation exception, không chuyển về một dải cổng hợp lệ ngẫu nhiên. Silent fallback che lỗi deploy.

## 11. Cấu hình BeanFactory conversionService

Trong plain Spring Framework, BeanFactory tìm bean được đặt tên `conversionService` để dùng trong conversion lúc tạo/inject bean, nếu type thích hợp.

~~~java
@Bean("conversionService")
static ConversionService conversionService() {
    DefaultConversionService service =
            new DefaultConversionService();
    service.addConverter(new PortWindowConverter());
    return service;
}
~~~

Đây là snippet; cần imports `@Bean`, `ConversionService`, `DefaultConversionService`. `static @Bean` giúp giảm rủi ro instantiate configuration class sớm cho hạ tầng được yêu cầu trong quá trình khởi tạo.

**Giữ built-in converters:** `DefaultConversionService` được tạo với bộ converters tiêu chuẩn rồi bổ sung converter tùy biến. Đừng tự tạo `GenericConversionService` hoàn toàn trống để thay BeanFactory conversion service nếu ứng dụng vẫn cần `String→Integer/Boolean`.

### Có cần PropertySourcesPlaceholderConfigurer?

`PropertySourcesPlaceholderConfigurer` là một `BeanFactoryPostProcessor` có vai trò resolve `${...}` trong bean definitions/values; môi trường annotation configuration hiện đại có các mechanisms để xử lý embedded placeholders, nhưng những tình huống muốn **fail strict trên unresolved placeholders**, legacy XML hoặc explicit ordering có thể dùng PSPC trong đúng context.

~~~java
@Bean
static PropertySourcesPlaceholderConfigurer placeholders() {
    return new PropertySourcesPlaceholderConfigurer();
}
~~~

Đây là snippet riêng, cần import `org.springframework.context.support.PropertySourcesPlaceholderConfigurer`; không phải phần của runnable demo vì demo dùng test properties đủ keys. BFPP có scope theo BeanFactory; đặt ở đúng context, không giả định post-processor của parent tự áp cho child.

### ConversionService không thay đổi annotation conditions

Một converter custom trên `sample.window` không có chức năng bật/tắt `@Profile` beans. Bean condition evaluation và value conversion ở injection points là hai concerns khác nhau.

## 12. ApplicationContextInitializer: cấu hình trước refresh

Với TestContext, `@ContextConfiguration(initializers=...)` hoặc thuộc tính `initializers` của `@SpringJUnitConfig` cho phép gọi một `ApplicationContextInitializer` trước khi refresh context.

~~~java
public final class EnvironmentBootstrap
        implements ApplicationContextInitializer<
                ConfigurableApplicationContext> {

    @Override
    public void initialize(
            ConfigurableApplicationContext context) {

        ConfigurableEnvironment env = context.getEnvironment();

        env.getConversionService()
                .addConverter(new PortWindowConverter());

        env.getPropertySources().addLast(
                new MapPropertySource(
                        "initializerFallback",
                        Map.of("sample.initializerOnly",
                               "hello-initializer")));
    }
}
~~~

Đây là snippet, imports và version hoàn chỉnh ở mục 14. Nó làm hai việc:

- Đăng ký converter cho `Environment.getProperty(...,PortWindow.class)` trước khi tests chạy.
- Thêm fallback `PropertySource` cuối danh sách, không ghi đè inline/file settings.

### @ActiveProfiles và initializer không phải cùng công cụ

`@ActiveProfiles` là metadata của TestContext để quyết định profile trước load; initializer có thể cấu hình Environment nhưng nếu tự thay profiles trong đó, phải hiểu thứ tự và không tạo một tập active profiles mâu thuẫn với test metadata. Trong demo, initializer **không thay active profiles**; mỗi test class dùng `@ActiveProfiles` tường minh.

### addLast hay addFirst?

- `addFirst` phù hợp với source override ưu tiên cao khi chủ đích rõ.
- `addLast` phù hợp với baseline fallback.
- `addBefore`/`addAfter` hoặc `replace` có thể chính xác hơn nếu đã biết source name.
- Không mutate system properties toàn process khi điều chỉnh chỉ một test context.

## 13. Thiết kế dự án Maven/JUnit: hai profiles, hai cấu hình giá trị

Ta xây một dự án nhỏ:

- `DemoTestConfig` khai báo bean `Settings`, converter bean tên `conversionService`, hai marker beans với `@Profile("dev")` và `@Profile("prod")`.
- `@PropertySource("classpath:demo/defaults.properties")` cung cấp defaults ở application config.
- `@TestPropertySource(locations="classpath:demo/file-overrides.properties")` cung cấp test-file overrides.
- Inline test properties đổi giữa `dev` và `prod`, có precedence cao hơn file.
- `EnvironmentBootstrap` thêm một fallback source và đăng ký converter cho `Environment`.
- Hai test classes có active profiles khác nhau; mỗi class gồm ba `@Test` methods.
- Assertions so sánh exact raw/effective properties, profile-specific beans, converted built-in values, custom record conversion ở cả `Environment` và `@Value`, và đường lỗi conversion.

### Cấu trúc files

~~~text
spring-test-profiles-properties/
├── pom.xml
└── src/
    └── test/
        ├── java/
        │   └── example/
        │       ├── PortWindow.java
        │       ├── PortWindowConverter.java
        │       ├── Settings.java
        │       ├── DemoTestConfig.java
        │       ├── EnvironmentBootstrap.java
        │       ├── DevContainerTests.java
        │       └── ProdContainerTests.java
        └── resources/
            └── demo/
                ├── defaults.properties
                └── file-overrides.properties
~~~

Toàn bộ code nằm trong `src/test`, vì bài tập tập trung vào integration tests container, không cần build một ứng dụng production độc lập.

### pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-test-profiles-properties</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.19</version>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-test</artifactId>
            <version>6.2.19</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.12.2</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.3</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

**Vì sao dùng spring-test?** `@SpringJUnitConfig`, TestContextManager, `@ActiveProfiles`, `@TestPropertySource` và SpringExtension nằm trong `spring-test`. Chỉ có `spring-context` không đủ cho JUnit integration với TestContext.

**Vì sao dùng Jupiter?** `@Test`, `assertEquals`, `assertThrows` và extension APIs thuộc JUnit 5; Maven Surefire version hiện đại nhận diện các file tên `*Tests.java` để chạy Jupiter.

### src/test/resources/demo/defaults.properties

~~~properties
sample.mode=config-mode
sample.origin=config-origin
sample.enabled=false
sample.retries=2
sample.window=7100:7102
sample.fallback=from-config
~~~

### src/test/resources/demo/file-overrides.properties

~~~properties
sample.mode=file-mode
sample.origin=file-origin
sample.enabled=false
sample.retries=5
sample.window=7200:7202
~~~

Đây là hai files properties riêng biệt. Không thêm Markdown fences vào các file thật. `@PropertySource` trỏ tới `defaults.properties`, `@TestPropertySource(locations=...)` trỏ tới `file-overrides.properties`.

## 14. Toàn bộ Java code chạy kiểm thử

### src/test/java/example/PortWindow.java

~~~java
package example;

public record PortWindow(int start, int end) {

    public PortWindow {
        if (start < 1 || end > 65535 || start > end) {
            throw new IllegalArgumentException(
                    "Invalid port window");
        }
    }
}
~~~

Java record có value-based `equals`, nên `assertEquals(new PortWindow(...), actual)` là phép kiểm phù hợp. Constraint loại bỏ các trường hợp 0, cổng vượt 65535 hoặc start lớn hơn end.

### src/test/java/example/PortWindowConverter.java

~~~java
package example;

import org.springframework.core.convert.converter.Converter;

public final class PortWindowConverter
        implements Converter<String, PortWindow> {

    @Override
    public PortWindow convert(String source) {
        String[] pieces = source.split(":", -1);

        if (pieces.length != 2) {
            throw new IllegalArgumentException(
                    "Expected start:end");
        }

        try {
            return new PortWindow(
                    Integer.parseInt(pieces[0].trim()),
                    Integer.parseInt(pieces[1].trim()));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Port values must be integers", ex);
        }
    }
}
~~~

### src/test/java/example/Settings.java

~~~java
package example;

public record Settings(
        String mode,
        boolean enabled,
        int retries,
        PortWindow window) {
}
~~~

### src/test/java/example/DemoTestConfig.java

~~~java
package example;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

@Configuration(proxyBeanMethods = false)
@PropertySource("classpath:demo/defaults.properties")
public class DemoTestConfig {

    @Bean("conversionService")
    public static ConversionService conversionService() {
        DefaultConversionService service =
                new DefaultConversionService();
        service.addConverter(new PortWindowConverter());
        return service;
    }

    @Bean
    public Settings settings(
            @Value("${sample.mode}") String mode,
            @Value("${sample.enabled}") boolean enabled,
            @Value("${sample.retries}") int retries,
            @Value("${sample.window}") PortWindow window) {

        return new Settings(mode, enabled, retries, window);
    }

    @Bean("devFlag")
    @Profile("dev")
    public String devFlag() {
        return "DEV";
    }

    @Bean("prodFlag")
    @Profile("prod")
    public String prodFlag() {
        return "PROD";
    }
}
~~~

**Hai bean tên `devFlag` và `prodFlag` chỉ dùng để quan sát profile selection.** Chúng trả `String`, nhưng không được inject vào một field `String` thiếu qualifier nên không gây ambiguity trong ví dụ. Method `settings` lấy bốn giá trị từ property sources; built-in conversions xử lý boolean/int, và custom BeanFactory conversionService xử lý PortWindow.

### src/test/java/example/EnvironmentBootstrap.java

~~~java
package example;

import java.util.Map;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public final class EnvironmentBootstrap
        implements ApplicationContextInitializer<
                ConfigurableApplicationContext> {

    @Override
    public void initialize(
            ConfigurableApplicationContext context) {

        ConfigurableEnvironment environment =
                context.getEnvironment();

        environment.getConversionService()
                .addConverter(new PortWindowConverter());

        environment.getPropertySources().addLast(
                new MapPropertySource(
                        "initializerFallback",
                        Map.<String, Object>of(
                                "sample.mode", "initializer-mode",
                                "sample.initializerOnly",
                                    "hello-initializer")));
    }
}
~~~

Dùng `Map.<String,Object>of(...)` giúp type của Map rõ với `MapPropertySource`. Initializer được TestContext gọi trước khi context refresh, nên converter cho Environment sẵn sàng khi test gọi `getProperty("sample.window", PortWindow.class)`.

`@PropertySource` trên `DemoTestConfig` thêm defaults vào Environment khi Spring xử lý configuration. Vị trí của defaults so với `initializerFallback` phụ thuộc thời điểm đăng ký; vì hai nguồn không trùng các khóa được kiểm tra ở đây, kết quả không dựa vào thứ tự tương đối đó. Inline và file properties của TestContext có mức ưu tiên cao hơn.

### src/test/java/example/DevContainerTests.java

~~~java
package example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringJUnitConfig(
        classes = DemoTestConfig.class,
        initializers = EnvironmentBootstrap.class)
@ActiveProfiles("dev")
@TestPropertySource(
        locations = "classpath:demo/file-overrides.properties",
        properties = {
                "sample.mode=inline-dev",
                "sample.enabled=true",
                "sample.retries=7",
                "sample.window=8100:8102"
        })
class DevContainerTests {

    @Autowired
    Settings settings;

    @Autowired
    ApplicationContext context;

    @Test
    void selectsDevGraphAndConvertsValuesForBean() {
        assertEquals("inline-dev", settings.mode());
        assertTrue(settings.enabled());
        assertEquals(7, settings.retries());
        assertEquals(new PortWindow(8100, 8102),
                settings.window());

        assertTrue(context.containsBean("devFlag"));
        assertFalse(context.containsBean("prodFlag"));
    }

    @Test
    void resolvesSourcesByPrecedence() {
        Environment env = context.getEnvironment();

        assertEquals("inline-dev",
                env.getProperty("sample.mode"));
        assertEquals("file-origin",
                env.getProperty("sample.origin"));
        assertEquals("from-config",
                env.getProperty("sample.fallback"));
        assertEquals("hello-initializer",
                env.getProperty("sample.initializerOnly"));
    }

    @Test
    void environmentConvertsBooleanIntegerAndPortWindow() {
        Environment env = context.getEnvironment();

        assertEquals(Boolean.TRUE,
                env.getProperty("sample.enabled", Boolean.class));
        assertEquals(Integer.valueOf(7),
                env.getProperty("sample.retries", Integer.class));
        assertEquals(new PortWindow(8100, 8102),
                env.getProperty("sample.window", PortWindow.class));

        assertTrue(env.acceptsProfiles(
                org.springframework.core.env.Profiles.of("dev")));
    }
}
~~~

Các tests ở lớp `DevContainerTests` kiểm tra ba thứ tách biệt: giá trị injected vào bean, precedence của property sources, và conversion của Environment. Assertion về bean `devFlag`/`prodFlag` chứng minh Spring thực sự chọn bean graph theo active profile chứ không chỉ thay một property string.

### src/test/java/example/ProdContainerTests.java

~~~java
package example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringJUnitConfig(
        classes = DemoTestConfig.class,
        initializers = EnvironmentBootstrap.class)
@ActiveProfiles("prod")
@TestPropertySource(
        locations = "classpath:demo/file-overrides.properties",
        properties = {
                "sample.mode=inline-prod",
                "sample.enabled=false",
                "sample.retries=13",
                "sample.window=9200:9201"
        })
class ProdContainerTests {

    @Autowired
    Settings settings;

    @Autowired
    ApplicationContext context;

    @Test
    void selectsProdGraphAndConvertsForBean() {
        assertEquals("inline-prod", settings.mode());
        assertFalse(settings.enabled());
        assertEquals(13, settings.retries());
        assertEquals(new PortWindow(9200, 9201),
                settings.window());

        assertTrue(context.containsBean("prodFlag"));
        assertFalse(context.containsBean("devFlag"));
    }

    @Test
    void prodProfileAndSourceOrderRemainIndependent() {
        Environment env = context.getEnvironment();

        assertTrue(env.acceptsProfiles(Profiles.of("prod")));
        assertFalse(env.acceptsProfiles(Profiles.of("dev")));
        assertEquals("inline-prod",
                env.getProperty("sample.mode"));
        assertEquals("file-origin",
                env.getProperty("sample.origin"));
        assertEquals("from-config",
                env.getProperty("sample.fallback"));
        assertEquals(new PortWindow(9200, 9201),
                env.getProperty("sample.window",
                        PortWindow.class));
    }

    @Test
    void rejectsMalformedCustomValue() {
        ConfigurableEnvironment env =
                (ConfigurableEnvironment) context.getEnvironment();

        assertThrows(ConversionFailedException.class,
                () -> env.getConversionService()
                        .convert("not-a-range",
                                PortWindow.class));
    }
}
~~~

Phép thử cuối **không ghi đè property của context thật**: nó đưa một value malformed trực tiếp vào converter của Environment và xác nhận Spring bọc lỗi chuyển đổi vào `ConversionFailedException`. Các tests trước vẫn xác nhận properties effective đúng. Đây là cách kiểm thử negative case mà không cố ý làm cả `ApplicationContext` fail khi refresh.

### Chạy Maven

~~~bash
mvn test
~~~

Hoặc chọn từng profile test class:

~~~bash
mvn -Dtest=DevContainerTests test
mvn -Dtest=ProdContainerTests test
~~~

### Kết quả dự kiến

Với các files đúng như trên và dependencies được giải thành công, Maven Surefire cần chạy:

~~~text
DevContainerTests  : 3 tests, 0 failures, 0 errors
ProdContainerTests : 3 tests, 0 failures, 0 errors
Total              : 6 tests, 0 failures, 0 errors, 0 skipped
~~~

Đây là **tóm tắt kỳ vọng từ các assertions**, không phải lời khẳng định đã chạy Maven. Console output chính xác của Surefire còn tùy plugin, logging và thứ tự tests. Kiểm chứng thực tế bằng `target/surefire-reports/`.

## 15. Phân tích chi tiết kết quả của DevContainerTests

### Chọn beans theo profile

~~~text
@ActiveProfiles("dev")
  ├── devFlag  : registered
  └── prodFlag : not registered
~~~

`@Profile("dev")`/`@Profile("prod")` được Spring xét trong config class processing. Đây là quyết định tạo bean definitions; `sample.mode=inline-dev` chỉ là một key dùng cho Settings, không tự kích hoạt `devFlag`.

### @Value nhận typed parameters

~~~text
sample.mode    → "inline-dev"  → String
sample.enabled → "true"        → boolean true
sample.retries → "7"           → int 7
sample.window  → "8100:8102"   → PortWindow(8100, 8102)
~~~

`settings()` của `DemoTestConfig` nhận bốn tham số đã convert. Các test xác nhận record fields. Converter bean tên `conversionService` làm cho BeanFactory biết chuyển `String → PortWindow`.

### Environment cũng convert PortWindow

~~~java
env.getProperty("sample.window", PortWindow.class)
~~~

trả `new PortWindow(8100,8102)` nhờ converter mà `EnvironmentBootstrap` đã thêm vào **Environment conversion service** trước refresh. Đây là một check riêng biệt với `@Value`.

### Nguồn file thắng ứng dụng mặc định

`sample.origin` không có ở inline test, nhưng có trong `file-overrides.properties` và `defaults.properties`. Nó trả `file-origin`. Nếu đảo thứ tự hoặc nhầm `@PropertySource` với `@TestPropertySource`, assertion này rất dễ phát hiện lỗi.

### Source addLast chỉ fallback

`sample.initializerOnly` chỉ có trong `initializerFallback` nên vẫn đọc được; `sample.fallback` chỉ có trong `defaults.properties`. Hai khóa riêng biệt này kiểm chứng khả năng truy cập cả hai nguồn, mà không giả định `@PropertySource` phải ưu tiên hơn nguồn thêm bằng `addLast` trước lúc refresh.

## 16. Phân tích kết quả của ProdContainerTests

Thay profile và inline test properties:

~~~text
@ActiveProfiles("prod")
  ├── prodFlag : registered
  └── devFlag  : not registered

Effective inline:
  sample.mode    = inline-prod
  sample.enabled = false
  sample.retries = 13
  sample.window  = 9200:9201
~~~

Cùng `DemoTestConfig` và cùng test property file, nhưng **@ActiveProfiles và inline properties khác nhau** làm TestContext tải cấu hình độc lập. Vì vậy bean singleton Settings của dev không bị reuse trong prod test.

### Tại sao không dùng một test class rồi đổi active profiles giữa methods?

Một `ApplicationContext` đã được refresh và cache theo profile. Đổi `env.setActiveProfiles("prod")` trong method dev test có thể làm property API báo prod active nhưng **không làm `@Profile("dev")` bean đã được tạo tự biến mất**. Test sẽ trộn metadata mới với bean graph cũ. Cách xác thực đúng là tạo hai context load configurations, ví dụ hai test classes như demo.

### Lỗi chuyển đổi được kiểm tra như thế nào?

`PortWindowConverter` yêu cầu chuỗi có đúng hai numbers phân cách bằng `:`. Với `"not-a-range"`, converter ném `IllegalArgumentException`; `GenericConversionService` bắt exception và bọc thành `ConversionFailedException` khi caller gọi convert.

`assertThrows(ConversionFailedException.class, ...)` giúp test chính xác failure semantics; không nuốt exception rồi dùng default PortWindow ngẫu nhiên. Nó cũng không sửa các bean singleton đã chạy thành công trong test context.

## 17. Context cache: đổi profile/properties làm thay key

Trong TestContext, `MergedContextConfiguration` tham gia cache key. Hai classes `DevContainerTests` và `ProdContainerTests` ở demo **khác `@ActiveProfiles` và inline test properties**, do đó thường nạp hai contexts phân biệt. Không cần dùng `@DirtiesContext` chỉ để chuyển profile giữa các test classes.

Hãy assert **beans và resolved values của từng profile**, không assert context identity xuyên classes: runner có thể fork JVM, cache entry có thể evict hoặc bị dirtied. Ngược lại, **cùng config** có thể reuse context và cả singleton mutable state; demo dùng immutable `Settings` record nên không có mutable fixture cần reset. Bài isolation/cache chuyên sâu giải thích đầy đủ cấu phần cache key, dirty modes, external state và parallel tests.

## 18. @DynamicPropertySource: độ ưu tiên cao và giá trị runtime

Trong integration tests dùng Testcontainers, port hoặc host được chọn lúc runtime. `@DynamicPropertySource` thêm key-value từ `Supplier` để Spring đọc khi cần.

~~~java
@DynamicPropertySource
static void dynamicProperties(DynamicPropertyRegistry registry) {
    registry.add("sample.port", () -> 54321);
    registry.add("sample.host", () -> "127.0.0.1");
}
~~~

Đây là snippet, cần imports của `DynamicPropertySource` và `DynamicPropertyRegistry`, không thuộc Maven demo. Real test cần lấy mapped port từ test container/server thực, không hard-code production endpoint.

### Precedence

Spring 6.2 Reference nêu rõ **dynamic properties có precedence cao hơn `@TestPropertySource`**, trong khi inlined test properties cao hơn test property resources. Đây là quy tắc quan trọng khi một giá trị inline “không có hiệu lực” do dynamic source đang override.

### Supplier được gọi khi resolve

Giá trị đăng ký dynamic property dùng `Supplier<?>`. Cùng một supplier có thể được gọi khi property cần resolve; không assume Spring chỉ evaluate đúng một lần trong mọi loại consumer. Nếu cần stable port/URL, runtime resource phải tạo một snapshot/giá trị nhất quán trước lookup.

### Context caching và dynamic values

Các dynamic property methods nằm trong cache customizers. Nếu subclasses dùng cùng dynamic method nhưng actual values khác nhau giữa subclasses, context cache có thể reuse sai external resource config. Spring docs khuyến nghị cân nhắc `@DirtiesContext` hoặc thiết kế test resource lifecycle/cache phân biệt rõ.

### Không giống @Value runtime refresh

Dynamic source có Supplier nhưng singleton `@Value` không tự rebind nếu một later lookup trả giá trị khác. **Giá trị property resolver động** và **các bean fields đã inject** là hai thời điểm khác nhau.

## 19. Kiểm tra property source nào thắng mà không lộ dữ liệu

Trong debug test:

~~~java
ConfigurableEnvironment configurable =
        (ConfigurableEnvironment) context.getEnvironment();

for (PropertySource<?> ps : configurable.getPropertySources()) {
    System.out.println(ps.getName());
}
~~~

Đây là snippet; cần imports. Nó in **tên** sources theo thứ tự ưu tiên, không in raw secret values. Nếu cần biết nguồn đầu tiên có key:

~~~java
String key = "sample.origin";

for (PropertySource<?> ps : configurable.getPropertySources()) {
    if (ps.containsProperty(key)) {
        System.out.println("First matching source: " + ps.getName());
        break;
    }
}
~~~

### Đừng hard-code các source names trong assertions nghiệp vụ

Spring có tên cho inlined TestPropertySource và file-backed resources, nhưng tên hiển thị có thể thay đổi theo loader/resource path/version. Với business tests, assert `getProperty` values. Với framework-level tests muốn kiểm chứng đúng name/position, làm việc với source được **bạn tạo có tên tường minh**.

### Debugging conversion

Nếu `environment.getProperty("sample.window", PortWindow.class)` lỗi:

1. In **key name và raw data đã sanitized**, tránh secrets.
2. Kiểm tra effective raw string đang thắng.
3. Kiểm tra Environment conversion service có converter `String→PortWindow`.
4. Kiểm tra grammar (separator, spaces, bounds).
5. Nếu `@Value` lỗi nhưng Environment conversion đúng, xem BeanFactory `conversionService` và placeholder resolution riêng.
6. Nếu parse fail ở refresh, root cause có thể bị bọc trong `BeanCreationException`.

## 20. Lỗi thường gặp về profiles và conditions

| Triệu chứng | Nguyên nhân thường gặp | Cách sửa |
| --- | --- | --- |
| `@Profile("prod")` bean xuất hiện trong test dev | Có nhiều active profiles do inheritance/config khác | Đọc `getActiveProfiles()` và annotation hierarchy |
| Bean dev không xuất hiện | `@ActiveProfiles` thiếu, sai tên, expression khác | Kiểm tra @Profile trên config/bean và test metadata |
| Đổi `sample.mode=prod` nhưng bean graph vẫn dev | Property khác active profile | Dùng `@ActiveProfiles("prod")` hoặc cấu hình trước refresh |
| Đổi `env.setActiveProfiles` trong @Test không tác dụng | Context đã refresh | Dùng test classes/context configs riêng |
| `@Profile("default")` bean mất khi active profile | Default profile chỉ áp khi không có active profiles | Xác định policy profile rõ |
| Một test chạy riêng pass, chạy suite fail | Global JVM properties/shared contexts/mutable state | Isolate settings, cache key, test reset |
| Hai profiles cùng active làm có hai beans type giống nhau | Bean graph ambiguous | @Primary/@Qualifier hoặc profile selection rõ |
| Spring Boot application dùng khác precedence | Boot Config Data/auto-config thêm sources | Kiểm tra Spring Boot docs riêng |
| `@Tag("dev")` không chọn bean dev | JUnit tag không phải Spring profile | @ActiveProfiles |
| Spring conditional fail dù property đúng | `@Conditional` evaluate ở bootstrap/timing khác | Set sources trước refresh và test condition path |

**Một profile là tên**, không phải enum Java với compiler validation tự động. Tránh typo bằng constants/conventions và integration tests theo từng deployment variant quan trọng.

## 21. Lỗi thường gặp về property precedence

| Triệu chứng | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| Giá trị file test không thắng inline test | Inline có precedence cao hơn | Sửa/remove inline override |
| `@PropertySource` không thắng `@TestPropertySource` | Test sources có precedence cao hơn | Đây là behavior mong đợi |
| Property source thêm bằng initializer bị bỏ qua | Dùng `addLast` và key tồn tại ở nguồn cao hơn | Dùng key khác hoặc thay priority có chủ đích |
| Key có trong file nhưng getProperty null | Sai path/classpath, loader, key spelling | Kiểm tra resource và raw value |
| Empty `@TestPropertySource` làm context fail | Default test properties file vắng | Chỉ định locations/properties rõ |
| Có hai MapPropertySource trùng tên | Source name collision/replacement | Đặt tên rõ, inspect MutablePropertySources |
| Đổi System.setProperty ảnh hưởng tests khác | Global mutable JVM state | Dùng test property source per context |
| Giá trị resource không đúng ở CI | System env, path, encoding hoặc profile khác | Log source names/active profiles an toàn |
| YAML file không được đọc theo kỳ vọng | Không có loader/factory tương ứng | Dùng .properties hoặc factory phù hợp |
| Dynamic source override inline unexpectedly | @DynamicPropertySource có precedence cao | Đọc dynamic registration và test |
| Test context cache giữ cấu hình cũ | Configuration metadata không đổi nhưng external state đổi | Dùng stable test data hoặc invalidate cache đúng |
| Đặt property sau refresh mà @Value không đổi | Singleton đã inject | Tạo context mới hoặc thiết kế dynamic config explicit |

### Thứ tự sources khác với thứ tự keys trong một file

Trong `.properties`, thứ tự các dòng không phải policy “key sau luôn thắng key trước giữa các sources”. Priority được điều khiển bởi thứ tự `PropertySource` objects. Trùng key **trong cùng file** là vấn đề parsing/last-write theo parser, tránh duplicate keys và lint configuration để rõ ý định.

## 22. Lỗi thường gặp về type conversion

| Triệu chứng | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| `"seven"` không convert sang `int` | Value malformed | Kiểm tra test data, fail fast |
| `"perhaps"` không convert sang `boolean` | Không phải boolean hợp lệ | Dùng true/false rõ |
| `PortWindow` conversion không có converter | Domain type không có built-in path | Đăng ký Converter<String,PortWindow> |
| Environment convert thành công, @Value fail | Converter chỉ đăng ký ở Environment | Cấu hình BeanFactory conversionService |
| @Value thành công, Environment typed lookup fail | Converter chỉ ở BeanFactory | env.getConversionService().addConverter |
| Custom ConversionService làm mất built-ins | Tạo registry rỗng thay vì default | Dùng DefaultConversionService và add converter |
| Giá trị malformed nhưng test vẫn pass | Silent fallback/catch quá rộng | Assert conversion failure, không swallow |
| Converter trả null cho malformed | Contract ambiguity | Prefer exception cho required value |
| `PortWindow(9002,9000)` được chấp nhận | Thiếu domain validation | Validate start/end ở record constructor |
| BeanCreationException khi startup | @Value conversion fail trong bean factory method | Đọc nested causes và source effective value |
| Converter có state mutable không thread safe | Custom converter quản lý shared state | Giữ converter stateless/immutable |
| JUnit test chỉ so raw string | Không test actual typed injection | Kiểm tra bean record typed fields |

### ConversionFailedException vs application exception

Khi gọi `ConversionService.convert`, `GenericConversionService` thường bọc converter exception thành `ConversionFailedException`. Khi lỗi xảy ra trong quá trình Spring tạo bean từ `@Value`, bạn có thể thấy các wrapper exception như `BeanCreationException` hoặc `UnsatisfiedDependencyException`. Đọc root cause; không cần hard-code exact message text trong tests vì message có thể khác giữa versions.

## 23. Kiểm thử negative path mà không phá cached context

Một test cố ý ghi malformed source vào live cached Environment rồi mong singleton tự rebind là một test rất dễ nhiễm trạng thái. Ba lựa chọn tốt hơn:

1. **Test converter trực tiếp** với bad input, như `ProdContainerTests.rejectsMalformedCustomValue()`.
2. Tạo một **ApplicationContext mới** được cấu hình bad property và assert refresh fail, rồi close trong `finally`.
3. Dùng một **test class riêng** với `@TestPropertySource(properties="sample.window=bad")`, assert expected startup failure thông qua appropriate harness chứ không giả vờ các `@Test` methods vẫn được chạy khi context không load.

### Kiểm thử strict missing property

Nếu `@Value("${required.key}")` cần key bắt buộc, bảo đảm cơ chế placeholder resolution phù hợp và test missing key. Đừng tùy tiện dùng placeholder default `:...` cho secrets hoặc contract-required configuration, vì nó có thể che deploy sai.

### Property validation

Ngoài parser conversion, cần kiểm tra ràng buộc nghiệp vụ: cổng hợp lệ, URL scheme được phép, retries trong giới hạn, durations không âm, path không traversal. Type conversion là một bước; validation là một bước khác. `@ConfigurationProperties` và Bean Validation thuộc một workflow khác (đặc biệt trong Boot), không phải tự bật chỉ vì có `Converter`.

## 24. Liên quan tới ApplicationContext parent-child

Nếu test có nhiều `ApplicationContext` theo `@ContextHierarchy`, mỗi context có Environment/BeanFactory boundaries riêng. Parent property sources và profiles có thể được merge vào child khi hierarchy được thiết lập, nhưng không được giả định mọi post-processors/conversionService beans của parent tự áp dụng vào child bean definitions.

- Parent BFPP/BPP có phạm vi parent BeanFactory.
- Child `Environment` có thể dùng properties đã merge từ parent theo rules `ConfigurableEnvironment.merge`.
- Converter của **BeanFactory** cần được đăng ký ở factory thật tạo bean.
- TestContext cache key có thể gồm parent context configuration.
- `@ActiveProfiles` và `@TestPropertySource` ảnh hưởng context creation theo test configuration, không phải “đổi active profile” live trên mọi sibling contexts.

Bài này sử dụng **một ApplicationContext cho mỗi test configuration** để tập trung đúng mục tiêu profile/source/conversion. Phân cấp context là chủ đề kiến trúc riêng; không tạo hierarchy chỉ để thử hai profiles.

## 25. CI strategy: ổn định, độc lập và nhanh

### Giữ dữ liệu test có thể dự đoán

- Dùng test .properties files trong `src/test/resources` để độc lập hệ thống ngoài.
- Không dựa vào giá trị environment machine khi assert exact strings.
- Không gọi `System.setProperty` toàn JVM nếu nhiều classes chạy song song.
- Không tạo random port trừ khi đang test dynamic resource thực.
- Nếu dùng dynamic values, giữ chúng stable trong lifetime context và test cache key.

### Giữ conversion deterministic

- Converter nên stateless.
- Không gọi network/database/clock trong converter cho domain configuration.
- Nếu parse số dùng locale-sensitive format, chỉ định locale hoặc grammar rõ ràng.
- Với boolean, đặt `true/false` chuẩn và test negative values.
- Với duration/size, dùng conversion API thích hợp theo framework thật; đừng giả định Boot-specific duration binding có trong plain Spring Core.

### Giữ context cache hữu ích

Hai classes dev/prod cần hai cache entries; đó là intentional. Không tự thêm `@DirtiesContext` vào cả hai nếu không mutate container. Không tạo hàng chục inline properties ngẫu nhiên không cần thiết làm phân mảnh cache keys.

### Phân biệt startup-test và behavior-test

Context refresh pass chỉ nói rằng những eagerly created beans đã được khởi tạo. Với lazy/prototype beans hoặc provider lookup deferred, gọi feature path tương ứng trong test. Profile-specific beans có thể chưa được instantiate nếu lazy, nhưng bean definition eligibility vẫn có thể kiểm tra bằng `containsBean`/bean type lookup khi thích hợp.

## 26. Câu hỏi tự kiểm tra

**Câu 1.** `@ActiveProfiles("dev")` có tự đặt `sample.mode=dev` không?

**Trả lời:** Không. Active profile quyết định bean selection; property là nguồn dữ liệu riêng.

**Câu 2.** Nếu inline test và file test cùng có `sample.mode`, nguồn nào thắng?

**Trả lời:** Inline `@TestPropertySource(properties=...)` có precedence cao hơn `@TestPropertySource(locations=...)`.

**Câu 3.** `@DynamicPropertySource` đứng ở đâu so với inline @TestPropertySource?

**Trả lời:** Dynamic test properties có precedence cao hơn.

**Câu 4.** Nếu key không có trong inline hay test file nhưng có trong @PropertySource, có đọc được không?

**Trả lời:** Có, nếu không có source ưu tiên cao hơn chứa key và file đã được nạp đúng.

**Câu 5.** `@PropertySource` và `@TestPropertySource` có giống nhau không?

**Trả lời:** Không. Một cái gắn với application @Configuration, một cái với TestContext metadata; precedence và lifecycle khác.

**Câu 6.** Thay active profiles sau khi context refresh có tạo lại beans @Profile không?

**Trả lời:** Không. Cần context configuration mới/reload hợp lệ.

**Câu 7.** Vì sao DevContainerTests và ProdContainerTests không nên chung cache entry?

**Trả lời:** Khác activeProfiles và inline test properties, là các yếu tố cache key.

**Câu 8.** String `"13"` tiêm vào `int` cần custom converter không?

**Trả lời:** Không trong cấu hình conversion tiêu chuẩn, Spring có built-in converter thích hợp.

**Câu 9.** Tại sao custom `PortWindow` cần converter?

**Trả lời:** Chuỗi `"9200:9201"` không có conversion path chuẩn sang domain record tùy biến.

**Câu 10.** Đăng ký converter vào Environment có chắc làm `@Value` custom type thành công không?

**Trả lời:** Không. BeanFactory có conversion service riêng; cần cấu hình đúng nơi.

**Câu 11.** Đăng ký bean tên conversionService có chắc Environment.getProperty(key, PortWindow.class) thành công không?

**Trả lời:** Không tự động. Cần converter trên Environment property resolver.

**Câu 12.** Vì sao môi trường test cần một `ApplicationContextInitializer`?

**Trả lời:** Cho phép thiết lập Environment/custom source/conversion sớm trước refresh.

**Câu 13.** `@DirtiesContext` có cần chỉ vì đang chạy hai profiles?

**Trả lời:** Không. Profile khác thuộc cache key; dùng @DirtiesContext khi context thực sự bị làm bẩn.

**Câu 14.** `containsProperty(key)` có chứng minh giá trị đã convert hợp lệ không?

**Trả lời:** Không. Nó chỉ nói key có khả năng resolve; cần kiểm tra typed getProperty hoặc bean injection.

**Câu 15.** Nếu file config chứa `sample.window=bad`, nên làm gì?

**Trả lời:** Fail rõ bằng conversion/validation, không silently chọn dải cổng mặc định.

**Câu 16.** Có nên đọc toàn bộ property sources và in raw values vào CI logs?

**Trả lời:** Không. Có thể rò rỉ secrets; nên in source names và keys đã sanitized.

## 27. Checklist triển khai và review

1. Chủ đề test là profile selection, property precedence hay conversion, hay kết hợp có lý do?
2. `@ActiveProfiles` được khai báo trước khi TestContext load application context?
3. `@Profile` beans/configurations có biểu thức đúng?
4. Không dùng `@Tag` của JUnit thay cho Spring profile?
5. Không trông chờ property `sample.mode` tự đặt active profile?
6. Test `@PropertySource` file có tồn tại trên classpath?
7. Test `@TestPropertySource(locations=...)` có path đúng?
8. Inline test properties có chủ đích ghi đè file values?
9. Có kiểm tra `@DynamicPropertySource` nếu dùng Testcontainers?
10. Source precedence đã được khẳng định bằng exact effective values?
11. Fallback source đã đăng ký bằng `addLast` hoặc ordering thích hợp?
12. Tránh source-name collision không có chủ đích?
13. Không mutate System properties để cô lập một context test?
14. Có kiểm thử built-in Boolean/Integer conversion?
15. Custom converter có rõ grammar, bounds và failure policy?
16. Converter được đăng ký ở Environment nếu dùng typed getProperty?
17. Converter được đăng ký ở BeanFactory nếu dùng @Value custom type?
18. BeanFactory conversionService vẫn giữ built-in converters?
19. Có test giá trị @Value trên bean thực được tạo bởi Spring?
20. Có test Environment typed lookup riêng?
21. Negative conversion test có ném exception mong đợi?
22. Không sửa cached live Environment rồi giả định singleton tự rebind?
23. Dev/prod có cache keys khác nhau khi cần?
24. Không thêm @DirtiesContext nếu chỉ đổi profile/test properties?
25. Test resource values ổn định với UTF-8, CI, timezone và locale?
26. Có kiểm tra profile-specific bean names/identities?
27. Không log property values có thể chứa credentials/secrets?
28. Test startup và deferred lookup paths đúng use case?
29. Có phân biệt Spring Framework Core với Boot configuration binding?
30. Đã đọc Surefire reports nếu build không đạt expected six passing tests?

## 28. Tài liệu tham khảo chính thức

- [Spring Framework 6.2 – Environment Abstraction](https://docs.spring.io/spring-framework/reference/6.2/core/beans/environment.html)
- [Spring Framework 6.2 – @Profile and Bean Definition Profiles](https://docs.spring.io/spring-framework/reference/6.2/core/beans/environment.html#beans-definition-profiles-java)
- [Spring Framework 6.2 – Context Configuration with Active Profiles](https://docs.spring.io/spring-framework/reference/6.2/testing/testcontext-framework/ctx-management/env-profiles.html)
- [Spring Framework 6.2 – Context Configuration with Test Property Sources](https://docs.spring.io/spring-framework/reference/6.2/testing/testcontext-framework/ctx-management/property-sources.html)
- [Spring Framework 6.2 – Context Configuration with Dynamic Property Sources](https://docs.spring.io/spring-framework/reference/6.2/testing/testcontext-framework/ctx-management/dynamic-property-sources.html)
- [Spring Framework 6.2 – Context Caching](https://docs.spring.io/spring-framework/reference/6.2/testing/testcontext-framework/ctx-management/caching.html)
- [Spring Framework 6.2 – @SpringJUnitConfig](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/test/context/junit/jupiter/SpringJUnitConfig.html)
- [Spring Framework 6.2 – @TestPropertySource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/test/context/TestPropertySource.html)
- [Spring Framework 6.2 – @ActiveProfiles](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/test/context/ActiveProfiles.html)
- [Spring Framework 6.2 – ConfigurableEnvironment](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/env/ConfigurableEnvironment.html)
- [Spring Framework 6.2 – ConfigurablePropertyResolver](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/env/ConfigurablePropertyResolver.html)
- [Spring Framework 6.2 – ConversionService](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/convert/ConversionService.html)
- [Spring Framework 6.2 – DefaultConversionService](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/convert/support/DefaultConversionService.html)
- [Spring Framework 6.2 – Converter<S,T>](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/convert/converter/Converter.html)
- [Spring Framework 6.2 – ConfigurableApplicationContext conversionService bean name](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/ConfigurableApplicationContext.html)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="container-testing-008">Kiểm chứng phát sự kiện và nhận thông điệp theo Locale</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm chứng phát sự kiện và nhận thông điệp theo Locale trong Spring Core Container

## 1. Mục tiêu: kiểm chứng hai hợp đồng của ApplicationContext

Một ứng dụng Spring thường vừa phát **application events** để thông báo cho các thành phần khác, vừa lấy **message đã bản địa hóa** thông qua `MessageSource`. Hai chức năng cùng thuộc `ApplicationContext` nhưng có hợp đồng riêng:

- **Phát và tiếp nhận sự kiện:** producer gọi `ApplicationEventPublisher.publishEvent(...)`; Spring gửi event đến những `ApplicationListener` hoặc method `@EventListener` được đăng ký phù hợp.
- **Phân giải thông điệp:** một bean hoặc `ApplicationContext` gọi `MessageSource.getMessage(code,args,locale)`; Spring chọn bản dịch theo `Locale`, thay tham số và xử lý fallback/error.

Khi kiểm thử, không nên gộp chúng thành một assertion “chương trình chạy không lỗi”. Ta cần xác minh **đúng event, đúng listener, đúng số lượt, đúng nội dung translation cho từng Locale**, kể cả nhánh lỗi và thiếu key.

Một use case rõ ràng:

```text
OrderService.submit("ORD-7", vi-VN)
     |
     +-- validate input
     |
     +-- ApplicationEventPublisher.publishEvent(
            OrderSubmitted("ORD-7", vi-VN))
                  |
                  v
          ApplicationEventMulticaster
                  |
                  v
          @EventListener onOrderSubmitted(...)
                  |
                  v
          MessageSource.getMessage(
              "order.saved", {"ORD-7"}, vi-VN)
                  |
                  v
          "Đã lưu đơn ORD-7"
                  |
                  v
          EventLog ghi nhận một kết quả
```

**Lưu ý quan trọng:** Spring event **không có một Locale bắt buộc**. `Locale` ở ví dụ là một field của event do application định nghĩa. `MessageSource` chỉ lựa chọn bản dịch nếu caller cung cấp một Locale hoặc chủ động lấy Locale từ context thích hợp. Không suy rằng `publishEvent` tự truyền Locale từ HTTP request sang listener.

Bài này giải thích ranh giới unit/integration test, cơ chế payload event, registry listener, bộ phân giải ResourceBundle, locale fallback, tình huống async/transaction và parent-child contexts; kèm ví dụ **Java 17+, Maven, Spring Framework 6.2.18, JUnit 5** có đầy đủ tệp, lệnh và output dự đoán được.

## 2. Phân biệt bài kiểm thử của publisher, listener và MessageSource

Ta có ba mức kiểm chứng thường bị lẫn:

| Câu hỏi | Test thích hợp | Có cần Spring context? |
| --- | --- | --- |
| Service có yêu cầu phát đúng event không? | Unit test với fake `ApplicationEventPublisher` | Không |
| Method xử lý event có format message đúng không? | Unit test listener + fake `MessageSource` | Không, nếu listener là Java object thường |
| Spring có đăng ký `@EventListener` và dispatch đúng không? | Integration test tạo `ApplicationContext` | Có |
| Một payload record có đến listener đúng type không? | Integration test publisher + listener thật | Có |
| Locale vi-VN, en-US, fr-FR lấy bản dịch đúng từ files không? | Context test với `ResourceBundleMessageSource` | Có cho integration config |
| Thiếu key có trả default hoặc throw đúng không? | Test `MessageSource` API | Có thể test standalone hoặc context |
| Exception listener có quay lại publisher không? | Test multicaster sync/error handler được cấu hình | Nên dùng context thật |
| `@Async` listener có chạy worker thread không? | Context + executor + synchronization test | Có |
| `@TransactionalEventListener` có chạy sau commit không? | Context + transaction manager/resource | Có |
| Listener của parent có nhận child event không? | Test hierarchy hai contexts | Có |

**Nguyên tắc:** dùng test nhỏ cho logic nội bộ; dùng **context integration test** khi điều cần xác minh là Spring infrastructure. Mockito/mock publisher chỉ chứng minh service *đã gọi publish*, không chứng minh Spring **đã phân phối** event cho một listener nào.

Trong ví dụ runnable, chương trình tạo `AnnotationConfigApplicationContext` thật và JUnit tests cũng chủ động tạo context. Không dùng full Spring Boot application, web server, database hay broker.

## 3. ApplicationEventPublisher và kiểu sự kiện

`ApplicationEventPublisher` có hai overload chính:

```java
void publishEvent(ApplicationEvent event);
void publishEvent(Object event);
```

Đây là **trích yếu API**, không phải một lớp Java đầy đủ.

**Kiểu cổ điển:** kế thừa `ApplicationEvent`:

```java
public final class OrderSubmittedEvent extends ApplicationEvent {
    private final String orderId;

    public OrderSubmittedEvent(Object source, String orderId) {
        super(source);
        this.orderId = orderId;
    }

    public String orderId() {
        return orderId;
    }
}
```

Đây là class fragment cần import `org.springframework.context.ApplicationEvent`. Spring giữ `source` là object do producer truyền, không tự gắn request Locale vào `source`.

**Kiểu object payload:** kể từ Spring Framework 4.2, có thể publish một Java object không kế thừa `ApplicationEvent`:

```java
public record OrderSubmitted(String orderId, Locale locale) {
}
```

Đây là record fragment cần `java.util.Locale`. Spring sẽ wrap object trong `PayloadApplicationEvent` để dispatch ở infrastructure, còn `@EventListener` có thể khai method parameter bằng chính `OrderSubmitted`.

```java
@EventListener
public void onOrderSubmitted(OrderSubmitted event) {
    // Handle payload object đã được giải kiểu.
}
```

Đây là method fragment ở trong một Spring-managed bean. `@EventListener` không bắt buộc method khai tham số `PayloadApplicationEvent`. Lựa chọn record bất biến có lợi cho unit tests và explicit metadata.

### Event ID và business key không đồng nghĩa

`orderId` xác định đơn hàng; một đơn có thể phát nhiều event khác nhau hoặc được phát lại. Trong hệ thống có retry/dedup, nên có thêm `eventId`/`occurredAt` và rule idempotency rõ. Ví dụ học tập phát mỗi lần submit một event, không tuyên bố at-most-once hoặc exactly-once trong production.

### Không phải mọi object tự trở thành event

Chỉ tạo `new OrderSubmitted(...)` không khiến Spring listener chạy. Cần có lời gọi `publisher.publishEvent(payload)` trên publisher gắn với context, hoặc một code path thực sự gửi event. Test chỉ khởi tạo event và assert fields mà không gọi publisher **không** test infrastructure.

## 4. Cơ chế listener và điều kiện được gọi

Hai phong cách đăng ký listener phổ biến:

**Interface `ApplicationListener<E>`:**

```java
public final class AuditListener
        implements ApplicationListener<OrderSubmittedEvent> {
    @Override
    public void onApplicationEvent(OrderSubmittedEvent event) {
        // Có thể kiểm event.orderId().
    }
}
```

Đây là class fragment cần imports và kiểu event ở mục 3. Nó nhận những events match `OrderSubmittedEvent`.

**Annotation `@EventListener`:**

```java
public final class NoticeListener {
    @EventListener
    public void onOrderSubmitted(OrderSubmitted event) {
        // Có thể kiểm event.orderId() và event.locale().
    }
}
```

Đây là class fragment cần import `EventListener`; chỉ có hiệu lực nếu `NoticeListener` **được Spring tạo/đăng ký làm bean** và context bật annotation event listener infrastructure.

Trong `AnnotationConfigApplicationContext` được tạo theo chuẩn, `EventListenerMethodProcessor` sẽ nhận biết `@EventListener` methods trên managed beans sau quá trình xử lý bean definitions. Khi dùng raw `new NoticeListener()` ở Java unit test, annotation không tự đăng ký method với một event bus.

### Listener registration khác đăng ký bean instance

Một listener method chưa chắc chạy nếu:
- Class không phải bean của context đang publish.
- Bean được đặt trong context khác, không có đường propagation phù hợp.
- `@EventListener` method bị loại bởi điều kiện hoặc type.
- Annotation processing infrastructure không được kích hoạt trong bare `BeanFactory`.
- `@EventListener(condition="...")` false.
- Listener method gắn type không tương thích với payload event.
- Context chưa `refresh()` hoặc đã `close()`.
- Asynchronous task chưa hoàn thành lúc assertion chạy.

Một integration test tốt phải xác nhận **registration và actual dispatch**, không chỉ thấy bean tồn tại bằng `getBean(NoticeListener.class)`.

## 5. Đồng bộ mặc định và giới hạn của assertion sau publish

Với `SimpleApplicationEventMulticaster` mặc định và không cấu hình `@Async`/custom executor, phần lớn application listeners được gọi **đồng bộ** trên thread của publisher. Do đó một test có thể làm:

```java
publisher.publishEvent(new OrderSubmitted("ORD-7", Locale.ENGLISH));
assertEquals(1, log.receipts().size());
```

Đây là JUnit fragment, cần publisher, log và imports. Với **default synchronous model**, assertion ngay sau publish là phù hợp: listener đã chạy trước khi `publishEvent` trả về, trừ khi exception làm publication fail.

Nhưng `ApplicationEventPublisher` là một hand-off contract; không được mặc định mọi ứng dụng dùng sync ở mọi thời điểm. Khi có `@Async`, multicaster executor hoặc custom listener behavior:
- `publishEvent(...)` có thể trả về trước khi handler hoàn thành.
- Assertion ngay sau publish trở nên racy.
- Thread-local context (bao gồm `LocaleContextHolder` trong web scenario) không tự đảm bảo chuyển sang executor.
- Exception từ asynchronous listener thường không propagate về publisher caller như sync listener.
- Một số listener types có execution restrictions riêng, ví dụ transactional listeners.

**Thiết kế test phải phản ánh infrastructure thực.** Ví dụ runnable cố tình không dùng executor hay `@Async` để có kết quả deterministic.

## 6. MessageSource: mã thông điệp, đối số và Locale

`org.springframework.context.MessageSource` là abstraction cốt lõi của i18n. Ba API thường gặp:

```java
String getMessage(String code, Object[] args, Locale locale)
        throws NoSuchMessageException;

String getMessage(String code, Object[] args,
                  String defaultMessage, Locale locale);

String getMessage(MessageSourceResolvable resolvable, Locale locale)
        throws NoSuchMessageException;
```

Đây là **trích yếu chữ ký**, không phải source của interface.

Tham số:
- `code`: key logic như `order.saved`, không phải chuỗi translation.
- `args`: giá trị điền vào placeholders `{0}`, `{1}` ...
- `locale`: đối tượng `Locale` xác định language/country/variant.
- `defaultMessage`: fallback string có kiểm soát nếu không tìm thấy code.
- `MessageSourceResolvable`: một object có thể cung cấp nhiều codes, arguments và default.

Ví dụ:

```java
String text = messages.getMessage(
        "order.saved",
        new Object[]{"ORD-7"},
        Locale.forLanguageTag("vi-VN"));
```

Đây là fragment; `messages` phải là `MessageSource` đã có catalog. Nếu messages_vi.properties chứa `order.saved=Đã lưu đơn {0}` thì kết quả là `Đã lưu đơn ORD-7`.

### Locale là một giá trị, không phải bản dịch

`Locale.forLanguageTag("vi-VN")` biểu diễn tiếng Việt/Viet Nam; `Locale.US` biểu diễn en-US; `Locale.FRANCE` biểu diễn fr-FR. Nó không tự dịch một chuỗi English. Spring chỉ tìm các resource keys trong catalogs đã cấu hình.

### MessageFormat và tham số

`ResourceBundleMessageSource` thường dùng `MessageFormat` khi cần xử lý arguments. Dùng placeholder indexed, không nối chuỗi thủ công khi nội dung thay đổi theo ngôn ngữ:

```properties
order.saved=Saved order {0}
```

Nếu message có dấu nháy đơn `'` trong thông điệp phức tạp, `java.text.MessageFormat` có cú pháp escape đặc biệt (doubled single quote). Cần test literal apostrophe, choice/number/date formatting theo Locale, không chỉ nhìn một chuỗi message tĩnh.

## 7. ResourceBundleMessageSource và cấu trúc file bản dịch

Một cấu hình phổ biến:

```java
ResourceBundleMessageSource source =
        new ResourceBundleMessageSource();
source.setBasename("messages");
source.setDefaultEncoding("UTF-8");
source.setFallbackToSystemLocale(false);
```

Đây là Java fragment cần import `org.springframework.context.support.ResourceBundleMessageSource`. `setBasename("messages")` tìm classpath resource bundles theo convention:

```text
src/main/resources/
  messages.properties
  messages_en.properties
  messages_vi.properties
```

Ví dụ:

**messages.properties — catalog gốc:**

```properties
order.saved=Base: saved order {0}
```

**messages_en.properties — English:**

```properties
order.saved=Saved order {0}
```

**messages_vi.properties — tiếng Việt:**

```properties
order.saved=Đã lưu đơn {0}
```

Với `vi-VN`, resolver thử candidate resource theo ngôn ngữ/vùng và chọn `messages_vi.properties` nếu không có `messages_vi_VN.properties`. Với `en-US`, nó có thể chọn `messages_en.properties`. Với `fr-FR` không có bundle `fr`, vì `fallbackToSystemLocale(false)`, nó chọn `messages.properties` thay vì tự dựa vào locale của máy đang chạy.

### Tại sao phải disable system Locale fallback khi test?

Nếu máy build/CI có default Locale tiếng Việt hoặc English, fallback mặc định của ResourceBundle có thể chọn bundle khác trước base bundle. Tests khi đó pass trên một máy nhưng fail trên máy khác. `setFallbackToSystemLocale(false)` giúp test `fr-FR` → base properties **độc lập system locale**.

### UTF-8 trong resource file

Lưu `messages_vi.properties` bằng UTF-8 và khai `setDefaultEncoding("UTF-8")`. Nếu editor lưu sai charset, chữ “Đã” có thể thành mojibake; đó là lỗi encoding, không phải sai Locale. Đừng test bằng cách strip dấu tiếng Việt, vì mục đích là kiểm chính xác localized Unicode.

### Base name không có đuôi file

`setBasename("messages")` không nhận `"messages_vi.properties"` như basename khi bạn muốn framework tự resolve Locale. Với nhiều modules, dùng `setBasenames("i18n/core", "i18n/orders")` tùy cấu trúc resources và precedence. Khi keys trùng, precedence của basenames và parent MessageSource cần được kiểm theo contract cụ thể.

## 8. ApplicationContext và tên bean messageSource

Trong `ApplicationContext`, một bean **tên `messageSource`** (với type phù hợp) là message source của context; nếu không tìm thấy, context sử dụng cơ chế fallback/delegation theo nội bộ Spring.

```java
@Bean
public MessageSource messageSource() {
    ResourceBundleMessageSource source =
            new ResourceBundleMessageSource();
    source.setBasename("messages");
    source.setDefaultEncoding("UTF-8");
    source.setFallbackToSystemLocale(false);
    return source;
}
```

Đây là method fragment trong `@Configuration`; cần imports. Tên method `messageSource` tạo bean name mặc định `messageSource`. Đặt nhầm tên như `translations` rồi nghĩ context tự nhận đó là message source đặc biệt là một nguyên nhân test i18n khác kết quả mong đợi.

Khi context có bean đúng tên, có thể test theo hai cách:

```java
MessageSource source = context.getBean(MessageSource.class);
String direct = source.getMessage("order.saved",
        new Object[]{"ORD-7"}, Locale.ENGLISH);

String viaContext = context.getMessage("order.saved",
        new Object[]{"ORD-7"}, Locale.ENGLISH);
```

Đây là fragment và cần context initialized. Với cấu hình demo, hai chuỗi tương đương. Tuy nhiên không suy chúng luôn giống nhau khi hierarchy/parent MessageSource delegation hoặc có nhiều candidates khác cấu hình; phải test đúng context scope mà application sử dụng.

## 9. Dự án chạy được Java 17+ Maven: event payload, listener và Locale

Dự án dùng:
- JDK **17 trở lên**;
- Maven **3.9 trở lên**;
- Spring Framework **6.2.18** qua `spring-context`;
- JUnit Jupiter **5.11.4** cho các integration/unit tests có xác nhận behavior;
- Không dùng Spring Boot, database, web server hay broker.

Chương trình có một `OrderService` được inject `ApplicationEventPublisher`, một `OrderNoticeListener` gắn `@EventListener`, một `EventLog` ghi nhận thông điệp, và `ResourceBundleMessageSource` đọc ba properties files. `App.main` thực sự dựng context, phát ba events và in result. Các unit/integration test ở mục 11 dùng đúng configuration này.

### 9.1. Cấu trúc thư mục

```text
spring-event-locale-tests/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/edu/example/eventlocale/App.java
    │   └── resources/
    │       ├── messages.properties
    │       ├── messages_en.properties
    │       └── messages_vi.properties
    └── test/
        └── java/edu/example/eventlocale/AppTest.java
```

### 9.2. pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>edu.example</groupId>
    <artifactId>spring-event-locale-tests</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.18</version>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.11.4</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.2</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
                <configuration>
                    <mainClass>edu.example.eventlocale.App</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 9.3. src/main/resources/messages.properties

```properties
order.saved=Base: saved order {0}
```

### 9.4. src/main/resources/messages_en.properties

```properties
order.saved=Saved order {0}
```

### 9.5. src/main/resources/messages_vi.properties

```properties
order.saved=Đã lưu đơn {0}
```

Đảm bảo cả ba files ở `src/main/resources`, không đặt ở `src/test/resources` nếu muốn `exec:java` chạy bản main. Maven tự copy resources vào runtime classpath khi compile/package theo conventions.

### 9.6. src/main/java/edu/example/eventlocale/App.java

```java
package edu.example.eventlocale;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.context.support.ResourceBundleMessageSource;

public final class App {

    public record OrderSubmitted(String orderId, Locale locale) {
    }

    public record RenderedNotice(
            String orderId, Locale locale, String message) {
    }

    public static final class EventLog {
        private final List<RenderedNotice> received =
                new ArrayList<>();

        public void record(RenderedNotice value) {
            received.add(value);
        }

        public List<RenderedNotice> receipts() {
            return List.copyOf(received);
        }
    }

    public static final class OrderService {
        private final ApplicationEventPublisher publisher;

        public OrderService(ApplicationEventPublisher publisher) {
            this.publisher = Objects.requireNonNull(publisher);
        }

        public void submit(String orderId, Locale locale) {
            if (orderId == null || orderId.isBlank()) {
                throw new IllegalArgumentException(
                        "Order ID must not be blank");
            }
            Objects.requireNonNull(locale, "locale");
            publisher.publishEvent(new OrderSubmitted(orderId, locale));
        }
    }

    public static final class OrderNoticeListener {
        private final MessageSource messages;
        private final EventLog log;

        public OrderNoticeListener(MessageSource messages, EventLog log) {
            this.messages = Objects.requireNonNull(messages);
            this.log = Objects.requireNonNull(log);
        }

        @EventListener
        public void onOrderSubmitted(OrderSubmitted event) {
            String text = messages.getMessage(
                    "order.saved",
                    new Object[]{event.orderId()},
                    event.locale());
            log.record(new RenderedNotice(
                    event.orderId(), event.locale(), text));
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class Config {
        @Bean
        public MessageSource messageSource() {
            ResourceBundleMessageSource source =
                    new ResourceBundleMessageSource();
            source.setBasename("messages");
            source.setDefaultEncoding("UTF-8");
            source.setFallbackToSystemLocale(false);
            return source;
        }

        @Bean
        public EventLog eventLog() {
            return new EventLog();
        }

        @Bean
        public OrderService orderService(
                ApplicationEventPublisher publisher) {
            return new OrderService(publisher);
        }

        @Bean
        public OrderNoticeListener orderNoticeListener(
                MessageSource messageSource, EventLog log) {
            return new OrderNoticeListener(messageSource, log);
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(Config.class)) {

            OrderService service = context.getBean(OrderService.class);
            EventLog log = context.getBean(EventLog.class);

            service.submit("ORD-7", Locale.forLanguageTag("vi-VN"));
            service.submit("ORD-8", Locale.US);
            service.submit("ORD-9", Locale.FRANCE);

            System.out.println("== event and locale ==");
            System.out.println("received=" + log.receipts().size());
            for (RenderedNotice notice : log.receipts()) {
                System.out.println(notice.locale().toLanguageTag()
                        + " -> " + notice.message());
            }

            String fallback = context.getMessage(
                    "unknown.message", null,
                    "Fallback text", Locale.ENGLISH);
            System.out.println("missing-default=" + fallback);

            boolean missingThrows = false;
            try {
                context.getMessage(
                        "unknown.message", null, Locale.ENGLISH);
            } catch (NoSuchMessageException expected) {
                missingThrows = true;
            }
            System.out.println("missing-throws=" + missingThrows);

            int before = log.receipts().size();
            try {
                service.submit(" ", Locale.ENGLISH);
                throw new AssertionError(
                        "Expected invalid order ID rejection");
            } catch (IllegalArgumentException expected) {
                // Validation failed before event publication.
            }
            System.out.println("invalid-keeps-count="
                    + (log.receipts().size() == before));

            if (log.receipts().size() != 3
                    || !missingThrows
                    || !fallback.equals("Fallback text")) {
                throw new AssertionError("Unexpected event/i18n result");
            }
            System.out.println("RESULT PASS");
        }
    }
}
```

**Mọi nested type cần public** như trong ví dụ để Maven/JUnit và Spring có thể truy cập type qua classpath. `OrderNoticeListener` là một Spring bean do `@Bean` factory method tạo, chứ không phải một object được tạo bằng `new` rồi đứng ngoài container.

### 9.7. Build, test và chạy ứng dụng

Từ thư mục chứa `pom.xml`:

```bash
mvn -q test
mvn -q compile exec:java
```

Lệnh đầu chạy JUnit tests ở mục 11; lệnh sau chạy `App.main`. Khi dependencies chưa có trong local Maven cache, Maven cần internet để tải Spring/JUnit artifacts. Build không yêu cầu server/database hoặc environment variables bí mật.

**Các dòng do `App.main` in, không tính Maven/Spring logging:**

```text
== event and locale ==
received=3
vi-VN -> Đã lưu đơn ORD-7
en-US -> Saved order ORD-8
fr-FR -> Base: saved order ORD-9
missing-default=Fallback text
missing-throws=true
invalid-keeps-count=true
RESULT PASS
```

Đây là stdout theo đúng `MessageSource` config và resource files đã cung cấp: hệ thống khác nhau về default Locale không thay đổi fallback sang base vì `setFallbackToSystemLocale(false)`. Các log do Spring/Maven có thể xuất hiện riêng và không nên là mục tiêu của assertion nguyên văn.

## 10. Giải thích luồng chạy của ví dụ

### 10.1. Context initialization

`new AnnotationConfigApplicationContext(Config.class)`:
1. Đăng ký `@Configuration` và thực hiện `refresh()`.
2. Khởi tạo bean `messageSource`, `eventLog`, `orderService`, `orderNoticeListener`.
3. Giải `ApplicationEventPublisher` chính bằng context.
4. Đăng ký method `OrderNoticeListener.onOrderSubmitted` thành listener adapter nhờ `EventListenerMethodProcessor`.
5. Chuẩn bị multicaster mặc định đồng bộ để nhận custom payload events.

Không có `@ComponentScan` và không cần. `@Bean` methods explicit chỉ tạo đúng các bean được khai.

### 10.2. Phát payload event

`service.submit("ORD-7",viVN)` tạo một immutable record và gọi `publishEvent(Object)`. Spring wraps object trong `PayloadApplicationEvent` nội bộ và chọn listener theo payload type `OrderSubmitted`. Method `onOrderSubmitted(OrderSubmitted)` được gọi bởi adapter.

Listener **không được gọi nhờ tên method**, mà vì annotation infrastructure đọc method signature và đăng ký event listener cho đúng type. Test nên kiểm chính payload đã đến, không phụ thuộc tên method nào được gọi trong implementation.

### 10.3. Từ Locale tới message

Listener dùng `event.locale()` chứ không dùng `Locale.getDefault()` hay `LocaleContextHolder.getLocale()`. Đối với ba events:
- `vi-VN` -> resource `messages_vi.properties`.
- `en-US` -> resource `messages_en.properties`.
- `fr-FR` -> base resource `messages.properties`.

Mỗi message dùng cùng `{0}` nhưng kết quả khác do dictionary. Đây là **message localization**, không phải máy tự dịch từ tiếng Anh qua tiếng Việt.

### 10.4. MessageSource.getMessage hai nhánh missing code

Overload có `defaultMessage`:

```java
context.getMessage(
        "unknown.message", null,
        "Fallback text", Locale.ENGLISH);
```

trả `Fallback text` khi key không có.

Overload **không** có default:

```java
context.getMessage(
        "unknown.message", null,
        Locale.ENGLISH);
```

ném `NoSuchMessageException` trong cấu hình demo. Hai nhánh này cần assertion riêng; một test “có trả ra chuỗi” có thể bỏ lọt missing translation do fallback sai.

### 10.5. Validation trước khi publish

`OrderService.submit(" ",Locale.ENGLISH)` ném `IllegalArgumentException` trước `publishEvent`. Vì publisher không được gọi, `EventLog.receipts()` vẫn có 3 entries. Một test chỉ kiểm exception là chưa đủ nếu yêu cầu là **không phát event khi input vô hiệu**.

### 10.6. EventLog trong ví dụ chỉ dùng cho synchronous tests

`ArrayList` đủ cho synchronous `SimpleApplicationEventMulticaster` trên cùng thread. Không dùng shared mutable `ArrayList` này trong async/multithread production listener. Nếu thêm executor, dùng thread-safe recorder và bounded synchronization, hoặc test event sink có concurrency contract thích hợp.

## 11. JUnit 5: kiểm thật cả event dispatch và i18n

Vì đã có Maven dependency `junit-jupiter` và Surefire, tạo file sau:

**src/test/java/edu/example/eventlocale/AppTest.java**

```java
package edu.example.eventlocale;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

final class AppTest {

    @Test
    void publishesPayloadAndListenerUsesEachLocale() {
        try (AnnotationConfigApplicationContext ctx =
                new AnnotationConfigApplicationContext(
                        App.Config.class)) {

            App.OrderService service =
                    ctx.getBean(App.OrderService.class);
            App.EventLog log = ctx.getBean(App.EventLog.class);

            service.submit("VI-1", Locale.forLanguageTag("vi-VN"));
            service.submit("EN-2", Locale.US);
            service.submit("FR-3", Locale.FRANCE);

            assertEquals(List.of(
                    new App.RenderedNotice(
                            "VI-1",
                            Locale.forLanguageTag("vi-VN"),
                            "Đã lưu đơn VI-1"),
                    new App.RenderedNotice(
                            "EN-2",
                            Locale.US,
                            "Saved order EN-2"),
                    new App.RenderedNotice(
                            "FR-3",
                            Locale.FRANCE,
                            "Base: saved order FR-3")),
                    log.receipts());
        }
    }

    @Test
    void rejectsInvalidInputWithoutDeliveringEvents() {
        try (AnnotationConfigApplicationContext ctx =
                new AnnotationConfigApplicationContext(
                        App.Config.class)) {

            App.OrderService service =
                    ctx.getBean(App.OrderService.class);
            App.EventLog log = ctx.getBean(App.EventLog.class);

            assertThrows(IllegalArgumentException.class,
                    () -> service.submit(" ", Locale.ENGLISH));
            assertThrows(NullPointerException.class,
                    () -> service.submit("ORD-1", null));

            assertTrue(log.receipts().isEmpty());
        }
    }

    @Test
    void messageKeysUseDefaultOrThrowWhenMissing() {
        try (AnnotationConfigApplicationContext ctx =
                new AnnotationConfigApplicationContext(
                        App.Config.class)) {

            assertEquals("Saved order X-1",
                    ctx.getMessage("order.saved",
                            new Object[]{"X-1"}, Locale.US));
            assertEquals("Fallback text",
                    ctx.getMessage("missing.key", null,
                            "Fallback text", Locale.ENGLISH));
            assertThrows(NoSuchMessageException.class,
                    () -> ctx.getMessage(
                            "missing.key", null, Locale.ENGLISH));
        }
    }

    @Test
    void ignoresPayloadsOfDifferentTypes() {
        try (AnnotationConfigApplicationContext ctx =
                new AnnotationConfigApplicationContext(
                        App.Config.class)) {

            ctx.publishEvent(new OtherEvent("OTHER"));
            assertTrue(
                    ctx.getBean(App.EventLog.class).receipts().isEmpty());
        }
    }

    private record OtherEvent(String code) {
    }

    @Test
    void publisherUnitTestChecksRequestNotSpringDelivery() {
        List<Object> sent = new ArrayList<>();

        ApplicationEventPublisher fake =
                new ApplicationEventPublisher() {
                    @Override
                    public void publishEvent(ApplicationEvent event) {
                        sent.add(event);
                    }

                    @Override
                    public void publishEvent(Object event) {
                        sent.add(event);
                    }
                };

        App.OrderService service = new App.OrderService(fake);
        service.submit("UNIT-1", Locale.ENGLISH);

        assertEquals(
                List.of(new App.OrderSubmitted(
                        "UNIT-1", Locale.ENGLISH)),
                sent);
    }
}
```

Có **5 method `@Test`** độc lập:
1. Kiểm event thật đi qua listener và đúng ba translation.
2. Kiểm dữ liệu sai không phát event.
3. Kiểm message fallback/no-such-message.
4. Kiểm khác payload type không kích hoạt listener mục tiêu.
5. Kiểm unit test publisher fake **chỉ chứng minh call tới publish**, không nhầm là Spring dispatch.

Lệnh:

```bash
mvn -q test
```

Với resources và classes đúng như cung cấp, kỳ vọng **5 tests chạy, 0 failures, 0 errors, 0 skipped** trong Surefire results. `mvn -q` có thể không in một summary ra console; không nên chế ra một khối stdout của Surefire. File report được tạo bởi Surefire ở `target/surefire-reports` theo cấu hình Maven thông thường.

### Tại sao không dùng @SpringBootTest?

Ví dụ chỉ cần container nhỏ chứa ba/four bean nghiệp vụ và message source, nên `AnnotationConfigApplicationContext` đủ để test Spring infrastructure. `@SpringBootTest` sẽ mở thêm nhiều Boot auto-configurations không liên quan. Trong ứng dụng thực, có thể dùng `@SpringJUnitConfig(Config.class)`/`@ContextConfiguration` để tích hợp Spring TestContext cache, nhưng **không bắt buộc** cho bài này.

### Test không phụ thuộc system Locale

JUnit tests đặt Locale tường minh trong event và getMessage. Không gọi `Locale.setDefault(...)`, vốn là một mutable global state dễ làm những tests chạy song song ảnh hưởng nhau. Nếu application thực cần test LocaleContextHolder, tách riêng test và reset thread-local sau mỗi case.

## 12. Viết assertion đủ mạnh cho event

Chỉ kiểm:

```java
assertTrue(log.receipts().size() > 0);
```

là quá yếu. Có thể pass dù:
- Event bị gửi hai lần.
- Listener format sai ngôn ngữ.
- Message argument bị mất.
- Listener ghi nhầm event ID.
- Một event bị bỏ sót.
- Event sai type cũng kích hoạt listener.

Ưu tiên:

```java
assertEquals(List.of(
        new App.RenderedNotice(
                "VI-1", Locale.forLanguageTag("vi-VN"),
                "Đã lưu đơn VI-1")),
        log.receipts());
```

Đây là JUnit fragment dùng types từ demo; nó xác nhận số lượng **và** từng field chính xác. Record có value equality giúp assertion rõ ràng và chẩn đoán chênh lệch khi thất bại.

### Có cần kiểm thứ tự event không?

Nếu default synchronous publisher gọi ba events tuần tự, log order như ví dụ là hợp lý. Nhưng nếu application dùng async multicast hay nhiều publishers/threads, **không tự giả định thứ tự xử lý toàn cục**. Khi business contract chỉ cần tập events, so theo IDs/sets; khi order thật sự quan trọng, cần đảm bảo ordering mechanism, test dựa trên contract của executor/broker chứ không dựa may mắn.

### Test số lần gọi với spy listener

Một `RecordingListener` có thể ghi ID, Locale và count. Nó giúp kiểm:
- Mỗi event match kích hoạt chính xác một lần theo registration mong muốn.
- Event type khác không được nhận.
- Khi listener bị đăng ký trùng trong parent/child, count có thể tăng — test cần có ownership policy.

Không cần verify Spring đã gọi internal `SimpleApplicationEventMulticaster.multicastEvent` mấy lần; đó là implementation detail. Quan sát **hành vi listener** là mục tiêu chính.

## 13. Không nhận sự kiện và lỗi dễ bị bỏ qua

Các negative cases quan trọng:

**Không hợp lệ trước khi publish:**
- `orderId == null` hoặc blank → từ chối, không phát.
- `locale == null` → từ chối, không phát.
- Business invariant fail → không gửi event “thành công” ngoài ý muốn.

**Không match type:**
- `OrderCancelled` không được `onOrderSubmitted(OrderSubmitted)` xử lý trừ khi listener intentionally nhận broader type.
- `PayloadApplicationEvent<OrderSubmitted>` được adapter unwrap đúng; test khác payload để phát hiện listener quá rộng.

**Listener lỗi:**
- Khi `MessageSource` thiếu key `order.saved` cho tất cả candidate bundles, non-default overload ném `NoSuchMessageException`.
- Trong multicast đồng bộ và không có ErrorHandler suppress, exception từ listener có thể propagate đến publisher. Việc service `submit` ném ở cuối không có nghĩa business transaction/side effects trước publish được tự rollback.
- Với async, exception và thread behavior khác; không dùng `assertThrows(...)` ở publisher để test listener async lỗi.

**Listener không đăng ký:**
- Bean chưa nằm trong context.
- `@EventListener` method ở object do code tự `new`, không bean.
- Method hoặc bean bị lazy mà không tạo/đăng ký theo lifecycle cần thiết.
- `@EventListener(condition)`/generic payload type không match.
- Context parent/child không ở nơi publish theo kỳ vọng.

**Các trường hợp message:**
- Thiếu `messages_vi.properties` khiến vi-VN fallback sang base thay vì Vietnamese.
- Thiếu `messages.properties` khiến fr-FR không có translation.
- Sai basename/classpath location.
- Dùng message code sai case/spelling.
- Passing null hoặc wrong Locale.
- Sai encoding làm lỗi Unicode.
- `MessageFormat` xử lý apostrophe/number/date không như dự đoán.

Nên tạo testcase riêng cho mỗi failure mode quan trọng; một test kiểm “context khởi động” không thể bao quát chúng.

## 14. LocaleContextHolder không phải metadata mặc định của event

Trong ứng dụng web, `LocaleContextHolder` có thể lưu Locale theo thread/request. Ví dụ:

```java
Locale current = LocaleContextHolder.getLocale();
```

Đây là fragment, cần import `org.springframework.context.i18n.LocaleContextHolder`. Đừng suy một event object có field `locale` chỉ vì publisher chạy trên thread có LocaleContextHolder.

Có hai cách thiết kế:

**A — Locale nằm ngay trong event** (như demo):

```java
publisher.publishEvent(
        new OrderSubmitted("ORD-7",
                Locale.forLanguageTag("vi-VN")));
```

Ưu điểm: rõ hợp đồng, dễ serialize/test, không lệ thuộc ThreadLocal. Nhược điểm: cần chọn đúng Locale ở entrypoint và tránh đưa thông tin request nhạy cảm vào payload không cần thiết.

**B — Listener đọc LocaleContextHolder:**

```java
@EventListener
public void onOrderSubmitted(OrderSubmitted event) {
    Locale locale = LocaleContextHolder.getLocale();
    // ...
}
```

Đây là method fragment, khác với demo. Cách B chỉ đáng tin khi **event dispatch đồng bộ trên đúng thread** và context đã set Locale. Nếu chuyển sang async executor, ThreadLocal không tự được chuyển, nên listener có thể nhận Locale mặc định/khác request. Đối với messages gửi sau commit, queue, scheduler hoặc background workers, **đưa explicit locale/language preference vào event** thường phù hợp hơn.

### Cleanup test khi dùng LocaleContextHolder

Nếu integration test phải set locale:

```java
LocaleContextHolder.setLocale(Locale.ENGLISH);
try {
    // Thực hiện và assert code path đọc LocaleContextHolder.
} finally {
    LocaleContextHolder.resetLocaleContext();
}
```

Đây là Java fragment. Không bỏ `finally` vì JUnit test runner có thể tái sử dụng thread; Locale đã set từ test A có thể ảnh hưởng test B. Demo chính cố tránh hẳn mutable thread-local state.

## 15. Fallback, defaultLocale và thông điệp chưa dịch

Phải phân biệt bốn tình huống:

1. **Có resource candidate chính xác** (ví dụ `messages_vi_VN.properties`): Spring ưu tiên key trong catalog phù hợp.
2. **Thiếu country-specific bundle nhưng có language-level** (`messages_vi.properties`): dùng bản dịch language-level.
3. **Thiếu language bundle và disable system fallback**: thử base `messages.properties`.
4. **Thiếu key trong mọi candidate**: overload có `defaultMessage` trả default; overload bắt buộc ném `NoSuchMessageException` theo setup của demo.

Nếu bạn bật fallback system locale hoặc đặt `defaultLocale`, đường tìm kiếm có thể khác. Đó là **policy cấu hình** cần test; không nói mọi ứng dụng luôn fallback về base trong mọi trường hợp.

### Thiếu key không nên luôn im lặng

Trong app production, nhiều UI messages có thể dùng default text để trải nghiệm người dùng không bị crash. Nhưng với translation coverage ở CI, đôi khi bạn nên test strict variant để phát hiện thiếu key.

```java
assertThrows(NoSuchMessageException.class,
        () -> ctx.getMessage(
                "unknown.message", null, Locale.ENGLISH));
```

Đây là JUnit fragment. Nếu dùng `setUseCodeAsDefaultMessage(true)` hoặc một custom MessageSource, behavior thay đổi và test phải phản ánh intended policy; không vô thức chấp nhận key string trả về như bản dịch hợp lệ.

### Pluralization và số/tiền/timestamp

Cụm “1 order” và “2 orders” không phải lúc nào cũng chỉ thay số: nhiều ngôn ngữ có plural forms phức tạp. `MessageFormat` hỗ trợ một số patterns như `choice`/number/date, nhưng i18n sản phẩm phức tạp có thể cần ICU MessageFormat hoặc localization layer chuyên dụng. Khi test, dùng Locale và input values cụ thể, tránh so một bản dịch có số format tùy default Locale.

## 16. TransactionalEventListener: publish không đồng nghĩa callback tức thì

Nếu method gắn `@TransactionalEventListener` thay vì `@EventListener`:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onCommitted(OrderSubmitted event) {
    // Callback theo transaction synchronization.
}
```

Đây là **method fragment**, cần imports `TransactionalEventListener`, `TransactionPhase` và một bean quản lý bởi Spring. Khác với `@EventListener` thông thường, listener này gắn với **transaction phase**.

Những điểm khi test:
- Không có active transaction: mặc định `fallbackExecution=false` khiến listener không được gọi.
- Nếu set `fallbackExecution=true`, có thể cho callback chạy khi không có transaction, nhưng semantics không giống “sau commit” thật.
- `AFTER_COMMIT` chạy sau successful commit; `AFTER_ROLLBACK` sau rollback; `BEFORE_COMMIT` trước commit; `AFTER_COMPLETION` sau completion.
- Callback được đăng ký theo transaction context thích hợp; resource availability và thread/reactive context tùy manager.
- Unit test với fake publisher không thể xác minh transaction synchronization.
- Test cần context, transaction manager, một resource transaction (JDBC/H2 hoặc DB khác) và assertions trước/sau commit.

**Không thay `@EventListener` của demo bằng `@TransactionalEventListener` rồi kỳ vọng 3 receipts xuất hiện khi không có transaction**. Đây là hai hợp đồng khác nhau.

### Không có durability mặc định

Dù callback chạy AFTER_COMMIT, process vẫn có thể crash sau DB commit trước khi callback hoàn tất. Spring in-process events **không phải durable messaging** và không cung cấp exactly-once. Nếu thông báo quan trọng phải gửi chắc chắn, cần transactional outbox/queue, idempotency và retry theo thiết kế riêng.

## 17. Exception behavior và trách nhiệm của publisher

Trong default synchronous multicast:
1. Producer publish event.
2. Listener được gọi trên publisher thread.
3. Nếu listener ném exception chưa được handler xử lý, `publishEvent` có thể ném lại.
4. Producer quan sát exception; các listeners sau có thể chưa được chạy.
5. Các side effects đã xảy ra không tự động undo.

Ví dụ về failure injection trong listener:

```java
@EventListener
public void onOrderSubmitted(OrderSubmitted event) {
    throw new IllegalStateException("Listener intentionally failed");
}
```

Đây là **method fragment** để kiểm error propagation. Test với dedicated context riêng (đừng thêm listener throw vào ví dụ chính nếu muốn stdout deterministic). Khi assertion exception:

```java
assertThrows(IllegalStateException.class,
        () -> service.submit("FAIL-1", Locale.ENGLISH));
```

Đây là JUnit fragment áp dụng **chỉ nếu** dedicated listener đúng type, multicaster sync và ErrorHandler không suppress. Không chạy test này trong default demo vì listener mặc định của demo không throw.

### Nếu có nhiều listeners?

Thứ tự local listeners có thể chịu `@Order`/`Ordered`, nhưng không dùng thứ tự registration ngẫu nhiên làm test contract. Nếu một listener throw sớm, listener khác có thể không chạy trong default sync multicaster. Nếu cấu hình `SimpleApplicationEventMulticaster.setErrorHandler(...)`, exception có thể được log/suppress và publication tiếp tục. Test cần cố định settings và assert kết quả mà application định nghĩa.

### “Event đã publish” là một trạng thái khác “event đã handle”

Một fake publisher kiểm *attempt to publish*. Một EventLog in-memory từ listener kiểm *handler invocation*. Neither proves external notification delivered, database committed hay consumer trên process khác đã nhận. Ghi nhận metrics/logs phân biệt `published`, `handled`, `failed` và `delivered` ở hệ thống ngoài.

## 18. Khi listener bất đồng bộ: cách kiểm không flaky

Nếu hệ thống cấu hình `@Async` hoặc executor trong multicaster, tránh:

```java
service.submit("A-1", Locale.ENGLISH);
assertEquals(1, log.receipts().size()); // Có thể chạy quá sớm!
```

Đây là anti-pattern trong async context (vẫn hợp lệ cho sync demo). Một chiến lược test thích hợp sử dụng bounded synchronization:

```java
CountDownLatch handled = new CountDownLatch(1);
ExecutorService pool = Executors.newSingleThreadExecutor();
try {
    pool.submit(() -> {
        // Thực hiện async handler mà test kiểm soát.
        handled.countDown();
    });
    assertTrue(handled.await(3, TimeUnit.SECONDS));
} finally {
    pool.shutdownNow();
}
```

Đây là **test technique fragment**, cần `java.util.concurrent` imports và logic handler thực nếu muốn kiểm cả Spring dispatch. Trong integration test thật, latch phải được **listener Spring được đăng ký** countDown, chứ không phải thread nhân tạo riêng như fragment chỉ minh họa kỹ thuật.

Không dùng `Thread.sleep(200)` và cầu mong thread đã xử lý xong. Với async:
- Recorder cần thread-safe collections (ví dụ `ConcurrentLinkedQueue`).
- Assertions sau latch/future completion.
- Có timeout rõ để không treo CI.
- Executor được shutdown/cleanup.
- Kiểm propagation Locale theo explicit event field hoặc TaskDecorator nếu thật sự dùng ThreadLocal.
- Kiểm exception handling và rejected tasks theo executor configuration.

Cần lưu ý `ApplicationEventPublisher.publishEvent` không bảo đảm completion timing chung cho mọi multicaster; chỉ dựa vào behavior của configuration đang test.

## 19. Parent–child contexts và event propagation

Nếu ứng dụng có root và child `ApplicationContext`, event được phát từ child có thể được chuyển lên parent trong implementation `AbstractApplicationContext`; event phát ở parent **không tự broadcast xuống children**.

```text
Root context
  @EventListener auditRoot
       ^
       | child event propagates upward
       |
Child context
  @EventListener noticeChild
       |
       +-- child.publishEvent(OrderSubmitted)
```

Các vấn đề khi test:
- Root listener có thể nhận event của child và event của root; nếu không filter, số lần xử lý vượt mong đợi.
- Một listener object được đăng ký cả ở root và child có thể bị gọi hai lần cho cùng child event.
- Hai contexts có thể có `messageSource` riêng; Spring có thể áp delegation từ child sang parent khi message code thiếu trong child source.
- Locale trong event vẫn là explicit field; hierarchy không tự đổi `vi-VN` thành system locale.
- ContextRefreshedEvent/ContextClosedEvent từ child cũng đi lên parent, nhưng đó là lifecycle events, không phải `OrderSubmitted` payload event.

Nếu cần test hierarchy, tạo parent/child contexts thật, cấu hình listener owners rõ, assert số lượt nhận ở từng cấp. Ví dụ mục 9 cố ý chỉ có **một context** để phân biệt chính xác i18n với vấn đề propagation nhiều cấp.

### Lưu ý MessageSource hierarchy

Một `HierarchicalMessageSource` ở child có thể nhận parent message source nếu cấu hình/khởi tạo context phù hợp. Vì vậy thiếu key ở child đôi khi **không** ném `NoSuchMessageException` nếu parent có bản dịch. Muốn test strict missing key, phải biết toàn bộ hierarchy/source chain, không chỉ kiểm file của một module.

## 20. Khi nào nên dùng StaticMessageSource trong unit tests?

`StaticMessageSource` thuộc Spring và có thể chứa translations trong memory:

```java
StaticMessageSource messages = new StaticMessageSource();
messages.addMessage("order.saved",
        Locale.forLanguageTag("vi-VN"),
        "Đã lưu đơn {0}");
```

Đây là fragment cần import `org.springframework.context.support.StaticMessageSource`. Có thể tự tạo `OrderNoticeListener(messages,log)`, rồi **gọi method trực tiếp**:

```java
OrderNoticeListener listener =
        new OrderNoticeListener(messages, log);
listener.onOrderSubmitted(
        new OrderSubmitted("ORD-7",
                Locale.forLanguageTag("vi-VN")));
```

Đây là fragment với types từ demo và `log` đã tạo. Nó kiểm **logic rendering của listener** và không cần mở context.

Nhưng không chứng minh:
- Spring đã đăng ký `@EventListener`.
- `publishEvent` dispatch đúng.
- Config `ResourceBundleMessageSource` đã load properties đúng.
- Parent context message source delegation hoạt động.

Vậy nên `StaticMessageSource` phù hợp cho test một handler logic nhỏ, và integration test mục 11 cần giữ để xác minh **Spring event/messaging infrastructure**.

### Dùng fake MessageSource thuần Java

Bạn cũng có thể mock `MessageSource` interface để trả text cố định. Nhưng với localization rule, một `StaticMessageSource` thật thường đơn giản và ít test code hơn. Dùng Mockito nếu cần ép lỗi `NoSuchMessageException` hoặc kiểm precise arguments truyền tới resolver.

## 21. Chẩn đoán lỗi phổ biến

| Triệu chứng | Nguyên nhân thường gặp | Cách kiểm/sửa |
| --- | --- | --- |
| Test publisher pass nhưng listener không chạy | Chỉ dùng fake publisher | Context integration test với listener bean thật |
| `@EventListener` không được gọi | Listener object không thuộc context | Đăng ký `@Bean`/`@Component` đúng |
| Event record không match handler | Type mismatch hoặc payload wrapping/generics | Kiểm method signature, payload class |
| Test count bằng 0 sau publish | Async callback chưa hoàn thành hoặc không register | Xác định multicaster, latch, listener registry |
| Event count bằng 2 thay vì 1 | Duplicate listener/parent-child propagation | Kiểm context hierarchy/registration |
| Listener ném lỗi khiến service submit fail | Sync error propagation | Test error handler/side effects rõ |
| Listener @TransactionalEventListener không chạy | Không có transaction, fallbackExecution false | Transaction-aware integration test |
| @Async listener mất Locale | LocaleContextHolder không tự propagate | Đưa Locale vào payload/TaskDecorator |
| vi-VN trả English | Thiếu messages_vi, sai basename/fallback | Kiểm classpath properties và Locale |
| fr-FR trả tiếng Việt trên CI | System Locale fallback đang bật | `setFallbackToSystemLocale(false)` |
| Chữ Việt hiển thị lỗi | File encoding sai | UTF-8 end-to-end |
| `NoSuchMessageException` khi có key trong source | Resource không ở classpath, sai key/name | Kiểm runtime classpath/basename |
| Key được trả nguyên thay vì translation | `useCodeAsDefaultMessage` hoặc fallback | Kiểm MessageSource configuration |
| `messages_en_US` thiếu | Có thể fallback sang messages_en | Test expected locale candidate |
| `getMessage` trả fallback mặc dù cần strict key | Dùng overload có defaultMessage | Dùng overload 3 tham số strict |
| Code có `{0}` nhưng không thay thành ID | Sai argument array hoặc format syntax | Assert full localized string |
| Ngày/tiền format khác khi chạy trên CI | Locale/timezone/MessageFormat khác | Explicit Locale, fixed data và format policy |
| Integration test chạy chậm bất thường | Mở full Boot context không cần thiết | Context nhỏ bằng `AnnotationConfigApplicationContext` |
| Chỉ assert bean tồn tại | Chưa thật sự publish event | Thực thi producer và assert listener state |
| Event từ root không tới child | Không có downward propagation mặc định | Đúng routing contract, thiết kế khác nếu cần |
| Fakes shared giữa tests | State từ test trước bị giữ | Context/log mới mỗi test |
| Unit test event pass nhưng broker fail | In-process event khác broker | Contract/integration test external messaging |
| Object payload được wrap nhưng cast sai | Listener trực tiếp cast ApplicationEvent | Dùng `@EventListener` typed payload hoặc PayloadApplicationEvent đúng |
| Test output quá phụ thuộc Maven logs | Log config/platform thay đổi | Assert business stdout và JUnit behavior |

### Quy trình debug ngắn

1. Kiểm producer thực có gọi `publishEvent` với đúng type không.
2. Kiểm context đang dùng và listener bean owner.
3. Kiểm `@EventListener` được đăng ký sau refresh.
4. Kiểm multicaster synchronous/async, ordering và ErrorHandler.
5. Kiểm exact Locale field/request context.
6. Kiểm `messageSource` bean name, basename và resource classpath.
7. Kiểm UTF-8, locale fallback và `getMessage` overload.
8. Kiểm data/value: IDs, arguments, key names và format strings.
9. Assert cả số event, bản dịch và negative paths.
10. Nếu listener transaction/async/hierarchy, chạy test đúng infrastructure thay vì chỉ dùng fake.

## 22. Checklist kiểm thử và câu hỏi tự đánh giá

### Checklist

- [ ] Phân biệt publisher unit test và event-dispatch integration test.
- [ ] Có Spring-managed listener bean trong context kiểm thử.
- [ ] Test một object payload event hoặc ApplicationEvent type thực.
- [ ] Kiểm @EventListener nhận đúng event type, không nhận event khác.
- [ ] Kiểm số lượng events/handler calls, không chỉ kiểm count > 0.
- [ ] Kiểm event ID/args/Locale fields và text đã format.
- [ ] Với sync test, dựa vào documented default multicaster; với async, chờ bằng latch/future.
- [ ] Không dùng Thread.sleep cố định để test executor.
- [ ] Không giả định LocaleContextHolder tự đi theo event payload.
- [ ] Ưu tiên explicit Locale cho notification chạy background/async.
- [ ] Cấu hình MessageSource basename và bean name chính xác.
- [ ] Lưu resource bundles bằng UTF-8.
- [ ] Test vi-VN, en-US và một locale không có translation.
- [ ] Test fallback system Locale không gây flaky CI.
- [ ] Test có và không có defaultMessage khi key thiếu.
- [ ] Test strict `NoSuchMessageException` theo intended policy.
- [ ] Test `{0}` substitution và escaped apostrophes/number/date khi dùng.
- [ ] Mỗi JUnit test tạo EventLog/context state riêng hoặc reset chắc chắn.
- [ ] Không gộp @TransactionalEventListener vào test no-transaction và suy nó hoạt động như @EventListener.
- [ ] Nếu giao dịch là yêu cầu, dùng transaction manager/resource thật.
- [ ] Nếu parent-child contexts, kiểm propagation/duplicate handling/message source hierarchy.
- [ ] Không coi in-process events là durable messaging hoặc exactly-once.
- [ ] Không log raw payload có PII/secret khi kiểm translations.
- [ ] Test independent of developer machine default Locale/timezone.
- [ ] Dùng context nhỏ khi cần hạ tầng, không ép full @SpringBootTest cho mọi case.
- [ ] Có test producer không publish khi validation thất bại.
- [ ] Phân biệt việc listener đã chạy với external effect thực sự hoàn tất.

### Câu hỏi và đáp án

1. Vì sao fake publisher không chứng minh listener chạy? — Vì fake chỉ ghi lời gọi từ service, không có Spring multicaster.
2. `@EventListener` cần điều kiện gì? — Method nằm trên Spring-managed bean và được annotation listener infrastructure phát hiện.
3. Có thể publish Java record không kế thừa ApplicationEvent không? — Có, Spring hỗ trợ object payload events.
4. Object payload được wrap thành loại nào? — `PayloadApplicationEvent`.
5. Mặc định `SimpleApplicationEventMulticaster` xử lý sync hay async? — Sync nếu không cấu hình executor/async behavior.
6. Có thể assert ngay sau publish không? — Có cho sync contract, không nói chung với async.
7. `MessageSource` dựa vào yếu tố gì chọn bản dịch? — Message code, arguments, Locale và configured resource bundles/hierarchy.
8. `Locale` có tự dịch một English sentence không? — Không.
9. `messages_vi.properties` tương ứng với gì? — Vietnamese language-level bundle.
10. `vi-VN` có thể dùng `messages_vi.properties` không? — Có khi không có candidate đặc hiệu hơn.
11. Vì sao tắt fallback system Locale? — Tránh phụ thuộc máy chạy test.
12. `messages.properties` dùng khi nào? — Base fallback khi không có candidate phù hợp/key ở localized bundles theo policy.
13. Hai overload `getMessage` khác nhau thế nào? — Một overload có default text, overload strict ném khi thiếu key.
14. Trong demo, `fr-FR` ra chuỗi nào? — `Base: saved order ORD-9`.
15. Trong demo, một event vi-VN in gì? — `Đã lưu đơn ORD-7`.
16. Một message có `{0}` lấy dữ liệu ở đâu? — Từ `Object[] args`.
17. Vì sao cần lưu properties bằng UTF-8? — Để tiếng Việt không bị sai charset.
18. Lỗi message missing có thể làm publisher fail không? — Có trong sync listener khi exception không bị ErrorHandler xử lý.
19. `@Async` listener có cùng thread/LocaleContextHolder mặc định không? — Không bảo đảm.
20. Nên xử lý Locale cho queued notification thế nào? — Mang explicit Locale/language preference trong event/command.
21. `@TransactionalEventListener` không có transaction sẽ làm gì mặc định? — Không gọi handler nếu không bật fallbackExecution.
22. Parent listener có nhận child events không? — Có thể có upward propagation với AbstractApplicationContext.
23. Root event có tự đi xuống child không? — Không.
24. `StaticMessageSource` unit test chứng minh điều gì? — Chứng minh handler format message, không chứng minh @EventListener registration.
25. Unit test thuần Java có kiểm Spring proxies không? — Không.
26. Có nên so nguyên Maven logs? — Không, dùng assertions/output của application.
27. Vì sao test negative order ID phải kiểm EventLog? — Để chứng minh không publish effect ngoài ý muốn.
28. `EventLog` dùng ArrayList có thread-safe không? — Không; demo chỉ dùng sync.
29. Có thể dùng TestContext cache không? — Có khi dùng Spring TestContext, nhưng ví dụ tạo context nhỏ chủ động.
30. Spring events có cung cấp exactly-once delivery qua JVM restart không? — Không.

## 23. Nguồn tham khảo

- Spring Framework 6.2 Reference — ApplicationContext Events và MessageSource: https://docs.spring.io/spring-framework/reference/6.2/core/beans/context-introduction.html
- Spring Framework 6.2 Javadoc — ApplicationEventPublisher: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/ApplicationEventPublisher.html
- Spring Framework 6.2 Javadoc — EventListener: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/event/EventListener.html
- Spring Framework 6.2 Javadoc — PayloadApplicationEvent: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/PayloadApplicationEvent.html
- Spring Framework 6.2 Javadoc — ApplicationEventMulticaster: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/event/ApplicationEventMulticaster.html
- Spring Framework 6.2 Javadoc — SimpleApplicationEventMulticaster: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/event/SimpleApplicationEventMulticaster.html
- Spring Framework 6.2 Javadoc — MessageSource: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/MessageSource.html
- Spring Framework 6.2 Javadoc — ResourceBundleMessageSource: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/ResourceBundleMessageSource.html
- Spring Framework 6.2 Javadoc — AbstractResourceBasedMessageSource (fallback locale): https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/AbstractResourceBasedMessageSource.html
- Spring Framework 6.2 Javadoc — StaticMessageSource: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/StaticMessageSource.html
- Spring Framework 6.2 Javadoc — NoSuchMessageException: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/NoSuchMessageException.html
- Spring Framework 6.2 Javadoc — TransactionalEventListener: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/transaction/event/TransactionalEventListener.html
- Spring Framework Reference — Testing the Spring IoC Container: https://docs.spring.io/spring-framework/reference/testing/integration.html
- JUnit 5 User Guide: https://junit.org/junit5/docs/current/user-guide/
- Java 17 Locale API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Locale.html
- Java 17 MessageFormat API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/MessageFormat.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="container-testing-009">Cô lập trạng thái, bộ nhớ đệm context và khi nào cần @DirtiesContext</a>

<details>
<summary>Xem chi tiết</summary>

# Cô lập trạng thái, bộ nhớ đệm context và khi nào cần @DirtiesContext

## 1. Vấn đề cần giải quyết

Khi kiểm thử Spring Core Container bằng Spring TestContext Framework, một cách hiểu sai rất phổ biến là:

> “Mỗi test method đều được Spring tạo một ApplicationContext mới, vì vậy tất cả singleton beans đều bắt đầu sạch.”

**Điều đó không đúng theo mặc định.** Spring TestContext Framework lưu `ApplicationContext` trong **context cache** và tái sử dụng nó cho những tests có cùng khóa cấu hình. Điều này giảm đáng kể thời gian bootstrap, nhưng cũng có nghĩa một mutable singleton bean có thể giữ trạng thái mà test trước đã thay đổi.

Một sai lầm ngược lại:

> “Muốn tests độc lập thì thêm @DirtiesContext trên mọi test.”

Làm vậy có thể phá lợi ích caching, tăng thời gian chạy, đóng/mở connection pools hoặc application resources quá nhiều mà không giải quyết các trạng thái ngoài Spring context như database rows, static fields, files, broker queues hay external services.

Bài này hướng dẫn:

1. Hiểu thế nào là **context cache** và **static cache per JVM process**.
2. Phân biệt **context isolation**, **bean state isolation**, **database isolation** và **test instance isolation**.
3. Xác định chính xác những yếu tố tạo context cache key.
4. Biết khi nào context được cache, reused, evicted và closed.
5. Sử dụng `@DirtiesContext` với `methodMode`, `classMode`, `hierarchyMode`.
6. Nhận biết khi nào **không** cần `@DirtiesContext`: reset state, rollback transaction, unique test data, fresh prototype.
7. Viết JUnit Jupiter tests minh họa context identity và mutable singleton state.
8. Kiểm chứng rằng `@DirtiesContext(methodMode = AFTER_METHOD)` loại context cũ khỏi cache và lần kiểm thử tiếp theo dùng context mới.
9. Dùng `@BeforeEach` để reset dữ liệu trong cùng context thay vì luôn tái tạo container.
10. Debug context cache misses, LRU eviction, cache size, test forks và parallel tests.

Ví dụ chạy bằng **Java 17, Spring Framework 6.2.18 và JUnit Jupiter 5.11.4**, không cần Spring Boot.

## 2. Spring TestContext Framework nằm ở đâu?

Spring TestContext Framework là module test integration trong `spring-test`. Với JUnit Jupiter, `SpringExtension` nối JUnit lifecycle với Spring TestContext:

```java
@SpringJUnitConfig(TestConfig.class)
class ServiceIntegrationTest {

    @Autowired
    ApplicationContext applicationContext;

    @Test
    void works() {
        // ApplicationContext được TestContext framework quản lý.
    }
}
```

Đây là mảnh minh họa; `TestConfig` cần được định nghĩa.

`@SpringJUnitConfig` là composed annotation kết hợp `@ExtendWith(SpringExtension.class)` và `@ContextConfiguration`. Nó giúp framework load context từ cấu hình và inject test dependencies. Spring Boot `@SpringBootTest` cũng dùng TestContext integration nhưng thêm cơ chế Boot application bootstrap; bài này không phụ thuộc Boot.

**Phân biệt hai vòng đời:**
- **JUnit test instance**: theo mặc định Jupiter dùng `PER_METHOD`, mỗi test method có một test class object mới.
- **Spring ApplicationContext**: có thể được **reuse** từ static context cache qua nhiều test methods và test classes.

Một test class Java mới **không buộc Spring phải tạo ApplicationContext mới**.

## 3. Cache được dùng để làm gì?

Tạo Spring context có thể phải:
- Đọc configuration metadata.
- Xử lý `@Configuration`, `@Bean`, `@Import`, `@ComponentScan`.
- Tạo và initialize non-lazy singleton beans.
- Đăng ký `BeanPostProcessor`, conversion services, events.
- Mở connection pools, test servers, security filters hoặc integration infrastructure.

Nếu mỗi test method load lại toàn bộ app, bộ integration tests trở nên chậm và dễ gây resource contention.

Spring TestContext Framework duy trì **một static cache**, keyed bởi combination của các configuration parameters. Những tests có khóa tương đương có thể dùng **cùng `ApplicationContext` object**.

Ví dụ:

```text
Test A with Config X
      |
      v
Cache lookup key X -> MISS
      |
      v
Load ApplicationContext X
      |
      v
Cache stores Context X

Test B with Config X
      |
      v
Cache lookup key X -> HIT
      |
      v
Reuse same Context X (same singleton instances)

Test C with Config Y
      |
      v
Different cache key -> MISS
      |
      v
Load separate Context Y
```

Bộ nhớ đệm này là **implementation của Spring TestContext testing infrastructure**, không phải mọi `new AnnotationConfigApplicationContext(...)` trong code ứng dụng. Nếu chính bạn tự tạo context bằng `new` ở mỗi test, chúng không tự được gom vào cùng cache theo cơ chế `SpringExtension` nói ở đây.

## 4. Cache key gồm những gì?

Theo Spring Framework 6.2 Reference, khóa duy nhất dựa trên nhiều cấu hình:

| Thành phần khóa | Nguồn hoặc ý nghĩa |
| --- | --- |
| `locations` | Resource paths trong `@ContextConfiguration` |
| `classes` | Configuration classes trong `@ContextConfiguration` |
| `contextInitializerClasses` | `ApplicationContextInitializer` classes |
| `contextCustomizers` | Customizers, gồm `@DynamicPropertySource` và bean overrides như `@TestBean`, `@MockitoBean` |
| `contextLoader` | Implementation `ContextLoader` |
| `parent` | Parent configuration theo `@ContextHierarchy` |
| `activeProfiles` | `@ActiveProfiles` |
| `propertySourceDescriptors` | Resource-based test properties theo `@TestPropertySource` |
| `propertySourceProperties` | Inline properties của `@TestPropertySource` |
| `resourceBasePath` | Web resource base path từ `@WebAppConfiguration` |

Hệ quả:

- Hai classes cùng `@SpringJUnitConfig(CommonConfig.class)`, cùng properties/profiles/customizers, có thể dùng chung context.
- Hai tests chỉ khác `@ActiveProfiles("dev")` vs `@ActiveProfiles("test")` thường có hai cache keys.
- Hai tests khác inline `@TestPropertySource(properties=...)` sẽ thường có hai keys.
- Hai tests dùng bean override khác nhau có thể có customizer khác, không reuse.
- **Tên test class hoặc tên test method không tự nó là toàn bộ cache key.**

Không dùng “cùng configuration class” làm điều kiện duy nhất để khẳng định cache hit; phải tính các phần còn lại của merged context configuration.

## 5. Cache là static và bị giới hạn theo JVM process

Spring TestContext context cache nằm trong static state của test infrastructure, tức là cache **trong cùng JVM process**.

Nếu Maven Surefire hoặc Gradle tách tests sang nhiều JVM forks, mỗi process có static state và cache riêng. Do đó:

- Không thể kỳ vọng reuse giữa hai process độc lập.
- Chạy một test riêng bằng IDE có thể load context một lần dù lần chạy cả suite có hit cache.
- Multi-fork build có thể làm startup xuất hiện nhiều lần, không phải `@DirtiesContext` gây ra.
- Tăng số workers/forks có thể tăng memory/CPU do nhiều contexts được load đồng thời.

Giới hạn cache mặc định của Spring 6.2 là **32 contexts**. Khi vượt giới hạn, framework áp dụng **Least Recently Used (LRU)** eviction và **close** context cũ bị đẩy khỏi cache.

Có thể cấu hình:

```text
-Dspring.test.context.cache.maxSize=64
```

Hoặc qua Spring `SpringProperties` mechanism. Đây là **JVM system property cho test process**, không phải một annotation đánh dấu class-level.

Đừng tăng lên vài trăm contexts mà không đo heap usage, pools, native resources và thời gian startup. Một cache đủ dùng giúp reuse; cache quá lớn giữ quá nhiều live Spring contexts.

## 6. Cache hit không bảo đảm trạng thái sạch

Giả sử có singleton bean:

```java
public final class CartState {
    private int itemCount;

    public int read() {
        return itemCount;
    }

    public void addOne() {
        itemCount++;
    }

    public void reset() {
        itemCount = 0;
    }
}
```

Đây là class minh họa.

Test thứ nhất:

```java
@Test
void first() {
    cartState.addOne();
    assertEquals(1, cartState.read());
}
```

Test thứ hai:

```java
@Test
void second() {
    assertEquals(0, cartState.read());
}
```

Nếu dùng cùng cached ApplicationContext mà không reset bean, test thứ hai **có thể fail** vì state vẫn là 1. Jupiter test instance có thể đã thay, nhưng singleton `CartState` vẫn là object cũ.

**Nguyên nhân** không phải singleton scope bị sai. Singleton và context cache đang hoạt động đúng theo contract; chính test đã thay đổi shared state rồi giả định trạng thái tự quay về.

## 7. Phân loại trạng thái cần cô lập

| Loại trạng thái | Ví dụ | Reset phù hợp |
| --- | --- | --- |
| Mutable field của singleton | `CartState.count` | Gọi `reset()` trước/sau test hoặc dùng thiết kế stateless |
| Cache do application tự quản | `ConcurrentHashMap`, Caffeine cache | `clear()`/invalidate hoặc reset fixture |
| Database rows trong transaction | Entities inserted/updated | Rollback test-managed transaction khi áp dụng, truncate/cleanup có kiểm soát |
| Database writes ngoài transaction | Async jobs, external clients | Explicit cleanup/test data isolation |
| Static fields | Static counters, registries | Reset static state rõ ràng, đừng chỉ reload context |
| System properties | `System.setProperty(...)` | Restore previous value trong `finally`/extension |
| Environment/configuration bean definitions | Thay BeanFactory config/runtime | Có thể cần new context; `@DirtiesContext` nếu thực sự sửa context |
| Embedded server/resource owner | Server started/stopped, connection factory altered | Rebuild context khi không thể phục hồi an toàn |
| Prototype instance | Object được tạo mỗi `getBean` | Giữ state trong instance riêng; cleanup từng resource |
| Thread-local | SecurityContext, request context | Explicit clear trong test lifecycle |
| File system/broker | Test-generated files, queues | Unique names/containers/cleanup |
| Mockito interactions/stubbing | Mock instance trong context | Reset mock framework hooks hoặc recreate fixture theo contract |

**Không có một annotation nào reset tất cả các loại trên.** `@DirtiesContext` chỉ quản lý lifecycle của **Spring ApplicationContext được cache**.

## 8. Vì sao @DirtiesContext tồn tại?

`org.springframework.test.annotation.DirtiesContext` đánh dấu Spring context đã bị **làm bẩn** vì test có thể thay đổi hoặc phá vỡ nó:

- Test đã mutate singleton theo cách không thể reset đủ tin cậy.
- Test đã làm thay đổi BeanFactory definitions/settings có ảnh hưởng các tests tiếp theo.
- Một shared bean/resource đã ở trạng thái không thể sử dụng lại.
- Test cần chắc chắn chạy trước/sau trên context mới do configuration/lifecycle semantics.
- Một context hierarchy đã bị biến đổi và không nên reuse.

Khi Spring TestContext xử lý annotation:
1. Đánh dấu context tương ứng là dirty.
2. **Loại entry liên quan khỏi context cache**.
3. **Đóng context** cũ.
4. Khi một test sau cần configuration tương đương, framework **load context mới**.

Nó không “xóa sạch bean rồi giữ nguyên ApplicationContext instance”; nó **evicts and closes** context cũ, sau đó tạo context mới khi cần.

## 9. Khi nào không cần @DirtiesContext?

Thông thường **không cần** nếu:
- Test chỉ đọc dữ liệu từ beans bất biến/stateless.
- Test mutate một bean nhưng có thể reset deterministic bằng `@BeforeEach` hoặc `@AfterEach`.
- Test tạo prototype riêng, cleanup resource riêng và không thay đổi singleton/BeanFactory.
- Tests dùng transaction rollback và toàn bộ thay đổi cần cô lập **thật sự nằm trong test-managed transaction**.
- Test dùng unique IDs/schema/tenant để tách dữ liệu mà không ảnh hưởng configuration toàn context.
- Mỗi test chỉ kiểm tra service logic bằng Mockito/unit tests và không cần load Spring context.
- Test cần config khác; sửa chính `@ActiveProfiles`/`@TestPropertySource`/`@SpringJUnitConfig` để cache key thể hiện sự khác biệt, thay vì cố dirty shared context.

**Chi phí** của `@DirtiesContext` là rebuild context, chạy singleton constructors/init callbacks lại, có thể start/stop server/connection pools và làm cache hit ratio thấp hơn.

Quy tắc: **reset trạng thái nhỏ nếu có thể; chỉ evict toàn context khi cần.**

## 10. @DirtiesContext ở cấp method

Khi đặt trên một test method:

```java
@Test
@DirtiesContext(
        methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
void mutatesContainer() {
    // Test thay đổi context/bean không thể reset sạch.
}
```

**`AFTER_METHOD` là mặc định khi dùng trên method.** Framework sẽ mark context dirty sau test đó; method kế tiếp với cùng config sẽ load context mới.

Khi cần context mới **trước khi method chạy**:

```java
@Test
@DirtiesContext(
        methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
void requiresFreshContext() {
    // Test bắt đầu với một context được reload.
}
```

Dùng `BEFORE_METHOD` khi yêu cầu setup mới **đối với chính method được đánh dấu**, không muốn thừa hưởng context cache state trước đó. Nếu bản thân test tiếp tục mutate bean, cần xem xét cleanup sau method nữa.

Đừng gắn methodMode lên annotation ở class rồi tưởng áp dụng cả class; class-level semantics do `classMode` quyết định.

## 11. @DirtiesContext ở cấp class

Các giá trị `DirtiesContext.ClassMode`:

| Giá trị | Khi nào evict context |
| --- | --- |
| `BEFORE_CLASS` | Trước khi class hiện tại được chạy |
| `AFTER_CLASS` | Sau khi class hiện tại hoàn tất; **mặc định khi annotation ở class** |
| `BEFORE_EACH_TEST_METHOD` | Trước mỗi test method trong class |
| `AFTER_EACH_TEST_METHOD` | Sau mỗi test method trong class |

Ví dụ:

```java
@DirtiesContext(
        classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringJUnitConfig(Config.class)
class MutatingIntegrationTest {
    // ...
}
```

Đây là mảnh minh họa.

`AFTER_CLASS` giúp class có thể reuse context giữa các methods của chính nó, rồi xóa sau toàn bộ class. Điều này khác với `AFTER_EACH_TEST_METHOD`, vốn yêu cầu nhiều context reload hơn.

Nếu đặt `@DirtiesContext` cả ở class và method, cả hai tập hợp modes đều được honor. Ví dụ class `BEFORE_EACH_TEST_METHOD` và một method `AFTER_METHOD` khiến method đó evict cả trước và sau.

## 12. Bảng quyết định cho @DirtiesContext

| Nhu cầu | Lựa chọn phù hợp |
| --- | --- |
| Một method làm hỏng shared context, method sau phải load mới | `@DirtiesContext` trên method (default AFTER_METHOD) |
| Một method cần context mới trước khi bắt đầu | MethodMode `BEFORE_METHOD` |
| Một class mutate context nhưng các methods nội bộ có thể share | ClassMode `AFTER_CLASS` |
| Mỗi method trong class phải được bắt đầu với context mới | ClassMode `BEFORE_EACH_TEST_METHOD` |
| Mỗi method trong class làm dirty context | ClassMode `AFTER_EACH_TEST_METHOD` |
| Toàn class phải dùng context mới so với tests trước | ClassMode `BEFORE_CLASS` |
| Chỉ reset 1 mutable singleton field | `@BeforeEach` + `reset()`, thường không cần dirties |
| Chỉ rollback test-managed DB transaction | `@Transactional` testing + rollback khi áp dụng |
| Config profiles/properties khác nhau | Khai báo config khác để tạo cache key khác |
| Context hierarchy bị thay đổi ở một level | Xem `hierarchyMode`, tránh evict không cần thiết |
| Static/global/external state bị thay đổi | Explicit cleanup; dirties một mình không đủ |

Điểm cân nhắc chính là **chi phí rebootstrap vs độ tin cậy reset**.

## 13. ApplicationContext hierarchy và hierarchyMode

Trong một parent–child context hierarchy, dirty một child context có thể ảnh hưởng cache entries của các children khác có common parent configuration.

`@DirtiesContext` hỗ trợ `hierarchyMode`:

- `EXHAUSTIVE` (**mặc định**): Spring sử dụng thuật toán exhaustive, có thể loại khỏi cache **những context thuộc sub-hierarchies chia sẻ một common ancestor**, không chỉ child hiện tại.
- `CURRENT_LEVEL`: giới hạn việc dọn cache theo **level hiện tại** trong hierarchy, tránh loại bỏ những contexts ở level khác khi không cần.

Ví dụ minh họa:

```java
@DirtiesContext(
        hierarchyMode =
                DirtiesContext.HierarchyMode.CURRENT_LEVEL)
@Test
void changesOnlyCurrentWebContext() {
    // ...
}
```

Không dùng `CURRENT_LEVEL` chỉ để tăng tốc nếu test đã mutate một shared parent bean. Nếu parent không còn an toàn, giữ lại nó khiến các child contexts khác tiếp tục dùng shared state bị lỗi.

**Cách chọn:** kiểm tra **owner của state bị mutate**. Parent bean hỏng → cần dirty đầy đủ hơn. Chỉ một child context/resource cục bộ hỏng → `CURRENT_LEVEL` có thể giúp tránh dọn quá rộng.

## 14. Context cache và @ActiveProfiles

Giả sử hai test classes:

```java
@ActiveProfiles("test")
@SpringJUnitConfig(AppConfig.class)
class TestProfileChecks {
}

@ActiveProfiles("dev")
@SpringJUnitConfig(AppConfig.class)
class DevProfileChecks {
}
```

Đây là các class snippets.

Hai classes có `activeProfiles` khác nhau, vì vậy **thường có cache keys khác nhau**. Không cần `@DirtiesContext` chỉ để chuyển profile giữa những classes khai báo rõ trong metadata.

Ngược lại, nếu một test gọi `context.getEnvironment().setActiveProfiles("dev")` **sau khi context đã refresh**, điều này **không tự chạy lại** mọi `@Profile` condition và tái tạo bean definitions. Không phải cách đổi profile cho một cached context đã khởi tạo.

Hãy thiết kế profile/config của tests **trước lúc load context**, để Spring phân biệt cache key đúng.

## 15. @TestPropertySource và cache fragmentation

Ví dụ:

```java
@TestPropertySource(
        properties = "feature.payment.enabled=true")
@SpringJUnitConfig(PaymentConfig.class)
class PaymentEnabledTest {
}

@TestPropertySource(
        properties = "feature.payment.enabled=false")
@SpringJUnitConfig(PaymentConfig.class)
class PaymentDisabledTest {
}
```

Hai classes mô tả hai environments khác nhau; inline property là một thành phần khóa cache. Spring có thể load hai contexts khác nhau, không có lý do mặc định để dirty chúng.

Nhưng nếu hàng trăm classes mỗi class cấu hình một tập property inline khác nhau, cache misses và LRU eviction sẽ tăng. Khi phù hợp, nên dùng **một vài shared test configurations**, không phân mảnh không cần thiết chỉ vì mỗi test muốn reset một biến nghiệp vụ.

System property thay đổi bằng `System.setProperty(...)` trong test không đồng nghĩa TestContext framework tự thay khóa của context hiện có; static global environment mutations có thể gây cross-test pollution. Nên restore property cũ trong finally/extension hoặc dùng test configuration metadata riêng.

## 16. @DynamicPropertySource và dữ liệu động

`@DynamicPropertySource` thường dùng với external resources như Testcontainers:

```java
@DynamicPropertySource
static void configure(DynamicPropertyRegistry registry) {
    registry.add("app.endpoint", () -> "http://localhost:12345");
}
```

Đây là mảnh minh họa.

Dynamic property methods tham gia `contextCustomizers` trong cache key. Nhưng giá trị thực tế từ `Supplier` có thể thay đổi theo runtime resource. Trường hợp hay gặp:

- Một base test class khai báo `@DynamicPropertySource`.
- Các subclasses khác nhau sử dụng test container resources hoặc ports khác nhau.
- Context cache có thể được share theo metadata của base class dù value source động đã khác.

Spring Reference khuyến cáo: **nếu dynamic properties thay đổi giữa subclasses** và context cũ có cấu hình không hợp lệ, có thể cần `@DirtiesContext` trên base class để mỗi subclass dùng context phù hợp.

Tuy nhiên, không gắn dirties mù quáng trên mọi Testcontainers test. Nếu các tests share **cùng một container resource** còn sống và values ổn định, context reuse có thể hoàn toàn phù hợp.

## 17. @MockitoBean/@TestBean và context customizers

Spring Framework 6.2 hỗ trợ test bean overriding annotations như:
- `@TestBean` để override một bean bằng factory method.
- `@MockitoBean` tạo Mockito mock bean.
- `@MockitoSpyBean` tạo Mockito spy.
  
Các overrides là một phần của `contextCustomizers` và có thể khiến context cache keys khác nhau giữa hai classes.

Ví dụ:

```java
@MockitoBean
PaymentGateway gateway;
```

Đây là field snippet, cần annotation Spring Test `org.springframework.test.context.bean.override.mockito.MockitoBean`, `PaymentGateway` type và Mockito dependencies phù hợp.

**Không nhầm:** Mockito interaction state phải được reset theo test framework/mock reset strategy; context cache key chỉ phân biệt configuration, không tự tạo một fresh mock mọi test method nếu context được reuse. Spring TestContext có hỗ trợ reset mocks theo lifecycle, nhưng cần hiểu annotation/version/strategy cụ thể.

Với Spring Boot, `@MockBean` có lịch sử riêng; đây là bài Spring Framework 6.2 nên dùng `@MockitoBean` khi minh họa API framework, tránh lẫn nhiều versions.

## 18. @Transactional test rollback và giới hạn của nó

Spring TestContext Framework có thể quản lý transaction cho test methods được đánh dấu `@Transactional`, khi có `PlatformTransactionManager` phù hợp. Mặc định, **test-managed transaction** sẽ rollback sau test (trừ khi có annotation override như `@Commit`).

Nhưng:
- Chỉ những DB operations thực sự tham gia test-managed transaction mới rollback theo nó.
- Một `@Async` operation khác thread có thể không tham gia transaction của test thread.
- HTTP call tới server trong process khác, event consumer hoặc external broker không tự rollback.
- Mutable singleton fields/static caches không phải database transaction state.
- Preemptive timeout chạy test body ở thread khác có thể làm code không chạy trong ThreadLocal-bound transaction.

Vì vậy:

```java
@Transactional
@Test
void updatesDatabase() {
    // DB work trong test-managed transaction.
}
```

Đây là mảnh minh họa, không phải cam kết mọi side effect ngoài database đều được rollback.

**Khi chỉ có rollbackable DB changes**, `@DirtiesContext` thường là công cụ quá mạnh và không cần thiết.

## 19. @BeforeEach reset state là lựa chọn tiết kiệm

Đối với mutable singleton có API reset an toàn:

```java
@BeforeEach
void reset() {
    cartState.reset();
}
```

Khi mỗi test chạy:
1. Jupiter tạo test instance mới theo default.
2. Spring inject cùng cached bean vào test instance.
3. `@BeforeEach` đặt state về baseline.
4. Test mutate và assert.
5. Test tiếp theo cũng reset, nên không phụ thuộc execution order.

Ưu điểm:
- Giữ cache reuse.
- Không restart DB pool/embedded servers.
- Giảm thời gian suite.
- Tăng khả năng chạy lặp lại.

Nhược điểm:
- Reset phải thực sự đầy đủ; nếu bean có multiple caches, background tasks hoặc hidden state, một method reset đơn giản có thể không bảo đảm isolation.
- Reset các **shared external resources** không phải lúc nào cũng thread-safe nếu tests chạy song song.

**Đừng reset state “chỉ tình cờ”**; biến baseline thành một contract có thể kiểm thử.

## 20. Mã chạy được: kiểm chứng cache reuse và @DirtiesContext

Dự án minh họa **hai cách cô lập trạng thái**:

1. **`ContextCacheDirtiesTest`:** cố ý cho mutable singleton “rò” state từ test #1 sang test #2 để chứng minh context cache reuse, rồi dùng `@DirtiesContext(methodMode = AFTER_METHOD)` sau test #3 và xác nhận test #4 nhận context mới.
2. **`ResetInsteadOfDirtyTest`:** sử dụng context với configuration riêng và `@BeforeEach` reset state. Hai tests độc lập mà **không** evict context sau mỗi method.

Các tests được thiết kế deterministic. Test #1 → #4 có `@TestMethodOrder` và `@Execution(SAME_THREAD)` để chủ động thể hiện một *demonstration sequence*. Trong production, các tests thông thường nên **tránh** dựa vào test order.

### 20.1 Cấu trúc dự án

```text
spring-context-cache-tests/
├── settings.gradle
├── build.gradle
└── src/test/java/demo/
    ├── ContextCacheDirtiesTest.java
    └── ResetInsteadOfDirtyTest.java
```

Dự án chỉ có tests; không cần application main class. Java 17 trở lên.

### 20.2 settings.gradle

```groovy
rootProject.name = 'spring-context-cache-tests'
```

### 20.3 build.gradle

```groovy
plugins {
    id 'java'
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation 'org.springframework:spring-context:6.2.18'
    testImplementation 'org.springframework:spring-test:6.2.18'
    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

test {
    useJUnitPlatform()
    // Giữ mỗi class thực thi các methods một cách xác định;
    // không yêu cầu fork mỗi test class.
    maxParallelForks = 1
}
```

Mặc định JUnit Jupiter không bật parallel test execution. Trong demo có `@Execution(SAME_THREAD)` để nói rõ ý định đối với test trình diễn thứ tự.

### 20.4 src/test/java/demo/ContextCacheDirtiesTest.java

```java
package demo;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.*;

import static org.springframework.test.annotation.DirtiesContext.MethodMode.AFTER_METHOD;

@SpringJUnitConfig(ContextCacheDirtiesTest.Config.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Execution(ExecutionMode.SAME_THREAD)
class ContextCacheDirtiesTest {

    private static final AtomicInteger GENERATIONS =
            new AtomicInteger();

    private static ApplicationContext firstContext;
    private static CounterStore firstStore;
    private static int firstGeneration;

    @Autowired
    ApplicationContext context;

    @Autowired
    CounterStore store;

    static final class CounterStore {
        private final int generation;
        private int value;

        CounterStore(int generation) {
            this.generation = generation;
        }

        int generation() {
            return generation;
        }

        int get() {
            return value;
        }

        void set(int nextValue) {
            value = nextValue;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Config {

        @Bean
        CounterStore counterStore() {
            return new CounterStore(
                    GENERATIONS.incrementAndGet());
        }
    }

    @Test
    @Order(1)
    void firstMethodStoresReferenceAndMutatesSingleton() {
        firstContext = context;
        firstStore = store;
        firstGeneration = store.generation();

        assertEquals(0, store.get());
        store.set(7);

        assertSame(
                context.getBean(CounterStore.class), store);
    }

    @Test
    @Order(2)
    void secondMethodReusesContextAndObservesState() {
        // Cố ý minh họa leak trong cùng cached context.
        assertSame(firstContext, context);
        assertSame(firstStore, store);
        assertEquals(firstGeneration, store.generation());
        assertEquals(7, store.get());
    }

    @Test
    @Order(3)
    @DirtiesContext(methodMode = AFTER_METHOD)
    void thirdMethodDirtiesContextAfterRunning() {
        assertSame(firstContext, context);
        store.set(99);
        assertEquals(99, store.get());
        // Sau method: evict + close context cũ.
    }

    @Test
    @Order(4)
    void fourthMethodGetsReloadedContext() {
        assertNotSame(firstContext, context);
        assertNotSame(firstStore, store);
        assertTrue(store.generation() > firstGeneration);
        assertEquals(0, store.get());
        assertSame(
                context.getBean(CounterStore.class), store);
    }
}
```

**Chú ý test order:** Test class intentionally checks the order-dependent behavior of a mutable singleton so readers can **observe the leak**. Outside educational demonstrations, tests must clean their own state and work independently. Static fields lưu old context reference chỉ để so sánh identity sau evict; **không gọi methods trên old closed context**.

### 20.5 Giải thích từng test

**Test #1**
- SpringExtension load context từ `Config`, tạo `CounterStore` singleton có `value=0`.
- Test ghi lại `firstContext`, `firstStore` và generation.
- Test gọi `store.set(7)`, cố ý không reset.

**Test #2**
- Cache key không thay, SpringExtension dùng lại đúng context.
- JUnit tạo test instance mới nhưng fields lại được inject cùng singleton.
- `assertSame(firstContext, context)` và `assertSame(firstStore, store)` true.
- `store.get() == 7`: chứng minh mutable singleton có thể rò state giữa tests.

**Test #3**
- Vẫn cùng context để kiểm tra dirty lifecycle.
- Method mutate value thành 99.
- `@DirtiesContext(methodMode=AFTER_METHOD)` khiến Spring evict và close context sau method kết thúc.

**Test #4**
- Spring TestContext cần cấu hình cùng key nhưng entry cũ đã bị evict.
- Spring load context mới, tạo `CounterStore` mới với generation cao hơn.
- `assertNotSame` cho cả context và store.
- `value=0` vì constructor của state object mới bắt đầu ở baseline.

Các tests **không** assert absolute generation == 1 hay == 2, vì trong real test suites có thể có các contexts khác được tạo trước đó. Chỉ assert generation tăng và reference thay đổi.

### 20.6 src/test/java/demo/ResetInsteadOfDirtyTest.java

```java
package demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(ResetInsteadOfDirtyTest.Config.class)
class ResetInsteadOfDirtyTest {

    @Autowired
    MutableState state;

    @Configuration(proxyBeanMethods = false)
    static class Config {
        @Bean
        MutableState mutableState() {
            return new MutableState();
        }
    }

    static final class MutableState {
        private int count;

        int get() {
            return count;
        }

        void add(int amount) {
            count += amount;
        }

        void reset() {
            count = 0;
        }
    }

    @BeforeEach
    void resetBeforeEveryTest() {
        // Giữ ApplicationContext trong cache.
        // Chỉ reset dữ liệu có thể kiểm soát.
        state.reset();
    }

    @Test
    void firstUseIsIndependent() {
        assertEquals(0, state.get());
        state.add(10);
        assertEquals(10, state.get());
    }

    @Test
    void secondUseIsIndependent() {
        // Thứ tự test methods không quan trọng.
        assertEquals(0, state.get());
        state.add(25);
        assertEquals(25, state.get());
    }
}
```

Test class này sử dụng một `Config` riêng, do đó không share cache key với class `ContextCacheDirtiesTest`. Nó cho thấy **một shared singleton không đồng nghĩa tests buộc phải phụ thuộc thứ tự**, nếu fixture baseline được reset trước mỗi method.

### 20.7 Chạy

```bash
gradle test
```

Kết quả dự kiến của các assertions: **6 tests pass** (4 test methods ở `ContextCacheDirtiesTest` và 2 methods ở `ResetInsteadOfDirtyTest`), khi project được chạy với config nêu trên.

Gradle console output không nhất thiết in tên từng test ở mặc định. Có thể xem report:

```text
build/reports/tests/test/index.html
```

Đây là report path của Gradle Java Test task. Không diễn giải một dòng "BUILD SUCCESSFUL" thành test output từ Spring; kiểm tra test report để biết mọi test method đều pass.

### 20.8 Điều gì được và không được chứng minh?

Demo chứng minh:
- Cùng TestContext configuration reuse cùng context identity khi cache không bị invalidated.
- Mutable singleton sống cùng cached context.
- Method-level `@DirtiesContext` AFTER_METHOD làm context lần sau khác reference.
- Reset singleton state có thể rẻ hơn rebuild context.
- Một JUnit test class instance mới không đồng nghĩa một Spring context mới.

Demo **không** giả định mọi suites chạy đúng 6 tests nếu developer thêm tests khác, bật custom parallel execution hoặc thêm global test configuration. Nó cũng không minh họa database transactions, embedded servers hay external state cleanup trực tiếp; các trường hợp đó phải có tests riêng.

## 21. Vì sao JUnit ordering chỉ phù hợp demo này?

Phần `ContextCacheDirtiesTest` có `@Order(1..4)` để **chủ động kiểm tra lifecycle sequence**. Nếu bỏ thứ tự:
- Test #2 có thể chạy trước test #1, chưa có reference để assert.
- Test #4 có thể chạy trước method dirty, khiến assertion sai.
- Thử nghiệm sẽ không còn mô tả kịch bản nhân quả.

Với tests production, **không nên để test A phải chạy trước test B để trạng thái của B hợp lệ**. Nên chọn:
- Each test method self-contained.
- `@BeforeEach` resets mutable beans/fixtures.
- DB rollback hoặc explicit cleanup.
- Fresh context khi thật sự cần, chứ không phụ thuộc sequence.

Demo có intentionally shared static references; đó là công cụ học tập, **không phải recommended architecture** cho test suites.

## 22. Tự kiểm tra context cache bằng logs

Spring TestContext có logger category:

```text
org.springframework.test.context.cache
```

Đặt level `DEBUG` trong logging framework của test suite để thấy các thống kê cache như hitCount, missCount, size, eviction và parent-related metrics tùy version.

Ví dụ với Logback (nếu có dependency/logback configuration):

```xml
<configuration>
    <logger name="org.springframework.test.context.cache"
            level="DEBUG"/>
    <root level="WARN">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

Đây là **mảnh cấu hình minh họa**, không phải file Logback hoàn chỉnh: cần tự khai báo appender `STDOUT` và logback dependency nếu project thực tế sử dụng nó.

Không có Logback trong Gradle demo mặc định, nên đừng mong log category tự hiển thị chỉ vì code đã gọi `@DirtiesContext`. Nếu cần logs hãy thêm logging backend và config phù hợp.

Dấu hiệu có thể quan sát trong test:
- Tăng cache hits giữa methods cùng config.
- Cache miss khi entry chưa từng load, bị evict LRU hoặc bị dirtied.
- Context close callbacks khi dirty/evicted.
- Cache size nhỏ hơn số tất cả combinations nếu LRU đang hoạt động.

## 23. Context lifecycle và console logs

Context thường được load khi SpringExtension chuẩn bị test instance (để inject dependencies) — **trước test method**. Vì vậy:
- Logs từ context startup đôi khi không được IDE/Gradle liên kết với một method cụ thể.
- Nếu `@DirtiesContext(BEFORE_METHOD)` khiến reload ngay trước method, logging có thể xuất hiện sát execution của method.
- Logs từ context close sau class/suite có thể không gắn với test name.
- JVM shutdown hook có thể đóng cached contexts khi test JVM kết thúc. Thread name trong Reference có thể là `SpringContextShutdownHook`.

Nếu một bean implementation ghi “started” ra console, không suy rằng log đó thuộc test hiện tại chỉ dựa vào vị trí trong output. Dùng explicit context identity, generation counters và cache logs để phân tích.

## 24. @DirtiesContext không reset static state

Giả sử:

```java
public final class GlobalMetrics {
    public static final AtomicInteger COUNT =
            new AtomicInteger();
}
```

Spring tạo một context mới không gọi `GlobalMetrics.COUNT.set(0)`. Java static field thuộc class/classloader, không nằm trong bean instance lifecycle.

Nếu test #1 tăng static counter 3, đánh dấu `@DirtiesContext` rồi test #2 đọc static counter, nó vẫn có thể là 3.

Hướng xử lý:
- Reset static state rõ.
- Tránh mutable statics trong application code.
- Tách metrics vào test-scoped/fresh bean khi phù hợp.
- Fork JVM nếu test thật sự cần full process-level isolation, biết rằng điều đó mất context cache reuse giữa forks.

**Không dùng @DirtiesContext làm công cụ giả lập restart cả JVM.**

## 25. @DirtiesContext không xóa database hay filesystem

Nếu test tạo data trong database ngoài transaction:
- Context có thể đóng một DataSource pool.
- **Dữ liệu DB vẫn nằm trong database** nếu đã commit.
- Context mới có thể nối lại cùng database và thấy chính những rows đó.

Tương tự:
- File đã ghi vẫn tồn tại.
- Message đã publish vào broker vẫn có thể còn.
- External service đã mutate vẫn có state mới.
- Singleton cũ có reference trong static field vẫn còn được Java code giữ.

Do đó, nếu muốn test isolation:
1. Xác định ownership của từng resource.
2. Reset/rollback/cleanup tại resource owner.
3. Chỉ evict context nếu container object graph bị dirty.
4. Nếu cần resource hoàn toàn mới, dùng unique test database/schema/temporary directory/container, không trông chờ dirties tự tạo chúng.

## 26. @DirtiesContext và concurrency/parallel tests

Static context cache có thể được nhiều test methods/classes dùng song song. Nếu một test đánh dấu context dirty trong khi test khác vẫn dùng context đó, context có thể bị close và test kia gặp exception hoặc inconsistent state.

Vì vậy khi kết hợp:
- `@Execution(CONCURRENT)`,
- parallel test classes,
- shared mutable singleton beans,
- `@DirtiesContext`,
- external resources,

cần explicit execution/isolation policy.

Spring TestContext cache có concurrency protections nhưng không thể tự biến **business-level shared mutable singleton state** thành thread-safe. Một test mutate bean trong khi test khác assert baseline là test design race.

Chiến lược:
- Hạn chế parallel tests chia sẻ context mutable.
- Tách configurations/cache keys cho những scenarios độc lập thực sự.
- Dùng `@ResourceLock` của JUnit khi cần serialize access tới shared resource.
- Tránh dirty context đang được một test chạy song song sử dụng.
- Giữ test fixture cleanup deterministic.

Trong demo, `@Execution(SAME_THREAD)` giúp trình diễn theo thứ tự; Gradle `maxParallelForks=1` không tự bảo đảm mọi Java thread/test scheduling nếu bật parallel ở tầng Jupiter riêng.

## 27. Hai tests khác nhau có thể reuse context như thế nào?

Ví dụ:

```java
@SpringJUnitConfig(SharedConfig.class)
class CustomerServiceTest {
}

@SpringJUnitConfig(SharedConfig.class)
class OrderServiceTest {
}
```

Đây là mảnh minh họa.

Nếu combined merged context configuration giống nhau, cùng JVM test process và cache entry chưa bị evicted/dirtied, hai classes **có thể dùng chung context**. Đó là hành vi đáng mong đợi giúp suite chạy nhanh.

Nhưng nếu mỗi class tự khai báo `@TestPropertySource(properties = "...")` khác nhau, hoặc có bean overrides/customizers khác, **cùng SharedConfig.class chưa đủ** bảo đảm cache hit.

Do vậy không nên so sánh context identities giữa hai classes như một assertion bắt buộc của nghiệp vụ. Cache reuse là optimization theo documented configuration; các tests tốt vẫn đúng nếu context bị reload do giới hạn cache hoặc do class khác hợp pháp dirty nó.

## 28. Đo tốc độ và số lần startup

Một cách đo có kiểm soát:
- Instrument constructor/`@PostConstruct` của một infrastructure-heavy singleton bằng `AtomicInteger`.
- Chạy một suite với cấu hình đồng nhất, không có `@DirtiesContext`.
- Chạy một suite tương tự nhưng dirty sau mỗi test.
- So sánh startup time, cache hits/misses và tổng thời gian tests.
- Không lẫn JVM cold startup, class loading, Gradle daemon warmup hay database startup vào kết luận nếu chưa đo riêng.

Nhiều `@DirtiesContext` annotations có thể làm tăng rõ thời gian integration tests vì context reload chạy nhiều initialization callbacks. Nhưng không khẳng định một con số tối ưu cố định cho mọi application.

Spring Boot `@SpringBootTest` có thể đặc biệt tốn thời gian startup nếu nhiều auto-configurations/servers được load, nên càng cần tránh dirty không cần thiết. Đây là một nguyên tắc performance, không thay đổi contract của core TestContext cache.

## 29. Phân biệt context cache và BeanFactory singleton cache

Hai caches khác nhau:

| Cache | Chủ sở hữu | Key chính | Được chia sẻ thế nào |
| --- | --- | --- | --- |
| **TestContext context cache** | Spring `spring-test` | Merged context configuration | Reuse `ApplicationContext` giữa tests phù hợp |
| **BeanFactory singleton registry** | Mỗi Spring BeanFactory | Bean name/internal cache metadata | Reuse một singleton instance **trong** một context |
| Application business cache | Do application/library sở hữu | Business keys | Scope/lifecycle riêng, có thể nằm trong singleton |
| External DB/broker | Server độc lập | Data/resource identifiers | Không tự được reset bởi context close |

Một context cache hit làm tái sử dụng ApplicationContext, nên **cả singleton registry bên trong context cũng vẫn giữ các singleton references cũ**.

Khi context bị evicted/closed, context mới có BeanFactory registry mới. Nhưng **static state, external data và references đã rò ra ngoài** không tự biến mất chỉ vì singleton registry cũ bị bỏ.

## 30. Những lỗi thường gặp

| Hiện tượng | Nguyên nhân phổ biến | Cách xử lý |
| --- | --- | --- |
| Test #2 thấy giá trị test #1 | Mutable singleton trong cached context | Reset state hoặc dirty đúng phạm vi |
| Mỗi test đều reload context | Gắn `@DirtiesContext` ở class/method quá rộng | Bỏ dirty nếu reset có thể làm đúng |
| Cùng config nhưng cache miss | Property/profile/customizer khác | So toàn merged context configuration |
| Run IDE riêng không reuse | Khác JVM process/suite | Chạy cùng test process để quan sát cache |
| Gradle test forks làm load nhiều contexts | Cache static per JVM | Điều chỉnh fork strategy có kiểm soát |
| Cache quá nhỏ | >32 distinct keys, LRU evicts | Giảm fragmentation hoặc tăng maxSize có đo |
| Thêm dirties mà DB vẫn bẩn | Dữ liệu đã commit ngoài transaction | DB cleanup/unique fixtures |
| Thêm dirties mà static counter còn giá trị cũ | Static field không thuộc context | Reset static |
| @Transactional mà external side effects còn | Không tham gia test transaction | Cleanup từng resource |
| BEFORE_METHOD không reset state của test trước | Ngoại vi/static state không thuộc context | Reset/cleanup explicitly |
| @DirtiesContext AFTER_CLASS nhưng methods cùng class ảnh hưởng nhau | Chỉ dirty cuối class | @BeforeEach reset hoặc AFTER_EACH nếu bắt buộc |
| CURRENT_LEVEL không đủ | State bị mutate ở shared parent | Dùng EXHAUSTIVE phù hợp |
| EXHAUSTIVE làm nhiều contexts reload | Dọn cả related hierarchy | Cân nhắc CURRENT_LEVEL nếu chỉ child dirty |
| @DynamicPropertySource port khác mà old context reuse | Values thay đổi ngoài cache key | Đổi config hoặc dirty base test class khi cần |
| @MockitoBean thay đổi cache reuse | Override metadata là contextCustomizer | Giảm variations/nhóm tests cùng overrides |
| Dirty context trong parallel tests bị đóng giữa chừng | Shared context đang được dùng | Serialize hoặc isolate contexts |
| @BeforeEach reset làm mất dữ liệu background job chưa xong | Async/shared state race | Chờ shutdown/isolated resources |
| Test order làm suite flaky | Tests phụ thuộc previous state | Self-contained fixtures |
| Context startup log không có test method label | Context load ở test instance preparation | Dùng context cache DEBUG/generation counters |

## 31. Checklist tối ưu test isolation

Trước khi thêm `@DirtiesContext`, hỏi:

1. Đây có phải Spring **TestContext-managed ApplicationContext** không, hay context tự `new`?
2. Test thay đổi **state trong singleton**, **bean definitions**, **Environment** hay data ngoài context?
3. Có thể reset field/cache riêng bằng `@BeforeEach`/`@AfterEach` không?
4. DB writes có rollback trong đúng transaction không?
5. Async tasks/brokers/external endpoints có cleanup không?
6. Static/System properties/ThreadLocal có được restore không?
7. Các test classes có cùng merged config/cache key không?
8. Tests có dùng profiles/inline properties khác nhau làm tăng key count không?
9. Có `@DynamicPropertySource` với value thay đổi giữa subclasses không?
10. Bean overrides có tạo context customizers khác nhau không?
11. Có context hierarchy, state dirty thuộc parent hay child?
12. Nếu cần dirty, chọn `BEFORE_METHOD` hay `AFTER_METHOD`?
13. Nếu đặt class-level, `AFTER_CLASS` có đủ hay mỗi method cần fresh?
14. Cách này ảnh hưởng parallel test execution thế nào?
15. Bài test có assert nghiệp vụ độc lập, không phụ thuộc thứ tự chạy không?
16. Có logs/statistics xác minh cache hit/miss không?
17. Có process forks khiến cache không được reuse không?
18. Nếu tăng maxSize, heap/resource memory có chịu được không?
19. Có đảm bảo close/reload context không làm background jobs chạm resource đã đóng?
20. Chi phí context rebuild có được biện minh bởi chính loại state cần cô lập không?

## 32. Bài tập thực hành

### Bài tập 1 — thay AFTER_METHOD bằng BEFORE_METHOD

Ở `ContextCacheDirtiesTest`, chuyển annotation từ method #3 sang method #4 và dùng:

```java
@DirtiesContext(
        methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
```

Quan sát: test #3 sử dụng context cũ; ngay trước method #4 Spring evicts và loads context mới. Kết quả identity test #4 vẫn khác. Phân tích khác biệt thời điểm đóng context giữa hai modes.

### Bài tập 2 — bỏ dirties rồi reset thủ công

Gỡ annotation ở method #3, thêm reset state đúng trước method #4. Khi đó context reference **không đổi**, store cũng là cùng instance, nhưng `store.get()` về 0. Sửa assertion test #4 từ `assertNotSame` sang `assertSame`; loại này minh họa giữ cache mà vẫn đảm bảo dữ liệu baseline.

### Bài tập 3 — classMode AFTER_EACH_TEST_METHOD

Dùng `@DirtiesContext(classMode=AFTER_EACH_TEST_METHOD)` trên test class. Dự đoán mỗi method dùng context khác; vì vậy những assertions “test #2 thấy state của test #1” **không còn đúng**. Đây là ví dụ khi một annotation thay đổi mô hình test execution, không thể giữ nguyên các assertions của suite vốn cố ý kiểm chứng reuse.

### Bài tập 4 — thay đổi @ActiveProfiles

Tạo hai classes cùng `@SpringJUnitConfig(AppConfig.class)` nhưng profiles khác nhau. In context identity hoặc dùng constructor counter để quan sát chúng thuộc hai cache keys khác nhau. Không cần dirties chỉ vì profile khác nếu metadata khai báo đúng.

### Bài tập 5 — database cleanup

Tạo integration test với một relational database, `PlatformTransactionManager` và một test method `@Transactional`. So sánh rollbackable repository operations với một external HTTP side effect xảy ra trong test. Chứng minh vì sao transaction rollback và `@DirtiesContext` giải quyết hai khía cạnh khác nhau, và external effect cần cleanup riêng.

### Bài tập 6 — đo LRU eviction

Đặt `spring.test.context.cache.maxSize=2`, tạo ba config combinations khác nhau trong cùng JVM test process. Quan sát cache debug stats và việc một context cũ bị evicted. Không yêu cầu ba test methods có tên khác nhau nếu chúng vẫn dùng cùng một cache key; phải tạo ba **merged configurations** khác nhau.

## 33. Tài liệu chính thức

1. [Spring Framework 6.2 — Context Caching](https://docs.spring.io/spring-framework/reference/6.2/testing/testcontext-framework/ctx-management/caching.html): static cache, cache-key components, LRU, giới hạn 32, logging và @DirtiesContext.
2. [Spring Framework 6.2 — @DirtiesContext](https://docs.spring.io/spring-framework/reference/6.2/testing/annotations/integration-spring/annotation-dirtiescontext.html): method/class before-after phases, close/evict và hierarchyMode.
3. [Spring Framework 6.2 — Context Hierarchies](https://docs.spring.io/spring-framework/reference/6.2/testing/testcontext-framework/ctx-management/hierarchies.html): contexts parent/child và invalidation.
4. [Spring Framework 6.2 — Context Configuration with Dynamic Property Sources](https://docs.spring.io/spring-framework/reference/6.2/testing/testcontext-framework/ctx-management/dynamic-property-sources.html): dynamic suppliers và caveat khác values giữa subclasses.
5. [Spring Framework 6.2 — Transaction Management in Tests](https://docs.spring.io/spring-framework/reference/6.2/testing/testcontext-framework/tx.html): test-managed transaction rollback và giới hạn.
6. [Spring Framework 6.2.18 Javadoc — DirtiesContext](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/test/annotation/DirtiesContext.html): methodMode, classMode, hierarchyMode enum contracts.
7. [Spring Framework 6.2.18 Javadoc — SpringJUnitConfig](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/test/context/junit/jupiter/SpringJUnitConfig.html): JUnit Jupiter integration.
8. [Spring Framework 6.2.18 Javadoc — SpringExtension](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/test/context/junit/jupiter/SpringExtension.html): JUnit5 extension integration.
9. [Spring Framework 6.2 — Bean Overriding in Tests](https://docs.spring.io/spring-framework/reference/6.2/testing/testcontext-framework/bean-overriding.html): @TestBean/@MockitoBean and context customizers.
10. [JUnit Jupiter User Guide 5.11.4](https://docs.junit.org/5.11.4/user-guide/): @TestMethodOrder, @Order, @BeforeEach, @Execution và test instance lifecycle.
11. [Gradle Java Testing](https://docs.gradle.org/current/userguide/java_testing.html): Gradle Test task, forks, test reports.

**Kết luận:** Spring TestContext **tái sử dụng ApplicationContext qua static cache** khi merged configuration key tương đương. Điều này tối ưu startup nhưng cũng giữ nguyên singleton objects và mutable state giữa các tests. `@DirtiesContext` **evicts và closes context**, làm lần sử dụng sau phải load một context mới; dùng nó khi context thật sự bị biến đổi/không thể reset an toàn, không dùng như cách reset mọi database/static/external state. Với trạng thái nhỏ có thể kiểm soát, `@BeforeEach`/rollback/fixture cleanup thường vừa nhanh vừa rõ hơn. Chọn đúng before/after method/class modes và hierarchyMode, theo dõi cache hits/misses, đồng thời viết tests độc lập với thứ tự chạy.


</details>

- [Quay lại đầu trang](#back-to-top)
