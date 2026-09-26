# Enum

`enum` không chỉ là “một nhóm số có tên”. Mỗi enum constant là **một instance của enum type**, có thể có field, constructor, method và hành vi riêng.

## <a id="enum-type-model">Enum Constant là Instance</a>

```java
enum AccountStatus {
    ACTIVE, FROZEN, CLOSED
}
```

`AccountStatus.ACTIVE` là một object singleton theo hợp đồng của enum, không phải bí danh của một số nguyên.

Vì mỗi constant có identity ổn định, so sánh enum bằng `==` là phù hợp và thường được khuyến nghị.

## <a id="enum-fields-constructors">Thành phần trong Enum</a>

Enum có thể giữ dữ liệu và hành vi. Ví dụ một loại tài khoản có thể mang mã bên ngoài ổn định:

```java
enum AccountTier {
    STANDARD("STD"),
    PREMIUM("PRM");

    private final String code;

    AccountTier(String code) {
        this.code = code;
    }

    String code() {
        return code;
    }
}
```

Enum constructor không được gọi trực tiếp từ mã ứng dụng; nó phục vụ việc khởi tạo các constant đã khai báo.

## <a id="enum-interface">Enum Implement Interface</a>

Enum có thể `implements` interface, giúp một tập constant đóng vai trò cách triển khai có hợp đồng rõ ràng.

```java
interface FeePolicy {
    int feeFor(int amount);
}

enum FeeTier implements FeePolicy {
    STANDARD {
        public int feeFor(int amount) { return amount / 100; }
    },
    PREMIUM {
        public int feeFor(int amount) { return 0; }
    }
}
```

Điều này thường rõ hơn switch lớn khi hành vi thật sự thuộc từng constant.

## <a id="enum-constant-specific">Behavior riêng theo Constant</a>

Mỗi constant có thể cung cấp cách triển khai riêng cho abstract/overridable method của enum.

Trong ví dụ `FeeTier`, khi gọi `feeFor(...)`, Java chạy cách triển khai của constant đang được sử dụng. Đây là một dạng hành vi đa hình (polymorphic behavior) bên trong một tập instance đóng và đã biết trước.

## <a id="enum-values-valueof">values, valueOf, name và ordinal</a>

`values()` trả các constant theo thứ tự khai báo; `valueOf(String)` tìm theo đúng tên constant đã khai báo.

```java
AccountStatus[] all = AccountStatus.values();
AccountStatus status = AccountStatus.valueOf("ACTIVE");
```

`name()` là identifier đã khai báo. `ordinal()` chỉ là vị trí khai báo và **không nên dùng làm mã nghiệp vụ ổn định hoặc giá trị lưu trong database**, vì đổi thứ tự constant sẽ làm `ordinal()` thay đổi.

Nếu cần một mã bên ngoài ổn định, hãy định nghĩa field riêng.

Chương tiếp theo chuyển từ “object là gì” sang “sao chép object nghĩa là sao chép reference hay sao chép trạng thái?”.
