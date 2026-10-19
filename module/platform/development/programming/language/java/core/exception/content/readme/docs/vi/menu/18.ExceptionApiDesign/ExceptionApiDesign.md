<a id="back-to-top"></a>

# Thiết kế API

## Menu
- [Định nghĩa trạng thái hợp lệ trước và sau khi phương thức thất bại](#exception-api-design-001)
- [Chọn checked hay unchecked theo hợp đồng và khả năng xử lý](#exception-api-design-002)
- [Dùng loại ngoại lệ cụ thể thay vì throws Exception](#exception-api-design-003)
- [Phân biệt lỗi tham số, lỗi trạng thái và lỗi từ phụ thuộc](#exception-api-design-004)
- [Chuyển đổi ngoại lệ tại ranh giới trừu tượng](#exception-api-design-005)
- [Giữ cause khi chuyển lỗi kỹ thuật thành lỗi có ý nghĩa với bên gọi](#exception-api-design-006)
- [Tài liệu hóa bằng @throws và điều kiện phát sinh](#exception-api-design-007)
- [Chọn ngoại lệ, Optional hay kiểu kết quả có cấu trúc](#exception-api-design-008)
- [Không để chi tiết thư viện phụ thuộc trở thành hợp đồng công khai ngoài ý muốn](#exception-api-design-009)
- [Tác động tương thích khi thay đổi hợp đồng ngoại lệ](#exception-api-design-010)

## <a id="exception-api-design-001">Định nghĩa trạng thái hợp lệ trước và sau khi phương thức thất bại</a>

<details>
<summary>Xem chi tiết</summary>

# Định nghĩa trạng thái hợp lệ trước và sau khi phương thức thất bại

Một API Java không chỉ cần trả kết quả đúng khi **thành công**. Khi một lời gọi ném exception giữa chừng, đối tượng còn sử dụng được không? Field nào được phép đổi? Tài nguyên có được đóng không? Caller có thể thử lại không? Một thao tác đã ghi dữ liệu ra file, database hoặc dịch vụ bên ngoài có thực sự được hoàn tác không?

Chủ đề **“Định nghĩa trạng thái hợp lệ trước và sau khi phương thức thất bại”** là phần cốt lõi của thiết kế API có khả năng dự đoán. Nó kết hợp **invariant**, **precondition**, **postcondition**, **exceptional postcondition**, **mức bảo đảm an toàn khi có exception**, quản lý tài nguyên và kiểm thử đường lỗi.

Ví dụ lời gọi `wallet.withdraw(200)` từ ví chỉ có 100 đơn vị:

- Caller có vi phạm tiền điều kiện không? Nếu có, method ném loại exception nào?
- Sau khi ném, số dư còn 100 hay bị đổi thành giá trị âm?
- Nếu method đã gửi một yêu cầu thanh toán ra ngoài trước khi lỗi, nó có thể khẳng định giao dịch chưa xảy ra không?
- Nếu cần gọi lại, phải sửa input, sửa state, hay xác minh side effect trước?

**Hợp đồng tốt** nói rõ cả trạng thái **trước lời gọi**, trạng thái **sau thành công**, và trạng thái **sau thất bại**. Cú pháp `throws` hay việc có một khối `catch` **không tự đem lại** những bảo đảm đó.

Các ví dụ tập trung Java 17+, độc lập và có lệnh chạy/stdout khi được đánh dấu là chương trình đầy đủ. Những đoạn minh họa được ghi chú rõ, không phải tất cả đều có thể chạy một mình.

## 1. Ba lớp hợp đồng: trước, sau thành công, sau thất bại

Xét một phương thức thay đổi trạng thái:

```java
account.withdraw(amount);
```

Đây là **lời gọi minh họa**; biến và lớp cần được định nghĩa. Hợp đồng nên tách:

1. **Precondition (tiền điều kiện):** điều caller phải thỏa trước khi gọi. Ví dụ `amount > 0`.
2. **Normal postcondition (hậu điều kiện thành công):** nếu method trả bình thường, điều gì đúng? Ví dụ số dư giảm đúng bằng `amount`.
3. **Exceptional postcondition (hậu điều kiện khi thất bại):** nếu method ném exception, điều gì vẫn được bảo đảm? Ví dụ số dư không đổi với lỗi validation; hoặc object vẫn ở một trạng thái `FAILED` được công bố.

Bên cạnh đó, **invariant** là điều phải giữ ở mọi trạng thái ổn định và quan sát được của đối tượng. Ví dụ `balance >= 0` hay `start <= end`.

Một bản mô tả tối thiểu:

| Khía cạnh | Hợp đồng của `withdraw` |
| --- | --- |
| Trạng thái trước hợp lệ | `balance >= 0` |
| Đối số hợp lệ | `amount > 0` |
| Thành công | `newBalance = oldBalance - amount` |
| Đối số không hợp lệ | Ném `IllegalArgumentException`; `balance` không đổi |
| Không đủ tiền | Ném `IllegalStateException` hoặc domain failure theo thiết kế; `balance` không đổi |
| Tài nguyên/phụ thuộc lỗi | Quy tắc riêng, không được suy là đã rollback giao dịch bên ngoài |

Vấn đề quan trọng là **hậu điều kiện thất bại phải cụ thể**, không thể chỉ viết “có lỗi thì ném exception”.

## 2. Invariant là gì và cần giữ ở thời điểm nào?

**Invariant** là điều kiện về trạng thái của đối tượng cần luôn đúng tại các điểm mà hệ thống công bố trạng thái là hợp lệ.

Ví dụ một khoảng số:

```text
lower <= upper
```

Một đối tượng `Range` có thể dùng hai field `lower` và `upper`. Nếu method cập nhật một field trước rồi sau đó ném exception trước khi cập nhật field còn lại, invariant có thể bị phá.

**Phân biệt trạng thái trung gian và trạng thái quan sát được:**

- Trong một thao tác nội bộ, code có thể tính toán nhiều giá trị tạm.
- Trước khi phương thức trả hoặc ném, trạng thái công bố ra ngoài phải tuân theo bảo đảm đã ghi.
- Trong môi trường nhiều luồng, một trạng thái trung gian **không được lộ** qua các lời gọi đồng thời nếu class hứa thread safety/invariant luôn quan sát nhất quán.
- Nếu object hỗ trợ callback/reentrancy, ngay trong một method, callback có thể quan sát trạng thái chưa hoàn tất nếu bạn gọi callback giữa các bước cập nhật.

Vì vậy invariant không đơn thuần là một biểu thức `if`; nó gắn với **quy tắc xuất bản và quan sát trạng thái**.

## 3. Các mức bảo đảm khi có exception

Trong thiết kế phần mềm thường phân biệt ba mức *exception safety guarantee*:

| Mức | Khi method thất bại | Ví dụ ý nghĩa |
| --- | --- | --- |
| **Basic guarantee** | Các invariant vẫn đúng, tài nguyên được quản lý; trạng thái có thể đã đổi | Đã ghi một số bản ghi thành công trước khi dừng |
| **Strong guarantee** | Thất bại không làm thay đổi trạng thái quan sát được thuộc phạm vi cam kết (*commit-or-no-change*) | Cấu hình cũ giữ nguyên nếu cấu hình mới không hợp lệ |
| **No-throw guarantee** | Thao tác được thiết kế không để exception thoát ra trong những điều kiện mà hợp đồng bao phủ | Một getter thuần, đơn giản, không phụ thuộc I/O |

Đây là thuật ngữ **thiết kế**, không phải ba interface bắt buộc do Java cung cấp. Java **không tự động kiểm chứng** rằng một class đã đạt mức mà bạn công bố.

Cần rất cẩn thận với phạm vi:

- Strong guarantee về **field trong một object** không có nghĩa đã rollback **log, email, file, database, request HTTP**.
- Basic guarantee **không** cho phép object rơi vào trạng thái bất hợp lệ; nó chỉ cho phép một số thay đổi đã xảy ra nhưng **invariant vẫn đúng**.
- No-throw guarantee là một **lời hứa thiết kế về ngữ cảnh hợp lệ**, không có nghĩa không bao giờ có `Error` nghiêm trọng như `OutOfMemoryError` hay trục trặc JVM. Không dùng `catch (Throwable)` để giả vờ bảo đảm no-throw trong mọi trường hợp.

## 4. “Trạng thái không đổi” phải định nghĩa theo phạm vi

Khi công bố “nếu thất bại thì trạng thái không đổi”, hãy xác định **đối tượng và tác động nào** nằm trong lời hứa:

- Chỉ field của một instance?
- Cả collection do instance sở hữu?
- Những object mutable được tham chiếu?
- Dữ liệu đã ghi xuống file?
- Thay đổi trong transaction database?
- Side effect gửi sang hệ thống khác?
- Callback/listener đã nhận thông báo?
- Dữ liệu đã xuất bản cho thread khác?

Một class có thể giữ nguyên `this.state` nhưng đã:

```java
listener.onChanged(newValue);
throw new IllegalStateException("commit chưa hoàn tất");
```

Đây là **mã minh họa**. Dù field nội bộ chưa đổi, **listener đã quan sát một side effect**. Nói method này có strong guarantee toàn hệ thống sẽ không chính xác.

Hợp đồng nên viết cụ thể: “Nếu validation thất bại, cấu hình đang được sử dụng không đổi; không gọi listener”. Hoặc: “Nếu quá trình gửi thất bại, một số byte có thể đã được gửi, người gọi phải đối chiếu trạng thái trước khi retry”.

## 5. Ví dụ sai: sửa trạng thái trước rồi mới kiểm tra

Một triển khai nguy hiểm của cặp giá trị `lower`, `upper`:

```java
void setBounds(int newLower, int newUpper) {
    lower = newLower;
    if (newLower > newUpper) {
        throw new IllegalArgumentException("lower > upper");
    }
    upper = newUpper;
}
```

Đây là **phương thức minh họa** trong một class có hai field. Nếu ban đầu khoảng là `[2, 8]` rồi gọi `setBounds(10, 4)`, method gán `lower = 10` trước khi ném exception, trong khi `upper` vẫn là `8`. Trạng thái thành `[10, 8]`: **bất biến `lower <= upper` bị phá**.

Đây không chỉ là vi phạm strong guarantee mà có thể còn **không đạt basic guarantee**. Sau khi catch, các method khác có thể hoạt động sai vì đối tượng đang bất hợp lệ.

Cách sửa đơn giản: **kiểm tra toàn bộ các điều kiện có thể thất bại trước khi gán**, nếu phép commit cuối không thể thất bại trong phạm vi thông thường.

## 6. Chương trình Java 17: đối chiếu cách cập nhật sai và đúng

Lưu **`BoundsFailureDemo.java`**:

```java
public class BoundsFailureDemo {
    static class UnsafeRange {
        private int lower = 2;
        private int upper = 8;

        void setBounds(int a, int b) {
            lower = a;
            if (a > b) {
                throw new IllegalArgumentException("lower > upper");
            }
            upper = b;
        }

        String view() {
            return "[" + lower + ", " + upper + "]";
        }
    }

    static class SafeRange {
        private int lower = 2;
        private int upper = 8;

        void setBounds(int a, int b) {
            if (a > b) {
                throw new IllegalArgumentException("lower > upper");
            }
            lower = a;
            upper = b;
        }

        String view() {
            return "[" + lower + ", " + upper + "]";
        }
    }

    public static void main(String[] args) {
        UnsafeRange unsafe = new UnsafeRange();
        SafeRange safe = new SafeRange();

        try {
            unsafe.setBounds(10, 4);
        } catch (IllegalArgumentException e) {
            System.out.println("Unsafe bị từ chối");
        }
        try {
            safe.setBounds(10, 4);
        } catch (IllegalArgumentException e) {
            System.out.println("Safe bị từ chối");
        }

        System.out.println("Unsafe sau lỗi: " + unsafe.view());
        System.out.println("Safe sau lỗi: " + safe.view());
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 --release 17 BoundsFailureDemo.java
java BoundsFailureDemo
```

**Stdout chính xác:**

```text
Unsafe bị từ chối
Safe bị từ chối
Unsafe sau lỗi: [10, 8]
Safe sau lỗi: [2, 8]
```

Trong `UnsafeRange`, đối tượng vẫn giữ giá trị mới của `lower` dù thao tác bị từ chối và invariant đã sai. `SafeRange` kiểm tra trước, sau đó mới gán, nên lỗi validation giữ nguyên trạng thái.

**Giới hạn:** `SafeRange` ở đây là ví dụ **đơn luồng không có callback**. Việc gán hai field riêng rẽ không tự bảo đảm thread khác luôn quan sát một snapshot nhất quán. Vấn đề concurrent observation cần thiết kế riêng.

## 7. Tiền điều kiện: lỗi thuộc trách nhiệm caller

Một phương thức nên công bố đối số nào hợp lệ:

```java
/**
 * @param amount số tiền nguyên dương
 * @throws IllegalArgumentException nếu amount không dương
 */
public void deposit(long amount) {
    if (amount <= 0) {
        throw new IllegalArgumentException(
                "amount phải lớn hơn 0");
    }
    // ...
}
```

Đây là **mã minh họa**, phần cập nhật cần được triển khai. `IllegalArgumentException` mô tả **tham số caller truyền vi phạm tiền điều kiện**. Nó là unchecked nên Java không buộc khai báo `throws` trong chữ ký, nhưng JavaDoc `@throws` vẫn hữu ích.

Nếu một tham số null không được chấp nhận, có thể dùng `Objects.requireNonNull` theo hợp đồng. Nếu object ở trạng thái chưa mở/đã đóng, `IllegalStateException` thường phản ánh **vòng đời/trạng thái** chính xác hơn `IllegalArgumentException`.

Quan trọng: **kiểm tra tiền điều kiện càng sớm càng tốt, trước side effect**. Khi validation thất bại, API nên có exceptional postcondition dễ hiểu, thường là “không thay đổi trạng thái”.

## 8. Hậu điều kiện thành công phải tương ứng với hậu điều kiện lỗi

Xét ví tiền:

- **Trước:** `balance >= 0`.
- **Input:** `amount > 0`.
- **Thành công:** `newBalance = oldBalance - amount`, vẫn không âm.
- **Không đủ tiền:** không trừ bất kỳ phần nào, ném lỗi hoặc trả thất bại theo hợp đồng.
- **Validation sai:** không thay đổi.
- **Nếu dữ liệu thuộc giao dịch bên ngoài:** cần cam kết riêng về persistence.

Một contract không nên viết mơ hồ “nếu có lỗi thì trạng thái có thể bất kỳ”. Nếu không thể cam kết strong guarantee, hãy nói rõ **mức basic**, những tác động đã có thể xảy ra và thao tác phục hồi tương ứng.

Ngoại lệ là tín hiệu điều khiển về **thất bại của lời gọi**, nhưng không tự miêu tả tình trạng state. Hợp đồng API mới quyết định người dùng có thể tiếp tục như thế nào.

## 9. Ví dụ Java 17: bảo toàn số dư khi rút thất bại

Lưu **`WalletContractDemo.java`**:

```java
public class WalletContractDemo {
    static class Wallet {
        private long balance;

        Wallet(long initial) {
            if (initial < 0) {
                throw new IllegalArgumentException(
                        "initial phải không âm");
            }
            balance = initial;
        }

        long balance() {
            return balance;
        }

        void withdraw(long amount) {
            if (amount <= 0) {
                throw new IllegalArgumentException(
                        "amount phải dương");
            }
            if (amount > balance) {
                throw new IllegalStateException("Không đủ tiền");
            }
            balance -= amount;
        }
    }

    public static void main(String[] args) {
        Wallet wallet = new Wallet(100);
        try {
            wallet.withdraw(150);
        } catch (IllegalStateException e) {
            System.out.println("Từ chối: " + e.getMessage());
        }
        System.out.println("Sau từ chối: " + wallet.balance());

        wallet.withdraw(30);
        System.out.println("Sau thành công: " + wallet.balance());

        try {
            wallet.withdraw(-5);
        } catch (IllegalArgumentException e) {
            System.out.println("Input sai: " + e.getMessage());
        }
        System.out.println("Cuối: " + wallet.balance());
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 WalletContractDemo.java
java WalletContractDemo
```

**Stdout chính xác:**

```text
Từ chối: Không đủ tiền
Sau từ chối: 100
Sau thành công: 70
Input sai: amount phải dương
Cuối: 70
```

Trong trường hợp này, **validation xảy ra trước phép trừ** và `long` không bị overflow ở phép trừ vì `amount > 0` và `amount <= balance`, với `balance` luôn không âm trong class đơn giản. Nếu sau này thêm các method cộng số dư, vẫn cần kiểm soát overflow để invariant giữ đúng.

Bài học là **lỗi không để lại số dư âm hay trừ một phần**. Nó chưa phải ứng dụng ví điện tử thực với nhiều luồng, lưu trữ hoặc giao dịch ngân hàng.

## 10. Kiểm tra overflow cũng thuộc bảo đảm trạng thái

Một số phép tính Java `int` và `long` có thể **overflow mà không ném exception tự động**, tạo ra trạng thái sai.

Ví dụ nạp vào một số dư gần `Long.MAX_VALUE`:

```java
balance += amount;
```

Phép cộng có thể tràn thành số âm, phá invariant. Một cách mạnh hơn là tính trước bằng `Math.addExact`:

```java
long next = Math.addExact(balance, amount);
if (next < 0) {
    throw new IllegalStateException("Số dư không hợp lệ");
}
balance = next;
```

Đây là **mã minh họa**, giả định đã kiểm tra `amount > 0` và `balance >= 0` trước đó. `Math.addExact` ném `ArithmeticException` nếu phép cộng tràn; vì chưa gán, trạng thái cũ được giữ.

Không nên hiểu rằng **mọi phép tính trung gian đều an toàn chỉ vì không có checked exception**. Validation có thể ném unchecked exception và những phép tính im lặng overflow cũng cần được kiểm soát.

## 11. Ví dụ Java 17: phép cộng tràn nhưng state vẫn nguyên

Lưu **`OverflowGuaranteeDemo.java`**:

```java
public class OverflowGuaranteeDemo {
    static class Counter {
        private long value;

        Counter(long initial) {
            value = initial;
        }

        void add(long amount) {
            if (amount < 0) {
                throw new IllegalArgumentException(
                        "amount phải không âm");
            }
            long next = Math.addExact(value, amount);
            value = next;
        }

        long value() {
            return value;
        }
    }

    public static void main(String[] args) {
        Counter counter = new Counter(Long.MAX_VALUE - 1);
        counter.add(1);
        System.out.println("Đạt max: "
                + (counter.value() == Long.MAX_VALUE));

        try {
            counter.add(1);
        } catch (ArithmeticException e) {
            System.out.println("Bắt overflow");
        }

        System.out.println("Giữ max: "
                + (counter.value() == Long.MAX_VALUE));
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 --release 17 OverflowGuaranteeDemo.java
java OverflowGuaranteeDemo
```

**Stdout chính xác:**

```text
Đạt max: true
Bắt overflow
Giữ max: true
```

`Math.addExact` tính và kiểm tra **trước commit**. Nếu phép tính ném `ArithmeticException`, dòng gán `value = next` không chạy, giữ trạng thái cũ.

Nếu dùng `value += amount`, tràn số có thể làm trạng thái vi phạm invariant mà không hề có exception để catch.

## 12. Pattern “validate → prepare → commit”

Để đạt strong guarantee cho dữ liệu nội bộ, một cách thực tế:

1. **Validate:** kiểm tra precondition và state hiện tại.
2. **Prepare:** tạo dữ liệu/giá trị thay thế, tính mọi phép có thể lỗi trong biến local hoặc object tạm.
3. **Commit:** chỉ cập nhật trạng thái công bố khi mọi bước chuẩn bị đã thành công.

Ví dụ:

```java
void replace(List<String> incoming) {
    Objects.requireNonNull(incoming, "incoming");
    List<String> candidate = List.copyOf(incoming);
    this.items = candidate;
}
```

Đây là **method minh họa**, cần import `List`, `Objects` và field `items`. `List.copyOf` có thể ném nếu input null/bao gồm phần tử null, hoặc gặp lỗi phù hợp trong quá trình tạo bản sao. Vì `this.items` chỉ được gán **sau khi candidate hoàn tất**, lỗi trước commit giữ nguyên reference cũ.

**Không giả định List.copyOf luôn snapshot chính xác một collection bị thread khác sửa đồng thời**; cần đầu vào ổn định hoặc đồng bộ phù hợp. Và `List.copyOf` chỉ tạo cấu trúc list không chỉnh sửa được qua API của nó; nếu phần tử bên trong là object mutable, chúng không tự được deep-copy.

## 13. Ví dụ Java 17: cập nhật collection kiểu copy-then-swap

Lưu **`CopyThenSwapDemo.java`**:

```java
import java.util.List;
import java.util.Objects;

public class CopyThenSwapDemo {
    static class Registry {
        private List<String> names = List.of("old");

        List<String> snapshot() {
            return names;
        }

        void replace(List<String> incoming) {
            Objects.requireNonNull(incoming, "incoming");
            List<String> candidate = List.copyOf(incoming);
            names = candidate;
        }
    }

    public static void main(String[] args) {
        Registry registry = new Registry();

        try {
            registry.replace(java.util.Arrays.asList("A", null));
        } catch (NullPointerException e) {
            System.out.println("Bản cập nhật bị từ chối");
        }

        System.out.println("Sau lỗi: " + registry.snapshot());

        registry.replace(List.of("A", "B"));
        System.out.println("Sau thành công: " + registry.snapshot());
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 CopyThenSwapDemo.java
java CopyThenSwapDemo
```

**Stdout chính xác:**

```text
Bản cập nhật bị từ chối
Sau lỗi: [old]
Sau thành công: [A, B]
```

Ở đường lỗi, `List.copyOf` từ chối phần tử null và **chưa có thay đổi nào được gán cho `names`**. Ở đường thành công, chỉ một phép gán reference xuất bản bản list mới.

Đây là strong guarantee đối với **danh sách tên được lưu bên trong một instance trong ví dụ đơn luồng**; nó không tự đồng bộ khi nhiều thread truy cập field.

## 14. Không lộ trạng thái mutable qua getter

Strong guarantee có thể bị phá bởi **aliasing**, ngay cả khi setter được triển khai rất cẩn thận.

Ví dụ có vấn đề:

```java
class Store {
    private final List<String> values = new ArrayList<>();

    List<String> getValues() {
        return values;
    }
}
```

Đây là **class minh họa** cần import `List`, `ArrayList`. Caller nhận đúng reference tới list nội bộ rồi có thể viết `store.getValues().clear()` mà không đi qua validation.

Nếu invariant là “list luôn chứa ít nhất một item”, getter mutable làm invariant mất bảo vệ.

Các lựa chọn:

- Trả **immutable snapshot** như `List.copyOf(values)` nếu nội dung phù hợp.
- Trả view không chỉnh sửa được như `Collections.unmodifiableList` khi muốn phản ánh thay đổi của list nguồn nhưng không cho caller sửa qua view.
- Dùng object immutable ngay từ đầu và thay thế toàn bộ reference khi commit.
- Kiểm soát sở hữu/ownership của mọi reference mutable được truyền qua API.
- Nếu các phần tử cũng mutable, cân nhắc deep copy hoặc một representation bất biến phù hợp.

Hậu điều kiện thất bại không bền vững nếu caller có đường khác để sửa state không kiểm soát.

## 15. Đối tượng immutable giúp đơn giản hóa exception safety

Với một value object bất biến, method “thay đổi” có thể tạo **object mới**. Nếu xây object mới thất bại, object cũ tự nhiên còn nguyên.

Ví dụ Java 17:

```java
record PortRange(int min, int max) {
    PortRange {
        if (min < 1 || max > 65535 || min > max) {
            throw new IllegalArgumentException("PortRange sai");
        }
    }

    PortRange withMax(int nextMax) {
        return new PortRange(min, nextMax);
    }
}
```

Đây là **record minh họa**, có thể đặt trong một file Java riêng tên `PortRange.java`. Một `PortRange(1000, 2000)` sẽ không đổi khi `withMax(500)` ném `IllegalArgumentException`.

Lợi ích:

- Không có thao tác sửa một phần trạng thái của object gốc.
- Có thể chuẩn bị object mới hoàn chỉnh trước khi xuất bản.
- Dễ dùng với cache, snapshot, so sánh và kiểm thử.
- Giảm rủi ro callback quan sát trạng thái nửa chừng.

Giới hạn: record có các component tham chiếu mutable **không tự trở thành deep immutable**. Nếu chứa `List<MutableItem>`, cần thiết kế snapshot/deep copy phù hợp.

## 16. Ví dụ Java 17: cấu hình bất biến và thất bại khi tạo bản mới

Lưu **`ImmutableConfigDemo.java`**:

```java
public class ImmutableConfigDemo {
    record Config(String host, int port) {
        Config {
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("host rỗng");
            }
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("port sai");
            }
        }

        Config withPort(int newPort) {
            return new Config(host, newPort);
        }
    }

    public static void main(String[] args) {
        Config current = new Config("localhost", 8080);

        try {
            current = current.withPort(-1);
        } catch (IllegalArgumentException e) {
            System.out.println("Tạo cấu hình mới thất bại");
        }

        System.out.println("Cấu hình cũ: " + current);

        current = current.withPort(9090);
        System.out.println("Cấu hình mới: " + current);
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 ImmutableConfigDemo.java
java ImmutableConfigDemo
```

**Stdout chính xác:**

```text
Tạo cấu hình mới thất bại
Cấu hình cũ: Config[host=localhost, port=8080]
Cấu hình mới: Config[host=localhost, port=9090]
```

Phép gán `current = current.withPort(-1)` không xảy ra vì vế phải ném exception trước khi tạo kết quả. Nếu tạo cấu hình mới thành công, biến `current` mới được trỏ tới object mới. Đây là mẫu **prepare then publish** tự nhiên khi dùng immutable data.

## 17. Khi phép commit cũng có thể thất bại

Không phải mọi thao tác commit là một phép gán field đơn giản. Nếu commit phải:

- ghi qua filesystem,
- gọi database,
- gửi message ra broker,
- cập nhật nhiều object chia sẻ,
- gọi listener có thể ném exception,
- cấp phát thêm bộ nhớ,
- cập nhật cache rồi công bố sự kiện,

thì **commit có thể thất bại giữa chừng**. Khi ấy pattern “validate → prepare → commit” cần được mở rộng.

Một số chiến lược:

1. **Giảm điểm có thể thất bại trong commit:** chuẩn bị dữ liệu và cấp phát trước, commit chỉ bằng cập nhật nguyên tử khi có thể.
2. **Transaction** của hệ thống hỗ trợ, với phạm vi rõ ràng.
3. **Compensation** có chủ đích nếu không thể rollback thực sự (nhưng compensation cũng có thể thất bại).
4. **State machine** công bố trạng thái `IN_PROGRESS`, `FAILED` hoặc `UNKNOWN` để caller không hiểu nhầm.
5. **Idempotency keys** và đối chiếu trạng thái khi side effect phía xa có thể đã xảy ra.

Đừng dùng `catch (Exception e) { oldValue = backup; }` rồi tuyên bố strong guarantee nếu phần còn lại của commit đã có side effect không thể hoàn tác.

## 18. Basic guarantee: trạng thái thay đổi một phần nhưng vẫn hợp lệ

Không phải API nào cũng cần strong guarantee. Một bộ xử lý import cho phép ghi thành công các hàng đầu tiên rồi dừng ở hàng lỗi có thể cung cấp **basic guarantee**:

- Mọi bản ghi đã commit là hợp lệ.
- Dữ liệu chưa được commit không bị coi là đã xử lý.
- Vòng đời tài nguyên được quản lý.
- Sau lỗi, đối tượng vẫn hoạt động theo invariant và có thể báo trạng thái đã xử lý một phần.
- Caller được công bố rõ **partial success**.

Ví dụ conceptual:

```java
for (Record record : records) {
    validate(record);
    storeOne(record); // Mỗi record có commit riêng.
}
```

Đây là **mã minh họa**, với các lớp/phương thức phụ chưa được định nghĩa. Nếu item thứ 5 thất bại, 4 item trước đã được lưu; method có thể ném exception, nhưng hệ thống không nên tuyên bố “không thay đổi gì”.

Basic guarantee không hề là thiết kế tồi nếu nó được **công bố rõ**, giữ invariant và phù hợp nghiệp vụ. Sai lầm là để caller **mặc nhiên kỳ vọng all-or-nothing** trong khi triển khai chỉ đảm bảo partial progress.

## 19. Ví dụ Java 17: partial progress vẫn giữ invariant

Lưu **`PartialImportDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class PartialImportDemo {
    static class Importer {
        private final List<String> accepted = new ArrayList<>();

        void importAll(List<String> input) {
            for (String item : input) {
                if (item.isBlank()) {
                    throw new IllegalArgumentException(
                            "Item không được rỗng");
                }
                accepted.add(item);
            }
        }

        List<String> snapshot() {
            return List.copyOf(accepted);
        }
    }

    public static void main(String[] args) {
        Importer importer = new Importer();

        try {
            importer.importAll(List.of("A", "B", "", "C"));
        } catch (IllegalArgumentException e) {
            System.out.println("Import dừng: " + e.getMessage());
        }

        System.out.println("Đã nhận: " + importer.snapshot());
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 PartialImportDemo.java
java PartialImportDemo
```

**Stdout chính xác:**

```text
Import dừng: Item không được rỗng
Đã nhận: [A, B]
```

Các item `"A"` và `"B"` đã được thêm. Khi gặp chuỗi rỗng, exception được ném **trước khi thêm item lỗi**; list nội bộ vẫn chỉ chứa dữ liệu hợp lệ. Caller phải biết `importAll` có thể **commit từng phần tử**, không giả định thất bại làm list quay về trạng thái ban đầu.

Mẫu này không thích hợp với hợp đồng nhập khẩu dữ liệu kiểu all-or-nothing, trừ khi được thay bằng staging và commit một lần hoặc transaction.

## 20. Strong guarantee cho batch: chuẩn bị hết rồi mới công bố

Nếu nghiệp vụ yêu cầu **hoặc nhận toàn bộ batch, hoặc không thay đổi gì**, có thể chuẩn bị một cấu trúc mới trước:

```java
void importAll(List<String> input) {
    List<String> candidate = new ArrayList<>(accepted);
    for (String item : input) {
        if (item.isBlank()) {
            throw new IllegalArgumentException("Item rỗng");
        }
        candidate.add(item);
    }
    accepted = List.copyOf(candidate);
}
```

Đây là **method minh họa**, với `accepted` là field có thể gán lại kiểu `List<String>` và import `List`, `ArrayList`. Nếu validation hoặc copy thất bại trước `accepted = ...`, field cũ không đổi.

Lưu ý:

- `accepted` không được khai báo `final` nếu muốn thay reference.
- Nếu các item mutable, việc sao chép list không tự sao chép sâu các item.
- Nếu gọi callback trong lúc prepare, callback có thể phát sinh side effect bên ngoài.
- Nếu nhiều thread có thể thay đổi `accepted`, cần đồng bộ hoặc cơ chế update nguyên tử.
- Trong hệ thống persistence, copy-then-swap của bộ nhớ **không rollback database hay file**.

Mục tiêu không chỉ là “dùng một list tạm”, mà là **đặt mọi công việc có thể thất bại trước ranh giới commit**.

## 21. Chương trình Java 17: batch thất bại không thêm phần tử nào

Lưu **`AtomicImportDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class AtomicImportDemo {
    static class Importer {
        private List<String> accepted = List.of("old");

        void importAll(List<String> input) {
            List<String> candidate = new ArrayList<>(accepted);
            for (String item : input) {
                if (item == null || item.isBlank()) {
                    throw new IllegalArgumentException(
                            "Item không hợp lệ");
                }
                candidate.add(item);
            }
            accepted = List.copyOf(candidate);
        }

        List<String> snapshot() {
            return accepted;
        }
    }

    public static void main(String[] args) {
        Importer importer = new Importer();

        try {
            importer.importAll(List.of("A", "", "C"));
        } catch (IllegalArgumentException e) {
            System.out.println("Batch thất bại");
        }
        System.out.println("Sau lỗi: " + importer.snapshot());

        importer.importAll(List.of("A", "B"));
        System.out.println("Sau thành công: " + importer.snapshot());
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 AtomicImportDemo.java
java AtomicImportDemo
```

**Stdout chính xác:**

```text
Batch thất bại
Sau lỗi: [old]
Sau thành công: [old, A, B]
```

Mọi thay đổi được xây trong `candidate`. Chỉ khi danh sách hoàn tất và `List.copyOf` thành công, field `accepted` được cập nhật. Đây là strong guarantee cho **state nội bộ được quản lý trong ví dụ đơn luồng**.

## 22. Exception safety không đồng nghĩa với thread safety

Một method có strong guarantee khi gọi đơn luồng **không tự động an toàn** khi nhiều thread chạy đồng thời.

Ví dụ hai field:

```java
if (newLower > newUpper) {
    throw new IllegalArgumentException();
}
lower = newLower;
upper = newUpper;
```

Đây là **mã minh họa**. Một thread khác có thể đọc `lower` sau khi gán nhưng trước khi `upper` được gán, quan sát trạng thái trung gian không phù hợp. Nếu trường hợp này vi phạm hợp đồng, cần thêm đồng bộ.

Các lựa chọn thiết kế:

- Bảo vệ cả thao tác đọc và ghi bằng **cùng lock** (`synchronized` hoặc `Lock`).
- Gói trạng thái vào **một immutable snapshot** rồi cập nhật một reference với quy tắc xuất bản phù hợp (ví dụ `volatile`/`AtomicReference`).
- Sử dụng **CAS** nếu thao tác yêu cầu kiểm tra phiên bản hiện tại rồi cập nhật atomically.
- Giới hạn object ở **một thread** và ghi rõ nó không thread-safe.

**Chỉ thêm `volatile` cho từng field riêng lẻ không biến nhiều phép gán thành một transaction.** Exception guarantee và atomicity/visibility là các thuộc tính khác nhau nhưng có liên quan.

## 23. Ví dụ Java 17: công bố snapshot bất biến bằng một reference

Lưu **`SnapshotStateDemo.java`**:

```java
public class SnapshotStateDemo {
    record Bounds(int lower, int upper) {
        Bounds {
            if (lower > upper) {
                throw new IllegalArgumentException("Khoảng sai");
            }
        }
    }

    static class RangeStore {
        private volatile Bounds bounds = new Bounds(1, 5);

        Bounds snapshot() {
            return bounds;
        }

        void replace(int lower, int upper) {
            Bounds candidate = new Bounds(lower, upper);
            bounds = candidate;
        }
    }

    public static void main(String[] args) {
        RangeStore store = new RangeStore();

        try {
            store.replace(9, 2);
        } catch (IllegalArgumentException e) {
            System.out.println("Từ chối khoảng sai");
        }

        System.out.println("Sau lỗi: " + store.snapshot());

        store.replace(3, 7);
        System.out.println("Sau thành công: " + store.snapshot());
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 SnapshotStateDemo.java
java SnapshotStateDemo
```

**Stdout chính xác:**

```text
Từ chối khoảng sai
Sau lỗi: Bounds[lower=1, upper=5]
Sau thành công: Bounds[lower=3, upper=7]
```

Record `Bounds` kiểm tra invariant khi tạo. Field `volatile Bounds bounds` được cập nhật bằng **một phép gán reference**, nên reader lấy được một snapshot nhất quán của hai thành phần immutable. Nếu việc cập nhật là **read-modify-write dựa trên snapshot cũ** giữa nhiều writer, chỉ `volatile` không ngăn lost update: có thể cần `AtomicReference.compareAndSet` hoặc lock.

Đây là một cách kết hợp strong guarantee khi tạo candidate và quy tắc publish state trong môi trường concurrent.

## 24. Ví dụ: atomic transfer giữa hai số dư trong một object

Khi thay đổi nhiều field phải đi cùng nhau, hãy kiểm tra mọi điều kiện trước. Ví dụ dưới đây dùng **một lock trên instance** để không cho các method của chính class quan sát transfer nửa chừng.

Lưu **`AccountTransferDemo.java`**:

```java
public class AccountTransferDemo {
    static class Ledger {
        private long left = 100;
        private long right = 20;

        synchronized void transfer(long amount) {
            if (amount <= 0) {
                throw new IllegalArgumentException(
                        "amount phải dương");
            }
            if (amount > left) {
                throw new IllegalStateException(
                        "Không đủ số dư bên trái");
            }

            long nextRight = Math.addExact(right, amount);
            long nextLeft = left - amount;

            left = nextLeft;
            right = nextRight;
        }

        synchronized String snapshot() {
            return left + "/" + right;
        }
    }

    public static void main(String[] args) {
        Ledger ledger = new Ledger();

        try {
            ledger.transfer(150);
        } catch (IllegalStateException e) {
            System.out.println("Từ chối chuyển");
        }
        System.out.println("Sau lỗi: " + ledger.snapshot());

        ledger.transfer(30);
        System.out.println("Sau chuyển: " + ledger.snapshot());
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 AccountTransferDemo.java
java AccountTransferDemo
```

**Stdout chính xác:**

```text
Từ chối chuyển
Sau lỗi: 100/20
Sau chuyển: 70/50
```

`Math.addExact` kiểm soát overflow ở số dư nhận trước khi gán. Cả `transfer` và `snapshot` dùng **cùng monitor lock**; caller dùng API đã thiết kế không nhìn thấy giữa hai phép gán. Những side effect chưa được cài trong ví dụ (database, network, listener) không được bao phủ bởi lock này.

**Không coi đây là hệ thống chuyển tiền đa tài khoản/đa tiến trình thực tế.** Transaction database, lock đa đối tượng, crash recovery và idempotency cần thiết kế riêng.

## 25. Ràng buộc state trước và sau lifecycle failure

Có những object không thể trở lại trạng thái cũ sau khi hoạt động thất bại, nhưng vẫn có thể chuyển sang một trạng thái **hợp lệ, rõ nghĩa**.

Ví dụ state machine:

```text
NEW → OPEN → CLOSED
       │
       └── I/O thất bại → FAILED
```

Hợp đồng:

- `read()` chỉ hợp lệ ở `OPEN`.
- Nếu validation sai, object vẫn ở `OPEN` (strong guarantee cho lỗi input).
- Nếu underlying I/O hỏng không thể tiếp tục, object chuyển sang `FAILED`.
- Sau `FAILED`, chỉ những thao tác đã công bố (ví dụ `close`, `state` hoặc `diagnostics`) được phép; `read()` bị từ chối bằng `IllegalStateException`.
- Dù có lỗi, object **không** ở một trạng thái mơ hồ nửa OPEN nửa CLOSED.

Đây là **basic guarantee** cho lỗi I/O: trạng thái có thay đổi sang `FAILED`, nhưng invariant về lifecycle vẫn giữ.

Một số API JDK cho phép thử lại đọc sau timeout; không nên ép mọi loại IOException vào `FAILED` nếu contract và giao thức cho phép tiếp tục. Quyết định phải dựa trên **khả năng duy trì invariant sau lỗi cụ thể**.

## 26. Chương trình Java 17: exceptional postcondition là trạng thái FAILED

Lưu **`FailureStateDemo.java`**:

```java
import java.io.IOException;

public class FailureStateDemo {
    enum State {
        NEW, OPEN, FAILED, CLOSED
    }

    static class ReaderSession {
        private State state = State.NEW;

        void open() {
            if (state != State.NEW) {
                throw new IllegalStateException("Không còn NEW");
            }
            state = State.OPEN;
        }

        String read(boolean fail) throws IOException {
            if (state != State.OPEN) {
                throw new IllegalStateException(
                        "read cần trạng thái OPEN");
            }
            if (fail) {
                state = State.FAILED;
                throw new IOException("Lỗi đọc");
            }
            return "data";
        }

        State state() {
            return state;
        }

        void close() {
            state = State.CLOSED;
        }
    }

    public static void main(String[] args) {
        ReaderSession session = new ReaderSession();
        session.open();
        System.out.println("Trước: " + session.state());

        try {
            session.read(true);
        } catch (IOException e) {
            System.out.println("Bắt: " + e.getMessage());
        }

        System.out.println("Sau lỗi: " + session.state());
        session.close();
        System.out.println("Sau close: " + session.state());
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 FailureStateDemo.java
java FailureStateDemo
```

**Stdout chính xác:**

```text
Trước: OPEN
Bắt: Lỗi đọc
Sau lỗi: FAILED
Sau close: CLOSED
```

Lỗi đọc khiến object chuyển sang `FAILED` có chủ đích trước khi ném checked `IOException`. Mục tiêu là **công bố hậu điều kiện lỗi rõ ràng**, không phải giữ nguyên mọi thứ trong mọi trường hợp.

Đối với hệ thống thực, trạng thái `FAILED` phải có quy tắc đóng, tái tạo hoặc chẩn đoán phù hợp; không dùng state flag để che resource leak.

## 27. Kiểm tra và thao tác có thể ném exception bất kỳ lúc nào?

Trong Java, một lời gọi thường có nhiều nguồn lỗi:

- **Validation**: `IllegalArgumentException`, `NullPointerException`.
- **Lifecycle**: `IllegalStateException`.
- **Tính toán**: `ArithmeticException` như `Math.addExact`.
- **Collection**: `IndexOutOfBoundsException`, `ConcurrentModificationException` theo API.
- **I/O**: `IOException`, `UncheckedIOException` hoặc lỗi framework.
- **Allocation**: lỗi cấp phát/JVM nghiêm trọng, thường ngoài phạm vi bảo đảm phục hồi ứng dụng.
- **Callback tùy ý**: có thể ném exception mà class gọi callback không kiểm soát.
- **Cancellation/interruption**: đặc biệt ở hoạt động chờ hoặc tác vụ bất đồng bộ.

Việc định nghĩa state hậu lỗi cần xét **những nguồn lỗi nằm trong hợp đồng mà API hứa xử lý**, chứ không chỉ một câu `throw new IllegalArgumentException` do chính bạn viết.

Ví dụ nếu bạn đã cập nhật field thứ nhất rồi gọi một callback có thể ném `RuntimeException` trước khi cập nhật field thứ hai, lỗi callback có thể phá invariant dù nhánh validation đã được kiểm tra từ trước.

## 28. Callback, observer và rủi ro reentrancy

Xem mẫu có vấn đề:

```java
state = "UPDATING";
listener.onChange(newValue);
state = "READY";
```

Đây là **mã minh họa**. Nếu `listener.onChange` ném exception, object bị kẹt ở `"UPDATING"`. Nếu listener còn gọi ngược `getState()` trước khi ném, nó đã thấy trạng thái trung gian.

Các chiến lược:

1. Hoàn tất và công bố state nhất quán **trước** khi phát thông báo; nhưng nếu listener thất bại, phải công bố rằng side effect thông báo có thể thất bại sau commit.
2. Chuẩn bị event và state mới, commit theo thứ tự có hợp đồng rõ.
3. Không gọi callback không tin cậy giữa các bước sửa invariant.
4. Nếu callback chạy bất đồng bộ, cần công bố failure semantics riêng cho event delivery.
5. Nếu notification là bắt buộc cùng với state transition, cần một cơ chế transactional/outbox hoặc cách bù trừ, không chỉ `try/catch`.

**Strong guarantee với trạng thái nội bộ** không kéo theo strong guarantee với toàn bộ observer. Hợp đồng phải nói rõ **commit trước hay sau notification** và kết quả khi notification thất bại.

## 29. Tài nguyên và hậu điều kiện lỗi

State không chỉ là field nghiệp vụ. Một API còn quản lý **file handle, socket, lock, database connection, stream hoặc thread**.

Sau lỗi, cần trả lời:

- Tài nguyên có đang mở không?
- Ai sở hữu việc đóng?
- Đóng có thể ném lỗi không?
- Có giữ lock không, hay đã được giải phóng?
- Đối tượng có thể tái sử dụng hay phải tạo mới?
- Có dữ liệu trong buffer chưa flush?
- Có transaction chưa commit/rollback?
- Caller được phép retry trên cùng resource không?

`try-with-resources` giúp đóng resource khi thoát phạm vi:

```java
try (InputStream input = openInput()) {
    return parse(input);
}
```

Đây là **mã minh họa**, method `openInput`, `parse` cần được định nghĩa và phương thức bao ngoài xử lý checked exceptions. Khi `parse` ném, Java vẫn cố gọi `input.close()`. Nhưng **đóng resource** không tự bảo đảm dữ liệu ghi trước đó được rollback hoặc đối tác không nhận request.

**Không đánh đồng “cleanup thành công” với “transaction rollback thành công”.**

## 30. Ví dụ Java 17: try-with-resources vẫn đóng sau lỗi

Lưu **`CleanupAfterFailureDemo.java`**:

```java
public class CleanupAfterFailureDemo {
    static class Resource implements AutoCloseable {
        private boolean closed;

        void work() {
            System.out.println("Đang xử lý");
            throw new IllegalStateException("Thất bại");
        }

        @Override
        public void close() {
            closed = true;
            System.out.println("Đã đóng");
        }

        boolean isClosed() {
            return closed;
        }
    }

    public static void main(String[] args) {
        Resource resource = new Resource();

        try (resource) {
            resource.work();
        } catch (IllegalStateException e) {
            System.out.println("Bắt: " + e.getMessage());
        }

        System.out.println("Closed: " + resource.isClosed());
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 --release 17 CleanupAfterFailureDemo.java
java CleanupAfterFailureDemo
```

**Stdout chính xác:**

```text
Đang xử lý
Đã đóng
Bắt: Thất bại
Closed: true
```

`try (resource)` hợp lệ từ Java 9 với một biến resource **final hoặc effectively final**. Dù `work()` thất bại, `close()` được gọi trước khi handler catch thực thi.

Đây là một bảo đảm về **vòng đời resource** trong ví dụ, không phải bảo đảm rằng mọi trạng thái nghiệp vụ bị đổi trong `work()` đã được hoàn tác.

## 31. Nếu cả thao tác chính và cleanup đều thất bại?

`try-with-resources` dùng **suppressed exceptions** để không làm mất lỗi chính khi `close()` cũng ném exception.

Ví dụ:

```java
try (Resource resource = acquire()) {
    perform(resource);  // Ném lỗi chính.
} // close() có thể ném lỗi phụ.
```

Đây là **mã minh họa**, `Resource` và các method phụ cần được khai báo. Nếu thân `try` ném một exception và `close()` cũng ném, lỗi close thường được gắn vào `primary.getSuppressed()` theo cơ chế chuẩn.

Hợp đồng trạng thái cần xét: một resource có thể **đã thực hiện cleanup một phần** rồi `close` thất bại. Lỗi bị suppressed vẫn đáng ghi nhận khi đánh giá tài nguyên có thực sự được giải phóng.

Không chỉ lấy `primary.getMessage()` rồi bỏ cause/suppressed nếu cần chẩn đoán điều kiện khôi phục. Trong logger, ưu tiên ghi object `Throwable` đầy đủ.

## 32. Ví dụ Java 17: ngoại lệ close thành suppressed

Lưu **`SuppressedFailureDemo.java`**:

```java
import java.io.IOException;

public class SuppressedFailureDemo {
    static class Resource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            throw new IOException("close thất bại");
        }
    }

    static void run() throws IOException {
        try (Resource resource = new Resource()) {
            throw new IOException("work thất bại");
        }
    }

    public static void main(String[] args) {
        try {
            run();
        } catch (IOException e) {
            System.out.println("Primary: " + e.getMessage());
            System.out.println("Suppressed count: "
                    + e.getSuppressed().length);
            System.out.println("Suppressed: "
                    + e.getSuppressed()[0].getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 SuppressedFailureDemo.java
java SuppressedFailureDemo
```

**Stdout chính xác:**

```text
Primary: work thất bại
Suppressed count: 1
Suppressed: close thất bại
```

Nếu code tự viết `finally` rồi ném exception mới, nó có thể **che lỗi ban đầu**; dùng cấu trúc resource phù hợp giúp giữ dữ liệu chẩn đoán tốt hơn.

## 33. Phép ghi file/network có thể chỉ hoàn thành một phần

Một `OutputStream.write` có thể **đã xuất một số byte trước khi IOException được ném**. Khi đó không thể kết luận “method ném IOException nên chưa ghi gì”.

Ví dụ tự chạy mô phỏng **partial I/O** mà không cần filesystem:

Lưu **`PartialWriteDemo.java`**:

```java
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class PartialWriteDemo {
    static class FailingOutput extends OutputStream {
        private final StringBuilder delivered = new StringBuilder();

        @Override
        public void write(int value) throws IOException {
            if (delivered.length() == 2) {
                throw new IOException("Sink từ chối byte tiếp theo");
            }
            delivered.append((char) (value & 0xff));
        }

        String delivered() {
            return delivered.toString();
        }
    }

    public static void main(String[] args) {
        FailingOutput output = new FailingOutput();

        try {
            output.write("ABC".getBytes(StandardCharsets.US_ASCII));
        } catch (IOException e) {
            System.out.println("Bắt: " + e.getMessage());
        }

        System.out.println("Đã ghi: " + output.delivered());
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 --release 17 PartialWriteDemo.java
java PartialWriteDemo
```

**Stdout chính xác:**

```text
Bắt: Sink từ chối byte tiếp theo
Đã ghi: AB
```

`OutputStream` có implementation `write(byte[])` gọi `write(byte[],off,len)` mặc định; lớp `OutputStream` mặc định lần lượt gọi `write(int)` với từng byte. Trong ví dụ, hai byte `A` và `B` đã được chấp nhận, byte `C` gây lỗi. `IOException` không tự làm mất hai byte đã ghi.

**Đây là mô phỏng chủ động**, không chứng minh mọi stream sẽ chia nhỏ việc ghi giống vậy. Bài học tổng quát là phải viết hợp đồng về **partial progress**, không áp strong guarantee cho I/O chỉ bằng suy đoán.

## 34. Database transaction: mạnh trong phạm vi transaction, không tự bao cả thế giới

Một transaction database có thể cung cấp atomicity cho một nhóm thao tác **trên những tài nguyên thuộc phạm vi transaction ấy**. Ví dụ khung JDBC:

```java
connection.setAutoCommit(false);
try {
    debit(connection, from, amount);
    credit(connection, to, amount);
    connection.commit();
} catch (SQLException e) {
    try {
        connection.rollback();
    } catch (SQLException rollbackError) {
        e.addSuppressed(rollbackError);
    }
    throw e;
}
```

Đây là **mã minh họa**, cần connection, các helper và xử lý transaction lifecycle phù hợp. Trong thực tế phải khôi phục `autoCommit`/đóng connection theo quyền sở hữu, và tính đến lỗi network trong hoặc sau commit.

Các điều kiện quan trọng:

- Cùng transaction không tự bao phủ email, file hay HTTP call gửi trong giao dịch.
- `rollback()` cũng có thể thất bại; cần bảo toàn lỗi rollback.
- Một lỗi kết nối trong lúc `commit()` có thể khiến **kết quả commit chưa rõ** đối với client.
- Không được khẳng định “có SQLException thì database chắc chắn không thay đổi”.
- Isolation và concurrency của database cần được cấu hình phù hợp; exception safety ở Java không thay thế transaction semantics.

Hợp đồng có thể cần trạng thái **UNKNOWN** hoặc khả năng đối chiếu transaction ID khi kết quả commit chưa thể xác minh.

## 35. Distributed side effects: “ném lỗi” không có nghĩa “chưa thực hiện”

Trong RPC, HTTP, message broker hoặc hệ thống thanh toán, một request có thể:

1. Được client gửi.
2. Được server nhận và xử lý.
3. Được server commit.
4. Phản hồi bị mất.
5. Client timeout và ném exception.

Trong trường hợp này, lời gọi phía client **thất bại ở mức biết kết quả**, nhưng side effect phía server **có thể đã xảy ra**.

Không thể sửa vấn đề bằng:

```java
catch (IOException e) {
    sendAgain();
}
```

Đây là **phản ví dụ minh họa**, method `sendAgain` chưa được định nghĩa. Lặp lại có thể tạo giao dịch thứ hai.

Các kỹ thuật phù hợp:

- **Idempotency key** để cùng một logical request không bị xử lý lặp sai.
- **Request/transaction ID** để tra cứu trạng thái server.
- **Retry có giới hạn** với backoff và deadline.
- **Outbox** hoặc event-driven consistency nếu hệ thống cần phối hợp ghi dữ liệu và phát sự kiện.
- **Compensation** khi không thể transaction xuyên dịch vụ.
- **Trạng thái kết quả chưa rõ** được công bố rõ cho caller, không trả “thất bại chắc chắn chưa commit”.

Strong guarantee **toàn hệ thống phân tán** đòi hỏi giao thức/thành phần hỗ trợ, không phải là tính năng tự có của checked exception.

## 36. Trạng thái kết quả: SUCCESS, FAILED và UNKNOWN

Với những API có side effect từ xa, một kết quả có thể phải phân biệt:

- **SUCCESS:** server đã xác nhận thao tác thành công.
- **FAILED:** server xác nhận thao tác không thực hiện hoặc từ chối và hợp đồng biết trạng thái.
- **UNKNOWN:** client không xác định chắc thao tác đã commit hay chưa.

Ví dụ kiểu kết quả Java 17:

```java
enum Outcome {
    SUCCESS, FAILED, UNKNOWN
}

record OperationResult(String requestId, Outcome outcome) {
}
```

Đây là **hai loại minh họa**, không có `main`. Một request timeout có thể dẫn đến `UNKNOWN`, còn một validation failure trước khi gửi có thể chắc chắn là `FAILED` mà không có side effect.

Không nên trả `FAILED` với ý nghĩa “chưa thực hiện” khi chỉ biết là **không nhận được phản hồi**. Nếu caller cần retry, việc phân biệt UNKNOWN là rất quan trọng.

## 37. Checked và unchecked exception không quyết định state guarantee

Một method `throws IOException` có thể:

- không thay đổi state nếu thất bại,
- thay đổi một phần state hợp lệ,
- đưa object vào `FAILED`,
- hoặc không thể xác minh side effect bên ngoài.

Tương tự, method ném `IllegalArgumentException` cũng có thể **vô tình làm hỏng state** nếu validation được thực hiện sau khi sửa field.

Do đó:

- **Checked vs unchecked:** quy tắc compiler ép catch-or-declare và ý nghĩa hợp đồng lỗi.
- **Basic/strong/no-throw:** mức cam kết trạng thái khi method kết thúc bất thường.
- **Atomicity:** tính không chia nhỏ được của một nhóm cập nhật trong phạm vi hệ thống xác định.
- **Thread safety:** các bảo đảm khi truy cập đồng thời.
- **Idempotency:** thực hiện lặp cùng logical operation không tạo hiệu ứng không mong muốn.
- **Durability:** hiệu ứng đã commit còn tồn tại qua những sự cố tương ứng.

Những thuộc tính này **không thay thế cho nhau**. Một API đáng tin thường cần nhiều hơn một thuộc tính cùng lúc.

## 38. Không được giả định catch sẽ phục hồi invariant

`try/catch` là cơ chế xử lý luồng điều khiển sau exception:

```java
try {
    updatePartA();
    updatePartB();
} catch (RuntimeException e) {
    log(e);
}
```

Đây là **mã minh họa**. Nếu `updatePartA` đã thay đổi state, rồi `updatePartB` ném exception, handler chỉ ghi log **không khôi phục gì**.

Viết `catch` không đồng nghĩa:

- rollback field,
- rollback database,
- thu hồi message đã gửi,
- đóng tất cả tài nguyên,
- giải phóng mọi lock,
- hoặc giữ nguyên trạng thái đối tượng.

Một handler đúng phải biết **hậu điều kiện thất bại** và quyền sở hữu công việc. Nếu không thể phục hồi, nó nên lan truyền hoặc chuyển lỗi bằng cause theo hợp đồng, thay vì trả thành công giả.

## 39. Cẩn thận với rollback thủ công

Một cách nghĩ thường gặp:

```java
State old = current;
try {
    updateExternalSystem();
    current = next;
} catch (Exception e) {
    current = old;
    throw e;
}
```

Đây là **mã minh họa**, các biến và method cần được định nghĩa. Đặt `current = old` không thể tự xóa side effect của `updateExternalSystem()`. Nếu `current` trỏ tới một object mutable đã bị sửa trong chỗ khác, giữ reference cũ cũng không bảo đảm trạng thái cũ thực sự còn nguyên.

Rollback thủ công có thể thất bại do:

- backup là shallow copy/alias.
- thao tác hoàn tác tự nó ném exception.
- callback đã nhìn thấy dữ liệu trung gian.
- resource/transaction đã commit một phần.
- thread khác đã đọc hoặc sửa state.
- một sự kiện đã được gửi sang hệ thống khác.
- lỗi phát sinh ở vị trí chưa được bạn dự đoán.

Nếu không thể rollback có độ tin cậy tương xứng, **công bố basic guarantee hoặc FAILED/UNKNOWN** thay vì hứa strong guarantee giả.

## 40. Exception safety và method có side effect quan sát được

Một public API có thể tạo side effect ngay cả khi field object không đổi:

- Ghi log có chứa dữ liệu.
- Tăng metric/counter.
- Phát event.
- Gửi email.
- Cấp phát ID mới.
- Tiêu thụ item từ queue.
- Đánh dấu một công việc đã chạy.
- Gọi callback bất đồng bộ.

Điều này có nghĩa “**không đổi state**” cần định nghĩa theo **phạm vi quan sát thực sự cần bảo đảm**. Một số side effect như log/metrics được chấp nhận xảy ra ngay cả khi method thất bại; không phải tất cả cần rollback.

Ví dụ một API có thể cam kết “nếu validation thất bại, dữ liệu nghiệp vụ không thay đổi; hệ thống vẫn ghi một audit event về lần gọi bị từ chối”. Hợp đồng này có thể hoàn toàn hợp lệ, nhưng không nên viết đơn giản “mọi trạng thái đều không đổi”.

## 41. Kết hợp exception cause với tình trạng state

Exception nên cho biết **nguyên nhân**; contract/documentation nên cho biết **tình trạng object**. Một số API muốn biểu diễn trạng thái cùng exception domain:

```java
class SubmissionException extends Exception {
    private final boolean mayHaveCommitted;

    SubmissionException(String message,
                        boolean mayHaveCommitted,
                        Throwable cause) {
        super(message, cause);
        this.mayHaveCommitted = mayHaveCommitted;
    }

    boolean mayHaveCommitted() {
        return mayHaveCommitted;
    }
}
```

Đây là **class minh họa**, không có `main`. Nó thể hiện cách công bố tri thức mà caller cần để quyết định đối chiếu hoặc retry.

Tuy nhiên, boolean `mayHaveCommitted` có thể chưa đủ cho hệ thống thực. Một enum `NOT_SENT/CONFIRMED/UNKNOWN` hoặc một result object chứa request ID, outcome và thông tin hỗ trợ phục hồi có thể biểu đạt rõ hơn.

**Không suy diễn tình trạng commit chỉ từ `IOException`, `TimeoutException` hay `RuntimeException`.** Nguồn thông tin phải là hợp đồng giao thức/trạng thái được xác minh.

## 42. Viết JavaDoc với exceptional postcondition rõ ràng

Ví dụ API:

```java
/**
 * Thay toàn bộ cấu hình đang chạy.
 *
 * @param next dữ liệu cấu hình mới, không null
 * @throws NullPointerException nếu next null
 * @throws IllegalArgumentException nếu next không hợp lệ
 *
 * <p>Nếu validation thất bại, cấu hình hiện tại
 * không bị thay đổi và listener chưa được gọi.</p>
 *
 * <p>Nếu cập nhật nguồn bên ngoài thất bại, trạng thái
 * có thể là FAILED; gọi state() để kiểm tra trước khi retry.</p>
 */
public void replace(Config next) {
    // implementation theo hợp đồng trên
}
```

Đây là **mã minh họa**, với `Config` và implementation được thiết kế riêng. Khi viết JavaDoc thật, cần mô tả cả những điều kiện state và exception cụ thể nếu method có chúng.

Một template hữu ích trong tài liệu:

| Mục | Câu cần trả lời |
| --- | --- |
| Invariant | Những điều nào luôn đúng ở state hợp lệ? |
| Precondition | Caller được truyền gì và gọi lúc nào? |
| Success | Field/resource/side effect nào thay đổi? |
| Failure by validation | State nào không đổi? |
| Failure by I/O | Có thể đã ghi/gửi một phần không? |
| Failure by cancellation | Tài nguyên và interrupt status thế nào? |
| Retry | Có cần sửa input, rebuild object, tra cứu trạng thái hay idempotency key không? |
| Ownership | Ai đóng, rollback, release lock hoặc xử lý object FAILED? |

Mục tiêu là **caller không phải đọc toàn bộ source** mới biết sau exception có thể làm gì.

## 43. Thiết kế test: kiểm tra trạng thái, không chỉ loại exception

Một test chỉ xác nhận:

```java
assertThrows(IllegalArgumentException.class,
        () -> service.update(badInput));
```

là **mã minh họa dùng JUnit**, cần thư viện và biến phù hợp. Nó kiểm tra có exception nhưng **chưa xác minh state hậu lỗi**.

Cần thêm:

- Snapshot state trước khi gọi.
- Chèn một lỗi có chủ đích ở vị trí mong muốn.
- Gọi method và bắt đúng loại exception.
- Snapshot state sau lỗi.
- So sánh các phần thuộc invariant và strong/basic guarantee.
- Nếu có side effect bên ngoài, kiểm tra fake/mock sink đã nhận gì, không suy từ state nội bộ.
- Kiểm tra tài nguyên đóng/lock được giải phóng.
- Kiểm tra cause/suppressed nếu wrapper/cleanup nằm trong hợp đồng.
- Kiểm tra caller có thể gọi method nào sau lỗi.
- Kiểm tra retry và idempotency theo tình huống.

**Test exception safety phải chủ động thử nhiều điểm thất bại**, không chỉ một nhánh validation dễ nhất.

## 44. Chương trình Java 17: assert cả exception và trạng thái không đổi

Lưu **`FailureInvariantAssertionsDemo.java`**:

```java
public class FailureInvariantAssertionsDemo {
    static class Counter {
        private int value = 5;

        void increase(int delta) {
            if (delta <= 0) {
                throw new IllegalArgumentException(
                        "delta phải dương");
            }
            int candidate = Math.addExact(value, delta);
            value = candidate;
        }

        int value() {
            return value;
        }
    }

    static void assertUnchanged(
            Counter counter, int before, Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Thiếu exception");
        } catch (RuntimeException expected) {
            if (counter.value() != before) {
                throw new AssertionError("State đã thay đổi");
            }
            System.out.println("PASS unchanged");
        }
    }

    public static void main(String[] args) {
        Counter counter = new Counter();

        assertUnchanged(counter, 5,
                () -> counter.increase(-1));

        counter.increase(3);
        System.out.println("Success state: " + counter.value());

        Counter nearMax = new Counter();
        nearMax.increase(Integer.MAX_VALUE - 5);

        assertUnchanged(nearMax, Integer.MAX_VALUE,
                () -> nearMax.increase(1));
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 FailureInvariantAssertionsDemo.java
java FailureInvariantAssertionsDemo
```

**Stdout chính xác:**

```text
PASS unchanged
Success state: 8
PASS unchanged
```

Các nhánh được kiểm tra:

- Input âm ném `IllegalArgumentException`, `value` giữ 5.
- Tăng hợp lệ cập nhật từ 5 lên 8.
- Từ một đối tượng có `value = Integer.MAX_VALUE`, tăng tiếp gây `ArithmeticException` từ `Math.addExact`, giá trị vẫn nguyên.

Test dùng `AssertionError` chủ động để không cần cờ `-ea`. Nó chưa bao phủ mọi failure injection nhưng là mẫu kiểm thử **exceptional postcondition**, chứ không chỉ loại exception.

## 45. Failure injection: thử lỗi ở từng giai đoạn

Một hệ thống có nhiều bước:

```text
VALIDATE → PREPARE → WRITE → NOTIFY → COMMIT
```

Cần thử khi từng bước ném lỗi:

| Điểm phát lỗi | Điều cần xác minh |
| --- | --- |
| Validate | Không có thay đổi và không gọi dependency có side effect |
| Prepare | Candidate bị bỏ, state cũ hợp lệ |
| Write | Có partial I/O không? Có resource leak không? |
| Notify | Commit đã diễn ra chưa? Event có thể gửi một phần không? |
| Commit | Outcome có rõ không? Rollback/compensation có đảm bảo không? |
| Close/cleanup | Lỗi chính và suppressed có được bảo toàn không? |

Có thể dùng:

- Fake dependency chủ động ném `IOException` ở lần gọi thứ N.
- Fake sink ghi số lượng mục đã nhận rồi ném.
- In-memory repository quản lý transaction giả với outcome rõ.
- Latch để tạo tình huống concurrency cụ thể.
- Property-based test kiểm tra invariant trên nhiều input.
- Integration test độc lập cho database/filesystem/network thật.

Không phụ thuộc vào việc “đợi mạng tự lỗi” để kiểm thử logic guarantee; lỗi hạ tầng thực tế rất khó tái hiện đúng bước.

## 46. Đường thất bại và mức độ bảo đảm theo loại API

| API/thao tác | Mức có thể hướng tới | Chú ý |
| --- | --- | --- |
| Setter đơn giản đã validate đầy đủ | Strong cho field | Cần lưu ý observer và concurrency |
| Thay thế immutable snapshot | Strong cho snapshot | Snapshot cũ giữ nguyên nếu prepare thất bại |
| Batch import từng item | Basic | Đã commit một phần, phải công bố |
| Batch all-or-nothing trong memory | Strong trong phạm vi bộ nhớ | Prepare tất cả trước khi swap |
| Ghi `OutputStream` | Thường phải chấp nhận partial progress | IOException không rollback byte |
| JDBC transaction | Atomic trong phạm vi transaction hỗ trợ | Lỗi commit/đường mạng có thể khiến outcome chưa rõ |
| RPC có side effect | Cần idempotency/đối chiếu trạng thái | Timeout không chứng minh chưa commit |
| Resource close | Có thể ném IOException | Quản lý cleanup/suppressed |
| Getter bất biến đơn giản | Có thể thiết kế no-throw trong điều kiện hợp lệ | Không hứa miễn nhiễm mọi Error của JVM |

Bảng là gợi ý **thiết kế hợp đồng**, không phải khẳng định rằng mọi implementation của một loại API luôn đạt một mức guarantee cố định.

## 47. Những sai lầm phổ biến

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| “Có `catch` thì trạng thái tự rollback.” | Không. Catch chỉ xử lý luồng điều khiển. |
| “Checked exception chắc chắn giữ nguyên state.” | Không. Checked/unchecked độc lập với state guarantee. |
| “Chỉ cần invariant đúng khi thành công.” | Sau thất bại vẫn cần mức guarantee đã công bố. |
| “Basic guarantee nghĩa là không đổi gì.” | Không. State có thể đổi nhưng invariant phải giữ. |
| “Strong guarantee nghĩa là rollback cả email, log, database.” | Chỉ trong phạm vi cam kết; side effect ngoài cần cơ chế riêng. |
| “No-throw nghĩa là có thể bắt `Throwable` để không bao giờ lỗi.” | Không. Đừng che lỗi nghiêm trọng/JVM. |
| “Validation có thể đặt sau khi sửa field vì đã có exception.” | Có thể làm hỏng state trước khi ném. |
| “`List.copyOf` tự deep-copy mọi item mutable.” | Không. Chỉ tạo cấu trúc list snapshot theo hợp đồng. |
| “Getter trả list nội bộ không ảnh hưởng invariant.” | Caller có thể sửa trực tiếp nếu list mutable. |
| “Hai field `volatile` được gán liên tiếp là một transaction.” | Không. Reader có thể thấy trạng thái giữa chừng. |
| “Một immutable record chứa mutable field chắc chắn deep immutable.” | Không. Cần sở hữu/snapshot component. |
| “`finally`/try-with-resources tự hoàn tác dữ liệu ghi.” | Không. Chủ yếu quản lý cleanup tài nguyên. |
| “`IOException` khi write nghĩa là chưa ghi byte nào.” | Không. Ghi một phần có thể đã xảy ra. |
| “Timeout RPC chứng minh server chưa commit.” | Không. Outcome có thể là UNKNOWN. |
| “`rollback()` luôn thành công.” | Không. Rollback có thể ném lỗi riêng. |
| “Đã ném `IllegalStateException` thì object phải hỏng hoàn toàn.” | Không. Có thể là thao tác sai lifecycle, state vẫn hợp lệ. |
| “Chuyển sang FAILED là vi phạm basic guarantee.” | Không nếu FAILED là trạng thái hợp lệ có contract. |
| “Một phép gán reference đủ giải quyết mọi race writer.” | Không. Read-modify-write đồng thời cần lock/CAS. |
| “Catch rồi trả thành công là resilience.” | Chỉ đúng khi thật sự khôi phục theo contract. |
| “Chỉ assert loại exception là đủ test an toàn.” | Cần kiểm tra state và side effect sau failure. |

## 48. Câu hỏi tự kiểm tra

**1. Invariant là gì?**  
Một điều kiện về trạng thái phải giữ tại các điểm đối tượng được công bố là hợp lệ.

**2. Exceptional postcondition là gì?**  
Cam kết về state và side effect khi method kết thúc bằng exception.

**3. Basic guarantee khác strong guarantee ra sao?**  
Basic giữ invariant nhưng cho phép partial state changes; strong giữ nguyên trạng thái quan sát được trong phạm vi cam kết khi thất bại.

**4. Java có tự bảo đảm strong guarantee cho mọi method `throws IOException` không?**  
Không. Cần thiết kế/triển khai/kiểm thử.

**5. Vì sao đặt validation sau phép gán field nguy hiểm?**  
Exception có thể xảy ra sau khi state đã đổi một phần, thậm chí phá invariant.

**6. Pattern validate–prepare–commit giải quyết điều gì?**  
Đẩy những bước có thể lỗi trước một commit nhỏ, giúp giữ state cũ nếu chuẩn bị thất bại.

**7. Vì sao `List.copyOf` hữu ích nhưng không phải deep copy?**  
Nó tạo list không chỉnh sửa được bằng API list, nhưng object phần tử mutable có thể vẫn được chia sẻ.

**8. Record bất biến chứa `List<MutableItem>` có tự deep immutable không?**  
Không. Component tham chiếu mutable cần chính sách riêng.

**9. `Math.addExact` giúp ngoại lệ thế nào?**  
Nó phát hiện overflow trước khi gán, giúp tránh công bố giá trị tràn sai invariant.

**10. Catch một exception có bảo đảm callback trước đó chưa chạy không?**  
Không. Callback có thể đã tạo side effect trước khi ném.

**11. Nếu import 4 item rồi lỗi ở item thứ 5, đó có thể là mức guarantee nào?**  
Basic, nếu bốn item đã commit hợp lệ và invariant còn đúng, đồng thời contract công bố partial progress.

**12. Tại sao ghi `OutputStream` có thể để lại dữ liệu một phần sau IOException?**  
Implementation có thể ghi các byte trước khi gặp lỗi ở byte tiếp theo.

**13. `try-with-resources` cam kết điều gì khi thân try ném?**  
Nó cố đóng resource; nếu close cũng ném, có thể gắn suppressed exception lên lỗi chính. Không tự rollback side effect.

**14. Vì sao `rollback()` của database không thể được giả định luôn thành công?**  
Rollback có thể gặp lỗi I/O/kết nối hoặc tình trạng transaction chưa rõ, nên cần xử lý và log lỗi riêng.

**15. Timeout sau khi gửi lệnh tạo giao dịch có chứng minh giao dịch chưa commit?**  
Không. Có thể phải dùng trạng thái UNKNOWN và request ID để đối chiếu.

**16. `volatile Bounds` snapshot có giải quyết lost update giữa nhiều writer không?**  
Không. Cần CAS/lock cho read-modify-write.

**17. Nếu object chuyển thành `FAILED` sau IOException, có thể vẫn đạt basic guarantee không?**  
Có, nếu FAILED là trạng thái hợp lệ được công bố và tài nguyên được quản lý.

**18. Vì sao không catch `RuntimeException` rồi trả dữ liệu thành công mặc định?**  
Có thể che bug và partial side effect, vi phạm hợp đồng caller.

**19. Test ngoại lệ tốt cần kiểm tra thêm gì ngoài loại exception?**  
State trước–sau, invariant, side effect, resource, cause/suppressed, khả năng retry/sử dụng tiếp.

**20. Nếu getter cho caller reference tới collection mutable nội bộ, hậu điều kiện thất bại có dễ kiểm soát không?**  
Không. Caller có thể làm thay đổi state ngoài mọi validation của API.

**21. Lỗi trong listener sau commit có nghĩa commit trước đó đã tự rollback?**  
Không. Cần hợp đồng riêng cho commit và notification.

**22. Có nên dùng `catch (Throwable)` để tạo bảo đảm no-throw tuyệt đối không?**  
Không. Che cả lỗi nghiêm trọng của JVM thường không thể tạo bảo đảm an toàn thật.

**23. Tại sao cần nêu phạm vi “state không đổi”?**  
Vì field, resource, callback và side effect ngoài tiến trình có phạm vi bảo đảm khác nhau.

**24. Khi lỗi có thể khiến kết quả phía xa chưa rõ, caller cần thông tin gì?**  
Request ID, outcome UNKNOWN hoặc khả năng truy vấn trạng thái và chính sách idempotency/retry.

## 49. Checklist thiết kế API có hậu điều kiện lỗi rõ ràng

1. **Viết invariant:** đối tượng hợp lệ phải thỏa điều kiện gì?
2. **Viết precondition:** đối số, state và quyền sở hữu của caller là gì?
3. **Viết normal postcondition:** thành công làm đổi điều gì?
4. **Viết exceptional postcondition:** nếu thất bại thì gì được giữ, gì có thể đổi?
5. **Chọn mức guarantee phù hợp:** basic, strong hay no-throw trong phạm vi cụ thể.
6. **Không hứa strong guarantee ngoài phạm vi có thể kiểm soát.**
7. **Validate trước mutation** khi có thể.
8. **Prepare dữ liệu vào local/immutable candidate**, commit sau cùng.
9. **Kiểm tra overflow và các phép có thể ném** trước khi gán.
10. **Không lộ mutable internal state** qua getter hoặc input alias.
11. **Xét callback/reentrancy** có thể quan sát trạng thái giữa chừng.
12. **Nếu có nhiều writer, thêm lock/CAS đúng phạm vi** thay vì chỉ `volatile` cho nhiều field.
13. **Nếu cho partial progress, ghi rõ** item nào có thể đã commit.
14. **Nếu có resource, xác định ownership và cleanup**, dùng try-with-resources khi phù hợp.
15. **Quan sát cause và suppressed** khi thao tác và cleanup cùng lỗi.
16. **Xem ghi file/network có thể partial**; đừng suy ra rollback khi IOException.
17. **Với transaction DB, xác định isolation, commit, rollback và trạng thái chưa rõ.**
18. **Với RPC có side effect, dùng idempotency/đối chiếu trạng thái** trước retry.
19. **Trạng thái FAILED/CLOSED/UNKNOWN có được định nghĩa rõ không?**
20. **Không bắt exception rồi trả thành công giả** nếu chưa phục hồi thật.
21. **Test đường lỗi tại mọi giai đoạn**: validation, prepare, write, notification, commit, close.
22. **Assert invariant và snapshot trước–sau**, không chỉ assert exception type.
23. **Kiểm tra tài nguyên và side effect ngoài**, không chỉ field Java.
24. **Viết JavaDoc về retry/reuse policy** sau exception.
25. **Đánh giá tính tương thích API** nếu thay đổi loại exception hoặc hậu điều kiện lỗi.

## 50. Kết luận

**Thiết kế API không kết thúc ở việc “phương thức ném exception nào”.** Một hợp đồng đáng tin phải định nghĩa **trạng thái hợp lệ trước khi gọi, trạng thái sau thành công, và trạng thái sau thất bại**.

Khi thất bại, có ba cách cam kết phổ biến: **basic guarantee** giữ invariant dù có partial progress; **strong guarantee** giữ nguyên trạng thái quan sát được trong phạm vi công bố; **no-throw guarantee** dành cho những thao tác thật sự có thể thiết kế không để exception thoát trong điều kiện hợp lệ. Java không tự kiểm chứng bất kỳ lời hứa nào trong ba mức này.

Để thực hiện, hãy ưu tiên **validate → prepare → commit**, dùng immutable snapshot/copy-then-swap khi thích hợp, kiểm tra overflow trước gán, quản lý tài nguyên bằng hợp đồng rõ và viết test thất bại ở từng bước. Đồng thời nhớ rằng **ghi I/O có thể partial, commit transaction có thể chưa rõ, và timeout từ hệ thống bên ngoài không chứng minh side effect chưa diễn ra**.

Câu hỏi quan trọng nhất để review mỗi API là:

> **“Nếu phương thức dừng bằng exception ngay tại bước này, caller có thể tin điều gì về trạng thái hiện tại và được phép làm gì tiếp?”**

Trả lời được câu hỏi đó bằng invariant, exceptional postcondition và test cụ thể là nền tảng của **API Java an toàn khi có lỗi**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-api-design-002">Chọn checked hay unchecked theo hợp đồng và khả năng xử lý</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn checked hay unchecked theo hợp đồng và khả năng xử lý trong Java

Một API Java phải cho người gọi biết **khi nào thao tác không thể hoàn thành** và bên gọi được kỳ vọng làm gì. Quyết định chọn **checked exception** hay **unchecked exception** không chỉ là chọn `extends Exception` hoặc `extends RuntimeException`: đó là quyết định về **hợp đồng giữa người triển khai và người sử dụng API**, đặc biệt về khả năng xử lý, phục hồi và chi phí lan truyền ngoại lệ qua nhiều tầng.

Nguyên tắc thực dụng: nếu một tình huống thất bại là **phần được dự kiến của thao tác** và phía sử dụng API có **hành động xử lý hợp lý** (thử lại, hỏi đầu vào khác, dùng nguồn dự phòng, bỏ qua một bản ghi theo chính sách, hoặc thực hiện bù trừ), **checked exception có thể phù hợp** để buộc caller xem xét tình huống ấy. Nếu lỗi biểu thị **dùng API sai, vi phạm tiền điều kiện, bug/invariant bị phá vỡ**, hoặc phía gọi không có chính sách phục hồi tại tầng đó, **unchecked exception thường phù hợp**.

Đây là **hướng dẫn thiết kế**, không phải quy tắc cơ học được Java cưỡng chế. Có lỗi hạ tầng bên ngoài được biểu diễn bằng checked `IOException`, cũng có lỗi I/O được bao bằng unchecked `UncheckedIOException` khi đi qua API callback không khai báo `throws`. Một caller có thể phục hồi một số unchecked exception, và một caller khác có thể không phục hồi nổi checked exception. Quan trọng là **hợp đồng cụ thể, mức độ ổn định của API, ranh giới trách nhiệm và ngữ nghĩa lỗi**.

Bài viết dùng Java 17+ để giải thích hệ phân cấp, `catch-or-specify`, cách đánh giá khả năng phục hồi, lựa chọn kiểu lỗi theo tầng, giữ cause khi dịch ngoại lệ, ngoại lệ trong interface/override, callback/bất đồng bộ, thử lại, transaction, lỗi đóng tài nguyên, `InterruptedException`, cách kiểm thử và những anti-pattern nên tránh.

## 1. Checked và unchecked: chính xác là gì?

Java phân loại theo hệ phân cấp `Throwable`:

~~~text
java.lang.Throwable
  ├── java.lang.Error                       [unchecked]
  │     ├── AssertionError
  │     └── VirtualMachineError
  └── java.lang.Exception
        ├── IOException                    [checked]
        ├── SQLException                   [checked]
        ├── InterruptedException           [checked]
        ├── CustomFailure extends Exception [checked, nếu không thuộc RuntimeException]
        └── java.lang.RuntimeException     [unchecked]
              ├── IllegalArgumentException
              ├── IllegalStateException
              ├── NullPointerException
              └── CustomFailure extends RuntimeException
~~~

Theo JLS §11.1.1, **unchecked exception classes** gồm `RuntimeException` và các lớp con, `Error` và các lớp con. Các lớp ngoại lệ còn lại trong nhánh `Throwable` là **checked**. Thông thường custom checked exception được tạo bằng `extends Exception`, còn custom unchecked exception bằng `extends RuntimeException`.

| Yếu tố | Checked | Unchecked |
| --- | --- | --- |
| Kế thừa thông dụng | `Exception` nhưng không thuộc nhánh `RuntimeException` | `RuntimeException` hoặc nhánh `Error` |
| Trình biên dịch buộc `catch` hoặc `throws`? | Có, khi checked exception có thể thoát khỏi phương thức và không được xử lý | Không |
| Có được khai báo `throws`? | Có | Có, nhưng không bắt buộc |
| Có lan truyền qua call stack nếu không bị bắt? | Có | Có |
| Có mang `message`, `cause`, `stack trace`? | Có | Có |
| Có thể được `catch`? | Có | Có, nếu chính sách phù hợp |
| Compiler có buộc caller **phục hồi thành công**? | Không; chỉ buộc bắt hoặc khai báo | Không |
| Dùng cho lỗi đầu vào/API misuse? | Tùy hợp đồng | Thường là `IllegalArgumentException` hoặc loại phù hợp |
| Dùng cho lỗi môi trường có khả năng sửa? | Thường là lựa chọn đáng cân nhắc | Cũng có thể phù hợp ở tầng không thể khai báo checked |

**Điểm quan trọng:** `catch-or-specify` là nghĩa vụ **tại biên dịch**, không phải bảo đảm lỗi đã được giải quyết. Caller có thể khai báo `throws` và chuyển trách nhiệm lên tầng trên. Ngược lại, unchecked vẫn có thể làm chương trình kết thúc đột ngột nếu không có tầng xử lý.

## 2. Ví dụ 1: sự khác nhau của hợp đồng ngay tại phương thức

Lưu thành `CheckedUncheckedContractDemo.java`:

~~~java
public class CheckedUncheckedContractDemo {
    static class DocumentUnavailableException extends Exception {
        DocumentUnavailableException(String message) {
            super(message);
        }
    }

    static class InvalidDocumentIdException extends RuntimeException {
        InvalidDocumentIdException(String message) {
            super(message);
        }
    }

    static String load(String id) throws DocumentUnavailableException {
        if (id == null || id.isBlank()) {
            throw new InvalidDocumentIdException("id must not be blank");
        }
        if ("offline".equals(id)) {
            throw new DocumentUnavailableException("document store offline");
        }
        return "document:" + id;
    }

    public static void main(String[] args) {
        try {
            System.out.println("ok=" + load("A-1"));
            load("offline");
        } catch (DocumentUnavailableException ex) {
            System.out.println("checked=" + ex.getMessage());
        }

        try {
            load("");
        } catch (InvalidDocumentIdException ex) {
            System.out.println("unchecked=" + ex.getMessage());
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 CheckedUncheckedContractDemo.java
java CheckedUncheckedContractDemo
~~~

Kết quả:

~~~text
ok=document:A-1
checked=document store offline
unchecked=id must not be blank
~~~

Giải thích:

- `DocumentUnavailableException extends Exception` là checked; `load` khai báo `throws DocumentUnavailableException` và caller bắt loại đó.
- `InvalidDocumentIdException extends RuntimeException` là unchecked; compiler không bắt buộc thêm nó vào `throws`. Nó diễn đạt **vi phạm tiền điều kiện của API**.
- Người gọi vẫn có thể bắt unchecked nếu thật sự cần biến lỗi tham số thành thông báo giao diện. Nhưng một tầng khác có thể chỉ cần sửa bug truyền tham số sai.
- Hai loại lỗi có **ý nghĩa khác nhau**: tài liệu tạm không truy cập được có thể gợi ý thử lại, còn ID rỗng phải được sửa tại đầu vào; không nên chọn kiểu chỉ dựa vào nơi exception được tạo.

Nếu bỏ `try-catch` hoặc `throws` cho `DocumentUnavailableException`, compiler từ chối khi phân tích lời gọi có thể phát sinh ngoại lệ checked. Nhưng ngay cả khi code biên dịch, vẫn cần chính sách thực tế sau khi bắt nó.

## 3. Khả năng xử lý hợp lý nghĩa là gì?

Một câu hỏi hữu ích trước khi thiết kế loại ngoại lệ là: **caller cụ thể có thể đưa hệ thống về trạng thái hợp lệ sau lỗi không?**

Các hành động xử lý có ý nghĩa có thể gồm:

1. **Thử lại có giới hạn**, khi lỗi có khả năng tạm thời và thao tác có thể retry an toàn.
2. **Chuyển sang nguồn dự phòng** đã được xác thực, khi fallback không làm sai dữ liệu.
3. **Yêu cầu sửa đầu vào**, chẳng hạn chọn file khác hoặc cấu hình lại vị trí lưu trữ.
4. **Bỏ qua mục lỗi có ghi nhận**, nếu mỗi mục độc lập và chính sách cho phép.
5. **Bù trừ/rollback**, khi một thao tác bị thất bại và trạng thái đã thay đổi một phần.
6. **Chuyển lỗi lên tầng có quyền quyết định**, bằng `throws` hoặc domain exception có cause.
7. **Dừng tác vụ đúng cách**, khi không có cơ hội phục hồi nhưng tầng hiện tại có trách nhiệm shutdown, cleanup, trả thông tin lỗi hoặc báo trạng thái thất bại.

**Chỉ log rồi nuốt ngoại lệ không được coi là phục hồi.** Tương tự, viết `catch (Exception ex) { throw new RuntimeException(ex); }` tự nó chỉ đổi cách lan truyền lỗi, không giúp phục hồi.

Một hệ thống xử lý lệnh có thể có caller A là giao diện người dùng, có thể đề nghị sửa một đường dẫn; caller B là job tự động, không thể hỏi người dùng và chỉ có thể báo thất bại. Cùng lỗi `IOException` nhưng chiến lược ở hai tầng khác nhau. Vì vậy quyết định checked/unchecked phải xét **đối tượng sử dụng API mục tiêu**, không chỉ tên ngoại lệ.

## 4. Bảng quyết định theo tình huống thực tế

| Tình huống | Lựa chọn thường đáng cân nhắc | Lý do / lưu ý |
| --- | --- | --- |
| Caller truyền chỉ số ngoài phạm vi công bố | `IndexOutOfBoundsException` unchecked | Vi phạm hợp đồng dùng API |
| Tham số bắt buộc nhưng nhận `null` | `NullPointerException` unchecked hoặc validation riêng | Code có thể sửa tại ranh giới |
| Phương thức gọi khi đối tượng đã đóng | `IllegalStateException` unchecked | Trạng thái không cho phép thao tác |
| Người dùng chọn file không tồn tại | `IOException` / checked domain exception | Caller có thể chọn file khác; không bắt buộc ở mọi API |
| Đọc từ thiết bị/mạng tạm thời lỗi | Checked hoặc unchecked domain exception | Xét quyền retry, idempotency, framework và tầng gọi |
| Lỗi quy tắc nghiệp vụ mà caller được yêu cầu điều chỉnh | Checked domain exception, validation result hoặc unchecked | Tùy liệu thất bại là đường nghiệp vụ dự kiến và contract giao tiếp |
| Lỗi SQL tại repository | `SQLException` checked ở API JDBC | Tầng service có thể dịch sang checked/unchecked domain failure |
| Lỗi I/O bên trong `Stream.map` | `UncheckedIOException` | `Function.apply` không khai báo checked |
| Vi phạm invariant do bug nội bộ | Unchecked, đôi khi `AssertionError` trong debug | Không ép caller xử lý như thất bại có thể phục hồi |
| API xử lý bulk input và cần báo nhiều dòng sai | `Result`/danh sách lỗi thay vì ném liên tục | Thất bại từng mục là kết quả dự kiến |
| Thread bị yêu cầu ngắt khi chờ | `InterruptedException` checked | Cần truyền tiếp hoặc restore interrupt đúng hợp đồng |
| JVM gặp `OutOfMemoryError` | `Error`, không tạo custom business Error | Thường không phục hồi đáng tin; cần thiết kế khả năng dừng/giám sát |

Đây là **bảng gợi ý**, không phải ánh xạ bắt buộc trong JLS. Ngay cả một lỗi do môi trường cũng có thể được biểu diễn dưới dạng unchecked khi thiết kế API đích yêu cầu như vậy; lúc ấy phải công bố rõ kiểu lỗi, giữ cause và chính sách xử lý.

## 5. Ví dụ 2: checked exception khi caller có phương án fallback

Giả sử một API tìm dữ liệu trên dịch vụ ngoài. Nếu đường truy cập tạm thời hỏng nhưng có dữ liệu cache được **xác nhận hợp lệ**, caller có thể chọn fallback. Đó là một tình huống mà checked exception giúp caller cân nhắc phương án.

Lưu thành `CheckedFallbackDemo.java`:

~~~java
import java.io.IOException;

public class CheckedFallbackDemo {
    static String remoteRead(boolean unavailable) throws IOException {
        if (unavailable) {
            throw new IOException("remote unavailable");
        }
        return "fresh-value";
    }

    static String readWithFallback(
            boolean remoteUnavailable, String cached) throws IOException {
        try {
            return remoteRead(remoteUnavailable);
        } catch (IOException ex) {
            if (cached != null) {
                return cached;
            }
            throw ex;
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println("remote="
                    + readWithFallback(false, "cached-value"));
            System.out.println("fallback="
                    + readWithFallback(true, "cached-value"));
            readWithFallback(true, null);
        } catch (IOException ex) {
            System.out.println("propagate=" + ex.getMessage());
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 CheckedFallbackDemo.java
java CheckedFallbackDemo
~~~

Kết quả:

~~~text
remote=fresh-value
fallback=cached-value
propagate=remote unavailable
~~~

Trong ví dụ, `readWithFallback` chỉ dùng cache khi cache tồn tại. Nếu không có cache, nó **ném lại nguyên ngoại lệ IOException**, để tầng trên quyết định. Đây mới là chính sách lỗi có chủ đích.

Trong ứng dụng thật, một cache “có dữ liệu” chưa chắc “đủ an toàn để dùng”: còn cần tuổi dữ liệu, tính nhất quán, quyền truy cập, phiên bản và yêu cầu độ mới. **Không làm fallback giả** bằng cách trả chuỗi rỗng hoặc dữ liệu bịa chỉ để tránh ngoại lệ.

## 6. Ví dụ 3: lỗi misuse nên được phát hiện bằng unchecked exception

Nếu caller truyền một `quantity` bằng 0 vào API có hợp đồng chỉ chấp nhận số dương, đây thường là **tiền điều kiện bị vi phạm**. Caller phải sửa đầu vào trước khi gọi; compiler không cần buộc xử lý checked exception ở mọi nơi.

Lưu thành `ArgumentContractDemo.java`:

~~~java
public class ArgumentContractDemo {
    static int price(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "quantity must be positive");
        }
        return Math.multiplyExact(quantity, 10);
    }

    public static void main(String[] args) {
        System.out.println("price=" + price(3));

        try {
            price(0);
        } catch (IllegalArgumentException ex) {
            System.out.println("invalid=" + ex.getMessage());
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 ArgumentContractDemo.java
java ArgumentContractDemo
~~~

Kết quả:

~~~text
price=30
invalid=quantity must be positive
~~~

Lỗi tham số được kiểm tra **bằng mã luôn chạy**, không bằng `assert` có thể bị vô hiệu hóa. Việc dùng `Math.multiplyExact` còn giúp phát hiện overflow số học bằng `ArithmeticException` khi có số lượng lớn gây tràn.

Một tình huống khác cần phân biệt: người dùng nhập chuỗi `"abc"` vào trường số lượng. Đây là lỗi xác thực đầu vào ở ranh giới UI/API. Tầng nhận request có thể trả `ValidationResult` hoặc map `NumberFormatException` thành lỗi trường; không nhất thiết phải tạo checked exception bắt buộc ở mọi lớp chỉ vì người dùng có thể sửa dữ liệu.

## 7. Đặt ranh giới hợp đồng: hạ tầng, domain, giao diện

Giả sử các tầng:

~~~text
HTTP / CLI / Batch boundary
          |
          v
Application service
          |
          v
Repository / external client
          |
          v
Database / file / network
~~~

Một lỗi hạ tầng như `SQLException` hoặc `IOException` có thể có ngữ nghĩa khác ở mỗi tầng:

- **Repository** hiểu lỗi theo công nghệ: kết nối cơ sở dữ liệu, tệp hỏng, giới hạn truy cập.
- **Application service** hiểu mục tiêu nghiệp vụ: không lấy được đơn hàng, không tạo được báo cáo, không lưu được thanh toán.
- **HTTP/UI boundary** hiểu hành động tiếp theo: trả mã lỗi, đề nghị thử lại, yêu cầu sửa tham số, hủy giao dịch, hiển thị thông báo hoặc thông báo cho vận hành.

Không phải mọi tầng đều cần bọc ngoại lệ. Chỉ **dịch loại lỗi khi vượt qua một ranh giới có ý nghĩa**, ví dụ khi repository không muốn lộ `SQLException` như một phần public contract của service.

Khi chuyển, ưu tiên constructor dạng:

~~~java
public OrderLoadException(String message, Throwable cause) {
    super(message, cause);
}
~~~

Đây là **constructor minh họa** của lớp kế thừa Exception hoặc RuntimeException có constructor tương ứng. `message` chứa ý nghĩa ở tầng hiện tại; `cause` giữ **chính đối tượng lỗi gốc** cùng stack trace và suppressed exceptions của nó. Đừng chỉ dùng `new OrderLoadException(ex.getMessage())` nếu còn cần chẩn đoán cause chain.

## 8. Ví dụ 4: chuyển lỗi hạ tầng thành checked domain exception

Giả sử tầng repository có thể ném `IOException` nhưng service muốn công bố một lỗi có ý nghĩa nghiệp vụ, để caller chọn nguồn dự phòng hoặc hiển thị trạng thái không khả dụng.

Lưu thành `CheckedTranslationDemo.java`:

~~~java
import java.io.IOException;

public class CheckedTranslationDemo {
    static final class ReportUnavailableException extends Exception {
        ReportUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static String repository() throws IOException {
        throw new IOException("storage offline");
    }

    static String loadReport() throws ReportUnavailableException {
        try {
            return repository();
        } catch (IOException ex) {
            throw new ReportUnavailableException(
                    "Report cannot be loaded", ex);
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println(loadReport());
        } catch (ReportUnavailableException ex) {
            System.out.println("domain=" + ex.getMessage());
            System.out.println("causeType="
                    + ex.getCause().getClass().getSimpleName());
            System.out.println("causeMessage="
                    + ex.getCause().getMessage());
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 CheckedTranslationDemo.java
java CheckedTranslationDemo
~~~

Kết quả:

~~~text
domain=Report cannot be loaded
causeType=IOException
causeMessage=storage offline
~~~

Đây là **exception translation**, không phải “xử lý thành công”. Caller biết service không hoàn thành thao tác và nhận loại lỗi domain mà service đã công bố. Một interface service có thể dùng `throws ReportUnavailableException` để buộc các caller trực tiếp xem xét trường hợp này.

Nếu service chỉ truyền nguyên `IOException` mà hợp đồng cũng đã hứa về IOException thì việc bọc lại là không cần thiết. Ngược lại, nếu tầng gọi không nên phụ thuộc API hạ tầng, một domain exception ổn định giúp thay implementation bên dưới mà ít ảnh hưởng caller.

## 9. Ví dụ 5: chuyển sang unchecked tại ranh giới API khi có lý do

Một API có thể chọn unchecked domain exception cho lỗi dịch vụ để không buộc mọi caller ở nhiều tầng thêm `throws`, hoặc vì framework callback không hỗ trợ checked exception. Khi đó vẫn cần **tên loại lỗi rõ ràng và bảo toàn nguyên nhân gốc**.

Lưu thành `UncheckedTranslationDemo.java`:

~~~java
import java.io.IOException;

public class UncheckedTranslationDemo {
    static final class StorageFailure extends RuntimeException {
        StorageFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static String readStorage() throws IOException {
        throw new IOException("disk unavailable");
    }

    static String service() {
        try {
            return readStorage();
        } catch (IOException ex) {
            throw new StorageFailure("Service data unavailable", ex);
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println(service());
        } catch (StorageFailure ex) {
            System.out.println("domain=" + ex.getMessage());
            System.out.println("checkedRequired=false");
            System.out.println("causeIsIOException="
                    + (ex.getCause() instanceof IOException));
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 UncheckedTranslationDemo.java
java UncheckedTranslationDemo
~~~

Kết quả:

~~~text
domain=Service data unavailable
checkedRequired=false
causeIsIOException=true
~~~

Dòng `checkedRequired=false` mô tả hợp đồng biên dịch: `StorageFailure extends RuntimeException` không bắt buộc `throws`. **Nó không chứng minh lỗi đã được khắc phục**. Nếu không có nhánh catch, StorageFailure vẫn sẽ lan truyền.

Đừng biến mọi checked exception thành unchecked chỉ để xóa cảnh báo compiler. Trước khi dịch, xác định **ai nhận trách nhiệm xử lý** và API công khai có mô tả đầy đủ loại lỗi mới hay không. Đổi sang unchecked có thể làm caller cũ không còn được compiler nhắc phải xử lý tình huống mà nghiệp vụ từng xem là quan trọng.

## 10. `throws` là một phần của hợp đồng, không phải danh sách tuyệt đối mọi lỗi

Ví dụ chữ ký:

~~~java
String fetchReport(String key) throws ReportUnavailableException;
~~~

Đây là **phương thức interface/minh họa**, giả định ReportUnavailableException là checked exception.

Ý nghĩa: chương trình có thể để checked exception ấy thoát ra khi thực thi, và caller cần bắt hoặc khai báo loại đó (hoặc lớp cha phù hợp). Điều này **không** có nghĩa:

- Phương thức **luôn** ném lỗi mỗi lần chạy.
- Không có unchecked exception nào khác có thể xảy ra.
- Việc caller viết `catch` bảo đảm đã phục hồi.
- Compiler bắt buộc caller biết tất cả lỗi môi trường có thể xảy ra dưới dạng checked exception.

Các checked exceptions có thể được xử lý bằng `catch` tại chỗ hoặc lan truyền với `throws`. `throws RuntimeException` cũng hợp lệ nhưng không tạo nghĩa vụ compiler tương tự.

**Không viết `throws Exception` theo thói quen** nếu public API có thể dùng các loại lỗi cụ thể hơn. Một kiểu quá rộng khiến caller khó biết nên retry, sửa input, hiển thị thông báo hay dừng. Nhưng nếu API thực sự có contract cho nhiều lỗi độc lập và không thể biểu diễn rõ bằng loại cụ thể, cân nhắc interface riêng, error type/domain hierarchy hoặc Result.

## 11. Interface và override: ràng buộc về checked exception

Một interface là hợp đồng cho mọi implementation. Java không cho override một phương thức bằng chữ ký ném **checked exception rộng hơn** hợp đồng mà nó override (JLS §8.4.8.3).

Ví dụ đúng:

~~~java
interface Loader {
    String load() throws java.io.IOException;
}

class MemoryLoader implements Loader {
    @Override
    public String load() {
        return "cached";
    }
}
~~~

Đây là **hai khai báo hợp lệ** khi ở cùng file/package; implementation thu hẹp bằng cách không ném checked exception nào. Ở điểm gọi qua tham chiếu kiểu `Loader`, compiler vẫn dựa trên `throws IOException` của interface.

### Ví dụ 6: kiểu khai báo của tham chiếu quyết định nghĩa vụ checked

Lưu thành `InterfaceContractDemo.java`:

~~~java
import java.io.IOException;

public class InterfaceContractDemo {
    interface Loader {
        String load() throws IOException;
    }

    static class MemoryLoader implements Loader {
        @Override
        public String load() {
            return "cached";
        }
    }

    public static void main(String[] args) {
        Loader asInterface = new MemoryLoader();
        MemoryLoader asConcrete = new MemoryLoader();

        try {
            System.out.println("interface=" + asInterface.load());
        } catch (IOException ex) {
            System.out.println("unexpected I/O");
        }

        System.out.println("concrete=" + asConcrete.load());
    }
}
~~~

~~~bash
javac -encoding UTF-8 InterfaceContractDemo.java
java InterfaceContractDemo
~~~

Kết quả:

~~~text
interface=cached
concrete=cached
~~~

Cùng đối tượng runtime MemoryLoader nhưng hai kiểu tham chiếu dẫn đến **hợp đồng biên dịch** khác nhau. `asInterface.load()` cần xử lý/khai báo IOException; `asConcrete.load()` không khai báo checked exception.

Ví dụ không hợp lệ:

~~~java
// CỐ Ý KHÔNG BIÊN DỊCH:
interface Service {
    void run() throws java.io.IOException;
}

class InvalidService implements Service {
    @Override
    public void run() throws Exception {
    }
}
~~~

Override bằng `throws Exception` mở rộng checked exception được phép so với `throws IOException`, vì vậy compiler từ chối. Muốn triển khai, dùng loại checked giống hoặc hẹp hơn, xử lý lỗi nội bộ, hoặc đổi hợp đồng interface một cách có chủ ý.

**Hệ quả khi thiết kế thư viện:** thêm một checked exception mới vào `throws` của public method có thể khiến code client trước đây đang biên dịch trở thành không hợp lệ. Thay đổi hợp đồng cần đánh giá ảnh hưởng tương thích mã nguồn.

## 12. Khi dùng callback/Stream, checked exception có thể không hợp đồng được

Một số functional interface chuẩn như `Function<T,R>`, `Consumer<T>`, `Supplier<T>` và `Runnable` **không khai báo checked exceptions** trong phương thức trừu tượng. Vì thế, lambda truyền cho `Stream.map` không thể trực tiếp ném `IOException` checked ra ngoài.

Đoạn sau **cố ý không biên dịch**:

~~~java
// GIẢ SỬ read(String) throws IOException:
java.util.function.Function<String, String> f =
        text -> read(text); // IOException chưa được xử lý trong lambda.
~~~

Không thể sửa bằng cách chỉ thêm `throws IOException` vào phương thức **tạo lambda**; thân lambda phải tương thích với **function type đích**.

Ba phương án thường dùng:

1. **Catch ngay trong lambda** khi có giá trị fallback thực sự hợp lệ.
2. **Bọc IOException bằng UncheckedIOException** và giữ cause, nếu callback phải báo lỗi cho tầng ngoài mà interface không khai báo checked.
3. **Thiết kế functional interface riêng có throws E** khi kiểm soát API và muốn giữ checked exception qua callback.

### Ví dụ 7: adapter I/O sang Function

Lưu thành `CallbackExceptionDemo.java`:

~~~java
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Function;

public class CallbackExceptionDemo {
    @FunctionalInterface
    interface IOFunction<T, R> {
        R apply(T input) throws IOException;
    }

    static <T, R> Function<T, R> unchecked(IOFunction<T, R> operation) {
        return input -> {
            try {
                return operation.apply(input);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        };
    }

    static String fetch(String id) throws IOException {
        if ("bad".equals(id)) {
            throw new IOException("remote read failed");
        }
        return "ok:" + id;
    }

    public static void main(String[] args) {
        Function<String, String> f = unchecked(CallbackExceptionDemo::fetch);
        System.out.println(f.apply("A"));

        try {
            f.apply("bad");
        } catch (UncheckedIOException ex) {
            System.out.println("wrapper="
                    + ex.getClass().getSimpleName());
            System.out.println("cause=" + ex.getCause().getMessage());
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 CallbackExceptionDemo.java
java CallbackExceptionDemo
~~~

Kết quả:

~~~text
ok:A
wrapper=UncheckedIOException
cause=remote read failed
~~~

Adapter làm rõ **tại nơi nào hợp đồng checked được chuyển thành unchecked**. Ứng dụng không tự suy ra mọi IOException là không thể phục hồi; nó chỉ nêu rằng lời gọi qua Function thông thường không có `throws IOException`. Bên nhận callback vẫn phải có cơ chế xử lý thất bại phù hợp.

Nếu viết một API mới mà caller có quyền bắt checked exception, dùng interface riêng như `IOFunction` thay vì luôn ép qua `Function` có thể là lựa chọn tốt hơn.

## 13. Khi thất bại là kết quả thông thường, cân nhắc Result/Outcome

Không phải mọi trường hợp “không thành công” đều cần ném exception. Ví dụ, nhập hàng loạt mã đơn hàng: một vài mã bị sai định dạng là tình huống **được dự kiến trước** và muốn báo tất cả lỗi để người dùng sửa cùng lúc.

Một giá trị kết quả có trạng thái rõ ràng có thể phù hợp hơn:

~~~text
parse item
  ├── hợp lệ  -> Success(value)
  └── sai     -> Failure(code, field)
~~~

Khi dùng Result, caller phải kiểm tra trạng thái nhưng không bắt exception ở mỗi phần tử. Tuy nhiên, **đừng biến lỗi hạ tầng nghiêm trọng thành giá trị trông như thành công** hoặc bỏ nguyên nhân khi cần vận hành.

### Ví dụ 8: phân biệt validation result và lỗi bất thường

Lưu thành `ValidationResultDemo.java`:

~~~java
import java.util.List;

public class ValidationResultDemo {
    record ValidationResult(String raw, Integer value, String errorCode) {
        boolean success() {
            return errorCode == null;
        }
    }

    static ValidationResult parsePositive(String raw) {
        try {
            int value = Integer.parseInt(raw);
            if (value <= 0) {
                return new ValidationResult(raw, null, "NOT_POSITIVE");
            }
            return new ValidationResult(raw, value, null);
        } catch (NumberFormatException ex) {
            return new ValidationResult(raw, null, "INVALID_INTEGER");
        }
    }

    public static void main(String[] args) {
        for (String raw : List.of("10", "bad", "-2", "8")) {
            ValidationResult result = parsePositive(raw);
            if (result.success()) {
                System.out.println("ok=" + result.value());
            } else {
                System.out.println("error=" + result.errorCode());
            }
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 ValidationResultDemo.java
java ValidationResultDemo
~~~

Kết quả:

~~~text
ok=10
error=INVALID_INTEGER
error=NOT_POSITIVE
ok=8
~~~

Bài toán này không yêu cầu một checked exception để caller chọn retry. Những giá trị không hợp lệ là dữ liệu đầu vào bình thường trong quy trình kiểm tra và được trình bày bằng mã ổn định. Trong ứng dụng sản xuất, record có thể cần constructor/factory để cưỡng chế bất biến “thành công XOR thất bại”.

**Không hiểu sai rằng Result luôn tốt hơn exception**. Nếu API phục hồi bằng exception một cách tự nhiên hoặc thất bại có tính bất thường, dùng exception giúp không làm phức tạp đường thành công. Quyết định phụ thuộc cả tần suất lỗi và nhu cầu của caller.

## 14. Retry không phải cứ gặp ngoại lệ là thử lại

Một số checked exception phản ánh môi trường và **có thể** tạm thời, nhưng không phải mọi lỗi cùng kiểu đều nên retry.

Ví dụ, `IOException` có thể do mạng ngắt quãng, file bị thiếu vĩnh viễn, quyền đọc bị từ chối hoặc dữ liệu bị hỏng. Chỉ tên lớp không đủ để kết luận retry là an toàn.

Trước khi retry, cần hỏi:

- **Lỗi có khả năng tạm thời không?** Lỗi kết nối khác với file không tồn tại vĩnh viễn.
- **Thao tác có idempotent không?** Retry lệnh thanh toán sau timeout có thể tạo giao dịch trùng nếu server đã xử lý yêu cầu trước khi kết nối bị mất.
- **Có giới hạn lượt và thời gian không?** Không retry vô hạn.
- **Có backoff/jitter không?** Tùy hệ thống, tránh tạo áp lực đồng thời cho dịch vụ đang lỗi.
- **Có tín hiệu hủy/interrupt không?** Phải dừng khi bị yêu cầu.
- **Có điều kiện chuyển sang fallback hoặc báo lỗi cuối cùng không?**

### Ví dụ 9: retry có giới hạn, chỉ với lỗi I/O minh họa

Lưu thành `RetryPolicyDemo.java`:

~~~java
import java.io.IOException;

public class RetryPolicyDemo {
    private static int calls;

    static String readRemote() throws IOException {
        calls++;
        if (calls == 1) {
            throw new IOException("temporary outage");
        }
        return "READY";
    }

    static String retryAtMostThreeTimes() throws IOException {
        IOException last = null;

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return readRemote();
            } catch (IOException ex) {
                last = ex;
                System.out.println("retry-after=" + attempt);
            }
        }

        throw last;
    }

    public static void main(String[] args) {
        try {
            System.out.println("result=" + retryAtMostThreeTimes());
            System.out.println("calls=" + calls);
        } catch (IOException ex) {
            System.out.println("failed=" + ex.getMessage());
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 RetryPolicyDemo.java
java RetryPolicyDemo
~~~

Kết quả:

~~~text
retry-after=1
result=READY
calls=2
~~~

Ví dụ cố ý không sleep để stdout xác định, chỉ minh họa một hành động phục hồi có giới hạn. Nó **không phải** policy retry dùng ngay cho hệ thống phân tán. Trong sản xuất nên phân loại lỗi tạm thời, backoff, idempotency, cancellation, time budget và khả năng giao dịch đã hoàn tất nhưng phản hồi bị mất.

Khi exhaustion xảy ra, ví dụ ném lại `IOException` cuối cùng. Caller vẫn nhìn thấy thất bại thực sự; code không âm thầm trả giá trị giả.

## 15. Giữ cause và suppressed exceptions khi chuyển loại lỗi

Một ngoại lệ có thể chứa:

- `getMessage()`: thông tin lỗi ở tầng hiện tại.
- `getCause()`: ngoại lệ trước đó đã gây ra lỗi hiện tại.
- `getStackTrace()`: các frame liên quan đến điểm Throwable được ghi stack trace.
- `getSuppressed()`: các lỗi phụ, thường phát sinh khi đóng tài nguyên trong try-with-resources.

Khi bọc một checked exception thành checked hoặc unchecked domain exception bằng `super(message, cause)`, JVM **giữ tham chiếu đến cause**, không tự sao chép danh sách suppressed sang wrapper. Lỗi phụ vẫn có thể được truy qua cause.

### Ví dụ 10: lỗi đọc là primary, lỗi đóng là suppressed

Lưu thành `SuppressedDomainDemo.java`:

~~~java
import java.io.IOException;

public class SuppressedDomainDemo {
    static class Resource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            throw new IOException("close failed");
        }
    }

    static class ImportFailure extends Exception {
        ImportFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static void repository() throws IOException {
        try (Resource resource = new Resource()) {
            throw new IOException("read failed");
        }
    }

    static void service() throws ImportFailure {
        try {
            repository();
        } catch (IOException ex) {
            throw new ImportFailure("Import unavailable", ex);
        }
    }

    public static void main(String[] args) {
        try {
            service();
        } catch (ImportFailure ex) {
            Throwable root = ex.getCause();
            System.out.println("domain=" + ex.getMessage());
            System.out.println("cause=" + root.getMessage());
            System.out.println("causeSuppressed="
                    + root.getSuppressed()[0].getMessage());
            System.out.println("wrapperSuppressed="
                    + ex.getSuppressed().length);
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 SuppressedDomainDemo.java
java SuppressedDomainDemo
~~~

Kết quả:

~~~text
domain=Import unavailable
cause=read failed
causeSuppressed=close failed
wrapperSuppressed=0
~~~

Điểm quan trọng: **dịch ngoại lệ có cause** không làm mất lỗi đóng tài nguyên của đối tượng IOException; nó vẫn nằm trong `root.getSuppressed()`. Nhưng wrapper mới không tự có suppressed tương tự. Khi log, nên truyền **đối tượng Throwable** cho logger để hiển thị cause chain/suppressed thay vì chỉ lấy `getMessage()` rồi bỏ phần còn lại.

## 16. `InterruptedException` không nên bọc/nuốt như lỗi thông thường

`InterruptedException` là checked exception đặc biệt thường báo một thao tác blocking đã bị yêu cầu ngắt. Nhiều phương thức như `Thread.sleep` có thể **xóa cờ interrupt khi ném InterruptedException**.

Khi bị ngắt:

- Nếu chữ ký cho phép, **truyền `InterruptedException` lên tầng có quyền quyết định hủy tác vụ**.
- Nếu API (ví dụ `Runnable.run()`) không thể khai báo checked exception này, thường nên **khôi phục cờ interrupt** bằng `Thread.currentThread().interrupt()` rồi kết thúc hoặc chuyển theo policy hủy.
- Không chỉ log và tiếp tục thao tác dài như thể chưa nhận tín hiệu, trừ khi có lý do và contract rõ.
- Không bọc `InterruptedException` thành `UncheckedIOException`: không đúng loại và sai ý nghĩa lỗi.

### Ví dụ 11: bảo toàn trạng thái gián đoạn

Lưu thành `InterruptContractDemo.java`:

~~~java
public class InterruptContractDemo {
    static void runTask() {
        Thread.currentThread().interrupt();

        try {
            Thread.sleep(1);
            System.out.println("unexpected");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            System.out.println("restored="
                    + Thread.currentThread().isInterrupted());
        }
    }

    public static void main(String[] args) {
        runTask();
    }
}
~~~

~~~bash
javac -encoding UTF-8 InterruptContractDemo.java
java InterruptContractDemo
~~~

Kết quả:

~~~text
restored=true
~~~

Vì cờ interrupt được đặt ngay trước khi gọi sleep, lời gọi phát hiện gián đoạn và ném InterruptedException. Trong catch, chương trình đặt lại cờ, bảo toàn tín hiệu cho logic phía ngoài. Đoạn demo kết thúc ngay sau đó; một tác vụ thật nên có chính sách dừng, cleanup và báo trạng thái bị hủy.

Việc InterruptException là checked **không làm nó tự động có thể phục hồi bằng retry**. Ý nghĩa hợp đồng của nó thường là phối hợp hủy/dừng tác vụ.

## 17. Checked exception và API bất đồng bộ

Các API bất đồng bộ có ranh giới ngoại lệ khác với lời gọi trực tiếp. Ví dụ `CompletableFuture.supplyAsync` nhận `Supplier<T>` không khai báo checked exception. Nếu tác vụ có thể ném IOException, lambda thường phải bắt và bọc lỗi thành unchecked; caller quan sát thất bại qua `join()` hoặc `get()`.

Các loại đáng nhớ:

- `CompletionException`: unchecked wrapper thường được thấy khi `CompletableFuture.join()` thất bại.
- `ExecutionException`: checked wrapper thường được thấy khi gọi `Future.get()`.
- `InterruptedException`: có thể phát sinh khi thread đang chờ `Future.get()` bị ngắt.
- `CancellationException`: unchecked, thể hiện tác vụ đã bị hủy trong nhiều API.
- Một checked exception do code chạy bên trong task ném có thể được **bọc lại**, nên không suy ra caller của join/get sẽ thấy trực tiếp đúng loại ngoại lệ gốc.

### Ví dụ 12: cause chain qua CompletableFuture

Lưu thành `AsyncFailureDemo.java`:

~~~java
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class AsyncFailureDemo {
    public static void main(String[] args) {
        CompletableFuture<String> future =
                CompletableFuture.supplyAsync(() -> {
                    try {
                        throw new IOException("storage down");
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                });

        try {
            System.out.println(future.join());
        } catch (CompletionException ex) {
            System.out.println("outer="
                    + ex.getClass().getSimpleName());
            System.out.println("wrapper="
                    + ex.getCause().getClass().getSimpleName());
            System.out.println("root="
                    + ex.getCause().getCause().getMessage());
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 AsyncFailureDemo.java
java AsyncFailureDemo
~~~

Kết quả:

~~~text
outer=CompletionException
wrapper=UncheckedIOException
root=storage down
~~~

Tầng gọi nhận một CompletionException và có thể duyệt cause chain. Đừng chỉ bắt IOException quanh `future.join()` rồi nghĩ đó là cách xử lý trực tiếp: IOException là checked và chữ ký join không khai báo nó.

Ví dụ dùng task thất bại xác định, không có tác vụ khác hoặc race về kết quả. Trong ứng dụng thật, cần xét executor, timeout, cancellation, trạng thái transaction và nơi log lỗi.

## 18. Ngoại lệ, transaction và nhất quán dữ liệu

Một caller “có thể catch” không đồng nghĩa nó **nên tiếp tục giao dịch**. Ví dụ, thao tác lưu đơn hàng gồm nhiều bước có thể thất bại ở bước cuối sau khi vài thay đổi đã xảy ra. Khi đó:

- Bắt lỗi để **rollback hoặc bù trừ** trước khi trả kết quả.
- Nếu không bảo đảm toàn vẹn, phải thông báo trạng thái không rõ ràng và đưa lỗi tới tầng điều phối.
- Tránh retry tự động một lệnh có side effect nếu không có **idempotency key** hoặc cơ chế chống trùng.
- Không nuốt SQLException rồi trả thành công giả; hệ thống có thể đã không lưu đúng dữ liệu.
- Nếu dùng framework transaction, hãy hiểu rõ **quy tắc rollback** của framework, vì checked/unchecked có thể được framework diễn giải khác nhau theo cấu hình. Đây là chính sách framework, **không phải quy tắc JLS**.

Một checked exception có thể cần được trả lên thay vì xử lý tại repository. Một unchecked exception có thể cần được chuyển thành mã phản hồi ổn định ở HTTP boundary. Quyết định thiết kế không nên chỉ dựa trên loại compiler yêu cầu, mà còn trên **quyền kiểm soát trạng thái và hành động sau thất bại**.

## 19. Nguyên tắc bảo vệ hợp đồng công khai khi chọn loại lỗi

Khi public API chọn checked hoặc unchecked, hãy giữ ranh giới có ý nghĩa với caller:

- **Tên và loại lỗi ổn định** giúp caller quyết định sửa input, fallback, retry hay dừng; đừng trả một `RuntimeException("failed")` chung cho mọi trường hợp.
- **Vi phạm tiền điều kiện** thường báo bằng `IllegalArgumentException`, `NullPointerException` hoặc một loại unchecked phù hợp. Đừng dùng `assert` có thể bị vô hiệu hóa để kiểm tra input công khai.
- **Không lộ phụ thuộc hạ tầng ngoài ý muốn.** Nếu thay JDBC bằng HTTP mà caller phải sửa hàng loạt `catch (SQLException)`, thiết kế abstraction có thể chưa ổn; chuyển nghĩa ở ranh giới sở hữu hợp đồng và giữ `cause`.
- **Không lạm dụng `Error` cho nghiệp vụ** và không tạo cây custom exceptions nhiều tầng nếu các lớp không khác nhau về hành động xử lý.
- **Xem xét tác động nâng cấp.** Thêm/bớt checked `throws`, đổi class exception hoặc chuyển checked sang unchecked có thể làm mã nguồn hoặc hành vi caller thay đổi, kể cả khi binary linkage vẫn hoạt động.
- **Tài liệu hóa và kiểm thử đúng kết cục.** Caller cần biết loại lỗi, tình huống phát sinh, hậu điều kiện trạng thái và khả năng retry, không chỉ tên class.

Bài về ranh giới phụ thuộc và bài tương thích khi đổi hợp đồng ngoại lệ phân tích sâu hai rủi ro cuối. Ở đây tiêu chí chọn checked/unchecked vẫn là **khả năng và trách nhiệm xử lý hợp lý của caller**.

## 20. Anti-pattern: ba cách khiến hợp đồng lỗi trở nên khó dùng

### A. “Mọi lỗi đều là RuntimeException vì dễ viết code”

~~~java
catch (java.io.IOException ex) {
    throw new RuntimeException("failed");
}
~~~

Đây là **mã minh họa không độc lập và có vấn đề**. Nó vừa dùng loại lỗi quá chung, vừa bỏ cause. Một caller không biết thao tác gì thất bại và không thể nhìn lại IOException gốc bằng getCause.

Sửa theo contract của tầng:

~~~java
catch (java.io.IOException ex) {
    throw new StorageFailure("Cannot load report", ex);
}
~~~

Đoạn sau giả định `StorageFailure(String, Throwable)` đã khai báo; có thể chọn extends RuntimeException nếu tầng này thực sự muốn unchecked domain failure.

### B. “Mọi lỗi đều checked vì có thể xảy ra”

Đừng khai báo `throws Exception` trên mọi public method chỉ để “tài liệu hóa khả năng lỗi”. Điều đó làm caller khó phân biệt lỗi đầu vào, lỗi có thể retry và lỗi programmer. Unchecked exceptions vẫn tồn tại ngoài danh sách throws.

Hãy công bố **những loại checked mà caller được kỳ vọng xử lý**, và tài liệu hóa các unchecked quan trọng nếu caller cần biết. Không cố liệt kê mọi RuntimeException có thể phát sinh từ JDK.

### C. “Đã catch là xem như thành công”

~~~java
try {
    saveOrder();
} catch (Exception ex) {
    System.out.println("cannot save");
}
return true;
~~~

Đây là **mã minh họa sai chính sách**: nếu thao tác lưu thất bại, việc trả thành công giả có thể gây lỗi dữ liệu nghiêm trọng. Catch không tự tạo rollback, retry hoặc trạng thái thành công.

Sửa bằng **chính sách rõ ràng**: rollback rồi trả thất bại, chuyển exception có cause, hoặc fallback có tính đúng đắn; không dùng log như một cách thay thế xử lý lỗi.

## 21. Quy trình quyết định checked hay unchecked

Khi thiết kế một phương thức public, có thể dùng quy trình sau:

1. **Xác định thao tác và caller mục tiêu.** Ai gọi API: người dùng tương tác, service nội bộ, callback, batch job hay thư viện bên ngoài?
2. **Liệt kê các nhóm thất bại có ý nghĩa.** Lỗi đầu vào, trạng thái, tài nguyên không có, lỗi kết nối, timeout, hủy, dữ liệu không hợp lệ, invariant nội bộ.
3. **Gắn hành động mong đợi cho từng nhóm.** Sửa input, thử lại, fallback, rollback, bỏ qua có ghi nhận, dừng, cảnh báo vận hành hay sửa bug.
4. **Phân biệt expected outcome với exceptional failure.** Nếu “không có kết quả” là đường đi bình thường, có thể dùng `Optional` hoặc `Result`. Nếu caller cần bị buộc xem xét, checked exception có thể hữu ích.
5. **Đánh giá ngữ nghĩa lỗi.** Misuse/precondition thường unchecked; lỗi môi trường có khả năng xử lý thường đáng cân nhắc checked.
6. **Xem ràng buộc framework/API.** Functional interface, reactive/async API, interface overrides và code client ảnh hưởng hợp đồng khả thi.
7. **Chọn tên và hệ phân cấp ổn định.** Tránh loại `Exception` quá rộng và phân cấp quá sâu không có chính sách khác biệt.
8. **Thiết kế message/cause/metadata.** Có constructor `(String, Throwable)` khi dịch; bổ sung error code hoặc field khi caller cần xử lý theo cấu trúc.
9. **Quyết định nơi xử lý.** Tầng nhận lỗi có quyền làm gì? Nếu không thể xử lý, truyền lên thay vì catch rỗng.
10. **Xem tác dụng phụ và retry.** Bảo đảm idempotency, transaction, backoff, cancellation, resource cleanup nếu có.
11. **Viết test từ góc nhìn caller.** Thử đường thành công, từng loại thất bại, cause chain, rollback, retry và trường hợp không xử lý.
12. **Đánh giá tương thích khi thay đổi contract.** Nếu thư viện đã public, thêm/bỏ checked throws không chỉ là sửa nội bộ.

### Bảng quyết định ngắn

| Câu hỏi | Gợi ý |
| --- | --- |
| Caller **phải** có chính sách xử lý rõ? | Cân nhắc checked |
| Caller không có cách phục hồi hợp lý tại mọi điểm gọi? | Cân nhắc unchecked hoặc truyền lỗi tới boundary |
| Lỗi do dùng API sai/đầu vào không thỏa precondition? | Thường unchecked hoặc validation result |
| Lỗi là một kết quả nghiệp vụ thường xuyên? | Cân nhắc Result/Optional/status code |
| API đích không hỗ trợ checked throws? | Adapter/unchecked có cause hoặc interface riêng |
| Tầng service cần che chi tiết JDBC/file? | Dịch sang domain exception phù hợp, giữ cause |
| Lỗi phát sinh khi chờ bị interrupt? | Truyền hoặc restore interrupt, xử lý hủy |
| Lỗi có side effect một phần? | Rollback/bù trừ trước khi coi phục hồi |
| Thay đổi public signature tác động nhiều caller? | Xem tương thích nguồn và lộ trình migrate |

## 22. Câu hỏi tự kiểm tra

**1.** Checked exception có nghĩa caller đã phục hồi thành công chưa?  
**Chưa.** Compiler chỉ buộc `catch` hoặc `throws`; phục hồi phụ thuộc logic ứng dụng.

**2.** RuntimeException có thể bắt và xử lý không?  
**Có.** Unchecked nghĩa là không bắt buộc ở compile time, không phải “không thể catch”.

**3.** Lỗi nhập tham số âm vào API chỉ nhận số dương nên thường dùng loại nào?  
`IllegalArgumentException` unchecked hoặc một validation result theo hợp đồng.

**4.** IOException luôn bắt buộc được bọc thành checked domain exception không?  
**Không.** Tùy tầng và policy, có thể truyền IOException, bọc checked domain, bọc unchecked domain hoặc phục hồi.

**5.** Vì sao chỉ viết `new RuntimeException(ex.getMessage())` khi bọc lỗi là kém?  
Vì mất tham chiếu cause, loại lỗi gốc, stack trace và các suppressed exceptions đi kèm.

**6.** Nếu method trong interface không khai báo checked exception, implementation được thêm `throws IOException` không?  
**Không** khi IOException không được phép bởi hợp đồng override.

**7.** Một public API có thể khai báo `throws RuntimeException` không?  
**Có**, nhưng compiler không buộc caller catch/specify theo đó.

**8.** Vì sao thêm một checked exception vào API public có thể phá code client?  
Vì các caller trước đây chưa catch hoặc throws loại lỗi mới có thể không biên dịch.

**9.** Khi một kết quả “không tìm thấy” diễn ra thường xuyên và được dự kiến, có buộc phải tạo exception không?  
**Không.** Có thể chọn Optional/Result nếu phù hợp hợp đồng.

**10.** Có nên retry mọi IOException không?  
**Không.** Cần đánh giá lỗi tạm thời, idempotency, giới hạn lượt, backoff và cancellation.

**11.** Khi catch InterruptedException trong Runnable, làm thế nào để không làm mất tín hiệu?  
Thường khôi phục cờ bằng `Thread.currentThread().interrupt()` và kết thúc/hủy theo policy.

**12.** Checked exception của task async sẽ luôn thoát trực tiếp từ CompletableFuture.join() dưới cùng kiểu không?  
**Không.** Task có thể bọc lỗi và join thường báo CompletionException; phải xem cause chain.

**13.** Một wrapper mới có tự copy suppressed exceptions từ cause không?  
**Không.** Suppressed của cause vẫn thuộc đối tượng cause, có thể đọc qua getCause().getSuppressed().

**14.** Catch Exception rồi log có phải luôn là recovery không?  
**Không.** Nó có thể chỉ là ghi nhận; cần quyết định trả thất bại, retry, rollback, fallback hoặc truyền lỗi.

**15.** Vì sao “mọi lỗi từ bên ngoài checked, mọi lỗi từ code unchecked” không hoàn toàn chính xác?  
Vì policy API còn phụ thuộc quyền phục hồi, khả năng caller, framework, ranh giới tầng và mức ổn định hợp đồng; cùng lỗi I/O có thể xuất hiện dưới cả IOException và UncheckedIOException.

## 23. Tài liệu tham khảo chuẩn

- [JLS Java SE 21 — §11.1.1: The Kinds of Exceptions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-11.html#jls-11.1.1) — phân loại checked và unchecked.
- [JLS 21 — §11.2: Compile-Time Checking of Exceptions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-11.html#jls-11.2) — nghĩa vụ catch-or-specify và hợp đồng.
- [JLS 21 — §8.4.6: Method Throws](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.4.6) — khai báo throws.
- [JLS 21 — §8.4.8.3: Requirements in Overriding and Hiding](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.4.8.3) — giới hạn checked exceptions khi override.
- [Oracle Java Tutorials — Unchecked Exceptions: The Controversy](https://docs.oracle.com/javase/tutorial/essential/exceptions/runtime.html) — hướng dẫn chọn theo khả năng phục hồi; tài liệu mang tính hướng dẫn, không thay JLS.
- [Oracle Java Tutorials — The Catch or Specify Requirement](https://docs.oracle.com/javase/tutorial/essential/exceptions/catchOrDeclare.html).
- [Java SE 21 — Throwable](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Throwable.html) — cause, suppressed, stack trace.
- [Java SE 21 — IOException](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/IOException.html).
- [Java SE 21 — UncheckedIOException](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/UncheckedIOException.html).
- [Java SE 21 — CompletableFuture](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html).

**Ghi nhớ:** Quyết định checked hay unchecked là thiết kế **hợp đồng xử lý lỗi**. Checked yêu cầu caller cân nhắc lỗi ở compile time; unchecked không tạo nghĩa vụ đó nhưng vẫn có thể cần xử lý tại boundary. Hãy xuất phát từ người gọi có thể làm gì khi thất bại, giữ nguyên nhân kỹ thuật khi dịch loại lỗi, tránh bắt rộng/nuốt lỗi và kiểm thử cả đường phục hồi lẫn đường thất bại không thể phục hồi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-api-design-003">Dùng loại ngoại lệ cụ thể thay vì throws Exception</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng loại ngoại lệ cụ thể thay vì `throws Exception` khi thiết kế API Java

> **Chủ đề:** Java Core → Exception → Thiết kế API. Mệnh đề `throws` là một phần của **hợp đồng phương thức** đối với checked exceptions. Khai báo `throws Exception` tuy hợp lệ nhưng thường **quá rộng** cho API ứng dụng/thư viện: người gọi không biết chính xác mình cần xử lý lỗi nào, khó phân nhánh chính sách khôi phục, và có thể vô tình phụ thuộc vào một hợp đồng cho phép quá nhiều loại thất bại. Hãy ưu tiên các loại ngoại lệ **có ngữ nghĩa, ở đúng mức trừu tượng và đủ hẹp**, chẳng hạn `IOException`, `ParseException` hoặc một custom checked exception phù hợp; đồng thời hiểu rằng không phải API nào cũng cần checked exception.

## 1. `throws Exception` có nghĩa là gì?

Một khai báo:

```java
public String load() throws Exception {
    // ...
}
```

nói với Java compiler rằng phương thức có thể kết thúc bất thường bằng một **checked exception** thuộc `Exception`, trong đó có `IOException`, `SQLException`, `ReflectiveOperationException`, custom checked exceptions và nhiều loại khác. Nó **không** khẳng định method luôn có lỗi, cũng không có nghĩa tất cả các subtype đều thực sự xuất hiện.

Hai ý cần tách riêng:

1. **Tập loại checked mà chữ ký cho phép:** `throws Exception` rộng hơn `throws IOException`.
2. **Những lỗi thực tế có thể xảy ra:** phụ thuộc vào mã chạy và các phương thức được gọi.

`Exception` còn là lớp cha của `RuntimeException`, nhưng các subclass unchecked như `IllegalArgumentException` **không cần** kê trong `throws` dù caller có thể gặp chúng.

### 1.1. Cây loại ngoại lệ liên quan

```text
Throwable
├── Error                         (unchecked)
└── Exception
    ├── IOException              (checked)
    │   └── FileNotFoundException (checked)
    ├── SQLException              (checked)
    ├── ParseException            (checked)
    ├── ReflectiveOperationException (checked)
    └── RuntimeException          (unchecked)
        ├── IllegalArgumentException
        └── IllegalStateException
```

Một `throws IOException` không bắt buộc kê mọi subtype `FileNotFoundException`: subtype đã nằm trong hợp đồng của superclass. Ngược lại, `throws FileNotFoundException` chỉ công bố nhánh hẹp và **không cho method tùy ý để `IOException` loại khác thoát theo checked contract**.

### 1.2. So sánh trực tiếp

```java
// Rộng nhưng hợp lệ về cú pháp:
static String readConfig() throws Exception {
    return java.nio.file.Files.readString(
            java.nio.file.Path.of("config.txt"));
}

// Rõ hơn khi thao tác chủ yếu liên quan I/O:
static String readConfigSpecific() throws java.io.IOException {
    return java.nio.file.Files.readString(
            java.nio.file.Path.of("config.txt"));
}
```

Đây là **hai method minh họa**, không phải một chương trình độc lập; cả hai có thể ném lỗi khi không đọc được file thật. Chữ ký thứ hai nói đúng nhóm failure được API trực tiếp sử dụng, giúp caller hiểu có thể chọn nguồn khác, báo lỗi đọc file hoặc dừng tác vụ.

**Không có quy tắc tuyệt đối** phải khai báo class con nhỏ nhất có thể tưởng tượng. Chọn loại **đủ cụ thể cho quyết định của caller** và đủ ổn định trước những thay đổi nội bộ.

## 2. Tại sao khai báo quá rộng làm caller khó xử lý?

Xét API:

```java
interface DocumentRepository {
    String load(String documentId) throws Exception;
}
```

Một caller không biết `load` có thể ném checked `IOException`, `SQLException`, `ParseException` hay loại khác. Code gọi phải xét toàn bộ `Exception`.

```java
try {
    String text = repository.load("DOC-1");
    System.out.println(text);
} catch (Exception e) {
    // Xử lý chung hoặc ném lên tiếp.
}
```

Đây là mảnh code cần biến `repository` đã khởi tạo. `catch(Exception)` có thể trở thành một nhánh quá rộng với nhiều failure **khác ý nghĩa**:

- File không tồn tại: có thể cho người dùng chọn file khác.
- Timeout từ dịch vụ phụ thuộc: có thể retry **nếu thao tác an toàn để retry**.
- Tài liệu không hợp lệ: cần thông báo dữ liệu sai, thường không retry.
- Bug lập trình như `NullPointerException`: không nên vô tình coi là lỗi tạm thời có thể retry.

**Một catch rộng không sai cú pháp**, nhưng nếu code “log rồi trả null” hoặc “retry mọi trường hợp”, nó có thể che bug và làm hợp đồng kết quả sai.

### 2.1. Compiler giúp được gì?

Với:

```java
void handle() throws Exception {
    repository.load("DOC-1");
}
```

compiler thấy method gọi đã được bao phủ, nhưng không giúp người bảo trì biết liệu cần xử lý lỗi file, database, parse hay nghiệp vụ.

Nếu chữ ký được làm cụ thể:

```java
interface DocumentRepository {
    String load(String documentId)
            throws java.io.IOException, DocumentFormatException;
}
```

caller nhìn vào API có thể quyết định **khôi phục I/O** khác với **từ chối format sai**. `DocumentFormatException` ở đây là một custom checked exception cần khai báo trong dự án (ví dụ sẽ có bên dưới).

### 2.2. Có thể vẫn viết `catch(IOException)` khi API `throws Exception`?

Có thể có những trường hợp `catch(IOException)` hợp lệ về compile-time vì API khai báo `throws Exception` — nhưng handler đó **không đủ** để xử lý toàn bộ checked `Exception` còn lại.

```java
try {
    broadApi(); // Khai báo throws Exception.
} catch (java.io.IOException e) {
    System.out.println("I/O");
} catch (Exception e) {
    System.out.println("Checked khác / unchecked khác");
}
```

Đây là mảnh code có `broadApi()` giả định. Handler `catch(Exception)` còn lại bắt cả những `RuntimeException` subtype, nên nếu code đó cố “phục hồi mọi lỗi” sẽ rất nguy hiểm. **Thu hẹp chữ ký ở API** có thể giảm nhu cầu về handler chung quá rộng ngay từ gốc.

## 3. Nguyên tắc chọn ngoại lệ có ý nghĩa

Một loại ngoại lệ tốt giúp caller trả lời:

1. **Điều gì thất bại?** Đọc file, phân tích cú pháp, kết nối, xác nhận trạng thái hay vi phạm hợp đồng?
2. **Ai có thể xử lý?** Caller trực tiếp, tầng service, retry layer, UI hay chỉ ranh giới log?
3. **Có thể làm gì?** Thử input khác, fallback, retry có điều kiện, hủy thao tác, báo lỗi người dùng?
4. **Có nên tiết lộ chi tiết bên trong?** Một service domain có cần public hóa `SQLException` không?
5. **Loại đó có ổn định khi implementation thay đổi?** Nếu đổi storage từ file sang HTTP, API có cần đổi `throws IOException`?

Các câu trả lời có thể dẫn tới:

- **Loại JDK phù hợp**, ví dụ `IOException` khi API bản chất là thao tác I/O.
- **Một custom checked exception**, ví dụ `DocumentFormatException extends Exception` cho dữ liệu được yêu cầu phải có định dạng hợp lệ.
- **Một custom unchecked exception**, ví dụ `InvalidTransitionException extends IllegalStateException` cho vi phạm trạng thái trong hợp đồng thao tác.
- **Result type**, ví dụ `ValidationResult` nếu nhiều vấn đề nhập liệu là kết quả dự kiến bình thường chứ không nên ném.
- **Không khai báo checked exception**, nếu method không có đường checked failure cần lộ ra.

**Độ cụ thể không có nghĩa tạo class mới cho từng dòng lỗi.** Loại ngoại lệ phải phản ánh một **phân loại xử lý hữu ích**, không chỉ một tên kỹ thuật.

## 4. `throws Exception` vs `throws IOException`

Giả sử một thư viện cung cấp hàm đọc text. Chữ ký:

```java
static String readText(Path path) throws IOException;
```

nói rõ failure liên quan I/O. Caller có thể bắt `IOException`, đọc cause/metadata, báo lỗi hoặc chuyển sang một nguồn dự phòng.

Còn:

```java
static String readText(Path path) throws Exception;
```

cho phép nhiều checked failures hơn. Lúc phát hành API, lựa chọn rộng có thể khiến người gọi phải xử lý `Exception` dù implementation chỉ đọc file. Khi lập trình lâu dài, một chữ ký rộng làm giảm giá trị tài liệu của API.

### 4.1. Java 17 — demo 1: phân biệt IOException và subtype

Tạo **`SpecificFileContractDemo.java`**:

```java
import java.io.FileNotFoundException;
import java.io.IOException;

public class SpecificFileContractDemo {
    static String read(String key) throws IOException {
        if ("missing".equals(key)) {
            throw new FileNotFoundException("Không thấy " + key);
        }
        if ("offline".equals(key)) {
            throw new IOException("Thiết bị không sẵn sàng");
        }
        return "data:" + key;
    }

    public static void main(String[] args) {
        for (String key : new String[] {
                "ok", "missing", "offline"
        }) {
            try {
                System.out.println("OK: " + read(key));
            } catch (FileNotFoundException e) {
                System.out.println("MISSING: "
                        + e.getClass().getSimpleName());
            } catch (IOException e) {
                System.out.println("IO: "
                        + e.getClass().getSimpleName());
            }
        }
    }
}
```

Biên dịch/chạy JDK 17+:

```bash
javac -encoding UTF-8 SpecificFileContractDemo.java
java SpecificFileContractDemo
```

Stdout:

```text
OK: data:ok
MISSING: FileNotFoundException
IO: IOException
```

Giải thích:

- `read` công bố **`throws IOException`**.
- Cả `FileNotFoundException` và `IOException` đều hợp lệ để thoát, vì loại thứ nhất là subtype của loại thứ hai.
- Caller **được phép** đặt catch chuyên biệt trước catch tổng quát.
- Đảo thứ tự (bắt `IOException` trước `FileNotFoundException`) gây lỗi unreachable handler.
- Bài cố tình tạo IOException có kiểm soát; không cần tạo file hay môi trường I/O thật để tái hiện.
- Không có lý do thêm `throws Exception` vào `read` chỉ vì method có thể thất bại.

### 4.2. Chọn loại cha đủ đúng mức

Không nhất thiết phải khai báo `throws FileNotFoundException` chỉ vì implementation hiện tại chỉ mở file. API thao tác I/O có thể hợp lý hơn khi công bố `IOException` vì tương lai có thể gặp những lỗi I/O khác cùng ý nghĩa. Đây là **hợp đồng ổn định hơn**, vẫn chính xác về nhóm failure.

Ngược lại, nếu API có ý nghĩa “tra một tên file, chỉ công bố tình huống không tìm thấy” và đảm bảo bắt/translate các I/O failure khác, `FileNotFoundException` hoặc một loại domain riêng có thể hợp lý. Quy tắc là **đúng theo hành vi được cam kết**, không máy móc chọn subtype sâu nhất.

## 5. Hai loại checked failure độc lập: kê rõ cả hai

Một method có thể gặp **hai nhóm lỗi thật sự khác nhau** và caller cần phân biệt. Khi đó thay vì `throws Exception`, khai báo nhiều loại cụ thể:

```java
String importDocument(String raw)
        throws IOException, DocumentFormatException;
```

Đây là method signature minh họa. Lỗi I/O có thể tạm thời; lỗi định dạng dữ liệu thường đòi sửa input. Chúng không có quan hệ subtype với nhau.

### 5.1. Java 17 — demo 2: I/O và dữ liệu sai hợp đồng

Tạo **`TwoSpecificFailuresDemo.java`**:

```java
import java.io.IOException;

public class TwoSpecificFailuresDemo {
    static final class InvalidRecordException extends Exception {
        InvalidRecordException(String message) {
            super(message);
        }
    }

    static String load(String raw)
            throws IOException, InvalidRecordException {
        if ("offline".equals(raw)) {
            throw new IOException("Kho không truy cập");
        }
        if (!raw.startsWith("ID:")) {
            throw new InvalidRecordException("Sai định dạng");
        }
        return raw.substring(3);
    }

    public static void main(String[] args) {
        for (String value : new String[] {
                "ID:42", "oops", "offline"
        }) {
            try {
                System.out.println("OK: " + load(value));
            } catch (InvalidRecordException e) {
                System.out.println("FORMAT: " + e.getMessage());
            } catch (IOException e) {
                System.out.println("IO: " + e.getMessage());
            }
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 TwoSpecificFailuresDemo.java
java TwoSpecificFailuresDemo
```

Stdout:

```text
OK: 42
FORMAT: Sai định dạng
IO: Kho không truy cập
```

**Lý do thiết kế hợp lý:**

1. `InvalidRecordException` là **checked custom exception**, giúp caller có nhánh xử lý riêng cho dữ liệu sai.
2. `IOException` thể hiện lỗi truy cập dữ liệu, không bị gộp với lỗi format.
3. Method `load` không khai báo `throws Exception`.
4. Caller được chọn riêng cách phản ứng cho mỗi nhóm lỗi.
5. Nếu dữ liệu sai định dạng là phản hồi rất thường gặp, có thể cân nhắc kiểu kết quả có cấu trúc thay cho checked exception; thiết kế ở ví dụ chỉ nhằm thể hiện hợp đồng `throws`.
6. Khi `raw == null`, mã hiện tại sẽ gây `NullPointerException` do gọi `startsWith`. API thật cần kiểm tra `null`/input invariants và mô tả chúng; demo dùng chuỗi không null để tập trung vào checked exceptions.

### 5.2. Multi-catch khi chính sách xử lý trùng nhau

Nếu caller thật sự muốn xử lý cùng cách cho cả hai checked exception:

```java
try {
    importData();
} catch (IOException | InvalidRecordException e) {
    reportFailure(e);
}
```

Đây là mảnh code minh họa, cần định nghĩa `importData`, `reportFailure` và `InvalidRecordException` trong dự án. Multi-catch dùng được khi hai loại exception **không thuộc quan hệ cha–con**. Không được viết `catch(IOException | FileNotFoundException e)`, vì `FileNotFoundException` là subtype của `IOException`.

Ngay cả khi xử lý chung, **chữ ký cụ thể vẫn có ích**: người dùng API biết chính xác những failure có thể xảy ra, và có thể chọn handler riêng sau này.

## 6. Khi không nên tiết lộ loại exception hạ tầng

Một API domain có thể dùng JDBC ở bên dưới:

```java
// Hợp đồng gắn chặt tầng domain vào implementation JDBC:
Order loadOrder(String id) throws SQLException;
```

Nếu service đổi từ JDBC sang REST hoặc nguồn khác, liệu caller có nên cần sửa code dù hợp đồng nghiệp vụ “lấy đơn hàng” không thay đổi? Đó là dấu hiệu API có thể đang **rò rỉ abstraction của tầng dưới**.

Một lựa chọn:

```java
Order loadOrder(String id) throws OrderRepositoryException;
```

`OrderRepositoryException` là custom checked exception có ý nghĩa “tầng lưu trữ đơn hàng thất bại”. Tầng repository/service **bắt `SQLException`, truyền cause vào custom exception** và chỉ công bố hợp đồng có ngữ nghĩa ổn định cho domain.

### 6.1. Java 17 — demo 3: dịch SQLException sang exception domain

Tạo **`ExceptionTranslationDemo.java`**:

```java
import java.sql.SQLException;

public class ExceptionTranslationDemo {
    static final class CatalogUnavailableException
            extends Exception {
        CatalogUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static String readFromDatabase() throws SQLException {
        throw new SQLException("Database down", "08001");
    }

    static String loadCatalog()
            throws CatalogUnavailableException {
        try {
            return readFromDatabase();
        } catch (SQLException e) {
            throw new CatalogUnavailableException(
                    "Không tải được catalog", e);
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println(loadCatalog());
        } catch (CatalogUnavailableException e) {
            System.out.println("Boundary: "
                    + e.getClass().getSimpleName());
            System.out.println("Cause: "
                    + e.getCause().getClass().getSimpleName());
            SQLException sql = (SQLException) e.getCause();
            System.out.println("SQLState: " + sql.getSQLState());
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 ExceptionTranslationDemo.java
java ExceptionTranslationDemo
```

Stdout:

```text
Boundary: CatalogUnavailableException
Cause: SQLException
SQLState: 08001
```

**Ý nghĩa:**

- `readFromDatabase()` vẫn có hợp đồng `throws SQLException`, đúng mức hạ tầng.
- `loadCatalog()` chuyển đổi lỗi tại **ranh giới abstraction** và công bố `CatalogUnavailableException`.
- Constructor `super(message, cause)` giữ `SQLException` thật làm cause; không ném một exception chỉ chứa `e.getMessage()` rồi mất đường nguyên nhân.
- Caller service chỉ cần biết failure catalog, không buộc phải phụ thuộc trực tiếp package JDBC trong chữ ký.
- Demo cast `SQLException` ở `main` để chứng minh cause; một caller tổng quát **không nên** cast mọi `getCause()` thành `SQLException` nếu implementation có thể thay đổi. Hãy phân loại bằng `instanceof` hoặc chính sách chẩn đoán thích hợp.

### 6.2. Dịch exception không phải giấu lỗi

Một số anti-pattern:

```java
try {
    saveToDatabase();
} catch (SQLException ignored) {
    return true; // CỐ Ý SAI: giả thành công khi thao tác thất bại.
}
```

Đây là mảnh code minh họa chưa có `saveToDatabase`. Nếu không có fallback/compensation đã được thiết kế, **đừng nuốt lỗi**. Dịch lỗi phải giữ trạng thái thất bại và phục vụ abstraction, không biến lỗi thành success.

## 7. Checked vs unchecked: không phải cứ “cụ thể” là checked

Một public method không cần `throws` cho mọi tình huống lỗi:

```java
static void setDiscount(int percent) {
    if (percent < 0 || percent > 100) {
        throw new IllegalArgumentException(
                "percent phải thuộc [0,100]");
    }
}
```

`IllegalArgumentException` là loại **cụ thể và unchecked**, phù hợp khi caller vi phạm tiền điều kiện. Compiler không yêu cầu kê `throws IllegalArgumentException`, dù Java cho phép liệt kê nếu muốn tài liệu hóa.

**Hai quyết định riêng biệt:**

1. Lỗi là một điều kiện có thể xảy ra trong luồng nghiệp vụ mà caller nên được compiler nhắc xử lý, hay là vi phạm contract/lỗi runtime?
2. Loại exception được chọn có cụ thể và có ý nghĩa với caller không?

Đừng biến mọi `IOException` thành unchecked chỉ để xóa `throws`, cũng đừng tạo checked exception cho mọi phép validation.

### 7.1. Java 17 — demo 4: cả checked và unchecked trong một API

Tạo **`CheckedUncheckedContractDemo.java`**:

```java
import java.io.IOException;

public class CheckedUncheckedContractDemo {
    static int compute(int input) throws IOException {
        if (input < 0) {
            throw new IllegalArgumentException("input âm");
        }
        if (input == 0) {
            throw new IOException("Nguồn dữ liệu rỗng");
        }
        return input * 2;
    }

    public static void main(String[] args) {
        for (int input : new int[] {3, -1, 0}) {
            try {
                System.out.println("OK: " + compute(input));
            } catch (IllegalArgumentException e) {
                System.out.println("INVALID: "
                        + e.getClass().getSimpleName());
            } catch (IOException e) {
                System.out.println("IO: "
                        + e.getClass().getSimpleName());
            }
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 CheckedUncheckedContractDemo.java
java CheckedUncheckedContractDemo
```

Stdout:

```text
OK: 6
INVALID: IllegalArgumentException
IO: IOException
```

Giải thích:

- `compute` công bố `IOException` vì đó là một checked failure.
- `IllegalArgumentException` vẫn có thể xảy ra nhưng không cần kê trong `throws`.
- Caller có thể bắt unchecked exception nếu có chính sách phản hồi input đúng; không cần làm vậy trong mọi đoạn code.
- `int * 2` trong demo có thể tràn số với input cực lớn; bài chọn input nhỏ. API production phải xem điều kiện số học riêng (`Math.multiplyExact` nếu cần).
- Đừng hiểu `throws IOException` nghĩa là “chỉ có IOException, không có RuntimeException”.

## 8. Lợi ích khi caller có thể viết handler theo từng loại

Khi API cụ thể, caller dễ thể hiện **chính sách khác nhau**:

| Loại failure | Hành động có thể phù hợp | Không nên mặc định |
| --- | --- | --- |
| `FileNotFoundException` | Chọn đường dẫn khác hoặc báo thiếu file | Retry vô hạn cùng path |
| `IOException` tạm thời | Retry có giới hạn nếu thao tác idempotent | Retry mọi I/O failure |
| `InvalidRecordException` | Yêu cầu sửa input | Retry I/O và hy vọng dữ liệu đúng |
| `SQLException` ở tầng JDBC | Quản lý transaction, đọc SQL state nếu cần | Phơi SQL internals cho API public |
| `CatalogUnavailableException` | Báo dịch vụ phụ thuộc không sẵn sàng | Cast mọi cause sang SQLException |
| `IllegalArgumentException` | Sửa tham số do caller cung cấp | Bắt rồi che bug và trả success |
| `IllegalStateException` | Sửa trạng thái/sequence thao tác | Tự giả định retry sẽ sửa invariant |
| `InterruptedException` | Tôn trọng interruption policy | Nuốt interrupt mà không khôi phục |

**Một loại exception không quyết định tự động chính sách retry.** Ví dụ `IOException` có thể bao gồm lỗi permission, file không tồn tại hoặc kết nối tạm mất. Cần metadata ngữ nghĩa, tính idempotent, trạng thái side effect và yêu cầu nghiệp vụ.

## 9. Viết `catch` cụ thể và sắp xếp đúng thứ tự

Trong Java, handler được xét từ trên xuống; lớp con phải ở trước lớp cha nếu cần phân biệt.

```java
try {
    readFile();
} catch (FileNotFoundException e) {
    handleMissingFile(e);
} catch (IOException e) {
    handleOtherIo(e);
}
```

Đây là mảnh code với ba hàm giả định. Nếu đặt `catch(IOException)` trước `catch(FileNotFoundException)`, handler sau không thể tới được.

### 9.1. `catch(Exception)` chỉ tại ranh giới thực sự cần?

Một ranh giới như CLI dispatcher, task runner, executor hoặc server request boundary có thể bắt rộng để **ghi log, chuyển lỗi thành phản hồi thất bại an toàn và không phá hủy việc điều phối khác**, sau khi các tầng gần nguồn đã thực hiện xử lý cụ thể.

Nhưng:

- `catch(Exception)` còn nhận cả `RuntimeException`; không coi mọi bug là một lỗi dự kiến có thể phục hồi.
- Nó **không** bắt `Error`. Catch `Throwable` còn rộng hơn, cần chính sách cực kỳ thận trọng.
- Bắt rộng ở boundary **không biện minh** cho việc mọi method ở mọi tầng đều khai báo `throws Exception`.
- Nếu tầng đó không thể giải quyết lỗi, có thể ném tiếp hoặc chuyển sang lỗi phù hợp, giữ cause.
- Không báo “thành công” chỉ vì exception đã bị bắt.

## 10. Java 17 — demo 5: multi-catch cho lỗi checked độc lập

Tạo **`MultiCatchContractDemo.java`**:

```java
import java.io.IOException;
import java.text.ParseException;

public class MultiCatchContractDemo {
    enum Mode { OK, IO, PARSE }

    static String run(Mode mode)
            throws IOException, ParseException {
        return switch (mode) {
            case OK -> "success";
            case IO -> throw new IOException("Lỗi thiết bị");
            case PARSE -> throw new ParseException("Sai ngày", 0);
        };
    }

    public static void main(String[] args) {
        for (Mode mode : Mode.values()) {
            try {
                System.out.println("OK: " + run(mode));
            } catch (IOException | ParseException e) {
                System.out.println("FAILED: "
                        + e.getClass().getSimpleName());
            }
        }
    }
}
```

Chạy JDK 17+:

```bash
javac -encoding UTF-8 MultiCatchContractDemo.java
java MultiCatchContractDemo
```

Stdout:

```text
OK: success
FAILED: IOException
FAILED: ParseException
```

Vì sao hợp lệ?

- `IOException` và `ParseException` đều là checked exceptions nhưng **không có quan hệ cha–con**.
- `run` công bố rõ hai loại.
- Một multi-catch cho cả hai nếu caller chọn chính sách giống nhau.
- Nếu handler cần xử lý khác, tách thành hai `catch`.
- `switch` expression với nhánh `throw` là tính năng ổn định trong Java 17.
- Trong multi-catch, biến `e` có kiểu được tính theo luật chung và là effectively final; không viết code giả định lúc nào `e` cũng là `IOException`.

### 10.1. Sai: multi-catch cha và con

```java
// CỐ Ý KHÔNG BIÊN DỊCH:
catch (IOException | FileNotFoundException e) {
    // Hai lựa chọn có quan hệ kế thừa.
}
```

Chọn `catch(IOException e)` nếu cần xử lý chung, hoặc hai handler theo thứ tự subtype rồi superclass.

## 11. Interface và override: `throws` là hợp đồng thay thế được

Một implementation của interface không được **mở rộng** danh sách checked exceptions so với phương thức interface bị override.

```java
interface Loader {
    String load() throws IOException;
}
```

Hợp lệ:

```java
final class FileLoader implements Loader {
    @Override
    public String load() throws FileNotFoundException {
        throw new FileNotFoundException("missing");
    }
}
```

Không hợp lệ:

```java
// CỐ Ý KHÔNG BIÊN DỊCH:
final class BroadLoader implements Loader {
    @Override
    public String load() throws Exception {
        return "x";
    }
}
```

`Exception` rộng hơn `IOException` nên caller của `Loader` không được hứa xử lý tất cả checked subtypes của Exception. Implementation phải tôn trọng contract: cùng loại checked, subtype phù hợp, hoặc ít checked hơn.

### 11.1. Java 17 — demo 6: interface và reference type

Tạo **`OverrideSpecificThrowsDemo.java`**:

```java
import java.io.FileNotFoundException;
import java.io.IOException;

public class OverrideSpecificThrowsDemo {
    interface Reader {
        String read() throws IOException;
    }

    static final class FileReader implements Reader {
        @Override
        public String read() throws FileNotFoundException {
            throw new FileNotFoundException("missing");
        }
    }

    public static void main(String[] args) {
        FileReader concrete = new FileReader();
        try {
            concrete.read();
        } catch (FileNotFoundException e) {
            System.out.println("Concrete catches: "
                    + e.getClass().getSimpleName());
        }

        Reader api = concrete;
        try {
            api.read();
        } catch (IOException e) {
            System.out.println("Interface catches: "
                    + e.getClass().getSimpleName());
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 OverrideSpecificThrowsDemo.java
java OverrideSpecificThrowsDemo
```

Stdout:

```text
Concrete catches: FileNotFoundException
Interface catches: FileNotFoundException
```

**Phân biệt hai thứ:**

1. **Nghĩa vụ compiler** ở caller phụ thuộc method được xác định từ **kiểu tham chiếu tĩnh**. `FileReader.read` công bố `FileNotFoundException`; `Reader.read` công bố `IOException`.
2. **Đối tượng exception runtime** là `FileNotFoundException` trong cả hai lần gọi; upcast receiver thành `Reader` không thay object error thành `IOException`.

Nếu không có lý do hợp đồng, implementation không nên tự thêm `throws Exception` để “dự phòng lỗi sau này” vì như vậy vi phạm interface.

## 12. Nhiều interface có `throws` khác nhau

Một class có thể implement hai interface có cùng method signature, nhưng mỗi interface kê checked exception khác. Implementation phải thỏa **đồng thời** các quy tắc override.

Ví dụ:

```java
interface A {
    void execute() throws IOException;
}
interface B {
    void execute() throws SQLException;
}

final class Combined implements A, B {
    @Override
    public void execute() {
        System.out.println("done");
    }
}
```

Đây là mảnh code minh họa cần import `IOException`/`SQLException`. Vì hai loại checked không liên quan, `Combined.execute()` **không được** khai báo `throws IOException, SQLException`; bỏ checked throws làm nó hợp lệ với cả hai.

Hãy nhớ: `throws Exception` càng không phải cách khắc phục khi hợp đồng interface **hẹp hơn**. Chữ ký implementation không thể mở rộng hợp đồng tùy ý.

## 13. Khi thay đổi `throws`, kiểm tra tương thích mã nguồn

Khai báo `throws` là một phần của hợp đồng kiểm tra checked exceptions. Nếu public API trước đây chỉ có `throws ParseException` rồi thêm `IOException`, một caller chỉ bắt `ParseException` có thể **không biên dịch lại được**:

~~~java
// Hai phiên bản chữ ký minh họa, không cùng khai báo trong một lớp.
String parse(String input) throws ParseException;
// Phiên bản kế tiếp:
String parse(String input) throws ParseException, IOException;
~~~

Thu hẹp hoặc bỏ checked `throws` cũng có thể làm handler checked ở caller trở nên không thể tới được sau khi biên dịch lại. Tuy nhiên chỉ đổi mệnh đề `throws` **không tự làm thay đổi method descriptor ở JVM**; cần tách *source*, *binary* và *behavioral compatibility*. Không nên thay public `throws` trong một thư viện chỉ vì implementation hiện tại không còn ném một lỗi; hãy kiểm tra caller, override và lộ trình chuyển đổi. Bài về **tác động tương thích khi thay đổi hợp đồng ngoại lệ** phân tích ma trận và các chiến lược nâng cấp đầy đủ.

## 14. Dùng Javadoc `@throws` để giải thích điều kiện, không chỉ liệt kê kiểu

Mệnh đề `throws` thông báo checked exception cho compiler; `@throws` trong Javadoc cho caller biết **khi nào** exception phát sinh. Trong tài liệu cho phương thức `load(String documentId) throws IOException`, có thể nêu: `@throws IOException nếu nguồn dữ liệu không đọc được` và `@throws IllegalArgumentException nếu documentId rỗng`. Đây là nội dung minh họa Javadoc, không phải khai báo phương thức mới.

Có thể tài liệu hóa cả **unchecked exception** bằng `@throws` dù không cần khai báo nó trong chữ ký. Tránh mô tả mơ hồ kiểu “nếu xảy ra lỗi”; viết điều kiện cụ thể, cập nhật Javadoc cùng test hợp đồng. Quy tắc sinh tài liệu, thẻ inline, override và nhiều nhánh thất bại được học đầy đủ ở bài **tài liệu hóa bằng `@throws`**.

## 15. `throws` ở constructor, resource và API I/O

Quy tắc “cụ thể” áp dụng cả cho constructor và resource cleanup.

```java
public FileStore(Path root) throws IOException {
    // Đọc hoặc tạo dữ liệu trên file system.
}
```

Nếu constructor có thể ném checked `IOException`, caller cần xử lý/khai báo. Không có lý do khai `throws Exception` khi chỉ có I/O như một loại checked thật sự.

Khi dùng `try-with-resources`, cả thân `try` và phương thức `close()` của resource có thể ném checked exceptions. Nếu kiểu resource công bố `close() throws IOException`, API bọc ngoài có thể dùng `throws IOException`; nhưng nếu resource được khai báo kiểu `AutoCloseable` chung với `close() throws Exception`, compiler có thể phải xét `Exception` ở điểm close. **Kiểu tĩnh của resource** và API `close` ảnh hưởng hợp đồng.

### 15.1. Java 17 — demo 7: `Closeable` cho checked IOException cụ thể

Tạo **`CloseableSpecificThrowsDemo.java`**:

```java
import java.io.Closeable;
import java.io.IOException;

public class CloseableSpecificThrowsDemo {
    static final class Resource implements Closeable {
        String read() throws IOException {
            return "ok";
        }

        @Override
        public void close() throws IOException {
            System.out.println("closing");
        }
    }

    static String load() throws IOException {
        try (Resource resource = new Resource()) {
            return resource.read();
        }
    }

    public static void main(String[] args) {
        try {
            String result = load();
            System.out.println("result: " + result);
        } catch (IOException e) {
            System.out.println("IO: " + e.getMessage());
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 CloseableSpecificThrowsDemo.java
java CloseableSpecificThrowsDemo
```

Stdout:

```text
closing
result: ok
```

Vì sao `load() throws IOException` là đủ?

- `Resource.read` và `Closeable.close` đều có `throws IOException`.
- Try-with-resources bảo đảm `close` được gọi khi rời `try` trong các đường thực thi phù hợp.
- Không cần `throws Exception` khi các nguồn checked failure của ví dụ đều là I/O.
- Nếu `read` và `close` cùng ném lỗi, Java có cơ chế primary/suppressed exceptions; chúng không tự biến thành `Exception` rộng chỉ vì có nhiều Throwable.
- `try-with-resources` không bảo đảm thao tác đọc thành công hoặc side effect được rollback; nó quản lý việc đóng resource.

### 15.2. Vì sao khai resource là `AutoCloseable` có thể làm `throws` rộng?

`AutoCloseable.close()` công bố `throws Exception`. Nếu code chỉ thấy kiểu `AutoCloseable` và không bắt/translate lỗi khi đóng, nó phải tính cả checked `Exception` từ `close`.

Giải pháp không phải tự ép cast tùy tiện, mà là chọn interface resource có hợp đồng hẹp phù hợp (`Closeable` cho I/O) hoặc xử lý/dịch lỗi ở ranh giới có ý nghĩa.

## 16. Exception chaining: giữ `cause` khi chọn exception cụ thể

Chuyển `SQLException` thành `CatalogUnavailableException` có ích chỉ khi không làm mất nguyên nhân gốc.

Nên:

```java
throw new CatalogUnavailableException(
        "Không tải được catalog", sqlException);
```

Không nên:

```java
throw new CatalogUnavailableException(
        sqlException.getMessage(), null);
```

Các mảnh code giả định constructor phù hợp và biến `sqlException`. Cách thứ hai bỏ cause, làm khó xem stack gốc, vendor code/SQL state và chuỗi lỗi.

**Message và cause có mục đích khác nhau:**

- Message: mô tả cho người đọc theo tầng hiện tại.
- Cause: liên kết đến failure tầng dưới.
- Custom typed context: các ID/code an toàn, nếu caller cần xử lý có cấu trúc.
- Suppressed: các lỗi phụ bị giữ lại trong cùng tình huống, đặc biệt khi đóng resource thất bại.

Không copy thủ công toàn bộ stack trace hoặc tạo một field `originalException` trùng với `getCause()` nếu không có nhu cầu riêng.

## 17. Khi nào nên bọc checked thành unchecked?

Một thư viện callback như `Function.apply` không công bố checked exception. Có trường hợp cần bọc `IOException` thành `UncheckedIOException` và giữ cause:

```java
Function<Path, String> read = path -> {
    try {
        return Files.readString(path);
    } catch (IOException e) {
        throw new UncheckedIOException("Không đọc được", e);
    }
};
```

Đây là **mảnh code** cần import `Function`/`Path`/`Files`/`IOException`/`UncheckedIOException`. Nó đổi **hình thức báo lỗi** qua API callback, không biến I/O failure thành success.

Nhưng không nên xem đây là lời khuyên tổng quát xóa checked `throws` khỏi mọi API. Nếu bạn kiểm soát method khai báo, một `throws IOException` rõ ràng có thể phù hợp hơn việc ép lỗi đi qua unchecked wrapper.

### 17.1. `InterruptedException` cần chính sách riêng

Nếu code bắt `InterruptedException` rồi dịch lỗi, phải cân nhắc bảo toàn tín hiệu interruption: có thể ném tiếp `InterruptedException` khi API cho phép, hoặc khôi phục interrupt flag bằng `Thread.currentThread().interrupt()` trước khi chuyển sang một exception khác, tùy hợp đồng.

Đừng chỉ `catch(Exception ignored)` quanh thao tác blocking rồi nuốt luôn interruption. Đây là ví dụ mạnh cho thấy `throws Exception` làm nhiều failure với chính sách khác nhau bị gộp thiếu rõ ràng.

## 18. `throws Exception` có luôn là lỗi thiết kế không?

**Không.** Nó có thể hợp lý ở một số **ranh giới tổng quát**:

- `public static void main(String[] args) throws Exception` trong **chương trình minh họa nhỏ**, khi mục tiêu chỉ là học một API khác và chấp nhận để chương trình kết thúc khi có lỗi.
- Test harness hoặc runner nội bộ, nơi callback được định nghĩa để cho phép nhiều checked exception.
- `java.util.concurrent.Callable<V>.call() throws Exception`: một API chuẩn có hợp đồng tổng quát cố ý cho phép tác vụ báo checked failure.
- Framework/plugin abstraction chạy mã do ứng dụng khác cung cấp, khi tập loại checked chưa biết trước và caller được thiết kế như một dispatcher chung.
- Một boundary nhất định cần propagate mọi checked exception nhưng không hứa được một abstraction cụ thể hơn (cần tài liệu rõ và giới hạn phạm vi).

Vẫn cần xem liệu `throws Exception` có gây bất tiện cho **public API lâu dài** hay không. Đừng sử dụng nó ở mọi tầng chỉ vì dễ viết.

### 18.1. `main throws Exception` có khác library API?

Có về mục đích. `main` là entry point được JVM gọi; với một demo nhỏ, khai báo `throws Exception` đôi khi chấp nhận được vì không có caller Java khác phải triển khai policy. Một phương thức **public library** `load() throws Exception` có thể bị hàng trăm caller dùng trong nhiều năm; hợp đồng quá rộng trở thành chi phí bảo trì.

Trong tài liệu học Java, nên ưu tiên ví dụ có handler cụ thể để thể hiện behavior, nhưng không cần “cấm” tuyệt đối `main throws Exception`.

### 18.2. `Callable.call() throws Exception` là ví dụ có chủ đích

`Callable<V>` cho phép nhiều loại checked failure vì một task framework có thể chạy logic tùy ý. Tuy nhiên khi gửi task vào `ExecutorService` và gọi `Future.get()`, caller nhìn thấy **`ExecutionException`** (có cause là lỗi task) theo API Future, không nhất thiết nhận checked exception của task trực tiếp. Mỗi tầng vẫn cần hợp đồng riêng.

**Bài học:** “cụ thể” là theo tầng abstraction và vai trò caller, không có nghĩa mọi API generics đều phải biết mọi loại failure của tác vụ từ trước.

## 19. Generic `throws E`: giữ tính cụ thể cho callback do bạn kiểm soát

Khi thiết kế API nhận lambda có thể ném checked exception, thay vì bắt caller chịu `throws Exception` ở mọi nơi, có thể sử dụng type parameter.

```java
@FunctionalInterface
interface CheckedSupplier<T, E extends Exception> {
    T get() throws E;
}

static <T, E extends Exception> T execute(
        CheckedSupplier<T, E> action) throws E {
    return action.get();
}
```

Đây là các khai báo minh họa, không phải một chương trình standalone. Với callback có checked `IOException`, `E` có thể là `IOException`, nên method gọi `execute` không phải công bố `Exception` rộng chỉ vì API generic.

### 19.1. Java 17 — demo 8: truyền checked type qua generic

Tạo **`GenericSpecificThrowsDemo.java`**:

```java
import java.io.IOException;

public class GenericSpecificThrowsDemo {
    @FunctionalInterface
    interface CheckedSupplier<T, E extends Exception> {
        T get() throws E;
    }

    static <T, E extends Exception> T execute(
            CheckedSupplier<T, E> action) throws E {
        return action.get();
    }

    public static void main(String[] args) {
        try {
            String ok =
                    GenericSpecificThrowsDemo
                        .<String, IOException>execute(() -> "done");
            System.out.println("Value: " + ok);

            GenericSpecificThrowsDemo
                .<String, IOException>execute(() -> {
                    throw new IOException("lost");
                });
        } catch (IOException e) {
            System.out.println("I/O: " + e.getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 GenericSpecificThrowsDemo.java
java GenericSpecificThrowsDemo
```

Stdout:

```text
Value: done
I/O: lost
```

Giải thích:

- `CheckedSupplier.get()` công bố checked `E`.
- `execute()` công bố `throws E` thay vì hard-code `throws Exception`.
- Ở call site, ví dụ khai báo tường minh `E = IOException` để tránh phụ thuộc vào chi tiết suy luận kiểu khi học.
- Lambda có thể ném `IOException` vì target functional interface **cho phép** checked exception đó.
- `Supplier<T>` chuẩn trong `java.util.function` lại **không** có `throws E`; không thể thay thế nó bằng custom `CheckedSupplier` một cách tự động trong API vốn nhận `Supplier`.
- Generic `E` đại diện cho một kiểu ngoại lệ, không phải một phép union tổng quát của các kiểu checked không liên quan.

## 20. Nhiều checked exception và API framework

Một framework nhận mã plugin có thể dùng interface:

```java
interface PluginAction {
    void execute() throws Exception;
}
```

Điều này có thể hợp lý vì plugin có thể làm I/O, SQL, parse và nhiều việc khác mà framework không biết trước. Tuy nhiên tại **biên gọi plugin**, framework nên:

- Xác định failure policy: log, đánh dấu task thất bại, cleanup.
- Không coi mọi `Exception` là transient hay retryable.
- Phân biệt hỏng plugin với vi phạm môi trường/permission, khi cần.
- Không bắt `Error` rồi trả thành công.
- Giữ cause khi chuyển lỗi sang `PluginExecutionException` hoặc result có cấu trúc.
- Không truyền thẳng stack trace nội bộ cho end user.
- Nếu các plugin có contract domain chung rõ ràng, có thể chọn một checked `PluginException` hẹp hơn `Exception`.

**Một exception riêng tên `PluginException` cũng có thể quá rộng** nếu nó gộp tất cả failure mà caller cần phân biệt. Chọn cấu trúc exception taxonomy dựa vào use cases thực tế, không theo số tầng lớp lớn nhất.

## 21. Nếu thất bại là kết cục nghiệp vụ dự kiến, cân nhắc kiểu kết quả

Không phải mọi đầu vào sai trong một luồng validation đều cần `throws`. Nếu caller muốn nhận nhiều lỗi trong một lần, một result có kiểu và mã lỗi có thể phù hợp:

~~~java
record FieldIssue(String field, String code) {}
record ValidationResult(java.util.List<FieldIssue> issues) {}
~~~

Đây là các record minh họa (Java 17), không phải toàn bộ chương trình. **Ngoại lệ cụ thể** vẫn đúng khi thao tác thất bại bất thường như không đọc được nguồn I/O; `Optional<T>` phù hợp hơn nếu việc **không có kết quả** là bình thường và không cần biết lý do. Bài “Chọn ngoại lệ, Optional hay kiểu kết quả có cấu trúc” là nơi so sánh và kiểm thử chi tiết ba lựa chọn.

## 22. Hạn chế tạo hierarchy custom quá sâu

Cụ thể không đồng nghĩa với hàng chục lớp:

```text
Exception
└── ApplicationException
    └── ServiceException
        └── DataException
            └── RepositoryException
                └── CatalogException
                    └── CatalogTimeoutException
```

Cây minh họa như trên có thể mang chi phí:

- Caller phải hiểu nhiều loại mà không có chính sách xử lý khác nhau thật.
- Tầng ứng dụng cần chuyển đổi qua nhiều wrapper, tạo stack/cause chain dài.
- Một hierarchy quá sâu làm API gắn với cách tổ chức package/tầng hiện tại.
- Nếu nhiều loại chỉ khác message, có thể một class với mã lỗi typed là đủ.
- Nếu quá ít loại, caller không thể phân biệt failure có ý nghĩa.

Một thiết kế vừa phải thường phân loại theo **khả năng phục hồi hoặc trách nhiệm xử lý**:

```text
CatalogException (checked)
├── CatalogNotFoundException
└── CatalogUnavailableException
```

Đây là ví dụ taxonomy, không yêu cầu mọi ứng dụng phải định nghĩa y hệt. Nếu `NotFound` là kết quả bình thường, có thể trả `Optional<Catalog>` hoặc result type thay cho exception.

## 23. Ngoại lệ cụ thể và dữ liệu ngữ cảnh có kiểu

Một loại exception rõ ràng có thể đi kèm getter có kiểu:

```java
final class ResourceNotFoundException extends Exception {
    private final String resourceId;

    ResourceNotFoundException(String resourceId) {
        super("Không tìm thấy tài nguyên");
        this.resourceId = java.util.Objects.requireNonNull(
                resourceId, "resourceId");
    }

    String resourceId() {
        return resourceId;
    }
}
```

Đây là class minh họa, không có `main`. Caller có thể biết **loại lỗi** và **ID nào liên quan**, không parse `getMessage()`.

Khi thiết kế:

- Chỉ giữ context cần cho hợp đồng xử lý/chẩn đoán.
- Không lưu password/token/raw request body trong exception.
- Không copy cause vào một field riêng nếu `Throwable.getCause()` đã đáp ứng.
- Không trùng `errorCode` và subtype có thể mâu thuẫn; nếu cả hai phục vụ API/telemetry, bảo đảm đồng bộ bằng thiết kế.
- Giữ context ổn định, tránh object graph mutable lớn.

**Thông điệp lỗi phục vụ con người; loại exception và getter typed phục vụ logic máy.**

## 24. Trường hợp checked exception được thư viện bọc bởi API khác

Một API có thể nhận callback nhưng không khai báo checked exception, chẳng hạn `Function.apply`/`Supplier.get`. Lúc đó bạn **không thể đơn giản thêm `throws IOException` vào lambda** nếu target functional method không có `throws`.

Các lựa chọn:

1. Xử lý checked exception trong lambda khi có fallback hợp nghĩa.
2. Bọc thành unchecked có cause, như `UncheckedIOException`.
3. Tự thiết kế functional interface có `throws E` nếu bạn kiểm soát API nhận callback.
4. Dùng vòng lặp hoặc method thường để giữ checked contract trực tiếp.

Khi API của framework chuyển lỗi qua `InvocationTargetException`, `ExecutionException` hoặc `CompletionException`, caller phải tuân theo **hợp đồng của tầng gọi** và tìm cause gốc theo cơ chế tương ứng. Không có một mệnh đề `throws IOException` của method nằm sâu nào tự xuất hiện ở mọi tầng.

**Điều này củng cố nguyên tắc:** chọn loại ngoại lệ đủ cụ thể nhưng **đúng abstraction boundary** mà caller đang sử dụng.

## 25. Thiết kế chính sách retry dựa vào semantics, không chỉ tên loại

Một `IOException` có thể bắt nguồn từ:

- file không tồn tại (retry y hệt thường vô ích);
- permission denied (cần quyền hoặc cấu hình);
- kết nối tạm thời mất (có thể retry có giới hạn);
- lỗi ghi giữa chừng (cần xem dữ liệu đã ghi đến đâu).

Vì vậy dù API có `throws IOException` cụ thể, caller **không tự được phép retry mọi IOException**. Tầng domain có thể cần thông tin riêng như error code, loại thao tác, timeout category, và idempotency.

Ngược lại, `throws Exception` lại làm retry policy khó đặt đúng vì quá nhiều loại failure không liên quan có thể đi qua một handler chung.

### 25.1. Ví dụ quyết định chính sách

| Tình huống | Lựa chọn hướng xử lý |
| --- | --- |
| Thiếu file nhập | Yêu cầu đường dẫn hợp lệ hoặc chọn file |
| File hiện có bị lỗi format | Báo giá trị/field sai, không retry tự động |
| Kết nối bất ổn | Retry giới hạn nếu thao tác cho phép và có backoff |
| Đã ghi một phần rồi mất kết nối | Xác minh trạng thái, idempotency hoặc compensation trước retry |
| Input vi phạm hợp đồng | Sửa caller/input, không tự retry cùng tham số |
| Dependency cố định đang bị thiếu cấu hình | Alert/configuration fix, không retry vô hạn |

**Một API rõ loại failure là điều kiện thuận lợi cho phục hồi**, nhưng vẫn cần chính sách ngoài chữ ký `throws`.

## 26. Những lỗi thiết kế thường gặp

| Sai lầm | Vì sao nguy hiểm | Cách sửa |
| --- | --- | --- |
| Mọi method đều `throws Exception` | Hợp đồng quá rộng; caller phải xử lý chung | Chọn checked types cụ thể hoặc domain exception |
| `catch(Exception) { return null; }` | Che lỗi, lẫn null thành công | Báo failure đúng hợp đồng hoặc fallback thực |
| `catch(Exception) { retry(); }` | Retry cả lỗi input/bug không phục hồi | Retry có phân loại và giới hạn |
| Public domain API phơi `SQLException` không cần thiết | Gắn caller vào JDBC internals | Dịch ở tầng repository, giữ cause |
| Bao `IOException` bằng `new Exception(message)` | Mất loại lỗi có ý nghĩa, có thể mất cause | Chọn subtype/domain checked có cause |
| Tạo class exception mới cho mỗi message | Taxonomy phình to, caller không khác xử lý | Nhóm theo chiến lược xử lý, typed code khi hợp |
| Chọn subclass checked hẹp quá mức để “chính xác” | Khó mở rộng khi API có nhóm lỗi liên quan | Chọn superclass đủ đúng abstraction |
| Quên `throws` từ `close()` resource | Compile error hoặc xử lý thiếu | Dùng `Closeable`/hợp đồng cleanup rõ |
| Dùng `throws Exception` chỉ để xử lý callback `Function` | Không thay đổi `Function.apply` | Bắt trong lambda hoặc callback typed |
| Implementation override dùng `throws Exception` khi interface `throws IOException` | Vi phạm quy tắc checked overriding | Giữ cùng loại/subtype hoặc bớt checked |
| Đổi API thêm checked exception mà không cập nhật caller | Source compatibility có thể vỡ | Quản lý thay đổi version/changelog |
| Cho rằng bỏ bớt `throws` không ảnh hưởng source | Catch checked cũ có thể unreachable | Kiểm tra caller biên dịch lại |
| Log chỉ `e.getMessage()` khi dịch lỗi | Mất stack/cause | Giữ Throwable làm cause, log đúng API |
| Cast mọi `getCause()` sang một type hạ tầng | Implementation thay đổi gây lỗi cast | Dùng `instanceof` hoặc không phụ thuộc nội bộ |
| Dùng `RuntimeException` chung cho mọi failure | Caller khó phân loại, có thể mất semantics | Chọn unchecked subtype có ý nghĩa khi cần |
| Bắt `Throwable` để thay `catch(Exception)` | Bắt cả `Error` nghiêm trọng | Chỉ boundary đặc biệt mới cân nhắc |
| Ném `Error` cho lỗi validation | Sai phân cấp và ý nghĩa | `IllegalArgumentException`/result phù hợp |
| Không document unchecked errors | Caller không biết tiền điều kiện | Javadoc `@throws` và validation |
| Test dựa trên message để phân nhánh | Text dễ đổi | Test type/code/context/cause |
| Giữ token/password trong exception | Log có thể rò rỉ | Context tối thiểu, thông điệp an toàn |

## 27. Quy trình lựa chọn mệnh đề `throws` cho một API mới

Khi thiết kế một method:

1. **Viết mục đích method** bằng một câu ở mức người dùng API.
2. **Liệt kê các đường thất bại** có thể thấy trong hợp đồng, không chỉ từng exception từ implementation hiện tại.
3. **Phân loại failure dự kiến**: input sai, không tìm thấy, I/O, parse, resource tạm thời, trạng thái bất hợp lệ, lỗi nội bộ.
4. **Xác định caller nào có thể làm gì**: fallback, sửa input, retry, rollback, báo lỗi hay kết thúc.
5. **Chọn checked vs unchecked vs result** theo semantics của thao tác, không theo tiện compiler.
6. **Chọn loại đủ hẹp và ổn định**: nhóm JDK phù hợp hoặc custom domain exception cần thiết.
7. **Dịch exception tầng thấp tại boundary** nếu không nên để chi tiết implementation lộ ra public API; luôn giữ cause.
8. **Viết `throws` và Javadoc `@throws`** mô tả nguyên nhân/hợp đồng, kể cả các điều kiện unchecked đáng chú ý.
9. **Kiểm tra interface override và callback APIs**: implementation không được rộng checked throws; lambda bị ràng buộc target functional type.
10. **Viết test** xác nhận exception type, cause, thông tin có kiểu, đường thành công và failure.
11. **Xem tương thích API** khi dự kiến phát hành phiên bản mới; checked throws tác động caller lúc biên dịch.
12. **Không thêm loại checked “phòng hờ”** chỉ vì tương lai có thể có lỗi khác. Nếu phạm vi thay đổi có thể dự báo, chọn một abstraction hợp nghĩa.

### 27.1. Ba câu tự kiểm tra

- Nếu người dùng thấy `throws Exception`, họ có biết **loại failure và chính sách xử lý** không?
- Nếu tôi thay implementation hạ tầng, chữ ký API có cần đổi không? Nếu có, đó có thể là leak abstraction.
- Nếu gọi qua interface hoặc callback, mệnh đề checked throws của tôi **có thật sự hợp đồng tương thích** không?

Nếu câu trả lời chưa tốt, hãy xem lại taxonomy hoặc API boundary.

## 28. Kiểm thử hợp đồng ngoại lệ

Một test tốt kiểm tra **loại ngoại lệ và nguyên nhân**, không chỉ “bất kỳ Exception nào cũng được”.

Ví dụ JUnit 5:

```java
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class SpecificThrowsContractTest {
    @Test
    void reportsSpecificIOException() {
        IOException e = assertThrows(IOException.class,
                () -> SpecificFileContractDemo.read("offline"));
        assertEquals("Thiết bị không sẵn sàng", e.getMessage());
    }

    @Test
    void translatesButPreservesCause() {
        var error = assertThrows(
                ExceptionTranslationDemo.CatalogUnavailableException.class,
                ExceptionTranslationDemo::loadCatalog);
        assertInstanceOf(java.sql.SQLException.class,
                error.getCause());
    }

    @Test
    void separatesFormatFromIo() {
        assertThrows(
                TwoSpecificFailuresDemo.InvalidRecordException.class,
                () -> TwoSpecificFailuresDemo.load("oops"));
    }
}
```

Đây là **một file test JUnit Jupiter**, không chạy độc lập bằng `javac` chỉ có JDK; cần JUnit 5 và các demo classes ở cùng package (hoặc điều chỉnh visibility/package). `assertThrows` kiểm tra subtype quan hệ kế thừa; ở những API có hợp đồng rất cụ thể, có thể dùng `assertThrowsExactly` của JUnit phiên bản phù hợp.

**Kiểm tra cả kết quả thành công**, không chỉ thất bại. Đối với dịch exception, xác minh `getCause()` là loại/đối tượng mong đợi, và dữ liệu domain không bị báo là thành công khi tầng dưới ném lỗi.

### 28.1. Test source-level API compatibility

Với thư viện công khai, có thể duy trì các **compile-time contract tests**: caller mô phỏng viết handler cho những checked types được API công bố. Khi đổi clause `throws`, kiểm tra các module downstream có biên dịch lại không. Một unit test runtime đơn lẻ không phát hiện mọi thay đổi source-level của hợp đồng checked exception.

## 29. Câu hỏi thường gặp

### 29.1. Viết `throws Exception` có “sai Java” không?

Không. Hoàn toàn hợp lệ về cú pháp. Vấn đề chủ yếu là **thiết kế hợp đồng**, nhất là public/library API cần caller xử lý failure chính xác.

### 29.2. `throws IOException, SQLException` có được không?

Có, nếu method thực sự công bố hai checked failure và chúng phù hợp với abstraction/override contract. Nhưng ở boundary domain có thể muốn dịch chúng thành một hoặc nhiều exception domain phù hợp để tránh lộ tầng hạ tầng.

### 29.3. Chỉ có `IOException` hiện tại, khai `throws Exception` để dễ nâng cấp sau này được không?

Có thể về cú pháp nhưng thường **không nên** nếu không có lý do hợp đồng. Nó khiến mọi caller chịu loại checked rộng ngay từ đầu. Hãy thiết kế abstraction ổn định theo nhóm lỗi thực tế; quản lý API evolution khi thêm failure mới.

### 29.4. Khai `throws IOException` có ngăn `NullPointerException` không?

Không. `NullPointerException` là unchecked, vẫn có thể xảy ra. `throws` chủ yếu tham gia luật compile-time của checked exceptions.

### 29.5. Tại sao `catch(Exception)` có thể che bug?

Vì `RuntimeException extends Exception`. Nếu handler catch rộng rồi retry/fallback mà không phân loại, nó có thể nuốt cả `NullPointerException` hoặc `IllegalStateException` vốn báo vấn đề lập trình/trạng thái.

### 29.6. Khi nào cần custom exception?

Khi caller cần một phân loại failure **có ngữ nghĩa domain** và hợp đồng JDK không thể hiện đúng, hoặc khi bạn muốn tránh làm lộ exception hạ tầng. Không tạo custom class chỉ để bọc mỗi `IOException` nếu API I/O thuần có thể dùng `IOException` trực tiếp.

### 29.7. Tại sao không chỉ dùng `RuntimeException` cho mọi thứ?

Mặc dù giảm nghĩa vụ compile-time, điều đó có thể làm mất hợp đồng failure mà caller cần xử lý. Checked vs unchecked là quyết định theo API design, không phải lựa chọn “Java hiện đại luôn bỏ checked”.

### 29.8. `throws Exception` có bao gồm `Error` không?

Không. `Error` là nhánh khác dưới `Throwable`. `throws Exception` không phải hợp đồng cho mọi `Throwable`; `catch(Exception)` cũng không bắt `Error`. `throws Throwable` còn rộng hơn và thường không hợp lý với API ứng dụng.

### 29.9. Nếu thêm `throws IOException` vào method interface, mọi implementation phải kê không?

Không. Implementation có thể khai báo ít checked exceptions hơn hoặc không khai báo gì, nhưng không được mở rộng vượt hợp đồng. Caller qua interface vẫn bị compiler kiểm tra theo chữ ký interface.

### 29.10. Javadoc có cần mô tả unchecked exception?

Nên ghi những điều kiện vi phạm hợp đồng quan trọng qua `@throws`, dù unchecked type không bắt buộc xuất hiện ở clause `throws`. Tài liệu rõ giúp caller biết tiền điều kiện.

## 30. Bài tập tự luyện

### Bài 1 — Thu hẹp API đọc file

Một method đang dùng `String read(Path) throws Exception` nhưng chỉ gọi `Files.readString`. Hãy chọn `throws IOException` và cập nhật caller. Vì sao `IOException` phù hợp hơn `Exception` mà vẫn bao phủ cả `FileNotFoundException` subtype?

### Bài 2 — Hai loại lỗi không liên quan

Viết method `importText(String) throws IOException, ParseException`, mô phỏng input “offline” và “bad-format”. Viết caller có hai catch riêng rồi đổi sang multi-catch khi chính sách xử lý giống nhau.

### Bài 3 — Dịch lỗi JDBC

Mô phỏng `SQLException` ở tầng repository, chuyển sang `CatalogUnavailableException` ở tầng domain. Test `getCause()` là `SQLException`; không để caller phụ thuộc `SQLState` như một phần business contract nếu không có nhu cầu.

### Bài 4 — Checked vs unchecked

Một method nhận giá trị phần trăm và gọi nguồn cấu hình có thể ném `IOException`. Nên xử lý `percent = -1` bằng loại nào? Nên công bố `IOException` ra sao? Giải thích vì sao `throws Exception` không bắt buộc.

### Bài 5 — Interface override

Interface `Loader.load() throws IOException`, implementation `throws FileNotFoundException` có hợp lệ không? Nếu đổi implementation thành `throws Exception` thì sao? Giải thích bằng quan hệ subtype.

### Bài 6 — Thay đổi API v2

Phiên bản 1 công bố `throws ParseException`; phiên bản 2 thêm `throws IOException`. Hãy viết một caller phiên bản 1 sẽ không biên dịch lại được nếu không cập nhật handler. Nêu tác động source compatibility.

### Bài 7 — Catch rộng ở boundary

Trong một CLI dispatcher, `catch(Exception)` có thể dùng để log rồi trả exit code lỗi. Vì sao không nên áp cùng catch rồi trả `null` ở mọi repository/service? `Error` có bị catch không?

### Bài 8 — Resource close

Thay `Closeable` trong `CloseableSpecificThrowsDemo` bằng kiểu tĩnh `AutoCloseable` và kiểm tra nghĩa vụ checked của `close() throws Exception`. Tìm cách dùng type/adapter phù hợp để không khai `throws Exception` không cần thiết.

### Bài 9 — Generic callback

Tạo `CheckedSupplier<T,E extends Exception>` nhận lambda ném `IOException`. Viết `execute(...) throws E`; so sánh với `java.util.function.Supplier` vốn không khai checked throws.

### Bài 10 — Một lỗi domain là kết quả bình thường?

Một form có ba field sai. Thảo luận vì sao trả `ValidationResult` gồm ba issue có thể rõ hơn việc ném ba exception. Khi nào một custom checked exception vẫn có ý nghĩa?

### Bài 11 — Test hợp đồng

Viết JUnit test xác nhận method `read("offline")` ném `IOException`, method `load("oops")` ném custom checked, và exception translation giữ `cause`. Đừng chỉ viết `assertThrows(Exception.class, ...)` cho tất cả test.

### Bài 12 — Nâng cấp library

Giả sử một public API đang `throws Exception` và bạn muốn thu hẹp sang `throws IOException`. Thử recompile caller có `catch(SQLException)` rồi `catch(Exception)`; xem handler nào có thể trở nên không reachable và cần refactor.

## 31. Checklist thiết kế API trước khi phát hành

- [ ] Tôi xác định được **những checked failures thực sự thuộc hợp đồng**, không chỉ những exception từ code nội bộ chưa được phân loại?
- [ ] Có thể thay `throws Exception` bằng loại JDK cụ thể như `IOException`/`ParseException` không?
- [ ] Nếu API domain không nên lộ JDBC/HTTP internals, tôi có một exception domain có nghĩa không?
- [ ] Khi dịch lỗi, tôi đã truyền `cause` để không mất stack trace gốc chưa?
- [ ] Caller có thể phân biệt các lỗi có **chính sách xử lý khác nhau** không?
- [ ] Tôi phân biệt checked exception, unchecked exception và `Error` chưa?
- [ ] API chỉ kê checked exception cần thiết; không công bố loại quá rộng để “phòng hờ” chứ?
- [ ] Loại exception đã chọn có đủ ổn định nếu implementation thay đổi không?
- [ ] Tôi không tạo taxonomy quá sâu hoặc một custom `ApplicationException` quá rộng vô nghĩa chứ?
- [ ] Tôi đã viết Javadoc `@throws` cho điều kiện failure và tiền điều kiện quan trọng chưa?
- [ ] Nếu method override interface, `throws` có tương thích với hợp đồng cấp cha chưa?
- [ ] Nếu dùng functional interface chuẩn, tôi đã hiểu hạn chế checked throws của callback chưa?
- [ ] Nếu dùng try-with-resources, đã xét checked exceptions từ `close()` chưa?
- [ ] Tôi không coi mọi `IOException` tự động retryable, mọi validation error phải ném, hay mọi bug có thể phục hồi chứ?
- [ ] Không có `catch(Exception)` ở tầng thấp âm thầm trả null/false/success?
- [ ] Nếu public API thay đổi checked throws, tôi đã xem xét tương thích source và migration chưa?
- [ ] Tôi đã test failure type, message có ý nghĩa, cause, success path và trạng thái sau lỗi chưa?
- [ ] Ngoại lệ và log không chứa dữ liệu bí mật hoặc nội dung raw của request không cần thiết?
- [ ] Có một boundary chịu trách nhiệm chuyển lỗi thành phản hồi API/CLI an toàn không?
- [ ] Tôi đã cân nhắc result type nếu failure là kết quả dự kiến thường xuyên chưa?

## 32. Tổng kết

`throws Exception` không sai cú pháp, nhưng thường là **hợp đồng quá rộng** cho một API cụ thể. Dùng `throws IOException` cho thao tác I/O, nhiều checked types rõ ràng nếu caller cần phân biệt, hoặc custom domain exception khi muốn ẩn chi tiết tầng dưới mà vẫn **giữ `cause`**. Không cần kê unchecked exception chỉ để compiler chấp nhận; thay vào đó, document tiền điều kiện. Checked `throws` ảnh hưởng caller, override, generic callback và sự tương thích khi nâng phiên bản, nên phải chọn loại **đủ hẹp, có ngữ nghĩa và ở đúng mức abstraction**. `throws Exception` vẫn có chỗ đứng trong callback/framework tổng quát và demo đơn giản, nhưng không nên dùng nó như lựa chọn mặc định cho mọi method.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-api-design-004">Phân biệt lỗi tham số, lỗi trạng thái và lỗi từ phụ thuộc</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt lỗi tham số, lỗi trạng thái và lỗi từ phụ thuộc khi thiết kế API Java

## 1. Vì sao phải phân loại đúng lỗi?

Khi một phương thức Java thất bại, người gọi cần biết **mình truyền sai dữ liệu**, **đối tượng/hệ thống đang ở trạng thái không cho phép thao tác**, hay **một thành phần bên ngoài không thực hiện được công việc**. Ba nhóm này dẫn đến cách sửa và cách xử lý khác nhau:

- **Lỗi tham số (invalid argument):** Giá trị đầu vào vi phạm hợp đồng của phương thức. Người gọi thường cần sửa dữ liệu hoặc cách gọi.
- **Lỗi trạng thái (invalid state):** Tham số có thể hoàn toàn hợp lệ, nhưng trạng thái hiện tại của đối tượng hoặc tài nguyên không cho phép thao tác. Người gọi cần thay đổi trình tự thao tác hoặc bảo đảm điều kiện trạng thái.
- **Lỗi từ phụ thuộc (dependency failure):** Đầu vào và trạng thái phía API có thể hợp lệ, nhưng thao tác lên mạng, tệp, cơ sở dữ liệu, hệ thống khác hoặc thư viện phụ thuộc thất bại. Cần xử lý ở ranh giới tích hợp: chẩn đoán, thông báo, thử lại có điều kiện hoặc dự phòng.

Cách phân loại này mô tả **nguyên nhân và trách nhiệm khắc phục**, không phải ba nhánh cố định của cây kế thừa `Exception`. Ví dụ `IllegalArgumentException` và `IllegalStateException` đều là `RuntimeException`; lỗi phụ thuộc có thể xuất hiện dưới dạng ngoại lệ checked, unchecked hoặc ngoại lệ miền nghiệp vụ.

## 2. Bảng quyết định nhanh

| Tiêu chí | Lỗi tham số | Lỗi trạng thái | Lỗi từ phụ thuộc |
| --- | --- | --- | --- |
| Câu hỏi trọng tâm | “Giá trị người gọi đưa vào có đúng hợp đồng?” | “Lời gọi này có hợp lệ **lúc này**?” | “Một thành phần cần thiết có thực thi được?” |
| Nguyên nhân điển hình | Số âm khi yêu cầu số không âm; chuỗi sai định dạng; tham chiếu `null` bị cấm | Dùng dịch vụ sau khi đóng; gọi `commit()` trước `begin()`; đọc tài nguyên chưa khởi tạo | Mất kết nối, timeout, lỗi I/O, DB không sẵn sàng |
| Ngoại lệ Java thường gặp | `IllegalArgumentException`, `NullPointerException`, `IndexOutOfBoundsException` | `IllegalStateException`; đôi khi ngoại lệ trạng thái riêng của API | `IOException`, `SQLException`, `TimeoutException` hoặc loại `...UnavailableException` do API định nghĩa |
| Người gọi thường làm gì? | Sửa đối số; sửa logic gọi | Sửa trình tự hoặc vòng đời tài nguyên | Xem nguyên nhân, khôi phục kết nối, retry nếu an toàn, báo lỗi tạm thời |
| Có nên thử lại nguyên lời gọi ngay? | Thường không; phải sửa tham số | Thường không; phải thay đổi trạng thái trước | Chỉ khi lỗi tạm thời, thao tác an toàn để retry và có chính sách giới hạn |

**Nguyên tắc quan trọng:** Cùng một chuỗi đầu vào có thể dẫn đến nhiều loại lỗi ở các giai đoạn khác nhau. Một mã tiền tệ hợp lệ về cú pháp vẫn có thể không được nhà cung cấp dữ liệu hỗ trợ, hoặc nhà cung cấp có thể mất kết nối khi xử lý mã đó. Hãy phân loại dựa trên **hợp đồng API và nguyên nhân thực**, không chỉ theo thông báo hay tên lớp ngoại lệ.

## 3. Lỗi tham số: vi phạm tiền điều kiện của lời gọi

Tham số sai khi người gọi có thể xác định lỗi **từ hợp đồng công khai của phương thức và giá trị truyền vào**, không phụ thuộc trạng thái bên ngoài. Ví dụ:

- `transfer(amount)` yêu cầu `amount > 0` nhưng nhận `0` hoặc số âm.
- `parsePort(port)` yêu cầu cổng trong khoảng `1..65535` nhưng nhận `70000`.
- `findById(id)` yêu cầu `id` khác `null` nhưng nhận `null`.
- API yêu cầu chuỗi gồm ba chữ cái in hoa mà người gọi truyền chuỗi sai định dạng.

Chọn ngoại lệ:

- `IllegalArgumentException`: tham số không thỏa điều kiện giá trị/định dạng của API.
- `NullPointerException`: thường phù hợp khi tham số tham chiếu bị cấm `null`; `Objects.requireNonNull(value, "value")` là idiom của JDK. API cũng có thể chọn `IllegalArgumentException` cho chính sách riêng, nhưng cần nhất quán.
- `IndexOutOfBoundsException`: dành cho chỉ số vượt miền hợp lệ của một chuỗi, danh sách, mảng hoặc cấu trúc hỗ trợ truy cập theo chỉ số. Đây là trường hợp cụ thể hơn của `RuntimeException`.

Mẫu kiểm tra:

```java
Objects.requireNonNull(name, "name");
if (name.isBlank()) {
    throw new IllegalArgumentException("name must not be blank");
}
if (pageSize < 1 || pageSize > 100) {
    throw new IllegalArgumentException("pageSize must be between 1 and 100");
}
```

Đây là **đoạn mã đặt bên trong phương thức** đã khai báo biến `name` và `pageSize`, không phải chương trình độc lập. `Objects.requireNonNull` ném `NullPointerException` khi nhận `null`; `String.isBlank()` được hỗ trợ từ Java 11. Thông báo lỗi nên nói rõ tên tham số và quy tắc bị vi phạm nhưng tránh in dữ liệu nhạy cảm.

**Ranh giới cần nhớ:** “Không tìm thấy tài khoản theo ID” không mặc nhiên là đối số sai. ID có thể đúng định dạng, nhưng tài khoản không tồn tại: đó là trường hợp *resource not found* trong miền nghiệp vụ, thường có ngoại lệ hoặc giá trị kết quả riêng. Cũng không nên dùng `IllegalArgumentException` để che lỗi DB chỉ vì lời gọi DB có truyền một tham số.

## 4. Lỗi trạng thái: giá trị hợp lệ nhưng thao tác sai thời điểm

Lỗi trạng thái xuất hiện khi tiền điều kiện phụ thuộc vào **vòng đời, pha xử lý hoặc trạng thái hiện tại** của đối tượng/tài nguyên:

- Gọi `send()` sau `close()`.
- Gọi `commit()` khi chưa bắt đầu transaction.
- Gọi `next()` trên đối tượng đã bị đóng, nếu hợp đồng quy định không được dùng sau khi đóng.
- Gọi `start()` lần thứ hai trong khi bộ xử lý chỉ cho phép khởi động một lần.

`IllegalStateException` diễn đạt rằng **lời gọi không phù hợp với trạng thái của đối tượng hoặc ứng dụng**. Nó hữu ích cho API đồng bộ nội bộ và object lifecycle:

```java
if (closed) {
    throw new IllegalStateException("service is closed");
}
```

Đoạn này giả định có biến trạng thái `closed` trong lớp sở hữu thao tác.

Khác biệt quan trọng: một tham số hợp lệ không trở nên “sai tham số” chỉ vì đối tượng đã đóng. Trạng thái cũng không đồng nghĩa với “mọi lỗi xảy ra lúc chạy”; một DB bị timeout là lỗi phụ thuộc, dù xảy ra khi đối tượng Java đang ở trạng thái sử dụng bình thường.

Các API đã định nghĩa ngoại lệ chuyên biệt cần tuân thủ hợp đồng của chính chúng. Ví dụ `UnsupportedOperationException` thường thể hiện thao tác không được hỗ trợ, không nhất thiết là trạng thái tạm thời; đừng thay bằng `IllegalStateException` chỉ để thống nhất hình thức.

## 5. Lỗi từ phụ thuộc: hệ thống bên ngoài thất bại

“Phụ thuộc” là thứ phương thức cần gọi để hoàn thành công việc: HTTP client, database, file system, message broker, RPC service, thư viện/native service hoặc nguồn dữ liệu từ một module khác. Hãy phân biệt ba trường hợp:

1. **Không liên lạc được:** mất mạng, timeout, tên miền không phân giải, kết nối bị từ chối.
2. **Liên lạc được nhưng xử lý thất bại:** DB báo tạm quá tải, dịch vụ trả lỗi 5xx, file không thể đọc do I/O.
3. **Phụ thuộc trả dữ liệu/hành vi vi phạm hợp đồng tích hợp:** payload thiếu trường bắt buộc, phản hồi không thể phân tích, phiên bản schema không tương thích.

Trong Java, `IOException` là ngoại lệ checked điển hình khi thao tác I/O thất bại; `SQLException` có thể xuất hiện từ JDBC. Thư viện HTTP/DB cụ thể có thể dùng ngoại lệ unchecked, ngoại lệ theo danh mục transient/permanent hoặc exception wrapper; không có một lớp Java duy nhất bao quát mọi lỗi phụ thuộc.

**Không phải mọi lỗi do dependency ném ra đều thuộc nhóm dependency failure.** Ví dụ một HTTP service trả `400` vì người dùng nhập trường bị cấm có thể tương ứng với lỗi đầu vào của API công khai; lỗi `401` có thể do thông tin xác thực của ứng dụng tích hợp; phản hồi `404` có thể là đối tượng nghiệp vụ không tồn tại. Cần đối chiếu chính xác hợp đồng từng tầng và ai có khả năng sửa lỗi.

Với lỗi phụ thuộc, thiết kế hợp đồng nên cân nhắc:

- **Giữ nguyên loại gốc** khi caller cần phân biệt `IOException`, `SQLException`... và API muốn cho phép xử lý chi tiết.
- **Dùng ngoại lệ miền hoặc ranh giới** (ví dụ `RateProviderUnavailableException`) khi muốn tránh rò rỉ công nghệ nội bộ; luôn giữ `cause` gốc.
- **Phân biệt lỗi tạm thời và lâu dài** dựa trên dữ liệu đáng tin: timeout/kết nối mất có thể tạm thời; cấu hình sai hoặc thông tin xác thực hết hiệu lực thường đòi hỏi sửa cấu hình.
- **Giới hạn retry:** có số lần chờ tối đa, timeout hợp lý, backoff/jitter và kiểm tra tính idempotent của thao tác. Không tự động retry một thao tác thanh toán hoặc ghi dữ liệu không có cơ chế chống trùng.
- **Không tiết lộ bí mật:** log theo ngữ cảnh phù hợp, ẩn token, mật khẩu, payload riêng tư và chi tiết kết nối khi trả lỗi cho người dùng.

## 6. Ví dụ Java 17+: cùng một API, ba nguyên nhân lỗi

Chương trình dưới đây có **một lớp public, một file độc lập** và mô phỏng nhà cung cấp tỷ giá. Nó kiểm tra đối số trước, sau đó kiểm tra trạng thái dịch vụ, cuối cùng mới gọi phụ thuộc. Mã `EUR` cố ý tạo lỗi I/O giả lập để dễ thấy nhánh dependency.

Tạo file `ExceptionTaxonomyDemo.java`:

```java
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Objects;

public class ExceptionTaxonomyDemo {
    @FunctionalInterface
    interface RateProvider {
        BigDecimal rateOf(String currency) throws IOException;
    }

    static final class QuoteService {
        private final RateProvider provider;
        private boolean closed;

        QuoteService(RateProvider provider) {
            this.provider = Objects.requireNonNull(provider, "provider");
        }

        BigDecimal quote(BigDecimal amount, String currency) throws IOException {
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(currency, "currency");
            if (amount.signum() < 0) {
                throw new IllegalArgumentException("amount must be >= 0");
            }
            if (!currency.matches("[A-Z]{3}")) {
                throw new IllegalArgumentException("currency must have 3 uppercase letters");
            }
            if (closed) {
                throw new IllegalStateException("service is closed");
            }
            return amount.multiply(provider.rateOf(currency));
        }

        void close() {
            closed = true;
        }
    }

    public static void main(String[] args) throws IOException {
        QuoteService service = new QuoteService(currency -> {
            if ("EUR".equals(currency)) {
                throw new IOException("exchange-rate endpoint unavailable");
            }
            return new BigDecimal("25000");
        });

        System.out.println("OK: " + service.quote(new BigDecimal("2"), "VND"));

        try {
            service.quote(new BigDecimal("-1"), "VND");
        } catch (IllegalArgumentException ex) {
            System.out.println("PARAM: " + ex.getClass().getSimpleName());
        }

        try {
            service.quote(new BigDecimal("2"), "EUR");
        } catch (IOException ex) {
            System.out.println("DEPENDENCY: " + ex.getClass().getSimpleName());
        }

        service.close();
        try {
            service.quote(new BigDecimal("2"), "VND");
        } catch (IllegalStateException ex) {
            System.out.println("STATE: " + ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy từ thư mục chứa file (JDK 17 hoặc mới hơn):

```bash
javac -encoding UTF-8 ExceptionTaxonomyDemo.java
java ExceptionTaxonomyDemo
```

```text
OK: 50000
PARAM: IllegalArgumentException
DEPENDENCY: IOException
STATE: IllegalStateException
```

Giải thích các nhánh:

1. `quote(2, "VND")`: số tiền và mã tiền tệ hợp lệ, dịch vụ mở, nhà cung cấp trả `25000` nên kết quả là `2 × 25000 = 50000`.
2. `quote(-1, "VND")`: số tiền vi phạm điều kiện `amount >= 0`, ném `IllegalArgumentException` **trước khi** gọi dependency.
3. `quote(2, "EUR")`: cả đầu vào và trạng thái đều hợp lệ, nhưng provider giả lập ném `IOException`. Đây là lỗi phụ thuộc, không phải lỗi tham số.
4. Sau `service.close()`, `quote(2, "VND")` ném `IllegalStateException`; tham số vẫn hợp lệ và provider không được gọi.

Ví dụ cố tình đơn giản: tỷ giá cố định chỉ phục vụ minh họa, biểu thức `[A-Z]{3}` kiểm tra **định dạng** chứ không chứng minh mã được ISO 4217 công nhận, `QuoteService` không hỗ trợ truy cập đồng thời và `close()` chỉ đổi cờ nội bộ (không triển khai `AutoCloseable`). Trong ứng dụng thật cần kiểm tra tính hợp lệ theo nghiệp vụ, quản lý tài nguyên và đồng bộ hóa phù hợp.

## 7. Thiết kế hợp đồng ngoại lệ cho phương thức public

Một API tốt cho caller biết những gì nó phải bảo đảm trước khi gọi, trạng thái cần thiết và loại thất bại bên ngoài có thể gặp. Ví dụ tài liệu `quote` tương ứng với chương trình trên:

```java
/**
 * Quy đổi amount theo tỷ giá do provider cung cấp.
 *
 * @param amount số tiền không âm; không được null
 * @param currency mã gồm đúng 3 chữ cái in hoa; không được null
 * @return số tiền sau quy đổi
 * @throws NullPointerException nếu amount hoặc currency là null
 * @throws IllegalArgumentException nếu amount âm hoặc currency sai định dạng
 * @throws IllegalStateException nếu service đã đóng
 * @throws IOException nếu rate provider bị lỗi I/O
 */
```

Đây là đoạn **Javadoc đặt ngay trước khai báo phương thức `quote`**, không phải file biên dịch độc lập. Ký hiệu `@throws` mô tả cả ngoại lệ unchecked; chỉ ngoại lệ checked như `IOException` cần có trong `throws` của chữ ký phương thức (trừ trường hợp xử lý ngay trong phương thức).

Khi chọn kiểu lỗi, tự hỏi:

1. Caller có thể phát hiện điều kiện sai chỉ từ giá trị của chính tham số và hợp đồng không? Nếu có, nhóm lỗi tham số.
2. Tham số vẫn hợp lệ nhưng tài nguyên/đối tượng đang ở pha không cho phép thao tác? Nhóm lỗi trạng thái.
3. Gọi tới hệ thống/đối tượng phụ thuộc và nó không hoàn thành cam kết? Nhóm lỗi phụ thuộc.
4. Đó là trường hợp nghiệp vụ dự kiến (không tìm thấy, hết hàng, bị từ chối vì quyền hạn...) hay lỗi lập trình? Cân nhắc **kết quả hoặc ngoại lệ chuyên biệt**; không cưỡng ép vào ba nhóm nếu không đúng ý nghĩa.

Ưu tiên tên lỗi phản ánh trách nhiệm và khả năng phục hồi. Đừng chọn `IllegalStateException` chỉ để khỏi khai báo `throws IOException`. Việc checked hay unchecked là một quyết định API **riêng** với phân loại nguyên nhân.

## 8. Chuyển lỗi qua tầng trừu tượng: giữ đúng nhóm nguyên nhân

Tầng hạ tầng có thể ném `IOException` hoặc `SQLException`; tầng nghiệp vụ có thể công bố một loại lỗi ổn định hơn. **Chỉ chuyển khi tầng hiện tại hiểu chính xác ngữ nghĩa**, không dùng message của driver hoặc tên exception ngẫu nhiên làm bằng chứng:

~~~java
try {
    return provider.quote(currency);
} catch (java.io.IOException cause) {
    throw new RateProviderUnavailableException(
            "Không thể lấy tỷ giá", cause);
}
~~~

Đây là mảnh mã minh họa với `provider`, `currency` và constructor domain exception đã được định nghĩa. `cause` phải được truyền vào wrapper để người điều tra có thể truy lỗi nguồn và suppressed exceptions nếu có. Đừng đánh đồng **input sai** với **trạng thái chưa sẵn sàng** hay **dependency không hoạt động** chỉ để có một loại ngoại lệ chung.

Với tiêu đề bài này, câu hỏi quan trọng vẫn là **lỗi thuộc nhóm nào và caller được làm gì**. Bài tiếp theo về exception translation chỉ ra nơi dịch, khi nào không dịch và cách kiểm thử; bài giữ `cause` đi sâu vào danh tính đối tượng nguyên nhân.

## 9. Ánh xạ ở ranh giới HTTP hoặc service

Khi một API Java đứng sau HTTP, HTTP status cần phản ánh **hợp đồng cụ thể**; không có ánh xạ một-một bắt buộc giữa từng lớp exception và status:

| Nguyên nhân đã xác định | Hướng phản hồi HTTP thường thấy | Lưu ý |
| --- | --- | --- |
| Request sai cú pháp, thiếu trường hoặc vi phạm validation công khai | `400 Bad Request` hoặc `422 Unprocessable Content` | Mô tả trường cần sửa, không đưa stack trace ra client |
| Xung đột với trạng thái tài nguyên đang lưu | `409 Conflict` (tùy API) | `IllegalStateException` do bug nội bộ không mặc nhiên thành `409` |
| Tài nguyên được hỏi không tồn tại | `404 Not Found` | Thường là trường hợp miền nghiệp vụ riêng, không mặc nhiên là lỗi tham số |
| Upstream trả phản hồi lỗi/hỏng | `502 Bad Gateway` (trong vai trò gateway/proxy phù hợp) | Chọn theo vai trò thực tế của service |
| Phụ thuộc tạm không sẵn sàng | `503 Service Unavailable` | Có thể kèm chỉ dẫn thử lại khi có căn cứ |
| Timeout upstream | `504 Gateway Timeout` (khi thực sự là gateway/proxy) | Phân biệt với timeout ở chính service |
| Lỗi triển khai nội bộ chưa xử lý | `500 Internal Server Error` | Ghi log nội bộ, hạn chế chi tiết phản hồi |

Ví dụ API có quy tắc “chỉ hủy đơn ở trạng thái PENDING” thì hủy đơn đã giao là **xung đột trạng thái nghiệp vụ**, thường biểu diễn bằng lỗi miền riêng như `OrderCannotBeCancelledException`. Ngược lại một đối tượng client Java đã `close()` rồi vẫn bị sử dụng là vi phạm vòng đời đối tượng, thường phù hợp với `IllegalStateException`. Hai trường hợp cùng liên quan “trạng thái” nhưng mức API và cách trả HTTP có thể khác nhau.

## 10. Các lỗi thiết kế phổ biến

- **Đổi mọi lỗi thành `IllegalArgumentException`:** đánh đồng dịch vụ mất kết nối với dữ liệu người gọi sai; caller sẽ cố sửa một giá trị vốn hợp lệ.
- **Đổi mọi lỗi thành `IllegalStateException`:** che giấu IOException, SQL timeout, bug và điều kiện nghiệp vụ khác nhau; khó phân biệt retry với sửa trình tự gọi.
- **`catch (Exception ex)` rồi trả `null`/`0`:** biến thất bại thành kết quả hợp lệ giả, làm phát sinh lỗi ở đoạn mã xa hơn. Chỉ dùng fallback nếu hợp đồng cho phép và giá trị fallback có ý nghĩa rõ.
- **`throw new RuntimeException(ex.getMessage())`:** đánh mất `cause` và stack gốc. Nếu cần bọc, dùng constructor có `cause`.
- **Gọi lại API ngay với cùng tham số cho mọi exception:** không sửa được lỗi tham số hoặc lỗi trạng thái, có thể làm trùng giao dịch khi lỗi phụ thuộc xảy ra sau khi bên kia đã nhận lệnh.
- **Dùng thông điệp văn bản làm khóa phân loại:** văn bản có thể thay đổi và được địa phương hóa. Ưu tiên loại exception, mã lỗi ổn định hoặc thuộc tính cấu trúc.
- **Bỏ qua khác biệt giữa lỗi phụ thuộc và kết quả miền nghiệp vụ:** ví dụ HTTP 404 “khách hàng không tồn tại” khác với gateway không truy cập được server.
- **Rò rỉ chi tiết nhạy cảm:** trả stack trace, SQL, chuỗi kết nối hoặc token vào thông báo dành cho client.

## 11. Kiểm thử đúng nhóm và đúng thời điểm thất bại

Kiểm thử nên kiểm tra **loại ngoại lệ** và **không gọi dependency ở nhánh sai điều kiện**. Với một fake hoặc mock có bộ đếm số lần gọi, các trường hợp tối thiểu:

| Tình huống | Kỳ vọng | Provider được gọi? |
| --- | --- | --- |
| `amount = null`, dịch vụ mở | `NullPointerException` | Không |
| `amount = -1`, dịch vụ mở | `IllegalArgumentException` | Không |
| `currency = "vn"`, dịch vụ mở | `IllegalArgumentException` | Không |
| `amount = 2`, `currency = "VND"`, dịch vụ mở | Trả kết quả đúng | Có, một lần |
| `amount = 2`, `currency = "EUR"`, provider ném `IOException` | Giữ `IOException` hoặc bọc đúng loại và giữ `cause` | Có, một lần |
| Sau khi đóng dịch vụ, tham số hợp lệ | `IllegalStateException` | Không |

Ngoài happy path, nên kiểm tra thông báo có ích, `cause` không bị mất khi dịch lỗi, timeout được giới hạn, retry không tạo tác dụng phụ lặp lại, và logging không rò thông tin. Nếu đã quyết định **ưu tiên validation tham số trước kiểm tra trạng thái** như ví dụ, thêm test trường hợp đồng thời vừa sai tham số vừa đóng dịch vụ để bảo vệ thứ tự đó. Thứ tự ưu tiên không có quy tắc Java bắt buộc; API phải chọn và giữ hợp đồng nhất quán.

Với tài nguyên có thể bị thay đổi bởi luồng khác, kiểm tra `closed` rồi gọi tiếp có thể xảy ra race condition nếu không bảo vệ đồng bộ. Ví dụ học tập ở trên dành cho luồng đơn; ứng dụng thực tế cần xử lý thread safety theo hợp đồng riêng.

## 12. Checklist khi thiết kế hoặc review một API

1. Viết rõ tiền điều kiện của tham số: `null`, miền giá trị, định dạng, quy tắc nghiệp vụ công khai.
2. Viết rõ điều kiện trạng thái: trước/sau `open`, `start`, `commit`, `close`; xác định lời gọi nào hợp lệ.
3. Liệt kê phụ thuộc có thể thất bại và cách chúng báo lỗi; xác định lỗi nào có thể phục hồi.
4. Chọn exception hoặc kiểu kết quả thể hiện chính xác nguyên nhân và trách nhiệm của caller; dùng exception miền khi cần.
5. Ghi tài liệu `@throws`, giữ `cause` khi chuyển tầng, quy định retry/timeout khi phù hợp.
6. Kiểm thử ba nhánh lỗi, không gây tác dụng phụ không cần thiết khi validation/state thất bại, và bảo đảm dữ liệu phản hồi an toàn.

**Quy tắc nhận diện:** *Tham số sai* → sửa giá trị truyền vào; *trạng thái sai* → sửa trình tự hoặc vòng đời; *phụ thuộc thất bại* → xử lý ranh giới tích hợp và phục hồi khi an toàn. Một API dễ dùng giúp caller nhìn thấy khác biệt đó qua hợp đồng và loại lỗi, thay vì phải đoán từ thông báo exception.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-api-design-005">Chuyển đổi ngoại lệ tại ranh giới trừu tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Chuyển đổi ngoại lệ tại ranh giới trừu tượng trong Java

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Giải thích vì sao một API nên biểu đạt lỗi theo ngôn ngữ của chính tầng trừu tượng mà nó cung cấp.
- Xác định đúng vị trí chuyển đổi ngoại lệ (exception translation) giữa tầng hạ tầng và tầng nghiệp vụ.
- Ánh xạ ngoại lệ cụ thể thành ngoại lệ có ý nghĩa đối với người gọi, giữ nguyên nguyên nhân gốc bằng cơ chế cause chaining.
- Thiết kế hợp đồng lỗi nhất quán, quyết định hợp lý giữa checked exception và unchecked exception.
- Tránh che giấu lỗi, đóng gói lặp nhiều tầng, bắt quá rộng hoặc xử lý sai ngắt luồng.

## 2. Exception translation là gì?

**Exception translation** là kỹ thuật bắt ngoại lệ được phát sinh ở một tầng triển khai và chuyển nó thành ngoại lệ thuộc hợp đồng của tầng đang cung cấp API. Người gọi nhận được một loại lỗi phản ánh thao tác mà họ yêu cầu, thay vì phải biết chi tiết tầng bên dưới dùng tệp, JDBC, HTTP client hay thư viện thứ ba nào.

Ví dụ, phương thức của kho tài liệu cung cấp thao tác **đọc tài liệu theo tên**. Bên dưới, nó dùng hệ thống tệp và có thể gặp <code>NoSuchFileException</code> hoặc <code>IOException</code>. Tầng kho tài liệu có thể công bố hai lỗi có ngữ nghĩa gần với nhu cầu của người dùng API:

- <code>DocumentNotFoundException</code>: tài liệu được yêu cầu không tồn tại.
- <code>DocumentAccessException</code>: không thể đọc tài liệu vì lỗi truy cập/hạ tầng.

Đây là thay đổi về **mức trừu tượng của lỗi**, chứ không phải thao tác sửa chữa nguyên nhân gốc. Ngoại lệ mới vẫn nên lưu ngoại lệ cũ trong <code>cause</code> để hỗ trợ chẩn đoán.

Một luồng xử lý điển hình:

~~~text
Application / Use case
        |
        | read("intro.txt")
        v
DocumentRepository       <- hợp đồng lỗi theo "tài liệu"
        |
        | Files.readString(...)
        v
Java NIO / File system   <- lỗi theo "đường dẫn, tệp, I/O"

NoSuchFileException
        |
        | catch, phân loại, giữ cause
        v
DocumentNotFoundException
        |
        v
Application xử lý theo nghĩa "không tìm thấy tài liệu"
~~~

Nếu khách hàng của <code>DocumentRepository</code> phải bắt trực tiếp hàng loạt loại <code>IOException</code> riêng của cơ chế lưu trữ, ranh giới trừu tượng đã để lọt chi tiết triển khai ra hợp đồng bên trên.

## 3. Khi nào nên chuyển đổi ngoại lệ?

**Nên chuyển đổi** khi cả ba điều kiện sau có ý nghĩa:

1. **Tầng hiện tại sở hữu một hợp đồng độc lập**: chẳng hạn repository, gateway, adapter, service API hoặc facade.
2. **Ngoại lệ bên dưới không phải ngôn ngữ phù hợp cho người gọi**: lỗi từ JDBC, filesystem hoặc HTTP client chỉ phản ánh cách cài đặt.
3. **Có ánh xạ đáng tin cậy** từ lỗi bên dưới sang ý nghĩa của thao tác: ví dụ không có bản ghi/tài liệu, xung đột phiên bản, từ chối truy cập, hoặc lỗi phụ thuộc tạm thời.

**Không nên chuyển đổi máy móc** mọi ngoại lệ tại mọi lời gọi phương thức. Trong một thư viện thao tác tệp được thiết kế để cho người gọi xử lý I/O trực tiếp, khai báo <code>throws IOException</code> có thể là hợp đồng đúng. Nếu loại ngoại lệ đã thuộc ngôn ngữ API và người gọi hiểu được nó, việc bọc thêm một ngoại lệ chung chỉ làm mất thông tin.

Cũng cần phân biệt:

- **Translation**: đổi kiểu hoặc ngữ nghĩa ngoại lệ; lỗi tiếp tục được ném ra.
- **Handling/recovery**: thật sự xử lý được lỗi, ví dụ dùng nguồn dự phòng hoặc cho người dùng thử lại.
- **Logging**: ghi nhận lỗi để quan sát; bản thân ghi log không xử lý được lỗi.
- **Mapping ở ranh giới giao tiếp**: chuyển lỗi nghiệp vụ thành HTTP response, gRPC status hoặc mã phản hồi. Việc này có thể xảy ra tại một tầng khác, sau khi exception translation đã hoàn thành.

## 4. Phân loại lỗi trước khi ánh xạ

Một ngoại lệ hạ tầng không luôn tương đương với một ngoại lệ nghiệp vụ. Cần dựa vào ngữ cảnh thao tác và các tín hiệu đáng tin cậy của API bên dưới.

| Tín hiệu ở tầng thấp | Ví dụ lỗi phù hợp ở tầng cao | Điều cần kiểm tra |
| --- | --- | --- |
| Không tìm thấy tệp khi đọc tài liệu | <code>DocumentNotFoundException</code> | Tệp đó thực sự đại diện cho tài liệu được yêu cầu |
| Không có bản ghi theo ID | <code>EntityNotFoundException</code> | Kết quả rỗng có phải tình huống bình thường theo hợp đồng hay không |
| Vi phạm khóa duy nhất khi tạo tài nguyên | <code>DuplicateResourceException</code> hoặc lỗi xung đột | Xác định chính xác ràng buộc, tránh coi mọi lỗi SQL là trùng dữ liệu |
| Timeout hoặc lỗi kết nối dịch vụ ngoài | <code>ExternalServiceUnavailableException</code> | Chỉ khẳng định tính tạm thời nếu có cơ sở; timeout không chứng minh thao tác chưa chạy |
| <code>IOException</code> không phân loại rõ | <code>DocumentAccessException</code> | Giữ cause để tra cứu nguyên nhân chi tiết |
| Dữ liệu đầu vào không hợp lệ trước khi gọi hạ tầng | <code>IllegalArgumentException</code> hoặc lỗi validation của API | Kiểm tra trước; không giả vờ đó là lỗi I/O |

**Nguyên tắc:** chuyển đổi theo *ngữ nghĩa có thể chứng minh*, tránh suy ra nguyên nhân chỉ từ chuỗi thông báo của ngoại lệ. Không so sánh <code>getMessage()</code> với một đoạn chữ cố định để quyết định loại lỗi; thông báo có thể thay đổi theo hệ điều hành, driver, ngôn ngữ hoặc phiên bản thư viện.

Thứ tự các khối <code>catch</code> cũng quan trọng. Ví dụ <code>NoSuchFileException</code> là lớp con của <code>IOException</code>, nên phải bắt loại chuyên biệt trước loại bao quát; làm ngược sẽ khiến nhánh chuyên biệt không thể tới được và Java báo lỗi biên dịch.

## 5. Ví dụ Java 17+ chạy độc lập

Ví dụ dùng một cổng truy cập văn bản có thể thay thế được để tái hiện ba tình huống **thành công**, **không tìm thấy**, **lỗi I/O** mà không phụ thuộc vào các tệp đang có trên máy. Điểm chuyển đổi nằm trong <code>DocumentRepository.read</code>. Ở môi trường thực tế, cổng đó có thể được nối với <code>Files.readString</code>, hệ cơ sở dữ liệu hoặc dịch vụ ngoài.

Tạo tệp **ExceptionTranslationDemo.java**:

~~~java
import java.io.IOException;
import java.nio.file.NoSuchFileException;

public class ExceptionTranslationDemo {

    @FunctionalInterface
    interface TextStore {
        String read(String name) throws IOException;
    }

    static final class DocumentNotFoundException extends RuntimeException {
        DocumentNotFoundException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static final class DocumentAccessException extends RuntimeException {
        DocumentAccessException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static final class DocumentRepository {
        private final TextStore store;

        DocumentRepository(TextStore store) {
            this.store = java.util.Objects.requireNonNull(store);
        }

        String read(String name) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException(
                        "Tên tài liệu không được để trống");
            }

            try {
                return store.read(name);
            } catch (NoSuchFileException e) {
                throw new DocumentNotFoundException(
                        "Không tìm thấy tài liệu: " + name, e);
            } catch (IOException e) {
                throw new DocumentAccessException(
                        "Không thể đọc tài liệu: " + name, e);
            }
        }
    }

    private static void show(String scenario, DocumentRepository repository) {
        try {
            System.out.println(scenario + ": " + repository.read("intro.txt"));
        } catch (DocumentNotFoundException | DocumentAccessException e) {
            System.out.println(
                    scenario + ": " + e.getClass().getSimpleName()
                    + " (cause=" + e.getCause().getClass().getSimpleName() + ")");
        }
    }

    public static void main(String[] args) {
        show("Thành công",
                new DocumentRepository(name -> "Xin chào Java"));

        show("Không tồn tại",
                new DocumentRepository(name -> {
                    throw new NoSuchFileException(name);
                }));

        show("Lỗi I/O",
                new DocumentRepository(name -> {
                    throw new IOException("Ổ đĩa không khả dụng");
                }));
    }
}
~~~

Biên dịch và chạy với Java 17 hoặc mới hơn:

~~~bash
javac -encoding UTF-8 ExceptionTranslationDemo.java
java ExceptionTranslationDemo
~~~

Kết quả chuẩn (stdout):

~~~text
Thành công: Xin chào Java
Không tồn tại: DocumentNotFoundException (cause=NoSuchFileException)
Lỗi I/O: DocumentAccessException (cause=IOException)
~~~

**Cơ chế của từng nhánh:**

1. Nếu <code>TextStore.read</code> trả về văn bản, <code>DocumentRepository.read</code> trả về nguyên văn, không tạo ngoại lệ.
2. Nếu cổng ném <code>NoSuchFileException</code>, repository ném <code>DocumentNotFoundException</code>. Người gọi biết **tài liệu không tìm thấy**, không cần biết nơi lưu tài liệu.
3. Nếu cổng ném một <code>IOException</code> khác, repository ném <code>DocumentAccessException</code>. Như vậy các lỗi I/O chưa phân loại vẫn được biểu đạt chính xác ở mức tổng quát.
4. Mỗi ngoại lệ mới được khởi tạo với <code>super(message, cause)</code>, nên <code>getCause()</code> trả về đúng ngoại lệ thấp hơn. Chuỗi cause còn có thể chứa nhiều tầng khi cần chẩn đoán.

Để sử dụng Java NIO thật, có thể thay cách khởi tạo cổng như đoạn **minh họa tích hợp** sau (đây là biểu thức đặt trong mã có import và ngữ cảnh tương ứng, không phải một chương trình Java độc lập):

~~~java
DocumentRepository repository = new DocumentRepository(
        name -> java.nio.file.Files.readString(
                java.nio.file.Path.of(name),
                java.nio.charset.StandardCharsets.UTF_8));
~~~

Cần lưu ý: <code>Files.readString</code> còn có thể ném ngoại lệ không thuộc <code>IOException</code>, ví dụ <code>SecurityException</code> theo ngữ cảnh kiểm tra quyền truy cập. Hai nhánh <code>catch</code> trên chỉ chuyển đổi <code>NoSuchFileException</code> và <code>IOException</code>; ví dụ không tuyên bố xử lý tất cả lỗi có thể có của hệ thống tệp.

## 6. Giữ `cause`: chuyển ngữ nghĩa mà không làm mất nguồn gốc

Khi `DocumentRepository` bắt `IOException` rồi ném `DocumentAccessException`, đối tượng mới cần message ở tầng tài liệu **và** tham chiếu tới đúng lỗi hạ tầng:

~~~java
catch (IOException original) {
    throw new DocumentAccessException(
            "Không thể đọc tài liệu", original);
}
~~~

Đây là mảnh minh họa thuộc phương thức có `try` tương ứng. `getCause()` trên wrapper trỏ tới **chính instance `original`**; các stack frame/suppressed exceptions vốn có của lỗi gốc vẫn thuộc đối tượng ấy. Không chỉ chép `original.getMessage()` vào message của wrapper, và không tự copy stack trace sang object mới. Nếu lỗi đã là loại public thích hợp, có thể để nó truyền lên thay vì bọc thêm một lớp chỉ nói “failed”.

Bài **giữ `cause` khi chuyển lỗi kỹ thuật** kiểm tra sâu danh tính cause, chuỗi nhiều tầng và tác động của suppressed; bài hiện tại tập trung ở **ranh giới chuyển đổi và phân loại loại lỗi public**.

## 7. Thiết kế hợp đồng ngoại lệ cho API

Trước khi khai báo ngoại lệ mới, hãy xác định **người gọi cần có hành động khác nhau hay không**. Nếu khách hàng có thể chọn hành động riêng cho “không tìm thấy” và “không thể truy cập”, hai loại lỗi tách biệt đem lại giá trị. Nếu không thể phân biệt đáng tin cậy các nguyên nhân, một loại lỗi truy cập tổng quát có thể phù hợp hơn.

Một hợp đồng tốt cần:

- **Tên ổn định và thể hiện ý nghĩa**: phân biệt tài nguyên không tồn tại, yêu cầu sai, xung đột và lỗi phụ thuộc.
- **Phạm vi sở hữu rõ**: ngoại lệ thuộc API của tầng đó, tránh buộc mọi lớp bên trên phụ thuộc driver hoặc thư viện cụ thể.
- **Tài liệu hóa trường hợp phát sinh**: với unchecked exception, vẫn mô tả chúng trong Javadoc/contract.
- **Giữ nguyên cause**, bổ sung định danh nghiệp vụ hữu ích nếu an toàn (ví dụ mã tài liệu thay vì đường dẫn máy chủ).
- **Không hứa sai mức độ khôi phục**: “truy cập thất bại” không đương nhiên có nghĩa “thử lại sẽ thành công”.

Ví dụ khai báo hợp đồng (minh họa API, không phải chương trình độc lập):

~~~java
public interface DocumentReader {
    /**
     * Đọc văn bản của tài liệu đã định danh.
     *
     * @throws DocumentNotFoundException nếu tài liệu không tồn tại
     * @throws DocumentAccessException nếu kho tài liệu không thể đọc dữ liệu
     * @throws IllegalArgumentException nếu tên tài liệu không hợp lệ
     */
    String read(String name);
}
~~~

Trong API công khai, các ngoại lệ tùy biến nên có độ hiển thị phù hợp (<code>public</code> nếu client ở package khác cần bắt chúng), và code của client chỉ nên dựa trên những loại đã được công bố. Trong bản demo, các lớp được lồng gọn trong một tệp để dễ chạy, nên chưa minh họa cách tổ chức nhiều tệp cho thư viện được phát hành.

### Checked hay unchecked?

Java chia ngoại lệ thành:

- **Checked exception**: mọi <code>Exception</code> không kế thừa <code>RuntimeException</code>; trình biên dịch buộc người gọi bắt hoặc khai báo <code>throws</code>.
- **Unchecked exception**: <code>RuntimeException</code> và các lớp con của nó (ngoài ra <code>Error</code> và lớp con cũng unchecked). Người gọi không bắt buộc khai báo/bắt.

Ví dụ dùng unchecked domain exception để API không ràng buộc từng lời gọi với <code>throws IOException</code>. Đây là **quyết định thiết kế của ví dụ**, không phải quy tắc chung.

Chọn checked exception nếu API muốn buộc người gọi nhận biết tình huống thất bại dự kiến và thường có đường xử lý hợp lý. Chọn unchecked khi lỗi không thể hoặc không nên bị ép xử lý tại từng điểm gọi, hoặc theo quy ước chung của kiến trúc. Dù chọn loại nào, vẫn phải giữ ngữ nghĩa, cause và tài liệu hợp đồng nhất quán. Đừng chỉ đổi <code>IOException</code> thành <code>RuntimeException</code> vô danh để né trình biên dịch.

## 8. Chọn đúng ranh giới chuyển đổi

Tại một hệ thống nhiều tầng, ví dụ:

~~~text
Controller / CLI / Consumer
          |
          v
      Use-case Service
          |
          v
   DocumentRepository       => chuyển lỗi file/JDBC thành lỗi kho tài liệu
          |
          v
       File system

HTTP boundary / CLI boundary => nếu cần, ánh xạ lỗi API sang status/thông báo
~~~

Một lỗi chỉ nên được chuyển khi **ý nghĩa mà tầng đó cung cấp thật sự thay đổi**. Repository có thể sở hữu <code>DocumentNotFoundException</code>. Service nên để lỗi đi qua nếu nó đã khớp hợp đồng của service; chỉ chuyển lần nữa khi service bổ sung ngữ nghĩa khác, chẳng hạn “không thể hoàn tất phát hành hồ sơ” bao gồm nhiều thao tác nghiệp vụ.

Nếu tầng HTTP ánh xạ <code>DocumentNotFoundException</code> thành trạng thái HTTP 404, đó là quy tắc của adapter HTTP, không nên khiến repository phụ thuộc vào loại <code>HttpStatus</code>. Với những lỗi hạ tầng không xác định, adapter thường trả lỗi tổng quát an toàn thay vì gửi nguyên văn ngoại lệ nội bộ.

Một số lựa chọn cần nhất quán trên toàn API:

| Quyết định | Câu hỏi kiểm tra |
| --- | --- |
| Trả giá trị rỗng hay ném lỗi khi không tìm thấy? | “Không có tài liệu” có phải kết quả thông thường của thao tác tra cứu này không? |
| Có loại lỗi riêng cho truy cập bị từ chối? | Người gọi có hành động riêng và có tín hiệu đủ chắc chắn để phân loại không? |
| Ngoại lệ nào được công bố cho client? | Có thể giữ ổn định hợp đồng khi thay hệ lưu trữ không? |
| Có phép thử lại không? | Thao tác có tính idempotent hay có thể đã thành công một phần dù nhận timeout? |

Một API tra cứu có thể dùng <code>Optional</code> khi “không tồn tại” là tình huống bình thường. Với API cam kết tài liệu phải tồn tại, một ngoại lệ not-found thường diễn đạt rõ ràng hơn. Cả hai là lựa chọn hợp đồng; không tự động chuyển mọi giá trị rỗng thành ngoại lệ.

## 9. Ngoại lệ không nên bị nuốt hoặc biến dạng

### 9.1. Không bắt bao trùm một cách tùy tiện

Mẫu dễ gây lỗi:

~~~java
try {
    doWork();
} catch (Exception e) {
    throw new RuntimeException("Thất bại", e);
}
~~~

Đây là ví dụ minh họa, **không phải chương trình độc lập**. Nó có thể gom cả lỗi validation, lỗi lập trình, lỗi nghiệp vụ đã dịch và các checked exception khác vào một loại chung. Người gọi không còn phân biệt được lỗi nào cần xử lý. Hãy bắt những loại thuộc đúng ranh giới chuyển đổi; nếu cần bắt rộng tại điểm đầu ứng dụng để ghi log hoặc chặn crash, đó là trách nhiệm khác.

Tuyệt đối không dùng <code>catch (Throwable)</code> như một cách dịch lỗi thông thường: <code>Error</code> có thể đại diện cho tình trạng nghiêm trọng của JVM và không thuộc tập lỗi nghiệp vụ có thể khôi phục.

### 9.2. Không biến lỗi thành kết quả thành công giả

~~~java
try {
    return store.read(name);
} catch (IOException e) {
    return null;
}
~~~

Nếu hợp đồng không định nghĩa <code>null</code> là trạng thái lỗi được xử lý rõ ràng, đoạn mã trên xóa mất sự khác biệt giữa “không có nội dung” và “hệ thống thất bại”. Người gọi có thể tiếp tục thao tác như thể việc đọc đã thành công.

### 9.3. Không bọc lặp khi chưa thay đổi ý nghĩa

~~~java
catch (DocumentAccessException e) {
    throw new RuntimeException("Không đọc được tài liệu", e);
}
~~~

Khi không thêm được ý nghĩa mới, wrapper chỉ tạo thêm tầng stack trace và làm người gọi mất loại lỗi hữu ích. Có thể cho ngoại lệ đi qua nguyên vẹn.

### 9.4. Xử lý InterruptedException có chủ đích

<code>InterruptedException</code> mang tín hiệu điều khiển luồng, không nên tự động coi như một lỗi hạ tầng thông thường. Với một đoạn mã đang chờ thao tác blocking:

~~~java
try {
    blockingOperation();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    throw new TaskCancelledException("Tác vụ bị ngắt", e);
}
~~~

Đây là **mã minh họa**, giả định có <code>blockingOperation()</code> và <code>TaskCancelledException</code> phù hợp. Khi phương thức blocking ném <code>InterruptedException</code>, trạng thái interrupt thường đã bị xóa; gọi <code>interrupt()</code> để khôi phục tín hiệu nếu không thể ném trực tiếp ngoại lệ checked đó lên trên. Trong API cho phép, chuyển tiếp <code>InterruptedException</code> trực tiếp cũng là một lựa chọn hợp lệ. Không được âm thầm nuốt ngắt rồi chạy tiếp như bình thường.

### 9.5. Không làm mất lỗi trong quá trình đóng tài nguyên

Với tài nguyên hỗ trợ <code>AutoCloseable</code>, ưu tiên <code>try-with-resources</code>. Nếu cả thao tác chính và việc đóng tài nguyên đều lỗi, Java giữ ngoại lệ thao tác chính là ngoại lệ chủ đạo và thường gắn lỗi đóng tài nguyên trong danh sách <code>getSuppressed()</code>. Khi dịch ngoại lệ, giữ cause gốc giúp còn có thể truy vết các suppressed exceptions. Tránh tự viết <code>finally</code> để đóng tài nguyên rồi vô tình thay thế ngoại lệ chính bằng lỗi đóng tài nguyên.

## 10. Cách kiểm thử exception translation

Các kiểm thử nên xác nhận **hợp đồng và nguyên nhân**, thay vì cố so khớp toàn bộ stack trace hoặc thông điệp phụ thuộc môi trường.

| Tình huống đầu vào | Kết quả cần kiểm tra |
| --- | --- |
| Cổng trả chuỗi văn bản | Giá trị trả về được giữ nguyên; không có ngoại lệ |
| Cổng ném <code>NoSuchFileException</code> | Ném đúng <code>DocumentNotFoundException</code> |
| Cổng ném <code>IOException</code> chung | Ném đúng <code>DocumentAccessException</code> |
| Lỗi đã dịch có cause | <code>getCause()</code> là **chính đối tượng** ngoại lệ gốc |
| Tên tài liệu null/rỗng | Có lỗi input phù hợp; không gọi kho lưu trữ |
| Một <code>RuntimeException</code> khác từ cổng | Không bị hai nhánh <code>catch IOException</code> thay đổi ngoài ý muốn |

Có thể kiểm tra trực tiếp cause bằng một đối tượng <code>IOException</code> được tạo trước. Đoạn mã **minh họa trong cùng phạm vi truy cập của bản demo**:

~~~java
IOException original = new IOException("I/O failure");
DocumentRepository repository = new DocumentRepository(name -> {
    throw original;
});

try {
    repository.read("intro.txt");
    throw new AssertionError("Phải ném DocumentAccessException");
} catch (DocumentAccessException translated) {
    if (translated.getCause() != original) {
        throw new AssertionError("Không giữ đúng cause gốc");
    }
}
~~~

Lệnh so sánh bằng <code>==</code> ở đây có ý nghĩa: kiểm tra **cùng một instance** được giữ làm cause, không chỉ kiểm tra cùng loại ngoại lệ. Trong một dự án dùng JUnit, có thể áp dụng <code>assertThrows</code> và <code>assertSame</code> để diễn đạt hai yêu cầu đó.

## 11. Những lỗi thiết kế phổ biến

1. **Lộ kiểu ngoại lệ hạ tầng** trong public API dù client chỉ làm việc với khái niệm nghiệp vụ: thay hệ lưu trữ khiến client phải sửa cách bắt lỗi.
2. **Tạo một ngoại lệ chung cho mọi tình huống**: mất khả năng phân biệt not-found, xung đột, thiếu quyền và lỗi phụ thuộc.
3. **Dịch dựa trên chuỗi message** thay vì loại ngoại lệ, mã trạng thái hoặc contract rõ ràng: dễ hỏng khi thay driver hay ngôn ngữ.
4. **Không truyền cause**: khó tìm được call site và nguyên nhân thực tế.
5. **Bắt rộng và wrap hai lần**: vô tình đổi loại lỗi đã phù hợp, làm phức tạp stack trace.
6. **Log cùng một lỗi ở mọi tầng rồi ném tiếp**: sinh log trùng lặp, khó xem một sự cố. Nên có chiến lược ghi log ở tầng thực sự xử lý hoặc tại điểm giám sát thích hợp.
7. **Dùng lỗi nghiệp vụ để che lỗi lập trình** như <code>NullPointerException</code> hay vi phạm bất biến mà không có chủ đích.
8. **Đổi API mà không duy trì hợp đồng ngoại lệ**: client đang bắt một loại lỗi có thể bị ảnh hưởng dù chữ ký phương thức với unchecked exception không thay đổi.

## 12. Checklist áp dụng vào dự án

Khi thiết kế hoặc review một phương thức qua ranh giới trừu tượng, hãy tự kiểm tra:

- Người gọi đang yêu cầu thao tác ở **mức khái niệm nào** (tài liệu, tài khoản, đơn hàng, tác vụ)?
- Những ngoại lệ nào là chi tiết hạ tầng; những ngoại lệ nào đã thuộc hợp đồng công khai?
- Có nhận diện được chắc chắn từng trường hợp cần xử lý riêng không?
- Có giữ được **cause**, đồng thời bảo vệ thông tin nhạy cảm khi tạo thông báo công khai?
- Đã bắt ngoại lệ cụ thể trước loại tổng quát, tránh bắt cả lỗi không liên quan?
- Checked/unchecked có phù hợp kỳ vọng xử lý lỗi của client và quy ước dự án?
- Có kiểm thử nhánh thành công, các nhánh lỗi được ánh xạ, và danh tính cause?
- Tầng phía trên có thể xử lý lỗi theo hợp đồng mà không phải biết thư viện lưu trữ cụ thể không?

Khi trả lời được các câu hỏi này, exception translation giúp **giữ ổn định API trước thay đổi công nghệ** và vẫn để lại đủ thông tin điều tra sự cố. Giá trị của kỹ thuật nằm ở ánh xạ đúng ngữ nghĩa, đúng ranh giới và bảo toàn nguyên nhân gốc.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-api-design-006">Giữ cause khi chuyển lỗi kỹ thuật thành lỗi có ý nghĩa với bên gọi</a>

<details>
<summary>Xem chi tiết</summary>

# Giữ cause khi chuyển lỗi kỹ thuật thành lỗi có ý nghĩa với bên gọi trong Java

## 1. Mục tiêu

Khi hoàn thành bài này, bạn có thể:

- Phân biệt **ngoại lệ được công bố cho bên gọi** với **ngoại lệ kỹ thuật gây ra sự cố**.
- Dùng constructor có tham số <code>Throwable cause</code> để chuyển lỗi mà không đánh mất dấu vết lỗi ban đầu.
- Giải thích cách <code>getCause()</code>, stack trace, <code>printStackTrace()</code> và chuỗi <code>Caused by</code> hỗ trợ điều tra lỗi.
- Phân biệt cause với suppressed exception, message và thông tin cần hiển thị cho người dùng.
- Kiểm tra rằng việc chuyển lỗi giữ **đúng đối tượng** cause, đồng thời tránh bọc lỗi dư thừa, che giấu lỗi hoặc tiết lộ chi tiết hạ tầng.

## 2. Bài toán: một lỗi có hai góc nhìn

Giả sử một API cung cấp thao tác <code>getDocument("guide")</code>. Bên gọi chỉ quan tâm liệu có đọc được tài liệu hay không. Việc tài liệu nằm trên hệ thống tệp, cơ sở dữ liệu hoặc dịch vụ từ xa là chi tiết cài đặt.

Trong quá trình đọc, tầng kỹ thuật có thể ném <code>java.nio.file.NoSuchFileException</code> hoặc <code>java.io.IOException</code>. Nếu API muốn cung cấp ngôn ngữ lỗi ổn định, nó có thể ném <code>DocumentNotFoundException</code> hoặc <code>DocumentReadException</code> cho bên gọi.

Vấn đề nằm ở chỗ: nếu chỉ tạo một ngoại lệ mới với thông báo “Không thể đọc tài liệu”, ta biết **thao tác nào thất bại** nhưng có thể mất **nguyên nhân kỹ thuật nào dẫn tới thất bại**. Vì vậy, một phép chuyển đổi tốt cần đồng thời giữ hai phần thông tin:

| Thành phần | Trả lời câu hỏi | Ví dụ |
| --- | --- | --- |
| Ngoại lệ bên ngoài (translated exception) | Thao tác ở tầng API thất bại như thế nào? | <code>DocumentReadException</code> |
| Message của ngoại lệ bên ngoài | Ngữ cảnh nào hữu ích cho bên gọi? | “Không thể đọc tài liệu: guide” |
| Cause | Điều gì trực tiếp khiến thao tác thất bại ở tầng dưới? | Đối tượng <code>IOException</code> ban đầu |
| Stack trace | Ngoại lệ được tạo/ném qua những vị trí thực thi nào? | Vị trí đọc dữ liệu và vị trí chuyển đổi ngoại lệ |

**Giữ cause** nghĩa là giữ tham chiếu tới đối tượng ngoại lệ đã gây ra lỗi và nối nó với ngoại lệ mới, thường bằng <code>super(message, cause)</code>. Cause không bị thay bằng chuỗi ký tự hoặc bằng một ngoại lệ mới cùng loại.

Mô hình:

~~~text
Bên gọi: DocumentGateway.get("guide")
                |
                v
       Lỗi kỹ thuật: IOException
                |
                | catch (IOException original)
                v
   DocumentReadException("Không thể đọc...", original)
                |
                +---- ngoại lệ bên ngoài: thông điệp theo hợp đồng API
                |
                +---- getCause(): chính đối tượng IOException gốc
~~~

Việc dịch loại lỗi và việc bảo toàn nguyên nhân là hai trách nhiệm có liên hệ nhưng khác nhau. Có tình huống nên giữ nguyên ngoại lệ mà không cần tạo wrapper mới; khi thật sự cần chuyển đổi, việc giữ cause giúp tránh mất thông tin.

## 3. Cause trong cơ chế ngoại lệ Java

Lớp gốc <code>java.lang.Throwable</code> cung cấp các cơ chế quan trọng:

| API | Ý nghĩa |
| --- | --- |
| <code>getMessage()</code> | Thông báo chi tiết của chính ngoại lệ đang được xét; có thể là <code>null</code> |
| <code>getCause()</code> | Ngoại lệ được liên kết là nguyên nhân của ngoại lệ hiện tại; có thể là <code>null</code> |
| <code>printStackTrace()</code> | In stack trace cùng chuỗi cause/suppressed khi có, phục vụ chẩn đoán |
| <code>getStackTrace()</code> | Lấy stack trace của chính <code>Throwable</code> dưới dạng mảng phần tử |
| <code>getSuppressed()</code> | Lấy các ngoại lệ bị giữ lại dạng *suppressed*, không phải cause chính |

Một đối tượng <code>Throwable</code> chỉ có tối đa **một cause trực tiếp**, nhưng cause cũng có thể có cause riêng, tạo thành một chuỗi. Ví dụ:

~~~text
DocumentExportException
    caused by -> DocumentReadException
        caused by -> IOException
~~~

Ở đây:

- <code>exportFailure.getCause()</code> trả về <code>DocumentReadException</code>.
- <code>exportFailure.getCause().getCause()</code> trả về <code>IOException</code>.
- <code>exportFailure.getCause()</code> **không** trực tiếp trả về ngoại lệ sâu nhất.
- Chuỗi cause chỉ có giá trị khi mỗi lần chuyển đổi thể hiện thêm ý nghĩa cần thiết; không nên tạo thêm tầng chỉ để có nhiều tầng.

**Cause khác với message.** Message là văn bản có thể thay đổi theo ngữ cảnh hoặc phiên bản triển khai. Cause là tham chiếu tới một đối tượng chứa loại lỗi, thông điệp, stack trace và các thông tin liên quan. Việc sao chép <code>original.getMessage()</code> vào message mới không đồng nghĩa với giữ nguyên cause.

## 4. Constructor có cause: cách nên dùng

Khi định nghĩa một loại unchecked exception ở tầng API, constructor đơn giản nhất cho việc chuyển đổi lỗi là:

~~~java
public final class DocumentReadException extends RuntimeException {
    public DocumentReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
~~~

Đây là **một lớp độc lập** nếu đặt trong tệp <code>DocumentReadException.java</code>. Constructor của <code>RuntimeException</code> tiếp nhận cả message và cause, nên:

- <code>getMessage()</code> của ngoại lệ mới trả về message dành cho API.
- <code>getCause()</code> của ngoại lệ mới trả về đối tượng đã truyền vào.
- Stack trace của ngoại lệ mới phản ánh vị trí tạo/ném wrapper, trong khi cause giữ stack trace của chính nó.

Nếu API dùng checked exception, cách giữ cause tương tự, chỉ cần kế thừa <code>Exception</code> và khai báo/bắt checked exception theo quy định Java:

~~~java
public final class DocumentReadCheckedException extends Exception {
    public DocumentReadCheckedException(String message, Throwable cause) {
        super(message, cause);
    }
}
~~~

Đây cũng là một lớp độc lập nếu lưu tại <code>DocumentReadCheckedException.java</code>. Việc chọn checked hay unchecked là lựa chọn hợp đồng API, không làm thay đổi nguyên tắc giữ cause.

### Ba cách viết và hệ quả

Các đoạn sau là **mã minh họa đặt trong một khối <code>catch (IOException original)</code>** với exception class phù hợp.

**Đúng — message ở tầng API, cause là lỗi gốc:**

~~~java
throw new DocumentReadException(
        "Không thể đọc tài liệu đã yêu cầu", original);
~~~

**Sai — chỉ giữ message, bỏ mất cause:**

~~~java
throw new DocumentReadException(
        "Không thể đọc tài liệu: " + original.getMessage(), null);
~~~

**Sai về mặt thiết kế — tạo một lỗi kỹ thuật mới thay cho lỗi kỹ thuật gốc:**

~~~java
throw new DocumentReadException(
        "Không thể đọc tài liệu",
        new IOException(original.getMessage()));
~~~

Cách thứ ba có thể tạo ra một <code>IOException</code> mới không có cùng stack trace và danh tính với lỗi gốc. Nếu không liên kết <code>original</code> là cause của ngoại lệ trung gian thì mất đường truy vết tới chính sự cố đã xảy ra. Cách đúng thường là truyền thẳng <code>original</code> làm cause.

Đừng sử dụng text của lỗi gốc để giả lập type của ngoại lệ hoặc đặt thông tin nhạy cảm vào thông báo công khai. Message của ngoại lệ mới nên mô tả hành động thất bại theo hợp đồng; cause phục vụ chẩn đoán kỹ thuật.

## 5. Ví dụ Java 17+ độc lập: đọc tài liệu và giữ đúng cause

Tạo tệp **CausePreservationDemo.java** với toàn bộ nội dung sau. Ví dụ dùng một nguồn dữ liệu giả lập để kết quả xác định và không phụ thuộc tệp thực tế trên máy.

~~~java
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Objects;

public class CausePreservationDemo {

    @FunctionalInterface
    interface RawDocumentSource {
        String read(String id) throws IOException;
    }

    static final class DocumentNotFoundException extends RuntimeException {
        DocumentNotFoundException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static final class DocumentReadException extends RuntimeException {
        DocumentReadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static final class DocumentGateway {
        private final RawDocumentSource source;

        DocumentGateway(RawDocumentSource source) {
            this.source = Objects.requireNonNull(source);
        }

        String getDocument(String id) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("ID tài liệu không hợp lệ");
            }

            try {
                return source.read(id);
            } catch (NoSuchFileException original) {
                throw new DocumentNotFoundException(
                        "Không tìm thấy tài liệu: " + id, original);
            } catch (IOException original) {
                throw new DocumentReadException(
                        "Không thể đọc tài liệu: " + id, original);
            }
        }
    }

    public static void main(String[] args) {
        DocumentGateway success =
                new DocumentGateway(id -> "Nội dung hướng dẫn");
        System.out.println("Thành công: " + success.getDocument("guide"));

        NoSuchFileException missing = new NoSuchFileException("guide");
        DocumentGateway absent = new DocumentGateway(id -> {
            throw missing;
        });

        try {
            absent.getDocument("guide");
        } catch (DocumentNotFoundException translated) {
            System.out.println(
                    "Không tìm thấy: " + translated.getClass().getSimpleName()
                    + " <- " + translated.getCause().getClass().getSimpleName());
            System.out.println(
                    "Giữ đúng instance cause: " + (translated.getCause() == missing));
        }

        IOException diskError = new IOException("Storage failure");
        DocumentGateway failed = new DocumentGateway(id -> {
            throw diskError;
        });

        try {
            failed.getDocument("guide");
        } catch (DocumentReadException translated) {
            System.out.println(
                    "Không đọc được: " + translated.getClass().getSimpleName()
                    + " <- " + translated.getCause().getClass().getSimpleName());
            System.out.println(
                    "Giữ đúng instance cause: " + (translated.getCause() == diskError));
        }
    }
}
~~~

Biên dịch và chạy bằng Java 17 hoặc mới hơn:

~~~bash
javac -encoding UTF-8 CausePreservationDemo.java
java CausePreservationDemo
~~~

**Stdout chính xác:**

~~~text
Thành công: Nội dung hướng dẫn
Không tìm thấy: DocumentNotFoundException <- NoSuchFileException
Giữ đúng instance cause: true
Không đọc được: DocumentReadException <- IOException
Giữ đúng instance cause: true
~~~

### Diễn giải cơ chế

1. <code>RawDocumentSource</code> đại diện cho tầng cung cấp dữ liệu thô. Nó được phép ném <code>IOException</code>, giống như nhiều API I/O của Java.
2. <code>DocumentGateway</code> là ranh giới API dành cho bên gọi. Nó không bắt người gọi phải hiểu chi tiết lưu trữ.
3. <code>NoSuchFileException</code> được bắt trước <code>IOException</code> vì nó là lớp con của <code>IOException</code>. Thứ tự ngược lại khiến khối <code>catch</code> chuyên biệt không thể tới được.
4. Từng <code>catch</code> tạo ngoại lệ mang ý nghĩa API và **truyền cùng đối tượng** <code>original</code> vào constructor.
5. Phép so sánh <code>translated.getCause() == missing</code> và <code>== diskError</code> trả về <code>true</code>. Đây là bằng chứng mạnh hơn việc chỉ kiểm tra cùng class hoặc cùng message.
6. Trường hợp thành công không sinh wrapper. Việc chuyển lỗi chỉ xảy ra khi nguồn thực sự ném ngoại lệ phù hợp.
7. Kiểm tra ID trống được thực hiện trước truy cập tầng dưới; lỗi yêu cầu không hợp lệ không bị ngụy trang thành lỗi I/O.

Ví dụ trên dùng kiểu unchecked exception của API. Nếu hợp đồng muốn dùng checked exception, có thể dùng lớp kế thừa <code>Exception</code> và khai báo <code>throws</code> tương ứng; **cách nối cause vẫn giữ nguyên**.

Tại một triển khai thực tế, <code>RawDocumentSource</code> có thể gọi <code>Files.readString(...)</code>, JDBC hoặc một adapter khác. Tuy nhiên, không phải mọi lỗi kỹ thuật đều nên quy về “không tìm thấy”: ví dụ <code>IOException</code> chung được dịch thành “không thể đọc” để tránh khẳng định sai nguyên nhân.

## 6. Đọc chuỗi cause khi điều tra lỗi

Khi một exception wrapper được in bằng <code>printStackTrace()</code> hoặc được logger ghi kèm đối tượng <code>Throwable</code>, đầu ra thường có đoạn kiểu sau:

~~~text
DocumentReadException: Không thể đọc tài liệu: guide
    at ...DocumentGateway.getDocument(...)
Caused by: java.io.IOException: Storage failure
    at ... vị trí sinh lỗi gốc ...
~~~

Đây là **minh họa cấu trúc stack trace**, không phải stdout chính xác của chương trình phía trên. Tên package, đường dẫn tệp, số dòng, stack trace và định dạng có thể khác nhau tùy cách chạy.

Điều cần hiểu:

- Phần trước <code>Caused by</code> mô tả ngoại lệ của tầng API cùng vị trí nó được ném.
- Phần <code>Caused by</code> chứa loại ngoại lệ và stack trace được lưu trên đối tượng kỹ thuật ban đầu.
- Nếu có nhiều tầng cause, các mục <code>Caused by</code> được hiển thị tiếp theo thứ tự liên kết.
- Khi in stack trace, Java có thể rút gọn các frame chung bằng dòng dạng <code>... N more</code>. Đó là tối ưu hiển thị, không có nghĩa cause đã bị mất.

Không nên dựng lại chuỗi lỗi bằng cách nối <code>getMessage()</code> của từng tầng rồi coi đó là bằng chứng hoàn chỉnh. Chuỗi ký tự không lưu được danh tính exception, cấu trúc cause hay đầy đủ stack trace.

### Khi truy cập cause bằng code

~~~java
Throwable cause = translated.getCause();
if (cause != null) {
    System.out.println("Cause type: " + cause.getClass().getName());
    System.out.println("Cause message: " + cause.getMessage());
}
~~~

Đây là **đoạn minh họa** giả định biến <code>translated</code> đang chứa một ngoại lệ. Việc kiểm tra <code>null</code> cần thiết vì không phải ngoại lệ nào cũng có cause. Với code sản phẩm, cân nhắc không đưa message kỹ thuật ra giao diện người dùng.

Một số thư viện cung cấp tiện ích phân tích “root cause”, nhưng <code>Throwable</code> chỉ quy định liên kết cause trực tiếp. Muốn truy sâu, phải duyệt từng cấp theo nhu cầu; tránh giả định mọi ngoại lệ có cùng số tầng hoặc cause luôn khác <code>null</code>.

## 7. Giữ cause qua nhiều ranh giới: khi nào thật sự cần thêm wrapper?

Giả sử tầng lưu trữ đã ném <code>DocumentReadException</code>. Tầng dùng nó để **xuất báo cáo** có thể muốn biểu diễn một hành động lớn hơn, chẳng hạn <code>ReportExportException</code>, vì người gọi tác vụ xuất báo cáo cần hiểu thất bại ở mức báo cáo.

Một sơ đồ hợp lý có thể là:

~~~text
IOException
    |
    | giữ cause khi vào DocumentGateway
    v
DocumentReadException
    |
    | chỉ chuyển tiếp hoặc bọc thêm nếu hợp đồng xuất báo cáo
    | cần diễn đạt một ngữ nghĩa mới
    v
ReportExportException
~~~

Nếu muốn bọc thêm ở một tầng mới, constructor vẫn nhận cause:

~~~java
public final class ReportExportException extends RuntimeException {
    public ReportExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
~~~

Trong phạm vi một phương thức nghiệp vụ có đối tượng <code>gateway</code> tương ứng, có thể minh họa như sau:

~~~java
try {
    String document = gateway.getDocument("guide");
    exportReport(document);
} catch (DocumentReadException original) {
    throw new ReportExportException("Không thể xuất báo cáo", original);
}
~~~

**Đoạn <code>try/catch</code> này chỉ minh họa**, giả định <code>gateway</code> và <code>exportReport</code> đã được khai báo trong ứng dụng. Nếu không có ngữ nghĩa xuất báo cáo cần thêm, nên cho <code>DocumentReadException</code> đi qua, tránh wrapper thừa.

Một chuỗi cause gồm ba đối tượng là hợp lý nếu nó mô tả ba trách nhiệm thực sự khác nhau. Nếu tầng nào cũng bọc bằng một <code>RuntimeException("Lỗi", e)</code> chung, người đọc sẽ phải tháo nhiều lớp vô nghĩa để tìm ra lỗi thực.

### Khi nào không nên wrap?

- Ngoại lệ đã biểu đạt đúng hợp đồng của tầng hiện tại; có thể ném tiếp bằng <code>throw e;</code> hoặc để lan truyền.
- Tầng hiện tại chỉ ghi log rồi chuyển lỗi lên; việc tạo exception mới chỉ để log thường không tăng ích lợi.
- Không hiểu được nguyên nhân kỹ thuật để phân loại một cách đáng tin cậy; khi cần, dùng loại lỗi API tổng quát thích hợp với cause gốc.
- Ngoại lệ là lỗi lập trình hoặc lỗi nghiêm trọng không nằm trong hợp đồng xử lý của thao tác.

### Khi nào xử lý thay vì wrap?

Nếu ứng dụng có nguồn dự phòng và có thể hoàn thành thao tác đúng hợp đồng, đó là **phục hồi lỗi**, có thể không cần ném wrapper. Ngược lại, trả <code>null</code>, chuỗi rỗng hoặc dữ liệu giả chỉ để làm ngoại lệ biến mất sẽ khiến bên gọi hiểu sai kết quả nếu hợp đồng không quy định cách đó.

## 8. Cause, suppressed exception và lỗi khi đóng tài nguyên

Cause và suppressed exception giải quyết hai vấn đề khác nhau:

- **Cause**: lỗi trực tiếp gây ra ngoại lệ hiện tại, thường là lỗi được dịch từ tầng dưới.
- **Suppressed**: các ngoại lệ phát sinh thêm trong quá trình xử lý lỗi hoặc đóng tài nguyên, được giữ để không che mất lỗi chính.

Với <code>try-with-resources</code>, nếu cả thao tác trong <code>try</code> và thao tác <code>close()</code> đều ném ngoại lệ, lỗi trong thân <code>try</code> thường là lỗi chính; lỗi từ <code>close()</code> được thêm vào <code>getSuppressed()</code> của lỗi chính.

Ví dụ Java 17 độc lập, lưu thành **SuppressedCauseDemo.java**:

~~~java
import java.io.IOException;

public class SuppressedCauseDemo {

    static final class FailingResource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            throw new IOException("Lỗi khi đóng");
        }
    }

    static final class ReadOperationException extends RuntimeException {
        ReadOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static void read() {
        try (FailingResource resource = new FailingResource()) {
            throw new IOException("Lỗi khi đọc");
        } catch (IOException original) {
            throw new ReadOperationException("Đọc tài nguyên thất bại", original);
        }
    }

    public static void main(String[] args) {
        try {
            read();
        } catch (ReadOperationException translated) {
            Throwable cause = translated.getCause();
            System.out.println("Cause: " + cause.getMessage());
            System.out.println("Suppressed: "
                    + cause.getSuppressed()[0].getMessage());
            System.out.println("Số suppressed: "
                    + cause.getSuppressed().length);
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 SuppressedCauseDemo.java
java SuppressedCauseDemo
~~~

**Stdout chính xác:**

~~~text
Cause: Lỗi khi đọc
Suppressed: Lỗi khi đóng
Số suppressed: 1
~~~

Ở đây wrapper giữ nguyên đối tượng <code>IOException("Lỗi khi đọc")</code> làm cause. Đối tượng cause đó đã giữ ngoại lệ <code>IOException("Lỗi khi đóng")</code> trong danh sách suppressed. Nếu tạo một <code>IOException</code> mới từ message rồi bỏ bản gốc, cả bối cảnh đọc lẫn lỗi đóng tài nguyên có nguy cơ bị mất.

Không được giả định mọi <code>IOException</code> đều có suppressed exception; chương trình ví dụ cố ý làm lỗi cả hai thao tác. Trong sản phẩm, phải kiểm tra độ dài mảng <code>getSuppressed()</code> trước khi truy cập phần tử. Khi chỉ cần ghi log chẩn đoán, việc truyền toàn bộ ngoại lệ cho logger thường tốt hơn viết mã thủ công để ghép từng suppressed error.

## 9. Không dùng initCause như lựa chọn mặc định

Ngoài constructor <code>Throwable(String message, Throwable cause)</code>, Java còn cung cấp <code>initCause(Throwable cause)</code>. API này phục vụ tình huống cần liên kết cause **sau khi** tạo ngoại lệ, chẳng hạn khi lớp ngoại lệ cũ không có constructor thích hợp.

Ví dụ minh họa trong ngữ cảnh phù hợp:

~~~java
RuntimeException translated =
        new RuntimeException("Không thể xử lý yêu cầu");
translated.initCause(original);
throw translated;
~~~

Trong đoạn trên, <code>original</code> là một đối tượng <code>Throwable</code> đã có trước. Không gọi <code>initCause</code> nhiều lần và không đặt chính ngoại lệ đó làm cause của nó. Nếu cause đã được khởi tạo bằng constructor (kể cả constructor có tham số cause là <code>null</code>), gọi <code>initCause</code> tiếp sẽ phát sinh <code>IllegalStateException</code>; việc tự gán chính nó làm cause cũng không hợp lệ. 

Đối với lớp ngoại lệ do mình sở hữu, constructor nhận <code>Throwable cause</code> thường rõ ràng và ít sai sót hơn. <code>initCause</code> phù hợp hơn với trường hợp tương thích API cũ, không nên dùng để sửa chữa một chuỗi cause đã tạo sai.

## 10. Chẩn đoán nội bộ và thông báo an toàn cho bên gọi

Giữ cause **không có nghĩa công khai mọi thông tin trong cause**. Ngoại lệ kỹ thuật có thể chứa đường dẫn máy chủ, tên bảng dữ liệu, thông tin truy vấn, hostname, địa chỉ dịch vụ hoặc dữ liệu nhạy cảm.

Nên tách:

1. **Hợp đồng cho client**: loại lỗi nghiệp vụ/kỹ thuật ở mức API, mã lỗi ổn định nếu cần, message không tiết lộ bí mật.
2. **Chẩn đoán nội bộ**: full exception object, cause chain, suppressed exceptions, thông tin tương quan như request ID, được lưu ở nơi có kiểm soát truy cập.
3. **Hành vi xử lý**: client có thể quyết định thông báo người dùng, yêu cầu nhập lại, chuyển hướng hoặc thử lại khi thao tác thật sự cho phép.

Ví dụ, API bên ngoài có thể chỉ phản hồi “Không thể tải tài liệu lúc này”, trong khi log nội bộ vẫn truy tới <code>DocumentReadException</code> và <code>IOException</code>. Việc ánh xạ ngoại lệ sang một HTTP status hay mã gRPC phụ thuộc vào ranh giới giao tiếp, không thuộc trách nhiệm của lớp I/O.

### Chiến lược ghi log

Khi cần ghi stack trace bằng thư viện logging có hỗ trợ đối tượng <code>Throwable</code>, hãy truyền exception vào tham số phù hợp của logger, thay vì chỉ nối <code>e.getMessage()</code>. Tuy nhiên, không nên ghi cùng một ngoại lệ ở mọi tầng rồi ném lại, vì có thể tạo nhiều bản log giống nhau cho một yêu cầu.

Nếu lỗi chưa được xử lý ở tầng hiện tại, có thể giữ cause và để lỗi lan truyền tới ranh giới giám sát thích hợp. Nếu tầng hiện tại đã thực sự khôi phục lỗi, log ở mức phù hợp với sự cố đã được xử lý. Chính sách cụ thể phụ thuộc ứng dụng, nhưng việc giữ cause khiến lựa chọn quan sát ở tầng sau vẫn khả thi.

**Tránh** dùng message của ngoại lệ thấp làm lời giải thích công khai bắt buộc. Message của driver hoặc thư viện không phải hợp đồng ổn định, và có thể lộ thông tin hoặc thay đổi giữa các phiên bản.

## 11. Những trường hợp đặc biệt cần phân biệt

### 11.1. Không bắt mọi Exception rồi dịch thành một lỗi duy nhất

~~~java
try {
    executeOperation();
} catch (Exception e) {
    throw new DocumentReadException("Lỗi đọc tài liệu", e);
}
~~~

Đây là **mã minh họa**, không phải chương trình độc lập. Nó giữ cause, nhưng có thể vẫn **dịch sai ngữ nghĩa**: lỗi validate, lỗi lập trình, lỗi nghiệp vụ đã được dịch hoặc <code>InterruptedException</code> đều có nguy cơ bị gộp vào “lỗi đọc”. Giữ cause là điều kiện quan trọng nhưng **chưa đủ** cho một exception translation đúng.

Nên bắt các loại ngoại lệ thuộc phạm vi chuyển đổi của API, phân loại theo hợp đồng, và tránh bắt <code>Throwable</code> như một lỗi nghiệp vụ thông thường. <code>Error</code> và các lỗi nghiêm trọng của môi trường chạy không nên bị mặc định biến thành lỗi có thể phục hồi.

### 11.2. InterruptedException cần bảo toàn tín hiệu ngắt

Nếu một thao tác blocking ném <code>InterruptedException</code> và API buộc phải chuyển nó thành unchecked exception, ứng dụng thường cần khôi phục trạng thái ngắt trước khi ném wrapper:

~~~java
try {
    blockingCall();
} catch (InterruptedException original) {
    Thread.currentThread().interrupt();
    throw new OperationInterruptedException(
            "Thao tác bị ngắt", original);
}
~~~

Đây là **mã minh họa**: <code>blockingCall()</code> và <code>OperationInterruptedException</code> là những thành phần được giả định có trong ngữ cảnh ứng dụng. Nếu hợp đồng cho phép, khai báo và chuyển tiếp <code>InterruptedException</code> trực tiếp là một lựa chọn tốt khác.

Việc chỉ lưu <code>original</code> làm cause **không tự khôi phục interrupt flag** cho luồng. Hai việc này độc lập: cause giữ bằng chứng, còn <code>Thread.currentThread().interrupt()</code> khôi phục tín hiệu điều khiển hợp tác để code phía trên có thể nhận biết.

### 11.3. Không tự động biến timeout thành “chưa xử lý”

Một lời gọi tới dịch vụ ngoài timeout có thể đã được máy chủ xử lý trước khi kết nối đứt. Wrapper có cause chỉ cho biết ngoại lệ kỹ thuật gốc, không tự chứng minh thao tác chưa thành công. Nếu cho client thử lại, cần dựa vào đặc tính idempotent, idempotency key hoặc hợp đồng riêng của thao tác.

### 11.4. Không sửa trực tiếp stack trace để che cause

Không nên gọi <code>setStackTrace(...)</code> hay tạo đối tượng lỗi mới từ text chỉ nhằm bỏ dấu vết gốc. Trong những tình huống bảo mật, cần kiểm soát **nơi hiển thị/log lỗi**, đồng thời vẫn bảo toàn thông tin nội bộ theo chính sách lưu trữ thích hợp. Chỉ cân nhắc loại bỏ thông tin khi đó là chủ đích rõ ràng của ranh giới tin cậy.

## 12. Kiểm thử: xác nhận danh tính cause và hợp đồng bên ngoài

Một bộ kiểm thử tốt không chỉ hỏi “có ném exception không?” mà còn hỏi ngoại lệ có **đúng tầng nghĩa**, và cause có **đúng đối tượng ban đầu** hay không.

| Tình huống | Điều cần khẳng định |
| --- | --- |
| Nguồn dữ liệu trả nội dung bình thường | Không ném ngoại lệ; nội dung không thay đổi |
| Nguồn ném <code>NoSuchFileException</code> | Bên gọi nhận <code>DocumentNotFoundException</code> |
| Nguồn ném <code>IOException</code> khác | Bên gọi nhận <code>DocumentReadException</code> |
| Ngoại lệ đã được dịch | <code>translated.getCause() == original</code> |
| Bên gọi nhận lỗi được công bố | Không cần phụ thuộc trực tiếp vào loại exception nội bộ của adapter để ra quyết định |
| Nguồn sinh lỗi bất ngờ ngoài <code>IOException</code> | Không bị các nhánh <code>catch (IOException)</code> đổi loại vô cớ |
| Thao tác đóng tài nguyên cũng thất bại | Suppressed exception còn được truy cập qua cause nếu nguyên nhân ban đầu được giữ |
| Input sai | Được từ chối ở ranh giới input theo hợp đồng, không bị giả dạng thành lỗi hạ tầng |

Trong demo chạy độc lập ở mục 5, hai phép so sánh <code>==</code> đã kiểm tra chính xác object identity. Với JUnit 5, có thể viết kiểm thử bằng <code>assertThrows</code> và <code>assertSame</code>.

Đoạn mã bên dưới **minh họa trong một dự án JUnit 5 đã có dependency, các lớp <code>DocumentGateway</code> và <code>DocumentReadException</code> ở phạm vi truy cập phù hợp**:

~~~java
IOException original = new IOException("disk error");
DocumentGateway gateway = new DocumentGateway(id -> {
    throw original;
});

DocumentReadException actual = assertThrows(
        DocumentReadException.class,
        () -> gateway.getDocument("guide"));

assertSame(original, actual.getCause());
~~~

Khi áp dụng trong tệp test thực tế, cần có import <code>java.io.IOException</code> và các static import từ <code>org.junit.jupiter.api.Assertions</code>. Đoạn minh họa trên không phải một chương trình Java độc lập.

Điểm mấu chốt của <code>assertSame</code>: hai exception có thể cùng class, cùng message nhưng là **hai đối tượng khác nhau**. Chỉ kiểm tra <code>getCause() instanceof IOException</code> sẽ không phát hiện trường hợp ai đó tạo <code>new IOException(original.getMessage())</code> rồi đánh mất exception gốc.

Đối với message của API, nên kiểm tra phần thông tin hợp đồng ổn định. Đừng buộc kiểm thử phụ thuộc từng câu message của hệ điều hành hoặc từng dòng stack trace, vì chúng thay đổi giữa môi trường.

## 13. Lỗi phổ biến khi giữ cause

1. **Chỉ copy message:** <code>new HighLevelException(original.getMessage())</code> không ghi lại đối tượng nguyên nhân.
2. **Truyền <code>null</code> vô tình:** constructor có cause nhưng thực tế không nhận được lỗi gốc.
3. **Tạo cause “tương tự”:** thay <code>original</code> bằng <code>new IOException(original.getMessage())</code> làm mất danh tính, stack trace và có thể cả suppressed exceptions.
4. **Chỉ giữ cause mà dịch sai loại lỗi:** ví dụ mọi ngoại lệ đều thành <code>NotFoundException</code>. Cause không cứu được hợp đồng sai.
5. **Bọc lặp nhiều tầng không thêm nghĩa:** stack trace khó đọc, client phải lần qua wrapper không cần thiết.
6. **Tưởng message là stack trace:** message chỉ là một chuỗi, còn full exception mang nhiều thông tin hơn.
7. **Quên kiểm tra <code>getCause() == null</code>:** nhiều ngoại lệ được ném trực tiếp không có cause.
8. **Nhầm suppressed exception với cause:** lỗi đóng tài nguyên có thể được lưu trong <code>getSuppressed()</code> của cause.
9. **Bắt quá rộng:** có thể che <code>InterruptedException</code>, lỗi validation hoặc các lỗi đã được dịch đúng.
10. **Lộ chi tiết kỹ thuật cho người dùng:** giữ cause nội bộ nhưng không tự động gửi nguyên stack trace hoặc message nhạy cảm ra API response.
11. **Ghi log lặp lại mọi lần catch:** cùng một sự cố xuất hiện nhiều lần trong log mà không cung cấp thêm bối cảnh.
12. **Kiểm thử chỉ nhìn loại ngoại lệ bên ngoài:** không phát hiện hồi quy làm mất nguyên nhân gốc.

## 14. Cách review một phép chuyển đổi lỗi

Hãy đọc một khối <code>catch</code> theo trình tự:

1. **Có cần dịch không?** Tầng này có cung cấp một hợp đồng khác tầng phát sinh lỗi không?
2. **Ánh xạ đúng không?** Loại exception mới có diễn đạt đúng điều có thể biết chắc từ lỗi thấp hơn không?
3. **Giữ gốc chưa?** Constructor có nhận **chính biến exception đã bắt** làm cause không?
4. **Message có phù hợp không?** Có giải thích hành động thất bại ở mức API mà không tiết lộ dữ liệu nhạy cảm không?
5. **Có thông tin phụ nào cần giữ không?** Cause có mang stack trace, suppressed exceptions và bối cảnh lỗi của thư viện không?
6. **Kiểm thử đã chứng minh chưa?** Có ít nhất một trường hợp khẳng định <code>assertSame(original, translated.getCause())</code> hoặc tương đương không?
7. **Tầng tiếp theo nên làm gì?** Cho ngoại lệ đi qua, chuyển sang hợp đồng nghiệp vụ khác, khôi phục lỗi hay ánh xạ sang kết quả giao tiếp?
8. **Có xử lý luồng và tài nguyên đúng không?** Đặc biệt với interrupt, cleanup và thao tác có thể đã thành công một phần.

**Điều cần nhớ:** Ngoại lệ bên ngoài giúp bên gọi hiểu **ý nghĩa của thất bại**; cause giữ lại **bằng chứng về thất bại đã xảy ra**. Kết hợp đúng hai thông tin này cho phép API che giấu chi tiết triển khai mà vẫn giữ được khả năng chẩn đoán chính xác khi sự cố phát sinh.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-api-design-007">Tài liệu hóa bằng @throws và điều kiện phát sinh</a>

<details>
<summary>Xem chi tiết</summary>

# Tài liệu hóa bằng @throws và điều kiện phát sinh

Một phương thức Java có thể thất bại theo nhiều cách: đầu vào sai, trạng thái đối tượng không cho phép thao tác, quy tắc nghiệp vụ bị vi phạm hoặc tài nguyên bên ngoài không khả dụng. Người sử dụng API cần biết **exception nào có ý nghĩa với họ, chính xác điều kiện nào gây ra exception và họ có thể làm gì tiếp theo**. Thẻ Javadoc `@throws` giúp mô tả hợp đồng này ngay cạnh chữ ký phương thức hoặc constructor.

Tài liệu tốt không chỉ liệt kê tên lớp exception. Nó liên kết **điều kiện phát sinh → loại lỗi → cách hiểu của caller**, đồng thời nhất quán với implementation và test. Đây là một phần thiết kế API ổn định, đặc biệt quan trọng với thư viện và public service dùng lại ở nhiều nơi.

## 1. Phân biệt ba khái niệm dễ nhầm

Trong Java, từ `throw`, `throws` và `@throws` xuất hiện gần nhau nhưng có ba vai trò khác nhau.

| Cú pháp | Vị trí | Vai trò |
| --- | --- | --- |
| `throw new SomeException(...)` | Thân phương thức hoặc constructor | **Ném** một exception ở thời điểm thực thi. |
| `throws SomeException` | Chữ ký phương thức hoặc constructor | **Khai báo** loại exception được phép truyền ra. Với checked exception, đây là một phần cơ chế kiểm tra ở thời điểm biên dịch. |
| `@throws SomeException ...` | Khối Javadoc `/** ... */` | **Mô tả** trường hợp ném exception cho người đọc tài liệu API; không thay đổi việc biên dịch hoặc tự tạo hành vi ném lỗi. |

Ví dụ minh họa cú pháp, chưa phải chương trình độc lập:

```java
/**
 * Tìm số phần tử trong trang.
 *
 * @param pageSize số phần tử yêu cầu
 * @return số phần tử được chấp nhận
 * @throws IllegalArgumentException nếu pageSize không dương
 */
public int validatePageSize(int pageSize) {
    if (pageSize <= 0) {
        throw new IllegalArgumentException("pageSize phải dương");
    }
    return pageSize;
}
```

`IllegalArgumentException` kế thừa `RuntimeException` nên thuộc loại **unchecked**. Phương thức không cần khai báo `throws IllegalArgumentException` để biên dịch, nhưng vẫn có lý do rõ ràng để viết `@throws` vì người gọi cần biết ràng buộc đầu vào.

Ngược lại, với checked exception như `IOException` (không phải subtype của `RuntimeException`), phương thức ném hoặc để exception đó truyền ra thường phải khai báo `throws IOException` hoặc xử lý bằng `catch` theo quy tắc compile-time. **Thẻ `@throws` không thay thế được khai báo `throws`**.

## 2. Cú pháp và ý nghĩa của @throws

Một thẻ Javadoc chuẩn có dạng:

```java
/**
 * ...
 * @throws ExceptionType điều kiện chính xác làm exception phát sinh
 */
```

`ExceptionType` là tên lớp exception mà Javadoc có thể phân giải từ ngữ cảnh import/package; nên sử dụng kiểu mà API thực sự công khai. Phần mô tả đi sau cần chỉ ra **tình huống quan sát được**, thay vì chỉ lặp lại “when an exception occurs”.

Cùng một phương thức có thể có nhiều thẻ `@throws` cho các loại lỗi khác nhau:

```java
/**
 * @throws IllegalArgumentException nếu mã đơn hàng không hợp lệ
 * @throws IllegalStateException nếu phiên làm việc đã đóng
 * @throws OrderNotFoundException nếu không tìm thấy đơn hàng
 */
```

Đây là đoạn Javadoc minh họa, với `OrderNotFoundException` giả định là kiểu exception của ứng dụng; chưa phải ví dụ Java đầy đủ.

`@exception` là bí danh được công cụ Javadoc chấp nhận cho `@throws`. Trong code mới, dùng nhất quán `@throws` giúp người đọc dễ nhận diện hơn.

Đối với toán tử `<` hoặc `>` trong văn bản Javadoc, đặt biểu thức trong `{@code ...}` để công cụ sinh tài liệu hiển thị đúng ký tự đặc biệt. Ví dụ:

```java
/**
 * @param amount lượng yêu cầu
 * @throws IllegalArgumentException nếu {@code amount <= 0}
 */
```

Nếu cần trỏ đến một kiểu Java trong câu văn, có thể dùng `{@link IllegalArgumentException}`. Javadoc phân tích các thẻ inline này, tạo phần trình bày code/link thích hợp trong HTML. Khối Markdown đang đọc là nội dung giảng dạy; những thẻ `{@code}` và `{@link}` có ý nghĩa đặc biệt **bên trong comment Javadoc của mã Java**.

## 3. “Điều kiện phát sinh” phải cụ thể đến đâu?

Mô tả tốt trả lời các câu hỏi: **đầu vào hoặc trạng thái nào làm lỗi xảy ra, lỗi có thuộc trách nhiệm caller hay hạ tầng, và ngoại lệ đó được bảo đảm trong hợp đồng không?**

So sánh:

| Mô tả yếu | Mô tả hữu ích |
| --- | --- |
| “Ném `IllegalArgumentException` khi đối số sai.” | “Ném `IllegalArgumentException` nếu `quantity <= 0`.” |
| “Ném `IllegalStateException` khi trạng thái không đúng.” | “Ném `IllegalStateException` nếu gọi `send()` sau `close()`.” |
| “Ném `NoSuchElementException` nếu không có phần tử.” | “Ném `NoSuchElementException` nếu bộ lặp đã hết phần tử và vẫn gọi `next()`.” |
| “Ném `IOException` nếu có lỗi.” | “Ném `IOException` nếu không thể đọc nội dung tệp do lỗi I/O.” |
| “Ném `QuotaExceededException` khi thất bại.” | “Ném `QuotaExceededException` nếu số bản ghi yêu cầu vượt hạn mức của tài khoản ở thời điểm gọi.” |

Không phải lúc nào cũng xác định được chính xác nguyên nhân sâu bên trong. Với `IOException`, tài liệu có thể nói “nếu xảy ra lỗi I/O khi đọc” vì đây là abstraction đủ rộng; không nên cam kết từng lỗi hệ điều hành hoặc từng subtype do implementation tình cờ ném.

Khi lỗi phụ thuộc vào trạng thái thay đổi theo thời gian, nêu thời điểm áp dụng quy tắc. Ví dụ “nếu hạn mức còn lại **ở thời điểm xử lý** không đủ” chính xác hơn “nếu tài khoản có hạn mức thấp”, bởi caller không kiểm soát được giao dịch đồng thời.

Nếu có nhiều điều kiện, có thể ghi rõ chúng trong một thẻ khi cùng một hành động khắc phục:

```java
/**
 * @throws IllegalArgumentException nếu tên rỗng hoặc độ dài tên
 *         vượt giới hạn 100 ký tự
 */
```

Nếu các điều kiện dẫn đến **cách phục hồi khác nhau**, cân nhắc exception hoặc mã lỗi riêng thay vì một mô tả quá rộng.

## 4. Ví dụ hoàn chỉnh Java 17: cả checked và unchecked exception

Ví dụ thiết kế `ReservationService.reserve(int)` với hai điều kiện rõ ràng:

- Số lượng không dương là lỗi đối số và ném `IllegalArgumentException` (unchecked).
- Số lượng lớn hơn giới hạn của ví dụ là lỗi quy tắc nghiệp vụ và ném `ReservationLimitException` (checked).

API dùng `@param`, `@return` và `@throws` để diễn đạt cả đầu vào, kết quả và điều kiện thất bại. File dưới đây độc lập, có thể biên dịch và chạy bằng Java 17+.

Tạo file `ThrowsDocumentationDemo.java`:

```java
/**
 * Minh họa hợp đồng exception qua thẻ Javadoc @throws.
 */
public class ThrowsDocumentationDemo {

    /**
     * Lỗi checked khi yêu cầu vượt giới hạn đặt chỗ.
     */
    public static final class ReservationLimitException extends Exception {
        /**
         * @param message mô tả lỗi nghiệp vụ
         */
        public ReservationLimitException(String message) {
            super(message);
        }
    }

    /**
     * Dịch vụ đặt chỗ đơn giản, không truy cập cơ sở dữ liệu.
     */
    public static final class ReservationService {

        private static final int MAX_PER_REQUEST = 5;

        /**
         * Chấp nhận yêu cầu đặt chỗ với số lượng hợp lệ.
         *
         * @param quantity số chỗ cần đặt, phải lớn hơn 0
         * @return thông báo xác nhận số chỗ
         * @throws IllegalArgumentException nếu {@code quantity <= 0}
         * @throws ReservationLimitException nếu
         *         {@code quantity > MAX_PER_REQUEST}
         */
        public String reserve(int quantity) throws ReservationLimitException {
            if (quantity <= 0) {
                throw new IllegalArgumentException(
                    "quantity phải lớn hơn 0"
                );
            }
            if (quantity > MAX_PER_REQUEST) {
                throw new ReservationLimitException(
                    "Tối đa 5 chỗ mỗi yêu cầu"
                );
            }
            return "Đã đặt " + quantity + " chỗ";
        }
    }

    /**
     * Chạy ba tình huống để quan sát hợp đồng đã mô tả.
     *
     * @param args tham số dòng lệnh, không sử dụng
     * @throws ReservationLimitException nếu yêu cầu hợp lệ ở ví dụ
     *         bất ngờ vượt giới hạn; sẽ được truyền ra main
     */
    public static void main(String[] args)
            throws ReservationLimitException {
        ReservationService service = new ReservationService();

        System.out.println(service.reserve(2));

        try {
            service.reserve(0);
        } catch (IllegalArgumentException ex) {
            System.out.println("INPUT: " + ex.getMessage());
        }

        try {
            service.reserve(7);
        } catch (ReservationLimitException ex) {
            System.out.println("LIMIT: " + ex.getMessage());
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 ThrowsDocumentationDemo.java
java ThrowsDocumentationDemo
```

Kết quả chuẩn:

```text
Đã đặt 2 chỗ
INPUT: quantity phải lớn hơn 0
LIMIT: Tối đa 5 chỗ mỗi yêu cầu
```

Các điểm cần đối chiếu với hợp đồng:

1. `reserve(2)` hợp lệ vì `0 < 2 <= 5`, nên trả về thông báo xác nhận.
2. `reserve(0)` gặp điều kiện `quantity <= 0` đầu tiên, ném `IllegalArgumentException`; caller bắt unchecked exception để phản hồi lỗi đầu vào.
3. `reserve(7)` vượt `MAX_PER_REQUEST`, ném `ReservationLimitException`; caller bắt checked exception theo quyết định xử lý nghiệp vụ.
4. `reserve` khai báo `throws ReservationLimitException` để thỏa quy tắc Java cho checked exception; dù không khai báo `throws IllegalArgumentException`, Javadoc vẫn mô tả unchecked exception đó.
5. `main` khai báo `throws ReservationLimitException` vì lời gọi `reserve(2)` nằm ngoài `try/catch`. Trong ví dụ, số lượng `2` không thể gây lỗi giới hạn theo implementation hiện tại; khai báo vẫn hợp lệ và cho thấy compiler kiểm tra exception theo **kiểu và luồng mã**, không chứng minh điều kiện runtime luôn xảy ra.
6. Giới hạn `5` là quy tắc **được cố ý chọn riêng cho ví dụ**. Nếu public API thực sử dụng hạn mức cấu hình hoặc thay đổi theo tài khoản, Javadoc phải mô tả chính xác quy tắc thực tế.

Có thể tạo HTML Javadoc cho file:

```bash
javadoc -encoding UTF-8 -charset UTF-8 -d api-docs ThrowsDocumentationDemo.java
```

Mở `api-docs/index.html` để xem trang tài liệu. Trong tài liệu của `ReservationService.reserve`, phần **Throws** sẽ gồm hai exception và mô tả điều kiện tương ứng. Lệnh `javadoc` có thể phát cảnh báo ở những nơi công cụ yêu cầu thêm mô tả; sự tồn tại của cảnh báo (nếu có) không tự thay đổi runtime behavior.

## 5. @throws không phải là cơ chế kiểm tra hợp đồng tại runtime

Công cụ biên dịch và JVM **không đọc câu mô tả trong `@throws` để kiểm tra điều kiện**. Viết:

```java
/**
 * @throws IllegalArgumentException nếu số lượng nhỏ hơn 0
 */
```

không làm Java tự ném exception khi caller truyền số âm. Người triển khai vẫn phải kiểm tra điều kiện hoặc dựa vào hành vi được bảo đảm bởi API được gọi.

Do đó, tài liệu có thể **sai hoặc lỗi thời** so với code. Những trường hợp hay gặp:

- Comment hứa “ném khi `quantity < 0`” nhưng code kiểm tra `quantity <= 0`.
- Comment ghi “tệp không tồn tại” cho mọi `IOException`, dù thực tế còn có lỗi quyền truy cập và lỗi đọc.
- Comment ghi “đã đóng” nhưng implementation còn cho phép một số thao tác sau `close()`.
- Code thay đổi thứ tự validation khiến một đầu vào vi phạm nhiều quy tắc nhận exception khác trước.

Cần đối chiếu tài liệu với logic và test khi thay đổi điều kiện. Với hợp đồng public có client bên ngoài, thay đổi loại exception hoặc điều kiện ném có thể ảnh hưởng khả năng tương thích ngay cả khi chữ ký Java giữ nguyên.

## 6. Nên tài liệu hóa exception nào?

Với **checked exception**, tài liệu `@throws` thường cần giải thích loại đã khai báo và điều kiện khiến nó truyền ra. Chữ ký `throws IOException` chỉ cho biết có khả năng lỗi I/O; người gọi vẫn cần biết thao tác I/O nào thất bại.

Với **unchecked exception**, không cần ghi toàn bộ `RuntimeException` có thể phát sinh do bug. Nên ưu tiên loại được **cố ý định nghĩa là một phần hành vi của API**:

- `IllegalArgumentException` khi caller vi phạm precondition được chỉ rõ.
- `NullPointerException` khi tham số bắt buộc non-null bị truyền `null`, nếu đây là lựa chọn thiết kế và nhất quán với hợp đồng.
- `IllegalStateException` khi đối tượng ở trạng thái cấm thao tác.
- `UnsupportedOperationException` khi một thao tác tùy chọn không được implementation hỗ trợ.
- Exception miền nghiệp vụ để caller phân biệt lỗi có thể phục hồi.

Không phải mọi `NullPointerException` bất ngờ đều đáng ghi trong `@throws`. NPE do lỗi lập trình nội bộ không phải một điều kiện hữu ích hay lời hứa hành vi dành cho caller. Tương tự, không hứa mọi tên exception do thư viện phụ thuộc ném nếu chúng là chi tiết triển khai mà API muốn che giấu.

### Các exception bọc và truyền tiếp

Nếu API bắt `SQLException` và cố ý chuyển thành `RepositoryUnavailableException`, tài liệu public nên mô tả exception **sau khi chuyển nghĩa** và tình huống có ý nghĩa với caller. Có thể giữ exception gốc làm `cause` để chẩn đoán, nhưng không cần liệt kê subtype của driver như một hợp đồng public.

Ngược lại, nếu API công khai `IOException` như một phần abstraction đọc file, có thể viết `@throws IOException` mà không cần tạo exception tự định nghĩa. Mục tiêu là đúng ranh giới API, không phải thay tất cả ngoại lệ JDK bằng loại mới.

## 7. Viết điều kiện đủ rõ cho người gọi hành động

Một cách thực tế là lập bảng trước khi viết Javadoc:

| Điều kiện có thể quan sát | Kiểu exception public | Người gọi có thể làm gì? |
| --- | --- | --- |
| `quantity <= 0` | `IllegalArgumentException` | Sửa tham số trước khi gọi lại. |
| `quantity > limit` | `ReservationLimitException` | Giảm số lượng hoặc thay đổi quy trình đặt chỗ. |
| Gọi `send()` sau khi `close()` | `IllegalStateException` | Tạo đối tượng/phiên mới theo contract. |
| Không tìm thấy đối tượng với ID hợp lệ | `ResourceNotFoundException` | Kiểm tra ID hoặc thông báo không tìm thấy. |
| Đọc stream bị lỗi I/O | `IOException` | Xử lý lỗi đọc, xem xét retry phù hợp. |

Bảng là công cụ suy nghĩ, không nhất thiết phải lặp nguyên trong Javadoc. Khi viết thẻ `@throws`, chọn câu ngắn nêu **điều kiện chính**; giải thích retry, giao dịch, trạng thái sau lỗi hoặc cách phục hồi trong phần mô tả dài nếu những điều ấy quan trọng.

Một ví dụ Javadoc **minh họa chữ ký API**, không phải chương trình hoàn chỉnh:

```java
/**
 * Gửi gói tin qua phiên đang mở.
 *
 * <p>Thao tác không tự thử lại khi truyền thất bại.
 *
 * @param payload dữ liệu cần gửi, không được null
 * @throws NullPointerException nếu {@code payload == null}
 * @throws IllegalStateException nếu phiên đã đóng
 * @throws IOException nếu gặp lỗi I/O trong khi gửi
 */
public void send(byte[] payload) throws IOException;
```

Ví dụ có thân chữ ký kết thúc bằng `;` như khai báo trong interface; khi đưa vào source code độc lập phải đặt trong interface hoặc khai báo abstract method phù hợp. Các mô tả ở đây là **hợp đồng giả định của interface đó**, không phải khẳng định chung cho mọi API gửi dữ liệu.

Chú ý: `IOException` không tự có nghĩa là gửi thất bại hoàn toàn hoặc chưa có byte nào đến nơi. Nếu caller cần biết liệu có thể thử lại an toàn, API phải nói rõ thêm về khả năng gửi một phần hoặc idempotency.

## 8. Khi một lời gọi vi phạm nhiều điều kiện

Giả sử phương thức `update(record, token)` vừa nhận `record == null`, vừa được gọi khi dịch vụ đã đóng. Nếu code hiện tại kiểm tra `record` trước, caller có thể thấy `NullPointerException`; nếu kiểm tra trạng thái trước, caller có thể thấy `IllegalStateException`.

Không nên hứa thứ tự ưu tiên của lỗi trừ khi nó thực sự cần thiết cho người gọi. Nếu thứ tự được bảo đảm, có thể ghi rõ:

> Kiểm tra đối số được thực hiện trước kiểm tra trạng thái phiên; nếu cả hai đều sai, phương thức báo lỗi đối số.

Nếu không bảo đảm, tài liệu nên nêu điều kiện riêng cho từng loại và tránh khẳng định một thứ tự không được kiểm soát. Khi đổi thứ tự validation, test và Javadoc cần được xem lại.

Với thao tác nhiều bước, cũng phải cân nhắc **thời điểm** lỗi có thể xảy ra. Nếu validation thành công nhưng I/O thất bại, caller cần biết phương thức có thể đã cập nhật một phần trạng thái hay không. `@throws` mô tả điều kiện ném; đoạn mô tả chính nên chứa ràng buộc về trạng thái sau thất bại.

## 9. @throws ở interface, lớp triển khai và phương thức override

Đối với API có interface hoặc base class, hợp đồng lỗi nên đặt ở **điểm người gọi thực sự phụ thuộc**. Nếu caller biết đối tượng qua interface, Javadoc của interface là nơi thích hợp để mô tả loại lỗi và điều kiện mà mọi implementation phải tuân thủ.

Ví dụ minh họa cấu trúc, các class cụ thể không được khai báo đầy đủ ở đây:

```java
public interface Storage {
    /**
     * Đọc tài nguyên theo khóa.
     *
     * @param key khóa không được null
     * @return nội dung tài nguyên
     * @throws NullPointerException nếu {@code key == null}
     * @throws IOException nếu không thể đọc do lỗi I/O
     */
    byte[] read(String key) throws IOException;
}
```

Một implementation có thể dùng cơ chế lưu trữ khác mà vẫn đáp ứng hợp đồng này. Khi override:

- Java **không cho phép** phương thức override mở rộng danh sách checked exception thành checked exception không tương thích với `throws` của phương thức cha. Ví dụ implementation không thể tự thêm `throws SQLException` vào `read` khi interface chỉ khai báo `throws IOException` và `SQLException` không là subtype của `IOException`.
- Java **cho phép** phương thức override ném unchecked exception không có trong `throws`; song hành vi mới vẫn phải phù hợp hợp đồng mà caller được hứa. “Compiler cho phép” không đồng nghĩa với “thiết kế tương thích”.
- Javadoc hỗ trợ kế thừa tài liệu của phương thức được override; `{@inheritDoc}` có thể được dùng khi thích hợp để lấy nội dung đã có. Cần xem kết quả HTML và bổ sung mô tả cho ràng buộc thật sự khác biệt hoặc riêng biệt của implementation, tránh copy tài liệu rồi để lệch.
- Nếu implementation thực sự không đáp ứng một lời hứa hành vi ở interface, sửa thiết kế hoặc làm rõ abstraction; đừng chỉ thêm `@throws` để hợp thức hóa việc phá contract.

Constructor cũng có thể dùng `@throws` để nêu điều kiện khởi tạo thất bại (ví dụ đường dẫn không tồn tại, tham số cấu hình thiếu). Đảm bảo tài liệu phản ánh lỗi thực sự truyền đến caller, không chỉ lỗi xảy ra bên trong.

## 10. Phân biệt Javadoc với tài liệu về xử lý lỗi toàn hệ thống

Thẻ `@throws` thích hợp cho **hợp đồng của từng phương thức**. Những vấn đề lớn hơn có thể cần mô tả ở cấp type, module hoặc API guide:

- Phân loại lỗi thống nhất trong toàn thư viện; mã lỗi hoặc subclass stable.
- Chính sách retry, backoff, idempotency và trạng thái sau lỗi.
- Hành vi rollback hoặc side effect trước khi exception truyền ra.
- Quy tắc xử lý interrupt/cancellation ở API concurrent.
- Biên giới bảo mật: message gửi cho client không được chứa token, SQL hay stack trace.

Một Javadoc như `@throws IOException if an I/O error occurs` có thể hoàn toàn hợp lý cho đọc stream, nhưng không đủ làm tài liệu cho giao dịch upload nhiều phần có tính nhất quán và khả năng retry phức tạp.

Ngược lại, đừng biến mỗi thẻ `@throws` thành một đoạn văn dài mô tả lại toàn bộ kiến trúc. Giữ phần điều kiện ngắn, bổ sung link hoặc phần diễn giải khi người gọi cần.

## 11. Kiểm tra Javadoc và hành vi cùng nhau

### 11.1. Kiểm tra bằng công cụ

Có thể biên dịch source, chạy test và sinh Javadoc như hai loại kiểm chứng khác nhau:

```bash
javac -encoding UTF-8 ThrowsDocumentationDemo.java
java ThrowsDocumentationDemo
javadoc -encoding UTF-8 -charset UTF-8 -d api-docs ThrowsDocumentationDemo.java
```

`javac` xác nhận mã hợp lệ về mặt Java và kiểm tra checked exception. `javadoc` phân tích tài liệu, cố gắng phân giải `@throws` và tạo HTML. Một số lựa chọn doclint giúp phát hiện lỗi về cú pháp tài liệu, tham số hoặc tham chiếu; tuy vậy, **không có công cụ nào tự chứng minh rằng câu “nếu quantity <= 0” đúng với tất cả đường chạy**.

### 11.2. Kiểm tra bằng test tình huống

Đối với mỗi dòng `@throws` quan trọng, nên có test ít nhất cho điều kiện gây lỗi và một trường hợp biên hợp lệ. Ví dụ cho `reserve`:

| Input | Kỳ vọng |
| --- | --- |
| `quantity = 0` | `IllegalArgumentException` |
| `quantity = -1` | `IllegalArgumentException` |
| `quantity = 1` | Trả kết quả thành công |
| `quantity = 5` | Trả kết quả thành công |
| `quantity = 6` | `ReservationLimitException` |

Tập test này kiểm tra đúng **ranh giới `0` và `5`** mà Javadoc cam kết; không cần assert stack trace hoặc message của dependency phụ thuộc bên ngoài. Nếu message public được tuyên bố là hợp đồng, lúc đó mới kiểm tra chuỗi hoặc mã lỗi có chủ đích.

Với API nhiều trạng thái, test thêm `open → close → send`; với lỗi I/O, dùng fake hoặc mock để kích hoạt trường hợp được mô tả. Nếu phương thức có nhiều nhánh lỗi, test nên phản ánh các nhánh có thể quan sát thay vì chỉ độ phủ dòng mã.

### 11.3. Checklist review trước khi phát hành

- Kiểu exception ghi trong `@throws` có thực sự được phương thức ném hoặc truyền ra theo hợp đồng dự kiến?
- Những checked exception trong `throws` đã được giải thích đủ để caller hiểu vì sao chúng xảy ra chưa?
- Các unchecked exception do precondition và trạng thái API cố ý quy định đã được mô tả chưa?
- Các điều kiện ranh giới (`null`, rỗng, âm, bằng giới hạn, vượt giới hạn, đã đóng) có chính xác không?
- Có tên class, mã lỗi, message hoặc chi tiết implementation vô tình thành public contract không?
- Nếu có override, Javadoc của interface/base class có nhất quán với implementation không?
- Nếu đổi logic validation hoặc dependency, đã cập nhật tài liệu và test tương ứng chưa?

## 12. Những lỗi thường gặp và cách sửa

**Chỉ viết “if an error occurs”.** Câu này không hướng dẫn caller. Hãy mô tả loại thất bại và trạng thái/đầu vào kích hoạt; với I/O rộng, có thể dùng câu “nếu xảy ra lỗi I/O khi đọc dữ liệu”.

**Ghi `@throws` nhưng không khai báo checked exception trong `throws`.** Comment không ảnh hưởng đến kiểm tra của compiler. Phải bắt hoặc khai báo checked exception theo luật Java.

**Không tài liệu hóa unchecked exception là một phần precondition.** Những điều kiện như `size <= 0` hay “gọi sau close” có thể quan trọng hơn lỗi checked hiếm gặp. Ghi `@throws` cho hành vi được thiết kế có chủ đích.

**Liệt kê mọi exception từ các lời gọi sâu bên trong.** Điều này dễ biến chi tiết triển khai thành hợp đồng. Tài liệu hóa lỗi public có ý nghĩa; translation ở boundary cần rõ ràng nếu implementation thay đổi.

**Mô tả khác với điều kiện thực tế.** Ví dụ comment nói “nhỏ hơn 0” nhưng code nói “nhỏ hơn hoặc bằng 0”. Đọc lại toán tử, điều kiện biên và thứ tự kiểm tra; dùng test cho từng biên.

**Nhầm `@return` với `@throws`.** `@return` mô tả kết quả khi hoàn thành bình thường, còn `@throws` mô tả đường thất bại. Với API trả sentinel (`null`, empty, Optional) thay vì ném lỗi, tài liệu phải nhất quán với lựa chọn đó.

**Hứa điều kiện phụ thuộc lịch trình hoặc implementation.** Ví dụ khẳng định timeout luôn an toàn để retry hoặc exception chỉ xảy ra trước khi ghi dữ liệu. Chỉ hứa khi có cơ chế bảo đảm; cần mô tả side effect và trạng thái sau lỗi nếu caller dựa vào chúng.

**Dùng tên kiểu exception không phân giải được trong Javadoc.** Kiểm tra import/package và chạy `javadoc` để phát hiện lỗi tham chiếu. Nếu code trong ví dụ giả định một kiểu ứng dụng chưa khai báo, ghi rõ nó là đoạn minh họa.

## 13. Bài tập áp dụng

Xét một API giả định `SessionCache.get(String key)` có yêu cầu: `key` phải non-null, cache phải đang mở, nếu không có phần tử thì trả `Optional.empty()`, còn nếu backend không thể truy cập thì ném `CacheUnavailableException`.

Hãy phác thảo Javadoc bằng `@param`, `@return`, `@throws` cho ba lỗi có thể quan sát: null key, cache đã đóng, backend không sẵn sàng. Đồng thời tự trả lời: **cache miss** có nên có một thẻ `@throws` không?

Đáp án tham khảo **chỉ là chữ ký minh họa; `CacheUnavailableException` và implementation chưa được định nghĩa**:

```java
/**
 * Tra cứu phần tử theo khóa.
 *
 * @param key khóa tra cứu, không được null
 * @return Optional chứa giá trị nếu tìm thấy; Optional.empty()
 *         nếu khóa hợp lệ nhưng không có dữ liệu
 * @throws NullPointerException nếu {@code key == null}
 * @throws IllegalStateException nếu cache đã đóng
 * @throws CacheUnavailableException nếu backend không thể phục vụ tra cứu
 */
public Optional<String> get(String key)
        throws CacheUnavailableException;
```

Nếu cache miss được thiết kế là kết quả `Optional.empty()` thì đó **không phải exception** và cần mô tả ở `@return`, không đưa vào `@throws`. Đây là ví dụ về việc quyết định hợp đồng thành công/thất bại trước khi viết tài liệu.

Điểm cần nhớ: **`@throws` mô tả lời hứa có thể kiểm chứng về điều kiện exception truyền đến caller**. Để Javadoc đáng tin, nó phải đồng bộ với chữ ký `throws`, điều kiện `throw` trong implementation, hợp đồng ở interface/base class và các test cho trường hợp biên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-api-design-008">Chọn ngoại lệ, Optional hay kiểu kết quả có cấu trúc</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn ngoại lệ, Optional hay kiểu kết quả có cấu trúc trong thiết kế API Java

## Mục tiêu và bối cảnh

Một API có hai phần hợp đồng: **giá trị thành công** và **ý nghĩa của các kết cục khác**. Với Java Core, những kết cục khác có thể là “không có kết quả nhưng vẫn bình thường”, “yêu cầu bị từ chối theo quy tắc nghiệp vụ”, “tham số vi phạm tiền điều kiện” hoặc “không thể thực hiện do lỗi I/O/hạ tầng”. Ba công cụ `exception`, `Optional<T>` và **kiểu kết quả có cấu trúc** diễn đạt các ý nghĩa khác nhau.

Sau bài này, bạn sẽ biết cách chọn kiểu trả về theo ngữ nghĩa, thiết kế hợp đồng không nhập nhằng, vận dụng `sealed interface` và `record` trong Java 17, phân biệt checked/unchecked exception, xử lý nguyên nhân lỗi và kiểm thử đầy đủ các nhánh.

## 1. Phân loại trước khi chọn công cụ

| Kết cục | Ví dụ | Biểu diễn nên cân nhắc |
| --- | --- | --- |
| Thành công chắc chắn có giá trị | Tính tổng hai số hợp lệ | Trả `T` trực tiếp |
| Thành công nhưng có thể vắng mặt, không cần biết “vì sao” | Tìm sản phẩm theo mã; không có sản phẩm tương ứng | `Optional<Product>` |
| Nhiều kết cục nghiệp vụ **dự kiến**, mỗi nhánh có thông tin khác nhau | Yêu cầu mua hàng: được chấp nhận, mã không tồn tại, số lượng sai, không đủ hàng | `QuoteResult`/`Result<T,E>` |
| Người gọi dùng sai API hoặc vi phạm invariant | Truyền mã `null`/trống khi hợp đồng cấm | `IllegalArgumentException`, `NullPointerException` hoặc ngoại lệ phù hợp |
| Thao tác không thể hoàn thành do lỗi kỹ thuật | Không đọc được file, truy vấn database lỗi | Exception phù hợp, giữ nguyên `cause` |

Bảng là định hướng, không phải luật tuyệt đối. Ví dụ **số lượng âm** có thể là `InvalidQuantity` khi tiếp nhận biểu mẫu và muốn hiển thị lỗi sửa được; trong một API nội bộ yêu cầu `quantity > 0` từ caller đã xác thực, đó có thể là `IllegalArgumentException`. Câu hỏi cốt lõi là: **người gọi có dự kiến tình huống này và phải xử lý nó như một giá trị bình thường hay không?**

Trước khi viết chữ ký, hãy hỏi:
1. Phương thức có hoàn thành thành công theo hợp đồng không?
2. Nếu hoàn thành, người gọi cần phân biệt “không có giá trị” hay nhiều trạng thái khác nhau?
3. Nếu không hoàn thành, đó là sai hợp đồng của caller, lỗi hạ tầng hay invariant bị phá vỡ?
4. Người gọi nên tiếp tục, hiện thông báo, sửa dữ liệu, thử lại hay dừng xử lý?

## 2. Khi nào dùng exception?

Exception diễn đạt **luồng xử lý thành công bị gián đoạn**. `throw` làm phương thức ngừng trả giá trị bình thường; JVM tìm `catch` phù hợp qua các khung gọi. Exception có kiểu, thông điệp, stack trace và có thể có nguyên nhân gốc (`cause`). Không nên ném exception liên tục như cơ chế rẽ nhánh cho những trạng thái nghiệp vụ xảy ra thường xuyên.

Các loại hay dùng:

- **`IllegalArgumentException`**: giá trị tham số không đáp ứng tiền điều kiện đã công bố. Với tham số tham chiếu bắt buộc khác `null`, `Objects.requireNonNull` ném `NullPointerException` và có thể cho thông điệp rõ ràng.
- **`IllegalStateException`**: đối tượng hoặc tiến trình không ở trạng thái cho phép thao tác.
- **`IOException`**: thao tác I/O bị thất bại; người gọi có thể có phương án khác hoặc báo lỗi phù hợp.
- **Exception miền riêng**: dùng khi một loại lỗi bất thường có ý nghĩa xử lý riêng; tránh tạo nhiều class chỉ để đổi thông điệp.

### Checked và unchecked exception

`Exception` và các lớp con là **checked**, trừ `RuntimeException` và các lớp con (unchecked). `Error` cũng là `Throwable` nhưng thông thường không phải đối tượng cần bắt trong logic nghiệp vụ. Compiler yêu cầu bắt hoặc khai báo `throws` đối với checked exception; unchecked không bắt buộc khai báo.

| Tiêu chí | Checked, ví dụ `IOException` | Unchecked, ví dụ `IllegalArgumentException` |
| --- | --- | --- |
| Khi biên dịch | Cần `catch` hoặc `throws` | Không bắt buộc |
| Công dụng điển hình | Thất bại bên ngoài mà API muốn caller cân nhắc rõ ràng | Sai tiền điều kiện, trạng thái bất hợp lệ, hoặc lỗi có chính sách lan truyền |
| Rủi ro | Caller bắt đại hoặc phải chuyển tiếp qua nhiều tầng | Hợp đồng thiếu tài liệu khiến caller bỏ sót nhánh lỗi |

Không có quy tắc “lỗi nặng thì checked, lỗi nhẹ thì unchecked”. Cần xem tầng gọi **có thể khắc phục được gì**, chuẩn của thư viện đang dùng và tương thích chữ ký công khai.

Ví dụ Java sử dụng checked exception:

~~~java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class ConfigReader {
    static String read(Path path) throws IOException {
        return Files.readString(path);
    }
}
~~~

Đây là **lớp minh họa**, chưa có phương thức `main`. Khi file không đọc được, `Files.readString` ném `IOException` phù hợp; khi đọc thành công, kết quả là chuỗi. Không thể xác định stdout vì đoạn mã không thực thi đọc một file cụ thể. Đừng đổi lỗi đọc file thành `Optional.empty()`: “không tìm thấy dữ liệu sau khi tra cứu thành công” khác với “không biết dữ liệu có tồn tại vì I/O thất bại”.

Nếu API quyết định bọc checked exception thành unchecked, hãy giữ nguyên nhân:

~~~java
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

class ConfigService {
    static String requireConfig(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read config: " + path, e);
        }
    }
}
~~~

Đoạn này cũng là minh họa không có `main`. `UncheckedIOException` giữ `cause` nên tầng trên vẫn truy ra lỗi gốc. Chỉ bọc khi hợp đồng cần điều đó; không mặc định mọi checked exception đều phải chuyển thành unchecked.

**Cách bắt exception đúng:** bắt ở ranh giới có khả năng xử lý (đổi nguồn dữ liệu, thử lại có giới hạn, hiển thị lỗi, ánh xạ HTTP response), hoặc bổ sung ngữ cảnh rồi ném tiếp. Tránh `catch (Exception ignored)`, trả `null`/`false` sau khi nuốt lỗi, bắt `Throwable`/`Error` trong logic bình thường, và ghi log lặp lại ở mọi tầng.

## 3. Khi nào dùng Optional<T>?

`Optional<T>` trả lời đúng một câu hỏi: **sau khi thao tác hợp lệ đã thực hiện, có một giá trị T hay không?** Nó không chứa nguyên nhân lỗi, số lượng sai, mã lỗi hay ngăn phương thức phát sinh exception.

Ví dụ:

~~~java
import java.util.Map;
import java.util.Optional;

class Catalog {
    private static final Map<String, String> NAMES =
            Map.of("P01", "Notebook");

    static Optional<String> findName(String sku) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku must not be blank");
        }
        return Optional.ofNullable(NAMES.get(sku));
    }
}
~~~

Lớp `Catalog` là mã minh họa không có `main`. `findName("P01")` có giá trị, `findName("P02")` là Optional rỗng. Đầu vào `null` hoặc trắng vi phạm hợp đồng và bị ném exception; hai tình huống này không bị nhầm thành “mã hợp lệ nhưng không tìm thấy”.

Các API hữu ích:

| Phương thức | Ý nghĩa |
| --- | --- |
| `Optional.of(x)` | Tạo Optional có giá trị; `x == null` gây `NullPointerException` |
| `Optional.ofNullable(x)` | Trả có giá trị khi `x != null`, ngược lại rỗng |
| `Optional.empty()` | Biểu diễn vắng mặt hợp lệ |
| `map(f)`, `flatMap(f)` | Chuyển đổi khi có giá trị; `flatMap` dành cho hàm đã trả Optional |
| `filter(p)` | Giữ giá trị khi thỏa điều kiện |
| `ifPresent(f)`, `ifPresentOrElse(f, emptyAction)` | Xử lý một hoặc cả hai nhánh |
| `orElse(defaultValue)` | Dùng giá trị dự phòng; biểu thức đối số được đánh giá ngay |
| `orElseGet(supplier)` | Chỉ tính giá trị dự phòng khi Optional rỗng |
| `orElseThrow(supplier)` | Chuyển vắng mặt thành exception tại chỗ caller yêu cầu có giá trị |
| `isPresent()`, `isEmpty()` | Kiểm tra hai trạng thái |

Ví dụ người gọi quyết định chính sách:

~~~java
String name = Catalog.findName("P02").orElse("Chưa có tên");

String requiredName = Catalog.findName("P02")
        .orElseThrow(() -> new IllegalStateException("Product P02 is required"));
~~~

Các biểu thức này giả định `Catalog` đã được định nghĩa phía trên. Biểu thức đầu chọn giá trị thay thế; biểu thức thứ hai ném exception vì **caller áp đặt yêu cầu bắt buộc có bản ghi**. Không nên gọi `Optional.get()` khi chưa chắc có giá trị vì Optional rỗng sẽ gây `NoSuchElementException` và thường ít thông tin ngữ cảnh.

Những sai lầm thường thấy:
- **Trả `null` thay cho đối tượng Optional:** nếu chữ ký là `Optional<T>` thì luôn trả một Optional thực sự.
- **Nuốt lỗi hạ tầng và trả `empty()`:** không thể phân biệt mất kết nối với bản ghi không tồn tại.
- **Dùng `Optional<List<T>>` khi chỉ muốn báo tập rỗng:** thường chỉ cần trả `List.of()` hoặc một danh sách rỗng. Dùng Optional khi *bản thân sự vắng mặt của danh sách* có nghĩa riêng.
- **Lạm dụng Optional ở tham số và field:** Optional đặc biệt hữu ích ở **kiểu trả về**. Tham số tùy chọn có thể biểu đạt bằng overload, builder hoặc kiểu yêu cầu phù hợp; cân nhắc yêu cầu framework/serialization khi dùng Optional như field.
- **Lồng Optional không cần thiết:** khi phép chuyển đổi đã trả Optional, dùng `flatMap`.
- **Tưởng Optional thay thế validation:** đầu vào sai hoặc lỗi bất thường vẫn cần hợp đồng riêng.

## 4. Khi nào dùng kiểu kết quả có cấu trúc?

Khi thao tác có **nhiều kết cục hợp lệ theo quy trình nghiệp vụ**, người gọi cần loại trạng thái và dữ liệu tương ứng. Ví dụ đánh giá yêu cầu mua hàng có thể cho:
- `Accepted`: đủ điều kiện, kèm mã và số lượng;
- `UnknownProduct`: mã đã nhập không tồn tại;
- `InvalidQuantity`: số lượng người dùng yêu cầu không hợp lệ;
- `OutOfStock`: số lượng mong muốn lớn hơn tồn kho, kèm số lượng yêu cầu và hiện có.

Trả `boolean`, `null`, `Optional.empty()` hoặc ném một `RuntimeException("out of stock")` cho tất cả các trường hợp sẽ làm người gọi thiếu thông tin để hiển thị, sửa dữ liệu hoặc thống kê.

Trong Java 17+, **`sealed interface` + `record`** đặc biệt phù hợp. `sealed` giới hạn các kiểu được phép triển khai, trong khi mỗi `record` chứa dữ liệu có kiểu cho một trạng thái. `record` sinh accessor, `equals`, `hashCode` và `toString`, nhưng không tự kiểm tra quy tắc nghiệp vụ trong dữ liệu; cần constructor khi invariant đòi hỏi điều đó.

Một phương án chung khác là `Result<T,E>` với hai nhánh `Success<T,E>` và `Failure<T,E>`, trong đó `E` nên là enum/record/hierarchy có cấu trúc. Nếu `E` chỉ là một chuỗi tự do, caller rất dễ phải phân tích nội dung thông điệp bằng cách so chữ hoặc `contains`, dẫn đến API mong manh khi đổi ngôn ngữ.

### 4.1. Ví dụ đầy đủ Java 17: kết hợp ba cơ chế

Lưu mã sau dưới tên **`ApiOutcomeDemo.java`**. Catalog đặt trong bộ nhớ để kết quả có thể lặp lại. `quote` chỉ **đánh giá yêu cầu**, không thực hiện đặt hàng và không trừ tồn kho.

~~~java
import java.util.Map;
import java.util.Optional;

public class ApiOutcomeDemo {
    record Product(String sku, int available) {}

    sealed interface QuoteResult
            permits Accepted, UnknownProduct, InvalidQuantity, OutOfStock {}

    record Accepted(String sku, int quantity) implements QuoteResult {}
    record UnknownProduct(String sku) implements QuoteResult {}
    record InvalidQuantity(int quantity) implements QuoteResult {}
    record OutOfStock(String sku, int requested, int available) implements QuoteResult {}

    private static final Map<String, Product> STOCK = Map.of(
            "P01", new Product("P01", 5)
    );

    static Optional<Product> findProduct(String sku) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku must not be blank");
        }
        return Optional.ofNullable(STOCK.get(sku));
    }

    static QuoteResult quote(String sku, int quantity) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku must not be blank");
        }
        if (quantity <= 0) {
            return new InvalidQuantity(quantity);
        }
        Product product = STOCK.get(sku);
        if (product == null) {
            return new UnknownProduct(sku);
        }
        if (quantity > product.available()) {
            return new OutOfStock(sku, quantity, product.available());
        }
        return new Accepted(sku, quantity);
    }

    static String describe(QuoteResult result) {
        if (result instanceof Accepted ok) {
            return "OK " + ok.sku() + " x" + ok.quantity();
        }
        if (result instanceof UnknownProduct missing) {
            return "NOT_FOUND " + missing.sku();
        }
        if (result instanceof InvalidQuantity invalid) {
            return "INVALID_QUANTITY " + invalid.quantity();
        }
        if (result instanceof OutOfStock stock) {
            return "OUT_OF_STOCK " + stock.available();
        }
        throw new IllegalStateException("Unknown QuoteResult: " + result);
    }

    public static void main(String[] args) {
        System.out.println(findProduct("P01").isPresent());
        System.out.println(findProduct("P02").isEmpty());
        System.out.println(describe(quote("P01", 2)));
        System.out.println(describe(quote("P01", 8)));
        System.out.println(describe(quote("P02", 1)));
        System.out.println(describe(quote("P01", 0)));
        try {
            quote("", 2);
        } catch (IllegalArgumentException e) {
            System.out.println("EXCEPTION " + e.getMessage());
        }
    }
}
~~~

Biên dịch và chạy từ thư mục chứa file:

~~~bash
javac --release 17 ApiOutcomeDemo.java
java ApiOutcomeDemo
~~~

Stdout chính xác:

~~~text
true
true
OK P01 x2
OUT_OF_STOCK 5
NOT_FOUND P02
INVALID_QUANTITY 0
EXCEPTION sku must not be blank
~~~

Ý nghĩa từng nhánh:
1. `findProduct("P01")` trả Optional có giá trị; `findProduct("P02")` trả rỗng. Tìm kiếm vẫn hoàn thành theo hợp đồng.
2. `quote("P01", 2)` trả `Accepted`; `quote("P01", 8)` trả `OutOfStock` với `requested=8`, `available=5`.
3. `quote("P02", 1)` trả `UnknownProduct`, một kết cục nghiệp vụ dự kiến, khác với một lỗi database không thể truy vấn.
4. `quote("P01", 0)` trả `InvalidQuantity` để caller có thể nhắc người dùng sửa số lượng.
5. `quote("", 2)` ném `IllegalArgumentException` vì chuỗi trắng vi phạm tiền điều kiện của API.
6. `STOCK` bất biến. Nếu viết một phương thức đặt hàng thực sự, cần transaction/cạnh tranh truy cập và nhất quán tồn kho; một kết quả `quote` không bảo đảm giữ được hàng.

**Thứ tự kiểm tra cũng là hợp đồng.** Ví dụ ưu tiên số lượng không hợp lệ trước tra cứu SKU; khi vừa sai số lượng vừa không có sản phẩm, kết quả là `InvalidQuantity`. Nếu API muốn thông báo nhiều lỗi cùng lúc, nên trả một kết quả validation chứa danh sách lỗi.

Java 17 hỗ trợ ổn định `instanceof` pattern matching như trong ví dụ, nhưng `switch` pattern matching trong Java 17 còn là preview, vì vậy không đưa vào ví dụ cần chạy bằng `javac --release 17` thông thường. Đối với caller ở ngôn ngữ/phiên bản hỗ trợ switch toàn diện trên sealed hierarchy, có thể tận dụng kiểm tra exhaustiveness; còn chuỗi `if/else` Java 17 trong ví dụ phải tự xử lý đầy đủ biến thể.

### 4.2. Nhiều lỗi xác thực trong một kết quả

Thay vì dừng ở lỗi đầu tiên của biểu mẫu, có thể trả một cấu trúc bao gồm mã trường và mã lỗi:

~~~java
import java.util.List;

record FieldError(String field, String code) {}

record ValidationFailure(List<FieldError> errors) {
    ValidationFailure {
        errors = List.copyOf(errors);
    }
}
~~~

Hai record ở đây chỉ là ví dụ khai báo kiểu, không có `main`. `List.copyOf` tạo bản sao không thể chỉnh sửa qua list trả về và từ chối phần tử `null`; nếu muốn danh sách bắt buộc không rỗng thì cần kiểm tra thêm. Mã lỗi như `REQUIRED`, `NOT_INTEGER` hoặc `OUT_OF_RANGE` ổn định hơn thông điệp tự do. UI có thể tự bản địa hóa thông báo từ mã lỗi.

## 5. Ba công cụ có thể dùng trong cùng một API

Không cần ép cả module theo duy nhất một hình thức:

~~~text
findProduct(validSku)        -> Optional<Product>
quote(validSku, quantity)    -> QuoteResult
readConfig(path)             -> String, throws IOException
findProduct(blankSku)        -> IllegalArgumentException
~~~

Bảng chữ ký trên là **sơ đồ ý nghĩa**, không phải cú pháp khai báo Java hoàn chỉnh. Chúng phản ánh bốn tình huống: tra cứu có thể vắng mặt, kết cục nghiệp vụ nhiều nhánh, lỗi I/O bất thường và vi phạm hợp đồng gọi.

Thậm chí một phương thức khai báo `Result<Order, DomainError>` vẫn có thể ném ngoại lệ I/O nếu tầng dưới thất bại ngoài các kết cục nghiệp vụ dự kiến. Khi thiết kế, cần làm rõ loại lỗi nào là `DomainError` và loại nào truyền ra dưới dạng exception. Đừng tùy tiện quy đổi mọi `Exception` thành `Failure` vì có thể che lỗi lập trình đang làm hỏng invariant.

## 6. Tầng nào nên chuyển đổi kết cục?

| Nguồn tình huống | Tầng Java cần hiểu | Ranh giới giao tiếp có thể làm gì |
| --- | --- | --- |
| Tra cứu hợp lệ, không có bản ghi | Repository/service trả Optional theo hợp đồng | REST có thể biến vắng mặt thành 404 khi resource bắt buộc tồn tại |
| Không đủ hàng theo nghiệp vụ | Service trả `OutOfStock` | REST có thể ánh xạ thành 409 Conflict theo hợp đồng ứng dụng |
| Người dùng nhập sai dữ liệu | Validation/service trả result chứa mã lỗi | UI hiển thị từng trường; REST thường cân nhắc 400 hoặc 422 |
| Không kết nối được database | Hạ tầng truyền exception đủ nguyên nhân | Ranh giới REST xử lý thành lỗi máy chủ thích hợp; không báo 404 |
| Caller truyền tham số bị cấm | Phương thức ném exception về sai tiền điều kiện | Sửa caller hoặc bổ sung validation tại điểm tiếp nhận dữ liệu |

Các mã HTTP trên chỉ là ví dụ **chuyển đổi ở adapter**. `Optional.empty()` tự nó không “có nghĩa là HTTP 404”; trong Java Core, đó chỉ là kết quả rỗng. Tầng REST quyết định status dựa trên hợp đồng endpoint. Không trả stack trace, connection string hoặc thông tin nhạy cảm ra bên ngoài. Có thể log có ngữ cảnh ở ranh giới xử lý và giữ cause để chẩn đoán; tránh ghi trùng cùng lỗi ở mọi tầng.

**Retry:** chỉ thử lại lỗi có khả năng tạm thời và khi thao tác an toàn để thử lại; không retry đơn hàng có tác dụng phụ một cách mù quáng. “Không đủ hàng” hoặc “số lượng nhập âm” thường là trạng thái yêu cầu người dùng thay đổi, không phải lý do để retry tự động.

## 7. Cây quyết định nhanh

~~~text
Thao tác hoàn thành theo hợp đồng?
|
+-- CÓ
|   +-- Luôn có T                     -> T
|   +-- Chỉ có/không có T             -> Optional<T>
|   +-- Nhiều kết cục nghiệp vụ      -> Kiểu result có cấu trúc
|
+-- KHÔNG
    +-- Caller vi phạm tiền điều kiện -> Exception phù hợp
    +-- Sự cố I/O, trạng thái hỏng    -> Exception phù hợp, giữ cause
~~~

Đây là sơ đồ thiết kế, không phải chương trình Java. Nếu tình huống “không tìm thấy” cần thêm dữ liệu lỗi cụ thể hoặc phân biệt với “không đủ quyền”, “hết hạn”, “chưa được kích hoạt”, result type có cấu trúc có thể rõ hơn Optional. Nếu chỉ cần “có hay không”, Optional đơn giản và đủ ý nghĩa.

## 8. Thiết kế sai và hướng sửa

### A. `null` được dùng cho mọi thất bại

~~~java
// Anti-pattern: null vừa có thể là không tìm thấy,
// vừa là input sai, vừa là lỗi truy vấn.
Product findProduct(String sku) { /* ... */ return null; }
~~~

Đây là mã minh họa không độc lập, vì chưa có định nghĩa `Product` và phần thân thật. Hướng sửa: khai báo `Optional<Product>` cho không tìm thấy hợp lệ; exception cho sai tiền điều kiện và lỗi không thể hoàn thành; hoặc kiểu result khi các nhánh dự kiến cần phân biệt. Chỉ thay `null` bằng `Optional.empty()` mà giữ cách đánh đồng cũ thì chưa sửa được lỗi thiết kế.

### B. Ném exception để xử lý mọi sự kiện nghiệp vụ

~~~java
// Anti-pattern nếu hết hàng là trạng thái thường xuyên mà UI phải xử lý:
if (requested > available) {
    throw new IllegalStateException("out of stock");
}
~~~

Đây là fragment, giả sử `requested` và `available` đã được khai báo. Khi caller cần số lượng còn và cách đề xuất sửa đơn hàng, `OutOfStock(requested, available)` truyền được thông tin ngay trong kiểu dữ liệu. Ngược lại, tồn kho âm ngoài invariant có thể đúng là tình trạng bất thường nên ném exception.

### C. Nuốt lỗi hạ tầng rồi trả Optional rỗng

~~~java
// Anti-pattern: caller tưởng rằng chắc chắn không có bản ghi.
try {
    return repository.findById(id);
} catch (RuntimeException ignored) {
    return Optional.empty();
}
~~~

Fragment giả sử `findById` trả Optional. Truy vấn thất bại có nghĩa là **chưa xác định được** bản ghi có tồn tại không. Không được biến thành bằng chứng “không tồn tại”.

### D. Trả mã lỗi qua một chuỗi tự do

~~~java
record WeakResult(boolean success, String message) {}
~~~

Khi caller phải làm `message.contains("stock")` để ra quyết định, chỉ một thay đổi câu chữ hoặc ngôn ngữ cũng phá logic. Thay bằng subtype/mã lỗi ổn định và trường dữ liệu có kiểu; thông điệp chỉ dành cho hiển thị và chẩn đoán.

### E. Lạm dụng `Optional.get()` hoặc `orElse`

`get()` sẽ ném `NoSuchElementException` nếu rỗng. `orElse(expensiveFallback())` gọi `expensiveFallback()` ngay cả khi Optional có giá trị do quy tắc đánh giá đối số Java; dùng `orElseGet(() -> expensiveFallback())` khi cần tạo dự phòng lười. `Optional.of(null)` gây NPE; dùng `ofNullable` khi đầu vào có thể `null`.

## 9. Thiết kế hợp đồng API công khai

Một chữ ký tốt cần đi kèm tài liệu:
- **Tiền điều kiện:** có cho phép `null`, chuỗi trắng, số âm không? Điều kiện nào là validation do người dùng sửa và điều kiện nào là sai từ caller?
- **Thành công:** có thể trả `null` không? Có khả năng vắng mặt hay nhiều biến thể? Các record có cho phép trường rỗng không?
- **Thất bại:** checked exception nào trong `throws`; unchecked exception quan trọng nào phải ghi rõ; I/O error và cause được giữ thế nào?
- **Tác dụng phụ:** phương thức chỉ kiểm tra hay thật sự ghi dữ liệu? Retry có an toàn không?
- **Tương thích:** việc thêm subtype kết quả hoặc chuyển `T` thành `Optional<T>` có yêu cầu caller thay đổi không?

Đổi `Product findById(String)` thành `Optional<Product> findById(String)` là **thay đổi chữ ký Java**, buộc caller cập nhật. Với thư viện đã phát hành, cân nhắc tạo một API mới có tên rõ ràng và lộ trình chuyển đổi. Thêm biến thể mới vào sealed result hierarchy cũng là thay đổi hợp đồng với caller vốn giả định tập trường hợp cũ. Đừng coi `Result<T,String>` là sự bảo đảm tương thích khi ý nghĩa các chuỗi có thể thay đổi tùy ý.

Khi cần kết quả được chuẩn hóa qua nhiều API, có thể dùng `Result<T,E>` với `E` là kiểu lỗi miền. Nhưng tránh trừu tượng hóa chỉ để thay hai giá trị “có/không” đơn giản bằng một khuôn cồng kềnh.

## 10. Ma trận kiểm thử theo hợp đồng

| Đầu vào/tình huống | Hành vi kỳ vọng | Khẳng định quan trọng |
| --- | --- | --- |
| `findProduct("P01")` | Optional có Product | Giá trị tồn tại và đúng SKU |
| `findProduct("P02")` | Optional rỗng | Không ném do không có bản ghi |
| `findProduct("")` | `IllegalArgumentException` | Input sai không biến thành `empty()` |
| `quote("P01", 2)` | `Accepted` | Đúng SKU và số lượng |
| `quote("P01", 8)` | `OutOfStock` | `requested=8`, `available=5` |
| `quote("P02", 1)` | `UnknownProduct` | Không nhầm với lỗi truy vấn |
| `quote("P01", 0)` | `InvalidQuantity` | Mã lỗi cho caller sửa nhập liệu |
| Đồng thời số lượng sai và SKU không tồn tại | `InvalidQuantity` trong ví dụ | Thứ tự validation ổn định |
| File bị từ chối đọc | `IOException` hoặc wrapper có cause | Không rơi vào nhánh “vắng mặt” |
| API bọc lỗi I/O | `UncheckedIOException` có cause gốc | Không làm mất ngữ cảnh chẩn đoán |

Ở chương trình minh họa, `STOCK` là Map bất biến nên không có lỗi I/O; muốn test lỗi hạ tầng cần cung cấp dependency giả lập thất bại. Kiểm thử cần xác nhận **loại kết quả và các trường bên trong**, không chỉ chuỗi `toString()` hoặc thông điệp.

## 11. Checklist quyết định cuối cùng

1. Nếu chỉ có **một giá trị hoặc vắng mặt bình thường**, dùng `Optional<T>`; không dùng nó để giấu lỗi.
2. Nếu có **nhiều kết cục nghiệp vụ dự kiến và cần xử lý khác nhau**, dùng result type có kiểu cụ thể, chẳng hạn sealed hierarchy.
3. Nếu xảy ra **vi phạm tiền điều kiện, invariant hoặc thất bại thực thi bất thường**, dùng exception phù hợp, tránh biến nó thành mã “thành công rỗng”.
4. Bảo đảm giá trị và lỗi có dữ liệu đủ để caller xử lý, không buộc caller phân tích câu chữ.
5. Ghi rõ nullability, thứ tự validation, mức độ recoverable, side effect và tính an toàn khi retry.
6. Khi chuyển exception, giữ cause; khi chuyển result thành HTTP/UI, ánh xạ ở ranh giới giao tiếp.
7. Test thành công, vắng mặt, mọi nhánh nghiệp vụ, input sai và lỗi phụ thuộc bên ngoài.

**Bài tập vận dụng:** Thiết kế `parseAge(String raw)` cho biểu mẫu có thể nhận chuỗi trống, chữ không phải số, số ngoài khoảng cho phép hoặc tuổi hợp lệ. Hãy lý giải lợi ích của `MissingAge`, `NotANumber`, `OutOfRange` và `ValidAge` so với `Optional<Integer>`. Sau đó thiết kế `findUserById(long id)` với id hợp lệ và kết quả “không tìm thấy” là bình thường: `Optional<User>` có thể là lựa chọn sáng sủa nhất. Cuối cùng giả lập lỗi I/O ở kho dữ liệu và chứng minh API không đánh đồng “truy vấn thất bại” với “không có user”.

Tiêu chí cốt lõi: **kiểu trả về và ngoại lệ phải biểu đạt đúng ý nghĩa thực tế của từng kết cục**, giúp người gọi đưa ra quyết định chính xác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-api-design-009">Không để chi tiết thư viện phụ thuộc trở thành hợp đồng công khai ngoài ý muốn</a>

<details>
<summary>Xem chi tiết</summary>

# Không để chi tiết thư viện phụ thuộc trở thành hợp đồng công khai ngoài ý muốn

Khi một API Java gọi thư viện phân tích dữ liệu, HTTP client, JDBC driver hoặc SDK bên ngoài, thư viện đó có thể ném những exception rất cụ thể. Nếu các kiểu exception, mã lỗi, thông điệp hay cách xử lý nội bộ ấy tràn qua ranh giới API, người dùng API sẽ vô tình phụ thuộc vào một thư viện mà họ không trực tiếp lựa chọn. Việc thay thư viện hoặc nâng phiên bản về sau có thể trở thành thay đổi phá vỡ hợp đồng.

Mục tiêu là **công khai ý nghĩa lỗi ổn định ở mức chức năng**, giữ chi tiết phụ thuộc trong phần triển khai và vẫn bảo toàn thông tin cần thiết để chẩn đoán. Đây là quyết định thiết kế exception của Java; không nhất thiết phải tạo một lớp bọc cho mọi exception.

## 1. Khi nào exception trở thành một phần hợp đồng API?

Hợp đồng của một phương thức không chỉ gồm tên, tham số và kiểu trả về. Người gọi còn cần biết:

- Thao tác có thể thất bại trong những tình huống nào: dữ liệu không hợp lệ, tài nguyên không tồn tại, truy cập bị từ chối, dịch vụ tạm thời không sẵn sàng...
- Lỗi nào người gọi có thể **xử lý có chủ đích** (sửa đầu vào, thử lại, hiển thị thông báo, hủy giao dịch)?
- Dấu hiệu ổn định để phân loại lỗi: kiểu exception công khai, mã lỗi/enum, hoặc một hợp đồng kết quả được tài liệu hóa.
- Các bảo đảm sau lỗi: thao tác có làm thay đổi trạng thái không, có an toàn khi thử lại không, dữ liệu có thể đã được ghi một phần không?

Ví dụ về chữ ký **minh họa, chưa phải chương trình Java hoàn chỉnh**:

```java
// API vô tình buộc client biết thư viện parser.
public Report importReport(String input) throws VendorParseException;
```

Nếu `VendorParseException` là checked exception được khai báo trong `throws`, mã gọi phải bắt hoặc khai báo tiếp nó; client có thể phải mang dependency chứa kiểu đó để biên dịch. Ngay cả khi exception là unchecked (không bắt buộc xuất hiện trong `throws`), nó vẫn tạo phụ thuộc ngầm nếu người dùng bắt cụ thể, kiểm tra bằng `instanceof` hoặc dựa vào tên lớp/thông điệp để quyết định.

Một kiểu `IOException` công khai **không tự động là thiết kế sai**: đây là abstraction phổ biến của JDK và có thể đúng hợp đồng của API thao tác I/O. Vấn đề là API làm lộ một **chi tiết triển khai không có chủ đích**.

## 2. Những con đường làm rò rỉ dependency

| Điểm rò rỉ | Ví dụ | Hậu quả |
| --- | --- | --- |
| Chữ ký checked exception | `throws VendorParseException` | Client bị ràng buộc với kiểu của dependency lúc biên dịch. |
| Unchecked exception không được chuyển nghĩa | Vendor SDK ném `VendorTimeoutException` đi xuyên qua API | Client có thể dựa vào lớp/vendor để phân loại lỗi. |
| Kiểu public chứa class phụ thuộc | `VendorError getLastError()` | Kiểu trả về cũng trở thành hợp đồng, kể cả khi `throws` sạch. |
| Mã lỗi hoặc message nội bộ | So khớp chuỗi `"VND-1042"` hoặc `getMessage()` | Nâng phiên bản, đổi ngôn ngữ hoặc đổi thư viện có thể làm hỏng logic. |
| Tài liệu, ví dụ, bài kiểm thử client | Hướng dẫn `catch (VendorXException e)` | Một hành vi ban đầu “vô tình” có thể được cộng đồng xem là bảo đảm tương thích. |
| Serialization của lỗi | Đưa nguyên tên lớp, stack trace hoặc `cause` ra JSON trả cho client | Lộ cấu trúc nội bộ, phụ thuộc và đôi khi thông tin nhạy cảm. |

`getCause()` hữu ích cho việc chẩn đoán, nhưng **không nên hứa rằng lớp cause từ vendor sẽ ổn định**. Nếu framework tự serialize toàn bộ throwable, cần kiểm soát rõ dữ liệu trả ra; chỉ “bọc” exception mà vẫn trả toàn bộ stack trace cho bên ngoài chưa tạo được ranh giới thông tin.

## 3. Thiết kế ranh giới lỗi có chủ đích

Đặt một lớp chuyển đổi lỗi tại nơi hệ thống giao tiếp với dependency: adapter, repository, gateway hoặc implementation của service. Chuyển **ngữ nghĩa** của lỗi, không chỉ đổi tên class.

1. **Liệt kê hành vi mà người gọi cần phân biệt.** Ví dụ `INVALID_FORMAT` (sửa dữ liệu), `RESOURCE_NOT_FOUND` (chọn tài nguyên khác), `TEMPORARY_UNAVAILABLE` (cân nhắc retry). Chỉ tách lỗi nếu người gọi cần hành động khác nhau.
2. **Chọn hợp đồng lỗi thuộc quyền sở hữu API.** Với Java, thường là exception chuyên biệt của miền nghiệp vụ, kèm enum/mã lỗi ổn định. Giữ loại lỗi đủ cụ thể để xử lý, tránh một `RuntimeException` mơ hồ cho mọi trường hợp.
3. **Bắt loại lỗi cụ thể gần điểm gọi thư viện.** Chuyển `VendorParseException` thành lỗi định dạng của API, `VendorTimeoutException` thành lỗi tạm thời, v.v. Tránh `catch (Exception)` rồi gán tất cả là lỗi đầu vào.
4. **Giữ nguyên nhân gốc để điều tra.** Truyền exception đã bắt vào constructor `super(message, cause)`, hoặc cơ chế `initCause` nếu có lý do phù hợp. Đừng ghép toàn bộ stack trace vào message.
5. **Tách thông điệp công khai khỏi thông tin chẩn đoán.** Thông điệp hướng người dùng cần ổn định, an toàn; log nội bộ mới chứa ngữ cảnh vận hành và cause. Không log cùng stack trace nhiều lần ở mọi lớp.
6. **Tài liệu hóa hành vi ổn định.** Nêu exception công khai, mã lỗi, điều kiện phát sinh, khả năng retry và trạng thái sau thất bại. Không hứa hành vi của vendor nếu không kiểm soát được.

Luồng thông thường:

```text
Client
  → API / Service công khai
      → Adapter của dependency
          → Thư viện bên ngoài ném VendorParseException
      ← Adapter bắt lỗi và chuyển thành ReportImportException(INVALID_FORMAT)
  ← Client xử lý theo mã lỗi của API
```

Không cần dịch tất cả exception: lỗi lập trình như `NullPointerException` do bug nội bộ nên được sửa, ghi nhận đúng nguyên nhân, thay vì ngụy trang thành “dữ liệu người dùng sai”.

## 4. Ví dụ Java 17 chạy độc lập: bọc lỗi và giữ cause

Giả sử một thư viện parser có thể ném `VendorParseException`. API nhập báo cáo công khai `ReportImportException` và mã `ReportError` do chính API sở hữu. Để ví dụ chạy độc lập không cần tải dependency, `VendorParser` bên dưới là **thư viện giả lập nội bộ**.

Tạo tệp `DependencyBoundaryDemo.java`:

```java
public class DependencyBoundaryDemo {

    // Phần public là hợp đồng lỗi mà client được phép dựa vào.
    public enum ReportError {
        INVALID_FORMAT,
        TEMPORARY_UNAVAILABLE
    }

    public static final class ReportImportException extends RuntimeException {
        private final ReportError code;

        public ReportImportException(
                ReportError code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public ReportError code() {
            return code;
        }
    }

    public static final class ReportService {
        private final VendorParser parser = new VendorParser();

        /**
         * @throws ReportImportException khi dữ liệu không thể nhập.
         */
        public int importReport(String text) {
            try {
                return parser.parseTotal(text);
            } catch (VendorParseException cause) {
                throw new ReportImportException(
                    ReportError.INVALID_FORMAT,
                    "Dữ liệu báo cáo không hợp lệ",
                    cause
                );
            }
        }
    }

    // Mô phỏng dependency: các lớp vendor KHÔNG xuất hiện
    // trong chữ ký public hoặc mã lỗi public của ReportService.
    private static final class VendorParser {
        int parseTotal(String text) {
            int separator = text.indexOf('=');
            if (separator < 0) {
                throw new VendorParseException("Missing '='");
            }
            try {
                return Integer.parseInt(text.substring(separator + 1));
            } catch (NumberFormatException cause) {
                throw new VendorParseException("Invalid integer", cause);
            }
        }
    }

    private static final class VendorParseException extends RuntimeException {
        VendorParseException(String message) {
            super(message);
        }

        VendorParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static void main(String[] args) {
        ReportService service = new ReportService();
        try {
            service.importReport("total=?");
        } catch (ReportImportException ex) {
            System.out.println(ex.code());
            System.out.println(ex.getMessage());
            System.out.println(ex.getCause() != null);
        }
    }
}
```

Biên dịch và chạy:

```bash
javac DependencyBoundaryDemo.java
java DependencyBoundaryDemo
```

Kết quả chuẩn:

```text
INVALID_FORMAT
Dữ liệu báo cáo không hợp lệ
true
```

Giải thích:

- `Integer.parseInt("?")` ném `NumberFormatException`, parser giả lập chuyển thành `VendorParseException`.
- `ReportService.importReport` bắt đúng lỗi của dependency và ném `ReportImportException` với `ReportError.INVALID_FORMAT`.
- Người gọi chỉ cần xử lý `ReportImportException` và `code()`. Việc thay `VendorParser` bằng parser khác không nhất thiết làm thay đổi hợp đồng API.
- `super(message, cause)` giữ chuỗi nguyên nhân để chẩn đoán; `getCause() != null` cho thấy thông tin này còn đó. Client **không nên kiểm tra kiểu cause vendor** như điều kiện nghiệp vụ.
- Trong thư viện thực, có thể tách các kiểu công khai thành những tệp riêng (`ReportService.java`, `ReportImportException.java`, `ReportError.java`) và đặt adapter vendor trong package nội bộ. Ví dụ dùng nested classes để chỉ cần một tệp tự chạy.

`TEMPORARY_UNAVAILABLE` mới được khai báo như một mã có thể mở rộng; ví dụ **chưa mô phỏng lỗi mạng** và không tự nhận đã xử lý timeout/retry.

## 5. Checked exception hay unchecked exception?

Cả hai đều có thể tạo ra hợp đồng tốt hoặc tạo ràng buộc ngoài ý muốn.

| Lựa chọn | Phù hợp khi | Lưu ý |
| --- | --- | --- |
| Checked exception ổn định của API | Caller được kỳ vọng phải quyết định cách phục hồi và compiler cần nhắc xử lý; ví dụ API đọc tệp công khai `IOException` hoặc API miền nghiệp vụ có `ImportFailedException` checked | `throws` là một phần chữ ký Java; thêm hoặc đổi checked exception có tác động đến mã nguồn client. |
| Unchecked exception ổn định của API | Không phải mọi caller đều có cách phục hồi hữu ích, hoặc caller xử lý lỗi tại boundary cao hơn | Vẫn tài liệu hóa loại lỗi/mã lỗi và hành vi; “unchecked” không đồng nghĩa với “không có hợp đồng”. |
| Đối tượng kết quả có phân nhánh lỗi | Lỗi là kết quả dự kiến, thường xuyên và quy trình nghiệp vụ cần buộc caller xét đủ các trường hợp | Không thay thế việc ném exception cho bug hoặc hỏng hạ tầng không mong đợi. |

Không nhất thiết biến mọi `IOException` thành một exception tự đặt tên. Với thư viện đọc stream, `IOException` có thể là chính abstraction ổn định; khi dùng SDK chuyên biệt để tải một tài liệu theo ID, một mã lỗi như `DOCUMENT_NOT_FOUND` hoặc `TEMPORARY_UNAVAILABLE` lại có thể giúp client hơn.

Đừng khai báo `throws Exception` chỉ để tránh chọn lỗi phù hợp. Nó làm API kém rõ ràng mà vẫn không bảo đảm tránh phụ thuộc; `throws` cũng không kiểm soát tất cả unchecked exception.

## 6. Bảng chuyển nghĩa dựa trên hành động của caller

Ví dụ bảng thiết kế **minh họa** cho một API nhập tài liệu; tên các exception vendor chỉ là ký hiệu mẫu:

| Nguyên nhân trong adapter | Exception/mã public có thể dùng | Người gọi nên làm gì? |
| --- | --- | --- |
| `VendorParseException` do nội dung sai cấu trúc | `INVALID_FORMAT` | Sửa tệp/đầu vào; retry nguyên xi thường vô ích. |
| `VendorNotFoundException` | `RESOURCE_NOT_FOUND` | Kiểm tra ID hoặc chọn tài nguyên khác. |
| `VendorTimeoutException` do timeout tạm thời | `TEMPORARY_UNAVAILABLE` | Retry có giới hạn, backoff nếu thao tác an toàn để lặp. |
| `VendorAuthenticationException` do cấu hình credentials của server | `INTERNAL_INTEGRATION_ERROR` hoặc một hợp đồng phù hợp | Thông báo lỗi vận hành; không gán nhầm là lỗi nhập liệu của người dùng. |
| `IllegalArgumentException` do API phát hiện tham số caller không hợp lệ | Có thể giữ `IllegalArgumentException` của JDK hoặc công khai lỗi đầu vào riêng | Sửa cách gọi API. |
| Bug, invariant hỏng, `NullPointerException` ngoài ý muốn | Thông thường để cơ chế xử lý lỗi nội bộ ghi nhận | Sửa code; không cố “dịch” thành lỗi nghiệp vụ bình thường. |

Phân loại dựa vào **ý nghĩa thực tế**, không dựa duy nhất vào tên exception. Chẳng hạn cùng một `IOException` có thể có nguyên nhân khác nhau; không thể mặc định mọi `IOException` đều an toàn để thử lại.

### Đặt exception ở lớp nào?

Một adapter hướng SDK có thể bắt kiểu vendor và dịch sang một exception kỹ thuật ổn định của ứng dụng. Service nghiệp vụ có thể tiếp tục dịch sang lỗi miền nghiệp vụ khi ngữ cảnh đủ rõ. Tuy nhiên, tránh bọc lồng nhiều lớp vô ích:

```text
VendorTimeoutException
  → PaymentGatewayUnavailableException
  → CheckoutTemporarilyUnavailableException
```

Hai lần dịch chỉ hợp lý nếu mỗi lớp bổ sung ý nghĩa khác nhau cho **người tiêu dùng của chính lớp đó**. Nếu service chỉ catch rồi đổi tên mà không bổ sung ngữ nghĩa, bớt một lớp bọc sẽ rõ và dễ truy vết hơn.

## 7. Các tình huống đặc biệt cần giữ đúng ngữ nghĩa

**Ngắt luồng và hủy tác vụ.** Nếu adapter bắt `InterruptedException`, thông thường cần khôi phục cờ interrupt bằng `Thread.currentThread().interrupt()` trước khi chuyển/thoát, trừ khi API chủ ý truyền tiếp `InterruptedException`. Không biến một tín hiệu hủy thành lỗi để retry vô hạn. `CancellationException` cũng nên được xử lý theo chính sách hủy của hệ thống. Đoạn sau là **mảnh minh họa**, giả định có kiểu `TaskAbortedException` do API định nghĩa:

```java
try {
    blockingCall();
} catch (InterruptedException cause) {
    Thread.currentThread().interrupt();
    throw new TaskAbortedException("Tác vụ đã bị ngắt", cause);
}
```

**Lỗi nghiêm trọng và bug.** Không dùng `catch (Throwable)` để bọc tất cả, vì nó bắt cả `Error` như `OutOfMemoryError`. Không dùng `catch (RuntimeException)` bao trùm tùy tiện và đổi mọi lỗi thành `INVALID_FORMAT`; cách đó che bug và phá ý nghĩa lỗi.

**Đóng tài nguyên.** Với `try-with-resources`, lỗi xảy ra khi đóng tài nguyên có thể nằm trong `getSuppressed()` của exception chính. Nếu chuyển exception, giữ `cause` sẽ bảo toàn đường truy vết tới suppressed exceptions gốc; nếu bỏ cause, thông tin này có thể mất. Đừng chỉ ghi `cause.getMessage()` rồi ném exception mới không có cause.

**Giao dịch và ghi một phần.** Một exception “service unavailable” không tự bảo đảm rằng thao tác chưa thực hiện thành công ở phía dịch vụ. Với API ghi dữ liệu, cần tài liệu hóa idempotency, mã yêu cầu, khả năng ghi một phần và điều kiện retry. Bọc lỗi transport thành mã ổn định là chưa đủ để cho phép retry an toàn.

**Thông tin nhạy cảm.** Message vendor có thể chứa URL nội bộ, SQL, tên bảng, token hoặc dữ liệu người dùng. Không trực tiếp dùng `cause.getMessage()` làm thông điệp trả cho client. Giữ cause cho log được kiểm soát quyền truy cập; thông điệp public cần ít thông tin nhạy cảm và vẫn đủ để caller hành động.

## 8. Tương thích khi thay dependency

Giả sử phiên bản đầu dùng parser A, phiên bản sau chuyển sang parser B:

- **Trước:** client bắt `ParserAException` rồi đọc error code do A định nghĩa.
- **Sau, nếu không có ranh giới:** client phải đổi import, cập nhật `catch`, có thể phải hiểu hệ mã lỗi của B.
- **Sau, với hợp đồng có chủ đích:** service vẫn ném `ReportImportException` với `INVALID_FORMAT`; chỉ code adapter và ánh xạ lỗi thay đổi.

Tuy nhiên, **giữ nguyên tên exception chưa đủ**. Cần giữ cả ngữ nghĩa: input nào bị từ chối, lỗi được ưu tiên khi có nhiều nguyên nhân, error code nào được trả, retry có an toàn không. Thay parser có thể làm thay đổi tiêu chí hợp lệ của input; nếu điều đó thuộc hợp đồng, vẫn phải đánh giá tương thích và cập nhật test/tài liệu.

Nếu API cũ **đã công khai** exception từ vendor và người dùng đang dựa vào nó, việc loại bỏ ngay có thể phá mã nguồn hoặc hành vi runtime. Nên đánh giá mức độ sử dụng, chính sách versioning, kế hoạch deprecation/migration và kiểm thử tương thích. Không thể giả định rằng chỉ đổi checked exception sang unchecked là nâng cấp tương thích hoàn toàn.

## 9. Kiểm thử đúng ranh giới

Thiết kế test tập trung vào những điều API hứa, không cố đóng băng chi tiết thư viện:

1. Khi đầu vào có lỗi định dạng, assert **kiểu lỗi công khai**, `code() == INVALID_FORMAT` và thông điệp public an toàn. Không assert tên/vendor message từ `getCause()`.
2. Với lỗi phụ thuộc tạm thời, giả lập timeout ở adapter, kiểm tra mã public và chính sách retry/idempotency do API tuyên bố.
3. Với lỗi hạ tầng không xác định hoặc bug, xác nhận chúng không bị gán sai thành lỗi dữ liệu người dùng.
4. Với lỗi có cause, xác nhận cause được bảo toàn để chẩn đoán nội bộ; không coi **kiểu cause cụ thể** là API promise.
5. Nếu đổi thư viện, chạy lại bộ contract test độc lập với vendor trước khi xuất bản phiên bản mới.
6. Kiểm tra boundary xuất JSON/log để chắc không vô tình hiển thị stack trace, token, câu SQL hoặc tên class phụ thuộc cho client ngoài.

Tài liệu Javadoc của API nên mô tả **điều kiện và hành động**. Ví dụ cho phương thức trong bài:

```java
/**
 * Nhập tổng tiền từ nội dung báo cáo.
 *
 * @param text nội dung dạng "total=123"
 * @return giá trị tổng tiền
 * @throws ReportImportException nếu không thể phân tích nội dung;
 *         code() là INVALID_FORMAT khi định dạng sai
 */
public int importReport(String text) {
    // Triển khai trong ReportService ở ví dụ đầy đủ phía trên.
    throw new UnsupportedOperationException("Minh họa chữ ký/Javadoc");
}
```

Đoạn Javadoc trên **chỉ minh họa tài liệu hợp đồng**, không phải bản triển khai thay thế cho chương trình đã chạy; `UnsupportedOperationException` là placeholder minh họa cú pháp trong snippet này.

## 10. Những sai lầm thường gặp

- **Bọc mọi exception thành `RuntimeException("Failed")`:** mất ngữ nghĩa, mất hướng xử lý; nếu thiếu cause còn mất chẩn đoán.
- **Gọi `cause.getMessage()` là message công khai:** làm format nội bộ trở thành hợp đồng và có thể lộ dữ liệu nhạy cảm.
- **Bắt `Exception` ở mọi tầng:** che bug, che lỗi ngắt, sinh nhiều lớp wrapper và stack trace khó đọc.
- **Tưởng unchecked exception không ảnh hưởng API:** client vẫn có thể bắt hoặc kiểm tra chúng; cam kết tài liệu cũng tạo ràng buộc.
- **Phát minh quá nhiều class lỗi:** tăng độ phức tạp nếu caller không có hành động khác nhau. Enum/mã lỗi ổn định đôi khi đủ.
- **Quên semantics retry:** timeout không cho biết server đã xử lý yêu cầu hay chưa.
- **Test phụ thuộc vendor:** assert exact text, subtype vendor hay stack trace khiến test vỡ khi thay thư viện dù hành vi public đúng.

## 11. Tự kiểm tra thiết kế API

Trước khi công bố một phương thức mới hoặc nâng cấp dependency, tự trả lời:

1. Nếu ngày mai đổi thư viện, người gọi có phải import/catch class khác, đổi phép so chuỗi hay đổi logic business không?
2. Người dùng API dựa vào những **lỗi có ý nghĩa nào** để xử lý, và chúng được mô tả ở đâu?
3. Chuỗi cause và suppressed exceptions có còn cho chẩn đoán không, và việc hiển thị cho client có an toàn không?
4. Hợp đồng có nói rõ trạng thái sau lỗi, điều kiện retry và lỗi do caller hay do hạ tầng không?
5. Có test xác nhận hành vi public **không phụ thuộc parser/SDK cụ thể** không?

Nguyên tắc cần nhớ: **thiết kế exception theo điều mà client cần biết và có thể xử lý; giữ loại lỗi, mã lỗi và message của dependency trong phần triển khai, trừ khi cố ý chọn chúng làm hợp đồng.**

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-api-design-010">Tác động tương thích khi thay đổi hợp đồng ngoại lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Tác động tương thích khi thay đổi hợp đồng ngoại lệ trong Java

> **Phạm vi:** Java Core → Exception → Thiết kế API. Bài này tập trung vào hậu quả đối với mã gọi, lớp triển khai, bản biên dịch cũ và hành vi vận hành khi một API thay đổi ngoại lệ có thể phát sinh.

## 1. Hợp đồng ngoại lệ là gì?

Hợp đồng ngoại lệ (*exception contract*) mô tả những tình huống thất bại mà người dùng API cần dự liệu, cách phát hiện chúng và cách khôi phục hoặc truyền lỗi lên tầng cao hơn. Hợp đồng được thể hiện qua nhiều nguồn:

- **Khai báo `throws`:** cho biết các *checked exception* mà trình biên dịch buộc bên gọi phải xử lý hoặc khai báo tiếp. Khai báo này là khả năng xảy ra, không bảo đảm ngoại lệ luôn phát sinh.
- **Kiểu ngoại lệ thực tế:** lớp exception và quan hệ kế thừa quyết định một khối `catch` có bắt được lỗi hay không.
- **Tài liệu API:** `@throws`, điều kiện lỗi, tính khôi phục được, và ý nghĩa của tham số hoặc trạng thái khi thao tác thất bại.
- **Hành vi quan sát được:** thời điểm phát sinh, việc giữ `cause`, trạng thái tài nguyên hoặc giao dịch sau lỗi, cơ chế retry/cancel, và các tín hiệu cho logging/monitoring.

Ví dụ, một phương thức đọc cấu hình có thể cam kết ném `IOException` khi truy cập dữ liệu thất bại. Khách hàng được quyền viết `catch (IOException e)` để thực hiện fallback. Nếu phiên bản mới đổi lỗi đó thành `IllegalStateException`, chữ ký phương thức có thể vẫn biên dịch, nhưng fallback cũ không còn hoạt động. Vì vậy đánh giá tương thích phải xét cả hợp đồng đã công bố lẫn hành vi thực tế.

### Checked và unchecked exception

- **Checked:** các lớp con của `Throwable` không thuộc nhánh `RuntimeException` hoặc `Error`; ví dụ `IOException`, `SQLException`. Chúng được kiểm tra tại thời điểm biên dịch theo quy tắc Java.
- **Unchecked:** `RuntimeException`, `Error` và các lớp con của chúng; ví dụ `IllegalArgumentException`, `IllegalStateException`. Trình biên dịch không yêu cầu `catch` hoặc `throws`, nhưng bên gọi vẫn có thể bắt.
- **Không dùng `Error` làm ngoại lệ nghiệp vụ.** Nó thường biểu thị lỗi nghiêm trọng ở mức runtime/hệ thống, không phải một đường xử lý lỗi thường lệ của API ứng dụng.

Một hợp đồng tốt nêu rõ **điều kiện phát sinh và lựa chọn xử lý**, thay vì chỉ liệt kê tên lớp ngoại lệ. `throws Exception` hoặc `throws Throwable` quá rộng thường làm người gọi khó quyết định phải xử lý điều gì.

## 2. Ba trục tương thích cần đánh giá riêng

| Loại tương thích | Câu hỏi phải trả lời | Ví dụ |
|---|---|---|
| **Mã nguồn (source)** | Biên dịch lại mã khách hàng hoặc lớp triển khai với API mới có thành công không? | Thêm `throws IOException` khiến một phương thức gọi cũ thiếu `catch`. |
| **Nhị phân (binary)** | Bản `.class` đã biên dịch có liên kết/chạy được với thư viện mới không? | Chỉ thay đổi mệnh đề `throws` không làm thay đổi JVM method descriptor. |
| **Hành vi (behavioral)** | Các luồng xử lý lỗi, retry, fallback, ghi log và phản hồi hệ thống có giữ nguyên ý nghĩa không? | Ngoại lệ cũ được bọc thành một runtime exception mà không giữ `cause`. |

**Điểm dễ nhầm:** theo Java Language Specification (JLS §13.4.21), thay đổi riêng mệnh đề `throws` của phương thức hoặc constructor **không phá vỡ tương thích nhị phân**; cơ chế kiểm tra checked exception diễn ra lúc biên dịch. Điều đó **không** có nghĩa thay đổi an toàn về mã nguồn hoặc hành vi. Nếu thay cả tên phương thức, tham số, kiểu trả về, khả năng truy cập, lớp exception hay logic thực thi, phải đánh giá các thay đổi đó độc lập.

## 3. Ma trận tác động của các thay đổi thường gặp

| Thay đổi ở API công khai | Khi biên dịch lại bên gọi/triển khai | Với nhị phân cũ nếu **chỉ đổi `throws`** | Rủi ro hành vi |
|---|---|---|---|
| **Thêm checked exception** `throws IOException` | Bên gọi phải bắt/khai báo; method reference hoặc lambda liên quan có thể không hợp lệ | Vẫn tương thích nhị phân | Đường lỗi mới có thể chưa được xử lý |
| **Xóa checked exception** khỏi `throws` | `catch` chuyên biệt có thể trở thành không hợp lệ; lớp override khai báo ngoại lệ cũ có thể lỗi | Vẫn tương thích nhị phân | Khách hàng không biết lỗi được đổi thành gì |
| **Đổi từ subtype sang supertype checked** (ví dụ `FileNotFoundException` → `IOException`) | Mở rộng phạm vi lỗi cần xử lý; caller bắt loại hẹp có thể thiếu xử lý | Vẫn tương thích nhị phân | Caller không phân biệt được một số trường hợp |
| **Thu hẹp từ supertype sang subtype checked** | Một số lớp override đang khai báo loại rộng sẽ không biên dịch lại; bên gọi có `catch` phù hợp thường vẫn hợp lệ | Vẫn tương thích nhị phân | Có thể thay đổi ý nghĩa cam kết lỗi |
| **Thêm/đổi sang unchecked exception** | Thường không cần khai báo mới, nhưng mã nguồn của `catch` checked cũ có thể lỗi nếu checked bị xóa | Nếu chỉ sửa `throws`, vẫn liên kết; logic ném lỗi là vấn đề riêng | Fallback, retry, thống kê lỗi, điều khiển giao dịch có thể thay đổi |
| **Giữ kiểu exception nhưng đổi điều kiện ném** | Có thể vẫn biên dịch | Có thể vẫn liên kết | Phá vỡ hợp đồng ngữ nghĩa dù không hề đổi chữ ký |
| **Đổi thông tin `cause`, error code hoặc loại wrapper** | Thường không được trình biên dịch cảnh báo | Thường không làm lỗi liên kết nếu không đổi cấu trúc công khai | Debug, phân loại lỗi và chính sách retry có thể sai |

Bảng trên không phải cam kết rằng mọi thay đổi đều an toàn nhị phân: phạm vi cột nhị phân chỉ là **thay đổi đơn thuần ở `throws`**. Nếu xóa class ngoại lệ công khai, đổi hệ phân cấp lớp, bỏ phương thức hoặc đổi descriptor, có thể gây lỗi ở lúc biên dịch, liên kết hoặc chạy.

## 4. Thêm checked exception: lỗi xuất hiện từ vị trí gọi

Giả sử phiên bản 1 công bố:

~~~java
// Trích đoạn khai báo API phiên bản 1, không phải chương trình độc lập.
public String fetch(String key);
~~~

Phiên bản 2 đổi thành:

~~~java
// Trích đoạn khai báo API phiên bản 2.
public String fetch(String key) throws java.io.IOException;
~~~

Mã khách hàng từng viết:

~~~java
String value = service.fetch("theme");
System.out.println(value);
~~~

Khi biên dịch lại với API phiên bản 2, trình biên dịch phát hiện checked exception chưa được xử lý. Bên gọi phải đặt đoạn lệnh trong `try/catch` hoặc thêm `throws IOException` vào phương thức bao quanh; nếu là constructor, cũng phải xét `throws` của constructor đó. Chỉ “thêm khai báo để biên dịch qua” có thể tiếp tục đẩy lỗi lên các tầng chưa sẵn sàng xử lý.

Sự thay đổi còn lan sang biểu thức lambda hoặc method reference:

~~~java
// Minh họa: Supplier.get() không khai báo checked exception.
java.util.function.Supplier<String> supplier = () -> service.fetch("theme");
~~~

Biểu thức trên có thể hợp lệ với phiên bản 1 nhưng không còn hợp lệ nếu `fetch` của phiên bản 2 ném `IOException`. Một functional interface tự định nghĩa với `run() throws IOException` có thể biểu đạt hợp đồng thích hợp hơn, hoặc API cần cung cấp một nhánh chuyển đổi lỗi có chủ đích.

## 5. Xóa hoặc thu hẹp checked exception cũng có thể phá mã nguồn

Phiên bản 1:

~~~java
// Trích đoạn API, không phải chương trình độc lập.
public String read() throws java.io.IOException;
~~~

Bên gọi:

~~~java
// Trích đoạn mã phía caller.
try {
    return api.read();
} catch (java.io.IOException ex) {
    return "default";
}
~~~

Nếu phiên bản 2 đổi thành `public String read()` và thân `try` không có lệnh nào khác có thể ném `IOException`, `catch (IOException)` sẽ báo lỗi biên dịch vì checked exception này không thể phát sinh từ khối `try` theo phân tích tĩnh. Đây là ví dụ điển hình của thay đổi tưởng như “giảm ràng buộc” nhưng lại phá mã nguồn khách hàng.

Ngoài caller, còn có **hệ phân cấp kế thừa**. Quy tắc override: phương thức ghi đè không được khai báo checked exception rộng hơn các loại được phương thức bị ghi đè cho phép. Ví dụ:

~~~java
// Các đoạn này biểu diễn API cũ và lớp triển khai đang hợp lệ.
interface Loader {
    String load() throws java.io.IOException;
}

class DiskLoader implements Loader {
    @Override
    public String load() throws java.io.IOException {
        throw new java.io.IOException("unavailable");
    }
}
~~~

Nếu ở phiên bản sau `Loader.load()` bỏ `throws IOException` hoặc thu hẹp chỉ còn `FileNotFoundException`, thì `DiskLoader.load()` ở trên **không thể biên dịch lại** nếu vẫn khai báo `IOException`. Ngược lại, thêm một checked exception được cho phép vào khai báo của interface không buộc implementation hiện tại phải ném nó, nhưng bên **gọi** qua interface vẫn phải cập nhật.

Quy tắc tương tự áp dụng cho abstract class, phương thức override và functional interface. Đối với constructor, việc thay đổi `throws` tác động trực tiếp tới biểu thức `new` và các constructor gọi `super(...)`.

## 6. Đổi lớp exception hoặc chuyển checked thành unchecked

Ví dụ API truy xuất dữ liệu từng khai báo `SQLException`, sau đó chuyển sang `DataAccessException extends RuntimeException`. Chữ ký mới có thể ngắn gọn hơn, song một caller từng `catch (SQLException)` không tự động bắt được `DataAccessException`. Khi biên dịch lại, `catch` checked cũ có thể không còn hợp lệ; với bản caller đã biên dịch, ngoại lệ mới có thể đi xuyên qua các handler cũ.

Để quyết định việc chuyển đổi, cần trả lời:

1. Caller có **cách khôi phục cụ thể** từ checked exception ban đầu không (retry, chọn nguồn dự phòng, yêu cầu người dùng sửa đầu vào)?
2. Các điểm chặn lỗi có dựa vào **kiểu cụ thể** hay quan hệ kế thừa của exception không?
3. Khi bọc ngoại lệ, `cause` có còn truy xuất được để điều tra lỗi và phân loại thông báo không?
4. Lỗi xảy ra **trước hay sau** tác dụng phụ (ghi file, thay đổi trạng thái, commit giao dịch)? Người gọi có thể thử lại an toàn không?
5. API đã công bố hành vi gì trong Javadoc và tài liệu versioning?

Nếu lựa chọn unchecked exception vì caller không thể xử lý hợp lý tại vị trí gọi, hãy dùng một kiểu đủ rõ nghĩa, chẳng hạn `UncheckedIOException` cho lỗi I/O được chuyển đổi, hoặc một exception miền nghiệp vụ có `cause`. Không nên đổi mọi lỗi thành `RuntimeException` chung, vì caller sẽ mất khả năng phân nhánh theo nguyên nhân.

### Chương trình Java 17+ chạy được: chuyển đổi lỗi mà giữ nguyên nguyên nhân

Ví dụ dưới đây minh họa **một API được thiết kế công khai để trả lỗi unchecked**. Nếu đang di chuyển từ API checked đã phát hành, cần thêm lộ trình tương thích ở phần 8; chỉ áp dụng lớp bọc không tự bảo đảm tương thích.

Lưu toàn bộ vào tệp `ExceptionContractDemo.java`:

~~~java
import java.io.IOException;

public class ExceptionContractDemo {
    static final class ConfigurationException extends RuntimeException {
        ConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static String loadRaw(boolean fail) throws IOException {
        if (fail) {
            throw new IOException("storage unavailable");
        }
        return "mode=prod";
    }

    static String load(boolean fail) {
        try {
            return loadRaw(fail);
        } catch (IOException cause) {
            throw new ConfigurationException("cannot load config", cause);
        }
    }

    public static void main(String[] args) {
        System.out.println(load(false));

        try {
            load(true);
        } catch (ConfigurationException ex) {
            System.out.println(ex.getClass().getSimpleName());
            System.out.println(ex.getCause().getClass().getSimpleName());
            System.out.println(ex.getMessage());
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ExceptionContractDemo.java
java ExceptionContractDemo
~~~

Đầu ra chuẩn chính xác:

~~~text
mode=prod
ConfigurationException
IOException
cannot load config
~~~

Ở đây `loadRaw` có hợp đồng checked `IOException`, còn `load` đưa ra hợp đồng unchecked `ConfigurationException`. Constructor `super(message, cause)` giữ được chuỗi nguyên nhân. Handler bên gọi của `load` cần nhận đúng `ConfigurationException`; nó không thể thay thế máy móc cho handler của một API checked cũ.

## 7. Các cam kết hành vi dễ bị bỏ sót

### 7.1. Phạm vi bắt lỗi và thứ tự `catch`

`catch (IOException)` bắt `IOException` cùng các lớp con, nhưng không bắt một `RuntimeException` không liên quan. Khi đổi ngoại lệ, nhánh fallback có thể không chạy. Với nhiều `catch`, phải đặt loại cụ thể trước loại rộng để tránh nhánh không thể tới; kiểm tra lại các khai báo trong `multi-catch` khi thay hệ phân cấp exception.

### 7.2. Nguyên nhân gốc và dữ liệu chẩn đoán

Cần giữ `cause` khi chuyển đổi ngoại lệ; tránh bắt rồi ném exception mới chỉ mang thông điệp và làm mất stack trace gốc. Thông điệp exception thường phục vụ chẩn đoán và không nên trở thành định dạng máy đọc ổn định nếu API chưa cam kết điều đó. Nếu caller cần phân loại bền vững, dùng **kiểu exception**, mã lỗi công khai hoặc thuộc tính có kiểu rõ ràng.

### 7.3. Ngắt luồng và hủy tác vụ

`InterruptedException` là tín hiệu kiểm soát việc chờ/hủy tác vụ. Nếu một API bắt rồi chuyển nó sang unchecked exception, cần cân nhắc khôi phục trạng thái interrupt để tầng ngoài nhận biết:

~~~java
// Đoạn minh họa nằm trong một phương thức đang bắt InterruptedException.
catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    throw new IllegalStateException("operation interrupted", ex);
}
~~~

Nếu API có thể khai báo `throws InterruptedException`, truyền ngoại lệ lên thường cho phép caller quyết định hủy tác vụ chính xác hơn. Không nuốt tín hiệu interrupt và tiếp tục xử lý như thể không có chuyện gì xảy ra.

### 7.4. Đóng tài nguyên và ngoại lệ bị suppress

Với `try-with-resources`, ngoại lệ phát sinh ở thân khối `try` thường là lỗi chính; ngoại lệ từ `close()` có thể được lưu trong `Throwable.getSuppressed()`. Việc chuyển đổi lỗi không nên làm mất thông tin này. Đổi khai báo `throws` của `AutoCloseable.close()` hoặc thay loại tài nguyên có thể kéo theo yêu cầu xử lý checked exception tại nơi sử dụng.

### 7.5. Bất đồng bộ, reflection và framework

Trong `CompletableFuture`, thất bại có thể được truyền qua exceptional completion; `join()` thường ném `CompletionException`, còn `get()` dùng `ExecutionException` để bao nguyên nhân. Chuyển một thao tác đồng bộ sang bất đồng bộ vì thế thay cả **kênh** nhận lỗi, không chỉ tên exception.

Reflection và một số framework quan sát được danh sách exception khai báo qua `Method.getExceptionTypes()`; cơ chế gọi phản xạ có thể bọc lỗi trong `InvocationTargetException`. Proxy, interceptor, logging, mapper HTTP, cấu hình retry và giao dịch cũng có thể rẽ nhánh theo loại exception. Một thay đổi không bị JVM từ chối vẫn có thể làm các tầng tích hợp vận hành khác trước.

## 8. Chiến lược tiến hóa API giảm nguy cơ phá vỡ khách hàng

### Cách 1: giữ API cũ và thêm entry point mới

Khi muốn cung cấp bản unchecked, một chiến lược rõ ràng là **giữ nguyên phương thức cũ** (tên, tham số, kiểu trả về và hợp đồng lỗi), đồng thời thêm phương thức mới khác tên. Ví dụ lớp thư viện đã có `read(Path)`:

~~~java
// Trích đoạn trong một lớp thư viện, với import java.io.*,
// java.nio.file.Files và java.nio.file.Path.
public byte[] read(Path path) throws IOException {
    return Files.readAllBytes(path);
}

public byte[] readUnchecked(Path path) {
    try {
        return read(path);
    } catch (IOException ex) {
        throw new UncheckedIOException("cannot read " + path, ex);
    }
}
~~~

Caller cũ vẫn gọi `read` và xử lý `IOException`; caller mới có thể chủ động chọn `readUnchecked`. Hai nhánh nên được kiểm thử để bảo đảm cùng một nguyên nhân lỗi không bị diễn giải khác nhau ngoài chủ đích.

### Cách 2: mở rộng bằng loại con có ý nghĩa

Nếu API khai báo `throws IOException`, việc implementation ném một **subtype của `IOException`** có thể vẫn đi qua handler bắt `IOException` hiện hữu. Đây thường là lựa chọn ít gây xáo trộn hơn ném một ngoại lệ checked không liên quan. Tuy nhiên caller bắt subtype riêng, theo dõi loại lỗi cụ thể hoặc có chính sách retry vẫn có thể bị ảnh hưởng; hãy tài liệu hóa điều kiện mới.

### Cách 3: lộ trình deprecate và phiên bản hóa

Nếu bắt buộc thay đổi hợp đồng cũ, nên công bố trước điều kiện lỗi, đường migration, ví dụ thay thế `catch`, mốc deprecation và phiên bản áp dụng. Với thay đổi lớn khó tránh phá mã nguồn, cân nhắc phát hành phiên bản API mới có tên hoặc package riêng, hoặc major version theo chính sách tương thích của thư viện. Không đánh dấu `@Deprecated` là đã giải quyết xong vấn đề: annotation chỉ báo cho người dùng, không tự bảo đảm hai hành vi cùng đúng.

### Những việc không nên dùng để “sửa nhanh”

- Thêm `throws Exception` khắp tầng ứng dụng: code có thể qua biên dịch nhưng hợp đồng càng khó kiểm soát.
- Đổi hết checked exception thành `RuntimeException` rồi giả định không có breaking change.
- Bắt `Exception` hay `Throwable` ở mọi nơi để che sự không tương thích.
- Bắt ngoại lệ rồi `return null` hoặc `Optional.empty()` mà không phân biệt **không có dữ liệu** với **thao tác thất bại**, trừ khi API đã cam kết rõ.
- Bỏ `cause`, bỏ `suppressed` hoặc nuốt `InterruptedException` khi làm wrapper.
- Phụ thuộc vào nội dung `getMessage()` làm protocol ổn định nếu thông điệp chỉ phục vụ con người.

## 9. Quy trình kiểm tra tương thích trước khi phát hành

1. **Liệt kê hợp đồng cũ và mới:** từng phương thức/constructor công khai, checked exception trong `throws`, runtime exception được tài liệu hóa, điều kiện lỗi, lớp exception và subtype được xuất bản.
2. **Đối chiếu bên dùng:** nơi gọi trực tiếp, `catch` chuyên biệt, method reference, lambda, constructor chaining, lớp `extends` hoặc `implements`, xử lý giao dịch và retry.
3. **Biên dịch lại mã khách hàng:** đặc biệt kiểm tra các trường hợp thêm checked exception, xóa checked exception có `catch` riêng, và thu hẹp hợp đồng của phương thức bị ghi đè.
4. **Thử nhị phân cũ với thư viện mới:** dùng artifact của caller đã biên dịch trước thay đổi. Phân biệt lỗi liên kết JVM với lỗi hành vi khi ngoại lệ thực tế thay đổi; có thể xem method descriptor bằng `javap -s` và metadata khai báo ngoại lệ bằng `javap -v`.
5. **Kiểm thử hành vi thất bại:** kiểm tra đúng class/cause của exception, tài nguyên được đóng, interrupt flag, side effect, fallback, retry, logs và phản hồi public-facing. Bao gồm nhiều exception gần nhau trong hệ phân cấp.
6. **Công bố migration:** nêu điều kiện thay đổi, phạm vi ảnh hưởng, cách thay handler và lịch loại bỏ API cũ. Chỉ xác nhận tương thích theo những trục đã kiểm thử.

### Bảng quyết định nhanh

| Tình huống | Hướng xử lý nên ưu tiên |
|---|---|
| Cần thêm một nguyên nhân lỗi checked cho API đã phát hành | Cân nhắc giữ hợp đồng cũ và cung cấp API mới, hoặc dùng subtype phù hợp với loại đã công bố; đánh giá caller trước khi thêm `throws`. |
| Muốn đơn giản hóa caller bằng unchecked exception | Giữ API checked cũ và thêm phương thức mới có tên, ngữ nghĩa và `cause` rõ ràng. |
| Muốn bỏ một ngoại lệ không còn phát sinh | Kiểm tra `catch` checked cũ và các lớp override; tài liệu hóa hành vi thay thế, tránh thay trực tiếp nếu gây lỗi biên dịch trên khách hàng. |
| Muốn thu hẹp `throws` ở interface | Kiểm tra tất cả implementation hiện có và khả năng họ biên dịch lại. |
| Chỉ đổi văn bản thông báo lỗi | Xác nhận không có hệ thống đã được cam kết dùng văn bản đó như mã máy đọc; ưu tiên error code ổn định. |
| Code vẫn liên kết nhưng fallback không chạy | Kiểm tra loại ngoại lệ thực tế, wrapper và `cause`, không kết luận an toàn chỉ từ kết quả build. |

## 10. Ghi nhớ

- **`throws` thuộc hợp đồng biên dịch của checked exception; loại exception thực tế và quy tắc xử lý tạo nên hợp đồng hành vi.**
- **Tương thích nhị phân không đồng nghĩa tương thích mã nguồn hoặc hành vi.** Chỉ đổi mệnh đề `throws` được JLS bảo đảm không phá liên kết nhị phân, nhưng khách hàng biên dịch lại vẫn có thể lỗi.
- **Xóa một checked exception cũng có thể là breaking change** vì `catch` cũ và override.
- Khi cần cải tiến API đã được sử dụng rộng rãi, giữ đường đi cũ hoạt động và công bố một đường mới rõ ràng thường dễ kiểm soát hơn đổi loại exception đột ngột.
- Đánh giá cuối cùng phải có cả **biên dịch lại**, **chạy nhị phân cũ**, và **test hành vi lỗi**.

### Tham chiếu tiêu chuẩn

- [JLS §11.2 — Compile-Time Checking of Exceptions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-11.html#jls-11.2)
- [JLS §8.4.8.3 — Requirements in Overriding and Hiding](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.4.8.3)
- [JLS §13.4.21 — Method and Constructor Throws](https://docs.oracle.com/javase/specs/jls/se21/html/jls-13.html#jls-13.4.21)


</details>

- [Quay lại đầu trang](#back-to-top)
