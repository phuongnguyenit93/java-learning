# String.intern

String pool có thể chia sẻ identity cho literal và một số String đã được chuẩn hóa. `String.intern()` là API cho phép yêu cầu một **reference chuẩn trong pool** tương ứng với cùng nội dung text.

## <a id="intern-semantics">String.intern làm gì?</a>

Khi gọi:

```java
String canonical = value.intern();
```

JVM trả về reference đại diện trong String pool cho chuỗi có cùng nội dung.

Contract mental model:

```text
value.intern()
        │
        ├── pool đã có String equals(value)
        │      → trả canonical pooled reference đã có
        │
        └── chưa có
               → value trở thành canonical representative
               → trả canonical reference
```

Điều này không thay đổi nội dung của `value` và cũng không làm `String` trở nên mutable. `intern()` chỉ liên quan tới **identity/canonicalization**, không thay đổi equality ngữ nghĩa.

### WHY intern tồn tại?

Intern cho phép application yêu cầu canonical identity cho equal String values. Nó có thể hữu ích trong một số workload có tập value lặp lại nhiều và cardinality được kiểm soát.

Nếu mục tiêu chỉ là “hai chuỗi có cùng nội dung?”, `equals` đã là API đúng; không cần intern trước khi compare.

## <a id="intern-identity">Canonical Reference</a>

Ví dụ:

```java
String a = new String("java");
String b = a.intern();
String c = "java";

b == c // true
```

Sau `intern()`, `b` dùng canonical pooled reference cho nội dung `"java"`.

Nhưng business logic vẫn nên dùng `equals` khi câu hỏi là **nội dung có bằng nhau không**. Không nên chuyển mọi comparison sang identity chỉ vì có `intern()`.

Intern nối trực tiếp với literal:

```java
String runtime = new String("java");
String canonical = runtime.intern();
String literal = "java";

canonical == literal // true
```

Đây là identity experiment hợp lệ vì canonicalization chính là concept đang quan sát.

## <a id="intern-tradeoffs">Đánh đổi của Interning</a>

Interning có thể giảm số object identity khác nhau cho một tập String lặp lại nhiều, nhưng không phải optimization mặc định cho mọi ứng dụng.

Chi phí/cân nhắc gồm:

- thao tác lookup/canonicalization;
- giữ nhiều String trong pool;
- memory pressure nếu intern dữ liệu có cardinality rất lớn;
- làm mã phụ thuộc không cần thiết vào identity.

### High-cardinality và untrusted input

Nếu intern value gần như unique:

```text
user-000001
user-000002
user-000003
...
```

canonicalization có thể không đem lại deduplication đáng kể nhưng vẫn thêm lookup/retention pressure.

Đặc biệt không nên intern user-controlled data cardinality cao chỉ vì “tiết kiệm memory”.

### Không dựa vào folklore JVM cũ

Các câu kiểu “interned strings luôn nằm ở PermGen” là knowledge gắn với implementation/version cũ, không phải Java language contract.

Application nên reasoning theo:

```text
canonical identity
lookup cost
retention/memory profile
workload cardinality
```

Chỉ intern khi workload và measurement cho thấy lợi ích rõ ràng, hoặc hợp đồng thực sự cần canonical identity.

chương tiếp theo rời khỏi identity và đi qua một ranh giới quan trọng hơn: **String trong JVM biến thành byte bên ngoài JVM bằng cách nào?**
