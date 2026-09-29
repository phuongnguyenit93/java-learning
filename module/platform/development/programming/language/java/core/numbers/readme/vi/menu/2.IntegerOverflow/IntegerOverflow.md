# Integer Overflow

Số nguyên primitive nhìn có vẻ “exact”, nhưng chỉ exact **trong phạm vi fixed-width**. Khi arithmetic vượt `MIN_VALUE` hoặc `MAX_VALUE`, Java không tự đổi sang `BigInteger` và cũng không mặc định throw exception.

## <a id="integer-overflow-wraparound">Overflow và Wraparound</a>

### WHAT

`int` là signed 32-bit integer. Giá trị lớn nhất và nhỏ nhất là:

```text
Integer.MIN_VALUE = -2^31
Integer.MAX_VALUE =  2^31 - 1
```

Khi phép toán đi ra ngoài range đó, kết quả wrap theo arithmetic fixed-width:

```java
int value = Integer.MAX_VALUE;
value++;

System.out.println(value); // Integer.MIN_VALUE
```

### WHY nguy hiểm?

Overflow thường **không tạo exception**:

```text
input hợp lệ
    ↓
arithmetic overflow
    ↓
giá trị wrap nhưng vẫn là int hợp lệ
    ↓
business logic tiếp tục chạy với dữ liệu sai
```

Nếu giá trị là quantity, cents, counter, timeout, offset hoặc capacity, bug có thể xuất hiện xa nơi overflow thực sự xảy ra.

### HOW — intermediate result mới là nơi cần nhìn

```java
int quantity = 1_000_000;
int unitPrice = 10_000;

long total = quantity * unitPrice;
```

Phép nhân vẫn là `int * int`. Assignment sang `long` diễn ra **sau arithmetic**.

Cách đúng nếu range của result cần `long`:

```java
long total = (long) quantity * unitPrice;
```

Tương tự:

```java
long a = 3_000_000_000L;
long b = 4_000_000_000L;

long product = a * b; // long cũng có thể overflow
```

Đổi từ `int` sang `long` chỉ tăng range, không loại bỏ overflow vĩnh viễn.

## <a id="checked-arithmetic">Checked Arithmetic</a>

Khi overflow phải được coi là **contract failure**, dùng exact helper thay vì silent wrap:

```java
Math.addExact(a, b);
Math.subtractExact(a, b);
Math.multiplyExact(a, b);
Math.incrementExact(a);
Math.decrementExact(a);
Math.negateExact(a);
Math.absExact(a);
Math.divideExact(a, b);
Math.toIntExact(longValue);
```

Ví dụ:

```java
try {
    int next = Math.addExact(Integer.MAX_VALUE, 1);
} catch (ArithmeticException ex) {
    System.out.println("overflow detected");
}
```

Mental model:

```text
ordinary arithmetic
→ overflow có thể wrap

exact arithmetic helper
→ overflow được biến thành ArithmeticException
```

Không phải mọi arithmetic đều cần helper. Nếu wraparound là chủ ý của low-level algorithm thì checked arithmetic có thể không phù hợp. Nhưng với quantity/money/counter domain, silent overflow thường là bug.

Với baseline Java 21 của repository, `absExact` và `divideExact` đặc biệt hữu ích cho hai boundary case ở phần sau: `MIN_VALUE` không có positive counterpart cùng primitive type, và `MIN_VALUE / -1` overflow.

## <a id="boundary-values">Giá trị biên MIN/MAX</a>

### Vì sao MIN_VALUE đặc biệt?

Signed two's-complement có một giá trị âm nhiều hơn phía dương:

```text
int
min = -2147483648
max =  2147483647
```

Vì vậy:

```java
int abs = Math.abs(Integer.MIN_VALUE);
System.out.println(abs); // vẫn là Integer.MIN_VALUE
```

`Math.abs` không thể trả `2147483648` dưới dạng `int` vì giá trị đó nằm ngoài range. Với overload `int` này, kết quả overflow và vẫn là `Integer.MIN_VALUE`; method không tự throw chỉ vì case này.

Tương tự, phép:

```java
int x = Integer.MIN_VALUE / -1;
System.out.println(x); // Integer.MIN_VALUE
```

Về toán học cần `2147483648`, nhưng `int` không biểu diễn được. Java định nghĩa special overflow case này trả lại `Integer.MIN_VALUE` thay vì throw overflow exception; chỉ division by zero mới throw `ArithmeticException` trong integer division.

### Boundary testing

Khi logic có nguy cơ gần range limit, nên test:

```text
MIN_VALUE
MIN_VALUE + 1
-1
0
1
MAX_VALUE - 1
MAX_VALUE
```

và các expression có **intermediate multiplication/addition**.

Nếu domain thực sự có thể vượt `long`, đừng vá từng overflow case. Khi đó representation phù hợp hơn là `BigInteger`.

Trước khi tới `BigInteger`, ta cần hiểu một trade-off khác: floating-point không wrap theo cùng kiểu nhưng lại **không biểu diễn chính xác mọi decimal fraction**.
