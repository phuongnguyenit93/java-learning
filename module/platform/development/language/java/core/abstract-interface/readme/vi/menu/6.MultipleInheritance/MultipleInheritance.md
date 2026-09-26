# Multiple Inheritance qua Interface

Java không hỗ trợ một class kế thừa cách triển khai từ nhiều class cha, nhưng một class có thể implement nhiều interface. Khi các interface chỉ chứa abstract hợp đồng thì thường không có vấn đề. Xung đột đáng chú ý xuất hiện khi nhiều interface cung cấp **default cách triển khai** cho cùng một method.

## <a id="default-method-conflict">Xung đột Default Method</a>

Nếu hai interface không có quan hệ kế thừa cung cấp cùng một default method:

```java
interface A {
    default String name() { return "A"; }
}

interface B {
    default String name() { return "B"; }
}
```

thì class implement cả `A` và `B` phải override `name()` để tự quyết định hành vi.

Java không tự đoán default nào “đúng hơn” khi không có interface nào cụ thể hơn interface còn lại.

## <a id="class-wins-rule">Class Method được ưu tiên</a>

Nếu cây kế thừa class đã cung cấp một concrete instance method tương thích, method từ class được ưu tiên hơn interface default.

```text
concrete class method
→ ưu tiên

interface default
→ dùng khi cây kế thừa class không có phần triển khai phù hợp
```

Default method vì vậy không phải cơ chế để âm thầm override hành vi đã có từ class cha.

## <a id="explicit-super-interface">Gọi InterfaceName.super</a>

Khi class phải override để xử lý xung đột, nó có thể gọi explicit một default method từ directly inherited interface:

```java
@Override
public String name() {
    return A.super.name() + B.super.name();
}
```

Cú pháp `InterfaceName.super.method()` cho phép class **kết hợp** default hành vi thay vì buộc phải viết lại toàn bộ.

## <a id="diamond-interface">Diamond Interface</a>

Hình diamond tự thân không phải vấn đề.

Nếu hai nhánh cuối cùng cùng kế thừa **một most-specific default method**, hợp đồng vẫn rõ ràng. Xung đột chỉ xuất hiện khi có nhiều default method độc lập cùng cạnh tranh mà không có method nào cụ thể hơn.

Điểm cần nhớ:

```text
multiple interface inheritance
→ ghép kiểu/hợp đồng và có thể ghép hành vi từ default method
→ không tạo nhiều bản sao trạng thái instance như đa kế thừa class
```

Sau module này, khi gặp `abstract class` hoặc `interface`, hãy hỏi trước **mối quan hệ thiết kế là gì**: cần shared trạng thái/partial cách triển khai hay cần một capability hợp đồng linh hoạt?
