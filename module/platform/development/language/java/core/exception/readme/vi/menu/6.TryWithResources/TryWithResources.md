# Try-with-resources

Tài nguyên như file, stream, socket hoặc database handle thường phải được đóng **đúng thời điểm**, không thể chờ Garbage Collector. `try-with-resources` biến việc sở hữu và dọn dẹp tài nguyên thành một phần rõ ràng của cú pháp.

## <a id="autocloseable">AutoCloseable</a>

Tài nguyên dùng được với `try-with-resources` phải implement `AutoCloseable`:

```java
interface AutoCloseable {
    void close() throws Exception;
}
```

Ví dụ:

```java
try (InputStream in = Files.newInputStream(path)) {
    return in.read();
}
```

Khi block kết thúc, Java gọi `close()` tự động theo hợp đồng của `try-with-resources`.

## <a id="resource-close-order">Thứ tự đóng tài nguyên</a>

Nếu khai báo nhiều tài nguyên:

```java
try (A a = ...; B b = ...; C c = ...) {
    ...
}
```

chúng được đóng theo thứ tự ngược:

```text
C → B → A
```

Thứ tự này tương ứng với thứ tự sở hữu: tài nguyên tạo sau thường phụ thuộc tài nguyên tạo trước và cần được đóng trước.

## <a id="effective-final-resource">Effective-final Resource</a>

Từ Java 9, một biến cục bộ đã tồn tại có thể được dùng trực tiếp trong danh sách tài nguyên nếu nó là `final` hoặc effectively final:

```java
InputStream in = Files.newInputStream(path);

try (in) {
    ...
}
```

Biến không được gán lại sau khi được xác định là tài nguyên của khối `try`.

## <a id="twr-vs-finally">Try-with-resources và finally</a>

`try-with-resources` thường tốt hơn `finally` thủ công khi quản lý tài nguyên vì:

- ít mã lặp theo khuôn mẫu;
- close order rõ ràng;
- giữ primary exception tốt hơn khi `close()` cũng fail;
- khó quên dọn dẹp hơn.

Điểm cuối dẫn trực tiếp tới chương tiếp theo: **nếu phần thân `try` throw và `close()` cũng throw, exception nào được giữ?**
