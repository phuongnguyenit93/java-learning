# String Pool

Vì String immutable, JVM có thể an toàn chia sẻ một số String có cùng nội dung thay vì luôn tạo object mới. Đây là ý tưởng nền tảng của **String pool**.

## <a id="string-pool-model">String Pool là gì?</a>

String pool lưu các reference chuẩn hóa cho một số String, đặc biệt là String literal và giá trị được `intern()`.

```java
String a = "java";
String b = "java";
```

Trong cùng runtime ngữ cảnh phù hợp, `a` và `b` thường tham chiếu cùng pooled String object.

Điểm cần nhớ là pool là **identity optimization/canonicalization**, không thay đổi hợp đồng so sánh text: muốn so nội dung vẫn dùng `equals`.

## <a id="literal-vs-new">Literal và new String</a>

```java
String a = "java";
String b = new String("java");
```

`a` thường trỏ tới pooled literal, còn `new String(...)` yêu cầu tạo String object mới.

Vì vậy:

```java
a == b      // thường false
a.equals(b) // true
```

Không dùng `new String("...")` nếu chỉ cần một literal bình thường; nó thường thêm object không cần thiết.

## <a id="pool-identity">String Pool và Identity</a>

Compile-time constant concatenation có thể được gộp thành cùng pooled literal:

```java
String a = "ja" + "va";
String b = "java";
```

Trong khi concatenation phụ thuộc runtime value không có cùng guarantee về identity.

Đừng viết business logic dựa trên pooled identity. Pooling là optimization/runtime hành vi; **text equality vẫn là `equals`**.

chương tiếp theo tập trung trực tiếp vào quy tắc so sánh String.
