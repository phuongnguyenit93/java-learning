# Multiple Inheritance qua Interface

Java không hỗ trợ một class kế thừa cách triển khai từ nhiều class cha, nhưng một class có thể implement nhiều interface. Khi các interface chỉ chứa abstract method thì thường không có vấn đề. Xung đột đáng chú ý xuất hiện khi nhiều interface cung cấp **phần triển khai mặc định** cho cùng một method.

Đừng ghi nhớ các quy tắc dưới dạng danh sách rời rạc. Có thể đọc quá trình giải quyết theo thứ tự:

```text
Cây kế thừa class đã có method cụ thể tương thích?
→ có: class method được dùng

Nếu không, trong các interface có một default cụ thể hơn các default còn lại?
→ có: default cụ thể hơn được dùng

Nếu vẫn còn nhiều default độc lập cùng cạnh tranh?
→ class phải override để giải quyết rõ ràng
```

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

Ngược lại, nếu một interface kế thừa interface khác và override default method, default ở subinterface là lựa chọn cụ thể hơn:

```java
interface Parent {
    default String name() { return "parent"; }
}

interface Child extends Parent {
    @Override
    default String name() { return "child"; }
}
```

Một class chỉ nhận `Child` không phải tự giải quyết lại giữa `Parent.name()` và `Child.name()`; `Child` đã là hợp đồng cụ thể hơn.

## <a id="class-wins-rule">Class Method được ưu tiên</a>

Nếu cây kế thừa class đã cung cấp một instance method cụ thể tương thích, method từ class được ưu tiên hơn interface default.

```java
class Named {
    public String name() {
        return "class";
    }
}

interface NamedContract {
    default String name() {
        return "interface";
    }
}

class CardPayment extends Named implements NamedContract {
}
```

Với `new CardPayment().name()`, phần triển khai từ `Named` được dùng. Default method vì vậy không phải cơ chế để âm thầm override hành vi đã có từ class cha.

Quy tắc này đang nói về **method cụ thể của class**. Nếu superclass chỉ khai báo abstract method cùng signature, class cụ thể vẫn phải thỏa hợp đồng đó; không nên hiểu “class wins” như một phép chọn ở runtime giữa hai phần thân có sẵn.

## <a id="explicit-super-interface">Gọi InterfaceName.super</a>

Khi class phải override để xử lý xung đột, nó có thể gọi tường minh một default method từ interface cha trực tiếp:

```java
@Override
public String name() {
    return A.super.name() + B.super.name();
}
```

Cú pháp `InterfaceName.super.method()` cho phép class **kết hợp** hành vi mặc định thay vì buộc phải viết lại toàn bộ.

## <a id="diamond-interface">Diamond Interface</a>

Hình diamond tự thân không phải vấn đề.

Nếu hai nhánh cuối cùng cùng kế thừa **một default method cụ thể nhất (most-specific)**, hợp đồng vẫn rõ ràng. Xung đột chỉ xuất hiện khi có nhiều default method độc lập cùng cạnh tranh mà không có method nào cụ thể hơn.

Điểm cần nhớ:

```text
multiple interface inheritance
→ ghép kiểu/hợp đồng và có thể ghép behavior từ default method
→ không tạo nhiều bản sao trạng thái instance như đa kế thừa class
```

Sau module này, khi gặp `abstract class` hoặc `interface`, hãy hỏi trước **mối quan hệ thiết kế là gì**: cần trạng thái/phần triển khai dùng chung hay cần một khả năng/hợp đồng linh hoạt?
