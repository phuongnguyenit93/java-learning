# Chọn lớp trừu tượng hay interface?

Không có quy tắc “interface luôn tốt hơn abstract class” hoặc ngược lại. Hai cơ chế giải quyết những nhu cầu có phần giao nhau nhưng không giống nhau. Việc lựa chọn nên bắt đầu từ **mối quan hệ cần mô hình hóa**, không phải từ sở thích cú pháp.

Hãy quay lại bài toán thanh toán:

```text
PaymentMethod
→ mọi cách thanh toán đều phải thực hiện pay(...)

BasePayment
→ một số cách thanh toán cùng chia sẻ trạng thái `provider`, kiểm tra hợp lệ và hành vi nền

Refundable
→ chỉ những cách thanh toán hỗ trợ hoàn tiền mới cần khả năng này
```

Ba nhu cầu này không nên bị ép vào một cơ chế duy nhất. Hợp đồng cho bên sử dụng, phần triển khai dùng chung và khả năng bổ sung là ba trách nhiệm khác nhau.

## <a id="abstract-vs-interface-state">Trạng thái và hàm khởi tạo</a>

Lớp trừu tượng có thể sở hữu trạng thái của đối tượng và định nghĩa hàm khởi tạo để thiết lập trạng thái chung:

```java
abstract class BasePayment {
    private final String provider;

    protected BasePayment(String provider) {
        this.provider = provider;
    }
}
```

Interface không có trường riêng cho từng đối tượng hay hàm khởi tạo. Mọi trường khai báo trong interface đều là `public static final`, nên chúng thuộc về chính interface thay vì tạo trạng thái riêng cho từng đối tượng.

Vì vậy nếu nhiều lớp con thật sự chia sẻ trạng thái, bất biến (invariant) và quy tắc khởi tạo chung, lớp trừu tượng thường là lựa chọn tự nhiên hơn.

## <a id="abstract-vs-interface-inheritance">Một lớp cha, nhiều interface</a>

Java chỉ cho một lớp `extends` một lớp:

```text
kế thừa lớp
→ chỉ một lớp cha trực tiếp
```

nhưng một lớp có thể `implements` nhiều interface:

```text
triển khai interface
→ có thể kết hợp nhiều vai trò/khả năng độc lập
```

Điều này giúp interface phù hợp cho các vai trò xuất hiện ở nhiều hệ phân cấp khác nhau, ví dụ `Comparable`, `AutoCloseable`, `Serializable` hoặc một khả năng trong miền nghiệp vụ như `Refundable`.

## <a id="selection-guidance">Tiêu chí lựa chọn</a>

Một nguyên tắc kinh nghiệm hữu ích:

| Nhu cầu | Thường phù hợp hơn |
| --- | --- |
| giữ trạng thái và quy tắc khởi tạo dùng chung | abstract class |
| cung cấp phần triển khai nền tảng gắn với một họ lớp | abstract class |
| mô tả vai trò/khả năng | interface |
| nhiều lớp không liên quan cùng thực hiện một hợp đồng | interface |
| một lớp cần đảm nhận nhiều vai trò | interface |
| cần cả hợp đồng linh hoạt và phần triển khai dùng chung | interface + lớp cha trừu tượng |

Hai cơ chế hoàn toàn có thể dùng cùng nhau:

```java
interface PaymentMethod {
    void pay(int amount);
}

abstract class BasePayment implements PaymentMethod {
    private final String provider;

    protected BasePayment(String provider) {
        this.provider = provider;
    }
}

class CardPayment extends BasePayment implements Refundable {
    ...
}
```

Interface giữ hợp đồng mà bên sử dụng phụ thuộc; lớp trừu tượng cung cấp phần triển khai chung cho một nhánh cụ thể. Một lớp khác vẫn có thể triển khai `PaymentMethod` mà không cần kế thừa `BasePayment`.

Sau khi biết cách chọn hai cơ chế cơ bản, bước tiếp theo là xem **bản thân các interface có thể kế thừa và kết hợp hợp đồng của nhau như thế nào**.
