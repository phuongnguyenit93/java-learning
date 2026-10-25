# Số thập phân chính xác với BigDecimal

`BigDecimal` phù hợp khi bài toán cần **ngữ nghĩa thập phân rõ ràng**, ví dụ tiền, thuế, tỷ lệ hoặc các phép tính mà số `0.1` phải mang đúng ý nghĩa thập phân.

Nó không đơn giản là “double nhưng chính xác hơn”. BigDecimal có mô hình và các quy tắc sử dụng riêng.

## <a id="big-decimal-model">Mô hình BigDecimal</a>

Có thể hiểu một `BigDecimal` bằng:

```text
 giá trị chưa áp dụng scale (BigInteger có độ chính xác tùy ý)
      ×
10^(-scale)

scale
→ int 32-bit
```

Ví dụ:

```text
123.45

giá trị chưa áp dụng scale = 12345
scale          = 2
```

`1.0` và `1.00` có cùng giá trị số nhưng scale khác nhau. Scale là một phần của cách biểu diễn và sẽ quan trọng ở chương so sánh.

Vì vậy “độ chính xác tùy ý” (arbitrary precision) của BigDecimal chủ yếu nói về **số nguyên chưa áp dụng scale / số lượng chữ số**, không có nghĩa scale cũng là một số nguyên không giới hạn.

### VÌ SAO BigDecimal thuộc Numbers?

Với tiền/tỷ lệ/thuế, câu hỏi không chỉ là:

> giá trị gần đúng có đủ tốt không?

mà còn là:

> bài toán muốn giữ bao nhiêu chữ số thập phân, khi nào làm tròn, làm tròn theo chế độ nào, phép so sánh bằng nhau có xét scale không?

BigDecimal buộc các chính sách này phải được thể hiện rõ thay vì giấu chúng trong giá trị xấp xỉ của số dấu phẩy động.

## <a id="big-decimal-construction">Khởi tạo BigDecimal</a>

### Tạo từ String

Khi có literal/chuỗi thập phân chính xác:

```java
BigDecimal rate = new BigDecimal("0.1");
```

### `valueOf(double)`

```java
BigDecimal value = BigDecimal.valueOf(0.1);
```

`valueOf(double)` dùng dạng chuỗi thập phân của `double`, nên với literal như `0.1` nó thường cho kết quả đúng với ý nghĩa thập phân mà người viết mã mong đợi.

### Lỗi thường gặp: `new BigDecimal(double)`

```java
BigDecimal bad = new BigDecimal(0.1);
```

Constructor nhận **giá trị dấu phẩy động nhị phân thực tế**, nên kết quả có thể giống:

```text
0.10000000000000000555...
```

### Quan trọng: BigDecimal không thể khôi phục ý nghĩa đã mất

```java
double x = 0.1 + 0.2;
BigDecimal value = BigDecimal.valueOf(x);
```

Ở đây phép toán dấu phẩy động đã xảy ra trước:

```text
0.1 + 0.2
→ giá trị xấp xỉ dấu phẩy động nhị phân
→ 0.30000000000000004
→ BigDecimal.valueOf(...)
```

BigDecimal không biết người viết mã “muốn” giá trị thập phân `0.3`.

Nếu ngữ nghĩa thập phân chính xác là yêu cầu, hãy giữ toàn bộ chuỗi tính toán ở BigDecimal ngay từ đầu:

```java
BigDecimal result =
        new BigDecimal("0.1")
                .add(new BigDecimal("0.2"));
```

## <a id="big-decimal-arithmetic">Phép toán và phép chia</a>

BigDecimal là bất biến:

```java
BigDecimal amount = new BigDecimal("10.00");
BigDecimal fee = new BigDecimal("1.25");

amount.add(fee); // amount không đổi
amount = amount.add(fee);
```

Các phép toán chính:

```java
amount.add(fee);
amount.subtract(discount);
amount.multiply(rate);
amount.divide(divisor);
```

### Phép toán cũng mang theo ngữ nghĩa của scale

Không chỉ giá trị số thay đổi; scale ưu tiên của kết quả còn phụ thuộc phép toán. Ví dụ, khi không dùng `MathContext`:

```text
add/subtract
→ preferred scale thường là max(scale trái, scale phải)

multiply
→ preferred scale = scale trái + scale phải

phép chia chính xác
→ scale ưu tiên bắt đầu từ scale trái - scale phải,
  nhưng thương chính xác có thể cần scale lớn hơn
```

Vì vậy không nên đoán scale của kết quả chỉ bằng cách nhìn chuỗi đầu vào. Nếu bài toán yêu cầu scale đầu ra cố định, chính sách đó vẫn cần được áp dụng rõ ràng tại ranh giới thích hợp.

### Phép chia có thể cần chính sách làm tròn

```java
BigDecimal.ONE.divide(new BigDecimal("3"));
```

`1 / 3` không có biểu diễn thập phân hữu hạn, nên phép chia chính xác không thể hoàn thành và có thể ném `ArithmeticException`.

Bạn phải chỉ định chính sách:

```java
BigDecimal result =
        BigDecimal.ONE.divide(
                new BigDecimal("3"),
                4,
                RoundingMode.HALF_UP
        );
```

### BigDecimal không tự quyết định quy tắc nghiệp vụ

Nó không biết:

- tiền tệ cần scale 2 hay 0;
- thuế phải làm tròn từng dòng hay tổng cuối;
- tỷ lệ trung gian giữ bao nhiêu độ chính xác;
- quy định pháp lý dùng HALF_UP hay chế độ khác.

Đây là chủ đích thiết kế, không phải thiếu sót:

```text
BigDecimal
→ cung cấp phép toán thập phân

quy tắc nghiệp vụ
→ quyết định độ chính xác / scale / ranh giới làm tròn
```

Chương tiếp theo phân biệt **độ chính xác (precision) và scale**, hai khái niệm rất dễ bị dùng lẫn.
