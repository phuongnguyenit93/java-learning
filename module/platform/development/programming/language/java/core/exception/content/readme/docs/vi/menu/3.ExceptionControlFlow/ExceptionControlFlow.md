<a id="back-to-top"></a>

# Luồng thực thi

## Menu
- [Hoàn tất bình thường và hoàn tất đột ngột](#exception-control-flow-001)
- [Ngoại lệ phát sinh từ mã Java, API hoặc JVM](#exception-control-flow-002)
- [Dừng phần biểu thức hoặc câu lệnh chưa hoàn tất](#exception-control-flow-003)
- [Lan truyền ngoại lệ dọc theo ngăn xếp lời gọi](#exception-control-flow-004)
- [Tìm khối catch phù hợp gần nhất](#exception-control-flow-005)
- [Vị trí tiếp tục thực thi sau khi xử lý](#exception-control-flow-006)
- [Ngoại lệ chưa được bắt và việc kết thúc luồng hiện tại](#exception-control-flow-007)
- [Giới hạn giữa ngoại lệ đồng bộ và ngoại lệ bất đồng bộ của JVM](#exception-control-flow-008)
- [Giải phóng monitor khi rời synchronized do ngoại lệ](#exception-control-flow-009)

## <a id="exception-control-flow-001">Hoàn tất bình thường và hoàn tất đột ngột</a>

<details>
<summary>Xem chi tiết</summary>

# Hoàn tất bình thường và hoàn tất đột ngột trong Java

Khi chạy một câu lệnh, Java không chỉ quan tâm đến kết quả tính toán mà còn quan tâm **cách câu lệnh kết thúc**. Java Language Specification (JLS) gọi hai khả năng này là *normal completion* (**hoàn tất bình thường**) và *abrupt completion* (**hoàn tất đột ngột**). Khái niệm này giải thích chính xác vì sao `return` rời phương thức, `break` rời vòng lặp, `continue` bỏ qua phần còn lại của vòng lặp, `yield` cung cấp giá trị cho `switch expression`, ngoại lệ làm thay đổi đường đi và `finally` vẫn được thực thi trong quá trình chuyển quyền điều khiển.

Điểm quan trọng: **“đột ngột” không đồng nghĩa với “đã xảy ra ngoại lệ”**. Một câu lệnh `return 10;` cũng hoàn tất đột ngột theo thuật ngữ của ngôn ngữ, dù phương thức có thể trả về giá trị hoàn toàn thành công.

## 1. Hai kiểu hoàn tất là gì?

### Hoàn tất bình thường — normal completion

Một câu lệnh hoàn tất bình thường khi nó thực hiện xong các bước được quy định cho đường đi bình thường, không có sự kiện chuyển quyền điều khiển đột ngột. Quyền điều khiển thường tiếp tục đến câu lệnh kế tiếp trong cùng khối.

```java
int count = 0;
count++;
System.out.println(count); // In ra 1
```

Ba câu lệnh trên lần lượt hoàn tất bình thường nếu không có ngoại lệ phát sinh. Đoạn này chỉ minh họa trong thân một phương thức; không phải chương trình độc lập. Một khối `{ ... }` cũng hoàn tất bình thường nếu toàn bộ các câu lệnh thực sự được thực hiện trong khối hoàn tất theo cách cho phép khối kết thúc bình thường.

### Hoàn tất đột ngột — abrupt completion

Một câu lệnh hoàn tất đột ngột khi nó không đi hết luồng thực thi bình thường vì xuất hiện **một lý do chuyển quyền điều khiển**. Lý do đó được truyền qua những câu lệnh hoặc khối bao quanh cho đến khi gặp cấu trúc xử lý nó; khi đó cấu trúc bên ngoài có thể tiếp tục hoàn tất bình thường.

JLS §14.1 phân loại tám dạng lý do:

| Lý do | Diễn giải |
| --- | --- |
| `break;` | Chuyển quyền điều khiển ra khỏi cấu trúc `switch` statement hoặc vòng lặp gần nhất phù hợp. |
| `break label;` | Rời câu lệnh được gắn nhãn phù hợp. |
| `continue;` | Chuyển sang lượt lặp tiếp theo của vòng lặp gần nhất phù hợp. |
| `continue label;` | Chuyển sang lượt lặp tiếp theo của vòng lặp có nhãn phù hợp. |
| `return;` | Hoàn tất đột ngột với `return;`, trả quyền điều khiển từ phương thức `void`, constructor hoặc thân lambda phù hợp; không trả về giá trị. |
| `return value;` | Trả về một giá trị từ phương thức/lambda có kiểu trả về phù hợp. |
| `throw exception;` | Ném đối tượng ngoại lệ, bao gồm ngoại lệ do JVM phát sinh trong lúc đánh giá biểu thức. |
| `yield value;` | Chuyển giá trị từ khối của `switch expression` đến toàn bộ biểu thức `switch`. |

**Lưu ý cú pháp:** Hai dạng `break`, hai dạng `continue` và hai dạng `return` là các lý do phân biệt theo JLS; `throw` và `yield` có một dạng lý do tương ứng. Nhãn của `continue label` phải gắn với một vòng lặp bao quanh; nhãn của `break label` có thể gắn với câu lệnh bao quanh phù hợp. Không thể tùy tiện dùng chúng ngoài cấu trúc hợp lệ.

Trong trường hợp `throw`, câu lệnh có thể ném tường minh hoặc một biểu thức như `10 / zero` có thể gây `ArithmeticException`. Khi đánh giá **biểu thức**, theo JLS, hoàn tất đột ngột chỉ xảy ra vì ngoại lệ được ném; `break`, `continue`, `return` và `yield` là cơ chế của **câu lệnh**.

## 2. Quy tắc lan truyền: từ câu lệnh con đến cấu trúc bao quanh

Xem luồng thông thường:

```text
Bắt đầu khối
    │
    ▼
Câu lệnh A ── hoàn tất bình thường ──► Câu lệnh B ──► Câu lệnh C
                                           │
                                           └── hoàn tất đột ngột
                                                     │
                                                     ▼
                                     Tìm cấu trúc xử lý lý do
                                          │             │
                                          ▼             ▼
                                    Được xử lý      Tiếp tục lan ra
                                          │             │
                                          ▼             ▼
                                    Có thể chạy     Có thể thoát khỏi
                                    phần tiếp        phạm vi hiện tại
```

Nếu một biểu thức được dùng trong câu lệnh ném ngoại lệ, câu lệnh bao quanh cũng hoàn tất đột ngột vì cùng lý do đó, trừ khi có quy tắc cụ thể xử lý ngoại lệ. Các bước tiếp theo vốn thuộc đường chạy bình thường sẽ bị bỏ qua.

```java
System.out.println("Trước");
int value = Integer.parseInt("abc");
System.out.println("Sau");
```

Giả sử đoạn này nằm trong phương thức có đường gọi xử lý ngoại lệ: dòng đầu in `Trước`; `parseInt` ném `NumberFormatException`, vì vậy câu lệnh gán và phần còn lại của khối không hoàn tất bình thường. Dòng in `Sau` **không được chạy trên đường đi đó**, trừ khi ngoại lệ được bắt ở một cấu trúc có thể đưa quyền điều khiển trở lại phía sau khối `try` thích hợp. Java không tự động quay lại vị trí đang lỗi để tiếp tục nửa chừng biểu thức.

Tuy vậy, phải xét **mỗi mức cấu trúc riêng**. Một câu lệnh `break` làm thân vòng lặp hoàn tất đột ngột, nhưng vòng lặp có thể **xử lý `break` rồi hoàn tất bình thường**. Tương tự, câu lệnh `return` hoàn tất đột ngột, nhưng lời gọi phương thức bên ngoài có thể đánh giá bình thường và nhận giá trị trả về. Đây là nguồn nhầm lẫn rất phổ biến khi đọc thuật ngữ JLS.

## 3. `break`, `continue` và `yield`: đột ngột ở trong, bình thường ở ngoài

Với `for` hoặc `while`:

- `break` thoát vòng lặp đang nhận lý do `break`; câu lệnh sau vòng lặp có thể chạy tiếp.
- `continue` kết thúc lượt lặp hiện tại; vòng lặp tiếp tục phần cập nhật/điều kiện theo quy tắc của loại vòng lặp.
- Một ngoại lệ **không được bắt** sẽ không được vòng lặp thông thường tự xử lý; nó tiếp tục lan ra.
- `yield` dùng để tạo giá trị từ khối của `switch expression`. Câu lệnh `yield` hoàn tất đột ngột, nhưng `switch expression` nhận giá trị và có thể hoàn tất bình thường. `yield` không tương đương `return` khỏi phương thức.

Ví dụ:

```java
int sum = 0;
for (int i = 0; i < 5; i++) {
    if (i == 1) continue;
    if (i == 3) break;
    sum += i;
}
System.out.println(sum); // In ra 2: cộng i=0 và i=2
```

Các lần `continue` và `break` thuộc đường điều khiển dự kiến. Chúng không tự tạo `Throwable` và không kích hoạt `catch`.

Trong `switch expression`:

```java
int level = 2;
int points = switch (level) {
    case 1 -> 10;
    default -> {
        yield 20;
    }
};
System.out.println(points); // In ra 20
```

Lệnh `yield 20;` kết thúc đột ngột ở cấp câu lệnh, còn biểu thức `switch` cấp ngoài hoàn tất bình thường với giá trị `20`. Mũi tên `case 1 -> 10` cũng cung cấp giá trị nhưng không yêu cầu viết từ khóa `yield`. Hai ví dụ này là đoạn mã đặt bên trong một phương thức, không phải tệp Java độc lập.

## 4. `return` và `throw`: giống nhau ở việc rời đường chạy, khác nhau ở nguyên nhân

`return` truyền kết quả về bên gọi và kết thúc lần thực thi phương thức hiện tại theo cách được quy định. `throw` kích hoạt tìm `catch` tương thích trong các khối đang bao quanh, hoặc lan ngoại lệ ra khỏi lời gọi nếu không được xử lý.

| Tình huống | Câu lệnh bên trong | Kết quả ở cấp ngoài |
| --- | --- | --- |
| `return 5;` trong phương thức `int` | Hoàn tất đột ngột với lý do trả giá trị 5. | Biểu thức gọi phương thức có thể nhận 5 và hoàn tất bình thường. |
| `throw new IllegalArgumentException(...)` | Hoàn tất đột ngột với lý do ném ngoại lệ. | Nếu không có `catch` phù hợp, lời gọi cũng hoàn tất đột ngột. |
| `throw ...` bên trong `try` được `catch` xử lý, rồi `catch` kết thúc bình thường | Khối `try` đầu tiên hoàn tất đột ngột. | Toàn bộ cấu trúc `try-catch` có thể hoàn tất bình thường. |
| `return` trong `try` có `finally` | `return` vẫn là lý do hoàn tất đột ngột của khối `try`. | `finally` chạy trước khi phương thức thực sự chuyển quyền điều khiển ra ngoài, nếu điều kiện thực thi cho phép. |

Một điều cần phân biệt: **kết thúc một lời gọi phương thức** và **hoàn tất một câu lệnh `return`** không phải cùng cấp phân tích. Câu lệnh `return` có lý do *abrupt completion*, nhưng lời gọi có thể đem lại kết quả bình thường cho biểu thức của bên gọi.

## 5. Chương trình Java 17+: quan sát nhiều kiểu hoàn tất

Lưu nội dung sau thành `FlowCompletionDemo.java`:

```java
public class FlowCompletionDemo {
    static void normalBlock() {
        int count = 0;
        {
            count++;
        }
        System.out.println("normal=" + count);
    }

    static int returnWithFinally() {
        try {
            System.out.println("in try");
            return 10;
        } finally {
            System.out.println("in finally");
        }
    }

    static void catchAndContinue() {
        try {
            throw new IllegalArgumentException("bad");
        } catch (IllegalArgumentException ex) {
            System.out.println("caught=" + ex.getMessage());
        }
        System.out.println("after catch");
    }

    static void loopAndYield() {
        int sum = 0;
        for (int i = 0; i < 5; i++) {
            if (i == 1) continue;
            if (i == 3) break;
            sum += i;
        }

        int level = 2;
        int points = switch (level) {
            case 1 -> 10;
            default -> {
                yield 20;
            }
        };
        System.out.println("loop=" + sum + ", switch=" + points);
    }

    public static void main(String[] args) {
        normalBlock();
        System.out.println("method result=" + returnWithFinally());
        catchAndContinue();
        loopAndYield();
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```bash
javac -encoding UTF-8 FlowCompletionDemo.java
java FlowCompletionDemo
```

Kết quả chính xác:

```text
normal=1
in try
in finally
method result=10
caught=bad
after catch
loop=2, switch=20
```

Giải thích đường chạy:

1. Khối lồng nhau trong `normalBlock` kết thúc bình thường, nên câu lệnh in tiếp theo được thực thi.
2. `return 10` làm khối `try` kết thúc đột ngột với lý do `return`. Java vẫn chạy `finally`, sau đó giá trị `10` được trả về lời gọi bên ngoài.
3. `throw` làm khối `try` của `catchAndContinue` hoàn tất đột ngột; `catch (IllegalArgumentException)` xử lý ngoại lệ và hoàn tất bình thường, nên dòng `after catch` vẫn chạy.
4. `continue` bỏ qua lượt `i=1`; `break` dừng vòng lặp ở `i=3`. Tổng là `0 + 2 = 2`. `yield 20` hoàn thành nhiệm vụ cung cấp giá trị cho `switch expression`.

Không có ngoại lệ nào thoát ra ngoài `main` trong ví dụ này, dù nhiều câu lệnh con đã hoàn tất đột ngột.

## 6. `try-catch-finally` quyết định cách hoàn tất như thế nào?

### `try-catch`

Với `try-catch` không có `finally`, trước tiên Java chạy khối `try`:

- Nếu khối `try` hoàn tất bình thường, không cần vào `catch`; cấu trúc `try-catch` hoàn tất bình thường.
- Nếu khối `try` ném ngoại lệ, Java xét các nhánh `catch` theo thứ tự khai báo để chọn nhánh đầu tiên tương thích với kiểu ngoại lệ thực tế.
- Nếu nhánh `catch` được chọn hoàn tất bình thường, cấu trúc `try-catch` cũng hoàn tất bình thường.
- Nếu không có `catch` phù hợp, ngoại lệ tiếp tục lan ra.
- Nếu chính `catch` phát sinh lý do hoàn tất đột ngột mới, cấu trúc bên ngoài nhận lý do mới đó.

`catch` chỉ xử lý lý do `throw` tương thích, **không bắt `return`, `break`, `continue` hoặc `yield`**.

### `try-finally` và `try-catch-finally`

Sau khi phần `try` (và `catch` nếu được chọn) kết thúc, Java thực hiện `finally` trước khi hoàn tất việc chuyển quyền điều khiển theo lý do đang chờ. Từ đó có bốn khả năng cốt lõi:

| Hoàn tất của phần trước `finally` | Hoàn tất của `finally` | Hoàn tất của toàn bộ cấu trúc |
| --- | --- | --- |
| Bình thường | Bình thường | Bình thường |
| Đột ngột vì lý do R | Bình thường | Đột ngột vì R (tiếp tục lý do cũ) |
| Bình thường | Đột ngột vì S | Đột ngột vì S |
| Đột ngột vì R | Đột ngột vì S | Đột ngột vì S; R bị thay thế |

Hệ quả quan trọng: một `return` hay `throw` bên trong `finally` có thể **ghi đè kết quả trả về hoặc che khuất ngoại lệ ban đầu**. Đây là lý do nên dùng `finally` cho công việc dọn dẹp nhưng **tránh `return` trong `finally`**. Logic dọn dẹp cũng cần cẩn thận khi có khả năng ném lỗi mới.

## 7. Chương trình Java 17+: `finally` giữ hoặc thay thế kết quả

Lưu thành `FinallyCompletionDemo.java`:

```java
public class FinallyCompletionDemo {
    static int preservedReturn() {
        int local = 1;
        try {
            return local;
        } finally {
            local = 99;
            System.out.println("finally local=" + local);
        }
    }

    static int replacedReturn() {
        try {
            return 1;
        } finally {
            return 2;
        }
    }

    static void maskedException() {
        try {
            try {
                throw new IllegalStateException("original");
            } finally {
                throw new IllegalArgumentException("cleanup");
            }
        } catch (RuntimeException ex) {
            System.out.println("seen=" + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        System.out.println("preserved=" + preservedReturn());
        System.out.println("replaced=" + replacedReturn());
        maskedException();
    }
}
```

```bash
javac -encoding UTF-8 FinallyCompletionDemo.java
java FinallyCompletionDemo
```

Kết quả:

```text
finally local=99
preserved=1
replaced=2
seen=cleanup
```

Với `preservedReturn()`, giá trị `local` được đánh giá thành `1` cho câu lệnh `return` **trước** khi `finally` chạy; đổi biến cục bộ sang `99` không làm đổi giá trị nguyên thủy đã được chọn để trả về. Với `replacedReturn()`, `finally` tự hoàn tất đột ngột vì `return 2`, nên kết quả `1` của `try` bị thay bằng `2`.

Ở `maskedException()`, `try` bên trong muốn ném `IllegalStateException("original")` nhưng `finally` lại ném `IllegalArgumentException("cleanup")`. Lý do `throw` mới **thay thế** lý do ban đầu; `catch` bên ngoài thấy `cleanup`. Cách viết này dùng để chỉ ra lỗi thiết kế; không nên dùng `return` hoặc cố tình ném lỗi mới trong `finally` của mã nghiệp vụ. Thông tin lỗi `original` cũng không tự động trở thành `cause` hay `suppressed` trong cấu trúc `try-finally` thông thường.

## 8. `try-with-resources`: hoàn tất và ngoại lệ khi đóng tài nguyên

`try-with-resources` cũng tuân thủ quy tắc hoàn tất nhưng có cơ chế **đóng các tài nguyên** triển khai `AutoCloseable`. Tài nguyên được đóng theo thứ tự ngược với thứ tự khởi tạo. Nếu phần thân `try` ném ngoại lệ và thao tác `close()` cũng ném ngoại lệ, Java thường giữ lỗi từ phần thân làm **ngoại lệ chính** và thêm lỗi đóng tài nguyên vào danh sách **suppressed**. Cơ chế này tránh làm mất lỗi gốc theo cách `finally` ném lỗi mới có thể gây ra.

Lưu thành `ResourceCompletionDemo.java`:

```java
public class ResourceCompletionDemo {
    static class BrokenResource implements AutoCloseable {
        @Override
        public void close() {
            throw new IllegalStateException("close failed");
        }
    }

    public static void main(String[] args) {
        try (BrokenResource resource = new BrokenResource()) {
            throw new IllegalArgumentException("work failed");
        } catch (Exception ex) {
            System.out.println("primary=" + ex.getMessage());
            System.out.println("suppressed count=" + ex.getSuppressed().length);
            System.out.println("suppressed=" + ex.getSuppressed()[0].getMessage());
        }
        System.out.println("after try");
    }
}
```

```bash
javac -encoding UTF-8 ResourceCompletionDemo.java
java ResourceCompletionDemo
```

Kết quả:

```text
primary=work failed
suppressed count=1
suppressed=close failed
after try
```

Phần thân `try` hoàn tất đột ngột vì `throw`; `close()` cũng thất bại, nhưng lỗi đóng tài nguyên được giữ làm lỗi bị đính kèm. `catch` xử lý ngoại lệ chính và hoàn tất bình thường, nên dòng `after try` vẫn chạy. Nếu phần thân `try` kết thúc bình thường nhưng `close()` thất bại, lỗi đóng tài nguyên có thể trở thành ngoại lệ chính.

Không nên nhầm `getSuppressed()` với `getCause()`: `cause` biểu thị chuỗi nguyên nhân, còn `suppressed` ghi những ngoại lệ bổ sung trong cùng quá trình xử lý.

## 9. Hoàn tất đột ngột còn ảnh hưởng tới biên dịch

Java có **phân tích khả năng đến được câu lệnh** (*reachability*) và **kiểm tra đường trả về** dựa trên đường điều khiển có thể xảy ra. Những quy tắc này được kiểm tra tại lúc biên dịch; chúng liên quan đến nhưng không đồng nhất với quan sát thực tế của một lần chạy.

Ví dụ lỗi biên dịch:

```java
class InvalidExample {
    static int bad(int number) {
        if (number > 0) {
            return number;
        }
        // Thiếu return hoặc throw trên đường number <= 0.
    }
}
```

Đây là ví dụ **cố ý không biên dịch**: phương thức khai báo trả `int` nhưng vẫn có đường đi đến cuối thân phương thức mà không trả giá trị; trình biên dịch báo `missing return statement`. Cách sửa có thể là bổ sung `return` ở đường còn lại hoặc ném ngoại lệ có lý do:

```java
class ValidExample {
    static int positiveOnly(int number) {
        if (number > 0) {
            return number;
        }
        throw new IllegalArgumentException("number must be positive");
    }
}
```

`ValidExample` là mã Java hợp lệ nhưng không có `main` để chạy độc lập. Phương thức có hai đường: trả về giá trị hoặc ném ngoại lệ. Không có đường thực thi hợp lệ nào rơi xuống cuối thân phương thức mà thiếu giá trị trả về.

Ví dụ không tới được câu lệnh:

```java
class UnreachableExample {
    static void bad() {
        return;
        // System.out.println("never"); // Bỏ chú thích sẽ là lỗi unreachable statement.
    }
}
```

Trình biên dịch có thể nhận ra các trường hợp không thể thực thi do câu lệnh chuyển quyền điều khiển vô điều kiện. Tuy nhiên, **đừng xem mọi nhánh không xảy ra trong dữ liệu thử là unreachable theo JLS**: reachability là phân tích tĩnh với các quy tắc ngôn ngữ cụ thể, không phải chứng minh tổng quát mọi điều kiện chạy.

## 10. Cách phân tích một tình huống thực tế

Khi đọc mã kết hợp vòng lặp, ngoại lệ, `try-catch-finally` và `return`, hãy theo bốn bước:

1. **Xác định mức đang xét:** biểu thức, câu lệnh, thân vòng lặp, khối `try`, toàn bộ `try-catch-finally` hay lời gọi phương thức.
2. **Ghi lý do tại điểm chuyển quyền:** `break`, `continue`, `return`, `throw`, `yield`; đối với `throw` ghi cả kiểu và đối tượng ngoại lệ nếu cần.
3. **Đi từ trong ra ngoài:** vòng lặp/switch có nhận và xử lý `break` hoặc `continue` không; `switch expression` có nhận `yield` không; `catch` có bắt được kiểu ngoại lệ không; `finally` có hoàn tất bình thường hay ghi đè lý do không?
4. **Chỉ kết luận ở đúng cấp:** một khối có thể hoàn tất đột ngột nhưng cấu trúc bao ngoài đã xử lý và hoàn tất bình thường. Từ đó mới xác định câu lệnh nào chạy tiếp và phương thức trả về hay ném lỗi.

Ví dụ tư duy nhanh:

```java
int result;
try {
    result = Integer.parseInt("oops");
} catch (NumberFormatException ex) {
    result = -1;
} finally {
    System.out.println("done");
}
System.out.println(result);
```

Đây là đoạn mã minh họa đặt trong một phương thức. `parseInt` ném ngoại lệ, nên câu lệnh gán trong `try` hoàn tất đột ngột. `catch` bắt đúng kiểu, gán `-1` và hoàn tất bình thường. `finally` in `done` và hoàn tất bình thường. Toàn bộ `try-catch-finally` hoàn tất bình thường, rồi dòng cuối in `-1`. Kết quả theo đúng thứ tự là:

```text
done
-1
```

## 11. Những nhầm lẫn cần tránh

- **“Abrupt = lỗi”**: sai. `return`, `break`, `continue`, `yield` đều có thể là hành vi mong muốn.
- **“Có `return` thì `finally` không chạy”**: sai trong luồng Java thông thường; `finally` chạy trước khi hoàn tất chuyển quyền điều khiển.
- **“`finally` luôn giữ ngoại lệ gốc”**: sai. `return` hoặc `throw` từ chính `finally` có thể thay thế lý do trước đó. Dùng `try-with-resources` cho tài nguyên có thể đóng để quản lý suppressed exception phù hợp.
- **“`catch (Exception)` xử lý được mọi lý do abrupt”**: sai. Nó chỉ xử lý những ngoại lệ tương thích với `Exception`; không xử lý `return`, `break`, `continue`, `yield` và không bắt được nhánh `Error`.
- **“Câu lệnh `return` đột ngột nghĩa là lời gọi phương thức cũng thất bại”**: sai. Bên gọi có thể nhận giá trị và tiếp tục bình thường.
- **“`finally` được bảo đảm chạy kể cả khi JVM bị chấm dứt đột ngột”**: không đúng. Các tình huống như tiến trình bị cưỡng bức kết thúc hoặc JVM dừng trước khi thực thi khối không bảo đảm chạy `finally`.
- **“Bắt lỗi là chạy tiếp ngay tại câu lệnh gây lỗi”**: sai. Xử lý diễn ra ở `catch` tương ứng, và sau đó quyền điều khiển đi theo cấu trúc của `try-catch`.

## 12. Tự kiểm tra

1. `return 42;` có phải hoàn tất đột ngột? **Có**, vì lý do `return` với giá trị. Việc lời gọi phương thức nhận được `42` có thể là một kết quả bình thường ở cấp ngoài.
2. `break;` trong vòng lặp có tự ném ngoại lệ không? **Không.** Nó chuyển quyền điều khiển ra ngoài vòng lặp thích hợp.
3. Khi `try` ném lỗi được `catch` bắt và `catch` kết thúc bình thường, cấu trúc `try-catch` có thể kết thúc bình thường không? **Có.**
4. `finally` ném ngoại lệ mới trong lúc một ngoại lệ cũ đang chờ được ném ra thì sao? **Thông thường lý do mới thay thế lý do cũ** trong `try-finally` thông thường; lỗi cũ không tự động được đính kèm.
5. `yield` có thoát khỏi phương thức giống `return` không? **Không.** Nó chuyển giá trị ra khỏi `switch expression` thích hợp.
6. Một biểu thức hoàn tất đột ngột vì `continue` được không? **Không.** Theo JLS, biểu thức chỉ hoàn tất đột ngột do một ngoại lệ bị ném.

## Tham khảo

- [Java Language Specification, Java SE 21 — §14.1 Normal and Abrupt Completion of Statements](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.1).
- [JLS Java SE 21 — §14.20 `try` statement và `finally`](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.20).
- [JLS Java SE 21 — §14.20.3 `try-with-resources`](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.20.3).
- [JLS Java SE 21 — §15.6 Normal and Abrupt Completion of Evaluation](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.6).

**Ghi nhớ:** Hai khái niệm *normal completion* và *abrupt completion* mô tả **cách một đơn vị thực thi kết thúc ở một cấp cụ thể**. Hãy xác định lý do abrupt, theo dõi cách cấu trúc bao ngoài xử lý lý do đó và luôn kiểm tra `finally` trước khi kết luận điều gì được trả về, ném ra hoặc chạy tiếp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-control-flow-002">Ngoại lệ phát sinh từ mã Java, API hoặc JVM</a>

<details>
<summary>Xem chi tiết</summary>

# Ngoại lệ phát sinh từ mã Java, API hoặc JVM

## 1. Mục tiêu

Khi đọc stack trace, bạn thường thấy một tên ngoại lệ như `NullPointerException`, `NumberFormatException` hoặc `IOException`. Tên kiểu lỗi cho biết **điều gì đã được báo**, nhưng chưa đủ cho biết **ai hoặc cơ chế nào đã tạo ra lỗi đó**.

Sau bài này, bạn có thể:

- Phân biệt ba đường phát sinh thường gặp: mã ứng dụng chủ động `throw`, phương thức API báo thất bại, và JVM phát sinh ngoại lệ/lỗi khi thực thi.
- Xác định điểm ngoại lệ xuất hiện, cách nó lan truyền qua các phương thức, cách `catch` chọn handler và tác động tới `finally`.
- Giải thích vì sao một kiểu ngoại lệ có thể xuất hiện từ nhiều nguồn, đặc biệt là `NullPointerException` và `IllegalArgumentException`.
- Phân biệt checked exception, unchecked exception và `Error` dù nguồn phát sinh là gì.
- Viết và chạy ví dụ Java 17+, quan sát luồng thực thi và áp dụng cách chẩn đoán phù hợp.

> “Mã Java”, “API” và “JVM” là cách phân loại theo **nơi hoặc cơ chế báo lỗi**. Chúng không phải ba nhánh lớp độc lập trong hệ phân cấp `Throwable`. Một API cũng được viết bằng Java hoặc mã native, và JVM chạy cả mã ứng dụng lẫn mã API.

## 2. Nền tảng: ngoại lệ đi vào luồng thực thi như thế nào?

Trong Java, đối tượng được ném phải là `Throwable` hoặc lớp con của nó. Cấu trúc chính:

```text
java.lang.Throwable
├── java.lang.Exception
│   ├── các checked exception (ví dụ IOException)
│   └── java.lang.RuntimeException
│       ├── IllegalArgumentException
│       │   └── NumberFormatException
│       ├── NullPointerException
│       └── IndexOutOfBoundsException
└── java.lang.Error
    ├── AssertionError
    ├── LinkageError
    └── VirtualMachineError
        ├── OutOfMemoryError
        └── StackOverflowError
```

Đây là **sơ đồ các lớp tiêu biểu**, không liệt kê toàn bộ hệ phân cấp. Những lớp trong nhánh `Error` không phải `Exception` dù đều có thể được ném. Trong Java, `RuntimeException` và `Error` cùng các lớp con thuộc nhóm *unchecked*; các lớp `Exception` khác không kế thừa `RuntimeException` thường là *checked* (trừ cách diễn đạt kỹ thuật chính xác hơn: mọi lớp con của `Throwable` không thuộc `RuntimeException` hoặc `Error` là checked).

Khi một biểu thức hoặc lời gọi phương thức hoàn thành đột ngột (*abrupt completion*) do ném ngoại lệ:

1. Phần còn lại của biểu thức/câu lệnh đang thực hiện không tiếp tục như bình thường.
2. JVM tìm handler `catch` có kiểu phù hợp trong các khối `try` bao quanh.
3. Nếu không có handler tại phương thức hiện tại, quá trình lan truyền ra phương thức gọi; các khối `finally` thích hợp được thực hiện trong quá trình thoát bình thường của stack.
4. Nếu không được bắt đến tận ranh giới thực thi của luồng, ngoại lệ có thể đến `UncaughtExceptionHandler`; luồng đó kết thúc. Điều này không nhất thiết làm mọi luồng hoặc cả JVM dừng ngay.

Nguồn phát sinh **không đổi cơ chế lan truyền**. Một `IllegalArgumentException` do bạn `throw` và một `IllegalArgumentException` được ném từ API đều đi qua cùng quy tắc chọn `catch`.

## 3. Ba nguồn phát sinh chính

| Nguồn | Điều gì tạo tín hiệu lỗi? | Ví dụ điển hình | Điều cần kiểm tra |
| --- | --- | --- | --- |
| **Mã ứng dụng Java** | Lệnh `throw` do người lập trình viết khi điều kiện/bất biến không đạt | `throw new IllegalArgumentException("age < 0")` | Điều kiện kiểm tra, hợp đồng phương thức, dữ liệu đầu vào |
| **API/thư viện** | Phương thức được gọi báo thất bại theo hợp đồng của nó; nội bộ có thể dùng `throw` Java hoặc xử lý native | `Integer.parseInt("12x")` → `NumberFormatException`; `Files.readString(path)` có thể ném `IOException` | JavaDoc, tham số, trạng thái tài nguyên, `throws`, nguyên nhân gốc |
| **JVM khi thực thi** | Một thao tác ở mức ngôn ngữ/bytecode vi phạm điều kiện chạy hoặc VM không đáp ứng tài nguyên | Gọi phương thức trên `null` → `NullPointerException`; chia nguyên cho 0 → `ArithmeticException`; thiếu stack → `StackOverflowError` | Dòng mã/bytecode, giá trị tại thời điểm chạy, thông báo và trạng thái JVM |

**Lưu ý về ranh giới:** Không thể kết luận chắc “do JVM”, “do API” hay “do mã mình” **chỉ bằng tên lớp**. Ví dụ `NullPointerException` có thể do dereference `null` trong bytecode, do `Objects.requireNonNull(null)` trong API hoặc do lập trình viên tự `throw new NullPointerException()`. Stack trace, call site và hợp đồng API mới giúp xác định nguồn.

### 3.1. Ngoại lệ do mã Java chủ động ném

Cú pháp:

```java
if (quantity <= 0) {
    throw new IllegalArgumentException("quantity must be positive");
}
```

Đây là **mảnh mã minh họa bên trong phương thức**. Lệnh `throw` tạo một lối thoát đột ngột; câu lệnh sau đó trên cùng nhánh không chạy. Nếu một phương thức khai báo trả về giá trị, đường đi `throw` không cần trả về giá trị.

Chọn kiểu ngoại lệ để truyền đạt đúng hợp đồng:

- `IllegalArgumentException`: tham số không hợp lệ; một biến thể phổ biến là `NumberFormatException`.
- `IllegalStateException`: trạng thái hiện tại không cho phép thao tác.
- Checked exception tự định nghĩa hoặc có sẵn: bên gọi cần biết và được trình biên dịch buộc xử lý/khai báo nếu kiểu được ném là checked.
- Đừng dùng `Error` hoặc `OutOfMemoryError` để diễn đạt lỗi nghiệp vụ thông thường.

Lệnh `throw` có thể ném lại **cùng đối tượng đã bắt**:

```java
try {
    performWork();
} catch (IllegalStateException failure) {
    // Ghi nhận ngữ cảnh phù hợp, sau đó giữ nguyên ngoại lệ gốc.
    throw failure;
}
```

Đây là **mảnh mã minh họa**, `performWork()` không được định nghĩa trong đoạn này. Khi tạo ngoại lệ bọc bằng `new SomeException(message, cause)`, nên giữ nguyên `cause` để không mất chuỗi nguyên nhân.

### 3.2. Ngoại lệ do API hoặc thư viện Java báo

API có hợp đồng về dữ liệu hợp lệ và điều kiện thất bại. Các ví dụ:

```java
Integer.parseInt("12x");        // NumberFormatException
java.util.Objects.requireNonNull(null); // NullPointerException
java.util.List.of("a").get(3); // IndexOutOfBoundsException
```

Các dòng trên là **ví dụ riêng lẻ về lời gọi ném lỗi**, không phải một chương trình `main` hoàn chỉnh. `Integer.parseInt` phát hiện chuỗi không thể chuyển thành `int`; `Objects.requireNonNull` chủ động kiểm tra tham chiếu; `List.get` kiểm tra chỉ số. Đây là lỗi được báo theo **hợp đồng API**, kể cả khi loại ngoại lệ trùng với loại JVM có thể ném.

API cũng khai báo checked exception:

```java
String content = java.nio.file.Files.readString(path); // có thể ném IOException
```

Đây là **mảnh mã minh họa**; `path` phải là `java.nio.file.Path` được khởi tạo và câu lệnh phải được đặt trong khối xử lý `IOException` hoặc phương thức có `throws IOException`. Tùy filesystem và quyền truy cập, lời gọi có thể thành công hoặc báo một lớp con của `IOException`.

Đọc JavaDoc để xác định: phương thức nhận gì, ném loại nào, có kiểm tra tham số hay không, có phụ thuộc tệp/mạng/cấu hình/luồng hay không. `throws` trong chữ ký mô tả checked exception bắt buộc phải xét; một số unchecked exception được nêu trong JavaDoc mà không bắt buộc xuất hiện ở chữ ký.

### 3.3. Ngoại lệ/lỗi JVM có thể phát sinh khi thực thi

Một số thao tác Java có kiểm tra tại runtime và có thể thất bại ngay cả khi **không có lệnh `throw` hiện rõ trong mã nguồn**.

| Hoạt động | Ví dụ | Loại có thể phát sinh |
| --- | --- | --- |
| Truy cập trường/gọi phương thức qua `null` | `String s = null; s.length();` | `NullPointerException` |
| Truy cập phần tử mảng sai giới hạn | `int[] a = new int[1]; int x = a[2];` | `ArrayIndexOutOfBoundsException` |
| Ép kiểu tham chiếu không tương thích | `Object x = "hi"; Integer n = (Integer) x;` | `ClassCastException` |
| Chia số nguyên cho 0 | `int zero = 0; int q = 10 / zero;` | `ArithmeticException` |
| Tạo mảng với kích thước âm | `int[] a = new int[-1];` | `NegativeArraySizeException` |
| Gán phần tử không phù hợp kiểu runtime của mảng tham chiếu | `Object[] a = new String[1]; a[0] = 42;` | `ArrayStoreException` |
| Stack của luồng vượt giới hạn | Đệ quy quá sâu | `StackOverflowError` (`Error`) |
| JVM không đáp ứng được yêu cầu cấp phát/tài nguyên | Thiếu heap, native resource tùy tình huống | `OutOfMemoryError` (`Error`) |

Lưu ý kỹ thuật:

- Với toán hạng `double` hoặc `float`, phép chia cho `0.0` theo IEEE 754 thường tạo `Infinity` hoặc `NaN`, **không** tạo `ArithmeticException` như phép chia số nguyên cho 0.
- Các `Error` như `OutOfMemoryError`, `StackOverflowError` thuộc `Throwable` nhưng **không thuộc `Exception`**; `catch (Exception e)` sẽ không bắt chúng.
- Không nên cố tạo lỗi cạn bộ nhớ/stack để kiểm tra logic xử lý trong môi trường thực; hệ quả phụ thuộc JVM, tài nguyên và cách triển khai.

## 4. Ví dụ chạy được: quan sát cả ba nguồn

Tạo file **`ExceptionOriginsDemo.java`**:

```java
import java.util.Objects;

public class ExceptionOriginsDemo {
    private static void validateAge(int age) {
        if (age < 0) {
            throw new IllegalArgumentException("age cannot be negative");
        }
    }

    private static void observe(String label, Runnable action) {
        try {
            action.run();
            System.out.println(label + ": OK");
        } catch (RuntimeException failure) {
            System.out.println(label + ": "
                    + failure.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        observe("Java code", () -> validateAge(-1));

        observe("JDK API parse", () -> Integer.parseInt("12x"));

        observe("JDK API null check", () -> Objects.requireNonNull(null));

        observe("JVM null access", () -> {
            String text = null;
            text.length();
        });

        System.out.println("Program continues");
    }
}
```

Biên dịch và chạy với **JDK 17+**:

```bash
javac ExceptionOriginsDemo.java
java ExceptionOriginsDemo
```

Kết quả `stdout`:

```text
Java code: IllegalArgumentException
JDK API parse: NumberFormatException
JDK API null check: NullPointerException
JVM null access: NullPointerException
Program continues
```

Giải thích theo từng dòng:

1. **Java code**: `validateAge(-1)` kiểm tra tham số và chủ động `throw new IllegalArgumentException(...)`.
2. **JDK API parse**: `Integer.parseInt("12x")` nhận chuỗi không hợp lệ và ném `NumberFormatException` theo hợp đồng API.
3. **JDK API null check**: `Objects.requireNonNull(null)` chủ động kiểm tra rồi ném `NullPointerException` ở tầng API.
4. **JVM null access**: phép gọi `text.length()` cố dereference `null`, khiến JVM ném `NullPointerException` trong quá trình thực thi.
5. **Program continues**: `observe` xử lý từng `RuntimeException` ngay tại ranh giới ví dụ, nên luồng chính tiếp tục. Trong ứng dụng thật, không nhất thiết nên bắt mọi `RuntimeException`; ranh giới phục hồi phải có mục đích rõ ràng.

Chương trình chỉ in **tên lớp ngoại lệ**, tránh phụ thuộc thông điệp `NullPointerException` chi tiết có thể khác nhau tùy JDK, bytecode và cấu hình JVM.

Điểm đáng nhớ: hai dòng cuối cùng trong nhóm lỗi đều in `NullPointerException`, nhưng một lỗi do phương thức API kiểm tra chủ động, còn lỗi kia do JVM kiểm tra thao tác truy cập tham chiếu.

## 5. Ngoại lệ lan truyền: nguồn nào cũng đi qua cùng cơ chế

Tạo file **`ExceptionFlowDemo.java`**:

```java
public class ExceptionFlowDemo {
    private static void lowLevel() {
        System.out.println("low: before throw");
        throw new IllegalStateException("invalid state");
    }

    private static void middleLevel() {
        try {
            lowLevel();
            System.out.println("middle: after call");
        } finally {
            System.out.println("middle: finally");
        }
    }

    public static void main(String[] args) {
        try {
            middleLevel();
            System.out.println("main: after call");
        } catch (IllegalStateException failure) {
            System.out.println("main caught: " + failure.getMessage());
        }
        System.out.println("main continues");
    }
}
```

```bash
javac ExceptionFlowDemo.java
java ExceptionFlowDemo
```

Kết quả `stdout`:

```text
low: before throw
middle: finally
main caught: invalid state
main continues
```

Diễn biến chính xác:

- `lowLevel()` in dòng đầu rồi `throw` nên không trả về bình thường.
- `middleLevel()` không có `catch`, nhưng khối `finally` của nó vẫn được thực hiện trong quá trình lan truyền ngoại lệ.
- Câu lệnh `middle: after call` và `main: after call` không chạy.
- `catch (IllegalStateException)` tại `main` nhận lỗi, in thông điệp rồi cho chương trình tiếp tục.

Nếu thay `throw new IllegalStateException(...)` bằng một lời gọi API ném `IllegalStateException` hoặc một thao tác JVM ném `RuntimeException` khác mà `catch` tương thích, cơ chế tìm handler và unwind vẫn tương tự. **Nguồn tạo lỗi** không tự động quyết định lỗi sẽ được bắt ở đâu.

### `finally` có phải luôn chạy?

Không thể hứa “luôn chạy” trong mọi hoàn cảnh. Với ví dụ bình thường trên, `finally` chạy khi phương thức bị thoát do ngoại lệ. Nhưng nếu tiến trình/VM bị kết thúc bất thường, `Runtime.halt`, mất nguồn điện hoặc môi trường không cho phép tiếp tục, nó không được bảo đảm hoàn tất. Nếu chính `finally` ném một ngoại lệ mới, kết quả lan truyền có thể thay đổi; đừng viết `finally` che giấu lỗi gốc.

## 6. Checked exception: nghĩa vụ khi gọi API

Việc ngoại lệ đến từ thư viện hay từ ứng dụng **không quyết định** nó có checked hay không. Phân loại được xác định bởi **kiểu lớp kế thừa** và ngữ cảnh bắt/khai báo.

- **Checked**: ví dụ `IOException`. Nếu mã có thể ném một checked exception, trình biên dịch yêu cầu bắt bằng `catch` phù hợp hoặc khai báo bằng `throws` ở phương thức gọi (trong khuôn khổ quy tắc Java).
- **Unchecked**: các lớp con của `RuntimeException` và `Error`. Không bắt buộc khai báo trong chữ ký hoặc bắt tại call site.
- Từ khóa `throws` **không tạo ra ngoại lệ**; nó khai báo khả năng phương thức hoàn thành đột ngột bằng những kiểu ngoại lệ được liệt kê. `throw` là thao tác thực sự ném một đối tượng.

Ví dụ chạy được với một API khai báo `throws IOException` dù dữ liệu đọc ở ví dụ không gây lỗi.

Tạo file **`CheckedApiDemo.java`**:

```java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

public class CheckedApiDemo {
    private static String firstLine(Reader source) throws IOException {
        try (BufferedReader reader = new BufferedReader(source)) {
            return reader.readLine();
        }
    }

    public static void main(String[] args) throws IOException {
        String value = firstLine(new StringReader("hello\nworld"));
        System.out.println(value);
    }
}
```

```bash
javac CheckedApiDemo.java
java CheckedApiDemo
```

Kết quả:

```text
hello
```

`BufferedReader.readLine()` có chữ ký khai báo `IOException`. Vì vậy `firstLine` và `main` khai báo `throws IOException` để thỏa quy tắc checked exception. Lần chạy này dùng `StringReader` nên thành công, nhưng **không vì thế mà bỏ được nghĩa vụ khai báo/bắt checked exception**. Với nguồn I/O thật, lỗi đọc có thể xảy ra do điều kiện ngoài chương trình.

Có thể thay `throws IOException` tại biên ứng dụng bằng `try/catch (IOException e)` khi có cách phản hồi phù hợp. Với tài nguyên như file/socket/stream, ưu tiên `try-with-resources` để đóng tài nguyên. Nếu cả phần thân và thao tác `close()` đều ném ngoại lệ, cơ chế try-with-resources thường giữ lỗi chính và lưu lỗi đóng dưới dạng **suppressed exception**; cần xem cả `getSuppressed()` khi chẩn đoán.

## 7. Chẩn đoán từ stack trace và hợp đồng API

Khi gặp lỗi, thứ tự phân tích hữu ích là:

1. **Đọc kiểu và thông điệp đầy đủ**: loại nào, nội dung thông báo nêu tham số, đường dẫn, chỉ số hay trạng thái nào? Tránh bỏ qua `Caused by` và `Suppressed`.
2. **Đọc stack trace từ vị trí phát sinh**: xem các frame đầu để tìm điểm tạo lỗi/điểm kiểm tra, rồi theo đường gọi về code của mình. Với lỗi được bọc, xem từng `cause` để tìm lỗi gốc.
3. **Phân loại thao tác**: có `throw` chủ động của ứng dụng không, có một API báo sai hợp đồng hoặc lỗi tài nguyên không, hay có toán tử/truy cập mà JVM kiểm tra?
4. **Kiểm tra giá trị tại thời điểm lỗi**: `null`, chỉ số, số chia, đầu vào chuỗi, điều kiện bất biến, trạng thái file/network hoặc dung lượng tài nguyên.
5. **Sửa nguyên nhân** và quyết định nơi xử lý đúng: chặn input sớm, chỉnh logic, bảo đảm tài nguyên, trả thông báo nghiệp vụ, hay nâng mức cảnh báo vận hành.

Ví dụ, cùng `NullPointerException` nhưng hướng sửa khác nhau:

| Tình huống | Hướng điều tra |
| --- | --- |
| `customer.getName()` với `customer == null` | Tìm đường dữ liệu tạo/cho phép `null`, sửa hợp đồng và nhánh logic |
| `Objects.requireNonNull(config, "config")` | Xem vì sao đối số truyền vào API thiếu và lời gọi nào vi phạm hợp đồng |
| API bên thứ ba ném `NullPointerException` nội bộ | Đối chiếu hợp đồng, phiên bản thư viện, input và stack trace; có thể là lỗi thư viện |
| `OutOfMemoryError` khi gọi API | Kiểm tra cả workload của API, dữ liệu, giới hạn bộ nhớ và môi trường JVM; nguồn nằm tại thao tác cấp phát thực tế |

Một stack trace **không nhất thiết chứng minh tất cả nguyên nhân sâu xa**: JVM có thể tối ưu cách hiển thị, một thư viện có thể bọc lỗi, còn dữ liệu runtime có thể đã thay đổi trước khi điều tra. Hãy giữ log/đầu vào/phiên bản JDK phù hợp để tái hiện.

### Khi chủ động ném lại hoặc bọc lỗi

Nên giữ ngữ cảnh bằng `cause` khi chuyển một lỗi từ tầng thấp lên tầng cao. Ví dụ **mảnh mã trong phương thức có phép đọc dữ liệu**:

```java
try {
    loadFromStorage();
} catch (java.io.IOException failure) {
    throw new IllegalStateException("Cannot initialize data", failure);
}
```

Đây là **mảnh minh họa**, cần có phương thức `loadFromStorage()` khai báo `throws IOException` trong chương trình thật. `IllegalStateException` là unchecked nhưng `failure` vẫn truy cập được qua `getCause()`. Không nên bọc vô tội vạ nhiều tầng bằng thông điệp vô nghĩa, hoặc chỉ ghi `failure.getMessage()` rồi làm mất stack trace.

## 8. Những nhầm lẫn cần tránh

**“API tạo ngoại lệ là JVM tự tạo.”** Không nhất thiết. Một phương thức JDK có thể dùng `throw` sau khi kiểm tra hợp đồng, như `Objects.requireNonNull`. JVM chỉ thực thi lời gọi ấy; phân loại nguồn phụ thuộc nơi điều kiện lỗi được nhận biết.

**“`NullPointerException` luôn chứng minh có lệnh gọi trên `null`.”** Sai. Mã hoặc API có thể chủ động ném chính lớp này.

**“Ngoại lệ do JVM phát sinh đều là `Error`.”** Sai. Truy cập `null`, chỉ số mảng sai hoặc chia số nguyên cho 0 thường tạo các lớp `RuntimeException`.

**“`catch (Exception)` bắt mọi lỗi trong Java.”** Sai. Nó không bắt `Error` như `StackOverflowError` hoặc `OutOfMemoryError`. Hạn chế bắt `Throwable` ở mức chung trừ những hạ tầng chuyên dụng có lý do rõ ràng.

**“Không thấy `throws` thì phương thức không thể lỗi.”** Sai. Unchecked exception/Error có thể phát sinh mà không được khai báo, và các unchecked exception được JavaDoc mô tả vẫn cần được hiểu.

**“Có `throws IOException` chắc chắn lần chạy sẽ ném lỗi.”** Sai. Nó là một khả năng cần xử lý tại lúc biên dịch, không phải một sự kiện chắc chắn khi chạy.

**“Bắt xong là chương trình đã phục hồi.”** Chưa chắc. Chỉ xử lý và tiếp tục khi trạng thái và tài nguyên vẫn thỏa bất biến. Đặc biệt, không giả định có thể tiếp tục an toàn sau lỗi cạn tài nguyên JVM.

**“Ném ngoại lệ cùng tên nghĩa là cùng nguyên nhân.”** Sai. Tên lớp cần được đặt cùng thao tác gây lỗi, thông điệp và chuỗi `cause`.

## 9. Câu hỏi tự kiểm tra

1. **Bạn tự viết `throw new IllegalArgumentException("bad")`. Nguồn phát sinh là gì?** Mã ứng dụng Java.
2. **`Integer.parseInt("x")` phát sinh gì và ở đâu?** `NumberFormatException` do API báo dữ liệu không thể phân tích theo hợp đồng.
3. **`String s = null; s.length();` phát sinh gì?** JVM có thể ném `NullPointerException` khi đánh giá thao tác dereference.
4. **Hai trường hợp ở câu 2 và 3 đều unchecked vì đều từ API/JVM?** Không. Chúng unchecked vì kế thừa `RuntimeException`, độc lập với nguồn.
5. **`catch (IOException e)` có nhận `OutOfMemoryError`?** Không, chúng thuộc hai nhánh `Exception` và `Error` khác nhau.
6. **Một phương thức khai báo `throws IOException` có buộc phải thực sự ném IOException mỗi lần gọi?** Không. Chữ ký thể hiện khả năng phát sinh checked exception.
7. **Khi không biết một `NullPointerException` đến từ API hay bytecode, nên đọc gì?** Call site, stack trace, API contract và dữ liệu thực tại thời điểm lỗi.

## 10. Tham khảo Java 17

- [Java Language Specification 17 — §11.1.3 Causes of Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.1.3): nguyên nhân phát sinh ngoại lệ.
- [Java Language Specification 17 — §11.2 Compile-Time Checking of Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.2): quy tắc checked exception.
- [Java Language Specification 17 — §11.3 Run-Time Handling of an Exception](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.3): lan truyền và xử lý tại runtime.
- [Java SE 17 API — Throwable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html): cause, stack trace, suppressed exception.
- [Java SE 17 API — Objects.requireNonNull](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Objects.html#requireNonNull(T)): kiểm tra `null` theo hợp đồng API.
- [Java SE 17 API — BufferedReader.readLine](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedReader.html#readLine()): API có `throws IOException`.

Khi hiểu **thao tác nào đã báo lỗi**, **kiểu `Throwable` nào được ném** và **handler nào nhận được nó**, bạn sẽ phân tích luồng thực thi chính xác và chọn vị trí sửa lỗi phù hợp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-control-flow-003">Dừng phần biểu thức hoặc câu lệnh chưa hoàn tất</a>

<details>
<summary>Xem chi tiết</summary>

# Dừng phần biểu thức hoặc câu lệnh chưa hoàn tất khi phát sinh Exception

> Java Core – Exception · Nhóm: Luồng thực thi · Ví dụ chạy độc lập với Java 17+.

## 1. Điều gì bị dừng khi một ngoại lệ xuất hiện?

Java đánh giá biểu thức để tạo ra giá trị, thực hiện phép gán hoặc gọi phương thức; đồng thời chạy các câu lệnh theo luồng điều khiển. Nếu một bước ném ngoại lệ, bước đó **không hoàn thành bình thường**. Những bước còn lại đòi hỏi nó phải hoàn thành sẽ bị bỏ qua. Java chuyển điều khiển đến bộ xử lý ngoại lệ phù hợp, hoặc truyền lỗi ra phương thức gọi.

Đặc tả Java gọi trường hợp này là *abrupt completion* (kết thúc bất thường), đối lập với *normal completion* (kết thúc bình thường).

Ví dụ giả định:

```java
int result = first() + second() + third();
```

Nếu `first()` hoàn thành nhưng `second()` ném ngoại lệ:

1. `first()` **đã chạy**; tác dụng phụ nó tạo ra vẫn tồn tại.
2. `second()` **đã bắt đầu**, nhưng không trả về kết quả bình thường.
3. `third()` **không được gọi** theo luồng đánh giá bình thường.
4. Không có tổng hoàn chỉnh để gán cho `result`; câu lệnh gán chưa hoàn tất.
5. Nếu có nhánh `catch` phù hợp, mã trong nhánh ấy được chạy. Java không tự quay lại thực hiện `third()` sau khi xử lý ngoại lệ.

Chìa khóa là phân biệt **phần việc chưa được thực hiện** với **những tác dụng phụ đã xảy ra trước điểm lỗi**. Ngoại lệ không tự hoàn tác tác dụng phụ.

## 2. Cơ sở ngôn ngữ: biểu thức và câu lệnh kết thúc bất thường

Theo [Java Language Specification §15.6](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.6), nếu việc tính biểu thức bao ngoài phụ thuộc một biểu thức con, mà biểu thức con kết thúc bất thường, biểu thức bao ngoài cũng kết thúc bất thường vì cùng lý do. Những bước đánh giá thông thường tiếp theo sẽ không thực hiện.

JLS §15.7 quy định thứ tự đánh giá toán hạng và đối số. Trong những trường hợp tương ứng, Java đánh giá từ trái sang phải. Nếu bước ở bên trái ném ngoại lệ, phần phụ thuộc phía sau không được đánh giá.

Câu lệnh cũng có thể kết thúc bất thường. JLS §14.1 liệt kê các lý do như ném ngoại lệ (`throw`), `return`, `break`, `continue` và `yield` khi thích hợp. **Không phải mọi abrupt completion đều là exception**: ví dụ `return` là chuyển điều khiển theo cấu trúc ngôn ngữ, không phải tự động ném một đối tượng `Throwable`. Ở bài này ta xem xét riêng tình huống ngoại lệ.

```text
Đánh giá biểu thức / chạy câu lệnh
               |
           Bước hiện tại
            /      \
    Bình thường   Ném exception
        |               |
        v               v
   Bước tiếp theo     Dừng đường đánh giá
   nếu được chọn      thông thường còn lại
                        |
                        v
                  Rời phạm vi liên quan
                  / chạy cleanup theo quy tắc
                        |
                        v
                  catch phù hợp hoặc
                  truyền exception ra caller
```

Sơ đồ là quy trình khái quát. `finally` và `try-with-resources` có quy tắc thực thi riêng khi rời phạm vi. Một số cách chọn nhánh như `&&`, `||`, `?:` chủ động bỏ qua toán hạng khi không cần đánh giá, **ngay cả khi không có ngoại lệ**.

## 3. Những vị trí thường bị ngắt giữa chừng

### 3.1. Toán hạng của phép toán

```java
int total = calculateLeft() + fail() + calculateRight();
```

Với lời gọi `fail()` ném ngoại lệ, `calculateRight()` chưa chạy; kết quả toàn phép cộng chưa hoàn tất. Nếu `calculateLeft()` đã tăng bộ đếm hoặc ghi log, tác dụng phụ ấy vẫn đã xảy ra.

Lưu ý thứ tự đánh giá khác thứ tự ưu tiên toán tử. Dấu ngoặc và độ ưu tiên quyết định cấu trúc biểu thức, còn JLS quyết định thứ tự đánh giá các phần trong cấu trúc. Đừng suy rằng mọi biểu thức bên phải theo mặt chữ luôn được chạy.

Đối với phép chia số nguyên, toán hạng được đánh giá trước, phép chia mới xảy ra:

```java
int result = left() / right();
```

Nếu `left()` và `right()` đều trả về bình thường, `right()` trả về 0, thì phép chia ném `ArithmeticException` sau khi hai lời gọi đã chạy. Nếu `left()` đã ném lỗi trước, `right()` chưa chạy. Số thực `double` chia cho `0.0` lại tuân quy tắc số thực, thường cho Infinity hoặc NaN, nên không dùng nó thay cho ví dụ chia số nguyên.

### 3.2. Danh sách đối số của phương thức hoặc constructor

```java
send(readId(), parseAmount(), auditCode());
```

Nếu `parseAmount()` ném ngoại lệ, `readId()` có thể đã hoàn thành, `auditCode()` chưa chạy và phần thân `send` không được thực thi. Java đánh giá các biểu thức đối số theo thứ tự trái sang phải; một đối số kết thúc bất thường ngăn phần còn lại.

Với constructor, các biểu thức đối số cũng tuân thứ tự trái sang phải. Đối với lời gọi qua đối tượng và truy cập trường/mảng, cần đọc các quy tắc đánh giá đặc thù nếu muốn biết chính xác thời điểm kiểm tra `null` và những side effect liên quan.

### 3.3. Phép gán chưa hoàn tất

Giả sử:

```java
int amount = 100;
int[] effects = {0};

try {
    amount = ++effects[0] + fail();
} catch (IllegalStateException ex) {
    System.out.println("caught");
}
```

Đây là **đoạn mã minh họa**, giả định `fail()` được định nghĩa trả về `int` nếu thành công nhưng sẽ ném `IllegalStateException` trong tình huống thử. `++effects[0]` tăng phần tử mảng từ 0 lên 1 trước lỗi. Vế phải của phép gán không hoàn tất, nên `amount` vẫn là 100. **Giá trị của `effects[0]` đã đổi**, vì nó là tác dụng phụ của một biểu thức con đã chạy.

Việc gán không hoàn thành không tương đương với việc rollback toàn bộ biểu thức hoặc giao dịch.

### 3.4. Truy cập mảng, ép kiểu và dereference

```java
int result = numbers[index()] + bonus();
```

Giả sử `numbers` là mảng có một phần tử, `index()` trả về 5. `index()` có thể đã tạo side effect; phép truy cập phần tử ném `ArrayIndexOutOfBoundsException`; `bonus()` chưa chạy.

Tương tự, `ClassCastException`, `NullPointerException` hoặc ngoại lệ phát sinh khi gọi API có thể khiến biểu thức bao ngoài dừng. Tuy nhiên, thứ tự tính biểu thức đích, đối số và kiểm tra runtime có những quy tắc chính xác; không nên phát biểu chung rằng hễ NPE thì mọi tham số chưa bao giờ được đánh giá.

### 3.5. Short-circuit khác với ngoại lệ

```java
boolean safe = false && mayThrow();
int chosen = true ? 7 : mightFail();
```

Trong hai dòng này, phần bên phải `&&` và nhánh không được chọn của `?:` không chạy vì quy tắc *conditional evaluation*, dù **không có lỗi nào xảy ra**.

Nếu là toán tử boolean `&` hoặc `|`, Java vẫn đánh giá vế phải khi vế trái hoàn thành bình thường. Nhưng một exception thực sự từ vế trái vẫn chấm dứt biểu thức bao ngoài, khiến vế sau không được đánh giá.

## 4. Chương trình chạy được: lần theo phần đã chạy và phần bị bỏ qua

Lưu trọn mã nguồn dưới đây thành tệp **`AbruptCompletionDemo.java`**. Không cần thư viện ngoài, input file hay tham số chạy.

```java
public class AbruptCompletionDemo {

    private static int trace(String label, int value) {
        System.out.println(label);
        return value;
    }

    private static int explode(String label) {
        System.out.println(label);
        throw new IllegalStateException(label);
    }

    private static boolean failBoolean() {
        System.out.println("never-run");
        throw new IllegalStateException("boolean failure");
    }

    private static void accept3(int a, int b, int c) {
        System.out.println("accept3 called");
    }

    public static void main(String[] args) {
        binaryExpression();
        argumentList();
        unfinishedAssignment();
        arrayAccess();
        interruptedStatements();
        finallyOnUnwind();
        conditionalEvaluation();
    }

    private static void binaryExpression() {
        System.out.println("== binary ==");
        try {
            int result = trace("left", 2)
                    + explode("middle")
                    + trace("right", 4);
            System.out.println("result=" + result);
        } catch (IllegalStateException ex) {
            System.out.println("catch=" + ex.getMessage());
        }
    }

    private static void argumentList() {
        System.out.println("== arguments ==");
        try {
            accept3(
                    trace("arg1", 10),
                    explode("arg2"),
                    trace("arg3", 30));
        } catch (IllegalStateException ex) {
            System.out.println("catch=" + ex.getMessage());
        }
    }

    private static void unfinishedAssignment() {
        System.out.println("== assignment ==");
        int balance = 100;
        int[] effects = {0};

        try {
            balance = ++effects[0] + explode("assignment-failure");
        } catch (IllegalStateException ex) {
            System.out.println("catch=" + ex.getMessage());
        }

        System.out.println("balance=" + balance
                + ", effects=" + effects[0]);
    }

    private static void arrayAccess() {
        System.out.println("== array ==");
        int[] scores = {10};

        try {
            int result = scores[trace("index", 4)]
                    + trace("rhs", 20);
            System.out.println("result=" + result);
        } catch (ArrayIndexOutOfBoundsException ex) {
            System.out.println("catch=ArrayIndexOutOfBoundsException");
        }
    }

    private static void interruptedStatements() {
        System.out.println("== statements ==");
        try {
            System.out.println("before");
            int value = explode("fail-statement");
            System.out.println("after");
        } catch (IllegalStateException ex) {
            System.out.println("catch=" + ex.getMessage());
        }
        System.out.println("resume-after-catch");
    }

    private static void finallyOnUnwind() {
        System.out.println("== finally ==");
        try {
            try {
                trace("inside", 1);
                explode("cleanup-failure");
            } finally {
                System.out.println("finally");
            }
        } catch (IllegalStateException ex) {
            System.out.println("catch=" + ex.getMessage());
        }
    }

    private static void conditionalEvaluation() {
        System.out.println("== conditional ==");
        boolean ok = false && failBoolean();
        System.out.println("false && failBoolean()=" + ok);

        int chosen = true
                ? trace("selected", 7)
                : explode("unused");
        System.out.println("chosen=" + chosen);
    }
}
```

Biên dịch và chạy trên JDK 17 hoặc mới hơn:

```bash
javac -encoding UTF-8 AbruptCompletionDemo.java
java AbruptCompletionDemo
```

Stdout **chính xác**:

```text
== binary ==
left
middle
catch=middle
== arguments ==
arg1
arg2
catch=arg2
== assignment ==
assignment-failure
catch=assignment-failure
balance=100, effects=1
== array ==
index
catch=ArrayIndexOutOfBoundsException
== statements ==
before
fail-statement
catch=fail-statement
resume-after-catch
== finally ==
inside
cleanup-failure
finally
catch=cleanup-failure
== conditional ==
false && failBoolean()=false
selected
chosen=7
```

Những chuỗi **không được in** cũng là bằng chứng: `right`, `arg3`, `accept3 called`, `rhs`, `after`, `never-run` và `unused`. Trình biên dịch không cần biết trước `explode` chắc chắn ném exception; trên lý thuyết hàm có kiểu trả về `int` nhưng khi chạy nó ném trước khi có giá trị.

## 5. Phân tích từng tình huống

### Phép toán đang tính: `binaryExpression`

`trace("left", 2)` in `left` và hoàn thành. `explode("middle")` in `middle` rồi ném `IllegalStateException`. Giá trị cho vế cộng tiếp theo không tồn tại, nên `trace("right", 4)` không chạy. Câu lệnh in `result` không chạy. Nhánh `catch` in `catch=middle`.

### Tham số thứ hai lỗi: `argumentList`

`trace("arg1", 10)` chạy. `explode("arg2")` ném lỗi; `trace("arg3", 30)` chưa chạy. Vì vậy không in `accept3 called`: thân `accept3` chưa bắt đầu.

### Side effect đã hoàn thành: `unfinishedAssignment`

`++effects[0]` đã làm giá trị từ 0 thành 1. Khi `explode("assignment-failure")` ném lỗi, phép gán ngoài cùng không hoàn tất, nên `balance` vẫn là 100. Do đó dòng cuối in `balance=100, effects=1`. **Hai biến có trạng thái sau lỗi khác nhau** dù cùng xuất hiện trong một câu lệnh.

### Truy cập ngoài giới hạn: `arrayAccess`

`trace("index", 4)` in `index`, sau đó Java phát hiện chỉ số 4 vượt quá mảng có một phần tử. `ArrayIndexOutOfBoundsException` khiến phép cộng với `trace("rhs", 20)` không được thực hiện. Nhánh catch in tên kiểu lỗi cố định, không phụ thuộc cách JVM diễn đạt message.

### Câu lệnh tiếp theo không chạy: `interruptedStatements`

Chương trình in `before`. Biểu thức khởi tạo biến `value` gọi `explode("fail-statement")` và ném exception. Dòng `after` **không được thực hiện**. Sau khi catch in thông báo lỗi, lệnh nằm sau cấu trúc `try/catch` chạy và in `resume-after-catch`. Java không tiếp tục từ giữa câu lệnh khởi tạo `value`.

### Cleanup khi chuyển điều khiển: `finallyOnUnwind`

`explode("cleanup-failure")` làm thân `try` kết thúc bất thường. Trước khi handler ở cấp ngoài nhận lỗi, khối `finally` ở cấp trong được chạy và in `finally`. Vì khối này hoàn tất bình thường, ngoại lệ gốc tiếp tục đến catch ngoài, nơi in `catch=cleanup-failure`. Việc chạy `finally` không đồng nghĩa quay lại bước đang tính dở.

### Không đánh giá nhánh không được chọn: `conditionalEvaluation`

`false && failBoolean()` không gọi `failBoolean()`, nên không ném ngoại lệ. Toán tử điều kiện chọn `trace("selected", 7)`, không chạy `explode("unused")`. Đây là bỏ qua do chọn nhánh bình thường, cần phân biệt với dừng vì exception.

## 6. Nếu không có catch tại chỗ thì câu lệnh nào chạy tiếp?

Xét luồng:

```text
main()
  |
  +-- process()
        |
        +-- stepA()   → thành công, side effects còn lại
        |
        +-- stepB()   → ném exception
        |
        +-- stepC()   → không chạy
  |
  +-- phần sau lời gọi process() trong try → không chạy
  |
  +-- catch tương ứng → xử lý lỗi
  |
  +-- câu lệnh sau try/catch → chạy nếu handler hoàn tất bình thường
```

Ngoại lệ lan ra phương thức gọi khi không được xử lý tại chỗ. Nó không tự động kết thúc toàn JVM: một handler ở lớp gọi bên ngoài có thể xử lý và tiếp tục ở vị trí mới. Nếu không có handler, exception có thể đi tới uncaught-exception handler của thread.

Đối chiếu thêm:

| Điểm phát sinh | Bước không hoàn tất | Điểm tiếp tục khi xử lý được |
| --- | --- | --- |
| Khởi tạo biến `int x = bad();` | Giá trị khởi tạo và câu lệnh tiếp theo trong khối đang bị rời | Bên trong `catch` phù hợp |
| Đối số hàm `run(a(), bad(), c())` | `c()` và phần thân `run` | `catch` của phạm vi gọi |
| Điều kiện `if (badBoolean())` | Chưa chọn nhánh true/false | `catch` phù hợp |
| Điều kiện `while (badBoolean())` | Lượt đánh giá điều kiện không hoàn thành | `catch` ngoài hoặc trong cấu trúc phù hợp |
| Giá trị `return bad();` | Chưa có giá trị return bình thường | Handler được tìm qua các phạm vi |
| Đọc từng bản ghi với catch **trong** vòng lặp | Chỉ phần còn lại của xử lý bản ghi lỗi | Mã trong handler rồi logic vòng lặp kế tiếp |
| Exception thoát ra khỏi vòng lặp, catch **ngoài** | Vòng lặp không tự quay lại | Handler ngoài vòng lặp |

Nếu muốn thử lại sau lỗi, ứng dụng phải **chủ động viết vòng lặp/retry** và xem lại tác dụng phụ của lần thử trước. Việc bắt lỗi đơn thuần không có chức năng thực thi lại.

## 7. Vì sao `finally` và tài nguyên vẫn có thể chạy?

Theo JLS §14.20, khi rời một khối `try` vì exception, các phần xử lý liên quan được áp dụng theo quy tắc:

- Nếu tìm thấy `catch` phù hợp, khối catch được thực thi thay cho việc tiếp tục phần thân try.
- `finally` liên quan được thực hiện khi rời cấu trúc trong những tình huống mà ngôn ngữ quy định. Nếu `finally` hoàn tất bình thường, lý do kết thúc bất thường trước đó có thể tiếp tục được truyền đi.
- Nếu `finally` kết thúc bất thường vì ngoại lệ khác hoặc `return`, kết quả điều khiển trước có thể bị thay thế. Vì thế hạn chế `throw` và `return` trong `finally`.
- `try-with-resources` đóng tài nguyên theo cơ chế tự động. Nếu phần thân ném lỗi rồi `close()` cũng ném lỗi, ngoại lệ từ thân khối được ưu tiên và lỗi đóng thường xuất hiện trong `getSuppressed()` của ngoại lệ chính.

Các thao tác này là những **đường dọn dẹp riêng**, không phải tiếp tục đánh giá những toán hạng hoặc câu lệnh đã bị bỏ qua. Những tình huống JVM bị chấm dứt đột ngột có thể ngăn cleanup chạy như thường lệ; không diễn giải `finally` thành cam kết tuyệt đối cho mọi trường hợp.

## 8. Điều gì xảy ra với trạng thái và giao dịch?

Exception thay đổi luồng điều khiển, **không tạo phép rollback tự động**. Từ ví dụ `unfinishedAssignment`, ta biết `effects[0]` vẫn là 1. Nguyên lý tương tự áp dụng cho nhiều tác dụng phụ:

- Gán biến, thay đổi trường, cập nhật collection đã hoàn thành trước lỗi.
- Ghi một phần nội dung xuống tệp.
- Gửi một HTTP request hoặc phát một sự kiện ra hệ thống khác.
- Thay đổi dữ liệu database, tùy tình trạng transaction và cơ chế rollback thực tế.

Muốn có tính nguyên tử, cần thiết kế rõ ràng: validate trước khi ghi, dùng transaction, staging, thao tác bù (compensation), hoặc mô hình xử lý lỗi từng đơn vị dữ liệu. Không nên cố xử lý bằng một `catch (Exception)` chỉ để che dấu thất bại.

Ví dụ một biểu thức phức tạp thường làm khó phân tích:

```java
save(updateCounter() + parseInput() + recordAudit());
```

Đoạn trên là **mã minh họa, không độc lập** vì chưa định nghĩa phương thức. Khi `parseInput()` lỗi, `updateCounter()` đã có thể cập nhật trạng thái, còn `recordAudit()` và `save()` chưa chạy. Nếu nghiệp vụ cho phép, có thể đổi thiết kế để kiểm tra input trước khi tạo side effect:

```java
int parsed = parseInput();
int counted = updateCounter();
int audit = recordAudit();
save(parsed + counted + audit);
```

Đây là quyết định **đổi thứ tự xử lý có chủ đích**; không tự thay đổi thứ tự một API thực tế nếu chưa kiểm tra hợp đồng và yêu cầu nghiệp vụ.

## 9. Bài tập tự dự đoán output

Đoạn dưới đây đặt trong thân một phương thức Java hợp lệ. Nó minh họa chính xác việc một câu lệnh gán bị gián đoạn nhưng phép tăng tiền tố đã hoàn thành.

```java
int x = 4;
try {
    x = ++x + Integer.parseInt("oops");
    System.out.println("done");
} catch (NumberFormatException ex) {
    System.out.println("caught");
}
System.out.println("x=" + x);
```

Phân tích: `++x` đổi `x` thành 5, sau đó `Integer.parseInt("oops")` ném `NumberFormatException`. Phép cộng/gán ngoài cùng không có giá trị hoàn tất, nên không ghi đè `x` bằng kết quả tổng. Dòng `done` không chạy; catch in thông báo rồi dòng sau in trạng thái hiện tại.

Stdout:

```text
caught
x=5
```

**Câu hỏi kiểm tra:**

1. Nếu `bad()` là đối số thứ hai trong lời gọi ba đối số và nó ném lỗi, đối số nào có thể đã chạy, đối số nào chưa chạy và thân hàm đích có được gọi không?
2. Tại sao `++effects[0]` đã có hiệu lực trong khi phép gán `balance = ...` không hoàn tất?
3. Nếu không có catch trong phương thức hiện tại, Java có chạy câu lệnh ngay sau nơi ném rồi mới thoát method không?
4. `finally` chạy khi có exception có đồng nghĩa toàn bộ biểu thức ban đầu đã được tính tiếp không?
5. `false && mayThrow()` khác với lỗi exception giữa phép cộng thế nào?
6. Vì sao không nên giả định rằng `getMessage()` của ngoại lệ runtime luôn giống nhau trên mọi JDK?

**Gợi ý:** (1) Các đối số bên trái hoàn thành trước đó có thể đã chạy; đối số bên phải và thân hàm chưa chạy. (2) Side effect của biểu thức con hoàn thành trước lỗi vẫn tồn tại. (3) Không, exception lan ra caller. (4) Không, đó là cleanup. (5) Short-circuit là hoàn thành bình thường theo lựa chọn nhánh. (6) Message có thể phụ thuộc triển khai/ngữ cảnh; nên test kiểu lỗi và hành vi.

## 10. Các lỗi hiểu sai cần tránh

- “Hễ exception thì toàn biểu thức chưa làm gì.” **Sai:** những bước đã thực hiện và side effect có thể còn lại.
- “Catch xong, Java quay về chạy tiếp toán hạng kế tiếp.” **Sai:** điều khiển chuyển sang handler và sau handler.
- “Tham số sau tham số lỗi vẫn chạy vì cùng nằm trong dấu ngoặc hàm.” **Sai** theo thứ tự đánh giá đối số.
- “Phép gán không hoàn tất nên mọi biến bên trong vế phải giữ nguyên.” **Sai:** side effect có thể đã xảy ra.
- “Nhánh bên phải `&&` không chạy nghĩa là có exception.” **Sai:** short-circuit thông thường không tạo exception.
- “Dùng `finally` sẽ khiến phép toán lỗi chạy tiếp.” **Sai:** đó là cleanup khi rời phạm vi.
- “Không bắt exception trong loop thì loop tự sang bản ghi sau.” **Sai:** exception có thể thoát hẳn loop.
- “Exception chỉ áp dụng cho `RuntimeException`.” **Sai:** checked exception, unchecked exception và `Error` đều có thể làm dừng đường thực thi.
- “Dùng `catch (Throwable)` là bảo đảm an toàn.” **Sai:** nó bao gồm cả các `Error` nghiêm trọng mà ứng dụng thường không có khả năng phục hồi.
- “Ngoại lệ tự rollback database/file.” **Sai:** rollback phụ thuộc cơ chế riêng.
- “Java ngẫu nhiên chọn thứ tự đánh giá.” **Sai:** JLS quy định nhiều thứ tự cần tuân thủ. Vẫn nên giảm biểu thức nhiều side effect để dễ bảo trì.

## 11. Tài liệu chuẩn

- [JLS 17 §15.6 – Normal and Abrupt Completion of Evaluation](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.6): quy tắc khi biểu thức con ném ngoại lệ.
- [JLS 17 §15.7 – Evaluation Order](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.7): toán hạng, đối số và thứ tự đánh giá.
- [JLS 17 §14.1 – Normal and Abrupt Completion of Statements](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.1): các lý do kết thúc bất thường của câu lệnh.
- [JLS 17 §14.20 – The try Statement](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20): catch, finally và try-with-resources.
- [JLS 17 §15.12.4 – Runtime Evaluation of Method Invocation](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.12.4): cách đánh giá biểu thức đích và đối số của lời gọi.

**Kết quả học tập:** Khi nhìn một câu lệnh Java có ngoại lệ, bạn phải chỉ ra được *bước gây lỗi*, *những bước đã chạy*, *những bước bị bỏ qua*, *side effect còn lại*, *đường cleanup* và *điểm tiếp tục sau handler*.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-control-flow-004">Lan truyền ngoại lệ dọc theo ngăn xếp lời gọi</a>

<details>
<summary>Xem chi tiết</summary>

# Lan truyền ngoại lệ dọc theo ngăn xếp lời gọi trong Java

Khi một phương thức Java ném ngoại lệ nhưng không có khối `catch` phù hợp để xử lý, ngoại lệ **truyền ngược theo chuỗi lời gọi**. Java lần lượt rời các ngữ cảnh thực thi, thực hiện những khối `finally` hoặc thao tác đóng tài nguyên cần thiết, rồi tìm nơi bắt ngoại lệ phù hợp gần nhất. Quá trình này thường gọi là **exception propagation** và **stack unwinding** (tháo dỡ ngăn xếp lời gọi).

Hiểu cơ chế này giúp bạn giải thích vì sao một lỗi ở tầng đọc dữ liệu lại được bắt tại tầng điều phối; vì sao các câu lệnh sau `throw` không chạy; vì sao `finally` vẫn hoạt động; và vì sao stack trace chứa tên của nhiều phương thức dù ngoại lệ chỉ được ném tại một vị trí.

## 1. Ngăn xếp lời gọi và hướng ngoại lệ di chuyển

Mỗi lời gọi phương thức đang hoạt động có một **stack frame** logic chứa trạng thái thực thi, biến cục bộ và thông tin để quay lại nơi gọi. Giả sử luồng đang đi theo chuỗi:

~~~text
main() gọi outer()
    outer() gọi middle()
        middle() gọi deepest()
            deepest() ném IllegalStateException
~~~

Có thể hình dung trạng thái trước thời điểm ném như sau:

~~~text
Đỉnh call stack
┌───────────────────────────────────────────────┐
│ deepest()       ← ngoại lệ phát sinh ở đây     │
├───────────────────────────────────────────────┤
│ middle()        ← không có catch phù hợp       │
├───────────────────────────────────────────────┤
│ outer()         ← có catch phù hợp             │
├───────────────────────────────────────────────┤
│ main()          ← gọi outer()                  │
└───────────────────────────────────────────────┘
Đáy call stack

Đường lan truyền: deepest() → middle() → outer()
~~~

Các ý cần phân biệt:

- **Luồng gọi** đi từ người gọi đến phương thức được gọi: `main → outer → middle → deepest`.
- **Ngoại lệ chưa được xử lý** đi theo hướng ngược lại: `deepest → middle → outer → main`, cho đến khi gặp một bộ xử lý phù hợp hoặc hết ngăn xếp của luồng.
- Ngoại lệ là một **đối tượng `Throwable`** được ném ra. Nó không tự động tạo thành ngoại lệ mới ở từng tầng.
- Khi một phương thức kết thúc bất thường do ngoại lệ, phương thức ấy **không trả về giá trị bình thường**, dù chữ ký khai báo có kiểu trả về.
- Ngăn xếp dùng để tìm `catch` là ngăn xếp **thực thi động**: điều quan trọng là những lời gọi hiện đang bao quanh thao tác lỗi, không chỉ là quan hệ lớp hay vị trí chữ viết trong tệp nguồn.

Java Language Specification §11.3 mô tả việc tìm `catch` gần nhất bao quanh động và có thể xử lý giá trị ngoại lệ. Phần `try`/`catch`/`finally` được đặc tả chi tiết tại §14.20.

## 2. Runtime làm gì khi gặp một ngoại lệ?

Xét một lệnh như `throw new IllegalStateException("kết nối thất bại");`, hoặc một API tự phát sinh ngoại lệ. Các bước về mặt ngữ nghĩa:

1. **Hoạt động bình thường dừng tại điểm ném:** những câu lệnh còn lại trên đường đi bình thường của khối đang thực thi bị bỏ qua.
2. **Xét bộ xử lý trong ngữ cảnh hiện tại:** runtime tìm `catch` của các khối `try` bao quanh phù hợp với *kiểu thực tế* của đối tượng ngoại lệ.
3. **Nếu không có bộ xử lý phù hợp:** thực hiện những hành động bắt buộc khi rời ngữ cảnh (ví dụ `finally`, đóng tài nguyên do `try-with-resources` quản lý), rồi kết thúc bất thường lời gọi hiện tại.
4. **Quay lại ngữ cảnh gọi:** lời gọi ở tầng trước cũng kết thúc bất thường; việc tìm `catch` tiếp tục tại vị trí lời gọi đó.
5. **Khi gặp `catch` phù hợp:** khối `catch` được chạy. Nếu nó hoàn thành bình thường, quá trình lan truyền của ngoại lệ này kết thúc. Nếu nó lại `throw`, ngoại lệ tiếp tục lan truyền theo đường mới.
6. **Nếu không tầng nào bắt:** ngoại lệ thoát ra khỏi phương thức đầu vào của luồng; cơ chế xử lý ngoại lệ chưa bắt (`UncaughtExceptionHandler`) của luồng được áp dụng và luồng kết thúc.

Đây là mô hình về **luồng điều khiển**: không phải mỗi tầng “tự động ghi log”, không phải mỗi tầng tự động thêm thông báo vào ngoại lệ, và không có việc JVM chạy tất cả `catch` trên đường về. Chỉ khối `catch` phù hợp được chọn theo quy tắc ngôn ngữ; các `finally` liên quan vẫn được thực hiện trong quá trình rời khối.

## 3. Ví dụ Java 17+ hoàn chỉnh: ngoại lệ vượt qua tầng giữa

Lưu nội dung sau trong tệp `PropagationDemo.java`:

~~~java
public class PropagationDemo {
    static String deepest() {
        System.out.println("3. deepest chạy");
        throw new IllegalStateException("kết nối thất bại");
    }

    static String middle() {
        try {
            System.out.println("2. middle chạy");
            return deepest();
        } finally {
            System.out.println("4. finally của middle");
        }
    }

    static void outer() {
        try {
            System.out.println("1. outer chạy");
            System.out.println(middle());
            System.out.println("Dòng này không chạy");
        } catch (IllegalStateException ex) {
            System.out.println("5. outer bắt: " + ex.getMessage());
        } finally {
            System.out.println("6. finally của outer");
        }
    }

    public static void main(String[] args) {
        outer();
        System.out.println("7. Sau outer");
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 PropagationDemo.java
java PropagationDemo
~~~

Kết quả stdout chính xác:

~~~text
1. outer chạy
2. middle chạy
3. deepest chạy
4. finally của middle
5. outer bắt: kết nối thất bại
6. finally của outer
7. Sau outer
~~~

**Vì sao theo đúng thứ tự này?**

- `main()` gọi `outer()`; `outer()` đi vào `try` và gọi `middle()`.
- `middle()` đi vào `try`, sau đó gọi `deepest()`. `deepest()` ném `IllegalStateException` và không trở về với một `String`.
- `middle()` không có `catch`, nhưng có `finally`. JVM thực thi `finally` của `middle()` trước khi chuyển ngoại lệ ra khỏi lời gọi `middle()`.
- `outer()` có `catch (IllegalStateException ex)` bao quanh vị trí gọi `middle()`, vì vậy xử lý ngoại lệ và in dòng 5. Biểu thức `System.out.println(middle())` **không in kết quả nào** vì phần tính đối số `middle()` đã thất bại.
- `finally` của `outer()` in dòng 6. Phương thức `outer()` kết thúc bình thường vì `catch` đã xử lý ngoại lệ, và `main()` in dòng 7.

Hãy chú ý: câu lệnh `System.out.println("Dòng này không chạy")` không chạy vì thuộc đoạn của `try` sau lời gọi ném lỗi. Ngược lại, câu lệnh `System.out.println("7. Sau outer")` chạy vì nằm **sau** một lời gọi đã xử lý xong ngoại lệ.

Không thể mong `deepest()` thực hiện nốt những câu lệnh sau `throw`, cũng không thể mong `middle()` trả về giá trị khi `deepest()` kết thúc bất thường.

## 4. Khi nào quá trình lan truyền dừng, khi nào tiếp tục?

### 4.1. Bắt đúng kiểu sẽ kết thúc đường lan truyền hiện tại

Một `catch` được chọn dựa trên kiểu runtime của đối tượng ngoại lệ và quan hệ kế thừa. Ví dụ, `catch (RuntimeException ex)` có thể bắt `IllegalStateException`, vì `IllegalStateException extends RuntimeException`. `catch (IOException ex)` không bắt được `IllegalStateException`.

Trong một `try`, Java chọn **khối `catch` đầu tiên phù hợp**. Viết các `catch` từ kiểu con đến kiểu cha:

~~~java
// Đoạn minh họa nằm trong một phương thức.
try {
    throw new IllegalArgumentException("dữ liệu không hợp lệ");
} catch (IllegalArgumentException ex) {
    System.out.println("Sai đối số");
} catch (RuntimeException ex) {
    System.out.println("Lỗi runtime khác");
}
~~~

Đầu ra là `Sai đối số`. Không có bước tự chạy tiếp sang `catch (RuntimeException)` sau khi khối thứ nhất hoàn thành. Nếu đặt `catch (RuntimeException)` trước `catch (IllegalArgumentException)`, mã không biên dịch vì nhánh cụ thể ở sau không thể được chọn.

### 4.2. Bắt rồi ném lại: tiếp tục lan truyền

Một `catch` có thể ghi thêm ngữ cảnh hoặc dọn dẹp rồi ném lại chính đối tượng ngoại lệ:

~~~java
// Đoạn minh họa trong một phương thức.
try {
    callDownstream();
} catch (IllegalStateException ex) {
    System.err.println("Lỗi tại tầng trung gian");
    throw ex;
}
~~~

`throw ex;` tiếp tục lan truyền **cùng đối tượng**. Nó không giống `throw new IllegalStateException(...)` vì cách thứ hai tạo đối tượng khác; nếu cần bọc ngoại lệ, hãy truyền `ex` làm `cause`.

Nếu `catch` chỉ in log và kết thúc bình thường, lỗi coi như đã được xử lý tại đó. Người gọi không tự động nhận lại ngoại lệ. Vì vậy phải có chủ đích rõ ràng: xử lý thực sự, ném tiếp hoặc bọc lại; đừng ghi log rồi giả vờ thao tác đã thành công.

### 4.3. Ném loại ngoại lệ khác tại tầng giữa

Ví dụ phương thức thao tác cấu hình đọc tệp và chuyển lỗi I/O thành một thông điệp ở tầng nghiệp vụ:

~~~java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

// Đoạn minh họa đặt trong một lớp.
static String loadConfig(Path path) {
    try {
        return Files.readString(path);
    } catch (IOException ex) {
        throw new IllegalStateException("Không nạp được cấu hình: " + path, ex);
    }
}
~~~

`loadConfig` đã bắt `IOException` và ném ngoại lệ runtime mới. Người gọi sẽ nhìn thấy `IllegalStateException` là ngoại lệ ngoài cùng; `getCause()` trả về `IOException` gốc. Khi in stack trace, bạn thường thấy một phần `Caused by:` cho chuỗi nguyên nhân.

Việc bọc lỗi chỉ phù hợp khi tầng hiện tại bổ sung ý nghĩa hữu ích. Trong nhiều API I/O, giữ `throws IOException` có thể đơn giản và rõ hợp đồng hơn.

## 5. Checked và unchecked: cùng cách lan truyền runtime, khác kiểm tra biên dịch

Cả `IOException` (checked) và `IllegalStateException` (unchecked) đều có thể vượt qua nhiều lời gọi bằng cơ chế runtime như đã mô tả. Khác biệt nằm ở **quy tắc biên dịch**:

- Với checked exception như `IOException`, mỗi phương thức có thể để ngoại lệ thoát ra phải khai báo kiểu checked phù hợp trong `throws`, trừ khi đã bắt nó hoặc nó không thể phát sinh qua luồng điều khiển của phương thức.
- Với unchecked exception như `IllegalStateException`, `NullPointerException` và các lớp con `Error`, không bắt buộc có `throws` mặc dù chúng vẫn có thể truyền qua call stack.
- `throws` thông báo trách nhiệm cho **mã gọi ở thời điểm biên dịch**. Nó không tạo ra nhánh xử lý runtime và không chặn ngoại lệ.

Ví dụ checked exception hợp lệ:

~~~java
import java.io.IOException;

// Các phương thức nằm trong cùng một lớp.
static void lowLevel() throws IOException {
    throw new IOException("không thể đọc");
}

static void middleLevel() throws IOException {
    lowLevel();
}

static void topLevel() {
    try {
        middleLevel();
    } catch (IOException ex) {
        System.out.println(ex.getMessage());
    }
}
~~~

Khi `topLevel()` được gọi, stdout là `không thể đọc`. Nếu bỏ `throws IOException` khỏi `middleLevel()`, đoạn trên sẽ lỗi biên dịch kiểu `unreported exception IOException; must be caught or declared to be thrown`. Điều này là yêu cầu tại **định nghĩa** `middleLevel`, không phụ thuộc vào việc `topLevel` cuối cùng đã bắt lỗi.

## 6. Tác động của `finally` trong lúc tháo dỡ stack

`finally` được thực thi khi rời `try`/`catch` liên quan, dù rời do hoàn thành bình thường, `return` hay ném ngoại lệ. Đây là lý do quan trọng khiến việc “quay ngược call stack” không đồng nghĩa với bỏ qua toàn bộ mã ở các tầng giữa.

~~~java
// Đoạn minh họa bên trong một phương thức.
try {
    throw new IllegalStateException("lỗi gốc");
} finally {
    System.out.println("Dọn dẹp trước khi truyền lỗi");
}
~~~

Khối `finally` in dòng thông báo rồi ngoại lệ vẫn được truyền ra ngoài (do `finally` hoàn thành bình thường). Thứ tự này có thể lặp lại ở nhiều lời gọi, từ `finally` của tầng sâu lên `finally` của tầng nông.

**Cảnh báo về ngoại lệ bị che lấp:** nếu chính `finally` ném ngoại lệ khác, hoặc thực hiện `return`, nguyên nhân thoát khối ban đầu có thể bị thay thế. Ví dụ sau có kết quả trả về `2` dù khối `try` đã chuẩn bị `return 1`:

~~~java
// Đoạn minh họa không nên dùng trong thiết kế thực tế.
static int unsafeReturn() {
    try {
        return 1;
    } finally {
        return 2;
    }
}
~~~

Nguy hiểm hơn, `return` hoặc `throw` trong `finally` có thể che đi ngoại lệ thật sự đang lan truyền. Chỉ đặt công việc dọn dẹp phù hợp ở `finally`, và tránh viết luồng điều khiển kết thúc đột ngột tại đây.

`finally` là cơ chế đảm bảo theo ngữ nghĩa Java khi khối được rời một cách thông thường hoặc bất thường; nó **không phải lời hứa tuyệt đối** nếu JVM hoặc tiến trình bị dừng cưỡng bức, chẳng hạn `Runtime.halt` hay mất điện.

## 7. `try-with-resources`: ngoại lệ chính và ngoại lệ bị suppressed

Để quản lý đối tượng `AutoCloseable`, `try-with-resources` giúp gọi `close()` tự động khi rời khối. Các tài nguyên được đóng theo **thứ tự ngược với thứ tự khởi tạo thành công**.

Có thể cả thân `try` lẫn `close()` đều ném ngoại lệ. Trong trường hợp đó, Java ưu tiên giữ ngoại lệ chính từ thân `try`; ngoại lệ trong lúc đóng được ghi dưới dạng **suppressed exception** (truy xuất qua `getSuppressed()`), thay vì che khuất lỗi chính như một `finally` viết thiếu cẩn thận.

Chương trình độc lập `SuppressedDemo.java`:

~~~java
public class SuppressedDemo {
    static class BrokenResource implements AutoCloseable {
        @Override
        public void close() throws Exception {
            throw new Exception("lỗi đóng");
        }
    }

    public static void main(String[] args) {
        try (BrokenResource resource = new BrokenResource()) {
            throw new Exception("lỗi xử lý");
        } catch (Exception ex) {
            System.out.println("Chính: " + ex.getMessage());
            System.out.println("Suppressed: " + ex.getSuppressed()[0].getMessage());
        }
    }
}
~~~

Chạy bằng:

~~~bash
javac -encoding UTF-8 SuppressedDemo.java
java SuppressedDemo
~~~

Stdout:

~~~text
Chính: lỗi xử lý
Suppressed: lỗi đóng
~~~

Nếu thân `try` không ném ngoại lệ nhưng `close()` ném, ngoại lệ từ `close()` có thể trở thành ngoại lệ đang lan truyền. Khi đọc stack trace từ code có nhiều tài nguyên, cần kiểm tra cả `Caused by` (chuỗi nguyên nhân) lẫn `Suppressed` (các lỗi phụ khi đóng tài nguyên); chúng không phải cùng một quan hệ.

## 8. Đọc stack trace để truy ra đường gọi

`Throwable.printStackTrace()` in loại ngoại lệ, thông điệp và các khung stack liên quan. Với một ngoại lệ được tạo trong `deepest()` và phát sinh khi đang gọi từ `middle()`, `outer()`, dạng có thể giống như:

~~~text
java.lang.IllegalStateException: kết nối thất bại
    at PropagationDemo.deepest(PropagationDemo.java:4)
    at PropagationDemo.middle(PropagationDemo.java:10)
    at PropagationDemo.outer(PropagationDemo.java:19)
    at PropagationDemo.main(PropagationDemo.java:29)
~~~

Đây là **minh họa định dạng**, không phải stdout của chương trình `PropagationDemo` ở mục 3 (vì chương trình đó đã bắt ngoại lệ và chỉ in `getMessage()`). Số dòng trong stack trace phụ thuộc chính xác vào mã nguồn đã lưu và môi trường; không nên suy luận chúng từ ví dụ minh họa.

Khi phân tích trace:

1. Dòng đầu cho biết **loại ngoại lệ và thông điệp**.
2. Khung ở trên cùng thường chỉ vị trí stack trace được ghi nhận khi đối tượng ngoại lệ được tạo hoặc `fillInStackTrace()` được gọi; các khung dưới thể hiện chuỗi lời gọi vào thời điểm đó.
3. Đọc từ trên xuống để lần về các tầng gọi; đọc từ dưới lên để thấy đường đi vào lời gọi dẫn tới lỗi.
4. Nếu có `Caused by:`, lần theo nguyên nhân sâu hơn; lỗi ngoài cùng có thể chỉ là lớp bọc của lỗi gốc.
5. Nếu có `Suppressed:`, xem các lỗi phụ xảy ra khi thu dọn tài nguyên.

Một điểm thường bị hiểu nhầm: **việc lan truyền không tự động chồng thêm frame vào stack trace ở mỗi lần `throw ex;`**. Đối tượng ngoại lệ mang dấu vết được ghi lại khi tạo hoặc khi cập nhật stack trace, thường tại tầng sâu. Khi tạo ngoại lệ bọc mới, ngoại lệ bọc có stack trace riêng ở điểm bọc; ngoại lệ gốc vẫn được nối bằng `cause`.

Để chẩn đoán tốt, nên ghi log toàn bộ ngoại lệ một lần ở ranh giới chịu trách nhiệm báo lỗi (truyền chính đối tượng cho logger để giữ trace), và thêm ngữ cảnh khi chuyển tầng. Ghi log rồi ném lại ở mọi tầng thường tạo bản ghi trùng lặp mà không thêm giá trị.

## 9. Lan truyền có vượt qua ranh giới luồng không?

**Không tự động.** Call stack thuộc về một luồng đang thực thi; ngoại lệ trong luồng con không quay ngược vào stack của luồng đã gọi `Thread.start()`.

~~~java
// Đoạn minh họa: catch bên ngoài start() không bắt lỗi trong task.
try {
    Thread worker = new Thread(() -> {
        throw new IllegalStateException("lỗi của worker");
    });
    worker.start();
} catch (IllegalStateException ex) {
    // Không xử lý ngoại lệ phát sinh sau đó trong luồng worker.
}
~~~

Lời gọi `start()` diễn ra ở luồng gọi và chủ yếu bắt đầu một luồng khác. Ngoại lệ xuất hiện khi lambda thực thi trên **call stack riêng** của `worker`. Nếu worker không tự xử lý, ngoại lệ đi tới `UncaughtExceptionHandler` của worker; `catch` quanh `start()` không nhận nó.

Tương tự, khi dùng `ExecutorService.submit(Callable)`, lỗi trong tác vụ thường được giữ trong `Future`; khi gọi `future.get()`, bên nhận có thể gặp **`ExecutionException`**, lấy lỗi tác vụ bằng `getCause()`. Với `CompletableFuture.join()`, lỗi bất thường thường được phản ánh qua **`CompletionException`**. Cơ chế này chuyển **thông tin lỗi qua kết quả tác vụ**, không phải ném cùng ngoại lệ xuyên qua hai call stack độc lập.

Điều này quan trọng khi debug ứng dụng có thread pool, callback hoặc tác vụ bất đồng bộ: cần tìm nơi thực thi task, nơi thu kết quả và lớp bọc ngoại lệ; không thể chỉ đặt một `try/catch` ở vị trí khởi chạy rồi kỳ vọng bắt mọi lỗi tương lai.

## 10. Nếu không có `catch` nào phù hợp thì sao?

Đối với lời gọi đồng bộ, khi một ngoại lệ không được bắt trong toàn bộ đường gọi hiện tại, nó đạt đến ranh giới thực thi của luồng. JVM áp dụng cơ chế `Thread.UncaughtExceptionHandler` đã cấu hình; handler mặc định thường in thông tin lỗi và stack trace ra luồng lỗi chuẩn. Luồng bị kết thúc do ngoại lệ chưa bắt.

Nếu đó là luồng `main` duy nhất của một chương trình đơn giản, tiến trình thường kết thúc. Nếu là luồng worker, những luồng khác không nhất thiết dừng theo; hành vi cấp ứng dụng còn phụ thuộc cách quản lý luồng và tác vụ. Một số framework/executor **thu giữ lỗi** và trả về dưới dạng kết quả bất thường thay vì để nó đi tới handler mặc định.

Không nên dùng `catch (Throwable)` hoặc `catch (Error)` rộng ở mọi nơi để “ngăn chương trình dừng”: những lỗi nghiêm trọng của JVM có thể không thể phục hồi. Hãy bắt các ngoại lệ thực sự có cách xử lý; thiết lập xử lý lỗi ở ranh giới ứng dụng theo đúng yêu cầu quan sát và vận hành.

## 11. Những lỗi suy luận và lỗi thiết kế thường gặp

| Nhầm lẫn | Thực tế | Cách xử lý |
| --- | --- | --- |
| Ngoại lệ luôn bị bắt ngay ở phương thức ném | Nó được tìm từ ngữ cảnh gần nhất ra các tầng gọi. | Vẽ chuỗi lời gọi và đánh dấu phạm vi `try`. |
| Sau `throw`, chương trình chạy tiếp phần còn lại của `try` | Đường thực thi bình thường dừng; các `finally` phù hợp vẫn chạy. | Truy vết riêng luồng bình thường và bất thường. |
| `throws` giải quyết hoặc nuốt lỗi | `throws` chỉ khai báo với compiler, không bắt tại runtime. | Đặt `catch` ở tầng có phương án xử lý thực sự. |
| Mọi `catch` tương thích trên đường truyền đều chạy | Chỉ bộ xử lý phù hợp gần nhất được chọn cho lần ném đó. | Ghi lại nơi `catch` hoàn thành hoặc ném tiếp. |
| `finally` không liên quan tới ngoại lệ | Nó chạy trong quá trình rời khối và có thể ảnh hưởng đường lan truyền. | Dọn dẹp an toàn; tránh `return`/`throw` che lỗi gốc. |
| `throw ex` luôn cập nhật stack trace tại nơi ném lại | Ném lại cùng đối tượng thường giữ trace cũ. | Dùng `cause` khi cần tạo ngoại lệ mới kèm ngữ cảnh. |
| `catch` bên ngoài `thread.start()` bắt lỗi trong luồng mới | Mỗi luồng có call stack riêng. | Xử lý lỗi bên trong task hoặc đọc kết quả Future. |
| Ghi log cùng lỗi ở mọi tầng giúp debug tốt hơn | Dễ sinh nhiều bản ghi cho một sự cố. | Bọc đủ ngữ cảnh và log một lần tại điểm quan sát phù hợp. |
| Ngoại lệ từ `close()` luôn thay thế lỗi xử lý | `try-with-resources` có cơ chế suppressed exception. | Kiểm tra `getSuppressed()` cùng `getCause()`. |

Một cách debug thực tế là đánh dấu ba vị trí: **điểm ném**, **những khối dọn dẹp trên đường truyền**, và **điểm bắt**. Nếu không có điểm bắt, xác định ranh giới luồng hoặc cơ chế thực thi tác vụ nhận lỗi. Sau đó truy nguyên `cause` và `suppressed` để không bỏ sót nguyên nhân ban đầu.

## 12. Câu hỏi tự kiểm tra

1. **A gọi B, B gọi C, C ném `RuntimeException`. B không có `catch`, A bắt `RuntimeException`. Ai xử lý?** — A xử lý; ngoại lệ đi C → B → A, trừ khi B có đoạn `finally` làm thay đổi đường thoát.
2. **`return` sau lời gọi thất bại có chạy không?** — Không, nếu ngoại lệ chưa được bắt bên trong lời gọi/biểu thức tương ứng.
3. **Nếu B có `finally` và A có `catch`, thứ tự là gì?** — `finally` của B hoàn tất trước khi quyền điều khiển chuyển đến `catch` của A, giả sử `finally` không kết thúc bất thường.
4. **`catch (Exception)` có bắt `Error` không?** — Không; `Error` không phải lớp con của `Exception`.
5. **Ném lại cùng đối tượng bằng `throw ex` có tạo ngoại lệ mới không?** — Không; đây là cùng một đối tượng, giữ dấu vết sẵn có trừ khi bị cập nhật tường minh.
6. **`try (resource)` có thể giữ đồng thời lỗi trong body và lỗi `close()` không?** — Có, lỗi đóng có thể nằm trong `getSuppressed()` của lỗi chính.
7. **Lỗi trong `Callable` chạy bằng executor có nhảy trực tiếp vào `catch` quanh `submit()` không?** — Không; thường phải kiểm tra `Future.get()` và `ExecutionException.getCause()`.

## Tài liệu đối chiếu

- Java Language Specification 17, [§11.3 — Run-Time Handling of an Exception](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.3): cơ chế tìm `catch` trong ngữ cảnh thực thi.
- Java Language Specification 17, [§14.18 — The throw Statement](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.18): cách một câu lệnh `throw` kết thúc bất thường.
- Java Language Specification 17, [§14.20 — The try Statement](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20): lựa chọn `catch`, thực thi `finally` và `try-with-resources`.
- Java SE 17 API, [Throwable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html): `getCause()`, `getSuppressed()`, `printStackTrace()`.
- Java SE 17 API, [Thread.UncaughtExceptionHandler](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.UncaughtExceptionHandler.html) và [Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): cách quan sát lỗi ở ranh giới luồng/tác vụ.

Cốt lõi: ngoại lệ đi ngược **các lời gọi đang hoạt động** để tìm `catch` tương thích gần nhất; việc tháo dỡ stack vẫn tuân theo quy tắc `finally` và đóng tài nguyên, còn việc xử lý hoặc ném tiếp tại tầng bắt quyết định luồng thực thi sau đó.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-control-flow-005">Tìm khối catch phù hợp gần nhất</a>

<details>
<summary>Xem chi tiết</summary>

# Tìm khối `catch` phù hợp gần nhất trong Java

Khi một ngoại lệ được ném, Java cần quyết định **khối `catch` nào sẽ nhận đối tượng ngoại lệ đó**. Quy tắc cốt lõi: Java tìm **trình xử lý phù hợp gần nhất theo luồng thực thi đang hoạt động**, dựa trên kiểu thực tế của ngoại lệ và phạm vi `try/catch` bao quanh vị trí phát sinh lỗi. Nếu không có `catch` phù hợp ở điểm hiện tại, ngoại lệ tiếp tục truyền ra ngoài qua các lời gọi phương thức; trong quá trình đó, các khối `finally` phải chạy theo quy tắc của Java.

Bài học này tập trung vào quá trình tìm `catch` phù hợp và sự thay đổi luồng điều khiển. Đây là một phần của **Java Core – Exception / Luồng thực thi**.

## 1. Thế nào là “`catch` phù hợp”?

Một câu lệnh `catch` có dạng:

~~~java
catch (SomeException e) {
    // Xử lý ngoại lệ.
}
~~~

`catch` phù hợp khi **đối tượng ngoại lệ đang lan truyền có kiểu có thể gán cho kiểu tham số `catch`**. Nói cách khác, kiểu đối tượng có thể là chính kiểu trong `catch` hoặc một lớp con của kiểu đó.

Ví dụ, phân cấp liên quan:

~~~text
Throwable
└── Exception
    └── RuntimeException
        └── IllegalArgumentException
            └── NumberFormatException
~~~

Vì `NumberFormatException` là lớp con của `IllegalArgumentException`, ngoại lệ `new NumberFormatException("sai")` có thể được bắt bằng:

~~~java
catch (NumberFormatException e) { ... }
catch (IllegalArgumentException e) { ... }
catch (RuntimeException e) { ... }
catch (Exception e) { ... }
catch (Throwable e) { ... }
~~~

Những dòng trên là **các lựa chọn minh họa riêng lẻ**, không phải năm `catch` đặt liên tiếp theo thứ tự đó trong cùng một `try`. Trong mã thực tế, các `catch` phải được sắp xếp hợp lệ để nhánh sau không bị nhánh trước che khuất.

Hai điều kiện cần phân biệt:

- **Kiểu tương thích:** `catch (IllegalArgumentException e)` nhận được `NumberFormatException`, nhưng `catch (NullPointerException e)` không nhận được `NumberFormatException` vì không có quan hệ kế thừa tương ứng.
- **Nằm trên đường lan truyền:** `catch` phải thuộc một vùng xử lý bao quanh thao tác ném ngoại lệ trong **chuỗi gọi đang thực thi**. Một `catch` nằm trong phương thức khác nhưng không nằm trên đường gọi hiện tại không có cơ hội nhận lỗi.

Việc so khớp xét **kiểu đối tượng ngoại lệ đang truyền**; thông điệp trong `getMessage()` không quyết định `catch` nào được chạy. Kiểu khai báo của biến tham chiếu cũng không thay thế kiểu runtime của đối tượng khi xác định một `catch` phù hợp.

## 2. Java tìm `catch` theo thứ tự nào?

Hãy xét một ngoại lệ được ném trong phương thức đang thực thi. Có thể theo dõi quá trình bằng các bước sau:

1. **Bắt đầu tại điểm ném ngoại lệ.** Khi `throw` được thực hiện (hoặc API phát sinh ngoại lệ), phần mã bình thường sau điểm đó trong nhánh đang chạy không tiếp tục ngay.
2. **Kiểm tra vùng `try` liên quan gần nhất.** Java xét các nhánh `catch` gắn với `try` đã bao quanh điểm phát sinh ngoại lệ, theo thứ tự khai báo từ trên xuống dưới. Nhánh đầu tiên có kiểu phù hợp sẽ được chọn.
3. **Nếu chưa có nhánh phù hợp, truyền ra phạm vi ngoài.** Các vùng `try` bao quanh khác trong cùng phương thức hoặc ở phương thức gọi có thể có `catch` phù hợp.
4. **Thực thi các khối `finally` liên quan trong quá trình thoát phạm vi.** `finally` thông thường chạy dù tìm thấy `catch` ở tầng ngoài; bản thân `finally` cũng có thể ném ngoại lệ mới và thay đổi kết quả.
5. **Khi gặp `catch` phù hợp, chuyển điều khiển vào khối đó.** Nếu khối xử lý kết thúc bình thường, chương trình tiếp tục sau cấu trúc `try/catch/finally` tương ứng.
6. **Nếu không có trình xử lý phù hợp trên đường gọi**, ngoại lệ thoát khỏi phương thức ngoài cùng của luồng đang thực thi; cơ chế xử lý ngoại lệ chưa bắt của luồng/runtime sẽ tiếp nhận.

Khái niệm “gần nhất” là **gần nhất trong các vùng xử lý đang bao quanh đường thực thi**, có kiểu nhận được ngoại lệ. Nó **không** có nghĩa là dòng `catch` có số dòng nhỏ nhất, phương thức có tên gần nhất, hoặc “bất cứ `catch` nào xuất hiện ở đầu tệp”.

Một `catch` không khớp kiểu không thể “chặn tạm” rồi tự chuyển đổi ngoại lệ thành kiểu đó. Nếu ngoại lệ không phù hợp, Java tiếp tục tìm bên ngoài.

### Sơ đồ luồng tìm handler

~~~text
Một lệnh ném ngoại lệ E
         |
         v
Có catch phù hợp trong vùng try gần nhất?
         | Có
         +------> Vào catch đầu tiên phù hợp
         |
         | Không
         v
Thoát phạm vi hiện tại (thực hiện finally liên quan)
         |
         v
Có vùng try bao ngoài đang hoạt động?
         | Có
         +------> Tìm catch phù hợp tại vùng bao ngoài
         |
         | Không
         v
Ngoại lệ thoát khỏi phương thức / đi về caller
         |
         v
Lặp lại trong các caller đang hoạt động
         |
         v
Không còn handler phù hợp -> ngoại lệ chưa được bắt
~~~

Sơ đồ biểu diễn tư duy theo dõi luồng; trong ngôn ngữ Java, việc hoàn tất các khối `finally`, đóng tài nguyên và xử lý lỗi mới phát sinh có các quy tắc chi tiết hơn trình bày ở phần sau.

## 3. Ví dụ Java 17+ chạy độc lập: tìm `catch` qua nhiều tầng

Tạo tệp **`CatchSearchDemo.java`** với toàn bộ nội dung sau:

~~~java
public class CatchSearchDemo {
    public static void main(String[] args) {
        System.out.println("1) Tim catch qua cac phuong thuc");
        try {
            first();
        } catch (IllegalArgumentException e) {
            System.out.println("main catch: " + e.getMessage());
        } finally {
            System.out.println("main finally");
        }

        System.out.println("2) Catch phu hop trong try gan nhat");
        try {
            try {
                throw new NumberFormatException("abc");
            } catch (IllegalArgumentException e) {
                System.out.println(
                        "inner catch: " + e.getClass().getSimpleName());
            } finally {
                System.out.println("inner finally");
            }
        } catch (IllegalArgumentException e) {
            System.out.println("outer catch");
        }

        System.out.println("3) Catch va nem lai");
        try {
            try {
                throw new IllegalStateException("chua san sang");
            } catch (IllegalStateException e) {
                System.out.println("inner catch: " + e.getMessage());
                throw e;
            } finally {
                System.out.println("inner finally");
            }
        } catch (RuntimeException e) {
            System.out.println(
                    "outer catch: " + e.getClass().getSimpleName());
        }

        System.out.println("4) Catch cu the dung truoc catch cha");
        try {
            Integer.parseInt("7x");
        } catch (NumberFormatException e) {
            System.out.println("specific catch");
        } catch (IllegalArgumentException e) {
            System.out.println("general catch");
        }
    }

    static void first() {
        try {
            middle();
        } catch (NullPointerException e) {
            System.out.println("first null catch");
        } finally {
            System.out.println("first finally");
        }
    }

    static void middle() {
        try {
            leaf();
        } catch (IllegalStateException e) {
            System.out.println("middle state catch");
        } finally {
            System.out.println("middle finally");
        }
    }

    static void leaf() {
        throw new NumberFormatException("bad-number");
    }
}
~~~

Chạy bằng Java 17 hoặc phiên bản mới hơn:

~~~bash
javac CatchSearchDemo.java
java CatchSearchDemo
~~~

Đầu ra chính xác:

~~~text
1) Tim catch qua cac phuong thuc
middle finally
first finally
main catch: bad-number
main finally
2) Catch phu hop trong try gan nhat
inner catch: NumberFormatException
inner finally
3) Catch va nem lai
inner catch: chua san sang
inner finally
outer catch: IllegalStateException
4) Catch cu the dung truoc catch cha
specific catch
~~~

### Diễn giải tình huống 1: đi qua các phương thức

Luồng gọi:

~~~text
main()
  └── first()
        └── middle()
              └── leaf()
                    └── throw NumberFormatException
~~~

Theo dõi lần lượt:

1. `leaf()` ném đối tượng `NumberFormatException("bad-number")`.
2. `middle()` có `catch (IllegalStateException e)`. Ngoại lệ đang truyền **không phải** `IllegalStateException` hoặc lớp con của nó nên không vào nhánh này. Khối `middle finally` được in.
3. `first()` có `catch (NullPointerException e)`. Kiểu không tương thích nên không chạy nhánh `catch`. Khối `first finally` được in.
4. `main()` có `catch (IllegalArgumentException e)`. Vì `NumberFormatException extends IllegalArgumentException`, đây là **handler phù hợp đầu tiên trên đường truyền**. Chương trình in `main catch: bad-number`.
5. `finally` của `main` chạy và in `main finally`. Cấu trúc `try/catch/finally` này kết thúc bình thường, chương trình chuyển sang phần 2.

Điểm đáng chú ý: hai `finally` ở tầng trong chạy **trước** khi `catch` ở `main` bắt ngoại lệ. Các dòng `first null catch` và `middle state catch` **không** được in, vì kiểu của chúng không khớp ngoại lệ.

Quá trình rời các lời gọi do ngoại lệ đang truyền thường được gọi là **stack unwinding**. Các lời gọi bình thường còn nằm trên call stack sẽ được tháo dần cho tới điểm xử lý phù hợp, đồng thời chạy các khối dọn dẹp theo quy tắc của Java.

### Diễn giải tình huống 2: ưu tiên vùng `try` gần hơn

`NumberFormatException` được ném trong `try` bên trong. Vùng đó có `catch (IllegalArgumentException e)`, đủ để nhận vì quan hệ kế thừa. Java vào `inner catch` và tiếp tục chạy `inner finally`. Ngoại lệ đã được xử lý và **không tự động truyền sang `outer catch`**.

“Gần nhất” ở đây được thể hiện bằng quan hệ **bao lồng** giữa hai vùng `try`. Nếu đã có một nhánh `catch` phù hợp trong vùng trong, vùng ngoài không được chạy chỉ vì nó có kiểu cũng tương thích.

### Diễn giải tình huống 3: bắt xong nhưng lại ném tiếp

Khối `inner catch` nhận `IllegalStateException` rồi thực hiện `throw e;`. Đây là **một lần ném tiếp** đối tượng ngoại lệ đang bắt. Sau đó `inner finally` chạy, rồi `catch (RuntimeException e)` ở vùng ngoài nhận ngoại lệ vì `IllegalStateException extends RuntimeException`.

Ngược lại, nếu `inner catch` hoàn tất bình thường và không ném tiếp, `outer catch` sẽ không chạy. Bắt được ngoại lệ **không đồng nghĩa** với việc mọi tầng ngoài đều nhận nó; chỉ những ngoại lệ tiếp tục lan truyền mới được tìm handler tiếp.

### Diễn giải tình huống 4: thứ tự các `catch` cùng một `try`

`Integer.parseInt("7x")` ném `NumberFormatException`. Trong cùng một cấu trúc `try`, `catch (NumberFormatException e)` xuất hiện trước `catch (IllegalArgumentException e)` nên nhánh cụ thể được chọn và chỉ có `specific catch` được in.

Java kiểm tra các nhánh `catch` **theo thứ tự xuất hiện**, dừng ở nhánh phù hợp đầu tiên. Bạn nên đặt loại chuyên biệt trước loại tổng quát.

## 4. Quan hệ kiểu và thứ tự của các nhánh `catch`

Hai đoạn sau **không tương đương**:

Đúng — loại con đứng trước loại cha:

~~~java
try {
    Integer.parseInt("khong-phai-so");
} catch (NumberFormatException e) {
    System.out.println("Sai dinh dang so");
} catch (IllegalArgumentException e) {
    System.out.println("Tham so khong hop le");
}
~~~

Sai — trình biên dịch từ chối vì nhánh sau không thể nhận ngoại lệ mà nhánh trước chưa bắt:

~~~java
try {
    Integer.parseInt("khong-phai-so");
} catch (IllegalArgumentException e) {
    System.out.println("Loi tham so");
} catch (NumberFormatException e) {
    System.out.println("Loi dinh dang so");
}
~~~

Đây là **hai đoạn minh họa**, không phải chương trình độc lập. Trong đoạn sai, `catch (NumberFormatException e)` sẽ bị báo lỗi vì mọi `NumberFormatException` đều đã khớp `IllegalArgumentException` ở phía trên.

| Đối tượng ngoại lệ thực tế | `catch (NumberFormatException)` | `catch (IllegalArgumentException)` | `catch (IOException)` |
| --- | --- | --- | --- |
| `new NumberFormatException()` | Phù hợp | Phù hợp | Không |
| `new IllegalArgumentException()` | Không | Phù hợp | Không |
| `new IOException()` | Không | Không | Phù hợp |

Bảng chỉ xét **khả năng khớp kiểu từng nhánh**; kết quả của toàn bộ cấu trúc phụ thuộc cả thứ tự `catch` và các vùng `try` đang thực sự hoạt động.

### `catch (Exception)` và `catch (Throwable)`

- `catch (Exception e)` nhận các ngoại lệ thuộc nhánh `Exception`, gồm `RuntimeException` và nhiều checked exception, nhưng **không** nhận `Error`.
- `catch (Throwable t)` có thể nhận cả `Exception` và `Error`. Bắt quá rộng có thể nuốt lỗi nghiêm trọng hoặc che giấu lỗi thiết kế. Chỉ nên dùng tại ranh giới được thiết kế rõ chính sách.
- `catch (RuntimeException e)` nhận các lớp con của `RuntimeException`, không nhận những checked exception cùng nhánh `Exception` khác như `IOException`.
- Tính *checked/unchecked* không thay đổi quy tắc tìm `catch` **khi chạy**: trình biên dịch dùng phân loại checked để yêu cầu bắt hoặc khai báo; khi ngoại lệ đã phát sinh, Java tìm handler dựa trên kiểu thực tế và phạm vi.

## 5. `catch` gần nhất không nhất thiết nằm trong cùng phương thức

Xét chuỗi lời gọi:

~~~java
static void controller() {
    try {
        service();
    } catch (IllegalArgumentException e) {
        System.out.println("Du lieu khong hop le");
    }
}

static void service() {
    repository();
}

static void repository() {
    throw new IllegalArgumentException("id sai");
}
~~~

Đây là **đoạn minh họa ba phương thức**. Khi `controller()` đang thực sự gọi `service()` rồi `repository()`, ngoại lệ ném từ `repository()` có thể được `controller()` bắt. Việc hai phương thức trung gian không khai báo `throws IllegalArgumentException` không cản trở lan truyền vì ngoại lệ này là unchecked.

Tuy nhiên, một `catch` trong phương thức `controller()` **không thể** nhận ngoại lệ từ một lần gọi `repository()` thuộc chuỗi gọi hoàn toàn khác. Java không quét toàn bộ mã nguồn để chọn bất kỳ handler nào trông có vẻ phù hợp.

Nếu `service()` đã bắt và xử lý xong ngoại lệ, `controller()` cũng không nhận được chính ngoại lệ đó, trừ khi `service()` ném tiếp ngoại lệ hoặc ném ngoại lệ mới.

### `throws` có định vị handler không?

Không. Khai báo:

~~~java
static void readFile(java.nio.file.Path path)
        throws java.io.IOException {
    java.nio.file.Files.readString(path);
}
~~~

nói rằng ngoại lệ checked có thể thoát khỏi phương thức và người gọi phải đáp ứng quy tắc của trình biên dịch. Nó **không chọn** handler hay thay đổi việc so khớp kiểu tại runtime. Handler thực tế vẫn được quyết định bằng cấu trúc `try/catch` đang hoạt động.

## 6. `catch` bên trong `catch` hoặc `finally` có tự bắt lỗi mới không?

Một chi tiết dễ nhầm: **các nhánh `catch` của một `try` chỉ xử lý ngoại lệ phát sinh từ phần `try` tương ứng**, bao gồm các lời gọi trong phần đó. Khi mã **đã ở trong một khối `catch`**, ngoại lệ mới phát sinh tại chính khối xử lý **không quay lại xét các nhánh `catch` anh em** của cùng `try`.

Ví dụ:

~~~java
try {
    throw new IllegalArgumentException("loi dau");
} catch (IllegalArgumentException e) {
    throw new IllegalStateException("loi trong catch", e);
} catch (IllegalStateException e) {
    System.out.println("Khong bat loi moi o day");
}
~~~

Đây là **đoạn minh họa**, không phải chương trình độc lập. `IllegalArgumentException` ban đầu được nhánh thứ nhất bắt. Ngoại lệ `IllegalStateException` mới phát sinh **bên trong nhánh `catch` thứ nhất** sẽ đi ra vùng xử lý bao ngoài, nếu có. Nhánh `catch (IllegalStateException e)` là nhánh anh em nên **không** nhận ngoại lệ mới đó.

Nếu muốn bắt lỗi phát sinh khi xử lý ngoại lệ, hãy tổ chức một vùng `try/catch` bao ngoài hoặc một `try/catch` mới bên trong khối xử lý khi thật sự cần.

Tương tự, ngoại lệ mới phát sinh trong một khối `finally` không được các `catch` gắn với `try` đó xử lý như ngoại lệ từ thân `try`. Nó truyền tới vùng bao ngoài phù hợp.

## 7. Vai trò của `finally` khi tìm handler

Một ngoại lệ có thể đi qua nhiều khối `finally` trước khi được bắt. Thông thường, `finally` chạy khi rời vùng `try/catch` dù rời vì kết thúc bình thường, `return` hay ngoại lệ. Điều này giải thích thứ tự in ở tình huống 1 và 3.

Ví dụ đơn giản:

~~~java
try {
    try {
        throw new IllegalArgumentException("sai");
    } finally {
        System.out.println("don dep ben trong");
    }
} catch (IllegalArgumentException e) {
    System.out.println("bat o ngoai");
}
~~~

Kết quả:

~~~text
don dep ben trong
bat o ngoai
~~~

Đây là **đoạn minh họa** có thể đặt trong `main` để chạy. Khối trong không có `catch`, nhưng `finally` chạy trước khi ngoại lệ được bắt ở ngoài.

**Cảnh báo quan trọng:** Nếu `finally` tự `throw` một ngoại lệ mới, hoặc thực hiện `return` trong lúc ngoại lệ cũ đang truyền, kết quả có thể bị thay đổi: ngoại lệ gốc có thể không còn là ngoại lệ thoát ra. Vì vậy tránh `return` trong `finally` và tránh để công việc dọn dẹp che khuất lỗi chính.

Nếu dùng **try-with-resources**, các tài nguyên được đóng tự động khi ra khỏi `try`. Ngoại lệ phát sinh khi đóng có thể được ghi dưới dạng *suppressed exception* khi thân `try` đã ném lỗi chính; tìm handler cho lỗi chính vẫn theo quy tắc kiểu và phạm vi. Đây là cơ chế khác với việc ném lỗi mới thủ công trong `finally`.

## 8. Khi không tìm thấy `catch` phù hợp

Nếu Java đã đi qua mọi phạm vi xử lý thuộc chuỗi gọi mà không tìm thấy handler phù hợp, ngoại lệ trở thành **uncaught exception** đối với luồng thực thi đó. Thông thường, runtime hiển thị loại ngoại lệ, thông điệp và stack trace qua trình xử lý mặc định của luồng; hành vi cụ thể có thể được framework hoặc `Thread.UncaughtExceptionHandler` điều chỉnh.

Không nên hiểu “không được bắt” là **toàn bộ JVM chắc chắn dừng ngay** trong mọi trường hợp: một luồng nền có thể kết thúc trong khi các luồng khác vẫn hoạt động; framework có thể quản lý lỗi tại ranh giới tác vụ. Với chương trình một luồng đơn giản chạy trong `main`, ngoại lệ không được xử lý thường khiến `main` kết thúc bất thường.

Ví dụ minh họa độc lập nếu đặt trong `main`:

~~~java
throw new IllegalArgumentException("du lieu sai");
~~~

Nếu không có `catch` bao quanh, runtime báo ngoại lệ với stack trace. Không có dòng mã chạy bình thường sau lệnh `throw` trong cùng luồng điều khiển đó.

### Phân biệt ba kết quả sau một lần ném

| Tình huống | Hành vi |
| --- | --- |
| `catch` phù hợp bắt và kết thúc bình thường | Ngoại lệ không tiếp tục truyền; chạy phần còn lại sau cấu trúc theo luồng bình thường |
| `catch` phù hợp bắt rồi `throw e` hoặc ném lỗi mới | Tiếp tục tìm handler ở phạm vi ngoài cho ngoại lệ được ném ra |
| Không tìm được `catch` phù hợp | Ngoại lệ thoát khỏi đường gọi hiện tại và được xử lý như lỗi chưa bắt ở ranh giới luồng/runtime |

## 9. Cách lần theo một lỗi bằng stack trace

Khi thấy stack trace, hãy bắt đầu từ **điểm ném** và **chuỗi lời gọi** để tìm điểm nào đang thực sự bao quanh việc ném ngoại lệ. Chẳng hạn một lỗi được ném trong `leaf()`, đi qua `middle()` và `first()` đến `main()`. Những bước hữu ích:

1. Xác định **kiểu ngoại lệ** thực tế trong dòng đầu, ví dụ `NumberFormatException`.
2. Tìm vị trí phát sinh lỗi trong các frame và xác nhận các phương thức caller đang gọi lẫn nhau.
3. Kiểm tra những vùng `try/catch` thật sự bao quanh các lệnh gọi tương ứng, bắt đầu từ vùng gần nhất.
4. So sánh kiểu của từng `catch` theo quan hệ kế thừa, đồng thời xem thứ tự các nhánh trong cùng một `try`.
5. Xem khối `finally`, đóng tài nguyên hoặc `catch` ném lại để giải thích thứ tự và khả năng thay đổi ngoại lệ.
6. Nếu thấy `Caused by`, phân biệt **ngoại lệ bọc bên ngoài** với **nguyên nhân**. Handler nhận ngoại lệ đang được ném ở tầng đó; Java không tự dò kiểu trong `getCause()` để chọn `catch`.

Ví dụ, `catch (java.io.IOException e)` **không** tự động bắt một `RuntimeException` chỉ vì `runtimeException.getCause()` là `IOException`. Muốn xử lý dựa trên nguyên nhân, tầng phù hợp phải kiểm tra cause một cách chủ động hoặc thiết kế API ném đúng loại ngoại lệ.

Lưu ý: thứ tự frame trong stack trace giúp hiểu chuỗi gọi; nó **không** hiện đầy đủ mọi `try/catch` hay cho biết một `catch` cụ thể có đang nằm trong phạm vi xử lý hay không. Để kết luận chắc chắn, cần đối chiếu mã nguồn và luồng điều khiển thực tế.

## 10. Những lỗi hiểu sai phổ biến

**Sai lầm 1 — “Java tìm `catch` khớp tên ngoại lệ gần nhất trong tệp.”**

Java tìm theo **phạm vi thực thi**, không theo thứ tự văn bản toàn cục. Một nhánh `catch` trong phương thức chưa được gọi không liên quan.

**Sai lầm 2 — “`catch` cùng lớp gần nhất luôn được ưu tiên dù nó ở ngoài `try` bên trong.”**

Nếu `try` bên trong đã có một nhánh kiểu cha nhận được ngoại lệ, nó xử lý trước vùng ngoài. Java không bỏ qua handler gần để tìm một handler chuyên biệt hơn ở xa.

**Sai lầm 3 — “Các nhánh `catch` được thử hết rồi tất cả nhánh khớp đều chạy.”**

Trong **một `try`**, chỉ nhánh đầu tiên khớp được chọn. Sau khi nhánh đó xử lý xong bình thường, các nhánh còn lại không chạy.

**Sai lầm 4 — “Nhánh `catch` kiểu cha đứng trước kiểu con cũng được.”**

Nếu kiểu cha đã bao trùm hoàn toàn kiểu con, nhánh kiểu con ở sau là không thể tới; Java báo lỗi biên dịch.

**Sai lầm 5 — “`finally` chỉ chạy khi có `catch` nhận ngoại lệ.”**

`finally` thông thường chạy cả khi không có `catch` phù hợp và trong quá trình ngoại lệ đang truyền ra tầng ngoài. Những trường hợp kết thúc tiến trình hoặc VM bất thường có thể ngăn bảo đảm này; không dựa vào `finally` cho tình huống JVM bị chấm dứt đột ngột.

**Sai lầm 6 — “Một `catch` tiếp theo trong cùng cấu trúc sẽ nhận lỗi do `catch` trước ném.”**

Không. Lỗi từ mã xử lý `catch` phải tìm handler thuộc phạm vi bao ngoài hoặc một `try` khác.

**Sai lầm 7 — “Chỉ checked exception mới lan qua phương thức.”**

Cả checked và unchecked đều có thể truyền qua nhiều phương thức. Checked khác ở nghĩa vụ bắt/khai báo lúc biên dịch; tìm `catch` lúc chạy vẫn theo kiểu và phạm vi.

**Sai lầm 8 — “Bắt `Exception` là bắt mọi thứ có thể ném.”**

`Error` là nhánh khác của `Throwable` nên không được `catch (Exception e)` nhận. Tránh bắt `Throwable` một cách đại trà chỉ để cố gắng tiếp tục sau lỗi nghiêm trọng.

## 11. Bài tập tự kiểm tra

**Câu 1.** `NumberFormatException` được ném trong một `try` có `catch (IllegalArgumentException e)`. `catch` này có khớp không? Vì sao?

**Câu 2.** Phương thức `a()` gọi `b()`, `b()` gọi `c()`. `c()` ném `IllegalStateException`; `b()` chỉ có `catch (NullPointerException e)`; `a()` có `catch (RuntimeException e)`. `catch` nào được chọn?

**Câu 3.** Nếu cả vùng `try` bên trong và vùng `try` bên ngoài đều có `catch (Exception e)`, và ngoại lệ phát sinh trong vùng bên trong, vùng nào nhận trước?

**Câu 4.** Nếu `catch` bên trong nhận được ngoại lệ nhưng thực hiện `throw e;`, điều gì xảy ra tiếp theo? `finally` có thể chạy ở thời điểm nào?

**Câu 5.** Vì sao không thể đặt `catch (RuntimeException e)` trước `catch (NumberFormatException e)` trong cùng một cấu trúc `try`?

**Câu 6.** Một phương thức ném `new IllegalStateException("x", new IOException("root"))`. Nếu `catch` ngoài chỉ bắt `IOException`, nó có nhận ngoại lệ đang lan truyền không? Vì sao?

**Câu 7.** Khi không có `catch` phù hợp ở `main`, chắc chắn mọi luồng khác trong JVM đều phải dừng ngay hay không?

### Đáp án gợi ý

1. Có, vì `NumberFormatException` là lớp con của `IllegalArgumentException`.
2. `catch (RuntimeException e)` của `a()`; nhánh trong `b()` không khớp kiểu.
3. Vùng trong nhận trước nếu ngoại lệ phát sinh trong `try` của nó và nhánh đó phù hợp.
4. Ngoại lệ tiếp tục truyền ra vùng ngoài; `finally` liên quan thường chạy khi thoát khối trước khi handler ngoài nhận.
5. Vì nhánh `RuntimeException` đã nhận mọi `NumberFormatException`, khiến nhánh sau không thể đạt tới.
6. Không. Đối tượng đang truyền là `IllegalStateException`; `IOException` chỉ là nguyên nhân lưu ở `cause`.
7. Không. Ngoại lệ chưa bắt làm ảnh hưởng tới luồng đó; hệ quả toàn tiến trình phụ thuộc các luồng và môi trường quản lý tác vụ.

## 12. Ghi nhớ quy tắc cốt lõi

Khi Java gặp ngoại lệ, hãy hỏi **“vùng xử lý nào đang bao quanh điểm ném và có `catch` nhận được kiểu đối tượng đó?”**. Java ưu tiên vùng gần nhất đang hoạt động; trong cùng một vùng, ưu tiên nhánh `catch` phù hợp đầu tiên. Nếu không có, ngoại lệ truyền qua các tầng gọi, thực hiện dọn dẹp liên quan, cho đến khi gặp handler phù hợp hoặc trở thành ngoại lệ chưa bắt. Hiểu được đường truyền này giúp bạn dự đoán stdout, tìm đúng điểm lỗi và chọn vị trí xử lý ngoại lệ mà không bắt lỗi quá rộng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-control-flow-006">Vị trí tiếp tục thực thi sau khi xử lý</a>

<details>
<summary>Xem chi tiết</summary>

# Vị trí tiếp tục thực thi sau khi xử lý ngoại lệ trong Java

Một câu hỏi quan trọng khi đọc chương trình dùng `try`/`catch` là: **sau khi exception được bắt, Java chạy câu lệnh nào tiếp theo?** Java **không tự quay lại câu lệnh đã ném exception** và cũng **không tiếp tục những câu lệnh còn lại của khối `try` đã bị ngắt**. Nếu một `catch` phù hợp xử lý exception và kết thúc bình thường, luồng thực thi sẽ đi qua phần dọn dẹp cần thiết (nếu có) rồi tiếp tục tại **câu lệnh hợp lệ tiếp theo sau cấu trúc `try`/`catch`/`finally`** trong phạm vi hiện tại.

Tuy nhiên, vị trí thực tế còn phụ thuộc nơi đặt `catch`, phương thức nào đã bị tháo khỏi call stack, `finally`, `return`, `break`, `continue`, exception mới trong lúc xử lý, và việc exception có được bắt hay không. Bài này xây dựng quy tắc để xác định **đường thực thi tiếp theo** thay vì học thuộc riêng một ví dụ.

## 1. Quy tắc nền tảng: khối `try` đã ném exception không chạy tiếp

Với cấu trúc:

```java
try {
    statementA();
    statementB(); // Ném exception
    statementC();
} catch (SomeException e) {
    handle(e);
}
statementD();
```

Đây là **mã khung minh họa**, các phương thức và kiểu `SomeException` chưa được định nghĩa; không chạy độc lập. Giả sử `statementB()` ném đúng loại được `catch` bắt và `handle(e)` kết thúc bình thường, thứ tự thực thi là:

1. `statementA()` thực thi.
2. `statementB()` bắt đầu và ném exception; lời gọi đó **không hoàn tất bình thường**.
3. Java bỏ qua `statementC()` trong cùng khối `try`.
4. Java thực thi thân `catch` phù hợp, ở đây là `handle(e)`.
5. Khi `catch` kết thúc bình thường, Java thực thi `statementD()` nằm **sau toàn bộ cấu trúc `try/catch`**.

Java không “nhảy về” `statementB()` để thử lại, không tự chạy `statementC()` và không tiếp tục một phương thức đã bị thoát bất thường. Một lần thử lại chỉ xảy ra khi mã chương trình **chủ động** viết logic lặp hoặc gọi lại phương thức.

Nếu **không có exception**, các lệnh A, B, C chạy theo thứ tự; thân `catch` không chạy và sau đó mới đến D. Nếu exception **không có `catch` phù hợp**, nó tiếp tục lan truyền ra phương thức gọi; D có thể hoàn toàn không chạy.

## 2. Ví dụ đầu tiên: tiếp tục sau `catch`

Lưu mã sau thành tệp `CatchResumeDemo.java`. Đây là một chương trình đầy đủ chạy trên Java 17+:

```java
public class CatchResumeDemo {
    public static void main(String[] args) {
        System.out.println("1. Trước try");

        try {
            System.out.println("2. Đầu try");
            int number = Integer.parseInt("abc");
            System.out.println("3. Number = " + number);
        } catch (NumberFormatException e) {
            System.out.println("4. Đang xử lý NumberFormatException");
        }

        System.out.println("5. Sau try/catch");
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 CatchResumeDemo.java
java CatchResumeDemo
```

**Stdout chính xác** khi console dùng UTF-8:

```text
1. Trước try
2. Đầu try
4. Đang xử lý NumberFormatException
5. Sau try/catch
```

Lý do không có dòng số 3: `Integer.parseInt("abc")` ném `NumberFormatException`, do đó phần còn lại của câu lệnh gán và lệnh `println` kế tiếp **không chạy**. `catch` được chọn dựa trên kiểu exception. Sau khi xử lý xong, lệnh số 5 nằm ngoài cấu trúc `try/catch` được thực thi.

Đừng nhầm “exception đã được bắt” với “câu lệnh gây lỗi đã trở thành thành công”. Exception vẫn xảy ra; chương trình chỉ có một đường đi xử lý khác và có thể tiếp tục làm những công việc phù hợp.

## 3. Quy tắc tìm `catch`: vị trí bắt quyết định vị trí tiếp tục

Khi một exception được ném, JVM tìm trình xử lý phù hợp theo cấu trúc của ngôn ngữ:

- Trước hết xét những `catch` gắn với `try` bao quanh điểm ném, theo thứ tự khai báo. Một `catch` phù hợp được chọn; những `catch` còn lại của cùng `try` không chạy.
- Nếu không có handler thích hợp tại tầng hiện tại, exception tiếp tục truyền ra ngoài phạm vi chứa `try` và lên phương thức gọi. Những phương thức bị thoát bất thường không tiếp tục từ câu lệnh kế tiếp sau lời gọi gây lỗi.
- Nếu tìm thấy một `catch` ở tầng cao hơn và khối `catch` đó kết thúc bình thường, việc thực thi có thể tiếp tục **sau cấu trúc `try/catch` ở tầng đó**.
- Những `finally` và thao tác đóng tài nguyên có liên quan vẫn được xét khi các phạm vi bị thoát; chúng có thể tạo thêm exception hoặc thay đổi việc kết thúc.

Các nhánh `catch` so khớp theo quan hệ kiểu. Một `catch (RuntimeException e)` có thể bắt `NumberFormatException` vì đây là lớp con gián tiếp. Một `catch (IOException e)` không bắt `NumberFormatException`. Khi có nhánh cụ thể và tổng quát trong cùng một `try`, đặt nhánh cụ thể trước để tránh mã `catch` không thể tới.

Hình dung luồng thông thường với handler khớp:

```text
Câu lệnh trước try
        │
        ▼
      try
        │
        ├─ Không ném exception ────────────┐
        │                                 │
        └─ Ném exception                   │
                 │                        │
                 ▼                        │
          catch tương thích               │
                 │                        │
                 └───────────┐            │
                             ▼            │
                  finally (nếu có) ◀──────┘
                             │
                             ▼
                  Sau toàn bộ try/catch/finally
```

Sơ đồ này mô tả **đường kết thúc bình thường** của handler. Nếu `catch`/`finally` ném exception, hoặc có `return`/`break`/`continue`, đích thực thi được xác định theo quy tắc của những trường hợp đó.

## 4. Exception từ phương thức con: caller không tự thử lại lời gọi

Đây là tình huống rất hay bị hiểu nhầm. Khi phương thức con ném exception, phương thức gọi có thể bắt nó nếu lời gọi nằm trong `try` phù hợp. Nhưng phương thức con đã thoát bất thường; Java **không mở lại stack frame cũ** để chạy tiếp ngay sau lệnh `throw`.

Lưu tệp `CallStackResumeDemo.java`:

```java
public class CallStackResumeDemo {
    static void parseAtLowerLevel() {
        System.out.println("B. Bắt đầu trong phương thức con");
        Integer.parseInt("not-a-number");
        System.out.println("C. Cuối phương thức con");
    }

    static void invokeWithoutHandling() {
        System.out.println("A. Trước khi gọi con");
        parseAtLowerLevel();
        System.out.println("D. Sau khi gọi con");
    }

    public static void main(String[] args) {
        System.out.println("0. Trong main");
        try {
            invokeWithoutHandling();
            System.out.println("E. Sau lời gọi trong try");
        } catch (NumberFormatException e) {
            System.out.println("F. Bắt tại main");
        }
        System.out.println("G. Tiếp tục tại main");
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 --release 17 CallStackResumeDemo.java
java CallStackResumeDemo
```

**Stdout chính xác:**

```text
0. Trong main
A. Trước khi gọi con
B. Bắt đầu trong phương thức con
F. Bắt tại main
G. Tiếp tục tại main
```

Các dòng C, D, E không xuất hiện. `parseAtLowerLevel` và `invokeWithoutHandling` đã kết thúc bất thường vì không tự bắt exception; tới `main` mới có handler phù hợp. Sau handler ở `main`, câu lệnh G chạy, **không phải** D hoặc E.

Nếu muốn phương thức `invokeWithoutHandling` tiếp tục sau một lỗi đã biết, cần **đặt `try/catch` bên trong chính phương thức đó** quanh thao tác có thể lỗi, hoặc thay đổi hợp đồng của phương thức con để trả về một kết quả phù hợp.

## 5. Bắt ở bên trong hay bên ngoài lời gọi: hai kết quả khác nhau

Cùng một exception nhưng handler đặt ở đâu làm thay đổi những lệnh còn được chạy.

### Trường hợp A: phương thức con bắt lỗi, sau đó trả về bình thường

```java
static int parseOrDefault(String text) {
    try {
        return Integer.parseInt(text);
    } catch (NumberFormatException e) {
        return 0;
    }
}
```

Đây là **phương thức minh họa**, không có `main`. Với `text = "abc"`, `catch` được thực thi và `return 0` làm phương thức trả về bình thường. Caller có thể chạy lệnh ngay sau `parseOrDefault("abc")` vì lời gọi đã hoàn tất bình thường (giá trị `0`). Nhưng cần cân nhắc: sử dụng `0` làm giá trị dự phòng chỉ đúng nếu hợp đồng quy định rõ; nếu số `0` cũng là dữ liệu hợp lệ, nên cân nhắc kiểu kết quả phân biệt thành công/thất bại.

### Trường hợp B: phương thức con để lỗi đi ra; caller bắt lỗi

```java
static int parseStrict(String text) {
    return Integer.parseInt(text);
}

static void run() {
    try {
        int value = parseStrict("abc");
        System.out.println("Giá trị: " + value);
    } catch (NumberFormatException e) {
        System.out.println("Giá trị nhập sai");
    }
    System.out.println("Tiếp tục run");
}
```

Đây là **các phương thức minh họa** được đặt trong một lớp; không có `main`. Lệnh in `Giá trị:` bị bỏ qua vì lời gọi `parseStrict` không trả về bình thường. Lệnh `Tiếp tục run` nằm sau cấu trúc `try/catch` nên chạy nếu handler kết thúc bình thường.

Điểm cần nhớ: **“sau `catch`” là sau `catch` thuộc đúng phạm vi đang xử lý**, không có một vị trí tiếp tục toàn cục dành chung cho tất cả exception.

## 6. `finally` nằm trên đường rời `try/catch`

Một cấu trúc có `finally`:

```java
try {
    work();
} catch (SomeException e) {
    recover(e);
} finally {
    cleanup();
}
nextStep();
```

Đây là khung minh họa không chạy độc lập. Trường hợp `work()` ném `SomeException` và `recover(e)` kết thúc bình thường, thứ tự là: **`work` tới điểm ném → `catch` xử lý → `finally` dọn dẹp → `nextStep`**.

Nếu `work` không ném exception, thứ tự là `work` hoàn tất → `finally` → `nextStep`. Nếu exception không được bắt, `finally` vẫn được thực hiện trong quá trình exception lan truyền thông thường, nhưng `nextStep` không chạy tại phạm vi đang bị thoát.

Ví dụ hoàn chỉnh trong `FinallyResumeDemo.java`:

```java
public class FinallyResumeDemo {
    static void execute(boolean fail) {
        System.out.println("Bắt đầu");
        try {
            System.out.println("Trong try");
            if (fail) {
                throw new IllegalArgumentException("Dữ liệu sai");
            }
            System.out.println("Try thành công");
        } catch (IllegalArgumentException e) {
            System.out.println("Trong catch: " + e.getMessage());
        } finally {
            System.out.println("Trong finally");
        }
        System.out.println("Sau try/catch/finally");
    }

    public static void main(String[] args) {
        execute(false);
        System.out.println("---");
        execute(true);
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 FinallyResumeDemo.java
java FinallyResumeDemo
```

**Stdout chính xác**:

```text
Bắt đầu
Trong try
Try thành công
Trong finally
Sau try/catch/finally
---
Bắt đầu
Trong try
Trong catch: Dữ liệu sai
Trong finally
Sau try/catch/finally
```

Ví dụ cho thấy `finally` được đặt trên cả đường thành công và đường exception đã được xử lý. Đừng đặt `return` hoặc ném exception mới trong `finally` một cách tùy tiện: chúng có thể ghi đè việc kết thúc đã được xác định trước đó.

**Lưu ý giới hạn:** `finally` không phải cam kết tuyệt đối trong mọi tình huống môi trường. Tiến trình bị chấm dứt cưỡng bức, JVM gặp sự cố nghiêm trọng hoặc lời gọi `System.exit` có thể ngăn việc thực hiện cleanup thông thường. Với tài nguyên `AutoCloseable`, `try-with-resources` thường là lựa chọn ưu tiên.

## 7. `return` trong `try` hoặc `catch`: không đi tiếp sau cấu trúc

Đường đi bình thường sau `catch` chỉ xảy ra khi khối `catch` hoàn tất **bình thường**. Nếu khối `catch` thực hiện `return`, phương thức sẽ trả về cho caller sau khi thực thi `finally` cần thiết, thay vì chạy lệnh đứng sau cấu trúc.

Tệp `ReturnFinallyDemo.java`:

```java
public class ReturnFinallyDemo {
    static int compute(boolean fail) {
        try {
            System.out.println("Try");
            if (fail) {
                throw new IllegalStateException("Không tính được");
            }
            return 10;
        } catch (IllegalStateException e) {
            System.out.println("Catch");
            return -1;
        } finally {
            System.out.println("Finally");
        }
    }

    public static void main(String[] args) {
        System.out.println("Kết quả A = " + compute(false));
        System.out.println("Kết quả B = " + compute(true));
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 --release 17 ReturnFinallyDemo.java
java ReturnFinallyDemo
```

**Stdout chính xác**:

```text
Try
Finally
Kết quả A = 10
Try
Catch
Finally
Kết quả B = -1
```

Ở lần A, `return 10` trong `try` khiến `finally` chạy trước khi caller nhận `10`; `catch` không chạy. Ở lần B, `try` ném exception, `catch` in rồi `return -1`, nhưng `finally` vẫn chạy trước khi caller nhận giá trị.

Tương tự, `throw` trong `catch` khiến exception mới đi lên thay vì tiếp tục với lệnh sau cấu trúc. Lệnh `break` hoặc `continue` bên trong `try/catch` nằm trong vòng lặp cũng điều khiển việc chuyển sang vị trí khác, sau khi chạy `finally` theo quy tắc.

**Cảnh báo:** `return` trong `finally` có thể làm mất cả giá trị `return` đang chờ lẫn exception đang lan truyền; thông thường tránh viết như vậy.

## 8. Nếu chính `catch` ném exception mới thì sao?

Các nhánh `catch` của **cùng một `try`** không phải chuỗi “thử tiếp nhánh khác” sau khi một handler gặp vấn đề. Một khi `catch` đã được chọn, những `catch` đứng sau trong cùng cấu trúc **không bắt exception được ném từ thân `catch` đó**. Exception mới phải đi tới một `try` bao ngoài hoặc caller phù hợp.

Ví dụ đầy đủ ở `CatchThrowsDemo.java`:

```java
public class CatchThrowsDemo {
    public static void main(String[] args) {
        try {
            try {
                throw new NumberFormatException("chuỗi sai");
            } catch (NumberFormatException e) {
                System.out.println("Catch bên trong");
                throw new IllegalStateException("Xử lý bị lỗi", e);
            } catch (IllegalStateException e) {
                System.out.println("Catch thứ hai ở bên trong");
            } finally {
                System.out.println("Finally bên trong");
            }
        } catch (IllegalStateException e) {
            System.out.println("Catch bên ngoài: " + e.getMessage());
        }
        System.out.println("Tiếp tục bên ngoài");
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 CatchThrowsDemo.java
java CatchThrowsDemo
```

**Stdout chính xác**:

```text
Catch bên trong
Finally bên trong
Catch bên ngoài: Xử lý bị lỗi
Tiếp tục bên ngoài
```

Lý do: `NumberFormatException` được handler đầu tiên bắt, sau đó handler này ném `IllegalStateException`. Nhánh `catch (IllegalStateException e)` **cùng cấp** không được thử nữa. `finally` của cấu trúc bên trong chạy trước khi exception tiếp tục đến handler bên ngoài. Khi handler bên ngoài kết thúc bình thường, lệnh cuối của `main` mới chạy.

Sử dụng constructor chứa `cause` để giữ nguyên nhân ban đầu là `NumberFormatException`. Khi chẩn đoán, `e.getCause()` ở handler bên ngoài cho phép lần về lỗi gốc.

## 9. Nếu `finally` ném exception hoặc điều khiển luồng thì sao?

`finally` có thể thay đổi kết quả đã được xác định ở `try` hoặc `catch`. Ví dụ, một exception ban đầu đang được lan truyền, nhưng `finally` lại ném một exception khác: exception mới có thể **thay thế** exception ban đầu như kết quả trực tiếp quan sát được của toàn bộ cấu trúc.

Xét đoạn mã minh họa:

```java
try {
    throw new IllegalArgumentException("Lỗi chính");
} finally {
    throw new IllegalStateException("Lỗi trong cleanup");
}
```

Nếu được đặt trong một ngữ cảnh Java hợp lệ, exception thoát ra là `IllegalStateException` từ `finally`. Trong cách viết này, lỗi chính có thể bị che khuất. Vì vậy không chủ động `throw` hoặc `return` trong `finally` chỉ để “chắc chắn có xử lý”; ưu tiên code cleanup rõ ràng, giữ nguyên nhân và dùng `try-with-resources` khi thích hợp.

Tương tự, nếu `finally` có `return`, giá trị trả về đang chờ từ `try`/`catch` có thể bị thay thế. Java cho phép một số dạng này, nhưng chúng làm luồng thực thi khó lý giải và thường bị quy tắc code quality cảnh báo.

`finally` có thể không hoàn tất bình thường vì exception từ thao tác cleanup. Khi đó câu lệnh kế tiếp sau cấu trúc **không tự động chạy**, trừ khi tầng bao ngoài bắt exception mới và tiếp tục tại vị trí của tầng đó.

## 10. `try-with-resources`: đóng tài nguyên trước khi vào `catch` bên ngoài khối tài nguyên

Trong `try-with-resources`, Java đóng những tài nguyên `AutoCloseable` đã khởi tạo thành công khi rời khối `try`. Nếu có nhiều tài nguyên, chúng được đóng theo **thứ tự ngược với thứ tự khai báo**. Tài nguyên được đóng **trước** khi một `catch` hoặc `finally` gắn với câu lệnh `try-with-resources` ấy thực thi.

Tệp `ResourceResumeDemo.java`:

```java
public class ResourceResumeDemo {
    static class Resource implements AutoCloseable {
        private final String name;

        Resource(String name) {
            this.name = name;
            System.out.println("Mở " + name);
        }

        @Override
        public void close() {
            System.out.println("Đóng " + name);
        }
    }

    public static void main(String[] args) {
        try (Resource first = new Resource("A");
             Resource second = new Resource("B")) {
            System.out.println("Trong try");
            throw new IllegalArgumentException("Thao tác thất bại");
        } catch (IllegalArgumentException e) {
            System.out.println("Catch: " + e.getMessage());
        } finally {
            System.out.println("Finally");
        }
        System.out.println("Sau try-with-resources");
    }
}
```

Biên dịch, chạy:

```bash
javac -encoding UTF-8 --release 17 ResourceResumeDemo.java
java ResourceResumeDemo
```

**Stdout chính xác**:

```text
Mở A
Mở B
Trong try
Đóng B
Đóng A
Catch: Thao tác thất bại
Finally
Sau try-with-resources
```

Thứ tự có ý nghĩa: exception tại thân `try` không làm mã chạy tiếp ở đó; trước khi tới `catch`, hai tài nguyên đã được đóng theo B rồi A. Sau `catch` là `finally`, sau đó mới đến dòng cuối.

Khi `close()` cũng ném exception trong lúc một exception khác từ thân `try` đang được lan truyền, exception khi đóng **thường được đính kèm dưới dạng suppressed exception**, còn exception chính của thân `try` được giữ lại. Điều này khác cách viết `finally` ném lỗi mới có thể che lỗi ban đầu.

Ví dụ tối giản kiểm tra suppressed:

```java
public class SuppressedDemo {
    static class FailingClose implements AutoCloseable {
        @Override
        public void close() {
            throw new IllegalStateException("Không đóng được");
        }
    }

    public static void main(String[] args) {
        try (FailingClose resource = new FailingClose()) {
            throw new IllegalArgumentException("Thao tác lỗi");
        } catch (IllegalArgumentException e) {
            System.out.println("Chính: " + e.getMessage());
            System.out.println("Suppressed: " + e.getSuppressed().length);
            System.out.println("Phụ: " + e.getSuppressed()[0].getMessage());
        }
        System.out.println("Tiếp tục sau catch");
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 SuppressedDemo.java
java SuppressedDemo
```

**Stdout chính xác**:

```text
Chính: Thao tác lỗi
Suppressed: 1
Phụ: Không đóng được
Tiếp tục sau catch
```

Đoạn này cho thấy `catch` nhìn thấy exception chính của thân `try`, và exception khi đóng được lưu trong `getSuppressed()`. Nếu chỉ `close()` thất bại mà thân `try` không ném exception, exception từ `close()` có thể trở thành exception chính và được handler phù hợp bắt.

## 11. `catch` nằm trong hay ngoài vòng lặp?

Vòng lặp làm vị trí tiếp tục dễ nhầm. So sánh hai cấu trúc:

**`try/catch` ở bên trong vòng lặp:**

```java
for (String value : inputs) {
    try {
        process(value);
    } catch (NumberFormatException e) {
        System.out.println("Bỏ qua input sai");
    }
}
```

**`try/catch` bao ngoài toàn bộ vòng lặp:**

```java
try {
    for (String value : inputs) {
        process(value);
    }
} catch (NumberFormatException e) {
    System.out.println("Dừng vòng lặp vì input sai");
}
```

Hai đoạn đều là **khung mã minh họa**, giả định có `inputs` và `process`. Với handler bên trong, lần lặp hiện tại có thể kết thúc rồi vòng lặp xét phần tử tiếp theo. Với handler bao ngoài, khi một lượt lặp ném exception và thoát đến `catch` bên ngoài, **toàn bộ vòng lặp đã bị thoát**; Java không tự quay lại phần tử tiếp theo.

Ví dụ hoàn chỉnh tại `LoopCatchDemo.java`:

```java
public class LoopCatchDemo {
    static void insideLoop(String[] values) {
        for (String value : values) {
            try {
                System.out.println("Trong: " + Integer.parseInt(value));
            } catch (NumberFormatException e) {
                System.out.println("Trong: bỏ qua " + value);
            }
        }
        System.out.println("Trong: kết thúc vòng lặp");
    }

    static void outsideLoop(String[] values) {
        try {
            for (String value : values) {
                System.out.println("Ngoài: " + Integer.parseInt(value));
            }
        } catch (NumberFormatException e) {
            System.out.println("Ngoài: bắt lỗi và rời vòng lặp");
        }
        System.out.println("Ngoài: sau try/catch");
    }

    public static void main(String[] args) {
        String[] values = {"1", "sai", "3"};
        insideLoop(values);
        System.out.println("---");
        outsideLoop(values);
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 --release 17 LoopCatchDemo.java
java LoopCatchDemo
```

**Stdout chính xác**:

```text
Trong: 1
Trong: bỏ qua sai
Trong: 3
Trong: kết thúc vòng lặp
---
Ngoài: 1
Ngoài: bắt lỗi và rời vòng lặp
Ngoài: sau try/catch
```

Với `insideLoop`, lần lặp giá trị `"sai"` có handler tại chính lần lặp đó; sau `catch`, vòng lặp đi tới `"3"`. Với `outsideLoop`, lỗi tại `"sai"` khiến Java đi ra khỏi vòng lặp tới `catch` bên ngoài; `"3"` không còn được xử lý.

Quy tắc này không đồng nghĩa `catch` luôn có nghĩa “bỏ qua input sai”. Nếu trạng thái trung gian đã thay đổi hoặc có side effect, tiếp tục vòng lặp có thể không an toàn. Cần thiết kế chính sách xử lý theo hợp đồng và trạng thái.

## 12. `break`, `continue` và `return` thay đổi đích điều khiển

Trong các khối `try` hoặc `catch` đặt trong vòng lặp:

- `continue` chuyển điều khiển sang bước tiếp theo của vòng lặp tương ứng (hoặc nhãn vòng lặp được chỉ định), sau khi thực hiện `finally` cần thiết. Nó không đơn thuần chuyển đến câu lệnh sau khối `try/catch` của lần lặp hiện tại.
- `break` rời vòng lặp hoặc khối `switch` tương ứng (hoặc cấu trúc có nhãn) sau khi thực hiện `finally` cần thiết. Đích là câu lệnh sau vòng lặp/`switch`, không phải đầu lần lặp tiếp theo.
- `return` rời phương thức sau khi thực hiện `finally` cần thiết. Caller chỉ tiếp tục sau lời gọi khi phương thức kết thúc bình thường bằng việc trả về; nếu exception được ném tiếp ra ngoài, caller có thể phải bắt nó.
- `throw` tạo/đẩy exception và bắt đầu tìm handler phù hợp; những lệnh đứng sau `throw` trong cùng đường đi không thực hiện.

Ví dụ `continue` và `finally` trong `ContinueFinallyDemo.java`:

```java
public class ContinueFinallyDemo {
    public static void main(String[] args) {
        for (int i = 1; i <= 3; i++) {
            try {
                System.out.println("Try " + i);
                if (i == 2) {
                    continue;
                }
                System.out.println("Giữa " + i);
            } finally {
                System.out.println("Finally " + i);
            }
            System.out.println("Cuối lượt " + i);
        }
        System.out.println("Sau vòng lặp");
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 --release 17 ContinueFinallyDemo.java
java ContinueFinallyDemo
```

**Stdout chính xác**:

```text
Try 1
Giữa 1
Finally 1
Cuối lượt 1
Try 2
Finally 2
Try 3
Giữa 3
Finally 3
Cuối lượt 3
Sau vòng lặp
```

Khi `i == 2`, `continue` trong `try` làm bỏ qua `Giữa 2` và `Cuối lượt 2`, nhưng **`Finally 2` vẫn được in** trước khi tới lần lặp có `i == 3`. Ví dụ này không có exception, vì mục tiêu là hiểu cách `finally` tương tác với các dạng điều khiển luồng khác nhau.

## 13. `try` lồng nhau: bắt ở mức gần nhất phù hợp

`try` lồng nhau có thể khiến ngoại lệ được xử lý ngay trong khối bên trong hoặc lan sang bên ngoài. Nếu tầng bên trong bắt và xử lý bình thường, câu lệnh sau cấu trúc bên trong **trong khối ngoài** có thể tiếp tục. Nếu bên trong không có handler phù hợp, Java rời phần còn lại của `try` ngoài để tìm `catch` ngoài.

Chương trình `NestedTryDemo.java`:

```java
public class NestedTryDemo {
    static void handledInside() {
        try {
            System.out.println("A1");
            try {
                throw new IllegalArgumentException("Lỗi A");
            } catch (IllegalArgumentException e) {
                System.out.println("A2: catch trong");
            }
            System.out.println("A3: tiếp trong try ngoài");
        } catch (RuntimeException e) {
            System.out.println("A4: catch ngoài");
        }
        System.out.println("A5: sau toàn bộ");
    }

    static void handledOutside() {
        try {
            System.out.println("B1");
            try {
                throw new IllegalStateException("Lỗi B");
            } catch (NumberFormatException e) {
                System.out.println("B2: catch trong");
            }
            System.out.println("B3: sau try trong");
        } catch (IllegalStateException e) {
            System.out.println("B4: catch ngoài");
        }
        System.out.println("B5: sau toàn bộ");
    }

    public static void main(String[] args) {
        handledInside();
        System.out.println("---");
        handledOutside();
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 NestedTryDemo.java
java NestedTryDemo
```

**Stdout chính xác**:

```text
A1
A2: catch trong
A3: tiếp trong try ngoài
A5: sau toàn bộ
---
B1
B4: catch ngoài
B5: sau toàn bộ
```

Trường hợp A, handler trong bắt `IllegalArgumentException`, nên khối ngoài vẫn ở đường thực thi bình thường và chạy A3. Trường hợp B, handler trong chỉ bắt `NumberFormatException`, không phù hợp với `IllegalStateException`; vì vậy B2 và B3 không chạy, exception đi tới handler B4 bên ngoài.

Một đặc điểm đáng chú ý: khi một `catch` đã hoàn tất bình thường, các câu lệnh sau cấu trúc của nó được chạy **nếu** không bị một thao tác điều khiển luồng khác chi phối. Ví dụ A3 chạy do việc bắt trong thành công, nhưng B3 không chạy vì phần `try` chứa B3 đã bị thoát bất thường.

## 14. Khi không có handler phù hợp, “tiếp tục” ở đâu?

Nếu một exception không được bắt ở phương thức hiện tại, nó tiếp tục truyền lên caller. Khi không còn handler phù hợp trên đường gọi, luồng đang thực thi có thể kết thúc bất thường. Với ứng dụng console đơn giản, exception thoát khỏi `main` thường dẫn đến in stack trace ra **stderr** và trạng thái kết thúc chương trình có lỗi.

Trong ứng dụng server, một framework có thể bắt exception tại ranh giới request, trả phản hồi lỗi và tiếp tục xử lý request khác. Trong ứng dụng đa luồng, exception không được bắt có thể kết thúc thread hiện tại mà không bắt buộc mọi thread khác cùng dừng. Vì vậy không có quy tắc “ném exception là chắc chắn JVM bị dừng toàn bộ”.

Đặc biệt, `try` không tự tạo ra handler. Một khối `try` chỉ kèm `finally` nhưng không có `catch` có thể dọn dẹp khi exception xảy ra, rồi **exception tiếp tục lan truyền**. Muốn khôi phục đường đi tại phạm vi hiện tại, cần một handler phù hợp hoặc phương án điều khiển hợp lệ khác.

## 15. Bảng tra nhanh: sau tình huống X sẽ chạy lệnh nào?

| Tình huống | Kết quả của `try/catch` ở phạm vi hiện tại | Vị trí tiếp theo |
| --- | --- | --- |
| `try` hoàn tất bình thường | Không chạy `catch`; chạy `finally` nếu có | Lệnh sau toàn bộ cấu trúc |
| `try` ném exception và `catch` phù hợp hoàn tất bình thường | Bỏ phần còn lại của `try`, xử lý `catch`, chạy `finally` nếu có | Lệnh sau toàn bộ cấu trúc |
| `try` ném exception, không có `catch` phù hợp | Thoát phạm vi, chạy cleanup/finally liên quan | Tìm handler bên ngoài hoặc ở caller; không chạy phần còn lại của phạm vi bị thoát |
| `catch` ném exception mới | Bỏ phần còn lại của `catch`, thực thi `finally` liên quan | Handler của `try` bao ngoài hoặc caller |
| `catch` thực hiện `return` | Chuẩn bị giá trị trả về, chạy `finally` nếu có | Caller nhận giá trị nếu không có can thiệp mới |
| `catch` thực hiện `break` trong loop | Rời loop sau `finally` cần thiết | Lệnh sau loop tương ứng |
| `catch` thực hiện `continue` trong loop | Chuyển lần lặp sau `finally` cần thiết | Bước cập nhật/kiểm tra tiếp theo của loop tương ứng |
| `finally` ném exception mới | Thay đổi kết quả điều khiển, có thể che exception/return cũ | Tìm handler bên ngoài cho exception mới |
| `try-with-resources` đóng tài nguyên thành công rồi handler bình thường | Đóng tài nguyên trước `catch` và `finally` | Lệnh sau toàn bộ cấu trúc |
| `try-with-resources` có lỗi chính và lỗi khi đóng | Lỗi đóng có thể được gắn suppressed vào lỗi chính | Handler nhận exception chính và có thể kiểm tra suppressed |
| `catch` trong một lời gọi con trả về bình thường | Lời gọi con hoàn tất bình thường | Câu lệnh ngay sau lời gọi ở caller |
| Handler chỉ có ở caller cao hơn | Những caller trung gian thoát bất thường | Lệnh sau cấu trúc `try/catch` tại caller thực sự bắt lỗi |

Bảng nêu các đường đi phổ biến; `finally` có thể thay đổi kết quả nếu tự nó kết thúc bất thường, và việc phục hồi phải được thiết kế phù hợp với trạng thái chương trình.

## 16. Các nhầm lẫn thường gặp và cách sửa

**Nhầm 1: “Sau `catch`, Java quay lại dòng ném lỗi.”**  
Sai. `catch` không hoạt động như retry tự động. Đường thực thi bị ném lỗi đã bị bỏ qua cho phạm vi đó; muốn retry phải viết vòng lặp hoặc gọi lại thao tác, chú ý trạng thái đã thay đổi và side effect.

**Nhầm 2: “Nếu bắt lỗi ở `main`, phương thức con được chạy tiếp.”**  
Sai. Nếu exception từ phương thức con đã lan ra ngoài, stack frame của phương thức đó đã bị tháo, không được tự phục hồi. `main` tiếp tục ở vị trí sau handler của chính nó.

**Nhầm 3: “Lệnh ngay sau `throw` vẫn chạy nếu ở dưới có `catch`.”**  
Sai. `throw` làm ngắt đường bình thường tại điểm đó. Mã sau `throw` trong cùng khối theo đường không có nhánh thay thế còn có thể là mã không thể tới và gây lỗi biên dịch.

**Nhầm 4: “`finally` chỉ chạy khi lỗi.”**  
Sai. `finally` thường chạy khi rời cấu trúc dù `try` thành công, có exception, `return` hoặc `continue`. Không được lạm dụng `finally` để viết `return` ghi đè kết quả.

**Nhầm 5: “Một `catch` có thể bắt exception mới từ `catch` đứng trước cùng cấp.”**  
Sai. Exception ném từ thân handler cần handler bao ngoài hoặc caller; các handler kế bên chỉ phục vụ exception xuất phát từ khối `try` tương ứng.

**Nhầm 6: “`try/catch` trong vòng lặp và ngoài vòng lặp đều xử lý phần tử sau giống nhau.”**  
Sai. Handler trong vòng lặp có thể cho phép lần lặp tiếp theo; handler bao ngoài nghĩa là vòng lặp đã bị thoát khi exception được bắt.

**Nhầm 7: “`try-with-resources` đóng sau khi `catch` chạy xong.”**  
Sai với `catch` gắn trực tiếp với câu lệnh `try-with-resources` ấy: các tài nguyên đã khởi tạo được đóng **trước** khi handler đó chạy.

**Nhầm 8: “Đã vào `catch` là chắc chắn chương trình ổn định và tiếp tục.”**  
Sai. `catch` có thể ném lỗi mới, chứa `return` hoặc làm mất bất biến dữ liệu. Quyết định tiếp tục chỉ an toàn khi trạng thái và hợp đồng thao tác được khôi phục phù hợp.

**Nhầm 9: “Bắt `Exception` là mọi lỗi Java đều dừng tại đây.”**  
Sai. `Error` không thuộc nhánh `Exception`; ngoài ra `catch` cũng có thể ném lỗi mới. Mỗi handler chỉ bắt các đối tượng phù hợp kiểu của nó từ phạm vi `try` gắn kèm.

## 17. Cách tự suy luận một đoạn Java có exception

Khi đọc code, đừng chỉ tìm chữ `catch` gần nhất về mặt văn bản. Hãy đánh dấu **phạm vi cấu trúc** và **thứ tự thực thi**:

1. Xác định chính xác biểu thức hoặc câu lệnh có thể ném exception; liệt kê các lệnh đã chạy trước điểm ném.
2. Xác định `try` bao quanh điểm ném; tìm handler có kiểu phù hợp, xét từ cấu trúc bên trong đến bên ngoài và lên chuỗi gọi khi cần.
3. Gạch bỏ những lệnh ở phần còn lại của `try` bị ngắt; cả những câu lệnh ngay sau lời gọi trong phương thức đã bị thoát bất thường.
4. Theo dõi những phần dọn dẹp khi unwind: `try-with-resources` đóng tài nguyên, `finally` chạy theo quy tắc.
5. Xác định handler có kết thúc **bình thường** hay thực hiện `return`, `break`, `continue`, `throw` hoặc ném lỗi mới.
6. Chỉ khi đường đi của toàn bộ cấu trúc kết thúc bình thường mới đánh dấu câu lệnh kế tiếp sau `try/catch/finally` là vị trí thực thi tiếp theo.
7. Với vòng lặp, nested `try` và caller/callee, xác định chính xác cấu trúc nào vẫn đang hoạt động sau xử lý.
8. Kiểm tra side effect trước lỗi và khả năng thực sự phục hồi. Tiếp tục thực thi là hành vi về luồng điều khiển, **không bảo đảm** dữ liệu và trạng thái đã đúng.

## 18. Bài tập tự kiểm tra

**Câu 1.** Sau khi `NumberFormatException` bị bắt bởi `catch` và handler kết thúc bình thường, Java chạy tiếp phần còn lại của `try` hay câu lệnh sau toàn bộ `try/catch`?  
**Đáp án:** Câu lệnh sau toàn bộ `try/catch`, sau `finally` nếu có. Phần còn lại của `try` bị bỏ qua.

**Câu 2.** `main` gọi `A()`, `A()` gọi `B()`; `B()` ném exception, chỉ `main` có handler. Câu lệnh sau lời gọi `B()` bên trong `A()` có được chạy không?  
**Đáp án:** Không. `A()` đã bị thoát bất thường. `main` tiếp tục sau handler phù hợp của nó nếu handler hoàn tất bình thường.

**Câu 3.** Một `catch` bắt exception rồi `return 5`, bên ngoài cấu trúc còn lệnh `println("X")` trong cùng phương thức. Có in X không?  
**Đáp án:** Không, nếu không có can thiệp thay đổi luồng và câu lệnh đó chỉ nằm sau cấu trúc. Phương thức trả về cho caller; `finally` liên quan vẫn thực hiện.

**Câu 4.** Trong vòng lặp xử lý ba chuỗi, chuỗi thứ hai gây lỗi. `try/catch` ở bên trong thân vòng lặp và handler kết thúc bình thường. Chuỗi thứ ba có được xử lý không?  
**Đáp án:** Có thể có, nếu điều kiện và cấu trúc vòng lặp vẫn cho phép. Ngược lại nếu `try/catch` bao ngoài toàn bộ vòng lặp, lỗi thoát vòng lặp tới handler bên ngoài, nên không tiếp tục phần tử thứ ba tự động.

**Câu 5.** Khi dùng `try (A a = ...; B b = ...)` và thân `try` ném exception, thứ tự đóng tài nguyên là gì?  
**Đáp án:** B rồi A, với các tài nguyên đã khởi tạo thành công; việc đóng diễn ra trước `catch`/`finally` gắn trực tiếp với câu lệnh đó.

**Câu 6.** `catch` bên trong ném một exception mới. `catch` kế tiếp cùng cấp có bắt được không?  
**Đáp án:** Không. Exception mới truyền ra `try` bao ngoài hoặc caller, sau khi hoàn thành cleanup/finally liên quan.

**Câu 7.** `finally` ném exception khác khi một exception cũ đang truyền ra thì sao?  
**Đáp án:** Exception mới từ `finally` có thể thay thế exception cũ như kết quả của cấu trúc, khiến lỗi gốc bị che khuất. Đây là một lý do tránh `throw` mới vô ý trong `finally`.

**Câu 8.** Nếu `try` kết thúc bình thường và có `finally`, câu lệnh sau cấu trúc chạy trước hay sau `finally`?  
**Đáp án:** Sau `finally`, với điều kiện `finally` cũng hoàn tất bình thường.

## 19. Ghi nhớ trọng tâm

Vị trí tiếp tục sau một exception đã được xử lý **phụ thuộc nơi nó được bắt**. Nếu handler hoàn tất bình thường, Java tiếp tục **sau toàn bộ cấu trúc `try/catch/finally` chứa handler ấy**, sau khi dọn dẹp liên quan; những lệnh còn lại của `try` bị ngắt không tự chạy lại. Nếu exception lan từ phương thức con lên caller, phương thức con và các phương thức trung gian đã thoát bất thường không tự tiếp tục. `return`, `break`, `continue`, `throw`, exception mới trong handler/`finally` và `try-with-resources` đều có thể thay đổi đường đi cụ thể.

Để suy luận đúng, luôn theo dõi bốn yếu tố: **điểm ném → handler phù hợp → cleanup/finally → vị trí điều khiển tiếp theo**. Khi cả bốn được xác định, bạn có thể giải thích chính xác vì sao một dòng được chạy, bị bỏ qua, hay chỉ chạy ở tầng gọi bên ngoài.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-control-flow-007">Ngoại lệ chưa được bắt và việc kết thúc luồng hiện tại</a>

<details>
<summary>Xem chi tiết</summary>

# Ngoại lệ chưa được bắt và việc kết thúc luồng hiện tại trong Java

Một ngoại lệ (exception) được xem là **chưa được bắt** (*uncaught exception*) đối với một luồng thực thi khi nó lan truyền qua toàn bộ các lời gọi đang hoạt động của luồng mà **không gặp một `catch` phù hợp**. Trong một `Thread` Java thông thường, ngoại lệ thoát khỏi điểm bắt đầu thực thi của thread sẽ kích hoạt cơ chế báo lỗi chưa được bắt và **thread đó kết thúc**.

Điểm cốt lõi phải phân biệt:

- **Ngoại lệ chưa được bắt trong một thread** có thể chấm dứt **thread đang chạy**.
- **Kết thúc một thread** không đồng nghĩa với việc **mọi thread khác** hoặc **toàn bộ tiến trình JVM** lập tức dừng.
- **Nơi kết thúc thread** khác với **nơi báo lỗi**: một `UncaughtExceptionHandler` có thể ghi nhận sự cố, nhưng nó không đưa thread quay lại câu lệnh đã thất bại.
- Với `ExecutorService.submit()`, ngoại lệ của tác vụ có thể được **lưu trong `Future`** và được báo về người gọi qua `Future.get()`. Vì thế không được suy diễn rằng mọi ngoại lệ phát sinh trong tác vụ luôn thoát tới uncaught handler của thread.

Bài học này giải thích đường đi của ngoại lệ, thời điểm kết thúc luồng, cơ chế handler, ảnh hưởng tới ứng dụng và cách chủ động đặt ranh giới xử lý đúng.

## 1. Thế nào là “chưa được bắt”?

Xét một lời gọi:

```java
static int parseAge(String raw) {
    return Integer.parseInt(raw);
}

static void process(String raw) {
    int age = parseAge(raw);
    System.out.println("Tuổi: " + age);
}
```

Đây là **hai phương thức minh họa**, cần đặt trong một lớp Java. Nếu gọi `process("abc")`, `Integer.parseInt` ném `NumberFormatException`. Phương thức `parseAge` không có handler phù hợp nên ngoại lệ truyền ra `process`. `process` cũng không có handler nên dòng `println` không chạy. Ngoại lệ tiếp tục đi lên những lời gọi còn hoạt động.

Có hai khả năng:

1. Một caller bao quanh lời gọi bằng `try-catch` tương thích, ví dụ `catch (NumberFormatException ex)`. Ngoại lệ được bắt ở đó và chương trình có thể thực hiện chính sách phục hồi hay phản hồi lỗi.
2. Không có handler phù hợp nào trên đường thực thi. Ngoại lệ thoát ra khỏi điểm bắt đầu của thread; đây là **uncaught exception** đối với thread đó.

Không nên gọi một ngoại lệ là “uncaught” chỉ vì phương thức phát sinh không có `catch`. Nó vẫn có thể được caller ở tầng trên bắt; lúc ấy nó **đã được xử lý**, không dẫn đến kết thúc thread vì chính ngoại lệ đó.

### Đường lan truyền trên ngăn xếp gọi

```text
Thread đang chạy
  run() hoặc main()
    └── handleRequest()
          └── execute()
                └── parse()
                      └── throw NumberFormatException
                             ↓
                   parse() không có catch
                             ↓
                   execute() không có catch
                             ↓
                   handleRequest() không có catch
                             ↓
             run()/main() không có catch phù hợp
                             ↓
                Uncaught exception
                             ↓
               Thread kết thúc
```

Khi lan truyền, các lời gọi đang hoạt động bị **kết thúc bất thường** (*abrupt completion*). Các câu lệnh nằm sau điểm gọi thất bại không tự chạy tiếp. JVM thực hiện các bước tháo ngăn xếp (*stack unwinding*), trong đó những khối `finally` có hiệu lực và thao tác dọn tài nguyên theo `try-with-resources` được thực hiện theo quy tắc Java.

## 2. Thread nào kết thúc khi không có `catch`?

Mỗi `Thread` có ngăn xếp lời gọi riêng. Một ngoại lệ chưa được bắt sẽ kết thúc **thread mà lỗi thoát ra**, chứ không tự động truyền sang ngăn xếp gọi của thread khác.

Ví dụ:

- Nếu `main` gọi trực tiếp một phương thức ném ngoại lệ mà không bắt, ngoại lệ có thể thoát khỏi **main thread**.
- Nếu một `Thread` tự tạo thực thi `Runnable.run()` và lỗi thoát khỏi `run()`, **worker thread đó** kết thúc.
- Nếu thread A đã gọi `worker.start()` để tạo thread B, ngoại lệ xảy ra ở B **không tự động bị ném ngược vào A**. `start()` chỉ khởi động công việc; nó không biến B thành một lời gọi đồng bộ trong stack của A.
- Nếu A gọi `worker.join()` để đợi B kết thúc, `join()` **không tự ném lại ngoại lệ đã làm B chết**. `join()` có thể ném `InterruptedException` khi chính A bị ngắt trong lúc chờ, nhưng đó là một cơ chế khác.

Đối với một `Thread` đã kết thúc, trạng thái cuối cùng là `Thread.State.TERMINATED`. Bạn **không thể gọi `start()` lần thứ hai trên cùng đối tượng `Thread`**; hành vi đó gây `IllegalThreadStateException`. Muốn chạy công việc lại, cần tạo một thread/tác vụ mới hoặc thiết kế cơ chế retry rõ ràng.

### JVM có dừng khi main thread chết?

**Không phải lúc nào cũng vậy.** Trong một chương trình Java thông thường, JVM sẽ kết thúc khi không còn thread **non-daemon** nào đang sống (bên cạnh các cách dừng tiến trình khác như `System.exit`). Vì vậy:

- Nếu main thread có ngoại lệ chưa bắt và **không còn non-daemon thread khác**, tiến trình thường kết thúc.
- Nếu một non-daemon worker khác vẫn đang chạy, việc main thread kết thúc **không tự động làm worker đó chết**. Tiến trình có thể còn tiếp tục.
- Daemon thread **không tự giữ JVM tiếp tục chạy** khi mọi non-daemon thread đã kết thúc. Vì thế không nên dựa vào daemon thread để hoàn thành công việc quan trọng lúc ứng dụng đóng.

Cần tách ba khái niệm: **thất bại của một tác vụ**, **kết thúc một thread**, và **kết thúc JVM/process**. Chúng có thể liên quan nhưng không đồng nhất.

## 3. Ví dụ Java 17+: worker lỗi, main vẫn tiếp tục

Để quan sát kết quả một cách **ổn định, không phụ thuộc định dạng stack trace của JVM**, ta gắn một `UncaughtExceptionHandler` cho worker. Handler chỉ in thông tin xác định rồi để thread kết thúc.

Lưu toàn bộ chương trình thành **`UncaughtThreadDemo.java`**:

```java
public class UncaughtThreadDemo {

    static void doWork() {
        System.out.println("worker: bắt đầu");
        throw new IllegalStateException("không xử lý được dữ liệu");
    }

    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(
                UncaughtThreadDemo::doWork, "job-worker");

        worker.setUncaughtExceptionHandler((thread, error) -> {
            System.out.println(
                    "handler: " + thread.getName()
                    + " -> " + error.getClass().getSimpleName()
                    + ": " + error.getMessage());
        });

        System.out.println("main: khởi động worker");
        worker.start();
        worker.join();

        System.out.println("main: worker còn sống = " + worker.isAlive());
        System.out.println("main: tiếp tục sau join");
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac -encoding UTF-8 UncaughtThreadDemo.java
java UncaughtThreadDemo
```

**Stdout chính xác trong lần chạy bình thường, không có thread khác ngắt main:**

```text
main: khởi động worker
worker: bắt đầu
handler: job-worker -> IllegalStateException: không xử lý được dữ liệu
main: worker còn sống = false
main: tiếp tục sau join
```

### Diễn biến từng bước

1. Main thread tạo một đối tượng `Thread` có tên `job-worker` và gắn handler riêng bằng `setUncaughtExceptionHandler`.
2. Main in `"main: khởi động worker"` **trước** khi gọi `worker.start()`. Vì thế dòng đó xuất hiện trước dòng đầu tiên của worker.
3. Worker bắt đầu thực thi `doWork()`, in `"worker: bắt đầu"` rồi ném `IllegalStateException`.
4. Không có `catch` nào trên đường thoát của worker, nên cơ chế uncaught-exception gọi handler được gắn với thread này. Handler chạy **trong chính worker đang gặp sự cố**, in tên thread, tên lớp ngoại lệ và thông điệp.
5. Sau khi quá trình kết thúc của worker hoàn tất, `worker.join()` ở main trả về. Lúc ấy `worker.isAlive()` là `false`.
6. Main vẫn tiếp tục in dòng cuối: lỗi của worker không tự động nhảy vào `main` thành một ngoại lệ mới.

Handler **không sửa được stack đang thất bại**: sau khi handler chạy xong, worker không quay lại `doWork()` để chạy câu lệnh tiếp theo. Nếu cần thử lại, hãy đặt logic retry tại tầng điều phối và tạo nhiệm vụ mới với giới hạn/sự kiểm soát phù hợp.

## 4. Khi không gắn handler riêng: mặc định điều gì xảy ra?

Mỗi thread có thể có `Thread.UncaughtExceptionHandler` riêng. Khi một thread chuẩn bị kết thúc vì ngoại lệ chưa bắt, JVM chuyển thông tin thread và `Throwable` cho handler được xác định theo cơ chế của `Thread`.

Có ba cách thường gặp:

- **Handler riêng của thread:** đặt bằng `thread.setUncaughtExceptionHandler(handler)`.
- **Handler mặc định cấp ứng dụng:** đặt bằng `Thread.setDefaultUncaughtExceptionHandler(handler)`, có tác dụng như một điểm quan sát chung khi được cơ chế lựa chọn handler sử dụng.
- **`ThreadGroup` / hành vi mặc định:** nếu thread không có handler riêng, nhóm thread thực hiện cơ chế ủy quyền; khi không có default handler khác, triển khai Java chuẩn thường báo ngoại lệ và stack trace lên `System.err` (trừ trường hợp đặc biệt như `ThreadDeath`).

Trong môi trường phát triển phổ biến, nếu main thread ném một `IllegalStateException` không được bắt, stderr có dạng:

```text
Exception in thread "main" java.lang.IllegalStateException: ...
    at Example.someMethod(Example.java:...)
    at Example.main(Example.java:...)
```

Đây là **mẫu minh họa**, không phải output cố định: tên file, số dòng, stack trace, định dạng và nội dung thông điệp phụ thuộc mã thực tế và môi trường. Có handler tùy chỉnh thì đầu ra cũng có thể khác hoàn toàn.

### Cách đọc phần thông báo mặc định

- `Exception in thread "main"`: tên thread có ngoại lệ thoát ra.
- `java.lang.IllegalStateException`: **kiểu thực tế** của ngoại lệ.
- Phần sau dấu hai chấm: thông điệp nếu có.
- Các dòng `at ...`: đường gọi tại thời điểm stack trace được ghi; chúng gợi ý điểm phát sinh và các caller.
- Các đoạn `Caused by: ...` (nếu có): chuỗi nguyên nhân được truyền bằng `Throwable.getCause()` hoặc constructor nhận `cause`.

**Stack trace giúp chẩn đoán, nhưng không phải một API phân loại lỗi ổn định bằng chuỗi.** Không nên xây logic nghiệp vụ dựa trên việc tìm tên phương thức hoặc thông điệp trong stderr.

## 5. `UncaughtExceptionHandler` có thể và không thể làm gì?

Một handler được đăng ký bằng:

```java
thread.setUncaughtExceptionHandler((currentThread, error) -> {
    // Ghi log, đếm số tác vụ thất bại, gửi tín hiệu nội bộ...
});
```

Đây là **đoạn minh họa**; biến `thread` phải là một đối tượng `Thread` đã được tạo.

Handler nhận:

- `currentThread`: thread đang kết thúc vì lỗi.
- `error`: đối tượng `Throwable` chưa được bắt, có kiểu, message, cause và stack trace để chẩn đoán.

Những việc thường thích hợp:

1. Ghi nhận tên thread, loại ngoại lệ, nguyên nhân và ID tác vụ không nhạy cảm.
2. Thông báo một cơ chế giám sát hoặc điều phối rằng **tác vụ/thread thất bại**.
3. Quyết định ở **một tầng khác** có cần khởi chạy tác vụ mới, báo lỗi yêu cầu hoặc dừng dịch vụ có kiểm soát.
4. Giữ đủ ngữ cảnh để điều tra nhưng tránh ghi token, mật khẩu hay dữ liệu cá nhân nhạy cảm vào log.

Những giả định **không đúng**:

- Handler không phải một khối `catch` cho phép thread trở về điểm `throw`.
- Handler không tự khởi chạy lại `Runnable` đã chết.
- Handler không tự khôi phục giao dịch, hoàn tác thay đổi file hay trả tài nguyên đã rò rỉ.
- Handler không bảo đảm thông tin đã được gửi đến nơi giám sát nếu chính cơ chế log/transport thất bại.
- Không nên dựa vào việc handler ném tiếp ngoại lệ để xử lý lại: theo hợp đồng Java, ngoại lệ do `uncaughtException` tự ném ra không được biến thành cơ chế phục hồi thread đáng tin cậy.

Đặc biệt, callback được gọi trong **ngữ cảnh thread đang lỗi**. Nếu handler thực hiện một thao tác có thể bị chặn lâu, tiến trình kết thúc của thread và người gọi đang `join()` có thể bị kéo dài. Giữ handler ngắn gọn, an toàn, tránh deadlock và suy nghĩ cẩn thận về xử lý lỗi ngay trong handler.

### Handler mặc định là phạm vi rộng

`Thread.setDefaultUncaughtExceptionHandler` thay đổi chính sách báo lỗi ở cấp JVM, nên có thể ảnh hưởng tới các thread không do một module trực tiếp quản lý. Với thư viện dùng chung, thường nên để ứng dụng quyết định handler mặc định thay vì tự thay đổi chính sách toàn cục.

## 6. Bắt lỗi tại ranh giới `run()` để ứng dụng chủ động phục hồi

Nếu muốn một worker không kết thúc bất ngờ khi một thao tác nghiệp vụ thất bại, hãy thiết kế một ranh giới xử lý trong `run()` hoặc ngay quanh phần thao tác có thể lỗi:

```java
Runnable task = () -> {
    try {
        processOneMessage();
    } catch (IllegalArgumentException ex) {
        System.out.println("Bỏ qua thông điệp sai định dạng");
    }
};
```

Đây là **đoạn mã minh họa**, cần đặt trong phương thức có `processOneMessage()` và các import/phụ thuộc phù hợp. Cách này có thể giữ worker sống nếu vòng lặp xử lý các thông điệp khác được thiết kế tiếp tục chạy.

Chỉ bắt những ngoại lệ bạn **thực sự có phương án xử lý**:

- Sai dữ liệu một bản tin: ghi nhận lỗi và bỏ qua bản tin theo chính sách có thể hợp lý.
- Hết kết nối cơ sở dữ liệu: có thể thử lại theo giới hạn, chuyển tác vụ sang hàng chờ, hoặc báo trạng thái thất bại.
- Vi phạm invariant nội bộ không thể phục hồi: cố tiếp tục có thể nguy hiểm hơn kết thúc và để tầng điều phối nhận biết.
- `OutOfMemoryError`, `StackOverflowError` và các loại `Error` khác: không nên coi chúng như lỗi nghiệp vụ thông thường rồi `catch (Throwable)` để chạy tiếp.

Với `Runnable.run()` thông thường, chữ ký không cho phép khai báo `throws` một checked exception rộng như `IOException`. Vì vậy phải **bắt checked exception bên trong**, chuyển nó một cách có chủ đích sang unchecked exception, hoặc dùng API tác vụ như `Callable<V>` khi cần hợp đồng khai báo checked exception.

Ví dụ ranh giới làm việc độc lập, cần có phần dọn dẹp trước khi thread kết thúc:

```java
static void runJob() {
    try {
        performJob();
    } catch (IllegalArgumentException ex) {
        reportBadInput(ex);
    } finally {
        releaseJobResources();
    }
}
```

Đây là **khung thiết kế**, không phải chương trình độc lập; `performJob`, `reportBadInput` và `releaseJobResources` phải được định nghĩa. `finally` thường được chạy khi rời `try` dù có lỗi hay không. Tuy nhiên, `finally` cũng có thể ném ngoại lệ và làm che khuất nguyên nhân ban đầu; đóng tài nguyên `AutoCloseable` thường nên sử dụng `try-with-resources`.

## 7. `ExecutorService`: lỗi tác vụ không luôn là uncaught exception của worker thread

Trong ứng dụng đồng thời, nhiều tác vụ được giao cho một thread pool qua `ExecutorService`. Cần phân biệt **thất bại của công việc** với **ngoại lệ thoát khỏi chính vòng chạy worker thread**.

Với `executor.submit(Callable)` hoặc `submit(Runnable)`, executor trả về một `Future`. Nếu phần thân tác vụ ném ngoại lệ, quá trình thực thi tác vụ được đánh dấu thất bại; việc gọi `future.get()` báo về bằng `ExecutionException`, trong đó `getCause()` chứa nguyên nhân tác vụ.

### Ví dụ Java 17+ hoàn chỉnh: lỗi được truyền qua `Future`

Lưu tên **`FutureFailureDemo.java`**:

```java
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class FutureFailureDemo {

    public static void main(String[] args) throws InterruptedException {
        ExecutorService pool = Executors.newSingleThreadExecutor();

        try {
            Future<Integer> result = pool.submit(() -> {
                System.out.println("task: bắt đầu");
                throw new IllegalStateException("không thể tính kết quả");
            });

            try {
                int value = result.get();
                System.out.println("result: " + value);
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                System.out.println(
                        "main: task thất bại vì "
                        + cause.getClass().getSimpleName()
                        + ": " + cause.getMessage());
            }

            System.out.println("main: vẫn hoạt động");
        } finally {
            pool.shutdown();
        }
    }
}
```

Lệnh biên dịch và chạy:

```bash
javac -encoding UTF-8 FutureFailureDemo.java
java FutureFailureDemo
```

Stdout trong điều kiện chạy bình thường:

```text
task: bắt đầu
main: task thất bại vì IllegalStateException: không thể tính kết quả
main: vẫn hoạt động
```

**Giải thích:**

- `submit` trả về `Future<Integer>` đại diện cho **kết quả của tác vụ**, không phải một tham chiếu trực tiếp tới `Thread`.
- Lambda của tác vụ ném `IllegalStateException`. Trong cơ chế `Future` của executor chuẩn, lỗi được ghi nhận là kết quả thất bại của tác vụ.
- `result.get()` chờ tác vụ hoàn thành rồi ném `ExecutionException`; `getCause()` là `IllegalStateException` gốc.
- Dòng `result: ...` không chạy vì `get()` không trả về `int`.
- Main vẫn tiếp tục sau `catch`. `pool.shutdown()` yêu cầu executor dừng nhận tác vụ mới và kết thúc sau các tác vụ đã nhận. Trong ví dụ, tác vụ đã kết thúc khi `get()` trả về/ném.

Một ngoại lệ đã được **framework bắt và đưa vào `Future`** không phải là “uncaught exception thoát khỏi worker thread” chỉ vì mã trong lambda không có `catch`. Do đó handler ở cấp thread có thể không được gọi cho thất bại này.

### `execute`, `submit` và tác vụ bất đồng bộ khác

Với `Executor.execute(Runnable)`, cách ngoại lệ tác vụ thoát ra có thể khác `submit`, vì không có `Future` kết quả được trả về. Ví dụ trong `ThreadPoolExecutor` chuẩn, một ngoại lệ unchecked thoát khỏi `Runnable` được `execute` chuyển vào quá trình thất bại của worker và có thể dẫn đến handler của thread, sau đó executor có thể bố trí worker thay thế khi cần. Chi tiết đó thuộc cơ chế executor và cấu hình triển khai, **không nên đồng nhất mọi loại executor**.

Với `CompletableFuture`, lỗi có thể được quan sát bằng `get()` (bọc trong `ExecutionException`) hoặc `join()` (thường ném `CompletionException`). Hãy chọn nơi quan sát kết quả và chính sách xử lý của API đang dùng.

Điểm cần nhớ: muốn biết lỗi có trở thành **uncaught exception của thread** hay không, phải xét **ai đứng ở ranh giới thực thi** và liệu framework có bắt/lưu lỗi trước khi nó thoát khỏi thread hay không.

## 8. `try-catch` và `finally` ở đâu trong quá trình kết thúc thread?

Khi ngoại lệ chưa được bắt đang đi ngược qua stack, các khối `finally` được thực thi theo quy tắc ngôn ngữ. Ví dụ:

```java
static void job() {
    try {
        System.out.println("đang xử lý");
        throw new IllegalStateException("thất bại");
    } finally {
        System.out.println("dọn dẹp");
    }
}
```

Đây là **phương thức minh họa**. Nếu caller không bắt lỗi, phương thức in:

```text
đang xử lý
dọn dẹp
```

sau đó `IllegalStateException` tiếp tục lan lên. Không có dòng “đã thành công” nào tự xuất hiện.

### `try-with-resources` nên được ưu tiên cho tài nguyên

Nếu tài nguyên triển khai `AutoCloseable`, dùng cấu trúc:

```java
static String read(java.nio.file.Path path)
        throws java.io.IOException {
    try (var reader = java.nio.file.Files.newBufferedReader(path)) {
        return reader.readLine();
    }
}
```

Đây là **phương thức minh họa**; tệp thật phải được chỉ định khi sử dụng. Reader được đóng khi rời `try` dù thao tác đọc ném `IOException`. Nếu lỗi xuất hiện cả trong thân `try` lẫn lúc đóng, Java có thể lưu lỗi đóng dưới dạng **suppressed exception** để giữ ngoại lệ chính.

Không nên dựa hoàn toàn vào uncaught handler để đóng connection, stream hoặc lock. Handler chỉ nhận lỗi khi quá trình thất bại đã lan tới điểm kết thúc thread; vòng đời tài nguyên phải được quản lý **ngay tại nơi cấp phát/sử dụng**. Lưu ý một khối `finally` không đảm bảo chạy nếu tiến trình bị dừng bất thường, chẳng hạn `Runtime.halt()` hoặc bị hệ điều hành kết thúc cưỡng bức.

## 9. `InterruptedException` không đồng nghĩa với uncaught exception

Khi một thread đang `sleep()`, `wait()` hoặc `join()` bị ngắt, các phương thức có thể ném **`InterruptedException`**. Đây là một **checked exception**; nó báo tình huống yêu cầu ngắt/chấm dứt chờ, không mặc nhiên có nghĩa một lỗi unchecked đã thoát khỏi thread.

Trong `Runnable.run()`, ví dụ xử lý hủy có thể là:

```java
@Override
public void run() {
    try {
        while (!Thread.currentThread().isInterrupted()) {
            doOneStep();
        }
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
    } finally {
        closeResources();
    }
}
```

Đây là **khung phương thức minh họa**, giả sử lớp đang triển khai `Runnable`, `doOneStep()` có thể ném `InterruptedException` và `closeResources()` đã được định nghĩa. Gọi lại `interrupt()` giúp khôi phục cờ ngắt (các phương thức như `sleep()` thường xóa cờ khi ném `InterruptedException`), để tầng điều phối có thể quan sát trạng thái thích hợp. Trong ví dụ, `run()` sau đó kết thúc có kiểm soát; đây không phải một `InterruptedException` uncaught.

Nếu nghiệp vụ cần tiếp tục thay vì kết thúc sau khi bị ngắt, phải có chính sách rõ ràng. Không bắt `InterruptedException` rồi lặng lẽ bỏ qua vì điều đó có thể làm cơ chế hủy không hoạt động như dự định.

## 10. Nên xử lý lỗi tại điểm nào?

| Ranh giới | Mục tiêu xử lý | Gợi ý |
| --- | --- | --- |
| Trong hàm nghiệp vụ | Phát hiện lỗi, giữ ý nghĩa nguyên nhân | `throw` ngoại lệ cụ thể khi vi phạm hợp đồng |
| Tầng gọi có khả năng phục hồi | Thử lại, sửa đầu vào hoặc chuyển thành trạng thái nghiệp vụ | `catch` loại phù hợp, hạn chế retry và giữ `cause` |
| Biên `run()` / vòng xử lý tác vụ | Quyết định tác vụ thất bại có làm dừng vòng lặp hay không | Bắt lỗi có thể xử lý; đóng tài nguyên đúng phạm vi |
| `Thread.UncaughtExceptionHandler` | Ghi nhận một thread đã thất bại ngoài ý muốn | Log/giám sát ngắn gọn; không coi là nơi phục hồi stack |
| Người gọi `Future.get()` | Quan sát thành công/thất bại của tác vụ được submit | Bắt `ExecutionException`, xem `getCause()` |
| Tầng điều phối ứng dụng | Quyết định retry, cảnh báo, dừng dịch vụ hoặc phản hồi người dùng | Dựa vào chính sách và khả năng hệ thống còn an toàn |

Ví dụ, một consumer đọc thông điệp có thể xử lý lỗi định dạng ở từng thông điệp và tiếp tục với thông điệp tiếp theo. Nhưng nếu lỗi làm hỏng trạng thái nội bộ không thể khôi phục, giữ thread sống bằng cách bỏ qua ngoại lệ có thể khiến dữ liệu sai lan rộng. Không nên gộp hai tình huống này vào một `catch (Exception ignored)`.

## 11. Sai lầm thường gặp

### “Worker lỗi thì main tự nhận được lỗi”

Không đúng. `Thread.start()` tạo một luồng thực thi độc lập; `join()` chỉ chờ thread hoàn thành. Nếu cần truyền kết quả hoặc lỗi về caller, nên dùng `Future`, `CompletableFuture`, hàng đợi thông điệp, trạng thái được đồng bộ hóa đúng cách hoặc một cơ chế báo cáo được thiết kế rõ.

### “Main thread chết thì toàn bộ ứng dụng dừng ngay”

Không đúng trong mọi trường hợp. Các non-daemon thread khác có thể tiếp tục giữ JVM hoạt động. Ngược lại, ứng dụng cũng có thể kết thúc sau lỗi worker nếu không còn non-daemon thread nào giữ JVM chạy. Cần xét vòng đời thực tế của thread/process.

### “Có uncaught handler là đã bắt và phục hồi lỗi”

Handler **nhận thông báo khi thread đang kết thúc**. Nó không tương đương với `try-catch` nằm trước điểm thoát lỗi và không tự tiếp tục stack đã bị tháo. Muốn phục hồi, hãy đặt logic ở nơi có thể chủ động xử lý hoặc điều phối tác vụ mới.

### “Để `catch (Throwable)` giữ cho thread sống là an toàn”

Bắt `Throwable` có thể ôm cả các `Error` nghiêm trọng như `OutOfMemoryError`. Nhiều trường hợp ứng dụng không đủ điều kiện để tiếp tục đúng đắn. Nên chọn đúng kiểu lỗi và xác định chính sách kết thúc an toàn thay vì trả trạng thái thành công giả.

### “`finally` đảm bảo mọi sự cố đều được dọn sạch”

`finally` giúp dọn dẹp khi rời vùng `try` theo quy tắc Java nhưng không thay thế quản lý tài nguyên, không rollback mọi thay đổi và không phải cam kết trong mọi kiểu kết thúc JVM bất thường. Việc `finally` ném lỗi khác còn có thể che mất nguyên nhân ban đầu.

### “`submit` ném lỗi trong worker nên sẽ luôn in stack trace”

Không nhất thiết. Với `submit` của executor chuẩn, lỗi tác vụ thường được ghi vào `Future` và chỉ xuất hiện khi được quan sát/ghi nhận. Nếu không đọc `Future` hoặc giám sát kết quả, lỗi có nguy cơ bị bỏ quên. Vì thế cần tiêu chuẩn quan sát thành công/thất bại của từng tác vụ.

### “`join()` thành công nghĩa là tác vụ thành công”

Không đúng. `join()` chỉ cho biết thread đã kết thúc (khi trả về bình thường). Thread có thể kết thúc vì hoàn thành công việc hoặc vì ngoại lệ chưa bắt. Trạng thái `TERMINATED` không chứa kết quả nghiệp vụ; phải có kênh báo cáo riêng.

### “Có thể `start()` lại thread đã `TERMINATED`”

Không đúng. Một đối tượng `Thread` chỉ có thể `start()` một lần. Tác vụ muốn retry phải được thực thi trong một luồng mới hoặc được quản lý bởi một executor phù hợp.

## 12. Tình huống luyện tập và kết luận

**Tình huống 1:** Worker B ném `IllegalArgumentException`, không có `catch` và không có handler tùy chỉnh; main A đang đợi B bằng `join()`. Điều gì xảy ra?

B kết thúc vì ngoại lệ chưa bắt và cơ chế báo lỗi mặc định có thể in stack trace lên stderr. `join()` ở A trả về sau khi B kết thúc, trừ khi A bị ngắt. Ngoại lệ của B **không tự động bị ném lại từ `join()`**; A cần kênh khác nếu muốn biết lý do thất bại.

**Tình huống 2:** Một tác vụ được gửi bằng `ExecutorService.submit()` ném `IllegalStateException`. Có thể dùng `Future.get()` để biết lỗi không?

Có. `get()` có thể ném `ExecutionException`; `getCause()` cho biết ngoại lệ của tác vụ. Bản thân việc lỗi được lưu vào `Future` không đồng nghĩa worker thread đã chết.

**Tình huống 3:** Một worker có `try { ... } finally { ... }` nhưng không `catch`; trong `try` có ngoại lệ unchecked. Worker có tiếp tục sau `finally` không?

Nếu không có handler phù hợp ở tầng khác của thread, ngoại lệ vẫn lan lên và thread kết thúc. `finally` thường chạy khi đang tháo stack, nhưng nó **không tự nuốt ngoại lệ**.

**Tình huống 4:** Một non-daemon worker đang thực hiện vòng lặp. Main thread ném ngoại lệ chưa bắt. JVM có chắc dừng ngay không?

Không. Nếu worker vẫn sống, non-daemon worker có thể giữ JVM tiếp tục hoạt động. Ứng dụng cần chính sách kết thúc/điều phối của riêng mình.

**Tình huống 5:** Gắn `UncaughtExceptionHandler` cho worker, sau đó handler ghi log. Có thể dùng chính đối tượng `Thread` đó và gọi `start()` để retry không?

Không. Thread đã chấm dứt không thể được khởi động lại; tạo một thread mới hoặc gửi lại tác vụ qua executor.

**Điểm cần nhớ:** một ngoại lệ chỉ thực sự **uncaught đối với thread** khi nó thoát qua mọi handler còn hiệu lực đến ranh giới kết thúc thread. Khi đó thread kết thúc, handler có thể báo lỗi và thread khác có thể vẫn sống. Trong mã có executor, cần quan sát lỗi của **tác vụ** qua kết quả như `Future`; còn nơi cần phục hồi nên là ranh giới có khả năng xử lý thực tế, không phải một uncaught handler dùng thay cho `try-catch`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-control-flow-008">Giới hạn giữa ngoại lệ đồng bộ và ngoại lệ bất đồng bộ của JVM</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn giữa ngoại lệ đồng bộ và ngoại lệ bất đồng bộ của JVM

Khi nói một ngoại lệ Java là *synchronous* (đồng bộ) hoặc *asynchronous* (bất đồng bộ) **theo nghĩa của Java Virtual Machine (JVM)**, ta đang nói đến **thời điểm và nguyên nhân mà ngoại lệ có thể được ném trong luồng thực thi hiện tại**. Đây là phân loại về *cơ chế phát sinh ngoại lệ*, không phải phân loại về cách viết chương trình đa luồng, API bất đồng bộ, hay checked/unchecked exception.

Trong Java SE 21, Java Language Specification (JLS) §11.1.2–§11.1.3 và Java Virtual Machine Specification (JVMS) §2.10 xác định ranh giới này:

- **Ngoại lệ đồng bộ** gắn với một hành động cụ thể của thread đang thực thi, tại vị trí mà đặc tả cho phép hành động đó ném ngoại lệ. Ví dụ: chia số nguyên cho 0, truy cập chỉ số mảng ngoài phạm vi, thực thi câu lệnh `throw` hoặc gặp lỗi khi liên kết lớp.
- **Ngoại lệ bất đồng bộ của JVM** có thể phát sinh tại một điểm không được gắn trực tiếp với phép toán cụ thể trong mã ứng dụng, do JVM gặp lỗi nội bộ hoặc giới hạn tài nguyên nghiêm trọng. Theo Java SE 21, ngoại lệ bất đồng bộ loại này thuộc nhánh `VirtualMachineError`; việc phát sinh rất hiếm và JVM được phép trì hoãn trong một khoảng thực thi nhỏ nhưng hữu hạn.
- **Cả hai vẫn là ngoại lệ Java chính xác (*precise exceptions*)**: khi quyền điều khiển chuyển sang bộ xử lý ngoại lệ, các hiệu ứng nhìn thấy được phải phù hợp với một điểm ném nhất quán trên đường thực thi.

Ví dụ dễ gây nhầm: một tác vụ chạy bằng `CompletableFuture.supplyAsync(...)` rồi ném `IllegalStateException` **không vì thế mà trở thành “JVM asynchronous exception”**. Đây là lỗi thông thường được ném trong thread thực thi tác vụ; kết quả thất bại của tác vụ chỉ được chuyển cho thread khác qua API bất đồng bộ.

## 1. “Đồng bộ” ở đây nghĩa là gì?

Theo JLS §11.1.2, có bốn nhóm nguyên nhân khiến ngoại lệ được ném:

| Nguyên nhân | Ví dụ | Cách hiểu |
| --- | --- | --- |
| Câu lệnh `throw` | `throw new IllegalArgumentException("bad input");` | Chương trình chủ động ném ngoại lệ tại câu lệnh được thực thi. |
| Câu lệnh `assert` được bật và điều kiện sai | `assert value > 0;`, khi chạy với `-ea` và điều kiện sai | JVM ném `AssertionError` tại việc thực thi câu lệnh `assert`. Nếu assertion bị tắt, điều kiện không được đánh giá. |
| JVM phát hiện tình huống bất thường **đồng bộ** | `x / zero` với phép chia nguyên và mẫu số 0; `array[index]` sai giới hạn; lỗi loading/linking | Lỗi xảy ra ở thao tác có khả năng phát sinh ngoại lệ được ngôn ngữ hoặc JVM xác định. Giới hạn tài nguyên cũng có thể được phát hiện đồng bộ. |
| JVM phát sinh ngoại lệ **bất đồng bộ** | Lỗi nội bộ hoặc giới hạn tài nguyên JVM được phát hiện ngoài thời điểm gắn với một phép toán cụ thể | Trường hợp hiếm; theo Java SE 21, loại ngoại lệ là lớp con `VirtualMachineError`. |

Trong hàng thứ nhất, `throw` là hành vi chủ động của mã Java. Hàng thứ ba bao gồm những ngoại lệ được JVM phát hiện khi thực hiện mã; chúng **không có nghĩa** rằng JVM có thể tự ném chúng bất cứ lúc nào. Ví dụ, `ArithmeticException` do chia số nguyên cho 0 gắn với phép chia cụ thể.

Một lỗi hệ thống có thể khiến thao tác I/O bị thất bại rồi API ném `IOException` tại điểm thực hiện thao tác. Việc thiết bị I/O hoạt động ở bên ngoài JVM không tự biến `IOException` thành ngoại lệ bất đồng bộ theo JLS. Cũng vậy, lỗi đọc kết quả `Future.get()` không tự thuộc loại JVM asynchronous exception.

**Đồng bộ không đồng nghĩa checked; bất đồng bộ không đồng nghĩa unchecked “vì chạy trên thread khác”.** Checked/unchecked là phân loại dựa trên cây lớp ngoại lệ và quy tắc `catch`/`throws` lúc biên dịch, tách biệt với thời điểm phát sinh. Trong Java SE 21, ngoại lệ bất đồng bộ của JVM thuộc `VirtualMachineError` (nhánh `Error`, unchecked), nhưng phần lớn unchecked exceptions khác như `NullPointerException` vẫn phát sinh đồng bộ.

## 2. Ranh giới đặc tả: những gì JVM được phép làm

### Ngoại lệ đồng bộ bị ràng buộc bởi điểm phát sinh

JLS §11.1.2 xác định ngoại lệ đồng bộ không được ném tại vị trí tùy tiện. Chúng được phát sinh ở nơi mà việc đánh giá biểu thức hoặc thực thi câu lệnh có thể có kết quả đó theo đặc tả.

Ví dụ:

```java
int[] items = {4, 8};
int value = items[2]; // ArrayIndexOutOfBoundsException tại phép truy cập sai
```

Đây là đoạn minh họa trong thân phương thức. Truy cập `items[2]` là thao tác làm phát sinh lỗi; không nên hình dung lỗi này “rơi từ ngoài vào” trong lúc một lệnh bất kỳ đang chạy.

Ngoại lệ có thể được ném trực tiếp bằng `throw` hoặc do JVM tạo khi thực hiện opcode tương ứng. Ở mức bytecode, lệnh `athrow` thực hiện việc ném một đối tượng thuộc `Throwable`; nhưng **không phải mọi ngoại lệ đồng bộ đều đòi hỏi có lệnh `athrow` tường minh trong mã nguồn**.

Các lỗi loading, linking, initializing (như một số `LinkageError`) có thể phát sinh khi JVM phải thực hiện hoạt động tương ứng. Những lỗi đó vẫn tuân theo quy tắc điểm phát sinh đã được xác định cho hành động liên quan.

### Ngoại lệ bất đồng bộ không bị gắn với một phép toán nguồn cụ thể

JLS §11.1.3 định nghĩa ngoại lệ bất đồng bộ là ngoại lệ **có khả năng xảy ra ở bất kỳ điểm nào** trong quá trình thực thi chương trình, khi JVM gặp lỗi nội bộ hoặc giới hạn tài nguyên khiến nó không thể duy trì ngữ nghĩa Java. Theo đặc tả Java SE 21, lớp ngoại lệ được ném thuộc nhánh `VirtualMachineError`.

Cụm “có khả năng ở bất kỳ điểm nào” cần hiểu đúng:

1. Không có nghĩa JVM buộc phải ném lỗi ngay tức khắc ở một lệnh nguồn bất kỳ do ứng dụng chọn.
2. Đặc tả **cho phép một lượng thực thi nhỏ nhưng hữu hạn** trước khi ném ngoại lệ bất đồng bộ. Cách phát hiện và điểm kiểm tra cụ thể là chi tiết triển khai.
3. JVM có thể chèn các điểm kiểm tra phù hợp với mã đã tối ưu. Đặc tả nêu việc kiểm tra tại những chỉ thị chuyển luồng điều khiển như một **ví dụ triển khai**, không phải lời bảo đảm rằng mọi JVM chỉ kiểm tra tại những chỉ thị đó.
4. Dù thời điểm phát hiện linh hoạt hơn, ngoại lệ vẫn phải giữ **tính chính xác của trạng thái quan sát được** tại điểm chuyển quyền điều khiển.

Ngoại lệ loại này rất hiếm; không nên xây chương trình dựa vào giả định nó sẽ xuất hiện đều đặn, có thể kích hoạt tại một dòng mã cố định hoặc luôn có thể phục hồi.

## 3. `VirtualMachineError`: cùng một lớp lỗi có thể có hai cơ chế xuất hiện

`VirtualMachineError` là lớp con của `Error`, được dùng cho các tình huống JVM bị hỏng hoặc hết tài nguyên để tiếp tục thực thi đúng. Những lớp con thường gặp cần biết gồm `OutOfMemoryError`, `StackOverflowError`, `InternalError` và `UnknownError`.

**Không được kết luận chỉ từ tên lớp rằng một ngoại lệ cụ thể chắc chắn là bất đồng bộ.** JLS §11.1.3 chỉ rõ:

- `StackOverflowError` có thể được ném **đồng bộ** khi thực hiện lời gọi phương thức làm tràn stack; nó cũng có thể xuất hiện **bất đồng bộ** liên quan việc chạy phương thức `native` hoặc giới hạn tài nguyên JVM.
- `OutOfMemoryError` có thể xuất hiện **đồng bộ** khi tạo đối tượng, tạo mảng, khởi tạo lớp, boxing, v.v.; nó cũng có thể xuất hiện **bất đồng bộ**.
- Các trường hợp lỗi JVM nội bộ hoặc giới hạn tài nguyên được xử lý dưới quy tắc của `VirtualMachineError`. Loại thông tin như tên lớp hoặc message **không nhất thiết chỉ ra chính xác thời điểm phát hiện lỗi**.

Ví dụ cách suy luận phù hợp:

```text
new int[veryLargeLength]
  └─ có thể thất bại khi cấp phát mảng
     └─ OutOfMemoryError (trường hợp đồng bộ tại phép cấp phát)

Một giới hạn tài nguyên JVM nghiêm trọng được phát hiện ngoài điểm
gắn với phép toán nguồn hiện tại
  └─ JVM có thể ném một lớp con VirtualMachineError bất đồng bộ
```

Sơ đồ trên chỉ minh họa hai cơ chế; **không phải kịch bản để ép JVM hết bộ nhớ**. Mức heap, heap dump, hoạt động GC và chi tiết cảnh báo lỗi thay đổi theo JVM/cấu hình. Việc cố tạo `OutOfMemoryError` hoặc `StackOverflowError` để “kiểm thử async exception” không chứng minh cơ chế bất đồng bộ và có thể làm tiến trình mất ổn định.

## 4. Quy tắc *precise exception* áp dụng cho cả hai loại

JLS §11.3 khẳng định **mọi ngoại lệ, đồng bộ lẫn bất đồng bộ, đều precise**. Tại thời điểm chuyển quyền điều khiển:

- Hiệu ứng của những câu lệnh và biểu thức được hoàn tất **trước điểm ném** phải được phản ánh như đã xảy ra.
- Những câu lệnh, biểu thức hoặc phần của chúng nằm **sau điểm ném** không được tạo ra hiệu ứng quan sát được như thể chúng đã được thực thi.
- JVM có thể thực thi suy đoán (*speculative execution*) để tối ưu hiệu năng nhưng phải ngăn các hiệu ứng suy đoán sai thứ tự xuất hiện trong trạng thái chương trình mà ngôn ngữ bảo đảm.

Quy tắc này nói về tính nhất quán của **đường thực thi và trạng thái quan sát được tại điểm ném**. Nó không hứa rằng toàn bộ tiến trình sẽ không bao giờ ngừng đột ngột, rằng mọi hiệu ứng I/O bên ngoài đều có thể hoàn tác, hay rằng chương trình có thể bắt và phục hồi khỏi mọi `VirtualMachineError`.

Trong `try-catch`, nếu một ngoại lệ được ném, các phần còn lại trên đường bình thường của `try` sẽ bị bỏ qua; quyền điều khiển đi đến bộ xử lý phù hợp gần nhất ở ngữ cảnh gọi hiện tại. Java không tiếp tục chạy ngay tại phép toán đã gây lỗi sau khi `catch` được thực thi.

### Ví dụ Java 17+: quan sát điểm ném và truyền ngoại lệ

Lưu thành `ExceptionBoundaryDemo.java`:

```java
public class ExceptionBoundaryDemo {
    static int divideByZero() {
        System.out.println("inside: before division");
        int denominator = 0;
        int result = 12 / denominator;
        System.out.println("inside: after division");
        return result;
    }

    public static void main(String[] args) {
        try {
            try {
                System.out.println("caller: before call");
                divideByZero();
                System.out.println("caller: after call");
            } finally {
                System.out.println("finally: cleanup");
            }
        } catch (ArithmeticException ex) {
            System.out.println("caught: " + ex.getClass().getSimpleName());
        }
        System.out.println("caller: continue");
    }
}
```

Chạy với JDK 17 trở lên:

```bash
javac -encoding UTF-8 ExceptionBoundaryDemo.java
java ExceptionBoundaryDemo
```

Kết quả xác định:

```text
caller: before call
inside: before division
finally: cleanup
caught: ArithmeticException
caller: continue
```

Điều cần quan sát: phép chia `12 / denominator` với `denominator = 0` làm JVM ném `ArithmeticException` **đồng bộ tại phép chia**. Không có dòng `inside: after division` hay `caller: after call`. Khối `finally` chạy trong quá trình truyền ngoại lệ; `catch` nhận đúng kiểu và cho phép `main` tiếp tục.

Đây là ví dụ về **ngoại lệ đồng bộ và tính chính xác của chuyển quyền điều khiển**. Không thể chỉ sửa câu lệnh trên thành `throw new OutOfMemoryError(...)` rồi gọi đó là minh họa ngoại lệ bất đồng bộ: câu lệnh `throw` tường minh luôn là một điểm ném do chương trình quyết định.

## 5. “Bất đồng bộ” trong thư viện lập trình không đồng nghĩa “ngoại lệ bất đồng bộ của JVM”

Cùng một từ *asynchronous* được dùng cho hai vấn đề khác nhau:

| Tình huống | Có phải ngoại lệ bất đồng bộ của JVM theo JLS §11.1.3? | Giải thích |
| --- | --- | --- |
| `CompletableFuture.supplyAsync(...)` chạy hàm rồi hàm `throw` | **Không**, chỉ vì tác vụ chạy bất đồng bộ. | Câu lệnh `throw` nằm trong thread thực thi tác vụ; API ghi nhận kết quả thất bại của tác vụ. |
| `future.join()` thấy một tác vụ đã thất bại | **Không**, chỉ vì `join` ném `CompletionException`. | Lời gọi `join` báo lỗi đã được lưu; nguyên nhân thường nằm ở `getCause()`. |
| `Future.get()` ném `ExecutionException` vì tác vụ thất bại | **Không**, chỉ vì tác vụ chạy thread khác. | Lỗi được bọc và báo tại thao tác lấy kết quả. |
| Thread khác gọi `interrupt()` vào thread đang `sleep()` | **Không**. | Cơ chế ngắt thread tác động đến trạng thái interrupt và những API có quy tắc phản ứng riêng. |
| `Thread.sleep()` ném `InterruptedException` | **Không** theo nghĩa ngoại lệ bất đồng bộ của JVM. | Ngoại lệ được ném theo hợp đồng của thao tác `sleep` khi có tín hiệu interrupt. |
| JVM phát hiện lỗi tài nguyên nội bộ và ném `VirtualMachineError` tại điểm không gắn với phép toán nguồn cụ thể | **Có thể**. | Đây mới là tình huống thuộc định nghĩa bất đồng bộ theo JLS. |

Điều này vẫn đúng nếu chương trình dùng platform thread, virtual thread, `ExecutorService`, callback hay reactive pipeline: **mô hình tổ chức tác vụ không quyết định phân loại của cơ chế ném ngoại lệ**.

### Ví dụ Java 17+: một tác vụ bất đồng bộ thất bại

Lưu thành `FutureFailureDemo.java`:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class FutureFailureDemo {
    public static void main(String[] args) {
        CompletableFuture<Integer> task = CompletableFuture.supplyAsync(() -> {
            throw new IllegalStateException("worker failed");
        });

        try {
            task.join();
        } catch (CompletionException ex) {
            Throwable original = ex.getCause();
            System.out.println("wrapper=" + ex.getClass().getSimpleName());
            System.out.println("cause=" + original.getClass().getSimpleName());
            System.out.println("message=" + original.getMessage());
        }
    }
}
```

```bash
javac -encoding UTF-8 FutureFailureDemo.java
java FutureFailureDemo
```

Kết quả:

```text
wrapper=CompletionException
cause=IllegalStateException
message=worker failed
```

`CompletableFuture.supplyAsync` đưa tác vụ tới executor mặc định; ví dụ chỉ in từ thread chính sau khi `join()` quan sát hoàn tất của tác vụ nên **thứ tự đầu ra là xác định**. `IllegalStateException` bắt nguồn từ một câu lệnh `throw` thông thường trong tác vụ. `CompletionException` là cách API truyền thông tin thất bại đến nơi gọi `join`, không phải dấu hiệu JVM vừa ném một ngoại lệ bất đồng bộ theo JLS.

Nếu sử dụng `get()` thay `join()`, chương trình phải xử lý hợp đồng checked exceptions của `get()` (đáng chú ý `InterruptedException` và `ExecutionException`). Điều này vẫn là cơ chế tương tác API, không thay đổi định nghĩa lỗi bất đồng bộ của JVM.

## 6. `Thread.interrupt()`: yêu cầu hợp tác, không phải “ném ngoại lệ vào bất cứ đâu”

`interrupt()` gửi tín hiệu gián đoạn cho thread đích. Tác động cụ thể tùy trạng thái thread và API đang thực hiện:

- Nếu thread đang `sleep`, `wait` hoặc `join` theo API chuẩn, một `InterruptedException` có thể được ném từ **chính thao tác chờ**; trạng thái interrupt được xóa khi ngoại lệ được ném trong các trường hợp đó.
- Nếu thread đang làm phép toán tính toán thuần Java không kiểm tra trạng thái, `interrupt()` không tự động ném `InterruptedException` vào một dòng tính toán bất kỳ. Cờ interrupt có thể được quan sát bằng `isInterrupted()`, hoặc thread có thể hợp tác kết thúc.
- Những API I/O và thư viện đồng thời khác có hợp đồng phản ứng riêng: đóng channel, trả sớm, ném loại ngoại lệ khác hoặc không phản ứng ngay. Đọc Javadoc của thao tác cụ thể.

Lưu thành `InterruptBoundaryDemo.java`:

```java
public class InterruptBoundaryDemo {
    public static void main(String[] args) {
        Thread.currentThread().interrupt();

        try {
            Thread.sleep(10);
            System.out.println("sleep completed");
        } catch (InterruptedException ex) {
            System.out.println("caught=" + ex.getClass().getSimpleName());
            System.out.println("flag after catch="
                    + Thread.currentThread().isInterrupted());

            Thread.currentThread().interrupt();
        }

        System.out.println("flag restored="
                + Thread.currentThread().isInterrupted());
    }
}
```

```bash
javac -encoding UTF-8 InterruptBoundaryDemo.java
java InterruptBoundaryDemo
```

Kết quả:

```text
caught=InterruptedException
flag after catch=false
flag restored=true
```

Ví dụ gọi `interrupt()` trên thread hiện tại **trước** khi thực hiện `sleep()` nên không có phụ thuộc vào thứ tự lập lịch giữa hai thread. Theo hợp đồng `Thread.sleep`, khi thread đã bị interrupt, `sleep` ném `InterruptedException` và xóa trạng thái interrupt; `catch` đặt lại cờ để tầng gọi biết có yêu cầu hủy tác vụ. Đây là một cách xử lý phổ biến nếu phương thức không thể khai báo tiếp `throws InterruptedException`, nhưng code thực tế còn phải quyết định có dừng công việc hiện tại hay không.

Không dùng `interrupt()` như cách “thử phát sinh asynchronous JVM exception”. Cơ chế interrupt là cơ chế điều phối/hủy tác vụ có hợp đồng Java API riêng.

## 7. Giới hạn lịch sử của `Thread.stop()`

Các tài liệu JVM cũ từng mô tả `Thread.stop()` có thể gây `ThreadDeath` ở thread khác, một trường hợp ném ngoại lệ bất đồng bộ. **Không áp dụng hành vi lịch sử đó như phương pháp thực hành trong Java SE 21**:

- `Thread.stop()` đã bị deprecated từ lâu vì có thể làm thread thoát trong trạng thái dữ liệu chưa nhất quán, giải phóng monitor ngoài ý muốn.
- Trong JDK 21, API `Thread.stop()` luôn ném `UnsupportedOperationException` **ở thread gọi**, thay vì dừng thread đích bằng `ThreadDeath`. Javadoc ghi phương thức này deprecated for removal.
- Cách hủy công việc nên dựa trên trạng thái hủy do chương trình quản lý, `interrupt()`, hoặc cơ chế hủy ở `Future`/executor với hợp đồng phù hợp.

Vì vậy, **không dùng `Thread.stop()` trong demo, bài kiểm thử hoặc mã triển khai** để tạo ngoại lệ bất đồng bộ. Nếu đọc tài liệu JVM rất cũ, cần xem rõ phiên bản trước khi áp dụng ví dụ của nó. Nên dùng JLS/JVMS của cùng phiên bản Java khi đối chiếu các khẳng định về trường hợp hiếm này.

## 8. Khi ngoại lệ được ném, JVM xử lý ra sao?

Dù phát sinh đồng bộ hay bất đồng bộ, sau khi có một đối tượng ngoại lệ, quy tắc ném và truyền ngoại lệ tuân theo hệ thống xử lý chung:

1. **Tìm `catch` gần nhất có thể xử lý kiểu ngoại lệ.** Một `catch (Exception ex)` sẽ không bắt `VirtualMachineError` vì nhánh `Error` không kế thừa `Exception`.
2. **Hoàn tất đột ngột các câu lệnh và lời gọi đang mở.** Những bước trên đường chạy bình thường sau điểm ném không được tiếp tục tự động.
3. **Thực hiện `finally` phù hợp trong quá trình tháo stack** nếu luồng thực thi còn cho phép; nếu chính `finally` ném ngoại lệ khác, lý do mới có thể thay thế lý do cũ.
4. **Giải phóng monitor** khi một khối `synchronized` hoặc lời gọi synchronized kết thúc đột ngột. Việc giải phóng monitor không có nghĩa các thay đổi dữ liệu trước đó được tự động rollback.
5. **Nếu không có `catch` phù hợp**, ngoại lệ không được bắt dẫn đến việc kết thúc thread theo cơ chế xử lý ngoại lệ chưa bắt của Java.

Có giới hạn thực tế quan trọng: một lỗi tài nguyên trầm trọng có thể khiến JVM không còn đủ tài nguyên để ghi log, cấp phát đối tượng mới hoặc thực hiện đường phục hồi mong muốn. Nếu toàn bộ tiến trình bị hệ điều hành cưỡng bức chấm dứt, đó không phải quá trình ném ngoại lệ Java theo nghĩa thông thường; không có bảo đảm rằng `finally` hay uncaught exception handler sẽ được thực thi.

Tuy `catch (Throwable ex)` có thể bắt một `VirtualMachineError` nếu lỗi được JVM ném và đi tới `catch`, **không nên bắt toàn bộ `Throwable` rồi giả định chương trình đủ an toàn để tiếp tục**. Lựa chọn xử lý phù hợp nhất thường là ghi nhận sự cố bằng cơ chế giám sát và để quá trình phục hồi ở cấp dịch vụ/tiến trình đảm nhiệm, tùy yêu cầu hệ thống.

## 9. Liên hệ tới tối ưu hóa JVM và việc quan sát lỗi

JLS cho phép độ trễ nhỏ và hữu hạn khi phát sinh ngoại lệ bất đồng bộ nhằm tạo không gian cho JIT tối ưu hóa. Nếu bất kỳ phép toán nào cũng phải được coi là điểm có thể bị chen lỗi một cách tức thời, nhiều phép biến đổi mã hợp lệ sẽ bị hạn chế. Với cơ chế phát hiện được phép trì hoãn, trình tạo mã có thể đặt điểm kiểm tra phù hợp mà vẫn tuân thủ ngữ nghĩa ngôn ngữ và giới hạn quan sát.

Tuy nhiên, **không được lấy các khái niệm triển khai như safepoint, polling hoặc trình tự bytecode làm lời cam kết bất biến cho mọi JVM**. Các chi tiết này phụ thuộc phiên bản và cách hiện thực JVM. Điều người lập trình có thể dựa vào là các bảo đảm của đặc tả về lý do ném, giới hạn độ trễ được phép và tính precise khi ngoại lệ thực sự chuyển quyền điều khiển.

Để phân tích log thực tế:

- Tìm **kiểu ngoại lệ, cause chain, suppressed exceptions và stack trace**. Đừng suy ra sync/async chỉ từ tên `OutOfMemoryError`.
- Xác định thao tác nào vừa được thực hiện: cấp phát mảng/đối tượng, gọi phương thức sâu, nạp lớp, chờ I/O, lấy kết quả `Future`, hay chạy callback.
- Với lỗi tài nguyên, kiểm tra metrics, heap/GC logs và cấu hình giới hạn tài nguyên theo hệ thống. Stack trace không luôn đủ để chỉ ra nguyên nhân căn bản.
- Phân biệt một **lỗi trong tác vụ bất đồng bộ** do API ghi nhận với một **lỗi bất đồng bộ của JVM** theo JLS. Cách sửa khác nhau hoàn toàn.

## 10. Câu hỏi tự kiểm tra

**1.** `new int[n]` ném `OutOfMemoryError` khi cấp phát có nhất thiết là JVM asynchronous exception?  
**Không.** Đây có thể là lỗi tài nguyên **đồng bộ** tại thao tác cấp phát được chỉ định. `OutOfMemoryError` cũng có thể xuất hiện bất đồng bộ trong điều kiện khác.

**2.** Một `CompletableFuture` thất bại trên thread khác là JVM asynchronous exception?  
**Không.** “Async” mô tả cách thực thi tác vụ. Ngoại lệ được ném trong tác vụ tuân theo cơ chế thông thường của thread đó; API cung cấp cách quan sát kết quả thất bại từ thread gọi.

**3.** `interrupt()` bảo đảm làm thread đích lập tức ném `InterruptedException` tại bất kỳ câu lệnh nào?  
**Không.** Thread nhận tín hiệu interrupt và mỗi API phản ứng theo hợp đồng riêng. Các thao tác như `sleep` có thể ném ngoại lệ tại điểm chờ cụ thể.

**4.** Mọi `VirtualMachineError` đều bất đồng bộ?  
**Không.** `StackOverflowError` và `OutOfMemoryError` có thể phát sinh đồng bộ hoặc bất đồng bộ.

**5.** JVM asynchronous exception có được ném mà bỏ qua tính nhất quán trạng thái chương trình không?  
**Không.** Cả ngoại lệ đồng bộ và bất đồng bộ đều precise theo JLS §11.3, dù điểm phát sinh của loại bất đồng bộ có độ linh hoạt và độ trễ được phép.

**6.** Bắt `Throwable` có thể bảo đảm chương trình phục hồi mọi lỗi JVM?  
**Không.** Việc bắt được đối tượng ngoại lệ không bảo đảm tài nguyên, trạng thái hoặc chính tiến trình còn khả năng phục hồi. Tiến trình cũng có thể dừng mà không đi qua cơ chế ném ngoại lệ Java.

**7.** Trong Java SE 21, có nên dùng `Thread.stop()` để thử ném `ThreadDeath` bất đồng bộ?  
**Không.** `Thread.stop()` của JDK 21 luôn ném `UnsupportedOperationException` và không phải cơ chế hủy tác vụ được hỗ trợ.

## Tham khảo chuẩn

- [JLS Java SE 21 — §11.1.2 The Causes of Exceptions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-11.html#jls-11.1.2).
- [JLS Java SE 21 — §11.1.3 Asynchronous Exceptions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-11.html#jls-11.1.3).
- [JLS Java SE 21 — §11.3 Run-Time Handling of an Exception, precise exceptions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-11.html#jls-11.3).
- [JVMS Java SE 21 — §2.10 Exceptions](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-2.html#jvms-2.10).
- [JDK 21 — `Thread.stop()`, `Thread.interrupt()` và `Thread.sleep()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html).
- [JDK 21 — `CompletableFuture`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html).

**Ghi nhớ:** Phân loại đồng bộ/bất đồng bộ của JVM dựa vào **mối liên hệ giữa điểm ném và hành động đang thực thi**, không dựa trên tên API có chữ “async”. Ngoại lệ đồng bộ gắn với điểm được đặc tả; ngoại lệ bất đồng bộ của JVM là trường hợp hiếm thuộc `VirtualMachineError` và có thể được phát hiện với độ trễ nhỏ, hữu hạn. Cả hai vẫn phải tuân thủ quy tắc ngoại lệ *precise* khi chuyển quyền điều khiển.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-control-flow-009">Giải phóng monitor khi rời synchronized do ngoại lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Giải phóng monitor khi rời synchronized do ngoại lệ

## 1. Mục tiêu học tập

Trong Java, `synchronized` giúp nhiều luồng phối hợp truy cập trạng thái dùng chung bằng *monitor lock* của một đối tượng. Khi một lệnh trong khối hoặc phương thức `synchronized` ném ngoại lệ, chương trình có thể thoát khỏi phần mã đang giữ monitor theo cách đột ngột. Java bảo đảm **giải phóng monitor tương ứng khi thực sự rời khỏi phạm vi `synchronized`**, kể cả khi việc rời khỏi đó do ngoại lệ.

Sau bài này, bạn sẽ giải thích và kiểm chứng được:

- Monitor nào được lấy khi dùng khối `synchronized (lock)`, phương thức `synchronized` của đối tượng và phương thức `static synchronized`.
- Lý do monitor được nhả khi khối/phương thức hoàn thành đột ngột (*abrupt completion*) do `throw` hoặc do ngoại lệ từ lời gọi bên trong.
- Khác biệt giữa `catch` **bên trong** và **bên ngoài** phạm vi giữ monitor.
- Cách hoạt động của khóa *reentrant* khi cùng luồng đi vào `synchronized` trên cùng đối tượng nhiều lần.
- Vì sao “đã nhả khóa” không đồng nghĩa “trạng thái dữ liệu đã nhất quán”, cũng không bảo đảm luồng khác được chạy ngay.
- Cách đối chiếu hành vi với `Object.wait()`, `Thread.sleep()` và `ReentrantLock`.

Phạm vi bài là **luồng thực thi và giải phóng monitor khi ngoại lệ xuất hiện**. Ví dụ dùng Java 17 trở lên và chỉ tạo ngoại lệ thông thường có kiểm soát.

## 2. Monitor và vùng `synchronized`

Mỗi đối tượng Java có một monitor mà các luồng có thể dùng để đồng bộ hóa. Một luồng muốn đi vào `synchronized (lock)` phải lấy monitor gắn với đúng tham chiếu `lock`. Trong khi luồng đó còn sở hữu monitor, một luồng khác muốn lấy **cùng monitor** phải chờ; mã không đồng bộ hóa với monitor đó không tự động bị khóa.

```java
Object lock = new Object();

synchronized (lock) {
    // Luồng hiện tại đang giữ monitor của đối tượng lock.
    doWork();
}
// Sau khi thực sự rời khối, monitor của lock được nhả.
```

Đây là đoạn minh họa; `doWork()` phải được định nghĩa trong chương trình thực tế.

Các dạng khóa:

| Cấu trúc | Monitor được lấy | Khi nào monitor được nhả? |
| --- | --- | --- |
| `synchronized (lock) { ... }` | Đối tượng mà biểu thức `lock` đánh giá ra | Khi khối `synchronized` đó hoàn thành, bình thường hoặc đột ngột |
| `synchronized void update() { ... }` | `this`, tức đối tượng nhận lời gọi | Khi phương thức `update` hoàn thành |
| `static synchronized void update() { ... }` | Đối tượng `Class` biểu diễn lớp khai báo phương thức | Khi phương thức static hoàn thành |

**Chú ý:** hai đối tượng khóa khác nhau đại diện cho hai monitor khác nhau, dù các phương thức có cùng tên. Một phương thức instance `synchronized` không tự đồng bộ với phương thức `static synchronized` của cùng lớp: một bên dùng `this`, một bên dùng đối tượng `Class`.

### 2.1. Quy tắc cốt lõi khi xuất hiện ngoại lệ

Theo Java Language Specification §14.19, với khối `synchronized (expression) { body }`:

1. Java đánh giá biểu thức lấy đối tượng khóa.
2. Nếu việc đánh giá thất bại bằng ngoại lệ, khối không lấy monitor.
3. Nếu kết quả là `null`, Java ném `NullPointerException` và cũng chưa lấy monitor.
4. Nếu có đối tượng hợp lệ, luồng hiện tại lấy monitor rồi thực thi phần thân.
5. Nếu phần thân kết thúc bình thường, Java nhả monitor trước khi đi tiếp.
6. Nếu phần thân **kết thúc đột ngột vì bất kỳ lý do nào**, bao gồm ném ngoại lệ, Java nhả monitor rồi tiếp tục lan truyền lý do thoát đó.

Ví dụ về điểm phát sinh ngoại lệ:

```java
synchronized (lock) {
    operationA();
    operationB(); // Có thể ném ngoại lệ.
    operationC(); // Bị bỏ qua nếu operationB() ném ngoại lệ.
}
```

Đây là mã minh họa trong một phương thức, với `lock` và các phương thức `operationA/B/C` giả định đã được khai báo. Nếu `operationB()` ném ngoại lệ không bị bắt ở bên trong, phần thân `synchronized` sẽ thoát; Java nhả đúng monitor của `lock` trước khi ngoại lệ tiếp tục đi tới handler ở ngoài.

Quy tắc đó áp dụng cho checked exception, `RuntimeException` và các đối tượng `Error` khi việc hoàn thành đột ngột của khối còn được JVM xử lý. Tuy nhiên, không thể dùng quy tắc này để bảo đảm dọn dẹp hoàn tất trong mọi trường hợp tiến trình/JVM bị kết thúc bất thường.

## 3. Ví dụ hoàn chỉnh: ngoại lệ thoát ra ngoài khối thì monitor được nhả

Tạo file **`MonitorReleaseDemo.java`**:

```java
public class MonitorReleaseDemo {
    private static final Object LOCK = new Object();

    public static void main(String[] args) throws InterruptedException {
        try {
            synchronized (LOCK) {
                System.out.println("inside: " + Thread.holdsLock(LOCK));
                throw new IllegalStateException("boom");
            }
        } catch (IllegalStateException failure) {
            System.out.println("caught: " + failure.getMessage());
            System.out.println("after catch: " + Thread.holdsLock(LOCK));
        }

        Thread worker = new Thread(() -> {
            synchronized (LOCK) {
                System.out.println("worker acquired: "
                        + Thread.holdsLock(LOCK));
            }
        });

        worker.start();
        worker.join();
        System.out.println("done");
    }
}
```

Biên dịch và chạy với JDK 17+:

```bash
javac MonitorReleaseDemo.java
java MonitorReleaseDemo
```

Kết quả `stdout`:

```text
inside: true
caught: boom
after catch: false
worker acquired: true
done
```

**Giải thích từng bước:**

1. Luồng `main` lấy monitor của `LOCK` và vào khối. `Thread.holdsLock(LOCK)` trả về `true` cho **luồng đang gọi phương thức**.
2. `throw new IllegalStateException("boom")` làm khối hoàn thành đột ngột. Java nhả monitor trước khi đi vào `catch` ở ngoài.
3. `catch` vẫn chạy trên luồng `main` nhưng lúc này luồng đó không còn giữ monitor, nên dòng `after catch: false` là chính xác.
4. Sau khi `catch` xong, chương trình khởi tạo luồng `worker`. Luồng này đi vào khối đồng bộ với cùng `LOCK` và in `true`.
5. `worker.join()` buộc `main` chờ đến khi worker kết thúc, vì vậy dòng `done` luôn in sau dòng `worker acquired: true`.

Ví dụ này cho thấy monitor được nhả khi thoát phạm vi `synchronized`. `Thread.holdsLock(object)` chỉ cho biết **luồng hiện tại** có đang giữ monitor của đối tượng đó hay không; giá trị `false` **không** có nghĩa chắc chắn không có luồng nào khác đang giữ khóa.

## 4. `catch` đặt ở đâu quyết định thời điểm nhả monitor

Hai tình huống dưới đây trông tương tự nhưng khác nhau về thứ tự thực thi.

### 4.1. `catch` nằm ngoài `synchronized`

```java
try {
    synchronized (lock) {
        riskyOperation();
    }
} catch (RuntimeException failure) {
    // Nếu không còn vùng synchronized bao ngoài giữ cùng lock,
    // luồng đã nhả monitor của khối vừa thoát.
    recover(failure);
}
```

Đây là đoạn minh họa. Khi `riskyOperation()` ném `RuntimeException`, khối `synchronized` bị thoát và nhả monitor; sau đó handler bên ngoài mới xử lý. Ngoại lệ vẫn được bắt, nhưng **việc bắt ngoại lệ không phải nguyên nhân nhả khóa**. Khóa được nhả vì luồng rời khỏi phạm vi đồng bộ hóa.

### 4.2. `catch` nằm bên trong `synchronized`

```java
synchronized (lock) {
    try {
        riskyOperation();
    } catch (RuntimeException failure) {
        // Luồng VẪN giữ lock vì chưa rời khối synchronized.
        recover(failure);
    }
    // Luồng vẫn giữ lock tại đây.
}
// Monitor được nhả khi luồng ra khỏi khối ngoài cùng này.
```

Đây cũng là đoạn minh họa. Vì lỗi đã được xử lý ngay trong phần thân, khối `synchronized` vẫn đang thực thi. Monitor không được tự động nhả tại thời điểm `catch` bắt được lỗi.

**Hệ quả thực tế:** nếu `recover(failure)` là tác vụ chậm (I/O, gọi mạng hoặc retry nhiều lần), các luồng khác cần cùng monitor có thể bị chặn lâu. Khi có thể, thu thập trạng thái cần thiết trong vùng khóa và chuyển việc xử lý tốn thời gian ra ngoài.

## 5. Ví dụ chạy được: khóa reentrant và `catch` bên trong vùng khóa

Monitor Java có tính **reentrant**: một luồng đã giữ một monitor có thể lấy lại chính monitor đó trong vùng gọi lồng nhau. Mỗi lần vào tương ứng với một lần thoát; chỉ khi không còn lần sở hữu nào của luồng đó thì monitor mới thật sự được giải phóng.

Tạo file **`NestedMonitorDemo.java`**:

```java
public class NestedMonitorDemo {
    private static final Object LOCK = new Object();

    public static void main(String[] args) {
        synchronized (LOCK) {
            try {
                synchronized (LOCK) {
                    System.out.println("inner entered: "
                            + Thread.holdsLock(LOCK));
                    throw new IllegalStateException("inner failed");
                }
            } catch (IllegalStateException failure) {
                System.out.println("catch in outer: "
                        + Thread.holdsLock(LOCK));
            }

            System.out.println("before outer exit: "
                    + Thread.holdsLock(LOCK));
        }

        System.out.println("after outer exit: "
                + Thread.holdsLock(LOCK));
    }
}
```

```bash
javac NestedMonitorDemo.java
java NestedMonitorDemo
```

Kết quả:

```text
inner entered: true
catch in outer: true
before outer exit: true
after outer exit: false
```

Quá trình xảy ra như sau:

- Khối **ngoài** lấy monitor của `LOCK` lần thứ nhất.
- Khối **trong** lấy lại cùng monitor lần thứ hai trên cùng luồng.
- Ngoại lệ khiến khối trong thoát và hoàn trả lần lấy khóa của **khối trong**.
- `catch` thuộc khối ngoài nhận ngoại lệ; lần lấy khóa của **khối ngoài** vẫn còn, nên `Thread.holdsLock(LOCK)` vẫn là `true`.
- Khi chương trình rời khối ngoài, lần lấy khóa còn lại được giải phóng; sau đó `Thread.holdsLock(LOCK)` là `false`.

Quy tắc đúng là **mỗi phạm vi `synchronized` đã thoát nhả phần sở hữu monitor tương ứng**. Không thể suy ra “ném một ngoại lệ sẽ thả tất cả monitor mà luồng đang giữ”. Nếu một luồng nắm nhiều khóa, những phạm vi vẫn còn đang thực thi vẫn giữ khóa của chúng.

## 6. Phương thức `synchronized` cũng tự nhả monitor nếu ném ngoại lệ

Đối với phương thức instance có từ khóa `synchronized`, monitor thuộc về `this`. Việc vào và ra monitor gắn với lời gọi phương thức, kể cả khi phương thức ném ngoại lệ.

Tạo file **`SynchronizedMethodDemo.java`**:

```java
public class SynchronizedMethodDemo {
    private synchronized void fail() {
        System.out.println("inside method: " + Thread.holdsLock(this));
        throw new IllegalArgumentException("invalid input");
    }

    public static void main(String[] args) {
        SynchronizedMethodDemo demo = new SynchronizedMethodDemo();

        try {
            demo.fail();
        } catch (IllegalArgumentException failure) {
            System.out.println("caught: " + failure.getMessage());
            System.out.println("after method: " + Thread.holdsLock(demo));
        }
    }
}
```

```bash
javac SynchronizedMethodDemo.java
java SynchronizedMethodDemo
```

Kết quả:

```text
inside method: true
caught: invalid input
after method: false
```

Khi `fail()` ném lỗi, lời gọi phương thức kết thúc đột ngột và monitor của đối tượng `demo` được nhả trước khi handler bên ngoài nhận lỗi.

Với `static synchronized`, monitor được lấy từ đối tượng `SynchronizedMethodDemo.class` (nếu phương thức được khai báo ở lớp này), **không phải** một instance của lớp.

### 6.1. Khác biệt trong JVM bytecode

Ở mức JVM, khối `synchronized (lock)` thường được biên dịch bằng chỉ thị `monitorenter` và `monitorexit`, bao gồm đường thoát ngoại lệ để nhả monitor.

Một phương thức `synchronized` thông thường lại được đánh dấu bằng cờ `ACC_SYNCHRONIZED` trong thông tin phương thức; JVM điều phối lấy và nhả monitor khi gọi và hoàn thành phương thức. Do đó, **không nên khẳng định mọi phương thức `synchronized` đều có cặp lệnh `monitorenter`/`monitorexit` trong phần thân bytecode**.

Có thể tự kiểm tra cấu trúc bằng:

```bash
javac SynchronizedMethodDemo.java
javap -c -v SynchronizedMethodDemo
```

Tìm phần khai báo `fail()` và cờ `ACC_SYNCHRONIZED`. Đầu ra chi tiết của `javap -v` phụ thuộc trình biên dịch và thông tin classfile; vì vậy bài không giả định một bản dump bytecode cố định.

## 7. Liên hệ với `finally`

Tư duy hữu ích là vùng `synchronized` có cơ chế **tự dọn khóa trên cả đường thành công và đường lỗi**. Điều này gần với ý tưởng sử dụng `finally`, nhưng giải phóng monitor của ngôn ngữ/JVM không phụ thuộc lập trình viên có viết khối `finally` hay không.

```java
synchronized (lock) {
    try {
        operation();
    } finally {
        // Dọn tài nguyên ứng dụng trong vùng khóa, nếu phù hợp.
        // Tại thời điểm này luồng VẪN giữ monitor lock.
        cleanup();
    }
}
// Khi thoát synchronized, monitor được nhả.
```

Đây là mảnh mã minh họa; `operation()`, `cleanup()` và `lock` cần được định nghĩa. Nếu `operation()` ném ngoại lệ, `finally` nội bộ chạy khi vẫn đang giữ monitor. Khi khối đồng bộ kết thúc, monitor được nhả.

**Lưu ý về ngoại lệ mới từ `finally`:** nếu `cleanup()` cũng ném lỗi, ngoại lệ đó có thể thay thế ngoại lệ đang lan truyền. Monitor vẫn được nhả khi rời khối `synchronized`, nhưng thông tin nguyên nhân ban đầu có thể bị che mất. Hãy thiết kế thao tác dọn dẹp sao cho không làm mất lỗi chính.

Việc nhả monitor không phụ thuộc `catch` có bắt ngoại lệ hay không:

| Đường thoát | Monitor của phạm vi đã thoát |
| --- | --- |
| Chạy hết khối | Được nhả |
| `return` từ bên trong khối | Được nhả trước khi hoàn thành việc trả về |
| `break` / `continue` làm thoát phạm vi đó | Được nhả |
| `throw` hoặc lời gọi ném ngoại lệ vượt ra khỏi khối | Được nhả |
| Ngoại lệ bị `catch` ngay trong khối, vẫn tiếp tục ở trong đó | Vẫn được giữ cho đến khi rời khối |
| Ngoại lệ xảy ra khi đánh giá biểu thức khóa hoặc biểu thức khóa bằng `null` | Chưa lấy monitor nên không có monitor mới để nhả |

Không đánh đồng bảo đảm thực thi ngữ nghĩa Java với trường hợp tiến trình bị kết thúc cưỡng bức, VM bị crash hoặc mất điện.

## 8. `Object.wait()`, `Thread.sleep()` và các kiểu khóa khác

Có những trường hợp luồng **tạm thời nhả monitor khi vẫn còn trong khối `synchronized`**, khác với tình huống thoát vì ngoại lệ. Quan trọng nhất là `Object.wait()`.

### 8.1. `wait()` tạm nhả monitor của đối tượng chờ

`Object.wait()` yêu cầu luồng hiện tại đang sở hữu monitor của **đúng đối tượng gọi `wait()`**. Khi luồng gọi `lock.wait()` hợp lệ, nó đi vào trạng thái chờ và tạm thời nhả **mọi lần sở hữu monitor của `lock`**. Sau khi được đánh thức, bị ngắt hoặc kết thúc chờ theo điều kiện phù hợp, luồng phải lấy lại monitor của `lock` trước khi `wait()` trả về bình thường hoặc ném `InterruptedException`.

```java
synchronized (lock) {
    while (!conditionReady()) {
        lock.wait(); // Tạm nhả monitor của lock khi đang chờ.
    }
    consume();
}
```

Đây là **mảnh mã minh họa**, cần nằm trong phương thức xử lý/khai báo `InterruptedException` và định nghĩa `conditionReady()`, `consume()`. Dùng `while` để kiểm tra lại điều kiện vì có thể có đánh thức không báo trước (*spurious wakeup*) hoặc điều kiện đã thay đổi.

Khi `wait()` bị ngắt và ném `InterruptedException`, luồng đã lấy lại monitor trước khi ném. Nếu ngoại lệ đó **thoát tiếp ra ngoài khối `synchronized`**, cơ chế thoát khối sẽ nhả monitor sau đó. Nếu được `catch` **ngay trong khối**, luồng vẫn giữ monitor sau khi `wait()` ném.

`notify()` hoặc `notifyAll()` đánh thức luồng đang chờ nhưng **không nhả monitor ngay tại lệnh gọi**. Luồng chờ chỉ có thể tiếp tục sau khi lấy được monitor trở lại.

### 8.2. `sleep()` không nhả monitor

Nếu một luồng gọi `Thread.sleep(...)` trong vùng `synchronized`, nó **tiếp tục giữ** các monitor đang sở hữu trong lúc ngủ. Vì thế, ngủ hoặc chờ I/O dài bên trong vùng khóa có thể làm các luồng khác chậm đáng kể.

### 8.3. `ReentrantLock` không có quy tắc tự nhả như `synchronized`

`java.util.concurrent.locks.ReentrantLock` là API khóa tường minh. Nếu lấy khóa bằng `lock()` thì bạn phải gọi `unlock()` một cách cân bằng, thông thường trong `finally`. Nếu không, một ngoại lệ ở giữa có thể khiến khóa **bị giữ lại**.

```java
java.util.concurrent.locks.ReentrantLock lock =
        new java.util.concurrent.locks.ReentrantLock();

lock.lock();
try {
    performOperation();
} finally {
    lock.unlock();
}
```

Đây là mảnh minh họa, cần định nghĩa `performOperation()`. Chỉ đặt `unlock()` trong `finally` sau khi đã lấy khóa thành công. Phân biệt khóa tường minh này với monitor nội tại do `synchronized` quản lý.

## 9. Đã nhả monitor chưa có nghĩa dữ liệu đã an toàn

`synchronized` cung cấp loại trừ lẫn nhau giữa những luồng đồng bộ trên **cùng monitor** và tạo quan hệ về thứ tự nhìn thấy bộ nhớ (*happens-before*) khi một luồng unlock monitor, sau đó luồng khác lock cùng monitor. Vì vậy, các ghi trước khi nhả khóa có thể được luồng lấy khóa sau nhìn thấy theo quy tắc Java Memory Model.

Nhưng Java **không hoàn tác các thay đổi trên đối tượng** khi khối `synchronized` kết thúc đột ngột. Nếu một luồng đã cập nhật một nửa trạng thái rồi ném lỗi, luồng kế tiếp có thể đọc trạng thái sai, dù monitor đã được giải phóng đúng cách.

Ví dụ thiết kế chưa an toàn:

```java
synchronized (lock) {
    available -= amount;      // Thay đổi một phần trạng thái.
    saveToExternalSystem();   // Có thể ném ngoại lệ.
    reserved += amount;       // Không chạy nếu lời gọi trên thất bại.
}
```

Đây là **đoạn minh họa**, giả định `lock`, `available`, `reserved`, `amount` và `saveToExternalSystem()` đã được định nghĩa. Nếu ứng dụng yêu cầu `available + reserved` không đổi, lỗi giữa hai phép cập nhật có thể phá vỡ bất biến. Khóa được nhả nhưng trạng thái không tự rollback.

Một hướng sửa cho trường hợp cập nhật **chỉ trong bộ nhớ** là kiểm tra và tính giá trị mới trước các thay đổi trạng thái, đưa hoạt động có khả năng thất bại lên trước phần commit, rồi áp dụng các phép gán dưới cùng monitor:

```java
synchronized (lock) {
    validate(amount);
    int newAvailable = available - amount;
    int newReserved = reserved + amount;
    operationThatMayFail();

    available = newAvailable;
    reserved = newReserved;
}
```

Đây cũng là **mảnh mã minh họa**; các phương thức và biến liên quan cần được định nghĩa. Mẫu này giả định phần commit chỉ gồm các phép gán nội bộ không chủ động ném lỗi, và mọi truy cập trạng thái liên quan đều tuân theo cùng cơ chế khóa. Đối với nhiều tài nguyên, giao dịch cơ sở dữ liệu, mạng hoặc những hiệu ứng bên ngoài khác, cần cơ chế transaction/compensation phù hợp; `synchronized` không biến một chuỗi thao tác thành giao dịch có rollback.

### Ví dụ chạy được: đảm bảo bất biến trước khi nhận lỗi

Tạo file **`InvariantSafetyDemo.java`**:

```java
public class InvariantSafetyDemo {
    private static class Counters {
        private int available = 10;
        private int reserved = 0;

        synchronized void reserve(int amount, boolean fail) {
            if (amount <= 0 || amount > available) {
                throw new IllegalArgumentException("invalid amount");
            }

            int nextAvailable = available - amount;
            int nextReserved = reserved + amount;

            if (fail) {
                throw new IllegalStateException("simulated failure");
            }

            available = nextAvailable;
            reserved = nextReserved;
        }

        synchronized String snapshot() {
            return available + "," + reserved;
        }
    }

    public static void main(String[] args) {
        Counters counters = new Counters();

        try {
            counters.reserve(4, true);
        } catch (IllegalStateException failure) {
            System.out.println("operation failed");
        }

        System.out.println(counters.snapshot());
        counters.reserve(4, false);
        System.out.println(counters.snapshot());
    }
}
```

```bash
javac InvariantSafetyDemo.java
java InvariantSafetyDemo
```

Kết quả:

```text
operation failed
10,0
6,4
```

Lần đầu, ngoại lệ được ném **trước khi cập nhật** hai trường, và phương thức `synchronized` tự nhả monitor. Giá trị đọc sau đó vẫn là `10,0`. Lần thứ hai không lỗi nên hai trường được cập nhật thành `6,4`. Mẫu này minh họa cách bảo toàn bất biến trong bộ nhớ đối với đường lỗi đã dự kiến; nó không phải bảo đảm phục hồi sau mọi `Error` hoặc sự cố bên ngoài.

## 10. Những hệ quả dễ bỏ sót

### 10.1. Giải phóng khóa không ép luồng khác chạy ngay

Khi một luồng nhả monitor, các luồng đang chờ có thể cạnh tranh lấy monitor đó, nhưng **không có bảo đảm thứ tự FIFO** hoặc bảo đảm một luồng cụ thể được chạy ngay. Lịch trình thực tế phụ thuộc hệ thống và các luồng đang hoạt động.

### 10.2. Việc nhả một monitor không mở mọi đường truy cập

Một luồng khác chỉ phải chờ monitor nếu nó đi vào vùng đồng bộ hóa **trên cùng đối tượng khóa**. Nếu một số phương thức đọc/ghi chung dữ liệu mà không dùng đúng khóa hoặc cơ chế đồng bộ hợp lệ khác, chúng vẫn có thể truy cập dữ liệu không nhất quán.

### 10.3. Deadlock có thể vẫn xảy ra

Nếu hai luồng lần lượt giữ monitor A và B, rồi chờ monitor còn lại, chúng có thể deadlock. Tự nhả monitor khi **rời** `synchronized` không giúp một luồng đang bị kẹt vĩnh viễn bên trong vùng khóa thoát ra. Giảm nested locking, cố định thứ tự lấy khóa và dùng công cụ thread dump khi chẩn đoán.

### 10.4. Không được dùng lỗi JVM nghiêm trọng làm kiểm thử đồng bộ thông thường

Quy tắc thoát monitor áp dụng khi việc hoàn thành đột ngột được Java/JVM xử lý. Tuy nhiên, việc cố tạo `OutOfMemoryError` hoặc `StackOverflowError` có thể khiến hệ thống không còn tài nguyên để in log, tạo đối tượng, điều phối công việc hay khởi động phục hồi. Hãy dùng `IllegalStateException` trong ví dụ thử nghiệm bình thường.

## 11. Tóm tắt sai lầm phổ biến

| Nhận định | Đánh giá chính xác |
| --- | --- |
| “Cứ ném ngoại lệ là giải phóng tất cả khóa của luồng.” | **Sai.** Mỗi phạm vi đã thoát nhả phần sở hữu của chính nó; phạm vi bao ngoài còn hiệu lực tiếp tục giữ monitor. |
| “`catch` bên trong `synchronized` làm khóa được nhả.” | **Sai.** Khóa vẫn giữ cho đến khi thực sự rời khối/phương thức đồng bộ. |
| “Phải viết `finally { unlock(); }` cho `synchronized`.” | **Sai.** Monitor nội tại được JVM quản lý. `unlock()` trong `finally` là cách dùng `Lock` tường minh. |
| “Khi exception đã được bắt ở ngoài, monitor của khối vừa thoát vẫn giữ đến hết handler.” | **Sai** nếu không còn vùng đồng bộ hóa bao ngoài giữ cùng khóa. Khối đã nhả monitor trước khi handler ngoài chạy. |
| “`wait()` ném `InterruptedException` khi chưa lấy lại monitor.” | **Sai.** Với `Object.wait()`, luồng lấy lại monitor trước khi phương thức trả về hoặc ném `InterruptedException`. |
| “`Thread.sleep()` giải phóng monitor giống `wait()`.” | **Sai.** `sleep()` giữ nguyên các monitor đang sở hữu. |
| “Monitor nhả nghĩa là thao tác dở dang được rollback.” | **Sai.** Những thay đổi đã thực hiện không tự hoàn tác. |
| “Nhả monitor sẽ đánh thức đúng luồng đang đợi theo FIFO.” | **Sai.** Không có bảo đảm FIFO của monitor nội tại. |

## 12. Tự kiểm tra

1. Một lời gọi trong `synchronized(lock)` ném `IOException` và không được bắt trong khối. Monitor có được nhả không? **Có**, khi quá trình hoàn thành đột ngột rời khối đó.
2. Nếu `catch (IOException e)` đặt *bên trong* cùng khối `synchronized` thì sao? **Vẫn giữ monitor** trong handler và những câu lệnh tiếp theo bên trong khối.
3. Khi cùng luồng đang giữ monitor hai lần do nested `synchronized(lock)`, thoát khối trong bằng ngoại lệ đã làm monitor hoàn toàn rảnh chưa? **Chưa**, khối ngoài vẫn giữ một lần sở hữu.
4. Nếu `synchronized(null)` ném `NullPointerException` thì phải nhả khóa nào? **Không có monitor nào vừa được lấy**, vì biểu thức khóa là `null`.
5. Nếu một phương thức instance `synchronized` ném `RuntimeException` ra bên ngoài, monitor của đối tượng nhận lời gọi được xử lý ra sao? **Được nhả** trong quá trình phương thức kết thúc đột ngột.
6. Nếu `wait()` bị interrupt, tại thời điểm nhận `InterruptedException` ở handler nằm trong khối đồng bộ, luồng có giữ lại monitor không? **Có**; nó đã lấy lại monitor trước khi `wait()` ném lỗi.
7. Vì sao phải kiểm tra bất biến sau một lỗi trong vùng đồng bộ hóa? Vì **giải phóng khóa không thực hiện rollback các phép gán đã diễn ra**.

## 13. Tài liệu chuẩn

- [Java Language Specification 17 — §14.19, The synchronized Statement](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.19): ngữ nghĩa lấy và nhả monitor khi khối kết thúc bình thường hoặc đột ngột.
- [Java Language Specification 17 — §17.1, Synchronization](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.1): reentrant monitor, synchronized methods và unlock tự động khi thoát.
- [Java Language Specification 17 — §17.4.5, Happens-before Order](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4.5): quan hệ unlock/lock và visibility giữa các luồng.
- [Java Virtual Machine Specification 17 — §2.11.10, Synchronization](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.11.10): monitorenter, monitorexit và cờ ACC_SYNCHRONIZED.
- [Java SE 17 API — Thread.holdsLock](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html#holdsLock(java.lang.Object)): kiểm tra quyền sở hữu monitor của luồng hiện tại.
- [Java SE 17 API — Object.wait](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#wait()): nhả tạm monitor và lấy lại trước khi trở về.
- [Java SE 17 API — ReentrantLock](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html): sự khác biệt của khóa tường minh.

**Nguyên tắc trọng tâm:** Khi luồng thực sự rời khỏi phạm vi `synchronized` vì một ngoại lệ, Java nhả monitor của phạm vi đã thoát. Hãy xét vị trí `catch`, những vùng khóa bao ngoài và tính nhất quán của dữ liệu để suy luận đầy đủ về hành vi đa luồng.


</details>

- [Quay lại đầu trang](#back-to-top)
