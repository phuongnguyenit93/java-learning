# StringBuilder

`StringBuilder` không phải “String có thể sửa”. Nó là **mutable buffer dùng để xây String**, sau đó tạo String result bằng `toString()`.

## <a id="builder-mutable-buffer">Mutable Buffer</a>

```java
StringBuilder builder = new StringBuilder();
builder.append("Hello");
builder.append(' ');
builder.append(name);
String result = builder.toString();
```

Khác String, cùng builder object thay đổi internal buffer qua nhiều `append`.

Đây là lý do builder phù hợp cho incremental construction trong một scope kiểm soát rõ.

### WHY — giải quyết vấn đề gì?

String immutable rất tốt cho sharing, nhưng không lý tưởng nếu ta cần xây result qua rất nhiều bước:

```text
repeated String concat
→ nhiều intermediate immutable value

StringBuilder
→ một mutable construction object
→ append nhiều lần
→ toString khi hoàn tất
```

### Fluent append

Nhiều mutating method trả lại chính builder:

```java
String result = new StringBuilder()
        .append("user=")
        .append(userId)
        .append(", active=")
        .append(active)
        .toString();
```

`append` có overload cho primitive, String, character sequence và object. Builder là construction tool, không phải “String mutable”.

## <a id="builder-capacity">Length và Capacity</a>

`length()` là số character/code unit hiện có trong builder.

`capacity()` là kích thước buffer nội bộ hiện có trước khi cần grow.

Capacity là performance detail hữu ích khi dự đoán đầu ra lớn, nhưng không phải part của text result ngữ nghĩa.

Pre-size capacity có thể giảm resize/copy nếu biết gần đúng kích thước cuối; không cần tối ưu sớm cho chuỗi nhỏ.

### Capacity không phải maximum length

```java
StringBuilder b = new StringBuilder(8);
b.append("this text is longer than eight");
```

Builder grow khi cần. Initial capacity là performance hint, không phải max-length contract.

Implementation có growth strategy riêng; application không nên phụ thuộc vào một công thức capacity expansion cụ thể.

`ensureCapacity(n)` có thể hữu ích khi biết lower bound lớn, nhưng chỉ đáng dùng khi workload/measurement cho thấy resize-copy thực sự là vấn đề.

### setLength cần cẩn thận

`setLength` có thể truncate hoặc mở rộng builder. Khi mở rộng, vị trí mới chứa null character (`\u0000`), không phải space. Vì vậy nó không phải API “pad bằng khoảng trắng”.

## <a id="builder-usage">Xây chuỗi tăng dần</a>

Builder phù hợp khi:

- loop append nhiều phần;
- format đầu ra theo nhiều branch;
- tạo text trong một method/thread-local scope;
- cần giảm intermediate String.

Sau khi gọi `toString()`, String result là immutable và độc lập về ngữ nghĩa với các thay đổi builder tiếp theo.

### Snapshot semantics của toString

```java
StringBuilder b = new StringBuilder("java");

String first = b.toString();
b.append("-core");
String second = b.toString();

System.out.println(first);  // java
System.out.println(second); // java-core
```

`first` không thay đổi khi builder tiếp tục mutate.

### Operation thường gặp

Ngoài `append` còn có:

```java
builder.insert(...);
builder.delete(...);
builder.replace(...);
builder.reverse();
```

Chọn chúng khi thật sự đang chỉnh **construction buffer**. Nếu đã có final String value và chỉ cần transformation đơn giản, String API thường diễn đạt intent tốt hơn.

### Thread-safety boundary

`StringBuilder` không synchronized và không được thiết kế để một mutable instance bị nhiều thread cùng mutate mà không có coordination.

Pattern phổ biến:

```text
method/thread-local builder
→ build
→ toString
→ publish immutable String
```

chương tiếp theo so `StringBuilder` với phiên bản có synchronization: `StringBuffer`.
