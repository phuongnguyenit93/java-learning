# Mô hình số trong Java

Java không có một kiểu số duy nhất phù hợp với mọi bài toán. Mỗi cách biểu diễn đánh đổi giữa **phạm vi giá trị, độ chính xác, hiệu năng, bộ nhớ và cách làm tròn**.

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
→ số thập phân với value + scale rõ ràng,
  phù hợp khi decimal semantics và rounding policy quan trọng
```

Không có representation nào đồng thời cho ta phạm vi vô hạn, decimal chính xác tuyệt đối, tốc độ của primitive và footprint nhỏ. Vì vậy học Numbers trước hết là học **chọn representation phù hợp với contract của bài toán**.

Lộ trình:

```text
Chọn kiểu số nào?
Numeric Model
        ↓
Số nguyên fixed-width có thể sai kiểu gì?
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
Khi nào cần làm tròn và dùng policy nào?
Rounding
        ↓
Vì sao equals và compareTo của BigDecimal khác nhau?
BigDecimal Comparison
        ↓
`Math` cung cấp helper nào?
Math
        ↓
Random khác SecureRandom ở contract nào?
Random → SecureRandom
```

Ba nhóm ví dụ sẽ được dùng xuyên module:

```text
counter / quantity / ID
→ integer semantics và overflow

sensor / scientific measurement
→ floating-point approximation và tolerance

money / rate / tax
→ BigDecimal, precision, scale và rounding policy
```

## <a id="numeric-type-model">Các nhóm kiểu số</a>

### WHAT — Java có những representation số nào?

Các primitive integer:

| Kiểu | Số bit | Giá trị nhỏ nhất | Giá trị lớn nhất |
| --- | ---: | ---: | ---: |
| `byte` | 8 | -128 | 127 |
| `short` | 16 | -32,768 | 32,767 |
| `int` | 32 | -2³¹ | 2³¹ - 1 |
| `long` | 64 | -2⁶³ | 2⁶³ - 1 |

`char` cũng là primitive integral type nhưng dùng để biểu diễn UTF-16 code unit; về mục đích học Numbers, trọng tâm arithmetic thường nằm ở `byte/short/int/long`.

Floating-point primitive:

```text
float   → IEEE 754 binary32
double  → IEEE 754 binary64
```

Các object type quan trọng:

```text
BigInteger
→ arbitrary-precision integer

BigDecimal
→ unscaled integer có arbitrary precision + scale kiểu int
→ decimal semantics và rounding policy rõ ràng
```

### WHY — Vì sao cần nhiều kiểu?

Hãy bắt đầu từ câu hỏi của domain thay vì từ cú pháp Java:

| Bài toán | Representation thường phù hợp | Lý do chính |
| --- | --- | --- |
| số lượng, index, counter nhỏ | `int` | nhanh, đơn giản, đủ range |
| timestamp/count lớn | `long` | range lớn hơn |
| scientific/sensor/graphics | `double` | wide range + hardware support |
| integer vượt `long` | `BigInteger` | không fixed-width |
| tiền, tax, rate decimal | `BigDecimal` | decimal semantics + explicit rounding |

Một ID database thường nên là `long` không phải vì cần làm toán, mà vì cần **range đủ rộng**. Một số đo nhiệt độ có thể dùng `double` vì domain chấp nhận tolerance. Một số tiền lại thường không nên dùng `double` vì contract cần decimal semantics.

### HOW — Chọn representation bằng contract

Khi gặp một giá trị số, hỏi theo thứ tự:

```text
1. Có cần fractional value không?
        ↓
2. Có cần decimal exactness không?
        ↓
3. Range tối đa là bao nhiêu?
        ↓
4. Overflow/rounding có được phép không?
        ↓
5. Performance/footprint có quan trọng không?
```

Không nên chọn `double` chỉ vì “chứa được số lớn”, cũng không nên dùng `BigDecimal` cho mọi phép toán chỉ vì “chính xác hơn”. Mỗi representation giải quyết một contract khác nhau.

## <a id="integer-vs-floating">Integer và Floating-point</a>

Integer arithmetic và floating-point arithmetic có **failure mode khác nhau**.

```text
integer
→ exact trong representable range
→ overflow/wraparound khi vượt range

floating-point
→ wide dynamic range
→ nhiều giá trị chỉ là approximation
→ có NaN / infinity / signed zero
```

Ví dụ:

```java
int count = Integer.MAX_VALUE;
count++;
System.out.println(count); // Integer.MIN_VALUE

double total = 0.1 + 0.2;
System.out.println(total); // 0.30000000000000004
```

Hai kết quả “lạ” này không cùng nguyên nhân:

- integer sai vì **range hữu hạn**;
- floating-point lệch vì **representation hữu hạn trong hệ nhị phân**.

Điều quan trọng không phải “kiểu nào chính xác hơn” một cách chung chung, mà là **kiểu nào đúng contract hơn**.

## <a id="numeric-conversions">Chuyển đổi và Numeric Promotion</a>

### Cơ chế quan trọng: operation type được quyết định trước assignment

Trước khi arithmetic chạy, Java áp dụng các rule về conversion và numeric promotion.

Các integer type nhỏ hơn `int` thường được promote lên `int`:

```java
byte a = 10;
byte b = 20;

int result = a + b;
// byte invalid = a + b; // compile error
```

Đây là lý do `byte + byte` không tự cho ra `byte`.

Với expression trộn kiểu:

```java
int i = 10;
long l = 20L;
double d = 1.5;

long x = i + l;
double y = l + d;
```

### Pitfall: widen ở assignment không cứu được overflow đã xảy ra

```java
int quantity = 1_000_000;
int price = 10_000;

long wrong = quantity * price;
```

Người mới thường nghĩ `wrong` là `long` nên an toàn. Nhưng flow thực tế là:

```text
int * int
    ↓
arithmetic chạy dưới dạng int
    ↓
có thể overflow
    ↓
kết quả int đã sai
    ↓
convert sang long
```

Muốn phép nhân chạy dưới dạng `long`, phải widen **trước arithmetic**:

```java
long correct = (long) quantity * price;
```

### Cast không phục hồi dữ liệu đã mất

```java
int overflowed = Integer.MAX_VALUE + 1;
long widened = (long) overflowed;
```

`widened` chỉ chứa phiên bản `long` của giá trị đã overflow. Cast sau cùng không thể quay lại phép toán ban đầu.

Tương tự, nếu arithmetic `double` đã tạo rounding error rồi mới đưa vào `BigDecimal`, BigDecimal không thể biết decimal intent ban đầu là gì.

Chương tiếp theo bắt đầu với failure mode dễ bỏ qua nhất của integer: **overflow nhưng chương trình vẫn tiếp tục chạy**.
