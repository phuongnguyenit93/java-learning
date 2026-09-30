# Tổng hợp thiết kế bằng lớp trừu tượng và interface

Sau module này, câu hỏi đầu tiên không nên là “dùng `abstract class` hay `interface` vì cái nào tốt hơn?”. Câu hỏi đúng hơn là: **mối quan hệ thiết kế nào đang cần được mô hình hóa?**

## <a id="abstraction-design-synthesis">Mô hình quyết định tổng thể</a>

Có thể đi theo chuỗi câu hỏi sau:

```text
Bên sử dụng chỉ cần một hợp đồng/khả năng chung?
→ ưu tiên interface

Một họ lớp có quan hệ gần và cần trạng thái + quy tắc khởi tạo + phần triển khai chung?
→ cân nhắc abstract class

Một lớp cần đảm nhận nhiều vai trò độc lập?
→ dùng nhiều interface

Cần hợp đồng linh hoạt nhưng một nhánh triển khai vẫn muốn dùng lại phần chung?
→ interface + lớp cha trừu tượng

Interface cần ghép nhiều hợp đồng nhỏ?
→ kế thừa interface

Interface cần hành vi mặc định cho đối tượng?
→ dùng default

Cần thao tác thuộc chính kiểu interface?
→ dùng static

Cần tái sử dụng chi tiết triển khai nội bộ?
→ dùng private / private static

Nhiều phương thức `default` cạnh tranh?
→ áp dụng quy tắc về độ cụ thể, ưu tiên hệ phân cấp lớp và ghi đè tường minh
```

Mục tiêu là chọn cơ chế từ **nhu cầu của mô hình**, không từ thói quen cú pháp.

## <a id="abstraction-design-example">Ghép lại ví dụ thanh toán</a>

```text
PaymentMethod
→ hợp đồng công khai mà checkout(...) phụ thuộc

BasePayment
→ lớp trừu tượng cho một nhánh triển khai cần trạng thái `provider`, kiểm tra hợp lệ và hành vi nền

CardPayment
→ lớp cụ thể hoàn thiện hành vi thanh toán

Refundable
→ khả năng độc lập có thể gắn vào CardPayment hoặc các kiểu khác
```

Ví dụ hoàn chỉnh:

```java
interface PaymentMethod {
    void pay(int amount);

    default String label() {
        return "payment";
    }
}

interface Refundable {
    void refund(int amount);
}

abstract class BasePayment implements PaymentMethod {
    private final String provider;

    protected BasePayment(String provider) {
        this.provider = provider;
    }

    protected String provider() {
        return provider;
    }
}

class CardPayment extends BasePayment implements Refundable {
    CardPayment() {
        super("card");
    }

    @Override
    public void pay(int amount) { ... }

    @Override
    public void refund(int amount) { ... }
}
```

Không có cơ chế nào trong ví dụ “thay thế” cơ chế còn lại. Mỗi cơ chế chịu một trách nhiệm khác nhau.

## <a id="abstraction-design-checklist">Danh sách kiểm tra khi thiết kế</a>

Trước khi chọn `abstract class` hoặc `interface`, hãy kiểm tra:

1. Bên gọi cần biết **hợp đồng** hay cần phụ thuộc vào **phần triển khai cụ thể**?
2. Các kiểu con có thật sự chia sẻ trạng thái/bất biến/quy tắc khởi tạo không?
3. Quan hệ có phải một họ lớp tự nhiên hay chỉ là một vai trò/khả năng cắt ngang nhiều họ lớp?
4. Một lớp có cần kết hợp nhiều vai trò độc lập không?
5. Nếu interface có `default`, liệu hành vi mặc định có thật sự đúng cho mọi cách triển khai hợp lệ không?
6. Nếu nhiều interface được kết hợp, có nguy cơ xung đột chữ ký phương thức hoặc hành vi mặc định không?
7. Nếu cần gọi `InterfaceName.super`, interface được gọi có phải interface cha trực tiếp phù hợp với cú pháp Java không?

Danh sách kiểm tra này giúp tránh hai lỗi phổ biến: dùng lớp trừu tượng chỉ để “tái sử dụng vài dòng mã”, hoặc tạo interface chỉ vì muốn có thêm một lớp trừu tượng mà không có hợp đồng rõ ràng.

## <a id="abstraction-final-mental-model">Mô hình tư duy cần giữ lại</a>

```text
abstract class
→ họ lớp gần nhau
→ trạng thái + khởi tạo + phần triển khai một phần
→ chỉ một lớp cha

interface
→ hợp đồng / vai trò / khả năng
→ nhiều interface trên một lớp
→ có thể tạo hệ phân cấp hợp đồng
→ default/static/private có vai trò riêng

khi kết hợp
→ interface định nghĩa điều bên sử dụng được quyền kỳ vọng
→ abstract class có thể cung cấp phần triển khai chung cho một nhánh
→ lớp cụ thể hoàn thiện hành vi
→ quy tắc xung đột bảo đảm việc chọn hành vi kế thừa không mơ hồ
```

Các cơ chế này hỗ trợ đa hình (polymorphism), nhưng chúng không phải toàn bộ OOP. OOP vẫn là bối cảnh rộng hơn về mô hình đối tượng, đóng gói, kế thừa, đa hình và thiết kế quan hệ giữa các kiểu.
