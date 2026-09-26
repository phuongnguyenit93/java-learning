# Mô hình số trong Java

Java không có một kiểu số duy nhất dùng tốt cho mọi bài toán. Mỗi cách biểu diễn đánh đổi giữa **phạm vi giá trị, độ chính xác, hiệu năng, kích thước bộ nhớ và cách làm tròn**.

Đây là mô hình tư duy quan trọng nhất của module:

```text
int / long
→ số nguyên nhanh, gọn, chính xác trong phạm vi cố định

float / double
→ phạm vi lớn, phù hợp tính toán khoa học/kỹ thuật,
  nhưng nhiều số thập phân chỉ được biểu diễn gần đúng

BigInteger
→ số nguyên không bị giới hạn bởi độ rộng cố định của primitive

BigDecimal
→ số thập phân với giá trị + scale rõ ràng,
  phù hợp khi độ chính xác thập phân và chính sách làm tròn quan trọng
```

Không có cách biểu diễn nào đồng thời cho ta phạm vi vô hạn, số thập phân chính xác tuyệt đối, tốc độ của primitive và mức sử dụng bộ nhớ nhỏ. Vì vậy học Numbers trước hết là học **chọn cách biểu diễn phù hợp với bài toán**.

Lộ trình:

```text
Chọn kiểu số nào?
Numeric Model
        ↓
Số nguyên có độ rộng cố định có thể sai kiểu gì?
Integer Overflow
        ↓
Vì sao 0.1 + 0.2 gây bất ngờ?
Floating Point
        ↓
Nếu phạm vi số nguyên không đủ?
BigInteger
        ↓
Nếu cần decimal chính xác?
BigDecimal
        ↓
Precision và scale khác nhau thế nào?
Precision & Scale
        ↓
Khi nào cần làm tròn và dùng chính sách nào?
Rounding
        ↓
Vì sao equals và compareTo của BigDecimal khác nhau?
BigDecimal Comparison
        ↓
`Math` cung cấp những phép toán hỗ trợ nào?
Math
        ↓
Random khác SecureRandom ở mục đích sử dụng nào?
Random → SecureRandom
```

## <a id="numeric-type-model">Các nhóm kiểu số</a>

### KHÁI NIỆM

Java có các kiểu số primitive với độ rộng cố định:

```text
byte, short, int, long
→ số nguyên có dấu

float, double
→ floating-point theo IEEE 754
```

và các kiểu object bất biến quan trọng:

```text
BigInteger
→ số nguyên có độ chính xác tùy ý

BigDecimal
→ giá trị thập phân với scale rõ ràng
```

Khi chọn kiểu số, hãy bắt đầu từ yêu cầu của bài toán: bộ đếm/ID, dữ liệu cảm biến hoặc khoa học, tiền tệ/tỷ lệ... thay vì chọn `double` hoặc `long` theo thói quen.

## <a id="integer-vs-floating">Integer và Floating-point</a>

Phép toán số nguyên cho kết quả **chính xác trong phạm vi biểu diễn**. Khi vượt giới hạn, primitive integer bị wrap quanh thay vì tự động mở rộng phạm vi.

Floating-point có phạm vi biểu diễn rất lớn nhưng dùng dạng nhị phân, nên nhiều số thập phân như `0.1` không thể được biểu diễn chính xác tuyệt đối.

```text
integer
→ chính xác trong phạm vi
→ overflow khi vượt phạm vi

floating-point
→ biểu diễn gần đúng
→ có NaN / infinity / negative zero
```

Đây là hai loại lỗi hoàn toàn khác nhau: integer có thể cho **giá trị wrap sai**, còn floating-point thường cho **giá trị gần đúng**.

## <a id="numeric-conversions">Chuyển đổi và Numeric Promotion</a>

Trước khi phép toán số học thực sự chạy, Java áp dụng các quy tắc chuyển đổi và numeric promotion.

Ví dụ các kiểu số nguyên nhỏ hơn `int` thường được promote lên `int` trong biểu thức:

```java
byte a = 10;
byte b = 20;
int result = a + b;
```

Biểu thức trộn nhiều kiểu số thường được nâng về kiểu có phạm vi hoặc precision phù hợp hơn theo quy tắc của ngôn ngữ.

Cast có thể thu hẹp kiểu của kết quả, nhưng **không thể khôi phục thông tin đã mất trước đó**. Nếu overflow hoặc rounding đã xảy ra trong phép toán, cast sau đó không sửa được giá trị gốc.

chương tiếp theo bắt đầu với lỗi dễ bỏ qua nhất của integer: **overflow nhưng chương trình vẫn chạy bình thường**.
