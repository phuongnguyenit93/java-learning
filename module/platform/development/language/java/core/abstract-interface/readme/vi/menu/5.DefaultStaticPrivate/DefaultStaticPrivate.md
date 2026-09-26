# Default, Static và Private Method trong Interface

Interface ban đầu chủ yếu được xem như nơi khai báo abstract method. Java hiện đại cho phép interface chứa thêm một số loại method để hỗ trợ **tiến hóa API** và dùng lại hành vi mà không biến interface thành một abstract class.

Ba loại method trong chương này giải quyết ba nhu cầu khác nhau:

```text
default
→ cung cấp hành vi instance mặc định cho class triển khai

static
→ đặt thao tác gắn với hợp đồng trên chính interface type

private / private static
→ dùng lại logic nội bộ mà không mở rộng hợp đồng công khai
```

## <a id="default-method">Default Method</a>

`default` method là instance method có phần thân ngay trong interface:

```java
interface PaymentMethod {
    void pay(int amount);

    default String label() {
        return "payment";
    }
}
```

Cách triển khai cũ có thể kế thừa `label()` mà không cần ngay lập tức thêm phần thân mới.

Default method vẫn là hành vi của instance và có thể bị override. Khi nhiều default method cạnh tranh, Java áp dụng các quy tắc giải quyết cụ thể thay vì tự chọn ngẫu nhiên.

### VÌ SAO

Nếu interface công khai đã có nhiều class triển khai, thêm một abstract method mới sẽ buộc các class đó phải bổ sung method. Một `default` method cho phép interface cung cấp hành vi hợp lý ngay tại hợp đồng khi hành vi đó thật sự có một mặc định chung.

## <a id="static-interface-method">Static Method trong Interface</a>

Static method thuộc về chính interface type. Nó hữu ích khi thao tác gắn chặt với hợp đồng nhưng không cần trạng thái của một object cụ thể:

```java
interface PaymentMethod {
    void pay(int amount);

    static void validate(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}

PaymentMethod.validate(100);
```

Nó **không được kế thừa thành instance method** của class implementing interface.

Static interface method phù hợp cho factory, validator hoặc helper gắn chặt với hợp đồng nhưng không cần trạng thái của object. Đặt thao tác ở interface giúp API liên quan đến `PaymentMethod` nằm gần chính hợp đồng thay vì bị phân tán sang một utility class không có ngữ nghĩa rõ ràng.

## <a id="private-interface-method">Private Method trong Interface</a>

Private method cho phép các method trong interface dùng lại chi tiết triển khai mà không biến helper đó thành một phần của hợp đồng công khai.

```java
interface Formatter {
    default String upper(String value) {
        return normalize(value).toUpperCase();
    }

    private String normalize(String value) {
        return value.trim();
    }

    static String normalizeKey(String value) {
        return normalizeStatic(value).toLowerCase();
    }

    private static String normalizeStatic(String value) {
        return value.trim();
    }
}
```

`private String normalize(...)` là **private instance method**: nó dùng được trong ngữ cảnh instance/default của interface. `private static String normalizeStatic(...)` là **private static method**: nó dùng được trong ngữ cảnh static vì không cần instance.

Class triển khai interface không thể gọi hoặc override các private helper này; chúng chỉ là chi tiết triển khai nội bộ.

## <a id="default-method-evolution">Tiến hóa Interface</a>

Một lý do quan trọng khiến default method tồn tại là **interface evolution**: thư viện có thể thêm một hành vi có cách triển khai mặc định mà không buộc mọi class triển khai cũ phải sửa mã nguồn ngay lập tức.

Nhưng default method không biến mọi thay đổi thành tương thích:

- thêm abstract method mới vẫn có thể làm các class triển khai cũ không còn compile;
- thêm default method có thể tạo xung đột với interface khác;
- thay đổi ý nghĩa hợp đồng vẫn có thể làm code phía sử dụng hoạt động sai.

Chương cuối dùng chính các default method để xem Java giải quyết xung đột khi kế thừa nhiều interface như thế nào.
