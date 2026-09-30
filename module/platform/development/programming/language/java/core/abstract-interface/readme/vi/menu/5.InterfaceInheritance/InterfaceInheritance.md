# Kế thừa và kết hợp interface

Một interface có thể mô tả một khả năng nhỏ. Khi hệ thống lớn hơn, các hợp đồng nhỏ có thể được kết hợp thành hợp đồng lớn hơn bằng **kế thừa interface**.

Nếu gom mọi hành vi vào một interface rất lớn, lớp triển khai có thể bị buộc phụ thuộc vào những khả năng nó không thật sự cần. Tách các vai trò nhỏ giúp hợp đồng rõ hơn:

```text
Payable
Refundable
Auditable
```

Sau đó một hợp đồng lớn hơn chỉ ghép những vai trò cần thiết. Vì vậy kế thừa interface không chỉ là cú pháp `extends`; nó là cách **kết hợp các hợp đồng** mà vẫn giữ từng khả năng có phạm vi rõ ràng.

## <a id="interface-extends-interface">Interface kế thừa interface</a>

Interface dùng `extends` để kế thừa một hoặc nhiều interface khác:

```java
interface Payable {
    void pay();
}

interface RefundablePayment extends Payable {
    void refund();
}
```

Lớp triển khai `RefundablePayment` phải thỏa cả `pay()` và `refund()`.

Interface con cũng có thể bổ sung phương thức mới hoặc tinh chỉnh kiểu trả về nếu vẫn tương thích với hợp đồng được kế thừa.

## <a id="multiple-interface-hierarchy">Nhiều interface cha</a>

Một interface có thể `extends` nhiều interface:

```java
interface AuditedPayment extends Payable, Auditable { ... }
```

Đây là đa kế thừa về **kiểu/hợp đồng**. Interface không đóng góp trạng thái riêng cho từng đối tượng, và cơ chế này không phải đa kế thừa lớp.

Nếu các phương thức abstract có chữ ký tương thích, các hợp đồng có thể kết hợp tự nhiên. Nếu kiểu trả về xung đột và không thể thỏa đồng thời, interface con có thể không hợp lệ ngay tại thời điểm biên dịch.

Các xung đột liên quan tới phương thức `default` được tách sang phần sau, sau khi vai trò của `default` đã được giải thích đầy đủ.

## <a id="interface-redeclaration">Khai báo lại phương thức được kế thừa</a>

Interface con có thể khai báo lại một phương thức được kế thừa để:

- bổ sung tài liệu hoặc annotation;
- thu hẹp kiểu trả về theo quy tắc kiểu trả về đồng biến (covariant return);
- làm rõ hơn hợp đồng mà interface con muốn công khai.

Ví dụ:

```java
interface Base {
    Number value();
}

interface Specific extends Base {
    @Override
    Integer value();
}
```

`Integer` là kiểu trả về hẹp hơn nhưng vẫn tương thích với `Number`, nên `Specific` có thể tinh chỉnh hợp đồng theo cách này.

Không nên khai báo lại chỉ để lặp lại cùng một chữ ký mà không bổ sung ý nghĩa. Mục tiêu là làm hợp đồng rõ hơn, không tạo thêm nhiễu.

Bước tiếp theo giải thích vì sao interface hiện đại có phương thức `default`, `static` và `private`, và mỗi loại giải quyết vấn đề gì.
