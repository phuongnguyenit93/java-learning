# String.intern

String pool có thể chia sẻ identity cho literal và một số String đã được chuẩn hóa. `String.intern()` là API cho phép yêu cầu một **reference chuẩn trong pool** tương ứng với cùng nội dung text.

## <a id="intern-semantics">String.intern làm gì?</a>

Khi gọi:

```java
String canonical = value.intern();
```

JVM trả về reference đại diện trong String pool cho chuỗi có cùng nội dung.

Điều này không thay đổi nội dung của `value` và cũng không làm `String` trở nên mutable. `intern()` chỉ liên quan tới **identity/canonicalization**, không thay đổi equality ngữ nghĩa.

## <a id="intern-identity">Canonical Reference</a>

Ví dụ:

```java
String a = new String("java");
String b = a.intern();
String c = "java";

b == c // true trong cùng runtime context phù hợp
```

Sau `intern()`, `b` dùng canonical pooled reference cho nội dung `"java"`.

Nhưng business logic vẫn nên dùng `equals` khi câu hỏi là **nội dung có bằng nhau không**. Không nên chuyển mọi comparison sang identity chỉ vì có `intern()`.

## <a id="intern-tradeoffs">Đánh đổi của Interning</a>

Interning có thể giảm số object identity khác nhau cho một tập String lặp lại nhiều, nhưng không phải optimization mặc định cho mọi ứng dụng.

Chi phí/cân nhắc gồm:

- thao tác lookup/canonicalization;
- giữ nhiều String trong pool;
- memory pressure nếu intern dữ liệu có cardinality rất lớn;
- làm mã phụ thuộc không cần thiết vào identity.

Chỉ intern khi workload và measurement cho thấy lợi ích rõ ràng, hoặc hợp đồng thực sự cần canonical identity.

chương tiếp theo rời khỏi identity và đi qua một ranh giới quan trọng hơn: **String trong JVM biến thành byte bên ngoài JVM bằng cách nào?**
