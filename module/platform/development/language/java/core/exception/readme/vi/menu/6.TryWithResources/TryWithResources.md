# Try-with-resources

File, stream, socket, JDBC object và nhiều resource bên ngoài heap cần được **đóng đúng thời điểm**. Garbage Collector quản lý memory của object Java, nhưng không phải là contract để giải phóng file descriptor, socket hay database handle đúng lúc.

`try-with-resources` (TWR) biến resource ownership và cleanup thành một phần rõ ràng của cú pháp.

## <a id="autocloseable">AutoCloseable</a>

Một resource dùng trong TWR phải có kiểu implement `AutoCloseable`:

```java
public interface AutoCloseable {
    void close() throws Exception;
}
```

Ví dụ:

```java
try (InputStream in = Files.newInputStream(path)) {
    return in.read();
}
```

Flow cơ bản:

```text
tạo resource
        ↓
chạy body
        ↓
rời body bằng success / return / throw
        ↓
Java gọi close()
        ↓
sau đó control flow mới tiếp tục ra ngoài
```

### VÌ SAO KHÔNG CHỜ GARBAGE COLLECTOR?

Một object stream có thể trở nên unreachable nhưng OS resource phía dưới vẫn cần cleanup deterministic.

Mental model:

```text
memory lifetime
≠
external resource lifetime
```

TWR giải quyết **lifecycle/ownership**, không phải tối ưu GC.

### Catch/finally nằm ở đâu?

```java
try (InputStream in = Files.newInputStream(path)) {
    use(in);
} catch (IOException ex) {
    handle(ex);
} finally {
    afterOperation();
}
```

Resource được close khi rời resource `try` **trước khi** `catch`/`finally` bên ngoài cấu trúc hoàn tất xử lý exception theo semantics của TWR.

Điều đó cho phép handler quan sát failure sau khi cleanup resource đã được thực hiện.

## <a id="resource-close-order">Thứ tự đóng tài nguyên</a>

Resource được khởi tạo từ trái sang phải:

```java
try (A a = openA();
     B b = openB();
     C c = openC()) {
    use(a, b, c);
}
```

Initialization:

```text
A → B → C
```

Close theo thứ tự ngược:

```text
C → B → A
```

### VÌ SAO ĐÓNG NGƯỢC?

Resource tạo sau thường phụ thuộc resource tạo trước.

Ví dụ wrapper:

```java
try (InputStream raw = Files.newInputStream(path);
     BufferedInputStream buffered = new BufferedInputStream(raw)) {
    ...
}
```

`buffered` bọc `raw`, nên wrapper được close trước.

### Nếu khởi tạo resource ở giữa bị fail?

Đây là edge case quan trọng.

```java
try (A a = openA();
     B b = openB();   // throw tại đây
     C c = openC()) {
    ...
}
```

Kết quả:

```text
A đã tạo thành công
B khởi tạo fail
C chưa bao giờ được tạo
        ↓
A vẫn được close
```

TWR chịu trách nhiệm cleanup những resource đã khởi tạo thành công trước điểm failure.

Điều này khó viết đúng nếu tự quản lý nhiều `try/finally` lồng nhau.

## <a id="effective-final-resource">Effective-final Resource</a>

Từ Java 9, local variable đã tồn tại có thể được dùng trực tiếp trong resource specification nếu nó là `final` hoặc effectively final:

```java
InputStream in = Files.newInputStream(path);

try (in) {
    consume(in);
}
```

Effectively final nghĩa là sau khi được gán giá trị, variable không bị gán lại.

Không hợp lệ về mặt effective-final:

```java
InputStream in = Files.newInputStream(path);
in = anotherStream;

try (in) {
    ...
}
```

### Ownership vẫn cần rõ ràng

Cú pháp cho phép dùng variable có sẵn không có nghĩa mọi method nhận `AutoCloseable` đều nên close nó.

Hãy hỏi:

```text
Ai tạo resource?
Ai sở hữu lifecycle?
Ai có trách nhiệm close?
```

Nếu method chỉ “mượn” resource do caller sở hữu, tự close nó có thể vi phạm contract của caller.

## <a id="twr-vs-finally">Try-with-resources và finally</a>

Manual cleanup:

```java
InputStream in = Files.newInputStream(path);
try {
    return in.read();
} finally {
    in.close();
}
```

trông đơn giản nhưng có vấn đề nếu:

```text
body throw A
và
close throw B
```

Với manual `finally`, B có thể che A nếu code không tự xử lý cẩn thận.

TWR có semantics chuẩn để giữ failure chính và gắn cleanup failure dưới dạng suppressed exception.

### So sánh

```text
manual finally
→ tự quản lý null, thứ tự close, nhiều resource, nhiều failure

try-with-resources
→ ownership rõ trong syntax
→ close order được định nghĩa
→ cleanup khi partial initialization fail
→ primary/suppressed failure được bảo toàn theo contract
```

TWR không thay thế mọi `finally`; `finally` vẫn hữu ích cho cleanup không biểu diễn bằng `AutoCloseable`. Nhưng với owned resource, TWR thường là lựa chọn mặc định tốt hơn.

### GHI NHỚ

```text
resource specification
→ ownership/lifecycle rõ ràng

khởi tạo
→ trái sang phải

đóng
→ phải sang trái

khởi tạo giữa chừng fail
→ resource đã mở trước đó vẫn được close

body + close cùng fail
→ cần suppressed exception
```

Chương tiếp theo đi sâu vào điểm cuối: nếu operation đã fail mà cleanup cũng fail, Java giữ cả hai failure như thế nào?