# Integer Overflow

Số nguyên primitive nhìn có vẻ “exact”, nhưng chỉ exact **trong phạm vi cố định**. Khi phép tính vượt `MIN_VALUE` hoặc `MAX_VALUE`, Java không tự chuyển sang `BigInteger` và cũng không mặc định throw exception.

## <a id="integer-overflow-wraparound">Overflow và Wraparound</a>

Ví dụ:

```java
int value = Integer.MAX_VALUE;
value++;
```

Kết quả trở thành `Integer.MIN_VALUE` do arithmetic fixed-width wrap quanh theo biểu diễn two's complement.

Điều nguy hiểm là chương trình vẫn tiếp tục chạy. Nếu giá trị là quantity, counter, price-in-cents hoặc offset, logic phía sau có thể nhận một số hoàn toàn sai nhưng không có exception cảnh báo.

### THỰC HÀNH

Khi đầu vào có thể tiến gần ranh giới, đừng chỉ test “giá trị bình thường”. Hãy test quanh:

```text
MIN_VALUE
MIN_VALUE + 1
-1 / 0 / 1
MAX_VALUE - 1
MAX_VALUE
```

## <a id="checked-arithmetic">Checked Arithmetic</a>

`Math` cung cấp các exact helper như:

```java
Math.addExact(a, b)
Math.subtractExact(a, b)
Math.multiplyExact(a, b)
Math.incrementExact(a)
```

Nếu thao tác overflow, chúng throw `ArithmeticException` thay vì wrap im lặng.

Đây là lựa chọn tốt khi overflow phải được xem là **lỗi của hợp đồng**, không phải hành vi chấp nhận được.

## <a id="boundary-values">Giá trị biên MIN/MAX</a>

ranh giới arithmetic dễ gây bug vì một số phép biến đổi toán học trực giác không còn đúng trong fixed-width integer.

Ví dụ `Math.abs(Integer.MIN_VALUE)` không thể trả `+2147483648` dưới dạng `int`, vì giá trị đó vượt `Integer.MAX_VALUE`.

Khi phạm vi của domain thực sự có thể vượt `long`, đừng cố vá từng overflow case; hãy cân nhắc `BigInteger`.

Trước khi tới `BigInteger`, ta cần hiểu một dạng trade-off khác: floating-point không wrap theo cùng cách nhưng lại **không biểu diễn chính xác mọi số thập phân**.
