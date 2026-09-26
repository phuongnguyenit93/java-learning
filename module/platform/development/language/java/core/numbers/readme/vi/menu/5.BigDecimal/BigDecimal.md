# BigDecimal

`BigDecimal` được dùng khi domain cần **decimal ngữ nghĩa rõ ràng**, ví dụ tiền, lãi suất hoặc các phép tính mà cách biểu diễn `0.1` phải mang đúng ý nghĩa thập phân.

## <a id="big-decimal-model">Mô hình BigDecimal</a>

Có thể hiểu một `BigDecimal` bằng hai thành phần chính:

```text
unscaled value
        +
scale
```

Ví dụ `123.45` có thể được hình dung như unscaled value `12345` với scale `2`.

Scale là một phần của cách biểu diễn, vì vậy `1.0` và `1.00` có thể có cùng numerical value nhưng cách biểu diễn khác nhau.

## <a id="big-decimal-construction">Khởi tạo BigDecimal</a>

Khi decimal literal phải chính xác, ưu tiên:

```java
new BigDecimal("0.1")
```

hoặc:

```java
BigDecimal.valueOf(0.1)
```

Tránh `new BigDecimal(0.1)` nếu bạn mong đúng decimal `0.1`, vì constructor đó nhận **binary floating-point value đã gần đúng** rồi chuyển cách biểu diễn gần đúng ấy sang BigDecimal.

## <a id="big-decimal-arithmetic">Arithmetic và Division</a>

`BigDecimal` cũng immutable:

```java
amount = amount.add(fee);
```

Một số phép chia decimal không kết thúc hữu hạn:

```java
BigDecimal.ONE.divide(new BigDecimal("3"));
```

nếu không cung cấp scale/rounding chính sách phù hợp có thể throw `ArithmeticException`.

Đây là điểm quan trọng: BigDecimal không “tự đoán” cách làm tròn cho domain. chương tiếp theo phân biệt **precision và scale**, nền tảng để hiểu rounding.
