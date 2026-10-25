<a id="back-to-top"></a>

# Tra cứu

## Menu
- [Bảng phân biệt throw, throws, catch và finally](#exception-reference-001)
- [Bảng checked, unchecked, Exception và Error](#exception-reference-002)
- [Bảng kết quả của try, catch, finally và try-with-resources](#exception-reference-003)
- [Bảng phân biệt cause, suppressed và stack trace](#exception-reference-004)
- [Bảng chọn cách xử lý lỗi theo ranh giới API hoặc luồng](#exception-reference-005)
- [Tra cứu JLS, Throwable Javadoc và hợp đồng ngoại lệ của từng API](#exception-reference-006)

## <a id="exception-reference-001">Bảng phân biệt throw, throws, catch và finally</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng phân biệt `throw`, `throws`, `catch` và `finally` trong Java

> **Chủ đề:** Java Core — Exception
> 
> **Mục đích:** tài liệu tra cứu độc lập về cú pháp, ý nghĩa, vị trí, cách phối hợp và các lỗi thường gặp khi sử dụng bốn từ khóa `throw`, `throws`, `catch`, `finally`. Ví dụ chính dùng Java 17+ và có thể biên dịch/chạy trực tiếp.

## 1. Bảng so sánh nhanh

| Đặc điểm | `throw` | `throws` | `catch` | `finally` |
| --- | --- | --- | --- | --- |
| Bản chất | **Câu lệnh** ném một đối tượng `Throwable` | **Mệnh đề khai báo** trong chữ ký method/constructor | **Mệnh đề xử lý** exception sau `try` | **Mệnh đề dọn dẹp/kết thúc** sau `try` hoặc `catch` |
| Mục tiêu | Chủ động báo một lỗi **ngay tại điểm thực thi** | Công bố các **checked exception** có thể thoát ra khỏi method/constructor | Tiếp nhận và xử lý một exception có kiểu phù hợp | Chạy đoạn code khi rời cấu trúc `try` theo cơ chế thông thường |
| Cú pháp đại diện | `throw new IOException("failed");` | `void load() throws IOException` | `catch (IOException ex) { ... }` | `finally { closeResources(); }` |
| Vị trí | Trong thân method, constructor, initializer, lambda/block thích hợp | Sau danh sách tham số, trước thân method/constructor | Sau khối `try`, trước `finally` nếu có | Sau khối `try` và các `catch` nếu có |
| Dấu kết thúc | `;` sau biểu thức | Không có dấu `;` riêng trong header; kết thúc theo chữ ký/phần thân | Block `{ ... }` | Block `{ ... }` |
| Đối số | Một **biểu thức có kiểu `Throwable`** | Danh sách **kiểu exception**, phân cách bằng dấu phẩy | Một parameter có kiểu exception hoặc multi-catch | Không có parameter |
| Có tạo/ném exception? | **Có**: chuyển điều khiển bằng việc ném | **Không**: tự nó không ném hoặc tạo đối tượng | **Không**: nhận exception đã xảy ra; trong thân có thể `throw` tiếp | **Không mặc định**: thân `finally` vẫn có thể chủ động ném |
| Xử lý checked exception | Việc ném checked exception phải được bắt hoặc khai báo theo luật Java | Cho phép caller biết và xử lý/khai báo checked exception | Có thể thỏa yêu cầu bắt checked exception | Không thay thế `catch`/`throws` cho checked exception |
| Quan hệ với call stack | Bắt đầu lan truyền exception qua stack | Mô tả một phần contract compile time của method | Dừng việc lan truyền **nếu** nhận và xử lý; có thể rethrow | Được chạy trong lúc rời khối, kể cả khi đang unwind trong trường hợp thông thường |
| Có thể đứng riêng? | Có thể dùng trong một block hợp lệ | Chỉ dùng trong khai báo method/constructor | Không: phải đi cùng `try` | Không: phải đi cùng `try` |
| Rủi ro nổi bật | Ném sai loại lỗi, mất cause, đưa secret vào message | Khai báo quá rộng `throws Exception`, hiểu nhầm là sẽ luôn ném | Bắt quá rộng/nuốt lỗi, che bug và ngắt luồng | `return` hoặc `throw` trong `finally` che kết quả/lỗi ban đầu |

**Một câu ghi nhớ:** `throw` là **hành động ném**, `throws` là **lời khai báo**, `catch` là **nơi bắt**, `finally` là **nơi thực hiện phần kết thúc/dọn dẹp**.

Một phương thức hoàn toàn có thể dùng **cả bốn**. Ví dụ:

```java
static String load(boolean offline) throws IOException {
    try {
        if (offline) {
            throw new IOException("source unavailable");
        }
        return "DATA";
    } catch (IllegalArgumentException ex) {
        return "INVALID";
    } finally {
        System.out.println("finished");
    }
}
```

Đây là **mảnh code** cần import `java.io.IOException` và đặt trong một class. `throws IOException` khai báo checked exception có thể thoát; `throw` ném thật khi `offline`; `catch` ở đây chỉ nhận `IllegalArgumentException`, **không** nhận `IOException`; `finally` in thông báo khi khối rời đi. Không nên tưởng bất kỳ `catch` nào cũng bắt được mọi exception.

## 2. Nhìn vào thứ tự điều khiển trước khi học cú pháp

Một đường đi đơn giản:

```text
Caller gọi method
       |
       v
  Vào khối try
       |
       +-- Chạy bình thường ---------> bỏ qua catch
       |                                     |
       +-- Ném exception E                   |
               |                             |
               +--> catch đầu tiên hợp E?    |
                         |                   |
                         +-- có -> xử lý     |
                         |                   |
                         +-- không -> lan truyền
                                             |
                                             v
                                      finally (nếu có)
                                             |
                              kết quả / tiếp tục ném / rẽ nhánh
```

Đây là mô hình khái niệm. `finally` được thực thi trước khi câu lệnh `try` hoàn tất theo các đường rời thông thường. Tuy nhiên **không phải lời bảo đảm tuyệt đối cho mọi trường hợp của JVM**: process bị dừng cưỡng bức, `System.exit` kết thúc VM, JVM crash, luồng kẹt vĩnh viễn hoặc những tình huống không đạt tới điểm rời có thể khiến `finally` không chạy.

**Thứ tự quan trọng:** nếu `catch` xử lý xong và không ném tiếp, `finally` chạy trước khi code sau toàn bộ `try/catch/finally` tiếp tục. Nếu exception không được catch, `finally` vẫn được thử chạy trước khi exception lan ra caller.

## 3. `throw`: chủ động ném một exception ngay bây giờ

Cú pháp chuẩn:

```java
throw expression;
```

`expression` phải có kiểu phù hợp với `Throwable` theo luật ngôn ngữ Java, ví dụ:

```java
throw new IllegalArgumentException("quantity must be positive");
```

Đây là mảnh code trong method. Khi câu lệnh được thực thi, các lệnh bình thường **phía sau trong cùng đường đi** không tiếp tục, trừ khi exception được bắt tại một khối bao quanh phù hợp.

Một ví dụ validation:

```java
static int requirePositive(int quantity) {
    if (quantity <= 0) {
        throw new IllegalArgumentException("quantity must be positive");
    }
    return quantity;
}
```

Đây là method có thể đặt trong class. `IllegalArgumentException` là `RuntimeException`, vì vậy method không bắt buộc khai báo `throws IllegalArgumentException`. Dù không bắt buộc, API có thể tài liệu hóa `@throws` và điều kiện đầu vào.

### `throw` không phải `return`

- `return` kết thúc method bằng một giá trị (hoặc không có giá trị đối với `void`).
- `throw` kết thúc **đường điều khiển hiện tại** bằng một exception.
- Caller nhận giá trị chỉ khi đường điều khiển hoàn tất bình thường; nếu exception lan ra, caller không nhận giá trị thông thường của lời gọi đó.

### `throw` có thể ném lại exception đã bắt

```java
try {
    read();
} catch (IOException ex) {
    recordFailure();
    throw ex;
}
```

Đây là mảnh code trong method khai báo `throws IOException` (hoặc có handler bao ngoài), cần import `IOException` và định nghĩa `read`/`recordFailure`. `throw ex;` giữ chính **đối tượng Throwable** đã bắt, bao gồm stack/cause/suppressed sẵn có; không tạo wrapper mới.

### `throw null` thì sao?

Một biểu thức throw có thể đánh giá thành `null`; khi thực thi `throw null;`, JVM phát sinh `NullPointerException` thay vì ném một exception hữu ích. Không làm vậy để biểu thị thiếu dữ liệu. Dùng loại exception có nghĩa với thông điệp phù hợp.

## 4. `throws`: khai báo khả năng lan truyền, không tự ném lỗi

Cú pháp:

```java
ReturnType methodName(Parameters...) throws ExceptionType1, ExceptionType2 {
    // method body
}
```

Đây là mẫu chữ ký, không phải code độc lập. Một ví dụ thật:

```java
static String readFile(Path path) throws IOException {
    return Files.readString(path);
}
```

Đây là method cần import `java.nio.file.Path`, `java.nio.file.Files`, `java.io.IOException`. Lời khai báo cho biết caller phải xử lý hoặc truyền tiếp khả năng `IOException` (checked exception). Nhưng **một lần gọi thành công sẽ không ném exception chỉ vì header có `throws`**.

Ví dụ với nhiều loại checked exception:

```java
static void load(Path file)
        throws IOException, java.text.ParseException {
    String raw = Files.readString(file);
    parseRequiredData(raw);
}
```

Đây là mảnh code có `parseRequiredData` do ứng dụng định nghĩa và các import liên quan. Khi thực hiện, phải đảm bảo `parseRequiredData` thật sự có hợp đồng có thể ném `ParseException`; đây chỉ là mẫu cho danh sách `throws`. Không nên khai báo một tập lỗi không liên quan chỉ để “dự phòng”.

### Checked và unchecked

| Nhóm | Ví dụ | Luật compile time |
| --- | --- | --- |
| Checked exception | `IOException`, `SQLException`, `ParseException` | Khi có thể thoát khỏi method, phải **catch hoặc declare (`throws`)**, với các ngoại lệ quy tắc ngôn ngữ liên quan |
| Unchecked exception | `IllegalArgumentException`, `NullPointerException`, `IllegalStateException` | Không bắt buộc catch/declare |
| `Error` | `OutOfMemoryError`, `StackOverflowError` | Không bắt buộc catch/declare; thường không nên phục hồi chung |

`throws` có thể khai báo cả unchecked exception nhằm tài liệu hóa, nhưng không tạo một nghĩa vụ compile-time mới cho caller như checked exception.

### `throws Exception` có phải lựa chọn tốt?

`throws Exception` có thể được dùng trong bài demo, test entrypoint hoặc adapter của thư viện legacy, nhưng API công khai nên ưu tiên nhóm checked exception **có ý nghĩa với người gọi**. Nếu mọi method chỉ `throws Exception`, caller khó biết cần phục hồi trường hợp nào và có xu hướng `catch (Exception)` quá rộng.

Không nên tuyên bố rằng một method `throws IOException` **chỉ có thể** ném `IOException`. Nó vẫn có thể ném unchecked exceptions như `NullPointerException`; `throws` không phải hệ thống kiểu kết quả loại trừ mọi lỗi khác.

## 5. `throw` và `throws` đặt cạnh nhau

Ví dụ:

```java
static String fetch(boolean available) throws IOException {
    if (!available) {
        throw new IOException("dependency offline");
    }
    return "OK";
}
```

Đây là method cần import `IOException`.

| Câu hỏi | `throw new IOException(...)` | `throws IOException` |
| --- | --- | --- |
| Xảy ra ở thời điểm nào? | Khi chạy đến câu lệnh trong body | Khi compiler phân tích chữ ký |
| Có tạo đối tượng exception? | Có: biểu thức `new IOException` | Không |
| Có khiến caller nhận exception ngay? | Khi nhánh được thực thi và không bị xử lý | Không, chỉ công bố khả năng |
| Có thể liệt kê nhiều loại bằng dấu phẩy? | Không: một câu lệnh throw ném một Throwable | Có: `throws IOException, ParseException` |
| Có cần dấu chấm phẩy? | Có, sau câu `throw` | Không có `;` riêng trước method body |
| Có thể dùng trong constructor? | Có, trong body | Có, ở chữ ký constructor |
| Có thể dùng ở một method call site? | Có nếu nằm trong block hợp lệ | Không: `throws` chỉ nằm ở declaration |

Một người đọc có thể thấy `throws IOException` trên chữ ký nhưng **không thấy `throw new IOException` trong method**: IOException có thể được phát sinh từ lời gọi sâu hơn như `Files.readString()`. Điều này hợp lệ vì ngoại lệ checked lan truyền qua call stack khi chưa được catch.

## 6. `catch`: nhận exception theo loại tương thích

Cú pháp:

```java
try {
    mayFail();
} catch (IOException ex) {
    handleIoError(ex);
}
```

Đây là mảnh code với import và các hàm ứng dụng. Khi code trong `try` ném một Throwable, Java chọn khối `catch` đầu tiên có kiểu parameter tương thích với kiểu exception thực tế.

Một `catch` có thể:

- **Phục hồi** với fallback hợp lệ theo nghiệp vụ.
- **Dịch lỗi** sang exception domain, giữ `cause`.
- **Ghi nhận trạng thái FAILED/CANCELLED** ở nơi sở hữu workflow.
- **Cleanup chuyên biệt** nếu `finally`/`try-with-resources` không phù hợp cho phần đó.
- **Ném lại** bằng `throw ex;` khi không thể xử lý tại tầng này.

Đừng nghĩ `catch` mặc định có nghĩa “lỗi đã được giải quyết”. Nếu handler chỉ in message rồi trả kết quả giả, trạng thái hệ thống có thể vẫn sai. Nếu `catch` ném exception mới, caller sẽ nhận exception đó, và `finally` (nếu có) vẫn được chạy theo quy tắc.

### Catch order: từ cụ thể đến tổng quát

```java
try {
    load();
} catch (FileNotFoundException ex) {
    handleMissing(ex);
} catch (IOException ex) {
    handleOtherIo(ex);
}
```

Đây là mảnh code có `load`/handlers và imports phù hợp. `FileNotFoundException` là subclass của `IOException`; phải đặt nó trước nếu cần handler riêng.

Thứ tự ngược sau **không biên dịch**:

```java
try {
    load();
} catch (IOException ex) {
    handleIo(ex);
} catch (FileNotFoundException ex) {
    handleMissing(ex); // Unreachable catch: đã bị IOException bắt.
}
```

Đây là **ví dụ cố tình sai**. Đừng chạy nó như một chương trình hoàn chỉnh.

## 7. Multi-catch và quy tắc không gộp cha–con

Từ Java 7, một handler có thể bắt nhiều loại exception không nằm trong quan hệ subtype với nhau:

```java
try {
    networkOperation();
} catch (SocketTimeoutException | ConnectException ex) {
    markTemporarilyUnavailable(ex);
}
```

Đây là mảnh code cần import `java.net.SocketTimeoutException`, `java.net.ConnectException` và các hàm ứng dụng. Hai loại được xử lý **giống nhau** nên multi-catch hợp lý.

Một ví dụ **không hợp lệ**:

```java
catch (Exception | IOException ex) {
    // Không biên dịch: IOException là subtype của Exception.
}
```

Trong multi-catch, parameter được xem là **implicitly final**; không thể gán lại biến `ex`. Java chọn handler dựa trên loại Throwable được ném, không chạy nhiều nhánh catch cho cùng một exception.

Multi-catch phù hợp khi **chính sách xử lý trùng nhau**, không chỉ vì muốn viết ít dòng. Nếu timeout có retry còn authentication failure không retry, nên tách xử lý.

## 8. `finally`: chạy khi rời try theo đường điều khiển thông thường

Cú pháp phổ biến:

```java
try {
    work();
} finally {
    cleanup();
}
```

Đây là mảnh code cần định nghĩa `work`/`cleanup`. `finally` có thể đi cùng `try` dù **không có `catch`**. Nó hữu ích để nhả lock hoặc thiết lập lại trạng thái nội bộ khi việc rời block xảy ra qua các đường thông thường.

Một ví dụ:

```java
lock.lock();
try {
    updateState();
} finally {
    lock.unlock();
}
```

Đây là mảnh code cần biến `lock` thuộc `java.util.concurrent.locks.Lock` và hàm `updateState`. `finally` bảo đảm nỗ lực `unlock()` trong quá trình rời block, nhưng **không tự rollback** các cập nhật đã thực hiện trước đó.

### Những tình huống thông thường mà finally được chạy

- Thân `try` chạy xong bình thường.
- Một `catch` xử lý lỗi và chạy xong.
- Thân `try` hoặc `catch` thực hiện `return`.
- Thân có `break` hoặc `continue` khiến control rời phạm vi `try`.
- Một exception không được bắt lan ra khỏi `try`.
- Một exception mới được ném từ `catch` trước khi thoát ra.

### Những giới hạn cần nhớ

Không được khẳng định “finally **luôn luôn** chạy dù bất cứ điều gì xảy ra”. Các trường hợp VM/process kết thúc đột ngột, bị kill, crash hoặc không bao giờ rời được khối (kẹt blocking vô hạn) có thể khiến `finally` không chạy. Ngoài ra, nếu **chính `finally` ném exception hoặc return**, nó có thể thay thế kết quả/lỗi đang được truyền đi.

## 9. `return` trong `try` và thứ tự chạy `finally`

Xem:

```java
static int getValue() {
    try {
        return 42;
    } finally {
        System.out.println("cleanup");
    }
}
```

Đây là method có thể đặt trong một class. Khi gọi `getValue()`, runtime chuẩn bị hoàn tất return nhưng thực thi `finally` trước. Vì vậy output `cleanup` xuất hiện trước khi caller nhận `42`.

Ví dụ cần phân biệt:

```java
static int snapshot() {
    int result = 1;
    try {
        return result;
    } finally {
        result = 2;
    }
}
```

Method trên trả **1**, vì giá trị return của primitive đã được xác định trước khi `finally` thay đổi biến local. Đây không phải lời hứa rằng mọi đối tượng được return là bất biến: nếu `finally` **mutate object** đã được tham chiếu, caller có thể quan sát object đã bị sửa.

### Không return từ finally

```java
static int misleading() {
    try {
        throw new IllegalStateException("failure");
    } finally {
        return 10;
    }
}
```

Đây là **mảnh anti-pattern**: nó có thể biên dịch nhưng return trong `finally` **che exception gốc**, khiến caller thấy số 10 dù công việc thất bại.

Tương tự, `finally` ném một exception mới có thể che exception đang được lan truyền. Chỉ để việc cleanup được thực hiện; khi cleanup cũng thất bại, cần bảo toàn lỗi gốc theo contract thay vì tự động thay thế.

## 10. `finally` khác `catch` ở điểm nào?

| Câu hỏi | `catch` | `finally` |
| --- | --- | --- |
| Có chọn loại exception không? | Có, dựa trên parameter type | Không |
| Có nhận Throwable như biến `ex`? | Có | Không |
| Chạy khi `try` hoàn tất bình thường? | Không | Có, nếu có `finally` và control rời bình thường |
| Có thể xử lý/nuốt lỗi gốc không? | Có | Không tự động, nhưng `return`/`throw` trong thân có thể che lỗi |
| Thường dùng cho | Recovery, translation, error mapping, reporting | Cleanup, release lock, cập nhật cờ/lifecycle |
| Có cần `catch` để tồn tại? | Cần `try`, không cần finally | Cần `try`, không cần catch |
| Nếu `catch` ném lại lỗi? | Handler ném lại | `finally` vẫn được thử chạy trước khi exception thoát |
| Có thay thế try-with-resources được không? | Không | Có thể dùng thủ công, nhưng dễ sai khi `close()` cũng ném |

`catch` và `finally` **không thay thế lẫn nhau**. Không bắt exception chỉ vì cần release resource, và không thêm `finally` chỉ để nuốt lỗi bằng `return`.

## 11. Bốn từ khóa phối hợp trong một luồng thực tế

Một mẫu phổ biến ở tầng xử lý I/O:

```java
static String readOrFallback(String path) {
    try {
        return readFile(path); // readFile có thể throws IOException.
    } catch (IOException ex) {
        return "DEFAULT";
    } finally {
        recordAttemptFinished();
    }
}
```

Đây là mảnh code cần các hàm `readFile`/`recordAttemptFinished` và import `IOException`. Nếu `readFile` chạy bình thường, `catch` bị bỏ qua, `finally` chạy rồi caller nhận nội dung. Nếu IOException xảy ra, handler trả fallback, `finally` chạy trước khi caller nhận `DEFAULT`.

**Điều kiện quan trọng:** fallback chỉ hợp lệ khi yêu cầu nghiệp vụ cho phép. Ví dụ cấu hình phụ có default là bình thường; lỗi đọc số dư tài khoản mà trả `0` là sai nghiêm trọng.

Với lỗi đầu vào như `IllegalArgumentException` không được handler `IOException` bắt, `finally` vẫn chạy, sau đó lỗi input lan tới caller. Điều này được xác nhận trong chương trình chạy độc lập ở phần 12.

## 12. Chương trình Java 17+ hoàn chỉnh và output được kiểm chứng

Ví dụ tích hợp sau chứng minh:

1. `throw` tạo `IllegalArgumentException` khi key sai.
2. `throws IOException` trên `read(...)` cho phép một checked exception thoát ra khi dependency giả lập offline.
3. `catch (IOException)` chuyển lỗi I/O thành fallback riêng, không nuốt lỗi validation.
4. `finally` tăng bộ đếm **cả khi thành công, khi bắt IOException và khi một unchecked exception lan ra**.
5. `try-with-resources` đóng tài nguyên theo thứ tự ngược và lưu lỗi đóng ở `getSuppressed()` khi thân cũng đã lỗi.
6. Nếu thân thành công nhưng resource close ném, lỗi close đầu tiên trở thành primary.

Tạo file **`ThrowThrowsCatchFinallyDemo.java`**:

```java
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ThrowThrowsCatchFinallyDemo {
    private static int finallyCount;
    private static final List<String> EVENTS = new ArrayList<>();

    static String read(String key) throws IOException {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        if ("offline".equals(key)) {
            throw new IOException("source unavailable");
        }
        return "VALUE:" + key;
    }

    static String request(String key) {
        try {
            return read(key);
        } catch (IOException ex) {
            return "FALLBACK:" + ex.getClass().getSimpleName();
        } finally {
            finallyCount++;
        }
    }

    static final class Resource implements AutoCloseable {
        private final String name;
        Resource(String name) {
            this.name = name;
            EVENTS.add("open-" + name);
        }
        @Override
        public void close() throws IOException {
            EVENTS.add("close-" + name);
            throw new IOException("close-" + name + "-failed");
        }
    }

    static void bodyAndCloseFailure() throws IOException {
        try (Resource a = new Resource("A");
             Resource b = new Resource("B")) {
            EVENTS.add("body-failed");
            throw new IOException("body-failed");
        }
    }

    static void onlyCloseFailure() throws IOException {
        try (Resource a = new Resource("A");
             Resource b = new Resource("B")) {
            EVENTS.add("body-ok");
        }
    }

    static String suppressedMessages(Throwable ex) {
        List<String> messages = new ArrayList<>();
        for (Throwable suppressed : ex.getSuppressed()) {
            messages.add(suppressed.getMessage());
        }
        return String.join(",", messages);
    }

    static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        String success = request("A");
        String fallback = request("offline");
        check("VALUE:A".equals(success), "success");
        check("FALLBACK:IOException".equals(fallback), "fallback");
        System.out.println("RETURN: " + success);
        System.out.println("CATCH: " + fallback);

        try {
            request("");
            throw new AssertionError("expected validation exception");
        } catch (IllegalArgumentException ex) {
            check("key must not be blank".equals(ex.getMessage()), "validation");
            System.out.println("THROW_UNCHECKED: " + ex.getMessage());
        }
        check(finallyCount == 3, "finally must run three times");
        System.out.println("FINALLY_COUNT: " + finallyCount);

        EVENTS.clear();
        try {
            bodyAndCloseFailure();
            throw new AssertionError("expected body failure");
        } catch (IOException ex) {
            check("body-failed".equals(ex.getMessage()), "body primary");
            check("close-B-failed,close-A-failed".equals(suppressedMessages(ex)),
                    "body suppressed");
            System.out.println("BODY_PRIMARY: " + ex.getMessage());
            System.out.println("BODY_SUPPRESSED: " + suppressedMessages(ex));
            System.out.println("BODY_EVENTS: " + String.join(",", EVENTS));
        }

        EVENTS.clear();
        try {
            onlyCloseFailure();
            throw new AssertionError("expected close failure");
        } catch (IOException ex) {
            check("close-B-failed".equals(ex.getMessage()), "close primary");
            check("close-A-failed".equals(suppressedMessages(ex)),
                    "close suppressed");
            System.out.println("CLOSE_PRIMARY: " + ex.getMessage());
            System.out.println("CLOSE_SUPPRESSED: " + suppressedMessages(ex));
            System.out.println("CLOSE_EVENTS: " + String.join(",", EVENTS));
        }
        System.out.println("CHECKS: PASS");
    }
}
```

**Biên dịch/chạy với JDK 17 trở lên** (không cần thư viện ngoài):

```bash
javac -encoding UTF-8 ThrowThrowsCatchFinallyDemo.java
java ThrowThrowsCatchFinallyDemo
```

**Standard output đã kiểm chứng:**

```text
RETURN: VALUE:A
CATCH: FALLBACK:IOException
THROW_UNCHECKED: key must not be blank
FINALLY_COUNT: 3
BODY_PRIMARY: body-failed
BODY_SUPPRESSED: close-B-failed,close-A-failed
BODY_EVENTS: open-A,open-B,body-failed,close-B,close-A
CLOSE_PRIMARY: close-B-failed
CLOSE_SUPPRESSED: close-A-failed
CLOSE_EVENTS: open-A,open-B,body-ok,close-B,close-A
CHECKS: PASS
```

Output này đến từ chương trình đã biên dịch/chạy bằng `javac`/`java` trên JDK hiện đại và chỉ sử dụng cú pháp/API hợp lệ của Java 17. Các hàm `check` ném `AssertionError` nếu một điều kiện trái kỳ vọng; vì vậy dòng `CHECKS: PASS` chỉ xuất hiện sau khi các nhánh kiểm tra trong demo hoàn tất.

### Phân tích kết quả

- `RETURN: VALUE:A`: không có exception; `read("A")` trả dữ liệu bình thường, `catch` không chạy.
- `CATCH: FALLBACK:IOException`: `read("offline")` **thực sự `throw new IOException`**; `request` bắt đúng checked exception và trả fallback theo contract của bài demo.
- `THROW_UNCHECKED`: key rỗng làm `IllegalArgumentException` lan qua `request` vì ở đó chỉ có `catch(IOException)`; caller ngoài nhận và kiểm tra.
- `FINALLY_COUNT: 3`: ba lần gọi `request` đều đi qua `finally` mặc dù đường trả kết quả/lỗi khác nhau.
- `BODY_PRIMARY` và `BODY_SUPPRESSED`: exception của thân được giữ là primary; lỗi đóng B, A nằm trong suppressed theo thứ tự ngược khi đóng.
- `CLOSE_PRIMARY` và `CLOSE_SUPPRESSED`: thân thành công, B đóng trước và thất bại nên nó trở thành primary; lỗi đóng A là suppressed.
- `BODY_EVENTS` / `CLOSE_EVENTS`: mở A rồi B, đóng B rồi A (LIFO). Những phép đóng vẫn được cố thực hiện sau lỗi đóng đầu tiên.

Ví dụ này dùng `Resource.close()` luôn ném để minh họa. Không nên làm vậy trong một resource production: lỗi đóng chỉ nên được ném khi cleanup thật sự không hoàn tất theo contract của tài nguyên.

## 13. Tại sao try-with-resources thường tốt hơn tự viết finally để close?

Mẫu thủ công thường thấy:

```java
InputStream input = Files.newInputStream(path);
try {
    process(input);
} finally {
    input.close();
}
```

Đây là mảnh code trong method phù hợp với `IOException`, đã có `Path path` và `process` cùng imports. Nếu `process(input)` ném một exception, rồi `input.close()` cũng ném, exception close có thể **che lỗi body**, khiến nguyên nhân ban đầu khó nhận ra.

Cú pháp được khuyến nghị khi tài nguyên triển khai `AutoCloseable`:

```java
try (InputStream input = Files.newInputStream(path)) {
    process(input);
}
```

Mảnh code tương ứng cần imports và contract method giống ví dụ trên. Với `try-with-resources`:

- Resource được khởi tạo từ trái sang phải.
- Khi rời scope, resource đã khởi tạo được đóng từ phải sang trái.
- Nếu body đã ném Throwable, lỗi đóng thứ cấp thường nằm trong `Throwable.getSuppressed()` của primary.
- Nếu body thành công nhưng close đầu tiên ném, lỗi close đó có thể là primary.
- Nếu initializer của resource sau thất bại, những resource trước đã khởi tạo vẫn được thử đóng.
- Resource reference null (hợp lệ về ngữ nghĩa trong một số trường hợp) không bị gọi `close()`.

`try-with-resources` không thay thế transaction/rollback nghiệp vụ. Đóng connection không đồng nghĩa mọi ghi trước đó đã được hoàn tác; semantics của transaction và driver phải được kiểm tra riêng.

### Lưu ý về `throws` của `AutoCloseable.close()`

`AutoCloseable.close()` được khai báo `throws Exception`, còn `Closeable.close()` (trong `java.io`) khai báo hẹp hơn với `IOException`. Khi viết method dùng một resource có checked exception từ `close()`, compiler cần thấy lỗi đó được catch hoặc declare.

Không nên tuyên bố chỉ vì body không ném mà method không thể ném: `close()` vẫn có thể ném.

## 14. `throw new`, `throw ex` và bọc exception có cause

Ba hình thức dễ nhầm:

```java
throw new IllegalArgumentException("bad parameter");
```

Đây là mảnh code: **tạo và ném instance mới**.

```java
catch (IOException ex) {
    throw ex;
}
```

Mảnh code nằm trong cấu trúc `try/catch` và chữ ký phù hợp: **ném lại đúng instance đã bắt**.

```java
catch (IOException ex) {
    throw new RepositoryUnavailableException("repository unavailable", ex);
}
```

Đây là mảnh code cần định nghĩa `RepositoryUnavailableException` có constructor nhận `Throwable cause`. Nó **dịch lỗi** sang loại có ý nghĩa ở boundary, đồng thời giữ lỗi nguồn thông qua `getCause()`.

Đừng làm:

```java
catch (IOException ex) {
    throw new RuntimeException("operation failed");
}
```

mà không có lý do, vì nó làm mất cause và đường đi của lỗi gốc. Đồng thời, dù đã giữ cause, **không tự in toàn bộ message/cause ra API response**: exception của dependency có thể chứa URL, token, credential hoặc dữ liệu cá nhân.

Với exception translation, chọn loại lỗi có ý nghĩa nghiệp vụ chứ không bọc mọi thứ thành `RuntimeException` chung.

## 15. Một `try` có bắt buộc phải có cả catch và finally không?

**Không.** Những cấu trúc sau đều hợp lệ:

**Chỉ try/catch:**

```java
try {
    work();
} catch (KnownFailure ex) {
    recover(ex);
}
```

**Chỉ try/finally:**

```java
try {
    work();
} finally {
    cleanup();
}
```

**Try/catch/finally:**

```java
try {
    work();
} catch (KnownFailure ex) {
    recover(ex);
} finally {
    cleanup();
}
```

**Try-with-resources:**

```java
try (Resource resource = openResource()) {
    workWith(resource);
}
```

Tất cả đều là mảnh code khái niệm, cần định nghĩa loại `KnownFailure`, `Resource` và các phương thức tương ứng. Với kiểu `try` truyền thống, phải có ít nhất **một `catch` hoặc một `finally`**. Với `try-with-resources` có resource specification, không bắt buộc có catch/finally tường minh.

Vị trí `finally` nếu có luôn **sau** các `catch` của cùng câu lệnh. Không đặt `catch` sau `finally`. Một khối `try` có thể có nhiều `catch`, nhưng chỉ một `finally` gắn trực tiếp với nó; muốn cleanup nhiều cấp thì dùng nhiều scope hoặc helper.

## 16. Checked exception từ method gọi sâu: không cần ném lại thủ công

```java
static void copy(Path source, Path destination) throws IOException {
    Files.copy(source, destination);
}
```

Đây là method cần imports từ `java.nio.file` và `java.io`. `Files.copy` có thể ném checked exception, nên `copy` có thể đơn giản khai báo `throws IOException` để caller quyết định xử lý. Không cần:

```java
static void copy(Path source, Path destination) throws IOException {
    try {
        Files.copy(source, destination);
    } catch (IOException ex) {
        throw ex;
    }
}
```

vì khối catch này **không làm thêm gì** so với để lỗi lan truyền. Catch chỉ hữu ích khi có xử lý như rollback thuộc quyền sở hữu, thêm context an toàn, chuyển loại exception hoặc ghi trạng thái có ý nghĩa.

Điểm quan trọng: `throws` **không chủ động chuyển checked thành unchecked**, cũng không bảo đảm một lỗi sẽ được bắt. Nó chỉ tham gia hợp đồng compile time và cho exception tiếp tục lan truyền nếu xảy ra.

## 17. Rethrow và precise rethrow

Java cho phép ném lại parameter của catch:

```java
try {
    mayThrowFileOrSql();
} catch (Exception ex) {
    record(ex);
    throw ex;
}
```

Đây là mảnh code với method `mayThrowFileOrSql` có hợp đồng checked exception và `record` tự định nghĩa. Với Java 7+, compiler có thể dùng **phân tích precise rethrow** để suy ra tập kiểu exception checked mà statement có thể ném nếu biến catch parameter effectively final (không bị gán lại), thay vì luôn bắt buộc `throws Exception`.

Ví dụ nếu `mayThrowFileOrSql()` chỉ có thể ném `IOException` và `SQLException`, method bao ngoài có thể khai báo hai loại đó khi điều kiện precise rethrow thỏa mãn. Ngược lại, nếu gán lại `ex`, phân tích có thể không còn đủ chính xác theo quy tắc ngôn ngữ.

Đây là đặc điểm **compile-time**. Ở runtime `throw ex` vẫn ném đúng object. Tuy nhiên một `catch(Exception)` chỉ để rethrow không có hành động thêm thường vẫn không cần thiết; đừng thêm khối catch chỉ để thử compiler feature.

## 18. `catch(Exception)` và `catch(Throwable)` khác nhau

| Handler | Bao gồm | Nguy cơ nếu dùng đại trà |
| --- | --- | --- |
| `catch (Exception ex)` | Checked exceptions và `RuntimeException` | Nuốt bug, che `InterruptedException`, biến lỗi thành kết quả giả |
| `catch (Throwable t)` | Toàn bộ `Exception` **và `Error` | Có thể che `OutOfMemoryError`/`StackOverflowError` và để ứng dụng chạy trong trạng thái không đáng tin |
| `catch (IOException ex)` | Nhóm I/O | Thường hẹp hơn, nhưng vẫn cần chính sách hợp lệ |
| `catch (InterruptedException ex)` | Tín hiệu ngắt blocking operation | Cần truyền tiếp hoặc restore flag/rời task theo contract |

Không phải `catch(Exception)` luôn sai: ở HTTP boundary hoặc job runner cuối, một fallback chung cho lỗi chưa dự kiến có thể hợp lý sau các handler cụ thể. Điều không nên làm là **sao chép catch rộng tại mọi tầng và nuốt lỗi**.

`catch(Throwable)` có thể tồn tại trong framework hoặc test harness để quan sát thất bại của một đơn vị thực thi, nhưng không nên xem đó là cách “phục hồi mọi Error”. Một VM thiếu tài nguyên có thể không đủ khả năng thực thi logic báo lỗi phức tạp.

## 19. Đừng nuốt InterruptedException trong catch rộng

Ví dụ tác vụ chạy trong thread pool:

```java
while (true) {
    try {
        process(queue.take());
    } catch (Exception ex) {
        logger.warn("retry", ex);
    }
}
```

Đây là **mảnh anti-pattern** có `queue`, `process` và `logger` do ứng dụng định nghĩa. `BlockingQueue.take()` có thể ném `InterruptedException`, mà nó là subclass của `Exception`. Catch chung có thể nuốt tín hiệu hủy, làm worker tiếp tục chạy.

Nếu method có thể khai báo:

```java
void receive(BlockingQueue<Job> queue) throws InterruptedException {
    process(queue.take());
}
```

Đây là mảnh code cần import `BlockingQueue`, định nghĩa `Job`/`process`. Truyền tiếp checked interruption thường rõ.

Trong `Runnable.run()` không được ném checked `InterruptedException`:

```java
@Override
public void run() {
    try {
        process(queue.take());
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return;
    } finally {
        releaseResources();
    }
}
```

Đây là mảnh trong class ứng dụng có queue/cleanup. Khi đã bắt, restore flag và thoát đường thực thi thích hợp; không restore rồi quay lại vòng `while(true)` bất chấp yêu cầu ngắt, vì có thể tạo vòng lặp nóng.

Một tầng sở hữu toàn bộ giao thức interruption có thể xử lý và tiêu thụ tín hiệu có chủ đích, nhưng đó phải là quyết định rõ ràng, không phải side effect của catch quá rộng.

## 20. `finally` không đồng nghĩa rollback và không tự đảm bảo close thành công

Một anti-pattern:

```java
try {
    chargeCustomer(); // Có thể đã commit.
    sendReceipt();    // Có thể lỗi.
    return true;
} finally {
    logFinished();
}
```

Đây là mảnh code có các hàm nghiệp vụ đã định nghĩa. `finally` chỉ gọi `logFinished()` khi rời. Nó **không tự hoàn tác** việc trừ tiền. Nếu `chargeCustomer()` đã thành công và `sendReceipt()` lỗi, caller có thể nhận exception dù tiền đã bị trừ.

Tương tự:

- `lock.unlock()` trong finally nhả lock, không undo state đã mutate.
- `connection.close()` không được mặc định tương đương transaction rollback trên mọi driver/pool.
- `future.cancel(true)` là yêu cầu hủy, không cam kết mọi side effect trước đó đã biến mất.
- `Files.delete(...)` trong finally có thể tự thất bại hoặc xóa sai nếu path không được quản lý cẩn thận.

Nếu cần rollback, xác định transaction owner, điều kiện commit/rollback, trạng thái chưa xác định và cách bảo toàn lỗi rollback. Với nhiều hệ thống, dùng idempotency, transactional outbox hoặc compensation khi phù hợp.

## 21. Bảng quyết định: dùng từ khóa nào?

| Bạn đang cần… | Công cụ thường phù hợp | Ví dụ cách tư duy |
| --- | --- | --- |
| Từ chối tham số sai ngay lập tức | `throw` | `throw new IllegalArgumentException(...)` |
| Báo caller rằng method có thể thất bại vì I/O | `throws` | `readFile(...) throws IOException` |
| Xử lý lỗi I/O bằng fallback hợp lệ | `catch (IOException ex)` | Chỉ dùng fallback có nghĩa theo contract |
| Dịch lỗi adapter sang domain error | `catch` + `throw new ...(..., ex)` | Giữ `cause` |
| Chỉ chuyển tiếp lỗi I/O từ method trung gian | `throws IOException` | Không cần catch rồi rethrow vô nghĩa |
| Giải phóng lock dù thân thất bại | `finally` | `unlock()` trong finally |
| Đóng resource `AutoCloseable`, giữ lỗi close | `try-with-resources` | Tự close, hỗ trợ suppressed |
| Bắt nhiều lỗi với chung cách xử lý | Multi-catch | `catch (A | B ex)` cho hai loại độc lập |
| Báo lỗi bất ngờ ở API boundary | Fallback catch chuyên trách | Trả public error code, không leak stack |
| Tác vụ bị interrupt trong khi chờ | `catch(InterruptedException)` hoặc `throws` | Truyền tiếp hoặc restore và dừng |
| Phục hồi sau partial commit | Không chỉ dùng bốn keyword | Transaction/idempotency/compensation |
| Giữ tất cả lỗi từ try và close | `try-with-resources` + `getSuppressed()` | Body primary, close suppressed khi phù hợp |

Bảng là định hướng, không thay cho hợp đồng của API đang dùng. Lỗi validation, lỗi domain, lỗi phụ thuộc và cancellation có chính sách rất khác nhau, dù tất cả đều có thể biểu diễn bằng Throwable.

## 22. Lỗi cú pháp và lỗi suy luận thường gặp

| Sai lầm | Vấn đề | Sửa theo hướng nào |
| --- | --- | --- |
| Viết `throws new IOException(...)` | `throws` nhận **kiểu**, không nhận instance | Dùng `throw new IOException(...)` trong body |
| Viết `throw IOException;` | `throw` cần **biểu thức Throwable**, không phải tên class | Tạo/nhận instance |
| Viết `throw "error";` | `String` không là `Throwable` | Ném kiểu exception phù hợp |
| Nghĩ `throws` làm exception xuất hiện mỗi lần gọi | Declaration không tự ném | Tìm đường `throw` hoặc lời gọi ném lỗi thật |
| Nghĩ `catch(IOException)` bắt cả `NullPointerException` | Không cùng nhánh | Catch đúng loại cần xử lý, để bug lan ra |
| Đặt `catch(IOException)` sau `catch(Exception)` | Handler subclass không thể tới | Bắt cụ thể trước |
| Dùng `catch(Exception | IOException)` | Multi-catch có quan hệ cha–con | Chỉ gộp các loại độc lập |
| Bỏ `catch`/`finally` khỏi `try` truyền thống | Cú pháp `try { }` đơn lẻ không hợp lệ | Thêm catch/finally hoặc dùng TWR |
| Nghĩ finally chạy ngay **trước** catch | Sai thứ tự | Với exception phù hợp: catch rồi finally |
| Return trong finally để “chắc chắn trả kết quả” | Có thể che exception/return từ try | Không return trong finally |
| Exception mới trong finally che lỗi chính | Mất chẩn đoán primary | Dùng TWR hoặc bảo toàn lỗi gốc |
| Catch IOException rồi trả success giả | Trạng thái nghiệp vụ sai | Fallback chỉ khi hợp đồng cho phép |
| Bọc exception mới mà bỏ cause | Mất lỗi gốc | Truyền `ex` vào constructor nhận cause |
| Catch Exception rồi nuốt interrupt | Task không dừng | Xử lý `InterruptedException` riêng |
| Tin finally luôn chạy kể cả process crash | Không có bảo đảm tuyệt đối | Cleanup theo lifecycle + cơ chế khôi phục ngoài process |
| Tin close đồng nghĩa rollback | Side effect có thể đã commit | Kiểm tra transaction semantics |
| Ghi Throwable message trực tiếp ra client | Có thể lộ secrets/PII | Error code an toàn + log theo policy |

## 23. Bài tập tự luyện và đáp án định hướng

### Câu 1 — Xác định kết quả

```java
static int example() {
    try {
        return 5;
    } finally {
        System.out.println("F");
    }
}
```

Khi gọi `System.out.println(example());`, chương trình in `F` rồi `5`. **Vì sao?** Giá trị return đã chuẩn bị nhưng `finally` được chạy trước khi method hoàn tất.

### Câu 2 — Phân biệt hai lỗi

```java
static void run() throws IOException {
    throw new IOException("io");
}
```

**`throws IOException` có phải nguyên nhân runtime ném lỗi?** Không: nguyên nhân runtime là câu `throw new IOException("io")` được thực thi. `throws` cho compiler/caller biết một checked exception có thể thoát khỏi method.

### Câu 3 — Đổi thứ tự catch

```java
try {
    read();
} catch (Exception ex) {
    handleGeneric(ex);
} catch (IOException ex) {
    handleIo(ex);
}
```

**Có biên dịch không?** Không, vì handler `Exception` trước đã bắt được `IOException`; handler IOException sau unreachable. Sửa bằng cách đặt handler IOException trước hoặc bỏ handler riêng nếu cùng chính sách.

### Câu 4 — Cuối cùng có che lỗi?

```java
static int example() {
    try {
        throw new RuntimeException("failure");
    } finally {
        return 99;
    }
}
```

**Caller nhận gì?** Method trả 99 và exception gốc bị che. Đây là lý do **tránh return trong finally**.

### Câu 5 — Khi thân và close đều ném

Một `try-with-resources` mở A rồi B; thân ném `IOException("body")`; B.close và A.close cùng ném. **Primary là gì?** Lỗi body. **Đóng theo thứ tự nào?** B rồi A. **Lỗi close nằm ở đâu?** Trong `primary.getSuppressed()` theo thứ tự phát sinh.

Các mảnh bài tập được đặt trong ngữ cảnh method/class phù hợp và thêm imports khi cần. Chỉ chương trình ở phần 12 được thiết kế thành một file Java độc lập hoàn chỉnh.

## 24. Checklist tra cứu cuối

- [ ] Phân biệt **`throw` (hành động)** và **`throws` (khai báo)**.
- [ ] Khi dùng checked exception, method có handler phù hợp hoặc `throws` đúng contract.
- [ ] Catch dựa trên loại Throwable thực tế; subclass trước superclass.
- [ ] Không bắt Exception/Throwable quá rộng chỉ để làm biến mất lỗi.
- [ ] Nếu dịch exception, chọn mã/loại đúng và giữ cause khi cần.
- [ ] `finally` chạy trên đường rời thông thường, **không phải bảo đảm tuyệt đối bất chấp JVM/process**.
- [ ] Không `return` trong `finally` và tránh làm exception từ finally che primary.
- [ ] Với `AutoCloseable`, ưu tiên `try-with-resources` và kiểm tra `getSuppressed()` khi có lỗi close.
- [ ] Không đánh đồng exception được ném với “chưa có side effect”.
- [ ] Không log trực tiếp secrets, token, PII vào exception message hoặc phản hồi client.
- [ ] Phân biệt interruption/cancellation với lỗi nghiệp vụ, nhất là trong worker.
- [ ] Kiểm thử cả success, checked failure, unchecked failure, catch mapping, finally, close failure và suppressed errors.

**Tóm lại:** `throw` làm lỗi **xảy ra**, `throws` mô tả lỗi checked **có thể thoát**, `catch` quyết định **có xử lý loại lỗi đó tại tầng này không**, và `finally` đảm nhận **bước hoàn tất/dọn dẹp khi rời khối**. Sử dụng đúng không chỉ là đúng cú pháp, mà còn là giữ nguyên nhân, không che trạng thái thất bại và không tạo thành công giả.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-reference-002">Bảng checked, unchecked, Exception và Error</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng checked, unchecked, Exception và Error trong Java

## 1. Mục đích của bảng tra cứu

Java dùng hệ thống kiểu `Throwable` để biểu diễn những tình huống khiến luồng xử lý bình thường bị gián đoạn. Khi đọc một chữ ký như `void read() throws IOException`, thấy `NullPointerException` trong log hoặc gặp `NoClassDefFoundError` khi khởi động, cần phân biệt hai câu hỏi:

1. **Kiểu đó nằm ở đâu trong cây kế thừa?** `Throwable`, `Exception`, `RuntimeException` hay `Error`?
2. **Trình biên dịch có bắt buộc caller phải bắt hoặc khai báo không?** Đây mới là ranh giới giữa **checked** và **unchecked**.

Bảng dưới đây dùng Java 17+, gồm cây phân cấp, ngoại lệ thường gặp, quy tắc `try/catch/throws`, ví dụ biên dịch/chạy được, các trường hợp đồng thời/bất đồng bộ và nguyên tắc thiết kế API. Nó là tài liệu **tra cứu và giải thích**, không phải lời khuyên “catch mọi lỗi” hoặc ép mọi phương thức ném checked exception.

### 1.1. Ba khái niệm cần phân biệt ngay

- **`Exception` là một lớp trong Java**, không đồng nghĩa với tất cả những thứ có thể bị `throw`. `Error` không kế thừa `Exception`.
- **Checked/unchecked là quy tắc kiểm tra tĩnh của compiler**, không phải thước đo chắc chắn về độ nặng, khả năng phục hồi hoặc nguồn gốc lỗi.
- **`RuntimeException` thuộc `Exception` nhưng là unchecked**; đây là chi tiết người mới học thường nhầm nhất.

Trong Java, `Throwable` là lớp cha chung của hai nhánh chính `Exception` và `Error`. Theo phân loại checked-exception của ngôn ngữ, một kiểu `Throwable` là **unchecked** nếu nó là `RuntimeException`, `Error` hoặc lớp con của chúng; các kiểu `Throwable` còn lại là **checked**. Do đó bản thân `Exception` là checked, còn `RuntimeException` dù là lớp con của `Exception` lại unchecked.

## 2. Cây phân cấp tổng quan

~~~text
java.lang.Object
    |
    +-- java.lang.Throwable                     checked
          |
          +-- java.lang.Exception                checked
          |     |
          |     +-- IOException                  checked
          |     |     +-- FileNotFoundException
          |     |     +-- ...
          |     +-- SQLException                  checked
          |     +-- ParseException                checked
          |     +-- InterruptedException          checked
          |     +-- ClassNotFoundException        checked
          |     +-- ExecutionException            checked
          |     +-- TimeoutException              checked
          |     |
          |     +-- RuntimeException              UNCHECKED
          |           |
          |           +-- IllegalArgumentException
          |           |     +-- NumberFormatException
          |           +-- IllegalStateException
          |           +-- NullPointerException
          |           +-- IndexOutOfBoundsException
          |           +-- ArithmeticException
          |           +-- CompletionException
          |           +-- CancellationException
          |           +-- UncheckedIOException
          |           +-- ...
          |
          +-- java.lang.Error                     UNCHECKED
                |
                +-- AssertionError
                +-- VirtualMachineError
                |     +-- OutOfMemoryError
                |     +-- StackOverflowError
                +-- LinkageError
                |     +-- NoClassDefFoundError
                |     +-- NoSuchMethodError
                |     +-- ExceptionInInitializerError
                +-- ...
~~~

Cây được **lược bớt** để dễ tra cứu, không liệt kê mọi lớp JDK. Các tên không có package trong cây thuộc nhiều package khác nhau; chẳng hạn `IOException` thuộc `java.io`, `SQLException` thuộc `java.sql`, `ExecutionException` và `TimeoutException` thuộc `java.util.concurrent`.

Một lớp tự định nghĩa cũng có thể kế thừa trực tiếp `Throwable`. Trường hợp đó vẫn là **checked**, miễn không nằm dưới `RuntimeException` hoặc `Error`. Tuy nhiên trong API ứng dụng thông thường, nên kế thừa `Exception` hoặc `RuntimeException` đúng ngữ nghĩa thay vì kế thừa trực tiếp `Throwable`.

## 3. Bảng chính: checked, unchecked, Exception và Error

| Khái niệm | Quan hệ kế thừa | Compiler buộc catch/declare? | Ý nghĩa thường gặp |
| --- | --- | --- | --- |
| `Throwable` | Cha của `Exception` và `Error` | **Có** khi trực tiếp ném một `Throwable` không thuộc nhánh unchecked | Nền tảng biểu diễn thứ có thể được ném |
| `Exception` | `extends Throwable` | **Có** với chính `Exception` và các subclass checked | Lỗi/điều kiện cần truyền qua API theo chính sách |
| Checked exception | `Throwable` trừ nhánh `RuntimeException`/`Error` | **Có**, khi có thể thoát khỏi code và thuộc kiểm tra compile-time | Nhiều lỗi I/O, parsing, điều phối |
| `RuntimeException` | `extends Exception` | **Không** | Sai argument/state, lỗi lập trình hoặc trạng thái runtime theo thiết kế API |
| Unchecked exception | `RuntimeException` hoặc `Error` và các subclass | **Không** | Lỗi/điều kiện không cần khai báo bắt buộc |
| `Error` | `extends Throwable`, **không** kế thừa `Exception` | **Không** | Lỗi JVM/linkage, assertion hoặc điều kiện nghiêm trọng khác |

**Câu nhớ nhanh:** `Exception` không đồng nghĩa “checked”, bởi `RuntimeException` là một `Exception` nhưng unchecked. Ngược lại, không phải mọi `Throwable` đều là `Exception`, vì `Error` là nhánh độc lập.

### 3.1. Công thức phân loại theo kiểu

Với một kiểu `T` kế thừa `Throwable`:

~~~text
Nếu T extends RuntimeException  -> unchecked
Nếu T extends Error             -> unchecked
Nếu T extends Throwable nhưng
  không thuộc hai nhánh trên    -> checked
~~~

Dùng chữ “`extends`” ở đây theo nghĩa **quan hệ subtype kế thừa bắc cầu**, không nhất thiết câu lệnh trực tiếp trong source. Chẳng hạn `NumberFormatException` extends `IllegalArgumentException` extends `RuntimeException`, vì vậy là unchecked. `NoClassDefFoundError` nằm dưới `LinkageError` extends `Error` nên unchecked.

### 3.2. Checked không có nghĩa lỗi nhẹ, unchecked không có nghĩa lỗi nặng

`IOException` do filesystem không ghi được có thể làm thất bại thao tác quan trọng. `IllegalArgumentException` có thể là điều kiện validation dễ khắc phục. `AssertionError` đôi khi là một assertion không thỏa trong test, không nhất thiết mọi lần đều khiến JVM lập tức tắt.

Vì thế chọn cách xử lý dựa vào **hợp đồng và trạng thái hệ thống**, không chỉ nhìn checked/unchecked:
- Có thể phục hồi thật không?
- Có side effect nào đã xảy ra trước khi lỗi ném?
- Caller có đủ thông tin và quyền retry không?
- Có cần shutdown/restart hay đối soát dữ liệu không?
- Có thể bảo đảm invariant sau khi catch không?

## 4. Bảng checked exception thường gặp

| Kiểu | Package / nguồn phổ biến | Khi nào gặp? | Điểm cần nhớ |
| --- | --- | --- | --- |
| `IOException` | `java.io` | Đọc/ghi file, stream, I/O | Checked; nhiều subtype |
| `FileNotFoundException` | `java.io` | Mở file không tìm thấy/không mở được theo API | Subclass của IOException |
| `NoSuchFileException` | `java.nio.file` | Đường dẫn không tồn tại trong NIO | Subclass checked của IOException qua FileSystemException |
| `EOFException` | `java.io` | Hết dữ liệu bất ngờ ở một số API stream | Checked |
| `SQLException` | `java.sql` | JDBC/SQL/driver/database error | Checked; trạng thái transaction cần xem riêng |
| `SQLTimeoutException` | `java.sql` | JDBC thao tác timeout theo driver | Checked; không mặc định rollback mọi side effect |
| `ParseException` | `java.text` | Phân tích chuỗi ngày/text theo API liên quan | Checked |
| `URISyntaxException` | `java.net` | Chuỗi URI không đúng cú pháp theo `new URI(String)` | Checked |
| `ClassNotFoundException` | `java.lang` | API tải class theo tên không tìm thấy class | Checked |
| `InterruptedException` | `java.lang` | Thread đang chờ bị interrupt tại API hỗ trợ | Checked; giữ/lan truyền yêu cầu ngắt |
| `ExecutionException` | `java.util.concurrent` | `Future.get()` quan sát task lỗi | Checked; nguyên nhân ở `getCause()` |
| `TimeoutException` | `java.util.concurrent` | `Future.get(timeout)` hết hạn chờ | Checked; không tự hủy task |
| `ReflectiveOperationException` | `java.lang` | Một số thao tác reflection | Checked |
| `GeneralSecurityException` | `java.security` | Một số API mật mã/chứng thư | Checked |
| `CloneNotSupportedException` | `java.lang` | Lời gọi `Object.clone()` không được hỗ trợ | Checked |

Bảng không có ý nói mọi thao tác I/O đều ném `IOException` hoặc mọi timeout đều là `TimeoutException`. **Loại exception phụ thuộc API cụ thể**: HTTP client, JDBC, CompletableFuture, socket và thư viện có thể dùng các kiểu khác nhau.

### 4.1. Checked có thể nằm ngoài `Exception` không?

Có: một subclass trực tiếp của `Throwable` mà **không** nằm dưới `RuntimeException`/`Error` cũng checked. Ví dụ:

~~~java
class DirectThrowable extends Throwable {
    DirectThrowable(String message) {
        super(message);
    }
}
~~~

Đây là fragment lớp có thể đặt trong một file nguồn Java phù hợp. Một phương thức ném `new DirectThrowable("...")` thông thường phải bắt hoặc khai báo `throws DirectThrowable`.

Tuy nhiên **không nên thiết kế business exception bằng cách extends Throwable trực tiếp** nếu không có lý do đặc biệt: phần lớn code ứng dụng bắt `Exception` sẽ không bắt được nó, gây hành vi dễ nhầm. Thực tế thường sử dụng `extends Exception` cho checked domain failure hoặc `extends RuntimeException` cho unchecked domain failure theo contract.

## 5. Bảng unchecked RuntimeException thường gặp

| Kiểu | Tình huống điển hình | Cách điều tra/thiết kế |
| --- | --- | --- |
| `IllegalArgumentException` | Tham số không thỏa tiền điều kiện | Kiểm tra input contract/validation |
| `NumberFormatException` | Không parse được số với `Integer.parseInt` và API tương tự | Subclass của IllegalArgumentException |
| `IllegalStateException` | Object hoặc hệ thống không ở trạng thái cho phép | Kiểm tra state transition và tiền điều kiện |
| `NullPointerException` | Truy cập qua null hoặc API yêu cầu non-null | Xem nguồn null và ngữ cảnh dòng |
| `IndexOutOfBoundsException` | Index ngoài phạm vi collection/array theo API | Kiểm tra size/index |
| `ArrayIndexOutOfBoundsException` | Index sai trong array | Subclass của IndexOutOfBoundsException |
| `ArithmeticException` | Ví dụ phép chia nguyên cho zero | Không phải mọi phép toán floating-point cho hành vi tương tự |
| `ClassCastException` | Ép kiểu runtime không tương thích | Kiểm tra type và classloader |
| `UnsupportedOperationException` | Operation không được hỗ trợ, ví dụ collection immutable | Đọc contract của object |
| `ConcurrentModificationException` | Một số iterator phát hiện sửa collection sai contract | Fail-fast thường best effort; không dùng như cơ chế đồng bộ |
| `IllegalMonitorStateException` | `wait/notify` hoặc monitor operation sai ownership | Kiểm tra synchronized/lock |
| `SecurityException` | API từ chối do cơ chế security tương ứng | Có thể do policy/config, không chỉ bug |
| `CompletionException` | CompletionStage/`CompletableFuture.join()` báo lỗi nguồn | Đọc `getCause()` |
| `CancellationException` | `Future.get()`/`join()` trên future đã hủy | Hủy không đồng nghĩa công việc dưới đã dừng |
| `UncheckedIOException` | Wrapper unchecked chứa IOException | Đọc `getCause()` |
| `DateTimeException` | API `java.time` từ chối dữ liệu ngày giờ | Unchecked trong `java.time` |

**RuntimeException không đồng nghĩa với “lỗi do JVM tự tạo”.** Developer có thể chủ động `throw new IllegalArgumentException(...)`, framework có thể ném exception unchecked của riêng mình, và một `RuntimeException` có thể biểu thị lỗi hạ tầng đã được wrapper. Phải xem type, cause và hợp đồng cụ thể.

### 5.1. Khi nào dùng RuntimeException do ứng dụng tự tạo?

Ví dụ một bất biến của service bị vi phạm:

~~~java
if (amount.signum() < 0) {
    throw new IllegalArgumentException("amount must be non-negative");
}
~~~

Đây là fragment giả định `amount` là `BigDecimal`. Việc dùng unchecked giúp chữ ký API gọn khi tiền điều kiện đã được mô tả, nhưng không miễn nghĩa vụ kiểm tra dữ liệu từ nguồn không tin cậy. Nếu một tình huống là kết quả nghiệp vụ dự kiến (ví dụ từ chối hạn mức), ứng dụng có thể chọn result type hoặc domain exception theo convention.

Đừng lạm dụng `RuntimeException` chung với message mơ hồ ở mọi tầng; custom subtype hoặc mã lỗi ổn định có thể giúp caller phân loại.

## 6. Bảng `Error` thường gặp

| Kiểu | Nhánh | Nguyên nhân/thông điệp thông thường | Hướng xử lý |
| --- | --- | --- | --- |
| `AssertionError` | `Error` | Assertion thất bại hoặc code chủ động ném | Sửa invariant/test hoặc điều kiện assert |
| `OutOfMemoryError` | `VirtualMachineError` | JVM không thể cấp đủ tài nguyên bộ nhớ cho thao tác | Điều tra heap/native resources, tải và môi trường; không giả định catch an toàn |
| `StackOverflowError` | `VirtualMachineError` | Call stack vượt khả năng | Tìm recursion/vòng gọi bất thường |
| `NoClassDefFoundError` | `LinkageError` | Không thể định nghĩa/tìm class cần khi linking hoặc class init trước đó lỗi | Kiểm tra dependency/classpath/cause/init |
| `NoSuchMethodError` | `IncompatibleClassChangeError` thuộc `LinkageError` | Binary caller tham chiếu method không có ở runtime binary | Kiểm tra phiên bản thư viện/descriptor |
| `NoSuchFieldError` | `IncompatibleClassChangeError` | Runtime class thiếu field mà bytecode tham chiếu | Kiểm tra ABI/JAR/runtime |
| `UnsupportedClassVersionError` | `ClassFormatError` thuộc `LinkageError` | Class file version không được runtime hỗ trợ | So compiler target và runtime JDK |
| `ExceptionInInitializerError` | `LinkageError` | Static initializer ném exception không được xử lý | Xem `<clinit>` và `getCause()` |
| `LinkageError` | `Error` | Vấn đề linking/loading binary | Xác minh artifact, dependency/module/classloader |
| `InternalError` | `VirtualMachineError` | Điều kiện lỗi nội bộ JVM | Thu bằng chứng runtime/JDK, tránh tiếp tục mù quáng |

**`Error` không phải “một Exception checked nặng hơn”.** Nó là nhánh riêng dưới `Throwable` và unchecked. Các `Error` liên quan JVM/resource/linkage thường không phù hợp để catch rồi chạy tiếp như một lỗi nghiệp vụ thông thường.

### 6.1. `AssertionError` là một trường hợp cần hiểu đúng

`AssertionError` có thể sinh ra từ cú pháp `assert` khi assertions được bật, hoặc do code/test framework tạo ra. Java mặc định thường **không bật assertions** khi chạy không có `-ea`; đừng xây nghiệp vụ bảo mật hay validation bắt buộc chỉ bằng `assert`.

~~~java
assert amount >= 0 : "amount must be non-negative";
~~~

Đây là fragment với biến `amount` đã khai báo. Khi assertions bật và điều kiện sai, `AssertionError` có thể xuất hiện; khi assertions tắt, biểu thức assert không có tác dụng như một validation thông thường. Dùng exception/kiểm tra tham số rõ ràng cho các invariant phải luôn được thực thi.

### 6.2. Có phải mọi Error đều làm JVM tự thoát ngay?

Không có một quy tắc tuyệt đối như vậy. `AssertionError` có thể được test framework bắt để báo test fail; một thread có thể kết thúc vì uncaught `Error` nhưng các thread khác vẫn tồn tại. Tuy nhiên **không nên dựa vào khả năng catch Error để tiếp tục an toàn** sau `OutOfMemoryError` hoặc lỗi nghiêm trọng khác: trạng thái JVM/tài nguyên có thể không còn đáp ứng giả định.

Việc xử lý lỗi nghiêm trọng thuộc phạm vi runtime supervision, restart, telemetry và chiến lược phục hồi hệ thống. Một khối `catch (Throwable t) { return defaultValue; }` ở tầng nghiệp vụ thường phá vỡ khả năng nhận biết điều kiện nguy hiểm.

## 7. Chương trình Java 17 phân loại Throwable — biên dịch và chạy được

Chương trình sau chủ động tạo những object thuộc bốn nhóm để kiểm tra quan hệ kế thừa bằng `instanceof`. Nó còn cho thấy cách bắt checked `IOException` và unchecked `IllegalArgumentException`.

Lưu thành **`ThrowableClassificationDemo.java`**:

~~~java
import java.io.IOException;

public class ThrowableClassificationDemo {
    static class CustomChecked extends Exception {}
    static class CustomUnchecked extends RuntimeException {}

    static String classify(Throwable t) {
        if (t instanceof Error) return "unchecked Error";
        if (t instanceof RuntimeException) return "unchecked RuntimeException";
        if (t instanceof Exception) return "checked Exception";
        return "checked Throwable";
    }

    static void read() throws IOException {
        throw new IOException("io problem");
    }

    static void validate(int x) {
        if (x < 0) throw new IllegalArgumentException("negative");
    }

    public static void main(String[] args) {
        Throwable[] samples = {
            new IOException(),
            new CustomChecked(),
            new IllegalArgumentException(),
            new CustomUnchecked(),
            new AssertionError(),
            new Throwable()
        };

        for (Throwable t : samples) {
            System.out.println(t.getClass().getSimpleName()
                    + " -> " + classify(t));
        }

        try {
            read();
        } catch (IOException e) {
            System.out.println("caught checked: " + e.getMessage());
        }

        try {
            validate(-1);
        } catch (IllegalArgumentException e) {
            System.out.println("caught unchecked: " + e.getMessage());
        }
    }
}
~~~

Biên dịch và chạy với JDK 17 hoặc mới hơn:

~~~bash
javac --release 17 ThrowableClassificationDemo.java
java ThrowableClassificationDemo
~~~

**Stdout đã được kiểm chứng khi chạy chương trình:**

~~~text
IOException -> checked Exception
CustomChecked -> checked Exception
IllegalArgumentException -> unchecked RuntimeException
CustomUnchecked -> unchecked RuntimeException
AssertionError -> unchecked Error
Throwable -> checked Throwable
caught checked: io problem
caught unchecked: negative
~~~

### 7.1. Tại sao chương trình phân loại đúng?

Thứ tự các nhánh trong `classify` là chủ đích:
1. `Error` trước: thuộc nhánh Error, không phải Exception.
2. `RuntimeException` kế tiếp: là subclass `Exception` nhưng phải nhận nhãn **unchecked**.
3. `Exception` còn lại: checked.
4. Những `Throwable` còn lại: trong ví dụ là một `new Throwable()` trực tiếp, checked.

Nếu đảo hai điều kiện `if (t instanceof Exception)` và `if (t instanceof RuntimeException)`, mọi RuntimeException sẽ bị nhận nhầm là checked Exception, vì `RuntimeException extends Exception`. Đây là lỗi logic phân loại dễ gặp khi tự viết một báo cáo thống kê throwable.

Chương trình tạo `AssertionError` như một **đối tượng dùng để phân loại**, không ném nó. Điều đó cho phép minh họa cây kế thừa mà không khuyến khích catch `Error` để phục hồi nghiệp vụ.

### 7.2. Những điều ví dụ không ngụ ý

- Không phải mọi `Error` đều có chung chiến lược xử lý.
- Không phải mọi checked exception đều nên catch ngay tại nơi phát sinh.
- Không phải RuntimeException nào cũng là bug lập trình; có runtime wrapper cho lỗi I/O/async.
- Biên dịch chạy thành công ví dụ này không chứng minh code business nào cũng nên `catch (IOException)`. Hợp đồng API phải dẫn dắt lựa chọn.
- `new Throwable()` trực tiếp được dùng cho mục đích giáo dục, không phải khuyến nghị tạo lớp domain extends Throwable.

## 8. Checked exception: compiler yêu cầu catch hoặc declare

Xem phương thức:

~~~java
static String readFirstLine(Path path) throws IOException {
    return Files.readAllLines(path).get(0);
}
~~~

Đây là **fragment Java**, cần import `java.nio.file.Path`, `java.nio.file.Files`, `java.io.IOException`. `Files.readAllLines` có thể ném `IOException` checked. Ngoài ra, `get(0)` có thể ném `IndexOutOfBoundsException` unchecked nếu file rỗng; `throws IOException` không ngăn lỗi unchecked đó.

Khi caller gọi:

~~~java
String line = readFirstLine(path);
~~~

Nếu lời gọi có thể ném checked `IOException`, caller phải:
- **Bắt lỗi** bằng một `catch` phù hợp, hoặc
- **Khai báo `throws IOException`** (hay một supertype hợp lệ) để truyền tiếp.

Ví dụ catch:

~~~java
try {
    String line = readFirstLine(path);
    use(line);
} catch (IOException e) {
    handleIoFailure(e);
}
~~~

Ví dụ truyền tiếp:

~~~java
static void process(Path path) throws IOException {
    String line = readFirstLine(path);
    use(line);
}
~~~

Đây đều là **fragments**, những hàm `use`/`handleIoFailure` là giả định. Chỉ có một `throws` trong chữ ký **không có nghĩa JVM tự bắt exception**; nó nói với compiler/caller về khả năng exception checked có thể đi ra. Nếu không ai bắt cuối cùng, exception vẫn có thể lên tới thread boundary/uncaught handler.

### 8.1. Compile error khi bỏ catch/throws

Giả định có:

~~~java
static void readFile() throws IOException {
    throw new IOException("disk error");
}

static void caller() {
    readFile();
}
~~~

Đây là fragment cố tình **không biên dịch**: `caller` không xử lý checked exception do `readFile` khai báo. Compiler thường báo nội dung tương đương “unreported exception IOException; must be caught or declared to be thrown”. Đây là diagnostic minh họa, chữ cụ thể có thể khác compiler/phiên bản.

Hai cách sửa:

~~~java
static void caller() throws IOException {
    readFile();
}
~~~

Hoặc:

~~~java
static void caller() {
    try {
        readFile();
    } catch (IOException e) {
        report(e);
    }
}
~~~

Đây là hai **phương án fragment độc lập** cho cùng method; không đặt cả hai phương thức cùng chữ ký trong một class rồi biên dịch cùng lúc. `report` cần được định nghĩa. Nếu chỉ catch rồi bỏ qua, compiler hài lòng nhưng chương trình **chưa chắc đúng nghiệp vụ**. Compiler chỉ kiểm tra được **hợp đồng type**, không chứng minh recovery thành công.

### 8.2. Chữ ký `throws Exception` hợp lệ nhưng thường quá rộng

Một phương thức khai báo:

~~~java
void execute() throws Exception {
    readFile();
}
~~~

có thể truyền `IOException` ra ngoài qua supertype `Exception`. Nhưng caller mất độ cụ thể: không biết loại checked nào thực sự được API công khai. Trong API ổn định, ưu tiên các kiểu checked đủ cụ thể hoặc một domain exception có cause. Với framework/callback có quy ước `throws Exception`, vẫn có thể dùng theo contract.

**`throws Exception` không giới hạn chỉ các Exception có thể thoát:** RuntimeException và Error vẫn có thể phát sinh mà không cần khai báo. Đồng thời `throws Exception` cũng không có nghĩa phương thức luôn ném exception.

## 9. Unchecked exception: compiler không buộc khai báo nhưng vẫn có thể lan ra

~~~java
static int parseCount(String raw) {
    return Integer.parseInt(raw);
}
~~~

Đây là fragment. Nếu `raw` không hợp lệ, `Integer.parseInt` có thể ném `NumberFormatException` unchecked; compiler không yêu cầu `parseCount` khai báo `throws NumberFormatException`. Caller có thể catch nếu có recovery phù hợp, nhưng không bị bắt buộc.

Một developer **có thể** viết:

~~~java
static int parseCount(String raw) throws NumberFormatException {
    return Integer.parseInt(raw);
}
~~~

Dù `throws` này không bắt buộc, nó có thể được dùng để nhấn mạnh contract. Với public API, Javadoc thường là nơi mô tả điều kiện ném unchecked exception; chỉ dựa vào compiler sẽ không đủ.

### 9.1. Unchecked không có nghĩa nên luôn để crash

Nếu input người dùng sai format, tầng HTTP có thể bắt `NumberFormatException` ở ranh giới validation và trả thông báo lỗi 400 phù hợp. Nhưng catch nên:
- chỉ bắt type dự kiến;
- biết input nào lỗi;
- không che `NullPointerException` bất ngờ;
- không biến lỗi thành một kết quả “thành công” giả.

Ví dụ `Integer.parseInt("abc")` ném `NumberFormatException`; đó là loại unchecked, nhưng một controller có thể chuyển nó thành validation error **theo hợp đồng**. Không có mâu thuẫn giữa “unchecked” và “có thể xử lý được”.

## 10. `catch (Exception)` bắt được gì?

| Catch clause | Checked Exception subclass | RuntimeException subclass | Error subclass | Throwable trực tiếp |
| --- | --- | --- | --- | --- |
| `catch (IOException e)` | Chỉ IOException và subclass | Không, trừ khi một kiểu có quan hệ subtype phù hợp — không có RuntimeException subclass của IOException trong cây JDK | Không | Không |
| `catch (Exception e)` | **Có** | **Có** | **Không** | Không |
| `catch (RuntimeException e)` | Không | **Có** | Không | Không |
| `catch (Error e)` | Không | Không | **Có** | Không |
| `catch (Throwable t)` | **Có** | **Có** | **Có** | **Có** |

Bảng nói về **type matching khi một throwable đã được ném**. Nó không có nghĩa catch mọi kiểu đều là chính sách tốt. `catch (Exception)` sẽ bắt `IllegalArgumentException` và `NullPointerException`, do đó có thể che bug nếu trả fallback không phân loại. Nhưng `catch (Exception)` không bắt `OutOfMemoryError` hoặc `NoSuchMethodError`.

### 10.1. Thứ tự catch từ cụ thể đến tổng quát

~~~java
try {
    read();
} catch (IOException e) {
    handleIo(e);
} catch (Exception e) {
    handleOther(e);
}
~~~

Đây là fragment với `read`/`handleIo`/`handleOther` giả định. Nếu một exception thuộc IOException, nhánh đầu xử lý. Nếu đặt `catch (Exception)` trước `catch (IOException)`, nhánh thứ hai **không thể tới được** vì IOException là subtype của Exception, dẫn tới compile error.

Đừng viết `catch (RuntimeException)` trước `catch (IllegalArgumentException)` vì IllegalArgumentException là subtype của RuntimeException, tình huống tương tự.

### 10.2. Có nên `catch (Throwable)` để “không bị crash”?

Ở tầng nghiệp vụ thông thường, **không**. Catch Throwable có thể nuốt cả `OutOfMemoryError`, `StackOverflowError` hoặc `LinkageError` rồi chạy tiếp trên trạng thái không tin cậy. Ở một số ranh giới nền tảng đặc biệt (ví dụ framework thực thi task, test runner) có thể cần catch rộng để ghi nhận/truyền lại/giám sát, nhưng không nên chuyển toàn bộ Throwable thành `null` hoặc kết quả thành công.

Một số `Error` có thể là assertion có chủ đích. Bắt nó trong **test framework** để báo test failure khác với bắt nó trong code lưu dữ liệu rồi nói “đã lưu”.

## 11. `throw` và `throws` không giống nhau

- `throw expression;` là lệnh **ném một đối tượng Throwable** tại runtime.
- `throws Type1, Type2` là **khai báo chữ ký** về khả năng exception có thể lan ra, đặc biệt quan trọng với checked exception.

Ví dụ:

~~~java
static void openFile() throws IOException {
    throw new IOException("unavailable");
}
~~~

Đây là fragment hợp lệ trong class Java sau khi import `IOException`. `throws IOException` không tạo lỗi; `throw new IOException(...)` mới thực sự ném khi code chạy.

Với unchecked:

~~~java
static void requirePositive(int count) {
    if (count <= 0) {
        throw new IllegalArgumentException("count must be positive");
    }
}
~~~

Không bắt buộc khai báo `throws IllegalArgumentException`, dù phương thức có thể ném nó.

### 11.1. Java có cho `throw` một giá trị không phải Throwable không?

Không: `throw "error";` không hợp lệ, vì chuỗi String không phải subtype của Throwable. Java kiểm tra kiểu của expression được throw. Không có phép đổi mọi object sang exception tự động.

Với exception do ứng dụng tự định nghĩa:

~~~java
class InvalidDocumentException extends Exception {
    InvalidDocumentException(String message) {
        super(message);
    }
}
~~~

Đây là fragment checked. Nếu đổi `extends Exception` thành `extends RuntimeException`, compiler không còn yêu cầu caller catch/declare. Quyết định này **thay đổi hợp đồng compile-time** của API, không chỉ thay tên class.

## 12. Cách thiết kế custom exception checked và unchecked

Hai ví dụ:

~~~java
public final class StorageUnavailableException extends Exception {
    public StorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
~~~

~~~java
public final class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
~~~

Đây là **hai fragment lớp public độc lập**, cần lưu trong các file `StorageUnavailableException.java` và `InvalidRequestException.java` nếu biên dịch riêng ở package thích hợp. Lớp đầu checked, lớp sau unchecked.

### 12.1. Tiêu chí chọn

**Checked phù hợp khi:**
- Lỗi là một phần contract cần caller xác nhận đã xử lý hoặc tuyên bố truyền tiếp.
- Caller thường có hành động nghiệp vụ ý nghĩa (retry, đổi nguồn, báo lỗi rõ, cleanup).
- Module chấp nhận tăng chữ ký `throws` và propagation qua các tầng.

**Unchecked phù hợp khi:**
- Vi phạm tiền điều kiện hoặc invariant mà code phải sửa/validate.
- Lỗi không có recovery hợp lý ở mỗi tầng trung gian.
- API/framework/functional interface dùng convention unchecked.
- Một lỗi hạ tầng được wrap có chủ đích (ví dụ `UncheckedIOException`), đồng thời **giữ cause** và mô tả hợp đồng.

Những tiêu chí này là **heuristic**, không có luật “I/O luôn checked và validation luôn unchecked” cho mọi library. Một domain “không đủ hạn mức” có thể dùng Result, checked exception hoặc unchecked domain exception tùy cách tổ chức API.

### 12.2. Giữ cause khi bọc

~~~java
try {
    writeToDisk();
} catch (IOException e) {
    throw new UncheckedIOException("Write failed", e);
}
~~~

Đây là fragment với `writeToDisk` giả định và import `java.io.UncheckedIOException`. `UncheckedIOException` là subclass của RuntimeException, cho phép truyền lỗi I/O qua một boundary không cho khai báo checked; **cause vẫn là IOException**, có thể đọc bằng `getCause()`.

Không dùng `new RuntimeException(e.getMessage())` nếu cần giữ stack/cause từ I/O, vì message không chứa toàn bộ thông tin nguyên nhân.

## 13. Ngoại lệ checked với `try-with-resources`

`AutoCloseable.close()` được khai báo có thể ném `Exception`. Khi dùng một resource mà static type chỉ là `AutoCloseable`, compiler có thể yêu cầu xử lý `Exception` từ việc close, ngay cả khi phần thân không ném gì.

~~~java
static void use(AutoCloseable resource) throws Exception {
    try (resource) {
        System.out.println("using");
    }
}
~~~

Đây là fragment Java 9+ (hoạt động trên Java 17): resource parameter phải là biến cuối cùng hiệu lực (*effectively final*) khi sử dụng cú pháp `try (resource)`. Vì static type của `close` là `throws Exception`, chữ ký bên ngoài thường phải khai báo/bắt exception phù hợp.

Nếu static type là `java.io.Closeable`, `close()` khai báo `throws IOException` hẹp hơn. Đây là lý do **type của resource** ảnh hưởng checked exceptions từ cleanup.

### 13.1. Primary và suppressed

Nếu thân try ném A và resource.close() ném B, try-with-resources giữ A làm primary, B trong `A.getSuppressed()`. Nếu body hoàn tất bình thường nhưng close ném B, B có thể thành primary. Với nhiều resource, Java đóng theo thứ tự ngược và có thể có nhiều suppressed.

Điều đó không đổi checked/unchecked của các exception: một `IOException` từ close vẫn checked khi API cần catch/declare; một `RuntimeException` từ close vẫn unchecked. Suppressed là **quan hệ giữa object Throwable**, không phải nhóm phân loại mới.

## 14. `InterruptedException`: checked đặc biệt liên quan điều phối

`InterruptedException` là checked exception, xuất hiện ở những API blocking hỗ trợ interruption như `Thread.sleep`, `BlockingQueue.take` hoặc `Future.get` khi thread đang chờ bị interrupt.

~~~java
try {
    Thread.sleep(100);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return;
}
~~~

Đây là fragment bên trong một phương thức `void`/lambda thích hợp. Nếu không truyền tiếp được, thường khôi phục interrupt flag và kết thúc công việc hợp tác. Nếu phương thức cho phép `throws InterruptedException`, có thể truyền tiếp cho caller.

**Không nuốt `InterruptedException` rồi tiếp tục vô hạn**: nó có thể là yêu cầu hủy/shutdown. Nhưng cũng không coi mọi interrupt là lỗi nghiệp vụ do một task khác ném; nó là tín hiệu tác động lên thread đang chờ.

### 14.1. `CancellationException` lại là unchecked

`Future.cancel(...)` có thể làm `Future.get()` ném `CancellationException`; nó kế thừa RuntimeException nên unchecked. Hai kiểu này thể hiện hai sự kiện khác nhau:
- `InterruptedException`: **thread đang chờ** bị interrupt.
- `CancellationException`: Future được quan sát ở trạng thái canceled.

Neither guarantees the underlying remote side effect rolled back. Khi caller hủy sau timeout, task có thể vẫn chạy nếu không hợp tác. Đừng catch cả hai và tự trả “đã thành công”.

## 15. `Future` và `CompletableFuture`: bảng đối chiếu exception

| API / tình huống | Exception caller quan sát | Checked? | Ghi chú |
| --- | --- | --- | --- |
| `Future.get()` khi task thất bại | `ExecutionException` | **Có** | Lỗi task nằm ở `getCause()` |
| `Future.get()` khi thread chờ bị interrupt | `InterruptedException` | **Có** | Task không nhất thiết đã dừng |
| `Future.get(timeout, unit)` khi quá hạn | `TimeoutException` | **Có** | Không tự hủy task |
| `Future.get()` sau khi future bị hủy | `CancellationException` | Không | Subclass của RuntimeException |
| `CompletableFuture.join()` khi stage thất bại | Thường `CompletionException` | Không | Cause giữ lỗi từ stage nguồn |
| `CompletableFuture.join()` trên future canceled | `CancellationException` có thể ném trực tiếp | Không | Cancellation không tự interrupt supplier |
| `CompletableFuture.get()` khi stage thất bại | `ExecutionException` | **Có** | `get()` tuân hợp đồng Future |
| `CompletableFuture.orTimeout(...)` khi stage quá hạn | Future hoàn thành ngoại lệ bằng `TimeoutException` | Kiểu TimeoutException là checked | `join()` thường bọc nó bằng unchecked `CompletionException` |
| Callback `whenComplete` tự ném RuntimeException | Stage phụ thuộc có thể thất bại | Không đối với RuntimeException | Nếu upstream đã lỗi, lỗi upstream được ưu tiên theo hợp đồng whenComplete |

**Điểm dễ nhầm:** cùng một `TimeoutException` là **checked type**, nhưng nếu được bọc trong `CompletionException` rồi đi qua `join()` thì caller nhận **unchecked wrapper**. Kiểu gốc không đổi, chỉ kênh biểu diễn lỗi ở boundary thay đổi.

### 15.1. `CompletableFuture.cancel(true)` không giống mọi `FutureTask.cancel(true)`

`CompletableFuture` triển khai Future nhưng tham số `mayInterruptIfRunning` trong `CompletableFuture.cancel(boolean)` không được dùng để điều khiển interrupt supplier. Nó có thể hoàn thành future bằng `CancellationException` mà phép tính đang chạy vẫn tiếp tục. Ngay cả với `FutureTask`, interruption là cơ chế hợp tác, không phải cưỡng chế rollback công việc.

Vì thế một phân loại “unchecked CancellationException” không nói gì chắc chắn về trạng thái side effect bên ngoài.

## 16. Reflection và binary compatibility: checked khác Error

Hai tên dễ nhầm:

| Kiểu | Checked? | Nghĩa |
| --- | --- | --- |
| `ClassNotFoundException` | Có | Một lời gọi API tìm class theo tên không tìm thấy class (ví dụ `Class.forName`) |
| `NoClassDefFoundError` | Không, thuộc Error | JVM không thể dùng/định nghĩa class cần thiết tại linking/initialization; nguyên nhân có thể là class thiếu hoặc init lỗi |

Tương tự:
- `InvocationTargetException` (checked, reflection) có thể bọc throwable do method được gọi ném.
- `ExceptionInInitializerError` (Error unchecked) thường liên quan static initialization thất bại.
- `NoSuchMethodException` (checked, reflection khi tra cứu method) khác `NoSuchMethodError` (Error unchecked, binary không tương thích lúc linking).

Đừng xử lý `NoSuchMethodError` bằng cách trả default như một phản hồi business bình thường. Nó thường đòi đối chiếu **class/JAR thực được JVM nạp, method descriptor, dependency versions và build/deploy artifact**. `ClassNotFoundException` cũng cần xác minh classloader/module path phù hợp, nhưng nguyên tắc catch/declare compile-time của nó khác.

### 16.1. Lỗi khi khởi tạo class có thể sinh chuỗi triệu chứng

Một static initializer ném exception có thể dẫn đến `ExceptionInInitializerError` ở lần truy cập đầu. Các lần truy cập sau vào class bị đánh dấu erroneous có thể dẫn đến `NoClassDefFoundError`. Đọc **cause và log gốc tại lần đầu** thay vì chỉ xem một lỗi về sau.

Không có nguyên tắc “NoClassDefFoundError luôn là thiếu JAR”; đôi khi class tồn tại nhưng initialization trước đó đã thất bại.

## 17. Checked exception và overriding

Quy tắc quan trọng khi override một instance method:
- Implementation **không được khai báo checked exception mới hoặc rộng hơn** những gì chữ ký method bị override cho phép.
- Có thể **khai báo checked exception hẹp hơn** hoặc không khai báo nữa.
- Unchecked exception không chịu giới hạn checked tương tự; vẫn phải tuân thủ contract và Liskov/thiết kế API ở mức ngữ nghĩa.

Ví dụ:

~~~java
interface Reader {
    String read() throws IOException;
}

final class FileReaderImpl implements Reader {
    @Override
    public String read() throws java.io.FileNotFoundException {
        throw new java.io.FileNotFoundException("missing");
    }
}
~~~

Đây là fragment, cần import `IOException` hoặc qualified name. `FileNotFoundException` là subclass của IOException, nên override hợp lệ. Nếu implementation thay bằng `throws SQLException` thì **không hợp lệ** vì SQLException là checked không nằm trong giới hạn của `Reader.read()`.

Việc implementation **không** khai báo `throws` được phép, nhưng caller thông qua reference `Reader` vẫn nhìn thấy contract `throws IOException` của interface khi compile.

### 17.1. Tại sao rule này tồn tại?

Nếu một API đã hứa chỉ ném checked IOException, một implementation khác không thể bất ngờ bắt caller phải xử lý checked SQLException theo signature mới mà không thay đổi type contract. Điều đó giúp tính thay thế giữa interface/base class và implementation.

Không nên dùng `throws Exception` ở interface chỉ để mọi implementation sau này tự do ném checked exception bất kỳ, trừ khi API thật sự cần hợp đồng rộng đó.

## 18. Checked exception trong lambda và method reference

Một lambda phải tương thích với chữ ký của **functional interface**. `java.util.function.Function<T,R>` chỉ có `R apply(T t)` và không khai báo checked exception. Do đó:

~~~java
Function<Path, String> loader = path -> Files.readString(path);
~~~

Đây là fragment **cố tình không biên dịch** (giả định imports), vì `Files.readString` có thể ném checked `IOException` nhưng `Function.apply` không cho `throws IOException`.

Một số hướng xử lý đúng:
1. Dùng interface tự định nghĩa `ThrowingFunction<T,R>` có `throws IOException` khi API kiểm soát được interface.
2. Bắt IOException bên trong lambda rồi chuyển thành `UncheckedIOException` **kèm cause**, nếu hợp đồng pipeline chấp nhận unchecked.
3. Trả result có kiểu `Found/Failed` thay vì chỉ giá trị thành công.
4. Đổi thiết kế để I/O thực hiện ở tầng đã có quyền xử lý checked exception.

Ví dụ fragment có wrapper:

~~~java
Function<Path, String> loader = path -> {
    try {
        return Files.readString(path);
    } catch (IOException e) {
        throw new UncheckedIOException("Cannot load " + path, e);
    }
};
~~~

Đây là fragment, cần import `Function`, `Path`, `Files`, `IOException`, `UncheckedIOException`. Bọc có cause giữ thông tin nguồn; không catch rồi trả chuỗi rỗng nếu chuỗi rỗng cũng là dữ liệu hợp lệ.

### 18.1. Streams không tự hiểu checked exception

`stream.map(...)` nhận Function, không tự thêm checked `throws IOException` vào chữ ký lambda. Không nên dùng một “sneaky throws” không rõ hợp đồng chỉ để vượt compiler, nhất là trong thư viện dùng chung. Chọn một adapter/wrapper/result có chính sách công khai.

## 19. Multi-catch và type chung

Java hỗ trợ bắt nhiều loại ngoại lệ không có quan hệ subclass trực tiếp:

~~~java
try {
    performIoAndDbOperation();
} catch (IOException | SQLException e) {
    report(e);
}
~~~

Đây là fragment với các hàm giả định; cả hai checked exception phải là loại có thể được phát sinh theo phân tích compile-time của khối try (và phải có import). Multi-catch chỉ hợp lệ khi các lựa chọn không có quan hệ cha–con bị cấm. `catch (IOException | FileNotFoundException e)` không hợp lệ vì FileNotFoundException là subclass của IOException.

Trong một multi-catch, biến `e` có các quy tắc typing riêng và là effectively final; cần dùng các thao tác có mặt trên kiểu chung thích hợp (ví dụ `Throwable.getMessage()`), không giả định e luôn là IOException.

Nếu `IOException` cần retry còn `SQLException` cần rollback theo những bước khác nhau, **đừng gộp** chỉ để giảm dòng code. Multi-catch phù hợp khi chính sách xử lý thực sự giống nhau.

## 20. Những lối hiểu sai phổ biến

### Sai 1 — “Mọi `Exception` đều checked”

Sai vì `RuntimeException extends Exception` nhưng unchecked. Hãy hỏi kiểu đó có nằm dưới RuntimeException hay Error không.

### Sai 2 — “`Error` là subclass của `Exception`”

Sai. Hai lớp là **hai nhánh dưới Throwable**. `catch (Exception)` không bắt Error.

### Sai 3 — “Checked là lỗi an toàn để phục hồi; unchecked là lỗi không phục hồi được”

Sai. Checked/unchecked là lựa chọn type contract của compiler, không chứng minh transactional/side effect safety. Một `IOException` có thể xảy ra sau khi file đã ghi một phần; một `NumberFormatException` có thể được chuyển thành lỗi validation an toàn.

### Sai 4 — “`throws` đã xử lý lỗi”

Sai. `throws` chỉ khai báo đường lan lỗi, không catch, rollback hay log.

### Sai 5 — “Catch rộng làm hệ thống ổn định”

`catch (Exception)` có thể che RuntimeException do bug. `catch (Throwable)` còn có thể che Error. Nếu không có recovery thật và trạng thái lỗi rõ, việc catch chỉ làm sai kết quả.

### Sai 6 — “`RuntimeException` không bao giờ cần ghi vào tài liệu API”

Compiler không bắt buộc khai báo, nhưng public API vẫn cần Javadoc/contract cho những điều kiện như invalid argument, illegal state hoặc timeout wrapper mà caller phải biết.

### Sai 7 — “`Error` luôn khiến cả JVM dừng ngay”

Không tuyệt đối. Ví dụ assertion có thể bị test framework bắt. Nhưng khả năng bắt không đồng nghĩa có thể tiếp tục an toàn sau lỗi nghiêm trọng.

### Sai 8 — “`Exception.getCause()` luôn khác null”

Không. Nhiều exception được tạo không có cause. Trước khi dereference `getCause()`, hãy kiểm tra, nhất là trong code log/unwrap tổng quát.

### Sai 9 — “Checked exception làm tất cả caller khó chịu, cứ chuyển mọi lỗi thành RuntimeException”

Bọc một checked exception thành unchecked có thể cần thiết ở ranh giới không hỗ trợ checked, nhưng **phải giữ cause và ngữ nghĩa**. Chuyển toàn bộ lỗi thành `RuntimeException("failed")` làm mất khả năng phân loại nghiệp vụ.

### Sai 10 — “Dùng `Optional.empty()` sau catch là đã xử lý checked IOException”

Compiler chấp nhận nhưng caller không còn phân biệt “không tìm thấy” với “storage lỗi” nếu empty là giá trị hợp lệ. Hãy giữ exception hoặc trả result `Failed`.

### Sai 11 — “`CancellationException` là checked như `InterruptedException`”

Không. `CancellationException` extends IllegalStateException/RuntimeException, unchecked; `InterruptedException` checked.

### Sai 12 — “`CompletionException` và `ExecutionException` giống nhau”

Hai kiểu đều có thể bọc nguyên nhân từ task, nhưng `CompletionException` là unchecked, `ExecutionException` là checked. Đọc boundary `join` vs `get` để hiểu vì sao.

### Sai 13 — “`NoClassDefFoundError` = `ClassNotFoundException`”

Không. Chúng có cây kế thừa khác, thời điểm phát sinh và ngữ nghĩa API/runtime khác. Đọc classloader, dependency và cause cụ thể.

### Sai 14 — “`Error` trong mã Java là mức log ERROR”

Không. `java.lang.Error` là lớp Throwable; log severity `ERROR` là khái niệm do logging framework quy định. Một `IOException` có thể được log ở mức ERROR mà **không trở thành `java.lang.Error`**.

## 21. Thực hành kiểm thử compiler và runtime

Ngoài chương trình runnable ở mục 7, hãy tự làm ba thí nghiệm ngắn.

### Thí nghiệm A — Compiler buộc xử lý checked

~~~java
import java.io.IOException;

public class CheckedCompileTest {
    static void fail() throws IOException {
        throw new IOException("read failed");
    }

    public static void main(String[] args) {
        fail(); // Cố ý lỗi compile: thiếu catch hoặc throws.
    }
}
~~~

Đây là **file Java cố tình không biên dịch**. Chạy `javac CheckedCompileTest.java` và xác nhận diagnostic liên quan unreported checked exception. Sau đó thêm `throws IOException` vào `main` **hoặc** bọc `fail()` trong `try/catch` rồi compile lại. Khi để `throws IOException` trên main và chạy mà không có catch, chương trình sẽ ném IOException lên ranh giới main; đây là behavior khác với compile-time success.

### Thí nghiệm B — Unchecked không cần khai báo

~~~java
public class UncheckedCompileTest {
    static void fail() {
        throw new IllegalArgumentException("invalid");
    }

    public static void main(String[] args) {
        fail();
    }
}
~~~

File Java này **biên dịch được**; khi chạy thì ném IllegalArgumentException và nếu không được catch, thread main sẽ kết thúc với stack trace. Đừng nhầm “biên dịch được” với “runtime thành công”.

### Thí nghiệm C — `catch (Exception)` không bắt `AssertionError`

~~~java
public class ErrorCatchTest {
    public static void main(String[] args) {
        try {
            throw new AssertionError("assertion failed");
        } catch (Exception e) {
            System.out.println("caught Exception");
        }
    }
}
~~~

File Java này có thể **biên dịch**, nhưng khi chạy, `AssertionError` không đi vào `catch (Exception)` vì Error không phải Exception. Đây là một thí nghiệm ngắn trong môi trường học tập, **không** phải khuyến nghị làm ứng dụng production chủ động throw Error để điều khiển nghiệp vụ.

Nếu muốn ghi nhận assertion failure có kiểm soát trong test, dùng test framework; đừng thêm `catch (Throwable)` để chương trình “luôn in thành công”.

## 22. Bảng quyết định khi đọc một exception trong log

~~~text
Gặp một Throwable trong Java
|
+-- Thuộc Error hoặc subclass?
|      +-- Có -> unchecked; xem VM/linkage/assertion/runtime
|      +-- Không
|
+-- Thuộc RuntimeException hoặc subclass?
|      +-- Có -> unchecked; xem cause, input, invariant, wrapper
|      +-- Không
|
+-- Thuộc Exception hoặc subclass?
|      +-- Có -> checked; caller phải catch/declare theo quy tắc Java
|      +-- Không -> Throwable trực tiếp/nhánh khác: checked
|
+-- Xử lý thế nào?
       +-- Có recovery thật -> catch chính xác và xác minh kết quả
       +-- Không có recovery -> propagate/wrap giữ cause
       +-- Có side effect -> xác minh trạng thái, không giả rollback
       +-- Qua async -> đọc ExecutionException/CompletionException cause
~~~

Cây phân loại giúp hiểu **kiểu và compiler obligations**, nhưng quyết định **cách phục hồi** vẫn phụ thuộc vào business contract. Không có nhánh mặc định “checked → retry” hoặc “unchecked → bỏ qua”.

## 23. Checklist khi thiết kế hoặc review xử lý ngoại lệ

- [ ] Biết rõ exception thuộc `Throwable`, `Exception`, `RuntimeException` hay `Error`.
- [ ] Không gọi mọi subclass của `Exception` là checked.
- [ ] Không giả định `catch (Exception)` sẽ bắt `Error`.
- [ ] Hiểu checked yêu cầu `catch` hoặc `throws` ở những điểm có thể lan ra.
- [ ] Chọn loại custom exception có chủ đích, tránh kế thừa Throwable trực tiếp khi không cần.
- [ ] Nếu API public ném unchecked theo tiền điều kiện, đã có tài liệu và test.
- [ ] Không dùng `catch (Throwable)` để che lỗi rồi trả mặc định.
- [ ] Không log rồi rethrow ở mọi tầng gây duplicate; giữ cause khi wrap.
- [ ] Không nuốt `InterruptedException`; propagate hoặc restore status phù hợp.
- [ ] Phân biệt `ExecutionException` checked với `CompletionException` unchecked.
- [ ] Phân biệt `ClassNotFoundException` với `NoClassDefFoundError` và `NoSuchMethodException` với `NoSuchMethodError`.
- [ ] Biết `try-with-resources` có thể ném checked từ `close()` và giữ suppressed errors.
- [ ] Không dùng `Optional.empty()`, `null` hoặc `false` để che lỗi mà contract không nói rõ.
- [ ] Nếu exception đến sau side effect hoặc timeout, không tự tuyên bố rollback.
- [ ] Nếu lỗi liên quan runtime JAR/linkage, xác minh binary được nạp thay vì chỉ sửa source line.
- [ ] Test cả trường hợp compile-time của checked và đường runtime của unchecked.
- [ ] Chọn Error handling phù hợp mức độ nghiêm trọng, không cố tiếp tục trong trạng thái không đáng tin.

## 24. Bài tập củng cố

### Bài 1 — Phân loại chính xác

Với từng kiểu sau, ghi: **cha gần nhất trong cây quan trọng**, **checked/unchecked**, và **compiler có buộc catch/declare không**:

~~~text
IOException
NumberFormatException
RuntimeException
SQLException
CancellationException
InterruptedException
CompletionException
ExecutionException
AssertionError
OutOfMemoryError
ClassNotFoundException
NoClassDefFoundError
Throwable
Exception
Error
UncheckedIOException
TimeoutException
~~~

Đáp án định hướng: mọi lớp con của RuntimeException hoặc Error là unchecked. IOException/SQLException/InterruptedException/ExecutionException/TimeoutException/ClassNotFoundException là checked. Bản thân Throwable và Exception là checked. UncheckedIOException và CompletionException thuộc RuntimeException; AssertionError và OutOfMemoryError thuộc Error.

### Bài 2 — Sửa lỗi compile

Cho phương thức `load() throws IOException` và một caller không khai báo throws, không catch. Hãy viết **hai** cách sửa. Giải thích vì sao một cách để caller cấp cao hơn chịu trách nhiệm và cách còn lại có thể phục hồi ngay trong phương thức hiện tại.

### Bài 3 — Phân biệt catch

Cho đoạn code có `catch (Exception e)`. Hãy dự đoán nó có bắt được `NullPointerException`, `IOException` và `StackOverflowError` hay không. Đáp án lần lượt: **có**, **có**, **không**; sau đó chứng minh bằng cây kế thừa, không đoán theo tên.

### Bài 4 — Lỗi API và binary

Một ứng dụng compile theo library có `Client.send(String)` nhưng runtime chạy bản JAR thiếu method này. Exception `NoSuchMethodError` thuộc nhóm nào? Nó có thể được khắc phục bằng cách thêm `throws IOException` vào method gọi không? Hướng sửa nên tập trung vào **phiên bản JAR/ABI được nạp ở runtime**.

### Bài 5 — Async và timeout

Một task ném `IOException`, caller A lấy qua `Future.get()`, caller B lấy qua `CompletableFuture.join()`, caller C gọi `Future.get(timeout)` rồi hết hạn. Hãy nêu wrapper hoặc lỗi quan sát thích hợp và giải thích tại sao **hết hạn chờ** không chứng minh task đã dừng.

### Bài 6 — Checked exception trong lambda

Thử dùng `Files.readString(path)` trong `Stream.map`. Compiler báo điều gì? Hãy thiết kế một cách giữ `cause` bằng `UncheckedIOException` hoặc trả typed Result, và test rằng lỗi đọc không bị chuyển thành chuỗi rỗng giả.

### Bài 7 — Không đánh đồng lỗi với kết quả nghiệp vụ

Một method tìm user trả `Optional.empty()` khi không tồn tại. Nếu database ném checked `SQLException`, có nên catch và trả Optional rỗng không? Trả lời dựa vào hợp đồng absence vs failure, nguy cơ negative caching và hành vi caller.

## 25. Tóm tắt

**Bốn sự thật cần nhớ:**

1. `Throwable` là gốc của nhánh những object được phép ném trong Java. `Exception` và `Error` là hai nhánh riêng.
2. **Checked** = subtype của Throwable **không thuộc** nhánh RuntimeException hoặc Error; compiler thường bắt buộc bắt/khai báo khi có thể lan ra.
3. **Unchecked** = RuntimeException, Error và mọi subclass của chúng. `RuntimeException` **đồng thời là Exception**; `Error` **không phải Exception**.
4. Compiler kiểm tra kiểu, **không chứng minh** chương trình đã phục hồi đúng, không rollback tự động và không bảo đảm lỗi nghiêm trọng có thể tiếp tục chạy an toàn.

Khi thiết kế API, hãy dùng bảng phân loại để hiểu nghĩa vụ compiler; sau đó quyết định `catch`, `throws`, wrapper, typed result và monitoring theo **hợp đồng nghiệp vụ và trạng thái thực sự của công việc**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-reference-003">Bảng kết quả của try, catch, finally và try-with-resources</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng kết quả của try, catch, finally và try-with-resources

Khi đọc hoặc viết mã Java xử lý ngoại lệ, câu hỏi quan trọng không chỉ là “exception nào được ném?”, mà còn là **“sau toàn bộ cấu trúc `try`/`catch`/`finally`, phương thức thực sự trả về gì hoặc để exception nào thoát ra?”**. Thứ tự thực thi có thể khác trực giác: `finally` chạy sau đường `return` đã được chuẩn bị; nếu `finally` tự ném hoặc `return` thì kết quả đang chờ của `try` hoặc `catch` có thể bị thay thế. Với `try-with-resources`, Java đóng tài nguyên trước khi thực thi `catch`/`finally` gắn với cấu trúc đó, và lỗi khi đóng thường được giữ là *suppressed exceptions* thay vì che exception chính.

Bài tra cứu này trình bày **bảng kết quả** cho các tổ hợp phổ biến trong Java 17+, thứ tự chạy, ngoại lệ ưu tiên, giá trị trả về và cách xác minh bằng chương trình độc lập. Các ví dụ có stdout giải thích được; quy tắc nào phụ thuộc giả định sẽ được nói rõ.

## 1. Những thuật ngữ cần thống nhất

Một statement Java có thể **hoàn thành bình thường** (*normal completion*) hoặc **hoàn thành bất thường** (*abrupt completion*). Các lý do hoàn thành bất thường quan trọng với bài này gồm:

- `return` — yêu cầu trả kết quả khỏi method.
- `throw` — ném một `Throwable`.
- `break` hoặc `continue` — chuyển điều khiển trong vòng lặp/switch phù hợp.
- Exception phát sinh trong biểu thức, ví dụ `parseInt`, gọi hàm hoặc `close()`.

Để đọc bảng, giả định các phương thức có những đoạn mã đại diện:

```java
try {
    work();
} catch (IOException ex) {
    recover(ex);
} finally {
    cleanup();
}
```

Đây là **mã minh họa theo ngữ cảnh**, với `work`, `recover` và `cleanup` do ứng dụng định nghĩa. Việc bắt được `IOException` hay không phụ thuộc loại exception thực tế; `catch` chỉ bắt exception từ vùng `try` tương ứng và phù hợp với kiểu bắt.

Một từ khóa quan trọng là **kết quả đang chờ** (*pending completion*): khi `try` hoặc `catch` đã chuẩn bị `return`/`throw` nhưng chưa rời cấu trúc vì còn `finally`, kết quả đó vẫn có thể bị thay bởi completion của `finally`.

## 2. Bảng kết quả cơ bản của try/catch/finally

Đọc bảng với các quy ước:

- **T**: giá trị được chuẩn bị để `return` trong `try`.
- **C**: giá trị được chuẩn bị để `return` trong `catch`.
- **F**: giá trị được `return` trong `finally`.
- **E1**: exception ném từ `try`; **E2**: exception ném từ `catch`; **E3**: exception ném từ `finally`.
- “`finally` hoàn tất bình thường” nghĩa là bản thân `finally` không `throw`/`return`/`break`/`continue` làm thay đổi hướng thoát.
- Bảng giả định không có lỗi khác phát sinh từ xử lý class loading, VM termination hoặc hệ thống bên ngoài.

| Kết quả trong `try` | `catch` | Kết quả trong `finally` | Kết quả quan sát bên ngoài |
| --- | --- | --- | --- |
| Hoàn tất bình thường | Không chạy | Bình thường | Thực thi tiếp sau statement. |
| `return T` | Không chạy | Bình thường | Method trả **T**. |
| Ném **E1**, có catch phù hợp | Catch hoàn tất bình thường | Bình thường | Thực thi tiếp sau statement. |
| Ném **E1**, có catch phù hợp | `return C` | Bình thường | Method trả **C**. |
| Ném **E1**, có catch phù hợp | Ném **E2** | Bình thường | **E2** thoát ra. |
| Ném **E1**, không có catch phù hợp | Không chạy | Bình thường | **E1** thoát ra. |
| Hoàn tất bình thường | Không chạy | `return F` | Method trả **F**. |
| `return T` | Không chạy | `return F` | Method trả **F**; T bị thay. |
| `return T` | Không chạy | Ném **E3** | **E3** thoát; giá trị T không được trả. |
| Ném **E1** | Có hoặc không có catch | Ném **E3** | **E3** thắng completion đang chờ, trừ khi một cơ chế bên trong đã tự quản lý lỗi riêng. |
| Ném **E1**, catch trả C | `return C` | `return F` | Trả **F**; C bị thay. |
| Ném **E1**, catch ném E2 | Ném **E2** | Ném **E3** | **E3** thoát; E2 bị thay trong luồng exception tự nhiên. |

**Quy tắc trọng tâm:** nếu `finally` **hoàn tất bình thường**, kết quả đang chờ của `try` hoặc `catch` được giữ nguyên. Nếu `finally` **hoàn tất bất thường**, kết quả của `finally` quyết định đường thoát, còn completion trước đó có thể mất khỏi đường truyền.

Điều đó không có nghĩa JVM tự thêm exception bị che thành suppressed. **Thông thường `finally` ném lỗi mới sẽ che lỗi cũ mà không tự động liên kết chúng.** Cơ chế suppressed tự động là đặc điểm quan trọng của `try-with-resources` (và chỉ trong các trường hợp được đặc tả), không phải mọi khối `finally`.

## 3. Thứ tự thực thi: finally chạy trước khi method rời khỏi

Xét:

```java
static int example() {
    try {
        System.out.println("inside try");
        return 10;
    } finally {
        System.out.println("inside finally");
    }
}
```

Đây là phương thức Java hợp lệ khi đặt trong class. Nếu gọi và in kết quả, thứ tự là:

```text
inside try
inside finally
```

Sau hai dòng trên, caller mới nhận giá trị `10`. `finally` không chạy “sau khi caller đã thấy return” mà chạy **trước khi method hoàn thành**.

Một điều tương tự xảy ra khi `catch` chuẩn bị `return`:

```java
static int example() {
    try {
        throw new IllegalArgumentException("invalid");
    } catch (IllegalArgumentException ex) {
        return -1;
    } finally {
        System.out.println("cleanup");
    }
}
```

Khi caller nhận `-1`, dòng `cleanup` đã được in. Nếu `finally` ném exception khác, caller không nhận `-1`.

## 4. Ví dụ Java 17 hoàn chỉnh: các đường kết quả của try/catch/finally

Tạo file `TryCatchFinallyOutcomesDemo.java`:

```java
public class TryCatchFinallyOutcomesDemo {

    private static String normalReturn() {
        try {
            System.out.println("normal: try");
            return "T";
        } finally {
            System.out.println("normal: finally");
        }
    }

    private static String caughtReturn() {
        try {
            throw new IllegalArgumentException("bad input");
        } catch (IllegalArgumentException ex) {
            System.out.println("caught: catch");
            return "C";
        } finally {
            System.out.println("caught: finally");
        }
    }

    private static void uncaughtThrow() {
        try {
            throw new IllegalStateException("original");
        } catch (IllegalArgumentException ex) {
            System.out.println("unreachable catch");
        } finally {
            System.out.println("uncaught: finally");
        }
    }

    private static void catchThrows() {
        try {
            throw new IllegalArgumentException("input");
        } catch (IllegalArgumentException ex) {
            System.out.println("catchThrows: catch");
            throw new IllegalStateException("from catch", ex);
        } finally {
            System.out.println("catchThrows: finally");
        }
    }

    private static String finallyReturns() {
        try {
            System.out.println("finallyReturns: try");
            return "T";
        } finally {
            System.out.println("finallyReturns: finally");
            return "F"; // Cố ý minh họa anti-pattern.
        }
    }

    private static void finallyThrows() {
        try {
            throw new IllegalArgumentException("original");
        } finally {
            throw new IllegalStateException("finally wins");
        }
    }

    public static void main(String[] args) {
        System.out.println("normal result: " + normalReturn());
        System.out.println("caught result: " + caughtReturn());

        try {
            uncaughtThrow();
        } catch (IllegalStateException ex) {
            System.out.println("outer caught: " + ex.getMessage());
        }

        try {
            catchThrows();
        } catch (IllegalStateException ex) {
            System.out.println("outer catchThrows: " + ex.getMessage());
            System.out.println(
                    "cause: " + ex.getCause().getClass().getSimpleName());
        }

        System.out.println("finallyReturns result: " + finallyReturns());

        try {
            finallyThrows();
        } catch (IllegalStateException ex) {
            System.out.println("outer finallyThrows: " + ex.getMessage());
            System.out.println(
                    "suppressed: " + ex.getSuppressed().length);
        }
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 -encoding UTF-8 TryCatchFinallyOutcomesDemo.java
java TryCatchFinallyOutcomesDemo
```

Stdout:

```text
normal: try
normal: finally
normal result: T
caught: catch
caught: finally
caught result: C
uncaught: finally
outer caught: original
catchThrows: catch
catchThrows: finally
outer catchThrows: from catch
cause: IllegalArgumentException
finallyReturns: try
finallyReturns: finally
finallyReturns result: F
outer finallyThrows: finally wins
suppressed: 0
```

**Đọc kết quả:**

1. `normalReturn()` chuẩn bị trả T; `finally` chạy rồi T mới tới caller.
2. `caughtReturn()`: exception trong `try` được `catch` xử lý, catch chuẩn bị trả C; `finally` vẫn chạy và C được giữ.
3. `uncaughtThrow()`: catch không phù hợp với `IllegalStateException`, nhưng `finally` vẫn chạy. `IllegalStateException("original")` đi ra đến catch bên ngoài.
4. `catchThrows()`: exception mới được ném **trong catch**. Catch cùng cấp không bắt lại exception do chính nó ném; `finally` chạy và exception mới được catch bên ngoài. Cause đã được code gắn tường minh.
5. `finallyReturns()` cố ý dùng `return` trong `finally` để minh họa việc trả F thay vì T. Đây là cách viết **không nên dùng trong production** vì làm mất hướng thoát đang chờ.
6. `finallyThrows()`: lỗi `IllegalStateException("finally wins")` che `IllegalArgumentException("original")` do `try` ném. `suppressed: 0` chứng minh **finally thông thường không tự thêm lỗi cũ vào suppressed**.

Chương trình cố ý minh họa hai cách viết nguy hiểm (`return`/`throw` trong `finally`). Không sao chép chúng vào cleanup production trừ khi muốn thay thế outcome một cách có chủ đích và đã xác định hậu quả.

## 5. Bảng riêng: return, throw, break và continue với finally

Ngoài `return` và `throw`, `finally` còn có thể tác động đến `break`/`continue` trong vòng lặp. Cơ chế chung vẫn là: hoàn thành bình thường giữ hướng thoát cũ; hoàn thành bất thường mới có thể thay hướng cũ.

| Điều đang chờ | `finally` hoàn tất bình thường | `finally` thay hướng điều khiển |
| --- | --- | --- |
| `return value` | Giữ giá trị đã đánh giá trước đó | `return` mới hoặc `throw` mới thay thế. |
| `throw E` | Giữ E | `return`/`throw` mới che E. |
| `break` | Tiếp tục break tới đích | `return`/`throw`/`continue` hợp lệ trong ngữ cảnh có thể thay. |
| `continue` | Tiếp tục vòng lặp theo đích continue | `return`/`throw`/`break` hợp lệ có thể thay. |
| Hoàn tất bình thường | Chạy statement tiếp theo | Hướng chuyển mới do finally quyết định. |

**Không đặt `return`, `break` hoặc `continue` trong `finally` để “xử lý cho gọn”.** Các câu lệnh đó có thể vô tình nuốt exception hoặc phá logic vòng lặp.

Ví dụ phản mẫu:

```java
for (int i = 0; i < 3; i++) {
    try {
        process(i);   // Có thể throw.
    } finally {
        continue;     // Có thể che throw đang chờ.
    }
}
```

Đây là snippet minh họa, `process` chưa định nghĩa. `continue` bên trong finally cố ý làm thay đổi hướng thoát của vòng lặp; nhiều bộ phân tích mã nguồn/linter cảnh báo kiểu này. Chỉ dùng `finally` để cleanup và để nó kết thúc bình thường khi có thể.

## 6. Giá trị return được đánh giá trước khi finally thực thi

Khi gặp `return expression`, Java tính **giá trị của expression** trước. Nếu sau đó `finally` hoàn tất bình thường, giá trị đã tính được trả cho caller. Thay đổi một biến local trong `finally` không tự tính lại expression.

Ví dụ:

```java
static int value() {
    int x = 5;
    try {
        return x;
    } finally {
        x = 99;
    }
}
```

Method trả **5**, không phải 99. Tuy nhiên, nếu expression trả **reference tới object mutable**, `finally` có thể sửa object đó và caller quan sát dữ liệu đã sửa, vì reference trả về vẫn trỏ cùng object.

Cần phân biệt:

- **Giá trị primitive hoặc reference được chọn khi đánh giá return.**
- **Nội dung của object mutable được reference ấy trỏ tới có thể thay đổi sau thời điểm đánh giá.**

`return` cũng không loại bỏ lỗi mới: nếu `finally` ném exception, giá trị đã chuẩn bị sẽ không đến caller.

## 7. Ví dụ Java 17 hoàn chỉnh: primitive và mutable object

Tạo file `ReturnAndFinallyValueDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class ReturnAndFinallyValueDemo {

    private static int primitiveValue() {
        int number = 5;
        try {
            return number;
        } finally {
            number = 99;
            System.out.println("primitive finally: " + number);
        }
    }

    private static List<String> mutableResult() {
        List<String> result = new ArrayList<>();
        result.add("try");
        try {
            return result;
        } finally {
            result.add("finally");
        }
    }

    private static int returnInterruptedByThrow() {
        try {
            return 42;
        } finally {
            throw new IllegalStateException("cleanup failed");
        }
    }

    public static void main(String[] args) {
        System.out.println("primitive returned: " + primitiveValue());
        System.out.println("mutable returned: " + mutableResult());

        try {
            System.out.println(returnInterruptedByThrow());
        } catch (IllegalStateException ex) {
            System.out.println("no returned value: " + ex.getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 -encoding UTF-8 ReturnAndFinallyValueDemo.java
java ReturnAndFinallyValueDemo
```

Stdout:

```text
primitive finally: 99
primitive returned: 5
mutable returned: [try, finally]
no returned value: cleanup failed
```

Ở dòng cuối, lời gọi `System.out.println(returnInterruptedByThrow())` **không in 42**, vì biểu thức argument không trả về bình thường: `finally` đã ném lỗi. `println` bên ngoài chỉ được gọi nếu việc đánh giá argument hoàn thành bình thường.

Với `mutableResult`, reference được chọn để return trước finally; sau đó finally sửa chính list ấy, vì vậy caller nhìn thấy cả hai phần tử. Điều này có thể phá hợp đồng nếu API lộ collection mutable nội bộ. Trả immutable snapshot khi caller không được phép quan sát mutation sau return.

## 8. Một finally không phải lúc nào cũng chắc chắn chạy

Trong vận hành Java bình thường, `finally` được thực hiện khi code rời `try`/`catch` theo các đường `return`, `throw`, `break`, `continue`. Nhưng **không nên nói finally chạy trong mọi hoàn cảnh tuyệt đối**:

- `System.exit` có thể kết thúc JVM trước khi stack hiện tại đi qua finally.
- `Runtime.halt` có thể dừng JVM ngay.
- JVM crash, tiến trình bị OS kill hoặc mất điện không bảo đảm chạy cleanup.
- Một thread bị kẹt vô hạn trong `try` không rời scope nên finally chưa chạy.
- `Thread.stop` cũ bị xem là không an toàn; không dùng để thiết kế cleanup tin cậy.

Vì vậy, finally là cơ chế cleanup rất quan trọng **trong luồng điều khiển ngôn ngữ**, nhưng không thay thế cơ chế phục hồi sau crash, transaction, lease expiration hoặc ghi bền vững.

## 9. try-with-resources: khác gì finally tự viết?

`try-with-resources` (TWR) được giới thiệu để đóng tài nguyên có kiểu `AutoCloseable` một cách có cấu trúc:

```java
try (InputStream in = Files.newInputStream(path)) {
    parse(in);
}
```

Đây là snippet minh họa; cần import `InputStream`, `Files` và định nghĩa `path`/`parse` theo ứng dụng.

Các quy tắc quan trọng:

1. Tài nguyên được **khởi tạo từ trái sang phải** trong khai báo.
2. Tài nguyên khởi tạo thành công được **đóng theo thứ tự ngược** khi rời phần thân.
3. Đóng tài nguyên diễn ra trên cả đường hoàn tất bình thường, `return` và exception (trong phạm vi các đường điều khiển Java bình thường).
4. Tài nguyên đã khởi tạo vẫn được đóng nếu quá trình tạo tài nguyên tiếp theo thất bại.
5. Exception từ body có ưu tiên làm **primary**; lỗi close phù hợp được thêm vào **suppressed** của exception chính.
6. Nếu body hoàn tất bình thường và close đầu tiên theo thứ tự đóng ném lỗi, lỗi close có thể là **primary**; các lỗi close sau đó được thêm vào suppressed.
7. `catch`/`finally` gắn với **toàn bộ TWR statement** thực thi sau khi các tài nguyên của statement đã được đóng theo quy tắc. Điều này khác với đặt một `catch` bên trong body.

Không tự hiểu TWR là “finally cũng tốt như nhau”: với finally viết thủ công, lỗi close mới có thể che lỗi body trừ khi code tự gắn suppressed. TWR giúp bảo toàn cả hai nguyên nhân.

## 10. Bảng tra cứu kết quả try-with-resources

Quy ước:

- **Ebody**: Throwable ném từ body.
- **Einit**: Throwable ném khi khởi tạo một resource.
- **Eclose-B**, **Eclose-A**: lỗi khi đóng B và A, với resource khai báo A rồi B nên **đóng B trước A**.
- “Suppressed” nghĩa là có thể truy cập qua `primary.getSuppressed()`; thông thường mỗi lỗi close được đưa vào mảng theo thứ tự xảy ra.
- Bảng giả định object exception thông thường có suppression enabled, các lỗi được ném là object khác nhau và không có xử lý tùy biến can thiệp.

| Kết quả body/khởi tạo | Đóng resource | Kết quả TWR |
| --- | --- | --- |
| Body bình thường | Tất cả close thành công | TWR bình thường, thực thi tiếp. |
| Body `return V` | Tất cả close thành công | Method trả V sau khi close. |
| Body ném **Ebody** | Tất cả close thành công | Ebody thoát; không có lỗi close. |
| Body ném **Ebody** | B close ném **Eclose-B** | Ebody vẫn primary; Eclose-B suppressed. |
| Body ném **Ebody** | B và A đều close ném | Ebody primary; lỗi B rồi A suppressed. |
| Body bình thường | B close ném Eclose-B, A đóng bình thường | Eclose-B thoát. |
| Body bình thường | B và A đều close ném | Eclose-B primary; Eclose-A suppressed của Eclose-B. |
| Body `return V` | Một close ném Eclose | Eclose thoát; V **không được trả**. |
| Khởi tạo A thành công; khởi tạo B ném Einit | A close bình thường | Einit thoát; body không chạy. |
| Khởi tạo A thành công; B ném Einit | A close cũng ném Eclose-A | Einit primary; Eclose-A suppressed. |
| Resource duy nhất khởi tạo thất bại | Không có resource ấy để close | Exception khởi tạo thoát; body không chạy. |

### Tại sao lỗi close không che Ebody?

TWR được đặc tả để bảo tồn lỗi công việc chính. Nếu body ném `IOException("read failed")` và `close()` cũng ném `IOException("close failed")`, exception ra ngoài là **"read failed"**; còn "close failed" nằm trong `getSuppressed()`.

Trái lại:

```java
Resource resource = open();
try {
    work(resource);   // Ném Ebody.
} finally {
    resource.close(); // Ném Eclose.
}
```

Đây là snippet minh họa; với cách viết này nếu `close()` ném, Eclose có thể thay Ebody. Muốn bảo toàn như TWR phải viết thêm code quản lý suppressed đúng cách. Vì vậy với tài nguyên sở hữu theo scope, **ưu tiên try-with-resources**.

## 11. Ví dụ Java 17 hoàn chỉnh: body lỗi và nhiều close lỗi

Tạo file `TryWithResourcesOutcomesDemo.java`:

```java
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TryWithResourcesOutcomesDemo {

    private static final class Resource implements AutoCloseable {
        private final String name;
        private final boolean failOnClose;
        private final List<String> events;

        Resource(String name, boolean failOnClose, List<String> events) {
            this.name = name;
            this.failOnClose = failOnClose;
            this.events = events;
            events.add("open " + name);
        }

        @Override
        public void close() throws IOException {
            events.add("close " + name);
            if (failOnClose) {
                throw new IOException("close " + name + " failed");
            }
        }
    }

    private static void printFailure(Throwable primary) {
        System.out.println("primary: " + primary.getMessage());
        System.out.println("suppressed count: "
                + primary.getSuppressed().length);
        for (Throwable s : primary.getSuppressed()) {
            System.out.println("suppressed: " + s.getMessage());
        }
    }

    private static void bodyFails() {
        List<String> events = new ArrayList<>();
        try (Resource a = new Resource("A", true, events);
             Resource b = new Resource("B", true, events)) {
            events.add("body failed");
            throw new IllegalStateException("body error");
        } catch (IllegalStateException ex) {
            events.forEach(System.out::println);
            printFailure(ex);
        } catch (IOException ex) {
            throw new AssertionError("unexpected close-only", ex);
        }
    }

    private static void closeFails() {
        List<String> events = new ArrayList<>();
        try (Resource a = new Resource("A", true, events);
             Resource b = new Resource("B", true, events)) {
            events.add("body OK");
        } catch (IOException ex) {
            events.forEach(System.out::println);
            printFailure(ex);
        }
    }

    private static String returnButCloseFails() throws IOException {
        List<String> events = new ArrayList<>();
        try (Resource x = new Resource("X", true, events)) {
            System.out.println("body returns");
            return "VALUE";
        }
    }

    private static Resource openSecond(List<String> events)
            throws IOException {
        events.add("open B failed");
        throw new IOException("init B failed");
    }

    private static void initFails() {
        List<String> events = new ArrayList<>();
        try (Resource a = new Resource("A", true, events);
             Resource b = openSecond(events)) {
            events.add("body unreachable");
        } catch (IOException ex) {
            events.forEach(System.out::println);
            printFailure(ex);
        }
    }

    public static void main(String[] args) {
        System.out.println("CASE: BODY FAILURE");
        bodyFails();

        System.out.println("CASE: CLOSE FAILURE");
        closeFails();

        System.out.println("CASE: RETURN/CLOSE");
        try {
            System.out.println(returnButCloseFails());
        } catch (IOException ex) {
            printFailure(ex);
        }

        System.out.println("CASE: INIT FAILURE");
        initFails();
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 -encoding UTF-8 TryWithResourcesOutcomesDemo.java
java TryWithResourcesOutcomesDemo
```

Stdout:

```text
CASE: BODY FAILURE
open A
open B
body failed
close B
close A
primary: body error
suppressed count: 2
suppressed: close B failed
suppressed: close A failed
CASE: CLOSE FAILURE
open A
open B
body OK
close B
close A
primary: close B failed
suppressed count: 1
suppressed: close A failed
CASE: RETURN/CLOSE
body returns
primary: close X failed
suppressed count: 0
CASE: INIT FAILURE
open A
open B failed
close A
primary: init B failed
suppressed count: 1
suppressed: close A failed
```

Giải thích kết quả:

- **BODY FAILURE:** Ebody là `IllegalStateException("body error")`. Đóng B và A đều ném `IOException`, nhưng body error vẫn primary và hai lỗi đóng được suppressed theo thứ tự B rồi A.
- **CLOSE FAILURE:** body thành công; B là resource đóng đầu tiên và thất bại, nên lỗi B là primary. A cũng đóng dù B đã lỗi; lỗi A là suppressed.
- **RETURN/CLOSE:** body đã chuẩn bị trả `"VALUE"` nhưng `close X` ném, nên caller nhận `IOException` và **không in `VALUE`**. `Resource X` ghi event vào danh sách riêng mà demo không in; vì thế stdout của case này bắt đầu trực tiếp ở “body returns”.
- **INIT FAILURE:** A đã tạo, mở B thất bại. Body không chạy. Java đóng A; vì A cũng ném, lỗi đóng A được suppressed vào lỗi khởi tạo B.

Các `Resource` trong demo chỉ ghi event và ném exception có kiểm soát, nên thứ tự output không phụ thuộc vào luồng bất đồng bộ. Trong production, `close()` có thể đóng một phần rồi lỗi; việc “đã gọi close” **không đồng nghĩa cleanup chắc chắn thành công**.

## 12. Khi TWR có catch và finally bên ngoài thì thứ tự là gì?

Xét:

```java
try (Resource r = openResource()) {
    System.out.println("body");
} catch (IOException ex) {
    System.out.println("catch");
} finally {
    System.out.println("finally");
}
```

Đây là snippet minh họa, `Resource`/`openResource` do ứng dụng định nghĩa. Trong hình thức này, resource được đóng **trước khi vào catch hoặc finally của TWR statement**. Nếu `close` ném `IOException` và body không có lỗi, catch có thể bắt chính exception do close tạo ra.

Trình tự:

```text
khởi tạo resource
body
close resource
catch phù hợp (nếu có exception)
finally
tiếp tục hoặc truyền exception ra ngoài
```

Nếu body bình thường và close bình thường thì `catch` không chạy, chỉ có `finally`. Nếu body ném Ebody và close ném Eclose, catch phù hợp với **Ebody** có thể đọc `Ebody.getSuppressed()` để biết Eclose.

**Phân biệt catch bên trong body:**

```java
try (Resource r = openResource()) {
    try {
        work(r);
    } catch (IOException ex) {
        recover(ex);
    }
}
```

Đây là snippet minh họa. Catch **bên trong body** chỉ bắt lỗi phát sinh trong vùng `try` nội bộ; nó **không bắt lỗi từ `r.close()`** xảy ra sau khi body hoàn tất. Nếu muốn bắt close exception, cần catch ngoài TWR hoặc try bao ngoài thích hợp.

## 13. finally sau TWR vẫn có thể thay exception chính

Nếu TWR tạo exception đã có suppressed, và `finally` gắn ngoài TWR tiếp tục ném một exception khác, thì **exception finally có thể thay exception từ TWR**, làm caller không còn trực tiếp quan sát Ebody cùng suppressed.

Ví dụ phản mẫu:

```java
try (Resource r = openResource()) {
    throw new IOException("body");
} finally {
    throw new IllegalStateException("finally failed");
}
```

Đây là snippet minh họa; các type/import cần có trong project và method phải xử lý checked exception theo contract. Kết quả thoát tự nhiên là `IllegalStateException("finally failed")` chứ không phải `IOException("body")`. Vì vậy **TWR giúp giữ lỗi đóng tài nguyên, nhưng không bảo vệ khỏi một finally ngoài có completion bất thường**.

Trong production, không gọi code cleanup dễ ném mà không xử lý trong `finally` theo cách vô tình che lỗi gốc. Ưu tiên scope tài nguyên rõ và cơ chế ghi nhận/ghép lỗi có kiểm soát.

## 14. Phân biệt cause và suppressed exception

Hai cơ chế phục vụ mục đích khác nhau:

| Cơ chế | API | Ý nghĩa |
| --- | --- | --- |
| **Cause** | `Throwable.getCause()`, constructor nhận cause hoặc `initCause` theo contract | Exception này được tạo để bao/diễn giải exception nguyên nhân ở tầng thấp hơn. |
| **Suppressed** | `Throwable.getSuppressed()`, `addSuppressed` | Những exception phụ bị giữ lại vì một exception khác có ưu tiên, điển hình lỗi close trong TWR. |

Ví dụ:

```java
IOException original = new IOException("read failed");
IllegalStateException wrapper =
        new IllegalStateException("service failed", original);

wrapper.addSuppressed(new IOException("cleanup failed"));
```

Đoạn trên là snippet Java hợp lệ khi đặt trong một method với import `IOException`. `wrapper.getCause()` là `original`; `wrapper.getSuppressed()[0]` là lỗi cleanup. Chúng không phải cùng một chuỗi liên kết.

Trong một exception thực tế, cả cause và suppressed đều có thể tồn tại. Stack trace khi in đầy đủ thường biểu diễn cả hai, nhưng không nên kiểm tra test chỉ bằng việc tìm chuỗi trong log. Dùng chính các API `getCause()` và `getSuppressed()` để kiểm tra cấu trúc lỗi.

### Một số giới hạn của suppressed

- `addSuppressed` không cho phép suppress **chính object Throwable đang nhận lời gọi**; hành vi đó có thể ném `IllegalArgumentException`.
- Suppression có thể bị tắt bằng constructor đặc biệt của `Throwable`; khi đó việc thêm suppressed có thể bị bỏ qua theo contract.
- `getSuppressed()` trả một mảng; đừng mặc định mọi exception bất kỳ đều có suppressed.
- TWR có quy tắc tự động xử lý những exception đóng tài nguyên trong trường hợp thích hợp; `finally` thông thường không có bảo đảm tương đương.

Một số hệ thống tạo lỗi domain với suppression disabled hoặc sử dụng exception đặc biệt. Nếu cần log tất cả cleanup failures, phải hiểu contract của Throwable và tài nguyên đang dùng.

## 15. Nhiều resource: mở trái sang phải, đóng phải sang trái

Ví dụ:

```java
try (A a = new A();
     B b = new B(a);
     C c = new C(b)) {
    doWork(a, b, c);
}
```

Đây là snippet minh họa, các type và hàm được giả định. Thứ tự bình thường:

```text
open A
open B
open C
body
close C
close B
close A
```

Nếu B khởi tạo thất bại, A đã có phải được đóng; C **chưa được khởi tạo**, không có C để đóng. Nếu C khởi tạo thất bại, B rồi A được đóng.

Đóng theo thứ tự ngược giúp phản ánh dependency: resource tạo sau thường phụ thuộc resource tạo trước. Tuy nhiên, không phải mọi resource đều có thể đóng an toàn theo quy tắc đó nếu contract đặc biệt; khi cần chủ động điều phối cleanup, phải thiết kế scopes/thứ tự tương ứng.

### AutoCloseable và Closeable

`AutoCloseable.close()` có thể khai báo `throws Exception`. `java.io.Closeable.close()` khai báo `throws IOException` và có hợp đồng chi tiết riêng. Khi định nghĩa class implement `AutoCloseable`, có thể override `close()` với kiểu exception hẹp hơn, kể cả không khai báo checked exception.

Ví dụ:

```java
final class InMemoryHandle implements AutoCloseable {
    @Override
    public void close() {
        // Cleanup không ném checked exception.
    }
}
```

Đây là đoạn khai báo class độc lập hợp lệ về cú pháp. Kiểu được khai báo của resource và `close` ảnh hưởng tới các checked exceptions mà compiler yêu cầu caller xử lý hoặc khai báo.

## 16. Return từ body TWR không bỏ qua close

Một hiểu lầm phổ biến:

```java
try (Resource r = acquire()) {
    return calculate(r);
}
```

Đây là snippet minh họa. Java **tính `calculate(r)` trước**, chuẩn bị giá trị return, sau đó đóng r. Nếu đóng thành công thì trả giá trị. Nếu `close()` ném, lỗi close có thể thay completion `return`.

Do đó khi API đọc file:

```java
try (InputStream input = Files.newInputStream(path)) {
    return parse(input);
}
```

return chỉ thành công nếu `parse` hoàn thành và quá trình đóng resource không phát sinh exception vượt ra ngoài theo contract. Điều này thường mong muốn: ví dụ output stream có thể cần flush/close và lỗi đóng là tín hiệu dữ liệu chưa được lưu đầy đủ.

Không tự đặt `return` trong finally để “giữ kết quả bằng mọi giá” vì như vậy có thể nuốt mất lỗi I/O quan trọng.

## 17. Nếu catch xử lý lỗi body rồi close lại lỗi?

Xét:

```java
try (Resource r = acquire()) {
    try {
        work(r);
    } catch (IOException ex) {
        recover(ex);
    }
}
```

Nếu `work` ném `IOException` nhưng `recover` xử lý và **body hoàn tất bình thường**, resource được đóng sau đó. Nếu close ném, exception close trở thành lỗi quan sát bên ngoài TWR.

Ngược lại, nếu `recover` **ném exception mới**, đó là lỗi của body TWR và close exception sẽ có thể bị suppressed vào nó. Điều này cho thấy bảng chỉ có thể trả lời đúng khi biết **completion cuối cùng của body**, không chỉ exception ban đầu ở `work`.

Ví dụ:

| Hành vi trong body | Hành vi close | Kết quả |
| --- | --- | --- |
| `work` lỗi, catch nội bộ xử lý rồi body bình thường | Close bình thường | TWR bình thường. |
| `work` lỗi, catch nội bộ xử lý rồi body bình thường | Close lỗi Eclose | Eclose là primary. |
| `work` lỗi, catch nội bộ ném Enew | Close bình thường | Enew thoát. |
| `work` lỗi, catch nội bộ ném Enew | Close lỗi Eclose | Enew primary; Eclose suppressed. |

Đừng chỉ đếm “có exception được ném ở đâu đó” để dự đoán kết quả cuối; phải theo dõi **nó có được bắt/phục hồi trước khi rời scope không**.

## 18. Exception trong biểu thức khởi tạo resource

TWR có thể thất bại **trước khi phần body được thực thi**:

```java
try (Resource a = openA();
     Resource b = openB()) {
    work();
}
```

Nếu `openB()` ném exception:

- A đã mở sẽ được đóng theo quy tắc TWR.
- B không được tạo thành công thì không có object B để đóng.
- `work()` không chạy.
- Nếu đóng A cũng ném, exception do `openB` tạo là primary, lỗi đóng A là suppressed.
- Catch ngoài TWR có thể xử lý exception khởi tạo và xem suppressed.

Nếu chính `openB` có side effect **trước khi ném nhưng chưa trả về resource object**, TWR ở caller không thể tự đóng thứ đã mở bên trong openB mà chưa trả handle. **Hàm factory phải tự cleanup tài nguyên nội bộ đã acquire thành công trước khi thất bại.**

## 19. Resource null và biến resource đã có

Trong `try-with-resources`, biến resource có thể chứa `null`; Java bỏ qua lời gọi `close` tương ứng khi reference null, thay vì tự tạo `NullPointerException` vì close. Nhưng dùng resource null thường khiến logic và ownership khó đọc; nên lựa chọn rõ hợp đồng.

Từ Java 9, TWR có thể sử dụng biến final hoặc **effectively final** đã được khai báo bên ngoài:

```java
InputStream stream = Files.newInputStream(path);
try (stream) {
    consume(stream);
}
```

Đây là snippet minh họa; cần import các lớp và khai báo `path`, `consume` thích hợp. Java sẽ đóng resource được tham chiếu khi rời scope. Dù biến khai báo ở ngoài, **lệnh `try (stream)` vẫn có nghĩa TWR sở hữu trách nhiệm gọi close**; không áp dụng bừa cho shared resource do caller khác quản lý.

Nếu caller chỉ cho mượn một stream, đừng đưa nó vào TWR trừ khi hợp đồng cho phép API đó đóng stream. Nếu cần dùng tài nguyên xuyên nhiều bước có ownership rộng hơn, scope đóng phải thuộc owner thực sự.

## 20. IOException hay Error trong close có được suppressed không?

TWR làm việc theo cơ chế `Throwable`, không chỉ IOException. Một `close()` có thể ném unchecked exception hoặc một `Error`; nếu body đã hoàn thành bằng một Throwable khác, lỗi close phù hợp có thể được ghi vào suppressed của lỗi chính.

Tuy nhiên, **không nên dùng cơ chế này để nuốt Error** hoặc đảm bảo có thể phục hồi sau `OutOfMemoryError`/lỗi VM nghiêm trọng. Những lỗi như vậy có thể khiến runtime không còn đủ tài nguyên để vận hành đúng. Tránh tự viết `catch(Throwable)` để chuyển tất cả thành kết quả thành công.

Với tài nguyên ngoài JVM, việc close ném Error cũng không nói rằng dữ liệu đã rollback. Mục tiêu của TWR là quản lý lời gọi đóng và ưu tiên exception **trong luồng điều khiển Java**, không phải transaction phân tán.

## 21. finally và cleanup khi return hoặc throw: tránh thay kết quả

Một khối cleanup tốt có xu hướng:

```java
Resource r = acquire();
try {
    work(r);
} finally {
    r.close();
}
```

Đây là cấu trúc minh họa. Nó giải phóng tài nguyên trên nhiều đường rời scope, nhưng vẫn có nguy cơ lỗi close che lỗi work như đã giải thích. Khi resource là `AutoCloseable`, ưu tiên TWR.

Nếu buộc phải tự quản lý nhiều lỗi cleanup, thiết kế rõ exception primary và suppressed, hoặc tạo một outcome có cấu trúc. Không được tự viết logic “nếu có lỗi thì `return null` trong finally” vì nó xóa đường thất bại.

**Cũng không nên gọi logging code có thể ném trong finally mà không có phương án:** nếu logger/appender phát sinh lỗi và finally ném, exception nghiệp vụ chính có thể bị che. Hãy giữ cleanup đơn giản, có kiểm soát và theo dõi failures bằng tầng phù hợp.

## 22. finally có phải nơi khôi phục interrupt không?

`InterruptedException` thường được ném từ thao tác blocking như `Thread.sleep`, `BlockingQueue.take`, `CountDownLatch.await` hoặc `Future.get`, làm kết thúc thao tác chờ. Khi catch `InterruptedException`, có thể truyền tiếp hoặc khôi phục cờ interrupt nếu không thể truyền checked exception:

```java
try {
    waitForWork();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    return;
} finally {
    cleanupOwnedResources();
}
```

Đây là snippet minh họa, với `waitForWork`/`cleanupOwnedResources` giả định. Lưu ý: **khôi phục cờ trước finally** có thể khiến cleanup blocking interruptible bị ngắt ngay. Nếu cleanup cần chờ trong thời hạn nhất định, phải thiết kế thứ tự bắt/đóng/phục hồi cờ có chủ đích.

TWR với `catch` bên ngoài có ưu điểm là resource `close` xảy ra trước khi catch khôi phục flag. Nhưng close vẫn có thể ném exception và cần policy tương ứng. Không dùng `return` trong finally để xóa `InterruptedException`; như vậy caller có thể không biết yêu cầu dừng.

## 23. TWR không tự làm thao tác I/O hoặc transaction trở nên atomic

TWR bảo đảm **đóng tài nguyên theo scope khi flow Java rời block**, không bảo đảm:

- Ghi file bị rollback nếu phần thân thất bại.
- Request HTTP bị hủy ở server khi client đóng socket.
- Database đã rollback sau khi connection `close()` nếu transaction chưa có hợp đồng rõ.
- Message đã gửi được thu hồi khi lỗi xảy ra sau publish.
- Giải phóng lock/permit đúng nếu code acquire sai phạm vi.
- Một task async đã dừng khi caller đóng resource chung.

Nếu việc ghi file phải atomic theo hợp đồng, cần chiến lược tệp tạm và commit/rename phù hợp filesystem, không chỉ try-with-resources. Với database, cần transaction/rollback được thiết kế; với mạng, cần idempotency và trạng thái UNKNOWN/PENDING khi kết quả không thể xác minh.

Phần “result” trong bảng TWR là **kết quả điều khiển Java của method**, không phải lời bảo đảm mọi side effect ngoài tiến trình chưa xảy ra.

## 24. Checked exception của close và giới hạn biên dịch

Một class có kiểu `AutoCloseable` nói chung khai báo `close() throws Exception`. Vì vậy TWR sử dụng resource kiểu `AutoCloseable` có thể yêu cầu method:

- Khai báo `throws Exception` thích hợp; hoặc
- Catch loại checked exception cần thiết.

Ví dụ:

```java
static void demo(AutoCloseable r) throws Exception {
    try (r) {
        System.out.println("using resource");
    }
}
```

Đây là phương thức hợp lệ khi đặt trong class; tham số `r` là effectively final nếu không bị gán lại. `throws Exception` xuất phát từ khả năng `AutoCloseable.close()` ném checked `Exception`.

Nếu subtype override `close()` không ném checked exception và biến resource có static type subtype, compiler có thể không yêu cầu `throws Exception` chỉ do `close`. Loại cụ thể của resource, không chỉ hành vi runtime, ảnh hưởng kiểm tra exception lúc biên dịch.

`catch` được chọn theo **runtime class của Throwable**, nhưng tính hợp lệ của nhiều checked `catch` còn chịu kiểm tra của compiler dựa trên các exception có thể được ném theo static types. Đừng thêm `catch(IOException)` khi compiler đã xác định đoạn `try` không có đường nào ném loại checked ấy.

## 25. Bảng chọn cấu trúc khi thiết kế API

| Nhu cầu | Cấu trúc nên cân nhắc | Lý do |
| --- | --- | --- |
| Chạy code có thể lỗi và xử lý lỗi được xác định | `try/catch` với loại exception cụ thể | Phục hồi/đổi nghĩa đúng ranh giới. |
| Luôn chạy một phần cleanup không phải resource close | `try/finally`, finally cố gắng kết thúc bình thường | Cleanup trên các đường thoát thông thường. |
| Quản lý `AutoCloseable` do scope sở hữu | `try-with-resources` | Đóng theo thứ tự chuẩn, bảo toàn suppressed. |
| Có nhiều resource phụ thuộc nhau | TWR với khai báo theo thứ tự acquire | Đóng ngược thứ tự, kể cả khi acquire sau lỗi. |
| Muốn kết quả trả về cùng dữ liệu sau cleanup | `return` trong try; finally không return/throw ngoài ý muốn | Giá trị tính trước cleanup, chỉ đến caller nếu cleanup không thay outcome. |
| Muốn preserve lỗi body lẫn lỗi close | TWR hoặc cơ chế explicit addSuppressed đúng | Tránh che nguyên nhân chính. |
| Cần transaction nhiều bước | Transaction/domain workflow, thường kết hợp TWR cho connection | TWR không thay transaction. |
| Cần observability khi lỗi | Log/counter tại boundary có quyền sở hữu | Không làm finally tự ném khiến mất lỗi gốc. |

Một cấu trúc có thể đúng cú pháp nhưng sai contract. Chọn theo loại resource, quyền sở hữu, exception cần giữ, trạng thái sau lỗi và cách caller xử lý.

## 26. Những lỗi nhận thức thường gặp

**“Có return trong try thì finally không chạy.”** Sai. Return được chuẩn bị, finally chạy rồi method mới trả nếu finally hoàn tất bình thường.

**“Return trong finally chỉ là ghi đè biến local.”** Sai. Return trong finally có thể thay hẳn return/throw đang chờ.

**“Finally luôn chạy, kể cả JVM bị kill.”** Sai. Nó là cơ chế ngôn ngữ trong luồng điều khiển bình thường, không phải bảo đảm khi process crash/halt.

**“Lỗi trong catch được catch khác cùng statement bắt tiếp.”** Sai. Các catch clause của cùng try không được tổ chức thành chuỗi xử lý cho lỗi ném từ một catch clause; lỗi mới đi qua finally và caller/outer catch.

**“Finally ném exception sẽ tự lưu exception cũ vào suppressed.”** Sai với finally thông thường. Nếu Efinally thắng, Etry có thể mất khỏi đường truyền tự nhiên.

**“TWR chỉ đóng khi không có return.”** Sai. TWR đóng resource trước khi rời scope, kể cả khi body return.

**“TWR đóng từ trái qua phải.”** Sai. Tạo trái sang phải, đóng phải sang trái.

**“Nếu resource thứ hai tạo thất bại thì resource thứ nhất không đóng.”** Sai. Những resource đã khởi tạo thành công vẫn được cleanup theo TWR.

**“Lỗi close lúc body throw sẽ thay lỗi body.”** Không phải quy tắc TWR thông thường; close lỗi thường được suppressed vào body exception.

**“Nếu body return và close fail thì vẫn trả được giá trị.”** Sai; lỗi close có thể thay pending return.

**“Cause và suppressed là giống nhau.”** Sai. Cause biểu diễn chuỗi nguyên nhân/wrapping; suppressed giữ các lỗi phụ không được chọn làm primary.

**“Catch đặt trong body có thể bắt lỗi resource close.”** Không, nếu close xảy ra sau khi vùng try nội bộ đã hoàn tất; cần catch ngoài statement TWR.

**“Có TWR là database transaction chắc rollback.”** Sai. Cleanup resource không tương đương transaction commit/rollback.

**“TWR đóng resource mượn là vô hại.”** Sai. Scope phải sở hữu trách nhiệm đóng; resource dùng chung có thể bị thành phần khác sử dụng.

**“Dùng `catch(Throwable)` là cách an toàn để lấy mọi exception.”** Thường sai cho business recovery: có thể nuốt Error nghiêm trọng, bug hoặc interruption/cancellation bị chuyển nghĩa sai.

## 27. Checklist đọc một đoạn mã try/catch/finally

Khi phải dự đoán kết quả, làm theo trình tự:

1. **Xác định exception hoặc return thật sự phát sinh trong try.** Đừng đoán theo tên method.
2. **Xác định catch phù hợp đầu tiên** trong cùng try, nếu có.
3. **Theo dõi completion của catch**: xử lý bình thường, return hay throw mới?
4. **Nếu là TWR, trước khi đến catch ngoài, xử lý resource close** theo thứ tự ngược khởi tạo.
5. **Phân loại lỗi đóng:** body/init có primary Throwable hay không? Nếu có, close errors thường suppressed; nếu không, close exception đầu tiên theo thứ tự đóng có thể primary.
6. **Xác định finally gắn với scope nào**: finally của TWR bên ngoài khác try/finally nằm bên trong body.
7. **Xác định completion của finally**: bình thường giữ kết quả trước; throw/return mới thay.
8. **Nếu có return expression**, nó được đánh giá trước cleanup; check xem finally có mutate object được trả hay không.
9. **Kiểm tra exception còn đi ra caller** và quan hệ cause/suppressed.
10. **Phân biệt kết quả Java với side effect bên ngoài**: close không bảo đảm transaction rollback.
11. **Kiểm tra ownership/lifecycle**: resource được scope tạo hay chỉ mượn?
12. **So với contract**, hỏi method sau lỗi có còn dùng được và state có hợp lệ không.

Đừng giải bài bằng câu ngắn “finally luôn thắng”. **Finally chỉ thay completion khi nó tự hoàn tất bất thường**; khi hoàn tất bình thường, nó giữ pending return/throw.

## 28. Bài tập tra cứu kết quả

**Câu 1.** `try` chuẩn bị `return 7`, `finally` chỉ in một dòng và hoàn tất bình thường. Method trả gì?

**Đáp án:** 7, sau khi finally chạy.

**Câu 2.** `try` ném `IllegalArgumentException`, catch phù hợp trả `-1`, finally hoàn tất bình thường. Kết quả?

**Đáp án:** -1. Exception đã được catch xử lý và pending return của catch không bị thay.

**Câu 3.** `try` ném E1, không có catch phù hợp, finally ném E2. Caller thấy gì? E1 có tự thành suppressed không?

**Đáp án:** Caller thấy E2; E1 không được tự gắn suppressed bởi finally thường.

**Câu 4.** `try` trả tham chiếu tới `ArrayList` rồi finally thêm phần tử vào list. Caller thấy list trước hay sau thay đổi?

**Đáp án:** Reference đã được chọn trước, nhưng object mutable bị sửa trong finally nên caller thấy phần tử được thêm.

**Câu 5.** TWR khai báo A, B, C; body hoàn tất bình thường. Close C và A ném IOException, B đóng bình thường. Exception nào primary?

**Đáp án:** Lỗi close C primary vì C đóng trước. Lỗi close A trở thành suppressed của lỗi C.

**Câu 6.** Body TWR ném Ebody, close B và A đều lỗi. Bảng ưu tiên?

**Đáp án:** Ebody primary; lỗi B rồi A được suppressed theo thứ tự đóng, giả định suppression enabled và các Throwable hợp lệ.

**Câu 7.** A tạo thành công, tạo B ném Einit, đóng A cũng lỗi. Body có chạy không? Caller thấy gì?

**Đáp án:** Body không chạy; Einit primary, lỗi close A suppressed.

**Câu 8.** TWR body return `"OK"`, close ném `IOException`. Caller nhận `"OK"` hay IOException?

**Đáp án:** IOException; pending return bị thay bởi exceptional completion của close.

**Câu 9.** Có `try (r) { try {work();} catch(IOException e) {recover();} }`. Close r ném IOException sau khi body hoàn tất. Catch nội bộ có bắt được không?

**Đáp án:** Không; close xảy ra sau body. Cần catch ngoài TWR để bắt exception từ close.

**Câu 10.** Có `try (r) {throw Ebody;} finally {throw Efinally;}` và r.close cũng ném lỗi. Caller thấy gì?

**Đáp án:** Trong xử lý TWR, lỗi close có thể được suppressed vào Ebody; sau đó finally ném Efinally và thay kết quả đang chờ. Caller thấy Efinally, không tự nhận chuỗi Ebody/suppressed nếu code không giữ riêng chúng.

## 29. Tổng kết

Có thể nhớ các quy tắc cốt lõi qua hai câu:

- **`try/catch/finally`:** xác định completion của try hoặc catch, sau đó xem finally có hoàn tất bình thường hay có một completion mới thay thế.
- **`try-with-resources`:** resource khởi tạo trái→phải và đóng phải→trái; exception body/khởi tạo được giữ làm primary, lỗi close được suppressed khi đã có primary; nếu chưa có primary thì lỗi close có thể trở thành lỗi chính và thay cả pending return.

Hãy dùng bảng để **dự đoán luồng điều khiển**, rồi kiểm tra thêm **state, tài nguyên, cause, suppressed và quyền sở hữu** để đánh giá API có thật sự an toàn. Trong mã production, tránh `return`/`throw` ngoài ý muốn trong `finally` và ưu tiên `try-with-resources` cho `AutoCloseable`, nhưng vẫn thiết kế transaction/rollback riêng khi thao tác có side effect ngoài JVM.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-reference-004">Bảng phân biệt cause, suppressed và stack trace</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng phân biệt cause, suppressed và stack trace trong Java

> **Chủ đề:** Java Core → Exception → Tra cứu. Tài liệu này là bảng tham chiếu thực hành về ba loại thông tin của `Throwable`: **cause** (lỗi nguyên nhân), **suppressed exceptions** (ngoại lệ phụ được đính kèm) và **stack trace** (dấu vết lời gọi). Nội dung dùng ngữ nghĩa Java 17+, ví dụ `try-with-resources` chạy độc lập, cách đọc log, xử lý wrapper và những nhầm lẫn dễ gây mất dữ liệu chẩn đoán.

## 1. Bảng phân biệt nhanh

| Tiêu chí | Cause | Suppressed exceptions | Stack trace |
|---|---|---|---|
| Câu hỏi trả lời | **Lỗi nào dẫn tới ngoại lệ hiện tại?** | **Những lỗi phụ nào được giữ trong khi xử lý lỗi chính?** | **Throwable này có dấu vết frame gọi như thế nào?** |
| Kiểu dữ liệu | Một tham chiếu `Throwable` hoặc `null` | Mảng `Throwable[]`, có thể rỗng | Mảng `StackTraceElement[]`, có thể rỗng |
| API đọc | `getCause()` | `getSuppressed()` | `getStackTrace()` |
| API ghi/phát sinh | Constructor nhận `cause` hoặc `initCause()` | `addSuppressed(Throwable)`; được Java tự thêm trong `try-with-resources` khi phù hợp | `fillInStackTrace()`, `setStackTrace(...)`; thường chụp ở lúc khởi tạo Throwable |
| Quan hệ với lỗi hiện tại | Mối quan hệ *nguyên nhân/chuyển tầng* | Lỗi đi kèm, **không tự trở thành nguyên nhân chính** | Thông tin về các frame thực thi, **không phải Throwable khác** |
| Số lượng trực tiếp | Tối đa một cause trực tiếp; cause có thể có cause tiếp | Có thể nhiều suppressed trực tiếp | Có thể nhiều frame trong một trace |
| Có tự động lan qua wrapper? | Khi wrapper được tạo với cause | **Không tự chuyển** suppressed của cause sang wrapper | Wrapper và cause thường có stack trace **riêng** |
| Khi dùng | Bọc lỗi kỹ thuật thành lỗi domain; `ExecutionException` chứa lỗi task | Lỗi khi đóng resource, cleanup, compensation phụ | Điều tra vị trí tạo/ném, đường đi mã, các tầng gọi |
| Kiểm tra trong test | `assertSame(expected, ex.getCause())` hoặc kiểm tra type | `ex.getSuppressed().length`, type/message của phần tử | Top method/class khi ổn định; thường **không** cố định line number |
| Có nên xóa cho log gọn? | Không: có thể mất nguyên nhân gốc | Không: có thể mất lỗi cleanup quan trọng | Không tùy tiện: có thể mất vị trí và đường gọi của lỗi |
| Có thể không có? | Có, nếu không khai báo hoặc không gắn cause | Có: mảng rỗng là bình thường | Có: stack có thể bị tắt hoặc rỗng vì triển khai/điều kiện |
| Dạng hiển thị phổ biến trong `printStackTrace()` | Dòng `Caused by: ...` | Dòng `Suppressed: ...` | Các dòng bắt đầu bằng `at ...` |

**Ghi nhớ:** *cause* và *suppressed* đều tham chiếu tới **Throwable**, còn *stack trace* chứa các **StackTraceElement**. Một đối tượng Throwable có thể đồng thời có một cause, nhiều suppressed và stack trace của chính nó; các Throwable ở cause/suppressed cũng có metadata riêng.

## 2. Mô hình quan hệ trên một Throwable

~~~text
IllegalStateException("order processing failed")  <-- outer Throwable
  |
  +-- cause --> IOException("read failed")
  |               |
  |               +-- suppressed[0] --> IOException("close failed")
  |               |                      +-- stack trace riêng
  |               +-- stack trace riêng
  |
  +-- suppressed[] = rỗng
  +-- stack trace = frames khi tạo outer Throwable
~~~

Sơ đồ thể hiện **hai dạng liên kết lỗi độc lập** và một dấu vết riêng cho từng Throwable:

1. Service bắt `IOException("read failed")` rồi bọc thành `IllegalStateException("order processing failed", cause)`.
2. Trong `try-with-resources`, lỗi `close()` được thêm vào **IOException do thân try ném**, dưới dạng suppressed.
3. Wrapper ở service có cause là IOException. `wrapper.getSuppressed()` vẫn có thể rỗng; muốn xem lỗi đóng tài nguyên phải đi tới `wrapper.getCause().getSuppressed()`.
4. Wrapper, cause và suppressed Throwable đều có thể có stack trace riêng, được chụp tại những vị trí khác nhau.

Không coi hệ thống này là một cây ngữ nghĩa tuyệt đối của nghiệp vụ. Nó là đồ thị tham chiếu Throwable; ứng dụng có thể tạo quan hệ không chuẩn, chain dài, hoặc wrapper dùng lại. Khi duyệt thủ công, tránh giả định mọi throwable đều chỉ có một chuỗi cause đơn giản.

## 3. Cause: nguyên nhân được giữ khi chuyển abstraction

Một cause là Throwable mà exception hiện tại xem như **lỗi nguyên nhân**. API đọc:

~~~java
Throwable cause = exception.getCause();
~~~

Nếu không có cause, giá trị thường là `null`. Một constructor có cause:

~~~java
// Trích đoạn trong một phương thức.
java.io.IOException original =
        new java.io.IOException("database response unavailable");

IllegalStateException wrapper =
        new IllegalStateException("cannot load order", original);

System.out.println(wrapper.getCause() == original); // true
~~~

Ở ví dụ này `IllegalStateException` là lỗi tại abstraction bên ngoài; `IOException` là nguyên nhân kỹ thuật. Việc bọc không làm IOException trở thành suppressed. Cause và suppressed là **hai quan hệ khác nhau**.

### Khi nào dùng cause?

- Repository chuyển lỗi driver/I/O sang exception lưu trữ.
- Service chuyển lỗi lưu trữ sang exception domain mà caller có thể hiểu.
- Executor lưu lỗi thực thi trong `ExecutionException` mà thread gọi `Future.get()` nhận được.
- CompletableFuture báo exceptional completion qua `CompletionException` khi gọi `join()`.
- Một phép xử lý cần bổ sung ngữ cảnh, nhưng vẫn cho người điều tra xem nguyên nhân gốc.

Constructor đúng khi bọc lỗi:

~~~java
catch (java.io.IOException ex) {
    throw new IllegalStateException("could not load profile", ex);
}
~~~

Không nên:

~~~java
catch (java.io.IOException ex) {
    throw new IllegalStateException(ex.getMessage());
}
~~~

Cách thứ hai **không giữ cause**; caller có thể mất stack của IOException và thông tin nguyên nhân. Nếu chỉ muốn truyền lỗi đúng hợp đồng, dùng `throw ex;` khi có thể — không phải cứ có catch là phải tạo wrapper.

### getCause() và initCause()

`Throwable.initCause(Throwable)` có thể đặt cause **một lần** nếu Throwable chưa được khởi tạo cause theo thiết kế constructor. Nó trả về chính Throwable nhận lời gọi. Thử thay cause đã khởi tạo sẽ gây `IllegalStateException`; đặt Throwable làm cause của chính nó là không hợp lệ.

~~~java
// Hợp lệ trong một phương thức.
Exception outer = new Exception("outer");
IOException cause = new IOException("inner");
outer.initCause(cause);
~~~

Đoạn này cần import `java.io.IOException` nếu dùng tên ngắn. **Ưu tiên constructor nhận cause** khi thư viện cung cấp, vì ngắn, rõ và giữ trạng thái đúng ngay từ đầu. Đừng dựa vào `initCause()` để thay đổi một error chain đã được công bố cho các thread khác.

Một số Throwable có constructor đã khởi tạo cause (kể cả khi giá trị là `null`), nên không thể gọi `initCause` nữa; hãy đọc Javadoc của constructor đang dùng.

## 4. Suppressed exceptions: giữ lỗi phụ mà không che lỗi chính

`Throwable.addSuppressed(Throwable)` gắn thêm một ngoại lệ phụ vào Throwable hiện tại. `getSuppressed()` trả một mảng của các Throwable đã được gắn, hoặc mảng rỗng nếu không có. Đừng hiểu suppressed là lỗi không quan trọng: lỗi khi đóng file, hoàn tất transaction hoặc dọn resource có thể giúp xác định vì sao trạng thái cuối không như kỳ vọng.

Ví dụ thao tác thủ công:

~~~java
// Trích đoạn trong một phương thức.
IllegalStateException primary =
        new IllegalStateException("primary failure");

primary.addSuppressed(new IOException("cleanup also failed"));

System.out.println(primary.getSuppressed().length); // 1
~~~

Đoạn này cần import `java.io.IOException`. `addSuppressed(null)` ném `NullPointerException`; thêm chính Throwable đó làm suppressed của nó ném `IllegalArgumentException`. `getSuppressed()` trả một mảng để đọc; sửa phần tử mảng trả về không phải cách cập nhật danh sách suppressed nội bộ.

### Suppressed có giống cause không?

Không:

- `cause` thể hiện quan hệ “ngoại lệ A do B gây ra” hoặc “A bọc B”.
- `suppressed` giúp giữ “ngoài A, còn có lỗi C phát sinh trong xử lý/cleanup”.
- `getCause()` không trả suppressed; `getSuppressed()` không trả cause.
- `printStackTrace()` có thể hiển thị cả hai theo cấu trúc của Throwable.

Không nên biến mọi lỗi phụ thành cause hoặc ngược lại chỉ để ghi log trông ngắn hơn. Cần giữ **lỗi nào quyết định kết quả thất bại chính** và **lỗi nào đi kèm**.

## 5. Try-with-resources: nơi suppressed xuất hiện thường gặp nhất

`try-with-resources` tự gọi `close()` cho resource ở cuối scope. Giả sử thân `try` đã ném ngoại lệ A, và `close()` cũng ném ngoại lệ B. Java giữ A làm **exception chính**, đồng thời gắn B vào `A.getSuppressed()`.

~~~java
try (SomeResource resource = openResource()) {
    throw new IOException("body failed");
    // Khi rời scope, close() vẫn được gọi.
}
~~~

Đây là đoạn minh họa, cần định nghĩa `SomeResource` và `openResource()`. Nếu `close()` ném IOException thứ hai, exception đó thường nằm trong suppressed của lỗi ở thân `try`.

Các trường hợp quan trọng:

| Thân try | close() | Kết quả |
|---|---|---|
| Thành công | Thành công | Không có lỗi từ hai nguồn này |
| Thất bại A | Thành công | Ném A |
| Thành công | Thất bại B | Ném B làm lỗi chính |
| Thất bại A | Thất bại B | Ném A; B được suppress vào A |
| Nhiều resource, body thất bại | Nhiều close thất bại | Các close failure có thể được gắn suppressed lên lỗi chính |
| Không có lỗi ở body, nhiều close thất bại | Close lỗi ở một hoặc nhiều resource | Lỗi của lần close thất bại đầu tiên khi đóng theo thứ tự ngược được ném chính; các lỗi close sau có thể bị suppress |

Java đóng resource theo **thứ tự ngược với thứ tự khai báo**. Nếu mở `A` rồi `B`, Java gọi `B.close()` trước `A.close()`. Đừng suy đoán suppressed exception nào thuộc resource nào chỉ từ vị trí mảng nếu bạn chưa xác định thứ tự đóng và các điểm ném.

### So với finally tự viết

Mã thủ công sau có thể làm lỗi close **che** lỗi ban đầu:

~~~java
// Phản ví dụ. closeResource() có thể ném.
try {
    readData();             // Ném lỗi chính.
} finally {
    closeResource();        // Nếu cũng ném, có thể thay lỗi đang lan truyền.
}
~~~

`try-with-resources` tự xử lý suppression để bảo toàn lỗi thân `try` khi cả hai đường đều thất bại. Với cleanup thủ công, nếu cần bảo tồn nhiều lỗi, phải thiết kế cơ chế kết hợp/`addSuppressed` phù hợp; không chỉ gọi `close` trong `finally` rồi mặc định tất cả nguyên nhân đều được giữ.

## 6. Stack trace: dấu vết thực thi gắn với Throwable

`Throwable.getStackTrace()` trả mảng `StackTraceElement[]`. Một phần tử cung cấp thông tin như tên lớp, phương thức, tệp nguồn, số dòng (nếu có) hoặc metadata khác của frame.

~~~java
// Trích đoạn trong phương thức; exception đã được tạo.
StackTraceElement[] frames = exception.getStackTrace();
if (frames.length > 0) {
    System.out.println(frames[0].getClassName());
    System.out.println(frames[0].getMethodName());
}
~~~

Stack trace thường được ghi nhận **khi đối tượng Throwable được khởi tạo** qua `fillInStackTrace()`, không phải lúc bất kỳ caller nào nhìn thấy exception. Điều này rất quan trọng khi:

- Tạo exception trước rồi `throw` sau: stack có thể chỉ nơi tạo.
- Bắt rồi `throw ex;`: ném lại cùng object thường **không tự chụp stack mới**.
- Bắt rồi `throw new WrapperException(..., ex);`: wrapper có trace tại nơi tạo wrapper; cause giữ trace riêng ở nơi tạo cause.
- Exception đi từ worker qua `Future.get()`: wrapper ở thread chờ và lỗi task có thể có **trace ở hai thread khác nhau**.
- JVM/Throwable có thể tối ưu hoặc vô hiệu hóa stack trace; mảng có thể rỗng, nên không giả định `frames[0]` luôn tồn tại.

`getStackTrace()` không trả danh sách lỗi khác. Một frame không phải cause và không phải suppressed Throwable; frame mô tả một điểm trong chuỗi lời gọi.

### printStackTrace là bản in có cấu trúc rộng hơn

`Throwable.printStackTrace()` có thể in ra:

~~~text
java.lang.IllegalStateException: operation failed
    at Example.execute(Example.java:...)
Caused by: java.io.IOException: read failed
    at Example.read(Example.java:...)
    Suppressed: java.io.IOException: close failed
        at Example$Resource.close(Example.java:...)
~~~

Đây là **sơ đồ định dạng tham khảo**, không phải output chính xác của code ở phần 7. Trong thực tế, vị trí và thụt lề có thể khác; các frame chung có thể được rút gọn thành `... N more`. Đừng xây parser nghiệp vụ dựa trên text của `printStackTrace()`.

Dùng `logger.error("operation failed", ex)` hoặc API logger tương đương có hỗ trợ Throwable trong production, thay vì chỉ `logger.error(ex.getMessage())` làm mất cause/suppressed/frames.

## 7. Chương trình Java 17 độc lập minh họa cả ba

Chương trình này cố tình tạo lỗi trong thân `try` và lỗi lúc `close`, sau đó bọc lỗi lên tầng ứng dụng. Các assertion quan trọng dựa vào **kiểu, message, số lượng và tên phương thức đầu stack**, không phụ thuộc số dòng.

Lưu tên **`ThrowableRelationsDemo.java`**:

~~~java
import java.io.IOException;

public class ThrowableRelationsDemo {
    static final class Resource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            throw new IOException("close failed");
        }
    }

    static void read() throws IOException {
        try (Resource resource = new Resource()) {
            throw new IOException("read failed");
        }
    }

    static void execute() {
        try {
            read();
        } catch (IOException cause) {
            throw new IllegalStateException("operation failed", cause);
        }
    }

    public static void main(String[] args) {
        try {
            execute();
        } catch (IllegalStateException outer) {
            Throwable cause = outer.getCause();
            Throwable[] suppressed = cause.getSuppressed();

            System.out.println("outer: " + outer.getMessage());
            System.out.println("cause: "
                    + cause.getClass().getSimpleName()
                    + ": " + cause.getMessage());
            System.out.println("outer suppressed: "
                    + outer.getSuppressed().length);
            System.out.println("cause suppressed: " + suppressed.length);
            System.out.println("suppressed[0]: "
                    + suppressed[0].getClass().getSimpleName()
                    + ": " + suppressed[0].getMessage());

            System.out.println("outer top method: "
                    + outer.getStackTrace()[0].getMethodName());
            System.out.println("cause top method: "
                    + cause.getStackTrace()[0].getMethodName());
            System.out.println("suppressed top method: "
                    + suppressed[0].getStackTrace()[0].getMethodName());
        }
    }
}
~~~

Chạy bằng JDK 17+:

~~~bash
javac ThrowableRelationsDemo.java
java ThrowableRelationsDemo
~~~

**Stdout chính xác của ví dụ khi chạy trên JDK thông thường với stack trace được bật:**

~~~text
outer: operation failed
cause: IOException: read failed
outer suppressed: 0
cause suppressed: 1
suppressed[0]: IOException: close failed
outer top method: execute
cause top method: read
suppressed top method: close
~~~

### Đọc kết quả

1. `Resource.close()` ném `IOException("close failed")`.
2. `read()` ném `IOException("read failed")` **trước** khi close, do đó lỗi body là lỗi chính; Java tự đính lỗi close vào mảng suppressed của **lỗi read**.
3. `execute()` bắt IOException và tạo `IllegalStateException("operation failed", cause)`. Wrapper có cause là IOException, nhưng **không tự nhận suppressed của cause**. Vì vậy outer suppressed = 0, cause suppressed = 1.
4. Trace của outer bắt đầu tại `execute`, của IOException lỗi body tại `read`, và của IOException lỗi close tại `close`. Đây là ba đối tượng Throwable và ba stack trace có vị trí phát sinh khác nhau.
5. Không cần in `printStackTrace()` để xác minh quan hệ. Nếu in, stack sẽ có đường `Caused by` và `Suppressed` cùng file/line, phần đó phụ thuộc bố trí source.
6. Ví dụ không dùng thread, network, đồng hồ hay hệ thống file; dữ liệu và thứ tự ổn định. Trong các runtime tùy biến có stack trace bị vô hiệu hóa, assertion trực tiếp vào `[0]` cần được điều chỉnh.

## 8. Một bảng tra cứu API Throwable quan trọng

| API | Đọc/ghi | Kết quả hoặc lưu ý |
|---|---|---|
| `getCause()` | Đọc | Trả cause trực tiếp hoặc `null` |
| `initCause(cause)` | Ghi một lần khi được phép | Đặt cause; không thay thế tùy tiện cause đã khởi tạo |
| `getSuppressed()` | Đọc | Trả mảng suppressed; mảng rỗng nếu không có |
| `addSuppressed(error)` | Ghi | Gắn Throwable phụ; cấm `null` và tự suppress |
| `getStackTrace()` | Đọc | Trả bản sao mảng các frame; có thể rỗng |
| `setStackTrace(frames)` | Ghi | Thay stack trace được trình bày khi writable; dùng thận trọng |
| `fillInStackTrace()` | Ghi | Làm mới trace theo stack hiện tại nếu writable; có thể thay bằng chứng gốc |
| `printStackTrace()` | Hiển thị | In trace của exception cùng cause/suppressed theo format chẩn đoán |
| `getMessage()` | Đọc | Message cho con người; không là error code có hợp đồng mặc định |
| `getLocalizedMessage()` | Đọc | Có thể được bản địa hóa; không làm dữ liệu máy đọc |
| `toString()` | Đọc | Thường kết hợp tên lớp và message; không thay thế full stack trace |

### Có thể chỉnh stack trace không?

`setStackTrace(StackTraceElement[])` cho phép thay thế dấu vết được trình bày trong những Throwable hỗ trợ writable stack trace. `fillInStackTrace()` chụp lại tình huống hiện tại. Cả hai hiếm khi là cách đúng để “sửa” lỗi nghiệp vụ: nếu thao tác mù quáng, bạn có thể xóa vị trí tạo lỗi ban đầu, gây khó điều tra.

`getStackTrace()` trả một mảng không nên được xem là state mutable chung. Thay một phần tử của mảng vừa nhận không phải cách cập nhật stack trace của Throwable; nếu thực sự cần đặt trace, phải gọi API có chủ đích và hiểu hậu quả.

## 9. Cause chain khác suppressed list và trace frames

Ba thao tác truy vấn thường có hình dạng khác nhau.

**Duyệt cause chain:**

~~~java
// Trích đoạn minh họa, exception đã tồn tại.
Throwable current = exception;
while (current != null) {
    System.out.println(current.getClass().getSimpleName());
    current = current.getCause();
}
~~~

**Đọc suppressed của một Throwable cụ thể:**

~~~java
for (Throwable extra : exception.getSuppressed()) {
    System.out.println("additional: " + extra.getMessage());
}
~~~

**Đọc các frame của một Throwable cụ thể:**

~~~java
for (StackTraceElement frame : exception.getStackTrace()) {
    System.out.println(frame.getClassName() + "."
            + frame.getMethodName());
}
~~~

Các đoạn là minh họa, không phải chương trình độc lập; cần biến `exception` và import thích hợp. Nếu bạn chỉ duyệt `getCause()`, bạn có thể bỏ qua suppressed. Nếu chỉ gọi `getSuppressed()` trên outer wrapper, bạn có thể bỏ qua suppressed **trên cause**. Nếu chỉ lấy trace ngoài cùng, bạn có thể nhìn thấy nơi wrapper được tạo nhưng không biết lỗi kỹ thuật gốc phát sinh ở đâu.

Trong code production có exception structure bất thường hoặc vòng tham chiếu do tích hợp, khi viết utility duyệt đồ thị hãy dùng tập danh tính đã thăm (ví dụ `IdentityHashMap`) và giới hạn độ sâu/số node; đừng dùng một vòng lặp không giới hạn từ input không đáng tin.

## 10. Kịch bản 1 — chỉ có cause, không có suppressed

Ví dụ:

~~~java
IOException io = new IOException("file not found");
RuntimeException application =
        new IllegalStateException("cannot import", io);
~~~

Trường hợp này:

- `application.getCause() == io` là true.
- `application.getSuppressed().length` thường bằng 0.
- `io.getSuppressed().length` cũng thường bằng 0 nếu không ai gắn thêm.
- `application.getStackTrace()` là trace khi wrapper được tạo.
- `io.getStackTrace()` là trace khi IOException được tạo.

Nếu log chỉ ngoại lệ ngoài cùng dưới dạng `application.getMessage()`, bạn sẽ mất thông tin `"file not found"` và stack của IOException. Nên truyền Throwable đầy đủ cho logger.

## 11. Kịch bản 2 — có suppressed nhưng không có cause

~~~java
IllegalStateException primary =
        new IllegalStateException("commit failed");
primary.addSuppressed(new IOException("rollback failed"));
~~~

Ở đây:

- Cause của `primary` có thể là `null`.
- Suppressed gồm `IOException("rollback failed")`.
- `rollback failed` không tự trở thành nguyên nhân gốc của lỗi commit; đây là **lỗi phụ** cần lưu vết.
- Đừng đổi `primary` thành `new RuntimeException("rollback failed")` mà vứt đi lỗi commit.

Ví dụ dùng `IOException` nên cần import trong phương thức/lớp. Khi xử lý giao dịch thật, nghiệp vụ phải thiết kế chính sách compensation/rollback riêng; suppressed chỉ lưu thông tin lỗi, không tự sửa dữ liệu.

## 12. Kịch bản 3 — wrapper với cause có suppressed

Đây là đúng tình huống `ThrowableRelationsDemo`:

~~~text
outer IllegalStateException
  cause -> body IOException
           suppressed[0] -> close IOException
~~~

**Hai truy vấn cho kết quả khác nhau:**

~~~java
outer.getSuppressed();             // Mảng rỗng.
outer.getCause().getSuppressed();  // Chứa IOException từ close.
~~~

Đoạn minh họa cần biến `outer` như trong chương trình đầy đủ. Đừng copy suppressed lên outer nếu không có lý do rõ ràng: như vậy một lỗi close có thể bị xuất hiện **hai lần** khi logger duyệt cả outer và cause. Việc wrapper giữ nguyên reference `cause` đã đủ để preserved metadata bên dưới.

Trong log, `printStackTrace()` duyệt cả cause và suppressed của cause. Các logger phổ biến có hỗ trợ Throwable thường cũng hiển thị cây lỗi; cần xác nhận cấu hình cụ thể của backend nếu output không có thông tin suppressed.

## 13. Kịch bản 4 — nhiều resource đóng theo thứ tự ngược

Với:

~~~java
try (ResourceA a = new ResourceA();
     ResourceB b = new ResourceB()) {
    doWork();
}
~~~

Java đóng `b` trước `a`. Nếu `doWork()` ném A và `b.close()` ném B, `a.close()` ném C:

~~~text
Primary: A (doWork)
  suppressed[0]: B (b.close)
  suppressed[1]: C (a.close)
~~~

Đây là mô hình logic với giả định cả hai close ném. Không dựa vào thứ tự suppressed để suy ra quan hệ nhân quả giữa B và C; thứ tự phản ánh việc Java đóng resource.

Nếu body không ném và `b.close()` ném B, lỗi B có thể là primary; lỗi từ `a.close()` được suppress vào B. Thứ tự khai báo resource khác có thể đổi thứ tự close, nhưng không nên thay tài nguyên chỉ để làm đẹp exception output.

### Giữ riêng lỗi mở resource

Nếu constructor hoặc biểu thức khởi tạo một resource trong danh sách khai báo ném lỗi, Java vẫn phải đóng các resource **đã mở thành công trước đó** theo quy tắc try-with-resources. Lỗi close khi cleanup các resource ấy có thể được suppress vào exception khởi tạo ban đầu. Đây cũng là lý do test cleanup cần cover cả lỗi ở quá trình mở và ở thân try.

## 14. Kịch bản 5 — ngoại lệ đi qua Future, CompletableFuture

Một worker ném lỗi và caller nhận exception wrapper:

~~~java
// Trích đoạn; pool là ExecutorService.
Future<String> future = pool.submit(() -> {
    throw new IOException("read failed");
});

try {
    future.get();
} catch (ExecutionException ex) {
    Throwable original = ex.getCause();
}
~~~

Đoạn này cần imports và xử lý `InterruptedException`, hoặc đặt trong method khai báo `throws InterruptedException`. `ExecutionException` có vai trò **wrapper**, giữ exception từ task làm cause. Lỗi worker không tự “được ném” trên call stack của thread đợi; caller nhận kênh lỗi qua `Future.get()`.

Với `CompletableFuture.join()`:

~~~java
// Trích đoạn:
CompletableFuture<String> failed =
        CompletableFuture.failedFuture(new IOException("disk failed"));

try {
    failed.join();
} catch (CompletionException ex) {
    Throwable cause = ex.getCause();
}
~~~

Trong hoàn tất bất thường thông thường, `join()` báo `CompletionException` chứa cause; một future bị hủy có thể ném `CancellationException` trực tiếp. Đừng coi wrapper mới là lỗi kỹ thuật ban đầu hoặc tự suy ra suppressed trên wrapper.

Khi một task có suppressed resource-close exception trước khi bị bọc, hãy kiểm tra suppressed **ở Throwable phát sinh trong task**, tức ở cause hoặc một cause sâu hơn. Không xem stack trace của `ExecutionException` ở caller như bằng chứng lỗi I/O xảy ra trên thread đang chờ.

## 15. Kịch bản 6 — log và serialization làm mất metadata

Một anti-pattern:

~~~java
catch (Exception ex) {
    logger.error(ex.getMessage());
    throw new IllegalStateException("failed");
}
~~~

Đoạn này chỉ minh họa; `logger` là logger của ứng dụng. Tại đây:

- Logger có thể chỉ ghi text, không có stack/cause/suppressed.
- Wrapper mới không giữ ex làm cause.
- Điều tra viên không thể tìm lại lỗi đóng resource hoặc vị trí lỗi gốc chỉ từ string `"failed"`.

Cách giữ metadata:

~~~java
catch (Exception ex) {
    throw new IllegalStateException("operation failed", ex);
}
~~~

Nếu tầng này chỉ ném tiếp, không nhất thiết log tại đây. Ghi lỗi một lần ở ranh giới chịu trách nhiệm, dùng logger API nhận cả `Throwable` và ngữ cảnh như operation/request ID. Tránh in nguyên message có bí mật hoặc dữ liệu cá nhân vào response public.

### Dữ liệu log dạng JSON không tự giữ cây Throwable

Một JSON event dạng:

~~~json
{
  "exceptionType": "IllegalStateException",
  "message": "operation failed",
  "requestId": "req-15"
}
~~~

là log minh họa **chưa** chứa cause, suppressed và stack frames. Nếu hệ thống cần điều tra, logger/serializer phải chủ động có field/renderer cho các cấu trúc ấy, hoặc lưu trace id liên kết tới lỗi đầy đủ. Đừng khẳng định “đã log Throwable” nếu thực tế chỉ serialize `getMessage()`.

## 16. Kiểm thử ba quan hệ riêng biệt

Bộ test cần xác minh:

| Mục tiêu | Assertion nên dùng | Điều tránh |
|---|---|---|
| Wrapper có cause đúng | `assertSame(original, wrapper.getCause())` | Chỉ so sánh message |
| Cause không bị mất khi đổi tầng | `assertInstanceOf(IOException.class, wrapper.getCause())` | Bọc rồi bỏ cause |
| Suppressed của lỗi thân try | `assertEquals(1, cause.getSuppressed().length)` | Nhìn suppressed trên wrapper và tưởng không có |
| Loại lỗi close | `assertInstanceOf(IOException.class, suppressed[0])` | Parse `printStackTrace()` để lấy subtype |
| Stack trace có frame | Kiểm tra mảng không rỗng khi môi trường test cam kết hỗ trợ | Hard-code số dòng source |
| Trace của wrapper và cause khác nhau | Xác nhận method đầu như `execute` và `read` trong demo | Coi stack chỉ là chain cause |
| Nhiều close failure | Kiểm tra thứ tự và số lượng suppressed tương ứng | Đếm lỗi suppressed như hai operation thất bại độc lập |
| Rethrow cùng Throwable | Kiểm tra identity/cause theo contract | Mong stack tự động cập nhật tại mỗi `throw` |
| Lỗi chỉ xảy ra trong close | `getSuppressed()` có thể rỗng; close failure là primary | Mặc định mọi close failure là suppressed |
| Logger | Bảo đảm render cause và suppressed theo backend | Test logic bằng regex trên text log động |

### Test JUnit 5 mẫu

JUnit 5 cần dependency bên ngoài JDK. Ví dụ sau có thể đặt cùng default package với `ThrowableRelationsDemo` trong dự án test:

~~~java
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class ThrowableRelationsDemoTest {
    @Test
    void causeHoldsBodyFailureAndSuppressedHoldsCloseFailure() {
        IllegalStateException outer = assertThrows(
                IllegalStateException.class,
                ThrowableRelationsDemo::execute);

        Throwable cause = outer.getCause();
        assertInstanceOf(IOException.class, cause);
        assertEquals("read failed", cause.getMessage());

        assertEquals(0, outer.getSuppressed().length);
        assertEquals(1, cause.getSuppressed().length);
        assertInstanceOf(IOException.class, cause.getSuppressed()[0]);
        assertEquals("close failed",
                cause.getSuppressed()[0].getMessage());

        assertEquals("execute",
                outer.getStackTrace()[0].getMethodName());
        assertEquals("read",
                cause.getStackTrace()[0].getMethodName());
        assertEquals("close",
                cause.getSuppressed()[0].getStackTrace()[0].getMethodName());
    }
}
~~~

**Điều kiện test stack trace:** ví dụ này dùng Throwable mặc định với writable stack trace trên JVM thông thường. Nếu framework cấu hình tắt stack trace hoặc tái sử dụng stackless exceptions, đừng dùng assertion cứng vào `getStackTrace()[0]` ở mọi trường hợp; test metadata nguyên nhân và suppressed vẫn có thể độc lập với trace.

## 17. Những câu hỏi dễ nhầm — giải đáp nhanh

**Q1. Cause có phải là lỗi xuất hiện đầu tiên theo thời gian không?** Không nhất thiết. Cause là quan hệ do API/lập trình viên thiết lập để diễn đạt nguyên nhân/chuyển tầng; thứ tự thời gian chỉ là một yếu tố.

**Q2. Suppressed có phải là một kiểu Exception riêng?** Không. Đây là các đối tượng `Throwable` được gắn vào một Throwable khác; subtype của chúng vẫn có thể là IOException, SQLException, RuntimeException hoặc loại khác.

**Q3. Một Throwable có được có cả cause và suppressed không?** Có. Ba nhóm metadata cùng tồn tại độc lập.

**Q4. `throw ex;` có tự tạo stack trace mới không?** Thông thường không. Nó ném lại cùng object; trace đã được ghi nhận khi object được tạo hoặc gọi `fillInStackTrace()`.

**Q5. `getCause() == null` có nghĩa lỗi không có nguyên nhân?** Chỉ có nghĩa không có cause được gắn để caller đọc. Không thể suy ra nguyên nhân thực tế trong hệ thống không tồn tại.

**Q6. `getSuppressed().length == 0` có nghĩa không có lỗi cleanup?** Không chắc. Có thể cleanup thành công, lỗi đã bị bỏ/che, suppression bị vô hiệu hóa hoặc lỗi nằm ở Throwable khác trong cause chain.

**Q7. `getStackTrace().length == 0` có nghĩa exception không thật?** Không. Stack trace có thể rỗng/tắt vì cấu hình, constructor hoặc đặc thù runtime.

**Q8. Có thể dựa vào số dòng trong stack trace làm test không?** Không nên. Refactor thêm vài dòng cũng khiến số dòng đổi; kiểm tra loại lỗi, cause/suppressed và frame có nghĩa nếu cần.

**Q9. `printStackTrace()` có hiển thị suppressed không?** Với `Throwable` chuẩn, có, bao gồm nhánh suppressed và cause. Logger custom/JSON serializer có thể trình bày khác hoặc bỏ qua nếu chưa cấu hình đúng.

**Q10. Một IOException close bị suppressed có cần xử lý không?** Có thể rất quan trọng, nhất là nếu close flush dữ liệu hoặc commit buffer. Suppressed không đồng nghĩa được phép bỏ qua.

**Q11. Nếu bọc cause mới thì có cần sao chép mọi suppressed vào wrapper?** Thường không. Cause giữ suppressed của nó; sao chép có thể tạo báo cáo trùng.

**Q12. Một lỗi trong finally có được tự động suppressed không?** Không mặc định. `try-with-resources` có quy tắc suppression đặc biệt; `finally` thủ công có thể làm lỗi trước bị thay thế nếu chính nó ném.

## 18. Sai lầm phổ biến và cách sửa

1. **Chỉ ghi `ex.getMessage()`:** mất stack/cause/suppressed. → Truyền Throwable tới logger.
2. **Bọc lỗi không có cause:** tạo exception mới nhưng không có `ex`. → Dùng constructor nhận `Throwable cause`.
3. **Đồng nhất suppressed với cause:** tưởng close failure là lỗi gốc. → Xem mối quan hệ primary/secondary.
4. **Chỉ gọi `outer.getSuppressed()`:** bỏ qua suppressed trên cause. → Kiểm tra đúng node Throwable.
5. **Phân tích text `printStackTrace()` làm dữ liệu nghiệp vụ:** phụ thuộc format động. → Dùng các API có kiểu và error code.
6. **Cho rằng `throw ex;` ghi thêm frame mới:** nhầm vị trí tạo và vị trí ném lại. → Hiểu vòng đời Throwable.
7. **Tạo exception sớm rồi dùng lại khắp nơi:** trace/context có thể lỗi thời. → Tạo lỗi tại vị trí có ý nghĩa, không reuse tùy tiện.
8. **Gọi `fillInStackTrace()` để “cập nhật” mọi rethrow:** có thể làm mất bằng chứng nơi lỗi gốc. → Chỉ dùng nếu có yêu cầu kỹ thuật rõ.
9. **Dùng `finally` ném lỗi close không giữ lỗi body:** làm che failure chính. → Dùng try-with-resources hoặc thiết kế suppression thủ công.
10. **Bỏ suppressed của resource:** lỗi cleanup bị vô hình. → Test thất bại cả body lẫn close.
11. **Gom mọi Throwable thành root cause duy nhất:** mất wrapper chứa context/domain. → Giữ toàn bộ chain.
12. **Log cùng exception nhiều tầng rồi ném tiếp:** nhân bản một sự cố thành nhiều ERROR. → Đặt logging boundary có trách nhiệm.
13. **Giả định future wrapper có trace worker:** trace wrapper và cause phát sinh ở ngữ cảnh khác. → Đọc cause/stack đúng node.
14. **Gắn suppressed vào exception không liên quan:** làm sai quan hệ nguyên nhân. → Chỉ gắn lỗi phụ của cùng thao tác/cleanup.
15. **Không xử lý null/empty trong utility đọc Throwable:** `getCause()` có thể null, mảng suppressed/trace có thể rỗng. → Kiểm tra và giới hạn traversal.
16. **Biến stack trace thành thông tin trả công khai:** có thể lộ code, đường dẫn, thư viện và thông tin nhạy cảm. → Trả error code/request ID an toàn; giữ trace trong kênh nội bộ.

## 19. Checklist khi điều tra exception Java

Khi mở một sự kiện log hoặc lỗi được báo từ thread/future:

1. Xác định **Throwable ngoài cùng** và operation/request/task liên quan.
2. Ghi nhận exception type và message nhưng không lấy message làm error code tự động.
3. Đọc `getCause()` để biết lỗi nào được bọc; lặp theo chain **có kiểm soát** nếu cần.
4. Ở **mỗi Throwable**, kiểm tra `getSuppressed()` — không chỉ ở wrapper.
5. Ở mỗi node quan trọng, xem `getStackTrace()` để định vị nơi Throwable được tạo.
6. Phân biệt **thất bại thân try** và **thất bại close/cleanup**.
7. Nếu lỗi đi qua `Future.get()`/`join()`, tách wrapper của completion khỏi lỗi task.
8. Nếu thấy nhiều suppressed, kiểm tra số resource, thứ tự close và operation phụ đã thất bại.
9. Kiểm tra contract exception qua tầng ứng dụng: có bị đổi kiểu, bỏ cause hoặc copy message không?
10. Không kết luận rollback hay resource cleanup hoàn tất chỉ vì exception được ném và suppressed.
11. Khi cần log, giữ Throwable đầy đủ, bổ sung operation, error code và request/business ID được phép ghi.
12. Kiểm thử đúng type/cause/suppressed, không hard-code text stack trace hay line number.

## 20. Kết luận

Bảng phân biệt **cause, suppressed và stack trace** tóm gọn trong ba câu:

- **Cause** (`getCause()`): *Lỗi nào được gắn làm nguyên nhân của lỗi hiện tại?*
- **Suppressed** (`getSuppressed()`): *Những lỗi phụ nào được giữ lại cùng lỗi chính, đặc biệt khi cleanup/close thất bại?*
- **Stack trace** (`getStackTrace()`): *Dấu vết những lời gọi được ghi nhận cho chính đối tượng Throwable này là gì?*

Ba thông tin không thay thế lẫn nhau. Một outer exception có thể có cause, cause ấy có suppressed, và cả ba Throwable đều có stack trace riêng. Viết Java đúng là **bảo toàn cause khi bọc lỗi, tôn trọng suppression trong try-with-resources, và dùng stack trace để điều tra chứ không làm dữ liệu nghiệp vụ**.

### Tài liệu tham khảo

- [Java SE 17 — Throwable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html)
- [Java SE 17 — StackTraceElement](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StackTraceElement.html)
- [Java Language Specification `14.20.3 — try-with-resources](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.3)
- [Java SE 17 — AutoCloseable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/AutoCloseable.html)
- [Java SE 17 — ExecutionException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutionException.html)
- [Java SE 17 — CompletionException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletionException.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-reference-005">Bảng chọn cách xử lý lỗi theo ranh giới API hoặc luồng</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng chọn cách xử lý lỗi theo ranh giới API hoặc luồng

## 1. Mục đích của bảng tra cứu

Khi một lỗi xuất hiện trong Java, câu hỏi đầu tiên không nên là “viết catch ở đâu cho hết báo đỏ?”, mà là **“thành phần nào có đủ trách nhiệm và thông tin để quyết định điều gì xảy ra tiếp theo?”**. Tầng xử lý dữ liệu, service nghiệp vụ, controller, thread worker, callback, executor và caller của Future không có cùng hợp đồng. Một cách bắt lỗi phù hợp ở ranh giới này có thể làm sai nghĩa ở ranh giới khác.

README này là **bảng tra cứu quyết định xử lý exception theo ranh giới API hoặc luồng** trong Java Core. Nó giúp chọn một trong những hướng:

- **Lan truyền nguyên trạng** bằng throws hoặc throw e khi caller được phép và nên quyết định.
- **Chuyển đổi có cause** thành exception thuộc abstraction cao hơn khi ranh giới API yêu cầu.
- **Phục hồi bằng fallback** nếu có kết quả thay thế thật sự hợp lệ theo hợp đồng.
- **Dừng tác vụ, giữ interrupt flag** khi một callback không được phép ném InterruptedException.
- **Hủy hoặc ngừng chờ** theo chính sách riêng, không đánh đồng timeout, interruption và cancellation.
- **Dọn tài nguyên / rollback** theo ownership, đồng thời giữ primary và suppressed exceptions.
- **Ghi log, trả kết quả lỗi hoặc thông báo ra ngoài** ở boundary có trách nhiệm, không log lặp ở mọi lớp.

Bảng này là **khung lựa chọn**, không phải luật bắt buộc mọi hệ thống phải sử dụng một loại exception hoặc mã trạng thái cụ thể. Ví dụ HTTP status 400/503 trong chương trình là cách ánh xạ minh họa, không phải một quy tắc tự động của Java.

**Quy tắc định hướng:** Tầng trung gian thường nên **bảo toàn thông tin lỗi và chuyển nó tới tầng có thể quyết định**. Tầng sở hữu vòng đời request, job hay task mới quyết định dừng, retry, fallback, trả response hoặc chuyển thành cảnh báo.

## 2. Xác định ranh giới trước khi quyết định catch

Một **ranh giới** là nơi trách nhiệm hoặc hợp đồng thay đổi. Có thể là:

| Ranh giới | Ai đang gọi ai? | Điều bên nhận thực sự có thể quyết định |
| --- | --- | --- |
| Phương thức nội bộ | Helper → helper khác | Kiểm tra tiền điều kiện, cung cấp context, hoặc ném tiếp |
| Adapter I/O | Repository → filesystem/network/database | Chuyển lỗi I/O thành hợp đồng adapter, quản lý resource thuộc sở hữu |
| Service | Controller/job → business service | Quy tắc nghiệp vụ, tính nguyên tử, có phục hồi được hay không |
| API công khai | Client → public API | Loại exception cam kết hoặc kiểu kết quả lỗi ổn định |
| HTTP controller | Remote caller → endpoint | Mã phản hồi, nội dung lỗi an toàn, correlation ID |
| Runnable | Executor/thread → Runnable.run | Không thể khai báo checked exception trên run |
| Callable | Executor → Callable.call | Có thể ném Exception; executor có thể lưu lỗi trong Future |
| Future.get | Thread caller → Future | Chờ kết quả; thấy lỗi tác vụ qua ExecutionException |
| CompletableFuture.join | Thread caller → CompletionStage | Thấy exceptional completion qua CompletionException thông thường |
| Callback/event handler | Framework → callback | Tuân thủ chữ ký framework, tránh làm hỏng event loop |
| Thread boundary | Worker ↔ supervisor | Quan sát dừng, interruption, uncaught exception và vòng đời worker |
| Resource boundary | Chủ sở hữu → AutoCloseable | Đóng đúng lúc, bảo toàn lỗi chính/lỗi cleanup |
| Transaction boundary | Unit of work → database | Commit/rollback/compensation theo hợp đồng |
| Service-to-service | Ứng dụng → dependency | Deadline, retry có giới hạn, idempotency, lỗi hệ ngoài |

Một đoạn **catch** chỉ nên xuất hiện khi tầng đó làm được điều hữu ích: phục hồi đúng, chuyển abstraction, cleanup có trách nhiệm hoặc đưa lỗi tới hệ thống điều phối. **Catch rồi tiếp tục như chưa có gì xảy ra** không phải là lựa chọn mặc định.

## 3. Bảng quyết định nhanh — chọn cách xử lý theo tình huống

| Tình huống | Ranh giới chịu trách nhiệm | Lựa chọn ưu tiên | Tránh |
| --- | --- | --- | --- |
| Helper nhận input sai | Phương thức/API sở hữu validation | Ném loại lỗi đầu vào theo hợp đồng, thường IllegalArgumentException hoặc subtype | Ném lỗi I/O không liên quan; che dữ liệu sai bằng null |
| Caller gọi helper có thể throws IOException | Caller/tầng trên được phép nhận checked exception | Để IOException truyền lên nếu chưa thể quyết định | Catch rồi chỉ printStackTrace và trả thành công |
| Repository phát sinh lỗi tầng lưu trữ | Adapter/service boundary | Giữ IOException/SQLException hoặc wrap thành domain/infrastructure exception **có cause** theo contract | Tạo RuntimeException chung không cause |
| Service có fallback thực sự tương đương | Service được quyền recovery | Trả fallback hợp lệ và ghi nhận degraded mode khi cần | Fallback cho mọi lỗi, kể cả lỗi toàn vẹn dữ liệu |
| Service vi phạm invariant | Business boundary | Ném loại exception thể hiện trạng thái sai | Retry vô hạn một điều kiện không thể tự sửa |
| Controller nhận input sai | Controller/API boundary | Chuyển thành response lỗi client theo quy tắc API | Trả status thành công hoặc lộ stack trace |
| Dependency tạm thời không sẵn sàng | Boundary có policy retry | Retry có giới hạn **nếu thao tác an toàn/idempotent**, hoặc trả lỗi availability | Retry tất cả Throwable |
| Runnable gặp InterruptedException | Runnable.run không khai báo checked exception | Khôi phục Thread.currentThread().interrupt() và thoát theo contract | Nuốt interruption rồi tiếp tục vòng chờ |
| Method được phép throws InterruptedException | Tầng caller biết cách xử lý | Lan truyền nguyên trạng | Khôi phục cờ rồi tiếp tục blocking không có lý do |
| Callable ném exception | Callable.call có throws Exception | Để exception truyền ra nếu executor quản lý kết quả | Giả định caller Future.get nhận lỗi worker trực tiếp |
| Future.get báo ExecutionException | Thread caller đọc task | Xem getCause(), quyết định ở caller | Coi chính Future.get là nơi bug gốc |
| Future.get bị InterruptedException | Thread caller bị ngắt khi chờ | Lan truyền hoặc khôi phục flag tại boundary phù hợp | Coi task chắc chắn đã bị hủy |
| Future.get(timeout) báo TimeoutException | Caller hết hạn chờ | Ngừng chờ/áp dụng deadline, có thể cân nhắc cancel theo ownership | Tưởng timeout tự cancel task |
| CompletableFuture.join báo CompletionException | Consumer stage | Xem cause, phân biệt lỗi có thể recover | Bắt mỗi CompletionException và bỏ qua cancellation |
| Future đã canceled | Caller/worker boundary | Xử lý CancellationException và trạng thái cancel riêng | Khẳng định worker đã dừng hoặc rollback |
| try-with-resources body và close cùng lỗi | Resource boundary | Giữ lỗi body primary, close lỗi suppressed | Để finally close che lỗi body |
| Transaction thất bại | Chủ sở hữu transaction | Rollback/compensation phù hợp, giữ lỗi rollback | Cho rằng close tự rollback mọi thay đổi |
| Uncaught exception ở worker thread | Thread/supervisor boundary | Quan sát, đánh dấu job thất bại, quản lý worker lifecycle | Dựa vào main thread để luôn thấy lỗi worker |
| Error nghiêm trọng | Boundary chẩn đoán/vận hành | Để propagate và cleanup cần thiết khi an toàn | Catch(Throwable) rồi trả fallback như lỗi thường |

**Đọc bảng theo thứ tự:** xác định **tình huống thật** → xác định **ai sở hữu công việc/tài nguyên** → chọn **hợp đồng lỗi** → xác định **có thể recover không** → bảo toàn **cause/interrupt/state** → đặt test kiểm chứng.

Bảng là định hướng theo Java 17+, không bảo đảm hành vi giống hệt khi dùng framework với policy riêng. Ví dụ có executor bắt exception, HTTP mapper tùy chỉnh hoặc API trả Result thay vì ném lỗi; khi đó ưu tiên contract chính thức của ứng dụng.

## 4. Sơ đồ quyết định bằng câu hỏi

Khi gặp một exception, tự hỏi tuần tự:

**Câu 1 — Đây là điều kiện bình thường có thể trả về kết quả không?** Nếu dữ liệu “không tìm thấy” là một trạng thái dự kiến, có thể dùng Optional/Result hoặc giá trị có ngữ nghĩa. Không cần tạo exception chỉ để biểu diễn mọi nhánh điều khiển.

**Câu 2 — Tầng này có quyền và khả năng recovery không?** Nếu biết một fallback hợp lệ, retry có kiểm soát hoặc một bước bù trừ đúng, có thể xử lý tại đây. Nếu không, truyền lỗi tới caller.

**Câu 3 — Lỗi có đổi abstraction không?** Ở một API công khai, không nhất thiết lộ loại SQLException/provider cụ thể. Có thể wrap thành exception mức domain hoặc infrastructure **nhưng phải giữ cause**.

**Câu 4 — Chữ ký có cho phép checked exception không?** Method bình thường có thể khai báo throws; Runnable.run và nhiều callback thì không. Khi không thể truyền trực tiếp, phải có policy chuyển đổi hoặc kết thúc rõ ràng.

**Câu 5 — Có liên quan timeout, interrupt hoặc cancel không?** Ba sự kiện này khác nhau. Timeout của lần chờ không chứng minh task kết thúc. Interruption thuộc thread đang chờ. Cancellation thuộc trạng thái Future hoặc cơ chế hủy tác vụ.

**Câu 6 — Có tài nguyên hoặc side effect nào cần dọn không?** Cleanup và rollback phải do owner xử lý; exception được lan truyền không tự đảo ngược trạng thái.

**Câu 7 — Ai log và ai báo kết quả cuối?** Tầng ở boundary có thể log một lần, trả API response, hoàn tất Future bất thường hoặc báo job status. Không log trùng chỉ vì mỗi tầng có catch.

Nếu trả lời “không biết” ở câu 2 hoặc 6, **đừng tự biến exception thành thành công**. Giữ thông tin nguyên nhân và để tầng có quyền quyết định xử lý.

## 5. Cách chọn giữa throws, throw e, wrap và fallback

| Cách làm | Khi phù hợp | Điều cần giữ |
| --- | --- | --- |
| Khai báo **throws IOException** | Caller có thể xử lý lỗi I/O; abstraction cho phép | Kiểu checked exception và stack trace ban đầu |
| Không catch, để exception tự lan truyền | Tầng trung gian không bổ sung policy | Không làm thay đổi Throwable |
| **catch(E e) { throw e; }** | Cần xử lý ngắn trước khi lan truyền | Chính object e và lỗi cleanup nếu có |
| **throw new DomainException(..., e)** | Ranh giới đổi từ lỗi hạ tầng sang nghiệp vụ | Cause, context phù hợp và contract mới |
| **return fallback** | Tầng có kết quả thay thế thật sự hợp lệ | Phân loại lỗi, ghi nhận degraded behavior khi cần |
| **completeExceptionally(e)** | API CompletableFuture chủ động biểu diễn lỗi | Throwable chính và semantics completion |
| **Thread.currentThread().interrupt(); return;** | Boundary không throws InterruptedException và policy là dừng | Interrupt flag và cleanup |
| **return Result.failure(code)** | API được thiết kế để biểu diễn thất bại bằng giá trị | Error code có cấu trúc, thông tin cho caller |
| **retry bounded** | Lỗi tạm thời, thao tác có thể retry an toàn | Budget, deadline, idempotency và nguyên nhân cuối |
| **try-with-resources** | Resource thuộc scope và AutoCloseable | Close trên mọi đường thoát, primary/suppressed |

Không nên **wrap một exception nhiều lần ở mọi tầng** nếu wrapper không thêm giá trị. Mỗi wrapper có thể khiến cây cause phức tạp và log khó đọc. Chỉ wrap ở nơi abstraction thật sự thay đổi hoặc caller cần thông tin ngữ cảnh mới.

Nếu wrapper có cause, caller cần kiểm tra **getCause()**. Việc chỉ ném **new RuntimeException(e.getMessage())** làm mất object lỗi gốc và stack trace/cause của nó.

## 6. Checked và unchecked theo ranh giới API

**Checked exceptions** như IOException, SQLException, InterruptedException, ExecutionException và TimeoutException yêu cầu caller catch hoặc khai báo theo quy tắc compiler.

**Unchecked exceptions** gồm RuntimeException và subclass (IllegalArgumentException, IllegalStateException, NumberFormatException, CancellationException, CompletionException), cùng Error. Compiler không buộc caller xử lý, nhưng điều đó không khiến lỗi tự biến mất.

Bảng chọn:

| Loại API | Thiết kế thường gặp | Lưu ý |
| --- | --- | --- |
| API Java Core có thao tác blocking interruptible | Khai báo **throws InterruptedException** | Cho caller quyền quyết định hủy/chờ |
| API đọc/ghi file | **throws IOException** hoặc kết quả lỗi rõ ràng | Đừng che I/O thành sai tham số |
| Helper validation | **IllegalArgumentException**/subtype | Phân biệt input sai với lỗi hệ thống |
| Phương thức có invariant bắt buộc | **IllegalStateException** hoặc loại riêng | Không tự đổi thành fallback mơ hồ |
| Public library API nhiều implementation | Loại exception ổn định theo contract | Đừng ép caller phụ thuộc exception vendor |
| Framework callback không checked throws | Kết thúc/khôi phục interrupt, hoặc unchecked wrapper khi contract cho phép | Không thêm throws vào method override trái chữ ký |
| Async API trả CompletableFuture | Exceptional completion/cancellation state | Consumer cần quan sát bằng get/join/handler |
| API hướng data với Result | Trả mã lỗi/thông tin cấu trúc | Đừng vừa trả success vừa báo thất bại không rõ ràng |

**Đừng phân loại bằng catch(Exception) chung rồi quyết định mọi lỗi đều retry.** Tính checked/unchecked chỉ là một chiều; điều quan trọng hơn là **nghĩa của exception trong hợp đồng cụ thể**.

## 7. Ranh giới đồng bộ: helper → repository → service → API

Một đường đi đơn giản:

~~~text
Caller / Controller
       |
       v
Application Service
       |
       v
Repository / Adapter
       |
       v
I/O, filesystem, network, database
~~~

Ở tầng repository, lỗi I/O có thể được truyền bằng IOException hoặc map sang infrastructure exception công khai của adapter. Ở service, nếu nghiệp vụ không thể tiếp tục, nên giữ nguyên lỗi hoặc thêm context cần thiết. Ở controller, có thể chuyển nó thành response lỗi bên ngoài.

Ví dụ minh họa:

~~~java
// Mã minh họa theo ngữ cảnh Java.
static String repository(String key) throws IOException {
    if (key.equals("down")) {
        throw new IOException("storage unavailable");
    }
    return "value:" + key;
}

static String service(String raw) throws IOException {
    if (raw == null || raw.isBlank()) {
        throw new IllegalArgumentException("id required");
    }
    return repository(raw.trim());
}
~~~

Ở đây **service** không catch IOException vì ví dụ cho caller quyền quyết định. Khi caller là một boundary HTTP, nó có thể map IllegalArgumentException thành lỗi client và IOException thành lỗi availability.

**Cảnh báo về mã trạng thái:** HTTP status và payload phải dựa vào quy tắc API, bảo mật, semantics request và chính sách system. Không có một quy tắc Java Core rằng mọi IOException là 503 hoặc mọi IllegalArgumentException là 400. Trong ví dụ runnable kế tiếp, các con số chỉ giúp thấy *lựa chọn nằm ở boundary*.

## 8. Ví dụ Java 17+ hoàn chỉnh: các boundary và kết quả cụ thể

Chương trình sau dùng Java SE để tạo những trường hợp có thể kiểm tra độc lập:

1. Request boundary minh họa ánh xạ input sai và dependency không sẵn sàng.
2. Resource boundary giữ exception của công việc làm primary, close error làm suppressed.
3. Runnable boundary khôi phục interrupt flag và kết thúc.
4. Callable ném IOException trong executor; caller đọc Future nhận ExecutionException có cause.
5. CompletableFuture hoàn tất bất thường: **get** và **join** trả wrapper khác nhau nhưng cùng cause.
6. Canceled Future báo CancellationException; một lần timed get báo TimeoutException nhưng task chưa tự canceled.

Lưu file **ErrorBoundaryDecisionDemo.java**:

~~~java
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

public class ErrorBoundaryDecisionDemo {
    static String repository(String key) throws IOException {
        if (key.equals("down")) {
            throw new IOException("storage unavailable");
        }
        return "value:" + key;
    }

    static String service(String raw) throws IOException {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("id required");
        }
        return repository(raw.trim());
    }

    static String requestBoundary(String raw) {
        try {
            return "200 " + service(raw);
        } catch (IllegalArgumentException badInput) {
            return "400 " + badInput.getMessage();
        } catch (IOException unavailable) {
            return "503 " + unavailable.getMessage();
        }
    }

    static final class BrokenClose implements AutoCloseable {
        @Override
        public void close() throws IOException {
            throw new IOException("close failed");
        }
    }

    static void transactionLikeWork() throws IOException {
        try (BrokenClose resource = new BrokenClose()) {
            throw new IOException("work failed");
        }
    }

    static void interruptBoundary() throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch gate = new CountDownLatch(1);
        AtomicBoolean restored = new AtomicBoolean(false);

        Thread worker = new Thread(() -> {
            try {
                ready.countDown();
                gate.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                restored.set(Thread.currentThread().isInterrupted());
            }
        });
        worker.start();
        ready.await();
        worker.interrupt();
        worker.join();
        System.out.println("runnable restored=" + restored.get());
    }

    static void futureBoundary() throws InterruptedException {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<String> task = pool.submit(() -> {
                throw new IOException("task I/O");
            });
            try {
                task.get();
            } catch (ExecutionException failure) {
                System.out.println("future cause="
                        + failure.getCause().getClass().getSimpleName());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    static void completionBoundary() throws InterruptedException {
        CompletableFuture<String> failed = new CompletableFuture<>();
        IOException original = new IOException("async I/O");
        failed.completeExceptionally(original);
        try {
            failed.get();
        } catch (ExecutionException wrapper) {
            System.out.println("get cause original="
                    + (wrapper.getCause() == original));
        }
        try {
            failed.join();
        } catch (CompletionException wrapper) {
            System.out.println("join cause original="
                    + (wrapper.getCause() == original));
        }

        CompletableFuture<String> canceled = new CompletableFuture<>();
        canceled.cancel(false);
        try {
            canceled.join();
        } catch (CancellationException failure) {
            System.out.println("canceled=" + failure.getClass().getSimpleName());
        }

        CompletableFuture<String> pending = new CompletableFuture<>();
        try {
            pending.get(1, TimeUnit.MILLISECONDS);
        } catch (ExecutionException failure) {
            throw new AssertionError("Unexpected completion", failure);
        } catch (TimeoutException expected) {
            System.out.println("timed wait=" + expected.getClass().getSimpleName());
        }
        pending.complete("later");
        System.out.println("after timeout=" + pending.join());
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("request ok=" + requestBoundary(" 42 "));
        System.out.println("request invalid=" + requestBoundary(" "));
        System.out.println("request storage=" + requestBoundary("down"));
        try {
            transactionLikeWork();
        } catch (IOException primary) {
            System.out.println("primary=" + primary.getMessage());
            System.out.println("suppressed="
                    + primary.getSuppressed()[0].getMessage());
        }
        interruptBoundary();
        futureBoundary();
        completionBoundary();
    }
}
~~~

Biên dịch và chạy JDK 17 trở lên:

~~~bash
javac --release 17 ErrorBoundaryDecisionDemo.java
java ErrorBoundaryDecisionDemo
~~~

Stdout đã được kiểm tra khi biên dịch/chạy:

~~~text
request ok=200 value:42
request invalid=400 id required
request storage=503 storage unavailable
primary=work failed
suppressed=close failed
runnable restored=true
future cause=IOException
get cause original=true
join cause original=true
canceled=CancellationException
timed wait=TimeoutException
after timeout=later
~~~

**Lưu ý về chương trình:** Không có HTTP server thật; chuỗi **200/400/503** là kết quả minh họa cho cách một boundary có thể map lỗi theo chính sách. **transactionLikeWork** chỉ mô phỏng một phạm vi tài nguyên, không triển khai transaction hay rollback thật. Cả hai được đặt tên để gợi nhớ responsibility boundary, không phải tuyên bố framework tự xử lý như vậy.

## 9. Diễn giải kết quả qua ranh giới

**request ok:** Service trả kết quả bình thường, boundary thêm tiền tố trạng thái thành công minh họa.

**request invalid:** Input rỗng khiến service ném IllegalArgumentException; boundary **có đủ thông tin** để chuyển thành trạng thái client error. Không để stack trace Java hiển thị như payload công khai.

**request storage:** Repository ném IOException. Service để lỗi truyền lên, boundary minh họa response 503. Trong ứng dụng thực, mapper phải xem lỗi có thực sự là temporary unavailability hay không; cùng IOException có thể do path sai, permission, disk failure hoặc dependency không sẵn sàng.

**primary/suppressed:** TWR giữ IOException **work failed** làm lỗi chính, còn **close failed** là suppressed. Nếu cuối cùng log ở controller chỉ ghi message primary mà không truyền Throwable, phần lỗi cleanup bị mất.

**runnable restored=true:** Worker đang chờ CountDownLatch nhận interrupt. Vì Runnable.run không được khai báo checked InterruptedException, catch đã khôi phục cờ rồi kết thúc. Main dùng join để quan sát kết quả sau khi worker đã kết thúc. Trong production, cần cleanup phù hợp với task thật.

**future cause=IOException:** Callable trong executor ném IOException. ExecutorService.submit giữ kết quả lỗi trong Future, và caller của Future.get bắt ExecutionException để tìm IOException trong cause.

**get/join cause original=true:** Hai API quan sát cùng CompletableFuture đã completeExceptionally bằng một IOException. get báo ExecutionException, join báo CompletionException, nhưng cause là cùng object IOException ban đầu.

**canceled:** Future đã bị canceled trực tiếp. join ném CancellationException; không thể kết luận từ dòng này worker nào đã dừng, bởi CompletableFuture ví dụ không có worker thực sự.

**timed wait/after timeout:** get có timeout chờ một CompletableFuture chưa được hoàn tất. TimeoutException chỉ làm lượt chờ kết thúc; sau đó **complete("later")** vẫn thành công và join nhận kết quả.

Các tình huống được dựng có điều khiển: latch và trạng thái Future rõ ràng, không dựa vào Thread.sleep ngẫu nhiên để hy vọng một race xảy ra.

## 10. Bảng chọn: Java method thường và API library

| Điều kiện | Hợp đồng method nên thể hiện | Xử lý ở tầng đang xét |
| --- | --- | --- |
| Input không hợp lệ | Exception input được định nghĩa rõ hoặc Result.failure | Validate sớm, message an toàn, test đúng subtype nếu cần |
| Method có thể bị ngắt khi chờ | throws InterruptedException khi hợp lệ | Để caller quyết định dừng/hủy |
| I/O thất bại | throws IOException hoặc lỗi infrastructure có cause | Không tự biến thành null nếu null có ý nghĩa bình thường |
| Invariant trạng thái bị vi phạm | IllegalStateException / loại domain riêng | Không retry mù quáng |
| Một operation optional có thể thiếu dữ liệu | Optional/result nếu missing là bình thường | Không dùng exception làm đường lặp phổ biến khi không cần |
| Code có thể thay đổi state trước khi lỗi | Contract exception safety/transaction | Chuẩn bị trước khi công bố, rollback nếu được bảo đảm |
| API công khai không muốn lộ vendor exception | Wrapper thuộc abstraction public | Giữ original trong cause, không mất stack trace |

**Lan truyền checked exception** thường hợp lý khi tầng trung gian chưa có đủ ngữ cảnh. Ngược lại, API public cần một vocabulary lỗi ổn định có thể wrap lỗi từ thư viện dưới, nhưng phải giữ nguyên nhân và tài liệu hóa nghĩa.

**Không nên ném RuntimeException chung chỉ để khỏi khai báo throws.** Đây là quyết định thiết kế hợp đồng, không phải cách né compiler. Một RuntimeException không có cause có thể khiến caller và hệ thống log mất thông tin quan trọng.

## 11. Bảng chọn: Throwable type cho kết quả lỗi

| Loại được quan sát | Thường mô tả | Hướng xử lý |
| --- | --- | --- |
| IllegalArgumentException | Caller vi phạm tiền điều kiện | Sửa input, trả lỗi validation tại API boundary |
| NumberFormatException | Chuỗi chuyển số không hợp lệ | Nêu lỗi định dạng; không coi là lỗi hạ tầng |
| IllegalStateException | Trạng thái object không cho phép thao tác | Xem invariant/lifecycle; sửa logic hoặc từ chối hợp lệ |
| IOException | Lỗi I/O theo hợp đồng API | Phân loại transient/permanent dựa trên cause và ngữ cảnh |
| SQLException | Lỗi theo JDBC/database contract | Chính sách transaction, timeout, retry dựa trên SQLState/provider |
| InterruptedException | Thread caller/worker đang chờ nhận interrupt | Lan truyền khi được phép hoặc khôi phục cờ tại boundary |
| TimeoutException | Lượt chờ quá hạn hoặc lỗi timeout được biểu diễn | Xem deadline và task ownership, phân biệt wrapper |
| ExecutionException | Future.get báo lỗi task | Phân tích getCause() |
| CompletionException | CompletionStage/join truyền exceptional completion | Phân tích getCause() |
| CancellationException | Future đã canceled hoặc completion bị hủy | Phân biệt cancellation với lỗi nghiệp vụ |
| Error | Điều kiện lỗi nghiêm trọng của runtime | Tránh catch thành fallback; cleanup an toàn khi thích hợp |

Bảng này không thay thế API contract. Ví dụ một **TimeoutException** có thể là exception trực tiếp từ get(timeout) hoặc là **cause** của ExecutionException khi CompletableFuture bị **orTimeout** hoàn tất bất thường. Cùng một class có thể xuất hiện ở hai boundary với ý nghĩa xử lý khác nhau.

## 12. Ranh giới Runnable: không được ném checked exception

**Runnable.run()** có chữ ký không khai báo checked exception:

~~~java
// Chữ ký khái niệm của Runnable.
void run();
~~~

Một lambda Runnable muốn gọi method throws InterruptedException cần bắt exception hoặc chuyển đổi theo contract. Policy phổ biến khi interruption mang nghĩa yêu cầu dừng:

~~~java
// Mã minh họa bên trong một Runnable.
try {
    blockingOperation();
} catch (InterruptedException interrupted) {
    Thread.currentThread().interrupt();
    return;
}
~~~

**Tại sao khôi phục cờ?** Nhiều blocking APIs như CountDownLatch.await() xóa trạng thái interrupt khi ném InterruptedException. Nếu không thể truyền checked exception qua run, khôi phục cờ giúp giữ tín hiệu để code điều phối hoặc các tầng có quyền quan sát sau đó.

**Không khôi phục cờ rồi tiếp tục một vòng lặp blocking vô hạn** trừ khi có chính sách cụ thể; các blocking calls tiếp theo có thể ném ngay hoặc task tiếp tục hoạt động trái yêu cầu dừng. Catch rồi bỏ qua làm mất signal và có thể khiến shutdown bị kẹt.

Nếu callback không thể return theo thiết kế, có thể dùng adapter hoặc unchecked wrapper có cause theo contract framework. Tuy nhiên, đừng dùng **IllegalStateException** như một “loại cancellation chuẩn” không có tài liệu; cơ chế hủy của framework có thể có kiểu/semantics riêng.

## 13. Ranh giới Callable, ExecutorService và lỗi task

**Callable<V>.call()** khai báo **throws Exception**, nên một Callable có thể truyền checked IOException hoặc InterruptedException ra khỏi call.

~~~java
// Mã minh họa với ExecutorService pool đã có.
Future<String> future = pool.submit(() -> {
    throw new IOException("remote failure");
});
~~~

Khi task đã hoàn tất bất thường, caller quan sát bằng **future.get()** thường nhận **ExecutionException** chứa IOException trong **getCause()**. Không phải chính caller nhận IOException trực tiếp qua chữ ký Future.get.

| API khởi chạy | Nơi lỗi được quan sát thường gặp | Lưu ý |
| --- | --- | --- |
| Thread.start với Runnable | Uncaught exception handler nếu lỗi thoát khỏi run | Không trả Future tự động |
| ExecutorService.execute(Runnable) | Theo executor/thread factory/uncaught handler/logging policy | Không cung cấp Future từ execute |
| ExecutorService.submit(Runnable) | Future.get báo ExecutionException khi task ném lỗi | Lỗi có thể được capture, không đi tới uncaught handler theo cách người viết mong |
| ExecutorService.submit(Callable) | Future.get báo ExecutionException với cause từ Callable | Callable được phép throws Exception |
| invokeAll | Danh sách Future; cần xem get trên từng Future | Không suy ra tất cả task thành công chỉ vì invokeAll trả list |
| invokeAny | Trả kết quả của một task thành công; có exception nếu không đạt contract | Cần đọc semantics và cancellation của các task còn lại |

**Không dựa vào Thread.UncaughtExceptionHandler như cơ chế duy nhất bắt lỗi của task submit.** Executor thường ghi nhận lỗi vào Future. Nếu caller không bao giờ gọi get hay có callback quan sát, lỗi có thể bị bỏ qua về mặt vận hành dù Future đã hoàn tất bất thường.

Tương tự, **execute** không cam kết có cùng cách báo lỗi với **submit**. Một implementation có thể ghi log, chuyển cho handler hoặc quản lý thread riêng. Khi chọn chính sách, cần xác định executor implementation, thread factory, error handler và nơi caller thực sự quan sát kết quả.

## 14. Bảng chọn: Future.get và thread caller

| Tình huống | get / get(timeout) biểu hiện | Thao tác tại caller |
| --- | --- | --- |
| Task hoàn tất bình thường | Trả T | Xử lý kết quả |
| Task ném lỗi thông thường | ExecutionException | Kiểm tra getCause; quyết định recovery hoặc báo thất bại |
| Future đã canceled | CancellationException | Xử lý trạng thái hủy, không coi là result |
| Thread caller bị interrupt khi đang chờ | InterruptedException | Lan truyền/khôi phục cờ và kết thúc theo contract |
| get(timeout) hết thời gian | TimeoutException | Dừng chờ, tùy owner quyết định cancel hoặc trả deadline error |
| Future chưa hoàn tất nhưng caller ngừng chờ | Task có thể vẫn chạy | Không suy ra side effects đã dừng |

**Hai hướng interruption phải tách biệt:**

1. **Caller bị interrupt trong future.get()** → chính method get có thể ném InterruptedException trực tiếp.
2. **Callable của worker tự ném InterruptedException** → nếu task được lưu như failure và chưa canceled, get thường ném ExecutionException với **cause là InterruptedException**.

Chỉ nhìn thấy chữ InterruptedException trong log mà không xác định object nào ném nó có thể dẫn đến chính sách sai. Lỗi của worker không phải lúc nào cũng là yêu cầu dừng thread đang chờ kết quả.

Nếu cần deadline, hãy cân nhắc **get(timeout, unit)** và budget tổng thể. Một loạt retry có timeout riêng không đảm bảo deadline tổng thể nếu tổng thời gian vượt quá budget của request.

## 15. Bảng chọn: CompletableFuture và CompletionStage

| Cách dùng | Lỗi được biểu diễn | Policy thường gặp |
| --- | --- | --- |
| **completeExceptionally(failure)** | Future hoàn tất bất thường với Throwable | Tầng tạo Future báo lỗi chủ động |
| **CompletableFuture.get()** | ExecutionException khi exceptional completion thông thường | Caller checked exception; lấy cause |
| **CompletableFuture.join()** | CompletionException khi exceptional completion thông thường | Caller unchecked exception; lấy cause |
| **cancel(...)** trên Future gốc | CancellationException khi quan sát canceled Future | Không khẳng định computation chắc chắn bị ngắt |
| **exceptionally(fn)** | Có thể chuyển failure thành fallback stage | Chỉ recovery loại lỗi được phép |
| **handle((value,error) -> ...)** | Chạy khi success hoặc failure | Phân biệt value/error, tránh vô tình nuốt cancellation |
| **whenComplete((value,error) -> ...)** | Callback quan sát, thường không recovery giá trị | Không để logging ném exception mới khi không cần |
| **orTimeout(...)** | Có thể hoàn tất chính Future bất thường với TimeoutException | Phân biệt với get(timeout) chỉ hết lượt chờ |
| **completeOnTimeout(value,...)** | Có thể hoàn tất chính Future bình thường bằng fallback | Không khẳng định supplier bên dưới đã dừng |
| **allOf(...)** | Future tổng hợp hoàn tất khi các Future tương ứng hoàn tất | Khi cần kết quả từng phần, quan sát từng Future |

**Quan trọng:** **CompletableFuture.cancel(true)** không có cùng bảo đảm interrupt computation như cơ chế cancellation của FutureTask; theo hợp đồng CompletableFuture, tham số mayInterruptIfRunning không được dùng để điều khiển xử lý. Muốn dừng tác vụ thật sự, cần thiết kế cơ chế cooperative cancellation riêng hoặc quản lý Future/task thực thi bên dưới.

Với **get()**, checked exception của caller có thể là InterruptedException hoặc ExecutionException; với **join()**, không có throws checked InterruptedException trong chữ ký. Không chọn join chỉ để tránh phải nghĩ về cancellation hoặc failure.

### Quan sát lỗi ở stage đúng vị trí

Một dependent stage có thể nhận exceptional completion do upstream hủy và bọc CancellationException trong CompletionException. Vì vậy, **catch(CancellationException)** tại Future gốc và quan sát một downstream stage là hai ngữ cảnh khác nhau. Khi debug, xem **đúng Future** và quan hệ cause trước khi chọn branch.

## 16. Timeout, interruption và cancellation không được đánh đồng

Ba khái niệm thường xuất hiện cùng nhau nhưng khác semantics:

**Timeout:** Một lượt chờ hoặc operation vượt thời gian đã định. Với **Future.get(timeout)**, caller ngừng chờ nhưng Future chưa chắc completed hoặc canceled. Nếu áp dụng **CompletableFuture.orTimeout**, Future có thể được hoàn tất bất thường với TimeoutException; đây là một trạng thái completion khác.

**Interruption:** Một thread đã nhận yêu cầu ngắt. API blocking interruptible có thể ném InterruptedException và xóa flag theo hợp đồng. Caller/callback phải quyết định lan truyền hay khôi phục cờ.

**Cancellation:** Một Future hoặc tác vụ bị yêu cầu hủy. Future có thể chuyển trạng thái canceled trước khi worker thực sự dừng hoặc trước khi các side effects kết thúc. **cancel(true)** chỉ có thể yêu cầu interrupt trong những implementation thích hợp; nó không phải lệnh rollback.

Bảng quyết định:

| Quan sát | Không được suy ra | Hướng cần làm rõ |
| --- | --- | --- |
| get(timeout) ném TimeoutException | Task đã ngừng | Deadline, owner của Future, có cancel được không |
| Thread đang chờ ném InterruptedException | Future đã canceled | Caller có quyền hủy Future hay không |
| Future.isCancelled() true | Worker chắc chắn đã dừng | Cơ chế task có hợp tác với cancellation không |
| CompletableFuture orTimeout hoàn tất bất thường | Supplier bị interrupt | Cách chấm dứt công việc bên dưới |
| Future.get ném ExecutionException | Caller đang bị interrupt | Cause thực tế thuộc worker nào |

Không dùng “timeout” làm một lý do mặc định để retry mọi thao tác có side effect. Nếu server đã xử lý request nhưng caller chưa nhận response, retry có thể tạo giao dịch trùng. Dùng idempotency key và chính sách at-least-once/at-most-once phù hợp.

## 17. Chọn nơi recovery: fallback không phải luôn là thành công

Một fallback hợp lệ phải trả lời được: **khi dùng kết quả thay thế, những invariant và cam kết với caller có còn đúng không?**

Ví dụ hợp lý:
- Dịch vụ đọc dữ liệu không quan trọng có thể trả bản cache còn trong thời hạn chấp nhận.
- Một tính năng gợi ý optional có thể bỏ phần gợi ý nhưng giữ chức năng chính.
- Một job được phép retry sau lỗi mạng tạm thời, khi thao tác idempotent và chưa hết deadline.

Ví dụ nguy hiểm:
- Trả danh sách rỗng khi database timeout, khiến caller tưởng người dùng không có dữ liệu.
- Trả thành công sau lỗi commit, khi trạng thái transaction chưa chắc.
- Bỏ qua InterruptedException và xử lý tiếp dù supervisor yêu cầu dừng.
- Catch Throwable để chuyển mọi lỗi, kể cả OutOfMemoryError, thành fallback.
- Chuyển lỗi phân quyền thành “không tìm thấy” mà không có policy bảo mật rõ.

Một fallback nên có **semantic rõ**: dữ liệu stale được phép hay không, có đánh dấu degraded mode không, metric nào ghi nhận, và lỗi nào không được recovery.

Với CompletableFuture.exceptionally, callback nhận Throwable; nó có thể là wrapper hoặc cause tùy stage. Không chỉ kiểm tra **failure.getMessage()**. Phân loại bằng class/cause phù hợp, bảo toàn lỗi không recovery và đừng biến cancellation thành fallback nếu ứng dụng không cho phép.

## 18. Quy tắc retry theo loại lỗi và ownership

| Điều kiện | Retry? | Lý do |
| --- | --- | --- |
| Input sai ổn định | Không | Retry cùng input không tự sửa |
| Vi phạm invariant nội bộ | Không theo mặc định | Cần sửa logic hoặc state |
| Kết nối tạm thời lỗi | Có thể | Chỉ nếu retry an toàn và budget còn |
| Timeout sau request có side effect | Rất thận trọng | Kết quả từ xa có thể đã được commit |
| Deadlock/optimistic concurrency conflict | Theo contract | Có thể retry transaction từ đầu với giới hạn |
| InterruptedException từ request bị hủy | Thường dừng | Không phớt lờ yêu cầu ngắt |
| CancellationException | Thường không tự retry | Cần biết ai yêu cầu hủy |
| SQLException | Tùy SQLState/provider và transaction | Không phải mọi SQLException là transient |
| Error/JVM failure | Không retry kiểu nghiệp vụ | Có thể ảnh hưởng an toàn runtime |

Retry cần được **sở hữu ở một boundary rõ ràng**; tránh repository retry ba lần, service retry ba lần rồi HTTP gateway retry ba lần khiến tổng số thao tác tăng ngoài ý muốn.

Áp dụng hạn mức số lần, tổng deadline, backoff có jitter và idempotency khi cần. Khi retry hết, báo lại exception cuối và ngữ cảnh của các lần thử liên quan theo chính sách chẩn đoán; không chỉ “return null” hoặc bỏ cause.

Nếu một thao tác có transaction, có thể phải **bắt đầu lại một unit of work mới** thay vì tiếp tục dùng transaction/connection đã thất bại. Quyết định phụ thuộc hợp đồng API và trạng thái resource.

## 19. Bảng chọn: HTTP/controller hoặc giao diện công khai

| Trạng thái/điều kiện | Response minh họa | Không nên làm |
| --- | --- | --- |
| Input sai cú pháp hoặc vi phạm validation | 400-series theo contract | Leak stack trace/SQL/path nội bộ |
| Không được phép theo quyền truy cập | 401/403 tùy nghĩa xác thực/phân quyền | Map tùy tiện mọi lỗi thành 404 |
| Resource không tồn tại | 404 nếu semantics endpoint phù hợp | Dùng 404 cho lỗi lưu trữ không xác định |
| Conflict trạng thái | 409 nếu contract quy định | Tự retry và giấu conflict không có policy |
| Dependency tạm thời không sẵn sàng | 503 hoặc mã lỗi được API định nghĩa | Mặc định coi mọi IOException là transient |
| Hệ thống xử lý lỗi ngoài dự kiến | 500-series, correlation ID | Trả HTTP 200 với payload giả thành công |
| Caller hủy/timeout | Theo contract gateway/server | Báo chắc chắn task đã rollback |
| Fallback được cho phép | Thành công có đánh dấu degraded nếu contract cần | Trả dữ liệu thiếu mà caller không được biết |

Bảng này nhằm minh họa nguyên tắc **dịch lỗi tại ranh giới giao tiếp**, không phải đặc tả REST bắt buộc. Một ứng dụng có thể chọn error code riêng, gRPC status hoặc Result object thay vì HTTP. Ở mọi lựa chọn, đừng để consumer phải đoán từ message tiếng Anh của một exception Java nội bộ.

Nên log **Throwable đầy đủ ở bên trong** với context không nhạy cảm; trả cho client **error code ổn định, thông tin phù hợp và request/correlation ID**. Không in exception stack trace, credential hoặc raw SQL ra response công khai.

## 20. Resource boundary: close đúng cách nhưng chưa phải rollback

Khi method **sở hữu AutoCloseable**, ưu tiên:

~~~java
// Mã minh họa theo ngữ cảnh; resource implements AutoCloseable.
try (Resource resource = openResource()) {
    process(resource);
}
~~~

Nếu process ném A và close ném B, Java giữ **A làm primary** và B thành **suppressed**, khi suppression được bật. Với nhiều resource, close theo thứ tự ngược khai báo và cố gắng dọn các resource đã mở thành công ngay cả khi mở resource tiếp theo thất bại.

Ngược lại:

~~~java
// Phản mẫu.
Resource resource = openResource();
try {
    process(resource);
} finally {
    resource.close();  // Có thể che lỗi process nếu close cũng ném.
}
~~~

Một lỗi cleanup có thể thay thế lỗi gốc. TWR giúp giữ thông tin chẩn đoán đúng hơn. Trong ví dụ runnable, work failed vẫn là primary, close failed là suppressed.

**Trường hợp không dùng TWR trực tiếp:** Resource caller cho mượn, resource dùng lâu hơn method, framework sở hữu vòng đời, một callback async còn cần sử dụng resource, hoặc API chỉ cung cấp unlock/release với semantics riêng. TWR đúng cú pháp nhưng sai ownership vẫn là bug.

**Transaction:** Đóng JDBC Connection không tự cam kết transaction rollback thành công trong mọi provider/pool. Cần chính sách **commit, rollback hoặc compensation** do transaction owner quản lý. Việc addSuppressed lỗi rollback bảo toàn thông tin lỗi; nó không chứng minh rollback đã hoàn tất.

## 21. Logging, metrics và observability theo ranh giới

Nên có một điểm chịu trách nhiệm **ghi nhận kết quả cuối** của request/job/task. Các tầng thấp có thể thêm context nếu thực sự hữu ích, nhưng không nhất thiết log cùng Throwable ở mỗi catch rồi throw lại.

Khi có wrapper:

~~~java
// Mã minh họa.
throw new IllegalStateException("Operation failed", original);
~~~

Đưa original vào cause giữ được nguồn lỗi. Ở boundary ghi log, logger nên nhận **Throwable object**, không chỉ string message. Nếu log chỉ ghi **failure.getMessage()**, thông tin **Caused by**, **Suppressed** và frame nguồn có thể biến mất.

Đối với công việc async, quan sát **exceptional completion** là bắt buộc nếu hệ thống cần biết task có thất bại hay không. Một Future không được await hoặc một CompletableFuture không có handler/monitor có thể hoàn tất bất thường mà boundary bên ngoài không tự phát hiện theo cách bạn tưởng.

Các trường nên có trong structured log khi thích hợp: operation, component, stage, type và cause của Throwable, request/job ID, retry count, deadline, trạng thái cancellation, elapsed time và mã lỗi nghiệp vụ. **Không đặt password, access token, dữ liệu cá nhân hoặc raw request nhạy cảm vào message/cause/log.**

Một nguyên tắc thực hành là **thêm ngữ cảnh ở tầng biết ngữ cảnh, quyết định ở tầng có trách nhiệm, log ở tầng thực sự quan sát kết quả cuối**.

## 22. Kiểm thử bảng quyết định bằng tình huống cụ thể

Một bộ test mạnh nên kiểm tra cả exception type và hệ quả của boundary. Ma trận tham khảo:

| Tình huống cần thử | Assertion quan trọng |
| --- | --- |
| Input null/blank | Exception input đúng loại hoặc response validation đúng contract |
| Repository ném IOException | Service không nuốt; API mapping đúng policy |
| Worker Callable ném IOException | Future.get ném ExecutionException, cause là IOException |
| Caller bị interrupt trong get | InterruptedException theo hợp đồng caller; chính sách cờ interrupt đúng |
| Runnable await bị interrupt | Runnable không nuốt, khôi phục flag/dừng theo contract |
| CompletableFuture completeExceptionally | get/join có wrapper thích hợp và giữ original cause |
| Future canceled | CancellationException/ trạng thái isCancelled đúng |
| get(timeout) trên pending Future | TimeoutException, Future không tự canceled |
| Body và close cùng ném | Body primary, close suppressed |
| Mở resource thứ hai thất bại | Resource đã mở trước vẫn được dọn |
| Retry lỗi tạm thời | Số lần giới hạn, deadline, không duplicate side effect |
| Fallback hợp lệ | Value/mode phù hợp, upstream failure vẫn được ghi nhận nếu cần |
| Transaction rollback thất bại | Lỗi chính và lỗi rollback được giữ; trạng thái không bị báo thành công giả |
| Public boundary | Error code/payload không lộ thông tin nhạy cảm |
| Async task không await trực tiếp | Có cơ chế quan sát thất bại theo contract |

Đừng viết một test chỉ **assertThrows(Exception.class)** rồi kết luận đã kiểm tra đủ mọi loại lỗi. **assertThrows** trong JUnit Jupiter chấp nhận subtype; **assertThrowsExactly** yêu cầu đúng runtime class khi phiên bản JUnit hỗ trợ. Khi API cam kết một họ exception, chấp nhận subtype có thể đúng; khi contract đòi exact class, assertion quá rộng có thể tạo false positive.

Với lỗi bất đồng bộ, ưu tiên điều khiển bằng CountDownLatch, CompletableFuture đã hoàn tất hoặc executor test phù hợp. Dùng Thread.sleep với thời gian đoán trước là nguồn test không ổn định.

## 23. Các phản mẫu phổ biến cần tránh

**Catch(Exception) rồi return null.** Null có thể được hiểu là “không có dữ liệu” thay vì “I/O thất bại”, làm tầng trên đi sai nhánh.

**Catch(Throwable) rồi phục hồi.** Error nghiêm trọng bị biến thành một fallback thông thường. Cleanup có thể cần chạy trong finally, nhưng không nên mặc định nuốt Error.

**Log rồi throw ở mọi tầng.** Cùng một failure tạo nhiều bản log, khó đếm và khó xác định boundary chịu trách nhiệm.

**Wrap không cause.** Chỉ giữ message làm mất stack trace và original Throwable.

**Nuốt InterruptedException.** Worker có thể tiếp tục chờ hoặc xử lý dù supervisor yêu cầu dừng.

**Re-interrupt rồi tiếp tục blocking vô thời hạn.** Cờ interrupt được đặt lại nhưng policy dừng không rõ, có thể gây vòng lỗi hoặc chặn.

**Coi timeout là cancellation.** Một lần get(timeout) quá hạn không tự dừng Future hoặc rollback side effect.

**Cho rằng cancel(true) của mọi Future bảo đảm task dừng.** Cancellation cần hợp tác và phụ thuộc implementation; với CompletableFuture, mayInterruptIfRunning không điều khiển computation.

**Bắt mỗi CompletionException để xử lý join.** Future canceled có thể ném CancellationException trực tiếp; dependent stage có thể wrap cancellation khác.

**Dựa vào uncaught handler để thấy lỗi ExecutorService.submit.** Lỗi task có thể đã được executor ghi vào Future.

**Dùng retry ở nhiều tầng.** Tổng số lần thực thi và side effects có thể tăng không kiểm soát.

**Cho rằng close tương đương rollback.** Cleanup resource và đảm bảo transaction/data consistency là hai hợp đồng khác nhau.

**Thay mọi lỗi thành HTTP 500/503.** Mất phân biệt input, conflict, permission và dependency; contract công khai trở nên kém hữu dụng.

## 24. Mẫu điều phối lỗi ở tầng phù hợp

Một thiết kế dễ kiểm soát chia trách nhiệm:

~~~text
Request boundary
    xác thực và chuẩn hóa input
    gọi application service
    map domain/infrastructure failure → error response
    log kết quả cuối một lần
          |
          v
Application service
    kiểm tra invariant
    quản lý transaction/unit of work nếu là owner
    fallback/retry có giới hạn nếu được phép
    giữ/đổi exception theo public contract
          |
          v
Repository / adapter
    gọi I/O, network, database
    đóng resource do adapter sở hữu
    không tự quyết policy retry của toàn request nếu không được giao
          |
          v
Executor / async worker
    làm việc theo task contract
    truyền lỗi qua Future/CompletionStage
    hợp tác interruption/cancellation
    có boundary quan sát completion
~~~

Đây là **một cách chia trách nhiệm điển hình**, không phải thiết kế bắt buộc cho ứng dụng đơn luồng hay thư viện nhỏ. Điều cần giữ là mỗi side effect/exception có một chủ thể sở hữu quyết định cuối, và thông tin lỗi không bị mất giữa các tầng.

### Khi nào nên dùng kiểu Result thay vì Throwable?

Nếu thất bại là trạng thái nghiệp vụ thường xuyên và caller cần xử lý nhánh đó như dữ liệu bình thường (ví dụ tìm kiếm không có kết quả), kiểu Result/Optional có thể phù hợp hơn exception. Nhưng **Result.failure** vẫn cần error code đủ rõ và không được biến một lỗi hệ thống bất thường thành kết quả thành công.

### Khi nào nên tự tạo checked exception?

Nếu một thư viện có một loại thất bại bắt buộc caller quyết định và muốn contract tường minh ở compile time, checked exception riêng có thể phù hợp. Nếu method override, lambda hoặc framework không cho phép checked throws, cần cân nhắc unchecked wrapper, callback failure channel hoặc Future. Không ép mọi interface thành **throws Exception** chung vì khó phân biệt lỗi.

## 25. Checklist một phút trước khi chọn catch/throw

1. **Đây là loại lỗi gì?** Input, state, I/O, dependency, task, timeout, interrupt, cancel hay Error?
2. **Ai sở hữu thao tác?** Helper, adapter, service, controller, executor, worker hay caller?
3. **Tầng hiện tại có đủ thông tin để phục hồi thật không?** Nếu không, truyền lên.
4. **Hợp đồng có cho phép checked exception?** Method thường, Runnable, Callable, callback hoặc CompletionStage?
5. **Có cần đổi abstraction?** Nếu wrap, đã giữ cause chưa?
6. **Đang đọc lỗi ở đúng thread/Future/stage?** Worker failure khác caller interrupted.
7. **Timeout có chỉ chấm dứt lượt chờ hay hoàn tất Future bất thường?**
8. **Cancellation có thật sự ngắt task không?** Đã phân biệt Future state với worker execution?
9. **Có side effect cần rollback/compensation không?** Close không tự làm việc đó.
10. **Có AutoCloseable do scope sở hữu không?** Ưu tiên TWR, xét suppressed và thứ tự close.
11. **Retry có idempotent, bounded và nằm trong deadline không?**
12. **Fallback có thỏa hợp đồng hay che dữ liệu sai?**
13. **Đã giữ cause/suppressed và log ở boundary phù hợp?**
14. **Test có phân biệt đúng exception type, cause, trạng thái và đường thành công không?**
15. **Boundary công khai có tránh lộ thông tin nhạy cảm không?**

**Kết luận:** Chọn cách xử lý lỗi theo **ranh giới sở hữu và hợp đồng**, không theo thói quen viết catch. Tầng thấp nên giữ đúng thông tin lỗi; tầng trung gian chỉ wrap hoặc recovery khi thật sự có trách nhiệm; caller của Future phải phân biệt lỗi worker với interruption của chính mình; callback không cho checked exceptions cần chính sách dừng/chuyển đổi rõ ràng; resource/transaction owner phải cleanup và giữ trạng thái nhất quán. Một bảng quyết định hữu ích nhất khi người đọc dùng nó để xác định **ai quyết định**, **lỗi được truyền theo kênh nào** và **những gì phải được bảo toàn sau thất bại**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-reference-006">Tra cứu JLS, Throwable Javadoc và hợp đồng ngoại lệ của từng API</a>

<details>
<summary>Xem chi tiết</summary>

# Tra cứu JLS, `Throwable` Javadoc và hợp đồng ngoại lệ của từng API trong Java

Khi đọc hoặc viết mã Java, một câu hỏi thường gặp là: **phương thức này có thể ném ngoại lệ gì, ngoại lệ đó được bắt hay lan truyền như thế nào, và tài liệu nào là căn cứ đáng tin?** Không thể trả lời chính xác chỉ từ tên ngoại lệ, một đoạn code trên diễn đàn hoặc thử chạy một lần. Java có nhiều cấp tài liệu với phạm vi trách nhiệm khác nhau:

1. **Java Language Specification (JLS):** đặc tả các quy tắc của ngôn ngữ, như `throw`, `throws`, kiểm tra checked exception, chọn nhánh `catch`, cách `finally` và `try-with-resources` kết thúc.
2. **Javadoc của `Throwable`:** hợp đồng các thành phần và thao tác trên đối tượng lỗi: message, cause, stack trace, suppressed exceptions, constructor và `initCause`.
3. **Javadoc của API cụ thể:** một phương thức như `Files.readString`, `Thread.sleep`, `Future.get` hoặc `CompletableFuture.join` khai báo ngoại lệ nào, lỗi nào thuộc điều kiện nào, lúc nào lệnh trả về và có tác dụng phụ gì.

Cách học đúng là **đọc cả ba nơi khi cần**, đối chiếu **đúng phiên bản Java đang sử dụng**, rồi viết một ví dụ nhỏ để kiểm chứng cách hiểu. Bài này dùng **JLS Java SE 17 và Javadoc Java 17** làm mốc thống nhất; những tính năng hoặc chữ ký của các bản Java khác phải được tra cứu trên tài liệu tương ứng.

## 1. Bản đồ nguồn tài liệu: hỏi gì, đọc ở đâu?

| Câu hỏi | Nguồn chính | Điều tìm trong tài liệu |
| --- | --- | --- |
| Vì sao `IOException` phải được bắt hoặc khai báo? | JLS §11.1–§11.2 | Checked/unchecked, compile-time checking |
| `throw` và `throws` khác nhau thế nào? | JLS §14.18, §8.4.6 | Câu lệnh ném lỗi và mệnh đề trong chữ ký |
| `catch (Exception e)` xử lý những loại nào? | JLS §11.2–§11.3, §14.20 | Kiểu bắt, thứ tự handler, exception analysis |
| `finally` có thể ghi đè `return` hoặc lỗi gốc không? | JLS §14.20.2 | Quy tắc hoàn tất bình thường/bất thường |
| Lỗi `close()` nằm ở đâu khi thân `try` cũng lỗi? | JLS §14.20.3 và `Throwable` Javadoc | `try-with-resources`, suppressed exceptions |
| Vì sao `new Wrapper(message, cause)` khác `new Wrapper(message)`? | `Throwable` Javadoc | `getCause`, constructor và cause chain |
| `initCause()` được gọi nhiều lần không? | `Throwable` Javadoc | Khởi tạo cause một lần và lỗi khi gọi sai |
| `getStackTrace()` cho biết gì? | `Throwable` Javadoc | Stack trace, `fillInStackTrace`, dữ liệu có thể rỗng |
| `Files.readString(path)` có `throws IOException` không? | `Files.readString` Javadoc | Chữ ký, phần `Throws`, ý nghĩa điều kiện |
| `Thread.sleep(...)` có thể nhận interrupt không? | `Thread.sleep` Javadoc | `InterruptedException`, xử lý interrupted status |
| `join()` và `get()` báo lỗi khác nhau thế nào? | `CompletableFuture`, `Future` Javadoc | `CompletionException`, `ExecutionException`, hủy, ngắt |
| Hành vi có thay đổi ở JDK khác không? | JLS/Javadoc **đúng phiên bản** | `Since`, ghi chú implementation, deprecation |

Không nên nhầm **quy tắc chung của ngôn ngữ** với **lời hứa của một phương thức cụ thể**. JLS xác định khi nào chương trình biên dịch và luồng điều khiển khi ném lỗi. Nó không thể thay thế bản mô tả rằng `readString` đọc UTF-8 hay một deadline của `Future.get` có thể ném `TimeoutException`.

### Ba liên kết khởi đầu

- JLS 17, Chương 11 — Exceptions: https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html
- JLS 17, Chương 14 — Statements (`throw`, `try`, `catch`, `finally`, `try-with-resources`): https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html
- Java 17, `Throwable` Javadoc: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html

Tất cả ví dụ trong bài sử dụng **đặc tả Java 17**. Khi dự án dùng Java 21 hoặc một phiên bản khác, hãy đổi cả hai bộ tài liệu theo phiên bản trước khi áp dụng kết luận về API mới.

## 2. Cách đọc JLS theo đúng vấn đề

JLS không phải danh sách mẹo lập trình. Nó trình bày các **quy tắc chuẩn tắc** của ngôn ngữ Java. Khi tra cứu, đọc đúng section và cả những điều kiện, ngoại lệ trong quy tắc.

### 2.1. JLS §11 — Các loại ngoại lệ và kiểm tra lúc biên dịch

JLS phân biệt:

- **Checked exception:** các lớp thuộc `Throwable` nhưng **không** nằm trong nhánh `RuntimeException` hoặc `Error`. Thường gặp `IOException`, `SQLException`, `InterruptedException`.
- **Unchecked exception:** `RuntimeException` và các lớp con, `Error` và các lớp con. Lập trình viên không bắt buộc khai báo những loại này bằng `throws`.
- **Compile-time exception checking:** nếu một lời gọi, câu lệnh hoặc thân phương thức có thể ném checked exception theo phân tích của compiler, nó phải được bắt thích hợp hoặc có khai báo `throws` tương thích.

Đọc mục **§11.2.1** và **§11.2.2** khi câu hỏi là “trình biên dịch xác định những ngoại lệ *có thể ném* như thế nào?”. Đọc **§11.2.3** khi thấy lỗi biên dịch kiểu *unreported exception; must be caught or declared to be thrown*.

Ví dụ **cố ý không biên dịch**, dùng để tra lỗi checked exception:

```java
import java.io.IOException;

class IncorrectCheckedHandling {
    static void read() throws IOException {
        throw new IOException("read failed");
    }

    static void run() {
        read(); // Lỗi biên dịch: không bắt hoặc khai báo IOException.
    }
}
```

Không có lệnh chạy chương trình trên vì code này **được viết có chủ đích để compiler từ chối**. Khắc phục bằng cách khai báo `static void run() throws IOException`, hoặc bắt `IOException` và xử lý đúng ở đây. Không chuyển bừa thành `catch (Exception ignored) {}` chỉ để hết lỗi compiler.

**Lưu ý:** compiler kiểm tra **các checked exception có thể phát sinh theo phép phân tích tĩnh**, không bảo đảm ở runtime sẽ không có unchecked exception khác. Java cũng không buộc Javadoc liệt kê mọi tình huống như `NullPointerException` do bug trong code người gọi.

### 2.2. JLS §8.4.6 — Mệnh đề `throws`

Ví dụ:

```java
static String load() throws java.io.IOException {
    return java.nio.file.Files.readString(
            java.nio.file.Path.of("config.txt"));
}
```

Đây là một **phương thức minh họa**, cần đặt trong lớp Java; chương trình không có file `config.txt` vẫn có thể biên dịch nhưng có thể thất bại lúc chạy. `throws IOException` nói phương thức có thể **để checked exception này thoát ra**, không có nghĩa mọi lời gọi chắc chắn ném lỗi hoặc nó đã được xử lý.

Mệnh đề `throws` còn là một phần của hợp đồng override: một phương thức override không được tự mở rộng tập checked exceptions vượt quá hợp đồng phương thức cha/interface theo quy tắc JLS. Đừng đổi method signature chỉ vì một lời gọi tầng thấp nay dùng thư viện khác; cân nhắc dịch ngoại lệ ở ranh giới thích hợp mà **giữ cause**.

### 2.3. JLS §14.18 — Câu lệnh `throw`

`throw expression;` thực sự chuyển luồng điều khiển bằng một đối tượng `Throwable` hoặc lớp con. Ngược lại, `throws X` nằm trong **khai báo** phương thức hoặc constructor. JLS giải thích cả vấn đề kiểu tĩnh của biểu thức ném, xử lý `throw null` và trường hợp biểu thức tạo exception cũng có thể lỗi.

Không dùng `throw` để diễn đạt trạng thái thành công thông thường nếu kiểu kết quả có thể biểu diễn rõ ràng hơn. Tuy vậy, ngoại lệ vẫn là cách hợp lệ để báo điều kiện đặc biệt mà người gọi phải xử lý.

### 2.4. JLS §14.20 — `try`–`catch`–`finally`

Đây là nơi phải đọc khi cần biết:

- `catch` nào có thể bắt ngoại lệ và vì sao thứ tự các `catch` quan trọng.
- Vì sao `finally` vẫn chạy trên đường `return` hoặc `throw`.
- Điều gì xảy ra khi chính `finally` trả về hoặc ném lỗi mới.
- Ngoại lệ nào là **primary** khi cả thân `try-with-resources` và `close()` cùng thất bại.

Ví dụ:

```java
static int masked() {
    try {
        throw new IllegalStateException("origin");
    } finally {
        return 10;
    }
}
```

Đây là một **phương thức minh họa**. Nó hoàn tất bình thường bằng `10`; câu lệnh `return` trong `finally` thay việc hoàn tất bất thường từ `try`. Code hợp lệ về mặt ngôn ngữ nhưng là mẫu cần tránh vì che nguyên nhân thất bại.

**Không suy diễn** rằng `finally` luôn hoàn tất thành công, luôn bảo toàn lỗi gốc hoặc luôn chạy khi cả tiến trình JVM bị chấm dứt đột ngột. JLS quy định hành vi của luồng thực thi Java trong điều kiện áp dụng, không phải cam kết phục hồi dữ liệu sau khi hệ điều hành tắt tiến trình.

## 3. Cách đọc `Throwable` Javadoc: đối tượng lỗi mang dữ liệu gì?

`Throwable` là lớp gốc của `Exception` và `Error`, định nghĩa phần lớn cơ chế chẩn đoán:

| Thành phần | Phương thức | Điều không nên hiểu nhầm |
| --- | --- | --- |
| Detail message | `getMessage()` | Có thể `null`; không phải stack trace hoặc cause |
| Cause | `getCause()` | Có thể `null`; không tự được gắn khi chỉ truyền message |
| Suppressed exceptions | `getSuppressed()`, `addSuppressed(...)` | Là lỗi phụ, không phải nguyên nhân chính |
| Stack trace | `getStackTrace()`, `printStackTrace(...)` | Có thể rỗng nếu bị vô hiệu hóa hay thiếu dữ liệu |
| Ghi lại stack | `fillInStackTrace()` | Chủ động cập nhật thông tin stack của chính object |
| Chỉnh stack | `setStackTrace(...)` | Không nên dùng để làm giả vị trí lỗi |
| Gắn cause sau tạo | `initCause(...)` | Chỉ được khởi tạo một lần khi cause chưa khởi tạo |

### 3.1. Message không bằng cause

So sánh:

```java
Throwable root = new java.io.IOException("disk unavailable");

RuntimeException lost =
        new IllegalStateException("load failed: " + root.getMessage());

RuntimeException preserved =
        new IllegalStateException("load failed", root);
```

Đây là đoạn minh họa bên trong một phương thức. `lost.getCause()` là `null`, còn `preserved.getCause() == root` là `true`. Khi formatter in stack của `preserved`, người điều tra có thể xem chuỗi **`Caused by:`** và nguyên nhân gốc. Khi chỉ giữ message, logger không thể tái tạo các khung stack từ `root`.

### 3.2. Constructor với cause và `initCause()`

Một constructor nhận `(String message, Throwable cause)` thường là cách rõ ràng nhất để bọc lỗi. `initCause(...)` tồn tại để hỗ trợ các ngoại lệ đã được tạo mà chưa khởi tạo nguyên nhân, nhưng không phải setter có thể thay đổi tùy ý: gọi lại sau khi đã gán cause gây `IllegalStateException`; tự đặt object làm cause của chính nó cũng không hợp lệ.

Đặc biệt, constructor đã **khởi tạo cause thành `null`** khác với một constructor để cause ở trạng thái *chưa khởi tạo*. `getCause() == null` tự nó không bảo đảm được phép gọi `initCause(...)`; phải xem constructor đã dùng.

### 3.3. Suppressed exception và `try-with-resources`

Giả sử `read()` ném `IOException("read failed")` rồi `close()` cũng ném `IOException("close failed")`. Với `try-with-resources`, thông thường:

```text
IOException: read failed      <-- ngoại lệ chính
  Suppressed: IOException: close failed
```

Đây là sơ đồ về cấu trúc lỗi, không phải stdout có số dòng xác định. Lỗi đóng tài nguyên nằm trong `getSuppressed()` của ngoại lệ chính; **không** phải là `getCause()` chỉ vì nó xảy ra sau. Nếu dùng `finally` thủ công và `close()` ném lỗi, hành vi có thể khác và lỗi cleanup có thể che lỗi nguồn.

### 3.4. Stack trace không phải một bản ghi bất biến vĩnh viễn

Constructor `Throwable` thông thường ghi nhận ngăn xếp khi đối tượng được tạo (theo các điều kiện API). Ném lại **cùng đối tượng** không bắt buộc tạo dấu vết mới. `fillInStackTrace()` có thể cập nhật dấu vết. Constructor `protected` với `writableStackTrace=false` cho phép tắt việc ghi stack, và `getStackTrace()` khi ấy rỗng.

Một ngoại lệ dùng chung giữa nhiều request có thể mang stack/cause/suppressed không phù hợp với từng sự cố. Khi điều tra hiệu năng hoặc lỗi đồng thời, cần lưu ý quyền sở hữu object, nhưng không hy sinh stack trace của lỗi bất ngờ chỉ vì muốn tiết kiệm cấp phát.

## 4. Cách đọc trang Javadoc của một API

Tìm đúng class hoặc interface, rồi định vị **đúng overload** của phương thức. Đối với mỗi phương thức, đọc theo trình tự:

1. **Module/package/class:** ví dụ `java.base` → `java.nio.file` → `Files`.
2. **Method signature:** tên, kiểu tham số, generic type, `static`/instance, kiểu trả về và `throws` có trong chữ ký.
3. **Mô tả chính:** điều kiện thành công, điều kiện biên, hành vi xử lý tài nguyên, thứ tự tác dụng phụ.
4. **Parameters / Returns / Throws:** điều kiện cụ thể ứng với mỗi ngoại lệ được tài liệu hóa.
5. **`Since`, `@Deprecated`, `See Also`, implementation notes:** dùng để xác định phiên bản hoặc giới hạn bảo đảm.
6. **Trang của loại ngoại lệ được liệt kê:** nếu cần hiểu `TimeoutException`, `ExecutionException` hay `CancellationException` thì đọc tiếp Javadoc của lớp đó.
7. **Liên kết tới JLS hoặc interface cha:** khi hợp đồng bị ràng buộc bởi ngôn ngữ hay một phương thức được override.

Đừng chỉ đọc một dòng `throws IOException` rồi ngừng. Ví dụ `Files.readString` còn có vấn đề về giải mã ký tự, dữ liệu lớn và việc đóng file. Phần `Throws` có thể ghi nhận cả unchecked exceptions đặc biệt được tài liệu hóa. **Không được kết luận “trang Throws không liệt kê `NullPointerException` thì nó tuyệt đối không thể xảy ra”.** Các unchecked exception do lỗi lập trình hoặc do đối tượng đầu vào vẫn có thể xuất hiện theo ngữ nghĩa chung.

### 4.1. Đúng overload và kiểu tham số

Một lớp có thể có nhiều phương thức cùng tên. `Future.get()` khác `Future.get(long timeout, TimeUnit unit)`: overload thứ hai có thể ném thêm `TimeoutException`. `Files.readString(Path)` và `Files.readString(Path, Charset)` có các tham số khác nhau, dù đều có thể ném `IOException`.

Cũng cần phân biệt `Thread.sleep(long)` với các overload khác và các phiên bản API mới hơn. Nếu copy nhầm link tới overload khác, có thể trả lời sai ngay cả khi cùng class.

### 4.2. `Throws` không tự nói cách xử lý

Nếu Javadoc nói `IOException` khi đọc file thất bại, điều đó **không có nghĩa** repository phải bắt và trả chuỗi rỗng. Chính sách xử lý thuộc tầng ứng dụng: truyền lỗi, thử lại khi an toàn, trả dữ liệu cache hay báo thất bại. API contract mô tả điều gì xảy ra; **nghiệp vụ quyết định phản ứng**.

### 4.3. Kiểm tra `Since` và phiên bản dự án

`CompletableFuture.failedFuture(...)` có từ Java 9. Các API hoặc overload khác có thể xuất hiện ở phiên bản mới hơn. Khi ứng dụng target Java 17, tra tài liệu Java 17 và kiểm thử với `javac --release 17` để phát hiện việc vô tình sử dụng chữ ký có ở JDK mới nhưng **không nằm trong API của bản mục tiêu**.

Javadoc không phải chính sách của mọi framework: một thư viện JDBC, Spring hoặc HTTP client có thể bọc/đổi ngoại lệ. Phải đọc **API thực sự được gọi**, gồm các lớp trung gian khi có, chứ không giả định hợp đồng của một class JDK áp dụng nguyên xi qua framework wrapper.

## 5. Case study: `Files.readString(Path)` và `Integer.parseInt(String)`

Đây là hai ví dụ hữu ích vì một phương thức có checked exception, còn phương thức kia thường báo lỗi đầu vào bằng unchecked exception.

### `Files.readString(Path)`

Javadoc Java 17 của `java.nio.file.Files.readString(Path)` nêu kiểu trả về `String` và `throws IOException`. Nó đọc nội dung tệp theo UTF-8 (ở overload một tham số), và mô tả các trường hợp lỗi I/O hoặc chuỗi byte không giải mã được. Khi gọi:

```java
static String readConfig(java.nio.file.Path path)
        throws java.io.IOException {
    return java.nio.file.Files.readString(path);
}
```

Đây là **phương thức minh họa**, đặt trong một lớp Java. Compiler buộc xử lý đúng `IOException` vì checked; caller có thể bắt ở tầng thích hợp. Nếu muốn cấu hình mặc định, quyết định fallback phải được hợp đồng ứng dụng cho phép.

Javadoc: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html

### `Integer.parseInt(String)`

`Integer.parseInt` có thể ném `NumberFormatException` khi chuỗi không phải dạng số hợp lệ. Đây là subclass của `IllegalArgumentException`/`RuntimeException`, nên caller **không bị compiler buộc** khai báo `throws NumberFormatException`.

```java
static int parseQuantity(String input) {
    return Integer.parseInt(input);
}
```

Đây là phương thức minh họa, không bao gồm bước validation toàn bộ dữ liệu nghiệp vụ. Khi input đến từ người dùng, sai định dạng thường là tình huống dự kiến cần trả lỗi kiểm tra đầu vào. Nếu code nội bộ luôn truyền một literal hợp lệ mà bất ngờ sai, đó có thể là bug khác. **Tên class ngoại lệ không quyết định duy nhất chính sách phục hồi.**

Javadoc: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html

## 6. Case study: `Thread.sleep` và `InterruptedException`

Javadoc Java 17 của `Thread.sleep(...)` khai báo `InterruptedException` khi luồng đang ngủ bị ngắt. Khi ngoại lệ được ném, interrupted status của luồng được xóa theo hợp đồng `sleep`.

Một phương thức có thể **truyền ngắt**:

```java
static void pause() throws InterruptedException {
    Thread.sleep(100);
}
```

Nếu phương thức không thể khai báo `InterruptedException`, ví dụ `Runnable.run()`, có thể cần **bắt, khôi phục cờ và kết thúc**:

```java
try {
    Thread.sleep(100);
} catch (InterruptedException interrupted) {
    Thread.currentThread().interrupt();
    return;
}
```

Đây là mảnh code minh họa bên trong một phương thức trả `void`. Nó **không** có nghĩa luôn phải nuốt hoặc luôn phải khôi phục mọi yêu cầu ngắt: hãy dựa vào hợp đồng thành phần đang thực thi. Điểm quan trọng là không biến tín hiệu ngắt thành thành công giả bằng cách tiếp tục một vòng lặp bất tận.

Đừng đồng nhất `InterruptedException` của **luồng chờ** với ngoại lệ do tác vụ bất đồng bộ ở thread khác ném. Việc đó đặc biệt quan trọng khi đọc `Future.get()`.

Javadoc: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html

## 7. Case study: `Future.get` và `CompletableFuture.join`

Hai API có quan hệ gần nhau nhưng hợp đồng quan sát lỗi khác nhau.

| Lời gọi | Ngoại lệ cần chú ý | Ý nghĩa |
| --- | --- | --- |
| `Future.get()` | `InterruptedException` | Luồng đang chờ bị ngắt |
| `Future.get()` | `ExecutionException` | Tác vụ được đợi đã hoàn tất thất bại; dùng `getCause()` |
| `Future.get()` | `CancellationException` | Future bị hủy (unchecked) |
| `Future.get(timeout, unit)` | Thêm `TimeoutException` | **Lần chờ** hết hạn, không tự làm future lỗi |
| `CompletableFuture.join()` | `CompletionException` | Stage hoàn tất lỗi, thường bọc nguyên nhân |
| `CompletableFuture.join()` | `CancellationException` | Có thể ném trực tiếp nếu chính future bị hủy |
| `CompletableFuture.get()` | `InterruptedException`, `ExecutionException` | Hợp đồng tương thích `Future.get()` |
| `CompletableFuture.orTimeout(...)` | Future bị hoàn tất lỗi với `TimeoutException` nếu deadline thắng | Khác với timeout của riêng một lần `get(timeout, unit)` |
| `CompletableFuture.completeOnTimeout(v, ...)` | Future hoàn tất bình thường bằng `v` nếu deadline thắng | Có thể che sự khác biệt giữa fallback và kết quả thật |

`join()` không khai báo `InterruptedException` và `ExecutionException`, nhưng **không vì thế mà mọi lỗi trở thành giá trị trả về**. Nó báo thất bại thông qua đường unchecked. Nếu `CompletableFuture` đã chứa một `CompletionException`, `get()` có cách trình bày cause riêng; không suy đoán rằng mọi đường đi nhất thiết luôn có cùng số wrapper.

Javadoc:

- https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html
- https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html

**Mẹo tra cứu:** khi có một exception nhìn thấy ở controller, hãy xác định xem nó phát sinh **trong supplier**, trong **callback `thenApply`/`handle`**, hay ở **lời gọi chờ `get()`**. Hai vị trí lỗi khác nhau có thể dẫn đến lớp wrapper giống nhau. `getCause()` và stack trace giúp truy đúng nơi phát sinh.

## 8. Ví dụ Java 17+ độc lập: kiểm chứng các hợp đồng đã tra

Tạo tệp **`ApiExceptionContractDemo.java`**:

```java
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class ApiExceptionContractDemo {
    static String parseStatus(String text) {
        try {
            return "value=" + Integer.parseInt(text);
        } catch (NumberFormatException invalid) {
            return "invalid number";
        }
    }

    static void checkedReadFailure() throws IOException {
        throw new IOException("disk offline");
    }

    static void preciseRethrow() throws IOException {
        try {
            checkedReadFailure();
        } catch (Exception original) {
            throw original;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("parseGood=" + parseStatus("25"));
        System.out.println("parseBad=" + parseStatus("abc"));

        try {
            preciseRethrow();
        } catch (IOException failure) {
            System.out.println("checked=" + failure.getMessage());
        }

        Thread.currentThread().interrupt();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException interrupted) {
            System.out.println("sleepInterrupted="
                    + interrupted.getClass().getSimpleName());
            System.out.println("flagAfterSleepThrows="
                    + Thread.currentThread().isInterrupted());
            Thread.currentThread().interrupt();
            System.out.println("flagRestored="
                    + Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted(); // Xóa cờ để ví dụ sau không bị ảnh hưởng.
        }

        CompletableFuture<String> failure =
                CompletableFuture.failedFuture(
                        new IOException("async disk offline"));

        try {
            failure.join();
        } catch (CompletionException wrapper) {
            System.out.println("joinWrapper="
                    + wrapper.getClass().getSimpleName());
            System.out.println("joinCause="
                    + wrapper.getCause().getClass().getSimpleName());
        }

        try {
            failure.get();
        } catch (ExecutionException wrapper) {
            System.out.println("getWrapper="
                    + wrapper.getClass().getSimpleName());
            System.out.println("getCause="
                    + wrapper.getCause().getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 ApiExceptionContractDemo.java
java ApiExceptionContractDemo
```

Đầu ra:

```text
parseGood=value=25
parseBad=invalid number
checked=disk offline
sleepInterrupted=InterruptedException
flagAfterSleepThrows=false
flagRestored=true
joinWrapper=CompletionException
joinCause=IOException
getWrapper=ExecutionException
getCause=IOException
```

Những điểm test này chứng minh:

- `Integer.parseInt` ném `NumberFormatException` cho `"abc"`; nhánh `catch` đã chủ động chuyển lỗi nhập liệu thành một chuỗi trạng thái.
- `checkedReadFailure()` ném checked `IOException`. `preciseRethrow()` dùng `catch (Exception original) { throw original; }` nhưng vẫn được compiler cho phép chữ ký `throws IOException` vì biến catch **effectively final** và phân tích chính xác các checked exception có thể thoát ra theo JLS §11.2.2. Nếu gán lại biến catch hoặc thêm các checked exception khác trong `try`, kết luận có thể thay đổi.
- `Thread.sleep` gặp trạng thái interrupted và ném `InterruptedException`; lúc bắt, cờ đã được xóa. Ví dụ khôi phục cờ có chủ đích, rồi xóa ở cuối **chỉ để cô lập chương trình trình diễn**; mã nghiệp vụ thật không nên xóa yêu cầu ngắt tùy tiện.
- Cùng một `CompletableFuture.failedFuture(IOException)`, `join()` báo qua `CompletionException`, còn `get()` báo qua `ExecutionException`; cả hai có `IOException` làm cause.

Kết quả đã chọn các đầu vào được kiểm soát, không phụ thuộc vào file thật hoặc cuộc đua thread, nên có thể kiểm thử chính xác stdout.

## 9. Ví dụ Java 17+ độc lập: `Throwable`, cause và lỗi cleanup

Tạo tệp **`ThrowableDocumentationDemo.java`**:

```java
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public class ThrowableDocumentationDemo {
    static final class FailingResource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            throw new IOException("close failed");
        }
    }

    static void read() throws IOException {
        try (FailingResource resource = new FailingResource()) {
            throw new IOException("read failed");
        }
    }

    public static void main(String[] args) {
        try {
            read();
        } catch (IOException original) {
            IllegalStateException wrapper =
                    new IllegalStateException("import failed", original);

            System.out.println("outer=" + wrapper.getMessage());
            System.out.println("causeType="
                    + wrapper.getCause().getClass().getSimpleName());
            System.out.println("causeMessage="
                    + wrapper.getCause().getMessage());
            System.out.println("suppressedCount="
                    + original.getSuppressed().length);
            System.out.println("suppressedMessage="
                    + original.getSuppressed()[0].getMessage());

            StringWriter output = new StringWriter();
            wrapper.printStackTrace(new PrintWriter(output));
            String trace = output.toString();
            System.out.println("containsCause="
                    + trace.contains("Caused by: java.io.IOException: read failed"));
            System.out.println("containsSuppressed="
                    + trace.contains(
                            "Suppressed: java.io.IOException: close failed"));
        }

        Exception legacy = new Exception("legacy");
        legacy.initCause(new IOException("first cause"));
        boolean secondRejected = false;

        try {
            legacy.initCause(new IOException("second cause"));
        } catch (IllegalStateException expected) {
            secondRejected = true;
        }

        System.out.println("initCauseOnce=" + secondRejected);
        System.out.println("causeStill="
                + legacy.getCause().getMessage());
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 ThrowableDocumentationDemo.java
java ThrowableDocumentationDemo
```

Đầu ra:

```text
outer=import failed
causeType=IOException
causeMessage=read failed
suppressedCount=1
suppressedMessage=close failed
containsCause=true
containsSuppressed=true
initCauseOnce=true
causeStill=first cause
```

**Phân tích:**

1. `read()` ném `IOException("read failed")` từ thân `try`.
2. `FailingResource.close()` cũng ném `IOException("close failed")`.
3. Theo hợp đồng `try-with-resources`, ngoại lệ đọc là chính và lỗi đóng được ghi trong suppressed của nó.
4. Wrapper `IllegalStateException("import failed", original)` **giữ cả đối tượng gốc**, nhờ vậy người đọc vẫn truy được lỗi chính và lỗi phụ.
5. `printStackTrace` có thể trình bày `Caused by:` và `Suppressed:`; ví dụ dùng phép kiểm tra chuỗi ổn định thay vì in số dòng cụ thể của stack.
6. `initCause` thành công lần đầu trên một `Exception` được tạo bằng constructor chỉ có message; lần thứ hai bị từ chối và cause đã gán không đổi.

Một constructor khác đã khởi tạo cause (kể cả `null`) có thể không cho dùng `initCause` nữa. Khi tự thiết kế lớp exception, hãy ưu tiên constructor có `cause` hơn là tạo đối tượng rồi cố đặt cause ở một chỗ khác.

## 10. Hiểu cách JLS và Javadoc bổ sung cho nhau

Xét cùng một đoạn mã:

```java
static String read(Path path) throws IOException {
    return Files.readString(path);
}
```

Đây là đoạn minh họa cần import `Path`, `Files`, `IOException`. Cần phân biệt ba nhận định:

- **Javadoc `Files.readString`:** mô tả thao tác đọc và ngoại lệ có thể phát sinh theo hợp đồng API.
- **JLS §11.2:** giải thích tại sao checked `IOException` phải được xử lý hoặc cho phép thoát ra bằng mệnh đề `throws` của phương thức.
- **`Throwable` Javadoc:** giải thích cách kiểm tra cause, message, stack và suppressed nếu người gọi bắt hoặc bọc lỗi.

Chỉ đọc JLS không cho biết chi tiết UTF-8 của `Files.readString`. Chỉ đọc Javadoc `Files` không giải thích đầy đủ tại sao compiler từ chối một phương thức thiếu `throws`. Chỉ đọc `Throwable` không cho biết phương thức nào tạo lỗi.

### Quy trình 7 bước khi gặp ngoại lệ chưa biết

1. **Xác định API và phiên bản đang chạy/build.** Ghi package, class, method, overload, kiểu tham số và Java release.
2. **Đọc chữ ký Javadoc.** Chú ý checked exceptions xuất hiện trong `throws`, interface gốc và biến thể overload.
3. **Đọc phần `Throws` và mô tả phương thức.** Ghi chính xác điều kiện gây lỗi và có thay đổi trạng thái/tài nguyên trước khi ném không.
4. **Đối chiếu JLS khi câu hỏi liên quan đến compiler hoặc điều khiển luồng.** Chọn §11.2 cho checked exceptions, §14.18 cho `throw`, §14.20 cho `catch`/`finally`/resources.
5. **Đọc `Throwable` nếu có wrapper/cause/suppressed.** Xác định nguyên nhân ở đâu, ngoại lệ phụ có bị mất hay không.
6. **Chạy một chương trình tối thiểu có đầu vào xác định.** Biên dịch bằng `javac --release` đúng bản mục tiêu. Đừng dùng một lần chạy thành công để khẳng định mọi trường hợp không thể lỗi.
7. **Ghi rõ kết luận và giới hạn.** Tách “JLS bảo đảm”, “Javadoc API bảo đảm”, “chỉ là hành vi quan sát ở JVM cụ thể” và “quyết định nghiệp vụ của ứng dụng”.

## 11. Đọc stack trace và cause để lần ngược hợp đồng bị vi phạm

Nếu log có dạng:

```text
java.util.concurrent.CompletionException: java.io.IOException: disk offline
    at ...
Caused by: java.io.IOException: disk offline
    at ...
```

Đây là **minh họa**, không phải stdout hoàn chỉnh. Cần phân biệt:

- `CompletionException`: lớp bọc giúp mô tả thất bại của một stage bất đồng bộ.
- `IOException`: loại ngoại lệ nguyên nhân, có thể do thao tác I/O ở bên trong.
- Các khung stack: vị trí tạo/điền stack của exception và đường đi; không nhất thiết chứa toàn bộ lịch sử request qua nhiều thread.
- `getCause()`: liên kết cause để truy lỗi bên dưới; không phải luôn chỉ có một tầng.
- `getSuppressed()`: lỗi phụ, không đồng nghĩa cause.

Một logger nhận cả `Throwable` thường giúp giữ các thông tin đó hơn là chỉ ghi `getMessage()`. Nhưng formatter/backend có thể được cấu hình lược bớt stack, nên phải kiểm tra hệ thống ghi log thực tế.

Để phân loại ở tầng xử lý lỗi, có thể gỡ các wrapper kỹ thuật đã biết như `CompletionException` và `ExecutionException`, nhưng không nên tự động bỏ mọi wrapper: ngoại lệ nghiệp vụ có thể mang thông tin quan trọng về thao tác đã thất bại. Khi log, giữ đủ chuỗi để chẩn đoán.

## 12. Phân biệt hợp đồng API và chi tiết triển khai

Javadoc đôi khi có đoạn **implementation note** hoặc mô tả riêng cho một triển khai. Hãy hiểu đúng cấp bảo đảm:

- **Đặc tả JLS** là quy tắc của ngôn ngữ; code Java hợp lệ phải tuân thủ các yêu cầu ấy.
- **Hợp đồng API Javadoc** mô tả hành vi được cam kết cho class/method, bao gồm các điều kiện `Throws` đã nêu.
- **Implementation note** có thể giải thích chiến lược trong bản triển khai, không nhất thiết là bảo đảm của mọi implementation cùng interface.
- **Quan sát runtime** như số khung stack hoặc thứ tự callback giữa các thread không tự trở thành bảo đảm ngôn ngữ.

Ví dụ, `CompletableFuture.cancel(boolean)` có ghi chú về đối số `mayInterruptIfRunning` đối với triển khai `CompletableFuture`: nó không được dùng để ngắt computation theo cách `FutureTask` có thể làm. Vì thế không suy ra từ tên `cancel(true)` rằng supplier luôn bị interrupt.

Ví dụ khác: `AutoCloseable.close()` có chữ ký cho phép `throws Exception`, nhưng lớp con như `Closeable` thu hẹp checked exception. Không thể chỉ nhìn interface rộng rồi kết luận mọi `close()` cụ thể đều khai báo `throws Exception`.

Đối với phương thức API của bên thứ ba, hãy kiểm tra phiên bản dependency. Một thay đổi thư viện có thể làm khác loại wrapper hoặc thời điểm ném lỗi mà không thay đổi các quy tắc JLS.

## 13. Một số bẫy khi tra cứu ngoại lệ

| Bẫy | Vì sao dễ sai | Cách kiểm tra |
| --- | --- | --- |
| Dùng Javadoc Java mới cho dự án Java 17 | API/overload có thể chưa tồn tại trong Java 17 | Chọn đúng trang `/java/javase/17/` và thử `javac --release 17` |
| Chỉ nhìn tên phương thức | Không biết overload đang gọi | So khớp đủ tên, tham số và kiểu trả về |
| Tưởng danh sách `Throws` liệt kê mọi lỗi runtime có thể xảy ra | Unchecked exceptions do code hoặc trạng thái có thể không được liệt kê hết | Kết hợp JLS, ngữ nghĩa phương thức và đầu vào thực |
| Tưởng `throws` là câu lệnh xử lý lỗi | Nó chỉ là khai báo | Xem §8.4.6 và §11.2 |
| Tưởng checked exception chắc chắn phục hồi được | Cơ chế compiler khác chính sách nghiệp vụ | Đọc điều kiện lỗi và thiết kế ranh giới xử lý |
| Tưởng unchecked không cần quan tâm | Có thể là bug hoặc input dự kiến | Xem hợp đồng và xử lý đúng |
| Tưởng mọi `Exception` đều thuộc nhóm checked | `RuntimeException` là unchecked | Xem phân loại JLS §11.1 |
| Tưởng `getMessage()` là cause | Message chỉ là chuỗi | Dùng `getCause()` và `printStackTrace()` |
| Tưởng `finally` luôn giữ lỗi cũ | Return/throw trong finally có thể che lỗi | Đọc JLS §14.20, test cả lỗi kép |
| Tưởng `getSuppressed()` chứa mọi cause | Suppressed và cause có nghĩa khác nhau | Đọc `Throwable`, thử `try-with-resources` |
| Tưởng `Future.get(timeout)` timeout nghĩa là công việc bị hủy | Lời gọi chờ hết hạn không tự hủy future | Đọc đúng overload và chính sách cancel |
| Tưởng `join()` với `get()` cùng loại exception | Một bên unchecked, một bên có `ExecutionException` checked | Đọc cả `Future` và `CompletableFuture` |
| Copy code chạy trên JDK mới rồi coi là hợp lệ với Java 17 | Dễ dùng API chưa có ở release mục tiêu | Biên dịch với `--release 17` |
| Chỉ dựa vào một câu trả lời trên mạng | Có thể thiếu phiên bản hoặc điều kiện biên | Đối chiếu nguồn chuẩn và thử nghiệm tối thiểu |

## 14. Bài tập tra cứu và tự kiểm tra

**Câu 1.** Muốn biết `catch(IOException e)` có bắt `FileNotFoundException` không, tra đâu?  
**Trả lời:** JLS về kiểu ngoại lệ và chọn handler; `FileNotFoundException` là subclass của `IOException`, nên handler `IOException` có thể bắt nó nếu exception đi tới đây.

**Câu 2.** Muốn biết `Thread.sleep` có xóa interrupted status khi ném `InterruptedException` không, tra đâu?  
**Trả lời:** Javadoc của `Thread.sleep` đúng overload/phiên bản. Không kết luận chỉ từ tên lớp `InterruptedException`.

**Câu 3.** `throw new IOException(...)` không biên dịch trong một phương thức `void run()` không có catch/throws. Tra đâu?  
**Trả lời:** JLS §11.2 (kiểm tra checked exceptions), §14.18 (`throw`) và §8.4.6 (`throws`).

**Câu 4.** `new RuntimeException("failed")` có tự có cause không?  
**Trả lời:** Không có đối tượng cause được gắn chỉ vì có message. Muốn giữ ngoại lệ gốc, sử dụng constructor nhận `Throwable` khi phù hợp.

**Câu 5.** Trong `try-with-resources`, thân `try` và `close()` cùng lỗi. Tra đâu?  
**Trả lời:** JLS §14.20.3 về tài nguyên và `Throwable` Javadoc về `getSuppressed()`.

**Câu 6.** Có thể gọi `initCause` hai lần để thay nguyên nhân không?  
**Trả lời:** Không. Phải đọc hợp đồng `Throwable.initCause` và ưu tiên tạo wrapper với cause từ đầu.

**Câu 7.** `CompletableFuture.join()` có buộc caller `catch (ExecutionException)` không?  
**Trả lời:** Không. `join` có đường lỗi unchecked như `CompletionException`, khác `get()`.

**Câu 8.** Gọi `future.get(100, MILLISECONDS)` bị timeout, nhưng công việc vẫn chạy. Có mâu thuẫn với Javadoc không?  
**Trả lời:** Không. Timeout ở đây giới hạn một lần chờ; không tự chứng minh future đã bị hủy.

**Câu 9.** Trong Javadoc chỉ thấy `throws IOException`, có thể khẳng định chắc chắn không bao giờ có `NullPointerException` không?  
**Trả lời:** Không. Danh sách ngoại lệ được tài liệu hóa không phải chứng minh không có mọi unchecked exception trong mọi tình huống.

**Câu 10.** `throws IOException` có tự rollback mọi thay đổi trước lỗi không?  
**Trả lời:** Không. JLS xử lý việc lan truyền exception; rollback hay bù trừ thuộc hợp đồng transaction/nghiệp vụ.

**Câu 11.** Một `Error` có bị bắt bởi `catch (Exception e)` không?  
**Trả lời:** Không theo quan hệ kế thừa thông thường: `Error` và `Exception` là hai nhánh riêng dưới `Throwable`. Tránh bắt `Throwable` rộng để làm ứng dụng “không bao giờ lỗi”.

**Câu 12.** Nếu API kế thừa phương thức từ interface khác, tài liệu nào đáng xem?  
**Trả lời:** Javadoc class triển khai, Javadoc interface gốc và quy tắc override/checked exceptions của JLS. Cần phân biệt hợp đồng chung và hành vi riêng.

## 15. Checklist cho code review và điều tra production

Khi gặp một exception khó hiểu trong pull request hoặc production:

1. Ghi lại **JDK target** và JDK đang chạy; không tra cứu mơ hồ “Java hiện tại”.
2. Xác định method signature và **đúng overload** được gọi.
3. Phân loại checked/unchecked bằng quy tắc JLS, không đoán từ tên.
4. Đọc phần `Throws` và điều kiện cụ thể trong Javadoc.
5. Phân biệt lỗi **phát sinh trong tác vụ** với lỗi **khi quan sát/chờ kết quả**.
6. Đọc `getCause()` và `getSuppressed()` nếu có wrapper hoặc lỗi đóng tài nguyên.
7. Kiểm tra `finally` và `try-with-resources` có bảo toàn lỗi chính không.
8. Kiểm tra trạng thái tài nguyên/dữ liệu sau thất bại; `throws` không tự rollback.
9. Nếu dùng wrapper mới, giữ nguyên nhân bằng constructor có cause.
10. Dùng `javac --release` và một ví dụ xác định để kiểm chứng đúng API của phiên bản mục tiêu.
11. Tách rõ kết luận **được đặc tả** khỏi nhận xét “đã thấy chạy như vậy”.
12. Trích đúng đường dẫn tài liệu JLS/Javadoc trong ghi chú kỹ thuật, tránh dùng bản API khác với bản phát hành.

## 16. Danh mục tài liệu chuẩn để mở lại

| Chủ đề | Tài liệu chính thức |
| --- | --- |
| JLS: exception kinds, checked exceptions, handlers | https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html |
| JLS: method `throws` | https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.6 |
| JLS: `throw` statement | https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.18 |
| JLS: `try`, `catch`, `finally`, `try-with-resources` | https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20 |
| `Throwable` | https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html |
| `AutoCloseable` | https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/AutoCloseable.html |
| `Files` | https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html |
| `Integer` | https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html |
| `Thread` | https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html |
| `Future` | https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html |
| `CompletableFuture` | https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html |
| `CompletionException` | https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletionException.html |
| `ExecutionException` | https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutionException.html |

**Kết luận:** JLS trả lời **ngôn ngữ Java xử lý ngoại lệ theo quy tắc nào**. `Throwable` Javadoc trả lời **một đối tượng lỗi lưu và trình bày dữ liệu chẩn đoán ra sao**. Javadoc của **chính phương thức API** trả lời **khi nào phương thức có thể thất bại và điều gì được cam kết**. Dùng cả ba nguồn, chọn đúng phiên bản và kiểm chứng bằng test Java tối thiểu là cách đáng tin để xử lý một câu hỏi về ngoại lệ.

</details>

- [Quay lại đầu trang](#back-to-top)
