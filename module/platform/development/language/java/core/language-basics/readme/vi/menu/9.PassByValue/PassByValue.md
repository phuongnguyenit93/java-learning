# Pass-by-Value trong Java

## <a id="java-pass-by-value">Java luôn pass-by-value</a>
Mọi argument đều được copy vào parameter. Với primitive, primitive value được copy. Với object, value được copy chính là reference value. Java không truyền chính biến của caller theo pass-by-reference.

## <a id="reference-copy-mutation">Copy reference và mutation nhìn thấy từ caller</a>
Khi copied reference vẫn nhận diện cùng một mutable object, callee có thể mutate object đó và caller quan sát được state mới.

```java
void add(List<String> x) { x.add("A"); }
```

Visible mutation không biến Java thành pass-by-reference; caller và callee chỉ giữ hai bản copy reference cùng tới một object.

## <a id="reassignment-vs-mutation">Parameter reassignment khác object mutation</a>
Reassign parameter chỉ đổi local parameter variable của callee. Nó không đổi object mà caller variable đang refer tới. Đây là cách phân biệt đơn giản nhất giữa copy reference value và pass-by-reference thật sự.
