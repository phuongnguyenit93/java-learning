# Kế thừa giữa các Interface

Một interface có thể mô tả một capability nhỏ. Khi hệ thống lớn hơn, ta có thể ghép các capability đó thành hợp đồng lớn hơn bằng interface inheritance.

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

Subinterface cũng có thể bổ sung method mới hoặc refine return type nếu vẫn tương thích với hợp đồng được kế thừa.

## <a id="multiple-interface-hierarchy">Nhiều Interface Cha</a>

Một interface có thể `extends` nhiều interface:

```java
interface AuditedPayment extends Payable, Auditable { ... }
```

Đây là đa kế thừa về **kiểu/hợp đồng**, không phải việc sao chép trạng thái instance từ nhiều class cha.

Nếu các abstract method có signature tương thích, hợp đồng có thể ghép tự nhiên. Nếu return type không tương thích hoặc nhiều default method cạnh tranh nhau, hierarchy có thể trở nên không hợp lệ hoặc cần giải quyết xung đột rõ ràng.

## <a id="interface-redeclaration">Redeclare Method được kế thừa</a>

Subinterface có thể khai báo lại một inherited method để:

- thêm documentation hoặc annotation;
- thu hẹp return type theo covariant return;
- biến một inherited default method trở lại thành abstract yêu cầu.

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

Redeclaration nên làm hợp đồng rõ hơn; nếu chỉ lặp lại signature mà không thêm ý nghĩa thì thường không cần thiết.

Tiếp theo ta xem vì sao interface lại có `default`, `static` và `private` method, và mỗi loại dùng để làm gì.
