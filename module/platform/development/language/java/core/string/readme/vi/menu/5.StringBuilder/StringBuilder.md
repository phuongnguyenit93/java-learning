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

## <a id="builder-capacity">Length và Capacity</a>

`length()` là số character/code unit hiện có trong builder.

`capacity()` là kích thước buffer nội bộ hiện có trước khi cần grow.

Capacity là performance detail hữu ích khi dự đoán đầu ra lớn, nhưng không phải part của text result ngữ nghĩa.

Pre-size capacity có thể giảm resize/copy nếu biết gần đúng kích thước cuối; không cần tối ưu sớm cho chuỗi nhỏ.

## <a id="builder-usage">Xây chuỗi tăng dần</a>

Builder phù hợp khi:

- loop append nhiều phần;
- format đầu ra theo nhiều branch;
- tạo text trong một method/thread-local scope;
- cần giảm intermediate String.

Sau khi gọi `toString()`, String result là immutable và độc lập về ngữ nghĩa với các thay đổi builder tiếp theo.

chương tiếp theo so `StringBuilder` với phiên bản có synchronization: `StringBuffer`.
