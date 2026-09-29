# Math và StrictMath

Java đã cung cấp nhiều standard numeric helper. Dùng API chuẩn thường thể hiện intent rõ hơn và tránh tự viết lại edge case khó.

## <a id="math-core-functions">Các hàm chính của Math</a>

`Math` có thể nhóm thành:

```text
basic selection
→ abs, min, max

power/root
→ pow, sqrt, cbrt

rounding helpers
→ floor, ceil, round, rint

trigonometry/logarithm
→ sin, cos, tan, log, exp

integer helpers
→ floorDiv, floorMod, exact arithmetic

floating-point helpers
→ ulp, nextUp, nextDown, copySign...
```

### `floor`, `ceil`, `round`

```java
Math.floor(2.7);  // 2.0
Math.ceil(2.1);   // 3.0
Math.round(2.5);  // 3

Math.floor(-2.1); // -3.0
Math.ceil(-2.9);  // -2.0
```

`floor` nghĩa là đi về -∞, không phải “bỏ phần decimal”.

### `floorDiv` và `floorMod`

Java integer division thông thường truncate toward zero:

```java
System.out.println(-7 / 3); // -2
```

`Math.floorDiv` dùng floor division:

```java
System.out.println(Math.floorDiv(-7, 3)); // -3
```

Khi algorithm cần mathematical floor semantics với số âm, distinction này rất quan trọng.

## <a id="exact-arithmetic-methods">Exact Integer Arithmetic</a>

Các helper:

```java
Math.addExact
Math.subtractExact
Math.multiplyExact
Math.incrementExact
Math.decrementExact
Math.negateExact
Math.absExact
Math.divideExact
Math.toIntExact
```

biến silent overflow thành `ArithmeticException`.

Ví dụ:

```java
long total =
        Math.multiplyExact(
                (long) quantity,
                unitPrice
        );
```

### Exact helper không làm domain thành arbitrary precision

Nếu value hợp lệ có thể vượt `long`, exact helper chỉ giúp **phát hiện** overflow. Representation đúng vẫn là `BigInteger`.

Với Java 21, `Math.absExact(Integer.MIN_VALUE)` và `Math.divideExact(Integer.MIN_VALUE, -1)` throw `ArithmeticException` thay vì trả silent overflow result như các phép tương ứng không checked.

## <a id="strictmath-boundary">Math và StrictMath</a>

`Math` là lựa chọn thông thường cho application code.

`StrictMath` tồn tại cho trường hợp cần behavior của floating-point mathematical function theo contract reproducibility/specification chặt hơn.

```text
Math
→ default numeric helper API

StrictMath
→ ưu tiên specified reproducibility cho các mathematical functions liên quan
```

Không nên hiểu `StrictMath` là “Math nhưng chính xác tuyệt đối”. Các function vẫn hoạt động trên floating-point values và chịu giới hạn representation của `double/float`.

Chỉ quan tâm boundary này khi cross-platform reproducibility thật sự là requirement.

Hai chapter cuối chuyển từ number representation sang một contract khác: **randomness**.
