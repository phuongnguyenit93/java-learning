# Kế thừa giữa các Interface

Một interface có thể mô tả một khả năng nhỏ. Khi hệ thống lớn hơn, ta có thể ghép các khả năng đó thành hợp đồng lớn hơn bằng kế thừa interface.

Nếu gom mọi hành vi vào một interface rất lớn, class triển khai có thể bị buộc phụ thuộc vào những khả năng nó không thật sự cần. Tách các vai trò nhỏ giúp hợp đồng rõ hơn:

```text
Payable
Refundable
Auditable
```

Sau đó một hợp đồng lớn hơn chỉ ghép đúng những vai trò cần thiết. Vì vậy kế thừa interface không chỉ là cú pháp `extends`; nó là cách **ghép các hợp đồng** mà vẫn giữ từng khả năng có phạm vi rõ ràng.

## <a id="interface-extends-interface">Interface kế thừa Interface</a>

Interface dùng `extends` để kế thừa một hoặc nhiều interface khác:

```java
interface Payable {
    void pay();
}

interface RefundablePayment extends Payable {
    void refund();
}
```

Class triển khai `RefundablePayment` phải thỏa cả `pay()` và `refund()`.

Subinterface cũng có thể bổ sung method mới hoặc thu hẹp kiểu trả về nếu vẫn tương thích với hợp đồng được kế thừa.

## <a id="multiple-interface-hierarchy">Nhiều Interface Cha</a>

Một interface có thể `extends` nhiều interface:

```java
interface AuditedPayment extends Payable, Auditable { ... }
```

Đây là đa kế thừa về **kiểu/hợp đồng**, không phải việc sao chép trạng thái instance từ nhiều class cha.

Nếu các abstract method có signature tương thích, hợp đồng có thể ghép tự nhiên. Nếu kiểu trả về không tương thích hoặc nhiều `default` method cạnh tranh nhau, hệ phân cấp có thể trở nên không hợp lệ hoặc cần giải quyết xung đột rõ ràng.

## <a id="interface-redeclaration">Redeclare Method được kế thừa</a>

Subinterface có thể khai báo lại một method được kế thừa để:

- thêm tài liệu hoặc annotation;
- thu hẹp kiểu trả về theo covariant return;
- biến một `default` method được kế thừa trở lại thành yêu cầu abstract.

Ví dụ:

```java
interface Base {
    default Number value() { return 0; }
}

interface Specific extends Base {
    @Override
    Integer value();
}
```

Việc khai báo lại nên làm hợp đồng rõ hơn; nếu chỉ lặp lại signature mà không thêm ý nghĩa thì thường không cần thiết.

Tiếp theo ta xem vì sao interface lại có `default`, `static` và `private` method, và mỗi loại dùng để làm gì.
