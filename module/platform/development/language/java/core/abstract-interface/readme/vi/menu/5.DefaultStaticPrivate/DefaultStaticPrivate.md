# Default, Static và Private Method trong Interface

Interface ban đầu chủ yếu được xem như nơi khai báo abstract method. Java hiện đại cho phép interface chứa thêm một số loại method để hỗ trợ **tiến hóa API** và dùng lại hành vi mà không biến interface thành một abstract class.

## <a id="default-method">Default Method</a>

`default` method là instance method có body ngay trong interface:

```java
interface PaymentMethod {
    void pay(int amount);

    default String label() {
        return "payment";
    }
}
```

cách triển khai cũ có thể kế thừa `label()` mà không cần ngay lập tức thêm body mới.

Default method vẫn là instance hành vi và có thể bị override. Khi nhiều default method cạnh tranh, Java áp dụng các quy tắc resolution cụ thể thay vì tự chọn ngẫu nhiên.

## <a id="static-interface-method">Static Method trong Interface</a>

Static method thuộc về chính interface type:

```java
PaymentMethod.validate(amount);
```

Nó **không được kế thừa thành instance method** của class implementing interface.

Static interface method phù hợp cho factory, validator hoặc helper gắn chặt với hợp đồng nhưng không cần trạng thái của object.

## <a id="private-interface-method">Private Method trong Interface</a>

Private method cho phép nhiều default/static method dùng chung cách triển khai detail mà không mở rộng public hợp đồng.

```java
interface Formatter {
    default String upper(String value) {
        return normalize(value).toUpperCase();
    }

    private String normalize(String value) {
        return value.trim();
    }
}
```

Class triển khai interface không thể gọi hoặc override `normalize(...)`; đây chỉ là helper nội bộ của interface.

## <a id="default-method-evolution">Tiến hóa Interface</a>

Một lý do quan trọng khiến default method tồn tại là **interface evolution**: thư viện có thể thêm một hành vi có cách triển khai mặc định mà không buộc mọi cách triển khai cũ phải thay đổi source ngay lập tức.

Nhưng default method không biến mọi thay đổi thành tương thích:

- thêm abstract method mới vẫn có thể làm source break;
- thêm default method có thể tạo xung đột với interface khác;
- thay đổi ý nghĩa hợp đồng vẫn có thể phá hành vi của client.

chương cuối dùng chính các default method để xem Java giải quyết multiple-interface conflict như thế nào.
