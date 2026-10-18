# Phương thức default, static và private trong interface

Interface ban đầu chủ yếu được xem như nơi khai báo phương thức abstract. Java hiện đại cho phép interface chứa thêm một số loại phương thức có phần thân, nhưng **mỗi loại có vai trò khác nhau**.

```text
default
→ cung cấp hành vi mặc định của đối tượng có thể được kế thừa
→ hỗ trợ mở rộng hợp đồng tương thích khi có một mặc định hợp lý

static
→ thao tác thuộc chính kiểu interface
→ không trở thành phương thức của đối tượng được lớp triển khai kế thừa

private / private static
→ phương thức hỗ trợ nội bộ để các phương thức trong interface dùng lại phần triển khai
→ không mở rộng hợp đồng công khai
```

## <a id="default-method">Phương thức default</a>

Phương thức `default` là phương thức của đối tượng có phần thân ngay trong interface:

```java
interface PaymentMethod {
    void pay(int amount);

    default String label() {
        return "payment";
    }
}
```

Lớp triển khai có thể kế thừa `label()` mà không cần tự viết ngay một phần thân khác.

Phương thức `default` vẫn là hành vi của đối tượng và có thể bị ghi đè (override). Khi nhiều phương thức `default` cạnh tranh, Java dùng các quy tắc giải quyết cụ thể thay vì tự chọn ngẫu nhiên.

### VÌ SAO

Nếu một interface công khai đã có nhiều lớp triển khai, thêm một phương thức abstract mới sẽ buộc các lớp đó phải bổ sung phần triển khai. Khi thật sự tồn tại một hành vi mặc định hợp lý, phương thức `default` có thể giúp mở rộng interface mà không buộc mọi lớp triển khai cũ phải sửa mã nguồn ngay lập tức.

`default` không có nghĩa interface trở thành abstract class: interface vẫn không có trường riêng cho từng đối tượng hay hàm khởi tạo riêng để giữ trạng thái.

## <a id="static-interface-method">Phương thức static trong interface</a>

Phương thức `static` thuộc về chính kiểu interface. Nó hữu ích khi thao tác gắn chặt với hợp đồng nhưng không cần trạng thái của một đối tượng cụ thể:

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

Nó **không được kế thừa** bởi lớp triển khai hoặc interface con. Cách gọi đúng đi qua chính tên interface khai báo phương thức, ví dụ `PaymentMethod.validate(...)`; không gọi như phương thức của đối tượng và cũng không dựa vào tên interface con như thể phương thức đã được kế thừa.

Phương thức `static` trong interface phù hợp cho thao tác tạo đối tượng, kiểm tra hợp lệ hoặc hỗ trợ gắn chặt với hợp đồng nhưng độc lập với trạng thái của từng đối tượng. Tuy vậy, việc đặt phương thức ở interface vẫn cần có ngữ nghĩa rõ ràng; không nên biến interface thành nơi chứa các tiện ích không liên quan.

## <a id="private-interface-method">Phương thức private trong interface</a>

Phương thức `private` cho phép các phương thức bên trong interface dùng lại chi tiết triển khai mà không biến phần hỗ trợ đó thành một phần của hợp đồng công khai:

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

`private String normalize(...)` là phương thức `private` của đối tượng nên dùng được trong ngữ cảnh của phương thức đối tượng/`default` trong interface. `private static String normalizeStatic(...)` là phương thức `private static` nên dùng được từ ngữ cảnh `static` mà không cần đối tượng.

Lớp triển khai interface không thể gọi hoặc override các phương thức hỗ trợ `private` này; chúng là chi tiết triển khai nội bộ của interface.

## <a id="default-method-evolution">Mở rộng interface theo thời gian</a>

Một lý do quan trọng khiến phương thức `default` tồn tại là khả năng **mở rộng interface theo thời gian (interface evolution)**: thư viện có thể bổ sung một hành vi với phần triển khai mặc định mà không buộc mọi lớp triển khai cũ phải thêm mã nguồn ngay lập tức.

Phương thức `default` không biến mọi thay đổi thành tương thích:

- thêm phương thức abstract mới vẫn có thể làm lớp triển khai cũ không còn biên dịch được;
- thêm phương thức `default` có thể tạo xung đột với interface khác;
- thay đổi ý nghĩa hợp đồng vẫn có thể làm mã phía sử dụng hoạt động sai.

Interface con còn có thể **biến một phương thức `default` được kế thừa trở lại thành yêu cầu abstract** bằng cách khai báo lại mà không có phần thân:

```java
interface Base {
    default Number value() { return 0; }
}

interface Specific extends Base {
    @Override
    Integer value();
}
```

Chi tiết này trước hết là một phần của cơ chế `default`; nó cũng cho thấy interface con có thể tinh chỉnh hợp đồng thay vì bắt buộc giữ nguyên hành vi mặc định của interface cha.

Phần tiếp theo dùng chính phương thức `default` để xem Java giải quyết xung đột khi một lớp triển khai nhiều interface như thế nào.
