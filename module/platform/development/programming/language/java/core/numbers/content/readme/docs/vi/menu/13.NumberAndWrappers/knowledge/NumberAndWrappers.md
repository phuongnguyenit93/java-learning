<a id="back-to-top"></a>

# Number và wrapper

## Menu
- [Number là lớp trừu tượng và hợp đồng chuyển đổi giá trị](#number-and-wrappers-001)
- [Byte, Short, Integer, Long, Float và Double](#number-and-wrappers-002)
- [Character không kế thừa Number dù char là kiểu số](#number-and-wrappers-003)
- [BigInteger, BigDecimal và các lớp Number khác trong JDK](#number-and-wrappers-004)
- [Wrapper bất biến và ngữ nghĩa value-based](#number-and-wrappers-005)
- [valueOf thay cho constructor wrapper lỗi thời](#number-and-wrappers-006)
- [MIN_VALUE, MAX_VALUE, TYPE và các hằng theo từng lớp](#number-and-wrappers-007)
- [compare, compareTo, equals và hashCode](#number-and-wrappers-008)
- [Wrapper khác kiểu không tự bằng nhau qua equals](#number-and-wrappers-009)
- [Number không cung cấp toán tử cộng chung cho mọi lớp con](#number-and-wrappers-010)
- [Giới hạn khi tự xây dựng một lớp con Number](#number-and-wrappers-011)

## <a id="number-and-wrappers-001">Number là lớp trừu tượng và hợp đồng chuyển đổi giá trị</a>

<details>
<summary>Xem chi tiết</summary>

# `Number` là lớp trừu tượng và hợp đồng chuyển đổi giá trị trong Java

Trong Java, `java.lang.Number` là **lớp trừu tượng (abstract class)** làm kiểu cha chung cho nhiều lớp biểu diễn số: `Byte`, `Short`, `Integer`, `Long`, `Float`, `Double`, `BigInteger`, `BigDecimal` và một số lớp số học đồng thời như `AtomicInteger`, `AtomicLong`. Mục đích của nó không phải cung cấp một “kiểu số chung có thể tự cộng, trừ, nhân, chia”, mà là cung cấp **giao diện ở cấp đối tượng để chuyển giá trị số sang một số kiểu nguyên thủy**.

```java
Number a = Integer.valueOf(42);
Number b = Long.valueOf(4_294_967_297L);
Number c = new java.math.BigDecimal("12.75");

System.out.println(a.intValue()); // 42
System.out.println(b.intValue()); // 1: không còn giữ đủ giá trị long.
System.out.println(c.intValue()); // 12: phần thập phân bị loại bỏ.
```

Cả ba cùng có kiểu tham chiếu `Number`, nhưng **hành vi chuyển đổi phụ thuộc lớp cụ thể của đối tượng**. Phương thức `intValue()` không cam kết luôn chính xác: có thể làm mất phần lẻ, mất độ lớn, mất bit quan trọng hoặc trả kết quả đổi dấu.

**Mục tiêu của bài:** hiểu hợp đồng của lớp `Number`, phân biệt phương thức trừu tượng và phương thức đã triển khai, sử dụng đa hình, nhận ra các chuyển đổi có thể mất thông tin, và quyết định khi nào cần cơ chế chuyển đổi *exact* riêng của từng kiểu.

## 1. Vị trí của `Number` trong hệ thống kiểu Java

Khai báo cơ bản của JDK:

```java
public abstract class Number implements java.io.Serializable {
    // Các phương thức chuyển giá trị sang kiểu nguyên thủy.
}
```

`Number` thuộc `java.lang` nên không cần `import`. Nó là một **class**, không phải interface; không thể tạo đối tượng bằng `new Number()` vì lớp là `abstract`.

```java
// Number n = new Number(); // Lỗi biên dịch: Number là abstract.

Number n1 = Integer.valueOf(10);
Number n2 = Double.valueOf(2.5);
Number n3 = new java.math.BigInteger("12345678901234567890");
```

Sơ đồ khái niệm:

```text
Object
  └── Number (abstract, Serializable)
       ├── Byte / Short / Integer / Long
       ├── Float / Double
       ├── BigInteger / BigDecimal
       ├── AtomicInteger / AtomicLong
       └── Các lớp con Number khác
```

Một số lớp kể trên là **wrapper của primitive**, một số không phải wrapper:

| Loại | Ví dụ | Đặc điểm |
| --- | --- | --- |
| Wrapper số nguyên | `Byte`, `Short`, `Integer`, `Long` | Đối tượng bất biến chứa giá trị primitive tương ứng |
| Wrapper số thực | `Float`, `Double` | Đối tượng bất biến chứa giá trị binary32/binary64 |
| Số nguyên tùy ý độ lớn | `BigInteger` | Bất biến, không giới hạn bởi 32/64 bit cố định (trong giới hạn bộ nhớ/triển khai) |
| Số thập phân | `BigDecimal` | Bất biến, giá trị gồm phần nguyên không tỷ lệ và `scale`; phù hợp thập phân chính xác |
| Số trong ngữ cảnh đồng thời | `AtomicInteger`, `AtomicLong` | Có thể thay đổi giá trị bằng thao tác atomic; không phải wrapper bất biến |

**Đừng hiểu `Number` là bảo đảm mọi lớp con đều bất biến hoặc thread-safe.** Đối tượng wrapper như `Integer` là bất biến; `AtomicInteger` lại có thể cập nhật. Mặc dù `Number` triển khai `Serializable`, các quyết định về biểu diễn bền vững, định dạng trao đổi, quy tắc so sánh và an toàn dữ liệu vẫn là trách nhiệm của API/kiểu cụ thể.

## 2. Hợp đồng sáu phương thức chuyển đổi

Theo API Java SE 17, `Number` khai báo sáu phương thức chính:

| Phương thức | Kiểu trả về | Trong `Number` | Bảo đảm |
| --- | --- | --- | --- |
| `intValue()` | `int` | **Trừu tượng** | Trả giá trị sau chuyển đổi sang `int` theo lớp triển khai |
| `longValue()` | `long` | **Trừu tượng** | Trả giá trị sau chuyển đổi sang `long` |
| `floatValue()` | `float` | **Trừu tượng** | Trả giá trị sau chuyển đổi sang `float` |
| `doubleValue()` | `double` | **Trừu tượng** | Trả giá trị sau chuyển đổi sang `double` |
| `byteValue()` | `byte` | **Có triển khai mặc định** | Mặc định lấy `(byte) intValue()` |
| `shortValue()` | `short` | **Có triển khai mặc định** | Mặc định lấy `(short) intValue()` |

Bốn phương thức trừu tượng yêu cầu **lớp con cụ thể phải cung cấp cách trả giá trị tương ứng**, trừ khi kế thừa một triển khai từ lớp cha trung gian. Hai phương thức `byteValue` và `shortValue` **không trừu tượng** trong `Number`, và lớp con có thể ghi đè nếu cần.

### Tại sao không có `charValue()` hay `booleanValue()`?

`char` và `boolean` đều là primitive Java, nhưng không thuộc sáu primitive được `Number` cung cấp chuyển đổi. `char` đại diện một đơn vị mã UTF-16, không phải kiểu số có dấu theo cùng hợp đồng; `boolean` không phải kiểu số. Vì vậy, **không có `Number.charValue()`, `Number.booleanValue()` hoặc `Number.stringValue()`**.

Muốn chuỗi, có thể gọi `toString()` của **đối tượng cụ thể**, nhưng `Number` không cam kết mọi lớp con sẽ dùng cùng một định dạng hiển thị hoặc cùng radix. Muốn phân tích chuỗi thành số, sử dụng parser riêng như `Integer.parseInt`, `Double.parseDouble`, `new BigInteger(String)`, `new BigDecimal(String)`; **`Number` không có phương thức static `parse` tổng quát**.

### Mã dạng đơn giản để hiểu hợp đồng

```java
public abstract class Number implements java.io.Serializable {
    public abstract int intValue();
    public abstract long longValue();
    public abstract float floatValue();
    public abstract double doubleValue();

    public byte byteValue() {
        return (byte) intValue();
    }

    public short shortValue() {
        return (short) intValue();
    }
}
```

Đây là **sơ đồ mô tả API**, không phải mã cần viết lại để thay `java.lang.Number` có sẵn trong JDK. Các chú thích truy xuất nguồn và chi tiết triển khai thực tế có thể khác; điều quan trọng là bốn phương thức abstract và hai phương thức có triển khai mặc định.

## 3. Đa hình: kiểu khai báo là `Number`, kiểu runtime quyết định hành vi

`Number` cho phép một API nhận nhiều kiểu số dưới cùng một kiểu tham chiếu:

```java
static void showAsDouble(Number number) {
    System.out.println(number.doubleValue());
}
```

Nếu truyền một `Integer`, một `Long` hoặc `BigDecimal`, Java gọi phương thức `doubleValue()` **được triển khai bởi đối tượng thực tế** (dynamic dispatch). Đây là đa hình; không phải tất cả số được “tự biến” thành `double` ngay lúc gán cho biến `Number`.

```java
Number n = Long.valueOf(9_007_199_254_740_993L);
System.out.println(n.longValue());   // 9007199254740993
System.out.println(n.doubleValue()); // 9.007199254740992E15 (mất 1 đơn vị).
```

`n` vẫn tham chiếu đối tượng `Long` giữ giá trị nguyên đúng. **Chỉ kết quả trả về của `doubleValue()`** có thể mất chính xác. Bản thân việc gán `Long` vào biến `Number` không tự thu hẹp giá trị.

### `Number` không có toán tử số học chung

```java
Number x = Integer.valueOf(3);
Number y = Double.valueOf(2.5);

// Number sum = x + y; // Không biên dịch: + không được định nghĩa cho Number.
double approximate = x.doubleValue() + y.doubleValue(); // 5.5.
```

Trong Java, toán tử `+` cho số nguyên thủy hoặc toán tử ghép chuỗi; nó **không được overload bởi class `Number`**. Việc hai đối tượng cùng là `Number` không cho Java biết nên dùng số nguyên lớn, số thập phân, hay chấm động nhị phân cho phép cộng.

Nếu **chủ động** chuyển cả hai sang `double`, phép cộng theo binary64 có thể hợp lý cho số đo gần đúng. Nhưng đó là **quyết định thuật toán**, không phải phép cộng trừu tượng giữ chính xác với mọi lớp con `Number`.

## 4. Hợp đồng là “chuyển giá trị”, không phải “giữ nguyên mọi thông tin”

Java API nói rõ: ngữ nghĩa chi tiết của `intValue`, `longValue`, `floatValue`, `doubleValue`... do **triển khai cụ thể** quyết định. Các chuyển đổi thường tương tự widening/narrowing primitive conversion, nên có thể:

1. **Mất phần lẻ:** `3.75` chuyển sang `int` thành `3`.
2. **Mất độ lớn:** `long` lớn hơn miền `int` chuyển sang `int` giữ mẫu bit thấp theo quy tắc thu hẹp nguyên.
3. **Thay đổi dấu:** số nguyên dương lớn có thể thành `int` âm.
4. **Mất độ chính xác:** một `long` vượt độ chính xác nguyên liên tiếp của `double` bị làm tròn.
5. **Mất miền hữu hạn:** `BigDecimal` rất lớn chuyển sang `double` có thể trở thành `Infinity`.
6. **Phụ thuộc trạng thái nếu lớp con có thể thay đổi:** `AtomicInteger.intValue()` đọc một giá trị tại thời điểm gọi.

`Number` **không hứa** sẽ ném exception nếu giá trị không thể biểu diễn chính xác dưới kiểu đích. Nếu nghiệp vụ đòi hỏi “không mất thông tin”, cần dùng các phương thức **exact** của loại số phù hợp hoặc kiểm tra miền/bước làm tròn riêng.

### Ví dụ: `long` thu hẹp xuống `int`

```java
Number n = Long.valueOf(4_294_967_297L);
System.out.println(n.longValue()); // 4294967297
System.out.println(n.intValue());  // 1
```

Giá trị ban đầu bằng `2^32 + 1`. Khi chuyển thành `int` 32 bit, các bit cao bị bỏ đi và 32 bit thấp biểu diễn 1. **Không có `ArithmeticException`** ở lời gọi `intValue()` này.

Tương tự:

```java
Number n = new java.math.BigInteger("2147483648");
System.out.println(n.intValue()); // -2147483648
```

Giá trị `2^31` không nằm trong miền `int` có dấu; 32 bit thấp có bit dấu bằng 1, nên kết quả thành `Integer.MIN_VALUE`. Dữ liệu không còn cùng dấu và độ lớn ban đầu.

### Ví dụ: `double` sang `int`

```java
System.out.println(Double.valueOf(3.75).intValue()); // 3
System.out.println(Double.valueOf(-3.75).intValue()); // -3
System.out.println(Double.valueOf(Double.NaN).intValue()); // 0
System.out.println(Double.valueOf(Double.POSITIVE_INFINITY).intValue()); // 2147483647
```

Các quy tắc chuyển primitive floating-point sang integer trong Java gồm bỏ phần lẻ theo hướng về 0, NaN thành 0, và giới hạn về giá trị cực đại/cực tiểu của kiểu nguyên trong các trường hợp ngoài miền. **Không được** diễn giải 0 trả về từ NaN là phép đo thật sự bằng 0. Nếu dữ liệu ngoại vi chứa NaN/Infinity, hãy xác thực bằng `Double.isFinite` trước khi chuyển.

## 5. Vì sao `byteValue()` và `shortValue()` dễ gây bất ngờ?

Mặc định `Number.byteValue()` tương đương với gọi `intValue()` rồi cast `byte`; `shortValue()` tương tự:

```java
byte small = (byte) 258; // 2.
short shortResult = (short) 65537; // 1.
```

Một lớp con chỉ cần triển khai bốn phương thức trừu tượng, và có thể kế thừa hai hành vi này. Do đó, ngay cả khi lớp con lưu dữ liệu trong `long`, gọi `byteValue()` theo mặc định sẽ qua bước `intValue()`. Các lớp con có thể override nếu hợp đồng của chúng cần hành vi khác.

**Kết luận:** tên `byteValue()` không có nghĩa “kiểm tra giá trị vừa miền byte”; nó chỉ yêu cầu một kết quả kiểu `byte`. Nếu muốn phát hiện mất dữ liệu, xem các phương thức `byteValueExact()` của `BigInteger` hoặc `BigDecimal`, thay vì mặc định tin `Number.byteValue()`.

## 6. Tự triển khai một lớp con `Number`

Khi có một loại số tự định nghĩa cần tham gia API nhận `Number`, ta có thể tạo lớp con và cài đặt bốn phương thức trừu tượng:

```java
static final class CounterNumber extends Number {
    private final long value;

    CounterNumber(long value) {
        this.value = value;
    }

    @Override
    public int intValue() {
        return (int) value;
    }

    @Override
    public long longValue() {
        return value;
    }

    @Override
    public float floatValue() {
        return value;
    }

    @Override
    public double doubleValue() {
        return value;
    }
}
```

Đây là **lớp lồng trong một class khác**, minh họa với dữ liệu lưu `long`. Chương trình đầy đủ có ở phần 11.

- `longValue()` giữ nguyên giá trị đã lưu.
- `intValue()` có thể tràn theo quy tắc cast `long -> int`.
- `floatValue()` và `doubleValue()` có thể làm tròn số nguyên lớn.
- `byteValue()` và `shortValue()` được kế thừa từ `Number`, thông qua `intValue()`.

```java
System.out.println(new CounterNumber(258L).byteValue()); // 2
```

**Lưu ý thiết kế:** mở rộng `Number` không tự tạo các API parse, format, cộng, so sánh hay hợp đồng equality có ý nghĩa. Nếu class đại diện tiền, phân số, đơn vị đo hoặc số bất biến riêng, cần thiết kế những thao tác đó một cách độc lập, và xem xét có thực sự cần kế thừa `Number` hay không. Đây là lựa chọn kiến trúc, không phải điều kiện bắt buộc của mọi lớp “có giá trị số”.

## 7. Phương thức `Exact` thuộc lớp con, không thuộc `Number`

`Number` không có `intValueExact()` hoặc `longValueExact()` dùng chung. Tuy nhiên các lớp số lớn cung cấp API kiểm tra mất thông tin.

| Loại | Phương thức | Bảo đảm / phản ứng |
| --- | --- | --- |
| `BigInteger` | `intValueExact()`, `longValueExact()`, `shortValueExact()`, `byteValueExact()` | Ném `ArithmeticException` nếu giá trị nguyên vượt miền kiểu đích |
| `BigDecimal` | `intValueExact()`, `longValueExact()`, `shortValueExact()`, `byteValueExact()` | Ném `ArithmeticException` nếu có phần lẻ khác 0 **hoặc** không vừa miền đích |
| `BigDecimal` | `toBigIntegerExact()` | Ném `ArithmeticException` nếu có phần thập phân khác 0 |
| `long` sang `int` | `Math.toIntExact(long)` | Ném `ArithmeticException` nếu `long` không vừa `int` |

### `BigDecimal`: `intValue` và `intValueExact` là hai chính sách

```java
java.math.BigDecimal value =
        new java.math.BigDecimal("12.75");

System.out.println(value.intValue()); // 12
// value.intValueExact(); // ArithmeticException: bỏ phần lẻ khác 0.
```

`intValue()` trên `BigDecimal` bỏ phần lẻ và khi giá trị nguyên vượt miền `int` có thể chỉ giữ 32 bit thấp. `intValueExact()` yêu cầu giá trị **là số nguyên** và **vừa `int`**. Chẳng hạn `new BigDecimal("12.00").intValueExact()` trả 12, vì phần thập phân toán học bằng 0.

### `BigInteger`: chỉ cần kiểm tra giới hạn

```java
java.math.BigInteger tooLarge =
        new java.math.BigInteger("2147483648");

System.out.println(tooLarge.intValue()); // -2147483648
// tooLarge.intValueExact(); // ArithmeticException: vượt Integer.MAX_VALUE.
```

`BigInteger` luôn biểu diễn một số nguyên, nên không có phần lẻ cần bỏ; API exact chủ yếu kiểm tra **miền kiểu đích**.

### `long` sang `int` khi đang làm việc với primitive

```java
long count = 4_294_967_297L;
// int unsafe = (int) count;       // 1, mất bit cao.
// int safe = Math.toIntExact(count); // ArithmeticException.
```

Các API exact này **không phải phương thức của `Number`**. Khi biến chỉ có kiểu khai báo `Number`, trình biên dịch không biết có `intValueExact` trên mọi subtype hay không. Phải thiết kế hợp đồng đầu vào, kiểm tra loại cụ thể hoặc chọn một API có kiểu chính xác hơn.

## 8. Xử lý một `Number` không rõ lớp mà không mất dữ liệu âm thầm

Một cách *không nên làm* khi muốn giữ nguyên thập phân:

```java
static java.math.BigDecimal badConversion(Number n) {
    return new java.math.BigDecimal(n.doubleValue());
}
```

Hàm này chuyển mọi loại số sang `double` trước: `BigInteger` lớn có thể mất chữ số và `BigDecimal` chính xác có thể bị làm tròn; `new BigDecimal(double)` sẽ mô tả **chính xác xấp xỉ nhị phân** đã được đưa vào, không khôi phục dữ liệu gốc. Ngoài ra `Double.NaN` và vô cực không thể tạo `BigDecimal` theo cách này.

Một ví dụ **có hợp đồng rõ** chỉ chấp nhận kiểu thập phân và kiểu nguyên thích hợp:

```java
static java.math.BigDecimal toExactDecimal(Number n) {
    if (n == null) {
        throw new IllegalArgumentException("number is required");
    }
    if (n instanceof java.math.BigDecimal bd) {
        return bd;
    }
    if (n instanceof java.math.BigInteger bi) {
        return new java.math.BigDecimal(bi);
    }
    if (n instanceof Byte || n instanceof Short
            || n instanceof Integer || n instanceof Long) {
        return java.math.BigDecimal.valueOf(n.longValue());
    }
    throw new IllegalArgumentException(
            "Unsupported numeric source: " + n.getClass().getName());
}
```

Đây là phương thức minh họa Java 17+ (dùng `instanceof` pattern matching). Nó **chủ động từ chối** `Float`, `Double`, `AtomicInteger` và mọi subtype lạ để tránh suy diễn sai rằng `Number.doubleValue()` bảo đảm thập phân chính xác.

Nếu ứng dụng muốn chấp nhận `double` hữu hạn, có thể đưa ra quy tắc riêng như `BigDecimal.valueOf(d)`, nhưng phải hiểu **giá trị `double` đã được làm tròn trước đó** và phân biệt với `new BigDecimal(d)`. Với dữ liệu đo xấp xỉ, chính sách cho phép làm tròn có thể hợp lý; với giao thức số thập phân chính xác, chỉ chấp nhận các loại rõ ràng như trên sẽ an toàn hơn.

**Thông điệp thiết kế:** đừng kỳ vọng một phương thức `Number` tự quyết định đúng độ chính xác cho mọi hệ thống. Càng cần giữ chính xác, kiểu tham số/kiểu trả về càng nên **cụ thể**.

## 9. `Number` khác autoboxing, unboxing và generic arithmetic

### Boxing không biến `Number` thành primitive

```java
Number a = 42;      // int -> Integer (boxing) -> Number (upcast).
Number b = 1.25;    // double -> Double (boxing) -> Number.
int x = a.intValue();

// int y = a; // Không biên dịch: không unbox trực tiếp một Number bất kỳ.
```

Java hỗ trợ **unboxing** từ các wrapper được ngôn ngữ xác định như `Integer`, `Double`, `Long`, chứ không có quy tắc unboxing từ **mọi subtype `Number`**. Lời gọi `intValue()` là gọi phương thức, không phải tự động chuyển tất cả `Number` thành primitive theo cùng phép toán.

Khi một biến có kiểu `Integer`, biểu thức như `Integer boxed = 3; int v = boxed;` có thể dùng unboxing ngầm. Nếu boxed bằng `null`, phép unboxing phát sinh `NullPointerException`. Tương tự, `Number n = null; n.intValue()` gây NPE vì gọi phương thức trên tham chiếu null.

### Generic `T extends Number` không tự cung cấp toán tử `+`

```java
static double sumAsDouble(
        java.util.List<? extends Number> numbers) {
    double result = 0.0;
    for (Number n : numbers) {
        result += n.doubleValue();
    }
    return result;
}
```

Phương thức này có thể dùng cho `List<Integer>`, `List<Long>`, `List<BigDecimal>`..., nhưng kết quả **là tổng xấp xỉ binary64**, không bảo đảm thập phân chính xác hoặc miễn overflow. `List<? extends Number>` chỉ mô tả ràng buộc kiểu; nó không làm Java cho phép `n1 + n2` trên các đối tượng `Number`.

Với thư viện tính tiền, tổng số nguyên lớn hoặc phân số hữu tỉ, thường nên nhận `List<BigDecimal>`, `List<BigInteger>`, hoặc một kiểu giá trị riêng có hợp đồng rõ. Đừng sử dụng generic `Number` rồi ép tất cả sang `double` nếu yêu cầu loại trừ mất dữ liệu.

## 10. Equality, thứ tự và tính biến đổi không được `Number` chuẩn hóa

Hai đối tượng có giá trị toán học giống nhau không nhất thiết so sánh bằng nhau khi gọi `equals`:

```java
System.out.println(
        Integer.valueOf(1).equals(Long.valueOf(1L))); // false.

System.out.println(
        new java.math.BigDecimal("1.0")
            .equals(new java.math.BigDecimal("1.00"))); // false.
```

`Integer.equals` yêu cầu đối tượng cùng kiểu `Integer` và cùng giá trị; `Long.equals` có quy tắc kiểu tương ứng. Với `BigDecimal`, `equals` còn xét **scale**, nên 1.0 khác 1.00; trong khi `compareTo` cho thấy giá trị toán học bằng nhau:

```java
java.math.BigDecimal x = new java.math.BigDecimal("1.0");
java.math.BigDecimal y = new java.math.BigDecimal("1.00");
System.out.println(x.compareTo(y) == 0); // true.
```

`Number` **không đặt một quy tắc `equals`/`hashCode`/`compareTo` chung giữa mọi subtype**. Không nên gọi `new HashSet<Number>()` rồi kỳ vọng nó tự gộp `Integer(1)`, `Long(1L)` và `BigDecimal("1.0")` thành một phần tử.

Một số `Number` còn **mutable**:

```java
java.util.concurrent.atomic.AtomicInteger counter =
        new java.util.concurrent.atomic.AtomicInteger(3);
Number snapshotSource = counter;
counter.incrementAndGet();
System.out.println(snapshotSource.intValue()); // 4.
```

`snapshotSource` tham chiếu **cùng đối tượng** `AtomicInteger` đã đổi giá trị, không phải ảnh chụp `3`. Nếu cần giá trị snapshot bất biến, hãy đọc primitive tại thời điểm có chủ đích (`int snapshot = counter.intValue()`) rồi lưu riêng. `intValue` của `AtomicInteger` trả giá trị quan sát tại lời gọi; nếu nhiều luồng cập nhật, các lần gọi liên tiếp không nhất thiết cùng một số.

## 11. Chương trình Java 17+ độc lập

Tạo tệp `NumberContractDemo.java`:

```java
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class NumberContractDemo {
    static class CounterNumber extends Number {
        private final long value;

        CounterNumber(long value) {
            this.value = value;
        }

        @Override public int intValue() {
            return (int) value;
        }
        @Override public long longValue() {
            return value;
        }
        @Override public float floatValue() {
            return value;
        }
        @Override public double doubleValue() {
            return value;
        }
    }

    public static void main(String[] args) {
        List<Number> list = List.of(
                Integer.valueOf(42),
                Long.valueOf(4_294_967_297L),
                Double.valueOf(3.75),
                new BigDecimal("42.50"),
                new BigInteger("4294967296"),
                new AtomicInteger(7));

        for (Number n : list) {
            System.out.println(n.getClass().getSimpleName()
                    + ": int=" + n.intValue()
                    + ", long=" + n.longValue());
        }

        System.out.println("Default byteValue: "
                + new CounterNumber(258L).byteValue());

        System.out.println("Decimal intValue: "
                + new BigDecimal("12.75").intValue());
        try {
            new BigDecimal("12.75").intValueExact();
        } catch (ArithmeticException ex) {
            System.out.println("Decimal intValueExact: rejected");
        }

        System.out.println("BigInteger intValue: "
                + new BigInteger("2147483648").intValue());
        try {
            new BigInteger("2147483648").intValueExact();
        } catch (ArithmeticException ex) {
            System.out.println("BigInteger intValueExact: rejected");
        }

        System.out.println("NaN intValue: "
                + Double.valueOf(Double.NaN).intValue());
        System.out.println("Infinity intValue: "
                + Double.valueOf(Double.POSITIVE_INFINITY).intValue());

        System.out.println("Cross-wrapper equals: "
                + Integer.valueOf(1).equals(Long.valueOf(1L)));
        System.out.println("Decimal equals by scale: "
                + new BigDecimal("1.0").equals(new BigDecimal("1.00")));
        System.out.println("Decimal compareTo numerically: "
                + (new BigDecimal("1.0")
                    .compareTo(new BigDecimal("1.00")) == 0));
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 NumberContractDemo.java
java NumberContractDemo
```

Kết quả stdout:

```text
Integer: int=42, long=42
Long: int=1, long=4294967297
Double: int=3, long=3
BigDecimal: int=42, long=42
BigInteger: int=0, long=4294967296
AtomicInteger: int=7, long=7
Default byteValue: 2
Decimal intValue: 12
Decimal intValueExact: rejected
BigInteger intValue: -2147483648
BigInteger intValueExact: rejected
NaN intValue: 0
Infinity intValue: 2147483647
Cross-wrapper equals: false
Decimal equals by scale: false
Decimal compareTo numerically: true
```

Chương trình kiểm chứng bốn ý chính: cùng kiểu tham chiếu `Number` có thể chứa nhiều kiểu số; chuyển đổi sang `int`/`long` có thể làm mất giá trị; hai phương thức `byteValue/shortValue` có mặc định; chuyển đổi *exact* và bình đẳng `equals` là **hợp đồng của kiểu cụ thể**, không phải của mọi `Number`.

## 12. Cách chọn kiểu và cách thiết kế API

| Nhu cầu | Lựa chọn thường phù hợp | Lý do |
| --- | --- | --- |
| Hiển thị thống kê xấp xỉ từ nhiều lớp số, sai số được chấp nhận | `Number` rồi `doubleValue()` **có chủ đích** | Dễ viết API đa hình; phải kiểm tra finite/độ chính xác |
| API yêu cầu số nguyên đúng miền `int` | `int`/`Integer` với kiểm tra đầu vào, hoặc `intValueExact` trên loại hỗ trợ | Không tin `Number.intValue` để phát hiện overflow |
| Số nguyên không giới hạn trong 64 bit | `BigInteger` | Tránh mất bit bởi `longValue/intValue` |
| Tài chính hoặc phép toán decimal chính xác | `BigDecimal` | Giữ cách biểu diễn thập phân và kiểm soát rounding |
| Chỉ lưu primitive 32/64 bit, cần hiệu suất/memory rõ | `int`/`long`/`double` | Tránh boxing không cần thiết trong vòng lặp nóng |
| Giá trị số có thể thay đổi giữa các luồng | `AtomicInteger`/`AtomicLong` nếu đúng nhu cầu đồng thời | Có API atomic; đọc `Number` chỉ là lấy một giá trị |
| Danh sách dị loại để trình bày/ghi log | `List<Number>` | Dùng chung kiểu tham chiếu; không suy ra arithmetic/equality |
| Thao tác cộng/trừ trên nhiều kiểu mà **phải** giữ chính xác | Thiết kế kiểu số riêng hoặc chọn kiểu chuẩn hóa phù hợp | `Number` không mô tả phép toán algebra hay quy tắc rounding |

### Hợp đồng API nên ghi rõ những gì?

Nếu định nghĩa phương thức nhận `Number`, nên trả lời:

1. Có cho phép **mọi** subtype `Number` không, hay chỉ `Integer`/`Long`/`BigDecimal`?
2. Có chấp nhận NaN, vô cực, số âm, phần thập phân hay không?
3. Có thể mất độ chính xác, cắt phần lẻ, thay đổi dấu hoặc tràn không?
4. Giá trị có thể bị thay đổi giữa hai lần đọc từ một `Number` mutable không?
5. Kết quả trả về mang kiểu nào; lỗi nhập liệu và overflow được báo thế nào?
6. Nếu chuyển sang `double`, sai số và miền độ lớn cho phép là bao nhiêu?
7. Có cần quy tắc `equals`/`compareTo` liên kiểu hay chỉ xử lý riêng từng subtype?

Trong API công khai, **chấp nhận `Number` vì linh hoạt** phải đi kèm **tài liệu về độ chính xác và conversion**. Nếu chỉ có một kiểu số hợp lệ theo nghiệp vụ, nhận đúng kiểu đó thường đơn giản và an toàn hơn.

## 13. Những lỗi hay gặp

| Nhầm lẫn | Điều xảy ra | Cách xử lý |
| --- | --- | --- |
| Tưởng `Number` là interface | Nó là **abstract class**, có phương thức đã triển khai | Dùng `extends Number` khi tạo subtype |
| Cố tạo `new Number()` | Lỗi biên dịch | Tạo đối tượng lớp con cụ thể |
| Tưởng đủ triển khai `intValue()` | `longValue`, `floatValue`, `doubleValue` còn abstract | Triển khai cả bốn nếu không được kế thừa từ lớp cha |
| Tin `n.intValue()` luôn kiểm tra miền | `Long(4294967297)` trả 1 | Dùng checked/exact conversion nếu bắt buộc |
| Tin `n.doubleValue()` bảo toàn mọi số nguyên | `2^53+1` không giữ chính xác trong binary64 | Giữ `long`/`BigInteger` nếu cần |
| Tin `n.byteValue()` tự ném lỗi nếu quá 127 | Mặc định cast từ `intValue` | Dùng exact API phù hợp |
| Tưởng `Number` hỗ trợ `x + y` | Toán tử `+` không được định nghĩa cho hai `Number` | Chọn kiểu số/chiến lược toán học tường minh |
| Dùng `new BigDecimal(n.doubleValue())` để “bảo toàn” dữ liệu | Dữ liệu đã mất trước khi xây `BigDecimal` | Nhận loại hỗ trợ exact và chuyển trực tiếp |
| Xem mọi `Number` là wrapper bất biến | `AtomicInteger` thay đổi được | Chú ý tính mutable và snapshot |
| Tưởng `Number` chuẩn hóa `equals` và `compareTo` | Mỗi subtype có hợp đồng riêng | Tránh equality/hashing liên kiểu chưa thiết kế |
| Tưởng gán `Number n = 2` biến số nguyên thành `double` | Giá trị được boxing thành `Integer` | Phân biệt boxing, upcast và conversion |
| Gọi `n.intValue()` trên `null` | `NullPointerException` | Kiểm tra null tại biên |
| Dùng `Number` cho tiền chính xác nhưng lại cộng qua `doubleValue` | Độ chính xác decimal có thể mất | Dùng `BigDecimal` và chính sách làm tròn |
| Giữ `Number` qua serialization mà không định nghĩa schema | Kiểu cụ thể/precision có thể mơ hồ khi liên hệ với hệ khác | Xác định loại số, format, scale và chính sách chuyển đổi trong giao thức |

## 14. Tự kiểm tra

1. `Number` là class, interface hay abstract class? Vì sao không dùng `new Number()`?
2. Bốn phương thức abstract của `Number` là gì? Hai phương thức nào có triển khai mặc định, và mặc định dùng cách nào?
3. Vì sao `Number n = Long.valueOf(4_294_967_297L); n.intValue()` trả 1 chứ không ném exception?
4. Một `BigDecimal("12.75")` cho `intValue()` bằng bao nhiêu? `intValueExact()` có hành vi gì?
5. Vì sao `BigInteger("2147483648").intValue()` có thể **âm** dù số gốc dương?
6. `Number n = Integer.valueOf(3)` có thể dùng `int x = n;` không? Tại sao `int x = n.intValue();` hợp lệ?
7. Tại sao `List<? extends Number>` không làm phép cộng `+` giữa hai phần tử trở thành hợp lệ?
8. Vì sao chuyển `BigInteger` lớn qua `doubleValue()` rồi tạo `BigDecimal` không bảo toàn mọi chữ số?
9. Khi nào `AtomicInteger` thuộc `Number` có thể cho hai kết quả `intValue()` khác nhau liên tiếp?
10. Vì sao `Integer.valueOf(1).equals(Long.valueOf(1L))` là false? `BigDecimal("1.0")` và `BigDecimal("1.00")` có giống nhau theo `equals` và `compareTo` không?
11. Nếu API chỉ chấp nhận số nguyên vừa `int`, nên thay `Number.intValue()` không kiểm tra bằng hợp đồng nào?
12. Với một loại đơn vị đo mới, việc `extends Number` có tự tạo toán tử arithmetic hoặc comparator không?

## 15. Tài liệu chính thức

- [`java.lang.Number` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Number.html): mô tả lớp abstract, bốn phương thức abstract, hai phương thức có mặc định và cảnh báo mất thông tin khi conversion.
- [`java.math.BigDecimal` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): hợp đồng chuyển kiểu và các phương thức `intValueExact`, `longValueExact`, `toBigIntegerExact`.
- [`java.math.BigInteger` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigInteger.html): số nguyên lớn, `intValueExact`/`longValueExact` và chuyển đổi cắt bit thấp.
- [Java Language Specification `5.1.2 và `5.1.3](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html): widening/narrowing primitive conversions.
- [`java.util.concurrent.atomic.AtomicInteger` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html): lớp số nguyên cập nhật atomic có kế thừa `Number`.

**Kết luận:** `Number` là **kiểu cha trừu tượng cho các đối tượng số có khả năng chuyển sang sáu primitive**. Nó cho đa hình chuyển đổi, **không cam kết** arithmetic chung, độ chính xác, bình đẳng liên kiểu hoặc tính bất biến. `intValue`/`longValue`/`floatValue`/`doubleValue` có thể làm mất thông tin; hai phương thức `byteValue`/`shortValue` có mặc định thông qua `intValue`. Khi cần miền số, phần lẻ hoặc độ chính xác được bảo đảm, hãy chọn **kiểu cụ thể và checked/exact conversion** thay vì coi mọi `Number` như một giá trị số có thể hoán đổi hoàn toàn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="number-and-wrappers-002">Byte, Short, Integer, Long, Float và Double</a>

<details>
<summary>Xem chi tiết</summary>

# `Byte`, `Short`, `Integer`, `Long`, `Float` và `Double` trong Java

## 1. Sáu lớp wrapper là gì?

Java có sáu kiểu nguyên thủy biểu diễn số được nhắc đến ở đây: `byte`, `short`, `int`, `long`, `float`, `double`. Mỗi kiểu có một **lớp bao (wrapper class)** tương ứng trong gói `java.lang`:

| Kiểu nguyên thủy | Lớp wrapper | Loại số | Độ rộng |
| --- | --- | --- | ---: |
| `byte` | `Byte` | Số nguyên có dấu | 8 bit |
| `short` | `Short` | Số nguyên có dấu | 16 bit |
| `int` | `Integer` | Số nguyên có dấu | 32 bit |
| `long` | `Long` | Số nguyên có dấu | 64 bit |
| `float` | `Float` | Dấu phẩy động nhị phân IEEE 754 | 32 bit |
| `double` | `Double` | Dấu phẩy động nhị phân IEEE 754 | 64 bit |

Các wrapper này là những **đối tượng bất biến (immutable)**: sau khi tạo, giá trị bên trong không thay đổi. Chúng đều là lớp `final`, đều kế thừa lớp trừu tượng `java.lang.Number`, và hỗ trợ so sánh cùng kiểu qua `Comparable<T>`.

Ví dụ:

```java
byte primitiveByte = 7;
Byte wrappedByte = Byte.valueOf(primitiveByte);

int primitiveInt = 42;
Integer wrappedInt = Integer.valueOf(primitiveInt);

double primitiveDouble = 0.5;
Double wrappedDouble = Double.valueOf(primitiveDouble);
```

Sự khác nhau quan trọng:

- **Primitive** (`int`, `double`...) trực tiếp biểu diễn giá trị kiểu nguyên thủy; không có phương thức instance và không thể là `null`.
- **Wrapper** (`Integer`, `Double`...) là một tham chiếu tới đối tượng chứa giá trị; có phương thức hữu ích như `compareTo`, `intValue`, `toString`, `equals`, có thể là `null`, và dùng được ở nơi cần đối tượng hoặc kiểu tham số của generic.
- `Byte`, `Short`, `Integer`, `Long` biểu diễn số nguyên theo độ rộng cố định; `Float`, `Double` biểu diễn số dấu phẩy động hữu hạn hoặc các giá trị đặc biệt như `NaN`, vô cực.

Bài này tập trung vào sáu wrapper số kể trên. `Boolean`, `Character` cũng là wrapper của primitive nhưng không thuộc nhóm số kế thừa `Number`. `BigInteger` và `BigDecimal` là những kiểu số đối tượng khác, không tương ứng trực tiếp với một primitive.

## 2. Miền giá trị và các hằng số quan trọng

### 2.1. Các wrapper số nguyên

| Wrapper | Primitive | `MIN_VALUE` | `MAX_VALUE` |
| --- | --- | ---: | ---: |
| `Byte` | `byte` | `-128` | `127` |
| `Short` | `short` | `-32768` | `32767` |
| `Integer` | `int` | `-2147483648` | `2147483647` |
| `Long` | `long` | `-9223372036854775808` | `9223372036854775807` |

Mỗi wrapper số nguyên có các hằng số `SIZE` (số bit), `BYTES` (số byte) và `TYPE` (đối tượng `Class` đại diện primitive tương ứng).

```java
System.out.println(Integer.SIZE);  // 32
System.out.println(Long.BYTES);    // 8
System.out.println(Integer.TYPE);  // int
```

Đây là các dòng **minh họa trong một phương thức**, không phải một chương trình đầy đủ. Có thể dùng `Integer.MIN_VALUE` và `Integer.MAX_VALUE` khi kiểm tra miền trước khi chuyển từ `long` xuống `int`.

### 2.2. `Float` và `Double`: đừng hiểu sai `MIN_VALUE`

| Wrapper | Bit | `MAX_VALUE` | `MIN_VALUE` |
| --- | ---: | --- | --- |
| `Float` | 32 | Khoảng `3.4028235E38` | Khoảng `1.4E-45`, **dương** |
| `Double` | 64 | Khoảng `1.7976931348623157E308` | Khoảng `4.9E-324`, **dương** |

Với `Float`/`Double`, `MIN_VALUE` là **số dương khác 0 nhỏ nhất có thể biểu diễn** (số dưới chuẩn/subnormal), **không** phải số âm lớn nhất về độ lớn. Giá trị âm hữu hạn thấp nhất là `-Float.MAX_VALUE` hoặc `-Double.MAX_VALUE`.

Các hằng số hữu ích khác:

- `Float.MIN_NORMAL`, `Double.MIN_NORMAL`: số dương chuẩn hóa nhỏ nhất.
- `Float.POSITIVE_INFINITY`, `Double.POSITIVE_INFINITY` và các hằng số `NEGATIVE_INFINITY`: vô cực dương/âm.
- `Float.NaN`, `Double.NaN`: giá trị “không phải một số”.
- `Float.SIZE == 32`, `Double.SIZE == 64`; `Float.BYTES == 4`, `Double.BYTES == 8`.

Hai kiểu dấu phẩy động có thể mất độ chính xác khi biểu diễn phân số thập phân hoặc số nguyên lớn. `Float` có khoảng 24 bit chính xác phần trị, `Double` có khoảng 53 bit; điều này khác với số bit tổng cộng của kiểu. Vì vậy, không chọn `Double` chỉ vì nó có thể chứa số rất lớn rồi mặc định mọi phép tính decimal đều chính xác tuyệt đối.

## 3. Mối quan hệ với `java.lang.Number`

`Number` là một lớp trừu tượng để biểu diễn “một giá trị số” dưới dạng đối tượng. Cả sáu wrapper đều kế thừa nó. Các phương thức chuyển giá trị phổ biến của `Number` gồm:

| Phương thức | Kiểu trả về | Ý nghĩa |
| --- | --- | --- |
| `byteValue()` | `byte` | Lấy giá trị theo kiểu `byte` |
| `shortValue()` | `short` | Lấy giá trị theo kiểu `short` |
| `intValue()` | `int` | Lấy giá trị theo kiểu `int` |
| `longValue()` | `long` | Lấy giá trị theo kiểu `long` |
| `floatValue()` | `float` | Lấy giá trị theo kiểu `float` |
| `doubleValue()` | `double` | Lấy giá trị theo kiểu `double` |

Ví dụ đa hình:

```java
Number[] values = {
    Byte.valueOf((byte) 7),
    Short.valueOf((short) 300),
    Integer.valueOf(42),
    Long.valueOf(9_000_000_000L),
    Float.valueOf(1.25f),
    Double.valueOf(0.5)
};

for (Number value : values) {
    System.out.println(value.getClass().getSimpleName()
            + ": " + value.doubleValue());
}
```

Nhờ `Number`, mã có thể đọc một **dạng giá trị số chung** từ các loại đối tượng khác nhau. Tuy nhiên, các phương thức `...Value()` không bảo đảm chuyển đổi chính xác:

```java
Number n = Integer.valueOf(257);
byte narrowed = n.byteValue(); // 1: chỉ giữ 8 bit thấp
```

Một `Long` lớn chuyển sang `double` có thể mất các bit phần trị; một `Double` có phần lẻ chuyển sang `int` bị cắt về 0 hoặc bão hòa tại biên; `Float.NaN` chuyển sang kiểu nguyên cho 0 theo quy tắc Java.

**`Number` không định nghĩa `parse`, `valueOf`, `compareTo` hoặc `add` chung cho mọi subtype.** Những phương thức `parseXxx`, `valueOf`, `compare` là API riêng của các wrapper cụ thể. Việc một biến được khai báo `Number` không biến Java thành ngôn ngữ có toán tử số học đa hình trên đối tượng:

```java
Number n = Integer.valueOf(12);
int a = n.intValue();     // hợp lệ
// int b = n + 1;         // không biên dịch: Number không tự unbox thành int
```

Muốn giữ nguyên kiểu cụ thể, hãy dùng `Integer`, `Long`, `Double` hoặc API/generic được thiết kế phù hợp, thay vì giả định `Number` tự động chuyển thành mọi primitive.

## 4. Tạo wrapper: `valueOf`, autoboxing và constructor

Cách thường dùng để tạo wrapper là **`valueOf`** hoặc để Java **autoboxing** (tự đóng hộp):

```java
Byte b = Byte.valueOf((byte) 10);
Short s = Short.valueOf((short) 1000);
Integer i = Integer.valueOf(42);
Long l = Long.valueOf(9_000_000_000L);
Float f = Float.valueOf(1.25f);
Double d = Double.valueOf(0.5);

Integer autoInteger = 42; // compiler chèn thao tác boxing
Double autoDouble = 0.5;  // autoboxing từ double
```

Các wrapper là immutable; muốn có giá trị mới, gán một wrapper khác:

```java
Integer count = 5;
count++; // unbox -> cộng 1 theo int -> box, rồi gán lại biến count
```

Biến `count` đổi đối tượng mà nó tham chiếu; **đối tượng `Integer` ban đầu không bị sửa**. Do autoboxing tạo thao tác đối tượng, với vòng lặp và phép tính số học tần suất lớn thường ưu tiên primitive nếu không cần `null`/generic.

Không cần gọi `new Integer(...)`, `new Double(...)` và các constructor wrapper cổ điển. Các constructor kiểu này đã bị deprecated, nên ưu tiên `valueOf`/autoboxing để diễn đạt ý định rõ và cho phép JVM tái sử dụng đối tượng khi thích hợp.

### Cảnh báo khi dùng `==` với wrapper

`==` giữa **hai tham chiếu wrapper** kiểm tra chúng có chỉ vào cùng đối tượng không. `equals` kiểm tra giá trị theo hợp đồng của từng lớp. `Integer.valueOf` bắt buộc cache ít nhất các giá trị `-128` đến `127`, nhưng mã không nên phụ thuộc vào identity để so sánh số:

```java
Integer x = Integer.valueOf(100);
Integer y = Integer.valueOf(100);
System.out.println(x == y);      // true trong phạm vi cache được bảo đảm

Integer p = Integer.valueOf(200);
Integer q = Integer.valueOf(200);
System.out.println(p.equals(q));  // true
```

Kết quả `p == q` **không được dùng làm quy tắc** vì phạm vi cache ngoài mức bảo đảm có thể thay đổi theo môi trường. Khi một bên là primitive và một bên wrapper, toán hạng wrapper có thể bị *unbox* để thực hiện so sánh số:

```java
Integer boxed = 42;
int primitive = 42;
System.out.println(boxed == primitive); // true: so sánh giá trị int
```

Vì các biểu thức nhìn giống nhau có thể mang nghĩa reference hoặc numeric, nên trong mã kiểm tra giá trị wrapper cùng kiểu hãy ưu tiên `equals` (có xử lý `null`) hoặc chuyển sang primitive một cách tường minh khi cần.

## 5. Autoboxing, unboxing và `null`

**Autoboxing** là chuyển primitive thành wrapper; **unboxing** là chuyển wrapper thành primitive:

```java
Integer boxed = 12; // autoboxing: int -> Integer
int raw = boxed;     // unboxing: Integer -> int
```

Unboxing một tham chiếu `null` ném `NullPointerException`:

```java
Integer missing = null;
// int n = missing; // NullPointerException khi chạy
```

Bẫy này còn xuất hiện trong phép tính, so sánh với primitive hoặc toán tử `++`:

```java
Integer maybeCount = null;
// if (maybeCount > 0) { } // NullPointerException
// maybeCount++;            // NullPointerException
```

Nếu `null` mang nghĩa “chưa biết”, phải kiểm tra trước:

```java
Integer count = null;
int safeCount = count == null ? 0 : count;
```

Chọn giá trị mặc định 0 chỉ khi nghiệp vụ cho phép; nhiều ứng dụng phải phân biệt số 0 với dữ liệu thiếu.

### Wrapper trong collection và generic

Java generic cần kiểu tham chiếu, vì vậy:

```java
import java.util.ArrayList;
import java.util.List;

List<Integer> scores = new ArrayList<>();
scores.add(10);                // 10 được box thành Integer
scores.add(20);
int first = scores.get(0);    // unboxing
```

`List<int>` không hợp lệ trong Java 17; dùng `List<Integer>`. Tương tự, có thể tạo `List<Long>`, `Map<String, Double>` hoặc `List<Number>`.

Lưu ý `List<Integer>` không phải subtype của `List<Number>`, dù `Integer` kế thừa `Number`: generic Java mặc định là **invariant**. Để đọc danh sách nhiều kiểu số, chữ ký `List<? extends Number>` có thể phù hợp, nhưng thao tác ghi vào danh sách bị giới hạn bởi wildcard. Đây là vấn đề kiểu generic, không phải khác biệt giá trị của wrapper.

Collection có thể chứa wrapper `null` khi cấu trúc dữ liệu cho phép, nên đọc từ `List<Integer>` rồi unbox vẫn có nguy cơ `NullPointerException`.

## 6. Phân tích chuỗi số: `parseXxx`, `valueOf(String)` và radix

Mỗi wrapper có các API phân tích chuỗi tương ứng:

| Wrapper | Trả về primitive | Trả về wrapper |
| --- | --- | --- |
| `Byte` | `Byte.parseByte(text)` | `Byte.valueOf(text)` |
| `Short` | `Short.parseShort(text)` | `Short.valueOf(text)` |
| `Integer` | `Integer.parseInt(text)` | `Integer.valueOf(text)` |
| `Long` | `Long.parseLong(text)` | `Long.valueOf(text)` |
| `Float` | `Float.parseFloat(text)` | `Float.valueOf(text)` |
| `Double` | `Double.parseDouble(text)` | `Double.valueOf(text)` |

Ví dụ:

```java
int a = Integer.parseInt("42");       // primitive int
Integer b = Integer.valueOf("42");    // Integer
long c = Long.parseLong("9000000000");
double d = Double.parseDouble("0.5");
```

**`parseXxx` không tạo wrapper ở kết quả** (dù JIT/runtime có thể có tối ưu nội bộ), còn `valueOf(String)` trả đối tượng wrapper. Cả hai đều có thể ném `NumberFormatException` khi chuỗi không hợp lệ hoặc vượt miền của kiểu.

### Hệ cơ số cho wrapper nguyên

Bốn wrapper nguyên có overload nhận **radix**, thông thường từ 2 đến 36:

```java
int hex = Integer.parseInt("FF", 16); // 255
int binary = Integer.parseInt("1010", 2); // 10
long base36 = Long.parseLong("Z", 36); // 35
```

Nếu chuỗi gồm tiền tố như `"0xFF"` hoặc `"0b1010"`, đừng mặc định `parseInt(text, radix)` nhận nguyên xi tiền tố:

```java
int hex = Integer.parseInt("FF", 16); // hợp lệ
int prefixed = Integer.decode("0xFF"); // 255
// Integer.parseInt("0xFF", 16); // NumberFormatException
```

`Integer.decode`/`Long.decode`/`Byte.decode`/`Short.decode` hỗ trợ quy tắc tiền tố thập lục phân và bát phân của các API này, nhưng không phải bộ parse tổng quát cho mọi dạng literal Java. Một số dấu phân tách hợp lệ trong mã nguồn, như dấu gạch dưới của `1_000`, **không tự được chấp nhận** khi parse chuỗi:

```java
int literal = 1_000;            // hợp lệ trong mã Java
// Integer.parseInt("1_000");  // NumberFormatException
```

`Integer.parseInt(" 42 ")` cũng không tự bỏ khoảng trắng; chỉ `trim()`/`strip()` khi chính sách đầu vào cho phép. Với các định dạng theo locale (ví dụ dấu phẩy thập phân), `parseDouble` không thay thế một parser phù hợp locale.

### Chuỗi số thực và giá trị đặc biệt

`Float.parseFloat` và `Double.parseDouble` nhận cú pháp thập phân hoặc dạng dấu phẩy động phù hợp API Java; chúng cũng có thể phân tích chuỗi `"NaN"`, `"Infinity"` và `"-Infinity"`.

```java
double half = Double.parseDouble("0.5");           // 0.5
double infinity = Double.parseDouble("Infinity");  // dương vô cực
boolean infinite = Double.isInfinite(infinity);    // true
```

Đầu vào là chuỗi đúng cú pháp chưa chắc đã mang giá trị hữu hạn: phải kiểm tra `Float.isFinite` / `Double.isFinite` nếu nghiệp vụ yêu cầu hữu hạn. Không dùng `Double.parseDouble` để lưu tiền thập phân cần chính xác tuyệt đối; cân nhắc `BigDecimal` từ chuỗi đầu vào.

## 7. Chuyển ngược sang chuỗi và các tiện ích cho số nguyên

Các wrapper có `toString`, `toString(primitive)` và `valueOf`; với `Integer`/`Long`, có thêm hỗ trợ nhị phân, thập lục phân, không dấu:

```java
String decimal = Integer.toString(255);       // "255"
String hex = Integer.toHexString(255);        // "ff"
String binary = Integer.toBinaryString(10);   // "1010"
String base16 = Integer.toString(255, 16);    // "ff"
String unsigned = Integer.toUnsignedString(-1); // "4294967295"
```

`Integer.toUnsignedString(-1)` và `Long.toUnsignedString(-1L)` xem cùng mẫu bit 32/64 bit theo **cách diễn giải không dấu**. Chúng không làm Java có primitive `uint`/`ulong`.

Ví dụ đọc một giá trị âm theo mẫu bit không dấu:

```java
int mask = -1;
System.out.println(Integer.toUnsignedString(mask));  // 4294967295
System.out.println(Long.toUnsignedString(-1L));       // 18446744073709551615
```

Với `byte` và `short`, có thể mở rộng không dấu:

```java
int uByte = Byte.toUnsignedInt((byte) -1);   // 255
int uShort = Short.toUnsignedInt((short) -1); // 65535
```

`Integer` và `Long` còn cung cấp phương thức tiện ích như `bitCount`, `highestOneBit`, `lowestOneBit`, `numberOfLeadingZeros`, `numberOfTrailingZeros`, `rotateLeft`, `rotateRight` và so sánh/chia không dấu (`compareUnsigned`, `divideUnsigned`...). Các phương thức thao tác trên **mẫu bit có độ rộng cố định**. Không có nghĩa `Integer`/`Long` tự động trở thành số nguyên không dấu khi gọi chúng.

Một số toán tử như `+`, `-`, `&`, `<<` áp dụng lên primitive sau khi unbox, không phải phương thức instance của wrapper.

## 8. So sánh, `equals`, `compareTo` và các giá trị đặc biệt

### 8.1. Wrapper nguyên

Với `Byte`, `Short`, `Integer`, `Long`, `equals` kiểm tra cùng **lớp wrapper** và cùng giá trị:

```java
Integer a = Integer.valueOf(10);
Integer b = Integer.valueOf(10);
Long c = Long.valueOf(10L);

System.out.println(a.equals(b)); // true
System.out.println(a.equals(c)); // false: khác kiểu wrapper
```

`Integer.compare(x, y)`, `Long.compare(x, y)` hoặc `compareTo` cho thứ tự theo số nguyên có dấu. `Integer.compareUnsigned`/`Long.compareUnsigned` dùng khi cùng mẫu bit phải được đọc như số không dấu.

```java
System.out.println(Integer.compare(-1, 1));        // -1
System.out.println(Integer.compareUnsigned(-1, 1)); // 1
```

Không viết `a - b` làm `Comparator` cho `Integer` vì hiệu có thể overflow, dẫn đến sai thứ tự. Dùng `Integer.compare(a, b)` hoặc `Comparator.comparingInt(...)` tùy tình huống.

### 8.2. `Float` và `Double`: `NaN`, dấu của 0 và thứ tự

Đây là sự khác biệt đáng nhớ giữa toán tử so sánh primitive và hợp đồng wrapper:

```java
double nan = Double.NaN;

System.out.println(nan == nan); // false
System.out.println(
        Double.valueOf(nan).equals(Double.valueOf(nan))); // true

System.out.println(+0.0 == -0.0); // true
System.out.println(
        Double.valueOf(+0.0).equals(Double.valueOf(-0.0))); // false
```

`Double.equals` coi mọi giá trị `NaN` là bằng nhau theo hợp đồng wrapper, nhưng phân biệt `+0.0` với `-0.0`. `Float.equals` có đặc điểm tương ứng. Điều này giúp `equals` và `hashCode` của wrapper có hành vi nhất quán, kể cả trong collection.

`Double.compare(a, b)` và `Float.compare(a, b)` định nghĩa thứ tự phục vụ sorting: `-0.0` thấp hơn `+0.0`, và `NaN` được sắp cao hơn mọi giá trị số, kể cả vô cực dương.

```java
System.out.println(Double.compare(-0.0, +0.0)); // -1
System.out.println(
        Double.compare(Double.NaN, Double.POSITIVE_INFINITY)); // 1
```

Không dùng `Double.compare` để kết luận hai giá trị gần bằng nhau về mặt tính toán. Nếu bài toán cần dung sai, phải đưa ra chính sách so sánh sai số riêng; quan hệ gần bằng có thể **không bắc cầu**, nên không thích hợp làm hợp đồng `equals`/`hashCode` hoặc `Comparator` sắp xếp.

### 8.3. So sánh khác wrapper type

`Integer.valueOf(10).equals(Long.valueOf(10L))` là `false`, dù hai số nguyên biểu diễn cùng giá trị toán học. Nếu cần so sánh hai giá trị kiểu `Number` khác loại, phải xác định quy tắc chuyển đổi trước:

- Với các số nguyên có miền phù hợp, so sánh trên cùng kiểu nguyên rộng hơn có thể hợp lý.
- Chuyển một `long` rất lớn sang `double` có thể **mất chữ số**, khiến hai giá trị khác nhau trở thành cùng một `double`.
- Với số decimal yêu cầu chính xác, phải dùng biểu diễn decimal phù hợp từ nguồn gốc, không cast bừa mọi `Number` sang `double`.

Không có một phương thức `Number.equalsNumericValue` dùng chung cho tất cả subtype và mọi miền giá trị.

## 9. Chuyển đổi qua `xxxValue()` có thể làm mất giá trị

Các wrapper cung cấp phương thức lấy giá trị ở nhiều kiểu nguyên thủy. Chúng thuận tiện nhưng thường có quy tắc giống các phép ép kiểu Java:

```java
Integer n = Integer.valueOf(257);
byte small = n.byteValue(); // 1

Double d = Double.valueOf(-2.9);
int truncated = d.intValue(); // -2

Long large = Long.valueOf(9_007_199_254_740_993L);
double approximate = large.doubleValue();
// approximate mang giá trị 9_007_199_254_740_992
```

Cần phân biệt:

- **Giữ nguyên giá trị:** `Integer.intValue()`, `Long.longValue()` đối với chính kiểu tương ứng, hoặc chuyển integer nhỏ sang kiểu lớn đủ miền.
- **Thu hẹp số nguyên:** `long` → `int`, `int` → `short`/`byte` có thể bỏ các bit cao; thao tác wrapper không tự báo overflow.
- **Thực sang nguyên:** `Double.intValue()`, `Float.longValue()` cắt phần lẻ về 0 khi hữu hạn và trong miền; NaN và giá trị ngoài miền có kết quả đặc biệt theo quy tắc Java.
- **Nguyên sang thực:** `Long.doubleValue()` và `Integer.floatValue()` có thể làm tròn phần bit thấp.

Nếu muốn kiểm tra `long` có vừa miền `int` hay không, dùng `Math.toIntExact(longValue)` thay vì `Integer.valueOf((int) longValue)`:

```java
long quantity = 3_000_000_000L;

// int wrong = (int) quantity; // giá trị âm vì tràn miền int
// int checked = Math.toIntExact(quantity); // ném ArithmeticException
```

Với `BigInteger`, `BigDecimal`, các phương thức `intValueExact()`/`longValueExact()` áp dụng hợp đồng khác để kiểm tra mất thông tin. Sáu primitive wrapper của bài này **không phải** các kiểu decimal/số nguyên độ dài tùy ý.

## 10. Ví dụ Java 17 hoàn chỉnh

Tạo tệp `NumberWrappersDemo.java`:

```java
public class NumberWrappersDemo {
    public static void main(String[] args) {
        Number[] numbers = {
                Byte.valueOf((byte) 7),
                Short.valueOf((short) 300),
                Integer.valueOf(42),
                Long.valueOf(9_000_000_000L),
                Float.valueOf(1.25f),
                Double.valueOf(0.5)
        };

        for (Number value : numbers) {
            System.out.println(
                    value.getClass().getSimpleName() + " = " + value);
        }

        System.out.println("parse hex = " + Integer.parseInt("FF", 16));
        System.out.println("unsigned byte = " + Byte.toUnsignedInt((byte) -1));
        System.out.println("unsigned int = " + Integer.toUnsignedString(-1));
        System.out.println("narrowed 257 = "
                + Integer.valueOf(257).byteValue());

        Integer a = Integer.valueOf(200);
        Integer b = Integer.valueOf(200);
        System.out.println("boxed equals = " + a.equals(b));

        System.out.println("primitive NaN == NaN = "
                + (Double.NaN == Double.NaN));
        System.out.println("wrapper NaN equals = "
                + Double.valueOf(Double.NaN)
                        .equals(Double.valueOf(Double.NaN)));

        System.out.println("primitive signed zero = " + (+0.0 == -0.0));
        System.out.println("wrapper signed zero equals = "
                + Double.valueOf(+0.0).equals(Double.valueOf(-0.0)));

        System.out.println("unsigned compare(-1, 1) = "
                + Integer.compareUnsigned(-1, 1));
    }
}
```

Biên dịch/chạy:

```bash
javac NumberWrappersDemo.java
java NumberWrappersDemo
```

Kết quả:

```text
Byte = 7
Short = 300
Integer = 42
Long = 9000000000
Float = 1.25
Double = 0.5
parse hex = 255
unsigned byte = 255
unsigned int = 4294967295
narrowed 257 = 1
boxed equals = true
primitive NaN == NaN = false
wrapper NaN equals = true
primitive signed zero = true
wrapper signed zero equals = false
unsigned compare(-1, 1) = 1
```

Các giá trị đặc biệt và parse trong ví dụ dùng những API có hành vi được quy định bởi Java; `valueOf(200)` chỉ được kiểm tra qua `equals`, nên kết quả không phụ thuộc cache bên ngoài miền bảo đảm.

## 11. Nên chọn wrapper nào?

| Nhu cầu | Lựa chọn |
| --- | --- |
| Số nguyên nhỏ cần đúng miền -128..127 | `Byte` nếu API/generic yêu cầu đối tượng; `byte` nếu không |
| Số nguyên 16 bit có dấu | `Short`/`short` khi giao thức/định dạng cần |
| Số nguyên thông thường, chỉ số, bộ đếm | `Integer`/`int` nếu vừa miền 32 bit |
| Số nguyên lớn hơn miền `int` | `Long`/`long` nếu vừa miền 64 bit |
| Số thực nhị phân với yêu cầu bộ nhớ thấp, chịu sai số lớn hơn | `Float`/`float` |
| Số thực nhị phân thông thường, cần độ chính xác cao hơn `float` | `Double`/`double` |
| Collection, generic, dữ liệu có thể vắng mặt (`null`) | Wrapper tương ứng, với chính sách xử lý `null` rõ ràng |
| Số nguyên vượt miền `long` | `BigInteger` |
| Số decimal đòi hỏi chữ số thập phân chính xác, quy tắc làm tròn rõ | `BigDecimal` tạo từ nguồn decimal thích hợp |

Không phải lúc nào `Byte`/`Short` tiết kiệm bộ nhớ hơn `Integer` khi sử dụng **đối tượng**: một wrapper có overhead của object và tham chiếu, khác với primitive nằm trong mảng nguyên thủy. Khi cần lưu hàng triệu số không nullable, các mảng `byte[]`, `int[]`, `double[]` thường tránh overhead boxing so với `List<Byte>`, `List<Integer>`, `List<Double>`.

`Float`/`Double` không phải lựa chọn an toàn mặc định cho tiền tệ vì các số decimal như `0.1` không được biểu diễn chính xác dưới dạng nhị phân. Nếu đầu vào là decimal, `BigDecimal` từ chuỗi giúp giữ đúng chữ số nguồn; lưu dữ liệu tiền dưới dạng số nguyên của đơn vị nhỏ nhất cũng là chiến lược khả thi trong miền phù hợp.

## 12. Lỗi phổ biến và cách khắc phục

1. **Dùng `==` cho hai wrapper để so sánh giá trị.** Kết quả có thể phụ thuộc cache và identity. Dùng `equals`, `Objects.equals(a, b)` nếu có `null`, hoặc unbox có kiểm soát.
2. **Unboxing `null`.** `Integer missing = null; int n = missing;` ném `NullPointerException`; kiểm tra trường hợp thiếu trước khi tính.
3. **Nghĩ `valueOf` và `parseXxx` cùng kiểu trả về.** `Integer.parseInt` trả `int`, `Integer.valueOf` trả `Integer`.
4. **Nhầm `Float.MIN_VALUE` là giá trị âm nhất.** Đây là số **dương khác 0 nhỏ nhất**; số hữu hạn âm nhỏ nhất là `-Float.MAX_VALUE`.
5. **Chuyển wrapper qua `xxxValue()` rồi cho rằng không mất dữ liệu.** `Integer.valueOf(257).byteValue()` là `1`, `Long.doubleValue()` có thể mất chính xác.
6. **Để Java tự unbox trong các phép so sánh không rõ ràng.** `Integer` so với `int` có thể unbox; nhưng hai `Integer` với `==` là so sánh tham chiếu. Hãy ghi rõ ý nghĩa bạn muốn.
7. **Giả định mọi `Number` đều có phương thức `valueOf`, `parseInt` hay phép cộng đối tượng.** Các API này thuộc wrapper cụ thể; `Number` chỉ cung cấp giao diện trích giá trị dạng primitive.
8. **Phân tích chuỗi khác cú pháp mà không bắt lỗi.** `Integer.parseInt("1_000")` không được hỗ trợ dù `1_000` là literal Java hợp lệ; parse chuỗi có thể ném `NumberFormatException`.
9. **Bỏ qua `NaN` và vô cực.** `Double.valueOf("NaN")` là hợp lệ theo parser, nhưng có thể trái hợp đồng dữ liệu; dùng `isNaN`, `isInfinite`, `isFinite`.
10. **Nhầm hợp đồng `equals` của số thực wrapper với toán tử `==` của primitive.** Wrapper coi NaN bằng NaN theo `equals` và phân biệt dấu 0; primitive thì ngược lại trong hai trường hợp đó.
11. **Dùng trừ hai số trong comparator.** `return a-b` có thể overflow; dùng `Integer.compare(a,b)`, `Long.compare(a,b)` và comparator phù hợp.
12. **Dùng đối tượng wrapper cho vòng lặp số học nóng mà không cần.** Boxing/unboxing có thể tăng allocation hoặc overhead và tạo nguy cơ `null`; dùng primitive và mảng primitive khi bài toán phù hợp.
13. **Đọc số không dấu như số có dấu.** Mẫu bit `0xFFFFFFFF` là `-1` khi đọc `int` signed, nhưng `"4294967295"` khi gọi `Integer.toUnsignedString(-1)`.
14. **Cho rằng `equals` giữa các subtype `Number` so sánh giá trị toán học.** `Integer(10)` và `Long(10)` không bằng nhau theo `equals`; chọn quy tắc ép kiểu chung một cách an toàn theo dữ liệu.

## 13. Bài tập tự kiểm tra

**Câu 1.** Cho hai biến:

```java
Integer a = Integer.valueOf(200);
Integer b = Integer.valueOf(200);
```

Nên dùng `a == b` hay `a.equals(b)` để kiểm tra cùng giá trị số nguyên?

**Đáp án:** `a.equals(b)`. `==` kiểm tra identity của hai tham chiếu wrapper và không bảo đảm đúng cho mọi giá trị.

**Câu 2.** `Float.MIN_VALUE` âm hay dương? Nếu cần số `float` âm hữu hạn nhỏ nhất về thứ tự số học, viết thế nào?

**Đáp án:** `Float.MIN_VALUE` dương, là số dương khác 0 nhỏ nhất; cận âm hữu hạn là `-Float.MAX_VALUE`.

**Câu 3.** Điều gì xảy ra khi chạy:

```java
Integer count = null;
int n = count;
```

**Đáp án:** Ném `NullPointerException` tại bước unboxing.

**Câu 4.** Hai biểu thức `Integer.parseInt("42")` và `Integer.valueOf("42")` trả về kiểu nào?

**Đáp án:** Lần lượt là primitive `int` và wrapper `Integer`.

**Câu 5.** Tại sao `Double.valueOf(Double.NaN).equals(Double.valueOf(Double.NaN))` là `true`, trong khi `Double.NaN == Double.NaN` là `false`?

**Đáp án:** Wrapper `Double.equals` dùng hợp đồng giá trị đối tượng, coi NaN bằng NaN để có hành vi nhất quán với `hashCode`; toán tử primitive `==` tuân theo quy tắc số học dấu phẩy động, với NaN không bằng bất kỳ số nào, kể cả chính nó.

**Câu 6.** `Byte.toUnsignedInt((byte)-1)` trả gì? Còn `Integer.valueOf(257).byteValue()`?

**Đáp án:** `255` và `1`. Trường hợp đầu diễn giải mẫu 8 bit toàn `1` như không dấu; trường hợp sau thu hẹp `int` 257 xuống 8 bit thấp.

**Câu 7.** `Number n = Long.valueOf(9_007_199_254_740_993L);` rồi gọi `n.doubleValue()` có giữ nguyên giá trị nguyên không?

**Đáp án:** Không. `double` không biểu diễn chính xác số nguyên `2^53 + 1`; giá trị bị làm tròn thành `9_007_199_254_740_992`. Gọi `doubleValue()` không báo lỗi và không giữ thông tin đã mất.

**Câu 8.** Có nên dùng `List<int>` trong Java 17? Vì sao `List<Integer>` có thể chứa `null`?

**Đáp án:** `List<int>` không hợp lệ với Java generic; dùng `List<Integer>` vì generic cần kiểu tham chiếu. `Integer` là reference nên một số collection chấp nhận phần tử `null`, khác với primitive `int`.

**Ghi nhớ:** `Byte`, `Short`, `Integer`, `Long`, `Float`, `Double` là sáu lớp wrapper bất biến của primitive số, cùng kế thừa `Number`. Chúng giúp parse, format, so sánh, thao tác bit và dùng generic/collection. Chọn kiểu theo miền giá trị và độ chính xác; phân biệt primitive với đối tượng, `parseXxx` với `valueOf`, `==` với `equals`, và kiểm tra `null` cũng như dữ liệu mất chính xác khi chuyển đổi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="number-and-wrappers-003">Character không kế thừa Number dù char là kiểu số</a>

<details>
<summary>Xem chi tiết</summary>

# `Character` không kế thừa `Number` dù `char` là kiểu số

## 1. Nghịch lý thường gặp: `char` là integral type, nhưng `Character` không phải `Number`

Java phân chia các kiểu dữ liệu thành **kiểu nguyên thủy (*primitive types*)** và **kiểu tham chiếu (*reference types*)**. Trong nhóm nguyên thủy, `char` được Java Language Specification (JLS) xếp vào **kiểu số nguyên (*integral types*)**, bên cạnh `byte`, `short`, `int` và `long`.

Tuy nhiên, lớp bao (*wrapper class*) của nó — `java.lang.Character` — **không kế thừa `java.lang.Number`**.

Hai mệnh đề sau đều đúng:

1. **`char` là một primitive integral type**: có giá trị số nguyên không dấu từ 0 đến 65.535; tham gia nâng kiểu và phép toán số.
2. **`Character` là một reference type độc lập với `Number`**: nó bọc một đơn vị mã UTF-16; đối tượng `Character` không thể dùng làm một đối tượng `Number`.

Đây không phải mâu thuẫn. **Quan hệ của primitive không quyết định quan hệ kế thừa của wrapper**. `char` và `Character` là hai kiểu khác nhau; việc một lớp “đại diện” cho primitive không có nghĩa nó phải kế thừa cùng một lớp cha với các wrapper số khác.

Ví dụ:

~~~java
char letter = 'A';          // primitive integral, giá trị code unit 65
Character box = letter;     // autoboxing: char -> Character
int code = letter;         // widening primitive: char -> int
~~~

Ba dòng trên là đoạn minh họa, chưa phải class Java chạy độc lập.

Trong khi đó, `Number number = box;` **không biên dịch**: `Character` không là lớp con của `Number`. Việc tự động *unbox* hoặc *box* không cho phép Java tự biến `Character` thành `Integer` rồi gán cho `Number` trong một bước tùy tiện.

## 2. Phân biệt loại kiểu nguyên thủy và hệ phân cấp lớp

### 2.1. Phân loại primitive theo đặc tả Java

Các primitive số của Java gồm:

| Nhóm | Primitive | Ý nghĩa |
|---|---|---|
| **Integral** | `byte` | Số nguyên có dấu 8 bit |
| **Integral** | `short` | Số nguyên có dấu 16 bit |
| **Integral** | `int` | Số nguyên có dấu 32 bit |
| **Integral** | `long` | Số nguyên có dấu 64 bit |
| **Integral** | `char` | Số nguyên **không dấu** 16 bit, biểu diễn một **đơn vị mã UTF-16** |
| **Floating-point** | `float` | Số dấu phẩy động IEEE 754 binary32 |
| **Floating-point** | `double` | Số dấu phẩy động IEEE 754 binary64 |

`boolean` cũng là một primitive nhưng **không thuộc nhóm kiểu số**.

Sự hiện diện của `char` trong *integral types* có ý nghĩa về các quy tắc ngôn ngữ: `char` có thể được mở rộng thành `int`/`long`/`float`/`double` và tham gia nhiều phép toán số học sau khi được nâng kiểu.

Ví dụ:

~~~java
char c = 'A';
int codeUnit = c;            // 65
int nextCodeUnit = c + 1;    // 66, biểu thức có kiểu int
boolean beforeB = c < 'B';   // true
~~~

Đây là minh họa. Java so sánh **giá trị code unit**, không tự áp dụng thứ tự chữ cái theo một ngôn ngữ tự nhiên.

### 2.2. Hệ phân cấp *wrapper classes*

`Number` là **một abstract class** trong `java.lang`. Những lớp như `Byte`, `Short`, `Integer`, `Long`, `Float` và `Double` đều là lớp con của `Number`.

~~~text
java.lang.Object
├── java.lang.Number (abstract class)
│   ├── java.lang.Byte
│   ├── java.lang.Short
│   ├── java.lang.Integer
│   ├── java.lang.Long
│   ├── java.lang.Float
│   └── java.lang.Double
├── java.lang.Character
└── java.lang.Boolean
~~~

Sơ đồ này **chỉ minh họa các lớp liên quan**, không liệt kê toàn bộ các lớp con khác của `Number` như `BigInteger`, `BigDecimal` hoặc các lớp đếm nguyên tử. `Character` và `Boolean` trực tiếp nằm ngoài nhánh `Number`.

| Primitive | Wrapper | Wrapper kế thừa `Number`? |
|---|---|---|
| `byte` | `Byte` | Có |
| `short` | `Short` | Có |
| `int` | `Integer` | Có |
| `long` | `Long` | Có |
| `float` | `Float` | Có |
| `double` | `Double` | Có |
| `char` | `Character` | **Không** |
| `boolean` | `Boolean` | **Không** |

`Character` vẫn có thể được lưu trong một biến kiểu `Object`, và nó hiện thực `Comparable<Character>` cùng các giao diện liên quan, nhưng **nó không phải một `Number`**.

## 3. Mục đích của `Number` và `Character` khác nhau

### 3.1. `Number` cung cấp giao diện chuyển đổi số

`Number` định nghĩa các phương thức như:

- `intValue()`
- `longValue()`
- `floatValue()`
- `doubleValue()`
- `byteValue()`
- `shortValue()`

Chúng cho phép mã làm việc với một **đối tượng số** mà không cần biết lớp cụ thể khi chỉ muốn rút ra một giá trị primitive. Một số phép chuyển đổi có thể mất phần thập phân, tràn/thu hẹp hoặc mất chính xác tùy kiểu gốc và kiểu đích; **`Number` không cam kết mọi phương thức luôn giữ giá trị chính xác**.

Ví dụ:

~~~java
Number integer = Integer.valueOf(65);
Number decimal = Double.valueOf(12.5);

int a = integer.intValue(); // 65
int b = decimal.intValue(); // 12: cắt phần thập phân về 0
~~~

Đây là đoạn minh họa. `Number` không có phương thức `charValue()` chung bắt buộc cho mọi lớp con.

### 3.2. `Character` bọc một đơn vị mã UTF-16

`Character` cung cấp `charValue()` để lấy ra primitive `char` và các phương thức liên quan ký tự Unicode, chẳng hạn:

- `Character.isDigit(...)`: kiểm tra ký tự/chữ số Unicode theo đặc tả API.
- `Character.isLetter(...)`: phân loại chữ cái Unicode.
- `Character.getNumericValue(...)`: trả **giá trị số theo thuộc tính Unicode** khi có, không phải luôn là code unit.
- `Character.digit(..., radix)`: diễn giải ký tự thành chữ số theo cơ số nếu hợp lệ.
- `Character.isHighSurrogate(...)`, `isLowSurrogate(...)`, `toChars(...)`: hỗ trợ làm việc với UTF-16 và code points.

Cách hiểu đúng là: **`Character` quản lý ý nghĩa văn bản và code unit**, còn `Number` là lớp cha của những kiểu đối tượng cung cấp chuyển đổi primitive số.

Ví dụ, `'A'` mang **giá trị code unit là 65**, nhưng `Character.getNumericValue('A')` là **10**, do `A` có giá trị chữ số 10 trong các hệ cơ số từ 11 trở lên. Hai giá trị này giải quyết **hai câu hỏi khác nhau**.

Không được coi `Character.getNumericValue(c)` là phép thay thế chung cho `(int) c`. Với chữ số `'7'`, `(int) '7'` bằng **55** (code unit), còn `Character.getNumericValue('7')` bằng **7** (giá trị số của chữ số).

## 4. Boxing `char` tạo `Character`, không tạo `Number`

**Autoboxing** là chuyển đổi từ primitive sang lớp bao tương ứng khi ngữ cảnh cho phép:

~~~java
char primitive = 'A';
Character wrapper = primitive; // boxing char -> Character
Object asObject = primitive;   // boxing Character rồi widening reference Object
~~~

Trong cả hai trường hợp cần boxing, đối tượng thu được là **`Character`**. Java không box `char` trực tiếp thành `Integer`.

Đối chiếu:

~~~java
int primitiveInt = 65;
Integer integerWrapper = primitiveInt; // int -> Integer
Number asNumber = primitiveInt;        // int -> Integer -> Number
~~~

`Number asNumber = primitiveInt;` hợp lệ nhờ **boxing `int → Integer` rồi widening reference `Integer → Number`**.

Nhưng đoạn sau **không hợp lệ** (các dòng đã comment để bài vẫn hợp lệ về cú pháp):

~~~java
char c = 'A';
Character wrapper = c;

// Number invalid1 = wrapper; // lỗi: Character không phải Number
// Number invalid2 = c;       // lỗi: char không tự box thành Number
// Integer invalid3 = c;      // lỗi: không tự widen char thành int rồi box Integer
~~~

Thứ tự chuyển đổi tự động của Java có giới hạn. Không nên suy từ `int number = c;` hợp lệ rằng `Integer integer = c;` cũng hợp lệ.

Nếu thật sự cần biểu diễn **giá trị số của code unit** dưới dạng `Number`, hãy chuyển `char` thành `int` trước:

~~~java
char c = 'A';
int numericCodeUnit = c;
Number n = numericCodeUnit; // int -> Integer -> Number, giá trị 65
// Hoặc: Number n = (int) c;
~~~

`n` ở đây là một đối tượng `Integer` có giá trị 65, **không phải** `Character`. Việc chuyển đổi này chỉ bảo tồn **giá trị số của một code unit**, chưa nói điều gì về “chữ số” hay toàn bộ Unicode code point.

## 5. Unboxing `Character` có thể kết hợp widening primitive

Ở chiều ngược lại, `Character` có thể *unbox* về `char`, và sau đó primitive `char` có thể **widen** sang `int`, `long`, `float` hoặc `double` trong ngữ cảnh cho phép:

~~~java
Character wrapped = Character.valueOf('A');

char codeUnit = wrapped;     // unbox -> char
int asInt = wrapped;         // unbox char rồi widen -> int
long asLong = wrapped;       // unbox char rồi widen -> long
double asDouble = wrapped;   // unbox char rồi widen -> double
~~~

Đoạn minh họa này hợp lệ. Tuy nhiên, **`Character` không vì thế kế thừa `Number`**. Sự khác nhau là:

- **Chuyển đổi primitive**: định nghĩa bởi hệ thống kiểu ngôn ngữ Java.
- **Kế thừa lớp**: định nghĩa bởi quan hệ `extends`/`implements` của các reference type.

Nếu `wrapped` bằng `null`, biểu thức cần **unboxing** sẽ ném `NullPointerException`, kể cả khi sau đó muốn widen về `int`. Ví dụ `Character absent = null; int value = absent;` ném NPE khi chạy. Bọc trong `Object` cũng không giúp nó trở thành `Number`.

Không nên ép `(Number) (Object) wrapped` để “né lỗi biên dịch”: nếu đối tượng runtime vẫn là `Character`, phép cast tới `Number` sẽ ném `ClassCastException`.

## 6. Ví dụ Java 17+: chứng minh hierarchy và boxing

Tạo tệp `CharacterHierarchyDemo.java`:

~~~java
public class CharacterHierarchyDemo {
    public static void main(String[] args) {
        char letter = 'A';
        Character wrapped = letter;
        Object object = wrapped;

        System.out.println("primitiveChar=" + (int) letter);
        System.out.println("wrapperType="
                + wrapped.getClass().getSimpleName());
        System.out.println("isCharacter=" + (object instanceof Character));
        System.out.println("isNumber=" + (object instanceof Number));
        System.out.println("isComparable=" + (object instanceof Comparable));

        int codeUnit = wrapped;
        Number number = codeUnit;

        System.out.println("numberType="
                + number.getClass().getSimpleName());
        System.out.println("numberValue=" + number.intValue());
        System.out.println("letterCharValue=" + wrapped.charValue());

        System.out.println("charSuperclass="
                + Character.class.getSuperclass().getSimpleName());
        System.out.println("integerSuperclass="
                + Integer.class.getSuperclass().getSimpleName());
    }
}
~~~

Dùng JDK 17 trở lên:

~~~bash
javac CharacterHierarchyDemo.java
java CharacterHierarchyDemo
~~~

Đầu ra:

~~~text
primitiveChar=65
wrapperType=Character
isCharacter=true
isNumber=false
isComparable=true
numberType=Integer
numberValue=65
letterCharValue=A
charSuperclass=Object
integerSuperclass=Number
~~~

`object instanceof Number` trả `false` vì object runtime thật sự là `Character`. Sau khi chuyển `char` sang `int` rồi box vào `Number`, đối tượng runtime trở thành `Integer`; do đó `number.intValue()` trả 65.

`Character.class.getSuperclass()` trả `Object.class`, còn `Integer.class.getSuperclass()` trả `Number.class`. Đây là bằng chứng trực tiếp về **quan hệ lớp**, khác với quy tắc kiểu primitive.

## 7. `Number` trong API, generics và collection

Java generics chỉ nhận **reference types** làm tham số kiểu: `List<Character>` và `List<Number>` hợp lệ; `List<char>` hoặc `List<int>` **không hợp lệ**.

Một collection `List<Number>` nhận các đối tượng thuộc `Number`, nhưng **không nhận `Character`**:

~~~java
import java.util.ArrayList;
import java.util.List;

List<Number> numbers = new ArrayList<>();
numbers.add(Integer.valueOf(65));
numbers.add(Double.valueOf(1.25));

// numbers.add(Character.valueOf('A')); // lỗi biên dịch
// numbers.add('A');                     // không tự box char thành Number
~~~

Đây là đoạn minh họa, chưa phải tệp chạy độc lập.

Nếu muốn giữ **ký tự**, hãy dùng `List<Character>`:

~~~java
List<Character> chars = List.of('A', '7');
~~~

Nếu muốn giữ **giá trị số của code unit**, chuyển sang `int` và dùng `List<Integer>` hoặc `List<Number>`:

~~~java
char c = 'A';
List<Number> codeUnits = new ArrayList<>();
codeUnits.add((int) c); // boxing Integer; giá trị 65
~~~

**Cảnh báo về generics:** `List<Character>` không phải `List<Number>`, và `List<Integer>` cũng không phải `List<Number>` do tính bất biến (*invariance*) của generics, dù `Integer` là một `Number`. Nếu API chỉ **đọc** các số, có thể thiết kế `List<? extends Number>`, nhưng wildcard này cũng **không nhận `List<Character>`**.

### Ví dụ Java 17+ độc lập: collection đúng kiểu

Tạo tệp `CharacterCollectionsDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.List;

public class CharacterCollectionsDemo {
    static int sumAsInt(List<? extends Number> values) {
        int sum = 0;
        for (Number value : values) {
            sum += value.intValue();
        }
        return sum;
    }

    public static void main(String[] args) {
        List<Character> characters = List.of('A', '7');
        List<Integer> codeUnits = new ArrayList<>();
        List<Integer> digitValues = new ArrayList<>();

        for (Character c : characters) {
            codeUnits.add((int) c.charValue());
            digitValues.add(Character.getNumericValue(c));
        }

        System.out.println("characters=" + characters);
        System.out.println("codeUnits=" + codeUnits);
        System.out.println("digitValues=" + digitValues);
        System.out.println("codeUnitSum=" + sumAsInt(codeUnits));
        System.out.println("numericValueSum=" + sumAsInt(digitValues));
    }
}
~~~

~~~bash
javac CharacterCollectionsDemo.java
java CharacterCollectionsDemo
~~~

Đầu ra:

~~~text
characters=[A, 7]
codeUnits=[65, 55]
digitValues=[10, 7]
codeUnitSum=120
numericValueSum=17
~~~

Lớp `sumAsInt` minh họa API chấp nhận `List<? extends Number>`; ta phải đưa vào `List<Integer>` chứ không đưa `List<Character>`. Chương trình còn chứng minh rằng **mã số UTF-16** và **giá trị số của chữ số/ký tự Unicode** khác nhau.

**Lưu ý:** `sumAsInt` cộng `int` thông thường, nên ví dụ chỉ dùng dữ liệu nhỏ; nếu xử lý giá trị lớn, cần chọn kiểu/kiểm tra tràn và chính sách chuyển `Number.intValue()` thích hợp. Mã này không phải hàm cộng chính xác cho mọi lớp con của `Number`, đặc biệt `BigDecimal` có phần lẻ hoặc `Long` quá lớn.

## 8. Vì sao `Character` không cung cấp `intValue()` như `Number`?

`Character` có phương thức `charValue()` vì nhiệm vụ của nó là lưu và truy xuất **một UTF-16 code unit**. Nếu muốn lấy số nguyên, Java đã cung cấp **widening conversion `char → int`**:

~~~java
char letter = 'A';
int codeUnit = letter; // 65
~~~

Và nếu muốn một số có ý nghĩa về **chữ số**, dùng API khác:

~~~java
int unitA = (int) 'A';                    // 65
int numericA = Character.getNumericValue('A'); // 10
int digitA16 = Character.digit('A', 16); // 10
int digitA10 = Character.digit('A', 10); // -1, A không là chữ số cơ số 10

int unit7 = (int) '7';                    // 55
int numeric7 = Character.getNumericValue('7'); // 7
~~~

Đây là minh họa.

Phương thức `Character.getNumericValue` có thể trả `-1` khi ký tự không có giá trị số, hoặc `-2` khi giá trị số không biểu diễn được bằng số nguyên không âm kiểu `int` theo quy ước API. `Character.digit(ch, radix)` dùng tập chữ số phù hợp cơ số đã chỉ định và trả `-1` khi không hợp lệ.

Vì vậy, không thể có một phương thức chung `Number.intValue()` trên `Character` mà luôn diễn đạt đúng “giá trị số” trong mọi mục đích văn bản:

- **Mã UTF-16 của `'A'`** là 65.
- **Giá trị chữ số của `'A'` trong cơ số 16** là 10.
- **Giá trị chữ số của `'A'` trong cơ số 10** là không hợp lệ.
- **Ý nghĩa ngôn ngữ** của `'A'` là một ký tự chữ cái, không phải số lượng 65.

Việc giữ các API tách biệt giúp lập trình viên chọn ý nghĩa mà bài toán thật sự yêu cầu.

## 9. `char` là code unit UTF-16, không phải mọi Unicode code point

Một lý do khác để không đối xử `Character` như một số tổng quát: primitive `char` luôn đúng **16 bit**, nhưng Unicode có các mã điểm lớn hơn `0xFFFF`. Mã điểm ngoài **Basic Multilingual Plane (BMP)** cần **hai đơn vị mã UTF-16** (*surrogate pair*), không thể lưu trọn trong một `char` hoặc một `Character`.

Ví dụ emoji 😀 có mã điểm `U+1F600` và được lưu trong chuỗi UTF-16 bằng **hai** code unit:

- High surrogate: `0xD83D`.
- Low surrogate: `0xDE00`.

`String.length()` đếm code unit, không đếm mã điểm Unicode hoặc số ký tự hiển thị. Nếu muốn tính toán dựa trên mã điểm, hãy dùng `int` và API `codePointAt`/`codePoints`.

### Ví dụ Java 17+ chạy độc lập: một code point có hai `Character`

Tạo tệp `CharacterUnicodeDemo.java`:

~~~java
public class CharacterUnicodeDemo {
    public static void main(String[] args) {
        String smile = new String(Character.toChars(0x1F600));

        char high = smile.charAt(0);
        char low = smile.charAt(1);
        int codePoint = smile.codePointAt(0);
        Number numericCodePoint = codePoint;

        System.out.println("utf16Units=" + smile.length());
        System.out.println("codePoints="
                + smile.codePointCount(0, smile.length()));
        System.out.printf("high=%04X%n", (int) high);
        System.out.printf("low=%04X%n", (int) low);
        System.out.println("validPair="
                + Character.isSurrogatePair(high, low));
        System.out.printf("codePoint=U+%X%n", codePoint);
        System.out.println("numericCodePoint="
                + numericCodePoint.intValue());
    }
}
~~~

~~~bash
javac CharacterUnicodeDemo.java
java CharacterUnicodeDemo
~~~

Đầu ra:

~~~text
utf16Units=2
codePoints=1
high=D83D
low=DE00
validPair=true
codePoint=U+1F600
numericCodePoint=128512
~~~

Lưu ý `numericCodePoint` trong ví dụ thuộc kiểu `Number` vì **mã điểm đã được biểu diễn bằng primitive `int` rồi box thành `Integer`**. Nó không phải một đối tượng `Character`. `Character.isSurrogatePair` được dùng để giải thích sự ghép cặp hai code unit, không phải phương thức của `Number`.

**Một ký tự cảm nhận của người dùng (grapheme cluster)** đôi khi còn gồm nhiều mã điểm Unicode kết hợp, nên ngay cả `String.codePointCount` cũng không nhất thiết bằng số ký tự hiển thị.

## 10. Lỗi ép kiểu và kiểm tra bằng `instanceof`

Nếu có một biến `Object` đến từ API tổng quát, bạn có thể kiểm tra kiểu runtime:

~~~java
Object value = Character.valueOf('A');

boolean character = value instanceof Character; // true
boolean number = value instanceof Number;       // false
~~~

Đây là minh họa. Điều kiện `value instanceof Number` không tự chuyển kiểu; nó chỉ kiểm tra đối tượng runtime có thuộc hệ phân cấp `Number` hay không.

Thử ép kiểu theo đường vòng:

~~~java
Object value = Character.valueOf('A');
// Number n = (Number) value; // biên dịch được, nhưng ném ClassCastException
~~~

`(Number) value` về mặt cú pháp hợp lệ vì biến nguồn là `Object`, nhưng đối tượng runtime là `Character`, không phải `Number`; khi thực thi cast sẽ ném `ClassCastException`. **Không nên** sử dụng cast để “biến Character thành Number”.

Nếu API nhận nhiều loại đối tượng và bạn muốn chuyển `Character` thành code unit dạng số, hãy kiểm tra từng kiểu:

~~~java
static int toCodeUnit(Object value) {
    if (value instanceof Character c) {
        return c.charValue(); // widening char -> int
    }
    throw new IllegalArgumentException("Expected Character");
}
~~~

Đây là một **phương thức minh họa** có thể đặt trong class. Nó sử dụng pattern matching cho `instanceof` có sẵn từ Java 16, nên phù hợp Java 17+. Nó minh họa **chuyển giá trị**, không thay đổi phân cấp kế thừa của đối tượng đầu vào.

## 11. Các lỗi phổ biến

| Nhầm lẫn | Cách hiểu đúng |
|---|---|
| “`char` là số nên `Character extends Number`.” | Không: primitive integral type và hierarchy reference type là hai cơ chế khác nhau. |
| “`Number n = 'A';` hợp lệ vì `'A'` có giá trị 65.” | Không: autoboxing `char` tạo `Character`, không tạo `Integer`. |
| “`Object obj = 'A';` tạo `Integer`.” | Không: đối tượng được box là `Character`. |
| “`int code = characterWrapper;` không hợp lệ vì wrapper không phải Number.” | Vẫn hợp lệ: unbox `Character → char` rồi widen `char → int`. |
| “`Integer number = characterWrapper;` sẽ tự widen và box.” | Không có chuyển đổi tự động kết hợp theo cách đó; hãy unbox/widen rõ ràng. |
| “`Character` có `intValue()` như `Number`.” | Không: dùng `charValue()` để lấy code unit hoặc các API Unicode nếu cần giá trị chữ số. |
| “`Character.getNumericValue('A')` bằng 65.” | Sai: nó trả 10; `(int) 'A'` mới bằng 65. |
| “`List<Number>` nhận mọi wrapper primitive.” | Không: `Character` và `Boolean` không thuộc `Number`. |
| “`List<Integer>` có thể gán vào `List<Number>`.” | Không: generics bất biến; có thể dùng `List<? extends Number>` cho API đọc phù hợp. |
| “Một `Character` có thể chứa mọi Unicode code point.” | Không: `Character` bọc một UTF-16 code unit; mã điểm ngoài BMP cần hai code unit. |
| “Cast `Object` chứa `Character` sang `Number` sẽ chuyển được mã.” | Không: ném `ClassCastException` khi chạy. |

## 12. Khi nào dùng `Character`, `Integer` hoặc `Number`?

**Chọn `char`/`Character`** khi thao tác một code unit UTF-16, ví dụ so sánh dấu phân cách `':'`, lưu ký tự BMP đơn giản hoặc dùng API nhận `char`. `Character` phù hợp khi ngữ cảnh yêu cầu tham chiếu, generics hoặc cho phép `null`; `char` tránh boxing khi chỉ cần primitive.

**Chọn `int`/`Integer`** khi thao tác **giá trị số của code unit**, mã điểm Unicode hoặc một số nguyên thông thường vừa miền. Khi xử lý Unicode code point, API kiểu `int` rộng hơn `char` và có thể chứa `U+1F600`. Nhưng cần phân biệt code point với các đơn vị mã UTF-16 và grapheme cluster.

**Chọn `Number`** khi API thực sự cần đa hình giữa các **đối tượng số**, chẳng hạn nhận cả `Integer` và `Double` để đọc dưới dạng primitive theo hợp đồng rõ ràng. `Number` là abstraction rộng, không tự cung cấp số học chính xác phổ quát hoặc đảm bảo các phép chuyển đổi không mất dữ liệu.

**Chọn `String`** cho văn bản hoàn chỉnh (tên, câu, từ, biểu tượng), thay vì cố ép mỗi đơn vị `char` thành một `Number`. Nếu cần chuyển dữ liệu Unicode sang byte, sử dụng charset như UTF-8; primitive cast không phải mã hóa ký tự.

### Checklist trước khi chuyển giữa “ký tự” và “số”

1. Giá trị đang là **primitive `char`** hay **object `Character`**?
2. Mục tiêu là **mã đơn vị UTF-16**, **mã điểm Unicode**, hay **giá trị chữ số**?
3. Có đang dùng API/collection chỉ nhận `Number` không? Nếu có, hãy biểu diễn số bằng `int`/`Integer` theo ý nghĩa thích hợp.
4. Có `null` ở wrapper không? Unboxing có thể ném `NullPointerException`.
5. Có code point ngoài BMP không? Một `Character` sẽ không chứa trọn mã điểm ấy.
6. Có nguy cơ mất dữ liệu khi cast sang `byte`/`short` hoặc đổi thành `float` không? Kiểm tra miền và yêu cầu chính xác.
7. Kiểm tra ở cả **thời điểm biên dịch** (gán/overload/generics) và **thời điểm chạy** (`instanceof`/`ClassCastException`).

## 13. Tự kiểm tra kiến thức

1. `char` được xếp vào nhóm primitive nào trong Java? Miền giá trị của nó?
2. `Character` kế thừa trực tiếp từ lớp nào? `Integer` kế thừa trực tiếp từ lớp nào?
3. Tại sao `Number n = Character.valueOf('A');` không biên dịch?
4. Tại sao `int code = Character.valueOf('A');` có thể biên dịch, dù `Character` không phải `Number`?
5. `(int) '7'` khác `Character.getNumericValue('7')` ra sao?
6. Muốn thêm mã code unit của `'A'` vào `List<Number>`, bạn phải làm gì?
7. Vì sao emoji `U+1F600` không lưu trong một `Character` duy nhất?
8. `Object o = Character.valueOf('A');` thì `o instanceof Number` là `true` hay `false`? Vì sao?
9. `Character c = null; int n = c;` thất bại khi biên dịch hay khi chạy?
10. Có thể dùng `Number.intValue()` để xử lý tất cả số thực mà không mất dữ liệu không?

**Đáp án gợi ý:** (1) Kiểu nguyên thủy integral, 0..65535. (2) `Object` và `Number`. (3) `Character` không nằm dưới `Number`. (4) Unboxing `Character → char` rồi widening `char → int`. (5) 55 (code unit) so với 7 (giá trị chữ số). (6) Chuyển `char` thành `int` trước rồi box `Integer`, ví dụ `numbers.add((int) 'A')`. (7) Nó cần hai code unit surrogate của UTF-16. (8) `false`. (9) Biên dịch được nhưng ném `NullPointerException` khi unbox. (10) Không; phép chuyển `Number.intValue()` có thể mất phần thập phân hoặc vượt miền đích.

**Ghi nhớ:** **`char` là một kiểu số nguyên nguyên thủy**, nhưng **`Character` không kế thừa `Number`**. Primitive numeric promotion, autoboxing/unboxing, kế thừa lớp và ý nghĩa Unicode là **những cơ chế độc lập**. Khi cần số, chuyển **giá trị `char`** sang `int`/`Integer` một cách có chủ ý; khi cần ký tự, giữ `Character` hoặc dùng `String`/API Unicode phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="number-and-wrappers-004">BigInteger, BigDecimal và các lớp Number khác trong JDK</a>

<details>
<summary>Xem chi tiết</summary>

# BigInteger, BigDecimal và các lớp Number khác trong JDK

Java có các kiểu nguyên thủy `byte`, `short`, `int`, `long`, `float`, `double` để lưu số trong miền và độ chính xác hữu hạn. Khi những kiểu này không đáp ứng yêu cầu, JDK cung cấp các **lớp đối tượng biểu diễn số**: wrapper như `Integer`/`Double`, số nguyên độ lớn tùy ý `BigInteger`, số thập phân độ chính xác tùy ý `BigDecimal`, cùng các bộ đếm phục vụ lập trình đồng thời như `AtomicInteger`, `AtomicLong` và `LongAdder`.

Nhiều lớp trong số đó kế thừa lớp trừu tượng `java.lang.Number`, nhưng **cùng là Number không có nghĩa chúng có cùng miền giá trị, độ chính xác, tính bất biến, quy tắc bằng nhau hay cách thực hiện phép toán**. Cần chọn kiểu theo ngữ nghĩa dữ liệu và hiểu rõ giới hạn của mỗi API.

## 1. Bức tranh tổng quan

| Nhóm | Các lớp tiêu biểu | Mục đích chính | Những điều cần lưu ý |
| --- | --- | --- | --- |
| Primitive | `byte`, `short`, `int`, `long`, `float`, `double` | Phép toán số học trực tiếp bằng toán tử | Miền cố định; `float`/`double` có sai số biểu diễn |
| Wrapper của primitive số | `Byte`, `Short`, `Integer`, `Long`, `Float`, `Double` | Biểu diễn số bằng đối tượng, generic, collection, boxing | Bất biến; chuyển đổi và so sánh cần để ý kiểu |
| Số nguyên lớn | `java.math.BigInteger` | Số nguyên chính xác vượt `long` | Bất biến; giới hạn thực tế là tài nguyên bộ nhớ/CPU |
| Số thập phân lớn | `java.math.BigDecimal` | Số thập phân chính xác theo cách biểu diễn thập phân và quy tắc làm tròn | Bất biến; phân biệt `scale`, `precision`, `equals` và `compareTo` |
| Bộ đếm đồng thời | `AtomicInteger`, `AtomicLong` | Cập nhật giá trị số nguyên với thao tác nguyên tử | Có thể thay đổi trạng thái; không phải số lớn tùy ý |
| Bộ cộng dồn đồng thời | `LongAdder`, `DoubleAdder`, `LongAccumulator`, `DoubleAccumulator` | Tổng/bộ tích lũy dưới cạnh tranh luồng cao | Có trạng thái; giá trị đọc có thể không phải snapshot nguyên tử |

Những lớp trên nằm trong các package khác nhau: wrapper và `Number` ở `java.lang`, hai lớp lớn ở `java.math`, còn nhóm atomic/adder/accumulator ở `java.util.concurrent.atomic`.

## 2. java.lang.Number là gì?

`Number` là một **lớp trừu tượng** đại diện cho những đối tượng có thể cung cấp giá trị dưới dạng các kiểu số nguyên thủy. Bốn phương thức trừu tượng quan trọng là:

~~~java
public abstract int intValue();
public abstract long longValue();
public abstract float floatValue();
public abstract double doubleValue();
~~~

Ngoài ra `Number` có `byteValue()` và `shortValue()` dạng phương thức cụ thể, về cơ bản dựa trên chuyển đổi thu hẹp từ `intValue()` trừ khi lớp con cung cấp cách triển khai riêng.

Bạn có thể dùng tham chiếu `Number` cho nhiều loại đối tượng:

~~~java
Number count = Integer.valueOf(42);
Number pi = Double.valueOf(3.14);
Number huge = new java.math.BigInteger("12345678901234567890");
Number amount = new java.math.BigDecimal("19.95");
~~~

Các phương thức `intValue()`, `longValue()`, `floatValue()`, `doubleValue()` hỗ trợ chuyển đổi **giá trị**, nhưng không tự đảm bảo chuyển đổi chính xác hay bảo toàn toàn bộ miền số. Ví dụ, `new BigDecimal("1.99").intValue()` trả 1: phần thập phân bị bỏ đi.

**Number không cung cấp phép toán đa hình tổng quát**. Một biến `Number` không có phương thức `add(Number)` trong lớp cơ sở, và Java không cho áp dụng toán tử `+` trực tiếp giữa hai tham chiếu `Number` theo nghĩa số học tổng quát.

~~~java
Number a = Integer.valueOf(2);
Number b = Long.valueOf(3);
// Number result = a + b; // Lỗi: không có toán tử cộng chung cho Number
~~~

Cần quyết định toán học cụ thể (số nguyên chính xác, số thực dấu phẩy động, thập phân...) trước khi thực hiện phép tính. Không nên tự động đưa mọi `Number` về `double` rồi tính nếu chương trình đòi bảo toàn chữ số.

### 2.1. Number là kiểu tham chiếu, không phải kiểu số nguyên thủy

Bạn không thể viết `Number x = 42.5 + 1.0` rồi kỳ vọng Number tự chọn một hệ số học đa hình. Biểu thức nguyên thủy được tính trước bằng `double` và **boxing** thành `Double` khi được gán vào `Number`.

~~~java
Number x = 42;      // int -> Integer (boxing) -> Number
Number y = 3.5;     // double -> Double (boxing) -> Number
System.out.println(x.getClass().getSimpleName()); // Integer
System.out.println(y.getClass().getSimpleName()); // Double
~~~

Java hỗ trợ boxing tự động cho primitive và wrapper tương ứng, nhưng **không tự động boxing `int` thành `BigInteger`** hoặc `double` thành `BigDecimal`. Hai lớp này cần factory/constructor riêng.

### 2.2. Number không hàm ý bất biến

`Integer`, `Long`, `BigInteger` và `BigDecimal` bất biến: phép tính tạo đối tượng kết quả mới. Nhưng `AtomicInteger`/`LongAdder` cũng là `Number` và có trạng thái thay đổi.

Vì thế, nếu API nhận `Number`, đừng mặc định rằng giá trị của đối tượng luôn giữ nguyên giữa hai lần gọi `doubleValue()`. Mức đồng thời và độ nhất quán phải theo hợp đồng của lớp cụ thể.

## 3. BigInteger: số nguyên độ lớn tùy ý

`BigInteger` biểu diễn số nguyên có dấu với độ chính xác tùy ý theo giới hạn khả thi của bộ nhớ/CPU và giới hạn triển khai. Nó phù hợp khi `long` (miền -2^63 đến 2^63-1) không đủ lớn: hệ đếm tổ hợp, mật mã học, số nguyên cực lớn, thuật toán cần kết quả nguyên chính xác.

Tạo đối tượng:

~~~java
import java.math.BigInteger;

BigInteger a = new BigInteger("123456789012345678901234567890");
BigInteger b = BigInteger.valueOf(42L);
BigInteger zero = BigInteger.ZERO;
BigInteger one = BigInteger.ONE;
BigInteger ten = BigInteger.TEN;
~~~

Dùng chuỗi thập phân để tạo số quá miền `long`. `BigInteger.valueOf(long)` tiện với giá trị `long` có sẵn, nhưng đầu vào của nó vẫn bị giới hạn bởi `long`. Không có constructor `new BigInteger(long)`.

### 3.1. Phép toán trên BigInteger

Vì là đối tượng, `BigInteger` không hỗ trợ toán tử `+`, `-`, `*`, `/` như primitive. Sử dụng phương thức:

| Mục tiêu | Phương thức |
| --- | --- |
| Cộng, trừ | `add(...)`, `subtract(...)` |
| Nhân, chia lấy phần nguyên | `multiply(...)`, `divide(...)` |
| Số dư | `remainder(...)`; `mod(...)` với mô-đun dương |
| Lũy thừa | `pow(int exponent)` (số mũ nguyên không âm) |
| UCLN | `gcd(...)` |
| Dịch bit | `shiftLeft(n)`, `shiftRight(n)` |
| Lũy thừa mô-đun | `modPow(exponent, modulus)` |
| So sánh | `compareTo(...)`, `equals(...)` |

~~~java
BigInteger a = new BigInteger("100000000000000000000");
BigInteger b = BigInteger.valueOf(3);
BigInteger sum = a.add(b);
BigInteger quotient = a.divide(b);
BigInteger remainder = a.remainder(b);
BigInteger square = b.pow(2);
~~~

Đây là đoạn minh họa cần đặt trong lớp Java và có import `java.math.BigInteger`. Với `divide`, thương là **số nguyên**, phần dư bị tách riêng. Nó không tạo ra phân số hay số thập phân như `BigDecimal`. Chia cho số 0 ném `ArithmeticException`.

**Phân biệt `remainder` và `mod`:** `remainder` có dấu theo phép chia số nguyên; `mod` yêu cầu mô-đun dương và trả kết quả không âm. Đừng thay thế vô điều kiện hai phương thức trong thuật toán xử lý số âm.

### 3.2. Bất biến và phép cộng bị bỏ quên

~~~java
BigInteger n = BigInteger.TEN;
n.add(BigInteger.ONE);        // Kết quả mới bị bỏ đi; n vẫn bằng 10
n = n.add(BigInteger.ONE);    // n trỏ đến đối tượng bằng 11
~~~

Lỗi tương tự xảy ra với `BigDecimal`. Trong JDK, cả hai đều là đối tượng **immutable**, nên bản thân lời gọi `add` không sửa đối tượng cũ.

### 3.3. Chuyển về primitive có thể làm mất dữ liệu

~~~java
BigInteger huge = BigInteger.ONE.shiftLeft(40); // 2^40
int truncated = huge.intValue();                // 0, chỉ còn 32 bit thấp
// int exact = huge.intValueExact();            // ném ArithmeticException
~~~

Các API an toàn hơn khi yêu cầu giữ nguyên giá trị gồm `intValueExact()`, `longValueExact()`, `shortValueExact()` và `byteValueExact()`. Chúng ném `ArithmeticException` nếu số nguyên không vừa kiểu đích.

Chuyển về `float` hoặc `double` có thể làm tròn hoặc thành vô cực với số quá lớn. Vì vậy, nếu cần giữ số nguyên chính xác, đừng lưu kết quả BigInteger vào `double` để “tiện tính tiếp”.

## 4. BigDecimal: số thập phân chính xác theo unscaled value và scale

`BigDecimal` biểu diễn một số thập phân dưới dạng **số nguyên không giới hạn độ lớn mang dấu (unscaled value)** kết hợp với **scale**:

\[
\text{value} = \text{unscaledValue} \times 10^{-\text{scale}}
\]

Ví dụ:

- `new BigDecimal("12.34")` có `unscaledValue() = 1234` và `scale() = 2`.
- `new BigDecimal("2.00")` có `unscaledValue() = 200` và `scale() = 2`.
- `new BigDecimal("2.0")` có `unscaledValue() = 20` và `scale() = 1`.
- Hai giá trị `2.00` và `2.0` giống nhau về số học, nhưng **biểu diễn scale khác nhau**.

`BigDecimal` giúp biểu diễn **chính xác giá trị thập phân đã nhập** và thực hiện các phép toán có kiểm soát độ chính xác/làm tròn. “Độ chính xác tùy ý” không có nghĩa mọi phép chia đều cho kết quả thập phân hữu hạn, hoặc mọi phép tính đều vô hạn tài nguyên và không cần chính sách làm tròn.

### 4.1. Tạo BigDecimal: ưu tiên chuỗi thập phân

~~~java
import java.math.BigDecimal;

BigDecimal a = new BigDecimal("0.1");    // Giá trị thập phân 0.1 chính xác
BigDecimal b = new BigDecimal("0.2");    // Giá trị thập phân 0.2 chính xác
BigDecimal sum = a.add(b);               // Chính xác 0.3
BigDecimal c = BigDecimal.valueOf(0.1);  // Tạo từ Double.toString(0.1): "0.1"
~~~

Đặc biệt cần hiểu:

~~~java
BigDecimal exactDecimal = new BigDecimal("0.1");
BigDecimal fromBinary = new BigDecimal(0.1);
BigDecimal fromValueOf = BigDecimal.valueOf(0.1);
~~~

`new BigDecimal(0.1)` nhận trực tiếp **giá trị nhị phân double** của literal 0.1 (vốn không chính xác bằng 0.1 thập phân), nên nó biểu diễn giá trị nhị phân đó ở dạng thập phân rất dài. `BigDecimal.valueOf(0.1)` dùng biểu diễn chuỗi chuẩn của `double` rồi chuyển sang `BigDecimal`, nên trong ví dụ cho giá trị `0.1` thập phân mong muốn.

Điều này không có nghĩa `BigDecimal.valueOf(double)` khôi phục được mọi thông tin thập phân gốc đã mất khi chuyển sang `double`. **Khi đầu vào ban đầu là chuỗi số thập phân, hãy parse trực tiếp chuỗi đó thành BigDecimal** để tránh mất độ chính xác trước bước chuyển đổi.

Với dữ liệu nguyên đã có trong `long`, có thể dùng `BigDecimal.valueOf(long)`; với `BigInteger`, dùng `new BigDecimal(bigInteger)`. Không có chuyển đổi tự động từ primitive sang `BigDecimal`.

### 4.2. Phép toán và chính sách làm tròn

Những phương thức cơ bản:

~~~java
BigDecimal price = new BigDecimal("19.95");
BigDecimal quantity = new BigDecimal("3");
BigDecimal total = price.multiply(quantity);    // 59.85
BigDecimal discounted = total.subtract(new BigDecimal("1.50")); // 58.35
~~~

`add`, `subtract` và `multiply` có thể cho kết quả thập phân chính xác với các toán hạng đã biểu diễn, nếu không giới hạn thêm bằng `MathContext`. **Chia** phức tạp hơn: nhiều thương không có khai triển thập phân hữu hạn, điển hình `1 / 3`.

~~~java
BigDecimal numerator = BigDecimal.ONE;
BigDecimal denominator = new BigDecimal("3");

// numerator.divide(denominator); // ArithmeticException: thương không kết thúc
BigDecimal ratio = numerator.divide(
        denominator, 2, java.math.RoundingMode.HALF_UP); // 0.33
~~~

Khi cần chỉ rõ số chữ số sau dấu thập phân, dùng `divide(divisor, scale, roundingMode)` hoặc `setScale(scale, roundingMode)`. Khi muốn giới hạn **số chữ số có nghĩa**, dùng `MathContext`:

~~~java
import java.math.MathContext;
import java.math.RoundingMode;

MathContext context = new MathContext(5, RoundingMode.HALF_EVEN);
BigDecimal third = BigDecimal.ONE.divide(new BigDecimal("3"), context);
// Giá trị: 0.33333 (5 chữ số có nghĩa)
~~~

Cẩn thận phân biệt:

- **Scale:** số chữ số thập phân theo phép biểu diễn `unscaled × 10^-scale`; có thể âm.
- **Precision:** số chữ số có nghĩa của phần unscaled biểu diễn bằng chữ số thập phân (bỏ qua dấu).
- **RoundingMode:** luật xử lý phần phải bỏ đi, ví dụ `HALF_UP` hay `HALF_EVEN`.
- **MathContext:** precision và rounding mode áp dụng khi thực hiện những phép tính nhận context.

Ví dụ `new BigDecimal("123.45").setScale(1, RoundingMode.HALF_UP)` trả `123.5`. Trong ứng dụng tài chính, một scale 2 và một rounding mode được thống nhất phải đến từ **quy định nghiệp vụ, loại tiền, hợp đồng hoặc chính sách hệ thống**, không phải luôn mặc định một cách tùy ý.

### 4.3. equals khác compareTo

Đây là bẫy quan trọng nhất của `BigDecimal` khi đưa vào collection:

~~~java
BigDecimal a = new BigDecimal("2.0");
BigDecimal b = new BigDecimal("2.00");

System.out.println(a.equals(b));       // false
System.out.println(a.compareTo(b));    // 0
~~~

`equals` đòi cùng **giá trị số và scale**; `compareTo` so sánh theo **giá trị số**, bỏ qua khác biệt scale. Vì thế:

- `HashSet<BigDecimal>` dùng `equals`/`hashCode` nên có thể chứa cả `2.0` và `2.00` như hai phần tử.
- `TreeSet<BigDecimal>` theo thứ tự tự nhiên `compareTo` sẽ coi chúng là cùng một khóa, nên chỉ giữ một phần tử.
- Để kiểm tra hai BigDecimal bằng nhau về **mặt số học**, dùng `a.compareTo(b) == 0`. Để kiểm tra cùng biểu diễn (bao gồm scale), dùng `a.equals(b)`.
- `stripTrailingZeros()` có thể chuẩn hóa phần zero cuối nhưng cũng có thể làm scale âm hoặc đổi dạng chuỗi sang ký hiệu khoa học, ví dụ số nguyên có nhiều zero. Không tự động chuẩn hóa trên toàn hệ thống nếu scale mang ý nghĩa nghiệp vụ.

Hai collection có hành vi khác nhau là kết quả từ hợp đồng API, không phải lỗi Java.

### 4.4. Chuyển BigDecimal sang kiểu nguyên

~~~java
BigDecimal amount = new BigDecimal("1.99");
int truncated = amount.intValue();    // 1: bỏ phần thập phân
// int exact = amount.intValueExact(); // ném ArithmeticException
~~~

`intValue()` có thể bỏ phần lẻ và/hoặc mất bit cao nếu giá trị ngoài miền. `intValueExact()` yêu cầu vừa miền kiểu `int` **và không có phần lẻ khác 0**. Các phương thức `longValueExact()` và `toBigIntegerExact()` tương tự khi cần bảo toàn số nguyên.

`toBigInteger()` không làm tròn: nó bỏ phần lẻ theo hướng về 0. Nếu phải làm tròn rồi lấy số nguyên, hãy chọn `setScale(0, roundingMode)` trước và sau đó chuyển đổi exact theo yêu cầu.

### 4.5. Hiển thị BigDecimal

`toString()` có thể dùng ký hiệu số mũ; `toPlainString()` không dùng ký hiệu số mũ; `toEngineeringString()` có quy tắc khác phù hợp biểu diễn kỹ thuật. Các phương thức này liên quan **chuỗi hiển thị**, không làm thay đổi đối tượng.

Đừng dùng việc format chuỗi hoặc làm tròn khi hiển thị như bằng chứng rằng tính toán nội bộ đã chính xác đến số chữ số đó. Khi xuất dữ liệu thanh toán, cũng cần quy tắc về scale, làm tròn và định dạng theo miền ứng dụng.

## 5. Những lớp Number khác trong JDK

### 5.1. Primitive wrapper: Byte, Short, Integer, Long, Float, Double

Sáu wrapper đều kế thừa `Number`; chúng đại diện cho giá trị primitive tương ứng nhưng là **đối tượng bất biến**. Chúng cần thiết khi dùng generics/collection hoặc khi API yêu cầu kiểu tham chiếu:

~~~java
java.util.List<Integer> ids = java.util.List.of(1, 2, 3);
Number first = ids.get(0);
long converted = first.longValue();
~~~

Các wrapper cung cấp factory như `Integer.valueOf(...)`, parser như `Integer.parseInt(...)`, phương thức chuyển đổi và các hằng MIN/MAX. Chúng có cùng giới hạn số học của primitive nền:

- `Integer`/`Long` không thay thế được `BigInteger` nếu cần số nguyên vượt miền.
- `Float`/`Double` vẫn có sai số nhị phân, NaN và vô cực; là wrapper không biến phép tính thành thập phân chính xác.
- `equals` của wrapper quan tâm **kiểu đối tượng**, ví dụ `Integer.valueOf(1).equals(Long.valueOf(1L))` là `false` dù số học cùng 1.
- Boxing và unboxing không phải lúc nào cũng miễn phí, và unboxing một tham chiếu `null` có thể gây `NullPointerException`.

Đối với danh sách `List<Number>` có nhiều kiểu con, đừng giả định mọi phần tử sẽ so sánh bằng nhau bằng `equals` nếu chúng mang giá trị toán học bằng nhau.

### 5.2. AtomicInteger và AtomicLong

`java.util.concurrent.atomic.AtomicInteger` và `AtomicLong` kế thừa `Number`, nhưng có trạng thái **mutable** và các phương thức cập nhật nguyên tử (atomic), ví dụ `incrementAndGet()`, `getAndAdd(...)` và `compareAndSet(...)`.

~~~java
import java.util.concurrent.atomic.AtomicInteger;

AtomicInteger requests = new AtomicInteger(7);
int updated = requests.incrementAndGet(); // 8
Number asNumber = requests;
int snapshot = asNumber.intValue();       // 8 tại thời điểm đọc
~~~

Chúng phù hợp cho counter/biến trạng thái cần cập nhật đồng thời trong miền `int` hoặc `long`, không phải “BigInteger có thể tự cập nhật”. Phép tăng có thể tràn miền như số nguyên primitive; nó không tự mở rộng thành số lớn.

### 5.3. LongAdder, DoubleAdder và Accumulator

Nhóm `LongAdder`/`DoubleAdder` được thiết kế để cộng dồn hiệu quả khi có nhiều luồng cạnh tranh. `LongAccumulator` và `DoubleAccumulator` cho phép phép tích lũy theo một hàm kết hợp đã chỉ định cùng giá trị khởi tạo.

~~~java
import java.util.concurrent.atomic.LongAdder;

LongAdder hits = new LongAdder();
hits.add(10);
hits.increment();

Number n = hits;
System.out.println(n.longValue()); // 11 trong ví dụ chạy tuần tự
~~~

`LongAdder.sum()` lấy một giá trị quan sát được, nhưng **không phải snapshot nguyên tử** khi các luồng khác đang cập nhật. Nó hợp cho thống kê/counter throughput cao, không nên được xem như thay thế trực tiếp cho giao dịch cần kiểm tra-cập nhật chính xác cùng thời điểm.

`DoubleAdder` tích lũy số thực nhị phân nên kết quả vẫn chịu sai số làm tròn và có thể phụ thuộc thứ tự cập nhật. Nó không thay `BigDecimal` trong bài toán thập phân chính xác.

Cả nhóm atomic lẫn adder chứng minh một điều cốt lõi: **kế thừa Number không đồng nghĩa đối tượng immutable hay phép đọc luôn phản ánh một trạng thái toàn cục cố định**.

## 6. Ví dụ chạy độc lập bằng Java 17+

Tạo tệp **NumberFamiliesDemo.java**:

~~~java
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public class NumberFamiliesDemo {
    public static void main(String[] args) {
        Number boxed = Integer.valueOf(42);
        System.out.println("Number intValue: " + boxed.intValue());

        BigInteger large = new BigInteger("123456789012345678901234567890");
        System.out.println("BigInteger + 10: " + large.add(BigInteger.TEN));
        System.out.println("2^100 bits: " + BigInteger.ONE.shiftLeft(100).bitLength());
        System.out.println("2^40 intValue: " + BigInteger.ONE.shiftLeft(40).intValue());
        try {
            BigInteger.ONE.shiftLeft(40).intValueExact();
        } catch (ArithmeticException ex) {
            System.out.println("BigInteger exact conversion: overflow");
        }

        BigDecimal decimalSum = new BigDecimal("0.1").add(new BigDecimal("0.2"));
        System.out.println("decimal 0.1 + 0.2: " + decimalSum.toPlainString());
        System.out.println("from double exact? "
                + new BigDecimal(0.1).equals(new BigDecimal("0.1")));
        System.out.println("valueOf double: "
                + BigDecimal.valueOf(0.1).toPlainString());

        BigDecimal one = new BigDecimal("2.0");
        BigDecimal two = new BigDecimal("2.00");
        System.out.println("equals with scale: " + one.equals(two));
        System.out.println("compareTo with scale: " + one.compareTo(two));

        System.out.println("1 / 3 (scale 2): "
                + BigDecimal.ONE.divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP));
        System.out.println("1 / 3 (precision 5): "
                + BigDecimal.ONE.divide(new BigDecimal("3"),
                        new MathContext(5, RoundingMode.HALF_EVEN)));
        try {
            BigDecimal.ONE.divide(new BigDecimal("3"));
        } catch (ArithmeticException ex) {
            System.out.println("1 / 3 without rounding: non-terminating");
        }

        System.out.println("rounded scale: "
                + new BigDecimal("123.45").setScale(1, RoundingMode.HALF_UP));
        System.out.println("intValue truncates: " + new BigDecimal("1.99").intValue());
        try {
            new BigDecimal("1.99").intValueExact();
        } catch (ArithmeticException ex) {
            System.out.println("BigDecimal exact conversion: fractional");
        }

        Set<BigDecimal> hash = new HashSet<>();
        Set<BigDecimal> sorted = new TreeSet<>();
        hash.add(one);
        hash.add(two);
        sorted.add(one);
        sorted.add(two);
        System.out.println("HashSet size: " + hash.size());
        System.out.println("TreeSet size: " + sorted.size());

        AtomicInteger counter = new AtomicInteger(7);
        System.out.println("AtomicInteger increment: " + counter.incrementAndGet());
        LongAdder adder = new LongAdder();
        adder.add(10);
        adder.increment();
        Number accumulator = adder;
        System.out.println("LongAdder as Number: " + accumulator.longValue());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac NumberFamiliesDemo.java
java NumberFamiliesDemo
~~~

Đầu ra chính xác:

~~~text
Number intValue: 42
BigInteger + 10: 123456789012345678901234567900
2^100 bits: 101
2^40 intValue: 0
BigInteger exact conversion: overflow
decimal 0.1 + 0.2: 0.3
from double exact? false
valueOf double: 0.1
equals with scale: false
compareTo with scale: 0
1 / 3 (scale 2): 0.33
1 / 3 (precision 5): 0.33333
1 / 3 without rounding: non-terminating
rounded scale: 123.5
intValue truncates: 1
BigDecimal exact conversion: fractional
HashSet size: 2
TreeSet size: 1
AtomicInteger increment: 8
LongAdder as Number: 11
~~~

Chương trình minh họa đồng thời ba khác biệt cần ghi nhớ: `BigInteger` giữ được số nguyên lớn nhưng `intValue()` không giữ toàn bộ; `BigDecimal` giữ giá trị thập phân và phân biệt scale ở `equals`; còn `AtomicInteger`/`LongAdder` là các lớp `Number` **có trạng thái**, phục vụ cập nhật/bộ đếm thay vì số học độ chính xác tùy ý.

## 7. Chọn kiểu nào cho bài toán?

| Nhu cầu | Lựa chọn thường phù hợp | Vì sao |
| --- | --- | --- |
| Đếm, chỉ số, số nguyên trong miền đã xác định | `int` hoặc `long` | Nhanh, cú pháp đơn giản; cần kiểm soát tràn |
| Số nguyên cực lớn/chính xác | `BigInteger` | Số nguyên tùy ý độ lớn; có kiểm tra chuyển đổi exact |
| Giá trị thập phân chính xác theo input/đặc tả | `BigDecimal` | Có mô hình scale và rounding mode |
| Tính toán gần đúng quy mô khoa học/kỹ thuật | `double` (hoặc `float` khi có yêu cầu) | Nhanh và phù hợp IEEE 754 nếu quản lý sai số |
| Truyền số vào API tổng quát chỉ cần đọc | `Number` hoặc generic theo mục đích | Abstraction, nhưng phải quy định chuyển đổi |
| Counter chia sẻ giữa nhiều thread | `AtomicInteger`/`AtomicLong` | Cập nhật nguyên tử |
| Counter thống kê contention cao | `LongAdder` | Có thể giảm contention, chấp nhận semantics snapshot |

Nếu hệ thống xử lý tiền, `BigDecimal` **không tự đảm bảo nghiệp vụ tiền tệ đúng**: vẫn cần xác định đơn vị, scale, loại tiền, cách làm tròn, thời điểm làm tròn, quy định thuế/phí và cách serialize. Với một số thiết kế, lưu số tiền dưới dạng **số nguyên đơn vị nhỏ nhất** (như số xu) bằng `long`/`BigInteger` có thể phù hợp hơn.

Nếu cần số học chính xác phân số như `1/3`, `BigDecimal` hữu hạn cũng không lưu được kết quả thập phân vô hạn chính xác trong một giá trị hữu hạn. Bạn phải chọn làm tròn, hoặc sử dụng mô hình **phân số hữu tỉ** có tử và mẫu là `BigInteger` (tự xây dựng hoặc thư viện phù hợp), tùy yêu cầu.

## 8. Những lỗi phổ biến và cách tránh

**Lỗi 1 — Dùng `new BigDecimal(double)` cho số tiền thập phân.**  
Giá trị `double` đã trải qua chuyển đổi nhị phân có thể không biểu diễn chính xác dữ liệu gốc. Khi đầu vào là chuỗi thập phân, tạo trực tiếp từ chuỗi; khi lấy từ `double`, hiểu giới hạn của `BigDecimal.valueOf(double)`.

**Lỗi 2 — Cho rằng `Number.doubleValue()` luôn giữ nguyên số.**  
BigInteger nhiều chữ số có thể mất độ chính xác khi thành `double`; BigDecimal chuyển thành `double` cũng có thể làm tròn/overflow. Đừng dùng `doubleValue()` như lớp chuyển đổi chung khi cần kết quả exact.

**Lỗi 3 — Quên BigInteger/BigDecimal bất biến.**  
`amount.add(extra)` không đổi `amount`; phải dùng `amount = amount.add(extra)` hoặc lưu đối tượng kết quả riêng.

**Lỗi 4 — Gọi `BigDecimal.divide` không có chính sách làm tròn cho 1/3.**  
Phép chia chính xác không có khai triển thập phân kết thúc sẽ ném `ArithmeticException`. Chọn scale/precision và rounding theo đặc tả.

**Lỗi 5 — Dùng `BigDecimal.equals` để kiểm tra bằng nhau về số học.**  
`new BigDecimal("2.0")` và `new BigDecimal("2.00")` có `equals=false`, dù `compareTo=0`. Hãy chọn định nghĩa bằng nhau mong muốn trước khi dùng làm khóa.

**Lỗi 6 — Nhầm các Number đều immutable.**  
`AtomicInteger`, `AtomicLong`, `LongAdder` là đối tượng mutable; phép `intValue()` hoặc `longValue()` phản ánh trạng thái khi đọc theo hợp đồng của lớp.

**Lỗi 7 — Chuyển BigInteger/BigDecimal về int bằng `intValue()` không kiểm tra.**  
Có thể mất phần lẻ hoặc bit cao. Dùng các phương thức `...Exact()` khi yêu cầu bảo toàn giá trị, xử lý `ArithmeticException` đúng cách.

**Lỗi 8 — Cho rằng BigDecimal không có giới hạn.**  
`BigDecimal` không cho phép vô hạn tài nguyên, có thể bị ràng buộc bởi phạm vi scale và tài nguyên tính toán; phép chia thập phân vô hạn cần rounding. Lựa chọn này không thay thế thiết kế thuật toán và kiểm soát đầu vào.

**Lỗi 9 — So sánh các wrapper bằng `==` theo danh tính object.**  
Khi cả hai vế là reference, `==` kiểm tra **danh tính tham chiếu**, trừ trường hợp unboxing dẫn tới toán tử primitive. Dùng `equals` hoặc API so sánh phù hợp, nhưng nhớ `equals` cũng có thể không đồng nhất giữa các kiểu `Number` khác nhau.

## 9. Bài tập tự kiểm tra

**Câu 1.** `Number n = new BigInteger("999999999999999999999");` có hợp lệ không, và `n.intValue()` có đảm bảo trả toàn bộ số không?  
**Đáp án:** Hợp lệ vì `BigInteger` kế thừa `Number`. `intValue()` không bảo toàn số lớn; hãy ép lại về `BigInteger` một cách có kiểm tra hoặc giữ tham chiếu kiểu cụ thể, rồi dùng `intValueExact()` nếu cần đổi sang `int`.

**Câu 2.** Vì sao `new BigDecimal("0.1").add(new BigDecimal("0.2"))` cho giá trị 0.3 chính xác, còn `0.1 + 0.2` có thể khác 0.3?  
**Đáp án:** BigDecimal tạo từ chuỗi lưu thập phân đúng 0.1 và 0.2; phép cộng BigDecimal chính xác trong trường hợp này. `double` lưu xấp xỉ nhị phân của 0.1 và 0.2, nên kết quả `0.30000000000000004`.

**Câu 3.** `new BigDecimal("1").divide(new BigDecimal("3"))` cho kết quả gì?  
**Đáp án:** Ném `ArithmeticException` vì thương 1/3 không có biểu diễn thập phân hữu hạn. Cung cấp `scale`/`RoundingMode` hoặc `MathContext`.

**Câu 4.** Vì sao `HashSet<BigDecimal>` chứa cả 2.0 và 2.00, còn `TreeSet<BigDecimal>` thường chỉ có một?  
**Đáp án:** `HashSet` dùng `equals`/`hashCode` xét cả scale; `TreeSet` dùng `compareTo` theo giá trị số, coi hai giá trị này bằng nhau khi so sánh.

**Câu 5.** `Number` có phù hợp làm interface chung cho mọi phép cộng số chính xác không?  
**Đáp án:** Không. `Number` chỉ định các phép chuyển về primitive, không định nghĩa phép `add(Number)` hay loại số học. Cần giao diện/domain type và quy tắc chuyển đổi cụ thể nếu xây một thư viện phép tính.

**Câu 6.** `AtomicInteger` và `LongAdder` có phải các loại BigInteger hiệu quả hơn khi đa luồng không?  
**Đáp án:** Không. Chúng phục vụ cập nhật/cộng dồn các giá trị miền cố định, có trạng thái và hợp đồng đồng thời riêng. BigInteger là số nguyên bất biến độ lớn tùy ý.

**Câu 7.** Nên dùng `BigDecimal.intValue()` hay `intValueExact()` khi nhập tuổi nguyên từ giá trị thập phân?  
**Đáp án:** `intValueExact()` nếu yêu cầu tuổi phải là số nguyên và nằm trong miền `int`; phương thức này từ chối các giá trị có phần lẻ hoặc vượt miền thay vì âm thầm làm mất thông tin.

## 10. Ghi nhớ

- `Number` là **lớp trừu tượng chuyển đổi giá trị số** sang các primitive, không phải phép tính chung cho mọi loại số.
- `BigInteger` phục vụ **số nguyên lớn chính xác**; các phép toán là phương thức, kết quả là đối tượng mới; kiểm soát chuyển kiểu bằng `...Exact()`.
- `BigDecimal` phục vụ **thập phân có kiểm soát**; tạo từ chuỗi khi cần giữ đầu vào gốc, quản lý `scale`/`precision`/`RoundingMode`, phân biệt `equals` và `compareTo`.
- Wrapper primitive có miền cố định và bất biến; các lớp `AtomicInteger`/`AtomicLong`/`LongAdder` cũng là `Number` nhưng có trạng thái và mục đích đồng thời.
- Chọn kiểu số dựa trên **độ lớn, độ chính xác, tính bất biến, yêu cầu đồng thời, quy tắc làm tròn và hiệu năng**, không chỉ dựa trên việc một lớp có kế thừa `Number` hay không.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="number-and-wrappers-005">Wrapper bất biến và ngữ nghĩa value-based</a>

<details>
<summary>Xem chi tiết</summary>

# Wrapper bất biến và ngữ nghĩa value-based trong Java

## 1. Mục tiêu

Trong Java, mỗi kiểu nguyên thủy có một lớp bọc (*wrapper*) tương ứng. Các lớp bọc cho phép đưa giá trị primitive vào những API yêu cầu đối tượng, chẳng hạn `List<Integer>`, `Map<Long, String>`, `Optional<Double>` hoặc các phương thức nhận `Object`/`Number`.

Điểm quan trọng nhất: **các wrapper là bất biến (immutable) và được Java mô tả là những lớp value-based**. Lập trình viên nên quan tâm đến **giá trị** của chúng, không dựa vào danh tính đối tượng (object identity). Bất biến giúp chia sẻ giá trị an toàn, nhưng không đồng nghĩa các wrapper là primitive hoặc Java đảm bảo hai đối tượng cùng giá trị luôn có cùng địa chỉ/tham chiếu.

Sau bài học, bạn cần giải thích được:

- Vì sao không thể “sửa trực tiếp” nội dung bên trong `Integer`, `Long`, `Double` hay `Boolean`.
- Vì sao `x++` hoặc `x = x + 1` trên biến wrapper không làm wrapper cũ thay đổi.
- Vì sao `equals()` thường đúng mục đích so sánh **giá trị**, còn `==` giữa hai tham chiếu wrapper lại có thể tạo lỗi khó phát hiện.
- Vai trò của `valueOf()`, autoboxing, unboxing và vùng cache của `Integer`.
- Tại sao không nên dùng wrapper làm khóa của `synchronized`, `IdentityHashMap` hoặc các phép xử lý dựa vào identity.
- Các ngoại lệ quan trọng của `Double.equals()`/`Float.equals()` đối với NaN và hai số không có dấu khác nhau `+0.0`/`-0.0`.

Bài sử dụng Java 17+; kiến thức value-based ở đây là hợp đồng API hiện hành và định hướng thiết kế Java, không yêu cầu dùng tính năng preview.

## 2. Primitive và wrapper khác nhau như thế nào?

| Primitive | Wrapper | Ví dụ |
| --- | --- | --- |
| `boolean` | `Boolean` | `Boolean.TRUE` |
| `byte` | `Byte` | `Byte.valueOf((byte) 12)` |
| `short` | `Short` | `Short.valueOf((short) 12)` |
| `char` | `Character` | `Character.valueOf('A')` |
| `int` | `Integer` | `Integer.valueOf(12)` |
| `long` | `Long` | `Long.valueOf(12L)` |
| `float` | `Float` | `Float.valueOf(1.25f)` |
| `double` | `Double` | `Double.valueOf(1.25)` |

Các wrapper số (trừ `Character` và `Boolean`) là lớp con của `java.lang.Number`. Tất cả tám lớp trên là lớp `final`: không thể viết lớp kế thừa từ chúng. Trong Java hiện đại, tài liệu API của chúng cũng mô tả đây là các **value-based classes**.

Sự khác biệt thực tế:

- Một biến primitive trực tiếp mang giá trị của kiểu đó; `int n = 10;`.
- Một biến wrapper là **tham chiếu đến một đối tượng** chứa giá trị primitive; `Integer n = Integer.valueOf(10);`.
- Tham chiếu wrapper có thể bằng `null`; primitive không có giá trị `null`.
- Wrapper có phương thức như `intValue()`, `compareTo()`, `equals()`, `hashCode()`, `toString()` và các phương thức static như `valueOf()`/parse theo từng lớp.
- Generics thông thường không nhận primitive: `List<int>` không hợp lệ, nhưng `List<Integer>` hợp lệ.

**Không nhầm tính bất biến với một biến `final`:** `final` hạn chế việc **gán lại biến**, còn tính bất biến hạn chế việc **thay đổi trạng thái của đối tượng đã có**. Hai khái niệm độc lập.

## 3. Wrapper bất biến: đối tượng cũ không bị sửa

Một đối tượng `Integer` đã biểu diễn số 10 sẽ tiếp tục biểu diễn số 10 trong suốt vòng đời của đối tượng đó. Lớp không cung cấp `setValue()` hoặc cơ chế hợp lệ nào để thay giá trị primitive bên trong.

~~~java
Integer first = Integer.valueOf(10);
Integer saved = first;

first = Integer.valueOf(20);

System.out.println(saved); // 10
System.out.println(first); // 20
~~~

Đây là **đoạn minh họa trong một phương thức**, không phải chương trình chạy độc lập. Lệnh gán `first = ...` làm biến `first` trỏ đến một giá trị wrapper khác; `saved` vẫn giữ giá trị 10.

Cùng nguyên tắc với `Long`, `Short`, `Double`, `Boolean` và các wrapper còn lại. Một lời gọi như `Integer.sum(a, b)` hoặc `Math.abs(n)` tính **giá trị kết quả**; nó không sửa đối tượng nguồn.

### Bất biến khác với final

~~~java
Integer changeableReference = 7;
changeableReference = 9; // hợp lệ: biến không final

final Integer fixedReference = 7;
// fixedReference = 9;  // lỗi biên dịch: không được gán lại final
~~~

Trong cả hai trường hợp, những đối tượng wrapper mang giá trị 7 vẫn bất biến. `final` không phải điều kiện làm cho wrapper bất biến.

### Biến wrapper tăng ++ thực sự làm gì?

~~~java
Integer counter = 5;
Integer before = counter;
counter++;

System.out.println(before);  // 5
System.out.println(counter); // 6
~~~

Về mặt giá trị, Java thực hiện **unboxing → cộng một trên primitive → boxing** rồi **gán lại** tham chiếu `counter`. Đối tượng gốc không bị sửa. Các quy tắc tràn của primitive vẫn áp dụng: tăng `Integer.MAX_VALUE` sẽ quay về `Integer.MIN_VALUE`, chứ wrapper không tự mở rộng miền số.

### Một lớp dữ liệu bất biến khác

`BigInteger` và `BigDecimal` cũng bất biến: `value.add(...) ` trả về kết quả mới thay vì sửa receiver. Tuy nhiên, chúng không phải wrapper primitive, có miền/độ chính xác và hợp đồng API riêng. Khái niệm “immutable” rộng hơn khái niệm “wrapper” và cũng rộng hơn danh sách lớp được API đánh dấu *value-based*.

## 4. Value-based nghĩa là gì?

Theo tài liệu API Java, những đối tượng của một lớp **value-based** phải được ứng dụng đối xử như **giá trị**: các instance được coi là bằng nhau theo `equals()` thì nên có thể thay thế cho nhau trong những phép sử dụng dựa trên giá trị.

Các hệ quả thiết kế:

1. **Đừng dùng identity làm dữ liệu nghiệp vụ:** tránh kết luận hai wrapper đại diện hai thực thể khác nhau chỉ bởi vì hai tham chiếu không `==`.
2. **Đừng dùng wrapper làm monitor trong `synchronized`:** một số instance có thể được chia sẻ bởi cache hoặc cơ chế tạo đối tượng khác. Java cũng cảnh báo rằng đồng bộ hóa theo value-based instance có thể có hành vi không đoán trước và có thể không còn được hỗ trợ trong tương lai.
3. **Đừng phụ thuộc vào `System.identityHashCode(wrapper)`, `IdentityHashMap` hay phép tra cứu dựa trên object identity** để lưu ý nghĩa của giá trị.
4. **Đừng dựa vào số lượng đối tượng được cấp phát** bởi `valueOf()` hay boxing. Triển khai JVM có thể tái sử dụng hoặc tối ưu đối tượng khi điều đó tương thích với hợp đồng của lớp.

Điều này **không** có nghĩa wrapper không phải đối tượng ở Java 17. `Integer` vẫn có kiểu tham chiếu, có thể là `null`, có `equals()` và `hashCode()`, và có thể được sử dụng làm khóa trong `HashMap`. Value-based là **cách dùng đúng theo hợp đồng API**, không phải tuyên bố rằng Java 17 đã biến `Integer` thành một kiểu nguyên thủy hoặc một *value class* mới của JVM.

Bất biến và value-based có liên quan nhưng **không đồng nhất**: một đối tượng có thể bất biến mà ứng dụng vẫn cần identity (ví dụ một token danh tính với hợp đồng riêng). Với wrapper, API đã xác định rõ ngữ nghĩa hướng về **giá trị**.

## 5. So sánh: equals() và ==

### Hai wrapper cùng kiểu: hãy so sánh giá trị

~~~java
Integer left = Integer.valueOf(1000);
Integer right = Integer.valueOf(1000);

System.out.println(left.equals(right)); // true
~~~

Không nên viết `left == right` để so sánh giá trị khi **cả hai toán hạng đều là tham chiếu** `Integer`. Khi đó `==` là phép so sánh **cùng đối tượng tham chiếu hay không**, không phải phép so sánh số học.

Điểm dễ nhầm: nếu một toán hạng là primitive, Java có thể unbox wrapper và biến `==` thành **so sánh số học**:

~~~java
Integer boxed = Integer.valueOf(1000);
System.out.println(boxed == 1000); // true: unboxing boxed thành int
~~~

Vì vậy có những câu lệnh nhìn gần giống nhau nhưng `==` có **hai ngữ nghĩa khác nhau** tùy kiểu của toán hạng lúc biên dịch.

### equals so sánh đúng lớp wrapper

~~~java
Integer oneInt = Integer.valueOf(1);
Long oneLong = Long.valueOf(1L);

System.out.println(oneInt.equals(oneLong)); // false
~~~

Hai đối tượng chứa cùng giá trị toán học 1 nhưng `Integer.equals(Object)` chỉ bằng một đối tượng `Integer` cùng giá trị; `Long` không phải `Integer`. Không nên gọi `equals()` giữa các wrapper khác kiểu rồi mong Java tự chuyển số hoặc nâng kiểu như khi cộng primitive.

Nếu thực sự muốn so sánh số học của hai kiểu nguyên, hãy **chuyển có kiểm soát** sang một kiểu có miền thích hợp (ví dụ `long` cho `int` và `long`), hoặc `BigInteger` cho giá trị lớn; với `float`/`double`, cân nhắc sai số và các giá trị đặc biệt.

### Null-safe equality

~~~java
Integer nullable = null;
Integer expected = 10;

System.out.println(java.util.Objects.equals(nullable, expected)); // false
System.out.println(java.util.Objects.equals(null, null));          // true
~~~

`Objects.equals(a, b)` xử lý an toàn trường hợp một hoặc cả hai tham chiếu là `null`. Nó không tự chuyển giá trị từ `Integer` sang `Long` hay thay đổi hợp đồng `equals()` của lớp.

## 6. Cache và Integer.valueOf(): lợi ích có, identity không phải hợp đồng ứng dụng

`Integer.valueOf(int)` **luôn cache** các giá trị từ **−128 đến 127** theo tài liệu Java 17 và **có thể cache thêm** các giá trị khác, tùy môi trường. Vì thế:

~~~java
Integer a = Integer.valueOf(42);
Integer b = Integer.valueOf(42);

System.out.println(a == b);      // true: ví dụ về cache được bảo đảm cho khoảng này
System.out.println(a.equals(b));  // true
~~~

Tuy nhiên, nếu dùng 1000:

~~~java
Integer x = Integer.valueOf(1000);
Integer y = Integer.valueOf(1000);

System.out.println(x.equals(y)); // true
// Không dựa vào (x == y): trạng thái cache ngoài -128..127 không được bảo đảm.
~~~

**Bài học:** dù `a == b` tình cờ đúng nhờ cache ở nhiều trường hợp, không dùng identity để kết luận hai giá trị có bằng nhau. Một đoạn code chạy đúng với 42 nhưng sai với 1000 là lỗi điển hình khi phụ thuộc vào object identity.

`Integer.valueOf` thường ưu tiên hơn tạo đối tượng qua constructor. Các constructor như `new Integer(int)` đã **deprecated for removal** trong Java 17; chúng tạo ra identity không cần thiết và không phải hướng tạo wrapper nên dùng. Tương tự, boxing tự động thường đi qua cơ chế tạo wrapper phù hợp chứ không phải lệnh sửa một đối tượng đang có.

Không nên suy rằng mọi wrapper đều có chính sách cache giống `Integer`: phạm vi được bảo đảm khác nhau theo lớp và kiểu; điều ổn định cho logic nghiệp vụ là dùng **`equals()` và `hashCode()` theo giá trị**.

## 7. Autoboxing và unboxing: tiện nhưng có chi phí và rủi ro

**Autoboxing** chuyển primitive thành wrapper ở ngữ cảnh thích hợp:

~~~java
int primitive = 7;
Integer boxed = primitive; // tương tự dùng Integer.valueOf(...)
~~~

**Unboxing** lấy giá trị primitive từ wrapper:

~~~java
Integer boxed = 7;
int primitive = boxed; // tương tự boxed.intValue()
~~~

Điều này khiến Java cho phép:

~~~java
Integer a = 2;
Integer b = 3;
Integer sum = a + b; // unboxing hai số, cộng int, boxing kết quả
System.out.println(sum); // 5
~~~

Quá trình trên **không** làm `a` hoặc `b` thay đổi. Phép `+` thực hiện trên các giá trị primitive theo quy tắc ngôn ngữ; chẳng hạn phép cộng `int` vẫn có thể tràn.

### Unboxing null ném NullPointerException

~~~java
Integer quantity = null;
// int count = quantity; // khi chạy sẽ ném NullPointerException
~~~

Ngay cả khi không gán rõ ràng sang `int`, một phép toán yêu cầu unboxing như `quantity + 1`, `quantity > 0` hoặc `quantity == 0` cũng có thể ném `NullPointerException`. Ngược lại, `quantity == null` là phép kiểm tra tham chiếu hợp lệ và không yêu cầu unboxing.

Không giả định giá trị wrapper lấy từ database, JSON, `Map.get()` hoặc API có thể trả `null` luôn hợp lệ. Dùng cách xử lý thiếu dữ liệu theo hợp đồng (validation, fallback có ý nghĩa hoặc thông báo lỗi).

### Hiệu năng

Tạo/đọc wrapper có thể làm phát sinh boxing/unboxing, đối tượng và truy cập bộ nhớ. Bộ biên dịch JIT có thể tối ưu một số trường hợp, nhưng ứng dụng không nên dựa vào một số lượng cấp phát cụ thể.

Với vòng lặp tính toán rất lớn, ưu tiên `int`, `long`, `double` và mảng primitive khi phù hợp. Với API generic/collection yêu cầu đối tượng, wrapper vẫn là lựa chọn tiêu chuẩn. Chỉ tối ưu dựa trên profiling và yêu cầu thật của chương trình.

## 8. Wrapper trong collection: HashMap, HashSet và bất biến

Các collection theo giá trị, như `HashMap` và `HashSet`, dùng `equals()`/`hashCode()` để nhận biết khóa/phần tử tương đương. Wrapper rất phù hợp làm khóa vì trạng thái giá trị của đối tượng không tự thay đổi sau khi chèn.

~~~java
java.util.Map<Integer, String> labels = new java.util.HashMap<>();
labels.put(Integer.valueOf(1000), "thousand");

String found = labels.get(Integer.valueOf(1000));
System.out.println(found); // thousand
~~~

Không cần bảo đảm hai lần `valueOf(1000)` trả về **cùng một object**: `Integer.equals()` và `hashCode()` giúp tra cứu theo giá trị.

Điều này khác với:

~~~java
java.util.Map<Integer, String> identityMap = new java.util.IdentityHashMap<>();
// Không dùng IdentityHashMap nếu nghiệp vụ cần tra cứu khóa số theo giá trị.
~~~

`IdentityHashMap` kiểm tra khóa theo `==`, vì vậy có thể cho hành vi khác với `HashMap` và chính sách tái sử dụng wrapper. Nó được thiết kế cho những tình huống **thật sự cần nhận diện object**, không phải bảng tra khóa số nguyên thông thường.

Lưu ý wrapper bất biến **không làm collection bất biến**. `labels.put(1000, "...")` vẫn có thể thay đổi dữ liệu của map; nó chỉ không thay đổi giá trị primitive chứa trong wrapper khóa.

## 9. Không dùng wrapper làm monitor đồng bộ hóa

Một đoạn code như sau là **mẫu cần tránh**:

~~~java
// Ví dụ sai về thiết kế, không sử dụng trong ứng dụng:
// Integer lock = Integer.valueOf(1);
// synchronized (lock) {
//     // critical section
// }
~~~

Vấn đề:

- `Integer.valueOf(1)` có thể trả object cache dùng chung với code hoàn toàn không liên quan. Hai luồng/khối code không chủ ý vẫn có thể dùng chung monitor.
- Nếu gán lại biến wrapper, các lệnh `synchronized` trong những lần chạy khác nhau có thể khóa **những đối tượng khác nhau**.
- Tài liệu chính thức cảnh báo wrapper là *value-based* và không nên được sử dụng để đồng bộ hóa; Java có thể thay đổi cách hỗ trợ identity của các giá trị này trong tương lai.
- Trình biên dịch Java 17 có thể phát cảnh báo với lệnh `synchronized` trên một instance của lớp value-based (liên quan JEP 390).

Hãy dùng một đối tượng khóa có identity được dành riêng cho việc đồng bộ:

~~~java
public class SafeCounter {
    private final Object lock = new Object();
    private int value;

    public void increment() {
        synchronized (lock) {
            value++;
        }
    }

    public int get() {
        synchronized (lock) {
            return value;
        }
    }
}
~~~

Đây là **lớp độc lập có thể biên dịch** (lưu thành `SafeCounter.java`) nhưng không có `main`. Với bộ đếm đơn giản cần thao tác atomic trên nhiều luồng, `AtomicInteger` có thể thích hợp; đó là **lớp có trạng thái có thể thay đổi**, không phải wrapper bất biến `Integer`.

## 10. Float và Double: equals khác == ở NaN và signed zero

Wrapper dấu phẩy động phải hỗ trợ một quan hệ `equals()` nhất quán cho những collection như `HashMap`/ `HashSet`. Vì vậy, `Double.equals()` và `Float.equals()` có **quy tắc khác phép `==` trên primitive** ở hai trường hợp đặc biệt.

| Tình huống | Primitive `double == double` | `Double.equals()` |
| --- | --- | --- |
| `1.0` và `1.0` | `true` | `true` |
| `Double.NaN` và `Double.NaN` | `false` | `true` |
| `+0.0` và `-0.0` | `true` | `false` |

Ví dụ:

~~~java
Double nanA = Double.valueOf(Double.NaN);
Double nanB = Double.valueOf(Double.NaN);
System.out.println(nanA.equals(nanB)); // true
System.out.println(nanA.doubleValue() == nanB.doubleValue()); // false

Double positiveZero = Double.valueOf(+0.0);
Double negativeZero = Double.valueOf(-0.0);
System.out.println(positiveZero.equals(negativeZero)); // false
System.out.println(positiveZero.doubleValue() == negativeZero.doubleValue()); // true
~~~

`Double.equals()` dựa trên biểu diễn theo `Double.doubleToLongBits()`, chuẩn hóa các bit pattern NaN để tất cả NaN được coi bằng nhau nhưng giữ phân biệt dấu của zero. `Float.equals()` có cơ chế tương tự dùng `Float.floatToIntBits()`.

Điều này **không phải mâu thuẫn với value-based**: khái niệm “hai instance bằng nhau” được xác định bằng **`equals()` của lớp**, không nhất thiết bằng toán tử số học `==` của primitive.

### compareTo và thứ tự toàn phần của wrapper floating point

`Double.compare()`/`Double.compareTo()` thiết lập thứ tự phù hợp với `Double.equals()`:

- `-0.0` được xếp **nhỏ hơn** `+0.0`.
- `NaN` được xếp cao hơn mọi giá trị `double` khác, kể cả `+Infinity`; các NaN so sánh bằng nhau theo thứ tự này.

Đây là lựa chọn để `Double` làm phần tử trong các collection có thứ tự như `TreeSet`. Đừng suy rằng `Double.compare(a, b)` luôn cho cùng ngữ nghĩa với `a < b` khi có NaN hoặc hai zero khác dấu.

## 11. Chương trình Java 17+ đầy đủ

Lưu mã sau vào tệp `WrapperValueDemo.java`:

~~~java
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class WrapperValueDemo {
    public static void main(String[] args) {
        Integer original = Integer.valueOf(10);
        Integer saved = original;
        original++;

        System.out.println("saved=" + saved);
        System.out.println("after increment=" + original);

        Integer left = Integer.valueOf(1000);
        Integer right = Integer.valueOf(1000);
        System.out.println("same value=" + left.equals(right));
        System.out.println("boxed vs primitive=" + (left == 1000));
        System.out.println("cached 42=" +
                (Integer.valueOf(42) == Integer.valueOf(42)));

        System.out.println("cross type equals=" +
                Integer.valueOf(1).equals(Long.valueOf(1L)));
        System.out.println("null safe=" + Objects.equals(null, null));

        Map<Integer, String> labels = new HashMap<>();
        labels.put(Integer.valueOf(1000), "thousand");
        System.out.println("map lookup=" + labels.get(Integer.valueOf(1000)));

        Double nanA = Double.valueOf(Double.NaN);
        Double nanB = Double.valueOf(Double.NaN);
        System.out.println("NaN wrapper equals=" + nanA.equals(nanB));
        System.out.println("NaN primitive equals=" +
                (nanA.doubleValue() == nanB.doubleValue()));

        Double plusZero = Double.valueOf(+0.0);
        Double minusZero = Double.valueOf(-0.0);
        System.out.println("signed zero wrapper equals=" +
                plusZero.equals(minusZero));
        System.out.println("signed zero primitive equals=" +
                (plusZero.doubleValue() == minusZero.doubleValue()));

        Integer missing = null;
        try {
            int primitive = missing;
            System.out.println(primitive);
        } catch (NullPointerException ex) {
            System.out.println("unboxing null: NPE");
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 WrapperValueDemo.java
java WrapperValueDemo
~~~

Stdout:

~~~text
saved=10
after increment=11
same value=true
boxed vs primitive=true
cached 42=true
cross type equals=false
null safe=true
map lookup=thousand
NaN wrapper equals=true
NaN primitive equals=false
signed zero wrapper equals=false
signed zero primitive equals=true
unboxing null: NPE
~~~

**Giải thích những điểm quan trọng:**

1. `original++` tính toán qua unboxing/boxing rồi gán lại biến; `saved` vẫn giữ 10.
2. `left.equals(right)` so sánh **giá trị**, không phụ thuộc hai wrapper 1000 có dùng chung object hay không.
3. `left == 1000` đúng vì toán hạng thứ hai là `int` khiến `left` được unbox. `valueOf(42) == valueOf(42)` đúng do mức cache được bảo đảm; **không áp dụng kết luận identity này cho mọi giá trị**.
4. `Integer(1)` và `Long(1L)` không bằng nhau theo `equals()` vì hai lớp khác nhau.
5. `HashMap` tra khóa theo `equals()`/`hashCode()`, vì vậy hai wrapper cùng giá trị truy xuất được cùng entry.
6. So sánh `NaN` và zero có dấu cho thấy ngữ nghĩa khác nhau giữa wrapper `equals()` và primitive `==`.
7. Unboxing `null` phát sinh `NullPointerException`; chương trình bắt exception và in thông báo.

Đây là ví dụ chạy đơn tiến trình, tập trung vào bất biến, identity và equality. Không cần dùng một khối `synchronized` trên wrapper để minh họa vì đó là mẫu thiết kế nên tránh.

## 12. Áp dụng đúng trong API và thiết kế dữ liệu

### Lựa chọn int hay Integer

Chọn `int` khi dữ liệu luôn có giá trị, cần thao tác tính toán và không có ý nghĩa “chưa biết”. Chọn `Integer` khi API generic/collection yêu cầu object hoặc ứng dụng **thật sự cần phân biệt** `null` với `0`.

Ví dụ, trường `retryCount` có thể dùng `int` với mặc định 0; trường dữ liệu database nullable có thể cần `Integer`. Không tự biến `null` thành `0` nếu chúng có hai ý nghĩa khác nhau.

### Không biến wrapper thành một thực thể có identity

Một giá trị `Integer.valueOf(42)` nên được coi là **số 42**, không phải một “đối tượng người dùng số 42” với vòng đời riêng. Nếu ứng dụng cần biểu diễn thực thể có danh tính, hãy tạo lớp/record domain với định danh riêng, ví dụ `CustomerId` hoặc `Order`, thay vì dùng danh tính của instance wrapper.

### Không chọn monitor từ một giá trị số

Nếu bạn cần một khóa riêng của đối tượng, dùng một trường `private final Object lock = new Object()`, `ReentrantLock` hoặc cấu trúc concurrent phù hợp. Nếu cần cập nhật giá trị đếm đồng thời, cân nhắc `AtomicInteger`/`AtomicLong`. Giá trị số chỉ nên là **dữ liệu**, không phải monitor.

### Tính bất biến không chữa được sai số dấu phẩy động

Một `Double` bất biến vẫn chứa giá trị `double` chịu giới hạn IEEE 754: NaN, Infinity, ±0 và sai số làm tròn vẫn tồn tại. Nếu nghiệp vụ cần số thập phân chính xác, `BigDecimal` có thể phù hợp hơn. Đây là quyết định về **mô hình số học**, không phải quyết định về object identity.

## 13. Lỗi thường gặp

| Lỗi | Hậu quả | Cách xử lý |
| --- | --- | --- |
| `Integer a = 1000, b = 1000; if (a == b)...` | So sánh reference, dễ phụ thuộc cache | `a.equals(b)` hoặc `Objects.equals(a,b)` |
| Dùng `==` trên wrapper chỉ vì với `42` thấy đúng | Hành vi khác khi giá trị/vùng cache thay đổi | So sánh giá trị một cách nhất quán |
| `Integer count = null; count++;` | `NullPointerException` khi unbox | Kiểm tra/null policy |
| Cho rằng `count++` sửa đối tượng `Integer` cũ | Hiểu sai alias và tính bất biến | Đây là unbox, cộng, box, gán lại |
| Dùng `synchronized(Integer.valueOf(id))` | Cache gây chia sẻ monitor ngoài ý muốn; vi phạm hướng dẫn value-based | Dùng lock có identity riêng |
| Dùng `IdentityHashMap<Integer, ...>` cho khóa số | Tra cứu theo `==` thay vì giá trị | `HashMap<Integer, ...>` |
| `Integer.valueOf(1).equals(Long.valueOf(1))` được kỳ vọng true | `equals` đòi đúng loại wrapper | Chuyển có kiểm soát khi muốn so sánh số học giữa các kiểu |
| Cho rằng `Double.NaN == Double.NaN` true | So sánh IEEE 754 không thỏa phản xạ với NaN | Dùng `Double.isNaN` hoặc `Double.equals` đúng ý nghĩa |
| Cho rằng `Double.valueOf(+0.0).equals(Double.valueOf(-0.0))` true | `Double.equals` phân biệt signed zero | Chọn ngữ nghĩa so sánh theo nghiệp vụ |
| Tạo wrapper qua constructor deprecated | Cảnh báo; bỏ qua `valueOf`/boxing | `valueOf` hoặc boxing |
| Dùng `Integer` cho biến đếm trong vòng lặp nóng mà không đánh giá chi phí | Boxing/unboxing và truy cập đối tượng không cần thiết | Primitive khi phù hợp, kiểm thử hiệu năng |

## 14. Câu hỏi tự kiểm tra

**1.** Đoạn sau in gì?

~~~java
Integer a = 5;
Integer b = a;
a = a + 1;
System.out.println(a + ", " + b);
~~~

**Đáp án:** `6, 5`. `a` trỏ đến giá trị wrapper mới; `b` không bị thay đổi.

**2.** Vì sao `Integer.valueOf(42) == Integer.valueOf(42)` đúng nhưng vẫn không nên dùng `==` để so sánh wrapper?

**Đáp án:** `Integer.valueOf` bảo đảm cache từ −128 đến 127. Ngoài miền đó, cache không bảo đảm; quan trọng hơn, `==` giữa hai reference kiểm tra object identity, còn mục đích so sánh số nên dùng `equals()`.

**3.** `Integer.valueOf(1000).equals(Long.valueOf(1000L))` có trả `true` không?

**Đáp án:** Không; `equals()` kiểm tra đúng loại lớp wrapper. Muốn so sánh giá trị số của `int` và `long` thì chuyển sang `long` theo cách rõ ràng và so sánh primitive.

**4.** Có thể đồng bộ bằng `synchronized(Integer.valueOf(1))` nếu chỉ một chỗ trong code sử dụng không?

**Đáp án:** Không nên. Wrapper là lớp value-based, có cơ chế cache và không được thiết kế để trở thành lock identity. Dùng `Object`/lock riêng.

**5.** `Double.valueOf(Double.NaN).equals(Double.valueOf(Double.NaN))` và `Double.NaN == Double.NaN` có giống nhau không?

**Đáp án:** Không; biểu thức thứ nhất `true`, thứ hai `false`. Đây là khác biệt chủ động giữa `Double.equals()` và phép so sánh primitive.

**6.** Một `HashMap<Integer, String>` có thể tra được entry với một wrapper cùng giá trị dù không dùng chung instance không?

**Đáp án:** Có. `HashMap` dùng `hashCode()` và `equals()` để xác định khóa tương đương, không cần identity của wrapper.

## 15. Ghi nhớ và tài liệu tham khảo

- Wrapper là **đối tượng bất biến**, còn biến chứa reference có thể được gán lại nếu không khai báo `final`.
- **Value-based** hướng ứng dụng đến giá trị theo `equals()`; không dùng wrapper làm monitor hay phụ thuộc vào object identity.
- `Integer.valueOf()` bảo đảm cache `-128..127`, nhưng đây **không** phải lý do dùng `==` để so sánh giá trị.
- Autoboxing/unboxing tiện lợi nhưng có thể phát sinh chi phí, mất thông tin khi chuyển kiểu số và ném `NullPointerException` nếu unbox `null`.
- `Double.equals()`/`Float.equals()` xử lý NaN và signed zero khác `==` của primitive; chọn đúng phép so sánh theo hợp đồng API.
- Với khóa số trong collection, dùng `HashMap`/`HashSet`; với tính toán nhiều lần, cân nhắc primitive; với đồng bộ, dùng lock riêng hoặc lớp concurrent phù hợp.

Nguồn chính thức:

- [Java SE 17 — Integer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html): value-based, `valueOf`, cache, `equals`, constructor deprecated.
- [Java SE 17 — Double](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html): equality cho NaN và signed zero; mô tả value-based.
- [Java SE 17 — Objects](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Objects.html): `Objects.equals`.
- [JEP 390 — Warnings for Value-Based Classes](https://openjdk.org/jeps/390): rủi ro liên quan identity và synchronized trên đối tượng value-based.
- [JLS SE 17 — Boxing Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.7) và [Unboxing Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.8): quy tắc chuyển đổi tự động.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="number-and-wrappers-006">valueOf thay cho constructor wrapper lỗi thời</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng valueOf thay cho constructor wrapper lỗi thời trong Java

> **Java core – numbers · Number và wrapper**  
> **Mục tiêu:** Thay đúng các lời gọi constructor wrapper đã lỗi thời bằng phương thức `valueOf`, phân biệt `valueOf` với `parseXxx` và autoboxing, hiểu tại sao không được dựa vào danh tính đối tượng wrapper, đồng thời tránh lỗi khi parse, so sánh và unboxing.

## 1. Vấn đề: wrapper không nên được tạo bằng constructor kiểu cũ

Java có các **kiểu nguyên thủy** (*primitive*) dùng để lưu số và giá trị logic/ký tự, cùng các **wrapper class** là đối tượng tương ứng:

| Primitive | Wrapper | Phương thức tạo từ primitive |
| --- | --- | --- |
| `byte` | `Byte` | `Byte.valueOf(byte)` |
| `short` | `Short` | `Short.valueOf(short)` |
| `int` | `Integer` | `Integer.valueOf(int)` |
| `long` | `Long` | `Long.valueOf(long)` |
| `float` | `Float` | `Float.valueOf(float)` |
| `double` | `Double` | `Double.valueOf(double)` |
| `char` | `Character` | `Character.valueOf(char)` |
| `boolean` | `Boolean` | `Boolean.valueOf(boolean)` |

Trước đây có thể gặp cú pháp:

```java
// Cách cũ: tránh dùng trong mã nguồn Java hiện đại.
// Integer boxed = new Integer(42);
// Long count = new Long(100L);
// Double rate = new Double(2.5);
// Boolean enabled = new Boolean(true);
```

Các **constructor công khai** của nhóm wrapper này đã bị đánh dấu **deprecated for removal** từ Java 9; với Java 17, mã dùng chúng có thể phát sinh cảnh báo biên dịch. Việc *deprecated* **không có nghĩa** constructor đã biến mất trong mọi JDK: mã cũ có thể vẫn biên dịch/chạy ở một số phiên bản, nhưng cách viết đó không còn được khuyến nghị và không nên dùng cho mã mới.

Cách viết được khuyến nghị:

```java
Integer boxed = Integer.valueOf(42);
Long count = Long.valueOf(100L);
Double rate = Double.valueOf(2.5);
Boolean enabled = Boolean.valueOf(true);
```

**Lý do chính:**

1. `valueOf` là **static factory method** phù hợp với quy ước API hiện đại.
2. Phương thức factory **có thể tái sử dụng** wrapper đã có sẵn thay vì bắt buộc tạo một đối tượng mới trong mọi lần gọi.
3. Wrapper là các **value-based class**: điều quan trọng là **giá trị được bọc**, không phải identity tham chiếu hoặc việc khóa trên đối tượng.
4. Cách viết này tránh phụ thuộc vào constructor đang được đánh dấu để loại bỏ trong tương lai.

**Kết luận ngắn:** Nếu thật sự cần một đối tượng `Integer`, dùng `Integer.valueOf(42)` hoặc autoboxing khi thích hợp; **không** dùng `new Integer(42)`.

## 2. Wrapper dùng để làm gì nếu đã có primitive?

Primitive thường đơn giản và hiệu quả cho số học:

```java
int quantity = 42;
double price = 19.5;
boolean available = true;
```

Nhưng một số API và cấu trúc dữ liệu cần **đối tượng tham chiếu**:

```java
import java.util.List;

List<Integer> counts = List.of(1, 2, 3);
Integer optionalCount = null;
Number number = Integer.valueOf(42);
```

Các tình huống thông dụng là:

- Tham số kiểu của collection/generic: `List<Integer>`, `Map<String, Long>`; Java generics không dùng trực tiếp `List<int>`.
- API nhận `Object`, `Number` hoặc interface liên quan thay vì primitive.
- Biểu diễn trạng thái *chưa có giá trị* bằng tham chiếu `null`, nếu hợp đồng dữ liệu cho phép.
- Thao tác với các phương thức của lớp như `Integer.compare`, `Integer.toHexString`, `Double.isFinite` hoặc `intValue()`.

Các wrapper số `Byte`, `Short`, `Integer`, `Long`, `Float`, `Double` là con của `java.lang.Number`. `Character` và `Boolean` **không** kế thừa `Number`; vì vậy đừng coi mọi wrapper đều là một `Number`.

Wrapper đều là đối tượng **bất biến** (*immutable*): không thể đổi giá trị bên trong một `Integer` đã tạo. Gán một wrapper khác vào biến chỉ khiến biến tham chiếu tới giá trị mới.

## 3. valueOf là factory method, không phải constructor

So sánh hai cách thể hiện ý định:

```java
// Cách cũ — constructor bị deprecated:
 // Integer oldStyle = new Integer(42);

// Cách mới — static factory:
Integer modern = Integer.valueOf(42);
```

Constructor kiểu `new Integer(42)` yêu cầu tạo một đối tượng mới. Trong khi đó `Integer.valueOf(42)` trả một tham chiếu tới `Integer` có giá trị 42; việc tham chiếu này trỏ đến đối tượng được tái sử dụng hay đối tượng vừa tạo **không phải điều mà chương trình nghiệp vụ nên phụ thuộc**.

Một số factory có quy tắc cache rõ ràng. Ví dụ trong Java, `Integer.valueOf(int)` bảo đảm cache các giá trị **từ −128 đến 127**; implementation có thể cache thêm ngoài miền đó. Nhưng nên nhớ:

- **Cache là chi tiết tối ưu/đặc tả factory, không phải cách so sánh số.**
- Không suy luận rằng mọi giá trị `Integer.valueOf(1000)` đều có cùng đối tượng hoặc luôn khác đối tượng.
- Không dùng danh tính wrapper để đại diện cho các trạng thái khác nhau.
- Không khóa (`synchronized`) trên wrapper: các wrapper được xem là **value-based** và những thao tác dựa trên identity có thể tạo rủi ro đồng bộ.

Đặc biệt, **`valueOf` không phải phương pháp “ép kiểu để được đối tượng mới mỗi lần”**. Nếu chương trình của bạn cần identity riêng, hãy tạo một lớp đối tượng nghiệp vụ riêng, thay vì cố dùng wrapper làm token định danh.

## 4. Cách thay từng constructor lỗi thời

Khi sửa mã Java cũ, có thể đối chiếu theo bảng sau:

| Constructor cũ (tránh dùng) | Cách thay đề xuất | Lưu ý |
| --- | --- | --- |
| `new Byte((byte) 7)` | `Byte.valueOf((byte) 7)` | Kiểu `byte` có miền −128..127 |
| `new Short((short) 300)` | `Short.valueOf((short) 300)` | Không đổi quy tắc ép hẹp |
| `new Integer(42)` | `Integer.valueOf(42)` | Không dùng `==` để so giá trị |
| `new Long(42L)` | `Long.valueOf(42L)` | Kết quả là wrapper `Long` |
| `new Float(1.25f)` | `Float.valueOf(1.25f)` | Dùng hậu tố `f` cho literal `float` |
| `new Double(1.25)` | `Double.valueOf(1.25)` | Nhận primitive `double` |
| `new Character('A')` | `Character.valueOf('A')` | Nhận một `char`, không nhận chuỗi |
| `new Boolean(true)` | `Boolean.valueOf(true)` | Có thể trả hằng `Boolean.TRUE` |

Các constructor từ **chuỗi** cũng nên thay bằng factory hoặc parser tương ứng:

```java
// Tránh:
// Integer n = new Integer("123");
// Long id = new Long("123456789");
// Double d = new Double("1.25");
// Boolean flag = new Boolean("true");

// Thay bằng:
Integer n = Integer.valueOf("123");
Long id = Long.valueOf("123456789");
Double d = Double.valueOf("1.25");
Boolean flag = Boolean.valueOf("true");
```

Không có cách thay constructor wrapper bằng việc tự viết `new Integer(Integer.parseInt(text))`: bước `new` vẫn tạo đối tượng qua API deprecated. Khi cần wrapper, chọn thẳng `Integer.valueOf(text)`.

## 5. valueOf với giá trị primitive và với chuỗi là hai trường hợp khác nhau

`Integer.valueOf` có nhiều overload:

```java
Integer fromInt = Integer.valueOf(123);        // primitive int -> wrapper Integer
Integer fromText = Integer.valueOf("123");    // String -> wrapper Integer
Integer fromHex = Integer.valueOf("FF", 16);  // String cơ số 16 -> Integer 255
```

Trong ví dụ thứ hai và thứ ba, phương thức phải **phân tích cú pháp chuỗi**. Chuỗi không hợp lệ có thể ném `NumberFormatException`. Trong ví dụ đầu, kiểu đầu vào đã là primitive hợp lệ nên không có bước parse chuỗi.

Một số ví dụ wrapper khác:

```java
Byte byteValue = Byte.valueOf("127");
Short shortValue = Short.valueOf("-123");
Long longValue = Long.valueOf("9000000000");
Float floatValue = Float.valueOf("1.25");
Double doubleValue = Double.valueOf("1.25");
Boolean boolValue = Boolean.valueOf("TrUe");
Character charValue = Character.valueOf('J');
```

**Không có** `Character.valueOf(String)` nhận một chuỗi bất kỳ. `Character.valueOf` nhận một `char`, tức một đơn vị mã UTF-16. Nếu yêu cầu xử lý ký tự Unicode ngoài BMP có thể cần **code point** bằng `int` hoặc `String`, không thể tùy tiện lấy phần tử `charAt(0)` rồi coi là cả ký tự.

## 6. valueOf khác parseXxx ở kiểu trả về

Với chuỗi số nguyên:

```java
String text = "42";

int primitive = Integer.parseInt(text); // int
Integer wrapper = Integer.valueOf(text); // Integer
```

Với chuỗi số thực:

```java
double primitive = Double.parseDouble("1.25"); // double
Double wrapper = Double.valueOf("1.25");        // Double
```

Bảng ghi nhớ:

| Cách gọi | Đầu vào | Kiểu kết quả |
| --- | --- | --- |
| `Integer.parseInt("42")` | String | `int` |
| `Integer.valueOf("42")` | String | `Integer` |
| `Long.parseLong("42")` | String | `long` |
| `Long.valueOf("42")` | String | `Long` |
| `Double.parseDouble("1.25")` | String | `double` |
| `Double.valueOf("1.25")` | String | `Double` |
| `Float.parseFloat("1.25")` | String | `float` |
| `Float.valueOf("1.25")` | String | `Float` |
| `Boolean.parseBoolean("true")` | String | `boolean` |
| `Boolean.valueOf("true")` | String | `Boolean` |

Về lựa chọn:

- Cần làm số học thông thường, biến có kiểu primitive và không yêu cầu đối tượng: ưu tiên **`parseXxx`** rồi dùng primitive.
- API/collection yêu cầu wrapper: dùng **`valueOf`** để nhận đúng kiểu đối tượng.
- Đã có primitive nhưng API cần wrapper: có thể dùng **`valueOf(primitive)`** hoặc **autoboxing**.
- Đã có wrapper và cần primitive: dùng **unboxing** hoặc các phương thức như `intValue()`, nhưng phải xử lý khả năng `null`.

Dù `parseXxx` và `valueOf(String)` có cùng cách phân tích chuỗi tương ứng, chúng không thể thay thế nhau một cách máy móc khi **kiểu trả về** là một phần của chữ ký API hoặc hợp đồng nullability.

## 7. Chuỗi cơ số và decode: tránh hiểu lầm 0x

Nếu cần parse cơ số khác 10:

```java
Integer decimal = Integer.valueOf("255");       // 255
Integer hex = Integer.valueOf("FF", 16);         // 255
Integer binary = Integer.valueOf("11111111", 2); // 255
```

Đối với overload có tham số radix, chuỗi truyền vào thường chỉ chứa chữ số phù hợp; **không** mặc định nhận tiền tố `0x`:

```java
// Integer.valueOf("0xFF", 16); // NumberFormatException
Integer prefixed = Integer.decode("0xFF"); // 255
```

`Integer.decode` có quy tắc riêng nhận tiền tố như `0x` hoặc `#` cho hex, `0` dẫn đầu cho octal; không dùng nó làm parser decimal cho dữ liệu người dùng nếu việc một số bắt đầu bằng 0 bị diễn giải là octal là ngoài ý muốn.

Đối với số nguyên âm, parser có quy tắc dấu:

```java
System.out.println(Integer.valueOf("-42"));   // -42
System.out.println(Integer.valueOf("-2A", 16)); // -42
```

Khi parse dữ liệu không đáng tin, giới hạn độ dài, validate radix và ghi rõ cách diễn giải dấu. Chuỗi quá lớn so với miền `int`/`long` ném `NumberFormatException`; `valueOf` **không mở rộng miền giá trị**. Nếu thực sự cần số nguyên ngoài miền `long`, dùng `BigInteger` theo yêu cầu của bài toán, không coi `Integer.valueOf` là arbitrary precision.

## 8. Boolean.valueOf: khác với parser số học nghiêm ngặt

`Boolean.valueOf(String)` và `Boolean.parseBoolean(String)` có hành vi quan trọng:

- Nếu chuỗi bằng `"true"` theo so sánh **không phân biệt hoa/thường**, kết quả là true.
- Các chuỗi khác, ví dụ `"yes"`, `"1"`, `"falsee"`, cho false thay vì ném `NumberFormatException`.

```java
System.out.println(Boolean.valueOf("TrUe")); // true
System.out.println(Boolean.valueOf("yes"));  // false
System.out.println(Boolean.parseBoolean("1")); // false
```

Vì vậy không nên dùng `Boolean.valueOf(text)` làm **bộ kiểm tra định dạng boolean nghiêm ngặt** khi API chỉ cho phép chính xác hai giá trị `"true"` và `"false"`. Nếu đầu vào có thể sai chính tả, một kết quả false im lặng dễ che giấu lỗi dữ liệu.

Ví dụ cách kiểm tra trước khi parse:

```java
static boolean parseStrictBoolean(String text) {
    if ("true".equalsIgnoreCase(text)) return true;
    if ("false".equalsIgnoreCase(text)) return false;
    throw new IllegalArgumentException("Expected true or false");
}
```

Đây là **phương thức minh họa** cần đặt trong một class nếu muốn biên dịch/chạy. Nó cố ý không tự chấp nhận khoảng trắng hoặc từ đồng nghĩa như `yes/no`; mở rộng quy tắc tùy theo hợp đồng API.

## 9. Autoboxing và unboxing: lúc nào Java tự xử lý?

**Autoboxing** là phép chuyển primitive sang wrapper được hỗ trợ bởi ngôn ngữ:

```java
Integer count = 42;     // boxing int -> Integer
Long length = 100L;     // boxing long -> Long
Boolean enabled = true; // boxing boolean -> Boolean
```

Cùng ý nghĩa giá trị với cách viết tường minh:

```java
Integer count = Integer.valueOf(42);
```

Khi code rõ ràng về kiểu, autoboxing thường ngắn gọn; khi muốn thể hiện quá trình tạo đối tượng, parse chuỗi hoặc chọn đúng overload, `valueOf` thích hợp hơn. Cả hai cách đều **không phải lời hứa cấp phát đối tượng mới**.

**Unboxing** lấy primitive từ wrapper:

```java
Integer boxed = Integer.valueOf(42);
int primitive = boxed;          // unboxing
int explicit = boxed.intValue(); // cùng giá trị
```

### Rủi ro null unboxing

```java
Integer maybe = null;
// int wrong = maybe; // biên dịch được, nhưng ném NullPointerException khi chạy
```

Một kiểu wrapper cho phép `null` ở vị trí tham chiếu, trong khi primitive không thể mang `null`. Để tránh lỗi, **kiểm tra null trước khi unbox** hoặc thiết kế API dùng một kiểu mô tả sự vắng mặt rõ ràng.

```java
Integer maybe = null;
int safeDefault = maybe == null ? 0 : maybe.intValue();
```

Lựa chọn giá trị mặc định 0 là **quyết định nghiệp vụ**, không phải quy tắc tự động của Java. Trong nhiều ứng dụng, phân biệt 0 với “không có giá trị” là bắt buộc; khi đó không thay `null` thành 0 một cách âm thầm.

## 10. Wrapper cache và phép ==: lỗi đặc biệt dễ gặp

Với primitive:

```java
int a = 1000;
int b = 1000;
System.out.println(a == b); // true: so sánh giá trị int
```

Với wrapper:

```java
Integer a = Integer.valueOf(1000);
Integer b = Integer.valueOf(1000);
System.out.println(a.equals(b)); // true: cùng giá trị int 1000
```

Nhưng `a == b` khi hai vế đều là wrapper **so sánh tham chiếu**, không phải giá trị số học. Kết quả có thể phụ thuộc việc factory tái sử dụng đối tượng. **Không dựa vào biểu thức này để kiểm tra nội dung số nguyên**.

Cache của `Integer.valueOf` bao gồm bắt buộc miền `−128..127`. Ví dụ:

```java
Integer a = Integer.valueOf(127);
Integer b = Integer.valueOf(127);
System.out.println(a == b); // true với miền cache bắt buộc
```

Đây chỉ là minh họa khả năng tái sử dụng, **không phải mẫu code so sánh nên dùng**. Để so sánh nội dung:

```java
Integer first = Integer.valueOf(1000);
Integer second = Integer.valueOf(1000);

System.out.println(first.equals(second));    // true
System.out.println(first.compareTo(second)); // 0
```

Khi so sánh với primitive, Java có thể thực hiện unboxing:

```java
Integer boxed = Integer.valueOf(1000);
System.out.println(boxed == 1000); // true: unbox rồi so sánh số
```

Cần đặc biệt đề phòng nếu `boxed` có thể `null`: biểu thức `boxed == 1000` ném `NullPointerException` khi đánh giá.

**Quy tắc thực hành:** wrapper ↔ wrapper dùng `equals` hoặc `compareTo` cho giá trị; primitive ↔ primitive dùng `==`; nếu có `null`, kiểm tra trước hoặc dùng `Objects.equals(a,b)` cho phép so sánh tham chiếu null-safe.

## 11. Collection/generic: wrapper là cần thiết nhưng đừng boxing vô ích

`List<Integer>` hoạt động với đối tượng:

```java
import java.util.ArrayList;
import java.util.List;

List<Integer> numbers = new ArrayList<>();
numbers.add(10);               // autoboxing
numbers.add(Integer.valueOf(20)); // factory tường minh

int sum = 0;
for (Integer n : numbers) {
    sum += n; // unboxing
}
System.out.println(sum); // 30
```

Lưu ý danh sách này **có thể** chứa `null` nếu API cho phép; khi đó `sum += n` gây `NullPointerException`.

Về hiệu năng, các thao tác boxing/unboxing liên tục trong vòng lặp nhiều phần tử có thể tạo overhead và cấp phát tùy giá trị, compiler/JIT và cách sử dụng. Trong khối tính toán số học thuần túy, ưu tiên accumulator primitive:

```java
long total = 0L;
for (int i = 0; i < 1_000_000; i++) {
    total += i; // số học long, không cần bọc mỗi bước
}
Long boxedTotal = Long.valueOf(total); // chỉ bọc tại ranh giới API
```

Đừng biến mọi phép toán thành chuỗi gọi `Integer.valueOf` chỉ vì constructor cũ bị deprecated. Mục tiêu là **thay đúng cách tạo wrapper khi cần**, không phải bắt buộc mọi biến số học đều trở thành wrapper.

## 12. Các kiểu Float, Double: valueOf không chữa sai số nhị phân

`Float.valueOf` và `Double.valueOf` trả wrapper cho giá trị dấu phẩy động:

```java
Float f = Float.valueOf(1.25f);
Double d = Double.valueOf(0.1);
```

Dù `valueOf` là factory được khuyến nghị, nó **không làm số 0.1 trở thành chính xác thập phân**:

```java
Double wrapped = Double.valueOf(0.1);
System.out.println(Double.toHexString(wrapped)); // 0x1.999999999999ap-4
```

`Double.valueOf("0.1")` sẽ parse chuỗi thập phân và tạo wrapper chứa giá trị `double` gần 0.1; đây vẫn là số học nhị phân hữu hạn. Đối với bài toán tiền/decimal exact có yêu cầu scale và rounding, cân nhắc `BigDecimal` tạo từ chuỗi (ví dụ `new BigDecimal("0.1")`), không dùng `Double.valueOf` như giải pháp chính xác tuyệt đối.

Với `Double`, phương thức `equals` có ngữ nghĩa theo các bit chuẩn hóa để xử lý một số trường hợp đặc biệt như NaN và signed zero; nó **không hoàn toàn trùng** với phép so sánh số học `==` trên hai primitive. Khi làm việc với NaN, ±Infinity hoặc ±0.0, chọn quy tắc so sánh đúng hợp đồng nghiệp vụ.

## 13. Các lỗi parse: không tự giả định valueOf chấp nhận mọi đầu vào

Với `Integer.valueOf(String)` và tương tự cho số nguyên:

```java
Integer good = Integer.valueOf("42");

// NumberFormatException:
// Integer bad = Integer.valueOf("42abc");
// Integer tooLarge = Integer.valueOf("2147483648");
// Integer withSpaces = Integer.valueOf(" 42 ");
```

Đây là những lỗi **dữ liệu đầu vào**, không phải lý do quay lại dùng constructor deprecated. Cần xác định quy tắc: có cho phép khoảng trắng trước/sau, dấu cộng, dấu âm, prefix hay giới hạn miền số không?

Nếu cho phép khoảng trắng ASCII đầu/cuối, có thể chuẩn hóa rõ ràng:

```java
static int parseIntWithTrim(String raw) {
    if (raw == null) {
        throw new IllegalArgumentException("Missing integer");
    }
    return Integer.parseInt(raw.trim());
}
```

Đây là phương thức minh họa. `trim()` chỉ là lựa chọn một tập ký tự whitespace theo đặc tả `String`; nếu cần quy tắc Unicode rộng hơn, phải định nghĩa parser phù hợp. Không tự động `trim` dữ liệu định danh hoặc chữ ký số nếu khoảng trắng là một phần của hợp đồng dữ liệu.

### Giữ đúng loại ngoại lệ

- `Integer.valueOf("invalid")` có thể ném `NumberFormatException`.
- `Integer.valueOf(1000)` nhận primitive hợp lệ nên không parse chuỗi.
- Unboxing từ wrapper `null` gây `NullPointerException`.
- `Boolean.valueOf("invalid")` **không** ném `NumberFormatException`; nó trả `false`, nên phải validate riêng nếu nghiêm ngặt.

Không “xử lý lỗi” bằng cách nuốt mọi ngoại lệ rồi trả 0 hoặc false nếu giá trị đó cũng có ý nghĩa nghiệp vụ, vì sẽ khó phân biệt dữ liệu sai với giá trị hợp lệ.

## 14. Chương trình Java 17+ chạy độc lập

Tạo tệp **`WrapperValueOfDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class WrapperValueOfDemo {
    private static boolean parseStrictBoolean(String text) {
        if ("true".equalsIgnoreCase(text)) return true;
        if ("false".equalsIgnoreCase(text)) return false;
        throw new IllegalArgumentException("Expected true or false");
    }

    public static void main(String[] args) {
        Integer boxed = Integer.valueOf(42);
        int parsed = Integer.parseInt("42");
        Integer parsedWrapper = Integer.valueOf("42");

        System.out.println("boxed = " + boxed);
        System.out.println("parsed primitive = " + parsed);
        System.out.println("parsed wrapper = " + parsedWrapper);
        System.out.println("hex = " + Integer.valueOf("FF", 16));
        System.out.println("prefixed hex = " + Integer.decode("0xFF"));

        Integer a = Integer.valueOf(1000);
        Integer b = Integer.valueOf(1000);
        System.out.println("value equals = " + a.equals(b));
        System.out.println("compareTo = " + a.compareTo(b));
        System.out.println("null-safe equals = "
                + Objects.equals(a, b));

        Integer cachedA = Integer.valueOf(127);
        Integer cachedB = Integer.valueOf(127);
        System.out.println("cached reference = " + (cachedA == cachedB));

        System.out.println("boolean TRUE = "
                + Boolean.valueOf("TrUe"));
        System.out.println("boolean yes = "
                + Boolean.valueOf("yes"));

        try {
            parseStrictBoolean("yes");
        } catch (IllegalArgumentException ex) {
            System.out.println("strict boolean: rejected");
        }

        List<Integer> numbers = new ArrayList<>();
        numbers.add(10); // autoboxing
        numbers.add(Integer.valueOf(20));
        int sum = 0;
        for (Integer value : numbers) {
            sum += value; // unboxing
        }
        System.out.println("list sum = " + sum);

        Integer absent = null;
        try {
            int value = absent;
            System.out.println(value);
        } catch (NullPointerException ex) {
            System.out.println("null unboxing: rejected");
        }

        try {
            Integer.valueOf("2147483648");
        } catch (NumberFormatException ex) {
            System.out.println("int parse range: rejected");
        }

        System.out.println("double hex = "
                + Double.toHexString(Double.valueOf(0.1)));
    }
}
```

Chạy ở thư mục chứa tệp:

```bash
javac WrapperValueOfDemo.java
java WrapperValueOfDemo
```

**Đầu ra chuẩn:**

```text
boxed = 42
parsed primitive = 42
parsed wrapper = 42
hex = 255
prefixed hex = 255
value equals = true
compareTo = 0
null-safe equals = true
cached reference = true
boolean TRUE = true
boolean yes = false
strict boolean: rejected
list sum = 30
null unboxing: rejected
int parse range: rejected
double hex = 0x1.999999999999ap-4
```

**Giải thích theo nhóm:**

1. `valueOf(42)` trả wrapper, `parseInt("42")` trả primitive, còn `valueOf("42")` parse chuỗi rồi trả wrapper.
2. `valueOf("FF",16)` dùng radix; `decode("0xFF")` có quy tắc nhận tiền tố.
3. `equals`, `compareTo`, `Objects.equals` thực hiện các cách so sánh nội dung hoặc null-safe, không dựa vào reference identity.
4. Hai giá trị `127` được cache theo hợp đồng của `Integer.valueOf`; ví dụ minh họa cache chứ **không khuyến khích dùng `==`** cho wrapper.
5. `Boolean.valueOf("yes")` trả false; parser nghiêm ngặt tự viết thì từ chối.
6. `List<Integer>` dùng autoboxing khi thêm primitive và unboxing khi cộng dồn.
7. Unboxing `null` ném `NullPointerException`; chuỗi ngoài miền `int` ném `NumberFormatException`.
8. `Double.valueOf(0.1)` vẫn chứa giá trị nhị phân của `double`, không giải quyết sai số biểu diễn.

## 15. Checklist thay constructor trong codebase

Khi gặp `new Integer(...)`, `new Long(...)` hoặc constructor wrapper tương tự, làm lần lượt:

1. **Xác định kiểu đích**: API cần primitive hay wrapper? Nếu cần `int`, `Integer.parseInt` thường tự nhiên hơn `Integer.valueOf`.
2. **Xác định nguồn**: primitive hay chuỗi? Dùng overload `valueOf(primitive)` hoặc `valueOf(String)` cho đúng.
3. **Thay constructor** bằng `valueOf` hoặc autoboxing khi thích hợp; **không giữ `new` chỉ để tạo đối tượng mới**.
4. **Không dựa vào identity**: thay so sánh wrapper `==` bằng `equals`/`compareTo`, hoặc kiểm tra null và so sánh primitive.
5. **Rà soát nullability**: nếu wrapper có thể `null`, xem mọi phép unboxing, tính toán, so sánh với primitive hoặc dùng collection.
6. **Rà soát parser**: đầu vào có thể sai cú pháp, ngoài miền, khác radix, có khoảng trắng, boolean viết tùy ý?
7. **Rà soát hiệu năng**: tránh boxing/unboxing vô ích trong vòng lặp nặng, dùng primitive cho phần tính thuần túy.
8. **Kiểm tra giới hạn kiểu**: `Integer.valueOf` không lưu giá trị ngoài `int`; `Double.valueOf` không tạo decimal exact.
9. **Không dùng wrapper để khóa đồng bộ** hoặc làm identity token; chuyển sang đối tượng khóa được thiết kế riêng nếu cần.
10. **Biên dịch trên phiên bản Java đích** và xử lý cảnh báo deprecated-for-removal thực chất, không bỏ qua do chương trình “vẫn chạy”.

## 16. Những nhầm lẫn thường gặp

| Nhầm lẫn | Điều đúng |
| --- | --- |
| “Constructor wrapper đã deprecated nghĩa là không thể biên dịch trong Java 17” | Có thể vẫn biên dịch với cảnh báo; tránh dùng vì deprecated for removal |
| “`valueOf` luôn cấp phát đối tượng mới” | Factory có thể tái sử dụng đối tượng/cache |
| “`valueOf` luôn cache mọi giá trị” | Chỉ một số miền/hành vi được bảo đảm; không dựa vào identity |
| “`Integer.valueOf("42")` và `Integer.parseInt("42")` trả cùng kiểu” | Một bên trả `Integer`, bên kia trả `int` |
| “`Integer.valueOf("0xFF",16)` giống `Integer.decode("0xFF")`” | Overload radix không tự nhận prefix `0x`; `decode` có quy tắc riêng |
| “Boolean.valueOf("yes") sẽ báo lỗi” | Nó trả false; cần parser nghiêm ngặt nếu muốn từ chối |
| “Hai wrapper số cùng giá trị thì `==` luôn true” | `==` với hai tham chiếu so identity; dùng `equals`/`compareTo` |
| “Autoboxing loại bỏ mọi chi phí” | Boxing/unboxing có thể tạo overhead, đặc biệt trong vòng lặp |
| “Wrapper mặc định null unbox thành 0” | Unboxing `null` ném `NullPointerException` |
| “`Double.valueOf("0.1")` giữ chính xác 1/10” | Nó trả `double` nhị phân gần 0.1, không phải BigDecimal |
| “Mọi wrapper đều kế thừa Number” | `Character` và `Boolean` không kế thừa `Number` |
| “Có thể dùng Integer wrapper làm khóa vì cache giúp chia sẻ” | Wrapper là value-based; không dùng để đồng bộ hoặc dựa vào identity |

## 17. Bài tập có lời giải

**Bài 1.** Viết lại `new Integer(123)` và `new Double("2.5")` theo API được khuyến nghị.  
**Lời giải:** `Integer.valueOf(123)` và `Double.valueOf("2.5")`. Nếu chỉ cần primitive, có thể chọn `123` và `Double.parseDouble("2.5")`.

**Bài 2.** Kiểu trả về của `Integer.parseInt("7")` và `Integer.valueOf("7")` khác nhau thế nào?  
**Lời giải:** lần lượt `int` và `Integer`. Các chữ số được parse tương ứng nhưng kết quả có dạng primitive hay wrapper khác nhau.

**Bài 3.** `Integer.valueOf("FF",16)` cho giá trị gì? Còn `Integer.valueOf("0xFF",16)`?  
**Lời giải:** 255; cách thứ hai ném `NumberFormatException` do prefix. `Integer.decode("0xFF")` là phương án có nhận prefix.

**Bài 4.** Có nên dùng `Integer.valueOf(1000) == Integer.valueOf(1000)` để xác định bằng giá trị?  
**Lời giải:** Không. Dù một implementation có thể cache thêm, `==` trên hai wrapper tham chiếu không phải hợp đồng so sánh số; dùng `equals` hoặc `compareTo`.

**Bài 5.** `Boolean.valueOf("YES")` cho gì, và nên xử lý thế nào nếu chỉ chấp nhận `true/false`?  
**Lời giải:** Cho false. Nếu yêu cầu từ chối mọi token không hợp lệ, validate đúng hai giá trị được chấp nhận rồi mới trả boolean hoặc ném lỗi.

**Bài 6.** Điều gì xảy ra với `Integer absent = null; int x = absent;`?  
**Lời giải:** Câu lệnh biên dịch được, nhưng khi unboxing tại runtime nó ném `NullPointerException`. Cần kiểm tra null hoặc thiết kế API rõ trạng thái vắng mặt.

**Bài 7.** Vì sao dùng `Double.valueOf(0.1)` không giúp tính tiền thập phân chính xác?  
**Lời giải:** Nó chỉ bọc một giá trị `double` nhị phân gần 0.1; bài toán decimal exact nên dùng biểu diễn decimal như `BigDecimal` từ chuỗi, kèm quy tắc làm tròn.

**Bài 8.** Vì sao không nên viết `Integer sum=0;` rồi lặp `sum += x` hàng triệu lần nếu chỉ cộng số nguyên?  
**Lời giải:** Biểu thức có thể unbox/rebox mỗi vòng, tạo overhead và cấp phát; dùng primitive accumulator phù hợp miền giá trị rồi chỉ boxing tại nơi cần đối tượng.

**Bài 9.** `Character.valueOf("A")` có biên dịch không?  
**Lời giải:** Không. Phương thức nhận `char`, nên viết `Character.valueOf('A')`. Một `String` có thể chứa nhiều UTF-16 code unit, vì vậy xử lý ký tự Unicode từ chuỗi cần hợp đồng rõ ràng.

**Bài 10.** Dùng `new Long("9000000000")` được thay bằng gì?  
**Lời giải:** `Long.valueOf("9000000000")` nếu API cần wrapper; `Long.parseLong("9000000000")` nếu cần primitive `long`.

**Ghi nhớ:** Wrapper là **đối tượng biểu diễn giá trị**, không phải nơi dựa vào identity. Khi cần wrapper, ưu tiên `valueOf` hoặc autoboxing thay cho constructor deprecated; khi cần primitive từ chuỗi, dùng `parseXxx`. Luôn kiểm soát parse lỗi, null unboxing, kiểu trả về và cách so sánh.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="number-and-wrappers-007">MIN_VALUE, MAX_VALUE, TYPE và các hằng theo từng lớp</a>

<details>
<summary>Xem chi tiết</summary>

# MIN_VALUE, MAX_VALUE, TYPE và các hằng theo từng lớp trong Java

## 1. Khái niệm

Java có sáu kiểu số nguyên thủy: byte, short, int, long, float, double. Các wrapper tương ứng là **Byte**, **Short**, **Integer**, **Long**, **Float** và **Double**, đều kế thừa lớp trừu tượng **Number**. Các lớp này cung cấp những hằng **public static final** để tra cứu giới hạn biểu diễn, số bit, số byte và kiểu nguyên thủy liên quan.

**Character**, **Boolean** cũng là wrapper nhưng không kế thừa Number. **Void** có TYPE đại diện cho void, dù void không chứa giá trị để tính toán.

~~~java
int smallest = Integer.MIN_VALUE;
int largest = Integer.MAX_VALUE;
Class<Integer> primitiveType = Integer.TYPE;
~~~

Các trường trên thuộc **lớp**, không cần khởi tạo đối tượng Integer. Điểm quan trọng nhất: ý nghĩa **MIN_VALUE** ở các wrapper nguyên **khác** với Float và Double.

## 2. Bảng hằng cơ bản

| Lớp | MIN_VALUE | MAX_VALUE | TYPE đại diện | SIZE (bit) | BYTES |
| --- | --- | --- | --- | ---: | ---: |
| Byte | −128 | 127 | byte.class | 8 | 1 |
| Short | −32768 | 32767 | short.class | 16 | 2 |
| Integer | −2147483648 | 2147483647 | int.class | 32 | 4 |
| Long | −9223372036854775808 | 9223372036854775807 | long.class | 64 | 8 |
| Float | 2^−149, **dương** | khoảng 3.4028235 × 10^38, dương hữu hạn | float.class | 32 | 4 |
| Double | 2^−1074, **dương** | khoảng 1.7976931348623157 × 10^308, dương hữu hạn | double.class | 64 | 8 |

Các số nguyên có dấu n bit dùng phạm vi **−2^(n−1)** đến **2^(n−1)−1**. Vì vậy Integer.MIN_VALUE âm hơn giá trị đối của Integer.MAX_VALUE một đơn vị.

Ngược lại, **Float.MIN_VALUE/Double.MIN_VALUE** là giá trị dương khác 0 **nhỏ nhất** theo định dạng IEEE 754, không phải giá trị âm hữu hạn nhỏ nhất. Hãy luôn đọc ý nghĩa của trường theo lớp chứa nó.

## 3. Byte, Short, Integer, Long và phép toán tràn

Byte và Short có MIN_VALUE/MAX_VALUE lần lượt là **−128/127** và **−32768/32767**. Chúng có kiểu trường **byte/short**, nhưng phép cộng thường nâng toán hạng thành int:

~~~java
byte b = Byte.MAX_VALUE;
int next = b + 1;                   // 128, kiểu int
byte wrapped = (byte) (b + 1);     // -128
// byte invalid = b + 1;           // lỗi biên dịch: int không tự thu hẹp về byte
~~~

Với Integer, phép cộng vẫn trong 32 bit nếu không có toán hạng long:

~~~java
int overflowed = Integer.MAX_VALUE + 1;       // -2147483648
long stillWrong = Integer.MAX_VALUE + 1;      // -2147483648L
long correct = (long) Integer.MAX_VALUE + 1;  // 2147483648L
~~~

Việc gán vào long **sau phép tính** không tránh overflow của int. Nếu cần báo lỗi khi tràn, dùng **Math.addExact**, **Math.subtractExact**, **Math.multiplyExact** hoặc **Math.negateExact**, tùy phép toán.

Hai tình huống biên của số nguyên có dấu:

~~~java
int min = Integer.MIN_VALUE;
int negated = -min;               // vẫn -2147483648
int absolute = Math.abs(min);     // vẫn -2147483648
long positive = -(long) min;      // 2147483648L
~~~

**−Integer.MIN_VALUE** không nằm trong miền int. Tương tự **−Long.MIN_VALUE** không nằm trong miền long. Nếu cần giá trị lớn hơn Long.MAX_VALUE, hãy dùng **BigInteger** để tính toán chính xác.

**Không dấu:** Integer.MAX_VALUE là cực đại **có dấu**. Cùng mẫu 32 bit toàn số 1 có giá trị int **−1**, nhưng **Integer.toUnsignedString(-1)** trả chuỗi **4294967295**. Tương tự, **Long.toUnsignedString(-1L)** trả **18446744073709551615**. Java không có primitive unsigned int/unsigned long riêng; đây là API diễn giải mẫu bit theo quy ước không dấu.

## 4. Float và Double: số normal, subnormal và infinity

| Hằng | Float (binary32) | Double (binary64) |
| --- | --- | --- |
| MIN_VALUE | 2^−149 ≈ 1.4 × 10^−45 | 2^−1074 ≈ 4.9 × 10^−324 |
| MIN_NORMAL | 2^−126 ≈ 1.17549435 × 10^−38 | 2^−1022 ≈ 2.2250738585072014 × 10^−308 |
| MAX_VALUE | ≈ 3.4028235 × 10^38 | ≈ 1.7976931348623157 × 10^308 |
| POSITIVE_INFINITY | +Infinity | +Infinity |
| NEGATIVE_INFINITY | −Infinity | −Infinity |
| NaN | Not a Number | Not a Number |
| MIN_EXPONENT | −126 | −1022 |
| MAX_EXPONENT | 127 | 1023 |

**MIN_NORMAL** là normal dương nhỏ nhất. **MIN_VALUE** là **subnormal dương nhỏ nhất**, nhỏ hơn MIN_NORMAL. Điều này giúp Java có gradual underflow: một kết quả nhỏ hơn normal nhỏ nhất chưa nhất thiết bị làm tròn ngay về 0.

Để lấy **số âm hữu hạn nhỏ nhất**, dùng **−Float.MAX_VALUE** hoặc **−Double.MAX_VALUE**. **NEGATIVE_INFINITY** còn nhỏ hơn mọi số hữu hạn nhưng bản thân không hữu hạn.

~~~java
float minPositive = Float.MIN_VALUE;       // dương khác 0
float mostNegativeFinite = -Float.MAX_VALUE;
boolean a = Float.MIN_VALUE > 0.0f;        // true
boolean b = Float.POSITIVE_INFINITY > Float.MAX_VALUE; // true
~~~

Kết quả quá lớn có thể overflow thành Infinity; một phép toán không xác định có thể tạo NaN. **NaN == NaN** trả false, vì vậy kiểm tra bằng **Float.isNaN/Double.isNaN**; dùng **isFinite** khi chỉ cho phép giá trị hữu hạn.

**MIN_EXPONENT/MAX_EXPONENT** nói về số mũ của miền normal hữu hạn, không có nghĩa subnormal không thể nhỏ đến 2^−149 hoặc 2^−1074.

Độ chính xác significand normal của float là **24 bit**, double là **53 bit**. Hai trường **Float.PRECISION** và **Double.PRECISION** được bổ sung từ **Java 19**; chúng **không có trong Java 17**, dù các con số 24 và 53 đã được đặc tả trước đó. Ví dụ dùng **javac --release 17** không được truy cập hai trường này.

## 5. TYPE khác với lớp wrapper

**Integer.TYPE** trỏ đến đối tượng Class đại diện primitive **int**, chính là **int.class**. **Integer.class** trỏ đến lớp **java.lang.Integer**:

~~~java
boolean same = Integer.TYPE == int.class;        // true
boolean different = Integer.TYPE == Integer.class; // false
boolean primitive = Integer.TYPE.isPrimitive();  // true
boolean wrapper = Integer.class.isPrimitive();    // false
~~~

Các trường TYPE tương ứng:

| Wrapper | TYPE |
| --- | --- |
| Byte | byte.class |
| Short | short.class |
| Integer | int.class |
| Long | long.class |
| Float | float.class |
| Double | double.class |
| Character | char.class |
| Boolean | boolean.class |
| Void | void.class |

Ví dụ trong reflection: **Math.class.getMethod("abs", Integer.TYPE)** tìm overload nhận primitive int; dùng Integer.class không khớp chữ ký primitive. Đoạn minh họa này cần xử lý **NoSuchMethodException** khi đặt trong mã có thể chạy.

**Integer.TYPE** không chứa số nguyên; nó chứa đối tượng Class. Ngược lại, **Integer.valueOf(42)** tạo/lấy wrapper chứa giá trị 42. Nếu gọi **getClass()** trên Integer object, kết quả là Integer.class chứ không phải Integer.TYPE.

## 6. SIZE và BYTES

~~~java
int a = Byte.SIZE;        // 8 bit
int b = Short.BYTES;      // 2 byte
int c = Integer.SIZE;     // 32 bit
int d = Long.BYTES;       // 8 byte
int e = Double.SIZE;      // 64 bit
~~~

**SIZE/BYTES mô tả độ rộng dữ liệu nguyên thủy**. Không suy rằng đối tượng Long trên heap chỉ tốn Long.BYTES = 8 byte: đối tượng còn header, alignment và các chi phí lưu trữ khác tùy JVM.

## 7. Character, Boolean và Void

**Character.MIN_VALUE = '\u0000'**, **Character.MAX_VALUE = '\uFFFF'**. Theo giá trị số nguyên, miền char là **0..65535**, không dấu, có **SIZE = 16**, **BYTES = 2**.

~~~java
char first = Character.MIN_VALUE;
char last = Character.MAX_VALUE;
int lastCodeUnit = (int) last; // 65535
~~~

Char là **một code unit UTF-16**, không phải mọi Unicode code point. **Character.MAX_CODE_POINT = 0x10FFFF** lớn hơn Character.MAX_VALUE; code point ngoài BMP được biểu diễn bằng cặp surrogate. Character còn có **MIN_RADIX = 2**, **MAX_RADIX = 36**, các hằng hỗ trợ thao tác cơ số/Unicode.

**Boolean.TRUE**, **Boolean.FALSE** là hằng đối tượng Boolean; **Boolean.TYPE == boolean.class**. Boolean **không có** MIN_VALUE, MAX_VALUE, SIZE hay BYTES, vì boolean không phải số nguyên.

**Void.TYPE == void.class** dùng trong reflection và mô tả kiểu trả về không chứa giá trị. Void cũng **không có** MIN_VALUE/MAX_VALUE hoặc SIZE/BYTES.

## 8. Number, BigInteger và BigDecimal

**Number** là lớp trừu tượng có các phương thức chuyển đổi số như intValue(), longValue(), doubleValue(); nó không ấn định miền số hay trường MIN_VALUE/MAX_VALUE/TYPE chung cho tất cả lớp con.

Hai lớp số quan trọng khác trong java.math:

- **BigInteger**: số nguyên độ chính xác tùy ý, có các hằng **ZERO**, **ONE**, **TWO** (Java 9+), **TEN**.
- **BigDecimal**: số thập phân biểu diễn bằng unscaled value và scale, có các hằng **ZERO**, **ONE**, **TEN**. **BigDecimal.TWO** không tồn tại trong Java 17; dùng BigDecimal.valueOf(2).

~~~java
java.math.BigInteger beyondLong =
        java.math.BigInteger.valueOf(Long.MAX_VALUE)
            .add(java.math.BigInteger.ONE);
java.math.BigDecimal zero = java.math.BigDecimal.ZERO;
~~~

Đừng gọi BigInteger.MAX_VALUE hay BigDecimal.MIN_VALUE: chúng không có miền giá trị cố định như primitive. Tính toán BigDecimal có thể cần **MathContext/RoundingMode** để diễn đạt độ chính xác và quy tắc làm tròn theo nghiệp vụ.

## 9. Ví dụ Java 17+ chạy độc lập

Lưu thành **WrapperConstantsDemo.java**:

~~~java
public class WrapperConstantsDemo {
    public static void main(String[] args) {
        System.out.println("Byte range: " + Byte.MIN_VALUE + " .. " + Byte.MAX_VALUE);
        System.out.println("Short range: " + Short.MIN_VALUE + " .. " + Short.MAX_VALUE);
        System.out.println("Integer range: " + Integer.MIN_VALUE + " .. " + Integer.MAX_VALUE);
        System.out.println("Long range: " + Long.MIN_VALUE + " .. " + Long.MAX_VALUE);

        System.out.println("Integer bits/bytes: " + Integer.SIZE + "/" + Integer.BYTES);
        System.out.println("Float exponents: " + Float.MIN_EXPONENT
                + " .. " + Float.MAX_EXPONENT);
        System.out.println("Double exponents: " + Double.MIN_EXPONENT
                + " .. " + Double.MAX_EXPONENT);

        System.out.println("Integer.TYPE == int.class: "
                + (Integer.TYPE == int.class));
        System.out.println("Integer.TYPE == Integer.class: "
                + (Integer.TYPE == Integer.class));
        System.out.println("Integer.TYPE name: " + Integer.TYPE.getName());
        System.out.println("Integer.class name: " + Integer.class.getName());
        System.out.println("Void.TYPE == void.class: "
                + (Void.TYPE == void.class));

        System.out.println("Character range: " + (int) Character.MIN_VALUE
                + " .. " + (int) Character.MAX_VALUE);
        System.out.println("Character bits/bytes: "
                + Character.SIZE + "/" + Character.BYTES);
        System.out.println("Boolean constants: "
                + Boolean.TRUE + "/" + Boolean.FALSE);

        System.out.println("Integer MAX + 1: " + (Integer.MAX_VALUE + 1));
        System.out.println("Byte MAX + 1: " + (Byte.MAX_VALUE + 1));
        System.out.println("Math.abs(Integer.MIN_VALUE): "
                + Math.abs(Integer.MIN_VALUE));
        System.out.println("Unsigned int max: "
                + Integer.toUnsignedString(-1));

        System.out.println("Float.MIN_VALUE: "
                + Float.toHexString(Float.MIN_VALUE));
        System.out.println("Float.MIN_NORMAL: "
                + Float.toHexString(Float.MIN_NORMAL));
        System.out.println("Double.MIN_VALUE: "
                + Double.toHexString(Double.MIN_VALUE));
        System.out.println("Double.MIN_NORMAL: "
                + Double.toHexString(Double.MIN_NORMAL));
        System.out.println("Float.MIN_VALUE > 0: "
                + (Float.MIN_VALUE > 0.0f));
        System.out.println("-Float.MAX_VALUE < Float.MIN_VALUE: "
                + (-Float.MAX_VALUE < Float.MIN_VALUE));
        System.out.println("Float infinity > MAX: "
                + (Float.POSITIVE_INFINITY > Float.MAX_VALUE));
        System.out.println("Float.isNaN: " + Float.isNaN(Float.NaN));
        System.out.println("NaN == NaN: " + (Float.NaN == Float.NaN));

        System.out.println("Integer extends Number: "
                + Number.class.isAssignableFrom(Integer.class));
        System.out.println("Character extends Number: "
                + Number.class.isAssignableFrom(Character.class));
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 WrapperConstantsDemo.java
java WrapperConstantsDemo
~~~

Stdout:

~~~text
Byte range: -128 .. 127
Short range: -32768 .. 32767
Integer range: -2147483648 .. 2147483647
Long range: -9223372036854775808 .. 9223372036854775807
Integer bits/bytes: 32/4
Float exponents: -126 .. 127
Double exponents: -1022 .. 1023
Integer.TYPE == int.class: true
Integer.TYPE == Integer.class: false
Integer.TYPE name: int
Integer.class name: java.lang.Integer
Void.TYPE == void.class: true
Character range: 0 .. 65535
Character bits/bytes: 16/2
Boolean constants: true/false
Integer MAX + 1: -2147483648
Byte MAX + 1: 128
Math.abs(Integer.MIN_VALUE): -2147483648
Unsigned int max: 4294967295
Float.MIN_VALUE: 0x0.000002p-126
Float.MIN_NORMAL: 0x1.0p-126
Double.MIN_VALUE: 0x0.0000000000001p-1022
Double.MIN_NORMAL: 0x1.0p-1022
Float.MIN_VALUE > 0: true
-Float.MAX_VALUE < Float.MIN_VALUE: true
Float infinity > MAX: true
Float.isNaN: true
NaN == NaN: false
Integer extends Number: true
Character extends Number: false
~~~

Giải thích: Byte.MAX_VALUE + 1 đã được nâng thành int nên là 128; Integer.MAX_VALUE + 1 tràn int; Integer.TYPE là int.class; Float.MIN_VALUE vẫn dương và nhỏ hơn Float.MIN_NORMAL. Hex của float/double hiển thị mẫu giá trị dạng nhị phân với số mũ qua **toHexString**, thuận tiện kiểm tra số rất nhỏ.

## 10. Sử dụng đúng trong ứng dụng

| Mục tiêu | Hằng/API phù hợp |
| --- | --- |
| Kiểm tra phạm vi signed byte trước khi cast | Byte.MIN_VALUE / Byte.MAX_VALUE |
| Kiểm tra tràn phép cộng int | Math.addExact, kết hợp miền Integer.MIN_VALUE/MAX_VALUE |
| Tìm số dương khác 0 nhỏ nhất của double | Double.MIN_VALUE |
| Tìm số normal dương nhỏ nhất của double | Double.MIN_NORMAL |
| Tìm số âm hữu hạn nhỏ nhất của double | −Double.MAX_VALUE |
| Phân biệt NaN và infinity | Double.isNaN / Double.isFinite / Double.isInfinite |
| Lấy Class primitive cho reflection | Integer.TYPE, Long.TYPE hoặc primitive.class |
| Tính số byte primitive long | Long.BYTES |
| Biểu diễn nguyên lớn hơn Long.MAX_VALUE | BigInteger |
| Lấy số 0 thập phân chính xác | BigDecimal.ZERO |

Hãy kiểm tra rõ **miền đầu vào** của bài toán. Ví dụ, một giá trị tiền cần quy tắc thập phân; một trường 8 bit dữ liệu mạng có thể là byte không dấu 0..255, khác signed Byte.MIN_VALUE..MAX_VALUE. Chọn hằng dựa trên ý nghĩa dữ liệu thay vì tên hằng giống nhau.

## 11. Các nhầm lẫn cần tránh

| Sai lầm | Giải thích đúng |
| --- | --- |
| Float.MIN_VALUE là số float âm nhất | Đó là số **dương subnormal khác 0 nhỏ nhất** |
| Double.MIN_VALUE là normal nhỏ nhất | Normal nhỏ nhất là **Double.MIN_NORMAL** |
| Integer.MAX_VALUE là unsigned int cực đại | Nó là cực đại signed int |
| Integer.TYPE bằng Integer.class | TYPE là **int.class**; wrapper Class khác primitive Class |
| SIZE/BYTES đo kích thước đối tượng | Chúng đo độ rộng dữ liệu primitive |
| Character.MAX_VALUE là Unicode code point cực đại | Nó là UTF-16 code unit tối đa, 0xFFFF |
| Boolean có MIN_VALUE/MAX_VALUE | Boolean chỉ có hằng TRUE/FALSE/TYPE liên quan chủ đề |
| Number khai báo MAX_VALUE chung | Lớp Number không khai báo hằng miền đó |
| BigInteger có MAX_VALUE | BigInteger không có cực đại cố định như long |
| Float.PRECISION có sẵn trong Java 17 | Trường PRECISION được bổ sung từ Java 19 |
| Gán biểu thức int cho long sẽ tránh tràn int | Mở rộng trước khi tính hoặc dùng exact API |
| Math.abs(Integer.MIN_VALUE) luôn không âm | Nó giữ Integer.MIN_VALUE do không biểu diễn được số đối |

## 12. Bài tập tự kiểm tra

**Bài 1.** Byte.MAX_VALUE + 1 có giá trị và kiểu biểu thức gì?

**Đáp án:** **128**, kiểu int, vì numeric promotion.

**Bài 2.** Integer.MAX_VALUE + 1 và (long) Integer.MAX_VALUE + 1 khác nhau thế nào?

**Đáp án:** Phép đầu tràn về −2147483648; phép sau là 2147483648L.

**Bài 3.** Số float âm hữu hạn nhỏ nhất dùng hằng gì?

**Đáp án:** **−Float.MAX_VALUE**, không phải Float.MIN_VALUE.

**Bài 4.** Integer.TYPE == int.class và Integer.TYPE == Integer.class lần lượt trả gì?

**Đáp án:** **true** và **false**.

**Bài 5.** Tại sao Character.MAX_VALUE không bằng Character.MAX_CODE_POINT?

**Đáp án:** char là code unit UTF-16 16 bit, còn code point Unicode có thể lớn hơn 0xFFFF.

**Bài 6.** Có thể truy cập trực tiếp Float.PRECISION khi biên dịch bằng Java 17 không?

**Đáp án:** Không; trường này được thêm từ Java 19. Giá trị significand float vẫn là 24 bit theo đặc tả.

**Bài 7.** BigInteger có MIN_VALUE và MAX_VALUE giống Integer không?

**Đáp án:** Không; nó dùng biểu diễn số nguyên độ chính xác tùy ý, giới hạn thực tế phụ thuộc tài nguyên/triển khai.

## 13. Tài liệu đối chiếu

- [Byte API Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Byte.html), [Short API Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Short.html), [Integer API Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html), [Long API Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Long.html).
- [Float API Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Float.html), [Double API Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html).
- [Character API Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html), [Boolean API Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Boolean.html), [Void API Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Void.html).
- [Number API Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Number.html), [BigInteger API Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigInteger.html), [BigDecimal API Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html).
- [Java Language Specification SE 17 — Primitive Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.2) và [Conversions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="number-and-wrappers-008">compare, compareTo, equals và hashCode</a>

<details>
<summary>Xem chi tiết</summary>

# compare, compareTo, equals và hashCode — Java Core: Number và wrapper

## 1. Vì sao cùng là “so sánh số” mà Java có nhiều cách?

Java có nhiều kiểu số nguyên thủy (`byte`, `short`, `int`, `long`, `float`, `double`) và các lớp wrapper tương ứng (`Byte`, `Short`, `Integer`, `Long`, `Float`, `Double`). Ngoài phép so sánh bằng toán tử `==`, người lập trình còn gặp:

- `Integer.compare(a, b)`, `Long.compare(a, b)`, `Double.compare(a, b)`…: **phương thức static** so sánh hai giá trị cùng kiểu primitive.
- `wrapperA.compareTo(wrapperB)`: **phương thức instance** so sánh thứ tự giữa các wrapper cùng lớp.
- `wrapperA.equals(other)`: **phương thức instance** quyết định hai đối tượng có được coi là cùng giá trị theo hợp đồng của lớp không.
- `wrapperA.hashCode()`: mã băm phù hợp với `equals` khi làm khóa trong `HashMap`, phần tử trong `HashSet`.
- Các toán tử `==`, `<`, `>`, `<=`, `>=`: so sánh **primitive** hoặc có thể kích hoạt unboxing khi toán hạng là wrapper.

Chúng có liên quan nhưng **không thay thế nhau một cách tùy tiện**. Ví dụ:

```java
Integer a = 9;
Long b = 9L;

System.out.println(a.equals(b)); // false
System.out.println(a.longValue() == b.longValue()); // true
```

`Integer.equals` không xem `Long` cùng lượng toán học là cùng kiểu đối tượng. Ngược lại, khi trích giá trị primitive và so sánh đúng kiểu, `9L == 9L` là true.

**Mục tiêu bài học:**

1. Phân biệt so sánh **giá trị**, **kiểu đối tượng**, **identity** và **thứ tự**.
2. Dùng đúng `compare` và `compareTo` cho signed integer, unsigned interpretation và floating point.
3. Hiểu `equals`/`hashCode`, hợp đồng bắt buộc và ảnh hưởng tới collection.
4. Xử lý đúng `Float`/`Double` với `NaN`, `+0.0` và `-0.0`.
5. Biết vì sao `==` trên wrapper không đáng dùng để so sánh giá trị đối tượng, đặc biệt với cache/boxing.
6. Tránh comparator bằng phép trừ, tránh so sánh “gần bằng epsilon” làm equality hoặc sắp xếp.
7. Đọc và kiểm thử các ví dụ Java 17+ với đầu ra có thể lặp lại.

**Phạm vi:** Number và các wrapper chuẩn Java, cách chúng tham gia so sánh/collection. `BigDecimal` và `BigInteger` chỉ được đưa vào để làm rõ trường hợp so sánh khác nhau trong họ `Number`, không biến bài này thành hướng dẫn tính toán thập phân chi tiết.

## 2. Kiểu primitive, wrapper và lớp `Number`

| Primitive | Wrapper | Họ kiểu trong Java | Comparable |
| --- | --- | --- | --- |
| `byte` | `Byte` | Kế thừa `Number` | `Comparable<Byte>` |
| `short` | `Short` | Kế thừa `Number` | `Comparable<Short>` |
| `int` | `Integer` | Kế thừa `Number` | `Comparable<Integer>` |
| `long` | `Long` | Kế thừa `Number` | `Comparable<Long>` |
| `float` | `Float` | Kế thừa `Number` | `Comparable<Float>` |
| `double` | `Double` | Kế thừa `Number` | `Comparable<Double>` |

`Number` là abstract class cung cấp cách **lấy giá trị dưới những kiểu số nhất định** qua `intValue()`, `longValue()`, `floatValue()`, `doubleValue()` và các phương thức chuyển đổi khác. Nó **không đưa ra một `compareTo` chung để so sánh mọi `Number`** và không quy định mọi subclass phải dùng chung một định nghĩa `equals`.

Điều đó có nghĩa:

```java
Number a = Integer.valueOf(5);
Number b = Double.valueOf(5.0);
```

Không thể đơn giản gọi `a.compareTo(b)` trên kiểu khai báo `Number`; `Number` không có `compareTo(Number)`. Nếu gọi `a.equals(b)`, dispatch tới `Integer.equals` và trả false. Cần xác định rõ chính sách so sánh khi nhận nhiều subclass `Number`; chuyển hết sang `double` cũng không tự an toàn vì có thể mất chính xác với số nguyên lớn hoặc `BigDecimal`.

### 2.1. Auto-boxing và unboxing

`Integer n = 9;` có thể được compiler chuyển thành thao tác boxing. `int x = n;` có thể được chuyển thành unboxing, tương đương lấy giá trị primitive. Khi có `null`:

```java
Integer missing = null;
// int x = missing; // runtime NullPointerException do unboxing
```

Các phương thức `compareTo` và `equals` cũng có quy tắc về null: `a.equals(null)` trả false nếu `a` khác null, trong khi `a.compareTo(null)` với `Integer a` không null thường ném `NullPointerException`. Nếu receiver `a` chính nó null, gọi method sẽ ném `NullPointerException`.

## 3. Bảng quyết định nhanh giữa các cách so sánh

| Mục đích | Cách dùng | Ghi chú |
| --- | --- | --- |
| Hai `int` có cùng giá trị? | `a == b` | Primitive equality |
| So thứ tự hai `int` không lo overflow? | `Integer.compare(a,b)` | Trả giá trị âm/0/dương |
| So thứ tự hai `long` | `Long.compare(a,b)` | Không dùng `(int)(a-b)` |
| So thứ tự hai wrapper cùng kiểu | `a.compareTo(b)` | Receiver/đối số phải có kiểu phù hợp |
| Hai wrapper có bằng theo hợp đồng lớp? | `a.equals(b)` | Null-safe nếu `a` chắc chắn khác null |
| Wrapper có thể null ở hai vế? | `Objects.equals(a,b)` | Trả true nếu hai tham chiếu đều null |
| Wrapper làm khóa hash? | `equals` đi cùng `hashCode` | Java wrappers đã có hợp đồng phù hợp |
| Sắp xếp dữ liệu số `double` | `Double.compare(a,b)` | Có quy tắc với NaN, signed zero |
| Diễn giải bit `int` dưới dạng không dấu | `Integer.compareUnsigned(a,b)` | Không thay bit, chỉ đổi thứ tự |
| Kiểm tra kết quả số thực nằm trong sai số? | Hàm `near(a,b,tolerance)` riêng | Không dùng làm `equals`/`compareTo` mặc định |

Cần phân biệt ý nghĩa `compare(a,b) < 0` với `compare(a,b) == -1`. Hợp đồng so sánh tổng quát chỉ bảo đảm **dấu của kết quả**, không bảo đảm luôn chỉ có `-1`, `0`, `1`. Ví dụ `Byte.compare((byte)-1, (byte)1)` có thể trả `-2` theo đặc tả phép trừ giá trị byte trong phương thức đó. Hãy viết `< 0`, `== 0`, `> 0`.

## 4. `compare`: phương thức static nhận primitive

Các lớp wrapper số có các phương thức so sánh static:

```java
int c1 = Integer.compare(9, 12);       // âm
int c2 = Long.compare(9L, 12L);        // âm
int c3 = Float.compare(9.0f, 12.0f);   // âm
int c4 = Double.compare(9.0, 12.0);    // âm
```

Dù tên thuộc lớp wrapper, đầu vào của các phương thức này là **primitive** (ví dụ `int,int` hoặc `double,double`) và kết quả là `int` biểu thị thứ tự.

Với `Byte`/`Short` cũng có `Byte.compare(byte,byte)`, `Short.compare(short,short)`. Các phương thức này hữu ích khi viết `Comparator` bằng lambda hoặc phương thức tham chiếu.

### 4.1. Không so sánh số nguyên bằng phép trừ

Đoạn mã sau có thể sai:

```java
// KHÔNG NÊN DÙNG:
Comparator<Integer> bad = (a, b) -> a - b;
```

Nếu `a=Integer.MAX_VALUE` và `b=-1`, hiệu số toán học vượt miền `int`; kết quả phép trừ `int` tràn, làm sai dấu thứ tự. Hãy dùng:

```java
Comparator<Integer> order = Integer::compare;
```

Hoặc:

```java
Comparator<Integer> order = Comparator.naturalOrder();
```

Đoạn `Comparator` cần import `java.util.Comparator` hoặc viết tên đầy đủ. Với `long`, tránh `return (int)(a-b);` vì vừa có nguy cơ `long` overflow vừa cắt xuống 32 bit; dùng `Long.compare(a,b)`.

### 4.2. Static compare trả thứ tự, không phải boolean

`Integer.compare(10,10)` trả số nguyên `0`. Nó **không** trả `true`; muốn hỏi bằng nhau thông qua compare, viết `Integer.compare(a,b)==0`, dù với primitive `int` thông thường `a==b` rõ hơn.

## 5. `compareTo`: hợp đồng Comparable của wrapper

Các wrapper triển khai `Comparable` cho **cùng loại đối tượng**. Ví dụ:

```java
Integer a = 9;
Integer b = 12;
System.out.println(a.compareTo(b)); // -1
```

Kết quả âm: `a` đứng trước `b`; kết quả 0: cùng vị trí theo natural ordering; kết quả dương: `a` đứng sau `b`.

`Integer.compareTo` nhận `Integer`; `Long.compareTo` nhận `Long`. Chúng **không** cho phép:

```java
Integer left = 9;
Long right = 9L;
// left.compareTo(right); // KHÔNG BIÊN DỊCH: Long không phải Integer
```

Tương tự, `Number` không có một natural ordering chung cho mọi subclass. `BigDecimal` và `BigInteger` có compareTo riêng của từng lớp.

### 5.1. Natural ordering và `Collections.sort`

Với một `List<Integer>`, có thể gọi `Collections.sort(list)` hoặc `list.sort(null)` để dùng natural ordering của `Integer`. Với một `List<Double>`, natural ordering của `Double` có quy tắc đặc biệt cho `NaN` và signed zero.

Đừng giả định rằng danh sách kiểu `List<Number>` trộn `Integer` với `Double` sẽ tự có một comparator đúng. Hãy chọn **mô hình dữ liệu đồng nhất** hoặc tự định nghĩa một thứ tự có hợp đồng rõ ràng.

### 5.2. `compareTo` và `equals` không luôn đồng nhất ở mọi loại Number

Các wrapper `Byte`, `Short`, `Integer`, `Long`, `Float`, `Double` thiết kế natural ordering phù hợp với equality của chính lớp (đặc biệt `Float`/`Double` xử lý NaN/zero theo contract của wrapper).

Nhưng một subclass `Number` khác, như `BigDecimal`, có khác biệt:

```java
BigDecimal x = new BigDecimal("2.0");
BigDecimal y = new BigDecimal("2.00");

System.out.println(x.compareTo(y)); // 0
System.out.println(x.equals(y));    // false
```

Hai `BigDecimal` bằng về **giá trị số** nhưng khác **scale**, nên `equals` trả false. Đoạn trên cần `import java.math.BigDecimal;`. Tình huống này cho thấy không thể khẳng định mọi đối tượng kế thừa `Number` có `compareTo()==0` đồng nghĩa `equals()==true`.

## 6. `equals`: bằng nhau theo hợp đồng lớp, không chỉ cùng con số

Với `Integer`:

```java
System.out.println(Integer.valueOf(9)
        .equals(Integer.valueOf(9))); // true

System.out.println(Integer.valueOf(9)
        .equals(Long.valueOf(9L)));   // false
```

`Integer.equals` yêu cầu đối tượng đối chiếu **là một `Integer`** và có cùng giá trị primitive `int`. `Long.equals` tương tự trong lớp `Long`. Vì thế `Integer.valueOf(9)` và `Long.valueOf(9)` không bằng theo `equals` dù cùng lượng toán học.

Một số ví dụ phân biệt:

| So sánh | Kết quả | Vì sao |
| --- | --- | --- |
| `Integer.valueOf(9).equals(Integer.valueOf(9))` | true | Cùng class, cùng giá trị |
| `Integer.valueOf(9).equals(Long.valueOf(9))` | false | Khác class |
| `Double.valueOf(9.0).equals(Float.valueOf(9.0f))` | false | Khác class |
| `Long.valueOf(9).equals(null)` | false | `equals(null)` |
| `Integer.valueOf(9).intValue() == 9` | true | Primitive int equality |
| `Integer.valueOf(9).longValue() == Long.valueOf(9L).longValue()` | true | Sau chuyển đổi phù hợp, primitive long equality |

**Không biến mọi `Number` thành `double` chỉ để so sánh.** `double` có 53 bit precision significand; nhiều giá trị `long` lớn hơn `2⁵³` không còn phân biệt chính xác. `BigDecimal.doubleValue()` cũng có thể làm tròn hoặc tràn sang vô cực, gây mất thông tin. Nếu domain thật sự cần so sánh giữa các loại `Number`, phải định nghĩa yêu cầu về miền/độ chính xác và chọn biểu diễn trung gian thích hợp.

### 6.1. `Objects.equals` chỉ xử lý null, không làm ép kiểu tương đương

`java.util.Objects.equals(a,b)` trả true nếu cả hai null; nếu một bên null thì false; nếu cả hai khác null thì sử dụng `a.equals(b)`.

`Objects.equals(Integer.valueOf(9), Long.valueOf(9))` vẫn false. Đây không phải phép “so sánh giá trị số tổng quát” mà là cách gọi `equals` an toàn khi có null.

## 7. `hashCode`: hợp đồng với `equals`

`hashCode` là phương thức từ `Object`. Với hai đối tượng `x`, `y`:

> Nếu `x.equals(y)` là true, thì **bắt buộc** `x.hashCode() == y.hashCode()`.

Chiều ngược lại **không bắt buộc**: hai đối tượng khác nhau vẫn có thể cùng hash, gọi là **hash collision**. Hash không phải mã định danh duy nhất và không bảo đảm tính bảo mật.

Các wrapper triển khai `hashCode` theo nội dung giá trị phù hợp `equals`:

- `Integer.hashCode(int value)` trả chính `int` đầu vào.
- `Long.hashCode(long value)` gộp các bit cao/thấp của `long` xuống `int`.
- `Float.hashCode(float value)` và `Double.hashCode(double value)` dựa trên biểu diễn bit phù hợp hợp đồng `equals`, gồm phân biệt signed zero và canonicalize NaN.
- `Byte` và `Short` dùng giá trị số tương ứng cho hash, theo quy tắc của từng API.

`Integer.hashCode(9)` là một **static method**, còn `Integer.valueOf(9).hashCode()` là **instance method**. Cả hai tạo hash tương ứng với cùng giá trị.

### 7.1. HashMap và HashSet

`HashMap`/`HashSet` dùng hash để tìm bucket ứng viên rồi dùng equality của khóa để xác định trùng hay không. Do đó:

```java
Set<Number> values = new HashSet<>();
values.add(Integer.valueOf(9));
values.add(Long.valueOf(9L));
System.out.println(values.size()); // 2
```

Cần import `java.util.Set` và `java.util.HashSet`; đoạn này là mảnh code đặt trong một phương thức. `Integer(9)` và `Long(9)` không bằng nhau theo `equals`, nên là hai phần tử khác nhau.

**Không dùng `hashCode` để kết luận hai đối tượng bằng nhau.** Hash giống chỉ là điều kiện kiểm tra nhanh, không thay `equals`.

**Không chọn hash ngẫu nhiên** cho một wrapper hoặc object bất biến. Nếu khóa tùy chỉnh có nhiều field, `equals` và `hashCode` phải dùng cùng ý niệm danh tính, tránh sửa field tham gia hash khi khóa đã được đưa vào map/set.

## 8. `==` trên wrapper: so tham chiếu hay unbox?

Khi cả hai toán hạng của `==` là **tham chiếu wrapper cùng loại** (ví dụ hai `Integer`), phép `==` có thể so **identity** của object, không phải nội dung. Vì Java boxing/valueOf có cache ở một số miền giá trị, kết quả có thể gây hiểu nhầm:

```java
Integer a = Integer.valueOf(42);
Integer b = Integer.valueOf(42);
boolean sameReference = (a == b); // true với Integer.valueOf trong miền -128..127

Integer c = Integer.valueOf(1000);
Integer d = Integer.valueOf(1000);
// c == d không nên được dùng làm kiểm tra giá trị: khả năng cache ngoài miền chuẩn tùy triển khai.
boolean sameValue = c.equals(d); // true
```

Để **so giá trị wrapper**, dùng `a.equals(b)` khi receiver chắc chắn không null, hoặc `Objects.equals(a,b)` khi có thể null. Để so primitive, unbox có chủ đích và xử lý null:

```java
Integer maybe = Integer.valueOf(42);
boolean equalsPrimitive = maybe != null && maybe.intValue() == 42;
```

### 8.1. Hai wrapper khác kiểu không dùng trực tiếp `==`

Hai tham chiếu thuộc hai lớp wrapper độc lập như `Integer` và `Long` **không thể trực tiếp so sánh với nhau bằng `==`**. Biểu thức này không được Java tự động unbox cả hai phía:

```java
Integer a = Integer.valueOf(9);
Long b = Long.valueOf(9L);
// boolean invalid = (a == b); // LỖI BIÊN DỊCH: incomparable types.
boolean sameNumericValue = a.longValue() == b.longValue(); // true.
boolean sameObjectValue = a.equals(b); // false: khác wrapper class.
```

Khi **một toán hạng là primitive**, ví dụ `a == 9L`, Java có thể unbox `Integer` thành `int` rồi widening lên `long` để so sánh. Nếu cả hai tham chiếu được khai báo là `Number`, phép `==` giữa chúng kiểm tra **cùng object** chứ không so sánh giá trị số. Khi muốn so sánh số của các wrapper khác kiểu, hãy chuyển đổi tường minh có bảo toàn miền và xử lý `null` trước khi gọi phương thức; `longValue()` phù hợp với cặp `Integer`/`Long`.

## 9. `Float`/`Double`: NaN và signed zero làm khác biệt rõ nhất

Số thực IEEE 754 có các giá trị đặc biệt mà số nguyên không có:

- `+0.0` và `-0.0`: hai mẫu bit khác nhau nhưng primitive `+0.0 == -0.0` là true.
- `NaN`: primitive `Double.NaN == Double.NaN` là false.
- `+Infinity`, `-Infinity`: vô cực có thứ tự số học tương ứng.

Với wrapper `Double`:

- `Double.compare(-0.0, +0.0)` trả số âm: **−0.0 đứng trước +0.0**.
- `Double.valueOf(-0.0).equals(Double.valueOf(+0.0))` trả false.
- `Double.compare(Double.NaN, Double.POSITIVE_INFINITY)` trả số dương: NaN đứng sau +Infinity.
- `Double.valueOf(Double.NaN).equals(Double.valueOf(Double.NaN))` trả true, dù primitive `NaN == NaN` false.

Các hành vi tương ứng cũng áp dụng cho `Float` ở độ rộng 32 bit.

### 9.1. Bảng đối chiếu thường dùng

| Biểu thức | Kết quả |
| --- | --- |
| `+0.0 == -0.0` | true |
| `Double.compare(-0.0,+0.0)` | giá trị âm (JDK trả -1) |
| `Double.valueOf(-0.0).equals(Double.valueOf(+0.0))` | false |
| `Double.NaN == Double.NaN` | false |
| `Double.compare(Double.NaN,Double.NaN)` | 0 |
| `Double.valueOf(Double.NaN).equals(Double.valueOf(Double.NaN))` | true |
| `Double.compare(Double.NaN,Double.POSITIVE_INFINITY)` | giá trị dương |
| `Double.hashCode(-0.0) == Double.hashCode(+0.0)` | false |

Vì sao wrapper có các lựa chọn này? `equals` của `Double` dựa trên `doubleToLongBits` để phân biệt ±0, và phương thức bit này **chuẩn hóa các mẫu NaN**. Natural ordering/`compare` được xác định nhất quán với equality của wrapper, phù hợp để làm khóa/sắp xếp theo semantics của `Double`.

**Đừng dùng dấu của số thực như điều kiện “thế nào cũng giống nhau vì zero”.** Signed zero có thể tác động đến phép chia, `Math.atan2`, `Math.min/max`. Nếu muốn gộp zero âm/dương cho một nghiệp vụ cụ thể, phải có chính sách chuẩn hóa riêng.

### 9.2. NaN không bằng chính nó dưới primitive `==`

`Double.NaN == Double.NaN` false là quy tắc của phép so sánh số thực primitive. Nhưng `Double.NaN` được bọc vào wrapper có hợp đồng `equals` khác, vốn cho phép các NaN được xem như một nhóm bằng nhau.

Đừng dùng `value == Double.NaN` để phát hiện NaN; dùng `Double.isNaN(value)` hoặc `Float.isNaN(value)`. Không dùng epsilon một cách tùy tiện để biến NaN thành một giá trị “gần bằng”.

### 9.3. Floating-point hash lấy bit đã canonicalize

`Double.hashCode(value)` dùng các bit tương thích với `Double.equals`, bao gồm canonicalize NaN. Nếu bạn tự viết `equals` trên raw bits bằng `doubleToRawLongBits` nhưng dùng `Double.hashCode` không xét khác biệt NaN payload, sự nhất quán “equal ⇒ same hash” vẫn có thể giữ, nhưng hai khái niệm equality/hash không mô tả hoàn toàn cùng tập thông tin; cần xem xét cẩn thận khi thiết kế khóa bit-level.

Một lựa chọn đơn giản khi muốn mô phỏng đúng wrapper là dùng `Double.doubleToLongBits` cho `equals` và `Double.hashCode` cho hash, thay vì tự cố gắng ghép logic signed zero/NaN từ các phép `==` riêng lẻ.

## 10. Chương trình chạy được A — `compare`, `compareTo`, `equals` và unsigned

Lưu thành **`WrapperComparison.java`**:

```java
public class WrapperComparison {
    public static void main(String[] args) {
        Integer a = Integer.valueOf(9);
        Integer b = Integer.valueOf(12);
        Long sameNumber = Long.valueOf(9L);

        System.out.println(Integer.compare(9, 12));
        System.out.println(a.compareTo(b));
        System.out.println(a.equals(Integer.valueOf(9)));
        System.out.println(a.equals(sameNumber));

        System.out.println(Integer.hashCode(9));
        System.out.println(Byte.compare((byte) -1, (byte) 1));

        System.out.println(Integer.compare(-1, 1));
        System.out.println(Integer.compareUnsigned(-1, 1));
        System.out.println(Long.compareUnsigned(-1L, 1L));
    }
}
```

Chạy với JDK 17+:

```bash
javac --release 17 WrapperComparison.java
java WrapperComparison
```

**Stdout chính xác:**

```text
-1
-1
true
false
9
-2
-1
1
1
```

Lưu ý:

1. `Integer.compare(9,12)` và `Integer(9).compareTo(Integer(12))` đều trả âm (ở trường hợp này là -1).
2. `Integer.equals` trả true với cùng `Integer(9)` nhưng false với `Long(9L)`.
3. `Integer.hashCode(9)` bằng 9.
4. `Byte.compare(-1,1)` trả `-2`; minh họa vì sao chỉ nên xét **dấu** của kết quả comparator, không yêu cầu luôn đúng `-1/0/1`.
5. Theo signed order, `-1` đứng trước `1`; theo unsigned interpretation của 32/64 bit, bit pattern toàn 1 lớn hơn 1. `compareUnsigned` không chuyển `int` thành một primitive unsigned mới.

## 11. Chương trình chạy được B — `Double`, NaN, signed zero và collection

Lưu thành **`FloatingWrapperComparison.java`**:

```java
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class FloatingWrapperComparison {
    public static void main(String[] args) {
        double positiveZero = +0.0;
        double negativeZero = -0.0;

        System.out.println(positiveZero == negativeZero);
        System.out.println(Double.compare(negativeZero, positiveZero));
        System.out.println(Double.valueOf(negativeZero)
                .equals(Double.valueOf(positiveZero)));

        System.out.println(Double.NaN == Double.NaN);
        System.out.println(Double.valueOf(Double.NaN)
                .equals(Double.valueOf(Double.NaN)));
        System.out.println(Double.compare(Double.NaN,
                Double.POSITIVE_INFINITY));

        Set<Double> hashed = new HashSet<>();
        hashed.add(positiveZero);
        hashed.add(negativeZero);
        hashed.add(Double.NaN);
        hashed.add(Double.NaN);
        System.out.println(hashed.size());

        TreeSet<Double> ordered = new TreeSet<>();
        ordered.add(positiveZero);
        ordered.add(negativeZero);
        ordered.add(Double.NaN);
        ordered.add(Double.NaN);
        System.out.println(ordered.size());
        System.out.println(ordered.first());
        System.out.println(ordered.last());
    }
}
```

Chạy:

```bash
javac --release 17 FloatingWrapperComparison.java
java FloatingWrapperComparison
```

**Stdout chính xác:**

```text
true
-1
false
false
true
1
3
3
-0.0
NaN
```

Giải thích:

- `primitive ==` coi hai signed zero bằng nhau; `Double.compare` và `Double.equals` phân biệt chúng.
- `NaN == NaN` false, trong khi `Double.equals` coi các NaN wrapper tương đương.
- `HashSet<Double>` dùng `equals`/`hashCode`, còn `TreeSet<Double>` dùng natural ordering; với những giá trị `Double` này, chúng đều giữ `+0.0`, `-0.0`, và một giá trị NaN, tổng `3` phần tử.
- `TreeSet.first()` là `-0.0`, `TreeSet.last()` là `NaN` theo thứ tự của `Double`; thứ tự duyệt `HashSet` không được dùng để suy ra natural ordering.

## 12. Chương trình chạy được C — boxing, null và `Objects.equals`

Lưu thành **`BoxingEquality.java`**:

```java
import java.util.Objects;

public class BoxingEquality {
    public static void main(String[] args) {
        Integer smallA = Integer.valueOf(42);
        Integer smallB = Integer.valueOf(42);
        Integer bigA = Integer.valueOf(1000);
        Integer bigB = Integer.valueOf(1000);

        System.out.println(smallA == smallB);
        System.out.println(bigA.equals(bigB));

        Integer missing = null;
        System.out.println(Objects.equals(missing, null));
        System.out.println(Objects.equals(missing, smallA));

        Integer n = Integer.valueOf(9);
        Long m = Long.valueOf(9L);
        System.out.println(n.equals(m));
        System.out.println(n.intValue() == m.longValue());
    }
}
```

Chạy:

```bash
javac --release 17 BoxingEquality.java
java BoxingEquality
```

**Stdout chính xác:**

```text
true
true
true
false
false
true
```

Đoạn code này **cố ý không in** `bigA == bigB`: việc cache ngoài vùng tối thiểu của `Integer.valueOf` có thể khác tùy triển khai/cấu hình, nên không đặt đầu ra đó làm hợp đồng portable. `equals` vẫn trả true với cùng nội dung số nguyên.

## 13. Lớp `Number` và so sánh hỗn hợp: tránh chuyển kiểu tùy tiện

Giả sử API nhận `Number x, Number y`. Có nhiều chính sách so sánh có thể đúng, nhưng phải chọn **một** dựa trên hợp đồng:

**Chính sách A — loại phải giống nhau:** chỉ chấp nhận cùng wrapper class và dùng `equals`. Hữu ích khi kiểu số mang ý nghĩa nghiệp vụ, chẳng hạn `int` là số đếm còn `double` là phép đo.

**Chính sách B — giá trị nguyên chính xác:** chỉ nhận các kiểu nguyên trong một miền xác định, chuyển sang `long` hoặc `BigInteger` với kiểm tra để so sánh, tránh làm tròn.

**Chính sách C — giá trị số thực theo độ chính xác được chấp nhận:** so sánh `doubleValue()` với tiêu chí phù hợp, đồng thời xử lý NaN/infinity. Nhưng không thích hợp nếu đầu vào chứa `BigDecimal` cực lớn hoặc số nguyên cần độ chính xác ngoài 53 bit.

**Chính sách D — sắp xếp kiểu số hỗn hợp:** phải định nghĩa thứ tự tổng quát cho loại, giá trị, NaN, ±Infinity, signed zero, những trường hợp không thể chuyển đổi chính xác. Thứ tự dựa trên kiểu trước rồi giá trị sau có thể phù hợp với một số domain, nhưng không đồng nghĩa thứ tự toán học giữa mọi `Number`.

**Cảnh báo:** ép tất cả `Number` sang `double` rồi gọi `Double.compare` có thể nhập nhằng những `long` lớn khác nhau. Ví dụ `9_007_199_254_740_992L` và `9_007_199_254_740_993L` có thể chuyển sang **cùng** giá trị `double` vì độ chính xác binary64 chỉ là 53 bit significand.

Nếu không có yêu cầu cụ thể cho so sánh nhiều lớp `Number`, giữ generic type nhất quán ngay từ API thường an toàn hơn.

## 14. Hợp đồng `Comparable` và `Comparator` khi đưa wrapper vào sort/map

Với `Comparable<T>`/`Comparator<T>`, dấu của kết quả phải mô tả thứ tự thỏa các quy tắc cơ bản: đối xứng dấu, tính bắc cầu và nhất quán khi so sánh bằng 0.

Một comparator hợp lệ cho dữ liệu đo kiểu `double` thường bắt đầu bằng:

```java
Comparator<Double> natural = Double::compare;
```

Một comparator hợp lệ cho đối tượng có field `double` có thể viết:

```java
Comparator<Sample> ordered =
        Comparator.comparingDouble(Sample::measurement)
                .thenComparingLong(Sample::id);
```

Trong đó `Sample` là một lớp/record có phương thức `measurement()` và `id()` phù hợp; đây là **mảnh mã**, không phải chương trình độc lập khi chưa định nghĩa `Sample`.

Tại sao có `thenComparingLong`? Khi hai `Sample` có cùng kết quả đo theo `Double.compare`, ứng dụng vẫn có thể muốn thứ tự phân biệt theo ID. Nhưng **Comparator.compare(a,b)==0 không buộc phải đồng nhất với a.equals(b) trong mọi tình huống**; nếu dùng làm khóa `TreeMap` hoặc `TreeSet`, sự không nhất quán với `equals` có thể làm hành vi khác kỳ vọng của `Map`/`Set`.

### 14.1. Tuyệt đối không biến epsilon thành comparator tùy tiện

Không viết:

```java
// Ý tưởng SAI:
int compare(double a, double b) {
    return Math.abs(a - b) <= 0.1 ? 0 : Double.compare(a, b);
}
```

Đây là mảnh method minh họa **không nên dùng**. Ba số `0.00`, `0.09` và `0.18` thỏa “0.00 gần 0.09”, “0.09 gần 0.18”, nhưng “0.00 không gần 0.18”. Quan hệ trả `0` không bắc cầu và vi phạm hợp đồng `Comparator`/`Comparable`.

Hãy tách **truy vấn số học gần bằng** khỏi **equality và ordering**:

```java
static boolean near(double a, double b, double tolerance) {
    if (!Double.isFinite(a) || !Double.isFinite(b)
            || !Double.isFinite(tolerance) || tolerance < 0) {
        return false; // một chính sách miền dữ liệu cụ thể
    }
    return Math.abs(a - b) <= tolerance;
}
```

Đây là một phương thức **truy vấn riêng** cho các số hữu hạn; không dùng làm `equals`/`compareTo`. Với thang giá trị rất rộng, cần cân nhắc relative tolerance, overflow của phép trừ và yêu cầu nghiệp vụ. Tolerance phải được xác định theo đơn vị đo, không tự lấy một epsilon cho mọi trường.

## 15. `equals` và `compareTo` của `BigDecimal` khác nhau

`BigDecimal` là subclass của `Number` với cách biểu diễn **unscaled value** và **scale**. Các ví dụ:

```java
BigDecimal a = new BigDecimal("2.0");
BigDecimal b = new BigDecimal("2.00");

System.out.println(a.equals(b));      // false
System.out.println(a.compareTo(b));   // 0
```

Cần `import java.math.BigDecimal;`.

- `equals` của `BigDecimal` xét **giá trị cùng scale**: `2.0` có scale 1, `2.00` có scale 2.
- `compareTo` xét **giá trị số**: cả hai bằng 2 nên trả 0.
- `hashCode` của hai `BigDecimal` có thể khác, phù hợp với `equals` của lớp đó.
- `HashSet<BigDecimal>` có thể giữ cả `2.0` và `2.00`; `TreeSet<BigDecimal>` có natural ordering theo `compareTo` và có thể coi chúng là cùng vị trí trong cây.

Đây là ví dụ tiêu biểu về một natural ordering **không nhất quán với equals**; không áp dụng kết luận này cho mọi wrapper. Khi nghiệp vụ muốn “cùng số thập phân nhưng bỏ qua scale”, hãy định nghĩa một chính sách canonicalization minh bạch, cân nhắc cách `stripTrailingZeros` thay đổi scale và trường hợp zero. Không dùng `equals` mặc định rồi giả định nó giống `compareTo`.

### 15.1. Chương trình ngắn chứng minh khác biệt collection

Lưu thành **`DecimalCollectionIdentity.java`**:

```java
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.TreeSet;

public class DecimalCollectionIdentity {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("2.0");
        BigDecimal b = new BigDecimal("2.00");

        System.out.println(a.equals(b));
        System.out.println(a.compareTo(b));

        HashSet<BigDecimal> hashed = new HashSet<>();
        hashed.add(a);
        hashed.add(b);
        System.out.println(hashed.size());

        TreeSet<BigDecimal> ordered = new TreeSet<>();
        ordered.add(a);
        ordered.add(b);
        System.out.println(ordered.size());
    }
}
```

Chạy:

```bash
javac --release 17 DecimalCollectionIdentity.java
java DecimalCollectionIdentity
```

**Stdout:**

```text
false
0
2
1
```

`HashSet` dùng `equals`/`hashCode`, `TreeSet` dùng `compareTo`. Nếu scale quan trọng cho nghiệp vụ, natural ordering của `BigDecimal` có thể **không** phù hợp làm chính sách loại trùng; lựa chọn cần dựa trên dữ liệu thực tế.

## 16. Những trường hợp đặc biệt cần kiểm tra khi wrapper được dùng làm khóa

### 16.1. Null

`Integer.compare(a,b)` nhận primitive nên nếu `a` hoặc `b` là wrapper null và được unbox sẽ ném `NullPointerException`. `Integer.compareTo(null)` cũng không định nghĩa null như một `Integer` hợp lệ.

Nếu collection cho phép null trong dữ liệu và cần sắp xếp, dùng `Comparator.nullsFirst` hoặc `Comparator.nullsLast` quanh comparator phù hợp, ví dụ:

```java
Comparator<Integer> byValueWithNull =
        Comparator.nullsLast(Integer::compare);
```

Đoạn này cần `import java.util.Comparator;`.

### 16.2. Signed zero và NaN

Nếu `+0.0` và `-0.0` phải có cùng danh tính nghiệp vụ, mặc định `Double.equals`/`Double.compare` **không** gom chúng. Có thể chuẩn hóa tại ranh giới:

```java
static double normalizeZero(double value) {
    return value == 0.0 ? 0.0 : value;
}
```

Nhưng dấu zero có thể ảnh hưởng phép chia và `Math.atan2`; chỉ chuẩn hóa khi phù hợp.

Với NaN, `Double.compare` đặt NaN lớn hơn các giá trị số khác và `Double.equals` canonicalize các mẫu NaN. Nếu NaN là giá trị dữ liệu lỗi, lựa chọn tốt hơn có thể là **không cho nó vào khóa**, thay vì mặc nhiên sắp sau infinity.

### 16.3. So sánh unsigned

`Integer.compare(-1,1)` và `Integer.compareUnsigned(-1,1)` cho thứ tự ngược nhau. Một trường dữ liệu chứa 32 bit nhưng được diễn giải unsigned theo giao thức phải dùng `compareUnsigned` khi thứ tự không dấu là yêu cầu; khi dùng `equals`/`hashCode` theo bit-pattern `Integer`, equality có thể vẫn đúng vì bit không đổi.

### 16.4. Immutable keys

Các wrapper chuẩn là bất biến. Nhưng nếu một object bao wrapper được tự viết và chứa field mutable quyết định `equals`/`hashCode`, sửa field đó sau khi đưa vào `HashMap`/`HashSet` có thể khiến việc tra cứu sai bucket. Giữ khóa bất biến hoặc quản lý cập nhật bằng xóa rồi chèn lại theo đúng chính sách.

## 17. Checklist chọn đúng API theo tình huống

- Khi dữ liệu là hai primitive `int`: dùng `==` cho bằng nhau, `Integer.compare` cho thứ tự.
- Khi dữ liệu là hai `Integer` non-null: dùng `equals` cho equality, `compareTo`/`Integer.compare` cho thứ tự.
- Khi wrapper có thể null: dùng `Objects.equals` hoặc comparator null-aware.
- Khi trộn `Integer` và `Long`: không tự coi `equals` là numeric equality xuyên kiểu; chọn miền/cách chuyển phù hợp.
- Khi xử lý `Double`: nhớ khác nhau giữa `==`, `equals` và `Double.compare` với `NaN`/signed zero.
- Khi so sánh dữ liệu nhị phân không dấu: dùng `compareUnsigned` theo nhu cầu.
- Khi sắp xếp: không trả hiệu số nguyên hoặc chênh lệch số thực ép thành `int`.
- Khi làm khóa `HashMap`: tuân thủ `equals`/`hashCode`; không dùng hash riêng lẻ làm kết luận bằng nhau.
- Khi làm khóa `TreeMap`: comparator hợp lệ, transitive và nhất quán; biết `compare==0` quyết định trùng khóa.
- Khi dùng `BigDecimal`: quyết định scale có phải một phần danh tính không.
- Khi đo gần bằng: dùng hàm riêng có tolerance/phạm vi rõ, không nhét vào `equals` hoặc comparator.

## 18. Bài tập kèm đáp án

### Bài 1 — `Integer` và `Long` cùng số

`Integer a = 9; Long b = 9L;`. `a.equals(b)` là gì, và tại sao?

**Đáp án:** false. `Integer.equals` đòi đối tượng bên phải là `Integer`; `Long` là class khác. `a.longValue() == b.longValue()` sẽ là true với dữ liệu này.

### Bài 2 — Static và instance compare

Viết hai cách so thứ tự hai giá trị 9 và 12 bằng `Integer`. Chúng trả kết quả gì?

**Đáp án:** `Integer.compare(9,12)` và `Integer.valueOf(9).compareTo(Integer.valueOf(12))` cùng trả số âm (ở đây là `-1`).

### Bài 3 — Hash collision

Nếu `x.hashCode() == y.hashCode()`, có đủ khẳng định `x.equals(y)` true không?

**Đáp án:** không. Hai đối tượng khác nhau có thể trùng hash; chỉ chiều `equals true ⇒ hash equal` là bắt buộc.

### Bài 4 — Byte.compare

Vì sao `Byte.compare((byte)-1,(byte)1)` có thể trả `-2` mà vẫn đúng?

**Đáp án:** API so sánh chỉ yêu cầu dấu âm/0/dương thể hiện thứ tự; không phải luôn trả đúng `-1,0,1`. `-2 < 0` phản ánh -1 nhỏ hơn 1.

### Bài 5 — Signed vs unsigned

Tính dấu của `Integer.compare(-1,1)` và `Integer.compareUnsigned(-1,1)`.

**Đáp án:** signed so sánh trả âm; unsigned so sánh trả dương. Cùng bit pattern của -1 được đọc là số unsigned 32-bit lớn nhất.

### Bài 6 — Cache/identity

Tại sao `Integer.valueOf(42) == Integer.valueOf(42)` có thể true, nhưng không nên dùng `==` để so giá trị hai `Integer` tùy ý?

**Đáp án:** `Integer.valueOf` cache các giá trị nhỏ trong miền được bảo đảm; `==` giữa hai `Integer` so identity tham chiếu. Với giá trị ngoài miền cache chuẩn, không có bảo đảm identity, trong khi `equals` vẫn so giá trị.

### Bài 7 — Signed zero

`Double.compare(-0.0,+0.0)` và `Double.valueOf(-0.0).equals(Double.valueOf(+0.0))` cho gì?

**Đáp án:** compare trả âm; equals trả false. Primitive `-0.0 == +0.0` lại true.

### Bài 8 — NaN

`Double.NaN == Double.NaN` và `Double.valueOf(Double.NaN).equals(Double.valueOf(Double.NaN))` cho gì?

**Đáp án:** false và true. Wrapper canonicalize NaN cho equality.

### Bài 9 — HashSet/TreeSet với Double

Thêm `+0.0`, `-0.0`, `Double.NaN` hai lần vào hai tập `HashSet<Double>` và `TreeSet<Double>`; mỗi tập có mấy phần tử?

**Đáp án:** 3. Cả hai đều phân biệt hai zero, nhưng hai NaN được coi tương đương theo `Double.equals` và natural ordering.

### Bài 10 — BigDecimal và scale

`new BigDecimal("2.0").compareTo(new BigDecimal("2.00"))` và `equals` cho gì?

**Đáp án:** 0 và false. `compareTo` xét giá trị số; `equals` còn xét scale.

### Bài 11 — Comparator bằng hiệu số

Vì sao `(a,b) -> a-b` không là lựa chọn an toàn cho `Comparator<Integer>`?

**Đáp án:** phép trừ `int` có thể overflow, khiến dấu của kết quả không còn phản ánh thứ tự. Dùng `Integer.compare(a,b)`.

### Bài 12 — Number hỗn hợp

Tại sao dùng `a.doubleValue() == b.doubleValue()` trên mọi `Number` là nguy hiểm?

**Đáp án:** mất chính xác với `long` lớn hơn miền biểu diễn nguyên chính xác của `double`, với `BigDecimal` có precision cao, và gây nhầm giữa NaN/signed zero nếu coi như một equality tổng quát. Phải xác định miền dữ liệu/hợp đồng trước khi chuyển.

### Bài 13 — Null

`Integer a = null;`. `Objects.equals(a,null)` trả gì? `a.compareTo(1)` thì sao?

**Đáp án:** true với `Objects.equals` vì cả hai null; gọi method trên receiver null gây `NullPointerException`.

### Bài 14 — Hash và cây với BigDecimal

Thêm `2.0` và `2.00` dưới dạng `BigDecimal` vào `HashSet` và `TreeSet`. Kết quả kích thước?

**Đáp án:** HashSet có 2, TreeSet có 1 theo default equality/natural ordering của `BigDecimal`.

## 19. Tài liệu Java SE 17 để đối chiếu

- [Java SE 17 Number API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Number.html): class trừu tượng cho các wrapper số.
- [Integer API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html): `compare`, `compareUnsigned`, `compareTo`, `equals`, `hashCode`, `valueOf`.
- [Long API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Long.html): so sánh signed/unsigned và hash.
- [Byte API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Byte.html) và [Short API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Short.html): so sánh và promotion.
- [Float API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Float.html) và [Double API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html): signed zero/NaN và bit-based equality.
- [Object.equals/hashCode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html): hợp đồng equality và hash.
- [Comparable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Comparable.html) và [Comparator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html): natural ordering, comparator contract.
- [HashSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html), [TreeSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html): tiêu chí trùng phần tử.
- [BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): `compareTo` vs `equals` khi scale khác nhau.

**Kết luận:** dùng **`compare`/`compareTo`** để biểu đạt *thứ tự*, **`equals`** để biểu đạt *bằng nhau theo hợp đồng lớp*, và **`hashCode`** để hỗ trợ *tra cứu dựa trên equality*. Với wrapper số, luôn xem xét **kiểu**, **null**, **boxing identity**, **signed/unsigned**, **NaN**, **±0.0** và **scale** thay vì mặc nhiên coi “cùng lượng toán học” là cùng khóa Java.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="number-and-wrappers-009">Wrapper khác kiểu không tự bằng nhau qua equals</a>

<details>
<summary>Xem chi tiết</summary>

# Wrapper khác kiểu không tự bằng nhau qua equals trong Java

Trong Java, các wrapper số như `Byte`, `Short`, `Integer`, `Long`, `Float` và `Double` đều kế thừa `Number`, nhưng **`equals(Object)` của mỗi wrapper đòi đối tượng còn lại thuộc đúng loại wrapper tương ứng**. Vì thế:

~~~java
Integer a = Integer.valueOf(42);
Long b = Long.valueOf(42L);

System.out.println(a.equals(b)); // false
~~~

Hai đối tượng biểu diễn cùng giá trị toán học 42, nhưng **không bằng nhau theo hợp đồng `Integer.equals`**. Đây là hành vi có chủ đích, không phải Java quên tự nâng kiểu. Bài học tập trung vào ranh giới giữa **bằng nhau theo kiểu đối tượng**, **bằng nhau theo số học**, **so sánh primitive**, và hệ quả trong collections.

## 1. Tại sao `Integer.equals(Long)` là false?

`Integer` lưu một giá trị `int`; `Long` lưu một giá trị `long`. Phương thức `Integer.equals(Object obj)` chỉ trả `true` khi `obj` là `Integer` có cùng giá trị `int`. Tương tự, `Long.equals` chỉ chấp nhận một `Long` có cùng giá trị `long`.

~~~java
Integer i = Integer.valueOf(42);
Long l = Long.valueOf(42L);
Double d = Double.valueOf(42.0);

boolean sameInteger = i.equals(Integer.valueOf(42)); // true
boolean integerVsLong = i.equals(l);                  // false
boolean longVsDouble = l.equals(d);                   // false
~~~

| Lời gọi | Kết quả | Vì sao? |
| --- | --- | --- |
| `Integer.valueOf(42).equals(Integer.valueOf(42))` | `true` | Cùng kiểu `Integer` và cùng `int` |
| `Integer.valueOf(42).equals(Long.valueOf(42L))` | `false` | `Long` không phải `Integer` |
| `Long.valueOf(42L).equals(Double.valueOf(42.0))` | `false` | `Double` không phải `Long` |
| `Double.valueOf(42.0).equals(Double.valueOf(42.0))` | `true` | Cùng kiểu `Double` và cùng giá trị |
| `Float.valueOf(42.0f).equals(Double.valueOf(42.0))` | `false` | Wrapper khác kiểu |

**Hợp đồng của `equals(Object)` khác phép so sánh các primitive có tự động widening.** Tham số của `equals` là `Object`, và Java không tự unbox/nâng kiểu hai đối tượng để so sánh giá trị số. Trong triển khai của các wrapper, logic kiểm tra loại trước khi xét giá trị.

Không dùng lập luận “cả hai đều là `Number`” để dự đoán `equals`: lớp cha `Number` **không định nghĩa một cơ chế equality số học chung** cho tất cả subclass. Đối tượng thực tế quyết định phương thức `equals` nào được chạy theo dynamic dispatch.

## 2. `==` giữa primitive và `equals` giữa wrapper khác nhau

### Hai primitive được nâng kiểu số học

~~~java
int x = 42;
long y = 42L;

System.out.println(x == y); // true
~~~

Với `int` và `long`, phép so sánh `==` áp dụng binary numeric promotion: `int` được widening thành `long`, sau đó so sánh hai primitive. Do vậy kết quả là `true`.

### Wrapper được so với primitive có thể bị unbox

~~~java
Integer wrapper = Integer.valueOf(42);
long primitive = 42L;

System.out.println(wrapper == primitive); // true
~~~

Vì một phía là primitive số, Java unbox `wrapper` thành `int`, rồi widening `int → long` để so sánh. Nếu `wrapper` là `null`, phép unboxing có thể ném `NullPointerException`.

### Hai wrapper khác kiểu không tự unbox để dùng `==`

Đoạn sau **cố ý không biên dịch**:

~~~java
Integer i = Integer.valueOf(42);
Long l = Long.valueOf(42L);

// boolean eq = i == l; // ERROR: incomparable types: Integer and Long
~~~

`i` và `l` đều có kiểu tham chiếu; trong trường hợp này, phép `==` không kích hoạt cùng chuỗi unboxing + widening như khi có một toán hạng primitive. Hai lớp wrapper độc lập `Integer` và `Long` không phải các kiểu tham chiếu có thể so sánh bằng `==` với nhau.

Nếu cả hai đều được khai báo là `Number` hoặc `Object` thì `==` giữa hai biến tham chiếu có thể biên dịch, nhưng kết quả lúc đó là **đồng nhất tham chiếu**, không phải so sánh giá trị số.

~~~java
Number a = Integer.valueOf(42);
Number b = Long.valueOf(42L);
boolean sameReference = a == b; // false
boolean sameByEquals = a.equals(b); // false
~~~

Ngay cả với **hai `Integer`**, `==` giữa hai tham chiếu chỉ kiểm tra xem đó có phải cùng một object hay không. Đừng dựa vào bộ đệm wrapper (`Integer.valueOf` có cache ở một miền giá trị) để suy luận rằng `==` là so sánh số. Hãy dùng `equals` nếu muốn equality theo kiểu wrapper hoặc unbox nếu thực sự muốn so sánh primitive và đã xử lý `null`.

## 3. `Number` và polymorphism không tự cung cấp equality xuyên kiểu

Một biến khai báo `Number` vẫn tham chiếu đến một object cụ thể như `Integer` hay `Long`:

~~~java
Number left = Integer.valueOf(42);
Number right = Long.valueOf(42L);

System.out.println(left.equals(right)); // false
~~~

Lời gọi `left.equals(right)` chạy implementation của `Integer.equals` vì object phía trái là `Integer`. Nó kiểm tra object phía phải có phải `Integer` không; kết quả là `false`. Java không nhìn thấy hai object cùng kế thừa `Number` rồi mặc định gọi `longValue()` để so sánh.

Điểm quan trọng: **kiểu tĩnh `Number` giúp truy cập các phép chuyển giá trị** như `intValue()`, `longValue()`, `doubleValue()`, nhưng không thiết lập khái niệm “cùng một số” thống nhất cho tất cả wrapper hoặc mọi subclass của `Number`.

Ngoài các wrapper, các lớp như `BigInteger` và `BigDecimal` cũng kế thừa `Number` và có hợp đồng `equals` riêng. Khi nhận tham số tổng quát `Number`, một phương thức phải chủ động quyết định mình muốn so sánh điều gì: cùng object, cùng kiểu và giá trị, hay cùng giá trị số học sau chuyển đổi có kiểm soát.

## 4. `Objects.equals` an toàn null nhưng không chuyển kiểu số

`Objects.equals(a, b)` là công cụ thuận tiện để so sánh object có thể null. Nó trả `true` nếu cùng tham chiếu (bao gồm cùng null); nếu chỉ `a` khác null thì gọi `a.equals(b)`. **Nó không normalize wrapper**, không chuyển `Integer` sang `Long`.

~~~java
Integer i = Integer.valueOf(42);
Long l = Long.valueOf(42L);

System.out.println(java.util.Objects.equals(i, l)); // false
System.out.println(java.util.Objects.equals(null, null)); // true
~~~

Dùng `Objects.equals` khi mục tiêu là **object equality** và cần tránh tự gọi `null.equals(...)`. Nếu mục tiêu là **numeric equivalence giữa nhiều kiểu khác nhau**, cần chính sách chuyển đổi và so sánh riêng. Đổi từ `i.equals(l)` sang `Objects.equals(i, l)` không giải quyết khác biệt kiểu.

## 5. Hệ quả trong `HashSet`, `HashMap` và khóa kiểu `Number`

Các collection băm dùng `equals` và `hashCode`. Vì `Integer(42)`, `Long(42)` và `Double(42.0)` không bằng nhau theo `equals`, chúng là **ba phần tử riêng** trong một tập `Set<Number>`:

~~~java
java.util.Set<Number> values = new java.util.HashSet<>();
values.add(Integer.valueOf(42));
values.add(Long.valueOf(42L));
values.add(Double.valueOf(42.0));

System.out.println(values.size()); // 3
~~~

Một `Map<Number, String>` cũng có thể giữ riêng các khóa `Integer.valueOf(42)` và `Long.valueOf(42L)`. Điều này tác động đến cache, thống kê, deduplication, ánh xạ ID và việc lấy giá trị: `map.get(42L)` không tương đương `map.get(42)` khi khóa được lưu bằng wrapper khác loại.

**Điều kiện thiết kế:** Nếu “ID 42” chỉ có một kiểu số hợp lệ, hãy chuẩn hóa **kiểu khóa lúc nhập**, ví dụ dùng `Long` cho toàn bộ khóa ID có miền `long` hoặc `BigInteger` nếu cần số nguyên rất lớn. Tránh tạo `Map<Number, ...>` rồi hy vọng các wrapper khác kiểu gộp về cùng một khóa.

Không tự viết `equals` cho object nghiệp vụ kiểu “đôi khi so sánh `Integer` với `Long` theo `doubleValue`” rồi giữ `hashCode` cũ: hai đối tượng được coi là bằng nhau bắt buộc có hash code tương ứng. Quy tắc nhất quán của `equals`/`hashCode` cần được duy trì.

## 6. Muốn so sánh *giá trị số* giữa hai wrapper thì làm thế nào?

Trước hết phải định nghĩa rõ **miền dữ liệu**. Không có một phép ép chung nào đúng cho mọi giá trị của `Number`.

### Trường hợp A — biết chắc cả hai là số nguyên primitive và vừa miền `long`

~~~java
Integer i = Integer.valueOf(42);
Long l = Long.valueOf(42L);

boolean sameNumericValue = i.longValue() == l.longValue(); // true
~~~

Cách này hợp lệ vì `Integer` và `Long` đều biểu diễn số nguyên chính xác trong phạm vi `long`. Nó cũng dùng được cho `Byte`/`Short`, khi đã kiểm soát các kiểu đầu vào. Nhưng **không áp dụng bừa bãi cho `BigInteger` vượt miền long hoặc số `Double` chứa phần thập phân**, vì các lời gọi `longValue()` tương ứng có thể làm mất thông tin.

Nếu cần chuẩn hóa nhiều wrapper số nguyên, có thể chuyển sang `BigInteger`:

~~~java
java.math.BigInteger fromInt =
        java.math.BigInteger.valueOf(Integer.valueOf(42).longValue());
java.math.BigInteger fromLong =
        java.math.BigInteger.valueOf(Long.valueOf(42L).longValue());

boolean equalIntegers = fromInt.equals(fromLong); // true
~~~

Với dữ liệu đã là `BigInteger`, giữ chính đối tượng đó; đừng chuyển nó về `long` rồi lại xây lại `BigInteger`.

### Trường hợp B — yêu cầu thập phân có độ chính xác và chính sách rõ

Nếu hệ thống nhận giá trị thập phân như `"42.0"` và `"42.00"`, `BigDecimal` cung cấp cả object equality và numeric comparison:

~~~java
java.math.BigDecimal x = new java.math.BigDecimal("42.0");
java.math.BigDecimal y = new java.math.BigDecimal("42.00");

boolean objectEqual = x.equals(y);   // false: khác scale
boolean numericEqual = x.compareTo(y) == 0; // true
~~~

`BigDecimal.equals` đòi **cùng giá trị và cùng scale**, còn `compareTo` so sánh giá trị số thập phân và bỏ qua scale khác nhau. Nếu cần key thập phân mà coi `42.0` và `42.00` giống nhau, hãy quy định chiến lược chuẩn hóa scale hoặc kiểu khóa từ đầu, và hiểu tác động lên `HashMap`/`TreeMap`. `BigDecimal` là một subclass của `Number` nhưng không phải wrapper primitive.

### Trường hợp C — số thực và giá trị gần bằng

`doubleValue()` giúp đưa nhiều `Number` về kiểu `double`, nhưng **không bảo toàn mọi giá trị số**. Ví dụ hai số nguyên `long` liền nhau ở vùng trên `2^53` có thể chuyển thành cùng một `double`:

~~~java
long a = 9_007_199_254_740_993L; // 2^53 + 1
long b = 9_007_199_254_740_992L; // 2^53

boolean equalAsLong = a == b; // false
boolean equalAsDouble = (double) a == (double) b; // true
~~~

Đổi cả hai sang `double` rồi `==` có thể báo bằng nhau sai. Tương tự, số cực lớn có thể thành vô cực, số rất nhỏ có thể bị làm tròn; NaN và ±0 có các quy tắc đặc biệt. Nếu bài toán hỗn hợp integer/floating cần so sánh **chính xác**, phải định nghĩa cách xử lý miền, dấu, NaN, vô cực, overflow và phương pháp chuyển đổi trước khi xây comparator.

Nếu nghiệp vụ chỉ cần “gần bằng” với dung sai, hãy viết một phép kiểm tra riêng cho dữ liệu hữu hạn, với tolerance hợp lý theo thang đo. **Không dùng phép so sánh gần bằng theo epsilon làm `equals`/comparator cho collection** nếu nó không bảo đảm bắc cầu và nhất quán.

## 7. Wrapper số thực còn có quy tắc riêng về NaN và số 0 có dấu

`Float.equals` và `Double.equals` cũng yêu cầu **cùng wrapper type**. Ngoài ra, chúng sử dụng quy tắc so sánh bit chuẩn hóa NaN:

- Hai object `Double` chứa NaN được `Double.equals` xem bằng nhau, dù toán tử primitive `Double.NaN == Double.NaN` là `false`.
- `Double.valueOf(+0.0).equals(Double.valueOf(-0.0))` là `false`, dù primitive `+0.0 == -0.0` là `true`.
- `Float.valueOf(Float.NaN).equals(Double.valueOf(Double.NaN))` là `false` vì **khác wrapper type**, dù cả hai đều biểu thị NaN.

~~~java
Double d1 = Double.valueOf(Double.NaN);
Double d2 = Double.valueOf(Double.NaN);
Float f = Float.valueOf(Float.NaN);

boolean sameDoubleNaNs = d1.equals(d2); // true
boolean floatVsDoubleNaN = f.equals(d1); // false
boolean signedZeroDifferent =
        Double.valueOf(0.0).equals(Double.valueOf(-0.0)); // false
~~~

Trong các thuật toán cần sắp xếp `Float`/`Double`, `Float.compare` và `Double.compare` định nghĩa thứ tự phù hợp với wrapper: NaN được coi bằng NaN trong `compare`, và `-0.0` đứng trước `+0.0`. Điều đó không tạo ra equality chung giữa `Float` với `Double`.

## 8. Ví dụ Java 17+ hoàn chỉnh

Lưu tệp tên `WrapperEquality.java`:

~~~java
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class WrapperEquality {
    public static void main(String[] args) {
        Integer integer = Integer.valueOf(42);
        Long longValue = Long.valueOf(42L);
        Double doubleValue = Double.valueOf(42.0);
        Number left = integer;
        Number right = longValue;

        System.out.println("Integer.equals(Long) = "
                + integer.equals(longValue));
        System.out.println("Long.equals(Double) = "
                + longValue.equals(doubleValue));
        System.out.println("Integer.equals(Integer) = "
                + integer.equals(Integer.valueOf(42)));
        System.out.println("Objects.equals(Integer, Long) = "
                + Objects.equals(integer, longValue));
        System.out.println("Number.equals(Long) = "
                + left.equals(longValue));
        System.out.println("int == long = " + (42 == 42L));
        System.out.println("Integer == long = " + (integer == 42L));
        System.out.println("Number reference == = " + (left == right));

        Set<Number> numbers = new HashSet<>();
        numbers.add(integer);
        numbers.add(longValue);
        numbers.add(doubleValue);
        System.out.println("HashSet size = " + numbers.size());
        System.out.println("contains Long(42) = "
                + numbers.contains(Long.valueOf(42L)));

        long large = 9_007_199_254_740_993L;
        System.out.println("large as double = " + (double) large);
        System.out.println("large double == previous double = "
                + ((double) large == (double) (large - 1)));
        System.out.println("BigInteger exact compare = "
                + BigInteger.valueOf(large)
                .compareTo(BigInteger.valueOf(large - 1)));

        BigDecimal decimalA = new BigDecimal("42.0");
        BigDecimal decimalB = new BigDecimal("42.00");
        System.out.println("BigDecimal equals = "
                + decimalA.equals(decimalB));
        System.out.println("BigDecimal compareTo = "
                + decimalA.compareTo(decimalB));
        System.out.println("Double NaN equals NaN = "
                + Double.valueOf(Double.NaN)
                .equals(Double.valueOf(Double.NaN)));
        System.out.println("Float NaN equals Double NaN = "
                + Float.valueOf(Float.NaN)
                .equals(Double.valueOf(Double.NaN)));
        System.out.println("Double +0 equals -0 = "
                + Double.valueOf(+0.0).equals(Double.valueOf(-0.0)));
        System.out.println("Objects.equals(null, null) = "
                + Objects.equals(null, null));
    }
}
~~~

Biên dịch/chạy bằng Java 17 trở lên:

~~~bash
javac WrapperEquality.java
java WrapperEquality
~~~

Stdout:

~~~text
Integer.equals(Long) = false
Long.equals(Double) = false
Integer.equals(Integer) = true
Objects.equals(Integer, Long) = false
Number.equals(Long) = false
int == long = true
Integer == long = true
Number reference == = false
HashSet size = 3
contains Long(42) = true
large as double = 9.007199254740992E15
large double == previous double = true
BigInteger exact compare = 1
BigDecimal equals = false
BigDecimal compareTo = 0
Double NaN equals NaN = true
Float NaN equals Double NaN = false
Double +0 equals -0 = false
Objects.equals(null, null) = true
~~~

Mười chín dòng kết quả minh họa sự khác nhau giữa wrapper `equals` và primitive equality, hệ quả trong tập hợp, sai số của `doubleValue` và quy tắc riêng cho NaN/zero có dấu.

## 9. Những lỗi thường gặp khi xử lý wrapper khác kiểu

1. **Cho rằng hai wrapper cùng giá trị toán học luôn `equals`.** `Integer.valueOf(42).equals(Long.valueOf(42L))` là `false` vì type khác nhau.
2. **Dùng `Objects.equals` như phép chuẩn hóa số.** Nó chỉ bao bọc object equality và xử lý `null` an toàn.
3. **Thấy `Number` rồi dùng `Number.equals` như một hợp đồng số học chung.** `Number` không triển khai một equality xuyên các subclass.
4. **Dùng `==` giữa hai wrapper để đo giá trị.** Nếu hai tham chiếu cùng loại, đó thường là kiểm tra cùng object; nếu wrapper khác kiểu như `Integer` và `Long`, biểu thức có thể không biên dịch. Với một toán hạng primitive, unboxing có thể diễn ra và gây `NullPointerException` khi wrapper null.
5. **Gộp mọi `Number` về `doubleValue` để so sánh.** Với số nguyên lớn hơn `2^53`, hai số khác nhau có thể thành cùng một `double`, khiến kết luận sai.
6. **Lấy dữ liệu mixed numeric làm khóa `Map<Number, ...>` rồi mong truy vấn bằng wrapper khác kiểu trúng cùng entry.** Cần chuẩn hóa kiểu khóa theo đặc tả dữ liệu.
7. **Nhầm `BigDecimal.equals` với `BigDecimal.compareTo`.** `42.0` và `42.00` có giá trị số thập phân bằng nhau nhưng scale khác; `equals` trả `false` và `compareTo` trả 0.
8. **Cho rằng NaN của `Float` và NaN của `Double` bằng nhau theo `equals`.** Chúng là wrapper khác class nên `false`; ngay trong mỗi loại wrapper, quy tắc NaN/±0 cũng khác primitive.
9. **Tự sửa `equals` mà không cập nhật `hashCode`.** Điều này làm hỏng tính nhất quán khóa hash và có thể gây lỗi khó tái hiện.

## 10. Quy trình chọn phép so sánh

| Mục đích | Cách phù hợp | Chú ý |
| --- | --- | --- |
| Cùng kiểu wrapper và cùng giá trị theo wrapper | `Objects.equals(a, b)` hoặc `a.equals(b)` khi a không null | Khác kiểu wrapper vẫn `false` |
| Hai primitive cần numeric equality | `==` sau numeric promotion | NaN và signed zero có quy tắc đặc biệt |
| Hai wrapper nguyên nhỏ biết chắc miền `long` | So sánh `longValue()` sau khi xác minh kiểu và miền | Không dùng cho `BigInteger` quá lớn hoặc số thập phân |
| Số nguyên lớn chính xác | `BigInteger.equals`/`compareTo` | Giữ dữ liệu gốc chính xác |
| Số thập phân chính xác | `BigDecimal.compareTo == 0` nếu không quan tâm scale | Xác định hợp đồng scale và key |
| Sắp xếp số thực cùng loại | `Double.compare` / `Float.compare` | NaN và ±0 được xếp theo quy ước Java |
| Map/set cần đồng nhất ID | Chuẩn hóa kiểu khóa ở biên dữ liệu | Bảo đảm `equals` và `hashCode` cùng chính sách |

**Câu hỏi tự luyện:**

- `Integer.valueOf(8).equals(Long.valueOf(8L))` trả gì? Thế `8 == 8L`?
- Nếu `Number n = Integer.valueOf(8)`, `n.equals(Long.valueOf(8L))` có khác không?
- Vì sao một `HashSet<Number>` có thể chứa cả `Integer(8)` và `Long(8)`?
- Điều gì sai khi so sánh hai `long` khoảng `2^53` bằng cách ép cả hai sang `double`?
- `new BigDecimal("8.0").equals(new BigDecimal("8.00"))` và `compareTo` có cùng kết quả không?

**Đáp án:** (1) `false` và `true`. (2) Vẫn `false` vì implementation `Integer.equals` được chạy. (3) Chúng là hai phần tử không bằng nhau theo wrapper `equals`. (4) Phép ép có thể làm mất bit độ chính xác và gộp hai số khác nhau. (5) `equals` là `false`, còn `compareTo` bằng 0.

## Tài liệu tham khảo

- [Java SE 21 — Integer.equals](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Integer.html#equals(java.lang.Object)).
- [Java SE 21 — Long.equals](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Long.html#equals(java.lang.Object)).
- [Java SE 21 — Number](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Number.html).
- [Java SE 21 — Objects.equals](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Objects.html#equals(java.lang.Object,java.lang.Object)).
- [Java SE 21 — BigDecimal](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html).
- [Java Language Specification `15.21 — Equality Operators](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.21).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="number-and-wrappers-010">Number không cung cấp toán tử cộng chung cho mọi lớp con</a>

<details>
<summary>Xem chi tiết</summary>

# Number không cung cấp toán tử cộng chung cho mọi lớp con trong Java

Trong Java, **`java.lang.Number` là lớp cha trừu tượng của nhiều kiểu số dạng đối tượng**, nhưng **không có toán tử cộng `+` áp dụng chung cho hai biến kiểu `Number`**. Đây là khác biệt quan trọng giữa **mối quan hệ kế thừa đối tượng** và **quy tắc toán tử số học của ngôn ngữ**.

Ví dụ, đoạn mã sau **không biên dịch**:

```java
// Ví dụ cố ý SAI để thấy giới hạn của kiểu Number:
Number a = Integer.valueOf(10);
Number b = Double.valueOf(2.5);
// Number total = a + b; // COMPILE ERROR: toán tử + không áp dụng cho Number.
```

Dù ở thời điểm chạy đối tượng trong `a` là `Integer` và đối tượng trong `b` là `Double`, trình biên dịch nhìn thấy **kiểu tĩnh** của hai biểu thức là `Number`. Java không có cơ chế tự gọi “phép cộng ảo” của lớp con chỉ vì chúng cùng kế thừa `Number`.

Ngược lại, mã này hợp lệ:

```java
Integer a = 10;
Double b = 2.5;
double result = a + b; // 12.5, unboxing rồi nâng kiểu số nguyên thủy.
```

Vì `Integer` và `Double` là **các kiểu wrapper cụ thể hỗ trợ unboxing**, trình biên dịch có thể xác định phép cộng của các giá trị nguyên thủy. Đổi tên kiểu khai báo sang `Number` làm thay đổi quy tắc biên dịch, dù đối tượng tại runtime có thể không đổi.

## 1. Lớp `Number` cung cấp những gì?

`Number` nằm trong `java.lang`, không cần import. Nó là một **abstract class** dùng làm lớp cha cho các đối tượng biểu diễn một giá trị số. Những lớp quen thuộc kế thừa `Number` gồm `Byte`, `Short`, `Integer`, `Long`, `Float`, `Double`, `BigInteger`, `BigDecimal` và cả `AtomicInteger`/`AtomicLong`. Danh sách này không đóng: chương trình hoặc thư viện khác cũng có thể khai báo lớp con.

Các phương thức chuyển giá trị quan trọng của `Number` là:

| Phương thức | Kiểu trả về | Ý nghĩa |
| --- | --- | --- |
| `byteValue()` | `byte` | Chuyển giá trị sang kiểu nguyên 8 bit |
| `shortValue()` | `short` | Chuyển giá trị sang kiểu nguyên 16 bit |
| `intValue()` | `int` | Chuyển giá trị sang kiểu nguyên 32 bit |
| `longValue()` | `long` | Chuyển giá trị sang kiểu nguyên 64 bit |
| `floatValue()` | `float` | Chuyển sang dấu phẩy động 32 bit |
| `doubleValue()` | `double` | Chuyển sang dấu phẩy động 64 bit |

Chúng cung cấp **cách đọc giá trị dưới một kiểu nguyên thủy được chọn**, chứ **không định nghĩa một phép cộng số học đa hình**. Trong lớp `Number`, bốn phương thức `intValue`, `longValue`, `floatValue`, `doubleValue` là abstract; `byteValue` và `shortValue` có cài đặt dựa trên chuyển kiểu từ `intValue`.

Cần phân biệt `Number` với `java.text.NumberFormat`, `java.text.DecimalFormat` hoặc một interface nghiệp vụ tên `Numeric`: chúng là các khái niệm khác nhau.

## 2. Vì sao toán tử `+` không tự động áp dụng cho `Number`?

Java định nghĩa `+` dựa trên **kiểu toán hạng được ngôn ngữ hỗ trợ**:

- Nếu hai toán hạng phù hợp cho **phép cộng số nguyên thủy**, Java thực hiện unboxing cần thiết rồi **binary numeric promotion**, kết quả là `int`, `long`, `float` hoặc `double`.
- Nếu một toán hạng là `String` trong phép `+`, Java có thể thực hiện **nối chuỗi** thay vì cộng số.
- `BigInteger`, `BigDecimal` và các lớp số tùy biến **không thể tự nạp chồng toán tử `+`** bằng cách khai báo một phương thức tên `add`. Java không hỗ trợ operator overloading tùy ý cho đối tượng.

Điểm dễ nhầm là **chỉ có những wrapper cụ thể** như `Integer` hay `Double` được unbox tự động theo quy tắc của Java. Kiểu khai báo `Number` không phải một kiểu được unbox trực tiếp thành “một số nguyên thủy bất kỳ”.

```java
Integer x = 3;
Integer y = 4;
int ok = x + y;           // Hợp lệ: hai Integer thành hai int.

// Number nx = x;
// Number ny = y;
// int notOk = nx + ny;  // LỖI biên dịch: Number + Number.
```

Không có toán tử `add(Number,Number)` chung được kế thừa từ `Object` hay `Number`, và Java không tra cứu phương thức `add` của đối tượng lúc chạy mỗi khi thấy dấu `+`.

**Kiểu động** dùng cho dispatch phương thức và `instanceof`; **kiểu tĩnh** là cơ sở chính để kiểm tra toán tử ở thời điểm biên dịch.

## 3. Điều gì xảy ra khi khai báo biến bằng `Number`?

```java
Number a = Integer.valueOf(10);
Number b = Double.valueOf(2.5);
```

Hai biến hợp lệ do cả `Integer` lẫn `Double` kế thừa `Number`. Thông tin kiểu thật của đối tượng không biến mất, nhưng biến tham chiếu chỉ bảo đảm **các thao tác công khai của `Number`**. Vì vậy:

```java
double approx = a.doubleValue() + b.doubleValue(); // 12.5
int discarded = a.intValue() + b.intValue();       // 12: phần 0.5 bị mất.
```

Hai phép cộng ở đây chạy được bởi chúng ta đã **chọn rõ cách chuyển đổi**. Kết quả `approx` là **tổng xấp xỉ theo `double`**; `discarded` là tổng sau khi từng đối tượng được chuyển sang `int`. Không thể coi chúng là hai cách viết tương đương của “cộng Number”.

Việc lấy `intValue()` đối với `2.5` sẽ bỏ phần thập phân, cho `2`. Với giá trị lớn hoặc các lớp con đặc biệt, chuyển đổi còn có thể mất bit, overflow khi chuyển hoặc đổi từ giá trị chính xác sang giá trị gần đúng. Vì vậy **việc ép về một kiểu chung là quyết định nghiệp vụ**, không phải hành vi mặc định an toàn.

## 4. `Integer + Integer`: unboxing chứ không phải phép cộng đối tượng

Java cho phép:

```java
Integer left = 10;
Integer right = 20;
Integer sum = left + right; // int 30, sau đó boxing về Integer.
```

Điều diễn ra là:

1. Hai biến `Integer` được **unbox** thành hai `int`.
2. Phép cộng nguyên thủy `int + int` tạo ra `int`.
3. Kết quả `int` được **box** trở lại `Integer` khi gán.

Đây không phải việc `Integer` có một overload `operator+`; Java quy định sẵn phép tự chuyển wrapper tương ứng. Nó cũng không biến các đối tượng `Integer` thành đối tượng có thể sửa giá trị: `Integer` bất biến.

```java
Integer n = null;
// int invalid = n + 1; // Biên dịch được về kiểu, nhưng chạy sẽ ném NullPointerException.
```

Unboxing cần có một đối tượng khác `null`. Trong Java, `Number` cũng có thể nhận `null`; nếu gọi `doubleValue()` trên `null`, bạn cũng gặp `NullPointerException`. Cần định nghĩa rõ cách xử lý dữ liệu thiếu trước khi cộng.

## 5. Vì sao không thể có phép cộng chính xác duy nhất cho mọi `Number`?

Các lớp con của `Number` có **mô hình số học và miền giá trị không đồng nhất**:

| Lớp | Kiểu giá trị | Phép cộng được khuyến nghị |
| --- | --- | --- |
| `Integer` | Số nguyên có dấu 32 bit | `int + int`, hoặc `Math.addExact` khi cần phát hiện tràn |
| `Long` | Số nguyên có dấu 64 bit | `long + long`, hoặc `Math.addExact` |
| `Float`/`Double` | Dấu phẩy động nhị phân hữu hạn độ chính xác | Cộng `float`/`double`, có thể có sai số làm tròn |
| `BigInteger` | Số nguyên độ chính xác tùy ý | `bigInteger.add(otherBigInteger)` |
| `BigDecimal` | Số thập phân có thang đo và độ chính xác thích hợp | `bigDecimal.add(otherBigDecimal)` |
| `AtomicInteger`/`AtomicLong` | Wrapper số có trạng thái thay đổi nguyên tử | API `addAndGet`/`getAndAdd` nếu cần cập nhật trạng thái |

Một phép cộng “chung” phải trả lời những câu hỏi không hề có một đáp án duy nhất:

- Trả về `int`, `long`, `double`, `BigInteger` hay `BigDecimal`?
- Nếu một đầu vào là `BigDecimal("0.1")` và đầu vào kia là `Double.valueOf(0.2)`, muốn cộng **decimal theo ký tự hiển thị**, hay cộng giá trị nhị phân thực tế của `double`?
- Nếu `BigInteger` rất lớn không thể chuyển chính xác sang `double`, chấp nhận mất chính xác hay báo lỗi?
- Nếu có overflow số nguyên hoặc NaN/Infinity, phải quay vòng, ném ngoại lệ hay chuyển sang kiểu khác?
- Nếu là `AtomicInteger`, muốn **đọc giá trị hiện tại** hay **cập nhật một biến có thể bị các luồng khác thay đổi**?

Do các lớp mang những ngữ nghĩa khác nhau, `Number` cố ý chỉ đưa ra hợp đồng chuyển kiểu cơ bản. **Không có một kết quả cộng chính xác với semantics duy nhất** cho mọi tổ hợp lớp con, nhất là khi có lớp do thư viện bên ngoài tự định nghĩa.

## 6. Tính gần đúng bằng `doubleValue()`: khi nào dùng được?

Đối với thống kê, đồ họa hoặc các phép đo chấp nhận sai số dấu phẩy động, có thể định nghĩa rõ một hàm cộng gần đúng:

```java
static double approximateSum(Number a, Number b) {
    return a.doubleValue() + b.doubleValue();
}
```

Đây là **phương thức trong một lớp Java**. Nó có tác dụng vì cả hai đối tượng đều được chuyển về `double` trước khi cộng. Nhưng tên `approximateSum` nên nêu thẳng đây là phép tính gần đúng.

Ví dụ giới hạn độ chính xác:

```java
java.math.BigInteger big =
        new java.math.BigInteger("9007199254740993");

double converted = big.doubleValue();
// converted biểu diễn 9007199254740992, mất một đơn vị.
```

`double` chỉ có 53 bit significand hiệu dụng. Giá trị `9_007_199_254_740_993 = 2^53 + 1` không thể lưu chính xác bằng `double` và sẽ được làm tròn. Nếu đối tượng `BigInteger` còn lớn hơn dải của `double`, `doubleValue()` có thể trả Infinity. Kết quả cộng `double` cũng có thể bị rounding, overflow hoặc underflow.

**Khi dùng cách này:** người lập trình đã chấp nhận sai số, dữ liệu đầu vào nằm trong dải có ý nghĩa và đặc tả đầu ra yêu cầu `double`. Không dùng `doubleValue()` như giải pháp cộng tổng quát cho tiền tệ hoặc số nguyên lớn đòi hỏi chính xác từng đơn vị.

## 7. Vì sao `intValue()` và `longValue()` không phải giải pháp tổng quát?

Hai phương thức này dễ dùng nhưng có thể **thu hẹp kiểu, bỏ phần lẻ hoặc cắt bớt bit**.

```java
Number a = Integer.valueOf(10);
Number b = Double.valueOf(2.5);
int sum = a.intValue() + b.intValue(); // 12, KHÔNG phải 12.5
```

Một ví dụ khác:

```java
java.math.BigInteger beyondInt =
        new java.math.BigInteger("4294967296");
int lost = beyondInt.intValue(); // 0: chỉ còn 32 bit thấp.
```

Đối với `BigInteger`, `intValue()` có thể chỉ giữ 32 bit thấp, khiến `4_294_967_296` thành `0`. Cách này không báo lỗi. Nếu thật sự cần chuyển `BigInteger` sang `int` một cách chính xác, dùng `intValueExact()`, phương thức sẽ ném `ArithmeticException` khi giá trị không nằm trong phạm vi.

Tương tự, `BigDecimal.intValueExact()` có thể phát hiện cả giá trị có phần thập phân không thể chuyển chính xác thành số nguyên. Các phương thức `Exact` là API của **lớp cụ thể**; `Number` không hứa rằng mọi lớp con đều có `intValueExact()`.

Một nguyên tắc hữu ích: **chọn miền giá trị và chính sách lỗi trước**, rồi mới gọi phương thức chuyển đổi. Đừng chuyển toàn bộ `Number` về `int` chỉ vì cần một phép cộng cú pháp đơn giản.

## 8. `BigInteger.add`: chính xác đối với số nguyên lớn

Nếu dữ liệu mang ngữ nghĩa số nguyên chính xác, `BigInteger` cho phép cộng không bị tràn ở ranh giới `int`/`long` thông thường:

```java
java.math.BigInteger a =
        new java.math.BigInteger("9007199254740993");
java.math.BigInteger b = java.math.BigInteger.ONE;
java.math.BigInteger exact = a.add(b);
// exact == 9007199254740994 (về giá trị số).
```

Đây là **mảnh mã** có thể đặt trong một phương thức. `BigInteger` không định nghĩa toán tử `+`; bạn phải gọi `a.add(b)`. Phương thức trả về **một đối tượng giá trị mới**; nó không sửa `a` hoặc `b`.

Nếu `a` được khai báo là `Number` dù bên trong trỏ tới `BigInteger`, trình biên dịch vẫn **không cho phép** `a.add(b)`, vì `Number` không có phương thức `add`. Cần giữ kiểu cụ thể trong API hoặc kiểm tra/cast hợp lệ. Tránh cast thiếu kiểm tra với dữ liệu không chắc chắn.

```java
Number value = new java.math.BigInteger("123");
if (value instanceof java.math.BigInteger big) {
    java.math.BigInteger plusOne = big.add(java.math.BigInteger.ONE);
}
```

**`instanceof` pattern matching** ở ví dụ này được hỗ trợ trong Java 17. Với lớp khác `BigInteger`, nhánh đó không chạy; chúng ta không giả định mọi `Number` đều có cùng phương thức cộng.

## 9. `BigDecimal.add`: phép cộng số thập phân theo ngữ nghĩa đã chọn

Nếu bài toán cần phép cộng decimal chính xác, đặc biệt trong bài toán tài chính hoặc các giá trị có thang đo thập phân, `BigDecimal` thường là lựa chọn phù hợp:

```java
java.math.BigDecimal a = new java.math.BigDecimal("0.1");
java.math.BigDecimal b = new java.math.BigDecimal("0.2");
java.math.BigDecimal sum = a.add(b); // 0.3 chính xác theo decimal.
```

Khi cả hai giá trị được tạo trực tiếp từ chuỗi thập phân `"0.1"` và `"0.2"`, `BigDecimal` giữ chính xác ý nghĩa thập phân và kết quả cộng là `0.3`. Ngược lại, phép `double` `0.1 + 0.2` không bằng `0.3` theo phép so sánh `==` do biểu diễn nhị phân hữu hạn.

Cảnh báo quan trọng: `new BigDecimal(0.1)` lấy **đúng giá trị nhị phân đã làm tròn của `double` 0.1**, vì vậy không có cùng ý nghĩa như `new BigDecimal("0.1")`. `BigDecimal.valueOf(0.1)` dùng biểu diễn chuỗi decimal round-trip của `double` và thường cho kết quả gần ý định chữ số người viết hơn, nhưng vẫn **không khôi phục được một giá trị chính xác ban đầu đã mất trước đó**.

Với phép cộng `BigDecimal.add` không truyền `MathContext`, các chữ số decimal được tính chính xác trong giới hạn tài nguyên. Nếu cung cấp `MathContext` và precision hữu hạn, phép toán có thể làm tròn theo bối cảnh đó; đặc tả nghiệp vụ cần nêu rõ.

Không nên chuyển **tùy ý mọi `Number` thành `BigDecimal` bằng `new BigDecimal(number.doubleValue())`**, vì một `BigInteger` lớn có thể bị mất chính xác ngay ở bước `doubleValue()`. Cũng không nên coi `number.toString()` của lớp con bất kỳ là dữ liệu decimal chuẩn: API `Number` không bắt buộc format chuỗi mà `BigDecimal` parse được.

## 10. Giải bài toán generic bằng cách truyền phép cộng của kiểu cụ thể

Với một kiểu `T extends Number`, Java cho biết `T` có các phương thức chuyển số, nhưng **không thể suy ra phép cộng giữ nguyên kiểu `T`**. Vì thế hàm sau là **không hợp lệ**:

```java
// Ví dụ cố ý SAI — không đưa vào chương trình chạy:
// static <T extends Number> T add(T a, T b) {
//     return a + b; // COMPILE ERROR: T + T không được định nghĩa.
// }
```

Một hướng thiết kế an toàn hơn là yêu cầu bên gọi cung cấp **chiến lược cộng** cho `T`:

```java
static <T extends Number> T addWith(
        T a, T b, java.util.function.BinaryOperator<T> adder) {
    return adder.apply(a, b);
}
```

Ví dụ cách gọi:

```java
Integer a = addWith(7, 9, Integer::sum); // 16

java.math.BigDecimal d =
        addWith(new java.math.BigDecimal("1.25"),
                new java.math.BigDecimal("2.75"),
                java.math.BigDecimal::add);    // 4.00
```

`BinaryOperator<T>` là một functional interface có chữ ký nhận hai `T` và trả một `T`. **Hàm generic chỉ tổ chức lời gọi, còn phép cộng thực sự do caller quyết định.** Ưu điểm là người dùng có thể chọn phát hiện overflow, xử lý decimal, áp dụng MathContext hoặc chính sách nghiệp vụ khác.

Trong trường hợp kiểu nguyên thủy cố định, có thể đơn giản hơn bằng `IntBinaryOperator`, `LongBinaryOperator` hoặc `DoubleBinaryOperator`, tránh boxing không cần thiết. Với `Integer::sum`, kết quả vẫn theo ngữ nghĩa `int` thông thường: **có thể overflow**. Nếu cần phát hiện tràn, truyền `Math::addExact` với kiểu phù hợp.

## 11. Danh sách `List<Number>`: cần chọn chính sách tổng

Một danh sách gồm nhiều lớp con:

```java
java.util.List<Number> values = java.util.List.of(1, 2.5, 3L);
```

Không có chuyện `List<Number>` tự biết nên dùng `BigDecimal`, `long` hay `double` cho tổng. Bạn có thể chủ động xác định chính sách “tổng gần đúng bằng `double`”:

```java
double total = 0.0;
for (Number value : values) {
    total += value.doubleValue();
}
// total == 6.5 với dữ liệu ví dụ.
```

Đây là **mảnh mã** khi `values` đã được khai báo. Nếu dùng stream, `values.stream().mapToDouble(Number::doubleValue).sum()` cũng thể hiện rõ lựa chọn tổng `double`, song nó không bảo đảm chính xác cho số nguyên lớn hoặc số thập phân tài chính.

Với dữ liệu chỉ gồm `BigInteger`, hãy sử dụng `List<BigInteger>` rồi `reduce(BigInteger.ZERO, BigInteger::add)`. Với `BigDecimal` cũng có thể dùng `reduce(BigDecimal.ZERO, BigDecimal::add)` với sự chú ý tới thang đo của kết quả. Đừng làm mất thông tin kiểu cụ thể quá sớm bằng cách đổi mọi cấu trúc dữ liệu thành `List<Number>` nếu ứng dụng yêu cầu toán học chính xác.

## 12. Kết hợp nhiều lớp con: thiết kế quy tắc trước khi viết code

Nếu API thật sự cần chấp nhận cả `Integer`, `Long`, `BigInteger`, `BigDecimal`, `Double` và những lớp khác, trước tiên cần chốt **quy tắc cộng**. Một lựa chọn thiết kế có kiểm soát:

1. **Số nguyên chính xác:** chỉ chấp nhận các loại nguyên đã nêu rõ và chuyển sang `BigInteger` không mất thông tin, sau đó dùng `BigInteger.add`.
2. **Số thập phân chính xác:** chấp nhận những nguồn decimal tin cậy (`BigDecimal`, chuỗi số decimal đã được parse theo contract) và dùng `BigDecimal.add`.
3. **Số thực gần đúng:** chấp nhận `float`/`double` hay các lớp tương thích với hợp đồng `doubleValue()`, kiểm tra phạm vi và đặc biệt NaN/Infinity nếu ứng dụng cần.
4. **Hỗn hợp:** quyết định tường minh được phép nâng lên `BigDecimal`, chuyển về `double`, hay phải báo lỗi khi hai kiểu không tương thích.
5. **Lớp con tùy biến:** không tự mặc định `Number.toString()` hay `doubleValue()` có tính chất chính xác/parse được. Cần adapter hoặc chính sách được lớp đó cung cấp.

Một hệ thống quan tâm type safety có thể sử dụng các đối tượng biểu diễn phép tính (strategy), ví dụ `NumericOps<T>` chứa phương thức `T add(T,T)`, hoặc phân tách API thành `sumIntegers`, `sumDecimals`, `sumApproximateDoubles`. Đây là cách đặt **ngữ nghĩa cộng** vào hợp đồng của ứng dụng thay vì giả định `Number` đã có sẵn hợp đồng đó.

## 13. Wrapper dạng nguyên tử không thay đổi quy tắc `Number`

`AtomicInteger` và `AtomicLong` trong `java.util.concurrent.atomic` cũng kế thừa `Number` và cho phép đọc các dạng số thông qua `intValue()`, `longValue()`, v.v. Nhưng chúng là **bộ chứa trạng thái có thể thay đổi**, thiết kế cho một số thao tác nguyên tử:

```java
java.util.concurrent.atomic.AtomicInteger counter =
        new java.util.concurrent.atomic.AtomicInteger(5);
int updated = counter.addAndGet(3); // 8.
```

`addAndGet(3)` là **phương thức trên `AtomicInteger`**, không phải `Number.add`. Nếu chỉ giữ biến ở kiểu `Number`:

```java
Number n = counter;
int snapshot = n.intValue(); // Đọc giá trị tại thời điểm gọi.
```

Bạn không thể gọi `n.addAndGet(3)` khi biến có kiểu tĩnh `Number`, vì `Number` không khai báo phương thức này. Ngoài ra, `n.intValue() + 3` chỉ cộng trên một giá trị snapshot, **không cập nhật atomic counter**. Không nên dùng `Number` để che giấu khác biệt về trạng thái/đồng bộ nếu logic nghiệp vụ cần cập nhật chính đối tượng.

## 14. Chương trình Java 17+ hoàn chỉnh

Tạo tệp **`NumberAdditionDemo.java`**:

```java
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BinaryOperator;

public class NumberAdditionDemo {
    static double approximateSum(Number a, Number b) {
        return a.doubleValue() + b.doubleValue();
    }

    static <T extends Number> T addWith(
            T a, T b, BinaryOperator<T> adder) {
        return adder.apply(a, b);
    }

    public static void main(String[] args) {
        Number left = Integer.valueOf(10);
        Number right = Double.valueOf(2.5);
        System.out.println("numberTypes="
                + left.getClass().getSimpleName()
                + "," + right.getClass().getSimpleName());
        System.out.println("approximateSum=" + approximateSum(left, right));
        System.out.println("intValueSum=" + (left.intValue() + right.intValue()));

        Integer boxedA = 10;
        Integer boxedB = 20;
        System.out.println("wrapperPlus=" + (boxedA + boxedB));

        BigInteger large = new BigInteger("9007199254740993");
        System.out.println("bigIntegerAdd=" + large.add(BigInteger.ONE));
        System.out.println("bigIntegerToDouble="
                + BigDecimal.valueOf(large.doubleValue()).toPlainString());
        System.out.println("bigIntegerToInt=" + new BigInteger("4294967296").intValue());

        BigDecimal tenth = new BigDecimal("0.1");
        BigDecimal fifth = new BigDecimal("0.2");
        System.out.println("decimalAdd=" + tenth.add(fifth).toPlainString());
        System.out.println("doublePoint3Equal=" + ((0.1 + 0.2) == 0.3));

        System.out.println("genericInteger="
                + addWith(7, 9, Integer::sum));
        System.out.println("genericDecimal="
                + addWith(new BigDecimal("1.25"),
                        new BigDecimal("2.75"), BigDecimal::add).toPlainString());

        List<Number> values = List.of(1, 2.5, 3L);
        double total = 0.0;
        for (Number value : values) {
            total += value.doubleValue();
        }
        System.out.println("heterogeneousDoubleTotal=" + total);

        AtomicInteger atomic = new AtomicInteger(5);
        Number asNumber = atomic;
        System.out.println("atomicAsNumber=" + asNumber.intValue());
        System.out.println("atomicAddAndGet=" + atomic.addAndGet(3));

        Integer nullable = null;
        try {
            int result = nullable + 1;
            System.out.println(result);
        } catch (NullPointerException ex) {
            System.out.println("nullUnboxing=NullPointerException");
        }
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```bash
javac --release 17 -encoding UTF-8 NumberAdditionDemo.java
java NumberAdditionDemo
```

Stdout chính xác:

```text
numberTypes=Integer,Double
approximateSum=12.5
intValueSum=12
wrapperPlus=30
bigIntegerAdd=9007199254740994
bigIntegerToDouble=9007199254740992
bigIntegerToInt=0
decimalAdd=0.3
doublePoint3Equal=false
genericInteger=16
genericDecimal=4.00
heterogeneousDoubleTotal=6.5
atomicAsNumber=5
atomicAddAndGet=8
nullUnboxing=NullPointerException
```

Các nhóm kết quả cần lưu ý:

- `numberTypes=Integer,Double` xác minh cả hai đối tượng đều là lớp con `Number`, nhưng `Number + Number` vẫn không hợp lệ về mặt kiểu tĩnh.
- `approximateSum=12.5`, `intValueSum=12`: **chọn phương thức chuyển kiểu quyết định kết quả**; `intValue()` cắt mất phần thập phân của `2.5`.
- `wrapperPlus=30`: `Integer + Integer` chạy được qua unboxing; đó không phải cộng object bằng nạp chồng toán tử.
- `bigIntegerAdd=9007199254740994` đối lập `bigIntegerToDouble=9007199254740992`: cộng `BigInteger` giữ chính xác; đổi qua `double` có thể làm tròn. `bigIntegerToInt=0` cho thấy chuyển hẹp có thể mất toàn bộ phần giá trị quan trọng.
- `decimalAdd=0.3` và `doublePoint3Equal=false`: `BigDecimal` từ chuỗi biểu diễn decimal chính xác, còn `double` là xấp xỉ nhị phân.
- `genericInteger=16` và `genericDecimal=4.00`: hai phép cộng được **truyền dưới dạng chiến lược cụ thể**.
- `heterogeneousDoubleTotal=6.5`: danh sách hỗn hợp có thể cộng theo chính sách `double`, chứ không nhờ toán tử `+` của `Number`.
- `atomicAddAndGet=8`: phương thức cập nhật của wrapper nguyên tử; `nullUnboxing=NullPointerException`: unboxing trên `null` thất bại khi chạy.

## 15. Những nhầm lẫn thường gặp

| Nhầm lẫn | Thực tế | Cách xử lý |
| --- | --- | --- |
| `Number` là cha của mọi wrapper nên `Number + Number` được | `Number` không thuộc nhóm kiểu được unbox trực tiếp | Giữ kiểu cụ thể hoặc chuyển theo chính sách |
| `Integer + Integer` chứng minh lớp `Integer` có `operator+` | Java thực hiện unboxing, cộng nguyên thủy và boxing | Phân biệt quy tắc ngôn ngữ với phương thức đối tượng |
| Mọi `Number` đều có `add` | `add` thuộc một số lớp cụ thể như `BigInteger`, `BigDecimal` | Dùng API lớp cụ thể |
| `doubleValue()` luôn giữ đủ giá trị | Số nguyên lớn/decimal có thể bị làm tròn, vượt phạm vi | Dùng `BigInteger` hoặc `BigDecimal` khi yêu cầu chính xác |
| `intValue()` chỉ bỏ phần lẻ | Nó còn có thể bỏ bit cao của số nguyên rất lớn | Chuyển chính xác với API `*ValueExact` khi có |
| Generic `<T extends Number>` có phép `a+b` | Bound `Number` không khai báo phép cộng | Nhận `BinaryOperator<T>` hoặc interface nghiệp vụ |
| `Number.toString()` luôn là chuỗi decimal hợp lệ | Contract của `Number` không bắt buộc mọi lớp con theo định dạng này | Hỗ trợ loại nguồn đã biết và kiểm thử |
| `new BigDecimal(0.1)` giống `new BigDecimal("0.1")` | Bản `double` đã bị làm tròn nhị phân | Dùng chuỗi decimal theo đặc tả |
| `List<Number>` có tổng chính xác mặc định | Không có kiểu/semantics chung cho phép cộng | Chọn `List<BigDecimal>`, `List<BigInteger>` hoặc quy tắc rõ |
| `AtomicInteger` cộng được qua `Number` | API nguyên tử của lớp cụ thể, không phải của `Number` | Giữ đúng kiểu khi cần cập nhật đồng thời |
| Unboxing wrapper `null` sẽ tự coi là 0 | Unboxing `null` ném `NullPointerException` | Kiểm tra hoặc quy ước dữ liệu thiếu rõ ràng |

## 16. Tự kiểm tra kiến thức

1. **Vì sao `Number a = 1; Number b = 2; a+b` không biên dịch?** Vì kiểu tĩnh của hai toán hạng là `Number`, không có unboxing chung hay overload phép cộng.
2. **Vì sao `Integer a = 1; Integer b = 2; a+b` hợp lệ?** Hai `Integer` được unbox thành hai `int`, sau đó cộng nguyên thủy.
3. **`Number x = 10, y = 2.5;` muốn cộng gần đúng thì viết gì?** `x.doubleValue() + y.doubleValue()`, kết quả `12.5`; phải chấp nhận chính sách `double`.
4. **Nếu thay bằng `x.intValue() + y.intValue()` thì kết quả ra sao?** `12`, vì phần 0.5 bị bỏ trước khi cộng.
5. **`BigInteger` cộng bằng cách nào?** Dùng `a.add(b)`, không phải `a+b`.
6. **`BigDecimal("0.1") + BigDecimal("0.2")` nên viết gì?** `new BigDecimal("0.1").add(new BigDecimal("0.2"))`, trả `0.3`.
7. **Một hàm generic `<T extends Number>` có thể `return a+b` không?** Không; cần chiến lược cộng `BinaryOperator<T>` hoặc API rõ cho từng kiểu.
8. **Vì sao `BigInteger("9007199254740993").doubleValue()` mất một đơn vị?** Vì `double` không lưu chính xác mọi số nguyên lớn hơn `2^53`.
9. **Nếu nhận một `Number` là `AtomicInteger`, gọi `intValue()+1` có cập nhật bộ đếm không?** Không; chỉ cộng một snapshot. Dùng `AtomicInteger.addAndGet` khi cần cập nhật nguyên tử.
10. **Nếu hệ thống cần cộng tiền chính xác, có nên nhận mọi loại `Number` rồi gọi `doubleValue()`?** Không; nên dùng kiểu và chính sách decimal chính xác (thường `BigDecimal`) cho dữ liệu hợp lệ ngay từ đầu.

**Ghi nhớ:** **`Number` là abstraction để cùng đọc giá trị dưới những kiểu nguyên thủy, không phải abstraction cho phép cộng**. Toán tử `+` của Java được quyết định bởi kiểu tĩnh, unboxing và quy tắc số nguyên thủy; các kiểu số đối tượng khác có API riêng. Khi cần tổng của nhiều lớp con, hãy **quyết định rõ kiểu đích, độ chính xác, overflow, cách xử lý hỗn hợp và lỗi**, rồi dùng phương thức cụ thể hoặc truyền phép toán tường minh.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="number-and-wrappers-011">Giới hạn khi tự xây dựng một lớp con Number</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn khi tự xây dựng một lớp con `Number` trong Java

## 1. Vì sao `extends Number` chưa đủ để có một kiểu số hoàn chỉnh?

Trong Java, `java.lang.Number` là **lớp trừu tượng** (*abstract class*) đại diện chung cho những đối tượng có thể cung cấp giá trị ở dạng một số kiểu nguyên thủy. Các lớp quen thuộc như `Integer`, `Long`, `Float`, `Double`, `BigInteger` và `BigDecimal` đều thuộc họ `Number`.

Bạn có thể xây dựng một lớp riêng:

```java
final class CustomNumber extends Number {
    @Override public int intValue() { return 0; }
    @Override public long longValue() { return 0L; }
    @Override public float floatValue() { return 0.0f; }
    @Override public double doubleValue() { return 0.0; }
}
```

Lớp này **biên dịch được**, nhưng mới chỉ đáp ứng hợp đồng tối thiểu: nó cung cấp bốn cách chuyển đổi thành primitive. Nó chưa có phép cộng, phép nhân, quy tắc so sánh giá trị, kiểm tra miền, độ chính xác, đơn vị đo hay các bất biến mà một kiểu số thực tế cần.

Vì vậy, **kế thừa `Number` không biến lớp của bạn thành một kiểu primitive hoặc wrapper có đầy đủ cú pháp số học**. Trong Java, không có cơ chế nạp chồng toán tử `+`, `-`, `*`, `/` dành cho các đối tượng `Number` tự định nghĩa.

Bài học này tập trung vào **giới hạn và trách nhiệm thiết kế khi tự xây dựng một lớp con `Number`**, cách đáp ứng đúng API, những mất mát dữ liệu có thể xảy ra và khi nào nên chọn một thiết kế khác.

## 2. Hợp đồng tối thiểu của `Number`

`Number` là lớp trừu tượng, triển khai `java.io.Serializable`. Bốn phương thức **abstract** mà lớp con cụ thể phải triển khai là:

| Phương thức | Kiểu kết quả | Trách nhiệm |
|---|---|---|
| `intValue()` | `int` | Biểu diễn giá trị dưới dạng số nguyên 32 bit |
| `longValue()` | `long` | Biểu diễn giá trị dưới dạng số nguyên 64 bit |
| `floatValue()` | `float` | Biểu diễn giá trị dưới dạng số thực nhị phân 32 bit |
| `doubleValue()` | `double` | Biểu diễn giá trị dưới dạng số thực nhị phân 64 bit |

Ngoài ra, `Number` đã cung cấp hai phương thức cụ thể:

- `byteValue()`: về cơ bản chuyển `(byte) intValue()`.
- `shortValue()`: về cơ bản chuyển `(short) intValue()`.

Điều đó có nghĩa bạn **không bắt buộc override** `byteValue()` hoặc `shortValue()` để có một lớp con không trừu tượng. Tuy nhiên, mặc định của chúng có thể làm mất bit theo quy tắc thu hẹp số nguyên. Nếu mô hình dữ liệu cần cách xử lý khác, hãy cân nhắc thiết kế API tường minh và kiểm thử kỹ.

Ví dụ:

```java
Number n = Integer.valueOf(258);
System.out.println(n.intValue());   // 258
System.out.println(n.byteValue());  // 2
```

Số 258 khi chuyển thành `byte` chỉ giữ 8 bit thấp, cho 2. **Việc có phương thức `byteValue()` không chứng minh giá trị sẽ vừa miền `byte`.**

### Điều mà lớp `Number` không hứa hẹn

`Number` không cung cấp một hợp đồng chung về:

- Miền giá trị, độ chính xác hay đơn vị.
- Số nguyên hay số thập phân; hữu hạn hay có NaN/Infinity.
- Phép cộng, trừ, nhân, chia giữa hai đối tượng `Number`.
- Kiểu kết quả sau phép tính.
- Thứ tự so sánh tự nhiên hoặc quy tắc bằng nhau theo giá trị.
- Chuyển đổi **chính xác** sang primitive.
- An toàn luồng hoặc tính bất biến của lớp con.

Một lớp con có thể bổ sung những khả năng đó, nhưng **đó là hợp đồng do chính lớp con thiết kế**, không tự xuất hiện vì kế thừa `Number`.

## 3. Kế thừa `Number` không tạo toán tử số học

Hãy xem:

```java
Number a = Integer.valueOf(7);
Number b = Long.valueOf(5);

// Number c = a + b; // LỖI biên dịch
int c = a.intValue() + b.intValue(); // 12, nhưng có thể mất dữ liệu
```

Dòng đầu tiên bị comment vì `a + b` **không hợp lệ**. Toán tử `+` trên số yêu cầu các toán hạng có thể chuyển đổi theo quy tắc số học của ngôn ngữ. Một đối tượng có kiểu tĩnh `Number` **không được Java tự động unbox thành kiểu số nguyên thủy tùy ý**.

Để tính toán, phải gọi phương thức chuyển đổi phù hợp hoặc sử dụng API dành riêng cho kiểu thực:

```java
BigDecimal price = new BigDecimal("12.50");
BigDecimal fee = new BigDecimal("2.25");
BigDecimal total = price.add(fee); // 14.75
```

Đây là **đoạn minh họa**, cần đặt trong phạm vi có import `java.math.BigDecimal`. Các phương thức như `add`, `subtract` và `multiply` là **API của `BigDecimal`**, không phải của `Number`.

**Không viết một hàm cộng tổng quát nhận hai `Number` rồi mặc định gọi `doubleValue()` để “giải quyết mọi kiểu số”.** Phương án ấy có thể làm mất chính xác của `BigInteger`/`BigDecimal` và có thể biến giá trị quá lớn thành Infinity.

## 4. Đừng nhầm `Number` với các lớp wrapper được auto-unbox

Java có hỗ trợ **unboxing conversion** cho các wrapper tương ứng với primitive, chẳng hạn `Integer → int` và `Double → double`:

```java
Integer x = 7;
int y = x;        // hợp lệ: unboxing Integer -> int
int sum = x + 5;  // hợp lệ: unboxing rồi cộng
```

Nhưng với biến có **kiểu tĩnh là `Number`**:

```java
Number x = Integer.valueOf(7);

// int y = x;       // LỖI biên dịch: Number không auto-unbox thành int
// int sum = x + 5; // LỖI biên dịch
int y = x.intValue(); // hợp lệ: gọi API Number một cách tường minh
```

Dù ở thời điểm chạy đối tượng thực tế là `Integer`, trình biên dịch xét kiểu tĩnh `Number` của biến. Không có quy tắc “unbox tùy động” đối với mọi lớp con `Number`.

Với một lớp con riêng như `FixedCents extends Number`, Java cũng **không tự có chuyển đổi `FixedCents → long`**, kể cả khi lớp đã định nghĩa `longValue()`. Phải gọi phương thức rõ ràng.

Đây là giới hạn thiết kế căn bản: kế thừa `Number` giúp cung cấp một **giao diện chuyển đổi giá trị ở mức lớp**, không mở rộng cú pháp và hệ thống kiểu primitive của Java.

## 5. Phương thức chuyển đổi có thể làm mất phần lẻ, tràn hoặc mất chính xác

Các phương thức `intValue()`, `longValue()`, `floatValue()` và `doubleValue()` không phải những phiên bản luôn thành công và luôn chính xác. Chúng có thể trả về một giá trị được làm tròn, thu hẹp hoặc mất thông tin.

### Từ decimal sang số nguyên

```java
Number amount = new BigDecimal("12.99");

System.out.println(amount.intValue());  // 12
System.out.println(amount.longValue()); // 12
```

Phần lẻ bị loại khi chuyển sang số nguyên. Với số âm, cần chú ý quy tắc cắt về 0 của chuyển đổi kiểu số thực/nguyên trong nhiều trường hợp; riêng `BigDecimal` thực thi quy tắc chuyển đổi theo hợp đồng của API.

### Vượt miền `int`

```java
BigInteger big = new BigInteger("2147483648");

System.out.println(big.intValue()); // -2147483648
```

Giá trị 2_147_483_648 không vừa `int`; `BigInteger.intValue()` lấy các bit thấp, dẫn tới một giá trị có dấu khác với số gốc. Nếu cần yêu cầu chuyển **chính xác**, `BigInteger` có `intValueExact()`: nó ném `ArithmeticException` nếu không vừa miền.

### Mất độ chính xác khi chuyển sang `double`

Binary64 có khoảng 53 bit độ chính xác phần trị. Hai số nguyên lớn khác nhau có thể chuyển thành **cùng một giá trị `double`**:

```java
BigInteger a = new BigInteger("9007199254740992");
BigInteger b = new BigInteger("9007199254740993");

System.out.println(a.equals(b)); // false
System.out.println(a.doubleValue() == b.doubleValue()); // true
```

Vì vậy, **không dùng `doubleValue()` làm khóa so sánh giá trị chung cho mọi `Number`**. Nếu lớp của bạn cần kiểm tra chính xác, hãy cung cấp phương thức riêng như `toBigDecimal()` hoặc `longValueExact()` khi mô hình cho phép.

### Chính sách chuyển đổi cho lớp con

Khi tự viết `Number`, bạn cần quyết định và ghi rõ:

- Với giá trị có phần lẻ, `intValue()`/`longValue()` cắt phần lẻ, làm tròn hay từ chối?
- Khi số vượt miền `int` hoặc `long`, xử lý thế nào?
- `floatValue()`/`doubleValue()` có thể mất độ chính xác đến mức nào?
- Có hỗ trợ NaN/Infinity không? Nếu là tiền tệ decimal, thường **không nên** đưa các giá trị ấy vào miền nghiệp vụ.
- Có phương thức **chuyển chính xác và báo lỗi rõ ràng** bên cạnh các phương thức `Number` không?

Đừng dựa vào lời gọi chuyển kiểu để che giấu đơn vị hoặc làm mất dữ liệu ngoài ý muốn.

## 6. Chương trình kiểm chứng các giới hạn của `Number`

Tạo file `NumberLimitsDemo.java`:

```java
import java.math.BigDecimal;
import java.math.BigInteger;

public class NumberLimitsDemo {
    public static void main(String[] args) {
        Number wrapperAsNumber = Integer.valueOf(7);
        System.out.println("manual extraction: "
                + (wrapperAsNumber.intValue() + 5));

        Number decimalAsNumber = new BigDecimal("12.99");
        System.out.println("decimal to int: "
                + decimalAsNumber.intValue());

        BigInteger tooLarge = new BigInteger("2147483648");
        System.out.println("overflowing intValue: "
                + tooLarge.intValue());

        try {
            System.out.println(tooLarge.intValueExact());
        } catch (ArithmeticException e) {
            System.out.println("intValueExact: out of range");
        }

        BigInteger a = new BigInteger("9007199254740992");
        BigInteger b = new BigInteger("9007199254740993");
        System.out.println("big integers equal: " + a.equals(b));
        System.out.println("converted doubles equal: "
                + (a.doubleValue() == b.doubleValue()));

        Number n = Integer.valueOf(258);
        System.out.println("inherited byte conversion: "
                + n.byteValue());
    }
}
```

Chạy với Java 17+:

```bash
javac NumberLimitsDemo.java
java NumberLimitsDemo
```

Kết quả:

```text
manual extraction: 12
decimal to int: 12
overflowing intValue: -2147483648
intValueExact: out of range
big integers equal: false
converted doubles equal: true
inherited byte conversion: 2
```

Chương trình cho thấy `Number` là **API chuyển đổi**, còn giới hạn miền, độ chính xác và chính sách báo lỗi vẫn phụ thuộc **kiểu số thực tế**.

## 7. Thiết kế ví dụ: số tiền cố định hai chữ số thập phân

Giả sử mô hình muốn biểu diễn một giá trị tiền bằng **số xu nguyên**, và mọi phép toán cộng phải được kiểm soát tràn. Một lớp `FixedCents` tự định nghĩa có thể lưu một biến `long cents`.

Đây chỉ là **mô hình học tập cố định hai chữ số thập phân**, chưa đầy đủ yêu cầu sản phẩm tiền tệ thực tế. Nó **không lưu currency**, không có quy tắc đổi tiền và không tự xử lý thuế hay phương thức làm tròn tỷ giá.

Quy ước rõ ràng:

- Giá trị gốc `cents` là **đơn vị nhỏ nhất**, ví dụ 1234 nghĩa là 12.34 đơn vị tiền.
- `toBigDecimal()` trả giá trị decimal **chính xác** theo đơn vị tiền chính.
- `intValue()` và `longValue()` trả **số đơn vị tiền nguyên**, cắt phần lẻ về 0.
- `floatValue()`, `doubleValue()` trả một **xấp xỉ binary floating-point** của giá trị decimal.
- Phép cộng phải gọi `plus`, không thể dùng `+` giữa hai `FixedCents`.
- `Math.addExact` dùng để phát hiện tràn số xu.
- `equals`/`hashCode`/`compareTo` xét cùng đơn vị và cùng giá trị `cents`.

Việc định nghĩa các quy tắc này **không do `Number` cung cấp**. Nhà phát triển phải thiết kế và công bố chúng.

## 8. Lớp con `Number` có thể biên dịch, chạy và kiểm tra được

Tạo file `CustomNumberDemo.java` chứa **hai lớp**: lớp `public` dùng để chạy và lớp `FixedCents` lồng tĩnh.

```java
import java.math.BigDecimal;

public class CustomNumberDemo {

    static final class FixedCents extends Number
            implements Comparable<FixedCents> {

        private static final long serialVersionUID = 1L;
        private final long cents;

        FixedCents(long cents) {
            this.cents = cents;
        }

        BigDecimal toBigDecimal() {
            return BigDecimal.valueOf(cents, 2);
        }

        FixedCents plus(FixedCents other) {
            return new FixedCents(Math.addExact(cents, other.cents));
        }

        @Override
        public int intValue() {
            return (int) longValue();
        }

        @Override
        public long longValue() {
            return cents / 100L; // cắt phần lẻ về 0
        }

        @Override
        public float floatValue() {
            return toBigDecimal().floatValue();
        }

        @Override
        public double doubleValue() {
            return toBigDecimal().doubleValue();
        }

        @Override
        public int compareTo(FixedCents other) {
            return Long.compare(cents, other.cents);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof FixedCents that
                    && cents == that.cents;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(cents);
        }

        @Override
        public String toString() {
            return toBigDecimal().toPlainString();
        }
    }

    public static void main(String[] args) {
        FixedCents price = new FixedCents(1234);
        FixedCents fee = new FixedCents(266);
        FixedCents total = price.plus(fee);

        Number generic = price;

        System.out.println("price: " + price);
        System.out.println("fee: " + fee);
        System.out.println("total: " + total);
        System.out.println("as long: " + generic.longValue());
        System.out.println("as double: " + generic.doubleValue());
        System.out.println("as byte: " + generic.byteValue());
        System.out.println("same cents: "
                + price.equals(new FixedCents(1234)));
        System.out.println("price > fee: "
                + (price.compareTo(fee) > 0));
    }
}
```

Biên dịch và chạy:

```bash
javac CustomNumberDemo.java
java CustomNumberDemo
```

Kết quả:

```text
price: 12.34
fee: 2.66
total: 15.00
as long: 12
as double: 12.34
as byte: 12
same cents: true
price > fee: true
```

### Vì sao cách triển khai này chỉ là một **lựa chọn chính sách**?

Một số quyết định được viết thành mã, không phải “nghĩa chuẩn duy nhất” của `Number`:

1. `longValue()` chọn trả **đơn vị chính nguyên**, không trả số xu. Nếu một ứng dụng khác mong `longValue()` trả 1234, hai hệ thống sẽ diễn giải sai nhau. Hợp đồng API phải mô tả rõ.
2. `intValue()` gọi `(int) longValue()`. Với số đơn vị chính vượt miền `int`, phép ép kiểu này **có thể mất bit**. Không dùng nó để kiểm tra tiền lớn.
3. `floatValue()` và `doubleValue()` được phép trả **xấp xỉ** — số thập phân `12.34` không nhất thiết có biểu diễn nhị phân chính xác.
4. `plus` dùng `Math.addExact` để từ chối tràn số `long` của cents. Nhưng lớp chưa có phép trừ, nhân với tỷ giá, ràng buộc currency hoặc xử lý làm tròn.
5. `equals` và `hashCode` cùng dựa vào `cents`. Đây là hành vi phải **tự override**: kế thừa `Number` không tự tạo bình đẳng theo giá trị.

Đặc biệt: `Number.byteValue()` của lớp ví dụ sẽ kế thừa cơ chế qua `intValue()`, rồi có thể thu hẹp tiếp về `byte`. Nếu `longValue()` có kết quả lớn hoặc âm, giá trị `byteValue()` cũng có thể khác giá trị toán học mong muốn.

## 9. Tại sao phải quan tâm `equals`, `hashCode` và `compareTo`?

`Number` không đảm bảo hai đối tượng khác nhau nhưng có “cùng giá trị” sẽ bằng nhau theo `equals`. Nếu không override, lớp con kế thừa hành vi so sánh đối tượng của `Object`.

Ví dụ hai đối tượng:

```java
FixedCents first = new FixedCents(1234);
FixedCents second = new FixedCents(1234);
```

Hai dòng trên là **minh họa** bên trong phạm vi lớp có thể truy cập `FixedCents`. Nếu `FixedCents` không tự triển khai `equals`, `first.equals(second)` có thể false mặc dù `cents` bằng nhau.

Khi dùng các cấu trúc như `HashSet` hoặc `HashMap`, cần đảm bảo:

- Nếu `a.equals(b)` true thì `a.hashCode() == b.hashCode()`.
- Giá trị sử dụng trong `equals` và `hashCode` phải được quản lý ổn định; **bất biến** là lựa chọn tốt cho value object.
- `compareTo` (nếu có) nên nhất quán với ý nghĩa thứ tự của mô hình và, khi có thể, với `equals`.
- Không so sánh giá trị tùy ý bằng `doubleValue()` của các `Number` không cùng miền/độ chính xác.

Ví dụ `new BigDecimal("2.0")` và `new BigDecimal("2.00")` có giá trị toán học bằng nhau theo `compareTo`, nhưng `equals` false vì khác scale. Điều đó cho thấy ngay cả **những lớp con `Number` của thư viện chuẩn cũng có chính sách bình đẳng riêng**.

### Có cần `Comparable<Number>` cho mọi kiểu số không?

Không. Một phép so sánh có ý nghĩa cần xét **cùng miền dữ liệu và cùng đơn vị**. Một đối tượng chứa mét không tự so sánh được với một đối tượng chứa tiền chỉ vì cả hai đều `extends Number`.

Trong ví dụ, `Comparable<FixedCents>` có chủ đích: chỉ so các giá trị cùng mô hình. Không định nghĩa thứ tự tổng quát cho mọi `Number` vì không tồn tại quy tắc thống nhất về NaN, độ chính xác, scale, đơn vị, Overflow và các kiểu số tùy biến.

## 10. Tính bất biến, serialize và an toàn luồng

`Number` triển khai `Serializable`, nên lớp con có liên hệ với Java Serialization. Tuy nhiên:

- Các trường dữ liệu của lớp con vẫn phải phù hợp cơ chế serialization nếu muốn serialize/deserialize thành công.
- Một lớp bất biến có thể cần thêm các bước bảo vệ bất biến khi deserialize nếu triển khai thực tế có logic phức tạp.
- `serialVersionUID` là một phần của quản lý tương thích serialization truyền thống; nó **không tự bảo đảm dữ liệu an toàn hoặc tương thích** giữa mọi phiên bản.
- Nếu không cần Java native serialization, không nên vì kế thừa `Number` mà mặc định dùng serialization đối tượng làm định dạng lưu trữ/giao tiếp bên ngoài.

Ví dụ `FixedCents` dùng `private final long cents` và chỉ tạo đối tượng mới khi tính toán. Cách tiếp cận **immutable value object** giúp tránh việc giá trị dùng làm khóa trong hash collection bị đổi sau khi thêm vào tập.

Nhưng kế thừa `Number` **không tự làm mọi lớp con trở nên immutable hoặc thread-safe**. Ví dụ một số kiểu liên quan đến trạng thái số như `AtomicInteger` cũng kế thừa `Number` nhưng có mục tiêu và cơ chế cập nhật khác.

Một lớp con mutable cần đặc biệt chú ý đồng bộ, quan sát trạng thái và hợp đồng `equals`/`hashCode` nếu đối tượng được chia sẻ giữa nhiều luồng.

## 11. Giới hạn của hệ thống kiểu và cú pháp Java

### 11.1. Một lớp chỉ được kế thừa một lớp cha trực tiếp

Java hỗ trợ **single inheritance** đối với class. Nếu bạn viết:

```java
class MyNumber extends Number {
    // ...
}
```

thì `MyNumber` không thể đồng thời `extends` một lớp cha khác. Bạn có thể triển khai nhiều interface, như `Comparable<MyNumber>`, nhưng không thể có hai superclass.

**Record** cũng không thể `extends Number` vì mọi record trực tiếp mở rộng `java.lang.Record`. Một record phù hợp hơn khi bạn cần value object đơn giản nhưng không cần quan hệ kiểu với `Number`.

Nếu mô hình cần đồng thời một superclass khác và các thao tác số, hãy cân nhắc **composition** (chứa `BigDecimal` hoặc `BigInteger` làm trường) và API tường minh thay vì cố kế thừa `Number`.

### 11.2. Java không cho tạo “primitive mới”

Một `Number` tự viết:

- Không có literal riêng như `123usd`.
- Không dùng trực tiếp toán tử số học primitive.
- Không được nhận bởi mọi overload yêu cầu `int`/`double` nếu không chuyển đổi.
- Không hỗ trợ unboxing tự động chỉ vì đã override `intValue()`.
- Không đổi quy tắc thăng cấp số học đã cố định của Java.

Đây là giới hạn ngôn ngữ, không phải do thiếu override một phương thức cụ thể.

### 11.3. Không thể dựa vào `instanceof Number` để suy ra mọi loại số đều so được

`instanceof Number` chỉ kiểm tra quan hệ kiểu tham chiếu. Đối tượng có thể là `BigDecimal`, `BigInteger`, `Integer` hoặc bất kỳ kiểu số tùy biến có miền/đơn vị riêng.

```java
static String describe(Number value) {
    if (value == null) {
        return "missing";
    }
    return value.getClass().getSimpleName()
            + " -> " + value.doubleValue();
}
```

Đây là phương thức minh họa **không bảo toàn giá trị số chính xác**. Nó phù hợp cho log xấp xỉ khi có chủ đích, nhưng không phải hàm chuẩn hóa số an toàn cho tài chính hoặc so sánh giá trị lớn.

## 12. Thiết kế chuyển đổi **chính xác** bên cạnh các phương thức `Number`

Bốn phương thức abstract phục vụ **truy cập giá trị nguyên thủy**. Nếu miền số của bạn đòi hỏi không mất dữ liệu, nên cung cấp API bổ sung với tên biểu đạt chính sách:

```java
BigDecimal toBigDecimal()
long centsExact()
int intValueExact()
```

Đây là **các chữ ký gợi ý**, không phải đoạn chương trình độc lập. Ví dụ:

- `toBigDecimal()` giữ nguyên phần thập phân trong hệ decimal khi cấu trúc nội bộ cho phép.
- `centsExact()` trả số xu gốc và mô tả đơn vị ngay ở tên hàm.
- `intValueExact()` chỉ trả `int` nếu giá trị toán học không có phần lẻ và nằm trong miền `int`; nếu không thì ném ngoại lệ có kiểm soát.

Một triển khai đơn giản cho lớp `FixedCents` bên trên có thể sử dụng:

```java
int intValueExact() {
    return toBigDecimal().intValueExact();
}
```

Phương thức này phải được đặt **bên trong lớp `FixedCents`**. Nó yêu cầu số tiền theo đơn vị chính là số nguyên chính xác: 12.00 được phép, 12.34 bị từ chối. Nếu nhu cầu thật là trả số xu dạng `int`, cần API khác như `Math.toIntExact(cents)`, cũng với tên đơn vị rõ ràng.

Tương tự, khi nhận một `Number` bất kỳ từ API, không thể bảo đảm tự khôi phục giá trị decimal gốc từ `doubleValue()`. Nếu cần decimal chính xác, hãy yêu cầu đầu vào `BigDecimal` hoặc giao diện riêng cung cấp decimal ở ranh giới thiết kế.

## 13. Khi nào nên dùng composition thay vì `extends Number`?

Một lớp giá trị có ý nghĩa miền dữ liệu, ví dụ `Money`, `Percentage`, `Temperature`, `Distance`, thường cần:

- Đơn vị và ràng buộc đi kèm giá trị.
- Các phép toán có ý nghĩa và không có ý nghĩa (ví dụ tiền + tiền cùng currency, nhưng không cộng tiền với mét).
- Quy tắc làm tròn/kiểm tra riêng.
- Kiểm soát so sánh, chuyển đổi và serialize.

Một đối tượng `Money` **không nhất thiết cần kế thừa `Number`**. Dùng composition với `BigDecimal` và `Currency` thường thể hiện mô hình rõ ràng hơn:

```java
import java.math.BigDecimal;
import java.util.Currency;

record Money(BigDecimal amount, Currency currency) {
    Money {
        if (amount == null || currency == null) {
            throw new IllegalArgumentException("missing money data");
        }
    }

    Money add(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("currency mismatch");
        }
        return new Money(amount.add(other.amount), currency);
    }
}
```

Đây là **đoạn khai báo record minh họa**, không có phương thức `main`. Nó cho thấy cách giữ mô hình bằng **đơn vị và API riêng**; chưa xử lý scale, currency minor unit, làm tròn hay toàn bộ yêu cầu triển khai tài chính.

### Vì sao composition có thể an toàn hơn?

Nếu `Money extends Number`, một framework có thể tùy ý gọi `doubleValue()` và vô tình làm mất precision, currency hay scale. Một record/class không kế thừa `Number` sẽ **buộc bên gọi chọn phép chuyển đổi hoặc thao tác có đơn vị rõ ràng**.

Hãy chỉ `extends Number` khi thật sự cần hỗ trợ một API đa hình nhận `Number` và bạn **có thể định nghĩa cách chuyển sang bốn primitive** nhất quán, dễ hiểu, được tài liệu hóa. Nếu không có yêu cầu tương thích đó, composition thường có ít hành vi ngầm hơn.

## 14. Generic, collection và giới hạn của kiểu `Number`

Java generics hỗ trợ:

```java
java.util.List<Number> numbers = java.util.List.of(
        Integer.valueOf(1),
        Long.valueOf(2L),
        new BigDecimal("3.5"));
```

Một danh sách như trên có thể giữ các lớp số khác nhau, nhưng Java **không tự có phép cộng tổng quát chính xác** cho `List<Number>`. Nếu duyệt mỗi phần tử rồi cộng `doubleValue()`, ta đã chọn chính sách **tính xấp xỉ binary64**.

```java
double sum = 0.0;
for (Number n : numbers) {
    sum += n.doubleValue();
}
```

Đây là **đoạn mã minh họa**; nó có thể phù hợp với dữ liệu đo xấp xỉ, nhưng không giữ exact decimal cho mọi `Number`.

Nếu API chỉ nhận số nguyên nhỏ và ổn định, có thể hạn chế kiểu cụ thể như `List<Integer>`; nếu đòi decimal chính xác thì sử dụng `List<BigDecimal>` hoặc một abstraction miền dữ liệu phù hợp. **Ràng buộc kiểu đầu vào chặt hơn có thể cải thiện tính đúng đắn hơn là chấp nhận mọi `Number`.**

### Generic bound không tạo phép toán

```java
static <T extends Number> double approximate(T value) {
    return value.doubleValue();
}
```

Phương thức generic trên hợp lệ, nhưng vẫn **không thể viết `value + value`** mà mong toán tử hiểu đó là số. Một bound `T extends Number` chỉ bảo đảm truy cập được API `Number`; không thêm hỗ trợ số học primitive, độ chính xác hoặc thao tác giữa các giá trị `T`.

## 15. Nhầm lẫn phổ biến và cách tránh

| Ngộ nhận | Sự thật | Cách xử lý |
|---|---|---|
| `extends Number` thì dùng được `a + b` | Java không cho nạp chồng toán tử cho lớp số tùy biến | Cung cấp `add/plus` hoặc API miền dữ liệu |
| Cần override cả 6 phương thức chuyển đổi | Chỉ 4 phương thức là abstract; `byteValue/shortValue` đã có mặc định | Override thêm khi có hợp đồng phù hợp |
| `Number n` sẽ tự unbox như `Integer` | Kiểu tĩnh `Number` không có unboxing primitive chung | Gọi `intValue()`/API cụ thể |
| `intValue()` luôn chính xác | Có thể mất phần lẻ hoặc bit khi vượt miền | Dùng phương thức `...Exact()` nếu có |
| `doubleValue()` đủ để so mọi số | Số nguyên lớn/decimal có thể mất precision | So theo kiểu gốc và miền dữ liệu |
| Kế thừa `Number` tự định nghĩa `equals` giá trị | Không; hành vi mặc định đến từ `Object` | Thiết kế `equals/hashCode` nhất quán |
| `Number` có thứ tự so sánh tự nhiên | Không bắt buộc `Comparable` | Implement `Comparable<T>` khi có ý nghĩa |
| `Number` bảo đảm immutable | Không | Thiết kế `final`, trường `final` hoặc quản lý đồng bộ |
| `Number` tự kiểm tra overflow | Không | `Math.*Exact` hoặc API kiểm tra |
| `Number` bảo toàn đơn vị đo | Không có trường/unit chung | Đặt API và loại dữ liệu rõ đơn vị |
| Mọi `Number` có thể serialize an toàn ra mạng | Serializable không tương đương định dạng trao đổi an toàn | Dùng định dạng dữ liệu xác định, kiểm soát schema |
| `record` có thể `extends Number` | Record đã kế thừa `Record` | Dùng composition nếu muốn value record |
| `List<Number>` có `sum` chuẩn cho mọi số | Không có mô hình cộng/tính chính xác duy nhất | Chọn thuật toán, kiểu và quy tắc phù hợp |

## 16. Bài tập tự kiểm tra

**Câu 1.** Muốn tạo lớp `extends Number` cụ thể, cần override bốn phương thức nào?  
**Đáp án:** `intValue()`, `longValue()`, `floatValue()` và `doubleValue()`.

**Câu 2.** Có bắt buộc override `byteValue()` và `shortValue()` không?  
**Đáp án:** Không. `Number` đã triển khai dựa trên `intValue()`, nhưng chuyển đổi có thể mất thông tin.

**Câu 3.** Nếu `Number n = Integer.valueOf(10);`, có viết `int x = n;` được không?  
**Đáp án:** Không. Biến có kiểu tĩnh `Number`; cần `int x = n.intValue();` hoặc thiết kế API sử dụng kiểu cụ thể.

**Câu 4.** Lớp `FixedCents extends Number` có tự dùng `price + fee` được không?  
**Đáp án:** Không. Phải định nghĩa rồi gọi phương thức `price.plus(fee)` hoặc API khác.

**Câu 5.** `new BigInteger("2147483648").intValue()` bằng bao nhiêu?  
**Đáp án:** -2_147_483_648 do mất bit cao theo quy tắc chuyển hẹp của `BigInteger`. Muốn từ chối số ngoài miền dùng `intValueExact()`.

**Câu 6.** Tại sao hai `BigInteger` hơn nhau 1 có thể trông bằng nhau sau `doubleValue()`?  
**Đáp án:** Vì binary64 có giới hạn độ chính xác phần trị; các giá trị lớn có thể làm tròn vào cùng một `double`.

**Câu 7.** Nếu hai đối tượng `FixedCents` có cùng `cents`, cần những phương thức nào để `HashSet` coi chúng bằng nhau?  
**Đáp án:** `equals` và `hashCode` được triển khai nhất quán.

**Câu 8.** Có nên để `longValue()` của kiểu tiền trả số xu hay số đơn vị chính?  
**Đáp án:** Không có đáp án chung. Phải định nghĩa chính sách rõ; tốt nhất cung cấp API có tên đơn vị, như `centsExact()`, để tránh nhập nhằng.

**Câu 9.** Nếu lớp muốn so thứ tự các số cùng loại, `Number` có đủ không?  
**Đáp án:** Không. Có thể triển khai `Comparable<MyNumber>` hoặc phương thức so sánh riêng.

**Câu 10.** Vì sao `Money` có thể nên là record/class chứa `BigDecimal` thay vì `extends Number`?  
**Đáp án:** Vì nó cần bảo vệ đơn vị, quy tắc chuyển tiền và tính toán miền dữ liệu; kế thừa `Number` có thể khuyến khích chuyển đổi primitive làm mất thông tin.

**Câu 11.** `class MyNumber extends Number` có thể còn `extends BaseEntity` không?  
**Đáp án:** Không, Java chỉ hỗ trợ một superclass trực tiếp. Có thể triển khai thêm các interface hoặc dùng composition.

**Câu 12.** `T extends Number` có cho phép `a + b` với hai biến `T` không?  
**Đáp án:** Không. Generic bound chỉ bảo đảm các phương thức của `Number`, không tạo toán tử cộng.

## 17. Checklist trước khi quyết định tự tạo lớp con `Number`

1. **Lý do kế thừa:** có framework/API thực sự yêu cầu kiểu `Number` không? Nếu không, composition có rõ nghĩa hơn không?
2. **Mô hình dữ liệu:** số nguyên, decimal, tỷ lệ, giá trị đo hay số kèm đơn vị? Miền cho phép là gì?
3. **Bốn phương thức bắt buộc:** mỗi phương thức sẽ trả giá trị nào khi phần lẻ, vượt miền hoặc mất độ chính xác?
4. **API chính xác:** có cần `toBigDecimal()`, `longValueExact()`, `centsExact()`, hoặc phương thức báo lỗi thay vì làm mất bit?
5. **Phép toán:** cộng, trừ, nhân, chia, làm tròn, chia cho 0 và tràn số xử lý thế nào?
6. **So sánh:** `equals/hashCode` và `compareTo` xét những trường/đơn vị nào? Có tương thích với các collection không?
7. **Đối tượng:** immutable hay mutable? Nếu mutable, đồng bộ và tính ổn định của hash key được bảo đảm ra sao?
8. **Ranh giới hệ thống:** có cần serialize, lưu database, JSON, logging? Có cách biểu diễn rõ ràng ngoài primitive không?
9. **Kiểm thử:** bao phủ số âm, số 0, cực trị, phần lẻ, giá trị ngoài miền, lỗi chuyển kiểu và các bất biến.
10. **Tài liệu hợp đồng:** người gọi có biết chính xác cách hiểu các kết quả `intValue/longValue/floatValue/doubleValue` không?

**Kết luận:** `Number` là **lớp nền cho việc lấy biểu diễn primitive của một giá trị số**, không phải một giao diện toán học đầy đủ. Muốn tự xây dựng lớp con an toàn, bạn phải tự thiết kế miền dữ liệu, phép toán, kiểm tra mất mát, so sánh và quy tắc chuyển đổi. Với kiểu có đơn vị hoặc độ chính xác nghiệp vụ quan trọng, **composition và API tường minh** thường là lựa chọn dễ bảo vệ tính đúng đắn hơn.

Tài liệu API chính thức: [Java SE 17 — Number](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Number.html), [Java SE 17 — BigInteger](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigInteger.html), [Java SE 17 — BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html).


</details>

- [Quay lại đầu trang](#back-to-top)
