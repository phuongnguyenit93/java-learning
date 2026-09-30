# Số nguyên phạm vi tùy ý với BigInteger

Khi `long` không đủ phạm vi, giải pháp đúng thường không phải “hy vọng tràn số không xảy ra” mà là dùng cách biểu diễn không bị giới hạn bởi kiểu nguyên thủy có độ rộng cố định.

## <a id="big-integer-model">BigInteger là gì?</a>

`BigInteger` biểu diễn số nguyên với độ chính xác tùy ý (arbitrary precision) trong giới hạn bộ nhớ thực tế.

```java
BigInteger value =
        new BigInteger("123456789012345678901234567890");
```

Các cách tạo thường gặp:

```java
BigInteger a = BigInteger.valueOf(123456789L);
BigInteger b = new BigInteger("FF", 16);
BigInteger zero = BigInteger.ZERO;
BigInteger one = BigInteger.ONE;
```

### VÌ SAO

`BigInteger` phù hợp khi:

- các giá trị tổ hợp tăng rất nhanh;
- bộ đếm/định danh của bài toán vượt `long`;
- phép toán số nguyên với độ chính xác tùy ý là một phần của thuật toán;
- một số phép toán mật mã cần số nguyên rất lớn.

Đánh đổi:

```text
kiểu số nguyên thủy
→ kích thước cố định
→ rất nhanh
→ ít cấp phát bộ nhớ

BigInteger
→ độ chính xác tùy ý
→ dựa trên đối tượng
→ chi phí phép toán tăng theo kích thước giá trị
```

Không nên dùng `BigInteger` chỉ vì “an toàn hơn” nếu `long` đã đủ và hiệu năng/khả năng tương tác với API khác là quan trọng.

## <a id="big-integer-immutability">Tính bất biến của BigInteger</a>

`BigInteger` không nạp chồng toán tử số học kiểu `+`, `-`, `*`. Thay vào đó, phép toán được thực hiện bằng phương thức và trả về đối tượng mới:

```java
BigInteger a = BigInteger.TEN;

a.add(BigInteger.ONE); // kết quả bị bỏ qua
System.out.println(a); // 10

a = a.add(BigInteger.ONE);
System.out.println(a); // 11
```

Cách ghi nhớ:

```text
đối tượng BigInteger
→ giá trị không đổi

biến tham chiếu
→ có thể trỏ sang đối tượng mới
```

Tính bất biến giúp việc chia sẻ giá trị dễ suy luận hơn, nhưng cũng có nghĩa một chuỗi phép toán có thể tạo nhiều đối tượng trung gian.

## <a id="big-integer-operations">Phép toán và ranh giới chuyển đổi</a>

Các phép toán chính:

```java
a.add(b);
a.subtract(b);
a.multiply(b);
a.divide(b);
a.remainder(b);
a.mod(b);
a.pow(3);
a.gcd(b);
```

### Phép chia BigInteger vẫn là phép chia số nguyên

```java
BigInteger seven = BigInteger.valueOf(7);
BigInteger two = BigInteger.valueOf(2);

System.out.println(seven.divide(two)); // 3
```

`BigInteger` giải quyết **phạm vi**, không biến phép toán số nguyên thành phép toán thập phân.

### `remainder` và `mod` không hoàn toàn đồng nghĩa

`remainder` tuân theo ngữ nghĩa phép dư số nguyên và có thể cho kết quả âm nếu số bị chia âm. `mod(m)` dùng số học mô-đun, yêu cầu mô-đun dương và trả kết quả không âm trong phạm vi `0 <= result < m`.

Đây là điểm khác biệt quan trọng trong lý thuyết số/phép toán mật mã; không nên thay hai phương thức cho nhau chỉ vì cả hai trông giống phép "%".

### So sánh

```java
int cmp = a.compareTo(b);
boolean same = a.equals(b);
```

Với BigInteger, quan hệ bằng nhau không có khác biệt về scale như BigDecimal.

### Chuyển đổi về kiểu nguyên thủy là một ranh giới quan trọng

```java
BigInteger huge = new BigInteger("999999999999999999999");

int truncated = huge.intValue();
```

`intValue()` có thể chỉ giữ lại các bit thấp thay vì báo lỗi. Nếu cần bảo đảm giá trị nằm gọn trong kiểu đích:

```java
int exact = huge.intValueExact();   // ArithmeticException nếu không nằm gọn trong kiểu
long exactLong = huge.longValueExact();
```

Quy tắc thực tế:

```text
BigInteger → kiểu nguyên thủy
→ coi như ranh giới thu hẹp kiểu
→ dùng chuyển đổi chính xác nếu tràn số phải được phát hiện
```

Nếu vấn đề không phải phạm vi số nguyên mà là **độ chính xác thập phân + chính sách làm tròn**, BigInteger chưa đủ; chương tiếp theo là `BigDecimal`.
