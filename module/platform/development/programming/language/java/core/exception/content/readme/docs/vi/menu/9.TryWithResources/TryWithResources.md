<a id="back-to-top"></a>

# Try-with-resources

## Menu
- [Mục đích và cú pháp try-with-resources từ Java 7](#try-with-resources-001)
- [AutoCloseable và Closeable](#try-with-resources-002)
- [Resource được khai báo trong try và phạm vi sử dụng](#try-with-resources-003)
- [Dùng resource có sẵn là final hoặc effectively final từ Java 9](#try-with-resources-004)
- [Thứ tự tạo tài nguyên từ trái sang phải](#try-with-resources-005)
- [Thứ tự đóng tài nguyên ngược với thứ tự tạo](#try-with-resources-006)
- [Xử lý khi việc tạo một tài nguyên thất bại](#try-with-resources-007)
- [Tài nguyên null và lời gọi close tự động](#try-with-resources-008)
- [Ngoại lệ từ thân try, close và nguyên tắc chọn ngoại lệ chính](#try-with-resources-009)
- [Try-with-resources kết hợp catch và finally](#try-with-resources-010)
- [Tài nguyên được đóng khi return hoặc thoát đột ngột](#try-with-resources-011)
- [Quyền sở hữu tài nguyên và trường hợp không nên tự động đóng](#try-with-resources-012)

## <a id="try-with-resources-001">Mục đích và cú pháp try-with-resources từ Java 7</a>

<details>
<summary>Xem chi tiết</summary>

# Mục đích và cú pháp try-with-resources từ Java 7

> **Bối cảnh:** Java Core → Exception → Try-with-resources. Từ **Java 7**, Java cung cấp cấu trúc **try-with-resources** (thường viết tắt **TWR**) để tự động gọi `close()` trên các đối tượng tài nguyên khi rời khối `try`. Cấu trúc này giúp tránh quên đóng file, stream, socket, JDBC resource và nhiều đối tượng `AutoCloseable` khác, kể cả khi thao tác chính ném exception. Từ Java 9, cú pháp được mở rộng để sử dụng một số biến tài nguyên đã khai báo sẵn; quy tắc cốt lõi vẫn là tự đóng tài nguyên và bảo toàn lỗi theo thứ tự xác định.

## 1. Vì sao cần try-with-resources?

Một chương trình Java thường sử dụng **tài nguyên có vòng đời**: file, stream, reader, writer, kết nối, result set hoặc đối tượng quản lý tài nguyên do ứng dụng tự tạo. Nhiều tài nguyên ngoài heap (file descriptor, socket, connection, native handle) cần được đóng đúng lúc; chỉ chờ garbage collector không phải chính sách giải phóng tài nguyên đáng tin cậy.

Đoạn mã dễ mắc lỗi:

```java
// Minh họa ý tưởng bên trong một method, chưa phải chương trình độc lập.
java.io.BufferedReader reader =
        new java.io.BufferedReader(new java.io.FileReader("input.txt"));

String line = reader.readLine();
System.out.println(line);
reader.close();
```

Nếu `readLine()` hoặc `println(...)` phát sinh exception trước `reader.close()`, đường thực thi bình thường bị gián đoạn và câu `close()` **không được gọi**. Đoạn trên cũng thiếu chính sách xử lý checked `IOException` và phụ thuộc file trên đĩa.

Trước Java 7, cách thường gặp là dùng `finally` với nhiều mã dọn dẹp thủ công:

```java
// Phương thức minh họa hợp lệ khi đặt bên trong một lớp.
static String readFirstLineBeforeJava7(String path)
        throws java.io.IOException {
    java.io.BufferedReader reader = null;
    try {
        reader = new java.io.BufferedReader(
                new java.io.FileReader(path));
        return reader.readLine();
    } finally {
        if (reader != null) {
            reader.close();
        }
    }
}
```

Cách này tốt hơn quên `close()`, nhưng vẫn có các khó khăn:

- Phải tự khai báo biến ngoài `try`, kiểm tra `null` và viết `finally`.
- Khi có nhiều tài nguyên, mã dọn dẹp dễ dài, lặp, hoặc bỏ sót.
- Khi phần thân `try` **và** `close()` đều ném exception, một exception từ `finally` có thể **che mất lỗi gốc** nếu không tự xử lý cẩn thận.
- Mọi nhánh `return`/exception đều phải được suy xét để bảo đảm dọn dẹp và giữ nguyên thông tin chẩn đoán.

TWR giải quyết phần lớn việc **quản lý vòng đời đóng tài nguyên**:

```java
// Phương thức minh họa hợp lệ khi đặt bên trong một lớp.
static String readFirstLine(String path) throws java.io.IOException {
    try (java.io.BufferedReader reader =
                 new java.io.BufferedReader(
                         new java.io.FileReader(path))) {
        return reader.readLine();
    }
}
```

Khi rời cấu trúc `try`, Java tự gọi `reader.close()` theo quy tắc ngôn ngữ; ta không cần viết `finally` chỉ để đóng `reader`.

**Giới hạn:** TWR không bảo đảm thao tác đọc/ghi thành công, không tự rollback dữ liệu đã ghi và không cứu được mọi trường hợp process/JVM bị kết thúc bất thường. Nó giải quyết **vòng đời tài nguyên** theo cơ chế Java, không tự thay thế transaction hay recovery nghiệp vụ.

## 2. Cú pháp gốc được hỗ trợ từ Java 7

Dạng phổ biến nhất:

```java
try (ResourceType resource = resourceInitializer) {
    // Sử dụng resource.
}
```

Trong đó:

- `try` mở đầu cấu trúc.
- Dấu ngoặc tròn `(...)` là **resource specification**, chứa một hoặc nhiều khai báo tài nguyên.
- Mỗi tài nguyên phải có kiểu tương thích với `java.lang.AutoCloseable`.
- `resourceInitializer` tạo/nhận đối tượng; nếu khởi tạo thành công, tài nguyên thuộc phạm vi được TWR quản lý.
- Dấu ngoặc nhọn `{...}` chứa công việc chính.
- Kết thúc khối (bình thường, `return`, `break`, `continue` hợp lệ, hoặc exception), các tài nguyên đã khởi tạo được đóng theo quy tắc.
- `catch` và `finally` có thể được gắn thêm nếu cần xử lý lỗi/dọn các trạng thái khác; **không bắt buộc phải có**.

Ví dụ tối thiểu:

```java
try (java.io.StringReader reader = new java.io.StringReader("Java")) {
    System.out.println(reader.read());
}
```

Đây là một đoạn code bên trong method, không phải file độc lập. `StringReader` hỗ trợ `Closeable` nên dùng được trong TWR. `reader.read()` trả về mã Unicode của ký tự đầu tiên (ở đây ký tự `J`); ví dụ chỉ minh họa cú pháp.

### 2.1. Một tài nguyên hoặc nhiều tài nguyên

TWR với nhiều tài nguyên trong Java 7:

```java
try (FirstResource first = openFirst();
     SecondResource second = openSecond()) {
    // Dùng cả first và second.
}
```

Đây là **mã minh họa**; `FirstResource`/`SecondResource`/`openFirst()`/`openSecond()` cần được định nghĩa. Dấu `;` ngăn cách các tài nguyên; có thể đặt xuống dòng để dễ đọc. Java khởi tạo theo thứ tự **từ trái sang phải**, sau đó đóng theo thứ tự **ngược lại**.

Ví dụ thực tế với reader/writer:

```java
try (java.io.BufferedReader reader =
                 new java.io.BufferedReader(new java.io.FileReader("in.txt"));
     java.io.BufferedWriter writer =
                 new java.io.BufferedWriter(new java.io.FileWriter("out.txt"))) {
    String line;
    while ((line = reader.readLine()) != null) {
        writer.write(line);
        writer.newLine();
    }
}
```

Đây là đoạn dùng I/O thật, cần đặt trong method có `throws IOException` hoặc handler phù hợp và có hai tệp/dường dẫn thích hợp. Khi rời khối, `writer` được đóng **trước** `reader`, không phụ thuộc thứ tự ta sử dụng chúng trong thân.

### 2.2. Có thể dùng `catch` và `finally` cùng TWR

```java
try (MyResource resource = open()) {
    use(resource);
} catch (java.io.IOException e) {
    report(e);
} finally {
    finishRequest();
}
```

Đoạn chỉ là mẫu, `MyResource` và các method chưa được định nghĩa. Điểm đáng nhớ là **đóng tài nguyên diễn ra trước khi thực thi các `catch`/`finally` gắn với cấu trúc đó**. Vì vậy handler có thể nhận cả lỗi từ thân `try` lẫn lỗi phát sinh trong quá trình `close()`, tùy kiểu và thứ tự ngoại lệ.

Không cần viết `finally { resource.close(); }` chỉ vì muốn đóng lại tài nguyên đã được TWR quản lý. Đóng thủ công lần hai có thể gây lỗi hoặc side effect tùy implementation; để TWR chịu trách nhiệm đóng.

## 3. Điều kiện kiểu: tài nguyên phải là `AutoCloseable`

`java.lang.AutoCloseable` được đưa vào Java 7; interface này có phương thức:

```java
public interface AutoCloseable {
    void close() throws Exception;
}
```

Đây là **khai báo API minh họa**, không phải toàn bộ mã nguồn JDK. `AutoCloseable` thiết lập giao thức đóng tài nguyên khi thoát phạm vi.

Các kiểu quen thuộc:

- `java.io.InputStream`, `OutputStream`, `Reader` và `Writer` thuộc nhánh các kiểu đóng được trong hệ I/O.
- `java.io.Closeable` kế thừa `AutoCloseable`; `close()` của `Closeable` khai báo `throws IOException`, hẹp hơn `throws Exception`.
- `java.sql.Connection`, `Statement` và `ResultSet` hỗ trợ `AutoCloseable`, với phương thức đóng có thể ném `SQLException`.
- Lớp tự tạo có thể `implements AutoCloseable` để đưa vòng đời riêng vào TWR.

Điều kiện không phải “tài nguyên bắt buộc là file” hoặc “bắt buộc khai báo checked exception”. Bất kỳ đối tượng nào triển khai giao thức `AutoCloseable` phù hợp đều có thể được quản lý.

### 3.1. Hai interface liên quan nhưng không đồng nhất

| Đặc điểm | `AutoCloseable` | `Closeable` |
| --- | --- | --- |
| Xuất hiện | Java 7 | Trước Java 7; từ Java 7 là subtype của `AutoCloseable` |
| Phương thức cốt lõi | `close() throws Exception` | `close() throws IOException` |
| Kiểu lỗi checked tối đa theo chữ ký interface | `Exception` | `IOException` |
| Có thể dùng trong TWR? | Có | Có |
| Giao ước về đóng lặp lại | Không bảo đảm mọi implementation idempotent | Giao ước của `Closeable.close()` yêu cầu đóng resource đã đóng không có tác dụng |

Nếu khai báo tài nguyên dưới kiểu `AutoCloseable`, compiler có thể buộc method xử lý/khai báo `Exception` ngay cả khi đối tượng runtime cụ thể có `close()` không ném checked exception. Với biến kiểu cụ thể hơn, compiler có thể nhìn thấy chữ ký `close()` hẹp hơn.

Lưu ý: các lớp `AutoCloseable` có thể tự định nghĩa `close()` không khai báo checked exception nào, ví dụ:

```java
final class SimpleResource implements AutoCloseable {
    @Override
    public void close() {
        System.out.println("Đã đóng");
    }
}
```

Đây là một lớp hợp lệ nhưng chưa có `main`. Không cần `throws Exception` ở `close()` vì implementation được phép thu hẹp tập checked exception của interface.

### 3.2. Code nào không dùng được trong resource specification?

Các kiểu không thực hiện `AutoCloseable` không hợp lệ:

```java
// Ví dụ CỐ Ý SAI, không biên dịch:
try (String message = "hello") {
    System.out.println(message);
}
```

`String` không triển khai `AutoCloseable`. Việc biến “có thể thoát khỏi phạm vi” không đủ; TWR cần khả năng gọi `close()` theo interface.

Một lỗi khác là quên **dấu ngoặc tròn** hoặc viết danh sách tài nguyên như tham số thông thường:

```java
// CỐ Ý SAI:
try (new java.io.StringReader("x")) {
    // Trong Java 7 không được dùng biểu thức tạo object trực tiếp như khai báo.
}
```

Trong cú pháp Java 7, resource phải được khai báo bằng biến trong `(...)`. Java 9 mở rộng khả năng dùng một biến đã khai báo sẵn và effectively final, được giải thích ở Mục 8.

## 4. Chương trình Java 17 số 1: tự đóng sau đường thành công và thất bại

Ví dụ không phụ thuộc file trên đĩa. Ta tự cài một resource để quan sát chính xác `close()` được gọi khi nào.

Tạo tệp `TryWithResourcesBasics.java`:

```java
public class TryWithResourcesBasics {
    static final class TrackedResource implements AutoCloseable {
        private final String name;

        TrackedResource(String name) {
            this.name = name;
            System.out.println("Mở " + name);
        }

        void use() {
            System.out.println("Dùng " + name);
        }

        @Override
        public void close() {
            System.out.println("Đóng " + name);
        }
    }

    static void run(boolean fail) {
        try (TrackedResource resource = new TrackedResource("R")) {
            resource.use();
            if (fail) {
                throw new IllegalStateException("Thao tác lỗi");
            }
            System.out.println("Thân try thành công");
        } catch (IllegalStateException e) {
            System.out.println("Bắt: " + e.getMessage());
        } finally {
            System.out.println("Kết thúc run");
        }
    }

    public static void main(String[] args) {
        run(false);
        System.out.println("---");
        run(true);
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 TryWithResourcesBasics.java
java TryWithResourcesBasics
```

Stdout chính xác:

```text
Mở R
Dùng R
Thân try thành công
Đóng R
Kết thúc run
---
Mở R
Dùng R
Đóng R
Bắt: Thao tác lỗi
Kết thúc run
```

**Phân tích:**

1. Lượt `run(false)` tạo tài nguyên, sử dụng và hoàn tất thân `try`. `close()` được gọi **trước khi** ra khỏi cấu trúc, rồi chạy `finally`.
2. Lượt `run(true)` ném `IllegalStateException` ngay trong thân. Dòng “Thân try thành công” không chạy; TWR vẫn gọi `close()` rồi mới chạy `catch`.
3. `catch` xử lý lỗi nghiệp vụ, không phải nơi chịu trách nhiệm đóng resource. `finally` ở đây chỉ minh họa tác vụ chung ngoài việc đóng tài nguyên.
4. Resource được mở mới mỗi lần gọi; đầu ra không phụ thuộc GC, thời gian hoặc file hệ thống.

**Không nên kết luận mọi chương trình luôn hoàn thành close:** TWR thực hiện lệnh đóng theo luồng điều khiển Java; dừng JVM/process bất thường, lỗi native hoặc sự cố nghiêm trọng có thể làm điều này không diễn ra. Những sự kiện đó cần chính sách độ bền dữ liệu riêng.

## 5. Thứ tự khởi tạo và thứ tự đóng nhiều tài nguyên

Java khởi tạo theo thứ tự tài nguyên xuất hiện trong `(...)`, từ **trái sang phải**. Khi rời phạm vi TWR, Java đóng chúng theo **thứ tự ngược lại**: tài nguyên cuối được khai báo sẽ được đóng trước.

Tạo `ResourceCloseOrderDemo.java`:

```java
public class ResourceCloseOrderDemo {
    static final class NamedResource implements AutoCloseable {
        private final String name;

        NamedResource(String name) {
            this.name = name;
            System.out.println("Mở " + name);
        }

        @Override
        public void close() {
            System.out.println("Đóng " + name);
        }
    }

    public static void main(String[] args) {
        try (NamedResource first = new NamedResource("A");
             NamedResource second = new NamedResource("B");
             NamedResource third = new NamedResource("C")) {
            System.out.println("Thân try");
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 ResourceCloseOrderDemo.java
java ResourceCloseOrderDemo
```

Stdout:

```text
Mở A
Mở B
Mở C
Thân try
Đóng C
Đóng B
Đóng A
```

Thứ tự này giúp viết pipeline: nếu tài nguyên `B` sử dụng tài nguyên `A`, khai báo `A` trước rồi `B` sau thường cho phép đóng `B` trước khi đóng `A`.

### 5.1. Nếu một resource initializer thất bại?

Một ưu điểm quan trọng: những resource **đã khởi tạo thành công trước đó** vẫn được đóng, ngay cả khi việc tạo tài nguyên sau bị lỗi.

Ví dụ ý tưởng:

```java
try (Resource first = openFirst();
     Resource second = openSecondThatThrows()) {
    work(first, second);
}
```

Đây là mã minh họa; các lớp và method chưa được định nghĩa. Nếu `openFirst()` thành công nhưng `openSecondThatThrows()` ném exception, `first` vẫn được đóng theo quy tắc. `second` chưa được khởi tạo thành công nên không được đóng như một resource đã có. Nếu `openSecondThatThrows()` đã cấp phát tài nguyên bên trong rồi tự ném mà chưa trả object, trách nhiệm dọn nội bộ của nó vẫn thuộc implementation của chính hàm khởi tạo; TWR bên ngoài không thể đóng một tài nguyên chưa nhận được.

### 5.2. Khởi tạo resource có giá trị `null`

Một initializer có thể cho ra `null` đối với kiểu tham chiếu resource. Quy tắc TWR **không gọi `close()` trên resource có giá trị `null`**. Điều này khác với việc gọi trực tiếp `resource.close()` khi `resource == null`, vốn ném `NullPointerException`.

Dù ngôn ngữ xử lý được trường hợp này, API không nên dùng giá trị `null` làm resource hợp lệ một cách mơ hồ. Cần thể hiện rõ trường hợp “không mở được” qua hợp đồng, exception hoặc kết quả có cấu trúc phù hợp.

## 6. Trường hợp đặc biệt: cả thân try và `close()` đều ném exception

Đây là lý do TWR tốt hơn một `finally` đơn giản trong nhiều trường hợp. Java không bỏ mất lỗi chính chỉ vì `close()` cũng lỗi.

**Khái niệm:**

- **Primary exception:** ngoại lệ thoát ra từ thân `try`/giai đoạn thao tác đang được ưu tiên giữ nguyên.
- **Suppressed exception:** ngoại lệ phát sinh khi đóng tài nguyên trong lúc một ngoại lệ chính đã tồn tại; được gắn vào đối tượng chính qua `Throwable.addSuppressed(...)` theo cơ chế TWR.
- `Throwable.getSuppressed()` trả về mảng các ngoại lệ đã được ghi nhận theo cách này.
- `getCause()` và `getSuppressed()` là hai quan hệ khác nhau: cause diễn tả chuỗi nguyên nhân, suppressed ghi nhận lỗi phụ trong quá trình dọn dẹp.

### 6.1. Ví dụ Java 17 số 3: primary và suppressed

Tạo `SuppressedExceptionDemo.java`:

```java
import java.io.IOException;

public class SuppressedExceptionDemo {
    static final class BrokenClose implements AutoCloseable {
        @Override
        public void close() throws IOException {
            System.out.println("Đang đóng");
            throw new IOException("Lỗi đóng");
        }
    }

    public static void main(String[] args) {
        try (BrokenClose resource = new BrokenClose()) {
            System.out.println("Đang xử lý");
            throw new IOException("Lỗi xử lý");
        } catch (IOException e) {
            System.out.println("Primary: " + e.getMessage());
            System.out.println("Số suppressed: "
                    + e.getSuppressed().length);
            System.out.println("Suppressed: "
                    + e.getSuppressed()[0].getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 SuppressedExceptionDemo.java
java SuppressedExceptionDemo
```

Stdout:

```text
Đang xử lý
Đang đóng
Primary: Lỗi xử lý
Số suppressed: 1
Suppressed: Lỗi đóng
```

**Luồng thực thi:**

1. Resource được tạo thành công.
2. Thân `try` in “Đang xử lý” rồi ném `IOException("Lỗi xử lý")`.
3. Java vẫn gọi `resource.close()`; method in “Đang đóng” rồi ném `IOException("Lỗi đóng")`.
4. Exception từ thân là **primary**. Lỗi từ `close()` được **suppress** vào exception chính.
5. `catch (IOException e)` nhận exception chính, dùng `getSuppressed()` để xem lỗi khi đóng.

Nếu chỉ dùng `finally { resource.close(); }` trong một mẫu cũ không xử lý riêng lỗi đóng, exception từ `close()` có thể thay thế lỗi ban đầu và làm mất thông tin quan trọng về nguyên nhân thao tác.

### 6.2. Nếu thân `try` thành công nhưng `close()` ném lỗi?

Khi chưa có primary exception từ thân `try`, lỗi phát sinh lúc `close()` có thể trở thành **exception chính thoát khỏi construct**. Nói “TWR luôn ưu tiên lỗi từ thân `try`” chỉ đúng **khi lỗi thân tồn tại**.

Với nhiều tài nguyên, nếu thân thành công nhưng một hoặc nhiều `close()` thất bại, lỗi đóng từ **resource được đóng đầu tiên** (resource khai báo cuối) là lỗi chính và các lỗi đóng sau có thể trở thành suppressed exceptions.

### 6.3. Ví dụ Java 17 số 4: hai lần đóng đều lỗi

Tạo `MultipleCloseFailuresDemo.java`:

```java
import java.io.IOException;

public class MultipleCloseFailuresDemo {
    static final class Failing implements AutoCloseable {
        private final String name;

        Failing(String name) {
            this.name = name;
        }

        @Override
        public void close() throws IOException {
            System.out.println("Đóng " + name);
            throw new IOException("close " + name);
        }
    }

    public static void main(String[] args) {
        try (Failing first = new Failing("A");
             Failing second = new Failing("B")) {
            System.out.println("Thân thành công");
        } catch (IOException e) {
            System.out.println("Primary: " + e.getMessage());
            System.out.println("Suppressed count: "
                    + e.getSuppressed().length);
            System.out.println("Suppressed first: "
                    + e.getSuppressed()[0].getMessage());
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 MultipleCloseFailuresDemo.java
java MultipleCloseFailuresDemo
```

Stdout:

```text
Thân thành công
Đóng B
Đóng A
Primary: close B
Suppressed count: 1
Suppressed first: close A
```

Resource `B` được đóng trước. Khi `B.close()` thất bại, nó trở thành primary. Java tiếp tục đóng `A`; exception của `A.close()` được gắn suppressed.

**Lưu ý chẩn đoán:** nếu chỉ in `e.getMessage()` rồi bỏ qua suppressed exceptions, rất dễ không nhìn thấy các lỗi đóng thứ cấp. Một logger in stack trace đầy đủ của `Throwable` thường thể hiện các suppressed exceptions; nếu dùng định dạng log tự xây, nên xác nhận nó không làm mất thông tin này.

## 7. `catch` và `finally` chạy vào lúc nào?

Với cấu trúc:

```java
try (Resource r = open()) {
    use(r);
} catch (Exception e) {
    handle(e);
} finally {
    reportEnd();
}
```

Đây là mẫu minh họa phụ thuộc các định nghĩa phù hợp. Thứ tự khái quát:

```text
Khởi tạo tài nguyên
        ↓
Thân try
        ↓
Đóng tài nguyên theo thứ tự ngược
        ↓
catch phù hợp (nếu exception đang lan truyền)
        ↓
finally (nếu có và không bị sự cố kết thúc bất thường bên ngoài)
        ↓
Kết quả/exception tiếp tục về caller
```

Đối với `return` trong thân `try`, TWR cũng đóng các tài nguyên trước khi lời gọi hoàn tất bình thường. Nếu `close()` ném exception sau khi giá trị trả về đã được tính, lỗi đóng có thể khiến method **không hoàn tất bằng return dự kiến**. Không nên giả định rằng `return` miễn trách nhiệm đóng.

### 7.1. Khối `catch` có thể bắt cả lỗi thân và lỗi close

Trong ví dụ `SuppressedExceptionDemo`, `catch(IOException)` nhận lỗi thân; với ví dụ `MultipleCloseFailuresDemo`, nó nhận lỗi đóng. Cùng cú pháp nhưng nguồn primary khác nhau.

Compiler xét checked exceptions của initializer, các lời gọi trong thân **và `close()`**, nên ví dụ sau vẫn cần bắt/khai báo `IOException` ngay cả khi thân khối chỉ in văn bản:

```java
// Phương thức minh họa, Resource.close() throws IOException.
try (Resource r = new Resource()) {
    System.out.println("Không có I/O trong thân");
} catch (java.io.IOException e) {
    System.out.println("Đóng lỗi: " + e.getMessage());
}
```

Đây là đoạn giải thích giả định `Resource` đã được định nghĩa. Khi bạn thay resource bằng lớp có `close()` không khai báo checked exception, khả năng bắt checked `IOException` cũng cần được xem lại theo chính xác chữ ký của initializer và thân khối.

### 7.2. TWR không phải thứ tự xử lý `catch` thông thường bị đảo ngược

Java không gọi `catch` trước rồi mới đóng resource. Resource được đóng khi rời phần TWR; các handler gắn kèm chạy sau quy trình này. Đây là điểm khác với ý tưởng đặt `close()` trong chính `catch`, vốn bỏ sót đường thành công.

## 8. Cú pháp Java 7 khác gì với Java 9 và Java 10+?

Đây là điểm quan trọng khi đề bài yêu cầu **“từ Java 7”**: cần phân biệt tính năng ra đời với những cú pháp được bổ sung ở phiên bản sau.

### 8.1. Java 7: khai báo biến resource ngay trong ngoặc try

```java
try (java.io.BufferedReader reader =
             new java.io.BufferedReader(
                     new java.io.StringReader("Hello"))) {
    System.out.println(reader.readLine());
}
```

Đoạn này dùng cú pháp TWR từ Java 7. Biến `reader` được khai báo trong resource specification; nó không thể bị gán lại sau đó trong thân `try`. Sau khi TWR kết thúc, biến resource khai báo kiểu này nằm ngoài phạm vi sử dụng thông thường của code sau đó.

### 8.2. Java 9: dùng biến đã khai báo sẵn nếu `final` hoặc effectively final

Từ **Java 9**, có thể viết:

```java
java.io.BufferedReader reader =
        new java.io.BufferedReader(
                new java.io.StringReader("Hello"));
try (reader) {
    System.out.println(reader.readLine());
}
```

Đây là đoạn minh họa trong method, hợp lệ trên Java 9+ (gồm Java 17). `reader` được khai báo trước `try`, không bị gán lại sau khi khởi tạo, nên là **effectively final**. Cú pháp **`try (reader)` không có khai báo biến bên trong** không hợp lệ nếu biên dịch với chế độ ngôn ngữ Java 7.

Nếu `reader` được gán sang đối tượng khác sau khai báo, nó không còn effectively final và không đủ điều kiện dùng trực tiếp trong resource specification Java 9.

Đọc đúng ý nghĩa:

- `try (reader)` **không tự khởi tạo** resource mới; nó dùng đối tượng mà biến đã tham chiếu.
- Khi khối kết thúc, resource được **đóng**, dù biến `reader` vẫn còn nằm trong phạm vi từ vựng ngoài `try`.
- Biến còn tồn tại **không** có nghĩa đối tượng đã đóng có thể tiếp tục sử dụng. Phải tuân theo hợp đồng của resource.
- Nếu mở resource trước khi vào `try`, một exception xảy ra **giữa lúc mở và lúc bắt đầu `try`** có thể khiến resource chưa được TWR quản lý. Khi có thể, khai báo và khởi tạo ngay trong `try(...)` thường đơn giản hơn.

### 8.3. Java 10+: `var` trong khai báo resource

Java 10 giới thiệu local-variable type inference `var`, nên trong Java 10+ có thể viết:

```java
try (var reader =
             new java.io.BufferedReader(
                     new java.io.StringReader("Hello"))) {
    System.out.println(reader.readLine());
}
```

Đây cũng là đoạn minh họa hợp lệ ở Java 17. TWR xuất hiện từ Java 7, nhưng cú pháp `var` **không** được hỗ trợ ở Java 7 hoặc Java 9. Chọn cú pháp tùy phiên bản Java của dự án; nếu muốn ví dụ chạy trên Java 7, dùng khai báo kiểu tường minh trong ngoặc tài nguyên.

### 8.4. Tóm tắt phiên bản

| Phiên bản Java | Cú pháp tài nguyên tiêu biểu | Ghi nhớ |
| --- | --- | --- |
| Java 7/8 | `try (BufferedReader r = new BufferedReader(...))` | Khai báo resource trực tiếp trong `try`; có thể nhiều resource phân cách `;`. |
| Java 9 | `BufferedReader r = ...; try (r)` | Dùng biến có sẵn `final` hoặc effectively final. |
| Java 10+ | `try (var r = new BufferedReader(...))` | Dùng `var` cho biến resource cục bộ có initializer. |
| Java 17 | Hỗ trợ cả ba dạng phù hợp | Lựa chọn tùy mục tiêu tương thích mã nguồn và kiểu dữ liệu. |

**Đừng nói** “Java 7 cho phép `try (existingVariable)`” hoặc “TWR chỉ có từ Java 9”. Cả hai nhận định đều sai.

## 9. Chương trình Java 17 số 5: dùng biến effectively final

Tạo `ExistingResourceDemo.java`:

```java
import java.io.BufferedReader;
import java.io.StringReader;
import java.io.IOException;

public class ExistingResourceDemo {
    public static void main(String[] args) throws IOException {
        BufferedReader reader =
                new BufferedReader(new StringReader("Java 17"));

        try (reader) {
            System.out.println("Đọc: " + reader.readLine());
        }
        System.out.println("Đã ra khỏi try-with-resources");
    }
}
```

Chạy bằng JDK 17:

```bash
javac -encoding UTF-8 ExistingResourceDemo.java
java ExistingResourceDemo
```

Stdout:

```text
Đọc: Java 17
Đã ra khỏi try-with-resources
```

`reader` vẫn là biến cục bộ hợp lệ sau khối `try`, nhưng tài nguyên đã bị đóng. Chương trình chủ động **không gọi `readLine()` sau khi đóng**; việc tiếp tục sử dụng tùy hành vi cụ thể của resource và thường không hợp lệ với stream/reader.

Nếu đổi code thành:

```java
BufferedReader reader =
        new BufferedReader(new StringReader("a"));
reader = new BufferedReader(new StringReader("b"));
try (reader) {
    // Không biên dịch: reader đã bị gán lại, không effectively final.
}
```

thì Java 17 từ chối dạng `try (reader)` do biến đã được gán lại. Đây là **đoạn cố ý không biên dịch**; cần dùng một biến resource mới được khai báo trong ngoặc hoặc thay đổi thiết kế vòng đời.

## 10. Cách thiết kế lớp `AutoCloseable` an toàn

Bạn có thể đưa đối tượng tự tạo vào TWR bằng cách triển khai `AutoCloseable`:

```java
final class Session implements AutoCloseable {
    private boolean closed;

    void send(String message) {
        if (closed) {
            throw new IllegalStateException("Session đã đóng");
        }
        System.out.println("Gửi: " + message);
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            System.out.println("Đóng session");
        }
    }
}
```

Đây là định nghĩa lớp không có `main`; nó có thể dùng cùng một program Java có entrypoint khác. Điểm thiết kế:

- Bảo đảm resource ở trạng thái hợp lệ từ lúc constructor hoàn tất.
- `close()` nên giải phóng đúng các resource mà instance sở hữu; không nên tùy tiện đóng tài nguyên chia sẻ đang được đối tượng khác quản lý.
- Cân nhắc tính idempotent nếu thích hợp: ví dụ trên đóng lần hai không in lại. Với `AutoCloseable` nói chung, không được giả định mọi implementation đều idempotent.
- Khi đã đóng, các thao tác yêu cầu trạng thái mở nên từ chối rõ ràng.
- Nếu `close()` có thể thất bại, chọn kiểu checked exception phù hợp và giải thích trạng thái còn lại trong API.
- Khi resource được sử dụng giữa nhiều thread, cần chính sách đồng bộ hóa riêng; TWR không tạo thread safety.

Ví dụ sử dụng:

```java
try (Session session = new Session()) {
    session.send("Xin chào");
}
```

Đây là đoạn trong method; khi chạy, in `Gửi: Xin chào` rồi `Đóng session`. Không cần `catch` hoặc `throws` vì `Session.close()` không khai báo checked exception.

### 10.1. Quyền sở hữu tài nguyên (ownership) là quan trọng

Không phải biến `AutoCloseable` nào cũng nên đưa vào một TWR ngắn. Nếu caller **không sở hữu** resource, việc tự đóng có thể phá hoạt động của bên khác.

Ví dụ:

- Một method tự mở file để đọc: thường **sở hữu** reader, nên nên đóng.
- Một method nhận `InputStream` do caller truyền vào: phải đọc hợp đồng; có thể caller muốn stream vẫn mở để dùng tiếp.
- Connection pool: một đối tượng `Connection` có thể đại diện connection được “trả về pool” khi gọi `close()`; ngữ nghĩa triển khai do thư viện/pool quy định.
- `System.in`: đóng wrapper quanh standard input có thể đóng luôn luồng nhập chuẩn, ảnh hưởng phần còn lại của chương trình.

**Mẫu suy nghĩ:** “Ai mở/thu nhận resource và ai chịu trách nhiệm kết thúc vòng đời?” Đừng áp dụng TWR mù quáng lên đối tượng được chia sẻ.

## 11. TWR có thay thế `finally` hoàn toàn không?

**Không.** TWR chuyên xử lý tài nguyên `AutoCloseable`. `finally` vẫn hữu ích cho những hành động không gắn với một resource `AutoCloseable` hoặc cần thực hiện dù thao tác thành công hay thất bại, như ghi nhận mốc kết thúc, giải phóng một khóa theo hợp đồng, hoặc khôi phục context thread theo chính sách.

Ví dụ minh họa:

```java
try (Resource resource = acquire()) {
    perform(resource);
} catch (java.io.IOException e) {
    reportFailure(e);
} finally {
    recordCompletion();
}
```

Đây là mã mẫu phụ thuộc resource và các method chưa được định nghĩa. Khi resource đã được TWR tự đóng, `finally` không cần đóng nó lần nữa. Nếu có lock, transaction hoặc trạng thái riêng cần hoàn tác, phải thiết kế trách nhiệm tương ứng chứ không ngầm giao hết cho `close()`.

### 11.1. Không biến đóng resource thành xử lý ngoại lệ nghiệp vụ

Một kết nối đóng thành công **không** chứng minh transaction đã commit/rollback theo ý định. Một writer đóng thành công **không** có nghĩa toàn bộ file đã được ghi một cách bền vững trước sự cố mất điện. Nếu cần tính nguyên tử, độ bền hay khả năng phục hồi, hãy chọn transaction, ghi vào file tạm rồi thay thế, kiểm tra kết quả hoặc giao thức ứng dụng phù hợp.

TWR chỉ quản lý **việc gọi `close()`** trên những resource đã được cấp cho nó trong đường điều khiển bình thường của Java.

### 11.2. Exception trong `finally` có thể che lỗi chính

Nếu phần dọn dẹp thủ công trong `finally` ném exception, nó có thể ghi đè exception đang lan truyền từ thân `try`. Đây là một trong các lý do dùng TWR: Java tự tổ chức suppressed exceptions khi `close()` cũng thất bại.

Không nên dùng `return` hoặc ném exception mới trong `finally` nếu không chủ động chấp nhận việc thay đổi kết quả/che lỗi trước đó.

## 12. Try-with-resources và checked exception

Các nguồn checked exception mà compiler có thể yêu cầu xử lý gồm:

1. Constructor/initializer của resource.
2. Các method được gọi trong thân `try`.
3. Phương thức `close()` trên kiểu resource theo chữ ký mà compiler nhìn thấy.

Ví dụ `AutoCloseable` có `close() throws Exception` nên dùng qua biến có kiểu interface này có thể làm method phải khai báo `throws Exception`; qua một lớp cụ thể với `close() throws IOException` có thể chỉ cần `throws IOException`.

So sánh hai ví dụ **trong thân lớp**:

```java
static void broad() throws Exception {
    try (AutoCloseable resource = createResource()) {
        System.out.println("Đang xử lý");
    }
}
```

`createResource()` chưa được định nghĩa; giả sử bản thân nó không có checked exception khác. Vì biến `resource` có kiểu tĩnh `AutoCloseable`, compiler xét `close() throws Exception`. Đây là mã minh họa.

```java
static void narrow() throws java.io.IOException {
    try (java.io.BufferedReader reader =
                 new java.io.BufferedReader(
                         new java.io.StringReader("x"))) {
        System.out.println(reader.readLine());
    }
}
```

`BufferedReader` có phương thức đọc/đóng công bố `IOException`, nên khai báo `throws IOException` phù hợp. Thực tế dữ liệu `StringReader` nằm trong bộ nhớ, nhưng hợp đồng kiểu `BufferedReader` vẫn có checked exception.

### 12.1. `close()` thất bại khi method đã chuẩn bị `return`

Xét ý tưởng:

```java
static String load() throws java.io.IOException {
    try (MyReader reader = createReader()) {
        return "OK";
    }
}
```

Đây là ví dụ minh họa; `MyReader`/`createReader()` chưa có triển khai, giả sử `close() throws IOException`. Java tính giá trị định trả, nhưng vẫn đóng resource trước khi kết thúc lời gọi. Nếu `close()` ném `IOException`, lời gọi có thể thoát bằng exception thay vì trả `"OK"`. Vì vậy đường kết thúc của method phải được thiết kế và kiểm thử với cả lỗi lúc close.

### 12.2. Có nên bắt mọi `Exception` để “nuốt” lỗi đóng?

Không. Nếu đóng thất bại, resource hoặc dữ liệu có thể ở trạng thái chưa xác định. Việc bắt rộng rồi giả báo thành công dễ che nguyên nhân. Khi lỗi cần xử lý, bắt loại phù hợp, ghi nhận cả primary và suppressed exceptions khi có giá trị, và chuyển trạng thái công việc thành thất bại nếu cần.

## 13. TWR trong thực tế: đọc file và xử lý dữ liệu

Ví dụ Java 17 đầy đủ thao tác với file tạm để không cần chuẩn bị đầu vào. Nó tạo một tệp tạm, ghi ba dòng, đọc lại bằng TWR, sau đó xóa file trong `finally` của ví dụ; thao tác xóa được kiểm soát và có thể ném `IOException`.

Tạo `FileResourceDemo.java`:

```java
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileResourceDemo {
    public static void main(String[] args) throws IOException {
        Path path = Files.createTempFile("twr-learning-", ".txt");
        try {
            try (BufferedWriter writer =
                         Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                writer.write("Java");
                writer.newLine();
                writer.write("Try-with-resources");
                writer.newLine();
            }

            try (BufferedReader reader =
                         Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
```

Biên dịch/chạy bằng JDK 17+:

```bash
javac -encoding UTF-8 FileResourceDemo.java
java FileResourceDemo
```

**Stdout khi các thao tác file thành công:**

```text
Java
Try-with-resources
```

Đây là ví dụ I/O thực, nên có thể thất bại do quyền truy cập, thiếu dung lượng, tệp bị tác nhân khác thay đổi hoặc lỗi hệ thống. **Không** coi hai dòng trên là kết quả bảo đảm nếu môi trường không cho phép tạo/xóa file; khi lỗi, chương trình có thể ném `IOException` và in stack trace ra stderr vì `main throws IOException`.

Điểm nổi bật:

- TWR thứ nhất sở hữu `BufferedWriter`, nên tự đóng sau khi ghi, giúp hoàn tất quá trình đóng writer trước khi mở reader.
- TWR thứ hai sở hữu `BufferedReader` và tự đóng sau vòng lặp.
- `finally` của khối ngoài chịu trách nhiệm dọn **tệp tạm**, khác với đóng stream.
- `StandardCharsets.UTF_8` làm encoding rõ ràng, tránh phụ thuộc charset mặc định.
- Dùng hai TWR liên tiếp thể hiện vòng đời tách biệt của writer và reader; không cần quản lý thủ công các lệnh `close()`.

### 13.1. Có thể vừa đọc vừa ghi trong một TWR không?

Có, nếu muốn đồng thời sở hữu các resource và không cần đợi writer được đóng trước khi đọc. Tuy nhiên với dữ liệu file cần đọc sau khi ghi, một writer còn buffer có thể chưa đẩy hết dữ liệu ra ngoài. Đóng hoặc `flush()` đúng thời điểm trước khi đọc giúp làm rõ hợp đồng. `flush()` không đồng nghĩa dữ liệu đã bền vững trên đĩa khi mất điện.

## 14. Những lỗi phổ biến với try-with-resources

| Hiểu nhầm hoặc lỗi | Vấn đề | Cách xử lý |
| --- | --- | --- |
| “TWR chỉ dùng để đọc file.” | Mọi `AutoCloseable` phù hợp đều có thể dùng. | Dùng cho JDBC, stream, resource tùy biến khi có ownership rõ. |
| “Đóng tài nguyên chỉ xảy ra nếu thân try thành công.” | `close()` cũng được gọi khi rời khối vì exception. | Kiểm thử cả đường thành công và lỗi. |
| “Có thể đưa `String` vào ngoặc try.” | `String` không triển khai `AutoCloseable`. | Dùng kiểu đúng hoặc chuyển logic về `try` thường. |
| “Java 7 cho phép `try (reader)` với biến ngoài.” | Cú pháp biến có sẵn được mở rộng từ Java 9. | Java 7/8 dùng khai báo resource trực tiếp trong `(...)`. |
| “Java 7 hỗ trợ `var` cho resource.” | `var` xuất hiện từ Java 10. | Dùng kiểu tường minh nếu cần tương thích Java 7. |
| “Resource nào đóng trước cũng được.” | TWR đóng ngược thứ tự khai báo. | Sắp xếp resource phụ thuộc sao cho thứ tự đóng phù hợp. |
| “Nếu initializer thứ hai ném thì resource đầu không được đóng.” | Các resource đã khởi tạo thành công được dọn khi khởi tạo tiếp thất bại. | Đảm bảo constructor không làm rò rỉ tài nguyên chưa bàn giao. |
| “Khi cả thân và close lỗi, close sẽ làm mất lỗi thân.” | TWR giữ lỗi thân làm primary, close có thể thành suppressed. | Xem `getSuppressed()` và log đầy đủ. |
| “Nếu thân không lỗi, close lỗi sẽ bị bỏ qua.” | Lỗi close có thể trở thành primary. | Bắt/khai báo checked exception từ `close()`. |
| “TWR tự động commit/rollback database.” | Đóng resource không tự bảo đảm toàn vẹn giao dịch. | Sử dụng chính sách transaction/commit/rollback tường minh theo API. |
| “Biến khai báo ngoài `try` vẫn dùng được nên resource còn mở.” | Biến có thể còn trong scope nhưng resource đã đóng. | Không thao tác sau close trừ khi API cho phép. |
| “`catch (IOException)` luôn hợp lệ vì TWR luôn có I/O.” | TWR có thể quản lý resource không khai báo `IOException`. | Dựa trên kiểu initializer, thân, và `close()` thực tế. |
| “Đóng một stream được chia sẻ không ảnh hưởng bên ngoài.” | `close()` có thể kết thúc resource do caller/ứng dụng khác dùng. | Thỏa thuận quyền sở hữu trước khi dùng TWR. |
| “Có TWR nên không cần finally nữa.” | `finally` vẫn hữu ích với hành động không phải `AutoCloseable`. | Phân chia trách nhiệm rõ; không đóng lại resource vô cớ. |
| “Có thể gọi reader sau TWR nếu reader chưa bị GC.” | Vòng đời resource không phụ thuộc GC. | Tôn trọng trạng thái đã đóng. |

## 15. Các câu hỏi thường gặp

### 15.1. Có thể viết `try (...)` mà không có `catch` và `finally` không?

**Có**, nếu đó là **try-with-resources** hợp lệ. Với `try` thông thường (không có resource specification), Java yêu cầu có `catch`, `finally`, hoặc cả hai. TWR cho phép không có cả hai vì chính cấu trúc đã định nghĩa hành vi đóng tài nguyên.

### 15.2. Nhiều resource đóng theo thứ tự nào?

Theo thứ tự **ngược** với khai báo trong `(...)`. Trong `try (A a = ...; B b = ...; C c = ...)`, thứ tự mở A → B → C, thứ tự đóng C → B → A.

### 15.3. `close()` có được gọi nếu trong thân `try` có `return`?

**Có** theo đường thực thi Java bình thường. Giá trị return có thể đã được tính nhưng việc đóng diễn ra trước khi method hoàn thành; lỗi trong `close()` có thể thay thế return.

### 15.4. `close()` có chạy khi resource initializer trả `null`?

Không, resource có giá trị `null` được bỏ qua khi đóng. Nhưng caller vẫn cần hiểu liệu giá trị null có phải một trạng thái hợp lệ theo hợp đồng.

### 15.5. TWR có tự khởi động giao dịch database không?

Không. JDBC `Connection` có giao thức transaction riêng. TWR giúp đóng connection, statement, result set; commit/rollback phải được thiết kế theo cấu hình auto-commit, giao thức driver/pool và yêu cầu nghiệp vụ. Không suy luận transaction thành công chỉ vì `close()` chạy.

### 15.6. Nếu thân try và `close()` cùng thất bại thì handler bắt exception nào?

Nếu thân ném exception và khi đóng lại có exception, exception từ thân thường là primary, các lỗi đóng được gắn dưới `getSuppressed()`. Handler so kiểu **primary Throwable đang được ném**, không tự chọn một suppressed exception có kiểu tương thích.

### 15.7. `AutoCloseable` và `Closeable` khác gì?

`AutoCloseable.close()` khai báo `throws Exception`; `Closeable.close()` khai báo `throws IOException` và có hợp đồng đóng lặp lại cụ thể. Cả hai có thể dùng trong TWR. Compiler xem chữ ký theo kiểu tĩnh của resource.

### 15.8. Tại sao dùng biến existing resource trong Java 9+ vẫn cần cẩn thận?

Nếu biến được khai báo bên ngoài, resource vẫn được đóng sau TWR; các đoạn code ngoài có thể còn giữ tham chiếu và vô tình thao tác trên resource đã đóng. Ngoài ra việc mở resource ngoài khối khiến bạn cần suy nghĩ về mọi code nằm giữa lúc mở và lúc bắt đầu quản lý TWR.

## 16. Bài tập tự luyện

### Bài 1 — Chuyển `finally` sang TWR

Cho method minh họa:

```java
static String firstLine(String path) throws java.io.IOException {
    java.io.BufferedReader reader = null;
    try {
        reader = new java.io.BufferedReader(
                new java.io.FileReader(path));
        return reader.readLine();
    } finally {
        if (reader != null) {
            reader.close();
        }
    }
}
```

Viết lại bằng `try (BufferedReader reader = ...) { return reader.readLine(); }`. So sánh số dòng, ý định và điều gì xảy ra nếu `readLine()` cũng như `close()` cùng ném lỗi.

### Bài 2 — Dự đoán thứ tự stdout

Chạy `ResourceCloseOrderDemo` với thứ tự khai báo `A, B, C`. Sau đó đổi thành `C, A, B`. Dự đoán thứ tự mở và đóng mà không chạy; hãy giải thích tại sao đóng luôn ngược thứ tự khai báo.

### Bài 3 — Lỗi thân và lỗi đóng

Chạy `SuppressedExceptionDemo`. Thay khối thân bằng một lời in không ném lỗi. Khi `close()` vẫn ném `IOException`, dòng primary sẽ đổi như thế nào? `getSuppressed()` trong trường hợp chỉ có một lỗi đóng sẽ có bao nhiêu phần tử?

### Bài 4 — Tự viết `AutoCloseable`

Tạo lớp `TimerScope implements AutoCloseable` ghi nhận tên và in `Start name` ở constructor, `End name` ở `close()`. Dùng TWR để quan sát cả nhánh thành công và ném `IllegalStateException`. Không dựa vào thời gian hệ thống để xuất stdout cố định.

### Bài 5 — So sánh Java 7 và Java 9

Viết hai phiên bản code đọc dòng từ `StringReader`: (A) khai báo `BufferedReader` trong `try (...)`, (B) khai báo trước rồi `try (reader)`. Giải thích phiên bản nào biên dịch với `--release 7` (nếu bộ JDK cho phép biên dịch mức đó) và vì sao phiên bản (B) chỉ hợp lệ từ Java 9. Trong ví dụ Java 17, cả hai cách đều có thể dùng.

### Bài 6 — Tìm lỗi ownership

Một API nhận `InputStream input` do caller cung cấp và chỉ cần đọc 5 byte. Có luôn nên viết `try (input)` để đóng không? Hãy giải thích vì sao cần biết contract về quyền sở hữu, và đề xuất tài liệu hóa việc “method sẽ đóng stream” hoặc “caller chịu trách nhiệm đóng”.

### Bài 7 — Quan hệ checked exceptions

Tạo `AutoCloseable` có `close() throws Exception` nhưng thân `try` không ném checked exception. Khi viết method chứa TWR, compiler đòi hỏi khai báo/handler loại checked nào? Thử thay kiểu resource thành lớp con có `close()` không khai báo checked exception để nhận ra tác dụng của kiểu tĩnh.

### Bài 8 — Kiểm thử khi khởi tạo một resource mới thất bại

Tạo `AutoCloseable` thứ nhất có `close()` in `"closed first"`, initializer thứ hai ném `IllegalStateException`. Xác nhận rằng resource thứ nhất vẫn được đóng. Giải thích vì sao tài nguyên chưa được cấp thành công cho TWR phải tự quản lý phần cấp phát nội bộ của initializer.

## 17. Checklist trước khi áp dụng TWR

- [ ] Tài nguyên thực sự triển khai `AutoCloseable` hoặc `Closeable` chưa?
- [ ] Tôi biết rõ **ai sở hữu** tài nguyên và ai chịu trách nhiệm đóng không?
- [ ] Tất cả resource cần quản lý có được đưa vào `try (...)` trong đúng thứ tự không?
- [ ] Tôi đã phân biệt cú pháp khai báo Java 7 với biến effectively final của Java 9 chưa?
- [ ] Tôi có đang dùng `var` dù dự án cần biên dịch Java 7 hoặc 8 không?
- [ ] Tôi đã xét checked exceptions từ initializer, thân và `close()` chưa?
- [ ] Tôi biết tài nguyên đóng theo thứ tự ngược khi có nhiều resource không?
- [ ] Tôi đã kiểm tra đường lỗi trong initializer làm tài nguyên trước vẫn được đóng chưa?
- [ ] Tôi hiểu primary/suppressed và không vô tình làm mất thông tin chẩn đoán không?
- [ ] Tôi đã tránh đóng lại resource thủ công trong `finally` vô cớ chưa?
- [ ] Nếu có resource còn tham chiếu sau TWR, tôi có tránh dùng nó sai trạng thái không?
- [ ] Tôi đã phân biệt dọn dẹp tài nguyên với transaction/rollback nghiệp vụ chưa?
- [ ] Với I/O thật, tôi đã chọn charset, xử lý lỗi file và thiết kế vòng đời dữ liệu hợp lý chưa?
- [ ] Test có bao gồm thành công, lỗi thân, lỗi đóng, nhiều resource và lỗi initializer chưa?

**Ghi nhớ:** try-with-resources có từ **Java 7**, dùng `try (Resource r = ...) { ... }` để tự động đóng `AutoCloseable`. Java khởi tạo resource theo thứ tự khai báo, đóng theo thứ tự ngược và giữ lỗi đóng dưới dạng **suppressed** khi đã có lỗi chính. Từ **Java 9**, có thể viết `try (existingResource)` với biến `final`/effectively final; từ **Java 10**, có thể dùng `var` trong resource declaration. TWR giúp quản lý vòng đời tài nguyên đáng tin cậy hơn, nhưng không thay cho xử lý ngoại lệ có ngữ cảnh hoặc giao dịch bảo toàn dữ liệu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="try-with-resources-002">AutoCloseable và Closeable</a>

<details>
<summary>Xem chi tiết</summary>

# `AutoCloseable` và `Closeable` trong Java: nền tảng của `try-with-resources`

Một chương trình Java thường làm việc với những tài nguyên phải được kết thúc sử dụng đúng lúc: luồng đọc/ghi tệp, kết nối cơ sở dữ liệu, socket, channel hoặc một phiên làm việc có trạng thái. Garbage collector quản lý bộ nhớ của các đối tượng Java, nhưng **không phải cơ chế thay thế cho việc đóng tài nguyên kịp thời**. Để biểu đạt khả năng “đóng được”, Java có interface `java.lang.AutoCloseable` và interface chuyên biệt hơn `java.io.Closeable`.

Khi một tài nguyên được khai báo trong phần đầu của câu lệnh `try-with-resources`, Java tự động gọi phương thức `close()` của tài nguyên đó lúc rời khối. Cơ chế này giúp giảm lỗi quên đóng, xử lý thứ tự đóng nhiều tài nguyên và bảo toàn thông tin khi cả thao tác chính lẫn thao tác đóng cùng thất bại.

**Phạm vi bài học:** hiểu chính xác mối quan hệ `AutoCloseable` – `Closeable`, chữ ký `close()`, quy tắc checked exception, hợp đồng đóng nhiều lần, cách sử dụng với `try-with-resources`, cách tự viết lớp tài nguyên và những lỗi thiết kế cần tránh. Các ví dụ hoàn chỉnh dùng **Java 17+**, không cần file hay dịch vụ bên ngoài.

## 1. Hai interface liên hệ với nhau thế nào?

`AutoCloseable` có trong `java.lang` (không cần import). `Closeable` có trong `java.io` và **kế thừa `AutoCloseable`**:

```java
public interface AutoCloseable {
    void close() throws Exception;
}

public interface Closeable extends AutoCloseable {
    @Override
    void close() throws IOException;
}
```

Đây là **bản lược giản chữ ký API để học**, không phải mã nguồn đầy đủ của JDK; `IOException` nằm trong `java.io`. Interface con `Closeable` khai báo lại phương thức với kiểu checked exception **hẹp hơn**: `IOException` là lớp con của `Exception`.

```text
AutoCloseable  (java.lang)
  └─ close() throws Exception
       ▲
       │ extends
Closeable      (java.io)
  └─ close() throws IOException
       ▲
       ├─ InputStream / OutputStream
       ├─ Reader / Writer
       └─ nhiều kiểu tài nguyên I/O khác
```

Không phải mọi đối tượng `AutoCloseable` đều là `Closeable`. Ví dụ, nhiều API JDBC như `java.sql.Connection` triển khai `AutoCloseable` với lỗi đặc thù `SQLException`; chúng không bắt buộc phải triển khai `java.io.Closeable`.

Bất kỳ lớp nào triển khai `Closeable` cũng là `AutoCloseable`, nên đều có thể xuất hiện trong phần khai báo tài nguyên của `try-with-resources`.

## 2. So sánh hợp đồng chính

| Điểm so sánh | `AutoCloseable` | `Closeable` |
| --- | --- | --- |
| Gói | `java.lang` | `java.io` |
| Quan hệ | Interface tổng quát cho tài nguyên có thể đóng | `extends AutoCloseable` |
| Chữ ký `close()` | `void close() throws Exception` | `void close() throws IOException` |
| Ngữ cảnh điển hình | I/O, JDBC, channel, giao dịch/phạm vi tự quản lý, đối tượng cần cleanup | Chủ yếu luồng và nguồn/đích dữ liệu I/O |
| Gọi `close()` nhiều lần | Không bắt buộc phải idempotent; tài liệu **khuyến khích** làm idempotent | Hợp đồng: đã đóng thì gọi lại **không có tác dụng** |
| Dùng với `try-with-resources` | Có | Có, vì kế thừa `AutoCloseable` |
| Checked exception nhìn từ biến kiểu interface | `Exception` | `IOException` |
| Có thể override `close()` với ít lỗi hơn? | Có, ví dụ không `throws` gì | Có, ví dụ không `throws` gì |

**Idempotent** đối với `close()` ở đây nghĩa là gọi lại sau khi tài nguyên đã đóng không tạo thêm hiệu ứng quan sát được theo hợp đồng đóng. `Closeable.close()` được Javadoc yêu cầu nếu stream đã đóng thì lời gọi tiếp theo không có tác dụng. Với `AutoCloseable` thuần túy, điều đó **không phải yêu cầu bắt buộc**, dù cách triển khai an toàn thường vẫn nên làm.

Cả hai interface **không hứa** rằng mọi thao tác `close()` luôn thành công. Ngay cả khi đóng thất bại, trách nhiệm về trạng thái và quyền sở hữu tài nguyên vẫn phải được thiết kế rõ ràng.

## 3. Vì sao `try-with-resources` cần `AutoCloseable`?

Cấu trúc căn bản:

```java
try (Resource resource = createResource()) {
    useResource(resource);
}
```

Đây là **mẫu minh họa**, giả định `Resource implements AutoCloseable` và các hàm liên quan đã được định nghĩa. Quy trình khi chạy:

1. Java đánh giá biểu thức `createResource()` để khởi tạo biến tài nguyên trong dấu `(...)`.
2. Nếu khởi tạo thành công, Java thực thi thân `try`.
3. Khi rời thân `try` theo đường Java thông thường — kết thúc bình thường, `return`, `throw`, `break` hoặc `continue` phù hợp — Java gọi `resource.close()`.
4. Sau bước đóng, câu lệnh `try-with-resources` hoàn tất; nếu có `catch` hoặc `finally` cùng cấu trúc, chúng được thực hiện theo quy tắc riêng, **sau khi việc đóng tự động đã được tiến hành**.
5. Nếu một lỗi phát sinh lúc đóng, Java xử lý ngoại lệ chính và `suppressed exceptions` theo quy tắc đặc biệt.

Khác với lời gọi `close()` thủ công, lập trình viên không cần lặp cùng logic giải phóng trong từng nhánh `catch`, `return` hay cuối phương thức.

**Lưu ý:** `AutoCloseable` là điều kiện về **kiểu** để một đối tượng có thể được đưa vào danh sách tài nguyên. Nó không tự có cơ chế đóng khi đối tượng bị thu gom rác. Tài nguyên đặt ngoài `try-with-resources` vẫn cần được quản lý theo hợp đồng API.

## 4. Ví dụ Java 17+: tự tạo một `AutoCloseable` đơn giản

Lưu thành `SimpleAutoCloseableDemo.java`:

```java
public class SimpleAutoCloseableDemo {
    static class Session implements AutoCloseable {
        private boolean closed;

        Session() {
            System.out.println("open session");
        }

        void work() {
            if (closed) {
                throw new IllegalStateException("already closed");
            }
            System.out.println("session work");
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                System.out.println("close session");
            }
        }
    }

    public static void main(String[] args) {
        try (Session session = new Session()) {
            session.work();
        }
        System.out.println("after try");
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 SimpleAutoCloseableDemo.java
java SimpleAutoCloseableDemo
```

Kết quả xác định:

```text
open session
session work
close session
after try
```

`Session implements AutoCloseable`, vì vậy có thể khai báo trong `try (...)`. Trình biên dịch tự tạo logic tương đương với một khối `finally` thích hợp để gọi `close()` khi kết thúc. Chữ ký thực tế `Session.close()` **không khai báo checked exception**, nên `main()` không cần `throws Exception` chỉ vì tự động đóng `Session`.

Ví dụ chủ động đánh dấu `closed = true` và chỉ in khi đóng lần đầu. Điều đó làm `close()` idempotent, dù interface `AutoCloseable` không bắt buộc. Phương thức `work()` kiểm tra trạng thái để từ chối dùng tài nguyên sau khi đóng.

Đừng tự gọi `session.close()` thêm một lần trong thân `try` chỉ để “chắc ăn”. Nếu đã khai báo ở phần đầu `try-with-resources`, Java sẽ gọi nó khi rời khối; gọi thủ công có thể dẫn tới double-close ở lớp triển khai không idempotent.

## 5. `throws Exception` và `throws IOException` ảnh hưởng compiler thế nào?

Khác biệt chữ ký `close()` liên quan trực tiếp tới cơ chế checked exception:

- `AutoCloseable.close()` được khai báo `throws Exception`. Nếu tài nguyên được nhìn qua **kiểu tĩnh `AutoCloseable`**, việc đóng tự động có thể gây `Exception` checked; người gọi phải xử lý hoặc khai báo phù hợp.
- `Closeable.close()` được khai báo `throws IOException`. Nếu tài nguyên được nhìn qua **kiểu tĩnh `Closeable`**, loại checked exception từ việc đóng hẹp hơn.
- Một **lớp triển khai cụ thể** được phép override `close()` mà **không ném checked exception**. Khi `try-with-resources` dùng chính kiểu cụ thể đó, nghĩa vụ compile-time liên quan riêng tới việc đóng có thể biến mất.
- Ngoại lệ từ **thân `try` hoặc lời gọi khởi tạo tài nguyên** vẫn được kiểm tra riêng. Chữ ký `close()` không xóa nghĩa vụ đối với những ngoại lệ đó.

### Chương trình Java 17+: cùng một hành động, hai hợp đồng `close()`

Lưu thành `CloseContractDemo.java`:

```java
import java.io.Closeable;
import java.io.IOException;

public class CloseContractDemo {
    static class GeneralResource implements AutoCloseable {
        @Override
        public void close() throws Exception {
            System.out.println("general-close");
        }
    }

    static class IoResource implements Closeable {
        @Override
        public void close() throws IOException {
            System.out.println("io-close");
        }
    }

    static void usingGeneral() throws Exception {
        try (AutoCloseable resource = new GeneralResource()) {
            System.out.println("general-work");
        }
    }

    static void usingIo() throws IOException {
        try (Closeable resource = new IoResource()) {
            System.out.println("io-work");
        }
    }

    public static void main(String[] args) throws Exception {
        usingGeneral();
        usingIo();
    }
}
```

```bash
javac -encoding UTF-8 CloseContractDemo.java
java CloseContractDemo
```

Kết quả:

```text
general-work
general-close
io-work
io-close
```

`usingGeneral()` khai báo `throws Exception` do tham chiếu `AutoCloseable` có chữ ký `close() throws Exception`. `usingIo()` chỉ cần `throws IOException` vì `Closeable.close()` thu hẹp hợp đồng. Trong demo, cả hai thao tác `close()` đều không thực sự ném lỗi, nhưng **khả năng ném theo chữ ký** vẫn được xét lúc biên dịch.

Sự khác nhau thể hiện rõ khi dùng lớp tự viết:

```java
static class QuietResource implements AutoCloseable {
    @Override
    public void close() { }
}

static void useQuiet() {
    try (QuietResource resource = new QuietResource()) {
        System.out.println("work");
    }
}
```

Đây là **đoạn phương thức/lớp lồng minh họa**, không phải tệp chạy độc lập. `useQuiet()` không cần `throws Exception` chỉ vì `QuietResource` implements `AutoCloseable`: compiler biết `QuietResource.close()` cụ thể không khai báo checked exception.

**Lỗi thiết kế thường gặp:** mọi nơi đều lưu resource qua biến `AutoCloseable` dù tài nguyên thật có chữ ký `close()` hẹp hơn. Điều đó có thể buộc khai báo/bắt `Exception` rộng không cần thiết. Nếu hợp đồng thực tế là I/O, dùng `Closeable` hoặc kiểu cụ thể thường chính xác hơn.

## 6. Tự triển khai `Closeable` khi nào hợp lý?

`Closeable` phù hợp với lớp đại diện cho nguồn hoặc đích dữ liệu có khả năng đóng và có thể thất bại vì lỗi I/O:

```java
import java.io.Closeable;
import java.io.IOException;

class TextChannel implements Closeable {
    private boolean closed;

    void send(String text) throws IOException {
        if (closed) {
            throw new IOException("channel closed");
        }
        System.out.println(text);
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            // Ở ứng dụng thật: giải phóng stream/socket liên quan.
        }
    }
}
```

Đây là **lớp minh họa hợp lệ**, không có `main` độc lập. `TextChannel` mô hình hóa nguồn/đích dữ liệu và trả lỗi I/O dưới dạng `IOException`. Trạng thái `closed` ngăn sử dụng sai sau khi đóng. Lời gọi `close()` lặp lại không tạo hành động mới, đúng tinh thần hợp đồng `Closeable`.

Không phải mọi đối tượng đóng được đều là I/O. Nếu lớp đại diện cho một transaction scope hoặc một cấu trúc tài nguyên riêng, `AutoCloseable` có thể phù hợp hơn. Nếu muốn hợp đồng đóng ném `SQLException`, không thể chọn `Closeable.close() throws SQLException` vì `SQLException` không phải lớp con của `IOException`. `AutoCloseable` cho phép thiết kế ngoại lệ chuyên biệt đó.

## 7. Ví dụ `Closeable` trong thư viện chuẩn: `BufferedReader`

Các loại như `InputStream`, `OutputStream`, `Reader` và `Writer` triển khai `Closeable`. Vì vậy, có thể dùng trực tiếp với `try-with-resources`. Để có ví dụ độc lập không cần tệp trên đĩa, dùng `StringReader` làm nguồn ký tự rồi bọc bằng `BufferedReader`.

Lưu thành `ReaderDemo.java`:

```java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

public class ReaderDemo {
    public static void main(String[] args) throws IOException {
        try (BufferedReader reader =
                     new BufferedReader(new StringReader("alpha\nbeta"))) {
            System.out.println("line1=" + reader.readLine());
            System.out.println("line2=" + reader.readLine());
        }
        System.out.println("reader closed");
    }
}
```

```bash
javac -encoding UTF-8 ReaderDemo.java
java ReaderDemo
```

Kết quả:

```text
line1=alpha
line2=beta
reader closed
```

Trong ví dụ này, `BufferedReader` là **tài nguyên sở hữu wrapper**, được khai báo ở đầu `try-with-resources`. Khi `BufferedReader.close()` chạy, nó cũng đóng `StringReader` được bọc theo hợp đồng của lớp đọc. Vì vậy không cần khai báo thêm một tài nguyên `StringReader` riêng chỉ để đóng lại cùng underlying reader.

Điều đó không có nghĩa mọi lớp wrapper trong Java đều tự động chịu trách nhiệm đóng tài nguyên được truyền vào. Khi dùng thư viện bên ngoài, **đọc hợp đồng ownership của API** để biết wrapper có đóng đối tượng gốc hay không.

Với tệp thật, có thể dùng `Files.newBufferedReader(path)` hoặc `Files.newInputStream(path)` trong `try-with-resources`; các API này có thể ném `IOException` khi mở, đọc và đóng. Không nên dùng nguồn tệp phụ thuộc máy trong ví dụ học thuật có stdout cố định.

## 8. Nhiều tài nguyên: khởi tạo từ trái sang phải, đóng ngược lại

Với danh sách nhiều tài nguyên:

```java
try (
    Resource first = new Resource("A");
    Resource second = new Resource("B");
    Resource third = new Resource("C")
) {
    doWork();
}
```

Đây là mẫu minh họa, giả định các kiểu đã tồn tại. Java **khởi tạo A → B → C**, chạy thân `try`, sau đó **đóng C → B → A**. Thứ tự LIFO này hữu ích khi tài nguyên tạo sau phụ thuộc tài nguyên tạo trước. Có thể đặt dấu `;` cuối cùng trong danh sách tài nguyên, nhưng không bắt buộc.

Lưu thành `CloseOrderDemo.java`:

```java
public class CloseOrderDemo {
    static class Step implements AutoCloseable {
        private final String name;

        Step(String name) {
            this.name = name;
            System.out.println("open " + name);
        }

        @Override
        public void close() {
            System.out.println("close " + name);
        }
    }

    public static void main(String[] args) {
        try (Step a = new Step("A");
             Step b = new Step("B");
             Step c = new Step("C")) {
            System.out.println("body");
        }
        System.out.println("after");
    }
}
```

```bash
javac -encoding UTF-8 CloseOrderDemo.java
java CloseOrderDemo
```

Kết quả:

```text
open A
open B
open C
body
close C
close B
close A
after
```

Từng biến tài nguyên được khai báo trong phần `try (...)` là biến chỉ gán một lần trong phạm vi đó; không được gán lại nó trong thân `try`. Sau khi câu lệnh `try-with-resources` hoàn tất, những biến khai báo trực tiếp trong danh sách tài nguyên không còn nằm trong phạm vi sử dụng phía sau.

## 9. Nếu mở tài nguyên thứ hai thất bại thì tài nguyên thứ nhất ra sao?

Đặc tả Java bảo đảm những tài nguyên **đã khởi tạo thành công** trong cùng `try-with-resources` được đóng nếu một tài nguyên phía sau **không thể khởi tạo**. Không cần vào thân `try` để cơ chế đóng bắt đầu.

Lưu thành `FailedOpenDemo.java`:

```java
import java.io.IOException;

public class FailedOpenDemo {
    static class Resource implements AutoCloseable {
        private final String name;

        Resource(String name, boolean fail) throws IOException {
            this.name = name;
            System.out.println("init " + name);
            if (fail) {
                throw new IOException("open " + name + " failed");
            }
        }

        @Override
        public void close() {
            System.out.println("close " + name);
        }
    }

    public static void main(String[] args) {
        try (Resource a = new Resource("A", false);
             Resource b = new Resource("B", true)) {
            System.out.println("body");
        } catch (IOException ex) {
            System.out.println("caught=" + ex.getMessage());
        }
    }
}
```

```bash
javac -encoding UTF-8 FailedOpenDemo.java
java FailedOpenDemo
```

Kết quả:

```text
init A
init B
close A
caught=open B failed
```

A được khởi tạo xong nên được đóng. B ném ngoại lệ ngay trong constructor trước khi trở thành một tài nguyên được khởi tạo thành công trong `try-with-resources`, vì vậy Java **không có đối tượng B đã nhận được để gọi `close()` tự động**. Thân `try` không chạy.

**Giới hạn quan trọng:** nếu constructor của một tài nguyên tạo ra một tài nguyên nội bộ rồi thất bại trước khi trả về đối tượng hoàn chỉnh, chính constructor/factory đó phải có biện pháp dọn dẹp những phần đã cấp phát. `try-with-resources` ở bên ngoài chỉ tự động đóng được các resource đã khởi tạo thành công và được quản lý trong danh sách.

## 10. Ngoại lệ chính và `suppressed exceptions`

Một lợi ích đặc biệt của `try-with-resources` là giữ dấu vết lỗi khi **cả thân `try` lẫn `close()`** cùng thất bại.

- Nếu thân `try` ném ngoại lệ `E`, sau đó `close()` ném ngoại lệ `C`, Java tiếp tục ném **E là ngoại lệ chính**, đồng thời gắn **C vào `E.getSuppressed()`**.
- Nếu thân `try` hoàn tất bình thường nhưng `close()` ném `C`, thì `C` có thể trở thành ngoại lệ chính đi ra ngoài.
- Nếu có nhiều tài nguyên và nhiều `close()` thất bại, Java vẫn cố đóng các tài nguyên còn lại, và những lỗi bổ sung được ghi lại trong danh sách `suppressed` theo cơ chế của JLS.
- `getSuppressed()` **khác `getCause()`**. Cause mô tả quan hệ nguyên nhân giữa các ngoại lệ; suppressed ghi các ngoại lệ bổ sung bị giữ lại khi xử lý một ngoại lệ chính.

### Chương trình Java 17+: hai lỗi `close()` đi kèm một lỗi ở thân `try`

Lưu thành `SuppressedDemo.java`:

```java
public class SuppressedDemo {
    static class Faulty implements AutoCloseable {
        private final String name;

        Faulty(String name) {
            this.name = name;
            System.out.println("open " + name);
        }

        @Override
        public void close() {
            System.out.println("close " + name);
            throw new IllegalStateException("close " + name + " failed");
        }
    }

    public static void main(String[] args) {
        try (Faulty a = new Faulty("A");
             Faulty b = new Faulty("B")) {
            System.out.println("body");
            throw new IllegalArgumentException("work failed");
        } catch (Exception ex) {
            System.out.println("primary=" + ex.getMessage());
            System.out.println("count=" + ex.getSuppressed().length);
            for (Throwable suppressed : ex.getSuppressed()) {
                System.out.println("suppressed=" + suppressed.getMessage());
            }
        }
    }
}
```

```bash
javac -encoding UTF-8 SuppressedDemo.java
java SuppressedDemo
```

Kết quả:

```text
open A
open B
body
close B
close A
primary=work failed
count=2
suppressed=close B failed
suppressed=close A failed
```

Cả hai tài nguyên đều được yêu cầu đóng theo thứ tự B rồi A. Lỗi từ thân `try` vẫn là `IllegalArgumentException("work failed")` chính; hai lỗi đóng được đính kèm theo thứ tự chúng xảy ra. Không có lần gọi `catch` độc lập cho từng ngoại lệ suppressed.

Nếu bỏ câu lệnh `throw new IllegalArgumentException("work failed")` trong thân `try`, lỗi đóng B sẽ trở thành ngoại lệ chính và lỗi đóng A sẽ bị suppress vào lỗi B. Đây là trường hợp **thân `try` thành công nhưng cleanup thất bại**.

Đừng che giấu lỗi đóng bằng một `catch (Exception ex) { }` rỗng. Trong hệ thống thực, tùy mức nghiêm trọng, ứng dụng cần lưu cả ngoại lệ chính và những suppressed errors để điều tra.

## 11. Thứ tự `catch` và `finally` khi dùng `try-with-resources`

Có thể thêm `catch` và `finally` sau một câu lệnh `try-with-resources`:

```java
try (Resource resource = openResource()) {
    useResource(resource);
} catch (IOException ex) {
    handleIoFailure(ex);
} finally {
    finishOperation();
}
```

Đây là **mẫu cú pháp**; các tên `Resource` và phương thức phải được định nghĩa với chữ ký phù hợp. Quy tắc thứ tự:

1. Khởi tạo tài nguyên.
2. Chạy thân `try` nếu khởi tạo thành công.
3. Tự động đóng những tài nguyên đã khởi tạo, theo thứ tự ngược.
4. Chọn nhánh `catch` đầu tiên phù hợp nếu có ngoại lệ đang được ném.
5. Thực hiện `finally` nếu có, trước khi hoàn tất chuyển quyền điều khiển.

Nếu lỗi `IOException` xảy ra **ngay khi đóng** mà thân `try` không ném lỗi, `catch (IOException ex)` vẫn có thể nhận ngoại lệ này. Nếu thân `try` ném `IllegalStateException`, còn `close()` ném `IOException` bị suppress, `catch (IOException)` **không** được chọn chỉ vì suppressed exception.

`finally` thông thường **không thay thế** cơ chế đóng tự động. Cách quản lý resource hợp lý thường là cho `try-with-resources` chịu trách nhiệm đóng, còn `finally` thực hiện các bước cuối không thuộc việc đóng cùng resource đó.

## 12. Dùng lại biến `final` hoặc *effectively final* từ Java 9

Có hai cách đặt tài nguyên vào danh sách:

**Khai báo tài nguyên tại `try`:**

```java
try (MyResource resource = new MyResource()) {
    use(resource);
}
```

**Dùng tham chiếu đến biến đã tồn tại (Java 9+):**

```java
MyResource resource = new MyResource();
try (resource) {
    use(resource);
}
```

Hai đoạn là **mẫu minh họa** với `MyResource implements AutoCloseable` và `use(...)` đã định nghĩa. Ở cách thứ hai, biến `resource` phải được gán và là `final` hoặc **effectively final**: không bị gán lại sau lần gán ban đầu.

Ví dụ sai nếu cố gán lại trước khối:

```java
MyResource resource = new MyResource();
resource = new MyResource();
try (resource) {       // Compile error: biến không effectively final.
    use(resource);
}
```

Đây là **ví dụ cố ý không biên dịch**. Trong trường hợp mã chạy được, sau khi khối `try (resource)` kết thúc, tên biến `resource` bên ngoài có thể vẫn còn trong phạm vi, nhưng **đối tượng đã được gọi `close()`**. Việc biến còn tham chiếu tới đối tượng không có nghĩa nó còn dùng an toàn.

Lưu ý **ownership:** một tài nguyên chỉ nên được đóng ở nơi chịu trách nhiệm sở hữu vòng đời. Dùng `try (existingResource)` có nghĩa đoạn mã này **đang nhận trách nhiệm đóng** đối tượng khi ra khỏi khối. Nếu tài nguyên đó phải tiếp tục được chủ sở hữu khác sử dụng, không nên tùy tiện đưa vào `try-with-resources`.

Ngoài ra Java 10+ cho phép dùng `var` khi khai báo resource, ví dụ `try (var resource = factory()) { ... }`, nếu phép suy luận kiểu hợp lệ. Dùng `var` có thể bảo toàn kiểu cụ thể của tài nguyên, do đó giúp compiler suy ra đúng kiểu checked exception từ `close()`.

## 13. Tài nguyên `null` và các trường hợp đặc biệt

Nếu một biến tài nguyên hợp lệ được khởi tạo thành `null`, Java **không gọi `close()` cho giá trị `null`**:

```java
try (MyResource resource = null) {
    System.out.println("body");
}
```

Đây là đoạn minh họa khi `MyResource implements AutoCloseable`. Nó không ném `NullPointerException` chỉ vì resource bằng `null`; nhưng nếu thân `try` tự gọi `resource.someMethod()` thì vẫn có thể phát sinh NPE theo quy tắc bình thường.

Dùng resource `null` hiếm khi là cách thiết kế tốt; nó có thể che một lỗi khởi tạo. Nếu tài nguyên là tùy chọn, nên thiết kế điều kiện và vòng đời rõ ràng.

**Không lạm dụng suy luận rằng “mọi đối tượng có phương thức `close()` đều dùng được trong `try-with-resources`”.** Java yêu cầu kiểu của resource là lớp/interface gán được cho `AutoCloseable`. Một lớp tự tạo có `void close()` nhưng **không implements `AutoCloseable`** sẽ bị từ chối làm resource.

Ví dụ cố ý không biên dịch:

```java
class FakeResource {
    void close() { }
}

class InvalidResourceDemo {
    void run() {
        try (FakeResource resource = new FakeResource()) {
            System.out.println("work");
        }
    }
}
```

Để sửa, triển khai `AutoCloseable` hoặc `Closeable` thích hợp rồi override `close()` với hợp đồng đúng.

## 14. Các nguyên tắc triển khai `close()` an toàn

Khi tự thiết kế lớp tài nguyên, cần quan tâm nhiều hơn một câu lệnh `implements AutoCloseable`.

### 14.1. Bảo đảm cleanup thực sự xảy ra

`close()` phải thực hiện việc giải phóng tài nguyên theo hợp đồng: đóng file handle, hủy đăng ký listener, kết thúc phiên sử dụng hoặc hoàn trả tài nguyên được cấp phát. **Đừng tạo một `close()` rỗng để khiến kiểu dữ liệu đi qua compiler** nếu đối tượng thực sự sở hữu tài nguyên cần dọn dẹp.

Nếu cần nhiều bước dọn dẹp, phải quyết định thứ tự và cách giữ thông tin lỗi. Một bước lỗi không nhất thiết được phép làm bỏ qua mọi bước còn lại.

### 14.2. Đánh dấu trạng thái đóng và tránh đóng lặp

Mặc dù `AutoCloseable` không bắt buộc idempotent, Javadoc khuyến khích làm thế. `Closeable` có yêu cầu rõ ràng rằng đóng lại một stream đã đóng không có hiệu ứng.

Mẫu đơn giản:

```java
@Override
public void close() {
    if (closed) {
        return;
    }
    closed = true;
    releaseOwnedResources();
}
```

Đây là **mẫu thiết kế**, giả định có trường `closed` và hàm `releaseOwnedResources()` phù hợp. Đánh dấu trước cleanup giúp giảm rủi ro tái nhập hay đóng hai lần trong một số lớp tài nguyên; nhưng cần thiết kế kỹ nếu cleanup có khả năng thất bại giữa chừng. Với tài nguyên có vòng đời phức tạp, một cờ boolean có thể chưa đủ để mô tả trạng thái đang đóng, đóng thất bại, hoặc tính an toàn đa luồng.

### 14.3. Không biến `close()` thành điểm làm mất ngoại lệ quan trọng

Nếu `close()` có khả năng ném checked exception, nên khai báo **kiểu hẹp có ý nghĩa** hoặc không khai báo checked exception nếu không thể thất bại. `AutoCloseable` cho phép `throws Exception`, nhưng không nên lạm dụng điều đó để trả bất kỳ lỗi nào không liên quan tới nhiệm vụ đóng.

Theo Javadoc `AutoCloseable`, tránh thiết kế `close()` ném `InterruptedException`: khi nó bị suppress do một lỗi chính khác, việc xử lý trạng thái interrupt có thể trở nên khó đoán. Nếu cleanup có thể bị ngắt, cần hợp đồng và cách duy trì tín hiệu hủy phù hợp.

### 14.4. Trách nhiệm đóng thuộc về ai?

Các thư viện thường có nhiều tầng wrapper. Nếu một wrapper sở hữu stream/kết nối gốc, việc đóng wrapper **có thể** đóng stream/kết nối gốc. Nhưng không thể mặc định điều đó cho mọi thư viện. Đọc tài liệu lớp cụ thể trước khi cho nhiều tầng cùng sở hữu quyền đóng.

Cần tránh:
- Hai thành phần đều nhận “quyền sở hữu” và đóng cùng một đối tượng không idempotent.
- Trả ra ngoài một đối tượng tài nguyên đã bị đóng trong phạm vi `try-with-resources`.
- Đưa một đối tượng dùng chung vào `try-with-resources` của một lời gọi nhỏ, làm người dùng khác nhận tài nguyên đã đóng.
- Dựa vào finalizer, garbage collector hay kết thúc JVM thay cho cleanup đúng lúc.

## 15. Khi nào không cần `try-with-resources`?

Không phải mọi đối tượng implements `AutoCloseable` đều luôn thực sự nắm tài nguyên bên ngoài. Một số API dùng chung interface vì có cả trường hợp cần đóng và không cần đóng.

Ví dụ, `java.util.stream.Stream` có liên hệ với `AutoCloseable` qua `BaseStream`. Stream được tạo từ nguồn I/O như `Files.lines(path)` cần được đóng theo hợp đồng tài nguyên. Nhưng một stream thuần trong bộ nhớ, chẳng hạn `List.of(1, 2, 3).stream()`, thường không sở hữu file handle nên không cần thiết phải bọc `try-with-resources` cho riêng nó.

Ngược lại, `Connection`, `PreparedStatement`, `ResultSet`, `BufferedReader` hoặc file stream trong các API thích hợp thường có tài nguyên cần giải phóng. Ngay cả khi tài nguyên không ném checked exception khi đóng, `try-with-resources` vẫn có thể là cách rõ ràng để xác định vòng đời.

**Quy tắc chọn:** không phải “hễ implements AutoCloseable thì bọc mọi lúc”, mà là “khi mã hiện tại có trách nhiệm đóng tài nguyên này và muốn bảo đảm cleanup theo luồng Java thông thường, ưu tiên `try-with-resources`”.

## 16. Những lỗi phổ biến

| Lỗi hoặc hiểu sai | Thực tế và cách sửa |
| --- | --- |
| “`Closeable` là interface hoàn toàn tách rời `AutoCloseable`.” | Sai; `Closeable extends AutoCloseable`. |
| “Hai phương thức `close()` đều khai báo `throws Exception`.” | Sai; `Closeable.close() throws IOException`. |
| “Một lớp có phương thức tên `close` thì tự động dùng được với `try-with-resources`.” | Sai; kiểu resource phải triển khai `AutoCloseable` trực tiếp hoặc gián tiếp. |
| “`AutoCloseable.close()` bắt buộc idempotent.” | Sai; chỉ được khuyến khích. `Closeable.close()` có hợp đồng gọi lại không tác dụng. |
| “Tài nguyên thứ hai mở lỗi thì tài nguyên thứ nhất bị quên.” | Sai; Java tự đóng các tài nguyên trước đó đã khởi tạo thành công. |
| “Đóng theo thứ tự A → B → C vì đã khai báo như thế.” | Sai; đóng C → B → A. |
| “`catch` chạy trước khi đóng tài nguyên.” | Sai; đóng tự động được thực hiện trước `catch`/`finally` của cùng `try-with-resources`. |
| “Lỗi `close()` luôn ghi đè lỗi trong thân `try`.” | Sai; khi đã có lỗi chính, lỗi đóng thường được lưu trong `getSuppressed()`. |
| “Lỗi bị suppress sẽ tự kích hoạt một `catch` riêng.” | Sai; chọn `catch` dựa trên ngoại lệ chính được ném. |
| “Biến còn trong scope tức là resource vẫn mở.” | Sai; tài nguyên có thể đã được đóng khi ra khỏi khối. |
| “`try-with-resources` bảo đảm `close()` chạy cả khi JVM bị kill.” | Không; nó dựa vào việc quyền điều khiển còn đi qua cơ chế Java. |
| “Không cần đọc hợp đồng wrapper vì Java biết tự đóng mọi thành phần liên quan.” | Không; Java đóng các resource được đăng ký; quan hệ sở hữu bên trong wrapper là hợp đồng API. |
| “Chỉ cần bắt `IOException` cho mọi `AutoCloseable`.” | Không; kiểu checked exception phụ thuộc chữ ký `close()` của kiểu tài nguyên và hoạt động trong thân. |

## 17. Quy trình áp dụng trong code review

Khi gặp một đối tượng có `close()`, kiểm tra theo thứ tự:

1. **Kiểu tài nguyên:** có implements `AutoCloseable` không? Có phải `Closeable` hoặc lớp chuyên biệt có `close()` hẹp hơn không?
2. **Ownership:** phương thức hiện tại có sở hữu và chịu trách nhiệm kết thúc vòng đời đối tượng không?
3. **Cách sử dụng:** có thể khai báo resource ngay trong `try (...)`, hoặc dùng một biến `final`/effectively final đã tồn tại từ Java 9 không?
4. **Ngoại lệ khi mở, khi dùng, khi đóng:** API công bố những kiểu checked nào? Cần `catch` tại chỗ hay `throws` lên tầng ngoài?
5. **Thứ tự phụ thuộc:** nếu nhiều tài nguyên, thứ tự khởi tạo và đóng ngược có đúng với quan hệ wrapper không?
6. **Khi thất bại:** có giữ ngoại lệ chính và `getSuppressed()` không? Có làm mất `cause` khi dịch lỗi không?
7. **Tính idempotent và tái sử dụng:** tài nguyên có được dùng sau khi đóng không, có khả năng double-close không?
8. **Khả năng phục hồi:** nếu cleanup thất bại, hệ thống có chính sách ghi nhận, hủy thao tác hoặc dừng dịch vụ phù hợp không?

Một cấu trúc thông thường đáng ưu tiên:

```java
try (SomeCloseable resource = open()) {
    return process(resource);
} catch (IOException ex) {
    throw new DataAccessException("Cannot process resource", ex);
}
```

Đây là **mẫu minh họa không độc lập**, giả định `SomeCloseable implements Closeable`, `open()`/`process(...)` có chữ ký thích hợp và `DataAccessException` là loại ngoại lệ có constructor nhận `cause`. Tài nguyên được đóng **trước khi nhánh `catch` cùng cấu trúc xử lý lỗi**. Bọc lại ngoại lệ giúp tầng trên nhận hợp đồng có ý nghĩa, đồng thời giữ lỗi gốc.

Nếu `process(resource)` trả về một đối tượng vẫn cần dùng tài nguyên sau khi phương thức kết thúc (ví dụ stream lười hoặc iterator trực tiếp phụ thuộc file), việc đóng ngay trước `return` có thể làm kết quả không sử dụng được. Khi đó phải thiết kế lại ownership và vòng đời thay vì áp dụng mẫu máy móc.

## 18. Tự kiểm tra

**Câu 1.** `Closeable` và `AutoCloseable` có quan hệ kế thừa thế nào?  
`Closeable extends AutoCloseable`. Mọi `Closeable` đều dùng được với `try-with-resources`.

**Câu 2.** Chữ ký `close()` trên hai interface khác nhau ở đâu?  
`AutoCloseable.close() throws Exception`; `Closeable.close() throws IOException`.

**Câu 3.** Vì sao `try (AutoCloseable r = ...)` có thể cần `throws Exception` dù object thật đóng không ném lỗi?  
Do compile-time dựa vào **kiểu tĩnh** và chữ ký `AutoCloseable.close()`, không chỉ vào hành vi một lần chạy.

**Câu 4.** Khai báo A, B, C trong cùng `try (...)` thì đóng theo thứ tự nào?  
**C, B, A**, ngược với thứ tự khởi tạo.

**Câu 5.** Nếu khởi tạo B thất bại sau khi A đã được khởi tạo, A có được đóng không?  
**Có**, A được tự đóng theo quy tắc của `try-with-resources`.

**Câu 6.** Nếu thân `try` ném lỗi và `close()` ném lỗi khác, lỗi nào được ưu tiên?  
Lỗi thân `try` thường là **ngoại lệ chính**; lỗi đóng được đính kèm qua `getSuppressed()`.

**Câu 7.** Gọi `close()` hai lần trên `AutoCloseable` luôn an toàn và không có hiệu ứng không?  
**Không có bảo đảm chung.** `AutoCloseable` khuyến khích idempotent, trong khi `Closeable` quy định gọi lại sau khi đã đóng không có tác dụng.

**Câu 8.** Một class có `public void close()` nhưng không implements interface thích hợp có dùng được trong `try (...)` không?  
**Không**. Java kiểm tra kiểu của resource phải là subtype của `AutoCloseable`.

**Câu 9.** Với `try-with-resources` có `catch` và `finally`, thứ tự “close → catch → finally” đúng không?  
**Đúng về các giai đoạn**, nếu có nhánh `catch` phù hợp thì nó chạy sau đóng; `finally` chạy sau giai đoạn bắt lỗi, theo ngữ nghĩa Java.

**Câu 10.** Khi dùng `try (existing)` từ Java 9, biến `existing` cần điều kiện gì?  
Biến phải được gán trước, là `final` hoặc **effectively final**, và có kiểu thích hợp. Việc khối kết thúc đồng nghĩa tài nguyên đã được yêu cầu đóng dù tên biến có thể còn trong scope.

## Tài liệu chuẩn

- [Java SE 21 API — `AutoCloseable`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/AutoCloseable.html).
- [Java SE 21 API — `Closeable`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/Closeable.html).
- [JLS Java SE 21 — §14.20.3 `try-with-resources`](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.20.3).
- [JLS Java SE 21 — §11.2 Compile-Time Checking of Exceptions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-11.html#jls-11.2).

**Ghi nhớ:** `AutoCloseable` là hợp đồng đóng tài nguyên tổng quát; `Closeable` là hợp đồng I/O cụ thể hơn với `IOException` và hành vi gọi lại `close()`. Cả hai cho phép `try-with-resources` tự đóng đúng thứ tự, quản lý ngoại lệ khi cleanup thất bại và làm rõ ai sở hữu vòng đời tài nguyên.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="try-with-resources-003">Resource được khai báo trong try và phạm vi sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Resource được khai báo trong `try-with-resources` và phạm vi sử dụng trong Java

`try-with-resources` cho phép Java **tự động đóng tài nguyên** khi rời một khối xử lý, kể cả khi phần thân `try` ném ngoại lệ hoặc `return`. Cú pháp này đặc biệt hữu ích với tệp, stream, reader, writer, kết nối hoặc các đối tượng tự quản lý vòng đời. Tuy nhiên, để sử dụng đúng, cần phân biệt rõ **biến resource được khai báo ở đâu**, **nó còn nằm trong phạm vi truy cập (scope) ở đâu** và **đối tượng đã được đóng vào thời điểm nào**.

Một ví dụ đơn giản:

```java
try (var reader = java.nio.file.Files.newBufferedReader(path)) {
    String line = reader.readLine();
    System.out.println(line);
}
```

Đây là **đoạn minh họa**, giả định `path` có kiểu `java.nio.file.Path` và lời gọi nằm trong phương thức có xử lý hoặc khai báo `IOException`. Biến `reader` được khai báo trong *resource specification* — cặp ngoặc tròn ngay sau `try`. Java đóng `reader` khi thoát khỏi phần thân `try`; sau đó tên biến `reader` không còn nằm trong phạm vi truy cập.

Có hai trường hợp cần phân biệt:

- **Khai báo resource mới trong `try`:** `try (Reader r = ...) { ... }`. Tên `r` dùng được trong phần thân `try` và, theo quy tắc scope, trong những phần thích hợp của danh sách khai báo resource theo sau nó, **không** dùng được từ `catch`, `finally` hay mã phía sau cấu trúc.
- **Sử dụng biến đã khai báo bên ngoài:** từ Java 9 có thể viết `try (existingResource) { ... }` nếu biến là `final` hoặc *effectively final*. Tên biến vẫn có phạm vi theo khai báo ban đầu, có thể xuất hiện sau khối `try`, **nhưng đối tượng đã được đóng**. “Biến vẫn còn tên” không có nghĩa “resource còn sử dụng được”.

## 1. Tại sao `try-with-resources` cần một phạm vi riêng?

Một chương trình thường phải thực hiện hai hành động đối với tài nguyên:

1. **Lấy quyền sở hữu hoặc mở tài nguyên:** ví dụ mở reader cho tệp.
2. **Đóng tài nguyên dù thành công hay thất bại:** ví dụ giải phóng file handle.

Cách thủ công dễ mắc lỗi:

```java
var reader = java.nio.file.Files.newBufferedReader(path);
String line = reader.readLine();
reader.close();
```

Đây là **mã minh họa chưa xử lý checked exception**. Nếu `readLine()` ném `IOException`, dòng `close()` phía sau có thể không được thực hiện. Viết `try-finally` thủ công có thể khắc phục một phần, nhưng phải cẩn thận với lỗi xảy ra khi mở, đọc và đóng.

`try-with-resources` biến việc đóng tài nguyên thành **một phần ngữ nghĩa của cú pháp**. Tài nguyên được đóng khi khối `try` kết thúc, không phụ thuộc việc bạn có nhớ gọi `close()` ở từng nhánh `return` hay không.

```java
try (var reader = java.nio.file.Files.newBufferedReader(path)) {
    return reader.readLine();
}
```

Đây là **thân phương thức minh họa** có kiểu trả về `String` và hợp đồng `throws IOException` phù hợp. Reader được đóng trước khi phương thức thực sự trả kết quả; nếu đóng gây ngoại lệ, đường trả kết quả có thể bị thay bằng đường ném ngoại lệ.

Phạm vi ngắn giúp **giới hạn vòng đời tài nguyên**: đọc/ghi nằm trong vùng được quản lý, còn câu lệnh sau vùng đó không vô tình tiếp tục dùng resource đang mở.

## 2. Điều kiện để một đối tượng là resource

Tài nguyên trong `try-with-resources` phải có kiểu tương thích với **`java.lang.AutoCloseable`**. Interface này có phương thức:

```java
public interface AutoCloseable {
    void close() throws Exception;
}
```

Đây là **chữ ký minh họa của API**, không phải mã cần tự khai báo lại trong ứng dụng. Một số lớp thường dùng:

| Tài nguyên | Interface/class liên quan | Tác dụng điển hình |
| --- | --- | --- |
| `BufferedReader` | `java.io.Closeable` → `AutoCloseable` | Đọc văn bản và đóng reader |
| `InputStream` | `java.io.Closeable` → `AutoCloseable` | Đọc byte, giải phóng nguồn đọc |
| `OutputStream` | `java.io.Closeable` → `AutoCloseable` | Ghi byte và đóng nguồn ghi |
| `Scanner` | `java.io.Closeable` → `AutoCloseable` | Đóng scanner và nguồn nếu phù hợp |
| `java.sql.Connection` | `AutoCloseable` | Trả/đóng kết nối theo triển khai |
| Lớp tự viết `implements AutoCloseable` | `AutoCloseable` | Định nghĩa công việc đóng cụ thể |

Một đối tượng bất kỳ **không trở thành resource hợp lệ chỉ vì nó có phương thức tên `close()`**. Kiểu của nó phải triển khai `AutoCloseable` để dùng trực tiếp trong resource specification.

Nếu lớp tự viết:

```java
final class LocalResource implements AutoCloseable {
    @Override
    public void close() {
        System.out.println("closed");
    }
}
```

thì một biến kiểu `LocalResource` có thể được quản lý bởi `try-with-resources`. Ví dụ này là **lớp minh họa**; `close()` được override không khai báo checked exception, nên người dùng lớp này không phải xử lý checked `Exception` chỉ vì gọi `close()`.

### `AutoCloseable` và checked exception

Interface `AutoCloseable.close()` cho phép khai báo `throws Exception`; các lớp cụ thể thường khai báo một kiểu hẹp hơn hoặc không khai báo checked exception. Trình biên dịch dựa trên **kiểu tĩnh của resource** và lời gọi liên quan để kiểm tra các checked exception có thể xảy ra khi khởi tạo, dùng và đóng.

Ví dụ nếu giữ tài nguyên bằng biến kiểu `AutoCloseable` và `close()` có thể ném checked `Exception`, một phương thức dùng nó có thể cần `catch` hoặc `throws Exception` phù hợp. Không nên mặc định mọi `try-with-resources` đều chỉ cần `catch (IOException)`.

## 3. Resource specification: khai báo trong ngoặc ngay sau `try`

Dạng khai báo mới:

```java
try (ResourceType resource = createResource()) {
    useResource(resource);
}
```

Đây là **cấu trúc minh họa**, yêu cầu `ResourceType implements AutoCloseable` và có các phương thức tương ứng. Cụm `ResourceType resource = createResource()` gồm:

- **Kiểu resource:** phải tương thích `AutoCloseable`.
- **Tên biến:** ví dụ `resource`, dùng trong phạm vi mà Java cho phép.
- **Initializer:** biểu thức tạo hoặc cung cấp tham chiếu tới resource, được đánh giá lúc đi vào cấu trúc.

Có thể khai báo nhiều resource, ngăn cách bằng dấu chấm phẩy:

```java
try (FirstResource first = openFirst();
     SecondResource second = openSecond()) {
    useBoth(first, second);
}
```

Hai kiểu, hai hàm mở và hàm sử dụng ở đây là **cấu trúc minh họa**, không phải chương trình độc lập.

Tính chất chính:

1. Các initializer được đánh giá **theo thứ tự từ trái sang phải**.
2. Phần thân `try` chạy sau khi việc khởi tạo resource hoàn tất.
3. Khi thoát, các resource đã được khởi tạo thành công sẽ được đóng theo **thứ tự ngược**: cuối cùng mở/khai báo thì đóng trước.
4. Nếu khởi tạo resource thứ hai thất bại, resource thứ nhất đã được khởi tạo vẫn được đóng, dù phần thân `try` chưa chạy.
5. Sau khi đóng xong, `catch` và `finally` gắn với cấu trúc `try-with-resources` mới được thực thi khi thích hợp.

Dấu `;` cuối danh sách resource có thể được dùng theo cú pháp Java; nó không tạo thêm một resource rỗng.

## 4. Phạm vi của biến resource khai báo mới

Với cấu trúc:

```java
try (var reader = java.nio.file.Files.newBufferedReader(path)) {
    System.out.println(reader.readLine());
}
```

biến `reader` thuộc phạm vi tài nguyên của cấu trúc, **không phải biến cục bộ của cả phương thức**.

### Dùng được trong phần thân `try`

```java
try (var reader = java.nio.file.Files.newBufferedReader(path)) {
    String first = reader.readLine();
    String second = reader.readLine();
    System.out.println(first);
    System.out.println(second);
}
```

Đây là đoạn minh họa với `path` và hợp đồng `IOException` thích hợp. Cả hai lần đọc đều dùng cùng reader đang được quản lý. Sau khi khối kết thúc, Java đóng reader.

### Không dùng tên resource đó trong `catch` cùng cấp

Đoạn sau **cố ý không biên dịch**:

```java
try (var reader = java.nio.file.Files.newBufferedReader(path)) {
    System.out.println(reader.readLine());
} catch (java.io.IOException ex) {
    reader.close(); // Lỗi: reader không nằm trong scope.
}
```

Điều cần sửa không phải thêm `throws` cho `close()`, mà là hiểu đúng **scope**. `reader` khai báo trong resource specification không được nhìn thấy ở thân `catch`. Java vốn đã gọi `close()` khi thoát khỏi phần thân `try`, nên câu đóng thêm cũng không cần thiết.

### Không dùng tên resource đó trong `finally` cùng cấp

```java
try (var reader = java.nio.file.Files.newBufferedReader(path)) {
    System.out.println(reader.readLine());
} finally {
    // reader.close(); // Sai scope; resource đã được quản lý tự động.
    System.out.println("read attempt finished");
}
```

Đây là cấu trúc minh họa, cần xử lý hoặc khai báo `IOException` có thể phát sinh. `finally` có thể thực hiện công việc tổng kết nhưng không truy cập trực tiếp biến `reader` vừa khai báo trong resource specification. Việc đóng resource xảy ra **trước** `finally` đi kèm.

### Không dùng tên resource ở các câu sau `try`

```java
try (LocalResource r = new LocalResource()) {
    System.out.println("inside");
}
// r.close(); // Lỗi biên dịch: không còn scope.
```

Đây là đoạn minh họa khi `LocalResource` được định nghĩa như ở phần trước. Không nên biến một resource cục bộ chỉ tồn tại trong vùng quản lý thành phụ thuộc lâu dài ở bên ngoài.

**Ghi nhớ:** “biến resource không còn scope” và “đối tượng resource đã được đóng” là hai sự thật **khác nhau** nhưng thường cùng xảy ra khi rời cấu trúc `try-with-resources` khai báo mới.

## 5. Nhiều resource: biến khai báo trước có thể được dùng để khởi tạo resource sau

Các khai báo trong resource specification được xử lý tuần tự. Một resource đứng sau có thể phụ thuộc vào resource đứng trước khi được khởi tạo.

Ví dụ đọc văn bản bằng cách bọc stream trong reader:

```java
try (var input = java.nio.file.Files.newInputStream(path);
     var reader = new java.io.BufferedReader(
             new java.io.InputStreamReader(
                     input, java.nio.charset.StandardCharsets.UTF_8))) {
    System.out.println(reader.readLine());
}
```

Đây là **đoạn minh họa**, yêu cầu biến `path` hợp lệ và xử lý hoặc khai báo `IOException`.

- Biến `input` được khai báo trước, nên initializer của `reader` có thể tham chiếu tới `input`.
- `reader` sử dụng `input` làm nguồn dữ liệu.
- Java sẽ gọi `reader.close()` trước, sau đó gọi `input.close()` theo quy tắc ngược thứ tự.
- Vì `reader.close()` có thể đã đóng stream bên dưới, `input.close()` có thể là lần đóng thứ hai. Nhiều implementation đóng lặp an toàn, nhưng không nên mặc định mọi tài nguyên đều chịu được double-close giống nhau.

Đối với stream được reader **sở hữu hoàn toàn**, thường chỉ cần đăng ký **reader** trong resource specification để tránh đóng chồng không cần thiết:

```java
try (var reader = java.nio.file.Files.newBufferedReader(
        path, java.nio.charset.StandardCharsets.UTF_8)) {
    System.out.println(reader.readLine());
}
```

Quyết định đăng ký resource nào phụ thuộc **ownership**: đối tượng nào chịu trách nhiệm đóng đối tượng nào, và mỗi resource có độc lập cần đóng hay không.

### Không dùng resource khai báo sau trong initializer của resource trước

Các khai báo resource tuân theo quy tắc phạm vi và definite assignment của Java. Không thể dùng một biến chưa được khai báo hoặc chưa có giá trị hợp lệ ở vị trí khởi tạo resource phía trước.

```java
// Cố ý sai: second chưa được khai báo tại initializer của first.
try (LocalResource first = second;
     LocalResource second = new LocalResource()) {
    // ...
}
```

Khi cần quan hệ phụ thuộc, hãy khai báo **nguồn trước, đối tượng phụ thuộc sau**, và cân nhắc vòng đời/ownership để tránh lỗi đóng chồng.

## 6. Biến resource khai báo trong ngoặc có thể gán lại không?

**Không.** Các biến resource được khai báo trực tiếp trong resource specification có ngữ nghĩa **`final`**: bạn không được gán cho biến resource một tham chiếu khác trong phần thân `try`.

```java
try (LocalResource resource = new LocalResource()) {
    // resource = new LocalResource(); // Lỗi biên dịch.
}
```

Dòng bị comment là lỗi nếu bỏ comment. Nhưng `final` ở đây **không khiến đối tượng resource bất biến**. Bạn vẫn có thể gọi các phương thức hợp lệ của nó, đọc dữ liệu, ghi vào stream hoặc thay đổi trạng thái nội bộ theo API.

Cấm gán lại biến mang ý nghĩa quan trọng đối với dọn dẹp: Java cần biết **đối tượng đã được đăng ký đóng**. Nếu có thể âm thầm thay biến sang đối tượng khác, một đối tượng có thể bị mất tham chiếu và rò rỉ tài nguyên.

Hãy phân biệt:

- **Biến tham chiếu `final`:** không thể bị gán lại.
- **Đối tượng resource:** có thể có trạng thái mutable.
- **Trạng thái đóng/mở:** là một phần vòng đời của resource; `final` không bảo đảm đối tượng còn mở.
- **Tính an toàn đa luồng:** `final` không tự biến phương thức của resource thành thread-safe.

Tương tự `catch (final IOException ex)`, từ khóa `final` không có nghĩa là không được gọi phương thức trên đối tượng.

## 7. Dùng resource đã khai báo bên ngoài — Java 9 trở lên

Từ **Java 9**, resource specification còn có thể sử dụng **tên biến đã tồn tại** (hoặc biểu thức truy cập biến phù hợp), với điều kiện biến cục bộ được tham chiếu là `final` hoặc **effectively final** và đã được gán chắc chắn.

Ví dụ Java 17:

```java
var reader = java.nio.file.Files.newBufferedReader(path);
try (reader) {
    System.out.println(reader.readLine());
}
```

Đây là **đoạn minh họa** trong một phương thức có `path` và xử lý `IOException` phù hợp.

Điểm khác biệt với `try (var reader = ...)`:

- `reader` được khai báo **bên ngoài** resource specification, nên tên biến vẫn có thể nằm trong scope **sau** cấu trúc `try`.
- Java vẫn gọi `reader.close()` khi rời `try`.
- Người viết không được gán lại `reader` nếu đang dùng nó dưới dạng resource variable-access theo điều kiện *effectively final*.
- Đọc lại hoặc ghi tiếp qua `reader` **sau khi bị đóng** thường không hợp lệ về vòng đời và có thể ném ngoại lệ như `IOException`, tùy implementation.

Ví dụ minh họa:

```java
var reader = java.nio.file.Files.newBufferedReader(path);
try (reader) {
    System.out.println(reader.readLine());
}
System.out.println(reader != null); // Biến còn scope; reader đã đóng.
```

Dòng in boolean ở đây chỉ kiểm tra tham chiếu không null, **không** nói rằng resource còn mở. Chỉ nên kiểm tra trạng thái đóng/mở thông qua hợp đồng API phù hợp; nhiều tài nguyên không cung cấp `isOpen()` hay `isClosed()` công khai.

### *Effectively final* nghĩa là gì?

Một biến cục bộ được coi là **effectively final** khi không bị gán lại theo quy tắc của Java mặc dù không viết từ khóa `final`.

Ví dụ hợp lệ:

```java
LocalResource reusable = new LocalResource();
try (reusable) {
    System.out.println("using reusable");
}
```

Biến `reusable` đã được gán một lần tại khai báo và không bị gán lại.

Ví dụ **cố ý không biên dịch**:

```java
LocalResource reusable = new LocalResource();
reusable = new LocalResource(); // Gán lại biến.
try (reusable) {
    System.out.println("using reusable");
}
```

Vì `reusable` bị gán lại, nó không còn *effectively final*. Đừng giải quyết bằng cách thêm `final` vào vị trí không phù hợp; cách đúng là quản lý rõ một tham chiếu resource duy nhất trong suốt phạm vi sử dụng hoặc khai báo mới trực tiếp bên trong `try`.

**Lưu ý về phiên bản:** cú pháp `try (existingResource)` không dùng được trong Java 7/8. Đối với Java 17+ thì đây là cách viết hợp lệ, miễn thỏa các ràng buộc của ngôn ngữ.

## 8. Ví dụ Java 17+ hoàn chỉnh: resource khai báo trong và ngoài

Lưu tệp **`ResourceScopeDemo.java`**:

```java
public class ResourceScopeDemo {

    static final class TrackedResource implements AutoCloseable {
        private final String name;
        private boolean closed;

        TrackedResource(String name) {
            this.name = name;
            System.out.println("open " + name);
        }

        void use() {
            if (closed) {
                throw new IllegalStateException(
                        name + " is already closed");
            }
            System.out.println("use " + name);
        }

        boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            closed = true;
            System.out.println("close " + name);
        }
    }

    public static void main(String[] args) {
        System.out.println("CASE declared-inside");

        try (TrackedResource first = new TrackedResource("first");
             TrackedResource second = new TrackedResource("second")) {
            first.use();
            second.use();
        }

        System.out.println("CASE declared-outside");

        TrackedResource external = new TrackedResource("external");

        // Java 9+: biến ngoài try, không bị gán lại.
        try (external) {
            external.use();
        }

        System.out.println(
                "external still in scope=" + (external != null));
        System.out.println(
                "external closed=" + external.isClosed());
    }
}
```

Lệnh biên dịch/chạy:

```bash
javac -encoding UTF-8 ResourceScopeDemo.java
java ResourceScopeDemo
```

**Stdout chính xác:**

```text
CASE declared-inside
open first
open second
use first
use second
close second
close first
CASE declared-outside
open external
use external
close external
external still in scope=true
external closed=true
```

### Giải thích

**Trường hợp khai báo trong `try`:**

1. Tạo `first` rồi `second`, nên in `open first`, `open second`.
2. Trong thân `try`, cả hai biến đang nằm trong scope; gọi `use` là hợp lệ.
3. Khi rời khối, Java đóng `second` trước `first`, nên dòng đóng xuất hiện theo thứ tự ngược.
4. Sau cấu trúc này, tên `first` và `second` không được truy cập từ `main` nữa; chương trình cố ý không chứa tham chiếu ngoài phạm vi.

**Trường hợp khai báo ngoài `try`:**

1. `external` được tạo ở phạm vi của `main`, sau đó dùng cú pháp `try (external)`.
2. `external.use()` chạy khi tài nguyên đang mở.
3. Java gọi `external.close()` trước khi tới hai câu in cuối.
4. Biến `external` vẫn còn trong scope, nhưng phương thức `isClosed()` của lớp giả lập trả `true`. Nếu gọi `external.use()` lúc này, lớp sẽ chủ động ném `IllegalStateException`.

Lớp `TrackedResource` được viết để đầu ra không phụ thuộc tệp thực, hệ điều hành hay chính sách log. Nó cũng cho thấy **scope của biến** và **trạng thái vòng đời của resource** là hai đặc điểm khác nhau.

## 9. Resource nào được đóng khi việc khởi tạo thất bại?

Xét:

```java
try (ResourceA a = openA();
     ResourceB b = openB()) {
    process(a, b);
}
```

Đây là sơ đồ minh họa. Nếu `openB()` ném ngoại lệ:

- `a` đã khởi tạo thành công sẽ được đóng.
- `b` không khởi tạo thành công nên **không** có đối tượng `b` hợp lệ để Java đóng theo declaration đó.
- Phần thân `process(a,b)` **không chạy**.
- Ngoại lệ từ initializer của `b` sẽ tiếp tục được xử lý theo `catch` phù hợp của cấu trúc sau khi đóng tài nguyên đã khởi tạo.
- Nếu `a.close()` cũng thất bại, ngoại lệ khi đóng có thể được lưu vào **suppressed exceptions** của ngoại lệ chính từ initializer `b`.

Không đúng nếu cho rằng Java “chỉ đóng tài nguyên khi đã vào phần thân `try`”. Resource được khởi tạo trước điểm lỗi vẫn được quản lý trong quá trình thoát khi khởi tạo tài nguyên sau thất bại.

### Thời điểm đóng so với `catch` và `finally`

Nếu viết:

```java
try (LocalResource r = new LocalResource()) {
    process(r);
} catch (RuntimeException ex) {
    handle(ex);
} finally {
    afterAttempt();
}
```

đây là một khung minh họa với các phương thức được định nghĩa phù hợp. Trình tự chuẩn là:

1. Khởi tạo resource.
2. Thực thi thân `try`.
3. Đóng resource khi rời thân `try` (hoặc sau lỗi xảy ra trong lúc khởi tạo).
4. Chuyển tới `catch` tương thích, nếu có ngoại lệ.
5. Thực hiện `finally` gắn với cấu trúc, nếu có.

Điều này giải thích tại sao handler `catch` có thể quan sát **lỗi từ `close()`** dù tên biến resource khai báo mới không nằm trong scope của handler. Handler nhận **đối tượng ngoại lệ**, không cần và không được dựa vào quyền truy cập biến resource đó.

## 10. Ví dụ Java 17+ hoàn chỉnh: lỗi trong thân, lỗi đóng và lỗi khởi tạo

Lưu mã thành **`ResourceFailureDemo.java`**:

```java
public class ResourceFailureDemo {

    static final class Tracked implements AutoCloseable {
        private final String name;
        private final boolean failOnClose;

        Tracked(String name, boolean failOnClose) {
            this.name = name;
            this.failOnClose = failOnClose;
            System.out.println("open " + name);
        }

        @Override
        public void close() {
            System.out.println("close " + name);

            if (failOnClose) {
                throw new IllegalStateException(
                        "close failed: " + name);
            }
        }
    }

    static Tracked open(
            String name, boolean failOpen, boolean failClose) {
        if (failOpen) {
            System.out.println("open failed: " + name);
            throw new IllegalArgumentException(
                    "init failed: " + name);
        }

        return new Tracked(name, failClose);
    }

    static void bodyAndCloseFailure() {
        System.out.println("CASE body-and-close");

        try (Tracked first = open("A", false, true);
             Tracked second = open("B", false, true)) {
            System.out.println("body: failed");
            throw new IllegalArgumentException("body failed");

        } catch (IllegalArgumentException ex) {
            System.out.println("primary: " + ex.getMessage());
            System.out.println(
                    "suppressed count: " + ex.getSuppressed().length);

            for (Throwable suppressed : ex.getSuppressed()) {
                System.out.println(
                        "suppressed: " + suppressed.getMessage());
            }
        }
    }

    static void initializationFailure() {
        System.out.println("CASE initializer");

        try (Tracked first = open("X", false, false);
             Tracked second = open("Y", true, false)) {
            System.out.println("body: never runs");

        } catch (IllegalArgumentException ex) {
            System.out.println("caught: " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        bodyAndCloseFailure();
        initializationFailure();
    }
}
```

Chạy bằng JDK 17+:

```bash
javac -encoding UTF-8 ResourceFailureDemo.java
java ResourceFailureDemo
```

**Stdout chính xác:**

```text
CASE body-and-close
open A
open B
body: failed
close B
close A
primary: body failed
suppressed count: 2
suppressed: close failed: B
suppressed: close failed: A
CASE initializer
open X
open failed: Y
close X
caught: init failed: Y
```

### Phân tích kết quả

**Trường hợp body và cả hai lệnh đóng đều ném ngoại lệ:**

- Resource A mở trước, B mở sau.
- Phần thân ném `IllegalArgumentException("body failed")`. Đây là **ngoại lệ chính**.
- Java gọi `B.close()` trước, rồi `A.close()`; mỗi phương thức ném `IllegalStateException`.
- Hai lỗi khi đóng được thêm vào danh sách `getSuppressed()` của ngoại lệ chính theo thứ tự B rồi A.
- `catch (IllegalArgumentException)` chạy **sau khi hoàn thành quá trình đóng**, nên đọc được `getSuppressed().length == 2`.

**Trường hợp initializer thứ hai thất bại:**

- `first` khởi tạo thành công từ `open("X", false, false)`.
- `open("Y", true, false)` ném lỗi trước khi tạo `Tracked` thứ hai.
- Java đóng `first`; thân `try` không thực thi.
- Handler bắt `IllegalArgumentException("init failed: Y")`.
- Đối tượng `second` không được tạo, vì vậy không có dòng `close Y`.

Điểm chung: tài nguyên đã được khởi tạo thành công được đóng trước khi người gọi đi tiếp, bất kể lý do thoát là lỗi từ initializer hay lỗi từ phần thân.

## 11. Scope, vòng đời, ownership: ba câu hỏi không thể thay thế nhau

Ba khái niệm thường bị trộn lẫn:

| Khía cạnh | Câu hỏi | Cách xác định |
| --- | --- | --- |
| **Scope** | Mã hiện tại có nhìn thấy tên biến resource không? | Quy tắc khai báo và phạm vi khối Java |
| **Lifetime / trạng thái đóng** | Tài nguyên đang mở hay đã đóng? | Hợp đồng của `AutoCloseable` và API cụ thể |
| **Ownership** | Ai chịu trách nhiệm đóng tài nguyên? | Thiết kế hàm/API và quy ước sở hữu |

Ví dụ một phương thức **nhận** reader từ caller:

```java
static String readOneLine(java.io.BufferedReader reader)
        throws java.io.IOException {
    return reader.readLine();
}
```

Nếu caller vẫn sở hữu reader và muốn đọc nhiều lần, phương thức `readOneLine` thường **không nên tự đóng** reader mà nó không sở hữu. Ngược lại, hàm tự mở tệp riêng cho một thao tác thường nên tự đóng bằng `try-with-resources` trong phạm vi đó.

Ví dụ phương thức tự sở hữu reader:

```java
static String readFirstLine(java.nio.file.Path path)
        throws java.io.IOException {
    try (var reader = java.nio.file.Files.newBufferedReader(path)) {
        return reader.readLine();
    }
}
```

`reader` chỉ nằm trong scope của `try`; caller nhận kết quả `String` nhưng không nắm resource. Cách này giúp encapsulation và giảm nguy cơ dùng tài nguyên sau khi đóng.

Không nên mở tài nguyên, trả trực tiếp một đối tượng phụ thuộc vào tài nguyên ấy rồi đóng tài nguyên trước khi caller sử dụng, trừ khi API đã định nghĩa rõ dữ liệu trả về độc lập với resource. Ví dụ, trả về một `Stream<String>` đọc lười từ file sau khi reader bị đóng có thể gây lỗi khi caller duyệt stream.

## 12. Dùng resource sau khi `try`: tên biến còn không có nghĩa là còn dùng được

Với biến khai báo ngoài:

```java
var stream = java.nio.file.Files.newInputStream(path);

try (stream) {
    System.out.println(stream.read());
}

int next = stream.read(); // Cố ý sai về vòng đời tài nguyên.
```

Đoạn trên có thể **hợp lệ về phạm vi và kiểu**, nhưng đọc sau khi đóng là hành vi không phù hợp với hợp đồng của phần lớn stream đọc tệp và có thể ném `IOException`. Nên bỏ dòng đọc cuối.

Nếu một phương thức cần sử dụng tài nguyên trong nhiều bước, hãy gộp những bước thật sự cần tài nguyên **trước khi thoát `try`**:

```java
try (var stream = java.nio.file.Files.newInputStream(path)) {
    int first = stream.read();
    int second = stream.read();
    consume(first, second);
}
```

Đây là **đoạn minh họa**, giả định `consume` đã được định nghĩa và mã xử lý `IOException`. Nếu cần dữ liệu sau khi đóng, đọc/copy dữ liệu sang dạng độc lập trước khi rời khối, thay vì đưa resource còn phụ thuộc tới caller.

### Resource không nhất thiết chỉ “đóng một lần”

Khi đóng stream bọc, reader, wrapper hoặc resource được chia sẻ giữa nhiều thành phần, một đối tượng có thể bị gọi `close()` nhiều lần qua các đường quản lý khác nhau. Java không bảo đảm **mọi lớp `AutoCloseable` đều chịu được đóng nhiều lần giống nhau**. Cần đọc hợp đồng loại tài nguyên cụ thể và tránh đăng ký đóng trùng một đối tượng khi không cần.

Nếu resource còn được dùng bởi thành phần khác, việc đóng trong `try-with-resources` hiện tại có thể phá vỡ công việc bên ngoài. Vì thế `try-with-resources` nên áp dụng cho tài nguyên mà phạm vi hiện tại **chịu trách nhiệm sở hữu và đóng**.

## 13. Resource trong `try` lồng nhau

Có thể lồng nhiều vùng quản lý tài nguyên:

```java
try (OuterResource outer = openOuter()) {
    useOuter(outer);

    try (InnerResource inner = openInner(outer)) {
        useInner(inner);
    }

    continueWithOuter(outer);
}
```

Đây là **cấu trúc minh họa**, các kiểu/phương thức cần được định nghĩa. Ý nghĩa:

1. `outer` được mở và nằm trong scope của toàn bộ thân `try` ngoài.
2. `inner` được mở trong `try` trong; tên nó chỉ ở phạm vi trong.
3. Khi rời `try` trong, `inner` được đóng.
4. `outer` vẫn còn nằm trong vùng quản lý của `try` ngoài và có thể tiếp tục được dùng **nếu API/ownership không khiến việc đóng `inner` đồng thời đóng `outer`**.
5. Khi rời `try` ngoài, `outer` được đóng.

Ví dụ này nhấn mạnh rằng thứ tự scope là có tính lồng nhau. Tuy nhiên, một wrapper `inner` có thể sở hữu resource `outer` và đóng nó khi `inner.close()` chạy. Khi đó câu `continueWithOuter(outer)` có thể không hợp lệ về **vòng đời**, dù hợp lệ về **scope**. Hãy đọc hợp đồng ownership của lớp wrapper.

## 14. Ngoại lệ khi đóng và thời điểm `catch` chạy

Một mô hình dễ nhớ:

```text
Khởi tạo resource theo thứ tự khai báo
                 ↓
          Chạy thân try
                 ↓
Thoát try: bình thường / return / throw
                 ↓
Đóng các resource đã khởi tạo, ngược thứ tự
                 ↓
       catch tương thích, nếu có
                 ↓
       finally đi kèm, nếu có
                 ↓
Tiếp tục, trả giá trị hoặc ném ngoại lệ theo kết quả cuối
```

Vì đóng xảy ra trước `catch` cùng cấp, handler có thể bắt được ngoại lệ khi `close()` ném lỗi. Trường hợp thân `try` cũng ném lỗi, đối tượng lỗi đóng thường có mặt dưới dạng suppressed exception của lỗi chính theo quy tắc `try-with-resources`.

### Nếu phần thân định `return` nhưng `close()` ném lỗi?

Giả sử:

```java
static int readValue() {
    try (ClosingResource r = new ClosingResource()) {
        return 42;
    }
}
```

Đây là **khung minh họa**, `ClosingResource` cần là lớp `AutoCloseable` đã định nghĩa. Nếu `close()` ném unchecked `IllegalStateException`, phương thức **không trả `42`**; ngoại lệ đóng có thể lan ra. Nếu `close()` ném checked exception thì phương thức còn phải tuân thủ hợp đồng `catch`/`throws`.

Ngược lại, nếu **phần thân ném ngoại lệ E1** rồi `close()` ném E2, `try-with-resources` ưu tiên E1 là lỗi chính và thêm E2 vào suppressed. Đây là khác biệt với `finally` thủ công ném E2 có thể trực tiếp ghi đè E1.

### Nếu phần thân chạy thành công, `close()` vẫn có thể thất bại

`try-with-resources` không “nuốt mọi lỗi đóng” để bảo đảm thành công. Nếu chỉ có lỗi đóng, lỗi đó trở thành ngoại lệ được xử lý/lan truyền. Vì vậy một phép ghi có thể ghi xong phần thân nhưng lại gặp lỗi khi flush/close, dẫn tới thất bại cuối cùng; cần xử lý dựa trên hợp đồng thao tác cụ thể.

## 15. Hướng dẫn thiết kế API và lựa chọn phạm vi

### Mở tài nguyên càng gần phạm vi sử dụng càng tốt

Khi một bước chỉ cần reader trong một phương thức, thường nên viết:

```java
static long countLines(java.nio.file.Path path)
        throws java.io.IOException {
    try (var reader = java.nio.file.Files.newBufferedReader(path)) {
        long count = 0;
        while (reader.readLine() != null) {
            count++;
        }
        return count;
    }
}
```

Đây là phương thức minh họa có thể đặt trong một lớp Java. Reader được đóng khi rời phạm vi, dù có ngoại lệ trong lúc đọc. Phương thức trả một số `long` độc lập với trạng thái reader, nên caller không cần tự quản lý tài nguyên.

### Không kéo dài scope chỉ vì muốn log tên resource

Nếu cần bối cảnh trong `catch`, lưu **dữ liệu mô tả phù hợp** ở ngoài resource specification:

```java
String logicalName = "configuration";

try (var reader = java.nio.file.Files.newBufferedReader(path)) {
    System.out.println(reader.readLine());
} catch (java.io.IOException ex) {
    System.out.println("Cannot read " + logicalName);
}
```

Ở đây `logicalName` nằm ngoài `try` nên handler truy cập được. Không cần đưa `reader` ra ngoài chỉ để đọc một chuỗi mô tả; tên resource không nằm trong scope của handler và resource đã được đóng trước khi handler chạy.

### Đừng vô tình đóng tài nguyên không thuộc quyền sở hữu

Ví dụ `System.in` là stream tiêu chuẩn của tiến trình. Nếu một phạm vi ngắn tự đóng một wrapper sở hữu `System.in`, các phần khác của chương trình có thể không đọc được đầu vào nữa. Trước khi đăng ký resource trong `try-with-resources`, hãy xác định **ai cấp phát, ai sử dụng tiếp và ai chịu trách nhiệm đóng**.

### Kết hợp với logging và handler ở đúng tầng

Tầng thao tác nên tự quản lý reader hoặc stream của mình; tầng caller chỉ cần xử lý `IOException` theo hợp đồng. Không yêu cầu caller biết resource tên gì hoặc phải gọi `close()` thủ công trên biến mà nó không sở hữu.

Một `catch` quá rộng rồi trả dữ liệu “thành công giả” có thể che lỗi khi đọc. Chỉ chọn fallback nếu đó là chính sách thật. Nếu dữ liệu bắt buộc, việc báo lỗi phù hợp thường tốt hơn trả `null` hoặc chuỗi rỗng.

## 16. Những lỗi thường gặp

| Sai lầm | Vì sao sai | Cách sửa |
| --- | --- | --- |
| Dùng tên resource khai báo trong `try (...)` ở `catch` | Resource variable không nằm trong scope của handler | Dùng metadata khai báo ngoài, không truy cập resource để đóng lại |
| Gọi resource sau khi `try (existingResource)` kết thúc | Biến có thể còn scope nhưng object đã đóng | Dùng toàn bộ thao tác phụ thuộc trước khi thoát `try` |
| Gán lại biến resource khai báo trong ngoặc | Resource variable ngầm `final` | Không gán lại; dùng biến mới khi cần |
| Gán lại biến rồi dùng `try (variable)` | Biến cục bộ không còn *effectively final* | Khai báo mới trong `try` hoặc giữ một tham chiếu không bị gán lại |
| Tưởng `AutoCloseable` luôn chỉ ném `IOException` | `close()` có thể khai báo checked `Exception` hoặc kiểu khác | Kiểm tra chữ ký kiểu resource và hợp đồng `throws` |
| Tưởng resource thứ nhất không đóng nếu mở thứ hai lỗi | Java đóng các resource đã khởi tạo thành công | Tin cậy cơ chế khởi tạo/đóng của `try-with-resources` |
| Tưởng nhiều resource đóng từ trái sang phải | Java đóng theo thứ tự ngược | Kiểm tra phụ thuộc để quản lý ownership |
| Cố bắt lỗi `close()` bằng cách đóng lại trong `catch` | Lần đóng tự động đã xảy ra, có thể gây lỗi mới | Xử lý exception, suppressed và chính sách vận hành |
| Trả một stream lazy phụ thuộc reader đã bị đóng | Caller sử dụng dữ liệu sau khi nguồn đóng | Giữ vòng đời sở hữu đúng phạm vi hoặc trả dữ liệu đã vật hóa |
| Bọc stream rồi đăng ký đóng cả wrapper và nguồn không cần thiết | Có thể double-close và làm mờ ownership | Xác định lớp nào đóng nguồn bên dưới |
| Đóng tài nguyên được cho mượn từ caller | Có thể làm hỏng công việc của caller | Thống nhất hợp đồng ai đóng resource |
| Bỏ qua suppressed exceptions khi body và close cùng lỗi | Mất bối cảnh lỗi đóng phụ | Xem `getSuppressed()` nếu cần chẩn đoán |

## 17. Bài tập tự kiểm tra

**Bài A.** Resource khai báo `try (var reader = open())` có thể được dùng ở `catch` cùng cấp không?

**Không.** Tên `reader` không thuộc phạm vi của `catch`. Tuy nhiên, ngoại lệ từ khởi tạo, đọc hoặc đóng reader vẫn có thể đến handler thích hợp.

**Bài B.** Một biến `reader` khai báo trước `try (reader)` (Java 17) có còn trong scope sau cấu trúc không?

**Có**, theo phạm vi khai báo ngoài. Nhưng reader đã được đóng; không nên đọc tiếp chỉ vì tên biến còn dùng được.

**Bài C.** Trong `try (R a = openA(); R b = openB())`, resource nào được đóng trước?

**`b`**, rồi `a`, nếu cả hai được khởi tạo thành công.

**Bài D.** `openA()` thành công, `openB()` ném lỗi. Phần thân `try` có chạy không, và `a` có đóng không?

**Phần thân không chạy; `a` được đóng.** Nếu `a.close()` cũng lỗi, lỗi đóng có thể bị suppressed dưới lỗi khởi tạo `b`.

**Bài E.** Có thể viết `try (resource)` khi `resource` là biến cục bộ đã bị gán lại không?

**Không** theo cú pháp Java 9+ này: biến cục bộ phải `final` hoặc *effectively final*. Dùng một biến mới không gán lại hoặc khai báo trực tiếp trong resource specification.

**Bài F.** `close()` ném một `IllegalStateException` trong khi phần thân `try` đã ném `IllegalArgumentException`. Với `try-with-resources`, lỗi nào là lỗi chính?

Thông thường **`IllegalArgumentException` từ thân `try`** là lỗi chính; lỗi lúc đóng xuất hiện trong `getSuppressed()` của nó.

**Bài G.** Phần thân `try` chuẩn bị `return 42`, nhưng resource `close()` ném lỗi. Caller có chắc nhận `42` không?

**Không.** Nếu lỗi từ `close()` không được xử lý, lời gọi có thể kết thúc bằng ngoại lệ thay vì trả `42`.

**Bài H.** Một reader bọc input stream và tự đóng input stream khi reader đóng. Có nhất thiết khai báo cả hai trong danh sách resource không?

**Không nhất thiết.** Nếu reader sở hữu và đóng stream, thường chỉ đăng ký reader là đủ. Đọc hợp đồng lớp và ownership để tránh đóng lặp hoặc sử dụng sau đóng.

**Bài I.** Resource variable khai báo trong resource specification có thể thay đổi trạng thái đối tượng không?

**Có**, nếu API cho phép. Biến resource ngầm `final` chỉ cấm **gán lại tham chiếu**, không khiến đối tượng bất biến.

**Bài J.** Có nên để phương thức nhận reader do caller sở hữu tự đóng nó trong mọi trường hợp?

**Không mặc định.** Quyết định dựa vào hợp đồng ownership. Phương thức mượn reader để đọc thường không nên tự đóng nếu caller còn cần sử dụng tiếp.

## 18. Checklist áp dụng trong dự án Java

Trước khi viết `try-with-resources`, hãy kiểm tra:

1. **Resource có triển khai `AutoCloseable` không?** Nếu không, nó không phải resource hợp lệ theo cú pháp này.
2. **Ai sở hữu resource?** Có phải phạm vi hiện tại cần đóng nó khi xong hay không?
3. **Khai báo biến ở đâu?** Khai báo mới trong ngoặc `try` nếu chỉ dùng cục bộ; dùng `try (existingResource)` khi biến bên ngoài là `final`/*effectively final* và mục tiêu quản lý vòng đời phù hợp.
4. **Tất cả thao tác cần resource đã nằm trước khi khối kết thúc chưa?** Đừng trả về một đối tượng còn phụ thuộc vào resource đã đóng.
5. **Có nhiều resource không?** Xác minh thứ tự khởi tạo trái→phải và đóng phải→trái, đặc biệt với wrapper sở hữu nguồn.
6. **Initializer có thể ném lỗi không?** Resource đã khởi tạo trước đó sẽ được đóng; phần thân `try` có thể chưa hề chạy.
7. **`close()` có thể ném checked exception nào?** Xác định kiểu tĩnh và chữ ký `throws` cần xử lý.
8. **Thời điểm `catch` chạy đã đúng chưa?** Resource được đóng trước khi handler cùng cấp nhận lỗi.
9. **Có cần điều tra suppressed exception không?** Khi body và close cùng lỗi, đừng chỉ xem message chính rồi bỏ mất tình trạng dọn dẹp.
10. **Biến resource có bị gán lại không?** Biến khai báo trong resource specification ngầm `final`; biến ngoài phải giữ `final`/*effectively final* khi dùng `try (variable)`.
11. **Người gọi có biết cách xử lý lỗi không?** Tầng thấp quản lý resource; tầng phù hợp quyết định phục hồi, trả lỗi hoặc retry.
12. **Có dùng resource sau khi đóng không?** Scope và vòng đời không phải một. Cần dừng dùng resource khi vùng quản lý của nó kết thúc.

## Kết luận

`try-with-resources` gắn **vòng đời của tài nguyên** với **một vùng thực thi xác định**. Khi khai báo resource mới trong `try (...)`, tên biến được sử dụng trong phạm vi tài nguyên và phần thân `try`, nhưng không ở `catch`, `finally` hoặc câu lệnh sau cấu trúc. Khi dùng `try (existingResource)` từ Java 9 trở lên, biến bên ngoài có thể vẫn còn scope nhưng **đối tượng đã được đóng**. Với nhiều resource, Java **khởi tạo từ trái sang phải, đóng từ phải sang trái**, đóng cả những resource đã mở nếu initializer sau thất bại, và có cơ chế **suppressed exceptions** để không đánh mất lỗi chính khi đóng tài nguyên cũng lỗi.

Hiểu rõ **scope, lifetime và ownership** giúp viết mã Java đúng hợp đồng, tránh dùng tài nguyên sau khi đóng và hạn chế rò rỉ tài nguyên cùng các lỗi bị che khuất.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="try-with-resources-004">Dùng resource có sẵn là final hoặc effectively final từ Java 9</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng resource có sẵn là `final` hoặc *effectively final* từ Java 9

## 1. Mục tiêu học tập

`try-with-resources` là cú pháp Java giúp **tự động gọi `close()`** đối với các tài nguyên triển khai `AutoCloseable` khi rời phạm vi `try`. Từ Java 7, cấu trúc này đã hỗ trợ khai báo tài nguyên mới ngay trong dấu ngoặc của `try`. **Từ Java 9**, Java bổ sung cách tham chiếu trực tiếp đến **biến tài nguyên đã tồn tại**, miễn là biến đó là `final` hoặc *effectively final*.

Ví dụ trọng tâm:

```java
java.io.BufferedReader reader =
        new java.io.BufferedReader(new java.io.StringReader("hello"));

try (reader) { // Hợp lệ từ Java 9 nếu reader effectively final.
    System.out.println(reader.readLine());
}
```

Đây là **mảnh mã minh họa** đặt trong một phương thức khai báo hoặc xử lý `IOException`. Không cần khai báo lại `BufferedReader` trong dấu ngoặc và cũng không cần tạo biến alias chỉ để đưa vào `try-with-resources`.

Học xong bài này, bạn có thể:

- Viết đúng cú pháp `try (resource)`, `try (first; second)` và phối hợp resource đã có với khai báo resource mới.
- Phân biệt `final` tường minh và `effectively final`; biết các dạng gán lại gây lỗi biên dịch.
- Giải thích resource được đóng khi hoàn thành bình thường, `return` hoặc ném ngoại lệ, với thứ tự đóng ngược.
- Hiểu biến tài nguyên **vẫn ở trong scope** sau `try` nhưng đối tượng tài nguyên có thể đã đóng.
- Hiểu xử lý ngoại lệ của `close()`, đặc biệt là *suppressed exception*.
- Tránh đóng trùng, dùng lại resource đã đóng, chia sẻ sai quyền sở hữu và dùng nhầm biểu thức không phải variable access.
- Phân biệt chính xác Java 7/8 với cải tiến ngôn ngữ trong Java 9; chạy ví dụ trên JDK 17+.

## 2. Nhắc lại `try-with-resources` trước Java 9

`try-with-resources` ra đời từ **Java 7**. Khi đó, danh sách tài nguyên trong dấu ngoặc phải chứa **các khai báo biến tài nguyên**. Ví dụ:

```java
try (java.io.BufferedReader reader =
             new java.io.BufferedReader(new java.io.StringReader("A\nB"))) {
    System.out.println(reader.readLine());
}
```

Đây là **mảnh mã** ở trong một phương thức có thể xử lý hoặc khai báo `IOException`. Ở cuối khối, Java tự gọi `reader.close()`; bạn không cần thêm `finally { reader.close(); }`.

Yêu cầu về kiểu tài nguyên: biểu thức khai báo phải có kiểu tương thích với `java.lang.AutoCloseable`. Các lớp `Closeable` cũng phù hợp vì `java.io.Closeable` kế thừa `AutoCloseable`.

Trước Java 9, nếu biến đã khai báo từ **bên ngoài**:

```java
java.io.BufferedReader reader =
        new java.io.BufferedReader(new java.io.StringReader("A"));

try (java.io.BufferedReader managed = reader) {
    System.out.println(managed.readLine());
}
```

Java 7/8 cho phép dùng một **biến khai báo mới trong header** như `managed` để quản lý cùng đối tượng mà `reader` đang trỏ đến. Cơ chế này đúng nhưng tạo thêm tên biến và dễ khiến người đọc lầm rằng có hai reader độc lập. Trên thực tế, `managed` và `reader` là hai tham chiếu đến **cùng một đối tượng**; đối tượng ấy được đóng khi ra khỏi khối.

### Thay đổi trong Java 9

Java 9 chấp nhận **truy cập trực tiếp biến đã khai báo trước đó**:

```java
java.io.BufferedReader reader =
        new java.io.BufferedReader(new java.io.StringReader("A"));

try (reader) {
    System.out.println(reader.readLine());
}
```

Trong cú pháp mới, `reader` là một *variable access*, không phải khai báo resource mới. Điều kiện cốt lõi là **biến được truy cập phải `final` hoặc effectively final**, đã được gán giá trị phù hợp trước lúc vào `try` và có kiểu tài nguyên tương thích với `AutoCloseable`.

So sánh nhanh:

| Khía cạnh | Java 7/8 | Java 9+ |
| --- | --- | --- |
| Khai báo resource ngay tại header | `try (BufferedReader r = open())` | Vẫn hợp lệ |
| Dùng biến đã khai báo trước đó | Dùng alias: `try (BufferedReader managed = reader)` | `try (reader)` nếu đủ điều kiện |
| Cần viết `final` tường minh? | Không phải điều kiện của khai báo mới | Không, `effectively final` cũng được |
| Resource được đóng tự động? | Có | Có |
| Thứ tự đóng khi nhiều resource? | Đảo ngược thứ tự khai báo | Đảo ngược thứ tự liệt kê/quản lý |
| Xử lý lỗi `close()` và suppressed? | Có | Giữ nguyên |
| Biến khai báo bên ngoài còn trong scope sau `try`? | Có | Có |

**Quan trọng:** Java 9 **không** thay đổi điều kiện một tài nguyên cần hỗ trợ `AutoCloseable` hay quy tắc đóng. Điểm mới chỉ là cho phép một dạng biểu thức tham chiếu tới **biến tài nguyên đã có**.

## 3. `final` và *effectively final* nghĩa là gì?

### 3.1. Biến `final` tường minh

Một biến cục bộ khai báo bằng `final` không thể được gán lại sau khi đã được khởi tạo:

```java
final java.io.BufferedReader reader =
        new java.io.BufferedReader(new java.io.StringReader("input"));

try (reader) {
    System.out.println(reader.readLine());
}
```

Ví dụ là **mảnh mã** cần nằm trong phương thức xử lý hoặc khai báo `IOException`. Đây là cách dùng hợp lệ từ Java 9.

`final` áp dụng cho **biến tham chiếu**, không làm nội dung đối tượng trở thành bất biến. Trong khi tài nguyên đang mở, lời gọi `reader.readLine()` vẫn thay đổi vị trí đọc của reader; khi rời `try`, cơ chế ngôn ngữ vẫn gọi `close()`.

### 3.2. Biến *effectively final*

Biến cục bộ **không có từ khóa `final`** nhưng không bị gán lại sau lần khởi tạo hợp lệ được coi là *effectively final*:

```java
java.io.BufferedReader reader =
        new java.io.BufferedReader(new java.io.StringReader("input"));

// Không có câu lệnh reader = ... ở bất kỳ nơi nào sau đó.
try (reader) {
    System.out.println(reader.readLine());
}
```

Java 9+ chấp nhận đoạn này. Compiler xác định tính effectively final dựa trên **các phép gán trong mã**, không phải dựa trên nhận xét của lập trình viên hoặc việc dòng gán lại có thực sự chạy trong một bài test.

Một số điều cần phân biệt:

- Biến `reader` có thể chưa ghi `final` nhưng vẫn effectively final.
- Có thể **gọi phương thức làm thay đổi trạng thái đối tượng** qua `reader` mà không làm biến mất tính effectively final.
- Gán lại chính biến bằng `reader = anotherReader;` hoặc `reader = null;` khiến biến không còn effectively final.
- Điều kiện này xét trong phạm vi phù hợp của biến, **không chỉ các câu lệnh trước `try`**. Gán lại biến sau khi ra khỏi `try` cũng có thể khiến `try (reader)` bị compiler từ chối.
- Không nên đồng nhất effectively final với `AutoCloseable`, tính bất biến của đối tượng, hoặc thread-safety. Đây là những khái niệm độc lập.

### 3.3. Ví dụ cố ý không biên dịch: gán lại resource

Tệp **`InvalidReassignment.java`** dưới đây **chủ ý sai**:

```java
import java.io.StringReader;

public class InvalidReassignment {
    public static void main(String[] args) {
        StringReader reader = new StringReader("first");
        reader = new StringReader("second");

        try (reader) { // LỖI: reader không effectively final.
            System.out.println(reader.read());
        }
    }
}
```

Lệnh kiểm tra:

```bash
javac InvalidReassignment.java
```

Compiler từ chối vì biến được dùng làm resource **không phải final hoặc effectively final**. Thông báo cụ thể có thể thay đổi giữa phiên bản javac. Không chạy `java InvalidReassignment` như chương trình thành công vì mã chưa biên dịch được.

Trong chương trình thực tế, còn cần tránh tạo resource thứ nhất rồi **đánh mất tham chiếu để đóng nó** khi gán đè bằng resource thứ hai. Giải pháp không phải thêm `try (reader)` ở cuối, vì resource cũ đã không còn được quản lý. Hãy quản lý từng resource trong phạm vi phù hợp.

### 3.4. Gán lại sau `try` vẫn có thể làm mã không hợp lệ

Mảnh mã **cố ý sai**:

```java
java.io.StringReader reader = new java.io.StringReader("A");
try (reader) {
    System.out.println(reader.read());
}
reader = new java.io.StringReader("B"); // reader không effectively final.
```

Với cú pháp `try (reader)`, điều kiện effectively final áp dụng cho biến trong phạm vi của nó. Việc bạn gán lại ở **phía sau** khối không “khôi phục” tính hợp lệ.

Nếu cần tiếp tục dùng tên biến cho một resource mới, hãy dùng các biến khác nhau hoặc chia tách scope để đảm bảo rõ quyền sở hữu của từng resource.

## 4. Ví dụ chạy được: dùng biến đã có sau khi đóng resource

Tạo tệp **`ExistingResourceDemo.java`**:

```java
public class ExistingResourceDemo {
    static class TrackedResource implements AutoCloseable {
        private final String name;
        private boolean closed;

        TrackedResource(String name) {
            this.name = name;
        }

        void use() {
            if (closed) {
                throw new IllegalStateException("already closed");
            }
            System.out.println("using " + name);
        }

        boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            closed = true;
            System.out.println("close " + name);
        }
    }

    public static void main(String[] args) {
        TrackedResource resource = new TrackedResource("A");

        try (resource) {
            resource.use();
        }

        System.out.println("resource closed: " + resource.isClosed());
    }
}
```

Biên dịch/chạy với JDK 17+:

```bash
javac ExistingResourceDemo.java
java ExistingResourceDemo
```

`stdout`:

```text
using A
close A
resource closed: true
```

**Phân tích:**

1. `resource` được khai báo **trước** header `try`.
2. Biến không có từ khóa `final` nhưng được gán đúng một lần và không gán lại, nên effectively final.
3. `try (resource)` bắt đầu quản lý vòng đời resource hiện có; không tạo ra một đối tượng `TrackedResource` mới.
4. Khi phần thân hoàn thành bình thường, Java gọi `resource.close()`. Ví dụ in `close A`.
5. Biến `resource` còn trong scope sau `try`, nên vẫn có thể gọi `resource.isClosed()` và nhận `true`.

Điểm cuối rất quan trọng: **còn trong scope không có nghĩa còn mở**. Sau `try`, biến vẫn trỏ tới đối tượng cũ, nhưng đối tượng đã được đóng. Nếu gọi `resource.use()` một lần nữa thì sẽ nhận `IllegalStateException` theo hợp đồng tự định nghĩa của ví dụ.

### Trách nhiệm của người dùng tài nguyên

Khi bạn đưa một resource đã tồn tại vào `try (resource)`, bạn đang chấp nhận rằng **khối đó sẽ đóng resource**. Nếu resource được quản lý bởi thành phần khác và vẫn phải mở sau lời gọi, không thể tự ý dùng `try (resource)` mà bỏ qua hợp đồng sở hữu.

Thí dụ thực tế: không tùy tiện đóng một `InputStream` thuộc sở hữu của framework, của một HTTP request do container quản lý, hoặc một kết nối đang được chia sẻ theo lifecycle dài hơn phạm vi hiện tại.

## 5. Dùng biến `final` tường minh: ví dụ độc lập

Tạo tệp **`ExplicitFinalResourceDemo.java`**:

```java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

public class ExplicitFinalResourceDemo {
    public static void main(String[] args) throws IOException {
        final BufferedReader reader =
                new BufferedReader(new StringReader("alpha\nbeta"));

        try (reader) {
            System.out.println(reader.readLine());
            System.out.println(reader.readLine());
        }

        System.out.println("finished reading");
    }
}
```

```bash
javac ExplicitFinalResourceDemo.java
java ExplicitFinalResourceDemo
```

`stdout`:

```text
alpha
beta
finished reading
```

`BufferedReader.readLine()` có khả năng ném `IOException`; thao tác `close()` cũng được quản lý tự động. `main throws IOException` thỏa kiểm tra checked exception. `StringReader` cho dữ liệu trong bộ nhớ để đầu ra ổn định, không phụ thuộc hệ thống tệp.

Biến `reader` vẫn tồn tại sau `try` nhưng reader đã bị đóng. Dòng `finished reading` không cố đọc lại resource. Đừng dùng việc biến vẫn tồn tại để suy ra có thể tiếp tục thực hiện I/O qua nó.

## 6. Nhiều resource có sẵn: thứ tự đóng từ phải sang trái

Khi quản lý nhiều resource trong một header, mỗi resource phải thỏa điều kiện kiểu và tính final/effectively final. Java đóng chúng theo **thứ tự ngược** với thứ tự khai báo hoặc liệt kê trong header.

Tạo tệp **`ExistingResourcesOrderDemo.java`**:

```java
public class ExistingResourcesOrderDemo {
    static class Tracked implements AutoCloseable {
        private final String name;

        Tracked(String name) {
            this.name = name;
        }

        @Override
        public void close() {
            System.out.println("close " + name);
        }
    }

    public static void main(String[] args) {
        Tracked first = new Tracked("first");
        final Tracked second = new Tracked("second");

        try (first; second) {
            System.out.println("body");
        }

        System.out.println("after try");
    }
}
```

```bash
javac ExistingResourcesOrderDemo.java
java ExistingResourcesOrderDemo
```

`stdout`:

```text
body
close second
close first
after try
```

Giải thích:

- `first` là effectively final; `second` là final tường minh.
- `try (first; second)` quản lý cả hai, dù đã được tạo **trước** câu lệnh `try`.
- Khi khối kết thúc, `close()` của `second` được gọi trước `close()` của `first`.
- Đây là quy tắc có ý nghĩa với tài nguyên phụ thuộc nhau: nếu `second` bọc `first`, hãy thiết kế quyền sở hữu và thứ tự đóng phù hợp.

Header cũng cho phép **dấu chấm phẩy cuối** tùy chọn; ví dụ `try (first; second;)` hợp lệ. Tuy vậy, dùng dấu chấm phẩy phân cách giữa các tài nguyên sẽ rõ nhất.

### Tạo resource trước khi vào `try` có rủi ro gì?

Xét:

```java
Resource first = openFirst();
Resource second = openSecond(); // Có thể thất bại.
try (first; second) {
    work();
}
```

Đây là **mảnh minh họa**, không phải chương trình độc lập; các kiểu/hàm chưa khai báo. Nếu `openSecond()` ném ngoại lệ trước khi thực thi `try`, chương trình **chưa đi vào phạm vi quản lý tài nguyên của try-with-resources**. `first` có thể vẫn còn mở và cần được xử lý bởi một phạm vi quản lý bên ngoài.

Đây là nhược điểm đáng cân nhắc khi tạo nhiều resource trước header. Cách an toàn phổ biến là khai báo tài nguyên trực tiếp trong một `try-with-resources`:

```java
try (Resource first = openFirst();
     Resource second = openSecond()) {
    work();
}
```

Đây cũng là **mảnh minh họa** cần khai báo `Resource` và các phương thức tương ứng. Khi `openSecond()` thất bại **trong quá trình khởi tạo các resource của header**, Java tự đóng resource `first` đã được khởi tạo và quản lý thành công trước đó.

**Kết luận:** cú pháp ngắn hơn của Java 9 không có nghĩa nó luôn tốt hơn khai báo resource tại header. Cần xét **thời điểm resource được tạo** và **phạm vi chịu trách nhiệm đóng**.

## 7. Kết hợp resource có sẵn với resource khai báo ngay trong header

Java 9+ có thể kết hợp hai kiểu phần tử trong danh sách resource:

```java
java.io.StringReader source = new java.io.StringReader("first\nsecond");

try (source;
     java.io.BufferedReader buffered =
             new java.io.BufferedReader(source)) {
    System.out.println(buffered.readLine());
}
```

Đây là **mảnh mã** bên trong phương thức khai báo/xử lý `IOException`. `source` đã có sẵn và effectively final; `buffered` là biến resource được khai báo mới trong header. Khi rời khối, Java đóng `buffered` trước, rồi đóng `source`.

Tuy nhiên, cần lưu ý **quyền sở hữu lồng nhau**: `BufferedReader.close()` cũng đóng reader mà nó bao bọc. Vì vậy, ví dụ này có khả năng gọi `close()` đến `source` nhiều hơn một lần. Một số triển khai có thể xử lý việc đóng lặp, nhưng không phải mọi `AutoCloseable` đều bảo đảm idempotent. Trong ứng dụng, thường chỉ nên quản lý **wrapper sở hữu delegate** một lần:

```java
java.io.StringReader source = new java.io.StringReader("first\nsecond");

try (java.io.BufferedReader buffered =
             new java.io.BufferedReader(source)) {
    System.out.println(buffered.readLine());
}
```

Đây là mảnh minh họa theo kiểu Java 7+, trong đó đóng `buffered` sẽ đóng `source` đi kèm. Khi chọn cú pháp kết hợp, hãy xác định rõ ai sở hữu resource gốc để tránh đóng trùng.

### Kết hợp với hai tài nguyên độc lập

Trường hợp thực sự cần trộn hai dạng header mà không gây rủi ro wrapper/delegate, có thể dùng hai tài nguyên không bao bọc nhau. Tạo tệp **`MixedResourcesDemo.java`**:

```java
public class MixedResourcesDemo {
    static class Tracked implements AutoCloseable {
        private final String name;

        Tracked(String name) {
            this.name = name;
        }

        @Override
        public void close() {
            System.out.println("close " + name);
        }
    }

    public static void main(String[] args) {
        Tracked existing = new Tracked("existing");

        try (existing; Tracked temporary = new Tracked("temporary")) {
            System.out.println("working");
        }

        System.out.println("done");
    }
}
```

```bash
javac MixedResourcesDemo.java
java MixedResourcesDemo
```

`stdout`:

```text
working
close temporary
close existing
done
```

`existing` được tham chiếu trực tiếp theo tính năng Java 9; `temporary` được khai báo tại chỗ như Java 7. Hai resource được đóng theo thứ tự ngược của danh sách trong header. Biến `temporary` không tồn tại ngoài `try`; biến `existing` vẫn tồn tại trong scope bên ngoài nhưng resource đã đóng.

## 8. `try-with-resources` đóng trên đường bình thường, `return` và ngoại lệ

Cơ chế đóng không phụ thuộc thân `try` có chạy hết bình thường hay không. Khi thoát do `return`, `break`, `continue` hoặc một ngoại lệ, Java thực hiện thao tác đóng theo quy tắc `try-with-resources`.

Tạo tệp **`EarlyReturnResourceDemo.java`**:

```java
public class EarlyReturnResourceDemo {
    static class Tracked implements AutoCloseable {
        @Override
        public void close() {
            System.out.println("close called");
        }
    }

    static int calculate() {
        Tracked resource = new Tracked();

        try (resource) {
            System.out.println("inside");
            return 7;
        }
    }

    public static void main(String[] args) {
        System.out.println("returned=" + calculate());
    }
}
```

```bash
javac EarlyReturnResourceDemo.java
java EarlyReturnResourceDemo
```

`stdout`:

```text
inside
close called
returned=7
```

Câu lệnh `return 7` chuẩn bị kết quả trước, resource được đóng, sau đó bên gọi nhận `7`. Nếu `close()` ném ngoại lệ trên đường `return` đang chờ, ngoại lệ đóng có thể làm lời gọi kết thúc bằng lỗi thay vì trả về giá trị như dự kiến.

**Phân biệt** việc biến vẫn ở scope sau `try` với thời điểm gọi `close()`: resource được đóng **khi rời khối `try-with-resources`**, không phải khi biến địa phương đi ra khỏi scope hoặc khi garbage collector chạy.

### Null resource trong Java 9

Một biến tài nguyên có kiểu phù hợp nhưng chứa `null` vẫn có thể xuất hiện trong header. Java không gọi `close()` trên một tham chiếu null. Ví dụ:

```java
AutoCloseable resource = null;
try (resource) {
    System.out.println("body runs");
}
```

Đây là **mảnh minh họa** có thể cần xử lý/khai báo checked `Exception` tùy kiểu tĩnh của resource, vì `AutoCloseable.close()` khai báo `throws Exception`. Thân `try` vẫn chạy và không có đối tượng nào để đóng ở cuối. Dù ngôn ngữ cho phép, việc sử dụng null như “không có resource” không nên che giấu một lỗi thiếu tài nguyên bắt buộc.

## 9. Ngoại lệ trong `close()` và *suppressed exceptions*

Resource chỉ được đóng tự động nếu quá trình chạy thực sự đi qua cơ chế đóng của `try-with-resources`. Khi `close()` ném ngoại lệ, Java không đơn giản bỏ qua lỗi đó.

Có ba tình huống chính:

1. Thân `try` hoàn thành bình thường, `close()` ném ngoại lệ: lỗi từ `close()` đi ra (nếu không được bắt).
2. Thân `try` ném ngoại lệ, sau đó `close()` cũng ném: lỗi của **thân `try`** thường được giữ làm lỗi chính; lỗi đóng được thêm vào danh sách **suppressed**.
3. Có nhiều resource: chúng vẫn được đóng lần lượt theo thứ tự ngược; các lỗi đóng tiếp theo có thể trở thành suppressed của lỗi chính theo quy tắc ngôn ngữ.

Điều này khác với cách viết `finally` thủ công nếu một lệnh `close()` trong `finally` ném lỗi mới và vô tình che mất lỗi gốc.

### Ví dụ chạy được: giữ lỗi thân và lỗi đóng

Tạo tệp **`SuppressedCloseDemo.java`**:

```java
public class SuppressedCloseDemo {
    static class ExplodingResource implements AutoCloseable {
        @Override
        public void close() {
            throw new IllegalStateException("close failed");
        }
    }

    public static void main(String[] args) {
        ExplodingResource resource = new ExplodingResource();

        try (resource) {
            throw new IllegalArgumentException("body failed");
        } catch (IllegalArgumentException failure) {
            System.out.println("primary: " + failure.getMessage());
            System.out.println("suppressed count: "
                    + failure.getSuppressed().length);
            System.out.println("suppressed: "
                    + failure.getSuppressed()[0].getMessage());
        }
    }
}
```

```bash
javac SuppressedCloseDemo.java
java SuppressedCloseDemo
```

`stdout`:

```text
primary: body failed
suppressed count: 1
suppressed: close failed
```

`resource` là biến effectively final tạo trước `try`. Lỗi từ phần thân `try` là `IllegalArgumentException`; lỗi từ `close()` trở thành suppressed. Thông tin này có thể được quan sát bằng `getSuppressed()`; khi chẩn đoán lỗi tài nguyên cần xem cả primary, cause và suppressed exceptions.

Nếu `close()` có thể ném **checked exception**, trình biên dịch yêu cầu bắt hoặc khai báo kiểu đó, kể cả khi thân `try` không có thao tác nào ném checked exception. Quy tắc này **không đổi** chỉ vì resource được tham chiếu theo cú pháp Java 9.

### Ví dụ checked exception khi đóng

Tạo tệp **`CheckedCloseDemo.java`**:

```java
import java.io.IOException;

public class CheckedCloseDemo {
    static class Resource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            System.out.println("closing");
            throw new IOException("cannot close");
        }
    }

    public static void main(String[] args) {
        Resource resource = new Resource();

        try (resource) {
            System.out.println("processing");
        } catch (IOException failure) {
            System.out.println("caught: " + failure.getMessage());
        }
    }
}
```

```bash
javac CheckedCloseDemo.java
java CheckedCloseDemo
```

`stdout`:

```text
processing
closing
caught: cannot close
```

`Resource.close()` khai báo `throws IOException`; lời gọi đóng do ngôn ngữ tự phát sinh tại cuối `try` vẫn tham gia kiểm tra checked exception. Không cần gọi `resource.close()` thủ công trong thân `try`.

## 10. Những điều **không** được Java 9 cho phép

Tính năng Java 9 không chấp nhận một biểu thức tùy ý trong danh sách tài nguyên. Một phần tử resource dùng cú pháp tham chiếu mới phải là **variable access** hợp lệ, như tên biến cục bộ hoặc trường phù hợp với quy tắc ngôn ngữ.

Ví dụ **cố ý không biên dịch**:

```java
try (new java.io.StringReader("A")) {
    // Không được viết trực tiếp biểu thức new trong resource header.
}
```

Muốn tạo mới trong header, dùng **resource declaration**:

```java
try (java.io.StringReader reader = new java.io.StringReader("A")) {
    System.out.println(reader.read());
}
```

Cả hai đều là **mảnh minh họa**, đoạn đầu sai cú pháp; đoạn thứ hai hợp lệ nếu đặt trong phương thức/lớp Java phù hợp.

### Kiểu tĩnh phải thực thi `AutoCloseable`

Đoạn này **cố ý không biên dịch**:

```java
Object resource = new java.io.StringReader("A");
try (resource) {
    System.out.println("read");
}
```

Dù đối tượng thực tế là `StringReader`, **kiểu tĩnh** của biến resource là `Object`, không phải subtype của `AutoCloseable`. Tham số resource bị compiler từ chối. Khai báo biến bằng kiểu phù hợp, chẳng hạn `StringReader`, `Reader`, `Closeable` hoặc `AutoCloseable` tùy API bạn cần sử dụng.

### `final` không có nghĩa resource vẫn mở mãi

Có thể viết `final StringReader reader = ...`, nhưng khi dùng `try (reader)`, **đối tượng vẫn sẽ được đóng**. `final` chỉ bảo đảm biến không bị gán lại; nó không ngăn gọi `close()` và không biến trạng thái resource thành immutable.

### Không dùng cùng đối tượng trong hai vị trí nếu không hiểu close semantics

Nếu hai biến khác nhau cùng trỏ đến **một đối tượng AutoCloseable**, một header `try (first; second)` có thể gọi `close()` cho **cùng đối tượng đó hai lần**. Java bảo đảm thực hiện các thao tác đóng theo danh sách resource, không bảo đảm mọi implementation chấp nhận close lặp hoặc không tạo tác dụng phụ.

Hãy quản lý ownership rõ ràng, đặc biệt với resource được bọc hoặc chia sẻ.

## 11. Kiểm tra ranh giới phiên bản Java 8 và Java 9

Tính năng `try (resource)` xuất hiện từ **Java 9**. Khi dùng JDK 17, bạn có thể yêu cầu compiler giả lập mức ngôn ngữ và API mục tiêu bằng tùy chọn `--release`.

Giả sử tệp `ExistingResourceDemo.java` ở mục 4 đã lưu:

```bash
javac --release 9 ExistingResourceDemo.java
java ExistingResourceDemo
```

Chương trình biên dịch và chạy thành công vì Java 9 đã hỗ trợ cú pháp này. Có thể dùng `--release 17` hoặc biên dịch bình thường trên JDK 17.

Thử:

```bash
javac --release 8 ExistingResourceDemo.java
```

Lệnh biên dịch sẽ **báo lỗi** vì cú pháp tham chiếu trực tiếp biến tài nguyên không được hỗ trợ ở nguồn ngôn ngữ Java 8. Thông báo cụ thể phụ thuộc compiler; không xem đây là chương trình build thành công.

Nếu bạn đang bảo trì mã nguồn cần tương thích Java 8, hãy sử dụng dạng khai báo resource trong header:

```java
TrackedResource resource = new TrackedResource("A");
try (TrackedResource managed = resource) {
    managed.use();
}
```

Đây là **mảnh minh họa trong ngữ cảnh có sẵn lớp `TrackedResource`** từ ví dụ mục 4. Dạng alias `managed` dùng được ở Java 7/8 và các phiên bản sau. Nó đóng đúng đối tượng mà `resource` và `managed` cùng trỏ tới, không tạo một resource độc lập mới.

Đừng nhầm **phiên bản của JDK đang chạy** với **mức source language được chọn cho project**. Bạn có thể chạy JDK 17 nhưng build bằng `--release 8`, và cú pháp Java 9 vẫn không hợp lệ trong project đó.

## 12. Resource field: cần thận trọng hơn biến cục bộ

Java 9 cho phép một số dạng truy cập **trường** trong resource specification khi thỏa điều kiện theo ngôn ngữ, chẳng hạn trường được khai báo `final` với kiểu tài nguyên hợp lệ. Tuy nhiên, cần đặc biệt cân nhắc khi một trường là resource dài hạn thuộc lifecycle của đối tượng.

Ví dụ mảnh mã ý tưởng:

```java
class ReaderOwner {
    private final java.io.StringReader reader =
            new java.io.StringReader("data");

    void readOnce() throws java.io.IOException {
        try (this.reader) {
            System.out.println(reader.read());
        }
    }
}
```

Đây là **định nghĩa lớp để minh họa**, không có `main`. `this.reader` là truy cập trường `final`, khác với biến cục bộ effectively final. Sau lần `readOnce()` đầu tiên, trường `reader` vẫn trỏ đến đối tượng đã đóng. Gọi `readOnce()` lần nữa có thể thất bại khi cố đọc vì resource không còn mở.

**Thiết kế nên cân nhắc:** nếu một trường sở hữu tài nguyên lâu dài, thường để phương thức quản lý vòng đời như `close()` của lớp chủ sở hữu đóng nó ở thời điểm phù hợp, thay vì đóng nó âm thầm ở một lời gọi phương thức ngắn. Hãy phân biệt quyền truy cập ngôn ngữ `final` với quyền sở hữu/vòng đời tài nguyên.

## 13. Những lỗi hiểu nhầm phổ biến

**“Chỉ biến viết từ khóa `final` mới dùng được trong `try (resource)`.”** Sai. Java 9+ cho phép biến cục bộ *effectively final*, nghĩa là không bị gán lại dù không ghi từ khóa.

**“Có thể gán lại resource sau `try` vì khối đã kết thúc.”** Sai về tính effectively final: compiler xét các phép gán của biến trong phạm vi liên quan, không chỉ trước câu lệnh `try`.

**“Nếu gọi `reader.readLine()`, reader không còn effectively final.”** Sai. Gọi phương thức thay đổi **trạng thái đối tượng**, không phải gán lại **biến tham chiếu**.

**“Dùng `try (reader)` tạo ra bản sao của reader.”** Sai. Nó quản lý **chính đối tượng** đang được tham chiếu, không tự clone resource.

**“Biến vẫn nhìn thấy sau `try` nghĩa là resource còn mở.”** Sai. Đối tượng có thể đã đóng; scope của biến và lifetime mở của tài nguyên là hai chuyện khác nhau.

**“Đã khai báo resource trước `try` thì mọi lỗi khi khởi tạo resource đều được try-with-resources quản lý.”** Sai. Nếu lỗi xuất hiện **trước khi vào `try`**, cơ chế tự đóng của `try` đó chưa áp dụng. Đây là lý do khai báo resource trực tiếp trong header vẫn rất hữu ích.

**“Đóng các resource theo thứ tự từ trái sang phải.”** Sai. Java đóng theo thứ tự ngược từ phải sang trái trong header.

**“Có `try-with-resources` thì lỗi `close()` bị bỏ qua.”** Sai. Lỗi có thể truyền ra hoặc được lưu là *suppressed exception* nếu đã có lỗi chính.

**“Biến có kiểu `Object` được chấp nhận miễn runtime object có `close()`.”** Sai. Kiểm tra kiểu resource diễn ra khi biên dịch; kiểu tĩnh phải phù hợp `AutoCloseable`.

**“Java 9 cho phép `try (new MyResource())`.”** Sai. Một biểu thức tạo đối tượng trần không phải variable access resource hợp lệ; hãy khai báo resource mới: `try (MyResource r = new MyResource())`.

**“Dùng một resource trong hai slot của header sẽ tự nhận diện và chỉ đóng một lần.”** Sai. Không được dựa vào cơ chế khử trùng lặp khi đóng. Hãy tránh quản lý hai alias đến cùng đối tượng mà không hiểu hợp đồng `close()`.

**“`StringReader` đóng xong vẫn đọc được vì dữ liệu nằm trong RAM.”** Sai. Hợp đồng của reader vẫn điều khiển trạng thái đóng; việc dữ liệu nằm trong bộ nhớ không có nghĩa bạn được đọc vô hạn sau `close()`.

## 14. Bài tập tự kiểm tra

1. **`BufferedReader r = ...; try (r) { ... }` hợp lệ ở Java 17 khi nào?** Khi `r` được gán chắc chắn trước `try`, có kiểu phù hợp với `AutoCloseable` và là final hoặc effectively final.
2. **Không có từ khóa `final` nhưng không gán lại, compiler có chấp nhận không?** Có, đó là effectively final.
3. **Viết `r = anotherReader;` ở phía sau `try (r)` thì sao?** Biến không còn effectively final; compiler có thể từ chối resource specification.
4. **Có thể thay đổi trạng thái resource bên trong `try` không?** Có, nếu API cho phép. Điều kiện effectively final chỉ ràng buộc phép gán **biến**.
5. **Sau `try (r)`, biến `r` còn trong scope không?** Có nếu nó được khai báo ở scope bên ngoài; nhưng đối tượng đã được đóng.
6. **`try (a; b)` đóng theo thứ tự nào?** `b` trước, rồi `a`.
7. **`try (new FileInputStream(path))` là cú pháp trực tiếp hợp lệ từ Java 9?** Không; cần resource declaration hoặc biến có sẵn hợp lệ.
8. **Nếu `close()` ném trong khi thân `try` đã ném lỗi, điều gì thường xảy ra?** Lỗi thân là primary, lỗi đóng là suppressed; kiểm tra `getSuppressed()`.
9. **Nếu tạo resource A thành công, rồi tạo B thất bại trước `try (a; b)`, A có tự được đóng bởi `try` này không?** Không, vì chưa vào phạm vi tự quản lý của `try`.
10. **Với Java 8, muốn dùng resource khai báo bên ngoài, viết thế nào?** Tạo biến resource mới ngay trong header, ví dụ `try (Reader managed = reader)`.
11. **Dùng biến `final` có khiến resource immutable không?** Không, chỉ làm tham chiếu không thể gán lại.
12. **Nếu `AutoCloseable.close()` có checked exception, cú pháp Java 9 bỏ nghĩa vụ `catch/throws` không?** Không. Bên gọi vẫn cần xử lý hoặc khai báo checked exception phù hợp.
13. **Có nên đóng một connection do framework quản lý bằng `try (connection)` không?** Chỉ khi hợp đồng quyền sở hữu cho phép; nếu framework quản lý vòng đời thì tự đóng có thể sai.

## 15. Tài liệu tham khảo chuẩn

- [Java Language Specification 17 — §14.20.3, try-with-resources](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.3): cú pháp và ngữ nghĩa tài nguyên, tự đóng, thứ tự đóng và suppressed exceptions.
- [Java Language Specification 17 — §4.12.4, final Variables](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.12.4): biến final và effectively final.
- [Java SE 17 API — AutoCloseable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/AutoCloseable.html): hợp đồng `close()` và lưu ý về tính idempotent.
- [Java SE 17 API — Closeable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Closeable.html): biến thể đóng resource I/O.
- [Java SE 17 API — Throwable.getSuppressed](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html#getSuppressed()): quan sát lỗi bị suppressed.
- [JEP 213 — Milling Project Coin](https://openjdk.org/jeps/213): cải tiến cú pháp try-with-resources sử dụng biến tài nguyên đã có từ Java 9.

**Ghi nhớ:** Từ Java 9, có thể viết `try (resource)` cho biến tài nguyên **đã tồn tại**, nếu biến là `final` hoặc *effectively final* và có kiểu hợp lệ với `AutoCloseable`. Java tự đóng **đối tượng được tham chiếu** khi rời khối; biến vẫn có thể còn trong scope nhưng tài nguyên không còn mở. Tính ngắn gọn về cú pháp không thay thế việc quản lý đúng quyền sở hữu, thời điểm tạo tài nguyên và ngoại lệ từ `close()`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="try-with-resources-005">Thứ tự tạo tài nguyên từ trái sang phải</a>

<details>
<summary>Xem chi tiết</summary>

# Thứ tự tạo tài nguyên từ trái sang phải trong try-with-resources

> Java Core – Exception · Nhóm: Try-with-resources · Java 17+.

## 1. Quy tắc và phạm vi bài học

Khi một câu lệnh `try-with-resources` khai báo nhiều tài nguyên, Java **đánh giá initializer từ trái sang phải**, rồi mới chạy thân `try`. Khi rời thân, Java **đóng những tài nguyên đã khởi tạo thành công theo thứ tự từ phải sang trái**. Quy tắc này vẫn áp dụng khi thân try ném ngoại lệ hoặc khi một initializer phía sau bị lỗi.

~~~java
try (Resource a = createA();
     Resource b = createB();
     Resource c = createC()) {
    useResources();
}
~~~

Đây là mã minh họa, cần định nghĩa kiểu `Resource` triển khai `AutoCloseable` và các hàm tương ứng. Nếu không có lỗi, trình tự là:

~~~text
createA() → createB() → createC() → useResources()
                                → c.close() → b.close() → a.close()
~~~

Nếu `createB()` ném lỗi: A được đóng, C **không được khởi tạo**, thân try **không chạy**, và catch bên ngoài nhận exception sau bước cleanup. Đừng nhầm “initializer B đã bắt đầu” với “B đã được khởi tạo thành công và được đăng ký để đóng”.

## 2. Cách đọc resource specification

~~~java
try (TypeA a = openA();
     TypeB b = openB()) {
    process(a, b);
} catch (IOException ex) {
    handle(ex);
}
~~~

Đoạn này chỉ biểu diễn cấu trúc, chưa độc lập. Các điều kiện chính:

- Biến tài nguyên phải có kiểu tương thích `AutoCloseable`. Ví dụ thư viện chuẩn: `InputStream`, `BufferedReader`, `java.sql.Connection`.
- Dấu `;` phân cách resource declarations. Dấu cuối trước ngoặc đóng là tùy chọn.
- Initializer A phải hoàn thành **bình thường** trước khi initializer B được đánh giá.
- Nếu initializer B thất bại, tài nguyên A đã khởi tạo thành công vẫn được đóng tự động; B không được đóng nếu chưa được tạo/nhận thành công.
- Resource declarations là các biến `final`, không được gán lại trong body.
- Resource khác null được đóng **ngược với thứ tự trong header**; resource null được bỏ qua ở bước đóng tự động.
- Khi một lần close ném lỗi, Java vẫn cố đóng những resource còn lại theo quy tắc ngôn ngữ.
- Cơ chế này có từ **Java 7**. Viết `try (existingVariable)` với biến final/effectively final đã khai báo trước được hỗ trợ từ **Java 9**.

Một initializer không nhất thiết chứa `new`; nó có thể là lời gọi factory, mở file hoặc kết hợp nhiều phép toán. Điều Java bảo đảm là **thứ tự đánh giá các biểu thức**. Các side effect đã xảy ra trong factory không tự rollback nếu factory cuối cùng ném ngoại lệ.

## 3. Ví dụ Java 17 hoàn chỉnh và có thể chạy

Tạo file **`ResourceOrderDemo.java`**. Lớp `TracedResource` thực thi `AutoCloseable`, ghi lại mỗi lần mở/đóng và tạo ngoại lệ giả lập theo cấu hình.

~~~java
import java.io.IOException;

public class ResourceOrderDemo {
    private static final class TracedResource implements AutoCloseable {
        private final String name;
        private final boolean failClose;

        TracedResource(String name, boolean failOpen, boolean failClose)
                throws IOException {
            System.out.println("open " + name);
            if (failOpen) {
                throw new IOException("open " + name + " failed");
            }
            this.name = name;
            this.failClose = failClose;
        }

        @Override
        public void close() throws IOException {
            System.out.println("close " + name);
            if (failClose) {
                throw new IOException("close " + name + " failed");
            }
        }
    }

    private static TracedResource open(
            String name, boolean failOpen, boolean failClose)
            throws IOException {
        return new TracedResource(name, failOpen, failClose);
    }

    private static void report(IOException ex) {
        System.out.println("caught=" + ex.getMessage());
        for (Throwable suppressed : ex.getSuppressed()) {
            System.out.println("suppressed=" + suppressed.getMessage());
        }
    }

    private static void normal() {
        System.out.println("== normal ==");
        try (TracedResource a = open("A", false, false);
             TracedResource b = open("B", false, false);
             TracedResource c = open("C", false, false)) {
            System.out.println("body");
        } catch (IOException ex) {
            report(ex);
        }
    }

    private static void failedSecond() {
        System.out.println("== failed second ==");
        try (TracedResource a = open("A", false, false);
             TracedResource b = open("B", true, false);
             TracedResource c = open("C", false, false)) {
            System.out.println("body");
        } catch (IOException ex) {
            report(ex);
        }
    }

    private static void failedThirdAndClose() {
        System.out.println("== failed third + close ==");
        try (TracedResource a = open("A", false, true);
             TracedResource b = open("B", false, true);
             TracedResource c = open("C", true, false)) {
            System.out.println("body");
        } catch (IOException ex) {
            report(ex);
        }
    }

    private static void failedBodyAndClose() {
        System.out.println("== failed body + close ==");
        try (TracedResource a = open("A", false, true);
             TracedResource b = open("B", false, true)) {
            System.out.println("body");
            throw new IOException("body failed");
        } catch (IOException ex) {
            report(ex);
        }
    }

    private static void failedCloseOnly() {
        System.out.println("== failed close only ==");
        try (TracedResource a = open("A", false, true);
             TracedResource b = open("B", false, true)) {
            System.out.println("body");
        } catch (IOException ex) {
            report(ex);
        }
    }

    private static void nullResource() {
        System.out.println("== null resource ==");
        try (TracedResource a = null;
             TracedResource b = open("B", false, false)) {
            System.out.println("body");
        } catch (IOException ex) {
            report(ex);
        }
    }

    private static void referenceJava9() {
        System.out.println("== variable reference (Java 9+) ==");
        try {
            final TracedResource resource = open("X", false, false);
            try (resource) {
                System.out.println("body");
            }
        } catch (IOException ex) {
            report(ex);
        }
    }

    public static void main(String[] args) {
        normal();
        failedSecond();
        failedThirdAndClose();
        failedBodyAndClose();
        failedCloseOnly();
        nullResource();
        referenceJava9();
    }
}
~~~

Biên dịch/chạy từ thư mục chứa file:

~~~bash
javac --release 17 -encoding UTF-8 ResourceOrderDemo.java
java ResourceOrderDemo
~~~

**Stdout chính xác** đã được kiểm chứng bằng chương trình Java biên dịch thực tế:

~~~text
== normal ==
open A
open B
open C
body
close C
close B
close A
== failed second ==
open A
open B
close A
caught=open B failed
== failed third + close ==
open A
open B
open C
close B
close A
caught=open C failed
suppressed=close B failed
suppressed=close A failed
== failed body + close ==
open A
open B
body
close B
close A
caught=body failed
suppressed=close B failed
suppressed=close A failed
== failed close only ==
open A
open B
body
close B
close A
caught=close B failed
suppressed=close A failed
== null resource ==
open B
body
close B
== variable reference (Java 9+) ==
open X
body
close X
~~~

Các exception mẫu có message do chương trình tạo nên output không phụ thuộc ngôn ngữ hệ điều hành. Toàn bộ chương trình biên dịch được với `--release 17`, không cần thư viện ngoài.

## 4. Phân tích từng tình huống

### 4.1. Thành công với ba tài nguyên

Nhánh `normal()` in lần lượt `open A`, `open B`, `open C`; lúc ấy body mới in `body`. Sau body, Java gọi `c.close()`, `b.close()`, `a.close()`. Nếu ba tài nguyên có quan hệ phụ thuộc như A là nguồn, B bọc A và C bọc B, thứ tự đóng đảo ngược giúp phần phụ thuộc được giải phóng trước phần nền tảng.

### 4.2. B lỗi khi khởi tạo

Trong `failedSecond()`, A thành công; constructor B in `open B` rồi ném `IOException("open B failed")`. Do vậy:

1. C **chưa được khởi tạo**, vì initializer của B chưa hoàn tất bình thường.
2. Body **không được vào**, vì cả danh sách tài nguyên chưa thành công.
3. B chưa được nhận là resource đã khởi tạo thành công nên try-with-resources **không gọi B.close()**.
4. Java tự gọi `a.close()`, in `close A`.
5. Sau khi cleanup A, catch in `caught=open B failed`.

Đây là bằng chứng rằng try-with-resources quản lý các tài nguyên đã khởi tạo thành công **ngay cả khi thân try chưa bao giờ bắt đầu**.

### 4.3. C mở lỗi, B và A lỗi đóng

Trong `failedThirdAndClose()`, A/B mở thành công, C bắt đầu rồi thất bại. B.close() và A.close() cùng ném lỗi, nhưng Java vẫn cố đóng theo thứ tự **B rồi A**. Primary exception là lỗi mở C. Hai lỗi đóng được thêm vào `getSuppressed()` của primary:

~~~text
Primary:    open C failed
Suppressed: close B failed
Suppressed: close A failed
~~~

Không có `close C` vì C chưa khởi tạo thành công. Catch được thực thi **sau bước cleanup**.

### 4.4. Body ném lỗi

Trong `failedBodyAndClose()`, A và B đều mở thành công, body in `body` rồi ném `IOException("body failed")`. B/A được đóng theo thứ tự ngược; lỗi đóng trở thành suppressed của **lỗi body**. Khác trường hợp 4.3, body đã thực sự bắt đầu, nhưng quy tắc đóng giống nhau.

### 4.5. Chỉ có lỗi ở close

Trong `failedCloseOnly()`, body thành công. B.close() chạy trước và ném lỗi, A.close() tiếp tục chạy và cũng ném lỗi. **Lỗi B là primary** vì là lỗi đóng đầu tiên; lỗi A là suppressed. Thành công trong body không bảo đảm cả try-with-resources thành công, bởi close cũng có thể thất bại.

### 4.6. Resource nhận null

Trong `nullResource()`, A có giá trị null, B được mở bình thường. Java **không gọi close trên null**; B vẫn được đóng. Đây là quy tắc của cơ chế tự động, không có nghĩa việc dereference A trong body là an toàn: thao tác gọi method qua A có thể ném `NullPointerException`.

### 4.7. Resource reference từ Java 9

Trong `referenceJava9()`, biến `resource` được tạo trước header, sau đó mới dùng `try (resource)`. Java 9 cho phép biến final hoặc effectively final đã tồn tại làm tài nguyên trong header. Resource được đóng sau body, nhưng **bước tạo X ở trước try**; nếu việc mở X thất bại, header `try (resource)` chưa được bắt đầu.

## 5. Ranh giới cleanup khi initializer thất bại

Cần phân biệt **bắt đầu khởi tạo** và **được đăng ký thành công**. Một factory có thể mở socket hoặc tệp trung gian, sau đó ném exception trước khi trả về resource cuối cùng. Try-with-resources ở caller không tự biết các tài nguyên trung gian đó; **factory phải tự cleanup trước khi thoát vì lỗi**.

Ví dụ minh họa chưa độc lập:

~~~java
static Wrapper create() throws IOException {
    InputStream stream = Files.newInputStream(path);
    return buildWrapper(stream);
}
~~~

Nếu `buildWrapper(stream)` thất bại, stream đã được mở nhưng caller chưa nhận một Wrapper. Mã trên cần được sửa để bảo vệ stream trong trường hợp tạo wrapper lỗi; tên `Wrapper`, `path` và `buildWrapper` cũng chưa được định nghĩa ở ví dụ này.

Ngược lại, nếu A được khai báo như resource đầu tiên của try rồi B được tạo từ A và B thất bại, A đã được quản lý và sẽ được đóng. Thiết kế **ownership** và thứ tự khai báo làm khác biệt rất lớn.

## 6. Suppressed exception và ngoại lệ chính

Try-with-resources bảo toàn ngoại lệ quan trọng hơn bằng cơ chế suppressed:

| Trường hợp | Primary | Suppressed |
| --- | --- | --- |
| Initializer C lỗi; đóng A/B bình thường | Lỗi C | Không có |
| Initializer C lỗi; B/A đóng lỗi | Lỗi C | Lỗi B rồi A |
| Body lỗi; B/A đóng lỗi | Lỗi body | Lỗi B rồi A |
| Body bình thường; B/A đóng lỗi | Lỗi B.close() | Lỗi A.close() |
| Mọi thao tác bình thường | Không có | Không có |

Để chẩn đoán trong một catch thích hợp:

~~~java
catch (IOException ex) {
    System.out.println("primary=" + ex.getMessage());
    for (Throwable child : ex.getSuppressed()) {
        System.out.println("suppressed=" + child.getMessage());
    }
}
~~~

Đây là đoạn minh họa trong cấu trúc try/catch phù hợp. `getSuppressed()` không giống `getCause()`: cause mô tả chuỗi nguyên nhân của exception được bọc; suppressed lưu các ngoại lệ thứ cấp phát sinh trong cleanup.

Một chuỗi close thủ công như:

~~~java
b.close();
a.close();
~~~

có thể dừng ngay tại B nếu B.close() ném exception. Try-with-resources có ngữ nghĩa đặc biệt để **tiếp tục cố đóng tài nguyên còn lại** và bảo toàn thông tin lỗi.

## 7. Catch, finally và return

Với cấu trúc:

~~~java
try (Resource a = openA();
     Resource b = openB()) {
    process();
} catch (IOException ex) {
    handle(ex);
} finally {
    afterAll();
}
~~~

Đây là cú pháp minh họa, không độc lập. Những tài nguyên đã được mở sẽ được đóng trước khi catch tương ứng chạy; finally bên ngoài chạy theo quy tắc của nó sau đó.

Khi body chứa `return expression;`, biểu thức return được đánh giá trước khi rời try, nhưng tài nguyên vẫn được đóng trước khi caller nhận kết quả. Một lỗi đóng có thể khiến phương thức ném exception thay vì hoàn thành return. Ví dụ:

~~~java
static String firstLine(Path path) throws IOException {
    try (BufferedReader reader = Files.newBufferedReader(path)) {
        return reader.readLine();
    }
}
~~~

Đây là một phương thức minh họa cần được đặt trong class và import các API I/O liên quan.

## 8. Thực hành và lỗi phổ biến

- **Nhầm hai chiều:** initializer trái→phải, close phải→trái.
- **Tưởng body chạy sau A dù B lỗi:** body chỉ chạy khi tất cả initializer thành công.
- **Tưởng B được close khi constructor B bắt đầu rồi ném:** B chưa được bàn giao thành công cho header, nên caller chưa quản lý nó.
- **Quên tài nguyên bên trái khi initializer bên phải lỗi:** Java đóng chúng tự động.
- **Tưởng close của B ném thì A không được đóng:** Java vẫn cố đóng A.
- **Chỉ ghi log primary:** có thể bỏ sót lỗi đóng trong suppressed.
- **Nhầm suppressed với cause:** hai quan hệ exception khác nhau.
- **Đăng ký cả wrapper và resource nền mà không xem Javadoc:** wrapper có thể tự đóng resource bên dưới, gây đóng trùng.
- **Gán lại biến resource:** biến trong resource declaration là final.
- **Dùng cú pháp resource reference từ Java 9 trong dự án Java 8:** không tương thích nguồn.
- **Cho rằng null resource luôn ném NPE trong bước close:** null bị bỏ qua ở bước close tự động.
- **Tưởng try-with-resources rollback mọi side effect:** nó quản lý close, không thay thế transaction.
- **Không kiểm tra tình huống initializer thất bại một phần:** factory cần tự giải phóng tài nguyên nội bộ.

Cách suy luận nhanh: đánh số A, B, C từ trái sang phải; xác định initializer đầu tiên có thể ném; giữ danh sách resource đã hoàn thành trước đó; đảo danh sách này để xác định thứ tự đóng; cuối cùng xác định primary/suppressed và handler nhận lỗi.

## 9. Tự đánh giá

**Câu 1.** Với A, B, C, nếu B mở lỗi thì C có được mở không?

*Không.* Java dừng đánh giá những initializer bên phải. A đã mở được đóng và body không chạy.

**Câu 2.** Nếu A, B, C thành công, thứ tự đóng thế nào?

*C → B → A.*

**Câu 3.** Nếu C mở lỗi rồi B.close() và A.close() cùng lỗi?

*Lỗi khởi tạo C là primary; lỗi đóng B rồi A là suppressed.*

**Câu 4.** Nếu body bình thường nhưng B.close() và A.close() cùng lỗi?

*Lỗi B.close() là primary vì B đóng trước; lỗi A.close() là suppressed.*

**Câu 5.** Nếu A=null và B hợp lệ?

*Java bỏ qua A.close(); B vẫn được mở và đóng theo quy tắc.*

**Câu 6.** Vì sao constructor B có thể gây rò rỉ khi thất bại dù caller dùng try-with-resources?

*B có thể đã mở các đối tượng trung gian trước khi trả kết quả cho caller; constructor/factory phải tự cleanup những đối tượng ấy.*

**Câu 7.** §try (existingResource)§ cần Java phiên bản nào?

*Cú pháp resource reference trực tiếp có từ Java 9; biến phải final/effectively final.*

## 10. Tài liệu tham khảo

- [JLS Java SE 17, mục 14.20.3 – try-with-resources](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.3) – resource specification và quản lý tài nguyên.
- [JLS Java SE 17, mục 14.20.3.1 – Basic try-with-resources](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.3.1) – thứ tự đóng và cơ chế mở rộng tương đương.
- [JLS Java SE 17, mục 14.20.3.2 – Extended try-with-resources](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.3.2) – catch/finally bên ngoài.
- [Java SE 17 API – AutoCloseable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/AutoCloseable.html) – hợp đồng close.
- [Java SE 17 API – Throwable.getSuppressed](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html#getSuppressed()) – ngoại lệ bị suppressed.
- [Oracle Java Tutorials – try-with-resources](https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html) – ví dụ cú pháp từ Java 7.

**Kết luận:** Java **đánh giá initializer từ trái sang phải**, chỉ chạy body khi mọi initializer thành công, rồi **đóng các resource khác null đã khởi tạo thành công từ phải sang trái**. Lỗi ở resource phía sau không ngăn đóng các resource phía trước; lỗi cleanup bổ sung được lưu trong §getSuppressed()§.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="try-with-resources-006">Thứ tự đóng tài nguyên ngược với thứ tự tạo</a>

<details>
<summary>Xem chi tiết</summary>

# Thứ tự đóng tài nguyên ngược với thứ tự tạo trong Java try-with-resources

Khi sử dụng **try-with-resources**, Java tự gọi `close()` cho những tài nguyên đã được khởi tạo thành công khi rời vùng `try`. Nếu khai báo nhiều tài nguyên trong cùng một phần đầu `try (...)`, Java **khởi tạo từ trái sang phải nhưng đóng theo chiều ngược lại: từ phải sang trái**. Đây là quy tắc quan trọng để quản lý các lớp tài nguyên phụ thuộc lẫn nhau và để dự đoán kết quả khi có ngoại lệ.

~~~java
try (Resource a = openA();
     Resource b = openB();
     Resource c = openC()) {
    use(a, b, c);
}
~~~

Đoạn trên là **mã giả theo cú pháp Java**, trong đó `Resource` cần là lớp triển khai `AutoCloseable` và các hàm `openA`, `openB`, `openC`, `use` phải tồn tại. Khi mọi thao tác đều thành công:

~~~text
Khởi tạo: A -> B -> C
Sử dụng:  thân try
Đóng:     C -> B -> A
~~~

Không phải JVM “đóng tất cả cùng lúc” hay theo thứ tự ngẫu nhiên; đối với các tài nguyên khai báo theo thứ tự trong cùng một try-with-resources, thứ tự đóng được Java xác định rõ. Quy tắc này vẫn áp dụng nếu thân `try` chạy xong bình thường, `return` hoặc ném ngoại lệ — miễn là luồng Java còn thực hiện được việc rời cấu trúc `try`.

## 1. Vì sao cần đóng theo thứ tự ngược?

Trong hệ thống thực, tài nguyên thường được **xây dựng theo tầng**: tài nguyên sau sử dụng hoặc bao bọc tài nguyên trước.

Ví dụ trực quan:

~~~text
Mở luồng dữ liệu gốc InputStream
         |
Tạo InputStreamReader đọc từ luồng gốc
         |
Tạo BufferedReader đọc từ InputStreamReader
         |
      Sử dụng
         |
Đóng BufferedReader
         |
Đóng lớp reader bên dưới / luồng gốc theo cơ chế đóng liên quan
~~~

Khi quản lý một chuỗi đối tượng, đóng tầng ngoài trước có thể bảo đảm nó thực hiện nốt thao tác cuối cùng và không còn sử dụng tầng dưới sau khi tầng dưới đã đóng.

Không được suy ra rằng **mọi đối tượng độc lập có quan hệ phụ thuộc** chỉ vì chúng được khai báo cạnh nhau. Thứ tự ngược là **quy tắc của ngôn ngữ**, còn lý do tạo tài nguyên theo thứ tự nào phụ thuộc thiết kế của ứng dụng.

Đóng theo chiều ngược thường được gọi là **LIFO** (*last in, first out*): đối tượng khởi tạo sau cùng được đóng trước tiên. Với ba tài nguyên `A, B, C` trong cùng danh sách, thứ tự là:

| Giai đoạn | Thứ tự |
| --- | --- |
| Đánh giá và khởi tạo tài nguyên | `A`, rồi `B`, rồi `C` |
| Chạy phần thân `try` | Khi tất cả tài nguyên cần thiết đã được khởi tạo thành công |
| Gọi `close()` | `C`, rồi `B`, rồi `A` |

Không nên nhầm quy tắc này với thứ tự hủy đối tượng của garbage collector. Try-with-resources gọi `close()` theo luồng chương trình; nó **không** đợi garbage collector phát hiện đối tượng không còn được tham chiếu.

## 2. Điều kiện để một đối tượng dùng được trong try-with-resources

Tài nguyên phải thuộc kiểu có thể chuyển thành `java.lang.AutoCloseable`. Giao diện cốt lõi có dạng:

~~~java
public interface AutoCloseable {
    void close() throws Exception;
}
~~~

Chữ ký trên mô tả API ở mức cơ bản. Một lớp triển khai được phép **thu hẹp hoặc bỏ** ngoại lệ checked trong `close()`:

~~~java
static final class SimpleResource implements AutoCloseable {
    private final String name;

    SimpleResource(String name) {
        this.name = name;
        System.out.println("open " + name);
    }

    @Override
    public void close() {
        System.out.println("close " + name);
    }
}
~~~

Đây là khai báo lớp lồng có thể đặt trong một lớp Java. `SimpleResource.close()` không khai báo checked exception nên mã sử dụng nó không phải xử lý `Exception` chỉ vì định nghĩa chung của `AutoCloseable` có `throws Exception`.

Cú pháp một tài nguyên:

~~~java
try (SimpleResource r = new SimpleResource("A")) {
    System.out.println("body");
}
~~~

Dự đoán stdout:

~~~text
open A
body
close A
~~~

Khi có nhiều tài nguyên, viết các khai báo trong cặp ngoặc sau `try`, ngăn cách bằng dấu chấm phẩy:

~~~java
try (SimpleResource a = new SimpleResource("A");
     SimpleResource b = new SimpleResource("B")) {
    System.out.println("body");
}
~~~

Kết quả:

~~~text
open A
open B
body
close B
close A
~~~

**Điểm phân biệt:** `AutoCloseable.close()` có thể ném `Exception`, còn `java.io.Closeable.close()` khai báo `IOException` và thường dùng cho các API I/O. Các lớp cụ thể có thể có chữ ký hẹp hơn. Tác động tới `throws` của phương thức phụ thuộc kiểu tĩnh của tài nguyên và các checked exception có thể thoát khỏi `close()`.

## 3. Java đóng lúc nào?

Việc đóng tài nguyên xảy ra khi phần thân try-with-resources **hoàn tất theo bất kỳ cách hợp lệ nào khiến cấu trúc cần được rời**:

- Phần thân chạy hết câu lệnh bình thường.
- Phần thân chuẩn bị `return` một giá trị.
- Phần thân ném ngoại lệ, dù sau đó có `catch` xử lý.
- `break` hoặc `continue` khiến luồng điều khiển rời khỏi phạm vi try-with-resources tương ứng.

Nếu thân `try` đang chờ vô hạn hoặc vòng lặp không có đường thoát, không có cơ chế tự động “hẹn giờ đóng”. Tương tự, tiến trình bị kết thúc cưỡng bức không có bảo đảm sẽ chạy `close()`. Những điều này không làm thay đổi thứ tự đóng **nếu** Java có cơ hội thực hiện việc đóng.

### `return` vẫn đóng trước khi caller nhận giá trị

~~~java
static int returnAfterClosing() {
    try (SimpleResource a = new SimpleResource("A");
         SimpleResource b = new SimpleResource("B")) {
        System.out.println("prepare return");
        return 7;
    }
}
~~~

Nếu caller chạy `System.out.println("value=" + returnAfterClosing());`:

~~~text
open A
open B
prepare return
close B
close A
value=7
~~~

`return` định ra giá trị trước, còn cả hai tài nguyên được đóng trước khi phương thức thực sự hoàn tất và caller nhận `7`. Nếu một `close()` phát sinh ngoại lệ, quá trình trả kết quả có thể bị thay thế bởi việc ném ngoại lệ — sẽ giải thích ở phần lỗi khi đóng.

## 4. Một chương trình Java 17+ đầy đủ để quan sát thứ tự

Chương trình dưới đây chạy nhiều trường hợp với những tài nguyên tự định nghĩa để đầu ra rõ ràng. Mỗi tài nguyên in một dòng khi mở và một dòng khi đóng; một số tài nguyên được cấu hình để **ném `IOException` khi đóng**.

Tạo tệp **`ResourceCloseOrderDemo.java`**:

~~~java
import java.io.IOException;

public class ResourceCloseOrderDemo {

    static final class Tracked implements AutoCloseable {
        private final String name;
        private final boolean failOnClose;

        Tracked(String name, boolean failOnClose) {
            this.name = name;
            this.failOnClose = failOnClose;
            System.out.println("open " + name);
        }

        @Override
        public void close() throws IOException {
            System.out.println("close " + name);
            if (failOnClose) {
                throw new IOException("close-" + name);
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("== normal ==");
        normal();

        System.out.println("== body failure ==");
        bodyFailure();

        System.out.println("== close failure ==");
        closeFailure();

        System.out.println("== initialization failure ==");
        initializationFailure();

        System.out.println("== return ==");
        System.out.println("result=" + returnEarly());
    }

    static void normal() {
        try (Tracked a = new Tracked("A", false);
             Tracked b = new Tracked("B", false);
             Tracked c = new Tracked("C", false)) {
            System.out.println("body ok");
        } catch (IOException e) {
            System.out.println("unexpected: " + e.getMessage());
        }
    }

    static void bodyFailure() {
        try (Tracked a = new Tracked("A", true);
             Tracked b = new Tracked("B", true)) {
            System.out.println("body throws");
            throw new IllegalArgumentException("work");
        } catch (IllegalArgumentException e) {
            System.out.println("primary=" + e.getMessage());
            printSuppressed(e);
        } catch (IOException e) {
            System.out.println("unexpected I/O: " + e.getMessage());
        }
    }

    static void closeFailure() {
        try (Tracked a = new Tracked("A", true);
             Tracked b = new Tracked("B", true)) {
            System.out.println("body ok");
        } catch (IOException e) {
            System.out.println("primary=" + e.getMessage());
            printSuppressed(e);
        }
    }

    static void initializationFailure() {
        try (Tracked a = new Tracked("A", true);
             Tracked b = new Tracked("B", true);
             Tracked c = failToInitializeC()) {
            System.out.println("body not reached");
        } catch (IOException e) {
            System.out.println("primary=" + e.getMessage());
            printSuppressed(e);
        }
    }

    static Tracked failToInitializeC() throws IOException {
        System.out.println("open C failed");
        throw new IOException("init-C");
    }

    static int returnEarly() throws IOException {
        try (Tracked a = new Tracked("A", false);
             Tracked b = new Tracked("B", false)) {
            System.out.println("return prepared");
            return 7;
        }
    }

    static void printSuppressed(Throwable e) {
        for (Throwable s : e.getSuppressed()) {
            System.out.println("suppressed=" + s.getMessage());
        }
    }
}
~~~

Biên dịch và chạy (Java 17 trở lên):

~~~bash
javac ResourceCloseOrderDemo.java
java ResourceCloseOrderDemo
~~~

**Stdout chính xác:**

~~~text
== normal ==
open A
open B
open C
body ok
close C
close B
close A
== body failure ==
open A
open B
body throws
close B
close A
primary=work
suppressed=close-B
suppressed=close-A
== close failure ==
open A
open B
body ok
close B
close A
primary=close-B
suppressed=close-A
== initialization failure ==
open A
open B
open C failed
close B
close A
primary=init-C
suppressed=close-B
suppressed=close-A
== return ==
open A
open B
return prepared
close B
close A
result=7
~~~

### Kiểm chứng trường hợp bình thường

Phần `normal()` khởi tạo ba đối tượng `A`, `B`, `C`. Sau `body ok`, Java lần lượt gọi `C.close()`, `B.close()` và `A.close()`. Không có ngoại lệ nên nhánh `catch` không chạy.

### Kiểm chứng trường hợp thân `try` ném ngoại lệ

Phần `bodyFailure()` có ngoại lệ chính `IllegalArgumentException("work")`. Dù thân `try` đã ném lỗi, Java vẫn thử đóng `B` rồi `A`. Hai lệnh `close()` cũng ném `IOException`, nhưng **chúng không thay thế ngoại lệ `work` đang là lỗi chính**: lỗi đóng được thêm vào danh sách **suppressed exceptions** của ngoại lệ chính.

Do `B` đóng trước, `close-B` xuất hiện trước `close-A` trong `getSuppressed()`. Tên loại ngoại lệ chính vẫn là `IllegalArgumentException` và thông điệp chính vẫn là `work`.

### Kiểm chứng trường hợp chỉ `close()` bị lỗi

Phần `closeFailure()` có thân `try` hoàn tất bình thường. Khi đóng `B`, `IOException("close-B")` được ném; Java tiếp tục đóng `A`. Lỗi `close-A` phát sinh sau, nên được lưu là *suppressed* của ngoại lệ `close-B`. Sau khi quá trình đóng kết thúc, `catch (IOException e)` nhận lỗi chính `close-B`.

Cần phân biệt: ở tình huống **thân `try` không lỗi**, lỗi đóng tài nguyên đầu tiên theo thứ tự đóng có thể trở thành ngoại lệ chính.

### Kiểm chứng trường hợp khởi tạo chưa hết tài nguyên

Phần `initializationFailure()` tạo `A` rồi `B` thành công. Đến tài nguyên `C`, biểu thức khởi tạo `failToInitializeC()` ném `IOException("init-C")` **trước khi tạo thành công đối tượng `C`**. Java không chạy thân `try`, cũng không có tài nguyên `C` đã khởi tạo thành công để tự động đóng.

Tuy nhiên, `B` và `A` đã được tạo thành công trước đó nên vẫn được đóng **theo chiều ngược**, tức `B` trước `A`. Nếu việc đóng gây lỗi, chúng được ghi nhận là suppressed exceptions của lỗi khởi tạo `init-C`.

### Kiểm chứng trường hợp `return`

Phần `returnEarly()` chuẩn bị trả `7` nhưng vẫn phải đóng `B` rồi `A` trước khi caller in `result=7`. Chữ ký `throws IOException` của phương thức hợp lý vì `Tracked.close()` có thể ném `IOException`, dù trong lần chạy này cả hai tài nguyên đều không được cấu hình gây lỗi khi đóng.

## 5. Nếu tài nguyên thứ hai hoặc thứ ba khởi tạo thất bại

Hãy phân biệt hai tình huống:

**Tình huống A — Khởi tạo `B` thất bại:**

~~~java
try (Resource a = openA();
     Resource b = openBThatThrows();
     Resource c = openC()) {
    runTask();
}
~~~

Đây là mã giả dùng để giải thích thứ tự. Khi `openA()` hoàn tất và `openBThatThrows()` ném lỗi:

1. `A` đã được khởi tạo thành công.
2. `B` chưa được xác lập là tài nguyên đã tạo thành công; `C` chưa được khởi tạo.
3. Thân `try` không được thực thi.
4. Java đóng `A`.
5. Ngoại lệ từ việc khởi tạo `B` tiếp tục lan ra ngoài; lỗi khi đóng `A` có thể được suppressed vào ngoại lệ đó.

**Tình huống B — Khởi tạo `C` thất bại:** `A` và `B` đã được khởi tạo, nên cả hai được đóng theo thứ tự `B` rồi `A` như ví dụ Java chạy được ở phần 4.

Không nói “bất cứ tài nguyên nào được nhắc đến trong header đều được đóng”: chỉ những tài nguyên **đã được khởi tạo thành công và được quản lý bởi cấu trúc** mới có `close()` tự động. Với một constructor bị lỗi giữa chừng, phần tài nguyên nội bộ mà constructor đã mở nhưng chưa bàn giao có thể cần được chính constructor hoặc hàm tạo **tự dọn dẹp**; try-with-resources bên ngoài không thể tự động đóng một đối tượng chưa được khởi tạo thành công.

## 6. Cơ chế ngoại lệ chính và suppressed exceptions

Sử dụng nhiều tài nguyên thường đặt ra tình huống: **thân `try` thất bại, rồi một hoặc nhiều tài nguyên lại thất bại khi đóng**. Java không muốn lỗi dọn dẹp vô tình che khuất nguyên nhân chính.

Giả sử:

~~~text
body:       throw WorkException("work")
close C:    throw IOException("close C")
close B:    return normally
close A:    throw IOException("close A")
~~~

Với try-with-resources, quan hệ lỗi được tổ chức như sau:

~~~text
Primary exception:
  WorkException("work")
  ├── suppressed: IOException("close C")
  └── suppressed: IOException("close A")
~~~

Thứ tự suppressed phản ánh **thứ tự thực tế lỗi được thêm khi các tài nguyên đóng**, thường tương ứng với thứ tự đóng ngược: `C` rồi `A` trong ví dụ.

### Trường hợp thân `try` thành công nhưng nhiều `close()` lỗi

Giả sử `A` và `B` được mở, thân `try` kết thúc bình thường, `B.close()` ném lỗi, rồi `A.close()` cũng ném lỗi:

~~~text
Primary:    IOException("close B")
Suppressed: IOException("close A")
~~~

Không có ngoại lệ từ thân `try` để làm primary, nên ngoại lệ đóng đầu tiên được giữ làm primary. Các lỗi đóng tiếp theo được đính kèm vào nó.

### Cách đọc trong code

~~~java
try (Tracked a = new Tracked("A", true);
     Tracked b = new Tracked("B", true)) {
    throw new IllegalStateException("operation failed");
} catch (Exception e) {
    System.out.println("Primary: " + e.getMessage());
    for (Throwable suppressed : e.getSuppressed()) {
        System.out.println("Suppressed: " + suppressed.getMessage());
    }
}
~~~

Đây là **đoạn minh họa** dùng lớp `Tracked` trong chương trình chính. Nó in lỗi chính từ thân `try` và các lỗi đóng được giữ lại. `getSuppressed()` trả về mảng chứa các ngoại lệ đã được đính kèm, không tự biến chúng thành `getCause()`.

Phân biệt:

| Thuộc tính | Ý nghĩa |
| --- | --- |
| `getCause()` | Một ngoại lệ/nguyên nhân được liên kết bằng cause |
| `getSuppressed()` | Các ngoại lệ phụ bị giữ lại khi ngoại lệ chính vẫn được ưu tiên |
| `getMessage()` | Thông điệp của chính đối tượng ngoại lệ đang xem |
| `printStackTrace()` | Có thể hiển thị lỗi chính, cause và suppressed để chẩn đoán |

**Suppressed exceptions không đồng nghĩa mọi lỗi đều được bỏ qua.** Chúng vẫn quan trọng để điều tra tài nguyên nào thất bại khi đóng. Không nên chỉ log `e.getMessage()` nếu cần giữ đầy đủ thông tin đóng tài nguyên.

## 7. Try-with-resources khác `finally { close(); }` viết thủ công ra sao?

Mẫu đóng bằng tay:

~~~java
Resource a = openA();
Resource b = openB();
try {
    use(a, b);
} finally {
    b.close();
    a.close();
}
~~~

Đây là **mã giả minh họa**, không phải chương trình độc lập. Nó có ít nhất hai rủi ro:

1. Nếu `openB()` lỗi **trước khi vào `try`**, đoạn `finally` chưa bắt đầu quản lý `A`, dẫn tới bỏ sót đóng tài nguyên đã mở.
2. Nếu `b.close()` ném lỗi, câu lệnh `a.close()` ở sau **có thể không được thực thi**, vì luồng chuyển ra khỏi `finally` trước khi tới dòng đó.

Với try-with-resources, Java sắp đặt cơ chế đóng để các tài nguyên đã khởi tạo thành công được **thử đóng theo chiều ngược**, kể cả khi việc đóng tài nguyên ở phía sau ném lỗi. Ngoài ra, Java còn quản lý suppressed exceptions theo quy tắc, tránh vô tình làm mất lỗi từ thân `try`.

Một phiên bản `finally` thủ công chắc chắn hơn cần các `try/finally` lồng nhau, xử lý ngoại lệ riêng cho từng lần đóng và quản lý quá trình khởi tạo dở dang — dễ sai và khó đọc hơn. Đây là lý do **ưu tiên try-with-resources** khi tài nguyên triển khai `AutoCloseable`.

### Sơ đồ ý tưởng của việc đóng ngược

~~~text
Tạo A thành công
  └── Tạo B thành công
        └── Tạo C thành công
              └── Chạy body
                  └── Đóng C
              └── Đóng B
          └── Đóng A
~~~

Đây là **sơ đồ khái niệm**, không phải phép biến đổi chính xác từng bytecode. Java có thể dùng logic tương đương các khối `try/finally` lồng nhau, đồng thời theo dõi primary exception và các lỗi bị suppressed.

## 8. Thứ tự đóng và vòng đời của tài nguyên phụ thuộc

Các tài nguyên có thể không độc lập. Ví dụ API thường có tầng đọc dữ liệu:

~~~java
try (java.io.InputStream raw =
             java.nio.file.Files.newInputStream(path);
     java.io.InputStreamReader decoder =
             new java.io.InputStreamReader(raw,
                     java.nio.charset.StandardCharsets.UTF_8);
     java.io.BufferedReader buffered =
             new java.io.BufferedReader(decoder)) {
    System.out.println(buffered.readLine());
}
~~~

Đây là đoạn minh họa cần biến `path` kiểu `Path` và xử lý `IOException` thích hợp ở phương thức bao quanh.

Trình tự quản lý của chính cấu trúc trên là:

1. Khởi tạo `raw`.
2. Khởi tạo `decoder` từ `raw`.
3. Khởi tạo `buffered` từ `decoder`.
4. Đọc dữ liệu.
5. Gọi `buffered.close()`, rồi `decoder.close()`, rồi `raw.close()`.

**Cảnh báo về đóng lặp:** Các lớp wrapper của Java thường **tự đóng tài nguyên bên dưới** khi `close()`. Vì vậy ví dụ đưa cả ba tài nguyên vào cùng header là cách minh họa thứ tự, nhưng trong thiết kế thực tế nó có thể khiến tầng bên dưới nhận nhiều lần đóng. Không nên mặc định mọi loại tài nguyên chịu được `close()` lặp hoặc mọi wrapper có cùng chính sách ownership.

Với một chuỗi wrapper mà wrapper ngoài có quyền sở hữu tài nguyên bên trong, có thể cân nhắc để **chỉ wrapper ngoài quản lý việc đóng**, theo hợp đồng API cụ thể:

~~~java
try (java.io.BufferedReader reader =
             java.nio.file.Files.newBufferedReader(
                     path, java.nio.charset.StandardCharsets.UTF_8)) {
    System.out.println(reader.readLine());
}
~~~

Đây là một đoạn minh họa dùng phương thức `Files.newBufferedReader`. Caller không phải tự liệt kê cả các tầng stream/decoder khi API trả về một tài nguyên đóng đúng trách nhiệm của nó.

Nguyên tắc **ownership** quan trọng không kém thứ tự: thành phần nào được giao trách nhiệm đóng, tài nguyên có được chia sẻ hay không, và việc đóng wrapper có kéo theo đóng tài nguyên gốc không.

## 9. Tài nguyên có sẵn từ trước và cú pháp Java 9+

Từ Java 9, có thể đưa vào try-with-resources một **biến tài nguyên đã tồn tại**, nếu nó là `final` hoặc **effectively final** và thỏa kiểu `AutoCloseable`:

~~~java
Tracked resource = new Tracked("existing", false);

try (resource) {
    System.out.println("use existing");
}
~~~

Đây là đoạn minh họa dùng lớp `Tracked` trong chương trình trước. Khi vùng `try` kết thúc, tài nguyên được đóng tự động dù nó đã được khởi tạo trước phần đầu `try`.

Với nhiều biến đã có, thứ tự xuất hiện trong danh sách vẫn quyết định thứ tự đóng:

~~~java
Tracked first = new Tracked("first", false);
Tracked second = new Tracked("second", false);

try (first; second) {
    System.out.println("body");
}
~~~

Đóng `second` trước `first`.

**Lưu ý:** Trong ví dụ biến đã có, thứ tự tạo thật sự xảy ra ở các câu lệnh trước `try`; quy tắc đóng dựa vào **thứ tự liệt kê trong phần đầu try-with-resources**, không phải khả năng “nhớ lịch sử tạo đối tượng” của JVM. Chẳng hạn có thể khai báo `try (second; first)` và sẽ đóng `first` trước `second`. Do đó cách phát biểu “ngược thứ tự tạo” chuẩn nhất khi nói về **các khai báo tạo tài nguyên ngay trong phần đầu `try`**. Khi dùng biến đã tạo từ trước, cần nói chính xác là **ngược thứ tự tài nguyên trong danh sách quản lý**.

Không nên dùng lại cùng đối tượng tài nguyên trong nhiều mục của danh sách như một mẹo: nó có thể dẫn đến gọi `close()` nhiều lần trên cùng đối tượng, vi phạm giả định ownership của một số API.

## 10. Trường hợp tài nguyên là `null`

Một biến tài nguyên có thể được khởi tạo bằng `null` nếu kiểu hợp lệ:

~~~java
AutoCloseable optional = null;
try (optional) {
    System.out.println("body");
}
~~~

Đây là ví dụ hợp lệ từ Java 9 trở lên vì `optional` là effectively final. Khi kết thúc khối, Java **không gọi `close()` lên một tham chiếu `null`**. Nó không phát sinh `NullPointerException` chỉ vì mục tài nguyên trong phần đầu `try` là `null`.

Tuy nhiên, điều đó không có nghĩa mọi tài nguyên nên cho phép `null` trong thiết kế API. Nếu thiếu tài nguyên là trạng thái không hợp lệ, nên kiểm tra và báo lỗi rõ ràng thay vì dùng `null` để bỏ qua công việc cần thiết.

Nếu các mục `A`, `B`, `C` gồm `B == null` thì phần đóng hiệu lực là `C` rồi `A`; mục `B` không có đối tượng để đóng.

## 11. Thứ tự đóng với `catch` và `finally` của cùng cấu trúc

Try-with-resources có thể đi cùng các nhánh `catch` và `finally`:

~~~java
try (Tracked a = new Tracked("A", false);
     Tracked b = new Tracked("B", false)) {
    System.out.println("body");
} catch (java.io.IOException e) {
    System.out.println("catch: " + e.getMessage());
} finally {
    System.out.println("finally");
}
~~~

Đoạn này dùng lớp `Tracked` đã định nghĩa trước. Nếu không có lỗi, thứ tự in:

~~~text
open A
open B
body
close B
close A
finally
~~~

**Tài nguyên được đóng trước khi chạy nhánh `catch` hoặc `finally` đi kèm cấu trúc try-with-resources.** Nếu lỗi từ thân `try` hoặc lỗi khi đóng xảy ra, các nhánh `catch` sẽ nhìn thấy ngoại lệ sau khi quá trình đóng đã được thực hiện theo quy tắc. `finally` chạy sau nhánh `catch` tương ứng.

Ví dụ nếu `B.close()` ném `IOException` mà thân `try` không ném lỗi, `catch (IOException e)` sẽ nhận lỗi khi đóng. Nếu thân `try` ném `IllegalArgumentException` và `B.close()` ném `IOException`, `catch (IOException e)` **không** tự động nhận lỗi suppressed đó như ngoại lệ chính; cần có handler phù hợp với ngoại lệ đang truyền và kiểm tra `getSuppressed()` nếu cần.

## 12. Khi `close()` ném lỗi, Java còn đóng tài nguyên trước đó không?

**Có — theo quy tắc try-with-resources**, Java cố gắng đóng những tài nguyên đã được khởi tạo thành công còn lại theo thứ tự ngược, ngay cả khi một lệnh `close()` vừa ném ngoại lệ.

Điều này tạo ra điểm khác với đoạn `finally` thủ công chỉ liệt kê:

~~~java
finally {
    b.close();
    a.close();
}
~~~

Ở đoạn thủ công đó, nếu `b.close()` ném lỗi và không có thêm xử lý, Java không đi tiếp tới `a.close()`. Với try-with-resources, công việc đóng các tài nguyên còn lại được sắp đặt riêng và lỗi phát sinh được quản lý bằng suppressed exceptions.

Tuy vậy, cần hiểu giới hạn của chữ “cố gắng”:

- Nếu một `close()` **treo vô hạn**, luồng không thể đi tiếp để đóng tài nguyên còn lại.
- Nếu tiến trình bị kết thúc cưỡng bức, không thể bảo đảm các `close()` còn lại chạy.
- Nếu một `close()` thực hiện `System.exit` hoặc `Runtime.halt`, quy tắc chấm dứt JVM có thể ngăn đóng tiếp.
- Nếu code hoặc VM gặp lỗi quá nghiêm trọng để tiếp tục thực thi, không thể hứa dọn dẹp hoàn tất.

Try-with-resources cải thiện **thứ tự và quản lý lỗi trong luồng điều khiển Java bình thường**; nó không phải cơ chế bảo đảm an toàn bất chấp việc tiến trình không còn chạy.

## 13. AutoCloseable, Closeable và cách tự thiết kế tài nguyên

Khi tự viết một lớp `AutoCloseable`, nên xác định rõ:

- Một lần `close()` phải làm gì (đóng kết nối, giải phóng khóa, xả bộ đệm, hoàn tất giao dịch hay chỉ thu hồi tài nguyên).
- Có an toàn nếu gọi `close()` lặp không?
- `close()` có thể ném loại ngoại lệ nào?
- Tài nguyên có thể được nhiều thành phần cùng sở hữu không?
- Việc đóng một wrapper có tự đóng đối tượng bên dưới không?

Ví dụ lớp đơn giản được đóng an toàn khi gọi lại:

~~~java
static final class IdempotentResource implements AutoCloseable {
    private boolean closed;

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        System.out.println("closed once");
    }
}
~~~

Đây là lớp lồng minh họa. Trong chương trình đa luồng, nếu một đối tượng có thể bị đóng đồng thời từ nhiều thread, cần thiết kế đồng bộ thích hợp; biến `closed` đơn thuần không đủ bảo đảm thread safety.

**Không phải `close()` của mọi thư viện đều idempotent.** Hãy xem hợp đồng của tài nguyên cụ thể, đặc biệt khi kết hợp những wrapper tự đóng tầng dưới và danh sách nhiều tài nguyên.

Nếu một tài nguyên cần xử lý nhiều bước khi đóng, hãy bảo đảm phần xử lý lỗi không vô tình bỏ qua bước quan trọng hoặc che nguyên nhân gốc. Trong các trường hợp phức tạp, cấu trúc thêm suppressed exceptions cho phép giữ thông tin của nhiều lỗi, nhưng vẫn cần chính sách ghi log và phản hồi thất bại phù hợp.

## 14. Những lỗi hiểu sai phổ biến

**Sai lầm 1: “Khai báo A rồi B thì Java đóng A trước.”**  
Không. Nếu chúng ở cùng phần đầu try-with-resources, Java đóng `B` trước `A`.

**Sai lầm 2: “Java đóng đồng thời các tài nguyên.”**  
Không. Các lời gọi `close()` được thực hiện có thứ tự theo quy tắc đóng ngược. Chỉ khi bản thân `close()` chủ động dùng bất đồng bộ thì hành vi nội bộ của nó có thể khác.

**Sai lầm 3: “Nếu body ném lỗi thì không đóng tài nguyên.”**  
Không. Những tài nguyên đã khởi tạo thành công vẫn được cố gắng đóng khi rời phạm vi `try`.

**Sai lầm 4: “Nếu đóng B bị lỗi thì A chắc chắn không được đóng.”**  
Không đúng với try-with-resources trong luồng Java bình thường. Java tiếp tục cố gắng đóng `A` và quản lý ngoại lệ bị suppressed.

**Sai lầm 5: “Nếu constructor của C lỗi, Java vẫn phải gọi `C.close()`.”**  
Không. Nếu `C` chưa được khởi tạo thành công thì nó không trở thành một tài nguyên đã được quản lý; `A` và `B` thành công trước đó mới được đóng theo quy tắc.

**Sai lầm 6: “Bất kỳ lỗi close nào cũng thay thế lỗi body.”**  
Không. Khi thân `try` đã ném lỗi chính, các lỗi close thường được đính kèm qua `getSuppressed()`.

**Sai lầm 7: “Nếu body không lỗi thì lỗi close chỉ là suppressed.”**  
Không. Lỗi đóng đầu tiên có thể là ngoại lệ chính khi không có lỗi khác đang truyền.

**Sai lầm 8: “`getCause()` và `getSuppressed()` giống nhau.”**  
Không. Cause mô tả một quan hệ nguyên nhân; suppressed giữ các lỗi bổ sung không thay thế primary exception.

**Sai lầm 9: “Đã viết `return` trong try thì caller nhận giá trị trước khi close.”**  
Không. Đóng xảy ra trước khi lời gọi hoàn tất. Nếu close ném checked exception, caller có thể nhận lỗi thay vì giá trị.

**Sai lầm 10: “Các biến tài nguyên tạo sẵn luôn đóng ngược thời gian chúng từng được tạo.”**  
Không. Với cú pháp Java 9+ `try (first; second)`, thứ tự đóng là ngược thứ tự **liệt kê trong header**, dù hai biến được tạo lúc nào.

**Sai lầm 11: “Wrapper ngoài và mọi tầng bên dưới đều phải khai báo trong cùng một header.”**  
Không nhất thiết. Đóng wrapper có thể tự đóng tầng dưới; cần hiểu hợp đồng ownership để tránh đóng trùng.

**Sai lầm 12: “Try-with-resources bảo đảm close sau khi JVM bị kill.”**  
Không. Khi không còn cơ hội chạy code Java, cả `finally` lẫn try-with-resources đều không thể bảo đảm được thực thi.

**Sai lầm 13: “Tài nguyên `null` sẽ làm close báo NullPointerException.”**  
Không. Một mục tài nguyên có giá trị `null` không bị gọi `close()` tự động.

**Sai lầm 14: “Có thể dùng bất kỳ đối tượng nào trong try (...) vì Java sẽ tìm phương thức close.”**  
Không. Kiểu tĩnh của tài nguyên phải tương thích với `AutoCloseable`; chỉ một phương thức trùng tên `close()` mà không triển khai giao diện phù hợp là chưa đủ.

## 15. Câu hỏi tự kiểm tra

1. Với `try (A a = ...; B b = ...; C c = ...)`, thứ tự khởi tạo và đóng lần lượt là gì?
2. Vì sao đóng tài nguyên theo LIFO thường hợp lý khi đối tượng mới được xây dựng từ đối tượng đã tồn tại?
3. Một phương thức `return` từ bên trong try-with-resources: tài nguyên đóng trước hay sau khi caller nhận kết quả?
4. Nếu thân `try` ném `IllegalArgumentException("work")`, `B.close()` và `A.close()` đều ném `IOException`, ngoại lệ nào là primary?
5. Trong câu 4, `getSuppressed()` chứa các lỗi theo thứ tự nào?
6. Nếu thân `try` chạy hết bình thường nhưng `B.close()` lỗi rồi `A.close()` lỗi, ngoại lệ nào là primary?
7. Nếu tài nguyên `C` khởi tạo thất bại, Java đóng những tài nguyên nào đã được khởi tạo thành công trước nó?
8. Nếu constructor của `B` mở một socket nội bộ rồi ném trước khi tạo thành công đối tượng `B`, ai phải chịu trách nhiệm thu hồi socket đó?
9. Vì sao `finally { b.close(); a.close(); }` viết thủ công có thể bỏ sót `a.close()`?
10. Trong try-with-resources có `catch` và `finally`, tài nguyên được đóng trước hay sau các khối đó?
11. Với `Tracked first` và `Tracked second` tạo sẵn, `try (second; first)` sẽ đóng theo thứ tự nào?
12. Nếu mục tài nguyên có giá trị `null`, Java có tự gọi `close()` lên `null` không?
13. Vì sao cùng đưa `BufferedReader` và `InputStream` bên dưới vào header đôi khi dẫn tới đóng trùng tài nguyên?
14. Có phải một lớp chỉ cần khai báo phương thức `void close()` là đủ được dùng trong try-with-resources không?
15. Try-with-resources có thể bảo đảm mọi tài nguyên được đóng khi một `close()` bị kẹt vô hạn không?
16. Khi thiết kế `AutoCloseable` riêng, tại sao cần xác định rõ ownership và hành vi nếu `close()` bị gọi nhiều lần?

### Đáp án gợi ý

1. Khởi tạo `A -> B -> C`, đóng `C -> B -> A`.
2. Để tầng mới dừng sử dụng tài nguyên bên dưới trước khi đóng tầng bên dưới.
3. Trước khi caller nhận kết quả.
4. Lỗi `work` từ thân `try` là primary.
5. `close-B` rồi `close-A` vì `B` đóng trước.
6. `close-B` là primary, `close-A` là suppressed.
7. Đóng `B` rồi `A`; không tự đóng `C` chưa khởi tạo thành công.
8. Constructor/hàm mở tài nguyên chịu trách nhiệm dọn dẹp phần chưa bàn giao.
9. Nếu `b.close()` ném lỗi, lệnh đóng `a` có thể không được thực hiện.
10. Trước các khối `catch` và `finally` gắn với cấu trúc đó.
11. Đóng `first` rồi `second`, ngược thứ tự liệt kê.
12. Không.
13. Wrapper thường đã đóng tài nguyên dưới khi `close()`, dẫn tới nhiều lần close.
14. Không, cần tương thích với `AutoCloseable`.
15. Không; luồng không thể tiến tới những lời gọi close còn lại nếu đang bị kẹt.
16. Để tránh đóng thiếu, đóng trùng hoặc làm hỏng tài nguyên được nhiều thành phần sử dụng.

## 16. Ghi nhớ

**Tài nguyên trong try-with-resources được khởi tạo từ trái sang phải và đóng từ phải sang trái**. Nếu thân `try` ném lỗi, Java vẫn cố gắng đóng các tài nguyên đã khởi tạo thành công; lỗi đóng thường được lưu dưới dạng **suppressed exceptions** của lỗi chính. Nếu không có lỗi trước đó, lỗi đóng đầu tiên có thể trở thành primary. Nếu quá trình khởi tạo dừng giữa chừng, những tài nguyên đã mở thành công vẫn được đóng ngược thứ tự. Với biến tài nguyên đã tạo từ trước trong Java 9+, thứ tự đóng dựa trên vị trí liệt kê trong header; hãy thiết kế ownership rõ ràng và dùng try-with-resources để tránh bỏ sót dọn dẹp so với `finally` thủ công.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="try-with-resources-007">Xử lý khi việc tạo một tài nguyên thất bại</a>

<details>
<summary>Xem chi tiết</summary>

# Xử lý khi việc tạo một tài nguyên thất bại trong Java `try-with-resources`

Trong Java, `try-with-resources` không chỉ tự động đóng tài nguyên khi **phần thân `try`** kết thúc. Nó còn bảo vệ trường hợp tinh tế hơn: **một biểu thức khởi tạo tài nguyên trong phần đầu `try (...)` bị lỗi trước khi phần thân bắt đầu chạy**. Khi ấy, Java phải dọn dẹp những tài nguyên đã khởi tạo thành công trước đó, giữ thông tin ngoại lệ chính và chuyển lỗi đến bộ xử lý thích hợp.

Ví dụ:

~~~java
try (Resource a = openA();
     Resource b = openB();
     Resource c = openC()) {
    use(a, b, c);
}
~~~

Nếu `openA()` thành công nhưng `openB()` ném ngoại lệ, Java **đóng `a`**, **không gọi `openC()`**, **không chạy `use(a,b,c)`**, rồi truyền ngoại lệ từ `openB()` ra ngoài. Java **không tự gọi `close()` trên một đối tượng `b` chưa được khởi tạo hoàn tất**. Nếu `a.close()` cũng thất bại, ngoại lệ đóng được gắn dưới dạng **suppressed exception** của lỗi khởi tạo B (với cơ chế suppression thông thường).

Bài này giải thích chính xác thứ tự khởi tạo, đóng, bắt ngoại lệ và `finally` trên Java 17+; phân biệt lỗi mở tài nguyên với lỗi sử dụng tài nguyên; chỉ ra giới hạn khi constructor cấp phát tài nguyên rồi thất bại; và đưa ra các ví dụ chạy được cùng hướng thiết kế an toàn.

## 1. Điều kiện để một tài nguyên được quản lý tự động

`try-with-resources` hỗ trợ những tài nguyên có kiểu thực thi `AutoCloseable`, trong đó phổ biến có `Closeable` như `InputStream`, `Reader` hay đối tượng kết nối tùy API. Phần `try (...)` khai báo một hoặc nhiều resource variable:

~~~java
try (java.io.InputStream in = java.nio.file.Files.newInputStream(path)) {
    int firstByte = in.read();
    System.out.println(firstByte);
}
~~~

Đây là **đoạn minh họa trong một phương thức** có biến `path` kiểu `Path` và xử lý hoặc khai báo `IOException`. Nếu biểu thức `Files.newInputStream(path)` thành công và trả về stream khác `null`, stream đó được Java quản lý và `close()` sẽ được gọi khi rời cấu trúc thích hợp, kể cả khi phần thân ném ngoại lệ.

Các đặc điểm cần nhớ:

1. **Khởi tạo từ trái sang phải.** Mỗi biểu thức mở tài nguyên phải hoàn thành bình thường thì Java mới bắt đầu biểu thức tiếp theo.
2. **Đóng theo thứ tự ngược.** Những tài nguyên đã khởi tạo thành công được đóng từ phải sang trái khi rời phạm vi.
3. **Phần thân chỉ chạy sau khi toàn bộ biểu thức tài nguyên cần thiết hoàn tất bình thường.**
4. **Một biểu thức khởi tạo thất bại** khiến phần thân không chạy; các tài nguyên đã khởi tạo trước đó vẫn cần được đóng.
5. `catch` và `finally` đi kèm của chính cấu trúc `try-with-resources` được thực hiện **sau bước dọn dẹp tự động** của các tài nguyên đã được quản lý.
6. Nếu biểu thức tài nguyên trả `null` **mà không ném lỗi**, biểu thức đó vẫn hoàn tất bình thường; Java bỏ qua việc `close()` cho tham chiếu `null`. Điều này khác với *khởi tạo ném ngoại lệ*.

Không được nhầm “đã bắt đầu chạy constructor” với “resource variable đã hoàn tất khởi tạo và được đưa vào phạm vi tự quản lý”. Nếu constructor ném trước khi trả về một đối tượng hợp lệ, biến tài nguyên tương ứng không được khởi tạo hoàn tất để Java tự gọi `close()` trên nó.

## 2. Luồng thực thi nếu một tài nguyên ở giữa danh sách thất bại

Giả sử có ba tài nguyên A, B và C, được khai báo theo thứ tự đó:

~~~java
try (Resource a = openA();
     Resource b = openB();
     Resource c = openC()) {
    System.out.println("Thân try");
} catch (IOException ex) {
    System.out.println("Lỗi: " + ex.getMessage());
} finally {
    System.out.println("Kết thúc cấu trúc");
}
~~~

Đây là **khung minh họa**, cần định nghĩa `Resource`, `openA/B/C()` và các import/chữ ký tương ứng. Nếu A thành công nhưng B ném `IOException`, luồng diễn ra như sau:

~~~text
Bước 1: Đánh giá openA()
        └── Thành công → A trở thành tài nguyên được quản lý

Bước 2: Đánh giá openB()
        └── Ném IOException → dừng khởi tạo danh sách

Bước 3: C không được khởi tạo; thân try không chạy

Bước 4: Đóng A bằng A.close()
        ├── Nếu thành công: giữ nguyên IOException từ B
        └── Nếu ném lỗi: bổ sung lỗi đóng vào suppressed của lỗi B

Bước 5: Chuyển đến catch (IOException), nếu phù hợp

Bước 6: Thực thi finally đi kèm

Bước 7: Tiếp tục hoặc truyền ngoại lệ, tùy cách catch/finally kết thúc
~~~

**Mấu chốt:** lỗi từ B không làm quên A; A được đóng dù phần thân `try` chưa hề thực thi. C thì không có lời gọi tạo nên không có đối tượng C phải đóng. Đừng dự đoán thứ tự từ tên biến hay kiểu ngoại lệ: phải xem **thứ tự khai báo và điểm lỗi thực tế**.

Nếu có nhiều tài nguyên đã hoàn tất khởi tạo, ví dụ A và B thành công còn C lỗi, Java đóng **B rồi A**. Nếu chính quá trình đóng ném ngoại lệ, các lỗi đóng được ghi nhận thành các suppressed exception, trong khi lỗi khởi tạo C vẫn là ngoại lệ chính của đường thoát đó.

## 3. Ví dụ Java 17+ hoàn chỉnh: tài nguyên B tạo thất bại, đóng A cũng lỗi

Tạo tệp `ResourceInitFailureDemo.java`:

~~~java
import java.io.IOException;

public class ResourceInitFailureDemo {
    static final class Resource implements AutoCloseable {
        private final String name;
        private final boolean failOnClose;

        Resource(String name, boolean failOnOpen, boolean failOnClose)
                throws IOException {
            System.out.println("Bắt đầu tạo " + name);
            if (failOnOpen) {
                throw new IOException("Tạo " + name + " thất bại");
            }
            this.name = name;
            this.failOnClose = failOnClose;
            System.out.println("Đã tạo " + name);
        }

        @Override
        public void close() throws IOException {
            System.out.println("Đóng " + name);
            if (failOnClose) {
                throw new IOException("Đóng " + name + " thất bại");
            }
        }
    }

    public static void main(String[] args) {
        try (Resource a = new Resource("A", false, true);
             Resource b = new Resource("B", true, false);
             Resource c = new Resource("C", false, false)) {
            System.out.println("Trong thân try");
        } catch (IOException ex) {
            System.out.println("Lỗi chính: " + ex.getMessage());
            System.out.println("Số lỗi suppressed: " + ex.getSuppressed().length);
            for (Throwable suppressed : ex.getSuppressed()) {
                System.out.println("Suppressed: " + suppressed.getMessage());
            }
        } finally {
            System.out.println("Cuối cấu trúc");
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 ResourceInitFailureDemo.java
java ResourceInitFailureDemo
~~~

Stdout **chính xác**:

~~~text
Bắt đầu tạo A
Đã tạo A
Bắt đầu tạo B
Đóng A
Lỗi chính: Tạo B thất bại
Số lỗi suppressed: 1
Suppressed: Đóng A thất bại
Cuối cấu trúc
~~~

### Diễn giải từng dòng

- `new Resource("A", false, true)` ghi hai dòng đầu: A khởi tạo thành công, được đưa vào danh sách quản lý. Tham số cuối `true` mô phỏng việc `A.close()` sẽ ném `IOException`.
- `new Resource("B", true, false)` in `Bắt đầu tạo B`, sau đó ném `IOException("Tạo B thất bại")` **trong constructor**, trước khi hoàn tất gán các field và trước khi tạo xong resource B.
- Ngay khi biểu thức B thất bại, Java dừng xử lý danh sách: không thấy `Bắt đầu tạo C` và không thấy `Trong thân try`.
- Java gọi `A.close()`, in `Đóng A`. Vì `close()` của A cũng ném, lỗi đóng A được gắn làm **suppressed** của lỗi chính.
- `catch (IOException ex)` nhận lỗi `Tạo B thất bại`, chứ không phải lỗi `Đóng A thất bại`, vì cơ chế `try-with-resources` giữ ưu tiên cho lỗi khởi tạo đã xảy ra trước khi bắt đầu cleanup.
- `ex.getSuppressed()` trả mảng chứa một lỗi từ `A.close()`. `finally` chạy sau `catch`.

Trong ví dụ, `catch` kết thúc bình thường nên chương trình không ném lỗi ra khỏi `main`. Nếu không có `catch` phù hợp, lỗi từ B được truyền lên người gọi **sau khi** quá trình đóng A hoàn tất. `finally` đi kèm vẫn chạy trên đường lan truyền, nếu môi trường thực thi tiếp tục bình thường.

Đây là trường hợp thực tế cần quan sát cả **lỗi chính** và **suppressed**, vì nếu chỉ ghi `ex.getMessage()` thì bạn nhìn thấy nguyên nhân mở B thất bại nhưng bỏ qua thông tin A cũng không được đóng thành công.

## 4. Nếu tài nguyên đầu tiên tạo thất bại thì sao?

Khi ngay **biểu thức tài nguyên đầu tiên** ném lỗi, chưa có tài nguyên nào trong danh sách khởi tạo thành công để `try-with-resources` gọi `close()`. Phần thân `try` không chạy. `catch` bên ngoài phần quản lý tự động vẫn có thể bắt lỗi, và `finally` vẫn thực thi theo quy tắc của cấu trúc.

Tệp `FirstInitFailureDemo.java` minh họa:

~~~java
import java.io.IOException;

public class FirstInitFailureDemo {
    static final class Resource implements AutoCloseable {
        Resource() throws IOException {
            System.out.println("Tạo tài nguyên đầu tiên");
            throw new IOException("Không khởi tạo được");
        }

        @Override
        public void close() {
            System.out.println("Không bao giờ in: close()");
        }
    }

    public static void main(String[] args) {
        try (Resource resource = new Resource()) {
            System.out.println("Không bao giờ in: thân try");
        } catch (IOException ex) {
            System.out.println("Bắt lỗi: " + ex.getMessage());
        } finally {
            System.out.println("Finally chạy");
        }
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 FirstInitFailureDemo.java
java FirstInitFailureDemo
~~~

Stdout chính xác:

~~~text
Tạo tài nguyên đầu tiên
Bắt lỗi: Không khởi tạo được
Finally chạy
~~~

Không có dòng `close()` vì constructor **không tạo xong** resource đầu tiên, và không có dòng trong thân `try`. Đừng nhầm điều này với bảo đảm rằng constructor không để rò rỉ thứ gì: nếu constructor đã mở một file hoặc giữ một tài nguyên con trước khi ném, đối tượng bao ngoài chưa hoàn tất khởi tạo **không được đóng tự động** theo `try-with-resources` ở nơi gọi. Đó là trách nhiệm của chính code khởi tạo hoặc factory, được bàn ở phần 8.

## 5. Nếu tất cả khởi tạo xong rồi phần thân `try` ném lỗi

Cần phân biệt **lỗi ở resource initializer** với **lỗi ở phần thân**. Khi tất cả tài nguyên đã tạo thành công, phần thân mới chạy. Nếu body ném ngoại lệ, Java cũng đóng các tài nguyên ngược thứ tự; nếu một `close()` ném lỗi thì ngoại lệ đóng có thể là suppressed của lỗi từ body.

Tệp `CompletedInitDemo.java`:

~~~java
import java.io.IOException;

public class CompletedInitDemo {
    static final class Resource implements AutoCloseable {
        private final String name;

        Resource(String name) {
            this.name = name;
            System.out.println("Tạo " + name);
        }

        @Override
        public void close() throws IOException {
            System.out.println("Đóng " + name);
            if (name.equals("B")) {
                throw new IOException("Lỗi đóng B");
            }
        }
    }

    public static void main(String[] args) {
        try (Resource a = new Resource("A");
             Resource b = new Resource("B")) {
            System.out.println("Trong thân try");
            throw new IllegalStateException("Lỗi xử lý");
        } catch (Exception ex) {
            System.out.println("Lỗi chính: " + ex.getMessage());
            System.out.println("Suppressed: " + ex.getSuppressed()[0].getMessage());
        }
    }
}
~~~

Lệnh:

~~~bash
javac -encoding UTF-8 CompletedInitDemo.java
java CompletedInitDemo
~~~

Stdout:

~~~text
Tạo A
Tạo B
Trong thân try
Đóng B
Đóng A
Lỗi chính: Lỗi xử lý
Suppressed: Lỗi đóng B
~~~

Ở đây, khác với mục 3, cả A và B đã được tạo xong nên **cả hai đều được đóng**, theo thứ tự B rồi A. Lỗi từ thân `try` là `IllegalStateException`; lỗi khi đóng B được lưu như suppressed. Java không bỏ qua việc đóng A chỉ vì đóng B đã thất bại.

Đừng suy ra `try-with-resources` làm mọi lỗi đóng biến mất. Nó **ghi nhận** chúng; bạn cần xem stack trace hoặc `getSuppressed()` để biết một số tài nguyên có thể đã thất bại trong lúc đóng. Ngoại lệ chính có thể đến từ **khởi tạo**, **body** hoặc **đóng**, tùy đường thực thi.

## 6. Trường hợp nhiều tài nguyên: ma trận các đường thất bại

Với:

~~~java
try (A a = openA();
     B b = openB();
     C c = openC()) {
    doWork(a, b, c);
}
~~~

Bảng sau giả định các tài nguyên được khởi tạo thành công trả về tham chiếu khác `null` và `close()` có thể được gọi theo hợp đồng `AutoCloseable`.

| Điểm xảy ra lỗi | Tài nguyên đã khởi tạo thành công | Những `close()` Java gọi | Body chạy? |
| --- | --- | --- | --- |
| `openA()` ném | Không có | Không có | Không |
| `openB()` ném | A | A | Không |
| `openC()` ném | A, B | B rồi A | Không |
| `doWork()` ném | A, B, C | C rồi B rồi A | Có, chạy đến điểm ném |
| Body hoàn tất bình thường | A, B, C | C rồi B rồi A | Có |
| `C.close()` ném sau body bình thường | A, B, C | C rồi B rồi A | Có |

Nếu các `close()` trong một hàng cũng ném, Java vẫn cố đóng những tài nguyên còn lại. Với lỗi khởi tạo hoặc lỗi body làm ngoại lệ chính, những lỗi đóng được thêm vào **suppressed exceptions**. Nếu body thành công mà chính việc đóng tài nguyên ném, ngoại lệ đóng đầu tiên theo thứ tự đóng có thể trở thành lỗi chính, còn các lỗi đóng sau được suppressed.

Đối với tài nguyên trả về `null` mà initializer hoàn thành bình thường, Java **không gọi `close()` với `null`**; biểu thức tiếp theo vẫn được đánh giá và body vẫn có thể chạy. Nếu API không cho phép kết quả `null`, nên kiểm tra điều kiện ấy trong factory, thay vì coi `null` là lỗi khởi tạo được runtime tự động ném.

## 7. Lỗi chính, `cause` và suppressed exceptions

Hai quan hệ thường xuất hiện khi xử lý ngoại lệ Java:

- **`getCause()`:** nguyên nhân được liên kết với một ngoại lệ qua constructor hoặc `initCause()`; hữu ích khi một lớp API bọc lỗi của tầng bên dưới.
- **`getSuppressed()`:** các ngoại lệ phụ được thêm qua `addSuppressed()` hoặc cơ chế tự động như `try-with-resources`; hữu ích khi một đường lỗi chính đồng thời có lỗi trong cleanup.

Trong ví dụ khởi tạo B thất bại rồi đóng A lỗi:

~~~text
IOException: Tạo B thất bại          <- lỗi chính
└── Suppressed: IOException: Đóng A thất bại
~~~

Đây là **suppressed**, không phải tự động thành `Caused by`. Nếu B được tạo bởi một factory chủ động bọc lỗi gốc bằng constructor `IOException(message, cause)`, bản thân lỗi khởi tạo B có thể còn có cause. Cả hai mối quan hệ có thể cùng tồn tại.

Đọc đầy đủ thông tin:

~~~java
// Đoạn minh họa bên trong catch (IOException ex).
System.out.println("Primary: " + ex);
Throwable cause = ex.getCause();
if (cause != null) {
    System.out.println("Cause: " + cause);
}
for (Throwable suppressed : ex.getSuppressed()) {
    System.out.println("Suppressed: " + suppressed);
}
~~~

Với logger chuẩn, thường truyền nguyên đối tượng `Throwable` thay vì chỉ `ex.getMessage()` để giữ stack trace và nhánh suppressed. **Đừng tự động dùng `ex.getSuppressed()[0]`** nếu chưa kiểm tra mảng có phần tử; trong một số đường chạy không có lỗi đóng nào nên mảng rỗng.

Không nên bắt rồi vứt bỏ lỗi khởi tạo chỉ vì dọn dẹp một tài nguyên đã thành công. Nếu lỗi khởi tạo khiến thao tác không thể tiếp tục, giữ nguyên nó hoặc bọc lại kèm cause, đồng thời ghi nhận lỗi đóng nếu có.

## 8. Giới hạn quan trọng: tài nguyên được cấp phát dở trong constructor

`try-with-resources` **chỉ tự động quản lý những resource expression đã hoàn tất bình thường**. Nếu một constructor hoặc factory bắt đầu cấp phát tài nguyên nội bộ, rồi ném trước khi trả về đối tượng cần quản lý, bên gọi **không nhận được tham chiếu đối tượng hoàn chỉnh** để đóng nó.

Ví dụ ý tưởng sai:

~~~java
// Mã minh họa hành vi nguy hiểm, không phải ví dụ độc lập.
class PartialResource implements AutoCloseable {
    private java.io.InputStream input;

    PartialResource(Path path) throws IOException {
        input = Files.newInputStream(path);  // Đã mở stream.
        validateHeader(input);               // Nếu ném IOException thì constructor thất bại.
    }

    @Override
    public void close() throws IOException {
        if (input != null) input.close();
    }
}
~~~

Nếu `validateHeader(input)` ném, việc tạo `new PartialResource(path)` **không hoàn tất bình thường**. Mặc dù lớp khai báo `close()`, `try (PartialResource r = new PartialResource(path))` ở bên ngoài **không tự gọi `r.close()`**, vì biến `r` chưa nhận đối tượng đã khởi tạo thành công. Nếu constructor không tự thu dọn stream, stream có thể bị rò rỉ.

Cách thiết kế tốt là đảm bảo constructor/factory **tự dọn những tài nguyên con nó đã cấp phát trước điểm thất bại**, hoặc xây dựng trong các phạm vi `try-with-resources` riêng. Ví dụ ý tưởng sửa:

~~~java
// Minh họa đặt trong lớp có Path, Files, InputStream và hàm validateHeader.
// ReturnedResource là lớp AutoCloseable nhận ownership của InputStream.
static ReturnedResource open(Path path) throws IOException {
    java.io.InputStream acquired = java.nio.file.Files.newInputStream(path);
    try {
        validateHeader(acquired);     // Có thể ném IOException.
        return new ReturnedResource(acquired);
    } catch (IOException | RuntimeException ex) {
        try {
            acquired.close();
        } catch (IOException closeFailure) {
            ex.addSuppressed(closeFailure);
        }
        throw ex;
    }
}
~~~

Đây là **đoạn minh họa không độc lập**, cần định nghĩa `ReturnedResource` và `validateHeader`; nó minh họa cách chuyển quyền sở hữu tài nguyên. Trong thiết kế trên, chỉ khi bước xác thực và tạo `ReturnedResource` thành công thì quyền đóng stream mới chuyển sang đối tượng kết quả; nếu xảy ra một `IOException` hoặc `RuntimeException` ở khối `try`, factory tự đóng stream và giữ lỗi đóng ở suppressed.

Phải định nghĩa rõ **điểm chuyển ownership** và tránh vừa factory vừa caller cùng chịu trách nhiệm đóng trên đường thành công. Tùy API, dùng factory riêng, các phạm vi `try-with-resources` lồng nhau hoặc đối tượng `AutoCloseable` có logic khởi tạo an toàn sẽ dễ kiểm soát hơn.

**Giới hạn của đoạn minh họa:** nó minh họa các ngoại lệ `IOException`/`RuntimeException` được bắt; đừng coi nó là cơ chế khôi phục tuyệt đối nếu JVM/process dừng cưỡng bức hoặc phát sinh lỗi nghiêm trọng khác. Tài nguyên cấp phát và sở hữu phải được thiết kế có chủ đích.

## 9. Resource expression là phương thức factory, không nhất thiết `new`

Danh sách tài nguyên có thể được khởi tạo bằng constructor hoặc một factory:

~~~java
try (BufferedReader reader = Files.newBufferedReader(path);
     InputStream input = Files.newInputStream(otherPath)) {
    // Dùng reader và input.
}
~~~

Nếu `Files.newBufferedReader(path)` trả về reader thành công còn `Files.newInputStream(otherPath)` ném `IOException`, Java gọi `reader.close()`. Nếu việc tạo reader ngay ở biểu thức đầu tiên thất bại, không có reader hoàn chỉnh thuộc danh sách để tự đóng.

Các `import` và biến `path`, `otherPath` trong đoạn minh họa cần được cung cấp ở phương thức bao ngoài. Điểm quan trọng không phụ thuộc vào việc biểu thức viết `new SomeResource()` hay `SomeFactory.open()`: **kết quả của biểu thức initializer có hoàn thành bình thường không** mới quyết định Java bắt đầu quản lý tài nguyên ấy.

Một factory tốt cần làm rõ: nếu factory ném ngoại lệ, nó có giữ lại tài nguyên nội bộ nào không? Nếu có, đó là lỗi quản lý ownership cần khắc phục trong factory, vì caller có thể không nhận được đối tượng để đóng.

## 10. Biến tài nguyên có sẵn từ Java 9: khác với lỗi trong header

Java 9 trở đi cho phép khai báo một biến tài nguyên **đã tồn tại** trong `try(...)` nếu biến đó là `final` hoặc *effectively final*:

~~~java
// Đoạn minh họa trong phương thức throws IOException.
java.io.InputStream in = java.nio.file.Files.newInputStream(path);

try (in) {
    System.out.println(in.read());
}
~~~

Trong ví dụ, `in` được tạo **trước khi bước vào cấu trúc `try-with-resources`**. Nếu lời gọi `Files.newInputStream(path)` ném, việc thực thi chưa đi đến `try (in)`, do đó `try (in)` không thể tự xử lý một tài nguyên chưa được tạo. Nếu thành công, `try (in)` sẽ đóng tài nguyên khi kết thúc phạm vi của nó.

Đây là một khác biệt đáng nhớ với cách viết mở tài nguyên **ngay trong header**:

~~~java
try (java.io.InputStream in = java.nio.file.Files.newInputStream(path)) {
    System.out.println(in.read());
}
~~~

Cách thứ hai đưa biểu thức mở tài nguyên trực tiếp vào vùng quản lý. Trong cả hai trường hợp, một lỗi xảy ra trước khi có một resource reference được khởi tạo thành công đều không làm Java gọi `close()` trên tài nguyên chưa được nhận về. Nhưng khi nhiều tài nguyên nằm trong header, Java sẽ dọn dẹp **những tài nguyên trước đó đã hoàn tất khởi tạo** nếu một biểu thức sau thất bại.

`effectively final` nghĩa là biến không được gán lại sau khi được gán giá trị, theo quy tắc của Java. Bạn không thể dùng một biến thường xuyên được gán lại trong cú pháp `try (in)` chỉ vì nó có kiểu `AutoCloseable`.

## 11. `catch` và `finally` nhận lỗi vào thời điểm nào?

Với cấu trúc mở rộng:

~~~java
try (Resource a = openA(); Resource b = openB()) {
    work(a, b);
} catch (IOException ex) {
    report(ex);
} finally {
    after();
}
~~~

Quy tắc ngữ nghĩa: khối dọn dẹp tài nguyên tự động chạy theo quy tắc `try-with-resources` **trước khi các nhánh `catch`/`finally` đi kèm được thực hiện**. Vì vậy `catch` có thể nhận một ngoại lệ chính đã mang suppressed exceptions từ bước đóng.

Nếu B khởi tạo thất bại:

~~~text
openA thành công
openB ném IOException
A.close chạy (có thể thêm suppressed)
catch (IOException ex) chạy
finally chạy
~~~

Nếu không có `catch` phù hợp, `finally` vẫn chạy khi cấu trúc bị rời, rồi ngoại lệ được truyền lên tầng gọi nếu `finally` hoàn tất bình thường.

Một cảnh báo khác: nếu chính `catch` hoặc `finally` đi kèm **ném ngoại lệ mới**, đường thoát của cấu trúc có thể thay đổi. Ví dụ `finally` viết thủ công ném lỗi khác có thể che cả lỗi khởi tạo B đã được lưu suppression. Vì vậy không nên thêm vào `finally` một thao tác có khả năng lỗi mà không xem xét cách xử lý/bảo toàn lỗi chính.

## 12. So sánh với `try/finally` thủ công

Trước Java 7, việc đóng một số tài nguyên thường viết bằng `finally`, chẳng hạn:

~~~java
// Minh họa không khuyến nghị cho tài nguyên AutoCloseable.
// Resource r = null đã được khai báo tại đây.
try {
    r = openResource();
    use(r);
} finally {
    if (r != null) {
        r.close();
    }
}
~~~

Khi chỉ quản lý một tài nguyên và tất cả thao tác thành công, cách này có thể hoạt động. Nhưng khi có **nhiều tài nguyên** và một tài nguyên ở giữa thất bại, bạn phải tự theo dõi những tài nguyên trước đó đã tạo thành công, đóng theo thứ tự phù hợp, bắt lỗi từ `close()` và bảo toàn lỗi chính.

Ví dụ thủ công với hai biến:

~~~java
// Mã khung minh họa; A/B đều AutoCloseable và các hàm có khai báo phù hợp.
A a = null;
B b = null;
try {
    a = openA();
    b = openB();
    work(a, b);
} finally {
    if (b != null) b.close();
    if (a != null) a.close();
}
~~~

Nhìn thoáng qua đã thấy rủi ro: nếu `b.close()` ném, **dòng `a.close()` có thể không được thực hiện**; nếu `work` hoặc `openB` đã ném, ngoại lệ từ `close()` có thể che lỗi ban đầu. Muốn viết đúng và bảo toàn tất cả thông tin cần nhiều mã xử lý phụ.

`try-with-resources` giúp loại bỏ những lỗi thủ công phổ biến đó, **nhưng không sửa được một constructor/factory cấp phát dở và không tự thu dọn phần tài nguyên nội bộ của chính nó khi thất bại**.

## 13. Checked exception và hợp đồng `close()`

`AutoCloseable.close()` khai báo `throws Exception`; các lớp con có thể thu hẹp kiểu ngoại lệ hoặc không ném checked exception. Chẳng hạn `Closeable.close()` khai báo `throws IOException`. Vì vậy khi viết `try-with-resources`, compiler xét cả:

- kiểu checked exception có thể được ném trong biểu thức khởi tạo tài nguyên;
- kiểu checked exception có thể phát sinh từ phần thân;
- kiểu checked exception mà `close()` của các tài nguyên có thể truyền ra.

Với ví dụ `ResourceInitFailureDemo`, cả constructor lẫn `close()` khai báo `throws IOException`, và `catch (IOException ex)` ở `main` là hợp lệ. Nếu bỏ `catch` đó và không khai báo `throws IOException` ở `main`, compiler sẽ báo lỗi do `IOException` có thể thoát ra.

Ví dụ chữ ký phương thức:

~~~java
// Đoạn minh họa đặt trong một lớp.
static void workWithResources() throws IOException {
    try (Resource a = openA(); Resource b = openB()) {
        doWork(a, b);
    }
}
~~~

Đoạn này **không độc lập**, phụ thuộc các kiểu/hàm được khai báo phù hợp. `throws IOException` thông báo khả năng lỗi, nhưng **không tự đóng tài nguyên** và không thay thế cấu trúc `try-with-resources`. Chính phần `try (...)` thực hiện quản lý tự động.

Đối với tài nguyên có `close()` khai báo `throws Exception`, phương thức bao ngoài có thể cần xử lý hoặc khai báo `Exception` (hay kiểu checked được suy luận cụ thể) nếu không có `catch` phù hợp. Không nên tùy tiện khai báo `throws Exception` cho mọi API; thiết kế tài nguyên với loại checked exception sát nghĩa sẽ giúp người dùng dễ xử lý hơn.

## 14. Quy tắc riêng khi có nhiều lỗi đóng tài nguyên

Khi một initializer hoặc body đã ném ngoại lệ chính, các lỗi từ việc đóng những tài nguyên đã khởi tạo thành công thường được **suppressed** vào ngoại lệ chính. Ví dụ:

~~~text
B khởi tạo lỗi          <- ngoại lệ chính
A.close lỗi            <- suppressed của lỗi B
~~~

Nếu **không có lỗi khởi tạo hoặc body** và trong lúc đóng có nhiều lỗi, ngoại lệ từ tài nguyên được đóng trước (theo thứ tự ngược khai báo) có thể trở thành **ngoại lệ chính**, còn lỗi từ các tài nguyên đóng sau là suppressed.

Ví dụ với thứ tự khai báo A, B, C và body hoàn tất:

~~~text
C.close lỗi            <- ngoại lệ chính lúc đóng
B.close lỗi            <- suppressed
A.close lỗi            <- suppressed
~~~

Cơ chế này giúp việc đóng những tài nguyên còn lại vẫn được thực hiện dù một lần đóng đã thất bại, đồng thời tránh bỏ mất các lỗi phụ. Nó không bảo đảm rằng thao tác đóng thành công; nó chỉ bảo toàn đường đi và thông tin lỗi theo quy tắc của Java.

Một số đối tượng `Throwable` tùy chỉnh có thể tắt tính năng suppression; trong tình huống đó `getSuppressed()` có thể không lưu lỗi phụ như trường hợp mặc định. Với `IOException` và `RuntimeException` được tạo thông thường, suppression được bật.

## 15. Phân biệt ba trường hợp dễ nhầm

### 15.1. Initializer ném exception

~~~java
try (Resource a = openA();
     Resource b = openB()) {
    work(a, b);
}
~~~

Nếu `openB()` ném: A được đóng, B không được coi là đã tạo thành công, body không chạy.

### 15.2. Initializer trả về `null`

~~~java
// Mã minh họa với phương thức maybeOpen() có thể trả null.
try (Resource a = maybeOpen()) {
    System.out.println("Đã vào body");
}
~~~

Nếu `maybeOpen()` **trả về `null` mà không ném**, body vẫn chạy và Java chỉ bỏ qua lệnh đóng `a` vì `a == null`. Nếu code body truy cập `a`, có thể phát sinh `NullPointerException`; đó là lỗi ở body, khác với lỗi initializer.

### 15.3. Constructor mở tài nguyên con rồi ném

~~~java
try (CompositeResource r = new CompositeResource(path)) {
    use(r);
}
~~~

Nếu constructor `CompositeResource` đã mở stream nội bộ rồi ném, `r` không được gán một đối tượng hoàn chỉnh, nên Java **không gọi `r.close()`**. Constructor/factory phải tự thu dọn những tài nguyên con nó đã cấp phát. Đây là nguyên nhân rò rỉ tài nguyên có thể xảy ra **dù caller có dùng `try-with-resources`**.

## 16. Checklist thiết kế và kiểm thử

Khi viết API mở tài nguyên hoặc đoạn `try-with-resources` có nhiều tài nguyên, hãy kiểm tra:

1. **Thứ tự mở/khởi tạo:** tài nguyên nào phụ thuộc tài nguyên trước? Thao tác mở có thể ném ngoại lệ gì?
2. **Ownership rõ ràng:** sau khi mở thành công, ai chịu trách nhiệm đóng? Nếu mở không hoàn tất, factory đã dọn phần được cấp phát chưa?
3. **Điểm thất bại ở từng initializer:** thử lỗi ở tài nguyên đầu, giữa và cuối; xác minh những tài nguyên trước đó được đóng ngược thứ tự.
4. **Phần thân không chạy khi khởi tạo chưa xong:** tránh kỳ vọng code trong body sẽ thực hiện bước rollback cho lỗi initializer.
5. **Lỗi trong `close()`:** kiểm tra lỗi chính và `getSuppressed()`, nhất là khi vừa mở tài nguyên sau bị lỗi vừa đóng tài nguyên trước bị lỗi.
6. **`catch` xử lý đúng kiểu:** lỗi từ initializer là checked/unchecked theo loại thực tế; handler phải có chiến lược xử lý hoặc truyền tiếp.
7. **`finally` của cấu trúc bên ngoài:** đảm bảo logic cleanup thêm không che lỗi chính hoặc ném ngoại lệ mới một cách vô ý.
8. **Không dùng GC/`finalize()` để chữa rò rỉ:** đóng chủ động và thiết kế đúng ownership mới là giải pháp.
9. **Không trả về đối tượng lỗi giả như một tài nguyên hợp lệ:** nếu factory dùng `null` báo thất bại, tài liệu phải làm rõ; tốt hơn là dùng API có hợp đồng minh bạch cho kết quả rỗng hoặc ném ngoại lệ.
10. **Báo lỗi đầy đủ:** log đối tượng ngoại lệ để giữ stack trace, cause và suppressed; không chỉ lưu thông điệp lỗi chính.

Nếu thiết kế có nhiều tài nguyên cần đóng, đưa chúng vào **cùng một `try-with-resources` hoặc các phạm vi lồng hợp lý** để Java quản lý cleanup. Đừng tự đóng lại các tài nguyên đã được cơ chế này quản lý nếu không có yêu cầu cụ thể và hiểu rõ hậu quả.

## 17. Những lỗi hiểu nhầm phổ biến

| Nhầm lẫn | Thực tế | Cách sửa |
| --- | --- | --- |
| B tạo thất bại nên A không được đóng vì body chưa chạy | Những tài nguyên đã khởi tạo trước đó vẫn được đóng. | Theo dõi thứ tự tạo và đóng ngược. |
| B constructor đã chạy một phần nên caller chắc chắn sẽ gọi `B.close()` | `B` chưa được tạo hoàn chỉnh; caller không có resource reference để đóng tự động. | Constructor/factory tự dọn tài nguyên con khi thất bại. |
| Nếu `openB()` lỗi, Java vẫn thử `openC()` | Đánh giá danh sách resource dừng tại điểm ném. | Không phụ thuộc vào side effect của các initializer phía sau. |
| Body có `finally` tự viết nên chắc chắn được thực hiện khi initializer lỗi | Nếu body chưa bắt đầu, những lệnh ở trong body không chạy. | Đặt cleanup tài nguyên cấp phát dở tại factory; dùng `try-with-resources`. |
| Lỗi `A.close()` thay thế lỗi khởi tạo B | `try-with-resources` thông thường lưu lỗi đóng dưới dạng suppressed của lỗi chính. | Xem `getSuppressed()`. |
| `catch` được chạy trước khi A được đóng | Các tài nguyên được đóng trước `catch` đi kèm. | Quan sát thứ tự log trong ví dụ. |
| Chỉ cần `try(r)` dù `r` được mở từ trước và mở thất bại | Khi mở trước `try` đã ném, cấu trúc `try` chưa bắt đầu. | Xử lý lỗi tại điểm mở hoặc mở trong resource header khi phù hợp. |
| Trả `null` từ initializer tương đương ném ngoại lệ | Trả `null` bình thường không ngăn body chạy; chỉ không gọi `close` lên `null`. | Thiết kế và kiểm tra hợp đồng factory rõ ràng. |
| `throws IOException` tự động dọn cleanup | `throws` chỉ công bố khả năng lỗi checked. | Dùng `try-with-resources` hoặc cleanup được quản lý. |
| Chỉ quan tâm lỗi khởi tạo, bỏ qua lỗi đóng | Có thể mất thông tin tài nguyên chưa đóng thành công. | Giữ stack trace/suppressed trong log và kiểm thử. |

## 18. Bài tập tự kiểm tra

**Câu 1.** `try (A a = openA(); B b = openB(); C c = openC())`: A thành công, B ném `IOException`. Java gọi những `close()` nào?

**Đáp án:** Gọi `a.close()`. Không gọi `b.close()` (B chưa tạo xong) hoặc `c.close()` (C chưa được tạo).

**Câu 2.** Khi B mở thất bại, thân `try` có chạy một vài lệnh đầu không?

**Đáp án:** Không. Toàn bộ danh sách tài nguyên phải khởi tạo bình thường rồi mới đến thân `try`.

**Câu 3.** A mở thành công, B mở lỗi, rồi A đóng cũng lỗi. Ngoại lệ nào được ưu tiên?

**Đáp án:** Lỗi từ B là ngoại lệ chính; lỗi đóng A được thêm vào suppressed của lỗi B trong trường hợp suppression thông thường.

**Câu 4.** Nếu C mở lỗi sau khi A và B đã mở thành công, thứ tự đóng là gì?

**Đáp án:** B rồi A.

**Câu 5.** `catch` gắn sau `try-with-resources` chạy trước hay sau `close()` của A/B?

**Đáp án:** Sau việc đóng các tài nguyên đã khởi tạo cần đóng.

**Câu 6.** Nếu ngay initializer của A lỗi, Java có gọi `A.close()` không?

**Đáp án:** Không, vì resource variable A chưa nhận được một đối tượng đã tạo thành công. Nếu constructor A tự cấp phát tài nguyên con, code trong constructor/factory phải tự xử lý phần ấy.

**Câu 7.** Nếu `openA()` trả về `null` nhưng không ném, body có chạy không?

**Đáp án:** Có thể chạy; Java không gọi `close()` đối với tham chiếu null.

**Câu 8.** `try (InputStream in = Files.newInputStream(path))` và `InputStream in = Files.newInputStream(path); try (in) { ... }` có cùng thời điểm mở stream không?

**Đáp án:** Không. Cách đầu mở trong resource header, cách sau mở trước khi vào cấu trúc `try (in)`. Cả hai đều quản lý tài nguyên đã mở thành công, nhưng vị trí phát sinh lỗi mở khác nhau.

**Câu 9.** Một constructor mở stream bên trong rồi ném trước khi trả đối tượng. Caller có thể chỉ trông vào `try-with-resources` để đóng stream ấy không?

**Đáp án:** Không; caller chưa sở hữu đối tượng được tạo hoàn chỉnh. Constructor/factory phải có chính sách cleanup khi thất bại.

**Câu 10.** Vì sao nên ghi log cả `getSuppressed()`?

**Đáp án:** Vì lỗi đóng tài nguyên xảy ra sau lỗi khởi tạo/body vẫn là thông tin quan trọng, dù không thay thế ngoại lệ chính.

## Tài liệu tham khảo

- [Java Language Specification 17 — §14.20.3, `try-with-resources`](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.3): cú pháp và ngữ nghĩa toàn bộ cấu trúc.
- [Java Language Specification 17 — §14.20.3.1, Basic `try-with-resources`](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.3.1): mô hình xử lý khởi tạo, đóng tự động và suppressed exceptions.
- [Java Language Specification 17 — §14.20.3.2, Extended `try-with-resources`](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.3.2): mối quan hệ với `catch` và `finally` đi kèm.
- [Java SE 17 API — `AutoCloseable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/AutoCloseable.html): hợp đồng `close()`, kiểu ngoại lệ và cách dùng với `try-with-resources`.
- [Java SE 17 API — `Throwable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html): `getSuppressed()`, `addSuppressed()`, `getCause()`.
- [Oracle Java Tutorials — The `try-with-resources` Statement](https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html): giải thích đóng tài nguyên và suppressed exceptions; ví dụ tutorial lịch sử, quy tắc nền tảng vẫn áp dụng trên Java 17.

**Ghi nhớ:** nếu **resource initializer ném ngoại lệ**, Java bỏ qua mọi initializer phía sau và phần thân `try`, nhưng vẫn **đóng các tài nguyên trước đó đã khởi tạo thành công theo thứ tự ngược**, giữ lỗi khởi tạo làm ngoại lệ chính và lưu lỗi đóng phụ qua suppressed exceptions. Riêng tài nguyên đang tạo dở bên trong constructor/factory phải được **chính nơi cấp phát quản lý an toàn**, vì caller không có đối tượng hoàn chỉnh để đóng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="try-with-resources-008">Tài nguyên null và lời gọi close tự động</a>

<details>
<summary>Xem chi tiết</summary>

# Tài nguyên `null` và lời gọi `close()` tự động trong Java try-with-resources

Trong Java, **try-with-resources** là cấu trúc `try (...) { ... }` dùng để quản lý tài nguyên triển khai `AutoCloseable`. Khi rời khối, Java tự động **cố gắng gọi `close()` trên các tài nguyên đã khởi tạo thành công và có tham chiếu khác `null`**. Nếu biến tài nguyên có giá trị **`null`**, Java **bỏ qua việc gọi `close()` cho tài nguyên đó**; chỉ riêng việc tài nguyên là `null` **không** làm câu lệnh try-with-resources ném `NullPointerException`.

Quy tắc ngắn gọn:

> **Tài nguyên `null` thì không đóng; tài nguyên khác `null` thì được cố đóng khi rời phạm vi.** Việc tự động đóng không làm biến `null` thành đối tượng hợp lệ và không ngăn `NullPointerException` nếu mã trong thân `try` cố dùng tham chiếu `null`.

Bài học này giải thích bản chất của lời gọi `close()` tự động, sự khác nhau giữa **tài nguyên `null`** và **khởi tạo tài nguyên thất bại vì exception**, cách Java đóng nhiều tài nguyên, quan hệ với `catch`/`finally`, lỗi đóng tài nguyên, các quy tắc checked exception, và cách thiết kế API tránh những tình huống khó hiểu.

## 1. Nhắc lại cấu trúc try-with-resources

Một cấu trúc thông thường:

```java
try (SomeResource resource = openResource()) {
    useResource(resource);
}
```

Đây là **mã khung minh họa**, trong đó `SomeResource` phải triển khai `AutoCloseable` và hai phương thức phụ cần được định nghĩa. Tại runtime, Java tiến hành:

1. Đánh giá biểu thức `openResource()` và khởi tạo biến `resource`.
2. Nếu khởi tạo thành công, đi vào thân `try`.
3. Khi rời thân `try`, dù thành công, có `return` hoặc exception, Java **cố đóng** tài nguyên nếu tham chiếu khác `null`.
4. Sau việc đóng, Java chuyển tới `catch` hoặc `finally` gắn với cấu trúc đó nếu có, hoặc tiếp tục/lan truyền theo đường tương ứng.

Thứ tự khởi tạo tài nguyên là từ **trái sang phải**, và thứ tự đóng là **ngược lại, từ phải sang trái**. Java chỉ quản lý những tài nguyên đã được khởi tạo thành công; điều kiện tham chiếu `null` quyết định có thực sự gọi `close()` trên từng tài nguyên hay không.

**`AutoCloseable` không có nghĩa việc đóng chắc chắn thành công.** `close()` có thể ném exception, và try-with-resources sẽ áp dụng các quy tắc về primary và suppressed exceptions để xử lý trường hợp đó.

## 2. Một tài nguyên được gán `null` có hợp lệ không?

Có. Java cho phép khai báo một biến tài nguyên bằng biểu thức có kiểu phù hợp và giá trị runtime là `null`. Ví dụ:

```java
try (MyResource resource = null) {
    System.out.println("Thân try vẫn chạy");
}
```

Đây là **đoạn minh họa**, cần lớp `MyResource` triển khai `AutoCloseable` để biên dịch. Trong trường hợp này:

- Phần khai báo tài nguyên được đánh giá bình thường và có kết quả tham chiếu `null`.
- Thân `try` **vẫn chạy**; Java không tự coi tài nguyên `null` là một điều kiện thất bại.
- Khi rời khối, Java **không gọi** `resource.close()` vì tham chiếu là `null`.
- Không có `NullPointerException` chỉ do thao tác cleanup tự động.
- Nếu ngay trong thân `try` bạn gọi `resource.read()`, Java có thể ném `NullPointerException` do **dereference `null`**, một vấn đề khác với cơ chế đóng.

Một số API thiết kế theo cách “không có tài nguyên” bằng `null`, nhưng đây không phải mặc định tốt cho mọi chương trình. Trong một API cần tài nguyên bắt buộc, trả `null` có thể là sai hợp đồng và nên được xử lý riêng, ví dụ bằng thông báo lỗi rõ ràng.

## 3. Ví dụ Java 17 hoàn chỉnh: `null` không bị đóng

Lưu chương trình sau thành **`NullResourceDemo.java`**:

```java
public class NullResourceDemo {
    static class TrackedResource implements AutoCloseable {
        private final String name;

        TrackedResource(String name) {
            this.name = name;
            System.out.println("Tạo: " + name);
        }

        void work() {
            System.out.println("Dùng: " + name);
        }

        @Override
        public void close() {
            System.out.println("Đóng: " + name);
        }
    }

    static TrackedResource openOrNull(boolean available) {
        if (!available) {
            System.out.println("Không tạo tài nguyên");
            return null;
        }
        return new TrackedResource("R");
    }

    static void execute(boolean available) {
        try (TrackedResource resource = openOrNull(available)) {
            System.out.println("resource == null: " + (resource == null));
            if (resource != null) {
                resource.work();
            } else {
                System.out.println("Bỏ qua thao tác cần tài nguyên");
            }
        }
        System.out.println("Sau try-with-resources");
    }

    public static void main(String[] args) {
        execute(false);
        System.out.println("---");
        execute(true);
    }
}
```

Biên dịch/chạy bằng Java 17 trở lên:

```bash
javac -encoding UTF-8 --release 17 NullResourceDemo.java
java NullResourceDemo
```

**Stdout chính xác** với console UTF-8:

```text
Không tạo tài nguyên
resource == null: true
Bỏ qua thao tác cần tài nguyên
Sau try-with-resources
---
Tạo: R
resource == null: false
Dùng: R
Đóng: R
Sau try-with-resources
```

Với `execute(false)`, biểu thức khởi tạo trả `null`. Thân `try` chạy, nhánh xử lý “không có tài nguyên” được chọn, và không có dòng `Đóng: R` vì không có object để đóng.

Với `execute(true)`, tài nguyên `TrackedResource` được tạo, sử dụng và đóng khi rời khối. Lệnh sau cấu trúc chỉ chạy sau bước đóng, nếu `close()` hoàn tất bình thường.

Bài học cốt lõi: **Java tự động kiểm tra tài nguyên khác `null` trước khi gọi `close()`, không cần viết một `if (resource != null)` chỉ để phòng NPE ở bước đóng**. Nhưng kiểm tra trước khi **sử dụng** tài nguyên có thể vẫn cần nếu hợp đồng cho phép `null`.

## 4. Null-safe cleanup không phải null-safe usage

Xét chương trình **`NullUsageDemo.java`**:

```java
public class NullUsageDemo {
    static class Resource implements AutoCloseable {
        void use() {
            System.out.println("Đã dùng");
        }

        @Override
        public void close() {
            System.out.println("Đã đóng");
        }
    }

    public static void main(String[] args) {
        try (Resource resource = null) {
            System.out.println("Vào try");
            resource.use();
            System.out.println("Sau use");
        } catch (NullPointerException e) {
            System.out.println("Bắt NullPointerException");
        }
        System.out.println("Sau try/catch");
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 --release 17 NullUsageDemo.java
java NullUsageDemo
```

**Stdout chính xác:**

```text
Vào try
Bắt NullPointerException
Sau try/catch
```

Lệnh `resource.use()` cố gọi instance method trên tham chiếu `null` và ném `NullPointerException`. Sau khi thân `try` bị ngắt, Java xét cleanup, thấy resource là `null` nên **không gọi `close()`**. `catch` nhận `NullPointerException` do thao tác `use`, không phải do đóng tự động.

Đừng suy luận sai rằng “`try-with-resources` xử lý được `null`” có nghĩa mọi thao tác lên đối tượng `null` đều an toàn. Cơ chế chỉ bỏ qua **lời gọi `close()` được tạo tự động**, chứ không kiểm tra mọi biểu thức trong thân `try`.

Nếu tài nguyên là bắt buộc, một thiết kế rõ ràng hơn có thể dùng `Objects.requireNonNull` tại ranh giới để thất bại sớm với ngữ cảnh:

```java
import java.util.Objects;

class RequiredResource {
    static void run(AutoCloseable resource) throws Exception {
        Objects.requireNonNull(resource, "resource là bắt buộc");
        try (resource) {
            System.out.println("Có tài nguyên hợp lệ");
        }
    }
}
```

Đây là **lớp minh họa không có `main`**, dùng cú pháp Java 9+ cho biến tài nguyên đã khai báo trước. Kiểm tra `requireNonNull` xảy ra **trước khi vào `try-with-resources`**, nhằm bộc lộ rõ hợp đồng “không chấp nhận `null`”. Khai báo `throws Exception` xuất phát từ kiểu tĩnh `AutoCloseable.close()`, có thể ném checked `Exception`.

## 5. Vì sao Java không gọi `null.close()`?

Có thể hình dung try-with-resources đơn giản như đoạn cleanup sau:

```java
Resource resource = openOrNull();
try {
    doWork(resource);
} finally {
    if (resource != null) {
        resource.close();
    }
}
```

Đây là **mã giả theo ý tưởng**, không phải bản dịch đầy đủ của mọi ngữ nghĩa try-with-resources. Nó giúp thấy **kiểm tra `resource != null`** trước khi đóng. Cơ chế thật của Java phức tạp hơn: phải bảo toàn ngoại lệ chính, xử lý ngoại lệ khi `close()` thất bại và đóng nhiều tài nguyên theo đúng thứ tự.

Đừng dùng đoạn khung này làm cách thay thế trực tiếp cho try-with-resources nếu cần giữ exception gốc. Nếu `doWork` ném exception A và `close` cũng ném B, một `finally` đơn giản như trên có thể làm **B che mất A**. Trong khi đó, try-with-resources giữ A làm ngoại lệ chính và thường gắn B dưới dạng **suppressed exception**.

Điểm rút ra: việc **bỏ qua đóng với tham chiếu `null`** chỉ là một phần của hợp đồng quản lý tài nguyên; việc quản lý lỗi đồng thời còn quan trọng không kém.

## 6. Phân biệt `null` với khởi tạo tài nguyên thất bại

Hai tình huống sau hoàn toàn khác:

**Tình huống A: factory trả về `null` bình thường.**

```java
Resource resource = openOrNull(); // Trả về null.
```

Trong try-with-resources, biểu thức khởi tạo hoàn tất bình thường, biến `resource` được gán `null`, thân `try` bắt đầu. Khi rời khối, tài nguyên `null` bị bỏ qua ở bước đóng.

**Tình huống B: factory ném exception khi khởi tạo.**

```java
Resource resource = openOrThrow(); // Ném exception.
```

Ở trường hợp này, biểu thức khởi tạo không hoàn tất bình thường. **Thân `try` chưa bắt đầu**; Java thực hiện cleanup đối với các tài nguyên **đã khởi tạo thành công trước đó trong cùng resource specification**, nếu có, rồi lan truyền exception theo luật xử lý.

Không nên coi “trả `null`” và “ném exception” là hai cách báo thất bại tương đương. Chúng khác nhau về luồng thực thi, hợp đồng API và khả năng tạo một kết quả hợp lệ.

## 7. Nhiều tài nguyên có một biến `null`: các biến khác vẫn được đóng

Một resource `null` không cản trở việc đóng những tài nguyên khác. Khi khai báo nhiều biến trong `try (...)`, Java xét chúng theo **thứ tự ngược khai báo**, bỏ qua biến nào có giá trị `null`.

Lưu chương trình **`MultipleNullResourcesDemo.java`**:

```java
public class MultipleNullResourcesDemo {
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
             Resource middle = null;
             Resource last = new Resource("C")) {
            System.out.println("Trong try: middle null = " + (middle == null));
        }
        System.out.println("Sau try");
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 --release 17 MultipleNullResourcesDemo.java
java MultipleNullResourcesDemo
```

**Stdout chính xác:**

```text
Mở A
Mở C
Trong try: middle null = true
Đóng C
Đóng A
Sau try
```

Tài nguyên A và C được tạo, còn `middle` được gán `null`. Việc đóng đi từ C → middle (bỏ qua) → A. Vì thế không có dòng “Đóng middle”. Java vẫn đóng C và A dù resource đứng giữa bằng `null`.

Nếu thân `try` ném exception, tài nguyên khác `null` đã khởi tạo thành công vẫn được cố đóng theo thứ tự này trước khi exception được chuyển đến handler phù hợp.

## 8. Tài nguyên trước đã mở, tài nguyên sau trả `null`

Khi một factory nằm ở giữa danh sách trả `null`, Java vẫn tiếp tục **khởi tạo các resource đứng sau**. Sự kiện trả `null` không khiến initialization bị bỏ dở.

Ví dụ:

```java
try (Resource a = open("A");
     Resource optional = optionalResource(); // Có thể null
     Resource b = open("B")) {
    process(a, optional, b);
}
```

Đây là **mã khung minh họa**, các hàm và lớp phải được định nghĩa. Nếu `optionalResource()` trả `null` bình thường, Java vẫn gọi `open("B")` rồi vào thân `try`. Chỉ khi một biểu thức khởi tạo **ném exception** mới có đường đi thoát bất thường.

Hàm `process` cần được thiết kế để biết `optional` có thể `null`. Nếu nó dereference `optional` mà không kiểm tra, `NullPointerException` có thể phát sinh trong thân `try`; khi đó A và B vẫn được đóng bình thường theo cấu trúc.

Đây là điểm khác với một số cấu trúc khác của lập trình có điều kiện: **`null` chỉ là giá trị tham chiếu**, không phải tín hiệu mà câu lệnh try-with-resources tự coi là “bỏ qua toàn bộ block”.

## 9. Nếu khởi tạo tài nguyên thứ hai ném exception, tài nguyên đầu được đóng

Lưu **`InitializationFailureDemo.java`**:

```java
public class InitializationFailureDemo {
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

    static Resource open(String name) {
        if ("B".equals(name)) {
            System.out.println("Không mở được B");
            throw new IllegalStateException("Lỗi khởi tạo B");
        }
        return new Resource(name);
    }

    public static void main(String[] args) {
        try (Resource a = open("A");
             Resource b = open("B");
             Resource c = open("C")) {
            System.out.println("Trong try");
        } catch (IllegalStateException e) {
            System.out.println("Catch: " + e.getMessage());
        }
        System.out.println("Sau cấu trúc");
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 InitializationFailureDemo.java
java InitializationFailureDemo
```

**Stdout chính xác:**

```text
Mở A
Không mở được B
Đóng A
Catch: Lỗi khởi tạo B
Sau cấu trúc
```

Resource A đã khởi tạo thành công; B ném `IllegalStateException` trước khi trả về một đối tượng resource; C chưa được khởi tạo. Java cố đóng A rồi truyền lỗi tới `catch`. Không có dòng “Trong try”, “Mở C” hoặc “Đóng C”.

Ví dụ này cho thấy try-with-resources xử lý an toàn **một phần danh sách tài nguyên đã được khởi tạo**, không chỉ trường hợp tất cả đã được mở. Khi một resource trả `null` thay vì ném exception, luồng khởi tạo không bị ngắt theo cách này.

## 10. `close()` được gọi khi nào?

Với tài nguyên không `null`, Java cố gọi `close()` khi rời phạm vi tài nguyên sau thân `try`. Các đường đi phổ biến:

| Đường rời thân `try` | Tài nguyên khác `null` có được cố đóng? | Sau đóng đi đâu? |
| --- | --- | --- |
| Thân `try` hoàn tất bình thường | Có | Tiếp tục sau try-with-resources nếu close bình thường |
| Thân `try` ném exception | Có | Exception được xét bởi `catch` ngoài phạm vi tài nguyên |
| Thân `try` thực hiện `return` | Có | Quay lại caller nếu đóng hoàn tất bình thường |
| Thân `try` thực hiện `break` hoặc `continue` hợp lệ | Có | Chuyển điều khiển theo cấu trúc nếu đóng bình thường |
| Khởi tạo resource sau ném exception | Các resource đã khởi tạo trước đó có | Lan truyền tới handler phù hợp |
| Tài nguyên cụ thể bằng `null` | **Không gọi `close()` cho tài nguyên đó** | Các tài nguyên còn lại vẫn theo quy tắc |

**Không có bảo đảm tuyệt đối** khi JVM bị chấm dứt cưỡng bức, tiến trình crash, hoặc một số tình huống nghiêm trọng khác. “Tự động” ở đây là theo các quy tắc thực thi Java khi rời cấu trúc, không phải phép màu bảo đảm tài nguyên vật lý luôn đóng thành công.

Một chi tiết quan trọng: với cấu trúc có `catch` và `finally` gắn trực tiếp với try-with-resources, **đóng tài nguyên hoàn tất/được thử trước khi handler `catch` hoặc khối `finally` ấy chạy**.

## 11. Thân `try` ném exception, tài nguyên khác `null` được đóng

Ví dụ **`CloseOnFailureDemo.java`**:

```java
public class CloseOnFailureDemo {
    static class Resource implements AutoCloseable {
        @Override
        public void close() {
            System.out.println("Đóng resource");
        }
    }

    public static void main(String[] args) {
        try (Resource resource = new Resource()) {
            System.out.println("Trong try");
            throw new IllegalArgumentException("Lỗi nghiệp vụ");
        } catch (IllegalArgumentException e) {
            System.out.println("Catch: " + e.getMessage());
        } finally {
            System.out.println("Finally");
        }
        System.out.println("Kết thúc");
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 --release 17 CloseOnFailureDemo.java
java CloseOnFailureDemo
```

**Stdout chính xác:**

```text
Trong try
Đóng resource
Catch: Lỗi nghiệp vụ
Finally
Kết thúc
```

Dù thân `try` ném exception, lời gọi `close()` vẫn diễn ra trước `catch` của cấu trúc. Trong trường hợp `resource = null`, dòng “Đóng resource” sẽ không có; handler vẫn bắt exception ném từ thân `try`.

## 12. Khi `close()` tự ném exception

`AutoCloseable.close()` được phép ném exception. Lúc đó cần xét có **exception chính** từ thân `try` trước hay không.

### 12.1. Chỉ `close()` thất bại

Lưu **`CloseOnlyFailureDemo.java`**:

```java
public class CloseOnlyFailureDemo {
    static class Resource implements AutoCloseable {
        @Override
        public void close() {
            System.out.println("Trong close");
            throw new IllegalStateException("Không đóng được");
        }
    }

    public static void main(String[] args) {
        try (Resource resource = new Resource()) {
            System.out.println("Thân try thành công");
        } catch (IllegalStateException e) {
            System.out.println("Catch: " + e.getMessage());
        }
        System.out.println("Sau try/catch");
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 CloseOnlyFailureDemo.java
java CloseOnlyFailureDemo
```

**Stdout chính xác:**

```text
Thân try thành công
Trong close
Catch: Không đóng được
Sau try/catch
```

Ở đây thân `try` hoàn tất, nhưng `close()` ném exception. Lỗi đóng trở thành **exception chính** của cấu trúc. `catch` nhận lỗi đó. Không thể kết luận rằng toàn bộ thao tác đã thành công chỉ vì thân `try` hoàn tất.

Nếu tài nguyên `resource` thực sự bằng `null`, Java không gọi `close()`, nên không có lỗi đóng từ tài nguyên ấy.

### 12.2. Thân `try` và `close()` cùng thất bại

Lưu **`PrimarySuppressedDemo.java`**:

```java
public class PrimarySuppressedDemo {
    static class Resource implements AutoCloseable {
        @Override
        public void close() {
            System.out.println("Trong close");
            throw new IllegalStateException("Lỗi đóng");
        }
    }

    public static void main(String[] args) {
        try (Resource resource = new Resource()) {
            System.out.println("Trong try");
            throw new IllegalArgumentException("Lỗi chính");
        } catch (IllegalArgumentException e) {
            System.out.println("Primary: " + e.getMessage());
            System.out.println("Suppressed: "
                    + e.getSuppressed().length);
            System.out.println("Phụ: "
                    + e.getSuppressed()[0].getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 PrimarySuppressedDemo.java
java PrimarySuppressedDemo
```

**Stdout chính xác:**

```text
Trong try
Trong close
Primary: Lỗi chính
Suppressed: 1
Phụ: Lỗi đóng
```

Ngoại lệ `IllegalArgumentException` từ thân `try` là **primary**. Ngoại lệ `IllegalStateException` từ `close()` được gắn vào primary qua `addSuppressed` theo cơ chế try-with-resources, truy cập bằng `getSuppressed()`.

Điều này khác kiểu cleanup thủ công trong `finally`: nếu `finally` ném exception mới, nó có thể **che mất lỗi ban đầu**. Với try-with-resources, Java giữ lỗi chính và lưu lỗi đóng như một lỗi phụ.

**Nếu resource bằng `null`, `close()` không chạy, nên không có suppressed exception từ resource đó.** Không suy đoán rằng cứ một exception trong thân `try` là chắc chắn có phần tử suppressed; chỉ truy `getSuppressed()[0]` sau khi kiểm tra mảng không rỗng trong mã thực.

## 13. Nhiều tài nguyên: lỗi đóng của đối tượng `null` không tồn tại

Giả sử resource A khác `null`, B bằng `null`, C khác `null`. Khi thân `try` thất bại, Java cố đóng C, bỏ qua B, rồi đóng A. Nếu `close()` của C và A đều ném exception, hai lỗi đóng có thể được gắn suppressed vào exception chính theo thứ tự đóng.

Không có lỗi đóng “giả” cho B: vì Java không gọi `close()` trên B, B không tạo thêm một suppressed exception do cleanup.

Ví dụ khung:

```java
try (Resource a = open("A");
     Resource b = null;
     Resource c = open("C")) {
    throw new IllegalArgumentException("Work failed");
}
```

Đây là **khung minh họa**, cần lớp `Resource` và hàm `open` hợp lệ. Nếu `close()` của A/C ném lỗi, có thể kiểm tra `getSuppressed()` ở handler tương ứng. Thứ tự đóng **C trước A** không đổi vì B bằng `null`.

Cách suy luận đúng là xét **mỗi tài nguyên đã khởi tạo thành công và khác `null`**, rồi mới xét các exception do các thao tác đóng đó ném.

## 14. `try (resource)` với biến đã khai báo trước: `null` vẫn được bỏ qua

Từ **Java 9**, resource specification có thể dùng một biến tham chiếu tài nguyên đã được khai báo trước đó, nếu biến đó là `final` hoặc *effectively final* và đã được gán hợp lệ. Java 17 hỗ trợ cú pháp này:

```java
Resource resource = mayReturnNull();
try (resource) {
    System.out.println("Trong try");
}
```

Đây là **mã khung**. Biến `resource` tồn tại ở phạm vi ngoài và vẫn nhìn thấy sau khối `try`. Nếu tham chiếu là `null`, Java bỏ qua việc gọi `close()`; nếu khác `null`, Java cố đóng nó khi rời block.

Ví dụ độc lập **`ExistingVariableNullDemo.java`**:

```java
public class ExistingVariableNullDemo {
    static class Resource implements AutoCloseable {
        @Override
        public void close() {
            System.out.println("Đóng");
        }
    }

    public static void main(String[] args) {
        Resource resource = null;

        try (resource) {
            System.out.println("Trong try");
        }

        System.out.println("Vẫn truy cập tên resource: "
                + (resource == null));
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 --release 17 ExistingVariableNullDemo.java
java ExistingVariableNullDemo
```

**Stdout chính xác:**

```text
Trong try
Vẫn truy cập tên resource: true
```

Không có dòng `Đóng` vì resource `null`. `resource` được khai báo ở scope ngoài nên tên vẫn dùng được sau cấu trúc, nhưng dùng `try (resource)` không đồng nghĩa mã Java cho phép gán lại biến tùy ý; cú pháp này yêu cầu `final` hoặc *effectively final*.

Nếu biến chưa được **definitely assigned** trước khi dùng trong resource specification, compiler sẽ từ chối. Nếu gán lại nó khiến biến không còn effectively final, cú pháp `try (resource)` cũng sẽ không hợp lệ. Đây là quy tắc kiểm tra **compile time**, khác với việc tham chiếu có thể bằng `null` ở runtime.

## 15. Giá trị đóng được xác định từ tài nguyên trong resource specification

Với dạng khai báo:

```java
try (Resource resource = createResource()) {
    // ...
}
```

biểu thức `createResource()` được đánh giá khi khởi tạo. Việc tài nguyên là `null` hay không được quyết định từ **giá trị tham chiếu được khởi tạo**. Java không tự đánh giá lại `createResource()` khi đến bước đóng.

Điều này quan trọng nếu factory có side effect hoặc kết quả thay đổi theo thời gian. Khởi tạo tài nguyên là một bước có tác dụng rõ ràng; không nên viết mã suy luận rằng factory sẽ được gọi lại lúc cleanup.

Với dạng Java 9+ `try (resource)`, biến là final/effectively final. Điều đó không bảo đảm **trạng thái nội bộ đối tượng** bất biến: tài nguyên vẫn có thể thay đổi trạng thái, nhưng try-with-resources cố gọi `close()` trên đối tượng mà phần resource specification đã chọn.

Đối tượng có thể đã được đóng từ trước bởi mã khác, và `close()` có thể được gọi lại khi ra khỏi `try`. Giao diện `AutoCloseable` không biến tất cả các triển khai thành hoàn toàn idempotent, nên cần xác định rõ quyền sở hữu tài nguyên và không chia sẻ đóng tùy tiện.

## 16. `null` không có nghĩa Java bỏ qua việc kiểm tra checked exception

Quy tắc `close()` tự động còn chịu **kiểm tra kiểu lúc biên dịch**. Theo API chuẩn, `AutoCloseable.close()` khai báo `throws Exception`, trong khi các lớp con có thể override với kiểu checked exception hẹp hơn hoặc không ném checked exception nào.

Ví dụ:

```java
static void useGeneric(AutoCloseable resource) throws Exception {
    try (resource) {
        System.out.println("Trong try");
    }
}
```

Đây là **phương thức minh họa**, với `resource` là tham số effectively final và dùng cú pháp Java 9+. Chữ ký `throws Exception` hợp lý vì kiểu tĩnh của `resource` là `AutoCloseable`; `close()` theo hợp đồng có thể ném `Exception`.

Nếu truyền tham chiếu `null` vào lời gọi này, **runtime sẽ bỏ qua `close()`**, nhưng compiler vẫn kiểm tra hợp đồng kiểu tĩnh của phương thức. Không nên cho rằng “vì lúc chạy biến này `null` nên có thể xóa mọi `throws` hay `catch` liên quan đến `close()`” trong tất cả trường hợp.

Nếu tự định nghĩa class implements `AutoCloseable` và override `close()` **không khai báo checked exception**, như `Resource` ở các ví dụ trên, caller làm việc với kiểu tĩnh `Resource` thường không phải xử lý `Exception` chỉ vì try-with-resources.

## 17. Khi nào nên cho phép factory trả `null`?

Một số API cũ hoặc ranh giới tích hợp có thể cho phép factory trả `null` với nghĩa “không có tài nguyên”. Try-with-resources xử lý cleanup của trường hợp này đúng theo quy tắc, nhưng thiết kế API vẫn cần trả lời:

- `null` có phải là **trạng thái dự kiến, hợp lệ** không?
- Nếu không có tài nguyên, thân `try` có công việc nào được phép tiếp tục không?
- Có cần một giá trị kết quả như `Optional` hoặc một loại trạng thái khác để biểu diễn vắng mặt rõ hơn không?
- Liệu caller có lẫn `null` với lỗi khởi tạo kỹ thuật đáng ra phải ném exception?
- Khi không có resource, liệu tác vụ phải trả kết quả sớm, báo lỗi, hay dùng một fallback thật sự hợp lệ?

Ví dụ, nếu một nguồn cấu hình là tùy chọn, không có reader có thể dẫn đến cấu hình mặc định hợp pháp. Nhưng nếu một giao dịch bắt buộc phải có kết nối, factory trả `null` rồi chương trình âm thầm chạy tiếp có thể tạo sai dữ liệu.

**Try-with-resources không tự định nghĩa ý nghĩa nghiệp vụ của `null`.** Nó chỉ cung cấp quy tắc không gọi `close()` trên `null` khi cleanup. Hãy làm rõ hợp đồng trước khi dựa vào hành vi này.

## 18. Thói quen an toàn khi mở và dùng resource

Cách viết phụ thuộc việc không có tài nguyên có được phép hay không.

### 18.1. Tài nguyên bắt buộc: kiểm tra rõ ràng

```java
import java.util.Objects;

class RequiredResourceExample {
    static void process(AutoCloseable resource) throws Exception {
        Objects.requireNonNull(resource, "resource không được null");
        try (resource) {
            // Thực hiện thao tác mà hợp đồng yêu cầu có resource.
        }
    }
}
```

Đây là lớp hợp lệ **không có `main`**. Nếu caller truyền `null`, chương trình ném `NullPointerException` ở bước kiểm tra chủ động, với message chỉ rõ tiền điều kiện. Cần lưu ý: `Objects.requireNonNull` không tự đóng gì, nó chỉ làm rõ hợp đồng.

### 18.2. Tài nguyên tùy chọn: kiểm tra trước khi sử dụng

```java
static void processOptional(Resource resource) {
    try (resource) {
        if (resource == null) {
            System.out.println("Không có dữ liệu cần xử lý");
            return;
        }
        resource.work();
    }
}
```

Đây là **phương thức minh họa** với `Resource` implements `AutoCloseable` và có method `work()`. `Resource` không khai báo checked exception ở `close()` trong ví dụ giả định. Cần bảo đảm tham số `resource` không bị gán lại, để thỏa điều kiện Java 9+.

Khi giá trị `null`, handler tự quyết định không làm gì và `return`. Java xét cleanup của `try-with-resources`, thấy tham chiếu `null` nên bỏ qua `close()`; khi khác `null`, thực thi `work()` rồi đóng.

Không nên xây API kiểu “thỉnh thoảng trả `null`, thỉnh thoảng ném lỗi” mà không giải thích rõ trường hợp nào là thiếu dữ liệu hợp lệ và trường hợp nào là sự cố kỹ thuật.

## 19. Sai lầm thường gặp

| Hiểu sai | Quy tắc đúng |
| --- | --- |
| “`try (Resource r = null)` lập tức ném `NullPointerException`.” | Không. Thân `try` vẫn chạy, và cleanup bỏ qua `close()` của resource `null`. |
| “Tài nguyên `null` vẫn được gọi `close()`, chỉ là Java tự bắt lỗi.” | Không. Java **không gọi** `close()` trên tham chiếu `null`. |
| “Có thể gọi `r.work()` thoải mái vì try-with-resources tự xử lý `null`.” | Không. Dereference `null` trong thân `try` vẫn có thể ném `NullPointerException`. |
| “Một resource ở giữa `null` thì các resource còn lại không đóng.” | Sai. Những resource khác `null` đã khởi tạo thành công vẫn được cố đóng theo thứ tự ngược. |
| “Factory trả `null` giống như constructor ném exception.” | Không. Trả `null` là hoàn tất bình thường; ném exception làm ngắt khởi tạo và đóng resource trước đó. |
| “Tài nguyên nào cũng đóng khi khởi tạo một resource sau thất bại.” | Chỉ những resource đã khởi tạo thành công trước đó được quản lý để đóng; resource chưa được tạo không có object để đóng. |
| “`close()` không thể ném lỗi vì Java tự gọi.” | Sai. `close()` có thể ném exception, trở thành primary hoặc suppressed tùy tình huống. |
| “Chỉ cần `getCause()` để xem lỗi đóng tài nguyên.” | Trong try-with-resources, lỗi đóng phát sinh khi đã có primary thường ở `getSuppressed()`. |
| “Nếu trong thân `try` có exception, chắc chắn có suppressed exception.” | Không. Nếu đóng bình thường hoặc resource là `null`, có thể không có suppressed nào. |
| “Java 9 `try (resource)` cho phép gán lại biến resource tùy ý.” | Không. Biến dùng trong resource specification phải final/effectively final và đã được gán hợp lệ. |
| “Nếu resource runtime là `null`, compiler khỏi xét checked exception của `close()`.” | Nghĩa vụ compile time được xác định từ hợp đồng kiểu tĩnh, không chỉ giá trị runtime. |
| “Try-with-resources đảm bảo close thành công trong mọi hoàn cảnh.” | Java chỉ cố gọi `close()` khi rời phạm vi; lỗi đóng, crash hoặc chấm dứt cưỡng bức vẫn có thể xảy ra. |

## 20. Tình huống tự kiểm tra

**Câu 1.** `try (Resource r = null) { System.out.println("ok"); }`, với `Resource` là `AutoCloseable`. Có in `ok` không? `close()` có được gọi không?  
**Đáp án:** Có in `ok` nếu thân `try` không ném lỗi; `close()` không được gọi trên `r` vì nó bằng `null`.

**Câu 2.** Trong thân `try`, chương trình gọi `r.read()` khi `r == null`. Có phải lỗi do cleanup tự động không?  
**Đáp án:** Không. `NullPointerException` (nếu có) phát sinh từ việc sử dụng tham chiếu `null` trong thân `try`. Bước cleanup vẫn bỏ qua resource `null`.

**Câu 3.** Có ba resource A, B, C; B bằng `null` còn A/C khác `null`. Thứ tự đóng là gì?  
**Đáp án:** C rồi A. Java xét B ở giữa nhưng không gọi `close()` vì B bằng `null`.

**Câu 4.** Factory tạo resource A thành công, sau đó factory tạo B trả `null`. Chương trình có tiếp tục khởi tạo C không?  
**Đáp án:** Có, nếu không có exception khác. Trả `null` bình thường không cắt ngang resource initialization.

**Câu 5.** Factory tạo A thành công, factory tạo B ném `IOException`. Thân `try` có chạy không?  
**Đáp án:** Không. Java cố đóng A trước khi exception khởi tạo B được xử lý hoặc lan truyền.

**Câu 6.** Thân `try` ném exception A, `close()` ném B. Caller nhận loại nào làm primary?  
**Đáp án:** Với try-with-resources, A là primary, B thường được đính kèm trong `A.getSuppressed()`.

**Câu 7.** Thân `try` thành công nhưng `close()` ném B. B có tự biến mất vì đó là cleanup tự động không?  
**Đáp án:** Không. B trở thành exception chính nếu không có exception chính từ thân `try`.

**Câu 8.** `try (AutoCloseable r = ...)` có thể buộc xử lý checked `Exception` không, dù tại runtime `r` đôi khi bằng `null`?  
**Đáp án:** Có. Hợp đồng kiểu tĩnh `AutoCloseable.close()` khai báo `throws Exception`, được compiler xét khi phân tích.

**Câu 9.** Java 17 có cho `Resource r = null; try (r) {...}` không?  
**Đáp án:** Có nếu `r` là final/effectively final và đã được gán trước khi dùng; vì `r` bằng `null` nên không gọi `close()`.

**Câu 10.** Nếu `null` là tình huống không hợp lệ đối với API, có nên để thân `try` tự phát sinh NPE ở một lệnh ngẫu nhiên không?  
**Đáp án:** Nên xác thực sớm bằng hợp đồng rõ, ví dụ `Objects.requireNonNull`, hoặc để factory ném exception phù hợp thay vì trả `null` mơ hồ.

## 21. Checklist khi review try-with-resources có thể nhận `null`

1. **Hợp đồng của factory:** có thể trả `null` hay bắt buộc trả object? Đây là trường hợp hợp lệ hay bug?
2. **Loại tài nguyên:** resource có triển khai `AutoCloseable` không, và `close()` có thể ném checked exception nào?
3. **Lúc khởi tạo:** biểu thức trả `null` bình thường hay ném exception? Hai đường này khác nhau.
4. **Thân `try`:** có dereference tài nguyên chưa được kiểm tra `null` không?
5. **Lời gọi `close()`:** chỉ xảy ra với tham chiếu khác `null` đã được khởi tạo thành công.
6. **Nhiều resource:** thứ tự khởi tạo trái→phải, thứ tự đóng phải→trái, bỏ qua null.
7. **Khởi tạo một phần:** nếu resource sau thất bại, resource trước đã mở có được cố đóng?
8. **Exception chính/phụ:** khi thân và `close()` cùng lỗi, caller có kiểm tra `getSuppressed()` không?
9. **Cú pháp Java 9+:** dùng `try (resource)` thì biến ngoài có final/effectively final và definite assignment?
10. **Bảo toàn thông tin:** có tránh `finally` tự ném lỗi mới làm che lỗi chính hay không?
11. **Quyền sở hữu:** tài nguyên có bị đóng hai lần hoặc dùng sau khi đóng vì được chia sẻ không?
12. **Kiểm thử:** có bao phủ resource khác `null`, resource `null`, lỗi khởi tạo và lỗi `close()`?

## 22. Ghi nhớ

**Try-with-resources tự động cố đóng tài nguyên triển khai `AutoCloseable`, nhưng chỉ gọi `close()` trên các tham chiếu khác `null` đã được khởi tạo thành công.** Một biến tài nguyên bằng `null` không tự làm câu lệnh ném `NullPointerException`, thân `try` vẫn chạy, và Java bỏ qua lời gọi `close()` cho biến đó. Tuy vậy, bất kỳ lệnh nào trong thân `try` cố dùng object bằng `null` vẫn có thể ném `NullPointerException`.

Với nhiều tài nguyên, Java khởi tạo từ trái sang phải, đóng từ phải sang trái và bỏ qua những tài nguyên `null`. Nếu một biểu thức khởi tạo **ném exception**, thân `try` không được vào; các tài nguyên trước đó đã mở được cố đóng. Nếu `close()` thất bại, lỗi đóng có thể trở thành primary khi thân thành công, hoặc suppressed khi đã có lỗi chính.

Hãy coi sự vắng mặt của tài nguyên là **quyết định thiết kế hợp đồng API**, không phải khả năng mà try-with-resources tự “chữa” cho chương trình. Khi tài nguyên là bắt buộc, xác thực rõ ràng; khi tài nguyên là tùy chọn, xử lý nhánh `null` có chủ đích, và để try-with-resources đảm nhiệm cleanup.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="try-with-resources-009">Ngoại lệ từ thân try, close và nguyên tắc chọn ngoại lệ chính</a>

<details>
<summary>Xem chi tiết</summary>

# Ngoại lệ từ thân `try`, `close()` và nguyên tắc chọn ngoại lệ chính trong Java

Một tài nguyên có thể thất bại **khi khởi tạo**, **khi được sử dụng trong thân `try`** hoặc **khi `close()` được gọi**. Nếu hai hoặc nhiều lỗi cùng xuất hiện trong một câu lệnh `try-with-resources`, Java không chỉ chọn một lỗi để ném ra rồi bỏ mọi lỗi còn lại: ngôn ngữ có quy tắc xác định **ngoại lệ chính** (*primary exception*) và ghi các ngoại lệ phụ vào danh sách **suppressed exceptions**.

Đây là điểm khác biệt rất quan trọng so với việc tự viết `resource.close()` trong `finally`: với `try-finally` thủ công, một lỗi ném từ `finally` có thể thay thế lỗi ở thân `try`. Với `try-with-resources`, khi thân `try` đã ném một ngoại lệ, Java thường giữ **chính ngoại lệ ấy** làm lỗi chính, còn lỗi đóng tài nguyên được thêm vào qua `Throwable.addSuppressed(...)`.

Bài này tập trung vào **thứ tự sự kiện và nguyên tắc chọn lỗi chính**, bao gồm trường hợp một tài nguyên, nhiều tài nguyên, lỗi khởi tạo, `return`, `catch`, `finally`, cách đọc `getSuppressed()` và cách không làm mất lỗi gốc khi chuyển đổi ngoại lệ. Các chương trình hoàn chỉnh sử dụng **Java 17+**.

## 1. Khái niệm: ngoại lệ chính và ngoại lệ bị suppress

Xét tình huống đọc dữ liệu:

```text
Mở tài nguyên            thành công
      |
      v
Thân try                 ném IOException("read failed")
      |
      v
Tự động close()          ném IOException("close failed")
      |
      v
Ngoại lệ thoát ra        IOException("read failed")   ← primary
      |
      +-- getSuppressed() -> IOException("close failed")
```

Ở đây:

- **Ngoại lệ chính** là đối tượng `Throwable` được truyền ra khỏi `try-with-resources` để các `catch` ở ngoài xem xét. Trong tình huống này, đó là lỗi đọc dữ liệu.
- **Ngoại lệ suppressed** là ngoại lệ bổ sung xảy ra trong quá trình xử lý một ngoại lệ đã có. Nó được lưu trong mảng trả về bởi `primary.getSuppressed()`.
- **Cause** là một quan hệ khác: `primary.getCause()` mô tả ngoại lệ gây ra một ngoại lệ được bọc hoặc có liên hệ nguyên nhân. Ngoại lệ suppressed **không tự trở thành** `cause`.
- Một ngoại lệ có thể có **cả cause lẫn suppressed**. Hai cơ chế chứa thông tin khác nhau.

**Không được kết luận rằng “lỗi được ném sau cùng luôn là lỗi chính”.** Trong `try-with-resources`, việc đóng thường xảy ra sau khi thân `try` đã thất bại, nhưng lỗi thân `try` vẫn giữ vai trò chính. Nếu thân `try` không thất bại, lỗi đầu tiên phát sinh trong **thứ tự đóng** có thể trở thành lỗi chính.

## 2. Quy tắc nền tảng theo JLS §14.20.3

Java Language Specification (JLS) mô tả `try-with-resources` như một cấu trúc có quy tắc tự động đóng tài nguyên:

1. Các tài nguyên được **khởi tạo từ trái sang phải**.
2. Nếu khởi tạo thất bại ở tài nguyên phía sau, những tài nguyên trước đó **đã khởi tạo thành công** được đóng.
3. Nếu khởi tạo thành công, thân `try` được thực thi; khi rời thân `try`, các tài nguyên được đóng theo **thứ tự ngược** với khởi tạo.
4. Mỗi tài nguyên có giá trị khác `null` được đóng. Một lỗi từ thao tác đóng **không mặc nhiên ngăn** Java thử đóng các tài nguyên còn lại.
5. Nếu đã có một ngoại lệ chính từ quá trình khởi tạo, thân `try` hoặc thao tác đóng tài nguyên sau hơn trong thứ tự đóng, lỗi đóng tiếp theo được **suppress** vào ngoại lệ chính.
6. Nếu không có ngoại lệ nào đang được ném trước khi gọi `close()`, mà `close()` thất bại, lỗi đóng ấy trở thành **ngoại lệ chính**.
7. Với dạng `try-with-resources` có `catch`/`finally` đi kèm, tài nguyên được đóng **trước khi** Java chọn `catch` của cùng câu lệnh và thực hiện `finally`.

Quy tắc này áp dụng cho cả những đối tượng implements `AutoCloseable` và `Closeable`, miễn chúng được dùng làm resource hợp lệ trong cú pháp `try (...)`. Nó cũng áp dụng khi sự hoàn tất của thân `try` diễn ra vì `return`, `break` hay `continue`, dù cách xác định ngoại lệ chính khác với trường hợp thân đã ném một lỗi.

### Bảng quyết định nhanh

| Điều xảy ra trước | Khi `close()` chạy | Ngoại lệ/chuyển quyền cuối cùng |
| --- | --- | --- |
| Thân `try` hoàn tất bình thường | `close()` bình thường | Toàn bộ câu lệnh có thể hoàn tất bình thường |
| Thân `try` ném A | `close()` bình thường | A tiếp tục được ném |
| Thân `try` ném A | `close()` ném B | A là **primary**, B là **suppressed** của A |
| Thân `try` bình thường | `close()` ném B | B trở thành **primary** |
| Tài nguyên thứ hai khởi tạo và ném A | `close()` tài nguyên thứ nhất ném B | A là **primary**, B bị suppress |
| Thân `try` dự định `return 42` | `close()` bình thường | Tiếp tục trả 42 |
| Thân `try` dự định `return 42` | `close()` ném B | B được ném ra; không trả 42 |
| Thân `try` ném A, nhiều `close()` lần lượt ném B, C | Các bước đóng được tiếp tục | A là **primary**, B và C được ghi suppressed theo thứ tự phát sinh |

Bảng mô tả luồng thông thường của `try-with-resources` và các đối tượng ngoại lệ khác nhau. Các trường hợp hiếm như một tài nguyên cố tình ném lại **chính cùng đối tượng `Throwable`** đã là ngoại lệ chính có thể tạo tình huống *self-suppression* đặc biệt; không nên thiết kế `close()` kiểu này.

## 3. Một tài nguyên: lỗi trong thân và lỗi ở `close()`

Đây là ví dụ cơ bản nhất: thao tác nghiệp vụ và cleanup đều thất bại. Lưu thành `BodyAndCloseDemo.java`:

```java
import java.io.IOException;

public class BodyAndCloseDemo {
    static class Resource implements AutoCloseable {
        Resource() {
            System.out.println("open");
        }

        @Override
        public void close() throws IOException {
            System.out.println("close");
            throw new IOException("close failed");
        }
    }

    public static void main(String[] args) {
        try (Resource resource = new Resource()) {
            System.out.println("body");
            throw new IllegalStateException("work failed");
        } catch (Exception ex) {
            System.out.println("primaryType="
                    + ex.getClass().getSimpleName());
            System.out.println("primaryMessage=" + ex.getMessage());
            System.out.println("suppressedCount=" + ex.getSuppressed().length);

            for (Throwable extra : ex.getSuppressed()) {
                System.out.println("suppressedType="
                        + extra.getClass().getSimpleName());
                System.out.println("suppressedMessage=" + extra.getMessage());
            }
        }
        System.out.println("after");
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 BodyAndCloseDemo.java
java BodyAndCloseDemo
```

Kết quả xác định:

```text
open
body
close
primaryType=IllegalStateException
primaryMessage=work failed
suppressedCount=1
suppressedType=IOException
suppressedMessage=close failed
after
```

Phân tích:

- Constructor `Resource()` hoàn tất nên đối tượng được đăng ký làm tài nguyên.
- Thân `try` ném `IllegalStateException("work failed")`. Đối tượng này được giữ làm lý do `throw` đang chờ.
- Trong bước tự động đóng, `close()` in `close` rồi ném `IOException("close failed")`.
- Vì đã có ngoại lệ chính từ thân, lỗi `close()` được thêm qua cơ chế suppressed; nó **không thay thế** `IllegalStateException`.
- Nhánh `catch (Exception ex)` bên ngoài phần quản lý tài nguyên nhận **IllegalStateException**, không phải IOException đóng tài nguyên.
- `catch` hoàn tất bình thường nên dòng `after` vẫn được thực thi.

Trong hệ thống thật, `catch (Exception)` nên được cân nhắc kỹ: ví dụ dùng nó để quan sát cơ chế với nhiều kiểu ngoại lệ; khi triển khai nghiệp vụ, nên chọn các loại lỗi mà tầng hiện tại thật sự xử lý được.

## 4. Nếu thân `try` thành công nhưng `close()` thất bại?

Một tài nguyên có thể hoạt động bình thường rồi thất bại khi flush dữ liệu, đồng bộ trạng thái hoặc đóng kết nối. Khi đó **không có ngoại lệ từ thân `try`** để được ưu tiên: chính lỗi đóng trở thành ngoại lệ thoát ra ngoài.

Lưu thành `CloseOnlyFailureDemo.java`:

```java
import java.io.IOException;

public class CloseOnlyFailureDemo {
    static class Resource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            System.out.println("close");
            throw new IOException("flush failed");
        }
    }

    public static void main(String[] args) {
        try (Resource resource = new Resource()) {
            System.out.println("work complete");
        } catch (IOException ex) {
            System.out.println("primary=" + ex.getMessage());
            System.out.println("suppressedCount=" + ex.getSuppressed().length);
        }
        System.out.println("after");
    }
}
```

```bash
javac -encoding UTF-8 CloseOnlyFailureDemo.java
java CloseOnlyFailureDemo
```

Kết quả:

```text
work complete
close
primary=flush failed
suppressedCount=0
after
```

Thao tác chính in `work complete` nhưng câu lệnh `try-with-resources` không hoàn tất bình thường: `close()` ném `IOException`. Lỗi này được `catch (IOException)` bắt, không có lỗi khác phải suppress.

**Đừng bỏ qua lỗi đóng chỉ vì phần thân đã chạy xong.** Với một số định dạng ghi dữ liệu, lỗi khi flush/close có thể báo rằng dữ liệu chưa được ghi bền vững. Chính sách xử lý phụ thuộc hợp đồng của tài nguyên, nhưng không thể tùy tiện coi kết quả tác vụ là thành công.

## 5. Nhiều tài nguyên: lỗi đóng đầu tiên là lỗi chính nếu không có lỗi trước đó

Với nhiều resource, Java khởi tạo **trái → phải** và đóng **phải → trái**. Nếu thân `try` không ném lỗi và cả hai lệnh `close()` đều thất bại, **lệnh `close()` được gọi trước** sẽ tạo lỗi chính; lỗi đóng tiếp theo bị suppress.

Lưu thành `TwoCloseFailuresDemo.java`:

```java
import java.io.IOException;

public class TwoCloseFailuresDemo {
    static class FailingResource implements AutoCloseable {
        private final String name;

        FailingResource(String name) {
            this.name = name;
            System.out.println("open " + name);
        }

        @Override
        public void close() throws IOException {
            System.out.println("close " + name);
            throw new IOException("close " + name + " failed");
        }
    }

    public static void main(String[] args) {
        try (FailingResource a = new FailingResource("A");
             FailingResource b = new FailingResource("B")) {
            System.out.println("body ok");
        } catch (IOException ex) {
            System.out.println("primary=" + ex.getMessage());
            System.out.println("suppressedCount=" + ex.getSuppressed().length);
            for (Throwable extra : ex.getSuppressed()) {
                System.out.println("suppressed=" + extra.getMessage());
            }
        }
    }
}
```

```bash
javac -encoding UTF-8 TwoCloseFailuresDemo.java
java TwoCloseFailuresDemo
```

Kết quả:

```text
open A
open B
body ok
close B
close A
primary=close B failed
suppressedCount=1
suppressed=close A failed
```

Điều cần ghi nhớ:

1. A và B đều khởi tạo thành công; `body ok` được in.
2. B được đóng trước A, vì B được khai báo sau.
3. `close B` ném IOException khi chưa có ngoại lệ đang chờ; nó trở thành **primary**.
4. Java vẫn thử đóng A; IOException từ `close A` được thêm vào danh sách suppressed của lỗi B.
5. Bên ngoài nhận `IOException("close B failed")`, kèm một ngoại lệ suppressed.

Với ba tài nguyên A, B, C đóng theo C → B → A, nếu chỉ có lỗi đóng thì lỗi đầu tiên xuất hiện trong thứ tự C → B → A là lỗi chính. Không nên hiểu “tài nguyên đầu tiên được khai báo” là “tài nguyên có lỗi chính”.

## 6. Thân `try` ném lỗi và nhiều tài nguyên cũng ném khi đóng

Nếu thân `try` đã ném ngoại lệ A, thì lỗi của từng thao tác đóng tiếp theo có thể được ghi trong danh sách suppressed của A, dù số lượng tài nguyên là bao nhiêu.

Lưu thành `BodyAndMultipleClosesDemo.java`:

```java
import java.io.IOException;

public class BodyAndMultipleClosesDemo {
    static class Failing implements AutoCloseable {
        private final String name;

        Failing(String name) {
            this.name = name;
            System.out.println("open " + name);
        }

        @Override
        public void close() throws IOException {
            System.out.println("close " + name);
            throw new IOException("close " + name + " failed");
        }
    }

    public static void main(String[] args) {
        try (Failing first = new Failing("first");
             Failing second = new Failing("second");
             Failing third = new Failing("third")) {
            System.out.println("work");
            throw new IllegalArgumentException("invalid input");
        } catch (Exception ex) {
            System.out.println("primary=" + ex.getMessage());
            System.out.println("suppressedCount=" + ex.getSuppressed().length);
            for (Throwable extra : ex.getSuppressed()) {
                System.out.println("suppressed=" + extra.getMessage());
            }
        }
    }
}
```

```bash
javac -encoding UTF-8 BodyAndMultipleClosesDemo.java
java BodyAndMultipleClosesDemo
```

Kết quả:

```text
open first
open second
open third
work
close third
close second
close first
primary=invalid input
suppressedCount=3
suppressed=close third failed
suppressed=close second failed
suppressed=close first failed
```

Thứ tự các ngoại lệ suppressed phản ánh thứ tự các lỗi được phát hiện trong quá trình đóng. Việc lỗi từ `close third` phát sinh trước `close first` **không làm lỗi đóng nào trở thành primary** vì thân `try` đã có ngoại lệ chính `IllegalArgumentException("invalid input")`.

Có thể gặp `getSuppressed().length == 0` ngay cả khi có lỗi, nếu tài nguyên không ném khi đóng; không nên coi `getSuppressed()` là một bộ đếm lỗi nghiệp vụ tổng quát.

## 7. Nếu khởi tạo tài nguyên phía sau ném lỗi thì chọn ngoại lệ nào?

Khi một resource initializer ném ngoại lệ, thân `try` **chưa được thực thi**. Java vẫn đóng những tài nguyên khai báo trước đó đã khởi tạo thành công.

Nếu việc đóng những tài nguyên trước đó cũng ném lỗi, **ngoại lệ từ bước khởi tạo thất bại** là lỗi chính; những lỗi đóng phát sinh trong quá trình rollback việc khởi tạo được suppress vào lỗi ấy.

Lưu thành `InitializationFailureDemo.java`:

```java
import java.io.IOException;

public class InitializationFailureDemo {
    static class Resource implements AutoCloseable {
        private final String name;

        Resource(String name, boolean failOpen) throws IOException {
            this.name = name;
            System.out.println("init " + name);
            if (failOpen) {
                throw new IOException("init " + name + " failed");
            }
        }

        @Override
        public void close() throws IOException {
            System.out.println("close " + name);
            throw new IOException("close " + name + " failed");
        }
    }

    public static void main(String[] args) {
        try (Resource a = new Resource("A", false);
             Resource b = new Resource("B", true)) {
            System.out.println("body");
        } catch (IOException ex) {
            System.out.println("primary=" + ex.getMessage());
            System.out.println("suppressedCount=" + ex.getSuppressed().length);
            for (Throwable extra : ex.getSuppressed()) {
                System.out.println("suppressed=" + extra.getMessage());
            }
        }
    }
}
```

```bash
javac -encoding UTF-8 InitializationFailureDemo.java
java InitializationFailureDemo
```

Kết quả:

```text
init A
init B
close A
primary=init B failed
suppressedCount=1
suppressed=close A failed
```

Điểm đáng chú ý: tài nguyên B ném lỗi **trong constructor**, nên câu lệnh khởi tạo B chưa hoàn tất thành công. Java không có một đối tượng B đã được khởi tạo thành công và đăng ký để tự gọi `B.close()`; A đã được khởi tạo thành công nên A được đóng. Thân `try` không chạy.

Nếu constructor B đã mở một tài nguyên nội bộ rồi ném lỗi trước khi trả về đối tượng, constructor/factory của B phải tự có chiến lược cleanup cho phần đã cấp phát; `try-with-resources` bên ngoài không thể tự giải phóng một resource chưa được trao cho nó.

## 8. Không chỉ lỗi: `return` vẫn phải đi qua bước `close()`

`return` là một cách câu lệnh hoàn tất đột ngột theo ngôn ngữ Java, nhưng nó **không tạo đối tượng ngoại lệ chính**. Vì vậy, nếu `try` dự định trả về một giá trị rồi `close()` ném lỗi, **lỗi `close()` được ném thay vì trả giá trị**.

Lưu thành `ReturnAndCloseFailureDemo.java`:

```java
import java.io.IOException;

public class ReturnAndCloseFailureDemo {
    static class Resource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            System.out.println("close");
            throw new IOException("cleanup failed");
        }
    }

    static int calculate() throws IOException {
        try (Resource resource = new Resource()) {
            System.out.println("body");
            return 42;
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println("value=" + calculate());
        } catch (IOException ex) {
            System.out.println("caught=" + ex.getMessage());
            System.out.println("suppressedCount=" + ex.getSuppressed().length);
        }
    }
}
```

```bash
javac -encoding UTF-8 ReturnAndCloseFailureDemo.java
java ReturnAndCloseFailureDemo
```

Kết quả:

```text
body
close
caught=cleanup failed
suppressedCount=0
```

Biểu thức `return 42` đã được đánh giá, nhưng trước khi giá trị tới nơi gọi, Java chạy `close()`. Nó thất bại nên lời gọi `calculate()` ném `IOException`; `System.out.println("value=" + calculate())` không được thực hiện vì chưa có giá trị cho phép nối chuỗi.

Nếu `close()` hoàn tất bình thường, phương thức sẽ trả `42`. Tương tự, với `break` hoặc `continue` rời khỏi phạm vi quản lý tài nguyên, `close()` được thực hiện trước khi quyền điều khiển hoàn tất việc chuyển đi; nếu nó ném lỗi, đường đi bình thường dự kiến có thể bị thay thế bởi việc ném lỗi.

**Phân biệt:** khi thân `try` đã ném ngoại lệ A, một lỗi `close()` được suppress vào A. Khi thân `try` chỉ dự định `return`, không có A để suppress, nên lỗi `close()` có thể trở thành primary.

## 9. `catch` của cùng câu lệnh nhìn thấy ngoại lệ nào?

Một `try-with-resources` có thể có `catch` và `finally`:

```java
try (Resource resource = open()) {
    perform(resource);
} catch (IOException ex) {
    handleIo(ex);
} catch (RuntimeException ex) {
    handleRuntime(ex);
} finally {
    afterOperation();
}
```

Đây là mẫu minh họa, giả định các kiểu và phương thức hỗ trợ đã tồn tại. Thứ tự thực thi quan trọng:

1. Khởi tạo resource và thực hiện thân `try`.
2. Thực hiện logic **đóng tự động** và xác định ngoại lệ chính/suppressed.
3. Nếu có ngoại lệ được ném ra từ phần quản lý tài nguyên, chọn **nhánh `catch` đầu tiên tương thích với kiểu thực tế của ngoại lệ chính**.
4. Thực hiện khối `catch` được chọn, nếu có.
5. Thực hiện `finally`, sau đó hoàn tất chuyển quyền điều khiển.

Không có chuyện mỗi suppressed exception lại kích hoạt thêm một nhánh `catch`. Một lỗi suppressed là **thông tin đi kèm** đối tượng chính, không phải một lần `throw` mới lên mỗi `catch` cùng cấp.

### Chương trình Java 17+: nhánh `catch` dựa trên lỗi chính

Lưu thành `CatchPrimaryDemo.java`:

```java
import java.io.IOException;

public class CatchPrimaryDemo {
    static class Resource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            System.out.println("close");
            throw new IOException("close io");
        }
    }

    public static void main(String[] args) {
        try (Resource resource = new Resource()) {
            System.out.println("body");
            throw new IllegalArgumentException("body invalid");
        } catch (IOException ex) {
            System.out.println("io catch");
        } catch (IllegalArgumentException ex) {
            System.out.println("runtime catch=" + ex.getMessage());
            System.out.println("suppressed="
                    + ex.getSuppressed()[0].getMessage());
        } finally {
            System.out.println("finally");
        }
        System.out.println("after");
    }
}
```

```bash
javac -encoding UTF-8 CatchPrimaryDemo.java
java CatchPrimaryDemo
```

Kết quả:

```text
body
close
runtime catch=body invalid
suppressed=close io
finally
after
```

Ở đây `IOException("close io")` xảy ra khi đóng, nhưng không chọn `catch (IOException)` vì nó đã bị suppress vào `IllegalArgumentException("body invalid")`. Nhánh runtime tiếp nhận **ngoại lệ chính**, in thông tin lỗi phụ, rồi `finally` chạy.

Nếu thân `try` bỏ câu lệnh `throw new IllegalArgumentException(...)` và hoàn tất bình thường, lỗi `IOException` từ `close()` sẽ trở thành chính và `catch (IOException)` có thể được chọn.

## 10. So sánh `try-finally` thủ công và `try-with-resources`

Với đoạn đóng thủ công:

```java
try {
    throw new IllegalStateException("work failed");
} finally {
    throw new IllegalArgumentException("close failed");
}
```

Đây là mã minh họa hợp lệ khi đặt trong phương thức, nhưng là **kiểu cleanup cần tránh**. Theo quy tắc `finally`, lý do mới từ `finally` thay thế lý do `throw` cũ. Bộ xử lý bên ngoài sẽ nhận `IllegalArgumentException("close failed")`, còn lỗi `IllegalStateException("work failed")` không tự động được ghi làm `cause` hoặc `suppressed`.

Ngược lại:

```java
try (Resource resource = new Resource()) {
    throw new IllegalStateException("work failed");
}
```

Đây là mẫu chỉ có nghĩa khi `Resource implements AutoCloseable`, `Resource.close()` có thể ném lỗi, và phương thức xử lý/khai báo ngoại lệ phù hợp. `try-with-resources` được thiết kế để không tùy tiện làm mất lỗi thân `try` vì một lỗi đóng bình thường của resource.

**Không được diễn giải `try-with-resources` là “`finally` đơn giản gọi `close()`” trong mọi trường hợp.** Ngữ nghĩa của JLS có logic giữ biến primary exception và gọi `addSuppressed` khi thích hợp. Đó là khác biệt cốt lõi.

Khi thiết kế code, ưu tiên `try-with-resources` cho tài nguyên implements `AutoCloseable`. Nếu có cleanup bổ sung phải làm thủ công, cần kiểm tra tác động khi cleanup ném lỗi để không ghi đè thông tin quan trọng.

## 11. `getSuppressed()`, `getCause()` và việc bọc lỗi ở tầng trên

Một tầng repository có thể ném `IOException` chính kèm suppressed từ lỗi đóng. Tầng service có thể quyết định chuyển đổi thành một lỗi nghiệp vụ `DataLoadException` bằng cách **gắn ngoại lệ ban đầu làm cause**. Khi đó:

```text
DataLoadException("cannot load")
   |
   +-- getCause() ---> IOException("read failed")       ← original primary
                           |
                           +-- getSuppressed()[0]
                                   |
                                   └── IOException("close failed")
```

Ngoại lệ mới ở tầng service không tự sao chép danh sách suppressed lên chính nó. **Danh sách suppressed vẫn nằm trên đối tượng cause ban đầu**, trừ khi code chủ động thao tác khác.

Lưu thành `WrappedPrimaryDemo.java`:

```java
import java.io.IOException;

public class WrappedPrimaryDemo {
    static class Resource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            throw new IOException("close failed");
        }
    }

    static class DataLoadException extends Exception {
        DataLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static void repository() throws IOException {
        try (Resource resource = new Resource()) {
            throw new IOException("read failed");
        }
    }

    static void service() throws DataLoadException {
        try {
            repository();
        } catch (IOException ex) {
            throw new DataLoadException("cannot load", ex);
        }
    }

    public static void main(String[] args) {
        try {
            service();
        } catch (DataLoadException ex) {
            Throwable cause = ex.getCause();
            System.out.println("wrapper=" + ex.getMessage());
            System.out.println("wrapperSuppressed="
                    + ex.getSuppressed().length);
            System.out.println("cause=" + cause.getMessage());
            System.out.println("causeSuppressed="
                    + cause.getSuppressed()[0].getMessage());
        }
    }
}
```

```bash
javac -encoding UTF-8 WrappedPrimaryDemo.java
java WrappedPrimaryDemo
```

Kết quả:

```text
wrapper=cannot load
wrapperSuppressed=0
cause=read failed
causeSuppressed=close failed
```

Đây là cách thường gặp để chuyển đổi ngoại lệ ở ranh giới tầng mà không đánh mất nguyên nhân. Khi in stack trace bằng `printStackTrace()` hoặc dùng hệ thống logging hỗ trợ `Throwable`, phần cause và suppressed có thể được trình bày theo cấu trúc tương ứng.

Không nên chỉ dùng `throw new DataLoadException(ex.getMessage(), null)` hoặc ghi lại một message chung rồi bỏ đối tượng `ex`: thao tác đó có thể đánh mất cả stack trace, cause lẫn suppressed exception cần để điều tra.

## 12. Kiểm tra kiểu ngoại lệ khi đóng vẫn là vấn đề compile-time

`AutoCloseable.close()` được khai báo `throws Exception`; `Closeable.close()` được khai báo `throws IOException`. Một lớp cụ thể có thể override với loại checked hẹp hơn hoặc không có checked exception. Khi dùng `try-with-resources`, compiler xét:

- Những checked exceptions có thể phát sinh **lúc khởi tạo** tài nguyên.
- Những checked exceptions có thể phát sinh từ **thân `try`**.
- Những checked exceptions mà phương thức **`close()` của kiểu tài nguyên** có thể ném.
- Những ngoại lệ được `catch` xử lý tại chỗ và những loại còn có thể thoát ra để yêu cầu `throws`.

Ví dụ mã hợp lệ với kiểu tài nguyên cụ thể:

```java
class SilentResource implements AutoCloseable {
    @Override
    public void close() { }
}

class TypeCheckExample {
    static void useSilent() {
        try (SilentResource r = new SilentResource()) {
            System.out.println("work");
        }
    }
}
```

Đây là lớp minh họa hợp lệ nhưng không có `main`. Vì `SilentResource.close()` không khai báo checked exception, `useSilent()` không cần thêm `throws Exception` chỉ vì lớp implements `AutoCloseable`.

Ngược lại, với tham chiếu kiểu tĩnh `AutoCloseable`:

```java
static void useAsAutoCloseable(AutoCloseable resource) throws Exception {
    try (resource) {
        System.out.println("work");
    }
}
```

Đoạn thứ hai là phương thức minh họa (Java 9+), có thể đặt trong một lớp. Tham số `resource` không bị gán lại nên là effectively final và hợp lệ trong `try (resource)`. Kiểu tĩnh `AutoCloseable` có `close() throws Exception`, nên phương thức phải xử lý hoặc khai báo loại checked đó. Trong Java 17, mẫu này hợp lệ.

Quy tắc primary/suppressed là **hành vi runtime**. Nó không cho phép bỏ qua nghĩa vụ checked exception lúc biên dịch, kể cả khi bạn nghĩ rằng lỗi `close()` “chỉ bị suppress”. Nếu thân `try` bình thường, lỗi `close()` có thể chính là primary.

## 13. Ngoại lệ `Error`, interrupted cleanup và những trường hợp hiếm

`AutoCloseable.close()` có thể ném cả unchecked exceptions. Nếu resource `close()` ném `RuntimeException` hoặc `Error`, cách xác định lỗi chính/suppressed của `try-with-resources` về mặt ngữ nghĩa vẫn dựa trên các quy tắc `Throwable`; nhưng **không nên bắt `Throwable` một cách đại trà chỉ để tiếp tục chương trình**. Một `Error` nghiêm trọng có thể chỉ ra JVM hoặc trạng thái ứng dụng không còn phù hợp để phục hồi.

Theo Javadoc của `AutoCloseable`, nên tránh để `close()` ném `InterruptedException`. Nếu nó xuất hiện trong cleanup khi đã có lỗi chính, ngoại lệ bị suppress có thể làm ý nghĩa trạng thái interrupt khó quản lý; cần thiết kế API cleanup không làm mất yêu cầu hủy thread.

Ngoài ra, `Throwable.addSuppressed(...)` có giới hạn:

- Không thể suppress chính **cùng đối tượng** `Throwable` vào nó (*self-suppression*), vì đó là thao tác không hợp lệ.
- Một lớp `Throwable` có thể được xây dựng với suppression bị vô hiệu hóa (thông qua constructor phù hợp ở lớp con). Khi đó `addSuppressed(...)` không ghi lỗi phụ vào danh sách như trường hợp thông thường.
- Nếu một resource `close()` cố tình tái ném đúng cùng đối tượng đang là ngoại lệ chính, logic tự động có thể gặp lỗi self-suppression. Đừng tạo thiết kế dựa vào trường hợp đó.
- Nếu JVM/tiến trình bị chấm dứt ngoài đường đi Java thông thường, không có bảo đảm rằng `close()` hay `finally` đã chạy.

Những tình huống hiếm này không làm thay đổi nguyên tắc thực hành: **thiết kế `close()` có trách nhiệm, tránh ném cùng đối tượng lỗi đang truyền, và luôn giữ ngữ cảnh lỗi chính**.

## 14. Cách đọc log và stack trace khi có suppressed exception

Khi debug một thất bại có nhiều dấu vết lỗi:

1. Đọc **kiểu và message của ngoại lệ chính** đầu tiên. Nó cho biết hoạt động nào làm cấu trúc `try-with-resources` thất bại theo đường truyền ra ngoài.
2. Kiểm tra **cause chain** qua `getCause()` để xem ngoại lệ này có được tầng khác chuyển đổi từ nguyên nhân cụ thể nào không.
3. Kiểm tra `getSuppressed()` trên **từng đối tượng quan trọng trong cause chain**, không chỉ trên wrapper ngoài cùng.
4. Xem thứ tự các lỗi suppressed. Thứ tự này thường phản ánh thứ tự các bước đóng tài nguyên, tức **ngược với thứ tự khai báo**.
5. Từ loại resource, xác định liệu lỗi đóng chỉ là cleanup phụ hay có thể chứng tỏ dữ liệu chưa được hoàn tất an toàn (ví dụ lỗi flush).
6. Quyết định retry, rollback hoặc phục hồi theo hợp đồng nghiệp vụ; không tự động retry chỉ vì thông báo chứa chữ “close”.

Một cách ghi lỗi đủ dữ liệu:

```java
try (Resource resource = open()) {
    process(resource);
} catch (Exception ex) {
    logger.error("Operation failed", ex);
    throw ex;
}
```

Đây là **mẫu minh họa**, giả định `Resource implements AutoCloseable`, các hàm và logger đã được định nghĩa, và phương thức bao ngoài khai báo `throws` đúng với các checked exceptions có thể đi ra. Logger nhận nguyên đối tượng `ex` nên có cơ hội hiển thị cả stack trace, cause lẫn suppressed.

Hạn chế ghi cùng một lỗi đầy đủ ở mọi tầng rồi ném lại vì có thể tạo log trùng. Nên xác định tầng chịu trách nhiệm ghi nhận cuối cùng; những tầng trung gian chỉ bổ sung ngữ cảnh khi thật sự cần.

## 15. Các hiểu lầm thường gặp

| Nhận định sai | Điều đúng cần nhớ |
| --- | --- |
| “`close()` chạy sau nên lỗi của `close()` luôn là ngoại lệ chính.” | Nếu thân đã ném lỗi A thì A thường là chính; lỗi đóng bị suppress. |
| “Nếu thân `try` chạy thành công, lỗi `close()` không quan trọng.” | Lỗi đóng có thể thành ngoại lệ chính; thao tác nghiệp vụ chưa chắc hoàn tất an toàn. |
| “Nếu hai `close()` cùng lỗi, resource khai báo trước quyết định primary.” | Nếu trước đó chưa có lỗi, `close()` ném đầu tiên theo thứ tự **đóng ngược** quyết định primary. |
| “Khi B mở thất bại, Java không đóng A.” | Java đóng các tài nguyên đã khởi tạo thành công trước đó. |
| “Lỗi `close()` làm Java không đóng các resource còn lại.” | Cơ chế `try-with-resources` tiếp tục cố đóng những resource còn lại. |
| “Mỗi suppressed exception đi vào một nhánh `catch` khác nhau.” | `catch` được chọn theo ngoại lệ chính; suppressed chỉ là thông tin đi kèm. |
| “`getCause()` và `getSuppressed()` là cùng một mảng.” | Cause là một quan hệ nguyên nhân; suppressed là danh sách lỗi bổ sung. |
| “`return` trong `try` chắc chắn thắng lỗi khi `close()`.” | Lỗi đóng có thể thay thế đường `return` dự kiến. |
| “Khi bọc lỗi thành `ServiceException`, danh sách suppressed tự sao chép sang wrapper.” | Nó thường ở nguyên đối tượng cause; cần truy cause rồi suppressed. |
| “`finally` thủ công có cùng logic ưu tiên lỗi với `try-with-resources`.” | `finally` tự ném lỗi có thể ghi đè lỗi cũ; `try-with-resources` có cơ chế suppression riêng. |
| “Có `getSuppressed()` nên không cần quản lý ownership của resource.” | Ownership vẫn quyết định đối tượng nào được đóng, có đóng quá sớm/nhầm đối tượng không. |
| “`try-with-resources` bảo đảm đóng nếu tiến trình bị kill.” | Không; nó thực thi theo đường điều khiển Java khi JVM còn vận hành phù hợp. |

## 16. Quy trình phân tích một bài tập chọn ngoại lệ chính

Với một đoạn `try-with-resources` cụ thể, hãy phân tích đúng trình tự:

**Bước 1 — Khởi tạo:** đọc các resource từ **trái sang phải**. Nếu một initializer ném, ghi loại lỗi và danh sách những resource đã được tạo thành công trước đó.

**Bước 2 — Thân `try`:** chỉ thực hiện nếu các resource cần thiết đều khởi tạo xong. Ghi thân hoàn tất bình thường, `return`, `throw` hay chuyển quyền điều khiển khác.

**Bước 3 — Đóng:** đi qua các resource đã khởi tạo thành công theo **thứ tự ngược**. Với mỗi `close()`, xác định nó hoàn tất bình thường hay ném ngoại lệ.

**Bước 4 — Chọn primary/suppressed:** nếu đã có ngoại lệ từ initializer hoặc thân thì nó được ưu tiên; nếu chưa, lỗi `close()` đầu tiên trở thành chính; các lỗi đóng sau đó được suppress theo quy tắc.

**Bước 5 — Xử lý bên ngoài:** sau quá trình tự đóng, xác định `catch` đầu tiên phù hợp với **kiểu ngoại lệ chính**, rồi `finally` nếu có. Nếu không bắt được, ngoại lệ tiếp tục lan ra.

**Bước 6 — Giữ thông tin:** khi chuyển đổi hoặc log lỗi, đảm bảo vẫn có thể truy vấn `cause` và `suppressed` của đối tượng ban đầu; không chỉ giữ message.

Áp dụng vào ba tình huống sau:

```text
Tình huống A:
  body        -> throw IOException("read")
  close B     -> throw IOException("close B")
  close A     -> bình thường
  primary     -> IOException("read")
  suppressed  -> IOException("close B")

Tình huống B:
  body        -> bình thường
  close B     -> throw IOException("close B")
  close A     -> throw IOException("close A")
  primary     -> IOException("close B")
  suppressed  -> IOException("close A")

Tình huống C:
  init A      -> thành công
  init B      -> throw IOException("init B")
  close A     -> throw IOException("close A")
  body        -> không chạy
  primary     -> IOException("init B")
  suppressed  -> IOException("close A")
```

Cách ghi sự kiện từ trên xuống này giúp tránh nhầm thứ tự khởi tạo với thứ tự đóng, nhầm “lỗi ném sau cùng” với primary và nhầm suppressed với cause.

## 17. Tự kiểm tra

**Câu 1.** Thân `try` ném `IOException("read")`, `close()` ném `IOException("close")`. Lỗi chính là gì?  
**`IOException("read")`.** Lỗi đóng trở thành suppressed của lỗi đọc.

**Câu 2.** Thân `try` bình thường, `close()` ném `IOException("flush")`. Lỗi chính?  
**`IOException("flush")`.** Không có lỗi nào trước đó để được ưu tiên.

**Câu 3.** Khai báo A rồi B; cả hai đóng đều ném, thân thành công. Lỗi nào là chính?  
**Lỗi từ B.close()**, vì B đóng trước A. Lỗi A.close() được suppress.

**Câu 4.** Thân ném lỗi X; `close(B)` và `close(A)` đều ném. Có tổng cộng bao nhiêu suppressed errors thông thường?  
**Hai**, theo thứ tự B rồi A; X vẫn là primary.

**Câu 5.** Khởi tạo B thất bại, A đã khởi tạo thành công. A có được đóng không?  
**Có.** Nếu A.close() cũng ném, lỗi từ B initializer được ưu tiên làm primary.

**Câu 6.** `return 42` trong thân `try`, `close()` ném IOException. Hàm trả về 42 hay ném IOException?  
**Ném IOException.** Khi đóng thất bại, đường trả giá trị dự kiến bị thay thế.

**Câu 7.** Nếu có `catch (IOException)` và `catch (IllegalStateException)`, thân ném IllegalStateException còn `close()` ném IOException, nhánh nào chạy?  
**Nhánh `catch (IllegalStateException)`**, vì lỗi IOException đóng đã bị suppress, không phải primary.

**Câu 8.** Một `ServiceException` bọc `IOException` chính làm cause. Có thể tìm lỗi đóng ở đâu?  
Thường trong `serviceException.getCause().getSuppressed()`, không nhất thiết ở `serviceException.getSuppressed()`.

**Câu 9.** `getSuppressed()` và `getCause()` trả cùng một thông tin?  
**Không.** Cause liên kết nguyên nhân; suppressed lưu các lỗi phụ cùng quá trình xử lý.

**Câu 10.** Có nên bắt mọi `Throwable` để “cứ tiếp tục sau lỗi đóng” không?  
**Không.** Lỗi có thể chỉ ra tài nguyên hoặc JVM không còn trạng thái phù hợp để tiếp tục; việc xử lý phải dựa trên hợp đồng và khả năng phục hồi thực tế.

## Tài liệu chuẩn

- [Java Language Specification, Java SE 21 — §14.20.3: `try-with-resources`](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.20.3).
- [JLS 21 — §14.20.3.1: Basic `try-with-resources` và cách chuyển đổi về `try-catch-finally`](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.20.3.1).
- [JLS 21 — §14.20.3.2: Extended `try-with-resources` có `catch`/`finally`](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.20.3.2).
- [JLS 21 — §11.2: Compile-Time Checking of Exceptions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-11.html#jls-11.2).
- [Java SE 21 API — `Throwable.addSuppressed()` và `getSuppressed()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Throwable.html).
- [Java SE 21 API — `AutoCloseable.close()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/AutoCloseable.html).

**Ghi nhớ:** Trong `try-with-resources`, ngoại lệ đã phát sinh ở phần khởi tạo hoặc thân `try` thường được giữ làm **primary**; ngoại lệ từ `close()` được **suppress** vào nó. Nếu chưa có lỗi nào, lỗi đóng đầu tiên theo thứ tự **đóng ngược** trở thành primary. Java tự động đóng những tài nguyên còn lại, sau đó `catch` phân loại **lỗi chính**, không phân loại từng lỗi suppressed.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="try-with-resources-010">Try-with-resources kết hợp catch và finally</a>

<details>
<summary>Xem chi tiết</summary>

# Try-with-resources kết hợp `catch` và `finally` trong Java

> **Bối cảnh:** Java Core → Exception → Try-with-resources. Cấu trúc `try-with-resources` (TWR, được giới thiệu từ Java 7) quản lý việc gọi `close()` của các tài nguyên `AutoCloseable`. Khi kết hợp thêm `catch` và `finally`, điểm quan trọng nhất là **tài nguyên đã được đóng theo cơ chế TWR trước khi các `catch` và `finally` gắn với cấu trúc đó được thực hiện**. Vì vậy `catch` có thể bắt lỗi phát sinh từ **thân `try`, khởi tạo tài nguyên hoặc `close()`**; `finally` thích hợp để hoàn tất những công việc khác, không cần đóng lại các tài nguyên đã giao cho TWR.

## 1. Mẫu cú pháp và thứ tự tổng quát

Dạng đầy đủ:

```java
try (ResourceType resource = acquire()) {
    use(resource);
} catch (SomeException e) {
    handle(e);
} finally {
    afterOperation();
}
```

Đây là **mã mẫu**, `ResourceType` và các phương thức chưa được định nghĩa; không phải tệp chạy độc lập.

- `try ( ... )` khai báo hoặc tham chiếu tài nguyên cần tự đóng.
- Thân `{ ... }` thực hiện công việc có thể thành công hoặc ném exception.
- Khi rời thân (theo đường Java thông thường), hệ thống gọi `close()` đối với các resource đã được khởi tạo thành công, theo **thứ tự ngược thứ tự khai báo**.
- `catch` tương thích xử lý `Throwable` đang lan truyền từ cấu trúc TWR nếu có.
- `finally` thực hiện công việc hoàn tất chung theo quy tắc `try`/`finally`, sau khi đóng resource và sau `catch` nếu có.
- Cả `catch` lẫn `finally` đều **không bắt buộc** đối với TWR: một `try (resource) { ... }` đứng riêng là hợp lệ.

Hình dung đường thành công và thất bại:

```text
      Khởi tạo resource(s)
                │
                ▼
           Thân try
          /        \
      thành công   phát sinh Throwable
          \        /
                ▼
        TWR gọi close()
        (đảo thứ tự khai báo)
                │
                ▼
     Có Throwable thoát ra?
          /          \
        Có            Không
        │              │
        ▼              │
  catch phù hợp?       │
  (nếu có)             │
        \              /
                 ▼
              finally
                 │
                 ▼
        Kết thúc hoặc lan lỗi
```

**Không phải:** `thân try → catch → close → finally`. Đối với handler gắn trực tiếp sau một TWR, Java **đóng trước khi vào handler**. Một lỗi mới từ `close()` có thể khiến dòng code vốn tưởng đã thành công rẽ sang `catch`.

### 1.1. Ví dụ tối giản có `catch` và `finally`

```java
try (java.io.BufferedReader reader =
             new java.io.BufferedReader(
                     new java.io.StringReader("Java"))) {
    System.out.println(reader.readLine());
} catch (java.io.IOException e) {
    System.out.println("Đọc hoặc đóng lỗi: " + e.getMessage());
} finally {
    System.out.println("Kết thúc thao tác");
}
```

Đây là đoạn trong một method Java phù hợp. `BufferedReader` có phương thức `readLine()`/`close()` khai báo `IOException`; dùng `catch(IOException)` ở đây hợp lệ. Dữ liệu đọc trong bộ nhớ và việc đóng thường thành công, cho stdout:

```text
Java
Kết thúc thao tác
```

Không xuất hiện dòng của `catch` vì không có `IOException` phát sinh trong lượt này. Sự tồn tại của `catch` không tự tạo ra ngoại lệ.

## 2. Ba vai trò riêng: `try(resource)`, `catch` và `finally`

| Thành phần | Vai trò đúng | Điều **không** tự làm |
| --- | --- | --- |
| `try (resource)` | Xác lập **quyền sở hữu tạm thời** và tự gọi `close()` khi rời khối | Không bảo đảm đọc/ghi thành công; không tự rollback giao dịch |
| `catch (T e)` | Chọn handler theo kiểu đối tượng được ném; phục hồi, báo lỗi, chuyển đổi hoặc ném tiếp | Không tự đóng resource khác, không tự hoàn tác trạng thái, không biến mọi lỗi thành thành công |
| `finally` | Thực hiện hành động hoàn tất chung trong phạm vi `try` tương ứng | Không nhất thiết chạy nếu JVM/process bị chấm dứt bất thường; không nên che exception gốc bằng `return`/`throw` mới |

Một lý do phổ biến để dùng TWR **cùng** `catch` và `finally` là phân tách trách nhiệm:

1. Resource được quản lý tập trung, không cần lặp mã đóng ở đường thành công và thất bại.
2. Handler chỉ tập trung vào **quyết định nghiệp vụ khi thao tác thất bại**.
3. Khối `finally` làm một công việc kết thúc khác, như ghi trạng thái hoàn tất lượt xử lý, khôi phục context hoặc giải phóng cơ chế không thuộc resource `AutoCloseable`.

Nếu chỉ cần tự đóng resource, hãy dùng TWR đơn giản, **không thêm `catch`/`finally` vô ích**. Nếu không biết xử lý exception tại tầng hiện tại, có thể để nó lan ra caller thay vì bắt rồi bỏ qua.

## 3. Chương trình Java 17: kiểm chứng thứ tự ở cả đường thành công và lỗi

Lưu tệp **`TwrCatchFinallyOrderDemo.java`**:

```java
public class TwrCatchFinallyOrderDemo {
    static final class Probe implements AutoCloseable {
        private final String name;

        Probe(String name) {
            this.name = name;
            System.out.println("Mở " + name);
        }

        void use() {
            System.out.println("Dùng " + name);
        }

        @Override
        public void close() {
            System.out.println("Đóng " + name);
        }
    }

    static void run(boolean fail) {
        try (Probe p = new Probe("R")) {
            p.use();
            if (fail) {
                throw new IllegalStateException("Lỗi thân try");
            }
            System.out.println("Thân try thành công");
        } catch (IllegalStateException e) {
            System.out.println("Catch: " + e.getMessage());
        } finally {
            System.out.println("Finally: kết thúc lượt");
        }
    }

    public static void main(String[] args) {
        run(false);
        System.out.println("---");
        run(true);
    }
}
```

Biên dịch và chạy với Java 17 trở lên:

```bash
javac -encoding UTF-8 TwrCatchFinallyOrderDemo.java
java TwrCatchFinallyOrderDemo
```

**Stdout chính xác:**

```text
Mở R
Dùng R
Thân try thành công
Đóng R
Finally: kết thúc lượt
---
Mở R
Dùng R
Đóng R
Catch: Lỗi thân try
Finally: kết thúc lượt
```

**Phân tích:**

- `run(false)`: khởi tạo → dùng → thân thành công → `close()` → bỏ qua `catch` → `finally`.
- `run(true)`: khởi tạo → dùng → ném `IllegalStateException` → `close()` → `catch` → `finally`.
- Dòng `Đóng R` luôn xuất hiện **trước `Catch: ...`** khi thân có lỗi.
- `finally` không cần gọi lại `p.close()`. Nó chỉ thực hiện công việc độc lập khi lượt xử lý kết thúc.
- Resource `Probe` có `close()` không khai báo checked exception, nên không phải xử lý `IOException`/`Exception` do riêng `close()` này.

Đây là tình huống quan sát rõ ràng nhất cho yêu cầu “TWR kết hợp catch và finally”.

## 4. Lỗi ở `close()` cũng có thể kích hoạt `catch`

Người học đôi khi cho rằng `catch` gắn với TWR chỉ xử lý lỗi trong **thân** khối `try`. Điều đó không đúng: ngoại lệ của `close()` có thể là ngoại lệ thoát khỏi toàn bộ phần được bảo vệ.

Ví dụ: thân hoàn tất thành công, nhưng resource không đóng được.

Tạo **`TwrCloseFailureDemo.java`**:

```java
import java.io.IOException;

public class TwrCloseFailureDemo {
    static final class FailingResource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            System.out.println("Close được gọi");
            throw new IOException("Không thể đóng");
        }
    }

    public static void main(String[] args) {
        try (FailingResource r = new FailingResource()) {
            System.out.println("Công việc đã xong");
        } catch (IOException e) {
            System.out.println("Catch: " + e.getMessage());
        } finally {
            System.out.println("Finally: ghi nhận kết thúc");
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 TwrCloseFailureDemo.java
java TwrCloseFailureDemo
```

Stdout:

```text
Công việc đã xong
Close được gọi
Catch: Không thể đóng
Finally: ghi nhận kết thúc
```

Điểm cần hiểu:

- Thân `try` không chủ động ném exception.
- Khi rời thân, Java gọi `close()` và nhận `IOException`.
- `catch(IOException)` nhận exception từ **đóng tài nguyên**.
- `finally` chạy sau handler.
- Vì `close()` công bố `throws IOException` nên handler checked này hợp lệ, dù thân `try` chỉ in một dòng.

**Khi nào lỗi `close()` là vấn đề nghiêm trọng?** Với writer/buffered stream, quá trình đóng có thể flush dữ liệu và thất bại; vì vậy không được mặc định xem `Công việc đã xong` là bằng chứng chắc chắn dữ liệu đã được ghi thành công và bền vững. Cần để lỗi đóng ảnh hưởng đến kết quả nghiệp vụ nếu hợp đồng yêu cầu.

## 5. Cả thân `try` và `close()` cùng lỗi: primary và suppressed

Đây là khác biệt quan trọng giữa TWR và một số mẫu `finally` đóng tài nguyên thủ công. Nếu thân đã ném exception **và** resource đóng cũng ném exception, Java thường giữ lỗi từ thân làm **primary exception** và gắn lỗi đóng vào `getSuppressed()`.

Hai quan hệ không đồng nghĩa:

- `getCause()`: “ngoại lệ này được gây ra bởi ngoại lệ nào?” — thường do code tạo wrapper và truyền `cause`.
- `getSuppressed()`: “những ngoại lệ nào bị đặt vào danh sách phụ?” — TWR tự dùng cơ chế này cho lỗi phát sinh khi đóng sau khi đã có primary.

Tạo **`TwrSuppressedWithFinallyDemo.java`**:

```java
import java.io.IOException;

public class TwrSuppressedWithFinallyDemo {
    static final class FailingClose implements AutoCloseable {
        @Override
        public void close() throws IOException {
            System.out.println("Close thất bại");
            throw new IOException("Lỗi đóng");
        }
    }

    public static void main(String[] args) {
        try (FailingClose r = new FailingClose()) {
            System.out.println("Thân đang chạy");
            throw new IllegalStateException("Lỗi xử lý");
        } catch (IllegalStateException e) {
            System.out.println("Primary: " + e.getMessage());
            System.out.println("Suppressed count: "
                    + e.getSuppressed().length);
            System.out.println("Suppressed[0]: "
                    + e.getSuppressed()[0].getMessage());
        } catch (IOException e) {
            System.out.println("Lỗi chỉ xảy ra khi đóng: "
                    + e.getMessage());
        } finally {
            System.out.println("Finally: hoàn tất");
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 TwrSuppressedWithFinallyDemo.java
java TwrSuppressedWithFinallyDemo
```

Stdout:

```text
Thân đang chạy
Close thất bại
Primary: Lỗi xử lý
Suppressed count: 1
Suppressed[0]: Lỗi đóng
Finally: hoàn tất
```

Giải thích theo thứ tự:

1. Thân `try` ném `IllegalStateException("Lỗi xử lý")`.
2. Java tiến hành đóng resource; `close()` ném `IOException("Lỗi đóng")`.
3. Ngoại lệ từ thân được giữ làm **primary**, lỗi đóng được gắn suppressed.
4. `catch(IllegalStateException)` được chọn theo kiểu của **primary**, không phải theo kiểu của suppressed exception.
5. `catch(IOException)` không chạy ở đường này, dù suppressed exception là `IOException`.
6. `finally` chạy sau `catch`.

Có mặt `catch(IOException)` ở chương trình là hợp lệ do `close()` có thể tạo `IOException` làm primary khi không có lỗi thân.

### 5.1. Nếu chỉ có lỗi đóng?

Nếu bỏ `throw new IllegalStateException(...)` trong thân, khi `close()` ném `IOException` sẽ không có primary từ thân. Khi ấy `IOException` đóng có thể trở thành **primary**, nên nhánh `catch(IOException)` sẽ chạy. Đừng viết handler `catch(IllegalStateException)` và tưởng nó luôn xử lý cả lỗi đóng.

### 5.2. Tại sao phải kiểm tra suppressed?

Nếu chỉ in `e.getMessage()`, người phát triển sẽ không thấy lỗi đóng. Với một logger ghi stack trace đầy đủ, suppressed thường có thể được hiển thị; nếu xây logging riêng, hãy cân nhắc lưu cả `getSuppressed()`.

Không nên “xử lý” một suppressed exception như thể lỗi chính đã biến mất. Đây là thông tin chẩn đoán bổ sung, không phải một luồng `catch` độc lập được tự kích hoạt.

## 6. Nhiều resource: đóng ngược thứ tự và xử lý lỗi sau khi đóng xong

Với:

```java
try (Resource first = openFirst();
     Resource second = openSecond();
     Resource third = openThird()) {
    work();
} catch (Exception e) {
    report(e);
} finally {
    finish();
}
```

Đây là mã mẫu, chưa phải chương trình độc lập. Thứ tự **khởi tạo**: `first → second → third`. Thứ tự **đóng**: `third → second → first`. Nếu `third.close()` phát sinh lỗi, Java vẫn tiến hành đóng các resource còn lại theo quy tắc ngôn ngữ; ngoại lệ tiếp theo có thể bị suppressed.

Tạo **`TwrMultipleResourcesDemo.java`**:

```java
import java.io.IOException;

public class TwrMultipleResourcesDemo {
    static final class Named implements AutoCloseable {
        private final String name;

        Named(String name) {
            this.name = name;
            System.out.println("Mở " + name);
        }

        @Override
        public void close() throws IOException {
            System.out.println("Đóng " + name);
            throw new IOException("close " + name);
        }
    }

    public static void main(String[] args) {
        try (Named a = new Named("A");
             Named b = new Named("B")) {
            System.out.println("Thân thành công");
        } catch (IOException e) {
            System.out.println("Primary: " + e.getMessage());
            for (Throwable suppressed : e.getSuppressed()) {
                System.out.println("Suppressed: "
                        + suppressed.getMessage());
            }
        } finally {
            System.out.println("Finally");
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 TwrMultipleResourcesDemo.java
java TwrMultipleResourcesDemo
```

Stdout:

```text
Mở A
Mở B
Thân thành công
Đóng B
Đóng A
Primary: close B
Suppressed: close A
Finally
```

Vì thân thành công, lỗi đóng đầu tiên (ở `B`) làm primary, còn lỗi khi đóng `A` bị suppressed vào đó. Handler chạy **sau khi Java đã thử đóng cả hai tài nguyên**; `finally` chạy sau handler.

### 6.1. Khi thân cũng có lỗi với nhiều tài nguyên

Giả sử thân ném `IllegalStateException("body")` trước khi đóng `B` và `A`, trong khi cả hai `close()` ném `IOException`. Khi đó:

- Primary là `IllegalStateException("body")`.
- Các lỗi đóng của `B` và `A` được ghi suppressed theo thứ tự chúng xảy ra.
- `catch(IllegalStateException)` nhận primary.
- `finally` chạy sau khi handler hoàn tất.

Đừng dựa vào `catch(IOException)` để đón từng lỗi đóng trong tình huống này. Các lỗi đó không trở thành primary khi đã có lỗi chính từ thân; cần đọc `getSuppressed()`.

### 6.2. Nếu khởi tạo resource thứ hai bị lỗi?

Một resource đã được khởi tạo thành công vẫn được đóng nếu quá trình mở resource sau thất bại. Chẳng hạn:

```java
try (Resource a = openA();
     Resource b = openBThatThrows()) {
    work();
} catch (IOException e) {
    report(e);
} finally {
    finish();
}
```

Đây là minh họa, giả sử `openA` trả resource, `openBThatThrows` có `throws IOException`. Nếu mở `a` thành công, mở `b` ném `IOException`, Java đóng `a` trước khi vào handler. Nếu `a.close()` cũng ném, lỗi mở `b` thường được giữ primary và lỗi đóng `a` bị suppressed.

TWR không thể đóng một đối tượng mà initializer **chưa trả về/khởi tạo thành công**. Nếu initializer tự mở resource phụ rồi ném lỗi giữa chừng, mã bên trong initializer phải chịu trách nhiệm không làm rò rỉ resource phụ đó.

## 7. `catch` xử lý loại lỗi nào? Chọn theo primary exception

Sau TWR, `catch` hoạt động theo **cơ chế chọn handler thông thường**: so kiểu runtime của đối tượng exception đang được ném với kiểu khai báo trong `catch`.

| Nguồn lỗi | Primary có thể là | Handler phù hợp |
| --- | --- | --- |
| Đọc file trong thân lỗi | `IOException` | `catch(IOException)` hoặc kiểu cha |
| Kiểm tra nghiệp vụ trong thân lỗi | `IllegalStateException` | `catch(IllegalStateException)` hoặc `catch(Exception)` |
| Thân thành công, `close()` ném `IOException` | `IOException` | `catch(IOException)` |
| Thân ném `IllegalStateException`, `close()` ném `IOException` | `IllegalStateException` với `IOException` suppressed | `catch(IllegalStateException)`; handler IOException không tự bắt suppressed |
| Khởi tạo resource ném `FileNotFoundException` | `FileNotFoundException` | `catch(FileNotFoundException)` hoặc `catch(IOException)` |
| Thân ném `AssertionError`, `close()` thành công | `AssertionError` | `catch(Error)` hoặc `catch(Throwable)` về mặt kiểu, **không** phải `catch(Exception)` |

Đặt handler cụ thể trước handler cha:

```java
try (java.io.BufferedReader reader = openReader()) {
    consume(reader);
} catch (java.io.FileNotFoundException e) {
    System.out.println("Không tìm thấy file");
} catch (java.io.IOException e) {
    System.out.println("Lỗi I/O");
} finally {
    System.out.println("Hoàn tất");
}
```

Đoạn trên là mẫu, `openReader` và `consume` phải được định nghĩa với những ngoại lệ checked phù hợp. Nếu đặt `catch(IOException)` trước `catch(FileNotFoundException)`, handler con phía sau bị che khuất và compiler từ chối.

**Nguyên tắc:** đừng dùng `catch(Exception)` chỉ để tránh nghiên cứu lỗi nào có thể phát sinh. Handler rộng có thể bắt các bug như `NullPointerException`, nhưng không tự phục hồi dữ liệu. Ở ranh giới một tác vụ độc lập, bắt rộng có thể hợp lý nếu luôn báo `FAILED` và cách ly trạng thái; trong phương thức đọc dữ liệu, bắt kiểu cụ thể thường sáng sủa hơn.

## 8. `finally` chạy sau `catch` — nhưng có những ngoại lệ quan trọng

Một `finally` gắn với TWR thường chạy:

- khi thân và close đều thành công;
- khi exception được `catch` xử lý thành công;
- khi exception không có handler phù hợp và tiếp tục lan ra;
- khi `catch` thực hiện `return` hoặc ném exception khác;
- khi thân `try` có một `return` hợp lệ.

Tuy nhiên **không thể hứa “luôn chạy trong mọi hoàn cảnh”**: nếu JVM/process bị kết thúc đột ngột, mất nguồn điện, lỗi native hoặc các tình huống không trở lại điều khiển Java bình thường, code không có bảo đảm tuyệt đối.

### 8.1. Không tự động nuốt exception gốc

Nếu `finally` chỉ ghi nhận kết thúc hoặc khôi phục một context an toàn, exception trước đó có thể tiếp tục lan truyền bình thường sau khi `finally` kết thúc.

Nếu `finally` thực hiện `return` hoặc ném exception mới, kết quả có thể **thay thế** exception đang chờ. Đây là lý do nên tránh `return` trong `finally` và cẩn trọng với các thao tác có thể ném lỗi mới tại đây.

Ví dụ sai về thiết kế:

```java
// Phản ví dụ trong method, không phải chương trình độc lập.
try (Resource resource = acquire()) {
    perform(resource);
} finally {
    return; // Có thể che mất exception đang lan truyền.
}
```

Nếu `perform` ném exception, một `return` trong `finally` có thể khiến caller không nhận được lỗi đó. TWR không thể bảo toàn primary exception khi người viết cố tình điều khiển lại luồng trong `finally`.

### 8.2. Exception phát sinh trong `finally` không được `catch` cùng cấp bắt

```java
try (Resource resource = acquire()) {
    System.out.println("Công việc");
} catch (Exception e) {
    System.out.println("Lỗi trong try/close");
} finally {
    throw new IllegalStateException("Lỗi finally");
}
```

Đây là mã mẫu cần định nghĩa resource. `IllegalStateException` được ném **trong `finally`**, không phải bên trong phần thân TWR đã được `catch` này bảo vệ, nên handler `catch(Exception)` kèm theo không tự bắt được ngoại lệ mới đó. Caller ở ngoài mới có thể nhận.

**Khuyến nghị:** để phần `finally` nhỏ, đơn giản, tránh những thao tác có thể thất bại. Với công việc đóng tài nguyên, giao cho TWR thay vì lặp lại trong `finally`.

## 9. Chương trình Java 17: ngoại lệ mới ở `catch`, `finally` vẫn chạy

Tạo **`TwrCatchRethrowFinallyDemo.java`**:

```java
public class TwrCatchRethrowFinallyDemo {
    static final class Resource implements AutoCloseable {
        @Override
        public void close() {
            System.out.println("Close");
        }
    }

    static void doWork() {
        try (Resource r = new Resource()) {
            System.out.println("Thân try");
            throw new IllegalArgumentException("Input sai");
        } catch (IllegalArgumentException e) {
            System.out.println("Catch: " + e.getMessage());
            throw new IllegalStateException("Không hoàn tất", e);
        } finally {
            System.out.println("Finally: giải phóng context");
        }
    }

    public static void main(String[] args) {
        try {
            doWork();
        } catch (IllegalStateException e) {
            System.out.println("Caller: " + e.getMessage());
            System.out.println("Cause: "
                    + e.getCause().getClass().getSimpleName());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 TwrCatchRethrowFinallyDemo.java
java TwrCatchRethrowFinallyDemo
```

Stdout:

```text
Thân try
Close
Catch: Input sai
Finally: giải phóng context
Caller: Không hoàn tất
Cause: IllegalArgumentException
```

Lưu ý:

1. `close()` chạy trước handler nội bộ.
2. Handler tạo **wrapper `IllegalStateException`** mới, giữ `IllegalArgumentException` gốc làm `cause`.
3. `finally` vẫn chạy khi handler chuẩn bị ném wrapper ra ngoài.
4. `catch` ở `main` nhận wrapper **sau** khi phần `finally` trong `doWork` hoàn tất.
5. `getCause()` là ngoại lệ gốc; không lẫn với `getSuppressed()` dùng cho lỗi đóng tài nguyên.

Để đơn giản ví dụ, `Resource.close()` không ném checked exception. Với resource thực, thiết kế hợp đồng `close()` tương ứng.

## 10. Trường hợp `return` trong thân try và lỗi đóng

TWR gọi `close()` **trước khi hoàn tất `return` ra caller**. Nếu `close()` thất bại, code có thể không trả về giá trị đã chuẩn bị.

Tạo **`TwrReturnCloseDemo.java`**:

```java
import java.io.IOException;

public class TwrReturnCloseDemo {
    static final class CloseFails implements AutoCloseable {
        @Override
        public void close() throws IOException {
            System.out.println("Close");
            throw new IOException("Lỗi đóng");
        }
    }

    static String calculate() {
        try (CloseFails resource = new CloseFails()) {
            System.out.println("Chuẩn bị return");
            return "SUCCESS";
        } catch (IOException e) {
            System.out.println("Catch: " + e.getMessage());
            return "FALLBACK";
        } finally {
            System.out.println("Finally");
        }
    }

    public static void main(String[] args) {
        System.out.println("Kết quả: " + calculate());
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 TwrReturnCloseDemo.java
java TwrReturnCloseDemo
```

Stdout:

```text
Chuẩn bị return
Close
Catch: Lỗi đóng
Finally
Kết quả: FALLBACK
```

Bởi `close()` ném lỗi, lời gọi không trả về `"SUCCESS"` như thân `try` đã chuẩn bị. `catch(IOException)` chọn một đường trả về khác; `finally` thực hiện trước khi `calculate()` trả `"FALLBACK"` cho caller.

**Trong thực tế**, chỉ dùng một “fallback” nếu thật sự có cách phục hồi hợp lệ. Nếu đóng writer thất bại, không nên trả “thành công” giả chỉ vì có một `catch`. Ví dụ này chứng minh thứ tự điều khiển, không khuyến khích nuốt lỗi đóng.

## 11. Phạm vi biến resource: khác nhau giữa khai báo trong `try` và biến bên ngoài

### 11.1. Java 7: tài nguyên khai báo trong ngoặc `try (...)`

```java
try (java.io.BufferedReader reader = openReader()) {
    System.out.println(reader.readLine());
} catch (java.io.IOException e) {
    // reader KHÔNG nằm trong scope ở đây.
    System.out.println(e.getMessage());
} finally {
    // reader KHÔNG nằm trong scope ở đây.
    System.out.println("Hoàn tất");
}
```

Đây là đoạn minh họa, giả định `openReader()` trả `BufferedReader` và có hợp đồng checked exception phù hợp. Biến `reader` khai báo trong resource specification thuộc phạm vi resource specification và thân TWR; **không thể truy cập trực tiếp trong `catch`/`finally` gắn ngoài**.

Ví dụ cố ý sai:

```java
try (java.io.BufferedReader reader = openReader()) {
    System.out.println(reader.readLine());
} catch (java.io.IOException e) {
    reader.close(); // Không biên dịch: reader không còn nằm trong scope.
}
```

Không cần tìm cách gọi thêm `reader.close()` ở handler; TWR đã đảm nhiệm việc đóng (nếu resource đã khởi tạo thành công).

### 11.2. Java 9+: biến đã khai báo bên ngoài và effectively final

Từ Java 9, cú pháp sau hợp lệ với biến `final` hoặc **effectively final**:

```java
java.io.BufferedReader reader = openReader();
try (reader) {
    System.out.println(reader.readLine());
} catch (java.io.IOException e) {
    System.out.println("Lỗi: " + e.getMessage());
} finally {
    System.out.println("Đã rời try");
}
```

Biến `reader` vẫn nằm trong phạm vi từ vựng bên ngoài, nên handler có thể tham chiếu đến biến đó nếu cần quan sát thuộc tính. **Nhưng đối tượng đã được TWR đóng khi rời khối**, không được hiểu việc biến còn tồn tại đồng nghĩa resource vẫn mở.

Một bất tiện của việc mở resource **trước** `try (reader)`: nếu code giữa lời gọi mở và việc vào TWR phát sinh exception, resource có thể chưa được chuyển quyền quản lý cho TWR. Khi có thể, khai báo và khởi tạo ngay trong `try (...)` thường giảm rủi ro.

**Lưu ý phiên bản:** TWR xuất hiện từ Java 7; dùng biến có sẵn `try (reader)` được hỗ trợ từ Java 9; cú pháp `var` trong khai báo biến local/resource xuất hiện từ Java 10. Trên Java 17 đều có thể dùng đúng ngữ cảnh.

## 12. `catch`, `finally` và checked exception từ `close()`

`AutoCloseable.close()` khai báo `throws Exception`. Một lớp thực thi có thể thu hẹp loại lỗi, ví dụ `Closeable.close() throws IOException` hoặc `close()` không khai báo checked exception nào.

Compiler xét checked exception có thể phát sinh từ:

1. Resource initializer (constructor hoặc method tạo resource).
2. Lời gọi/câu lệnh bên trong thân `try`.
3. `close()` trên kiểu resource mà compiler nhìn thấy.

Ví dụ:

```java
static void useResource() throws Exception {
    try (AutoCloseable resource = acquire()) {
        System.out.println("Sử dụng");
    } finally {
        System.out.println("Kết thúc");
    }
}
```

Đây là phương thức minh họa cần định nghĩa `acquire()`. `finally` có mặt nhưng **không bắt** checked exception; vì kiểu tĩnh của resource là `AutoCloseable`, `close()` có thể ném `Exception`, nên khai báo `throws Exception` có thể cần thiết.

Nếu không muốn khai báo rộng, có thể bắt loại phù hợp với API:

```java
static void run() {
    try (MyResource resource = new MyResource()) {
        System.out.println("Thao tác");
    } catch (java.io.IOException e) {
        System.out.println("I/O thất bại: " + e.getMessage());
    } finally {
        System.out.println("Kết thúc");
    }
}
```

Mã này **chỉ là minh họa**: giả định `MyResource.close() throws IOException` và constructor không khai báo checked exception khác. Không thể tùy tiện dùng `catch(IOException)` nếu resource và thân không có nguồn checked exception tương thích; compiler sẽ báo “exception IOException is never thrown in body of corresponding try”.

### 12.1. Dùng `catch(Exception)` có cần thiết không?

Không mặc định. Nếu resource chỉ có khả năng ném `IOException`, handler cụ thể thường đủ. Nếu cần xử lý nhiều loại checked exception với **cùng** chính sách, có thể dùng multi-catch:

```java
try (Resource resource = acquire()) {
    process(resource);
} catch (java.io.IOException | java.sql.SQLException e) {
    reportFailure(e);
} finally {
    markFinished();
}
```

Ví dụ phụ thuộc vào định nghĩa `Resource`/`acquire`/`process` thực sự có khả năng ném cả hai loại. `catch(Exception)` rộng hơn multi-catch vì còn bắt nhiều unchecked exceptions. Nếu đặt handler rộng, cần bảo đảm nó không nuốt lỗi và giả rằng công việc hoàn tất thành công.

## 13. Nested try-with-resources và vị trí xử lý

Một chương trình có thể có TWR bên trong TWR khác, mỗi khối sở hữu resource riêng.

Ví dụ:

```java
try (OuterResource outer = openOuter()) {
    try (InnerResource inner = openInner(outer)) {
        use(inner);
    } catch (InnerException e) {
        handleInner(e);
    } finally {
        afterInner();
    }
} catch (OuterException e) {
    handleOuter(e);
} finally {
    afterOuter();
}
```

Đây là sơ đồ mã giả theo cú pháp Java; các kiểu và method chưa được định nghĩa.

Thứ tự nếu cả hai resource đã được mở và đường thực thi đi bình thường:

1. Thân TWR trong chạy.
2. `inner.close()`.
3. `finally` trong chạy (và `catch` trong nếu lỗi phù hợp).
4. Thân TWR ngoài tiếp tục hoặc kết thúc.
5. `outer.close()`.
6. `catch`/`finally` ngoài chạy theo điều kiện.

Nếu handler trong **ném lại** exception hoặc ném wrapper, exception có thể lan ra phạm vi ngoài **sau** phần dọn dẹp trong. Khi outer resource đóng và phát sinh thêm exception, logic primary/suppressed được áp dụng theo các mức TWR liên quan. Không thể mặc định hai `finally` chạy cùng lúc hoặc handler ngoài được chọn trước handler trong.

### 13.1. Exception được ném trong `catch` có được handler cùng cấp bắt tiếp không?

**Không.** Các `catch` đi kèm cùng một `try` là những handler thay thế đối với ngoại lệ thoát khỏi phần TWR được bảo vệ. Một exception **mới** do chính handler ném sẽ không được những `catch` tiếp theo của cùng cấu trúc tự bắt; nó tiếp tục ra một `try` bao ngoài hoặc caller.

Ví dụ trong `TwrCatchRethrowFinallyDemo`: handler trong tạo `IllegalStateException` mới, phần `finally` vẫn chạy, còn handler ở `main` mới nhận wrapper.

### 13.2. `finally` bên ngoài TWR khác với `finally` gắn cùng TWR

Đôi khi muốn tách khối quản lý resource và handler:

```java
try {
    try (Resource resource = acquire()) {
        perform(resource);
    }
} catch (Exception e) {
    report(e);
} finally {
    after();
}
```

Đây là mẫu minh họa. TWR trong tự đóng resource khi thoát; `catch` của outer try được xét sau khi inner TWR hoàn tất. Kết quả về thứ tự **vẫn là close trước catch/finally ngoài**, nhưng ranh giới mỗi cấu trúc cần được đọc đúng để suy luận nguồn lỗi.

## 14. Đừng nhầm tự động đóng với transaction và khôi phục trạng thái

TWR **chỉ** quản lý những resource triển khai `AutoCloseable` được khai báo/tham chiếu trong resource specification. Nó không tự cung cấp:

- atomicity cho nhiều phép ghi dữ liệu;
- rollback thay đổi trên các đối tượng trong heap;
- rollback giao dịch JDBC sau một lỗi, trừ khi chính giao thức/driver có hành vi riêng và được cấu hình phù hợp;
- bảo đảm một yêu cầu HTTP chưa xử lý ở máy chủ nếu client ném exception;
- dữ liệu đã flush xuống thiết bị lưu trữ một cách bền vững khi mất điện;
- tự thử lại an toàn sau khi kết quả nghiệp vụ chưa chắc chắn.

Ví dụ trong JDBC:

```java
// Mã minh họa, phụ thuộc DataSource và SQL thực tế.
try (java.sql.Connection connection = dataSource.getConnection()) {
    connection.setAutoCommit(false);
    try {
        updateInventory(connection);
        insertOrder(connection);
        connection.commit();
    } catch (java.sql.SQLException e) {
        connection.rollback();
        throw e;
    }
} catch (java.sql.SQLException e) {
    reportFailure(e);
} finally {
    markRequestFinished();
}
```

Ở đây **`rollback()` là lời gọi tường minh**, không phải do TWR tự chèn. Nếu `rollback()` cũng ném lỗi, cần chiến lược chẩn đoán, nhất là khi đã có lỗi gốc. Hành vi khi đóng connection, trả connection về pool, hay tình trạng giao dịch khi process chết phụ thuộc API/driver/pool; không suy luận transaction an toàn chỉ từ cú pháp `try(connection)`.

Trong hệ thống thực tế, nên đặt trách nhiệm rõ ràng: transaction layer quyết định commit/rollback; TWR quản lý vòng đời connection; handler báo trạng thái thất bại cho caller và phần `finally` xử lý những công việc kết thúc không thuộc transaction.

## 15. Những sai lầm thường gặp

| Nhầm lẫn | Thực tế |
| --- | --- |
| “Thân `try` ném lỗi thì `catch` chạy trước khi resource đóng.” | TWR đóng các resource đã khởi tạo thành công **trước** handler gắn trực tiếp. |
| “Có TWR thì không cần bao giờ có `catch`.” | TWR tự đóng; nếu tầng này cần xử lý lỗi, vẫn dùng `catch` phù hợp. |
| “Có TWR thì `finally` bị cấm.” | `finally` vẫn hợp lệ và hữu ích cho công việc hoàn tất khác. |
| “Phải viết `finally { resource.close(); }`.” | Resource đã được TWR tự đóng; đóng lặp có thể gây side effect/lỗi. |
| “Lỗi `close()` không thể đi vào `catch`.” | Nếu close lỗi và exception đó trở thành primary, handler tương thích sẽ nhận. |
| “Thân và close đều lỗi thì catch bắt lỗi close.” | Thông thường lỗi thân là primary, lỗi close là suppressed. |
| “`getSuppressed()` giống `getCause()`.” | Hai quan hệ khác nhau; suppressed mô tả lỗi phụ, cause mô tả nguyên nhân của wrapper. |
| “Đã vào `catch` thì `finally` không chạy.” | `finally` vẫn thực hiện theo đường điều khiển Java bình thường. |
| “`finally` được phép `return` an toàn sau exception.” | `return` trong `finally` có thể che exception; nên tránh. |
| “Exception ném từ `finally` được `catch` trước đó bắt.” | Không; handler cùng cấp không tự nhận lỗi phát sinh ở `finally` của nó. |
| “Biến resource khai báo trong `try(...)` dùng được ở `catch`.” | Không, phạm vi biến resource khai báo trong specification không kéo dài sang handler gắn ngoài. |
| “Biến dùng trong `try(reader)` Java 9 vẫn chắc còn mở sau đó.” | Biến còn trong scope, nhưng resource đã đóng. |
| “`catch(IOException)` luôn hợp lệ trong TWR.” | Phải có nguồn checked `IOException` có thể phát sinh từ initializer, thân hoặc close. |
| “TWR tự rollback file, database, HTTP.” | TWR tự gọi `close()`; transaction và trạng thái nghiệp vụ là trách nhiệm khác. |
| “Đóng luôn tài nguyên do caller truyền vào là an toàn.” | Cần thỏa thuận ownership; đóng resource chia sẻ có thể phá code phía ngoài. |
| “`catch(Throwable)` giúp chương trình vượt qua mọi lỗi JVM.” | Có thể bắt `Error` về mặt kiểu nhưng không bảo đảm phục hồi an toàn hay process luôn tồn tại. |

## 16. Bài tập thực hành

### Bài 1 — Dự đoán thứ tự

Không chạy chương trình, hãy dự đoán stdout của `TwrCatchFinallyOrderDemo` với `run(false)` rồi `run(true)`. Chỉ ra chính xác vị trí các dòng `Đóng R`, `Catch: ...` và `Finally: ...`. Sau đó chạy để đối chiếu.

### Bài 2 — Lỗi chỉ ở `close()`

Chạy `TwrCloseFailureDemo`. Sửa `close()` để không ném exception; giải thích vì sao handler `catch(IOException)` có thể trở nên không hợp lệ **nếu không còn nguồn checked IOException khác**. Hãy đổi code hợp lý và không thêm handler checked thừa.

### Bài 3 — Primary và suppressed

Chạy `TwrSuppressedWithFinallyDemo`. Đổi lỗi thân từ `IllegalStateException` sang `IOException`, đồng thời giữ `close()` ném `IOException`. Dự đoán primary message, suppressed count và handler nào nhận exception. Viết test xác nhận `getSuppressed()[0].getMessage()`.

### Bài 4 — Thêm resource thứ ba

Thêm `Named c = new Named("C")` vào `TwrMultipleResourcesDemo`. Dự đoán thứ tự `Mở A/B/C`, `Đóng C/B/A`, primary là lỗi đóng của resource nào và thứ tự các suppressed. Sau đó kiểm tra chương trình bằng Java 17.

### Bài 5 — `return` bị lỗi đóng thay thế

Trong `TwrReturnCloseDemo`, đổi `CloseFails.close()` thành method in `Close` nhưng không ném IOException. Điều chỉnh `catch` nếu compiler yêu cầu. Phân biệt output khi resource đóng thành công với output khi `close()` phát sinh checked exception.

### Bài 6 — Exception ném trong handler

Trong `TwrCatchRethrowFinallyDemo`, bỏ truyền `e` vào constructor `IllegalStateException`. Quan sát xem `getCause()` có còn là `IllegalArgumentException` không. Vì sao cần giữ cause khi đổi exception tầng thấp thành wrapper tầng cao?

### Bài 7 — Phạm vi biến resource

Viết hai phiên bản: Java 7 khai báo `BufferedReader r` ngay trong `try(...)`, và Java 9+ khai báo biến trước rồi dùng `try(r)`. So sánh việc tham chiếu tới `r` trong `catch`/`finally`, sau đó trả lời vì sao **dù biến còn tồn tại thì resource vẫn đã được đóng**.

### Bài 8 — Thiết kế với ownership

Một method nhận `InputStream` từ caller và chỉ đọc vài byte. Nên tự đóng input bằng TWR hay trả quyền đóng về caller? Đưa ra hai hợp đồng API hợp lý tương ứng, mô tả rõ trách nhiệm đóng và viết test theo hợp đồng đã chọn.

### Bài 9 — Transaction không tự rollback

Trong ví dụ JDBC, nếu cập nhật tồn kho thành công nhưng chèn đơn hàng thất bại, phần nào của code chịu trách nhiệm rollback? `catch(SQLException)` trong khối transaction được gọi ở đâu, TWR đóng connection ở đâu, `finally` có nên dùng để commit không? Giải thích vì sao không được chỉ đóng connection rồi coi như đã rollback chắc chắn.

## 17. Checklist vận dụng TWR + catch + finally

- [ ] Resource thật sự triển khai `AutoCloseable`/`Closeable` và tầng hiện tại có quyền sở hữu để đóng chưa?
- [ ] Tôi đã viết `try(...)` khai báo/tái sử dụng resource hợp lệ với phiên bản Java của dự án chưa?
- [ ] Tôi nhớ **close trước catch và finally** đối với cấu trúc TWR gắn handler trực tiếp chưa?
- [ ] Tôi đã xét thứ tự **đóng ngược** khi có nhiều resource chưa?
- [ ] Nếu khởi tạo resource thứ hai lỗi, resource thứ nhất vẫn được TWR dọn đúng chưa?
- [ ] Các handler có thể nhận lỗi từ initializer, thân và `close()` không?
- [ ] Nếu thân và close cùng thất bại, tôi có biết cách xem `getSuppressed()` mà không mất primary không?
- [ ] Tôi có đặt `catch` theo kiểu phù hợp, cụ thể trước tổng quát chưa?
- [ ] `finally` chỉ làm công việc kết thúc khác, không đóng lại resource đã được TWR quản lý chứ?
- [ ] Tôi đã tránh `return` và ngoại lệ mới không cần thiết trong `finally` chưa?
- [ ] Tôi có biết `catch` cùng cấp không bắt exception mới ném từ `catch` hoặc `finally` của chính nó không?
- [ ] Tôi không truy cập biến resource ngoài phạm vi khai báo của nó chứ?
- [ ] Tôi đã giải quyết checked exception từ constructor, thân, `close()` theo hợp đồng compiler chưa?
- [ ] Tôi không nhầm tự động `close()` với commit/rollback transaction hay độ bền dữ liệu chưa?
- [ ] Test đã kiểm tra đường thành công, lỗi thân, lỗi đóng, nhiều resource, primary/suppressed, và thứ tự `finally` chưa?

**Kết luận:** TWR giúp sở hữu và đóng tài nguyên đúng quy tắc, `catch` xử lý **exception thoát khỏi phần TWR sau khi đóng**, còn `finally` đảm nhiệm công việc kết thúc độc lập. Hãy ghi nhớ **khởi tạo → thân → đóng → catch phù hợp → finally**, quan sát cả **primary lẫn suppressed** và không để hành động trong `finally` che mất lỗi gốc. Đó là nền tảng để xử lý lỗi Java đáng tin cậy khi có tài nguyên cần giải phóng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="try-with-resources-011">Tài nguyên được đóng khi return hoặc thoát đột ngột</a>

<details>
<summary>Xem chi tiết</summary>

# Tài nguyên được đóng khi `return` hoặc thoát đột ngột trong `try-with-resources`

Trong Java, `try-with-resources` quản lý vòng đời của các đối tượng `AutoCloseable` theo một **phạm vi thực thi**. Khi chương trình rời phạm vi này, Java tự gọi `close()` trên các tài nguyên đã được khởi tạo thành công, kể cả nếu phần thân `try` không chạy đến dấu ngoặc nhọn đóng.

Các đường thoát đáng chú ý gồm **`return`**, **`throw`**, **`break`** và **`continue`**. Điều đó không chỉ có nghĩa “Java cố đóng tài nguyên”: trình tự đóng, ngoại lệ phát sinh từ `close()`, và loại kết quả cuối cùng được gửi tới caller đều có quy tắc cụ thể.

**Quy tắc cốt lõi:**

- `return` trong phần thân `try-with-resources` **không bỏ qua `close()`**. Tài nguyên được đóng trước khi caller nhận kết quả.
- Nếu phần thân ném ngoại lệ, Java vẫn đóng các tài nguyên đã mở; nếu `close()` cũng ném, lỗi đóng thường được **suppressed** dưới ngoại lệ chính.
- Nếu phần thân định `return` nhưng `close()` ném lỗi, caller **có thể nhận ngoại lệ thay vì nhận giá trị trả về**.
- Với nhiều resource, Java khởi tạo theo thứ tự khai báo và đóng theo **thứ tự ngược**.
- Không được suy ra rằng resource chắc chắn đã đóng trong mọi cách process/JVM bị kết thúc cưỡng bức; đây là cơ chế gắn với luồng thực thi Java, không phải cam kết vượt qua mọi sự cố hệ thống.

## 1. Cú pháp và hợp đồng của `AutoCloseable`

Dạng cơ bản:

```java
try (Resource resource = openResource()) {
    use(resource);
}
```

Đây là **khung minh họa**, yêu cầu `Resource` triển khai `AutoCloseable` và các phương thức được định nghĩa. `resource` được khai báo trong *resource specification*, là phần nằm giữa cặp ngoặc tròn sau `try`.

Interface `java.lang.AutoCloseable` định nghĩa phương thức `close()`. Nhiều API Java như `InputStream`, `Reader`, `Writer`, `Scanner` và `java.sql.Connection` cung cấp các tài nguyên tương thích. Khi dùng `try-with-resources`:

1. Java đánh giá biểu thức khởi tạo resource.
2. Nếu khởi tạo thành công, resource được đăng ký để đóng khi rời phạm vi.
3. Phần thân `try` được thực thi.
4. Khi chuẩn bị rời khối, Java gọi `close()` cho những resource cần đóng.
5. Sau quá trình đóng, điều khiển chuyển tới `catch`, `finally` hoặc caller theo lý do hoàn tất cuối cùng.

Bạn có thể kết hợp với `catch` và `finally`:

```java
try (Resource resource = openResource()) {
    use(resource);
} catch (SomeException ex) {
    handle(ex);
} finally {
    afterAttempt();
}
```

Đây cũng là **khung minh họa**, không phải chương trình Java độc lập. Một điểm quan trọng: với `catch`/`finally` gắn cùng cấu trúc, bước **đóng resource xảy ra trước khi handler hoặc `finally` ấy chạy**.

Không cần tự gọi `resource.close()` trong `finally` chỉ để đảm bảo đóng. Làm vậy dễ dẫn đến đóng lặp và khó xử lý lỗi cleanup.

## 2. “Thoát đột ngột” (*abrupt completion*) nghĩa là gì?

Một câu lệnh hoặc khối Java có thể **hoàn tất bình thường** (*normal completion*) hoặc **hoàn tất bất thường** (*abrupt completion*).

Ví dụ các lý do hoàn tất bất thường:

| Nguyên nhân | Ví dụ | Hệ quả điều khiển |
| --- | --- | --- |
| `return` | `return result;` | Chuẩn bị rời phương thức |
| `throw` | `throw new IOException(...);` | Chuẩn bị lan truyền ngoại lệ |
| `break` | `break;` | Chuẩn bị thoát vòng lặp/`switch` phù hợp |
| `continue` | `continue;` | Chuẩn bị chuyển sang lần lặp kế |
| Ngoại lệ từ lời gọi | `readLine()` ném `IOException` | Câu lệnh hiện tại hoàn tất bất thường |

`try-with-resources` được thiết kế để **đóng tài nguyên trên những đường thoát đó**. Nó không đợi chương trình thực thi đến dấu `}` của phần thân theo đường thông thường mới gọi `close()`.

Ví dụ:

```java
static String firstLine(java.nio.file.Path path)
        throws java.io.IOException {
    try (var reader = java.nio.file.Files.newBufferedReader(path)) {
        return reader.readLine();
    }
}
```

Đây là một **phương thức minh họa hợp lệ khi đặt trong lớp Java**. `reader.readLine()` được đánh giá để chuẩn bị kết quả, nhưng trước khi caller nhận chuỗi `String`, Java đóng reader. Nếu việc đọc hoặc đóng ném `IOException`, phương thức có thể ném ngoại lệ thay vì trả kết quả.

**Không nhầm:** một resource còn nằm trong scope của khối khác không có nghĩa còn mở sau khi khối quản lý của nó đã kết thúc. Khi dùng `try (existingResource)` với biến khai báo bên ngoài, tên biến thậm chí có thể vẫn dùng được, nhưng đối tượng đã được đóng.

## 3. `return` trong phần thân: đóng trước khi phương thức trả kết quả

Xem:

```java
static int calculate() {
    try (TrackedResource resource = new TrackedResource()) {
        return 42;
    }
}
```

Đây là **mã minh họa**, giả định `TrackedResource implements AutoCloseable` và `close()` có hợp đồng phù hợp.

Diễn biến logic:

```text
mở resource
    ↓
vào thân try
    ↓
đánh giá biểu thức return 42
    ↓
Java gọi resource.close()
    ↓
nếu close hoàn tất bình thường
    ↓
caller nhận 42
```

Nếu `close()` ném một ngoại lệ không được xử lý, đường đi thay đổi: `return 42` có thể bị **thay bằng ngoại lệ từ `close()`**. Vì vậy, nói “gặp `return` thì resource vẫn được đóng” là đúng, nhưng chưa đầy đủ. Phải hỏi tiếp “`close()` có thành công không?” để biết caller thật sự nhận gì.

### Giá trị `return` được tính trước khi đóng

Với biểu thức trả về:

```java
try (Resource resource = openResource()) {
    return compute(resource);
}
```

thứ tự về mặt ngữ nghĩa là **tính `compute(resource)`**, chuẩn bị giá trị, rồi đóng resource, sau đó mới hoàn tất việc trả nếu không có một lý do kết thúc khác.

Nếu `compute(resource)` ném ngoại lệ trước khi có kết quả, Java vẫn đóng resource và tiếp tục xử lý đường ngoại lệ.

Điều này cũng giải thích vì sao trả về **giá trị độc lập** như `int`, `String` đã đọc hoặc DTO đã vật hóa thường phù hợp hơn trả về một đối tượng còn cần resource đang mở. Đừng trả ra một `Stream` hoặc iterator đọc lười nếu nguồn dữ liệu bị đóng trước khi caller sử dụng.

## 4. Ví dụ Java 17+ hoàn chỉnh: `return`, `throw`, `break`, `continue`

Để quan sát chính xác trình tự mà không phụ thuộc file hoặc hệ điều hành, dùng một resource giả lập tự in khi mở và đóng.

Lưu tệp **`ResourceExitFlowDemo.java`**:

```java
public class ResourceExitFlowDemo {

    static final class Trace implements AutoCloseable {
        private final String name;

        Trace(String name) {
            this.name = name;
            System.out.println("open " + name);
        }

        @Override
        public void close() {
            System.out.println("close " + name);
        }
    }

    static int returnCase() {
        System.out.println("CASE return");

        try (Trace first = new Trace("A");
             Trace second = new Trace("B")) {
            System.out.println("body: prepare return 7");
            return 7;
        }
    }

    static void throwCase() {
        System.out.println("CASE throw");

        try (Trace resource = new Trace("T")) {
            System.out.println("body: throw");
            throw new IllegalArgumentException("bad request");
        } catch (IllegalArgumentException ex) {
            System.out.println("catch: " + ex.getMessage());
        }
    }

    static void breakCase() {
        System.out.println("CASE break");

        for (int i = 0; i < 2; i++) {
            try (Trace resource = new Trace("break-" + i)) {
                System.out.println("body: break at " + i);
                break;
            }
        }

        System.out.println("after break loop");
    }

    static void continueCase() {
        System.out.println("CASE continue");

        for (int i = 0; i < 2; i++) {
            try (Trace resource = new Trace("continue-" + i)) {
                System.out.println("body: continue at " + i);
                continue;
            }
        }

        System.out.println("after continue loop");
    }

    public static void main(String[] args) {
        System.out.println("returned=" + returnCase());
        throwCase();
        breakCase();
        continueCase();
    }
}
```

Biên dịch/chạy với **JDK 17 trở lên**:

```bash
javac -encoding UTF-8 ResourceExitFlowDemo.java
java ResourceExitFlowDemo
```

**Stdout chính xác:**

```text
CASE return
open A
open B
body: prepare return 7
close B
close A
returned=7
CASE throw
open T
body: throw
close T
catch: bad request
CASE break
open break-0
body: break at 0
close break-0
after break loop
CASE continue
open continue-0
body: continue at 0
close continue-0
open continue-1
body: continue at 1
close continue-1
after continue loop
```

### Phân tích các nhánh

**`returnCase()`:**

- Mở A rồi B theo thứ tự khai báo.
- Phần thân chạy `return 7`.
- Java đóng B rồi A (**đảo thứ tự khai báo**).
- Sau khi cả hai `close()` hoàn tất bình thường, caller mới in `returned=7`.

Nếu `return` làm bỏ qua việc đóng, hai dòng `close` sẽ không xuất hiện trước `returned=7`. Ví dụ chứng minh điều ngược lại.

**`throwCase()`:**

- Phần thân ném `IllegalArgumentException`.
- Trước khi handler `catch` gắn cùng cấu trúc xử lý ngoại lệ, Java gọi `close T`.
- Handler in `catch: bad request`, nghĩa là nó nhận được ngoại lệ từ phần thân sau khi tài nguyên đã được đóng.

**`breakCase()`:**

- Vòng lặp bắt đầu ở `i=0`, resource `break-0` được tạo.
- `break` yêu cầu rời vòng lặp, nhưng Java **đóng resource trước**.
- Không chạy lần lặp `i=1`; sau đó in `after break loop`.

**`continueCase()`:**

- Ở mỗi lần lặp, resource được mở trong `try` riêng của lần đó.
- `continue` yêu cầu chuyển ngay đến bước tiếp theo của vòng lặp, nhưng trước hết resource hiện tại được đóng.
- Do đó thứ tự luôn là mở → dùng → đóng cho từng lần lặp, rồi mới chuyển sang lần kế tiếp.

Cả hai kiểu thoát vòng lặp đều kích hoạt việc đóng. Tuy vậy, nếu `close()` ném ngoại lệ, đường `break` hoặc `continue` **có thể bị thay bằng đường ném ngoại lệ**, thay vì chuyển tới đích dự kiến.

## 5. Khi phần thân ném ngoại lệ, `close()` chạy trước handler ngoài

Với:

```java
try (var reader = java.nio.file.Files.newBufferedReader(path)) {
    throw new IllegalArgumentException("invalid content");
} catch (IllegalArgumentException ex) {
    System.out.println("caught: " + ex.getMessage());
}
```

Đây là **đoạn minh họa**, cần biến `path` kiểu `Path`, và cần xử lý/khai báo checked `IOException` phát sinh khi mở/đóng reader. Phần thân ném một ngoại lệ unchecked, nhưng điều đó không bỏ qua thao tác `reader.close()`.

Với `try-with-resources` gắn `catch`, cơ chế tương đương về mặt trực giác:

```text
mở resource
  → chạy thân try
  → ngoại lệ xuất hiện
  → đóng resource
  → chọn catch của cấu trúc
  → xử lý hoặc tiếp tục lan truyền
```

Điều này có ích khi `catch` ghi log lỗi sau một lần xử lý: resource được đăng ký trong cấu trúc đã qua bước đóng trước khi handler chạy. Nhưng **không** được kết luận rằng việc đóng luôn thành công; nếu `close()` thất bại, cần kiểm tra exception hoặc suppressed exceptions.

### Nếu handler ở tầng ngoài, có khác không?

Giả sử:

```java
try {
    try (Resource resource = openResource()) {
        doWork(resource);
    }
} catch (RuntimeException ex) {
    recoverOrReport(ex);
}
```

Đây là **khung minh họa**. Khi ngoại lệ unchecked thoát khỏi `try` trong, resource được đóng trước khi điều khiển chuyển đến handler bên ngoài. Phạm vi xử lý lồng nhau không làm mất cơ chế tự động đóng, vì mỗi `try-with-resources` bảo vệ vòng đời tài nguyên của chính nó.

## 6. Nếu `close()` ném lỗi khi phần thân định `return`

Đây là trường hợp dễ gây hiểu lầm:

```java
static int compute() {
    try (FailingResource resource = new FailingResource()) {
        return 123;
    }
}
```

Giả sử `FailingResource.close()` ném `IllegalStateException("close failed")`. Khi ấy:

1. Phần thân đã đánh giá `123` và chuẩn bị trả.
2. `close()` được gọi theo cơ chế `try-with-resources`.
3. `close()` ném `IllegalStateException`.
4. Phương thức **không hoàn tất bằng `return 123`** mà kết thúc bằng ngoại lệ từ `close()`, trừ khi ngoại lệ được xử lý trong một handler bao ngoài phù hợp.

Đây không phải việc Java “quên trả kết quả”, mà là quy tắc ưu tiên **hoàn tất bất thường mới** của việc đóng so với đường trả giá trị đang chờ.

**Vì sao không có suppressed exception trong trường hợp này?** Phần thân `try` mới chuẩn bị `return`, chưa ném một ngoại lệ chính nào. Khi `close()` là nguồn ngoại lệ duy nhất, lỗi đóng trở thành ngoại lệ chính được quan sát.

So sánh:

| Trạng thái phần thân | Trạng thái `close()` | Caller thường quan sát |
| --- | --- | --- |
| `return 123` | Bình thường | Nhận `123` |
| `return 123` | Ném `Eclose` | Nhận `Eclose` thay vì `123` |
| Hoàn tất bình thường | Ném `Eclose` | Nhận `Eclose` |
| Ném `Ebody` | Bình thường | Nhận `Ebody` |
| Ném `Ebody` | Ném `Eclose` | Nhận `Ebody`; `Eclose` là suppressed |

Phần cuối bảng là điểm nổi bật của `try-with-resources` so với một `finally` thủ công có câu `throw` mới.

## 7. Khi cả phần thân và `close()` cùng ném lỗi: suppressed exceptions

Giả sử phần thân ném `IllegalArgumentException("body failed")` rồi quá trình đóng resource ném `IllegalStateException("close failed")`.

Java cần bảo toàn **lỗi chính của thao tác** và vẫn ghi nhận sự cố **dọn dẹp**. Với `try-with-resources`, trong tình huống thông thường:

- Ngoại lệ từ phần thân được giữ làm **ngoại lệ chính**.
- Ngoại lệ từ `close()` được thêm vào danh sách `getSuppressed()` của ngoại lệ chính.
- Handler bao ngoài bắt ngoại lệ chính; nếu muốn điều tra lỗi đóng, xem mảng do `getSuppressed()` trả về.

Đây là cách Java tránh việc một lỗi cleanup vô tình **ghi đè hoàn toàn** lỗi gốc.

### `getSuppressed()` khác `getCause()`

`Throwable.getCause()` biểu thị **chuỗi nguyên nhân** thường được lập trình viên truyền qua constructor `new X(message, cause)`. `Throwable.getSuppressed()` biểu thị **các ngoại lệ phụ đã được gắn vào ngoại lệ chính**, điển hình là lỗi đóng tài nguyên.

Một ngoại lệ chính có thể có cả `cause` và nhiều suppressed exceptions; đây là hai quan hệ khác nhau.

Trong trường hợp `try-with-resources` tự quản lý lỗi đóng, đừng chỉ nhìn `ex.getCause()` rồi khẳng định không có lỗi phụ. Cần xem `ex.getSuppressed()` nếu tình trạng cleanup quan trọng.

### Có bao nhiêu lỗi đóng được lưu lại?

Với nhiều resource, mỗi `close()` được gọi theo thứ tự ngược, kể cả khi một `close()` trước đó ném lỗi. Nếu phần thân đã ném ngoại lệ chính và nhiều lần đóng đều lỗi, các lỗi đóng được thêm vào danh sách suppressed.

Ví dụ khai báo A rồi B, phần thân ném E, cả B.close và A.close ném lỗi:

```text
mở A
mở B
thân try ném E
B.close() ném EB
A.close() ném EA
caller bắt E
E.getSuppressed() thường có [EB, EA]
```

Ở đây `[EB, EA]` là ký hiệu giải thích thứ tự, không phải một câu lệnh Java. Đầu ra chính xác sẽ được kiểm chứng bằng chương trình kế tiếp.

## 8. Ví dụ Java 17+ hoàn chỉnh: return bị thay và lỗi đóng bị suppressed

Lưu tệp **`CloseFailureFlowDemo.java`**:

```java
public class CloseFailureFlowDemo {

    static final class Tracked implements AutoCloseable {
        private final String name;
        private final boolean failsOnClose;

        Tracked(String name, boolean failsOnClose) {
            this.name = name;
            this.failsOnClose = failsOnClose;
            System.out.println("open " + name);
        }

        @Override
        public void close() {
            System.out.println("close " + name);
            if (failsOnClose) {
                throw new IllegalStateException(
                        "close failed: " + name);
            }
        }
    }

    static int returnThenCloseFails() {
        System.out.println("CASE return-close-error");

        try (Tracked resource = new Tracked("R", true)) {
            System.out.println("body: return 9");
            return 9;
        }
    }

    static void bodyAndCloseFail() {
        System.out.println("CASE body-and-close-errors");

        try (Tracked first = new Tracked("A", true);
             Tracked second = new Tracked("B", true)) {
            System.out.println("body: throw");
            throw new IllegalArgumentException("work failed");
        } catch (IllegalArgumentException ex) {
            System.out.println("primary: " + ex.getMessage());
            System.out.println("suppressed count: "
                    + ex.getSuppressed().length);
            for (Throwable suppressed : ex.getSuppressed()) {
                System.out.println(
                        "suppressed: " + suppressed.getMessage());
            }
        }
    }

    static void closeOnlyFailure() {
        System.out.println("CASE close-only-error");

        try (Tracked resource = new Tracked("C", true)) {
            System.out.println("body: normal");
        } catch (IllegalStateException ex) {
            System.out.println("caught: " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println(
                    "returned=" + returnThenCloseFails());
        } catch (IllegalStateException ex) {
            System.out.println("caught: " + ex.getMessage());
            System.out.println("suppressed count: "
                    + ex.getSuppressed().length);
        }

        bodyAndCloseFail();
        closeOnlyFailure();
    }
}
```

Biên dịch/chạy với JDK 17+:

```bash
javac -encoding UTF-8 CloseFailureFlowDemo.java
java CloseFailureFlowDemo
```

**Stdout chính xác:**

```text
CASE return-close-error
open R
body: return 9
close R
caught: close failed: R
suppressed count: 0
CASE body-and-close-errors
open A
open B
body: throw
close B
close A
primary: work failed
suppressed count: 2
suppressed: close failed: B
suppressed: close failed: A
CASE close-only-error
open C
body: normal
close C
caught: close failed: C
```

### Phân tích ba đường lỗi

**`returnThenCloseFails()`:** phần thân chuẩn bị trả `9` nhưng `R.close()` ném `IllegalStateException`. Lời gọi bên trong biểu thức `"returned=" + returnThenCloseFails()` không hoàn tất bình thường, nên **không in dòng `returned=9`**. Handler bên ngoài bắt lỗi đóng. Vì phần thân không ném lỗi chính, danh sách suppressed rỗng.

**`bodyAndCloseFail()`:** phần thân ném `IllegalArgumentException("work failed")`. Hai resource đã mở được đóng **B trước, A sau**. Cả hai `close()` ném `IllegalStateException`; Java gắn các lỗi này vào ngoại lệ chính. Handler bắt lỗi chính và in hai suppressed exceptions đúng thứ tự B, A.

**`closeOnlyFailure()`:** thân `try` hoàn tất bình thường nhưng `close()` ném `IllegalStateException`. Handler nhận chính ngoại lệ đóng. Điều này xác nhận `try-with-resources` không mặc nhiên bỏ qua lỗi cleanup.

Trong hệ thống thật, cần lựa chọn chính sách khi đóng thất bại: log, báo lỗi tác vụ, retry ở tầng phù hợp hoặc đánh dấu dữ liệu chưa chắc đã được ghi hoàn chỉnh. Không nên trả thành công giả chỉ vì phần thân `try` đã in “done”.

## 9. `break` và `continue` vẫn đóng tài nguyên

Hai lệnh này không rời toàn bộ phương thức, nhưng chúng **rời phạm vi `try`** khi chuyển điều khiển ra ngoài phần thân. Vì vậy `try-with-resources` vẫn thực hiện đóng.

Ví dụ ở mức cấu trúc:

```java
for (int i = 0; i < 10; i++) {
    try (Resource resource = open(i)) {
        if (shouldSkip(i)) {
            continue;
        }

        if (shouldStop(i)) {
            break;
        }

        use(resource);
    }
}
```

Đây là khung minh họa, cần định nghĩa `Resource`, `open`, `shouldSkip`, `shouldStop` và `use`. Đường đi:

- Nếu `shouldSkip(i)` là `true`, resource của lần lặp hiện tại được đóng **trước khi** vòng lặp bắt đầu lần kế.
- Nếu `shouldStop(i)` là `true`, resource được đóng **trước khi** thoát khỏi vòng lặp.
- Nếu `use(resource)` hoàn tất bình thường, resource vẫn được đóng khi hết thân `try`.
- Nếu `use(resource)` ném ngoại lệ, resource vẫn được đóng trong quá trình thoát bất thường.

**Lưu ý:** nếu `close()` phát sinh lỗi trên đường `continue` hoặc `break`, quá trình chuyển điều khiển có thể bị thay bằng ngoại lệ chưa được bắt. Vì thế vòng lặp có thể không chuyển đến nơi bạn dự đoán nếu cleanup thất bại.

### `break` có nhãn và `continue` có nhãn

Java có `break label;` và `continue label;`. Khi lệnh chuyển điều khiển ra khỏi một hoặc nhiều phạm vi `try-with-resources`, các resource của những phạm vi bị rời được đóng theo quy tắc tương ứng. Nếu một lệnh có nhãn **không rời khỏi một phạm vi `try-with-resources` cụ thể**, tài nguyên của phạm vi đó chưa bị đóng chỉ vì có lệnh chuyển điều khiển.

Do đó cần xét **những scope nào thực sự bị thoát**, không chỉ dựa vào việc có từ khóa `break`/`continue` xuất hiện trong mã.

## 10. `return` trong `catch` hoặc `finally` ngoài có ảnh hưởng gì?

Xét:

```java
static int recover() {
    try (Tracked resource = openTracked()) {
        throw new IllegalArgumentException("bad input");
    } catch (IllegalArgumentException ex) {
        return 0;
    } finally {
        System.out.println("request finished");
    }
}
```

Đây là khung minh họa với kiểu `Tracked` và `openTracked()` phù hợp. Trình tự:

1. Mở resource và chạy thân `try`.
2. Phần thân ném `IllegalArgumentException`.
3. Resource được đóng.
4. Handler `catch` quyết định `return 0`.
5. `finally` cùng cấu trúc thực thi.
6. Nếu `finally` hoàn tất bình thường, caller nhận `0`.

Nếu `finally` **cũng `return` hoặc `throw`**, nó có thể thay thế quyết định của `catch`. Đó là quy tắc hoàn tất bất thường của `finally`, **khác** cơ chế resource tự động đóng, nhưng có thể xảy ra trong cùng một cấu trúc.

Không nên dùng `finally` để trả fallback hoặc nuốt lỗi. Đặt chính sách phục hồi ở `catch` thích hợp; để `try-with-resources` giải quyết vòng đời resource.

## 11. Ngoại lệ trong quá trình khởi tạo resource cũng dẫn tới đóng những resource đã mở

Không phải mọi trường hợp thoát bất thường đều bắt đầu từ phần thân `try`. Ngay **initializer** trong danh sách resource cũng có thể thất bại:

```java
try (Resource first = openFirst();
     Resource second = openSecond()) {
    doWork(first, second);
}
```

Đây là đoạn minh họa. Nếu `openFirst()` thành công, nhưng `openSecond()` ném ngoại lệ:

- `first` đã được khởi tạo thành công, nên Java gọi `first.close()`.
- `second` chưa được khởi tạo thành công, nên không có resource `second` hợp lệ để gọi `close()`.
- Phần thân `doWork(first, second)` **không chạy**.
- Ngoại lệ từ `openSecond()` là lỗi chính.
- Nếu `first.close()` cũng ném lỗi, lỗi đóng có thể được gắn **suppressed** dưới lỗi khởi tạo.

Với tài nguyên có initializer trả `null`, Java **không gọi `close()` trên `null`** theo cơ chế đóng tài nguyên. Tuy nhiên, việc sử dụng biến resource `null` trong thân `try` có thể phát sinh `NullPointerException` như các lời gọi Java khác.

### Ví dụ kiểm chứng bằng lập luận

```text
open A thành công
open B ném "init B failed"
close A
catch thấy "init B failed"
```

Nếu `close A` cũng ném, ngoại lệ đóng được ghi nhận như lỗi phụ theo quy tắc suppression; không làm cho thân `try` đã bị bỏ qua bắt đầu chạy.

Vì vậy, `try-with-resources` còn xử lý cả **rò rỉ tài nguyên khi một phần công đoạn khởi tạo đã thành công**.

## 12. `try-with-resources` và `try-finally` tự viết khác nhau khi cả hai lỗi

Xét mã thủ công:

```java
Resource resource = openResource();
try {
    doWork(resource);
} finally {
    resource.close();
}
```

Đây là đoạn minh họa, yêu cầu các khai báo/phương thức thích hợp. Về đường đóng bình thường, nó có vẻ tương tự `try-with-resources`. Nhưng khi:

- `doWork(resource)` ném ngoại lệ `Ebody`;
- `resource.close()` trong `finally` ném ngoại lệ `Eclose`;

thì một `throw` mới từ `finally` có thể **che mất `Ebody`**. Không có cơ chế tự động thêm `Eclose` vào `Ebody.getSuppressed()` chỉ vì `close()` được viết trong `finally` thông thường.

Với `try-with-resources`:

```java
try (Resource resource = openResource()) {
    doWork(resource);
}
```

Java biên dịch cấu trúc theo cơ chế quản lý lỗi chính và lỗi đóng, giữ `Ebody` làm ngoại lệ chính và có thể gắn `Eclose` vào danh sách suppressed. Đây là lý do **nên ưu tiên `try-with-resources` cho đối tượng `AutoCloseable`** thay vì tự viết đóng trong `finally`.

### Có nên tự gọi `close()` trong thân `try` không?

Nếu tự gọi `resource.close()` trong thân và sau đó để `try-with-resources` tự đóng lần nữa, bạn có nguy cơ **đóng hai lần**. Không phải mọi `AutoCloseable` đều cam kết hành vi đóng lặp vô hại. Hãy thiết kế ownership rõ ràng; đa số trường hợp chỉ để Java gọi `close()` khi rời phạm vi.

### `close()` ném checked exception

`AutoCloseable.close()` có thể khai báo `throws Exception`. Loại checked exception cần xử lý được kiểm tra theo **kiểu tĩnh của resource**. Nếu gọi qua một biến kiểu `AutoCloseable`, một phương thức có thể cần `throws Exception`; nếu dùng `BufferedReader`, các checked exception liên quan thường ở nhánh `IOException`.

Vì vậy đoạn:

```java
static void use(AutoCloseable resource) throws Exception {
    try (resource) {
        System.out.println("using resource");
    }
}
```

là **phương thức minh họa theo Java 9+**, sử dụng tham số `resource` không bị gán lại và có chữ ký `throws Exception` để đáp ứng checked exception có thể xảy ra khi đóng. Nó cũng minh họa rằng sử dụng biến khai báo bên ngoài trong resource specification là hợp lệ khi tham số *effectively final*.

## 13. Thứ tự đóng khi có nhiều resource và thoát bất thường

Với:

```java
try (R a = openA();
     R b = openB();
     R c = openC()) {
    return calculate(a, b, c);
}
```

đây là khung minh họa với các lớp/hàm giả định. Java mở các resource theo **A → B → C** và đóng theo **C → B → A** khi rời phạm vi.

Nếu `calculate` trả bình thường, Java chuẩn bị trả giá trị nhưng vẫn đóng cả ba resource trước khi caller nhận.

Nếu `calculate` ném ngoại lệ `E`, Java vẫn gọi ba `close()`; các lỗi đóng có thể được gắn suppressed trên `E`.

Nếu `c.close()` ném khi phần thân đang định `return`, ngoại lệ đóng này có thể trở thành lỗi chính. Các lần đóng `b` và `a` vẫn được thử; nếu chúng cũng ném lỗi, các lỗi phụ có thể được gắn suppressed vào lỗi chính từ `c.close()`.

**Đừng kết luận rằng một `close()` lỗi sẽ khiến Java bỏ qua các resource còn lại.** Cơ chế đóng được thiết kế để thử đóng những resource đã khởi tạo thành công, theo thứ tự ngược.

### Khi nhiều resource phụ thuộc nhau

Ví dụ reader bọc một input stream: `reader.close()` có thể đã đóng luôn stream bên dưới. Nếu khai báo cả hai trong danh sách resource, Java có thể gọi đóng stream thêm lần nữa. Nhiều stream chuẩn chịu được điều đó, nhưng một số `AutoCloseable` tùy biến có thể không an toàn khi đóng lặp.

Hãy đăng ký đúng **tài nguyên mà phạm vi hiện tại sở hữu**, dựa vào hợp đồng đóng của wrapper và nguồn, thay vì tự động thêm tất cả đối tượng có `close()` vào danh sách.

## 14. Ngắt thread và đóng tài nguyên

Một thread có thể bị yêu cầu dừng công việc bằng cơ chế interrupt. Nhưng `Thread.interrupt()` **không tự động ném ngoại lệ vào mọi chỗ đang chạy** và không bảo đảm mọi API sẽ ngay lập tức dừng.

Nếu một thao tác trong thân `try-with-resources` ném `InterruptedException` và ngoại lệ này làm rời phần thân, các tài nguyên đã được đăng ký vẫn được đóng theo quy tắc thoát bất thường.

Ví dụ khung:

```java
static void consume(AutoCloseable resource)
        throws Exception {
    try (resource) {
        Thread.sleep(1_000);
    }
}
```

Đây là phương thức minh họa Java 17+, trong đó tham số resource không bị gán lại. Nếu `Thread.sleep` ném `InterruptedException` vì thread đang ngủ bị ngắt, resource được đóng trước khi ngoại lệ thoát khỏi phương thức. Nếu tầng trên bắt `InterruptedException` và chọn dừng tác vụ, thông thường cần xử lý trạng thái ngắt đúng chính sách (ví dụ khôi phục interrupt flag khi không thể ném tiếp).

Cần phân biệt việc **thoát khỏi phần thân bằng ngoại lệ** (cơ chế đóng có hiệu lực) với một thread vẫn đang chạy trong lời gọi bị chặn hoặc vòng lặp vô hạn mà chưa rời `try`. Trong trường hợp thứ hai, resource chưa nhất thiết được đóng tại thời điểm bạn quan sát.

## 15. Khi nào cơ chế đóng tự động không đảm bảo xảy ra?

`try-with-resources` hoạt động theo các quy tắc thực thi của ngôn ngữ Java. Tuy vậy, không thể hứa `close()` sẽ luôn chạy trong mọi trường hợp bên ngoài mô hình này:

- `Runtime.getRuntime().halt(...)` kết thúc JVM trực tiếp.
- `System.exit(...)` bắt đầu kết thúc JVM; không đảm bảo mọi thread đang trong `try-with-resources` hoàn tất công việc và đóng resource.
- Tiến trình bị hệ điều hành kết thúc cưỡng bức, JVM gặp sự cố nghiêm trọng hoặc hệ thống mất điện.
- Thread mắc kẹt trong một thao tác không thoát, nên khối `try` chưa được rời.
- `close()` được gọi nhưng chính nó treo vô thời hạn hoặc thất bại giữa chừng; lời gọi được thực hiện không chứng minh việc giải phóng tài nguyên hoàn tất thành công.

Không dùng shutdown hook thay cho quản lý vòng đời tài nguyên ở từng phạm vi; shutdown hook cũng không phải cơ chế có thể bảo đảm trong mọi kiểu dừng process.

**Quy tắc thực hành:** `try-with-resources` đáng tin cho các đường hoàn tất Java thông thường, gồm `return`, `throw`, `break` và `continue`; nhưng nó không thay thế quy trình phục hồi sau crash, cơ chế giao dịch hoặc chính sách shutdown.

## 16. Quan hệ giữa scope, ownership và việc đóng khi thoát sớm

Ba khái niệm cần tách rời:

| Khái niệm | Câu hỏi cần trả lời | Ví dụ |
| --- | --- | --- |
| Scope | Tên biến còn có thể truy cập từ mã hiện tại không? | Biến khai báo trong `try (...)` không dùng được sau cấu trúc |
| Lifetime | Resource đã đóng hoặc còn sẵn sàng dùng chưa? | `try (external)` đóng external khi rời khối |
| Ownership | Thành phần nào chịu trách nhiệm đóng? | Hàm tự mở file thì thường tự đóng file |

Từ Java 9, có thể khai báo biến trước rồi đưa vào `try`:

```java
var reader = java.nio.file.Files.newBufferedReader(path);
try (reader) {
    return reader.readLine();
}
```

Đây là **thân phương thức minh họa** với `path` hợp lệ và chữ ký checked exception thích hợp. Biến `reader` vốn được khai báo ngoài `try`, nhưng khi phương thức chuẩn bị `return`, resource vẫn được đóng. Điều kiện sử dụng biến ngoài trong resource specification là `final` hoặc *effectively final*.

Nếu một biến còn nằm trong scope sau khi rời `try (reader)`, điều đó **không cho phép tiếp tục đọc qua reader đã đóng**. Phạm vi biến không kéo dài vòng đời resource.

### Không đóng resource do người khác còn sở hữu

Nếu caller cấp một stream còn cần dùng, một phương thức con tùy tiện viết `try (callerStream)` rồi `return` sớm sẽ khiến stream **bị đóng trước khi caller sử dụng tiếp**. Cú pháp chạy đúng nhưng thiết kế ownership sai.

Tốt hơn là thống nhất hợp đồng: hàm nào **tạo và sở hữu** resource thường chịu trách nhiệm đóng; hàm chỉ **mượn** resource thường không đóng nếu caller còn sử dụng sau đó. Có những API chuyển quyền sở hữu cho callee, nhưng việc đó phải được công bố rõ.

## 17. Những nhầm lẫn thường gặp

| Nhầm lẫn | Sự thật | Cách thực hành |
| --- | --- | --- |
| “`return` chạy ngay nên bỏ qua `close()`” | Java đóng trước khi hoàn tất `return` | Dùng `try-with-resources` quanh tài nguyên cần đóng |
| “Có `break`/`continue` thì resource không đóng” | Các đường thoát khỏi phạm vi vẫn kích hoạt đóng | Đảm bảo phạm vi `try` tương ứng thực sự bị rời |
| “`close()` luôn chạy sau `catch`” | `close()` chạy trước các handler gắn cùng cấu trúc | Kiểm tra suppressed exception từ handler |
| “`close()` lỗi nhưng `return` chắc chắn trả thành công” | Lỗi đóng có thể thay đường `return` | Không bỏ qua ngoại lệ đóng |
| “Nếu body và close cùng lỗi thì chỉ thấy lỗi close” | Với `try-with-resources`, lỗi body là chính, close bị suppressed | Kiểm tra `getSuppressed()` |
| “Suppressed exception là `cause`” | `getCause()` và `getSuppressed()` khác nhau | Dùng đúng API chẩn đoán |
| “`close()` của B lỗi thì A không được đóng” | Java vẫn thử đóng các resource đã mở khác | Thiết kế `close()` độc lập và an toàn |
| “`openB()` lỗi thì `openA()` để rò rỉ” | A đã mở được sẽ được đóng khi init B lỗi | Đặt resource vào cùng resource specification khi phù hợp |
| “Biến còn scope nghĩa là resource còn mở” | Scope không đồng nghĩa lifetime | Không dùng resource đã đóng |
| “Một đối tượng có `close()` là dùng được trong `try`” | Kiểu phải triển khai `AutoCloseable` | Kiểm tra kiểu và hợp đồng đóng |
| “`try-with-resources` đảm bảo trên cả mất điện/halt” | Không có bảo đảm tuyệt đối ngoài thực thi bình thường | Thiết kế shutdown/crash recovery riêng |
| “Tài nguyên đóng thì tác động trước đó tự rollback” | Đóng không đồng nghĩa rollback | Dùng hợp đồng transaction/bù trừ khi cần |
| “Có thể tự gọi `close()` rồi để tự động đóng lại” | Có thể double-close | Làm rõ ownership và tránh đóng trùng |

## 18. Checklist trước khi sử dụng `try-with-resources`

1. **Kiểm tra kiểu:** resource có thực sự triển khai `AutoCloseable` không? `close()` có thể ném checked exception nào?
2. **Xác định ownership:** đây có phải resource mà phạm vi hiện tại chịu trách nhiệm đóng không?
3. **Giới hạn scope:** các thao tác cần resource đã nằm trọn trong phần thân `try` chưa?
4. **Xét `return`:** giá trị được đánh giá trước khi đóng; nếu close lỗi, caller có thể không nhận giá trị đó.
5. **Xét `throw`:** nếu thân ném ngoại lệ, Java sẽ thử đóng và lỗi đóng có thể xuất hiện trong `getSuppressed()`.
6. **Xét `break` và `continue`:** lệnh có thật sự rời vùng quản lý resource không? Có phụ thuộc vào lần lặp kế tiếp không?
7. **Xét nhiều resource:** mở trái → phải, đóng phải → trái; kiểm tra wrapper có thể đã đóng nguồn bên dưới chưa.
8. **Xét initializer thất bại:** các resource đã khởi tạo thành công trước điểm lỗi vẫn được đóng.
9. **Xét handler:** `catch` gắn với `try-with-resources` chạy sau bước đóng; phải đủ rộng để bắt lỗi mong muốn, nhưng không nên nuốt lỗi không có phương án xử lý.
10. **Xét tính đầy đủ của kết quả:** phần thân thành công không đảm bảo toàn bộ lời gọi thành công nếu `close()`/flush thất bại.
11. **Xét suppressed:** nếu hệ thống log lỗi, có cần lưu cả danh sách lỗi đóng phụ không?
12. **Xét shutdown/crash:** có cơ chế xử lý tình huống tiến trình không đi qua đường thoát Java thông thường không?
13. **Kiểm thử:** bao phủ đường thành công, `return` sớm, ngoại lệ thân `try`, lỗi `close()`, lỗi initializer và nhiều resource cùng thất bại.

## 19. Bài tập tự kiểm tra

**Bài A.** Trong `try (R r = new R()) { return 5; }`, `r.close()` có được gọi không?

**Có**, khi phương thức chuẩn bị rời phần thân bằng `return`, với điều kiện resource đã được khởi tạo và JVM tiếp tục thực thi bình thường. `close()` chạy trước khi caller nhận `5`.

**Bài B.** Nếu `close()` ném `IllegalStateException` trong khi `try` chuẩn bị trả `5`, caller luôn nhận `5` chứ?

**Không.** Ngoại lệ từ `close()` có thể thay đường `return`, nên caller nhận lỗi thay vì số `5` nếu không có handler phù hợp.

**Bài C.** `try` ném `IOException("read failed")`, còn `close()` ném `IOException("close failed")`. Với `try-with-resources`, lỗi nào là chính?

**Thông thường là `"read failed"`**, còn lỗi `"close failed"` được lưu trong `getSuppressed()` của ngoại lệ chính.

**Bài D.** Resource A mở trước B. Khi `return` trong thân, A hay B được đóng trước?

**B trước A.** Java đóng ngược thứ tự khai báo, không phụ thuộc việc thoát bằng `return` hay kết thúc bình thường.

**Bài E.** Vòng lặp mở một resource mỗi lần. Trong thân `try` có `continue;`. Resource của lần hiện tại có đóng trước lần sau không?

**Có**, nếu `continue` rời phạm vi `try-with-resources` đó. Cơ chế đóng chạy trước khi điều khiển tiếp tục vòng lặp.

**Bài F.** `openA()` thành công, `openB()` lỗi, thân `try` chưa chạy. A có được đóng không?

**Có.** Những resource đã được khởi tạo thành công được đóng khi khởi tạo resource sau thất bại.

**Bài G.** Cả thân và hai lệnh đóng đều lỗi. Caller có thể xem những lỗi đóng ở đâu?

Qua **`getSuppressed()`** trên ngoại lệ chính, theo thứ tự các lần đóng tạo ra chúng.

**Bài H.** Một resource có `close()` đã được gọi nhưng bản thân close gặp lỗi. Có thể kết luận tài nguyên chắc chắn được giải phóng thành công không?

**Không.** “Đã gọi `close()`” không bảo đảm “đóng thành công”; trạng thái thực tế phụ thuộc hợp đồng và implementation của resource.

**Bài I.** Gọi `System.exit(...)` bên trong thân `try-with-resources` có giống một `return` thông thường không?

**Không.** Dừng tiến trình/JVM không bảo đảm việc đóng tài nguyên đang mở qua đường thực thi `finally`/`try-with-resources`. Không dùng `System.exit` để thay cho quy trình đóng thông thường.

**Bài J.** `try (external)` tự động đóng tài nguyên, nhưng `external` được khai báo bên ngoài và sau `try` vẫn nhìn thấy. Có thể dùng tiếp không?

**Không nên**, trừ khi hợp đồng resource cụ thể quy định một thao tác sau đóng là hợp lệ. Scope của biến và trạng thái đóng là hai chuyện khác nhau.

## Kết luận

**`try-with-resources` đóng tài nguyên đã được khởi tạo thành công trước khi hoàn tất đường rời phạm vi**, kể cả khi có `return`, `throw`, `break` hoặc `continue`. Với nhiều resource, thứ tự đóng **ngược với thứ tự khởi tạo**. Khi phần thân ném lỗi và `close()` cũng ném, Java bảo toàn ngoại lệ chính và ghi lỗi đóng dưới dạng **suppressed exception**. Nhưng nếu phần thân chỉ chuẩn bị `return` và `close()` ném lỗi, kết quả trả về có thể **bị thay bằng ngoại lệ đóng**.

Thiết kế đúng cần kết hợp ba điều: **phạm vi sử dụng đủ hẹp**, **ownership tài nguyên rõ ràng**, và **xử lý trung thực các lỗi cả trong thân lẫn lúc đóng**. Cơ chế này giải quyết nhiều đường thoát trong Java, nhưng không thay thế quản lý giao dịch hoặc các bảo đảm phục hồi khi process bị kết thúc bất thường.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="try-with-resources-012">Quyền sở hữu tài nguyên và trường hợp không nên tự động đóng</a>

<details>
<summary>Xem chi tiết</summary>

# Quyền sở hữu tài nguyên và trường hợp không nên tự động đóng trong Java

## 1. Mục tiêu bài học

`try-with-resources` giúp Java tự gọi `close()` khi rời một vùng mã. Nhưng **có thể đóng** và **có quyền đóng** là hai chuyện khác nhau: đối tượng triển khai `AutoCloseable` không có nghĩa bất kỳ phương thức nào được nhận tham chiếu đến nó cũng nên tự động kết thúc vòng đời đối tượng đó.

Một phương thức đọc dữ liệu được truyền `InputStream` từ bên ngoài có thể chỉ **mượn** tài nguyên. Trái lại, một phương thức tự mở tệp để hoàn thành công việc thường phải **sở hữu trách nhiệm đóng** tệp ấy. Trong ứng dụng có framework, connection pool, giao dịch hoặc vòng đời request, việc đóng tài nguyên tại một scope quá nhỏ có thể làm thành phần khác mất khả năng sử dụng tài nguyên, dù mã có vẻ “đúng mẫu” `try-with-resources`.

Sau bài này, bạn có thể:

- Phân biệt tài nguyên **owned (sở hữu)**, **borrowed (mượn)**, **transferred (chuyển giao quyền đóng)** và **framework-managed (do tầng khác quản lý vòng đời)**.
- Chọn có dùng `try-with-resources` hay không dựa trên **hợp đồng quyền sở hữu** và vòng đời thực tế, không chỉ dựa trên kiểu `AutoCloseable`.
- Nhận diện trường hợp đóng một wrapper kéo theo đóng stream/reader mà nó bao bọc.
- Hiểu vì sao `try (resource)` của Java 9+ vẫn tự đóng đối tượng dù biến được khai báo bên ngoài.
- Thiết kế các hàm nhận `InputStream`, `Reader`, `Connection` và các factory trả resource sao cho người gọi biết ai phải đóng.
- Tránh leak, double-close, trả một tài nguyên đã đóng, đóng nhầm `System.in`/`System.out` hoặc tài nguyên do framework sở hữu.
- Xử lý cleanup khi khởi tạo nhiều resource hoặc xảy ra ngoại lệ mà không phá vỡ hợp đồng của bên gọi.

Các ví dụ Java 17+ độc lập có lệnh biên dịch/chạy và kết quả `stdout` xác định. Những đoạn chỉ minh họa chữ ký hoặc phụ thuộc API bên ngoài được ghi rõ.

## 2. `try-with-resources` bảo đảm điều gì — và không bảo đảm điều gì?

Cấu trúc Java quen thuộc:

```java
try (java.io.BufferedReader reader =
        java.nio.file.Files.newBufferedReader(path)) {
    System.out.println(reader.readLine());
}
```

Đây là **mảnh mã** trong một phương thức có biến `path` kiểu `Path` và xử lý/khai báo `IOException`. `reader` là resource được khai báo trong header. Khi phạm vi kết thúc, Java gọi `reader.close()`, ngay cả khi phần thân kết thúc đột ngột do ngoại lệ hoặc `return`.

Mệnh đề `try-with-resources` quản lý việc gọi `close()` theo cú pháp, nhưng **không tự biết**:

1. Resource có phải do phương thức này sở hữu hay chỉ được mượn.
2. Resource còn phải được nơi khác dùng sau đó hay không.
3. `close()` có kết thúc socket/file vật lý, trả connection về pool, flush dữ liệu, hay thực hiện hành vi khác.
4. Wrapper đóng có kéo theo đóng tài nguyên nền hay không.
5. Một resource được tham chiếu ở hai vị trí của header có thực chất là cùng một đối tượng hay không.
6. Liệu đóng ở thời điểm này có đúng với transaction/request/session lifecycle hay không.

Điểm mấu chốt: **`try-with-resources` là cơ chế đóng tự động; nó không phải hệ thống kiểm tra quyền sở hữu.** Java không có trình biên dịch theo kiểu “ownership type system” bắt buộc bạn chứng minh đã sở hữu resource trước khi đưa vào header.

### `AutoCloseable` không đồng nghĩa “luôn đóng ngay khi dùng xong một phương thức”

`java.lang.AutoCloseable` công bố phương thức `close()`. Một số implementation của `close()` có thể có side effect và ném ngoại lệ. `Closeable` dành cho nhiều tài nguyên I/O và có hợp đồng riêng. Tuy nhiên, **interface không cho biết ai là owner**; điều đó đến từ hợp đồng API, kiến trúc và đối tượng tạo/quản lý tài nguyên.

Do vậy, cách sử dụng tốt không phải “không bao giờ dùng `try-with-resources` với tham số”, mà là “dùng khi **scope đó đã nhận quyền sở hữu và trách nhiệm đóng** theo hợp đồng”.

## 3. Bốn mô hình quyền sở hữu thường gặp

| Mô hình | Ví dụ | Ai quyết định đóng? | Khuyến nghị |
| --- | --- | --- | --- |
| **Owned** | Phương thức tự mở tệp, tự tạo reader/stream để dùng nội bộ | Chính phương thức hoặc component nhận quyền sở hữu rõ ràng | Đóng trong đúng scope, thường bằng `try-with-resources` |
| **Borrowed** | Hàm parse nhận `Reader` từ người gọi chỉ để đọc | Người gọi vẫn sở hữu | Hàm parse **không** tự đóng tham số, trừ khi hợp đồng nói khác |
| **Ownership transfer** | Factory trả về `InputStream` mới mở và chuyển trách nhiệm cho caller | Caller sau khi nhận thành công | API phải ghi rõ caller phải đóng; caller dùng `try-with-resources` |
| **Framework-managed / pooled** | Connection/session/stream do container, transaction manager hoặc pool cấp | Theo hợp đồng framework/pool hoặc đơn vị giữ lifecycle | Không tự đóng chỉ vì có thể gọi `close()`; tuân thủ phạm vi và quy tắc trả về do API quy định |

**Lưu ý quan trọng về pool:** Nhiều `Connection` được cấp từ pool có `close()` mang nghĩa **trả logical connection về pool**, không nhất thiết đóng kết nối vật lý. Nếu bạn là người được pool giao quyền sử dụng một connection với trách nhiệm trả về, `try-with-resources` có thể hoàn toàn đúng. Nếu một connection được *mượn* từ caller/transaction manager đang quản lý chung, đóng trong hàm phụ trợ có thể quá sớm.

### Quyền sở hữu nên được xác định bằng hợp đồng, không bằng phỏng đoán

Hãy trả lời trước khi thêm `try (resource)`:

- **Ai tạo hoặc nhận resource từ factory?** Người tạo thường chịu trách nhiệm ban đầu, nhưng có thể chuyển quyền sở hữu.
- **Ai được phép kết thúc vòng đời?** Có thể là caller, callee, transaction manager, container hoặc application shutdown.
- **Sau lời gọi này, có nơi nào còn cần resource không?**
- **`close()` thực hiện chính xác điều gì?** Chỉ đóng wrapper, đóng cả delegate, flush output, trả connection về pool hay rollback transaction?
- **Có xảy ra đóng trùng hoặc leak trên đường lỗi không?**
- **Hợp đồng API đã mô tả trách nhiệm đóng và tình huống lỗi chưa?**

Đừng căn cứ duy nhất vào việc “đối tượng không còn được dùng trong phương thức hiện tại”. Nó có thể vẫn còn được dùng bởi caller hoặc các phần khác của ứng dụng.

## 4. Khi nào nên tự động đóng?

### 4.1. Resource do phương thức tự tạo và không thoát ra ngoài

Ví dụ hợp lệ:

```java
static String readFirstLine(java.nio.file.Path path)
        throws java.io.IOException {
    try (java.io.BufferedReader reader =
            java.nio.file.Files.newBufferedReader(path)) {
        return reader.readLine();
    }
}
```

Đây là **mảnh phương thức**. Reader được tạo trong phương thức, dữ liệu trả về là một `String` chứ không phải chính reader. Scope của công việc và vòng đời resource trùng nhau: mở, đọc, đóng, trả giá trị.

`try-with-resources` còn gọi `close()` khi `readLine()` ném ngoại lệ; thao tác close vẫn có thể ném ngoại lệ riêng, được xử lý theo quy tắc suppressed exception.

### 4.2. Caller nhận quyền sở hữu rõ ràng

Khi API của một factory trả một tài nguyên **đang mở** và tuyên bố người gọi có trách nhiệm đóng, caller thường nên dùng:

```java
try (java.io.InputStream input = openOwnedStream()) {
    useInput(input);
}
```

Đây là **mảnh minh họa**, cần định nghĩa `openOwnedStream()` và `useInput(...)`, đồng thời xử lý checked exception phù hợp. Quyền sở hữu **được chuyển** từ factory sang caller ở kết quả thành công; caller chịu trách nhiệm đóng.

Nếu factory trả resource nhưng nó thực ra thuộc vòng đời của đối tượng khác, API đó phải mô tả rõ; đừng suy luận chỉ từ tên `open...`.

### 4.3. Scope quản lý trùng scope sử dụng

Trong một CLI đọc tệp một lần, scope có thể nằm gọn trong một phương thức. Trong service kết nối cơ sở dữ liệu nhiều bước, scope có thể là cả transaction. Trong server, scope của một request hoặc của ứng dụng là hai thứ khác nhau. Chọn scope theo **đơn vị công việc cần tài nguyên tồn tại**, không theo số dòng ngắn nhất có thể đưa vào `try`.

## 5. Khi nào **không nên tự động đóng**?

### 5.1. Hàm chỉ mượn `InputStream` hoặc `Reader`

Ví dụ một parser nhận `Reader` do caller truyền và chỉ cần đọc dữ liệu. Nếu parser tự viết:

```java
static String parse(java.io.Reader source)
        throws java.io.IOException {
    try (source) {
        char[] buffer = new char[16];
        int count = source.read(buffer);
        return count < 0 ? "" : new String(buffer, 0, count);
    }
}
```

Đây là **mảnh phương thức hợp lệ từ Java 9+** nếu tham số `source` effectively final, nhưng có thể **sai về hợp đồng**: nó tự đóng reader mà caller đưa vào.

Nếu hợp đồng nói “caller retains ownership”, nên giữ nguyên reader mở:

```java
static String parseBorrowed(java.io.Reader source)
        throws java.io.IOException {
    char[] buffer = new char[16];
    int count = source.read(buffer);
    return count < 0 ? "" : new String(buffer, 0, count);
}
```

Đây là mảnh phương thức dùng reader mượn; caller vẫn chịu trách nhiệm đóng. Cả hai phương thức đều có thể đọc và trả `String`; khác biệt nằm ở **quyền kết thúc vòng đời**.

**Không nên đóng tài nguyên mượn kể cả khi xử lý thất bại** nếu hợp đồng yêu cầu caller giữ quyền sở hữu. Hãy truyền ngoại lệ ra, để caller quyết định retry, reset, tiếp tục hoặc đóng.

### 5.2. Stream chuẩn do tiến trình quản lý

Các stream như `System.in`, `System.out` và `System.err` thường được dùng bởi nhiều phần của ứng dụng. Một helper không nên tùy ý:

```java
try (java.io.InputStream input = System.in) {
    // ...
}
```

Đây là mảnh mã hợp lệ nhưng **rủi ro**: nó sẽ đóng `System.in` khi rời khối. Các bước nhập liệu sau đó có thể không còn dùng được stream này.

Tương tự, đóng một `Scanner` được tạo trên `System.in` thường cũng đóng stream nền, vì `Scanner.close()` đóng nguồn nếu nguồn triển khai `Closeable`. Nếu một ứng dụng xác định riêng rằng đã kết thúc hoàn toàn việc đọc stdin, chủ sở hữu cấp ứng dụng có thể có chính sách đóng phù hợp. Nhưng **helper ngắn hạn** không nên áp chính sách đó cho toàn tiến trình.

`System.out` và `System.err` cũng không nên bị helper đóng sớm; việc đóng wrapper/output có thể khiến các bước log/in tiếp theo gặp lỗi hoặc không còn hoạt động như mong đợi.

### 5.3. Tài nguyên thuộc framework, container hoặc transaction

Những trường hợp cần xem hợp đồng framework:

- Request/response body stream do web server/container quản lý.
- Một `Connection` được caller truyền để tham gia transaction đang mở.
- Session hoặc entity manager được transaction manager quản lý.
- Stream nằm trong một pipeline do tầng điều phối cần tiếp tục xử lý.
- Resource dùng chung nhiều lời gọi hoặc sống đến khi component/service shutdown.

Một phương thức phục vụ nghiệp vụ **không tự động trở thành owner** của resource chỉ vì nó nhận được tham chiếu. Nếu framework giao quyền đóng cho ứng dụng trong phạm vi cụ thể (ví dụ connection checkout độc lập), hãy đóng đúng hạn theo tài liệu của framework.

### 5.4. Resource còn được trả về hoặc lưu lại để dùng sau

Sai lầm điển hình:

```java
static java.io.BufferedReader openBroken(java.nio.file.Path path)
        throws java.io.IOException {
    try (java.io.BufferedReader reader =
            java.nio.file.Files.newBufferedReader(path)) {
        return reader; // Trả tham chiếu đến reader đang chuẩn bị bị đóng.
    }
}
```

Đây là mảnh phương thức **biên dịch được nhưng sai về vòng đời**. Trong quá trình `return`, `try-with-resources` đóng `reader` trước khi caller nhận giá trị. Caller nhận đối tượng đã đóng. Gọi `readLine()` sau đó có thể phát sinh `IOException` vì stream không còn mở.

Cách đúng nếu muốn chuyển quyền sở hữu:

```java
static java.io.BufferedReader openOwned(java.nio.file.Path path)
        throws java.io.IOException {
    return java.nio.file.Files.newBufferedReader(path);
}
```

Caller sau đó chịu trách nhiệm:

```java
try (java.io.BufferedReader reader = openOwned(path)) {
    System.out.println(reader.readLine());
}
```

Các đoạn trên là **mảnh mã minh họa**; `path` phải là biến hợp lệ và caller xử lý/khai báo `IOException`. Không đóng resource ở factory trên đường thành công nếu hợp đồng đã **chuyển quyền sở hữu** cho caller.

## 6. Chương trình chạy được: resource mượn và resource được sở hữu

Tạo file **`BorrowedOwnedDemo.java`**:

```java
import java.io.IOException;
import java.io.StringReader;

public class BorrowedOwnedDemo {
    static class TrackedReader extends StringReader {
        private boolean closeCalled;

        TrackedReader(String text) {
            super(text);
        }

        @Override
        public void close() {
            closeCalled = true;
            super.close();
        }

        boolean closeCalled() {
            return closeCalled;
        }
    }

    static String readBorrowed(TrackedReader source)
            throws IOException {
        char[] chars = new char[16];
        int count = source.read(chars);
        return count < 0 ? "" : new String(chars, 0, count);
    }

    public static void main(String[] args) throws IOException {
        TrackedReader external = new TrackedReader("alpha");

        System.out.println("borrowed=" + readBorrowed(external));
        System.out.println("borrowed closed=" + external.closeCalled());

        TrackedReader owned = new TrackedReader("beta");
        try (owned) {
            System.out.println("owned=" + readBorrowed(owned));
        }
        System.out.println("owned closed=" + owned.closeCalled());

        external.close();
        System.out.println("external closed by owner="
                + external.closeCalled());
    }
}
```

Chạy bằng JDK 17+:

```bash
javac BorrowedOwnedDemo.java
java BorrowedOwnedDemo
```

`stdout`:

```text
borrowed=alpha
borrowed closed=false
owned=beta
owned closed=true
external closed by owner=true
```

Giải thích:

1. `external` được tạo ở `main` và truyền cho `readBorrowed`, nhưng hàm đó **không** đặt resource vào header `try` và không gọi `close()`.
2. Sau khi đọc, `external.closeCalled()` vẫn là `false`; `main` còn quyền quyết định vòng đời.
3. `owned` là resource mà scope `main` chủ động quản lý bằng `try (owned)`. Java 9+ cho phép cú pháp này vì biến effectively final.
4. Khi rời khối, Java gọi `owned.close()` nên cờ theo dõi thành `true`.
5. Cuối cùng, `main` tự đóng `external` tại nơi có quyền sở hữu.

Cờ `closeCalled` là cơ chế theo dõi **được thiết kế trong ví dụ**, không phải API chung có sẵn của mọi `Reader`. Ví dụ không cần tệp hoặc network, nên đầu ra ổn định.

## 7. Đóng wrapper có thể đóng cả tài nguyên nền

Một nguồn lỗi khác là **wrapper cascading close**. Các lớp bọc tài nguyên thường chuyển tiếp thao tác `close()` xuống delegate.

Ví dụ:

```java
java.io.InputStream borrowedInput = getBorrowedInput();

try (java.io.BufferedReader reader = new java.io.BufferedReader(
        new java.io.InputStreamReader(borrowedInput))) {
    System.out.println(reader.readLine());
}
```

Đây là **mảnh minh họa**, cần định nghĩa `getBorrowedInput()` và xử lý checked exception. Đóng `BufferedReader` sẽ đóng `InputStreamReader`, từ đó đóng `borrowedInput`. **Chỉ vì tên `borrowedInput` không xuất hiện trong header không có nghĩa nó không bị đóng.**

Đây là một lý do quan trọng để tra hợp đồng `close()` của wrapper: `BufferedReader`, `InputStreamReader`, `BufferedInputStream` và nhiều wrapper khác thường sở hữu việc đóng delegate.

### Ví dụ chạy được: `close()` lan qua wrapper

Tạo tệp **`WrapperCascadeDemo.java`**:

```java
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class WrapperCascadeDemo {
    static class TrackedInput extends ByteArrayInputStream {
        private boolean closeCalled;

        TrackedInput(String text) {
            super(text.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() {
            closeCalled = true;
            super.close();
        }

        boolean closeCalled() {
            return closeCalled;
        }
    }

    public static void main(String[] args) throws IOException {
        TrackedInput input = new TrackedInput("hello");

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            System.out.println("read=" + reader.readLine());
        }

        System.out.println("underlying close called="
                + input.closeCalled());
    }
}
```

```bash
javac WrapperCascadeDemo.java
java WrapperCascadeDemo
```

`stdout`:

```text
read=hello
underlying close called=true
```

`ByteArrayInputStream` gốc được override `close()` chỉ để ghi nhận lời gọi; class này bình thường có `close()` không gây tác động đáng kể. Dù vậy, cờ chứng minh lời gọi **đã được chuyển tiếp** qua lớp bọc. Với file, socket hoặc tài nguyên bên ngoài, tác động đóng có thể là thực sự giải phóng tài nguyên.

### Khi nào dùng wrapper mà vẫn muốn giữ stream nền mở?

Nếu hợp đồng yêu cầu giữ delegate mở, có ba hướng thường phù hợp:

- Tránh tạo wrapper có quyền đóng delegate trong phạm vi ngắn; trực tiếp dùng API trên borrowed stream nếu đủ nhu cầu.
- Điều chỉnh API để **caller tạo, sở hữu và đóng wrapper**, trong khi helper chỉ mượn wrapper để đọc.
- Khi thật sự cần, dùng một wrapper **leave-open/non-closing** có hành vi đóng được xác định rõ, và kiểm tra tác động đến buffer, flush, trạng thái stream, lỗi và việc đóng resource. Đây là thiết kế cần chủ đích, không phải thủ thuật áp dụng mặc định.

Không nên đơn giản bỏ `close()` trong mọi trường hợp: một số wrapper có buffer đầu ra cần flush/finish để bảo đảm tính toàn vẹn dữ liệu. Việc giữ stream nền mở phải đi cùng chính sách kết thúc wrapper và flush dữ liệu rõ ràng.

## 8. Chương trình chạy được: wrapper không đóng input mượn

Đôi khi phải sử dụng API của wrapper nhưng vẫn để caller sở hữu stream nền. Một cách minh họa là tạo một lớp bọc `InputStream` mà `close()` **không đóng delegate**.

Tạo file **`LeaveOpenDemo.java`**:

```java
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class LeaveOpenDemo {
    static class TrackedInput extends ByteArrayInputStream {
        private boolean closeCalled;

        TrackedInput(String text) {
            super(text.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() {
            closeCalled = true;
            super.close();
        }

        boolean closeCalled() {
            return closeCalled;
        }
    }

    static class LeaveOpenInput extends FilterInputStream {
        LeaveOpenInput(InputStream source) {
            super(source);
        }

        @Override
        public void close() {
            // Chủ ý không đóng delegate: ownership vẫn thuộc caller.
        }
    }

    static String readBorrowed(InputStream source)
            throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new LeaveOpenInput(source),
                        StandardCharsets.UTF_8))) {
            return reader.readLine();
        }
    }

    public static void main(String[] args) throws IOException {
        TrackedInput external = new TrackedInput("line one\nline two");

        System.out.println("line=" + readBorrowed(external));
        System.out.println("closed by helper=" + external.closeCalled());
        external.close();
        System.out.println("closed by caller=" + external.closeCalled());
    }
}
```

```bash
javac LeaveOpenDemo.java
java LeaveOpenDemo
```

`stdout`:

```text
line=line one
closed by helper=false
closed by caller=true
```

Đây là một **cơ chế minh họa có chủ đích**, không phải quy tắc mọi wrapper `AutoCloseable`. `BufferedReader.close()` vẫn được gọi theo `try-with-resources`, nhưng thao tác đóng đến `LeaveOpenInput` không được chuyển tiếp xuống `external`.

**Cảnh báo với buffering:** một wrapper có thể đã đọc trước nhiều byte hơn kết quả trả về. Trong ví dụ, `BufferedReader.readLine()` có thể buffer cả `line two` trước khi trả về `line one`; đóng wrapper rồi tiếp tục đọc trực tiếp từ `external` có thể làm mất dữ liệu logic đối với caller. Vì vậy, chỉ dùng mẫu leave-open khi hợp đồng thật sự cho phép sự khác biệt giữa **trạng thái stream nền** và **trạng thái buffer của wrapper**. Với giao thức cần giữ nguyên vị trí đọc, hãy thống nhất wrapper do caller sở hữu và tái sử dụng nó, thay vì tạo wrapper tạm chỉ để đọc một phần.

Ví dụ trên chỉ kiểm chứng **không gọi `close()` trên stream nền**, không cam kết dữ liệu còn lại sẽ luôn đọc được theo cách ban đầu.

## 9. `try (resource)` Java 9+ vẫn đóng đối tượng mượn nếu dùng sai

Từ Java 9, có thể viết:

```java
static void process(java.io.Reader borrowed)
        throws java.io.IOException {
    try (borrowed) {
        // Đọc dữ liệu.
    }
}
```

Đây là mảnh phương thức cú pháp **hợp lệ** vì tham số `borrowed` không bị gán lại (effectively final) và `Reader` thực thi `Closeable`/`AutoCloseable`. Tuy nhiên, khi rời `try`, `close()` sẽ được gọi trên **đúng đối tượng mà caller truyền vào**.

Điều kiện final/effectively final chỉ quyết định **tính hợp lệ của biến trong header**, không quyết định **quyền sở hữu**.

So sánh:

| Mã | Hành vi | Đúng khi nào? |
| --- | --- | --- |
| `try (Reader r = openMyFile())` | Scope mở và đóng reader | Khi scope này sở hữu resource |
| `try (borrowedReader)` | Đóng reader đã được caller cấp | Chỉ khi caller giao quyền đóng |
| `try (Reader alias = borrowedReader)` | Đóng cùng đối tượng qua alias | Không bảo vệ reader mượn khỏi bị đóng |
| `try (BufferedReader b = new BufferedReader(borrowedReader))` | Thường đóng cả borrowed reader qua wrapper | Chỉ nếu có quyền đóng cả delegate |
| Không dùng TWR trên tham số mượn | Không tự động đóng tham số | Khi caller vẫn sở hữu và tự đóng |
| `try (Reader r = factoryTransfersOwnership())` | Caller nhận và đóng resource | Khi factory đã chuyển giao quyền đóng |

**Alias không tạo bản sao resource.** Việc khai báo `Reader managed = borrowedReader` chỉ tạo một tham chiếu khác; đóng một trong hai alias vẫn đóng cùng đối tượng, trừ khi một lớp bọc đã định nghĩa hành vi khác rõ ràng.

## 10. Quyền sở hữu với JDBC và connection pool

JDBC là ví dụ mà việc hiểu sai quyền sở hữu gây hậu quả lớn.

### 10.1. Hàm nhận `Connection` từ caller: thường không đóng `Connection`

Ví dụ đoạn mã **minh họa, không có `main` hoặc database thật**:

```java
static int countRows(java.sql.Connection connection)
        throws java.sql.SQLException {
    try (java.sql.PreparedStatement statement =
            connection.prepareStatement(
                    "SELECT COUNT(*) FROM inventory");
         java.sql.ResultSet rows = statement.executeQuery()) {
        rows.next();
        return rows.getInt(1);
    }
}
```

Trong ví dụ, `connection` là **borrowed resource** được caller truyền vào. Hàm này chỉ quản lý statement và result set mà nó tạo cho truy vấn; nó **không tự đóng connection**. Caller/transaction manager có thể tiếp tục sử dụng connection cho nhiều câu lệnh hoặc quyết định commit/rollback ở phạm vi lớn hơn.

Đóng `ResultSet` trước `PreparedStatement` phù hợp thứ tự đóng ngược trong header. Thực tế JDBC cũng có quy tắc statement đóng các result set phụ thuộc, nên tránh dựa vào một số lần gọi close cố định; hãy theo hợp đồng driver/framework và không giả định mọi đối tượng đều đóng lặp an toàn theo mọi cách.

**Điểm quan trọng:** câu “không đóng connection” ở đây bắt nguồn từ giả định **callee chỉ mượn connection**. Nếu chính phương thức lấy connection từ `DataSource`, chịu trách nhiệm cho một unit of work và không chuyển quyền đóng đi, thì `try (Connection connection = dataSource.getConnection())` có thể là lựa chọn đúng để trả connection về pool đúng hạn.

### 10.2. Đừng nhầm đóng connection với kết thúc transaction

`Connection.close()` và `commit()`/`rollback()` là những hành động khác nhau. Không được coi việc kết thúc một scope TWR là cách duy nhất để quyết định transaction thành công hay thất bại. Nếu auto-commit tắt, cần có chính sách commit/rollback do tầng sở hữu transaction quyết định, với các quy tắc của JDBC, driver và pool đang dùng.

Nếu một hàm phụ trợ tự đóng connection đang thuộc transaction của caller, nó có thể làm gián đoạn transaction, trả connection về pool quá sớm hoặc khiến những thao tác kế tiếp thất bại.

### 10.3. Pool có thể thay đổi ý nghĩa của `close()`

Một connection proxy do pool trả về thường dùng `close()` để **trả logical handle về pool**, không nhất thiết ngắt kết nối TCP vật lý. Vì vậy, không đúng khi nói “tuyệt đối không đóng pooled connection”: nếu ứng dụng checkout connection theo hợp đồng và cần trả về sau công việc, **phải đóng/return đúng lúc**. Điều sai là đóng resource **không thuộc quyền sở hữu hiện tại** hoặc đóng **quá sớm**.

## 11. Trả resource từ factory: chuyển quyền sở hữu một cách tường minh

Một API mở tệp rồi trả `InputStream` có thể định nghĩa: “Caller must close the returned stream.” Khi đó **không dùng** TWR bao quanh resource trong chính factory nếu đường thành công muốn trả resource đang mở.

Tạo chương trình **`OwnershipTransferDemo.java`**:

```java
public class OwnershipTransferDemo {
    static class TrackedResource implements AutoCloseable {
        private final String name;

        TrackedResource(String name) {
            this.name = name;
        }

        void use() {
            System.out.println("use " + name);
        }

        @Override
        public void close() {
            System.out.println("close " + name);
        }
    }

    // Hợp đồng: caller phải đóng đối tượng được trả về.
    static TrackedResource openOwned() {
        return new TrackedResource("report");
    }

    public static void main(String[] args) {
        try (TrackedResource resource = openOwned()) {
            resource.use();
        }
        System.out.println("done");
    }
}
```

```bash
javac OwnershipTransferDemo.java
java OwnershipTransferDemo
```

`stdout`:

```text
use report
close report
done
```

Quá trình:

1. `openOwned()` tạo resource và **chuyển trách nhiệm đóng** cho caller khi trả về thành công.
2. `main` khai báo resource trong header và trở thành owner cho phạm vi này.
3. Sau `use()`, Java tự gọi `close()`, rồi `main` in `done`.

Với factory thực tế có nhiều bước khởi tạo có thể thất bại, factory cần đóng những tài nguyên nó đã mở nhưng **chưa kịp chuyển giao quyền sở hữu** trên đường lỗi. Quyền sở hữu chỉ chuyển thành công sau khi kết quả hợp lệ được bàn giao.

### Factory không nên trả một tài nguyên đang đóng

Một lỗi dễ mắc:

```java
static TrackedResource openBroken() {
    try (TrackedResource resource = new TrackedResource("report")) {
        return resource;
    }
}
```

Đây là **mảnh phương thức** giả định lớp `TrackedResource` ở ví dụ trên. Trước khi caller nhận đối tượng, `try-with-resources` đã gọi `close()`. Trả tham chiếu vẫn có thể biên dịch, nhưng API “trả resource đang mở” bị vi phạm.

## 12. Tạo nhiều resource: tránh leak trước khi vào TWR

Khi khởi tạo tài nguyên bên ngoài header:

```java
Resource first = openFirst();      // Thành công.
Resource second = openSecond();    // Có thể ném ngoại lệ.
try (first; second) {
    use(first, second);
}
```

Đây là **mảnh minh họa**, cần định nghĩa `Resource` và các hàm. Nếu `openSecond()` ném ngoại lệ, chương trình **chưa thực thi** câu lệnh `try`; phạm vi TWR đó chưa chịu trách nhiệm đóng `first`. Điều này có thể gây leak.

Một cách phổ biến để tránh lỗi:

```java
try (Resource first = openFirst();
     Resource second = openSecond()) {
    use(first, second);
}
```

Đây cũng là mảnh minh họa. Khi resource thứ hai trong header khởi tạo thất bại, resource thứ nhất đã được tạo và quản lý sẽ được đóng theo ngữ nghĩa TWR.

### Khi buộc phải có resource trước header

Đôi khi resource được một tầng khác tạo và chuyển quyền sở hữu trước lời gọi. Hãy thiết kế **scope bảo vệ bắt đầu đủ sớm** để mọi đường lỗi sau thời điểm nhận quyền sở hữu đều có cleanup. Nếu caller vẫn sở hữu, **không** cố khắc phục nguy cơ leak bằng cách âm thầm nhận quyền đóng.

Cách tốt nhất phụ thuộc API: truyền factory/supplier để scope tự mở resource, dùng một object quản lý lifecycle, hoặc thiết lập hợp đồng chuyển quyền rõ ràng với try-with-resources ở caller.

## 13. Ngoại lệ từ `close()`: quyền sở hữu còn liên quan đến xử lý lỗi

Nếu đã nhận quyền đóng, bạn còn chịu trách nhiệm **quan sát lỗi đóng** phù hợp với hợp đồng.

Trong TWR:

- Nếu thân `try` thành công nhưng `close()` ném checked exception, cần bắt hoặc khai báo ngoại lệ đó.
- Nếu thân `try` ném một lỗi và `close()` cũng ném lỗi, lỗi của thân thường được giữ làm primary; lỗi từ `close()` nằm trong `getSuppressed()`.
- Nếu có nhiều resource, Java cố đóng theo thứ tự ngược và ghi nhận các lỗi đóng theo quy tắc suppressed.
- Đừng viết `finally` tự đóng trùng một resource đã được TWR quản lý, trừ khi API có yêu cầu rất cụ thể.

### Chương trình chạy được: primary và suppressed exception

Tạo file **`CloseFailureDemo.java`**:

```java
public class CloseFailureDemo {
    static class Resource implements AutoCloseable {
        @Override
        public void close() {
            throw new IllegalStateException("close failed");
        }
    }

    public static void main(String[] args) {
        Resource resource = new Resource();

        try (resource) {
            throw new IllegalArgumentException("work failed");
        } catch (IllegalArgumentException failure) {
            System.out.println("primary=" + failure.getMessage());
            System.out.println("suppressed="
                    + failure.getSuppressed()[0].getMessage());
        }
    }
}
```

```bash
javac CloseFailureDemo.java
java CloseFailureDemo
```

`stdout`:

```text
primary=work failed
suppressed=close failed
```

Ví dụ sử dụng resource thuộc sở hữu của scope `main`. Nếu đó là resource do caller khác sở hữu, việc đưa vào TWR sẽ tạo **thêm rủi ro**: không chỉ đóng quá sớm mà còn có thể xuất hiện lỗi đóng tại nơi không có quyền quyết định lifecycle.

### Nếu `close()` có side effect hoặc không idempotent

`AutoCloseable` không bắt buộc mọi implementation của `close()` hoàn toàn idempotent; một số có side effect và có thể ném nếu được gọi lại. Tránh tạo hai wrapper cùng sở hữu delegate hoặc đưa cùng resource vào nhiều scope TWR chồng lấn mà không có hợp đồng cụ thể.

Các API khác nhau có thể có bảo đảm riêng về việc đóng nhiều lần; hãy kiểm tra tài liệu của implementation thay vì áp một giả định cho mọi `AutoCloseable`.

## 14. Quyền sở hữu trong API thiết kế mới

### 14.1. Ghi rõ ai phải đóng trong JavaDoc

Đối với phương thức nhận borrowed stream:

```java
/**
 * Đọc dữ liệu từ stream do caller sở hữu.
 * Phương thức không đóng {@code source}.
 *
 * @param source stream đang mở do caller quản lý
 * @throws java.io.IOException nếu thao tác đọc thất bại
 */
static void decode(java.io.InputStream source)
        throws java.io.IOException {
    int first = source.read();
    System.out.println(first);
}
```

Đây là **mảnh phương thức minh họa**. Mục đích chính là mô tả **không đóng tham số** một cách tường minh. JavaDoc `@throws` giải thích loại lỗi, còn văn bản mô tả phải nói rõ trách nhiệm resource.

Đối với factory trả resource:

```java
/**
 * Mở một reader mới.
 * Caller nhận quyền sở hữu và phải đóng reader được trả về.
 */
static java.io.BufferedReader openDocument(java.nio.file.Path path)
        throws java.io.IOException {
    return java.nio.file.Files.newBufferedReader(path);
}
```

Đây cũng là mảnh phương thức. Trên đường thành công, reader được trả khi còn mở; trách nhiệm đóng thuộc caller.

### 14.2. Đặt tên API thể hiện ý định, nhưng không chỉ dựa vào tên

Tên như `readBorrowed`, `openOwned`, `withReader` hoặc `executeInTransaction` có thể gợi ý hợp đồng. Nhưng tên không thay thế JavaDoc và quy tắc lifecycle của framework. Khi API nhận callback để dùng resource rồi tự đóng, nó phải bảo đảm callback không lưu resource để sử dụng sau scope.

### 14.3. Không lưu resource mượn lâu hơn thời gian được phép mượn

Nếu helper nhận resource borrowed và ghi lại vào field để dùng bất đồng bộ sau khi caller rời scope, helper có thể truy cập resource đã bị owner đóng. Điều này sai ngay cả khi helper **không hề gọi `close()`**. Quyền sở hữu liên quan không chỉ “ai đóng” mà cả **thời gian được phép sử dụng**.

### 14.4. Thread-safety là yêu cầu riêng

Borrowed/owned không tự cho biết resource có dùng được đồng thời từ nhiều thread không. Với một stream, JDBC connection hay session, cần tuân thủ hợp đồng thread-safety của API. Không dùng “caller giữ ownership” để suy ra được chia sẻ resource vô hạn giữa các luồng.

## 15. Bảng tình huống quyết định

| Tình huống | Có nên dùng `try-with-resources` trong phương thức này? | Lý do |
| --- | --- | --- |
| Tự `Files.newInputStream(path)` để đọc một lần | **Có** | Scope mở và sở hữu resource |
| Nhận `InputStream` do caller giữ quyền đóng | **Không** trên chính stream đó | Caller cần tiếp tục quản lý |
| Nhận `Reader` và parse một phần dữ liệu, không chuyển quyền | **Không** trên reader nhận vào | Đóng có thể làm caller mất resource |
| Factory mới mở stream và trả cho caller | **Không** đóng trên đường trả về thành công | Quyền sở hữu được chuyển cho caller |
| Caller nhận stream mới từ factory có hợp đồng caller đóng | **Có** | Caller chịu trách nhiệm đóng |
| Mượn `System.in` để đọc tạm | **Không** trong helper | Stream dùng chung toàn tiến trình |
| Dùng wrapper đóng kéo theo borrowed stream | **Chỉ khi có quyền đóng delegate** | Closing cascade không phụ thuộc tên biến |
| Caller trực tiếp checkout connection từ pool theo hợp đồng phải trả | **Thường có**, ở phạm vi công việc phù hợp | `close()` thường trả handle về pool |
| Hàm nhận connection thuộc transaction của caller | **Không** trên connection đó | Transaction owner quyết định lifecycle |
| Tạo statement và result set nội bộ trên connection mượn | **Có** trên statement/result set | Các resource con có phạm vi của truy vấn |
| API được container giao một response stream | **Theo hợp đồng container** | Container có thể quản lý close hoặc có quy tắc riêng |
| Resource đã được đóng nhưng biến còn trong scope | Không dùng tiếp trừ khi API cho phép rõ ràng | Scope biến khác lifecycle mở |
| Nhiều resource mới có thể thất bại khi tạo lần lượt | **Ưu tiên header khai báo từ đầu** | Đảm bảo cleanup nếu khởi tạo resource sau thất bại |

Các lựa chọn trong bảng dựa trên **giả định quyền sở hữu đã nêu ở từng hàng**. Chúng không thể thay thế tài liệu riêng của driver, pool, library hoặc framework đang dùng.

## 16. Những sai lầm dễ mắc

**“`AutoCloseable` thì hễ thấy là phải tự gọi `close()` ngay.”** Không. Interface chỉ cho biết cơ chế đóng; việc ai có quyền đóng phải đến từ hợp đồng vòng đời.

**“`try-with-resources` tự phát hiện resource mượn và không đóng.”** Sai. Nếu resource được liệt kê trong header và không null, cơ chế sẽ gọi `close()` theo quy tắc Java, bất kể ai tạo đối tượng.

**“Đóng một wrapper thì delegate vẫn mở vì không nằm trong header.”** Sai với nhiều wrapper: `close()` có thể truyền xuống resource bên dưới.

**“Tạo alias `try (Reader alias = borrowed)` là cách giữ borrowed reader mở.”** Sai. Alias cùng tham chiếu đến một đối tượng; close qua alias có thể đóng chính borrowed reader.

**“Hàm trả stream trong `try-with-resources` vẫn trả stream đang mở.”** Thường sai: khi rời `try` để `return`, resource được đóng trước khi caller nhận kết quả.

**“`System.in` chỉ là một `InputStream` bình thường nên helper đóng cũng không sao.”** Sai về vòng đời dùng chung. Đóng có thể ảnh hưởng toàn bộ tiến trình.

**“Pooled connection tuyệt đối không được gọi `close()`.”** Sai. Khi ứng dụng được pool giao connection với hợp đồng trả về, `close()` có thể là thao tác cần thiết để trả handle về pool.

**“`close()` của JDBC connection chính là `commit()`.”** Sai. Quản lý transaction có semantics riêng; phải dùng API phù hợp và hợp đồng framework.

**“Reader đã mượn thì có thể lưu vào field rồi dùng sau bất cứ lúc nào.”** Sai. Bên mượn phải tuân thủ thời hạn mượn; owner có thể đóng resource khi hết thời hạn.

**“`try (resource)` Java 9 sẽ không đóng vì biến được khai báo bên ngoài.”** Sai. Tính năng Java 9 chỉ làm cú pháp ngắn hơn; resource vẫn được đóng khi thoát scope.

**“Đóng hai lần luôn an toàn.”** Không. Có API cho phép close lặp, có API có side effect hoặc không bảo đảm idempotent.

**“Không dùng TWR cho borrowed stream nghĩa là không cần xử lý ngoại lệ.”** Sai. Checked exception từ `read()` vẫn phải bắt/khai báo; caller vẫn cần cleanup ở scope sở hữu.

**“Sau khi parser đọc xong, caller không thể cần stream nữa.”** Sai. Caller có thể đọc nhiều phần, xử lý giao thức nhiều giai đoạn, retry hoặc quyết định đóng trong scope lớn hơn.

**“Resource mở trước TWR mà bước tạo kế tiếp thất bại thì TWR luôn cleanup được nó.”** Sai. Nếu chưa vào `try`, header chưa quản lý tài nguyên đó.

## 17. Tự kiểm tra

1. **Một hàm nhận `Reader` để đọc và JavaDoc nói caller sở hữu. Có nên `try (reader)` không?** Không; nó sẽ đóng resource của caller.
2. **Từ Java 9, tham số method effectively final có được dùng trong `try (reader)` về cú pháp không?** Có, nhưng phải xét quyền sở hữu trước khi dùng.
3. **Factory tạo và trả `BufferedReader` còn mở, ai đóng?** Caller nếu hợp đồng đã chuyển quyền sở hữu.
4. **Đóng `BufferedReader` bọc `InputStreamReader` có thể đóng `InputStream` gốc không?** Có, do chuỗi close của wrapper/delegate.
5. **Có thể dùng `try-with-resources` để tự đóng `PreparedStatement` nhưng giữ `Connection` mượn mở không?** Có, nếu method sở hữu statement và caller giữ quyền quản lý connection.
6. **Một pooled `Connection` được checkout cho unit of work và phải trả pool, nên đóng hay không?** Theo hợp đồng pool: thường phải gọi `close()` khi xong để trả handle.
7. **Một helper nhận `System.in` có nên tự tạo `Scanner` rồi `try (scanner)` không?** Thường không vì close của scanner có thể đóng `System.in`.
8. **Lỗi xảy ra khi mở resource thứ hai trước khi vào `try (first; second)`, `first` có chắc được đóng không?** Không; scope chưa bắt đầu.
9. **Resource chưa đóng và một helper chỉ mượn nhưng ghi lại để dùng sau khi owner kết thúc scope, hợp lệ không?** Không nếu vượt thời hạn mượn.
10. **Nếu `close()` cũng ném khi thân TWR đã ném lỗi, xem lỗi đóng ở đâu?** Thường ở `getSuppressed()` của lỗi chính.
11. **Việc đổi biến thành `final` có xác lập quyền sở hữu resource không?** Không. `final` ràng buộc gán lại biến, không xác định ai được phép đóng.
12. **Khi nào nên dùng lớp bọc leave-open?** Chỉ khi hợp đồng cần giữ delegate mở và đã phân tích side effect, buffering, flush và quy tắc đọc tiếp; không dùng mặc định.
13. **Đóng một wrapper và dùng lại stream nền sau đó có luôn giữ vị trí đọc như cũ không?** Không. Wrapper có thể đọc trước vào buffer.
14. **Nên tìm nguồn chính xác của trách nhiệm đóng ở đâu?** JavaDoc/API contract, hợp đồng framework/pool và kiến trúc quản lý lifecycle của ứng dụng.

## 18. Tài liệu tham khảo chính thức

- [Java Language Specification 17 — §14.20.3, try-with-resources](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.3): cơ chế đóng, đường lỗi và suppressed exceptions.
- [Java SE 17 API — AutoCloseable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/AutoCloseable.html): hợp đồng `close()` và lưu ý về idempotency.
- [Java SE 17 API — Closeable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Closeable.html): contract đóng tài nguyên I/O.
- [Java SE 17 API — BufferedReader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedReader.html) và [InputStreamReader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStreamReader.html): lớp bọc reader và hành vi đóng.
- [Java SE 17 API — Scanner](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Scanner.html): đóng scanner và nguồn đầu vào liên quan.
- [Java SE 17 API — Connection](https://docs.oracle.com/en/java/javase/17/docs/api/java.sql/java/sql/Connection.html): lifecycle và transaction của JDBC connection.
- [Java SE 17 API — Throwable.getSuppressed](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html#getSuppressed()): đọc suppressed exceptions do đóng resource.
- [Java Language Specification 17 — §4.12.4 final Variables](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.12.4): biến final/effectively final dùng trong resource access.

**Nguyên tắc trọng tâm:** `try-with-resources` rất hữu ích khi **scope hiện tại có quyền và nghĩa vụ đóng** tài nguyên. Không đặt tài nguyên chỉ **mượn** hoặc do thành phần khác quản lý vào TWR một cách máy móc. Hãy làm rõ người sở hữu, thời hạn sử dụng, closing cascade của wrapper, semantics của pool/framework và cả đường lỗi khi bàn giao resource; từ đó đặt việc tự động đóng ở **đúng ranh giới vòng đời**.


</details>

- [Quay lại đầu trang](#back-to-top)
