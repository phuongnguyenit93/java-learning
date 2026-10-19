<a id="back-to-top"></a>

# Kiểm thử

## Menu
- [Kiểm thử đường thành công và đường thất bại theo hợp đồng](#exception-testing-001)
- [Khẳng định đúng loại ngoại lệ thay vì chỉ khẳng định có lỗi](#exception-testing-002)
- [Kiểm tra message, thuộc tính ngữ cảnh và cause khi phù hợp](#exception-testing-003)
- [Kiểm tra suppressed exception và thứ tự đóng tài nguyên](#exception-testing-004)
- [Kiểm tra trạng thái đối tượng sau khi phương thức thất bại](#exception-testing-005)
- [Tạo fake resource để mô phỏng lỗi khởi tạo hoặc close](#exception-testing-006)
- [Kiểm thử finally không che lỗi và không thay đổi kết quả ngoài ý muốn](#exception-testing-007)
- [Kiểm thử ngoại lệ qua Future và CompletableFuture](#exception-testing-008)
- [Kiểm thử ngắt bằng phối hợp luồng xác định thay vì chỉ sleep](#exception-testing-009)
- [Phân biệt lỗi của mã đang kiểm thử với lỗi trong phần chuẩn bị dữ liệu](#exception-testing-010)

## <a id="exception-testing-001">Kiểm thử đường thành công và đường thất bại theo hợp đồng</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử đường thành công và đường thất bại theo hợp đồng trong Java

Một phương thức Java không chỉ có *đường thành công* (*happy path*); hợp đồng của nó còn quy định điều gì xảy ra khi đầu vào không hợp lệ, tài nguyên thiếu, I/O lỗi hoặc thao tác bất đồng bộ thất bại. Nếu test chỉ kiểm tra “gọi được và trả kết quả đúng”, những lỗi như ném sai loại exception, mất `cause`, vô tình nuốt lỗi hoặc để trạng thái dở dang rất dễ lọt vào production.

Bài thuộc **Java Core – Exception / Kiểm thử**, dành cho **Java 17+**. Mục tiêu là viết test xác minh **đúng hợp đồng** ở cả hai đường: kết quả trả về và side effect khi thành công; loại lỗi, nội dung quan trọng, cause/suppressed và trạng thái sau lỗi khi thất bại. Các chương trình Java thuần bên dưới biên dịch/chạy trực tiếp bằng `javac`/`java`; phần JUnit 5 được ghi rõ là ví dụ sử dụng thư viện kiểm thử.

## 1. Hợp đồng của phương thức không chỉ là kiểu trả về

Hãy xem API:

```java
int calculateTotal(int unitPrice, int quantity)
```

Một chữ ký như vậy chưa nói hết hợp đồng. Ứng dụng cần quyết định:

- **Điều kiện đầu vào hợp lệ:** ví dụ `unitPrice >= 0`, `quantity > 0`.
- **Kết quả thành công:** tổng tiền được tính thế nào; có giới hạn tràn số không?
- **Điều kiện thất bại:** đầu vào sai; kết quả vượt giới hạn; dependency lỗi.
- **Loại lỗi:** `IllegalArgumentException`, `ArithmeticException`, `IOException`, exception nghiệp vụ...
- **Thông tin lỗi:** message hoặc mã lỗi nào là phần ổn định của API?
- **Trạng thái sau lỗi:** có cập nhật dở dữ liệu, giữ khóa, để mở tài nguyên, đã gửi request ngoài chưa?
- **Tín hiệu đặc biệt:** cancellation, interrupt hoặc timeout nếu API có hỗ trợ.
- **Thứ tự và phạm vi side effect:** điều kiện nào phải được kiểm tra *trước khi* tác động lên trạng thái?

Một test đúng không phải test khiến code đang có “xanh” bằng mọi giá. Nó phải đối chiếu code với **hợp đồng mong muốn** đã được xác định độc lập.

Đối chiếu minh họa:

| Tình huống | Đầu vào/thiết lập | Điều cần xác minh |
|---|---|---|
| Thành công điển hình | `unitPrice=100`, `quantity=2` | Trả `200`, không ném |
| Thành công ở biên hợp lệ | `unitPrice=0`, `quantity=1` | Trả `0` nếu hợp đồng cho phép |
| Thất bại do số lượng | `quantity=0` | Ném `IllegalArgumentException` |
| Thất bại do giá | `unitPrice=-1` | Ném loại lỗi được quy định |
| Tràn số | `unitPrice=Integer.MAX_VALUE`, `quantity=2` | Không âm thầm trả giá trị sai; báo lỗi đúng chính sách |
| Dependency gặp lỗi | Kho dữ liệu giả ném `IOException` | Giữ/biến đổi exception đúng hợp đồng, không báo thành công |
| Cleanup gặp lỗi | Thân công việc và `close()` cùng ném | Lỗi chính được bảo toàn, lỗi đóng nằm ở `Suppressed` nếu dùng TWR |

**Test cả hai đường không nhất thiết nghĩa là số lượng case 50/50.** Số test phải theo **các nhánh và cam kết của API**, đặc biệt các ranh giới có rủi ro cao.

## 2. Đường thành công: cần khẳng định điều gì?

Test happy path phải **khẳng định kết quả**, không chỉ “không xảy ra exception”. Tùy phương thức, hãy kiểm tra:

1. Giá trị trả về hoặc dữ liệu được tạo ra.
2. Trạng thái đối tượng sau thao tác.
3. Side effect cần xảy ra: lưu dữ liệu, tăng counter, gọi dependency.
4. Side effect **không được xảy ra**: không ghi trùng, không gọi fallback khi không cần.
5. Tính bất biến của đối tượng/đầu vào nếu hợp đồng quy định.
6. Tài nguyên được đóng hoặc chuyển quyền sở hữu đúng ranh giới.

Ví dụ test quá yếu:

```java
service.createOrder(validRequest);
```

Đây chỉ là mảnh code minh họa. Nó không chứng minh đơn hàng đã được tạo đúng số tiền hay đã được lưu. Test cần đọc kết quả hoặc kiểm tra trạng thái quan sát được:

```java
Order order = service.createOrder(validRequest);
assertEquals(expectedTotal, order.total());
```

Đây là **ví dụ tích hợp JUnit**, giả định các kiểu `service`, `Order`, `validRequest`, `expectedTotal` đã được định nghĩa và có `assertEquals` từ JUnit. Trong test thực, cần kiểm tra trạng thái quan trọng thay vì lặp lại chính thuật toán trong expected value; nếu viết cùng một công thức sai cả trong production lẫn test, test vẫn có thể xanh.

Một nguyên tắc hữu ích: **mỗi assertion phải trả lời một cam kết của hợp đồng**. Đừng assert mọi chi tiết nội bộ của implementation, vì refactor đúng vẫn phải giữ nguyên hợp đồng công khai.

## 3. Đường thất bại: kiểm tra nhiều hơn việc có exception

Trên failure path, các lớp kiểm tra thường gồm:

- **Exception type:** API phải ném loại đã hứa, không ném một `NullPointerException` tình cờ vì bug.
- **Message hoặc mã lỗi:** chỉ kiểm tra phần ổn định, có ý nghĩa đối với caller.
- **Cause:** khi tầng cao bọc exception, lỗi gốc còn nằm trong `getCause()` không?
- **Suppressed:** nếu cleanup cũng thất bại, có giữ được lỗi thứ cấp không?
- **Không có kết quả thành công giả:** không trả giá trị mặc định khiến caller tưởng thao tác đã hoàn tất.
- **Không có side effect cấm:** validation sai không làm trừ tiền, ghi dở dữ liệu hoặc đổi state sai.
- **Tài nguyên được giải phóng:** `close`, `unlock`, rollback hoặc release theo hợp đồng.
- **Lỗi chỉ xuất hiện trong đúng phạm vi:** không để một exception từ dependency khác tình cờ làm test “đạt”.

Ví dụ test yếu:

```java
assertThrows(Exception.class, () -> repository.load("bad-id"));
```

Mảnh này là cú pháp JUnit 5, không phải chương trình chạy độc lập. Nó có thể chấp nhận rất nhiều lỗi không mong muốn. Nếu hợp đồng chỉ cho `IllegalArgumentException` khi mã đầu vào không hợp lệ, hãy dùng:

```java
IllegalArgumentException error = assertThrows(
        IllegalArgumentException.class,
        () -> repository.load("bad-id"));
```

Sau đó kiểm tra message/mã lỗi quan trọng và nếu có state, kiểm tra state không bị thay đổi.

`assertThrows(Expected.class, executable)` của JUnit 5 chấp nhận cả **subclass của loại expected**. Khi hợp đồng thực sự yêu cầu đúng class cụ thể, hãy xem xét `assertThrowsExactly` (nếu phiên bản JUnit hỗ trợ) hoặc so sánh `error.getClass()`.

## 4. Ví dụ Java 17 độc lập: kiểm thử hợp đồng đầu vào và kết quả

Tạo tệp `PriceContractTest.java`. Đây là một bộ test thuần JDK với `main`, không cần JUnit.

```java
public class PriceContractTest {
    static int total(int price, int quantity) {
        if (price < 0) {
            throw new IllegalArgumentException("price must be >= 0");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }
        return Math.multiplyExact(price, quantity);
    }

    static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    static void expectIllegalArgument(
            Runnable action, String expectedMessage) {
        try {
            action.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            check(expectedMessage.equals(e.getMessage()),
                    "Wrong message: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        check(total(100, 2) == 200, "Wrong normal total");
        System.out.println("success: normal");

        check(total(0, 1) == 0, "Wrong boundary total");
        System.out.println("success: boundary");

        expectIllegalArgument(() -> total(-1, 2),
                "price must be >= 0");
        System.out.println("failure: negative price");

        expectIllegalArgument(() -> total(10, 0),
                "quantity must be > 0");
        System.out.println("failure: invalid quantity");

        try {
            total(Integer.MAX_VALUE, 2);
            throw new AssertionError("Expected ArithmeticException");
        } catch (ArithmeticException expected) {
            System.out.println("failure: overflow");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac PriceContractTest.java
java PriceContractTest
```

Đầu ra:

```text
success: normal
success: boundary
failure: negative price
failure: invalid quantity
failure: overflow
```

Cách test hoạt động:

- Hai trường hợp thành công kiểm tra **kết quả cụ thể**.
- Hai trường hợp invalid input kiểm tra đúng **loại lỗi** và message do chính API quy định.
- `Math.multiplyExact` phát hiện overflow, cho phép test xác nhận lỗi thay vì âm thầm nhận một số sai.
- `check` ném `AssertionError` nếu invariant bị vi phạm. Đây là bộ test **chủ động kiểm tra**, không phụ thuộc việc chạy JVM với cờ `-ea`.
- Trong `expectIllegalArgument`, nếu phương thức không ném, chương trình ném `AssertionError`; nó **không bị bắt nhầm** bởi `catch (IllegalArgumentException)`.

Lưu ý: `assert` của ngôn ngữ Java có thể bị tắt mặc định nếu không dùng `-ea`. Với một bộ test thuần `main` như trên, `if (!condition) throw new AssertionError(...)` đảm bảo test vẫn thực hiện assertion. Khi dùng JUnit, assertions của JUnit không phụ thuộc cờ `-ea`.

## 5. Chọn bộ giá trị đại diện: bình thường, biên và không hợp lệ

Test thành công/thất bại nên thiết kế theo **phân vùng tương đương** (*equivalence partitioning*) và **giá trị biên** (*boundary value analysis*).

Với quy tắc `quantity > 0`:

| Nhóm | Input ví dụ | Kỳ vọng |
|---|---:|---|
| Giá trị hợp lệ nhỏ nhất | `1` | Thành công |
| Giá trị hợp lệ phổ biến | `5` | Thành công |
| Biên ngay bên ngoài hợp lệ | `0` | `IllegalArgumentException` |
| Giá trị âm | `-1` | `IllegalArgumentException` |
| Giá trị dương rất lớn | `Integer.MAX_VALUE` | Theo giới hạn và khả năng overflow của phép tính |

Với `String` không được `null` và không được blank, nên phân biệt:

- `null`: lỗi null theo hợp đồng.
- `""`: chuỗi rỗng.
- `"   "`: chỉ chứa khoảng trắng.
- `"A"`: hợp lệ tối thiểu.
- `" A "`: có trim/normalize hay không tùy hợp đồng.
- Ký tự Unicode: xử lý theo yêu cầu thực tế, không giả định chỉ có ASCII.

Đừng tự đặt kỳ vọng mà chưa xác định policy. Một số API cho phép chuỗi rỗng nhưng cấm `null`, một số cho phép số `0`, một số xem “không tìm thấy” là `Optional.empty()` thay vì exception. Test phải **phản ánh cam kết được công bố**, không áp một loại lỗi chung cho mọi API.

## 6. Với JUnit 5: viết test success và failure rõ ràng

JUnit Jupiter là công cụ phổ biến để tổ chức unit test Java. Các ví dụ trong mục này **cần dependency JUnit Jupiter phù hợp và test runner của Maven/Gradle/IDE**; không phải các tệp có thể chạy chỉ với `javac` thuần.

Giả sử tệp production `PricePolicy.java`:

```java
public final class PricePolicy {
    public int total(int price, int quantity) {
        if (price < 0) {
            throw new IllegalArgumentException("price must be >= 0");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }
        return Math.multiplyExact(price, quantity);
    }
}
```

Tệp kiểm thử `PricePolicyTest.java`:

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PricePolicyTest {
    private final PricePolicy policy = new PricePolicy();

    @Test
    void returnsTotalForValidInput() {
        int result = assertDoesNotThrow(() -> policy.total(100, 2));
        assertEquals(200, result);
    }

    @Test
    void acceptsMinimumValidQuantity() {
        assertEquals(0, policy.total(0, 1));
    }

    @Test
    void rejectsNegativePrice() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> policy.total(-1, 2));

        assertEquals("price must be >= 0", error.getMessage());
    }

    @Test
    void rejectsZeroQuantity() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> policy.total(10, 0));

        assertEquals("quantity must be > 0", error.getMessage());
    }

    @Test
    void detectsOverflow() {
        assertThrows(ArithmeticException.class,
                () -> policy.total(Integer.MAX_VALUE, 2));
    }
}
```

Phân tích:

- `assertDoesNotThrow(...)` làm rõ mong muốn không có exception; **vẫn phải assert giá trị** trả về. Chỉ “không ném lỗi” chưa đủ để chứng minh hợp đồng tính toán.
- `assertThrows` chỉ đạt nếu executable **thực sự ném exception phù hợp**.
- Test nhận chính đối tượng `error`, nhờ đó có thể kiểm tra message/cause/suppressed hoặc error code.
- Mỗi test có tên thể hiện tình huống và kỳ vọng. Nếu một test thất bại, tên phương thức giúp tìm nhanh cam kết bị vi phạm.

Thực thi trong dự án Maven thường dùng `mvn test`; trong dự án Gradle được cấu hình JUnit Platform là `gradle test` hoặc `./gradlew test`. Những lệnh này yêu cầu dự án có build file, dependency JUnit và cấu hình test thích hợp. Không nên chạy tệp JUnit bằng `java PricePolicyTest` như ví dụ có `main`.

**Không cần thêm `assertDoesNotThrow` ở mọi test happy path**: một lời gọi ném ngoại lệ bất ngờ thường cũng khiến test JUnit thất bại. Hãy dùng khi nó làm hợp đồng rõ hơn hoặc cần lấy kết quả trong lambda.

## 7. Exception type: khi nào kiểm tra subtype, khi nào kiểm tra chính xác?

Giả sử hợp đồng ghi “ném `IllegalArgumentException` nếu input không hợp lệ”. `assertThrows(IllegalArgumentException.class, ...)` có thể chấp nhận subclass, phù hợp nếu hợp đồng cho phép phân lớp riêng.

Nếu contract ghi rõ **đúng một loại lỗi**, có thể dùng:

```java
IllegalArgumentException error = assertThrowsExactly(
        IllegalArgumentException.class,
        () -> policy.total(-1, 1));
```

Đây là **mảnh code JUnit**; cần import `assertThrowsExactly` và biến `policy` đã có. Có thể dùng `assertEquals(IllegalArgumentException.class, error.getClass())` nếu cần kiểm tra chính xác sau `assertThrows`.

Đừng test quá hẹp nếu thư viện công khai cho phép một họ exception. Ví dụ, hợp đồng JDBC hoặc HTTP client có thể hứa checked exception gốc hoặc subclass tùy driver; test ép đúng class implementation có thể hỏng khi nâng dependency mà hành vi công khai vẫn đúng.

Ngược lại, đừng test quá rộng:

```java
assertThrows(RuntimeException.class, () -> parse("not-a-number"));
```

Câu này có thể xanh vì một `NullPointerException` bất ngờ trong implementation. Nếu contract nêu `NumberFormatException` hoặc `IllegalArgumentException`, hãy test tương ứng. Khi đọc một exception lạ, xác định nó là **lỗi được mong đợi** hay bug bị test vô tình chấp nhận.

## 8. Message và error code: điều gì đáng khóa chặt trong test?

Thông điệp exception thường phục vụ con người chẩn đoán. Có những phần nên test:

- Mã lỗi ổn định, ví dụ `E_INVALID_QUANTITY`.
- Tên ràng buộc hoặc trường sai nếu hợp đồng công khai quy định.
- Giá trị an toàn quan trọng giúp caller hiểu lỗi.
- Thông điệp chính xác **nếu nó được công bố là một phần của API**.

Không nên mặc định kiểm tra **từng ký tự** của message do JDK, driver hoặc thư viện bên ngoài tạo. Message có thể đổi do JDK version, locale, driver, định dạng hoặc nâng dependency. Ví dụ test nguyên văn thông điệp `NumberFormatException` của JDK dễ gây brittle test khi API không hứa format đó.

Ví dụ JUnit minh họa:

```java
IllegalArgumentException error = assertThrows(
        IllegalArgumentException.class,
        () -> service.applyQuantity(-1));

assertTrue(error.getMessage().contains("quantity"));
```

Đây là mảnh tích hợp, cần `service` và import `assertTrue`, `assertThrows`. `contains("quantity")` chỉ phù hợp khi contract thực sự yêu cầu thông tin ấy; không phải phép kiểm tra tự động tốt hơn mọi trường hợp.

Nếu ứng dụng cần lỗi có thể xử lý bằng máy, ưu tiên **exception class, enum error code hoặc field riêng** thay vì bắt người gọi parse message. Test mã lỗi ổn định thường bền vững hơn test một câu chữ có thể được dịch.

## 9. Kiểm tra chuỗi nguyên nhân với `getCause()`

Một service có thể bọc lỗi tầng thấp thành exception có nghĩa ở tầng nghiệp vụ:

```java
try {
    return repository.load(id);
} catch (IOException e) {
    throw new IllegalStateException("Không tải được hồ sơ", e);
}
```

Đây là **mảnh mã minh họa** trong phương thức có các biến được định nghĩa. Hợp đồng thất bại có thể yêu cầu:

1. Caller nhận `IllegalStateException`.
2. Message của wrapper diễn đạt ngữ cảnh tải hồ sơ.
3. `getCause()` vẫn là `IOException`, giữ thông tin nguyên nhân.
4. Không trả kết quả thành công giả.
5. Không log lặp ở từng tầng; handler cuối xử lý tùy chính sách.

### Chương trình Java 17 độc lập: kiểm tra cause và trường hợp thành công

Tệp `CauseContractTest.java`:

```java
import java.io.IOException;

public class CauseContractTest {
    @FunctionalInterface
    interface Repository {
        String read() throws IOException;
    }

    static String load(Repository repository) {
        try {
            return repository.read();
        } catch (IOException e) {
            throw new IllegalStateException("load failed", e);
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        check("OK".equals(load(() -> "OK")), "Success path failed");
        System.out.println("success = OK");

        IOException root = new IOException("disk offline");
        try {
            load(() -> {
                throw root;
            });
            throw new AssertionError("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            check("load failed".equals(e.getMessage()),
                    "Wrong wrapper message");
            check(e.getCause() == root, "Original cause lost");
            System.out.println("outer = "
                    + e.getClass().getSimpleName());
            System.out.println("cause = "
                    + e.getCause().getClass().getSimpleName());
        }
    }
}
```

Chạy:

```bash
javac CauseContractTest.java
java CauseContractTest
```

Đầu ra:

```text
success = OK
outer = IllegalStateException
cause = IOException
```

Dependency được thay bằng một **fake** theo interface `Repository` đơn giản, không cần database thật. Đường thành công trả `"OK"`; đường thất bại ném **chính đối tượng `root`** để kiểm tra wrapper giữ đúng identity trong `getCause()`.

Nếu không kiểm tra cause, một refactor sai kiểu:

```java
throw new IllegalStateException("load failed");
```

có thể vẫn qua test loại lỗi và message nhưng **mất nguyên nhân gốc**. Đây là lý do test hợp đồng exception cần có chiều sâu hơn `assertThrows`.

## 10. Kiểm tra trạng thái và side effect sau thất bại

Một API có side effect không chỉ cam kết “ném đúng exception”. Nó còn có thể cam kết:

- Validation chạy **trước** khi ghi dữ liệu.
- Khi dependency thất bại, bộ đếm thành công không tăng.
- Một transaction thất bại phải rollback theo quy tắc.
- Resource được đóng dù thân xử lý có exception.
- Một lần retry không nhân đôi giao dịch đã thành công trước đó.

Ví dụ Java thuần `MutationContractTest.java`:

```java
public class MutationContractTest {
    static final class Wallet {
        private int balance;

        Wallet(int initialBalance) {
            balance = initialBalance;
        }

        int balance() {
            return balance;
        }

        void debit(int amount) {
            if (amount <= 0) {
                throw new IllegalArgumentException("invalid amount");
            }
            if (amount > balance) {
                throw new IllegalStateException("insufficient funds");
            }
            balance -= amount;
        }
    }

    static void check(boolean ok, String message) {
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        Wallet wallet = new Wallet(100);
        wallet.debit(40);
        check(wallet.balance() == 60, "Successful debit is wrong");
        System.out.println("success balance = " + wallet.balance());

        try {
            wallet.debit(-1);
            throw new AssertionError("Expected invalid amount");
        } catch (IllegalArgumentException expected) {
            check(wallet.balance() == 60,
                    "Balance changed after validation error");
            System.out.println("invalid balance = " + wallet.balance());
        }

        try {
            wallet.debit(70);
            throw new AssertionError("Expected insufficient funds");
        } catch (IllegalStateException expected) {
            check(wallet.balance() == 60,
                    "Balance changed after insufficient funds");
            System.out.println("insufficient balance = " + wallet.balance());
        }
    }
}
```

Chạy:

```bash
javac MutationContractTest.java
java MutationContractTest
```

Kết quả:

```text
success balance = 60
invalid balance = 60
insufficient balance = 60
```

Sau lần debit thành công, số dư còn 60. Hai lần debit thất bại phải **giữ nguyên số dư**. Nếu implementation cập nhật số dư rồi mới ném, test sẽ thất bại ngay cả khi loại exception vẫn đúng.

Đây là ví dụ đồng bộ thuần bộ nhớ; **không** chứng minh cách triển khai ví điện tử thực tế an toàn nhiều luồng hoặc bảo đảm transaction giữa nhiều hệ thống. Với database, cần dùng ranh giới transaction và fixture phù hợp, test cả rollback và commit theo hợp đồng.

## 11. Resource cleanup: kiểm tra cả primary và `Suppressed`

Một trường hợp dễ bị bỏ sót là `try-with-resources`: thân `try` ném lỗi A, còn `close()` ném lỗi B. Theo Java, **A là primary**, B được gắn vào A qua `getSuppressed()`. Nếu code đổi sang `finally` thủ công sai cách, B có thể **che A**.

Tệp Java 17 độc lập `ResourceExceptionContractTest.java`:

```java
import java.io.IOException;

public class ResourceExceptionContractTest {
    static final class Resource implements AutoCloseable {
        private final boolean failOnClose;

        Resource(boolean failOnClose) {
            this.failOnClose = failOnClose;
        }

        @Override
        public void close() throws IOException {
            if (failOnClose) {
                throw new IOException("close failed");
            }
        }
    }

    static String execute(boolean fail) throws Exception {
        try (Resource resource = new Resource(fail)) {
            if (fail) {
                throw new IllegalStateException("body failed");
            }
            return "completed";
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) throws Exception {
        check("completed".equals(execute(false)),
                "Expected successful result");
        System.out.println("success = completed");

        try {
            execute(true);
            throw new AssertionError("Expected primary exception");
        } catch (IllegalStateException e) {
            check("body failed".equals(e.getMessage()),
                    "Primary exception changed");

            Throwable[] suppressed = e.getSuppressed();
            check(suppressed.length == 1,
                    "Expected exactly one suppressed exception");
            check(suppressed[0] instanceof IOException,
                    "Expected IOException in suppressed");
            check("close failed".equals(suppressed[0].getMessage()),
                    "Wrong cleanup message");

            System.out.println("primary = " + e.getMessage());
            System.out.println("suppressed = "
                    + suppressed[0].getMessage());
        }
    }
}
```

Chạy:

```bash
javac ResourceExceptionContractTest.java
java ResourceExceptionContractTest
```

Output:

```text
success = completed
primary = body failed
suppressed = close failed
```

Khi `fail=false`, thân công việc thành công và `close()` không ném; kết quả là `"completed"`. Khi `fail=true`, cả thân xử lý và close đều thất bại. Test xác minh **exception chính không bị ghi đè**, đồng thời lỗi cleanup không bị mất.

Các trường hợp khác đáng kiểm thử nếu API hứa quản lý nhiều tài nguyên:

1. Thân thành công, `close()` thất bại: exception cleanup trở thành primary.
2. Thân lỗi, nhiều `close()` lỗi: có nhiều suppressed theo thứ tự đóng ngược.
3. Constructor tài nguyên thứ hai lỗi: tài nguyên thứ nhất vẫn phải được đóng.
4. Thân `try` return giá trị nhưng close lỗi: caller nhìn thấy lỗi đóng thay vì kết quả thành công.
5. Hàm cleanup thủ công ném unchecked exception: phải xác định precedence theo hợp đồng.

Test đừng chỉ xác minh `close()` được gọi bằng mock; **nếu hợp đồng cần giữ primary**, kiểm tra cả cause/suppressed mới phát hiện mất dữ liệu chẩn đoán.

## 12. Checked exception, unchecked exception và việc chọn ranh giới test

Hai đường lỗi hay bị trộn lẫn:

- **Checked exception**, ví dụ `IOException`: phương thức phải khai báo `throws` hoặc bắt. Test nên xác nhận `IOException` được propagate/bọc đúng hợp đồng.
- **Unchecked exception**, ví dụ `IllegalArgumentException`, `IllegalStateException`: vẫn cần test khi loại lỗi là một phần của contract, dù compiler không bắt buộc khai báo.

Không nên coi checked exception là “lỗi quan trọng” còn unchecked exception chỉ là bug. Một `IllegalArgumentException` có thể là phản hồi dự kiến của validation; một `IOException` có thể do dependency tạm thời; phân loại dựa theo **ý nghĩa nghiệp vụ**.

Nếu API quy định `IOException` từ repository phải đi xuyên qua service, test kỳ vọng checked exception. Nếu service cố ý chuyển sang exception nghiệp vụ có cause, test cả wrapper và cause. Nhưng test **không nên tự đặt quy tắc bọc lỗi** chỉ vì muốn code nhất quán: nó phải dựa trên contract mà caller thực sự sử dụng.

Với một API `Optional<T>` hoặc kết quả `Result<T>`, trường hợp “không tìm thấy” có thể là **đường bình thường** không ném exception. Khi đó failure test của hệ thống có thể kiểm tra `Optional.empty()` chứ không phải `assertThrows`. Dùng exception cho điều kiện nào phải được quyết định ở API, rồi mới viết test.

## 13. Exception từ `Future` và `CompletableFuture`

Khi một tác vụ bất đồng bộ lỗi, loại exception quan sát ở **caller** có thể khác lỗi ném từ **worker**:

- `Future.get()` thường ném `ExecutionException` nếu task thất bại; lỗi gốc ở `getCause()`.
- `CompletableFuture.join()` thường ném `CompletionException` khi stage hoàn thành ngoại lệ; nguyên nhân ở `getCause()`.
- `CompletableFuture.get()` dùng `ExecutionException` cho exceptional completion thông thường.
- Khi caller đang chờ `get()` và **chính caller bị interrupt**, có thể nhận `InterruptedException`; điều này không tự nói rằng worker đã lỗi/hủy.
- Một Future đã hủy có thể khiến `get()` ném `CancellationException`; trạng thái canceled không bảo đảm worker đã dọn dẹp xong.
- Timeout của `get(timeout, unit)` có thể ném `TimeoutException` nhưng không tự hủy task.

Do đó test cần **nhắm đúng hợp đồng mà caller quan sát**, không nhầm `IOException` bên trong `Callable` với `IOException` được ném trực tiếp từ `get()`.

### Ví dụ Java 17 độc lập: hai đường hoàn thành của `CompletableFuture`

Tệp `AsyncExceptionContractTest.java`:

```java
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CancellationException;

public class AsyncExceptionContractTest {
    static void check(boolean ok, String message) {
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        CompletableFuture<String> success =
                CompletableFuture.completedFuture("ok")
                        .thenApply(String::toUpperCase);

        check("OK".equals(success.join()),
                "Wrong successful result");
        System.out.println("success = " + success.join());

        IOException root = new IOException("offline");
        CompletableFuture<String> failed =
                CompletableFuture.failedFuture(root);

        try {
            failed.join();
            throw new AssertionError("Expected CompletionException");
        } catch (CompletionException e) {
            check(e.getCause() == root, "Cause was lost");
            System.out.println("cause = "
                    + e.getCause().getClass().getSimpleName());
        }

        CompletableFuture<String> canceled = new CompletableFuture<>();
        check(canceled.cancel(false), "Cancel should succeed");

        try {
            canceled.join();
            throw new AssertionError("Expected CancellationException");
        } catch (CancellationException expected) {
            check(canceled.isCancelled(), "Future must be canceled");
            System.out.println("canceled = true");
        }
    }
}
```

Chạy:

```bash
javac AsyncExceptionContractTest.java
java AsyncExceptionContractTest
```

Kết quả:

```text
success = OK
cause = IOException
canceled = true
```

Test dùng các Future đã hoàn thành hoặc được hủy trực tiếp, nên output ổn định và **không phụ thuộc lịch thực thi các thread**. Nó kiểm tra hợp đồng **quan sát kết quả** của `CompletableFuture`, không thử chứng minh hiệu quả hủy một worker đang chạy.

Nếu cần test worker thật sự nhận interrupt, hãy dùng `CountDownLatch`, `Future` hoặc cơ chế đồng bộ thích hợp; tránh ngủ một thời lượng ngẫu nhiên rồi hi vọng worker đã chạy đến đúng chỗ.

## 14. Test bất đồng bộ mà không tạo race/flaky test

Anti-pattern thường gặp:

```java
executor.submit(() -> updateState());
Thread.sleep(100);
assertEquals("DONE", state.get());
```

Đây là **mảnh mã minh họa JUnit** (chưa độc lập), với `executor`, `updateState`, `state` đã có. `sleep(100)` không bảo đảm task sẽ hoàn thành trong 100 ms trên CI. Test có thể qua trên máy cá nhân nhưng thất bại dưới tải.

Nếu cần đợi kết quả, dùng đồng bộ có deadline:

```java
Future<String> future = executor.submit(() -> "DONE");
String result = future.get(2, TimeUnit.SECONDS);
assertEquals("DONE", result);
```

Đây là **mảnh mã tích hợp**, cần `ExecutorService`, `Future`, `TimeUnit`, JUnit imports và quy tắc shutdown. Deadline giúp test tránh treo vô hạn, nhưng không biến mã bất đồng bộ sai thành đúng.

Đối với scenario phức tạp, dùng `CountDownLatch` để ép thứ tự cần thiết; khi test xong hãy giải phóng latch trong `finally`, đóng executor và xử lý interrupt phù hợp. Không gọi `Thread.stop()` để “dọn” một test bị treo.

**Timeout chỉ là hàng rào chống treo**, không phải bằng chứng correctness. Test vẫn phải assert trạng thái kết quả, exception type và các side effect đã cam kết.

## 15. Không test failure bằng cách tạo lỗi không liên quan

Một test như sau có thể *giả xanh*:

```java
assertThrows(NullPointerException.class,
        () -> service.process(null));
```

Nếu hợp đồng thực tế yêu cầu `IllegalArgumentException`, test trên không đúng kỳ vọng. Hoặc test có thể vô tình ném `NullPointerException` ngay ở bước dựng fixture, không phải trong `service.process(...)`.

Biện pháp:

1. Chuẩn bị fixture **trước** lambda kiểm tra lỗi.
2. Trong lambda, chỉ đặt **hành động mà hợp đồng yêu cầu sẽ thất bại**.
3. Kiểm tra đúng exception type, mã lỗi/cause quan trọng.
4. Xác minh side effect và state sau thất bại.
5. Nếu có nhiều lỗi có thể xảy ra, cô lập precondition để chỉ một điều kiện sai.

Ví dụ JUnit phù hợp hơn:

```java
OrderRequest request = new OrderRequest("A-1", -1);
IllegalArgumentException error = assertThrows(
        IllegalArgumentException.class,
        () -> service.createOrder(request));

assertEquals("INVALID_QUANTITY", error.getMessage());
```

Đây là **mảnh minh họa** với `OrderRequest`/`service` do hệ thống định nghĩa. Hãy đặt `request` trước `assertThrows`, tránh việc khởi tạo fixture bên trong lambda cũng có thể là nơi gây exception. Message chính xác chỉ nên test nếu `INVALID_QUANTITY` là cam kết ổn định.

## 16. Failure path không được tạo thành công giả

Một lỗi nghiêm trọng là code bắt exception rồi trả mặc định:

```java
try {
    return repository.read();
} catch (IOException e) {
    return "";
}
```

Đây là mảnh mã minh họa. Nó chỉ đúng nếu hợp đồng thật sự quy định `""` là kết quả fallback hợp lệ và caller phân biệt được nó với dữ liệu bình thường. Nếu hợp đồng yêu cầu báo lỗi, đây là bug: exception đã bị **nuốt**.

Test phải tìm ra hành vi sai bằng cách:

- Cho fake repository ném `IOException`.
- Xác minh service propagate `IOException` hoặc bọc đúng loại exception.
- Khẳng định **không** có kết quả thành công.
- Kiểm tra cause còn nguyên nếu có chuyển loại lỗi.

Tương tự, `return` trong `finally` có thể che exception đang ném. Một test failure path có kiểm tra chính xác loại lỗi sẽ phát hiện tình huống chương trình trả kết quả bình thường khi lẽ ra phải ném.

## 17. Test exception message/cause sau refactor

Refactor có thể giữ happy path nhưng làm hỏng failure path theo nhiều cách:

- Bỏ `throw`/chuyển sang trả `null`.
- Thay `IOException` bằng `IllegalStateException` mà quên cause.
- Tạo exception mới với message chung chung `"failed"` thay vì mã lỗi ổn định.
- Đổi `try-with-resources` sang `try-finally` khiến close exception che primary.
- Thay `throw e` bằng `throw new RuntimeException(e.getMessage())`, làm mất stack cause.
- Bắt `InterruptedException` rồi bỏ qua, khiến tác vụ vẫn tiếp tục.
- Catch `Exception` quá rộng, chuyển các lỗi lập trình thành fallback thành công.

Vì vậy, khi thay đổi code liên quan exception, hãy chạy lại **ít nhất một test success và các test failure tương ứng từng hợp đồng bị ảnh hưởng**. Phần regression suite nên tập trung vào hành vi có thể quan sát từ caller, không phụ thuộc quá sâu vào vị trí `catch` trong implementation.

## 18. Unit test, integration test và nơi chịu trách nhiệm

Không phải mọi hợp đồng có thể được chứng minh bằng unit test giả lập.

| Loại test | Kiểm tra tốt nhất |
|---|---|
| Unit test | Validation, exception type, message/code ổn định, cause wrapping, lựa chọn fallback, thay đổi state cục bộ |
| Integration test | Transaction rollback thật, tài nguyên được đóng, SQL/HTTP error mapping, adapter giữ cause |
| Contract test giữa service | HTTP status/body ổn định, mã lỗi công khai, retry semantics ở ranh giới |
| End-to-end test | Hành vi từ góc nhìn người dùng; operation thành công/thất bại qua nhiều thành phần |

Ví dụ, mock một `Connection.rollback()` chỉ chứng minh code **đã gọi** rollback trong test cụ thể; chưa chứng minh database thực sự rollback. Ngược lại, integration test với database thật đắt hơn nhưng xác nhận semantics end-to-end của resource/transaction.

Khi cần test exception từ một dependency, fake/stub có thể chủ động ném lỗi xác định để tái lập dễ dàng. Đừng dùng network thật để tạo timeout trong một unit test nếu không bắt buộc; nó khiến test chậm và flaky.

## 19. Phân biệt exception dự kiến với lỗi test hạ tầng

Khi test thất bại, đọc tín hiệu:

- **Assertion failure:** hành vi quan sát khác hợp đồng; xem message của assertion và input.
- **Exception chưa dự kiến:** test/setup hoặc code production ném lỗi khác; đọc stack và cause.
- **Test timeout:** có thể deadlock, chờ quá lâu, môi trường quá tải hoặc fixture không giải phóng latch. Không vội tăng timeout nếu có logic sai.
- **Failure do môi trường:** thiếu file, DB chưa chạy, timezone/locale khác, permission hoặc dependency chưa cấu hình.
- **Flaky test:** kết quả phụ thuộc thread scheduling, thời gian thật, random hoặc shared state.

Một test tốt phải **thất bại khi contract bị vi phạm** và **ổn định khi contract được đáp ứng**. Không nên biến test đỏ thành xanh bằng cách catch quá rộng hoặc bỏ assertion quan trọng.

### Message của JDK/stack trace không nên là oracle cứng

Nếu test in toàn bộ `printStackTrace()` rồi đối chiếu 100% text, nó dễ khác do số dòng, tên file, wrapper hoặc bản JDK. Hãy dùng API như `getCause()`, `getSuppressed()` và kiểm tra loại lỗi. Chỉ lock exact message của code bạn kiểm soát nếu có lý do hợp đồng rõ ràng.

## 20. Checklist trước khi kết luận một exception contract đã được kiểm thử đủ

- **Success:** có input điển hình, biên hợp lệ và assert kết quả/side effect đúng không?
- **Invalid input:** mỗi nhóm điều kiện sai có test exception type phù hợp không?
- **No exception:** test success có vô tình chỉ gọi hàm mà không kiểm tra kết quả không?
- **Exception type:** có bắt quá rộng (`Exception`/`RuntimeException`) khiến bug được chấp nhận không?
- **Message/code:** đang test thông tin hợp đồng ổn định hay chi tiết do JDK/driver tự sinh?
- **Cause:** exception được bọc có giữ đúng nguyên nhân gốc không?
- **Suppressed:** lỗi cleanup có được giữ đúng và không che primary không?
- **State:** sau failure có tránh cập nhật dở hoặc side effect cấm không?
- **Async:** caller thấy `CompletionException`, `ExecutionException`, `CancellationException` hoặc `InterruptedException` đúng bối cảnh không?
- **Determinism:** test dùng fake/latch/deadline thay vì sleep tùy tiện không?
- **Isolation:** mỗi test có fixture sạch, không phụ thuộc thứ tự chạy test khác không?
- **Observability:** test assertion, tên test và failure message có giúp tìm cam kết bị vi phạm không?

**Ghi nhớ:** test exception theo hợp đồng nghĩa là **xác minh cả cách API thành công lẫn cách nó từ chối/thất bại**, kể cả kết quả, loại ngoại lệ, cause, suppressed và trạng thái hệ thống sau lỗi. Một bộ test đủ mạnh không chỉ chứng minh “có exception”, mà chứng minh **đúng exception ở đúng tình huống và không có tác dụng phụ sai**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-testing-002">Khẳng định đúng loại ngoại lệ thay vì chỉ khẳng định có lỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Khẳng định đúng loại ngoại lệ thay vì chỉ khẳng định có lỗi

## 1. Vì sao “có lỗi” không đủ để kết luận test đúng?

Khi một phương thức Java thất bại, exception được ném ra không chỉ là tín hiệu cho biết có vấn đề. **Loại exception** là một phần của hợp đồng giữa phương thức và caller: đầu vào không hợp lệ thường được phân biệt với trạng thái đối tượng sai, lỗi nhập/xuất, lỗi task bất đồng bộ, hủy tác vụ hoặc timeout.

Giả sử một API phân tích cổng mạng được thiết kế theo quy tắc:

- Đầu vào null hoặc blank: **IllegalArgumentException** do thiếu dữ liệu.
- Đầu vào không thể chuyển thành số nguyên: **NumberFormatException**.
- Giá trị số ngoài phạm vi từ 1 tới 65535: **IllegalArgumentException** do vượt giới hạn.
- Giá trị hợp lệ: trả về số cổng.

Test chỉ xác nhận “một exception nào đó đã xuất hiện” có thể PASS dù mã bất ngờ ném **NullPointerException**, **IndexOutOfBoundsException** hoặc một lỗi hệ thống hoàn toàn không thuộc hợp đồng. Một bug mới đã làm thay đổi bản chất thất bại, nhưng test không phát hiện.

**Điểm học tập cốt lõi:** Khi test đường lỗi, cần xác định **đúng tình huống gây lỗi**, **đúng exception class theo hợp đồng**, và khi có ý nghĩa cả **message, cause, suppressed exceptions, trạng thái dữ liệu sau thất bại**. Đừng làm test quá rộng chỉ để nó luôn PASS.

Mục tiêu không phải luôn yêu cầu *runtime class chính xác* trong mọi hệ thống. Có API cam kết cả một **họ exception**, cố ý cho phép các subtype. Điều quan trọng là **chọn assertion khớp contract**, thay vì vô tình bỏ qua sai khác giữa kiểu cha và kiểu con.

## 2. Exception hierarchy ảnh hưởng trực tiếp tới assertion

Java có hệ phân cấp class, trong đó NumberFormatException kế thừa IllegalArgumentException. Một phần cấu trúc như sau:

~~~text
Throwable
├── Error
└── Exception
    ├── IOException
    └── RuntimeException
        ├── IllegalArgumentException
        │   └── NumberFormatException
        ├── IllegalStateException
        └── NullPointerException
~~~

Đây chỉ là những nhánh điển hình, không phải toàn bộ class trong JDK. Do tính kế thừa:

~~~java
// Mã minh họa có thể đặt trong main.
Throwable failure = new NumberFormatException("invalid number");

System.out.println(failure instanceof IllegalArgumentException); // true
System.out.println(failure.getClass() == IllegalArgumentException.class); // false
System.out.println(failure.getClass() == NumberFormatException.class); // true
~~~

So sánh **instanceof** hoặc **Class.isInstance** chấp nhận subclass. So sánh **failure.getClass() == Expected.class** yêu cầu đúng runtime class. Nếu test dự kiến IllegalArgumentException nhưng implementation ném NumberFormatException, cách thứ nhất PASS còn cách thứ hai FAIL.

Điều đó không tự nói một trong hai đúng về nghiệp vụ: nếu API cam kết IllegalArgumentException **hoặc subclass**, assertion assignable phù hợp; nếu API cam kết đúng IllegalArgumentException cho điều kiện null, assertion exact có giá trị phát hiện regression.

## 3. Các mức độ mạnh yếu của kiểm tra exception

| Mức kiểm tra | Ví dụ | Điều nó thực sự xác minh | Điều chưa xác minh |
| --- | --- | --- | --- |
| Exception bất kỳ | Catch Throwable rồi báo PASS | Có một Throwable phát sinh | Contract lỗi đúng hay sai |
| Class cha hoặc subtype | assertThrows(RuntimeException.class, ...) | Có RuntimeException hoặc subtype | Có phải lỗi mong muốn |
| Class cụ thể hoặc subtype | assertThrows(IllegalArgumentException.class, ...) | Thuộc họ IllegalArgumentException | Exact runtime class |
| Class chính xác | assertThrowsExactly(IllegalArgumentException.class, ...) | Runtime class đúng chính xác | Message/cause/state |
| Class và ngữ nghĩa | Exact type + message/cause/suppressed/state | Phần quan trọng của hành vi lỗi | Các nhánh input chưa test |

Một test mong đợi **Exception.class**, **RuntimeException.class** hoặc đặc biệt **Throwable.class** thường quá rộng. Nó có thể chấp nhận bug mới, lỗi setup hoặc thậm chí **AssertionError** của chính test nếu người viết đặt các assertion khác bên trong lambda.

Tuy nhiên, test exact class cũng có thể quá chặt nếu API chủ ý cho phép các subtype khác nhau giữa implementation. **Độ mạnh của assertion phải được cân bằng với mức cam kết của API.**

## 4. Một API ví dụ có nhiều điều kiện thất bại

Xét phương thức sau:

~~~java
// Minh họa trong một class Java.
static int parsePort(String input) {
    if (input == null || input.isBlank()) {
        throw new IllegalArgumentException("port required");
    }
    int port = Integer.parseInt(input);
    if (port < 1 || port > 65535) {
        throw new IllegalArgumentException("port out of range");
    }
    return port;
}
~~~

Theo chính implementation này:

- **parsePort(null)** ném chính xác IllegalArgumentException với message **port required**.
- **parsePort("  ")** cũng ném IllegalArgumentException với cùng message.
- **parsePort("abc")** ném NumberFormatException từ Integer.parseInt.
- **parsePort("70000")** ném IllegalArgumentException do ngoài khoảng cho phép.
- **parsePort("8080")** trả 8080.
- Chuỗi số dài không biểu diễn được trong kiểu int gây NumberFormatException từ Integer.parseInt trước bước kiểm tra phạm vi.

Những loại này là **hợp đồng được chọn cho ví dụ**, không phải quy tắc mọi parser đều bắt buộc làm giống hệt. Khi sản phẩm đổi hợp đồng, phải cập nhật tài liệu và test có chủ đích, không chỉ sửa test để làm chúng PASS.

Nếu ai đó vô ý xóa kiểm tra null, biểu thức **input.isBlank()** có thể ném NullPointerException. Test chỉ đòi “có RuntimeException” vẫn PASS, nhưng test đòi đúng IllegalArgumentException sẽ FAIL và chỉ ra regression.

## 5. Ví dụ Java 17+ hoàn chỉnh, không phụ thuộc JUnit

Chương trình sau cung cấp hai helper đơn giản để minh họa sự khác biệt:

- **expectExact**: chỉ chấp nhận đúng runtime class bằng so sánh getClass.
- **expectAssignable**: chấp nhận class dự kiến hoặc subclass bằng Class.isInstance.

Chương trình còn xác minh message do ứng dụng tạo, exception wrapping với cause, lỗi dọn dẹp nằm trong suppressed và một đường xử lý thành công. Đây là ví dụ học tập; với dự án thật nên dùng test framework như JUnit Jupiter để có reporting và test lifecycle đầy đủ.

Lưu file **ExactExceptionTestDemo.java**:

~~~java
import java.io.IOException;

public class ExactExceptionTestDemo {
    @FunctionalInterface
    interface ThrowingAction {
        void run() throws Exception;
    }

    static final class FailingClose implements AutoCloseable {
        @Override
        public void close() throws IOException {
            throw new IOException("close failed");
        }
    }

    static int parsePort(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("port required");
        }
        int port = Integer.parseInt(input);
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port out of range");
        }
        return port;
    }

    static int loadPort() {
        try {
            return parsePort("not-a-number");
        } catch (NumberFormatException original) {
            throw new IllegalStateException("config invalid", original);
        }
    }

    static void writeWithCloseFailure() throws IOException {
        try (FailingClose resource = new FailingClose()) {
            throw new IOException("write failed");
        }
    }

    static <T extends Throwable> T expectExact(
            Class<T> type, ThrowingAction action) {
        try {
            action.run();
        } catch (Throwable actual) {
            if (actual.getClass() != type) {
                throw new AssertionError("Expected " + type.getSimpleName()
                        + ", got " + actual.getClass().getSimpleName(), actual);
            }
            return type.cast(actual);
        }
        throw new AssertionError("Expected " + type.getSimpleName()
                + ", but no exception was thrown");
    }

    static <T extends Throwable> T expectAssignable(
            Class<T> type, ThrowingAction action) {
        try {
            action.run();
        } catch (Throwable actual) {
            if (!type.isInstance(actual)) {
                throw new AssertionError("Expected a subtype of "
                        + type.getSimpleName(), actual);
            }
            return type.cast(actual);
        }
        throw new AssertionError("Expected " + type.getSimpleName()
                + ", but no exception was thrown");
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        IllegalArgumentException blank = expectExact(
                IllegalArgumentException.class, () -> parsePort(null));
        require("port required".equals(blank.getMessage()), "wrong message");
        System.out.println("blank: " + blank.getClass().getSimpleName());

        NumberFormatException badNumber = expectExact(
                NumberFormatException.class, () -> parsePort("abc"));
        System.out.println("malformed: " + badNumber.getClass().getSimpleName());

        IllegalArgumentException broad = expectAssignable(
                IllegalArgumentException.class, () -> parsePort("abc"));
        System.out.println("broad assertion accepts: "
                + broad.getClass().getSimpleName());

        boolean wrongTypeDetected = false;
        try {
            expectExact(IllegalArgumentException.class, () -> parsePort("abc"));
        } catch (AssertionError mismatch) {
            wrongTypeDetected = mismatch.getMessage().equals(
                    "Expected IllegalArgumentException, got NumberFormatException");
        }
        require(wrongTypeDetected, "exact assertion must reject subclasses");
        System.out.println("exact assertion rejects wrong subtype: true");

        IllegalStateException wrapped = expectExact(
                IllegalStateException.class, ExactExceptionTestDemo::loadPort);
        require(wrapped.getCause() instanceof NumberFormatException,
                "cause must be NumberFormatException");
        System.out.println("wrapped cause: "
                + wrapped.getCause().getClass().getSimpleName());

        IOException primary = expectExact(
                IOException.class, ExactExceptionTestDemo::writeWithCloseFailure);
        require("write failed".equals(primary.getMessage()), "wrong primary");
        require(primary.getSuppressed().length == 1, "missing close error");
        require("close failed".equals(primary.getSuppressed()[0].getMessage()),
                "wrong suppressed");
        System.out.println("primary: " + primary.getMessage());
        System.out.println("suppressed: " + primary.getSuppressed()[0].getMessage());

        require(parsePort("8080") == 8080, "valid input failed");
        System.out.println("valid result: " + parsePort("8080"));
        System.out.println("ALL CHECKS PASSED");
    }
}
~~~

Biên dịch và chạy bằng JDK 17 hoặc mới hơn:

~~~bash
javac --release 17 ExactExceptionTestDemo.java
java ExactExceptionTestDemo
~~~

Đây là stdout thực tế đã được kiểm chứng khi biên dịch và chạy:

~~~text
blank: IllegalArgumentException
malformed: NumberFormatException
broad assertion accepts: NumberFormatException
exact assertion rejects wrong subtype: true
wrapped cause: NumberFormatException
primary: write failed
suppressed: close failed
valid result: 8080
ALL CHECKS PASSED
~~~

Trong helper đào tạo, **catch(Throwable)** dùng để bắt cả loại Throwable sai rồi ném AssertionError; đây **không phải** cách xử lý exception nên sử dụng để lặng lẽ phục hồi trong mã nghiệp vụ. Những kiểm tra dùng AssertionError tường minh nên hoạt động mà không cần bật tùy chọn **-ea** của Java.

## 6. Diễn giải output: assertion quá rộng nhận sai subtype

Hai dòng quan trọng:

~~~text
broad assertion accepts: NumberFormatException
exact assertion rejects wrong subtype: true
~~~

Khi người kiểm thử yêu cầu **IllegalArgumentException hoặc subclass**, NumberFormatException là hợp lệ vì kế thừa IllegalArgumentException. Nhưng khi yêu cầu chính xác **IllegalArgumentException**, cùng exception không đạt assertion.

Điều này giúp ngăn một test “cho tất cả input sai đều phải ném IllegalArgumentException” vô tình bỏ qua yêu cầu phân biệt **malformed number** với **missing input**. Mỗi lỗi có ý nghĩa khác nhau đối với caller. Ngược lại, nếu hợp đồng chỉ cam kết **IllegalArgumentException family**, exact test có thể làm code bị ràng buộc vào một lựa chọn lớp con không cần thiết.

Khi review test, câu hỏi đúng là: **Caller được phép dựa vào class nào?** Nếu caller dùng catch(NumberFormatException) riêng để báo sai định dạng, thay NumberFormatException bằng IllegalArgumentException sẽ phá hợp đồng. Nếu caller chỉ dùng catch(IllegalArgumentException), subtype có thể được phép theo thiết kế.

## 7. JUnit Jupiter assertThrows chấp nhận subclass

Trong JUnit Jupiter 5, **assertThrows(Expected.class, executable)** chấp nhận exception thuộc class Expected **hoặc subtype của nó**. Ví dụ:

~~~java
// JUnit Jupiter, cần dependency JUnit và class ví dụ trong cùng package.
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PortExceptionFamilyTest {
    @Test
    void nonNumericPortBelongsToIllegalArgumentFamily() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> ExactExceptionTestDemo.parsePort("abc"));
    }
}
~~~

Test này PASS vì NumberFormatException là một IllegalArgumentException. Nó **không chứng minh** runtime class chính xác là IllegalArgumentException.

**assertThrows trả về chính Throwable được bắt** dưới dạng kiểu generic phù hợp. Có thể tiếp tục kiểm tra message, cause, suppressed và thông tin liên quan. Đừng chỉ gọi assertThrows rồi bỏ mất thông tin trong một test mà contract còn yêu cầu các thuộc tính khác.

Khi nào kiểm tra họ exception là đủ? Ví dụ một abstraction có thể dùng nhiều implementation đọc file, contract chỉ bảo đảm IOException hoặc subtype. Khi đó assertThrows(IOException.class, ...) có thể đúng hơn việc buộc class chính xác mà implementation không cam kết.

## 8. JUnit Jupiter assertThrowsExactly yêu cầu runtime class đúng

JUnit Jupiter cung cấp **assertThrowsExactly(Class<T>, Executable)** trên các phiên bản hỗ trợ. Phương thức chỉ PASS khi exception có đúng runtime class; một subclass không đủ.

Ví dụ JUnit 5 dùng class từ chương trình Java SE ở mục 5:

~~~java
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExactPortExceptionJUnitTest {
    @Test
    void missingInputHasExactType() {
        IllegalArgumentException failure = assertThrowsExactly(
                IllegalArgumentException.class,
                () -> ExactExceptionTestDemo.parsePort(null));
        assertEquals("port required", failure.getMessage());
    }

    @Test
    void malformedNumberHasExactType() {
        assertThrowsExactly(
                NumberFormatException.class,
                () -> ExactExceptionTestDemo.parsePort("abc"));
    }

    @Test
    void outOfRangeHasExactType() {
        IllegalArgumentException failure = assertThrowsExactly(
                IllegalArgumentException.class,
                () -> ExactExceptionTestDemo.parsePort("70000"));
        assertEquals("port out of range", failure.getMessage());
    }

    @Test
    void validPortWorks() {
        int port = assertDoesNotThrow(
                () -> ExactExceptionTestDemo.parsePort("8080"));
        assertEquals(8080, port);
    }
}
~~~

Đây là test class hợp lệ theo ngữ cảnh **một project đã cấu hình JUnit Jupiter** và chứa **ExactExceptionTestDemo** trong cùng package/classpath. Nó không phải chương trình Java SE có thể chạy chỉ bằng lệnh javac không thêm JUnit dependency. Dùng test runner của build tool dự án.

Nếu phiên bản JUnit Jupiter quá cũ chưa có **assertThrowsExactly**, có thể gọi **assertThrows** rồi kiểm tra **assertEquals(Expected.class, failure.getClass())**. Điều quan trọng là thể hiện rõ đòi hỏi exact type, không phải tên API cụ thể.

**Tránh thắt quá chặt contract:** Dùng assertThrowsExactly khi runtime class thực sự được cam kết. Nếu API chỉ công bố IOException và cho phép subtype, exact IOException có thể khiến test FAIL khi implementation vẫn hợp lệ.

## 9. Trong Java thuần: isInstance khác getClass

Không sử dụng JUnit, có thể phân biệt:

~~~java
// Mã minh họa, failure là Throwable khác null.
boolean acceptsSubtype =
        IllegalArgumentException.class.isInstance(failure);
boolean exactClass =
        failure.getClass() == IllegalArgumentException.class;
~~~

Hoặc:

~~~java
// Mã minh họa.
if (!(failure instanceof IllegalArgumentException)) {
    throw new AssertionError("Wrong exception family");
}

if (failure.getClass() != IllegalArgumentException.class) {
    throw new AssertionError("Wrong exact exception class");
}
~~~

**instanceof** đúng với subtype và không ném NullPointerException khi failure là null; khi failure null, biểu thức instanceof cho false. **failure.getClass()** yêu cầu object khác null. Trong test có framework, assertion API thường cho thông báo lỗi rõ hơn một phép so boolean tự viết.

Không dùng **failure.toString().contains("IllegalArgumentException")** để xác định class. Message và tên class có thể trùng chuỗi mà không phải đúng runtime class; use **getClass()** hoặc kiểu dữ liệu Java.

## 10. Kiểm tra checked và unchecked exceptions đúng hợp đồng

**Checked exceptions** như IOException, SQLException, InterruptedException và ExecutionException thường được compiler buộc caller catch hoặc khai báo. **Unchecked exceptions** như IllegalArgumentException, IllegalStateException, NumberFormatException và CancellationException không có yêu cầu đó.

Nhưng khi kiểm thử, phân loại checked/unchecked **không thay thế** loại lỗi chính xác. Ví dụ:

~~~java
// Mã JUnit minh họa khi api.read() có thể ném IOException.
IOException error = assertThrows(
        IOException.class,
        () -> api.read());
~~~

Assertion này sẽ chấp nhận các subtype IOException. Nếu hợp đồng yêu cầu **FileNotFoundException** chính xác, cần assertion tương ứng; nếu API chỉ cam kết IOException family, assertion như trên đủ ý nghĩa.

Đừng dùng **assertThrows(Exception.class, ...)** để gom InterruptedException (caller bị ngắt), ExecutionException (worker thất bại) và IOException (I/O lỗi) vào cùng một nhánh thành công của test. Các exception này báo những điều kiện khác nhau và cần chính sách khác nhau.

Đừng bao mọi Error vào kiểm thử phục hồi như một loại exception bình thường. Test helper có thể bắt Throwable để xác định loại, nhưng ứng dụng sản phẩm không nên mặc nhiên “khôi phục” từ bất kỳ Error nào.

## 11. Exception wrapping: cần kiểm tra cả outer type lẫn cause

Một tầng service có thể bọc lỗi thấp thành lỗi trừu tượng cấp cao:

~~~java
// Phương thức loadPort từ ví dụ.
static int loadPort() {
    try {
        return parsePort("not-a-number");
    } catch (NumberFormatException original) {
        throw new IllegalStateException("config invalid", original);
    }
}
~~~

Exception mà caller bắt được là **IllegalStateException**; exception ban đầu là **NumberFormatException** ở **getCause()**. Chỉ assert có RuntimeException không xác minh được chính sách wrap và bảo toàn thông tin lỗi.

Mẫu JUnit:

~~~java
// Bên trong @Test, với static imports JUnit tương ứng.
IllegalStateException wrapper = assertThrowsExactly(
        IllegalStateException.class,
        ExactExceptionTestDemo::loadPort);

assertEquals("config invalid", wrapper.getMessage());
assertNotNull(wrapper.getCause());
assertEquals(NumberFormatException.class,
             wrapper.getCause().getClass());
~~~

Nếu developer thay bằng **new IllegalStateException(original.getMessage())**, test outer type vẫn PASS nhưng **assertNotNull(wrapper.getCause())** FAIL, giúp phát hiện việc làm mất exception gốc.

Khi API cam kết cause là một subtype bất kỳ của IOException, thay **assertEquals(IOException.class, cause.getClass())** bằng **assertInstanceOf(IOException.class, cause)** nếu subtype được phép. Trong test nào cũng phải phân biệt **outer exception class**, **cause class**, và **thông điệp nghiệp vụ**.

## 12. Kiểm tra suppressed exceptions: lỗi chính có bị che không?

Trong try-with-resources, khi công việc chính ném exception A và close() ném exception B, Java giữ A là **primary**, B được gắn vào **A.getSuppressed()** nếu suppression được bật.

Đoạn từ chương trình chạy được:

~~~java
static void writeWithCloseFailure() throws IOException {
    try (FailingClose resource = new FailingClose()) {
        throw new IOException("write failed");
    }
}
~~~

Test mạnh hơn việc chỉ assert IOException:

~~~java
// Trong JUnit @Test với imports Assertions phù hợp.
IOException primary = assertThrowsExactly(
        IOException.class,
        ExactExceptionTestDemo::writeWithCloseFailure);

assertEquals("write failed", primary.getMessage());
assertEquals(1, primary.getSuppressed().length);
assertEquals(IOException.class,
             primary.getSuppressed()[0].getClass());
assertEquals("close failed",
             primary.getSuppressed()[0].getMessage());
~~~

Nếu đoạn sản phẩm bị sửa thành finally đóng resource một cách sơ sài, lỗi **close failed** có thể che **write failed**. Test chỉ kiểm tra IOException vẫn PASS, còn bộ kiểm tra primary message và suppressed FAIL. Đây là ví dụ điển hình về việc **đúng class nhưng sai ngữ nghĩa vẫn là bug**.

**Suppressed không phải cause:** Cause diễn đạt quan hệ nguyên nhân/wrapping; suppressed ghi lỗi phụ trong quá trình dọn dẹp. Nếu service còn wrap primary thành exception khác, suppressed có thể nằm trên **wrapper.getCause()** chứ không được sao chép lên wrapper.

## 13. Kiểm tra message: khi nào nên so chính xác?

Message do chính ứng dụng cam kết có thể là một phần hợp đồng có ích cho test:

~~~java
// Minh họa JUnit.
IllegalArgumentException error = assertThrowsExactly(
        IllegalArgumentException.class,
        () -> ExactExceptionTestDemo.parsePort(null));
assertEquals("port required", error.getMessage());
~~~

Ngược lại, một message do JDK tự tạo như chi tiết NumberFormatException có thể khác theo phiên bản JDK/đầu vào. Nếu API chỉ cam kết **NumberFormatException**, không nên biến nguyên văn message JDK thành contract trừ khi cần thiết.

Một thứ tự ưu tiên hợp lý:

1. Đúng exception type hoặc họ exception theo hợp đồng.
2. Đúng message/error code **nếu ứng dụng công bố rõ**.
3. Đúng cause/suppressed nếu cần giữ thông tin lỗi.
4. Đúng trạng thái dữ liệu hoặc resource sau thất bại.
5. Đúng object identity nếu quy tắc là ném lại cùng Throwable.

Một test chỉ dùng **failure.getMessage().contains("error")** chưa chứng minh nguồn hay loại lỗi. Nhiều lỗi khác nhau có cùng từ khóa, và message có thể null.

## 14. Khi cần kiểm tra đúng instance exception

Có API chủ động **ném lại cùng đối tượng** lỗi gốc:

~~~java
// Mã minh họa trong một class phù hợp.
static void rethrow(IOException original) throws IOException {
    throw original;
}
~~~

JUnit có thể kiểm tra:

~~~java
// Bên trong @Test; rethrow được định nghĩa thích hợp.
IOException original = new IOException("disk failure");
IOException actual = assertThrowsExactly(
        IOException.class,
        () -> rethrow(original));
assertSame(original, actual);
~~~

**assertSame** mạnh hơn so message: nó đảm bảo không tạo một IOException mới chỉ có message giống original nhưng mất cause, suppressed hoặc dấu vết ngăn xếp.

Không dùng identity assertion khi API **có quyền** wrap exception. Khi đó nên kiểm tra **wrapper.getCause() == original**. Mục tiêu là giữ đúng contract quan hệ giữa những Throwable, không cố định implementation vô cớ.

## 15. Test trạng thái sau lỗi: đúng exception vẫn chưa đủ

Một lỗi chuyển tiền có thể ném đúng IllegalStateException khi ghi vào tài khoản nhận không thành công, nhưng đã trừ tiền ở tài khoản nguồn. Test chỉ kiểm tra class vẫn PASS dù dữ liệu bị thay đổi dở.

Ví dụ phản mẫu:

~~~java
// Mã minh họa theo ngữ cảnh.
from.debit(amount);
to.credit(amount); // Có thể ném IllegalStateException.
~~~

Nếu API cam kết thất bại không làm thay đổi trạng thái, bài test cần ghi snapshot trước rồi so sánh sau khi gọi:

~~~java
// JUnit theo ngữ cảnh; Account và transfer được định nghĩa bởi ứng dụng.
long sourceBefore = from.balance();
long targetBefore = to.balance();

assertThrowsExactly(IllegalStateException.class,
        () -> transfer(from, to, amount));

assertEquals(sourceBefore, from.balance());
assertEquals(targetBefore, to.balance());
~~~

Khi code có side effect ngoài bộ nhớ (file, database, remote service), cần thêm integration test, transaction test hoặc mô hình idempotency phù hợp. **Loại exception chính xác không tự chứng minh cleanup hoặc rollback đã hoàn tất.**

## 16. Kiểm tra đường thành công và assertDoesNotThrow

Nếu mọi test đều chỉ thử input lỗi, parser có thể từ chối cả những input hợp lệ mà vẫn vượt qua tất cả bài test đó. Cần kiểm tra đường thành công riêng:

~~~java
// JUnit theo ngữ cảnh.
int port = assertDoesNotThrow(
        () -> ExactExceptionTestDemo.parsePort("8080"));
assertEquals(8080, port);
~~~

**assertDoesNotThrow** xác nhận lambda không ném exception, nhưng **assertEquals** mới xác nhận giá trị đúng. Chỉ assertDoesNotThrow có thể PASS nếu phương thức trả sai giá trị nhưng không ném lỗi.

Trong chương trình Java SE đã chạy, điều kiện **require(parsePort("8080") == 8080, ...)** xác minh cả hành vi không lỗi và kết quả. Nếu fail, nó chủ động ném AssertionError chứ không chỉ in dòng thông báo.

Một sai lầm khác: test tự bắt mọi exception rồi ghi log mà không gọi **fail()** hoặc ném AssertionError. Khi đó test runner đánh dấu PASS vì method test kết thúc bình thường. Test phải để lỗi kiểm tra thất bại thực sự đi tới runner.

## 17. Bảng kiểm thử từng loại đầu vào

Với parser cụ thể ở bài này:

| Input | Điều kiện | Kết quả dự kiến |
| --- | --- | --- |
| null | Thiếu cổng | Exact IllegalArgumentException, message port required |
| Chuỗi hai dấu cách | Blank | Exact IllegalArgumentException, message port required |
| "abc" | Không phân tích được số | Exact NumberFormatException |
| "0" | Dưới khoảng 1–65535 | Exact IllegalArgumentException, message port out of range |
| "70000" | Vượt cổng tối đa | Exact IllegalArgumentException, message port out of range |
| "8080" | Hợp lệ | Trả 8080 |
| "65535" | Biên trên hợp lệ | Trả 65535 |
| "999999999999" | Không biểu diễn được kiểu int | Exact NumberFormatException |

Nếu dùng **@ParameterizedTest** trong JUnit, mỗi lần chạy nên có expected class và tên hiển thị tương ứng. Không gom tất cả input sai vào một test duy nhất chỉ mong **RuntimeException.class**, vì nó bỏ qua các quy tắc khác biệt của từng nhánh.

Tách trường hợp cũng giúp đọc thông báo thất bại: biết ngay đầu vào nào, loại lỗi nào, message nào không khớp. Một test dài gọi nhiều assertThrows mà không đặt tên kịch bản thường khó điều tra hơn một bảng test tường minh.

## 18. Async: ExecutionException, CompletionException và lỗi gốc

Ở bài kiểm thử bất đồng bộ, exception xuất hiện tại consumer có thể là một wrapper:

- **Future.get()** khi task lỗi thường ném ExecutionException với cause là lỗi worker.
- **CompletableFuture.join()** thường ném CompletionException với cause là lỗi stage.
- **CancellationException** có thể ném trực tiếp khi Future đã canceled.
- **Future.get(timeout)** có thể ném TimeoutException vì caller chờ quá hạn, task chưa chắc lỗi.
- **InterruptedException** từ get có thể là caller bị interrupt trong khi chờ, không đồng nghĩa worker bị hủy.

Ví dụ JUnit:

~~~java
// Minh họa theo ngữ cảnh với JUnit Jupiter.
CompletableFuture<Integer> failed =
        CompletableFuture.failedFuture(
                new IllegalArgumentException("bad payload"));

CompletionException wrapper = assertThrowsExactly(
        CompletionException.class, failed::join);
assertEquals(IllegalArgumentException.class,
             wrapper.getCause().getClass());
~~~

Nếu viết **assertThrows(RuntimeException.class, failed::join)**, test chưa phân biệt CompletionException với CancellationException hay lỗi trực tiếp khác. Nếu chuyển sang **get()**, wrapper thường là ExecutionException thay vì CompletionException — phải sửa assertion theo hợp đồng mới.

Đối với concurrency, tránh dùng Thread.sleep với thời lượng ngẫu nhiên làm cách duy nhất tạo tình huống lỗi. Dùng CountDownLatch hoặc một CompletableFuture đã hoàn tất bất thường để test deterministic.

## 19. Đừng bắt AssertionError của chính bài test

Một phản mẫu:

~~~java
// Phản mẫu JUnit, đừng dùng.
try {
    assertEquals(1, actual);
} catch (Throwable ignored) {
    // Test runner không còn nhìn thấy lỗi assertion.
}
~~~

Còn nguy hiểm hơn:

~~~java
// Phản mẫu JUnit.
assertThrows(Throwable.class, () -> {
    assertEquals(1, 2);
});
~~~

AssertionError do **assertEquals** ném cũng là **Throwable**. Vì thế assertThrows(Throwable.class, ...) có thể PASS, dù exception không đến từ code sản phẩm mà chỉ từ một assertion cố ý sai!

Để tránh false positive:

- Chỉ đặt **thao tác đang kiểm thử** trong lambda của assertThrows.
- Dùng expected class cụ thể theo hợp đồng.
- Đặt assertion về message/cause/state **sau khi** đã nhận Throwable.
- Không catch Throwable rồi bỏ qua trong method test.
- Để JUnit report AssertionError bình thường nếu kiểm tra thất bại.

Helper **expectExact** của ví dụ chỉ bắt Throwable để kiểm tra exact runtime class, và chủ động ném AssertionError khi class không đúng hoặc không có exception; không nuốt lỗi.

## 20. Lỗi trong setup/cleanup có thể làm assertion PASS giả

Xét đoạn test muốn kiểm tra việc parse một stream:

~~~java
// Phản mẫu JUnit minh họa.
assertThrows(IOException.class, () -> {
    InputStream input = openInput(); // Chính bước mở có thể gây IOException.
    parse(input);                    // Thao tác thực sự cần test.
});
~~~

Nếu **openInput()** ném IOException vì file không tồn tại, test PASS dù **parse(input)** chưa từng chạy. Lỗi có **đúng class** nhưng **không đúng nguồn**.

Cách tổ chức rõ hơn là chuẩn bị input/fixture đáng tin ở ngoài lambda, rồi chỉ đặt thao tác cần test trong lambda. Nếu hợp đồng yêu cầu cả bước open+parse, có thể giữ chúng cùng nhau nhưng cần kiểm tra thêm message/cause, kết quả theo dõi hoặc side effect để xác minh đúng giai đoạn.

Cũng cần xét trường hợp **close()** trong finally hoặc try-with-resources ném IOException cùng loại với lỗi work. Nếu chỉ assertThrows(IOException.class), test có thể PASS khi primary của work bị lỗi close che khuất. **Kiểm tra suppressed và message hoặc identity** giúp bắt nhầm nguồn lỗi.

## 21. Mutation thinking: giả sử code bị sửa sai thì test có FAIL không?

Khi đánh giá chất lượng test, thử đặt các tình huống thay đổi sai:

1. Bỏ null check: parsePort(null) ném NullPointerException.
2. Wrap lỗi parse thành IllegalArgumentException mới: parsePort("abc") không còn exact NumberFormatException.
3. Bỏ kiểm tra range: parsePort("70000") trả số 70000.
4. Catch NumberFormatException rồi trả fallback: test mong exception không còn nhận lỗi.
5. Thay try-with-resources bằng finally che primary: vẫn IOException nhưng message/suppressed khác.
6. Tạo wrapper không truyền cause: outer type vẫn đúng nhưng cause bị mất.
7. Khi Future bị hủy, consumer nhận CancellationException thay vì exceptional completion từ worker.

Các test kiểu **assertThrows(Exception.class)** có thể bỏ sót một số mutation. Test exact type + cause/suppressed + state phát hiện được nhiều thay đổi quan trọng hơn.

Có thể sử dụng mutation testing framework để đánh giá sức mạnh test trong dự án, nhưng không phải công cụ nào cũng tự sinh mọi mutation nói trên. Mục tiêu chính là **tư duy kiểm thử hợp đồng**, không chỉ chạy test cho có.

## 22. Checklist khi review một test exception

1. **Input và trạng thái kích hoạt lỗi** đã được thiết lập chính xác chưa?
2. **Có khẳng định loại exception cụ thể**, hay chỉ có catch Exception / expect any error?
3. **Muốn đúng runtime class hay cho phép subclass?** Assertion đã thể hiện đúng lựa chọn chưa?
4. **JUnit assertThrows và assertThrowsExactly** đã được phân biệt chưa?
5. **Thao tác trong lambda** có đúng giai đoạn cần test, tránh setup/cleanup gây PASS giả?
6. **AssertionError của test** có bị nuốt hoặc được coi là Throwable mong đợi không?
7. **Wrapper/cause** có được kiểm tra khi API chuyển exception sang tầng cao hơn?
8. **Suppressed exceptions** có được kiểm tra khi work và close cùng lỗi?
9. **Message/error code** có ổn định và thuộc hợp đồng hay phụ thuộc một chuỗi JDK không được cam kết?
10. **State sau thất bại** có đáp ứng invariants và cam kết rollback/atomicity không?
11. **Happy path** có được kiểm tra cùng kết quả, không chỉ test đường lỗi?
12. **Async/timeout/interruption/cancellation** có được phân biệt đúng loại không?
13. **Mỗi input và nhánh lỗi** có test riêng hoặc parameterized case rõ ràng không?
14. **Nếu implementation ném một exception khác**, bộ test có thực sự FAIL hay vô tình PASS vì assertion quá rộng?

**Kết luận:** Một test exception đáng tin không dừng ở câu “có lỗi”. Nó xác minh **đúng loại lỗi theo hợp đồng và đúng ngữ nghĩa thất bại**. Trong JUnit Jupiter, **assertThrows** chấp nhận subtype, còn **assertThrowsExactly** yêu cầu đúng runtime class. Kết hợp kiểm tra **message, cause, suppressed, identity và trạng thái** khi cần sẽ giúp phát hiện cả lỗi đổi loại exception lẫn lỗi bị che/nuốt — những vấn đề mà một assertion quá rộng khó thấy được.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-testing-003">Kiểm tra message, thuộc tính ngữ cảnh và cause khi phù hợp</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra message, thuộc tính ngữ cảnh và cause khi phù hợp — Kiểm thử Java Exception

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Xác định **hợp đồng lỗi** mà một phương thức Java phải cung cấp khi thất bại.
- Viết kiểm thử kiểm tra **loại exception**, **message**, **thuộc tính ngữ cảnh** và **cause** mà không ràng buộc quá mức vào các chi tiết triển khai.
- Dùng JUnit 5: <code>assertThrows</code>, <code>assertThrowsExactly</code>, <code>assertEquals</code>, <code>assertSame</code>, <code>assertInstanceOf</code>, <code>assertAll</code> và <code>assertDoesNotThrow</code> đúng mục đích.
- Chọn phép so sánh message phù hợp: khớp chính xác, kiểm tra phần ổn định, mã lỗi hoặc dữ liệu có cấu trúc.
- Chứng minh ngoại lệ khi chuyển tầng **giữ đúng đối tượng cause** và không mất ngữ cảnh giúp chẩn đoán.
- Kiểm tra exception do validation, nghiệp vụ, I/O, timeout hoặc thao tác bất đồng bộ theo hợp đồng từng API.
- Tránh các test “chỉ thấy có exception là đủ”, bắt quá rộng, so sánh stack trace nguyên văn hay dùng message JVM không được bảo đảm.
- Tách các thuộc tính **cần ổn định với người gọi** khỏi thông tin chẩn đoán nội bộ không nên khóa chặt bằng unit test.

Bài thuộc **Java Core — exception**, mục **Kiểm thử**. Các chương trình đầu tiên dùng Java 17+ độc lập. Những ví dụ JUnit 5 cần thư viện JUnit Jupiter trong dự án; bài không giả định JUnit là một phần sẵn có của JDK.

## 2. Kiểm thử exception là kiểm thử hợp đồng, không chỉ kiểm tra “có lỗi”

Giả sử phương thức <code>loadDocument("DOC-42")</code> đọc tài liệu từ repository. Nếu tầng dưới ném <code>IOException</code>, service có thể chuyển nó thành exception tầng ứng dụng. Người gọi không nhất thiết cần biết lớp I/O cụ thể, nhưng có thể phụ thuộc các yếu tố:

1. **Loại lỗi có ý nghĩa:** <code>DocumentLoadException</code>.
2. **Message đã công bố:** ví dụ “Không thể tải tài liệu”.
3. **Thuộc tính ngữ cảnh:** <code>documentId="DOC-42"</code>, <code>operation="LOAD"</code>.
4. **Cause:** giữ <code>IOException</code> ban đầu để chẩn đoán.
5. **Không tạo ra kết quả thành công giả:** thao tác lỗi phải được biểu diễn đúng theo hợp đồng.
6. **Không tiết lộ thông tin nội bộ** khi biến exception thành thông báo cho người dùng.

Một bài test chỉ viết:

~~~java
assertThrows(RuntimeException.class,
        () -> service.loadDocument("DOC-42"));
~~~

là **đoạn minh họa JUnit 5**. Nó rất yếu nếu service được yêu cầu ném một loại exception domain cụ thể, vì <code>NullPointerException</code>, <code>IllegalStateException</code> hoặc nhiều bug khác cũng là subclass của <code>RuntimeException</code>. Test có thể “xanh” dù service hỏng sai cách.

Một test có ý nghĩa hơn phải gắn với **những gì caller có quyền kỳ vọng**. Không phải thuộc tính nào của Throwable cũng cần assert. Chọn những phần thuộc hợp đồng công khai hoặc phục vụ một yêu cầu chẩn đoán cụ thể.

## 3. Các lớp thông tin có thể kiểm tra

| Khía cạnh | API quan sát | Khi nào nên kiểm tra |
| --- | --- | --- |
| Loại exception | <code>getClass()</code>, <code>instanceof</code>, JUnit <code>assertThrows</code> | Khi caller phân nhánh xử lý theo loại lỗi |
| Message | <code>getMessage()</code> | Khi message hoặc một phần nội dung là hợp đồng thực sự |
| Thuộc tính ngữ cảnh | Getter/record accessor do ứng dụng định nghĩa | Khi caller cần mã lỗi, ID tác vụ, thao tác, trạng thái retry |
| Cause trực tiếp | <code>getCause()</code> | Khi tầng trên cần giữ nguyên lỗi gốc hoặc phân loại nguyên nhân |
| Cause chain | Lặp qua <code>getCause()</code> | Khi một số tầng wrapper hợp lệ và cần tìm nguyên nhân sâu hơn |
| Suppressed exceptions | <code>getSuppressed()</code> | Với cleanup, try-with-resources hoặc lỗi phụ cần lưu |
| Stack trace | <code>getStackTrace()</code> | Chỉ khi đặc tính ghi trace là yêu cầu chẩn đoán cụ thể |
| Mã lỗi công khai | <code>code()</code>, <code>getErrorCode()</code> hoặc error DTO | Khi client không nên phân tích message tự do |
| Ranh giới lỗi async | <code>Future.get()</code>, <code>CompletableFuture.join()</code> | Khi lỗi được bọc theo hợp đồng async |

### Điều không nên mặc định assert

- Số dòng nguồn trong stack trace.
- Thứ tự hoặc số lượng frame cụ thể.
- Message của NPE, SQLException hay IOException do JVM/driver/thư viện tạo nếu không thuộc hợp đồng của bạn.
- Toàn văn log do framework render trong mọi phiên bản.
- Một message chứa đường dẫn file, tên máy chủ, token hay chi tiết nội bộ vốn không được công bố ổn định.

**Câu hỏi trước khi viết assert:** “Nếu triển khai thay đổi mà hành vi caller vẫn đúng, assertion này có nên tiếp tục PASS không?”

## 4. assertThrows: bắt exception và trả về chính đối tượng đó

JUnit 5 cung cấp:

~~~java
DocumentLoadException thrown =
        assertThrows(DocumentLoadException.class,
                () -> service.loadDocument("DOC-42"));
~~~

Đây là **đoạn JUnit 5 minh họa**, giả định có <code>service</code> và lớp <code>DocumentLoadException</code>. Nếu executable không ném exception phù hợp, assertion thất bại. Nếu ném đúng kiểu yêu cầu hoặc một subtype được chấp nhận, assertion thành công và trả về đối tượng Throwable bắt được.

Bạn có thể kiểm tra tiếp:

~~~java
assertEquals("Không thể tải tài liệu", thrown.getMessage());
assertEquals("DOC-42", thrown.documentId());
assertEquals("LOAD", thrown.operation());
assertNotNull(thrown.getCause());
~~~

Những assertion này chỉ nên dùng khi service công bố đúng các thuộc tính như ví dụ. Nếu message được phép thay đổi theo locale, không nên bắt test khớp một câu tiếng Việt duy nhất. Nếu caller cần mã lỗi ổn định, kiểm tra mã lỗi trước.

### Tại sao phải giữ biến thrown?

Việc viết riêng lẻ:

~~~java
assertThrows(DocumentLoadException.class,
        () -> service.loadDocument("DOC-42"));
~~~

chỉ kiểm tra loại exception. Lưu kết quả vào <code>thrown</code> giúp kiểm tra message, field và cause **trên cùng lần thực thi**, tránh gọi operation thêm lần nữa.

Đặc biệt với I/O, transaction hay thao tác có side effect, đừng gọi service hai hoặc ba lần chỉ để kiểm tra từng thuộc tính. Những lần gọi có thể trả trạng thái khác nhau, phát sinh chi phí hoặc tạo tác dụng phụ không mong muốn.

## 5. assertThrows và assertThrowsExactly khác nhau

Trong JUnit Jupiter:

- <code>assertThrows(Expected.class, executable)</code> chấp nhận <code>Expected</code> **hoặc subclass** của nó.
- <code>assertThrowsExactly(Expected.class, executable)</code> yêu cầu **chính xác class đó**, không chấp nhận subclass. API này có trong các phiên bản JUnit Jupiter hiện đại, bao gồm JUnit 5.8+.

Ví dụ minh họa:

~~~java
class ApplicationException extends RuntimeException {
}

class DocumentException extends ApplicationException {
}

assertThrows(ApplicationException.class,
        () -> { throw new DocumentException(); });

assertThrowsExactly(DocumentException.class,
        () -> { throw new DocumentException(); });
~~~

Đây là **đoạn JUnit 5 minh họa**. Cả hai assertion trên sẽ thành công.

Nếu hợp đồng cho phép hệ thống mở rộng lỗi bằng subclass, <code>assertThrowsExactly</code> có thể khiến test quá chặt. Nếu API thực sự yêu cầu đúng loại lỗi không phân cấp, phép kiểm tra exact mới phù hợp.

### Phân biệt việc bắt rộng và kiểm tra có chủ đích

<code>assertThrows(RuntimeException.class, ...)</code> có thể hợp lệ với một test chỉ yêu cầu bất kỳ lỗi runtime nào. Nhưng với validation cụ thể, hãy kiểm tra <code>IllegalArgumentException</code> hoặc lớp domain được công bố.

Một lựa chọn sai thường gặp:

~~~java
assertThrows(Exception.class, () -> parseAndSave(input));
~~~

Đây là **mã minh họa**. Test này có thể PASS vì một bug không liên quan như I/O thất bại, thay vì lỗi validation mong muốn. Hãy thu hẹp loại exception và xem thêm các thuộc tính ngữ nghĩa của nó.

## 6. Test message: khi nào so sánh chính xác?

Với một thông báo **do ứng dụng sở hữu, ổn định và được công bố là hợp đồng**, có thể dùng:

~~~java
assertEquals("Số lượng phải lớn hơn 0", thrown.getMessage());
~~~

Ưu điểm: test phát hiện thay đổi không mong muốn về wording, dấu tiếng Việt hoặc thông báo dành cho caller. Nhược điểm: thay đổi nhỏ về dấu câu/bản dịch cũng làm test fail.

So sánh chính xác phù hợp khi:

- Message là một phần hợp đồng có chủ đích.
- Hệ thống không áp dụng localization ở lớp đang test.
- Người dùng/caller cần nhận đúng template này.
- Test nhằm bảo vệ một yêu cầu cụ thể về văn bản, ví dụ một câu thông báo accessibility/UX đã được duyệt.

### Khi nào chỉ kiểm tra phần ổn định?

Nếu message được phép thêm chi tiết hoặc ID do ứng dụng quản lý, một assertion mềm hơn có thể hợp lý:

~~~java
assertTrue(thrown.getMessage().contains("Không thể tải tài liệu"));
~~~

Đây là **đoạn JUnit 5 minh họa**. Tuy nhiên, phép <code>contains</code> có thể quá yếu hoặc cho PASS giả. Ví dụ một message dài chứa cụm từ đó nhưng diễn đạt sai trạng thái. Có thể kiểm tra <code>startsWith</code> hoặc một mẫu được duyệt, nhưng chỉ khi thật sự là hợp đồng.

Nếu message có thể null, gọi <code>getMessage().contains(...)</code> sẽ tự ném <code>NullPointerException</code>, khiến báo lỗi test khó hiểu. Có thể dùng:

~~~java
String message = thrown.getMessage();
assertNotNull(message);
assertTrue(message.contains("Không thể tải tài liệu"));
~~~

Những dòng này là **mã JUnit minh họa**. Nó cho failure rõ hơn khi exception không có message.

### Khi nào không nên test text?

Message của <code>NumberFormatException</code> do <code>Integer.parseInt()</code>, <code>NullPointerException</code> do runtime hoặc <code>IOException</code> do filesystem không phải văn bản mà ứng dụng toàn quyền kiểm soát. Nếu test cần nhận diện loại lỗi, assert class; nếu có wrapper do ứng dụng định nghĩa, kiểm tra message của wrapper hoặc mã lỗi ổn định, không khóa vào chi tiết của thư viện bên dưới.

**Quy tắc:** kiểm tra giá trị caller có quyền dựa vào, không kiểm tra chi tiết tình cờ của một implementation.

## 7. Thuộc tính ngữ cảnh: nên là dữ liệu, không chỉ nằm trong chuỗi

Một lớp exception tầng ứng dụng có thể có:

~~~java
final class DocumentLoadException extends RuntimeException {
    private final String documentId;
    private final String operation;

    DocumentLoadException(
            String message, String documentId,
            String operation, Throwable cause) {
        super(message, cause);
        this.documentId = documentId;
        this.operation = operation;
    }

    String documentId() {
        return documentId;
    }

    String operation() {
        return operation;
    }
}
~~~

Đây là **định nghĩa lớp minh họa**, có thể dùng như một lớp riêng hoặc nested class nếu điều chỉnh phạm vi. Hai field cung cấp dữ liệu có cấu trúc cho tầng gọi.

Test:

~~~java
assertEquals("DOC-42", error.documentId());
assertEquals("LOAD", error.operation());
~~~

Tốt hơn việc phân tích message:

~~~java
assertTrue(error.getMessage().contains("DOC-42"));
~~~

Chứa ID trong message chưa chứng minh object có đúng thuộc tính context mà caller cần. Khi giữ ID trong field, ứng dụng có thể thay đổi hoặc dịch message mà không phá hợp đồng dữ liệu.

### Chọn thuộc tính nào là context?

Chỉ giữ những thuộc tính cần cho ngữ nghĩa API hoặc chẩn đoán:

- ID tài liệu/đơn hàng nếu được phép sử dụng trong tầng hiện tại.
- Mã thao tác như <code>LOAD</code>, <code>SAVE</code>, <code>DELETE</code>.
- Mã lỗi domain cố định như <code>DOCUMENT_UNAVAILABLE</code>.
- Cờ <code>retryable</code> nếu đã xác định chính sách retry thực sự hợp lệ.
- Tham chiếu correlation/request ID an toàn theo hợp đồng.
- Trạng thái đối tượng hoặc loại tài nguyên nếu không vi phạm quyền truy cập.

Không lưu mật khẩu, access token hoặc toàn bộ payload nhạy cảm vào exception context chỉ để test tiện. Context có thể bị log hoặc truyền qua nhiều tầng.

### Thông điệp không phải luôn là public message

Một exception nội bộ có thể có message chẩn đoán chứa ID nội bộ. Response gửi tới người dùng thường nên được tạo từ **mã lỗi ổn định và template an toàn**, không gửi trực tiếp <code>Throwable.getMessage()</code>. Vì vậy test service và test error mapper có thể kiểm tra hai hợp đồng **khác nhau**.

## 8. Kiểm tra cause: kiểu, nội dung hay identity?

Khi một tầng chuyển <code>IOException</code> thành <code>DocumentLoadException</code>, câu hỏi quan trọng là “exception gốc có được giữ không?”.

~~~java
try {
    return repository.read(documentId);
} catch (IOException original) {
    throw new DocumentLoadException(
            "Không thể tải tài liệu",
            documentId,
            "LOAD",
            original);
}
~~~

Đây là **đoạn mã minh họa trong một phương thức phù hợp**. Constructor nhận <code>original</code> làm cause, nên:

~~~java
assertSame(original, translated.getCause());
~~~

là phép kiểm tra mạnh nhất nếu test có thể giữ **chính instance gốc**. <code>assertSame</code> xác nhận hai tham chiếu trỏ tới cùng object. Điều này mạnh hơn:

~~~java
assertEquals(original.getClass(),
        translated.getCause().getClass());
~~~

vì hai exception khác nhau có thể cùng class.

### So sánh ba cấp độ

| Assertion | Chứng minh điều gì | Giới hạn |
| --- | --- | --- |
| <code>assertNotNull(error.getCause())</code> | Có cause | Không biết cause đúng hay không |
| <code>assertInstanceOf(IOException.class, error.getCause())</code> | Cause thuộc loại kỳ vọng | Chưa chứng minh là chính instance nguồn |
| <code>assertSame(original, error.getCause())</code> | Cause trực tiếp đúng **cùng object** | Chỉ phù hợp nếu hợp đồng yêu cầu giữ nguyên cause |

Trong JUnit 5, <code>assertEquals</code> trên exception thường không phải lựa chọn tương đương với <code>assertSame</code>; các Throwable thông thường không định nghĩa equality theo toàn bộ stack trace, type, message và cause. Nếu muốn kiểm tra identity, dùng <code>assertSame</code> cho rõ.

### Vì sao không chỉ so message của cause?

Một wrapper có thể sao chép message gốc nhưng đánh mất stack trace:

~~~java
throw new DocumentLoadException(
        original.getMessage(),
        documentId,
        "LOAD",
        null);
~~~

Đây là **mã minh họa cố ý không phù hợp khi cần bảo toàn cause**. Caller nhìn thấy một message giống nhau, nhưng <code>getCause() == null</code> và mất đường truy nguyên exception ban đầu. Test identity bằng <code>assertSame</code> phát hiện lỗi đó.

Tuy nhiên, không phải mọi API cần giữ **cause trực tiếp** là cùng object. Một số tầng có chủ đích dùng thêm wrapper trung gian. Khi hợp đồng chỉ yêu cầu “cause chain giữ được lỗi gốc”, test cần duyệt chuỗi và kiểm tra đúng cấp thay vì cứng nhắc yêu cầu một tầng duy nhất.

## 9. Ví dụ Java 17 độc lập: hợp đồng message, context và cause

Chương trình này dùng cơ chế tự kiểm tra đơn giản để chạy được chỉ với JDK. Nó không thay JUnit trong dự án nhưng thể hiện rõ cách kiểm tra cùng một exception qua nhiều thuộc tính.

Lưu tệp **ExceptionContractDemo.java**:

~~~java
import java.io.IOException;

public class ExceptionContractDemo {

    static final class DocumentLoadException extends RuntimeException {
        private final String documentId;
        private final String operation;

        DocumentLoadException(
                String message, String documentId,
                String operation, Throwable cause) {
            super(message, cause);
            this.documentId = documentId;
            this.operation = operation;
        }

        String documentId() {
            return documentId;
        }

        String operation() {
            return operation;
        }
    }

    static final class FakeRepository {
        final IOException original =
                new IOException("simulated storage failure");

        String read(String documentId) throws IOException {
            throw original;
        }
    }

    static String load(String documentId, FakeRepository repository) {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId không được trống");
        }

        try {
            return repository.read(documentId);
        } catch (IOException original) {
            throw new DocumentLoadException(
                    "Không thể tải tài liệu",
                    documentId,
                    "LOAD",
                    original);
        }
    }

    static void require(boolean condition) {
        if (!condition) {
            throw new AssertionError("Contract failed");
        }
    }

    public static void main(String[] args) {
        FakeRepository repository = new FakeRepository();

        try {
            load("DOC-42", repository);
            throw new AssertionError("Expected DocumentLoadException");
        } catch (DocumentLoadException failure) {
            require("Không thể tải tài liệu".equals(
                    failure.getMessage()));
            require("DOC-42".equals(failure.documentId()));
            require("LOAD".equals(failure.operation()));
            require(failure.getCause() == repository.original);

            System.out.println("Type: "
                    + failure.getClass().getSimpleName());
            System.out.println("Message: " + failure.getMessage());
            System.out.println("Context documentId: "
                    + failure.documentId());
            System.out.println("Context operation: "
                    + failure.operation());
            System.out.println("Same original cause: "
                    + (failure.getCause() == repository.original));
            System.out.println("Contract: PASS");
        }
    }
}
~~~

Biên dịch và chạy bằng Java 17+:

~~~bash
javac -encoding UTF-8 ExceptionContractDemo.java
java ExceptionContractDemo
~~~

**Stdout chính xác:**

~~~text
Type: DocumentLoadException
Message: Không thể tải tài liệu
Context documentId: DOC-42
Context operation: LOAD
Same original cause: true
Contract: PASS
~~~

### Giải thích

1. <code>FakeRepository.original</code> giữ một <code>IOException</code> có identity ổn định để test đối chiếu.
2. <code>load</code> có guard cho ID không hợp lệ, sau đó bắt đúng <code>IOException</code> ở tầng repository.
3. Wrapper giữ message có ý nghĩa ở tầng service, hai field context và **chính đối tượng cause**.
4. Khối <code>catch (DocumentLoadException)</code> chỉ chạy với loại error phù hợp. Các điều kiện <code>require</code> làm chương trình báo thất bại bằng <code>AssertionError</code> nếu bất kỳ thuộc tính nào không đúng.
5. Mọi kết quả được in sau khi các kiểm tra PASS, nên stdout xác định, không phụ thuộc thời gian hoặc thread pool.
6. Message của <code>IOException</code> gốc được giữ cho chẩn đoán, nhưng stdout ví dụ không xuất toàn bộ exception kỹ thuật cho người dùng.

Tại dự án thật, nên dùng framework test để báo rõ assertion nào thất bại, chứ không tự viết <code>require</code> chung như minh họa.

## 10. JUnit 5: bài test hoàn chỉnh cho exception có ngữ cảnh

Ví dụ dưới đây dùng JUnit Jupiter. Nó cho thấy cách kiểm tra bốn nhóm thuộc tính trong **một lần gọi** và cách kiểm tra cả nhánh validation.

Lưu tệp **DocumentServiceTest.java** trong source set kiểm thử của dự án, nơi JUnit Jupiter đã được cấu hình:

~~~java
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class DocumentServiceTest {

    static final class DocumentLoadException extends RuntimeException {
        private final String documentId;
        private final String operation;

        DocumentLoadException(
                String message,
                String documentId,
                String operation,
                Throwable cause) {
            super(message, cause);
            this.documentId = documentId;
            this.operation = operation;
        }

        String documentId() {
            return documentId;
        }

        String operation() {
            return operation;
        }
    }

    static final class FakeRepository {
        final IOException original =
                new IOException("disk temporarily unavailable");

        String read(String documentId) throws IOException {
            throw original;
        }
    }

    static String loadDocument(String documentId, FakeRepository repository) {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException(
                    "documentId không được trống");
        }

        try {
            return repository.read(documentId);
        } catch (IOException original) {
            throw new DocumentLoadException(
                    "Không thể tải tài liệu",
                    documentId,
                    "LOAD",
                    original);
        }
    }

    @Test
    void translationKeepsMessageContextAndOriginalCause() {
        FakeRepository repository = new FakeRepository();

        DocumentLoadException error = assertThrowsExactly(
                DocumentLoadException.class,
                () -> loadDocument("DOC-7", repository));

        assertAll("DocumentLoadException contract",
                () -> assertEquals(
                        "Không thể tải tài liệu", error.getMessage()),
                () -> assertEquals("DOC-7", error.documentId()),
                () -> assertEquals("LOAD", error.operation()),
                () -> assertSame(repository.original, error.getCause()));
    }

    @Test
    void blankDocumentIdReportsValidationError() {
        FakeRepository repository = new FakeRepository();

        IllegalArgumentException error = assertThrowsExactly(
                IllegalArgumentException.class,
                () -> loadDocument("   ", repository));

        assertEquals("documentId không được trống", error.getMessage());
        assertNull(error.getCause());
    }
}
~~~

### Điều kiện chạy

- Java 17+ để biên dịch mã ví dụ.
- Thư viện **JUnit Jupiter** (JUnit 5) được khai báo cho test source set.
- Test runner JUnit Jupiter được kích hoạt qua Maven Surefire, Gradle Test hoặc IDE phù hợp.
- <code>assertThrowsExactly</code> cần phiên bản JUnit Jupiter có API đó (JUnit 5.8+).

Nếu dự án dùng Maven với cấu trúc chuẩn, ví dụ có thể đặt tại:

~~~text
src/test/java/DocumentServiceTest.java
~~~

Đây là **đường dẫn gợi ý cấu trúc dự án**, không phải tệp được tạo trong công việc hiện tại. Chạy bằng công cụ build của dự án sau khi đã có dependency phù hợp, chẳng hạn:

~~~bash
mvn test
~~~

Đây là **lệnh cho dự án Maven đã cấu hình test**, không phải lệnh có thể chạy trực tiếp với JDK trần. Kết quả kỳ vọng là **2 tests passed**, không có stdout nghiệp vụ cố định vì JUnit runner có định dạng báo cáo riêng theo tool/version.

### Vì sao dùng assertAll?

<code>assertAll</code> giúp gom nhiều assertion độc lập cho cùng error. Khi nhiều thuộc tính cùng sai, framework có thể báo nhóm lỗi assertion, giúp thấy được bức tranh thay vì dừng ngay ở điều kiện đầu tiên. Tuy nhiên, mọi expression cần chuẩn bị an toàn: nếu trước khi vào <code>assertAll</code> đã dereference một giá trị null bất ngờ, test vẫn có thể hỏng theo cách khó đọc.

<code>assertThrowsExactly</code> trong ví dụ là lựa chọn vì bài cố tình định nghĩa loại wrapper cụ thể. Nếu hợp đồng chấp nhận subclass, dùng <code>assertThrows</code> sẽ linh hoạt hơn.

## 11. Kiểm tra nhánh không ném lỗi: assertDoesNotThrow

Kiểm thử exception cũng cần chứng minh **đường hợp lệ không phát sinh lỗi**. Ví dụ phương thức validation:

~~~java
static void validateQuantity(int quantity) {
    if (quantity <= 0) {
        throw new IllegalArgumentException(
                "quantity must be positive");
    }
}
~~~

Đây là **đoạn Java minh họa** trong lớp phù hợp.

JUnit:

~~~java
assertDoesNotThrow(() -> validateQuantity(1));

IllegalArgumentException error = assertThrowsExactly(
        IllegalArgumentException.class,
        () -> validateQuantity(0));

assertEquals("quantity must be positive", error.getMessage());
~~~

Đây là **mã JUnit 5 minh họa**. Kết hợp cả hai trường hợp giúp tránh một implementation luôn ném lỗi hoặc luôn trả về bình thường mà vẫn lọt qua bộ test chỉ có một nhánh.

Với method trả giá trị:

~~~java
String result = assertDoesNotThrow(() -> normalize("  a  "));
assertEquals("a", result);
~~~

Đây là **mã minh họa**, giả định <code>normalize</code> được thiết kế trả về chuỗi đã trim. Nếu một operation thực sự được phép ném một số exception có kiểm soát nhưng caller vẫn chấp nhận được, không nên ép test dùng <code>assertDoesNotThrow</code> một cách máy móc.

## 12. Cause chain: kiểm tra cấp nào là hợp đồng?

Một hệ thống nhiều tầng có thể sinh ra:

~~~text
HttpAdapterException
   |
   +-- ServiceException
          |
          +-- IOException
~~~

Nếu hợp đồng của adapter chỉ yêu cầu **bảo toàn nguyên nhân gốc**, đừng viết test bắt buộc hai cấp wrapper cố định nếu việc thêm hoặc bỏ một wrapper không ảnh hưởng caller.

Hai cách kiểm tra:

### 12.1. Kiểm tra cause trực tiếp

~~~java
assertSame(original, serviceError.getCause());
~~~

Phù hợp khi constructor/service cam kết giữ chính <code>original</code> ở cấp cause đầu tiên.

### 12.2. Kiểm tra một nguyên nhân nằm trong chain

Nếu có thêm wrapper hợp lệ:

~~~java
Throwable current = outer;
boolean containsOriginal = false;

while (current != null) {
    if (current == original) {
        containsOriginal = true;
        break;
    }
    current = current.getCause();
}

assertTrue(containsOriginal);
~~~

Đây là **đoạn JUnit 5 minh họa** với <code>outer</code> và <code>original</code> được tạo trước, giả định chain không bị tạo vòng. Trong thư viện chẩn đoán tổng quát, có thể thêm tập nhận diện identity để phòng chuỗi Throwable bất thường. Đừng thay identity check bằng so sánh message vì nhiều exception có thể có cùng văn bản.

### Không unwrap bừa bãi

Một exception có thể có cause hợp lệ nhưng không đại diện cho failure mà tầng gọi cần xử lý trực tiếp. Ví dụ một lớp domain đã cố tình chuyển <code>SQLException</code> thành <code>DocumentLoadException</code>. Nếu code test hoặc application chỉ đi tìm root cause để quyết định hành vi nghiệp vụ, nó có thể bỏ qua chính sách đã được đặt ở tầng domain.

**Kiểm tra cả ngữ nghĩa lớp wrapper và bảo toàn nguyên nhân khi phù hợp**, không chỉ root cause.

## 13. Ví dụ Java 17 độc lập: chuỗi cause và suppressed exception

Lưu tệp **CauseChainDemo.java**:

~~~java
import java.io.IOException;

public class CauseChainDemo {

    static void require(boolean condition) {
        if (!condition) {
            throw new AssertionError("Unexpected exception structure");
        }
    }

    public static void main(String[] args) {
        IOException root = new IOException("read timeout");
        IllegalStateException middle =
                new IllegalStateException("storage failure", root);
        RuntimeException outer =
                new RuntimeException("request failure", middle);
        IOException cleanup = new IOException("cleanup failure");
        outer.addSuppressed(cleanup);

        Throwable directCause = outer.getCause();
        Throwable deepestCause = directCause.getCause();
        Throwable[] suppressed = outer.getSuppressed();

        require(directCause == middle);
        require(deepestCause == root);
        require(suppressed.length == 1);
        require(suppressed[0] == cleanup);

        System.out.println("Outer: "
                + outer.getClass().getSimpleName());
        System.out.println("Direct cause: "
                + directCause.getClass().getSimpleName());
        System.out.println("Root cause: "
                + deepestCause.getClass().getSimpleName());
        System.out.println("Root identity: " + (deepestCause == root));
        System.out.println("Suppressed count: " + suppressed.length);
        System.out.println("Suppressed identity: "
                + (suppressed[0] == cleanup));
        System.out.println("Checks: PASS");
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 CauseChainDemo.java
java CauseChainDemo
~~~

**Stdout chính xác:**

~~~text
Outer: RuntimeException
Direct cause: IllegalStateException
Root cause: IOException
Root identity: true
Suppressed count: 1
Suppressed identity: true
Checks: PASS
~~~

### Những gì ví dụ chứng minh

- <code>outer.getCause()</code> trỏ tới **cùng instance** <code>middle</code>.
- <code>middle.getCause()</code> trỏ tới **cùng instance** <code>root</code>.
- <code>getSuppressed()</code> chứa exception phụ <code>cleanup</code>, khác với cause chain.
- Đọc cause và suppressed không yêu cầu in cả stack trace.
- Hai đường lưu thông tin lỗi này có ngữ nghĩa khác nhau: cause mô tả nguyên nhân, suppressed lưu các exception bổ sung theo cơ chế sử dụng.

Không nên đưa tất cả exception vào cause chain theo một thứ tự tùy tiện chỉ để thỏa test; điều đó làm sai ngữ nghĩa. Với nhiều lỗi độc lập, có thể cần kiểu kết quả tổng hợp, danh sách lỗi hoặc chính sách riêng.

## 14. Checked exception cũng cần kiểm tra hợp đồng

Không chỉ <code>RuntimeException</code> mới được kiểm thử bằng <code>assertThrows</code>. Một API Java có thể khai báo:

~~~java
String read(Path path) throws IOException {
    return Files.readString(path);
}
~~~

Đây là **đoạn minh họa**, cần import <code>Path</code>, <code>Files</code> và <code>IOException</code>. Khi test với điều kiện I/O xác định và phù hợp, có thể viết:

~~~java
IOException error = assertThrows(IOException.class,
        () -> reader.read(path));
~~~

Đây là **mã JUnit 5 minh họa**. Nhưng test không nên giả định một lỗi I/O cụ thể chỉ dựa vào trạng thái filesystem thật trên máy chạy CI. Cùng một tệp có thể bị xóa, thay quyền hoặc được tạo bởi process khác.

Nếu mục tiêu là kiểm tra **cách service chuyển IOException**, hãy dùng fake repository có thể ném **đúng instance** được tạo sẵn, như ví dụ trong phần 9–10. Điều đó giúp test:

- Không phụ thuộc vào hệ điều hành.
- Không cần quyền đặc biệt hay đường dẫn cứng.
- Không mở file thật và gây race.
- So sánh cause identity rõ ràng.
- Dễ tái hiện trên CI và máy lập trình viên.

Với integration test filesystem, có thể dùng thư mục tạm và dọn tài nguyên đúng cách. Tuy nhiên, đó là test về tích hợp I/O, không phải đơn vị nhỏ nhất cần dùng để kiểm tra hợp đồng exception của service.

## 15. Test thông báo dành cho người dùng khác test message nội bộ

Một lỗi hạ tầng có thể chứa message kiểu:

~~~text
connection refused to internal-db:5432
~~~

Tầng service giữ cause này cho chẩn đoán, nhưng API không nên đưa message thô đó cho người dùng. Thay vào đó, error mapper có thể trả:

~~~json
{
  "code": "DOCUMENT_UNAVAILABLE",
  "message": "Không thể tải tài liệu lúc này. Vui lòng thử lại sau.",
  "requestId": "RQ-1042"
}
~~~

Đây là **JSON minh họa**, không phải response từ một server được khởi chạy trong bài.

Hai bộ test nên tách nhau:

1. **Service test:** wrapper đúng loại, message nội bộ nếu là hợp đồng, context và cause được giữ.
2. **API/error mapper test:** mã lỗi, status và thông điệp công khai đúng; không có hostname, token, SQL, đường dẫn nội bộ hoặc stack trace.

Một test bảo mật có thể chèn secret giả vào <code>IOException</code> nguồn rồi kiểm tra **toàn bộ payload response** không chứa secret đó. Không chỉ kiểm tra một field message nếu các field khác cũng có thể làm lộ dữ liệu.

### Không dựa vào exact text của mọi lỗi

Một public message được bản địa hóa theo ngôn ngữ có thể khác nhau theo locale. Khi đó test code/status/semantics và chỉ kiểm tra text cụ thể ở test dành riêng cho locale/template. Đừng cố dùng duy nhất một assertion exact tiếng Việt cho mọi locale.

**Khả năng chẩn đoán nội bộ và tính an toàn công khai là hai thuộc tính độc lập**. Một hệ thống có thể giữ cause rất tốt nhưng leak lỗi kỹ thuật ở response; ngược lại, response an toàn nhưng service làm mất cause. Cần kiểm thử cả hai nếu đều là yêu cầu.

## 16. Không kiểm tra stack trace nguyên văn nếu không cần

Một anti-pattern:

~~~java
assertEquals(
        "at com.example.Service.load(Service.java:42)",
        error.getStackTrace()[0].toString());
~~~

Đây là **mã JUnit minh họa cố ý không khuyến nghị**. Số dòng, tên file, thứ tự frame và compiler/JIT/test runner có thể làm test lỗi dù hệ thống vẫn đúng.

Nếu yêu cầu là “exception có stack trace giúp chẩn đoán”, có thể kiểm tra mức độ hợp lý:

~~~java
assertTrue(error.getStackTrace().length > 0);
~~~

Nhưng ngay cả assertion này cũng có giới hạn: một số exception được thiết kế với <code>writableStackTrace=false</code>, hoặc runtime có tối ưu phụ thuộc JVM. Nếu hệ thống đã quyết định dùng exception nhẹ, test cần phản ánh chính sách đó chứ không áp điều kiện chung cho mọi Throwable.

Nếu mục tiêu chính là giữ nguyên cause, <code>assertSame(original, error.getCause())</code> thường bám sát hợp đồng hơn so sánh số lượng frame.

## 17. Kiểm thử message có tham số động

Một message có thể có ID, số lượng hoặc tên thao tác. Đừng chỉ kiểm tra “có một chuỗi bất kỳ”:

~~~java
assertNotNull(error.getMessage());
~~~

Assertion này không chứng minh message có đủ ngữ cảnh. Tuy nhiên, việc đưa ID trực tiếp vào message cũng không phải luôn là lựa chọn tốt.

**Nếu API có field có cấu trúc**, ưu tiên:

~~~java
assertEquals("DOC-7", error.documentId());
assertEquals("LOAD", error.operation());
~~~

Và chỉ kiểm tra phần message ổn định:

~~~java
assertTrue(error.getMessage().startsWith("Không thể tải tài liệu"));
~~~

Nếu message chính xác đã được công bố là contract:

~~~java
assertEquals("Không thể tải tài liệu DOC-7",
        error.getMessage());
~~~

Đây đều là **đoạn mã JUnit 5 minh họa**; chúng đại diện cho **những hợp đồng giả định khác nhau**, không phải ba assertion cùng bắt buộc đặt lên một implementation.

### Message có thể null

Theo hợp đồng <code>Throwable</code>, message có thể null. Có API cố ý tạo exception không message nhưng có error code và context riêng. Vì vậy test phải biết trường hợp nào yêu cầu message khác null, trường hợp nào chấp nhận null.

Không gọi <code>getMessage().contains(...)</code> trên assumption mù quáng; nếu cần, kiểm tra <code>assertNotNull</code> trước để tránh chính test ném NPE khó hiểu.

## 18. assertInstanceOf, class equality và cấp trừu tượng

JUnit Jupiter cung cấp <code>assertInstanceOf</code> cho nhu cầu khẳng định object thuộc một class/interface; một số phiên bản có overload trả về giá trị đã được ép kiểu để dùng tiếp.

Ví dụ:

~~~java
Throwable cause = error.getCause();
IOException io = assertInstanceOf(IOException.class, cause);
assertNotNull(io.getMessage());
~~~

Đây là **mã JUnit 5 minh họa**. Nếu hợp đồng yêu cầu cause là một loại ngoại lệ I/O hoặc subtype của nó, assertion này phù hợp.

Nếu cần đúng class:

~~~java
assertEquals(IOException.class, cause.getClass());
~~~

Nhưng hãy cẩn trọng: một implementation thay <code>IOException</code> bằng subclass có ý nghĩa hơn có thể vẫn đúng hợp đồng, còn phép so class exact sẽ khiến test fail. Với test về cause identity, ưu tiên <code>assertSame(original, cause)</code> khi instance nguồn có thể kiểm soát được.

**Không biến test thành bản sao của cấu trúc nội bộ.** Test nên bảo vệ contract người dùng của tầng đó, không ép các lớp wrapper/driver phải giữ đúng class cụ thể nếu điều đó không được công bố.

## 19. Exception trong Future và CompletableFuture

Khi tác vụ chạy bất đồng bộ, exception có thể không xuất hiện trực tiếp tại lời gọi khởi tạo hoặc submit.

Với <code>Future.get()</code>, lỗi từ task thường được báo qua <code>ExecutionException</code>. Với <code>CompletableFuture.join()</code>, lỗi completion thông thường được báo qua <code>CompletionException</code>; cancellation có thể ném <code>CancellationException</code> trực tiếp theo ngữ nghĩa từng future.

Ví dụ JUnit minh họa:

~~~java
IOException original = new IOException("network error");
CompletableFuture<String> failed = new CompletableFuture<>();
failed.completeExceptionally(original);

CompletionException wrapper =
        assertThrows(CompletionException.class, failed::join);

assertSame(original, wrapper.getCause());
~~~

Đây là **mã JUnit 5 minh họa**; cần import các lớp <code>java.io</code> và <code>java.util.concurrent</code> tương ứng. Khi gọi <code>get()</code> thay vì <code>join()</code>, loại exception báo cho caller sẽ khác:

~~~java
ExecutionException wrapper =
        assertThrows(ExecutionException.class, failed::get);

assertSame(original, wrapper.getCause());
~~~

Đây cũng là **mã JUnit 5 minh họa** sử dụng future đã hoàn tất lỗi ở đoạn trước. Mục tiêu là test lớp wrapper đúng theo API quan sát kết quả.

Không chỉ viết <code>assertThrows(IOException.class, failed::join)</code> rồi kết luận code sai khi assertion fail: hợp đồng của <code>join()</code> không hứa ném trực tiếp checked exception gốc. Cần hiểu cách API bọc lỗi.

Đối với một pipeline phức tạp, cause có thể đi qua nhiều <code>CompletionException</code> hoặc wrapper do các stage tạo. Test nên xác định **cấp lỗi nào có ý nghĩa với caller**, không tự động unwrap mọi lớp mà bỏ qua mã lỗi domain.

## 20. Các trường hợp ngoại lệ có thể bị thay thế hoặc che lấp

### 20.1. finally tự ném ngoại lệ mới

~~~java
try {
    operation();
} finally {
    cleanupThatThrows();
}
~~~

Đây là **mã minh họa**, giả định hai lời gọi tồn tại. Nếu <code>operation()</code> đã ném một lỗi và <code>cleanupThatThrows()</code> cũng ném, lỗi từ <code>finally</code> có thể thay thế lỗi ban đầu. Một test chỉ kiểm tra type cuối không bảo đảm nguyên nhân chính được giữ.

Nếu cần bảo toàn nhiều lỗi khi đóng tài nguyên, ưu tiên <code>try-with-resources</code> cho tài nguyên <code>AutoCloseable</code> phù hợp. Lỗi đóng có thể được lưu trong <code>getSuppressed()</code> của lỗi chính. Khi hợp đồng yêu cầu, hãy test suppressed thay vì chỉ cause.

### 20.2. Wrapper tạo bằng message mà không truyền cause

~~~java
catch (IOException original) {
    throw new DocumentLoadException(
            original.getMessage(),
            documentId,
            "LOAD",
            null);
}
~~~

Đây là **mã minh họa cố ý sai khi hợp đồng yêu cầu giữ cause**. Test message vẫn có thể PASS, nhưng <code>assertSame(original, thrown.getCause())</code> sẽ phát hiện lỗi.

### 20.3. Catch quá rộng

~~~java
catch (RuntimeException any) {
    throw new DocumentLoadException(
            "Không thể tải tài liệu",
            documentId,
            "LOAD",
            any);
}
~~~

Đây là **mã minh họa**. Nếu một bug <code>NullPointerException</code> ở service được chuyển thành lỗi đọc tài liệu, test chỉ kiểm tra có <code>DocumentLoadException</code> không phát hiện việc phân loại sai. Hãy thiết kế test cho những trường hợp **không nên bị chuyển lỗi** hoặc thu hẹp catch đúng loại hạ tầng.

## 21. Test negative cases: điều gì không nên xảy ra?

Kiểm thử tốt không chỉ xác nhận một nhánh lỗi mong muốn mà còn bảo vệ các nhánh tránh phân loại nhầm.

| Tình huống | Điều cần bảo vệ |
| --- | --- |
| Input trống | Validation exception có type/message phù hợp, không gọi repository |
| Input hợp lệ, repository thành công | Trả dữ liệu, không tạo exception |
| Repository ném IOException | Wrapper domain giữ context và cause |
| Repository ném một bug RuntimeException ngoài hợp đồng chuyển lỗi | Không bị đánh tráo thành lỗi validation hoặc “not found” |
| Cause message chứa secret | Public response không lộ secret |
| Message domain được localization | Error code ổn định; test message theo locale ở tầng phù hợp |
| Task async thất bại | Wrapper theo <code>join/get</code> có cause phù hợp |
| Try-with-resources vừa lỗi thao tác vừa lỗi đóng | Lỗi chính và suppressed phản ánh đúng hợp đồng |

### Kiểm tra dependency không bị gọi ở nhánh validation

Nếu bạn dùng fake repository có biến đếm:

~~~java
final class CountingRepository {
    int calls;

    String read(String id) {
        calls++;
        return "ok";
    }
}
~~~

Đây là **lớp minh họa**, không phải một test hoàn chỉnh. Khi ID sai, test có thể xác nhận method validation ném đúng exception và <code>calls == 0</code>. Điều này giúp bắt những implementation gọi I/O rồi mới validate.

Nhưng đừng gắn assertion “không gọi dependency” nếu thiết kế không công bố thứ tự đó hoặc nếu side effect không bị ảnh hưởng; hãy test điều kiện phản ánh yêu cầu thực sự.

## 22. Thử nghiệm message và cause trong một test có nhiều thông tin

Một pattern dễ đọc là nhóm các assertion theo trách nhiệm:

~~~java
DocumentLoadException error = assertThrowsExactly(
        DocumentLoadException.class,
        () -> service.loadDocument("DOC-7"));

assertAll("public exception contract",
        () -> assertEquals("DOCUMENT_UNAVAILABLE", error.code()),
        () -> assertEquals("DOC-7", error.documentId()),
        () -> assertEquals("LOAD", error.operation()));

assertAll("diagnostic cause",
        () -> assertNotNull(error.getCause()),
        () -> assertSame(original, error.getCause()));
~~~

Đây là **đoạn minh họa** với service, loại exception có <code>code()</code> và biến <code>original</code> được tạo ở phần chuẩn bị test. Các nhóm <code>assertAll</code> tách rõ dữ liệu mà caller dùng với dữ liệu chẩn đoán.

**Tránh test quá rộng:** không gom hàng chục tình huống độc lập vào một test duy nhất rồi khó xác định lỗi ở bước nào. Một test có thể có nhiều assertion khi tất cả cùng mô tả **một lần thất bại và một hợp đồng**.

Nếu message công khai được ánh xạ ở tầng controller, không bắt service exception đồng thời phải chứa đúng message công khai. Hãy kiểm tra tại đúng ranh giới.

## 23. Mức kiểm thử nào nên kiểm tra thuộc tính nào?

### Unit test lớp service

Phù hợp cho:

- Loại exception do service ném.
- Message hoặc error code tầng service được công bố.
- Context: document ID, operation, resource type.
- Cause: identity của exception nguồn.
- Đường input hợp lệ, validation và mapping lỗi.

### Integration test lớp repository

Phù hợp cho:

- Cách thư viện JDBC, filesystem hoặc HTTP client báo lỗi.
- Việc chuyển lỗi hạ tầng tại adapter theo contract.
- Tài nguyên được đóng đúng trong cả đường thành công và thất bại.

Tránh test integration phụ thuộc vào một hostname hoặc đường dẫn hệ điều hành không ổn định nếu không cần thiết.

### API contract test

Phù hợp cho:

- Status và error code client nhận được.
- Message công khai theo locale được chọn.
- Field ngữ cảnh được phép xuất.
- Không lộ secret, raw cause, stack trace hoặc thông tin hạ tầng.
- Correlation/request ID theo hợp đồng.

### End-to-end test

Phù hợp để chứng minh một workflow lỗi hiển thị thông báo đúng mức và có hướng xử lý, nhưng không nên thay mọi unit test bằng e2e đắt đỏ nếu chỉ cần khẳng định cause identity.

**Một thuộc tính nên được kiểm tra ở tầng sở hữu hợp đồng đó.** Kiểm tra cùng một message kỹ thuật ở tất cả tầng tạo test khó bảo trì và dễ làm rò rỉ chi tiết nội bộ.

## 24. Cách chọn assertion cho message biến động

Một quyết định thực hành:

| Message | Assertion gợi ý | Lý do |
| --- | --- | --- |
| Template công khai cố định của ứng dụng | <code>assertEquals(expected, message)</code> | Bảo vệ nội dung đã công bố |
| Message có đoạn ngữ nghĩa ổn định | <code>assertNotNull</code> + <code>contains</code>/<code>startsWith</code> | Bỏ qua bối cảnh phụ không thuộc hợp đồng |
| Message thay đổi theo locale | Kiểm tra theo locale cụ thể hoặc mã lỗi | Tránh test phụ thuộc locale mặc định của máy |
| Message do JDK/driver tạo | Kiểm tra type/cause; hạn chế exact text | Tránh khóa vào implementation |
| Message có secret nội bộ | Test public payload **không** chứa secret; internal log tuân thủ chính sách | Bảo mật |
| Message có ID nhưng API có field <code>documentId</code> | Assert field; message chỉ kiểm tra phần ổn định | Không parse dữ liệu từ text |
| Message được phép null | Kiểm tra hợp đồng nullable; không tự gọi <code>contains</code> | Phản ánh thực tế API |

Đừng dùng regex cực rộng như <code>.*error.*</code> chỉ để “test có text”. Nó có thể PASS khi nội dung không còn hữu ích. Cũng đừng dùng regex mô phỏng đúng từng dấu câu của một lỗi từ thư viện nếu hợp đồng không cam kết chúng.

## 25. Những lỗi phổ biến trong test exception

1. **Chỉ viết <code>assertThrows(Exception.class, ...)</code>:** có thể PASS vì lỗi không liên quan.
2. **Gọi lại service nhiều lần để kiểm tra từng thuộc tính:** thao tác có thể có side effect hoặc kết quả khác nhau.
3. **Chọn <code>assertThrowsExactly</code> khi subclass là hợp lệ:** test quá chặt với phân cấp exception.
4. **Chọn <code>assertThrows</code> quá rộng khi cần exact type:** test có thể bỏ sót chuyển loại sai.
5. **Assert exact message của JDK/driver:** dễ vỡ khi đổi runtime hoặc thư viện.
6. **Chỉ kiểm tra message chứa ID:** không bảo vệ field context có cấu trúc.
7. **Dùng <code>assertEquals</code> khi muốn chứng minh cùng Throwable instance:** hãy dùng <code>assertSame</code>.
8. **Chỉ kiểm tra cause không null:** có thể giữ nhầm exception.
9. **Chỉ kiểm tra cause root, bỏ qua loại wrapper domain:** có thể không phát hiện API mapping sai.
10. **Bắt nguyên stack trace thành snapshot cố định:** số dòng và frame không ổn định.
11. **Test lỗi I/O bằng file thật khi chỉ cần fake:** tạo test chậm và dễ phụ thuộc hệ điều hành.
12. **Dùng <code>catch (Exception)</code> trong test rồi assert <code>true</code>:** có thể bắt cả lỗi do chính assertion/test tạo ra.
13. **Không test nhánh thành công:** implementation luôn ném lỗi vẫn có thể PASS bộ test chỉ có negative case.
14. **Không kiểm tra public response:** service giữ cause đúng nhưng adapter có thể leak secret.
15. **Dùng <code>getCause()</code> như một API không bao giờ null:** test có thể ném NPE thay vì báo assertion rõ ràng.
16. **Không phân biệt <code>Future.get</code> và <code>CompletableFuture.join</code>:** assertion chọn sai wrapper exception.
17. **Không xét suppressed trong try-with-resources:** bỏ sót lỗi cleanup quan trọng khi hợp đồng yêu cầu.
18. **Test khẳng định mọi lỗi đều retryable:** retry phải tuân theo ngữ nghĩa và side effect thực tế.
19. **Đặt dữ liệu nhạy cảm thật trong fixture/test output:** dùng marker giả an toàn.
20. **Kiểm tra quá nhiều chi tiết nội bộ không được công bố:** test cản trở refactor hợp lệ.

## 26. Checklist trước khi hoàn tất bài test

Hãy tự hỏi:

1. Phương thức có **hợp đồng thất bại** rõ ràng không?
2. Test đã bắt đúng loại exception, hay chỉ một superclass quá rộng?
3. Có cần exact type, hay subclass cũng được chấp nhận?
4. Message có được công bố ổn định không? Nếu không, nên kiểm tra mã lỗi hoặc phần ngữ nghĩa nào?
5. Thuộc tính context nào caller thực sự cần: code, ID, operation, retryable, correlation ID?
6. Các field context được kiểm tra trực tiếp thay vì parse từ message chưa?
7. Nếu tầng đang wrap lỗi, test có giữ được object gốc để <code>assertSame</code> không?
8. Hợp đồng yêu cầu cause trực tiếp hay chỉ bảo toàn root cause trong chain?
9. Có cần kiểm tra suppressed exception khi cleanup lỗi không?
10. Test có tránh phụ thuộc vào raw stack trace, số dòng hoặc wording của JDK/driver không?
11. Nhánh thành công và nhánh validation đã được kiểm tra riêng chưa?
12. Nếu dùng fake/mocks, chúng có đủ xác định để tránh flaky test không?
13. Public response đã có test không lộ thông tin nhạy cảm từ exception gốc chưa?
14. Async API có được quan sát qua wrapper exception đúng hợp đồng không?
15. Khi thay implementation nhưng giữ contract, bộ test có thể tiếp tục PASS không?

## 27. Tóm tắt

- **Exception là một phần hợp đồng của API**, không chỉ là một sự kiện ném lỗi.
- Kiểm tra loại exception bằng <code>assertThrows</code> hoặc <code>assertThrowsExactly</code> theo yêu cầu chấp nhận subtype.
- Kiểm tra **message** khi nó có ý nghĩa và độ ổn định đã được công bố; tránh khóa vào wording tình cờ của JVM/thư viện.
- Kiểm tra **context** qua getter/field có cấu trúc, không tách ID từ chuỗi message.
- Kiểm tra **cause** bằng <code>assertSame(original, error.getCause())</code> khi cần bảo toàn cùng đối tượng; chọn kiểm tra chain nếu hợp đồng cho phép wrapper trung gian.
- Phân biệt cause, suppressed, lỗi async và thông báo công khai dành cho client.
- Dùng fake để tạo lỗi xác định, giữ test độc lập với filesystem hoặc hệ thống mạng.
- Kiểm thử đủ cả nhánh hợp lệ và thất bại, nhưng không ràng buộc những chi tiết triển khai không thuộc hợp đồng.

**Kết luận:** một bài kiểm thử exception tốt không dừng ở câu hỏi “có ném lỗi không?”. Nó xác minh **đúng loại lỗi, đủ ngữ cảnh, message phù hợp và cause được bảo toàn khi cần**, đồng thời không khiến test mong manh vì chi tiết tình cờ của JVM, log hay lớp hạ tầng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-testing-004">Kiểm tra suppressed exception và thứ tự đóng tài nguyên</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra suppressed exception và thứ tự đóng tài nguyên trong Java

## 1. Mục tiêu học tập

Khi dùng `try-with-resources`, Java giúp đóng tài nguyên tự động, nhưng một bài kiểm thử đúng không nên chỉ xác nhận tài nguyên đã được đóng. Cần kiểm tra **thứ tự đóng**, **exception nào là lỗi chính**, **exception nào được đưa vào `getSuppressed()`**, và liệu Java có tiếp tục cố đóng các tài nguyên khác sau khi một `close()` thất bại hay không.

Ví dụ đơn giản:

```java
try (Resource a = openA();
     Resource b = openB();
     Resource c = openC()) {
    doWork();
}
```

Đây là **mảnh mã minh họa**, cần tự định nghĩa `Resource` và các phương thức được gọi. Với các tài nguyên đã khởi tạo thành công, thứ tự thông thường là:

```text
Mở:   A → B → C
Thân:  doWork()
Đóng: C → B → A
```

Nếu `doWork()` ném lỗi E và các `close()` cũng ném, Java ưu tiên giữ **E là exception chính**. Lỗi đóng được gắn vào E dưới dạng **suppressed exceptions**, tránh mất dấu lỗi ban đầu trong khi vẫn giữ dữ liệu chẩn đoán các sự cố cleanup.

Nếu thân `try` thành công nhưng `close()` đầu tiên ném lỗi, exception từ `close()` đó trở thành **exception chính**; các lỗi đóng tài nguyên về sau được thêm vào danh sách suppressed của nó.

Bài này sử dụng ví dụ Java 17+ **biên dịch/chạy được**, xác nhận thứ tự event và danh sách suppressed bằng kiểm tra tự động, đồng thời chỉ ra cách viết test JUnit, những trường hợp biên và các lỗi triển khai thường gặp.

## 2. Những khái niệm cần phân biệt

**Primary exception** là Throwable được chọn làm thất bại chính của toàn khối `try-with-resources`. Trong trường hợp thân khối đã ném lỗi, lỗi thân thường là primary. Trong trường hợp thân thành công, lỗi đóng xuất hiện đầu tiên là primary nếu có lỗi đóng.

**Suppressed exception** là Throwable được ghi kèm primary bằng `Throwable.addSuppressed(...)`, thường biểu thị lỗi thứ cấp xảy ra trong khi đóng những tài nguyên còn lại. Có thể lấy ra bằng `Throwable.getSuppressed()`.

**Cause** là quan hệ `getCause()`, thường biểu diễn “lỗi này được gây ra bởi lỗi khác” khi ứng dụng bọc exception với constructor `new Exception(message, cause)`. Suppressed **không phải** cause; cả hai tồn tại độc lập trên cùng Throwable.

**LIFO (Last In, First Out)** trong ngữ cảnh đóng tài nguyên nghĩa là tài nguyên khai báo/khởi tạo sau được đóng trước.

**Cleanup failure** là lỗi từ `close()`, `finally`, rollback hoặc hành động dọn dẹp khác. Không phải cleanup failure nào cũng được Java tự gắn suppressed; hành vi chuẩn được thảo luận ở đây áp dụng cụ thể cho `try-with-resources` và các tình huống dùng `addSuppressed`.

**AutoCloseable** là interface của Java với phương thức `close()`. Kiểu `Closeable` của `java.io` kế thừa `AutoCloseable` và dùng kiểu exception hẹp hơn trong chữ ký `close()`. Tài nguyên phải phù hợp với hợp đồng đóng của nó; không phải mọi `close()` đều chỉ làm tác vụ đơn giản.

## 3. Quy tắc thứ tự của try-with-resources

Java xử lý resource specification theo hai chiều ngược nhau:

1. Các initializer trong header được đánh giá **từ trái sang phải**.
2. Thân `try` chỉ được bắt đầu khi các initializer cần thiết hoàn tất.
3. Khi rời khối, những tài nguyên đã khởi tạo thành công được gọi `close()` **từ phải sang trái**.
4. Java vẫn thử đóng tài nguyên trước đó khi một thao tác đóng ném exception.
5. Các `catch`/`finally` đặt **sau** thân `try-with-resources` được thực thi sau nỗ lực đóng tài nguyên, theo thứ tự ngữ nghĩa của câu lệnh.

Ví dụ:

```java
try (InputStream in = openInput();
     OutputStream out = openOutput()) {
    copy(in, out);
} catch (IOException ex) {
    report(ex);
}
```

Đây là **mảnh code** cần import `java.io.InputStream`, `OutputStream`, `IOException` và các hàm ứng dụng. Nếu cả hai đã được khởi tạo, thứ tự đóng là `out.close()` rồi `in.close()`. Khi vào `catch` ở bên ngoài resource header, nỗ lực đóng của cả hai đã được thực hiện.

Thứ tự đóng có thể quan trọng khi một wrapper phụ thuộc vào tài nguyên bên dưới. Ví dụ một stream được xây trên stream khác thường nên được tạo sau, rồi đóng trước. Tuy nhiên, nhiều wrapper có `close()` **tự đóng cả delegate**: đừng tùy tiện quản lý cùng một tài nguyên hai lần mà không xem hợp đồng idempotency/double close.

## 4. Trường hợp A: thân try lỗi và close cũng lỗi

Giả sử thân `try` ném `IllegalArgumentException("body-failed")`, rồi ba tài nguyên `C`, `B`, `A` đều ném trong `close()`. Java giữ một primary là lỗi thân:

```text
PRIMARY
  IllegalArgumentException("body-failed")
    SUPPRESSED
      IllegalStateException("close-C-failed")
      IllegalStateException("close-B-failed")
      IllegalStateException("close-A-failed")
```

Các sự kiện xảy ra theo thời gian:

```text
open-A
open-B
open-C
body
throw body-failed
close-C  -> throw close-C-failed  -> suppressed
close-B  -> throw close-B-failed  -> suppressed
close-A  -> throw close-A-failed  -> suppressed
propagate body-failed
```

**Điểm kiểm thử:** `getMessage()` của exception chính vẫn là `body-failed`, `getSuppressed().length == 3`, thông điệp của từng phần tử theo thứ tự `C, B, A`. Không được kết luận rằng vì `close-C` thất bại nên `close-B` và `close-A` không chạy.

Lưu ý: danh sách suppressed ghi theo **thứ tự các lỗi được thêm vào** trong quá trình cleanup. Với những `close()` ném trực tiếp trong ví dụ, nó đúng bằng thứ tự đóng ngược. Nếu code tự thêm các suppressed khác hoặc wrapper có cleanup lồng nhau, cần phân tích cấu trúc chính xác, không suy rộng rằng mọi mảng `getSuppressed()` trong mọi chương trình luôn phản ánh duy nhất thứ tự đóng resource header.

## 5. Trường hợp B: thân thành công, nhiều close cùng lỗi

Nếu thân hoàn tất bình thường nhưng `C.close()` thất bại đầu tiên, `C` được xem là exception chính:

```text
PRIMARY
  IllegalStateException("close-C-failed")
    SUPPRESSED
      IllegalStateException("close-B-failed")
      IllegalStateException("close-A-failed")
```

Thứ tự:

```text
open-A
open-B
open-C
body completed normally
close-C  -> failure becomes primary
close-B  -> failure suppressed on close-C failure
close-A  -> failure suppressed on close-C failure
propagate close-C-failed
```

**Bài test phải phân biệt lỗi body và lỗi close.** Không phải mọi exception thoát khỏi `try-with-resources` đều là lỗi trong thân. Nếu tác vụ đọc/ghi hoàn tất rồi `close` gặp lỗi flush/commit, tình trạng dữ liệu cần được xác minh; không nên tự động cho rằng thân chưa thực hiện.

Với `OutputStream` hoặc tài nguyên có buffering, một số lỗi ghi có thể bộc lộ **lúc flush/close**, không phải trong lệnh `write` trước đó. Vì vậy việc assert thân đã chạy không tương đương mọi dữ liệu đã được lưu bền vững.

## 6. Trường hợp C: mở một tài nguyên phía sau thất bại

Giả sử `A` và `B` đã khởi tạo, nhưng việc tạo `C` ném exception. Khi đó:

- Thân `try` **không chạy**.
- `C` chưa được khởi tạo thành công nên **không có tài nguyên C trong header để Java gọi close**.
- Những tài nguyên đã khởi tạo trước đó (`B`, `A`) vẫn được đóng theo thứ tự ngược.
- Exception do khởi tạo `C` tạo ra là **primary**.
- Nếu `B.close()`, `A.close()` cũng thất bại, chúng được gắn suppressed theo thứ tự `B` rồi `A`.

```text
open-A
open-B
attempt to open C -> throw open-C-failed
close-B -> suppressed close-B-failed
close-A -> suppressed close-A-failed
propagate open-C-failed
```

Một constructor có thể cấp phát tài nguyên nội bộ rồi ném trước khi trả object. Trong trường hợp đó, `try-with-resources` **không thể tự gọi `close()` trên object chưa được đăng ký thành công**. Constructor/factory phải quản lý rollback, cleanup các tài nguyên tạm của chính nó. Đây là trường hợp cần test riêng khi xây resource phức tạp.

## 7. Chương trình Java 17+ độc lập, có xác nhận tự động

Ví dụ sau dùng `AutoCloseable` tự tạo, danh sách sự kiện, các exception có message dễ nhận biết và hàm `verify(...)`. Nó kiểm tra **ba trường hợp**: lỗi thân + lỗi close, chỉ lỗi close, và lỗi khởi tạo. Nếu một kết quả không đúng, chương trình ném `AssertionError` thay vì in `TESTS: PASS`.

Tạo file **`SuppressedCloseOrderDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SuppressedCloseOrderDemo {
    private static final List<String> EVENTS = new ArrayList<>();

    static final class NamedResource implements AutoCloseable {
        private final String name;
        private final boolean failOnClose;

        NamedResource(String name, boolean failOnClose) {
            this.name = name;
            this.failOnClose = failOnClose;
            EVENTS.add("open-" + name);
        }

        @Override
        public void close() {
            EVENTS.add("close-" + name);
            if (failOnClose) {
                throw new IllegalStateException("close-" + name + "-failed");
            }
        }
    }

    static NamedResource failToOpen(String name) {
        EVENTS.add("open-attempt-" + name);
        throw new IllegalArgumentException("open-" + name + "-failed");
    }

    static Throwable runBodyCase(boolean failBody) {
        EVENTS.clear();
        try (NamedResource a = new NamedResource("A", true);
             NamedResource b = new NamedResource("B", true);
             NamedResource c = new NamedResource("C", true)) {
            EVENTS.add("body");
            if (failBody) {
                throw new IllegalArgumentException("body-failed");
            }
        } catch (Exception ex) {
            return ex;
        }
        throw new AssertionError("Expected a failure");
    }

    static Throwable runInitCase() {
        EVENTS.clear();
        try (NamedResource a = new NamedResource("A", true);
             NamedResource b = new NamedResource("B", true);
             NamedResource c = failToOpen("C")) {
            EVENTS.add("body");
        } catch (Exception ex) {
            return ex;
        }
        throw new AssertionError("Expected initialization failure");
    }

    static String suppressed(Throwable error) {
        return Arrays.stream(error.getSuppressed())
                .map(Throwable::getMessage)
                .collect(Collectors.joining(","));
    }

    static void verify(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected: " + expected + ", actual: " + actual);
        }
    }

    public static void main(String[] args) {
        Throwable bodyError = runBodyCase(true);
        String bodyEvents = String.join(",", EVENTS);
        verify(bodyEvents, "open-A,open-B,open-C,body,close-C,close-B,close-A");
        verify(bodyError.getMessage(), "body-failed");
        verify(suppressed(bodyError),
                "close-C-failed,close-B-failed,close-A-failed");
        System.out.println("BODY_EVENTS: " + bodyEvents);
        System.out.println("BODY_PRIMARY: " + bodyError.getMessage());
        System.out.println("BODY_SUPPRESSED: " + suppressed(bodyError));

        Throwable closeError = runBodyCase(false);
        String closeEvents = String.join(",", EVENTS);
        verify(closeEvents, "open-A,open-B,open-C,body,close-C,close-B,close-A");
        verify(closeError.getMessage(), "close-C-failed");
        verify(suppressed(closeError), "close-B-failed,close-A-failed");
        System.out.println("CLOSE_EVENTS: " + closeEvents);
        System.out.println("CLOSE_PRIMARY: " + closeError.getMessage());
        System.out.println("CLOSE_SUPPRESSED: " + suppressed(closeError));

        Throwable initError = runInitCase();
        String initEvents = String.join(",", EVENTS);
        verify(initEvents, "open-A,open-B,open-attempt-C,close-B,close-A");
        verify(initError.getMessage(), "open-C-failed");
        verify(suppressed(initError), "close-B-failed,close-A-failed");
        System.out.println("INIT_EVENTS: " + initEvents);
        System.out.println("INIT_PRIMARY: " + initError.getMessage());
        System.out.println("INIT_SUPPRESSED: " + suppressed(initError));
        System.out.println("TESTS: PASS");
    }
}
```

Biên dịch và chạy từ thư mục chứa file, sử dụng JDK 17 trở lên:

```bash
javac -encoding UTF-8 SuppressedCloseOrderDemo.java
java SuppressedCloseOrderDemo
```

**Standard output đã kiểm chứng bằng `javac` và `java`:**

```text
BODY_EVENTS: open-A,open-B,open-C,body,close-C,close-B,close-A
BODY_PRIMARY: body-failed
BODY_SUPPRESSED: close-C-failed,close-B-failed,close-A-failed
CLOSE_EVENTS: open-A,open-B,open-C,body,close-C,close-B,close-A
CLOSE_PRIMARY: close-C-failed
CLOSE_SUPPRESSED: close-B-failed,close-A-failed
INIT_EVENTS: open-A,open-B,open-attempt-C,close-B,close-A
INIT_PRIMARY: open-C-failed
INIT_SUPPRESSED: close-B-failed,close-A-failed
TESTS: PASS
```

### Đọc kết quả kiểm chứng

- `BODY_EVENTS` chứng minh mở `A,B,C` từ trái sang phải, thực hiện body, sau đó đóng `C,B,A` theo LIFO.
- `BODY_PRIMARY` là lỗi body; thứ tự suppressed đúng `C,B,A`.
- `CLOSE_PRIMARY` là lỗi ở `C.close()` vì thân không lỗi; `B` và `A` vẫn được gọi.
- `INIT_EVENTS` cho thấy **không có `body` và không có `close-C`**, do khởi tạo C chưa thành công; B/A vẫn đóng.
- Hàm `verify` kiểm tra giá trị chính xác. Nếu đổi thứ tự event hoặc mất một suppressed, phép kiểm tra ném `AssertionError` và không in `TESTS: PASS`.
- Mỗi case xóa `EVENTS` trước khi chạy; các tài nguyên luôn chạy tuần tự trên main thread nên demo **không** dựa vào thứ tự scheduling bất định.
- Các exception từ `close()` là `IllegalStateException` (unchecked), vì vậy phương thức override `close()` không cần khai báo `throws Exception`. Một tài nguyên thật có thể dùng checked exception và cần xử lý theo chữ ký thích hợp.

Dùng bộ test này để học **quy tắc Java**, không sử dụng `NamedResource` giả làm tài nguyên production. Các thao tác real-world như DB connection, stream flush, socket close có thể có side effects, timeout hoặc semantics riêng.

## 8. Viết JUnit 5 test: assert thứ tự và suppressed

Khi chuyển ví dụ thành test thực tế, có thể dùng **JUnit Jupiter (JUnit 5)**. Đoạn sau là *phương thức kiểm thử minh họa*, cần đặt trong một test class, thêm thư viện JUnit 5 và import phù hợp. Nó không phải chương trình Java SE độc lập:

```java
@Test
void bodyFailureKeepsCloseErrorsSuppressed() {
    List<String> events = new ArrayList<>();

    class R implements AutoCloseable {
        final String name;
        R(String name) {
            this.name = name;
            events.add("open-" + name);
        }
        @Override
        public void close() {
            events.add("close-" + name);
            throw new IllegalStateException("close-" + name);
        }
    }

    IllegalArgumentException thrown = assertThrows(
            IllegalArgumentException.class, () -> {
                try (R a = new R("A");
                     R b = new R("B");
                     R c = new R("C")) {
                    events.add("body");
                    throw new IllegalArgumentException("body");
                }
            });

    assertEquals("body", thrown.getMessage());
    assertEquals(List.of(
            "open-A", "open-B", "open-C", "body",
            "close-C", "close-B", "close-A"), events);
    assertEquals(List.of("close-C", "close-B", "close-A"),
            Arrays.stream(thrown.getSuppressed())
                  .map(Throwable::getMessage)
                  .toList());
}
```

Các import cần thiết (không phải phần của method ở trên):

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
```

Trong JUnit test này, kiểm tra ba yếu tố độc lập:

1. **Loại và message primary:** `assertThrows(IllegalArgumentException.class, ...)` và `getMessage()`.
2. **Thứ tự lifecycle:** danh sách event phải khớp đầy đủ từ open đến close.
3. **Thứ tự suppressed:** `getSuppressed()` là các lỗi từ C, B, A.

Nếu chỉ assert `thrown.getSuppressed().length == 3` thì vẫn có thể bỏ lọt bug gọi `close()` sai thứ tự. Nếu chỉ assert event, có thể bỏ lọt bug **không bảo toàn lỗi**. Kết hợp cả hai tạo một phép thử hữu ích hơn.

Dùng `assertThrowsExactly` khi cần phân biệt loại lỗi chính xác và phiên bản JUnit hỗ trợ; còn `assertThrows` chấp nhận cả subtype của kiểu kỳ vọng. Không cần assert toàn bộ stack trace text vì các tên frame và dòng source có thể thay đổi theo compiler/runtime.

## 9. Ma trận kiểm thử nên bao phủ

| Tình huống | Primary mong đợi | Suppressed | Thứ tự close |
| --- | --- | --- | --- |
| Thân thành công, mọi close thành công | Không có exception | Rỗng | C → B → A |
| Thân lỗi, mọi close thành công | Lỗi body | Rỗng | C → B → A |
| Thân lỗi, chỉ C close lỗi | Lỗi body | C | C → B → A |
| Thân lỗi, C và A close lỗi | Lỗi body | C rồi A | C → B → A |
| Thân lỗi, cả ba close lỗi | Lỗi body | C rồi B rồi A | C → B → A |
| Thân thành công, C close lỗi | Lỗi close-C | Rỗng | C → B → A |
| Thân thành công, C/B/A close lỗi | Lỗi close-C | B rồi A | C → B → A |
| Khởi tạo C lỗi, B/A close thành công | Lỗi khởi tạo C | Rỗng | B → A |
| Khởi tạo C lỗi, B/A close lỗi | Lỗi khởi tạo C | B rồi A | B → A |
| Khởi tạo A lỗi | Lỗi khởi tạo A | Thường rỗng, nếu constructor tự thêm lỗi thì tùy thiết kế | Không có resource header đã khởi tạo để đóng |
| Header có resource tham chiếu null | Tùy body và các resource khác | Không có lỗi close của resource null | Resource null bị bỏ qua |

Đây là **ma trận để thiết kế test**, không phải mọi hệ thống đều cần viết đủ một test riêng cho từng dòng nếu đã được bao phủ bởi parameterized tests hoặc component contract. Với thư viện quản lý tài nguyên, các ca lỗi khởi tạo và lỗi khi đóng là quan trọng nhất vì bug có thể chỉ xuất hiện trong đường thất bại hiếm gặp.

Một chiến lược tốt là tạo `FakeResource` hỗ trợ ba lựa chọn: không lỗi, lỗi khởi tạo, lỗi đóng, rồi chạy các tổ hợp cần thiết. Mỗi test phải xác định rõ **primary**, **getSuppressed()** và **event order** thay vì chỉ nhìn một Boolean “closed”.

## 10. Suppressed exception khác cause như thế nào?

Hai API của `Throwable`:

```java
Throwable cause = error.getCause();
Throwable[] cleanupFailures = error.getSuppressed();
```

Đây là mảnh code bên trong phương thức có `Throwable error`.

- `getCause()` chỉ một liên hệ nguyên nhân khi exception được bọc hoặc gán cause.
- `getSuppressed()` trả **mảng** các Throwable được gắn bằng `addSuppressed`, ví dụ những lỗi xảy ra trong khi đóng các resource còn lại.
- Có thể tồn tại **cả cause và suppressed trên cùng Throwable**.
- Một suppressed exception cũng có thể có **cause** và **suppressed riêng**, tạo cấu trúc lỗi nhiều tầng.

Ví dụ:

```text
OrderProcessingException (primary)
  Caused by: SQLException
  Suppressed: IOException("close output")
  Suppressed: IOException("close input")
```

Đây là cấu trúc minh họa, không phải output bắt buộc của một runtime. `cause` nói về nguồn thất bại nghiệp vụ, còn suppressed ghi những lỗi cleanup **không nên thay thế nguyên nhân chính**.

Nếu dùng `logger.error("failed", error)`, nhiều logging framework in cả suppressed lẫn cause. Khi điều tra, đừng bỏ qua các dòng `Suppressed: ...` của stack trace. Cũng không nên nhầm “suppressed” là “đã được xử lý an toàn và có thể bỏ qua”: việc close thất bại có thể liên quan đến mất dữ liệu, khóa, transaction hoặc tài nguyên không được giải phóng.

## 11. Cơ chế gần tương đương do compiler sinh

`try-with-resources` không đơn giản là:

```java
try {
    body();
} finally {
    resource.close();
}
```

Mẫu `finally` thủ công này **có thể làm exception của `close()` che exception từ `body()`**, nên không giữ đúng hành vi suppressed của try-with-resources.

Về ý tưởng, với một resource, compiler cần một Throwable primary và xử lý lỗi close theo hướng:

```java
Throwable primary = null;
try {
    body();
} catch (Throwable thrown) {
    primary = thrown;
    throw thrown;
} finally {
    if (resource != null) {
        if (primary != null) {
            try {
                resource.close();
            } catch (Throwable closeFailure) {
                primary.addSuppressed(closeFailure);
            }
        } else {
            resource.close();
        }
    }
}
```

Đây là **mã giả gần cấu trúc**, không phải chương trình độc lập; `resource` và `body()` đã giả định tồn tại. Nó giúp hiểu tại sao lỗi body vẫn được ưu tiên và lỗi close thứ cấp được thêm vào primary. Không nên tự sao chép đoạn này để thay thế cú pháp Java: code sinh thật phải xử lý nhiều resource, scopes, khởi tạo, loại exception và các chi tiết ngôn ngữ khác.

Khi nhiều tài nguyên được khai báo, có thể xem về mặt khái niệm như nhiều lớp `try-with-resources` lồng nhau, đảm bảo **thứ tự close ngược**. Cần nhớ resource đã đóng trước khi chạy catch/finally bên ngoài.

Tại sao code generated có thể bắt `Throwable`? Vì nó phải bảo toàn primary bất kể body ném loại Throwable nào, **khác với lời khuyên viết `catch (Throwable)` trong business layer rồi nuốt mọi lỗi**. Đây là chi tiết của cơ chế compiler, không phải chính sách “phục hồi mọi Error”.

## 12. Kiểm thử close khi tài nguyên khởi tạo thất bại

Không chỉ thân try mà **initializer** cũng có thể ném exception:

```java
try (Connection con = openConnection();
     PreparedStatement ps = con.prepareStatement(sql);
     ResultSet rs = ps.executeQuery()) {
    consume(rs);
}
```

Đây là **mảnh code minh họa JDBC**, cần import `java.sql.Connection`, `PreparedStatement`, `ResultSet`, và các phương thức/biến `openConnection`, `sql`, `consume`. Nếu `ps.executeQuery()` ném lỗi khi khởi tạo `rs`:

- `rs` chưa được nhận như resource đã khởi tạo thành công nên không được close bằng header.
- `ps` được đóng.
- `con` được đóng.
- Nếu `ps.close()` và `con.close()` ném, chúng có thể xuất hiện trong suppressed của exception từ `executeQuery()`.

Test unit có thể dùng fake JDBC hoặc mock có kiểm soát, nhưng integration test với driver thật còn cần kiểm tra **semantics transaction, rollback, connection pooling**. Đừng mặc định `Connection.close()` tương đương transaction rollback trong mọi tình huống; nó tùy driver và transaction configuration.

Nếu constructor/factory của một resource ném sau khi mở file descriptor nội bộ, nó phải tự dọn dẹp phần đã cấp phát; TWR không thể gọi close cho biến chưa được gán thành công.

## 13. Thứ tự close với resource lồng nhau và wrapper

Hai cú pháp có thể tương đương về thứ tự nếu không có side effects khác:

```java
try (A a = createA();
     B b = createB(a)) {
    use(b);
}
```

và một cấu trúc lồng:

```java
try (A a = createA()) {
    try (B b = createB(a)) {
        use(b);
    }
}
```

Đều là **mảnh code cần định nghĩa các kiểu/hàm ứng dụng**; `b` đóng trước `a` khi cả hai đã tạo được.

Nhưng một wrapper `B` có thể tự động đóng `A` khi gọi `B.close()`. Khi đó nếu cả hai cũng nằm trong header, `A.close()` có thể được gọi thêm lần nữa. Với những resource có close idempotent, điều này có thể không ảnh hưởng. Với resource khác, double close có thể lỗi hoặc gây side effect. Kiểm thử phải căn cứ **hợp đồng thật của wrapper/delegate**, không chỉ quy tắc ngôn ngữ.

### External resource từ Java 9

Java 9+ cho phép dùng một biến resource đã khai báo trước nếu biến final hoặc effectively final:

```java
InputStream in = Files.newInputStream(path);
try (in) {
    use(in);
}
```

Đây là **mảnh code**, cần import `java.io.InputStream`, `java.nio.file.Files`, và `Path` tương ứng; phương thức bên ngoài phải xử lý `IOException`. Khi ra khỏi `try`, `in` đã được đóng, dù biến vẫn tồn tại. Không tiếp tục sử dụng stream đó như còn mở và không bọc thêm một `try` thứ hai để đóng lại nếu loại resource không cho phép.

Trong mọi trường hợp, thứ tự close vẫn dựa trên **resource specification của từng try**, cùng cấu trúc lồng nhau.

## 14. Resource tham chiếu null, close không idempotent và những trường hợp biên

TWR có thể có một resource reference là `null`:

```java
AutoCloseable maybeResource = null;
try (maybeResource) {
    perform();
}
```

Đây là mảnh code Java 9+ với `perform()` do ứng dụng định nghĩa; nếu `maybeResource` không được gán lại, nó effectively final. Java **không gọi `close()`** trên resource reference null, vì vậy không có `NullPointerException` tự phát sinh chỉ do bước close của tham chiếu ấy.

Tuy nhiên, **không nên coi null resource là API design lý tưởng**. Nếu sự tồn tại resource là điều kiện bắt buộc, kiểm tra sớm bằng `Objects.requireNonNull` hoặc thiết kế factory trả giá trị rõ nghĩa.

Một số tình huống biên cần kiểm thử theo yêu cầu hệ thống:

- `close()` không idempotent: gọi hai lần có thể ném lỗi hoặc làm thay đổi trạng thái.
- `close()` có thứ tự phụ thuộc: wrapper/delegate, transaction/session, stream/filter.
- `close()` block lâu hoặc không trả về: TWR chỉ bảo đảm **cố gọi**, không có deadline mặc định.
- `close()` ném `Error` thay vì checked exception: cơ chế suppression có thể liên quan Throwable loại Error; đừng tự catch rồi tiếp tục.
- `close()` làm thay đổi dữ liệu hoặc flush: thất bại khi close có thể nghiêm trọng dù body thành công.
- Cleanup tự gắn suppressed vào exception qua `addSuppressed`; kiểm tra nội dung thay vì chỉ đếm số lượng.
- Một resource được chia sẻ cho nhiều owner: không để hai owner cùng nghĩ mình chịu trách nhiệm đóng.

Các tình huống lỗi lạ nên được kiểm thử bằng fake resource, còn semantics của driver/native resource cần integration test.

## 15. Các lưu ý về Throwable.addSuppressed

`Throwable.addSuppressed(Throwable exception)` thêm một Throwable thứ cấp vào đối tượng hiện tại. Một số điểm đáng nhớ:

- `getSuppressed()` trả mảng các exception được gắn; không phải `getCause()`.
- `addSuppressed(null)` gây `NullPointerException`.
- Không được tự suppress chính đối tượng Throwable lên nó (*self-suppression*); thao tác có thể ném `IllegalArgumentException`.
- Một số subclass có thể dùng constructor `Throwable` đặc biệt để **tắt cơ chế suppression**; khi đó `getSuppressed()` không giữ các lỗi thêm vào theo cách thông thường.
- Trong code ứng dụng tự quản lý cleanup thủ công, phải cẩn thận giữ exception chính đúng ý nghĩa. Mặc định TWR đã giải quyết trường hợp tiêu chuẩn này tốt hơn.

**Trường hợp cực đoan self-suppression** có thể xảy ra nếu resource `close()` cố tình ném lại **chính instance Throwable** đã được ném từ body. Đây không phải hợp đồng close bình thường. Đừng giả định mọi resource độc hại hoặc sai hợp đồng đều tuân theo mô hình lỗi đơn giản; có thể phát sinh lỗi khác từ thao tác thêm suppressed.

Nói cách khác, tài nguyên nên có exception mới/đúng bản chất khi fail close, không tái sử dụng cùng đối tượng Throwable đang là primary.

## 16. finally và catch ngoài try-with-resources

Xét:

```java
try (Resource r = open()) {
    work();
} catch (Exception ex) {
    inspect(ex.getSuppressed());
} finally {
    recordCompletion();
}
```

Đây là mảnh code có kiểu `Resource`, các hàm `open`, `work`, `inspect`, `recordCompletion`. Khi `catch` nhận exception, Java đã thực hiện nỗ lực `r.close()`. Nếu body lỗi và close cũng lỗi, `catch` có thể đọc suppressed trực tiếp tại thời điểm đó.

**Cảnh báo:** nếu `catch` hoặc `finally` của bạn **tự ném exception mới**, hoặc `recordCompletion()` ném, lỗi được quan sát cuối cùng có thể khác và che nguyên nhân ban đầu nếu không được bảo toàn. `try-with-resources` quản lý lỗi từ **resource close trong header**, không tự bảo toàn mọi exception phát sinh từ mọi `finally` bạn tự viết.

Ví dụ sai:

```java
try (Resource r = open()) {
    work();
} finally {
    throw new IllegalStateException("reporting failed");
}
```

Đây là mảnh **không khuyến nghị**. Nó cố tình ném ở `finally` và có thể làm lỗi primary từ body/close không còn là exception được lan truyền. Khi viết code quan sát/metric/cleanup bổ sung, đảm bảo không vô tình thay thế lỗi quan trọng hoặc làm log trở nên không đáng tin.

## 17. Đọc stack trace và cấu trúc suppressed

Java `Throwable.printStackTrace()` thường hiển thị các suppressed exception với nhãn `Suppressed:`, ví dụ minh họa:

```text
java.lang.IllegalArgumentException: body-failed
    at Demo.work(Demo.java:10)
    Suppressed: java.lang.IllegalStateException: close-C-failed
        at Demo$Resource.close(Demo.java:25)
    Suppressed: java.lang.IllegalStateException: close-B-failed
        at Demo$Resource.close(Demo.java:25)
    Suppressed: java.lang.IllegalStateException: close-A-failed
        at Demo$Resource.close(Demo.java:25)
```

Đây chỉ là **stack trace minh họa**, không phải output của `SuppressedCloseOrderDemo.java` và không bảo đảm số dòng/format. Trong hệ thống thật, trình logging có thể cấu hình giới hạn stack trace; collector hoặc APM có thể làm mất các phần suppressed nếu không hỗ trợ hoặc bị truncate.

Khi điều tra một lỗi, hãy hỏi:

1. Exception chính là lỗi body, initializer hay close?
2. Danh sách suppressed có bao nhiêu phần tử và theo thứ tự nào?
3. Mỗi suppressed có cause/suppressed riêng không?
4. Tài nguyên nào đã đóng, tài nguyên nào chưa bao giờ khởi tạo thành công?
5. Nếu close thất bại, có dữ liệu chưa flush hoặc transaction chưa hoàn tất không?

Trong log chứa dữ liệu thật, kiểm tra chính sách bảo vệ **secret và thông tin cá nhân**: cause/suppressed message từ driver có thể chứa URL, SQL, token hoặc payload. Không tự động đưa toàn bộ Throwable ra HTTP response hoặc các sink rộng quyền chỉ vì muốn kiểm tra suppressed.

## 18. Lỗi thường gặp khi kiểm thử

| Sai lầm | Vì sao sai | Cách sửa |
| --- | --- | --- |
| Chỉ assert có exception được ném | Không biết đó là lỗi thân hay lỗi close | Assert loại, message/cause, suppressed và event order |
| Cho rằng resource đóng theo thứ tự A → B → C | Trái LIFO của header | Kiểm tra C → B → A |
| Nghĩ close-C lỗi thì không gọi close-B/A | TWR vẫn thử đóng tài nguyên còn lại | Fake resource ghi event của mọi close |
| Kiểm tra suppressed bằng `getCause()` | Cause và suppressed là hai quan hệ khác | Dùng `getSuppressed()` |
| Assert số suppressed mà bỏ qua thứ tự | Không bắt được lỗi đóng sai thứ tự | Assert cả mảng message theo thứ tự |
| Coi tất cả lỗi close luôn là suppressed | Nếu body thành công, lỗi close đầu tiên là primary | Test cả case body thành công |
| Bỏ qua lỗi lúc khởi tạo resource giữa header | Tài nguyên trước phải vẫn được đóng | Test init fail ở A/B/C |
| Dùng close thật từ DB/network để ép lỗi theo timing | Test chậm, flaky, khó dự đoán | Unit test với fake, integration test riêng |
| Bọc `catch (Throwable)` rồi swallow tất cả | Có thể che `Error` và sai kết quả | Dựa vào TWR, phân loại lỗi tại boundary |
| Tự viết `finally` và để close che body exception | Mất nguyên nhân chính | Dùng `try-with-resources` |
| Test bằng full text của stack trace | Phụ thuộc version/dòng source | Assert Throwable API và sự kiện |
| Tưởng close thành công là dữ liệu đã commit bền vững | API tài nguyên có semantics riêng | Test transaction/flush/durability riêng |

## 19. Chiến lược unit test và integration test

**Unit test** là nơi tốt nhất để xác nhận cơ chế Java, dùng resource giả có hành vi được điều khiển:

- Thu danh sách event `open-X`, `body`, `close-X`.
- Tạo lỗi ở body/initializer/close theo cờ.
- Assert loại exception chính, message, `getSuppressed()`, thứ tự gọi.
- Không phụ thuộc kết nối mạng, driver, filesystem hoặc đồng hồ.

**Integration test** kiểm tra thêm contract thực tế của tài nguyên:

- `Connection.close()` dưới connection pool có trả handle về pool hay thực sự đóng kết nối vật lý?
- `ResultSet`, `Statement`, `Connection` có quan hệ tự đóng lẫn nhau không?
- `OutputStream.close()` có flush dữ liệu và báo lỗi không?
- Tài nguyên gắn transaction có rollback/commit ra sao khi close lỗi?
- Socket, file, lock và scheduler có timeout/cancellation phù hợp không?

Không dùng fake resource để “chứng minh” JDBC rollback hoặc file durability; fake chỉ kiểm tra logic code của ứng dụng và ngữ nghĩa Java đã lập trình.

Các bài test liên quan luồng nên có timeout và đồng bộ rõ ràng. Tuy nhiên, quy tắc đóng của **một try-with-resources statement đồng bộ** không cần concurrency để test; tạo nhiều thread không cải thiện độ chính xác của thứ tự resource header và chỉ khiến kết quả khó lặp lại.

## 20. Checklist trước khi hoàn tất một bài kiểm thử

1. Xác định thứ tự resource **khởi tạo thành công** và thứ tự close ngược.
2. Có case **thân lỗi**, **thân thành công nhưng close lỗi**, **initializer lỗi**.
3. Assert exception chính đúng nơi phát sinh.
4. Assert từng phần tử `getSuppressed()` đúng số lượng, nội dung, thứ tự.
5. Xác nhận những tài nguyên đã khởi tạo vẫn được thử đóng sau close failure.
6. Xác nhận tài nguyên không khởi tạo thành công không được close bởi header.
7. Với lỗi cleanup, không nhầm suppressed với cause và không tự động nuốt lỗi.
8. Kiểm tra `catch`/`finally` ngoài TWR không thay thế lỗi gốc.
9. Phân biệt code demo/fake với tác dụng phụ của resource thật.
10. Đọc stack trace, cause và suppressed có kiểm soát để tránh rò thông tin nhạy cảm.
11. Không dùng full stack trace text làm assertion chính của unit test.
12. Với hệ thống có DB/file/network, bổ sung integration test cho flush, rollback, close idempotency và trạng thái sau lỗi.

**Ghi nhớ:** tài nguyên trong `try-with-resources` được **khởi tạo từ trái sang phải, đóng từ phải sang trái**. Nếu body hoặc initializer đã lỗi, lỗi đó thường giữ vai trò **primary**, còn lỗi đóng được lưu bằng `getSuppressed()`. Nếu body thành công, lỗi close đầu tiên trở thành primary. Một phép kiểm thử tốt cần xác nhận **cả thứ tự sự kiện lẫn cấu trúc Throwable** để không đánh mất thông tin lỗi và không tưởng rằng cleanup đã thành công khi thực tế chưa.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-testing-005">Kiểm tra trạng thái đối tượng sau khi phương thức thất bại</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra trạng thái đối tượng sau khi phương thức thất bại

Một bài kiểm thử exception không nên chỉ hỏi **“phương thức đã ném đúng exception chưa?”**. Câu hỏi quan trọng không kém là: **“sau khi ném, đối tượng còn ở trạng thái nào?”** Nếu phương thức đã trừ số dư, thêm một phần tử, đổi trạng thái đơn hàng hoặc giữ tài nguyên rồi mới phát hiện lỗi, caller có thể nhận exception đúng loại trong khi đối tượng đã bị thay đổi một phần và không còn hợp lệ.

Ví dụ: `reserve(10)` ném `InsufficientStockException` nhưng kho đã bị trừ 10; `transfer()` ném `IOException` khi gửi thông báo nhưng số tiền ở tài khoản nguồn đã bị trừ; `registerListener()` ném giữa chừng nhưng listener vẫn ở trong danh sách đăng ký. Những lỗi này **không được phát hiện** nếu test chỉ dùng `assertThrows`.

Chủ đề này thuộc **Kiểm thử** trong Java Core – Exception. Mục tiêu là biết cách định nghĩa bảo đảm an toàn khi có exception, chụp trạng thái trước/sau, kiểm tra invariant và side effect, mô phỏng lỗi ở các mốc thực thi, và viết test Java 17 tin cậy.

## 1. Exception đúng loại chưa bảo đảm object đúng trạng thái

Đoạn code sau chỉ **minh họa anti-pattern**, chưa phải chương trình độc lập:

```java
class Inventory {
    private int stock = 5;

    void reserve(int quantity) {
        stock -= quantity;  // Đổi trạng thái trước.
        if (stock < 0) {
            throw new IllegalStateException("not enough stock");
        }
    }
}
```

Nếu caller gọi `reserve(10)`, phương thức ném `IllegalStateException` như mong đợi, nhưng `stock` đã thành `-5`. Một test chỉ kiểm tra exception sẽ **PASS dù đối tượng sai**.

```java
assertThrows(IllegalStateException.class,
        () -> inventory.reserve(10)); // Chưa đủ.
```

Đây là snippet dùng JUnit 5, cần dự án đã cài thư viện JUnit và biến `inventory`. Sau khi ném, test còn phải kiểm tra stock, danh sách giữ chỗ, các cờ trạng thái và tài nguyên liên quan theo hợp đồng.

Một test đầy đủ về logic có dạng:

```text
Given: trạng thái ban đầu hợp lệ S0
When:  gọi phương thức với lỗi được chủ động kích hoạt
Then:  exception đúng loại/nguyên nhân
And:   trạng thái sau lỗi thỏa mức bảo đảm đã công bố
And:   các invariant vẫn đúng
And:   tài nguyên và side effect không rò rỉ
```

Không phải phương thức nào cũng phải giữ nguyên trạng thái sau mọi lỗi. Điều cần kiểm tra là **mức bảo đảm mà API hứa**: giữ nguyên hoàn toàn, giữ trạng thái hợp lệ nhưng có thay đổi một phần, hay chỉ cleanup một số tài nguyên.

## 2. Ba mức bảo đảm an toàn khi có exception

Một cách phân loại hữu ích là các mức *exception safety guarantee*. Chúng mô tả **trạng thái quan sát được sau thất bại**, không chỉ loại exception được ném.

| Mức bảo đảm | Điều được bảo đảm khi lỗi | Ví dụ |
| --- | --- | --- |
| **No-throw guarantee** | Thao tác theo contract không ném exception, thường là cleanup rất đơn giản; cần xác định rõ phạm vi bảo đảm | `close()` của một handle in-memory được thiết kế không ném. |
| **Strong exception guarantee** | Nếu thao tác thất bại thì trạng thái quan sát được không thay đổi so với trước thao tác; “commit hoặc không đổi” | Tạo state mới rồi chỉ gán sau khi mọi bước có thể lỗi thành công. |
| **Basic exception guarantee** | Dù thất bại, các invariant của đối tượng vẫn đúng và không rò rỉ tài nguyên, nhưng có thể đã thay đổi một phần được phép | Import xử lý thành công 7 bản ghi rồi lỗi ở bản ghi thứ 8; 7 bản ghi vẫn có hiệu lực theo contract. |

Các thuật ngữ này hữu ích để thiết kế/test Java, nhưng **không phải ba annotation hay ba loại exception chuẩn trong ngôn ngữ Java**. API phải tự công bố mức bảo đảm thích hợp.

### Strong guarantee có phạm vi

“Không đổi” phải được định nghĩa theo **trạng thái quan sát được qua contract**. Một method có thể tăng internal telemetry counter khi thất bại mà không làm thay đổi state nghiệp vụ, nhưng nếu counter là một phần của API quan sát được thì test phải xét nó. Tương tự, cache, listener, file tạm, event và database có thể nằm ngoài field của object nhưng vẫn là side effect quan trọng.

Không nên hứa “mọi thứ rollback” nếu API chỉ bảo đảm object in-memory. Với DB, network và bên thứ ba, việc hoàn tác có ranh giới giao dịch/đối soát riêng.

### Basic guarantee vẫn phải bảo toàn invariant

Basic không có nghĩa được để object bị hỏng tùy ý. Nếu kho không cho phép tồn âm, `stock >= 0` phải đúng **ngay cả sau lỗi**, dù một phần các item trước đó đã được giữ chỗ thành công. Không được gọi “partial progress” để hợp thức hóa trạng thái bất khả thi.

### No-throw không phải miễn kiểm thử

Một method cleanup được thiết kế không ném vẫn cần test idempotency, thay đổi state, listener removal, release permit và ứng xử khi gọi hai lần. Không tự tuyên bố mọi `AutoCloseable.close()` đều no-throw: `AutoCloseable.close()` có thể khai báo `throws Exception`, và implementation có thể thất bại.

## 3. Định nghĩa invariant trước khi viết test

*Invariant* là điều kiện phải đúng ở các điểm quan sát trạng thái hợp lệ của object, đặc biệt sau khi constructor hoặc một method public hoàn thành — thành công hoặc thất bại, tùy contract.

Ví dụ đối tượng giỏ hàng:

- `availableStock >= 0`.
- `reservedQuantity >= 0`.
- `reservedQuantity <= initialStock` trong phạm vi tồn kho giả định không replenishment.
- Tổng số lượng phân bổ nhất quán với số còn lại.
- Không có record giữ chỗ mồ côi khi thao tác được bảo đảm atomic.
- Không còn tài nguyên mở bị bỏ quên nếu method kết thúc và phạm vi sở hữu đã kết thúc.

Một invariant phải có **nghĩa nghiệp vụ rõ ràng**. Nếu hệ thống cho phép tồn âm để biểu diễn backorder, test `stock >= 0` lại là sai contract. Tránh đưa implementation detail không được bảo đảm vào test và làm test vỡ khi refactor vô hại.

Khi định nghĩa state để so sánh, không chỉ nhìn một trường như `status`. Một trạng thái có thể sai ở collection, balance, version, resource handle, cache, subscription hoặc external side effect dù enum `status` vẫn như cũ.

## 4. Đừng chỉ dùng assertThrows

Trong JUnit 5, `assertThrows` kiểm tra rằng đoạn code ném một exception thuộc kiểu yêu cầu hoặc subtype. Nó **không kiểm tra** trạng thái object:

```java
IllegalStateException thrown =
        assertThrows(IllegalStateException.class,
                () -> inventory.reserve(10));

assertEquals("not enough stock", thrown.getMessage());
assertEquals(5, inventory.availableStock());
assertEquals(List.of(), inventory.reservations());
```

Đây là **snippet JUnit 5** yêu cầu import/static import JUnit, class `Inventory` và các method theo API thực; không thể chạy chỉ bằng `javac` trần. Kiểm tra message hữu ích khi message là contract; nếu message là text chẩn đoán có thể đổi, thường nên kiểm tra loại, mã lỗi và cause ổn định thay vì khóa test vào đúng câu chữ.

Các assertion sau exception cần kiểm tra:

1. **Loại lỗi:** checked/unchecked, subtype, mã domain, cause.
2. **State giữ nguyên hay thay đổi phần nào:** theo mức bảo đảm.
3. **Invariant:** tính hợp lệ của mọi field/quan hệ quan trọng.
4. **Side effect:** persistence, event, email, queue, file, log nếu contract yêu cầu.
5. **Resource lifecycle:** đóng handle, trả permit, unlock, hủy registration.
6. **Khả năng dùng tiếp:** object có thể tiếp tục hoạt động hợp lệ sau lỗi không?
7. **Concurrency:** trạng thái có nhất quán với thread khác theo contract không?

**Một test đúng exception nhưng sai trạng thái phải FAIL.** Đó là giá trị chính của bài học.

## 5. Snapshot trước/sau: phép so sánh rõ ràng nhất

Để kiểm tra strong guarantee, nên chụp một **snapshot độc lập** của trạng thái quan sát được trước lời gọi, rồi so sánh với snapshot sau khi exception đã được bắt.

Mẫu tư duy:

```java
State before = object.snapshot();

assertThrows(ExpectedException.class,
        () -> object.perform(invalidInput));

State after = object.snapshot();
assertEquals(before, after);
```

Đây là snippet minh họa dùng JUnit, với `State` và `ExpectedException` của ứng dụng. Snapshot phải phản ánh đầy đủ phần trạng thái contract yêu cầu; một snapshot chỉ gồm `status` chưa chắc đủ.

### Snapshot phải thực sự độc lập

Nếu snapshot giữ reference tới một `ArrayList` mutable của object, thay đổi sau đó cũng có thể làm snapshot “thay đổi theo”, khiến so sánh trước/sau **false pass**.

Ví dụ sai:

```java
List<String> before = inventory.getReservations();
inventory.reserve(...);
assertEquals(before, inventory.getReservations());
```

Nếu `getReservations()` trả chính list nội bộ, `before` và giá trị sau là **cùng một object**; cả hai cùng thấy mutation. Cách sửa: copy:

```java
List<String> before = List.copyOf(inventory.getReservations());
```

`List.copyOf` tạo danh sách không cho chỉnh sửa; nhưng đây chỉ là **shallow copy**: nếu phần tử là object mutable, việc sửa field của phần tử vẫn xuất hiện trong snapshot. Với object graph có nested mutable state, cần **deep snapshot** theo contract, sử dụng DTO/record bất biến hoặc serialize/copy có kiểm soát.

Lưu ý `List.copyOf` không nhận phần tử `null`. Nếu domain cho phép null, chọn representation/copy phù hợp, đừng vô tình làm test lỗi ngay lúc snapshot.

## 6. Ví dụ Java 17 hoàn chỉnh: phát hiện state bị hỏng và sửa bằng staged commit

Ví dụ sau có hai cách cập nhật cùng một kiểu `Inventory`:

- `reserveUnsafe` đổi state trước rồi mới kiểm tra điều kiện tồn kho.
- `reserveSafe` xác minh trước, tạo candidate state **trong biến local**, sau đó commit bằng **một lần gán reference**; nếu validation hoặc việc dựng candidate thất bại trước commit thì state cũ được giữ.

Tạo file `InventoryExceptionStateDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class InventoryExceptionStateDemo {

    private record State(int available, List<String> reservations) {
        State {
            reservations = List.copyOf(reservations);
        }
    }

    private static final class Inventory {
        private State state = new State(5, List.of());

        State snapshot() {
            return state;
        }

        void reserveUnsafe(String item, int quantity) {
            Objects.requireNonNull(item, "item");
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity <= 0");
            }

            State old = state;
            List<String> next = new ArrayList<>(old.reservations());
            next.add(item + ":" + quantity);

            // Sai: commit trước khi kiểm tra điều kiện thất bại.
            state = new State(old.available() - quantity, next);

            if (state.available() < 0) {
                throw new IllegalStateException("insufficient stock");
            }
        }

        void reserveSafe(String item, int quantity) {
            Objects.requireNonNull(item, "item");
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity <= 0");
            }

            State old = state;
            if (quantity > old.available()) {
                throw new IllegalStateException("insufficient stock");
            }

            List<String> next = new ArrayList<>(old.reservations());
            next.add(item + ":" + quantity);
            State candidate =
                    new State(old.available() - quantity, next);

            // Commit sau khi tất cả validation/preparation thành công.
            state = candidate;
        }
    }

    public static void main(String[] args) {
        Inventory unsafe = new Inventory();
        State beforeUnsafe = unsafe.snapshot();

        try {
            unsafe.reserveUnsafe("book", 7);
        } catch (IllegalStateException ex) {
            System.out.println("unsafe exception: " + ex.getMessage());
        }
        System.out.println("unsafe unchanged: "
                + beforeUnsafe.equals(unsafe.snapshot()));
        System.out.println("unsafe state: " + unsafe.snapshot());

        Inventory safe = new Inventory();
        State beforeSafe = safe.snapshot();

        try {
            safe.reserveSafe("book", 7);
        } catch (IllegalStateException ex) {
            System.out.println("safe exception: " + ex.getMessage());
        }
        System.out.println("safe unchanged: "
                + beforeSafe.equals(safe.snapshot()));
        System.out.println("safe state: " + safe.snapshot());

        safe.reserveSafe("book", 2);
        System.out.println("safe success: " + safe.snapshot());
    }
}
```

Chạy với Java 17+:

```bash
javac -encoding UTF-8 InventoryExceptionStateDemo.java
java InventoryExceptionStateDemo
```

Stdout:

```text
unsafe exception: insufficient stock
unsafe unchanged: false
unsafe state: State[available=-2, reservations=[book:7]]
safe exception: insufficient stock
safe unchanged: true
safe state: State[available=5, reservations=[]]
safe success: State[available=3, reservations=[book:2]]
```

Phân tích:

1. `beforeUnsafe` và `beforeSafe` là các `record State` bất biến đối với các phần tử `String` của ví dụ; list được copy ở constructor. Do đó snapshot trước không biến đổi theo state hiện tại.
2. `reserveUnsafe("book", 7)` đã gán state mới với `available=-2` và reservation `book:7` trước khi ném. Exception đúng nhưng invariant **tồn không âm** sai.
3. `reserveSafe("book", 7)` kiểm tra lỗi **trước mọi commit**. Khi ném, `state` vẫn tham chiếu tới record trước đó; `equals` trả `true`.
4. Gọi `reserveSafe("book", 2)` thành công tạo `State[available=3, reservations=[book:2]]`.
5. Phương thức an toàn chuẩn bị state mới trước gán. Đây là cách đạt strong guarantee **ở phạm vi state in-memory của ví dụ, khi một thread thực thi độc quyền**.

**Giới hạn quan trọng:** `Inventory` không có synchronization, vì thế **không thread-safe**; hai thread cùng đọc một `State` và ghi đè nhau có thể gây lost update. Bảo đảm exception safety không thay thế thread safety. `List.copyOf` là shallow copy; ở ví dụ các phần tử là `String` bất biến. Với DB/network, cần transaction hoặc idempotency/đối soát riêng chứ không thể chỉ gán một field Java để rollback side effect ngoài tiến trình.

## 7. Cách test mạnh hơn: tự viết assertion Java 17, không cần JUnit

Để người học thấy rõ test phải kiểm tra cả exception lẫn state, ví dụ sau **chạy độc lập bằng JDK** và dùng `AssertionError` do code chủ động tạo thay vì phụ thuộc cờ `-ea`.

Tạo file `ExceptionStateAssertionsDemo.java`:

```java
import java.util.List;
import java.util.Objects;

public class ExceptionStateAssertionsDemo {

    private static final class Account {
        private int balance = 100;
        private List<String> history = List.of();

        int balance() {
            return balance;
        }

        List<String> history() {
            return history;
        }

        void withdraw(int amount) {
            if (amount <= 0) {
                throw new IllegalArgumentException("amount must be positive");
            }
            if (amount > balance) {
                throw new IllegalStateException("insufficient funds");
            }

            List<String> nextHistory =
                    new java.util.ArrayList<>(history);
            nextHistory.add("withdraw:" + amount);

            // Mọi bước có thể phát hiện lỗi đã hoàn tất.
            history = List.copyOf(nextHistory);
            balance -= amount;
        }
    }

    private static <T extends Throwable> T expectThrows(
            Class<T> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable error) {
            if (type.isInstance(error)) {
                return type.cast(error);
            }
            throw new AssertionError("wrong exception type", error);
        }
        throw new AssertionError("expected " + type.getSimpleName());
    }

    private static void check(boolean condition, String explanation) {
        if (!condition) {
            throw new AssertionError(explanation);
        }
    }

    public static void main(String[] args) {
        Account account = new Account();
        int balanceBefore = account.balance();
        List<String> historyBefore = List.copyOf(account.history());

        IllegalStateException ex = expectThrows(
                IllegalStateException.class,
                () -> account.withdraw(120));

        check(Objects.equals(ex.getMessage(), "insufficient funds"),
                "wrong message");
        check(account.balance() == balanceBefore,
                "balance changed after failure");
        check(account.history().equals(historyBefore),
                "history changed after failure");
        check(account.balance() >= 0, "negative balance");

        System.out.println("exception checked");
        System.out.println("state unchanged: true");
        System.out.println("invariants checked");

        account.withdraw(40);
        check(account.balance() == 60, "success balance wrong");
        check(account.history().equals(List.of("withdraw:40")),
                "success history wrong");
        System.out.println("success after failure: " + account.balance());
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 ExceptionStateAssertionsDemo.java
java ExceptionStateAssertionsDemo
```

Stdout:

```text
exception checked
state unchanged: true
invariants checked
success after failure: 60
```

Điểm đáng học là kiểm tra **khả năng sử dụng tiếp** sau một lần thất bại. `withdraw(120)` phải bị từ chối và giữ state; sau đó `withdraw(40)` phải vẫn hoạt động bình thường. Nếu object đã hỏng ngầm sau failure, test thành công tiếp theo có thể phát hiện vấn đề mà so sánh một field chưa thấy.

Trong demo, `expectThrows` bắt `Throwable` chỉ để trình diễn một bộ assert xác định; **đây không phải chiến lược xử lý lỗi nghiệp vụ production**. Test thực nên dùng JUnit 5 `assertThrows` hoặc `assertThrowsExactly` với semantics phù hợp và tránh tự viết utility nếu framework đã có.

## 8. Kiểm tra exception guarantee từ bên ngoài, không buộc test vào field private

Nếu object có API public:

```java
int availableStock();
List<ReservationView> reservations();
InventoryStatus status();
```

thì test nên ưu tiên kiểm tra các **quan sát có trong hợp đồng** này. Không cần đọc field private bằng reflection trừ khi thật sự phải kiểm tra invariant không được biểu lộ và test được thiết kế cho implementation.

Cấu trúc kiểm thử nên có:

```text
Given:
  - tạo đối tượng ở state hợp lệ xác định
  - lấy snapshot qua public/read-only API
When:
  - gọi method và buộc một đường lỗi cụ thể
Then:
  - assert loại/mã lỗi/cause
  - assert state theo strong hoặc basic guarantee
  - assert bất biến giữa các field
  - assert không có side effect ngoài phạm vi
  - assert có thể dùng tiếp hoặc chuyển sang state FAILED đúng contract
```

Nếu implementation đổi từ mutable collection sang immutable record nhưng public state không đổi, test contract vẫn nên PASS. Test quá phụ thuộc vào số lần gọi setter hoặc thứ tự field assignment có thể bị vỡ vô ích khi refactor.

Tuy nhiên, khi phải bảo đảm **không gọi hệ thống bên ngoài nếu validation thất bại**, việc dùng fake/mock adapter để xác nhận “không gọi” là phù hợp, vì đây là side effect thuộc contract chứ không phải chi tiết triển khai ngẫu nhiên.

## 9. Khi chỉ có basic guarantee: kiểm tra phần tiến độ được cho phép

Một API xử lý batch có thể không hứa atomic cho cả danh sách. Ví dụ:

```java
void importRows(List<Row> rows) throws ImportException;
```

Đây là chữ ký minh họa; `Row` và `ImportException` của ứng dụng chưa được định nghĩa. Nếu contract nói “các record hoàn thành trước record lỗi vẫn giữ nguyên”, test phải **không khẳng định toàn batch không đổi**.

Giả sử row 1 và row 2 ghi thành công, row 3 không hợp lệ. Test basic guarantee có thể kiểm tra:

- Row 1, row 2 vẫn tồn tại chính xác một lần.
- Row 3 không được ghi dưới dạng record sai hoặc nửa record.
- Counter/sequence không hỏng.
- Index/cache giữ invariant.
- Không có resource/transaction handle bị rò rỉ.
- Có checkpoint hoặc con trỏ resume đúng theo contract.
- Việc gọi lại với cơ chế retry/resume không gây ghi trùng các record đã thành công.

Nếu hệ thống **hứa atomic batch**, kết quả đó lại là lỗi — test phải xác nhận rollback toàn bộ. Vì vậy **viết guarantee trước, viết assertion sau**. Không dùng một bộ assertion “unchanged” chung cho mọi API.

## 10. Phát hiện mutation một phần khi validation ở quá muộn

Một lỗi rất thường gặp:

```java
void updateProfile(UserProfile profile, String newEmail) {
    profile.setEmail(newEmail);
    if (!isValidEmail(newEmail)) {
        throw new IllegalArgumentException("invalid email");
    }
    notifyObservers(profile);
}
```

Đây là snippet minh họa, giả định `UserProfile`, `isValidEmail` và `notifyObservers`. Test chỉ kiểm tra exception sẽ bỏ sót `profile.email` đã đổi thành giá trị sai.

Cách cải thiện thường là **validate first**:

```java
void updateProfile(UserProfile profile, String newEmail) {
    if (!isValidEmail(newEmail)) {
        throw new IllegalArgumentException("invalid email");
    }
    profile.setEmail(newEmail);
    notifyObservers(profile);
}
```

Tuy nhiên, cách sửa này chỉ xử lý lỗi validation xảy ra trước mutation. Nếu `notifyObservers` ném exception sau `setEmail` thì profile **vẫn đã đổi**. Do đó validate-first **chưa đủ cho strong guarantee nếu các bước sau commit có thể thất bại**.

Có thể thiết kế:
- Prepare dữ liệu và tất cả validation trước commit.
- Commit state in-memory bằng một điểm gán nguyên tử ở ranh giới object.
- Thực hiện notification sau commit như tác động riêng với contract rõ: nếu notify thất bại, profile update có rollback hay đã commit?
- Nếu API yêu cầu atomic cùng notification, cần cơ chế phối hợp/transaction/outbox, không thể chỉ đảo lại thứ tự hai dòng đơn giản.

Test phải kích lỗi tại **mỗi bước có thể thất bại**, không chỉ input invalid.

## 11. Kiểm thử failure injection tại những mốc thực thi

Một input sai chỉ chạm một đường lỗi. Muốn biết method có strong/basic guarantee không, cần kích lỗi ở nhiều điểm:

| Mốc phát lỗi | Cần kiểm tra sau lỗi |
| --- | --- |
| Validation đầu vào | Không có mutation; không gọi dependency. |
| Sau khi tạo candidate state | Candidate bị bỏ; state cũ giữ nguyên. |
| Khi cấp phát tài nguyên thứ hai | Tài nguyên thứ nhất được đóng nếu owner đã acquire. |
| Sau mutation đầu tiên nhưng trước mutation thứ hai | Invariant và rollback theo contract. |
| Khi lưu vào database | Transaction rollback hoặc trạng thái pending/unknown đúng. |
| Khi phát event/notification | Ghi nhận trạng thái business commit riêng với event delivery. |
| Khi cleanup/close ném exception | Lỗi chính/các suppressed exceptions không bị mất; resource state có thể quan sát. |
| Khi callback/retry bị interrupt | Dừng theo policy; không để object ở trạng thái nửa đóng sai. |

**Failure injection** có thể dùng fake adapter để chủ động ném exception ở lần gọi thứ N, hoặc một fault injector trong integration test. Đừng phụ thuộc vào network thật tự nhiên thất bại đúng chỗ — test đó khó lặp và thường bỏ qua các điểm nguy hiểm nhất.

Trong Java, interface nhỏ giúp test dễ hơn:

```java
interface Store {
    void save(Order order) throws IOException;
}
```

Snippet này chỉ mô tả contract; `Order` và import cần được định nghĩa. Test dùng một `Store` giả lập có thể ném `IOException` trước/sau thao tác ghi để xác nhận service xử lý state đúng theo từng tình huống.

**Quan trọng:** giả lập “throw trước khi ghi” và “throw sau khi ghi” phải là **hai kịch bản khác nhau**. Nếu một timeout đến sau server commit, rollback bộ nhớ của client không làm mất giao dịch đã commit phía server. Kết quả này có thể là *unknown/pending*, không phải “không có side effect”.

## 12. Ví dụ Java 17: một điểm commit duy nhất

Đối với state in-memory, thiết kế dạng “tính toán trước rồi gán” giúp đạt strong guarantee nếu không có side effect quan sát được trước commit.

Tạo file `AtomicStateCommitDemo.java`:

```java
import java.util.Objects;

public class AtomicStateCommitDemo {

    private record Balance(int alice, int bob) {}

    private static final class Ledger {
        private Balance balance = new Balance(100, 50);

        Balance snapshot() {
            return balance;
        }

        void transfer(int amount, boolean failBeforeCommit) {
            if (amount <= 0) {
                throw new IllegalArgumentException("amount <= 0");
            }

            Balance old = balance;
            if (amount > old.alice()) {
                throw new IllegalStateException("insufficient funds");
            }

            Balance candidate =
                    new Balance(old.alice() - amount, old.bob() + amount);

            if (failBeforeCommit) {
                throw new IllegalStateException("injected failure");
            }

            // Chỉ một lần thay reference state.
            balance = candidate;
        }
    }

    public static void main(String[] args) {
        Ledger ledger = new Ledger();
        Balance before = ledger.snapshot();

        try {
            ledger.transfer(30, true);
        } catch (IllegalStateException ex) {
            System.out.println("failure: " + ex.getMessage());
        }
        System.out.println("unchanged: " + before.equals(ledger.snapshot()));
        System.out.println("after failure: " + ledger.snapshot());

        ledger.transfer(30, false);
        System.out.println("after success: " + ledger.snapshot());
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 AtomicStateCommitDemo.java
java AtomicStateCommitDemo
```

Stdout:

```text
failure: injected failure
unchanged: true
after failure: Balance[alice=100, bob=50]
after success: Balance[alice=70, bob=80]
```

Đây là một điểm commit cho hai số dư **trong cùng object in-memory**. Nếu exception được kích trước điểm gán, state giữ nguyên. Nhưng điều đó **không phải transaction tiền thật**: một ledger production còn có concurrency, persistence, audit, điều kiện isolation và external side effect.

Ngay cả với một reference field, nếu nhiều thread cùng đọc/ghi, cần dùng cơ chế đồng bộ thích hợp như lock hoặc atomic CAS với invariant; test một thread không chứng minh linearizability/concurrency safety. Khi `Balance` nằm trong record immutable, một lần thay reference không tạo ra trạng thái “alice mới, bob cũ” trong cùng record, nhưng vẫn cần publication/visibility đúng.

## 13. Kiểm thử nhiều field và object graph

Đối tượng thực tế có quan hệ phức tạp:

```text
Order
 ├── status
 ├── total
 ├── List<OrderLine>
 │    ├── quantity
 │    └── price
 └── paymentReference
```

Một snapshot nông chỉ copy reference `OrderLine` có thể không phát hiện mutation ở `quantity`. Nếu strong guarantee áp dụng cho toàn bộ `Order` quan sát được, test phải đọc/copy đủ các trường của nested object:

- Tạo immutable view/DTO cho từng line.
- So sánh danh sách theo **giá trị**, không chỉ kích thước.
- Kiểm tra tổng tiền và quantity nhất quán.
- Kiểm tra không có event/record ở kho phụ bị thêm sau khi test gọi method.
- Khi object cung cấp collection view, kiểm tra không thể mutate nội bộ trái hợp đồng.

Một chiến lược là biến state nghiệp vụ thành immutable record/value object rồi object chủ quản thay toàn state bằng reference mới. Đừng nhầm immutable outer record với deep immutability nếu field của record vẫn là mutable `List` hoặc entity.

Trường hợp có identity có chủ đích (ví dụ bảo đảm trả lại chính object cũ), test `assertSame` có thể phù hợp. Nếu chỉ cần giá trị state không đổi, `assertEquals` hoặc semantic comparator phù hợp hơn và ít buộc implementation.

## 14. Trạng thái bên ngoài object: database, file, event và email

Nếu phương thức thất bại sau khi tác động bên ngoài, chỉ kiểm tra field Java là **chưa đủ**.

| Side effect | Quan sát sau lỗi cần kiểm tra |
| --- | --- |
| Database | Record đã commit chưa? Transaction rollback thật chưa? Unique key có bị tiêu thụ không? |
| File | File mới/đã ghi một phần có tồn tại không? Tệp tạm có được cleanup không? |
| Queue/message | Event đã enqueue hay publish chưa? Gửi lặp có bị dedup không? |
| HTTP tới dịch vụ khác | Response mất có phải outcome không rõ? Có operation ID đối soát không? |
| Email/notification | Đã gửi trước khi ném chưa? Retry có gửi hai lần không? |
| Cache/index | Cache invalidation có nhất quán với nguồn dữ liệu không? |
| Metrics/log | Nếu là hợp đồng quan sát, số đếm/outcome có đúng không? |

Một thất bại sau bước commit có thể được tài liệu hóa là **“operation đã thành công, nhưng thông báo kết quả gặp lỗi”** hoặc **“outcome unknown”**; khi ấy yêu cầu object/DB không đổi là sai thực tế. API nên dùng transaction/operation ID hoặc kết quả có trạng thái để người gọi không retry bừa.

Mục tiêu của test không phải khiến mọi exception biến side effect về 0 bằng phép màu; nó là **phát hiện đúng điều đã xảy ra và xác nhận contract**.

## 15. Cleanup và quyền sở hữu cũng là trạng thái cần kiểm thử

Một method có thể mở stream, acquire permit, lock hoặc subscribe listener. Khi nó ném exception, **cleanup đúng** là một phần bảo toàn trạng thái.

Ví dụ:

```java
try (InputStream in = Files.newInputStream(path)) {
    parse(in);
}
```

Đây là snippet minh họa; `path`, `parse` và các import phù hợp chưa được khai báo. Khi `parse` ném exception, try-with-resources vẫn đóng `in`. Một test bằng fake resource có thể đếm số lần `close()` và xác nhận **đúng owner đã đóng đúng phạm vi**.

Những trường hợp cần test:

1. Tài nguyên thứ nhất mở thành công, mở tài nguyên thứ hai thất bại: thứ nhất được đóng.
2. Body ném exception, `close()` thành công: lỗi chính giữ nguyên và resource được đóng.
3. Body và `close()` cùng ném: exception từ close được đưa vào `getSuppressed()`, trừ những cấu trúc xử lý khác của implementation.
4. Một borrowed resource được truyền vào method: method **không tự đóng** nếu hợp đồng không chuyển ownership.
5. Đã đăng ký listener rồi lỗi giữa startup: handle đăng ký trước đó được hủy nếu chưa chuyển quyền sở hữu.
6. Lỗi trong `Semaphore.acquire()` hoặc `Lock.lockInterruptibly()`: không release/unlock khi chưa acquire.
7. Cancellation/interruption: lifecycle trả về trạng thái có thể quan sát; không giả định `cancel(true)` chứng minh cleanup hoàn tất.

Đóng tài nguyên chưa chắc rollback business state. Một `FileOutputStream.close()` không làm mất dữ liệu đã ghi; `unsubscribe` không rút lại những callback đã chạy. Vì vậy test cần tách **lifecycle cleanup** khỏi **business rollback**.

## 16. Ví dụ Java 17: body ném và close cũng lỗi

Ví dụ này minh họa cách kiểm tra object state sau khi có **hai exception liên quan**: lỗi body là primary; lỗi `close()` là suppressed; cờ resource đã đóng được kiểm tra độc lập.

Tạo file `CleanupAfterFailureDemo.java`:

```java
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class CleanupAfterFailureDemo {

    private static final class Resource implements AutoCloseable {
        private final AtomicBoolean closed;

        Resource(AtomicBoolean closed) {
            this.closed = closed;
        }

        @Override
        public void close() throws IOException {
            closed.set(true);
            throw new IOException("close failed");
        }
    }

    public static void main(String[] args) {
        AtomicBoolean closed = new AtomicBoolean(false);

        try (Resource resource = new Resource(closed)) {
            throw new IllegalStateException("work failed");
        } catch (IllegalStateException ex) {
            System.out.println("primary: " + ex.getMessage());
            System.out.println("suppressed: " + ex.getSuppressed().length);
            System.out.println(
                    "cleanup error: " + ex.getSuppressed()[0].getMessage());
        } catch (IOException ex) {
            System.out.println("unexpected close-only: " + ex.getMessage());
        }

        System.out.println("close invoked: " + closed.get());
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 CleanupAfterFailureDemo.java
java CleanupAfterFailureDemo
```

Stdout:

```text
primary: work failed
suppressed: 1
cleanup error: close failed
close invoked: true
```

**Diễn giải chính xác:** cờ `closed=true` trong ví dụ chỉ có nghĩa **code `close()` đã chạy đến bước ghi cờ**. Vì `close()` vẫn ném `IOException`, không được kết luận mọi tài nguyên production đã được giải phóng thành công. Với file, socket, connection hoặc flush buffer thật, việc cleanup hoàn tất cần được đánh giá theo contract và trạng thái của API đó.

Test đầy đủ kiểm tra primary exception, suppressed exception, trạng thái ghi nhận cleanup và khả năng object/ứng dụng đi tiếp sau lỗi theo hợp đồng.

## 17. Kiểm tra trạng thái khi lỗi được bọc hoặc đổi nghĩa

Một method có thể bắt lỗi tầng thấp rồi ném loại lỗi ổn định hơn:

```java
try {
    repository.save(entity);
} catch (SQLException ex) {
    throw new RepositoryException("save failed", ex);
}
```

Đây là mảnh minh họa, `repository`, `entity`, `RepositoryException` và import chưa được định nghĩa. Test không nên dừng ở `RepositoryException`:

- `getCause()` có phải lỗi SQL gốc không?
- Repository transaction đã rollback hay commit chưa?
- Entity in-memory có bị đánh dấu `SAVED` sai không?
- Cache có bị cập nhật một phần không?
- Nếu operation có thể đã commit nhưng response mất, có trạng thái **pending/unknown** và cơ chế đối soát không?

Nếu method catch rồi **swallow exception**, test cần xác nhận fallback/result domain rõ ràng; không được để lỗi biến thành thành công giả mà state thiếu nhất quán.

### Bắt lỗi ở đúng boundary

Nếu test ở tầng service, inject lỗi repository vào fake; assert state service và các lệnh gọi dependency. Nếu test ở tầng repository, dùng transaction/test database thật để xác nhận rollback/constraints. **Không cố dùng một unit test giả lập để chứng minh toàn bộ ACID guarantee của database production**.

## 18. JUnit 5: mẫu test exception và state bằng snapshot

Với một class có method và snapshot public thích hợp, test JUnit 5 có thể dùng:

```java
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class InventoryTest {

    @Test
    void reserveFailureLeavesStateUnchanged() {
        Inventory inventory = new Inventory(5);
        InventorySnapshot before = inventory.snapshot();

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> inventory.reserve("book", 7));

        InventorySnapshot after = inventory.snapshot();

        assertAll(
                () -> assertEquals("insufficient stock", error.getMessage()),
                () -> assertEquals(before, after),
                () -> assertEquals(5, after.available()),
                () -> assertEquals(0, after.reservations().size())
        );
    }
}
```

**Đây là template JUnit, không phải file tự chạy bằng JDK:** `Inventory`/`InventorySnapshot` là các kiểu ứng dụng cần được triển khai, JUnit 5 là dependency ngoài JDK. Đoạn test thể hiện cách kiểm tra cả exception và state; không bịa rằng JUnit có sẵn trong Java 17.

`assertAll` cho phép chạy một nhóm assertion và thu thập nhiều thất bại, giúp chẩn đoán state phức tạp. Tuy nhiên, nếu `after` là null hoặc getter ném exception, một assertion có thể cần được sắp xếp lại để tránh che nguyên nhân kiểm thử.

Nếu muốn chấp nhận subtype, `assertThrows(ExpectedException.class, ...)` phù hợp. Nếu contract đòi **đúng kiểu, không chấp nhận subtype**, JUnit 5 có `assertThrowsExactly` ở những phiên bản hỗ trợ; dùng khi thật sự có yêu cầu đó, không chỉ để làm test quá chặt.

## 19. Test sự phục hồi sau khi object đã thất bại một lần

Một object có thể qua được test “state không đổi” nhưng vẫn không thể sử dụng tiếp vì flag `busy`, lock, queue hoặc resource bị mắc kẹt.

Trình tự kiểm thử hữu ích:

1. Tạo object hợp lệ.
2. Gọi phương thức với input gây lỗi.
3. Khẳng định loại exception, trạng thái và invariant sau lỗi.
4. Gọi **một operation hợp lệ**.
5. Xác nhận operation hợp lệ thành công và state mới đúng.
6. Nếu object phải chuyển sang trạng thái không thể dùng tiếp (`FAILED`/`CLOSED`), xác nhận lời gọi tiếp theo bị từ chối bằng exception theo contract.

Ví dụ `Account` ở mục 7 bị từ chối rút `120` nhưng vẫn rút được `40` sau đó. Đây là kiểm tra rất hữu ích với service/resource object có lifecycle.

Đối với object bị đánh dấu `FAILED`, test đừng đòi nó hoạt động bình thường nếu contract yêu cầu discard/recreate. Bảo đảm đúng là **không giả vờ usable khi state không an toàn**.

## 20. State machine và tính hợp lệ của chuyển trạng thái

Với một lifecycle:

```text
NEW → OPEN → ACTIVE → CLOSED
             |
             +→ FAILED
```

Nếu `activate()` ném giữa chừng, đối tượng phải nằm ở trạng thái được thiết kế: vẫn `OPEN` (strong guarantee), hoặc `FAILED` (basic guarantee) nếu không thể rollback. Không được để một đối tượng vừa có `state=ACTIVE` vừa thiếu các resource cần thiết để thật sự hoạt động.

Test nên xác định:

- Những state nào hợp lệ?
- Chuyển nào được phép?
- Exception ở mỗi transition khiến state cuối là gì?
- Resource có được cleanup khi sang `FAILED` hoặc `CLOSED` không?
- Gọi lặp `close`/`activate` có idempotent hoặc ném rõ ràng không?
- Object có thể recover hay phải tạo lại?
- Có event callback nào còn chạy sau khi unregister không?

Nếu enum state đã đổi nhưng phần resource tương ứng chưa đổi, invariant có thể bị phá. Cần kiểm tra **state machine và các phụ thuộc**, không chỉ enum.

## 21. Concurrency: state đúng sau exception cần thêm thread safety

Các test một thread chỉ kiểm tra một phần. Một phương thức có thể bảo toàn state trong đường exception nhưng vẫn có race:

```java
State old = state;
State next = compute(old);
state = next;
```

Đây là snippet minh họa. Nếu hai thread cùng tính từ `old` và gán, một thay đổi có thể bị mất. Strong exception guarantee **không đồng nghĩa** linearizability, atomicity giữa các thread hay safe publication.

Một số hướng kiểm thử:

- Dùng `CountDownLatch`/`CyclicBarrier` để tạo interleaving có kiểm soát ở trước commit.
- Inject lỗi ở một thread khi thread kia đang đọc hoặc cập nhật.
- Xác nhận invariant không bị phá và kết quả quan sát theo synchronization contract.
- Nếu dùng `synchronized`/lock/CAS, kiểm tra không deadlock/lost update.
- Nếu callback cleanup xảy ra ở background thread, cần **đợi tín hiệu hoàn tất rõ ràng**, không dùng `Thread.sleep(50)` để phỏng đoán.
- Với `Future.cancel(true)`, trạng thái canceled của future không chứng minh worker đã cleanup hoàn tất; dùng acknowledgement/`CountDownLatch` khi contract yêu cầu.

**Đừng kiểm tra state ngay sau khi chỉ gửi yêu cầu cancel rồi tuyên bố code cleanup sai** nếu API cho phép cleanup hoàn tất bất đồng bộ. Hãy phân biệt mốc “yêu cầu dừng” với mốc “đã dừng”.

## 22. Property-based và fault-injection testing

Một vài ví dụ đơn lẻ chưa đủ chứng minh invariant tổng quát. Có thể kiểm tra bằng nhiều input và điểm lỗi:

- Sinh quantity âm, zero, nhỏ hơn tồn, bằng tồn, lớn hơn tồn; check invariant và guarantee từng nhánh.
- Sinh chuỗi thao tác hợp lệ/lỗi đan xen; object vẫn hợp lệ sau mỗi bước.
- Inject lỗi sau bước chuẩn bị A, B, C và trước commit, xem state có được giữ nguyên nếu API hứa strong.
- Inject lỗi cleanup để kiểm tra suppressed exceptions.
- Với nhiều tài nguyên, tạo lỗi khi resource thứ N được mở; các resource trước đó được đóng.
- Với retry, ném lỗi transient sau khi side effect đã xảy ra để phát hiện duplicate.
- Với transaction, mô phỏng lost response sau commit và xác nhận logic đánh dấu UNKNOWN/idempotency.
- Với concurrent API, chủ động thay đổi thứ tự hai thread và check invariant dưới contention.

Có thể dùng property-based testing framework ngoài JDK (ví dụ jqwik) hoặc viết vòng lặp data-driven. Không nên dựa vào random input không seed rồi test lúc pass lúc fail không tái lập. Lưu seed và scenario khi một phép thử thất bại để debug.

### Những property hữu ích

```text
Nếu reserve(q) thất bại dưới strong guarantee:
    snapshotAfter == snapshotBefore

Ở mọi trạng thái hợp lệ của inventory:
    available >= 0
    all reservation quantities > 0

Nếu transfer(a→b,q) thành công trong ledger không phí:
    balanceA + balanceB không đổi

Nếu transfer thất bại trước commit:
    cả balanceA và balanceB không đổi

Nếu close/cleanup hoàn tất theo contract:
    owner không còn giữ resource đang mở
```

Đây là các property **minh họa có điều kiện**. Ví dụ tổng balance chỉ bất biến khi không có phí/chuyển tiền ra ngoài ledger; đừng áp dụng ngoài giả định.

## 23. Phân biệt test unit, integration và end-to-end

| Cấp độ | Có thể xác minh tốt | Giới hạn |
| --- | --- | --- |
| **Unit** với fake/mocks | State Java, snapshot, số lần gọi, mốc injected exception, basic/strong logic | Không tự chứng minh transaction thật hoặc mạng đã commit. |
| **Integration** với DB/filesystem/message broker kiểm thử | Rollback, constraint, file cleanup, transaction boundary, side effect tương tác | Có thể chưa bao quát mọi race hoặc sự cố hạ tầng. |
| **End-to-end** với hệ thống nhiều service | Operation ID, retry/idempotency, trạng thái UNKNOWN, recovery sau incident | Tốn kém và khó fault-inject chính xác mọi điểm; cần đo ổn định. |

Tầng nào cũng cần kiểm tra exception **và state** theo boundary đó. Một fake DB ném `IOException` trước khi ghi giúp unit test service, nhưng không chứng minh DB thật không commit khi kết nối mất ngay sau câu lệnh COMMIT.

## 24. Những lỗi kiểm thử thường gặp

**Chỉ assert exception type.** Bỏ sót mutation và invariant hỏng. Luôn bổ sung state/side effect theo contract.

**Chụp snapshot bằng reference tới mutable list.** Trạng thái trước/sau cùng thay đổi khiến test false pass. Tạo snapshot độc lập.

**So sánh sai mức guarantee.** API chỉ hứa basic nhưng test yêu cầu mọi state không đổi, hoặc API hứa strong mà test chỉ kiểm tra một field.

**Chỉ inject lỗi ở bước đầu.** Không tìm ra lỗi xảy ra sau mutation hoặc trong cleanup. Dùng fault injection ở các mốc quan trọng.

**Chỉ kiểm tra một field như `status`.** Bỏ sót balance, collection, version, resource, event và DB.

**Assert exception message không ổn định.** Test vỡ khi thay câu chữ, dù contract không đổi. Ưu tiên type/code/cause ổn định.

**Bỏ qua nested mutable object.** Shallow snapshot có thể không phát hiện thay đổi trong phần tử.

**Dùng mock gọi `save` làm bằng chứng DB rollback.** Mock chỉ xác nhận invocation, không chứng minh semantics của transaction thật.

**Nhầm timeout với thất bại chắc chắn của remote operation.** Outcome có thể không rõ; test phải diễn tả PENDING/UNKNOWN và đối soát khi phù hợp.

**Bỏ qua cleanup.** Object field đúng nhưng connection hoặc listener rò rỉ. Kiểm tra ownership và lifecycle.

**Đọc state trước khi async cleanup hoàn tất.** Cần đợi tín hiệu completion theo contract.

**Không thử operation thành công sau lần lỗi.** Có thể bỏ sót cờ `busy` hoặc lock bị giữ khiến object không usable.

**Tạo test không xác định bằng `Thread.sleep`.** Dùng latch/barrier/fake clock khi có thể.

**Cho rằng fail-fast ở input đủ mạnh cho mọi exception.** Sau validation còn thể có I/O, allocation, observer callbacks và persistence ném lỗi.

**Dùng reflection để test implementation detail thay cho API contract.** Dễ làm test giòn và cản refactor; chỉ dùng khi thực sự cần.

## 25. Checklist cho test trạng thái sau exception

Trước khi xem một test là hoàn chỉnh, hãy hỏi:

- [ ] Đã nêu guarantee của method là strong/basic/no-throw hay contract cụ thể khác?
- [ ] Đã tạo trạng thái ban đầu hợp lệ và lưu **snapshot độc lập**?
- [ ] Đã kích đúng loại lỗi dự kiến, kiểm tra type/code/cause?
- [ ] Đã so sánh toàn bộ phần state thuộc contract, không chỉ một field?
- [ ] Đã kiểm tra invariant sau thất bại?
- [ ] Đã kiểm tra side effect ngoài object khi operation có DB/file/event/network?
- [ ] Đã kiểm tra resource owner cleanup, không đóng borrowed resource?
- [ ] Đã test lỗi sau partial work và lỗi trong cleanup?
- [ ] Đã phân biệt rollback với unknown outcome khi response có thể bị mất?
- [ ] Đã test object hoạt động tiếp hoặc chuyển đúng lifecycle state?
- [ ] Đã kiểm tra concurrency/synchronization nếu object được chia sẻ?
- [ ] Đã kiểm soát thứ tự với fake/latch thay vì sleep ngẫu nhiên?
- [ ] Đã có test happy path để chắc giải pháp sửa lỗi không làm hỏng thao tác thành công?
- [ ] Đã chạy lại test sau refactor và không khóa test vào chi tiết implementation không cần thiết?

## 26. Bài tập tự kiểm tra

**Câu 1:** `reserve(7)` ném đúng `IllegalStateException` nhưng `available=-2`. Test `assertThrows` có thể pass không? Object có bảo toàn invariant tồn không âm không?

**Đáp án:** `assertThrows` có thể pass, nhưng object đã sai invariant. Test phải khẳng định `available` và các reservation sau exception.

**Câu 2:** Bạn viết `List<Item> before = object.items()`; `items()` trả list nội bộ. Sau khi method thất bại, so sánh `before.equals(object.items())` trả true. Có đủ chứng minh strong guarantee không?

**Đáp án:** Không. Hai reference có thể cùng trỏ tới list đã bị mutate. Cần snapshot độc lập; nếu `Item` mutable, cần copy sâu/phép so sánh semantic thích hợp.

**Câu 3:** Batch import 10 record và lỗi ở record 8; 7 record trước đã commit. Basic guarantee có thể vẫn đúng không?

**Đáp án:** Có, **nếu contract cho phép partial progress**, mọi invariant đúng và không leak tài nguyên. Nếu API hứa atomic batch/strong guarantee thì test phải fail.

**Câu 4:** Đối tượng cập nhật DB thành công nhưng response bị mất. Client nhận timeout. Có thể assert DB không đổi chỉ vì client thấy exception không?

**Đáp án:** Không. Outcome của remote có thể đã commit. Cần kiểm tra trạng thái thật hoặc operation ID/idempotency để đối soát, không suy ra rollback từ exception phía client.

**Câu 5:** `try-with-resources` có body ném `IllegalStateException` và `close` ném `IOException`. Test cần kiểm tra gì?

**Đáp án:** Lỗi body là primary, lỗi close có thể nằm trong `getSuppressed()`, và trạng thái cleanup cần được kiểm tra theo contract của resource. Gọi `close` không tự chứng minh đã giải phóng hoàn toàn.

**Câu 6:** Test dùng `future.cancel(true)`, ngay lập tức assert listener đã unregister nhưng test thường thất bại. Có chắc implementation sai không?

**Đáp án:** Chưa chắc. Cancellation state của Future có thể thay đổi trước khi worker cleanup hoàn tất. Phải đợi acknowledgement hoặc synchronization theo contract nếu test muốn kiểm tra mốc cleanup hoàn thành.

**Câu 7:** Inventory dùng record immutable và một lần gán `state = candidate` nên strong guarantee trong một thread. Điều đó bảo đảm an toàn khi hai thread cùng `reserve` không?

**Đáp án:** Không. Hai thread có thể đọc cùng state cũ rồi gán kết quả khác nhau, mất cập nhật. Cần đồng bộ/CAS hoặc cơ chế concurrency phù hợp.

**Câu 8:** Sau một exception, state nhìn có vẻ đúng nhưng lệnh hợp lệ tiếp theo luôn ném `IllegalStateException("busy")`. Test nào có thể phát hiện?

**Đáp án:** Một test **thao tác thành công sau thất bại**, kiểm tra object còn usable theo contract, sẽ phát hiện cờ busy hoặc lifecycle bị mắc kẹt.

## 27. Kết luận

**Một exception đúng loại chỉ xác nhận đường báo lỗi; trạng thái sau exception mới cho biết đối tượng có còn đúng hợp đồng hay không.** Để kiểm thử đầy đủ, hãy công bố strong/basic/no-throw guarantee hoặc điều kiện tương đương, chụp snapshot độc lập trước lời gọi, kích lỗi ở nhiều mốc, rồi kiểm tra trạng thái, invariant, side effect, cleanup và khả năng sử dụng tiếp.

Thiết kế giảm lỗi thường dựa vào **validate trước mutation**, **prepare candidate trước commit**, **commit tại một ranh giới rõ**, và **transaction/compensation/idempotency** khi side effect vượt ra ngoài bộ nhớ. Kiểm thử nên bám vào những bảo đảm có thể quan sát, không phán đoán rollback chỉ từ exception. Khi làm được vậy, hệ thống vừa báo lỗi đúng, vừa giữ trạng thái đáng tin cậy sau những đường chạy không thành công.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-testing-006">Tạo fake resource để mô phỏng lỗi khởi tạo hoặc close</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo fake resource để mô phỏng lỗi khởi tạo hoặc close trong Java

## 1. Tại sao phải thử lỗi ở lúc mở và lúc đóng tài nguyên?

Trong Java, những tài nguyên như file stream, socket, database connection, transaction handle hoặc client của hệ thống ngoài thường có **vòng đời**: khởi tạo/cấp phát → sử dụng → đóng/giải phóng. Lỗi không chỉ xảy ra khi xử lý dữ liệu. Việc **mở tài nguyên** có thể thất bại; việc **đóng tài nguyên** cũng có thể ném exception, kể cả khi phần thân công việc đã chạy thành công.

Nếu chỉ kiểm thử trường hợp “mở được, dùng được, đóng được”, chương trình có thể giấu những lỗi nghiêm trọng:

- Tài nguyên A đã mở, nhưng khởi tạo tài nguyên B thất bại. A có được đóng không?
- Thân `try` ném exception, rồi `close()` cũng ném. Exception nào đến caller? Lỗi phụ có bị mất không?
- Thân `try` hoàn tất bình thường, nhưng `close()` ném. Caller có bị báo thất bại không?
- Có hai resource, mỗi `close()` đều ném. Java có tiếp tục đóng resource còn lại không?
- Constructor thất bại **sau khi đã cấp phát một phần tài nguyên nội bộ**. Ai chịu trách nhiệm dọn phần đã cấp phát đó?
- Resource có bị đóng hai lần, bỏ quên hoặc bị dùng sau khi đóng?
- Một test sử dụng file/network/database thật có tái hiện được lỗi ở **đúng điểm mong muốn**, hay dễ bị chập chờn bởi môi trường?

Một **fake resource** là một implementation thử nghiệm có hành vi được điều khiển bằng dữ liệu và cờ lỗi. Nó có thể ghi lại các sự kiện như `init:A`, `close:B`, đếm lần đóng và chủ động ném `IOException` ở các điểm chỉ định. Nhờ vậy, test tập trung vào **hợp đồng vòng đời**, không phụ thuộc đĩa thật hỏng hay mạng thật mất kết nối.

Mục tiêu của bài này là tạo và sử dụng fake resource với Java 17 để chứng minh thứ tự giải phóng, lỗi chính (*primary exception*), lỗi bị kèm theo (*suppressed exceptions*) và hậu điều kiện khi khởi tạo hoặc đóng thất bại.

## 2. Cơ chế Java phải hiểu trước khi viết fake

### 2.1. `AutoCloseable` là hợp đồng đóng tài nguyên

Giao diện cơ bản:

~~~java
public interface AutoCloseable {
    void close() throws Exception;
}
~~~

Đây là **chữ ký minh họa** của `AutoCloseable`, không phải một file triển khai mới cần tạo. Một class triển khai có thể thu hẹp checked exception, chẳng hạn khai báo `close() throws IOException`. `Closeable` trong `java.io` là một giao diện con có hợp đồng và cách dùng cụ thể cho I/O.

Cú pháp try-with-resources:

~~~java
try (Resource resource = acquire()) {
    use(resource);
}
~~~

Là **fragment minh họa** (cần có `Resource`, `acquire` và `use`). Java tự gọi `close()` khi rời khối, dù thân chạy bình thường hay thoát bởi exception, `return` hoặc một luồng điều khiển được hỗ trợ. Việc đóng tuân theo quy tắc ngoại lệ của try-with-resources; bản thân `close()` có thể ném exception mới.

**Quan trọng:** try-with-resources giúp gọi cleanup cho những resource đã khởi tạo thành công, **không tự động khôi phục side effect** như một lần ghi file, commit database hay gửi HTTP request trước khi lỗi.

### 2.2. Đóng theo thứ tự ngược với lúc khai báo

~~~java
try (Resource first = openFirst();
     Resource second = openSecond()) {
    doWork();
}
~~~

Fragment trên khai báo hai resource. Nếu cả hai khởi tạo thành công, thứ tự đóng là **`second.close()` rồi `first.close()`**. Đây là quy tắc xác định của Java, không phụ thuộc scheduler hay tốc độ máy.

Nếu `openSecond()` ném exception trong quá trình khởi tạo, resource `first` đã được cấp thành công vẫn phải được đóng. Nhưng `second` **không được đăng ký như một resource đã khởi tạo thành công** và không được try-with-resources gọi `close()`. Nếu việc tạo `second` đã mở một socket nội bộ trước khi ném, code khởi tạo của nó có trách nhiệm dọn tài nguyên mở dở.

### 2.3. Lỗi trong body và lỗi trong `close()`

Quy tắc cốt lõi:
- **Thân `try` ném A, `close()` ném B:** A vẫn là lỗi chính, B được gắn vào A qua `getSuppressed()`.
- **Thân chạy bình thường, `close()` ném B:** B trở thành lỗi chính đi ra khỏi try-with-resources.
- **Nhiều resource đóng cùng thất bại:** Java vẫn cố đóng các resource còn lại. Thứ tự đóng ngược quyết định lỗi nào được xem là chính nếu thân không có lỗi.
- **Khởi tạo resource tiếp theo ném A, resource đã mở đóng ném B:** A là lỗi chính, B được kèm như suppressed.

Các quy tắc này rất phù hợp để kiểm thử bằng fake, vì test có thể gây lỗi tại đúng chỗ mà không cần ép filesystem/network thật thất bại.

## 3. Fake resource khác mock và stub ở điểm nào?

Có ba cách mô phỏng thường gặp:

| Loại | Đặc điểm | Khi nào thích hợp |
| --- | --- | --- |
| **Fake** | Có implementation đơn giản có trạng thái thật trong bộ nhớ; ghi sự kiện, thay đổi trạng thái, chủ động ném lỗi theo cấu hình | Kiểm tra vòng đời resource, thứ tự đóng, trạng thái sau failure |
| **Stub** | Trả các giá trị hoặc exception được lập trình sẵn; thường ít logic/trạng thái | Điều khiển kết quả của dependency để test một nhánh |
| **Mock** | Đối tượng do công cụ/hàm tạo để xác minh interaction, số lần gọi, đối số và thứ tự | Test collaborator call contract qua Mockito hoặc cơ chế tương tự |

Các thuật ngữ này có thể giao nhau tùy cộng đồng và framework. Bài này chọn **fake viết bằng Java thuần**, vì implementation có trạng thái (event log, cờ fail, số lần close), tái hiện được hành vi mà không phải phụ thuộc thư viện mock.

Một fake tốt cần:
- **Điều khiển lỗi rõ ràng:** có thể chọn lỗi khi init, khi dùng, khi close.
- **Ghi nhận sự kiện:** bảo đảm test phân biệt `init`, `use`, `close` theo đúng thứ tự.
- **Hành vi deterministic:** không random, không `Thread.sleep` để “đoán” thời điểm lỗi.
- **Có identity:** biết resource nào là A, B, C.
- **Lặp lại được:** mỗi test tạo fresh fake/event log, không để state từ test trước rò sang test sau.
- **Chân thực vừa đủ:** mô phỏng đúng hợp đồng AutoCloseable, không giả định một hành vi cleanup mà tài nguyên thật không cam kết.

Đừng quá mô phỏng toàn bộ database/socket nếu mục tiêu chỉ là xác nhận Java gọi `close()` và giữ exception. Fake quá phức tạp có thể gây bug riêng trong test.

## 4. Ví dụ Java 17 hoàn chỉnh: ba tình huống init/body/close

Chương trình sau đủ để biên dịch/chạy, **không cần JUnit hay thư viện bên thứ ba**. `FakeResource` ghi các sự kiện vào một `List<String>` và có hai cờ độc lập:

- `failInit`: ném `IOException` trong constructor sau khi ghi dấu `init`.
- `failClose`: ghi dấu `close` rồi ném `IOException` khi được gọi đóng.

Hàm `report` ghi lỗi chính và từng exception trong `getSuppressed()`. Ba kịch bản bao phủ các nhánh trọng tâm.

Lưu chính xác thành **`FakeResourceFailureDemo.java`**:

~~~java
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FakeResourceFailureDemo {
    static final class FakeResource implements AutoCloseable {
        private final String name;
        private final List<String> events;
        private final boolean failClose;

        FakeResource(String name, List<String> events,
                     boolean failInit, boolean failClose) throws IOException {
            this.name = name;
            this.events = events;
            this.failClose = failClose;
            events.add("init:" + name);
            if (failInit) {
                throw new IOException("init " + name + " failed");
            }
        }

        @Override
        public void close() throws IOException {
            events.add("close:" + name);
            if (failClose) {
                throw new IOException("close " + name + " failed");
            }
        }
    }

    static void report(String label, List<String> events, IOException failure) {
        events.add("primary:" + failure.getMessage());
        for (Throwable suppressed : failure.getSuppressed()) {
            events.add("suppressed:" + suppressed.getMessage());
        }
        System.out.println(label + " -> " + String.join(" | ", events));
    }

    static void bodyAndCloseFail() {
        List<String> events = new ArrayList<>();
        try (FakeResource a = new FakeResource("A", events, false, true);
             FakeResource b = new FakeResource("B", events, false, true)) {
            events.add("body");
            throw new IOException("body failed");
        } catch (IOException failure) {
            report("body-fail", events, failure);
        }
    }

    static void secondInitFails() {
        List<String> events = new ArrayList<>();
        try (FakeResource a = new FakeResource("A", events, false, true);
             FakeResource b = new FakeResource("B", events, true, false)) {
            events.add("unreachable");
        } catch (IOException failure) {
            report("init-fail", events, failure);
        }
    }

    static void closeOnlyFails() {
        List<String> events = new ArrayList<>();
        try (FakeResource a = new FakeResource("A", events, false, true);
             FakeResource b = new FakeResource("B", events, false, true)) {
            events.add("body:ok");
        } catch (IOException failure) {
            report("close-fail", events, failure);
        }
    }

    public static void main(String[] args) {
        bodyAndCloseFail();
        secondInitFails();
        closeOnlyFails();
    }
}
~~~

Biên dịch và chạy bằng JDK 17+ từ thư mục chứa file:

~~~bash
javac --release 17 FakeResourceFailureDemo.java
java FakeResourceFailureDemo
~~~

**Stdout đã kiểm chứng bằng JDK 17:**

~~~text
body-fail -> init:A | init:B | body | close:B | close:A | primary:body failed | suppressed:close B failed | suppressed:close A failed
init-fail -> init:A | init:B | close:A | primary:init B failed | suppressed:close A failed
close-fail -> init:A | init:B | body:ok | close:B | close:A | primary:close B failed | suppressed:close A failed
~~~

### 4.1. Kịch bản 1 — body và cả hai close cùng lỗi

Trong `bodyAndCloseFail()`:
1. `FakeResource A` khởi tạo thành công.
2. `FakeResource B` khởi tạo thành công.
3. Thân `try` thêm sự kiện `body` rồi ném `IOException("body failed")`.
4. Java gọi `B.close()` trước; nó ghi `close:B` và ném `IOException("close B failed")`.
5. Java tiếp tục gọi `A.close()`; A ghi `close:A` và cũng ném.
6. `catch` nhận lỗi `body failed` làm **primary**, còn hai lỗi đóng nằm trong `failure.getSuppressed()` theo thứ tự B rồi A.

Kiểm tra đáng chú ý: **B và A đều được thử đóng, dù B.close() đã ném exception.** Nếu dùng `finally` tự viết sai, bạn có thể vô tình bỏ qua lần đóng A khi B.close thất bại.

### 4.2. Kịch bản 2 — constructor B lỗi khi A đã mở

Trong `secondInitFails()`:
1. Constructor A ghi `init:A`, khởi tạo thành công.
2. Constructor B ghi `init:B` rồi ném `IOException("init B failed")`.
3. Thân `try` **không chạy**, nên `unreachable` không xuất hiện.
4. A là resource đã tạo thành công, vì vậy Java gọi `A.close()`.
5. B không được gọi `close()` vì constructor không trả một resource hoàn chỉnh.
6. `A.close()` cũng ném, nhưng lỗi `init B failed` giữ vai trò primary; lỗi `close A failed` là suppressed.

Đây là một phép thử thường bị bỏ sót. Nó chứng minh try-with-resources **không chỉ đóng sau khi body bắt đầu**, mà còn cleanup những resource đã khởi tạo thành công trước một resource initializer bị lỗi.

### 4.3. Kịch bản 3 — body thành công, close thất bại

Trong `closeOnlyFails()`, phần thân chỉ thêm `body:ok`. Khi rời khối:
1. B.close() ném lỗi và trở thành **primary**.
2. A.close() vẫn được gọi, lỗi của nó trở thành **suppressed** đi kèm lỗi B.
3. Caller không thể nói toàn bộ thao tác “thành công không có lỗi”, dù body chạy hết.

**Lưu ý nghiệp vụ:** lỗi đóng có thể hoặc không ảnh hưởng tới dữ liệu đã xử lý tùy loại resource. Đối với một số output stream, lỗi `close`/flush có thể đồng nghĩa dữ liệu chưa được ghi đầy đủ. Với resource khác, close lỗi có thể chỉ ảnh hưởng cleanup. Phải biết hợp đồng của resource thật; không tự suy rằng dữ liệu luôn bị mất hoặc luôn đã được lưu.

## 5. Ma trận hành vi của try-with-resources

| Resource initializer | Body | Close | Exception đến caller |
| --- | --- | --- | --- |
| Tất cả thành công | Thành công | Tất cả thành công | Không có exception |
| A mở thành công, B init lỗi | Không chạy | A đóng thành công | Lỗi init B |
| A mở thành công, B init lỗi | Không chạy | A.close lỗi | Init B chính; close A suppressed |
| Tất cả mở thành công | Ném X | Tất cả close thành công | X |
| Tất cả mở thành công | Ném X | B.close ném Y | X chính; Y suppressed |
| Tất cả mở thành công | Thành công | B.close ném Y | Y chính |
| Tất cả mở thành công | Thành công | B.close ném Y, A.close ném Z | Y chính; Z suppressed |
| Tất cả mở thành công | Ném X | B.close ném Y, A.close ném Z | X chính; Y và Z suppressed |
| A init lỗi ngay từ đầu | Không chạy | Không có resource header nào đã khởi tạo hoàn tất | Lỗi init A; cleanup bên trong A do implementation chịu trách nhiệm |

Với bảng này, “lỗi init” là exception được ném ra trong quá trình khởi tạo resource; **lỗi đóng** là exception từ `close()`. Nếu loại lỗi không phải `IOException` mà là `RuntimeException`, nguyên tắc primary/suppressed vẫn áp dụng trong những điều kiện thông thường của try-with-resources.

Một `catch (IOException e)` chỉ nhận IOException phù hợp. Nếu fake ném một unchecked exception không thuộc IOException từ `close()` hoặc body, chương trình cần một catch/throws phù hợp với type đó; không nên catch `Throwable` rộng chỉ để mọi test luôn “pass”.

## 6. Thiết kế fake có thể tiêm lỗi từng điểm

Ở ví dụ runnable, constructor nhận:

~~~java
FakeResource(String name,
             List<String> events,
             boolean failInit,
             boolean failClose) throws IOException
~~~

Đây là **chữ ký fragment** của constructor đã có trong chương trình. Cách dùng hai boolean đủ cho bài học nhỏ. Với test nhiều failure mode, có thể đặt một enum thay vì nhiều boolean để tránh đọc nhầm:

~~~java
enum FailurePoint {
    NONE,
    INIT,
    READ,
    WRITE,
    FLUSH,
    CLOSE
}
~~~

Đây là một **fragment Java 17**, có thể đặt trong một class hoặc file riêng đúng tên. Mỗi enum value mô tả **một vị trí lỗi** được chọn cho một instance. Nếu bạn cần mô phỏng “body lỗi và close cũng lỗi”, cần cho phép nhiều điểm lỗi hoặc tách cấu hình cho lifecycle và operation; đừng dùng một enum duy nhất rồi vô tình loại mất kịch bản lỗi kép.

Một cấu hình fake rõ có thể bao gồm:
- `resourceId`: tên resource để truy vết.
- `failAtInit` hoặc injected exception khi khởi tạo.
- `failAtClose` hoặc injected exception khi đóng.
- `events`: danh sách nhận các event theo thứ tự.
- `closeCount`: số lần đóng để assert.
- `opened`/`closed`: trạng thái vòng đời.
- `payload`/`buffer`: dữ liệu nội bộ nếu test cần kiểm tra việc ghi/đọc.

Đối với test concurrent, `ArrayList` dùng chung giữa thread không tự an toàn. Các thí nghiệm trong `FakeResourceFailureDemo` đều chạy đồng bộ một thread, nên `ArrayList` là đủ. Nếu muốn kiểm thử nhiều thread, dùng cấu trúc thread-safe/cơ chế đồng bộ và điều phối bằng latch, không dùng `Thread.sleep` như một phép chứng minh thứ tự.

### 6.1. Tiêm exception object hay chỉ cờ boolean?

Hai cách đều có giá trị:

**Dùng cờ:**

~~~java
if (failClose) {
    throw new IOException("close failed");
}
~~~

Đơn giản, deterministic, phù hợp dạy quy tắc suppressed.

**Dùng exception do test cung cấp:**

~~~java
if (closeFailure != null) {
    throw closeFailure;
}
~~~

Đây là fragment, `closeFailure` là `IOException` đã được test cung cấp. Ưu điểm là bạn có thể assert **identity**: exception chính hoặc suppressed có phải đúng object đã inject hay không. Cẩn trọng nếu tái sử dụng cùng một exception object giữa nhiều lượt test, vì stack trace và suppressed list gắn với object có thể khiến bằng chứng sai lệch.

Trong test thực, nên tạo exception mới cho từng test, không dùng singleton Throwable toàn suite.

## 7. Kiểm tra event log thay vì chỉ kiểm tra exception class

Một test yếu:

~~~java
assertThrows(IOException.class, () -> useResource());
~~~

Đây là **fragment JUnit 5**, `useResource` là hàm giả định. Nó chỉ chứng minh có một IOException thoát ra; không chứng minh resource đã được đóng, có lỗi bị suppress, hay body có thực sự chạy không.

Một test tốt còn kiểm tra các **hậu điều kiện**:
- `init:A` xảy ra trước `init:B`.
- `close:B` xảy ra trước `close:A` khi cả hai đã khởi tạo.
- `body` không chạy nếu initializer thứ hai lỗi.
- `close()` của resource khởi tạo lỗi không được try-with-resources gọi.
- Mỗi resource đã khởi tạo thành công được đóng **đúng một lần** bởi cấu trúc try-with-resources trong flow đã thử.
- Lỗi chính vẫn đúng object/type/message.
- `getSuppressed()` chứa lỗi close theo đúng số lượng/thứ tự.
- Không còn tài nguyên do test sở hữu bị rò.

Đừng chỉ viết `verify(close())` một lần rồi bỏ qua thứ tự: close của resource B có thể ném làm code tự dọn không đi tiếp tới A. Với event log, lỗi thứ tự hiện ngay trong kết quả.

### 7.1. Thêm bộ đếm close khi cần assert chính xác

Một fake có thể định nghĩa:

~~~java
private int closeCount;

@Override
public void close() throws IOException {
    closeCount++;
    events.add("close:" + name);
    if (failClose) {
        throw new IOException("close " + name + " failed");
    }
}

int closeCount() {
    return closeCount;
}
~~~

Đây là **fragment để thêm vào class fake**, không phải class độc lập. Khi test chạy một thread, bộ đếm `int` đủ dùng. Khi test concurrent cần đồng bộ thích hợp (ví dụ `AtomicInteger`) và chiến lược đọc trạng thái sau khi các task thật sự kết thúc.

**Đóng một lần bởi try-with-resources** không chứng minh close thật của mọi library là idempotent. Nếu business code cũng gọi `close()` thủ công, resource có thể được gọi đóng lần thứ hai; tùy implementation, điều đó có thể gây lỗi hoặc không. Test phải phù hợp hợp đồng resource.

## 8. Ví dụ JUnit 5: assert lỗi khởi tạo và cleanup A

Bên dưới là đoạn **JUnit 5 test minh họa**. Nó giả định bạn đã đưa `FakeResourceFailureDemo` vào cùng package với test và có JUnit Jupiter trong classpath. Các import/static import thích hợp gồm `org.junit.jupiter.api.Test`, `java.util.ArrayList`, `java.util.List`, `java.io.IOException` và `org.junit.jupiter.api.Assertions.*`.

~~~java
@Test
void secondInitializerFailureClosesFirstResource() {
    List<String> events = new ArrayList<>();

    IOException error = assertThrows(IOException.class, () -> {
        try (var a = new FakeResourceFailureDemo.FakeResource(
                     "A", events, false, true);
             var b = new FakeResourceFailureDemo.FakeResource(
                     "B", events, true, false)) {
            events.add("body");
        }
    });

    assertEquals("init B failed", error.getMessage());
    assertEquals(1, error.getSuppressed().length);
    assertEquals("close A failed",
                 error.getSuppressed()[0].getMessage());
    assertIterableEquals(List.of("init:A", "init:B", "close:A"), events);
}
~~~

Điều được kiểm chứng:
- Lỗi init của B là **lỗi chính**, dù close A cũng ném.
- B không có `close:B` trong event log.
- A đã được dọn đúng một lần theo sự kiện `close:A`.
- Phần thân không thực thi vì `body` không xuất hiện.

Không nhất thiết phải thêm Mockito để test thứ tự này. Với fake ghi sự kiện, ta kiểm tra được cả nguyên nhân và lifecycle bằng Java/JUnit cơ bản.

## 9. Ví dụ JUnit 5: body lỗi cộng với nhiều close lỗi

Một test khác:

~~~java
@Test
void bodyFailureIsPrimaryAndCloseFailuresAreSuppressed() {
    List<String> events = new ArrayList<>();

    IOException error = assertThrows(IOException.class, () -> {
        try (var a = new FakeResourceFailureDemo.FakeResource(
                     "A", events, false, true);
             var b = new FakeResourceFailureDemo.FakeResource(
                     "B", events, false, true)) {
            events.add("body");
            throw new IOException("body failed");
        }
    });

    assertEquals("body failed", error.getMessage());
    assertEquals(2, error.getSuppressed().length);
    assertEquals("close B failed",
                 error.getSuppressed()[0].getMessage());
    assertEquals("close A failed",
                 error.getSuppressed()[1].getMessage());
    assertIterableEquals(
            List.of("init:A", "init:B", "body", "close:B", "close:A"),
            events);
}
~~~

Đây là **fragment test JUnit 5**, các import như phần trước. Test không chỉ nhìn thấy IOException mà còn chứng minh **không có lỗi đóng nào che mất IOException của body** và **A vẫn đóng sau khi B.close() thất bại**.

Nếu `getSuppressed().length == 0` dù bạn đã cho close ném, có thể code thực đang dùng cleanup viết tay, resource không được đăng ký trong try-with-resources, hoặc exception đã bị wrap sai cách và bạn đang kiểm tra nhầm đối tượng Throwable ngoài cùng.

## 10. Lỗi lúc mở: constructor không tự được close bởi try-with-resources

Đây là một ranh giới quan trọng:

~~~java
try (ConnectionLike c = new ConnectionLike()) {
    work(c);
}
~~~

Fragment này giả định `ConnectionLike` là AutoCloseable và `work` có sẵn. Nếu constructor `new ConnectionLike()` **ném exception trước khi trả object**, biểu thức khởi tạo resource chưa hoàn tất; try-with-resources **không thể gọi `close()` trên object chưa được gán như một resource đã khởi tạo**.

Điều này khác với case “A mở xong, B khởi tạo thất bại”: Java vẫn đóng **A**, nhưng không tự dọn nội bộ **B**. Nếu B đã mở socket/file handle rồi mới ném do một bước setup thất bại, constructor/factory của B phải xử lý **partial acquisition**.

### 10.1. Thiết kế factory dọn resource mở dở

Giả sử cần mở một input stream rồi đọc header để khởi tạo một object:

~~~java
static ParsedInput openParsed(Path path) throws IOException {
    InputStream in = Files.newInputStream(path);
    try {
        Header header = readHeader(in); // Có thể ném IOException.
        return new ParsedInput(in, header); // Nhận quyền sở hữu stream.
    } catch (IOException | RuntimeException failure) {
        try {
            in.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
        throw failure;
    }
}
~~~

Đây là **fragment thiết kế**, cần import `java.io.InputStream`, `java.io.IOException`, `java.nio.file.Files`, `java.nio.file.Path` và định nghĩa `Header`, `ParsedInput`, `readHeader`. Ý tưởng:

1. `Files.newInputStream` mở stream.
2. Nếu bước parse header/thao tác tạo object ném, factory vẫn đang **sở hữu** stream nên cần cố đóng nó.
3. Nếu close cũng lỗi, giữ failure gốc là chính và gắn close failure bằng `addSuppressed`.
4. Nếu khởi tạo thành công, factory **chuyển quyền sở hữu** stream sang `ParsedInput`; class này phải đóng stream khi `close()` được gọi.

Trong code thật, cần xác định rõ **thời điểm chuyển ownership** và liệu constructor `ParsedInput` có thể ném sau khi nhận stream. Nếu constructor có side effect trước khi throw, nhánh catch cần bao quát và không đóng nhầm stream đã chuyển giao. Nếu failure là một loại unchecked khác hoặc error nghiêm trọng, cũng phải có chính sách phù hợp; đoạn fragment trên chỉ minh họa những exception dự kiến.

### 10.2. Không đưa resource chưa mở vào danh sách phải close

Một fake có thể ghi `init:B` rồi ném, nhưng **không nên tự động thêm B vào danh sách “đã mở thành công”**. Trong test, phân biệt:
- `initAttempted`: đã bắt đầu khởi tạo;
- `initialized`: khởi tạo đã hoàn tất;
- `registeredForClose`: tài nguyên đã trở thành đối tượng của try-with-resources;
- `closed`: `close` đã được gọi và kết quả thế nào.

Đối với một constructor ném exception, fake có thể ghi log `initAttempted`, còn không ghi `closed` của chính instance đó. Nếu bạn cần kiểm tra resource nội bộ mở dở, phải có event riêng cho chúng; việc không thấy `close:B` trong trace TWR **không chứng minh các handle nội bộ B đều an toàn**.

## 11. Fake lỗi đóng có ý nghĩa gì đối với resource thật?

`close()` không có một hậu điều kiện phổ quát “đã flush dữ liệu và mọi tác động hoàn tất”. Hợp đồng của từng resource khác nhau:

| Resource | Lỗi khi đóng có thể liên quan | Cần kiểm tra riêng |
| --- | --- | --- |
| `OutputStream` ghi file | Flush/ghi cuối thất bại, lỗi filesystem | File đã ghi được bao nhiêu, dữ liệu có bền vững không |
| `BufferedWriter` | Flush buffer hoặc close delegate gặp lỗi | Dữ liệu đã được chuyển xuống stream chưa |
| JDBC `Connection` | Lỗi giải phóng kết nối; trạng thái transaction có thể khác | Auto-commit, transaction, commit/rollback đã diễn ra |
| JDBC `Statement` / `ResultSet` | Driver cleanup thất bại | Resource lifecycle, driver behavior |
| Socket/channel | Lỗi đóng, thông tin remote không chắc chắn | Có gói tin nào đã gửi/nhận, server đã xử lý chưa |
| Lock/lease handle | Giải phóng thất bại hoặc lease chưa hết hạn | Có process nào còn giữ quyền truy cập không |
| Resource giả tự viết | Bất cứ quy tắc nào bạn cấu hình | Fake có mô phỏng đúng contract của đối tượng thật không |

**Không chuyển mọi lỗi close thành “đã rollback” hoặc “đã ghi thành công”**. Fake giúp kiểm tra Java flow và policy ứng dụng, không chứng minh mọi điều kiện của filesystem, driver hay service xa.

### 11.1. Cleanup khác rollback

Một resource có thể được đóng sau khi thao tác đã gây side effect:
- Đã append file rồi mới lỗi đóng.
- Đã commit database rồi mới lỗi close connection.
- Đã gửi request tạo thanh toán, response bị mất rồi socket close lỗi.

Một `IOException` từ `close()` không tự khôi phục trạng thái trước đó. Nếu tác vụ cần all-or-nothing, sử dụng transaction/staging/atomic publish trong phạm vi hỗ trợ; với nhiều hệ thống khác nhau, cần idempotency, compensation hoặc đối soát. Test fake phải có tình huống **side effect xảy ra trước lỗi** nếu contract cần khẳng định trạng thái sau failure.

## 12. Tránh làm mất lỗi chính khi tự viết `finally`

Anti-pattern:

~~~java
Resource a = openA();
Resource b = openB();
try {
    work(a, b);
} finally {
    b.close();
    a.close();
}
~~~

Đây là **fragment** với các hàm/types giả định. Nếu `b.close()` ném:
- `a.close()` **không được gọi** nếu không có cơ chế bảo vệ riêng.
- Exception từ `b.close()` có thể che mất exception từ `work`.
- Nếu `openB()` ném trước khi vào `try`, phần `finally` cũng **không chạy**, khiến A bị rò.

Try-with-resources xử lý những đường thoát đó đúng ngữ nghĩa Java. Với đa số AutoCloseable, hãy **ưu tiên try-with-resources** thay vì tự viết nhiều tầng `finally` khó kiểm chứng.

### 12.1. Lỗi đóng che lỗi thân

Với cleanup tự viết:

~~~java
try {
    throw new IOException("body failed");
} finally {
    throw new IOException("close failed");
}
~~~

Đây là **fragment** trong một phương thức cho phép `throws IOException`. Exception từ `finally` thay thế điều khiển ném ra ngoài; nếu không bảo quản lỗi thân, bạn mất nguyên nhân ban đầu. Try-with-resources có quy tắc suppressed để tránh vấn đề này khi cả body và close cùng ném.

Đừng đặt `return` trong `finally` để “trả trạng thái mặc định”, bởi nó cũng có thể che exception đang lan ra. Nếu phải cleanup thủ công vì API đặc biệt, hãy thiết kế preservation của lỗi chính và kiểm thử các đường failure.

## 13. `Throwable.getSuppressed()`: đọc đúng nơi chứa lỗi phụ

Khi có một `Throwable primary`:

~~~java
Throwable[] sideFailures = primary.getSuppressed();
for (Throwable secondary : sideFailures) {
    System.err.println("suppressed: " + secondary);
}
~~~

Đây là fragment giả định `primary` có sẵn. `getSuppressed()` trả mảng có thể rỗng; không được luôn truy cập `[0]` khi không có suppressed exception.

Các lỗi suppressed quan trọng vì:
- Cho biết cleanup/close cũng thất bại sau nguyên nhân chính.
- Giúp xác định resource nào có vấn đề.
- Có stack trace riêng, có thể trỏ tới lớp khác.
- Có thể khiến trạng thái tài nguyên không chắc chắn.

Khi ghi log, truyền **Throwable chính** vào logger có khả năng in cause/suppressed để không mất thông tin. Chỉ log `e.getMessage()` hoặc ném wrapper mới không có cause sẽ che chứng cứ.

### 13.1. Khác `cause` ở đâu?

- **Cause:** thường biểu diễn quan hệ “exception ngoài được gây ra bởi exception bên trong”, ví dụ `new IllegalStateException("failed", ioException)`.
- **Suppressed:** biểu diễn lỗi phụ được gắn vào primary, thường là lỗi cleanup khi primary đã tồn tại.

Không coi suppressed là một phần `getCause()` mặc định. Nếu exception có cả cause và suppressed, cần kiểm tra cả hai loại. `Throwable.printStackTrace()` thường hiển thị những quan hệ này; cấu hình logger có thể rút gọn/truncate, nên công cụ phân tích cần giữ đầy đủ khi có thể.

### 13.2. Gắn suppressed thủ công có bẫy gì?

`Throwable.addSuppressed(other)` có những ràng buộc: không thể thêm chính throwable đó làm suppressed của nó, và đối số không được null. Nếu suppression bị vô hiệu hóa khi tạo Throwable theo constructor đặc biệt, việc thêm có thể không có hiệu lực được quan sát. Với những Throwable thông thường tạo bởi `IOException`, hành vi suppression hoạt động như ví dụ.

Đừng viết code luôn `primary.addSuppressed(closeFailure)` trong mọi tình huống mà không biết ownership của exception, vì có thể vô tình thêm nhiều lần hoặc ghi nhận cùng một failure lặp. Try-with-resources thường đáng tin hơn khi chỉ cần dọn AutoCloseable.

## 14. Resource mở thành công nhưng `close()` ném: có đóng hai lần không?

Nếu `close()` ném, không thể kết luận resource đã đóng hoàn toàn hay vẫn mở: điều đó tùy implementation. Ví dụ một close có thể đã giải phóng handle rồi ném lỗi khi flush buffer; gọi `close()` lần nữa chưa chắc giúp dữ liệu được ghi đủ. Ngược lại, một close lỗi sớm có thể để resource chưa được giải phóng.

Fake nên mô phỏng đúng contract khi test chính sách retry-close. Không mặc định:
- `close()` luôn idempotent với mọi loại `AutoCloseable`.
- `close()` ném nghĩa là resource chắc chắn vẫn mở.
- Gọi `close()` hai lần là an toàn.
- Lỗi close chỉ ảnh hưởng log, không ảnh hưởng kết quả.

### 14.1. Test dùng-after-close khi quan trọng

Nếu API của bạn yêu cầu resource không được sử dụng sau `close()`, fake có thể có cờ `closed` và ném `IllegalStateException` khi gọi `read()/write()` sau khi đóng. Kiểm thử phải tách:
- Số lần close được yêu cầu.
- Resource có báo đã đóng không.
- Phương thức business còn gọi resource sau close hay không.

Tuy nhiên đừng áp đặt hành vi “mọi resource đều ném khi đọc sau close” lên tất cả thư viện nếu contract thật khác. Fake phải phản ánh **hợp đồng bạn cần bảo đảm**.

## 15. Mô phỏng lỗi khởi tạo nhiều tầng

Xem một service cần ba tài nguyên:

~~~text
Mở A ──> Mở B ──> Mở C ──> xử lý
          |
          +-- B init lỗi
              |
              +-- A phải được cleanup
              +-- C không được mở
              +-- body không chạy
~~~

Một test đầy đủ nên kiểm tra:
1. Nếu A init lỗi, không có B/C/body.
2. Nếu B init lỗi, A đóng; C/body không chạy.
3. Nếu C init lỗi, B rồi A đóng; body không chạy.
4. Nếu C init lỗi và cả B/A close lỗi, C init là primary, B/A close là suppressed theo thứ tự.
5. Nếu body lỗi, C/B/A đóng ngược; lỗi body vẫn chính.
6. Nếu body thành công và C.close lỗi, B/A vẫn được thử đóng.

Chỉ cần thay cấu hình fake tại mỗi test; không phải tạo database/socket thật với fault ngẫu nhiên.

### 15.1. Constructor partial failure và trách nhiệm dọn nội bộ

Giả sử tạo một `CompositeClient` gồm socket và buffer. Nếu socket đã mở nhưng bước tạo buffer ném, đối tượng CompositeClient có thể chưa hoàn thành construction; outer try-with-resources không gọi `CompositeClient.close()` trên object constructor thất bại. Factory/constructor phải dọn socket trong đường failure.

Test riêng bằng một fake socket sẽ kiểm tra:
- socket tạo thành công;
- bước thứ hai ném;
- socket.close được gọi đúng một lần;
- lỗi bước thứ hai giữ primary nếu socket.close cũng ném.

Việc kiểm tra này khác với test resource A ở header được đóng khi B init fail. Cần biết **resource nào sở hữu handle ở từng giai đoạn**.

## 16. Kiểm thử bất đồng bộ cần thêm đồng bộ hóa

Ví dụ `ExecutorService.submit` gọi một task có try-with-resources. Nếu task ném lỗi body hoặc close, `Future.get()` có thể ném `ExecutionException` với cause tương ứng. Test phải đọc **cause của task**, không chỉ thấy `get()` fail:

~~~java
Future<Void> future = executor.submit(() -> {
    try (FakeResource r = openFake()) {
        performWork(r);
    }
    return null;
});

ExecutionException wrapper = assertThrows(
        ExecutionException.class, future::get);
Throwable cause = wrapper.getCause();
~~~

Đây là **fragment JUnit 5**, giả định `executor`, `FakeResource`, `openFake`, `performWork` và các imports tồn tại. Nếu resource được tạo và dùng trong worker, event log chia sẻ cần thread-safe hoặc phải đọc **sau một điểm đồng bộ** như `get()`/`CountDownLatch.await()`. Một `ArrayList` bị nhiều thread cùng ghi không an toàn; thay bằng synchronized list/queue thích hợp khi cần.

Không dùng `Thread.sleep(100)` rồi assert “close đã gọi”, vì scheduling khác nhau theo máy. Dùng Future completion hoặc latch đảm bảo quan hệ happens-before phù hợp.

### 16.1. Không nhầm cancellation với close thành công

Nếu task đang bị cancel/interrupted:
- Việc Java thoát khỏi try-with-resources khi task thực sự rời scope sẽ kích hoạt cleanup.
- `Future.cancel(true)` chỉ là yêu cầu hủy/interrupt theo implementation và khả năng hợp tác của worker.
- `Future.isCancelled()` **không bảo đảm** `close()` đã được thực thi.
- Nếu worker bị chặn mãi trong API không hỗ trợ interrupt, cleanup cũng có thể chưa chạy.
- Nếu `close()` ném trong khi có exception do interruption, cần kiểm tra primary/suppressed của đường thực thi thực tế.

Test đòi hỏi xác nhận cleanup phải có **kênh xác nhận worker đã thoát** (latch ở `finally`, trạng thái job hoặc cơ chế tương đương), thay vì chỉ assert `cancel(true) == true`.

## 17. Dùng Mockito thay fake viết tay khi nào?

Nếu dependency là interface lớn và mục tiêu chỉ là xác minh số lần gọi `close()`, một mock có thể tiết kiệm code. Ví dụ dạng minh họa cho Mockito:

~~~java
AutoCloseable resource = mock(AutoCloseable.class);
doThrow(new IOException("close failed")).when(resource).close();
~~~

Đây là **fragment Mockito**, cần dependency Mockito, import/static import, và được đặt trong môi trường test phù hợp. `AutoCloseable.close()` khai báo `throws Exception`, nên checked exception `IOException` có thể được cấu hình tại đó theo giao diện.

Nhưng mock không tự chứng minh thứ tự đóng nhiều đối tượng, không tự kiểm tra partial acquisition, cũng không đảm bảo setup fake giống hành vi resource thật. Khi cần test một state machine và nhiều kịch bản lỗi, fake có event log thường dễ đọc hơn. Khi cần xác minh interaction ngắn trong service, Mockito/JUnit là lựa chọn hợp lý.

**Không cần đưa Mockito vào ví dụ Java 17 runnable** của bài này; core behavior được kiểm chứng bằng fake thuần Java.

## 18. Phân biệt “fake xác minh Java semantics” và “integration test xác minh hạ tầng”

Fake resource là một **unit-test double**. Nó không thể tự chứng minh rằng:
- OS đã ghi dữ liệu vào ổ đĩa.
- Socket thật đã giải phóng đúng thời điểm.
- JDBC driver thật xử lý lỗi khi `close` theo giả định.
- Một HTTP request đã bị remote server hủy.
- Connection pool khôi phục về trạng thái sạch sau close exception.
- Resource native/library đóng an toàn khi đang bị interrupt.

Cần dùng integration test bổ sung khi hợp đồng thực tế phụ thuộc vào driver/filesystem/server. Ví dụ database có thể cần thử lỗi kết nối, rollback, close và trạng thái transaction với instance test thật. Tuy nhiên integration test cũng không thể ép mọi lỗi phần cứng xuất hiện deterministic; có thể cần fault injection ở client adapter, proxy hoặc test fixture được kiểm soát.

### 18.1. Lớp adapter giúp test lỗi dễ hơn

Nếu code nghiệp vụ đang trực tiếp gọi `Files.newOutputStream` khắp nơi, khó thay thế bằng fake để chủ động gây lỗi init/close. Có thể đưa điểm tạo resource vào interface/factory:

~~~java
@FunctionalInterface
interface ResourceFactory<R extends AutoCloseable> {
    R open() throws Exception;
}
~~~

Đây là **fragment Java 17**. Hàm nghiệp vụ nhận `ResourceFactory<R>` có thể dùng fake trong unit test và implementation thật trong production. Tuy vậy, chọn kiểu checked exception rộng `Exception` chỉ để minh họa; code sản phẩm nên thu hẹp type khi phù hợp, ví dụ `IOException`.

Dependency injection không cần framework: constructor injection hoặc truyền factory vào phương thức đã đủ cho nhiều test đơn giản. Quan trọng là tạo được seam để tiêm lỗi **tại đúng thời điểm** mà không sửa source business chỉ cho test.

## 19. Kiểm tra nội dung lỗi ngoài message

Test chỉ assert `error.getMessage()` là một bước, nhưng có thể chưa đủ. Cần đánh giá:
- **Exception type** (IOException, SQLException, custom type).
- **Identity** nếu inject chính exception object và test đòi đúng cause.
- **Cause chain**, nếu service bọc exception.
- **Suppressed list** (độ dài, thứ tự, type, message).
- **State** của resource và dữ liệu sau lỗi.
- **Side effect** đã xảy ra trước khi lỗi.
- **Log/reporting** nếu contract yêu cầu.
- **Số lần và thứ tự gọi close**.
- **Leak** khi exception xảy ra trong initializer hoặc body.

Với try-with-resources chuẩn, suppressed được gắn vào exception chính khi lỗi đóng xuất hiện trong quá trình unwinding. Nhưng nếu tầng trên wrap exception bằng `new RuntimeException("...", e)`, bạn phải kiểm tra `e` (cause của wrapper) để tìm suppressed đúng chỗ; đừng giả định suppressed từ cause sẽ tự được sao chép lên wrapper.

### 19.1. Tránh assert stack trace line number trong unit test nghiệp vụ

Có thể assert class/method của Throwable nếu cần điều tra, nhưng hardcode `FakeResourceFailureDemo.java:42` trong unit test là dễ vỡ khi thêm comment hoặc refactor. Mục tiêu của bài là **ngữ nghĩa lifecycle và exception preservation**, không phải layout dòng source. Assert type/cause/suppressed/events sẽ bền vững hơn.

## 20. Những anti-pattern thường gặp

### 20.1. “Test đã thấy IOException, vậy cleanup ổn”

Sai: có thể đó là lỗi init, body hoặc close. Kiểm tra **nguồn lỗi chính, suppressed và event log**.

### 20.2. “Close B ném thì A chắc đã được đóng”

Đúng với try-with-resources đã đăng ký cả A/B và không có yếu tố bất thường phá luồng cleanup; **không đúng** với `finally { b.close(); a.close(); }` tự viết mà không bảo vệ. Hãy test đúng implementation.

### 20.3. “Constructor ném thì TWR sẽ gọi close trên chính object lỗi”

Sai: resource initializer không trả object hoàn chỉnh. TWR chỉ dọn các resource đã khởi tạo thành công trước đó; dọn partial acquisition của constructor/factory là trách nhiệm khác.

### 20.4. “Lỗi close luôn trở thành primary”

Sai: nếu body hoặc một initializer sau đã ném primary, close failure được suppressed. Nếu body thành công, close failure đầu tiên (theo thứ tự đóng) có thể là primary.

### 20.5. “`getSuppressed()[0]` lúc nào cũng có”

Sai: mảng có thể rỗng. Luôn kiểm tra hoặc dùng vòng lặp; không biến code chẩn đoán thành nguồn `ArrayIndexOutOfBoundsException`.

### 20.6. “`close()` ném đồng nghĩa resource chắc chắn chưa đóng”

Sai: implementation có thể đã giải phóng phần lớn tài nguyên rồi mới ném. Chỉ biết tình trạng thật qua contract và quan sát phù hợp.

### 20.7. “Fake có output đúng chứng minh DB thật rollback đúng”

Sai: fake không thay driver và transaction protocol. Cần integration test, transaction semantics và bằng chứng thực tế.

### 20.8. “Chạy nhiều test trên cùng một fake tiện hơn”

Dễ làm sự kiện và trạng thái cũ gây false positive/false negative. Tạo fake mới mỗi test hoặc reset bằng cơ chế rõ ràng, không để suite phụ thuộc thứ tự.

### 20.9. “Chỉ cần gọi `close` từ thread ngoài sau cancel”

Không chắc an toàn với tài nguyên dùng chung; cần ownership/thread-safety đúng. Khi worker chưa ra scope thì cleanup từ ngoài có thể tranh chấp với worker.

### 20.10. “Cứ catch `Throwable` trong fake cho khỏi crash test”

Catch rộng có thể che lỗi thực của logic test. Hãy chỉ catch type mà test đang kiểm tra; để assertion failure/unexpected exception làm test fail.

### 20.11. “Mocking `close()` ném là đủ kiểm thử mọi bug”

Mô phỏng lỗi ở close không bao phủ init thất bại, close order, resource mở dở và side effects. Cần test matrix đầy đủ theo các failure points.

### 20.12. “Suppressed = cause”

Hai cơ chế khác nhau. Lỗi đóng nằm trong suppressed của primary ở try-with-resources, không nhất thiết nằm trong `getCause()`.

## 21. Cây quyết định khi xây bộ kiểm thử fake resource

~~~text
Ứng dụng đang sở hữu tài nguyên AutoCloseable?
|
+-- Khởi tạo mấy resource?
|     +-- Một: thử init-fail / body-fail / close-fail
|     +-- Nhiều: thử fail ở mỗi vị trí init và reverse close
|
+-- Có thể ném ở body?
|     +-- Có: cho body ném X và close ném Y, xác minh X primary
|     +-- Không: cho close ném, xác minh close failure primary
|
+-- Constructor/factory cấp phát nội bộ?
|     +-- Có: test partial acquisition cleanup
|     +-- Không: test close của resource đã init thành công
|
+-- Có side effect bền vững?
|     +-- Có: bổ sung transaction/rollback/compensation test
|     +-- Không: kiểm tra state in-memory và event log
|
+-- Có executor/concurrency?
      +-- Có: đồng bộ bằng latch/future và thread-safe event log
      +-- Không: fake thuần Java là đủ
~~~

Đây là sơ đồ quyết định dành cho thiết kế test. Không phải tất cả dự án cần cùng số lượng case, nhưng mỗi hành vi mà API **cam kết** phải có đường thất bại được kiểm thử.

## 22. Checklist QA cho fake resource

- [ ] Fake triển khai đúng giao diện AutoCloseable hoặc contract resource cần mô phỏng.
- [ ] Có cách inject lỗi ở initializer và ở `close()` một cách deterministic.
- [ ] Với nhiều resource, event log ghi đúng identity và thứ tự init/close.
- [ ] Mỗi test dùng fake/state riêng, không rò dữ liệu giữa tests.
- [ ] Có case tất cả thành công để làm đường baseline.
- [ ] Có case resource A init lỗi: body không chạy, không gọi close trên A khởi tạo thất bại.
- [ ] Có case B init lỗi sau A: A được đóng, B không có `close:B`.
- [ ] Có case body ném X, close ném Y: X primary, Y suppressed.
- [ ] Có case body bình thường, close ném: lỗi close đi ra caller.
- [ ] Có case hai close cùng lỗi: tất cả resource phù hợp vẫn được thử đóng; thứ tự suppressed chính xác.
- [ ] Có case constructor/factory mở resource nội bộ rồi fail: các handle mở dở được dọn theo ownership.
- [ ] Assert type, cause, suppressed, event log và trạng thái resource; không chỉ một dòng message.
- [ ] Không hardcode source line numbers vào test lifecycle.
- [ ] Với concurrency, có điểm đồng bộ chắc chắn trước khi assert cleanup.
- [ ] Không coi Future canceled là bằng chứng worker đã hoàn tất cleanup.
- [ ] Nếu resource thật có transaction/flush, có integration test riêng cho hậu điều kiện.
- [ ] Không log exception trùng/nuốt failure rồi trả success sai.
- [ ] Giữ cause và suppressed khi wrap exception qua các tầng.
- [ ] Không dùng fake để hứa hẹn điều driver/remote service thật không cam kết.

## 23. Bài tập thực hành

Một service cần mở `InputStream` đọc file cấu hình, đồng thời mở `OutputStream` ghi kết quả. Theo thứ tự, nó mở input, mở output, đọc dữ liệu, ghi kết quả rồi thoát khỏi try-with-resources.

Hãy xây một `FakeInput` và `FakeOutput` có các tùy chọn lỗi:
1. Input init lỗi trước khi output bắt đầu; assert output không được mở.
2. Input mở thành công, output init lỗi; assert input được đóng một lần, body không chạy.
3. Body đọc ném `IOException("read failed")`, input close cũng lỗi, output close lỗi; assert lỗi read là primary và hai close failures được suppressed theo **thứ tự đóng ngược**.
4. Body thành công, chỉ output close lỗi; assert caller nhận lỗi close output.
5. Output close lỗi rồi input close lỗi; assert input vẫn được thử đóng.
6. Output constructor mở một file tạm trước khi init lỗi; assert file tạm do factory sở hữu được dọn, không trông chờ TWR gọi `close()` trên object khởi tạo dở.
7. Nếu file output đã ghi một phần trước khi close lỗi, xác định trạng thái đầu ra được phép tồn tại và cách transaction/staging/publish giải quyết tính nhất quán.
8. Với task chạy trên `ExecutorService`, kiểm tra lỗi body/close đi qua `ExecutionException.getCause()`; thêm latch kiểm chứng cleanup khi yêu cầu cancellation.

Dùng một event log cho từng test, inject exception có tên khác nhau (`input-init`, `output-init`, `read`, `write`, `input-close`, `output-close`), rồi assert cả **kết quả ngoại lệ và lịch sử thao tác**. Không cần làm hỏng file hoặc mạng thật để tái hiện các trường hợp lỗi.

## 24. Kết luận

Một fake resource giúp bạn tạo điều kiện thất bại tại **đúng điểm vòng đời** và chứng minh chương trình phản ứng đúng, thay vì trông chờ hệ điều hành hay dependency thật thỉnh thoảng gặp lỗi. Với Java try-with-resources, các thuộc tính quan trọng cần test là:

- Resource đã khởi tạo thành công được đóng theo **thứ tự ngược**.
- Lỗi từ body hoặc initializer được giữ làm **primary** khi cleanup cũng ném lỗi.
- Lỗi `close()` thứ cấp nằm trong **`getSuppressed()`**, không tự xóa lỗi chính.
- Một resource **khởi tạo thất bại** không được TWR tự gọi `close()`, nên constructor/factory phải dọn phần đã cấp phát nội bộ.
- Cleanup không đồng nghĩa rollback các tác động trước đó.

**Kết quả test đáng tin** phải kiểm tra cả exception, suppressed exceptions, thứ tự event và hậu điều kiện resource. Khi áp dụng vào file/socket/database thật, kết hợp unit test dùng fake với integration test ở ranh giới hạ tầng để không suy diễn những bảo đảm mà resource implementation không cung cấp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-testing-007">Kiểm thử finally không che lỗi và không thay đổi kết quả ngoài ý muốn</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử `finally` không che lỗi và không thay đổi kết quả ngoài ý muốn

Trong Java, `finally` thường dùng để dọn dẹp sau một khối `try`, bất kể khối đó kết thúc bình thường hay bằng một ngoại lệ. Chính vì được thực thi trên nhiều đường thoát, `finally` rất dễ tạo ra lỗi khó phát hiện: **một câu lệnh `return` trong `finally` có thể ghi đè giá trị trả về hoặc nuốt ngoại lệ đang được truyền**, còn **một ngoại lệ phát sinh khi dọn dẹp có thể che mất ngoại lệ gốc**. Ngoài ra, nếu `finally` thay đổi đối tượng hoặc trạng thái chung, giá trị mà caller nhận được có thể khác với ý định ban đầu dù không có ngoại lệ mới.

Mục tiêu của bài này là xây dựng **kiểm thử hành vi** để phát hiện chính những sai sót ấy. Ta sẽ quan sát thứ tự thực thi của `try`–`catch`–`finally`, phân biệt kết quả trả về với trạng thái của đối tượng, kiểm thử lỗi kép (lỗi xử lý chính và lỗi cleanup), viết test kiểm tra bất biến trạng thái và hiểu khi nào nên dùng `try-with-resources`.

## 1. Hợp đồng cơ bản của `finally`

Cấu trúc thường gặp:

```java
try {
    performOperation();
} catch (ExpectedException error) {
    handleExpectedError(error);
} finally {
    releaseOwnedResource();
}
```

Đây là **mã minh họa**, chưa biên dịch độc lập vì các phương thức và kiểu phụ thuộc phải được ứng dụng định nghĩa.

Các điểm cần nhớ:

1. Nếu `try` kết thúc bình thường và không có câu lệnh khác đổi luồng điều khiển, `finally` được thực thi trước khi tiếp tục phía sau.
2. Nếu `try` chuẩn bị `return` hoặc `throw`, Java cũng thực hiện `finally` trước khi trả về hoặc truyền ngoại lệ ra ngoài.
3. Nếu `catch` chuẩn bị `return` hoặc `throw`, `finally` vẫn chạy trên đường thoát đó.
4. Nếu **chính `finally` kết thúc bất thường** bằng `throw`, `return`, `break` hoặc `continue`, việc hoàn tất đang chờ từ `try`/`catch` có thể bị **thay thế**. Đây là nguồn gây che lỗi hoặc thay đổi kết quả.
5. `finally` không được bảo đảm sẽ chạy trong **mọi** tình huống có thể xảy ra: ví dụ JVM bị chấm dứt đột ngột, tiến trình bị kill, hoặc luồng không bao giờ rời khỏi `try`. Không dùng `finally` làm cam kết tuyệt đối đối với sự cố mất điện hay process crash.

Vì vậy, không chỉ kiểm tra rằng cleanup đã được gọi; còn phải kiểm tra **ngoại lệ nào đến caller**, **giá trị nào được trả**, và **trạng thái nghiệp vụ sau khi dọn dẹp**.

## 2. Những rủi ro phải đưa vào bộ kiểm thử

| Tình huống | Hiện tượng cần nhận diện | Khẳng định kiểm thử |
| --- | --- | --- |
| `try` thành công, `finally` bình thường | Cleanup chạy, kết quả gốc được giữ | Giá trị trả về và số lần cleanup đúng |
| `try` ném lỗi, `finally` bình thường | Lỗi gốc đi đến caller | Đúng loại exception, message, cause/identity cần thiết |
| `try` trả giá trị, `finally` cũng `return` | Giá trị từ `finally` **ghi đè** | Test phải phát hiện kết quả sai |
| `try` ném lỗi, `finally` lại `return` | Ngoại lệ gốc bị **nuốt** | Test kỳ vọng exception phải thất bại |
| `try` ném lỗi, `finally` cũng ném lỗi | Lỗi cleanup có thể **che** lỗi chính | Bảo toàn hoặc báo đầy đủ cả hai lỗi |
| `try` trả tham chiếu đối tượng, `finally` sửa đối tượng ấy | Caller có thể thấy đối tượng đã bị biến đổi | Kiểm tra trạng thái thực, không chỉ tham chiếu |
| `try-with-resources` có lỗi ở thân và `close()` | Lỗi thân thường là lỗi chính, `close()` là suppressed | Kiểm tra `getSuppressed()` |
| Cleanup được gọi nhiều lần hoặc không được gọi | Rò rỉ tài nguyên, thay đổi ngoài ý muốn | Kiểm tra số lần cleanup theo từng đường thoát |

**Quy tắc kiểm thử:** có ít nhất một ca cho đường thành công, một ca cho lỗi ở thao tác chính và một ca cho lỗi khi dọn dẹp. Với mã trả dữ liệu hoặc sửa trạng thái, thêm kiểm thử bất biến và giá trị trả về.

## 3. `return` trong `finally` ghi đè `return` trong `try`

Xét đoạn phương thức:

```java
int calculate() {
    try {
        return 10;
    } finally {
        return 20;
    }
}
```

Phương thức **trả `20`**, không phải `10`. Đây là đoạn Java minh họa có thể đặt trong một lớp, nhưng không phải chương trình độc lập. Tại điểm `return 10`, Java đã quyết định một kết quả đang chờ. Tuy nhiên, câu lệnh `return 20` trong `finally` kết thúc phương thức bằng kết quả mới và thay thế kết quả cũ.

Điều tương tự còn xảy ra với ngoại lệ:

```java
int hideFailure() {
    try {
        throw new IllegalStateException("original failed");
    } finally {
        return 20;
    }
}
```

Trong trường hợp này, caller nhận **`20` như một kết quả bình thường**, còn `IllegalStateException("original failed")` không được truyền ra. Lỗi lập trình nghiêm trọng có thể bị biến thành thành công giả.

**Không đặt `return` trong `finally`.** Nếu một đoạn code sử dụng `finally` chỉ để cleanup, hãy thiết kế cleanup không tham gia quyết định thành công/thất bại của thao tác chính. Test cần khẳng định rằng một lỗi ở `try` vẫn xuất hiện ở caller.

## 4. Ngoại lệ trong `finally` có thể che ngoại lệ gốc

Đoạn sau ném lỗi từ cả hai nơi:

```java
void perform() {
    try {
        throw new IllegalArgumentException("business failed");
    } finally {
        throw new IllegalStateException("cleanup failed");
    }
}
```

Caller thường chỉ bắt được `IllegalStateException("cleanup failed")`. Ngoại lệ `IllegalArgumentException("business failed")` bị thay thế bởi cách `finally` kết thúc bất thường. Java **không tự động đưa lỗi chính vào `getSuppressed()`** trong `try/finally` viết thủ công như trên.

Đây là lý do một phép kiểm thử chỉ dùng `assertThrows(IllegalStateException.class, ...)` có thể **xanh sai**: nó thấy một ngoại lệ nhưng không phát hiện lỗi chính đã bị che. Cần kiểm tra loại lỗi **được hợp đồng mong đợi**, thông điệp hoặc cause/suppressed, và trạng thái sau thao tác.

Nếu cleanup cũng có thể thất bại, cần xác định chính sách:

- Lỗi xử lý chính phải được ưu tiên? Khi đó giữ lỗi chính, đính kèm lỗi cleanup như suppressed nếu thích hợp.
- Chỉ có lỗi cleanup, không có lỗi xử lý? Khi đó lỗi cleanup có thể là lỗi chính cần ném ra.
- Cleanup thất bại nhưng không ảnh hưởng tới hợp đồng của thao tác? Chỉ nên tiếp tục nếu việc bỏ qua lỗi là **được thiết kế rõ**; một lỗi đóng tài nguyên có thể là sự cố mất dữ liệu nên không thể mặc nhiên bỏ qua.
- Khi dùng `AutoCloseable`, ưu tiên `try-with-resources` để Java thực thi quy tắc ưu tiên ngoại lệ cho tình huống thân `try` và `close()` cùng ném lỗi.

## 5. Ví dụ Java 17+ chạy được: phân biệt các đường `return` và `throw`

Tạo tệp **`FinallyControlFlowDemo.java`**:

```java
public class FinallyControlFlowDemo {
    static int safeReturn() {
        try {
            return 10;
        } finally {
            System.out.println("cleanup ran");
        }
    }

    static int overrideReturn() {
        try {
            return 10;
        } finally {
            return 20; // Phản ví dụ: ghi đè kết quả.
        }
    }

    static int swallowFailure() {
        try {
            throw new IllegalArgumentException("original failed");
        } finally {
            return 30; // Phản ví dụ: nuốt ngoại lệ.
        }
    }

    static void maskFailure() {
        try {
            throw new IllegalArgumentException("original failed");
        } finally {
            throw new IllegalStateException("cleanup failed");
        }
    }

    static void preserveFailure() {
        try {
            throw new IllegalArgumentException("original failed");
        } finally {
            System.out.println("cleanup completed");
        }
    }

    static StringBuilder mutableReturn() {
        StringBuilder result = new StringBuilder("initial");
        try {
            return result;
        } finally {
            result.append("-changed");
        }
    }

    public static void main(String[] args) {
        System.out.println("safeReturn=" + safeReturn());
        System.out.println("overrideReturn=" + overrideReturn());
        System.out.println("swallowFailure=" + swallowFailure());

        try {
            maskFailure();
        } catch (RuntimeException error) {
            System.out.println("maskedType="
                    + error.getClass().getSimpleName());
            System.out.println("maskedMessage=" + error.getMessage());
            System.out.println("maskedCause=" + error.getCause());
            System.out.println("maskedSuppressed="
                    + error.getSuppressed().length);
        }

        try {
            preserveFailure();
        } catch (IllegalArgumentException error) {
            System.out.println("preservedMessage=" + error.getMessage());
        }

        System.out.println("mutableReturn=" + mutableReturn());
    }
}
```

Biên dịch, chạy với Java 17:

```bash
javac --release 17 FinallyControlFlowDemo.java
java FinallyControlFlowDemo
```

Đầu ra dự kiến:

```text
cleanup ran
safeReturn=10
overrideReturn=20
swallowFailure=30
maskedType=IllegalStateException
maskedMessage=cleanup failed
maskedCause=null
maskedSuppressed=0
cleanup completed
preservedMessage=original failed
mutableReturn=initial-changed
```

**Những điều ví dụ chứng minh:**

- `safeReturn()` để `finally` dọn dẹp rồi trả **giá trị ban đầu 10**.
- `overrideReturn()` trả **20** dù nhánh `try` định trả 10.
- `swallowFailure()` trả **30** và làm mất hoàn toàn tín hiệu lỗi `original failed` ở ranh giới gọi.
- `maskFailure()` chỉ phơi ra lỗi cleanup và **không tự** đặt lỗi gốc làm cause hoặc suppressed.
- `preserveFailure()` cho lỗi gốc tiếp tục tới caller sau một `finally` kết thúc bình thường.
- `mutableReturn()` cho thấy **tham chiếu đã chọn để trả về** vẫn chỉ vào đối tượng `StringBuilder` mà `finally` vừa sửa. Đừng nhầm việc Java đã chọn giá trị `return` với việc đối tượng mutable được “đóng băng”.

Một khác biệt tinh tế: nếu `try` trả **giá trị nguyên thủy** hoặc một `String` đã được tính xong, việc sửa biến cục bộ trong `finally` thông thường không thay thế giá trị trả về đã đánh giá. Nhưng nếu giá trị trả về là **tham chiếu tới object mutable**, nội dung object có thể thay đổi trước khi caller nhận. Hai trường hợp nên được kiểm tra riêng khi API trả về collection, DTO hay đối tượng mang trạng thái.

## 6. Khi `finally` sửa state, kiểm thử những gì?

Ngoại lệ không phải nguy cơ duy nhất. Một `finally` có thể thực hiện lệnh ghi thông tin, cập nhật bộ đếm, đóng tài nguyên hoặc phát sự kiện. Những thao tác này có thể làm kết quả thực tế khác trạng thái mà nhánh `try` đã chuẩn bị.

Ví dụ phản ví dụ:

```java
OrderResult execute(OrderResult result) {
    try {
        result.markCompleted();
        return result;
    } finally {
        result.markCancelled(); // Làm thay đổi object đã được return.
    }
}
```

Đây là **mã minh họa**, cần khai báo `OrderResult` cùng hai phương thức tương ứng, không phải chương trình độc lập. Caller nhận chính object `result`, nhưng sau khi `finally` chạy, cờ trạng thái có thể là `CANCELLED` thay vì `COMPLETED`. Việc kiểm thử chỉ dùng `assertSame(result, returned)` không phát hiện lỗi; cần kiểm tra **giá trị trạng thái bên trong**.

Với đối tượng chia sẻ giữa nhiều luồng, `finally` còn có thể thay đổi dữ liệu trong khi công việc khác đang truy cập. Cần cơ chế đồng bộ và ranh giới sở hữu rõ, không xem `finally` như phép toán nguyên tử.

Bộ kiểm thử nên xác nhận:

1. Trường nghiệp vụ giữ đúng trạng thái sau `return`.
2. Cleanup chỉ dọn dẹp **tài nguyên thuộc quyền sở hữu**, không vô tình sửa dữ liệu người gọi cần sử dụng.
3. Không có callback, sự kiện hay ghi DB đánh dấu thành công trong `finally` khi `try` thất bại.
4. Trạng thái sau ngoại lệ phù hợp với chính sách rollback hoặc thất bại đã được thiết kế.
5. Việc đóng tài nguyên không làm đối tượng trả về trở nên không thể dùng nếu hợp đồng yêu cầu caller sử dụng nó sau đó.

Một mẫu thường sai:

```java
try {
    persistChanges();
} catch (Exception error) {
    logFailure(error);
} finally {
    publishSuccessEvent();
}
```

Đây là mảnh mã minh họa, cần các phương thức phụ thuộc. `publishSuccessEvent()` chạy cả khi `persistChanges()` thất bại, tạo “thành công giả”. Sự kiện thành công nên được phát sau khi thao tác thực sự commit thành công, không phải vô điều kiện trong `finally`.

## 7. `try-with-resources` giữ lỗi gốc bằng suppressed exceptions

Một rủi ro thường gặp là cả xử lý chính và đóng tài nguyên đều thất bại. Với `try-with-resources`, Java định nghĩa rõ việc xử lý hai lỗi này:

- Nếu thân `try` ném lỗi A và `close()` ném lỗi B, A thường vẫn là **ngoại lệ chính**, B được thêm vào `A.getSuppressed()`.
- Nếu thân `try` thành công nhưng `close()` ném lỗi B, B là ngoại lệ được truyền ra.
- Nếu nhiều tài nguyên đóng thất bại, có thể có nhiều suppressed exceptions. Tài nguyên trong `try-with-resources` được đóng **theo thứ tự ngược lại** với lúc khai báo.
- Suppression là cơ chế báo lỗi phụ, **không phải** phép rollback cho các thay đổi nghiệp vụ.
- Nếu một exception được tạo với suppression bị tắt thì cách lưu lỗi phụ có thể khác; không nên giả định mọi `Throwable` tùy biến luôn chứa suppressed errors.

Một ví dụ rút gọn:

```java
try (SomeResource resource = openResource()) {
    performBody(resource);
}
```

Đây là mã minh họa chưa độc lập vì cần kiểu `SomeResource` (triển khai `AutoCloseable`) và các phương thức `openResource()`, `performBody(...)`.

So sánh với một khối `finally` thủ công:

```java
try {
    performBody();
} finally {
    resource.close();
}
```

Đây cũng chỉ là mã minh họa. Nếu cả `performBody()` và `resource.close()` đều ném ngoại lệ, lời gọi `close()` trong `finally` có thể **che** lỗi của `performBody()`. Không có quy tắc tự động chuyển lỗi cleanup vào suppressed như trong `try-with-resources`. Cần thay đổi thiết kế để bảo toàn lỗi chính.

## 8. Ví dụ Java 17+ chạy được: một bộ kiểm thử tự chứa cho cleanup

Ví dụ sau dùng các hàm assertion nhỏ trong `main`, không yêu cầu JUnit hay thư viện bên ngoài. Nó kiểm tra bốn đường đi của `try-with-resources`: thành công, chỉ lỗi thân `try`, chỉ lỗi đóng tài nguyên, và **cả hai cùng lỗi**. Nó cũng xác nhận tài nguyên đã được đóng và số lần đóng.

Tạo tệp **`FinallyCleanupContractDemo.java`**:

```java
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public class FinallyCleanupContractDemo {
    static final class Resource implements AutoCloseable {
        final boolean failOnClose;
        int closeCount;

        Resource(boolean failOnClose) {
            this.failOnClose = failOnClose;
        }

        @Override
        public void close() throws IOException {
            closeCount++;
            if (failOnClose) {
                throw new IOException("close failed");
            }
        }
    }

    static void execute(
            boolean failBody,
            boolean failClose,
            AtomicReference<Resource> reference) throws IOException {
        Resource resource = new Resource(failClose);
        reference.set(resource);

        try (resource) {
            if (failBody) {
                throw new IOException("body failed");
            }
        }
    }

    static void require(boolean condition, String explanation) {
        if (!condition) {
            throw new AssertionError(explanation);
        }
    }

    static void verify(
            boolean failBody, boolean failClose,
            String expectedPrimary, int expectedSuppressed) {
        AtomicReference<Resource> reference = new AtomicReference<>();
        IOException caught = null;

        try {
            execute(failBody, failClose, reference);
        } catch (IOException error) {
            caught = error;
        }

        Resource resource = reference.get();
        require(resource != null, "resource was not created");
        require(resource.closeCount == 1, "close must execute exactly once");

        if (expectedPrimary == null) {
            require(caught == null, "unexpected IOException");
        } else {
            require(caught != null, "expected IOException");
            require(expectedPrimary.equals(caught.getMessage()),
                    "wrong primary exception");
            require(caught.getSuppressed().length == expectedSuppressed,
                    "wrong suppressed count");
            if (expectedSuppressed == 1) {
                require("close failed".equals(
                        caught.getSuppressed()[0].getMessage()),
                        "wrong suppressed exception");
            }
        }
    }

    public static void main(String[] args) {
        verify(false, false, null, 0);
        System.out.println("success: PASS");

        verify(true, false, "body failed", 0);
        System.out.println("body failure: PASS");

        verify(false, true, "close failed", 0);
        System.out.println("close failure: PASS");

        verify(true, true, "body failed", 1);
        System.out.println("dual failure preserves body: PASS");
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 FinallyCleanupContractDemo.java
java FinallyCleanupContractDemo
```

Đầu ra khi tất cả điều kiện đều đúng:

```text
success: PASS
body failure: PASS
close failure: PASS
dual failure preserves body: PASS
```

Nếu một điều kiện vi phạm, chương trình ném `AssertionError` và không in dòng `PASS` tương ứng.

**Điểm đáng học từ cấu trúc test:**

- `AtomicReference<Resource>` cho phép test kiểm tra `Resource` được tạo bên trong hàm `execute`. Ở ví dụ tuần tự này nó chủ yếu là một cách chuyển tham chiếu ra ngoài để kiểm tra, **không chứng minh tính nguyên tử cho một nghiệp vụ nhiều luồng**.
- `closeCount == 1` kiểm tra cleanup được thực hiện đúng số lần ở cả đường thành công và đường lỗi.
- `expectedPrimary` kiểm tra **thông điệp của lỗi chính**, không chỉ kiểm tra “có lỗi”.
- Khi `failBody && failClose`, test bắt buộc `body failed` là lỗi chính và `close failed` nằm trong `getSuppressed()` của chính lỗi đó.
- Khi chỉ `close()` thất bại, test cho phép lỗi cleanup trở thành **lỗi chính** vì không có lỗi thân `try` cần được ưu tiên.

Kiểm thử kiểu này hữu ích hơn `try { execute(); } catch (Exception ignored) {}`: nó làm rõ hợp đồng lỗi và sẽ thất bại nếu một refactor của cleanup vô tình che ngoại lệ gốc.

## 9. Nếu bắt buộc phải dùng `finally` thủ công, bảo toàn lỗi chính thế nào?

Có những tài nguyên không triển khai `AutoCloseable`, hoặc ứng dụng phải thực hiện cleanup đặc biệt. Khi đó không nên chỉ thêm `finally { close(); }` rồi cho rằng tình huống lỗi kép đã được xử lý.

Một phương án về mặt khái niệm là:

1. Ghi nhận lỗi chính khi nó được ném.
2. Khi cleanup thất bại, nếu **đã có lỗi chính**, gắn lỗi cleanup vào nó bằng `addSuppressed(...)` (khi phù hợp).
3. Nếu **không có lỗi chính**, để lỗi cleanup được truyền ra theo hợp đồng.
4. Không dùng `return` trong `finally` và không tạo ngoại lệ mới chỉ từ `getMessage()` làm mất cause.

Đoạn Java minh họa bên trong một phương thức được phép truyền `Exception`:

```java
Exception primary = null;
try {
    doWork();
} catch (Exception failure) {
    primary = failure;
    throw failure;
} finally {
    try {
        cleanup();
    } catch (Exception cleanupFailure) {
        if (primary != null) {
            primary.addSuppressed(cleanupFailure);
        } else {
            throw cleanupFailure;
        }
    }
}
```

Đoạn trên **không phải chương trình độc lập** vì cần `doWork()` và `cleanup()` được ứng dụng khai báo; chữ ký phương thức bao quanh phải phù hợp với các checked exception. Đây là mẫu đơn giản cho các trường hợp lỗi thuộc `Exception`.

**Giới hạn quan trọng:** nếu `doWork()` ném `Error` chứ không phải `Exception`, nhánh `catch (Exception ...)` không giữ nó vào `primary`; một `Exception` từ cleanup trong `finally` khi đó có thể che `Error`. Không nên tự mở rộng thành `catch (Throwable)` tùy tiện nếu không thực sự hiểu hợp đồng và chính sách xử lý `Error`. Khi tài nguyên thích hợp, **`try-with-resources` thường là lựa chọn an toàn, rõ ràng hơn**.

Tránh thêm suppressed exception trùng hoặc tự thêm chính throwable vào chính nó. `addSuppressed(this)` bị từ chối. Cũng đừng biến lỗi cleanup thành “chỉ log và bỏ qua” trong mọi trường hợp: đôi khi cleanup lỗi là dấu hiệu dữ liệu chưa được ghi/đóng an toàn.

## 10. Kiểm thử `finally` với JUnit 5

Trong dự án có JUnit 5, các assertion giúp biểu đạt trực tiếp yêu cầu. Ví dụ sau là **mảnh test minh họa**, cần dependency JUnit 5, import `org.junit.jupiter.api.Test` và các assertion từ `org.junit.jupiter.api.Assertions`, đồng thời cần các phương thức/kiểu `service` tương ứng. Nó **không phải** chương trình Java chạy độc lập chỉ với JDK:

```java
@Test
void cleanupDoesNotMaskOriginalFailure() {
    IllegalArgumentException failure = assertThrows(
            IllegalArgumentException.class,
            () -> service.executeWithCleanupFailure());

    assertEquals("operation failed", failure.getMessage());
    assertEquals(1, failure.getSuppressed().length);
    assertEquals("cleanup failed",
            failure.getSuppressed()[0].getMessage());
}
```

Ca này phát hiện được lỗi **ngoại lệ gốc bị che**. Nếu `finally` ném `IllegalStateException` thay vì giữ lỗi chính, `assertThrows(IllegalArgumentException.class, ...)` sẽ thất bại.

Kiểm thử kết quả trả về:

```java
@Test
void cleanupDoesNotReplaceReturnValue() {
    int value = service.computeAndClose();
    assertEquals(10, value);
}
```

Ca này bắt được việc một người sau đó thêm `return 20` vào `finally`.

Kiểm thử trạng thái đối tượng mutable:

```java
@Test
void returnedResultIsNotMutatedByCleanup() {
    Result result = service.process();
    assertEquals(Status.COMPLETED, result.status());
    assertFalse(result.isClosedUnexpectedly());
}
```

Ca này yêu cầu các lớp `Result`, `Status` và service cụ thể. Nó giúp bắt lỗi mà `assertSame(...)` hoặc kiểm tra non-null đơn giản không phát hiện.

Kiểm thử cleanup đúng một lần:

```java
@Test
void cleanupRunsOnFailure() {
    assertThrows(IllegalArgumentException.class,
            () -> service.failAfterOpeningResource());

    verify(resource).close();
}
```

Ví dụ cuối **giả định** có Mockito hoặc cơ chế spy tương tự để cung cấp `verify(resource).close()`; Mockito không có sẵn trong JDK/JUnit. Nếu không dùng mock framework, một fake resource với bộ đếm như `FinallyCleanupContractDemo` sẽ minh bạch và tránh thêm dependency.

**Không kiểm thử bằng cách chỉ nhìn stdout** nếu hệ thống có state hoặc side effect quan trọng. `assertThrows`, số lần `close`, trạng thái sau lỗi, giá trị trả về và cause/suppressed nên được khẳng định trực tiếp.

## 11. Ma trận kiểm thử đầy đủ cho một tác vụ có cleanup

Ví dụ nghiệp vụ: mở resource, tính một kết quả, đóng resource và trả kết quả. Hãy thiết kế các ca sau:

| Thân thao tác | Cleanup | Kết quả kỳ vọng | Điều cần khẳng định |
| --- | --- | --- | --- |
| Thành công | Thành công | Trả kết quả thật | Kết quả không đổi, close đúng một lần |
| Thất bại | Thành công | Ném lỗi thao tác chính | Không có thành công giả, giữ lỗi gốc |
| Thành công | Thất bại | Theo hợp đồng: báo lỗi cleanup | Không âm thầm trả thành công khi cleanup quan trọng |
| Thất bại | Thất bại | Giữ lỗi thao tác chính, đính lỗi cleanup | Primary/cause/suppressed đúng |
| `try` có `return` | `finally` có thay đổi biến cục bộ | Không thay kết quả đã tính nếu không có `return` mới | Giá trị trả về chính xác |
| `try` trả object mutable | Cleanup sửa object | Theo hợp đồng đối tượng vẫn hợp lệ | Trường dữ liệu/state sau lời gọi |
| `try` ném lỗi | `finally` định `return` | Không được nuốt lỗi | Test phải thất bại nếu không ném |
| Cleanup được gọi hai lần | Lỗi đóng tài nguyên có thể xảy ra | Không đóng lặp ngoài ý muốn | Số lần gọi close/rollback |
| Tác vụ bị ngắt | Cleanup chạy rồi truyền ngắt đúng | Tín hiệu hủy không bị che | Chính sách `InterruptedException` và interrupted status |
| Nhiều resource cùng đóng | Một hoặc nhiều `close()` lỗi | Báo đúng lỗi chính và lỗi phụ | Số/loại suppressed, thứ tự đóng theo hợp đồng |

Bảng là **ma trận thiết kế test**; không phải mọi dự án phải có 10 test riêng biệt nếu các ca đã được bao phủ ở tầng thích hợp. Nhưng đường lỗi kép rất quan trọng: chỉ kiểm tra thành công và lỗi đơn lẻ không đủ để phát hiện lỗi bị che bởi cleanup.

## 12. Ngoại lệ phát sinh ở `catch` cũng có thể bị `finally` che

Đôi khi nhánh `catch` chuyển lỗi kỹ thuật thành một ngoại lệ nghiệp vụ:

```java
try {
    repository.load();
} catch (IOException cause) {
    throw new IllegalStateException("Cannot load", cause);
} finally {
    closeConnection();
}
```

Đây là đoạn minh họa không độc lập (`repository` và `closeConnection` do ứng dụng cung cấp). Nếu `closeConnection()` cũng ném, ngoại lệ cleanup có thể **thay thế** cả `IllegalStateException` mà `catch` vừa tạo. Vì vậy test nên đặt lỗi cho `closeConnection()` ngay cả khi nhánh `catch` đã có chính sách chuyển lỗi.

Dùng `try-with-resources` có thể bảo toàn lỗi đang truyền từ thân `try` hoặc `catch` tùy cấu trúc cụ thể. Để biết chính xác ngoại lệ nào là primary và suppressed trong mã thực tế, cần xem **phạm vi cú pháp của tài nguyên và khối `catch`** chứ không giả định mọi lệnh close đều được ưu tiên giống nhau.

Một nguyên tắc hữu ích: khi chuyển lỗi thành wrapper, giữ cause trong constructor, sau đó bảo đảm cleanup không cắt đi wrapper vừa được tạo.

## 13. `finally` và transaction/rollback: kiểm tra hậu điều kiện

Với đơn vị công việc có giao dịch, một cách thiết kế là:

```java
beginTransaction();
try {
    performBusinessChanges();
    commitTransaction();
} catch (Exception original) {
    rollbackTransaction();
    throw original;
} finally {
    closeTransactionResource();
}
```

Đoạn này **chỉ minh họa luồng điều khiển**, không thể biên dịch độc lập nếu không có các hàm và khai báo `throws` tương ứng. Quan trọng hơn: cả `rollbackTransaction()` trong `catch` lẫn `closeTransactionResource()` trong `finally` **đều có thể ném lỗi**, làm thay thế `original`. Chính sách đúng thường cần giữ nguyên lỗi thao tác và báo lỗi cleanup/rollback như thông tin bổ sung khi lỗi kép xảy ra.

Test tích hợp nên kiểm tra **dữ liệu thật** sau thất bại, không chỉ ngoại lệ:

- Nếu bước ghi DB lỗi, transaction không vô tình commit phần còn lại.
- Nếu rollback cũng lỗi, báo được cả lý do thất bại ban đầu và tình trạng rollback chưa chắc thành công.
- Nếu `finally` dọn kết nối, không có lệnh `commit` hoặc công bố thành công ngoài ý muốn.
- Nếu các hệ thống bên ngoài đã nhận tác dụng phụ, cần đối soát/idempotency/compensation; `finally` không tự hoàn tác HTTP request hay message đã gửi.

Một `finally` kết thúc bình thường **không bảo đảm trạng thái nghiệp vụ tự động trở lại như cũ**. Việc khẳng định trạng thái hậu điều kiện trong test là cách phát hiện sự khác nhau giữa “đã dọn resource” và “đã phục hồi dữ liệu”.

## 14. `InterruptedException` và cleanup

Tác vụ đang chờ có thể nhận `InterruptedException`. Khi xử lý ngắt, `finally` thường vẫn chạy để giải phóng tài nguyên. Nhưng một lỗi trong cleanup có thể làm mất tín hiệu ngắt hoặc thay ngoại lệ ngắt bằng loại khác, khiến tầng gọi tưởng tác vụ thất bại vì nguyên nhân không liên quan.

Nếu phương thức cho phép `throws InterruptedException`, thường nên truyền tiếp ngắt theo hợp đồng. Nếu không thể truyền, có thể cần khôi phục cờ bằng `Thread.currentThread().interrupt()` rồi rời công việc thích hợp; không được tự ý nuốt tín hiệu hủy chỉ để “tiếp tục bình thường”.

Ví dụ minh họa bên trong một phương thức:

```java
try {
    blockingWork();
} catch (InterruptedException interrupted) {
    Thread.currentThread().interrupt();
    return;
} finally {
    releaseResource();
}
```

Đây là mảnh mã cần `blockingWork()` và `releaseResource()`. Dù `catch` định `return`, `finally` vẫn chạy; nếu cleanup ném ngoại lệ unchecked, nó sẽ làm phương thức không trở về bình thường. Chính sách xử lý có thể cần giữ và ghi nhận cả lỗi cleanup lẫn trạng thái ngắt, thay vì mặc định rằng mọi lệnh dọn dẹp đều thành công.

Khi kiểm thử, phải tạo ngắt bằng công cụ đồng bộ như `CountDownLatch` thay vì dựa vào `Thread.sleep(10)` và timing ngẫu nhiên. Test nên xác nhận thao tác kết thúc, resource được giải phóng và interrupted status được giữ/khôi phục đúng chính sách.

## 15. Tránh những assertion quá yếu

Những bài test sau rất dễ bỏ sót lỗi thật:

```java
assertNotNull(service.process());
```

```java
assertThrows(Exception.class, () -> service.run());
```

```java
try {
    service.run();
} catch (Exception ignored) {
    // Chỉ cần “không crash” là pass?
}
```

Đây là các đoạn minh họa không độc lập, cần JUnit/ứng dụng cụ thể. Các dạng này không xác định rõ **kết quả nào đúng**, **ngoại lệ nào phải tồn tại**, **tài nguyên có đóng đủ**, hoặc **trạng thái có bị thay đổi sai**.

Nên thay bằng:

- Assert kiểu exception chính xác nếu đó là hợp đồng.
- Assert message hoặc mã lỗi có ý nghĩa thay vì chỉ `assertNotNull(error)`.
- Assert `getCause()` và `getSuppressed()` khi có thể phát sinh lỗi kép.
- Assert số lần cleanup, không chỉ việc cleanup đã từng chạy.
- Assert hậu điều kiện của state khi kết thúc bình thường **và** khi có lỗi.
- Assert không phát sinh sự kiện “SUCCESS” trên đường thất bại.
- Dùng test doubles/fakes để gây lỗi có kiểm soát trong `close()`, `rollback()` và các điểm cleanup khác.

Không nên bắt `Throwable` vô điều kiện trong code nghiệp vụ chỉ để “bao phủ mọi thứ” cho test. Trong test, `assertThrows` thường nhận một `Class` cụ thể; không biến `AssertionError` bị ném bởi framework test thành kết quả thành công giả.

## 16. Các lỗi thường gặp khi review code

| Sai lầm | Hệ quả | Cách phòng tránh |
| --- | --- | --- |
| `return` trong `finally` | Ghi đè kết quả hoặc nuốt ngoại lệ | Không `return` trong finally; test đường thành công/lỗi |
| `throw` mới từ `finally` mà không giữ lỗi cũ | Ngoại lệ cleanup che lỗi chính | Dùng `try-with-resources` hoặc xử lý suppressed có chủ đích |
| `finally` cập nhật cờ thành công | Báo thành công dù thao tác thất bại | Chỉ cập nhật trạng thái thành công sau commit thật |
| Sửa object mutable đã được return | Caller thấy object khác ý định | Kiểm tra nội dung object và quyền sở hữu |
| Cleanup không được gọi trên đường lỗi | Rò tài nguyên | Fake resource theo dõi số lần close |
| Cleanup được gọi nhiều lần | Double-close, tác dụng phụ bất ngờ | Kiểm tra số lần và tính idempotent |
| Nuốt ngoại lệ trong test | Test “xanh” dù lỗi xảy ra | Assert loại exception, cause, suppressed |
| `assertThrows(Exception.class, ...)` cho mọi ca | Sai loại lỗi vẫn có thể pass | Assert loại phù hợp và nội dung cần thiết |
| Nhầm `finally` với rollback transaction | Dữ liệu đã sửa vẫn còn | Test cả DB state/side effect |
| Bắt `InterruptedException` rồi cleanup che lỗi | Mất tín hiệu hủy | Test trạng thái ngắt và truyền lỗi đúng |
| Tin `finally` luôn chạy dù JVM bị kill | Không có đảm bảo cleanup khi process chấm dứt đột ngột | Thiết kế phục hồi/timeout ngoài tiến trình khi cần |

## 17. Checklist thiết kế bộ test

Trước khi chấp nhận một thay đổi có `finally`, tự hỏi:

1. Đã kiểm thử **kết quả trả về** của đường thành công chưa?
2. Đã kiểm thử **ngoại lệ chính** của đường thất bại chưa?
3. Nếu cleanup ném, test có chứng minh lỗi chính **không bị che** không?
4. Nếu chỉ cleanup lỗi, lỗi ấy có được báo trung thực không?
5. Nếu `try` trả object, `finally` có sửa/đóng object đó không? Test có quan sát được không?
6. Tài nguyên có luôn được giải phóng **đúng một lần** không?
7. Có bất kỳ lệnh `return`, `break`, `continue`, `throw` hoặc đổi trạng thái nghiệp vụ nào trong `finally` cần đánh giá lại không?
8. Nếu dùng `try-with-resources`, test có kiểm tra `getSuppressed()` khi cả body và `close()` lỗi không?
9. Có kịch bản transaction rollback lỗi hoặc I/O cleanup lỗi khiến việc chẩn đoán bị thay thế không?
10. Bộ test có dùng dữ liệu và bộ đếm độc lập thay vì dựa vào thứ tự thread/timing khó dự đoán không?

## 18. Tự kiểm tra

**Câu 1.** `try { return 1; } finally { return 2; }` trả gì?  
**Trả lời:** `2`. `return` từ `finally` thay kết quả đang chờ ở `try`.

**Câu 2.** `try` ném `IOException` nhưng `finally` ném `IllegalStateException`. Caller thường thấy gì?  
**Trả lời:** Ngoại lệ phát sinh từ `finally`, trừ khi mã chủ động bảo toàn lỗi chính. `try/finally` thủ công không tự gắn lỗi cũ làm suppressed.

**Câu 3.** `try` trả một `ArrayList`, `finally` thêm phần tử vào chính list đó. Caller có thể thấy phần tử mới không?  
**Trả lời:** Có. Caller nhận tham chiếu đến đối tượng đã bị thay đổi.

**Câu 4.** Trong `try-with-resources`, body ném `IOException("read")`, `close()` ném `IOException("close")`. Ngoại lệ chính thường là gì?  
**Trả lời:** Lỗi `read`; lỗi `close` được thêm vào `getSuppressed()` của lỗi chính.

**Câu 5.** Vì sao chỉ kiểm tra `assertThrows(Exception.class, ...)` là yếu?  
**Trả lời:** Vì một exception hoàn toàn khác do cleanup gây ra cũng có thể thỏa assertion, dù lỗi gốc bị che.

**Câu 6.** Cleanup chạy trong `finally` và tất cả resource đã đóng. Có chứng minh transaction thành công không?  
**Trả lời:** Không. Dọn tài nguyên và thành công nghiệp vụ là hai điều khác nhau; phải xác nhận hậu điều kiện hoặc rollback/commit.

**Câu 7.** `finally` có luôn chạy khi JVM bị cưỡng bức chấm dứt không?  
**Trả lời:** Không. Nó hoạt động theo luồng điều khiển Java thông thường, không phải đảm bảo phục hồi ở cấp tiến trình.

**Câu 8.** Khi cả xử lý chính và cleanup đều có thể lỗi, nên thử thiết kế gì trước?  
**Trả lời:** `try-with-resources` nếu tài nguyên triển khai `AutoCloseable`, cùng kiểm thử primary/suppressed, số lần close và trạng thái sau lỗi.

**Câu 9.** `return` đã được tính là một giá trị nguyên thủy, rồi `finally` sửa biến cục bộ; giá trị trả về tự đổi không?  
**Trả lời:** Không, nếu `finally` kết thúc bình thường và không có return/throw thay thế. Ngược lại, đối tượng mutable được trả theo tham chiếu vẫn có thể bị chỉnh sửa.

**Câu 10.** Làm sao phát hiện `finally` đã phát sự kiện `SUCCESS` dù `try` thất bại?  
**Trả lời:** Dùng fake publisher hoặc event collector trong test, gây lỗi có kiểm soát ở body rồi assert không có sự kiện thành công và trạng thái nghiệp vụ được bảo toàn.

## 19. Tham khảo

- [Java Language Specification §14.20 — `try` statement](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20): thứ tự và quy tắc hoàn tất của `try`, `catch`, `finally`, `try-with-resources`.
- [Java SE 17 — `Throwable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html): `getCause()`, `getSuppressed()`, `addSuppressed()` và stack trace.
- [Java SE 17 — `AutoCloseable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/AutoCloseable.html): hợp đồng `close()` của tài nguyên.
- [JUnit Jupiter User Guide](https://junit.org/junit5/docs/current/user-guide/): cách kiểm thử bằng `assertThrows`, assertion và test doubles (cần dependency bên ngoài JDK).

**Ghi nhớ:** `finally` là nơi dọn dẹp, **không phải nơi tự ý quyết định lại kết quả**. Test phải chứng minh được ba điều: kết quả hợp lệ **không bị thay**, lỗi chính **không bị che**, và tài nguyên/trạng thái **được xử lý đúng trên mọi đường thoát**. Đặc biệt cần kiểm thử tình huống body và cleanup cùng thất bại, vì đây là nơi những lỗi khó tìm nhất thường xuất hiện.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-testing-008">Kiểm thử ngoại lệ qua Future và CompletableFuture</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử ngoại lệ qua Future và CompletableFuture trong Java

> **Phạm vi:** Java Core → Exception → Kiểm thử. Bài học hướng dẫn viết test có thể lặp lại cho tác vụ bất đồng bộ: lỗi phát sinh trong worker, cách `Future.get()` bao lỗi bằng `ExecutionException`, cách `CompletableFuture.join()` dùng `CompletionException`, xử lý cancellation, timeout, interrupt và các stage phụ thuộc. Ví dụ chạy độc lập bằng Java 17; các test JUnit 5 được phân biệt rõ là cần thư viện ngoài JDK.

## 1. Vì sao kiểm thử exception bất đồng bộ khác kiểm thử đồng bộ?

Trong mã đồng bộ, ngoại lệ ném trên chính call stack của hàm kiểm thử:

~~~java
// Đoạn minh họa JUnit 5:
assertThrows(IllegalArgumentException.class,
        () -> parseRequired("not-a-number"));
~~~

Nhưng khi một công việc được nộp vào `ExecutorService` hoặc chạy trong `CompletableFuture`, lỗi có thể xảy ra trên **thread khác**. Test phải xác định **kênh mà API dùng để công bố thất bại**. Nếu chỉ bao câu lệnh nộp tác vụ bằng `assertThrows`, test rất có thể không thấy lỗi của worker.

~~~java
// Đoạn minh họa JUnit 5; pool có sẵn:
Future<Integer> task = pool.submit(() -> {
    throw new IllegalStateException("worker failed");
});

// Sai mục tiêu: submit thường trả Future bình thường.
// assertThrows(IllegalStateException.class, () -> pool.submit(...));

// Đúng mục tiêu: đọc kết quả của tác vụ qua get().
ExecutionException wrapper =
        assertThrows(ExecutionException.class, task::get);
assertInstanceOf(IllegalStateException.class, wrapper.getCause());
~~~

Một phép kiểm thử exception bất đồng bộ nên xác minh **ba lớp**: (1) *tác vụ* thực sự thất bại, (2) *cơ chế completion* báo lỗi đúng hợp đồng, (3) *nguyên nhân nghiệp vụ* có đúng loại, message/code, cause và trạng thái dữ liệu hay không.

## 2. Bảng định hướng: nên chờ và bắt loại nào?

| API/đường chạy | Lỗi ở worker thể hiện với caller | Loại exception test thường bắt | Câu hỏi tiếp theo |
|---|---|---|---|
| Gọi `Runnable.run()` trực tiếp | Exception đi lên cùng stack | Kiểu lỗi gốc | Có thực sự dùng thread mới không? |
| `new Thread(...).start()` | Không tự ném lỗi `run()` sang caller | `UncaughtExceptionHandler` hoặc kênh tự thiết kế | `join()` chỉ chờ, không ném lỗi worker |
| `ExecutorService.submit(Callable)` | Lỗi được ghi trong `Future` | `ExecutionException` từ `get()` | `getCause()` có đúng lỗi gốc? |
| `ExecutorService.submit(Runnable)` | Future cũng chứa lỗi task | `ExecutionException` | `Runnable` không cần trả giá trị |
| `CompletableFuture.supplyAsync(...)` | Future hoàn tất bất thường | `CompletionException` từ `join()`; `ExecutionException` từ `get()` | Cause và dependent stage thế nào? |
| `CompletableFuture.failedFuture(ex)` | Future lỗi ngay, không cần chạy worker | `join()`/`get()` như trên | Đừng suy diễn đã tạo thread |
| `CompletableFuture.cancel(...)` | Future được đánh dấu canceled | `CancellationException` với future trực tiếp | Công việc thật có dừng không? |
| `get(timeout, unit)` trên future chưa hoàn tất | Hết thời gian chờ | `TimeoutException` | Task có thể **vẫn đang chạy** |
| `get()` khi thread chờ bị interrupt | Chờ bị ngắt | `InterruptedException` | Không nhầm với lỗi computation |

Cần phân biệt ***đường quan sát*** với ***đường gây lỗi***. Test nhắm vào `get()`/`join()` để quan sát một lỗi đã được giữ trong future. Nó không chứng minh exception tự truyền xuyên qua hai thread.

## 3. Quy tắc cốt lõi của Future.get()

`Future<T>` đại diện cho kết quả có thể chưa sẵn sàng. Với Future từ `ExecutorService.submit()`, nếu `Callable.call()` hay `Runnable.run()` ném exception, framework ghi nhận lỗi. Khi caller gọi `get()`:

- Nếu task thành công, trả về kết quả. Với `submit(Runnable)` thường là `null` nếu không cung cấp result riêng.
- Nếu task thất bại bằng exception, `get()` ném **`ExecutionException`**. `getCause()` cho phép xem exception gốc.
- Nếu Future bị hủy, `get()` ném **`CancellationException`**; không xem đây là `ExecutionException` thông thường.
- Nếu **thread đang chờ** bị interrupt, `get()` có thể ném **`InterruptedException`**, bất kể kết quả cuối của task.
- Với `get(timeout, unit)`, một task chưa xong đúng hạn có thể dẫn đến **`TimeoutException`**. Timeout của người đợi không chứng minh task thất bại hay chấm dứt.

Một test tốt không chỉ assert wrapper:

~~~java
// JUnit 5; future nhận từ pool.submit(...).
ExecutionException ex =
        assertThrows(ExecutionException.class, future::get);

Throwable root = ex.getCause();
assertInstanceOf(IllegalArgumentException.class, root);
assertEquals("invalid amount", root.getMessage());
~~~

Kiểm tra `cause` quan trọng vì mọi lỗi task khác nhau có thể đều đến caller dưới cùng một lớp `ExecutionException`. Trong các API có wrapper domain, có thể cần kiểm tra chuỗi cause theo hợp đồng của chính ứng dụng, nhưng không nên bóc các lớp tùy tiện rồi bỏ bối cảnh hữu ích.

## 4. CompletableFuture.join() và get(): hai cách quan sát một lỗi

`CompletableFuture<T>` vừa là `Future<T>`, vừa triển khai `CompletionStage<T>`. Có hai cách chờ kết quả thường dùng:

- `join()` không khai báo checked exception. Với exceptional completion thông thường, nó ném `CompletionException` có `cause` liên quan tới lỗi computation.
- `get()` tuân hợp đồng `Future`, có `ExecutionException` và `InterruptedException` trong chữ ký; có thể bị hủy bằng `CancellationException`.
- Cả hai có thể báo `CancellationException` **trực tiếp** nếu chính CompletableFuture được hủy. Không nên khẳng định mọi lỗi của `join()` đều là `CompletionException`.
- `getNow(defaultValue)` trả giá trị mặc định khi chưa hoàn tất, nhưng **không** phải cơ chế che lỗi: nếu đã hoàn tất bất thường, nó vẫn có thể ném exception dạng completion.

Ví dụ dùng một nguyên nhân được tạo sẵn:

~~~java
// Đoạn minh họa có thể đặt trong một phương thức Java:
IllegalArgumentException original =
        new IllegalArgumentException("bad payload");

CompletableFuture<Integer> failed =
        CompletableFuture.failedFuture(original);

// join(): CompletionException
try {
    failed.join();
} catch (CompletionException ex) {
    assert ex.getCause() == original;
}

// get(): ExecutionException
try {
    failed.get();
} catch (ExecutionException ex) {
    assert ex.getCause() == original;
}
~~~

Đoạn trên cần các import `java.util.concurrent.*` và phương thức bao khai báo `throws InterruptedException`, hoặc tự catch tương ứng; các câu `assert` thuần Java còn phụ thuộc cờ `-ea`. Phần test JUnit 5 ở dưới dùng `assertThrows` và `assertSame` rõ ràng, không phụ thuộc `-ea`.

**Lưu ý:** với lỗi được bọc qua nhiều dependent stage, số lớp `CompletionException` và cách propagation cụ thể có thể khác đường gốc. Ưu tiên kiểm tra `cause`/loại exception được bảo đảm thay vì giả định mọi stage trả cùng object wrapper.

## 5. Chương trình Java 17 chạy độc lập: lỗi Future và CompletableFuture

Lưu thành **`FutureExceptionTestingDemo.java`**:

~~~java
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class FutureExceptionTestingDemo {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> submitted = pool.submit(() -> {
                throw new IllegalArgumentException("invalid amount");
            });

            try {
                submitted.get();
            } catch (ExecutionException ex) {
                System.out.println("Future.get: "
                        + ex.getClass().getSimpleName());
                System.out.println("Future cause: "
                        + ex.getCause().getClass().getSimpleName()
                        + ": " + ex.getCause().getMessage());
            }

            IllegalStateException original =
                    new IllegalStateException("service unavailable");
            CompletableFuture<String> failed =
                    CompletableFuture.failedFuture(original);

            try {
                failed.join();
            } catch (CompletionException ex) {
                System.out.println("CF.join: "
                        + ex.getClass().getSimpleName());
                System.out.println("CF cause: "
                        + ex.getCause().getClass().getSimpleName()
                        + ": " + ex.getCause().getMessage());
            }

            try {
                failed.get();
            } catch (ExecutionException ex) {
                System.out.println("CF.get: "
                        + ex.getClass().getSimpleName());
            }

            AtomicInteger transformCalls = new AtomicInteger();
            CompletableFuture<Integer> dependent =
                    failed.thenApply(value -> {
                        transformCalls.incrementAndGet();
                        return value.length();
                    });

            try {
                dependent.join();
            } catch (CompletionException ex) {
                System.out.println("dependent cause: "
                        + ex.getCause().getClass().getSimpleName());
            }
            System.out.println("transform calls: " + transformCalls.get());

            CompletableFuture<String> recovered =
                    failed.exceptionally(ex -> "fallback");
            System.out.println("recovered: " + recovered.join());
            System.out.println("source still exceptional: "
                    + failed.isCompletedExceptionally());

            CompletableFuture<String> cancelled = new CompletableFuture<>();
            cancelled.cancel(true);
            try {
                cancelled.join();
            } catch (CancellationException ex) {
                System.out.println("cancelled.join: "
                        + ex.getClass().getSimpleName());
            }

            CompletableFuture<String> neverCompleted =
                    new CompletableFuture<>();
            try {
                neverCompleted.get(0, TimeUnit.NANOSECONDS);
            } catch (TimeoutException ex) {
                System.out.println("pending.get: "
                        + ex.getClass().getSimpleName());
            } catch (ExecutionException ex) {
                throw new AssertionError("pending task did not run", ex);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
~~~

Biên dịch và chạy với JDK 17 hoặc mới hơn:

~~~bash
javac FutureExceptionTestingDemo.java
java FutureExceptionTestingDemo
~~~

**Stdout chính xác:**

~~~text
Future.get: ExecutionException
Future cause: IllegalArgumentException: invalid amount
CF.join: CompletionException
CF cause: IllegalStateException: service unavailable
CF.get: ExecutionException
dependent cause: IllegalStateException
transform calls: 0
recovered: fallback
source still exceptional: true
cancelled.join: CancellationException
pending.get: TimeoutException
~~~

### Vì sao kết quả xác định?

1. `submitted.get()` chờ tác vụ trong executor kết thúc thất bại rồi mới in; không cần `Thread.sleep()` để đoán thời điểm lỗi xảy ra.
2. `failedFuture(original)` hoàn tất bất thường **ngay khi được tạo**, không cần worker thực thi. `join()` biểu diễn lỗi thành `CompletionException`, còn `get()` biểu diễn thành `ExecutionException`.
3. `thenApply` chỉ chạy ở nhánh thành công. Vì nguồn đã lỗi, `transformCalls` vẫn là `0`; dependent stage báo exceptional completion.
4. `exceptionally` tạo stage có kết quả dự phòng. `recovered.join()` trả `fallback` nhưng **`failed` vẫn lỗi**.
5. Future `cancelled` được hủy trực tiếp nên `join()` ném `CancellationException` thay vì một kết quả.
6. `neverCompleted` không có producer; `get(0, NANOSECONDS)` kiểm tra trạng thái ngay và ném `TimeoutException`. Test này không phụ thuộc máy nhanh/chậm, nhưng **không chứng minh đã có một operation thực tế chạy quá hạn**.
7. `pool.shutdownNow()` dọn executor. Trong test integration có task không tuân interrupt, cần bổ sung timeout shutdown và kiểm tra rò rỉ tài nguyên.

Chương trình này minh họa contract exception. Nó không benchmark performance và không kiểm thử tác dụng phụ/transaction của một worker thực tế.

## 6. assertThrows đúng tầng để tránh false positives

Một lỗi thường thấy:

~~~java
// SAI về phạm vi kiểm thử (JUnit 5, executor có sẵn).
assertThrows(IllegalStateException.class, () -> {
    pool.submit(() -> {
        throw new IllegalStateException("task failed");
    });
});
~~~

Lời gọi `submit` thường chỉ nộp công việc rồi trả `Future`; exception thực thi không tự nhảy vào stack của `assertThrows`. Nếu lambda bên ngoài tình cờ ném `IllegalStateException` do lỗi khác, test có thể **pass sai nguyên nhân**.

Tách rõ điểm nộp và điểm quan sát:

~~~java
Future<?> future = pool.submit(() -> {
    throw new IllegalStateException("task failed");
});

ExecutionException ex =
        assertThrows(ExecutionException.class, future::get);
assertInstanceOf(IllegalStateException.class, ex.getCause());
assertEquals("task failed", ex.getCause().getMessage());
~~~

Tương tự với CompletableFuture:

~~~java
CompletableFuture<String> stage =
        CompletableFuture.failedFuture(
                new IllegalArgumentException("invalid id"));

CompletionException ex =
        assertThrows(CompletionException.class, stage::join);
assertInstanceOf(IllegalArgumentException.class, ex.getCause());
~~~

Khi code trả CompletableFuture đã lỗi ngay từ validation, test vẫn phải gọi `join()` hoặc kiểm tra stage để thấy **exceptional completion**. Nó không nên mặc định kỳ vọng method tạo future ném lỗi đồng bộ, trừ khi **hợp đồng API** công bố fail-fast bằng throw.

## 7. Bộ kiểm thử JUnit 5 hoàn chỉnh: những hợp đồng quan trọng

Đoạn dưới đây là **một lớp test JUnit 5** cần có `junit-jupiter` và JUnit test engine trong dự án; JDK 17 **không** tự kèm JUnit. Lưu tại đường dẫn test tương ứng, tên file **`AsyncExceptionContractTest.java`**. Ví dụ tránh sử dụng thread sleep và kiểm tra cả wrapper lẫn cause.

~~~java
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(5)
class AsyncExceptionContractTest {
    private final ExecutorService pool =
            Executors.newSingleThreadExecutor();

    @AfterEach
    void cleanup() {
        pool.shutdownNow();
    }

    @Test
    void submitWrapsTaskFailureWithExecutionException() {
        Future<Integer> future = pool.submit((Callable<Integer>) () -> {
            throw new IllegalArgumentException("bad row");
        });

        ExecutionException ex =
                assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        assertEquals("bad row", ex.getCause().getMessage());
    }

    @Test
    void completableFutureJoinAndGetExposeDifferentWrappers() {
        IllegalStateException cause =
                new IllegalStateException("repository down");
        CompletableFuture<String> failed =
                CompletableFuture.failedFuture(cause);

        CompletionException fromJoin =
                assertThrows(CompletionException.class, failed::join);
        ExecutionException fromGet =
                assertThrows(ExecutionException.class, failed::get);

        assertSame(cause, fromJoin.getCause());
        assertSame(cause, fromGet.getCause());
    }

    @Test
    void failedSourceSkipsSuccessTransform() {
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<String> source = new CompletableFuture<>();
        CompletableFuture<Integer> derived =
                source.thenApply(value -> {
                    calls.incrementAndGet();
                    return value.length();
                });

        IllegalArgumentException cause =
                new IllegalArgumentException("invalid request");
        assertTrue(source.completeExceptionally(cause));

        CompletionException ex =
                assertThrows(CompletionException.class, derived::join);
        assertSame(cause, ex.getCause());
        assertEquals(0, calls.get());
    }

    @Test
    void exceptionallyRecoversOnlyDependentStage() {
        CompletableFuture<String> source =
                CompletableFuture.failedFuture(
                        new IllegalStateException("offline"));

        CompletableFuture<String> recovered =
                source.exceptionally(ex -> "fallback");

        assertEquals("fallback", recovered.join());
        assertTrue(source.isCompletedExceptionally());
        assertFalse(recovered.isCompletedExceptionally());
    }

    @Test
    void cancelledSourceThrowsCancellationDirectly() {
        CompletableFuture<String> source = new CompletableFuture<>();
        CompletableFuture<Integer> derived =
                source.thenApply(String::length);

        assertTrue(source.cancel(true));
        assertTrue(source.isCancelled());
        assertTrue(source.isCompletedExceptionally());

        assertThrows(CancellationException.class, source::join);
        CompletionException ex =
                assertThrows(CompletionException.class, derived::join);
        assertInstanceOf(CancellationException.class, ex.getCause());
    }

    @Test
    void pendingGetTimesOutWithoutFailureCompletion() {
        CompletableFuture<String> pending = new CompletableFuture<>();

        assertThrows(TimeoutException.class,
                () -> pending.get(0, TimeUnit.NANOSECONDS));
        assertFalse(pending.isDone());

        assertTrue(pending.complete("ready"));
        assertEquals("ready", pending.join());
    }

    @Test
    void workerHasStartedBeforeItsFailureIsReleased()
            throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<?> future = pool.submit(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted", ex);
                }
                throw new IllegalStateException("expected failure");
            });

            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertFalse(future.isDone());

            release.countDown();
            ExecutionException ex =
                    assertThrows(ExecutionException.class, future::get);
            assertInstanceOf(IllegalStateException.class, ex.getCause());
            assertEquals("expected failure",
                    ex.getCause().getMessage());
        } finally {
            release.countDown();
        }
    }
}
~~~

### Điều đáng học từ bộ test

- `assertThrows` đặt tại lời gọi `future.get()` hoặc `stage.join()`, không phải tại `submit` hoặc `supplyAsync`.
- Test `join` và `get` dùng **cùng một cause** để phân biệt wrapper mà không cần giả định kết quả scheduler.
- Test dependent stage xác nhận **callback nhánh thành công không chạy** và nguyên nhân gốc được giữ.
- Test `exceptionally` xác nhận stage **mới** thành công nhưng stage nguồn vẫn lỗi; tránh hiểu nhầm recovery làm biến đổi quá khứ.
- Test cancellation phân biệt `CancellationException` trực tiếp từ nguồn với ngoại lệ được truyền qua derived stage.
- Test timeout dùng pending future chưa có producer; assert `isDone()==false` để chứng minh timeout chờ **không hoàn tất/cancel future**.
- Test có `CountDownLatch` điều phối trạng thái để biết worker đã chạy nhưng chưa được cho phép ném; không cần `Thread.sleep()`. `@Timeout(5)` làm test không chờ vô hạn trong điều kiện bất thường.
- `@AfterEach` dừng executor. Với test thật, có thể cần chờ termination có hạn hoặc dùng teardown thích hợp để đảm bảo tài nguyên không rò rỉ.

Một lỗi trong test `CountDownLatch` có thể tạo nhánh `IllegalStateException("interrupted", cause)`; test kỳ vọng `"expected failure"` để phát hiện nếu worker đã bị ngắt trước khi được release. Đây là lý do việc chỉ assert loại exception ở mọi đường có thể chưa đủ.

## 8. Kiểm thử checked exception của Callable

`Callable<V>.call()` khai báo `throws Exception`; vì vậy một tác vụ có thể ném `IOException` mà không cần bọc thành unchecked tại nơi thực thi:

~~~java
// JUnit 5; pool đã khởi tạo.
Future<String> future = pool.submit(() -> {
    throw new java.io.IOException("file unavailable");
});

ExecutionException ex =
        assertThrows(ExecutionException.class, future::get);
assertInstanceOf(java.io.IOException.class, ex.getCause());
assertEquals("file unavailable", ex.getCause().getMessage());
~~~

Điều cần kiểm tra không phải “`Callable` ném checked exception xuyên luồng”. Thực chất `submit` lưu `Throwable` do `call()` phát sinh; `get()` ở caller ném một `ExecutionException` có cause là `IOException`.

Với `Runnable.run()`, chữ ký không khai báo checked exception; code trong Runnable phải xử lý hoặc bọc lỗi checked trước khi cho task kết thúc bất thường. Test cần phân loại đúng wrapper mà ứng dụng đã tạo.

## 9. Kiểm thử exception của CompletableFuture chain

Một chain có nhiều điểm có thể thất bại:

~~~java
CompletableFuture<Integer> result =
        CompletableFuture.completedFuture("12")
                .thenApply(Integer::parseInt)
                .thenApply(n -> n / 0);
~~~

Đoạn này **không chạy tới stage cuối** nếu stage parse lỗi; ở ví dụ cụ thể `"12"` parse thành công, rồi phép chia cho zero trong stage tiếp theo tạo `ArithmeticException`. Test nên kiểm tra đúng stage/cause:

~~~java
CompletionException ex =
        assertThrows(CompletionException.class, result::join);
assertInstanceOf(ArithmeticException.class, ex.getCause());
~~~

Những câu hỏi quan trọng:

1. Khi stage trước thất bại, stage sau chỉ chạy ở nhánh thành công có bị bỏ qua không?
2. `thenApply`/`thenCompose` có thêm wrapper hay đổi nguyên nhân gốc theo hợp đồng không?
3. `exceptionally` có làm kết quả thành công giả ngoài ý định không?
4. `handle` có trả fallback đúng và có làm lộ `null` không?
5. `whenComplete` được dùng để **quan sát** hay tác giả tưởng nó sẽ phục hồi? Nếu nguồn lỗi, stage tạo từ `whenComplete` thường vẫn lỗi.
6. Nếu callback phục hồi tự ném lỗi mới, test cần xác nhận stage phụ thuộc phản ánh **lỗi callback**.
7. Nếu một stage bị cancel, có consumer nào vô tình khôi phục cancellation thành kết quả thành công không?

Không nên chỉ test `source.isCompletedExceptionally()`; điều đó không nói được caller nhận được gì sau composition. Hãy kiểm tra **stage cuối được công bố trong API** và những stage quan trọng ở giữa khi chúng có hợp đồng riêng.

## 10. Kiểm thử failedFuture, completeExceptionally và cạnh tranh hoàn tất

`CompletableFuture.failedFuture(cause)` tạo một future hoàn tất bất thường ngay, tiện cho test logic của consumer mà không cần thread thực:

~~~java
CompletableFuture<String> failed =
        CompletableFuture.failedFuture(
                new IllegalArgumentException("bad id"));
~~~

`completeExceptionally(cause)` dùng cho future **đang tồn tại**. Nó trả boolean thể hiện lần gọi có hoàn tất được future đang pending hay không. Test nên xác minh hoàn tất lần đầu thắng:

~~~java
CompletableFuture<String> promise = new CompletableFuture<>();
IllegalArgumentException cause =
        new IllegalArgumentException("bad input");

assertTrue(promise.completeExceptionally(cause));
assertFalse(promise.complete("late success"));
assertTrue(promise.isCompletedExceptionally());

CompletionException ex =
        assertThrows(CompletionException.class, promise::join);
assertSame(cause, ex.getCause());
~~~

Đừng gọi `completeExceptionally` hai lần và mong lần sau ghi đè lần trước. Trong cạnh tranh giữa `complete`, `completeExceptionally` và `cancel`, trạng thái thắng phụ thuộc thứ tự thực thi. Nếu muốn test race, dùng `CountDownLatch`/`CyclicBarrier` hoặc một executor có kiểm soát, sau đó assert **một kết quả hợp lệ trong tập được phép**, thay vì hard-code luồng nào thắng.

Lưu ý `CompletableFuture.cancel(boolean)` có ngữ nghĩa trả boolean riêng: một future đã canceled có thể tiếp tục trả `true` khi gọi `cancel` lần nữa. Đừng áp dụng kết luận từ `completeExceptionally` cho `cancel` mà không đọc hợp đồng của lớp.

## 11. CancellationException không chứng minh worker đã dừng

`Future.cancel(true)` và `CompletableFuture.cancel(true)` có đặc điểm khác nhau:

- Với `FutureTask` thường được trả từ `ExecutorService.submit`, `cancel(true)` có thể **yêu cầu interrupt** worker đang chạy. Task vẫn phải hợp tác mới dừng.
- Với `CompletableFuture`, tham số `mayInterruptIfRunning` **không dùng để điều khiển computation**; chỉ canceled future không chứng minh supplier bị interrupt.
- `CancellationException` từ `get()`/`join()` báo trạng thái completion bị hủy, **không phải bằng chứng transaction rollback**.
- Nếu có side effect trước khi cancel, tác dụng phụ có thể vẫn tồn tại.

Trong test, cần tách:

1. **Trạng thái future:** `isCancelled()`, `isDone()`, `isCompletedExceptionally()`, exception khi lấy kết quả.
2. **Trạng thái worker:** đã nhận tín hiệu hủy chưa, có kiểm tra flag/token không, có kết thúc không?
3. **Trạng thái nghiệp vụ:** dữ liệu có giữ invariant không, đã commit hay chưa, retry có an toàn không?

Một test chỉ `assertThrows(CancellationException.class, future::join)` không chứng minh được hai mục sau. Nếu yêu cầu hủy thực sự của API, test phải quan sát task qua latch, token hoặc trạng thái được đồng bộ hóa phù hợp.

## 12. TimeoutException và phép đo thời gian

Có hai mục tiêu test khác nhau:

### Test hợp đồng timeout của get

Tạo một future chưa complete và gọi `get(0, TimeUnit.NANOSECONDS)`; ta kiểm tra `TimeoutException` khi chưa có kết quả. Đây là cách **không phụ thuộc scheduler** để kiểm tra hợp đồng chờ.

### Test deadline thật của một service

Nếu cần xác minh hệ thống timeout sau một hạn định, test phải kiểm soát thời gian bằng clock abstraction/scheduler thử nghiệm khi framework cho phép, hoặc dùng timeout ngắn có giới hạn và chấp nhận điều kiện concurrency/integration. Không assert độ chính xác ở mức mili giây chỉ bằng `System.currentTimeMillis()` vì scheduler, GC và tải máy có thể gây sai lệch.

**Rất quan trọng:** `TimeoutException` từ `Future.get(timeout)` **không tự làm Future hoàn tất bất thường**. Nó chỉ cho biết người gọi đã chờ quá lâu. Ngược lại, `CompletableFuture.orTimeout(...)` có thể hoàn tất chính future bằng `TimeoutException` khi quá hạn; test hai API phải khác nhau.

Để kiểm thử `orTimeout` mà không dùng thời gian thực, có thể thiết kế wrapper nhận scheduler/clock có thể fake; `CompletableFuture.orTimeout` của JDK không cung cấp clock injection trực tiếp. Với integration test dùng đồng hồ thực, luôn có timeout test tổng thể và cleanup rõ ràng.

## 13. Interrupt: không nhầm lỗi của thread đang chờ với lỗi task

`InterruptedException` do `Future.get()` được ném tại **thread gọi get** khi thread đó bị ngắt trong lúc chờ. Nó không phải exception gốc của task.

Một test có thể tạo một thread chỉ làm việc chờ:

~~~java
// Trích đoạn Java; waitingFuture là CompletableFuture chưa hoàn tất.
Thread waiter = new Thread(() -> {
    try {
        Thread.currentThread().interrupt();
        waitingFuture.get();
        throw new AssertionError("expected interruption");
    } catch (InterruptedException expected) {
        Thread.currentThread().interrupt();
        // Ghi nhận bằng AtomicBoolean/latch nếu test cần quan sát.
    } catch (ExecutionException unexpected) {
        throw new AssertionError(unexpected);
    }
});
waiter.start();
waiter.join();
~~~

Ví dụ này chủ động đặt interrupt flag cho **chính waiter** trước `get()` và giả định future chưa hoàn tất. Mã chỉ là trích đoạn; test đầy đủ cần assert kênh quan sát và không để exception trong thread con bị bỏ sót.

Khi bắt `InterruptedException` và không thể khai báo `throws` tiếp, thường nên `Thread.currentThread().interrupt()` để phục hồi cờ rồi thoát theo chính sách hủy. Nhưng nếu test cần chứng minh cờ được xóa khi thao tác chặn ném lỗi, phải đo **trước** khi reinterrupt; đừng nhầm giá trị cờ sau khối catch với hành vi nguyên thủy của API.

Test cancellation cũng cần phân biệt `CancellationException` (future bị hủy) và `InterruptedException` (thread chờ bị ngắt). Chúng không thay thế nhau.

## 14. Tránh Thread.sleep để điều phối tác vụ trong test

Một test thiếu ổn định thường viết:

~~~java
// Phản ví dụ; độ tin cậy phụ thuộc tải máy.
Future<?> future = pool.submit(this::doWork);
Thread.sleep(50);
assertTrue(future.isDone());
~~~

50 ms không cam kết worker đã được scheduler chạy hoặc I/O đã xong. Trên máy CI quá tải, test fail ngẫu nhiên. Ngay cả việc tăng lên 500 ms chỉ làm test chậm hơn, không cung cấp một **quan hệ đồng bộ hóa**.

Thay bằng các primitive có ý nghĩa:

- `Future.get()`: đợi completion và nhận failure.
- `CountDownLatch`: báo “đã đến điểm X”, “được phép tiếp tục”.
- `CyclicBarrier` hoặc `Phaser`: điều phối nhiều participant khi thực sự cần.
- `CompletableFuture`: tạo nguồn hoàn tất có kiểm soát bằng `complete`/`completeExceptionally`.
- `AtomicReference<Throwable>`/`AtomicBoolean`: lưu quan sát từ thread khác khi có memory visibility thích hợp.
- `await(timeout, unit)` hoặc test-level timeout: bảo vệ test khỏi treo mãi nếu có bug.

Ví dụ đồng bộ hóa:

~~~java
// Trích đoạn; pool đã khởi tạo.
CountDownLatch started = new CountDownLatch(1);
CountDownLatch release = new CountDownLatch(1);

try {
    Future<?> task = pool.submit(() -> {
        started.countDown();
        try {
            release.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("cancelled", ex);
        }
        throw new IllegalArgumentException("bad data");
    });

    if (!started.await(2, TimeUnit.SECONDS)) {
        throw new AssertionError("worker did not start");
    }
    release.countDown();

    ExecutionException ex =
            assertThrows(ExecutionException.class, task::get);
    assertInstanceOf(IllegalArgumentException.class, ex.getCause());
} finally {
    release.countDown();
}
~~~

Đoạn này cần import các kiểu concurrent và JUnit 5. Việc `release.countDown()` trong `finally` tránh để worker kẹt khi test dừng sớm. Đây là **đồng bộ theo sự kiện** thay vì đoán thời gian.

## 15. Test một worker mà không đọc kết quả của Future có thể bỏ sót lỗi

`ExecutorService.submit()` ghi nhận exception vào `Future`. Nếu test nộp task rồi kết thúc khi chưa `get()` hoặc chưa có kênh quan sát failure, nó có thể **pass giả** dù worker đã ném exception.

~~~java
// Không đủ nếu mục tiêu là xác minh exception của task:
pool.submit(() -> {
    throw new IllegalStateException("unexpected failure");
});
// Test có thể kết thúc ở đây mà không assert gì về failure.
~~~

Các cách đúng:

- Giữ Future và gọi `get()`, assert `ExecutionException.getCause()`.
- Nếu API trả CompletableFuture, attach handler hoặc `join()` stage mà caller được công bố.
- Với fire-and-forget, thiết kế error sink/uncaught handler hoặc callback giám sát và test chính kênh đó.
- Với `Thread` trần, cài `UncaughtExceptionHandler` ghi exception vào `AtomicReference`; `Thread.join()` chỉ chờ thread kết thúc, **không ném lại exception**.

Đừng lấy stderr mặc định hay log không được capture làm bằng chứng tự động rằng test đã quan sát thất bại. Một test tự động phải assert kênh có thể đọc/kiểm soát.

## 16. Test exceptional completion không tự chứng minh rollback trạng thái

Nếu task cập nhật một phần dữ liệu rồi ném lỗi, Future báo thất bại chính xác nhưng dữ liệu có thể hỏng:

~~~java
// Mã minh họa về rủi ro, không phải chương trình độc lập.
Future<?> future = pool.submit(() -> {
    accountA.debit(amount);
    failingOperation();
    accountB.credit(amount);
});
~~~

Một test chỉ assert `ExecutionException` mới kiểm tra **kênh exception**, chưa kiểm tra invariant “tổng số dư không đổi”. Với nghiệp vụ cần atomicity, test nên:

1. Chuẩn bị trạng thái ban đầu rõ ràng.
2. Dùng fault injection khiến bước nhạy cảm ném lỗi.
3. Chờ task hoàn tất bằng `get()`/`join()`.
4. Kiểm tra ngoại lệ/cause đúng.
5. Đọc trạng thái qua kênh đồng bộ hóa đúng và assert invariant hoặc strong exception guarantee theo contract.
6. Xác minh side effect ngoài JVM, retry/idempotency khi tương tác DB/network.

Lỗi bất đồng bộ được truyền tốt không tự cho biết thao tác đã rollback. Một `CompletableFuture.cancel(true)` hoặc `orTimeout(...)` cũng không tự đảo ngược side effect. Vì vậy kiểm thử thông báo thất bại và kiểm thử tính nhất quán dữ liệu phải là **hai nhóm assertion độc lập**.

## 17. Những nhầm lẫn thường gặp

1. **`assertThrows` bao `submit` thay vì `get()`:** lỗi task được chuyển qua Future.
2. **Mong `Thread.join()` ném lại lỗi của `run()`:** join chỉ chờ thread kết thúc.
3. **Chỉ assert `ExecutionException`:** quên kiểm tra cause và message/code nghiệp vụ.
4. **Coi `CompletionException` và `ExecutionException` là một:** chúng xuất hiện ở API quan sát khác nhau.
5. **Coi mọi `join()` thất bại là `CompletionException`:** cancellation trực tiếp có thể ném `CancellationException`.
6. **Assert exception bằng regex vào `getMessage()`:** message không phải machine-readable contract; nên assert type/code/cause và chỉ assert message khi nó là yêu cầu của test.
7. **Dùng `Thread.sleep` để đợi worker:** dễ flaky khi chạy CI hoặc scheduler bận.
8. **Thử timeout bằng cách sleep đúng bằng deadline:** race và jitter có thể làm test không ổn định.
9. **Bỏ Future sau khi `submit`:** lỗi task có thể không được test nhìn thấy.
10. **Dùng `exceptionally` rồi tưởng nguồn hết lỗi:** recovery tạo stage phụ thuộc mới.
11. **Dùng `whenComplete` như luôn phục hồi lỗi:** nó chủ yếu quan sát; lỗi nguồn thường vẫn truyền.
12. **Test cancel rồi suy ra thread đã dừng:** `CompletableFuture.cancel(true)` không interrupt supplier theo hợp đồng.
13. **Nuốt `InterruptedException` trong test worker:** che tín hiệu hủy và làm tearDown không đáng tin.
14. **Quên shutdown executor:** test leak thread hoặc làm bộ kiểm thử treo sau khi assertions hoàn tất.
15. **Chỉ assert exception, không assert trạng thái:** thiếu kiểm tra rollback/invariant trong code có side effect.
16. **Kiểm tra stage chưa hoàn tất bằng `get()` không timeout:** test có thể chờ vô hạn.
17. **Assert đúng cause ở một stage nhưng không assert stage public cuối:** caller thực có thể nhận wrapper/fallback khác.
18. **Dùng một test pass làm bằng chứng không có race:** concurrency correctness cần lập luận về synchronization và nhiều kịch bản.

## 18. Checklist xây test đáng tin

Trước khi hoàn thành test exception bất đồng bộ, hãy tự trả lời:

1. **API trả cái gì?** `Future`, `CompletableFuture`, `CompletionStage`, `Thread` hay callback?
2. **Lỗi phát sinh ở stack nào?** Nếu trong worker, caller nhận qua API completion chứ không bắt ở `submit`.
3. **Quan sát lỗi ở đâu?** `get()`, `join()`, handler, callback hay error sink.
4. **Wrapper dự kiến là gì?** `ExecutionException`, `CompletionException`, `CancellationException`, `InterruptedException` hay `TimeoutException`?
5. **Cause nào quan trọng?** Type, code, message trong hợp đồng, business ID, suppressed cause khi cần.
6. **Có stage phụ thuộc không?** Kiểm tra callback thành công không chạy sau lỗi và recovery đúng phạm vi.
7. **Có test trạng thái khi chưa hoàn tất không?** Không chờ vô hạn; timeout có hạn hoặc deterministic.
8. **Worker đã chắc chắn bắt đầu chưa?** Dùng latch/barrier thay vì sleep đoán.
9. **Cancellation có dừng công việc thật không?** Phân biệt completion state với control signal và side effect.
10. **Tài nguyên có được dọn không?** Executor, latch, file, network và dữ liệu được reset trong `finally`/teardown.
11. **Test có bỏ sót failure không?** Future bị bỏ, callback không quan sát hoặc thread exception chỉ in stderr.
12. **Sau khi lỗi, invariant dữ liệu vẫn đúng không?** Kiểm tra riêng theo contract nghiệp vụ.

## 19. Tổng kết

**Kiểm thử ngoại lệ qua Future và CompletableFuture** là kiểm thử **kênh kết quả của tác vụ**, không chỉ kiểm thử câu lệnh tạo/khởi chạy tác vụ. Với `Future.get()`, hãy bắt `ExecutionException` và kiểm tra `getCause()`. Với `CompletableFuture.join()`, hãy bắt `CompletionException` cho exceptional completion thông thường và kiểm tra cause; phân biệt cancellation trực tiếp bằng `CancellationException`. `CompletableFuture.get()` vẫn theo hợp đồng `Future`.

Hãy kiểm thử thêm dependent stages, fallback, cancellation, timeout, interrupt, dữ liệu sau thất bại và lifecycle của executor. Dùng `CountDownLatch`/`Future.get()` thay cho `Thread.sleep()` để kiểm soát thứ tự và tránh flaky tests. Cuối cùng, một test chỉ có giá trị khi nó thực sự **quan sát ngoại lệ từ đúng kênh** và kiểm chứng **loại lỗi, nguyên nhân và trạng thái** mà caller nhận được.

### Tham khảo chuẩn Java và JUnit

- [Java SE 17 — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html)
- [Java SE 17 — CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [Java SE 17 — CompletionStage](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletionStage.html)
- [Java SE 17 — ExecutionException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutionException.html)
- [Java SE 17 — CompletionException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletionException.html)
- [Java SE 17 — CancellationException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CancellationException.html)
- [Java SE 17 — CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html)
- [JUnit 5 — Assertions.assertThrows](https://junit.org/junit5/docs/current/user-guide/#writing-tests-assertions)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-testing-009">Kiểm thử ngắt bằng phối hợp luồng xác định thay vì chỉ sleep</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử ngắt bằng phối hợp luồng xác định thay vì chỉ `sleep`

Kiểm thử `Thread.interrupt()`, `InterruptedException` và hủy tác vụ trong Java dễ trở nên **flaky** nếu test khởi tạo worker, gọi `Thread.sleep(100)` rồi đoán rằng worker đã kịp chạy đến đúng vị trí. Trên máy phát triển, 100 ms có vẻ đủ; trên CI đang tải cao, scheduler có thể chưa cấp CPU cho worker. Ngược lại, trên máy nhanh, worker có thể hoàn thành trước khi test gửi interrupt.

Để kiểm thử đáng tin, hãy **phối hợp luồng bằng những tín hiệu có ngữ nghĩa rõ ràng**, thường là `CountDownLatch`, `BlockingQueue`, `Future`, `Semaphore` hoặc đồng bộ tương đương. Mục tiêu không phải ép hai thread chạy ở một thứ tự ngẫu nhiên được quan sát, mà là tạo quan hệ **“bước này xảy ra trước bước kia”** và khẳng định **kết quả theo hợp đồng**.

Bài thuộc **Java Core – Exception / Kiểm thử**, dùng Java 17+. Nội dung gồm ví dụ Java độc lập chạy bằng `javac`/`java`, ví dụ JUnit 5 có dependency riêng, tình huống `InterruptedException` xóa cờ interrupt, phân biệt ngắt caller và worker, và những cách tránh test treo hoặc sai logic.

## 1. Vì sao `Thread.sleep(...)` không phải cơ chế đồng bộ kiểm thử?

Mẫu test dễ gặp:

```java
Thread worker = new Thread(() -> {
    doSomeWork();
    waitForNextItem();
});

worker.start();
Thread.sleep(100);
worker.interrupt();
```

Đây là **mảnh mã minh họa**, không chạy độc lập vì `doSomeWork()` và `waitForNextItem()` chưa được định nghĩa. `sleep(100)` chỉ yêu cầu **thread đang gọi nó** nghỉ ít nhất xấp xỉ khoảng thời gian đó, không tạo cam kết rằng **worker** đã chạy, đã xong khởi tạo hoặc đang chờ ở thao tác muốn kiểm thử.

Các nguồn không xác định:

- Hệ điều hành và JVM tự lập lịch thread; `start()` không đợi `run()` đi tới một dòng cụ thể.
- CPU/CI có thể đang bận, máy ảo có thể tạm ngừng hoặc bị nhiễu bởi GC.
- Worker có thể kết thúc quá sớm, khiến `interrupt()` chỉ tác động lên thread không còn chạy.
- Worker có thể mới bắt đầu, chưa vào thao tác chờ, nên assertion kiểm tra trạng thái `WAITING` sẽ không đáng tin.
- `sleep` kéo dài suite mà không chứng minh thêm điều kiện logic.
- Tăng từ 100 ms lên 2 giây làm giảm xác suất race trong một vài môi trường, **không loại bỏ** race.

Cần phân biệt hai ý:

1. **Dùng sleep để đoán trạng thái** là anti-pattern của test đồng thời.
2. **Dùng timeout để giới hạn thời gian chờ** là kỹ thuật an toàn cần thiết. `latch.await(2, TimeUnit.SECONDS)` không dùng 2 giây để *giả định* worker đã sẵn sàng; nó chờ một **tín hiệu cụ thể** và thất bại rõ ràng nếu tín hiệu không đến.

Không phải `Thread.sleep` luôn sai. Nó phù hợp nếu **hành vi cần kiểm tra chính là ngủ hoặc xử lý thời gian**. Nhưng nó không nên là cách xác lập thứ tự giữa các thread trong phần lớn test interrupt.

## 2. Thiết kế test theo các sự kiện có thể quan sát

Một test interrupt tốt thường có các pha:

```text
                    Thread test                 Thread worker
                       |                             |
                 tạo latch và worker                 |
                       |                             |
                  worker.start() ------------------> run()
                       |                             |
                  ready.await()  <---------------- ready.countDown()
                       |                             |
                worker.interrupt() -----------------> tín hiệu ngắt
                       |                             |
                  done.await() <------------------- done.countDown()
                       |                             |
                kiểm tra trạng thái / kết quả
```

Các tín hiệu quan trọng:

- **`ready`**: worker đã đi đến một mốc xác định do test đặt.
- **`interrupt()`**: test gửi yêu cầu ngắt đến đúng đối tượng `Thread`.
- **`done`** hoặc `join()`**:** worker đã thực hiện phần kết thúc cần quan sát.
- **Assertion**: kiểm tra một kết quả có ngữ nghĩa, ví dụ worker bắt `InterruptedException`, cờ interrupt được khôi phục, không còn chạy, hoặc `Future` báo canceled.

Một `CountDownLatch` bắt đầu ở 1:

```java
CountDownLatch ready = new CountDownLatch(1);

// Trong worker:
ready.countDown();

// Trong test:
boolean signaled = ready.await(2, TimeUnit.SECONDS);
```

Đây là các **mảnh mã minh họa**. `countDown()` chuyển count về 0; những caller đang `await()` có thể tiếp tục. Trong một luồng thực thi đúng, các hành động trước `countDown()` **happen-before** những hành động sau `await()` trả về thành công, theo quy tắc memory consistency của `CountDownLatch`.

**Giới hạn cần hiểu:** nếu worker gọi `ready.countDown()` **ngay trước** `queue.take()`, khi test nhận `ready` nó chỉ biết worker đã đến mốc này; **không thể khẳng định worker đang nằm trong `queue.take()` đúng tại thời điểm test gọi `interrupt()`**. Tín hiệu có thể đến trước lúc worker bước vào `take()`. Nhưng đó không nhất thiết là vấn đề: API `take()` phải tôn trọng trạng thái interrupt **đã đặt sẵn hoặc được đặt khi đang chờ**, và test có thể kiểm tra cùng hợp đồng xử lý interrupt trong cả hai thứ tự.

Nếu muốn kiểm thử chính xác một trạng thái blocking nội bộ hiếm gặp, cần công cụ đồng bộ hoặc hook thiết kế riêng cho mục tiêu đó. Không dùng `Thread.State` được lấy một lần hoặc một khoảng `sleep` như bằng chứng tuyệt đối rằng worker đang ở đúng instruction.

## 3. Ví dụ Java 17: kiểm thử `InterruptedException` trên `CountDownLatch.await()`

Tạo tệp `InterruptLatchTest.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class InterruptLatchTest {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch neverOpen = new CountDownLatch(1);

        AtomicBoolean caught = new AtomicBoolean(false);
        AtomicBoolean flagCleared = new AtomicBoolean(false);
        AtomicBoolean flagRestored = new AtomicBoolean(false);

        Thread worker = new Thread(() -> {
            ready.countDown();

            try {
                neverOpen.await();
            } catch (InterruptedException e) {
                caught.set(true);
                flagCleared.set(
                        !Thread.currentThread().isInterrupted());

                Thread.currentThread().interrupt();
                flagRestored.set(
                        Thread.currentThread().isInterrupted());
            }
        }, "interrupt-worker");

        worker.start();

        if (!ready.await(2, TimeUnit.SECONDS)) {
            worker.interrupt();
            throw new AssertionError("Worker not ready");
        }

        worker.interrupt();
        worker.join(2_000);

        if (worker.isAlive()) {
            worker.interrupt();
            throw new AssertionError("Worker did not finish");
        }

        if (!caught.get()
                || !flagCleared.get()
                || !flagRestored.get()) {
            throw new AssertionError("Interrupt contract violated");
        }

        System.out.println("caught = " + caught.get());
        System.out.println("cleared in catch = "
                + flagCleared.get());
        System.out.println("restored = " + flagRestored.get());
        System.out.println("worker alive = " + worker.isAlive());
    }
}
```

Lệnh chạy:

```bash
javac InterruptLatchTest.java
java InterruptLatchTest
```

Kết quả khi test thành công:

```text
caught = true
cleared in catch = true
restored = true
worker alive = false
```

Tại sao không cần ngủ?

1. Test đợi `ready` thay vì phỏng đoán thời điểm worker sẵn sàng.
2. Worker chờ một latch không có ai `countDown`, nên không thể đi qua `await()` bằng đường bình thường.
3. `interrupt()` có thể được gọi khi worker đã chờ **hoặc ngay trước lúc gọi** `await()`. Trong cả hai trường hợp, `CountDownLatch.await()` phản ứng với interrupt.
4. Khi `await()` ném `InterruptedException`, cờ interrupt của worker bị **xóa** theo hợp đồng thao tác chờ; vì vậy `flagCleared=true` trong nhánh `catch`.
5. Worker gọi `Thread.currentThread().interrupt()` để **khôi phục cờ** trước khi kết thúc; `flagRestored=true`.
6. `join(2000)` được dùng **làm giới hạn an toàn**, còn `isAlive()` và các biến trạng thái xác minh điều kiện mong muốn. Nếu worker vẫn chạy, test ném `AssertionError` chứ không báo qua loa là đạt.

`AtomicBoolean` được dùng để chia sẻ trạng thái an toàn giữa các thread. `join()` còn cung cấp quan hệ đồng bộ khi worker đã chấm dứt, nhưng các atomic flag giúp ý định kiểm thử rõ ràng và tránh truy cập dữ liệu chia sẻ không đồng bộ.

Trong kiểm thử thực, chọn timeout phù hợp với mức tải CI. Quá ngắn gây failure giả do môi trường; quá dài làm chẩn đoán test treo chậm. **Timeout là rào an toàn, không phải cách điều khiển thứ tự.**

## 4. Dùng `BlockingQueue.take()` để kiểm thử worker chờ việc

`BlockingQueue.take()` là điểm chờ điển hình ở consumer thread. Khi queue rỗng, worker đợi phần tử; khi bị interrupt, `take()` có thể ném `InterruptedException`.

Tệp `InterruptQueueTest.java`:

```java
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class InterruptQueueTest {
    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch cleaned = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean(false);

        Thread consumer = new Thread(() -> {
            ready.countDown();
            try {
                String item = queue.take();
                throw new AssertionError("Unexpected item: " + item);
            } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            } finally {
                cleaned.countDown();
            }
        }, "queue-consumer");

        consumer.start();

        try {
            if (!ready.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("Consumer not ready");
            }

            consumer.interrupt();

            if (!cleaned.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("Cleanup not observed");
            }

            consumer.join(2_000);
            if (consumer.isAlive() || !interrupted.get()) {
                throw new AssertionError("Consumer did not stop correctly");
            }

            System.out.println("interrupt observed = true");
            System.out.println("cleanup observed = true");
            System.out.println("consumer alive = false");
        } finally {
            consumer.interrupt();
        }
    }
}
```

Chạy:

```bash
javac InterruptQueueTest.java
java InterruptQueueTest
```

Kết quả khi thành công:

```text
interrupt observed = true
cleanup observed = true
consumer alive = false
```

Queue không được `put` bất kỳ phần tử nào; worker vì vậy không thể hoàn thành `take()` theo đường đọc dữ liệu. Test gửi interrupt và yêu cầu worker báo `cleaned` trong `finally`.

**Lưu ý về thứ tự:** test không cần phân biệt tín hiệu interrupt đến ngay trước hay trong khi `take()` chờ. Cả hai đường đều thuộc hợp đồng cần hỗ trợ. Nếu cố gắng lấy trạng thái `WAITING` bằng polling rồi mới interrupt, test còn phụ thuộc JVM implementation và có thêm race.

Trong mã production, vòng consumer nên truyền `InterruptedException` lên hoặc khôi phục cờ và **thoát**. Nếu bắt xong lặp lại `take()` trong một vòng `while(true)` mà không đổi chính sách, cờ được khôi phục có thể làm nó ném ngay liên tiếp, gây vòng lặp bận.

## 5. Không nhầm `Thread.interrupt()` với việc tác vụ đã kết thúc

Một bài test yếu chỉ làm:

```java
worker.interrupt();
assertTrue(worker.isInterrupted());
```

Đây là mảnh mã JUnit minh họa. Kiểm tra một cờ tại một thời điểm **không chứng minh worker đã nhận/giải quyết tín hiệu**, cũng không chứng minh nó đã kết thúc. Một thao tác chờ có thể xóa cờ khi ném `InterruptedException`, và worker có thể khôi phục cờ hoặc không. Test đọc `isInterrupted()` bên ngoài rất dễ thấy `false` dù xử lý interrupt hoàn toàn đúng.

Nên quyết định hợp đồng rõ:

- Nếu kiểm tra **tín hiệu đã được xử lý**, cho worker đặt `AtomicBoolean` trong `catch` hoặc xác nhận callback.
- Nếu kiểm tra **đã dọn tài nguyên**, có latch `cleaned` hoặc test fake resource.
- Nếu kiểm tra **đã chấm dứt**, dùng `join(timeout)` rồi assert `!worker.isAlive()`.
- Nếu kiểm tra **khôi phục cờ**, đo `Thread.currentThread().isInterrupted()` **ở bên trong worker** sau thao tác restore; đừng đợi tới khi thread đã kết thúc mới suy luận từ đối tượng `Thread`.
- Nếu kiểm tra **hành vi nghiệp vụ**, thêm assertion rằng không có side effect sau khi nhận hủy.

Không mặc định mọi ứng dụng đều phải khôi phục cờ khi bắt `InterruptedException` và ngay lập tức truyền tiếp; nếu một phương thức `throws InterruptedException`, việc propagate thường đã phù hợp. Chọn chiến lược theo hợp đồng nhưng **test chính xác chiến lược ấy**.

## 6. Test chính caller bị ngắt khi đang gọi `Future.get()`

Ngắt luồng đang **chờ kết quả** khác với ngắt worker thực thi công việc. Nếu caller bị interrupt trong `Future.get()`, caller có thể nhận `InterruptedException`; **Future không mặc nhiên được hủy**, tác vụ nguồn có thể tiếp tục.

Có thể kiểm tra điều này mà không cần executor hoặc timing may rủi bằng một `CompletableFuture` chưa hoàn tất.

Tệp `FutureGetInterruptTest.java`:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class FutureGetInterruptTest {
    public static void main(String[] args) throws InterruptedException {
        CompletableFuture<String> result = new CompletableFuture<>();
        CountDownLatch callerReady = new CountDownLatch(1);
        AtomicBoolean callerInterrupted = new AtomicBoolean(false);

        Thread caller = new Thread(() -> {
            callerReady.countDown();

            try {
                result.get();
                throw new AssertionError("Unexpected result");
            } catch (InterruptedException e) {
                callerInterrupted.set(true);
                Thread.currentThread().interrupt();
            } catch (java.util.concurrent.ExecutionException e) {
                throw new AssertionError("Unexpected task failure", e);
            }
        }, "waiting-caller");

        caller.start();

        if (!callerReady.await(2, TimeUnit.SECONDS)) {
            caller.interrupt();
            throw new AssertionError("Caller not ready");
        }

        caller.interrupt();
        caller.join(2_000);

        if (caller.isAlive() || !callerInterrupted.get()) {
            throw new AssertionError("Caller did not handle interrupt");
        }
        if (result.isDone()) {
            throw new AssertionError("Future was changed by caller interrupt");
        }

        System.out.println("caller interrupted = "
                + callerInterrupted.get());
        System.out.println("future done = " + result.isDone());

        result.complete("late result");
        System.out.println("future result = " + result.join());
    }
}
```

Chạy:

```bash
javac FutureGetInterruptTest.java
java FutureGetInterruptTest
```

Đầu ra:

```text
caller interrupted = true
future done = false
future result = late result
```

Tín hiệu `callerReady` là mốc trước `get()`. Interrupt có thể tới trước khi caller thực sự chờ ở `get()`, nhưng `get()` có khả năng phản ứng với **trạng thái interrupt đã đặt** hoặc **interrupt đến trong lúc chờ**. Vì Future chưa được complete ở thời điểm gửi tín hiệu, đường chờ thành công không xảy ra.

Sau khi caller thoát, `result.isDone()` vẫn là `false`: **interrupt caller không tự cancel Future**. Main chủ động `complete("late result")` để chứng minh nguồn kết quả có thể hoàn tất về sau.

Nếu hợp đồng nghiệp vụ quy định “caller bị ngắt thì phải hủy luôn tác vụ nền”, test phải bổ sung bước gửi cancel và kiểm tra hủy. Đó là **chính sách ứng dụng**, không phải tác động tự động của `Future.get()`.

## 7. Test `Future.cancel(true)` với sự hợp tác của worker

`Future.cancel(true)` không có nghĩa “giết worker”. Với `FutureTask` thông thường được trả từ `ExecutorService.submit`, lời gọi hủy thành công có thể gửi interrupt đến worker đang chạy. Một test tốt cần xác minh **hai tầng kết quả**:

1. Future được đánh dấu canceled: `cancel(...)`, `isCancelled()`, `get()` ném `CancellationException`.
2. Worker thực sự phản ứng với interrupt và chạy cleanup theo hợp đồng.

Tệp `FutureCancelCooperationTest.java`:

```java
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class FutureCancelCooperationTest {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean sawInterrupt = new AtomicBoolean(false);

        try {
            Future<String> task = executor.submit(() -> {
                started.countDown();

                try {
                    new CountDownLatch(1).await();
                    return "unexpected";
                } catch (InterruptedException e) {
                    sawInterrupt.set(true);
                    Thread.currentThread().interrupt();
                    return "handled";
                } finally {
                    finished.countDown();
                }
            });

            if (!started.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("Task not started");
            }

            boolean cancelled = task.cancel(true);
            if (!cancelled) {
                throw new AssertionError("Cancellation unexpectedly lost");
            }

            if (!finished.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("Worker did not finish cleanup");
            }

            boolean getCancelled = false;
            try {
                task.get();
            } catch (CancellationException expected) {
                getCancelled = true;
            } catch (java.util.concurrent.ExecutionException e) {
                throw new AssertionError("Unexpected task failure", e);
            }

            if (!task.isCancelled()
                    || !sawInterrupt.get()
                    || !getCancelled) {
                throw new AssertionError("Cancellation contract violated");
            }

            System.out.println("cancel accepted = " + cancelled);
            System.out.println("future canceled = " + task.isCancelled());
            System.out.println("worker saw interrupt = "
                    + sawInterrupt.get());
            System.out.println("get canceled = " + getCancelled);
        } finally {
            executor.shutdownNow();
        }
    }
}
```

Biên dịch và chạy:

```bash
javac FutureCancelCooperationTest.java
java FutureCancelCooperationTest
```

Kết quả:

```text
cancel accepted = true
future canceled = true
worker saw interrupt = true
get canceled = true
```

Lời gọi `started.await()` đảm bảo `Callable` đã bắt đầu trước khi hủy. Worker chờ một latch không bao giờ mở; nó chỉ rời thao tác chờ bằng interrupt hoặc một lỗi bất ngờ khiến test thất bại. Sau khi `cancel(true)` thành công, test đợi `finished` để xác nhận worker đã chạy `finally`, đồng thời kiểm tra worker có thực sự bắt `InterruptedException`.

**Phân biệt quan trọng:** Future có thể ở trạng thái canceled **trước** khi worker hoàn tất. Vì vậy `task.isDone() == true` hoặc `get()` ném `CancellationException` **không phải bằng chứng cleanup đã xong**. Latch `finished` là xác nhận riêng. Trong production cần cơ chế lifecycle tương đương hoặc chính sách shutdown phù hợp.

Với `CompletableFuture.cancel(true)`, tham số `true` không có cùng ý nghĩa yêu cầu ngắt worker như `FutureTask` nói trên. Không chuyển nguyên test này sang `CompletableFuture` rồi mặc định thread nền phải nhận interrupt; API có hợp đồng hủy khác nhau.

## 8. Ngắt lúc chờ khóa bằng `lockInterruptibly()`

Thao tác `synchronized` chờ lấy monitor **không thể bị hủy bằng `InterruptedException` chỉ vì đang đợi monitor**. Nếu cần một thao tác xin khóa có thể ngắt, `ReentrantLock.lockInterruptibly()` là lựa chọn phù hợp.

Một test có thể cho **main giữ khóa** trước khi worker cố lấy. Nhờ vậy worker không thể lấy khóa trước khi test gửi interrupt, và không cần sleep.

Tệp `InterruptibleLockTest.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class InterruptibleLockTest {
    public static void main(String[] args) throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        CountDownLatch ready = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean(false);
        AtomicBoolean acquired = new AtomicBoolean(false);

        lock.lock();
        Thread worker = new Thread(() -> {
            ready.countDown();

            try {
                lock.lockInterruptibly();
                try {
                    acquired.set(true);
                } finally {
                    lock.unlock();
                }
            } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            }
        }, "lock-waiter");

        try {
            worker.start();

            if (!ready.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("Worker not ready");
            }

            worker.interrupt();
            worker.join(2_000);

            if (worker.isAlive()
                    || !interrupted.get()
                    || acquired.get()) {
                throw new AssertionError("Lock interrupt contract violated");
            }

            System.out.println("interrupt observed = "
                    + interrupted.get());
            System.out.println("lock acquired = " + acquired.get());
            System.out.println("worker alive = " + worker.isAlive());
        } finally {
            worker.interrupt();
            lock.unlock();
        }
    }
}
```

Chạy:

```bash
javac InterruptibleLockTest.java
java InterruptibleLockTest
```

Kết quả:

```text
interrupt observed = true
lock acquired = false
worker alive = false
```

Main đã nắm khóa trước khi worker bắt đầu, nên worker không thể lấy được khóa trong thời gian test giữ nó. Interrupt khiến `lockInterruptibly()` phản ứng dù tín hiệu đến **trước** lúc gọi hoặc **trong** lúc đang chờ. `acquired` vẫn `false`, worker kết thúc.

**Chú ý cleanup:** chỉ gọi `lock.unlock()` trong worker nếu worker **đã lấy được khóa**. Ở nhánh `InterruptedException` trước khi lock thành công, gọi `unlock()` sẽ sai. Trong ví dụ, worker đặt `unlock()` bên trong phần được thực hiện sau `lockInterruptibly()` thành công.

Nếu test đơn giản dùng `synchronized(lock)` trong worker thay vì `lockInterruptibly`, nó **không cùng hợp đồng**. Đừng ép test chờ monitor phải ném `InterruptedException`.

## 9. `CountDownLatch` có phải cách duy nhất?

Không. Chọn primitive theo điều kiện bạn cần chứng minh:

| Primitive | Tín hiệu hữu ích | Khi nên dùng |
|---|---|---|
| `CountDownLatch` | Một hoặc nhiều công việc đã đến mốc nhất định | Phối hợp pha sẵn sàng/kết thúc |
| `CyclicBarrier` | Nhiều bên cùng đến một mốc để tiếp tục | Kiểm thử nhiều worker qua từng vòng |
| `Phaser` | Các pha đồng bộ có số bên linh hoạt | Test nhiều bước trong workflow |
| `Semaphore` | Cấp/nhận permit | Kiểm tra chờ permit và interrupt |
| `BlockingQueue` | Có/không có phần tử | Test producer-consumer và thao tác blocking |
| `ReentrantLock` | Ai giữ/đợi lock | Test `lockInterruptibly` và tranh chấp |
| `Future` / `CompletableFuture` | Tác vụ có kết quả hoặc trạng thái hoàn thành | Test exceptional completion, caller interrupt, cancellation |
| `Thread.join()` | Worker đã kết thúc | Test lifecycle, đọc kết quả worker sau khi thoát |

Điểm chung: primitive phải có **quan hệ sự kiện xác định**. Không cần tạo nhiều latch nếu một primitive đã đủ. Tên latch như `ready`, `started`, `finished`, `release`, `cleanupDone` nên phản ánh **đúng sự kiện đã xảy ra**.

Phân biệt `ready.countDown()` với một cam kết mạnh hơn như “đã vào code blocking”. Nếu event nằm **trước lời gọi blocking**, `ready` chứng minh worker đến dòng tín hiệu, không chứng minh chính xác vị trí instruction sau đó. Thiết kế test nên không phụ thuộc việc scheduler chọn một trong hai thời điểm.

## 10. Sử dụng timeout như hàng rào an toàn

Không nên làm:

```java
ready.await();
worker.join();
```

trong test mà implementation có thể bug khiến latch không được mở hoặc worker không kết thúc. Test có nguy cơ treo mãi trên CI.

Nên dùng timeout ở điểm chờ của **thread test**:

```java
if (!ready.await(3, java.util.concurrent.TimeUnit.SECONDS)) {
    throw new AssertionError("Worker did not become ready");
}

worker.join(3_000);
if (worker.isAlive()) {
    throw new AssertionError("Worker did not terminate");
}
```

Đây là mảnh mã minh họa với `ready`, `worker` đã tồn tại. Timeout vừa buộc test **thất bại có thông điệp** khi thiếu event, vừa đặt giới hạn thời gian cho tình huống có bug.

Cần tách rõ:

- `await(3s)` **đợi sự kiện** `ready` và chỉ trả `true` khi event đã đến.
- `sleep(3s)` **đợi thời gian**, không quan sát sự kiện.
- `join(3000)` đợi thread kết thúc tối đa khoảng thời gian; phải kiểm tra `isAlive()` sau đó.
- `Future.get(timeout, unit)` đợi kết quả tối đa khoảng thời gian; `TimeoutException` **không tự động hủy** Future.
- `CountDownLatch.await(timeout, unit)` trả `boolean`; phải assert kết quả, không bỏ qua.

Timeout có thể cần nới trên CI chậm nhưng không nên dùng timeout làm **cách cưỡng ép thứ tự**. Nếu test thường thất bại do timeout dưới tải hợp lệ, xem lại deadline hoặc ranh giới công việc, không thay test bằng `sleep` dài hơn.

### Trường hợp timeout cũng cần cleanup

Nếu worker chưa kết thúc tại timeout, test không nên bỏ đi một thread non-daemon đang chạy vô hạn. Trong `finally` nên **gửi tín hiệu dừng**, mở latch/queue nếu thích hợp, shutdown executor và giới hạn nỗ lực chờ lại. Nếu test còn treo do code cố tình phớt lờ interrupt, đó là lỗi/lỗ hổng thiết kế cần giải quyết; Java không có cơ chế an toàn chung để ép giết một thread tùy ý.

Không dùng `Thread.stop()` để cưỡng bức kết thúc test. API này nguy hiểm, có thể làm hỏng trạng thái dùng chung.

## 11. Không dùng `Thread.State` làm đồng bộ chính

Một ý tưởng thường thấy:

```java
while (worker.getState() != Thread.State.WAITING) {
    Thread.yield();
}
worker.interrupt();
```

Đây là mảnh code minh họa **không khuyến nghị**. Các vấn đề:

- Thread có thể chuyển qua nhiều trạng thái nhanh giữa hai lần `getState()`.
- `WAITING` không tự nói worker đang chờ đúng latch/queue nào.
- Polling/busy spin có thể chiếm CPU hoặc treo nếu trạng thái mong muốn không xuất hiện.
- `Thread.yield()` chỉ là gợi ý scheduler, không phải primitive xác nhận sự kiện.
- Test trở nên lệ thuộc vào chi tiết scheduling, JVM và thư viện.

Nếu cần chứng minh một điểm trong thuật toán đã xảy ra, hãy đặt **hook đồng bộ tại chính điểm logic đó**. Nếu đang test một thư viện third-party không có hook, hãy kiểm tra **hành vi observable** theo hợp đồng (kết thúc khi interrupt, không mutate sau cancel) thay vì state nội bộ tại một thời khắc không kiểm soát được.

Thread state có thể hữu ích để **chẩn đoán** deadlock hoặc thread dump, nhưng không phải thay thế latch/barrier để điều khiển một test.

## 12. Kiểm thử với JUnit 5: assert sau khi worker hoàn tất

JUnit Jupiter cung cấp `@Test`, `assertTrue`, `assertFalse`, `assertEquals`, `assertThrows`... Các ví dụ JUnit bên dưới **cần dependency JUnit 5 và test runner** của Maven/Gradle/IDE; không phải chương trình độc lập chạy trực tiếp bằng `java`.

Mã JUnit minh họa trong `InterruptContractJUnitTest.java`:

```java
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class InterruptContractJUnitTest {
    @Test
    void interruptStopsWorkerWaitingOnLatch() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch blocked = new CountDownLatch(1);
        AtomicBoolean caught = new AtomicBoolean(false);

        Thread worker = new Thread(() -> {
            ready.countDown();

            try {
                blocked.await();
            } catch (InterruptedException e) {
                caught.set(true);
                Thread.currentThread().interrupt();
            }
        });

        worker.start();

        try {
            assertTrue(ready.await(3, TimeUnit.SECONDS),
                    "Worker never reached ready point");

            worker.interrupt();
            worker.join(3_000);

            assertFalse(worker.isAlive(),
                    "Worker did not terminate");
            assertTrue(caught.get(),
                    "Worker did not handle InterruptedException");
        } finally {
            worker.interrupt();
            blocked.countDown();
            worker.join(3_000);
        }
    }
}
```

Test có ba phần riêng:

1. **Arrange:** tạo hai latch và trạng thái kiểm thử; worker chờ một latch đang đóng.
2. **Act:** test đợi `ready`, gửi `interrupt`.
3. **Assert:** đợi worker hoàn tất có giới hạn thời gian, kiểm tra `isAlive=false` và `caught=true`.

`finally` cố gắng giải phóng tình huống test fail trước khi worker kết thúc. `worker.join(3000)` trong cleanup cũng có thời hạn. **Không dùng `assertTrue(caught.get())` ngay sau `worker.interrupt()` mà chưa có đồng bộ hoàn tất**: test có thể đọc trạng thái trước khi worker vào `catch`.

Nếu test thread bị interrupt bởi môi trường ngoài, `await`/`join` có thể ném `InterruptedException`; test cần để runner nhận diện, đồng thời thực hiện cleanup thích hợp. Không nuốt interrupt của thread chạy test chỉ để giữ test xanh.

### JUnit `assertTimeout` không thay thế latch

`assertTimeout(...)` và `assertTimeoutPreemptively(...)` (trên JUnit 5) có thể giới hạn thời gian test nhưng **không xác lập thứ tự thực thi**. Đặc biệt timeout preemptive có thể chạy executable trong thread riêng, ảnh hưởng `ThreadLocal`, transaction context và logic interrupt. Chỉ dùng khi hiểu hợp đồng và rủi ro; vẫn cần latch, queue hoặc primitive đồng bộ để kiểm tra logic.

## 13. Test `InterruptedException` trong method riêng nên tách việc kiểm tra trạng thái

Một phương thức thực tế có thể:

```java
static void consume(java.util.concurrent.BlockingQueue<String> queue) {
    try {
        while (!Thread.currentThread().isInterrupted()) {
            String item = queue.take();
            process(item);
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    } finally {
        releaseResources();
    }
}
```

Đây là **mẫu minh họa**, `process(String)` và `releaseResources()` do ứng dụng định nghĩa. Nếu đang test lớp này, không chỉ cần biết worker đã dừng. Nên tạo **fake processing** và **fake cleanup** có thể quan sát:

- `processedCount` phải bằng 0 nếu worker chưa nhận phần tử.
- `cleanupCount` phải bằng 1 khi worker kết thúc.
- `interruptedObserved` hoặc outcome riêng thể hiện worker đã thực thi chính sách interrupt.
- Không có item mới được xử lý sau khi nhận tín hiệu dừng nếu đó là hợp đồng.
- Worker không còn sống sau deadline được cấp.

Một fake tốt tạo ra tín hiệu bằng `CountDownLatch` hoặc atomic state, không bằng log string được đọc theo thời gian may rủi. Nếu cần kiểm tra nhiều pha có item thực, tách rõ `itemReceived`, `processingStarted`, `processingFinished` và `stopObserved`.

Đừng tùy tiện assert số lần thread bị scheduler chuyển trạng thái, số nanosecond để thoát hay `Thread.getState()` chính xác tại một thời điểm; chúng thường không thuộc hợp đồng ứng dụng.

## 14. Phân biệt các loại lỗi khi test ngắt bất đồng bộ

| Tình huống | Caller thường thấy | Điều test phải kiểm tra |
|---|---|---|
| Chính worker bị interrupt tại `await`, worker propagate checked exception từ `Callable` | `Future.get()` có thể ném `ExecutionException` có cause là `InterruptedException` nếu Future không canceled | Xác nhận cause, trạng thái Future, cleanup |
| Chính caller bị interrupt trong `Future.get()` | `InterruptedException` trực tiếp trong caller | Không tự suy worker bị hủy |
| `future.cancel(true)` thành công trên `FutureTask` | `get()` ném `CancellationException`; worker có thể nhận interrupt | Phân biệt Future canceled với worker đã dừng |
| `get(timeout, unit)` hết hạn | `TimeoutException` ở caller | Deadline không tự cancel task |
| Worker hoàn thành bình thường rồi caller gọi interrupt quá muộn | Có thể không có exception từ worker | Phải phối hợp để giữ task chưa hoàn tất |
| `CompletableFuture.cancel(true)` | Future canceled, `join()` thường ném `CancellationException` | Không mặc định interrupt thread nền |

Chú ý `Future.get()` không nhất thiết ném `InterruptedException` trong mọi trạng thái nếu caller đã bị interrupt: ví dụ trong những tình huống kết quả đã có sẵn và race, hợp đồng/implementation có thể có đường hoàn thành ngay. Do đó để test **caller bị interrupt khi đang chờ**, hãy giữ Future **chưa hoàn thành** cho tới sau khi caller đã quan sát interrupt.

Tương tự, một task có thể kết thúc trước khi `cancel(true)` được gọi. Test phải dùng `started` và điểm chờ có kiểm soát để đảm bảo task không hoàn thành sớm nếu muốn assert `cancel(true)` thành công.

## 15. Kiểm thử mà không giấu exception của worker

Nếu `Runnable.run()` ném `AssertionError` trong thread phụ, framework kiểm thử ở thread chính **không tự động biến nó thành test failure** trong mọi trường hợp. Ví dụ:

```java
Thread worker = new Thread(() -> {
    throw new AssertionError("Worker assertion failed");
});
worker.start();
worker.join();
```

Đây là mảnh minh họa. Lỗi có thể chỉ được báo qua uncaught exception handler của worker; test thread cần có cơ chế **thu nhận và xác minh lỗi từ worker**. Với executor, `Future.get()` giúp chuyển lỗi tác vụ về caller dưới `ExecutionException`, nhưng nếu đã gọi `cancel(...)` trên Future, `get()` trả trạng thái cancel thay vì lỗi worker sau đó.

Các lựa chọn:

- `AtomicReference<Throwable>` để lưu lỗi không mong muốn, đọc sau `join`/`done`.
- `Future.get()` cho task không bị hủy, kiểm tra `ExecutionException.getCause()`.
- `Thread.setUncaughtExceptionHandler(...)` để ghi nhận lỗi uncaught nếu phù hợp.
- Một kênh báo cáo kết quả tường minh, ví dụ `BlockingQueue<Outcome>`, có deadline.

### Ví dụ: bắt lỗi worker thay vì cho test giả xanh

Tệp `WorkerFailureCaptureTest.java`:

```java
import java.util.concurrent.atomic.AtomicReference;

public class WorkerFailureCaptureTest {
    public static void main(String[] args) throws InterruptedException {
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread worker = new Thread(() -> {
            try {
                throw new IllegalStateException("worker failed");
            } catch (Throwable e) {
                failure.set(e);
            }
        });

        worker.start();
        worker.join(2_000);

        if (worker.isAlive()) {
            worker.interrupt();
            throw new AssertionError("Worker still alive");
        }

        Throwable observed = failure.get();
        if (!(observed instanceof IllegalStateException)) {
            throw new AssertionError("Wrong worker failure", observed);
        }

        System.out.println("worker failure = "
                + observed.getClass().getSimpleName());
    }
}
```

Chạy:

```bash
javac WorkerFailureCaptureTest.java
java WorkerFailureCaptureTest
```

Output:

```text
worker failure = IllegalStateException
```

Đây là một ví dụ **về kiểm tra kết quả của thread phụ**, không phải cách xử lý mọi `Throwable` trong production. Trong test, việc bắt và lưu giúp **chuyển lỗi sang thread đang chạy assertion**; ở production, bắt `Throwable` quá rộng có thể che `Error` nghiêm trọng và cần tránh trừ khi có ranh giới quản lý lỗi rõ ràng.

## 16. Đặt `finally` để test tự dọn dẹp khi assertion thất bại

Kiểm thử đồng thời có thể fail ở bất kỳ bước nào. Vì thế tài nguyên kiểm thử phải được dọn dẹp trong `finally`, kể cả khi một assertion ném `AssertionError`.

Ví dụ kiến trúc:

```java
ExecutorService executor = Executors.newSingleThreadExecutor();
CountDownLatch release = new CountDownLatch(1);

try {
    Future<?> task = executor.submit(() -> {
        release.await();
        return null;
    });

    // Phối hợp/test/assert ở đây.
} finally {
    release.countDown();
    executor.shutdownNow();
    executor.awaitTermination(3, TimeUnit.SECONDS);
}
```

Đây là **mảnh mã minh họa trong phương thức có `throws InterruptedException`** cùng imports phù hợp. `executor.submit(Callable)` cho phép lambda ném `InterruptedException`. Trong test thật, nếu `awaitTermination` trả `false`, phải xác định worker còn sống và fail/report rõ ràng; không chỉ gọi nó rồi bỏ qua giá trị.

Dọn dẹp có thể cần **đặt lại trạng thái interrupt** cho thread kiểm thử nếu phải catch `InterruptedException` ở boundary. Không nuốt exception của cleanup một cách tùy tiện, nhưng cũng không để cleanup failure che mất nguyên nhân test thất bại mà không ghi nhận; trường hợp có primary và secondary failure, `try-with-resources` hoặc cơ chế suppressed thích hợp giúp giữ cả hai.

Trong test fixture dùng thread tự quản, cân nhắc `setDaemon(true)` chỉ như một công cụ giảm rủi ro treo JVM, **không phải** thay thế cho `interrupt`, release và `join`. Một test chỉ xanh vì daemon worker bị bỏ lại sau khi JVM kết thúc chưa chứng minh lifecycle đúng.

## 17. Những anti-pattern hay gặp

### A. Dùng `sleep(100)` như dấu hiệu worker đã sẵn sàng

```java
worker.start();
Thread.sleep(100);
worker.interrupt();
```

Thay bằng latch có ý nghĩa: worker `ready.countDown()`, test `ready.await(timeout)`, rồi mới gửi tín hiệu. Tránh giả định thời gian chạy worker.

### B. Gửi interrupt rồi assert ngay

```java
worker.interrupt();
assertTrue(interruptedFlag.get());
```

Có thể fail vì worker chưa kịp xử lý. Đợi `done.await(timeout)` hoặc `join(timeout)` và **kiểm tra đã hoàn thành**, rồi mới đọc flag.

### C. Kiểm tra `isInterrupted()` bên ngoài khi worker đã bắt `InterruptedException`

Lời gọi chờ thường **xóa cờ** khi ném `InterruptedException`; worker có thể khôi phục cờ, sau đó kết thúc. Cờ đọc ở một thời điểm tùy ý không thể chứng minh exception có được bắt hay không. Đo cờ **trong worker**, ở đúng điểm trong hợp đồng.

### D. Chỉ kiểm tra `future.isDone()`

Sau `future.cancel(true)`, `isDone()` có thể là `true` dù worker còn chạy. Thêm `finished` hoặc lifecycle signal nếu test cần chứng minh cleanup/worker exit.

### E. Dùng `Thread.State.WAITING` như điều kiện chắc chắn

Thread state là snapshot tạm thời; không chứng minh worker đang chờ **đúng tài nguyên**. Dùng đồng bộ ứng dụng để chứng minh đúng trạng thái logic.

### F. `await()` vô hạn trong test có thể lỗi

Một bug có thể khiến latch không bao giờ mở và CI treo. Dùng `await(timeout)`, assert boolean, giải phóng tài nguyên trong `finally`.

### G. Test interrupt của caller nhưng lại hủy Future

Nếu mục tiêu là kiểm tra `Future.get()` trả `InterruptedException` khi caller bị ngắt, không tự gọi `future.cancel(true)` trước khi kiểm tra; lúc này `get()` có thể ném `CancellationException` theo trạng thái hủy. Giữ Future pending rồi interrupt caller là setup rõ ràng hơn.

### H. Swallow `InterruptedException` trong test

```java
try {
    latch.await();
} catch (InterruptedException ignored) {
}
```

Đây là mảnh minh họa **sai**: test có thể tiếp tục và báo xanh dù chính thread kiểm thử bị ngắt. Thông thường test method có thể `throws InterruptedException`; nếu phải bắt, khôi phục cờ và fail/propagate theo chính sách.

### I. Lệ thuộc thứ tự in log giữa nhiều thread

Những dòng `System.out.println` từ các thread khác nhau có thể xen kẽ. Nếu cần stdout chính xác, chỉ in ở main **sau khi đã đợi kết quả** như các ví dụ ở trên. Trong test production, kiểm tra state/event hơn là so sánh toàn bộ console transcript.

### J. Bỏ quên worker hoặc executor sau khi fail

Nếu assert fail trước bước shutdown, thread non-daemon có thể tiếp tục chạy vô hạn. Dùng `try/finally`, mở các latch dùng để chặn, `shutdownNow()` khi thích hợp và `awaitTermination(timeout)`; giữ dấu vết nếu dừng không thành công.

## 18. Checklist thiết kế một test interrupt đáng tin

Trước khi chốt test, hãy trả lời:

1. **Luồng nào bị interrupt?** Worker hay caller đang chờ `get()`/`join()`?
2. **Thao tác đích có thể ngắt không?** `BlockingQueue.take`, `CountDownLatch.await`, `lockInterruptibly` khác với chờ vào `synchronized`.
3. **Mốc `ready` chứng minh điều gì?** Nó có đủ để loại trừ task hoàn thành trước tín hiệu không?
4. **Đường thoát bình thường đã bị kiểm soát?** Queue rỗng, latch không mở, lock do test giữ, Future chưa hoàn tất?
5. **Không dựa vào may rủi scheduler?** Không dùng `sleep` để đoán thời điểm.
6. **Đợi worker xử lý xong chưa?** Có `done`, `join` hoặc `Future` thích hợp?
7. **Cờ interrupt được kiểm tra ở đúng thread/đúng thời điểm?** Có phân biệt `isInterrupted()` với `Thread.interrupted()` vốn xóa cờ?
8. **Có kiểm tra kết quả thực?** Loại exception, cause, trạng thái Future, cleanup và side effects.
9. **Có timeout để chống treo?** `await` trả `true`, `join` không còn sống, `get(timeout)` được xử lý đúng.
10. **Có cleanup kể cả khi test fail?** Mở latch, hủy task, đóng executor và báo lỗi còn sót.
11. **Lỗi thread phụ có được truyền về test thread?** Không để `AssertionError` của worker chỉ trôi vào log.
12. **Test có giữ đúng phạm vi hợp đồng?** Không ép một thứ tự ngẫu nhiên hoặc tên thread đặc thù JVM.

**Ghi nhớ:** test interrupt đúng không dựa vào “ngủ đủ lâu”. Hãy xây dựng **tín hiệu sẵn sàng, yêu cầu interrupt, tín hiệu hoàn tất và assertion** trên trạng thái có ý nghĩa. `CountDownLatch` và các primitive đồng bộ giúp tạo quan hệ sự kiện; timeout chỉ giới hạn test treo. Khi kiểm tra `Future`, luôn phân biệt **interrupt caller**, **interrupt worker**, **trạng thái canceled** và **worker thật sự đã dọn dẹp, kết thúc**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-testing-010">Phân biệt lỗi của mã đang kiểm thử với lỗi trong phần chuẩn bị dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt lỗi của mã đang kiểm thử với lỗi trong phần chuẩn bị dữ liệu (Java)

## 1. Vấn đề: một test “xanh” chưa chắc kiểm tra đúng đoạn mã

Một bài kiểm thử thường có ba pha: **Arrange** (chuẩn bị dữ liệu và điều kiện), **Act** (gọi mã cần kiểm thử, hay *System Under Test* — SUT), và **Assert** (xác nhận kết quả). Khi kiểm tra exception, ranh giới giữa Arrange và Act đặc biệt quan trọng.

Xem đoạn JUnit 5 sau:

```java
assertThrows(IllegalArgumentException.class, () -> {
    OrderLine line = makeOrderLine("", -2);
    orderService.calculate(line);
});
```

Đây là **mảnh test cần các hàm/class và JUnit 5 đã được định nghĩa**, không phải chương trình Java SE độc lập. Người viết muốn xác nhận `orderService.calculate(...)` từ chối số lượng âm. Nhưng `makeOrderLine("", -2)` có thể ném `IllegalArgumentException` trước khi dòng `calculate` chạy, chẳng hạn do SKU rỗng. Khi ấy `assertThrows` vẫn báo **PASS**, mặc dù SUT **chưa được gọi lần nào**.

Đây là một **false positive**: test đạt điều kiện hình thức “có exception thuộc loại X”, nhưng không chứng minh đặc tính đang cần kiểm thử.

Giải pháp thường là:

1. Chuẩn bị fixture **ngoài lambda của `assertThrows`**.
2. Đảm bảo fixture hợp lệ theo **những điều kiện ngoài điều kiện cần thử**.
3. Giới hạn lambda chỉ chứa **hành động có trách nhiệm phát sinh exception mong đợi**.
4. Kiểm tra thêm mã lỗi, thuộc tính exception, cause hoặc tương tác của SUT khi có giá trị.
5. Nếu bước Arrange thất bại, test phải báo **lỗi setup**, không được nhận nhầm là thành công của Act.

**Nguyên tắc cốt lõi:** nguồn gốc exception và thời điểm xuất hiện exception quan trọng không kém loại exception.

## 2. Ba pha Arrange — Act — Assert

| Pha | Nhiệm vụ | Lỗi thường xuất hiện | Ý nghĩa với kết quả test |
| --- | --- | --- | --- |
| Arrange | Tạo object, fixture, stub, mock, dữ liệu DB, cấu hình test | Factory thiếu dữ liệu, JSON parse lỗi, container không khởi động, mock chưa cấu hình | **Test chưa vào được hành vi cần kiểm thử** |
| Act | Gọi chính SUT theo một tình huống xác định | Exception nghiệp vụ, validation error, lỗi phụ thuộc mà SUT cần xử lý | Đây là **lỗi thuộc hành vi test đang đánh giá** |
| Assert | Kiểm tra kết quả, exception, state, interaction | Assertion sai, test helper lỗi, truy xuất result sai | Test thất bại ở **bước xác minh**, không đồng nghĩa SUT ném đúng exception |
| Cleanup/teardown | Đóng file, connection, transaction, resource thử nghiệm | Lỗi close, rollback, cleanup | Có thể làm test thất bại dù Act/Assert đã chạy |

Một exception phát sinh trong Arrange không tự động là “bug của SUT”. Nó có thể là lỗi chính fixture builder, seed dữ liệu, test infrastructure hoặc môi trường. Đổi lại, **test data setup cũng là code** và phải được kiểm thử khi nó có logic phức tạp. Tách nguồn lỗi giúp biết cần sửa fixture hay sửa SUT.

Khi đọc test failure, hãy xác định **pha nào** xảy ra lỗi trước khi quyết định thay đổi production code.

## 3. Tại sao kiểu exception thôi là chưa đủ?

`assertThrows(IllegalArgumentException.class, executable)` kiểm tra có exception thuộc loại `IllegalArgumentException` hoặc subtype của nó được ném khi thực thi `executable`. Nó **không biết ý định của bạn**: exception đó có thể đến từ:

- Builder `new OrderLine(...)`;
- Validator trong mock/stub;
- Mapper JSON;
- Phương thức SUT;
- Callback do SUT kích hoạt;
- Assertion helper trong cùng lambda;
- Một phương thức chuẩn bị dữ liệu được gọi “tiện tay” bên trong Act.

Ví dụ sai:

```java
assertThrows(IllegalStateException.class, () -> {
    Repository repository = createRepository();
    MyService service = new MyService(repository);
    service.run();
});
```

Đây là mảnh JUnit, với các API ứng dụng đã định nghĩa. `createRepository()` hoặc constructor `MyService` có thể ném `IllegalStateException` trước `run()`. Test đạt nhưng chưa chứng minh `run()` tuân thủ contract.

**Sửa:**

```java
Repository repository = createRepository();
MyService service = new MyService(repository);

IllegalStateException error =
        assertThrows(IllegalStateException.class, service::run);
```

Nếu Arrange lỗi, test vẫn **FAIL**, nhưng failure chỉ thẳng tới bước chuẩn bị. Nếu Arrange thành công, chỉ phương thức `run` nằm trong phạm vi kỳ vọng exception.

Không cần bao bọc setup bằng `try/catch` để “sửa” một failure. Để lỗi setup được framework báo tự nhiên thường đã đủ, miễn test được cấu trúc rõ.

## 4. Ví dụ Java 17+ độc lập: false positive và cách sửa

Chương trình sau không cần JUnit để có thể chạy ngay bằng JDK 17+. Nó tự định nghĩa một helper `expectThrows` đơn giản chỉ nhằm minh họa hành vi giống một phép kỳ vọng exception, **không phải bản thay thế đầy đủ cho JUnit**.

Các trường hợp:

- **BROAD_SCOPE:** một lambda bao cả Arrange và Act. Arrange ném `IllegalArgumentException`, test tưởng “bắt đúng lỗi”.
- **SETUP_FAILED:** tách Arrange ra và nhìn thấy rõ lỗi fixture.
- **ACT_FAILED:** tạo fixture với SKU hợp lệ nhưng số lượng âm để chủ động kiểm thử lỗi của SUT; chỉ gọi SUT bên trong `expectThrows`.
- **SUCCESS_TOTAL:** có một kiểm thử đường thành công.
- Các kiểm tra tự động dừng chương trình bằng `AssertionError` nếu kết quả không đúng.

Tạo file **`TestBoundaryDemo.java`**:

```java
public class TestBoundaryDemo {
    record OrderLine(String sku, int quantity) {}

    static OrderLine arrange(String sku, int quantity) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("fixture: sku is blank");
        }
        return new OrderLine(sku, quantity);
    }

    static int calculate(OrderLine line) {
        if (line.quantity() < 0) {
            throw new IllegalArgumentException("sut: quantity is negative");
        }
        return line.quantity() * 10;
    }

    static <T extends Throwable> T expectThrows(
            Class<T> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable error) {
            if (type.isInstance(error)) {
                return type.cast(error);
            }
            throw new AssertionError("Wrong exception: " + error, error);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }

    public static void main(String[] args) {
        IllegalArgumentException misleading =
                expectThrows(IllegalArgumentException.class, () -> {
                    OrderLine line = arrange("", -2);
                    calculate(line);
                });
        System.out.println("BROAD_SCOPE: " + misleading.getMessage());

        try {
            arrange("", -2);
            throw new AssertionError("Setup should fail");
        } catch (IllegalArgumentException error) {
            System.out.println("SETUP_FAILED: " + error.getMessage());
        }

        OrderLine validFixture = arrange("SKU-1", -2);
        IllegalArgumentException expected =
                expectThrows(IllegalArgumentException.class,
                        () -> calculate(validFixture));
        if (!"sut: quantity is negative".equals(expected.getMessage())) {
            throw new AssertionError("Wrong failure origin");
        }
        System.out.println("ACT_FAILED: " + expected.getMessage());

        OrderLine normalFixture = arrange("SKU-1", 3);
        int total = calculate(normalFixture);
        if (total != 30) {
            throw new AssertionError("Unexpected total");
        }
        System.out.println("SUCCESS_TOTAL: " + total);
        System.out.println("CHECKS: PASS");
    }
}
```

Lệnh chạy trong thư mục có file:

```bash
javac -encoding UTF-8 TestBoundaryDemo.java
java TestBoundaryDemo
```

**Standard output đã kiểm chứng:**

```text
BROAD_SCOPE: fixture: sku is blank
SETUP_FAILED: fixture: sku is blank
ACT_FAILED: sut: quantity is negative
SUCCESS_TOTAL: 30
CHECKS: PASS
```

### Diễn giải kết quả

1. `BROAD_SCOPE` hiển thị **fixture: sku is blank**, không phải lỗi quantity từ `calculate`. Lambda bao quá rộng đã che nguồn lỗi.
2. `SETUP_FAILED` minh họa việc tách Arrange giúp nhìn thấy lỗi chuẩn bị là một lỗi khác, không phải hành vi đang kiểm thử.
3. `validFixture` có SKU hợp lệ nhưng quantity âm. **Quantity âm là input chủ động cho SUT validation**, không phải lỗi builder; vì vậy Arrange không ném.
4. `ACT_FAILED` chỉ xuất hiện do `calculate` ném. Kiểm tra message ở đây có mục đích phân biệt rõ fixture/SUT trong demo.
5. `SUCCESS_TOTAL` xác nhận đường xử lý bình thường trả `30`.
6. `expectThrows` bắt `Throwable` trong một **test harness minh họa** để phát hiện loại Throwable ném ra và chuyển sai khác thành `AssertionError`. Điều này không phải lời khuyên thêm `catch(Throwable)` đại trà vào production code.

Trong ứng dụng thật, nên dùng assertion của framework kiểm thử; `assertThrows` của JUnit đã có cơ chế báo lỗi đầy đủ và hỗ trợ message tùy biến.

## 5. Mẫu JUnit 5 nên dùng

Với bài test “quantity âm phải bị từ chối”, tách rõ:

```java
@Test
void rejectsNegativeQuantity() {
    // Arrange: hợp lệ về SKU; quantity âm là input cần kiểm thử.
    OrderLine line = new OrderLine("SKU-1", -2);

    // Act + assert exception: chỉ chứa lời gọi SUT.
    IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> service.calculate(line));

    // Assert bổ sung: chứng minh đúng lý do.
    assertEquals("QUANTITY_NEGATIVE", error.getMessage());
}
```

Đây là **mảnh JUnit 5**, không biên dịch độc lập khi chưa có class `OrderLine`, `service`, import `@Test` và các static assertion. Chuỗi `QUANTITY_NEGATIVE` là giả định một **error code ổn định do ứng dụng quy định**; không được ghép với message của `TestBoundaryDemo` ở trên vì đây là hai ví dụ độc lập với hợp đồng khác nhau.

Nếu test builder có kiểm tra SKU, nên tạo fixture ngay trước `assertThrows`:

```java
OrderLine line = fixtureBuilder()
        .sku("SKU-1")
        .quantity(-2)
        .build();

IllegalArgumentException error = assertThrows(
        IllegalArgumentException.class,
        () -> service.calculate(line));
```

Nếu `build()` ném, JUnit báo lỗi từ Arrange; nó **không** được xem là exception đúng cho `service.calculate`. Phần này cũng là mảnh minh họa yêu cầu builder và SUT được khai báo.

### `assertThrows` so với `assertThrowsExactly`

- `assertThrows(Expected.class, executable)` chấp nhận `Expected` **hoặc subtype**.
- `assertThrowsExactly(Expected.class, executable)` yêu cầu đúng runtime class `Expected`, theo API JUnit Jupiter có hỗ trợ.
- Chọn “exact” nếu khác biệt subtype có ý nghĩa đối với hợp đồng lỗi; không lạm dụng nếu subclass hợp lệ.

Cả hai đều **không tự xác nhận nguồn gốc exception**. Lambda phải được khoanh đúng, và khi cần có thể assert error code/cause hoặc tương tác.

## 6. Khi nào dùng `assertDoesNotThrow` cho Arrange?

Đôi khi bạn muốn làm rõ bước setup **không được phép thất bại**:

```java
OrderLine fixture = assertDoesNotThrow(
        () -> fixtures.createValidOrderLine("SKU-1", -2),
        "Fixture setup must succeed");

IllegalArgumentException error = assertThrows(
        IllegalArgumentException.class,
        () -> service.calculate(fixture));
```

Đây là mảnh JUnit 5 và API ứng dụng minh họa. `assertDoesNotThrow` có thể giúp thông báo “setup lỗi” nổi bật hơn thay vì để exception xuất hiện như một failure thông thường. **Nó không bắt buộc:** nếu code Arrange đơn giản, gọi thẳng fixture helper bên ngoài `assertThrows` thường rõ ràng và ít lớp wrapper hơn.

Đừng bọc tất cả test body trong `assertDoesNotThrow` rồi sau đó kiểm tra exception khác; mục tiêu là phân pha, không phải thêm assertion quanh mọi dòng.

Quan trọng: helper setup có thể chủ động từ chối fixture không hợp lệ. Nếu vậy, phải chọn fixture đúng ý đồ; không ép helper bỏ validation hoặc catch lỗi chỉ để qua được Arrange.

## 7. Valid input cho setup khác với invalid input cho SUT

Một tình huống thường gây nhầm:

- Bạn muốn kiểm tra SUT từ chối **quantity âm**.
- Fixture builder cũng có validation **quantity không được âm**.
- Lúc gọi builder, exception xuất hiện sớm và không tới SUT.

Nếu builder thật sự cam kết chỉ tạo object hợp lệ, cần quyết định chiến lược test:

1. Kiểm thử input qua API SUT **ở trước validation của builder**, ví dụ gọi service nhận DTO/request chưa được validate.
2. Dùng một value object test cho phép biểu diễn trạng thái không hợp lệ tại đúng boundary đang thử, nếu kiến trúc cho phép.
3. Nếu trạng thái không hợp lệ **không thể tồn tại theo contract**, câu hỏi “SUT xử lý quantity âm trong object đã validate” có thể đang thử hành vi không thể xảy ra bằng public API. Cần điều chỉnh mục tiêu kiểm thử, không dùng reflection hoặc phá invariant một cách tùy tiện.
4. Nếu chính builder chịu trách nhiệm validation, chuyển test exception sang builder, thay vì tuyên bố đang test service.

**“Fixture hợp lệ”** ở đây nghĩa là nó được tạo **thành công** và thỏa các tiền điều kiện khác của phép kiểm thử, không nhất thiết mọi trường đều là dữ liệu hợp lệ theo quy tắc mà SUT cần kiểm tra. Cần chỉ rõ lớp nào chịu trách nhiệm validate mỗi điều kiện.

## 8. Kiểm tra SUT thực sự được gọi

Giới hạn `assertThrows` là lớp bảo vệ đầu tiên. Trong vài ca phức tạp còn cần xác nhận **tương tác**:

```java
Order input = validOrderFixture();

IllegalStateException error = assertThrows(
        IllegalStateException.class,
        () -> service.submit(input));

verify(repository).save(input);
```

Đây là **mảnh JUnit + Mockito** giả định `service`, `repository` và phương thức `verify` đã được cấu hình. Chỉ thêm verification nếu **thực sự thuộc contract đang thử**. Ví dụ nếu nghiệp vụ bắt buộc validation **trước** khi ghi repository, thì test validation failure phải kiểm tra ngược lại `verifyNoInteractions(repository)` hoặc `verify(repository, never()).save(any())` tùy mục tiêu.

Không nên cố định mọi chi tiết triển khai bằng mock verification; test sẽ dễ vỡ sau refactor mà hành vi không thay đổi. Sự bảo đảm chính thường là **phạm vi Act hẹp + trạng thái/exception đúng hợp đồng**. Verification là bằng chứng bổ sung khi có ý nghĩa.

Nếu một mock được cấu hình ném đúng loại exception đang kỳ vọng, cần phân biệt bạn đang test **SUT chuyển/propagate lỗi dependency** hay đang vô tình chỉ test **mock throw**. Test thành công phải chứng minh policy của SUT (ví dụ dịch lỗi, fallback hợp lệ, rollback hoặc ghi state), không chỉ rằng stub tự ném được một exception.

## 9. Không để assertion nằm trong lambda kỳ vọng exception của SUT

Sai:

```java
assertThrows(AssertionError.class, () -> {
    Result result = sut.execute();
    assertEquals("OK", result.status());
});
```

Đây là mảnh JUnit **cố tình minh họa lỗi thiết kế**. Test đang chứng minh một assertion nào đó thất bại, nhưng có thể vô tình “đạt” vì test assertion bị lỗi chứ không phải vì SUT xử lý đúng. Ngay cả khi đang test một hàm assertion utility riêng, phải đặt tên và mục tiêu rõ.

Đối với test business thông thường:

```java
Result result = sut.execute();
assertEquals("OK", result.status());
```

Hoặc khi kỳ vọng lỗi từ SUT:

```java
BusinessException ex = assertThrows(
        BusinessException.class,
        () -> sut.execute());
assertEquals(ErrorCode.CONFLICT, ex.code());
```

Đây là các mảnh minh họa có kiểu/enum ứng dụng được định nghĩa. **Không gộp nhiều pha trong một lambda** nếu việc gộp có thể làm sai nguồn exception.

## 10. Lỗi trong `@BeforeEach` và `@BeforeAll`

JUnit Jupiter cung cấp lifecycle callbacks:

- `@BeforeAll`: chuẩn bị tài nguyên/chia sẻ trạng thái theo cấu hình vòng đời test class.
- `@BeforeEach`: chuẩn bị trước mỗi test.
- `@Test`: phần test body chứa Act và Assert.
- `@AfterEach` và `@AfterAll`: cleanup.

Ví dụ:

```java
@BeforeEach
void setup() {
    order = fixtureFactory.createOrder(); // Có thể lỗi.
}

@Test
void rejectsNegativeQuantity() {
    assertThrows(IllegalArgumentException.class,
            () -> service.calculate(order));
}
```

Đây là **mảnh JUnit 5** cần field `order`, `service`, `fixtureFactory` và các import. Nếu `setup()` ném `IllegalArgumentException`, **method test chưa được thực thi**. JUnit ghi nhận sự cố ở lifecycle setup; `assertThrows` trong method test không có cơ hội chứng minh hành vi của SUT.

Đây là một lợi ích của tách lifecycle, nhưng cũng tạo rủi ro nếu error bị che:

```java
@BeforeEach
void setup() {
    try {
        order = fixtureFactory.createOrder();
    } catch (Exception ignored) {
        order = null;
    }
}
```

Đây là **anti-pattern**: lỗi setup bị nuốt, rồi test thất bại với NPE hay lỗi không liên quan sâu hơn. Tốt hơn để setup thất bại **ở đúng vị trí**.

Một số lưu ý:
- Chỉ đặt **setup thực sự dùng chung** trong `@BeforeEach`; fixture đặc thù nên đặt trong test để dễ đọc.
- Không tạo side effect toàn cục không cần thiết ở `@BeforeAll` vì khi hỏng có thể ảnh hưởng nhiều test trong cùng class.
- Dùng cleanup đáng tin cậy cho tài nguyên; thất bại trong `@AfterEach` cần được xem là vấn đề riêng.
- Trạng thái shared giữa các test có thể gây lỗi ngẫu nhiên khi chạy song song; ưu tiên isolation.

## 11. Teardown lỗi khác với Act lỗi

Một test có thể hoàn thành SUT và assertion thành công, nhưng `@AfterEach` hoặc thao tác `close()` lại ném exception. Điều này không có nghĩa production behavior đang được test đã sai theo cùng cách.

Ví dụ:

```java
@Test
void processesOrder() throws Exception {
    try (TestDatabase db = startDatabase()) {
        Result result = sut.process(db);
        assertEquals(Result.SUCCESS, result);
    }
}
```

Đây là **mảnh thiết kế**, cần định nghĩa `TestDatabase` là `AutoCloseable`, `Result` và các API ứng dụng. Khi body thất bại rồi `db.close()` cũng thất bại, try-with-resources có thể giữ **body exception** làm primary, lỗi close trong `getSuppressed()`. Nếu body thành công nhưng close lỗi, lỗi close là primary được báo.

Vì vậy khi phân tích stack trace test:
- Xem exception chính xuất hiện từ dòng Arrange, Act, Assert hay cleanup.
- Xem `getSuppressed()` nếu có nhiều lỗi.
- Không sửa SUT để “qua test” khi lỗi là container teardown hoặc connection cleanup.
- Không nuốt cleanup exception một cách mù quáng; rò tài nguyên giữa các test có thể gây failure về sau.

Ngược lại, lỗi SUT không được phép bị biến mất chỉ vì cleanup cũng lỗi. `try-with-resources` hữu ích để giữ thông tin đó.

## 12. Mock/stub: lỗi cấu hình test và lỗi SUT có thể cùng loại

Một test có thể dùng Mockito:

```java
when(repository.load("A")).thenReturn(null);
assertThrows(NullPointerException.class,
        () -> service.process("A"));
```

Đây là mảnh JUnit/Mockito. Một `NullPointerException` có thể là bug trong SUT hoặc đơn giản do mock trả `null` trái contract. Để test hữu ích, phải xác định **lỗi nào được kỳ vọng**:
- Nếu đang test SUT xử lý dependency trả dữ liệu rỗng, assert một error code/fallback đúng nghiệp vụ, không mặc nhiên kỳ vọng NPE.
- Nếu đang test SUT từ chối input `null`, tạo input đó tách biệt khỏi mock setup.
- Nếu mock trả `null` do thiếu stub, hãy xem lại fixture, không vội sửa logic production.
- Nếu `when(...)` hoặc `thenAnswer(...)` có callback ném lỗi, nó có thể phát sinh trong Arrange, lúc mock được cấu hình, hoặc trong Act tùy thời điểm thực thi.

Một nguyên tắc quan trọng: **mock phải mô phỏng một dependency có hợp đồng rõ**, không tùy tiện khiến dependency “làm mọi thứ” để ép một exception xuất hiện.

## 13. Test fixtures có nhiều bước: phân pha tường minh

Với test tích hợp, Arrange có thể gồm tạo schema, seed dữ liệu, lấy token thử, cấu hình HTTP client, mở transaction và tạo request. Nếu gom tất cả trong một helper “magic”, lỗi setup rất khó chẩn đoán.

Có thể chia Arrange thành những bước có tên:

```java
TestDatabase db = startTestDatabase();
Customer customer = seedCustomer(db, "fixture-001");
Request request = newRequest(customer.id());

Response response = application.handle(request);

assertEquals(409, response.status());
```

Đây là mảnh integration test, cần các API ứng dụng định nghĩa. Nếu `seedCustomer` lỗi do duplicate key hoặc DB không kết nối được, test thất bại **trước Act**; không được đánh đồng nó với việc application trả 409. Mỗi bước chỉ nên tạo dữ liệu phục vụ đúng kịch bản.

Khi một test có nhiều nhánh lỗi, có thể gắn nhãn rõ bằng helper/tên biến, nhưng không nên che mọi lỗi bằng custom exception quá chung như “test failed”. Nếu muốn bọc lỗi setup, hãy giữ cause:

```java
try {
    seedData();
} catch (SQLException ex) {
    throw new TestSetupException("Cannot seed test database", ex);
}
```

Đây là mảnh code cần `TestSetupException` tự định nghĩa và import `java.sql.SQLException`. Bọc như vậy hữu ích tại boundary setup phức tạp; không cần bọc lại mọi lời gọi nhỏ hoặc đưa password/connection string vào message.

## 14. Parameterized tests và lỗi cung cấp dữ liệu

Với JUnit 5, dữ liệu thường đến từ `@MethodSource`, `@CsvSource` hoặc các nguồn tham số khác. Nếu phương thức cung cấp dữ liệu ném exception trong lúc tạo arguments, **test invocation chưa được hình thành hoặc chưa thực hiện Act như dự kiến**.

Ví dụ:

```java
static Stream<Arguments> cases() {
    return Stream.of(
            Arguments.of("SKU-1", -1),
            Arguments.of("SKU-2", -2));
}

@ParameterizedTest
@MethodSource("cases")
void rejectsNegativeQuantity(String sku, int quantity) {
    OrderLine line = new OrderLine(sku, quantity);
    assertThrows(IllegalArgumentException.class,
            () -> service.calculate(line));
}
```

Đây là mảnh JUnit Jupiter Parameterized Tests, yêu cầu JUnit params, imports `Stream` và `Arguments`, các class/field ứng dụng. Nếu `cases()` tính toán dữ liệu bằng helper có thể lỗi, lỗi ở **data source** khác với lỗi từ `service.calculate`.

Nên:
1. Dùng arguments đơn giản, có tên và dễ kiểm tra khi có thể.
2. Nếu data source phụ thuộc IO/DB, cân nhắc tách thành integration fixture thay vì làm phát sinh side effect khó chẩn đoán trong annotation source.
3. Đặt tên invocation rõ ràng để biết tập input nào thất bại.
4. Không cho một nguồn dữ liệu rỗng hoặc không hợp lệ tạo cảm giác toàn bộ yêu cầu đã được kiểm tra.

Đừng dùng `assertThrows` bao quanh chính lời gọi `cases()` trong test hành vi của service nếu mục tiêu không phải kiểm thử data source.

## 15. Nhiều exception xảy ra nối tiếp: lỗi đầu tiên và lỗi thứ cấp

Khi setup, SUT và teardown cùng có lỗi, việc test báo exception nào phụ thuộc **đường thực thi và cơ chế kiểm thử**:

- Nếu Arrange ném, Act không chạy.
- Nếu Act ném, những assertion theo sau trong cùng method thường không chạy.
- Nếu Arrange và Act thành công nhưng Assert ném `AssertionError`, đó là thất bại kiểm tra.
- Nếu cleanup ném sau một lỗi trước đó, `try-with-resources` có thể lưu cleanup trong `getSuppressed()`.
- Nếu `finally` tự ném exception mà không bảo toàn lỗi trước, nó có thể che lỗi gốc.
- JUnit extension/lifecycle có thể báo thêm lỗi; cách runner trình bày nhiều failure không nên được dùng thay cho phân tích stack trace và nguồn lỗi.

**Không chỉ nhìn tên class exception**. Một `IllegalArgumentException` từ fixture builder và một `IllegalArgumentException` từ SUT có thể giống loại nhưng khác nguyên nhân. Thông điệp, call stack, error code, origin và điều kiện tiền đề giúp phân loại.

Nếu cần một quan hệ lỗi ổn định để assert, ứng dụng nên có **exception domain hoặc error code** hơn là so khớp chuỗi message của thư viện/JDK vốn có thể thay đổi.

## 16. Xác minh nơi xảy ra lỗi bằng marker khi cần

Trong một test phức tạp, đôi khi cần chứng minh Act đã bắt đầu hoặc một lời gọi dependency đã xảy ra. Có thể dùng counter/test spy có kiểm soát:

```java
AtomicInteger invocationCount = new AtomicInteger();
Sut sut = new Sut(() -> {
    invocationCount.incrementAndGet();
    throw new DomainException("DEPENDENCY_FAILED");
});

DomainException error = assertThrows(
        DomainException.class,
        sut::execute);

assertEquals(1, invocationCount.get());
```

Đây là **mảnh code thiết kế**, không độc lập; cần `Sut`, `DomainException`, `AtomicInteger`, import và API constructor như đã minh họa. Nếu Arrange thất bại trước `assertThrows`, counter sẽ không được kiểm tra như một Act thành công. Khi Act chạy, counter giúp xác nhận đường gọi mong đợi được đi qua.

Không nên thêm marker khắp production code chỉ để test. Hãy ưu tiên test spy/mock có mục đích rõ và assert trạng thái/interaction quan trọng. Đồng thời, nếu SUT được thiết kế **không gọi dependency khi input invalid**, assert counter bằng `0` mới là phù hợp.

## 17. Bất đồng bộ: lỗi SUT có thể xảy ra sau Act ban đầu

Với `CompletableFuture` hoặc `ExecutorService`, lời gọi Act có thể chỉ **khởi động công việc** rồi trả Future. Exception xảy ra sau đó trong worker không tự được bắt bởi `assertThrows` quanh câu submit/schedule:

```java
CompletableFuture<String> future = sut.loadAsync(request);

CompletionException error = assertThrows(
        CompletionException.class,
        future::join);

assertInstanceOf(DependencyException.class, error.getCause());
```

Đây là mảnh JUnit 5 với SUT và types ứng dụng. **Arrange** tạo `request`, **Act** gọi `sut.loadAsync`, còn **Assert** quan sát stage qua `join`. `CompletionException` là wrapper thường gặp của `join()` khi future exceptional; nếu cancellation, nó có thể ném `CancellationException` trực tiếp. `Future.get()` dùng `ExecutionException` và có thể ném `InterruptedException` ở caller đang chờ.

Khi setup mock async, cần biết nó có ném **ngay khi cấu hình**, ném **đồng bộ khi Act gọi**, hay trả một future hoàn tất exceptionally **sau Act**. Không thể dùng cùng một `assertThrows` bao quanh mọi bước rồi kết luận đã test đúng callback.

Tránh dùng `Thread.sleep` làm cách duy nhất chờ lỗi async: test có thể flaky. Dùng future completion, latch, executor được kiểm soát và timeout phù hợp. Nếu caller bị interrupt trong lúc `get()`, đó là lỗi của **đường chờ trong test** nếu không phải tình huống hủy đang được kiểm thử.

## 18. Kiểm thử exception expected không có nghĩa chấp nhận mọi Throwable

`assertThrows(Exception.class, ...)` hay `assertThrows(RuntimeException.class, ...)` quá rộng có thể tạo false positive tương tự, kể cả khi chỉ bao Act. Ví dụ muốn kiểm tra lỗi validation mà SUT lại có bug `NullPointerException`; nếu chỉ mong `RuntimeException` thì bug cũng khiến test xanh.

Nên chọn:

- Kiểu exception đủ cụ thể theo contract, ví dụ `InvalidOrderException` hoặc `IllegalArgumentException` khi có ý nghĩa.
- `assertThrowsExactly` khi subclass khác sẽ là lỗi contract.
- Mã lỗi hoặc thuộc tính bổ sung ổn định, không lạm dụng exact message của SDK/JDK.
- Điều kiện trạng thái/tương tác sau exception, chẳng hạn database không bị ghi khi validation thất bại.
- Với retry/cancel, assert đúng số lần gọi và trạng thái cuối.

Tránh `assertThrows(Throwable.class, ...)` nếu mục tiêu chỉ là kiểm thử nghiệp vụ thông thường: nó có thể coi `AssertionError` hoặc lỗi nghiêm trọng khác là một “thành công” giả. Một số test framework internals có lý do bắt Throwable để ghi nhận test failure, nhưng đó **không phải pattern assertion phù hợp** cho mọi test ứng dụng.

## 19. Test setup không nên được làm cho “luôn thành công” bằng cách nuốt lỗi

Một anti-pattern:

```java
OrderLine prepare() {
    try {
        return fixtureFactory.create();
    } catch (Exception ignored) {
        return null;
    }
}
```

Đây là mảnh code có factory do ứng dụng định nghĩa. Nó chuyển lỗi fixture thành `null`, làm Act hoặc Assert ném `NullPointerException` ở nơi xa nguyên nhân thật.

Hướng sửa:
- Giữ exception setup gốc hoặc bọc với cause và thông điệp **không chứa dữ liệu nhạy cảm**.
- Tạo fixture nhỏ, rõ tiền điều kiện và kiểm thử riêng nếu helper phức tạp.
- Tách test database/fixtures khỏi network hoặc yếu tố thời gian không cần thiết.
- Đừng dùng giả định `assumeTrue` để tự động bỏ qua mọi lỗi setup; test có thể bị **aborted/skipped** thay vì phát hiện cấu hình hỏng.
- Với dependency bên ngoài thực sự tùy chọn, xác định rõ chính sách integration test và điều kiện môi trường; không ngụy trang hỏng setup thành test pass.

Một test chuẩn bị thành công không bảo đảm SUT hoạt động đúng; ngược lại, setup failure luôn cần được xem xét trước khi kết luận SUT có bug.

## 20. Ma trận tình huống kiểm thử và kết quả mong đợi

| Trường hợp | Điểm thất bại | Kết quả đúng |
| --- | --- | --- |
| Builder sai SKU, SUT chưa được gọi | Arrange | Test báo lỗi setup, không pass exception của SUT |
| Builder hợp lệ, SUT từ chối quantity âm | Act | `assertThrows` nhận đúng domain exception |
| Builder hợp lệ, SUT bất ngờ NPE | Act | Test kỳ vọng validation cụ thể phải FAIL |
| SUT thành công, assertion expected khác actual | Assert | Failure do assertion, không nhận nhầm là exception của SUT |
| `@BeforeEach` không kết nối được DB test | Lifecycle Arrange | Báo thất bại setup/lifecycle; không khẳng định SUT đã chạy |
| `@MethodSource` tạo dữ liệu sai | Parameter source | Báo lỗi nguồn dữ liệu, không phải lỗi Act |
| Mock config bị lỗi trước test body | Arrange | Test failure ở setup, không được nuốt |
| Stub trả exception đúng kiểu nhưng SUT không thực hiện policy | Act | Kiểm tra mapping/fallback/state, không chỉ loại exception |
| Future bị lỗi sau submit | Async completion | Quan sát `join()`/`get()` và cause |
| Cleanup ném sau Assert thành công | Teardown | Test có thể thất bại do cleanup; phân loại đúng |
| Act và `close()` đều lỗi | Act + Cleanup | Xem primary và `getSuppressed()` |
| Setup helper nuốt exception và trả null | Arrange | Sửa helper, không “chữa NPE” ở SUT khi nguồn lỗi là fixture |

Ma trận này hữu ích cho test review và lỗi flaky. Mục tiêu là chứng minh **hành vi của đúng đối tượng đang kiểm thử**, không chỉ làm một assertion có màu xanh.

## 21. Checklist trước khi chấp nhận một test exception

1. Tên test mô tả hành vi của **SUT**, không chỉ tên loại exception.
2. Arrange và Act nằm ở những đoạn mã dễ nhận diện.
3. `assertThrows` chỉ bao đúng thao tác được kỳ vọng ném, tránh constructor/fixture helper không liên quan.
4. Fixture được xây thành công và chỉ có những giá trị bất thường **cố ý cho SUT kiểm tra**.
5. Loại exception kỳ vọng đủ cụ thể; nếu cần, assert error code/cause/attribute ổn định.
6. Test xác minh SUT đã thực hiện hoặc **không thực hiện** những side effects quan trọng.
7. Lỗi `@BeforeEach`/`@BeforeAll`, data provider, mock setup không bị nuốt.
8. Cleanup/teardown errors được phân biệt với Act failure; kiểm tra suppressed khi thích hợp.
9. Test async phân biệt lỗi đồng bộ lúc submit với exceptional completion về sau.
10. Không dùng `catch(Exception)` rộng hoặc `assertThrows(Throwable.class)` để “cho test xanh”.
11. Khi test thất bại, stack trace cho biết **pha** và **nguồn lỗi**; không tự đổi production code chỉ vì gặp cùng một loại exception.
12. Với integration test, test data, transaction và dependency được cô lập, có cleanup và timeout phù hợp.

**Ghi nhớ:** một phép `assertThrows` thành công chỉ chứng minh **một exception phù hợp đã xuất hiện trong lambda**, không chứng minh **đúng đoạn mã cần kiểm thử** đã ném nó. Muốn test đáng tin, hãy tách Arrange khỏi Act, tạo fixture có chủ đích, khoanh hẹp điểm mong đợi exception và xác minh hành vi đúng hợp đồng.


</details>

- [Quay lại đầu trang](#back-to-top)
