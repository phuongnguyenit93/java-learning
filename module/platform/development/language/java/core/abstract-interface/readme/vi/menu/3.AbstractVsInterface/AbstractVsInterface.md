# Abstract Class và Interface

Không có quy tắc “interface luôn tốt hơn abstract class” hoặc ngược lại. Hai cơ chế giải quyết những nhu cầu hơi khác nhau. Cách chọn tốt nhất bắt đầu từ **mối quan hệ thiết kế** chứ không phải từ sở thích cú pháp.

## <a id="abstract-vs-interface-state">Trạng thái và Constructor</a>

Abstract class có thể sở hữu instance trạng thái và constructor:

```java
abstract class PaymentBase {
    private final String provider;

    protected PaymentBase(String provider) {
        this.provider = provider;
    }
}
```

Interface không có instance field hay constructor riêng. Field của interface là constant `public static final`.

Vì vậy nếu nhiều subtype thực sự chia sẻ một phần trạng thái với invariant và quy tắc khởi tạo chung, abstract class có thể là lựa chọn tự nhiên hơn.

## <a id="abstract-vs-interface-inheritance">Một Class Cha, Nhiều Interface</a>

Java chỉ cho một class `extends` một class:

```text
class inheritance
→ chỉ một parent class
```

nhưng một class có thể `implements` nhiều interface:

```text
interface inheritance / implementation
→ có thể kết hợp nhiều capability
```

Điều này giúp interface phù hợp cho các vai trò cắt ngang nhiều hierarchy khác nhau, ví dụ `Comparable`, `AutoCloseable`, `Serializable` hoặc một capability domain như `Refundable`.

## <a id="selection-guidance">Chọn Abstract Class hay Interface?</a>

Một heuristic hữu ích:

| Nhu cầu | Thường phù hợp hơn |
| --- | --- |
| chia sẻ trạng thái và constructor | abstract class |
| chia sẻ cách triển khai nền tảng gắn chặt với hierarchy | abstract class |
| mô tả capability/role | interface |
| nhiều class không liên quan cùng thực hiện một hợp đồng | interface |
| một class cần nhiều role | interface |
| cần cả hợp đồng linh hoạt và partial cách triển khai | interface + abstract base class |

Hai cơ chế hoàn toàn có thể dùng cùng nhau:

```java
interface PaymentMethod { ... }

abstract class BasePayment implements PaymentMethod { ... }
```

Interface giữ hợp đồng mà consumer phụ thuộc; abstract class cung cấp phần triển khai chung cho một nhánh cách triển khai cụ thể.

chương tiếp theo mở rộng ý tưởng này: **bản thân các interface có thể kế thừa và ghép hợp đồng của nhau như thế nào?**
