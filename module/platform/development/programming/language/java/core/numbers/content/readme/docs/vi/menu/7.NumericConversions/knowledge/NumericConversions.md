<a id="back-to-top"></a>

# Chuyển kiểu

## Menu
- [Widening và narrowing giữa các kiểu số](#numeric-conversions-001)
- [Nâng kiểu vẫn có thể mất độ chính xác khi chuyển sang float hoặc double](#numeric-conversions-002)
- [Thu hẹp số nguyên và các bit bị loại bỏ](#numeric-conversions-003)
- [Chuyển char với byte hoặc short](#numeric-conversions-004)
- [Ép float hoặc double sang số nguyên cắt về phía 0](#numeric-conversions-005)
- [Chuyển NaN, vô cực và số ngoài phạm vi sang kiểu nguyên](#numeric-conversions-006)
- [Thu hẹp double sang float và mất độ chính xác](#numeric-conversions-007)
- [Chuyển đổi qua Number.intValue, longValue và doubleValue](#numeric-conversions-008)
- [Phân biệt ép kiểu, làm tròn và kiểm tra chuyển đổi exact](#numeric-conversions-009)
- [Không có phép cast trực tiếp giữa kiểu số và boolean](#numeric-conversions-010)

## <a id="numeric-conversions-001">Widening và narrowing giữa các kiểu số</a>

<details>
<summary>Xem chi tiết</summary>

# Widening và narrowing giữa các kiểu số trong Java

**Chuyển kiểu số (*numeric conversion*)** là việc đổi một giá trị giữa các kiểu primitive số của Java: `byte`, `short`, `char`, `int`, `long`, `float`, `double`. Hai nhóm quan trọng:

- **Widening primitive conversion**: chuyển sang kiểu được Java cho phép mở rộng **tự động**, chẳng hạn `int` → `long` hoặc `int` → `double`.
- **Narrowing primitive conversion**: chuyển theo hướng mà Java yêu cầu **ép kiểu tường minh** trong phần lớn ngữ cảnh, chẳng hạn `long` → `int`, `double` → `int` hoặc `int` → `byte`.

Cần phân biệt ba câu hỏi độc lập: **chuyển đổi có được phép về cú pháp không**, **giá trị toán học có được giữ chính xác không**, và **phạm vi của kiểu đích có đủ chứa kết quả không**. Widening được phép tự động không có nghĩa mọi chữ số được giữ tuyệt đối; narrowing không chỉ đơn giản là “cắt phần thập phân” cho tất cả các cặp kiểu.

## 1. Các kiểu primitive số và giới hạn

| Kiểu | Độ rộng | Miền giá trị hoặc ý nghĩa |
| --- | ---: | --- |
| `byte` | 8 bit | -128 đến 127 |
| `short` | 16 bit | -32.768 đến 32.767 |
| `char` | 16 bit | 0 đến 65.535 (mã đơn vị UTF-16 không dấu) |
| `int` | 32 bit | -2.147.483.648 đến 2.147.483.647 |
| `long` | 64 bit | -9.223.372.036.854.775.808 đến 9.223.372.036.854.775.807 |
| `float` | 32 bit | Số thực dấu phẩy động IEEE 754, độ chính xác khoảng 6–9 chữ số thập phân có nghĩa |
| `double` | 64 bit | Số thực dấu phẩy động IEEE 754, độ chính xác khoảng 15–17 chữ số thập phân có nghĩa |

`char` là **kiểu nguyên không dấu** trong các phép chuyển đổi số Java, dù thường được dùng làm ký tự. `boolean` **không thuộc nhóm kiểu số**: không thể chuyển tự động hoặc ép kiểu primitive giữa `boolean` và `int`.

`float` và `double` có phạm vi độ lớn rất rộng nhưng không biểu diễn chính xác mọi số nguyên hoặc mọi phân số thập phân. Độ rộng bit của số thực gồm bit dấu, số mũ và phần trị (*significand*); không thể suy luận độ chính xác chỉ từ độ rộng 32/64 bit.

## 2. Các đường widening hợp lệ

Java cho phép widening primitive conversion sau:

| Kiểu nguồn | Các kiểu đích widening |
| --- | --- |
| `byte` | `short`, `int`, `long`, `float`, `double` |
| `short` | `int`, `long`, `float`, `double` |
| `char` | `int`, `long`, `float`, `double` |
| `int` | `long`, `float`, `double` |
| `long` | `float`, `double` |
| `float` | `double` |
| `double` | Không có kiểu primitive số nào rộng hơn |

Ví dụ:

```java
byte b = -12;
short s = b;      // byte -> short
int i = s;        // short -> int
long l = i;       // int -> long
float f = l;      // long -> float
double d = f;     // float -> double

char letter = 'A';
int codePointUnit = letter; // 65: char -> int
long codeAsLong = letter;   // char -> long
```

Cả hai nhánh đều **không cần ép kiểu**. Chú ý `char` không widening sang `short`, vì `short` mang dấu không chứa toàn bộ dải 0..65535 của `char`; `byte` cũng không widening sang `char` vì `byte` có giá trị âm.

```java
char c = 'A';
// short invalid = c; // lỗi: char -> short không widening
byte b = 65;
// char invalid2 = b; // lỗi: byte -> char không widening
```

Đây là các đoạn minh họa để đặt bên trong class, không phải chương trình độc lập. Các dòng chú thích sẽ gây lỗi biên dịch nếu bỏ dấu chú thích.

### Widening có thể mất độ chính xác

Các chuyển đổi sau **luôn giữ chính xác giá trị**:

- `byte` → `short`/`int`/`long`/`float`/`double`.
- `short` và `char` → `int`/`long`/`float`/`double`.
- `int` → `long` hoặc `double`.
- `float` → `double` giữ đúng giá trị số thực nhị phân đang chứa, kể cả `NaN` và vô cực.

Nhưng `int` → `float`, `long` → `float` và `long` → `double` **có thể làm tròn**. Cụ thể, `float` có 24 bit độ chính xác phần trị, `double` có 53 bit; các số nguyên liên tiếp quá lớn sẽ không còn biểu diễn tách biệt được.

```java
int preciseAsInt = 16_777_217;    // 2^24 + 1
float roundedAsFloat = preciseAsInt; // làm tròn thành 16777216.0f

long preciseAsLong = 9_007_199_254_740_993L; // 2^53 + 1
double roundedAsDouble = preciseAsLong; // làm tròn thành 9007199254740992.0
```

Cả hai phép gán trên là **widening hợp lệ**, không gây lỗi biên dịch, cũng không ném exception. Tuy nhiên dữ liệu mất một đơn vị vì kiểu đích không đủ độ chính xác. Khi so sánh ID, số tiền nguyên hoặc bộ đếm lớn, đây là lý do không nên vô tình chuyển `long` thành `double`.

## 3. Narrowing: khi nào phải viết phép ép kiểu?

Narrowing primitive conversion thường được ghi bằng toán tử cast `(TargetType) value`:

```java
long large = 1_000;
int number = (int) large;        // long -> int
double decimal = 42.75;
int truncated = (int) decimal;   // double -> int: 42
int value = 260;
byte small = (byte) value;       // int -> byte: 4
```

Nếu bỏ cast trong ngữ cảnh gán thông thường thì trình biên dịch từ chối:

```java
long large = 1_000;
// int wrong = large;    // lỗi: possible lossy conversion from long to int
double price = 42.75;
// int wrong2 = price;   // lỗi: double -> int cần cast
```

Cast **cho phép chuyển đổi theo quy tắc ngôn ngữ**, không xác nhận giá trị nằm trong miền kiểu đích. Java không tự phát hiện tràn khi cast số nguyên primitive; mã biên dịch được vẫn có thể mất thông tin.

Các ví dụ narrowing phổ biến gồm `int` → `byte`/`short`/`char`, `long` → `int`, `double` → `float`, và `float`/`double` → một kiểu nguyên.

## 4. Narrowing từ kiểu nguyên sang kiểu nguyên: giữ các bit thấp

Khi chuyển một số nguyên sang kiểu nguyên có độ rộng nhỏ hơn, Java loại bỏ các bit cao và giữ số bit thấp theo độ rộng của kiểu đích. Sau đó:

- `byte` và `short` được diễn giải theo bù hai có dấu.
- `char` là giá trị không dấu 16 bit từ 0 đến 65.535.

Ví dụ:

| Biểu thức | Kết quả | Giải thích |
| --- | ---: | --- |
| `(byte) 127` | 127 | Nằm trong miền byte |
| `(byte) 128` | -128 | 8 bit thấp là `0x80`, có bit dấu |
| `(byte) 255` | -1 | 8 bit thấp là `0xFF` |
| `(byte) 256` | 0 | 8 bit thấp đều bằng 0 |
| `(byte) 260` | 4 | 260 mod 256 bằng 4 |
| `(short) 65_535` | -1 | 16 bit thấp đều bằng 1 |
| `(char) -1` | 65.535 | 16 bit thấp là `0xFFFF` |
| `(int) Long.MAX_VALUE` | -1 | Giữ 32 bit thấp của `long` |

```java
int source = 260;
byte byteValue = (byte) source; // 4
short shortValue = (short) 65_535; // -1
char charValue = (char) -1; // mã char 65535
int lowerBits = (int) Long.MAX_VALUE; // -1
```

**Không dùng cast để kiểm tra miền giá trị.** Nếu cần từ chối mọi `long` không chứa vừa `int`, sử dụng `Math.toIntExact(longValue)`: method này ném `ArithmeticException` khi ngoài miền `int`.

```java
long valid = 123;
int exact = Math.toIntExact(valid); // 123

// Math.toIntExact(Long.MAX_VALUE) ném ArithmeticException
```

`Math.toIntExact` không có biến thể tương ứng cho mọi cặp kiểu nhỏ hơn: muốn từ chối khi thu hẹp sang `byte`/`short`/`char`, hãy kiểm tra phạm vi rõ ràng trước khi cast.

## 5. Narrowing từ số thực sang số nguyên: cắt phần lẻ, chặn biên

Đối với `float`/`double` → `int` hoặc `long`, Java dùng phép chuyển đổi được xác định theo các bước:

1. Nếu toán hạng là `NaN`, kết quả số nguyên là `0`.
2. Nếu giá trị hữu hạn nằm trong khoảng biểu diễn, **loại bỏ phần lẻ theo hướng về 0** (*truncate toward zero*), không làm tròn đến số nguyên gần nhất.
3. Nếu giá trị quá lớn hoặc quá nhỏ, kết quả **bị chặn ở biên** tương ứng (`MIN_VALUE` hoặc `MAX_VALUE`).
4. `+Infinity` và `-Infinity` lần lượt chuyển thành biên dương và biên âm.

Ví dụ:

```java
int positive = (int) 12.99;          // 12
int negative = (int) -12.99;         // -12
int nan = (int) Double.NaN;         // 0
int positiveInf = (int) Double.POSITIVE_INFINITY; // 2147483647
int negativeInf = (int) Double.NEGATIVE_INFINITY; // -2147483648
long huge = (long) 1e100;           // Long.MAX_VALUE
```

**Truncation không giống `Math.floor`**: `(int)-12.99` bằng `-12`, còn `Math.floor(-12.99)` bằng `-13.0`.

### Đích nhỏ hơn `int`: có bước cắt bit tiếp theo

Với `float`/`double` → `byte`, `short` hoặc `char`, Java **chuyển số thực sang `int` trước**, rồi narrowing tiếp từ `int` sang kiểu nguyên nhỏ:

```java
byte a = (byte) 257.8;          // 1: 257 -> byte
byte b = (byte) Double.NaN;     // 0
char c = (char) -1.9;           // 65535: -1 -> char
byte saturatedThenNarrowed = (byte) 1e100; // -1: int MAX -> 8 bit thấp
```

Vì vậy “chặn ở biên” khi số thực vượt miền `int` **không đồng nghĩa** khi ép tiếp xuống `byte` thì nó sẽ bị chặn ở 127 hay -128. Việc cắt bit vẫn xảy ra sau bước chuyển sang `int`.

Nếu cần làm tròn số thực theo một chính sách xác định, việc cast chỉ là một lựa chọn rất cụ thể. Chẳng hạn `Math.round` có quy tắc riêng và `BigDecimal` có các chế độ làm tròn khác; không thay thế lẫn nhau một cách máy móc.

## 6. Narrowing giữa `double` và `float`

`double` → `float` cần cast trong ngữ cảnh gán:

```java
double d = 1.25;
float f = (float) d;   // 1.25f, biểu diễn chính xác ở ví dụ này
```

Trong quá trình thu hẹp, một giá trị hữu hạn có thể:

- **Bị làm tròn** vì độ chính xác 24 bit của `float` thấp hơn 53 bit của `double`.
- Trở thành `Float.POSITIVE_INFINITY` hoặc `Float.NEGATIVE_INFINITY` nếu độ lớn vượt giá trị `float` hữu hạn tối đa.
- Trở thành `+0.0f` hoặc `-0.0f` (hay số *subnormal*) nếu độ lớn quá nhỏ.
- Giữ `NaN` hoặc vô cực theo bản chất giá trị nguồn.

```java
double large = 1e100;
float infinity = (float) large; // Infinity

double tiny = 1e-100;
float underflow = (float) tiny; // +0.0f
```

Widening ngược lại `float` → `double` là tự động và giữ chính xác giá trị `float` hiện có, **không thể khôi phục** những chữ số đã mất khi tạo `float`.

Ví dụ `double widenedBack = (double) ((float) 0.1);` không bằng literal `double` `0.1`: giá trị ban đầu đã được làm tròn ở bước chuyển xuống `float` rồi mới được mở rộng trở lại.

## 7. Ngoại lệ: gán biểu thức hằng `int` vào `byte`, `short` và `char`

Mặc dù `int` → `byte`/`short`/`char` là narrowing, Java cho phép **narrowing conversion trong ngữ cảnh gán** với biểu thức hằng tại thời điểm biên dịch thỏa điều kiện.

```java
byte a = 100;      // hợp lệ: int literal 100 nằm trong byte
short b = 30_000;  // hợp lệ: int literal nằm trong short
char c = 65;       // hợp lệ: int literal nằm trong char

final int LIMIT = 120; // constant variable: initializer là constant expression
byte value = LIMIT;    // hợp lệ vì giá trị vừa byte

// byte invalid = 128;    // lỗi: vượt byte
// short invalid2 = 40000; // lỗi: vượt short
// char invalid3 = -1;    // lỗi: char không âm
```

**Giá trị có vẻ nhỏ tại runtime chưa đủ**; với biến thường có kiểu `int`, compiler không tự narrowing:

```java
int count = 100;
// byte no = count; // lỗi: count là biến int thông thường
byte yes = (byte) count;
```

Trường hợp hằng là quy tắc ngôn ngữ áp dụng theo ngữ cảnh cụ thể; nó **không thay thế** cast ở các tình huống nói chung. `long` literal như `100L` cũng không tự gán thành `byte` chỉ vì giá trị 100 vừa phạm vi.

## 8. Phép toán số học có thể làm đổi kiểu trước khi gán

Java áp dụng **binary numeric promotion** với các toán tử số học thông thường. `byte`, `short` và `char` thường được nâng lên `int` trong phép cộng, trừ, nhân. Nếu có `long` và không có toán hạng số thực, biểu thức được tính bằng `long`; nếu có `float` hoặc `double` thì quy tắc nâng kiểu tương ứng áp dụng.

```java
byte left = 10;
byte right = 20;
// byte sum = left + right; // lỗi: left + right có kiểu int
int sum = left + right;   // 30
byte smallSum = (byte) (left + right); // 30, nhưng có thể mất dữ liệu ở giá trị khác
```

Hệ quả nguy hiểm: cast **sau khi** phép toán đã tràn thì không khôi phục được kết quả toán học:

```java
int quantity = 100_000;
long wrong = (long) (quantity * quantity); // int tràn -> 1410065408L
long correct = (long) quantity * quantity; // nhân kiểu long -> 10000000000L
```

Dù `wrong` có kiểu `long`, phép nhân bên trong dấu ngoặc vẫn là `int`. Cast một toán hạng **trước phép nhân** làm thay đổi kiểu tính toán, vì toán hạng `long` buộc toán hạng `int` còn lại được nâng lên `long`.

Một trường hợp khác với `float`:

```java
long veryLarge = 9_007_199_254_740_993L;
double rounded = veryLarge;      // làm tròn
long convertedBack = (long) rounded; // 9007199254740992L
```

Giá trị đã mất khi widening không thể lấy lại bằng narrowing ngược.

### Compound assignment có thể narrowing ngầm

`+=`, `-=`, `*=` có ngữ nghĩa tương đương về kiểu với phép toán rồi chuyển kết quả trở về kiểu biến bên trái (với lưu ý vế trái chỉ được đánh giá một lần):

```java
byte b = 127;
b += 1;     // hợp lệ, nhưng b thành -128 do thu hẹp sau phép cộng
// b = b + 1; // lỗi: b + 1 có kiểu int, không tự gán về byte
```

Dạng `b++` cũng cập nhật giá trị về kiểu của biến và có thể tràn; việc thấy cú pháp ngắn không có nghĩa được bảo đảm không mất dữ liệu.

## 9. Boxing, unboxing và chuyển đổi primitive: đừng đánh đồng

Kiểu wrapper như `Integer`, `Long` và `Double` là **kiểu tham chiếu**, khác các kiểu primitive `int`, `long` và `double`. Java có boxing/unboxing ở một số ngữ cảnh, nhưng không phải mọi cặp wrapper đều tự chuyển cho nhau.

```java
Integer boxed = 42;
long widenedAfterUnboxing = boxed; // Integer -> int (unbox) -> long
Long boxedLong = 42L;              // long -> Long (boxing)

// Long invalid = boxed;          // không thể tự đổi Integer -> Long qua unbox/widen/box
Long explicit = boxed.longValue();  // lấy long rồi boxing
```

Unboxing `null` có thể ném `NullPointerException`:

```java
Integer missing = null;
// long n = missing; // biên dịch được, nhưng ném NullPointerException khi chạy
```

Các vấn đề như chuyển chuỗi `"42"` bằng `Integer.parseInt`, lưu tiền dưới dạng `BigDecimal` hoặc biểu diễn số nguyên không giới hạn bằng `BigInteger` là những cơ chế khác, không phải quy tắc widening/narrowing primitive.

## 10. Chương trình Java 17+ chạy được và đầu ra được kiểm chứng

Tạo tệp **`PrimitiveConversionsOverviewDemo.java`**:

```java
public class PrimitiveConversionsOverviewDemo {
    public static void main(String[] args) {
        byte b = -10;
        long widened = b;
        System.out.println("widenByteToLong=" + widened);

        char letter = 'A';
        int charCode = letter;
        System.out.println("widenCharToInt=" + charCode);

        int intBeyondFloatPrecision = 16_777_217;
        float roundedFloat = intBeyondFloatPrecision;
        System.out.println("floatRoundedToInt=" + (int) roundedFloat);

        long longBeyondDoublePrecision = 9_007_199_254_740_993L;
        double roundedDouble = longBeyondDoublePrecision;
        System.out.println("doubleRoundedToLong=" + (long) roundedDouble);

        System.out.println("narrowIntToByte=" + (byte) 257);
        System.out.println("narrowNegativeToChar=" + (int) (char) -1);
        System.out.println("floatToIntTrunc=" + (int) 9.99);
        System.out.println("negativeFloatToIntTrunc=" + (int) -9.99);
        System.out.println("positiveInfinityToInt=" +
                (int) Double.POSITIVE_INFINITY);
        System.out.println("nanToInt=" + (int) Double.NaN);
        System.out.println("longToInt=" + (int) Long.MAX_VALUE);
        System.out.println("doubleToLongClamped=" + (long) 1e100);

        byte compound = 127;
        compound += 1;
        System.out.println("compoundByte=" + compound);

        int quantity = 100_000;
        long earlyWide = (long) quantity * quantity;
        long lateWide = (long) (quantity * quantity);
        System.out.println("earlyWide=" + earlyWide);
        System.out.println("lateWide=" + lateWide);

        byte constantByte = 127;
        System.out.println("constByte=" + constantByte);

        long roundTrip = (long) roundedDouble;
        System.out.println("roundTripLoss=" +
                (roundTrip == longBeyondDoublePrecision));
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac PrimitiveConversionsOverviewDemo.java
java PrimitiveConversionsOverviewDemo
```

Đầu ra dự kiến:

```text
widenByteToLong=-10
widenCharToInt=65
floatRoundedToInt=16777216
doubleRoundedToLong=9007199254740992
narrowIntToByte=1
narrowNegativeToChar=65535
floatToIntTrunc=9
negativeFloatToIntTrunc=-9
positiveInfinityToInt=2147483647
nanToInt=0
longToInt=-1
doubleToLongClamped=9223372036854775807
compoundByte=-128
earlyWide=10000000000
lateWide=1410065408
constByte=127
roundTripLoss=false
```

Đặc biệt chú ý `earlyWide` khác `lateWide`: vị trí cast làm thay đổi kiểu **khi thực hiện phép nhân**. Hai dòng về số thực minh họa việc widening có thể làm tròn số nguyên lớn; phép cast ngược chỉ đọc giá trị đã làm tròn.

## 11. Chọn cách chuyển đổi theo yêu cầu dữ liệu

| Nhu cầu | Cách làm phù hợp | Cần lưu ý |
| --- | --- | --- |
| Đổi `int` sang `long` | `long n = intValue;` | Giá trị được giữ chính xác |
| Đổi `long` sang `int` mà không cho mất dữ liệu | `Math.toIntExact(longValue)` | Ném `ArithmeticException` nếu ngoài miền |
| Đổi `int` sang `byte` và chấp nhận bit thấp | `(byte) intValue` | Có thể ra số âm do bù hai |
| Đổi `int` sang `byte` nhưng phải nằm trong -128..127 | Kiểm tra `Byte.MIN_VALUE <= n && n <= Byte.MAX_VALUE` trước khi cast | Tự xử lý khi ngoài miền |
| Xử lý `double` sang `int` | `(int) doubleValue` nếu đúng chính sách cắt về 0 | `NaN` thành 0; vô cực/ngoài miền bị chặn |
| Lưu số nguyên `long` cực lớn chính xác | Giữ `long` hoặc dùng `BigInteger` | Chuyển qua `double` có thể mất đơn vị |
| Tính tích hai `int` có thể vượt 32 bit | `(long) a * b` | Cast xảy ra trước phép nhân |
| Muốn phép tính tràn phải báo lỗi | `Math.addExact`, `subtractExact`, `multiplyExact` | Phải chọn overload và bảo vệ ngay bước tính |

### Checklist tránh lỗi chuyển kiểu

1. Xác định **kiểu nguồn và kiểu đích**; tra đường widening để biết có cần cast không.
2. Kiểm tra **độ chính xác**, đặc biệt `int/long` sang `float/double`; widening chưa chắc chính xác tuyệt đối.
3. Với narrowing nguyên → nguyên, xem **bit bị cắt**; với số thực → nguyên, xem quy tắc **cắt phần lẻ, `NaN` và chặn biên**.
4. Phân biệt **hằng biểu thức** với biến thường khi gán vào `byte`/`short`/`char`.
5. Nếu có phép toán trung gian, xác định **kiểu được dùng khi tính**, rồi mới xét kiểu biến đích; cast phải đúng vị trí.
6. Nếu không được phép mất dữ liệu, tự kiểm tra miền giá trị hoặc dùng API kiểm tra chính xác thay vì cast mù quáng.

**Kết luận:** *Widening* chủ yếu là sự cho phép chuyển kiểu tự động theo hệ thống kiểu Java; *narrowing* thường phải được yêu cầu tường minh vì có nguy cơ mất thông tin. Mọi lựa chọn chuyển kiểu nên dựa trên **miền giá trị, độ chính xác và mục đích phép tính**, không chỉ dựa vào việc dòng code có biên dịch được hay không.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-conversions-002">Nâng kiểu vẫn có thể mất độ chính xác khi chuyển sang float hoặc double</a>

<details>
<summary>Xem chi tiết</summary>

# Nâng kiểu vẫn có thể mất độ chính xác khi chuyển sang float hoặc double trong Java

Trong Java, **nâng kiểu nguyên thủy** (*widening primitive conversion*) không đồng nghĩa với **giữ nguyên giá trị toán học trong mọi trường hợp**. Khi chuyển một số nguyên sang `float` hoặc `double`, kiểu đích có thể có phạm vi giá trị rộng hơn nhưng **không có đủ bit phần trị để biểu diễn chính xác từng số nguyên** trong phạm vi đó.

Hai ví dụ quan trọng:

```java
int integer = 16_777_217;
float approximate = integer;  // Giá trị float thực tế: 16_777_216.0f

long large = 9_007_199_254_740_993L;
double rounded = large;       // Giá trị double thực tế: 9_007_199_254_740_992.0
```

Đây là các mảnh mã hợp lệ bên trong phương thức Java. Không có lỗi biên dịch hoặc `ArithmeticException`: chuyển kiểu được phép, nhưng kết quả **đã bị làm tròn**. Nếu các số biểu diễn số đếm, mã định danh, phiên bản dữ liệu hoặc số tiền ở đơn vị nhỏ nhất, việc mất một đơn vị cũng có thể làm sai nghiệp vụ.

Bài học thuộc phần **Chuyển kiểu** của module **Java core – numbers**, tập trung vào lý do, điều kiện xảy ra, các bẫy khi so sánh và cách bảo toàn dữ liệu khi đổi sang kiểu dấu phẩy động.

## 1. “Nâng kiểu” và “chính xác” là hai thuộc tính khác nhau

Trong Java, chuyển kiểu nguyên thủy được gọi là *widening* nếu nằm trong nhóm chuyển đổi được ngôn ngữ cho phép mở rộng, ví dụ `int → long`, `int → float`, `int → double`, `long → float`, `long → double`, `float → double`. Đa số có thể diễn ra tự động khi gán, truyền tham số hoặc trong biểu thức số học.

Nhưng từ “widening” mô tả **quy tắc chuyển đổi và phạm vi giá trị được hỗ trợ**, không bảo đảm tăng số bit lưu **phần nguyên chính xác**.

| Chuyển đổi | Có giữ chính xác mọi giá trị nguồn? | Giải thích |
| --- | --- | --- |
| `byte → short/int/long` | Có | Số nguyên đích biểu diễn được toàn bộ miền nguồn |
| `short → int/long` | Có | Bảo toàn mọi giá trị |
| `char → int/long` | Có | Mọi giá trị `char` 0–65535 đều biểu diễn được |
| `int → long` | Có | 64 bit nguyên chứa đủ mọi `int` |
| `int → float` | **Không** | `float` chỉ có 24 bit độ chính xác phần trị |
| `int → double` | **Có** | `double` có 53 bit độ chính xác phần trị, đủ cho mọi `int` |
| `long → float` | **Không** | Nhiều `long` phải làm tròn khi vào `float` |
| `long → double` | **Không** | Không đủ 53 bit để biểu diễn mọi số nguyên 64 bit |
| `float → double` | Có, đối với giá trị hữu hạn đã lưu trong `float` | Giá trị nhị phân của `float` được biểu diễn chính xác trong `double`; **không phục hồi** thông tin đã mất trước đó |

Cần phân biệt **độ lớn cực đại** với **độ phân giải**. `float` có thể biểu diễn số có độ lớn vượt xa `Long.MAX_VALUE`, nhưng khi số lớn, các giá trị biểu diễn được cách nhau rất xa. Kiểu có dải giá trị lớn hơn **không nhất thiết** chứa được mọi số nguyên nhỏ hơn ngưỡng lớn nhất ấy.

## 2. Cơ chế: số dấu phẩy động lưu phần trị và số mũ

`float` và `double` trong Java sử dụng biểu diễn dấu phẩy động nhị phân IEEE 754:

- `float`: 32 bit tổng cộng, **24 bit độ chính xác phần trị** (bao gồm bit ẩn ở các số chuẩn hóa).
- `double`: 64 bit tổng cộng, **53 bit độ chính xác phần trị**.
- Phần bit còn lại đảm nhiệm dấu và số mũ; chúng giúp mở rộng **phạm vi độ lớn**, nhưng không làm cho mọi số nguyên trong phạm vi đó đều có biểu diễn chính xác.

Một cách hình dung: khi còn đủ bit phần trị, hai số nguyên liên tiếp như 100 và 101 có thể có hai biểu diễn riêng. Khi tăng tới giá trị lớn, khoảng cách giữa hai số dấu phẩy động liền kề tăng lên 2, 4, 8… đơn vị. Những số nguyên nằm giữa phải được làm tròn đến một giá trị lân cận.

### Mốc 2^24 của float

```text
2^24 = 16_777_216

16_777_216 → float: chính xác
16_777_217 → float: 16_777_216
16_777_218 → float: chính xác
16_777_219 → float: 16_777_220 (làm tròn tới giá trị gần nhất)
```

Ở miền quanh `2^24`, các giá trị `float` liên tiếp cách nhau **2**. `16_777_217` nằm giữa `16_777_216` và `16_777_218`; quy tắc làm tròn đến gần nhất, hòa chọn chữ số cuối chẵn (*round to nearest, ties to even*) chọn `16_777_216` trong trường hợp này.

**Mọi số nguyên từ `-2^24` đến `2^24` đều được biểu diễn chính xác bằng `float`**, nhưng điều đó **không** có nghĩa mọi số nguyên bên ngoài miền đều sai: ví dụ `16_777_218` vẫn biểu diễn chính xác. Mốc này là ngưỡng **bảo đảm liên tục**, không phải ngưỡng “mọi giá trị lớn hơn đều không thể lưu”.

### Mốc 2^53 của double

```text
2^53 = 9_007_199_254_740_992

9_007_199_254_740_992 → double: chính xác
9_007_199_254_740_993 → double: 9_007_199_254_740_992
9_007_199_254_740_994 → double: chính xác
```

Tương tự, mọi số nguyên trong `[-2^53, 2^53]` đều biểu diễn chính xác bằng `double`, nhưng các số nguyên liền kề sau ngưỡng này không còn luôn có biểu diễn riêng. Vì `long` chứa những số nguyên lớn hơn `2^53`, chuyển `long → double` có thể đánh mất chữ số thấp.

Ngưỡng `Number.MAX_SAFE_INTEGER` trong JavaScript là `2^53 - 1`, có liên hệ với khả năng phân biệt liên tiếp của binary64. Điều đó đặc biệt đáng quan tâm nếu dịch vụ Java xuất `long` qua JSON number cho JavaScript.

## 3. Vì sao int → double vẫn chính xác, còn int → float có thể không?

Mọi `int` Java nằm trong miền:

```text
-2_147_483_648 ≤ int ≤ 2_147_483_647
```

Một `double` có 53 bit phần trị, nên biểu diễn được mọi số nguyên 32 bit. Vì vậy:

```java
int source = Integer.MAX_VALUE;
double widened = source;

boolean exact = (long) widened == source; // true
```

Trong ví dụ này có thể cast kết quả về `long` để thấy giá trị nguyên, nhưng chỉ riêng tính chất “mọi `int` đều nằm trong độ chính xác của `double`” đã đủ để kết luận chuyển đổi chính xác.

Với `float`, dù kiểu này có phạm vi độ lớn rất rộng, 24 bit phần trị không đủ chứa mọi `int`. Ví dụ:

```java
int source = 16_777_217;
float widened = source;

int recovered = (int) widened;   // 16_777_216
boolean equal = recovered == source; // false
```

**Thông điệp quan trọng:** số bit tổng cộng của kiểu đích không trực tiếp cho biết số chữ số nguyên được giữ. `double` là 64 bit, `long` cũng 64 bit, nhưng `double` dành bit cho số mũ và dấu nên chỉ có 53 bit độ chính xác phần trị.

## 4. Java làm gì khi một số nguyên không biểu diễn chính xác được?

Việc chuyển `int` hoặc `long` sang `float`/`double` là chuyển đổi được phép và được làm tròn đến giá trị dấu phẩy động biểu diễn được gần nhất theo quy tắc thích hợp của Java. Với các ca đúng giữa hai số lân cận, quy tắc ties to even quyết định kết quả.

```java
int first = 16_777_216;
int second = 16_777_217;

float f1 = first;
float f2 = second;

// Hai số nguyên khác nhau tạo cùng một giá trị float:
boolean collision = f1 == f2; // true
```

Điểm này giải thích vì sao một bộ đếm nguyên **không nên được lưu bằng `float`** chỉ vì `float` có thể chứa số rất lớn. Giá trị đích có thể không thay đổi sau khi cộng 1 ở miền lớn:

```java
float count = 16_777_216f;
count = count + 1f;
// count vẫn là 16_777_216f.
```

Điều này không phải vì phép cộng bị bỏ qua, mà vì kết quả toán học `16_777_217` không biểu diễn chính xác được và bị làm tròn trở lại `16_777_216`.

Các chuyển đổi này không tự phát sinh `ArithmeticException`, và không có cảnh báo bắt buộc từ compiler. Vì vậy cần phân tích đầu vào, chọn kiểu và kiểm thử các giá trị biên thay vì dựa vào việc biên dịch thành công.

## 5. Bẫy khi so sánh: int == float hoặc long == double có thể vẫn true

Một lỗi đặc biệt khó thấy là đem số nguyên gốc so trực tiếp với số thực đã bị làm tròn:

```java
int original = 16_777_217;
float converted = original;

System.out.println(original == converted);         // true
System.out.println(original == (int) converted);   // false
```

Tại sao phép so sánh đầu tiên trả `true`?

Trong biểu thức `original == converted`, Java áp dụng **binary numeric promotion**: toán hạng `int` được chuyển thành `float` để so với `float`. Trong lúc chuyển, `16_777_217` cũng bị làm tròn thành `16_777_216f`. Hai toán hạng lúc so sánh bằng nhau, nhưng **điều đó không chứng minh `float` giữ được số nguyên gốc**.

Tương tự:

```java
long original = 9_007_199_254_740_993L;
double converted = original;

System.out.println(original == converted);        // true
System.out.println(original == (long) converted); // false
```

Không dùng `original == (float) original` hoặc `original == (double) original` làm phép kiểm tra chính xác. Với so sánh hỗn hợp nguyên/dấu phẩy động, chính toán hạng nguyên có thể bị chuyển đổi mất thông tin trước phép so sánh.

### Hai số nguyên khác nhau có thể “bằng nhau” sau chuyển kiểu

```java
long a = 9_007_199_254_740_992L;
long b = 9_007_199_254_740_993L;

boolean integerDifferent = a != b; // true
boolean doubleSame = (double) a == (double) b; // true
```

Nếu một hệ thống chuyển các ID lớn sang `double` rồi dùng chúng làm khóa so sánh, tra cứu hay deduplication, hai định danh khác nhau có thể trở thành một. Hệ quả có thể nặng hơn sai số trong phép đo thông thường.

## 6. Bẫy cast ngược: kiểm tra round-trip cần hiểu quy tắc

Một cách kiểm tra có vẻ hợp lý:

```java
double converted = (double) originalLong;
boolean unchanged = ((long) converted == originalLong);
```

Với `originalLong = 9_007_199_254_740_993L`, phép này trả `false` và phát hiện được mất 1 đơn vị. Tuy nhiên **không nên coi đây là kiểm tra đúng cho mọi `long`**.

Ví dụ:

```java
long originalLong = Long.MAX_VALUE;
double converted = originalLong;

boolean appearsEqual = ((long) converted == originalLong); // true
```

Phân tích:

- `Long.MAX_VALUE` là `9223372036854775807`.
- Giá trị `double` gần nhất khi chuyển có giá trị toán học `9223372036854775808` (`2^63`), **lớn hơn** `Long.MAX_VALUE`.
- Khi chuyển **thu hẹp** từ `double` về `long`, Java cắt phần lẻ và xử lý giá trị nằm ngoài miền theo quy tắc thu hẹp, trong đó giá trị dương quá lớn bị chặn về `Long.MAX_VALUE`.
- Vì thế cast ngược có thể cho lại `Long.MAX_VALUE` dù `double` đã không còn bằng số nguyên gốc!

Phép kiểm tra kiểu `(long) d == original` có thể cho **dương tính giả** tại giới hạn. Bản thân phép cast từ `double` sang `long` không phải một phép chứng minh chính xác.

### Kiểm tra giá trị integer ↔ double bằng biểu diễn chính xác

Trong trường hợp cần biết một `long` cụ thể có chuyển sang `double` **mà không thay đổi giá trị toán học** hay không, có thể so sánh bằng kiểu thập phân chính xác:

```java
import java.math.BigDecimal;

// Phương thức đặt trong một class Java:
static double toDoubleExact(long original) {
    double converted = (double) original;
    BigDecimal fromLong = BigDecimal.valueOf(original);
    BigDecimal exactDoubleValue = new BigDecimal(converted);

    if (fromLong.compareTo(exactDoubleValue) != 0) {
        throw new ArithmeticException("Precision lost converting long to double");
    }
    return converted;
}
```

Ở đây, **`new BigDecimal(converted)` được sử dụng có chủ đích để lấy đúng giá trị nhị phân của `double`**, kể cả khi biểu diễn thập phân dài. Đây là tình huống khác hẳn việc tạo tiền từ `double`; với số tiền, nên tạo `BigDecimal` từ **chuỗi thập phân gốc** thay vì dựa vào một giá trị thực đã bị làm tròn.

`BigDecimal.valueOf(original)` giữ chính xác số nguyên `long`. So sánh hai giá trị thập phân chính xác sẽ phát hiện cả trường hợp `Long.MAX_VALUE` dù cast ngược về `long` cho kết quả có vẻ bằng nhau.

Nếu yêu cầu của ứng dụng là hỗ trợ toàn bộ miền `long` mà không mất thông tin, **không dùng `double` làm kiểu lưu trữ số nguyên đó**. Kiểm tra trên chỉ phù hợp ở nơi có chủ đích cho phép hoặc từ chối từng giá trị cụ thể.

## 7. float → double không phục hồi độ chính xác của đầu vào ban đầu

Đây là một phân biệt quan trọng:

```java
float decimalLike = 0.1f;
double promoted = decimalLike;

System.out.println(promoted); // 0.10000000149011612
```

Bản thân việc chuyển `float → double` **giữ chính xác giá trị nhị phân đã được lưu trong float**. Nhưng literal `0.1f` từ đầu đã không biểu diễn chính xác 1/10 trong hệ nhị phân; `double` chỉ biểu diễn chính xác hơn **giá trị float đã làm tròn**, không biến nó thành 0.1 thập phân toán học.

Tương tự, nếu trước đó một số nguyên đã được lưu qua `float` rồi mới chuyển sang `double`:

```java
int original = 16_777_217;
float intermediate = original;
double later = intermediate;

// later mang giá trị 16_777_216.0; không còn biết số gốc là 16_777_217.
```

Một khi nhiều đầu vào khác nhau đã bị làm tròn thành cùng một giá trị `float`, bước widening tiếp theo **không có thông tin để khôi phục**.

## 8. ULP: khoảng cách giữa các số thực lân cận

`Math.ulp(value)` (*unit in the last place*) cho biết độ lớn một đơn vị ở vị trí bit phần trị thấp nhất quanh một giá trị dấu phẩy động. Nó giúp quan sát **độ phân giải** thay đổi theo độ lớn.

```java
System.out.println(Math.ulp(16_777_216f));          // 2.0
System.out.println(Math.ulp(9_007_199_254_740_992d)); // 2.0
```

Tại những vị trí này, khoảng cách tới số thực biểu diễn kế tiếp về phía lớn hơn là 2 đơn vị. Khi độ lớn tiếp tục tăng, ULP sẽ tăng. Điều đó khiến `float`/`double` vẫn chứa được số lớn nhưng không còn phân biệt được từng đơn vị nhỏ như trước.

Lưu ý ULP là thông tin về độ phân giải, **không tự cung cấp một tiêu chí sai số nghiệp vụ**. Trong bài toán đo lường, sai số cần xét đơn vị và yêu cầu thuật toán. Trong bài toán số đếm hoặc định danh, mất dù chỉ 1 đơn vị có thể không được phép.

## 9. Chương trình Java 17+ chạy độc lập

Tạo file **`WideningPrecisionDemo.java`**. Chương trình kiểm tra những bẫy quan trọng: chuyển `int → float`, `long → double`, so sánh hỗn hợp kiểu, ULP, chuyển `float → double`, cast ngược ở `Long.MAX_VALUE` và hàm chuyển `long` sang `double` có kiểm tra chính xác.

```java
import java.math.BigDecimal;

public class WideningPrecisionDemo {
    static double toDoubleExact(long original) {
        double converted = (double) original;
        BigDecimal fromLong = BigDecimal.valueOf(original);
        BigDecimal exactDoubleValue = new BigDecimal(converted);

        if (fromLong.compareTo(exactDoubleValue) != 0) {
            throw new ArithmeticException("Precision lost");
        }
        return converted;
    }

    public static void main(String[] args) {
        int intSource = 16_777_217;
        float asFloat = intSource;
        System.out.println("int-original=" + intSource);
        System.out.println("float-as-int=" + (int) asFloat);
        System.out.println("int-float-round-trip=" +
                ((int) asFloat == intSource));
        System.out.println("int-equals-float=" + (intSource == asFloat));
        System.out.println("int-to-double-exact=" +
                ((long) (double) Integer.MAX_VALUE == Integer.MAX_VALUE));
        System.out.println("float-ulp-at-2pow24=" +
                Math.ulp(16_777_216f));

        long longSource = 9_007_199_254_740_993L;
        double asDouble = longSource;
        System.out.println("long-original=" + longSource);
        System.out.println("double-as-long=" + (long) asDouble);
        System.out.println("long-double-round-trip=" +
                ((long) asDouble == longSource));
        System.out.println("long-equals-double=" +
                (longSource == asDouble));
        System.out.println("double-ulp-at-2pow53=" +
                Math.ulp(9_007_199_254_740_992d));

        double promotedFloat = 0.1f;
        System.out.println("float-to-double=" + promotedFloat);

        double roundedMax = (double) Long.MAX_VALUE;
        System.out.println("long-max-cast-back-equal=" +
                ((long) roundedMax == Long.MAX_VALUE));
        System.out.println("long-max-double-exact-integer=" +
                new BigDecimal(roundedMax).toBigIntegerExact());

        System.out.println("exact-small-long=" + toDoubleExact(10L));
        try {
            toDoubleExact(longSource);
        } catch (ArithmeticException ex) {
            System.out.println("checked-widening=precision-lost");
        }
    }
}
```

Lệnh thực thi:

```bash
javac WideningPrecisionDemo.java
java WideningPrecisionDemo
```

Kết quả stdout:

```text
int-original=16777217
float-as-int=16777216
int-float-round-trip=false
int-equals-float=true
int-to-double-exact=true
float-ulp-at-2pow24=2.0
long-original=9007199254740993
double-as-long=9007199254740992
long-double-round-trip=false
long-equals-double=true
double-ulp-at-2pow53=2.0
float-to-double=0.10000000149011612
long-max-cast-back-equal=true
long-max-double-exact-integer=9223372036854775808
exact-small-long=10.0
checked-widening=precision-lost
```

Điểm đáng chú ý: hai dòng `int-equals-float=true` và `long-equals-double=true` **không chứng minh phép chuyển đổi chính xác**. Giá trị số nguyên ở vế so sánh cũng được nâng sang kiểu thực và bị làm tròn. Dòng `long-max-cast-back-equal=true` cũng không chứng minh chính xác vì cast thu hẹp từ `double` về `long` có cơ chế chặn tại giới hạn.

## 10. Cách chọn kiểu khi cần bảo toàn số nguyên

| Nhu cầu | Lựa chọn phù hợp | Rủi ro nếu dùng float/double |
| --- | --- | --- |
| Đếm sự kiện, kích thước tệp, tổng số byte | `int`/`long`, hoặc `BigInteger` nếu cần | Mất các đơn vị lẻ ở miền lớn |
| ID số lớn, khóa tra cứu, số tài khoản | Thường `String` nếu là mã; `long`/`BigInteger` nếu cần số học | Hai ID khác nhau có thể trở thành một |
| Số tiền theo cent nguyên | `long` với kiểm soát tràn; hoặc giải pháp decimal theo miền | Không chấp nhận mất 1 cent |
| Số đo thực nghiệm chấp nhận sai số | `double`, đôi khi `float` theo đặc tả | Sai số là đặc tính được tính vào mô hình |
| Tiền hoặc giá trị thập phân chính xác | `BigDecimal` từ dữ liệu thập phân gốc | `double` có sai số nhị phân |
| Phải gửi `long` lớn sang JavaScript | String trong JSON hoặc giao thức hỗ trợ số nguyên chính xác | JavaScript Number (binary64) không phân biệt mọi số nguyên lớn |
| Một API bắt buộc nhận `double` từ `long` | Kiểm tra chính xác hoặc quy định sai số cho phép | Widening xảy ra âm thầm và không exception |

Tránh suy luận rằng một kiểu có **phạm vi biểu diễn lớn** thì luôn tốt hơn để lưu mọi dữ liệu nguyên. Ngữ nghĩa dữ liệu và yêu cầu bảo toàn thông tin phải quyết định thiết kế.

## 11. Những phép chuyển đổi và lời gọi thường gây nhầm

### 11.1. Truyền tham số vào method nhận double

```java
static void record(double idOrMeasurement) {
    // ...
}

long id = 9_007_199_254_740_993L;
record(id); // Java tự chuyển long sang double, có thể mất độ chính xác.
```

Không cần cast tường minh nên lỗi có thể xuất hiện khi refactor chữ ký method hoặc kết nối thư viện. Nếu `id` là định danh cần chính xác, chữ ký nhận `double` là một vấn đề thiết kế, không chỉ là lỗi format dữ liệu.

### 11.2. Toán hạng hỗn hợp trong biểu thức

```java
long count = 9_007_199_254_740_993L;
double average = count / 1.0; // count được nâng sang double trước phép chia.
```

Đầu ra là `double`; sai số chuyển kiểu có thể xuất hiện **trước phép chia**. Đừng cho rằng phép toán có kết quả dạng số thực vẫn giữ đủ từng đơn vị của bộ đếm rất lớn.

### 11.3. Tính nguyên trước rồi mới chuyển sang double

```java
long count = Long.MAX_VALUE;
double result = count + 1L;
```

Trong đoạn này, `count + 1L` thực hiện phép cộng `long` **trước khi chuyển sang `double`**, đã bị tràn thành `Long.MIN_VALUE`. Đó là **hai lớp rủi ro khác nhau**: tràn trong phép tính nguyên và mất chính xác ở bước chuyển sang số thực. Nếu mục đích là số thực lớn, cần chuyển kiểu đúng thời điểm theo yêu cầu tính toán; nếu mục đích là số nguyên chính xác, cân nhắc `BigInteger`.

### 11.4. Tăng double ở miền lớn

```java
double count = 9_007_199_254_740_992d;
count = count + 1.0;
// count vẫn bằng 9_007_199_254_740_992d.
```

Một biến kiểu `double` có thể ngừng thay đổi khi cộng một lượng nhỏ hơn độ phân giải hiện tại. Đây là nguyên nhân khiến `float`/`double` không phù hợp làm bộ đếm nguyên không giới hạn miền.

### 11.5. BigDecimal không tự phục hồi giá trị đã mất

```java
long id = 9_007_199_254_740_993L;
double rounded = id;
BigDecimal fromRounded = BigDecimal.valueOf(rounded);
```

`fromRounded` giữ giá trị tương ứng biểu diễn chuỗi của `double` đã làm tròn, không thể tự khôi phục số nguyên `id`. Nếu cần thập phân chính xác từ số nguyên, tạo **`BigDecimal.valueOf(id)` trước khi chuyển qua `double`**.

## 12. Chiến lược kiểm tra và thử nghiệm

Trong thực tế, không nhất thiết mọi chuyển `long → double` đều sai: nhiều giá trị cụ thể biểu diễn chính xác. Điều quan trọng là biết **hợp đồng của hệ thống**.

- Nếu yêu cầu **mọi đầu vào phải chính xác**, chọn kiểu giữ được toàn bộ miền, như `long` hoặc `BigInteger`, và tránh chuyển qua `float`/`double`.
- Nếu đầu vào bị giới hạn trong một miền an toàn, ghi rõ và kiểm tra giới hạn tại biên. Ví dụ `|n| ≤ 2^24` bảo đảm chính xác khi chuyển nguyên sang `float`; `|n| ≤ 2^53` bảo đảm chính xác khi chuyển nguyên sang `double`. Đối với `Long.MIN_VALUE`, tránh tính `Math.abs(n)` để kiểm tra miền vì `abs` có ca biên riêng.
- Nếu chỉ một số giá trị cụ thể được chấp nhận, có thể thực hiện chuyển kiểu rồi so với biểu diễn **toán học chính xác** như ví dụ `toDoubleExact`.
- Nếu cho phép xấp xỉ, định nghĩa **mức sai số chấp nhận được** và cách so sánh phù hợp; một ngưỡng tuyệt đối dùng cho số nhỏ không nhất thiết có nghĩa với số rất lớn.

Các ca thử nên bao gồm:

| Đầu vào | Chuyển đổi | Điều cần xác nhận |
| --- | --- | --- |
| `16_777_216` | `int → float` | Chính xác tại `2^24` |
| `16_777_217` | `int → float` | Làm tròn xuống `16_777_216` |
| `16_777_218` | `int → float` | Vẫn chính xác dù vượt `2^24` |
| `Integer.MAX_VALUE` | `int → double` | Chính xác |
| `9_007_199_254_740_992L` | `long → double` | Chính xác tại `2^53` |
| `9_007_199_254_740_993L` | `long → double` | Mất 1 đơn vị |
| `Long.MAX_VALUE` | `long → double → long` | Có thể trông đúng do cast bão hòa dù double đã khác |
| `0.1f` | `float → double` | Giữ đúng giá trị float đã xấp xỉ, không thành 0.1 chính xác |
| Hai ID kề nhau quanh `2^53` | So sánh sau khi đổi sang `double` | Phát hiện khả năng va chạm hai ID |

Một bộ test chỉ dùng số nhỏ dễ bỏ qua lỗi vì các chuyển đổi vẫn chính xác ở miền đó. Cần cố ý kiểm thử **ngay hai phía của mốc 2^24 và 2^53** cùng các giá trị giới hạn `int`/`long`.

## 13. Câu hỏi tự luyện

1. Vì sao `int → double` giữ chính xác mọi giá trị, nhưng `long → double` thì không? Giải thích theo số bit phần trị chứ không chỉ theo tổng số bit.
2. Dự đoán `(float) 16_777_217` và `(float) 16_777_218`. Vì sao một số phải làm tròn còn số kia thì không?
3. Với `long a = 9_007_199_254_740_992L` và `long b = a + 1`, vì sao `a != b` nhưng `(double) a == (double) b`?
4. Dự đoán `int i = 16_777_217; float f = i; i == f`. Vì sao `true` không chứng minh việc chuyển `int → float` giữ nguyên giá trị?
5. Giải thích ví dụ `Long.MAX_VALUE` chuyển sang `double` rồi cast ngược lại `long`: tại sao phép so sánh có thể `true` mà vẫn mất thông tin?
6. Viết một hàm nhận `long` và chỉ cho phép trả `double` nếu giá trị toán học không thay đổi; thử `10`, `2^53`, `2^53 + 1` và `Long.MAX_VALUE`.
7. Một DTO có trường ID kiểu `long`, nhưng JavaScript client nhận bằng `Number`. Chọn định dạng truyền tải phù hợp để giữ mọi giá trị ID, kèm ví dụ JSON.
8. Vì sao `double d = 0.1f;` biểu diễn đúng giá trị float đã lưu nhưng không phải 0.1 toán học? Việc đổi sang `BigDecimal` sau đó có thể phục hồi ý định số thập phân gốc không?
9. Một thư viện nhận `double` làm đầu vào cho thống kê, còn nguồn dữ liệu là số đếm `long`. Bạn sẽ chấp nhận sai số, chia nhỏ tính toán hay thay đổi API? Hãy nêu các giả định và test cần có.

## 14. Ghi nhớ

- **Nâng kiểu không bảo đảm giữ chính xác giá trị:** `int → float` và `long → float/double` có thể làm tròn âm thầm.
- **`float` có 24 bit độ chính xác, `double` có 53 bit**, nên mọi số nguyên đến `2^24` hoặc `2^53` về độ lớn tương ứng được giữ đúng, nhưng ngoài ngưỡng đó không phải mọi số nguyên đều biểu diễn được.
- **`int → double` giữ chính xác mọi `int`**, còn `float → double` giữ chính xác **giá trị float hiện có** và không phục hồi dữ liệu đã bị làm tròn.
- **So sánh hỗn hợp kiểu có thể đánh lừa:** `int == float` hoặc `long == double` có thể đúng ngay cả khi đã mất độ chính xác.
- **Cast ngược từ double về long không phải kiểm tra chính xác tổng quát**, đặc biệt tại `Long.MAX_VALUE` do quy tắc chặn khi thu hẹp kiểu.
- Với số đếm, mã định danh, số tiền nguyên và dữ liệu cần chính xác, **giữ kiểu nguyên hoặc kiểu thập phân chính xác thích hợp**; chỉ chuyển sang dấu phẩy động khi bài toán thật sự chấp nhận điều đó.

Nên tách ba câu hỏi khi chọn kiểu: *giá trị có nằm trong phạm vi biểu diễn không*, *giá trị có được biểu diễn chính xác không*, và *quy tắc chuyển/so sánh có bảo toàn điều kiện nghiệp vụ không*. Một phép widening hợp lệ chỉ trả lời câu hỏi thứ nhất.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-conversions-003">Thu hẹp số nguyên và các bit bị loại bỏ</a>

<details>
<summary>Xem chi tiết</summary>

# Thu hẹp số nguyên và các bit bị loại bỏ trong Java

> **Java core – numbers · Chuyển kiểu**  
> Mục tiêu: xác định chính xác bit nào còn lại sau narrowing integer conversion, cách Java diễn giải lại bit có dấu và không dấu, và cách tránh mất dữ liệu trong chuyển kiểu, phép gán và xử lý nhị phân.

## 1. Bài toán: một giá trị lớn đi vào kiểu nhỏ

Trong Java, các kiểu nguyên có độ rộng và miền giá trị cố định:

| Kiểu | Độ rộng | Miền | Có dấu? |
| --- | ---: | --- | --- |
| `byte` | 8 bit | −128 đến 127 | Có |
| `short` | 16 bit | −32.768 đến 32.767 | Có |
| `char` | 16 bit | 0 đến 65.535 | Không |
| `int` | 32 bit | −2³¹ đến 2³¹−1 | Có |
| `long` | 64 bit | −2⁶³ đến 2⁶³−1 | Có |

*Thu hẹp số nguyên* (*narrowing primitive conversion*) là chuyển một giá trị nguyên sang kiểu đích không bảo đảm chứa được mọi giá trị của kiểu nguồn. Ví dụ:

```java
int source = 130;
byte result = (byte) source;
System.out.println(result); // -126
```

**Java không chọn giá trị gần nhất trong miền `byte`.** Khi cast từ nguyên rộng sang nguyên hẹp, nó loại bỏ bit cao và chỉ giữ **n bit thấp**, trong đó n là độ rộng của kiểu đích; sau đó đọc mẫu bit này theo quy tắc kiểu đích. Vì `130` có 8 bit thấp là `1000_0010`, mẫu ấy là `−126` khi diễn giải signed byte theo bù hai.

Narrowing integer conversion cũng không tự ném ngoại lệ khi không giữ được giá trị gốc. Cast là một lời yêu cầu chuyển kiểu theo quy tắc ngôn ngữ, **không phải lời hứa rằng giá trị còn nguyên**.

## 2. Cơ chế bit: cắt bên trái, giữ bên phải

Xét `int 130` (32 bit) sang `byte` (8 bit):

```text
int (32 bit): 00000000 00000000 00000000 10000010
             \________ 24 bit cao bị loại ______/ \__ 8 bit __/
                                                    10000010

Nếu đọc 8 bit KHÔNG dấu: 128 + 2 = 130
Nếu đọc dưới kiểu byte có dấu: 130 - 256 = -126
```

Với `int 255`:

```text
int:    00000000 00000000 00000000 11111111
byte:                              11111111
                                              -> -1 (signed byte)
```

Với `int 256`:

```text
int:    00000000 00000000 00000001 00000000
byte:                              00000000
                                              -> 0 (bit ở vị trí 8 đã bị loại)
```

Để dự đoán kết quả, có thể sử dụng phép toán học hai bước:

1. Tìm `U` là **phần dư không âm** của số nguồn khi chia cho `2^n`. Khi đó `U` nằm trong `0..2^n−1` và mô tả n bit thấp.
2. Nếu kiểu đích là `char`, kết quả bằng `U`. Nếu kiểu đích có dấu n bit, kết quả bằng `U` khi `U < 2^(n−1)`, hoặc bằng `U−2^n` khi `U ≥ 2^(n−1)`.

Với `(byte) 300`: `300 mod 256 = 44` nên kết quả là `44`. Với `(byte) -130`: phần dư **không âm** của −130 khi chia 256 là 126, nên kết quả `126`.

**Chú ý:** ký hiệu `mod` ở trên là phần dư toán học không âm. Toán tử `%` trong Java có thể trả phần dư âm nếu số bị chia âm; ví dụ `-130 % 256 == -130`. Muốn phần dư không âm với môđun dương có thể dùng `Math.floorMod(-130, 256)`.

## 3. Bảng mẫu: cast int sang byte

| Nguồn `int` | 8 bit thấp | `(byte)` | Diễn giải |
| ---: | --- | ---: | --- |
| 100 | `0110_0100` | 100 | Vẫn vừa byte |
| 127 | `0111_1111` | 127 | Biên dương |
| 128 | `1000_0000` | −128 | Bit dấu của byte bật |
| 129 | `1000_0001` | −127 | Giá trị bù hai |
| 130 | `1000_0010` | −126 | Giá trị bù hai |
| 255 | `1111_1111` | −1 | Tám bit đều là 1 |
| 256 | `0000_0000` | 0 | Bit thứ 8 đã mất |
| 257 | `0000_0001` | 1 | Chỉ còn bit 0 |
| −1 | `1111_1111` | −1 | Vẫn giữ giá trị |
| −128 | `1000_0000` | −128 | Biên âm |
| −129 | `0111_1111` | 127 | Quay vòng qua miền byte |

Ví dụ `(byte) 128` cho `−128`, còn `(byte) 255` cho `−1`. Không có logic **clamp** về −128/127. Những giá trị nguồn khác nhau cách nhau bội số 256 có cùng mẫu 8 bit thấp và sẽ cho cùng giá trị `byte`.

## 4. Thu hẹp long thành int

`long` có 64 bit, `int` có 32 bit. Khi cast `long → int`, Java loại 32 bit cao:

```java
long large = 0x1_0000_0001L; // 4294967297
int shortened = (int) large; // 1
```

```text
long: 00000001 00000001  (mỗi nhóm ở đây là 32 bit hex: 0x00000001_00000001)
                   ^^^^^^^^
                   32 bit thấp = 0x00000001 -> int 1
```

Để thấy trường hợp đổi dấu:

```java
long source = 0x00000001_FFFFFFFFL;
int shortened = (int) source;
System.out.println(shortened); // -1
```

Phần thấp `0xFFFFFFFF` của `long` được giữ nguyên, nhưng trong `int` có dấu đó là −1.

Khi rút gọn ID hoặc giá trị số lượng từ `long` sang `int`, việc mất 32 bit cao có thể làm nhiều giá trị khác nhau trở thành cùng một giá trị. Ví dụ `1L` và `0x1_0000_0001L` đều cast thành `int 1`. Chỉ dùng cách thu hẹp để trích bit nếu **đặc tả thực sự cần trích**; nếu phải bảo toàn giá trị, kiểm tra miền trước cast.

## 5. int sang short: giữ 16 bit, diễn giải có dấu

```java
System.out.println((short) 32767); // 32767
System.out.println((short) 32768); // -32768
System.out.println((short) 65535); // -1
System.out.println((short) 65536); // 0
```

```text
 32768 (0x00008000) -> giữ 0x8000 -> short -32768
 65535 (0x0000FFFF) -> giữ 0xFFFF -> short -1
 65536 (0x00010000) -> giữ 0x0000 -> short 0
```

Cách thu hẹp `long → short` có cùng đích là 16 bit thấp. Việc đi qua `int` trước rồi chuyển tiếp sang `short` (không xen phép toán làm đổi giá trị) cũng giữ cùng 16 bit thấp.

## 6. char: cùng 16 bit với short, nhưng không dấu

`char` là đơn vị mã UTF-16 không dấu, có giá trị số từ 0 đến 65.535. Narrowing thành `char` vẫn giữ 16 bit thấp, nhưng **không dùng bit cao nhất làm bit dấu**:

```java
char a = (char) 65;       // 'A'
char b = (char) 65535;    // giá trị số 65535
char c = (char) 65536;    // giá trị số 0
char d = (char) -1;       // giá trị số 65535
System.out.println((int) d); // 65535
```

Khi in một biến `char` trực tiếp, Java có thể in ra **ký tự**, không phải mã số. Muốn quan sát mã số, ép `char` lên `int` như trên.

So sánh hai kiểu 16 bit:

```java
char unit = '\uFFFF';
short signed = (short) unit;   // -1
char restored = (char) signed; // 65535
```

Bản thân 16 bit `1111_1111_1111_1111` không đổi. Nhưng `short` đọc là −1 còn `char` đọc là 65.535. Vì khác miền, chuyển giữa hai kiểu này yêu cầu cast dù số bit bằng nhau. Không nên coi `char` là kiểu số không dấu thay thế tùy tiện cho dữ liệu: một đơn vị UTF-16 không nhất thiết là một ký tự Unicode độc lập hoặc ký tự hiển thị được.

## 7. Chuyển theo chuỗi cast: vẫn giữ bit thấp khi không có phép toán xen giữa

```java
long number = 0x1234_5678_9ABC_DEF0L;

int low32 = (int) number;       // 0x9ABCDEF0
short low16 = (short) number;   // 0xDEF0
byte low8 = (byte) number;      // 0xF0
```

```text
long:   1234 5678 9ABC DEF0
int:              9ABC DEF0
short:                 DEF0
byte:                    F0
```

Có thể xác nhận `(byte) number`, `(byte)(int) number` và `(byte)(short) number` đều thành `−16`, vì 8 bit thấp luôn là `0xF0`. Đây chỉ là tương đương trong trường hợp các bước trung gian thuần túy là chuyển kiểu nguyên. Xen các phép số học, cast qua `float`/`double` hoặc dịch bit có thể tạo mẫu khác.

## 8. Tại sao một số phép gán không cần cast: constant expression

Narrowing từ biến `int` sang `byte` thường đòi cast:

```java
int value = 100;
// byte invalid = value; // lỗi: biến int không được thu hẹp ngầm
byte explicit = (byte) value;
```

Nhưng Java có ngoại lệ **chuyển đổi gán cho biểu thức hằng** (constant expression) kiểu `byte`, `short`, `char` hoặc `int` nếu giá trị nằm trong miền của kiểu đích thích hợp:

```java
byte a = 100;          // được: literal int 100 vừa byte
short b = 30000;       // được: hằng int vừa short
char c = 65;           // được: hằng int nằm trong miền char
byte total = 40 + 2;   // được: biểu thức hằng int bằng 42

final int FIXED = 120;
byte fromFixed = FIXED; // được: biến hằng final int

int ordinary = 120;
// byte wrong = ordinary; // không được

// byte tooLarge = 128;  // không được: ngoài miền
byte wrapped = (byte) 128; // được: -128
```

Nên phân biệt rõ **ngoại lệ khi gán hằng trong miền** với cast tường minh: compiler biết được giá trị hằng và cho phép gán nếu an toàn; cast tường minh ngoài miền vẫn giữ bit thấp và có thể mất giá trị. Quy tắc gán hằng này cũng **không đồng nghĩa** mọi lời gọi phương thức nhận `byte` đều có thể nhận literal `int` mà không cast.

## 9. Numeric promotion: toán học được thực hiện trước narrowing

Trong phép cộng, trừ, nhân, chia giữa các primitive nguyên, các toán hạng `byte`, `short` và `char` thường được nâng lên `int` trước (*binary numeric promotion*).

```java
byte x = 100;
byte y = 30;
int sum = x + y;           // 130, kiểu int
byte result = (byte) (x + y); // -126
// byte invalid = x + y;  // lỗi: tổng có kiểu int
```

Nếu phép tính chạy bằng `int` và **tràn trước khi cast**, cast sau đó không thể khôi phục thông tin đã mất:

```java
int a = 1_500_000_000;
long bad = a * 2;           // -1294967296L, int tràn trước
long good = (long) a * 2;  // 3000000000L, long trước phép nhân
```

Khi cần kiểm tra tràn trong `int`/`long`, dùng `Math.addExact`, `Math.multiplyExact` và các hàm exact liên quan; khi cần miền số nguyên lớn hơn `long`, chuyển toán hạng sang `BigInteger` **trước phép tính**.

Không lẫn quy tắc trên với cast từ số thực sang số nguyên. `(int) 3.9` có quy tắc bỏ phần lẻ, còn `(byte) 130` có quy tắc giữ bit thấp. Với số thực `NaN`, vô cực và ngoài miền còn có quy tắc riêng, nằm ngoài trọng tâm của bài này.

## 10. Compound assignment và ++/--: có narrowing ngầm

Phép gán kết hợp (*compound assignment*) như `+=` chứa chuyển đổi ngầm về kiểu vế trái:

```java
byte b = 127;
b += 1;
System.out.println(b); // -128
```

Nhưng phép gán thường lại khác:

```java
byte b = 127;
// b = b + 1; // lỗi: b + 1 là int
b = (byte) (b + 1); // -128
```

Về mặt khái niệm, `E1 op= E2` gần với `E1 = (T)(E1 op E2)`, nhưng có điểm thiết yếu: **E1 chỉ được đánh giá một lần**. Vì vậy không thay thế tùy tiện nếu vế trái có truy cập mảng, tăng chỉ số hoặc lời gọi phương thức.

Toán tử `++`/`--` trên `byte`, `short`, `char` cũng có thể quay vòng:

```java
byte b = 127;
b++;
System.out.println(b); // -128

char unit = '\uFFFF';
unit++;
System.out.println((int) unit); // 0
```

Nếu nghiệp vụ không chấp nhận quay vòng, hãy dùng kiểu đủ rộng và kiểm tra miền trước phép gán.

## 11. Mask và diễn giải byte không dấu

Trong Java, `byte` có dấu. Nhưng định dạng nhị phân/giao thức thường dùng một octet **không dấu** từ 0..255:

```java
byte raw = (byte) 0xFF;  // signed byte -1
int signed = raw;        // -1: sign extension khi nâng lên int
int unsigned = raw & 0xFF; // 255: giữ 8 bit thấp
int api = Byte.toUnsignedInt(raw); // 255
```

Điểm khác nhau:

- `byte → int` là **widening** giữ giá trị có dấu: bit dấu `1` được mở rộng vào 24 bit cao.
- `raw & 0xFF` là phép mask sau khi operand đã được nâng lên `int`: xóa các bit cao để giữ giá trị 0..255.

Khi ghép hai octet big-endian:

```java
byte high = (byte) 0xAB;
byte low = (byte) 0xCD;
int unsigned16 = ((high & 0xFF) << 8) | (low & 0xFF);
System.out.println(unsigned16); // 43981 (0xABCD)
```

Mask từng byte trước khi dịch/OR tránh sign extension làm sai các bit. Quy tắc thực tế: xác định **độ rộng trường, signedness và thứ tự byte** từ đặc tả giao thức; chỉ dùng cast xuống byte khi thật sự muốn lấy bit thấp, còn dùng kiểm tra miền khi cần giữ nguyên **giá trị số học**.

## 12. Phát hiện mất dữ liệu trước khi cast

### 12.1. Kiểm tra miền trước khi thu hẹp

```java
public static byte checkedByte(int value) {
    if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
        throw new ArithmeticException("Outside byte range");
    }
    return (byte) value;
}
```

Đây là một **phương thức minh họa**, đặt trong một lớp trước khi sử dụng. Với `short` dùng `Short.MIN_VALUE`/`Short.MAX_VALUE`. Với `char` dùng điều kiện `value >= Character.MIN_VALUE && value <= Character.MAX_VALUE`.

### 12.2. Math.toIntExact và BigInteger exact

```java
long large = 0x1_0000_0001L;
int truncated = (int) large; // 1
// Math.toIntExact(large) sẽ ném ArithmeticException
```

`Math.toIntExact(long)` là API có sẵn để yêu cầu chuyển sang `int` mà **không làm mất giá trị**. Với `BigInteger` có `byteValueExact()`, `shortValueExact()`, `intValueExact()`, `longValueExact()`. Ngược lại, `BigInteger.intValue()` và `longValue()` có thể bỏ bit cao mà không báo lỗi.

### 12.3. Kiểm tra cast bằng vòng ngược

```java
int original = 130;
byte narrowed = (byte) original;
boolean preserved = ((int) narrowed == original); // false
```

Đây là cách tự kiểm chứng sự mất mát, nhưng khi validate đầu vào thực tế, **kiểm tra miền trước cast** dễ đọc hơn và tránh nhầm lẫn khi chuyển qua `char` không dấu.

## 13. Chương trình Java 17+ chạy độc lập

Tạo tệp **`IntegerNarrowingDemo.java`**:

```java
public class IntegerNarrowingDemo {
    private static byte checkedByte(int value) {
        if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
            throw new ArithmeticException("byte range exceeded");
        }
        return (byte) value;
    }

    private static String bits8(int value) {
        String binary = Integer.toBinaryString(value & 0xFF);
        return String.format("%8s", binary).replace(' ', '0');
    }

    public static void main(String[] args) {
        int source = 130;
        byte narrowed = (byte) source;

        System.out.println("130 -> byte = " + narrowed);
        System.out.println("low 8 bits = " + bits8(source));
        System.out.println("255 -> byte = " + (byte) 255);
        System.out.println("256 -> byte = " + (byte) 256);
        System.out.println("-129 -> byte = " + (byte) -129);

        long wide = 0x1_0000_0001L;
        System.out.println("long -> int = " + (int) wide);
        System.out.println("32768 -> short = " + (short) 32768);
        System.out.println("-1 -> char code = " + (int) (char) -1);

        byte octet = (byte) 0xFF;
        System.out.println("signed byte = " + octet);
        System.out.println("unsigned octet = " + Byte.toUnsignedInt(octet));

        byte counter = 127;
        counter += 1;
        System.out.println("byte += 1 = " + counter);

        try {
            checkedByte(130);
        } catch (ArithmeticException e) {
            System.out.println("checked byte: rejected");
        }
        try {
            Math.toIntExact(wide);
        } catch (ArithmeticException e) {
            System.out.println("toIntExact: rejected");
        }
    }
}
```

Chạy trong thư mục chứa tệp:

```bash
javac IntegerNarrowingDemo.java
java IntegerNarrowingDemo
```

**Stdout chính xác:**

```text
130 -> byte = -126
low 8 bits = 10000010
255 -> byte = -1
256 -> byte = 0
-129 -> byte = 127
long -> int = 1
32768 -> short = -32768
-1 -> char code = 65535
signed byte = -1
unsigned octet = 255
byte += 1 = -128
checked byte: rejected
toIntExact: rejected
```

Đọc kết quả:

1. `130` thành `-126` vì `10000010` là signed byte −126.
2. `255` thành `−1`, `256` thành `0`, `−129` thành `127` vì cùng quy tắc modulo 256 ở mức bit.
3. `long 0x1_0000_0001L` mất 32 bit cao và trở thành `int 1`; cast sang `short`/`char` cũng giữ số bit thấp của kiểu đích.
4. Cùng octet `0xFF` được đọc signed byte −1 hoặc unsigned 255.
5. `+=` thu hẹp ngầm và quay vòng; các helper checked từ chối chuyển không bảo toàn giá trị.

## 14. Lỗi phổ biến

| Nhầm lẫn | Quy tắc đúng |
| --- | --- |
| `(byte) 130` sẽ chặn ở 127 | Giữ 8 bit thấp, trả −126 |
| Narrowing integer cast tự báo overflow | Cast không tự báo; phải kiểm tra biên |
| Thu hẹp nguyên là làm tròn số học | Nó loại bit cao và diễn giải bit đích |
| `short` và `char` đều 16 bit nên cùng giá trị | `short` có dấu, `char` không dấu |
| `(char) -1` vẫn bằng −1 | Giá trị là 65535 |
| `byte` âm nâng lên `int` sẽ thành 0..255 | Nâng kiểu giữ dấu; mask nếu cần unsigned |
| `byte + byte` vẫn là `byte` | Toán tử số học nhị phân promote thành `int` |
| `byte b = 100;` chứng minh mọi gán int→byte được | Đó là ngoại lệ cho hằng vừa miền |
| `byte += 1` kiểm tra tràn | Compound assignment thu hẹp ngầm |
| Cast sau phép nhân khôi phục tràn trước đó | Thông tin đã mất không tự quay lại |
| Mask và cast luôn bằng nhau về giá trị | Mask phục vụ bit không dấu; cast giữ kiểu đích có dấu |
| Cast sang `char` tạo Unicode scalar value hợp lệ | `char` chỉ là một đơn vị UTF-16 |

## 15. Bài tập có lời giải

**Bài 1.** `(byte) 300` bằng bao nhiêu?  
**Lời giải:** `300 mod 256 = 44`, mẫu `00101100`, kết quả **44**.

**Bài 2.** `(short) 65535` và `(char) 65535` cho các giá trị số nào?  
**Lời giải:** cùng mẫu `FFFF` nhưng lần lượt là **−1** và **65535**. In bằng `(int)(short)65535` và `(int)(char)65535`.

**Bài 3.** `(int) 0x0000_0002_0000_0005L` bằng bao nhiêu?  
**Lời giải:** **5**; chỉ 32 bit thấp `00000005` còn lại.

**Bài 4.** Những câu nào biên dịch: `byte a = 127;`, `byte b = 128;`, `byte c = (byte)128;`?  
**Lời giải:** a được (=127), b lỗi compile do hằng ngoài miền, c được (=−128).

**Bài 5.** Với `byte b = (byte)0xFE;`, `b`, `(int)b` và `b & 0xFF` là gì?  
**Lời giải:** **−2**, **−2**, **254**. Nâng lên `int` giữ dấu; mask đọc 8 bit không dấu.

**Bài 6.** Làm sao từ chối một `int` ngoài miền `short`?  
**Lời giải:** kiểm tra `x >= Short.MIN_VALUE && x <= Short.MAX_VALUE` trước `(short)x`; nếu sai, trả lỗi thích hợp.

**Bài 7.** Vì sao `b += 1` được nhưng `b = b + 1` với `byte b` không được?  
**Lời giải:** `+=` có narrowing ngầm về kiểu vế trái; `b + 1` là `int` và gán thường không tự thu hẹp biến `int`.

## 16. Checklist trước khi thu hẹp

1. Xác định **độ rộng bit đích** và bit nào sẽ bị loại bỏ.
2. Phân biệt **kiểu có dấu** với `char` không dấu; cùng bit có thể mang giá trị khác.
3. Hỏi rõ cần **bảo toàn giá trị số** hay chỉ lấy **bit thấp** cho giao thức.
4. Kiểm tra **phép tính nào diễn ra trước cast**, có promotion hoặc overflow không.
5. Nếu không được mất thông tin, dùng **kiểm tra miền**, `Math.toIntExact` hoặc các hàm `BigInteger.*ValueExact` phù hợp.
6. Kiểm tra `+=`/`++` có narrowing ngầm; chú ý các biên ±128, ±32.768, ±2³¹.
7. Với byte nhị phân, xác định giao thức là **signed/unsigned** và mask chính xác trước shift/OR.
8. Tự kiểm tra cả hai phía của biên: giá trị trong miền, đúng biên, ngay ngoài biên.

**Điểm cần nhớ:** Narrowing giữa các kiểu nguyên trong Java **giữ n bit thấp của kiểu đích và bỏ bit cao**. Kết quả cuối phụ thuộc cách kiểu đích đọc n bit ấy, có dấu hay không dấu. Cast tường minh không đảm bảo không mất dữ liệu; kiểm tra biên mới đảm bảo giữ nguyên giá trị số học.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-conversions-004">Chuyển char với byte hoặc short</a>

<details>
<summary>Xem chi tiết</summary>

# Chuyển `char` với `byte` hoặc `short` trong Java

## 1. Vì sao chuyển đổi giữa các kiểu 8 và 16 bit lại dễ nhầm?

Trong Java, `char`, `byte` và `short` đều là **kiểu số nguyên nguyên thủy** (*integral primitive types*), nhưng chúng không biểu diễn cùng miền giá trị.

| Kiểu | Kích thước | Có dấu? | Miền số nguyên | Vai trò chính |
|---|---:|---|---|---|
| `byte` | 8 bit | Có | `-128` đến `127` | Dữ liệu byte, số nguyên 8 bit |
| `short` | 16 bit | Có | `-32768` đến `32767` | Số nguyên có dấu 16 bit |
| `char` | 16 bit | **Không** | `0` đến `65535` | **Một đơn vị mã UTF-16** |

Điểm quan trọng nhất là **`char` và `short` đều rộng 16 bit nhưng khác cách diễn giải bit cao nhất**. Với `short`, bit cao nhất tham gia biểu diễn dấu; với `char`, toàn bộ 16 bit tạo thành một giá trị từ 0 đến 65535. Còn `byte` chỉ có 8 bit và cũng là kiểu có dấu.

Do đó, các chuyển đổi giữa `char` với `byte` hoặc `short` là **narrowing primitive conversion** (chuyển đổi thu hẹp) về mặt phân loại quy tắc Java, kể cả `short` và `char` cùng 16 bit. Không có chuyển đổi mở rộng (*widening primitive conversion*) trực tiếp giữa các cặp kiểu này.

Lưu ý: “thu hẹp” trong đặc tả không chỉ nói số bit giảm. Nó còn có thể nói **miền giá trị đích không chứa tất cả giá trị của miền nguồn**. Ví dụ, `char` nhận `40000` nhưng `short` không nhận được số nguyên dương `40000`, dù cả hai cùng 16 bit.

## 2. Chuyển đổi ngầm định và chuyển đổi tường minh

Với **biến thông thường**, Java không cho gán trực tiếp từ `byte` hoặc `short` sang `char`, hay từ `char` sang `byte`/`short`:

~~~java
byte b = 65;
short s = 65;
char c = 'A';

// Không biên dịch:
// char fromByte = b;
// char fromShort = s;
// byte fromChar = c;
// short otherFromChar = c;
~~~

Đây là **đoạn minh họa** cố ý chứa các dòng lỗi được comment. Phép gán giữa các kiểu trên cần **ép kiểu tường minh** (*explicit cast*):

~~~java
char fromByte = (char) b;
char fromShort = (char) s;
byte fromChar = (byte) c;
short otherFromChar = (short) c;
~~~

Hai đoạn cùng ngữ cảnh biến; chúng không phải chương trình độc lập.

Ép kiểu chỉ nói “hãy thực hiện phép chuyển đổi theo quy tắc của Java”. Nó **không bảo đảm giá trị số học được giữ nguyên**, không kiểm tra dữ liệu có ý nghĩa là ký tự hợp lệ, và không phải thao tác giải mã văn bản.

### Ngoại lệ quan trọng: biểu thức hằng

Java cho phép **thu hẹp biểu thức hằng tại phép gán** trong một số trường hợp: biểu thức hằng có kiểu `byte`, `short`, `char` hoặc `int` và giá trị nằm trong miền kiểu đích có thể được gán không cần cast:

~~~java
char letter = 65;   // hằng int 65 vừa char
byte small = 65;    // hằng int 65 vừa byte
short number = 'A'; // hằng char 65 vừa short
~~~

Ngược lại:

~~~java
int variable = 65;
// char invalid = variable; // không biên dịch: biến int không được thu hẹp ngầm
~~~

Điều này giải thích vì sao `char c = 65;` hợp lệ nhưng `int n = 65; char c = n;` không hợp lệ. Không nên kết luận từ cú pháp hằng số rằng Java hỗ trợ chuyển đổi ngầm giữa các biến `byte`, `short` và `char`.

## 3. Từ `byte` sang `char`: số âm biến thành giá trị lớn

Một `byte` có dấu nhận giá trị âm từ `-128` đến `-1`. Khi ép sang `char`, Java thực hiện chuyển đổi theo quy tắc **mở rộng từ `byte` lên `int` rồi thu hẹp xuống `char`**, tương đương về kết quả với việc giữ 16 bit thấp sau khi mở rộng có dấu.

Ví dụ:

~~~java
byte b = -1;
char c = (char) b;
// (int) c == 65535; mã hex của c là FFFF
~~~

Vì `b` bằng `-1`, khi được mở rộng có dấu sang `int`, mẫu bit hai mươi bốn bit cao cũng là 1. Thu hẹp xuống 16 bit tạo `0xFFFF`. `char` diễn giải `0xFFFF` thành **65535**, không phải `-1`.

| `byte` nguồn | Dạng hex của 8 bit | Giá trị `(int) (char) b` | Hex `char` |
|---:|---|---:|---|
| `0` | `00` | `0` | `0000` |
| `65` | `41` | `65` | `0041` |
| `127` | `7F` | `127` | `007F` |
| `-1` | `FF` | `65535` | `FFFF` |
| `-128` | `80` | `65408` | `FF80` |

**Cảnh báo:** Chuyển `byte` âm trực tiếp thành `char` sẽ **không** cho bạn cách hiểu byte ấy là số không dấu từ 0 đến 255. Nếu đang xử lý dữ liệu nhị phân mà muốn lấy giá trị **unsigned byte**, hãy dùng:

~~~java
byte b = (byte) 0xFF;
int unsigned = Byte.toUnsignedInt(b); // 255
int masked = b & 0xFF;               // 255
char unit = (char) unsigned;         // U+00FF nếu thực sự muốn code unit đó
~~~

Đây là đoạn minh họa. `Byte.toUnsignedInt(b)` và `b & 0xFF` cho giá trị `int` từ 0 đến 255. Việc tiếp tục cast sang `char` chỉ hợp lý khi bạn **thực sự đang ánh xạ giá trị số ấy vào một đơn vị mã UTF-16**, không phải đang giải mã UTF-8 hay xác định ký tự của một charset bất kỳ.

## 4. Từ `short` sang `char`: cùng bit, khác ý nghĩa dấu

`short` và `char` cùng 16 bit. Khi chuyển `short` sang `char`, Java giữ 16 bit thấp, nhưng `char` luôn diễn giải chúng là giá trị **không dấu**.

Ví dụ:

~~~java
short s = -1;
char c = (char) s;
// (int) c == 65535 (0xFFFF)
~~~

Một số giá trị minh họa:

| `short` nguồn | Hex 16 bit | Giá trị `char` sau cast |
|---:|---|---:|
| `0` | `0000` | `0` |
| `65` | `0041` | `65` |
| `32767` | `7FFF` | `32767` |
| `-32768` | `8000` | `32768` |
| `-1` | `FFFF` | `65535` |

Một `short` âm không trở thành ký tự “số âm”. Nó trở thành code unit có cùng mẫu 16 bit nhưng giá trị số không dấu. Java cung cấp `Short.toUnsignedInt(shortValue)` để diễn giải mẫu 16 bit của một `short` thành giá trị `int` từ 0 đến 65535.

Ví dụ `Short.toUnsignedInt((short) -1)` bằng 65535, trùng với `(int) (char) ((short) -1)` về **giá trị số**. Tuy nhiên, khi mục tiêu là xử lý số nhị phân unsigned 16 bit, `int` thường diễn đạt ý nghĩa rõ hơn `char`; nên giữ `char` cho các API xử lý code unit UTF-16.

## 5. Từ `char` sang `byte`: mất 8 bit cao và có thể đổi dấu

Khi cast `char` xuống `byte`, Java giữ **8 bit thấp nhất**. Kết quả được diễn giải theo số nguyên **có dấu 8 bit**:

~~~java
char c1 = '\u0041'; // 'A' = 65
char c2 = '\u00FF'; // 255
char c3 = '\u0100'; // 256

byte b1 = (byte) c1; // 65
byte b2 = (byte) c2; // -1 (0xFF)
byte b3 = (byte) c3; // 0  (0x0100 -> 0x00)
~~~

Bảng ví dụ:

| `char` nguồn | Hex 16 bit | `(byte) c` | Giải thích |
|---:|---|---:|---|
| `65` | `0041` | `65` | Trong miền `byte` có dấu |
| `127` | `007F` | `127` | Giá trị dương lớn nhất của `byte` |
| `128` | `0080` | `-128` | Bit dấu của byte đích bằng 1 |
| `255` | `00FF` | `-1` | Giữ `FF`, diễn giải có dấu |
| `256` | `0100` | `0` | 8 bit cao bị loại |
| `65535` | `FFFF` | `-1` | 8 bit thấp đều là 1 |

Đây là **chuyển đổi dữ liệu số**. Nếu `char` biểu diễn ký tự Unicode, cast sang `byte` **không có nghĩa là chuyển ký tự thành UTF-8**. UTF-8 sử dụng thuật toán mã hóa và có thể cần nhiều byte cho một code point. Bài học về mã hóa ở phần sau cho ví dụ cụ thể.

## 6. Từ `char` sang `short`: giá trị trên 32767 trở thành âm

Từ `char` (0..65535) sang `short` (-32768..32767), Java giữ 16 bit và diễn giải theo kiểu số nguyên có dấu:

~~~java
char a = (char) 32767;
char b = (char) 32768;
char c = (char) 65535;

short s1 = (short) a; // 32767
short s2 = (short) b; // -32768
short s3 = (short) c; // -1
~~~

Không có bit nào bị bỏ khi chuyển `char` sang `short`, vì độ rộng đều là 16 bit. Tuy nhiên, **giá trị toán học có thể đổi dấu** do cách diễn giải bit đầu cao nhất. Có thể hiểu công thức tương đương:

- Với `c <= 32767`: `(short) c` giữ giá trị số.
- Với `c >= 32768`: `(short) c` bằng `(int) c - 65536`.

Tại sao lại cần cast dù không giảm số bit? Vì giá trị như `65535` nằm trong miền `char` nhưng ngoài miền `short`. Java yêu cầu thể hiện rõ thao tác đổi cách hiểu dấu.

## 7. Bốn phép chuyển trong một chương trình Java 17+ hoàn chỉnh

Tạo tệp `CharByteShortDemo.java`:

~~~java
public class CharByteShortDemo {
    public static void main(String[] args) {
        byte b = 65;
        short s = 65;
        char a = 'A';

        System.out.println("byteToChar=" + (int) (char) b);
        System.out.println("shortToChar=" + (int) (char) s);
        System.out.println("charToByte=" + (byte) a);
        System.out.println("charToShort=" + (short) a);

        byte negativeByte = -1;
        short negativeShort = -1;
        System.out.printf("negativeByteToChar=U+%04X%n",
                (int) (char) negativeByte);
        System.out.printf("negativeShortToChar=U+%04X%n",
                (int) (char) negativeShort);

        char ff = (char) 0x00FF;
        char highBit = (char) 0x8000;
        System.out.println("FFToByte=" + (byte) ff);
        System.out.println("FFToShort=" + (short) ff);
        System.out.println("8000ToShort=" + (short) highBit);
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp bằng JDK 17 trở lên:

~~~bash
javac CharByteShortDemo.java
java CharByteShortDemo
~~~

Đầu ra chính xác:

~~~text
byteToChar=65
shortToChar=65
charToByte=65
charToShort=65
negativeByteToChar=U+FFFF
negativeShortToChar=U+FFFF
FFToByte=-1
FFToShort=255
8000ToShort=-32768
~~~

Từ `65`, bốn hướng chuyển đổi đều giữ giá trị vì nó nằm trong miền chung của ba kiểu. Nhưng `-1` chuyển sang `char` thành 65535; `char` 255 khi thu về `byte` thành -1; `char` 32768 chuyển sang `short` thành -32768. Những kết quả này không phụ thuộc vào cách màn hình hiển thị ký tự, mà đến từ quy tắc số nguyên.

## 8. Khi byte từ mạng hoặc file được dùng như số không dấu

Nhiều định dạng nhị phân định nghĩa một trường 8 bit hoặc 16 bit **không dấu**. Vì Java không có primitive `unsigned byte` hay `unsigned short` riêng, người lập trình thường lưu raw bit bằng `byte`/`short` và diễn giải bằng kiểu `int`:

- `Byte.toUnsignedInt(byteValue)` trả giá trị 0..255.
- `Short.toUnsignedInt(shortValue)` trả giá trị 0..65535.
- `byteValue & 0xFF` tương đương về giá trị với cách đọc unsigned byte.
- `shortValue & 0xFFFF` tương đương về giá trị với cách đọc unsigned short.

Tạo tệp `UnsignedNumbersDemo.java`:

~~~java
public class UnsignedNumbersDemo {
    public static void main(String[] args) {
        byte rawByte = (byte) 0xFF;
        short rawShort = (short) 0xFFFF;

        int unsignedByte = Byte.toUnsignedInt(rawByte);
        int unsignedShort = Short.toUnsignedInt(rawShort);

        char directByteCast = (char) rawByte;
        char unsignedByteAsChar = (char) unsignedByte;

        System.out.println("signedByte=" + rawByte);
        System.out.println("unsignedByte=" + unsignedByte);
        System.out.println("maskedByte=" + (rawByte & 0xFF));
        System.out.printf("directByteCast=U+%04X%n",
                (int) directByteCast);
        System.out.printf("unsignedByteAsChar=U+%04X%n",
                (int) unsignedByteAsChar);

        System.out.println("signedShort=" + rawShort);
        System.out.println("unsignedShort=" + unsignedShort);
        System.out.println("maskedShort=" + (rawShort & 0xFFFF));
        System.out.printf("directShortCast=U+%04X%n",
                (int) (char) rawShort);
    }
}
~~~

~~~bash
javac UnsignedNumbersDemo.java
java UnsignedNumbersDemo
~~~

Đầu ra:

~~~text
signedByte=-1
unsignedByte=255
maskedByte=255
directByteCast=U+FFFF
unsignedByteAsChar=U+00FF
signedShort=-1
unsignedShort=65535
maskedShort=65535
directShortCast=U+FFFF
~~~

**Khác biệt quan trọng:** cast trực tiếp `(char) rawByte` khi raw byte là `-1` tạo `U+FFFF`; chuyển qua `Byte.toUnsignedInt` trước rồi cast cho `U+00FF`. Hai kết quả khác nhau dù cùng bắt đầu từ mẫu bit `FF` của byte.

Đối với raw `short`, cast sang `char` và diễn giải unsigned 16 bit có cùng con số 0..65535, nhưng **ý nghĩa dữ liệu** vẫn khác: một số đo/giao thức unsigned 16 bit không tự biến thành một ký tự Unicode.

## 9. Chuyển đổi số không phải là mã hóa hay giải mã văn bản

`char` là **một đơn vị mã UTF-16**, còn `byte` là **một đơn vị dữ liệu 8 bit**. Cast giữa chúng chỉ thực hiện các quy tắc chuyển đổi primitive ở những phần trước. Cast **không biết charset** và không thực hiện thuật toán UTF-8/UTF-16.

Ví dụ chữ `é` là code point `U+00E9`. Khi mã hóa bằng UTF-8, nó dùng **hai byte `C3 A9`**, không phải chỉ một byte `E9`. Mỗi byte UTF-8 đó khi đọc vào primitive `byte` có giá trị có dấu (theo thứ tự) `-61` và `-87`.

Nếu cast từng byte sang `char`, Java chỉ mở rộng/thu hẹp bit: `(char) (byte) 0xC3` cho `U+FFC3`, và `(char) (byte) 0xA9` cho `U+FFA9`. Kết quả **không phải** văn bản `é`.

Muốn giải mã đúng, dùng API charset:

~~~java
import java.nio.charset.StandardCharsets;

public class Utf8DecodeDemo {
    public static void main(String[] args) {
        byte[] encoded = {(byte) 0xC3, (byte) 0xA9};
        String decoded = new String(encoded, StandardCharsets.UTF_8);

        System.out.println("decoded=" + decoded);
        System.out.println("units=" + decoded.length());
        System.out.printf("unit=U+%04X%n", (int) decoded.charAt(0));
        System.out.printf("wrongFirst=U+%04X%n",
                (int) (char) encoded[0]);
        System.out.printf("wrongSecond=U+%04X%n",
                (int) (char) encoded[1]);
    }
}
~~~

~~~bash
javac Utf8DecodeDemo.java
java Utf8DecodeDemo
~~~

Đầu ra:

~~~text
decoded=é
units=1
unit=U+00E9
wrongFirst=U+FFC3
wrongSecond=U+FFA9
~~~

Dữ liệu `byte[]` đã được giải mã theo `StandardCharsets.UTF_8` mới trở thành `String` đúng. Khi cần biến chuỗi thành byte theo charset, dùng `text.getBytes(StandardCharsets.UTF_8)`. Đây là quy tắc quan trọng cho file, JSON, HTTP và dữ liệu từ mạng.

Lưu ý rằng `char` chỉ biểu diễn **một code unit UTF-16**, nên ngay cả khi đã giải mã đúng, một Unicode code point ngoài BMP (chẳng hạn emoji) có thể chiếm **hai** giá trị `char` trong `String`. Không coi một byte hay một char bất kỳ là “một ký tự người dùng nhìn thấy”.

## 10. Phép toán với `char`, `byte` và `short` được nâng kiểu

Trong đa số biểu thức số học, `byte`, `short` và `char` được **nâng lên `int`**. Vì thế:

~~~java
byte b = 1;
short s = 2;
char c = 'A';

int sum = b + s + c;     // int: 1 + 2 + 65 = 68
int nextCode = c + 1;    // int: 66

// Không biên dịch:
// char next = c + 1;
// byte smallSum = b + 1;
~~~

Đây là đoạn minh họa (các câu không hợp lệ đã comment). Mặc dù `char` là kiểu số nguyên không dấu, kết quả `c + 1` có kiểu `int`. Nếu ép về `char`, có thể gây cuốn vòng về 16 bit thấp:

~~~java
char max = Character.MAX_VALUE;     // 65535
char wrapped = (char) (max + 1);    // 0
~~~

Cast rõ ràng không kiểm tra tràn. Với số học thông thường, giữ kết quả ở `int`, kiểm tra miền trước khi cast về kiểu hẹp; các phép toán `Math.toIntExact` chỉ kiểm tra chuyển `long` sang `int`, **không** tự kiểm tra chuyển `int` sang `char`/`byte`/`short`.

### Làm sao kiểm tra miền thay vì cắt bit?

Giả sử API nhận `int codeUnit` và yêu cầu trả về `char` giữ nguyên giá trị:

~~~java
if (codeUnit < Character.MIN_VALUE || codeUnit > Character.MAX_VALUE) {
    throw new IllegalArgumentException("out of char range");
}
char unit = (char) codeUnit;
~~~

Đoạn minh họa này bảo đảm giá trị số nằm trong 0..65535 trước khi cast. Nhưng “nằm trong miền `char`” **chưa chắc có nghĩa là Unicode scalar value hoàn chỉnh**: vùng `U+D800`–`U+DFFF` là các code unit surrogate, vốn cần phối hợp trong cặp nếu biểu diễn code point ngoài BMP.

Đối với `byte` và `short` có dấu, kiểm tra `Byte.MIN_VALUE`/`Byte.MAX_VALUE` hoặc `Short.MIN_VALUE`/`Short.MAX_VALUE` tương tự. Nếu muốn diễn giải một unsigned byte, hãy kiểm tra 0..255 hoặc dùng `Byte.toUnsignedInt` với raw byte.

## 11. Ví dụ mất dữ liệu và tính chất round-trip

Một *round-trip* là chuyển từ kiểu A sang kiểu B rồi quay trở lại A. Không phải round-trip nào cũng giữ nguyên giá trị.

Tạo tệp `NarrowingRoundTripDemo.java`:

~~~java
public class NarrowingRoundTripDemo {
    public static void main(String[] args) {
        char original = '\u0100'; // 256
        byte narrowed = (byte) original; // 0
        char restored = (char) narrowed; // 0

        char small = 'A';
        byte smallByte = (byte) small;
        char smallRestored = (char) smallByte;

        short negativeShort = -1;
        char unsignedUnit = (char) negativeShort;
        short sameBits = (short) unsignedUnit;

        System.out.println("original=" + (int) original);
        System.out.println("narrowed=" + narrowed);
        System.out.println("restored=" + (int) restored);
        System.out.println("smallRoundTrip=" + (small == smallRestored));
        System.out.println("shortBitsRoundTrip=" + (negativeShort == sameBits));
        System.out.println("charUnsignedValue=" + (int) unsignedUnit);
    }
}
~~~

~~~bash
javac NarrowingRoundTripDemo.java
java NarrowingRoundTripDemo
~~~

Đầu ra:

~~~text
original=256
narrowed=0
restored=0
smallRoundTrip=true
shortBitsRoundTrip=true
charUnsignedValue=65535
~~~

Ở ví dụ thứ nhất, `char` có giá trị 256 mất bit cao khi chuyển qua `byte` rồi không thể khôi phục thông tin gốc. Ở ví dụ thứ hai, `'A'` bằng 65 nằm trong khoảng an toàn của `byte` nên giữ nguyên. Ở ví dụ thứ ba, đi từ `short -1` sang `char 65535` rồi quay về `short -1` giữ nguyên **mẫu bit**, nhưng giá trị số trung gian khác nhau.

Cần phân biệt rõ:

- **Bảo toàn mẫu bit:** giữ các bit trong một độ rộng nhất định.
- **Bảo toàn giá trị số:** kết quả số học giống ban đầu.
- **Bảo toàn văn bản:** các code point/ký tự vẫn mang cùng ý nghĩa sau khi mã hóa/giải mã.

Một chuyển đổi có thể giữ mẫu bit nhưng đổi giá trị số, hoặc làm mất dữ liệu mà không có exception.

## 12. Những lỗi phổ biến và cách tránh

| Nhầm lẫn | Thực tế |
|---|---|
| “`char` và `short` đều 16 bit nên gán trực tiếp được.” | Không: miền số khác nhau; cần cast khi làm việc với biến. |
| “`byte` sang `char` là widening vì `char` có 16 bit.” | Không có widening trực tiếp `byte → char`; cast là bắt buộc. |
| “`(char) (byte) -1` bằng 255.” | Sai: nó bằng 65535 (`U+FFFF`); muốn unsigned byte 255, dùng `Byte.toUnsignedInt` trước. |
| “`(short) (char) 65535` bằng 65535.” | Sai: kết quả `short` là -1. |
| “`(byte) '\u0100'` báo lỗi overflow.” | Sai: cast primitive cho 0 sau khi bỏ 8 bit cao. |
| “Ép byte sang char sẽ đọc đúng UTF-8.” | Sai: cần decoder, ví dụ `new String(bytes, StandardCharsets.UTF_8)`. |
| “Kết quả `char + 1` vẫn là `char`.” | Sai: biểu thức được nâng lên `int`. |
| “`Math.toIntExact` kiểm tra được việc cast `int` sang `byte`.” | Sai: API này chỉ chuyển `long → int`. |
| “Một `char` bằng một ký tự Unicode hiển thị.” | Sai: `char` là một code unit UTF-16; một code point có thể cần hai code unit. |

## 13. Checklist khi cần chuyển `char` với `byte`/`short`

1. **Xác định dữ liệu:** đang tính số, đọc trường nhị phân, xử lý đơn vị mã UTF-16 hay giải mã văn bản?
2. **Xác định cách hiểu dấu:** `byte`/`short` có dấu, `char` không dấu. Dữ liệu đầu vào có thể âm không?
3. **Kiểm tra miền hợp lệ:** đầu vào có chắc nằm trong khoảng biểu diễn của kiểu đích khi cần giữ giá trị không?
4. **Kiểm tra mục tiêu:** muốn giữ **giá trị số**, giữ **mẫu bit**, hay khôi phục **văn bản**? Ba mục tiêu đòi cách làm khác nhau.
5. **Chọn API đúng:** `Byte.toUnsignedInt`/`Short.toUnsignedInt` để đọc unsigned; charset decoder để đọc byte thành chuỗi; cast chỉ khi thực sự muốn phép chuyển đổi số Java.
6. **Test các biên:** `-128`, `-1`, `0`, `127`, `128`, `255`, `256`, `32767`, `32768`, `65535` và dữ liệu Unicode nhiều byte/cặp surrogate khi có xử lý văn bản.

## 14. Tự kiểm tra

1. Vì sao không thể gán trực tiếp biến `short` vào `char` dù chúng đều 16 bit?
2. Kết quả `(int) (char) ((byte) -1)` là 255 hay 65535? Giải thích.
3. `(byte) ((char) 256)` bằng bao nhiêu? Thông tin nào bị mất?
4. `(short) ((char) 0x8000)` bằng bao nhiêu, và tại sao?
5. Nếu giao thức truyền byte `0xFF` đại diện cho số unsigned 255, nên dùng `(char) rawByte` hay `Byte.toUnsignedInt(rawByte)` để tính toán?
6. Vì sao một chuỗi UTF-8 gồm hai byte `C3 A9` không thể khôi phục chữ `é` bằng hai phép cast `(char)`?
7. `char c = 65;` có biên dịch không? Còn `int x = 65; char c = x;` thì sao?
8. `char max = Character.MAX_VALUE; char next = (char) (max + 1);` cho giá trị số bao nhiêu?

**Gợi ý đáp án:** (1) Khác miền và cách diễn giải dấu nên không có widening giữa `short` với `char`. (2) 65535 do sign extension của `byte -1` trước khi thu hẹp xuống 16 bit. (3) 0, mất tám bit cao. (4) -32768, giữ mẫu bit `8000` nhưng `short` có dấu. (5) `Byte.toUnsignedInt`. (6) UTF-8 là mã hóa nhiều byte, phải giải mã bằng charset. (7) Câu đầu hợp lệ do hằng số 65 nằm trong miền `char`; câu sau không biên dịch nếu không cast. (8) 0 vì cast giữ 16 bit thấp.

**Ghi nhớ:** Giữa `char` với `byte` hoặc `short`, Java yêu cầu **ép kiểu tường minh** đối với biến vì miền giá trị và cách diễn giải dấu khác nhau. Cast có thể giữ bit, đổi dấu hoặc bỏ bit; khi xử lý binary unsigned hãy dùng các API đọc số không dấu, còn khi xử lý văn bản hãy dùng charset decoder phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-conversions-005">Ép float hoặc double sang số nguyên cắt về phía 0</a>

<details>
<summary>Xem chi tiết</summary>

# Ép `float` hoặc `double` sang số nguyên: cắt về phía 0 trong Java

## 1. Bản chất của phép ép kiểu

Trong Java, một giá trị `float` hoặc `double` có thể chứa phần thập phân, còn các kiểu số nguyên `byte`, `short`, `int`, `long` và `char` biểu diễn các giá trị nguyên trong miền hữu hạn. Khi ép kiểu số thực sang số nguyên bằng cú pháp `(T) expression`, Java thực hiện **narrowing primitive conversion** (chuyển đổi nguyên thủy thu hẹp).

Với một số thực **hữu hạn có phần nguyên biểu diễn được**, phép chuyển đổi loại bỏ phần lẻ bằng quy tắc **làm tròn về 0** (*round toward zero*, *truncation*):

- `(int) 7.9` bằng **7**.
- `(int) 7.1` bằng **7**.
- `(int) -7.9` bằng **-7**.
- `(int) -7.1` bằng **-7**.

Đây **không phải làm tròn tới số nguyên gần nhất**. Nó cũng không phải luôn “làm tròn xuống”: với số âm, `-7.9` chuyển thành `-7`, trong khi `Math.floor(-7.9)` là `-8.0`.

Điểm quan trọng khác: khi số thực là **NaN**, **vô cực**, hoặc có độ lớn vượt miền số nguyên đích, Java có các quy tắc riêng. Không thể chỉ nói “bỏ dấu chấm” hoặc “xóa phần thập phân” để giải thích mọi trường hợp.

## 2. Cú pháp và kiểu kết quả

Cú pháp:

```java
int n = (int) 12.75;      // 12
long m = (long) -12.75;   // -12
float f = 3.99f;
int fromFloat = (int) f;  // 3
double d = -3.99;
long fromDouble = (long) d; // -3
```

`(int)` và `(long)` là **toán tử ép kiểu tường minh** (*cast*). Biểu thức sau khi ép có kiểu tương ứng với tên kiểu được ghi trong dấu ngoặc. Java không tự động cho phép gán một `double` bất kỳ vào `int` mà không có cast:

```java
double value = 12.75;
// int invalid = value; // LỖI biên dịch: có nguy cơ mất phần lẻ
int valid = (int) value;
```

Về cú pháp, dấu ngoặc ở `(int) value` là một phần của cast, không phải lời gọi phương thức. Hãy đặt dấu ngoặc ngoài khi cần ép kết quả cả biểu thức:

```java
double a = 1.9;
double b = 2.9;

int castEach = (int) a + (int) b; // 1 + 2 = 3
int castSum = (int) (a + b);      // (int) 4.8 = 4
```

**Ép từng toán hạng rồi cộng** khác với **cộng số thực trước rồi ép kết quả**. Quyết định cách nào đúng phụ thuộc yêu cầu nghiệp vụ; không thể tùy tiện chuyển vị trí của cast.

## 3. Cắt về 0 khác `floor`, `ceil` và làm tròn gần nhất

Xem bảng đối chiếu với đầu vào là `double`:

| Giá trị đầu vào | `(int) x` | `Math.floor(x)` | `Math.ceil(x)` | `Math.round(x)` |
|---:|---:|---:|---:|---:|
| 3.8 | 3 | 3.0 | 4.0 | 4 |
| 3.2 | 3 | 3.0 | 4.0 | 3 |
| -3.8 | -3 | -4.0 | -3.0 | -4 |
| -3.2 | -3 | -4.0 | -3.0 | -3 |
| 2.5 | 2 | 2.0 | 3.0 | 3 |
| -2.5 | -2 | -3.0 | -2.0 | -2 |

Ý nghĩa:

- **Cast `(int) x`**: cắt về 0; kết quả kiểu `int`.
- **`Math.floor(x)`**: lấy số nguyên theo hướng **âm vô cực**; với đối số `double`, kết quả vẫn có kiểu `double`.
- **`Math.ceil(x)`**: lấy số nguyên theo hướng **dương vô cực**; với đối số `double`, kết quả vẫn có kiểu `double`.
- **`Math.round(double)`**: chọn số nguyên gần nhất, xử lý trường hợp đúng nửa theo hướng dương vô cực; trả về `long`. Với `float`, overload `Math.round(float)` trả về `int`.

Ví dụ `Math.round(-2.5)` bằng -2, còn `Math.round(2.5)` bằng 3. Cast cả hai cho -2 và 2 vì chỉ loại bỏ phần lẻ.

**Không dùng `Math.floor` để mô phỏng cast với số âm.** Nếu muốn hành vi của cast, viết cast và áp dụng kiểm tra NaN/miền giá trị khi cần. Nếu muốn quy tắc làm tròn cho tài chính, độ chính xác hoặc quy tắc nửa lên/nửa chẵn, hãy xác định rõ yêu cầu và chọn API thích hợp; cast không đáp ứng những yêu cầu làm tròn đó.

## 4. Ví dụ hoàn chỉnh: cắt phần lẻ về 0

Tạo file `TruncationDemo.java`:

```java
public class TruncationDemo {
    public static void main(String[] args) {
        double positive = 7.9;
        double negative = -7.9;

        System.out.println("(int) 7.9 = " + (int) positive);
        System.out.println("(int) -7.9 = " + (int) negative);
        System.out.println("(long) -7.9 = " + (long) negative);
        System.out.println("(int) -0.9 = " + (int) -0.9);
        System.out.println("(int) 0.9 = " + (int) 0.9);

        System.out.println("floor(-7.9) = " + Math.floor(negative));
        System.out.println("ceil(-7.9) = " + Math.ceil(negative));
        System.out.println("round(7.9) = " + Math.round(positive));
        System.out.println("round(-7.9) = " + Math.round(negative));

        double a = 1.9;
        double b = 2.9;
        System.out.println("cast each = " + ((int) a + (int) b));
        System.out.println("cast sum = " + (int) (a + b));
    }
}
```

Biên dịch và chạy bằng Java 17+:

```bash
javac TruncationDemo.java
java TruncationDemo
```

Kết quả:

```text
(int) 7.9 = 7
(int) -7.9 = -7
(long) -7.9 = -7
(int) -0.9 = 0
(int) 0.9 = 0
floor(-7.9) = -8.0
ceil(-7.9) = -7.0
round(7.9) = 8
round(-7.9) = -8
cast each = 3
cast sum = 4
```

Một lưu ý về **-0.0**: `double` và `float` có cả giá trị +0.0 và -0.0, nhưng kiểu số nguyên Java không có hai loại số 0 như vậy. Ép `-0.0` sang `int` hoặc `long` đều cho **0**.

## 5. Quy tắc Java thực sự dùng khi chuyển sang số nguyên

Đặc tả ngôn ngữ Java (JLS, phần *Narrowing Primitive Conversion*) xác định việc chuyển từ `float`/`double` sang kiểu tích phân theo **hai bước**.

### Bước 1: chuyển sang `int` hoặc `long`

- Nếu kiểu đích cuối là `long`, bước đầu chuyển sang `long`.
- Nếu kiểu đích cuối là `byte`, `short`, `char` hoặc `int`, bước đầu chuyển sang `int`.

Quy tắc của bước này:

1. **NaN** → kết quả nguyên **0**.
2. **Số hữu hạn** → làm tròn về 0; nếu phần nguyên biểu diễn được trong kiểu trung gian, lấy phần nguyên đó.
3. **Quá lớn hoặc +Infinity** → dùng giá trị **lớn nhất** của `int` hoặc `long` tương ứng.
4. **Quá nhỏ hoặc -Infinity** → dùng giá trị **nhỏ nhất** của `int` hoặc `long` tương ứng.

Việc đưa kết quả về giá trị biên khi vượt miền thường gọi là **saturation/clamping**. Nó khác với quy tắc bỏ bit khi chuyển từ một kiểu nguyên lớn sang kiểu nguyên nhỏ.

### Bước 2: nếu đích là `byte`, `short` hoặc `char`

Kết quả `int` của bước đầu tiếp tục được **thu hẹp theo quy tắc chuyển số nguyên**: giữ các bit thấp tương ứng 8 hoặc 16 bit, rồi diễn giải theo kiểu đích. Vì thế kết quả **có thể quay vòng, đổi dấu**, chứ không bị kẹp trực tiếp trong miền `byte`/`short`.

Ví dụ:

```java
byte b = (byte) 130.9; // bước 1: int 130; bước 2: byte -126
short s = (short) 32768.7; // bước 1: int 32768; bước 2: short -32768
char c = (char) -1.9; // bước 1: int -1; bước 2: char code unit 65535
```

**Đừng nhầm hai kiểu thu hẹp:** ép số thực sang `int/long` có quy tắc làm tròn và giới hạn biên; ép một số `int` sang `byte/short/char` tiếp tục bằng việc giữ bit thấp.

## 6. Giá trị đặc biệt cần xác thực trước khi ép

Quy tắc cắt về 0 chỉ mô tả trường hợp số hữu hạn nằm trong miền. Với `NaN`, cast sang `int` hoặc `long` cho `0`; với `+Infinity`, `-Infinity` hoặc giá trị vượt miền, kết quả bị chặn ở biên tương ứng. Nếu đích là `byte`, `short` hoặc `char`, Java chuyển qua `int` trước, rồi giữ các bit thấp của kiểu đích; do đó không có quy tắc chặn trực tiếp về giới hạn `byte` hay `short`.

Ví dụ `(int) Double.NaN == 0` và `(byte) Double.POSITIVE_INFINITY == -1`. Những kết quả này chỉ là quy tắc cast, không chứng minh dữ liệu đầu vào hợp lệ. Khi API cần số nguyên từ phép đo, nên dùng `Double.isFinite` và kiểm tra miền trước khi cast. Bài tiếp theo khảo sát riêng các trường hợp `NaN`, vô cực và biên số thực bằng trace và ví dụ đầy đủ.

## 7. Mất độ chính xác có thể xảy ra **trước cả phép ép**

Kiểu `float` (32 bit) có khoảng 24 bit ý nghĩa nhị phân; `double` (64 bit) có khoảng 53 bit ý nghĩa. Không phải mọi số thập phân hay số nguyên lớn đều biểu diễn chính xác.

Ví dụ:

```java
float f = 16_777_217f;  // không lưu chính xác số này
int value = (int) f;     // 16777216
```

Số nguyên toán học **16_777_217** không biểu diễn chính xác bằng `float`; giá trị trong biến đã được làm tròn thành 16_777_216 **trước** khi ép. Phép `(int) f` chỉ chuyển **giá trị thực sự đang lưu**, không lấy lại chữ số đã mất.

Tương tự, nhiều phân số thập phân như `0.1` không biểu diễn chính xác bằng số thực nhị phân. Với phép ép số nguyên, điều này đặc biệt đáng lưu ý khi kết quả tính toán nằm sát một ranh giới nguyên.

```java
double value = 0.1 + 0.2;
System.out.println(value); // 0.30000000000000004
System.out.println((int) value); // 0
```

Không nên coi việc cast là cách sửa sai số dấu phẩy động. Khi cần giữ số chữ số thập phân và quy tắc làm tròn có kiểm soát (ví dụ tiền tệ), cân nhắc `BigDecimal` cùng nguồn dữ liệu và `RoundingMode` phù hợp.

## 8. Vì sao `Math.round` không giống ép kiểu?

Một chương trình có thể cần “lấy phần nguyên” theo nhiều nghĩa:

- **Bỏ phần lẻ về 0**: `(int) x` hoặc `(long) x`.
- **Lấy số nguyên không lớn hơn x**: `Math.floor(x)`.
- **Lấy số nguyên không nhỏ hơn x**: `Math.ceil(x)`.
- **Chọn số nguyên gần nhất**: `Math.round(x)` theo quy tắc của phương thức.

Các phương thức này không chỉ khác nhau với số âm; chúng còn có **kiểu trả về** và các quy tắc đặc biệt riêng.

Ví dụ:

```java
System.out.println((int) 2.9);       // 2
System.out.println(Math.round(2.9));  // 3
System.out.println((int) -2.9);      // -2
System.out.println(Math.round(-2.9)); // -3
System.out.println(Math.floor(-2.9)); // -3.0
System.out.println(Math.ceil(-2.9));  // -2.0
```

`Math.round(double)` trả `long`, nhưng `Math.round(float)` trả `int`; phương thức đó cũng có hành vi khi gặp NaN và vượt miền, nên **đừng thay kiểm tra tính hợp lệ bằng `round`**. Ngoài ra, kết quả `Math.floor`/`ceil` vẫn là kiểu số thực, chưa phải `int` hoặc `long`.

Nếu yêu cầu **từ chối mọi giá trị có phần lẻ**, cast đơn thuần là không đủ, vì nó cố ý bỏ phần lẻ. Dùng cách kiểm tra phù hợp trước khi chuyển.

## 9. Kiểm tra dữ liệu trước khi chuyển sang `int`

Giả sử một ứng dụng nhận `double` từ bên ngoài nhưng yêu cầu:

1. Giá trị phải là **hữu hạn** (không NaN, không vô cực).
2. Giá trị đầu vào phải **nằm trong miền số nguyên `int`**.
3. Chấp nhận phép **cắt phần lẻ về 0** theo quy tắc bài học.

Một phương thức kiểm tra tường minh:

```java
static int truncateFiniteInt(double value) {
    if (!Double.isFinite(value)
            || value < Integer.MIN_VALUE
            || value >= 2_147_483_648.0) {
        throw new IllegalArgumentException("outside int input range");
    }
    return (int) value;
}
```

Đây là **đoạn phương thức minh họa**, cần đặt trong một class Java. Mốc cận trên dùng `2_147_483_648.0` vì đó là **số đầu tiên lớn hơn `Integer.MAX_VALUE`** trong miền số nguyên; biểu diễn `(double) Integer.MAX_VALUE` bằng 2_147_483_647.0 là chính xác.

Phương thức này áp dụng **chính sách kiểm tra giá trị số thực gốc phải nằm trong miền `int`**, chứ không chỉ kiểm tra “sau khi cắt, số nguyên có thể biểu diễn được không”. Ví dụ `-2_147_483_648.5` nếu cast trực tiếp có thể ra `Integer.MIN_VALUE`, nhưng chính sách trên từ chối vì **giá trị đầu vào** nhỏ hơn cận dưới. Đây là lựa chọn kiểm tra rõ ràng và nghiêm ngặt cho API.

Nếu ứng dụng còn yêu cầu **không được có phần lẻ**, thêm:

```java
if (value != Math.rint(value)) {
    throw new IllegalArgumentException("fractional input");
}
```

Điều kiện này chỉ nên dùng **sau kiểm tra `Double.isFinite`**; `Math.rint` trả về giá trị `double` gần nhất theo kiểu làm tròn nửa về số chẵn, và so sánh bằng chỉ giúp kiểm tra giá trị đang lưu đã là số nguyên hay chưa. Nó **không chứng minh** văn bản đầu vào ban đầu không có phần thập phân, vì dữ liệu `double` có thể đã mất độ chính xác trước đó.

### Phiên bản runnable kèm kiểm tra

Tạo file `ValidatedTruncation.java`:

```java
public class ValidatedTruncation {
    static int truncateFiniteInt(double value) {
        if (!Double.isFinite(value)
                || value < Integer.MIN_VALUE
                || value >= 2_147_483_648.0) {
            throw new IllegalArgumentException("outside int input range");
        }
        return (int) value;
    }

    public static void main(String[] args) {
        System.out.println(truncateFiniteInt(15.9));
        System.out.println(truncateFiniteInt(-15.9));

        for (double value : new double[] {
                Double.NaN, Double.POSITIVE_INFINITY, 1.0e20
        }) {
            try {
                System.out.println(truncateFiniteInt(value));
            } catch (IllegalArgumentException e) {
                System.out.println("rejected");
            }
        }
    }
}
```

Chạy:

```bash
javac ValidatedTruncation.java
java ValidatedTruncation
```

Kết quả:

```text
15
-15
rejected
rejected
rejected
```

Với `long`, cần chú ý đặc biệt: **`(double) Long.MAX_VALUE` làm tròn thành \(2^{63}\)**, vốn **ngoài miền `long`**. Bởi vậy kiểm tra `value <= Long.MAX_VALUE` dưới phép so sánh số thực không bảo đảm ngăn chặn giới hạn trên. Với chính sách yêu cầu giá trị đầu vào nằm trong miền `long`, các cận hữu ích là:

```java
static long truncateFiniteLong(double value) {
    if (!Double.isFinite(value)
            || value < -0x1.0p63
            || value >= 0x1.0p63) {
        throw new IllegalArgumentException("outside long input range");
    }
    return (long) value;
}
```

`0x1.0p63` là literal **số thực dạng thập lục phân** của Java, có giá trị chính xác \(2^{63}\) ở kiểu `double`. Đây không phải literal số nguyên. Cận dưới \(-2^{63}\) là giá trị chính xác hợp lệ của `long`, còn cận trên \(2^{63}\) là giá trị đầu tiên bị loại khỏi miền.

## 10. Khi nào cần `BigDecimal` hoặc chuyển đổi chính xác?

Nếu dữ liệu ban đầu là **chuỗi thập phân** hoặc giá trị phải giữ chính xác phần thập phân (ví dụ số tiền), việc đọc vào `double` trước có thể làm mất chính xác. `BigDecimal` giúp biểu diễn các số thập phân theo decimal và kiểm soát chuyển đổi:

```java
import java.math.BigDecimal;

BigDecimal amount = new BigDecimal("123.75");
int truncated = amount.intValue(); // 123, nhưng có thể mất phần lẻ
```

Đây là **đoạn mã minh họa** bên trong phương thức, không phải một tệp chạy độc lập. Nếu muốn **bắt lỗi khi có phần lẻ hoặc không vừa kiểu đích**, dùng:

```java
BigDecimal amount = new BigDecimal("123.75");
// int exact = amount.intValueExact(); // ném ArithmeticException
```

`intValueExact()` chỉ thành công khi giá trị là số nguyên chính xác nằm trong miền `int`; `longValueExact()` làm điều tương tự cho `long`. Với yêu cầu làm tròn trước khi chuyển, chọn `setScale(0, RoundingMode.XXX)` với chính sách rõ ràng rồi gọi phương thức chuyển phù hợp.

Nếu số nguyên có thể vượt miền `long`, `BigInteger` là một lựa chọn cho số nguyên độ lớn không cố định; `BigDecimal.toBigIntegerExact()` yêu cầu giá trị thập phân đúng là số nguyên. Không nên giả định cast từ `double` sang `long` sẽ tự mở rộng giới hạn lưu trữ.

## 11. Những lỗi thường gặp

| Hiện tượng | Nguyên nhân | Cách xử lý |
|---|---|---|
| `(int) -3.9` cho -3 chứ không -4 | Cast cắt về 0 | Dùng `Math.floor` nếu cần làm tròn xuống |
| `(int) 3.9` cho 3 chứ không 4 | Cast không làm tròn gần nhất | Dùng `Math.round` theo quy tắc thích hợp |
| `(int) Double.NaN` cho 0 | Quy tắc chuyển NaN của Java | Kiểm tra `Double.isNaN` hoặc `isFinite` trước |
| `(int) Double.POSITIVE_INFINITY` cho MAX | Giá trị vượt miền bị kẹp ở cận | Từ chối giá trị không hữu hạn nếu không hợp lệ |
| `(int) 1e20` vẫn có giá trị `int` | Cast bão hòa, không ném lỗi | Kiểm tra miền trước khi chuyển |
| `(byte) 130.9` thành -126 | Chuyển double → int 130 → giữ 8 bit | Kiểm tra cả miền `byte` nếu cần giá trị chính xác |
| `(byte) +Infinity` thành -1 | +Infinity → `Integer.MAX_VALUE` → 8 bit thấp | Hiểu quy trình hai bước và từ chối infinity |
| `(int) 16_777_217f` cho 16_777_216 | `float` đã mất chính xác từ trước | Sử dụng kiểu/định dạng phù hợp nguồn dữ liệu |
| `(int) a + (int) b` khác `(int)(a+b)` | Thời điểm cắt phần lẻ khác nhau | Đặt cast và ngoặc theo đúng yêu cầu |
| Kiểm tra `doubleValue <= Long.MAX_VALUE` không chắc chắn | `Long.MAX_VALUE` đổi sang double thành \(2^{63}\) | Dùng cận chính xác `value < 0x1.0p63` |
| Cast dùng để xác nhận “đầu vào là số nguyên” | Cast im lặng bỏ phần lẻ | Kiểm tra phần lẻ hoặc dùng `intValueExact` |

## 12. Bài tập tự kiểm tra

**Câu 1.** `(int) 8.99` và `(int) -8.99` bằng bao nhiêu?  
**Đáp án:** 8 và -8 vì cả hai đều cắt phần lẻ về 0.

**Câu 2.** `(int) -0.99` có bằng -1 không?  
**Đáp án:** Không. Kết quả là 0.

**Câu 3.** `(int) -2.7` khác `Math.floor(-2.7)` thế nào?  
**Đáp án:** Lần lượt là -2 và -3.0; `floor` làm tròn xuống, cast cắt về 0.

**Câu 4.** Vì sao `(int) Double.NaN` ra 0 nhưng `(int) Double.POSITIVE_INFINITY` ra 2_147_483_647?  
**Đáp án:** JLS quy định NaN thành số nguyên 0; vô cực dương thành giá trị tối đa của kiểu trung gian.

**Câu 5.** `(byte) 130.9` cho kết quả gì?  
**Đáp án:** -126. Đầu tiên cắt về 0 thành `int` 130, tiếp đó thu hẹp về `byte` bằng cách giữ 8 bit thấp.

**Câu 6.** Với `double a = 1.9, b = 2.9`, hai biểu thức `(int)a + (int)b` và `(int)(a+b)` cho gì?  
**Đáp án:** 3 và 4. Cast diễn ra ở hai thời điểm khác nhau.

**Câu 7.** Cast từ `double` sang `int` có ném ngoại lệ khi vượt miền không?  
**Đáp án:** Không. Nó cho cận MIN/MAX thích hợp; nếu cần từ chối dữ liệu, phải kiểm tra trước.

**Câu 8.** `float f = 16_777_217f; (int) f` có chắc bằng 16_777_217 không?  
**Đáp án:** Không. `float` làm tròn giá trị thành 16_777_216 trước khi cast.

**Câu 9.** Tại sao kiểm tra giá trị `double` với cận `Long.MAX_VALUE` cần cẩn thận?  
**Đáp án:** Vì `Long.MAX_VALUE` không biểu diễn chính xác dưới dạng `double` và được làm tròn thành \(2^{63}\), ngoài miền `long`. Cận trên an toàn theo chính sách đầu vào là `value < 0x1.0p63`.

**Câu 10.** Khi bắt buộc dữ liệu thập phân là một số nguyên chính xác, nên dùng gì?  
**Đáp án:** Với dữ liệu `BigDecimal`, dùng `intValueExact()` hoặc `longValueExact()`. Với `double`, kiểm tra tính hữu hạn, miền và có phần lẻ trước khi chuyển, đồng thời hiểu rằng giá trị có thể đã mất chính xác từ nguồn.

## 13. Ghi nhớ và tài liệu tham khảo

- **Trong miền hợp lệ và với số hữu hạn:** `float/double → int/long` bằng cast là **cắt về 0**, cả số dương lẫn số âm.
- **NaN → 0**, **+Infinity/quá lớn → MAX**, **-Infinity/quá nhỏ → MIN** của kiểu trung gian; không tự ném ngoại lệ.
- **Ép sang `byte/short/char` là hai bước:** sang `int` theo quy tắc số thực rồi tiếp tục thu hẹp kiểu số nguyên theo bit.
- **Thời điểm cast rất quan trọng:** `(int)a + (int)b` có thể khác `(int)(a+b)`.
- **Chọn chính sách chuyển đổi theo nhu cầu:** cắt phần lẻ, làm tròn, từ chối dữ liệu ngoài miền hoặc yêu cầu chuyển chính xác là bốn mục tiêu khác nhau.

Nguồn quy tắc ngôn ngữ: [Java Language Specification — Chapter 5, Conversions and Contexts, §5.1.3 Narrowing Primitive Conversion](https://docs.oracle.com/javase/specs/jls/se26/html/jls-5.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-conversions-006">Chuyển NaN, vô cực và số ngoài phạm vi sang kiểu nguyên</a>

<details>
<summary>Xem chi tiết</summary>

# Chuyển NaN, vô cực và số ngoài phạm vi sang kiểu nguyên trong Java

Trong Java, ép kiểu từ số dấu phẩy động (`float`, `double`) sang số nguyên (`byte`, `short`, `int`, `long`; và cả `char`) là **narrowing primitive conversion**. Đây không đơn thuần là “bỏ phần thập phân”: với **`NaN`**, **`+Infinity`**, **`-Infinity`** hoặc giá trị hữu hạn nằm ngoài miền kiểu đích, Java có các quy tắc chuyển đổi xác định.

Ba điều cần ghi nhớ ngay:

1. **Giá trị hữu hạn trong phạm vi:** bỏ phần phân số **hướng về 0**; ví dụ `(int) 3.9 == 3` và `(int) -3.9 == -3`.
2. **Chuyển trực tiếp `float`/`double` sang `int` hoặc `long`:** `NaN` thành `0`; giá trị quá lớn hoặc `+Infinity` thành `MAX_VALUE`; giá trị quá âm hoặc `-Infinity` thành `MIN_VALUE`.
3. **Chuyển sang `byte`, `short`, `char`:** Java **trước hết chuyển sang `int`**, sau đó thu hẹp `int` về kiểu nhỏ bằng cách bỏ các bit cao. Vì thế `(byte) Double.POSITIVE_INFINITY` thành **`-1`**, không phải `Byte.MAX_VALUE`.

Các chuyển đổi nguyên thủy này **không tự ném ngoại lệ do tràn miền**. Nếu chương trình cần từ chối dữ liệu không hữu hạn, vượt miền hoặc có phần lẻ, phải **kiểm tra rõ ràng trước khi cast**.

## 1. Nguồn và đích: chuyển kiểu nào đang được nói đến?

Bài này chỉ xét **giá trị kiểu `float` hoặc `double` khi chuyển sang kiểu nguyên**:

| Kiểu đích | Miền giá trị |
| --- | --- |
| `byte` | -128 đến 127 |
| `short` | -32.768 đến 32.767 |
| `int` | -2.147.483.648 đến 2.147.483.647 |
| `long` | -9.223.372.036.854.775.808 đến 9.223.372.036.854.775.807 |
| `char` | 0 đến 65.535 (đơn vị mã UTF-16, không dấu) |

`char` được đưa vào để hiểu đúng quy tắc chuyển kiểu của Java, nhưng không nên dùng `char` để lưu số lượng hoặc tính toán số nguyên thông thường.

`NaN` (*Not a Number*) và vô cực (`+Infinity`, `-Infinity`) là các giá trị đặc biệt của `float`/`double`, **không phải giá trị nguyên**. `NaN` có thể xuất hiện từ `0.0 / 0.0`, `+Infinity` có thể đến từ `1.0 / 0.0` hoặc overflow dấu phẩy động, `-Infinity` có thể đến từ `-1.0 / 0.0`. Ngoài ra, `Double.MAX_VALUE` tuy là **số hữu hạn** vẫn lớn hơn rất nhiều so với `Long.MAX_VALUE`.

Không nên đánh đồng ba tình huống:

- **Phần lẻ trong miền:** có thể cắt về 0 mà không chạm biên, ví dụ `7.8 → 7`.
- **Ngoài miền:** có thể nhận giá trị giới hạn `MAX_VALUE`/`MIN_VALUE` khi chuyển trực tiếp thành `int`/`long`.
- **`NaN` hoặc vô cực:** là giá trị đặc biệt và được xử lý theo quy tắc riêng; bản thân không chứa một số nguyên hữu hạn cần chuyển.

## 2. Quy tắc chính xác theo Java: chuyển sang `int` hoặc `long`

Theo quy tắc thu hẹp từ dấu phẩy động sang số nguyên của Java, khi kiểu đích là `int` hoặc `long`, có thể hình dung thành ba bước:

1. Nếu nguồn là **`NaN`**, kết quả là **`0`** thuộc kiểu đích.
2. Nếu nguồn là **số hữu hạn khác NaN** và phần nguyên sau khi cắt về 0 nằm trong phạm vi, kết quả là phần nguyên đó.
3. Nếu độ lớn vượt miền (kể cả **`+Infinity`/`-Infinity`**), kết quả **bị chặn tại biên đích**: lớn quá → `MAX_VALUE`, âm quá → `MIN_VALUE`.

Bước 2 sử dụng **truncation toward zero**, không phải làm tròn đến số nguyên gần nhất:

| Biểu thức | Kết quả |
| --- | ---: |
| `(int) 3.9` | `3` |
| `(int) -3.9` | `-3` |
| `(int) 0.99` | `0` |
| `(int) -0.99` | `0` |
| `(int) -0.0` | `0` |
| `(int) Double.NaN` | `0` |
| `(int) Double.POSITIVE_INFINITY` | `2147483647` |
| `(int) Double.NEGATIVE_INFINITY` | `-2147483648` |
| `(int) 1e100` | `2147483647` |
| `(long) -1e100` | `-9223372036854775808` |

**Điểm quan trọng:** `(int) Double.POSITIVE_INFINITY` cho `Integer.MAX_VALUE`, nhưng điều này **không có nghĩa** vô cực là một số nguyên lớn có thể lưu chính xác. Java chỉ quy định một kết quả cast. Dữ liệu ban đầu đã không thể biểu diễn trong `int`.

Tương tự, `(int) 1e100` và `(int) Double.POSITIVE_INFINITY` có thể cùng ra `Integer.MAX_VALUE`. Nhìn giá trị `int` cuối cùng **không thể biết** nó đến từ số hợp lệ, từ số lớn bị chặn hay từ vô cực; vì vậy không nên chỉ dựa vào kết quả sau cast để xác nhận dữ liệu.

Các phép cast này **khác hẳn** việc thu hẹp `long` sang `int`. Với `long → int`, Java bỏ các bit cao nên `(int) 3_000_000_000L` bằng `-1294967296`. Với `double → int`, giá trị nguồn `3_000_000_000.0` lại thành **`2147483647`** do quy tắc chuyển từ dấu phẩy động.

## 3. Vì sao `byte` và `short` xử lý khác `int`?

Với đích là `byte`, `short` hoặc `char`, Java áp dụng quy trình **hai giai đoạn**:

1. Thu hẹp `float`/`double` sang **`int`**, theo quy tắc `NaN → 0`, cắt về 0, chặn biên `int` nếu ngoài miền.
2. Thu hẹp giá trị `int` trung gian sang `byte`, `short` hay `char` bằng cách **giữ lại các bit thấp** phù hợp với kiểu đích.

Đối với số có dấu `byte`/`short`, mẫu bit được diễn giải theo bù hai; với `char` là giá trị không dấu 16 bit. Bước thứ hai **không phải clamp về giới hạn `byte`/`short`**.

Ví dụ chuyển `+Infinity` sang `byte`:

```text
+Infinity
  → chuyển double sang int: Integer.MAX_VALUE = 2147483647 (0x7FFFFFFF)
  → chuyển int sang byte: giữ 8 bit thấp = 0xFF
  → byte có dấu: -1
```

Vì thế:

| Biểu thức | Giá trị cuối |
| --- | ---: |
| `(byte) Double.NaN` | `0` |
| `(short) Double.NaN` | `0` |
| `(byte) Double.POSITIVE_INFINITY` | `-1` |
| `(byte) Double.NEGATIVE_INFINITY` | `0` |
| `(short) Double.POSITIVE_INFINITY` | `-1` |
| `(short) Double.NEGATIVE_INFINITY` | `0` |
| `(byte) 130.9` | `-126` |
| `(byte) 1e100` | `-1` |
| `(char) Double.POSITIVE_INFINITY` | ký tự có mã `65535` |

Ví dụ `(byte) 130.9`: bước 1 cắt `130.9` thành `int 130` (vẫn trong miền `int`), bước 2 giữ 8 bit thấp của `130` là `0x82`, diễn giải dạng `byte` có dấu thành **-126**. Đây là hai quy tắc liên tiếp, **không phải** làm tròn `130.9` xuống -126 theo số học.

**Không suy luận** rằng ép từ `double` sang `byte` luôn “saturate tại ±128”. Kết quả tùy **bit của `int` trung gian**; với `+Infinity`, kết quả `byte` âm là hoàn toàn hợp lệ theo đặc tả Java.

## 4. Chuyển NaN: tại sao ra 0 mà không báo lỗi?

`Double.NaN` và `Float.NaN` đại diện cho trạng thái *Not a Number*. Với phép narrowing floating-point → integral, Java quy định `NaN` thành 0 ở bước chuyển sang `int` hoặc `long`. Nếu đích là `byte`, `short` hay `char`, `int 0` trung gian cũng cho kết quả 0.

```java
int a = (int) Double.NaN;     // 0
long b = (long) Float.NaN;    // 0L
byte c = (byte) Double.NaN;   // 0
```

Điểm nguy hiểm là **0 có thể đồng thời là dữ liệu nghiệp vụ hợp lệ**. Nếu hệ thống nhận một giá trị đo không hợp lệ là `NaN` rồi cast thẳng sang `int`, kết quả `0` có thể bị hiểu sai thành “không có số lượng”, “nhiệt độ bằng 0” hoặc một mã trạng thái hợp lệ.

Đừng dùng `value == Double.NaN` để phát hiện NaN: `NaN` không bằng chính nó trong phép so sánh dấu phẩy động. Dùng `Double.isNaN(value)` hoặc `Float.isNaN(value)`.

`Double.isFinite(value)`/`Float.isFinite(value)` thuận tiện hơn nếu **mọi giá trị đặc biệt** (NaN và cả ±Infinity) đều phải bị từ chối.

## 5. Vô cực: quy tắc ép kiểu khác phép chia cho 0

`Double.POSITIVE_INFINITY` và `Double.NEGATIVE_INFINITY` là các giá trị hợp lệ của kiểu `double` theo ngữ nghĩa IEEE 754. Phép chia `1.0 / 0.0` trả `+Infinity`, khác với phép chia số nguyên `1 / 0`, vốn ném `ArithmeticException` khi thực hiện.

```java
double positive = Double.POSITIVE_INFINITY;
double negative = Double.NEGATIVE_INFINITY;

int max = (int) positive;  // Integer.MAX_VALUE
int min = (int) negative;  // Integer.MIN_VALUE

long longMax = (long) positive; // Long.MAX_VALUE
long longMin = (long) negative; // Long.MIN_VALUE
```

Trong phép cast primitive này Java không ném `ArithmeticException`. Thay vào đó, giá trị vô cực **bị chặn** ở biên của `int`/`long` trước khi có thể tiếp tục thu hẹp sang kiểu nhỏ.

Tuy nhiên, có sự khác biệt giữa **giá trị nguồn trước cast** và **giá trị được chọn sau cast**. Một chương trình cần giữ ý nghĩa “nguồn bị lỗi/vô cực” phải kiểm tra nguồn **trước khi chuyển kiểu**; sau cast, thông tin rằng nó từng là vô cực đã biến mất.

## 6. Số hữu hạn ngoài miền vẫn bị chặn: không cần NaN hay Infinity

Một `double` có thể hữu hạn và lớn hơn `Integer.MAX_VALUE` nhiều lần. Ví dụ `1e100` là hữu hạn, nhưng tích cực lớn hơn cả `Long.MAX_VALUE`:

```java
double big = 1e100;

int toInt = (int) big;   // 2147483647
long toLong = (long) big; // 9223372036854775807
```

Số âm ngoài miền cũng bị chặn ở biên âm:

```java
double small = -1e100;

int toInt = (int) small;   // -2147483648
long toLong = (long) small; // -9223372036854775808
```

Với giá trị chỉ hơi ngoài miền `int`:

```java
double d = 2_147_483_648.0; // Integer.MAX_VALUE + 1, biểu diễn chính xác bằng double
int converted = (int) d;    // 2147483647, không phải -2147483648
```

Điều này khác với cast `(int) 2_147_483_648L` từ `long`: cast `long → int` cắt bit, cho `-2147483648`. Với cast từ `double`, Java **chặn ở `Integer.MAX_VALUE`**. Phân biệt **kiểu nguồn** rất quan trọng trước khi dự đoán kết quả.

## 7. Chương trình Java 17+ chạy độc lập

Tạo tệp `FloatToIntegerDemo.java`:

```java
public class FloatToIntegerDemo {
    static int checkedInt(double v) {
        if (!Double.isFinite(v)
                || v < Integer.MIN_VALUE
                || v > Integer.MAX_VALUE
                || v != Math.rint(v)) {
            throw new IllegalArgumentException("not an in-range integer");
        }
        return (int) v;
    }

    public static void main(String[] args) {
        System.out.println("3.9 -> int: " + (int) 3.9);
        System.out.println("-3.9 -> int: " + (int) -3.9);
        System.out.println("-0.0 -> int: " + (int) -0.0);
        System.out.println("NaN -> int: " + (int) Double.NaN);
        System.out.println("+Infinity -> int: " + (int) Double.POSITIVE_INFINITY);
        System.out.println("-Infinity -> int: " + (int) Double.NEGATIVE_INFINITY);
        System.out.println("1e100 -> int: " + (int) 1e100);
        System.out.println("-1e100 -> long: " + (long) -1e100);
        System.out.println("+Infinity -> long: " + (long) Double.POSITIVE_INFINITY);
        System.out.println("+Infinity -> byte: " + (byte) Double.POSITIVE_INFINITY);
        System.out.println("-Infinity -> byte: " + (byte) Double.NEGATIVE_INFINITY);
        System.out.println("NaN -> short: " + (short) Double.NaN);
        System.out.println("130.9 -> byte: " + (byte) 130.9);
        System.out.println("1e100 -> byte: " + (byte) 1e100);
        System.out.println("1e100 -> char code: " + (int) (char) 1e100);
        System.out.println("NaN finite? " + Double.isFinite(Double.NaN));

        System.out.println("checkedInt(7.0): " + checkedInt(7.0));
        try {
            checkedInt(7.5);
        } catch (IllegalArgumentException ex) {
            System.out.println("checkedInt(7.5): rejected");
        }
        try {
            checkedInt(Double.POSITIVE_INFINITY);
        } catch (IllegalArgumentException ex) {
            System.out.println("checkedInt(Infinity): rejected");
        }
        try {
            new java.math.BigDecimal("2147483648").intValueExact();
        } catch (ArithmeticException ex) {
            System.out.println("BigDecimal out of range: rejected");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac FloatToIntegerDemo.java
java FloatToIntegerDemo
```

Kết quả stdout (các giá trị đã được kiểm chứng trên JDK):

```text
3.9 -> int: 3
-3.9 -> int: -3
-0.0 -> int: 0
NaN -> int: 0
+Infinity -> int: 2147483647
-Infinity -> int: -2147483648
1e100 -> int: 2147483647
-1e100 -> long: -9223372036854775808
+Infinity -> long: 9223372036854775807
+Infinity -> byte: -1
-Infinity -> byte: 0
NaN -> short: 0
130.9 -> byte: -126
1e100 -> byte: -1
1e100 -> char code: 65535
NaN finite? false
checkedInt(7.0): 7
checkedInt(7.5): rejected
checkedInt(Infinity): rejected
BigDecimal out of range: rejected
```

Trong ví dụ này:

- Các dòng `3.9`, `-3.9` và `-0.0` chứng minh phép chuyển sang `int` **cắt về 0**, bao gồm cả việc mất dấu của `-0.0`.
- Các dòng `NaN`, `Infinity` và `1e100` chứng minh **0 hoặc chặn biên** khi đích trực tiếp là `int`/`long`.
- Các dòng `byte` và `char` chứng minh **giai đoạn chuyển qua `int` rồi bỏ bit cao**.
- Hàm `checkedInt` minh họa **phát hiện dữ liệu ngoài miền, không hữu hạn hoặc không nguyên** thay vì âm thầm làm mất thông tin.
- `BigDecimal.intValueExact()` minh họa một API ném ngoại lệ khi số thập phân chính xác không thể chuyển thành số nguyên đích mà vẫn giữ giá trị.

## 8. Chuyển đổi có kiểm tra: từ chối mất dữ liệu thay vì chấp nhận cast

Nếu nghiệp vụ cần chuyển một phép đo `double` sang `int` và **chỉ chấp nhận số nguyên hữu hạn nằm trong miền**, hãy kiểm tra đủ ba điều kiện **trước khi cast**:

1. **Hữu hạn:** `Double.isFinite(v)` để loại `NaN` và ±Infinity.
2. **Nằm trong phạm vi:** `Integer.MIN_VALUE <= v && v <= Integer.MAX_VALUE` (các giới hạn này đều biểu diễn chính xác bằng `double`).
3. **Không có phần lẻ:** `v == Math.rint(v)`. `Math.rint` trả giá trị `double` nguyên gần nhất theo quy tắc làm tròn của nó; dùng **để so sánh xem `v` đã nguyên chưa**, không dùng kết quả rint để âm thầm thay đổi dữ liệu.

Hàm `checkedInt` trong chương trình đầy đủ thực hiện cả ba kiểm tra. Với nguồn `NaN`, điều kiện `Double.isFinite` đặc biệt quan trọng: các so sánh kiểu `v < min`, `v > max` hoặc `v != ...` cần được hiểu đúng với NaN. Mã kiểm tra thường nên có nhánh `isFinite` rõ ràng để tránh vô tình chấp nhận NaN.

Nếu yêu cầu **cho phép cắt phần lẻ** (ví dụ từ 7.9 thành 7), bỏ điều kiện `Math.rint`, nhưng vẫn kiểm tra `isFinite` và miền **trước** phép cast. Nếu yêu cầu **làm tròn** theo chính sách (nearest, floor, ceiling), áp dụng quy tắc làm tròn được chọn rồi **kiểm tra miền của kết quả** trước khi chuyển.

### Kiểm tra riêng cho `long`: biên dương cần lưu ý

Kiểm tra một `double` để cast an toàn sang `long` phức tạp hơn nhìn qua, vì **`Long.MAX_VALUE` không được biểu diễn chính xác bằng `double`**. Khi chuyển `Long.MAX_VALUE` sang `double`, giá trị được làm tròn thành **`2^63`**, là số nằm **ngoài miền `long`**. Vì vậy biểu thức:

```java
// KHÔNG phải phép kiểm tra đầy đủ đối với double:
if (v <= Long.MAX_VALUE) {
    // Có thể chấp nhận cả v == 2^63 do Long.MAX_VALUE bị làm tròn lên.
}
```

không đủ nghiêm ngặt cho biên dương. Với nguồn `double`, có thể kiểm tra miền bằng hai ngưỡng biểu diễn chính xác là **`-2^63`** (được phép) và **`+2^63`** (phải từ chối):

```java
static long checkedLong(double v) {
    if (!Double.isFinite(v)
            || v < -0x1.0p63   // v nhỏ hơn Long.MIN_VALUE
            || v >= 0x1.0p63   // v lớn hơn hoặc bằng 2^63
            || v != Math.rint(v)) {
        throw new IllegalArgumentException("not an in-range integer");
    }
    return (long) v;
}
```

Đây là **đoạn phương thức minh họa**, cần đặt trong một class khi dùng thực tế. Các literal hexa dấu phẩy động `0x1.0p63` biểu diễn chính xác `2^63` dưới dạng `double`. Vì vậy `checkedLong` chấp nhận `-2^63`, nhưng từ chối `+2^63`. Điều kiện `Math.rint` từ chối giá trị có phần lẻ biểu diễn được.

Cần biết thêm: nguồn `double` đã **có độ chính xác hữu hạn**. Ngay cả khi giá trị sau kiểm tra là một số nguyên, không thể suy ra mọi chữ số của một số nguyên đầu vào rất lớn ban đầu còn nguyên; một phép chuyển `long → double` trước đó có thể đã làm tròn. **Kiểm tra sau cùng không phục hồi thông tin đã mất trước khi chuyển.**

### Khi đầu vào là dữ liệu thập phân chính xác

Nếu nhập từ JSON/chuỗi thập phân và cần phân biệt số có phần lẻ, vượt miền, hoặc yêu cầu giữ chính xác, cân nhắc **`BigDecimal` từ chuỗi**:

```java
int valid = new java.math.BigDecimal("2147483647.0").intValueExact();
// 2147483647

// new java.math.BigDecimal("2147483648").intValueExact();
// ArithmeticException: vượt miền int.

// new java.math.BigDecimal("7.5").intValueExact();
// ArithmeticException: có phần lẻ.
```

`BigDecimal.intValueExact()` và `longValueExact()` kiểm tra khả năng chuyển **không mất giá trị**. Nhưng `BigDecimal` từ chuỗi thập phân hữu hạn **không đại diện trực tiếp cho NaN hoặc vô cực**; dữ liệu đặc biệt phải được xử lý theo quy tắc xác thực đầu vào. Tạo `BigDecimal` từ một `double` đã bị làm tròn cũng không thể khôi phục chữ số nguyên ban đầu bị mất.

## 9. Phân biệt cast, `Math.round` và `Math.toIntExact`

| Cách chuyển | Hành vi chủ yếu | Có báo lỗi do không vừa phạm vi? |
| --- | --- | --- |
| `(int) someDouble` | Cắt phần lẻ về 0; NaN → 0; quá miền → biên `int` | Không |
| `(long) someDouble` | Tương tự, dùng miền `long` | Không |
| `(byte) someDouble` | Qua `int` rồi bỏ bit cao xuống 8 bit | Không |
| `Math.round(double)` | Làm tròn về số nguyên gần nhất theo quy tắc của API; trả `long` | Không; NaN/±Infinity được xử lý đặc biệt |
| `Math.round(float)` | Làm tròn về số nguyên gần nhất theo quy tắc của API; trả `int` | Không; NaN/±Infinity được xử lý đặc biệt |
| `Math.toIntExact(long)` | Chuyển `long` sang `int` và kiểm tra miền | **Có**: `ArithmeticException` nếu vượt miền |
| `BigDecimal.intValueExact()` | Chuyển giá trị thập phân chính xác sang `int`, kiểm tra phần lẻ/miền | **Có**: `ArithmeticException` nếu không chính xác |

`Math.round` là **phép làm tròn**, không đồng nghĩa với cast cắt về 0: `(int) 3.9 == 3`, nhưng `Math.round(3.9) == 4L`. Với NaN, `Math.round` trả 0; với ±Infinity, trả biên nguyên của kiểu kết quả. Phải chọn API theo **chính sách làm tròn** mong muốn.

`Math.toIntExact` **không có overload nhận `double`**. Viết `Math.toIntExact((long) value)` có thể đã **chặn giá trị ở miền `long` hoặc cắt phần lẻ trước khi kiểm tra**. Điều này không thay thế bước xác thực nguồn `double`.

Khi giải bài toán tiền tệ, mã giao dịch, phân trang, số lượng hoặc số đo có giới hạn, nên xác định rõ liệu giá trị `NaN`/Infinity có thể xuất hiện hay không, có cho phép phần lẻ hay không, miền hợp lệ là gì và chính sách làm tròn nào được phép. Cast đơn giản chỉ phù hợp khi đã chấp nhận ngữ nghĩa mất dữ liệu của Java.

## 10. Những nhầm lẫn thường gặp

1. **“Cast `double` sang `int` sẽ quấn vòng như cast `long` sang `int`.”** Sai. `double → int` chặn biên khi vượt miền, còn `long → int` bỏ bit cao.
2. **“`NaN` luôn ném lỗi khi cast thành số nguyên.”** Sai: narrowing primitive chuyển NaN thành 0.
3. **“`+Infinity` chuyển sang `byte` bằng `127`.”** Sai: bước `double → int` cho `Integer.MAX_VALUE`, sau đó `int → byte` cho `-1`.
4. **“`(int) -3.9` bằng -4.”** Sai: cắt về 0 nên bằng -3; đó không phải `Math.floor`.
5. **“Vượt phạm vi cast sẽ tự báo `ArithmeticException`.”** Sai: primitive cast dùng quy tắc chuyển đổi, không tự ném lỗi do vượt miền.
6. **“Kiểm tra `v <= Long.MAX_VALUE` bằng `double` là đủ.”** Sai ở biên dương vì `Long.MAX_VALUE` được làm tròn thành `2^63` khi so với `double`.
7. **“Chỉ cần kiểm tra `v < min || v > max` để bắt mọi giá trị xấu.”** Sai: NaN có so sánh đặc biệt; hãy xác thực hữu hạn rõ ràng.
8. **“Sau cast không thấy Infinity thì dữ liệu đã hợp lệ.”** Sai: nhiều nguồn rất khác nhau cùng tạo ra `MAX_VALUE`, `MIN_VALUE` hoặc 0.
9. **“`Math.round` và cast đều bỏ phần thập phân về 0.”** Sai: `Math.round` thực hiện làm tròn.
10. **“`BigDecimal.valueOf(double)` tự phục hồi chữ số đã mất.”** Sai: việc chuyển đổi một số nguyên lớn qua `double` có thể làm tròn trước đó; thông tin bị mất không thể lấy lại.

## 11. Tự kiểm tra

- `(int) Double.NaN` bằng gì? **0**.
- `(long) Double.NEGATIVE_INFINITY` bằng gì? **Long.MIN_VALUE**.
- `(byte) Double.POSITIVE_INFINITY` bằng gì? **-1**, vì chuyển qua `int` rồi giữ 8 bit thấp.
- `(int) -8.99` bằng gì? **-8**, do cắt phần lẻ về 0.
- `(int) 3_000_000_000.0` bằng gì? **Integer.MAX_VALUE**, không phải kết quả cast từ `long`.
- `Math.toIntExact((long) Double.NaN)` có phát hiện NaN không? **Không**: cast sang `long` đã cho 0 trước khi `toIntExact` nhận được đối số.
- Muốn chấp nhận *chỉ* các giá trị nguyên hữu hạn trong miền `int`, cần làm gì? **Kiểm tra `Double.isFinite`, min/max và không có phần lẻ trước khi cast**, hoặc dùng giải pháp thập phân chính xác phù hợp nguồn dữ liệu.

**Ghi nhớ:** chuyển `float`/`double` sang `int`/`long` là **cắt về 0, NaN → 0, vượt miền/±Infinity → biên**. Chuyển sang `byte`/`short`/`char` còn có **bước thu hẹp từ `int`**, nên kết quả có thể âm hoặc bất ngờ. Nếu dữ liệu phải chính xác và hợp lệ, **xác thực trước khi chuyển**, thay vì kiểm tra giá trị đã mất thông tin sau cast.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-conversions-007">Thu hẹp double sang float và mất độ chính xác</a>

<details>
<summary>Xem chi tiết</summary>

# Thu hẹp `double` sang `float` và mất độ chính xác trong Java

Trong Java, `double` và `float` đều là kiểu dấu phẩy động nhị phân theo IEEE 754, nhưng **khác độ rộng và miền biểu diễn**. Ép `double` sang `float` là *narrowing primitive conversion* (chuyển kiểu nguyên thủy thu hẹp). Phép ép được phép với cú pháp `(float)`, nhưng có thể **làm tròn, mất bit chính xác, mất khả năng phân biệt hai giá trị, thành vô cực hoặc thành 0**.

```java
double original = 0.1;
float narrowed = (float) original;
double widenedAgain = narrowed;

System.out.println(original);             // 0.1
System.out.println(narrowed);             // 0.1
System.out.println(widenedAgain);         // 0.10000000149011612
System.out.println(original == widenedAgain); // false
```

Bề ngoài cả hai dòng đầu đều in `0.1`, nhưng chúng **không chứa cùng một giá trị nhị phân**. Phần dưới sẽ giải thích điều gì xảy ra, khi nào an toàn, cách phát hiện mất thông tin và cách chọn kiểu cho bài toán thực tế.

## 1. `double` và `float` khác nhau thế nào?

| Thuộc tính | `float` (binary32) | `double` (binary64) |
| --- | --- | --- |
| Dung lượng biểu diễn | 32 bit | 64 bit |
| Bit dấu | 1 | 1 |
| Bit số mũ lưu trữ | 8 | 11 |
| Bit phần fraction lưu trữ | 23 | 52 |
| Độ chính xác significand với bit ẩn ở số chuẩn hóa | 24 bit | 53 bit |
| Số chữ số thập phân chính xác thường dùng để ước lượng | Khoảng 6–7 chữ số có nghĩa | Khoảng 15–16 chữ số có nghĩa |
| Số dương hữu hạn lớn nhất | Khoảng `3.4028235 × 10^38` | Khoảng `1.7976931348623157 × 10^308` |
| Số dương chuẩn hóa nhỏ nhất | `Float.MIN_NORMAL`, khoảng `1.17549435 × 10^-38` | `Double.MIN_NORMAL`, khoảng `2.2250738585072014 × 10^-308` |
| Số dương khác 0 nhỏ nhất (subnormal) | `Float.MIN_VALUE`, khoảng `1.4 × 10^-45` | `Double.MIN_VALUE`, khoảng `4.9 × 10^-324` |

**Chú ý:** `Float.MIN_VALUE` **không phải** số âm có độ lớn lớn nhất. Nó là **số dương khác 0 nhỏ nhất** mà `float` biểu diễn được. Muốn giá trị âm hữu hạn có độ lớn lớn nhất, dùng `-Float.MAX_VALUE`. Hai kiểu còn có `+0.0`, `-0.0`, `NaN`, `+Infinity` và `-Infinity`.

`float` không đơn giản là “`double` nhưng in ít chữ số hơn”. Nó chỉ có 24 bit significand và miền số mũ nhỏ hơn nhiều. Khi đổi từ binary64 sang binary32, Java phải chọn một giá trị có thể biểu diễn trong **tập số `float`**, chứ không được giữ nguyên mọi bit có trong `double`.

## 2. Cú pháp: vì sao cần cast tường minh?

Chuyển từ `float` lên `double` là *widening primitive conversion*: Java cho phép ngầm. Chuyển từ `double` xuống `float` là thu hẹp: ở gán biến, cần cast rõ ràng.

```java
float measurement = 12.5f;
double widened = measurement; // Được phép ngầm.

double source = 12.5;
// float invalid = source;     // Lỗi biên dịch: possible lossy conversion.
float narrowed = (float) source; // Hợp lệ.
```

Với literal, `1.25` mặc định là `double`, còn hậu tố `f` hoặc `F` chọn literal `float`:

```java
float a = 1.25f;       // Literal float.
float b = (float) 1.25; // Ép literal double sang float.
double c = 1.25;       // Literal double.
```

Hậu tố `f` **khác** với việc bỏ qua mất chính xác: nó nói rằng literal được xét như giá trị `float`. Ví dụ `0.1f` vẫn là xấp xỉ nhị phân của 0.1, không phải 0.1 toán học chính xác.

### Khi cast xuất hiện gián tiếp

- Khi **truyền tham số**: phương thức nhận `float` không tự chấp nhận một biến `double`.
- Khi **trả kết quả**: phương thức khai báo trả `float` không thể `return` một biến `double` mà không cast.
- Khi **gán phần tử mảng** `float[]` hoặc trường `float`: cần chuyển hẹp.
- Trong **toán tử gán kết hợp** như `+=`, Java có quy tắc chuyển kiểu ngầm có thể che bớt sự mất chính xác.

```java
float sum = 0.1f;
double step = 0.2;

// sum = sum + step; // Không biên dịch: biểu thức float + double có kiểu double.
sum += step;         // Biên dịch; về ngữ nghĩa gần như sum = (float)(sum + step).
```

Quy tắc gán kết hợp đặc biệt này không bảo đảm kết quả chính xác. Tránh dùng nó để che khuất một quyết định thu hẹp trong phép tính tài chính hoặc thuật toán nhạy với sai số.

## 3. Java làm gì khi chuyển `double` sang `float`?

Theo quy tắc chuyển kiểu chấm động, Java chuyển một giá trị `double` về biểu diễn `float`:

1. Nếu là **NaN**, kết quả vẫn là `float` NaN.
2. Nếu là **+Infinity** hoặc **-Infinity**, kết quả giữ vô cực tương ứng.
3. Nếu là **+0.0** hoặc **-0.0**, kết quả giữ dấu của zero.
4. Nếu là giá trị **hữu hạn**, Java làm tròn về số `float` biểu diễn được gần nhất, theo *round-to-nearest, ties-to-even*; nếu vượt ngưỡng phía lớn, kết quả có thể là vô cực; nếu quá gần 0, có thể là số subnormal hoặc zero có dấu.

Phép thu hẹp số thực thông thường **không ném `ArithmeticException` vì tràn hoặc mất độ chính xác**. Kiểu đích có các giá trị `Infinity` và `0.0` để nhận kết quả làm tròn tương ứng.

Với giá trị nằm trong miền bình thường, mất chính xác có nghĩa hai giá trị `double` khác nhau có thể cùng chuyển thành một `float`. Điều này **không phải lỗi của cú pháp cast**; đó là giới hạn của biểu diễn 32 bit.

### Tại sao một số giá trị chuyển vẫn chính xác?

Những số như `0.5`, `1.25`, `12.5` có khai triển nhị phân hữu hạn ngắn, nên `float` có thể biểu diễn chúng chính xác. Nhưng `0.1` có khai triển nhị phân vô hạn tuần hoàn; cả `double` lẫn `float` chỉ lưu được giá trị xấp xỉ.

Còn các số nguyên: `float` với significand 24 bit biểu diễn **mọi số nguyên** có độ lớn tới `2^24 = 16_777_216`, nhưng không biểu diễn được **mọi số nguyên** lớn hơn mốc này. Một số nguyên lớn hơn vẫn có thể biểu diễn chính xác nếu phù hợp khoảng cách giữa các số `float` liền kề.

## 4. Mất chính xác dễ nhận thấy: `0.1` và 16.777.217

### 4.1. `0.1`: kết quả in không thể hiện hết bit

```java
double source = 0.1d;
float smaller = (float) source;

System.out.println(source);           // 0.1
System.out.println(smaller);          // 0.1
System.out.println((double) smaller); // 0.10000000149011612
```

`System.out.println(float)` dùng cách biểu diễn thập phân có thể chuyển ngược về đúng giá trị `float`. Dòng `0.1` **không chứng minh** biến đang lưu đúng 0.1 thập phân. Khi chuyển `smaller` trở lại `double`, mọi bit của **giá trị `float` hiện tại** được giữ chính xác trong `double`, nên sự khác biệt thành dễ thấy.

- `source`: xấp xỉ 0.1 theo binary64.
- `smaller`: xấp xỉ 0.1 theo binary32.
- `(double) smaller`: diễn đạt chính xác giá trị binary32 đó trong binary64.

**Ép ngược `float -> double` không khôi phục các bit đã mất từ `source`.** Đây là sự khác biệt giữa “mở rộng chỗ chứa” và “phục hồi thông tin”.

### 4.2. Số nguyên 16.777.217 mất một đơn vị

```java
double original = 16_777_217.0;
float rounded = (float) original;

System.out.println(original); // 1.6777217E7
System.out.println(rounded);  // 1.6777216E7
```

`double` có đủ significand để biểu diễn chính xác 16.777.217. `float` ở vùng giá trị này có khoảng cách 2 giữa các số biểu diễn liền nhau; 16.777.217 nằm giữa 16.777.216 và 16.777.218. Với *ties-to-even*, kết quả được chọn là **16.777.216**.

Điểm cốt lõi không phải cứ vượt mốc 16.777.216 là mọi giá trị sai, mà là **độ phân giải của `float` thưa dần khi độ lớn tăng**.

## 5. Khái niệm ULP và độ phân giải tại một độ lớn

*ULP* (*unit in the last place*) mô tả khoảng cách giữa các số biểu diễn liên tiếp trong một vùng. Java có `Math.ulp(float)` và `Math.ulp(double)`.

```java
System.out.println(Math.ulp(1.0f)); // 1.1920929E-7
System.out.println(Math.ulp(1.0d)); // 2.220446049250313E-16
```

Gần 1.0, bước lượng tử của `float` vào khoảng 0.000000119, còn `double` nhỏ hơn nhiều. Khi giá trị lớn, ULP lớn dần; không có một con số “độ chính xác tuyệt đối” cố định cho mọi vùng.

Ví dụ:

```java
float a = 16_777_216f;
float b = a + 1.0f;
System.out.println(a == b); // true: cộng 1 không đổi được giá trị float tại đây.
```

Đây không phải lỗi toán tử `+`. Kết quả toán học nằm giữa hai số `float` biểu diễn được và làm tròn về một trong hai. Với phép tích lũy số lượng lớn, nhiều phép làm tròn có thể làm sai số tăng hoặc phụ thuộc thứ tự cộng.

### Bao nhiêu chữ số thập phân có thể tin tưởng?

Cụm “`float` khoảng 7 chữ số, `double` khoảng 15 chữ số” chỉ là **ước lượng độ chính xác**, không phải cam kết rằng *bất kỳ* 7 chữ số sau dấu phẩy đều đúng. Số chữ số **có nghĩa** khác với số chữ số **sau dấu thập phân**; sai số phụ thuộc độ lớn, phép tính và khoảng cách giữa các số biểu diễn.

Nếu dữ liệu cần phân biệt hai giá trị cách nhau 0.001 ở vùng hàng tỷ, `float` thường không phù hợp. Hãy tính cận trên của dữ liệu và độ phân giải cần thiết theo đơn vị thực tế.

## 6. Vượt miền: vô cực, subnormal và zero

Độ chính xác không phải rủi ro duy nhất. `float` có miền số mũ hẹp hơn `double`.

### 6.1. Overflow thành vô cực

```java
double huge = 1.0e40;       // double hữu hạn.
float f = (float) huge;     // +Infinity.

System.out.println(f);                    // Infinity
System.out.println(Float.isInfinite(f));  // true
```

`1.0e40` nhỏ hơn `Double.MAX_VALUE` nhưng lớn hơn rất nhiều so với `Float.MAX_VALUE`. Cast không tự động kẹp về `Float.MAX_VALUE` và không ném exception.

Để kiểm tra một kết quả có là số hữu hạn hay không, dùng `Float.isFinite(f)`; muốn biết riêng vô cực dùng `Float.isInfinite(f)`.

### 6.2. Underflow: mất thành 0 hoặc số subnormal

```java
double tiny = 1.0e-50;
float zero = (float) tiny;
System.out.println(zero); // 0.0

double small = 1.0e-40;
float subnormal = (float) small;
System.out.println(subnormal); // 1.0E-40
```

`1.0e-50` quá gần 0 để có `float` khác 0 theo quy tắc làm tròn, nên kết quả là `0.0f`. `1.0e-40` vẫn có thể thành một giá trị `float` **subnormal** (nhỏ hơn `Float.MIN_NORMAL` nhưng khác 0), song thường mất nhiều chữ số chính xác tương đối.

Nói cách khác, underflow không nhất thiết luôn cho 0 ngay: có một vùng subnormal, đổi lại độ chính xác tương đối giảm dần khi tiến tới 0.

### 6.3. NaN và hai số 0 có dấu

```java
float notANumber = (float) Double.NaN;
System.out.println(Float.isNaN(notANumber)); // true

float negativeZero = (float) -0.0d;
System.out.println(1.0f / negativeZero); // -Infinity
```

`0.0f == -0.0f` là `true` theo phép so sánh số học, nhưng dấu 0 có thể ảnh hưởng phép chia và một số API bit-level. `NaN` không so sánh bằng chính nó theo `==`; cần dùng `Float.isNaN` thay vì `value == Float.NaN`.

## 7. Mất thông tin có thể lan qua biểu thức hỗn hợp

Java thực hiện *binary numeric promotion* khi hai toán hạng số học có kiểu khác nhau: nếu có `double`, biểu thức thường được tính bằng `double`. Nhưng **chuyển sang `double` sau khi `float` đã mất thông tin không khôi phục giá trị gốc**.

```java
float f = (float) 0.1d;
double result = f + 0.2d;
System.out.println(result); // 0.30000000149011613
```

Ở phép `+` trên, `f` được mở rộng sang `double` rồi cộng với `0.2d`. Giá trị của `f` vẫn là xấp xỉ binary32 của 0.1. Nếu cần chuỗi tính toán dùng `double` từ đầu, tránh ép xuống `float` ở bước trung gian.

### Khi gọi phương thức và API

```java
static float narrowMeasurement(double input) {
    return (float) input; // Quyết định có thể mất dữ liệu được ghi rõ.
}
```

Đây là phương thức minh họa, cần đặt trong một lớp. Khi API bên ngoài yêu cầu `float` (ví dụ buffer đồ họa, mạng hoặc thư viện xử lý dữ liệu), hãy đặt cast tại **ranh giới chuyển giao dữ liệu** và kiểm tra giới hạn ở đó. Tránh đổi toàn bộ nội bộ ứng dụng sang `float` chỉ vì lớp lưu trữ đích dùng `float`.

Đặc biệt với `float[]`, việc lưu một chuỗi `double` đã chuyển hẹp đồng nghĩa toàn bộ mẫu trong mảng bị giới hạn bởi độ chính xác binary32, kể cả khi sau đó đọc lên dưới dạng `double`.

## 8. Làm sao biết việc thu hẹp có “an toàn” không?

Từ “an toàn” có thể mang ba nghĩa khác nhau:

1. **Không thành NaN/vô cực hoặc 0 bất ngờ**: yêu cầu giới hạn miền và giá trị hữu hạn.
2. **Giữ chính xác cùng giá trị số học**: kiểm tra chuyển qua `float` rồi mở rộng lại có bằng giá trị ban đầu hay không, với lưu ý xử lý đặc biệt NaN và zero có dấu.
3. **Sai số nằm trong mức chấp nhận của nghiệp vụ**: cần dung sai tuyệt đối/tương đối, đơn vị dữ liệu và kiểm thử mô hình; đây là tiêu chí thực tế của nhiều thuật toán số.

### 8.1. Kiểm tra giá trị hữu hạn và trường hợp đổi thành vô cực

```java
static float requireFiniteFloat(double value) {
    if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("Input must be finite");
    }
    float narrowed = (float) value;
    if (!Float.isFinite(narrowed)) {
        throw new IllegalArgumentException("Outside finite float range");
    }
    return narrowed;
}
```

Phương thức này loại **đầu vào không hữu hạn** và kết quả **overflow thành Infinity**. Nó **không** bảo đảm giữ chính xác tất cả bit và **không** loại underflow về 0 hoặc làm tròn.

Nếu không muốn từ chối `NaN` hoặc vô cực theo nghiệp vụ, cần xác định hợp đồng cho chúng thay vì mặc định “mọi NaN đều sai”.

### 8.2. Kiểm tra có mất giá trị số học hay không

```java
static float requireExactFiniteFloat(double value) {
    if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("Input must be finite");
    }
    float narrowed = (float) value;
    if (!Float.isFinite(narrowed) || (double) narrowed != value) {
        throw new IllegalArgumentException("Not exactly representable as float");
    }
    return narrowed;
}
```

Với các giá trị **hữu hạn** mà sự bằng nhau số học là tiêu chí, so sánh round-trip như trên kiểm tra được việc làm tròn có đổi giá trị không. Ví dụ:

- `1.25d` chuyển chính xác sang `float`.
- `0.1d` không giữ được nguyên giá trị `double` khi chuyển.
- `16_777_217d` cũng không giữ được.
- `1.0e40` thành vô cực và bị từ chối.
- `1.0e-50` thành 0, khác giá trị ban đầu và bị từ chối.

Điều kiện `==` trên số hữu hạn chấp nhận cả hai trường hợp zero cùng giá trị số học: `+0.0 == -0.0`. Nếu hợp đồng cần phân biệt **bit dấu zero**, phải kiểm tra thêm với `Double.doubleToRawLongBits`/`Float.floatToRawIntBits` sau khi xác định cách đối chiếu; không dùng chỉ `==` để kiểm soát điều này.

### 8.3. Chấp nhận sai số theo yêu cầu thực tế

Không phải ứng dụng nào cũng cần giữ nguyên mọi giá trị `double`. Với dữ liệu tọa độ hiển thị, cảm biến hoặc ảnh, sai số nhỏ có thể chấp nhận; với tài chính, kiểm toán hay dữ liệu định danh, làm tròn như vậy có thể không phù hợp.

Một quy trình thực tế:

1. Định nghĩa miền giá trị (ví dụ `-1_000..1_000`) và độ phân giải cần thiết (ví dụ nhỏ hơn 0,0001 đơn vị).
2. Thử cast tại các điểm biên và giá trị đại diện.
3. Tính sai số `Math.abs(original - (double) narrowed)` khi cả hai hữu hạn, rồi so với ngân sách sai số do nghiệp vụ quy định.
4. Nếu gần 0 hoặc trên nhiều bậc độ lớn, cân nhắc tiêu chí sai số tuyệt đối kết hợp tương đối, tránh chia cho 0.
5. Kiểm thử cả các chuỗi phép toán thực tế, không chỉ một cast lẻ.

Không đặt một epsilon chung cho mọi bài toán. `Math.ulp` giúp ước lượng bước lượng tử theo độ lớn, nhưng **ngưỡng chấp nhận cuối cùng phải xuất phát từ nghiệp vụ**.

## 9. Chuyển `String` và ghi/đọc dữ liệu: đừng nhầm với cast

`Double.parseDouble("0.1")` trả giá trị binary64 gần 0.1; `Float.parseFloat("0.1")` trả giá trị binary32 gần 0.1. Cả hai đều parse văn bản và chọn miền biểu diễn của kiểu đích.

```java
double d = Double.parseDouble("0.1");
float f = Float.parseFloat("0.1");

System.out.println(d); // 0.1
System.out.println(f); // 0.1
System.out.println((double) f); // 0.10000000149011612
```

Chuỗi hiển thị `"0.1"` là **biểu diễn thập phân** được tạo ra để việc chuyển ngược về kiểu tương ứng nhận lại cùng một giá trị, không nói rằng hai kiểu lưu cùng một số nhị phân.

Khi cần giữ chính xác thập phân nhập từ người dùng (ví dụ tiền), có thể dùng:

```java
java.math.BigDecimal price = new java.math.BigDecimal("0.1");
```

`BigDecimal` là mô hình số thập phân chính xác ở mức giá trị được khai báo, không phải bản “`float` nhiều bit hơn”. Dùng `BigDecimal.valueOf(double)` có quy tắc chuyển từ biểu diễn thập phân của `double`, nhưng nó cũng không thể phục hồi dữ liệu đã từng bị ép thành `float`.

### `float` không đồng nghĩa với “ít bộ nhớ mà luôn đủ”

Một mảng `float[]` có phần tử 32 bit, trong khi `double[]` có phần tử 64 bit. Điều này có thể hữu ích với tập dữ liệu rất lớn, bộ đệm đồ họa hoặc giao thức lưu trữ bắt buộc binary32. Tuy nhiên tổng bộ nhớ còn có overhead, căn chỉnh và cấu trúc dữ liệu. Tiết kiệm dung lượng là lựa chọn kỹ thuật phải cân bằng với sai số, không phải lý do tự động thu hẹp mọi phép tính.

## 10. Chương trình Java 17+ chạy độc lập

Tạo tệp `NarrowingDoubleToFloatDemo.java`:

```java
public class NarrowingDoubleToFloatDemo {
    public static void main(String[] args) {
        double d = 0.1d;
        float f = (float) d;
        double widened = f;

        System.out.println("Original double: " + d);
        System.out.println("Narrowed float: " + f);
        System.out.println("Widened again: " + widened);
        System.out.println("Round trip equal? " + (d == widened));

        double largeInteger = 16_777_217.0;
        float roundedInteger = (float) largeInteger;
        System.out.println("Integer original: " + largeInteger);
        System.out.println("Integer as float: " + roundedInteger);

        System.out.println("Float spacing near 1: " + Math.ulp(1.0f));
        System.out.println("Double spacing near 1: " + Math.ulp(1.0d));

        System.out.println("Overflow: " + (float) 1.0e40);
        System.out.println("Underflow: " + (float) 1.0e-50);
        float subnormal = (float) 1.0e-40;
        System.out.println("Subnormal: " + subnormal);

        float negativeZero = (float) -0.0d;
        System.out.println("Negative zero sign: " + (1.0f / negativeZero));
        System.out.println("NaN stays NaN? " + Float.isNaN((float) Double.NaN));

        double mixed = f + 0.2d;
        System.out.println("Mixed expression type double value: " + mixed);
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 NarrowingDoubleToFloatDemo.java
java NarrowingDoubleToFloatDemo
```

Stdout:

```text
Original double: 0.1
Narrowed float: 0.1
Widened again: 0.10000000149011612
Round trip equal? false
Integer original: 1.6777217E7
Integer as float: 1.6777216E7
Float spacing near 1: 1.1920929E-7
Double spacing near 1: 2.220446049250313E-16
Overflow: Infinity
Underflow: 0.0
Subnormal: 1.0E-40
Negative zero sign: -Infinity
NaN stays NaN? true
Mixed expression type double value: 0.30000000149011613
```

Ví dụ thể hiện ba hiện tượng độc lập: **giảm độ chính xác** (`0.1` và số nguyên 16.777.217), **thu hẹp miền** (vô cực, zero và subnormal), cùng **quy tắc lan truyền kiểu** (phép cộng với `double` vẫn giữ xấp xỉ `float` đã có). Có thể thử thêm `1.25d` để thấy một trường hợp chuyển chính xác.

## 11. Các lỗi phổ biến và cách tránh

| Lỗi | Vì sao sai | Cách xử lý |
| --- | --- | --- |
| Cho rằng `(float) d` chỉ “đổi tên kiểu” | `float` chỉ có 24 bit significand | Xác định sai số cho phép; giữ `double` nếu cần |
| Thấy hai biến đều in `0.1` nên kết luận bằng nhau | `Float.toString` và `Double.toString` chọn chuỗi biểu diễn theo kiểu | Kiểm tra giá trị mở rộng lại, bit hoặc sai số đúng ngữ cảnh |
| Ép lại `(double)(float)d` rồi cho rằng đã phục hồi dữ liệu | Mở rộng không khôi phục bit đã mất | Giữ nguồn `double` nếu còn cần |
| Cho rằng cast quá lớn sẽ ném exception | Giá trị hữu hạn lớn có thể thành `Infinity` | Kiểm tra `Float.isFinite`, giới hạn miền |
| Cho rằng cast quá nhỏ giữ nguyên khác 0 | Có thể thành subnormal hoặc `0.0` | Kiểm tra ngưỡng nhỏ nhất và sai số |
| Dùng `float` cho số đếm lớn cần biểu diễn đúng từng đơn vị | Không phải mọi số nguyên trên `2^24` còn chính xác | Dùng `int`/`long` cho số đếm nguyên |
| Dùng `==` để so sánh kết quả thuật toán chấm động với kỳ vọng toán học | Làm tròn khiến các đường tính khác nhau | Chọn tiêu chí dung sai theo đơn vị và miền |
| Chuyển `double` xuống `float` trước phép tính rồi mở rộng ở cuối | Sai số đã xuất hiện ở bước trung gian | Tính bằng `double` trong suốt pipeline khi cần |
| Tưởng `float` đủ cho tiền vì “chỉ hai chữ số lẻ” | Decimal như 0.1 không biểu diễn chính xác trong binary32 | Dùng `BigDecimal` hoặc đơn vị nguyên (xu) khi phù hợp |
| Không để ý `+=` nhận `double` với biến `float` | Compound assignment thực hiện chuyển hẹp ngầm | Tách phép tính và cast tường minh |
| Dùng `Float.MIN_VALUE` như giá trị âm nhỏ nhất | Đây là số dương subnormal nhỏ nhất | Phân biệt `MIN_VALUE`, `MIN_NORMAL`, `MAX_VALUE` |

## 12. Câu hỏi tự kiểm tra

1. Vì sao `float f = d;` không biên dịch với `d` kiểu `double`, còn `float f = (float) d;` thì được?
2. `(double) (float) 0.1d` có bằng `0.1d` không? Tại sao cả hai có thể được in ra gần giống nhau?
3. Tại sao `16_777_217.0d` không giữ nguyên sau khi ép sang `float`, trong khi `16_777_216.0d` giữ nguyên?
4. Sự khác nhau giữa **độ chính xác (precision)** và **miền số mũ (range)** thể hiện ra sao khi ép `1e40` và `1e-50` sang `float`?
5. `Float.MIN_VALUE` biểu diễn điều gì? Số `float` subnormal có giống zero không?
6. Khi nào phép kiểm tra round-trip `(double)(float) d == d` hữu ích, và tại sao phải xử lý riêng NaN hoặc dấu của zero nếu hợp đồng yêu cầu?
7. Vì sao kết quả `float f = (float) 0.1d; double x = f + 0.2d;` vẫn chứa sai số từ `f` dù biểu thức cộng có kiểu `double`?
8. Ứng dụng đo đạc chấp nhận sai số dưới 0,01 đơn vị. Cần kiểm tra những yếu tố nào trước khi lưu kết quả `double` vào `float[]`?

### Ghi nhớ

**Ép `double` sang `float` là một phép làm tròn sang tập giá trị biểu diễn được của binary32**, không phải chuyển đổi đảm bảo giữ nguyên giá trị. Cast có thể khiến kết quả **mất độ chính xác, thành ±Infinity hoặc ±0.0, hoặc nằm trong vùng subnormal** mà không ném ngoại lệ. Hãy giữ `double` cho đến ranh giới thực sự phải lưu/truyền `float`, kiểm tra miền và sai số theo nghiệp vụ, và không kỳ vọng cast ngược lên `double` sẽ lấy lại thông tin đã mất.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-conversions-008">Chuyển đổi qua Number.intValue, longValue và doubleValue</a>

<details>
<summary>Xem chi tiết</summary>

# Chuyển đổi qua Number.intValue(), longValue() và doubleValue() trong Java

## 1. Vì sao Java có các phương thức chuyển đổi này?

Trong Java, `java.lang.Number` là **lớp trừu tượng** chung cho các lớp biểu diễn giá trị số, chẳng hạn `Byte`, `Short`, `Integer`, `Long`, `Float`, `Double`, `BigInteger` và `BigDecimal`. `Number` cho phép chương trình làm việc với nhiều loại số qua cùng một kiểu tham chiếu:

~~~java
Number quantity = Integer.valueOf(42);
int count = quantity.intValue();
long total = quantity.longValue();
double measured = quantity.doubleValue();
~~~

Đoạn trên là mã minh họa nằm trong một phương thức. Tại thời điểm chạy, biến `quantity` trỏ đến một đối tượng `Integer`, nên các lệnh gọi được thực hiện theo cài đặt của `Integer`. Nếu thay đối tượng bằng `Double` hoặc `BigDecimal`, **kiểu trả về vẫn cố định**, nhưng quy tắc chuyển đổi cụ thể có thể dẫn đến giá trị khác, mất độ chính xác, hoặc mất các bit cao.

Ba API cốt lõi:

| Phương thức của `Number` | Kiểu trả về | Ý nghĩa |
| --- | --- | --- |
| `intValue()` | `int` có dấu, 32 bit | Lấy giá trị ở dạng số nguyên 32 bit |
| `longValue()` | `long` có dấu, 64 bit | Lấy giá trị ở dạng số nguyên 64 bit |
| `doubleValue()` | `double`, IEEE 754 binary64 | Lấy giá trị ở dạng số thực dấu phẩy động 64 bit |

Những lời gọi này **không tạo ra đối tượng `Integer`, `Long` hay `Double`**: chúng trả về giá trị **primitive**. Nếu gán vào biến wrapper tương ứng, Java có thể tự boxing kết quả, ví dụ `Integer boxed = quantity.intValue();`.

## 2. Lớp Number: kiểu chung, đối tượng thực tế quyết định hành vi

`Number` khai báo bốn phương thức trừu tượng `intValue()`, `longValue()`, `floatValue()` và `doubleValue()`. Ngoài ra, `byteValue()` và `shortValue()` là phương thức cụ thể mặc định, chuyển qua `intValue()` rồi ép kiểu về `byte` hoặc `short`. Các lớp số chuẩn cài đặt hành vi chuyển đổi phù hợp với cách lưu dữ liệu của từng lớp.

~~~java
Number first = Long.valueOf(20L);
Number second = Double.valueOf(20.9);
Number third = new java.math.BigDecimal("20.9");

System.out.println(first.intValue());  // 20
System.out.println(second.intValue()); // 20
System.out.println(third.intValue());  // 20
~~~

Dù ba kết quả giống nhau, nguyên nhân không hoàn toàn giống: `Long` chuyển số nguyên sang `int`; `Double` chuyển số thực sang số nguyên theo quy tắc Java cho floating point; `BigDecimal` loại bỏ phần thập phân rồi lấy phần số nguyên có thể biểu diễn bởi 32 bit. Với những giá trị ngoài giới hạn, sự khác nhau rõ rệt hơn.

`Number` là **lớp**, không phải interface. Vì một tham chiếu `Number` có thể trỏ đến nhiều lớp con, không nên suy đoán mọi `Number` đều chính xác, đều hữu hạn hoặc đều nằm trong khoảng của `int`. Mã dùng `Number` cũng phải xử lý khả năng giá trị bằng `null`.

## 3. intValue(): chuyển sang int 32 bit

`intValue()` trả về `int` trong khoảng từ −2.147.483.648 đến 2.147.483.647.

### 3.1. Nguồn là Integer, Short hoặc Byte

Khi nguồn vốn là `Integer`, kết quả giữ nguyên. `Byte` và `Short` có miền nhỏ hơn `int` nên mọi giá trị đều biểu diễn chính xác khi mở rộng lên `int`.

~~~java
Number a = Byte.valueOf((byte) -7);
Number b = Short.valueOf((short) 32000);
Number c = Integer.valueOf(100000);

System.out.println(a.intValue()); // -7
System.out.println(b.intValue()); // 32000
System.out.println(c.intValue()); // 100000
~~~

### 3.2. Nguồn là Long: giữ 32 bit thấp

`Long.intValue()` tương đương chuyển thu hẹp `(int) longValue`: Java giữ **32 bit thấp** và diễn giải chúng là `int` có dấu. Đây không phải phép chuyển đổi có kiểm tra tràn; giá trị có thể thay đổi mạnh.

~~~java
Number n = Long.valueOf(3_000_000_000L);

System.out.println(n.intValue());  // -1294967296
System.out.println(n.longValue()); // 3000000000
~~~

Vì 3.000.000.000 lớn hơn `Integer.MAX_VALUE`, 32 bit thấp có bit dấu bằng 1; cùng mẫu bit đó được biểu diễn bởi `-1294967296` khi đọc dưới dạng `int`.

Nếu yêu cầu phải bảo toàn giá trị trong miền `int`, với nguồn `long` cụ thể hãy dùng `Math.toIntExact(long)`; API này ném `ArithmeticException` khi giá trị ngoài miền:

~~~java
long raw = 3_000_000_000L;
try {
    int safe = Math.toIntExact(raw);
    System.out.println(safe);
} catch (ArithmeticException ex) {
    System.out.println("Vượt giới hạn int");
}
~~~

### 3.3. Nguồn là Double hoặc Float: bỏ phần lẻ, có quy tắc cho giá trị đặc biệt

Với nguồn `Double`/`Float`, `intValue()` thực hiện quy tắc ép từ floating point về `int` của Java:

- Phần thập phân bị **cắt về phía 0**, không làm tròn gần nhất: `12.9 → 12`; `-12.9 → -12`.
- `NaN → 0`.
- Số dương quá lớn hoặc `+Infinity → Integer.MAX_VALUE`.
- Số âm quá nhỏ hoặc `-Infinity → Integer.MIN_VALUE`.

~~~java
System.out.println(Double.valueOf(12.9).intValue());                // 12
System.out.println(Double.valueOf(-12.9).intValue());               // -12
System.out.println(Double.valueOf(Double.NaN).intValue());         // 0
System.out.println(Double.valueOf(Double.POSITIVE_INFINITY).intValue()); // 2147483647
System.out.println(Double.valueOf(Double.NEGATIVE_INFINITY).intValue()); // -2147483648
~~~

Quy tắc này thường gọi là **saturation** ở trường hợp vượt giới hạn đối với chuyển đổi floating point sang integral. Đừng áp dụng nó cho `Long.intValue()` hoặc `BigInteger.intValue()`; hai loại đó có thể lấy các bit thấp và quay vòng, không chặn ở `Integer.MAX_VALUE`.

### 3.4. Nguồn là BigInteger hoặc BigDecimal

`BigInteger.intValue()` chỉ giữ 32 bit thấp của số nguyên lớn. `BigDecimal.intValue()` trước hết loại phần thập phân, sau đó kết quả cuối có thể chỉ giữ 32 bit thấp. Vì vậy **không thể coi `intValue()` là phép chuyển đổi kiểm tra miền hoặc kiểm tra phần lẻ**.

~~~java
Number huge = new java.math.BigInteger("4294967303"); // 2^32 + 7
Number decimal = new java.math.BigDecimal("-12.99");

System.out.println(huge.intValue());    // 7
System.out.println(decimal.intValue()); // -12
~~~

Nếu nguồn là `BigInteger` hoặc `BigDecimal` và yêu cầu giá trị nguyên chính xác, dùng `intValueExact()` của chính lớp đó. `BigDecimal.intValueExact()` còn báo lỗi nếu phần lẻ khác 0.

## 4. longValue(): chuyển sang long 64 bit

`longValue()` trả về `long` trong khoảng từ −9.223.372.036.854.775.808 đến 9.223.372.036.854.775.807.

- Từ `Byte`, `Short`, `Integer` hoặc `Long`: giá trị số nguyên được bảo toàn vì tất cả nằm trong miền `long`.
- Từ `Double`/`Float`: bỏ phần lẻ về phía 0, `NaN → 0L`, vượt giới hạn dương/`+Infinity → Long.MAX_VALUE`, vượt giới hạn âm/`-Infinity → Long.MIN_VALUE`.
- Từ `BigInteger`: chỉ giữ **64 bit thấp**, kể cả khi số gốc lớn hơn `Long.MAX_VALUE`.
- Từ `BigDecimal`: bỏ phần lẻ rồi có thể mất các bit cao khi nằm ngoài miền `long`.

~~~java
Number fromInt = Integer.valueOf(-150);
Number fromDouble = Double.valueOf(98.75);
Number fromHuge = new java.math.BigInteger("18446744073709551617"); // 2^64 + 1

System.out.println(fromInt.longValue());    // -150
System.out.println(fromDouble.longValue()); // 98
System.out.println(fromHuge.longValue());   // 1
~~~

Ở ví dụ cuối, `18446744073709551617 = 2^64 + 1`. Khi chỉ còn 64 bit thấp, mẫu bit bằng 1. Dùng `BigInteger.longValueExact()` hoặc `BigDecimal.longValueExact()` để phát hiện chuyển đổi mất giá trị, nếu loại nguồn tương ứng hỗ trợ.

### Vì sao intValue và longValue không có cùng kết quả?

Cùng một `Number` có thể cho kết quả khác nhau theo số bit đích:

~~~java
Number n = Long.valueOf(4_294_967_296L); // 2^32

System.out.println(n.intValue());  // 0
System.out.println(n.longValue()); // 4294967296
~~~

`intValue()` giữ 32 bit thấp toàn số 0; `longValue()` giữ đủ số nguyên 64 bit.

## 5. doubleValue(): chuyển sang double 64 bit nhưng chỉ có 53 bit chính xác cho số nguyên

`double` sử dụng chuẩn IEEE 754 binary64: 1 bit dấu, 11 bit số mũ và 52 bit phần trị được lưu (thêm 1 bit dẫn đầu ngầm định cho số hữu hạn thông thường). Vì vậy độ chính xác hiệu dụng khoảng **53 bit nhị phân**, tương đương khoảng 15–17 chữ số thập phân có nghĩa.

Điều này có hai hệ quả:

1. Mọi số nguyên có độ lớn không quá **2^53 = 9.007.199.254.740.992** đều chuyển sang `double` **chính xác**. Một số lớn hơn vẫn có thể chính xác nếu biểu diễn được, nhưng **không còn bảo đảm cho mọi số nguyên liên tiếp**.
2. Số nguyên lớn như `long` và `BigInteger` hoặc số thập phân như `BigDecimal` có thể bị **làm tròn**. `doubleValue()` không báo lỗi vì mất độ chính xác.

~~~java
Number exact = Long.valueOf(9_007_199_254_740_992L); // 2^53
Number next = Long.valueOf(9_007_199_254_740_993L);  // 2^53 + 1

System.out.println(exact.doubleValue() == next.doubleValue()); // true
~~~

Hai số `long` liền nhau nhưng cùng được làm tròn về một giá trị `double`. Đây là lý do không dùng `doubleValue()` làm phép chuyển đổi không mất dữ liệu cho ID lớn, số đếm tuyệt đối lớn, hoặc khóa so sánh chính xác.

### BigDecimal và số thập phân không thể biểu diễn chính xác

~~~java
java.math.BigDecimal price = new java.math.BigDecimal("0.1");
double approximated = price.doubleValue();
System.out.println(approximated); // 0.1
~~~

Kết quả in là `0.1` vì `Double.toString` chọn biểu diễn thập phân ngắn có thể round-trip về cùng bit, **không có nghĩa 0,1 được lưu chính xác trong hệ nhị phân**. Nếu phải cộng tiền, tính thuế, kiểm tra sự bằng nhau chính xác theo đơn vị thập phân, giữ phép tính trong `BigDecimal` và quy định scale/rounding thích hợp.

Với `BigDecimal` hoặc `BigInteger` cực lớn, `doubleValue()` có thể tạo `Double.POSITIVE_INFINITY` hoặc `Double.NEGATIVE_INFINITY`; số khác 0 nhưng quá gần 0 cũng có thể bị làm tròn về `0.0` hoặc `-0.0` khi chuyển từ `BigDecimal` sang `double`. `double` không có miền hữu hạn hoặc độ chính xác để chứa mọi số thập phân.

## 6. Chương trình Java 17+ chạy đầy đủ

Tạo tệp `NumberConversionsDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.BigInteger;

public class NumberConversionsDemo {
    public static void main(String[] args) {
        Number whole = Integer.valueOf(42);
        Number large = Long.valueOf(3_000_000_000L);
        Number fraction = Double.valueOf(-12.75);
        Number decimal = new BigDecimal("123.99");
        Number huge = new BigInteger("4294967303");

        System.out.println("Integer -> " + whole.intValue() + ", " +
                whole.longValue() + ", " + whole.doubleValue());
        System.out.println("Long -> " + large.intValue() + ", " +
                large.longValue() + ", " + large.doubleValue());
        System.out.println("Double -> " + fraction.intValue() + ", " +
                fraction.longValue() + ", " + fraction.doubleValue());
        System.out.println("BigDecimal -> " + decimal.intValue() + ", " +
                decimal.longValue() + ", " + decimal.doubleValue());
        System.out.println("BigInteger -> " + huge.intValue() + ", " +
                huge.longValue() + ", " + huge.doubleValue());

        System.out.println("NaN as int -> " + Double.valueOf(Double.NaN).intValue());
        System.out.println("Infinity as long -> " +
                Double.valueOf(Double.POSITIVE_INFINITY).longValue());

        try {
            ((BigDecimal) decimal).intValueExact();
        } catch (ArithmeticException ex) {
            System.out.println("Decimal exact -> ArithmeticException");
        }

        Number aboveExactDouble = new BigInteger("9007199254740993");
        BigDecimal roundedAsDecimal = BigDecimal.valueOf(aboveExactDouble.doubleValue());
        System.out.println("53-bit boundary -> " +
                aboveExactDouble.longValue() + " / " +
                roundedAsDecimal.toPlainString());
    }
}
~~~

Chạy:

~~~bash
javac NumberConversionsDemo.java
java NumberConversionsDemo
~~~

Kết quả:

~~~text
Integer -> 42, 42, 42.0
Long -> -1294967296, 3000000000, 3.0E9
Double -> -12, -12, -12.75
BigDecimal -> 123, 123, 123.99
BigInteger -> 7, 4294967303, 4.294967303E9
NaN as int -> 0
Infinity as long -> 9223372036854775807
Decimal exact -> ArithmeticException
53-bit boundary -> 9007199254740993 / 9007199254740992
~~~

Cần quan sát:

- Giá trị 3 tỷ chuyển sang `int` cho ra số âm vì bị giới hạn 32 bit.
- `-12.75` thành `-12` ở kiểu nguyên vì cắt về phía 0.
- `BigDecimal("123.99").intValue()` bỏ phần lẻ trong khi `intValueExact()` phát hiện mất dữ liệu.
- `2^32 + 7` chuyển sang `int` còn 7.
- `2^53 + 1` không thể giữ chính xác trong `double`; cách in cuối đối chiếu giá trị số nguyên gốc với giá trị đã được làm tròn khi đổi qua `double`.

## 7. Bảng tổng hợp quy tắc theo loại đối tượng

| Nguồn thực tế | `intValue()` | `longValue()` | `doubleValue()` |
| --- | --- | --- | --- |
| `Byte` / `Short` / `Integer` | Chính xác nếu nằm trong miền `int`; các lớp này đều thỏa | Chính xác | Chính xác cho tất cả giá trị `int` |
| `Long` | Giữ 32 bit thấp; có thể đổi giá trị | Chính xác | Có thể làm tròn với số nguyên lớn |
| `Float` / `Double` | Cắt phần lẻ; NaN → 0; tràn thì chặn biên `int` | Cắt phần lẻ; NaN → 0; tràn thì chặn biên `long` | `Double` giữ nguyên giá trị; `Float` mở rộng chính xác mẫu floating point đã lưu sang `double` |
| `BigInteger` | Giữ 32 bit thấp | Giữ 64 bit thấp | Có thể làm tròn hoặc thành ±Infinity |
| `BigDecimal` | Bỏ phần lẻ, có thể mất bit cao | Bỏ phần lẻ, có thể mất bit cao | Có thể làm tròn, underflow hoặc overflow |

**Ghi chú về `Float.doubleValue()`:** Phép mở rộng từ `float` sang `double` bảo toàn chính xác **giá trị nhị phân của `float`**, nhưng không phục hồi độ chính xác thập phân đã mất khi số ban đầu được lưu dưới `float`. Ví dụ:

~~~java
Number n = Float.valueOf(0.1f);
System.out.println(n.doubleValue()); // 0.10000000149011612
~~~

## 8. Cách chuyển đổi có kiểm tra mất dữ liệu

### Với nguồn long

`Math.toIntExact(long)` trả `int` nếu trong giới hạn, ngược lại ném `ArithmeticException`:

~~~java
long value = 50_000L;
int safe = Math.toIntExact(value);
System.out.println(safe); // 50000
~~~

### Với nguồn BigInteger hoặc BigDecimal

Dùng các phương thức `intValueExact()` và `longValueExact()` thay cho `intValue()`/`longValue()` khi không thể chấp nhận bị cắt giá trị:

~~~java
java.math.BigDecimal amount = new java.math.BigDecimal("19.00");
System.out.println(amount.intValueExact()); // 19

java.math.BigDecimal bad = new java.math.BigDecimal("19.01");
try {
    System.out.println(bad.longValueExact());
} catch (ArithmeticException ex) {
    System.out.println("Có phần thập phân");
}
~~~

`19.00` có phần biểu diễn thập phân nhưng **giá trị toán học là số nguyên** nên `intValueExact()` thành công. Ngược lại, `19.01` không thể chuyển thành `long` chính xác.

### Với Double và Float

`Number` không có `intValueExact()` chung. Nếu thực sự cần chuyển từ `double` sang số nguyên **đúng giá trị và trong giới hạn**, hãy kiểm tra `Double.isFinite`, phần lẻ, miền giá trị và những điều kiện chính xác theo yêu cầu trước khi chuyển. Cần đặc biệt cẩn thận tại biên `long`: `(double) Long.MAX_VALUE` được làm tròn lên thành 2^63 và không còn đại diện chính xác cho `Long.MAX_VALUE`.

Một cách tường minh cho các giá trị `double` hữu hạn là dùng `BigDecimal.valueOf(d).toBigIntegerExact()` để kiểm tra tính nguyên của **biểu diễn thập phân round-trip của `double`**, sau đó `longValueExact()`/`intValueExact()` để kiểm tra miền. Tuy nhiên, cách này **không phục hồi chữ số đã mất trước đó** khi dữ liệu được chuyển thành `double`. Nếu đầu vào gốc có tính chính xác thập phân (như tiền), nên parse/lưu nó bằng `BigDecimal` ngay từ đầu.

## 9. Khi nào chọn intValue, longValue hay doubleValue?

| Nhu cầu | Lựa chọn hợp lý | Điều cần xác minh |
| --- | --- | --- |
| Lấy số đếm nhỏ từ `Number` | `intValue()` | Có thể bị cắt phần lẻ/tràn? |
| Lấy timestamp/ID nguyên theo miền `long` | `longValue()` | Đối tượng nguồn có thực sự là số nguyên và trong miền `long`? |
| Dùng tính toán số thực hoặc phép đo chấp nhận sai số | `doubleValue()` | Độ chính xác floating point có đủ không? |
| Chuyển `BigDecimal` sang số nguyên không mất dữ liệu | `intValueExact()`/`longValueExact()` | Ném `ArithmeticException` nếu không biểu diễn chính xác |
| Chuyển `long` thành `int` mà không tràn | `Math.toIntExact()` | Xử lý `ArithmeticException` |
| Tính toán tiền tệ và cần chính xác số thập phân | Giữ `BigDecimal` | Scale, quy tắc làm tròn, phép so sánh |
| Tính toán số nguyên lớn hơn 64 bit | Giữ `BigInteger` | Không cắt xuống `int` hoặc `long` nếu chưa kiểm tra |

Đừng nhầm **chuyển giá trị** với **parse chuỗi**. `number.intValue()` chỉ áp dụng khi đã có một đối tượng `Number`. Đầu vào `"123.45"` là `String`, cần parse phù hợp trước (chẳng hạn `new BigDecimal("123.45")`); `Number` không khai báo phương thức static `parse` hay `valueOf` dùng chung.

## 10. Đa hình, null và lớp Number tùy biến

Một API có thể nhận `Number` để làm việc với các lớp con:

~~~java
static double twice(Number n) {
    if (n == null) {
        throw new IllegalArgumentException("Number must not be null");
    }
    return n.doubleValue() * 2;
}
~~~

Đoạn trên là phương thức minh họa; không phải chương trình độc lập. Việc chuyển sang `double` phù hợp nếu hợp đồng của API **cho phép sai số**. Nếu API yêu cầu chính xác với `BigDecimal` hoặc `BigInteger`, giao diện nhận `Number` quá rộng và việc tự gọi `doubleValue()` có thể làm mất dữ liệu. Khi đó nên chọn một kiểu tham số cụ thể hoặc mô tả rõ giới hạn và chính sách chuyển đổi.

Một lớp kế thừa `Number` tùy biến phải cài đặt bốn phương thức abstract; vì thế các quy tắc chung phía trên mô tả **các lớp Number chuẩn**, không bảo đảm mọi lớp con do bên thứ ba tự viết tuân theo cùng cách làm tròn, tràn hoặc chuyển đổi.

## 11. Lỗi thường gặp

1. **Cho rằng `intValue()` có kiểm tra tràn.** Sai với `Long`, `BigInteger` và `BigDecimal`. Khi cần chính xác, dùng API `Exact` tương ứng.
2. **Cho rằng bỏ phần lẻ là làm tròn.** `12.9` trở thành `12` và `-12.9` trở thành `-12`; hãy dùng quy tắc làm tròn rõ ràng trước khi chuyển nếu nghiệp vụ cần.
3. **Dùng `doubleValue()` cho số tiền hoặc khóa số nguyên rất lớn.** `double` có thể mất chữ số; ưu tiên `BigDecimal`/`BigInteger`.
4. **Giả định NaN hoặc Infinity sẽ gây lỗi khi chuyển sang nguyên.** Với `Double` và `Float`, phép chuyển theo quy tắc Java tạo 0 hoặc giá trị biên.
5. **Ép `Number` về lớp cụ thể vô điều kiện.** `(BigDecimal) number` sẽ ném `ClassCastException` nếu đối tượng thực tế là `Integer`, `Long` hoặc `Double`; hãy kiểm tra loại hoặc thiết kế API chặt hơn.
6. **Nhầm `Number` với cơ chế chuyển chuỗi.** Dùng parser/phương thức khởi tạo phù hợp để tạo đối tượng số trước.
7. **Quên null.** Gọi `n.intValue()` khi `n == null` sẽ ném `NullPointerException`.

## 12. Câu hỏi tự kiểm tra

**1.** Vì sao `Long.valueOf(4294967296L).intValue()` trả `0`?

Vì chuyển từ 64 bit xuống 32 bit chỉ giữ 32 bit thấp. `4294967296 = 2^32` có 32 bit thấp bằng 0.

**2.** `Double.valueOf(-3.9).longValue()` trả bao nhiêu?

`-3L`, vì chuyển floating point sang integer loại phần lẻ về phía 0.

**3.** Có thể thay `new BigDecimal("123.45")` bằng `Double.valueOf(123.45)` trong xử lý tiền rồi gọi `doubleValue()` không?

Có thể thực hiện về mặt cú pháp, nhưng số thập phân có thể được biểu diễn xấp xỉ trong binary64. Khi yêu cầu chính xác cho nghiệp vụ tiền tệ, giữ `BigDecimal` cùng quy tắc scale/rounding.

**4.** Tại sao `BigInteger.intValue()` và `Double.intValue()` có thể khác quy tắc với số lớn?

Vì `BigInteger` lấy 32 bit thấp của số nguyên tùy ý; `Double` tuân theo chuyển đổi dấu phẩy động sang nguyên của Java, bão hòa ở min/max khi vượt miền.

**5.** Muốn ép `BigDecimal("100.0")` thành `int` và chắc chắn không mất thông tin phải làm gì?

Gọi `new BigDecimal("100.0").intValueExact()`; kết quả là `100` vì giá trị toán học đúng là số nguyên, mặc dù biểu diễn có một chữ số thập phân.

## 13. Ghi nhớ

- `Number` cung cấp một giao diện phương thức chung cho nhiều loại đối tượng số; **kiểu đối tượng thực tế quyết định** cách chuyển đổi.
- `intValue()` và `longValue()` trả giá trị nguyên primitive; có thể cắt phần thập phân, mất bit cao hoặc bão hòa, **tùy loại nguồn**.
- `doubleValue()` cho phép làm việc với số thực nhưng có giới hạn **53 bit độ chính xác hiệu dụng** và có thể làm tròn, tràn thành Infinity hoặc mất giá trị rất nhỏ.
- Khi không được phép mất dữ liệu, kiểm tra miền và tính nguyên; dùng `Math.toIntExact`, `BigInteger.intValueExact/longValueExact` hoặc `BigDecimal.intValueExact/longValueExact` theo loại nguồn. Giữ `BigDecimal`/`BigInteger` trong các phép tính cần độ chính xác cao.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-conversions-009">Phân biệt ép kiểu, làm tròn và kiểm tra chuyển đổi exact</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt ép kiểu, làm tròn và kiểm tra chuyển đổi `exact` trong Java

## 1. Ba câu hỏi khác nhau khi chuyển đổi số

Khi chuyển một giá trị từ kiểu số này sang kiểu số khác, cần tách ba mục tiêu:

1. **Ép kiểu (cast/conversion)**: đưa giá trị sang một kiểu dữ liệu khác theo quy tắc của Java. Phép chuyển có thể giữ nguyên, làm mất phần thập phân, làm mất bit, hoặc giới hạn giá trị ở biên kiểu đích.
2. **Làm tròn (rounding)**: chọn một giá trị đại diện theo quy tắc rõ ràng (gần nhất, lên, xuống, về 0, quy tắc xử lý trường hợp đúng giữa hai mức...). Làm tròn không đồng nghĩa với đổi kiểu, cũng không đảm bảo kết quả nằm trong miền kiểu đích.
3. **Chuyển đổi chính xác (`exact`)**: yêu cầu kết quả giữ nguyên giá trị số theo hợp đồng API; nếu phải bỏ phần lẻ hoặc vượt giới hạn kiểu đích thì báo lỗi, thường là `ArithmeticException`.

Ví dụ với `12.8`:

- `(int) 12.8` cho `12` vì chuyển `double` sang `int` cắt phần thập phân về phía 0.
- `Math.round(12.8)` cho `13L`, vì hàm chọn số nguyên gần nhất và trả về `long` với đối số `double`.
- `new BigDecimal("12.8").intValueExact()` ném `ArithmeticException`, vì `12.8` không phải số nguyên.

Không có phép nào trong ba phép trên tự động phù hợp mọi nghiệp vụ. Đặc biệt, *`exact`* nói về việc chuyển không làm đổi **giá trị hiện đang được lưu**, chứ không khôi phục được độ chính xác đã mất ở bước tính toán trước đó.

## 2. Nền tảng: miền giá trị, độ rộng và độ chính xác

| Kiểu nguyên thủy | Độ rộng | Miền / đặc tính quan trọng |
| --- | ---: | --- |
| `byte` | 8 bit | `-128` đến `127` |
| `short` | 16 bit | `-32768` đến `32767` |
| `char` | 16 bit | Không dấu `0` đến `65535`; là một đơn vị mã UTF-16 |
| `int` | 32 bit | `-2147483648` đến `2147483647` |
| `long` | 64 bit | `-9223372036854775808` đến `9223372036854775807` |
| `float` | 32 bit | Số dấu phẩy động nhị phân, khoảng 24 bit độ chính xác phần trị |
| `double` | 64 bit | Số dấu phẩy động nhị phân, khoảng 53 bit độ chính xác phần trị |

Giá trị `float`/`double` còn có thể là `NaN`, `+Infinity`, `-Infinity` và `+0.0`/`-0.0`. Chúng không biểu diễn chính xác mọi số thập phân hữu hạn; chẳng hạn `0.1` không có biểu diễn nhị phân hữu hạn chính xác trong `double`.

### Widening không phải lúc nào cũng giữ nguyên độ chính xác

Một số chuyển đổi mở rộng (*widening*) là chính xác: `byte` → `short` → `int` → `long` giữ nguyên giá trị nguyên; `float` → `double` giữ chính xác giá trị `float` **đã có**.

Tuy nhiên, `int` → `float` và `long` → `double` có thể làm mất bit chính xác dù kiểu đích thường được gọi là “rộng hơn”. Ví dụ:

```java
long original = 9_007_199_254_740_993L; // 2^53 + 1
double converted = original;            // 2^53
long back = (long) converted;            // 9_007_199_254_740_992L
```

`double` chỉ có 53 bit phần trị chính xác, nên không biểu diễn được hai số nguyên liên tiếp ở vùng này. Sau khi chuyển thành `double`, thông tin đã mất; ép ngược hoặc gọi một phương thức `exact` sau đó không thể tái tạo số ban đầu. Khi so sánh trực tiếp `original == converted`, Java lại chuyển `long` sang `double` để so sánh, nên cách so sánh đó cũng có thể che giấu mất mát. Hãy kiểm tra trước khi chuyển hoặc dùng một miền số phù hợp.

## 3. Ép kiểu nguyên thủy: kết quả có thể mất dữ liệu mà không báo lỗi

### 3.1. Từ số nguyên lớn xuống số nguyên nhỏ

Phép chuyển thu hẹp (*narrowing*) bằng cast, như `(int) someLong` hay `(byte) someInt`, giữ các bit thấp phù hợp với độ rộng kiểu đích. Với kiểu có dấu, mẫu bit kết quả được diễn giải theo bù hai.

```java
int a = 130;
byte b = (byte) a; // -126

long large = 3_000_000_000L;
int narrowed = (int) large; // -1294967296
```

Với `130`, 8 bit thấp là `1000_0010`, mang giá trị `-126` trong `byte`. Với `3_000_000_000L`, 32 bit thấp tương ứng với giá trị `int` âm. Java **không ném ngoại lệ** khi cast nguyên thủy mất bit.

Với `char`, kết quả nằm trong `0..65535`; giá trị lớn hơn cũng bị giữ 16 bit thấp. Vì `char` là đơn vị mã UTF-16, không nên coi cast số → `char` là thao tác chuyển số Unicode code point bất kỳ thành ký tự đầy đủ.

### 3.2. Từ `float`/`double` sang `int`/`long`

Với giá trị hữu hạn còn nằm trong miền kiểu đích, Java **cắt phần thập phân về phía 0** (*truncation toward zero*), khác với làm tròn số nguyên gần nhất:

| Biểu thức | Kết quả |
| --- | ---: |
| `(int) 2.9` | `2` |
| `(int) -2.9` | `-2` |
| `(long) 2.9` | `2L` |
| `(long) -2.9` | `-2L` |

Các biên đặc biệt quan trọng:

- `NaN` chuyển sang `int`/`long` thành `0`.
- Giá trị quá lớn phía dương (kể cả `+Infinity`) chuyển thành `Integer.MAX_VALUE` hoặc `Long.MAX_VALUE`.
- Giá trị quá nhỏ phía âm (kể cả `-Infinity`) chuyển thành `Integer.MIN_VALUE` hoặc `Long.MIN_VALUE`.
- Cast không ném `ArithmeticException` khi xảy ra các trường hợp trên.

Ví dụ `(int) 1e20` cho `2147483647`; `(int) Double.NaN` cho `0`. Vì vậy cast **không phải** một phép xác thực đầu vào.

Đối với ép `double` → `float`, Java cũng có thể làm tròn theo độ chính xác nhị phân thấp hơn; giá trị có độ lớn quá lớn có thể trở thành vô cực, giá trị rất nhỏ có thể thành 0 (hoặc số subnormal). Đây là quy tắc chuyển dấu phẩy động, không phải hành vi cắt phần thập phân của `double` → `int`.

### 3.3. Cú pháp cast khác với parse

`(int) d` chuyển một giá trị số có sẵn. `Integer.parseInt("123")` đọc một **chuỗi ký tự** thành số nguyên, kiểm tra cú pháp và miền `int`, có thể ném `NumberFormatException`.

```java
int a = (int) 12.9;         // 12
int b = Integer.parseInt("12"); // 12
// Integer.parseInt("12.9"); // NumberFormatException
```

Nếu đầu vào người dùng phải là số nguyên và không được mất phần lẻ, hãy phân tích chuỗi bằng API phù hợp (`Integer.parseInt`, hoặc `BigDecimal` nếu có định dạng thập phân cần kiểm tra), thay vì parse sang `double` rồi cast.

## 4. Làm tròn: xác định đúng hướng và quy tắc khi gặp giá trị giữa hai mức

### 4.1. Cast, `floor`, `ceil`, `round` và `rint`

Các API toán học của `Math` trả kết quả theo những quy tắc khác nhau:

| Phép toán | Quy tắc | Ví dụ với `2.5` | Ví dụ với `-2.5` |
| --- | --- | --- | --- |
| `(int) x` | Cắt về phía 0 (nếu hữu hạn, trong miền) | `2` | `-2` |
| `Math.floor(x)` | Số nguyên lớn nhất `<= x`, kết quả `double` | `2.0` | `-3.0` |
| `Math.ceil(x)` | Số nguyên nhỏ nhất `>= x`, kết quả `double` | `3.0` | `-2.0` |
| `Math.round(x)` | Số nguyên gần nhất; khi đúng giữa hai số, chọn số lớn hơn | `3` | `-2` |
| `Math.rint(x)` | Số nguyên gần nhất ở dạng `double`; hòa chọn số chẵn | `2.0` | `-2.0` |

Trong bảng, `Math.round(double)` trả về **`long`**, còn `Math.round(float)` trả về **`int`**. `Math.floor`, `Math.ceil`, `Math.rint` trả kiểu `double`; **kiểu trả về khác nhau** dù các kết quả nhìn giống số nguyên.

Các ví dụ khác:

```java
Math.round(2.5);  // 3L
Math.round(-1.5); // -1L
Math.rint(3.5);   // 4.0
Math.rint(2.5);   // 2.0
Math.floor(-2.2); // -3.0
Math.ceil(-2.2);  // -2.0
```

`Math.round` cũng xử lý `NaN` thành `0` và bão hòa ở biên `int`/`long` với vô cực hoặc giá trị vượt miền. Vì vậy, dù có chữ “round”, kết quả của hàm **không có nghĩa là** đã xác thực giới hạn kiểu đích. `Math.floor`/`ceil`/`rint` giữ kết quả dạng `double` và có quy tắc riêng với `NaN`/vô cực; nếu sau đó cast, các quy tắc mất dữ liệu của cast vẫn áp dụng.

### 4.2. RoundingMode của `BigDecimal`

Khi cần kiểm soát số chữ số thập phân, đặc biệt với tiền, thuế hoặc dữ liệu yêu cầu quy tắc làm tròn được chỉ định, dùng `BigDecimal.setScale(scale, roundingMode)`:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;

BigDecimal x = new BigDecimal("12.345");
BigDecimal a = x.setScale(2, RoundingMode.HALF_UP);   // 12.35
BigDecimal b = x.setScale(2, RoundingMode.HALF_EVEN); // 12.34
```

Ở đây `scale = 2` nghĩa là hai chữ số bên phải dấu thập phân; **không** phải hai chữ số có nghĩa. Các chế độ thường dùng:

| Chế độ | Quy tắc chính | Ví dụ làm tròn thành số nguyên |
| --- | --- | --- |
| `UP` | Ra xa 0 khi phải bỏ chữ số khác 0 | `-2.1 → -3` |
| `DOWN` | Về phía 0 | `-2.9 → -2` |
| `CEILING` | Về phía dương vô cực | `-2.1 → -2` |
| `FLOOR` | Về phía âm vô cực | `-2.1 → -3` |
| `HALF_UP` | Gần nhất; đúng giữa hai mức thì ra xa 0 | `-2.5 → -3` |
| `HALF_DOWN` | Gần nhất; đúng giữa hai mức thì về 0 | `-2.5 → -2` |
| `HALF_EVEN` | Gần nhất; đúng giữa hai mức thì chọn chữ số cuối chẵn | `2.5 → 2`, `3.5 → 4` |
| `UNNECESSARY` | Không cho phép bỏ phần có giá trị; cần làm tròn thì ném lỗi | `2.5` về scale 0 → lỗi |

`Math.round(-2.5)` là `-2`, nhưng `new BigDecimal("-2.5").setScale(0, RoundingMode.HALF_UP)` là `-3`. Cả hai đều đúng theo quy tắc riêng; không nên gọi chung chung là “làm tròn kiểu thông thường”.

Để tính decimal ổn định, ưu tiên `new BigDecimal("0.1")` hoặc `BigDecimal.valueOf(0.1)`. `new BigDecimal(0.1)` ghi lại **giá trị nhị phân chính xác của `double` đã làm tròn**, vốn không bằng chính xác `0.1` trong hệ thập phân. `BigDecimal.valueOf(double)` sử dụng chuỗi thập phân chuẩn của giá trị `double`, nhưng không thể phục hồi các chữ số mà phép tính dấu phẩy động trước đó đã làm mất.

## 5. Các API chuyển đổi `exact` kiểm tra điều gì?

### 5.1. `Math.toIntExact(long)`: thu hẹp `long` → `int` có kiểm tra miền

```java
int ok = Math.toIntExact(42L); // 42

// Ném ArithmeticException vì vượt Integer.MAX_VALUE:
int overflow = Math.toIntExact(3_000_000_000L);
```

`Math.toIntExact` không có overload nhận `double` hoặc `BigDecimal`; nó chỉ dùng cho một giá trị `long` muốn chuyển sang `int`. Trái với `(int) someLong`, hàm này **từ chối** giá trị ngoài phạm vi `int`. Nếu cần kiểm tra tràn số của phép cộng/nhân nguyên trong quá trình tính, có các hàm liên quan `Math.addExact`, `Math.subtractExact`, `Math.multiplyExact`...; đó là kiểm tra **phép tính**, không phải ép kiểu.

### 5.2. `BigInteger.intValueExact()` / `longValueExact()`

`BigInteger` có thể lớn hơn miền `long`. Hai phương thức `intValueExact()` và `longValueExact()` ném `ArithmeticException` nếu giá trị không vừa kiểu đích.

```java
import java.math.BigInteger;

BigInteger small = new BigInteger("2147483647");
int ok = small.intValueExact(); // 2147483647

BigInteger tooLarge = new BigInteger("2147483648");
// tooLarge.intValueExact(); // ArithmeticException
long fitsLong = tooLarge.longValueExact(); // 2147483648L
```

Trong khi đó, `.intValue()`/`.longValue()` có thể làm mất bit; tên không có `Exact` không đảm bảo giữ nguyên giá trị.

### 5.3. `BigDecimal.intValueExact()` / `longValueExact()`

Với `BigDecimal`, `exact` kiểm tra **cả phần lẻ lẫn phạm vi**:

```java
import java.math.BigDecimal;

new BigDecimal("12.00").intValueExact(); // 12: phần thập phân chỉ gồm 0
new BigDecimal("-4").longValueExact();   // -4L

// new BigDecimal("12.01").intValueExact();  // ArithmeticException: có phần lẻ
// new BigDecimal("2147483648").intValueExact(); // ArithmeticException: quá miền
```

Điều kiện không phải `scale == 0`: `12.00` có scale là 2 nhưng vẫn biểu thị chính xác số nguyên 12. Các phương thức `.intValue()` và `.longValue()` thông thường không phát tín hiệu khi bỏ phần lẻ hoặc vượt miền.

Nếu mục tiêu là **phải có số nguyên**, hãy gọi `intValueExact()` trực tiếp lên giá trị `BigDecimal` gốc. Nếu nghiệp vụ cho phép làm tròn trước, hãy chọn rõ `RoundingMode`, rồi gọi `intValueExact()` lên giá trị đã làm tròn. Hai quy trình này có ý nghĩa khác nhau:

```java
BigDecimal input = new BigDecimal("12.8");

// Không được làm mất phần lẻ: input.intValueExact() sẽ ném lỗi.
int rounded = input.setScale(0, java.math.RoundingMode.HALF_UP)
                   .intValueExact(); // 13
```

`setScale(0, ...)` giải quyết chuyện phần lẻ; `intValueExact()` sau đó vẫn kiểm tra miền `int`. Nếu muốn không bao giờ phải làm tròn, có thể dùng `RoundingMode.UNNECESSARY` và kiểm tra chính xác.

### 5.4. Có phương thức `exact` cho `double` → `int` không?

Không có `Math.toIntExact(double)` trong Java 17. Với dữ liệu `double` hiện có, hãy kiểm tra hữu hạn, nguyên vẹn và trong miền `int` **trước khi cast**:

```java
static int requireInt(double value) {
    if (!Double.isFinite(value)
            || value != Math.rint(value)
            || value < Integer.MIN_VALUE
            || value > Integer.MAX_VALUE) {
        throw new ArithmeticException("Cannot convert exactly to int");
    }
    return (int) value;
}
```

Ở đây `Math.rint(value)` giúp xác nhận giá trị `double` hiện tại là số nguyên (không cần làm tròn); dấu `==`/`!=` có thể so sánh chính xác trong trường hợp này vì ta so sánh hai `double` và đã lọc `NaN`/vô cực. Giá trị `-0.0` được chấp nhận thành `int` 0, vì `int` không phân biệt dấu của số 0.

**Bẫy biên `long`:** không sao chép mù quáng điều kiện `value > Long.MAX_VALUE` vào phép kiểm tra `double` → `long`. `Long.MAX_VALUE` khi chuyển sang `double` làm tròn thành `2^63`, nên phép so sánh đó có thể chấp nhận sai một giá trị ngoài miền. Nếu cần chuyển `double` thành `long` đúng với **giá trị `double` đang có**, dùng giới hạn nửa mở, chẳng hạn:

```java
static long requireLong(double value) {
    if (!Double.isFinite(value)
            || value != Math.rint(value)
            || value < -0x1.0p63
            || value >= 0x1.0p63) {
        throw new ArithmeticException("Cannot convert exactly to long");
    }
    return (long) value;
}
```

`0x1.0p63` là literal `double` biểu diễn chính xác `2^63`; `Long.MIN_VALUE == -2^63` vẫn hợp lệ, nhưng `+2^63` đã vượt `Long.MAX_VALUE`. Dẫu vậy, nếu một `long` lớn đã được đổi sang `double` và mất bit trước đó, cả kiểm tra này lẫn cast đều không thể phát hiện hay sửa lại giá trị `long` ban đầu. Khi có dữ liệu thập phân ban đầu, hãy giữ dữ liệu trong `BigDecimal` và dùng phương thức `...ValueExact()` ngay trên nguồn đó.

## 6. Chọn phép toán theo yêu cầu nghiệp vụ

| Yêu cầu | Lựa chọn phù hợp | Cảnh báo |
| --- | --- | --- |
| Chuyển `int` thành `long` | Gán trực tiếp `long n = i;` | Giữ nguyên giá trị |
| Chuyển `long` thành `int`, phải nguyên vẹn | `Math.toIntExact(n)` | Ném `ArithmeticException` nếu ngoài miền |
| Chuyển `long` thành `int`, cố ý giữ bit thấp | `(int) n` | Có thể đổi dấu/giá trị |
| Chuyển số thực thành số nguyên bằng cách cắt về 0 | Cast sau khi xác thực phạm vi nếu cần | `NaN` và quá miền có kết quả đặc biệt |
| Làm tròn đến số nguyên gần nhất, hòa về phía dương | `Math.round(...)` | Không dùng để chứng minh đầu vào nằm trong miền |
| Làm tròn đến số nguyên gần nhất, hòa về số chẵn | `Math.rint(double)` hoặc `BigDecimal` `HALF_EVEN` | `rint` vẫn trả `double` |
| Làm tròn tiền thành hai chữ số thập phân | `BigDecimal.setScale(2, mode)` | Quy tắc `mode` phải đến từ yêu cầu nghiệp vụ |
| Chuyển decimal thành `int` không mất thông tin | `BigDecimal.intValueExact()` | Cần là số nguyên và vừa miền `int` |
| Chuyển `BigInteger` thành `long` không mất thông tin | `BigInteger.longValueExact()` | Không vừa miền sẽ ném lỗi |
| Nhập chuỗi số nguyên từ người dùng | `Integer.parseInt` / `Long.parseLong` | Lỗi chuỗi/miền là `NumberFormatException` |

Không chọn `Math.round` chỉ vì tên gọi nghe “an toàn”. `round`, `cast` và `exact` trả lời ba câu hỏi khác nhau về cách xử lý phần lẻ, tràn miền và thất thoát dữ liệu.

## 7. Ví dụ Java 17 đầy đủ và kết quả kiểm chứng

Lưu tệp `NumericConversionDemo.java`:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class NumericConversionDemo {
    public static void main(String[] args) {
        System.out.println("cast 2.9 = " + (int) 2.9);
        System.out.println("cast -2.9 = " + (int) -2.9);
        System.out.println("cast byte 130 = " + (byte) 130);
        System.out.println("cast NaN = " + (int) Double.NaN);
        System.out.println("cast 1e20 = " + (int) 1e20);

        System.out.println("round 2.5 = " + Math.round(2.5));
        System.out.println("round -2.5 = " + Math.round(-2.5));
        System.out.println("rint 2.5 = " + Math.rint(2.5));
        System.out.println("rint 3.5 = " + Math.rint(3.5));
        System.out.println("floor -2.2 = " + Math.floor(-2.2));
        System.out.println("ceil -2.2 = " + Math.ceil(-2.2));

        BigDecimal negativeHalf = new BigDecimal("-2.5");
        BigDecimal positiveHalf = new BigDecimal("2.5");
        System.out.println("HALF_UP -2.5 = "
                + negativeHalf.setScale(0, RoundingMode.HALF_UP));
        System.out.println("HALF_EVEN 2.5 = "
                + positiveHalf.setScale(0, RoundingMode.HALF_EVEN));

        System.out.println("Math.toIntExact(42) = " + Math.toIntExact(42L));
        System.out.println("BigDecimal 12.00 exact = "
                + new BigDecimal("12.00").intValueExact());

        try {
            Math.toIntExact(3_000_000_000L);
        } catch (ArithmeticException e) {
            System.out.println("long to int overflow: ArithmeticException");
        }

        try {
            new BigDecimal("12.5").intValueExact();
        } catch (ArithmeticException e) {
            System.out.println("decimal with fraction: ArithmeticException");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac NumericConversionDemo.java
java NumericConversionDemo
```

Kết quả:

```text
cast 2.9 = 2
cast -2.9 = -2
cast byte 130 = -126
cast NaN = 0
cast 1e20 = 2147483647
round 2.5 = 3
round -2.5 = -2
rint 2.5 = 2.0
rint 3.5 = 4.0
floor -2.2 = -3.0
ceil -2.2 = -2.0
HALF_UP -2.5 = -3
HALF_EVEN 2.5 = 2
Math.toIntExact(42) = 42
BigDecimal 12.00 exact = 12
long to int overflow: ArithmeticException
decimal with fraction: ArithmeticException
```

Ví dụ dùng literal `double` và `BigDecimal` từ chuỗi rõ ràng để tránh trộn lẫn kết quả làm tròn nhị phân với quy tắc rounding thập phân.

## 8. Khi dữ liệu đến từ chuỗi hoặc phép tính thập phân

Hãy xét yêu cầu: người dùng nhập số lượng sản phẩm ở dạng `"12.00"`, hệ thống chỉ chấp nhận số lượng nguyên trong miền `int`. Cách phù hợp là:

```java
import java.math.BigDecimal;

public class QuantityParser {
    public static int parseQuantity(String raw) {
        BigDecimal parsed = new BigDecimal(raw);
        int quantity = parsed.intValueExact();
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity must be non-negative");
        }
        return quantity;
    }

    public static void main(String[] args) {
        System.out.println(parseQuantity("12.00")); // 12
    }
}
```

```bash
javac QuantityParser.java
java QuantityParser
```

```text
12
```

`new BigDecimal(raw)` có thể ném `NumberFormatException` nếu chuỗi không hợp lệ; `intValueExact()` có thể ném `ArithmeticException` nếu số có phần lẻ hoặc vượt miền. Sau đó mới kiểm tra luật nghiệp vụ (ví dụ không âm). Như vậy mỗi lỗi có ý nghĩa riêng và không bị âm thầm cắt dữ liệu.

Trái lại, nếu yêu cầu là *cho phép số lẻ và làm tròn số lượng* thì phải xác định `RoundingMode` rõ ràng trước khi dùng `intValueExact()`. Không tự quyết định `HALF_UP` hay `HALF_EVEN` chỉ dựa vào thói quen.

Một tình huống tương tự là tính tiền. Nếu tổng tiền cần hai chữ số thập phân, giữ giá trị trong `BigDecimal`, tính với độ chính xác phù hợp, rồi dùng `setScale(2, mode)` theo yêu cầu kế toán. Cast sang `int` chỉ giữ phần nguyên và khiến phần lẻ bị loại bỏ, không tương đương làm tròn đến đơn vị tiền nhỏ.

## 9. Những lỗi phổ biến và cách phát hiện

1. **Cho rằng cast là làm tròn.** `(int) -2.9 == -2`, còn `Math.floor(-2.9) == -3.0`; các phép toán phục vụ mục đích khác nhau.
2. **Cho rằng cast tự báo tràn.** `(int) 3_000_000_000L` cho giá trị âm; `Math.toIntExact(3_000_000_000L)` mới ném `ArithmeticException`.
3. **Cho rằng `Math.round` bảo đảm không vượt miền.** `Math.round(Double.POSITIVE_INFINITY)` trả `Long.MAX_VALUE`; `Math.round(Double.NaN)` trả `0`.
4. **Trộn quy tắc hòa khi làm tròn.** `Math.round(-2.5) == -2`, `HALF_UP` của `BigDecimal("-2.5")` cho `-3`, còn `HALF_EVEN` chọn chữ số cuối chẵn.
5. **Tạo `BigDecimal` từ `double` mà không hiểu dữ liệu nguồn.** `new BigDecimal(0.1)` ghi lại giá trị nhị phân của `double`; nếu yêu cầu đúng số thập phân `0.1` từ đầu vào, dùng chuỗi hoặc dữ liệu decimal nguyên gốc.
6. **Làm mất dữ liệu trước khi kiểm tra `exact`.** `(long) someDouble` đã có thể cắt/bão hòa; `Math.toIntExact((long) someDouble)` chỉ kiểm tra `long` sau bước cast, không thể kiểm tra phần lẻ hoặc vô cực của nguồn ban đầu.
7. **Dùng `Long.MAX_VALUE` làm cận trên cho `double` → `long`.** Giá trị `Long.MAX_VALUE` khi chuyển sang `double` không biểu diễn chính xác; cận trên an toàn là `value < 0x1.0p63`.
8. **Chỉ kiểm tra phần lẻ mà quên giới hạn kiểu đích.** `1e20` là một giá trị `double` không có phần lẻ, nhưng không vừa trong `int` hoặc `long`; kiểm tra `x == Math.rint(x)` là chưa đủ.
9. **Nhầm chữ `Exact` là “luôn cho ra đáp án thập phân chính xác”.** `BigDecimal.intValueExact` bảo đảm chuyển integer value hiện có, không sửa sai số từ phép tính `double` trước đó.
10. **Bỏ qua kiểu trả về.** `Math.round(float)` trả `int`, `Math.round(double)` trả `long`, còn `Math.floor`/`Math.ceil`/`Math.rint` trả `double`. Việc ép tiếp sang kiểu nhỏ hơn cần kiểm tra riêng.
11. **Dùng `parseInt` để hiểu chuỗi decimal.** `"12.00"` không hợp lệ với `Integer.parseInt` dù có thể được `BigDecimal.intValueExact()` chuyển thành 12 sau khi parse thành decimal.
12. **Lấy `RoundingMode` từ trực giác.** Quy tắc kế toán, điều chỉnh điểm, thống kê và hiển thị có thể khác nhau; phải ghi rõ cách xử lý phần lẻ, dấu âm và trường hợp đúng giữa hai mức.

## 10. Bài tập tự kiểm tra

Hãy dự đoán kết quả hoặc ngoại lệ trước khi chạy Java:

```java
(int) -7.9
Math.round(-7.5)
Math.rint(7.5)
Math.toIntExact((long) Integer.MAX_VALUE + 1)
new java.math.BigDecimal("15.000").intValueExact()
new java.math.BigDecimal("15.001").intValueExact()
new java.math.BigDecimal("-1.5")
    .setScale(0, java.math.RoundingMode.HALF_UP)
```

**Đáp án theo thứ tự:**

1. `-7` vì cast cắt phần lẻ về phía 0.
2. `-7L` vì `Math.round` chọn số nguyên gần nhất, đúng giữa hai số chọn phía dương.
3. `8.0` vì `Math.rint` hòa về số chẵn.
4. `ArithmeticException` vì lớn hơn `Integer.MAX_VALUE`.
5. `15` vì phần thập phân chỉ chứa các chữ số 0.
6. `ArithmeticException` vì còn phần lẻ khác 0.
7. `-2` dưới dạng `BigDecimal` vì `HALF_UP` ra xa 0 khi đúng giữa hai mức.

**Quy tắc ghi nhớ:** khi muốn **thay kiểu** có thể cast (và chấp nhận quy tắc mất dữ liệu), khi muốn **chọn giá trị gần mức nào** phải chỉ rõ rounding, còn khi muốn **bảo toàn giá trị** hãy dùng `...Exact()` hoặc một phép kiểm tra tương đương **trước** khi thực hiện cast. Nếu dữ liệu bắt đầu từ chuỗi thập phân, giữ chúng ở dạng decimal cho đến khi hoàn tất xác thực và làm tròn theo yêu cầu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-conversions-010">Không có phép cast trực tiếp giữa kiểu số và boolean</a>

<details>
<summary>Xem chi tiết</summary>

# Không có phép cast trực tiếp giữa kiểu số và boolean trong Java

## 1. Vấn đề cần hiểu

Khi học **chuyển kiểu (type conversion)** trong Java Core — Numbers, ta thường thấy `int` đổi sang `long`, `double` đổi sang `float`, hoặc `char` tham gia phép tính số. Nhưng Java có một ranh giới rõ ràng: **`boolean` không thuộc nhóm kiểu số và không có phép cast trực tiếp hợp lệ giữa `boolean` và các kiểu số nguyên thủy**.

Cụ thể, những ý tưởng quen thuộc trong một số ngôn ngữ khác **không hợp lệ trong Java**:

```java
// CÁC DÒNG SAU CỐ Ý SAI, KHÔNG BIÊN DỊCH:
boolean a = (boolean) 1;
boolean b = (boolean) 0;
int c = (int) true;
double d = (double) false;
boolean e = 42;
int f = true;
```

`0` không tự động là `false` và `1` không tự động là `true` trong ngôn ngữ Java. Nếu dữ liệu số mã hóa ý nghĩa đúng/sai, **chính chương trình phải định nghĩa cách chuyển ý nghĩa đó** bằng phép so sánh, toán tử điều kiện, xác thực hoặc một phương thức có tên.

Sau bài này, bạn sẽ:

- Phân biệt các kiểu số `byte`, `short`, `char`, `int`, `long`, `float`, `double` với `boolean`.
- Hiểu tại sao phép gán, cast, toán tử điều kiện và lời gọi phương thức cần kiểu `boolean` thực sự.
- Chuyển **ý nghĩa** “có/không”, “bật/tắt” từ dữ liệu số sang `boolean` bằng quy tắc rõ ràng.
- Mã hóa `boolean` thành `0/1` theo hợp đồng giao tiếp, không nhầm với cast.
- Xử lý các trường hợp biên của số thực, `NaN`, `Infinity`, chuỗi `"true"`/`"1"` và bitmask.
- Đọc đúng lỗi biên dịch, tránh cast lòng vòng qua wrapper hoặc `Object`.

Các ví dụ đầy đủ dùng **Java 17+**.

## 2. Hệ kiểu primitive: số và boolean là hai nhóm riêng

Java có tám kiểu primitive:

| Nhóm | Kiểu | Ghi chú |
| --- | --- | --- |
| Số nguyên | `byte`, `short`, `int`, `long` | Giá trị số nguyên có dấu |
| Liên quan số nguyên/UTF-16 | `char` | Đơn vị mã UTF-16 16 bit không dấu, có thể tham gia một số phép số học |
| Số thực | `float`, `double` | Số chấm động nhị phân |
| Logic | `boolean` | Chỉ có hai giá trị ngôn ngữ: `true` và `false` |

Trong các **numeric conversion**, Java cho phép nhiều chuyển đổi giữa các kiểu số với nhau, chẳng hạn `int → long`, `int → double`, `double → int` hoặc `char → int`, có thể là chuyển rộng hoặc chuyển hẹp tùy loại.

Nhưng `boolean` không là kiểu số, không có quy tắc `numeric promotion` như `int`, và không tham gia các **widening/narrowing primitive numeric conversions**. Theo đặc tả ngôn ngữ, không có cast primitive trực tiếp hợp lệ từ kiểu số sang `boolean` hay ngược lại.

Điều này vẫn đúng cho cả:

- `byte`, `short`, `int`, `long`;
- `char`, dù giá trị của nó có thể đọc được dưới dạng số nguyên;
- `float`, `double`;
- Các literal số như `0`, `1`, `0.0`, `1.0F`;
- Các giá trị âm và dương: Java không có cú pháp cho phép dùng `-1` làm `true` như một điều kiện.

Cần nhớ rằng **giá trị 0/1 dùng để mã hóa boolean trong dữ liệu** là một thỏa thuận ứng dụng, khác với **quy tắc chuyển kiểu của ngôn ngữ Java**.

## 3. Tại sao `(boolean) 1` không hoạt động?

Hãy xét:

```java
int count = 1;
// boolean hasItems = (boolean) count; // LỖI BIÊN DỊCH
```

Khi trình biên dịch gặp `(boolean) count`, nó kiểm tra có phép chuyển kiểu được JLS cho phép giữa kiểu `int` và kiểu đích `boolean` hay không. **Không có** phép chuyển đổi đó, nên mã không hợp lệ. Đây là lỗi **thời điểm biên dịch**, không phải “chạy thử mới ra `false` hay `true`”.

Tương tự:

```java
boolean enabled = true;
// int n = (int) enabled; // LỖI BIÊN DỊCH
```

Việc thêm nhiều dấu ngoặc không thay đổi quy tắc:

```java
// boolean a = ((boolean) (1));     // vẫn sai
// int b = ((int) (false));         // vẫn sai
```

Không có hậu tố literal hoặc tùy chọn `javac` thông thường nào khiến `boolean` trở thành kiểu số. Java chủ ý yêu cầu điều kiện logic rõ ràng, thay vì âm thầm suy luận “khác 0 là đúng”.

### 3.1. So sánh với cast số hợp lệ

```java
int quantity = 5;
long larger = (long) quantity;  // hợp lệ: int -> long
double exactHere = (double) quantity; // hợp lệ: int -> double

double estimate = 5.9;
int truncated = (int) estimate; // hợp lệ nhưng có mất phần thập phân: 5
```

Tính **hợp lệ** của phép cast và việc nó **giữ chính xác dữ liệu** là hai câu hỏi khác nhau. Với `boolean` và kiểu số, câu hỏi đầu đã thất bại: ngôn ngữ không định nghĩa cast primitive giữa chúng.

## 4. Điều kiện trong `if`, `while`, `for` và toán tử logic yêu cầu boolean

Một lỗi chuyển từ các ngôn ngữ cho phép “truthy/falsy” sang Java là viết:

```java
int count = 3;
// if (count) { ... } // LỖI BIÊN DỊCH
```

Trong Java, biểu thức điều kiện của `if`, `while`, `do ... while` và phần điều kiện của `for` thường phải là `boolean` hoặc `Boolean` có thể unbox thành `boolean`. Một `int` riêng lẻ không đủ. Tương tự, toán tử `!` và `&&`/`||` áp dụng cho giá trị logic; `!count` và `count && true` không hợp lệ.

Cách viết đúng phụ thuộc **ý nghĩa nghiệp vụ**:

```java
int count = 3;

if (count > 0) {
    System.out.println("Có phần tử");
}

if (count != 0) {
    System.out.println("Khác không");
}
```

Hai điều kiện này không đồng nghĩa khi `count` âm:

- `count > 0`: chỉ nhận số dương.
- `count != 0`: nhận cả số âm và số dương.
- `count == 1`: chỉ nhận đúng mã `1`.
- `count >= minimum`: thể hiện điều kiện ngưỡng.

**Không tự chọn `!= 0` chỉ để làm trình biên dịch hết báo lỗi.** Hãy chọn phép so sánh diễn đạt đúng dữ liệu đang biểu thị.

### 4.1. Bảng diễn giải 0/1 thường gặp

| Dữ liệu số | Ý định nghiệp vụ | Biểu thức Java |
| --- | --- | --- |
| Bộ đếm `count` | “Có ít nhất một phần tử” | `count > 0` |
| Mã giao thức quy ước 0/1 | “Đúng khi đúng bằng 1” | `code == 1` |
| Trạng thái quy ước mọi số khác 0 là bật | “Bật khi khác 0” | `state != 0` |
| Một bit trong `flags` | “Bit được chọn đang bật” | `(flags & MASK) != 0` |
| Giá trị đo `v` | “Lớn hơn ngưỡng” | `v > threshold` |
| Tập bit phải có đầy đủ | “Tất cả bit trong mask đều bật” | `(flags & MASK) == MASK` |

Đây là **những quy tắc quy ước khác nhau**, không phải các kiểu cast tương đương.

## 5. Cách chuyển ý nghĩa kiểu số sang boolean bằng phép so sánh

Nếu dữ liệu là số đếm, biểu thức `count > 0` trả về `boolean`:

```java
int count = 3;
boolean hasItems = count > 0; // true
```

Nếu dữ liệu là cờ mã hóa bằng `0` hoặc `1`:

```java
int activeCode = 1;
boolean active = activeCode == 1; // true
```

Nếu hợp đồng chấp nhận mọi số khác 0:

```java
int status = -2;
boolean nonzero = status != 0; // true
```

**Sự khác biệt:** ba biến `hasItems`, `active`, `nonzero` có cùng kiểu `boolean`, nhưng chúng được tạo từ **ba điều kiện rõ ràng**.

### 5.1. Đặc biệt với số thực

Với `double`/`float`, không nên tùy tiện dùng `value != 0.0` làm điều kiện “giá trị số hợp lệ” hoặc “có số”:

- `0.0 == -0.0` là `true`; hai giá trị này khác bit dấu nhưng cùng so sánh bằng về mặt `==`.
- `Double.NaN != 0.0` là `true`, nhưng `NaN` **không phải** một độ lớn đo được.
- `Double.POSITIVE_INFINITY != 0.0` là `true`; giá trị vô cực cũng thường không hợp lệ cho phép đo hữu hạn.
- Giá trị rất nhỏ nhưng khác `0.0` vẫn khiến `value != 0.0` thành `true`; yêu cầu nghiệp vụ “đủ lớn để đáng kể” có thể cần ngưỡng.

Ví dụ nếu chỉ muốn giá trị dương **hữu hạn**:

```java
double reading = 2.5;
boolean validPositive = Double.isFinite(reading) && reading > 0.0;
```

Nếu cần “khác 0 và hữu hạn”, viết `Double.isFinite(reading) && reading != 0.0`. Nếu cần so sánh số thực với độ chênh lệch chấp nhận được, chọn tolerance theo bài toán và lưu ý overflow/underflow trong công thức so sánh. Không có một epsilon duy nhất đúng cho mọi dữ liệu.

## 6. Cách mã hóa boolean thành số: dùng toán tử điều kiện

Để xuất `boolean` thành mã nguyên, hãy nêu rõ quy ước bằng **toán tử điều kiện ba ngôi `?:`**:

```java
boolean enabled = true;
int encoded = enabled ? 1 : 0;  // 1
```

Mẫu này đọc là: nếu `enabled` đúng, trả `1`; ngược lại trả `0`. Với mã dài hơn hoặc dùng chung ở nhiều nơi, viết một phương thức đặt tên rõ ràng:

```java
static int toZeroOne(boolean value) {
    return value ? 1 : 0;
}
```

Đây là **một mảnh phương thức** cần đặt trong class để gọi, không phải chương trình độc lập.

Có thể chọn mã khác:

```java
boolean allowed = false;
int protocolCode = allowed ? 100 : 400; // 400
```

Không có quy định chung rằng boolean phải ánh xạ sang `0/1`, `-1/0` hay bất kỳ cặp giá trị nào. Điều này do hợp đồng trao đổi dữ liệu quyết định.

### 6.1. Ternary không phải primitive cast

Biểu thức `enabled ? 1 : 0` trả một giá trị số dựa trên điều kiện boolean; nó **không chuyển kiểu `boolean` thành `int`**.

Ngược lại, `code == 1` trả boolean bằng so sánh số; nó **không chuyển kiểu `int` sang `boolean`**.

Nhận diện đúng thao tác giúp kiểm tra dễ hơn: nếu hệ thống đổi quy ước từ mã `1` sang mã `100`, ta sửa quy tắc mapping mà không thay đổi các quy tắc type conversion của Java.

## 7. Mã 0/1 phải được xác thực nếu input bên ngoài có thể sai

Nếu giao thức tuyên bố chỉ có `0` và `1`, dùng `code != 0` sẽ **âm thầm chấp nhận** `2`, `-1` hoặc `999` là true. Điều đó có thể che giấu dữ liệu lỗi.

Cách tốt hơn là định nghĩa phương thức chỉ nhận hai mã hợp lệ:

```java
static boolean fromZeroOne(int code) {
    if (code == 0) {
        return false;
    }
    if (code == 1) {
        return true;
    }
    throw new IllegalArgumentException("Expected 0 or 1: " + code);
}
```

Nếu một API phải cho phép mọi số khác 0 có nghĩa là true, hãy viết và tài liệu hóa rõ `code != 0`. Hai chính sách không nên bị trộn lẫn.

### Ví dụ hoàn chỉnh: mapping có kiểm tra

Lưu tệp **`NumericBooleanMapping.java`**:

```java
public class NumericBooleanMapping {
    static boolean fromZeroOne(int code) {
        if (code == 0) return false;
        if (code == 1) return true;
        throw new IllegalArgumentException("Expected 0 or 1: " + code);
    }

    static int toZeroOne(boolean value) {
        return value ? 1 : 0;
    }

    public static void main(String[] args) {
        System.out.println(fromZeroOne(0));
        System.out.println(fromZeroOne(1));
        System.out.println(toZeroOne(true));
        System.out.println(toZeroOne(false));

        try {
            fromZeroOne(2);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }
    }
}
```

Chạy:

```bash
javac --release 17 NumericBooleanMapping.java
java NumericBooleanMapping
```

Stdout chính xác:

```text
false
true
1
0
Expected 0 or 1: 2
```

Lợi ích của phương thức này là **giữ hợp đồng dữ liệu ở một nơi**, phân biệt lỗi input khỏi mã hợp lệ và tránh sao chép các phép so sánh khác nhau khắp chương trình.

## 8. Wrapper `Boolean` và `Number` cũng không phải cầu nối cast

Các wrapper như `Integer`, `Long`, `Double` là đối tượng đại diện các giá trị số và kế thừa `Number`. `Boolean` đại diện giá trị logic; nó **không kế thừa `Number`**.

Ví dụ hợp lệ:

```java
Integer boxedInt = Integer.valueOf(1);
Boolean boxedFlag = Boolean.TRUE;

int number = boxedInt.intValue();
boolean flag = boxedFlag.booleanValue();
```

Nhưng không có cách cast trực tiếp `Integer` thành `Boolean` để nhận “1 = true”, hoặc cast `Boolean` thành `Integer` để nhận “true = 1”.

Đừng dùng ép kiểu gián tiếp qua `Object` để “né” lỗi trình biên dịch. Ví dụ nếu một tham chiếu `Object` đang chứa `Integer`, cast nó thành `Boolean` sẽ dẫn đến lỗi kiểu lúc chạy (`ClassCastException`), thay vì tự động chuyển giá trị số thành logic. Việc boxing/unboxing không sáng tạo quy tắc numeric-to-boolean.

### 8.1. Unboxing đúng nghĩa là gì?

`Boolean enabled = Boolean.TRUE;` có thể unbox thành `boolean` trong ngữ cảnh cần primitive boolean. Đó là **`Boolean → boolean`**, không phải `int → boolean`.

Cần đề phòng `null`:

```java
Boolean optional = null;
// if (optional) { } // chạy sẽ ném NullPointerException khi unbox
boolean safelyTrue = Boolean.TRUE.equals(optional); // false
```

Nếu dữ liệu có ba trạng thái “true”, “false”, “chưa biết”, đừng vội ép mọi trường hợp vào số `0/1` rồi làm mất trạng thái null. Nên chọn mô hình dữ liệu thể hiện rõ đủ trạng thái.

## 9. Parse chuỗi: `Boolean.parseBoolean` không hiểu chuỗi "1" là true

Đây là một nhầm lẫn phổ biến khi chuyển từ biểu diễn số trong file, JSON hoặc form nhập:

```java
System.out.println(Boolean.parseBoolean("true"));  // true
System.out.println(Boolean.parseBoolean("TrUe"));  // true
System.out.println(Boolean.parseBoolean("1"));     // false
System.out.println(Boolean.parseBoolean("0"));     // false
System.out.println(Boolean.parseBoolean(" TRUE ")); // false
```

`Boolean.parseBoolean(String)` trả `true` **chỉ khi** chuỗi khác null và bằng `"true"` theo so sánh không phân biệt hoa/thường. Các chuỗi khác trả `false`. Nó không parse `"1"` thành true, và không tự trim khoảng trắng.

Nếu dữ liệu đầu vào quy ước `"0"`/`"1"`, có thể parse sang số rồi chuyển bằng chính sách:

```java
String raw = "1";
int code = Integer.parseInt(raw);
boolean active = code == 1; // chỉ đúng khi hợp đồng cho phép các giá trị khác
```

Nếu hợp đồng chỉ nhận `0/1`, hãy gọi `fromZeroOne(code)` ở mục trên để **báo lỗi** khi nhận `2`. `Integer.parseInt` có thể ném `NumberFormatException` nếu dữ liệu không phải số nguyên hợp lệ, vì vậy API nhận dữ liệu bên ngoài cần xử lý trường hợp này theo yêu cầu.

Với một trường boolean thực sự trong JSON, nhiều thư viện mapping sẽ dùng giá trị JSON `true`/`false`. Nếu bên ngoài gửi `0/1`, bạn đang xử lý một **quy ước schema** và phải kiểm tra chính sách mapping của thư viện; Java primitive cast không tự xử lý việc ấy.

## 10. Bitmask: giá trị bitwise là số, điều kiện được tạo bằng so sánh

Các toán tử `&`, `|`, `^` và `~` cũng xuất hiện ở chủ đề Numbers. Với toán hạng kiểu `int`, `&` trả về **`int`**, không phải `boolean`.

Ví dụ:

```java
int flags = 0b0101;
int mask = 0b0100;

// boolean invalid = flags & mask; // LỖI: int không thể gán sang boolean
boolean hasFlag = (flags & mask) != 0; // true
```

Đọc logic đúng:

1. `flags & mask` tạo một **giá trị nguyên** chứa các bit chung.
2. `!= 0` **so sánh** giá trị đó với số 0 và tạo ra `boolean`.
3. Biến `hasFlag` nhận kết quả của phép so sánh.

Nếu mask có nhiều bit và cần tất cả đều bật, dùng `(flags & mask) == mask`. Đừng nhầm kiểm tra “ít nhất một bit” với kiểm tra “mọi bit”.

### 10.1. `&` và `|` cũng hoạt động trên boolean

Java có hai nhóm overload ngữ nghĩa của `&`, `|` và `^`:

- Với **hai toán hạng nguyên**, chúng thao tác từng bit và cho giá trị số nguyên.
- Với **hai toán hạng boolean**, chúng thực hiện AND/OR/XOR logic và cho `boolean`.

`&&` và `||` chỉ dành cho logic và có khả năng short circuit; `&`/`|` trên boolean vẫn đánh giá cả hai toán hạng.

Ví dụ hợp lệ:

```java
boolean a = true;
boolean b = false;
boolean x = a & b; // false
boolean y = a | b; // true
boolean z = a ^ b; // true
```

Ngược lại, `int bits = 1 & 2;` cho `0`; `boolean test = 1 & 2;` không hợp lệ do vế phải là `int`. Việc cùng dùng ký hiệu `&` không tạo một cast tự động giữa hai loại kết quả.

## 11. Ví dụ Java hoàn chỉnh: số tạo điều kiện, điều kiện tạo mã số

Lưu tệp **`NumericBooleanConditions.java`**:

```java
public class NumericBooleanConditions {
    public static void main(String[] args) {
        int count = 3;
        double measurement = 0.0;

        boolean hasItems = count != 0;
        boolean isPositive = count > 0;
        boolean hasNonzeroMeasurement = measurement != 0.0;

        System.out.println(hasItems);
        System.out.println(isPositive);
        System.out.println(hasNonzeroMeasurement);

        System.out.println(hasItems ? 1 : 0);
        System.out.println(false ? 1 : 0);

        System.out.println(Double.NaN != 0.0);
        System.out.println(Double.isFinite(Double.NaN));

        int flags = 0b0101;
        System.out.println((flags & 0b0100) != 0);
        System.out.println((flags & 0b0010) != 0);
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac --release 17 NumericBooleanConditions.java
java NumericBooleanConditions
```

**Stdout chính xác:**

```text
true
true
false
1
0
true
false
true
false
```

Giải thích:

- `count != 0` và `count > 0` đều đúng cho `3` nhưng không cùng hợp đồng ở mọi giá trị.
- `measurement != 0.0` sai cho `0.0`.
- Toán tử `?:` mã hóa `boolean` thành `int` theo quy ước `true ↦ 1` và `false ↦ 0`.
- `Double.NaN != 0.0` đúng vì so sánh không bằng với NaN luôn đúng (trừ các chi tiết riêng của một số phương thức so sánh đối tượng); `Double.isFinite(Double.NaN)` trả false.
- Bitmask `0101 & 0100` khác 0, còn `0101 & 0010` bằng 0.

Đây là ví dụ cho thấy **rõ ràng từng bước chuyển ý nghĩa**, hoàn toàn không dùng cast trực tiếp giữa kiểu số và boolean.

## 12. Lỗi biên dịch thường gặp và cách sửa

Các đoạn trong bảng là mã **cố ý không hợp lệ**. Mọi cách sửa cần được đánh giá theo yêu cầu thực, vì khác ý nghĩa sẽ cho kết quả khác.

| Mã sai | Lý do | Cách sửa có chủ đích |
| --- | --- | --- |
| `boolean b = (boolean) 1;` | Không có numeric-to-boolean cast | `boolean b = (1 == 1);` hoặc `true` nếu là hằng |
| `int n = (int) true;` | Không có boolean-to-numeric cast | `int n = true ? 1 : 0;` |
| `if (count) { ... }` | `int` không là điều kiện boolean | `if (count > 0) { ... }` nếu “có phần tử” |
| `while (n) { ... }` | `n` không là `boolean` | `while (n != 0) { ... }` nếu dữ liệu xác nhận |
| `boolean x = 1 & 2;` | `1 & 2` có kiểu `int` | `boolean x = (1 & 2) != 0;` |
| `boolean a = !1;` | `!` cần boolean | `boolean a = !(1 != 0);` nếu thực sự muốn phủ định “khác 0” |
| `boolean b = "1";` | Chuỗi không là boolean | Parse/mapping theo schema, không cast |
| `Boolean b = Integer.valueOf(1);` | Wrapper khác kiểu | `Boolean b = Integer.valueOf(1) == 1;` nếu chỉ nhận mã 1 |

Khi IDE hoặc `javac` báo `incompatible types` hoặc `bad operand types`, hãy nhìn vào **kiểu của từng biểu thức**, không chỉ kiểu biến đích. Gắn thêm `(boolean)` hoặc `(int)` sẽ không sửa được lỗi chuyển giữa hai họ kiểu này.

### 12.1. Điều gì xảy ra nếu ép kiểu một `Object`?

Giả sử một giá trị tại runtime được lưu dưới dạng `Integer`. Ép tham chiếu `Object` thành `Boolean` (khi biểu thức được chấp nhận bởi cơ chế reference casting) không chuyển số `1` thành `true`; kiểu object thực tế vẫn là `Integer` và cast sai có thể gây `ClassCastException`. Đây là **reference cast khác với primitive value conversion**. Tốt nhất định nghĩa phương thức mapping tường minh ở biên nhận dữ liệu.

## 13. Câu hỏi tự luyện có đáp án

**Bài 1 — Phân loại mã hợp lệ.** Mã nào biên dịch: `boolean a = 0;`, `boolean b = 0 == 0;`, `int c = true ? 1 : 0;` và `int d = (int) false;`?

Đáp án: `b` và `c` hợp lệ; `a` và `d` sai vì Java không chuyển primitive số ↔ boolean.

**Bài 2 — Chọn điều kiện đúng.** Với `int count = -1;`, `count != 0` và `count > 0` trả gì?

Đáp án: `true` và `false`. Nếu count là số lượng hợp lệ phải không âm, cần xử lý giá trị âm như một lỗi hoặc xác định thêm hợp đồng.

**Bài 3 — Kiểm tra 0/1 nghiêm ngặt.** Một API chỉ chấp nhận mã `0` và `1`. Vì sao `code != 0` không đủ? Hãy viết hàm nhận 0/1 và báo lỗi nếu nhận 2.

Đáp án: `2 != 0` cũng true, dù 2 không phải mã hợp lệ. Dùng `fromZeroOne` ở mục 7, ném `IllegalArgumentException` cho các giá trị khác.

**Bài 4 — Đổi boolean sang số.** Với `boolean permitted = false;`, làm sao xuất `0`?

Đáp án: `int code = permitted ? 1 : 0;`. Đây là biểu thức chọn giá trị số, không phải cast.

**Bài 5 — NaN.** Với `double value = Double.NaN;`, `value != 0.0` và `Double.isFinite(value)` bằng gì?

Đáp án: `true` và `false`. Vì vậy không dùng riêng `!= 0` để kết luận “số đo hợp lệ”.

**Bài 6 — Wrapper.** Tại sao `Boolean.parseBoolean("1")` trả false? Nó có phải chuyển từ số nguyên 1 sang boolean không?

Đáp án: phương thức chỉ nhận chuỗi tương đương `"true"` (không phân biệt hoa/thường), và `"1"` không thỏa. Đây là **parsing chuỗi**, không phải numeric cast.

**Bài 7 — Bitmask.** Với `flags = 0b0101` và `mask = 0b0100`, biểu thức `flags & mask` có kiểu gì? Viết điều kiện boolean kiểm tra cờ.

Đáp án: `int`, giá trị `4`. `boolean enabled = (flags & mask) != 0;` trả true.

**Bài 8 — Toán tử logic.** Có thể viết `1 && 1` trong Java không? Làm sao diễn đạt ý “hai số đều khác 0”?

Đáp án: không; `&&` yêu cầu boolean. Viết `(a != 0) && (b != 0)` với hai biến số `a`/`b`.

**Bài 9 — Nullability.** Điều gì xảy ra khi unbox `Boolean b = null;` vào biến `boolean`?

Đáp án: xảy ra `NullPointerException` ở runtime. Nếu null có ý nghĩa “chưa có dữ liệu”, phải xử lý riêng; ví dụ kiểm tra `Boolean.TRUE.equals(b)` khi chỉ cần nhận đúng `true`.

## 14. Checklist khi xử lý Numbers và chuyển kiểu

- [ ] Tôi biết `boolean` là primitive logic riêng, không thuộc các numeric primitive.
- [ ] Tôi không dùng `(boolean) number`, `(int) boolean` hay gán `0/1` trực tiếp cho `boolean`.
- [ ] Tôi phân biệt numeric conversion với điều kiện logic và mapping nghiệp vụ.
- [ ] Tôi dùng `count > 0`, `code == 1` hoặc `code != 0` có lý do rõ, thay vì tự coi mọi số có “truthiness”.
- [ ] Tôi kiểm tra miền input trước khi chuyển mã giao tiếp, không âm thầm chấp nhận mã lỗi.
- [ ] Tôi biết toán tử `?:` có thể chọn `1/0` để mã hóa boolean theo hợp đồng.
- [ ] Tôi không nhầm `Boolean.parseBoolean("1")` với phép chuyển số sang boolean.
- [ ] Tôi xét đúng các trường hợp `0.0`, `-0.0`, `NaN` và vô cực nếu điều kiện lấy từ số thực.
- [ ] Tôi biết `flags & mask` có kiểu số, và `(flags & mask) != 0` mới là boolean.
- [ ] Tôi không coi `Boolean` là `Number` hoặc dùng reference cast như một phép chuyển nội dung.
- [ ] Tôi đã chạy ví dụ, thử input ngoài miền và tự giải thích được lỗi biên dịch.

## 15. Tài liệu chuẩn để đối chiếu

- [JLS 17 §4.2 — Primitive Types and Values](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.2): phân biệt numeric primitive và `boolean`.
- [JLS 17 §5.1.2 — Widening Primitive Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.2): chuyển rộng primitive, không chuyển sang `boolean`.
- [JLS 17 §5.1.3 — Narrowing Primitive Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.3): chuyển hẹp giữa các kiểu primitive được phép.
- [JLS 17 §5.5 — Casting Contexts](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.5): những chuyển đổi mà casting context cho phép.
- [JLS 17 §14.9 — The `if` Statement](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.9): điều kiện phải có kiểu boolean/Boolean.
- [JLS 17 §15.22 — Bitwise and Logical Operators](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.22): cùng ký hiệu `&`/`|`/`^` nhưng toán hạng số và boolean có hợp đồng khác nhau.
- [Java SE 17 Boolean API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Boolean.html): `parseBoolean`, `valueOf` và wrapper boolean.

Hãy luôn diễn đạt rõ **dữ liệu số đang có ý nghĩa gì** trước khi đưa nó vào điều kiện. Java không có cast trực tiếp giữa số và `boolean`, vì vậy phép so sánh, phương thức mapping và xác thực input là nơi phải thể hiện quy tắc mà chương trình thực sự mong muốn.

</details>

- [Quay lại đầu trang](#back-to-top)
