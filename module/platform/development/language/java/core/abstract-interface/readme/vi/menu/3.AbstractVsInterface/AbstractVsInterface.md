# Abstract Class và Interface

Không có quy tắc “interface luôn tốt hơn abstract class” hoặc ngược lại. Hai cơ chế giải quyết những nhu cầu hơi khác nhau. Cách chọn tốt nhất bắt đầu từ **mối quan hệ thiết kế** chứ không phải từ sở thích cú pháp.

Hãy quay lại cùng một bài toán thanh toán:

```text
PaymentMethod
→ mọi cách thanh toán đều phải thực hiện pay(...)

BasePayment
→ một số cách thanh toán cùng chia sẻ provider, validation và logic nền

Refundable
→ chỉ những cách thanh toán hỗ trợ hoàn tiền mới cần khả năng này
```

Ba nhu cầu trên không nên bị ép vào một cơ chế duy nhất. Hợp đồng dành cho bên sử dụng, phần triển khai chung và khả năng bổ sung là ba trách nhiệm khác nhau.

## <a id="abstract-vs-interface-state">Trạng thái và Constructor</a>

Abstract class có thể sở hữu instance trạng thái và constructor:

```java
abstract class BasePayment {
    private final String provider;

    protected BasePayment(String provider) {
        this.provider = provider;
    }
}
```

Interface không có instance field hay constructor riêng. Field của interface là constant `public static final`.

Vì vậy nếu nhiều kiểu con thực sự chia sẻ một phần trạng thái với ràng buộc bất biến (invariant) và quy tắc khởi tạo chung, abstract class có thể là lựa chọn tự nhiên hơn.

## <a id="abstract-vs-interface-inheritance">Một Class Cha, Nhiều Interface</a>

Java chỉ cho một class `extends` một class:

```text
class inheritance
→ chỉ một parent class
```

nhưng một class có thể `implements` nhiều interface:

```text
interface inheritance / implementation
→ có thể kết hợp nhiều vai trò/khả năng
```

Điều này giúp interface phù hợp cho các vai trò cắt ngang nhiều hệ phân cấp khác nhau, ví dụ `Comparable`, `AutoCloseable`, `Serializable` hoặc một khả năng trong miền nghiệp vụ như `Refundable`.

## <a id="selection-guidance">Chọn Abstract Class hay Interface?</a>

Một nguyên tắc kinh nghiệm hữu ích:

| Nhu cầu | Thường phù hợp hơn |
| --- | --- |
| chia sẻ trạng thái và constructor | abstract class |
| chia sẻ cách triển khai nền tảng gắn chặt với một cây kế thừa | abstract class |
| mô tả vai trò/khả năng | interface |
| nhiều class không liên quan cùng thực hiện một hợp đồng | interface |
| một class cần nhiều role | interface |
| cần cả hợp đồng linh hoạt và phần triển khai dùng chung | interface + abstract base class |

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

Interface giữ hợp đồng mà bên sử dụng phụ thuộc; abstract class cung cấp phần triển khai chung cho một nhánh cách triển khai cụ thể. Một class khác vẫn có thể triển khai `PaymentMethod` mà không cần kế thừa `BasePayment`.

Chương tiếp theo mở rộng ý tưởng này: **bản thân các interface có thể kế thừa và ghép hợp đồng của nhau như thế nào?**
