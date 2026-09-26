# BigInteger

Khi `long` không đủ range, giải pháp đúng thường không phải “hy vọng overflow không xảy ra” mà là dùng representation không bị fixed-width primitive giới hạn.

## <a id="big-integer-model">BigInteger là gì?</a>

`BigInteger` biểu diễn integer với arbitrary precision trong giới hạn bộ nhớ thực tế.

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

### WHY

`BigInteger` phù hợp khi:

- combinatorial values tăng rất nhanh;
- counters/domain identifiers vượt `long`;
- arbitrary-precision integer arithmetic là một phần của algorithm;
- một số cryptographic arithmetic cần integer rất lớn.

Trade-off:

```text
primitive integer
→ fixed-size
→ rất nhanh
→ ít allocation

BigInteger
→ arbitrary precision
→ object-based
→ arithmetic cost tăng theo kích thước value
```

Không nên dùng `BigInteger` chỉ vì “an toàn hơn” nếu `long` đã đủ và performance/interop quan trọng.

## <a id="big-integer-immutability">BigInteger là Immutable</a>

`BigInteger` không có arithmetic operator overload kiểu `+`, `-`, `*`. Thay vào đó, operation là method và trả object mới:

```java
BigInteger a = BigInteger.TEN;

a.add(BigInteger.ONE); // result bị bỏ qua
System.out.println(a); // 10

a = a.add(BigInteger.ONE);
System.out.println(a); // 11
```

Mental model:

```text
BigInteger object
→ value không đổi

variable reference
→ có thể trỏ sang object mới
```

Immutability giúp value sharing dễ reasoning hơn, nhưng cũng có nghĩa một chuỗi arithmetic có thể tạo nhiều intermediate object.

## <a id="big-integer-operations">Operation và Conversion Boundary</a>

Core operations:

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

### Integer division vẫn là integer division

```java
BigInteger seven = BigInteger.valueOf(7);
BigInteger two = BigInteger.valueOf(2);

System.out.println(seven.divide(two)); // 3
```

`BigInteger` giải quyết **range**, không biến integer arithmetic thành decimal arithmetic.

### `remainder` và `mod` không hoàn toàn đồng nghĩa

`remainder` đi theo integer remainder semantics và có thể cho result âm nếu dividend âm. `mod(m)` dùng modular arithmetic, yêu cầu modulus dương và trả non-negative result trong range `0 <= result < m`.

Đây là distinction quan trọng trong number-theory/cryptographic arithmetic; không nên thay hai method cho nhau chỉ vì cả hai trông giống phép "%".

### Comparison

```java
int cmp = a.compareTo(b);
boolean same = a.equals(b);
```

Với BigInteger, equality không có scale nuance như BigDecimal.

### Conversion về primitive là một boundary quan trọng

```java
BigInteger huge = new BigInteger("999999999999999999999");

int truncated = huge.intValue();
```

`intValue()` có thể giữ lại low-order bits thay vì báo lỗi. Nếu cần đảm bảo value fit:

```java
int exact = huge.intValueExact();   // ArithmeticException nếu không fit
long exactLong = huge.longValueExact();
```

Rule thực tế:

```text
BigInteger → primitive
→ coi như narrowing boundary
→ dùng exact conversion nếu overflow phải bị phát hiện
```

Nếu vấn đề không phải integer range mà là **decimal exactness + rounding policy**, BigInteger chưa đủ; chương tiếp theo là `BigDecimal`.
