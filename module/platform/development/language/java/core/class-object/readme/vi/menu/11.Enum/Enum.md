# Enum

`enum` không chỉ là “mấy số constant có tên”. Mỗi enum constant là **một instance của enum type**, có thể có field, constructor, method và hành vi riêng.

## <a id="enum-type-model">Enum Constant là Instance</a>

```java
enum Status {
    NEW, PAID, CANCELLED
}
```

`Status.NEW` là một object singleton theo enum hợp đồng, không phải integer alias.

Vì mỗi constant có identity ổn định, so sánh enum bằng `==` là phù hợp và thường được khuyến nghị.

## <a id="enum-fields-constructors">Thành phần trong Enum</a>

Enum có thể giữ dữ liệu và hành vi:

```java
enum Currency {
    USD(2), JPY(0);

    private final int fractionDigits;

    Currency(int fractionDigits) {
        this.fractionDigits = fractionDigits;
    }
}
```

Enum constructor không được gọi trực tiếp từ mã ứng dụng; nó phục vụ construction của các constant đã khai báo.

## <a id="enum-interface">Enum Implement Interface</a>

Enum có thể `implements` interface, giúp một tập constant đóng vai trò cách triển khai có hợp đồng rõ ràng.

```java
enum Operation implements IntBinaryOperator { ... }
```

Điều này thường rõ hơn switch lớn khi hành vi thật sự thuộc từng constant.

## <a id="enum-constant-specific">Behavior riêng theo Constant</a>

Mỗi constant có thể cung cấp cách triển khai riêng cho abstract/overridable method của enum.

```java
PLUS {
    int apply(int a, int b) { return a + b; }
}
```

Đây là một dạng polymorphic hành vi trong một tập type đóng.

## <a id="enum-values-valueof">values, valueOf, name và ordinal</a>

`values()` trả các constant theo declaration order; `valueOf(String)` lookup theo exact name.

`name()` là identifier khai báo. `ordinal()` chỉ là vị trí declaration và **không nên dùng làm persistent business mã/database value**, vì reorder constant sẽ đổi ordinal.

Nếu cần external mã ổn định, hãy định nghĩa field riêng.

chương tiếp theo chuyển từ “object là gì” sang “copy object nghĩa là copy reference hay copy trạng thái?”.
