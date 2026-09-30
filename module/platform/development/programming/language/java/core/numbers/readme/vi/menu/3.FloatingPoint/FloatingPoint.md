# Số dấu phẩy động và sai số biểu diễn

`float` và `double` được thiết kế cho **phạm vi rộng + tính toán số thực hiệu quả**. Đổi lại, chúng dùng biểu diễn dấu phẩy động nhị phân nên nhiều giá trị thập phân quen thuộc không có biểu diễn nhị phân hữu hạn.

## <a id="binary-floating-point">Biểu diễn dấu phẩy động nhị phân</a>

### KHÁI NIỆM

Theo IEEE 754, floating-point có thể hình dung bằng ba phần:

```text
sign
 + số mũ (exponent)
 + phần định trị (significand)
```

`float` dùng binary32; `double` dùng binary64. Trong mã ứng dụng Java, `double` thường là lựa chọn mặc định khi cần số dấu phẩy động vì có độ chính xác/phạm vi tốt hơn `float`.

### VÌ SAO 0.1 không “vừa” trong hệ nhị phân?

Một số phân số hữu hạn trong hệ 10 lại lặp vô hạn trong hệ 2.

```text
0.5 thập phân
→ 0.1 nhị phân
→ biểu diễn hữu hạn

0.1 thập phân
→ phân số nhị phân lặp
→ chỉ lưu được giá trị gần nhất
```

Vì vậy:

```java
double x = 0.1 + 0.2;
System.out.println(x); // 0.30000000000000004
```

Đây không phải lỗi riêng của Java mà là hệ quả của cách biểu diễn dấu phẩy động nhị phân với số bit hữu hạn.

## <a id="precision-rounding-error">Sai số biểu diễn và làm tròn</a>

### CƠ CHẾ — sai số xuất hiện như thế nào?

Với kết quả hữu hạn thông thường nằm trong phạm vi biểu diễn được, phép toán thường phải đưa kết quả toán học về giá trị dấu phẩy động gần nhất theo quy tắc làm tròn của IEEE 754/Java.

```text
kết quả toán học chính xác
        ↓
giá trị nhị phân biểu diễn được gần nhất
        ↓
phép toán tiếp theo
        ↓
làm tròn lần nữa
```

Nhưng không phải mọi kết quả đều chỉ trở thành một giá trị xấp xỉ hữu hạn:

```text
mức độ lớn quá cao
→ tràn số
→ có thể thành +Infinity / -Infinity

độ lớn quá nhỏ
→ tràn dưới (underflow)
→ có thể thành giá trị dưới chuẩn (subnormal) hoặc số 0 có dấu
```

Sai số nhỏ có thể tích lũy:

```java
double total = 0.0;
for (int i = 0; i < 10; i++) {
    total += 0.1;
}
System.out.println(total);
```

Phép toán dấu phẩy động cũng không phải lúc nào có tính kết hợp theo trực giác toán học:

```java
double a = 1e16;
double b = -1e16;
double c = 1.0;

System.out.println((a + b) + c); // có thể khác
System.out.println(a + (b + c)); // với cách nhóm khác
```

### Khi nào phù hợp?

`double` thường phù hợp cho:

- cảm biến/đo lường;
- thống kê;
- đồ họa;
- tính toán khoa học/kỹ thuật;
- mô phỏng;
- các bài toán có mức sai số chấp nhận rõ ràng.

Nó thường không phù hợp nếu yêu cầu đòi hỏi **ngữ nghĩa thập phân chính xác**, ví dụ nhiều quy trình tính tiền/thuế/tỷ lệ.

## <a id="nan-infinity-negative-zero">NaN, vô cực và số 0 âm</a>

IEEE 754 có các giá trị đặc biệt:

```text
NaN
→ kết quả không phải giá trị số thông thường

Infinity / -Infinity
→ tràn số hoặc một số trường hợp chia cho 0

+0.0 / -0.0
→ hai cách biểu diễn số 0 có dấu khác nhau
```

Ví dụ:

```java
double nan = 0.0 / 0.0;
double inf = 1.0 / 0.0;
double negInf = -1.0 / 0.0;

System.out.println(Double.isNaN(nan));      // true
System.out.println(Double.isInfinite(inf)); // true
```

### Sự lan truyền của NaN

```java
double result = Double.NaN + 10;
System.out.println(result); // NaN
```

`NaN == NaN` là `false`. Vì vậy khi cần kiểm tra NaN, dùng:

```java
Double.isNaN(value)
```

### Số 0 có dấu

```java
System.out.println(0.0 == -0.0); // true
```

nhưng dấu vẫn có thể ảnh hưởng đến phép toán:

```java
System.out.println(1.0 / 0.0);  // Infinity
System.out.println(1.0 / -0.0); // -Infinity
```

## <a id="floating-point-comparison">So sánh số dấu phẩy động</a>

`==` chỉ nên dùng khi yêu cầu thực sự là **hai giá trị được biểu diễn phải bằng nhau chính xác**.

Với phép tính gần đúng, thường cần một ngưỡng sai số chấp nhận:

```java
double actual = 0.1 + 0.2;
double expected = 0.3;
double epsilon = 1e-9;

boolean close = Math.abs(actual - expected) <= epsilon;
```

Nhưng một epsilon tuyệt đối không phù hợp cho mọi độ lớn:

```text
giá trị quanh 0.000001
và
giá trị quanh 1_000_000_000

→ có thể cần chiến lược sai số chấp nhận khác nhau
```

Trong mã thực tế, mức sai số chấp nhận nên đến từ yêu cầu bài toán/ngân sách sai số, không phải một “epsilon thần kỳ” tùy ý.

Ngoài ra Java có:

```java
Double.compare(a, b);
Double.isFinite(value);
Double.isNaN(value);
```

`Double.compare` không có ngữ nghĩa giống hệt toán tử `==`: nó cung cấp thứ tự toàn phần (total ordering) hữu ích cho việc sắp xếp, trong đó NaN và số 0 có dấu được xử lý theo quy tắc của API. Vì vậy hãy chọn API theo câu hỏi mà bài toán đang cần trả lời.

Nếu vấn đề là **phạm vi số nguyên**, chương sau dùng `BigInteger`. Nếu vấn đề là **ngữ nghĩa thập phân chính xác**, ta sẽ tới `BigDecimal`.
