<a id="back-to-top"></a>

# Lambda và generic

## Menu
- [Checked exception trong lambda theo hợp đồng của kiểu hàm](#lambda-generic-exceptions-001)
- [Method reference và tính tương thích của throws](#lambda-generic-exceptions-002)
- [Runnable, Callable và khác biệt về khai báo ngoại lệ](#lambda-generic-exceptions-003)
- [Vì sao Function, Consumer và Supplier thông thường gây vướng với checked exception](#lambda-generic-exceptions-004)
- [Bắt tại lambda, chuyển đổi ngoại lệ hoặc chọn interface riêng](#lambda-generic-exceptions-005)
- [Phương thức generic có tham số kiểu mở rộng Throwable](#lambda-generic-exceptions-006)
- [Giới hạn của catch đối với type parameter](#lambda-generic-exceptions-007)
- [Suy luận kiểu với throws và rủi ro của sneaky throw](#lambda-generic-exceptions-008)
- [Giữ hợp đồng rõ ràng khi kết hợp Stream và thao tác có thể thất bại](#lambda-generic-exceptions-009)

## <a id="lambda-generic-exceptions-001">Checked exception trong lambda theo hợp đồng của kiểu hàm</a>

<details>
<summary>Xem chi tiết</summary>

# Checked exception trong lambda theo hợp đồng của kiểu hàm trong Java

Trong Java, một biểu thức lambda không có danh sách `throws` của riêng nó. Việc lambda có được phép để **checked exception** thoát ra hay không phụ thuộc vào **functional interface** mà biểu thức lambda đang được gán hoặc chuyển vào. Chính xác hơn, trình biên dịch kiểm tra những exception mà phần thân lambda có thể ném so với **mệnh đề `throws` của function type/functional descriptor** thuộc kiểu đích (*target type*).

Điều này giải thích vì sao **cùng một thao tác có thể biên dịch khi viết dưới dạng `Callable` nhưng không biên dịch khi viết dưới dạng `Supplier`**, và vì sao phương thức được tham chiếu bằng `::` có `throws IOException` đôi khi không thể dùng trực tiếp trong `Stream.map`.

Ý chính cần nhớ:

1. `Runnable.run()`, `Supplier.get()`, `Function.apply()` và `Consumer.accept()` trong thư viện Java chuẩn **không khai báo checked exception**.
2. `Callable.call() throws Exception` cho phép phần thân lambda ném những checked exception thuộc `Exception`.
3. Bạn có thể định nghĩa một functional interface riêng với `throws IOException` hoặc tham số generic `E extends Exception` nếu caller cần nhận và xử lý lỗi checked.
4. Nếu kiểu hàm đích không cho phép một checked exception, lambda phải **xử lý nó trong thân** hoặc **chuyển sang một loại lỗi được hợp đồng cho phép**, chứ không thể tự thêm `throws` sau dấu `->`.
5. Gán một lambda sang kiểu khác không làm đổi bản chất của nguồn lỗi; **kiểu đích quyết định hợp đồng kiểm tra tại thời điểm biên dịch**.

## 1. Phân biệt checked exception và unchecked exception

Java dùng `Throwable` làm gốc của hệ thống ngoại lệ. Đối với kiểm tra tại compile time:

| Nhóm | Ví dụ | Có phải bắt hoặc khai báo theo quy tắc checked exception? |
| --- | --- | --- |
| **Checked exception** | `IOException`, `SQLException`, `ParseException`, custom `Exception` không thuộc `RuntimeException` | Có |
| **Unchecked exception** | `RuntimeException` và các lớp con như `IllegalArgumentException`, `NumberFormatException` | Không |
| **Unchecked `Error`** | `Error` và các lớp con | Không; thường biểu diễn lỗi nghiêm trọng, không dùng cho validation nghiệp vụ |

Ví dụ phương thức nguồn:

```java
static String load(String key) throws java.io.IOException {
    if ("missing".equals(key)) {
        throw new java.io.IOException("source unavailable");
    }
    return "value:" + key;
}
```

Đây là một **phương thức minh họa**. Khi gọi `load(key)`, mã sử dụng phải thỏa hợp đồng checked `IOException`: hoặc đặt lời gọi trong `try-catch` phù hợp, hoặc nằm trong phương thức/lambda được phép truyền checked exception đó ra ngoài.

Với lambda, điểm khác là **lambda không tự công bố `throws IOException` bằng cú pháp của chính nó**. Nó phải dựa vào functional interface của ngữ cảnh.

## 2. Lambda cần một functional interface làm kiểu đích

Một **functional interface** là interface có đúng **một phương thức abstract theo quy tắc xác định function type**, dù vẫn có thể có phương thức `default`, `static` hoặc phương thức tương ứng với các phương thức `public` của `Object`.

Ví dụ:

```java
@FunctionalInterface
interface CheckedReader {
    String read(String key) throws java.io.IOException;
}
```

Lambda phù hợp:

```java
CheckedReader reader = key -> {
    if (key.isBlank()) {
        throw new java.io.IOException("blank key");
    }
    return "value:" + key;
};
```

Đây là **các khai báo minh họa**, đặt trong lớp/phạm vi Java hợp lệ. Tham số `key` và giá trị trả về của lambda được ràng buộc bởi `CheckedReader.read(String)`. Vì phương thức abstract khai báo `throws IOException`, phần thân có thể ném checked `IOException`.

Nếu đổi kiểu đích:

```java
java.util.function.Function<String, String> reader = key -> {
    if (key.isBlank()) {
        throw new java.io.IOException("blank key"); // Không biên dịch.
    }
    return "value:" + key;
};
```

Mã trên **cố ý không biên dịch**. `Function.apply(T)` không có `throws IOException`. Lambda không thể đưa ra loại checked exception mới ngoài hợp đồng của kiểu hàm đích.

### Lambda không có cú pháp `throws` riêng

Không có cú pháp Java hợp lệ như sau:

```java
// Cố ý SAI CÚ PHÁP.
Function<String, String> reader =
        (String key) throws IOException -> load(key);
```

Lời khai báo `throws` thuộc về **phương thức** hoặc **phương thức abstract của functional interface**, không nằm giữa danh sách tham số và dấu `->` của lambda.

Bạn có thể viết `throws` trên phương thức chứa lambda nếu phương thức đó thực sự phát sinh checked exception trong đường thực thi **của chính nó**, nhưng **không** vì thế mà lambda gán vào `Function` được quyền ném `IOException`. Hai hợp đồng kiểm tra khác nhau.

## 3. “Function type” hay “functional descriptor” chứa những gì?

Khi compiler kiểm tra lambda, nó xét kiểu hàm mục tiêu với các thông tin thiết yếu:

- **Số lượng và kiểu tham số**.
- **Kiểu trả về**.
- **Các kiểu trong `throws`** được function type cho phép.
- Các ràng buộc do generic type, method type parameter hoặc kiểu đích suy ra.

Ví dụ:

```java
@FunctionalInterface
interface Parser {
    int parse(String text) throws java.text.ParseException;
}
```

Biểu thức:

```java
Parser parser = text -> {
    if (text.isBlank()) {
        throw new java.text.ParseException(
                "empty input", 0);
    }
    return text.length();
};
```

hợp lệ vì `Parser.parse(String)` khai báo `throws ParseException`. Nhưng cùng một phần thân, nếu gán vào `ToIntFunction<String>`, sẽ không hợp lệ nếu `ParseException` thoát ra mà không được xử lý, vì `ToIntFunction.applyAsInt` không khai báo checked exception.

**Lưu ý:** Java không bắt người viết lambda phải **thực sự** ném một checked exception được khai báo ở kiểu đích. Lambda không ném lỗi checked nào vẫn có thể triển khai interface có `throws IOException`.

## 4. So sánh các functional interface quen thuộc

| Functional interface | Phương thức hàm | Checked `throws` của phương thức hàm |
| --- | --- | --- |
| `Runnable` | `void run()` | Không |
| `Supplier<T>` | `T get()` | Không |
| `Consumer<T>` | `void accept(T t)` | Không |
| `Function<T,R>` | `R apply(T t)` | Không |
| `Predicate<T>` | `boolean test(T t)` | Không |
| `BiFunction<T,U,R>` | `R apply(T t, U u)` | Không |
| `Callable<V>` | `V call() throws Exception` | `Exception` |
| Custom `CheckedReader` | `String read(String) throws IOException` | `IOException` |
| Custom `ThrowingFunction<T,R,E>` | `R apply(T) throws E` | Tham số kiểu `E` theo ràng buộc |

Các tên kiểu trong bảng là của `java.lang.Runnable`, `java.util.concurrent.Callable`, `java.util.function` hoặc interface custom. Không nên ghi nhớ đơn giản “lambda không ném được checked exception”: lambda **ném được** khi hợp đồng functional interface cho phép.

### `Runnable` và `Callable` khác nhau ở checked exception

Một lambda dùng `Runnable`:

```java
Runnable action = () -> {
    throw new java.io.IOException("I/O failed"); // Không biên dịch.
};
```

Phương thức `run()` không khai báo checked `IOException`.

Cùng ý tưởng với `Callable`:

```java
java.util.concurrent.Callable<String> action = () -> {
    throw new java.io.IOException("I/O failed");
};
```

Đoạn `Callable` **hợp lệ**: `call() throws Exception` cho phép `IOException`.

Tuy nhiên, khi caller dùng `action.call()` thông qua **kiểu `Callable<String>`**, compiler thấy `throws Exception`, nên caller phải bắt hoặc khai báo loại **`Exception`** tương ứng, không tự suy ra rằng lambda này chỉ ném `IOException`.

## 5. Ví dụ Java 17+ hoàn chỉnh: cùng thao tác, ba hợp đồng xử lý

Chương trình không cần đọc tệp thật; một hàm giả lập ném checked `IOException` để đầu ra hoàn toàn xác định.

Lưu toàn bộ nội dung vào **`CheckedLambdaContractDemo.java`**:

```java
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.Callable;
import java.util.function.Function;

public class CheckedLambdaContractDemo {

    @FunctionalInterface
    interface CheckedReader {
        String read(String key) throws IOException;
    }

    static String load(String key) throws IOException {
        if ("missing".equals(key)) {
            throw new IOException("source unavailable");
        }
        return "value:" + key;
    }

    static void inspect(String key) {
        CheckedReader checked =
                CheckedLambdaContractDemo::load;

        try {
            System.out.println(
                    "checked=" + checked.read(key));
        } catch (IOException ex) {
            System.out.println(
                    "checked error=" + ex.getMessage());
        }

        Function<String, String> converted = value -> {
            try {
                return load(value);
            } catch (IOException cause) {
                throw new UncheckedIOException(
                        "cannot resolve " + value, cause);
            }
        };

        try {
            System.out.println(
                    "function=" + converted.apply(key));
        } catch (UncheckedIOException ex) {
            System.out.println(
                    "function error=" + ex.getMessage());
            System.out.println(
                    "function cause="
                    + ex.getCause().getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        inspect("ok");
        inspect("missing");

        Callable<String> task = () -> load("ok");
        try {
            System.out.println("callable=" + task.call());
        } catch (Exception ex) {
            System.out.println(
                    "callable error=" + ex.getClass().getSimpleName());
        }

        Runnable action = () -> {
            try {
                load("missing");
            } catch (IOException ex) {
                System.out.println(
                        "runnable handled=" + ex.getMessage());
            }
        };
        action.run();
    }
}
```

Biên dịch và chạy bằng **JDK 17 trở lên**:

```bash
javac -encoding UTF-8 CheckedLambdaContractDemo.java
java CheckedLambdaContractDemo
```

**Stdout chính xác:**

```text
checked=value:ok
function=value:ok
checked error=source unavailable
function error=cannot resolve missing
function cause=IOException
callable=value:ok
runnable handled=source unavailable
```

### Đọc ba kiểu hợp đồng

**`CheckedReader`:** phương thức abstract `read(String) throws IOException` cho phép lambda hoặc method reference truyền checked `IOException` ra ngoài. Người gọi qua `CheckedReader` bắt `IOException` trực tiếp.

**`Function<String,String>`:** `apply` không cho phép checked `IOException`. Lambda phải bắt lỗi ngay trong thân; ví dụ chuyển lỗi I/O sang `UncheckedIOException` và **giữ `cause`**. Người gọi `Function` không bắt buộc phải có `catch`, nhưng vẫn có thể gặp unchecked exception ở runtime.

**`Callable<String>`:** `call() throws Exception` cho phép method body gọi `load` mà không cần bắt `IOException` bên trong. Tuy nhiên caller qua `Callable` phải xử lý `Exception` theo khai báo của `call`.

**`Runnable`:** `run()` không có checked `throws`, nên lambda phải tự bắt `IOException`. Ở ví dụ, hành động chỉ in một dòng báo lỗi, không ném lại, giúp chứng minh cú pháp. Trong hệ thống thật, quyết định phục hồi/báo lỗi không nên chỉ là `println` rồi lặng lẽ coi tác vụ thành công.

**Quan trọng:** `UncheckedIOException` phù hợp khi chuyển một lỗi I/O checked sang unchecked, bởi nó yêu cầu một `IOException` làm cause. Với checked exception không phải I/O, cần chọn wrapper hợp đồng khác; đừng cố ép mọi loại checked exception thành `UncheckedIOException`.

## 6. Kiểu đích quyết định checked exception ở thời điểm gán lambda

Một lambda biểu diễn việc gọi hàm:

```java
() -> load("missing")
```

**chưa có kiểu độc lập giống một biểu thức `String` thông thường**. Lambda được kiểm tra trong ngữ cảnh kiểu đích khi gán biến, truyền đối số hoặc ép kiểu sang functional interface.

Ví dụ cùng biểu thức:

```java
java.util.concurrent.Callable<String> c =
        () -> load("missing"); // Hợp lệ.

java.util.function.Supplier<String> s =
        () -> load("missing"); // Không biên dịch.
```

Đây là đoạn minh họa, giả định `load` là phương thức ném `IOException`. `Callable.call` cho phép `Exception`; `Supplier.get` không cho phép checked `IOException`.

### Có thể thêm `throws IOException` cho phương thức chứa lambda để sửa không?

Không, nếu lỗi nằm trong phần thân lambda được gán vào `Supplier`:

```java
// Cố ý KHÔNG BIÊN DỊCH.
static java.util.function.Supplier<String> build()
        throws java.io.IOException {
    return () -> load("missing");
}
```

`build()` chỉ **tạo và trả một lambda**, chưa thực thi lời gọi `load`. Hợp đồng `throws IOException` của `build` không làm thay đổi hợp đồng `Supplier.get()` khi lambda được gọi sau này.

Muốn giữ checked exception để caller xử lý, hãy đổi **kiểu đích** sang functional interface có `throws IOException`. Muốn giữ `Supplier`, hãy xử lý/chuyển lỗi ở trong lambda.

## 7. Method reference `::` tuân theo cùng quy tắc

Không chỉ lambda dạng `x -> method(x)`; **method reference** cũng được kiểm tra với functional interface đích.

Ví dụ:

```java
static String loadFile(String key) throws java.io.IOException {
    if (key.isEmpty()) {
        throw new java.io.IOException("empty key");
    }
    return key;
}
```

Với interface tùy chỉnh cho phép `IOException`:

```java
@FunctionalInterface
interface CheckedLoader {
    String load(String key) throws java.io.IOException;
}

CheckedLoader good = Example::loadFile;
```

Đây là những khai báo minh họa, `Example` là lớp chứa phương thức `loadFile`.

Nhưng:

```java
java.util.function.Function<String, String> bad =
        Example::loadFile; // Không biên dịch vì IOException checked.
```

Thông điệp compiler có thể nhắc `incompatible thrown types` hoặc `unreported exception` tùy ngữ cảnh. Bản chất giống hệt lambda: **function descriptor của kiểu đích không cho phép loại checked exception mà lời gọi được tham chiếu có thể ném**.

### Không thể “sửa” method reference bằng ép kiểu tùy tiện

Ép sang `Function<String,String>` không xóa `IOException` khỏi phương thức nguồn. Cần chọn kiểu hàm phù hợp hoặc viết một lambda adapter có `try-catch` đúng nghĩa.

Đổi sang `Callable` cũng không giải quyết mọi trường hợp: `Callable` không có tham số và trả về một giá trị; method reference với chữ ký nhận đối số chỉ phù hợp nếu danh sách tham số theo kiểu đích có thể ánh xạ đúng.

## 8. Định nghĩa functional interface generic với `E extends Exception`

Trong thư viện tái sử dụng, một interface generic có thể mô tả thao tác biến đổi vừa có kiểu đầu vào/đầu ra vừa có **kiểu checked exception**:

```java
@FunctionalInterface
interface ThrowingFunction<T, R, E extends Exception> {
    R apply(T value) throws E;
}
```

Ba tham số kiểu có ý nghĩa:

- **`T`:** kiểu đầu vào.
- **`R`:** kiểu đầu ra.
- **`E`:** kiểu ngoại lệ được phép ném, bị giới hạn trong các subtype của `Exception`.

Ví dụ một phương thức parse dữ liệu có checked exception tùy chỉnh:

```java
final class ParseFailure extends Exception {
    ParseFailure(String message, Throwable cause) {
        super(message, cause);
    }
}

ThrowingFunction<String, Integer, ParseFailure> parser =
        value -> {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException cause) {
                throw new ParseFailure("invalid integer", cause);
            }
        };
```

Đây là đoạn minh họa, cần được đặt trong lớp/phạm vi phù hợp. `ParseFailure` là checked vì extends `Exception` nhưng không extends `RuntimeException`. `throws E` trên phương thức hàm cho phép lambda ném `ParseFailure` khi `E` được xác định là kiểu đó.

### `E` không làm Java tự động bắt ngoại lệ

Caller vẫn phải xử lý:

```java
try {
    int value = parser.apply("12");
    System.out.println(value);
} catch (ParseFailure ex) {
    System.out.println(ex.getMessage());
}
```

Vì kiểu tĩnh của `parser` mang `E=ParseFailure`, trình biên dịch có thể biết loại checked exception cụ thể. Đây là một lợi ích của interface có generic exception parameter so với việc chỉ ghi `throws Exception` cho tất cả thao tác.

### Lưu ý khi một thao tác có nhiều checked exception

Một tham số `E` biểu diễn **một kiểu ngoại lệ tĩnh**. Nếu mã có thể ném hai checked exception độc lập, ví dụ `IOException` và `SQLException`, không phải lúc nào bạn cũng thể hiện chính xác cả hai chỉ bằng một `E` mà không nới lên một supertype chung như `Exception`.

Những lựa chọn thường là:

- Định nghĩa phương thức hàm với `throws IOException, SQLException` nếu đó là chính xác hợp đồng của module.
- Chuyển lỗi sang một custom checked exception theo tầng trừu tượng và giữ `cause`.
- Sử dụng thiết kế kết quả có kiểu nếu lỗi là một phần bình thường của nghiệp vụ.
- Chấp nhận `E=Exception` nếu thư viện thực sự cần độ tổng quát ấy, đồng thời hiểu caller sẽ phải xử lý loại rộng hơn.

Đừng ép một thiết kế generics làm mất ý nghĩa của loại lỗi chỉ để viết API ngắn.

## 9. Adapter từ `ThrowingFunction` sang `Function`: đổi hợp đồng, không tự xóa lỗi

`Stream.map` nhận `Function<? super T, ? extends R>`, tức `Function.apply` **không khai báo checked exception**. Nếu bạn có `ThrowingFunction`, không thể truyền trực tiếp nó vào `map` chỉ vì cùng số lượng tham số.

Có thể viết adapter:

```java
static <T, R, E extends Exception>
java.util.function.Function<T, R> unchecked(
        ThrowingFunction<T, R, E> action) {

    return value -> {
        try {
            return action.apply(value);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception cause) {
            throw new IllegalArgumentException(
                    "conversion rejected", cause);
        }
    };
}
```

Đây là **phương thức minh họa** trong lớp có định nghĩa `ThrowingFunction`. Ý nghĩa:

1. Adapter nhận một hàm **có thể ném checked E**.
2. Lambda bên trong implement `Function.apply`, vốn không có checked `throws`.
3. Nếu nguồn ném một `RuntimeException`, adapter để nguyên lỗi unchecked ấy.
4. Nếu nguồn ném một checked `Exception`, adapter bọc thành `IllegalArgumentException` có `cause`.
5. Caller nhận một `Function` thông thường và có thể truyền vào `Stream.map`.

**Cảnh báo thiết kế:** `IllegalArgumentException` trong ví dụ chỉ là lựa chọn minh họa cho thao tác parse/convert dữ liệu đầu vào. Với lỗi I/O, có thể dùng `UncheckedIOException`; với thao tác bất đồng bộ hoặc lỗi nghiệp vụ, chọn loại wrapper khác đúng hợp đồng.

Adapter **không làm lỗi biến mất**. Nó đổi một đường lỗi checked thành một đường lỗi unchecked; ứng dụng vẫn cần quyết định xử lý ở ranh giới phù hợp.

### Vì sao bắt `RuntimeException` trước `Exception`?

Một `RuntimeException` cũng là lớp con của `Exception`. Nếu chỉ viết `catch (Exception ex)` rồi bọc tất cả, adapter có thể bọc cả lỗi lập trình như `NullPointerException`, làm việc điều tra sai hướng và thay đổi loại runtime mà caller từng mong đợi.

Tách:

```java
catch (RuntimeException ex) {
    throw ex;
} catch (Exception cause) {
    throw new IllegalArgumentException(
            "conversion rejected", cause);
}
```

giữ nguyên lỗi unchecked và chỉ bọc những exception còn lại thuộc nhóm checked. `Error` không bị bắt bởi các nhánh trên; tránh `catch (Throwable)` chung chỉ để “chuyển hết mọi thứ thành thành công”.

## 10. Ví dụ Java 17+ hoàn chỉnh: custom generic function và `Stream.map`

Lưu tệp **`ThrowingStreamAdapterDemo.java`**:

```java
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

public class ThrowingStreamAdapterDemo {

    @FunctionalInterface
    interface ThrowingFunction<T, R, E extends Exception> {
        R apply(T value) throws E;
    }

    static final class ParseFailure extends Exception {
        private static final long serialVersionUID = 1L;

        ParseFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static Integer strictPositive(String text)
            throws ParseFailure {

        final int number;
        try {
            number = Integer.parseInt(text);
        } catch (NumberFormatException cause) {
            throw new ParseFailure("not integer", cause);
        }

        if (number <= 0) {
            throw new ParseFailure("not positive", null);
        }
        return number;
    }

    static <T, R, E extends Exception> Function<T, R> unchecked(
            ThrowingFunction<T, R, E> action) {

        return value -> {
            try {
                return action.apply(value);
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception cause) {
                throw new IllegalArgumentException(
                        "conversion rejected", cause);
            }
        };
    }

    static void runCase(String first, String second) {
        ThrowingFunction<String, Integer, ParseFailure> parser =
                ThrowingStreamAdapterDemo::strictPositive;

        Function<String, Integer> normal = unchecked(parser);

        try {
            List<Integer> values =
                    Stream.of(first, second)
                            .map(normal)
                            .toList();

            System.out.println("values=" + values);

        } catch (IllegalArgumentException ex) {
            System.out.println(
                    "failed=" + ex.getMessage());
            System.out.println(
                    "cause=" + ex.getCause().getClass().getSimpleName());
            System.out.println(
                    "detail=" + ex.getCause().getMessage());
        }
    }

    public static void main(String[] args) {
        runCase("2", "3");
        runCase("2", "abc");
        runCase("0", "4");
    }
}
```

Biên dịch/chạy với JDK 17 trở lên:

```bash
javac -encoding UTF-8 ThrowingStreamAdapterDemo.java
java ThrowingStreamAdapterDemo
```

**Stdout chính xác:**

```text
values=[2, 3]
failed=conversion rejected
cause=ParseFailure
detail=not integer
failed=conversion rejected
cause=ParseFailure
detail=not positive
```

### Phân tích đường thành công và thất bại

**`runCase("2","3")`:** lambda dưới dạng `ThrowingFunction` gọi `strictPositive` hai lần, mỗi lần trả `Integer` hợp lệ. Adapter `Function` không ném exception; `Stream.toList()` hoàn tất và danh sách in `[2, 3]`.

**`runCase("2","abc")`:** `Integer.parseInt("abc")` ném unchecked `NumberFormatException`. `strictPositive` bắt lỗi và chuyển thành checked `ParseFailure("not integer", cause)`. Adapter bắt `Exception`, bọc thành unchecked `IllegalArgumentException("conversion rejected", cause)`. Handler ngoài in nguyên nhân trực tiếp là `ParseFailure` với message `not integer`. Nếu điều tra sâu, `ParseFailure.getCause()` vẫn là `NumberFormatException`.

**`runCase("0","4")`:** `strictPositive("0")` parse được số `0` nhưng quy tắc yêu cầu dương, nên ném `ParseFailure("not positive", null)`. Stream dừng do ngoại lệ trước khi xử lý phần còn lại theo đường thành công, adapter bọc và caller nhận cùng kiểu wrapper như trên, nhưng có message nguyên nhân khác.

### Vì sao không thể chỉ viết `Stream.of(...).map(parser)`?

`parser` có kiểu `ThrowingFunction<String,Integer,ParseFailure>`. `Stream.map` cần một `Function`. Hai interface là hai **kiểu Java khác nhau**, và kiểu đích `Function.apply` cũng không cho phép checked `ParseFailure`. Việc chúng có cùng hình dạng tham số/giá trị trả về không biến chúng thành cùng kiểu hoặc cùng hợp đồng lỗi.

Adapter là một **chuyển đổi có chủ đích** giữa hai hợp đồng. Nếu pipeline lớn hoặc error handling phức tạp, có thể tốt hơn khi tách công đoạn parse checked ra ngoài stream hoặc dùng kiểu kết quả có cấu trúc.

## 11. Vì sao không được bắt checked exception “ở ngoài stream” nếu lambda không hợp lệ?

Một lỗi thường gặp là viết:

```java
// Cố ý KHÔNG BIÊN DỊCH.
try {
    java.util.stream.Stream.of("a", "b")
            .map(value -> readFile(value))
            .toList();
} catch (java.io.IOException ex) {
    System.out.println("read failed");
}
```

Ở đây `readFile(String)` được giả định khai báo `throws IOException`. `Stream.map` vẫn đòi `Function.apply` không ném checked `IOException`. **Handler ngoài không biến lambda không phù hợp thành hợp lệ.** Compiler cần kiểm tra riêng body lambda theo functional descriptor trước.

Các cách đúng:

1. **Bắt `IOException` bên trong lambda**, trả fallback hợp lệ hoặc chuyển sang unchecked wrapper.
2. **Dùng custom throwing function và adapter** để chọn rõ chính sách.
3. **Đưa thao tác I/O ra ngoài stream** trong một phương thức vòng lặp có `throws IOException`, nếu muốn giữ lỗi checked nguyên dạng cho caller.
4. **Dùng kiểu kết quả** như một record/sealed hierarchy để biểu diễn success/failure như dữ liệu, nếu pipeline cần xử lý nhiều thất bại mà không ngắt giữa chừng.

Đừng dùng `catch (IOException)` bên ngoài với kỳ vọng compiler sẽ nhìn xuyên qua mọi lambda và tự mở rộng hợp đồng `Function.apply`.

### Một handler ngoài có thể bắt loại lỗi nào sau khi adapter?

Nếu adapter chuyển `IOException` sang `UncheckedIOException`, caller có thể bắt `UncheckedIOException`. Nếu adapter chuyển `ParseFailure` sang `IllegalArgumentException`, caller có thể bắt `IllegalArgumentException`.

Handler ngoài **không tự động bắt `ParseFailure`** chỉ vì nó nằm trong `getCause()` của wrapper. Java chọn `catch` theo **đối tượng ngoại lệ đang được ném**, không tự đi xuyên qua cause.

## 12. Xử lý ngay trong lambda: fallback có ý nghĩa thay vì nuốt lỗi

Một cách làm hợp lệ về cú pháp:

```java
java.util.function.Function<String, Integer> parse = value -> {
    try {
        return Integer.parseInt(value);
    } catch (NumberFormatException ex) {
        return 0;
    }
};
```

`NumberFormatException` là unchecked, và lambda trả `0` khi gặp lỗi. Về mặt Java, không vấn đề checked exception; nhưng về **ngữ nghĩa**, chuỗi `"0"` hợp lệ và chuỗi `"abc"` đều có thể trả `0`. Nếu caller cần phân biệt hai trường hợp, fallback này là **mất thông tin**.

Với checked `IOException` cũng vậy:

```java
java.util.function.Supplier<String> supplier = () -> {
    try {
        return readRequiredConfig();
    } catch (java.io.IOException ex) {
        return "";
    }
};
```

Đây là đoạn minh họa, `readRequiredConfig()` giả định có `throws IOException`. Mã có thể biên dịch, nhưng nếu config bắt buộc và chuỗi rỗng vẫn là dữ liệu hợp lệ, caller không biết lỗi đã xảy ra.

**Chỉ chọn fallback khi hợp đồng thực sự cho phép phục hồi.** Trong ví dụ khác, nếu dữ liệu có thể thiếu mà caller được phép dùng default được kiểm chứng, fallback có thể là chính sách hợp lý.

### Dạng kết quả có cấu trúc

Có thể định nghĩa:

```java
sealed interface ReadResult
        permits ReadResult.Success, ReadResult.Failure {

    record Success(String value) implements ReadResult {}

    record Failure(String code, String message)
            implements ReadResult {}
}
```

Đây là **mã minh họa Java 17**, có thể đặt trong file/class phù hợp. Một `Function<String,ReadResult>` có thể bắt `IOException` nội bộ rồi trả `Failure`, giúp pipeline phân biệt dữ liệu với thất bại mà không ném checked exception ra ngoài `Function.apply`.

Thiết kế này có ích khi cần tổng hợp **nhiều kết quả lỗi** thay vì để stream dừng ở lỗi đầu tiên. Nhưng nó cũng làm kiểu dữ liệu phong phú hơn; không ép mọi thao tác đơn giản phải dùng sealed result.

## 13. Đọc kiểu tĩnh của biến hàm khi gọi lại

Đừng nhầm **nội dung lambda** với **chữ ký phương thức mà caller dùng**.

Giả sử:

```java
@FunctionalInterface
interface ReaderAction {
    String read() throws java.io.IOException;
}

ReaderAction action = () -> "cached";
```

Đây là đoạn minh họa. Phần thân lambda không hề ném checked exception. Nhưng:

```java
String value = action.read(); // Cần bắt/khai báo IOException.
```

Trình biên dịch dùng kiểu tĩnh `ReaderAction`, mà `read()` công bố `throws IOException`. Không có cơ chế compile-time suy ngược từ lời khai báo lambda cụ thể để loại bỏ nghĩa vụ checked exception khi caller dùng interface.

Ngược lại, nếu dùng:

```java
java.util.function.Supplier<String> supplier = () -> "cached";
String value = supplier.get(); // Không đòi IOException.
```

thì phương thức hàm không khai báo checked exception, nên caller không bị bắt/khai báo `IOException` do lời gọi `get()` này.

Điều này tương tự quy tắc override trong Java: compiler áp dụng **hợp đồng của kiểu tĩnh được dùng tại vị trí gọi**, không đoán mọi nhánh implementation thực tế.

## 14. Lambda generic không tự “xóa” hợp đồng exception

Khi dùng:

```java
ThrowingFunction<String, Integer, java.io.IOException> f =
        value -> {
            throw new java.io.IOException("read failed");
        };
```

lambda hợp lệ vì `E` là `IOException`. Caller `f.apply("x")` phải xử lý hoặc khai báo `IOException`.

Nếu chuyển thành:

```java
ThrowingFunction<String, Integer, Exception> f =
        value -> {
            throw new java.io.IOException("read failed");
        };
```

lambda cũng hợp lệ, nhưng caller qua biến kiểu này thấy `throws Exception` rộng hơn. **Kiểu exception generic được chọn có ảnh hưởng đến nghĩa vụ tại điểm gọi.**

### Erasure và generic exception parameter

Tại runtime, thông tin generic của kiểu `E` bị ảnh hưởng bởi *type erasure*. Compiler vẫn dùng `E` để kiểm tra nguồn và caller ở compile time, nhưng không nên viết logic runtime kiểu:

```java
// Cố ý KHÔNG HỢP LỆ: không thể catch một type parameter E.
catch (E ex) {
    // ...
}
```

Java không cho dùng biến kiểu generic `E` làm loại trong `catch` như vậy. Đó là lý do nhiều adapter tổng quát bắt `Exception`, phân biệt `RuntimeException` trước, rồi xử lý checked exception còn lại theo hợp đồng adapter.

### Inference không thay thế thiết kế API

Trong phương thức generic dùng `ThrowingFunction<T,R,E>`, trình biên dịch có thể suy ra `E` từ lambda hoặc method reference trong những hoàn cảnh cụ thể. Tuy nhiên, không nên giả định inference sẽ luôn chọn chính xác các loại checked exception trong một biểu thức phức tạp hoặc cho phép tự động truyền một throwing function sang `Function`.

Ở nơi cần minh bạch, khai báo biến có kiểu rõ:

```java
ThrowingFunction<String, Integer, ParseFailure> parser =
        MyParser::parsePositive;
```

Đây là minh họa, với `MyParser.parsePositive` được định nghĩa tương thích. Kiểu rõ giúp caller và người review thấy ngay loại lỗi checked được phép.

## 15. `throws Exception` của `Callable` không có nghĩa mọi tác vụ đều chạy bất đồng bộ

`Callable<V>` là functional interface từ `java.util.concurrent`. Nó cho phép `call() throws Exception`, nhưng **bản thân interface không yêu cầu một thread mới**.

Nếu viết:

```java
java.util.concurrent.Callable<String> task =
        () -> readRequiredConfig();

String result = task.call();
```

đây là minh họa, `readRequiredConfig` là phương thức checked `IOException` giả định. Gọi `task.call()` trực tiếp chạy đồng bộ trên thread hiện tại; caller phải xử lý/khai báo `Exception` theo kiểu `Callable`.

Nếu submit cho `ExecutorService`:

```java
java.util.concurrent.Future<String> future =
        executor.submit(task);

try {
    String result = future.get();
} catch (java.lang.InterruptedException ex) {
    Thread.currentThread().interrupt();
} catch (java.util.concurrent.ExecutionException ex) {
    Throwable original = ex.getCause();
    // Xử lý/log theo chính sách thực tế.
}
```

Đây là **đoạn minh họa**, biến `executor` và `task` phải được khai báo hợp lệ ở phạm vi tương ứng.

- `submit` đưa tác vụ tới executor.
- `Future.get()` có thể ném checked `InterruptedException` hoặc `ExecutionException`; nó không tự ném trực tiếp mọi `IOException` mà body `Callable` đã ném.
- Khi tác vụ thất bại, `ExecutionException.getCause()` thường trỏ tới ngoại lệ gốc của tác vụ.
- Việc interrupt cần được xử lý theo chính sách; ví dụ khôi phục flag khi không tiếp tục ném `InterruptedException`.

Do đó, có **hai hợp đồng** cần phân biệt: `Callable.call() throws Exception` ở tác vụ, và `Future.get()` khi caller lấy kết quả từ executor.

## 16. `CompletableFuture` và các API async cũng có ranh giới riêng

`CompletableFuture.supplyAsync` nhận `Supplier<U>`, mà `Supplier.get()` **không cho checked exception thoát ra**. Vì thế:

```java
// Cố ý KHÔNG BIÊN DỊCH nếu readRequiredConfig() throws IOException.
java.util.concurrent.CompletableFuture<String> result =
        java.util.concurrent.CompletableFuture.supplyAsync(
                () -> readRequiredConfig());
```

Cần bắt hoặc chuyển lỗi trong lambda:

```java
java.util.concurrent.CompletableFuture<String> result =
        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                return readRequiredConfig();
            } catch (java.io.IOException cause) {
                throw new java.io.UncheckedIOException(
                        "config read failed", cause);
            }
        });
```

Đây là khung minh họa. Khi lấy kết quả:

- `result.join()` có thể báo thất bại bằng một `CompletionException` unchecked, với cause liên quan.
- `result.get()` sử dụng hợp đồng `Future` với checked `InterruptedException`/`ExecutionException`.
- Cần phân biệt lỗi wrapper của hạ tầng async và exception nguyên nhân thực tế ở `getCause()`.

Chỉ việc viết `throws IOException` trên phương thức tạo `CompletableFuture` không khiến `Supplier.get()` cho phép checked `IOException`.

### Không suy diễn rằng executor “bắt giúp mọi lỗi”

Framework có thể giữ exception của tác vụ để truy vấn qua `Future`. Việc caller không thấy exception ném ngay tại lệnh `submit` không có nghĩa công việc thành công. Hãy quan sát trạng thái và kết quả bất đồng bộ tại đúng ranh giới.

## 17. Checked exception và `throws` khi functional interface kế thừa nhiều nguồn

Một functional interface có thể có các phương thức abstract tương đương nhau qua quan hệ kế thừa interface. Java xác định function type theo các quy tắc về chữ ký, return type và tập checked exception được phép.

Ví dụ đơn giản:

```java
interface ActionA {
    void run() throws java.io.IOException;
}

interface ActionB {
    void run() throws java.sql.SQLException;
}

@FunctionalInterface
interface CombinedAction extends ActionA, ActionB {
}
```

Đây là **khung minh họa hợp lệ** khi hai phương thức cùng signature có thể được hợp nhất làm một functional method. Nhưng lambda triển khai `CombinedAction` **không được tùy tiện ném `IOException` hoặc `SQLException`** chỉ vì mỗi interface cha cho phép một loại. Để thỏa **cả hai hợp đồng**, tập checked exception mà function type cho phép có thể bị thu hẹp đáng kể; với hai nhánh độc lập như ví dụ, lambda không thể để một trong hai checked exception ấy thoát ra theo cách trái với hợp đồng còn lại.

Một lambda không ném checked exception nào là trường hợp đơn giản:

```java
CombinedAction action = () ->
        System.out.println("combined action");
```

Một lưu ý tinh tế là **kiểu hàm được suy ra** có thể chặt hơn một khai báo cụ thể trong interface cha. Khi bài toán có nhiều interface kết hợp hoặc generics phức tạp, hãy kiểm tra đặc tả kiểu đích bằng compiler thay vì suy đoán rằng danh sách `throws` được cộng dồn.

## 18. Adapter và chính sách giữ nguyên nguyên nhân

Khi chuyển checked exception sang unchecked để thỏa `Function`, lựa chọn wrapper cần được cân nhắc:

| Ngoại lệ checked ban đầu | Một cách chuyển đổi có thể phù hợp | Điều kiện cần chú ý |
| --- | --- | --- |
| `IOException` | `UncheckedIOException(message, cause)` | `cause` phải là `IOException` |
| Checked exception nghiệp vụ | Custom `RuntimeException(message, cause)` | Bảo toàn mã và ý nghĩa nghiệp vụ |
| `SQLException` | Một wrapper phù hợp tầng repository/service | Không tự đưa SQL chi tiết ra client |
| `ParseException` | Một exception validation phù hợp | Không biến mọi lỗi parse thành lỗi hệ thống |
| Đã có một `RuntimeException` | Thường ném lại nếu không cần đổi ngữ cảnh | Không bọc lại vô nghĩa |

**Không dùng `throw new RuntimeException(ex.getMessage())`** nếu bạn muốn giữ loại và stack trace nguyên nhân. Constructor này chỉ nhận chuỗi, không giữ `cause`. Chọn `new RuntimeException("context", ex)` hoặc một subtype chuyên biệt khi cần chuyển lỗi.

Ví dụ:

```java
try {
    return checkedOperation();
} catch (SomeCheckedException cause) {
    throw new DomainOperationException(
            "Cannot complete operation", cause);
}
```

Đây là đoạn minh họa với các kiểu/hàm do ứng dụng định nghĩa. Điều quan trọng là **truyền đối tượng `cause`**, không chỉ message, và chỉ chuyển loại exception tại ranh giới thực sự có ý nghĩa.

### Không dùng “sneaky throw” để né compiler một cách mù quáng

Một số thư viện dùng generic trick hoặc helper đặc biệt để ném checked exception qua API không khai báo `throws`. Dù runtime có thể cho phép ngoại lệ được ném, kỹ thuật này **làm mờ hợp đồng ở compile time**, khiến caller của `Function` không được hướng dẫn bắt loại checked exception thực tế.

Nếu mục đích giáo dục hoặc yêu cầu API là tôn trọng checked exception, hãy ưu tiên **throwing functional interface có khai báo `throws`**, `try-catch` rõ ràng, hoặc **wrapper có cause**. Tránh dùng kỹ thuật né kiểm tra để thay thế thiết kế.

## 19. Trường hợp `finally` và resource trong lambda

Một lambda có thể tự chứa `try-with-resources`:

```java
java.util.concurrent.Callable<String> loader = () -> {
    try (var reader =
                 java.nio.file.Files.newBufferedReader(path)) {
        return reader.readLine();
    }
};
```

Đây là đoạn minh họa với biến `path` hợp lệ kiểu `Path`. Vì `Callable.call() throws Exception`, lambda được phép để checked `IOException` từ mở, đọc hoặc đóng tài nguyên thoát ra. Resource vẫn được đóng trước khi `call` thực sự trả, kể cả khi body `return` sớm hoặc ném lỗi.

Nếu thay `Callable<String>` bằng `Supplier<String>` cho cùng body thì không hợp lệ trừ khi đã xử lý hoặc chuyển checked `IOException`. **`try-with-resources` không tự xóa checked exception** phát sinh trong lúc mở, đọc hoặc đóng.

Đồng thời:

- Nếu thân `try` ném checked exception E1, còn `close()` ném E2, cơ chế `try-with-resources` thường giữ E1 làm lỗi chính và gắn E2 dưới dạng suppressed.
- Nếu thân `try` định trả giá trị nhưng `close()` ném checked `IOException`, đường trả có thể bị thay bằng exception mà hợp đồng functional interface đích phải cho phép hoặc handler phải xử lý.
- Không nên trả một stream/lazy iterator phụ thuộc resource đã đóng từ lambda và giả định caller có thể sử dụng bình thường.

## 20. Lỗi biên dịch và lỗi thiết kế thường gặp

| Hiểu lầm hoặc lỗi | Vì sao sai | Cách xử lý |
| --- | --- | --- |
| “Lambda không bao giờ ném được checked exception” | Tùy `throws` của functional descriptor | Dùng interface có checked `throws` |
| “Lambda tự khai báo `throws IOException` sau `->`” | Cú pháp Java không hỗ trợ | Đặt `throws` ở functional method |
| Gán lambda gọi `Files.readString` trực tiếp vào `Supplier` | `Supplier.get` không khai báo `IOException` | Catch/wrap hoặc dùng custom interface |
| Ghi `throws IOException` trên phương thức tạo `Supplier` | Không sửa được hợp đồng `Supplier.get` | Xử lý lỗi trong lambda hoặc đổi target type |
| Bao `Stream.map` trong `catch(IOException)` rồi mong lambda được phép ném | Vi phạm checked exception ngay trong `Function.apply` | Đổi cách biểu diễn hoặc adapter |
| Dùng `Callable` nhưng caller chỉ bắt `IOException` khi gọi `call()` | `Callable.call` khai báo `throws Exception` | Bắt/khai báo `Exception` hoặc dùng interface hẹp |
| Nghĩ method reference bỏ qua checked `throws` | Cũng phải thỏa target functional descriptor | Dùng kiểu đích/adapter phù hợp |
| Tạo adapter bắt mọi `Exception` rồi luôn bọc | Có thể bọc cả `RuntimeException` không cần thiết | Giữ unchecked nguyên trạng, bọc lỗi checked có chủ đích |
| Bọc bằng message nhưng không truyền `cause` | Mất liên kết lỗi gốc | Truyền `Throwable cause` |
| Dùng `UncheckedIOException` cho mọi checked exception | Constructor/cơ chế dành cho `IOException` | Dùng wrapper phù hợp loại gốc |
| Dùng `catch (Throwable)` trong lambda và nuốt lỗi | Có thể giấu `Error` nghiêm trọng | Bắt những lỗi ứng dụng có thể xử lý |
| Chỉ in stack trace rồi trả giá trị giả | Caller tưởng thao tác thành công | Dùng recovery thật hoặc kết quả thất bại rõ |
| Đồng nhất việc tạo `Callable` với thực thi async | `Callable` chỉ là interface | Phân biệt `call()` trực tiếp và `submit()` |
| Nghĩ `Future.get()` ném trực tiếp mọi exception trong `call()` | `get()` thường dùng `ExecutionException` | Kiểm tra `getCause()` |
| Nghĩ stream có `map` sẽ xử lý mọi phần tử trước khi ném | Exception có thể dừng pipeline | Cân nhắc result type để tổng hợp lỗi |

## 21. Checklist thiết kế lambda có checked exception

Trước khi viết một lambda gọi phương thức `throws`:

1. **Xác định kiểu đích:** là `Runnable`, `Supplier`, `Function`, `Callable` hay interface custom?
2. **Đọc function descriptor:** kiểu tham số, kiểu trả về và danh sách checked `throws` thực sự là gì?
3. **Xác định nguồn checked exception:** lời gọi nào có thể ném `IOException`, `SQLException` hoặc custom checked exception?
4. **Đối chiếu với `throws` của descriptor:** các loại checked thoát khỏi body đã được cho phép chưa?
5. **Nếu không được cho phép**, có chính sách xử lý trong lambda không? Không chỉ nuốt lỗi hoặc trả fallback sai.
6. **Nếu caller cần xử lý checked trực tiếp**, có nên định nghĩa custom functional interface rõ loại `throws` không?
7. **Nếu API cố định dùng `Function`/`Supplier`**, adapter nào diễn đạt ý định chuyển checked sang unchecked tốt nhất?
8. **Nếu wrapper được tạo**, đã giữ `cause` và không vô tình bọc cả các lỗi unchecked cần giữ nguyên chưa?
9. **Nếu dùng generics**, `E` có đủ cụ thể chưa, hay đang vô tình mở thành `Exception` quá rộng?
10. **Nếu dùng method reference**, phương thức nguồn và kiểu đích có tương thích cả tham số, return type và checked `throws` không?
11. **Nếu chạy stream**, có hiểu rằng lỗi sẽ ngắt pipeline, không tự tổng hợp tất cả lỗi không?
12. **Nếu chạy async**, caller quan sát lỗi qua `Future.get()`/`join()` thế nào?
13. **Nếu lambda quản lý resource**, có xét checked exception từ cả khởi tạo, thân `try` và `close()` không?
14. **Đã biên dịch các đường thành công/thất bại chưa?** Kiểm tra cả lỗi compile-time do descriptor và cách lan truyền cause runtime.

## 22. Bài tập tự kiểm tra

**Bài A — Runnable hay Callable?**

```java
Runnable a = () -> {
    throw new java.io.IOException("failed");
};
```

Có biên dịch không?

**Không.** `Runnable.run()` không khai báo checked `IOException`. Với `Callable<Void>`, lambda có thể ném `IOException` nếu các ràng buộc tham số/return phù hợp.

**Bài B — cùng lambda dưới kiểu khác:**

```java
java.util.concurrent.Callable<String> c =
        () -> load("item");

java.util.function.Supplier<String> s =
        () -> load("item");
```

Nếu `load(String) throws IOException`, câu nào hợp lệ?

**`Callable` hợp lệ**, còn `Supplier` không hợp lệ nếu `IOException` chưa được xử lý trong lambda.

**Bài C — thêm throws ở phương thức chứa lambda:**

Có thể chỉ thêm `throws IOException` cho method `createSupplier()` để lambda `() -> load()` được gán vào `Supplier` không?

**Không.** Kiểm tra phần thân lambda vẫn dựa vào `Supplier.get()`, không phải `throws` của `createSupplier()`.

**Bài D — method reference:**

`ReadAction` có `read() throws IOException`. Một method reference tới phương thức `readFile() throws IOException` có thể triển khai interface ấy không?

**Có**, nếu kiểu tham số và kết quả cũng tương thích. Nhưng nó không mặc nhiên triển khai `Supplier` không có checked `throws`.

**Bài E — generic exception:**

`ThrowingFunction<String,Integer,ParseFailure>` có giúp caller bắt riêng `ParseFailure` không?

**Có.** Kiểu `E=ParseFailure` được dùng ở compile time cho phương thức hàm `apply`.

**Bài F — adapter có làm checked exception biến mất ở runtime không?**

**Không.** Adapter có thể bọc lỗi checked thành unchecked wrapper; caller có thể xem lỗi gốc qua `getCause()` nếu adapter giữ nguyên nhân.

**Bài G — Future:**

`Callable.call()` ném `IOException` khi chạy trong executor. `future.get()` có nhất thiết ném trực tiếp `IOException` không?

**Không.** Khi tác vụ thất bại, `get()` thường ném `ExecutionException` và `getCause()` trỏ tới lỗi gốc.

**Bài H — catch bên ngoài stream:**

`try { stream.map(v -> readFile(v)).toList(); } catch (IOException ex) { ... }` có khiến body `Function.apply` được phép ném `IOException` không?

**Không.** Lambda phải phù hợp descriptor của `Function` trước; handler ngoài không sửa được lỗi compile-time đó.

**Bài I — fallback:**

`Supplier` bắt `IOException` rồi trả chuỗi rỗng. Có luôn là xử lý đúng không?

**Không.** Chỉ đúng nếu chuỗi rỗng là fallback hợp đồng cho phép. Nếu không, nó che thất bại và có thể làm caller hiểu sai.

**Bài J — unchecked wrapper:**

Tại sao nên dùng `new UncheckedIOException("context", cause)` hơn `new RuntimeException(cause.getMessage())` cho lỗi `IOException`?

Vì `UncheckedIOException` thể hiện đúng loại nguồn I/O và **giữ đối tượng cause**, còn chỉ copy message làm mất thông tin stack, cause chain và suppressed của lỗi ban đầu.

## Kết luận

**Checked exception trong lambda được quyết định bởi hợp đồng của kiểu hàm đích, không bởi một danh sách `throws` viết trên lambda.** Với `Runnable`, `Supplier`, `Function` và các functional interface chuẩn không có checked `throws`, phần thân phải xử lý hoặc chuyển đổi lỗi checked. `Callable.call() throws Exception` và các functional interface tự định nghĩa có `throws IOException` hay `throws E` có thể cho phép chuyển lỗi checked lên caller.

Hãy phân biệt **kiểu đích tại compile time**, **ngoại lệ thực tế tại runtime**, và **cách hệ thống gọi lambda** (trực tiếp, stream, executor hay `CompletableFuture`). Khi cần adapter, giữ `cause` và diễn đạt chính sách lỗi rõ ràng. Khi cần caller xử lý checked exception theo kiểu hẹp, dùng một **functional interface có `throws` đúng hợp đồng** thay vì giấu lỗi, phân tích message hoặc tìm cách né kiểm tra của compiler.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lambda-generic-exceptions-002">Method reference và tính tương thích của throws</a>

<details>
<summary>Xem chi tiết</summary>

# Method reference và tính tương thích của `throws` trong Java

**Method reference** (`::`) là cách viết ngắn để dùng một phương thức hoặc constructor như phần triển khai của một **functional interface**. Khi phương thức được tham chiếu có thể ném **checked exception**, Java không chỉ kiểm tra tham số và kiểu trả về: trình biên dịch còn so sánh **những checked exception mà lời gọi có thể ném** với **mệnh đề `throws` của phương thức trừu tượng chức năng (SAM – single abstract method)** trong kiểu đích.

Một quy tắc cốt lõi:

> **Method reference có checked exception chỉ tương thích với functional interface nếu các checked exception liên quan được cho phép bởi `throws` của SAM**, xét các quy tắc kiểu và suy luận generic của Java. Không có chuyển đổi “tự bắt lỗi”, “tự thêm `throws`” hoặc “tự bọc thành RuntimeException” chỉ vì dùng ký hiệu `::`.

Ví dụ, `Callable<T>.call() throws Exception` có thể nhận một method reference tới phương thức khai báo `throws IOException`. Ngược lại, `Supplier<T>.get()` và `Runnable.run()` không khai báo checked exception nào, nên không thể nhận trực tiếp một method reference mà phương thức được tham chiếu khai báo `throws IOException`.

Bài này giải thích cách Java kiểm tra method reference với `throws` trên Java 17+, phân biệt static/bound/unbound/constructor reference, thử nghiệm bằng chương trình có đầu ra chính xác, cách sửa lỗi biên dịch, generic functional interface có kiểu exception, adapter sang API chuẩn, overload, và những ranh giới thiết kế cần chú ý.

## 1. Nhớ lại các thành phần của một method reference

Một method reference không tự tạo ra một lời gọi ngay khi được viết. Nó là một biểu thức được **kiểu hóa theo ngữ cảnh đích** (*target typing*) và tạo một đối tượng hoạt động theo functional interface được chọn.

Ví dụ:

```java
Runnable task = service::run;
```

Đây là **mã minh họa**; cần biến `service` có method `run()` thích hợp. Ở đây:

- `service::run` là **method reference expression**.
- `Runnable` là **target type**.
- `Runnable.run()` là **SAM**, có chữ ký `void run()`, không khai báo checked exception.
- Phương thức instance `service.run()` là **phương thức được tham chiếu**; nó cần tương thích với SAM.
- Khi `task.run()` được gọi, hành vi được tham chiếu mới thực thi.

Các thành phần phải ăn khớp gồm **số lượng/kiểu tham số, khả năng chuyển đổi kiểu trả về và tập checked exception được phép thoát ra**. Phương thức có `throws` không phải là một overload riêng biệt chỉ bởi mệnh đề `throws`.

**Phân biệt ba thời điểm:**

1. **Biên dịch**: Java kiểm tra method reference có tương thích với target SAM hay không, gồm checked exceptions.
2. **Đánh giá reference**: tạo/capture reference thích hợp; thông thường không gọi phương thức đích.
3. **Gọi SAM**: phương thức đích thực thi và exception thực sự có thể được ném, bắt hoặc lan truyền.

Đối với bound reference như `object::instanceMethod`, việc đánh giá receiver có quy tắc riêng; receiver bằng `null` có thể gây `NullPointerException` ngay khi tạo reference. Điều này khác với checked-exception checking ở compile time.

## 2. Bốn hình thức method reference thường gặp

| Dạng | Ý nghĩa | Ví dụ minh họa |
| --- | --- | --- |
| `ClassName::staticMethod` | Tham chiếu static method | `Parser::parse` |
| `instance::instanceMethod` | Tham chiếu method trên receiver đã có | `reader::read` |
| `ClassName::instanceMethod` | Tham chiếu unbound method; receiver trở thành tham số của SAM | `String::length` |
| `ClassName::new` | Tham chiếu constructor | `InputStreamReader::new` |

Bốn hình thức đều chịu kiểm tra kiểu đích. Nếu lời gọi thực tế theo biểu thức tham chiếu có thể ném checked exception mà SAM không cho phép, **biên dịch thất bại**, dù method reference trông ngắn hoặc chưa được gọi trong chương trình.

Ví dụ, `String::length` khớp `Function<String, Integer>` qua boxing từ `int` thành `Integer`, vì `String.length()` không khai báo checked exception. Ngược lại, một factory có `throws IOException` không thể tự động được dùng như `Function<String, Resource>` nếu `Function.apply` không cho phép checked exception.

## 3. `throws` của SAM đóng vai trò gì?

Functional interface phải có đúng một phương thức trừu tượng theo luật của Java; phương thức ấy cung cấp chữ ký chức năng mà method reference sẽ thực hiện.

Hai interface khác nhau:

```java
@FunctionalInterface
interface PlainAction {
    void run();
}

@FunctionalInterface
interface CheckedAction {
    void run() throws java.io.IOException;
}
```

Đây là **hai interface hoàn chỉnh**, không có `main`. Nếu một phương thức có khai báo `throws IOException`, nó có thể phù hợp với `CheckedAction`, nhưng không phù hợp với `PlainAction` chỉ bởi vì tên method của interface đều là `run()`.

Tập checked exception mà method reference có thể ném phải nằm trong những loại được SAM khai báo cho phép. Quy tắc dùng kiểu **gán được / subtype**:

- Phương thức được tham chiếu `throws FileNotFoundException`; SAM `throws IOException` → **hợp lệ**, vì `FileNotFoundException extends IOException`.
- Phương thức được tham chiếu `throws IOException`; SAM `throws Exception` → **hợp lệ**, vì `IOException extends Exception`.
- Phương thức được tham chiếu `throws Exception`; SAM chỉ `throws IOException` → **không hợp lệ**, vì `Exception` quá rộng.
- Phương thức được tham chiếu `throws IOException, SQLException`; SAM chỉ `throws IOException` → **không hợp lệ**, vì `SQLException` không thuộc `IOException`.
- Phương thức được tham chiếu chỉ ném `RuntimeException` hoặc `Error` → không bị bắt buộc khai báo trong SAM.

**Đừng nghĩ rằng “SAM có một kiểu exception giống tên nào đó” là đủ**; mọi checked exception liên quan từ phương thức đích đều phải được phép theo quy tắc tương thích.

## 4. Ví dụ Java 17 đầy đủ: cùng method, SAM phù hợp và không phù hợp

Lưu **`ThrowsCompatibilityDemo.java`**:

```java
import java.io.IOException;

public class ThrowsCompatibilityDemo {
    @FunctionalInterface
    interface CheckedAction {
        void run() throws IOException;
    }

    static void load() throws IOException {
        System.out.println("Đang tải");
        throw new IOException("Không đọc được cấu hình");
    }

    public static void main(String[] args) {
        CheckedAction action = ThrowsCompatibilityDemo::load;
        System.out.println("Reference đã tạo");

        try {
            action.run();
        } catch (IOException e) {
            System.out.println("Đã bắt: " + e.getMessage());
        }

        System.out.println("Kết thúc");
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 ThrowsCompatibilityDemo.java
java ThrowsCompatibilityDemo
```

**Stdout chính xác**:

```text
Reference đã tạo
Đang tải
Đã bắt: Không đọc được cấu hình
Kết thúc
```

Giải thích:

1. `CheckedAction.run() throws IOException` là hợp đồng của target.
2. `ThrowsCompatibilityDemo.load() throws IOException` được phép bởi hợp đồng đó.
3. Gán `ThrowsCompatibilityDemo::load` chỉ tạo một biểu thức thực thi theo SAM; không gọi `load`, nên dòng `Đang tải` chưa xuất hiện.
4. Khi gọi `action.run()`, method `load` chạy và ném `IOException`.
5. Caller phải catch hoặc khai báo checked `IOException` tại điểm gọi `action.run()`.

Nếu thay dòng gán bằng:

```java
Runnable action = ThrowsCompatibilityDemo::load;
```

đây là **phản ví dụ cố ý không biên dịch**. `Runnable.run()` không khai báo `throws IOException`. Method reference bị từ chối vì checked exception của `load` không được SAM cho phép. Java **không tạo một Runnable rồi âm thầm nuốt lỗi**.

## 5. `Runnable`, `Supplier`, `Function` và `Consumer` không cho phép checked exception

Các functional interface quen thuộc trong JDK thường được sử dụng ở nơi API muốn callback đơn giản:

| Interface | SAM | Mệnh đề checked `throws` |
| --- | --- | --- |
| `Runnable` | `void run()` | Không |
| `Supplier<T>` | `T get()` | Không |
| `Function<T,R>` | `R apply(T t)` | Không |
| `Consumer<T>` | `void accept(T t)` | Không |
| `Predicate<T>` | `boolean test(T t)` | Không |
| `BiFunction<T,U,R>` | `R apply(T t, U u)` | Không |
| `Callable<V>` | `V call() throws Exception` | **Có, Exception** |

Một method reference tới phương thức chỉ ném unchecked exception có thể hợp lệ với các interface không `throws` nếu **những yếu tố kiểu khác đều khớp**.

Ví dụ sau là **mã minh họa**:

```java
java.util.function.Supplier<Integer> supplier = String::length;
```

Dòng này **không hợp lệ**, nhưng **không phải vì throws**: `String::length` là **unbound instance method** cần một receiver kiểu `String`, trong khi `Supplier.get()` không nhận tham số. Cách đúng:

```java
java.util.function.Function<String, Integer> length = String::length;
```

Đây là ví dụ nhấn mạnh: một method reference phải thỏa **cả chữ ký tham số/trả về lẫn checked exceptions**. Khi chẩn đoán lỗi compile, không quy tất cả vào `throws`.

## 6. `Callable<V>` cho phép checked exception rộng `Exception`

`java.util.concurrent.Callable<V>` có SAM:

```java
V call() throws Exception;
```

Do đó một phương thức `String read() throws IOException` có thể được tham chiếu qua `Callable<String>`, vì `IOException` là con của `Exception`.

Lưu **`CallableReferenceDemo.java`**:

```java
import java.io.IOException;
import java.util.concurrent.Callable;

public class CallableReferenceDemo {
    static String readData() throws IOException {
        System.out.println("Đọc dữ liệu");
        return "OK";
    }

    public static void main(String[] args) {
        Callable<String> callable = CallableReferenceDemo::readData;

        try {
            System.out.println("Kết quả: " + callable.call());
        } catch (Exception e) {
            System.out.println("Lỗi: " + e.getMessage());
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 CallableReferenceDemo.java
java CallableReferenceDemo
```

**Stdout chính xác**:

```text
Đọc dữ liệu
Kết quả: OK
```

Ở điểm gán, `Callable<String>` chấp nhận phương thức khai báo `throws IOException`. Nhưng tại điểm **gọi qua biến `Callable<String>`**, chữ ký `call() throws Exception` là chữ ký tĩnh caller nhìn thấy. Do đó ví dụ catch `Exception` thay vì chỉ `IOException`.

**Sự khác biệt quan trọng:**

- **Kiểm tra gán method reference:** `throws IOException` nằm trong `throws Exception` → hợp lệ.
- **Kiểm tra lời gọi SAM:** `call()` được khai báo `throws Exception` → caller phải đáp ứng hợp đồng rộng đó.

Không có nghĩa method `readData` bỗng có khả năng ném mọi `Exception`. Chỉ có nghĩa khi nhìn qua target type `Callable`, compiler kiểm tra lời gọi bằng chữ ký của target interface.

## 7. Thu hẹp hợp đồng ở functional interface riêng

Nếu muốn giữ nghĩa vụ checked exception chính xác hơn `Callable`, có thể định nghĩa SAM riêng:

```java
@FunctionalInterface
interface IoSupplier<T> {
    T get() throws java.io.IOException;
}
```

Ví dụ độc lập **`IoSupplierDemo.java`**:

```java
import java.io.IOException;

public class IoSupplierDemo {
    @FunctionalInterface
    interface IoSupplier<T> {
        T get() throws IOException;
    }

    static String obtain() throws IOException {
        throw new IOException("Nguồn chưa sẵn sàng");
    }

    static String use(IoSupplier<String> supplier)
            throws IOException {
        return supplier.get();
    }

    public static void main(String[] args) {
        IoSupplier<String> supplier = IoSupplierDemo::obtain;

        try {
            System.out.println(use(supplier));
        } catch (IOException e) {
            System.out.println("I/O: " + e.getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 IoSupplierDemo.java
java IoSupplierDemo
```

**Stdout chính xác**:

```text
I/O: Nguồn chưa sẵn sàng
```

`IoSupplier.get() throws IOException` chính xác hơn `Callable.call() throws Exception` nếu API chỉ cần mô tả lỗi I/O. Nó giúp caller xử lý đúng loại checked exception mà không mở rộng vô cớ thành `Exception`.

Lưu ý: không cần tạo functional interface mới cho mọi method đơn giản; chỉ nên làm khi giao diện này biểu diễn **hợp đồng callback có checked failure** thật sự cần thiết.

## 8. So sánh với lambda: cùng luật `throws`

Hai cách viết:

```java
IoSupplier<String> first = DataStore::read;
IoSupplier<String> second = () -> DataStore.read();
```

Đây là **mã minh họa**, giả định `DataStore.read() throws IOException` và `IoSupplier<T>` có `get() throws IOException`. Với mục đích tương thích checked exception, cả hai biểu thức đều phải đáp ứng hợp đồng SAM.

Lambda không phải “cửa sau” để né luật. Dạng sau **cố ý không biên dịch** nếu `DataStore.read() throws IOException`:

```java
java.util.function.Supplier<String> s =
        () -> DataStore.read();
```

Lý do: thân lambda có lời gọi có thể ném checked `IOException` nhưng `Supplier.get()` không khai báo checked exception. Dạng method reference:

```java
java.util.function.Supplier<String> s = DataStore::read;
```

cũng **không biên dịch** với cùng nguyên nhân về `throws`. Khác biệt cú pháp không làm thay đổi nghĩa vụ checked exception.

Tuy nhiên, lambda **có thể** chứa `try/catch` để xử lý hoặc chuyển lỗi:

```java
java.util.function.Supplier<String> s = () -> {
    try {
        return DataStore.read();
    } catch (IOException e) {
        return "fallback";
    }
};
```

Đây là **mã minh họa**, với fallback chỉ hợp lệ khi hợp đồng nghiệp vụ thực sự cho phép. Đoạn này phù hợp với `Supplier` vì **không có checked IOException thoát ra khỏi thân lambda**.

Chọn giữa method reference và lambda theo mức độ rõ ràng, không theo suy nghĩ rằng `::` có quy tắc exception “thoáng” hơn.

## 9. Static method reference với checked exception

Cú pháp:

```java
TargetAction action = UtilityClass::operation;
```

Khi `UtilityClass.operation()` là static, Java kiểm tra nó tương thích với SAM của `TargetAction`, gồm checked exceptions.

Một ví dụ Java 17 độc lập **`StaticReferenceDemo.java`**:

```java
import java.io.IOException;

public class StaticReferenceDemo {
    @FunctionalInterface
    interface Parser {
        int parse(String input) throws IOException;
    }

    static int parsePort(String input) throws IOException {
        int port = Integer.parseInt(input);
        if (port < 1 || port > 65535) {
            throw new IOException("Port ngoài giới hạn");
        }
        return port;
    }

    public static void main(String[] args) {
        Parser parser = StaticReferenceDemo::parsePort;

        try {
            System.out.println("Port: " + parser.parse("8080"));
            System.out.println("Port: " + parser.parse("70000"));
        } catch (IOException e) {
            System.out.println("Lỗi: " + e.getMessage());
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 StaticReferenceDemo.java
java StaticReferenceDemo
```

**Stdout chính xác**:

```text
Port: 8080
Lỗi: Port ngoài giới hạn
```

`Parser.parse` có cùng tham số/kiểu trả về với `parsePort` và cho phép `IOException`. Việc bắt `IOException` ở `main` áp dụng cho lời gọi qua `parser`.

**Một chú ý quan trọng:** `Integer.parseInt` có thể ném `NumberFormatException`, vốn là unchecked. Nó có thể lan truyền dù `Parser.parse` chỉ khai báo `throws IOException`. Mệnh đề `throws` của SAM không chặn unchecked exception và không bảo đảm mọi đầu vào có lỗi đều biến thành `IOException`.

## 10. Bound instance method reference: `object::method`

**Bound reference** cố định receiver ở thời điểm tạo biểu thức:

```java
ReaderService service = new ReaderService();
CheckedAction action = service::reload;
```

Đây là **mã minh họa**; `ReaderService.reload() throws IOException` được giả định tương thích với `CheckedAction.run() throws IOException`.

Trong hình thức này, SAM **không cần nhận receiver làm đối số**, vì receiver `service` đã được chọn. Checked exceptions được xét từ method đích theo hợp đồng.

Ví dụ đầy đủ **`BoundReferenceDemo.java`**:

```java
import java.io.IOException;

public class BoundReferenceDemo {
    static class Worker {
        private final String name;

        Worker(String name) {
            this.name = name;
        }

        void execute() throws IOException {
            System.out.println("Worker: " + name);
            throw new IOException("Chưa có dữ liệu");
        }
    }

    @FunctionalInterface
    interface CheckedAction {
        void run() throws IOException;
    }

    public static void main(String[] args) {
        Worker worker = new Worker("A");
        CheckedAction action = worker::execute;

        try {
            action.run();
        } catch (IOException e) {
            System.out.println("Bắt: " + e.getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 BoundReferenceDemo.java
java BoundReferenceDemo
```

**Stdout chính xác**:

```text
Worker: A
Bắt: Chưa có dữ liệu
```

Method `execute` vẫn khai báo checked `IOException`, nên không thể thay target thành `Runnable` chỉ bằng việc dùng `worker::execute`.

### Receiver `null` là vấn đề riêng với `throws`

Với bound reference `worker::execute`, Java kiểm tra receiver ở **thời điểm đánh giá method reference**. Nếu receiver bằng `null`, một `NullPointerException` có thể xuất hiện **trước khi SAM được gọi**.

Đây là unchecked exception, không phải một `IOException` cần bổ sung vào `throws` của interface. Không nên nhầm lỗi tạo bound reference vì receiver null với lỗi do method được tham chiếu phát sinh lúc SAM chạy.

## 11. Unbound instance reference: `Type::instanceMethod`

Hình thức unbound dùng **receiver là đối số đầu tiên của SAM**. Ví dụ:

```java
java.util.function.Function<String, Integer> length =
        String::length;
```

Khi `length.apply("abc")` chạy, đối số `"abc"` trở thành receiver để gọi `String.length()`. Với method có checked exception, SAM cũng cần khai báo khả năng ném checked exception đó.

Ví dụ Java 17 **`UnboundReferenceDemo.java`**:

```java
import java.io.IOException;

public class UnboundReferenceDemo {
    static class Document {
        private final String name;

        Document(String name) {
            this.name = name;
        }

        String read() throws IOException {
            if ("bad".equals(name)) {
                throw new IOException("Không mở được tài liệu");
            }
            return "Nội dung " + name;
        }
    }

    @FunctionalInterface
    interface Reader {
        String read(Document document) throws IOException;
    }

    public static void main(String[] args) {
        Reader reader = Document::read;
        try {
            System.out.println(reader.read(new Document("good")));
            System.out.println(reader.read(new Document("bad")));
        } catch (IOException e) {
            System.out.println("Bắt: " + e.getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 UnboundReferenceDemo.java
java UnboundReferenceDemo
```

**Stdout chính xác**:

```text
Nội dung good
Bắt: Không mở được tài liệu
```

Phân tích: `Document::read` không gắn sẵn một object `Document` nào. SAM `Reader.read(Document)` cung cấp receiver ở lúc gọi, và cho phép checked `IOException`.

Nếu dùng `Function<Document, String> fn = Document::read;`, Java không chấp nhận vì `Function.apply` không khai báo `throws IOException`. Đây là lỗi về hợp đồng exceptions, dù tham số và kiểu trả về khớp.

## 12. Constructor reference cũng kiểm tra checked exceptions

Ký hiệu `Type::new` có thể tạo object thông qua một constructor tương thích với SAM. Nếu constructor khai báo checked exception, **SAM của factory interface phải cho phép exception đó**.

Ví dụ Java 17 đầy đủ **`ConstructorReferenceDemo.java`**:

```java
import java.io.IOException;

public class ConstructorReferenceDemo {
    static class Connection {
        private final String address;

        Connection(String address) throws IOException {
            if (address.isBlank()) {
                throw new IOException("Địa chỉ trống");
            }
            this.address = address;
        }

        String address() {
            return address;
        }
    }

    @FunctionalInterface
    interface ConnectionFactory {
        Connection open(String address) throws IOException;
    }

    public static void main(String[] args) {
        ConnectionFactory factory = Connection::new;
        try {
            System.out.println("Đã tạo: "
                    + factory.open("db.internal").address());
            factory.open("");
        } catch (IOException e) {
            System.out.println("Lỗi factory: " + e.getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 ConstructorReferenceDemo.java
java ConstructorReferenceDemo
```

**Stdout chính xác**:

```text
Đã tạo: db.internal
Lỗi factory: Địa chỉ trống
```

`Connection::new` tương thích `ConnectionFactory.open(String) throws IOException`. Nếu viết:

```java
java.util.function.Function<String, Connection> factory =
        Connection::new;
```

đây là **phản ví dụ cố ý không biên dịch** vì constructor `Connection(String) throws IOException`, trong khi `Function.apply` không cho phép checked exception.

**Constructor reference không tự gọi constructor khi reference được gán.** Constructor chạy lúc `factory.open(...)` được gọi. Ngoại lệ từ constructor được kiểm tra theo kiểu tĩnh tại bước gán reference và thực sự ném tại bước gọi.

## 13. Tương thích theo quan hệ kế thừa của exception

Không cần SAM khai báo đúng y nguyên tên exception. Một **supertype hợp lệ** có thể bao phủ checked exception của method đích.

Ví dụ minh họa:

```java
static void openSpecific() throws java.io.FileNotFoundException {
    throw new java.io.FileNotFoundException("Không tìm thấy");
}
```

Nếu có interface:

```java
@FunctionalInterface
interface GeneralIoAction {
    void run() throws java.io.IOException;
}
```

thì `GeneralIoAction action = Example::openSpecific;` (giả sử method nằm trong `Example`) **hợp lệ** vì `FileNotFoundException` là subtype của `IOException`.

Chiều ngược lại không hợp lệ:

```java
@FunctionalInterface
interface SpecificIoAction {
    void run() throws java.io.FileNotFoundException;
}
```

Nếu method được tham chiếu chỉ khai báo `throws IOException` rộng, Java không thể bảo đảm mọi `IOException` đều là `FileNotFoundException`, nên method reference tới nó không tương thích với `SpecificIoAction`.

**Lỗi compile dựa trên những exception được khai báo và phân tích theo kiểu tĩnh, không dựa trên việc nhánh runtime trong một lần thử có ném loại nào.**

## 14. Nhiều checked exceptions phải được SAM bao phủ

Một method có thể khai báo nhiều loại lỗi:

```java
static void synchronize() throws java.io.IOException,
                                 java.sql.SQLException {
    // ...
}
```

Đây là **phương thức minh họa**, thân được lược vì không cần logic thực hiện. SAM tương thích:

```java
@FunctionalInterface
interface SyncAction {
    void run() throws java.io.IOException,
                      java.sql.SQLException;
}
```

Hoặc một SAM có `throws Exception` có thể bao phủ cả hai:

```java
@FunctionalInterface
interface GeneralAction {
    void run() throws Exception;
}
```

Nhưng SAM chỉ `throws IOException` **không đủ** nếu method tham chiếu có thể ném checked `SQLException`. Gộp vào `throws Exception` có thể dễ triển khai, nhưng sẽ khiến caller gọi SAM phải xử lý/khai báo kiểu **rộng hơn**. Khi API cần caller phân loại lỗi, khai báo cụ thể thường hữu ích hơn.

Một ví dụ độc lập **`MultipleThrowsReferenceDemo.java`**:

```java
import java.io.IOException;
import java.sql.SQLException;

public class MultipleThrowsReferenceDemo {
    @FunctionalInterface
    interface SyncAction {
        void run() throws IOException, SQLException;
    }

    static void sync(boolean dbFailure)
            throws IOException, SQLException {
        if (dbFailure) {
            throw new SQLException("DB lỗi");
        }
        throw new IOException("Tệp lỗi");
    }

    public static void main(String[] args) {
        SyncAction io = () -> sync(false);
        SyncAction db = () -> sync(true);

        for (SyncAction action : new SyncAction[] {io, db}) {
            try {
                action.run();
            } catch (IOException e) {
                System.out.println("I/O: " + e.getMessage());
            } catch (SQLException e) {
                System.out.println("SQL: " + e.getMessage());
            }
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 MultipleThrowsReferenceDemo.java
java MultipleThrowsReferenceDemo
```

**Stdout chính xác**:

```text
I/O: Tệp lỗi
SQL: DB lỗi
```

Đoạn này dùng **lambda** để truyền đối số `boolean` cố định; cách dùng method reference trực tiếp sẽ cần SAM có cùng danh sách tham số với `sync(boolean)`, ví dụ `void run(boolean) throws IOException, SQLException`. Trọng tâm là nhiều checked exceptions vẫn phải được target bao phủ.

## 15. Method reference có thêm tham số của phương thức gốc

Method reference phải phù hợp với SAM về các tham số. Nếu muốn gán `Demo::sync` trực tiếp, thay vì lambda đã cố định boolean, có thể định nghĩa:

```java
@FunctionalInterface
interface SyncOperation {
    void run(boolean dbFailure)
            throws java.io.IOException, java.sql.SQLException;
}
```

Rồi trong ngữ cảnh method `sync(boolean)` thuộc lớp `Demo`:

```java
SyncOperation operation = Demo::sync;
```

Đây là **mã minh họa**, không phải chương trình độc lập. Java sẽ kiểm tra:

- Một tham số kiểu `boolean` bên SAM chuyển vào method `sync(boolean)`.
- Kiểu trả về `void` tương thích.
- `throws IOException, SQLException` bên SAM bao phủ các checked exceptions của method `sync`.

Nếu một trong ba nhóm điều kiện không đáp ứng, phép gán có thể thất bại. **Method reference không tự bỏ tham số**; dùng lambda để cố định, chuyển đổi hoặc cung cấp logic bổ sung nếu cần.

## 16. Overload và lựa chọn method reference

Khi một lớp có **overloaded methods**, trình biên dịch tìm phương thức tương thích theo kiểu đích, số tham số, quan hệ kiểu và các quy tắc overload resolution.

Ví dụ:

```java
class Reporter {
    static void report(String text) {
        System.out.println("Text: " + text);
    }

    static void report(Integer code) {
        System.out.println("Code: " + code);
    }
}
```

Đây là **class minh họa**, không có `main`. Các target:

```java
java.util.function.Consumer<String> a = Reporter::report;
java.util.function.Consumer<Integer> b = Reporter::report;
```

được phân giải tới các overload có tham số tương ứng, không dựa trên `throws` vì hai method trên không khai báo checked exception.

**Không thể overload hai method chỉ khác mệnh đề `throws`**:

```java
// Cố ý không hợp lệ nếu đặt trong cùng một class:
void process() throws IOException { }
void process() throws SQLException { }
```

Dù hai danh sách `throws` khác nhau, Java coi hai method cùng tên và cùng danh sách tham số là cùng chữ ký cho mục đích này. Do đó `throws` không phải cách tạo lựa chọn overload.

Nếu gặp method reference không rõ ràng hoặc không tương thích, hãy xem **target type**, overload được chọn và mọi checked exceptions từ method cụ thể. Đừng nghĩ compiler sẽ chọn một overload chỉ vì có `throws` “vừa” với SAM.

## 17. Generic functional interface khai báo kiểu exception

Một pattern phù hợp khi thư viện cần nhiều loại checked exception là tham số hóa kiểu exception:

```java
@FunctionalInterface
interface ThrowingFunction<T, R, E extends Exception> {
    R apply(T input) throws E;
}
```

Ở đây:

- `T` là kiểu đầu vào.
- `R` là kiểu kết quả.
- `E extends Exception` là kiểu exception được phép trong hợp đồng.
- Khi chọn `E = IOException`, SAM được hiểu là `R apply(T) throws IOException` theo cách kiểm tra kiểu Java phù hợp.
- Khi chọn `E = Exception`, SAM cho phép nhiều loại checked exception hơn nhưng hợp đồng caller rộng hơn.

Ví dụ Java 17 độc lập **`GenericThrowingFunctionDemo.java`**:

```java
import java.io.IOException;

public class GenericThrowingFunctionDemo {
    @FunctionalInterface
    interface ThrowingFunction<T, R, E extends Exception> {
        R apply(T input) throws E;
    }

    static Integer parseFile(String text) throws IOException {
        if (text.isBlank()) {
            throw new IOException("Không có nội dung");
        }
        return Integer.parseInt(text);
    }

    static <T, R, E extends Exception> R execute(
            ThrowingFunction<T, R, E> fn, T value) throws E {
        return fn.apply(value);
    }

    public static void main(String[] args) {
        ThrowingFunction<String, Integer, IOException> parser =
                GenericThrowingFunctionDemo::parseFile;

        try {
            System.out.println("Số: " + execute(parser, "25"));
            execute(parser, "");
        } catch (IOException e) {
            System.out.println("I/O: " + e.getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 GenericThrowingFunctionDemo.java
java GenericThrowingFunctionDemo
```

**Stdout chính xác**:

```text
Số: 25
I/O: Không có nội dung
```

Dạng này giữ checked exception cụ thể qua interface generic và phương thức `execute`. Vì `execute` khai báo `throws E`, khi `E` là `IOException`, caller vẫn phải xử lý `IOException`.

**Một giới hạn thiết kế:** một tham số `E extends Exception` diễn đạt tốt một nhóm exception có thể bao phủ bằng một kiểu. Nếu callback ném nhiều checked exception **không có một superclass hẹp hữu ích**, bạn có thể cần `throws` nhiều loại cụ thể, một exception domain chung, hoặc một hợp đồng kết quả khác. Không phải generic exception type tự giải quyết mọi bài toán.

## 18. Adapter từ checked function sang `Function`

Một API chuẩn như `Stream.map` nhận `Function`, không cho phép checked exceptions. Nếu bạn có `ThrowingFunction<T,R,IOException>`, cần lựa chọn chính sách chuyển đổi hoặc xử lý lỗi.

Một adapter chuyển `IOException` thành unchecked `UncheckedIOException` là một lựa chọn khi hợp đồng cho phép:

```java
static <T, R> java.util.function.Function<T, R> uncheckedIo(
        ThrowingFunction<T, R, IOException> fn) {
    return value -> {
        try {
            return fn.apply(value);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    };
}
```

Đây là **phương thức minh họa**, giả sử `ThrowingFunction` đã khai báo; có import `java.io.IOException` nếu không viết tên đầy đủ. Adapter được dùng:

```java
java.util.function.Function<String, Integer> fn =
        uncheckedIo(Parser::parse);
```

Đây cũng là **mã khung**; `Parser.parse(String) throws IOException` được giả định. Sau chuyển đổi:

- `Function.apply` không khai báo checked `IOException`.
- Nếu callback ném `IOException`, adapter bắt và ném **`UncheckedIOException`** (một unchecked exception).
- `UncheckedIOException.getCause()` giữ exception I/O gốc.
- Caller qua `Function` không phải khai báo `throws IOException` nhưng cần có chiến lược xử lý nếu failure thực sự xảy ra.
- Đổi kiểu exception **không tự phục hồi** và không bảo đảm thao tác thành công.

Không nên bọc mọi `Exception` bằng `RuntimeException` chỉ để trình biên dịch im lặng. Hãy chọn conversion phù hợp với hợp đồng API và giữ `cause`.

## 19. Ví dụ Java 17 chạy được: adapter giữ nguyên `cause`

Lưu **`AdapterReferenceDemo.java`**:

```java
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Function;

public class AdapterReferenceDemo {
    @FunctionalInterface
    interface IoFunction<T, R> {
        R apply(T value) throws IOException;
    }

    static <T, R> Function<T, R> unchecked(IoFunction<T, R> fn) {
        return value -> {
            try {
                return fn.apply(value);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    static String read(String name) throws IOException {
        if ("bad".equals(name)) {
            throw new IOException("Không đọc được bad");
        }
        return "OK:" + name;
    }

    public static void main(String[] args) {
        Function<String, String> fn =
                unchecked(AdapterReferenceDemo::read);

        System.out.println(fn.apply("good"));
        try {
            fn.apply("bad");
        } catch (UncheckedIOException e) {
            System.out.println("Wrapper: "
                    + e.getClass().getSimpleName());
            System.out.println("Cause: "
                    + e.getCause().getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 AdapterReferenceDemo.java
java AdapterReferenceDemo
```

**Stdout chính xác**:

```text
OK:good
Wrapper: UncheckedIOException
Cause: Không đọc được bad
```

Nhờ `IoFunction.apply throws IOException`, method reference `AdapterReferenceDemo::read` được nhận trước khi adapter chuyển thành `Function`. `UncheckedIOException` giữ nguyên object `IOException` ở `getCause()`, giúp không mất trace và message gốc.

Adapter này **chuyển hợp đồng lỗi từ checked sang unchecked**. Đây là lựa chọn thiết kế cần được công bố, không phải một tính năng tự động của method reference.

## 20. Stream API và các thao tác lazy

`Stream.map` nhận `Function<? super T, ? extends R>`, còn `forEach` nhận `Consumer<? super T>`. Các SAM đó **không khai báo checked exceptions**.

Vì vậy code kiểu:

```java
// Đoạn minh họa, cố ý không biên dịch:
stream.map(Files::readString)
      .toList();
```

nếu `stream` là `Stream<Path>` và `Files.readString(Path)` có `throws IOException`, method reference sẽ không tương thích với `Function<Path,String>` do checked `IOException`. Việc terminal operation `toList()` nằm ở cuối không giúp Java tự bắt lỗi.

Có những lựa chọn rõ ràng:

1. **Đọc tuần tự bằng vòng lặp có `throws IOException` hoặc `try/catch`** nếu muốn caller xử lý checked I/O.
2. **Dùng adapter** chuyển `IOException` thành `UncheckedIOException` có `cause` nếu pipeline chỉ phù hợp unchecked exception.
3. **Trả kết quả có trạng thái** (ví dụ success/error) nếu mỗi phần tử có thể thất bại và tác vụ cần thu thập cả kết quả lẫn lỗi.
4. **Xử lý lỗi trong lambda** nếu có fallback thực sự hợp lệ cho một phần tử.
5. **Tách thao tác I/O khỏi pipeline**, khi điều đó giúp luồng lỗi dễ hiểu và kiểm soát tài nguyên hơn.

Một stream pipeline có thể **lazy**: nhiều phép tính trung gian chưa chạy cho đến khi có terminal operation. Nhưng luật checked exception được kiểm tra **ngay lúc biên dịch biểu thức method reference**, không chờ tới khi pipeline chạy.

## 21. `Function` hay `Callable` không tự tạo một quy tắc retry

Bất kỳ method reference nào cũng chỉ mô tả **hành vi có thể gọi**, không tự động bổ sung retry hoặc bù trừ thao tác.

Ví dụ:

```java
Callable<String> task = Client::read;
```

đây là **mã minh họa**: nếu `Client.read() throws IOException` là static không tham số, `Callable.call() throws Exception` nhận được reference. Nếu sau đó `task.call()` ném `IOException`, việc có thử lại hay không tùy mã gọi.

Không nên suy luận rằng chọn SAM rộng `throws Exception` nghĩa là Java “xử lý lỗi ở nền” cho bạn. Nếu dùng `ExecutorService.submit(Callable)`, cơ chế bất đồng bộ có thể **bọc lỗi khi lấy kết quả qua `Future.get()`**, chẳng hạn trong `ExecutionException`, nhưng đó là hợp đồng của executor/future, **không phải của method reference hoặc cú pháp `throws`**.

Nhiệm vụ callback và chính sách xử lý lỗi nên được thiết kế ở hai tầng tách biệt: **chữ ký target** cho tính hợp lệ compile time, và **caller/framework** cho cách phản ứng runtime.

## 22. Phương thức tham chiếu có `throws Exception` rộng

Nếu một method đích khai báo `throws Exception`, Java không tự suy luận rằng nó thực tế chỉ ném `IOException` bên trong để gán vào một SAM chỉ cho phép `IOException`.

Ví dụ:

```java
static void legacyOperation() throws Exception {
    throw new java.io.IOException("I/O lỗi");
}
```

Đây là **phương thức minh họa**. Dù implementation hiện tại chỉ ném `IOException`, hợp đồng tĩnh của method là `throws Exception`. Gán:

```java
IoAction action = Example::legacyOperation;
```

với `IoAction.run() throws IOException` là **không hợp lệ** chỉ vì method đích công bố kiểu quá rộng. Trong một phiên bản sau, `legacyOperation` có thể ném một checked `Exception` không phải `IOException` theo hợp đồng.

Các cách xử lý hợp lý:

- Nếu sở hữu method, **thu hẹp chữ ký `throws`** khi hợp đồng thật sự cho phép.
- Nếu target cho phép `Exception`, dùng target có `run() throws Exception`.
- Nếu cần chuyển loại lỗi, viết một **adapter/lambda có `try/catch`** và chọn cách giữ/đổi hợp đồng rõ ràng.
- Không ép kiểu vô căn cứ từ `Exception` sang `IOException`; làm vậy có thể phát sinh `ClassCastException` ngay trong xử lý lỗi.

Đây là nguyên tắc chung: **compiler tin hợp đồng tĩnh**, không dự đoán mọi hành vi runtime của thư viện trong tương lai.

## 23. Phương thức target khai báo `throws Exception` không buộc reference phải ném checked exception

Chiều ngược lại vẫn hợp lệ: target SAM có `throws Exception` nhưng method được tham chiếu **không** ném checked exception nào.

Ví dụ:

```java
@FunctionalInterface
interface Task {
    String run() throws Exception;
}
```

Với method không checked exceptions:

```java
static String greeting() {
    return "Xin chào";
}
```

thì phép gán `Task t = Example::greeting;` hợp lệ. SAM công bố **tập lỗi có thể được cho phép**, không đòi implementation nhất thiết ném một exception.

Tuy nhiên tại lời gọi `t.run()`, compiler vẫn thấy `throws Exception` của **SAM**, nên caller phải catch/declare `Exception` theo hợp đồng. Đó là chi phí của việc chọn target rộng, dù implementation cụ thể rất đơn giản.

Không nên cố dùng `Callable` chỉ vì có sẵn nếu API của bạn cần hợp đồng checked exception cụ thể để hỗ trợ caller.

## 24. Method reference và ngoại lệ unchecked

`RuntimeException` và `Error` thuộc nhóm unchecked, không bị quy tắc catch-or-declare ép khai báo. Do đó một method reference có thể hợp lệ với `Runnable` dù method có thể ném `IllegalStateException`.

Ví dụ độc lập **`UncheckedReferenceDemo.java`**:

```java
public class UncheckedReferenceDemo {
    static void enforce() {
        throw new IllegalStateException("Trạng thái không hợp lệ");
    }

    public static void main(String[] args) {
        Runnable task = UncheckedReferenceDemo::enforce;
        try {
            task.run();
        } catch (IllegalStateException e) {
            System.out.println("Bắt: " + e.getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 UncheckedReferenceDemo.java
java UncheckedReferenceDemo
```

**Stdout chính xác**:

```text
Bắt: Trạng thái không hợp lệ
```

`Runnable.run()` không khai báo checked exception, nhưng không ngăn unchecked `IllegalStateException` thoát ra. `main` vẫn có thể bắt exception để phản hồi có nghĩa.

**Không diễn giải** rằng dùng `Runnable` làm mọi lỗi bất ngờ biến mất. Target type chỉ quy định giới hạn checked exception ở compile time, không tạo ra cơ chế bắt `RuntimeException` tự động.

## 25. `InterruptedException` và callback không khai báo checked exception

Một trường hợp dễ sai trong ứng dụng đa luồng:

```java
static void waitForSignal() throws InterruptedException {
    Thread.sleep(100);
}
```

Method reference `SomeClass::waitForSignal` **không** tương thích `Runnable` vì `Runnable.run()` không cho phép checked `InterruptedException`.

Nếu API cho phép, thiết kế SAM riêng `void run() throws InterruptedException` và để caller quyết định cách xử lý. Khi phải dùng `Runnable`, thường cần lambda/adapter xử lý ngắt có chủ đích:

```java
Runnable task = () -> {
    try {
        waitForSignal();
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Tác vụ bị ngắt", e);
    }
};
```

Đây là **mã minh họa trong class chứa `waitForSignal`**. Một số API chờ như `Thread.sleep` xóa trạng thái interrupt khi ném `InterruptedException`; gọi lại `Thread.currentThread().interrupt()` thường cần để không đánh mất tín hiệu hủy.

Nhưng **không áp dụng wrapper một cách máy móc**: nếu framework có chính sách hủy riêng, nên tuân thủ hợp đồng của nó. Nguyên tắc là không nuốt `InterruptedException` hoặc đổi thành một thành công giả chỉ vì muốn gán method reference cho `Runnable`.

## 26. `Files::readString` và kiểm tra `throws` khi dùng JDK API

Từ Java 11, `java.nio.file.Files.readString(Path)` khai báo `throws IOException` và trả `String`. Nó có thể được dùng trong Java 17 bằng target phù hợp:

```java
@FunctionalInterface
interface FileReader {
    String read(java.nio.file.Path path)
            throws java.io.IOException;
}
```

Trong ngữ cảnh đã có interface:

```java
FileReader read = java.nio.file.Files::readString;
```

đây là **mã minh họa**, không phụ thuộc tệp cụ thể tại thời điểm tạo reference. Khi gọi `read.read(path)`, chương trình phải catch/declare `IOException` vì SAM `FileReader.read(Path)` công bố nó.

Ngược lại:

```java
java.util.function.Function<java.nio.file.Path, String> read =
        java.nio.file.Files::readString;
```

là **phản ví dụ cố ý không biên dịch**: target `Function.apply` không khai báo checked `IOException`.

Nếu cần hành vi chỉ đọc từ một `Path` cố định, có thể dùng lambda `() -> Files.readString(path)` với `IoSupplier<String>`. Một method reference `Files::readString` không thể tự biến phương thức nhận `Path` thành SAM không có tham số nếu không có cơ chế cung cấp tham số.

## 27. Có thể bắt exception bên ngoài việc gọi SAM không?

Có. Nếu target SAM có checked `throws`, bạn **bắt ở điểm gọi**:

```java
IoSupplier<String> supplier = Store::load;

try {
    String value = supplier.get();
    System.out.println(value);
} catch (IOException e) {
    System.out.println("I/O: " + e.getMessage());
}
```

Đây là **mã minh họa**, giả định `IoSupplier<T>.get() throws IOException` và `Store.load() throws IOException`. `catch` bên ngoài bao lấy **lời gọi `supplier.get()`**, nơi exception thực sự được phép thoát ra.

Nhưng không thể sửa việc gán **không tương thích** bằng cách chỉ bọc *dòng gán* trong `try/catch`:

```java
try {
    Runnable r = Store::load; // Vẫn lỗi compile nếu Store.load() throws IOException.
} catch (IOException e) {
    // Không làm method reference hợp lệ.
}
```

Đây là **phản ví dụ cố ý không biên dịch**. Vì `Runnable` không cho checked exception, compiler từ chối reference trước khi bàn đến lời gọi. `try/catch` quanh phép gán không thêm `throws` cho SAM, và method được tham chiếu cũng chưa được gọi tại dòng gán.

**Vị trí đặt handler phải theo nơi checked exception thực sự có thể phát sinh và hợp đồng SAM**, chứ không chỉ ở gần cú pháp `::`.

## 28. Method reference và tính đa hình ở runtime

Phân tích checked exception được quyết định từ **kiểu tĩnh** của method tham chiếu và target SAM. Nhưng khi thực thi, Java có thể gọi **implementation override thực tế** theo cơ chế đa hình.

Ví dụ:

```java
interface Repository {
    String fetch() throws IOException;
}

class MemoryRepository implements Repository {
    public String fetch() {
        return "cache";
    }
}
```

Đây là **mã minh họa**, cần import `IOException`. Nếu biến có kiểu `Repository repo = new MemoryRepository();`, method reference `repo::fetch` được kiểm tra dựa trên hợp đồng `Repository.fetch() throws IOException`, ngay cả khi implementation `MemoryRepository.fetch()` không khai báo checked exception.

Nghĩa là:

- `IoSupplier<String> supplier = repo::fetch;` hợp lệ.
- `Supplier<String> supplier = repo::fetch;` có thể bị từ chối vì kiểu tĩnh `Repository` công bố `IOException`.
- Nếu biến được khai báo kiểu `MemoryRepository concrete`, reference `concrete::fetch` không có checked `IOException` theo chữ ký `MemoryRepository.fetch()`, nên có thể hợp lệ với `Supplier<String>`.

**Không nên dùng thông tin “lúc chạy luôn là MemoryRepository” để bỏ qua hợp đồng static type.** Đó là điểm thiết yếu của thiết kế API và polymorphism.

## 29. Constructor `throws` và generic factory

Một factory generic có checked exception có thể được diễn đạt bằng interface:

```java
@FunctionalInterface
interface Factory<T, E extends Exception> {
    T create(String name) throws E;
}
```

Nếu constructor `Resource(String) throws IOException`, có thể chọn:

```java
Factory<Resource, IOException> factory = Resource::new;
```

Đây là **mã minh họa**, giả định đã định nghĩa class `Resource` với constructor thích hợp. `E` biểu diễn ngoại lệ mà constructor có thể ném. Caller khi gọi `factory.create(name)` phải đáp ứng `throws IOException`.

Điều này có giá trị khi một API framework có nhiều factory nhưng muốn cho phép mỗi factory công bố **một checked exception cụ thể**. Nếu một factory có nhiều loại checked exception độc lập, đừng ép mô hình một `E` nếu nó làm hỏng ý nghĩa lỗi: chọn target với `throws` phù hợp hoặc abstraction khác.

## 30. Kiểm thử tính tương thích bằng chương trình tự kiểm tra

Một bài test hữu ích cần phân biệt:

- **Tương thích compile time:** phép gán method reference phải được compiler chấp nhận.
- **Hành vi runtime:** khi SAM được gọi, method đích chạy và ném/không ném đúng exception.
- **Hợp đồng checked:** caller phải catch/declare ở điểm gọi SAM, không phải vì đã gán reference.

Ví dụ độc lập **`MethodReferenceAssertionsDemo.java`**:

```java
import java.io.IOException;

public class MethodReferenceAssertionsDemo {
    @FunctionalInterface
    interface IoTask {
        void run() throws IOException;
    }

    static void failIo() throws IOException {
        throw new IOException("expected");
    }

    static void failUnchecked() {
        throw new IllegalStateException("unchecked");
    }

    public static void main(String[] args) {
        IoTask checked = MethodReferenceAssertionsDemo::failIo;
        Runnable unchecked = MethodReferenceAssertionsDemo::failUnchecked;

        try {
            checked.run();
            throw new AssertionError("Thiếu IOException");
        } catch (IOException e) {
            if (!"expected".equals(e.getMessage())) {
                throw new AssertionError("Sai I/O message");
            }
            System.out.println("PASS checked");
        }

        try {
            unchecked.run();
            throw new AssertionError("Thiếu unchecked");
        } catch (IllegalStateException e) {
            if (!"unchecked".equals(e.getMessage())) {
                throw new AssertionError("Sai unchecked message");
            }
            System.out.println("PASS unchecked");
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 MethodReferenceAssertionsDemo.java
java MethodReferenceAssertionsDemo
```

**Stdout chính xác**:

```text
PASS checked
PASS unchecked
```

Bài test dùng `AssertionError` để chủ động thất bại khi kết quả sai, không phụ thuộc tùy chọn `-ea` của từ khóa `assert`. Kiểm thử compile-negative như `Runnable r = SomeClass::checkedMethod;` nên được tách trong một file/dự án test riêng **và được kỳ vọng không biên dịch**, không trộn cùng file chương trình runnable ở trên.

## 31. Các thông báo lỗi biên dịch thường gặp

| Triệu chứng | Nguyên nhân cần xét | Hướng xử lý |
| --- | --- | --- |
| `incompatible thrown types IOException in functional expression` | Method ref có checked `IOException`; SAM không cho phép | Chọn SAM có `throws IOException` hoặc adapter/lambda bắt phù hợp |
| `unreported exception IOException; must be caught or declared` | Gọi SAM/khối lambda có checked exception chưa xử lý | Bắt tại điểm gọi hoặc khai báo trên phương thức bao ngoài |
| `invalid method reference` | Có thể sai tham số/kiểu trả về hoặc checked exception | Kiểm tra toàn bộ chữ ký, không chỉ `throws` |
| `unexpected instance method ... found in unbound lookup` | Sai cách bound/unbound receiver | Dùng `object::method`, `Type::method` hoặc lambda hợp số tham số |
| `reference to method is ambiguous` | Quá tải, kiểu tham số hoặc target type không đủ rõ | Làm rõ target type, thay reference bằng lambda có kiểu tham số tường minh nếu cần |
| `bad return type in method reference` | SAM cần kiểu trả về không thể thích nghi từ method đích | Chọn target phù hợp hoặc lambda chuyển đổi |
| `Exception is never thrown in body of corresponding try statement` | Handler bắt checked exception ở chỗ không có lời gọi tương ứng | Bắt ở đúng nơi gọi SAM hoặc sửa loại handler |
| `cannot infer type-variable(s)` | Interface generic hoặc method generic không suy luận được | Chỉ rõ tham số kiểu/target type; đảm bảo exception type tương thích |

**Thông báo lỗi nguyên văn phụ thuộc compiler/JDK.** Bảng giúp nhận biết nhóm vấn đề, không phải danh sách chuỗi lỗi ổn định cần dùng trong tool tự động.

Một lỗi ở method reference **không nhất thiết** do `throws`; nếu method có checked exception nhưng số tham số sai, sửa `throws` cũng chưa đủ.

## 32. Sai lầm hiểu biết phổ biến

### 32.1. “Dùng `::` tự biến checked exception thành unchecked.”

Sai. Method reference không tạo wrapper exception. Nếu muốn đổi hợp đồng, bạn phải triển khai adapter/lambda có chính sách rõ ràng.

### 32.2. “Target `Runnable` sẽ nuốt mọi exception.”

Sai. `Runnable.run` không cho checked exception ở compile time, nhưng unchecked exceptions vẫn có thể thoát ra khi thực thi.

### 32.3. “`Callable` nghĩa là mọi checked exception sẽ được tự bắt.”

Sai. `Callable.call() throws Exception` cho phép lỗi thoát; caller hoặc framework thực thi vẫn phải chịu trách nhiệm theo hợp đồng.

### 32.4. “Compiler nhìn thân method nên biết dù `throws Exception` chỉ ném `IOException`.”

Không theo cách đó ở điểm sử dụng thông thường. Chữ ký tĩnh rộng là hợp đồng cần được tôn trọng.

### 32.5. “Bọc dòng `Supplier s = X::read` trong `try/catch` sẽ hợp lệ.”

Không. Handler quanh dòng gán không thay đổi `Supplier.get()` để cho phép checked `IOException`.

### 32.6. “Một method reference sai chỉ vì `throws`.”

Không nhất thiết. Sai receiver, số tham số, overload và kiểu trả về cũng gây lỗi.

### 32.7. “Khai báo SAM `throws IOException` thì implementation bắt buộc phải ném IOException.”

Sai. Một implementation không ném checked exception vẫn tương thích; caller nhìn qua SAM vẫn phải đáp ứng chữ ký đó.

### 32.8. “Method reference tự gọi method ngay khi gán.”

Sai trong trường hợp thông thường. Reference được tạo để gọi về sau; bound reference với receiver null có quy tắc kiểm tra lúc đánh giá nhưng không phải lời gọi nghiệp vụ.

### 32.9. “Checked exception từ method đích có thể bị bỏ qua khi nằm trong Stream API.”

Sai. `Function`/`Consumer` không cho checked exception; cần chọn hợp đồng khác hoặc xử lý/chuyển lỗi.

### 32.10. “Mỗi callback cần `throws Exception` thật rộng.”

Không. Khi có thể, giữ tập exception hẹp giúp người gọi hiểu và xử lý API. Nếu chỉ có một loại I/O, `IoSupplier<T>` có `throws IOException` thường rõ hơn một interface với `throws Exception`.

### 32.11. “Unchecked exception có nghĩa lỗi không quan trọng.”

Sai. Unchecked chỉ là phân loại cho catch-or-declare; hệ thống vẫn phải quản lý trạng thái và lỗi theo hợp đồng.

### 32.12. “Overload theo `throws` là hợp lệ.”

Sai. Không tạo hai method có cùng chữ ký chỉ khác danh sách checked exceptions.

## 33. Câu hỏi tự kiểm tra

**Câu 1.** Phương thức `static void load() throws IOException` có gán trực tiếp vào `Runnable` bằng method reference không?  
**Đáp án:** Không. `Runnable.run()` không cho phép checked `IOException`.

**Câu 2.** Có thể gán `load` vào `CheckedAction.run() throws IOException` không?  
**Đáp án:** Có, nếu tham số và kiểu trả về cũng tương thích.

**Câu 3.** `Callable<String> task = Source::read` có hợp lệ nếu `read() throws IOException` và trả `String` không?  
**Đáp án:** Có. `IOException` là subtype của `Exception`, được `Callable.call()` cho phép.

**Câu 4.** Khi gọi `task.call()` qua `Callable<String>`, caller cần xét kiểu checked nào?  
**Đáp án:** Theo hợp đồng tĩnh `Callable.call() throws Exception`, caller phải catch/declare `Exception` (hoặc xử lý phù hợp).

**Câu 5.** `String::length` gán vào `Supplier<Integer>` có hợp lệ không?  
**Đáp án:** Không. Unbound reference cần receiver `String` như tham số SAM; `Supplier.get()` không có tham số.

**Câu 6.** `String::length` gán vào `Function<String,Integer>` có hợp lệ không?  
**Đáp án:** Có. Receiver `String` là đầu vào, kết quả `int` có thể boxed thành `Integer`.

**Câu 7.** Method `throws FileNotFoundException` có phù hợp với SAM `throws IOException` không?  
**Đáp án:** Có; `FileNotFoundException` là lớp con của `IOException`.

**Câu 8.** Method `throws Exception` có phù hợp SAM chỉ `throws IOException` không?  
**Đáp án:** Không, vì `Exception` rộng hơn `IOException`.

**Câu 9.** Constructor `Resource(String) throws IOException` có gán trực tiếp `Resource::new` vào `Function<String,Resource>` không?  
**Đáp án:** Không. `Function.apply` không cho checked `IOException`.

**Câu 10.** Method reference tới method có hai checked exception `IOException` và `SQLException` có phù hợp SAM chỉ `throws IOException` không?  
**Đáp án:** Không. `SQLException` không thuộc `IOException`.

**Câu 11.** Method reference tới method có thể ném `IllegalStateException` có được dùng với `Runnable` không?  
**Đáp án:** Có, nếu những yếu tố chữ ký khác khớp; unchecked exception không phải khai báo checked.

**Câu 12.** Nếu method reference bị compiler từ chối với `Supplier`, bọc dòng gán trong `try/catch(IOException)` có sửa được không?  
**Đáp án:** Không. `Supplier.get` vẫn không khai báo checked exception.

**Câu 13.** Khi nào dùng lambda thay vì method reference để xử lý checked exception?  
**Đáp án:** Khi cần `try/catch`, fallback, đổi lỗi có cause, truyền tham số bổ sung hoặc logic kết hợp mà `::` không diễn đạt trực tiếp.

**Câu 14.** Vì sao `try/catch` trong lambda có thể giúp dùng `Supplier`?  
**Đáp án:** Vì lambda có thể xử lý hoặc chuyển checked exception để không còn checked exception bị phép tính SAM ném ra ngoài.

**Câu 15.** `@FunctionalInterface interface Task { void run() throws IOException; }` có yêu cầu mọi implementation thực sự ném IOException không?  
**Đáp án:** Không. `throws` cho phép, không bắt buộc phải ném.

**Câu 16.** Có thể overload hai method chỉ khác `throws IOException` và `throws SQLException` không?  
**Đáp án:** Không. Danh sách `throws` không tham gia tạo chữ ký overload khác nhau.

**Câu 17.** Một reference `repo::fetch` với biến kiểu `Repository` công bố `throws IOException` có được kiểm tra khác với biến kiểu implementation override không khai báo checked lỗi không?  
**Đáp án:** Có thể khác vì kiểu tĩnh của receiver quyết định method được nhìn thấy và hợp đồng checked exception ở điểm tham chiếu.

**Câu 18.** Dùng adapter `UncheckedIOException` có còn là checked I/O đối với caller `Function` không?  
**Đáp án:** Không ở mặt chữ ký; `UncheckedIOException` là unchecked, nhưng `getCause()` vẫn giữ `IOException` gốc và caller/framework phải quyết định xử lý.

**Câu 19.** `Stream.map(Files::readString)` với `Stream<Path>` có trực tiếp hợp lệ không?  
**Đáp án:** Không, vì `Files.readString` có checked `IOException` trong khi `Function` của `map` không cho phép checked exception.

**Câu 20.** Lỗi `NullPointerException` do receiver null khi đánh giá bound method reference có yêu cầu thêm `throws` không?  
**Đáp án:** Không. Đây là unchecked exception, khác với các checked exception mà method đích có thể ném khi SAM được gọi.

## 34. Checklist khi review method reference có `throws`

Trước khi gán `Type::method` hoặc `object::method` vào functional interface, hãy kiểm tra:

1. **Target functional interface là gì?** Xác định chính xác SAM và chữ ký của nó.
2. **SAM có `throws` nào?** `Runnable`/`Function` không giống `Callable` hoặc custom interface.
3. **Phương thức được tham chiếu có checked exceptions nào theo kiểu tĩnh?** Đọc chữ ký đã chọn, không đoán từ một lần chạy.
4. **Tất cả checked exceptions có được target cho phép không?** Xét quan hệ subtype/supertype.
5. **Có nhiều checked exceptions không?** Đừng bỏ sót loại thứ hai chỉ vì loại thứ nhất đã khớp.
6. **Method reference thuộc dạng nào?** Static, bound, unbound hay constructor.
7. **Số tham số, receiver và kiểu trả về khớp chưa?** Không chỉ xét `throws`.
8. **Có overloaded methods không?** Đảm bảo target type đủ rõ để chọn overload.
9. **Có generic type parameter về exception không?** Xác định type argument `E` phù hợp, tránh fallback sang `Exception` quá rộng nếu không cần.
10. **Nếu đang dùng Stream/Function/Consumer, có cần adapter không?** Không tự nuốt lỗi hoặc làm giả thành công.
11. **Adapter có giữ `cause` khi chuyển sang unchecked không?** Nên dùng wrapper có nguyên nhân phù hợp.
12. **Tầng nào xử lý lỗi cuối?** Caller, controller, job runner hay framework.
13. **Có `InterruptedException` không?** Tôn trọng ngữ nghĩa ngắt/hủy.
14. **Có nguy cơ receiver `null` với bound reference không?** Đánh giá tại thời điểm tạo reference.
15. **Lỗi có được kiểm thử runtime bằng cách gọi SAM không?** Việc gán thành công không chứng minh hành vi thành công.
16. **Có test compile-negative cho những phép gán bị cấm không?** Tách khỏi ví dụ runnable để tránh phá build.
17. **Tài liệu API có nói rõ checked/unchecked và ý nghĩa lỗi không?** Không chỉ dựa vào tên interface.
18. **Có đang tạo interface mới chỉ để tránh một lỗi compile cục bộ không?** Hãy cân nhắc hợp đồng dài hạn trước khi mở rộng API.

## 35. Tổng kết

**Method reference phải tương thích với `throws` của functional interface đích**, không chỉ với danh sách tham số và kiểu trả về. Một method khai báo `throws IOException` có thể được tham chiếu qua `IoSupplier<T>` có `get() throws IOException` hoặc qua `Callable<T>` có `call() throws Exception`, nhưng không trực tiếp qua `Supplier<T>`, `Function<T,R>` hay `Runnable` vốn không khai báo checked exception.

Điểm kiểm tra thực tế là **chữ ký của method/SAM theo kiểu tĩnh**. Java không tự catch, retry hay chuyển checked exception khi thấy `::`. Bound, unbound, static và constructor reference đều phải tuân thủ luật này; method overload và generic type inference có thể làm bước lựa chọn phức tạp hơn.

Khi API chuẩn không cho checked exception, các lựa chọn đúng là: **định nghĩa callback có `throws` phù hợp, bắt và xử lý trong lambda, đổi sang unchecked wrapper có `cause` theo hợp đồng, hoặc dùng kiểu kết quả có trạng thái**. Không nuốt lỗi chỉ để compile và không ép `throws Exception` rộng nếu caller cần hợp đồng cụ thể.

Ghi nhớ ngắn nhất: **“`::` thay đổi cách diễn đạt lời gọi, không thay đổi luật checked exceptions của Java.”**


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lambda-generic-exceptions-003">Runnable, Callable và khác biệt về khai báo ngoại lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Runnable, Callable và khác biệt về khai báo ngoại lệ trong Java

> **Java Core – Exception** · Nhóm: **Lambda và generic** · Trọng tâm: hợp đồng `throws` của hàm mục tiêu, cách lambda xử lý checked exception, và cách exception truyền từ tác vụ sang caller qua `Future`. Nội dung áp dụng cho Java 17+.

## 1. Câu trả lời cốt lõi

`Runnable` và `Callable<V>` đều là **functional interface**, vì vậy có thể tạo implementation bằng lambda. Nhưng hai interface có chữ ký khác nhau:

~~~java
// Hợp đồng cốt lõi của java.lang.Runnable:
void run();

// Hợp đồng cốt lõi của java.util.concurrent.Callable<V>:
V call() throws Exception;
~~~

Đây là **hai khai báo chữ ký minh họa**, không phải một file Java độc lập.

Chúng khác nhau ở **hai khía cạnh độc lập**:

1. **Kết quả:** `Runnable.run()` trả `void`; `Callable<V>.call()` trả giá trị kiểu `V`.
2. **Checked exception:** `Runnable.run()` **không khai báo checked exception**; `Callable<V>.call()` khai báo `throws Exception`. Một lambda hoặc implementation của `Callable<V>` có thể để checked exception phù hợp thoát ra từ `call()`; lambda/implementation của `Runnable` phải **tự xử lý** checked exception không được phép khai báo trong hợp đồng `run()`.

**Cực kỳ quan trọng:** `Runnable` vẫn có thể ném **unchecked exception** như `IllegalStateException` hoặc `UncheckedIOException`. Không có `throws` **không có nghĩa là không bao giờ phát sinh lỗi**.

Đồng thời, `Callable.call() throws Exception` không có nghĩa nó **luôn ném exception**. Nó có thể trả kết quả bình thường; nó cũng có thể ném unchecked exception. Mệnh đề `throws` nêu giới hạn checked exception mà phương thức có thể truyền ra theo hệ thống kiểu Java, không phải mô tả một sự kiện chắc chắn xảy ra.

### So sánh nhanh

| Đặc điểm | `Runnable` | `Callable<V>` |
| --- | --- | --- |
| Functional method | `void run()` | `V call() throws Exception` |
| Giá trị trả về | Không có | Có kiểu `V`, ví dụ `String`, `Integer` |
| Cho phép checked exception thoát ra trực tiếp? | Không, do chữ ký `run()` | Có, trong giới hạn `throws Exception` |
| Có thể ném unchecked exception? | Có | Có |
| Dùng trực tiếp với `new Thread(...)`? | Có | Không; cần chuyển thành `Runnable` hoặc dùng cơ chế khác |
| Gửi đến `ExecutorService.submit`? | Có | Có |
| `submit` trả về | `Future<?>`; `get()` thành công trả `null` | `Future<V>`; `get()` thành công trả giá trị `V` |
| Lỗi của task khi truy qua `Future.get()` | Thường nằm trong `ExecutionException.getCause()` | Cũng nằm trong `ExecutionException.getCause()` |

Bảng về `Future` áp dụng cho các tác vụ gửi bằng overload `submit(Runnable)` và `submit(Callable<T>)`, **không áp dụng nguyên xi cho việc gọi `run()` hay `call()` trực tiếp**.

## 2. Tại sao lambda còn phải quan tâm tới throws?

Một biểu thức lambda trong Java không có kiểu “tự tồn tại” độc lập. Nó được **target-typed** theo functional interface ở vị trí được sử dụng. Chính chữ ký của **functional method** quyết định kiểu tham số, kiểu kết quả và các checked exception mà body của lambda được phép để thoát ra.

Ví dụ:

~~~java
import java.io.IOException;
import java.util.concurrent.Callable;

class Illustrations {
    static String read() throws IOException {
        throw new IOException("unavailable");
    }

    static void example() {
        Callable<String> task = () -> read(); // Hợp lệ.
    }
}
~~~

Đây là một class minh họa hợp lệ. Lambda có target type `Callable<String>`, nên body gọi `read()` có thể ném `IOException`; `call()` cho phép `Exception`, là lớp cha của `IOException`.

Ngược lại:

~~~java
// CỐ Ý KHÔNG BIÊN DỊCH; đặt trong ngữ cảnh có read() throws IOException:
Runnable task = () -> {
    read(); // Unhandled exception type IOException.
};
~~~

Target type lúc này là `Runnable`. Phương thức chức năng `run()` **không có checked throws**, nên body lambda không được để `IOException` thoát ra. Việc `Runnable` có thể chạy trên thread riêng **không làm compiler miễn trừ quy tắc**.

Đây không phải hạn chế riêng của lambda. Một class implements Runnable cũng chịu quy tắc override tương tự:

~~~java
// CỐ Ý KHÔNG BIÊN DỊCH:
class BadTask implements Runnable {
    @Override
    public void run() throws java.io.IOException {
        throw new java.io.IOException("failed");
    }
}
~~~

Lý do: override không được thêm checked exception mới vượt quá hợp đồng `Runnable.run()`.

**Kết luận:** quyết định có thể để checked exception thoát ra phải dựa vào **kiểu phương thức được triển khai**, không phải lambda có dấu ngoặc, không phải nó có chạy bất đồng bộ hay không.

## 3. Phân biệt checked với unchecked khi đọc API

Theo [JLS Java 17 chương 11](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html), checked exception phải được bắt hoặc khai báo thích hợp; unchecked exception không phải chịu nghĩa vụ khai báo tại compile time.

~~~text
Throwable
├── Error                         → unchecked
└── Exception
    ├── IOException               → checked
    ├── InterruptedException      → checked
    ├── ExecutionException        → checked
    └── RuntimeException          → unchecked
        ├── IllegalStateException
        ├── IllegalArgumentException
        └── UncheckedIOException
~~~

Đây là **cây rút gọn** chỉ những lớp liên quan; không phải toàn bộ hierarchy.

Ví dụ phương thức:

~~~java
static String load() throws java.io.IOException {
    throw new java.io.IOException("I/O failed");
}
~~~

Trong lambda `Callable<String>`, có thể gọi `load()` và để IOException thoát ra. Trong lambda `Runnable`, cần bắt, xử lý hoặc chuyển đổi nó sang unchecked exception **có chủ đích**.

Một `RuntimeException` có thể xuất hiện từ cả hai loại lambda dù không có mệnh đề `throws` tường minh. Không nên gán nhãn “Runnable không thể throw” chỉ vì nhìn thấy `void run()`.

## 4. Lựa chọn Runnable hay Callable theo hợp đồng tác vụ

Nên chọn dựa trên **nhu cầu của caller**, không chỉ dựa trên việc viết được lambda ngắn hơn.

### Chọn Runnable khi

- Tác vụ **không cần trả kết quả** qua functional method.
- Các checked exception phát sinh bên trong có thể được **xử lý tại chỗ**, hoặc có chính sách chuyển sang unchecked phù hợp.
- Chỉ cần gọi trực tiếp `run()`, dùng `Thread`, `Executor.execute` hoặc `ExecutorService.submit(Runnable)`.

Ví dụ:

~~~java
Runnable announce = () -> System.out.println("Task completed");
announce.run();
~~~

Output:

~~~text
Task completed
~~~

Ví dụ này **chạy đồng bộ** trong thread gọi khi sử dụng `announce.run()`. Bản thân đối tượng Runnable không tự tạo thread mới. Muốn lập lịch hoặc chạy thread riêng phải dùng một cơ chế như `new Thread(announce).start()` hoặc executor.

### Chọn Callable khi

- Tác vụ cần **trả về một giá trị** có kiểu.
- Body có thể để **checked exception** thoát ra theo hợp đồng `call() throws Exception`.
- Caller có kế hoạch nhận kết quả bằng `Future<V>`, hoặc gọi `call()` trực tiếp trong ngữ cảnh xử lý `Exception`.

Ví dụ:

~~~java
Callable<Integer> count = () -> 42;
~~~

Đoạn minh họa cần import `java.util.concurrent.Callable`. Kiểu generic `Integer` được dùng làm giá trị trả về của `call()`. Compiler không yêu cầu lambda có một checked exception cụ thể; nó chỉ **cho phép** kiểu phù hợp nếu có.

### Callable<Void> có phải tương đương hoàn toàn với Runnable?

Không. Khi không cần kết quả nhưng muốn giữ hợp đồng checked exception, có thể dùng:

~~~java
Callable<Void> task = () -> {
    performWork();
    return null;
};
~~~

Đây là đoạn minh họa, cần có `performWork()` phù hợp và import Callable. Nó trả về `null` vì kiểu `Void` không đại diện một giá trị kết quả thông thường. Tuy nhiên **chữ ký của functional method vẫn là `call() throws Exception`**, khác `Runnable.run()`. Do đó hai interface không hoàn toàn tương đương chỉ vì kết quả có thể là null.

## 5. Ví dụ Java 17 hoàn chỉnh: năm tình huống phân biệt

Tạo file **`RunnableCallableExceptionDemo.java`**. Chương trình minh họa cả Runnable, Callable, checked exception, cách bọc IOException thành unchecked và trường hợp `Callable<Void>`.

Dùng một `ExecutorService` có **một worker thread** và chờ từng Future trước khi chuyển sang bước tiếp theo, để output dễ kiểm chứng, không phụ thuộc thứ tự tranh chấp giữa nhiều task.

~~~java
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class RunnableCallableExceptionDemo {
    private static String read(String key) throws IOException {
        if ("fail".equals(key)) {
            throw new IOException("file unavailable");
        }
        return "DATA:" + key;
    }

    public static void main(String[] args)
            throws InterruptedException, ExecutionException {
        Runnable plain = () -> System.out.println("runnable=done");

        Callable<String> successful = () -> read("ok");
        Callable<String> checkedFailure = () -> read("fail");

        Runnable adapted = () -> {
            try {
                read("fail");
            } catch (IOException ex) {
                throw new UncheckedIOException("runnable I/O failure", ex);
            }
        };

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            System.out.println("== successful jobs ==");
            Future<?> plainFuture = executor.submit(plain);
            System.out.println("runnable-result=" + plainFuture.get());

            Future<String> callFuture = executor.submit(successful);
            System.out.println("callable-result=" + callFuture.get());

            System.out.println("== checked exception from Callable ==");
            Future<String> checkedFuture = executor.submit(checkedFailure);
            try {
                checkedFuture.get();
            } catch (ExecutionException ex) {
                System.out.println("wrapper=" + ex.getClass().getSimpleName());
                Throwable cause = ex.getCause();
                System.out.println("cause=" + cause.getClass().getSimpleName()
                        + ": " + cause.getMessage());
            }

            System.out.println("== checked exception adapted to Runnable ==");
            Future<?> adaptedFuture = executor.submit(adapted);
            try {
                adaptedFuture.get();
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                System.out.println("cause=" + cause.getClass().getSimpleName()
                        + ": " + cause.getMessage());
                System.out.println("root="
                        + cause.getCause().getClass().getSimpleName()
                        + ": " + cause.getCause().getMessage());
            }

            System.out.println("== Callable<Void> ==");
            Callable<Void> noValue = () -> {
                System.out.println("callable-void=done");
                return null;
            };
            System.out.println("void-result=" + executor.submit(noValue).get());
        } finally {
            executor.shutdown();
        }
    }
}
~~~

### Biên dịch và chạy

Sử dụng JDK 17 trở lên:

~~~bash
javac --release 17 -encoding UTF-8 RunnableCallableExceptionDemo.java
java RunnableCallableExceptionDemo
~~~

**Stdout chính xác, đã chạy và đối chiếu:**

~~~text
== successful jobs ==
runnable=done
runnable-result=null
callable-result=DATA:ok
== checked exception from Callable ==
wrapper=ExecutionException
cause=IOException: file unavailable
== checked exception adapted to Runnable ==
cause=UncheckedIOException: runnable I/O failure
root=IOException: file unavailable
== Callable<Void> ==
callable-void=done
void-result=null
~~~

Ví dụ **đã được biên dịch bằng `javac --release 17` và thực thi thành công**. Nó không cần file thực hoặc mạng; method `read()` mô phỏng thành công/thất bại với message cố định. Để dễ theo dõi, các tác vụ được gửi và chờ kết quả theo từng bước; output không dựa trên tên thread hoặc thứ tự scheduling ngẫu nhiên.

**Chú ý môi trường Java 17:** `ExecutorService` trong ví dụ được đóng lịch làm việc bằng `shutdown()` trong `finally`. Đừng tự dùng `try (ExecutorService ...)` nếu đang yêu cầu mã biên dịch theo Java 17; việc hỗ trợ `AutoCloseable` trên interface này ở các phiên bản Java mới hơn không thay đổi tương thích nguồn của Java 17.

## 6. Phân tích stdout: thành công với Runnable và Callable

### 6.1. Runnable không trả giá trị, nhưng Future.get() vẫn tồn tại

Phần:

~~~java
Runnable plain = () -> System.out.println("runnable=done");
Future<?> plainFuture = executor.submit(plain);
System.out.println("runnable-result=" + plainFuture.get());
~~~

Có hai tầng hành vi khác nhau:

- `Runnable.run()` bản thân có kiểu trả về `void`, không tạo một đối tượng kết quả.
- `ExecutorService.submit(Runnable)` **trả về `Future<?>` để theo dõi trạng thái hoàn thành**; nếu tác vụ hoàn thành bình thường, `get()` trả `null`.

Khi chạy, task in `runnable=done` trước. Sau khi `plainFuture.get()` trả về, caller in `runnable-result=null`.

**Không nên nhầm:** không phải lambda Runnable “trả về null”; giá trị `null` là **kết quả do overload submit(Runnable) quy định cho Future**, còn functional method vẫn là `void run()`.

### 6.2. Callable<String> trả String qua Future<String>

Phần:

~~~java
Callable<String> successful = () -> read("ok");
Future<String> callFuture = executor.submit(successful);
System.out.println("callable-result=" + callFuture.get());
~~~

Ở thời điểm thực thi, `read("ok")` trả `"DATA:ok"`. Vì callable thành công, `Future.get()` trả chính chuỗi này. Lúc biên dịch, generic `String` giúp caller biết kết quả có kiểu chuỗi, không phải `Object` phải ép kiểu thủ công.

Tham số kiểu `V` của `Callable<V>` mô tả **kiểu giá trị trả về**; nó **không phải** tham số generic mô tả kiểu exception. Chữ ký của `call()` vẫn khai báo `throws Exception` bất kể V là `String`, `Integer` hay `Void`.

## 7. Callable ném IOException: Future.get() truyền lỗi thế nào?

Phần:

~~~java
Callable<String> checkedFailure = () -> read("fail");
Future<String> checkedFuture = executor.submit(checkedFailure);
try {
    checkedFuture.get();
} catch (ExecutionException ex) {
    Throwable cause = ex.getCause();
    System.out.println(cause.getClass().getSimpleName());
}
~~~

Phân tích từng bước:

1. Body lambda có target type `Callable<String>`, nên lệnh gọi `read("fail")` được phép để `IOException` thoát ra khỏi `call()`.
2. Executor chạy callable trong worker thread.
3. `read("fail")` ném `IOException("file unavailable")`.
4. Tác vụ hoàn thành **thất bại**. Khi caller gọi `Future.get()`, API ném `ExecutionException`, với **cause là Throwable gốc** từ tác vụ.
5. Caller đọc `ex.getCause()` để biết nguyên nhân chính xác; trong chương trình là `IOException`.

Stdout có:

~~~text
wrapper=ExecutionException
cause=IOException: file unavailable
~~~

**Đây không phải lỗi Java tự biến `IOException` thành `ExecutionException` lúc lời gọi `call()` được khai báo.** Việc bọc xảy ra ở **ranh giới lấy kết quả thông qua Future**. Nếu bạn gọi trực tiếp `checkedFailure.call()` trong cùng thread, ngoại lệ gốc có thể được ném thẳng ra caller của `call()` và phải xử lý theo chữ ký `throws Exception`; không có quy tắc tự động bọc qua `ExecutionException` chỉ vì đối tượng là Callable.

### Caller cần bắt những checked exception nào tại get()?

Chữ ký quan trọng của `Future.get()`:

~~~java
V get() throws InterruptedException, ExecutionException;
~~~

Đây là chữ ký rút gọn theo kiểu V của Future, không phải file độc lập.

- `InterruptedException`: **thread đang chờ get** bị interrupt trong khi chờ. Không nhất thiết là lỗi từ body callable.
- `ExecutionException`: **task thất bại**; xem `getCause()` để biết Throwable do task ném.
- `CancellationException`: unchecked, có thể xảy ra nếu Future đã bị hủy.
- `get()` có thể chặn đến khi task hoàn thành; overload có timeout còn có thể ném `TimeoutException`.

Đừng viết `catch (IOException)` trực tiếp quanh `future.get()` với kỳ vọng nó nhận IOException từ callable. Bên ngoài Future, API chuyển thất bại của task qua `ExecutionException`.

## 8. Runnable gặp IOException: phải xử lý tại lambda

Ví dụ chương trình chính:

~~~java
Runnable adapted = () -> {
    try {
        read("fail");
    } catch (IOException ex) {
        throw new UncheckedIOException("runnable I/O failure", ex);
    }
};
~~~

Tại sao phải bắt `IOException`? Vì `Runnable.run()` không cho checked `IOException` thoát ra. Tại sao có thể `throw new UncheckedIOException(...)`? Vì `UncheckedIOException extends RuntimeException`, thuộc nhóm unchecked.

Việc chọn wrapper cần rõ ràng:

- Nó **không hề làm thao tác đọc thành công**; lỗi vẫn lan ra.
- Nó thay đổi **hợp đồng truyền lỗi từ checked sang unchecked**.
- Constructor nhận cause giúp giữ `IOException` ban đầu cho chẩn đoán.
- Nếu một hệ thống có khả năng phục hồi lỗi I/O ngay bên trong task, có thể **xử lý tại chỗ** thay vì wrap.
- Không nên “bắt IOException rồi bỏ qua” chỉ để Runnable biên dịch; làm như vậy có thể gây thành công giả.

Khi gửi Runnable lỗi bằng `executor.submit(adapted)`, worker thread ném `UncheckedIOException`. Khi caller gọi `Future.get()`, nó nhận `ExecutionException` với cause là `UncheckedIOException` và **cause của UncheckedIOException** là `IOException` gốc.

~~~text
ExecutionException
  └── UncheckedIOException("runnable I/O failure")
        └── IOException("file unavailable")
~~~

Đó là lý do stdout phân biệt hai dòng:

~~~text
cause=UncheckedIOException: runnable I/O failure
root=IOException: file unavailable
~~~

### Tại sao không ném trực tiếp IOException từ Runnable bằng casts?

Không nên sử dụng “sneaky throw”, generic casting đánh lừa compiler, reflection hay các thủ thuật bypass checked exception chỉ để truyền IOException ra ngoài `Runnable.run()`. Chúng làm chữ ký công khai không phản ánh kỳ vọng của caller và khiến code khó hiểu/khó bảo trì.

Khi có một tác vụ thực sự cần ném checked exception, **sử dụng Callable hoặc functional interface tự định nghĩa với throws phù hợp** là lựa chọn rõ ràng hơn. Nếu framework bắt buộc Runnable, hãy chọn policy xử lý hoặc wrapper unchecked có nguyên nhân rõ.

## 9. Callable<Void> khi cần throws nhưng không cần dữ liệu trả về

Chữ ký Callable luôn trả kiểu V. Nếu chỉ cần khai báo nghĩa vụ checked exception mà không có kết quả hữu ích, dùng `Callable<Void>`:

~~~java
Callable<Void> task = () -> {
    saveDocument();
    return null;
};
~~~

Đây là **đoạn minh họa**; cần import Callable và định nghĩa `saveDocument()`. Lambda phải có nhánh `return null;` cho đường hoàn thành bình thường. Vì target type là `Callable<Void>`, phương thức `saveDocument()` có thể ném checked exception phù hợp.

Trong chương trình runnable, task được khai báo:

~~~java
Callable<Void> noValue = () -> {
    System.out.println("callable-void=done");
    return null;
};
System.out.println("void-result=" + executor.submit(noValue).get());
~~~

Khi hoàn thành bình thường, Future trả `null`. So với Runnable:

- Hai loại đều có thể tạo Future với kết quả null.
- Nhưng **chữ ký chức năng** khác nhau: `Callable<Void>.call() throws Exception` so với `Runnable.run()` không checked throws.
- Callback API yêu cầu Runnable **không tự động nhận Callable<Void>**. Cần adapter hoặc đổi overload phù hợp.

## 10. Khác biệt giữa gọi trực tiếp và gửi qua executor

Cần phân biệt **ba ngữ cảnh**:

| Cách thực thi | Chạy ở đâu | Nhận kết quả | Ngoại lệ |
| --- | --- | --- | --- |
| `runnable.run()` | Thread hiện tại | Không có | Unchecked lan trực tiếp nếu không bắt |
| `callable.call()` | Thread hiện tại | Giá trị V trực tiếp | Có thể lan checked `Exception` theo chữ ký |
| `executor.submit(runnable)` | Thread do executor quản lý | `Future<?>` trả null khi thành công | Thất bại phản ánh qua `ExecutionException` khi `get()` |
| `executor.submit(callable)` | Thread do executor quản lý | `Future<V>` | Thất bại phản ánh qua `ExecutionException` khi `get()` |
| `executor.execute(runnable)` | Theo executor | Không có Future | Xử lý unchecked tùy implementation/uncaught handling; không có `Future.get()` để quan sát |
| `new Thread(runnable).start()` | Thread mới | Không có Future | Uncaught exception của thread theo cơ chế handler của Thread/JVM |

Điểm dễ nhầm: **`executor.execute(runnable)` và `executor.submit(runnable)` không cung cấp cùng cơ chế quan sát exception.** Với `submit`, executor trả Future; lỗi task được truy xuất khi gọi `get()`. Với `execute`, caller không nhận Future từ API đó, nên không thể dựa trên `get()` để biết tác vụ thất bại.

Ngoài ra, `new Thread(callable)` không phải overload chuẩn nhận Callable. Muốn chạy Callable bằng Thread trực tiếp, có thể dùng `FutureTask<V>` (vừa Runnable vừa Future) hoặc đơn giản hơn là sử dụng ExecutorService nếu mục tiêu là nộp task và nhận kết quả.

### Thread và runnable có khác biệt về checked exception không?

Không: `Runnable.run()` **vẫn là cùng một chữ ký** dù chạy trực tiếp, trên Thread hay trong executor. Lập lịch trên thread khác không cho phép lambda Runnable tự nhiên ném checked IOException mà không xử lý.

### Callable không đồng nghĩa bất đồng bộ

`callable.call()` là lời gọi bình thường, chạy ở thread hiện tại nếu được gọi trực tiếp. Chỉ khi gửi cho executor hoặc cơ chế khác mới có thực thi do framework/lịch trình kiểm soát. Tương tự `runnable.run()` chỉ là gọi method bình thường.

## 11. Generics đóng vai trò gì trong Callable<V>?

Xét:

~~~java
Callable<String> textTask = () -> "hello";
Callable<Integer> numberTask = () -> 123;
Callable<Void> actionTask = () -> {
    System.out.println("done");
    return null;
};
~~~

Đây là **đoạn minh họa**, cần import Callable. Kết quả của `call()` lần lượt là String, Integer, Void; Java compiler kiểm tra kiểu return trong lambda theo target type.

Tuy nhiên, mọi bản thể hóa vẫn dùng functional method có khai báo `throws Exception`. Không có tham số generic kiểu `E extends Exception` được Callable chuẩn dùng để chỉ loại checked exception cụ thể.

Nếu cần contract hẹp hơn, bạn có thể tự định nghĩa:

~~~java
@FunctionalInterface
interface IoTask<T> {
    T execute() throws java.io.IOException;
}
~~~

Đây là định nghĩa interface hợp lệ, có thể đặt trong class hoặc file riêng. Lambda gán cho `IoTask<String>` được phép ném IOException, nhưng **không được tùy tiện ném SQLException checked** vì hợp đồng chỉ cho IOException.

Chú ý rằng `IoTask<T>` **không phải Callable<T>** chỉ vì có chữ ký gần giống. Muốn dùng API nhận Callable, cần chuyển đổi:

~~~java
IoTask<String> io = () -> readData();
Callable<String> callable = io::execute;
~~~

Đoạn minh họa này cần `readData()` phù hợp và import Callable. Method reference `io::execute` có checked IOException nhưng Callable cho phép Exception nên hợp lệ.

**Nguyên tắc chung:** Checked-exception compatibility được compiler phân tích tại **functional target** của lambda hoặc method reference; generics xác định kiểu dữ liệu, không tự “xóa” giới hạn `throws`.

## 12. Adapter từ functional interface cho phép checked exception sang Runnable

Nếu một API thực sự chỉ chấp nhận `Runnable`, có thể viết adapter tập trung thay vì lặp `try/catch` trong từng lambda.

~~~java
import java.io.IOException;
import java.io.UncheckedIOException;

@FunctionalInterface
interface IoAction {
    void run() throws IOException;
}

class Adapters {
    static Runnable asRunnable(IoAction action) {
        return () -> {
            try {
                action.run();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        };
    }
}
~~~

Đây là **đoạn Java hợp lệ để minh họa**, chưa có main. Cách sử dụng ở nơi có phương thức ném IOException:

~~~java
Runnable task = Adapters.asRunnable(() -> readInput());
~~~

Tên `readInput()` cần được định nghĩa. Adapter chỉ thay đổi cách exception được trình bày qua `Runnable.run()`, **không biến một IOException thành kết quả thành công**.

Rủi ro cần cân nhắc:

- Caller của Runnable có thể không biết về wrapper nếu API không được tài liệu hóa.
- Nếu lỗi có thể xử lý tại nơi tạo task, bắt và phục hồi có thể hợp lý hơn bọc rồi lan.
- Với `InterruptedException` không nên wrap mù mà bỏ qua trạng thái interrupt; xem phần 14.
- Nếu nghiệp vụ cần dữ liệu trả về hoặc muốn giữ checked exception dưới dạng gốc, Callable thường đơn giản hơn adapter.

## 13. Cẩn thận khi dùng lambda trong overload nhận Runnable/Callable

Một API có thể có các overload như:

~~~java
Future<?> submit(Runnable task);
<T> Future<T> submit(Callable<T> task);
~~~

Đây là **hai chữ ký API rút gọn** của ExecutorService. Java dùng target typing và quy tắc overload resolution để quyết định lambda ứng với functional interface nào.

Ví dụ có kiểu khai báo tường minh:

~~~java
Runnable r = () -> process();
Callable<String> c = () -> load();
~~~

Hai lời gọi `process()` và `load()` là tên tượng trưng. Với target được chỉ rõ, compiler biết `r` là Runnable (không cho checked throws) và `c` là Callable<String> (cho Exception).

Nếu đưa lambda trực tiếp vào một phương thức overloaded và compiler báo **ambiguous** hoặc lỗi tương thích throws/return type, đừng cố giải bằng cách “bỏ checked exception” từ body. Hãy đặt biến có kiểu `Runnable` / `Callable<T>` hoặc cast lambda về target phù hợp:

~~~java
executor.submit((Callable<String>) () -> load());
~~~

Đoạn này minh họa; `executor` và `load()` cần khai báo. Việc chọn target type cũng chọn luôn **hợp đồng checked exception** mà body có thể để thoát ra.

**Không dùng việc ép target type chỉ để lừa caller**: kiểm tra kỹ liệu tác vụ phải có kết quả, có checked exception và cách xử lý failure sau khi chạy.

## 14. InterruptedException khi đợi Future không phải IOException từ task

`Future.get()` có thể ném `InterruptedException` khi **thread đang chờ kết quả** bị interrupt. Đây là checked exception riêng với exception do callable ném.

Một cách xử lý phù hợp tại nơi không thể truyền tiếp `InterruptedException`:

~~~java
try {
    String value = future.get();
    use(value);
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    // Dừng hoặc báo hủy phù hợp với luồng điều khiển ứng dụng.
    return;
} catch (ExecutionException ex) {
    handleTaskFailure(ex.getCause());
}
~~~

Đây là **đoạn minh họa**, đặt trong method có kiểu return phù hợp với `return;` và có định nghĩa `future/use/handleTaskFailure`, import tương ứng.

**Vì sao gọi `Thread.currentThread().interrupt()`?** Khi thao tác chờ phát sinh InterruptedException, cờ interrupt thường bị xóa khi exception được ném. Nếu method không thể khai báo/lan truyền InterruptedException, việc phục hồi cờ cho tầng trên biết yêu cầu ngắt là một chính sách phổ biến. Sau đó nên **dừng công việc đang làm**, tránh tiếp tục xử lý như thể không có gì xảy ra.

Nếu method có thể khai báo `throws InterruptedException` và trách nhiệm được giao cho caller, **truyền tiếp checked exception** thường rõ ràng hơn việc bắt rồi bỏ qua.

Không nên viết:

~~~java
// MẪU XẤU: nuốt yêu cầu interrupt.
try {
    future.get();
} catch (InterruptedException ex) {
    // Bỏ qua, tiếp tục bình thường.
}
~~~

Đây là đoạn minh họa anti-pattern, chưa xử lý ExecutionException nên không phải chương trình độc lập. Ý chính là **không âm thầm bỏ qua interrupt** trong môi trường concurrency.

## 15. Timeout và hủy task không làm thay đổi throws của Runnable/Callable

`Future.get(timeout, unit)` còn có thể ném `TimeoutException` nếu hết thời gian chờ mà task chưa hoàn tất. **Timeout khi chờ** không tự đồng nghĩa task đã bị dừng.

Nếu ứng dụng quyết định hủy:

~~~java
future.cancel(true);
~~~

thì đây là một **yêu cầu hủy**, có thể interrupt thread đang chạy nếu task cho phép và backend hỗ trợ. Không có bảo đảm rằng mọi thao tác I/O/CPU sẽ dừng lập tức. Task cần hợp tác với interruption/cancellation khi thiết kế.

Các ngoại lệ tại boundary này cần phân biệt:

- `ExecutionException`: task đã hoàn thành thất bại, xem cause.
- `InterruptedException`: thread chờ bị interrupt.
- `TimeoutException`: chưa lấy được kết quả trong khoảng thời gian quy định.
- `CancellationException`: Future bị hủy.
- Unchecked exception từ chính task: nhìn từ `Future.get()` của task submit, thường được bọc trong ExecutionException, không tự xuất hiện trực tiếp như lời gọi method.

Đừng suy rằng khai báo `Callable.call() throws Exception` là nguyên nhân khiến `Future.get()` có thể ném InterruptedException hoặc TimeoutException. Chúng đến từ **cơ chế chờ và quản lý task**, không phải yêu cầu riêng của business method.

## 16. Lập lịch, shutdown và quan sát ngoại lệ

Một ví dụ task có thể chạy đúng nhưng chương trình vẫn quản lý executor sai nếu quên shutdown. Với Java 17:

~~~java
ExecutorService executor = Executors.newSingleThreadExecutor();
try {
    Future<String> result = executor.submit((Callable<String>) () -> "done");
    System.out.println(result.get());
} finally {
    executor.shutdown();
}
~~~

Đây là **đoạn minh họa** trong method có xử lý/khai báo `InterruptedException` và `ExecutionException`, import các API concurrency. `shutdown()` báo executor ngừng nhận công việc mới và tiếp tục xử lý các task đã được nhận theo chính sách của executor; **nó không bắt buộc chờ mọi task hoàn thành**. Nếu ứng dụng cần đợi tài nguyên thực sự dừng, phải có chính sách `awaitTermination(...)`, timeout và xử lý interrupt thích hợp.

Trong chương trình chính, mọi Future đã được `get()` xử lý trước `shutdown()`, nên các tác vụ ví dụ đã hoàn tất và output rõ ràng.

Với ứng dụng dài hạn, cân nhắc thêm monitoring, bounded queue, chính sách từ chối task, cancellation, timeout và cách log Throwable. Các yêu cầu đó là **thiết kế concurrency cấp ứng dụng**, không thay đổi chữ ký `Runnable.run()` hoặc `Callable.call()`.

## 17. Những hiểu nhầm phổ biến

1. **“Runnable không bao giờ ném exception.”** Sai: có thể ném unchecked exception; không được tự để checked exception mới thoát ra.
2. **“Callable bắt buộc phải ném checked exception.”** Sai: `throws Exception` là khả năng, không phải nghĩa vụ luôn ném.
3. **“Chỉ có task chạy trên thread mới chịu quy tắc checked exception.”** Sai: compiler kiểm tra lambda/override bất kể chạy trực tiếp hay bất đồng bộ.
4. **“Gán cùng lambda cho Runnable hoặc Callable không liên quan tới throws.”** Sai: target type quyết định hợp đồng checked throws.
5. **“Runnable.run trả null.”** Sai: `run()` là void; `Future.get()` của `submit(Runnable)` thành công trả null.
6. **“Callable<Void> và Runnable là một interface.”** Sai: functional method và checked throws khác nhau.
7. **“Callable<String> nghĩa là chỉ ném StringException.”** Sai: generic String là **kiểu kết quả**, không phải kiểu exception.
8. **“IOException từ callable sẽ bị bắt trực tiếp bởi catch IOException quanh Future.get().”** Sai: Future.get báo task failure bằng `ExecutionException`; xem `getCause()`.
9. **“Nếu Future báo ExecutionException thì chính get() gây IOException trong worker.”** Sai: lỗi gốc phát sinh ở task, còn Future báo lỗi tại boundary chờ kết quả.
10. **“Bọc IOException thành UncheckedIOException làm biến mất nguyên nhân.”** Sai nếu constructor được truyền cause.
11. **“Bọc checked thành unchecked luôn là cách hay.”** Sai: phải xét chính sách caller và không nuốt lỗi.
12. **“Executor.execute và submit giống nhau về cách lấy exception.”** Sai: execute không trả Future.
13. **“new Thread(callable) là overload nhận Callable chuẩn.”** Sai: Thread nhận Runnable trong cách dùng này; cần adapter hoặc FutureTask.
14. **“InterruptedException từ get là exception từ nội dung Callable.”** Không nhất thiết; nó phản ánh thread chờ bị ngắt.
15. **“Bắt InterruptedException rồi bỏ qua là vô hại.”** Sai: có thể làm hỏng chính sách dừng/hủy tác vụ.
16. **“Future.get có timeout nghĩa là task đã tự bị hủy khi hết thời gian.”** Sai: timeout ở thao tác chờ không tự dừng task.
17. **“Runnable không cần shutdown executor.”** Sai: việc quản lý executor độc lập với loại task.
18. **“Cứ catch Exception trong Runnable là đủ.”** Bắt rộng có thể che bug; nên bắt đúng checked exception và có chính sách lỗi rõ.
19. **“Một exception xảy ra trong thread khác sẽ tự ném ở thread gọi submit.”** Sai: cần quan sát qua Future, handler hoặc cơ chế phù hợp.
20. **“Đổi target type sau này không ảnh hưởng source compatibility.”** Có thể ảnh hưởng, vì kiểu return và checked throws của functional method khác nhau.

## 18. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao `Runnable r = () -> Files.readString(path);` có thể gây lỗi compile-time?

*Đáp án:* `Files.readString` có thể ném checked IOException. Khi target type là Runnable, `run()` không khai báo checked exception nên phải xử lý lỗi tại lambda hoặc dùng target phù hợp.

**Câu 2.** Nếu đổi target thành `Callable<String>` thì lời gọi `Files.readString(path)` có thể hợp lệ không?

*Đáp án:* Có, vì functional method `call() throws Exception` cho phép IOException thoát ra, miễn các chi tiết kiểu kết quả và context khác hợp lệ.

**Câu 3.** Gọi `r.run()` có tự tạo thread mới không?

*Đáp án:* Không. Đây là method call bình thường trên thread đang gọi.

**Câu 4.** Kết quả `Future.get()` sau khi `submit(Runnable)` chạy bình thường là gì?

*Đáp án:* Null, với overload không chỉ định kết quả trả về của Runnable.

**Câu 5.** Nếu một callable ném IOException khi chạy trong executor, `Future.get()` ném gì?

*Đáp án:* `ExecutionException`, trong đó `getCause()` chứa IOException gốc.

**Câu 6.** Nếu một Runnable bắt IOException rồi ném UncheckedIOException, `Future.get()` của submit(Runnable) cho cause gì?

*Đáp án:* Cause trực tiếp của ExecutionException là UncheckedIOException; cause của UncheckedIOException là IOException đã được bọc.

**Câu 7.** Tại sao `Callable<Void>` vẫn cần `return null;` trên đường hoàn thành bình thường?

*Đáp án:* Functional method `call()` có kiểu trả về V; khi V là Void, lambda vẫn phải trả một giá trị tương thích (thường null) nếu có đường hoàn thành bình thường.

**Câu 8.** Một task hoàn thành thất bại có làm mọi lệnh `submit` trước đó ném trực tiếp lỗi gốc không?

*Đáp án:* Không. Nộp task và nhận kết quả là hai bước; phải quan sát Future/handler theo API.

**Câu 9.** InterruptedException từ `get()` nên được xử lý thế nào?

*Đáp án:* Lan truyền nếu contract cho phép, hoặc khôi phục cờ interrupt và dừng/thoát theo chính sách; không im lặng nuốt yêu cầu ngắt.

**Câu 10.** Một generic Callable<V> có tham số generic kiểu exception không?

*Đáp án:* Không. V mô tả kiểu kết quả; chữ ký checked throws là Exception không phụ thuộc V.

**Câu 11.** Khi API chỉ nhận Runnable nhưng việc xử lý cần ném IOException, có những lựa chọn nào?

*Đáp án:* Bắt và phục hồi hợp lệ, hoặc bọc vào một unchecked exception có cause rõ; nếu thay đổi được API, Callable hoặc custom throwing functional interface có thể tự nhiên hơn.

**Câu 12.** `executor.shutdown()` có tự đợi tất cả task kết thúc trước khi trả về không?

*Đáp án:* Không có bảo đảm chờ như vậy; để chờ cần thêm cơ chế thích hợp như `awaitTermination()` hoặc đợi Future, kèm chính sách timeout/interrupt.

## 19. Tài liệu chính thức Java 17

- [API Runnable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Runnable.html): chữ ký `run()`, contract nhiệm vụ không có giá trị trả về.
- [API Callable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Callable.html): chữ ký `V call() throws Exception`.
- [API ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): các overload `submit`, quản lý vòng đời executor.
- [API Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): `get()`, `cancel()`, timeout và ngoại lệ khi chờ.
- [API ExecutionException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutionException.html): exception từ task được báo qua Future.
- [API UncheckedIOException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/UncheckedIOException.html): wrapper unchecked dành cho IOException.
- [API Executors](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Executors.html): các utility factory và adapter giữa Runnable/Callable.
- [JLS Java 17 mục 15.27.3 – Type of a Lambda Expression](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.27.3): tương thích lambda với function type, bao gồm checked exception.
- [JLS Java 17 mục 11.2 – Compile-Time Checking of Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.2): nguyên tắc phân tích checked exception.

**Ghi nhớ cuối:** `Runnable.run()` **không có checked throws**, nhưng vẫn có thể ném unchecked; `Callable<V>.call() throws Exception` có thể **truyền checked exception** và trả kết quả kiểu V. Lambda chịu hợp đồng của functional interface mục tiêu. Khi chạy qua `ExecutorService.submit`, lỗi của cả hai loại task được quan sát qua **`Future.get() → ExecutionException → getCause()`**. Chọn Runnable hay Callable dựa vào **kết quả, khả năng ném checked exception và chính sách xử lý của caller**, không chỉ vì tên interface.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lambda-generic-exceptions-004">Vì sao Function, Consumer và Supplier thông thường gây vướng với checked exception</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao `Function`, `Consumer` và `Supplier` thông thường gây vướng với checked exception trong Java?

> **Chủ đề:** Java Core → Exception → Lambda và generic. Ba functional interface quen thuộc trong `java.util.function` — `Function<T,R>`, `Consumer<T>` và `Supplier<T>` — được thiết kế cho callback thông dụng nhưng **phương thức trừu tượng của chúng không khai báo `throws` cho checked exception**. Vì vậy một lambda hay method reference gán cho các kiểu này không được để `IOException`, `SQLException` hay checked exception khác thoát trực tiếp. Vướng mắc đến từ **hợp đồng phương thức đích của lambda**, chứ không phải vì lambda “không thể dùng checked exception”.

## 1. Nắm đúng ba chữ ký

Đọc ba phương thức trừu tượng (các interface thật còn có những method hỗ trợ khác):

```java
@FunctionalInterface
interface Function<T, R> {
    R apply(T value);
}

@FunctionalInterface
interface Consumer<T> {
    void accept(T value);
}

@FunctionalInterface
interface Supplier<T> {
    T get();
}
```

Đây là bản **rút gọn để giải thích chữ ký**, không phải bản sao toàn bộ mã nguồn JDK; khi viết code thật dùng `java.util.function.Function`, `Consumer`, `Supplier`. Tên tham số không quan trọng, nhưng sự vắng mặt của `throws IOException` hoặc `throws Exception` rất quan trọng.

| Interface chuẩn | Phương thức đích | Công việc thường dùng | Cho checked exception thoát trực tiếp? |
| --- | --- | --- | --- |
| `Function<T,R>` | `R apply(T t)` | Chuyển đổi từ T sang R, `stream.map(...)` | Không |
| `Consumer<T>` | `void accept(T t)` | Thực hiện side effect, `forEach(...)` | Không |
| `Supplier<T>` | `T get()` | Tạo/cung cấp giá trị khi cần, `orElseGet(...)` | Không |

**Lưu ý:** “không khai báo checked exception” không có nghĩa callback không thể ném **unchecked `RuntimeException` hoặc `Error`**. Chúng vẫn có thể xuất hiện trong quá trình chạy dù không kê `throws`.

Ví dụ hợp lệ:

```java
java.util.function.Function<String, Integer> length =
        String::length;

java.util.function.Consumer<String> print =
        System.out::println;

java.util.function.Supplier<String> supply =
        () -> "Hello";
```

Các đoạn trên dùng lambda/method reference không gây checked exception theo chữ ký. Trái lại, `Files.readString(path)` hoặc `BufferedReader.readLine()` có thể ném `IOException` (checked), nên không thể nhét trực tiếp vào callback chuẩn mà không có cách xử lý.

## 2. Checked và unchecked exception: vấn đề nằm ở compile-time

Trong Java:

- **Checked exception:** các subtype của `Throwable` không thuộc nhánh `RuntimeException` hoặc `Error`; ví dụ `IOException`, `SQLException`, `ParseException`, `InterruptedException`. Khi có thể thoát khỏi phương thức, code phải bắt hoặc khai báo theo luật Java.
- **Unchecked exception:** `RuntimeException`/`Error` cùng các subtype. Compiler không áp đặt nghĩa vụ bắt/khai báo như với checked exceptions.
- **Lambda:** có thể chạy code gây lỗi như bất kỳ phương thức nào; **điều bị giới hạn** là tập checked exception nó được phép để thoát, dựa trên **functional interface làm target type**.

Mục đích của checked exception là để người gọi nhìn thấy và giải quyết đường thất bại theo API. `Function.apply` không quảng cáo `IOException`; một nơi gọi `Function.apply`, như implementation của `Stream.map`, không có nghĩa vụ checked I/O được công bố. Java không thể cho lambda bí mật mở rộng hợp đồng ấy.

### 2.1. Code cố ý không biên dịch: `Function`

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

class InvalidFunction {
    static void demo() {
        Function<Path, String> read = path -> Files.readString(path);
    }
}
```

Đây là **ví dụ cố ý không biên dịch**: `Files.readString(Path)` công bố checked `IOException`, còn `Function.apply(Path)` không công bố nó. Thông báo `javac` thường có ý “unreported exception IOException; must be caught or declared”.

Ngay cả khi `demo()` được đổi thành:

```java
static void demo() throws IOException {
    Function<Path, String> read = path -> Files.readString(path);
}
```

thì **vẫn sai**. `throws IOException` thuộc phương thức **`demo()`**, không thay đổi chữ ký `Function.apply()` mà lambda triển khai. Exception có thể phát sinh **sau khi `demo()` đã kết thúc**, tại lúc một caller khác chạy `read.apply(...)`. Compiler cần bảo vệ hợp đồng của callback, không chỉ phương thức nơi tạo lambda.

### 2.2. Code cố ý không biên dịch: `Consumer`

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

class InvalidConsumer {
    static void demo() {
        Consumer<Path> delete = path -> Files.delete(path);
    }
}
```

`Files.delete(Path)` có thể ném checked `IOException`; `Consumer.accept` không khai báo checked exception. Vì vậy đoạn trên bị từ chối.

Đừng suy luận “Consumer trả void thì được ném tùy ý”. Giá trị trả về và quy tắc checked `throws` là hai khía cạnh độc lập.

### 2.3. Code cố ý không biên dịch: `Supplier`

```java
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

class InvalidSupplier {
    static void demo(Path path) {
        Supplier<String> source = () -> Files.readString(path);
    }
}
```

`Supplier.get()` không khai báo checked exception. Ngay cả khi `get()` chỉ được gọi sau này, hợp đồng vẫn không cho `IOException` thoát trực tiếp.

## 3. Lambda không tự quyết định chữ ký `throws`: target typing

Một lambda như:

```java
path -> java.nio.file.Files.readString(path)
```

không mang một “kiểu lambda độc lập” với hợp đồng exception tự động suy từ body. Nó cần một **target type** — thường là một functional interface.

Cùng logic đọc file có thể:

- **Bị từ chối** khi target là `Function<Path,String>`.
- **Hợp lệ** khi target là một functional interface tự định nghĩa có `String apply(Path) throws IOException`.
- **Hợp lệ** khi lambda bắt `IOException` ngay bên trong và trả kết quả/đổi thành unchecked exception phù hợp.

Ví dụ:

```java
@FunctionalInterface
interface ThrowingPathReader {
    String read(java.nio.file.Path path)
            throws java.io.IOException;
}
```

Khi đó:

```java
ThrowingPathReader reader =
        path -> java.nio.file.Files.readString(path);
```

là hợp lệ trong một class/method có import hoặc tên đầy đủ tương ứng. Nhưng `ThrowingPathReader` **không tự là** `Function<Path,String>`; không thể truyền biến reader thẳng vào `Stream.map(...)`, vì `map` yêu cầu `Function` chuẩn.

Đó là lý do đôi khi cần **adapter** giữa hai kiểu callback.

## 4. Method reference gặp cùng ràng buộc

Không chỉ lambda:

```java
// CỐ Ý SAI:
java.util.function.Function<java.nio.file.Path, String> read =
        java.nio.file.Files::readString;
```

`Files.readString(Path)` khai báo `throws IOException`, nên method reference đó không tương thích với phương thức trừu tượng `Function.apply`.

Ngược lại:

```java
ThrowingPathReader read =
        java.nio.file.Files::readString;
```

có thể hợp lệ nếu `ThrowingPathReader` đã định nghĩa như Mục 3.

**Method reference không làm biến mất luật checked exception.** Nó là cách viết ngắn cho việc chuyển method hiện có thành callback của một functional interface phù hợp; việc kiểm tra loại ngoại lệ vẫn dựa trên target method.

## 5. Vì sao `Stream.map(...)` và `forEach(...)` hay làm lộ vấn đề?

API Stream dùng các functional interface chuẩn, ví dụ:

```java
<R> java.util.stream.Stream<R> map(
        java.util.function.Function<? super T, ? extends R> mapper);
```

Đây là chữ ký khái quát của `Stream<T>.map`, không phải một chương trình độc lập. Phương thức mapper nhận kiểu `Function`; vì vậy lambda trong `map` vẫn bị giới hạn checked `throws` của `Function.apply`.

Ví dụ người học muốn đọc nhiều file:

```java
// CỐ Ý SAI — readString có checked IOException:
java.util.List<String> content = paths.stream()
        .map(path -> java.nio.file.Files.readString(path))
        .toList();
```

Giả định `paths` là `List<Path>`. Mặc dù đoạn code ở trong một method có thể khai báo `throws IOException`, callback mà `map` nhận vẫn là `Function<Path,String>` **không khai báo checked exception**.

Tương tự, `forEach` dùng `Consumer`:

```java
// CỐ Ý SAI — Files.delete(path) có checked IOException:
paths.forEach(path -> java.nio.file.Files.delete(path));
```

Không phải vì Stream hay `List.forEach` cấm I/O, mà vì **kiểu callback tại điểm gọi** không cho checked exception thoát trực tiếp.

### 5.1. Stream thường lười thực thi

Trong pipeline Stream, `map` là một **intermediate operation**. Lambda được đăng ký có thể chưa chạy ngay khi tạo pipeline. Thông thường nó được gọi trong quá trình thực hiện **terminal operation** như `toList()`, `collect(...)`, `forEach(...)` hoặc `reduce(...)`.

Điều này ảnh hưởng nơi bắt lỗi:

```java
var pipeline = inputs.stream().map(converter);
try {
    var results = pipeline.toList();
    // Sử dụng results.
} catch (RuntimeException e) {
    // Lỗi unchecked từ converter có thể xuất hiện lúc terminal operation.
}
```

Đoạn minh họa giả định biến `inputs`/`converter` đã được định nghĩa. Nếu `converter` bọc checked `IOException` thành `UncheckedIOException`, lỗi có thể xuất hiện ở `toList()`, không phải tại lúc bạn gọi `map(converter)`.

Đừng kết luận một pipeline “thành công” chỉ vì biểu thức `stream.map(...)` đã trả về một Stream.

### 5.2. Parallel stream và chính sách side effect

Nếu lambda thực hiện I/O qua `parallelStream()`, thứ tự thực thi/hoàn thành có thể không xác định; một exception từ một worker có thể khiến terminal operation thất bại trong khi side effect khác đã xảy ra. Không được giả định rollback, thứ tự log hoặc “đã gặp lỗi thì mọi tác vụ khác chắc chắn dừng ngay”.

Đặc biệt cẩn thận với `Consumer` ghi file/xóa file hoặc thao tác giao dịch: parallel execution và exception không tự bảo đảm tính nguyên tử.

## 6. Cách 1: bắt checked exception bên trong lambda

Khi API chuẩn cần một `Function` và ta có cách **xử lý thật sự** lỗi ở ngay đó, có thể `try/catch` trực tiếp trong body của lambda.

### 6.1. Trả fallback có nghĩa, không che lỗi tùy tiện

Tạo **`FunctionCatchInsideDemo.java`**:

```java
import java.io.IOException;
import java.util.List;
import java.util.function.Function;

public class FunctionCatchInsideDemo {
    static int parseSize(String input) throws IOException {
        if ("bad".equals(input)) {
            throw new IOException("Nguồn không khả dụng");
        }
        return input.length();
    }

    public static void main(String[] args) {
        Function<String, Integer> safeLength = input -> {
            try {
                return parseSize(input);
            } catch (IOException e) {
                System.out.println("Ghi nhận lỗi: " + input);
                return -1;
            }
        };

        List<Integer> results = List.of("cat", "bad", "xy")
                .stream()
                .map(safeLength)
                .toList();

        System.out.println("Kết quả: " + results);
    }
}
```

Biên dịch/chạy Java 17:

```bash
javac -encoding UTF-8 FunctionCatchInsideDemo.java
java FunctionCatchInsideDemo
```

Stdout:

```text
Ghi nhận lỗi: bad
Kết quả: [3, -1, 2]
```

**Giải thích:**

1. `parseSize` công bố `throws IOException`; bên trong có đường lỗi được mô phỏng có kiểm soát.
2. Lambda `safeLength` tự bắt `IOException`, nên không để checked exception thoát khỏi `Function.apply`.
3. Giá trị `-1` ở ví dụ là một **sentinel** được quy ước cho thất bại để dễ quan sát; không phải fallback an toàn mặc định cho mọi bài toán.
4. Stream được chạy qua `toList()`, từ đó callback thực thi.
5. Nếu `-1` cũng là một giá trị hợp lệ trong domain, hoặc caller cần biết nguyên nhân I/O, cách thiết kế này dễ gây nhập nhằng. Khi đó nên dùng một kiểu kết quả rõ ràng hoặc cho lỗi truyền theo một hợp đồng khác.

### 6.2. Tránh “bắt rồi coi là thành công”

Anti-pattern:

```java
Function<Path, String> unsafe = path -> {
    try {
        return Files.readString(path);
    } catch (IOException ignored) {
        return "";
    }
};
```

Đây là đoạn minh họa cần import `Path`/`Files`/`IOException`. Nếu chuỗi rỗng là dữ liệu file hợp lệ, caller không thể phân biệt “file rỗng thật” với “đọc file thất bại”. Không dùng cách này chỉ để qua compiler.

Nếu fallback là một phần **hợp đồng sản phẩm** — ví dụ cache dự phòng đã được kiểm chứng — có thể trả fallback; cần ghi rõ trường hợp nào được fallback và tác động tới trạng thái.

## 7. Cách 2: chuyển checked exception thành unchecked và giữ `cause`

Khi không thể xử lý hợp lệ tại callback, nhưng API bắt buộc dùng `Function`/`Consumer`/`Supplier`, có thể bọc checked exception vào một unchecked exception phù hợp rồi để lỗi **lan lên** nơi quyết định failure.

Với `IOException`, Java cung cấp `java.io.UncheckedIOException`:

```java
try {
    return java.nio.file.Files.readString(path);
} catch (java.io.IOException original) {
    throw new java.io.UncheckedIOException(
            "Không đọc được file", original);
}
```

Đây là mảnh method/lambda minh họa cần đặt trong ngữ cảnh có `return` thích hợp. Ưu điểm:

- `UncheckedIOException extends RuntimeException`, nên callback `Function.apply` được phép ném.
- `getCause()` giữ đối tượng `IOException` gốc để truy nguyên.
- Caller nhận **UncheckedIOException**, không nhận `IOException` trực tiếp khi gọi callback.
- Lỗi không bị nuốt hoặc trá hình thành giá trị thành công.

**Không** thay bằng `throw new RuntimeException(original.getMessage())`: cách đó sao chép message nhưng làm mất mối liên kết cause. Khi dùng wrapper, ưu tiên constructor nhận cause.

### 7.1. Java 17: adapter I/O từ `ThrowingFunction` sang `Function`

Tạo **`UncheckedFunctionAdapterDemo.java`**:

```java
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class UncheckedFunctionAdapterDemo {
    @FunctionalInterface
    interface IoFunction<T, R> {
        R apply(T value) throws IOException;
    }

    static <T, R> Function<T, R> unchecked(
            IoFunction<T, R> source) {
        Objects.requireNonNull(source);
        return value -> {
            try {
                return source.apply(value);
            } catch (IOException e) {
                throw new UncheckedIOException(
                        "Không thể chuyển đổi dữ liệu", e);
            }
        };
    }

    static int readSize(String key) throws IOException {
        if ("bad".equals(key)) {
            throw new IOException("Không đọc được bad");
        }
        return key.length();
    }

    public static void main(String[] args) {
        Function<String, Integer> mapper =
                unchecked(UncheckedFunctionAdapterDemo::readSize);

        try {
            System.out.println(
                    List.of("good", "bad", "later")
                            .stream()
                            .map(mapper)
                            .toList());
        } catch (UncheckedIOException e) {
            System.out.println("Wrapper: "
                    + e.getClass().getSimpleName());
            System.out.println("Cause: "
                    + e.getCause().getClass().getSimpleName());
            System.out.println("Cause message: "
                    + e.getCause().getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 UncheckedFunctionAdapterDemo.java
java UncheckedFunctionAdapterDemo
```

Stdout:

```text
Wrapper: UncheckedIOException
Cause: IOException
Cause message: Không đọc được bad
```

**Luồng hoạt động:**

1. `IoFunction.apply` **có** `throws IOException`, nên method reference `readSize` là target hợp lệ của `IoFunction`.
2. `unchecked(...)` trả một `Function<T,R>` chuẩn, bắt `IOException` và ném `UncheckedIOException`.
3. `Stream.map(...)` nhận `Function` và biên dịch được.
4. Khi terminal `toList()` chạy, phần tử `"bad"` làm `readSize` ném `IOException`.
5. Wrapper unchecked thoát lên vị trí terminal operation và được `catch (UncheckedIOException)` bắt.
6. `"later"` không được biến đổi trong pipeline tuần tự này sau khi exception đã làm terminal operation thất bại; ví dụ không dựa vào giả định này để khôi phục công việc.

**Không phải adapter phép màu:** adapter đổi **cách báo lỗi**, không biến thất bại thành thành công, không rollback side effect và không bảo đảm retry an toàn. Nó phù hợp khi boundary của API đã chấp nhận unchecked failure và caller có chính sách chẩn đoán/khôi phục.

### 7.2. Khi không phải `IOException` thì sao?

`UncheckedIOException` chỉ nhận **`IOException`** làm nguyên nhân. Với checked exception khác, có thể tạo một subclass `RuntimeException` phù hợp hoặc dùng `RuntimeException("...", cause)` để giữ cause khi đúng hợp đồng.

Đừng bọc tất cả `RuntimeException` sẵn có thành một wrapper khác nếu không có lý do: làm mất thông tin phân loại và khiến stack phức tạp. Adapter chuyên biệt nên bắt **chỉ checked exception dự kiến**, để những unchecked lỗi lập trình tiếp tục truyền nguyên dạng.

## 8. Cách 3: dùng `ThrowingFunction<T,R,E>` riêng có `throws E`

Khi **bạn kiểm soát API nhận lambda**, cách thể hiện hợp đồng checked exception rõ ràng nhất đôi khi là tạo functional interface riêng:

```java
@FunctionalInterface
interface ThrowingFunction<T, R, E extends Exception> {
    R apply(T input) throws E;
}
```

Nếu `E` là `IOException`, lambda có thể để `IOException` thoát và caller của method nhận callback phải giải quyết nó. Interface này dùng **generic type parameter `E`** để khai báo checked exception có thể phát sinh.

Tương tự:

```java
@FunctionalInterface
interface ThrowingConsumer<T, E extends Exception> {
    void accept(T input) throws E;
}

@FunctionalInterface
interface ThrowingSupplier<T, E extends Exception> {
    T get() throws E;
}
```

Ba kiểu trên là **định nghĩa tự tạo**, không phải các interface chuẩn của `java.util.function`. Tên `ThrowingFunction` và generic `E extends Exception` là một convention thiết kế; Java không có sẵn chúng trong `java.util.function`.

### 8.1. Generic `E` giúp gì?

Xét:

```java
static <T, R, E extends Exception> R call(
        T input, ThrowingFunction<T, R, E> operation) throws E {
    return operation.apply(input);
}
```

Đây là method minh họa cần đặt trong class. Vì operation có thể ném `E`, method `call` công bố `throws E`. Nếu lambda gắn với `IOException`, caller có thể bắt `IOException` thay vì buộc bắt `Exception` rộng.

Generic exception type có thể tăng độ chính xác hợp đồng. Tuy nhiên type inference có quy tắc riêng; nếu chương trình phức tạp hoặc có nhiều exception độc lập, có thể cần khai báo type parameter/chữ ký rõ ràng. Đừng giả định một generic `E` mô tả hoàn hảo mọi union của các checked exception.

### 8.2. Java 17: giữ nguyên checked exception qua custom functional interface

Tạo **`ThrowingFunctionDemo.java`**:

```java
import java.io.IOException;

public class ThrowingFunctionDemo {
    @FunctionalInterface
    interface ThrowingFunction<T, R, E extends Exception> {
        R apply(T value) throws E;
    }

    static <T, R, E extends Exception> R execute(
            T input, ThrowingFunction<T, R, E> action) throws E {
        return action.apply(input);
    }

    static int loadLength(String input) throws IOException {
        if ("bad".equals(input)) {
            throw new IOException("Dữ liệu không đọc được");
        }
        return input.length();
    }

    public static void main(String[] args) {
        try {
            int result = ThrowingFunctionDemo
                    .<String, Integer, IOException>execute(
                            "cat", ThrowingFunctionDemo::loadLength);
            System.out.println("Length: " + result);

            ThrowingFunctionDemo
                    .<String, Integer, IOException>execute(
                            "bad", ThrowingFunctionDemo::loadLength);
        } catch (IOException e) {
            System.out.println("Checked: "
                    + e.getClass().getSimpleName());
            System.out.println("Message: " + e.getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 ThrowingFunctionDemo.java
java ThrowingFunctionDemo
```

Stdout:

```text
Length: 3
Checked: IOException
Message: Dữ liệu không đọc được
```

**Điểm quan trọng:**

- `ThrowingFunction.apply` cho phép checked `E`.
- `execute(...)` công bố `throws E`, nên exception gốc có thể thoát **mà không cần wrapper unchecked**.
- Ở call site, ví dụ chỉ rõ generic arguments, gồm `IOException`, để việc học dễ theo dõi; trong trường hợp đơn giản compiler có thể suy luận kiểu.
- Không cần `catch(Exception)` hoặc `throws Exception` rộng nếu hợp đồng thực tế chỉ yêu cầu `IOException`.
- Nếu muốn truyền `ThrowingFunction` trực tiếp cho `Stream.map` chuẩn, vẫn không được: `map` nhận `Function`, không phải kiểu custom này.

### 8.3. Vì sao tự định nghĩa interface không giải quyết trực tiếp `Stream.map`?

Trong Java, lambda là target-typed, nhưng `Stream.map` đã công bố:

```java
<R> Stream<R> map(Function<? super T, ? extends R> mapper)
```

Method signature của `map` thuộc thư viện chuẩn; việc bạn tạo `ThrowingFunction` **không thay thế parameter type của `map`**. Muốn áp dụng, phải:

1. Viết **adapter** từ `ThrowingFunction` sang `Function`, quyết định chuyển lỗi sang unchecked hoặc xử lý khác; hoặc
2. Viết hàm/luồng xử lý riêng nhận `ThrowingFunction` và công bố `throws E`; hoặc
3. Dùng vòng lặp thông thường có `try/catch`/`throws` khi logic checked exception là trung tâm.

Đây là hạn chế của **API đích**, không phải vấn đề generic làm thiếu từ khóa `throws`.

## 9. `Consumer<T>`: xử lý side effect và checked exception

`Consumer.accept` nhận một đối tượng, trả `void`; các thao tác side effect thường là ghi file, xóa file, lưu dữ liệu, gửi sự kiện hoặc cập nhật trạng thái. Những thao tác này đặc biệt dễ có checked exceptions.

Một `Consumer` với `forEach` có thể:

- Bắt lỗi và tiếp tục xử lý phần tử kế tiếp **nếu đó là chính sách đã chọn**.
- Bắt rồi ném unchecked wrapper khiến vòng lặp thường dừng ngay vì exception.
- Đưa lỗi vào một kết quả có cấu trúc được quản lý bên ngoài (cần cân nhắc concurrency và thread safety).
- Không thể để checked exception thoát trực tiếp theo `Consumer.accept`.

### 9.1. Java 17: bắt từng lỗi khi chính sách cho phép tiếp tục

Tạo **`ConsumerFailurePolicyDemo.java`**:

```java
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

public class ConsumerFailurePolicyDemo {
    static void process(String name) throws IOException {
        if ("bad".equals(name)) {
            throw new IOException("Không xử lý được bad");
        }
        System.out.println("OK: " + name);
    }

    public static void main(String[] args) {
        Consumer<String> consumer = name -> {
            try {
                process(name);
            } catch (IOException e) {
                System.out.println("FAILED: " + name);
            }
        };

        List.of("one", "bad", "two").forEach(consumer);
        System.out.println("Hoàn tất lượt forEach");
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 ConsumerFailurePolicyDemo.java
java ConsumerFailurePolicyDemo
```

Stdout:

```text
OK: one
FAILED: bad
OK: two
Hoàn tất lượt forEach
```

Ví dụ chủ động chọn **tiếp tục sau một phần tử thất bại**, vì các phần tử được coi độc lập trong tình huống mô phỏng. Đây **không phải mặc định đúng** cho các thao tác ghi dữ liệu thực tế.

Nếu thất bại của một phần tử khiến cả batch phải thất bại, chỉ in “FAILED” rồi tiếp tục như thành công là sai. Khi đó hãy dùng cấu trúc kết quả batch thể hiện phần tử lỗi, hoặc ném wrapper và để caller xử lý trạng thái batch; cũng phải xét những side effect đã xảy ra trước đó.

### 9.2. Viết vòng lặp thường khi cần semantics checked rõ ràng

Thay vì `forEach`:

```java
static void deleteAll(java.util.List<java.nio.file.Path> files)
        throws java.io.IOException {
    for (java.nio.file.Path path : files) {
        java.nio.file.Files.delete(path);
    }
}
```

Đây là một **method hoàn chỉnh**, nhưng cần đặt trong class. Nó giữ checked `throws IOException` rõ ràng, dùng `for` thông thường và không cần adapter `Consumer`.

Khi chức năng chính là I/O có thể thất bại, vòng lặp thường thường **dễ hiểu hơn** việc ép `Consumer` chuẩn rồi bọc exception qua nhiều tầng. Không phải mọi tác vụ phải dùng Stream/lambda mới là Java hiện đại.

## 10. `Supplier<T>`: cung cấp giá trị nhưng `get()` không cho checked exception

`Supplier<T>` rất phổ biến với tạo lazy value, `Optional.orElseGet(...)`, các API cache và các hook tạo đối tượng. Nếu khởi tạo giá trị có I/O/SQL checked, lambda sẽ gặp giới hạn tương tự.

Ví dụ sai:

```java
java.util.function.Supplier<String> config =
        () -> java.nio.file.Files.readString(path);
```

Giả định `path` có kiểu `Path`. `Files.readString` có `throws IOException`, nên không biên dịch khi dùng trực tiếp với `Supplier.get`.

### 10.1. Java 17: `Supplier` đổi IOException thành unchecked

Tạo **`SupplierCheckedDemo.java`**:

```java
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Supplier;

public class SupplierCheckedDemo {
    static String readConfig(boolean fail) throws IOException {
        if (fail) {
            throw new IOException("Không tải được config");
        }
        return "ready";
    }

    static Supplier<String> createSupplier(boolean fail) {
        return () -> {
            try {
                return readConfig(fail);
            } catch (IOException e) {
                throw new UncheckedIOException("Lỗi cấu hình", e);
            }
        };
    }

    public static void main(String[] args) {
        Supplier<String> good = createSupplier(false);
        Supplier<String> bad = createSupplier(true);

        System.out.println("Good: " + good.get());
        try {
            System.out.println("Bad: " + bad.get());
        } catch (UncheckedIOException e) {
            System.out.println("Wrapper: "
                    + e.getClass().getSimpleName());
            System.out.println("Cause: " + e.getCause().getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 SupplierCheckedDemo.java
java SupplierCheckedDemo
```

Stdout:

```text
Good: ready
Wrapper: UncheckedIOException
Cause: Không tải được config
```

`createSupplier(...)` chỉ tạo đối tượng callback; `readConfig(...)` được thực thi **khi gọi `get()`**, không phải lúc dựng supplier. Do đó lỗi xảy ra trong `get()`. Chữ ký `Supplier.get` không khai báo checked exception, nên wrapper unchecked làm đường thất bại tiếp tục hợp lệ với API đích.

### 10.2. `Optional.orElseGet` cũng chịu quy tắc này

`Optional.orElseGet(Supplier<? extends T>)` nhận một `Supplier` chuẩn. Nếu fallback phải gọi API checked I/O, code cần xử lý/bọc exception theo cùng cách. Không thể vì đó là “fallback” mà Java tự miễn checked exception.

Thêm một lưu ý: `Optional.orElseGet` chỉ gọi supplier khi Optional rỗng, nên lỗi từ supplier **chỉ có thể phát sinh trên đường cần fallback**. Đừng chủ quan không kiểm thử nhánh này.

## 11. `Callable<V>` khác `Supplier<V>` ở checked `throws`

Trong thư viện chuẩn có `java.util.concurrent.Callable<V>`:

```java
@FunctionalInterface
public interface Callable<V> {
    V call() throws Exception;
}
```

Vì `Callable.call()` khai báo `throws Exception`, một lambda gọi API ném checked `IOException` có thể được gán cho `Callable<String>`. Đây là đối chiếu rất rõ với `Supplier.get()`.

| Tiêu chí | `Supplier<T>` | `Callable<V>` |
| --- | --- | --- |
| Phương thức đích | `T get()` | `V call() throws Exception` |
| Checked exception có thể thoát? | Không | Có; trong phạm vi `Exception` |
| Package | `java.util.function` | `java.util.concurrent` |
| Được `Stream.map` nhận trực tiếp? | Không; map nhận `Function` | Không; map nhận `Function` |
| Dùng nhiều trong | Lazy value, Optional, factory callback | Executor/service task, công việc có thể thất bại |

`Callable` không phải một `Supplier` có tên khác. Hai interface có mục đích và chữ ký khác nhau; không thể đưa `Callable` trực tiếp cho API yêu cầu `Supplier` mà không adapter.

### 11.1. Java 17: `Callable` giữ checked exception

Tạo **`CallableVsSupplierDemo.java`**:

```java
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

public class CallableVsSupplierDemo {
    static String load(boolean fail) throws IOException {
        if (fail) {
            throw new IOException("Không có dữ liệu");
        }
        return "loaded";
    }

    public static void main(String[] args) {
        Callable<String> task = () -> load(true);
        Supplier<String> good = () -> {
            try {
                return load(false);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        };

        System.out.println("Supplier: " + good.get());
        try {
            System.out.println("Callable: " + task.call());
        } catch (IOException e) {
            System.out.println("Checked: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Other: "
                    + e.getClass().getSimpleName());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 CallableVsSupplierDemo.java
java CallableVsSupplierDemo
```

Stdout:

```text
Supplier: loaded
Checked: Không có dữ liệu
```

**Vì sao `catch(IOException)` hợp lệ?** `Callable.call()` công bố `throws Exception`, nên có thể phát sinh checked subtype `IOException`. Handler `catch(Exception)` còn lại bao phủ các checked exceptions khác mà hợp đồng `Callable` cho phép.

Nếu đổi lời gọi `task.call()` thành `good.get()` trong `try` chỉ có `Supplier.get()`, một handler checked `IOException` không có nguồn checked tương ứng sẽ bị compiler từ chối. Trường hợp có wrapper unchecked, phải bắt kiểu wrapper thích hợp.

### 11.2. `Runnable` cũng không khai báo checked exception

`Runnable.run()` là `void run()`, không `throws` checked exception. Vì vậy lambda truyền cho `new Thread(() -> ...)` hoặc `Executor.execute(Runnable)` gặp hạn chế tương tự `Consumer`. Ngược lại, `Callable.call` cho phép `throws Exception`.

**Tuy nhiên:** khi submit `Callable` tới `ExecutorService.submit` và lấy kết quả bằng `Future.get()`, thất bại tác vụ thường được bọc trong `ExecutionException` (cause là lỗi từ task). Không suy ra rằng checked `IOException` tự động ném trực tiếp từ `Future.get()`. Mỗi tầng có hợp đồng exception riêng.

## 12. Kiểu generic `E extends Exception` và giới hạn của suy luận kiểu

Một interface tự tạo có thể viết:

```java
@FunctionalInterface
interface CheckedMapper<T, R, E extends Exception> {
    R map(T input) throws E;
}
```

Ở đây `E` là biến kiểu đại diện cho **một họ ngoại lệ được khai báo theo type parameter**. Khi API sở hữu callback, nó có thể truyền nghĩa vụ checked từ callback ra caller:

```java
static <T, R, E extends Exception> R convert(
        T value, CheckedMapper<T, R, E> mapper) throws E {
    return mapper.map(value);
}
```

Nhưng một số giới hạn cần nhớ:

- `E` không làm `java.util.function.Function` bỗng chốc khai báo checked exception.
- Generic parameter là kiểu Java, không phải cú pháp “union exception”. Với nhiều checked exception không có quan hệ subtype chung hẹp, đôi khi API cần `throws Exception` hoặc phải khai báo nhiều tham số kiểu/thêm thiết kế thích hợp.
- Type inference chịu ràng buộc của ngữ cảnh gọi và lambda; trong ví dụ phức tạp, nên kiểm tra chữ ký suy luận thay vì khẳng định mọi lời gọi đều suy luận đúng subtype nhỏ nhất.
- Không nên tạo cả một framework utility generic chỉ để thay một vòng lặp `for` dễ hiểu.
- Java không có checked-exception polymorphism như một “effect system” tùy ý; luật `throws` vẫn nằm ở những phương thức và interface được khai báo.

### 12.1. Vì sao không để `E extends Throwable` tùy tiện?

Về cú pháp, functional interface có thể khai báo `throws E` với `E extends Throwable`. Tuy nhiên việc mở rộng lên `Throwable` có thể khiến API caller phải xử lý cả các tình huống dưới `Error` hoặc khai báo quá rộng, không hợp nghĩa vụ thông thường. `E extends Exception` thường phù hợp hơn nếu mục tiêu là hỗ trợ checked exceptions trong ứng dụng.

## 13. Cách viết adapter tổng quát: ưu điểm và bẫy

Một thư viện nội bộ đôi khi muốn adapter generic:

```java
@FunctionalInterface
interface CheckedFunction<T, R, E extends Exception> {
    R apply(T input) throws E;
}

static <T, R, E extends Exception>
java.util.function.Function<T, R> unchecked(
        CheckedFunction<T, R, E> source) {
    return input -> {
        try {
            return source.apply(input);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Callback thất bại", e);
        }
    };
}
```

Đây là **hai khai báo minh họa** cần đặt đúng trong class/interface. Cơ chế:

1. `CheckedFunction` cho phép checked `E` thoát.
2. Adapter bắt unchecked `RuntimeException` và **ném lại nguyên trạng**.
3. Adapter bắt các `Exception` checked còn lại và bọc trong một `RuntimeException` có `cause`.
4. Kết quả adapter có kiểu `Function<T,R>` để truyền cho API chuẩn.

Nhưng cách này có hạn chế:

- Sau khi bọc, caller phải xử lý runtime wrapper, **không được mong `IOException` trực tiếp**.
- Wrapper chung mất khả năng phân loại nhanh các checked exceptions nếu không đọc `getCause()`; một adapter chuyên biệt cho `IOException` dùng `UncheckedIOException` có thể tốt hơn.
- `Exception` là phạm vi catch rộng; cần quyết định có nên giữ nguyên các `RuntimeException` vốn là lỗi khác không. Đoạn mẫu đã làm điều đó.
- Không được dùng adapter để âm thầm nuốt exception, hoặc thay mọi lỗi bằng `null`/giá trị mặc định ngoài hợp đồng.
- Nếu API xử lý song song, adapter phải tuân thủ thread safety của callback và quản lý side effect riêng.

### 13.1. Không “sneaky throw” chỉ để qua compiler

Có những thủ thuật generic/erasure cho phép ném checked exception qua một API không công bố nó (*sneaky throw*). Điều đó có thể khiến người sử dụng `Function.apply` bất ngờ nhận một checked exception ở runtime mà chữ ký không cho họ dự đoán.

Trong phần lớn ứng dụng, hãy ưu tiên ba cách rõ hợp đồng:

1. Bắt và xử lý thực sự trong callback.
2. Bọc unchecked có `cause` và chấp nhận thay đổi hợp đồng báo lỗi.
3. Dùng API/functional interface cho phép checked exception, hoặc vòng lặp thông thường.

Thủ thuật né compiler làm giảm khả năng đọc hiểu, có thể gây khó xử lý ở caller và khiến unit test/exception policy thiếu minh bạch.

## 14. Tài nguyên và `try-with-resources` trong lambda

Lambda có thể mở resource và tự đóng bằng `try-with-resources`:

```java
Function<Path, String> load = path -> {
    try (var reader = Files.newBufferedReader(path)) {
        return reader.readLine();
    } catch (IOException e) {
        throw new UncheckedIOException(
                "Không thể đọc dòng đầu", e);
    }
};
```

Đây là **đoạn minh họa** (cần import `Path`, `Files`, `Function`, `IOException`, `UncheckedIOException`). Nhờ TWR, reader được đóng khi rời callback. Vì cả `Files.newBufferedReader(...)`, `readLine()` và `close()` có thể ném `IOException`, callback bắt và chuyển sang `UncheckedIOException`.

Những điểm cần chú ý:

- Lambda gọi nhiều lần có thể mở/đóng nhiều resource riêng; cân nhắc hiệu năng và vòng đời.
- Nếu thân đọc và `close()` đều thất bại, TWR giữ primary/suppressed theo quy tắc của `Throwable`; wrapper có `cause` là primary, nhờ đó vẫn có thể xem `getCause().getSuppressed()`.
- Không đưa một resource đã đóng ra ngoài callback rồi dùng lại tùy tiện.
- Nếu callback trả `Stream<String>` từ `Files.lines(path)`, phải thiết kế rõ **ai sở hữu và đóng stream**; không thể luôn đóng ngay trước khi caller kịp xử lý các phần tử.
- `Files.lines` có checked `IOException` khi mở, và một số lỗi đọc sau đó có thể được báo dưới dạng unchecked I/O trong lúc stream được tiêu thụ; cần hiểu hợp đồng API cụ thể.

### 14.1. Lazy stream và vị trí xảy ra lỗi I/O

Một lời gọi cung cấp dữ liệu cho Stream có thể không tiêu thụ file ngay. Nếu đóng source quá sớm hoặc giữ resource mở vô thời hạn, chương trình có thể gặp lỗi trong giai đoạn sau.

Đây là vấn đề **ownership/resource lifetime** bên cạnh vấn đề checked exception của callback. Không nên kết luận rằng chỉ cần adapter `Function` là đã giải quyết cả việc đóng tài nguyên.

## 15. `CompletableFuture`: callback khác, hợp đồng khác

Các method như `CompletableFuture.supplyAsync(Supplier<U>)` cũng nhận `Supplier` **không khai báo checked exception**. Vì thế:

```java
// CỐ Ý SAI nếu readChecked() throws IOException:
java.util.concurrent.CompletableFuture.supplyAsync(
        () -> readChecked());
```

sẽ gặp hạn chế checked tương tự.

Nếu chuyển thành unchecked wrapper trong supplier, một CompletableFuture thất bại sẽ hoàn tất **exceptionally**. Khi gọi `join()`, người sử dụng thường nhận `CompletionException`, và phải đọc cause chain theo hợp đồng `CompletableFuture`. Không giả định rằng `join()` tự ném checked `IOException` trực tiếp.

Khi dùng `get()` của `Future`/`CompletableFuture`, API công bố `ExecutionException` cùng các ngoại lệ của hoạt động chờ; tiếp tục phân biệt **lỗi gốc**, **wrapper unchecked trong callback**, và **wrapper đồng bộ hóa ở tương lai**.

### 15.1. Hệ quả với policy retry

Một thao tác callback đã ghi một phần dữ liệu trước khi thất bại không tự trở nên an toàn để retry chỉ vì nó chạy trong future/stream. Muốn retry, phải biết thao tác idempotent hay có transaction/compensation phù hợp. Exception wrapping giữ chẩn đoán, không hoàn tác side effect.

## 16. Khi nào nên bỏ Stream/lambda để viết vòng lặp?

Có những lúc câu trả lời tốt nhất là không ép I/O checked vào `Function` hoặc `Consumer`.

Ví dụ đọc nhiều file và **báo checked IOException trực tiếp**:

```java
static java.util.List<String> readAll(
        java.util.List<java.nio.file.Path> paths)
        throws java.io.IOException {
    java.util.List<String> results =
            new java.util.ArrayList<>();
    for (java.nio.file.Path path : paths) {
        results.add(java.nio.file.Files.readString(path));
    }
    return results;
}
```

Đây là method Java hợp lệ đặt trong class. Ưu điểm:

- Chữ ký `throws IOException` phản ánh lỗi I/O.
- Không cần adapter hoặc wrapper chỉ để làm vừa `Stream.map`.
- Thứ tự duyệt và vị trí lỗi khá dễ đọc.
- Có thể kiểm soát dừng khi lỗi, cleanup và xử lý phần đã hoàn tất.

**Nhược điểm không phải điều đáng sợ:** số dòng nhiều hơn Stream, nhưng hợp đồng lỗi rõ ràng. Ưu tiên khả năng đọc hiểu và bảo toàn ngữ nghĩa hơn cú pháp “functional” bằng mọi giá.

## 17. So sánh các phương án theo hợp đồng

| Nhu cầu | Hướng thường phù hợp | Điều cần tránh |
| --- | --- | --- |
| Lỗi có fallback đúng nghiệp vụ trong từng phần tử | `try/catch` ngay trong lambda chuẩn | Trả sentinel nhập nhằng rồi giả thành công |
| API thư viện yêu cầu `Function`/`Consumer`/`Supplier`, không thể đổi | Bọc unchecked có cause, hoặc kết quả có cấu trúc khi API hỗ trợ | Tự ý truyền checked exception bằng thủ thuật |
| Bạn kiểm soát API nhận lambda | `ThrowingFunction`/`ThrowingConsumer`/`ThrowingSupplier` có `throws E` | Tạo abstraction quá phức tạp không có nhu cầu |
| Task được gọi như `Callable` | Dùng `Callable.call() throws Exception` | Nghĩ Callable là subtype của Supplier |
| Đọc/ghi file hàng loạt cần truyền checked exception lên caller | `for` thường với `throws IOException` | Ép stream rồi nuốt lỗi |
| Stream pipeline đọc file lazy | Thiết kế resource lifetime và vị trí terminal operation | Đóng resource quá sớm/không đóng |
| Async supplier | Chuyển lỗi có cause, dùng exception policy của future | Mong checked IOException thoát trực tiếp từ `join()` |
| Xử lý nhiều phần tử độc lập | Kết quả per-item hoặc error collection có nghĩa | Gọi mọi phần tử thành công khi một số đã thất bại |

## 18. Những lỗi phổ biến

### 18.1. “Lambda không được ném exception”

Sai. Lambda có thể ném unchecked exceptions, và có thể ném checked exceptions **nếu functional method của target type cho phép**, chẳng hạn `Callable.call() throws Exception` hoặc custom `ThrowingFunction.apply() throws IOException`.

### 18.2. “Chỉ cần thêm `throws IOException` vào method bao ngoài”

Sai nếu checked exception nằm **bên trong callback `Function.apply`** không khai báo checked. Tập exception cho phép được xét theo phương thức của target functional interface.

### 18.3. “Khi `Stream.map` chứa I/O thì Java tự chuyển checked thành unchecked”

Sai. Compiler từ chối callback chưa xử lý checked exception. Chỉ khi bạn hoặc chính API cụ thể chủ động wrapper thì kiểu unchecked xuất hiện.

### 18.4. “Dùng `Function<T,R>` với `throws E` ở generic type argument”

Sai. `Function` chuẩn chỉ có hai type parameters T/R; không có parameter kiểu ngoại lệ. Không thể viết `Function<T,R,IOException>`.

### 18.5. “Method reference được miễn quy tắc `throws`”

Sai. `Files::readString` chịu kiểm tra checked exception theo target tương tự lambda gọi `Files.readString(path)`.

### 18.6. “Bọc mọi exception thành unchecked là luôn tốt”

Không. Nó thay đổi hợp đồng của caller và có thể che phân loại lỗi. Chỉ dùng khi đúng kiến trúc và giữ cause, không biến mọi failure thành một kiểu vô nghĩa.

### 18.7. “`UncheckedIOException` dùng cho mọi checked exception”

Sai. Constructor của `UncheckedIOException` dành cho `IOException`. Với `SQLException` hoặc exception checked khác, chọn wrapper/thiết kế khác phù hợp.

### 18.8. “`Callable` có thể truyền trực tiếp cho `Supplier`”

Sai. Hai interface độc lập, dù đều trả giá trị. Chữ ký abstract method khác nhau: `get()` vs `call() throws Exception`. Cần adapter với chính sách exception rõ ràng nếu muốn chuyển.

### 18.9. “Chuyển sang `parallelStream` sẽ tự giải quyết IOException”

Sai. Parallel không thay target method `Function.apply`/`Consumer.accept`. Nó còn làm side effect và thứ tự xử lý khó suy luận hơn.

### 18.10. “Ngoại lệ trong `map` xuất hiện ngay khi viết `map(...)`”

Không nhất thiết. Stream thường lazy: callback được gọi khi terminal operation chạy. Hãy đặt handler ở phạm vi chứa **thao tác thực sự tiêu thụ pipeline**.

### 18.11. “Có `catch` nên mọi trạng thái trước đó đã rollback”

Sai. Nếu Consumer thực hiện ghi/xóa file trước khi lỗi xảy ra, những side effect trước đó không tự bị hoàn tác. Checked/unchecked chỉ là hợp đồng báo lỗi, không phải transaction.

### 18.12. “Bắt rồi trả `null` là trung lập”

Sai. `null` có thể bị hiểu là dữ liệu hợp lệ, gây `NullPointerException` ở vị trí xa lỗi gốc hoặc mất thông tin chẩn đoán. Dùng result có cấu trúc, fallback hợp lệ hoặc lan failure.

## 19. Câu hỏi thường gặp

### 19.1. Tại sao các interface chuẩn không thêm `throws Exception` cho thuận tiện?

Đó là lựa chọn API của thư viện chuẩn: phần lớn callback thông dụng được thiết kế để không ép mọi caller phải xử lý checked `Exception` rộng. Nếu `Function.apply` khai báo `throws Exception`, vô số API và code dùng nó sẽ phải thay chữ ký/bắt lỗi, kể cả khi callback không bao giờ làm I/O.

Điều đó có lợi cho nhiều thao tác thuần nhưng bất tiện khi tác vụ callback có checked failures; Java cho phép tạo functional interface chuyên biệt khi cần.

### 19.2. Có thể viết `try/catch` ở ngoài `stream.map(...)` không?

Chỉ `try/catch` bên ngoài **không khắc phục lỗi compile-time của lambda** nếu lambda còn để checked exception thoát. Trước tiên lambda phải hợp lệ với `Function.apply` bằng cách tự xử lý, chuyển sang unchecked hoặc dùng API khác. Sau đó bên ngoài có thể bắt wrapper unchecked khi terminal operation chạy.

### 19.3. Có thể gắn `throws IOException` lên chính lambda không?

Lambda expression của Java không có cú pháp khai báo `throws` như một method declaration độc lập. Nó nhận khả năng checked `throws` từ phương thức trừu tượng của functional interface target.

### 19.4. Làm sao giữ stack trace và nguyên nhân?

Bọc lỗi bằng constructor nhận cause, ví dụ `new UncheckedIOException("...", e)` hoặc `new RuntimeException("...", e)`, rồi log Throwable tại boundary. Đừng chỉ giữ message hoặc thay lỗi gốc bằng một message mới.

### 19.5. `ThrowingFunction` có phải chuẩn JDK không?

Không. Đây là tên custom thường gặp; dự án có thể định nghĩa riêng hoặc sử dụng thư viện hỗ trợ. Cần đọc hợp đồng cụ thể nếu dùng thư viện bên thứ ba, vì một số adapter có cách bọc lỗi khác nhau.

### 19.6. Dùng `Supplier` cho lazy I/O có nguy hiểm gì?

Đôi khi caller gọi `get()` nhiều lần, có thể mở resource nhiều lần hoặc kích hoạt nhiều lần side effect. `Supplier` **không** hứa memoization. Nếu cần cache kết quả, phải xây chính sách caching và vòng đời resource riêng.

### 19.7. Khi nào nên dùng `Callable`?

Khi API thực sự nhận `Callable` hoặc tác vụ có contract `call() throws Exception` phù hợp. Đừng dùng `Callable` chỉ vì muốn nhét callback vào `Stream.map`, vì kiểu đầu vào của `map` là `Function`.

## 20. Bài tập tự luyện

### Bài 1 — Xác định compile error

Đọc ba ví dụ `InvalidFunction`, `InvalidConsumer` và `InvalidSupplier`. Với từng ví dụ, viết rõ **phương thức đích** và API nào khai báo checked `IOException`. Giải thích vì sao phương thức bao ngoài `throws IOException` không giải quyết được.

### Bài 2 — Thay target type

Từ method `loadLength(String) throws IOException`, thử gán method reference cho `Function<String,Integer>` và `ThrowingFunction<String,Integer,IOException>`. Chỉ ra bản nào biên dịch và vì sao.

### Bài 3 — Fallback hay error signal?

Trong `FunctionCatchInsideDemo`, giả sử giá trị `-1` cũng là một length hợp lệ trong mô hình mới. Hãy thay bằng một result type có trạng thái thành công/thất bại rõ ràng, hoặc để lỗi lan theo một callback có checked `throws`.

### Bài 4 — Wrapper và cause

Trong `UncheckedFunctionAdapterDemo`, thay `new UncheckedIOException("...", e)` bằng `new RuntimeException(e.getMessage())`. Caller còn lấy được IOException gốc qua `getCause()` không? Vì sao đây là regression về chẩn đoán?

### Bài 5 — Tiếp tục hay dừng batch?

Chạy `ConsumerFailurePolicyDemo`. Chuyển handler từ in `FAILED` sang ném `UncheckedIOException` để cả vòng lặp dừng tại phần tử `"bad"`. Dự đoán dòng `OK: two` có còn xuất hiện không. Mô tả cách báo batch failure cho caller.

### Bài 6 — Dùng Callable

Chạy `CallableVsSupplierDemo`. Thay `Callable` bằng `Supplier` rồi để lambda trực tiếp gọi `load(true)`. Compiler báo vấn đề gì? Hãy so sánh `Callable.call` và `Supplier.get`.

### Bài 7 — Stream lazy

Tạo `Stream<String>` với `map` dùng một `Function` in “mapping X” rồi trả kết quả. Không chạy terminal operation, quan sát callback chưa được thực hiện. Thêm `toList()` và giải thích thời điểm callback chạy.

### Bài 8 — Loop thay stream

Viết method `readAll(List<Path>) throws IOException` bằng vòng lặp, sau đó thử cùng logic bằng `stream().map(Files::readString).toList()`. So sánh khả năng compile, hợp đồng của caller và cách bảo toàn IOException khi muốn truyền trực tiếp.

### Bài 9 — Exception trong CompletableFuture

Dùng `CompletableFuture.supplyAsync` với supplier bọc IOException thành UncheckedIOException. Dùng `join()` và quan sát `CompletionException`/cause chain theo hợp đồng API. Phân biệt wrapper của callback với wrapper của future.

### Bài 10 — Quản lý tài nguyên

Dùng `try-with-resources` trong callback để mở reader, đọc xong rồi đóng. Nếu callback trả một Stream lazy đọc từ cùng reader, hãy giải thích vì sao đóng resource trước khi stream được tiêu thụ là lỗi thiết kế ownership.

## 21. Checklist trước khi dùng lambda có I/O/SQL

- [ ] Tôi đã xác định callback target là `Function`, `Consumer`, `Supplier` hay interface khác chưa?
- [ ] Tôi đã đọc chữ ký **phương thức trừu tượng** của target, có `throws` checked không?
- [ ] Tôi không kỳ vọng `throws IOException` ở method bao ngoài tự sửa lambda sai contract chứ?
- [ ] Tôi phân biệt lambda với method reference: cả hai tuân theo target checked throws?
- [ ] Khi dùng `Stream.map`, tôi biết lambda thường chạy lúc terminal operation chứ?
- [ ] Nếu bắt ngay trong lambda, tôi có chính sách phục hồi/fallback thực sự thay vì che lỗi không?
- [ ] Nếu bọc unchecked, tôi có giữ cause và không bọc thừa RuntimeException không?
- [ ] Tôi đã chọn đúng `UncheckedIOException` chỉ cho IOException chưa?
- [ ] Nếu tự định nghĩa `ThrowingFunction<T,R,E>`, API nhận callback có công bố `throws E` đầy đủ không?
- [ ] Tôi nhớ custom `ThrowingFunction` không truyền trực tiếp vào `Stream.map` nếu thiếu adapter chứ?
- [ ] Tôi có cân nhắc một vòng lặp thường để giữ checked IOException minh bạch không?
- [ ] Với `Consumer` có side effect, tôi biết trạng thái của các phần tử đã xử lý khi lỗi không?
- [ ] Với `Supplier`, tôi biết `get()` có thể được gọi nhiều lần và chưa chắc memoize không?
- [ ] Tôi có phân biệt `Supplier.get` với `Callable.call throws Exception` không?
- [ ] Khi dùng future, tôi xem đúng hợp đồng exception của `join()`/`get()` không?
- [ ] Resource mở trong lambda có được đóng đúng lúc, đặc biệt với stream lazy không?
- [ ] Tôi đã test đường thành công, checked failure, wrapper cause và side effect sau lỗi chưa?

**Kết luận:** `Function.apply`, `Consumer.accept` và `Supplier.get` **không có checked `throws`**, nên lambda/method reference gán cho chúng không được để checked exception thoát trực tiếp. Có ba hướng giải quyết rõ nghĩa: **bắt và xử lý trong callback**, **chuyển sang unchecked wrapper có cause**, hoặc **chọn/tạo callback API khai báo `throws`**; khi cần, vòng lặp `for` thường giúp hợp đồng checked I/O sáng sủa hơn. Sự khác biệt nằm ở **chữ ký của functional interface**, không phải một hạn chế chung của cú pháp lambda Java.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lambda-generic-exceptions-005">Bắt tại lambda, chuyển đổi ngoại lệ hoặc chọn interface riêng</a>

<details>
<summary>Xem chi tiết</summary>

# Bắt tại lambda, chuyển đổi ngoại lệ hoặc chọn interface riêng trong Java

Lambda giúp truyền hành vi dưới dạng một giá trị, nhưng không xóa bỏ cơ chế **checked exception** của Java. Một lời gọi trong thân lambda có thể ném `IOException`, `SQLException`, `InterruptedException` hoặc ngoại lệ checked khác. Nó có hợp lệ hay không phụ thuộc vào **functional interface được dùng làm kiểu đích** (*target type*) và mệnh đề `throws` trên phương thức trừu tượng của interface ấy.

Ví dụ, `java.util.function.Function<T, R>` khai báo `R apply(T t)` mà **không có `throws IOException`**. Vì thế, bạn không thể gán trực tiếp lambda gọi một phương thức `throws IOException` cho `Function<String, String>` rồi mong compiler bỏ qua ngoại lệ. Cũng không thể chỉ thêm `throws IOException` vào phương thức bao quanh để sửa mọi trường hợp: **thân lambda phải tương thích với hợp đồng ngoại lệ của chính functional interface**.

Có ba hướng xử lý chính:

1. **Bắt tại lambda:** dùng `try-catch` trong thân lambda khi bạn có thể phục hồi hoặc quyết định kết quả phù hợp tại chỗ.
2. **Chuyển đổi ngoại lệ:** giữ `cause` và bọc checked exception thành một unchecked exception có ý nghĩa, ví dụ `UncheckedIOException` cho lỗi I/O, khi API buộc dùng `Function`/`Consumer` không khai báo `throws`.
3. **Chọn interface riêng có `throws`:** tạo `@FunctionalInterface` như `ThrowingFunction<T,R,E extends Exception>` để giữ checked exception trong chữ ký; phương thức gọi nó phải `catch` hoặc `throws` đúng hợp đồng.

Không có lựa chọn đúng cho mọi trường hợp. Bài học này trình bày cơ chế theo Java 17+, ví dụ độc lập có lệnh biên dịch/chạy và kết quả stdout, cách tích hợp với Stream API, lambda/method reference, Generic `throws E`, `InterruptedException`, lỗi phổ biến và checklist quyết định thiết kế.

## 1. Lambda có một kiểu đích, và kiểu đó xác định ngoại lệ được phép

Một lambda như `name -> read(name)` **không có kiểu độc lập tách khỏi ngữ cảnh**. Nó cần được gán, truyền hoặc ép sang một functional interface phù hợp. *Function type* của interface cho biết tham số, kết quả và các checked exception được phép thoát ra từ hành vi ấy.

Ví dụ về hai interface:

```java
@FunctionalInterface
interface SimpleLoader {
    String load(String key);
}

@FunctionalInterface
interface CheckedLoader {
    String load(String key) throws java.io.IOException;
}
```

Đây là **hai interface minh họa hợp lệ** khi đặt ở phạm vi thích hợp. Cả hai cùng nhận một `String` và trả một `String`, nhưng chúng khác nhau ở **hợp đồng ném ngoại lệ**.

Giả sử có phương thức:

```java
static String read(String key) throws java.io.IOException {
    if ("bad".equals(key)) {
        throw new java.io.IOException("cannot read");
    }
    return "DATA:" + key;
}
```

Khi gán:

```java
CheckedLoader allowed = key -> read(key); // Hợp lệ.
SimpleLoader invalid = key -> read(key);   // Không biên dịch.
```

Đây là **mã đặt trong một lớp có phương thức `read` như trên**, dòng thứ hai được ghi rõ là ví dụ cố ý không hợp lệ. `CheckedLoader.load` công bố `throws IOException` nên lambda được quyền truyền lỗi checked này cho bên gọi. `SimpleLoader.load` không công bố loại checked nào nên lambda buộc phải tự xử lý hoặc chuyển đổi `IOException` trước khi thoát khỏi thân.

### Những interface chuẩn thường gặp

| Functional interface / API | Phương thức trừu tượng | Cho phép checked exception do người dùng khai báo? |
| --- | --- | --- |
| `Runnable` | `void run()` | Không |
| `Supplier<T>` | `T get()` | Không |
| `Consumer<T>` | `void accept(T t)` | Không |
| `Function<T,R>` | `R apply(T t)` | Không |
| `Predicate<T>` | `boolean test(T t)` | Không |
| `BiFunction<T,U,R>` | `R apply(T t,U u)` | Không |
| `Callable<V>` | `V call() throws Exception` | Có; giao diện cho phép `Exception` |
| `ThrowingFunction<T,R,E>` tự tạo | `R apply(T t) throws E` | Có; kiểu `E` được ràng buộc phù hợp |

Cột cuối nói về **ngoại lệ checked được phép truyền qua hợp đồng**, không có nghĩa các interface không khai báo `throws` sẽ không bao giờ ném bất kỳ lỗi nào. `RuntimeException` và `Error` là unchecked nên vẫn có thể xuất hiện lúc chạy.

## 2. Lỗi biên dịch khi gọi phương thức có `throws` trong lambda

Mã sau **cố ý không biên dịch**:

```java
import java.io.IOException;
import java.util.function.Function;

class InvalidLambda {
    static String load(String key) throws IOException {
        throw new IOException("file unavailable");
    }

    static void example() {
        Function<String, String> reader = key -> load(key);
        // Lỗi: IOException checked không được xử lý trong lambda.
    }
}
```

Lý do **không phải** `Function` không thể biểu diễn một thao tác có lỗi, mà vì `Function.apply` **không có `throws IOException`**. Compiler kiểm tra exception analysis cho **thân lambda theo function type đích** và từ chối checked exception có thể đi ra ngoài mà không được cho phép.

Thay chữ ký bên ngoài thành `static void example() throws IOException` **không sửa** tính không tương thích giữa thân lambda và `Function.apply`. Phần thân lambda không được chạy ngay khi khai báo biến; nó sẽ thực hiện khi `reader.apply(...)` được gọi về sau, có thể ở nơi hoàn toàn khác. Bởi vậy, Java không thể dùng `throws` của phương thức **tạo lambda** để thay cho `throws` của phương thức **functional interface**.

Cùng quy tắc áp dụng cho method reference:

```java
Function<String, String> reader = InvalidLambda::load;
// Cũng không biên dịch, vì load(String) throws IOException.
```

Method reference không phải “cách né checked exception”; nó phải tương thích với function type đích như lambda.

## 3. Hướng 1: bắt ngoại lệ ngay trong lambda

Nếu lỗi có thể được xử lý **đúng và đủ** ngay tại nơi chạy lambda, một `try-catch` trong thân là cách trực tiếp nhất. Ví dụ một tác vụ chấp nhận sự kiện được phép ghi nhận lỗi của từng sự kiện rồi tiếp tục nhận các sự kiện độc lập.

Lưu thành `LocalCatchDemo.java`:

```java
import java.io.IOException;
import java.util.function.Consumer;

public class LocalCatchDemo {
    static String load(String id) throws IOException {
        if ("bad".equals(id)) {
            throw new IOException("source unavailable");
        }
        return "data:" + id;
    }

    public static void main(String[] args) {
        Consumer<String> consumer = id -> {
            try {
                System.out.println("accepted=" + load(id));
            } catch (IOException ex) {
                System.out.println("error=" + id + ":" + ex.getMessage());
            }
        };

        consumer.accept("ok");
        consumer.accept("bad");
        consumer.accept("next");
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 LocalCatchDemo.java
java LocalCatchDemo
```

Kết quả:

```text
accepted=data:ok
error=bad:source unavailable
accepted=data:next
```

Giải thích:

- `Consumer.accept` không khai báo checked exceptions. Nhưng **bản thân lambda đã bắt `IOException`**, không để lỗi checked ấy đi ra ngoài.
- Lần gọi với `bad` đi vào `catch`, in lỗi rồi kết thúc **lần gọi `accept("bad")` bình thường**. Vì `main` tiếp tục gọi `accept("next")`, ví dụ in được dòng cuối.
- Cách này chỉ đúng khi chính sách nghiệp vụ thực sự cho phép xử lý lỗi mỗi mục **tại chỗ**. Với thao tác tài chính hoặc cập nhật trạng thái phải thành công toàn bộ, việc chỉ in lỗi rồi coi đã xử lý có thể rất nguy hiểm.
- `catch` trong lambda không ảnh hưởng ngoại lệ được ném từ một lần gọi `accept` **khác** hoặc các tác vụ chạy ở thread khác.

**Đừng dùng `catch(Exception ex) { }` rỗng** để compiler chấp nhận lambda. Cách đó che khuất lỗi, khiến dữ liệu sai hoặc mất mà bên gọi không biết.

## 4. Với `Function<T,R>`, một `catch` phải quyết định giá trị trả về

Khác `Consumer.accept` trả `void`, `Function.apply` phải trả một giá trị `R` hoặc kết thúc đột ngột. Nếu bắt lỗi nhưng vẫn muốn trả giá trị, hãy chọn một **fallback hợp lệ theo hợp đồng**, không tự động trả `null` hoặc chuỗi rỗng.

Mẫu đặt trong một phương thức:

```java
Function<String, String> safeReader = key -> {
    try {
        return readChecked(key);
    } catch (IOException ex) {
        return "verified-cache:" + key;
    }
};
```

Đây là **mẫu minh họa**, giả định `readChecked(String) throws IOException` đã được định nghĩa và kết quả `verified-cache` thật sự là một giá trị dự phòng hợp lệ. Không phải mọi lỗi đọc đều phù hợp với fallback: cache có thể cũ, không được phép sử dụng hoặc làm vi phạm tính nhất quán.

Nếu chính sách đòi **không được tiếp tục**, đừng trả giá trị giả. Khi đó chuyển đổi thành unchecked exception với cause hoặc dùng một interface có `throws` sẽ làm trách nhiệm của caller rõ ràng hơn.

Một cách khác là trả `Optional<R>` hay một `Result<R,E>` để biểu thị thất bại như dữ liệu. Chỉ dùng lựa chọn đó khi nó phù hợp API và thông tin lỗi cần được giữ; `Optional.empty()` có thể không đủ nếu bên gọi cần biết **vì sao** đã thất bại.

## 5. Hướng 2: bọc `IOException` thành `UncheckedIOException`

Khi một API chỉ nhận `Function` hoặc `Consumer`, nhưng bạn muốn **dừng xử lý và báo lỗi cho tầng gọi**, có thể bọc ngoại lệ I/O trong `java.io.UncheckedIOException`.

`UncheckedIOException extends RuntimeException` và nhận `IOException` làm **cause**. Đây là kiểu phù hợp với lỗi I/O, không phải wrapper tổng quát cho mọi checked exception khác.

Lưu thành `UncheckedStreamDemo.java`:

```java
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Stream;

public class UncheckedStreamDemo {
    static String read(String key) throws IOException {
        if ("bad".equals(key)) {
            throw new IOException("read failed");
        }
        return key.toUpperCase(Locale.ROOT);
    }

    public static void main(String[] args) {
        Function<String, String> adapter = key -> {
            try {
                return read(key);
            } catch (IOException ex) {
                throw new UncheckedIOException(
                        "cannot load " + key, ex);
            }
        };

        Stream<String> pipeline =
                Stream.of("one", "bad", "two").map(adapter);

        System.out.println("pipeline created");

        try {
            pipeline.forEach(value ->
                    System.out.println("value=" + value));
        } catch (UncheckedIOException ex) {
            System.out.println("wrapper=" + ex.getMessage());
            System.out.println("cause=" + ex.getCause().getMessage());
        }
    }
}
```

```bash
javac -encoding UTF-8 UncheckedStreamDemo.java
java UncheckedStreamDemo
```

Kết quả với stream tuần tự trong ví dụ:

```text
pipeline created
value=ONE
wrapper=cannot load bad
cause=read failed
```

Vì `Stream.map` là thao tác **lazy**, việc tạo pipeline chưa thực thi `adapter.apply`. Lỗi chỉ phát sinh khi gọi terminal operation `forEach`.

Dòng `value=TWO` không được in, vì khi tới `bad`, lambda ném `UncheckedIOException` và lần đánh giá stream tuần tự này kết thúc bằng ngoại lệ. Đừng dùng đầu ra này để khẳng định rằng **mọi parallel stream** hoặc pipeline có thao tác khác sẽ xử lý/chấm dứt theo thứ tự y hệt; thứ tự xử lý và tác dụng phụ cần xét riêng.

Bộ xử lý ngoài terminal operation nhận `UncheckedIOException`. Nó có thể xem `ex.getCause()` để tìm đúng `IOException("read failed")`. **Cause không mất**, khác với `throw new RuntimeException(ex.getMessage())` chỉ sao chép một chuỗi.

## 6. Khi chuyển đổi checked sang unchecked, cần thiết kế ranh giới xử lý

Đổi loại ngoại lệ chỉ để “né compiler” thường là lựa chọn kém. Quyết định hợp lệ khi **hợp đồng API** buộc callback không khai báo checked exception nhưng ứng dụng vẫn muốn phân loại thất bại tại một ranh giới cao hơn.

| Nguồn lỗi | Wrapper có thể phù hợp | Lưu ý |
| --- | --- | --- |
| `IOException` khi đọc tệp, mạng hoặc stream | `UncheckedIOException(message, cause)` | Chỉ nhận `IOException` không `null` |
| `SQLException` từ repository | Custom `DataAccessFailure extends RuntimeException` | Truyền `SQLException` làm cause; không đổi thành `UncheckedIOException` |
| Checked exception nghiệp vụ | Custom unchecked exception **nếu hợp đồng cho phép** | Không làm mất ý nghĩa phục hồi của lỗi |
| `InterruptedException` | Thường **truyền lên hoặc khôi phục cờ interrupt và kết thúc tác vụ** | Không xử lý như lỗi thông thường rồi tiếp tục |
| Bất kỳ `RuntimeException` có sẵn | Có thể cho lan truyền | Không cần bọc lại chỉ vì ở lambda |

Ví dụ custom wrapper:

```java
class DataAccessFailure extends RuntimeException {
    DataAccessFailure(String message, Throwable cause) {
        super(message, cause);
    }
}
```

Đây là lớp minh họa hợp lệ không có `main` độc lập. Khi catch lỗi SQL:

```java
catch (java.sql.SQLException ex) {
    throw new DataAccessFailure("Cannot load orders", ex);
}
```

Nhánh catch này là **đoạn minh họa trong thân lambda hoặc phương thức** có thể phát sinh `SQLException`. Bằng cách truyền `ex` làm cause, bên gọi giữ được loại lỗi gốc, stack trace và các suppressed exceptions nằm trên đối tượng cause.

**Không nên** dùng `new UncheckedIOException(new IOException(sqlException))` chỉ để ép một lỗi SQL thành lỗi I/O: tạo sai ngữ nghĩa chẩn đoán và gây hiểu nhầm cho logic xử lý phía trên.

## 7. Hướng 3: tự tạo functional interface có `throws E`

Khi bạn **kiểm soát API gọi callback**, một interface riêng thường tốt hơn việc buộc mọi lambda phải bọc lỗi. Có thể khai báo:

```java
@FunctionalInterface
interface ThrowingFunction<T, R, E extends Exception> {
    R apply(T value) throws E;
}
```

Đây là một functional interface hợp lệ:

- `T`: kiểu đầu vào;
- `R`: kiểu kết quả;
- `E extends Exception`: loại checked (hoặc unchecked thuộc nhánh Exception) mà callback có thể ném;
- `apply(T) throws E`: chữ ký cho phép checked exception `E` đi ra ngoài.

### Chương trình Java 17+: giữ `IOException` qua interface riêng

Lưu thành `CheckedFunctionDemo.java`:

```java
import java.io.IOException;

public class CheckedFunctionDemo {
    @FunctionalInterface
    interface ThrowingFunction<T, R, E extends Exception> {
        R apply(T input) throws E;
    }

    static String read(String key) throws IOException {
        if ("bad".equals(key)) {
            throw new IOException("file unavailable");
        }
        return "data:" + key;
    }

    static <T, R, E extends Exception> R invoke(
            T input, ThrowingFunction<T, R, E> function) throws E {
        return function.apply(input);
    }

    public static void main(String[] args) {
        ThrowingFunction<String, String, IOException> loader =
                CheckedFunctionDemo::read;

        try {
            System.out.println("value=" + invoke("ok", loader));
            invoke("bad", loader);
        } catch (IOException ex) {
            System.out.println("caught=" + ex.getMessage());
        }
    }
}
```

```bash
javac -encoding UTF-8 CheckedFunctionDemo.java
java CheckedFunctionDemo
```

Kết quả:

```text
value=data:ok
caught=file unavailable
```

Phân tích:

1. Method reference `CheckedFunctionDemo::read` tương thích với `ThrowingFunction<String,String,IOException>` vì `apply` được phép ném IOException.
2. Phương thức generic `invoke` công bố `throws E` nên cho phép truyền nguyên đối tượng ngoại lệ ra ngoài.
3. `main` bắt `IOException` cụ thể. Không cần tạo một wrapper unchecked mới.
4. Trong ví dụ, `loader` được truyền dưới dạng interface riêng, **không phải `Function<String,String>`**; dù giống tham số/kết quả, hai hợp đồng exception không tự đồng nhất.

Đây là lựa chọn phù hợp cho callback trong thư viện nội bộ, batch processing hoặc workflow mà bên gọi thực sự có trách nhiệm xử lý checked exception.

## 8. Dùng interface riêng với Stream API: không tự động tương thích

Một hiểu lầm thường gặp:

```java
ThrowingFunction<String, String, IOException> loader =
        CheckedFunctionDemo::read;

// KHÔNG HỢP LỆ:
// Stream.of("a", "b").map(loader);
```

Đây là đoạn minh họa, giả sử `ThrowingFunction` đã được khai báo. Lý do không phải lỗi type erasure mà là **`Stream.map` yêu cầu `Function<? super T, ? extends R>`**, không yêu cầu một `ThrowingFunction` tự tạo. Hai interface không cùng kiểu, và bản thân `Function.apply` cũng không công bố `throws IOException`.

Khi bạn **không kiểm soát chữ ký của API chuẩn**, hãy chọn:

- **Adapter** từ interface ném checked sang `Function` bằng một chính sách bọc/biểu diễn lỗi minh bạch.
- **Đổi quy trình xử lý** sang phương thức nhận `ThrowingFunction` nếu có quyền thiết kế API.
- **Trả kết quả biểu diễn lỗi** cho từng phần tử, nếu chính sách không muốn dừng cả pipeline.

Không cố ép kiểu `(Function<String,String>) loader`: hai interface độc lập không tự tương thích và phép cast không sửa mệnh đề `throws` của phương thức chức năng.

## 9. Adapter tái sử dụng: từ `IOFunction` đến `Function`

Nếu nhiều chỗ trong dự án cần dùng Stream với thao tác I/O, có thể gom logic bọc vào một adapter thay vì viết `try-catch` trùng trong từng lambda.

Lưu thành `IoAdapterDemo.java`:

```java
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Function;
import java.util.stream.Stream;

public class IoAdapterDemo {
    @FunctionalInterface
    interface IOFunction<T, R> {
        R apply(T value) throws IOException;
    }

    static <T, R> Function<T, R> unchecked(
            IOFunction<T, R> checked) {
        return value -> {
            try {
                return checked.apply(value);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        };
    }

    static String read(String key) throws IOException {
        if ("bad".equals(key)) {
            throw new IOException("cannot read " + key);
        }
        return "loaded:" + key;
    }

    public static void main(String[] args) {
        Function<String, String> loader =
                unchecked(IoAdapterDemo::read);

        try {
            Stream.of("a", "bad", "c")
                    .map(loader)
                    .forEach(x -> System.out.println("value=" + x));
        } catch (UncheckedIOException ex) {
            System.out.println("io=" + ex.getCause().getMessage());
        }
    }
}
```

```bash
javac -encoding UTF-8 IoAdapterDemo.java
java IoAdapterDemo
```

Kết quả cho pipeline tuần tự:

```text
value=loaded:a
io=cannot read bad
```

Ở đây, callback mà adapter **nhận** có hợp đồng `throws IOException`; callback mà adapter **trả về** là `Function<T,R>` không khai báo checked exception. Vì vậy bước bọc được thực hiện **tại đúng ranh giới chuyển kiểu**.

Chính sách của adapter là **fail fast**: lỗi từ một phần tử ngăn pipeline tuần tự hoàn thành bình thường. Nếu bạn cần gom tất cả lỗi để báo cáo sau, không sử dụng adapter fail-fast này mà hãy thiết kế `Result` hoặc một bộ thu thập lỗi riêng.

**Giới hạn:** `IOFunction` và adapter trên chỉ xử lý `IOException`. Với `SQLException` hoặc checked exception khác, tạo adapter phù hợp với hợp đồng lỗi của module; không bắt `Exception` rộng rồi tự động bọc mọi thứ theo cùng một cách.

## 10. Dùng `Result` để giữ cả dữ liệu thành công lẫn lỗi của từng phần tử

Khi xử lý nhiều bản ghi độc lập, đôi lúc yêu cầu là **không dừng toàn bộ batch** khi một bản ghi lỗi. Bắt lỗi rồi chuyển mỗi kết quả thành một giá trị có trạng thái sẽ phù hợp hơn so với bọc lỗi và dừng stream.

Lưu thành `ResultPipelineDemo.java`:

```java
import java.io.IOException;
import java.util.stream.Stream;

public class ResultPipelineDemo {
    record Outcome(String key, String value, IOException error) {
        boolean success() {
            return error == null;
        }
    }

    static String read(String key) throws IOException {
        if ("bad".equals(key)) {
            throw new IOException("disk unavailable");
        }
        return "DATA-" + key;
    }

    public static void main(String[] args) {
        Stream.of("a", "bad", "c")
                .map(key -> {
                    try {
                        return new Outcome(key, read(key), null);
                    } catch (IOException ex) {
                        return new Outcome(key, null, ex);
                    }
                })
                .forEach(result -> {
                    if (result.success()) {
                        System.out.println("ok=" + result.key()
                                + ":" + result.value());
                    } else {
                        System.out.println("fail=" + result.key()
                                + ":" + result.error().getMessage());
                    }
                });
    }
}
```

```bash
javac -encoding UTF-8 ResultPipelineDemo.java
java ResultPipelineDemo
```

Kết quả:

```text
ok=a:DATA-a
fail=bad:disk unavailable
ok=c:DATA-c
```

Lúc này, lambda của `Stream.map` **không ném checked exception ra ngoài**; nó luôn trả một `Outcome` cho những trường hợp được xử lý. Kiểu `Outcome` chứa tham chiếu `IOException` gốc, nên có thể tiếp tục truy vấn stack trace, cause và suppressed nếu cần.

`record` là tính năng chuẩn ở Java 17, vì vậy ví dụ không cần thư viện Result bên ngoài. Trong hệ thống sản xuất, nên thiết kế bất biến rõ ràng: mỗi outcome phải là **thành công hoặc thất bại**, không đồng thời cả hai; có thể dùng sealed hierarchy hoặc factory methods thay cho constructor record công khai khi cần.

Ưu điểm của cách này là biểu diễn lỗi **như một phần kết quả**. Đổi lại, các tầng sau phải chủ động kiểm tra trạng thái; nếu bỏ qua `error`, sự cố vẫn có thể bị “nuốt” ở tầng nghiệp vụ.

## 11. `InterruptedException` trong lambda cần chính sách riêng

`InterruptedException` biểu thị tín hiệu phối hợp gián đoạn/hủy trong nhiều API như `Thread.sleep(...)`. Không nên coi nó như `IOException` rồi chỉ log hoặc bọc thành unchecked để cho tác vụ tiếp tục bình thường.

Khi interface không khai báo `throws InterruptedException`, hai hướng thường hợp lý:

- Nếu có thể **thay hợp đồng interface**, chọn functional interface có `throws InterruptedException` và truyền tín hiệu cho tầng điều phối tác vụ.
- Nếu buộc dùng `Runnable`, bắt `InterruptedException`, **khôi phục cờ interrupt** bằng `Thread.currentThread().interrupt()` khi API đã xóa cờ, rồi kết thúc thao tác hoặc thực hiện quy trình hủy phù hợp.

Chương trình Java 17+ minh họa, lưu thành `InterruptLambdaDemo.java`:

```java
public class InterruptLambdaDemo {
    public static void main(String[] args)
            throws InterruptedException {

        Runnable task = () -> {
            try {
                Thread.sleep(10_000);
                System.out.println("completed sleep");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                System.out.println("interruptFlag="
                        + Thread.currentThread().isInterrupted());
            }
        };

        Thread worker = new Thread(task);
        worker.start();
        worker.interrupt();
        worker.join();

        System.out.println("finished=" + !worker.isAlive());
    }
}
```

```bash
javac -encoding UTF-8 InterruptLambdaDemo.java
java InterruptLambdaDemo
```

Kết quả trong điều kiện chạy bình thường:

```text
interruptFlag=true
finished=true
```

Nếu tín hiệu interrupt tới **trước khi thread bắt đầu `sleep`**, trạng thái interrupt đã được đặt và `Thread.sleep` ném `InterruptedException`; nếu tới **trong lúc sleep**, nó đánh thức thao tác bằng ngoại lệ đó. Hai đường đều vào nhánh catch, cờ được đặt lại và `Runnable.run()` kết thúc. `join()` làm main chờ worker kết thúc trước khi in dòng cuối.

Ví dụ cố ý điều phối worker theo một lần interrupt rõ ràng, **không minh họa một hệ thống hủy tác vụ hoàn chỉnh**. Trong mã thật, nên xem chính sách dừng thread, dọn dẹp tài nguyên, xử lý gián đoạn khi chờ và việc shutdown của executor.

**Không ném `UncheckedIOException` cho `InterruptedException`.** Ngoài việc không đúng loại tham số, nó còn sai ngữ nghĩa về nguyên nhân/hợp đồng I/O.

## 12. `Callable` và `Runnable` khác nhau về hợp đồng checked exception

Không nhất thiết phải tự tạo functional interface trong mọi trường hợp. Thư viện chuẩn đã có `java.util.concurrent.Callable<V>`:

```java
@FunctionalInterface
public interface Callable<V> {
    V call() throws Exception;
}
```

Đây là bản lược giản chữ ký API để học. `Callable.call()` cho phép checked exception, còn `Runnable.run()` thì không. Do đó một lời gọi ném `IOException` có thể phù hợp với target type `Callable<String>`:

```java
Callable<String> task = () -> readChecked("profile");
```

Đoạn này minh họa trong phương thức đã import `Callable`, với `readChecked(String) throws IOException`. Nếu tự gọi `task.call()`, chữ ký là `throws Exception` nên bên gọi phải bắt hoặc khai báo `Exception` phù hợp.

Nếu nộp `Callable` vào `ExecutorService.submit(...)`, checked exception do task ném thường được quan sát tại `Future.get()` thông qua `ExecutionException` với cause là lỗi từ task. **Không nên giả định `Future.get()` sẽ ném trực tiếp chính `IOException` theo chữ ký**. `Future.get()` còn có thể ném `InterruptedException` khi thread chờ bị ngắt.

Ngược lại, `CompletableFuture.supplyAsync(...)` nhận `Supplier<T>` không có checked `throws`. Vì vậy, đổi từ `Runnable` sang `supplyAsync` không tự giải quyết checked exception của lambda. Tùy API, bạn cần adapter/bọc lỗi hoặc thiết kế cơ chế báo lỗi khác. Những wrapper bất đồng bộ có thể làm xuất hiện `CompletionException` tại điểm truy cập kết quả; nên kiểm tra cause chain chứ không đoán tất cả lỗi sẽ lộ ra cùng một kiểu.

## 13. Method reference phải theo cùng quy tắc với lambda

Giả sử:

```java
static String read(String key) throws IOException { /* ... */ }
```

Khi đó:

```java
ThrowingFunction<String, String, IOException> ok = MyClass::read;

// Function<String, String> invalid = MyClass::read;
```

Đây là **mẫu minh họa** với tên lớp `MyClass`, `ThrowingFunction` đã khai báo và import phù hợp. Lần gán thứ nhất được phép vì interface nhận checked `IOException`. Nếu bỏ comment ở dòng thứ hai, nó sẽ không biên dịch vì `Function.apply` không công bố IOException.

Method reference hữu ích để viết ngắn nhưng **không làm mất checked exception**. Nếu có method overload, compiler còn xét overload resolution theo target type của method reference; việc đổi kiểu target có thể ảnh hưởng phương thức cụ thể được tham chiếu. Cần kiểm tra chữ ký thực tế thay vì giả định mọi `MyClass::read` luôn tương ứng một phương thức.

Trong chương trình `CheckedFunctionDemo`, method reference `CheckedFunctionDemo::read` đã được dùng trong một ví dụ hoàn chỉnh có `throws IOException`.

## 14. Generics giúp giữ loại checked exception, nhưng không tạo generic exception class

Khai báo:

```java
@FunctionalInterface
interface ThrowingFunction<T, R, E extends Exception> {
    R apply(T value) throws E;
}
```

là **hoàn toàn hợp lệ**. Interface generic có tham số `E` để biểu diễn kiểu ngoại lệ được phép ném, nhưng **chính interface này không kế thừa `Throwable`**.

Điểm cần phân biệt:

- `<E extends Exception> ... throws E` trong phương thức generic là hợp lệ theo JLS §8.4.6.
- `catch (E ex)` với E là type variable không hợp lệ; type variable không được dùng làm loại exception parameter.
- `class GenericFailure<E> extends Exception` không hợp lệ; Java cấm generic class kế thừa trực tiếp hoặc gián tiếp `Throwable`.
- `ThrowingFunction<T,R,E>` có thể ném một đối tượng của lớp ngoại lệ **không generic** như `IOException`, `SQLException` hoặc custom `DataLoadException`.

Nếu cần một callback có thể ném **nhiều loại checked exception độc lập** trong cùng một lần thực thi, một tham số `E` đôi khi làm việc suy luận kiểu kém chính xác hoặc buộc chọn lớp cha như `Exception`. Có thể định nghĩa một hợp đồng `throws IOException, SQLException` rõ ràng khi hai loại là phần ổn định của API, hoặc dùng thiết kế dựa trên result type. Không tự động ép mọi callback thành `throws Exception` nếu bên gọi cần biết chính xác điều gì có thể xảy ra.

## 15. Ba chính sách khác nhau khi một phần tử bị lỗi

Với một batch gồm các khóa `a, bad, c`, cùng một lỗi đọc có thể dẫn tới ba kết quả khác nhau:

| Chính sách | Cách biểu diễn | Tác dụng |
| --- | --- | --- |
| **Continue after handling** | Bắt trong lambda, ghi nhận hợp lệ rồi tiếp tục | Xử lý mục tiếp theo; lỗi phải được ghi nhận theo hợp đồng |
| **Fail fast** | Chuyển thành unchecked exception có cause | Dừng đường xử lý hiện tại khi exception lan ra; tầng cao nhận lỗi |
| **Accumulate per-item failures** | Trả `Result`/`Outcome` | Vẫn tạo kết quả cho các mục độc lập; bên gọi phân loại lỗi từng mục |
| **Propagate checked** | Functional interface có `throws E` | Tầng gọi bắt/khai báo E; thường dùng khi kiểm soát API |
| **Cancellation** | Truyền `InterruptedException` hoặc khôi phục interrupt và kết thúc | Thông báo yêu cầu dừng công việc, không giả vờ thành công |

Đừng dựa vào cấu trúc `try-catch` để quyết định hoàn toàn chính sách nghiệp vụ. Bắt lỗi là **cơ chế ngôn ngữ**; tiếp tục xử lý, thử lại, bỏ qua, bọc lỗi, trả result hay hủy tác vụ là **quyết định của ứng dụng**.

Ví dụ, nếu mỗi phần tử ghi dữ liệu lên cơ sở dữ liệu và các phần tử cùng nằm trong một giao dịch, việc bắt `SQLException` rồi tiếp tục xử lý có thể làm trạng thái giao dịch không nhất quán. Nếu chúng là các phép đọc độc lập có thể báo lỗi từng mục, `Result` lại có thể phù hợp.

## 16. Không nên bắt rộng và vô tình nuốt lỗi runtime

Khi viết adapter tổng quát, cách sau có vấn đề:

```java
Function<String, String> reader = key -> {
    try {
        return readChecked(key);
    } catch (Exception ex) {
        return "";
    }
};
```

Đây là mẫu **cần tránh**. `catch (Exception)` bắt cả nhiều `RuntimeException` không liên quan như `NullPointerException` hoặc `IllegalStateException`, sau đó thay mọi lỗi bằng chuỗi rỗng. Bên gọi không thể phân biệt một chuỗi rỗng hợp lệ với một lỗi nghiêm trọng.

Nếu chính sách cần xử lý `IOException`, **bắt đúng `IOException`**:

```java
Function<String, String> reader = key -> {
    try {
        return readChecked(key);
    } catch (IOException ex) {
        throw new UncheckedIOException(
                "Reading key failed: " + key, ex);
    }
};
```

Đây là đoạn minh họa khi `readChecked(String) throws IOException` tồn tại, và import được khai báo. Việc giữ cause giúp chẩn đoán nơi lỗi phát sinh. Nếu `key` chứa dữ liệu nhạy cảm, đừng đưa nó thẳng vào message/log.

Một adapter `catch(Exception) { throw new RuntimeException(ex); }` tổng quát có thể che giấu cả `InterruptedException` lẫn lỗi runtime vốn không cần bọc. Chỉ dùng nếu API thực sự muốn mọi lỗi checked được chuyển theo chính sách đã công bố, và thiết kế riêng đường hủy/gián đoạn.

## 17. Mỗi lambda có phạm vi `try-catch` riêng

Đặt `try-catch` quanh câu lệnh **tạo lambda** không có nghĩa nó bao quanh lần thực thi về sau:

```java
Function<String, String> reader;

try {
    reader = key -> readChecked(key); // Vẫn lỗi biên dịch.
} catch (RuntimeException ex) {
    // Không phải nơi xử lý IOException checked từ thân lambda.
}
```

Đây là ví dụ **cố ý không biên dịch**, giả định `readChecked` ném `IOException`. Checked exception trong thân lambda không tự lan ra khi tạo lambda, nên `try-catch` quanh chỗ gán không thay được hợp đồng của `Function.apply`.

Nếu lambda bọc checked exception thành unchecked, caller có thể bắt tại lúc **kích hoạt** callback:

```java
Function<String, String> reader = key -> {
    try {
        return readChecked(key);
    } catch (IOException ex) {
        throw new UncheckedIOException(ex);
    }
};

try {
    System.out.println(reader.apply("bad"));
} catch (UncheckedIOException ex) {
    System.out.println("failure=" + ex.getCause().getMessage());
}
```

Đây là hai đoạn minh họa trong thân phương thức; import và `readChecked` cần được định nghĩa. Ở Stream API, điểm kích hoạt thường là terminal operation; với `ExecutorService`, callback có thể chạy trong worker thread và ngoại lệ được biểu diễn qua `Future`.

**Phải phân biệt thời điểm khai báo lambda và thời điểm callback được gọi.** Nếu không, bạn có thể bắt lỗi ở một phạm vi không bao giờ chứng kiến ngoại lệ đó.

## 18. Ưu tiên thiết kế API rõ hợp đồng hơn “sneaky throw”

Một số đoạn mã dùng generic cast hoặc mẹo kiểu để ném checked exception **như thể** unchecked, thường được gọi là *sneaky throw*. Dù có thể tồn tại các kỹ thuật như vậy, chúng làm hợp đồng khó hiểu: tầng gọi không nhìn thấy loại lỗi thật trong `throws`, trong khi ngoại lệ checked vẫn có thể xuất hiện tại runtime.

Với mục tiêu học và xây thư viện dễ bảo trì, nên chọn một trong ba cơ chế **hiển thị rõ**:

- Một `try-catch` chủ động phục hồi ngay trong lambda.
- Một wrapper unchecked đúng ý nghĩa có cause, ví dụ `UncheckedIOException` hoặc custom domain runtime exception.
- Một functional interface riêng với `throws E` và caller quản lý checked exception.

Khi cần tiếp tục batch, một `Outcome`/`Result` biểu diễn thất bại cũng minh bạch hơn việc giấu ngoại lệ sau các cast khó đọc. Sử dụng `throws Exception` một cách quá rộng hoặc “giấu” checked exception không giúp mô hình lỗi của ứng dụng chính xác hơn.

## 19. Những lỗi thường gặp

| Sai lầm | Hậu quả | Hướng sửa |
| --- | --- | --- |
| Gán lambda gọi `throws IOException` cho `Function<T,R>` mà không catch | Không biên dịch | Bắt trong lambda, adapter/bọc, hoặc interface có `throws` |
| Thêm `throws IOException` vào phương thức *tạo* lambda | Không sửa hợp đồng `Function.apply` | Xử lý theo function type, không theo vị trí gán |
| Đổi sang method reference `Class::read` để “né checked” | Vẫn phải tương thích với target type | Dùng target interface cho phép `throws` |
| Bắt `Exception` rồi trả `null`/chuỗi rỗng cho mọi lỗi | Che sự cố và vi phạm hợp đồng dữ liệu | Bắt loại đúng, xác định fallback hợp lệ |
| Bọc `IOException` bằng runtime wrapper mà bỏ cause | Mất đường chẩn đoán | Truyền `ex` làm cause |
| Dùng `UncheckedIOException` cho `SQLException` | Sai hợp đồng loại lỗi | Tạo domain exception hoặc wrapper phù hợp |
| Trả `Optional.empty()` dù caller cần lý do thất bại | Mất loại lỗi, message, stack trace | Trả Result/Outcome có error |
| Dùng `ThrowingFunction` trực tiếp với `Stream.map` | Không tương thích với `Function` chuẩn | Dùng adapter hoặc API riêng nhận ThrowingFunction |
| Mong `catch(IOException)` quanh việc tạo Stream sẽ bắt lỗi từ `map` | Pipeline lazy; lỗi khi thực thi terminal operation | Đặt xử lý ở đúng điểm thực thi |
| Catch `InterruptedException` rồi tiếp tục im lặng | Có thể làm mất tín hiệu hủy | Truyền interrupt hoặc restore flag và kết thúc |
| Bọc lại mọi `RuntimeException` mà không thêm ý nghĩa | Chain dài, mất ý nghĩa loại lỗi | Cho lỗi phù hợp lan truyền |
| Dùng callback trong parallel stream rồi dựa vào side effect tuần tự | Thứ tự/hoàn tất có thể khác giả định | Thiết kế xử lý concurrency/ordering rõ |
| Tạo custom generic exception `MyException<E>` | Java cấm generic subtype của Throwable | Dùng exception không generic; generic ở interface/result |
| Chỉ chạy test happy path | Không kiểm tra chính sách lỗi và cause | Test đường lỗi, dòng output, cause chain, interrupt |

## 20. Checklist chọn hướng xử lý

Trước khi viết lambda gọi một phương thức có `throws`, tự hỏi:

1. **Target type là gì?** `Runnable`/`Function`/`Consumer` không khai báo checked exception, hay `Callable`/interface riêng có cho phép?
2. **Loại ngoại lệ cụ thể nào có thể phát sinh?** `IOException`, `SQLException`, `InterruptedException` hay custom checked exception?
3. **Bạn kiểm soát API nhận lambda không?** Nếu có, cân nhắc interface có `throws` để giữ checked exception nguyên dạng.
4. **Lỗi có thể phục hồi hợp lệ ngay tại phần tử không?** Nếu có, catch cục bộ với fallback/ghi nhận rõ ràng.
5. **Nếu không phục hồi, có cần dừng pipeline/tác vụ không?** Có thể bọc unchecked có cause rồi xử lý ở tầng ranh giới.
6. **Caller cần kết quả cho mọi phần tử?** Nếu có, cân nhắc Result/Outcome thay cho ném lỗi dừng cả batch.
7. **Lỗi có phải interrupt?** Truyền tín hiệu hoặc khôi phục trạng thái interrupt và kết thúc đúng chính sách.
8. **Đường thực thi lambda ở đâu?** Trực tiếp, Stream terminal operation, executor hay callback bất đồng bộ? Đặt catch ở nơi có thể chứng kiến lỗi.
9. **Thông tin gốc còn không?** Dùng `getCause()`, log toàn bộ `Throwable`, giữ suppressed nếu lỗi gốc có.
10. **Kiểm tra compiler và test:** một mã chạy thành công không chứng minh đường checked exception đã đúng; biên dịch với JDK đích và thử cả tình huống thất bại.

### Bảng quyết định nhanh

| Hoàn cảnh | Lựa chọn có xu hướng phù hợp |
| --- | --- |
| Callback được phép bỏ qua một bản ghi lỗi sau khi ghi nhận, các bản ghi độc lập | `try-catch` bên trong lambda |
| `Stream.map` phải dừng khi I/O hỏng và caller chịu trách nhiệm | Bọc `IOException` bằng `UncheckedIOException`, giữ cause |
| Viết API nội bộ nhận callback mà caller cần xử lý checked exception rõ ràng | `ThrowingFunction<T,R,E>` / `ThrowingConsumer<T,E>` |
| Cần báo thành công/thất bại riêng cho từng mục | `Result`/`Outcome` |
| Gọi tác vụ trả kết quả cho executor với checked exception | `Callable<V>` và xử lý `Future.get()`/`ExecutionException` |
| Callback có thể bị interrupt | Hợp đồng `throws InterruptedException` hoặc catch + restore flag và kết thúc |
| Thư viện buộc dùng `Supplier<T>`/`Function<T,R>` | Adapter minh bạch theo policy; không dùng sneaky throw |

## 21. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao `Function<String,String> f = x -> read(x);` không biên dịch nếu `read` khai báo `throws IOException`?  
Vì phương thức `Function.apply` không công bố `IOException`, nên lambda không được phép để checked exception này thoát.

**Câu 2.** Thêm `throws IOException` vào phương thức đang khai báo biến `f` có đủ không?  
**Không.** Exception checking của thân lambda dựa vào function type đích, không phải chữ ký của phương thức tạo lambda.

**Câu 3.** `UncheckedIOException` dùng để bọc loại lỗi nào?  
**`IOException`.** Nó là unchecked wrapper có constructor nhận IOException và cause được giữ.

**Câu 4.** `Stream.map` có chạy callback ngay khi được gọi không?  
**Không.** `map` là thao tác trung gian lazy; callback được đánh giá khi có terminal operation thích hợp.

**Câu 5.** `ThrowingFunction<String,String,IOException>` có thể mang method reference trỏ tới phương thức `throws IOException` không?  
**Có**, vì `apply` công bố `throws IOException`.

**Câu 6.** Có thể truyền trực tiếp `ThrowingFunction` vào `Stream.map` không?  
**Không**, vì `map` yêu cầu `Function` chuẩn; cần adapter hoặc đổi API.

**Câu 7.** Nếu muốn xử lý tiếp các bản ghi sau một lỗi riêng lẻ và giữ nguyên lỗi, có thể dùng cách nào?  
Một `Result`/`Outcome` chứa giá trị thành công hoặc đối tượng ngoại lệ, rồi xử lý từng kết quả.

**Câu 8.** `Callable.call()` và `Runnable.run()` khác nhau về checked exception thế nào?  
`Callable.call() throws Exception`; `Runnable.run()` không khai báo checked exception.

**Câu 9.** Có nên catch `InterruptedException` trong `Runnable` rồi chỉ in log, bỏ qua yêu cầu dừng?  
**Không nên.** Cần truyền lên nếu hợp đồng cho phép, hoặc restore interrupt flag và kết thúc tác vụ đúng chính sách.

**Câu 10.** Bọc checked exception thành unchecked có nghĩa ngoại lệ được “xử lý thành công” chưa?  
**Chưa.** Đó chỉ là **đổi hợp đồng lan truyền**. Tầng tiếp nhận wrapper vẫn phải áp dụng chính sách xử lý thực tế.

## Tài liệu tham khảo

- [JLS Java SE 21 — §15.27.3, Type of a Lambda Expression](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.27.3).
- [JLS Java SE 21 — §11.2.3, Exception Checking](https://docs.oracle.com/javase/specs/jls/se21/html/jls-11.html#jls-11.2.3).
- [JLS Java SE 21 — §8.4.6, Method Throws](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.4.6).
- [Java SE 21 API — `Function<T,R>`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/function/Function.html).
- [Java SE 21 API — `UncheckedIOException`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/UncheckedIOException.html).
- [Java SE 21 API — `Callable<V>`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Callable.html).
- [Java SE 21 API — `Stream<T>`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Stream.html).

**Ghi nhớ:** Khi lambda có thể ném checked exception, hãy nhìn vào **functional interface đích**. Bắt tại chỗ khi có chính sách phục hồi đúng; bọc thành unchecked **có cause** khi API chuẩn không cho truyền checked mà caller cần biết lỗi; hoặc dùng **interface riêng có `throws`** khi bạn kiểm soát API và muốn giữ chính xác hợp đồng checked exception. Với nhiều bản ghi độc lập, có thể dùng Result/Outcome để biểu diễn lỗi như dữ liệu thay vì nuốt lỗi hay buộc toàn bộ pipeline dừng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lambda-generic-exceptions-006">Phương thức generic có tham số kiểu mở rộng Throwable</a>

<details>
<summary>Xem chi tiết</summary>

# Phương thức generic có tham số kiểu mở rộng `Throwable` trong Java

## 1. Vấn đề đặt ra

Java cho phép khai báo một **type parameter** trong phương thức generic với ràng buộc trên là `Throwable`:

```java
static <E extends Throwable> void propagate(E failure) throws E {
    throw failure;
}
```

Ở đây, `E` không phải tên một lớp ngoại lệ cụ thể. Nó là **tham số kiểu** được suy ra hoặc chỉ định khi gọi phương thức. Điều kiện `E extends Throwable` bảo đảm mọi kiểu thực dùng thay `E` là `Throwable` hoặc một lớp con của nó, do đó đối tượng có thể được ném bằng `throw` và xuất hiện trong mệnh đề `throws E`.

Một số tình huống cần tính năng này: helper muốn truyền lại **đúng loại ngoại lệ** của caller; functional interface có phương thức ném checked exception; API callback generic muốn chuyển tiếp ngoại lệ mà không ép mọi người gọi phải xử lý một `Exception` quá rộng.

Tuy nhiên, vì `E` là type variable, Java có những giới hạn quan trọng: không thể `catch (E e)`, không thể `new E()`, và hệ thống kiểm tra checked exception phải dựa trên **ràng buộc kiểu cùng ngữ cảnh suy luận**. Không nên dùng generic `throws E` chỉ như mẹo “qua mặt” checked exception.

Mục tiêu:

- Phân biệt `<E extends Throwable>`, `throws E` và `throw expression`.
- Hiểu ý nghĩa của ràng buộc `Throwable`, `Exception`, `RuntimeException` và các kiểu con cụ thể.
- Tạo phương thức generic giữ kiểu ngoại lệ khi truyền tiếp, với ví dụ checked và unchecked.
- Xây một functional interface có tham số kiểu ngoại lệ để dùng lambda ném checked exception.
- Xác định nghĩa vụ `try/catch` hoặc `throws` ở caller qua kiểu generic đã được suy ra/chỉ định.
- Hiểu tại sao `catch (E e)` và `new E()` không hợp lệ.
- Biết cách dựng ngoại lệ qua factory thay vì tạo trực tiếp từ type variable.
- Nắm giới hạn khi phương thức generic được override, phương thức functional interface được chuyên biệt hóa, và khi dùng `Runnable`/`Function` chuẩn.
- Nhận biết rủi ro của *sneaky throw*, unchecked cast và suy luận type parameter ở `throws`.

Tất cả chương trình hoàn chỉnh bên dưới được thiết kế cho **Java 17+**, có lệnh `javac`/`java` và kết quả `stdout` chính xác. Đoạn không độc lập và ví dụ cố ý sai được ghi rõ.

## 2. Đọc đúng cú pháp generic `throws`

Xét:

```java
public static <E extends Throwable> void rethrow(E error) throws E {
    throw error;
}
```

Hãy tách thành ba thành phần:

| Cú pháp | Ý nghĩa |
| --- | --- |
| `<E extends Throwable>` | Khai báo tham số kiểu `E` với upper bound `Throwable` |
| `E error` | Tham số phương thức có kiểu tĩnh là type variable `E` |
| `throws E` | Khai báo phương thức có thể truyền một ngoại lệ mang kiểu `E` |
| `throw error;` | Thực hiện ném **đối tượng** mà biến `error` đang tham chiếu |

Dấu `extends` trong **type parameter bound** cho phép chọn các lớp con thích hợp của `Throwable`. Nó **không phải** câu lệnh `throw` và cũng không có nghĩa “bắt mọi throwable”.

Ví dụ khi caller truyền một biến có kiểu tĩnh `IOException`:

```java
java.io.IOException failure = new java.io.IOException("disk failed");
rethrow(failure);
```

Đây là mảnh mã đặt trong một phương thức thích hợp. Compiler có thể suy ra `E = IOException` trong lời gọi trên. Vì vậy lời gọi có nghĩa vụ checked exception `IOException`: phải bắt hoặc khai báo nếu đang ở ngữ cảnh yêu cầu.

Nếu caller truyền `new IllegalArgumentException(...)`, kiểu ngoại lệ được truyền là unchecked; không bắt buộc ghi `throws` ở caller.

### Quan trọng: upper bound không biến đối tượng thành một `Throwable` mới

`rethrow(E error)` ném **chính đối tượng** mà nó nhận, không tạo một ngoại lệ khác. Các trường như message, cause, stack trace và suppressed exceptions đang nằm trên đối tượng đó được giữ nguyên. Type parameter chỉ là công cụ kiểm tra kiểu ở compile time.

## 3. `Throwable`, `Exception` và `RuntimeException` trong generic bound

Một cây kiểu rút gọn:

```text
Throwable
├── Error                       [unchecked]
│   └── AssertionError
└── Exception
    ├── IOException             [checked]
    │   └── FileNotFoundException
    ├── SQLException            [checked]
    └── RuntimeException        [unchecked]
        ├── IllegalArgumentException
        └── IllegalStateException
```

Đây không phải toàn bộ hệ phân cấp Java, chỉ các kiểu cần để hiểu ràng buộc.

Các khai báo generic:

```java
static <E extends Throwable> void a(E failure) throws E {
    throw failure;
}

static <E extends Exception> void b(E failure) throws E {
    throw failure;
}

static <E extends RuntimeException> void c(E failure) throws E {
    throw failure;
}

static <E extends java.io.IOException> void d(E failure) throws E {
    throw failure;
}
```

Đây là **bốn phương thức minh họa hợp lệ**, đặt trong một class Java. Chúng không có cùng phạm vi kiểu:

- `E extends Throwable` cho phép `IOException`, `RuntimeException`, `Error` hoặc chính `Throwable` và các lớp con thích hợp.
- `E extends Exception` loại bỏ nhánh `Error`, nhưng **vẫn gồm** checked exception và các subclass của `RuntimeException`.
- `E extends RuntimeException` giới hạn về nhánh unchecked, do đó các ngoại lệ thuộc `E` không tạo nghĩa vụ checked exception ở caller.
- `E extends IOException` giới hạn vào các kiểu checked I/O, như `IOException` và `FileNotFoundException`.

**Không được suy ra “mọi `E extends Exception` đều checked”.** `RuntimeException` là con của `Exception` nhưng unchecked.

Tương tự, **`E extends Throwable` không buộc mọi lời gọi đều phải bắt `Throwable`**. Nghĩa vụ có thể cụ thể hơn khi `E` được suy ra hoặc được chỉ định; nếu kiểu tĩnh của lời gọi đúng là `Throwable`, caller có thể phải xử lý/khai báo phạm vi rộng ấy.

## 4. Chương trình hoàn chỉnh: ném lại đúng loại đã truyền

Tạo tệp **`GenericRethrowDemo.java`**:

```java
import java.io.IOException;

public class GenericRethrowDemo {
    static <E extends Throwable> void rethrow(E failure) throws E {
        throw failure;
    }

    public static void main(String[] args) {
        IOException io = new IOException("disk offline");

        try {
            rethrow(io);
        } catch (IOException failure) {
            System.out.println("checked=" + failure.getMessage());
        }

        try {
            rethrow(new IllegalArgumentException("bad argument"));
        } catch (IllegalArgumentException failure) {
            System.out.println("unchecked=" + failure.getMessage());
        }

        try {
            rethrow(new AssertionError("broken invariant"));
        } catch (AssertionError failure) {
            System.out.println("error=" + failure.getMessage());
        }
    }
}
```

Biên dịch và chạy:

```bash
javac GenericRethrowDemo.java
java GenericRethrowDemo
```

`stdout`:

```text
checked=disk offline
unchecked=bad argument
error=broken invariant
```

Phân tích:

1. Lời gọi `rethrow(io)` có đối số kiểu `IOException`, nên kiểu generic phù hợp là checked exception này. `catch (IOException)` xử lý.
2. `IllegalArgumentException` là unchecked, vẫn được ném đúng đối tượng đó, không bắt buộc khai báo `throws`.
3. `AssertionError` cũng thuộc `Throwable` nhưng qua nhánh `Error` và unchecked. Ví dụ chủ ý bắt ngay tại `main` để minh họa kiểu; ứng dụng thông thường **không nên coi mọi Error là có thể phục hồi**.

Phương thức `rethrow` không tạo ngoại lệ mới, không chuyển checked thành unchecked và không đổi nguyên nhân gốc. Nó đơn thuần cho phép phương thức generic khai báo một loại lỗi gắn với tham số kiểu của chính lời gọi.

### Kiểu tĩnh của đối số ảnh hưởng nghĩa vụ xử lý

Nếu biến được khai báo là `Throwable` mặc dù runtime đang chứa `IOException`:

```java
Throwable failure = new java.io.IOException("disk");
rethrow(failure);
```

Đây là mảnh mã. Compiler nhìn thấy type argument có thể là `Throwable`, nên ngữ cảnh gọi phải xử lý/khai báo kiểu phù hợp với `Throwable`, trừ khi có ngữ cảnh suy luận khác hợp lệ. Java không suy luận chắc chắn `IOException` chỉ dựa trên một đối tượng runtime.

Để giữ thông tin checked exception cụ thể khi API cần, đừng tùy tiện đổi mọi tham số thành `Throwable`. Hợp đồng generic phụ thuộc **kiểu biểu thức tại compile time**.

## 5. Tại sao phải có `throws E` nếu đã có `E extends Throwable`?

Ràng buộc kiểu chỉ chứng minh rằng `E` là loại có thể ném; nó **không tự khai báo** phương thức sẽ truyền checked exception ra ngoài. Ví dụ mã sau là **cố ý không biên dịch**:

```java
static <E extends Throwable> void broken(E failure) {
    throw failure; // Compiler có thể yêu cầu khai báo/bắt E.
}
```

Vì `E` có thể là `IOException` hoặc một checked `Throwable` khác, phương thức không được bỏ qua nghĩa vụ. Cần khai báo:

```java
static <E extends Throwable> void valid(E failure) throws E {
    throw failure;
}
```

Nếu thay `E extends RuntimeException`, ngoại lệ được ném là unchecked:

```java
static <E extends RuntimeException> void validUnchecked(E failure) {
    throw failure;
}
```

Đây là ví dụ hợp lệ về ràng buộc chặt hơn, không bắt buộc có `throws E`. Dù vậy, có thể viết `throws E` để mô tả API; nó không biến exception unchecked thành checked.

### `throws E` và `throws Throwable` không luôn tương đương

`throws Throwable` công bố một loại rất rộng cho mọi caller. `throws E` có thể cung cấp mối liên hệ chính xác giữa kiểu ngoại lệ của tham số hoặc callback và trách nhiệm của caller. Đây là giá trị lớn của generic exception type: **không mở rộng vô cớ hợp đồng lỗi**.

Nhưng khi `E` chỉ bị ràng buộc bởi `Throwable` và không thể suy luận hẹp, compiler có thể vẫn phải kiểm tra một loại ngoại lệ rộng. Generic không bảo đảm hợp đồng luôn hẹp; việc lựa chọn type argument và chữ ký API mới là yếu tố quyết định.

## 6. Lambda có checked exception: cần functional interface phù hợp

Các functional interface phổ biến của Java như `Runnable` hoặc `java.util.function.Function<T,R>` **không khai báo checked exception** trong phương thức abstract của chúng. Vì thế, không thể trực tiếp ném `IOException` không được xử lý từ thân lambda gán vào `Runnable`:

```java
Runnable task = () -> {
    throw new java.io.IOException("cannot read"); // KHÔNG BIÊN DỊCH.
};
```

Đây là ví dụ **cố ý không biên dịch**. `Runnable.run()` không có `throws IOException`.

Một giải pháp thiết kế đúng là khai báo **functional interface của riêng ứng dụng** có type parameter ngoại lệ:

```java
@FunctionalInterface
interface ThrowingAction<E extends Throwable> {
    void run() throws E;
}
```

Sau đó tạo phương thức generic gọi callback:

```java
static <E extends Throwable> void execute(ThrowingAction<E> action)
        throws E {
    action.run();
}
```

Hai mảnh khai báo này hợp lệ. Chúng giữ quan hệ:

```text
ThrowingAction<IOException>
          |
          v
execute(action) throws IOException
          |
          v
caller catch IOException / throws IOException
```

Kiểu `E` không nhất thiết phải luôn là `Throwable`; nếu API chỉ cần checked/unchecked thuộc `Exception` và không muốn nhận `Error` như một lựa chọn thiết kế, có thể chọn `E extends Exception`.

## 7. Chương trình hoàn chỉnh: lambda ném `IOException` qua generic helper

Tạo tệp **`GenericLambdaThrowsDemo.java`**:

```java
import java.io.IOException;

public class GenericLambdaThrowsDemo {
    @FunctionalInterface
    interface ThrowingSupplier<R, E extends Throwable> {
        R get() throws E;
    }

    static <R, E extends Throwable> R supply(
            ThrowingSupplier<R, E> supplier) throws E {
        return supplier.get();
    }

    public static void main(String[] args) {
        ThrowingSupplier<String, IOException> failingRead = () -> {
            throw new IOException("read failed");
        };

        ThrowingSupplier<String, IOException> successfulRead =
                () -> "data";

        try {
            System.out.println(supply(failingRead));
        } catch (IOException failure) {
            System.out.println("failure=" + failure.getMessage());
        }

        try {
            System.out.println("success=" + supply(successfulRead));
        } catch (IOException failure) {
            System.out.println("unexpected IO");
        }
    }
}
```

```bash
javac GenericLambdaThrowsDemo.java
java GenericLambdaThrowsDemo
```

`stdout`:

```text
failure=read failed
success=data
```

Để đầu ra và suy luận rõ ràng, biến `failingRead` và `successfulRead` được **khai báo kiểu tường minh** `ThrowingSupplier<String, IOException>`.

Giải thích:

- `R` biểu diễn kiểu dữ liệu trả về của lambda (`String`).
- `E` biểu diễn **loại ngoại lệ** lambda được phép truyền ra (`IOException`).
- `ThrowingSupplier.get()` khai báo `throws E` nên thân lambda có thể ném `IOException`.
- `supply` khai báo `throws E` và truyền lỗi ra mà không ép kiểu sang `Exception`.
- Caller biết ngữ cảnh là `IOException` nên có thể viết `catch (IOException)`.
- Ngay cả lambda thành công vẫn có **kiểu interface** cho phép `IOException`, vì thế một lời gọi thông qua kiểu ấy phải đáp ứng hợp đồng checked exception. Compiler không dựa vào việc lambda hiện tại chỉ `return "data"` để bỏ nghĩa vụ.

### Có thể gọi helper với lambda trực tiếp không?

Có thể viết:

```java
String value = GenericLambdaThrowsDemo.supply(() -> "ok");
```

Đây là mảnh minh họa trong ngữ cảnh phù hợp. Với lambda trực tiếp, Java có thể dùng ngữ cảnh đích và các ràng buộc suy luận để chọn type arguments. Khi muốn hợp đồng lỗi dễ đọc và ổn định giữa các lần refactor, **biến typed rõ ràng** hoặc type witness (`ClassName.<R,E>method(...)`) có thể tốt hơn dựa hoàn toàn vào suy luận tinh vi.

Không nên đoán rằng Java sẽ luôn suy ra một kiểu checked exception bạn mong muốn trong mọi biểu thức lambda phức tạp; hãy kiểm tra compiler trên chữ ký thực tế.

## 8. Hàm generic nhận nhiều hành động và giữ cùng loại lỗi

Có thể tái sử dụng một type parameter cho nhiều callback cùng hợp đồng:

```java
@FunctionalInterface
interface Step<E extends Exception> {
    void run() throws E;
}

static <E extends Exception> void runTwo(Step<E> first, Step<E> second)
        throws E {
    first.run();
    second.run();
}
```

Đây là mảnh khai báo hợp lệ. `E extends Exception` phù hợp khi API muốn tập trung vào nhóm exception thay vì `Error`. Nếu hai bước đều có kiểu `Step<IOException>`, caller chỉ phải xử lý `IOException`.

Nhưng hai bước có **hai loại checked exception khác nhánh** có thể khiến việc suy luận một `E` chung phải chọn supertype đủ rộng (chẳng hạn `Exception`) hoặc không phù hợp trong ngữ cảnh cụ thể. Khi API thật sự cần giữ hai loại độc lập, hãy công bố **hai tham số kiểu ngoại lệ**:

```java
static <E1 extends Exception, E2 extends Exception> void runTwoDifferent(
        Step<E1> first, Step<E2> second)
        throws E1, E2 {
    first.run();
    second.run();
}
```

Đây là mảnh phương thức hợp lệ (với `Step` đã định nghĩa). Nó biểu diễn rõ ràng: bước thứ nhất được phép ném `E1`, bước thứ hai được phép ném `E2`. Generic có thể diễn đạt **mối quan hệ** giữa exception ở callback và exception của helper, nhưng bạn phải chọn số tham số kiểu phản ánh đúng hợp đồng.

### Chương trình chạy được: hai loại checked exception độc lập

Tạo tệp **`TwoGenericExceptionsDemo.java`**:

```java
import java.io.IOException;
import java.sql.SQLException;

public class TwoGenericExceptionsDemo {
    @FunctionalInterface
    interface Step<E extends Exception> {
        void run() throws E;
    }

    static <E1 extends Exception, E2 extends Exception> void runBoth(
            Step<E1> first, Step<E2> second) throws E1, E2 {
        first.run();
        second.run();
    }

    public static void main(String[] args) {
        Step<IOException> read = () -> {
            System.out.println("read");
        };

        Step<SQLException> query = () -> {
            throw new SQLException("query failed");
        };

        try {
            runBoth(read, query);
        } catch (IOException failure) {
            System.out.println("IO=" + failure.getMessage());
        } catch (SQLException failure) {
            System.out.println("SQL=" + failure.getMessage());
        }
    }
}
```

```bash
javac TwoGenericExceptionsDemo.java
java TwoGenericExceptionsDemo
```

`stdout`:

```text
read
SQL=query failed
```

`runBoth` không bắt hoặc đổi loại lỗi. Nó truyền `E1` và `E2` ra ngoài. Khi `read` chạy thành công rồi `query` ném `SQLException`, caller nhận đúng loại SQL. Nếu `read` thất bại bằng `IOException`, bước `query` sẽ **không chạy**, trừ khi method được thiết kế bắt/điều phối riêng.

Mẫu này hữu ích khi một thư viện callback có nhiều bước **thật sự cần các loại lỗi riêng**. Không nên gộp thành `throws Exception` nếu generic có thể mô tả hợp đồng chính xác và không làm API khó dùng quá mức.

## 9. Ràng buộc `E extends Throwable` vẫn tuân thủ kiểm tra checked exception

`throws E` không phải quyền miễn trừ kiểm tra compile-time. Nếu caller có type argument cụ thể `IOException`, caller phải xử lý checked exception đó.

Ví dụ:

```java
static <E extends Throwable> void fail(E failure) throws E {
    throw failure;
}

static void caller() {
    fail(new java.io.IOException("disk")); // LỖI: chưa catch/throws.
}
```

Đây là mảnh **cố ý không biên dịch** khi đặt trong một class: `caller()` không bắt hoặc khai báo `IOException`.

Sửa bằng:

```java
static void caller() throws java.io.IOException {
    fail(new java.io.IOException("disk"));
}
```

hoặc:

```java
static void caller() {
    try {
        fail(new java.io.IOException("disk"));
    } catch (java.io.IOException failure) {
        System.out.println(failure.getMessage());
    }
}
```

Hai đoạn sửa là mảnh phương thức hợp lệ nếu đặt cạnh định nghĩa `fail` ở trên.

### Checked exception trong lambda được kiểm tra ở đâu?

Với lambda, compiler kiểm tra thân lambda dựa trên **hàm abstract của functional interface** và phần `throws` tương ứng. Khi interface không công bố checked exception, ném `IOException` là sai. Khi interface công bố `throws E` với kiểu phù hợp, lambda có thể ném `IOException`, còn caller của helper generic phải đáp ứng hợp đồng `throws E` đã suy ra.

Điều này giải thích vì sao chỉ viết:

```java
static <E extends Throwable> void invoke(Runnable task) throws E {
    task.run();
}
```

không làm `Runnable` tự cho phép checked exception trong lambda. `Runnable.run()` vẫn **không khai báo checked exception**. Cách viết generic `throws E` ở **method helper** không sửa chữ ký functional method của `Runnable`. Đây là mảnh method hợp lệ về cú pháp nhưng **không tự tạo khả năng checked exception cho Runnable**.

## 10. Không thể `catch (E e)`: type variable không phải kiểu catch hợp lệ

Đoạn sau **cố ý không biên dịch**:

```java
static <E extends Throwable> void badCatch(E value) {
    try {
        throw value;
    } catch (E failure) { // LỖI: không catch bằng type variable.
        System.out.println(failure.getMessage());
    }
}
```

Java không cho dùng type variable `E` làm kiểu tham số `catch`. Lý do liên quan tới type erasure và quy tắc catch phải dùng kiểu ngoại lệ phù hợp tại runtime; type parameter generic không thể được dùng ở vị trí này như một class exception cụ thể.

**Không nhầm:** `throws E` là hợp lệ; `catch (E e)` là không hợp lệ. `throws` là hợp đồng kiểm tra compile-time, còn `catch` phải có kiểu ngoại lệ cho cơ chế matching tại runtime.

### Cách xử lý hợp lệ

Nếu muốn chuyển tiếp nguyên lỗi, không cần `catch`:

```java
static <E extends Throwable> void passThrough(E error) throws E {
    throw error;
}
```

Nếu cần bắt lỗi chung để thêm ngữ cảnh, có thể bắt một kiểu concrete hợp lệ (`Exception` hoặc `Throwable`) và phải **ném lại/bao bọc phù hợp với checked exception của method**:

```java
static <E extends Throwable> void traceAndPass(
        ThrowingAction<E> action) throws E {
    action.run();
}
```

Đây là mảnh method với `ThrowingAction` từ phần trước, thể hiện cách không cần bắt chỉ để truyền lỗi. Nếu muốn xử lý lỗi theo **một loại cụ thể**, hãy dùng một class exception cụ thể tại ranh giới biết rõ loại đó, ví dụ `catch (IOException e)` khi có đường ném IOException phù hợp.

### `instanceof E` cũng không hợp lệ

`E` không có runtime class token để dùng trực tiếp với `instanceof`:

```java
static <E extends Throwable> boolean isExpected(Throwable failure) {
    return failure instanceof E; // LỖI BIÊN DỊCH.
}
```

Nếu cần kiểm tra runtime type của `E`, API có thể nhận **`Class<E>`**:

```java
static <E extends Throwable> boolean isExpected(
        Throwable failure, Class<E> expected) {
    return expected.isInstance(failure);
}
```

Đây là phương thức hợp lệ. `Class<E>` mang token lớp cụ thể vào runtime, còn type parameter `E` thông thường đã bị xóa kiểu.

## 11. Không thể `new E()`: dùng exception factory

Type parameter generic không thể được khởi tạo trực tiếp:

```java
static <E extends Throwable> E create() {
    return new E(); // LỖI BIÊN DỊCH.
}
```

Lý do không chỉ là “Java không thích generic”. Với `E`, compiler không biết constructor nào có sẵn, và `E` là type variable bị erasure tại runtime. Ngay cả khi biết `E extends Throwable`, không thể suy ra mọi subtype có constructor không tham số hoặc constructor `(String)`.

Nếu API cần **tạo** ngoại lệ theo loại do caller chọn, hãy nhận một factory phù hợp, ví dụ `Function<String,E>`.

### Chương trình chạy được: tạo exception qua factory

Tạo tệp **`GenericExceptionFactoryDemo.java`**:

```java
import java.io.IOException;
import java.util.function.Function;

public class GenericExceptionFactoryDemo {
    static class ValidationException extends Exception {
        ValidationException(String message) {
            super(message);
        }
    }

    static <E extends Exception> void requirePositive(
            int value, Function<String, E> errorFactory) throws E {
        if (value <= 0) {
            throw errorFactory.apply("value must be positive");
        }
    }

    public static void main(String[] args) {
        try {
            requirePositive(0, ValidationException::new);
        } catch (ValidationException failure) {
            System.out.println("validation=" + failure.getMessage());
        }

        try {
            requirePositive(-1, IOException::new);
        } catch (IOException failure) {
            System.out.println("IO=" + failure.getMessage());
        }

        try {
            requirePositive(5, ValidationException::new);
            System.out.println("positive accepted");
        } catch (ValidationException failure) {
            System.out.println("unexpected validation error");
        }
    }
}
```

```bash
javac GenericExceptionFactoryDemo.java
java GenericExceptionFactoryDemo
```

`stdout`:

```text
validation=value must be positive
IO=value must be positive
positive accepted
```

Giải thích:

- `E extends Exception` là loại exception do factory tạo.
- `Function<String,E>` nhận message và tạo một `E` cụ thể; method reference `ValidationException::new` và `IOException::new` cung cấp constructor tương ứng.
- Phương thức generic chỉ biết `E`, nhưng có thể `throw` đối tượng factory trả về nhờ `throws E`.
- Caller bắt loại exception **tương ứng với factory typed**.
- Chọn `IOException` cho lỗi validation chỉ là ví dụ khả năng kiểu, **không phải khuyến nghị thiết kế**: trong ứng dụng nên chọn loại ngoại lệ phản ánh đúng bản chất lỗi.

`Function.apply` bản thân không khai báo checked exception. Nếu **quá trình tạo đối tượng trong factory** cần ném checked exception, phải thiết kế một functional interface có hợp đồng `throws` phù hợp thay vì cho rằng `Function` sẽ tự truyền checked exception.

## 12. Type erasure và giới hạn của generic exception class

Java hỗ trợ **type variable làm kiểu trong `throws`**, nhưng **không** cho khai báo một generic class là subclass của `Throwable`. Ví dụ **cố ý không biên dịch**:

```java
class GenericFailure<T> extends Exception {
    // LỖI: generic class không thể là subclass của Throwable.
}
```

Vì thế không thể thiết kế một ngoại lệ generic mang type parameter như `GenericFailure<String>` rồi bắt bằng `catch (GenericFailure<String> e)`. Đây là giới hạn ngôn ngữ.

Các cấu trúc **hợp lệ** hơn:

```java
class RequestFailure extends Exception {
    RequestFailure(String message) {
        super(message);
    }
}

class ResponseFailure extends Exception {
    ResponseFailure(String message) {
        super(message);
    }
}
```

Hoặc đặt generic ở **method/interface thao tác**, không đặt trên class exception:

```java
interface ThrowingOperation<R, E extends Throwable> {
    R execute() throws E;
}
```

Đây đều là các class/interface minh họa hợp lệ. Exception được biểu diễn bởi các lớp cụ thể, trong khi generic type parameter mô tả **loại lỗi mà một thao tác có thể ném**.

### Vì sao không `E.class`?

`E.class` không hợp lệ vì `E` là type variable, không phải tên class biết tại compile time như `IOException.class`. Nếu cần runtime class, caller truyền `Class<E>`; nếu cần tạo exception, caller truyền factory; nếu cần bọc exception, giữ đối tượng cause thực tế.

## 13. Chuyên biệt hóa generic interface và phương thức override

Generic exception type có thể được đưa vào một interface để lớp triển khai khai báo checked exception phù hợp:

```java
interface Operation<E extends Exception> {
    void run() throws E;
}
```

Khi dùng `Operation<IOException>`, hợp đồng có nghĩa là `run() throws IOException`. Implementation có thể giữ `IOException`, thu hẹp thành subclass (ví dụ `FileNotFoundException`) hoặc không khai báo checked exception. Nó không được tự thêm checked `SQLException` vì không tương thích với `IOException` của interface đã được chuyên biệt hóa.

### Chương trình chạy được: override thu hẹp generic `throws`

Tạo tệp **`GenericOverrideThrowsDemo.java`**:

```java
import java.io.FileNotFoundException;
import java.io.IOException;

public class GenericOverrideThrowsDemo {
    interface Operation<E extends Exception> {
        void run() throws E;
    }

    static class FileOperation implements Operation<IOException> {
        @Override
        public void run() throws FileNotFoundException {
            throw new FileNotFoundException("missing.txt");
        }
    }

    public static void main(String[] args) {
        Operation<IOException> abstractView = new FileOperation();

        try {
            abstractView.run();
        } catch (IOException failure) {
            System.out.println("type="
                    + failure.getClass().getSimpleName());
        }
    }
}
```

```bash
javac GenericOverrideThrowsDemo.java
java GenericOverrideThrowsDemo
```

`stdout`:

```text
type=FileNotFoundException
```

Giải thích:

- `Operation<IOException>` là hợp đồng generic đã có loại lỗi cụ thể.
- `FileOperation.run()` chỉ ném `FileNotFoundException`, subtype của `IOException`, nên override hợp lệ.
- Caller dùng kiểu tĩnh `Operation<IOException>` nên bắt `IOException`.
- Runtime object vẫn là `FileNotFoundException`, được handler `IOException` nhận nhờ quan hệ kế thừa.

**Không nhầm generic throws với việc override được quyền mở rộng checked exception.** Khi interface đã chuyên biệt hóa, các quy tắc checked exception của override vẫn áp dụng.

## 14. Vì sao `Runnable` và `Function` không tự hưởng lợi từ `throws E`?

`java.lang.Runnable.run()` khai báo:

```java
void run();
```

`java.util.function.Function<T,R>.apply(T)` cũng không khai báo checked exception. Do đó, lớp triển khai hoặc lambda gán vào các interface này **không được khai báo checked exception mới** cho functional method.

Không hợp lệ:

```java
Runnable task = () -> {
    throw new java.io.IOException("disk failed");
};
```

Hợp lệ khi **xử lý tại chỗ**:

```java
Runnable task = () -> {
    try {
        operationThatThrowsIOException();
    } catch (java.io.IOException original) {
        throw new IllegalStateException("task failed", original);
    }
};
```

Đây là **mảnh lambda minh họa**, cần định nghĩa `operationThatThrowsIOException()`. Chọn unchecked wrapper có `cause` là quyết định API, không có nghĩa lỗi đã được phục hồi.

Một giải pháp khác là **không dùng `Runnable`/`Function` tại ranh giới cần checked exception**, mà định nghĩa `ThrowingAction<E>` hoặc `ThrowingSupplier<R,E>` với `throws E`.

### Có thể chuyển `ThrowingAction` sang `Runnable` không?

Có thể **adapt** bằng wrapper xử lý hoặc đổi loại exception một cách có ý thức. Nhưng không thể cast tùy tiện một đối tượng implement `ThrowingAction` sang `Runnable` chỉ vì hai interface có method cùng tên `run`; chúng là hai interface độc lập.

Nếu adapter chuyển checked exception thành unchecked, nên giữ nguyên cause và mô tả hợp đồng rõ ràng. Nếu người dùng API phải biết và phục hồi từ checked exception, cần một API callback có khả năng `throws E` ngay từ đầu.

## 15. Kỹ thuật *sneaky throw*: hiểu để tránh lạm dụng

Generic `throws E` có thể bị khai thác để ném một checked exception mà caller **không bị yêu cầu khai báo nó** theo cách thông thường. Đây thường được gọi là *sneaky throw*.

Ví dụ **chạy được nhưng không khuyến nghị dùng để thiết kế API thông thường**:

Tạo file **`SneakyThrowDemo.java`**:

```java
import java.io.IOException;

public class SneakyThrowDemo {
    @SuppressWarnings("unchecked")
    static <E extends Throwable> void sneaky(Throwable failure) throws E {
        throw (E) failure;
    }

    public static void main(String[] args) {
        try {
            SneakyThrowDemo.<RuntimeException>sneaky(
                    new IOException("checked failure"));
        } catch (Throwable failure) {
            System.out.println(
                    failure.getClass().getSimpleName()
                    + ": " + failure.getMessage());
        }
    }
}
```

```bash
javac SneakyThrowDemo.java
java SneakyThrowDemo
```

`stdout`:

```text
IOException: checked failure
```

**Giải thích kỹ thuật:**

1. Phương thức nhận tham số kiểu tĩnh `Throwable`, không phải `E`. Do đó compiler không xác nhận tham số thực sự có kiểu `E`.
2. Ép kiểu `(E) failure` là **unchecked cast**, được gắn `@SuppressWarnings("unchecked")` để tránh cảnh báo; đây không phải bằng chứng về tính an toàn kiểu.
3. Lời gọi chỉ rõ `E = RuntimeException` qua type witness `<RuntimeException>`, khiến hợp đồng `throws E` ở caller trông như unchecked.
4. Runtime vẫn ném **đối tượng IOException thực tế**, không đổi loại nó thành RuntimeException.
5. `catch (Throwable)` chỉ dùng trong ví dụ để in kết quả xác định.

Đừng kết luận rằng checked exception biến thành unchecked thực sự. Nó vẫn là `IOException` ở runtime; chỉ **cơ chế kiểm tra kiểu tại lời gọi** bị làm cho thiếu thông tin chính xác. Điều này có thể phá vỡ kỳ vọng của caller, gây khó bảo trì, làm công cụ phân tích static kém hiệu quả, và tạo ngoại lệ không nằm trong hợp đồng API công khai.

### Tình huống khó xử với `catch (IOException)`

Nếu một lời gọi chỉ được compiler nhìn như ném `RuntimeException`, một handler `catch (IOException)` chỉ dành cho lời gọi đó có thể bị javac từ chối vì **checked IOException không được biết là có thể ném**. Nghĩa là caller mất khả năng xử lý chính loại lỗi mà runtime vẫn có thể tạo ra.

Vì vậy, **không dùng sneaky throw để thay `try/catch`, thay thiết kế functional interface hoặc che lỗi từ caller**. Chỉ nên hiểu cơ chế này khi đọc mã thư viện đặc biệt, nơi tác giả có lý do và hợp đồng rõ ràng.

### Suy luận type parameter trong `throws`

JLS mô tả một số quy tắc đặc biệt khi suy luận type variables có upper bound `Throwable` và chỉ xuất hiện trong ràng buộc `throws`. Trong những tình huống thiếu ràng buộc khác, compiler có thể chọn kiểu unchecked phù hợp (thường `RuntimeException`) làm lựa chọn suy luận. Đây là lý do những API kiểu sneaky throw có thể tồn tại.

Không nên ghi nhớ một mẹo suy luận rồi áp dụng vô điều kiện cho mọi chữ ký generic. Kiểu được suy ra còn phụ thuộc tham số method, kiểu đích, lambda, overload và các ràng buộc khác. Mẫu an toàn hơn là công bố mối quan hệ **thực** giữa callback, loại ngoại lệ và caller, như `ThrowingSupplier<R,E>`.

## 16. Type erasure không xóa đối tượng exception thật

Khi biên dịch generic, nhiều thông tin type parameter không còn hiện diện ở runtime như một kiểu cụ thể độc lập; đây là **type erasure**. Ví dụ upper bound của `E extends Throwable` giúp compiler kiểm tra và xác định cách biểu diễn phù hợp, nhưng `E` không phải một lớp runtime mới.

Do đó:

- Nếu `throw failure;`, đối tượng thực tế vẫn mang class runtime như `IOException` hay `FileNotFoundException`.
- Cơ chế `catch (IOException e)` của mã bên gọi vẫn dựa trên class thực tế của throwable đang truyền.
- `Class<E>` có thể giúp kiểm tra một runtime class cụ thể khi caller cung cấp type token.
- Không dùng `E.class` hoặc `new E()`.
- Không dựa vào cast unchecked để “khẳng định” một đối tượng `Throwable` là subtype `E` do caller chọn.

### Ví dụ runtime type với `Class<E>`

Tạo file **`GenericThrowableClassTokenDemo.java`**:

```java
import java.io.IOException;

public class GenericThrowableClassTokenDemo {
    static <E extends Throwable> boolean matches(
            Throwable value, Class<E> expected) {
        return expected.isInstance(value);
    }

    public static void main(String[] args) {
        Throwable failure = new IOException("disk");

        System.out.println("IOException="
                + matches(failure, IOException.class));
        System.out.println("RuntimeException="
                + matches(failure, RuntimeException.class));
        System.out.println("Throwable="
                + matches(failure, Throwable.class));
    }
}
```

```bash
javac GenericThrowableClassTokenDemo.java
java GenericThrowableClassTokenDemo
```

`stdout`:

```text
IOException=true
RuntimeException=false
Throwable=true
```

`Class.isInstance` thực hiện kiểm tra tương thích runtime. Phương thức generic `matches` không cần ép kiểu và vẫn giữ quan hệ `Class<E>` với type variable. Dùng khi API thật sự cần kiểm tra loại exception do caller chỉ định; nếu chỉ cần chuyển tiếp ngoại lệ, `throws E` và `throw e` đơn giản hơn.

## 17. Precise rethrow và generic `throws E`

Từ Java 7, compiler có phân tích **precise rethrow** cho một số biến exception được bắt và không bị gán lại. Điều này giúp một phương thức bắt một kiểu rộng để quan sát lỗi rồi vẫn khai báo một tập checked exception hẹp tương ứng với các thao tác trong `try`.

Ví dụ:

```java
static <E extends Exception> void executeAndReport(
        ThrowingAction<E> action) throws E {
    try {
        action.run();
    } catch (Exception failure) {
        System.out.println("action failed: " + failure.getMessage());
        throw failure;
    }
}
```

Đây là **mảnh phương thức minh họa** với interface `ThrowingAction<E>` đã được định nghĩa ở trên. Vì tham số `failure` của `catch` không bị gán lại và phần thân `try` chỉ chuyển tiếp ngoại lệ từ `action.run()`, Java có thể áp dụng phân tích rethrow chính xác trong phạm vi các ràng buộc checked exception của `E`.

Tuy nhiên, cần phân biệt hai cơ chế:

1. **Generic `throws E`**: mối liên hệ tĩnh giữa loại lỗi của callback và loại lỗi của helper.
2. **Precise rethrow**: phân tích compiler về checked exception thực sự có thể ném từ khối `try` và tính effectively final của biến được bắt.

Không phải hễ viết `catch (Exception)` hoặc `catch (Throwable)` là có thể `throw` lại mà không khai báo; phụ thuộc chữ ký, các lời gọi trong `try` và quy tắc kiểm tra ngoại lệ.

Nếu không cần thêm log/ngữ cảnh, **đừng bắt chỉ để ném lại**: đơn giản viết `action.run();` cho rõ hợp đồng và tránh log lặp.

### `throws E` không bảo đảm giữ nguyên stack trace nếu bạn bọc sai

`throw original;` truyền cùng đối tượng throwable, còn:

```java
throw new IllegalStateException("failed", original);
```

tạo một đối tượng mới và giữ `original` làm `cause`. Cả hai là cách đúng tùy ranh giới API, nhưng có **ý nghĩa khác nhau**. Không chỉ copy `original.getMessage()` vì có thể mất stack trace/cause/suppressed của lỗi gốc.

## 18. Những lỗi compile-time thường gặp

| Mã / tình huống | Hợp lệ? | Lý do |
| --- | --- | --- |
| `<E extends Throwable> void run(E e) throws E { throw e; }` | Có | E là throwable và được khai báo trong throws |
| `<E extends Throwable> void run(E e) { throw e; }` | **Không**, nói chung | Có thể truyền checked E nhưng thiếu catch/throws |
| `<E extends RuntimeException> void run(E e) { throw e; }` | Có | E bị giới hạn vào nhánh unchecked |
| `catch (E e)` khi E là type variable | **Không** | Type variable không là kiểu catch hợp lệ |
| `new E()` | **Không** | Không thể tạo instance trực tiếp từ type parameter |
| `value instanceof E` | **Không** | Type variable không là runtime reifiable type |
| `E.class` | **Không** | Không tồn tại class literal cho type variable |
| `class Failure<E> extends Exception` | **Không** | Generic class không được là subclass của Throwable |
| `class Failure extends Exception` | Có | Exception class không generic |
| `ThrowingAction<IOException> action = () -> { throw new IOException(); };` | Có | Functional method khai báo throws E = IOException |
| `Runnable action = () -> { throw new IOException(); };` | **Không** | Runnable.run() không công bố checked exception |
| `static <E extends Throwable> void run(ThrowingAction<E> a) throws E` | Có với method body phù hợp | Generic callback bảo toàn hợp đồng lỗi |
| `Operation<IOException>` được implement với `throws FileNotFoundException` | Có | Override thu hẹp checked type |
| `Operation<IOException>` được implement với `throws SQLException` | **Không** | Override thêm checked exception khác nhánh |
| `<RuntimeException>sneaky(new IOException())` | Có thể qua unchecked cast | Kỹ thuật che hợp đồng, không khuyến nghị |

Các dòng là **mảnh cú pháp rút gọn**, không phải toàn bộ chương trình. Bảng xét ý nghĩa generic/exception chính; class, interface, phương thức và quyền truy cập vẫn phải được khai báo phù hợp.

## 19. Thiết kế API: chọn `E extends Throwable` hay `E extends Exception`?

Khi thiết kế library, nên chọn bound đủ hẹp theo nhu cầu.

### `E extends Throwable`

Phù hợp nếu muốn mô hình hóa callback/operation có thể truyền **mọi subtype Throwable**, bao gồm `Error`. Nhưng đây là phạm vi rất rộng. Đừng chọn chỉ vì “trông tổng quát” nếu callers và chính sách xử lý của ứng dụng không có lý do công bố `Error` trong hợp đồng kiểu.

### `E extends Exception`

Phù hợp nếu API liên quan chủ yếu tới nhóm exception của ứng dụng và checked exception của business/I/O. Nó vẫn nhận `RuntimeException`, nhưng không nhận `Error` qua type argument `E`. Unchecked `Error` vẫn có thể phát sinh thực tế; bound này không phải cơ chế ngăn JVM ném Error.

### `E extends IOException` hoặc một ngoại lệ miền cụ thể

Hữu ích khi helper chỉ phục vụ một họ lỗi và caller cần biết giới hạn rõ ràng. Chọn bound rộng hơn `IOException` nếu API thật sự phải xử lý các loại checked exception khác nhánh.

### Khi nào **không cần generic exception type**?

Nếu API chỉ gọi một thao tác cố định luôn công bố `IOException`, một chữ ký thường:

```java
static String read() throws java.io.IOException {
    return "data";
}
```

có thể dễ hiểu hơn tạo `<E extends Throwable>` giả. Generic exception type có giá trị khi tồn tại **mối liên hệ thực giữa type argument của caller/callback và lỗi được truyền**, không phải khi method chỉ muốn né một loại checked exception cụ thể.

### Không lạm dụng nhiều type parameter

Một API với `<R,T,E1,E2,E3>` có thể rất khó đọc. Dùng khi hợp đồng nhiều loại lỗi thực sự cần phân biệt; nếu độ phức tạp vượt lợi ích, cân nhắc một loại domain exception có cause đúng hoặc thiết kế operation/result object. Không dùng `throws Exception` chỉ để bỏ qua thiết kế lỗi, nhưng cũng không ép mọi helper đơn giản thành khung generic phức tạp.

## 20. Những nhầm lẫn phổ biến

**“`E extends Throwable` nghĩa là mọi `E` đều checked exception.”** Sai. `RuntimeException` và `Error` là unchecked.

**“`throws E` nghĩa là luôn phải bắt `Throwable` ở caller.”** Sai. Nếu `E` được suy ra/chỉ định là `IOException`, caller chỉ cần hợp đồng checked tương ứng.

**“Có generic `throws E` thì lambda gán vào `Runnable` được phép ném IOException.”** Sai. Lambda phải tuân thủ `throws` của phương thức abstract trong functional interface đích.

**“`<E extends Throwable>` là một kiểu ngoại lệ mới ở runtime.”** Sai. Đó là type parameter với upper bound, chịu type erasure.

**“Có thể `catch (E failure)` vì E extends Throwable.”** Sai. Java cấm type variable làm kiểu catch.

**“Có thể tạo ngoại lệ với `new E()`.”** Sai. Truyền factory hoặc class token cùng cơ chế tạo có kiểm soát.

**“Hai class exception đều có constructor (String) nên compiler tự biết tạo E.”** Sai. Constructor không được suy ra theo mọi subtype của bound; cần factory cụ thể.

**“Generic exception class `MyException<T> extends Exception` là bình thường.”** Sai. Java cấm generic subclass của `Throwable`.

**“`ThrowingSupplier<R,E>` thay thế hoàn toàn `Function<T,R>` mà không có gì đổi.”** Không. Chúng có hợp đồng functional method khác nhau, không thay thế/cast trực tiếp tùy tiện.

**“`throws E` cho phép method override thêm mọi checked exception.”** Sai. Sau khi type argument được chuyên biệt hóa, quy tắc override checked exception vẫn áp dụng.

**“Sneaky throw đã chuyển IOException thành RuntimeException.”** Sai. Runtime object vẫn là IOException; kỹ thuật chỉ làm caller không thấy đúng checked contract.

**“`@SuppressWarnings("unchecked")` khiến cast an toàn về kiểu.”** Sai. Nó chỉ ẩn cảnh báo, không chứng minh tương thích runtime.

**“Nếu lambda hiện tại không ném lỗi, caller có thể bỏ checked handler dù interface ghi throws E.”** Sai. Kiểm tra dựa trên chữ ký functional interface/type tại lời gọi.

**“`getClass()` hoặc `Class<E>` là lúc nào cũng cần để generic rethrow.”** Không. Truyền `E e` và `throw e` là đủ nếu chỉ chuyển tiếp loại lỗi.

**“Có thể bỏ `throws E` khi throw E vì compiler nhìn bound Throwable.”** Sai trong trường hợp E có thể checked. Bound chứng minh throwable, không tự khai báo lỗi.

**“Bắt rồi ném lại luôn cần tạo một exception mới để giữ stack trace.”** Sai. `throw original;` bảo toàn đối tượng cũ; bọc bằng cause là việc khác.

## 21. Bài tập tự kiểm tra

### Bài 1: Generic bound

```java
static <E extends Throwable> void fail(E e) throws E {
    throw e;
}
```

**Hỏi:** `E` có thể là `IOException`, `IllegalArgumentException`, `AssertionError` không? **Đáp:** Cả ba đều được về bound; quy tắc checked/unchecked khi gọi phụ thuộc kiểu cụ thể.

### Bài 2: Bỏ `throws E`

```java
static <E extends Throwable> void fail(E e) {
    throw e;
}
```

**Hỏi:** Có biên dịch không? **Đáp:** Không trong dạng này, vì `E` có thể là checked exception và method không bắt/khai báo.

### Bài 3: Lambda

```java
@FunctionalInterface
interface Action<E extends Exception> {
    void run() throws E;
}
```

**Hỏi:** `Action<IOException>` có thể dùng lambda ném `IOException` không? **Đáp:** Có, nếu IOException được khai báo hoặc ghi tên đầy đủ và lambda theo hợp đồng interface.

### Bài 4: Catch type variable

```java
static <E extends Exception> void use(E e) {
    try {
        throw e;
    } catch (E caught) {
    }
}
```

**Hỏi:** Cách catch có hợp lệ không? **Đáp:** Không; type variable `E` không được dùng làm kiểu của tham số `catch`.

### Bài 5: Class exception generic

```java
class ValidationFailure<T> extends Exception {
}
```

**Hỏi:** Có biên dịch không? **Đáp:** Không. Generic class không thể là subclass của `Throwable`.

### Bài 6: Runtime type

```java
static <E extends Throwable> boolean test(
        Throwable e, Class<E> expected) {
    return expected.isInstance(e);
}
```

**Hỏi:** Phương thức này có cần `E.class` không? **Đáp:** Không. Caller cung cấp token `Class<E>`.

### Bài 7: Rethrow qua generic

Nếu `ThrowingSupplier<String, IOException> s` ném `IOException`, `supply(s) throws E` với `E=IOException` có cần caller bắt/khai báo IOException không? **Có.**

### Bài 8: Hai loại lỗi

Nếu callback A có `Step<IOException>`, callback B có `Step<SQLException>`, có thể dùng method `<E1 extends Exception,E2 extends Exception> runBoth(...) throws E1,E2` không? **Có.** Caller cần xử lý cả hai checked type nếu chúng có thể thoát.

### Bài 9: Sneaky throw

Một method khai báo generic `throws E` nhưng nội bộ cast unchecked từ `Throwable` sang `E`, caller chỉ định `E=RuntimeException` rồi truyền `IOException`. **Runtime exception object là gì?** Vẫn là `IOException`. Đây là lý do không nên dùng để giấu checked exception.

### Bài 10: Override

`interface Op<E extends Exception> { void run() throws E; }`. Class implements `Op<IOException>` có thể override `run() throws FileNotFoundException` không? **Có**, vì subtype checked được cho phép.

### Bài 11: Factory

Tại sao `<E extends Exception> E make() { return new E(); }` sai, còn nhận `Function<String,E> factory` rồi `factory.apply(message)` có thể đúng? Vì generic không biết constructor thật của `E`, còn factory do caller cung cấp thao tác tạo cụ thể.

### Bài 12: Bound `Exception`

`E extends Exception` có chấp nhận `IllegalStateException` không? **Có** vì là subclass của `RuntimeException`, mà `RuntimeException` kế thừa `Exception`. Nó có chấp nhận `AssertionError` làm type argument không? **Không**, vì `Error` không kế thừa `Exception`.

## 22. Tài liệu Java 17

- [Java Language Specification 17 — 8.4.6 Method Throws](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.6): cho phép type variables phù hợp trong `throws`.
- [Java Language Specification 17 — 11.2 Compile-Time Checking of Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.2): loại checked exception và nghĩa vụ catch/throws.
- [Java Language Specification 17 — 18.4 Resolution](https://docs.oracle.com/javase/specs/jls/se17/html/jls-18.html#jls-18.4): suy luận type variables, bao gồm những ràng buộc liên quan `throws`.
- [Java Language Specification 17 — 15.27 Lambda Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.27): kiểm tra checked exception từ thân lambda theo functional interface.
- [Java Language Specification 17 — 14.20 try/catch](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20): kiểu bắt ngoại lệ và quy tắc lựa chọn handler.
- [Java Language Specification 17 — 8.1.2 Generic Classes and Type Parameters](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.1.2): generic class không được là subclass của Throwable.
- [Java Language Specification 17 — 8.4.8.3 Override Throws](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.8.3): giới hạn checked exception khi ghi đè phương thức.
- [Java SE 17 API — Throwable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html): lớp gốc của cơ chế exception.
- [Java SE 17 API — Runnable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Runnable.html) và [Function](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/function/Function.html): functional methods không khai báo checked exception.
- [Java SE 17 API — Class.isInstance](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#isInstance(java.lang.Object)): kiểm tra runtime type bằng class token.

**Ghi nhớ:** `<E extends Throwable>` cho phép phương thức generic mô tả **loại ngoại lệ E** trong tham số, callback và `throws E`, nhờ đó có thể chuyển tiếp checked exception với hợp đồng rõ ràng. Nhưng type variable không thay thế một class exception cụ thể ở runtime: không `catch (E)`, không `new E()`, không `E.class`. Hãy sử dụng generic `throws` để **giữ thông tin kiểu thật của callback/caller**, không dùng unchecked cast hay *sneaky throw* để che giấu checked exception khỏi người sử dụng API.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lambda-generic-exceptions-007">Giới hạn của catch đối với type parameter</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn của `catch` đối với type parameter trong Java Generic và Lambda

Trong Java, **generic type parameter** như `T`, `E` hay `X` cho phép định nghĩa phương thức và lớp dùng chung cho nhiều kiểu. Riêng với exception handling, có một giới hạn rất dễ nhầm: **Java cho phép khai báo `throws E` khi `E extends Throwable`, nhưng không cho phép viết `catch (E e)` nếu `E` là một type parameter**.

Đây không phải khác biệt về dấu ngoặc hoặc cách đặt tên. `throws E` mô tả hợp đồng checked exception cho trình biên dịch, còn `catch` là một **bộ lọc kiểu ngoại lệ khi xử lý ở runtime**; tham số kiểu generic không thể được dùng làm loại tham số của một catch clause.

Ví dụ cố ý sai:

~~~java
static <E extends Exception> void wrong(ThrowingTask<E> task) {
    try {
        task.run();
    } catch (E e) { // KHÔNG BIÊN DỊCH.
        System.out.println(e.getMessage());
    }
}
~~~

`ThrowingTask<E>` trong mảnh minh họa được giả định là functional interface có `void run() throws E`. Mặc dù `E extends Exception`, việc dùng `E` làm loại trong `catch` vẫn không hợp lệ.

Cách sửa cơ bản là **bắt một kiểu exception cụ thể** như `IOException` hoặc một superclass phù hợp như `Exception`, hoặc để ngoại lệ tiếp tục truyền ra bằng `throws E`. Nếu cần xác định một loại lỗi được biểu diễn bằng type parameter tại runtime, truyền thêm **class token `Class<E>`** và kiểm tra bằng `isInstance`/`cast` là một hướng được kiểm soát tốt hơn.

Bài học thuộc Java Core – Exception, ngữ cảnh **Lambda và generic**. Trọng tâm là quy tắc `catch` với type variable, type erasure, checked exception trong generic functional interface, giới hạn của generic exception class và các giải pháp code Java 17+.

## 1. Nhắc lại: generic type parameter là gì?

Ví dụ phương thức generic:

~~~java
static <T> T identity(T value) {
    return value;
}
~~~

Ở đây `T` là **type parameter** do phương thức khai báo. Caller có thể gọi `identity("Hello")` hoặc `identity(42)`, và trình biên dịch kiểm tra kiểu giá trị tương ứng.

Đối với ngoại lệ, có thể giới hạn type parameter:

~~~java
static <E extends Exception> void fail(E exception) throws E {
    throw exception;
}
~~~

Đoạn này là một phương thức Java **hợp lệ** trong một lớp. `E` biểu diễn một subtype của `Exception`; `throws E` được phép và `throw exception;` cũng được phép.

Ví dụ cách dùng:

~~~java
try {
    fail(new java.io.IOException("disk unavailable"));
} catch (java.io.IOException e) {
    System.out.println(e.getMessage());
}
~~~

Ở caller, kiểu exception dùng trong `catch` là **`IOException` — một class cụ thể**. Ta không viết `catch(E e)` ở phương thức generic.

**Cặp quy tắc cần phân biệt:**

| Cấu trúc | Có dùng type variable `E` được không? |
| --- | --- |
| `<E extends Exception>` | Có, khai báo type parameter có upper bound |
| `void run() throws E` | Có, khi `E` có bound phù hợp với `Throwable` |
| `throw exception;`, với biến kiểu `E` | Có |
| `catch (E e)` | **Không** |
| `catch (IOException e)` | Có, nếu các quy tắc checked/reachability khác thỏa |
| `catch (Exception e)` | Có, nếu hợp lệ với `try` tương ứng |
| `catch (IOException | SQLException e)` | Có thể, khi các alternative hợp lệ |
| `catch (E | IOException e)` | **Không**, vì `E` vẫn là type variable |

Một type variable dù tên là `ExceptionType`, `E` hay `IOExceptionLike` vẫn là **type variable**, không biến thành một class declaration có thể làm catch type.

## 2. Java dùng kiểu nào để chọn `catch`?

Khi một `Throwable` bị ném, JVM tìm handler có phạm vi phù hợp với kiểu của **đối tượng ngoại lệ thực tế**. Một catch clause điển hình:

~~~java
try {
    readFile();
} catch (java.io.IOException e) {
    System.out.println("I/O problem");
} catch (Exception e) {
    System.out.println("Another exception");
}
~~~

Đây là mảnh minh họa; `readFile()` là phương thức có thể ném `IOException`. Nếu một `IOException` được ném, handler đầu tiên có kiểu `IOException` nhận nó. Handler `Exception` rộng hơn được đặt sau.

`catch` cần một kiểu exception hợp lệ để xác định điều kiện so khớp. **Type parameter generic không biểu diễn một class riêng ở runtime.** Vì vậy compiler cấm sử dụng type variable làm catch parameter type, ngay cả khi có upper bound `Exception` hoặc `IOException`.

Một lỗi thường thấy:

~~~java
static <E extends java.io.IOException> void invalidCatch(
        ThrowingTask<E> task) {
    try {
        task.run();
    } catch (E e) { // Lỗi compile-time.
        System.out.println(e.getMessage());
    }
}
~~~

Dù `E extends IOException`, từ khóa `E` vẫn đại diện cho **một biến kiểu**, không phải class `IOException`. Upper bound làm hạn chế phạm vi của `E`, nhưng **không cho phép `catch(E)`**.

## 3. Type erasure và lý do thiết kế của giới hạn này

Java generics chủ yếu hoạt động bằng **type erasure**. Trong nhiều ngữ cảnh runtime, tham số kiểu được xóa và không có một phiên bản class runtime riêng cho mỗi tổ hợp type argument như `Box<String>` hay `Box<Integer>`.

Trong giải thích khái niệm, một type variable `E extends Exception` thường bị xóa về upper bound tương ứng là `Exception`. Nhưng **điều này không có nghĩa trình biên dịch sẽ tự chấp nhận `catch (E e)` rồi thay bằng `catch (Exception e)`**. Ngôn ngữ cấm type variable trong catch clause ngay từ compile-time.

Vì sao? Giả sử phương thức được gọi với `E = IOException` trong một lần và `E = SQLException` ở lần khác. Nếu `catch(E)` được coi là lọc **chính xác E** thì runtime cần biết type argument nào đang được dùng. Type erasure không tự tạo ra handler khác nhau theo từng instantiation của generic method.

Do đó, muốn bắt một loại exception ở runtime, ta cần **kiểu exception được xác định rõ**, hoặc một **class token** được truyền như dữ liệu:

~~~java
Class<java.io.IOException> expected = java.io.IOException.class;

Exception e = new java.io.IOException("disk failed");
System.out.println(expected.isInstance(e)); // true
~~~

`Class.isInstance` kiểm tra loại runtime thật của object, không giả vờ rằng type parameter đã được giữ nguyên để JVM tạo catch clause động.

### Type erasure không giải thích toàn bộ quy tắc bằng một câu

Nên hiểu chính xác hơn:

1. `catch` của Java có những yêu cầu ngôn ngữ riêng về **type** của catch parameter.
2. **Type variable không được dùng làm type của catch parameter**, ngay cả khi bound là exception class.
3. Type erasure giải thích vì sao type argument generic không tự trở thành một class filter runtime độc lập.
4. Khi cần so khớp động, ta làm điều đó **bên trong một handler hợp lệ** hoặc ở code thường, dùng token/reflection có hợp đồng rõ.

Không nên chỉ nói “mọi generic đều không thể dùng với `catch`”. Generic method có thể **ném checked exception generic bằng `throws E`**, và lambda có thể biểu diễn loại checked exception trong một generic functional interface.

## 4. `throws E` hoàn toàn hợp lệ khi `E` có bound phù hợp

Ví dụ:

~~~java
@FunctionalInterface
interface ThrowingAction<E extends Exception> {
    void run() throws E;
}

static <E extends Exception> void execute(
        ThrowingAction<E> action) throws E {
    action.run();
}
~~~

Đây là hai khai báo minh họa hợp lệ khi đặt trong một lớp, với interface ở phạm vi phù hợp.

Điều quan trọng:

- `E` phải có bound khiến nó là subtype của `Throwable` (ở đây chọn `Exception` để mô tả các exception thường gặp).
- `ThrowingAction<E>.run()` có thể khai báo `throws E`.
- `execute(...)` có thể lan truyền `E` bằng `throws E`.
- Caller được trình biên dịch kiểm tra checked exception theo kiểu được suy ra.

Caller:

~~~java
try {
    execute(() -> {
        throw new java.io.IOException("Cannot read");
    });
} catch (java.io.IOException e) {
    System.out.println("Caught: " + e.getMessage());
}
~~~

Trong ví dụ này, compiler có thể suy ra một loại checked exception phù hợp từ ngữ cảnh lambda và generic method. Để ví dụ hướng dẫn dễ hiểu và tránh phụ thuộc vào suy luận kiểu tại những tình huống phức tạp, ta cũng có thể **khai báo rõ** biến:

~~~java
ThrowingAction<java.io.IOException> ioTask =
        () -> { throw new java.io.IOException("Cannot read"); };

try {
    execute(ioTask);
} catch (java.io.IOException e) {
    System.out.println("Caught: " + e.getMessage());
}
~~~

Ở đây, `throws E` cho phép lan truyền checked `IOException`; **không có `catch(E)` nào được khai báo**.

### Tại sao `throws E` không mâu thuẫn với type erasure?

`throws E` là một phần của kiểm tra kiểu tĩnh và thông tin khai báo generic. Nó không yêu cầu JVM tạo handler runtime riêng dựa trên type argument.

Ngược lại, `catch (E e)` đặt `E` trực tiếp vào **điều kiện xử lý ngoại lệ**. Đó là thao tác mà Java không cho phép với type variable.

## 5. Dùng một kiểu class cụ thể thay cho `E`

Nếu generic method chỉ cần xử lý **mọi `IOException` hoặc subtype** và bound là `IOException`, bạn có thể bắt chính `IOException`:

~~~java
@FunctionalInterface
interface ReaderTask<E extends java.io.IOException> {
    String read() throws E;
}

static <E extends java.io.IOException> String withFallback(
        ReaderTask<E> task) {
    try {
        return task.read();
    } catch (java.io.IOException e) {
        return "fallback";
    }
}
~~~

Đây là mã hợp lệ nếu đặt trong một lớp Java. `E` có thể là `IOException` hoặc subtype; `catch(IOException)` bắt được những subtype đó. Không có lý do phải viết `catch(E)`.

Tuy nhiên, đổi `catch(E)` thành `catch(Exception)` không phải lúc nào cũng là một quyết định tốt. Handler rộng có thể bắt luôn những `RuntimeException` do lỗi lập trình hoặc trạng thái bất thường. Cần cân nhắc phạm vi lỗi mình thực sự muốn xử lý.

### Bắt theo upper bound: đúng nhưng có thể quá rộng

Ví dụ:

~~~java
static <E extends Exception> void inspect(
        ThrowingAction<E> action) {
    try {
        action.run();
    } catch (Exception caught) {
        System.out.println(caught.getClass().getSimpleName());
    }
}
~~~

Đây là mã Java hợp lệ. Nó nhận mọi `Exception` có thể thoát từ khối `try`, bao gồm cả unchecked `RuntimeException`.

Nếu mục tiêu chỉ là xử lý một nhóm lỗi cụ thể, hãy dùng upper bound hẹp hơn, overload chuyên biệt hoặc class token rõ ràng. Đừng bắt `Exception` rồi mặc định coi **mọi đối tượng bắt được** đều thuộc một subtype `E` nào đó.

## 6. Dùng `Class<E>` để kiểm tra subtype cụ thể khi thật sự cần

Giả sử ta viết một tiện ích generic nhận một tác vụ và muốn áp dụng fallback chỉ khi ngoại lệ đúng với một **lớp được caller truyền vào**.

Một hướng rõ ràng:

~~~java
static <E extends Exception> boolean matches(
        Throwable caught, Class<E> expectedType) {
    return expectedType.isInstance(caught);
}
~~~

Đây là một phương thức Java hợp lệ. `Class<E>` mang thông tin class runtime; `isInstance` làm phép kiểm tra. Nếu cần lấy biến kiểu `E` sau khi kiểm tra:

~~~java
static <E extends Exception> E castIfMatches(
        Throwable caught, Class<E> expectedType) {
    if (!expectedType.isInstance(caught)) {
        return null;
    }
    return expectedType.cast(caught);
}
~~~

Không nên tự viết ép kiểu `(E) caught` mà không kiểm tra, vì type erasure khiến compiler có thể cảnh báo unchecked cast và tính đúng đắn phụ thuộc dữ liệu runtime.

### Ưu điểm của class token

- Caller ghi rõ loại lỗi mong muốn, ví dụ `IOException.class`.
- `expectedType.isInstance(caught)` kiểm tra chính loại object ở runtime.
- `expectedType.cast(caught)` thực hiện checked cast theo token.
- Không cần `catch(E)` vốn bất hợp lệ.

### Giới hạn và trách nhiệm

- `Class<E>` chỉ đại diện một class runtime, **không biến type variable thành catch type**.
- Caller có thể truyền một class quá rộng như `Exception.class` và sẽ nhận nhiều loại lỗi hơn dự định.
- Nếu muốn chỉ khớp đúng class, dùng `caught.getClass() == expectedType`, không dùng `isInstance`. Tuy nhiên, trong xử lý exception thường muốn nhận cả subtype nên `isInstance` dễ phù hợp hơn.
- Nếu có nhiều loại checked exception, một `Class<E>` duy nhất không biểu diễn một hợp đồng đa lỗi trọn vẹn; cần thiết kế API riêng.
- Việc “đã match” chỉ cho biết **kiểu lỗi**, không tự quyết định retry/fallback có an toàn về nghiệp vụ hay không.

## 7. Một chương trình Java 17+ độc lập minh họa đầy đủ

Tạo tệp **`GenericCatchLimitDemo.java`**:

~~~java
import java.io.IOException;
import java.sql.SQLException;

public class GenericCatchLimitDemo {

    @FunctionalInterface
    interface ThrowingSupplier<T, E extends Exception> {
        T get() throws E;
    }

    @FunctionalInterface
    interface ThrowingAction<E extends Exception> {
        void run() throws E;
    }

    static <T, E extends Exception> T execute(
            ThrowingSupplier<T, E> supplier) throws E {
        return supplier.get();
    }

    static <E extends Exception> void executeAction(
            ThrowingAction<E> action) throws E {
        action.run();
    }

    static <T, E extends Exception> T fallbackOnType(
            ThrowingSupplier<T, E> supplier,
            Class<E> expectedType,
            T fallback) {
        try {
            return supplier.get();
        } catch (Exception caught) {
            if (expectedType.isInstance(caught)) {
                E typed = expectedType.cast(caught);
                System.out.println(
                        "matched=" + typed.getClass().getSimpleName()
                                + ": " + typed.getMessage());
                return fallback;
            }
            if (caught instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(
                    "Unexpected checked exception", caught);
        }
    }

    static <E extends IOException> String catchBound(
            ThrowingSupplier<String, E> supplier) {
        try {
            return supplier.get();
        } catch (IOException e) {
            System.out.println("bound=" + e.getMessage());
            return "bound-fallback";
        }
    }

    static void multiCatch(boolean io) {
        try {
            if (io) {
                throw new IOException("read error");
            }
            throw new SQLException("query error");
        } catch (IOException | SQLException e) {
            System.out.println(
                    "multi=" + e.getClass().getSimpleName()
                            + ": " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        System.out.println("== generic throws ==");
        ThrowingSupplier<String, IOException> ioSupplier =
                () -> { throw new IOException("disk offline"); };

        try {
            execute(ioSupplier);
        } catch (IOException e) {
            System.out.println("caught=" + e.getMessage());
        }

        System.out.println("== generic lambda ==");
        ThrowingAction<SQLException> sqlAction =
                () -> { throw new SQLException("database offline"); };
        try {
            executeAction(sqlAction);
        } catch (SQLException e) {
            System.out.println("action=" + e.getMessage());
        }

        System.out.println("== class token ==");
        String recovered = fallbackOnType(
                ioSupplier, IOException.class, "default");
        System.out.println("result=" + recovered);

        System.out.println("== successful supplier ==");
        ThrowingSupplier<String, IOException> ok =
                () -> "ready";
        System.out.println(
                "result=" + fallbackOnType(
                        ok, IOException.class, "default"));

        System.out.println("== subtype bound ==");
        System.out.println("result=" + catchBound(ioSupplier));

        System.out.println("== runtime mismatch ==");
        ThrowingSupplier<String, IOException> runtimeTask =
                () -> { throw new IllegalArgumentException("bad state"); };
        try {
            fallbackOnType(
                    runtimeTask, IOException.class, "default");
        } catch (IllegalArgumentException e) {
            System.out.println("not-matched=" + e.getMessage());
        }

        System.out.println("== multi-catch classes ==");
        multiCatch(true);
        multiCatch(false);
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac GenericCatchLimitDemo.java
java GenericCatchLimitDemo
~~~

**Stdout xác định:**

~~~text
== generic throws ==
caught=disk offline
== generic lambda ==
action=database offline
== class token ==
matched=IOException: disk offline
result=default
== successful supplier ==
result=ready
== subtype bound ==
bound=disk offline
result=bound-fallback
== runtime mismatch ==
not-matched=bad state
== multi-catch classes ==
multi=IOException: read error
multi=SQLException: query error
~~~

### Phân tích `generic throws`

`ThrowingSupplier<String, IOException>` là một lambda có thể ném checked `IOException`. Phương thức `execute` khai báo `throws E`, nên có thể lan truyền lỗi này. Caller sử dụng `catch (IOException e)` — một kiểu exception **cụ thể**.

Không có `catch (E e)` trong `execute`. Sự generic nằm ở **hợp đồng của method và functional interface**, không nằm ở loại catch.

### Phân tích `generic lambda`

`ThrowingAction<SQLException>` có một abstract method `void run() throws SQLException` sau khi áp dụng type argument. Lambda ném `SQLException` và caller bắt `SQLException`.

Điều này cho thấy generic type parameter trong `throws` vẫn rất hữu ích để tổ chức các công việc ném checked exception, dù catch không dùng type parameter.

### Phân tích `class token`

`fallbackOnType` bắt bằng `catch(Exception)`, sau đó gọi `expectedType.isInstance(caught)`. Với token `IOException.class`, ngoại lệ thực sự là `IOException` nên check thành công, `Class.cast` hợp lệ và phương thức trả `"default"`.

**Lưu ý:** handler rộng có chính sách rõ: khi lỗi là một `RuntimeException` không khớp token, nó **ném lại**, không che lỗi bằng giá trị mặc định; khi xuất hiện checked exception khác token, code bọc thành `IllegalStateException` giữ cause. Chính sách này minh họa cách tránh nuốt lỗi ngoài nhóm mong đợi, không phải quy định mọi thư viện phải xử lý y hệt.

### Phân tích `catchBound`

`E extends IOException` nên nếu task ném checked `E`, `catch(IOException)` bao phủ nó. Cách làm này đơn giản hơn class token khi tất cả lỗi mà phương thức muốn xử lý đều thuộc cùng một họ exception đã biết.

### Phân tích `runtime mismatch`

Lambda được khai báo với `E = IOException` vẫn có thể ném unchecked `IllegalArgumentException` vì unchecked exceptions không cần nằm trong `throws`. `catch(Exception)` bắt được nó, nhưng `IOException.class.isInstance(...)` sai. Mã không thay đổi lỗi đó thành một fallback; nó ném lại và caller bắt đúng `IllegalArgumentException`.

### Phân tích multi-catch

`catch (IOException | SQLException e)` hợp lệ vì cả hai đều là class exception cụ thể và không có quan hệ subtype với nhau. Compiler vẫn biết lựa chọn catch type có thể dùng để lọc handler.

**Không thể thay một vế bằng `E`**, dù `E` có bound như `Exception` hoặc `IOException`.

## 8. Phản ví dụ không biên dịch: `catch(E e)` trong generic method

Xét:

~~~java
interface Task<E extends Exception> {
    void run() throws E;
}

static <E extends Exception> void handle(Task<E> task) {
    try {
        task.run();
    } catch (E e) {
        System.out.println(e.getMessage());
    }
}
~~~

**Đây là phản ví dụ cố ý không biên dịch**, không chép chung vào chương trình chạy được. Tham số `e` trong `catch` có kiểu `E` là **type variable**. Compiler từ chối dù `E extends Exception`.

Có thể sửa theo ý định:

**Phương án A — Không xử lý tại đây:**

~~~java
static <E extends Exception> void propagate(Task<E> task) throws E {
    task.run();
}
~~~

**Phương án B — Xử lý toàn bộ nhóm `Exception` với chính sách cụ thể:**

~~~java
static <E extends Exception> void logAndWrap(Task<E> task) {
    try {
        task.run();
    } catch (Exception e) {
        throw new IllegalStateException("Task failed", e);
    }
}
~~~

**Phương án C — Thu hẹp bound về họ đã biết:**

~~~java
static <E extends IOException> void handleIO(
        ThrowingAction<E> task) {
    try {
        task.run();
    } catch (IOException e) {
        System.out.println("I/O: " + e.getMessage());
    }
}
~~~

Các ví dụ A/B giả định `Task` tồn tại, và phương án C giả định `ThrowingAction` có khai báo `throws E` như chương trình chính. Bản chất của ba hướng:

- A bảo toàn **checked contract `throws E`**.
- B chủ động chuyển lỗi sang unchecked wrapper **giữ cause**.
- C xử lý một lớp exception cụ thể đã biết qua upper bound.

Không nên chuyển `catch(E)` thành `catch(Throwable)` một cách máy móc. Bắt cả `Error` sẽ thay đổi đáng kể trách nhiệm của handler, và có thể che những sự cố nghiêm trọng nếu code chỉ log rồi tiếp tục.

## 9. Multi-catch và type parameter

Java 7+ hỗ trợ multi-catch:

~~~java
try {
    perform();
} catch (IOException | SQLException e) {
    log(e);
}
~~~

Đây là **mảnh minh họa**, `perform` có thể ném hai loại checked exception và `log` là hàm phù hợp.

Các alternative như `IOException` và `SQLException` phải là các loại exception hợp lệ. Một số quy tắc khác của multi-catch vẫn có hiệu lực: ví dụ không được kết hợp hai alternative có quan hệ subtype với nhau như `IOException | Exception`.

Type variable không thể làm một alternative:

~~~java
static <E extends Exception> void invalidMulti(
        ThrowingAction<E> task) throws E {
    try {
        task.run();
    } catch (E | RuntimeException e) { // KHÔNG BIÊN DỊCH.
        System.out.println(e);
    }
}
~~~

Mã này **cố ý sai**. Vế `E` vẫn là type variable, không hợp lệ làm catch type. Việc có thêm `RuntimeException` không sửa quy tắc.

### Type của biến multi-catch không phải type parameter chung

Ví dụ `catch (IOException | SQLException e)` làm biến `e` có kiểu được compiler suy ra theo quy tắc union catch (common supertype phù hợp để truy cập các thành viên chung). Điều đó không đồng nghĩa khai báo một type variable `E` rồi chọn subtype cụ thể theo từng lần gọi.

Tham số multi-catch được xem là **implicitly final** trong phạm vi tương ứng; không thể gán lại `e`. Đây là quy tắc khác với việc cấm `catch(E)` và không nên trộn lẫn hai nguyên nhân biên dịch.

## 10. Một lớp generic kế thừa `Throwable` cũng không hợp lệ

Java còn có một giới hạn liên quan: **không được tạo generic class là subtype của `Throwable`**.

Phản ví dụ:

~~~java
class TypedFailure<T> extends Exception {
    private final T value;

    TypedFailure(T value) {
        this.value = value;
    }
}
~~~

**Mã trên không biên dịch** vì generic class không được là subtype của `Throwable`. Điều này đúng ngay cả khi lớp kế thừa `Exception` hoặc `RuntimeException` thay vì `Throwable` trực tiếp.

Không thể dùng `TypedFailure<String>` và `TypedFailure<Integer>` như hai loại exception riêng biệt để catch ở runtime, bởi thông tin tham số kiểu không được biểu diễn thành các exception class có kiểu khác nhau ở runtime.

### Giải pháp: exception không generic, phương thức xử lý có thể generic

Ví dụ:

~~~java
final class ValidationFailure extends Exception {
    private final String field;

    ValidationFailure(String field, String message) {
        super(message);
        this.field = field;
    }

    String field() {
        return field;
    }
}

static <T> T validateNotNull(T value)
        throws ValidationFailure {
    if (value == null) {
        throw new ValidationFailure(
                "value", "value must not be null");
    }
    return value;
}
~~~

Các khai báo có thể đặt trong một lớp Java. `ValidationFailure` không generic; phương thức `validateNotNull` vẫn có type parameter `T`, bảo toàn kiểu giá trị trả về khi thành công.

Nếu cần dữ liệu lỗi có cấu trúc, dùng mã lỗi, enum, tên trường hoặc metadata thích hợp, thay vì generic type argument trên chính exception class.

### Exception có phương thức generic thì sao?

Một **lớp exception không generic** vẫn có thể định nghĩa một số phương thức generic thông thường, miễn là chúng phù hợp quy tắc Java. Điều bị cấm là **lớp exception bản thân mang type parameter** như `MyException<T> extends Exception`, không phải mọi cách sử dụng generic trong code liên quan đến exception.

## 11. Lambda và checked exception: target type mới là hợp đồng

Một lambda không tự mang một chữ ký `throws` độc lập. Quy tắc checked exception của nó phụ thuộc vào **functional interface** được dùng làm target type.

Ví dụ `java.util.function.Function<T,R>` có phương thức:

~~~java
R apply(T value);
~~~

Nó không khai báo `throws IOException`. Vì vậy mảnh sau **không biên dịch** khi `Files.readString` có thể ném checked `IOException`:

~~~java
java.util.function.Function<String, String> readText =
        path -> java.nio.file.Files.readString(
                java.nio.file.Path.of(path));
~~~

Có thể giải quyết theo ba hướng tùy hợp đồng.

### Hướng A — Bắt và chuyển thành unchecked wrapper

~~~java
java.util.function.Function<String, String> readText =
        path -> {
            try {
                return java.nio.file.Files.readString(
                        java.nio.file.Path.of(path));
            } catch (IOException cause) {
                throw new java.io.UncheckedIOException(
                        "Cannot read file", cause);
            }
        };
~~~

Đây là đoạn minh họa cần import `IOException` hoặc dùng tên đầy đủ. `UncheckedIOException` là một unchecked exception chuẩn có thể giữ `IOException` làm cause.

Hướng này phù hợp khi API tiếp nhận `Function` không cho checked exception thoát và bên gọi được thiết kế xử lý unchecked wrapper.

### Hướng B — Dùng functional interface có `throws E`

~~~java
@FunctionalInterface
interface CheckedFunction<T, R, E extends Exception> {
    R apply(T value) throws E;
}
~~~

Caller:

~~~java
CheckedFunction<String, String, IOException> readText =
        path -> java.nio.file.Files.readString(
                java.nio.file.Path.of(path));
~~~

Lambda có thể ném checked `IOException` vì `apply` của target type khai báo `throws E` và `E` được chọn là `IOException`. Một phương thức sử dụng nó có thể khai báo `throws E` tương ứng.

### Hướng C — Dùng API khác phản ánh thất bại như kết quả

Nếu “không đọc được” là một nhánh dự kiến mà caller luôn phải xử lý, có thể cân nhắc một `Result` type với hai trạng thái hoặc API cung cấp fallback có kiểm soát.

Không có một lựa chọn duy nhất cho mọi dự án. Quan trọng là **hợp đồng exception của functional interface**, không phải suy luận “lambda không hỗ trợ checked exceptions”.

### Liên hệ với `catch(E)`

Ngay cả khi target type có `throws E`:

~~~java
static <T, R, E extends Exception> R applyChecked(
        CheckedFunction<T, R, E> fn, T input) throws E {
    return fn.apply(input);
}
~~~

đây là code hợp lệ. Nhưng nếu phương thức muốn tự bắt lỗi:

~~~java
static <T, R, E extends Exception> R broken(
        CheckedFunction<T, R, E> fn, T input) {
    try {
        return fn.apply(input);
    } catch (E e) { // Không hợp lệ.
        return null;
    }
}
~~~

Giới hạn của `catch(E)` vẫn áp dụng. Type variable làm phần `throws` được, nhưng không làm type của catch parameter.

## 12. Phân biệt overload, subtype và token trong API xử lý lỗi

Giả sử thư viện có hai chiến lược xử lý:

- Một phương thức chỉ xử lý `IOException`.
- Một phương thức chỉ xử lý `SQLException`.

Đôi khi **overload chuyên biệt** làm API dễ hiểu hơn một generic handler phức tạp.

~~~java
static String fromIO(ThrowingSupplier<String, IOException> fn) {
    try {
        return fn.get();
    } catch (IOException e) {
        return "I/O fallback";
    }
}
~~~

Một API SQL có thể dùng functional interface và method riêng cho SQL. Tuy nhiên do type erasure, **không thể giả định hai overload chỉ khác `ThrowingSupplier<String, IOException>` và `ThrowingSupplier<String, SQLException>` sẽ có chữ ký runtime khác nhau**: chúng có cùng raw type và có thể gây *name clash* khi trùng tên method.

Hãy dùng tên phương thức rõ ràng, interface khác nhau hoặc thêm tham số phân biệt nếu thực sự cần overload.

### Token không tự làm typed catch

Ngay cả khi method nhận `Class<E> expectedType`, đoạn này vẫn sai:

~~~java
static <E extends Exception> void wrongTokenCatch(
        Class<E> expectedType, ThrowingAction<E> task) {
    try {
        task.run();
    } catch (E e) { // Vẫn lỗi biên dịch.
        System.out.println(e);
    }
}
~~~

`Class<E>` chỉ giúp **kiểm tra động sau khi bắt bằng kiểu hợp lệ**. Nó không thay đổi ngữ pháp hoặc luật type checking của `catch`.

## 13. Bắt rộng có thể che lỗi lập trình

Giả sử code generic làm:

~~~java
static <E extends Exception> String unsafeFallback(
        ThrowingSupplier<String, E> supplier) {
    try {
        return supplier.get();
    } catch (Exception ignored) {
        return "default";
    }
}
~~~

Đây là một phương thức hợp lệ nhưng **chỉ nên áp dụng khi chính sách fallback cho mọi `Exception` là hợp lệ**. Vì `RuntimeException extends Exception`, những lỗi như `NullPointerException` hoặc `IllegalArgumentException` cũng bị nuốt.

Khi lỗi chỉ nên được phục hồi nếu đúng một lớp được dự kiến, dùng:

- `catch(IOException)` khi hợp đồng đã hẹp tới I/O; hoặc
- `Class<E>.isInstance` cùng một chính sách rõ cho lỗi không khớp.

Đừng suy ra rằng **vì `E extends Exception` nên mọi `Exception` bắt được đều đúng loại `E`**. Một lambda có thể ném unchecked exception không được liệt kê trong `throws E`.

## 14. Vì sao không nên dùng ép kiểu `(E) e` bừa bãi?

Giả sử:

~~~java
static <E extends Exception> E unsafeCast(Exception e) {
    return (E) e;
}
~~~

Đây là phản ví dụ có thể biên dịch với **cảnh báo unchecked cast** tùy ngữ cảnh compiler. Vì type erasure, câu lệnh cast không bảo đảm kiểm tra đúng type argument mong muốn tại thời điểm ấy.

Một caller có thể tưởng mình nhận `IOException` nhưng đối tượng thực tế lại là subtype `Exception` khác. Lỗi có thể chỉ xuất hiện muộn khi giá trị bị ép lại ở điểm sử dụng.

Hướng an toàn hơn:

~~~java
static <E extends Exception> E checkedCast(
        Exception e, Class<E> expectedType) {
    return expectedType.cast(e);
}
~~~

`Class.cast` kiểm tra class runtime và ném `ClassCastException` nếu đối tượng không phù hợp. Nếu muốn tránh ném khi không khớp, kiểm tra `isInstance` trước.

**Không nên dùng reflection làm mọi nơi chỉ để thay generic exception handling.** Nếu chỉ có một nhóm lỗi được xử lý, `catch(IOException)` hoặc `catch(SQLException)` thường đơn giản hơn.

## 15. `E extends Throwable` có khác `E extends Exception` không?

Có thể khai báo:

~~~java
static <E extends Throwable> void throwAny(E failure) throws E {
    throw failure;
}
~~~

Đây là Java hợp lệ. Type parameter `E` có bound `Throwable` nên được dùng ở `throws` và `throw`.

Nhưng:

~~~java
static <E extends Throwable> void cannotCatchE(
        E failure) {
    try {
        throw failure;
    } catch (E caught) { // KHÔNG BIÊN DỊCH.
        System.out.println(caught);
    }
}
~~~

Vẫn sai. Nới bound từ `Exception` lên `Throwable` không làm `catch(E)` hợp lệ.

Thêm nữa, `catch(Throwable)` sẽ bắt cả những đối tượng thuộc nhánh `Error`. Đây là trách nhiệm rộng, cần chính sách rất đặc biệt nếu sử dụng tại ranh giới runtime/framework. Trong code nghiệp vụ thông thường, bắt `Exception` hoặc lớp hẹp hơn thường phù hợp hơn.

### `E extends RuntimeException` cũng không cứu được `catch(E)`

~~~java
static <E extends RuntimeException> void bad(
        E failure) {
    try {
        throw failure;
    } catch (E e) { // Vẫn lỗi compile-time.
        System.out.println(e);
    }
}
~~~

Cho dù `E` chắc chắn unchecked, nó vẫn là type variable. **Checked/unchecked khác với yêu cầu type của catch parameter**; không thể dùng một quy tắc để xóa quy tắc còn lại.

## 16. Generic method có thể mô tả chính xác checked exception của caller

Ví dụ một chuỗi xử lý hai bước cùng khai báo khả năng ném `E`:

~~~java
@FunctionalInterface
interface CheckedStep<E extends Exception> {
    void run() throws E;
}

static <E extends Exception> void runBoth(
        CheckedStep<E> first,
        CheckedStep<E> second) throws E {
    first.run();
    second.run();
}
~~~

Đây là code hợp lệ. Trong trường hợp caller dùng `CheckedStep<IOException>` ở cả hai bước, `runBoth` có thể ném checked `IOException` theo hợp đồng.

Tuy nhiên, nếu hai bước có hai loại checked exception khác nhau, việc ép cả hai về một `E` có thể làm mất thông tin tĩnh cụ thể hoặc buộc suy luận một common supertype. Có thể thiết kế hai type parameter:

~~~java
static <E1 extends Exception, E2 extends Exception>
void runTwoKinds(
        CheckedStep<E1> first,
        CheckedStep<E2> second) throws E1, E2 {
    first.run();
    second.run();
}
~~~

Đoạn này hợp lệ. Caller phải xử lý những checked exceptions tương ứng theo cách sử dụng.

Điểm chính: **generic throws rất linh hoạt**, nhưng khi muốn tự bắt trong phương thức này, vẫn phải dùng catch type hợp lệ, ví dụ `catch(IOException)` khi biết trước lớp, `catch(Exception)` khi chính sách cho phép, hoặc một cơ chế kiểm tra động có thiết kế riêng.

## 17. Những lỗi biên dịch phổ biến cần nhận diện

### 17.1. `catch(E e)`

~~~java
static <E extends Exception> void f(ThrowingAction<E> action) {
    try {
        action.run();
    } catch (E e) { }
}
~~~

Sai vì **catch parameter type là type variable**.

### 17.2. `catch(E | IOException e)`

~~~java
static <E extends Exception> void f(ThrowingAction<E> action) {
    try {
        action.run();
    } catch (E | IOException e) { }
}
~~~

Sai vì `E` không được dùng làm multi-catch alternative.

### 17.3. `class Problem<T> extends Exception`

~~~java
class Problem<T> extends Exception {
}
~~~

Sai vì **generic class không được là subtype của `Throwable`**.

### 17.4. `new E()`

~~~java
static <E extends Exception> E make() {
    return new E();
}
~~~

Sai vì không thể tạo instance trực tiếp từ type variable.

### 17.5. `instanceof E`

~~~java
static <E extends Exception> boolean matches(
        Throwable thrown) {
    return thrown instanceof E;
}
~~~

Sai vì type variable không phải một reifiable runtime type thích hợp cho phép kiểm tra `instanceof`.

Các lỗi 17.4 và 17.5 không riêng về `catch`, nhưng đến từ cùng một ý tưởng sai: tưởng rằng compiler có một class object runtime độc lập tương ứng với type variable `E`.

### 17.6. Lambda ném checked exception vào `Function` chuẩn

~~~java
java.util.function.Function<String, String> read =
        path -> java.nio.file.Files.readString(
                java.nio.file.Path.of(path));
~~~

Sai vì `Function.apply` không có checked `throws IOException`.

### 17.7. Multi-catch hai loại có quan hệ subtype

~~~java
try {
    something();
} catch (IOException | Exception e) {
    System.out.println(e);
}
~~~

Sai vì `IOException` là subtype của `Exception`. Đây là quy tắc **khác** với type variable, nhưng thường xuất hiện khi người học cố thay thế `catch(E)` bằng một union rộng quá mức.

## 18. FAQ về cách xử lý type parameter và exception

**Hỏi: Có thể dùng `catch (T e)` nếu `T extends IOException` không?**  
Không. `T` vẫn là type variable; bắt `IOException` nếu muốn xử lý cả họ I/O.

**Hỏi: Có thể dùng `catch (E e)` nếu method nhận `Class<E>` không?**  
Không. Token chỉ giúp kiểm tra **bên trong một catch hợp lệ** hoặc code xử lý bình thường.

**Hỏi: `throws E` có cần `E extends Exception` không?**  
Không nhất thiết; upper bound `Throwable` hoặc subtype thích hợp cũng được. Ví dụ `<E extends Throwable> ... throws E` là hợp lệ.

**Hỏi: Vì sao `catch (Exception e)` hợp lệ nhưng `catch (E e)` không?**  
`Exception` là một class exception có thể dùng cho handler. `E` là type variable, bị cấm làm catch parameter type.

**Hỏi: Generic class có thể khai báo phương thức throws một type parameter không?**  
Có, nếu tham số kiểu có bound phù hợp. Ví dụ một generic functional interface với `E extends Exception` có thể có `run() throws E`.

**Hỏi: Java có generic exception class như `MyException<T>` không?**  
Không, nếu class ấy kế thừa `Throwable` trực tiếp hoặc gián tiếp. Hãy dùng exception class không generic, rồi giữ metadata hoặc dùng generic method bên ngoài.

**Hỏi: `IOException.class` có bắt cả subtype `FileNotFoundException` không?**  
Có, nếu dùng `Class.isInstance`; đây là phép kiểm tra tương ứng quan hệ subtype tại runtime.

**Hỏi: Dùng `getClass() == expectedType` thì sao?**  
Chỉ khớp class **chính xác**, không nhận subtype. Chọn hành vi theo hợp đồng.

**Hỏi: Có nên luôn bắt `Exception` rồi check `Class<E>` không?**  
Không. Nếu class cần xử lý đã biết, `catch` kiểu cụ thể dễ đọc, rõ trách nhiệm và ít rủi ro hơn.

**Hỏi: Một lambda khai báo `ThrowingSupplier<T,IOException>` có thể vẫn ném `NullPointerException` không?**  
Có. Unchecked exception không bị giới hạn chỉ vào checked `throws IOException`. Vì vậy handler rộng không nên mặc định mọi lỗi bắt được đều thuộc `IOException`.

**Hỏi: `catch(Throwable)` có giải quyết giới hạn này không?**  
Về cú pháp là handler hợp lệ, nhưng phạm vi rất rộng gồm cả `Error`. Nó không tương đương bắt chính xác `E` và không nên là cách thay thế mặc định.

**Hỏi: Vì sao cần bảo toàn cause khi bọc lỗi trong generic helper?**  
Để không mất thông tin lỗi gốc và stack trace. Dùng constructor `new IllegalStateException("...", caught)`, không chỉ nối `caught.getMessage()` thành chuỗi.

## 19. Kiểm tra kiến thức

1. Vì sao `catch (E e)` không hợp lệ khi `E extends Exception`?
2. `throws E` có hợp lệ với `E extends Exception` không?
3. `throw error;` với biến `error` kiểu `E extends Exception` có hợp lệ không?
4. `catch (IOException e)` có bắt một subtype của `IOException` không?
5. Nếu `E extends IOException`, có thể dùng `catch(E)` không?
6. `catch (Exception e)` trong generic method có thể bắt cả unchecked `RuntimeException` không?
7. Vì sao type erasure liên quan tới việc không thể dùng `E` làm catch type?
8. `Class<E>` cho phép hai thao tác nào để kiểm tra và cast an toàn?
9. `Class.isInstance` có nhận subclass không?
10. Vì sao không nên luôn trả fallback sau bất kỳ `Exception` nào?
11. Dạng khai báo `<E extends Throwable> void f() throws E` có hợp lệ không?
12. Vì sao `class MyException<T> extends Exception` không biên dịch?
13. Có thể dùng type parameter trong `throws` của generic functional interface không?
14. `Function<T,R>` chuẩn có cho lambda tự ném checked `IOException` ra ngoài không?
15. Nếu muốn lambda ném checked `IOException`, nên thay target interface như thế nào?
16. `catch (IOException | SQLException e)` có hợp lệ khi hai lỗi có thể ném không?
17. `catch (IOException | Exception e)` vì sao sai?
18. `catch (E | IOException e)` vì sao sai?
19. Một `ThrowingSupplier<String,IOException>` có thể ném unchecked `IllegalArgumentException` không?
20. `catch(Throwable)` có ý nghĩa giống `catch(E)` không?
21. `(E) exception` có kiểm tra chắc chắn đúng type argument runtime không?
22. Khi nào nên ưu tiên catch class cụ thể thay cho token?
23. `throws E1, E2` trong method có hai type parameter bounded là hợp lệ không?
24. Một exception class không generic có thể được ném từ generic method không?
25. Nếu chỉ cần ghi lại lỗi rồi chuyển tiếp, có nhất thiết phải tự viết catch generic không?

### Đáp án gợi ý

1. Vì type variable không được dùng làm loại tham số của catch clause.
2. Có.
3. Có.
4. Có.
5. Không; dùng `catch(IOException)` nếu phù hợp.
6. Có.
7. Type argument generic không tạo ra class handler runtime riêng cho mỗi instantiation.
8. `isInstance` và `cast`.
9. Có.
10. Vì có thể che lỗi lập trình/unchecked hoặc lỗi không có fallback an toàn.
11. Có, trong một cấu trúc khai báo hợp lệ.
12. Generic class không được kế thừa `Throwable` trực tiếp/gián tiếp.
13. Có, ví dụ `void run() throws E`.
14. Không; `Function.apply` không khai báo checked exception.
15. Dùng interface riêng có `throws E` hoặc bắt và wrap theo hợp đồng.
16. Có.
17. Vì hai alternative có quan hệ subtype.
18. Vì `E` là type variable.
19. Có, unchecked exception không buộc khai báo.
20. Không; `Throwable` bắt cả lỗi khác ngoài phạm vi `E`, kể cả `Error`.
21. Không; có thể chỉ là unchecked cast do type erasure.
22. Khi loại lỗi đã biết tại nơi viết handler.
23. Có, với bound phù hợp và xử lý checked exception ở caller.
24. Có.
25. Không; có thể dùng `throws E` và để caller xử lý, hoặc bắt loại cụ thể khi cần.

## 20. Ghi nhớ

**Trong Java Generic, `throws E` được phép còn `catch (E e)` bị cấm nếu `E` là type parameter.** Upper bound `E extends Exception`, `E extends IOException` hay `E extends RuntimeException` đều không biến `E` thành một kiểu catch hợp lệ. Hãy dùng `catch` với **class exception cụ thể** khi biết nhóm lỗi, dùng `throws E` để giữ hợp đồng checked exception của generic method/functional interface, và chỉ dùng `Class<E>.isInstance/cast` khi thực sự cần so khớp một loại lỗi do caller chỉ định tại runtime. Đồng thời tránh định nghĩa generic exception class kế thừa `Throwable`; hãy giữ exception type không generic và đặt sự linh hoạt vào method hoặc interface.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lambda-generic-exceptions-008">Suy luận kiểu với throws và rủi ro của sneaky throw</a>

<details>
<summary>Xem chi tiết</summary>

# Suy luận kiểu với `throws` và rủi ro của `sneaky throw` trong Java

Java có cơ chế **checked exception**: nếu một phương thức hoặc biểu thức có thể làm một ngoại lệ checked thoát ra, compiler thường buộc code gọi phải `catch` hoặc khai báo `throws` phù hợp. Tuy nhiên, khi **generic type variable** xuất hiện trong mệnh đề `throws`, quá trình **suy luận kiểu (type inference)** có những quy tắc đặc biệt. Từ đó xuất hiện kỹ thuật thường được gọi là **sneaky throw**: làm một ngoại lệ checked thực sự lan ra ở runtime, trong khi chữ ký và việc kiểm tra tại điểm gọi không cho thấy nghĩa vụ checked exception tương ứng.

Điểm phân biệt quan trọng:

- **Generic `throws E` hợp lệ và hữu ích** khi thiết kế API cho lambda, method reference hoặc callback có khả năng ném loại checked exception của riêng chúng.
- **Suy luận kiểu của lambda có kiểm tra checked exception**; nó không tự động “bỏ qua” `IOException` hay `SQLException`.
- **Sneaky throw cố ý lợi dụng một trường hợp suy luận kiểu** để caller không bị compiler yêu cầu bắt một ngoại lệ checked, dù ngoại lệ đó có thể thực sự được ném.
- **Runtime không biến `IOException` thành `RuntimeException`**. Đối tượng được ném giữ nguyên loại và danh tính; chỉ **mô hình checked exception tại compile time** bị làm cho khác với hành vi thực tế.
- Kỹ thuật này có rủi ro lớn với hợp đồng API, `catch` theo kiểu cụ thể, code tích hợp và khả năng bảo trì. Trong phần lớn ứng dụng, nên **công bố checked exception minh bạch, xử lý ngay hoặc bọc thành một unchecked exception có cause rõ ràng**.

Bài này giải thích nền tảng JLS Java 17, các tình huống suy luận kiểu, ví dụ runnable, các ví dụ compiler từ chối, nguyên nhân runtime vẫn ném được `IOException`, rủi ro trong Stream/lambda, cách thay thế an toàn và các câu hỏi tự kiểm tra.

## 1. Ba tầng cần phân biệt: đối tượng, chữ ký và suy luận

Xét một ngoại lệ:

~~~java
java.io.IOException failure =
        new java.io.IOException("đọc dữ liệu thất bại");
~~~

Có ba câu hỏi khác nhau:

1. **Loại runtime của đối tượng là gì?** — `java.io.IOException`.
2. **Một phương thức công bố loại lỗi gì?** — Ví dụ `throws IOException`, `throws Exception`, `throws E` hoặc không có mệnh đề `throws`.
3. **Compiler suy ra nghĩa vụ nào tại một lời gọi generic cụ thể?** — Phụ thuộc type variable, bounds, đối số, target type và các ràng buộc từ lambda/method reference.

Các câu hỏi này không luôn cho cùng một tên kiểu. Ví dụ một đối tượng `FileNotFoundException` có thể được ném từ phương thức `throws IOException` vì nó thuộc nhánh `IOException`. Điều đó vẫn **đúng hợp đồng**. Ngược lại, khi một đối tượng `IOException` thoát khỏi phương thức mà caller không thấy bất kỳ checked `throws` nào, có thể đang có thiết kế sai hoặc một kỹ thuật cố ý làm mờ hợp đồng.

Trong các ví dụ của bài, **`throw`** là câu lệnh ném một đối tượng tại runtime, còn **`throws`** là phần khai báo khả năng thoát ngoại lệ trong phương thức. Sự khác nhau này là nền tảng để hiểu generic `throws E`.

## 2. Checked và unchecked exception trong Java 17

Phân cấp rút gọn:

~~~text
java.lang.Throwable
├── java.lang.Error                       unchecked
│   └── ...
└── java.lang.Exception
    ├── java.io.IOException               checked
    │   └── FileNotFoundException          checked
    ├── java.sql.SQLException             checked
    └── java.lang.RuntimeException        unchecked
        ├── IllegalStateException
        └── IllegalArgumentException
~~~

**Checked exception** là các kiểu thuộc `Throwable` nhưng **không** nằm dưới `RuntimeException` hoặc `Error`. Với các ngoại lệ checked có thể thoát, code thường phải tuân theo luật **catch-or-declare**.

Ví dụ đơn giản:

~~~java
import java.io.IOException;

// Đoạn minh họa bên trong một lớp.
static void read() throws IOException {
    throw new IOException("không đọc được");
}

static void caller() {
    // read();  // Không biên dịch nếu không bắt hoặc khai báo IOException.
}
~~~

Một cách sửa hợp lệ:

~~~java
// Đoạn minh họa bên trong lớp có read() ở trên.
static void caller() {
    try {
        read();
    } catch (IOException ex) {
        System.out.println("Đã bắt lỗi I/O");
    }
}
~~~

Hoặc `caller()` có thể khai báo `throws IOException` để chuyển nghĩa vụ cho người gọi tiếp theo.

**Unchecked exception** có thể thoát mà compiler không buộc caller bắt/khai báo:

~~~java
static void failFast() {
    throw new IllegalStateException("trạng thái không hợp lệ");
}
~~~

Điều này không có nghĩa unchecked exception “an toàn hơn” hoặc “không phải ngoại lệ thật”. Nó chỉ khác về **quy tắc kiểm tra tại thời điểm biên dịch**.

## 3. Generic method có `throws E`

Một phương thức generic có thể công bố kiểu ngoại lệ bằng type parameter:

~~~java
// Đoạn minh họa bên trong một lớp.
static <E extends Exception> void perform(ThrowingAction<E> action) throws E {
    action.run();
}
~~~

Trong đó `ThrowingAction<E>` là functional interface:

~~~java
@FunctionalInterface
interface ThrowingAction<E extends Exception> {
    void run() throws E;
}
~~~

Kiểu `E` được dùng ở cả **chữ ký callback** và **mệnh đề throws của phương thức nhận callback**. Khi gọi, compiler có thông tin từ lambda hoặc kiểu của biến callback để quyết định checked exception cần xử lý.

Ví dụ:

~~~java
// Đoạn minh họa dùng hai khai báo trên.
perform(() -> {
    throw new java.io.IOException("lỗi I/O từ lambda");
});
~~~

Nếu lời gọi nằm trong một phương thức không khai báo `throws IOException` và không có `catch` phù hợp, nó **không được phép tự nhiên “thoát” lỗi này chỉ vì `E` là generic**. Ràng buộc phát sinh từ lambda có thể khiến `E` phải bao phủ `IOException`, vì vậy caller phải xử lý.

Đây là ý nghĩa lành mạnh của generic `throws E`: **truyền đúng hợp đồng checked exception từ callback tới người gọi**, thay vì buộc mọi callback phải công bố `throws Exception` rộng hoặc chuyển tất cả lỗi thành unchecked.

## 4. Suy luận `throws` trong JLS: điểm tinh tế

JLS §18 quy định suy luận kiểu cho generic method và các biểu thức lambda/method reference. Một số chi tiết có thể giải thích mà không cần thuộc toàn bộ thuật toán:

- Khi type variable `E` xuất hiện ở mệnh đề `throws E`, hệ thống suy luận lưu dấu một bound thông tin thường mô tả là **`throws α`** đối với inference variable tương ứng.
- Nếu không có ràng buộc cụ thể nào buộc `E` phải bao phủ checked exception, và các upper bounds cho phép, việc suy luận ưu tiên một **unchecked exception type**.
- Trong tình huống phổ biến khi upper bound là `Throwable` hoặc `Exception` và không có lower bound áp đặt kiểu checked, JLS §18.4 có quy tắc lựa chọn **`RuntimeException`** làm kiểu được suy ra.
- Ngược lại, **checked exception thực tế xuất hiện trong body lambda** có thể tạo ràng buộc bổ sung theo JLS §18.2.5, khiến `E` không còn tự do được chọn theo cách trên.
- Một **type witness** do lập trình viên chỉ rõ, ví dụ `SomeClass.<IOException>method(...)`, cũng có thể khiến caller phải tuân theo checked `throws IOException`.

Quy tắc này không có nghĩa compiler “đọc loại runtime” bằng cách chạy chương trình. Compiler giải các ràng buộc kiểu từ **chữ ký và biểu thức nguồn**, không quan sát sự cố ở runtime.

Đặc biệt, `throws α` trong giải thích JLS là **ký hiệu nội bộ của quá trình suy luận**, không phải cú pháp viết trực tiếp `throws α` trong mã Java thông thường. Lập trình viên viết type variable như `E`, còn ký hiệu α dùng để mô tả thuật toán.

## 5. Cơ chế của một helper `sneaky throw`

Một biến thể ngắn:

~~~java
@SuppressWarnings("unchecked")
static <E extends Throwable> void passThrough(Throwable failure) throws E {
    throw (E) failure;
}
~~~

Về cú pháp, phương thức này khai báo rằng nó có thể ném `E`, một subtype của `Throwable`. Bên trong, phép ép `(E) failure` là **unchecked cast** do thông tin generic bị xóa theo type erasure, nên annotation `@SuppressWarnings("unchecked")` chỉ nhằm loại bỏ cảnh báo liên quan. Annotation **không** tự bỏ qua compiler checking của caller.

Điểm mấu chốt nằm ở **chữ ký**:

- Đối số nhận vào có kiểu `Throwable`, **không phải `E`**.
- Vì vậy truyền `new IOException(...)` vào tham số `Throwable` **không trực tiếp áp đặt ràng buộc `E = IOException`** tại điểm gọi.
- Nếu không có ràng buộc khác, type variable `E` trong `throws E` có thể được compiler suy luận là `RuntimeException` theo quy tắc đã nêu.
- Caller không bị buộc `catch IOException`, dù đối tượng `Throwable` được truyền có thể đang là một `IOException`.

Ví dụ:

~~~java
static void hiddenFromSignature() {
    passThrough(new java.io.IOException("I/O ẩn"));
}
~~~

**Câu lệnh trên có thể biên dịch**, mặc dù `hiddenFromSignature()` không khai báo `throws IOException` và không bắt nó. Khi chạy, `IOException` vẫn được ném ra từ `passThrough`. Đây là điểm gây ra tên gọi **sneaky throw**.

Không nên khẳng định “Java luôn suy luận mọi `E extends Throwable` thành `RuntimeException`”. Suy luận còn phụ thuộc những ràng buộc khác từ tham số, target type, lambda hoặc type arguments tường minh. **Sneaky throw tận dụng đúng một tình huống cụ thể** mà các ràng buộc đó không buộc `E` thành checked exception.

## 6. Chương trình Java 17+ hoàn chỉnh: một `IOException` thực sự thoát ra

Tạo tệp `SneakyThrowDemo.java`:

~~~java
import java.io.IOException;

public class SneakyThrowDemo {
    @SuppressWarnings("unchecked")
    static <E extends Throwable> void passThrough(Throwable failure) throws E {
        throw (E) failure;
    }

    static void hiddenFromSignature() {
        passThrough(new IOException("I/O ẩn"));
    }

    static void explicitContract() throws IOException {
        SneakyThrowDemo.<IOException>passThrough(new IOException("I/O khai báo"));
    }

    public static void main(String[] args) {
        try {
            hiddenFromSignature();
        } catch (Throwable ex) {
            System.out.println("Thực tế: " + ex.getClass().getSimpleName()
                    + " - " + ex.getMessage());
        }

        try {
            explicitContract();
        } catch (IOException ex) {
            System.out.println("Rõ ràng: " + ex.getMessage());
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 SneakyThrowDemo.java
java SneakyThrowDemo
~~~

Stdout chính xác:

~~~text
Thực tế: IOException - I/O ẩn
Rõ ràng: I/O khai báo
~~~

Phân tích:

1. `hiddenFromSignature()` không có `throws`. Call `passThrough(new IOException(...))` cho phép compiler suy ra một lựa chọn unchecked cho `E`, nên lời gọi hợp lệ xét checked exceptions.
2. Đối tượng thực sự được ném vẫn là `IOException`; `ex.getClass().getSimpleName()` in `IOException`. Không có lớp `RuntimeException` mới được tạo.
3. `catch (Throwable ex)` đủ rộng để nhận đối tượng đã thoát khỏi phương thức bất kể loại checked hay unchecked. Đây là một lựa chọn **chỉ nhằm minh họa**; ứng dụng bình thường không nên bắt `Throwable` rộng để che cả `Error`.
4. `explicitContract()` dùng **type witness** `<IOException>`, khiến `passThrough` được gọi như phương thức có `throws IOException`. Vì thế bản thân `explicitContract()` phải khai báo `throws IOException` hoặc xử lý lỗi.
5. `main()` có `catch (IOException)` hợp lệ quanh lời gọi `explicitContract()` vì **chữ ký tĩnh** của phương thức này công bố `throws IOException`.

Sự khác biệt giữa hai phần của chương trình không phải runtime biết loại ngoại lệ nào sẽ xuất hiện. Khác biệt nằm ở **hợp đồng mà compiler nhìn thấy** khi kiểm tra từng lời gọi.

## 7. Vì sao không có `ClassCastException` ở chỗ `(E) failure`?

Một câu hỏi tự nhiên: nếu compiler suy ra `E = RuntimeException`, tại sao `(E) failure` trong helper lại không gây `ClassCastException` khi đối tượng là `IOException`?

Giải thích: Java generic sử dụng **type erasure**. Với type variable `E extends Throwable`, biểu thức cast trong code generic không tương đương việc JVM luôn kiểm tra đối tượng theo kiểu cụ thể `RuntimeException` tại vị trí ném đó. Trong helper này, giới hạn runtime cơ bản của `E` là `Throwable`; đối tượng `IOException` đáp ứng giới hạn ấy.

Câu lệnh `throw` ở JVM ném một đối tượng thuộc `Throwable`, không có cơ chế runtime tự đối chiếu bắt buộc loại ngoại lệ với **danh sách `throws` được compiler suy ra tại điểm gọi**. Chữ ký generic và `throws` phần lớn phục vụ **kiểm tra ngôn ngữ tại compile time**, không phải bộ lọc ngoại lệ runtime.

**Không suy rộng:** có những unchecked cast khác trong Java sẽ tạo kiểm tra runtime và có thể ném `ClassCastException`, nhất là khi giá trị bị ép sang một kiểu cụ thể, hẹp hơn. Kết luận ở đây áp dụng cho **mẫu helper đang trình bày**, không phải lời bảo đảm rằng mọi cast generic đều an toàn.

Ngoài ra, helper chỉ ném cùng đối tượng; nếu có nhiều tầng bọc ngoại lệ khác trong chương trình, chúng có thể tạo cause hoặc stack trace riêng, nhưng đó là công việc của các tầng ấy chứ không tự xảy ra do suy luận kiểu.

## 8. Compile time và runtime có thể “bất đồng” về ngoại lệ

So sánh hai phát biểu sau:

| Câu hỏi | Compiler tại lời gọi `hiddenFromSignature()` | Runtime nếu helper ném `IOException` |
| --- | --- | --- |
| Có `throws IOException` tĩnh cần xử lý? | Không | Không dùng chữ ký để chặn đối tượng được ném |
| Có thể bắt bằng `catch (RuntimeException)` nếu đối tượng là `IOException`? | `catch` của RuntimeException có thể hợp lệ trong code | **Không**. `IOException` không là subtype của RuntimeException |
| Có thể bắt bằng `catch (Exception)` hoặc `catch (Throwable)`? | Các nhánh đủ rộng có thể hợp lệ | Có, vì `IOException` thuộc các nhánh ấy |
| Loại thực tế của đối tượng? | Không được suy ra chỉ từ lựa chọn `E` | `IOException` |
| Caller được compiler nhắc `catch IOException`? | Không trong đường sneaky đã kiểm chứng | Ngoại lệ có thể vẫn thoát nếu không có handler thực tế phù hợp |

Một `catch (RuntimeException ex)` được phép trong code không có checked `throws`, nhưng **nó không “hóa giải” `IOException`** đã bị ném lén. Nếu chương trình chỉ bắt `RuntimeException`, đối tượng `IOException` có thể tiếp tục lan truyền đến nơi xử lý ngoài cùng.

Tương tự, cơ chế dispatch `catch` của Java dựa trên **loại runtime của đối tượng**, không dựa trên kiểu `E` mà compiler đã suy ra cho một generic call trước đó.

## 9. Lỗi biên dịch bất ngờ: `catch (IOException)` không được coi là reachable

Tệp `CatchCheckedBad.java` sau đây được viết **cố ý không biên dịch**:

~~~java
import java.io.IOException;

class CatchCheckedBad {
    @SuppressWarnings("unchecked")
    static <E extends Throwable> void pass(Throwable t) throws E {
        throw (E) t;
    }

    static void run() {
        try {
            pass(new IOException("I/O"));
        } catch (IOException ex) {
            System.out.println("Đã bắt IOException");
        }
    }
}
~~~

Biên dịch:

~~~bash
javac -encoding UTF-8 CatchCheckedBad.java
~~~

Thông báo trọng tâm của `javac` Java 17:

~~~text
error: exception IOException is never thrown in body of corresponding try statement
~~~

**Tại sao?** Ở lời gọi generic `pass(new IOException(...))`, compiler có thể chọn `E` unchecked và không coi body của `try` là có checked `IOException` được khai báo. Vì vậy `catch (IOException)` **chỉ dựa trên lời gọi đó** bị xem là không hợp lệ tại compile time, dù ở runtime đối tượng `IOException` thực sự có thể được ném.

Đây là một trong những rủi ro rõ nhất: **hợp đồng thiếu trung thực khiến caller không thể viết ngay handler checked chuyên biệt theo cách tự nhiên**.

Cách sửa đúng nếu được quyền thay đổi API là **công bố `throws IOException` rõ ràng**, hoặc dùng type witness có kiểm soát trong phương thức đã khai báo `throws IOException`, như `explicitContract()` ở mục 6. Nếu bất đắc dĩ phải tương tác với code legacy dùng sneaky throw, cần hiểu rõ nơi ngoại lệ thoát và quyết định một boundary xử lý có thể kiểm tra được; đừng dùng `catch (Throwable)` tràn lan để che mất tất cả lỗi hệ thống.

## 10. Lambda không tự động bỏ qua checked exception

Một lambda chỉ có nghĩa khi gắn với **functional interface target type**. `Runnable.run()` không khai báo checked exception nào:

~~~java
@FunctionalInterface
interface Runnable {
    void run();
}
~~~

Đây là dạng chữ ký rút gọn của `java.lang.Runnable`, không phải lời khuyên định nghĩa lại `Runnable` trong code sử dụng. Vì thế:

~~~java
import java.io.IOException;

// CỐ Ý SAI:
class RunnableCheckedBad {
    Runnable x = () -> {
        throw new IOException("checked");
    };
}
~~~

không biên dịch. `javac` báo dạng:

~~~text
error: unreported exception IOException; must be caught or declared to be thrown
~~~

**Lý do:** body lambda có thể ném checked `IOException`, nhưng phương thức trừu tượng duy nhất `Runnable.run()` không cho phép ngoại lệ đó thoát.

Không thể sửa bằng cách thêm `throws IOException` vào method bao ngoài chứa phép gán lambda rồi kỳ vọng target type `Runnable` thay đổi. Thân lambda phải phù hợp với chữ ký của **functional interface** mà nó đang triển khai. Phương thức bao ngoài có `throws IOException` chỉ công bố ngoại lệ trực tiếp ở thân phương thức đó, không tự thay `Runnable.run()`.

Có ba cách thiết kế phù hợp: dùng functional interface có `throws` phù hợp, xử lý lỗi ngay trong lambda, hoặc bọc lỗi thành unchecked có nguyên nhân được bảo toàn.

## 11. Generic functional interface giúp lan truyền đúng checked exception

Định nghĩa:

~~~java
@FunctionalInterface
interface ThrowingAction<E extends Exception> {
    void run() throws E;
}
~~~

Và phương thức generic:

~~~java
static <E extends Exception> void perform(ThrowingAction<E> action) throws E {
    action.run();
}
~~~

Nếu lambda body ném `IOException`, suy luận cần phản ánh loại checked ấy:

~~~java
// Minh họa trong phương thức đã khai báo throws IOException hoặc có catch.
perform(() -> {
    throw new java.io.IOException("lambda I/O");
});
~~~

Nếu caller chỉ muốn viết:

~~~java
static void ordinaryCaller() {
    perform(() -> {
        throw new java.io.IOException("lambda I/O");
    });
}
~~~

thì đoạn này **không biên dịch** do checked exception chưa được xử lý. Đây là điều nên có: caller nhìn thấy trách nhiệm về lỗi từ callback.

Tình huống không có checked exception trong lambda, chẳng hạn lambda chỉ in log hoặc ném `IllegalStateException`, có thể suy luận kiểu phù hợp mà không yêu cầu thêm `throws IOException`.

## 12. Chương trình Java 17+ hoàn chỉnh: generic lambda làm đúng hợp đồng

Lưu thành `GenericLambdaDemo.java`:

~~~java
import java.io.IOException;

public class GenericLambdaDemo {
    @FunctionalInterface
    interface ThrowingAction<E extends Exception> {
        void run() throws E;
    }

    static <E extends Exception> void perform(ThrowingAction<E> action) throws E {
        action.run();
    }

    static void checkedCase() throws IOException {
        perform(() -> {
            throw new IOException("lambda I/O");
        });
    }

    static void uncheckedCase() {
        perform(() -> {
            throw new IllegalStateException("lambda trạng thái");
        });
    }

    static void noThrowCase() {
        perform(() -> System.out.println("Lambda bình thường"));
    }

    public static void main(String[] args) {
        try {
            checkedCase();
        } catch (IOException ex) {
            System.out.println("Đã bắt: " + ex.getMessage());
        }

        try {
            uncheckedCase();
        } catch (IllegalStateException ex) {
            System.out.println("Unchecked: " + ex.getMessage());
        }

        noThrowCase();
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac -encoding UTF-8 GenericLambdaDemo.java
java GenericLambdaDemo
~~~

Stdout chính xác:

~~~text
Đã bắt: lambda I/O
Unchecked: lambda trạng thái
Lambda bình thường
~~~

Phân tích:

1. `checkedCase()` chứa lambda thực sự ném `IOException`; compiler yêu cầu phương thức này khai báo hoặc bắt checked exception. Ở đây chữ ký có `throws IOException`.
2. `main()` bắt `IOException` do chữ ký `checkedCase()` công bố loại đó.
3. `uncheckedCase()` có lambda ném `IllegalStateException`; đây là unchecked, nên không cần khai báo checked `throws`.
4. `noThrowCase()` dùng lambda hoàn tất bình thường. Không có checked exception cần công bố.
5. Method generic `perform` **không làm biến mất lỗi**; nó truyền contract type `E` từ callback lên caller. Đây là mô hình generic `throws` minh bạch, trái với sneaky throw chủ động che loại checked.

Cùng nguyên lý có thể áp dụng cho **method reference** có `throws` phù hợp, ví dụ một method reference tới phương thức đọc dữ liệu checked được dùng với một functional interface có khai báo `throws IOException`. Chữ ký của target type vẫn là điều kiện quan trọng, không chỉ cú pháp lambda hay `::`.

## 13. Suy luận khi không có lỗi checked khác với cố tình giấu lỗi

Hai lời gọi có thể trông tương tự:

~~~java
// Không tạo checked exception trong lambda:
perform(() -> System.out.println("x"));
~~~

~~~java
// Có checked IOException trong lambda:
perform(() -> { throw new java.io.IOException("lỗi"); });
~~~

Trong lời gọi đầu, không có ràng buộc phải bao phủ `IOException`, nên compiler có thể suy luận một ngoại lệ unchecked thích hợp cho `E`. Trong lời gọi thứ hai, body lambda góp ràng buộc checked exception; compiler không thể tùy ý bỏ nó để vẫn coi chương trình đúng.

**Sneaky throw khác ở chỗ đối tượng checked được đưa vào tham số kiểu `Throwable`, không phải qua chính một lambda body được kiểm tra đối với `throws E`.** Từ đó thiếu ràng buộc làm `E` biểu diễn chính xác loại checked của đối tượng.

Hãy đánh giá API dựa vào đường truyền thông tin kiểu:

~~~text
Generic callback an toàn:
lambda throws IOException
        ↓ (ràng buộc checked exception)
ThrowingAction<E>.run() throws E
        ↓
perform(...) throws E
        ↓
caller phải catch/declare IOException

Sneaky helper:
IOException object → tham số Throwable
        ↓ (không ép E = IOException)
passThrough(Throwable) throws E
        ↓ (có thể suy ra E = RuntimeException)
caller không bị catch/declare IOException
        ↓
runtime vẫn ném đối tượng IOException
~~~

Sơ đồ mô tả **hợp đồng compile time**, không phải một chuỗi wrapper runtime; với sneaky helper không có đối tượng `RuntimeException` được tự tạo trên đường này.

## 14. Rủi ro của sneaky throw đối với hợp đồng public API

Vấn đề lớn nhất không phải là helper dài hay ngắn, mà là **người dùng API không thể hiểu đúng từ chữ ký**.

Ví dụ:

~~~java
public void process() {
    passThrough(new java.io.IOException("không kết nối được"));
}
~~~

Người đọc thấy `process()` không khai báo `throws IOException`. Nếu triển khai hoặc thư viện không công bố hành vi ngoại lệ lén, caller có thể:

- Chỉ bắt `RuntimeException` với giả định tất cả lỗi không khai báo là unchecked.
- Không triển khai cleanup hoặc rollback phù hợp với lỗi I/O.
- Không biết phải dùng boundary nào để bắt một `IOException` runtime.
- Ghi log thiếu phân loại khiến chẩn đoán khó hơn.
- Viết test kỳ vọng `RuntimeException` nhưng nhận `IOException`.

Quan trọng hơn, việc **refactor** có thể làm đổi kết quả compiler nếu ràng buộc suy luận kiểu ở điểm gọi thay đổi. Một lời gọi có explicit type argument `<IOException>` không giống một lời gọi để compiler tự suy luận; thay đổi generic signature cũng có thể thay đổi nghĩa vụ `throws`.

**Đừng coi unchecked inference là sự cho phép về hợp đồng.** Java có quy tắc suy luận đặc biệt, nhưng người thiết kế API vẫn chịu trách nhiệm cung cấp hành vi dễ hiểu và có thể kiểm thử.

## 15. Nguy cơ khi dùng sneaky throw với callback, Stream và thư viện bên ngoài

Các API Java chuẩn như `Runnable`, `Supplier<T>`, `Function<T,R>`, `Consumer<T>`, `Predicate<T>` **không công bố checked exceptions** trong abstract method chính. Nếu một lambda bên trong muốn gọi API ném `IOException`, compiler từ chối đường checked trực tiếp:

~~~java
// CỐ Ý SAI nếu readFromDisk() throws IOException.
java.util.function.Supplier<String> supplier = () -> readFromDisk();
~~~

Một số người chuyển sang helper sneaky để đưa checked exception qua lambda mà không sửa kiểu. Cách đó có thể chạy, nhưng làm callback **vi phạm kỳ vọng ngữ nghĩa** của người dùng API.

Ví dụ rủi ro với pipeline Stream:

- Biểu thức `map(...)` có thể bị trì hoãn cho đến terminal operation; exception thực tế phát sinh ở lúc pipeline được thực thi.
- Một pipeline có nhiều bước có thể làm vị trí lỗi khó lần ra.
- `catch (IOException)` quanh terminal operation có thể **không được compiler chấp nhận** nếu chỉ căn cứ vào chữ ký Stream API không khai báo checked `IOException`.
- Nếu dùng thư viện hoặc framework bất đồng bộ, ngoại lệ có thể còn được wrap, truyền qua future/result object hoặc log ở nơi khác theo hợp đồng riêng của thư viện.
- Code downstream có thể phân loại nhầm nếu chỉ dựa vào giả định “callback không thể ném checked exception”.

Không có quy tắc rằng mọi API Stream sẽ tự **chuyển `IOException` sang unchecked**. Cách ngoại lệ lan truyền trong Stream phụ thuộc vào đối tượng được ném và các tầng bao quanh; sneaky throw khiến checked exception có thể thoát qua vị trí mà chữ ký không phản ánh nó.

Trong ứng dụng sản phẩm, **bọc `IOException` thành `UncheckedIOException` một cách tường minh** là lựa chọn rõ ràng hơn nếu API buộc dùng `Function` không khai báo checked exception.

## 16. Thay thế thứ nhất: `UncheckedIOException` với nguyên nhân được giữ lại

Nếu một lambda dùng API I/O chuẩn cần phù hợp với `Function<T,R>`, một phương án là bắt `IOException` và chuyển thành `UncheckedIOException`:

~~~java
// Mã minh họa trong lambda có biến name.
try {
    return readName(name);
} catch (java.io.IOException ex) {
    throw new java.io.UncheckedIOException("Không đọc được " + name, ex);
}
~~~

`UncheckedIOException` là một lớp unchecked được Java cung cấp **dành riêng cho lỗi I/O được bọc**, có `getCause()` trả về `IOException` gốc. Cách này làm rõ cho caller rằng có một **unchecked wrapper** cần được xử lý theo hợp đồng API, thay vì lén chuyển một `IOException` qua chữ ký không công bố.

**Không dùng `UncheckedIOException` cho mọi checked exception**. Constructor của nó đòi `IOException` làm cause; với `SQLException` hoặc exception nghiệp vụ, cần chọn unchecked wrapper có ý nghĩa đúng hoặc thiết kế một result/callback interface khác.

## 17. Ví dụ Java 17+ hoàn chỉnh: adapter rõ ràng cho Stream

Tệp `CheckedLambdaAdapterDemo.java`:

~~~java
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.Function;

public class CheckedLambdaAdapterDemo {
    static String readName(String name) throws IOException {
        if ("missing".equals(name)) {
            throw new IOException("không đọc được " + name);
        }
        return name.toUpperCase();
    }

    static Function<String, String> reader() {
        return name -> {
            try {
                return readName(name);
            } catch (IOException ex) {
                throw new UncheckedIOException("Lỗi đọc: " + name, ex);
            }
        };
    }

    public static void main(String[] args) {
        try {
            System.out.println(List.of("ok", "missing").stream()
                    .map(reader()).toList());
        } catch (UncheckedIOException ex) {
            System.out.println("Bao ngoài: " + ex.getMessage());
            System.out.println("Nguyên nhân: " + ex.getCause().getMessage());
        }
    }
}
~~~

Lệnh Java 17:

~~~bash
javac -encoding UTF-8 CheckedLambdaAdapterDemo.java
java CheckedLambdaAdapterDemo
~~~

Stdout chính xác:

~~~text
Bao ngoài: Lỗi đọc: missing
Nguyên nhân: không đọc được missing
~~~

Giải thích:

1. `reader()` trả `Function<String,String>`, là kiểu mà `Stream.map()` sử dụng.
2. `readName("ok")` thành công và trả `"OK"`. `readName("missing")` ném `IOException`.
3. Lambda bắt `IOException` và **tạo một `UncheckedIOException` rõ ràng**, truyền nguyên nhân qua constructor.
4. Khi terminal operation `toList()` thực thi pipeline, unchecked wrapper thoát ra; không có danh sách được in vì hoạt động `map` không hoàn tất.
5. `catch (UncheckedIOException)` ở `main` hợp lệ và nhận đúng kiểu wrapper; `getCause()` cho biết lỗi I/O gốc.
6. Ví dụ cố ý mô phỏng lỗi bằng điều kiện trên tên, không phụ thuộc vào tệp thực tế. Đường đi của `List.of(...).stream()` trong ví dụ là tuần tự, giúp kết quả ổn định.

Nếu API gọi dùng `Stream` của dữ liệu thật, vẫn cần quyết định **fail-fast, tiếp tục từng phần, lưu lỗi cho từng phần tử hay trả một kết quả tổng hợp**. `UncheckedIOException` minh bạch hơn sneaky throw nhưng không tự quyết định chính sách nghiệp vụ.

## 18. Thay thế thứ hai: `ThrowingFunction` hoặc `ThrowingSupplier`

Nếu bạn kiểm soát API, có thể thiết kế functional interface có tham số exception:

~~~java
@FunctionalInterface
interface ThrowingSupplier<T, E extends Exception> {
    T get() throws E;
}
~~~

Và hàm xử lý:

~~~java
static <T, E extends Exception> T supply(
        ThrowingSupplier<T, E> supplier) throws E {
    return supplier.get();
}
~~~

Khi truyền lambda ném `IOException`:

~~~java
// Đoạn minh họa trong phương thức throws IOException.
String value = supply(() -> readName("missing"));
~~~

`readName` là phương thức có `throws IOException`. Khi được suy luận theo kiểu functional interface trên, `IOException` không “biến mất”: caller của `supply` phải xử lý hoặc khai báo theo hợp đồng thực tế.

**Nhược điểm:** `ThrowingSupplier` tùy chỉnh không phải lúc nào thay trực tiếp được `java.util.function.Supplier` trong API chuẩn. Bạn cần một adapter có chính sách rõ ràng ở ranh giới chuyển đổi, hoặc sử dụng các API phù hợp cho checked callback.

**Ưu điểm:** callback và caller nhìn thấy đường lỗi theo compile-time checking. Đây là lựa chọn đặc biệt hữu ích cho thư viện nội bộ được thiết kế quanh I/O, database hoặc thao tác có checked exception một cách tự nhiên.

## 19. Thay thế thứ ba: xử lý hoặc trả về kết quả có cấu trúc

Không phải mọi thất bại của một bước xử lý phải được ném qua nhiều tầng:

~~~java
// Ví dụ minh họa phương thức có thể trả về một kiểu kết quả.
record ReadResult(String value, String error) {
    static ReadResult success(String value) {
        return new ReadResult(value, null);
    }

    static ReadResult failure(String reason) {
        return new ReadResult(null, reason);
    }
}
~~~

Đây là một record Java 17 hợp lệ; code ví dụ không biểu diễn đầy đủ invariants của result object, nên trong hệ thống thật cần đảm bảo hai trạng thái thành công/thất bại không nhập nhằng.

Với batch processing, một cấu trúc kết quả chứa `value` hoặc `error` trên từng phần tử có thể hợp lý hơn việc để một checked exception thoát lén qua `Stream.map()`. Caller có thể:

- Thu thập cả thành công lẫn thất bại.
- Xác định phần tử nào cần retry.
- Không cần dùng exception để biểu diễn một kết quả dự kiến thường gặp.
- Có chính sách riêng cho partial success.

Cần chú ý tránh trả thành công giả tạo khi thao tác thực tế đã thất bại. Một lựa chọn tốt không chỉ làm compiler hài lòng; nó phải giữ **ngữ nghĩa thất bại**.

## 20. So sánh `sneaky throw`, checked generic API và unchecked wrapper

| Tiêu chí | Generic API `throws E` minh bạch | Sneaky throw | `UncheckedIOException` |
| --- | --- | --- | --- |
| Checked exception của callback có được biểu diễn ở lời gọi? | Có, khi constraints/type witness truyền đúng checked type | Có thể bị suy luận thành unchecked tại call site | Không cần checked `throws`; wrapper là unchecked |
| Loại đối tượng ném ra | Thường giữ nguyên ngoại lệ của callback | Giữ nguyên checked exception gốc | Tạo **đối tượng wrapper mới** |
| Caller biết nên `catch IOException`? | Có khi hợp đồng công bố `IOException` | Thường không, có thể lỗi compile khi bắt chuyên biệt | Caller bắt `UncheckedIOException`, tìm `IOException` qua cause |
| Cause/stack trace | Phụ thuộc đối tượng gốc; không bắt buộc wrapper | Không tự tạo cause/wrapper | Giữ cause `IOException`, wrapper có stack trace riêng |
| Rõ ràng về hợp đồng | Cao | Thấp nếu không tài liệu hóa rất rõ | Cao hơn nếu API ghi nhận loại wrapper |
| Nên dùng cho ứng dụng thông thường? | Có, khi checked phù hợp | Thường nên tránh | Có, ở boundary API không hỗ trợ checked và lỗi đúng loại I/O |
| Rủi ro chính | Signature generic phức tạp nếu lạm dụng | Che loại checked runtime khỏi compiler | Chuyển nghĩa vụ checked sang unchecked; caller phải biết hợp đồng runtime |

Không có phương án nào miễn phí. Với unchecked wrapper, compiler vẫn không **bắt buộc** caller `catch`; điều nó cải thiện là **tính minh bạch về đối tượng thực tế và cách lấy cause**, không phải tự động khôi phục nghĩa vụ checked exception.

## 21. Sneaky throw khác với precise rethrow của Java

Java có một cơ chế khác thường bị nhầm với sneaky throw: **phân tích ném lại ngoại lệ tương đối chính xác** (`precise rethrow`) trong các trường hợp `catch` phù hợp.

Ví dụ:

~~~java
import java.io.IOException;
import java.sql.SQLException;

// Đoạn minh họa trong lớp có hàm operation() ném hai loại checked.
static void process() throws IOException, SQLException {
    try {
        operation();
    } catch (Exception ex) {
        throw ex;
    }
}
~~~

Ở đây `operation()` cần được định nghĩa với `throws IOException, SQLException`, và biến `ex` không bị gán lại. Compiler có thể phân tích những loại checked exception cụ thể phát sinh từ vùng `try` để chấp nhận mệnh đề `throws IOException, SQLException` thay vì ép method công bố `throws Exception` rộng.

**Đây không phải sneaky throw**:

- Các loại checked phù hợp vẫn xuất hiện trong hợp đồng method.
- Caller vẫn phải bắt hoặc khai báo chúng.
- Không cố dùng type variable không bị ràng buộc để làm mất nghĩa vụ checked.
- Việc phân tích phụ thuộc những điều kiện JLS quy định, gồm tình trạng *effectively final* của tham số catch và khả năng ném từ vùng `try`.

Đừng gom mọi trường hợp compiler suy ra một `throws` hẹp hơn vào nhãn “sneaky”. Suy luận chính xác để **bảo toàn** hợp đồng khác với thủ thuật cố ý **che** loại ngoại lệ.

## 22. Sneaky throw khác với `throw new RuntimeException(ex)`

So sánh:

~~~java
// Minh họa trong catch (IOException ex).
throw new RuntimeException("Không thể đọc dữ liệu", ex);
~~~

và:

~~~java
// Minh họa trong cùng ngữ cảnh, helper passThrough(...) đã định nghĩa.
passThrough(ex);
~~~

Ở trường hợp thứ nhất:

- Tạo **một đối tượng `RuntimeException` mới**.
- Đối tượng mới có `IOException` làm `cause`.
- `catch (RuntimeException)` bên ngoài có thể bắt wrapper.
- `getCause()` và stack trace cho thấy cả tầng bọc lẫn lỗi I/O gốc.

Ở trường hợp thứ hai:

- Không tự tạo wrapper mới.
- Nếu helper ném `ex`, đối tượng thoát ra vẫn chính là `IOException`.
- `catch (RuntimeException)` sẽ **không** bắt `IOException`.
- Danh tính đối tượng và các dữ liệu trên ngoại lệ gốc vẫn được giữ nguyên.
- `throws` tĩnh của phương thức caller có thể không phản ánh `IOException`.

Kỹ thuật thứ hai dễ gây bất ngờ hơn, vì người dùng phương thức không thể suy từ chữ ký rằng một checked exception cụ thể sẽ thoát.

## 23. Type witness tường minh có thể thay đổi nghĩa vụ `throws`

Trở lại helper:

~~~java
static <E extends Throwable> void passThrough(Throwable failure) throws E {
    throw (E) failure;
}
~~~

Đây là **mã minh họa không gồm annotation suppress warning**; bản thân generic cast có thể sinh cảnh báo unchecked. Khi gọi:

~~~java
passThrough(new java.io.IOException("I/O"));
~~~

compiler có thể suy ra `E` là một loại unchecked nếu không có constraints cụ thể khác. Nhưng nếu viết:

~~~java
SomeClass.<java.io.IOException>passThrough(
        new java.io.IOException("I/O"));
~~~

với `SomeClass` là lớp khai báo `passThrough`, `E` đã được người gọi chỉ định là `IOException`, khiến lời gọi thuộc đường checked exception. Caller phải xử lý hoặc khai báo `IOException`.

Sự khác biệt này chỉ liên quan **kiểm tra tĩnh**: runtime vẫn nhận cùng một kiểu đối tượng ngoại lệ trong hai lời gọi nếu tham số được tạo giống nhau.

Tuy nhiên không nên dùng type witness như một “giải pháp chữa sneaky throw” cho mọi caller bên ngoài. Một public API được thiết kế tốt nên công bố hợp đồng lỗi trực tiếp, thay vì buộc người dùng hiểu nội bộ generic và gắn type argument đặc biệt để làm compiler nhận diện checked exception.

## 24. Sự thật về `@SuppressWarnings("unchecked")`

Tên annotation có thể khiến người mới học hiểu sai:

~~~java
@SuppressWarnings("unchecked")
static <E extends Throwable> void passThrough(Throwable t) throws E {
    throw (E) t;
}
~~~

Annotation trên:

- **Không chuyển** `IOException` thành unchecked exception.
- **Không sửa** `throws E` thành `throws RuntimeException`.
- **Không thay** runtime dispatch của `catch`.
- **Không đảm bảo** cast là an toàn xét kiểu cụ thể.
- Chỉ **ẩn cảnh báo compiler** thuộc loại unchecked cast trong vùng áp dụng.

Lý do caller không bị buộc xử lý `IOException` trong một số lời gọi nằm ở **chữ ký generic và kết quả suy luận `E`**, chứ không nằm ở annotation.

Khi xem code có `@SuppressWarnings("unchecked")`, đừng kết luận tự động có sneaky throw. Rất nhiều generic API hợp lệ cũng phải sử dụng unchecked cast có kiểm soát. Nhưng nếu annotation xuất hiện cạnh helper `<E extends Throwable> ... throws E` nhận một tham số `Throwable` độc lập với `E`, đó là dấu hiệu cần rà soát hợp đồng ngoại lệ.

## 25. Một số điều sai dễ gặp trong bài toán này

| Phát biểu hoặc thói quen | Vì sao không đúng? | Cách hiểu đúng |
| --- | --- | --- |
| “Generic `throws E` luôn bỏ checked exception.” | Lambda có thể góp checked constraints, type witness có thể đặt `E` cụ thể. | Xem toàn bộ các ràng buộc suy luận và target type. |
| “Sneaky throw đổi `IOException` thành `RuntimeException`.” | Loại runtime đối tượng không thay đổi. | Compiler có thể suy luận `E` unchecked, còn đối tượng vẫn là `IOException`. |
| “`catch (RuntimeException)` chắc chắn bắt được lỗi sneaky.” | `IOException` không thuộc nhánh RuntimeException. | Dispatch theo class runtime thực tế. |
| “Cứ viết `catch (IOException)` quanh lời gọi sneaky là được.” | Compiler có thể cho rằng `try` không ném checked IOException. | Công bố checked contract hoặc xử lý ở boundary phù hợp. |
| “Đã dùng `@SuppressWarnings` thì mọi thứ an toàn.” | Annotation chỉ ẩn cảnh báo, không sửa logic. | Đánh giá cast và hợp đồng độc lập. |
| “Lambda trong `Runnable` được ném IOException nếu method ngoài có throws IOException.” | Target method `Runnable.run()` không khai báo checked exception. | Dùng functional interface phù hợp hoặc xử lý/wrap trong lambda. |
| “`Function<T,R>` tự chuyển checked thành unchecked.” | API không có cơ chế biến đổi ngoại lệ như vậy. | Bọc rõ ràng nếu cần hoặc dùng API khác. |
| “Mọi generic method có throws type variable đều sneaky.” | Nhiều generic method truyền checked constraints đúng. | Phân biệt callback `throws E` có ràng buộc với helper tách `Throwable` khỏi `E`. |
| “Sneaky throw chỉ là vấn đề biên dịch, không có hậu quả runtime.” | Đối tượng checked vẫn có thể thoát và không được handler kỳ vọng bắt. | Thiết kế chính sách lỗi và kiểm thử đường runtime. |
| “Unchecked wrapper và sneaky throw giống nhau.” | Wrapper tạo đối tượng mới có cause, sneaky thường ném chính đối tượng gốc. | Phân biệt danh tính, cause và kiểu runtime. |
| “Đã suy luận `E=RuntimeException`, JVM sẽ checkcast đối tượng IOException sang RuntimeException.” | Không phải hành vi của helper generic được type-erasure ở đây. | Hiểu erasure và cơ chế ném `Throwable` ở JVM. |
| “Không cần giữ cause khi bọc.” | Mất ngữ cảnh và stack trace lỗi tầng dưới. | Dùng constructor `(message,cause)`. |
| “Cứ dùng `catch(Throwable)` để sửa tất cả.” | Có thể che cả `Error`, làm xử lý lỗi thiếu kiểm soát. | Sửa API hoặc dùng boundary được thiết kế riêng. |

## 26. Cách nhận diện sneaky throw trong code review

Một checklist thực dụng:

1. Có helper generic dạng `<E extends Throwable> ... throws E` không?
2. Type variable `E` có thực sự được ràng buộc bởi kiểu tham số/functional interface, hay chỉ xuất hiện trong `throws` và cast?
3. Phương thức nhận `Throwable` độc lập với `E` và dùng `throw (E) t` không?
4. Caller có không khai báo checked exception mặc dù truyền `IOException`, `SQLException` hoặc checked exception khác không?
5. Có `@SuppressWarnings("unchecked")` gần cast generic liên quan không?
6. Có API `Runnable`, `Supplier`, `Function`, `Stream.map` hoặc callback không công bố checked exception không?
7. Thực tế runtime exception nào được ném — loại gốc hay wrapper?
8. Handler bên ngoài chỉ bắt `RuntimeException` hay có chiến lược phù hợp với loại thực tế?
9. Người dùng public API có được tài liệu hóa rõ loại checked exception có thể thoát không?
10. Có lựa chọn minh bạch hơn: checked generic callback, `UncheckedIOException`, custom unchecked wrapper, result object hoặc xử lý cục bộ không?

Nếu helper được dùng ở một thư viện legacy và chưa thể thay đổi ngay, cần kiểm thử **những tình huống thất bại thực tế**, không chỉ compile code gọi. Ghi rõ trong tài liệu kỹ thuật điểm nào có thể ném checked exception ngoài chữ ký và lên kế hoạch loại bỏ sự lệch hợp đồng khi có thể.

## 27. Kiểm thử đường lỗi sao cho không bị compiler đánh lừa

Khi nghi ngờ một API có sneaky throw, nên có cả hai loại kiểm thử:

**Kiểm thử compile-time:** xác minh một số lời gọi hợp lệ hay bị từ chối như dự kiến. Ví dụ `RunnableCheckedBad.java` và `CatchCheckedBad.java` ở bài này **cố ý không biên dịch**. Đây là bằng chứng compiler không coi hai trường hợp đó tương đương với việc công bố `throws IOException`.

**Kiểm thử runtime:** tạo đối tượng checked exception cụ thể và quan sát đối tượng cuối cùng mà một boundary bắt được:

~~~java
// Đoạn minh họa với helper passThrough được định nghĩa.
try {
    passThrough(new java.io.IOException("sự cố"));
} catch (Throwable ex) {
    System.out.println(ex.getClass().getName());
}
~~~

Sơ đồ này chỉ dành cho test/chẩn đoán. Trong code sản phẩm, `catch(Throwable)` quá rộng cho hầu hết nghiệp vụ; tránh nuốt `OutOfMemoryError`, `ThreadDeath` hoặc những loại `Error` không nên xử lý theo cách lỗi nghiệp vụ thông thường.

Với unchecked wrapper đúng thiết kế, có thể assert `ex instanceof UncheckedIOException` và `ex.getCause() instanceof IOException`. Với sneaky helper, có thể kiểm tra **cùng một đối tượng Throwable** được ném ra, nhưng phép thử đó không chứng minh API nên được dùng theo cách ấy.

## 28. Câu hỏi tự kiểm tra

**Câu 1.** `IOException` có tự trở thành `RuntimeException` khi đi qua sneaky helper không?

**Đáp án:** Không. Runtime object vẫn là `IOException`; chỉ việc suy luận `throws E` tại compile time có thể không buộc caller xử lý checked exception.

**Câu 2.** Một lambda `() -> { throw new IOException(); }` có gán thẳng cho `Runnable` được không?

**Đáp án:** Không, vì `Runnable.run()` không khai báo checked `IOException`.

**Câu 3.** Vì sao `perform(() -> { throw new IOException(); })` với `ThrowingAction<E>` có thể khiến caller phải xử lý IOException?

**Đáp án:** Checked exception từ body lambda tạo ràng buộc đối với kiểu `E` trong `throws` của target functional interface và generic method.

**Câu 4.** Trong sneaky helper nhận `Throwable`, tại sao tham số là `Throwable` quan trọng?

**Đáp án:** Vì đối số `new IOException()` không trực tiếp bắt buộc `E=IOException`; type variable trong `throws E` có thể được suy ra unchecked khi không có ràng buộc khác.

**Câu 5.** Có thể tự động bắt đối tượng sneaky `IOException` bằng `catch(RuntimeException)` không?

**Đáp án:** Không. Catch dựa vào kiểu runtime, mà `IOException` không phải subtype của `RuntimeException`.

**Câu 6.** Nếu chỉ có lời gọi sneaky trong `try`, `catch(IOException)` có thể bị compiler từ chối không?

**Đáp án:** Có. Trong ví dụ kiểm chứng, `javac` báo `IOException` không được coi là ngoại lệ checked có thể ném từ thân `try` theo thông tin tĩnh.

**Câu 7.** `@SuppressWarnings("unchecked")` có phải thứ làm `throws E` trở thành unchecked không?

**Đáp án:** Không. Nó chỉ ẩn cảnh báo cast; kết quả là do suy luận kiểu và các ràng buộc generic.

**Câu 8.** Gọi `SneakyThrowDemo.<IOException>passThrough(...)` khác gì để compiler tự suy luận?

**Đáp án:** Type witness chỉ rõ `E=IOException`; lời gọi được kiểm tra theo checked `throws IOException`.

**Câu 9.** Vì sao `UncheckedIOException` thường minh bạch hơn sneaky throw trong Stream?

**Đáp án:** Vì caller thấy một **loại runtime ngoại lệ wrapper có chủ đích**, và lỗi I/O gốc được giữ ở `getCause()`, thay vì một checked exception thoát khỏi chữ ký không công bố nó.

**Câu 10.** Generic `throws E` có luôn là anti-pattern không?

**Đáp án:** Không. Nó hữu ích và đúng ngữ nghĩa với functional interface/callback có khai báo `throws E` và truyền checked constraints tới caller.

**Câu 11.** Precise rethrow `catch(Exception ex) { throw ex; }` có giống sneaky throw không?

**Đáp án:** Không. Precise rethrow suy ra các checked exception thực sự được vùng `try` phép ném theo điều kiện JLS; caller vẫn nhận hợp đồng `throws` đúng.

**Câu 12.** Khi refactor một public API đang dùng sneaky throw, nên làm gì?

**Đáp án:** Xác định những exception runtime thực sự thoát ra, quyết định contract checked hoặc unchecked minh bạch, giữ nguyên cause nếu bọc, cập nhật test/caller và công bố thay đổi tương thích.

## 29. Tài liệu tham khảo chính thức

- [Java Language Specification 17 — §18.1.3, Bounds](https://docs.oracle.com/javase/specs/jls/se17/html/jls-18.html#jls-18.1.3): mô tả bound thông tin `throws α` khi inference variable xuất hiện trong `throws`.
- [Java Language Specification 17 — §18.2.5, Checked Exception Constraints](https://docs.oracle.com/javase/specs/jls/se17/html/jls-18.html#jls-18.2.5): ràng buộc checked exception từ lambda/method reference.
- [Java Language Specification 17 — §18.4, Resolution](https://docs.oracle.com/javase/specs/jls/se17/html/jls-18.html#jls-18.4): khi không có lower bounds phù hợp và upper bounds cho phép, một inference variable trong `throws` có thể được gán `RuntimeException`.
- [Java Language Specification 17 — §11.2, Compile-Time Checking of Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.2): quy tắc kiểm tra checked exception.
- [Java Language Specification 17 — §15.27.3, Type of a Lambda Expression](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.27.3): lambda và target functional interface.
- [Java Language Specification 17 — §4.6, Type Erasure](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.6): ý nghĩa type erasure.
- [Java SE 17 API — `java.io.UncheckedIOException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/UncheckedIOException.html): unchecked wrapper có `IOException` làm cause.
- [Java SE 17 API — `java.util.function.Function`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/function/Function.html): functional interface dùng trong Stream, không có checked `throws` ở phương thức `apply`.

**Kết luận:** Generic `throws E` là tính năng thiết kế API hợp lệ, đặc biệt khi kết hợp với lambda và functional interface có checked exceptions. **Sneaky throw** xuất hiện khi type variable dùng trong `throws` không bị ràng buộc đủ để giữ loại ngoại lệ checked; compiler có thể suy ra kiểu unchecked, còn runtime vẫn ném đối tượng `IOException` thật. Vì rủi ro đối với hợp đồng và handler của caller, nên ưu tiên **catch-or-declare minh bạch, callback có `throws E`, unchecked wrapper đúng ngữ nghĩa hoặc result object có cấu trúc** thay vì che checked exception.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lambda-generic-exceptions-009">Giữ hợp đồng rõ ràng khi kết hợp Stream và thao tác có thể thất bại</a>

<details>
<summary>Xem chi tiết</summary>

# Giữ hợp đồng rõ ràng khi kết hợp Stream và thao tác có thể thất bại trong Java

`Stream` giúp mô tả chuỗi xử lý dữ liệu dưới dạng các thao tác `map`, `filter`, `flatMap`, `forEach` và các phép toán kết thúc. Nhưng khi **một thao tác trên phần tử có thể thất bại**, lựa chọn cách truyền lỗi trở thành một phần quan trọng của **hợp đồng phương thức**. Nếu không xác định rõ, chương trình dễ nuốt lỗi, trả kết quả thiếu phần tử mà không giải thích, mất nguyên nhân gốc hoặc đóng tài nguyên sai thời điểm.

Vấn đề nổi bật nhất trong Java là các phương thức như `Stream.map` dùng `Function.apply`, trong khi `Function.apply` **không khai báo checked exception**. Một thao tác đọc tệp hoặc parse theo API khai báo `throws IOException` / checked exception tùy chỉnh vì thế **không thể tự động ném loại checked đó xuyên qua lambda của `map`**.

Có ba chính sách thiết kế thường dùng:

1. **Fail-fast:** dừng pipeline ở lỗi đầu tiên và lan truyền một exception có ý nghĩa; thường dùng khi chỉ cần tất cả dữ liệu đều đúng thì mới có kết quả.
2. **Kết quả trên từng phần tử:** chuyển từng phần tử thành `Success` hoặc `Failure` có cấu trúc, để xử lý hết dữ liệu và tổng hợp các lỗi mong đợi.
3. **Phục hồi có chủ đích:** bỏ qua, thay thế hay thử lại một phần tử **chỉ khi hợp đồng cho phép** và phải ghi nhận đủ ngữ cảnh để không tạo thành công giả.

Không có một kiểu xử lý đúng cho mọi pipeline. Điều cần giữ rõ ràng là: **lỗi nào được xem là thất bại của cả thao tác, lỗi nào là dữ liệu của từng phần tử, lỗi nào được phục hồi, và bên nào chịu trách nhiệm đóng tài nguyên**.

## 1. Hiểu đúng Stream trước khi bàn về ngoại lệ

Một pipeline điển hình:

```java
List<Integer> values = List.of("2", "3", "4").stream()
        .map(Integer::parseInt)
        .filter(n -> n > 2)
        .toList();
```

Đoạn này hợp lệ với Java 17+. `Integer.parseInt` có thể ném `NumberFormatException`, nhưng đó là **unchecked exception**, vì vậy việc truyền method reference vào `map` không vi phạm quy tắc checked exception.

`Stream` là mô hình xử lý dữ liệu, không phải tự thân là một cấu trúc xử lý lỗi. Cần phân biệt:

- **Nguồn**: `Collection.stream()`, `Stream.of(...)`, `Files.lines(...)`...
- **Intermediate operations**: `map`, `filter`, `peek`, `flatMap`; nói chung chỉ dựng các bước xử lý, chưa kích hoạt việc duyệt nguồn.
- **Terminal operations**: `toList`, `collect`, `count`, `forEach`, `findFirst`, `anyMatch`...; khi chạy sẽ kích hoạt các phần pipeline cần thiết để cho ra kết quả.
- **Một lần sử dụng**: một stream sau khi đã tiêu thụ không được xem là pipeline có thể tái sử dụng; muốn xử lý lần nữa phải tạo stream mới từ nguồn phù hợp.
- **Close/ownership**: phần lớn stream trên collection không cần đóng, nhưng các stream được tạo từ nguồn I/O, ví dụ `Files.lines`, có thể nắm tài nguyên cần `close()`.

**Đặc biệt:** pipeline có thể không duyệt hết mọi phần tử: `findFirst` hoặc `anyMatch` có thể kết thúc sớm, và một exception cũng có thể cắt ngang quá trình. Không nên suy luận rằng gọi một terminal operation luôn khiến mọi lambda chạy một lần trên từng phần tử.

## 2. Vì sao checked exception không đi xuyên qua `map` thông thường?

Chữ ký liên quan về ý nghĩa:

```java
<R> Stream<R> map(
        java.util.function.Function<? super T, ? extends R> mapper);
```

Đây là **chữ ký minh họa rút gọn** của API `Stream`. Kiểu `Function<T,R>` có phương thức hàm:

```java
R apply(T value);
```

Phương thức này **không có `throws IOException`, `throws SQLException` hoặc `throws Exception`**. Vì thế:

```java
static String load(String id) throws java.io.IOException {
    return java.nio.file.Files.readString(
            java.nio.file.Path.of(id));
}
```

là một phương thức Java hợp lệ khi đặt trong lớp. Nhưng dùng nó thế này:

```java
// Cố ý KHÔNG BIÊN DỊCH.
List<String> texts = ids.stream()
        .map(id -> load(id))
        .toList();
```

sẽ không hợp lệ nếu `ids` là `List<String>` và `load` khai báo `throws IOException`, vì body lambda có thể ném checked exception mà `Function.apply` không cho phép.

### Thêm `throws IOException` bên ngoài chưa đủ

Ví dụ:

```java
// Cố ý KHÔNG BIÊN DỊCH.
static List<String> loadAll(List<String> ids)
        throws java.io.IOException {

    return ids.stream()
            .map(id -> load(id))
            .toList();
}
```

Dù `loadAll` có `throws IOException`, lambda vẫn phải tương thích **hợp đồng của `Function.apply`**. `throws` của phương thức bao ngoài không được “truyền” tự động vào function descriptor của `map`.

Điểm cần nhớ: **nghĩa vụ checked exception được xác định theo kiểu hàm đích tại chỗ tạo lambda**. Đây là cơ chế compile-time, không phải lỗi chỉ phát sinh khi chương trình chạy.

## 3. Chọn hợp đồng fail-fast cho dữ liệu bắt buộc chính xác

Giả sử một thao tác cần chuyển các chuỗi thành số lượng đơn hàng dương. Nếu một dòng sai, có thể quy định rằng **toàn bộ thao tác thất bại**; caller không nhận một danh sách “có vẻ thành công” nhưng âm thầm thiếu dòng.

Với parser chỉ ném unchecked exception:

```java
List<Integer> quantities = List.of("2", "oops", "5")
        .stream()
        .map(Integer::parseInt)
        .toList();
```

Khi pipeline được thực thi, `Integer.parseInt("oops")` ném `NumberFormatException`; `toList()` không trả một `List<Integer>` bình thường cho caller. Việc có phần tử `2` đã xử lý trong nội bộ **không bảo đảm có một danh sách kết quả hợp lệ được trả về**.

Nhược điểm: nếu không bổ sung ngữ cảnh, message/stack trace có thể không nêu rõ **ID dòng** nào gây lỗi. Lúc đó nên dùng một loại exception mang thông tin có cấu trúc.

### Một wrapper có ý nghĩa

```java
static int parseQuantity(String id, String raw) {
    try {
        int value = Integer.parseInt(raw);
        if (value <= 0) {
            throw new IllegalArgumentException(
                    "quantity must be positive");
        }
        return value;
    } catch (NumberFormatException cause) {
        throw new IllegalArgumentException(
                "invalid quantity for item " + id,
                cause);
    }
}
```

Đây là một **phương thức minh họa có thể biên dịch khi đặt trong một lớp**. Nó giữ `NumberFormatException` làm cause và thêm ID mặt hàng. Tuy nhiên, với dữ liệu nhạy cảm, chỉ nên đưa mã định danh không bí mật hoặc mã tương quan vào message; không nên ghi raw token hay toàn bộ payload.

Nếu muốn xử lý bằng chương trình theo mã thay vì parse message, có thể dùng custom exception có `errorCode` và `itemId`. Ví dụ hoàn chỉnh phía dưới sẽ làm rõ điều đó.

## 4. Lỗi phát sinh khi terminal operation chạy: hệ quả của tính lazy

Ví dụ:

```java
var pipeline = List.of("1", "bad", "3")
        .stream()
        .map(Integer::parseInt);
```

Ngay sau khi khai báo `pipeline`, việc parse các phần tử **chưa nhất thiết xảy ra**. Lời gọi:

```java
List<Integer> result = pipeline.toList();
```

mới là phép kết thúc kích hoạt quá trình cần thiết. `NumberFormatException` được quan sát tại thời điểm terminal operation thực thi, dù stack trace cũng có frame lambda/`Integer.parseInt`.

Hệ quả thực hành:

- Đặt `try-catch` quanh nơi **pipeline được tiêu thụ**, không chỉ nơi pipeline được xây dựng.
- Nếu một phương thức chỉ trả về `Stream` còn caller mới gọi terminal operation, thời điểm lỗi có thể thuộc về caller, không còn nằm trong `try-catch` của phương thức tạo stream.
- Không nên trả `Stream` đọc lười từ tài nguyên đã đóng, vì caller mới thực thi pipeline sau khi phương thức tạo nguồn đã rời phạm vi.
- Một phép kết thúc dạng short-circuit có thể không chạm tới phần tử lỗi; **không thấy exception chưa đủ để khẳng định mọi phần tử đều hợp lệ**.

### `findFirst` không tự kiểm tra toàn bộ nguồn

```java
var first = List.of("2", "bad", "4")
        .stream()
        .map(Integer::parseInt)
        .findFirst();
```

Đây là Java hợp lệ. Với sequential ordered stream này, `findFirst()` có thể trả `Optional.of(2)` mà không parse `"bad"`. Nó chỉ cần phần tử đầu để hoàn tất. Không có ngoại lệ **không có nghĩa** toàn bộ danh sách đã vượt qua validation.

Nếu cần xác thực tất cả phần tử, hãy chọn terminal operation và chính sách validation thực sự cần xử lý tất cả, hoặc mô hình hóa từng kết quả để ghi nhận lỗi từng dòng.

## 5. Ví dụ Java 17+ hoàn chỉnh: fail-fast, lazy và giữ nguyên nhân

Ví dụ dùng một custom **checked exception** trong hàm parser, sau đó chuyển nó thành unchecked wrapper khi buộc phải truyền vào `Stream.map`.

Lưu tệp **`StreamFailFastDemo.java`**:

```java
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

public class StreamFailFastDemo {

    record Item(String id, String raw) {}

    static final class InvalidItemException extends Exception {
        private static final long serialVersionUID = 1L;

        private final String itemId;

        InvalidItemException(
                String itemId,
                String message,
                Throwable cause) {
            super(message, cause);
            this.itemId = itemId;
        }

        String getItemId() {
            return itemId;
        }
    }

    static final class ItemProcessingException
            extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ItemProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @FunctionalInterface
    interface CheckedFunction<T, R, E extends Exception> {
        R apply(T value) throws E;
    }

    static int parse(Item item) throws InvalidItemException {
        System.out.println("parse " + item.id());

        final int parsed;
        try {
            parsed = Integer.parseInt(item.raw());
        } catch (NumberFormatException cause) {
            throw new InvalidItemException(
                    item.id(), "not an integer", cause);
        }

        if (parsed <= 0) {
            throw new InvalidItemException(
                    item.id(), "must be positive", null);
        }

        return parsed;
    }

    static <T, R, E extends Exception> Function<T, R> failFast(
            CheckedFunction<T, R, E> action) {

        return value -> {
            try {
                return action.apply(value);
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception cause) {
                throw new ItemProcessingException(
                        "stream conversion failed", cause);
            }
        };
    }

    public static void main(String[] args) {
        List<Item> items = List.of(
                new Item("A", "3"),
                new Item("B", "bad"),
                new Item("C", "5"));

        System.out.println("created pipeline");

        Stream<Integer> pipeline = items.stream()
                .map(failFast(StreamFailFastDemo::parse));

        System.out.println("before terminal");

        try {
            List<Integer> values = pipeline.toList();
            System.out.println("values=" + values);
        } catch (ItemProcessingException ex) {
            InvalidItemException cause =
                    (InvalidItemException) ex.getCause();

            System.out.println("failure=" + ex.getMessage());
            System.out.println("failing id=" + cause.getItemId());
            System.out.println("detail=" + cause.getMessage());
            System.out.println(
                    "root=" + cause.getCause()
                            .getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy với **JDK 17 trở lên**:

```bash
javac -encoding UTF-8 StreamFailFastDemo.java
java StreamFailFastDemo
```

**Stdout dự kiến chính xác:**

```text
created pipeline
before terminal
parse A
parse B
failure=stream conversion failed
failing id=B
detail=not an integer
root=NumberFormatException
```

### Vì sao không có `parse C`?

Pipeline tuần tự này bắt đầu xử lý phần tử A, tiếp tục B, rồi parser B ném `InvalidItemException`. Adapter `failFast` bọc thành `ItemProcessingException` và ngoại lệ thoát khỏi `toList()`. Vì terminal operation thất bại, chương trình không tiếp tục tới phần tử C, cũng không in `values=[...]`.

Đây là **hợp đồng fail-fast**: dừng ở lỗi đầu tiên, không làm như đã chuyển thành công tất cả dữ liệu.

### Những thông tin lỗi được giữ lại

- Exception ngoài: `ItemProcessingException("stream conversion failed")` — diễn đạt thất bại ở tầng stream.
- Cause trực tiếp: `InvalidItemException` — thuộc tầng validation và có `itemId="B"`.
- Cause sâu: `NumberFormatException` — lỗi parse kỹ thuật ban đầu.
- Caller có thể đọc `getItemId()` thay vì phân tích message `"not an integer"`.

Trong ứng dụng thật, không cần lúc nào cũng tạo cả ba lớp lỗi. Mức bọc phụ thuộc ranh giới trừu tượng và trách nhiệm của caller. Ví dụ cố tình có nhiều tầng để thấy cơ chế bảo toàn cause và ngữ cảnh.

### Tại sao adapter bắt `RuntimeException` riêng?

`RuntimeException` kế thừa `Exception`. Nếu adapter chỉ bắt mọi `Exception` rồi bọc lại, nó sẽ bọc cả lỗi unchecked không liên quan, có thể che lớp lỗi có ý nghĩa hoặc làm stack trace khó đọc. Ví dụ giữ nguyên unchecked lỗi gốc, và chỉ bọc checked exception cần chuyển hợp đồng.

Không nên dùng adapter “sneaky throw” để lách kiểm tra checked exception mà không công bố gì với caller. Kể cả khi runtime có thể ném lỗi, kiểu `Function` ở compile time không thể hiện trách nhiệm xử lý checked exception đó.

## 6. Khi nào không nên dùng fail-fast?

Giả sử một tệp nhập liệu có 10.000 bản ghi, người dùng muốn sửa **toàn bộ bản ghi không hợp lệ trong một lượt**. Dừng ở lỗi đầu tiên buộc họ sửa một dòng, chạy lại, rồi tiếp tục thấy lỗi dòng khác. Trường hợp này **thu thập kết quả theo từng phần tử** có thể phù hợp hơn.

Một hợp đồng rõ ràng:

```text
Input item
   |
   v
parse/validate
   |
   +----> Success(itemId, parsedValue)
   |
   +----> Failure(itemId, errorCode, safeMessage, cause?)
```

Trong mô hình này, lỗi dự kiến như dữ liệu không phải số hoặc số lượng ngoài phạm vi **không nhất thiết được ném xuyên khỏi pipeline**. Chúng được biểu diễn bằng **giá trị kết quả**.

Lợi ích:

- Có thể tiếp tục xử lý phần tử tiếp theo sau một lỗi validation của phần tử trước.
- Caller nhận danh sách lỗi kèm ID và mã lỗi ổn định.
- Không cần dùng chuỗi `getMessage()` để phân loại.
- Có thể đếm tổng số thành công/thất bại, hiển thị lỗi theo từng dòng.
- Phân biệt lỗi **dự kiến của dữ liệu** với lỗi **bất thường của hạ tầng**.

Tuy nhiên, mô hình này **không tự động bảo đảm tính nguyên tử hoặc tính giao dịch**. Nếu pipeline đã ghi dữ liệu bên ngoài theo từng phần tử, việc tạo `Failure` sau đó không rollback các tác động trước đó.

## 7. Dùng `sealed interface` và `record` cho kết quả có cấu trúc

Java 17 hỗ trợ `record` và `sealed interface`. Chúng giúp định nghĩa một hợp đồng kết quả rõ ràng:

```java
sealed interface ParseOutcome permits Success, Failure {}

record Success(String itemId, int value)
        implements ParseOutcome {}

record Failure(String itemId, String code, String message)
        implements ParseOutcome {}
```

Đây là các kiểu minh họa. Một hàm `parse` có thể trả `ParseOutcome` mà không ném ngoại lệ checked cho các lỗi validation dự kiến.

Dùng kiểu trả về này trong `Stream.map`:

```java
List<ParseOutcome> outcomes = items.stream()
        .map(MyParser::parse)
        .toList();
```

Lời gọi là **minh họa**, giả định `MyParser.parse` và `items` có chữ ký phù hợp.

### Có nên nhét `Throwable` vào mọi `Failure`?

Không bắt buộc. Với dữ liệu nhập sai do người dùng, một `Failure(code, field, safeMessage)` có thể đủ để trả về giao diện. Nếu cần chẩn đoán kỹ thuật, có thể lưu cause nội bộ trong kết quả hoặc log có cấu trúc tại đúng ranh giới. Nhưng **không tự đưa nguyên Throwable vào DTO API công khai**.

Cũng tránh giữ input bí mật trong record kết quả; mọi record có `toString()` tự sinh có thể khiến các field bị in vào log.

## 8. Ví dụ Java 17+ hoàn chỉnh: thu thập mọi lỗi hợp lệ về nghiệp vụ

Lưu tệp **`StreamResultsDemo.java`**:

```java
import java.util.List;

public class StreamResultsDemo {

    record Item(String id, String raw) {}

    enum ErrorCode {
        INVALID_INTEGER,
        NOT_POSITIVE
    }

    sealed interface Outcome permits Success, Failure {}

    record Success(String itemId, int value)
            implements Outcome {}

    record Failure(
            String itemId,
            ErrorCode code,
            String message,
            Throwable cause)
            implements Outcome {}

    static Outcome parse(Item item) {
        final int number;

        try {
            number = Integer.parseInt(item.raw());
        } catch (NumberFormatException cause) {
            return new Failure(
                    item.id(),
                    ErrorCode.INVALID_INTEGER,
                    "quantity must be an integer",
                    cause);
        }

        if (number <= 0) {
            return new Failure(
                    item.id(),
                    ErrorCode.NOT_POSITIVE,
                    "quantity must be positive",
                    null);
        }

        return new Success(item.id(), number);
    }

    public static void main(String[] args) {
        List<Item> items = List.of(
                new Item("A", "2"),
                new Item("B", "bad"),
                new Item("C", "0"),
                new Item("D", "4"));

        List<Outcome> results = items.stream()
                .map(StreamResultsDemo::parse)
                .toList();

        for (Outcome result : results) {
            if (result instanceof Success ok) {
                System.out.println(
                        "OK " + ok.itemId() + "=" + ok.value());
            } else if (result instanceof Failure bad) {
                String root = bad.cause() == null
                        ? "none"
                        : bad.cause().getClass().getSimpleName();

                System.out.println(
                        "ERR " + bad.itemId() + " "
                        + bad.code() + " cause=" + root);
            }
        }

        long successCount = results.stream()
                .filter(result -> result instanceof Success)
                .count();

        long failureCount = results.stream()
                .filter(result -> result instanceof Failure)
                .count();

        System.out.println("successes=" + successCount);
        System.out.println("failures=" + failureCount);
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 StreamResultsDemo.java
java StreamResultsDemo
```

**Stdout chính xác:**

```text
OK A=2
ERR B INVALID_INTEGER cause=NumberFormatException
ERR C NOT_POSITIVE cause=none
OK D=4
successes=2
failures=2
```

### Phân tích

- A và D parse thành số dương nên được biểu diễn bằng `Success`.
- B có chuỗi `"bad"`. `NumberFormatException` được bắt trong parser và **chuyển thành dữ liệu `Failure`** với mã `INVALID_INTEGER`.
- C parse thành `0` nhưng vi phạm quy tắc dương; trả `Failure` mã `NOT_POSITIVE`. Đây là lỗi validation trực tiếp nên cause có thể để `null`.
- Parser xử lý đủ bốn phần tử: không có unchecked exception thoát ra để ngắt stream ở B hoặc C.
- `successCount` và `failureCount` được tính bằng **hai stream mới từ danh sách `results`**, không cố tái sử dụng một stream đã tiêu thụ.
- `ErrorCode` là dữ liệu có cấu trúc. Có thể thay câu message hoặc bản dịch mà nhánh xử lý không cần phân tích chuỗi.

Một chính sách quan trọng: **khi parser bắt `NumberFormatException`, đó là một lỗi dữ liệu dự kiến**. Chương trình không nên tiếp tục bắt tất cả `Throwable` để biến cả lỗi hệ thống như `OutOfMemoryError` thành một dòng `Failure` bình thường.

## 9. Tổng hợp kết quả ở ranh giới, không trả dữ liệu thiếu mà im lặng

Trong mô hình thu thập kết quả, sau khi có `List<Outcome>`, caller cần quyết định:

- Nếu **mọi phần tử thành công**, trả danh sách giá trị theo thứ tự cần thiết.
- Nếu có **bất kỳ Failure nào**, trả danh sách lỗi và không commit dữ liệu, nếu tác vụ yêu cầu tất-cả-hoặc-không-gì.
- Nếu hợp đồng chấp nhận **partial success**, trả cả các thành công và thất bại kèm trạng thái rõ ràng.
- Nếu một lỗi thuộc về **hạ tầng** hoặc lỗi không thể khôi phục, có thể vẫn cần fail-fast thay vì cố tiếp tục.

Ví dụ nguy hiểm:

```java
List<Integer> values = inputs.stream()
        .map(Parser::tryParse)
        .filter(java.util.Objects::nonNull)
        .toList();
```

Đây là đoạn minh họa, với `Parser.tryParse` giả định trả `null` khi parse thất bại. Nếu API không công bố quy tắc bỏ qua dữ liệu lỗi, kết quả có ít phần tử hơn nhưng caller **không biết những phần tử nào thất bại và vì sao**.

Thay vào đó, nếu bỏ qua dòng lỗi là chủ đích, ít nhất cần giữ log/đếm/diagnostic đúng chính sách và thể hiện điều đó trong tên API hoặc loại kết quả. Với yêu cầu audit, một tập `Success/Failure` thường rõ ràng hơn.

### `Optional` không thay cho tất cả thông tin lỗi

`Optional<T>` có thể biểu diễn “có hoặc không có giá trị”, nhưng **không mang loại lỗi, vị trí lỗi hoặc nguyên nhân**. Nếu caller cần phân biệt thiếu dữ liệu hợp lệ với dữ liệu không hợp lệ hay I/O thất bại, chỉ trả `Optional.empty()` có thể quá ít thông tin.

Sử dụng `Optional` khi việc không có giá trị là một kết quả bình thường đúng hợp đồng; dùng `Failure` có cấu trúc khi **nguyên nhân thất bại có ý nghĩa**.

## 10. Lỗi phần tử và lỗi pipeline không phải một loại

Một pipeline có thể chịu nhiều nguồn lỗi:

| Nguồn lỗi | Ví dụ | Hợp đồng nên cân nhắc |
| --- | --- | --- |
| Lỗi validation từng phần tử | `"abc"` không phải số | `Failure(itemId, INVALID_INTEGER)` hoặc fail-fast |
| Không tìm thấy một bản ghi được phép thiếu | Kết quả tra cứu không có | `Optional` hoặc kết quả “not found” có cấu trúc |
| I/O không đọc được nguồn | `Files.lines` không mở được file | Checked `IOException` ở ranh giới tạo nguồn |
| I/O trong quá trình duyệt | Lỗi khi đọc dòng sau lúc mở | Có thể xuất hiện unchecked `UncheckedIOException` trong pipeline |
| Lỗi parse logic | Parser ném `IllegalStateException` do bug | Không biến mặc định thành lỗi nhập liệu |
| Lỗi trong `close()` | Reader đóng thất bại | Xem ngoại lệ lúc đóng và suppressed |
| Lỗi side effect | API ghi dữ liệu giữa `forEach` | Cân nhắc giao dịch, idempotency và hậu quả một phần |

Một API chung `processAll` không nên che tất cả các trường hợp thành `List.empty()` hoặc `null`. Phải cho caller biết **điều kiện trả kết quả thành công là gì**.

## 11. Stream và nguồn tài nguyên: `Files.lines` cần đóng đúng phạm vi

Khác với `List.stream()`, một số stream có nguồn I/O cần được đóng. Ví dụ:

```java
static List<String> readNonBlankLines(
        java.nio.file.Path path)
        throws java.io.IOException {

    try (java.util.stream.Stream<String> lines =
                 java.nio.file.Files.lines(path)) {

        return lines.filter(line -> !line.isBlank())
                .toList();
    }
}
```

Đây là **phương thức Java minh họa hợp lệ khi đặt trong lớp**. `Files.lines(path)` tạo stream có tài nguyên file cần đóng; `try-with-resources` bảo đảm đóng khi rời phạm vi, kể cả khi `toList()` ném ngoại lệ.

Các điểm cần phân biệt:

1. **Mở nguồn**: lời gọi `Files.lines(path)` có thể ném checked `IOException` ngay khi mở.
2. **Đọc trong lúc duyệt**: lỗi I/O xảy ra sau khi mở có thể được bọc vào `UncheckedIOException` khi stream thực hiện terminal operation, tùy hợp đồng của API.
3. **Đóng**: `Stream.close()` chạy do `try-with-resources`; `close` có thể có hành vi lỗi riêng theo nguồn.
4. **Kết quả sau khi đóng**: `List<String>` do `toList()` tạo ra là dữ liệu đã được vật hóa, không còn phụ thuộc vào việc tiếp tục đọc stream.
5. **Không trả stream sống ra ngoài phạm vi vừa đóng**: caller không thể kỳ vọng duyệt nguồn I/O đã hết vòng đời.

### Anti-pattern: trả một stream đã đóng

```java
// Cố ý là thiết kế SAI, dù có thể biên dịch.
static java.util.stream.Stream<String> broken(
        java.nio.file.Path path)
        throws java.io.IOException {

    try (var lines = java.nio.file.Files.lines(path)) {
        return lines;
    }
}
```

Khi phương thức `broken` trả về, `try-with-resources` đã gọi `close()` trên stream. Caller nhận một đối tượng stream **đã đóng**; duyệt hoặc liên kết thêm thao tác có thể thất bại, không thể dùng để đọc file như kỳ vọng.

Hai lựa chọn đúng:

- **Vật hóa kết quả bên trong vùng sở hữu tài nguyên**, như `readNonBlankLines` trả `List<String>`.
- **Chuyển giao ownership một cách tường minh** bằng API trả `Stream<String>` và yêu cầu caller đóng bằng `try-with-resources`, đồng thời tài liệu hóa rõ trách nhiệm và thời điểm lỗi.

Nếu API được thiết kế để trả stream sống, bản thân phương thức trả stream **không được tự đóng** trước khi caller sử dụng.

## 12. Mối liên hệ giữa checked exception và `try-with-resources` trong stream

Ngay cả khi xử lý phần tử không ném checked exception, quá trình mở/đóng nguồn có thể có lỗi.

Ví dụ:

```java
static long count(java.nio.file.Path path)
        throws java.io.IOException {

    try (var lines = java.nio.file.Files.lines(path)) {
        return lines.count();
    }
}
```

Phương thức khai báo `throws IOException` cho lỗi mở nguồn. Trong quá trình duyệt, các lỗi từ nguồn có thể xuất hiện dưới dạng `UncheckedIOException`. Nếu gặp cả lỗi xử lý chính và lỗi lúc đóng, cơ chế `try-with-resources` có thể ghi lỗi đóng dưới dạng suppressed exception của lỗi chính theo luật tương ứng.

Không nên tuyên bố rằng mọi lỗi I/O khi dùng `Files.lines` sẽ tự biến thành checked `IOException` có thể bắt ngoài pipeline. Hãy đọc hợp đồng từng API: checked ở biên mở nguồn, unchecked khi stream xử lý dữ liệu, và lỗi đóng là những chặng cần phân biệt.

### Stream bình thường có cần `try-with-resources` không?

`List.of("a","b").stream()` thường không nắm tài nguyên phải đóng, vì nguồn dữ liệu nằm trong bộ nhớ. Không cần bọc mọi stream bằng `try-with-resources` cho đúng “phong cách”.

Với `Files.lines`, `BufferedReader.lines` hoặc API tạo stream từ kết nối/nguồn bên ngoài, phải đọc rõ **ai sở hữu nguồn, có cần đóng hay không, và đóng stream có đóng luôn nguồn không**. Ví dụ `BufferedReader.lines()` không thay thế trách nhiệm đóng chính `BufferedReader`: có thể viết `try (BufferedReader reader = ...)` rồi dùng `reader.lines()` trong vùng đó.

## 13. Một chương trình nhỏ quan sát `Stream.close()` khi pipeline lỗi

Mã sau minh họa tác dụng của `try-with-resources` với close handler ngay cả khi terminal operation ném unchecked exception. Đây **không phải ví dụ I/O file thật**; chỉ dùng `onClose` để thấy thứ tự.

Lưu tệp **`StreamCloseDemo.java`**:

```java
import java.util.stream.Stream;

public class StreamCloseDemo {

    public static void main(String[] args) {
        try (Stream<String> values =
                     Stream.of("1", "bad", "3")
                             .onClose(
                                     () -> System.out.println("stream closed"))) {

            values.map(Integer::parseInt)
                    .forEach(number ->
                            System.out.println("value=" + number));

        } catch (NumberFormatException ex) {
            System.out.println(
                    "caught=" + ex.getClass().getSimpleName());
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 StreamCloseDemo.java
java StreamCloseDemo
```

**Stdout chính xác:**

```text
value=1
stream closed
caught=NumberFormatException
```

**Giải thích:** `forEach` gặp `"1"`, in `value=1`; tới `"bad"`, `Integer.parseInt` ném `NumberFormatException`. Trong quá trình rời `try-with-resources`, `values.close()` được gọi và handler `onClose` in `stream closed`. Sau đó `catch` bắt lỗi parse.

Không nên kết luận từ ví dụ rằng mọi stream tự đóng ngay sau khi terminal operation chạy. **`Stream` không tự động gọi `close()` chỉ vì đã gọi `toList()` hoặc `forEach()`**. Việc đóng xảy ra vì cấu trúc `try-with-resources`.

## 14. Lỗi khi đóng nguồn và suppressed exceptions

Nếu một pipeline bị lỗi và `close()` của nguồn cũng ném exception, cần phân biệt hai sự kiện:

- **Ngoại lệ chính**: xuất hiện khi xử lý phần tử hoặc đọc nguồn.
- **Ngoại lệ dọn dẹp**: xuất hiện khi đóng nguồn/stream.

Với `try-with-resources`, nếu phần thân đã có ngoại lệ chính và `close()` ném thêm lỗi, Java thông thường **giữ lỗi chính** và ghi lỗi đóng vào `getSuppressed()`. Nếu phần thân chuẩn bị `return` mà `close()` ném lỗi, lời gọi có thể **ném lỗi đóng thay vì trả kết quả**.

Ví dụ dạng khung:

```java
try (SomeAutoCloseable resource = openResource()) {
    return process(resource);
}
```

Đây là **mã minh họa**, cần các kiểu/hàm được định nghĩa phù hợp. Không giả định `process` thành công chỉ vì đã tính ra một giá trị trước khi đóng.

Khi điều tra thất bại của pipeline I/O, hãy xem **cause chain và suppressed ở mọi tầng** thay vì chỉ in `getMessage()` của wrapper ngoài.

## 15. Exception và thứ tự trên sequential/parallel stream

Với **sequential stream** trên một `List` có thứ tự gặp (*encounter order*), các ví dụ phía trên cho kết quả theo đường duyệt dễ quan sát.

Với **parallel stream**:

```java
items.parallelStream()
        .map(this::convert)
        .toList();
```

đây là mã minh họa, `items` và `convert` phải được định nghĩa trong ngữ cảnh phù hợp.

Các nguy cơ bổ sung:

- Nhiều phần tử có thể được xử lý **đồng thời trên các thread khác nhau**.
- Nếu nhiều lambda cùng thất bại, **không nên mặc định lỗi “đầu tiên trong danh sách” sẽ là exception được báo đầu tiên**.
- Một số công việc đã bắt đầu có thể vẫn tạo side effect trước khi pipeline hoàn tất bất thường.
- Thứ tự side effect/log từ `forEach` có thể không phản ánh encounter order.
- Những biến trạng thái dùng chung không đồng bộ gây race condition.
- **“Fail-fast” trong nghĩa dừng tạo kết quả khi gặp lỗi không phải bảo đảm dừng ngay mọi tác vụ đang chạy đồng thời.**

Nếu chính sách cần nhận diện chắc chắn **mọi lỗi của tất cả phần tử**, không nên chỉ dựa vào một exception thoát khỏi `parallelStream().map(...).toList()`. Một dạng kết quả trên từng phần tử, không có side effect ngoài cần thiết, có thể phù hợp hơn.

### `forEachOrdered` có giải quyết mọi thứ không?

`forEachOrdered` yêu cầu tôn trọng encounter order cho tác dụng của chính terminal operation trên ordered stream, nhưng không biến toàn bộ pipeline parallel thành xử lý tuần tự không side effect. Không nên dùng nó như cách giải quyết hoàn chỉnh cho rủi ro shared mutable state hoặc nhiều lỗi đồng thời.

Muốn audit đáng tin, hãy ưu tiên **dữ liệu kết quả có cấu trúc**, quy tắc tổng hợp rõ ràng và xử lý transaction ở tầng nghiệp vụ.

## 16. Side effects và giao dịch: không nhầm stream với transaction

Stream rất phù hợp để **biến đổi dữ liệu thuần** (*pure transformation*). Nhưng nhiều pipeline thực tế có thao tác ghi:

```java
orders.stream()
        .map(this::chargeCustomer)
        .toList();
```

Đây là **mã minh họa**, `chargeCustomer` giả định có side effect thanh toán. Nếu đơn thứ ba thất bại, hai đơn đầu **có thể đã bị charge**. Việc `toList()` ném ngoại lệ không tự hoàn tác tác động bên ngoài.

Đây là vấn đề về **transaction và idempotency**, không chỉ là cú pháp Stream.

Những nguyên tắc hữu ích:

1. **Validate trước khi tạo side effect** nếu có thể.
2. **Phân biệt hai giai đoạn**: parse/validate dữ liệu, sau đó thực hiện nghiệp vụ với chính sách transaction rõ ràng.
3. Nếu phải ghi từng phần tử, xác định **partial success có được phép không** và ghi nhận kết quả.
4. Nếu cần all-or-nothing, sử dụng transaction hoặc chiến lược bù trừ phù hợp với hệ thống đích.
5. Không giả định một checked exception đã được bọc thành unchecked có thể tự rollback hệ thống bên ngoài.
6. Tránh đưa I/O blocking hoặc thao tác dài vào `parallelStream` một cách tùy tiện khi chưa đánh giá concurrency/throughput.

Một API stream trả `List` không tự thay thế thiết kế tính nhất quán của domain.

## 17. Các kiểu fallback: loại nào phù hợp?

Một chiến lược:

```java
.map(raw -> {
    try {
        return riskyTransform(raw);
    } catch (SomeCheckedException ex) {
        return defaultValue;
    }
})
```

Đây là khung minh họa, chỉ hợp lệ nếu `riskyTransform`, `SomeCheckedException` và `defaultValue` được định nghĩa phù hợp. Nó có thể tốt nếu **fallback là một kết quả được cho phép**, nhưng có thể cực kỳ nguy hiểm nếu:

- `defaultValue` trùng một giá trị hợp lệ nên caller không phân biệt được lỗi.
- Lỗi thực tế là hạ tầng đang xuống cấp.
- Cần retry hoặc báo cảnh báo nhưng exception đã bị bỏ.
- Trách nhiệm xử lý lỗi thuộc tầng ngoài, không thuộc mapper.
- Việc tiếp tục tạo side effect trên các phần tử còn lại làm trạng thái khó khôi phục.

Do đó, hãy mô tả hợp đồng rõ:

| Chính sách | Khi hợp lý | Điều caller cần biết |
| --- | --- | --- |
| **Fail-fast** | Tác vụ chỉ hợp lệ khi mọi phần tử đều đúng; lỗi là ngoại lệ của toàn pipeline | Exception loại gì, có mã/ID/cause nào |
| **Collect failures** | Import hàng loạt, validation muốn thông báo nhiều lỗi | Kiểu `Outcome`, mã lỗi, danh sách kết quả |
| **Skip invalid** | Bài toán chấp nhận loại bỏ một số dữ liệu | Tiêu chí skip, số lượng bị loại, khả năng audit |
| **Fallback** | Có giá trị mặc định nghiệp vụ rõ ràng | Điều kiện dùng fallback và tính phân biệt |
| **Retry** | Lỗi tạm thời, thao tác idempotent/được kiểm soát | Số lần, delay, phân loại lỗi retryable |
| **Abort before side effect** | Muốn nhất quán tác vụ | Giai đoạn validate và commit tách rời |

Không nên để mapper tự quyết định một chính sách mơ hồ bằng `catch (Exception) { return null; }`.

## 18. Bảo toàn thông tin lỗi thay vì chỉ có message

Một nguyên tắc quen thuộc trong Java exceptions: **message dành cho người đọc; dữ liệu có cấu trúc dành cho chương trình**.

Với lỗi trên phần tử, một exception hoặc result có thể mang:

- `itemId` hoặc số dòng an toàn.
- Mã lỗi ổn định như `INVALID_INTEGER` hoặc `MISSING_FIELD`.
- Ngữ cảnh nghiệp vụ cần thiết, chẳng hạn tên trường.
- `cause` là đối tượng lỗi gốc khi có nguyên nhân kỹ thuật.
- Thông điệp chẩn đoán không chứa secret.
- Với lỗi cleanup, `getSuppressed()` trên Throwable thích hợp.

Đừng viết logic:

```java
if (ex.getMessage().contains("bad integer")) {
    // Phân nhánh nghiệp vụ.
}
```

Bởi message có thể thay đổi do dịch thuật, version hoặc làm rõ nội dung. Trong ví dụ fail-fast, caller dùng `InvalidItemException.getItemId()` và loại exception. Trong ví dụ collect, caller dùng `ErrorCode` của `Failure`.

### Đừng mất cause khi bọc ngoại lệ

Mã:

```java
throw new IllegalStateException(
        "conversion failed", original);
```

giữ nguyên `original` qua constructor nhận cause, nếu biến đó tồn tại và đúng kiểu.

Mã:

```java
throw new IllegalStateException(
        "conversion failed: " + original.getMessage());
```

không tự giữ cause; stack trace và suppressed thuộc ngoại lệ cũ sẽ không được nối vào lỗi mới chỉ vì message bị copy. Hãy bọc có nguyên nhân khi thật sự chuyển hợp đồng.

## 19. Không dùng unchecked exception để che lỗi thiết kế

Một adapter như:

```java
catch (Exception ex) {
    throw new RuntimeException(ex);
}
```

có thể giúp code vượt compiler, nhưng nếu không hiểu ngữ nghĩa thì nó chỉ làm ẩn một checked contract. Các câu hỏi cần trả lời:

- Caller của stream có biết **khi nào pipeline thất bại**?
- Loại wrapper có đủ ý nghĩa hay chỉ là `RuntimeException` chung chung?
- Cause còn nguyên không?
- Có cần xử lý lỗi tại từng phần tử thay vì ném?
- Mapper có chạy lặp hoặc chạy trên thread khác không?
- Có một ranh giới API nào phù hợp hơn để dùng vòng lặp `for` truyền checked exception trực tiếp?

Đôi khi giải pháp tốt nhất đơn giản là **không dùng stream**:

```java
static List<String> loadAll(List<String> ids)
        throws java.io.IOException {

    List<String> values = new java.util.ArrayList<>();

    for (String id : ids) {
        values.add(load(id));
    }

    return values;
}
```

Đây là **phương thức minh họa**, `load(String) throws IOException` được định nghĩa trong cùng phạm vi. Với vòng lặp, `throws IOException` của `loadAll` trực tiếp mô tả lỗi checked, không cần adapter unchecked. Nếu mục tiêu là giữ đúng loại checked exception tới caller, một vòng lặp rõ ràng đôi khi đơn giản hơn cố ép vào `Function`.

**Không có yêu cầu phải dùng Stream bằng mọi giá.** Chọn công cụ diễn đạt rõ nhất hợp đồng kết quả, lỗi và tác động phụ.

## 20. Kiểm thử ba tầng: compile time, hành vi pipeline, hợp đồng kết quả

Một bộ kiểm thử tốt không chỉ xác minh danh sách cuối cùng.

### A. Quy tắc compile-time

Kiểm thử biên dịch hoặc ví dụ nhỏ để xác nhận:

- Lambda ném checked exception trực tiếp **không** gán được vào `Function` không khai báo `throws`.
- Functional interface custom có `throws E` cho phép checked exception tương thích.
- Adapter chuyển sang `Function` có chính sách rõ và có thể được dùng với `Stream.map`.

### B. Fail-fast và cause

Kiểm tra:

- Dữ liệu hợp lệ tạo ra `List` đúng.
- Dữ liệu lỗi khiến terminal operation không trả danh sách thành công giả.
- Exception ngoài có loại/ID/mã lỗi đúng hợp đồng.
- `getCause()` giữ đối tượng nguyên nhân phù hợp.
- Khi xử lý sequential ordered stream theo logic fail-fast, phần tử sau điểm lỗi không bị xử lý tiếp như trường hợp minh họa.

Không nên kiểm thử bằng chuỗi đầy đủ của `printStackTrace()` vì tên file, số dòng và frames có thể thay đổi.

### C. Collect-all

Kiểm tra:

- Một `Failure` **không ngắt pipeline** nếu parser chuyển lỗi validation thành dữ liệu.
- Tổng số `Success + Failure` bằng tổng số đầu vào khi chính sách là một kết quả cho mỗi phần tử.
- Các mã lỗi và ID có cấu trúc được bảo toàn.
- Một lỗi hạ tầng chưa được xử lý vẫn thất bại đúng nghĩa, không lặng lẽ hóa thành `Failure` validation.
- Có tiêu chí rõ để quyết định commit hay rollback dựa trên danh sách kết quả.

### D. Resource và terminal operation

Với `Files.lines` hoặc nguồn có `close`:

- Resource được đóng trên đường thành công.
- Resource cũng được đóng khi terminal operation ném ngoại lệ.
- Caller không dùng stream sau khi chủ sở hữu đã đóng.
- Nếu close ném, lỗi phụ/primary được giữ và xử lý đúng.

## 21. Lỗi thường gặp khi kết hợp Stream và thao tác thất bại

| Lỗi hoặc hiểu lầm | Hậu quả | Cách đúng hơn |
| --- | --- | --- |
| Gọi hàm `throws IOException` trực tiếp trong `map` | Không biên dịch nếu checked exception thoát khỏi `Function.apply` | Catch/wrap, custom interface + adapter hoặc dùng vòng lặp |
| Thêm `throws IOException` ở method ngoài rồi tưởng lambda hợp lệ | Không thay đổi hợp đồng `Function` | Xử lý ngay ở descriptor boundary |
| Bắt mọi `Exception` rồi trả `null` | Mất lý do lỗi, dễ thiếu dữ liệu | Kết quả có cấu trúc hoặc fail-fast |
| `filter(Objects::nonNull)` sau parse lỗi trả null | Im lặng bỏ phần tử | Chỉ skip nếu hợp đồng cho phép và có audit |
| Chỉ log `ex.getMessage()` | Mất stack, cause và suppressed | Giữ Throwable và log đúng ranh giới |
| Nghĩ xây pipeline là đã xử lý xong | Lỗi xuất hiện lúc terminal operation | Đặt handler ở nơi thực thi |
| Dùng `findFirst` để kết luận toàn bộ nguồn hợp lệ | Short-circuit không duyệt tất cả | Chọn validation duyệt toàn bộ dữ liệu |
| Nghĩ `toList()` tự đóng nguồn file | Stream terminal không tự gọi close | Dùng try-with-resources cho nguồn cần đóng |
| Trả `Stream` từ `try-with-resources` đã đóng | Caller nhận stream không còn dùng được | Vật hóa trong scope hoặc chuyển giao ownership |
| Dùng lại một stream đã tiêu thụ | `IllegalStateException` hoặc hành vi không hợp lệ | Tạo stream mới từ nguồn phù hợp |
| Dùng `parallelStream` rồi mong lỗi đầu tiên theo thứ tự | Nondeterminism và tác vụ có thể đã chạy | Thiết kế lỗi và side effect rõ ràng |
| Dùng `forEach` để ghi database rồi tưởng lỗi sẽ rollback | Stream không phải transaction | Transaction/idempotency/bù trừ |
| Bắt `Throwable` và đổi thành `Failure` | Có thể che `Error` nghiêm trọng | Chỉ bắt lỗi đã được chính sách cho phép |
| Trộn lỗi hạ tầng và lỗi validation cùng một mã | Caller retry/hiển thị sai | Phân loại theo domain và source |
| Chỉ có `Optional.empty()` cho mọi lý do lỗi | Mất mã lỗi và ngữ cảnh | Dùng Outcome/Result khi cần |
| So chuỗi `getMessage()` để quyết định | Dễ hỏng khi đổi ngôn ngữ/câu chữ | Dùng type, code và field |

## 22. Bài tập tự kiểm tra

**Bài A — Vì sao không biên dịch?**

```java
// load(String) throws IOException.
List<String> loaded = ids.stream()
        .map(id -> load(id))
        .toList();
```

**Đáp án:** `map` nhận `Function.apply` không khai báo checked `IOException`. Cần bắt/chuyển lỗi trong lambda, adapter có hợp đồng, hoặc phương án xử lý khác.

**Bài B — Thêm `throws` ngoài có giải quyết không?**

Phương thức chứa `map` thêm `throws IOException`. Có đủ không?

**Không.** Lambda vẫn phải tuân thủ function descriptor của `Function`. Hợp đồng của phương thức bao ngoài không thay đổi descriptor này.

**Bài C — Pipeline lỗi ở đâu?**

`var p = Stream.of("1","x").map(Integer::parseInt);`. Lúc tạo `p` có nhất thiết ném `NumberFormatException` không?

**Không.** `map` là intermediate operation. Lỗi thường xuất hiện khi terminal operation yêu cầu phần tử `"x"` được parse.

**Bài D — `findFirst` có xác thực mọi phần tử không?**

`Stream.of("1","bad").map(Integer::parseInt).findFirst()` có thể trả `1` mà không gặp lỗi không?

**Có.** `findFirst` short-circuit trên stream tuần tự có thứ tự gặp, không yêu cầu duyệt mọi phần tử.

**Bài E — Bỏ phần tử sai**

Parser trả `null` khi lỗi và pipeline `filter(Objects::nonNull)`. Caller có biết chắc phần tử nào bị bỏ không?

**Không**, nếu không có cơ chế báo cáo riêng. Điều đó chỉ đúng khi hợp đồng công khai cho phép bỏ và có cách quan sát đủ.

**Bài F — Mọi lỗi đều phải throw?**

Nếu import nhiều dòng và giao diện cần hiển thị tất cả lỗi validation, có thể trả `Failure` cho từng dòng thay vì ném fail-fast không?

**Có.** `Success/Failure` có cấu trúc là lựa chọn phù hợp; cần quyết định việc commit dữ liệu ở tầng ngoài.

**Bài G — Resource của `Files.lines`**

`Files.lines(path)` có cần quan tâm `close()` không?

**Có.** Stream này có thể giữ file mở. Khi phạm vi hiện tại sở hữu nó, dùng `try-with-resources` hoặc chuyển giao trách nhiệm đóng tường minh.

**Bài H — Thực thi song song**

Một `parallelStream` có ba mapper đồng thời lỗi. Có bảo đảm nhận lỗi của phần tử đầu danh sách trước không?

**Không.** Không nên dựa vào thứ tự xuất hiện lỗi hoặc giả định mọi tác vụ khác đã dừng ngay.

**Bài I — `Stream` tự rollback?**

Mapper đã ghi hai bản ghi vào DB rồi bản ghi thứ ba lỗi. `toList()` ném exception có tự rollback hai bản ghi đầu không?

**Không.** Stream không cung cấp transaction. Cần chính sách transaction, idempotency hoặc bù trừ thích hợp.

**Bài J — Bọc checked sang unchecked**

Một adapter ném `new RuntimeException(cause.getMessage())`. Hậu quả?

**Không giữ `cause`** nếu chỉ truyền message. Nên dùng constructor nhận đối tượng `cause`, kèm ngữ cảnh có ý nghĩa.

**Bài K — Đếm mọi lỗi**

Trong chương trình `StreamResultsDemo`, vì sao có thể đếm `successes=2` và `failures=2` dù hai dòng dữ liệu sai?

Vì parser chuyển hai lỗi validation dự kiến thành **đối tượng `Failure`**, không ném exception làm ngắt pipeline, nên `toList()` hoàn tất với bốn kết quả.

**Bài L — Một stream dùng hai lần**

Sau `List<Outcome> r = s.toList()`, có nên gọi `s.count()` trên cùng biến `s` không?

**Không.** Stream đã được tiêu thụ. Có thể duyệt danh sách `r` bằng stream mới, hoặc tạo pipeline mới từ nguồn còn hợp lệ.

## 23. Checklist ra quyết định trước khi dùng Stream với thao tác có thể thất bại

Trước khi viết pipeline, hãy trả lời:

1. **Hàm mapper có checked `throws` không?** Nếu có, `Function` của `map` không cho loại đó tự thoát.
2. **Chính sách của cả tác vụ là gì?** Fail-fast, collect-all, skip hay fallback?
3. **Caller cần nhìn thấy lỗi dưới dạng gì?** Exception có kiểu/code/cause, hoặc `Outcome` cho từng phần tử?
4. **Thông tin phần tử lỗi có giữ được không?** ID, số dòng, tên trường hoặc mã lỗi, không parse message.
5. **Có đang bỏ mất cause không?** Wrapper phải giữ đối tượng lỗi gốc khi hợp lý.
6. **Pipeline chạy khi nào?** Lỗi trong intermediate operation có thể xuất hiện khi terminal được gọi, thậm chí ở tầng khác.
7. **Có short-circuit không?** `findFirst`, `anyMatch` không chứng minh mọi phần tử đã được kiểm tra.
8. **Nguồn có tài nguyên cần đóng không?** Với `Files.lines`, xác định quyền sở hữu và vị trí `try-with-resources`.
9. **Có trả stream lười ra ngoài API không?** Nếu có, ai tiêu thụ và ai đóng, và khi nào lỗi được báo?
10. **Có side effect không?** Ghi file, DB, network hoặc thanh toán cần chính sách tính nhất quán tách khỏi Stream.
11. **Có parallelism không?** Không giả định thứ tự lỗi, thứ tự side effect hoặc việc dừng mọi công việc ngay.
12. **Có thể phân loại lỗi kỳ vọng và lỗi hạ tầng không?** Tránh biến mọi exception thành `Failure` validation.
13. **Có kiểm thử đường lỗi và đóng nguồn không?** Kiểm tra cause, suppressed, output và khả năng quan sát thất bại.
14. **Một vòng lặp `for` có rõ ràng hơn không?** Nếu mục tiêu chính là truyền checked exception tới caller, đừng ép pipeline chỉ vì cú pháp ngắn.

## Kết luận

Kết hợp `Stream` với thao tác có thể thất bại đòi hỏi **hợp đồng xử lý lỗi tường minh**: `map`/`filter` dùng functional interfaces không khai báo checked exception, pipeline có tính lazy và terminal operation mới là nơi lỗi thường được quan sát. Khi một lỗi phải làm thất bại toàn tác vụ, có thể dùng **fail-fast** với exception đúng ngữ nghĩa và giữ `cause`. Khi cần xử lý mọi dòng rồi báo cáo, dùng **`Success/Failure` có cấu trúc**. Khi muốn bỏ qua hoặc fallback, phải chứng minh đó là hành vi được phép, không phải cách giấu lỗi.

Cuối cùng, **Stream không phải transaction và cũng không tự đóng mọi nguồn dữ liệu**. Hãy thiết kế ownership cho nguồn I/O, tránh side effect ngoài kiểm soát và không dựa vào thứ tự lỗi của pipeline song song. Hợp đồng rõ ràng giúp caller biết **thất bại xảy ra ở đâu, có tiếp tục được không, kết quả có đầy đủ không và phải xử lý tài nguyên ra sao**.

</details>

- [Quay lại đầu trang](#back-to-top)
