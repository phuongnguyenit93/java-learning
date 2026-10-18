# Kiểu liệt kê (enum)

`enum` dùng để mô hình một **tập giá trị hữu hạn có kiểu rõ ràng**, ví dụ trạng thái tài khoản chỉ có thể là `ACTIVE`, `FROZEN` hoặc `CLOSED`. Cách này tránh việc dùng các hằng rời rạc dễ trộn lẫn hoặc nhận giá trị không hợp lệ. Mỗi hằng `enum` là **một đối tượng của chính kiểu enum**, có thể có trường, hàm khởi tạo, phương thức và hành vi riêng.

## <a id="enum-type-model">Hằng enum là đối tượng</a>

```java
enum AccountStatus {
    ACTIVE, FROZEN, CLOSED
}
```

`AccountStatus.ACTIVE` là một đối tượng duy nhất đại diện cho hằng đó theo hợp đồng của `enum`, không phải bí danh của một số nguyên.

Vì mỗi hằng có danh tính ổn định, so sánh enum bằng `==` là phù hợp và thường được khuyến nghị.

## <a id="enum-fields-constructors">Trường, hàm khởi tạo và phương thức trong enum</a>

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

Hàm khởi tạo của enum không được gọi trực tiếp từ mã ứng dụng; nó phục vụ việc khởi tạo các hằng đã khai báo.

## <a id="enum-interface">Enum triển khai giao diện</a>

Enum có thể `implements` giao diện, giúp một tập hằng đóng vai trò các cách triển khai có hợp đồng rõ ràng. Phần này chỉ cần nhận biết khả năng đó; thiết kế giao diện được học sâu ở mô-đun Abstract Interface.

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

Điều này thường rõ hơn một `switch` lớn khi hành vi thật sự thuộc từng hằng.

## <a id="enum-constant-specific">Hành vi riêng theo từng hằng</a>

Mỗi hằng có thể cung cấp cách triển khai riêng cho phương thức abstract hoặc phương thức có thể được ghi đè của enum.

Trong ví dụ `FeeTier`, khi gọi `feeFor(...)`, Java chạy cách triển khai của hằng đang được sử dụng. Đây là một dạng hành vi đa hình bên trong một tập đối tượng đóng và đã biết trước; cơ chế đa hình được học sâu hơn ở OOP.

## <a id="enum-values-valueof">values, valueOf, name và ordinal</a>

`values()` trả các hằng theo thứ tự khai báo; `valueOf(String)` tìm theo đúng tên hằng đã khai báo.

```java
AccountStatus[] all = AccountStatus.values();
AccountStatus status = AccountStatus.valueOf("ACTIVE");
```

`name()` là tên định danh đã khai báo. `ordinal()` chỉ là vị trí khai báo và **không nên dùng làm mã nghiệp vụ ổn định hoặc giá trị lưu trong cơ sở dữ liệu**, vì đổi thứ tự hằng sẽ làm `ordinal()` thay đổi.

Nếu cần một mã bên ngoài ổn định, hãy định nghĩa trường riêng.

Chương tiếp theo chuyển từ “đối tượng là gì” sang “sao chép đối tượng nghĩa là sao chép tham chiếu hay sao chép trạng thái?”.
