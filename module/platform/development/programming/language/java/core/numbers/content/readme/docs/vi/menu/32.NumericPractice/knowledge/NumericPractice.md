<a id="back-to-top"></a>

# Thực hành

## Menu
- [Viết bảng kiểm chứng kiểu kết quả của các biểu thức số](#numeric-practice-001)
- [Xây dựng phép tính kích thước có kiểm tra tràn](#numeric-practice-002)
- [Đọc ghi số theo nhiều cơ số và dạng không dấu](#numeric-practice-003)
- [Khảo sát sai số của 0.1, tổng lặp và thứ tự cộng](#numeric-practice-004)
- [Xây dựng hàm so sánh gần bằng có hợp đồng rõ ràng](#numeric-practice-005)
- [Tính giai thừa hoặc dãy số lớn bằng BigInteger](#numeric-practice-006)
- [Thiết kế phép tính thập phân với MathContext và RoundingMode](#numeric-practice-007)
- [So sánh HashSet với TreeSet khi chứa BigDecimal](#numeric-practice-008)
- [Parse và format cùng một giá trị theo nhiều locale](#numeric-practice-009)
- [Mô phỏng số ngẫu nhiên có thể tái lập bằng seed](#numeric-practice-010)
- [So sánh bộ đếm thường, atomic và adder](#numeric-practice-011)
- [Hoàn thiện ví dụ tích hợp cùng bộ kiểm thử dữ liệu biên](#numeric-practice-012)

## <a id="numeric-practice-001">Viết bảng kiểm chứng kiểu kết quả của các biểu thức số</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành: Viết bảng kiểm chứng kiểu kết quả của các biểu thức số trong Java

## 1. Mục tiêu thực hành

Khi đọc một biểu thức Java như `byte + short`, `int / int`, `long + float` hoặc `1 << 33`, ta cần trả lời **hai câu hỏi khác nhau**:

1. **Kiểu của biểu thức tại thời điểm biên dịch (*compile-time type*) là gì?**
2. **Giá trị khi chạy (*runtime value*) là bao nhiêu**, và có khả năng mất dữ liệu, làm tròn hoặc tràn số không?

Java không tự động giữ kiểu nhỏ nhất của các toán hạng. Nhiều phép toán trên `byte`, `short` hoặc `char` tạo kết quả `int`. Một phép chia số nguyên không bỗng trở thành số thực vì kết quả toán học có phần dư. Phép cộng có `float` và `long` có thể cho ra `float`, dù `long` chứa nhiều chữ số hơn. Việc hiểu chính xác **quy tắc suy ra kiểu** sẽ giúp bạn tránh lỗi biên dịch, chọn phép ép kiểu hợp lý và phát hiện bug số học.

Bài thực hành này có:

- Quy trình lập bảng kiểm chứng theo từng biểu thức.
- Bảng tham chiếu các quy tắc numeric promotion và ngoại lệ thường gặp.
- Chương trình **Java 17+ chạy độc lập**, trong đó mỗi kết quả được gán cho biến có **kiểu khai báo tường minh**, để trình biên dịch kiểm chứng.
- Các biểu thức **không biên dịch**, phân tích lý do và cách sửa.
- Bài tập tự điền bảng, kèm đáp án về cả kiểu lẫn giá trị.

## 2. Định nghĩa bảng kiểm chứng

Một bảng hữu ích cần ít nhất các cột:

| Biểu thức | Kiểu toán hạng | Quy tắc áp dụng | Kiểu kết quả | Giá trị khi chạy / lưu ý |
| --- | --- | --- | --- | --- |
| `a + b` | `byte` + `short` | Binary numeric promotion | `int` | Tùy giá trị `a`, `b` |
| `a / b` | `int` / `int` | Chia nguyên | `int` | Bỏ phần lẻ theo hướng về 0 |
| `a + b` | `long` + `float` | Binary numeric promotion | `float` | Có thể mất chính xác khi chuyển `long` sang `float` |

Không suy ra kiểu từ cách `System.out.println` hiển thị. Ví dụ giá trị in ra `3` có thể đến từ `byte`, `int`, `long`, `float` hoặc `double` (với dạng hiển thị khác nhau ở một số trường hợp). Hãy dựa vào **cú pháp biểu thức và quy tắc kiểu**; kết quả runtime chỉ là bước kiểm chứng bổ sung.

Với `var`, Java cũng xác định một kiểu cụ thể lúc biên dịch. `var` không phải kiểu số động, và không khiến kết quả chọn kiểu dựa theo độ lớn giá trị lúc chạy.

## 3. Quy tắc số 1: kiểu của literal và biến

| Cú pháp | Kiểu | Giải thích |
| --- | --- | --- |
| `123` | `int` | Literal nguyên không hậu tố mặc định là `int` nếu hợp lệ |
| `123L` | `long` | Hậu tố `L` hoặc `l`; ưu tiên `L` để dễ đọc |
| `1.5` | `double` | Literal có dấu thập phân mặc định là `double` |
| `1.5f` | `float` | Hậu tố `f` hoặc `F` |
| `1.5d` | `double` | Hậu tố `d` hoặc `D` |
| `1e3` | `double` | Ký pháp số mũ của literal floating point |
| `'A'` | `char` | Literal ký tự; giá trị số Unicode code unit của `'A'` là 65 |
| `true` | `boolean` | Không phải kiểu số |
| `"123"` | `String` | Chuỗi, không phải số |

Dấu `-` ở `-123` là **toán tử một ngôi** áp dụng lên literal, không phải là một phần của literal nguyên thông thường. Hai giá trị đặc biệt `-2147483648` và `-9223372036854775808L` được ngôn ngữ hỗ trợ theo quy tắc về literal thập phân và unary minus; không suy luận rằng mọi số nguyên ngoài miền đều hợp lệ.

Ví dụ:

~~~java
int whole = 12;
long large = 12L;
float measure = 12.5f;
double ratio = 12.5;

var inferredWhole = 10 / 4;  // int, giá trị 2
var inferredRatio = 10 / 4.0; // double, giá trị 2.5
~~~

`var` được suy ra **`int`** và **`double`** ở hai câu lệnh cuối. Không có phép chuyển đổi tự động theo giá trị kết quả.

## 4. Quy tắc số 2: binary numeric promotion

Đối với các toán tử số học nhị phân thường gặp như `+`, `-`, `*`, `/` và `%`, Java dùng *binary numeric promotion*. Sau unboxing (nếu cần), quy tắc khái quát là:

1. Nếu có toán hạng `double` → kết quả thuộc kiểu `double`.
2. Nếu không có `double` nhưng có `float` → kết quả thuộc kiểu `float`.
3. Nếu không có hai kiểu trên nhưng có `long` → kết quả thuộc kiểu `long`.
4. Nếu còn lại các kiểu nguyên nhỏ, kết quả thuộc kiểu **`int`**.

| Toán hạng trái | Toán hạng phải | Kiểu của `left + right` |
| --- | --- | --- |
| `byte` | `byte` | `int` |
| `byte` | `short` | `int` |
| `char` | `int` | `int` |
| `int` | `int` | `int` |
| `int` | `long` | `long` |
| `long` | `float` | `float` |
| `float` | `double` | `double` |
| `long` | `double` | `double` |

Một chỗ dễ nhầm: **`long + float` trả `float`**, không phải `long` và cũng không nhất thiết là `double`. `float` là floating point 32-bit, có thể không giữ được tất cả chữ số của một `long` lớn. Kiểu kết quả do **quy tắc promotion**, không do việc kiểu nào “chứa nhiều số hơn”.

Đối với `byte`, `short`, `char`, kết quả cộng thường là `int`:

~~~java
byte b = 10;
short s = 20;
char c = 'A';

int sum = b + s;    // int, giá trị 30
int next = c + 1;   // int, giá trị 66
~~~

Câu lệnh `byte bad = b + s;` không biên dịch nếu không có cast: kết quả là `int`, không thể gán ngầm vào `byte`.

## 5. Quy tắc số 3: unary promotion, bitwise và shift

Các toán tử một ngôi `+`, `-`, `~` thực hiện **unary numeric promotion**. Với `byte`, `short` hoặc `char`, toán hạng được nâng thành `int`:

~~~java
byte b = 10;

int positive = +b; // 10
int negative = -b; // -10
int bits = ~b;     // -11
~~~

Toán tử shift (`<<`, `>>`, `>>>`) không sử dụng binary numeric promotion như phép cộng. Nó nâng kiểu từng toán hạng theo quy tắc riêng; **kiểu kết quả bằng kiểu toán hạng trái sau unary promotion**:

| Biểu thức | Kiểu kết quả | Giá trị |
| --- | --- | --- |
| `(byte) 3 << 1` | `int` | `6` |
| `1 << 3` | `int` | `8` |
| `1L << 3` | `long` | `8` |
| `1 << 33` | `int` | `2` |
| `1L << 33` | `long` | `8589934592` |
| `~(short) 0` | `int` | `-1` |

Với kiểu `int`, Java chỉ sử dụng **5 bit thấp** của khoảng dịch; vì thế `1 << 33` tương đương `1 << 1`, kết quả là `2`. Với kiểu `long`, dùng **6 bit thấp** của khoảng dịch, nên `1L << 33` là `8589934592`.

Đừng suy ra kiểu shift bằng kiểu toán hạng phải: `1 << 3L` vẫn có kiểu `int` vì toán hạng trái là `int`. Tuy nhiên, tốt nhất chỉ dùng khoảng dịch hợp lệ với thiết kế nghiệp vụ, không dựa vào việc Java bỏ qua các bit cao để làm *validation*.

## 6. Quy tắc số 4: phép chia nguyên và cách tạo số thực

| Biểu thức | Kiểu | Kết quả |
| --- | --- | --- |
| `7 / 2` | `int` | `3` |
| `-7 / 2` | `int` | `-3` |
| `7L / 2` | `long` | `3` |
| `7 / 2.0` | `double` | `3.5` |
| `7 / 2.0f` | `float` | `3.5` |
| `(double) 7 / 2` | `double` | `3.5` |
| `(double) (7 / 2)` | `double` | `3.0` |

Điểm khác biệt ở hai dòng cuối là **thứ tự đánh giá**:

~~~java
int numerator = 7;
int denominator = 2;

double right = (double) numerator / denominator; // 3.5
double tooLate = (double) (numerator / denominator); // 3.0
~~~

Ở `tooLate`, `numerator / denominator` đã chia nguyên và ra 3 **trước khi cast**. Phép cast từ `3` thành `double` chỉ tạo `3.0`, không phục hồi phần dư.

Phép chia nguyên cho 0 ném `ArithmeticException` tại runtime. Phép chia floating point cho 0 có thể trả vô cực hoặc `NaN` theo ngữ nghĩa floating point; đừng coi hai trường hợp như nhau.

## 7. Quy tắc số 5: so sánh, điều kiện và toán tử ba ngôi

Toán tử so sánh số học như `<`, `<=`, `>`, `>=` và `==` áp dụng các quy tắc chuyển đổi toán hạng cần thiết, nhưng **kiểu kết quả là `boolean`**.

~~~java
int i = 7;
long l = 9L;
boolean smaller = i < l;  // true
boolean equal = i == l;   // false
~~~

Toán tử điều kiện `condition ? left : right` có nhiều quy tắc, **không phải lúc nào cũng áp dụng máy móc bảng binary numeric promotion**.

| Biểu thức | Kiểu kết quả | Vì sao |
| --- | --- | --- |
| `true ? 7 : 0.5` | `double` | Hai nhánh có `int` và `double` |
| `true ? 7 : 9L` | `long` | Hai nhánh có `int` và `long` |
| `true ? (byte) 1 : 2` | `byte` | Quy tắc đặc biệt: literal `int` 2 là constant expression biểu diễn được trong `byte` |
| `true ? 1 : 2.0f` | `float` | Hai nhánh có `int` và `float` |

Quy tắc nhánh `byte` và literal `int` có thể khiến kết quả trở thành `byte`, không phải `int`; đây là lý do cần học quy tắc riêng cho conditional expression thay vì áp dụng bảng promotion của `+`.

Cũng phải phân biệt **kiểu của biểu thức** với **nhánh được đánh giá**: `condition` quyết định nhánh chạy, nhưng kiểu biểu thức được xác định ngay khi biên dịch từ kiểu hai nhánh và quy tắc của `?:`.

## 8. Ngoại lệ quan trọng: assignment, compound assignment, ++ và --

Bản thân phép gán có kiểu tương ứng với kiểu biến bên trái nếu hợp lệ. Một số cú pháp còn có **narrowing conversion ngầm** mà phép cộng bình thường không có.

~~~java
byte b = 127;
b += 1; // Biên dịch; b trở thành -128 (overflow kiểu byte).
~~~

Đối với phép gán hợp thành, `b += 1` có ngữ nghĩa tương tự `b = (byte) (b + 1)`, ngoại trừ vế trái chỉ được đánh giá một lần. Vì thế:

~~~java
byte b = 100;

// byte wrong = b + 1; // Không biên dịch: b + 1 là int.
b += 1;                // Biên dịch, giá trị b là 101.
b++;                   // Biên dịch, tăng b thêm 1.
~~~

Tương tự, với một biến `byte`, biểu thức `b++` có **kiểu `byte`** (kết quả hậu tố là giá trị cũ) dù phép cộng số học trung gian được thực hiện theo quy tắc nâng kiểu.

| Cú pháp | Kiểu của biểu thức / hành vi |
| --- | --- |
| `b + 1` với `b` là `byte` | `int` |
| `b += 1` với `b` là `byte` | Phép gán hợp thành hợp lệ, kết quả có kiểu `byte` |
| `b++` với `b` là `byte` | Kiểu `byte`, trả giá trị cũ rồi cập nhật |
| `++b` với `b` là `byte` | Kiểu `byte`, trả giá trị mới |
| `byte k = 1 + 2;` | Có thể biên dịch nhờ constant expression bằng 3 nằm trong miền `byte` |
| `byte k = b + 2;` | Không biên dịch vì `b` là biến, biểu thức có kiểu `int` |

Không nên nghĩ `b += 1` kiểm tra tràn. Khi chạy quá `Byte.MAX_VALUE`, kết quả có thể wrap. Nếu nghiệp vụ yêu cầu phát hiện tràn, thiết kế kiểm tra/biểu diễn rộng hơn trước khi thu hẹp kiểu.

## 9. Autoboxing, unboxing và kiểu đối tượng số

Java có các wrapper class như `Integer`, `Long`, `Float` và `Double`. Với toán tử số học, wrapper có thể bị **unbox** thành primitive trước khi promotion:

~~~java
Integer boxed = 5;
int result = boxed + 1; // int, giá trị 6
~~~

**Không** kết luận `Integer + int` tạo ra `Integer`; biểu thức cộng có kiểu `int`. Nếu một wrapper bằng `null`, biểu thức cần unboxing có thể ném `NullPointerException`:

~~~java
Integer missing = null;
try {
    int result = missing + 1;
    System.out.println(result);
} catch (NullPointerException ex) {
    System.out.println("null unboxing");
}
~~~

Chuỗi `String` là trường hợp khác: toán tử `+` nối chuỗi khi một toán hạng được dùng trong string concatenation.

~~~java
String message = "count=" + 7; // "count=7", kiểu String
~~~

Không dùng toán tử `+` trực tiếp giữa hai `BigDecimal` hoặc hai `BigInteger`. Đây là **đối tượng**, không phải primitive numeric:

~~~java
import java.math.BigDecimal;
import java.math.BigInteger;

BigDecimal decimal = new BigDecimal("0.1")
        .add(new BigDecimal("0.2")); // BigDecimal, đúng 0.3

BigInteger integer = BigInteger.ONE.add(
        BigInteger.valueOf(2)
); // BigInteger, giá trị 3
~~~

Kiểu của lời gọi `decimal.add(...)` do **chữ ký method** quyết định; không áp dụng binary numeric promotion cho hai object đó.

## 10. Bảng kiểm chứng hoàn chỉnh: kiểu + giá trị cụ thể

Giả định các biến ban đầu:

~~~java
byte b = 10;
short s = 20;
char c = 'A';
int i = 7;
long l = 9L;
float f = 2.5f;
double d = 0.5;
Integer boxed = 5;
~~~

Bảng điền sẵn:

| Biểu thức | Kiểu kết quả | Giá trị | Lý do ngắn |
| --- | --- | --- | --- |
| `b + s` | `int` | `30` | `byte` và `short` nâng lên `int` |
| `c + 1` | `int` | `66` | `char` nâng lên `int` |
| `i + l` | `long` | `16` | Có `long` |
| `l + f` | `float` | `11.5` | Có `float`, không có `double` |
| `f + d` | `double` | `3.0` | Có `double` |
| `-b` | `int` | `-10` | Unary numeric promotion |
| `~b` | `int` | `-11` | Bitwise complement của `int` 10 |
| `b << 1` | `int` | `20` | Shift dùng kiểu toán hạng trái sau promotion |
| `1 << 33` | `int` | `2` | Khoảng shift `int` chỉ xét 5 bit thấp |
| `1L << 33` | `long` | `8589934592` | Toán hạng trái là `long` |
| `i / 2` | `int` | `3` | Chia nguyên |
| `i / 2.0` | `double` | `3.5` | Có `double` |
| `i < l` | `boolean` | `true` | So sánh |
| `true ? i : d` | `double` | `7.0` | Hai nhánh `int` và `double` |
| `true ? (byte) 1 : 2` | `byte` | `1` | Trường hợp đặc biệt của `?:` |
| `Math.round(2.6f)` | `int` | `3` | Overload nhận `float` trả `int` |
| `Math.round(2.6d)` | `long` | `3` | Overload nhận `double` trả `long` |
| `"n=" + i` | `String` | `n=7` | Nối chuỗi |
| `boxed + 1` | `int` | `6` | Unboxing rồi cộng |
| `new BigDecimal("0.1").add(new BigDecimal("0.2"))` | `BigDecimal` | `0.3` | Method `add` trả `BigDecimal` |

Giá trị và kiểu được xác định bằng ngữ nghĩa Java, không phải vì bảng liệt kê trùng tên wrapper. Phần kế tiếp có chương trình **biên dịch được**, dùng biến khai báo tường minh để buộc compiler kiểm chứng các kiểu trên.

## 11. Chương trình Java 17+ chạy độc lập để xác nhận bảng

Tạo file `NumericExpressionTypeTable.java`:

~~~java
import java.math.BigDecimal;
import java.math.BigInteger;

public class NumericExpressionTypeTable {
    static void row(String expression, String resultType, Object value) {
        System.out.println(expression + " | " + resultType + " | " + value);
    }

    public static void main(String[] args) {
        byte b = 10;
        short s = 20;
        char c = 'A';
        int i = 7;
        long l = 9L;
        float f = 2.5f;
        double d = 0.5;
        Integer boxed = 5;

        // Kiểu được kiểm tra thực sự khi javac gán kết quả cho biến.
        int sumSmall = b + s;
        int sumChar = c + 1;
        long sumLong = i + l;
        float sumFloat = l + f;
        double sumDouble = f + d;
        int unaryMinus = -b;
        int bitComplement = ~b;
        int shifted = b << 1;
        int maskedShift = 1 << 33;
        long longShift = 1L << 33;
        int integerDivision = i / 2;
        double realDivision = i / 2.0;
        boolean compare = i < l;
        double conditional = true ? i : d;
        byte specialConditional = true ? (byte) 1 : 2;
        int roundedFloat = Math.round(2.6f);
        long roundedDouble = Math.round(2.6d);
        String concatenated = "n=" + i;
        int unboxed = boxed + 1;
        BigDecimal exactDecimal = new BigDecimal("0.1")
                .add(new BigDecimal("0.2"));
        BigInteger exactInteger = BigInteger.ONE
                .add(BigInteger.valueOf(2));

        row("b + s", "int", sumSmall);
        row("c + 1", "int", sumChar);
        row("i + l", "long", sumLong);
        row("l + f", "float", sumFloat);
        row("f + d", "double", sumDouble);
        row("-b", "int", unaryMinus);
        row("~b", "int", bitComplement);
        row("b << 1", "int", shifted);
        row("1 << 33", "int", maskedShift);
        row("1L << 33", "long", longShift);
        row("i / 2", "int", integerDivision);
        row("i / 2.0", "double", realDivision);
        row("i < l", "boolean", compare);
        row("true ? i : d", "double", conditional);
        row("true ? (byte) 1 : 2", "byte", specialConditional);
        row("Math.round(2.6f)", "int", roundedFloat);
        row("Math.round(2.6d)", "long", roundedDouble);
        row("\"n=\" + i", "String", concatenated);
        row("boxed + 1", "int", unboxed);
        row("BigDecimal.add", "BigDecimal", exactDecimal);
        row("BigInteger.add", "BigInteger", exactInteger);

        byte nearLimit = 127;
        byte before = nearLimit++;
        row("nearLimit++ (old)", "byte", before);
        row("nearLimit (new)", "byte", nearLimit);
    }
}
~~~

Chạy:

~~~bash
javac NumericExpressionTypeTable.java
java NumericExpressionTypeTable
~~~

Stdout:

~~~text
b + s | int | 30
c + 1 | int | 66
i + l | long | 16
l + f | float | 11.5
f + d | double | 3.0
-b | int | -10
~b | int | -11
b << 1 | int | 20
1 << 33 | int | 2
1L << 33 | long | 8589934592
i / 2 | int | 3
i / 2.0 | double | 3.5
i < l | boolean | true
true ? i : d | double | 7.0
true ? (byte) 1 : 2 | byte | 1
Math.round(2.6f) | int | 3
Math.round(2.6d) | long | 3
"n=" + i | String | n=7
boxed + 1 | int | 6
BigDecimal.add | BigDecimal | 0.3
BigInteger.add | BigInteger | 3
nearLimit++ (old) | byte | 127
nearLimit (new) | byte | -128
~~~

### Tại sao chương trình kiểm chứng được kiểu?

Mỗi biến có kiểu tường minh như `long sumLong = i + l;`, `float sumFloat = l + f;` hoặc `byte specialConditional = true ? (byte) 1 : 2;`. Trình biên dịch bảo đảm **kết quả có thể gán** cho kiểu khai báo đó, còn quy tắc biểu thức xác định kiểu thật sự. Để phân biệt kiểu biểu thức với những chuyển đổi gán ngầm được phép (`int` sang `long` chẳng hạn), cần tiếp tục phân tích theo quy tắc trong các mục 3–9, không chỉ dựa trên việc lệnh gán biên dịch thành công.

Việc truyền biến primitive vào tham số `Object value` của `row` khiến nó được **boxing** để in. Vì vậy, nếu đổi hàm sang in `value.getClass()`, bạn đang xem **lớp wrapper sau boxing**, không phải cơ chế suy ra kiểu primitive gốc. Cột kiểu ở đây được viết tường minh theo phân tích compile-time.

## 12. Các trường hợp không biên dịch và cách sửa

Những ví dụ sau **cố ý sai**, không dán chung vào chương trình `NumericExpressionTypeTable.java`:

~~~java
byte b = 10;
short s = 20;

byte sum = b + s;
// ERROR: b + s có kiểu int, không thể chuyển ngầm về byte.

float f = 2.5;
// ERROR: literal 2.5 là double, không chuyển ngầm về float.

int whole = 3 / 2.0;
// ERROR: biểu thức có kiểu double, không chuyển ngầm về int.

BigDecimal decimal =
        new BigDecimal("1") + new BigDecimal("2");
// ERROR: toán tử + không hỗ trợ hai BigDecimal.
~~~

Cách sửa:

~~~java
byte b = 10;
short s = 20;

int sum = b + s;                     // Tránh cast hẹp nếu không cần.
float f = 2.5f;                      // Literal float.
double quotient = 3 / 2.0;          // Giữ phần thập phân.

BigDecimal decimal = new BigDecimal("1")
        .add(new BigDecimal("2"));   // Dùng phương thức add.
~~~

Đây là đoạn mã minh họa; cần đặt trong method/lớp phù hợp và thêm `import java.math.BigDecimal;` nếu dùng độc lập.

Một cast như `byte sum = (byte) (b + s);` sẽ giúp biên dịch, nhưng **không đảm bảo** tổng nằm trong phạm vi `byte`. Nếu điều quan trọng là giữ nguyên tổng, dùng kiểu rộng hơn (`int`); nếu điều quan trọng là giới hạn miền, kiểm tra trước khi cast.

## 13. Những góc khuất nâng cao khi xác định kiểu

### `Math` có nhiều overload

`Math.round(float)` trả `int`, `Math.round(double)` trả `long`. Vì thế hậu tố của literal làm khác kiểu kết quả:

~~~java
int a = Math.round(2.6f);
long b = Math.round(2.6);
~~~

`Math.pow(2, 3)` trả `double` vì overload của `Math.pow` nhận hai `double` và trả `double`, dù các literal đầu vào có kiểu `int`. Cần tra **chữ ký method** thay vì tự áp dụng quy tắc toán tử số học.

### Overflow không tự nâng kiểu

~~~java
int overflowed = Integer.MAX_VALUE + 1;
System.out.println(overflowed); // -2147483648

long preserved = (long) Integer.MAX_VALUE + 1;
System.out.println(preserved);  // 2147483648
~~~

Trong phép cộng đầu tiên, hai toán hạng đều kiểu `int`, nên phép toán vẫn trả `int` và wrap. Việc **gán** kết quả của `Integer.MAX_VALUE + 1` sang biến `long` cũng không sửa được lỗi nếu phép cộng đã thực hiện ở `int`. Muốn thực hiện phép cộng với kiểu rộng hơn, phải chuyển ít nhất một toán hạng **trước phép cộng**.

### Loss of precision khi kiểu kết quả là floating point

~~~java
long exact = 9_007_199_254_740_993L;
float changed = exact + 0.0f;
System.out.println(changed); // một xấp xỉ float, không giữ toàn bộ chữ số
~~~

Quy tắc kiểu vẫn là `float`. `float` không thể giữ mọi số nguyên lớn biểu diễn được bằng `long`. Cũng không nên kết luận toán hạng nào “rộng” hơn chỉ từ số bit lưu trữ: floating point có phạm vi độ lớn rộng nhưng độ chính xác nguyên hữu hạn.

### `var` không chữa phép toán sai

~~~java
var quotient = 7 / 2;       // int, giá trị 3
var precise = 7 / 2.0;      // double, giá trị 3.5
var joined = "n=" + 7;      // String
~~~

`var` chỉ bỏ phần viết tên kiểu của biến local; các quy tắc kiểu của biểu thức vẫn không đổi.

### Constant expression có thể thu hẹp khi gán

~~~java
byte a = 1 + 2;   // Hợp lệ: biểu thức hằng int có giá trị 3.

int n = 1;
 // byte b = n + 2; // Không hợp lệ: n không phải constant variable.
~~~

Quy tắc narrowing ngầm cho constant expression còn phụ thuộc dạng phép gán và miền giá trị. Nó **không đổi kiểu của biểu thức** `1 + 2` từ `int` thành `byte`; chỉ có nghĩa trình biên dịch cho phép chuyển kết quả hằng sang `byte` ở ngữ cảnh cụ thể.

## 14. Cách tự tạo bảng để chẩn đoán lỗi thật

Thực hiện theo quy trình:

1. **Ghi kiểu của từng operand**: literal nguyên, literal thực, biến primitive, wrapper hay object?
2. **Khoanh toán tử hoặc lời gọi method**: phép toán nhị phân, unary, shift, so sánh, `?:`, phép gán, hay overloaded method?
3. **Áp dụng đúng quy tắc**: đừng dùng bảng `+` cho toán tử shift hay `?:`; đừng dùng numeric promotion cho `BigDecimal.add`.
4. **Ghi kiểu compile-time** *trước khi* tính giá trị.
5. **Tính giá trị** với quy tắc chia nguyên, overflow, cast, thứ tự đánh giá và giới hạn floating point.
6. **Kiểm tra bằng mã biên dịch**: dùng assignment typed, tìm lỗi conversion không hợp lệ và dùng output để kiểm tra giá trị.
7. **Ghi cảnh báo** khi phép tính có thể mất chữ số, overflow, unboxing null hoặc kết quả khác biểu thức toán học mong đợi.

Ví dụ mẫu:

| Biểu thức | Nhận diện | Kiểu | Giá trị | Cảnh báo |
| --- | --- | --- | --- | --- |
| `(double) (7 / 2)` | Chia `int` trước, cast sau | `double` | `3.0` | Phần thập phân đã mất |
| `(double) 7 / 2` | `double` chia `int` | `double` | `3.5` | Không bị chia nguyên |
| `Integer.MAX_VALUE + 1` | `int + int` | `int` | `-2147483648` | Overflow |
| `Math.round(2.6f)` | Overload `float` | `int` | `3` | Không trả `float` |

## 15. Bài tập: tự điền bảng kiểu và giá trị

Hãy xác định **kiểu của biểu thức** trước, sau đó mới tính giá trị. Giả sử:

~~~java
byte b = 4;
short s = 6;
char c = 'B';        // 66
int i = 9;
long l = 5L;
float f = 0.5f;
double d = 2.0;
Integer boxed = 3;
~~~

| STT | Biểu thức | Kiểu bạn dự đoán | Giá trị bạn dự đoán |
| ---: | --- | --- | --- |
| 1 | `b + s` | ? | ? |
| 2 | `c - 1` | ? | ? |
| 3 | `i / 2` | ? | ? |
| 4 | `i / 2.0f` | ? | ? |
| 5 | `l + f` | ? | ? |
| 6 | `f + d` | ? | ? |
| 7 | `~b` | ? | ? |
| 8 | `b << 2` | ? | ? |
| 9 | `1L << 5` | ? | ? |
| 10 | `i > l` | ? | ? |
| 11 | `true ? b : 2.0` | ? | ? |
| 12 | `boxed * 2` | ? | ? |
| 13 | `"value=" + i` | ? | ? |
| 14 | `Math.round(3.6f)` | ? | ? |
| 15 | `Math.pow(2, 3)` | ? | ? |

Đáp án:

| STT | Kiểu đúng | Giá trị | Quy tắc |
| ---: | --- | --- | --- |
| 1 | `int` | `10` | `byte + short` nâng lên `int` |
| 2 | `int` | `65` | `char` nâng lên `int` |
| 3 | `int` | `4` | Chia nguyên 9/2 |
| 4 | `float` | `4.5` | Có toán hạng `float` |
| 5 | `float` | `5.5` | `long + float` ra `float` |
| 6 | `double` | `2.5` | Có `double` |
| 7 | `int` | `-5` | Unary ~ trên `int` 4 |
| 8 | `int` | `16` | Shift operand trái nâng thành `int` |
| 9 | `long` | `32` | Dịch trái trên `long` |
| 10 | `boolean` | `true` | So sánh `9 > 5` |
| 11 | `double` | `4.0` | Hai nhánh `byte` và `double` |
| 12 | `int` | `6` | Unbox `Integer` rồi nhân |
| 13 | `String` | `value=9` | Nối chuỗi |
| 14 | `int` | `4` | `Math.round(float)` trả `int` |
| 15 | `double` | `8.0` | `Math.pow(double, double)` trả `double` |

### Thử thay đổi để hiểu sâu hơn

- Thay `l = 5L` bằng một số `long` rất lớn, thử `l + f` và quan sát sai số do `float`.
- Thay `i / 2` bằng `(double) i / 2`, so sánh kiểu và giá trị.
- Thay `b + s` bằng `b += s` và dự đoán kiểu biểu thức lẫn nguy cơ overflow.
- Thay `Math.round(3.6f)` bằng `Math.round(3.6)`: giá trị vẫn là 4 nhưng **kiểu trả về đổi từ `int` sang `long`**.
- Thử một wrapper `Integer` bằng `null` trong phép `boxed + 1` và giải thích ngoại lệ runtime.

**Kết luận:** để kiểm chứng biểu thức số trong Java, luôn bắt đầu từ **kiểu của toán hạng và quy tắc của từng toán tử**, sau đó mới tính giá trị và đánh giá nguy cơ overflow/loss of precision. Bảng kết quả cần phân biệt rõ compile-time type, giá trị runtime và cách gán, để tránh suy ra kiểu từ một dòng output.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-practice-002">Xây dựng phép tính kích thước có kiểm tra tràn</a>

<details>
<summary>Xem chi tiết</summary>

# Xây dựng phép tính kích thước có kiểm tra tràn trong Java

## 1. Mục tiêu thực hành

Khi tính kích thước bộ nhớ, tập tin, ảnh, buffer hoặc số trang lưu trữ, biểu thức đơn giản như `width * height * bytesPerPixel` có thể **tràn số nguyên** (*integer overflow*) dù các đối số đầu vào đều hợp lệ riêng lẻ.

Ví dụ:

```java
int width = 70_000;
int height = 70_000;

int wrong = width * height; // 605032704, không phải 4900000000
```

Kết quả đúng là **4.900.000.000**, vượt `Integer.MAX_VALUE = 2.147.483.647`. Java không tự ném ngoại lệ cho phép nhân `int` thông thường: phần bit vượt phạm vi bị loại theo quy tắc số nguyên Java, dẫn đến kết quả sai nhưng chương trình vẫn có thể tiếp tục.

Trong các tác vụ tính kích thước, lỗi này đặc biệt nguy hiểm: phân bổ buffer thiếu byte, tính sai offset, số bản ghi hoặc giới hạn dữ liệu. Mục tiêu của bài này là xây một phép tính **rõ đơn vị, kiểm tra điều kiện, phát hiện tràn ngay tại phép toán, làm tròn an toàn và không cấp phát vượt khả năng thực tế**.

Sau bài học, bạn sẽ:
- Dùng `Math.multiplyExact`, `Math.addExact`, `Math.subtractExact` và `Math.toIntExact` đúng chỗ.
- Phân biệt mở rộng kiểu **trước** phép nhân với gán kết quả đã tràn vào `long`.
- Tính số byte một hàng, stride có alignment, tổng dung lượng, số trang cần cấp phát.
- Phân biệt **tràn số học**, **dữ liệu đầu vào không hợp lệ** và **thiếu tài nguyên vật lý**.
- Kiểm thử ca bình thường, biên `int`/`long`, số 0, số âm và bài toán vượt `long` với `BigInteger`.

## 2. Phát biểu bài toán: tính kích thước theo đơn vị

Giả sử ảnh có:
- `rows`: số hàng.
- `columns`: số điểm ảnh trên mỗi hàng.
- `bytesPerPixel`: số byte mỗi điểm ảnh.
- `alignment`: mức căn chỉnh số byte của mỗi hàng.

Không có căn chỉnh, tổng byte là:

```text
rawRowBytes = columns × bytesPerPixel
totalBytes  = rows × rawRowBytes
```

Nếu stride hàng cần là bội số của `alignment`:

```text
remainder  = rawRowBytes % alignment
padding    = (remainder == 0) ? 0 : alignment - remainder
stride     = rawRowBytes + padding
totalBytes = rows × stride
```

Phải kiểm tra `alignment > 0`, `rows >= 0`, `columns >= 0` và `bytesPerPixel >= 0` hoặc `> 0` theo quy tắc nghiệp vụ. Có thể yêu cầu kích thước ảnh dương, nhưng ví dụ chung trong bài **cho phép vùng 0 byte** để mô tả buffer rỗng.

Ví dụ: ảnh 2 hàng, 3 cột, 3 byte/pixel, alignment 4:
- Raw: `3 × 3 = 9` byte/hàng.
- Phần dư 9 % 4 = 1; cần thêm 3 byte padding.
- Stride: 12 byte/hàng.
- Tổng: `2 × 12 = 24` byte.

Điều cốt yếu: **mọi phép nhân/cộng có thể vượt giới hạn phải được kiểm tra ngay lúc thực hiện**, kể cả phép cộng padding sau khi phép nhân đầu tiên vẫn hợp lệ.

## 3. Tràn số nguyên hoạt động thế nào?

`int` có dấu 32 bit, từ `-2^31` đến `2^31 - 1`. `long` có dấu 64 bit, từ `-2^63` đến `2^63 - 1`.

Trong Java, cộng/trừ/nhân số nguyên thông thường thực hiện theo độ rộng của kiểu tính toán. Khi vượt phạm vi, kết quả quấn vòng theo 2^32 hoặc 2^64 (không tự ném ngoại lệ).

```java
int a = 70_000;
int b = 70_000;

int incorrect = a * b;       // 605032704
long stillWrong = a * b;     // 605032704L: đã tràn trước khi gán!
long correct = (long) a * b; // 4900000000L: mở rộng trước khi nhân
```

Đặt toán hạng `long` trước khi nhân quan trọng hơn kiểu biến kết quả. Ngoài ra:

```java
long checked = Math.multiplyExact((long) a, b); // 4900000000L
// Math.multiplyExact(a, b) chọn overload int và ném ArithmeticException.
```

Đây là khác biệt giữa **mở rộng phạm vi** và **kiểm tra tràn**:
- Chuyển một toán hạng từ `int` sang `long` có thể tránh tràn `int`, nhưng phép nhân `long` vẫn có thể tràn.
- `Math.multiplyExact(long, long)` phát hiện tràn `long` bằng cách ném `ArithmeticException`.
- `Math.multiplyExact(int, int)` ném nếu không thể chứa kết quả trong `int`.

Dùng overload có chủ đích theo **kiểu trả về và phạm vi kết quả mong muốn**.

## 4. Các API kiểm tra tràn trong Java 17

| API | Công dụng | Khi vượt miền |
| --- | --- | --- |
| `Math.addExact(int,int)` / `Math.addExact(long,long)` | Cộng có kiểm tra | `ArithmeticException` |
| `Math.subtractExact(...)` | Trừ có kiểm tra | `ArithmeticException` |
| `Math.multiplyExact(...)` | Nhân có kiểm tra | `ArithmeticException` |
| `Math.incrementExact(...)` / `Math.decrementExact(...)` | Tăng/giảm 1 | `ArithmeticException` |
| `Math.negateExact(...)` | Đổi dấu | `ArithmeticException` khi đổi dấu giá trị MIN |
| `Math.toIntExact(long)` | Thu hẹp từ `long` sang `int` có kiểm tra | `ArithmeticException` |

Một số ví dụ:

```java
long bytes = Math.multiplyExact(4_000L, 3_000L);
bytes = Math.multiplyExact(bytes, 4L);   // 48000000
long withHeader = Math.addExact(bytes, 128L);
long remaining = Math.subtractExact(withHeader, 128L);

int acceptableLength = Math.toIntExact(4_096L); // 4096
// Math.toIntExact(2_147_483_648L) ném ArithmeticException.
```

**Không nhầm `Math.toIntExact` với `(int) x`**: ép kiểu `(int)` chỉ giữ các bit thấp, có thể trả một số hoàn toàn khác. Cần kiểm tra **tính hợp lệ nghiệp vụ** riêng: `Math.toIntExact(-1L)` vẫn trả `-1` vì số -1 nằm trong miền `int`, trong khi độ dài mảng âm là không hợp lệ.

Nếu phép tính cần nhiều bước, mỗi bước có nguy cơ tràn đều cần được bảo vệ. Chỉ kiểm tra kết quả cuối `if (result < 0)` là **không đủ**: kết quả tràn có thể vẫn dương, như `70_000 × 70_000` đã cho.

## 5. Thiết kế API: giá trị hợp lệ và ngoại lệ

Một API tính kích thước nên tách hai loại lỗi:

1. **Đối số vi phạm quy tắc**: âm, divisor/alignment bằng 0; thường ném `IllegalArgumentException` với thông báo nêu tên tham số.
2. **Phép toán đúng về ý nghĩa nhưng vượt phạm vi kiểu**: ném `ArithmeticException` qua `Math.*Exact`.

Ví dụ phương thức tính byte dữ liệu:

```java
static long elementBytes(long count, long bytesPerElement) {
    if (count < 0 || bytesPerElement < 0) {
        throw new IllegalArgumentException("dimensions must be >= 0");
    }
    return Math.multiplyExact(count, bytesPerElement);
}
```

Đây là **đoạn phương thức minh họa**, phải đặt trong một lớp. Với `elementBytes(70_000,70_000)`, kết quả là `4_900_000_000L`. Với `elementBytes(Long.MAX_VALUE,2)`, phép nhân vượt `long` và ném `ArithmeticException`.

Lựa chọn cho phép `count = 0` và `bytesPerElement = 0` thuộc hợp đồng bài toán. Nếu “mỗi phần tử phải chiếm byte dương”, kiểm tra `bytesPerElement > 0` thay vì để 0 hợp lệ. Điều quan trọng là không âm thầm sửa giá trị đầu vào về 0 hoặc giới hạn tối đa mà người gọi không biết.

## 6. Căn chỉnh kích thước mà không làm tràn phép cộng trung gian

Một công thức dễ gặp:

```java
// Phản ví dụ: raw + alignment - 1 có thể tràn.
long stride = ((raw + alignment - 1) / alignment) * alignment;
```

Ngay cả khi số hàng có thể chứa trong `long`, biểu thức `raw + alignment - 1` có thể tràn; phép nhân với `alignment` ở bước cuối cũng có thể tràn.

Cách an toàn cho `raw >= 0` và `alignment > 0` là tính phần dư:

```java
static long alignUp(long raw, long alignment) {
    if (raw < 0 || alignment <= 0) {
        throw new IllegalArgumentException("invalid size or alignment");
    }
    long remainder = raw % alignment;
    long padding = remainder == 0 ? 0 : alignment - remainder;
    return Math.addExact(raw, padding);
}
```

Khi `remainder != 0`, `padding` thuộc `1..alignment-1`. Phép `Math.addExact` cuối phát hiện việc **không còn đủ phạm vi `long` cho kích thước đã căn chỉnh**. Không cần giả định `alignment` luôn là lũy thừa hai; thuật toán trên dùng được cho mọi alignment dương.

Ví dụ:
- `alignUp(9,4) = 12`.
- `alignUp(12,4) = 12`.
- `alignUp(0,4) = 0`.
- `alignUp(Long.MAX_VALUE-2,8)` ném `ArithmeticException` vì kích thước căn chỉnh tiếp theo vượt `Long.MAX_VALUE`.

Chỉ dùng kỹ thuật bitwise `(value + alignment - 1) & -alignment` nếu bạn đã quy định alignment là lũy thừa hai, kiểm tra miền và xử lý tràn. Nó không phải giải pháp tổng quát tự động an toàn.

## 7. Làm tròn số trang bằng chia và phần dư

Nếu `bytes` byte cần chia thành trang `pageSize` byte, số trang là **ceil(bytes/pageSize)**. Với hai số không âm và `pageSize > 0`, có thể tính:

```java
static long pageCount(long bytes, long pageSize) {
    if (bytes < 0 || pageSize <= 0) {
        throw new IllegalArgumentException("invalid page arguments");
    }
    long whole = bytes / pageSize;
    return bytes % pageSize == 0 ? whole : Math.addExact(whole, 1);
}
```

Tránh công thức `(bytes + pageSize - 1) / pageSize` nếu phép cộng có thể tràn. Với `bytes = Long.MAX_VALUE` và `pageSize = Long.MAX_VALUE`, công thức chia-dư trả 1, trong khi công thức cộng trước có thể tràn.

Ví dụ:
- 0 byte / trang 4096 byte → 0 trang.
- 4096 byte / trang 4096 byte → 1 trang.
- 4097 byte / trang 4096 byte → 2 trang.
- 48.000.000 byte / trang 4096 byte → 11.719 trang.

Với tiền, kích thước trang, phân lô hoặc các thuật toán khác, **làm tròn lên** và **căn chỉnh bội số** là các yêu cầu có quan hệ nhưng khác nhau: số trang là **số lượng**, còn stride đã căn chỉnh là **số byte**.

## 8. Chương trình Java 17 độc lập: bộ tính kích thước có kiểm tra

Tạo tệp **`CheckedSizeDemo.java`**:

```java
import java.math.BigInteger;

public class CheckedSizeDemo {
    static long nonNegative(long value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " must be >= 0");
        return value;
    }

    static long elementBytes(long count, long bytesPerElement) {
        nonNegative(count, "count");
        nonNegative(bytesPerElement, "bytesPerElement");
        return Math.multiplyExact(count, bytesPerElement);
    }

    static long alignedRowBytes(long columns, long bytesPerPixel, long alignment) {
        nonNegative(columns, "columns");
        nonNegative(bytesPerPixel, "bytesPerPixel");
        if (alignment <= 0) throw new IllegalArgumentException("alignment must be > 0");
        long raw = Math.multiplyExact(columns, bytesPerPixel);
        long remainder = raw % alignment;
        long padding = remainder == 0 ? 0 : alignment - remainder;
        return Math.addExact(raw, padding);
    }

    static long gridBytes(long rows, long columns, long bytesPerPixel, long alignment) {
        nonNegative(rows, "rows");
        long stride = alignedRowBytes(columns, bytesPerPixel, alignment);
        return Math.multiplyExact(rows, stride);
    }

    static long pageCount(long bytes, long pageBytes) {
        nonNegative(bytes, "bytes");
        if (pageBytes <= 0) throw new IllegalArgumentException("pageBytes must be > 0");
        long wholePages = bytes / pageBytes;
        return bytes % pageBytes == 0 ? wholePages : Math.addExact(wholePages, 1);
    }

    static int arrayLength(long requestedLength) {
        nonNegative(requestedLength, "requestedLength");
        return Math.toIntExact(requestedLength);
    }

    static void expectedError(String label, Class<? extends Throwable> expected,
                              Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected " + expected.getSimpleName() + ": " + label);
        } catch (Throwable failure) {
            if (!expected.isInstance(failure)) throw failure;
            System.out.println(label + "=" + failure.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        int incorrect = 70_000 * 70_000;
        System.out.println("uncheckedInt=" + incorrect);
        System.out.println("wideProduct=" + elementBytes(70_000, 70_000));
        System.out.println("rowStride=" + alignedRowBytes(3, 3, 4));
        System.out.println("smallGrid=" + gridBytes(2, 3, 3, 4));
        long imageBytes = gridBytes(3_000, 4_000, 4, 64);
        System.out.println("imageBytes=" + imageBytes);
        System.out.println("pages=" + pageCount(imageBytes, 4_096));
        System.out.println("zeroPages=" + pageCount(0, 4_096));
        System.out.println("maxPages=" + pageCount(Long.MAX_VALUE, Long.MAX_VALUE));
        System.out.println("arrayLength=" + arrayLength(4_096));

        expectedError("intOverflow", ArithmeticException.class,
                () -> Math.multiplyExact(70_000, 70_000));
        expectedError("longOverflow", ArithmeticException.class,
                () -> elementBytes(Long.MAX_VALUE, 2));
        expectedError("alignmentOverflow", ArithmeticException.class,
                () -> alignedRowBytes(Long.MAX_VALUE - 2, 1, 8));
        expectedError("negativeCount", IllegalArgumentException.class,
                () -> elementBytes(-1, 8));
        expectedError("zeroAlignment", IllegalArgumentException.class,
                () -> alignedRowBytes(3, 4, 0));
        expectedError("arrayLengthOverflow", ArithmeticException.class,
                () -> arrayLength(2_147_483_648L));

        BigInteger huge = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(8));
        System.out.println("hugeBytes=" + huge);
        expectedError("bigIntegerToLong", ArithmeticException.class, huge::longValueExact);
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac CheckedSizeDemo.java
java CheckedSizeDemo
```

Kết quả **đã kiểm chứng thực tế** khi biên dịch với `javac --release 17`:

```text
uncheckedInt=605032704
wideProduct=4900000000
rowStride=12
smallGrid=24
imageBytes=48000000
pages=11719
zeroPages=0
maxPages=1
arrayLength=4096
intOverflow=ArithmeticException
longOverflow=ArithmeticException
alignmentOverflow=ArithmeticException
negativeCount=IllegalArgumentException
zeroAlignment=IllegalArgumentException
arrayLengthOverflow=ArithmeticException
hugeBytes=73786976294838206456
bigIntegerToLong=ArithmeticException
```

**Các phép kiểm chứng quan trọng:**

1. `uncheckedInt` là ví dụ số **sai do tràn nhưng không báo lỗi**; `Math.multiplyExact` với overload `int` báo lỗi rõ ràng.
2. `wideProduct` là phép nhân theo `long`, cho kết quả 4.900.000.000.
3. `rowStride=12` là số byte hàng sau padding; `smallGrid=24` là hai hàng.
4. `imageBytes=48000000`: `4000 × 4 = 16000` byte/hàng, đã chia hết cho alignment 64; nhân 3000 hàng được 48.000.000 byte.
5. `pages=11719` chứng minh làm tròn lên bằng chia dư, không dùng cộng `pageSize-1` dễ tràn.
6. `maxPages=1` xử lý đầu vào cận trên `long` mà không tràn.
7. Những dòng mang tên `*Overflow` và lỗi đầu vào chứng minh API **ném ngoại lệ đúng loại** thay vì trả giá trị size âm/sai.
8. `hugeBytes` vượt phạm vi `long` và được tính bằng `BigInteger`; chuyển về `long` bằng `longValueExact()` báo lỗi đúng.

Hàm `expectedError` chỉ phục vụ bài kiểm thử. Ở ứng dụng thật, có thể dùng JUnit để kiểm tra `assertThrows` thay vì tự tạo helper. Vì hàm này chỉ chạy tác vụ dự kiến ném ngoại lệ, nó không dùng để nuốt những lỗi ngoài dự kiến; ngoại lệ sai loại vẫn được ném tiếp.

## 9. Phân biệt bytes, elements và số hàng

Một trong những lỗi không phải overflow nhưng cũng nguy hiểm là nhầm đơn vị:

```text
count           : số phần tử
bytesPerElement : byte/phần tử
totalBytes      : byte
rowStride       : byte/hàng
pageSize        : byte/trang
pageCount       : số trang
```

Nếu kích thước là `long bytes = 4_000_000_000L` thì **không phải** có thể tạo `new int[4_000_000_000]`. `int[]` nhận **số phần tử** kiểu `int`, không nhận số byte. Với mảng `int[]`, số byte cho dữ liệu phần tử thường là `4 × count` (chưa tính overhead đối tượng và yêu cầu bộ nhớ khác).

Để chuyển yêu cầu từ byte sang số phần tử, phải định nghĩa chính sách:
- Nếu yêu cầu phải chia hết cho kích thước mỗi phần tử, kiểm tra `bytes % bytesPerElement == 0` rồi chia.
- Nếu có thể cấp phát dư một phần tử, dùng quy tắc làm tròn lên an toàn.
- Cuối cùng kiểm tra số phần tử có nằm trong miền `int` hay không.

Ví dụ dưới đây là **đoạn phương thức minh họa**, không cần cấp phát thật:

```java
static int exactElementCount(long totalBytes, long bytesPerElement) {
    if (totalBytes < 0 || bytesPerElement <= 0) {
        throw new IllegalArgumentException("invalid byte arguments");
    }
    if (totalBytes % bytesPerElement != 0) {
        throw new IllegalArgumentException("not divisible");
    }
    return Math.toIntExact(totalBytes / bytesPerElement);
}
```

Kiểm tra `Math.toIntExact` mới chỉ xác nhận miền kiểu `int`. JVM có thể có **giới hạn mảng thực tế thấp hơn `Integer.MAX_VALUE`**, và việc cấp phát còn phụ thuộc heap còn trống. Không sử dụng `Math.toIntExact` như một bảo đảm “cấp phát sẽ luôn thành công”.

## 10. BigInteger khi yêu cầu kích thước vượt Long.MAX_VALUE

Nếu chỉ cần **ước lượng, báo cáo hoặc tính dung lượng logic** rất lớn, việc `long` không đủ không nhất thiết là dữ liệu lỗi. `BigInteger` biểu diễn số nguyên có độ lớn không bị giới hạn bởi 64 bit (vẫn chịu tài nguyên bộ nhớ):

```java
BigInteger a = BigInteger.valueOf(Long.MAX_VALUE);
BigInteger size = a.multiply(BigInteger.valueOf(8));
System.out.println(size); // 73786976294838206456
```

`BigInteger.multiply()` không gặp overflow 64 bit. Tuy nhiên, `BigInteger` không tự khiến bạn có thể cấp phát một mảng lớn như kết quả đó. Hãy tách rõ:
- **Kích thước lý thuyết**: có thể tính chính xác bằng `BigInteger`.
- **Kích thước có thể biểu diễn bằng long**: dùng `big.longValueExact()`; nếu vượt phạm vi, ném `ArithmeticException`.
- **Kích thước hợp lệ cho API cấp phát**: có thể cần `int`, ngưỡng ứng dụng và giới hạn thực tế của JVM/tài nguyên.

Không dùng `big.longValue()` khi cần phép chuyển không mất dữ liệu: nó có thể làm mất các bit cao của giá trị. Dùng `longValueExact()` để phát hiện khi không biểu diễn được.

## 11. Chính sách lỗi: fail fast, giới hạn và chia nhỏ

Khi tính kích thước, có ba cách xử lý tùy mục đích:

**1. Fail fast khi tràn.** `Math.*Exact` là lựa chọn mặc định tốt nếu giá trị sai có thể làm hỏng địa chỉ, buffer, offset hoặc kiểm tra an toàn. Ném lỗi thay vì âm thầm quay vòng.

**2. Giới hạn theo ngân sách tài nguyên.** Bên cạnh kiểm tra overflow, kiểm tra giới hạn nghiệp vụ, ví dụ `requestedBytes <= maxAllowedBytes`. Giới hạn này phải là cấu hình rõ ràng và áp dụng trước khi thử cấp phát. Không tự động “kẹp” một yêu cầu 10 GB thành 100 MB nhưng vẫn hành xử như đã cấp phát đủ 10 GB.

**3. Chia nhỏ dữ liệu.** Khi tổng kích thước hợp lệ về mặt logic nhưng không thể tạo một mảng/buffer đơn lẻ, chia thành các chunk có kích thước an toàn, theo dõi tổng số byte đã xử lý bằng `long` hoặc `BigInteger` khi thích hợp. Cần kiểm tra toán học offset và lượng còn lại ở mỗi bước.

Một điều không được lẫn lộn: **kiểm tra tràn số nguyên** chỉ trả lời “số đếm này có biểu diễn đúng trong kiểu không?”. Nó **không bảo đảm** JVM còn heap, native memory, disk space hoặc quota để thực thi.

## 12. Test kế hoạch: ca biên và bất biến

### 12.1. Test table

| Đầu vào/đặc tính | Kết quả mong đợi |
| --- | --- |
| `elementBytes(0, 4)` | `0` |
| `elementBytes(1, 4)` | `4` |
| `elementBytes(70_000, 70_000)` | `4_900_000_000L` |
| `elementBytes(Long.MAX_VALUE, 1)` | `Long.MAX_VALUE` |
| `elementBytes(Long.MAX_VALUE, 2)` | `ArithmeticException` |
| `elementBytes(-1, 4)` | `IllegalArgumentException` |
| `alignedRowBytes(3, 3, 4)` | `12` |
| `alignedRowBytes(3, 3, 1)` | `9` |
| `alignedRowBytes(3, 3, 0)` | `IllegalArgumentException` |
| `alignedRowBytes(Long.MAX_VALUE-2, 1, 8)` | `ArithmeticException` |
| `pageCount(0,4096)` | `0` |
| `pageCount(4096,4096)` | `1` |
| `pageCount(4097,4096)` | `2` |
| `pageCount(Long.MAX_VALUE, Long.MAX_VALUE)` | `1` |
| `arrayLength(2_147_483_648L)` | `ArithmeticException` |

Kiểm thử overflow bằng việc **kỳ vọng ngoại lệ**, không phải chỉ kiểm tra rằng kết quả có dấu âm. Test cả trường hợp tổng vẫn dương sau khi quay vòng.

### 12.2. Những bất biến hữu ích

Với `rows, columns, bytesPerPixel >= 0`, `alignment > 0` và giả sử không tràn:

- `stride >= rawRowBytes`.
- `stride % alignment == 0`.
- `0 <= stride - rawRowBytes < alignment`.
- `totalBytes == rows × stride` theo toán học chính xác.
- `pageCount(bytes, pageSize) × pageSize >= bytes` **về mặt toán học**; nếu nhân bằng `long` để kiểm thử, bản thân tích có thể tràn. Dùng `BigInteger` hoặc kiểm tra bằng chia/dư.
- Với `bytes > 0`, `pageCount(bytes, pageSize) >= 1`.
- Nếu `bytes` tăng mà `pageSize` cố định, `pageCount` không được giảm.

Bài test tự động có thể sinh ngẫu nhiên tham số **trong vùng hợp lệ**, tính kết quả tham chiếu bằng `BigInteger`, rồi kiểm tra rằng:
1. khi giá trị tham chiếu nằm trong `0..Long.MAX_VALUE`, hàm `long` trả cùng giá trị;
2. khi vượt phạm vi `long`, hàm ném `ArithmeticException`;
3. khi đối số âm hoặc divisor/alignment không dương, hàm ném `IllegalArgumentException`.

Cần tách test đầu vào không hợp lệ khỏi test overflow để tránh khẳng định nhầm loại lỗi.

## 13. Những lỗi phổ biến và cách sửa

### Lỗi 1: nhân int rồi mới gán long

```java
int width = 70_000, height = 70_000;

long wrong = width * height;        // sai vì overflow int
long correct = (long) width * height; // đúng trong phạm vi long
long checked = Math.multiplyExact((long) width, height);
```

### Lỗi 2: chỉ kiểm tra kết quả âm

```java
int size = width * height;
if (size < 0) {
    // Không bắt hết trường hợp overflow!
}
```

Ví dụ `70_000 × 70_000` tràn thành số dương 605.032.704. Phải kiểm tra phép toán thay vì suy từ dấu kết quả.

### Lỗi 3: dùng phép cộng trước khi chia để ceil

```java
// Có nguy cơ tràn:
long pages = (bytes + pageSize - 1) / pageSize;
```

Sửa bằng chia và phần dư, với các ràng buộc byte không âm và pageSize dương.

### Lỗi 4: căn chỉnh mà không kiểm tra cộng padding

```java
long stride = raw + padding; // có thể tràn long
```

Sửa thành `Math.addExact(raw, padding)`, sau khi đã đảm bảo padding hợp lệ.

### Lỗi 5: dùng toIntExact và nghĩ có thể cấp phát mọi mảng

`Math.toIntExact(long)` chỉ đảm bảo phép chuyển kiểu không mất thông tin. JVM và heap có giới hạn riêng. Cần ngưỡng cấp phát/chunk phù hợp, và xử lý tình huống thiếu bộ nhớ theo kiến trúc hệ thống.

### Lỗi 6: trộn đơn vị KB, KiB, MB, MiB

- `1 KB = 1000 byte`.
- `1 KiB = 1024 byte`.
- `1 MB = 1_000_000 byte`.
- `1 MiB = 1_048_576 byte`.

Đừng so sánh một giá trị byte với “MB” không xác định hệ thập phân/hệ nhị phân. Tốt hơn đặt tên biến chứa đơn vị như `fileBytes`, `pageBytes`, `maxBytes` và xác định quy ước trong tài liệu.

### Lỗi 7: cho rằng nhân chính xác đồng nghĩa kích thước hợp lệ

`Math.multiplyExact(-2, 4)` trả -8 mà **không tràn**, nhưng kích thước âm không hợp lệ. Phải xác minh tính hợp lệ **trước hoặc cùng lúc** với phép tính.

### Lỗi 8: chuyển BigInteger sang long mà không kiểm tra

`BigInteger.longValue()` có thể cắt mất dữ liệu; `longValueExact()` ném lỗi khi không chứa vừa `long`.

### Lỗi 9: tính đúng tổng nhưng sai phép nhân trung gian

Nếu phép tính gồm nhiều bước, một giá trị trung gian có thể vượt phạm vi dù kết quả sau đó được chia nhỏ trở lại. Nếu cần thực hiện phép toán chính xác trên giá trị trung gian lớn, dùng `BigInteger` cho giai đoạn đó hoặc biến đổi đại số một cách có chứng minh để tránh overflow.

### Lỗi 10: âm thầm nuốt ArithmeticException

Nếu `Math.*Exact` ném lỗi, việc trả `0` thay thế có thể dẫn đến **cấp phát ít hơn yêu cầu** và lỗi dữ liệu. Chỉ dùng fallback nếu hợp đồng nghiệp vụ đã định nghĩa rõ và người gọi có thể phân biệt lỗi với trường hợp kích thước thực sự bằng 0.

## 14. Bài luyện tập

**Bài 1.** Tính kích thước pixel cho 70.000 × 70.000, mỗi pixel 4 byte. Vì sao `(long)(70_000 * 70_000 * 4)` sai?  
**Đáp án:** biểu thức bên trong cast được tính bằng `int` và đã tràn. Dùng `Math.multiplyExact(Math.multiplyExact(70_000L, 70_000L), 4L)` → 19.600.000.000 byte, hoặc cách tương đương có kiểm tra.

**Bài 2.** Với raw row 13 byte và alignment 8, padding/stride là bao nhiêu?  
**Đáp án:** 13 % 8 = 5, padding = 3, stride = 16.

**Bài 3.** Vì sao `(Long.MAX_VALUE + 4) / 4` không phải cách tính số trang an toàn?  
**Đáp án:** phép cộng `long` tràn trước khi chia. Dùng thương và phần dư.

**Bài 4.** Nếu cần phân bổ 3.000.000.000 phần tử trong một `int[]`, `Math.toIntExact(3_000_000_000L)` xử lý thế nào?  
**Đáp án:** ném `ArithmeticException` vì số phần tử không vừa miền `int`. Ngay cả số phần tử vừa `int` cũng có thể không cấp phát được.

**Bài 5.** Khi cần biểu diễn kích thước 8 × `Long.MAX_VALUE`, kiểu số nào phù hợp để tính giá trị lý thuyết?  
**Đáp án:** `BigInteger`. Nó tính chính xác giá trị 73.786.976.294.838.206.456; không thể chuyển về `long` bằng `longValueExact()`.

**Bài 6.** Viết phương thức tính `totalBytes = rows × alignedRowBytes(columns,bpp,alignment)`, làm gì để không có overflow âm thầm?  
**Đáp án:** kiểm tra rows/columns/bpp không âm và alignment dương; dùng `Math.multiplyExact(columns,bpp)`, chia dư để tính padding, `Math.addExact(raw,padding)` rồi `Math.multiplyExact(rows,stride)`.

## 15. Checklist trước khi đưa vào sản phẩm

1. Tất cả tham số có **đơn vị** và phạm vi hợp lệ rõ ràng chưa?
2. Các phép toán `+`, `-`, `*` trên kích thước/offset có dùng exact arithmetic hoặc cách chứng minh an toàn chưa?
3. Nếu có phép nhân `int`, đã chuyển đúng toán hạng sang `long` **trước** phép nhân chưa?
4. Có chia/làm tròn lên không? Đã tránh `bytes + pageSize - 1` chưa?
5. Căn chỉnh có kiểm tra overflow ở bước cộng padding không?
6. Phân biệt kích thước logic `long/BigInteger` với độ dài mảng `int` và tài nguyên vật lý chưa?
7. Mọi phép chuyển thu hẹp kiểu có dùng `Math.toIntExact` hoặc phương án được kiểm tra tương đương chưa?
8. Khi yêu cầu vượt ngân sách, có báo lỗi minh bạch hoặc chia chunk thay vì cấp phát thiếu không?
9. Test đã có ca 0, âm, boundary, overflow cho số dương và case alignment overflow chưa?
10. Có kiểm tra rằng tính đúng số byte vẫn **không có nghĩa** sẽ luôn cấp phát thành công không?

## 16. Kết luận

- Dùng **`Math.multiplyExact`, `Math.addExact` và `Math.toIntExact`** để phát hiện tràn và mất dữ liệu khi tính kích thước.
- Với số nguyên thông thường, **gán kết quả tràn `int` sang `long` không sửa được phép tính**; phải mở rộng kiểu trước.
- Với kích thước được căn chỉnh, tính padding từ phần dư và cộng bằng `Math.addExact`; với số trang, dùng thương và phần dư thay công thức cộng `pageSize-1`.
- Kiểm tra **đối số âm, đơn vị, ngưỡng tài nguyên** bên cạnh kiểm tra số học.
- Khi kết quả lý thuyết vượt `long`, dùng `BigInteger`; nếu một API yêu cầu `long` hoặc `int`, chuyển bằng phương thức `*ValueExact` hoặc `Math.toIntExact` để phát hiện không biểu diễn được.
- Số đếm **đúng** không đảm bảo bộ nhớ/đĩa **đủ**; cả hai lớp kiểm tra đều cần trong hệ thống an toàn.

Tài liệu tham khảo chính thức Java SE 17:
- [`Math` — multiplyExact, addExact, subtractExact, toIntExact](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html)
- [`BigInteger` — multiply, longValueExact](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigInteger.html)
- [`ArithmeticException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ArithmeticException.html)
- [`IllegalArgumentException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/IllegalArgumentException.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-practice-003">Đọc ghi số theo nhiều cơ số và dạng không dấu</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc ghi số theo nhiều cơ số và dạng không dấu trong Java

## 1. Mục tiêu: cùng giá trị, nhiều cách biểu diễn

Một con số có thể được **đọc từ chuỗi** hoặc **xuất thành chuỗi** theo nhiều cơ số: nhị phân (base 2), bát phân (base 8), thập phân (base 10), thập lục phân (base 16), hoặc các cơ số từ 2 đến 36. Đồng thời, Java có `int` và `long` **có dấu** nhưng không có kiểu nguyên primitive `unsigned int`/`unsigned long` riêng.

Bởi vậy, khi xử lý bit mask, địa chỉ, protocol, checksum, ID từ hệ thống khác hoặc trường nhị phân trong tệp, cần phân biệt ba thứ:

1. **Giá trị toán học**: ví dụ số dương 4.294.967.295.
2. **Mẫu bit lưu trong bộ nhớ**: ví dụ 32 bit toàn `1`.
3. **Cách diễn giải và trình bày**: cùng mẫu 32 bit đó là `-1` theo `int` có dấu, nhưng là `4294967295` theo diễn giải không dấu.

Trong bài này, “đọc” chủ yếu có nghĩa **parse văn bản hoặc lấy các bit từ dữ liệu**; “ghi” có nghĩa **format ra văn bản hoặc chuyển lại thành biểu diễn bit phù hợp**. Cách làm đúng phụ thuộc giao thức: nó quy định cơ số, độ rộng bit, dấu, leading zeros, byte order và cách xử lý dữ liệu không hợp lệ.

## 2. Cơ số (radix) và giá trị chữ số

Trong hệ cơ số `r`, mỗi chữ số ở vị trí thứ `k` đóng góp `digit × r^k`. Ví dụ:

\[
(1010)_2=1\cdot2^3+0\cdot2^2+1\cdot2^1+0=10.
\]

Và:

\[
(\text{FF})_{16}=15\cdot16+15=255.
\]

Bảng cơ bản:

| Giá trị thập phân | Nhị phân (2) | Bát phân (8) | Hex (16) |
| ---: | ---: | ---: | --- |
| 10 | `1010` | `12` | `A` |
| 15 | `1111` | `17` | `F` |
| 16 | `10000` | `20` | `10` |
| 255 | `11111111` | `377` | `FF` |
| 256 | `100000000` | `400` | `100` |

Java cho phép radix **2..36** trong `Integer.parseInt`, `Long.parseLong` và nhiều API chuyển đổi. Chữ số `0`–`9` có giá trị 0–9; các chữ `a`–`z` (hoặc `A`–`Z`) có giá trị 10–35 trong quá trình parse. Ví dụ `z` trong base 36 biểu diễn 35.

Cơ số là **quy tắc đọc chuỗi**, không thay đổi giá trị số nguyên sau khi parse. Sau:

~~~java
int x = Integer.parseInt("FF", 16);
// x == 255
~~~

`x` không “mang cơ số 16” trong kiểu `int`; nó chỉ lưu số 255. Khi cần hiển thị hex, phải format nó thành chuỗi hex.

## 3. Đọc số có dấu: parseInt, parseLong và BigInteger

### 3.1. Parse từ văn bản với radix được chỉ định

~~~java
int binary = Integer.parseInt("1010", 2); // 10
int octal = Integer.parseInt("377", 8);  // 255
int hex = Integer.parseInt("FF", 16);    // 255
int base36 = Integer.parseInt("z", 36);  // 35

long large = Long.parseLong("7FFFFFFFFFFFFFFF", 16);
// 9223372036854775807L == Long.MAX_VALUE
~~~

Có thể truyền số có dấu bằng `+` hoặc `-` ở đầu:

~~~java
int a = Integer.parseInt("-FF", 16); // -255
int b = Integer.parseInt("+1010", 2); // 10
~~~

Tuy nhiên `Integer.parseInt("FFFFFFFF", 16)` **ném `NumberFormatException`**: chuỗi hex dương ấy biểu diễn 4.294.967.295, nằm ngoài phạm vi `int` có dấu. Nó **không** tự coi `FFFFFFFF` là `-1`. Muốn diễn giải mẫu bit theo kiểu unsigned, dùng `parseUnsignedInt` (phần 5).

Điểm cốt lõi: API parse signed đang cố đọc **giá trị toán học có dấu** trong miền `int` hoặc `long`, không phải tự nhận một chuỗi bit bù hai.

### 3.2. Prefix 0x/0b không mặc định được parseInt nhận

~~~java
Integer.parseInt("FF", 16);       // 255
Integer.parseInt("0xFF", 16);     // NumberFormatException
Integer.parseInt("0b1010", 2);   // NumberFormatException
Integer.decode("0xFF");          // 255
Integer.decode("#FF");           // 255
Integer.decode("0377");          // 255: tiền tố 0 thể hiện bát phân
~~~

`parseInt(text, radix)` yêu cầu các chữ số hợp lệ của radix đã cho, ngoại trừ dấu `+`/`-` được hỗ trợ; không tự xử lý các prefix literal `0x`, `0b`. `Integer.decode`/`Long.decode` có quy tắc nhận diện `0x`, `0X`, `#` hoặc tiền tố `0` cho bát phân, nhưng **không được xem là parser tổng quát cho literal nhị phân `0b`**.

Với định dạng text theo một protocol, cách dễ kiểm soát nhất là xác thực prefix riêng (nếu có), xác định radix, rồi parse phần chữ số bằng đúng API. Đặc biệt cẩn trọng với các số có leading zero: `Integer.parseInt("010",10) == 10`, còn `Integer.decode("010") == 8`.

### 3.3. Cú pháp literal trong source code khác văn bản runtime

Khi viết Java trực tiếp:

~~~java
int binaryLiteral = 0b1010; // 10
int hexLiteral = 0xFF;      // 255
int octalLiteral = 0377;   // 255: cú pháp octal cũ
int grouped = 1_000_000;   // 1000000
~~~

Các prefix và dấu gạch dưới được **Java compiler xử lý trong mã nguồn**. Điều đó không có nghĩa `Integer.parseInt("0b1010",2)` hoặc `Integer.parseInt("1_000_000",10)` sẽ nhận. Các chuỗi nhập có dấu `_` phải được validate theo schema trước khi loại dấu phân nhóm; không xóa bừa ký tự bất hợp lệ của dữ liệu bên ngoài.

### 3.4. BigInteger cho giá trị vượt miền int/long

~~~java
import java.math.BigInteger;

BigInteger huge = new BigInteger("FFFFFFFFFFFFFFFF", 16);
// 18446744073709551615, lớn hơn Long.MAX_VALUE

BigInteger base36 = new BigInteger("Z", 36);
// 35

String backToHex = huge.toString(16);
// "ffffffffffffffff"
~~~

`BigInteger(String, radix)` hữu ích khi **giá trị toán học** vượt 64 bit hoặc khi phải kiểm soát thao tác chính xác trên số nguyên lớn. `BigInteger` hỗ trợ số âm, nên `new BigInteger("-FF",16)` cho -255. Nó không tự gán quy ước “unsigned 64-bit” cho mọi giá trị: ứng dụng vẫn phải kiểm tra phạm vi hợp đồng khi cần.

## 4. Ghi số có dấu theo nhiều cơ số

Dùng `Integer.toString(value, radix)`, `Long.toString(value, radix)`:

~~~java
int n = 255;

String binary = Integer.toString(n, 2); // "11111111"
String octal = Integer.toString(n, 8);  // "377"
String decimal = Integer.toString(n, 10); // "255"
String hex = Integer.toString(n, 16);   // "ff"
String base36 = Integer.toString(35, 36); // "z"
~~~

Các hàm chuyên biệt cũng có:

~~~java
Integer.toBinaryString(255); // "11111111"
Integer.toOctalString(255);  // "377"
Integer.toHexString(255);    // "ff"

Long.toBinaryString(255L);  // "11111111"
Long.toHexString(255L);     // "ff"
~~~

**Phân biệt số âm:** `Integer.toString(-1,16)` trả `"-1"` (một số âm theo base 16), còn `Integer.toHexString(-1)` trả `"ffffffff"`, tức **mẫu bit 32 bit** diễn giải không dấu. Đây là hai mục tiêu format khác nhau.

Nếu giao diện cần ký tự `A`–`F` viết hoa, có thể dùng `toUpperCase(Locale.ROOT)` trên chuỗi hex hoặc formatter thích hợp:

~~~java
String upperHex = Integer.toHexString(255)
        .toUpperCase(java.util.Locale.ROOT); // "FF"
~~~

Lưu ý độ rộng: `Integer.toHexString(5)` cho `"5"`, không tự thêm `00000005`. Nếu cần 8 chữ số cho 32 bit, định dạng riêng hoặc padding với kiểm tra độ dài, ví dụ:

~~~java
String hex32 = String.format(java.util.Locale.ROOT,
        "%08X", 5); // "00000005"
~~~

`%X` trong `Formatter` là **thập lục phân**, không phải nhị phân. Đừng dùng `%b` để in binary: trong Java `Formatter`, `%b` là conversion boolean (giá trị true/false theo quy tắc), không phải base 2.

## 5. Đọc và ghi int không dấu (unsigned 32-bit)

Java không có kiểu primitive `uint32` riêng. Đối với giá trị unsigned 32-bit `0..4294967295`, bạn có thể dùng **`int` như container 32 bit**, rồi dùng các API unsigned khi cần diễn giải.

### 5.1. Đọc mẫu 32 bit bằng parseUnsignedInt

~~~java
int bits = Integer.parseUnsignedInt("FFFFFFFF", 16);
// bits == -1 khi nhìn như int signed.
// Nhưng 32 bit toàn 1 tương ứng unsigned 4294967295.
~~~

`Integer.parseUnsignedInt(text, radix)` nhận giá trị nguyên **không âm** trong `[0, 2^32 - 1]` của radix, rồi trả `int` chứa đúng 32 bit. Vì `int` là có dấu nên kết quả có thể âm.

- `Integer.parseUnsignedInt("FFFFFFFF",16)` cho `-1`.
- `Integer.parseUnsignedInt("80000000",16)` cho `Integer.MIN_VALUE`.
- `Integer.parseUnsignedInt("100000000",16)` ném `NumberFormatException` vì lớn hơn 32 bit.
- Chuỗi với dấu trừ như `"-1"` không phải giá trị unsigned hợp lệ; đừng dùng nó để biểu diễn `0xFFFFFFFF`.

### 5.2. Ghi dưới dạng unsigned

~~~java
int bits = -1;

String signed = Integer.toString(bits); // "-1"
String unsignedDec = Integer.toUnsignedString(bits); // "4294967295"
String unsignedHex = Integer.toUnsignedString(bits, 16); // "ffffffff"
String unsignedBin = Integer.toUnsignedString(bits, 2);
// 32 ký tự '1'
~~~

`Integer.toHexString(bits)` cũng biểu diễn mẫu bit theo hex không dấu, nên với `-1` cho `"ffffffff"`. Nhưng `Integer.toString(bits,16)` khác: `"-1"`.

Muốn tính toán số học không dấu 32 bit trong miền số dương có thể biểu diễn được, chuyển sang `long`:

~~~java
int rawBits = -1;
long unsignedValue = Integer.toUnsignedLong(rawBits);
// 4294967295L
~~~

Vì `long` có 64 bit, toàn bộ miền unsigned 32 bit đều vừa trong miền **signed long**. Đây là phương án thuận tiện khi cần so, cộng hoặc xuất một trường `uint32`, miễn là tiếp tục quản lý overflow có thể xảy ra khi các giá trị cộng nhân lớn hơn `Long.MAX_VALUE`.

### 5.3. So sánh và chia không dấu

Cùng bit `-1` theo signed là số nhỏ hơn `1`, nhưng theo unsigned là `4294967295` (lớn hơn 1):

~~~java
int bits = -1;

int signedCompare = Integer.compare(bits, 1); // < 0
int unsignedCompare = Integer.compareUnsigned(bits, 1); // > 0

int quotient = Integer.divideUnsigned(bits, 2); // 2147483647
int remainder = Integer.remainderUnsigned(bits, 2); // 1
~~~

`compareUnsigned` trả số âm/0/dương theo quy tắc so sánh, **không hứa luôn trả đúng -1 hoặc 1 trong mọi triển khai**. Kiểm tra quan hệ `< 0`, `== 0`, `> 0` thay vì phụ thuộc một trị số cụ thể.

Các phép cộng/trừ/nhân primitive vẫn xử lý các bit theo modulo \(2^{32}\). Nếu hợp đồng muốn **báo lỗi khi phép toán unsigned vượt 2^32-1**, hãy mở rộng lên `long` trước khi tính và so phạm vi, hoặc dùng `BigInteger` khi phép tính có thể lớn hơn `long`. Không coi phép `a + b` thông thường tự phát hiện tràn unsigned.

## 6. Đọc và ghi long không dấu (unsigned 64-bit)

Miền unsigned 64 bit:

\[
0 \le u \le 2^{64}-1=18446744073709551615.
\]

Miền signed long lại chỉ đến `9223372036854775807`. Vì vậy **không thể biểu diễn toàn bộ unsigned 64 bit thành một giá trị `long` dương**.

### 6.1. Giữ mẫu bit bằng long

~~~java
long bits = Long.parseUnsignedLong("18446744073709551615");
// bits == -1L khi xem là long signed

String unsignedDec = Long.toUnsignedString(bits);
// "18446744073709551615"

String unsignedHex = Long.toUnsignedString(bits, 16);
// "ffffffffffffffff"
~~~

`Long.parseUnsignedLong` chấp nhận giá trị không âm trong phạm vi 64 bit và trả đúng mẫu bit. Chuỗi lớn hơn, như `"18446744073709551616"`, ném `NumberFormatException`.

`Long.parseLong("18446744073709551615")` **không** thành công: đó là giá trị dương vượt `Long.MAX_VALUE`. Nếu muốn lưu nguyên văn giá trị dương thay vì chỉ bit pattern, chọn `BigInteger`.

### 6.2. Diễn giải giá trị unsigned 64 bit dưới dạng BigInteger dương

~~~java
long bits = -1L;

java.math.BigInteger unsigned =
        new java.math.BigInteger(Long.toUnsignedString(bits));
// 18446744073709551615

String hex = unsigned.toString(16);
// "ffffffffffffffff"
~~~

Đây là cách dễ hiểu: đổi bit pattern thành chuỗi decimal unsigned, rồi parse thành `BigInteger` dương. Có các cách hiệu quả hơn dựa trên hai nửa 32 bit hoặc byte array với `new BigInteger(1, bytes)`, nhưng phải cẩn trọng **độ rộng, thứ tự byte và dấu**.

Ví dụ kiểm tra phạm vi khi cần chuyển ngược về unsigned long:

~~~java
BigInteger maxUnsignedLong = BigInteger.ONE.shiftLeft(64)
        .subtract(BigInteger.ONE);

BigInteger candidate = new BigInteger("18446744073709551615");

if (candidate.signum() < 0
        || candidate.compareTo(maxUnsignedLong) > 0) {
    throw new IllegalArgumentException("Out of uint64 range");
}

// longValue() trả 64 bit thấp (có thể âm khi nhìn theo long signed).
long storageBits = candidate.longValue();
String roundTrip = Long.toUnsignedString(storageBits);
~~~

Ở đây `longValue()` chỉ an toàn **theo hợp đồng lưu bit pattern** và **sau khi đã kiểm tra** candidate nằm trong `[0,2^64-1]`. Nó không diễn giải unsigned lớn thành long dương. `longValueExact()` lại sẽ ném lỗi với các giá trị unsigned lớn hơn `Long.MAX_VALUE`, dù chúng hợp lệ trong uint64.

### 6.3. Các phép so và chia unsigned long

~~~java
long allOnes = -1L;

boolean greaterThanOne =
        Long.compareUnsigned(allOnes, 1L) > 0; // true

long q = Long.divideUnsigned(allOnes, 2L);
// Khi xem signed long: 9223372036854775807

long r = Long.remainderUnsigned(allOnes, 2L); // 1
~~~

Các toán tử so sánh `<`, `>` trên primitive `long` dùng **signed semantics**, không phù hợp để so các giá trị unsigned có bit dấu bật. Nếu dữ liệu đến từ protocol định nghĩa uint64, ưu tiên `Long.compareUnsigned`, `divideUnsigned`, `remainderUnsigned` và `toUnsignedString` để xử lý đúng.

## 7. Byte và short không dấu: đọc tệp nhị phân

Một byte trong Java (`byte`) có dấu, miền `-128..127`, nhưng một trường `uint8` của tệp nhị phân có miền `0..255`.

~~~java
byte raw = (byte) 0xFF; // raw == -1 khi xem là byte signed

int unsignedByte = Byte.toUnsignedInt(raw); // 255
int masked = raw & 0xFF; // 255
~~~

Phép `& 0xFF` loại bit dấu đã được sign-extend khi chuyển `byte` sang `int`. `Byte.toUnsignedInt` thể hiện mục tiêu rõ ràng hơn. Với `short` unsigned 16 bit có `Short.toUnsignedInt(rawShort)`, miền `0..65535`.

Với `DataInputStream`:

~~~java
// in là DataInputStream hợp lệ đã mở:
int u8 = in.readUnsignedByte();  // 0..255
int u16 = in.readUnsignedShort(); // 0..65535, byte order big-endian
~~~

Đây là snippet nằm trong một phương thức có biến `DataInputStream in`; có thể phát sinh `IOException`, bao gồm EOF. Với `ByteBuffer`, phải quy định `ByteOrder.BIG_ENDIAN` hay `LITTLE_ENDIAN` theo format, rồi `getInt()`/`getLong()` lấy các bit và dùng API unsigned để diễn giải. **Endian** quyết định cách ghép byte thành số nhiều byte, còn **signed/unsigned** quyết định cách đọc giá trị sau khi đã ghép: đây là hai vấn đề khác nhau.

### 7.1. Đầu ra hex cố định độ rộng

Trong một protocol, mỗi byte thường hiển thị bằng **hai chữ số hex**, mỗi số 32 bit là **tám chữ số hex**, mỗi số 64 bit là **16 chữ số hex**. Ví dụ:

~~~java
byte b = (byte) 0x0A;

String byteHex = String.format(java.util.Locale.ROOT,
        "%02X", Byte.toUnsignedInt(b)); // "0A"

String intHex = String.format(java.util.Locale.ROOT,
        "%08X", 5); // "00000005"
~~~

Padding có thể quan trọng để khôi phục chính xác độ rộng của tệp/giao thức. `Integer.toHexString(5)` trả `"5"`, làm mất thông tin “8 chữ số” nếu bên nhận đang mong đợi trường 32 bit fixed-width. Ngược lại, padding không làm tăng độ chính xác toán học; nó chỉ là quy ước biểu diễn.

## 8. Chương trình Java 17+ hoàn chỉnh

Chương trình sau thể hiện cả signed parse/format lẫn unsigned parse/format, nâng unsigned int lên long, so/chia không dấu, BigInteger cho uint64 và các lỗi đầu vào. Tạo file **`RadixUnsignedDemo.java`**:

~~~java
import java.math.BigInteger;
import java.util.Locale;

public class RadixUnsignedDemo {
    public static void main(String[] args) {
        int value = Integer.parseInt("FF", 16);

        System.out.println("hex FF -> dec: " + value);
        System.out.println("255 -> binary: " + Integer.toString(value, 2));
        System.out.println("255 -> octal: " + Integer.toString(value, 8));
        System.out.println("35 -> base36: " + Integer.toString(35, 36));
        System.out.println("decode 0xFF: " + Integer.decode("0xFF"));

        int bits = Integer.parseUnsignedInt("FFFFFFFF", 16);
        System.out.println("int signed storage: " + bits);
        System.out.println("int unsigned dec: "
                + Integer.toUnsignedString(bits));
        System.out.println("int unsigned hex: "
                + Integer.toUnsignedString(bits, 16));
        System.out.println("int unsigned as long: "
                + Integer.toUnsignedLong(bits));
        System.out.println("int compareUnsigned to 1: "
                + Integer.compareUnsigned(bits, 1));
        System.out.println("int divideUnsigned by 2: "
                + Integer.divideUnsigned(bits, 2));
        System.out.println("int remainderUnsigned by 2: "
                + Integer.remainderUnsigned(bits, 2));

        long longBits = Long.parseUnsignedLong(
                "18446744073709551615");
        System.out.println("long signed storage: " + longBits);
        System.out.println("long unsigned dec: "
                + Long.toUnsignedString(longBits));
        System.out.println("long unsigned hex: "
                + Long.toUnsignedString(longBits, 16));
        System.out.println("unsigned long BigInteger: "
                + new BigInteger(Long.toUnsignedString(longBits)));

        System.out.println("hex padded: "
                + String.format(Locale.ROOT, "%08X", bits));
        System.out.println("byte unsigned: "
                + Byte.toUnsignedInt((byte) 0xFF));
        System.out.println("binary padded: "
                + String.format(Locale.ROOT, "%8s",
                        Integer.toBinaryString(5)).replace(' ', '0'));

        try {
            Integer.parseInt("FFFFFFFF", 16);
        } catch (NumberFormatException ex) {
            System.out.println("signed hex overflow: NumberFormatException");
        }

        try {
            Integer.parseUnsignedInt("100000000", 16);
        } catch (NumberFormatException ex) {
            System.out.println("unsigned int overflow: NumberFormatException");
        }

        try {
            Long.parseUnsignedLong("18446744073709551616");
        } catch (NumberFormatException ex) {
            System.out.println("unsigned long overflow: NumberFormatException");
        }
    }
}
~~~

Chạy:

~~~bash
javac RadixUnsignedDemo.java
java RadixUnsignedDemo
~~~

Kết quả:

~~~text
hex FF -> dec: 255
255 -> binary: 11111111
255 -> octal: 377
35 -> base36: z
decode 0xFF: 255
int signed storage: -1
int unsigned dec: 4294967295
int unsigned hex: ffffffff
int unsigned as long: 4294967295
int compareUnsigned to 1: 1
int divideUnsigned by 2: 2147483647
int remainderUnsigned by 2: 1
long signed storage: -1
long unsigned dec: 18446744073709551615
long unsigned hex: ffffffffffffffff
unsigned long BigInteger: 18446744073709551615
hex padded: FFFFFFFF
byte unsigned: 255
binary padded: 00000101
signed hex overflow: NumberFormatException
unsigned int overflow: NumberFormatException
unsigned long overflow: NumberFormatException
~~~

Bảng giải thích nhanh:

| Dòng chương trình | Điều được kiểm chứng |
| --- | --- |
| `hex FF -> dec: 255` | Parse hex chuyển sang giá trị số, không “giữ radix” trong biến |
| `int signed storage: -1` | `FFFFFFFF` lưu thành mẫu bit 32 bit toàn 1 |
| `int unsigned dec: 4294967295` | Cùng bit được format theo miền unsigned 32 bit |
| `int unsigned as long: 4294967295` | `long` đủ rộng giữ uint32 dưới dạng số dương |
| `long signed storage: -1` | uint64 lớn nhất không vừa signed long dương |
| `unsigned long BigInteger: 18446744073709551615` | `BigInteger` lưu giá trị dương đầy đủ |
| Các dòng `NumberFormatException` | Parser chặn giá trị vượt miền đã định, không tự wrap khi đọc text |
| `hex padded`, `binary padded` | Cơ số và độ rộng trình bày là hai chính sách riêng |

Đầu ra là deterministic vì bài không sinh ngẫu nhiên, và đoạn ví dụ dùng các API chuẩn của Java 17. Đừng thay `Long.parseUnsignedLong` bằng `Long.parseLong` ở ví dụ max uint64: signed long sẽ ném lỗi vượt miền.

## 9. Round-trip: đọc–ghi không đổi giá trị hoặc mẫu bit

### 9.1. Round-trip signed

Đối với một `int x` và cơ số hợp lệ từ 2 đến 36:

~~~java
int x = -255;
int radix = 16;

String text = Integer.toString(x, radix); // "-ff"
int parsed = Integer.parseInt(text, radix); // -255
~~~

Chuỗi signed phản ánh **giá trị toán học** của `x`.

### 9.2. Round-trip unsigned theo bit pattern

~~~java
int originalBits = -1;
int radix = 16;

String wireText = Integer.toUnsignedString(originalBits, radix);
// "ffffffff"

int restoredBits = Integer.parseUnsignedInt(wireText, radix);
// -1, cùng bit pattern với originalBits

boolean sameBits = restoredBits == originalBits; // true
~~~

Với `long`, thay bằng `Long.toUnsignedString` và `Long.parseUnsignedLong`. Đây là round-trip của **bit pattern**, không phải bảo đảm giá trị signed hiển thị dương.

Có thể viết test thuộc tính:

~~~java
for (int radix = Character.MIN_RADIX;
        radix <= Character.MAX_RADIX; radix++) {
    int value = -123456789;
    String text = Integer.toUnsignedString(value, radix);
    int reconstructed = Integer.parseUnsignedInt(text, radix);
    if (reconstructed != value) {
        throw new AssertionError("Mismatch at radix " + radix);
    }
}
~~~

Đoạn code được đặt trong một phương thức hợp lệ. Để kiểm thử rộng hơn, chọn các biên `0`, `1`, `-1`, `Integer.MIN_VALUE`, `Integer.MAX_VALUE` và nhiều giá trị ngẫu nhiên cố định seed. Cần nhớ `MIN_RADIX=2` và `MAX_RADIX=36`.

### 9.3. Leading zeros, chữ hoa và tính canonical

Đọc hai chuỗi `"000A"` và `"a"` với radix 16 đều được giá trị 10, nhưng format theo `Integer.toString(10,16)` trở lại thường là `"a"`, **không phục hồi chính xác độ rộng/chữ hoa ban đầu**.

Nếu yêu cầu truyền dữ liệu dưới dạng đúng 4 ký tự hex viết hoa, phải lưu hoặc quy định riêng **fixed width = 4**, **case = uppercase**, **prefix = không có**, và chính sách kiểm tra đầu vào. Parse/format số học không thể tự giữ các đặc điểm về bố cục văn bản đã bị bỏ qua.

Nếu định dạng sử dụng `0x`, `0b` hoặc các dấu phân nhóm, parser phải tách/validate chúng một cách rõ ràng. Đừng trộn “chuỗi giá trị” với “định dạng hiển thị” mà không có hợp đồng.

## 10. Kiểm thử và chẩn đoán dữ liệu đầu vào

### 10.1. Những trường hợp phải xác thực

Các API `parseInt`, `parseLong`, `parseUnsignedInt`, `parseUnsignedLong` có thể ném `NumberFormatException` khi:

- Chuỗi có chữ số không hợp lệ với radix: ví dụ `"2"` trong base 2, `"G"` trong base 16.
- Giá trị vượt miền có dấu/không dấu tương ứng.
- Không có chữ số cần parse, chẳng hạn chuỗi rỗng.
- Có prefix mà API không nhận, ví dụ `"0xFF"` với `parseInt(...,16)`.
- Dấu trừ xuất hiện trong dữ liệu unsigned không âm theo contract.
- Radix ở ngoài khoảng hỗ trợ (với các parser có tham số radix).

`null` cũng không phải một chuỗi số hợp lệ; tùy overload/JDK, API có thể báo `NumberFormatException` hoặc lỗi liên quan. Ở ranh giới ứng dụng, nên kiểm tra null rõ ràng trước khi parse để có thông báo xác thực dễ hiểu thay vì phụ thuộc message nội bộ của thư viện.

Ví dụ:

~~~java
static int parseHexUnsigned32(String input) {
    if (input == null || !input.matches("[0-9A-Fa-f]{1,8}")) {
        throw new IllegalArgumentException("Expected 1..8 hex digits");
    }
    return Integer.parseUnsignedInt(input, 16);
}
~~~

Đây là một **phương thức** cần đặt trong lớp Java. Nó cố ý chỉ nhận ASCII hex không prefix, không khoảng trắng, không dấu âm và tối đa 8 chữ số. Tùy định dạng của bạn, có thể cần yêu cầu **đúng 8 chữ số** thay vì 1..8.

Lưu ý một số API parse của Java nhận ký tự chữ số Unicode theo `Character.digit`; nếu protocol yêu cầu ASCII nghiêm ngặt, tự validate alphabet trước. Không được mặc định rằng “parse thành công” đã đồng nghĩa đầu vào có đúng tập ký tự mà giao thức quy định.

### 10.2. Format phải phản ánh kiểu và độ rộng

Với số `-1`:

| Phương thức | Chuỗi |
| --- | --- |
| `Integer.toString(-1, 16)` | `-1` |
| `Integer.toHexString(-1)` | `ffffffff` |
| `Integer.toUnsignedString(-1)` | `4294967295` |
| `Long.toString(-1L, 16)` | `-1` |
| `Long.toHexString(-1L)` | `ffffffffffffffff` |
| `Long.toUnsignedString(-1L)` | `18446744073709551615` |

Nếu gửi một trường 32-bit dưới dạng 64-bit hex, thêm các số `f` có thể làm người nhận tưởng giá trị lớn hơn. Đảm bảo **độ rộng 32/64**, **signedness** và **radix** khớp với schema. Đừng lấy `toHexString` của một `long` đã sign-extend từ `int` rồi kỳ vọng luôn nhận đúng 8 ký tự.

### 10.3. Endian và unsigned là hai tầng khác nhau

Ví dụ hai byte `01 00`:

- Big-endian 16 bit: `0x0100 = 256`.
- Little-endian 16 bit: `0x0001 = 1`.

Cả hai đều có thể được diễn giải dưới dạng unsigned 16-bit sau khi ghép. Nếu đọc từ `ByteBuffer`, chọn `order(ByteOrder.LITTLE_ENDIAN)` hoặc `order(ByteOrder.BIG_ENDIAN)` theo giao thức, rồi sử dụng `Short.toUnsignedInt` hoặc các hàm tương ứng. Không sửa lỗi byte order bằng `toUnsignedString`; nó chỉ thay cách **diễn giải dấu**, không tráo thứ tự byte.

## 11. Các lỗi phổ biến

1. **Tưởng Java có primitive uint32/uint64 riêng.** Không có; dùng `int`/`long` làm bit container với API unsigned, hoặc dùng `long`/`BigInteger` cho giá trị dương cần giữ.
2. **Đọc `FFFFFFFF` bằng `Integer.parseInt(...,16)`.** Nó vượt int signed; muốn 32 bit toàn 1 dùng `parseUnsignedInt`.
3. **Dùng `Long.parseLong` cho max uint64.** `18446744073709551615` vượt signed long; dùng `parseUnsignedLong` hoặc `BigInteger`.
4. **Cho rằng `parseInt("0xFF",16)` nhận prefix.** Không; `decode` có quy tắc riêng hoặc tách prefix rồi parse.
5. **Nhầm cú pháp literal với parser runtime.** `0b1010`, `1_000` có thể hợp lệ trong Java source, không tự hợp lệ trong chuỗi parse.
6. **Dùng `Integer.toString(-1,16)` để xuất mẫu bit.** Nó cho `-1`; dùng `toHexString`/`toUnsignedString` để hiển thị bit không dấu.
7. **So hai uint32 lưu trong int bằng `>`/ `<`.** Khi bit dấu bật, quan hệ signed khác unsigned; dùng `compareUnsigned`.
8. **Chia uint64 bằng toán tử `/` trên long.** Khi giá trị bit pattern có dấu âm, kết quả signed không phù hợp; dùng `Long.divideUnsigned`.
9. **Chuyển uint64 sang BigInteger bằng `BigInteger.valueOf(bits)` khi bits âm.** Điều đó tạo BigInteger âm; muốn unsigned dương dùng `new BigInteger(Long.toUnsignedString(bits))` hoặc xây từ bytes có signum dương.
10. **Dùng `intValueExact`/ `longValueExact` để lấy bit pattern của uint64 lớn.** Chúng từ chối giá trị vượt miền signed, dù có thể hợp lệ theo unsigned. Kiểm tra miền unsigned trước rồi chỉ lấy 64 bit thấp theo hợp đồng.
11. **Quên leading zeros.** Parser không tự giữ số chữ số ban đầu; format fixed-width nếu giao thức cần.
12. **Nhầm chữ hoa/thường của hex với khác giá trị.** Parser radix 16 nhận cả A–F/a–f, nhưng output canonical có thể chỉ chữ thường.
13. **Tưởng xử lý unsigned tự xử lý byte order.** Không; endian được quyết định trong bước ghép/đọc byte.
14. **Mặc định parse thành công nghĩa là chuẩn hóa input đã đúng.** Có thể cần kiểm ASCII, prefix, dấu, khoảng trắng, độ rộng và format cố định.
15. **Dùng phép cộng primitive như thể tự báo overflow unsigned.** Nó có thể wrap; nếu không được phép wrap thì so phạm vi bằng kiểu rộng hơn/`BigInteger`.
16. **Dùng `%b` của `String.format` để xuất nhị phân.** `%b` thuộc boolean formatting; dùng `Integer.toBinaryString`.
17. **Quên ranh giới giữa mã hóa bit và giá trị toán học.** Cùng `-1` có thể có ý nghĩa khác nhau theo độ rộng và signedness; hãy nêu chúng trong API.

## 12. Câu hỏi luyện tập có đáp án

**Câu 1.** Giá trị của `Integer.parseInt("101101",2)` là bao nhiêu?

**Đáp án:** 45, vì `32+8+4+1=45`.

**Câu 2.** `Integer.parseInt("FF",16)` và `Integer.parseUnsignedInt("FF",16)` có khác nhau không?

**Đáp án:** Không, cùng trả int 255. Khác biệt xuất hiện với giá trị có bit dấu bật hoặc vượt phạm vi int signed; ví dụ `FFFFFFFF`.

**Câu 3.** Tại sao `Integer.parseUnsignedInt("FFFFFFFF",16)` trả `-1`?

**Đáp án:** Phương thức giữ mẫu 32 bit toàn `1` trong một `int` có dấu; cùng bit đó diễn giải signed là -1, unsigned là 4294967295.

**Câu 4.** Muốn lấy giá trị uint32 dương từ `int raw = -1;` thì dùng gì?

**Đáp án:** `long u = Integer.toUnsignedLong(raw);` cho `4294967295L`.

**Câu 5.** Muốn giữ nguyên giá trị dương của unsigned long lớn nhất dưới dạng số học, dùng kiểu nào?

**Đáp án:** `BigInteger`, ví dụ `new BigInteger("18446744073709551615")`. `Long.parseUnsignedLong` chỉ giữ mẫu 64 bit trong `long` (signed view là -1).

**Câu 6.** Vì sao `Integer.toString(-1,16)` khác `Integer.toHexString(-1)`?

**Đáp án:** Hàm đầu biểu diễn **số signed -1** là `"-1"`; hàm thứ hai biểu diễn **mẫu 32-bit không dấu** là `"ffffffff"`.

**Câu 7.** Có nên dùng `Integer.decode("010")` để đọc chuỗi decimal `010` từ người dùng?

**Đáp án:** Không nếu contract yêu cầu decimal; `decode("010")` hiểu octal và cho 8. Dùng `parseInt("010",10)` để có 10.

**Câu 8.** Một protocol cần đúng bốn chữ số hex uppercase cho một trường 16 bit có giá trị 10. Đầu ra chuẩn là gì?

**Đáp án:** `000A`. `Integer.toHexString(10)` chỉ cho `"a"`; dùng định dạng có padding và uppercase.

**Câu 9.** `Long.compareUnsigned(-1L,1L) > 0` đúng hay sai?

**Đáp án:** Đúng, vì `-1L` có 64 bit toàn `1`, được hiểu là `2^64-1` trong so sánh unsigned.

**Câu 10.** Trong tệp, byte `0xFF` đọc thành `byte -1`. Làm thế nào để tính số lượng 255?

**Đáp án:** `Byte.toUnsignedInt(raw)` hoặc `raw & 0xFF` cho int 255.

## 13. Tài liệu chính thức

- [`Integer` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html): `parseInt`, `parseUnsignedInt`, `toUnsignedString`, `toUnsignedLong`, `compareUnsigned`, `divideUnsigned` và `decode`.
- [`Long` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Long.html): `parseUnsignedLong`, `toUnsignedString`, `compareUnsigned`, `divideUnsigned`.
- [`BigInteger` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigInteger.html): parse/format đa cơ số và tính toán số nguyên lớn.
- [`Byte` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Byte.html): `toUnsignedInt` và diễn giải byte không dấu.
- [`DataInputStream` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataInputStream.html): `readUnsignedByte`, `readUnsignedShort`.

**Ghi nhớ:** Chọn rõ **radix để parse/format**, **độ rộng bit**, **signed hay unsigned** và **quy tắc output**. Với unsigned 32-bit, dùng `int` làm mẫu bit và `Integer.toUnsignedLong` khi cần số dương; với unsigned 64-bit, dùng `long` giữ bit và `BigInteger` khi cần giá trị toán học dương lớn hơn `Long.MAX_VALUE`. Chú ý prefix, leading zeros, endian và lỗi vượt phạm vi khi đọc từ bên ngoài.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-practice-004">Khảo sát sai số của 0.1, tổng lặp và thứ tự cộng</a>

<details>
<summary>Xem chi tiết</summary>

# Khảo sát sai số của 0.1, tổng lặp và thứ tự cộng trong Java

## 1. Mục tiêu thực hành

Thử trực tiếp trong Java:

~~~java
double a = 0.1;
double b = 0.2;

System.out.println(a + b);          // 0.30000000000000004
System.out.println(a + b == 0.3);   // false
~~~

Về mặt toán học, `0.1 + 0.2 = 0.3`. Nhưng với kiểu `double`, phép toán được thực hiện trên những **giá trị nhị phân gần đúng** với các số thập phân mà lập trình viên đã viết. Sai số biểu diễn và sai số làm tròn khi tính toán làm kết quả khác giá trị `double` biểu diễn literal `0.3`.

Bài thực hành này khảo sát bốn vấn đề:

1. Vì sao `0.1` không được biểu diễn chính xác bằng `double`.
2. Vì sao cộng `0.1` lặp lại nhiều lần có thể khác phép nhân `n * 0.1`.
3. Vì sao **thứ tự cộng** có thể thay đổi kết quả của số dấu phẩy động.
4. Cách đo, giải thích và xử lý sai số: `Math.ulp`, sai số tuyệt đối/tương đối, thuật toán cộng bù và `BigDecimal`.

Cần phân biệt **giá trị toán học kỳ vọng**, **giá trị thực tế được lưu trong kiểu số** và **chuỗi ký tự được in ra**. Việc `System.out.println(0.1)` in `0.1` không có nghĩa mọi chữ số thập phân của giá trị lưu trong `double` đều chính xác bằng một phần mười.

## 2. Nguồn gốc: 0.1 có khai triển nhị phân vô hạn

Số thập phân `0.1 = 1/10` không biểu diễn bằng một phân số có mẫu số là lũy thừa của 2 trong một số bit hữu hạn. Tương tự cách `1/3 = 0.3333...` có khai triển vô hạn ở hệ thập phân, `1/10` có khai triển vô hạn ở hệ nhị phân:

~~~text
0.1 (hệ thập phân) = 0.000110011001100110011... (hệ nhị phân)
                                  ^ mẫu 0011 lặp
~~~

`double` Java dùng định dạng nhị phân IEEE 754 binary64: 64 bit gồm bit dấu, trường số mũ và 52 bit phần fraction, với **53 bit chữ số có nghĩa** cho các giá trị chuẩn hóa. Nó chỉ lưu được hữu hạn bit của khai triển nhị phân, nên phải làm tròn sang một giá trị gần nhất.

Kiểm tra giá trị thực tế của `double` đại diện cho `0.1`:

~~~java
double x = 0.1;
System.out.println(Double.toHexString(x));
// 0x1.999999999999ap-4
System.out.println(new java.math.BigDecimal(x));
// 0.1000000000000000055511151231257827021181583404541015625
~~~

Chuỗi hex `0x1.999999999999ap-4` biểu diễn chính xác bit-value của `double`. Constructor `new BigDecimal(double)` cũng cho phép nhìn thấy **giá trị decimal chính xác của double đã lưu**; đây là trường hợp ta cố ý sử dụng constructor này để khảo sát sai số.

Giá trị lưu lớn hơn `0.1` đúng khoảng `5.55 × 10^-18`. Đây là **sai số biểu diễn ban đầu**; các phép cộng tiếp theo có thể phát sinh thêm sai số làm tròn. Sai số trong phép tính phức tạp không nhất thiết tăng đều theo số lần gọi và có thể có dấu thay đổi.

### Tại sao System.out.println(0.1) chỉ in 0.1?

`Double.toString` chọn biểu diễn thập phân ngắn đủ để phân biệt/khôi phục đúng giá trị `double` tương ứng khi parse, không phải in toàn bộ khai triển decimal chính xác của giá trị nhị phân. Vì vậy:

~~~java
System.out.println(0.1);                  // 0.1
System.out.println(new BigDecimal(0.1));  // 0.100000000000000005551...
~~~

Hai dòng đang **biểu diễn cùng một giá trị double theo hai cách khác nhau**. Không được kết luận dòng đầu chứng minh số lưu là đúng 1/10.

## 3. 0.1 + 0.2 khác 0.3: thực sự đã xảy ra điều gì?

Với `double`, các literal `0.1`, `0.2`, `0.3` được làm tròn riêng từ decimal sang binary64. Phép cộng `0.1 + 0.2` trên hai giá trị gần đúng tạo kết quả rồi làm tròn sang giá trị double có thể biểu diễn:

~~~java
double actual = 0.1 + 0.2;
double expected = 0.3;

System.out.println(actual);            // 0.30000000000000004
System.out.println(expected);          // 0.3
System.out.println(actual == expected); // false
~~~

Hai giá trị `actual` và `expected` là **hai điểm nhị phân có thể biểu diễn khác nhau** gần số thập phân 0.3. Kiểm tra `==` thực hiện so sánh giá trị `double` hiện có, không thực hiện so sánh phép tính thập phân lý tưởng.

Điều này là hành vi bình thường của số dấu phẩy động, không phải bug riêng của `println` hoặc Java. Thư viện và ngôn ngữ tuân theo các quy tắc toán học trên số hữu hạn bit. Số rất gần đúng có thể là đủ cho đo lường, đồ họa hoặc tính gần đúng, nhưng không tự đáp ứng hợp đồng về số decimal chính xác.

## 4. Cộng lặp: 10 lần và 100 lần có thể khác phép nhân

So sánh ba cách tính về mặt toán học đều có giá trị là 10:

~~~java
double sum = 0.0;
for (int i = 0; i < 100; i++) {
    sum += 0.1;
}

double multiplication = 100 * 0.1;
~~~

Trên Java 17:

| Cách thực hiện | Giá trị `double` được in |
| --- | --- |
| Cộng `0.1` 10 lần từ `0.0` | `0.9999999999999999` |
| Cộng `0.1` 100 lần từ `0.0` | `9.99999999999998` |
| Nhân `100 * 0.1` | `10.0` |

Tại sao khác nhau dù cùng công thức toán học?

- Với phép cộng lặp, mỗi lượt `sum += 0.1` thực hiện **một phép cộng dấu phẩy động và làm tròn**. Sai số sau lượt trước đi vào lượt sau.
- Với phép nhân `100 * 0.1`, phần tử đầu vào `0.1` vốn đã xấp xỉ, nhưng phép nhân rồi làm tròn có thể dẫn đến một giá trị lưu khác; trong ví dụ này được in là đúng `10.0`.
- Không có quy tắc “nhân luôn chính xác hơn cộng”, và cũng không có bảo đảm “sai số của tổng luôn tăng”. Kết quả phụ thuộc giá trị, phương pháp và trình tự phép toán.

**Đừng lấy một ví dụ rồi suy ra `100 * 0.1` luôn tốt trong mọi hoàn cảnh.** Nó chỉ cho thấy những biểu thức tương đương toán học có thể cho ra `double` khác nhau.

### Vì sao so sánh bằng tuyệt đối gây bất ngờ?

~~~java
double sum = 0.0;
for (int i = 0; i < 10; i++) {
    sum += 0.1;
}
System.out.println(sum == 1.0); // false
~~~

Sai số ở đây nhỏ, nhưng `==` không có khái niệm “gần bằng”. Độ lớn sai số có thể quan trọng hoặc không quan trọng tùy ứng dụng, nên cần một tiêu chí đánh giá dựa trên miền dữ liệu thực tế thay vì mặc định kỳ vọng kết quả bằng bit.

## 5. Thứ tự cộng: phép cộng double không có tính kết hợp toán học

Với số thực lý tưởng, ta có `(a + b) + c = a + (b + c)`. Với số dấu phẩy động hữu hạn bit, **hai vế có thể khác nhau** do mỗi phép toán trung gian đều có thể làm tròn.

Chọn:

~~~java
double huge = 1.0e16;

double first = (huge + (-huge)) + 1.0;
double second = (huge + 1.0) + (-huge);

System.out.println(first);  // 1.0
System.out.println(second); // 0.0
~~~

Lý giải:

1. `huge + (-huge)` bằng zero chính xác, rồi `0.0 + 1.0` là `1.0`.
2. `huge + 1.0` quá sát `huge` so với khoảng cách giữa các giá trị double có thể biểu diễn quanh `1e16`: phần `1.0` bị mất khi làm tròn. Bước sau cộng `-huge` cho `0.0`.

Vì vậy, ngay cả khi **cùng các toán hạng và cùng phép cộng**, thay đổi thứ tự tính cũng có thể làm thay đổi kết quả.

| Cách cộng ba số | Giá trị |
| --- | ---: |
| `(1e16 + (-1e16)) + 1.0` | `1.0` |
| `(1e16 + 1.0) + (-1e16)` | `0.0` |

Tình huống này thường được gọi là **mất chữ số có nghĩa** khi cộng số nhỏ vào số quá lớn (*loss of significance*), kết hợp với phép triệt tiêu (*cancellation*). Triệt tiêu có thể làm lộ rõ sai số đã có ở các bước trước.

### Thứ tự của mảng và phép tổng hợp

Ví dụ một mảng có `[1e16, 1.0, -1e16]`:

~~~java
double[] values = {1e16, 1.0, -1e16};
double sum = 0.0;
for (double value : values) {
    sum += value;
}
System.out.println(sum); // 0.0
~~~

Nếu đổi thành `[1e16, -1e16, 1.0]`, vòng lặp tuần tự cho `1.0`. Do đó, khi so sánh kết quả giữa các triển khai, hãy ghi nhận **cả thứ tự đầu vào và thuật toán cộng dồn**.

Trong xử lý song song, thuật toán reduction có thể nhóm các phép cộng khác nhau; kết quả dấu phẩy động có thể khác với vòng lặp tuần tự mà không nhất thiết vi phạm hợp đồng của phép tổng. Không nên đòi `parallelStream` tái tạo chính xác bit-wise tổng của một `for` tuần tự trừ khi đã có hợp đồng xác định cụ thể và kiểm chứng.

## 6. Chương trình Java 17 đầy đủ có thể chạy và đối chiếu

Lưu tệp `FloatingPointExperiment.java`:

~~~java
import java.math.BigDecimal;
import java.util.Locale;

public class FloatingPointExperiment {

    static double repeated(double term, int count) {
        double sum = 0.0;
        for (int i = 0; i < count; i++) {
            sum += term;
        }
        return sum;
    }

    static double kahan(double term, int count) {
        double sum = 0.0;
        double compensation = 0.0;
        for (int i = 0; i < count; i++) {
            double y = term - compensation;
            double t = sum + y;
            compensation = (t - sum) - y;
            sum = t;
        }
        return sum;
    }

    public static void main(String[] args) {
        double tenth = 0.1;

        System.out.printf(Locale.ROOT, "hex(0.1) = %a%n", tenth);
        System.out.println("exact(0.1) = " + new BigDecimal(tenth));
        System.out.println("valueOf(0.1) = " + BigDecimal.valueOf(tenth));

        System.out.println("0.1+0.2 = " + (0.1 + 0.2));
        System.out.println("equal 0.3 = " + ((0.1 + 0.2) == 0.3));

        System.out.println("10 additions = " + repeated(0.1, 10));
        System.out.println("100 additions = " + repeated(0.1, 100));
        System.out.println("100 multiplied = " + (100 * 0.1));
        System.out.println("Kahan 100 additions = " + kahan(0.1, 100));

        System.out.println("BigDecimal 100 additions = "
                + BigDecimal.valueOf(0.1)
                    .multiply(BigDecimal.valueOf(100)));

        double large = 1e16;
        System.out.println("(big + -big) + 1 = "
                + ((large + -large) + 1.0));
        System.out.println("(big + 1) + -big = "
                + ((large + 1.0) + -large));

        System.out.println("ulp(1.0) = " + Math.ulp(1.0));
    }
}
~~~

Biên dịch/chạy với JDK 17 trở lên:

~~~bash
javac FloatingPointExperiment.java
java FloatingPointExperiment
~~~

Kết quả đã đối chiếu khi chạy trên Java 17:

~~~text
hex(0.1) = 0x1.999999999999ap-4
exact(0.1) = 0.1000000000000000055511151231257827021181583404541015625
valueOf(0.1) = 0.1
0.1+0.2 = 0.30000000000000004
equal 0.3 = false
10 additions = 0.9999999999999999
100 additions = 9.99999999999998
100 multiplied = 10.0
Kahan 100 additions = 10.0
BigDecimal 100 additions = 10.0
(big + -big) + 1 = 1.0
(big + 1) + -big = 0.0
ulp(1.0) = 2.220446049250313E-16
~~~

Chương trình có bốn nhóm đối chiếu:

- **Biểu diễn:** `0.1` dạng hex và decimal chính xác của double.
- **Tích lũy:** `repeated` cộng lặp 10/100 lần so với phép nhân một lần.
- **Cải thiện thuật toán:** `kahan` cộng bù để giảm mất mát khi tích lũy trong ví dụ.
- **Miền giá trị lớn:** cùng ba toán hạng nhưng hai thứ tự cộng tạo `1.0` và `0.0`.

Các con số được in từ API chuẩn, không phụ thuộc vào ngôn ngữ hệ thống nhờ `Locale.ROOT` ở lời gọi in hex. Các biểu thức in còn lại dựa vào `Double.toString` mặc định theo Java.

## 7. Sai số tuyệt đối, tương đối và đơn vị ULP

Để phân tích kết quả, đặt:

~~~text
expected = giá trị tham chiếu
actual   = giá trị tính được

absoluteError = |actual - expected|
relativeError = absoluteError / |expected|   (khi expected != 0)
~~~

**Sai số tuyệt đối** đo chênh lệch theo đơn vị của đại lượng. Ví dụ kết quả `9.99999999999998` so với 10 có sai số tuyệt đối khoảng `2 × 10^-14`.

**Sai số tương đối** biểu diễn sai lệch so với độ lớn giá trị chuẩn. Nó không phù hợp khi giá trị tham chiếu bằng 0; nếu gần 0, mẫu số quá nhỏ có thể làm tỷ lệ tăng mạnh dù sai số tuyệt đối nhỏ.

Một công cụ liên quan là `Math.ulp(x)` (*unit in the last place*): trả khoảng cách cỡ một bước biểu diễn dấu phẩy động tại độ lớn `x` theo định nghĩa của Java. Ví dụ:

~~~java
System.out.println(Math.ulp(1.0));
// 2.220446049250313E-16
~~~

Giá trị này cho biết mức độ chi tiết của double quanh 1.0. Khoảng cách giữa các số double có thể biểu diễn **không cố định trên toàn trục số**: khi trị tuyệt đối của số lớn lên, khoảng cách tuyệt đối giữa các số lân cận nhìn chung cũng lớn hơn.

Lưu ý: Không thể lấy `Math.ulp(1.0)` làm ngưỡng so sánh đúng cho mọi miền. Trong phép cộng `1e16 + 1.0`, bước biểu diễn tại `1e16` lớn hơn đơn vị `1.0`; đó là lý do đóng góp nhỏ có thể bị mất.

## 8. Kiểm tra gần bằng: đặt dung sai theo bài toán, không dùng một epsilon tùy tiện

Nếu mục tiêu là biết kết quả có **đủ gần** một tham chiếu không, có thể định nghĩa dung sai:

~~~java
double actual = 0.1 + 0.2;
double expected = 0.3;
double absoluteTolerance = 1e-12;

boolean nearEqual = Math.abs(actual - expected)
        <= absoluteTolerance;
System.out.println(nearEqual); // true
~~~

Đây là **mảnh code minh họa** đặt trong một phương thức/lớp Java. Ngưỡng `1e-12` chỉ là ví dụ cho bài toán có giá trị cỡ 0.3, **không phải hằng số đúng cho mọi ứng dụng**.

Hãy phân biệt:

- **Dung sai tuyệt đối:** phù hợp khi đơn vị và ngưỡng sai số có ý nghĩa cố định, đặc biệt quanh zero.
- **Dung sai tương đối:** hợp lý cho những giá trị có độ lớn trải rộng; so sánh chênh lệch với thang của dữ liệu.
- **So sánh theo ULP:** hữu ích trong kiểm thử thuật toán dấu phẩy động đã được phân tích kỹ; cần xét các trường hợp đặc biệt và miền biến thiên.

Một quy tắc kết hợp thường gặp về mặt ý tưởng:

~~~text
|actual - expected| <= max(absTolerance,
                           relTolerance * max(|actual|, |expected|))
~~~

Trước khi áp dụng thực tế phải xác định tolerance không âm, xử lý `NaN`, vô cực, zero và khả năng overflow/underflow ở phép tính chênh lệch hoặc nhân tolerance. Không áp dụng công thức này một cách máy móc cho mọi bài toán.

**Quan trọng:** Không dùng kiểm tra “gần bằng” để thay thế bằng nhau chính xác cho định danh, số đếm, số tiền hoặc các bất biến bắt buộc có giá trị exact. Chọn thuật toán/kiểu dữ liệu đúng vẫn quan trọng hơn việc tăng tolerance cho qua kiểm thử.

### Toán tử == không phải luôn sai

Với `double`, `==` chính xác khi bạn **muốn so giá trị nhị phân hiện có** và biết rõ điều kiện. Ví dụ `x == 0.0` là cách kiểm tra đúng nghĩa “x hiện đang bằng 0.0 theo phép so sánh double” (cả `+0.0` và `-0.0` đều bằng nhau theo `==`). Điều không an toàn là giả định các phép toán gần đúng tự cho kết quả bit-wise bằng một literal thập phân mong đợi.

`NaN` là trường hợp đặc biệt: `Double.NaN == Double.NaN` là false. Dùng `Double.isNaN(x)` khi cần nhận diện NaN và `Double.isFinite(x)` khi cần loại cả NaN/vô cực. Không kết luận một kết quả `false` của `==` luôn đến từ sai số làm tròn.

## 9. Cộng bù Kahan: giảm lỗi tích lũy, không biến double thành decimal exact

Thuật toán cộng bù của Kahan giữ một biến `compensation` để ước lượng phần nhỏ bị mất trong quá trình cộng:

~~~java
static double kahan(double[] values) {
    double sum = 0.0;
    double compensation = 0.0;
    for (double value : values) {
        double y = value - compensation;
        double t = sum + y;
        compensation = (t - sum) - y;
        sum = t;
    }
    return sum;
}
~~~

Đây là **mảnh phương thức** đặt trong lớp Java. Các bước theo trực giác:

1. `y` điều chỉnh hạng tử tiếp theo bằng phần lỗi bù đã ghi nhận.
2. `t` thực hiện cộng vào tổng.
3. `compensation` lưu một ước lượng của phần làm tròn từ phép cộng vừa diễn ra.
4. `sum` nhận kết quả tích lũy mới.

Trong ví dụ cộng `0.1` 100 lần, Kahan cho `10.0` thay vì `9.99999999999998`. Đây là **một cải thiện ở ví dụ cụ thể**, không phải lời hứa mọi tổng và mọi thứ tự cộng đều trở thành chính xác.

Các giới hạn:

- `0.1` đầu vào vẫn được biểu diễn gần đúng bằng double; thuật toán không thay được bản chất của kiểu số.
- Kahan có thể giúp với nhiều trường hợp tích lũy nhưng không xử lý hoàn hảo mọi trường hợp hủy chữ số, tràn, thiếu chính xác đầu vào hoặc dữ liệu có điều kiện số xấu.
- Các thuật toán khác như cộng theo cặp (*pairwise summation*), Neumaier summation hoặc sắp xếp hạng tử trước khi cộng có thể hữu ích tùy bài toán; sắp xếp lại có thể ảnh hưởng ý nghĩa xử lý dữ liệu, và không tự bảo đảm kết quả exact.
- Trong hệ thống cần kết quả có thể phát lại, phải giữ cố định thuật toán, thứ tự và chế độ song song theo hợp đồng.

Nếu quan trọng là **đúng 100 lần số thập phân 0.1 bằng 10.0**, dùng kiểu biểu diễn decimal chính xác như `BigDecimal` từ chuỗi, thay vì coi Kahan là thay thế chung.

## 10. BigDecimal: biểu diễn thập phân chính xác khi đầu vào chính xác

`BigDecimal` có thể lưu chính xác số decimal đã định nghĩa trong chuỗi:

~~~java
BigDecimal tenth = new BigDecimal("0.1");
BigDecimal sum = BigDecimal.ZERO;

for (int i = 0; i < 100; i++) {
    sum = sum.add(tenth);
}

System.out.println(sum.toPlainString()); // 10.0
~~~

Khác biệt căn bản:

- `new BigDecimal("0.1")` diễn giải thập phân trong chuỗi và lưu đúng giá trị 0.1.
- `BigDecimal.valueOf(0.1)` lấy biểu diễn thập phân chuẩn của **giá trị double** qua `Double.toString`, nên với literal này tạo `BigDecimal` có giá trị decimal `0.1`. Đây thường là cách hợp lý khi nguồn đầu vào thực sự chỉ có sẵn kiểu double và muốn biểu diễn thập phân chuẩn của nó.
- `new BigDecimal(0.1)` lưu chính xác **giá trị nhị phân đã làm tròn** của double, vì thế có chuỗi thập phân dài `0.10000000000000000555...`, **không** phải exact 1/10.

So sánh:

~~~java
System.out.println(new BigDecimal("0.1"));
// 0.1

System.out.println(BigDecimal.valueOf(0.1));
// 0.1

System.out.println(new BigDecimal(0.1));
// 0.1000000000000000055511151231257827021181583404541015625
~~~

`BigDecimal` dùng số thập phân tùy ý precision để thực hiện các phép cộng/nhân chính xác khi không yêu cầu một `MathContext` làm tròn. Nhưng **không có nghĩa mọi phép chia đều cho biểu diễn thập phân hữu hạn**. Chẳng hạn `1 / 3` không kết thúc trong decimal; cần định nghĩa scale và `RoundingMode` khi chia.

Lưu ý thêm: `BigDecimal` immutable, nên phải **gán lại** `sum = sum.add(tenth)`; nếu chỉ gọi `sum.add(tenth);` mà bỏ kết quả, `sum` không thay đổi.

## 11. Cách tiến hành một thí nghiệm có ý nghĩa

Để so sánh sai số giữa các chiến lược, hãy xác định trước:

1. **Giá trị tham chiếu:** trong thí nghiệm 100 lần 0.1, giá trị decimal lý tưởng là 10.0. Tạo tham chiếu bằng `BigDecimal("0.1")`, không suy ra từ cùng phép cộng double đang kiểm tra.
2. **Dữ liệu và thứ tự:** số phần tử, giá trị ban đầu, thứ tự nhập, trường hợp có độ lớn rất chênh lệch và dấu đối nhau.
3. **Thuật toán:** vòng lặp tuần tự, phép nhân, Kahan, pairwise, hoặc `BigDecimal`.
4. **Chỉ tiêu:** giá trị in, sai số tuyệt đối/tương đối, thời gian và chi phí bộ nhớ khi thích hợp.
5. **Quy tắc kết luận:** hiệu năng không thể được đánh giá đáng tin chỉ bằng một lần đo nhỏ; độ chính xác không thể được đánh giá chỉ qua một ví dụ thuận lợi.

Một bảng thí nghiệm minh họa:

| Bài thử | Thuật toán | Kết quả | Giá trị tham chiếu |
| --- | --- | --- | --- |
| 10 × 0.1 | Cộng double lặp | `0.9999999999999999` | 1.0 |
| 100 × 0.1 | Cộng double lặp | `9.99999999999998` | 10.0 |
| 100 × 0.1 | Nhân double | `10.0` | 10.0 |
| 100 × 0.1 | Kahan double | `10.0` | 10.0 |
| 100 × 0.1 | BigDecimal từ chuỗi | `10.0` | 10.0 |
| 1e16, -1e16, 1 | Cộng theo một thứ tự | `1.0` | 1.0 |
| 1e16, 1, -1e16 | Cộng theo thứ tự khác | `0.0` | 1.0 |

Hai hàng cuối đặc biệt quan trọng: kết quả có thể **lệch hẳn 1.0** chứ không chỉ ở vài chữ số cuối, khi các hạng tử chênh lệch độ lớn và triệt tiêu nhau.

## 12. Stream và song song: đừng vô tình thay đổi mô hình cộng dồn

Java cung cấp:

~~~java
double sum = java.util.Arrays.stream(values).sum();
~~~

Khi `values` là mảng `double[]`, đây là `DoubleStream.sum()`. Hành vi thực hiện có thể sử dụng kỹ thuật cộng bù/các chiến lược được Java mô tả để cải thiện chất lượng kết quả. Không nên mặc định phép tổng này nhất thiết giống vòng `for (double x : values) sum += x`, đặc biệt khi cấu trúc pipeline hoặc thực thi song song khiến thứ tự kết hợp thay đổi.

Với số dấu phẩy động:

- Tổng toán học không đổi khi hoán vị thứ tự, nhưng tổng thực hiện bằng số hữu hạn bit có thể đổi.
- Cộng theo nhánh song song có thể thay đổi thứ tự kết hợp; kết quả có thể phụ thuộc chiến lược reduction.
- Các benchmark so sánh độ chính xác và tốc độ phải xác định rõ tập dữ liệu, thuật toán và điều kiện chạy.
- Không dùng `parallel()` chỉ vì nghĩ kết quả số học phải giống từng bit với vòng lặp tuần tự.

Nếu một kết quả phải tái lập chính xác, hãy chọn thuật toán có hợp đồng đủ chặt và kiểm soát cách phân chia/cộng kết quả. Nếu cần decimal exact, xem xét `BigDecimal` và mô hình làm tròn nghiệp vụ thay vì dựa vào thứ tự tổng hợp double.

## 13. Khi nào nên dùng double, khi nào nên dùng BigDecimal?

| Nhu cầu | Lựa chọn thường phù hợp | Điểm cần kiểm tra |
| --- | --- | --- |
| Đồ họa, mô phỏng, cảm biến, tính toán khoa học chấp nhận xấp xỉ | `double` | Sai số tuyệt đối/tương đối, điều kiện số, thuật toán ổn định |
| Tổng nhiều số có độ lớn chênh nhau | `double` với phương pháp tổng thích hợp, nếu chính sách sai số cho phép | Thứ tự, khả năng triệt tiêu, Kahan/pairwise, test theo tham chiếu |
| Số tiền theo đơn vị thập phân chính xác | `BigDecimal` từ chuỗi hoặc đơn vị nhỏ nhất bằng `long` khi phù hợp | Scale, rounding, overflow, đơn vị tiền |
| Giá trị decimal cần đúng 0.1 + 0.2 = 0.3 | `BigDecimal("0.1")` + `BigDecimal("0.2")` | Không đi vòng qua double |
| Số đếm nguyên | `int`, `long`, `BigInteger` theo miền | Overflow, không dùng float/double nếu cần số nguyên chính xác |

Không có quy tắc rằng `BigDecimal` luôn tốt hơn `double`. Nó thường có chi phí tính toán/bộ nhớ cao hơn, yêu cầu quản lý scale và chính sách làm tròn. Ngược lại, `double` hiệu quả và phù hợp cho nhiều ứng dụng số học xấp xỉ nếu kết quả được đánh giá bằng tiêu chí sai số đúng đắn.

## 14. Lỗi thường gặp và cách sửa

**Lỗi 1 — Tin rằng 0.1 lưu trong double đúng 1/10.** `println(0.1)` in ngắn gọn; dùng `Double.toHexString` và `new BigDecimal(0.1)` khi cần khám phá giá trị chính xác của double.

**Lỗi 2 — So `0.1 + 0.2 == 0.3` để kiểm thử phép cộng số thực xấp xỉ.** Phép so sánh có thể false do biểu diễn và làm tròn. Dùng tolerance có ý nghĩa hoặc mô hình thập phân exact theo yêu cầu.

**Lỗi 3 — Nghĩ cộng lặp và phép nhân phải cho cùng double.** Nhiều phép cộng có nhiều lần làm tròn; một phép nhân chỉ thực hiện bước tính/làm tròn tương ứng của nó. Kết quả có thể khác.

**Lỗi 4 — Mặc định phép cộng dấu phẩy động kết hợp được.** `(a+b)+c` và `a+(b+c)` có thể khác. Các số độ lớn rất chênh lệch hoặc trái dấu là tình huống dễ bộc lộ sai lệch.

**Lỗi 5 — Dùng ngưỡng sai số chung cho tất cả số.** `1e-12` có thể hợp lý ở thang 1 nhưng vô nghĩa ở thang 1e15 hoặc 1e-20. Xác định tolerance theo đơn vị, ngưỡng nghiệp vụ và độ lớn của giá trị.

**Lỗi 6 — Suy ra Kahan cho kết quả luôn chính xác.** Nó giảm sai số tích lũy trong nhiều trường hợp nhưng không sửa tất cả tình huống triệt tiêu và không biến dữ liệu double thành decimal exact.

**Lỗi 7 — Dùng `new BigDecimal(0.1)` để thể hiện một phần mười exact.** Constructor này nhận bit-value double đã xấp xỉ. Hãy tạo từ chuỗi `"0.1"` khi nguồn chính là thập phân đó.

**Lỗi 8 — Gọi `sum.add(...)` mà không gán kết quả.** `BigDecimal` immutable; phải dùng `sum = sum.add(...)`.

**Lỗi 9 — Kết luận giá trị double bị lỗi chỉ vì biểu diễn in ra khác mong đợi.** Cần định lượng sai số so với giá trị tham chiếu, xem xét yêu cầu và điều kiện số. Một sai lệch nhỏ có thể chấp nhận được, nhưng cũng có thể tích lũy thành lỗi lớn.

**Lỗi 10 — Coi số 0 âm, NaN và Infinity là các giá trị thông thường.** Những giá trị đặc biệt ảnh hưởng so sánh và phép đo sai số. Dùng `Double.isFinite` và quy tắc xử lý dữ liệu đầu vào rõ ràng.

**Lỗi 11 — So sánh kết quả vòng lặp với DoubleStream.sum rồi khẳng định Java chạy sai.** Thuật toán và thứ tự cộng dồn có thể khác. Chỉ kết luận sau khi so sánh cùng giả định và hợp đồng.

**Lỗi 12 — Dựa vào một bộ test với 0.1 để kết luận thuật toán ổn định.** Hãy khảo sát thêm số rất lớn/nhỏ, dấu đối nhau, phép trừ gần nhau, nhiều lượt cộng, đầu vào ngẫu nhiên lẫn phản ví dụ có chủ đích.

## 15. Bài luyện tập và đáp án

### Bài 1 — Dự đoán và giải thích

~~~java
double s = 0.0;
for (int i = 0; i < 10; i++) {
    s += 0.1;
}
System.out.println(s);
System.out.println(s == 1.0);
~~~

**Đáp án:**

~~~text
0.9999999999999999
false
~~~

Mỗi lần cộng sử dụng double gần đúng và có thể làm tròn. Sai số cuối khiến giá trị biểu diễn khác `1.0`.

### Bài 2 — Đổi thứ tự cộng

Với `a=1e16`, `b=-1e16`, `c=1.0`, hãy dự đoán:

~~~java
double left = (a + b) + c;
double right = (a + c) + b;
~~~

**Đáp án:** `left = 1.0`, `right = 0.0`. Nhánh `a+c` làm mất `1.0` khi làm tròn vì số lớn hơn quá nhiều.

### Bài 3 — BigDecimal: chọn constructor

Dự đoán:

~~~java
BigDecimal a = new BigDecimal("0.1");
BigDecimal b = new BigDecimal("0.2");
System.out.println(a.add(b).toPlainString());
~~~

**Đáp án:** `0.3`. Cả hai được tạo chính xác từ chuỗi decimal và `add` không yêu cầu làm tròn.

Nếu thay `new BigDecimal("0.1")` bằng `new BigDecimal(0.1)`, dữ liệu đầu vào đã là giá trị nhị phân xấp xỉ, không còn là exact 1/10.

### Bài 4 — Thiết kế kiểm thử sai số

Bạn cần kiểm tra một thuật toán tính tổng dữ liệu cảm biến có giá trị khoảng `10^-6` và `10^6`. Có nên dùng một epsilon tuyệt đối duy nhất như `1e-12`?

**Đáp án:** Không nên mặc định. Cần xem đơn vị, thang giá trị, mức nhiễu đo, yêu cầu sai số và khả năng triệt tiêu; có thể cần phối hợp sai số tuyệt đối, tương đối hoặc các tiêu chí thống kê riêng. Tolerance là đặc tả, không phải thủ thuật để làm mọi test pass.

### Bài 5 — Kahan có thay thế BigDecimal không?

Trong ví dụ 100 lần cộng `0.1`, Kahan cho `10.0`. Có thể suy ra Kahan lưu exact `0.1` không?

**Đáp án:** Không. Biểu diễn đầu vào vẫn là binary64 gần đúng; Kahan giúp bù phần sai số tích lũy. Với nghiệp vụ cần giá trị decimal chính xác, dùng `BigDecimal` từ chuỗi và quy tắc làm tròn rõ ràng.

## 16. Checklist khi khảo sát một lỗi số dấu phẩy động

1. **Giá trị đầu vào thực tế là gì?** Decimal từ chuỗi, double đã làm tròn hay dữ liệu cảm biến?
2. **Giá trị tham chiếu là gì?** Được tạo độc lập bằng một mô hình đủ chính xác chưa?
3. **Đã phân biệt sai số biểu diễn với sai số phép tính và sai số tích lũy chưa?**
4. **Có số quá lớn so với số quá nhỏ trong cùng tổng không?** Có triệt tiêu hay đổi thứ tự cộng không?
5. **Kiểm thử có nhìn cả giá trị in, hex bit-value, sai số tuyệt đối/tương đối và ULP khi cần không?**
6. **Nếu cần cộng nhiều phần tử, có chọn thuật toán cộng dồn phù hợp và xác định thứ tự xử lý không?**
7. **Nếu cần decimal exact, có dùng `BigDecimal` từ chuỗi hoặc kiểu nguyên biểu diễn đơn vị nhỏ nhất không?**
8. **Nếu dùng tolerance, ngưỡng có xuất phát từ yêu cầu nghiệp vụ/đơn vị và đã xét NaN/vô cực chưa?**

**Kết luận:** `0.1` không biểu diễn chính xác được trong binary64, nên `0.1 + 0.2`, tổng cộng lặp và phép cộng ở các thứ tự khác nhau có thể cho các kết quả `double` khác nhau. Đây là giới hạn của số dấu phẩy động hữu hạn bit. Hãy thực nghiệm, đo sai số và chọn đúng thuật toán/kiểu số; không nhầm **gần đúng đủ tốt** với **bằng nhau chính xác theo giá trị decimal**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-practice-005">Xây dựng hàm so sánh gần bằng có hợp đồng rõ ràng</a>

<details>
<summary>Xem chi tiết</summary>

# Xây dựng hàm so sánh gần bằng có hợp đồng rõ ràng trong Java

## 1. Mục tiêu thực hành

Trong Java, `float` và `double` dùng biểu diễn nhị phân hữu hạn theo IEEE 754. Nhiều số thập phân quen thuộc, chẳng hạn `0.1` hoặc `0.2`, không biểu diễn chính xác bằng một số bit hữu hạn. Vì vậy phép tính:

```java
double value = 0.1 + 0.2;
System.out.println(value);       // 0.30000000000000004
System.out.println(value == 0.3); // false
```

cho kết quả khác trực giác thập phân. Đôi khi bài toán cho phép xem hai giá trị sai lệch rất nhỏ là **gần bằng nhau** (*approximately equal*), chẳng hạn khi đối chiếu kết quả đo đạc, mô phỏng hoặc phép tính dấu phẩy động có dung sai đã được quy định.

Tuy nhiên, câu “gần bằng nếu chênh lệch đủ nhỏ” chưa đủ để triển khai: **đủ nhỏ so với cái gì, khi dữ liệu gần 0 thì sao, có tính vô cực/NaN không, và những giá trị không hợp lệ được xử lý thế nào?** Một hàm so sánh cần **hợp đồng** (*contract*) tường minh và bộ kiểm thử sát biên.

Bài này xây dựng một hàm Java 17+ cho `double` với **absolute tolerance** (ngưỡng sai lệch tuyệt đối) và **relative tolerance** (ngưỡng sai lệch tương đối), xử lý `NaN`, `Infinity`, `+0.0`/`-0.0`, tránh lỗi tràn trong phép trừ; đồng thời chỉ ra giới hạn toán học khiến hàm gần bằng **không** thay thế được `equals()` hoặc `compareTo()`.

## 2. Phải chọn công thức và ý nghĩa trước khi viết code

Gọi hai số cần so sánh là \(a\) và \(b\). Với các số hữu hạn, ta định nghĩa:

\[
\Delta = |a-b|,\qquad
S = \max(|a|,|b|),\qquad
T = \max(\mathrm{absTol}, \mathrm{relTol}\times S)
\]

Hàm trả về **`true` nếu và chỉ nếu** \(\Delta \le T\), sau khi xử lý các ngoại lệ `NaN`, vô cực và bằng nhau chính xác theo hợp đồng.

- **`absTol`:** sai số tuyệt đối tối đa theo **đơn vị của đại lượng**. Ví dụ kết quả đo bằng mét có thể cho phép lệch `0.001` mét.
- **`relTol`:** tỷ lệ sai lệch cho phép theo độ lớn của hai giá trị, ví dụ `1e-9` là một phần trên một tỷ. Đây là tỷ lệ **không có đơn vị**.
- **`max(absTol, relTol × S)`:** cho phép sai lệch theo **một trong hai điều kiện** (absolute hoặc relative). Đây là **lựa chọn chính sách trong bài**, không phải định nghĩa duy nhất của “gần bằng”.

Cách chọn `max` đồng nghĩa:

```text
abs(a - b) <= absTol
                 HOẶC
abs(a - b) <= relTol * max(abs(a), abs(b))
```

Một số thư viện dùng `absTol + relTol × S` hoặc dạng khác. Hai công thức không giống nhau gần ranh giới, nên không thể thay thế tùy tiện nếu chưa cập nhật hợp đồng và test.

### Vì sao cần kết hợp cả hai loại dung sai?

Chỉ có **sai số tuyệt đối**:

```text
abs(a-b) <= 0.001
```

hợp lý nếu tất cả kết quả có một thang đo cố định, nhưng có thể quá chặt đối với những số cỡ một tỷ và quá lỏng đối với những số cỡ một phần tỷ.

Chỉ có **sai số tương đối**:

```text
abs(a-b) <= 1e-9 * max(abs(a), abs(b))
```

tự thay đổi theo độ lớn của giá trị, nhưng gần `0`, độ lớn `S` tiến về `0` và ngưỡng tương đối cũng tiến về `0`. Do đó cần một ngưỡng tuyệt đối có ý nghĩa nghiệp vụ để quyết định “đủ gần 0”.

Ví dụ, `absTol = 1e-9`, `relTol = 0` coi `0.0` và `1e-12` là gần bằng. Còn `absTol = 0`, `relTol = 1e-9` chỉ có thể chấp nhận các giá trị đủ gần nhau theo tỷ lệ, không cung cấp “vùng an toàn” tuyệt đối quanh 0.

## 3. Hợp đồng của hàm sẽ triển khai

Hàm có chữ ký:

```java
static boolean nearlyEqual(
        double a, double b, double absTol, double relTol)
```

Hợp đồng rõ ràng:

1. Hai tham số `a`, `b` có thể là bất kỳ giá trị `double`, bao gồm hữu hạn, `+0.0`, `-0.0`, hai vô cực và `NaN`.
2. `absTol` phải **hữu hạn và không âm**. Chấp nhận `0.0`; từ chối `NaN`, vô cực và mọi số âm.
3. `relTol` phải **hữu hạn và thuộc khoảng `[0, 1)`**. Ràng buộc nhỏ hơn 1 là một quyết định trong hợp đồng để relative tolerance luôn nhỏ hơn 100% độ lớn tham chiếu; ứng dụng thực tế nên chọn giá trị nhỏ hơn rất nhiều.
4. **`NaN` ở một trong hai toán hạng → `false`**, kể cả khi cả hai đều là NaN.
5. Nếu `a == b` theo so sánh primitive của Java → **`true`**. Do đó cùng một vô cực theo dấu trả `true` và `+0.0` gần bằng `-0.0`.
6. Nếu chỉ một toán hạng là vô cực, hoặc chúng là hai vô cực khác dấu → **`false`**. Không cố tính sai số hữu hạn cho vô cực.
7. Với hai số hữu hạn còn lại, tính \(\Delta\), \(S\), \(T\) theo công thức ở phần 2 và trả `true` khi \(\Delta \le T\). **Đúng bằng ngưỡng cũng được chấp nhận**.
8. Với tolerance không hợp lệ, ném `IllegalArgumentException` **trước khi xem dữ liệu**. Nghĩa là ngay cả `nearlyEqual(1, 1, NaN, 0)` vẫn ném lỗi: cấu hình sai không được âm thầm bỏ qua.

Ràng buộc dung sai và hành vi đối với special values là quyết định thiết kế. Một hệ thống khác có thể chọn coi NaN là lỗi đầu vào và ném exception, hoặc yêu cầu tất cả đầu vào đều hữu hạn. Điều quan trọng là **công khai chính sách** và kiểm thử chúng, chứ không để chúng phát sinh ngẫu nhiên từ các toán tử Java.

## 4. Cài đặt Java 17+: phiên bản có thể dùng và kiểm thử

Lưu toàn bộ file dưới tên **`NearlyEqualDemo.java`**:

```java
public class NearlyEqualDemo {

    /**
     * Approximate comparison by an absolute-or-relative tolerance:
     * |a - b| <= max(absTol, relTol * max(|a|, |b|)).
     *
     * absTol: finite, >= 0.
     * relTol: finite, 0 <= relTol < 1.
     * NaN: false; same-sign infinities: true;
     * other infinite pairs: false; +0 and -0: true.
     */
    public static boolean nearlyEqual(
            double a, double b, double absTol, double relTol) {

        if (!Double.isFinite(absTol) || absTol < 0.0
                || !Double.isFinite(relTol)
                || relTol < 0.0 || relTol >= 1.0) {
            throw new IllegalArgumentException("invalid tolerances");
        }

        if (Double.isNaN(a) || Double.isNaN(b)) {
            return false;
        }

        if (a == b) {
            return true;
        }

        if (!Double.isFinite(a) || !Double.isFinite(b)) {
            return false;
        }

        double difference = Math.abs(a - b);
        double scale = Math.max(Math.abs(a), Math.abs(b));
        double allowed = Math.max(absTol, relTol * scale);
        return difference <= allowed;
    }

    public static void main(String[] args) {
        System.out.println("0.1 + 0.2 ~= 0.3: "
                + nearlyEqual(0.1 + 0.2, 0.3, 1e-12, 0.0));

        System.out.println("large relative pass: "
                + nearlyEqual(
                        1_000_000_000.0, 1_000_000_001.0, 0.0, 1e-9));
        System.out.println("large relative fail: "
                + nearlyEqual(
                        1_000_000_000.0, 1_000_000_002.0, 0.0, 1e-9));
        System.out.println("near zero absolute: "
                + nearlyEqual(0.0, 1e-12, 1e-9, 0.0));

        System.out.println("zero tolerances require exact equality: "
                + nearlyEqual(1.0, Math.nextUp(1.0), 0.0, 0.0));
        System.out.println("one ulp accepted: "
                + nearlyEqual(
                        1.0, Math.nextUp(1.0), Math.ulp(1.0), 0.0));

        System.out.println("signed zeros: "
                + nearlyEqual(+0.0, -0.0, 0.0, 0.0));
        System.out.println("same infinities: "
                + nearlyEqual(
                        Double.POSITIVE_INFINITY,
                        Double.POSITIVE_INFINITY, 0.0, 0.0));
        System.out.println("different infinities: "
                + nearlyEqual(
                        Double.POSITIVE_INFINITY,
                        Double.NEGATIVE_INFINITY, 0.0, 0.0));
        System.out.println("NaNs: "
                + nearlyEqual(Double.NaN, Double.NaN, 1.0, 0.1));

        System.out.println("overflowing difference: "
                + nearlyEqual(
                        Double.MAX_VALUE, -Double.MAX_VALUE,
                        Double.MAX_VALUE, 0.5));

        double sub = Double.MIN_VALUE;
        System.out.println("subnormal exact-only: "
                + nearlyEqual(0.0, sub, 0.0, 0.0));
        System.out.println("subnormal with absTol: "
                + nearlyEqual(0.0, sub, sub, 0.0));

        boolean ab = nearlyEqual(0.0, 0.75, 1.0, 0.0);
        boolean bc = nearlyEqual(0.75, 1.5, 1.0, 0.0);
        boolean ac = nearlyEqual(0.0, 1.5, 1.0, 0.0);
        System.out.println(
                "not transitive: " + ab + ", " + bc + ", " + ac);

        try {
            nearlyEqual(1.0, 1.0, Double.NaN, 0.0);
            System.out.println("invalid tolerance: accepted");
        } catch (IllegalArgumentException expected) {
            System.out.println("invalid tolerance: rejected");
        }
    }
}
```

Biên dịch và chạy với JDK 17+:

```bash
javac NearlyEqualDemo.java
java NearlyEqualDemo
```

Đầu ra:

```text
0.1 + 0.2 ~= 0.3: true
large relative pass: true
large relative fail: false
near zero absolute: true
zero tolerances require exact equality: false
one ulp accepted: true
signed zeros: true
same infinities: true
different infinities: false
NaNs: false
overflowing difference: false
subnormal exact-only: false
subnormal with absTol: true
not transitive: true, true, false
invalid tolerance: rejected
```

### Giải thích thứ tự xử lý

**Kiểm tra tolerance trước:** nếu `absTol` là `NaN` mà ta dùng trực tiếp `Math.max`, kết quả so sánh có thể bị ảnh hưởng khó hiểu. Điều tệ hơn là một nhánh xử lý giá trị bằng nhau có thể che giấu cấu hình sai. Vì vậy, validation là bước đầu.

**NaN trước toán tử bằng:** trong Java, `Double.NaN == Double.NaN` là `false`. Việc xét riêng cho biết rõ đây là chính sách, thay vì tình cờ phụ thuộc toán tử.

**Đúng bằng nhau trước kiểm tra vô cực:** `Double.POSITIVE_INFINITY == Double.POSITIVE_INFINITY` là `true`; hai số 0 khác dấu cũng `==`. Không nên lấy `Infinity - Infinity` rồi so sánh, vì hiệu như vậy có thể tạo `NaN`.

**Kiểm tra hữu hạn trước tính sai số:** nếu còn ít nhất một vô cực, `a != b` và không thể áp công thức cho hai số hữu hạn, hàm trả `false`.

**Tính ngưỡng rồi so sánh:** với hai số hữu hạn, hàm dùng `difference <= allowed`, chấp nhận cả trường hợp bằng đúng ngưỡng. `Math.max(Math.abs(a), Math.abs(b))` khiến kết quả **đối xứng** khi đổi chỗ hai toán hạng. Không dùng riêng `Math.abs(a)` làm mẫu số/thang đo vì khi đảo đối số có thể cho kết quả khác.

## 5. Vì sao có thể viết Math.abs(a - b) ở đây?

Một lỗi thường gặp là khẳng định hiệu hai số `double` hữu hạn luôn hữu hạn. Không đúng: với `a = Double.MAX_VALUE` và `b = -Double.MAX_VALUE`, biểu thức `a - b` **tràn thành `+Infinity`**, dù cả hai toán hạng đều hữu hạn.

Hàm trên kiểm soát trường hợp đó bằng hợp đồng cụ thể:

- `absTol` phải **hữu hạn**, nên không lớn hơn `Double.MAX_VALUE`.
- `relTol < 1` và `scale` hữu hạn, nên `relTol * scale` vẫn hữu hạn và không vượt `Double.MAX_VALUE`.
- Nếu `Math.abs(a-b)` thành `Infinity`, sai lệch thực lớn hơn miền hữu hạn của `double`, và **không thể nhỏ hơn hoặc bằng ngưỡng hữu hạn** đã chọn. Vì vậy `Infinity <= allowed` là `false` và phù hợp với chính sách này.

Điều này giải thích vì sao hàm **không cần giả vờ trừ chính xác bằng BigDecimal** cho mọi trường hợp. Tuy nhiên, các phép trừ dấu phẩy động vẫn có làm tròn. Nếu cần chứng minh chính xác ranh giới độ sai số tính theo số thực toán học, phải xem xét cách biểu diễn đầu vào và đặc tả bài toán; hàm trên có hợp đồng dựa trên các **giá trị double thực tế và phép tính double**.

Không dùng thay bằng biểu thức:

```java
Math.abs(a / b - 1.0) <= relTol
```

nếu chưa xử lý `b == 0`, dấu âm, underflow, overflow và tính đối xứng. Phép chia có thể không xác định hoặc tạo vô cực; chọn scale bằng `max(abs(a), abs(b))` là quy tắc rõ ràng hơn cho hợp đồng đã chọn.

## 6. Chọn ngưỡng dung sai từ miền bài toán

Các giá trị `1e-12`, `1e-9` trong chương trình là **giá trị minh họa**, không phải dung sai mặc định an toàn cho mọi hệ thống. Một ngưỡng phù hợp phải xuất phát từ ít nhất một trong các yếu tố:

- **Đơn vị và độ phân giải đầu vào:** thiết bị đo nhiệt độ có sai số ±0,1°C không cần dung sai `1e-15` °C.
- **Giới hạn sai số được nghiệm thu:** nếu hệ thống định nghĩa kết quả chênh không quá 0,01 đơn vị, `absTol = 0.01` có ý nghĩa nghiệp vụ cụ thể.
- **Độ lớn biến thiên:** kết quả có thể từ phần triệu tới hàng tỷ; `relTol` hữu ích khi sai số cho phép tỷ lệ với độ lớn.
- **Tích lũy lỗi của thuật toán:** sau nhiều phép cộng/nhân, sai số làm tròn có thể tích lũy tùy thuật toán, thứ tự phép tính và điều kiện số (*numerical conditioning*).
- **Nhu cầu xác nhận chính xác:** với tiền tệ hoặc dữ liệu định danh số nguyên, một ngưỡng gần bằng tùy ý có thể che giấu lỗi quan trọng.

### Ví dụ về ngưỡng tuyệt đối

Nếu hai cảm biến cùng đo chiều dài theo mét và yêu cầu “chênh tối đa `0.002` mét”, hàm có thể gọi:

```java
boolean close = nearlyEqual(measuredA, measuredB, 0.002, 0.0);
```

Đây là ngưỡng **định nghĩa bằng mét**, áp dụng đồng đều trên miền giá trị. Không nên dùng `0.002` nếu giá trị đang ở đơn vị milimét mà không đổi đơn vị tương ứng.

### Ví dụ về ngưỡng tương đối

Nếu yêu cầu “sai khác dưới một phần triệu so với độ lớn của giá trị”, có thể chọn `relTol = 1e-6` và một `absTol` nhỏ phù hợp khi gần 0. Với `a` và `b` khoảng một triệu, ngưỡng tương đối khi ấy khoảng 1 đơn vị; với giá trị cỡ một phần triệu, ngưỡng tương đối chỉ cỡ `1e-12`.

Công thức của bài sử dụng **`<=`** nên “dưới một phần triệu” cần được diễn đạt chính xác trong hợp đồng là **không vượt quá ngưỡng**. Nếu yêu cầu thật sự là “nhỏ hơn nghiêm ngặt”, phải đổi toán tử và sửa bài kiểm thử biên.

### Tolerance bằng 0

Khi `absTol = 0` và `relTol = 0`, hai số hữu hạn phải có hiệu tính bằng 0 để gần bằng; với các số finite thông thường, tương đương so sánh `==`. Trong chính sách đang dùng, `+0.0` và `-0.0` bằng nhau, hai vô cực cùng dấu bằng nhau, còn NaN không gần bằng chính nó.

Nếu yêu cầu **so sánh cùng mẫu bit**, dùng `Double.doubleToRawLongBits` để kiểm tra riêng; đó là hợp đồng khác hoàn toàn so với gần bằng về số học.

## 7. Kiểm thử theo lớp tương đương và giá trị biên

Một hàm so sánh gần bằng cần kiểm thử theo **hợp đồng**, không chỉ bằng ví dụ `0.1 + 0.2`. Các nhóm ca tối thiểu:

| Nhóm | Ví dụ | Kỳ vọng theo hợp đồng |
|---|---|---|
| Đúng bằng finite | `(1.0, 1.0, 0, 0)` | `true` |
| Sai lệch thập phân nhỏ | `(0.1+0.2, 0.3, 1e-12, 0)` | `true` |
| Vượt ngưỡng tuyệt đối | `(0, 0.02, 0.01, 0)` | `false` |
| Gần 0 | `(0, 1e-12, 1e-9, 0)` | `true` |
| Số lớn, ngưỡng tương đối đủ | `(1e9, 1e9+1, 0, 1e-9)` | `true` |
| Số lớn, ngưỡng tương đối thiếu | `(1e9, 1e9+2, 0, 1e-9)` | `false` |
| Không dung sai | `(1, Math.nextUp(1), 0, 0)` | `false` |
| Một ULP ở 1 | `(1, Math.nextUp(1), Math.ulp(1), 0)` | `true` |
| Hai zero có dấu | `(+0.0, -0.0, 0, 0)` | `true` |
| Hai vô cực dương | `(+∞, +∞, 0, 0)` | `true` |
| Vô cực khác dấu | `(+∞, -∞, 0, 0)` | `false` |
| NaN | `(NaN, NaN, 1, 0.1)` | `false` |
| Hiệu double tràn | `(MAX_VALUE, -MAX_VALUE, MAX_VALUE, 0.5)` | `false` |
| Subnormal sát 0 | `(0, MIN_VALUE, 0, 0)` | `false` |
| Subnormal có dung sai | `(0, MIN_VALUE, MIN_VALUE, 0)` | `true` |
| Tolerance không hợp lệ | `absTol = NaN`; `relTol = 1`; `absTol < 0` | Ném `IllegalArgumentException` |

`Double.MIN_VALUE` là **số `double` dương nhỏ nhất khác 0** (subnormal), không phải số âm có độ lớn lớn nhất. `Double.MAX_VALUE` là số hữu hạn dương lớn nhất. Không nhầm hai hằng số này khi thiết kế test.

### Kiểm tra tính đối xứng

Hàm được định nghĩa đối xứng: `nearlyEqual(a,b,t,r)` phải bằng `nearlyEqual(b,a,t,r)` với cùng tolerance hợp lệ. Việc lấy `max(abs(a),abs(b))` giúp giữ thuộc tính này. Nếu dùng `relTol * abs(a)` riêng và xem `a` là tham chiếu, đó là **so sánh có hướng**; cũng có ứng dụng hợp lệ nhưng phải ghi rõ và không gọi là quan hệ đối xứng.

### Kiểm tra gần biên với nextUp và nextDown

Dùng `Math.nextUp(x)` và `Math.nextDown(x)` để lấy giá trị `double` liền kề trong thứ tự số thực có thể biểu diễn, thay vì tự cộng `1e-16` rồi tưởng rằng số thực sẽ đổi. `Math.ulp(x)` cho khoảng cách ULP tại vùng đó (với chú ý về vị trí sát ranh giới số mũ).

Ví dụ `Math.nextUp(1.0)` khác `1.0` một bước `Math.ulp(1.0)`. Với `absTol = 0` và `relTol = 0`, hàm phải trả `false`; với `absTol = Math.ulp(1.0)` thì trả `true`.

**Đừng mặc định N ULP giống một sai số nghiệp vụ cố định.** ULP đo theo cấu trúc biểu diễn nhị phân và thay đổi theo độ lớn; việc chọn ULP làm tiêu chí phải xuất phát từ đặc điểm thuật toán, không thay thế vô điều kiện ngưỡng tuyệt đối/tương đối.

## 8. Những tính chất toán học và giới hạn quan trọng

### Gần bằng không có tính bắc cầu

Một quan hệ bằng nhau theo nghĩa thông thường cần tính bắc cầu: nếu A bằng B và B bằng C thì A phải bằng C. Gần bằng theo tolerance **không có** tính chất này.

Với `absTol = 1.0` và `relTol = 0`:

```text
a = 0.00
b = 0.75
c = 1.50

|a - b| = 0.75 <= 1.0 => gần bằng
|b - c| = 0.75 <= 1.0 => gần bằng
|a - c| = 1.50 >  1.0 => KHÔNG gần bằng
```

Do đó `nearlyEqual(a,b) == true` và `nearlyEqual(b,c) == true` không suy ra `nearlyEqual(a,c) == true`. Hàm trong bài cũng **không phản xạ đối với NaN**, vì chính sách cố ý coi mọi so sánh có NaN là false.

**Hệ quả:** không dùng so sánh gần bằng làm cơ sở triển khai `Object.equals()`/`hashCode()` cho đối tượng đưa vào `HashSet` hoặc làm khóa `HashMap`. Cũng không tự dùng nó để trả `0` trong `Comparator` của `TreeSet`/`TreeMap` hoặc thuật toán sắp xếp: comparator cần hợp đồng thứ tự nhất quán; không bắc cầu có thể gây lỗi hoặc hành vi khó đoán.

Nếu cần **gom nhóm** những số theo độ phân giải cố định, hãy chọn quy tắc phân vùng/quantization có định nghĩa ranh giới rõ ràng, hoặc xây thuật toán clustering với hợp đồng riêng. Những kỹ thuật đó có ngữ nghĩa khác và có thể gây khác biệt tại ranh giới nhóm.

### Ngưỡng phụ thuộc giá trị so sánh

Với công thức `max(absTol, relTol × max(abs(a), abs(b)))`, một giá trị `b` rất lớn có thể làm relative tolerance lớn. Chẳng hạn `relTol = 0.5` cho phép sai lệch tới 50% độ lớn tham chiếu; về mặt số học hợp đồng vẫn nhất quán, nhưng phần lớn ứng dụng sẽ coi ngưỡng như vậy là quá rộng.

Giới hạn kỹ thuật `relTol < 1` ngăn cấu hình bằng hoặc vượt 100%, **không chứng minh** dung sai nhỏ phù hợp. Cần giới hạn riêng theo miền nghiệp vụ: ví dụ tối đa `1e-6` nếu hợp đồng yêu cầu.

### Không có “một epsilon cho mọi double”

`Double.MIN_VALUE` không phải epsilon thích hợp cho mọi phép tính; khoảng cách giữa các giá trị biểu diễn được tăng theo độ lớn. `Math.ulp(1.0)` phản ánh bước gần 1, còn `Math.ulp(1e12)` lớn hơn nhiều. Một ngưỡng `1e-9` có thể quá nghiêm ngặt ở miền giá trị lớn nhưng lại quá dễ dãi ở miền rất nhỏ. Chính sách thực tế phải xét đơn vị, số phép toán, độ nhạy của thuật toán và mức sai khác có thể chấp nhận.

### Precision là khái niệm khác với tolerance

`double` có precision nhị phân hữu hạn (53 bit có nghĩa đối với số chuẩn hóa); đây là giới hạn biểu diễn. `absTol` và `relTol` là **chính sách chấp nhận sai khác** khi so sánh kết quả. Chúng có liên quan khi phân tích sai số nhưng không phải một khái niệm.

Ví dụ một phép toán kém điều kiện có thể khuếch đại sai số đầu vào lớn hơn nhiều ULP. Dung sai cho kết quả phải dựa trên sai số hợp lệ của **toàn thuật toán**, chứ không đơn giản lấy cố định `Math.ulp(1.0)` ở mọi nơi.

## 9. Khi nào cần BigDecimal hoặc so sánh chính xác?

Với **tiền, tỷ lệ được quy định bằng số chữ số thập phân, hoặc quy tắc làm tròn pháp lý/nghiệp vụ**, so sánh `double` gần bằng có thể che giấu sự khác biệt cần được phát hiện. Nếu dữ liệu là thập phân chính xác, nên cân nhắc `BigDecimal` dựng từ chuỗi:

```java
import java.math.BigDecimal;

BigDecimal a = new BigDecimal("1.00");
BigDecimal b = new BigDecimal("1.0");

// So sánh giá trị số học; không coi scale là điều kiện bằng:
boolean sameNumericValue = a.compareTo(b) == 0; // true

// Nếu quy định độ lệch thập phân tối đa là 0.01:
BigDecimal tolerance = new BigDecimal("0.01");
boolean withinAbsoluteTolerance =
        a.subtract(b).abs().compareTo(tolerance) <= 0; // true
```

Đây là **đoạn mã minh họa dùng trong phương thức hoặc lớp**; nó không có `main()` độc lập. `BigDecimal.compareTo()` so sánh giá trị số học, còn `BigDecimal.equals()` còn xét scale; ví dụ `"1.00"` và `"1.0"` có giá trị bằng nhau nhưng `equals()` trả false.

Khi áp dụng dung sai với `BigDecimal`, cần đặt giới hạn giá trị/tài nguyên, xác định scale/precision, quy tắc làm tròn nếu các phép tính không hữu hạn trong biểu diễn thập phân, và trường hợp làm tròn trước hay sau khi so sánh. Không nên âm thầm ép `BigDecimal` về `double` chỉ để sử dụng hàm `nearlyEqual` khi yêu cầu đòi độ chính xác thập phân.

Nếu so sánh **số nguyên** (`int`, `long`, `BigInteger`), giá trị đã biểu diễn chính xác theo miền kiểu; thường so sánh bằng chính xác phù hợp hơn. Nếu muốn cho phép lệch N đơn vị nguyên, hãy định nghĩa phạm vi rõ ràng và chú ý overflow khi tính hiệu, không tự chuyển sang `double` với các số nguyên lớn hơn \(2^{53}\).

## 10. Các sai lầm triển khai phổ biến

### Chỉ viết Math.abs(a - b) < 1e-9

Cách này không nói rõ miền đơn vị, loại trừ trường hợp **bằng đúng ngưỡng** vì dùng `<`, không có tương đối theo độ lớn và không mô tả NaN/vô cực. Nó có thể đủ cho một phép kiểm tra nhỏ đã giới hạn thang đo, nhưng không phải API dùng chung có hợp đồng đầy đủ.

### Dùng relTol * a thay vì một thang đo đối xứng

Nếu dùng riêng `a` làm mốc, đảo tham số có thể đổi kết quả. Có bài toán chủ ý phân biệt **giá trị mong đợi** và **giá trị đo được**; khi đó dùng sai số tương đối theo expected là hợp lệ nếu hợp đồng nêu rõ vai trò từng đối số. Nhưng nếu tên hàm hàm ý so sánh đối xứng hai số, dùng `max(abs(a), abs(b))` giúp tính chất đó rõ ràng.

### Dùng Infinity làm tolerance để “bật tắt” kiểm tra

Nếu `absTol = Infinity`, các trường hợp xa nhau có thể được coi gần bằng vô điều kiện và gây kết quả bất ngờ với special values. Hàm bài này **từ chối tolerance vô hạn và NaN** để tránh cấu hình che giấu lỗi. Muốn bỏ một loại điều kiện, dùng giá trị `0` cho loại đó và chọn rõ loại còn lại theo nghiệp vụ.

### Quên quy tắc NaN và signed zero

`Double.NaN == Double.NaN` là false, nhưng `Double.compare(Double.NaN, Double.NaN) == 0` có ngữ nghĩa khác. `+0.0 == -0.0` là true, dù `Double.doubleToRawLongBits` cho hai mẫu bit khác nhau. Hàm gần bằng nên quy định **ngữ nghĩa số học** hay **ngữ nghĩa bit** từ đầu.

### Không chặn hiệu số tràn

Hai số hữu hạn có thể cho hiệu vô cực. Nếu viết một công thức tương đối có phép chia trên hiệu, hoặc dùng một tolerance vô hạn, lỗi có thể khó nhận thấy. Trong hàm bài này, `difference == Infinity` tự cho kết quả false vì `allowed` hữu hạn, và điều đó có lý do từ hợp đồng `relTol < 1`.

### Kiểm tra trung bình hai số để lấy scale

Viết `scale = Math.abs((a+b)/2)` có thể bị overflow ở `a+b`, hoặc gần 0 khi hai giá trị lớn nhưng trái dấu, khiến ngưỡng trở nên sai lệch. `max(abs(a),abs(b))` tránh phép cộng trung gian và giữ tính đối xứng theo định nghĩa đã chọn.

### Làm tròn chuỗi rồi so sánh

Định dạng `String.format("%.2f", value)` chỉ kiểm soát **hiển thị**, có thể làm hai số khác nhau cùng trông giống nhau hoặc biến đổi theo locale. So sánh chuỗi đã định dạng không thể thay thế hợp đồng số học về tolerance; nếu nghiệp vụ yêu cầu làm tròn về 2 chữ số thập phân, phải định nghĩa đúng phép làm tròn và thời điểm thực hiện trước khi so sánh.

### Áp dụng cho dữ liệu đã mất precision mà không biết

Một số `long` lớn có thể mất chữ số khi chuyển thành `double`. Lúc đó `nearlyEqual` đang so sánh **hai giá trị double sau chuyển đổi**, không khôi phục dữ liệu nguyên gốc. Tương tự, số tiền đi qua `double` có thể đã mất thông tin thập phân chính xác. Hãy xác nhận nguồn dữ liệu trước khi chọn cách so sánh.

## 11. Bài tập áp dụng và tiêu chí nghiệm thu

Giả sử một bộ kiểm thử số học phải chấp nhận sai lệch tối đa `0.001` theo đơn vị của kết quả, hoặc `1e-8` theo độ lớn của kết quả, tùy điều kiện nào cho ngưỡng lớn hơn. Theo hợp đồng đã chọn, lời gọi là:

```java
boolean accepted = nearlyEqual(actual, expected, 0.001, 1e-8);
```

Để sử dụng trong ứng dụng, trước tiên phải làm rõ ý nghĩa của `actual` và `expected`, đơn vị đo, cách lấy giá trị mong đợi và liệu ngưỡng **bằng đúng ranh giới** có chấp nhận hay không. Hàm đang định nghĩa so sánh **đối xứng** (đổi `actual`/`expected` không đổi kết quả). Nếu nghiệm thu yêu cầu “sai lệch không quá X% của giá trị expected”, hãy xây một hàm khác theo đúng **mẫu số expected** và xử lý expected bằng 0, thay vì bí mật đổi công thức của hàm này.

Kiểm thử nghiệm thu nên có các nhóm:

1. Một kết quả đúng chính xác; một kết quả lệch nhỏ nhưng trong ngưỡng; một kết quả vượt ngưỡng.
2. Giá trị gần 0 để absolute tolerance có vai trò rõ ràng.
3. Giá trị rất lớn để relative tolerance chi phối.
4. Số âm, hai số trái dấu, `+0.0` và `-0.0`.
5. `NaN`, hai vô cực cùng dấu, vô cực khác dấu, một hữu hạn và một vô cực.
6. Dung sai không hợp lệ: âm, `NaN`, vô cực, `relTol = 1`.
7. Các số liền kề bằng `Math.nextUp`/`nextDown` và số subnormal sát 0.
8. Ví dụ mất tính bắc cầu để người dùng API hiểu rằng gần bằng **không phải quan hệ tương đương**.

### Câu hỏi tự kiểm tra

**1.** Vì sao `absTol = 0` và `relTol = 1e-9` chưa đủ cho một phép thử “gần bằng 0” có sai số đo tuyệt đối nhỏ? Vì ngưỡng tương đối `relTol × max(|a|,|b|)` tiến về 0 khi hai giá trị cùng tiến về 0; cần một `absTol` dựa trên độ phân giải thực tế.

**2.** `nearlyEqual(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 0, 0)` trả gì? **`true`**, vì hai giá trị bằng chính xác theo chính sách, được xử lý trước phép trừ có thể tạo NaN.

**3.** Tại sao không dùng `nearlyEqual` trong `hashCode`/`equals` của khóa map? Vì quan hệ gần bằng theo ngưỡng **không bắc cầu**, không đáp ứng hợp đồng tương đương cần cho equality collections.

**4.** `nearlyEqual(0.0, Double.MIN_VALUE, 0, 0)` trả gì? **`false`**, vì một số là 0 còn số kia là subnormal dương khác 0. Nếu đặt `absTol = Double.MIN_VALUE` thì là **`true`**.

**5.** Nếu tính `a - b` từ hai số double hữu hạn mà kết quả thành vô cực, có chắc nên coi chúng gần bằng không? **Không**. Hợp đồng của bài từ chối cả hai số khác nhau khi sai lệch vượt ngưỡng hữu hạn; `difference <= allowed` sẽ false.

**Ghi nhớ:** Một hàm gần bằng tốt phải có **công thức, ý nghĩa tolerance, xử lý special values, quy tắc biên, giới hạn đầu vào và phép kiểm thử** được công bố. Hợp đồng bài này chọn \(|a-b| \le \max(\mathrm{absTol},\mathrm{relTol}\cdot\max(|a|,|b|))\) cho hai số hữu hạn, NaN luôn false, cùng vô cực true, signed zero true, tolerance bất hợp lệ ném lỗi. Hãy chọn các ngưỡng bằng yêu cầu thực tế và không dùng gần bằng thay thế bằng nhau chính xác trong cấu trúc dữ liệu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-practice-006">Tính giai thừa hoặc dãy số lớn bằng BigInteger</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành: tính giai thừa và dãy số lớn bằng BigInteger trong Java

## 1. Mục tiêu và bài toán

Các kiểu nguyên nguyên thủy của Java có giới hạn cố định: `int` dùng 32 bit có dấu, `long` dùng 64 bit có dấu. Khi tính các dãy tăng nhanh như **giai thừa** hoặc **Fibonacci**, kết quả sớm vượt những giới hạn đó. Nếu phép tính vẫn chạy trên `int` hoặc `long`, Java thường **tràn số (overflow) và cho kết quả sai**, chứ không tự nâng sang kiểu có nhiều bit hơn.

Bài thực hành sử dụng `java.math.BigInteger` để giải hai bài toán:

1. **Giai thừa**: tính `n! = 1 × 2 × ... × n`, với `0! = 1` và `n` nguyên không âm.
2. **Fibonacci**: tính `F(0) = 0`, `F(1) = 1` và `F(n) = F(n-1) + F(n-2)` khi `n ≥ 2`.

Kết thúc bài, bạn sẽ biết viết chương trình Java 17+ chạy được, sử dụng đúng API `BigInteger`, so sánh hai cách tính Fibonacci, xác thực đầu vào, kiểm tra kết quả, và hiểu vì sao “không tràn kiểu số” **không đồng nghĩa “không tốn tài nguyên”**.

## 2. Vì sao không dùng int hoặc long cho kết quả lớn?

### 2.1. Giai thừa vượt long rất sớm

Một số mốc:

| n | n! | Đánh giá |
|---|---|---|
| 0 | 1 | Định nghĩa giai thừa |
| 1 | 1 | Cơ sở |
| 5 | 120 | Vừa `int` |
| 12 | 479001600 | Còn vừa `int` |
| 13 | 6227020800 | **Vượt int** |
| 20 | 2432902008176640000 | Còn vừa `long` |
| 21 | 51090942171709440000 | **Vượt long** |
| 100 | Có 158 chữ số thập phân | Cần số nguyên tùy ý độ dài |

`Long.MAX_VALUE = 9223372036854775807`, nhỏ hơn `21!`. Bản thân việc gán kết quả đã overflow cho `BigInteger` sau đó **không sửa lại giá trị đúng**:

~~~java
long wrong = 1L;
for (int i = 2; i <= 21; i++) {
    wrong *= i; // long đã overflow trước khi có BigInteger nào tham gia
}
BigInteger tooLate = BigInteger.valueOf(wrong); // giữ giá trị sai
~~~

Đây là đoạn minh họa đặt trong một phương thức, có import `java.math.BigInteger`. Nếu cần chính xác, phải dùng phép nhân của `BigInteger` **ngay từ bước đầu**, không đợi tới khi có số lớn.

### 2.2. Fibonacci cũng vượt long

Dãy bắt đầu:

~~~text
n:     0  1  2  3  4  5  6  7  8  9  10
F(n):  0  1  1  2  3  5  8  13 21 34 55
~~~

Các mốc quan trọng:

- `F(50) = 12586269025`: vượt `int` nhưng còn vừa `long`.
- `F(92) = 7540113804746346429`: còn vừa `long`.
- `F(93) = 12200160415121876738`: vượt `Long.MAX_VALUE`.
- `F(100) = 354224848179261915075`: `BigInteger` biểu diễn được chính xác.

Không phải mọi bài toán cần BigInteger: nếu phạm vi n cố định rất nhỏ, `long` có thể đủ và thường nhẹ hơn. Khi phạm vi có thể lớn hoặc không thể chứng minh vừa kiểu cố định, `BigInteger` là lựa chọn phù hợp để giữ **số nguyên chính xác**.

## 3. Làm quen với API BigInteger

Import:

~~~java
import java.math.BigInteger;
~~~

Cách tạo:

~~~java
BigInteger zero = BigInteger.ZERO;
BigInteger one = BigInteger.ONE;
BigInteger two = BigInteger.TWO; // Có trong Java 17
BigInteger small = BigInteger.valueOf(123L);
BigInteger large = new BigInteger("51090942171709440000");
~~~

Vì kết quả có thể lớn hơn `long`, chuỗi thập phân là cách nhập một hằng số lớn chính xác. Không viết `BigInteger.valueOf(51090942171709440000L)`: literal kiểu `long` đã vượt giới hạn và code không biên dịch.

`BigInteger` không hỗ trợ các toán tử số học thông thường kiểu `+`, `-`, `*` hoặc `/` dành cho primitive. Dùng các phương thức của nó:

| Phép toán | BigInteger |
|---|---|
| `a + b` | `a.add(b)` |
| `a - b` | `a.subtract(b)` |
| `a * b` | `a.multiply(b)` |
| `a / b` (thương nguyên) | `a.divide(b)` |
| `a % b` | `a.remainder(b)` |
| `a ^ n` theo nghĩa lũy thừa | `a.pow(n)` |
| So sánh lớn hơn | `a.compareTo(b) > 0` |
| Bằng nhau theo giá trị BigInteger | `a.equals(b)` |

BigInteger **bất biến (immutable)**: lời gọi `result.multiply(factor)` không tự thay đổi `result`. Cần lưu giá trị trả về:

~~~java
BigInteger result = BigInteger.ONE;
result.multiply(BigInteger.valueOf(5)); // Kết quả bị bỏ đi!
System.out.println(result);             // 1

result = result.multiply(BigInteger.valueOf(5));
System.out.println(result);             // 5
~~~

Đây là lý do trong vòng lặp ta luôn viết `result = result.multiply(...)` và `next = previous.add(current)`.

**Lưu ý:** `BigInteger` bảo đảm phép toán số nguyên chính xác trong phạm vi tài nguyên và giới hạn triển khai, nhưng không tạo bộ nhớ vô hạn. Các số lớn vẫn làm tăng chi phí CPU, vùng nhớ và lượng dữ liệu khi in/ghi.

## 4. Giải bài toán 1: giai thừa bằng vòng lặp

Với `n ≥ 0`:

\[
n! = \prod_{k=1}^{n} k,\qquad 0! = 1.
\]

Ý tưởng:

- Bắt đầu `result = BigInteger.ONE`, vì **phần tử trung hòa của phép nhân là 1**.
- Duyệt `k` từ 2 đến `n`.
- Mỗi bước tính `result = result.multiply(BigInteger.valueOf(k))`.
- Trả lại `result`. Nếu `n = 0` hoặc `n = 1`, vòng lặp không chạy và kết quả vẫn là 1.

Phiên bản method:

~~~java
static BigInteger factorial(int n) {
    if (n < 0) {
        throw new IllegalArgumentException("n must be >= 0");
    }
    BigInteger result = BigInteger.ONE;
    for (long k = 2; k <= n; k++) {
        result = result.multiply(BigInteger.valueOf(k));
    }
    return result;
}
~~~

Đây là **phương thức để đặt trong một class** có import `java.math.BigInteger`, không phải chương trình standalone. Dùng biến đếm `long k` để tránh trường hợp vòng lặp `int` tự overflow khi n ở cực trị `Integer.MAX_VALUE`; điều đó không có nghĩa là tính `Integer.MAX_VALUE!` khả thi trong bộ nhớ/thời gian thực tế. Ở ứng dụng thật, vẫn phải đặt giới hạn đầu vào.

**Bất biến vòng lặp:** sau khi xử lý xong số `k`, `result == k!`. Trước vòng đầu, `result = 1`, đúng cho `1!`. Nếu `result` đang bằng `(k-1)!` thì nhân với `k` cho `k!`. Do đó kết quả sau cùng là `n!`.

Có thể viết đệ quy:

~~~java
static BigInteger factorialRecursive(int n) {
    if (n < 0) {
        throw new IllegalArgumentException("n must be >= 0");
    }
    if (n <= 1) {
        return BigInteger.ONE;
    }
    return BigInteger.valueOf(n)
            .multiply(factorialRecursive(n - 1));
}
~~~

Đây cũng là một method trong class (có import BigInteger). Bản đệ quy phản ánh định nghĩa toán học, nhưng dùng **stack O(n)** và có thể `StackOverflowError` khi n lớn. Bản vòng lặp là lựa chọn mặc định dễ hiểu và vững hơn cho bài tập tính n! lớn. Đừng chọn đệ quy chỉ vì công thức n! thường được viết đệ quy.

## 5. Giải bài toán 2: Fibonacci bằng hai biến BigInteger

Một cách viết Fibonacci đệ quy ngây thơ:

~~~java
// CHỈ minh họa công thức; không phù hợp cho n lớn.
static BigInteger fibonacciSlow(int n) {
    if (n < 0) {
        throw new IllegalArgumentException("n must be >= 0");
    }
    if (n <= 1) {
        return BigInteger.valueOf(n);
    }
    return fibonacciSlow(n - 1).add(fibonacciSlow(n - 2));
}
~~~

Đoạn code có import BigInteger, nhưng **tính lại cùng giá trị rất nhiều lần**. Ví dụ khi tính `F(40)`, cả `F(38)` và nhiều giá trị nhỏ hơn xuất hiện lặp lại trong cây gọi. Số lời gọi tăng theo cấp số nhân; tăng bit-width bằng BigInteger **không sửa được thuật toán lãng phí đó**.

Giải pháp vòng lặp chỉ giữ hai giá trị gần nhất:

~~~java
static BigInteger fibonacci(int n) {
    if (n < 0) {
        throw new IllegalArgumentException("n must be >= 0");
    }
    BigInteger previous = BigInteger.ZERO; // F(0)
    BigInteger current = BigInteger.ONE;   // F(1)

    for (long k = 0; k < n; k++) {
        BigInteger next = previous.add(current);
        previous = current;
        current = next;
    }
    return previous;
}
~~~

Cách hiểu:

| n cần tính | Số vòng | `previous` khi trả về |
|---|---:|---|
| 0 | 0 | 0 |
| 1 | 1 | 1 |
| 2 | 2 | 1 |
| 3 | 3 | 2 |
| 10 | 10 | 55 |

**Bất biến:** tại đầu vòng lặp thứ k (đếm từ 0), `previous = F(k)` và `current = F(k+1)`. Gán `next = previous.add(current)` tạo `F(k+2)`, rồi dịch cặp thành `F(k+1), F(k+2)`. Vì thế sau n lần lặp, `previous = F(n)`.

Lưu ý thứ tự gán: nếu viết `previous = current; current = previous.add(current);` thì sau khi thay previous, bạn đã mất giá trị cũ cần dùng; công thức dãy sẽ sai. Biến `next` giúp tránh lỗi cập nhật trạng thái theo sai thứ tự.

Phương pháp này có **n bước cộng**, dùng một số lượng nhỏ tham chiếu BigInteger trong code; nó không lưu toàn bộ dãy trong bộ nhớ. Nếu cần **in cả dãy**, số dữ liệu đầu ra sẽ lớn hơn rất nhiều so với chỉ tìm một số hạng.

## 6. Chương trình hoàn chỉnh: factorial, Fibonacci và đối chiếu fast doubling

Phương pháp Fibonacci vòng lặp phù hợp để học nguyên lý. Nếu chỉ cần `F(n)` với n lớn, có một kỹ thuật ít bước hơn gọi là **fast doubling**:

\[
\begin{aligned}
F(2k) &= F(k)\,[2F(k+1)-F(k)],\\
F(2k+1) &= F(k)^2 + F(k+1)^2.
\end{aligned}
\]

Một hàm có thể trả đồng thời cặp `(F(n), F(n+1))`. Chia n làm đôi mỗi lần nên số **tầng đệ quy O(log n)**; tuy nhiên các phép nhân làm việc với BigInteger ngày càng dài, nên **không được suy ra tổng thời gian tính chỉ O(log n) theo bit**.

Lưu toàn bộ code sau vào **`BigIntegerSequenceDemo.java`**:

~~~java
import java.math.BigInteger;

public final class BigIntegerSequenceDemo {
    private BigIntegerSequenceDemo() {}

    private static void validate(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n must be >= 0");
        }
    }

    public static BigInteger factorial(int n) {
        validate(n);
        BigInteger result = BigInteger.ONE;
        for (long k = 2; k <= n; k++) {
            result = result.multiply(BigInteger.valueOf(k));
        }
        return result;
    }

    public static BigInteger fibonacci(int n) {
        validate(n);
        BigInteger previous = BigInteger.ZERO;
        BigInteger current = BigInteger.ONE;
        for (long k = 0; k < n; k++) {
            BigInteger next = previous.add(current);
            previous = current;
            current = next;
        }
        return previous;
    }

    public static BigInteger fibonacciFast(int n) {
        validate(n);
        return fibPair(n)[0];
    }

    private static BigInteger[] fibPair(int n) {
        if (n == 0) {
            return new BigInteger[] {BigInteger.ZERO, BigInteger.ONE};
        }
        BigInteger[] half = fibPair(n / 2);
        BigInteger a = half[0];
        BigInteger b = half[1];
        BigInteger c = a.multiply(b.shiftLeft(1).subtract(a));
        BigInteger d = a.multiply(a).add(b.multiply(b));
        return (n % 2 == 0)
                ? new BigInteger[] {c, d}
                : new BigInteger[] {d, c.add(d)};
    }

    public static void main(String[] args) {
        System.out.println("0! = " + factorial(0));
        System.out.println("1! = " + factorial(1));
        System.out.println("5! = " + factorial(5));
        System.out.println("20! = " + factorial(20));
        System.out.println("21! = " + factorial(21));
        System.out.println("100! decimal digits = "
                + factorial(100).toString().length());

        System.out.println("F(0) = " + fibonacci(0));
        System.out.println("F(1) = " + fibonacci(1));
        System.out.println("F(10) = " + fibonacci(10));
        System.out.println("F(50) = " + fibonacci(50));
        System.out.println("F(100) = " + fibonacci(100));
        System.out.println("fast F(100) matches = "
                + fibonacci(100).equals(fibonacciFast(100)));

        boolean allMatch = true;
        for (int n = 0; n <= 100; n++) {
            if (!fibonacci(n).equals(fibonacciFast(n))) {
                allMatch = false;
                break;
            }
        }
        System.out.println("cross-check F(0..100) = " + allMatch);

        boolean negativeRejected = false;
        try {
            factorial(-1);
        } catch (IllegalArgumentException expected) {
            negativeRejected = true;
        }
        System.out.println(
                "negative factorial rejected = " + negativeRejected);
    }
}
~~~

Chạy trên Java 17+:

~~~bash
javac BigIntegerSequenceDemo.java
java BigIntegerSequenceDemo
~~~

**Kết quả stdout đã kiểm chứng bằng chạy chương trình:**

~~~text
0! = 1
1! = 1
5! = 120
20! = 2432902008176640000
21! = 51090942171709440000
100! decimal digits = 158
F(0) = 0
F(1) = 1
F(10) = 55
F(50) = 12586269025
F(100) = 354224848179261915075
fast F(100) matches = true
cross-check F(0..100) = true
negative factorial rejected = true
~~~

### Giải thích công thức fast doubling trong code

Giả sử lời gọi đệ quy trả `a = F(k)` và `b = F(k+1)`, trong đó `k = n / 2`. Ta tính:

- `c = a × (2b - a) = F(2k)`. Với BigInteger, `b.shiftLeft(1)` tương đương nhân b với 2.
- `d = a² + b² = F(2k+1)`.
- Nếu n chẵn (`n = 2k`), cặp cần trả là `(c, d)`.
- Nếu n lẻ (`n = 2k+1`), cặp cần trả là `(d, c+d)`.

Khi `n = 0`, trả `(F(0), F(1)) = (0, 1)`. Mỗi lần gọi giảm n còn `n/2`, nên độ sâu đệ quy tăng theo log n thay vì n. Thuật toán không yêu cầu memoize toàn bộ dãy và tránh chi phí cây đệ quy cấp số nhân của `fibonacciSlow`.

**Không nhầm tính đúng với độ nhanh tuyệt đối:** với n nhỏ, vòng lặp dễ đọc và có thể chạy nhanh hơn do không phải nhân BigInteger. Fast doubling thường đáng cân nhắc khi chỉ muốn số hạng ở chỉ số lớn.

## 7. So sánh thuật toán và độ phức tạp thực tế

Cần phân biệt **số lần thao tác ở mức thuật toán** với **chi phí tính toán mỗi thao tác BigInteger**.

| Cách tính | Số bước mức cao | Bộ nhớ giữ trạng thái | Đặc điểm |
|---|---|---|---|
| Giai thừa vòng lặp | n−1 phép nhân khi n≥2 | Ít biến BigInteger; kết quả lớn dần | Dễ hiểu, tránh sâu stack |
| Giai thừa đệ quy | n mức lời gọi | Stack O(n) | Không phù hợp n rất lớn |
| Fibonacci đệ quy ngây thơ | Tăng theo cấp số nhân | Stack O(n), tính lặp | Chỉ để dạy công thức, không dùng n lớn |
| Fibonacci vòng lặp | n phép cộng | Hai giá trị trạng thái chính | Đơn giản, không lưu toàn dãy |
| Fibonacci fast doubling | O(log n) tầng, mỗi tầng một số phép nhân/cộng BigInteger | Stack O(log n) và kết quả trung gian | Hiệu quả hơn về số bước cho chỉ số lớn |

BigInteger không phải phép cộng/nhân O(1) khi toán hạng có độ dài tăng. Với Fibonacci, độ dài bit của `F(n)` là Θ(n) khi n lớn; vì thế một phép cộng ở bước muộn tốn nhiều hơn ở bước đầu. Theo mô hình cộng số nhiều limb tuyến tính, n lần cộng trên các số tăng dần có tổng công việc xấp xỉ **O(n²) bit operations**; bảng “n phép cộng” không phải cam kết O(n) thời gian máy.

Với giai thừa, `n!` có độ dài bit Θ(n log n); mỗi lần nhân thêm một số có độ dài khoảng log n bit, còn tích trung gian lớn dần. Tổng thời gian tùy thuật toán nhân, phân bổ bộ nhớ và triển khai JDK; không nên mô tả mọi lời gọi `multiply` như một phép toán tốn cùng chi phí.

Bộ nhớ BigInteger cũng tăng theo kích thước **kết quả và các kết quả tạm**, dù chương trình chỉ giữ vài biến. Ví dụ `F(100000)` có hàng chục nghìn chữ số thập phân; nếu in toàn bộ mỗi bước, thời gian I/O và tạo chuỗi có thể chiếm ưu thế so với tính toán.

## 8. Đầu vào không hợp lệ, tài nguyên và giới hạn ứng dụng

Về định nghĩa giai thừa cơ bản, `n` phải là số nguyên không âm. Fibonacci trong bài này được định nghĩa cho chỉ số không âm. Do đó API minh họa từ chối `n < 0` bằng `IllegalArgumentException` thay vì lặng lẽ trả 1 hoặc 0.

Nhưng `n >= 0` **chưa đủ để một dịch vụ sản xuất an toàn**. Người dùng có thể gửi `n = 1_000_000_000`: kết quả giai thừa vô cùng lớn trong phạm vi thực tế, có thể tiêu hao bộ nhớ, CPU hoặc thời gian phản hồi. Một dịch vụ cần quy định rõ **giới hạn dựa trên quota và mục đích**:

~~~java
static final int MAX_FACTORIAL_N = 5_000;

static BigInteger factorialForApi(int n) {
    if (n < 0 || n > MAX_FACTORIAL_N) {
        throw new IllegalArgumentException("n out of allowed range");
    }
    return BigIntegerSequenceDemo.factorial(n);
}
~~~

Đoạn trên là **method minh họa của một class khác** có import BigInteger và quyền gọi `BigIntegerSequenceDemo` từ ví dụ hoàn chỉnh. Giá trị 5.000 chỉ là **chính sách ví dụ**, không phải giới hạn cố định của BigInteger. Muốn đặt quota thật, hãy đo và xác nhận trên môi trường chạy cụ thể.

Các giới hạn có thể cần xét đồng thời:

- Chỉ số n đầu vào, nguồn gốc và kiểu dữ liệu (không parse số tùy ý mà không giới hạn độ dài request).
- Số bit hoặc chữ số kết quả ước lượng trước khi bắt đầu.
- Thời gian CPU, heap/memory, thời gian chờ và số yêu cầu chạy đồng thời.
- Chi phí serialize và truyền dữ liệu qua mạng nếu API trả toàn bộ số nguyên khổng lồ.
- Việc lưu cache kết quả: cache nhiều BigInteger lớn có thể tiết kiệm CPU nhưng tốn heap đáng kể.

**Chỉ dùng `BigInteger` không làm thuật toán an toàn trước yêu cầu từ chối dịch vụ do input quá lớn.** BigInteger vẫn có giới hạn của JVM/array và tài nguyên máy.

## 9. Không dùng double làm lối tắt cho kết quả nguyên chính xác

Có thể thấy `double` có miền số mũ rất rộng, nhưng độ chính xác hữu hạn nên không biểu diễn chính xác mọi số nguyên lớn. Chuyển số nguyên lớn sang double hoặc tính gần đúng giai thừa bằng double có thể đánh mất chữ số.

~~~java
BigInteger exact = BigIntegerSequenceDemo.factorial(21);
double approximate = exact.doubleValue(); // Có thể không giữ đủ chữ số
~~~

Đoạn này nằm trong phương thức, có import BigInteger và phụ thuộc class ở phần 6. Khi mục tiêu là **số nguyên chính xác tuyệt đối**, không lấy `double` làm nguồn chân lý hay so sánh kết quả BigInteger bằng cách chuyển đôi bên sang double.

Ngay cả `BigDecimal` cũng không thay thế tự động cho BigInteger: BigDecimal phục vụ số thập phân tùy ý scale/precision; BigInteger đơn giản và trực tiếp hơn cho các dãy số nguyên rời rạc như giai thừa và Fibonacci. Chọn kiểu theo bài toán, không chỉ theo “số có thể lớn”.

## 10. Kiểm tra tính đúng bằng test bất biến

Một bộ test có ý nghĩa hơn kiểm tra một kết quả lớn được chép tay:

**Giai thừa:**

- `factorial(0) = 1` và `factorial(1) = 1`.
- `factorial(5) = 120`, `factorial(20)` khớp giá trị đã biết.
- Với `n ≥ 1`, `factorial(n) = factorial(n-1) × n`.
- Với `n < 0`, nhận `IllegalArgumentException`.
- `factorial(21)` lớn hơn `Long.MAX_VALUE`, không được quy về `long`.

**Fibonacci:**

- `F(0) = 0`, `F(1) = 1`.
- Với `n ≥ 2`, `F(n) = F(n-1) + F(n-2)`.
- Với n từ 0 đến 100, bản vòng lặp và bản fast doubling trả kết quả bằng nhau; ví dụ ở phần 6 đã làm việc này.
- `F(93)` vượt `Long.MAX_VALUE`; mọi lần cộng vẫn thực hiện bằng `BigInteger.add`.
- Với `n < 0`, API từ chối theo contract của bài.

Ví dụ các kiểm tra tự chạy được sau khi có class hoàn chỉnh, đặt trong thân method:

~~~java
if (!BigIntegerSequenceDemo.factorial(6)
        .equals(BigInteger.valueOf(720))) {
    throw new AssertionError("6! must equal 720");
}

if (!BigIntegerSequenceDemo.fibonacci(20)
        .equals(BigInteger.valueOf(6765))) {
    throw new AssertionError("F(20) must equal 6765");
}
~~~

Đây là code minh họa (có import BigInteger). Có thể chuyển những kiểm tra này sang JUnit; bài này không yêu cầu dependency ngoài Java 17 để chạy ví dụ chính.

### Kiểm thử thay vì chỉ in kết quả

- `System.out.println` giúp quan sát, nhưng tự động hóa nên dùng so sánh `equals` hoặc `compareTo` và ném lỗi nếu sai.
- Với BigInteger, **không dùng `==`** để so sánh giá trị số vì nó so sánh tham chiếu đối tượng.
- Khi test một hàm tối ưu (fast doubling), hãy đối chiếu với bản đơn giản đáng tin ở phạm vi nhỏ; với phạm vi lớn, kiểm tra công thức hoặc mốc đã biết và tiêu chí tài nguyên.
- Đối với ca n cực lớn, test riêng timeout, giới hạn request và xử lý lỗi thay vì chạy thật kết quả quá lớn trong bộ unit test nhanh.

## 11. Mở rộng sang một dãy số khác

Bài toán không chỉ áp dụng cho Fibonacci. Nhiều dãy có thể biểu diễn bằng recurrence:

\[
a_n = c_1a_{n-1} + c_2a_{n-2},\qquad n\ge 2.
\]

Nếu các hệ số và giá trị ban đầu là số nguyên, có thể dùng BigInteger cho mọi phép nhân/cộng để tránh overflow. Ví dụ **dãy Lucas** có `L(0) = 2`, `L(1) = 1`, `L(n) = L(n-1)+L(n-2)`:

~~~java
static BigInteger lucas(int n) {
    if (n < 0) {
        throw new IllegalArgumentException("n must be >= 0");
    }
    BigInteger previous = BigInteger.TWO;
    BigInteger current = BigInteger.ONE;
    for (long k = 0; k < n; k++) {
        BigInteger next = previous.add(current);
        previous = current;
        current = next;
    }
    return previous;
}
~~~

Đây là method (đặt trong class với import BigInteger) dùng cùng kỹ thuật dịch hai biến. Nó trả `L(0)=2`, `L(1)=1`, `L(2)=3`, `L(3)=4`, `L(4)=7`.

Khi cần toàn bộ dãy từ `a0` đến `an`, có thể lưu từng số vào list, nhưng phải tính chi phí bộ nhớ theo **tổng độ dài của tất cả các phần tử**. Nếu chỉ cần `an`, hai biến trạng thái thường phù hợp hơn. Với recurrence tuyến tính và n cực lớn, có thể nghiên cứu **lũy thừa ma trận** bằng BigInteger hoặc công thức đặc biệt; trước hết phải chứng minh công thức và xử lý hệ số âm/điều kiện đầu đúng.

## 12. Những lỗi phổ biến

| Lỗi | Điều xảy ra | Cách làm đúng |
|---|---|---|
| Dùng `int` cho `13!` | Tràn 32 bit | BigInteger ngay từ bước nhân đầu |
| Dùng `long` cho `21!` hoặc `F(93)` | Tràn 64 bit | `BigInteger.multiply/add` |
| Nhân bằng long rồi chuyển `valueOf` | Giá trị đã sai trước khi chuyển | Duy trì toàn bộ phép toán ở BigInteger |
| Viết `result.multiply(x)` mà không nhận kết quả | `result` không đổi | `result = result.multiply(x)` |
| Viết `a + b` cho hai BigInteger | Không biên dịch | `a.add(b)` |
| So sánh `a == b` để kiểm tra giá trị | So tham chiếu | `a.equals(b)` |
| Trả `0` cho `factorial(-1)` | Phá định nghĩa đầu vào | Ném lỗi rõ ràng |
| Viết Fibonacci đệ quy `F(n-1)+F(n-2)` cho n lớn | Tính trùng vô số lần | Vòng lặp hoặc fast doubling |
| Chọn đệ quy cho giai thừa cực lớn | Có thể tràn stack | Vòng lặp |
| Cho rằng có BigInteger thì vô hạn CPU/heap | Có thể treo/hết bộ nhớ | Giới hạn và kiểm tra tài nguyên |
| In mọi số khổng lồ khi benchmark | Format/I/O che chi phí thuật toán | Đo tách riêng, dùng JMH khi cần |
| Chuyển số lớn sang double để đối chiếu | Mất tính chính xác của số nguyên | So trực tiếp BigInteger |

## 13. Bài tập tự luyện

**Bài 1.** Viết hàm `factorial(30)` bằng BigInteger. Dự đoán vì sao một biến `long` không thể lưu được kết quả. Dùng `equals` để so sánh với chuỗi:

~~~text
265252859812191058636308480000000
~~~

**Bài 2.** Viết hàm trả `F(93)` theo vòng lặp, đối chiếu với:

~~~text
12200160415121876738
~~~

Vì sao giá trị này không vừa signed long?

**Bài 3.** Viết hàm tính **tổng từ F(0) đến F(n)** mà không xây một list toàn dãy. Bạn có thể dùng đẳng thức `F(0)+...+F(n)=F(n+2)-1` với n không âm, nhưng phải chứng minh trước khi sử dụng.

**Bài 4.** Phân tích khác biệt chi phí giữa “in toàn bộ F(0)..F(n)” và “chỉ trả về F(n)”, dù cả hai đều có thể chạy với BigInteger.

**Bài 5.** Sửa lỗi:

~~~java
BigInteger result = BigInteger.ONE;
for (int i = 2; i <= 30; i++) {
    result.multiply(BigInteger.valueOf(i));
}
System.out.println(result);
~~~

**Bài 6.** Viết một test gọi hai phiên bản Fibonacci cho mọi n từ 0 tới 1000, dừng khi thấy khác biệt. Giải thích vì sao test chéo thuật toán mạnh hơn chỉ test một giá trị `F(10)=55`.

### Đáp án/gợi ý

1. `30! = 265252859812191058636308480000000`, lớn hơn `Long.MAX_VALUE`. Code factorial phần 6 xử lý.
2. `F(93)` lớn hơn `9223372036854775807`; dùng `BigInteger` là phù hợp.
3. Có thể cộng dồn qua vòng lặp dùng `BigInteger.add`, hoặc dùng identity và `fibonacciFast(n+2).subtract(BigInteger.ONE)` khi chỉ số n+2 nằm trong miền int và input hợp lệ.
4. Lưu hoặc in mọi số hạng tốn dữ liệu I/O/bộ nhớ tỉ lệ tổng độ dài từng phần tử. Trả một số hạng chỉ giữ các giá trị trung gian cần thiết.
5. Gán lại `result = result.multiply(BigInteger.valueOf(i));` để không bỏ kết quả.
6. Mỗi n là một trường hợp gồm cả parity chẵn/lẻ, base case, công thức nhân và phép cộng; test chéo có thể phát hiện nhiều lỗi hơn một mốc đơn lẻ, dù không thay thế hoàn toàn chứng minh tính đúng.

## 14. Tài liệu chuẩn

- Java SE 17, `BigInteger` API (`add`, `multiply`, `pow`, `shiftLeft`, `bitLength`): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigInteger.html
- Java SE 17, `ArithmeticException`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ArithmeticException.html
- Java SE 17, `IllegalArgumentException`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/IllegalArgumentException.html

**Ghi nhớ:** BigInteger giúp giữ kết quả **số nguyên chính xác** khi giai thừa hoặc dãy tăng vượt `int`/`long`. Tính đúng đến từ **công thức, điều kiện đầu, thứ tự cập nhật và việc dùng phép toán BigInteger ngay từ đầu**; tính khả thi đến từ lựa chọn thuật toán và giới hạn tài nguyên phù hợp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-practice-007">Thiết kế phép tính thập phân với MathContext và RoundingMode</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế phép tính thập phân với MathContext và RoundingMode trong Java

Một hệ thống báo giá, tính thuế, chiết khấu hoặc chia tỷ lệ thường dùng `BigDecimal` để giữ đúng giá trị thập phân. Tuy nhiên, **dùng BigDecimal không tự động làm cho mọi kết quả và quy tắc làm tròn đều đúng nghiệp vụ**: phép chia có thể không có biểu diễn thập phân hữu hạn; phép toán có thể tạo nhiều chữ số hơn mong muốn; cùng một bộ dữ liệu có thể cho tổng khác nhau nếu làm tròn tại các bước khác nhau.

Bài thực hành này hướng dẫn thiết kế một chính sách tính toán rõ ràng qua ba thành phần:

- **Giá trị và cách biểu diễn:** `BigDecimal` giữ một số nguyên không chia tỷ lệ (`unscaledValue`) và `scale`.
- **Độ chính xác khi tính:** `MathContext` định nghĩa `precision` (số chữ số có nghĩa) và `RoundingMode` dùng khi phải bỏ chữ số.
- **Làm tròn kết quả theo đơn vị:** `setScale(...)` và một chính sách `RoundingMode` quy định số chữ số sau dấu thập phân khi chốt giá trị.

Mục tiêu không phải chọn một “rounding mode tốt nhất cho mọi hệ thống”. Mục tiêu là **tạo quy tắc cụ thể, dễ kiểm thử và nhất quán với hợp đồng nghiệp vụ**.

## 1. Mô hình số thập phân của BigDecimal

Giá trị được biểu diễn theo công thức:

~~~text
numericValue = unscaledValue × 10^(-scale)
~~~

Ví dụ:

| Chuỗi đưa vào constructor | `unscaledValue()` | `scale()` | `precision()` |
|---|---:|---:|---:|
| `"12"` | 12 | 0 | 2 |
| `"12.00"` | 1200 | 2 | 4 |
| `"0.00123"` | 123 | 5 | 3 |
| `"123.456"` | 123456 | 3 | 6 |
| `"1E+3"` | 1 | -3 | 1 |

**`scale`** đếm vị trí dấu thập phân trong biểu diễn; có thể âm. **`precision`** đếm tổng số chữ số trong phần nguyên `unscaledValue`, bỏ dấu âm và số 0 đầu không thuộc biểu diễn. Vì vậy `precision = 4` không có nghĩa “bốn chữ số sau dấu phẩy”, và `scale = 2` cũng không có nghĩa số được tính với chính xác hai chữ số có nghĩa.

`BigDecimal` là immutable: phép toán trả đối tượng kết quả; việc gọi `setScale` không thay đổi đối tượng ban đầu. Luôn gán hoặc trả về kết quả nếu muốn dùng nó trong bước sau.

Khởi tạo bằng chuỗi decimal:

~~~java
BigDecimal rate = new BigDecimal("0.075");
BigDecimal amount = new BigDecimal("123.45");
~~~

Tránh `new BigDecimal(0.1)` khi ý muốn là số thập phân chính xác 0.1, vì constructor nhận `double` lấy **giá trị nhị phân** đang được biểu diễn, có thể khác chuỗi decimal người viết kỳ vọng. Sử dụng `new BigDecimal("0.1")` hoặc `BigDecimal.valueOf(0.1)` nếu nguồn dữ liệu phù hợp.

## 2. MathContext là gì?

`java.math.MathContext` là một đối tượng bất biến chứa hai quyết định:

1. `precision` — tối đa bao nhiêu **chữ số có nghĩa** cho kết quả của một phép toán sử dụng context, nếu precision lớn hơn 0;
2. `roundingMode` — phải xử lý các chữ số bị loại như thế nào khi kết quả không thể giữ nguyên trong precision đó.

Tạo context:

~~~java
import java.math.MathContext;
import java.math.RoundingMode;

MathContext work = new MathContext(4, RoundingMode.HALF_EVEN);
~~~

`precision = 4` nói rằng kết quả làm tròn của phép toán dùng context sẽ có tối đa bốn chữ số có nghĩa. Nó **không** nói kết quả luôn có bốn chữ số sau dấu thập phân.

`MathContext` chỉ tác động khi **được truyền vào thao tác có overload tương ứng**, ví dụ `a.add(b, work)`, `a.multiply(b, work)`, `a.divide(b, work)`, `a.round(work)`. Tạo biến `MathContext work` rồi gọi `a.add(b)` không tự động áp dụng context cho phép cộng đó.

Ví dụ:

~~~java
BigDecimal a = new BigDecimal("123.456");
BigDecimal b = new BigDecimal("0.789");
MathContext work = new MathContext(4, RoundingMode.HALF_EVEN);

System.out.println(a.add(b));       // 124.245
System.out.println(a.add(b, work)); // 124.2
~~~

Kết quả chính xác `124.245` có sáu chữ số có nghĩa; với context bốn chữ số, kết quả là `124.2`. Phần `0.045` đã được làm tròn **trong phép toán đang dùng context**. Nếu lưu kết quả này rồi tính tiếp, thông tin phần bị bỏ không tự xuất hiện trở lại.

## 3. Khác biệt cốt lõi: precision và scale

| Câu hỏi | API phù hợp | Ý nghĩa |
|---|---|---|
| “Kết quả phép tính giữ tối đa 6 chữ số có nghĩa?” | `new MathContext(6, roundingMode)` | Giới hạn **precision** khi tính |
| “Kết quả cuối có đúng 2 chữ số sau dấu thập phân?” | `setScale(2, roundingMode)` | Đặt **scale** của biểu diễn cuối |
| “Phải chia 1/3 và lấy 6 chữ số có nghĩa?” | `divide(divisor, mc6)` | `0.333333` |
| “Phải chia 1/3 và chốt đến 2 chữ số thập phân?” | `divide(divisor, 2, roundingMode)` | `0.33` |
| “Phải không mất bất kỳ chữ số có nghĩa nào?” | `MathContext.UNLIMITED` hoặc không dùng context nếu phép toán có kết quả hữu hạn chính xác | Kết quả cần chính xác; phép chia vô hạn có thể ném lỗi |

Một ví dụ quan trọng về độ lớn:

~~~java
BigDecimal x = new BigDecimal("12345");
System.out.println(x.round(new MathContext(3, RoundingMode.HALF_UP)));
// 1.23E+4

System.out.println(x.setScale(2));
// 12345.00
~~~

Kết quả thứ nhất làm tròn thành **ba chữ số có nghĩa** (giá trị thay đổi từ 12345 thành 12300). Kết quả thứ hai giữ nguyên **giá trị 12345** nhưng biểu diễn với hai chữ số sau dấu thập phân. Đây là hai nhiệm vụ khác nhau.

Đừng dùng `new MathContext(2)` để biểu diễn “tiền có hai chữ số lẻ”. Và đừng mặc định `setScale(2)` đủ để kiểm soát sai số của các phép chia/trị số trung gian mà không cần xét thuật toán.

## 4. Những RoundingMode cần nắm

`java.math.RoundingMode` là enum; sử dụng các hằng enum thay vì các hằng `BigDecimal.ROUND_*` kiểu `int` đã lỗi thời.

| Mode | Quy tắc khi phải bỏ chữ số khác 0 | Cách hiểu |
|---|---|---|
| `UP` | Tăng độ lớn ra xa 0 | Dù giá trị dương hay âm |
| `DOWN` | Tiến về 0 | Cắt chữ số bị bỏ theo độ lớn |
| `CEILING` | Hướng tới +∞ | Với số âm thường ít âm hơn |
| `FLOOR` | Hướng tới -∞ | Với số âm thường âm hơn |
| `HALF_UP` | Tới gần nhất; đúng điểm giữa thì ra xa 0 | Kiểu làm tròn .5 thường được dạy |
| `HALF_DOWN` | Tới gần nhất; đúng điểm giữa thì về 0 | Chỉ khác các trường hợp đúng giữa |
| `HALF_EVEN` | Tới gần nhất; đúng điểm giữa chọn chữ số giữ lại chẵn | Giảm thiên lệch trong nhiều chuỗi làm tròn |
| `UNNECESSARY` | Không được bỏ chữ số có nghĩa | Ném `ArithmeticException` nếu cần làm tròn |

Ví dụ số dương và âm:

~~~java
BigDecimal positive = new BigDecimal("2.345");
BigDecimal negative = new BigDecimal("-2.345");

System.out.println(positive.setScale(2, RoundingMode.HALF_UP));   // 2.35
System.out.println(positive.setScale(2, RoundingMode.HALF_EVEN)); // 2.34
System.out.println(negative.setScale(2, RoundingMode.HALF_UP));   // -2.35
~~~

Ở đây chữ số giữ lại thứ hai của `2.34` là 4 (chẵn); tại đúng điểm giữa `2.345`, `HALF_EVEN` chọn `2.34`, còn `HALF_UP` chọn `2.35`. Với `-2.345`, `HALF_UP` làm tròn **ra xa 0**, được `-2.35`, không phải `-2.34`.

Với giá trị `2.355` ở scale 2, `HALF_EVEN` lại chọn `2.36` vì chữ số 6 là chẵn. `HALF_EVEN` không có nghĩa “luôn làm tròn xuống khi .5”, mà là quy tắc chọn chữ số chẵn **chỉ trong trường hợp đúng giữa hai lựa chọn**.

Không tự chọn `HALF_EVEN` chỉ vì thuật ngữ “banker's rounding”; quy tắc tính thuế, tiền, chiết khấu và báo cáo có thể quy định phương thức khác tùy hệ thống/quốc gia/hợp đồng.

## 5. Vì sao MathContext không phải cấu hình toàn cục?

Mỗi phép toán của `BigDecimal` có các overload **với hoặc không với `MathContext`**:

~~~java
BigDecimal exact = a.multiply(b);
BigDecimal rounded = a.multiply(b, work);
~~~

`exact` có kết quả phép nhân chính xác (nếu tài nguyên và biểu diễn cho phép), `rounded` có thể đã bỏ chữ số theo context. Chỉ một tham số `work` được truyền tới phép nhân; nó không gắn vĩnh viễn vào `a`, `b` hoặc vào JVM.

Khi thiết kế chuỗi phép tính, chọn nhất quán:

- **Cần tính chính xác toàn bộ trước thời điểm chốt:** ưu tiên dùng toán tử chính xác cho các phép toán có thể biểu diễn hữu hạn, chỉ dùng rounding tại ranh giới được xác định.
- **Cần giới hạn độ chính xác ở từng bước** (mô phỏng một chuẩn decimal arithmetic hoặc để kiểm soát độ phức tạp): truyền `MathContext` vào từng phép toán theo chính sách, và chấp nhận rằng intermediate rounding làm đổi giá trị.
- **Có phép chia vô hạn:** lựa chọn scale và rounding cụ thể hoặc `MathContext` với precision hữu hạn; không chờ JVM đoán policy.

Ví dụ:

~~~java
BigDecimal left = new BigDecimal("123.456");
BigDecimal right = new BigDecimal("0.789");
MathContext work = new MathContext(4, RoundingMode.HALF_EVEN);

System.out.println(left.multiply(right));       // 97.406784
System.out.println(left.multiply(right, work)); // 97.41
~~~

Khi phép nhân đã trả `97.41`, việc gọi `setScale(6)` chỉ tạo `97.410000`; nó **không** khôi phục `0.003216` đã mất trong lần rounding trước.

## 6. Phép chia: điểm cần quyết định chiến lược rõ nhất

Phép chia `1 / 3` có biểu diễn thập phân vô hạn. `BigDecimal` không mặc định âm thầm chọn số chữ số:

~~~java
BigDecimal one = BigDecimal.ONE;
BigDecimal three = new BigDecimal("3");

// one.divide(three); // ArithmeticException: decimal expansion vô hạn
~~~

Có các lựa chọn chính:

~~~java
// 6 chữ số có nghĩa:
BigDecimal withContext = one.divide(
        three, new MathContext(6, RoundingMode.HALF_EVEN)
); // 0.333333

// Cố định 2 chữ số sau dấu thập phân:
BigDecimal fixedScale = one.divide(
        three, 2, RoundingMode.HALF_UP
); // 0.33
~~~

| Overload | Quy tắc kết quả | Khi nào dùng |
|---|---|---|
| `divide(divisor)` | Phải chính xác; phép chia không hữu hạn sẽ lỗi | Giá trị quotient cần chính xác và biết biểu diễn hữu hạn |
| `divide(divisor, mathContext)` | Giới hạn số chữ số có nghĩa | Tính xấp xỉ với precision xác định |
| `divide(divisor, newScale, roundingMode)` | Đặt scale của kết quả | Chốt tỷ lệ/đơn vị ở đúng số chữ số sau dấu phẩy |
| `divide(divisor, roundingMode)` | Dùng scale của **số bị chia** để tạo kết quả | Dễ gây bất ngờ nếu scale đầu vào không được chuẩn hóa |

**Tránh overload chỉ có `RoundingMode` nếu scale số bị chia không phải một phần của chính sách.** Ví dụ:

~~~java
BigDecimal divisor = new BigDecimal("3");
System.out.println(new BigDecimal("2.0").divide(divisor, RoundingMode.HALF_UP));
// 0.7
System.out.println(new BigDecimal("2.00").divide(divisor, RoundingMode.HALF_UP));
// 0.67
~~~

Hai số bị chia cùng giá trị số 2 nhưng khác scale, làm hai phép chia dùng scale kết quả khác nhau. Vì vậy API thiết kế cho hệ thống cần xác định **tính chính xác, precision hoặc scale** bằng tham số nghiệp vụ thay vì dựa ngầm vào cách nhập chuỗi.

Phép chia cho 0 luôn không hợp lệ, kể cả khi có context; cần xử lý `ArithmeticException`/validate divisor theo hợp đồng.

## 7. MathContext.UNLIMITED và RoundingMode.UNNECESSARY

`MathContext.UNLIMITED` có `precision = 0`; các phép toán với precision 0 yêu cầu **kết quả chính xác**, và cấu hình `RoundingMode` không có vai trò làm tròn khi precision 0. Giá trị `MathContext.UNLIMITED` có rounding mode mặc định nhưng không làm cho `1/3` trở thành một số thập phân hữu hạn.

~~~java
BigDecimal one = BigDecimal.ONE;
BigDecimal three = new BigDecimal("3");

// Cả hai đều ném ArithmeticException:
one.divide(three);
one.divide(three, MathContext.UNLIMITED);
~~~

`UNNECESSARY` thì khác: nó là một **kiểm tra không được phép làm tròn**, có thể dùng cả với context có precision hữu hạn và `setScale`.

~~~java
BigDecimal ok = new BigDecimal("2.340");
System.out.println(ok.setScale(2, RoundingMode.UNNECESSARY)); // 2.34

// Ném ArithmeticException: mất chữ số 5:
new BigDecimal("2.345").setScale(2, RoundingMode.UNNECESSARY);

// Ném ArithmeticException: cần precision > 3 để giữ nguyên:
new BigDecimal("2.345").round(
        new MathContext(3, RoundingMode.UNNECESSARY));
~~~

Dùng `UNNECESSARY` tại biên dữ liệu khi **chính sách yêu cầu chính xác** và muốn phát hiện đầu vào không phù hợp thay vì làm tròn âm thầm. Ví dụ đầu vào tiền được quy định tối đa 2 chữ số thập phân có thể kiểm tra bằng `setScale(2, RoundingMode.UNNECESSARY)` rồi từ chối `"12.345"` nếu đúng quy tắc.

`UNNECESSARY` không đồng nghĩa với “không bao giờ có ngoại lệ” hay “chỉ ghi log”; nó **chủ động làm phép toán thất bại** nếu phải bỏ chữ số có nghĩa.

## 8. Ranh giới làm tròn: mỗi dòng hay tổng cuối?

Tính toán thập phân không chỉ phụ thuộc rounding mode mà còn **thời điểm làm tròn**. Xét ba khoản giống nhau `1.005` và chính sách `HALF_UP` về 2 chữ số:

**Cách A: làm tròn từng khoản rồi cộng**

~~~java
BigDecimal each = new BigDecimal("1.005")
        .setScale(2, RoundingMode.HALF_UP); // 1.01
BigDecimal totalA = each.multiply(new BigDecimal("3"));
// 3.03
~~~

**Cách B: cộng trước rồi làm tròn tổng**

~~~java
BigDecimal exactTotal = new BigDecimal("1.005")
        .multiply(new BigDecimal("3"));     // 3.015
BigDecimal totalB = exactTotal.setScale(2, RoundingMode.HALF_UP);
// 3.02
~~~

Hai số đều hợp lý **theo hai quy tắc chốt giá trị khác nhau**; chúng không tương đương. Chênh lệch `0.01` xuất hiện dù cùng đầu vào, cùng kiểu `BigDecimal`, cùng `RoundingMode.HALF_UP` và cùng target scale.

Điều phải làm trong thiết kế là quy định:

- Chốt làm tròn **từng dòng hóa đơn** rồi tính tổng?
- Hay giữ nguyên từng giá trị trung gian, **làm tròn một lần khi chốt tổng**?
- Thuế áp dụng trên từng dòng hay trên tổng sau chiết khấu?
- Các phần lẻ bị phân bổ thế nào nếu tổng phải bằng tổng chi tiết sau chốt?
- Khi có hoàn tiền hoặc điều chỉnh âm, mode và thứ tự thao tác có được áp dụng nhất quán không?

Đừng đổi vị trí rounding chỉ để test qua: thay đổi đó có thể làm sai số dư hoặc hợp đồng nghiệp vụ.

## 9. Chương trình Java 17 chạy độc lập và kết quả chuẩn

Tạo tệp `DecimalDesignDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class DecimalDesignDemo {
    private static final MathContext WORK =
            new MathContext(4, RoundingMode.HALF_EVEN);
    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUND = RoundingMode.HALF_UP;

    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("123.456");
        BigDecimal b = new BigDecimal("0.789");
        System.out.println("exact add = " + a.add(b));
        System.out.println("context add = " + a.add(b, WORK));
        System.out.println("exact multiply = " + a.multiply(b));
        System.out.println("context multiply = " + a.multiply(b, WORK));
        System.out.println("prec and scale = "
                + a.precision() + " / " + a.scale());

        BigDecimal one = BigDecimal.ONE;
        BigDecimal three = new BigDecimal("3");
        System.out.println("division context = "
                + one.divide(three,
                        new MathContext(6, RoundingMode.HALF_EVEN)));
        System.out.println("division fixed scale = "
                + one.divide(three, MONEY_SCALE, MONEY_ROUND));

        BigDecimal halfUp = new BigDecimal("2.345")
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal halfEven = new BigDecimal("2.345")
                .setScale(2, RoundingMode.HALF_EVEN);
        System.out.println("half up / even = " + halfUp + " / " + halfEven);
        System.out.println("negative half up = "
                + new BigDecimal("-2.345")
                        .setScale(2, RoundingMode.HALF_UP));
        System.out.println("round precision = "
                + new BigDecimal("999.9")
                        .round(new MathContext(3, RoundingMode.HALF_UP)));
        System.out.println("money 12345.00 = "
                + new BigDecimal("12345").setScale(2));

        BigDecimal x = new BigDecimal("1.005");
        BigDecimal each = x.setScale(2, MONEY_ROUND)
                .multiply(new BigDecimal("3"));
        BigDecimal end = x.multiply(new BigDecimal("3"))
                .setScale(2, MONEY_ROUND);
        System.out.println("round each = " + each);
        System.out.println("round at end = " + end);

        try {
            one.divide(three);
        } catch (ArithmeticException ex) {
            System.out.println("unlimited divide = ArithmeticException");
        }
        try {
            new BigDecimal("2.345")
                    .setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            System.out.println("unnecessary scale = ArithmeticException");
        }
        try {
            new BigDecimal("2.345").round(
                    new MathContext(3, RoundingMode.UNNECESSARY));
        } catch (ArithmeticException ex) {
            System.out.println("unnecessary precision = ArithmeticException");
        }
        System.out.println("exact scale = "
                + new BigDecimal("2.340")
                        .setScale(2, RoundingMode.UNNECESSARY));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 DecimalDesignDemo.java
java DecimalDesignDemo
~~~

Kết quả được kiểm chứng:

~~~text
exact add = 124.245
context add = 124.2
exact multiply = 97.406784
context multiply = 97.41
prec and scale = 6 / 3
division context = 0.333333
division fixed scale = 0.33
half up / even = 2.35 / 2.34
negative half up = -2.35
round precision = 1.00E+3
money 12345.00 = 12345.00
round each = 3.03
round at end = 3.02
unlimited divide = ArithmeticException
unnecessary scale = ArithmeticException
unnecessary precision = ArithmeticException
exact scale = 2.34
~~~

Chú ý dòng `round precision = 1.00E+3`: khi `999.9` làm tròn thành ba chữ số có nghĩa, phép làm tròn có carry sang hàng nghìn. `MathContext` không buộc kết quả in dạng `1000.00` hay giữ nguyên scale ban đầu.

Trong chương trình, `WORK` là một chính sách **minh họa** cho bài tập, không phải khuyến nghị rằng mọi hệ thống tiền tệ nên lấy 4 chữ số có nghĩa. `MONEY_SCALE = 2` cũng là giả định của bài tập, không áp dụng mặc định cho mọi đơn vị tiền trên thế giới.

## 10. Thiết kế một luồng xử lý thập phân theo nghiệp vụ

Một quy trình dễ kiểm toán tách bốn giai đoạn:

~~~text
Input decimal đã được xác thực
        |
        v
Giá trị BigDecimal nguyên bản + đơn vị + metadata
        |
        v
Phép toán có quy tắc precision/rounding công khai
        |
        v
Điểm chốt: setScale hoặc chia với scale cố định
        |
        +---> Giá trị đã chốt đem lưu/xuất machine-readable
        |
        +---> Chuỗi hiển thị theo locale và định dạng UI
~~~

**Bước 1 — Xác định đơn vị:** `12.34` là 12.34 đồng, 12.34 USD, hay tỷ lệ 12.34%? Số decimal không mang theo đơn vị; lưu đơn vị với dữ liệu hoặc trong kiểu miền nghiệp vụ.

**Bước 2 — Xác định độ chính xác đầu vào:** số được nhập tối đa mấy chữ số sau dấu thập phân, giới hạn độ lớn ra sao, có chấp nhận ký pháp mũ, số âm và số 0 không?

**Bước 3 — Xác định policy phép toán:** nếu chỉ cộng/nhân chính xác với dữ liệu hữu hạn và kích thước trong khả năng tài nguyên, có thể không cần round ở mỗi thao tác. Nếu có phép chia vô hạn, cần chọn context/scale và rounding. Với thuật toán mô phỏng precision cố định, truyền `MathContext` nhất quán qua các bước cần thiết.

**Bước 4 — Xác định thời điểm chốt:** một giao dịch có thể yêu cầu làm tròn từng dòng trước khi cộng; thuật toán khác có thể yêu cầu cộng chính xác rồi round kết quả. Phải viết điều này trong code và test.

**Bước 5 — Tách lưu trữ và hiển thị:** `toPlainString()`, `toString()`, `DecimalFormat` và `setScale()` có tác dụng khác nhau. Định dạng chuỗi có thể chọn dấu phẩy, dấu chấm và ký hiệu tiền theo locale, nhưng việc format chỉ để hiển thị **không thay giá trị BigDecimal gốc**.

Một đối tượng policy giúp tránh những phép tính dùng mode “mỗi nơi một kiểu”:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class DecimalPolicy {
    private final int scale;
    private final RoundingMode rounding;

    public DecimalPolicy(int scale, RoundingMode rounding) {
        this.scale = scale;
        this.rounding = java.util.Objects.requireNonNull(rounding);
    }

    public BigDecimal finish(BigDecimal value) {
        return java.util.Objects.requireNonNull(value)
                .setScale(scale, rounding);
    }
}
~~~

Đoạn này là **lớp độc lập có thể biên dịch khi lưu vào `DecimalPolicy.java`** nhưng chưa có `main`; nó thể hiện nơi đặt chính sách chốt giá trị. Đối với ứng dụng thật, cần validate `scale` phù hợp quy tắc, đơn vị và giới hạn tài nguyên, đồng thời thêm test cho số âm, tie và giá trị vượt giới hạn.

## 11. MathContext không tự áp đặt số chữ số thập phân cho tiền

`MathContext.DECIMAL32`, `DECIMAL64` và `DECIMAL128` lần lượt có precision **7, 16, 34** và `RoundingMode.HALF_EVEN`. Đây là những cấu hình precision tiện dùng, không phải quy định tiền tệ.

~~~java
MathContext a = MathContext.DECIMAL32;  // precision 7
MathContext b = MathContext.DECIMAL64;  // precision 16
MathContext c = MathContext.DECIMAL128; // precision 34
~~~

Dùng `DECIMAL64` không có nghĩa kết quả luôn là **hai chữ số sau dấu phẩy**, và cũng không tự hạn chế exponent/scale như toàn bộ một định dạng IEEE decimal64 vật lý. `BigDecimal` với context có precision tương ứng nhưng không ràng buộc tập scale/exponent giống hệt định dạng IEEE.

Với một đơn vị tiền có chính sách chốt hai chữ số, cần thao tác `setScale(2, mode)` tại ranh giới thích hợp hoặc lưu số nguyên đơn vị nhỏ nhất nếu quy trình phù hợp. Không dùng context precision 2, precision 16 hay “DECIMAL64” làm tên khác của `scale = 2`.

Nếu yêu cầu là **tỷ lệ phần trăm** có 6 chữ số có nghĩa, có thể dùng context 6 và chuyển đổi đơn vị đúng hợp đồng; đừng coi 0.075, 7.5% và 7.5 là cùng một số không cần quy tắc.

## 12. Làm tròn sớm và làm tròn kép

Làm tròn có thể không có tính phân phối hoặc kết hợp như phép toán số học chính xác. Một ví dụ đặc biệt nguy hiểm là **làm tròn một kết quả rồi lại làm tròn lần nữa theo scale khác**.

Xét `2.345` với `HALF_UP`:
- Trực tiếp `setScale(1, HALF_UP)` cho `2.3`, vì chữ số thứ hai là 4;
- Nếu `setScale(2, HALF_UP)` trước, được `2.35`, rồi `setScale(1, HALF_UP)` lần nữa, kết quả là `2.4`.

~~~java
BigDecimal x = new BigDecimal("2.345");
BigDecimal direct = x.setScale(1, RoundingMode.HALF_UP);
BigDecimal twice = x.setScale(2, RoundingMode.HALF_UP)
                    .setScale(1, RoundingMode.HALF_UP);

System.out.println(direct); // 2.3
System.out.println(twice);  // 2.4
~~~

Đây là **double rounding**: kết quả cuối có thể thay đổi khi round qua một precision/scale trung gian. Do đó, nếu ứng dụng yêu cầu chốt một lần ở scale 1, không tự ý đưa vào một bước `setScale(2)` chỉ để “chuẩn hóa định dạng”.

Điều này cũng xảy ra khi quy trình dùng `MathContext` ở các phép toán trước, rồi lại dùng `setScale` ở cuối: hai lần rounding có thể cho kết quả khác so với tính chính xác rồi chốt một lần. Không có chiến lược phổ quát — tính toán với precision giới hạn có thể là yêu cầu bắt buộc. Điều quan trọng là **biết rõ bước nào làm thay đổi giá trị** và viết test theo đúng thứ tự xử lý.

## 13. BigDecimal.equals(), compareTo() và scale sau phép tính

Hai `BigDecimal` có thể bằng nhau về giá trị số nhưng khác biểu diễn:

~~~java
BigDecimal x = new BigDecimal("2.0");
BigDecimal y = new BigDecimal("2.00");

System.out.println(x.compareTo(y) == 0); // true
System.out.println(x.equals(y));         // false
~~~

`compareTo()` dùng thứ tự **theo giá trị số**; `equals()` còn xét `scale`. Vì vậy một bước `setScale(2)` có thể làm thay đổi kết quả `equals()` nhưng không làm thay đổi phép so sánh số học. Khi làm khóa cho `HashMap` hoặc phần tử trong `HashSet`, cần xác định rõ danh tính bao gồm scale hay không.

`stripTrailingZeros()` bỏ số 0 tận cùng mà không đổi giá trị số nhưng có thể làm scale âm:

~~~java
BigDecimal amount = new BigDecimal("1000.00");
System.out.println(amount.stripTrailingZeros()); // 1E+3
~~~

Nếu chính sách lưu trữ yêu cầu `"1000.00"`, đừng gọi `stripTrailingZeros` như một bước format bắt buộc. `MathContext`, `setScale`, `stripTrailingZeros` và `toPlainString()` phục vụ những chức năng khác nhau.

## 14. Kiểm tra ngoại lệ và giới hạn đầu vào

Một thiết kế tốt cần chủ động xử lý các lỗi thay vì chỉ bắt mọi `ArithmeticException` rồi cho 0:

| Tình huống | Phản ứng hợp lý |
|---|---|
| Chia cho 0 | Từ chối divisor 0; báo lỗi miền nghiệp vụ |
| `divide` chính xác nhưng kết quả không hữu hạn | Yêu cầu policy precision/scale + rounding, hoặc từ chối nếu nghiệp vụ bắt buộc chính xác |
| `setScale(2, UNNECESSARY)` phải bỏ chữ số khác 0 | Từ chối dữ liệu không tuân thủ scale đã thỏa thuận |
| `new MathContext(-1, mode)` | `IllegalArgumentException`, vì precision không được âm |
| Giới hạn chữ số đầu vào quá lớn | Validate độ dài/giá trị, chống tiêu thụ CPU và bộ nhớ quá mức |
| Kết quả vượt schema DECIMAL của database | Kiểm tra trước khi lưu, không trông chờ cơ sở dữ liệu tự làm tròn đúng chính sách |
| Chuyển `BigDecimal` sang `int`/`long` | Dùng `intValueExact()`/`longValueExact()` nếu không được phép mất phần lẻ hoặc tràn số |

`BigDecimal` hỗ trợ precision tùy ý về mặt khái niệm nhưng vẫn phụ thuộc tài nguyên máy, miền `scale` kiểu `int` và giới hạn thư viện. Một input số mũ cực đoan, chuỗi decimal khổng lồ hoặc yêu cầu `toPlainString()` với exponent lớn có thể gây tiêu tốn bộ nhớ đáng kể. Xác thực giới hạn dữ liệu không kém quan trọng so với chọn rounding mode.

Không bắt `ArithmeticException` rồi trả `BigDecimal.ZERO` một cách chung chung: nó che cả lỗi chia 0, sai chính sách rounding và dữ liệu không hợp lệ, làm kết quả sai mà khó kiểm toán.

## 15. Các test cần có cho chính sách thập phân

Một bộ kiểm thử nên gồm ít nhất những nhóm sau:

1. **Giữa hai giá trị (tie):** `2.345` với scale 2 cho `HALF_UP = 2.35`, `HALF_EVEN = 2.34`.
2. **Số âm:** `-2.345` với `HALF_UP` và các mode hướng tới ±∞.
3. **Chia vô hạn:** `1 / 3` phải dùng context/scale; kiểm tra ngoại lệ khi dùng overload chính xác.
4. **Chia hữu hạn:** `1 / 8 = 0.125` không cần rounding nếu để kết quả chính xác.
5. **Rounding từng dòng so với tổng:** `1.005 × 3` tạo `3.03` hoặc `3.02` tùy policy.
6. **Scale của kết quả:** `setScale(2)` cho `12345.00`, trong khi precision 3 có thể biến 12345 thành `1.23E+4`.
7. **Không mất dữ liệu bắt buộc:** `UNNECESSARY` từ chối `2.345` khi cần scale 2 nhưng cho phép `2.340` về `2.34`.
8. **Overflow khi ép kiểu và giới hạn lưu trữ:** kiểm tra `longValueExact()` khi chuyển sang đơn vị nhỏ nhất.
9. **Bất biến giá trị ban đầu:** vì `BigDecimal` immutable, tính toán/format không sửa instance gốc.
10. **Chính sách đầu vào:** từ chối định dạng/giá trị ngoài hợp đồng trước khi tính và lưu.

Ở cấp ứng dụng, nên test luôn **kết quả giá trị** và nếu cần **scale**:

~~~java
BigDecimal actual = new BigDecimal("2.340").setScale(2);
BigDecimal expected = new BigDecimal("2.34");

System.out.println(actual.equals(expected));     // true
System.out.println(actual.compareTo(expected));  // 0
~~~

Không viết một test chỉ so sánh chuỗi UI khi chức năng cần xác minh giá trị số; ngược lại, nếu hợp đồng yêu cầu giữ đúng hai chữ số sau dấu thập phân, kiểm tra cả `scale()` hoặc format đầu ra thích hợp.

## 16. Bài tập tự thực hành

### Bài 1 — precision hay scale?

Với `x = new BigDecimal("98765.4321")`, bạn muốn:
- (A) giữ bốn chữ số có nghĩa;
- (B) giữ hai chữ số sau dấu thập phân.

**Đáp án định hướng:** (A) gọi `x.round(new MathContext(4, policy))`; (B) gọi `x.setScale(2, policy)`. Đây không phải hai cách viết của cùng một kết quả. Với `HALF_UP`, (A) là `9.877E+4`, còn (B) là `98765.43`. Cần chọn chính sách trước khi triển khai.

### Bài 2 — chốt từng dòng

Ba đơn giá đều là `1.005` đơn vị tiền và phải xuất tổng hai chữ số. Hãy nêu hai kết quả hợp lệ theo hai chính sách khác nhau.

**Đáp án:** Làm tròn từng dòng theo `HALF_UP`: `1.01 × 3 = 3.03`. Cộng trước rồi làm tròn tổng: `3.015 → 3.02`. Phải ghi rõ quy tắc mà nghiệp vụ yêu cầu.

### Bài 3 — divide() báo lỗi

`new BigDecimal("1").divide(new BigDecimal("3"))` ném ngoại lệ dù không chia cho 0. Vì sao?

**Đáp án:** Vì `1/3` có khai triển decimal vô hạn, nên phép chia chính xác không thể trả một `BigDecimal` hữu hạn. Phải quy định precision/scale và rounding hoặc từ chối phép tính nếu yêu cầu chính xác tuyệt đối.

### Bài 4 — MathContext.UNLIMITED

`MathContext.UNLIMITED` có tự làm `1/3` thành một số hữu hạn không?

**Đáp án:** Không. `precision = 0` yêu cầu tính toán chính xác không làm tròn; phép chia có kết quả decimal vô hạn sẽ báo `ArithmeticException`.

### Bài 5 — ROUND_UNNECESSARY

Với `new BigDecimal("7.500")` và `new BigDecimal("7.505")`, giá trị nào có thể chuyển sang scale 2 bằng `RoundingMode.UNNECESSARY`?

**Đáp án:** `7.500 → 7.50` chính xác; `7.505` không thể giữ nguyên giá trị khi scale 2, nên ném ngoại lệ.

### Bài 6 — double rounding

Giả sử yêu cầu cuối là một chữ số thập phân, rounding `HALF_UP`. So sánh:
- `new BigDecimal("2.345").setScale(1, HALF_UP)`;
- `new BigDecimal("2.345").setScale(2, HALF_UP).setScale(1, HALF_UP)`.

**Đáp án:** Trực tiếp ra `2.3`, qua trung gian ra `2.4`. Không thêm bước làm tròn trung gian nếu hợp đồng không yêu cầu.

## 17. Checklist trước khi đưa vào sản phẩm

Trước khi áp dụng `MathContext` và `RoundingMode` trong nghiệp vụ thật, xác nhận:

- Đầu vào là số decimal chính xác từ chuỗi, số nguyên đơn vị nhỏ nhất hoặc nguồn có hợp đồng rõ, không âm thầm đi qua `double` ngoài ý muốn.
- Đơn vị, precision đầu vào, scale chốt và giới hạn độ lớn đều được định nghĩa.
- Các phép toán cần precision hữu hạn được truyền cùng `MathContext` theo đúng phương án tính toán, không nhầm với scale.
- Phép chia xác định explicit scale/precision, mode và quy tắc xử lý divisor 0.
- Biết rõ lúc nào tính chính xác, lúc nào round; không để vòng tính “round mỗi bước” xuất hiện ngẫu nhiên.
- Quy tắc `HALF_UP`, `HALF_EVEN`, `CEILING`, `FLOOR`, `UNNECESSARY` được chọn có lý do từ nghiệp vụ và có test số âm/tie.
- Dữ liệu sau chốt được lưu đúng schema và đơn vị; format UI là trách nhiệm riêng.
- Nếu cần chứng minh không mất chữ số, dùng `UNNECESSARY` và xử lý lỗi, không chỉ âm thầm làm tròn.
- Có ví dụ kiểm thử cho precision khác scale, rounding từng dòng vs tổng cuối và double rounding.
- Tất cả API public hoặc trường lưu trữ dùng policy nhất quán, tránh “mỗi chỗ một mặc định” làm lệch số liệu.

## 18. Tài liệu tham khảo

- [BigDecimal — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): `precision`, `scale`, preferred scales, `add`, `multiply`, `divide`, `setScale`, `round`, `stripTrailingZeros`.
- [MathContext — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/MathContext.html): `precision`, `roundingMode`, `UNLIMITED`, `DECIMAL32`, `DECIMAL64`, `DECIMAL128`.
- [RoundingMode — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/RoundingMode.html): tám chính sách làm tròn và ví dụ xử lý các trường hợp midpoint.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-practice-008">So sánh HashSet với TreeSet khi chứa BigDecimal</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành so sánh `HashSet` và `TreeSet` khi chứa `BigDecimal`

## 1. Vấn đề cần giải quyết

`HashSet` và `TreeSet` đều hiện thực giao diện `Set` trong Java, nhưng với `BigDecimal`, chúng **không nhất thiết giữ cùng số phần tử** khi nhận cùng danh sách đầu vào:

- `HashSet<BigDecimal>` nhận định trùng lặp theo `equals()` và `hashCode()`.
- `TreeSet<BigDecimal>` dùng thứ tự tự nhiên `compareTo()` hoặc `Comparator` đã truyền vào. Hai phần tử được xem là cùng một phần tử nếu phép so sánh trả **0**.

Điểm đặc biệt của `BigDecimal`: `equals()` kiểm tra **cả giá trị lẫn scale**, nhưng `compareTo()` chỉ so sánh **giá trị toán học**.

~~~java
BigDecimal a = new BigDecimal("1.0");
BigDecimal b = new BigDecimal("1.00");

System.out.println(a.equals(b));    // false
System.out.println(a.compareTo(b)); // 0
~~~

Hai số có giá trị bằng 1 nhưng scale lần lượt 1 và 2. Do đó `HashSet` xem chúng là hai phần tử khác, còn `TreeSet` mặc định xem chúng là một lớp tương đương. Sự khác biệt này tác động đến `add`, `size`, `contains`, `remove` và dữ liệu xuất ra.

**Mục tiêu:** tự chạy một chương trình Java 17+, dự đoán đầu ra trước khi chạy, giải thích từng dòng, sau đó chọn quy tắc trùng lặp phù hợp hợp đồng dữ liệu thay vì chọn collection theo thói quen.

## 2. Ôn lại `BigDecimal` và scale

Công thức biểu diễn của `BigDecimal`:

~~~text
value = unscaledValue × 10^(-scale)
~~~

| Dữ liệu đầu vào | Unscaled value | Scale | Giá trị toán học |
| --- | ---: | ---: | ---: |
| `"1"` | 1 | 0 | 1 |
| `"1.0"` | 10 | 1 | 1 |
| `"1.00"` | 100 | 2 | 1 |
| `"1.000"` | 1000 | 3 | 1 |
| `"1E+0"` | 1 | 0 | 1 |
| `"1000.00"` | 100000 | 2 | 1000 |

Tất cả các biểu diễn có giá trị 1 đều bằng nhau theo `compareTo`, nhưng chỉ các đối tượng có cùng giá trị và scale mới bằng nhau theo `equals`. `BigDecimal.hashCode()` cũng có tính đến scale để nhất quán với `equals`.

Vì vậy, khi cần bảo toàn số chữ số sau dấu thập phân như một phần của hợp đồng, **không được mặc định rằng “cùng giá trị” nghĩa là “cùng dữ liệu”**. Ngược lại, nếu chỉ cần tập các giá trị số học phân biệt, không nên vô tình giữ nhiều biểu diễn chỉ vì chúng khác scale.

Hãy dựng dữ liệu thực hành bằng `new BigDecimal("1.00")` thay vì qua `double`. Constructor nhận chuỗi giữ đúng giá trị thập phân và scale dự định; constructor nhận `double` có thể đưa vào sai số nhị phân cho nhiều số thập phân như 0.1.

## 3. `HashSet<BigDecimal>`: quy tắc bằng nhau có xét scale

Thí nghiệm:

~~~java
HashSet<BigDecimal> hash = new HashSet<>();
System.out.println(hash.add(new BigDecimal("1.0")));   // true
System.out.println(hash.add(new BigDecimal("1.00")));  // true
System.out.println(hash.add(new BigDecimal("1.000"))); // true
System.out.println(hash.size());                       // 3
~~~

Đây là đoạn mã minh họa đặt trong phương thức, cần import `java.math.BigDecimal` và `java.util.HashSet`.

Cả ba lời gọi `add` trả `true`, vì ba đối tượng không bằng nhau theo `BigDecimal.equals`. Số phần tử trong tập là 3 mặc dù có duy nhất một giá trị toán học là 1.

Hệ quả với truy vấn:

~~~java
hash.contains(new BigDecimal("1.00"));   // true
hash.contains(new BigDecimal("1.0000")); // false
~~~

`1.0000` có scale 4, chưa được lưu trong set. Kết quả `false` không có nghĩa trong set không có số bằng 1 về toán học; nó chỉ có nghĩa không có đối tượng tương đương theo `equals`.

**Không dựa vào thứ tự duyệt hoặc `toString()` của `HashSet`.** `HashSet` không cam kết duy trì thứ tự chèn hay sắp xếp. Khi kiểm thử kết quả hãy dùng `size`, `contains`, các phép so sánh hoặc kiểm tra từng phần tử mà không dựa vào thứ tự ngẫu nhiên.

## 4. `TreeSet<BigDecimal>`: quy tắc bằng nhau theo comparator

`TreeSet` là `NavigableSet` duy trì thứ tự. Khi không truyền `Comparator`, kiểu `BigDecimal` dùng `compareTo` làm thứ tự tự nhiên:

~~~java
TreeSet<BigDecimal> tree = new TreeSet<>();
System.out.println(tree.add(new BigDecimal("1.0")));   // true
System.out.println(tree.add(new BigDecimal("1.00")));  // false
System.out.println(tree.add(new BigDecimal("1.000"))); // false
System.out.println(tree.size());                       // 1
System.out.println(tree);                              // [1.0]
~~~

Giữa ba số, phép `compareTo` trả 0. Chỉ phần tử được thêm đầu tiên (`1.0`) trở thành **đại diện đang lưu**. Các lần thêm số có cùng giá trị nhưng khác scale không thay thế đại diện cũ. `TreeSet` không tự gọi `setScale` hay `stripTrailingZeros`.

### 4.1. `contains` và `remove` có thể gây bất ngờ

~~~java
TreeSet<BigDecimal> tree = new TreeSet<>();
tree.add(new BigDecimal("1.0"));

System.out.println(tree.contains(new BigDecimal("1.0000"))); // true
System.out.println(tree.remove(new BigDecimal("1.0000")));   // true
System.out.println(tree.isEmpty());                          // true
~~~

Lời gọi `contains` là `true` vì có phần tử so sánh bằng 0, **không phải vì set đã lưu đối tượng scale 4**. Tương tự, `remove` xóa đại diện `1.0` bằng cách tìm một khóa tương đương theo comparator.

Nếu `HashSet` chỉ chứa `1.0`, `remove(new BigDecimal("1.0000"))` lại trả `false`. Vì vậy không được coi hai kiểu `Set` là thay thế hoàn toàn về ngữ nghĩa trong bài toán này.

### 4.2. Đại diện giữ lại phụ thuộc thứ tự chèn

~~~java
TreeSet<BigDecimal> first = new TreeSet<>();
first.add(new BigDecimal("6.0"));
first.add(new BigDecimal("6.00"));

TreeSet<BigDecimal> second = new TreeSet<>();
second.add(new BigDecimal("6.00"));
second.add(new BigDecimal("6.0"));

System.out.println(first);  // [6.0]
System.out.println(second); // [6.00]
~~~

Hai tập đều chỉ có **một giá trị toán học phân biệt**, nhưng scale của đại diện được lưu khác nhau. Nếu API yêu cầu biểu diễn đúng scale, đừng dùng `TreeSet` mặc định làm bước “chuẩn hóa” bởi thứ tự chèn có thể quyết định chuỗi kết quả.

## 5. Bài thực hành chính: dự đoán kết quả cho cùng dữ liệu

Cho đầu vào:

~~~java
List<BigDecimal> data = List.of(
        new BigDecimal("2.0"),
        new BigDecimal("1.0"),
        new BigDecimal("1.00"),
        new BigDecimal("2.00"),
        new BigDecimal("1.000"));
~~~

Xét về **giá trị số học**, dữ liệu chỉ có hai giá trị 1 và 2. Xét về **cặp giá trị và scale**, có năm phần tử phân biệt.

| Phép kiểm tra | `HashSet` | `TreeSet` mặc định |
| --- | --- | --- |
| `size()` sau khi thêm năm phần tử | 5 | 2 |
| Có gom `1.0` và `1.00` thành một không? | Không | Có |
| `contains(1.0000)` | `false` | `true` |
| Đồng nhất phần tử | `equals/hashCode` | `compareTo == 0` |
| Duy trì thứ tự số tăng dần | Không bảo đảm | Có |
| Tự chuẩn hóa scale | Không | Không |

Với đúng thứ tự dữ liệu trên, `TreeSet.toString()` cho `[1.0, 2.0]`. Điều này do hai đại diện `1.0` và `2.0` được gặp trước các biểu diễn khác của chúng; không phải vì `TreeSet` cố tình chọn biểu diễn ngắn nhất.

## 6. Chương trình Java 17+ chạy độc lập

Tạo tệp **BigDecimalSetsLab.java**:

~~~java
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class BigDecimalSetsLab {
    private static final Comparator<BigDecimal> VALUE_THEN_SCALE =
            (a, b) -> {
                int byValue = a.compareTo(b);
                return byValue != 0
                        ? byValue
                        : Integer.compare(a.scale(), b.scale());
            };

    public static void main(String[] args) {
        List<BigDecimal> data = List.of(
                new BigDecimal("2.0"),
                new BigDecimal("1.0"),
                new BigDecimal("1.00"),
                new BigDecimal("2.00"),
                new BigDecimal("1.000"));

        HashSet<BigDecimal> hash = new HashSet<>(data);
        TreeSet<BigDecimal> tree = new TreeSet<>(data);

        BigDecimal one = new BigDecimal("1.0");
        BigDecimal oneHundredths = new BigDecimal("1.00");
        BigDecimal lookup = new BigDecimal("1.0000");

        System.out.println("equals 1.0 / 1.00: "
                + one.equals(oneHundredths));
        System.out.println("compareTo 1.0 / 1.00: "
                + one.compareTo(oneHundredths));

        System.out.println("hash size: " + hash.size());
        System.out.println("tree size: " + tree.size());
        System.out.println("tree values: " + tree);

        System.out.println("hash contains 1.0000: "
                + hash.contains(lookup));
        System.out.println("tree contains 1.0000: "
                + tree.contains(lookup));

        TreeSet<BigDecimal> scaleAware =
                new TreeSet<>(VALUE_THEN_SCALE);
        scaleAware.addAll(data);
        System.out.println("scale-aware tree size: "
                + scaleAware.size());
        System.out.println("scale-aware tree values: "
                + scaleAware);

        HashSet<BigDecimal> normalized = data.stream()
                .map(BigDecimal::stripTrailingZeros)
                .collect(Collectors.toCollection(HashSet::new));
        System.out.println("normalized hash size: "
                + normalized.size());

        System.out.println("1000.00 stripped: "
                + new BigDecimal("1000.00")
                        .stripTrailingZeros());

        System.out.println("tree remove 1.0000: "
                + tree.remove(lookup));
        System.out.println("tree after remove: " + tree);
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~bash
javac BigDecimalSetsLab.java
java BigDecimalSetsLab
~~~

Kết quả:

~~~text
equals 1.0 / 1.00: false
compareTo 1.0 / 1.00: 0
hash size: 5
tree size: 2
tree values: [1.0, 2.0]
hash contains 1.0000: false
tree contains 1.0000: true
scale-aware tree size: 5
scale-aware tree values: [1.0, 1.00, 1.000, 2.0, 2.00]
normalized hash size: 2
1000.00 stripped: 1E+3
tree remove 1.0000: true
tree after remove: [2.0]
~~~

Các dòng trên không in trực tiếp `HashSet`, bởi thứ tự phần tử của hash set không được bảo đảm. `TreeSet` mặc định sắp theo giá trị toán học, trong khi `scaleAware` sử dụng comparator riêng để phân biệt các biểu diễn có cùng giá trị nhưng khác scale.

## 7. Làm rõ `add`, `contains`, `remove` và tập hợp tương đương

Trong `HashSet`, việc thêm `BigDecimal("7.00")` sau `BigDecimal("7.0")` làm tăng số phần tử vì `equals` là `false`. Trong `TreeSet` mặc định, việc thêm tương tự không làm tăng số phần tử vì `compareTo == 0`.

~~~java
HashSet<BigDecimal> hash = new HashSet<>();
hash.add(new BigDecimal("7.0"));
System.out.println(hash.add(new BigDecimal("7.00"))); // true

TreeSet<BigDecimal> tree = new TreeSet<>();
tree.add(new BigDecimal("7.0"));
System.out.println(tree.add(new BigDecimal("7.00"))); // false
~~~

`Set.add` trả `true` khi tập hợp thay đổi, `false` nếu phần tử được nhận định đã tồn tại. Với `TreeSet` trên, `false` **không thay đại diện `7.0` bằng `7.00`**.

`TreeSet` giữ thứ tự tăng dần theo comparator và hỗ trợ các phép `first()`, `last()`, `ceiling()`, `floor()`, `subSet()`. `HashSet` tập trung vào tra cứu theo hash, không bảo đảm thứ tự. Nhưng vấn đề chính khi dùng `BigDecimal` không đơn thuần là hiệu năng/sắp xếp mà là **định nghĩa “trùng nhau”**.

## 8. Nếu cần sắp xếp mà vẫn phân biệt scale

`TreeSet<BigDecimal>` tự nhiên coi `1.0` và `1.00` là một. Khi hợp đồng yêu cầu **hai đối tượng khác nhau theo `BigDecimal.equals` vẫn phải là hai phần tử**, hãy dùng comparator phân biệt scale khi giá trị bằng nhau:

~~~java
Comparator<BigDecimal> valueThenScale = (a, b) -> {
    int byValue = a.compareTo(b);
    return byValue != 0 ? byValue
            : Integer.compare(a.scale(), b.scale());
};

TreeSet<BigDecimal> scaleAware = new TreeSet<>(valueThenScale);
scaleAware.add(new BigDecimal("1.0"));
scaleAware.add(new BigDecimal("1.00"));
System.out.println(scaleAware.size()); // 2
~~~

Comparator sắp theo **giá trị toán học** trước; nếu giá trị bằng nhau, scale quyết định thứ tự. Với `BigDecimal` hợp lệ, comparator trả 0 chính xác khi `equals` trả `true`, nên quan hệ bằng nhau của set nhất quán với `equals`.

Đừng dùng `a.toString().compareTo(b.toString())` làm comparator số chỉ vì dễ viết: so sánh từ điển của chuỗi có thể cho thứ tự khác thứ tự toán học (nhất là số âm, độ dài phần nguyên và ký pháp mũ).

Nếu không cần sắp xếp nhưng cần giữ **thứ tự chèn**, `LinkedHashSet` có thể thích hợp. Nó vẫn dùng `equals`/`hashCode` để nhận định trùng lặp, tương tự `HashSet`. Thứ tự và quan hệ bằng nhau là hai quyết định riêng biệt.

## 9. Nếu muốn khử trùng lặp theo giá trị số học

Nếu chỉ cần các giá trị toán học phân biệt, `TreeSet` mặc định có thể phù hợp. Tuy nhiên, nếu dùng `HashSet` để tra cứu theo hash mà muốn gom các `BigDecimal` khác scale, có thể **chuẩn hóa các khóa một cách tường minh**:

~~~java
HashSet<BigDecimal> canonical = new HashSet<>();

canonical.add(new BigDecimal("1.0").stripTrailingZeros());
canonical.add(new BigDecimal("1.00").stripTrailingZeros());

System.out.println(canonical.size()); // 1
~~~

`stripTrailingZeros()` giữ nguyên **giá trị toán học** nhưng có thể thay đổi scale. Ví dụ:

~~~java
BigDecimal raw = new BigDecimal("1000.00");
BigDecimal compact = raw.stripTrailingZeros();

System.out.println(compact);         // 1E+3
System.out.println(compact.scale()); // -3
~~~

Điểm cần nhớ: chuẩn hóa theo cách này có thể tạo scale âm; **không thích hợp khi đầu ra bắt buộc giữ scale cố định**.

Nếu hợp đồng yêu cầu mọi khóa có đúng scale 2 và không được làm tròn, có thể chuẩn hóa bằng:

~~~java
BigDecimal key = raw.setScale(
        2, java.math.RoundingMode.UNNECESSARY);
~~~

Đây là **đoạn minh họa với biến `raw` kiểu `BigDecimal`**. `1.0`, `1.00` đều trở thành `1.00` và sẽ là cùng khóa hash. `1.234` không thể đổi chính xác thành scale 2, nên ném `ArithmeticException`. Nếu nghiệp vụ cho phép làm tròn, hãy chọn rounding mode một cách có chủ đích: làm tròn có thể khiến **hai giá trị ban đầu khác nhau** nhập thành một khóa.

## 10. `Set` contract và tác động đến API

`BigDecimal` là trường hợp nổi tiếng mà thứ tự tự nhiên **không nhất quán với `equals`**. Điều này được tài liệu Java nêu rõ.

Một API chỉ khai báo nhận `Set<BigDecimal>` nhưng tự giả định `contains` luôn có ý nghĩa theo `BigDecimal.equals` có thể hoạt động khác khi người gọi truyền `HashSet` hoặc `TreeSet`. Với `TreeSet`, tập dùng comparator để xác định phần tử tương đương; về mặt `Set` contract tổng quát dựa trên `equals`, kết quả có thể gây bất ngờ.

Ví dụ nếu một set chứa `1.0`:

~~~java
boolean present = set.contains(new BigDecimal("1.00"));
~~~

Nếu `set` là `HashSet`, kết quả `false`. Nếu là `TreeSet` tự nhiên, kết quả `true`. Vì vậy cần mô tả rõ quan hệ tương đương trong API, nhất là khi dữ liệu được dùng cho khử trùng lặp, phát hiện bản ghi lặp, lookup hoặc kiểm thử.

Tương tự, `HashMap<BigDecimal, V>` dùng `equals`/`hashCode` cho khóa, còn `TreeMap<BigDecimal, V>` dùng comparator. Khi đổi cấu trúc map, số khóa hoặc hành vi `get`/`put` có thể thay đổi. Không đổi giữa map hash và map tree nếu chưa xem xét ngữ nghĩa của `BigDecimal`.

## 11. Bài tập tự giải và đáp án

### Bài 1 — Dự đoán số phần tử

~~~java
List<BigDecimal> input = List.of(
        new BigDecimal("3"),
        new BigDecimal("3.0"),
        new BigDecimal("3.00"),
        new BigDecimal("4.0"),
        new BigDecimal("4.00"));

System.out.println(new HashSet<>(input).size());
System.out.println(new TreeSet<>(input).size());
~~~

**Đáp án:** `5` rồi `2`. Tập hash phân biệt cả giá trị/scale; tập tree tự nhiên chỉ phân biệt hai giá trị 3 và 4.

### Bài 2 — Tìm theo giá trị hay theo scale

~~~java
Set<BigDecimal> hash = new HashSet<>();
Set<BigDecimal> tree = new TreeSet<>();
hash.add(new BigDecimal("0.50"));
tree.add(new BigDecimal("0.50"));

System.out.println(hash.contains(new BigDecimal("0.5")));
System.out.println(tree.contains(new BigDecimal("0.5")));
~~~

**Đáp án:** `false` rồi `true`, vì scale 2 khác scale 1 nhưng `compareTo == 0`.

### Bài 3 — Thứ tự chèn ảnh hưởng đại diện thế nào?

~~~java
TreeSet<BigDecimal> one = new TreeSet<>();
one.add(new BigDecimal("6.0"));
one.add(new BigDecimal("6.00"));

TreeSet<BigDecimal> two = new TreeSet<>();
two.add(new BigDecimal("6.00"));
two.add(new BigDecimal("6.0"));

System.out.println(one); // [6.0]
System.out.println(two); // [6.00]
~~~

**Giải thích:** mỗi tập giữ đại diện được chèn trước. Hai tập có cùng một giá trị số học nhưng khác cách biểu diễn của phần tử.

### Bài 4 — Giữ cả ba scale mà vẫn có thứ tự

Yêu cầu: với `6.0`, `6.00`, `6.000`, một sorted set cần giữ đủ **3** phần tử. Hãy dùng comparator `valueThenScale` trong phần 8. Chỉ dùng `compareTo` thì còn một phần tử.

### Bài 5 — Chọn cấu trúc cho dữ liệu đơn giá

Một hệ thống coi `15.0` và `15.00` là **cùng đơn giá**, nhưng phải xuất kết quả ở **scale 2**. Không nên chỉ dùng `TreeSet` mặc định rồi in phần tử được giữ: đại diện có thể là `15.0`. Hãy chuẩn hóa đầu vào bằng `setScale(2, UNNECESSARY)` khi phù hợp, sau đó dùng set theo nhu cầu thứ tự/tra cứu. Nếu đầu vào có hơn hai chữ số khác 0 ở phần thập phân, xử lý lỗi hoặc chính sách rounding đã định nghĩa.

## 12. Kiểm thử trong Java

Với JUnit 5, các assertion minh họa sau cần đặt trong phương thức test và import `org.junit.jupiter.api.Assertions.*`:

~~~java
BigDecimal a = new BigDecimal("1.0");
BigDecimal b = new BigDecimal("1.00");

assertNotEquals(a, b);
assertEquals(0, a.compareTo(b));
assertEquals(2, new HashSet<>(List.of(a, b)).size());
assertEquals(1, new TreeSet<>(List.of(a, b)).size());
~~~

Chú ý `assertEquals(a, b)` sử dụng `equals` nên sẽ thất bại. Nếu contract chỉ xét giá trị số học, hãy kiểm tra `compareTo == 0` thay vì một assertion `equals` không tương ứng yêu cầu.

Các trường hợp nên kiểm thử thêm:

| Đầu vào | Điều muốn xác minh |
| --- | --- |
| `1.0`, `1.00`, `1.000` | Trùng theo giá trị hay giá trị+scale |
| `0`, `0.00` | Zero vẫn có scale trong `BigDecimal` |
| `-2.0`, `-2.00` | Quy tắc không phụ thuộc dấu |
| `1000.00`, `1E+3` | `stripTrailingZeros` có thể tạo scale âm |
| `1.01`, `1.010`, `1.02` | Không gom các giá trị thật sự khác nhau |
| `1.234` chuẩn hóa scale 2 | `UNNECESSARY` báo lỗi nếu làm tròn mới được |
| Chèn phần tử theo thứ tự đảo ngược | `TreeSet` có thể giữ đại diện khác |
| `HashSet` được duyệt nhiều lần | Không assert thứ tự không được bảo đảm |

Nếu `TreeSet` chứa kết quả đúng về số lượng nhưng cần xuất biểu diễn scale 2, hãy thêm assertion `scale() == 2` cho từng phần tử. **Đúng số lượng không chứng minh đúng biểu diễn dữ liệu.**

## 13. Các lỗi thường gặp

| Hiểu nhầm | Sự thật |
| --- | --- |
| Hai `BigDecimal` bằng nhau về số thì hash set chỉ giữ một | `HashSet` dựa vào `equals`, còn xét scale |
| `TreeSet` dùng `equals` để phát hiện trùng | `TreeSet` dùng comparator và coi `compare(...) == 0` là tương đương |
| `TreeSet` chỉ thêm khả năng sắp xếp cho `HashSet` | Với `BigDecimal`, nó còn đổi cả quy tắc đồng nhất |
| `TreeSet.contains(1.000)` chứng minh đúng đối tượng scale 3 đã được lưu | Nó chỉ chứng minh có đại diện cùng giá trị theo comparator |
| Thêm `1.00` sau `1.0` vào tree set sẽ thay đại diện | `add` trả `false` và giữ đại diện cũ |
| `HashSet` in phần tử theo thứ tự chèn | Không có cam kết về thứ tự duyệt |
| `stripTrailingZeros` luôn đưa scale về 0 | Có thể tạo scale âm, như `1E+3` |
| Comparator chuỗi `toString` tương đương so sánh số | Thứ tự chữ cái và số học có thể khác |
| Chuyển sang `double` trước khi khử trùng lặp sẽ chính xác hơn | Chuyển nhị phân có thể làm tròn và nhập nhằng các giá trị khác nhau |
| Chọn `HashSet` hay `TreeSet` là chuyện hiệu năng thuần túy | Nó có thể thay đổi cả kết quả và hợp đồng dữ liệu |

**Kết luận:** Hãy xác định rõ “trùng” nghĩa là **cùng giá trị số học** hay **cùng giá trị và scale**. `HashSet<BigDecimal>` mặc định phù hợp với quan hệ `equals`; `TreeSet<BigDecimal>` mặc định phù hợp với quan hệ `compareTo == 0`. Nếu cần một quan hệ khác, chuẩn hóa dữ liệu hoặc cung cấp comparator tường minh, rồi kiểm thử cả `size`, `contains`, `remove`, thứ tự và scale của đầu ra.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-practice-009">Parse và format cùng một giá trị theo nhiều locale</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành: Parse và format cùng một giá trị số theo nhiều Locale trong Java

Trong hệ thống có người dùng ở nhiều quốc gia, **một giá trị số học có thể được viết thành những chuỗi rất khác nhau**. Ví dụ cùng giá trị một triệu hai trăm ba mươi tư nghìn năm trăm sáu mươi bảy phẩy tám mươi chín:

| Locale | Ví dụ chuỗi hiển thị | Dấu nhóm hàng nghìn | Dấu thập phân |
|---|---|---|---|
| `en-US` (Mỹ) | `1,234,567.89` | `,` | `.` |
| `de-DE` (Đức) | `1.234.567,89` | `.` | `,` |
| `fr-FR` (Pháp) | `1 234 567,89` | khoảng trắng hẹp không ngắt (`U+202F`) trong cấu hình CLDR thông dụng | `,` |
| `vi-VN` (Việt Nam) | `1.234.567,89` | `.` | `,` |

Trong đó, dấu cách của ví dụ Pháp có thể rất khó phân biệt bằng mắt với dấu cách thường. Quy ước locale của nền tảng có thể được cập nhật theo phiên bản dữ liệu CLDR/JDK và cấu hình nhà cung cấp locale. Không nên sao chép cứng một chuỗi được format rồi giả định nó bất biến giữa mọi môi trường.

**Mục tiêu thực hành:** bắt đầu từ **một** `BigDecimal`, format theo bốn locale, parse ngược bằng đúng locale, so sánh giá trị số học, đồng thời nhận biết vì sao parse với sai locale, parse một phần hoặc format đã làm tròn có thể làm thay đổi nghĩa của dữ liệu.

## 1. Phân biệt giá trị số học và chuỗi trình bày

`BigDecimal` mang giá trị thập phân; `Locale` quyết định quy ước trình bày và phân tích chuỗi theo ngôn ngữ/vùng. `Locale` không biến `1234567.89` thành một số học khác.

```java
BigDecimal amount = new BigDecimal("1234567.89");
```

Giá trị trên có thể format ra `1,234,567.89` trong `en-US`, `1.234.567,89` trong `de-DE`. Hai chuỗi không giống nhau, nhưng khi được parse theo đúng quy ước chúng đều có thể cho lại cùng giá trị số học.

Chuỗi hiển thị dành cho **con người** khác biểu diễn trao đổi giữa máy với máy. Với một trường decimal truyền bằng chuỗi trong giao thức, nếu giao thức yêu cầu định dạng trung lập như `1234567.89`, hãy dùng `toPlainString()` và `new BigDecimal(String)` theo đúng hợp đồng thay vì dựa vào locale đang cài ở hệ điều hành.

**Quan trọng:** `BigDecimal.toString()` hay `toPlainString()` không tự thay dấu thập phân thành dấu phẩy theo ngôn ngữ. Muốn format/parse theo locale, dùng công cụ tương ứng trong `java.text` hoặc một API khác có quy tắc được thiết kế tường minh.

## 2. Bộ API cần dùng trong Java 17+

Những lớp chính:

- `Locale` (`java.util.Locale`): định danh quy tắc ngôn ngữ/vùng như `Locale.US`, `Locale.GERMANY`, `Locale.FRANCE` và `Locale.forLanguageTag("vi-VN")`.
- `NumberFormat` (`java.text.NumberFormat`): factory cho việc format và parse giá trị số theo locale. `getNumberInstance(locale)` dành cho số thông thường; `getCurrencyInstance(locale)` phục vụ định dạng tiền tệ với các quy tắc khác.
- `DecimalFormat` (`java.text.DecimalFormat`): cung cấp cấu hình scale hiển thị, dấu nhóm, `RoundingMode` và đặc biệt `setParseBigDecimal(true)` để parse thành `BigDecimal` khi thích hợp.
- `DecimalFormatSymbols`: các ký hiệu như dấu thập phân, dấu nhóm, chữ số và ký hiệu số âm của một locale.
- `ParsePosition`: vị trí bắt đầu và kết thúc parse. Dùng để biết parser đã **tiêu thụ hết chuỗi** hay chỉ đọc một tiền tố hợp lệ.
- `BigDecimal`: đại diện chính xác cho giá trị decimal, thuận tiện cho các phép tính thập phân cần kiểm soát làm tròn.

Ví dụ lấy bộ formatter:

```java
Locale locale = Locale.GERMANY;
NumberFormat generic = NumberFormat.getNumberInstance(locale);
```

`NumberFormat` có kiểu trừu tượng. Các locale JDK phổ biến thường trả về `DecimalFormat` từ `getNumberInstance`; muốn dùng `setParseBigDecimal(true)`, chương trình nên kiểm tra kiểu trả về thay vì ép kiểu mà không nêu giả định.

```java
NumberFormat generic = NumberFormat.getNumberInstance(locale);
if (!(generic instanceof DecimalFormat format)) {
    throw new IllegalStateException(
            "DecimalFormat required for locale " + locale);
}
format.setParseBigDecimal(true);
```

Đây là ví dụ về cú pháp pattern matching cho `instanceof`, dùng được từ Java 17.

## 3. Format một BigDecimal theo từng Locale

Đối với bài thực hành có đúng hai vị trí thập phân, có thể cấu hình:

```java
DecimalFormat f =
        (DecimalFormat) NumberFormat.getNumberInstance(Locale.US);

f.setGroupingUsed(true);
f.setMinimumFractionDigits(2);
f.setMaximumFractionDigits(2);
f.setRoundingMode(RoundingMode.HALF_UP);

String display = f.format(new BigDecimal("1234567.89"));
// "1,234,567.89" với quy ước en-US phổ biến
```

Các lệnh có ý nghĩa khác nhau:

| Cấu hình | Ý nghĩa |
|---|---|
| `setGroupingUsed(true)` | Cho phép dấu phân nhóm chữ số như nghìn/triệu |
| `setMinimumFractionDigits(2)` | Hiển thị ít nhất hai chữ số phần thập phân; có thể thêm số 0 |
| `setMaximumFractionDigits(2)` | Không hiển thị quá hai chữ số phần thập phân; có thể **làm tròn** |
| `setRoundingMode(HALF_UP)` | Chọn cách làm tròn tại giới hạn phần thập phân |
| `setParseBigDecimal(true)` | Khi parse qua `DecimalFormat`, ưu tiên kết quả `BigDecimal` thay vì `Double`/`Long` |

**Cảnh báo:** định dạng để hiển thị có thể thay đổi giá trị số học khi giới hạn chữ số. Chẳng hạn `1.005` sẽ hiển thị thành `1.01` với cấu hình hai chữ số và `HALF_UP`. Không được suy ngược rằng chuỗi hiển thị luôn tái tạo đầy đủ giá trị số học gốc.

Nên xác định scale/mode theo quy tắc hiển thị hoặc nghiệp vụ đã phê duyệt. Đừng mặc định mọi số trong mọi miền đều cần hai chữ số.

### Locale khác nhau có dấu phân cách khác nhau

Giả sử sử dụng bốn formatter:

```java
NumberFormat us =
        NumberFormat.getNumberInstance(Locale.US);
NumberFormat de =
        NumberFormat.getNumberInstance(Locale.GERMANY);
NumberFormat fr =
        NumberFormat.getNumberInstance(Locale.FRANCE);
NumberFormat vi =
        NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
```

Không nên tự thay dấu trong một chuỗi `en-US` bằng `String.replace(",", ".")` rồi tiếp tục thay `"."` thành `","`. Dễ thay nhầm cả dấu nhóm và dấu thập phân, xử lý sai ký hiệu âm, khoảng trắng đặc biệt và chữ số của các hệ viết khác. Hãy để `NumberFormat` sử dụng ký hiệu của đúng locale.

## 4. Parse đúng Locale và giữ độ chính xác BigDecimal

Để parse chuỗi có dấu nhóm theo một locale, hãy dùng formatter được tạo từ **locale của nguồn dữ liệu**. Ví dụ:

```java
DecimalFormat german =
        (DecimalFormat) NumberFormat.getNumberInstance(Locale.GERMANY);
german.setParseBigDecimal(true);

Number value = german.parse("1.234.567,89");
// Với cấu hình này, value là BigDecimal mang giá trị 1234567.89
```

Đoạn trên có thể ném `ParseException` và phải đặt trong phương thức khai báo/ứng xử với ngoại lệ. Chương trình hoàn chỉnh ở phần sau dùng `ParsePosition` để kiểm tra hết chuỗi.

**Vì sao `setParseBigDecimal(true)` quan trọng?** Nếu không cấu hình, `NumberFormat.parse` trả về `Number` và một triển khai có thể trả `Long` hoặc `Double` tùy dữ liệu. `Double` là kiểu nhị phân gần đúng, có thể mất độ chính xác decimal. `DecimalFormat#setParseBigDecimal(true)` giúp giữ số decimal từ chuỗi được parse thành `BigDecimal` phù hợp với yêu cầu thực hành.

Ngay cả khi formatter đang được cấu hình hiển thị tối đa hai chữ số, thao tác parse là **một hoạt động riêng**: cần kiểm tra chuỗi đầu vào, các ký hiệu được nhận diện và giới hạn của API, không được đồng nhất tính hợp lệ của chuỗi với cấu hình hiển thị.

### Parse một phần là một bẫy rất phổ biến

Ví dụ chuỗi `"1,234.50foo"` trong `en-US`:

```java
DecimalFormat us =
        (DecimalFormat) NumberFormat.getNumberInstance(Locale.US);
us.setParseBigDecimal(true);

ParsePosition pos = new ParsePosition(0);
Number parsed = us.parse("1,234.50foo", pos);

// parsed = 1234.50 nhưng pos.getIndex() = 8,
// trong khi chiều dài chuỗi đầu vào là 11.
```

Nếu chỉ kiểm tra `parsed != null`, dữ liệu dư `foo` có thể bị bỏ qua. Do đó, để kiểm tra chuỗi đã được parse **hết**, cần:

```java
if (parsed == null || pos.getIndex() != input.length()) {
    throw new IllegalArgumentException("Invalid number for locale");
}
```

Kiểm tra hết chuỗi **không làm NumberFormat trở thành trình phân tích nghiêm ngặt mọi cú pháp**. Một số formatter khá dễ dãi về vị trí nhóm chữ số; tùy yêu cầu, ứng dụng có thể cần kiểm tra pattern được phép, các ký tự Unicode, số chữ số, khoảng giá trị và quy tắc nghiệp vụ. Khi cần chuỗi **đúng một dạng trình bày chuẩn**, có thể format lại giá trị đã parse rồi so sánh với đầu vào sau khi áp dụng chính sách chuẩn hóa được định nghĩa rõ.

## 5. Không tự đoán Locale từ dấu phẩy/dấu chấm

Chuỗi `"1,234"` rất dễ gây nhầm:

| Locale parse | Cách hiểu hợp lệ theo quy ước phổ biến | Giá trị |
|---|---|---:|
| `en-US` | Dấu phẩy là dấu nhóm | 1234 |
| `de-DE` | Dấu phẩy là dấu thập phân | 1.234 |

Cả hai lần parse có thể tiêu thụ **toàn bộ** chuỗi và không báo lỗi, nhưng cho hai giá trị khác nhau! Vì vậy, điều kiện “parse hết chuỗi” là cần thiết cho dữ liệu chặt chẽ, nhưng **không chứng minh locale được chọn là đúng**.

Một số lời khuyên:

1. **Biết locale của nguồn**: từ lựa chọn của người dùng, hồ sơ locale của form, định dạng file nhập, hợp đồng API hoặc cột metadata.
2. **Không tự đoán chỉ từ IP/quốc gia của máy chủ**: ngôn ngữ trình bày và nguồn dữ liệu có thể khác nhau.
3. **Dữ liệu trao đổi giữa hệ thống nên có định dạng chuẩn**: ví dụ giá trị decimal ASCII không có dấu nhóm và dùng dấu chấm, theo hợp đồng rõ ràng.
4. Nếu nhập dữ liệu từ nhiều locale trong cùng một đợt, **lưu thông tin locale theo nguồn**, hoặc dùng bước xác nhận/cấu hình nhập rõ ràng.
5. Nếu dữ liệu mơ hồ, **không silently chọn** một locale để “thành công bằng mọi giá”; yêu cầu xác nhận hoặc dùng định dạng được đặc tả.

### Chuỗi Pháp và dấu cách Unicode

`fr-FR` có thể dùng khoảng trắng hẹp không ngắt `U+202F` để nhóm nghìn. Nó khác `U+0020` (dấu cách thường) và `U+00A0` (khoảng trắng không ngắt). Một đầu vào nhìn giống `"1 234 567,89"` chưa chắc chứa đúng code point mà formatter mong đợi.

Nếu parse thất bại hoặc chỉ parse tiền tố, kiểm tra code point thực tế thay vì chỉ nhìn trên màn hình. Đừng tùy tiện xóa **mọi** khoảng trắng trước khi parse: bạn có thể làm cho dữ liệu sai cú pháp trở nên hợp lệ hoặc vô tình xóa những ký tự mang nghĩa.

## 6. Chương trình Java 17+ độc lập: format và parse qua bốn locale

Tạo tệp `LocaleNumberDemo.java`. Chương trình dùng **một** `BigDecimal` chính xác, format theo `en-US`, `de-DE`, `fr-FR`, `vi-VN` rồi parse ngược. Các khoảng trắng Unicode đặc biệt được chuyển thành nhãn hiển thị để kết quả trong terminal dễ phân biệt.

```java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.List;
import java.util.Locale;

public class LocaleNumberDemo {

    private static DecimalFormat decimalFor(Locale locale) {
        NumberFormat numberFormat =
                NumberFormat.getNumberInstance(locale);
        if (!(numberFormat instanceof DecimalFormat df)) {
            throw new IllegalStateException(
                    "DecimalFormat required: " + locale);
        }
        df.setGroupingUsed(true);
        df.setMinimumFractionDigits(2);
        df.setMaximumFractionDigits(2);
        df.setRoundingMode(RoundingMode.HALF_UP);
        df.setParseBigDecimal(true);
        return df;
    }

    private static BigDecimal parseFully(
            String input, Locale locale) {
        if (input == null) {
            throw new IllegalArgumentException("Null input");
        }
        DecimalFormat df = decimalFor(locale);
        ParsePosition position = new ParsePosition(0);
        Number number = df.parse(input, position);

        if (!(number instanceof BigDecimal decimal)
                || position.getIndex() != input.length()) {
            throw new IllegalArgumentException(
                    "Invalid number for " + locale.toLanguageTag());
        }
        return decimal;
    }

    private static String visible(String formatted) {
        return formatted.replace("\u202F", "<NNBSP>")
                .replace("\u00A0", "<NBSP>");
    }

    private static String tryParse(String input, Locale locale) {
        try {
            return parseFully(input, locale).toPlainString();
        } catch (IllegalArgumentException ex) {
            return "REJECTED";
        }
    }

    public static void main(String[] args) {
        BigDecimal original = new BigDecimal("1234567.89");
        List<Locale> locales = List.of(
                Locale.US,
                Locale.GERMANY,
                Locale.FRANCE,
                Locale.forLanguageTag("vi-VN"));

        System.out.println("Original: " + original.toPlainString());

        for (Locale locale : locales) {
            DecimalFormat formatter = decimalFor(locale);
            String shown = formatter.format(original);
            BigDecimal parsed = parseFully(shown, locale);

            System.out.println(locale.toLanguageTag()
                    + " | " + visible(shown)
                    + " | " + parsed.toPlainString()
                    + " | sameValue="
                    + (parsed.compareTo(original) == 0));
        }

        System.out.println("Wrong locale: "
                + tryParse("1.234,50", Locale.US));
        System.out.println("Trailing text: "
                + tryParse("1,234.50foo", Locale.US));

        System.out.println("Ambiguous en-US: "
                + parseFully("1,234", Locale.US).toPlainString());
        System.out.println("Ambiguous de-DE: "
                + parseFully("1,234", Locale.GERMANY).toPlainString());

        BigDecimal needsRounding = new BigDecimal("1.005");
        String rounded = decimalFor(Locale.US).format(needsRounding);
        BigDecimal afterRoundTrip = parseFully(rounded, Locale.US);

        System.out.println("Original 1.005: "
                + needsRounding.toPlainString());
        System.out.println("Formatted 1.005: " + rounded);
        System.out.println("Parsed after format: "
                + afterRoundTrip.toPlainString());
        System.out.println("Same after rounding: "
                + (needsRounding.compareTo(afterRoundTrip) == 0));
    }
}
```

Biên dịch và chạy:

```bash
javac LocaleNumberDemo.java
java LocaleNumberDemo
```

Một kết quả tham chiếu theo dữ liệu locale CLDR phổ biến của JDK:

```text
Original: 1234567.89
en-US | 1,234,567.89 | 1234567.89 | sameValue=true
de-DE | 1.234.567,89 | 1234567.89 | sameValue=true
fr-FR | 1<NNBSP>234<NNBSP>567,89 | 1234567.89 | sameValue=true
vi-VN | 1.234.567,89 | 1234567.89 | sameValue=true
Wrong locale: REJECTED
Trailing text: REJECTED
Ambiguous en-US: 1234
Ambiguous de-DE: 1.234
Original 1.005: 1.005
Formatted 1.005: 1.01
Parsed after format: 1.01
Same after rounding: false
```

`<NNBSP>` trong stdout là **nhãn do phương thức `visible` tạo**, không phải chuỗi format thực tế. Thực tế `fr-FR` thường dùng ký tự Unicode `U+202F` ở vị trí đó; một môi trường dùng ký hiệu nhóm khác có thể in nhãn/chuỗi khác. Bảng kết quả cần được đối chiếu với locale provider/JDK đang chạy, còn các nguyên tắc về chọn locale, parse hết chuỗi và làm tròn vẫn không đổi.

**Phân tích kết quả:**

- Trong bốn vòng lặp, cùng `BigDecimal("1234567.89")` tạo các chuỗi địa phương hóa khác nhau. Parse bằng **đúng locale** cho lại giá trị decimal bằng nhau về số học.
- Parse `"1.234,50"` theo `en-US` chỉ đọc được một tiền tố theo cách hiểu của Mỹ; `parseFully` phát hiện chưa đọc hết và từ chối.
- `"1,234.50foo"` có một tiền tố số hợp lệ nhưng phần đuôi `foo` không hợp lệ, cũng bị từ chối.
- `"1,234"` được parse hoàn toàn theo cả Mỹ và Đức mà thành **hai số khác nhau**. Hệ thống phải biết đúng locale của input.
- Với `1.005`, formatter giới hạn **hai chữ số sau dấu** và dùng `HALF_UP` nên phát ra `1.01`. Parse chuỗi hiển thị không thể khôi phục chữ số thứ ba đã bị bỏ.

## 7. Phân biệt round-trip về giá trị, về scale và về chuỗi

Một “round-trip” có thể nhắm đến ba mục tiêu:

1. **Giá trị toán học bằng nhau**: `original.compareTo(parsed) == 0` với `BigDecimal`. Hữu ích khi scale không mang nghĩa nghiệp vụ.
2. **Giá trị và scale trùng nhau**: `original.equals(parsed)`. Hữu ích khi cách biểu diễn thập phân cũng quan trọng.
3. **Chuỗi nhập và chuỗi format lại giống nhau**: `input.equals(formatter.format(parsed))`. Đây là điều kiện kiểm tra **dạng biểu diễn chuẩn** theo formatter, mạnh hơn việc parse hết chuỗi nhưng cũng có thể từ chối các cách nhập hợp lệ khác như không có dấu nhóm.

Ví dụ:

```java
BigDecimal a = new BigDecimal("1.0");
BigDecimal b = new BigDecimal("1.00");

System.out.println(a.compareTo(b) == 0); // true
System.out.println(a.equals(b));         // false
```

Phần scale không chỉ là kiểu trình bày: với `BigDecimal.equals()`, scale có tham gia so sánh. Nếu chương trình chỉ cần số học bằng nhau, dùng `compareTo() == 0`; nếu scale là một phần của hợp đồng, hãy kiểm tra scale.

**Chú ý với validation canonical:** formatter có thể chủ động thêm số 0, thêm dấu nhóm hoặc chuyển dấu/kiểu chữ số, vì vậy so sánh chuỗi raw với chuỗi chuẩn phải là quyết định được nêu trong UX/API. Không ép người nhập phải dùng đúng mọi dấu nhóm và khoảng trắng nếu yêu cầu thực tế cho phép nhiều cách nhập hợp lệ.

## 8. Khi nào parse là đủ, khi nào cần thêm validation?

`DecimalFormat.parse` là bộ phân tích số địa phương hóa, **không phải bộ kiểm tra toàn bộ quy tắc nghiệp vụ của form**. Một giá trị parse thành công vẫn có thể không hợp lệ vì những lý do khác.

Một pipeline đáng tin cậy thường có:

1. **Nguồn locale xác định rõ:** lấy từ ngữ cảnh input; không dùng `Locale.getDefault()` một cách ngầm nếu xử lý dữ liệu từ nhiều người dùng.
2. **Parse theo locale:** dùng đúng formatter và `setParseBigDecimal(true)` khi cần decimal exact.
3. **Kiểm tra cú pháp:** parse phải tiêu thụ hết chuỗi, sau đó áp dụng yêu cầu về ký hiệu, dấu nhóm, số chữ số, ký tự được phép hoặc format canonical tùy trường hợp.
4. **Kiểm tra miền giá trị:** không âm, lớn hơn 0, không vượt giới hạn, không có phần thập phân nếu là số lượng nguyên, v.v.
5. **Chính sách lượng hóa:** nếu cần làm tròn đến đơn vị nào, phải quy định scale/mode và thời điểm áp dụng. Formatter giao diện **không tự quyết định** quy tắc lưu trữ.
6. **Lưu/trao đổi dạng trung lập:** lưu giá trị decimal hoặc chuỗi máy theo hợp đồng, giữ thông tin locale/đơn vị nếu cần tái hiện input hay audit.

### Ví dụ validation số lượng nguyên

```java
BigDecimal parsed = parseFully("1.234", Locale.GERMANY);
// Với de-DE, đây là 1234 theo dấu nhóm.

int quantity = parsed.intValueExact();
// Thành công nếu giá trị nguyên và vừa miền int.
```

Nếu một người nhập `"1,234"` theo `de-DE`, kết quả lại là `1.234` và `intValueExact()` sẽ ném `ArithmeticException` do phần lẻ. Cả hai hành vi đều hợp lý theo định dạng Đức nhưng cần đúng mục đích trường nhập.

Khi parse tiền tệ, `getCurrencyInstance(locale)` có thể format kèm ký hiệu tiền nhưng **locale trình bày không phải mã tiền tệ đáng tin cậy để tự suy ra nghĩa của khoản tiền**; ví dụ đơn vị ISO 4217 hoặc mã tiền thực tế nên nằm trong dữ liệu theo hợp đồng.

### Format dữ liệu với vai trò trình bày

Một phép tính thập phân có thể giữ nhiều chữ số để tính toán và chỉ định dạng ít chữ số trên UI. Nếu sau đó lấy chuỗi UI đã làm tròn đem parse lại rồi dùng cho phép tính tiếp, bạn đã làm mất thông tin. Tránh coi chuỗi được format là **bản gốc** cho luồng tính toán nếu không có quyết định nghiệp vụ rõ ràng.

## 9. Thread safety, hiệu năng và lựa chọn API

`NumberFormat`/`DecimalFormat` là những lớp **có trạng thái, mutable và nói chung không an toàn để chia sẻ giữa nhiều luồng mà không đồng bộ hóa**. Khi xử lý nhiều request song song:

- Không giữ một `static DecimalFormat` dùng chung cho toàn bộ yêu cầu rồi liên tục thay locale, pattern, rounding và parse flags trên nó.
- Có thể tạo một formatter riêng theo lượt dùng, theo luồng hoặc áp dụng kỹ thuật cache an toàn với trạng thái bất biến/copy phù hợp.
- `Locale` là mô tả quy ước; bản thân lựa chọn `Locale` không thay thế việc quản lý formatter.
- Cấu hình formatter rõ ràng và kiểm thử hành vi với JDK/locale provider của môi trường triển khai.

Nếu chỉ cần trao đổi số decimal giữa các dịch vụ, `BigDecimal.toPlainString()`/`new BigDecimal(String)` hoặc định dạng JSON numeric theo hợp đồng thường đơn giản hơn việc tạo formatter địa phương hóa ở tầng transport. Chỉ dùng parsing locale tại ranh giới **nhập chuỗi dành cho con người**.

## 10. Lỗi thường gặp

1. **Dùng `Double.parseDouble("1.234,56")` để đọc số Đức.** API này không hiểu dấu nhóm/thập phân theo locale đó; cần parse theo locale hoặc định dạng máy chuẩn.
2. **Dùng `NumberFormat.getInstance()` không truyền locale.** Kết quả phụ thuộc default locale của tiến trình, dễ thay đổi giữa máy dev, máy test và production.
3. **Dựa vào `NumberFormat.parse` mà không kiểm tra phần dư.** Parser có thể chỉ đọc tiền tố hợp lệ và bỏ qua ký tự phía sau.
4. **Parse bằng sai locale nhưng vẫn ra số.** Chuỗi `"1,234"` là ví dụ nguy hiểm: hai locale hiểu thành hai giá trị hoàn toàn khác nhau.
5. **Cho rằng parse hết chuỗi tương đương validation nghiêm ngặt.** Vẫn cần kiểm tra pattern/miền dữ liệu nếu nghiệp vụ yêu cầu.
6. **Không bật `setParseBigDecimal(true)` khi cần decimal exact.** Kết quả `Number` có thể là kiểu khác, gây mất độ chính xác trong phép tính tiếp.
7. **Format rồi parse lại mà kỳ vọng mọi chữ số được bảo toàn.** Formatter có thể làm tròn bằng `setMaximumFractionDigits`.
8. **Dùng chuỗi `fr-FR` với dấu cách thường thay vì dấu Unicode đúng.** Bằng mắt khó phân biệt; cần kiểm tra code point thực tế.
9. **Cố tự thay dấu chấm/phẩy thủ công.** Có thể thay cả dấu nhóm và dấu thập phân, hoặc không xử lý âm/Unicode.
10. **Chia sẻ `DecimalFormat` mutable giữa các luồng.** Cấu hình và kết quả có thể bị can thiệp đồng thời.
11. **Hiểu locale là đơn vị tiền.** Cần quản lý currency code, đơn vị và chính sách số học độc lập với định dạng hiển thị.
12. **Không kiểm thử với nhiều JDK/provider.** Ký hiệu locale thay đổi theo dữ liệu chuẩn, nên tránh hard-code những ký tự không được hợp đồng đảm bảo.

## 11. Tự kiểm tra

**Câu 1.** Cùng giá trị `1234.5` hiển thị khác nhau như thế nào trong `en-US` và `de-DE`?  
**Đáp án:** Ví dụ phổ biến: `1,234.50` và `1.234,50` nếu bật grouping và buộc hiển thị hai vị trí thập phân.

**Câu 2.** `"1,234"` được hiểu giống nhau trong Mỹ và Đức không?  
**Đáp án:** Không. `en-US` thường parse thành `1234`, `de-DE` thành `1.234`. Cần biết locale của input, không thể suy ra chỉ từ kết quả parse thành công.

**Câu 3.** Vì sao cần `ParsePosition` thay vì chỉ kiểm tra `NumberFormat.parse` không ném lỗi?  
**Đáp án:** Parser có thể đọc một tiền tố hợp lệ rồi bỏ qua phần đuôi. `position.getIndex() == input.length()` giúp xác nhận đã tiêu thụ hết chuỗi.

**Câu 4.** Parse hết chuỗi có bảo đảm người dùng viết dấu nhóm theo đúng mẫu mong muốn không?  
**Đáp án:** Không hoàn toàn; `NumberFormat` có thể tương đối dễ dãi. Nếu hợp đồng cần cú pháp canonical, phải có kiểm tra bổ sung.

**Câu 5.** Vì sao nên bật `DecimalFormat#setParseBigDecimal(true)` trong bài toán tính toán decimal chính xác?  
**Đáp án:** Để parse thành `BigDecimal` thay vì phải chấp nhận một kết quả `Double` gần đúng hoặc `Long` tùy giá trị.

**Câu 6.** Format `1.005` bằng `HALF_UP` và tối đa hai chữ số, sau đó parse lại, có được giá trị gốc không?  
**Đáp án:** Không. Chuỗi hiển thị là `1.01`; chữ số đã bị làm tròn không thể được phục hồi.

**Câu 7.** Vì sao dấu cách trong số kiểu Pháp đôi khi không parse được dù trông giống mẫu?  
**Đáp án:** Có các ký tự khoảng trắng Unicode khác nhau như `U+0020`, `U+00A0` và `U+202F`. Phải dùng đúng quy ước của formatter hoặc một bước chuẩn hóa được đặc tả.

**Câu 8.** Khi truyền giá trị decimal giữa hai microservice, nên dùng locale của máy chủ để format chuỗi giao thức không?  
**Đáp án:** Không, trừ khi giao thức quy định như vậy. Nên dùng định dạng số trung lập, không có dấu nhóm, với dấu thập phân/mã đơn vị đã được xác định rõ.

**Ghi nhớ:** `format(value, locale)` tạo cách **trình bày**; `parse(text, locale)` diễn giải chuỗi theo **locale của nguồn**. Để tránh sai dữ liệu, hãy giữ `BigDecimal` làm giá trị gốc, chọn đúng locale, kiểm tra parse tiêu thụ hết chuỗi và xác định rõ khi nào được phép làm tròn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-practice-010">Mô phỏng số ngẫu nhiên có thể tái lập bằng seed</a>

<details>
<summary>Xem chi tiết</summary>

# Mô phỏng số ngẫu nhiên có thể tái lập bằng seed trong Java

## 1. Tình huống thực hành và mục tiêu

Giả sử ta mô phỏng việc tung xúc xắc, tạo dữ liệu kiểm thử hoặc ước lượng π bằng phương pháp Monte Carlo. Ta muốn có ngẫu nhiên để thử nhiều tình huống, nhưng khi phát hiện lỗi, cần **chạy lại đúng các giá trị ngẫu nhiên đã dùng** để tái hiện và chẩn đoán.

Đó là mục tiêu của **mô phỏng có thể tái lập bằng seed** (*reproducible seeded simulation*). Một seed là trạng thái khởi tạo hoặc đầu vào khởi tạo cho bộ sinh số giả ngẫu nhiên (*pseudorandom number generator, PRNG*). Với cùng thuật toán, cùng seed, cùng chuỗi lời gọi và cùng điều kiện thực thi liên quan, ta có thể tạo lại cùng chuỗi đầu ra.

Ví dụ đơn giản:

```java
Random first = new Random(2026L);
Random again = new Random(2026L);

System.out.println(first.nextInt(100) == again.nextInt(100)); // true
```

Đây không phải “ngẫu nhiên thực sự” từ môi trường; dãy đầu ra được tính theo thuật toán xác định. Seed cho phép chuyển một ca kiểm thử vốn khó tái hiện thành **một ca có thể phát lại**.

Mục tiêu bài học:
- Biết dùng `java.util.Random`, `SplittableRandom` và các API Java 17 thích hợp để điều khiển seed.
- Hiểu điều kiện **cùng seed chưa đủ** để có cùng kết quả khi cách gọi hoặc phân phối khác nhau.
- Xây dựng mô phỏng Monte Carlo tính xấp xỉ π và một ví dụ tung xúc xắc.
- Kiểm tra sự tái lập, so sánh nhiều seed và tránh nhầm tính tái lập với tính đúng về thống kê.
- Biết cách lưu seed, số bước, phiên bản thuật toán, tham số và chiến lược song song để tái hiện khi lỗi xảy ra.
- Phân biệt PRNG cho mô phỏng với bộ sinh ngẫu nhiên cần bảo mật.

## 2. Seed, PRNG và trạng thái thay đổi như thế nào?

PRNG duy trì **trạng thái nội bộ**. Mỗi lần gọi phương thức sinh số, nó dựa vào trạng thái hiện tại để tạo một kết quả và cập nhật trạng thái cho lần tiếp theo.

Một mô hình khái niệm:

```text
                seed cố định
                     |
                     v
                 khởi tạo PRNG
                     |
              trạng thái S0
                     |
            nextInt / nextDouble
                     v
         đầu ra R0, trạng thái S1
                     |
            nextInt / nextDouble
                     v
         đầu ra R1, trạng thái S2
                     |
                    ...
```

Nếu hai PRNG độc lập bắt đầu từ cùng seed và thực hiện **cùng chuỗi phương thức với cùng đối số**, chúng tạo cùng kết quả theo thuật toán xác định tương ứng. Ngược lại, nếu luồng A gọi `nextInt()` ba lần còn luồng B gọi thêm một lần ở giữa, chuỗi giá trị gắn với từng bước nghiệp vụ có thể lệch dù seed giống nhau.

**Không có cam kết** rằng “cùng seed” sẽ cho cùng chuỗi khi bạn đổi từ `Random` sang `SplittableRandom`, đổi thuật toán, thay trình tự gọi, thay cách chia luồng hoặc đổi một thành phần làm tiêu thụ RNG.

Một seed **không quyết định độ chính xác thống kê** của mô phỏng: nó quyết định điểm bắt đầu của một chuỗi giả ngẫu nhiên. Kết quả một seed có thể gần giá trị lý thuyết hoặc lệch đáng kể, đặc biệt khi số mẫu nhỏ.

## 3. Chọn API phù hợp trong Java

| API | Cách dùng seed | Phạm vi phù hợp | Lưu ý |
| --- | --- | --- | --- |
| `java.util.Random` | `new Random(longSeed)` | Ví dụ, kiểm thử và mô phỏng tuần tự | Thuật toán lõi và cách cập nhật trạng thái được Java mô tả; nhiều phương thức có khả năng sinh dãy lặp lại khi cùng seed/call sequence |
| `java.util.SplittableRandom` | `new SplittableRandom(longSeed)` | Mô phỏng phân luồng/cần tách bộ sinh | Có `split()` để sinh bộ sinh con; không dùng chung một đối tượng với nhiều luồng mà không có chiến lược phù hợp |
| `java.util.random.RandomGenerator` (Java 17+) | Giao diện chung; chọn implementation thích hợp | Thiết kế API không gắn với một lớp cụ thể | Cần ghi lại **tên thuật toán/implementation**, không chỉ seed |
| `java.util.random.RandomGeneratorFactory` (Java 17+) | Chọn thuật toán có tên, tạo generator từ seed | Thí nghiệm có yêu cầu quản lý thuật toán | Không tự suy rằng hai thuật toán khác nhau cho cùng kết quả |
| `ThreadLocalRandom` | Không cho ứng dụng đặt seed bằng `setSeed` | Sinh số thuận tiện theo từng luồng khi không cần phát lại dãy bằng seed được điều khiển | Không phải lựa chọn phù hợp cho ca cần seed cụ thể |
| `SecureRandom` | Thiết kế theo nhu cầu an ninh, không coi như một PRNG mô phỏng thông thường | Token, khóa, nonce và yêu cầu mật mã | Không dùng một seed công khai cố định để sinh dữ liệu bí mật |

**Lựa chọn thực hành:** Với bài mô phỏng tuần tự và test tái lập đơn giản, `Random` với seed ghi rõ thường là lựa chọn dễ hiểu. Với mô phỏng có nhiều nhánh độc lập, `SplittableRandom` hỗ trợ tách generator, nhưng vẫn cần kiểm soát **thứ tự tách**, phần việc của từng nhánh và cấu hình chạy.

### Tạo Random từ seed

```java
long seed = 2026L;
Random rng = new Random(seed);

int die = rng.nextInt(6) + 1; // 1..6, gồm cả 1 và 6
double u = rng.nextDouble(); // [0.0, 1.0)
```

`Random.nextInt(6)` sinh các số nguyên từ 0 đến 5 với phân phối đều theo hợp đồng của API; cộng 1 để mô phỏng một xúc xắc sáu mặt. Cận trên 6 **không được bao gồm**.

Với `SplittableRandom` Java 17, có thể gọi:

```java
SplittableRandom rng = new SplittableRandom(2026L);
int die = rng.nextInt(1, 7);           // 1..6
double x = rng.nextDouble(-1.0, 1.0); // [-1.0, 1.0)
```

Các câu lệnh trên là đoạn minh họa dùng trong phương thức, cần import lớp tương ứng.

## 4. Nguyên tắc tái lập: cùng seed + cùng thao tác + cùng ngữ cảnh

Có ba cấp độ thường gặp:

**Cấp độ A — Phát lại đúng chuỗi trên một generator tuần tự.** Giữ nguyên seed, thuật toán và lời gọi. Ví dụ cùng gọi `nextInt(6)` tám lần với `Random(2026L)` cho cùng dãy tám kết quả.

**Cấp độ B — Phát lại một mô phỏng.** Ngoài generator, giữ nguyên dữ liệu đầu vào, số lần lặp, quy tắc chuyển đổi từ giá trị ngẫu nhiên sang tình huống, cách làm tròn, thứ tự các phép toán và nhánh điều kiện. Một thay đổi nhỏ, chẳng hạn thêm lần gọi RNG để mô phỏng thời tiết, có thể làm toàn bộ lượt tiếp theo khác nhau.

**Cấp độ C — Phát lại giữa môi trường/phiên bản hoặc qua nhiều luồng.** Cần kiểm soát thêm implementation, tên thuật toán, phiên bản chương trình/JDK nếu dựa vào chi tiết triển khai, cách chia luồng, số luồng, thứ tự gộp kết quả và chính sách về phép toán dấu chấm động.

Một lỗi thiết kế thường gặp:

```java
Random rng = new Random(2026L);
int first = rng.nextInt(100);
int second = rng.nextInt(100);

// Ở lần chạy khác, mã gọi thêm rng.nextInt(100) trước khi tính second.
// Giá trị gắn với "second" sẽ không còn như trước.
```

**Tính tái lập của nguồn RNG** không bảo đảm **tính tái lập của toàn bộ hệ thống** nếu ứng dụng có I/O, thời gian thực, dữ liệu bên ngoài, race condition hoặc thuật toán không xác định.

## 5. Bài thực hành 1: tung xúc xắc và phát lại kết quả

Tạo một hàm nhận seed và số lần tung. Không truyền generator dùng chung từ bên ngoài vào các ca mà bạn muốn hoàn toàn độc lập; tạo generator mới từ seed cho mỗi lượt phát lại:

```java
static int[] rollDice(long seed, int rolls) {
    if (rolls < 0) {
        throw new IllegalArgumentException("rolls must be >= 0");
    }
    Random generator = new Random(seed);
    int[] result = new int[rolls];
    for (int i = 0; i < rolls; i++) {
        result[i] = generator.nextInt(6) + 1;
    }
    return result;
}
```

Đây là một **phương thức** để đặt trong lớp Java, kèm `import java.util.Random;`. Mỗi lời gọi mới với cùng `seed` và `rolls` sẽ thực hiện cùng số lần `nextInt(6)` theo cùng thứ tự.

```java
int[] a = rollDice(2026L, 8);
int[] b = rollDice(2026L, 8);

System.out.println(Arrays.equals(a, b)); // true
```

Cần import `java.util.Arrays` khi sử dụng `Arrays.equals`. Thay seed thành 2027L có thể tạo dãy khác. Không nên ghi điều kiện cứng rằng **hai seed khác nhau luôn** sinh dãy kết quả khác nhau: đầu ra hữu hạn có thể trùng do ngẫu nhiên, nhất là khi số mẫu nhỏ hoặc miền kết quả nhỏ.

### Vì sao không tạo lại Random bên trong từng lần tung?

Đoạn code sau cho kết quả ít đa dạng hơn dự định:

```java
for (int i = 0; i < 8; i++) {
    Random rng = new Random(2026L); // Reset cùng seed ở mỗi lượt!
    int die = rng.nextInt(6) + 1;
    // die lặp lại cùng giá trị đầu tiên của generator.
}
```

Muốn tám lượt liên tiếp, **tạo một generator trước vòng lặp** và gọi nó tám lần. Nếu muốn tám kịch bản độc lập, có thể sử dụng tám seed được quản lý riêng nhưng không nên chỉ “lặp lại cùng seed” cho mỗi kịch bản.

## 6. Bài thực hành 2: mô phỏng Monte Carlo ước lượng π

Một điểm `(x, y)` được sinh đều trong hình vuông `[-1,1) × [-1,1)`. Điểm nằm trong hình tròn bán kính 1 khi:

```text
x² + y² <= 1
```

Tỉ lệ diện tích hình tròn so với hình vuông bằng `π / 4`. Nếu sinh `N` điểm đều và đếm `inside` điểm nằm trong hình tròn, ta tính:

```text
piEstimate = 4 × inside / N
```

Khi `N` lớn hơn, sai số lấy mẫu thường giảm về mặt thống kê; nhưng **một seed cố định không làm ước lượng tự trở thành giá trị π chính xác**, và không bảo đảm lần tăng `N` nào cũng cải thiện kết quả.

### Cài đặt an toàn, dễ phát lại

```java
record Estimate(long inside, int trials, double pi) {}

static Estimate estimatePi(long seed, int trials) {
    if (trials <= 0) {
        throw new IllegalArgumentException("trials must be > 0");
    }
    SplittableRandom generator = new SplittableRandom(seed);
    long inside = 0;
    for (int i = 0; i < trials; i++) {
        double x = generator.nextDouble(-1.0, 1.0);
        double y = generator.nextDouble(-1.0, 1.0);
        if (x * x + y * y <= 1.0) {
            inside++;
        }
    }
    return new Estimate(inside, trials, 4.0 * inside / trials);
}
```

Đây là **đoạn phương thức và record** cần đặt trong lớp/chương trình Java 17, có import `java.util.SplittableRandom`.

Các điểm thiết kế:
- `trials > 0` để không có chia cho 0.
- `SplittableRandom(seed)` mới được khởi tạo **ở đầu mỗi mô phỏng**, tránh nhận một generator đã bị tiêu thụ ở lời gọi trước.
- Mỗi lượt dùng chính xác **hai lần `nextDouble(-1.0,1.0)`**, theo thứ tự x rồi y.
- `inside` kiểu `long` để dễ thích nghi nếu mở rộng vòng lặp; với `int trials`, bộ đếm này không tràn trong phạm vi đã cho.
- `4.0 * inside / trials` là phép toán `double`, không phải phép chia số nguyên bị cắt phần lẻ.
- Khi chạy lại với cùng seed, trial count và cùng implementation/call order, cặp `inside`/`pi` tái hiện được.

### Đánh giá chất lượng thống kê

Trong mô phỏng này, xác suất điểm nằm trong hình tròn là `p = π / 4`. Nếu các mẫu độc lập như mô hình giả định, tỉ lệ `inside / N` có độ lệch chuẩn xấp xỉ:

\[
\sqrt{\frac{p(1-p)}{N}}
\]

Độ lệch chuẩn của `piEstimate = 4 × inside/N` xấp xỉ bốn lần giá trị trên. Đây là ước lượng **sai số lấy mẫu**, không phải bảo đảm một lượt phải nằm trong một khoảng chặt. Không kiểm thử rằng kết quả với một seed cụ thể phải “gần π hơn” kết quả seed khác. Cần nhiều seed độc lập, thống kê và kiểm định thích hợp nếu muốn đánh giá chất lượng phân phối.

## 7. Chương trình Java 17 hoàn chỉnh và kết quả đã chạy

Tạo tệp **`SeededSimulationDemo.java`**:

```java
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import java.util.SplittableRandom;

public class SeededSimulationDemo {
    record Estimate(long inside, int trials, double pi) {}

    static int[] rollDice(long seed, int rolls) {
        if (rolls < 0) throw new IllegalArgumentException("rolls must be >= 0");
        Random generator = new Random(seed);
        int[] result = new int[rolls];
        for (int i = 0; i < rolls; i++) {
            result[i] = generator.nextInt(6) + 1;
        }
        return result;
    }

    static Estimate estimatePi(long seed, int trials) {
        if (trials <= 0) throw new IllegalArgumentException("trials must be > 0");
        SplittableRandom generator = new SplittableRandom(seed);
        long inside = 0;
        for (int i = 0; i < trials; i++) {
            double x = generator.nextDouble(-1.0, 1.0);
            double y = generator.nextDouble(-1.0, 1.0);
            if (x * x + y * y <= 1.0) inside++;
        }
        return new Estimate(inside, trials, 4.0 * inside / trials);
    }

    public static void main(String[] args) {
        long seed = 2026L;
        int[] first = rollDice(seed, 8);
        int[] replay = rollDice(seed, 8);
        int[] other = rollDice(seed + 1, 8);
        System.out.println("dice=" + Arrays.toString(first));
        System.out.println("sameSeedSameDice=" + Arrays.equals(first, replay));
        System.out.println("otherSeedDice=" + Arrays.toString(other));

        Random a = new Random(seed);
        Random b = new Random(seed);
        int a0 = a.nextInt(100);
        int a1 = a.nextInt(100);
        int b0 = b.nextInt(100);
        System.out.println("sameFirstDraw=" + (a0 == b0));
        System.out.println("differentDrawPosition=" + (a1 != b0));
        System.out.println("draws=" + a0 + "," + a1 + "," + b0);

        Estimate m1 = estimatePi(seed, 10_000);
        Estimate m2 = estimatePi(seed, 10_000);
        Estimate m3 = estimatePi(seed + 1, 10_000);
        System.out.println("sameSimulation=" + m1.equals(m2));
        System.out.println("piSeed2026=" + String.format(Locale.ROOT,
                "inside=%d,trials=%d,pi=%.6f", m1.inside(), m1.trials(), m1.pi()));
        System.out.println("piSeed2027=" + String.format(Locale.ROOT,
                "inside=%d,trials=%d,pi=%.6f", m3.inside(), m3.trials(), m3.pi()));

        SplittableRandom parent1 = new SplittableRandom(seed);
        SplittableRandom parent2 = new SplittableRandom(seed);
        int child1 = parent1.split().nextInt(1, 101);
        int child2 = parent2.split().nextInt(1, 101);
        System.out.println("reproducibleSplit=" + (child1 == child2));
        System.out.println("childDraw=" + child1);

        try {
            estimatePi(seed, 0);
            throw new AssertionError("Expected invalid trial count");
        } catch (IllegalArgumentException expected) {
            System.out.println("zeroTrialsRejected=true");
        }
    }
}
```

Biên dịch và chạy (JDK 17+):

```bash
javac SeededSimulationDemo.java
java SeededSimulationDemo
```

Kết quả đã kiểm chứng bằng biên dịch `javac --release 17` và chạy chương trình:

```text
dice=[6, 5, 2, 1, 4, 5, 6, 5]
sameSeedSameDice=true
otherSeedDice=[4, 1, 1, 1, 1, 6, 3, 5]
sameFirstDraw=true
differentDrawPosition=true
draws=99,50,99
sameSimulation=true
piSeed2026=inside=7850,trials=10000,pi=3.140000
piSeed2027=inside=7877,trials=10000,pi=3.150800
reproducibleSplit=true
childDraw=72
zeroTrialsRejected=true
```

**Giải thích quan trọng:**

1. Hai lời gọi `rollDice(2026L, 8)` xây hai generator độc lập nhưng cùng seed; vì quy trình giống nhau, mảng bằng nhau.
2. `otherSeedDice` khác trong ví dụ cụ thể khi seed = 2027L. Đây là dữ liệu quan sát từ chương trình, không phải một định lý rằng mọi seed khác đều cho chuỗi khác.
3. `a` và `b` cùng seed: lượt bốc thứ nhất của mỗi generator đều ra 99. Lượt thứ hai của `a` ra 50; so sánh `a1` với lượt thứ nhất `b0` minh họa tác động của **vị trí trong dãy**.
4. Với 10.000 điểm, seed 2026L tạo 7.850 điểm trong hình tròn → π ước lượng 3.14; seed 2027L tạo 7.877 điểm → 3.1508. Hai kết quả là **mẫu mô phỏng**, không phải độ chính xác đảm bảo cho mọi seed.
5. Hai `SplittableRandom` độc lập có cùng seed và đều gọi `split()` đúng một lần, nên generator con tạo cùng kết quả (72) trong môi trường đã kiểm chứng.
6. `estimatePi(...,0)` bị từ chối bằng `IllegalArgumentException`; đây là kiểm tra đầu vào, không phải lỗi ngẫu nhiên.

## 8. Vì sao cùng seed đôi khi vẫn không cùng kết quả?

### 8.1. Khác thứ tự hoặc số lần gọi

Các lệnh sinh số tiêu thụ trạng thái. Ví dụ:

```java
Random x = new Random(123L);
Random y = new Random(123L);

int firstX = x.nextInt(100);
int secondX = x.nextInt(100);
int firstY = y.nextInt(100);
// firstX == firstY, nhưng secondX không buộc bằng firstY.
```

Nếu nhánh điều kiện phụ thuộc dữ liệu khác, hai lần chạy có thể gọi RNG số lần khác nhau. Để phát lại, cố định cả dữ liệu và **thứ tự các sự kiện**.

### 8.2. Khác thuật toán sinh số

`Random`, `SplittableRandom` và các implementation trong `java.util.random` là **các bộ sinh khác nhau**. Không so sánh kết quả seed 42 giữa hai lớp rồi kết luận một lớp có lỗi vì không trùng.

Nếu bạn muốn test hợp đồng ứng dụng độc lập thuật toán, kiểm tra các tính chất như:
- kết quả xúc xắc thuộc 1..6;
- cùng cấu hình tái lập trong thuật toán đã chọn;
- không có index vượt phạm vi;
- phân phối có tính chất thống kê phù hợp qua nhiều seed (với test được thiết kế chuẩn).

Nếu muốn tái hiện **từng giá trị chính xác xuyên phiên bản và máy**, lưu tên thuật toán, phiên bản JDK/implementation và đầu ra mẫu để kiểm soát thay đổi; nếu yêu cầu nghiêm ngặt, định nghĩa hoặc đóng băng thuật toán PRNG trong giao thức ứng dụng thay vì mặc định mọi API đều có cam kết bit-for-bit trên mọi runtime.

### 8.3. Khác tham số phân phối hoặc phương thức

`nextInt(100)` và `nextInt(200)` có cách ánh xạ đầu ra khác nhau. Ngay cả khi dùng cùng generator và seed, thay bound, thay số lần gọi `nextDouble`, chuyển sang phân phối Gaussian, hoặc chèn `nextGaussian()` cũng có thể thay chuỗi giá trị xuất hiện trong nghiệp vụ. Các API có thể tiêu thụ trạng thái nội bộ với số bước khác nhau; không tự giả định một lời gọi nghĩa là một bước PRNG chuẩn.

### 8.4. Tính không xác định bên ngoài RNG

Nếu dữ liệu nguồn được lấy theo thứ tự thay đổi (ví dụ đọc các event đến bất định, duyệt collection không có thứ tự được bảo đảm, số luồng thay đổi), luồng logic có thể tiêu thụ RNG khác thứ tự. Nếu kết quả được tổng hợp bằng `double` ở các thứ tự khác, phép cộng dấu chấm động cũng có thể tạo kết quả chữ số cuối khác.

**Seed cần thiết, nhưng không đủ** để kiểm soát mọi nguồn không xác định trong hệ thống.

## 9. Nhiều luồng: chia generator thay vì chia sẻ tùy tiện

### 9.1. Tránh phụ thuộc lịch chạy luồng

Chia sẻ một generator cho nhiều worker có thể khiến **số nào thuộc worker nào** phụ thuộc thứ tự thực thi. `Random` hỗ trợ sử dụng từ nhiều luồng theo đặc điểm API nhưng điều đó không tạo kết quả có thể dự báo cho từng task; `SplittableRandom` không được thiết kế để nhiều luồng đồng thời gọi trên cùng instance.

Một cách tốt hơn là cấp **một generator riêng cho mỗi nhánh công việc**, và xác định ánh xạ nhánh ↔ seed hoặc generator con một cách cố định.

### 9.2. Tách với SplittableRandom

```java
long rootSeed = 2026L;
SplittableRandom root = new SplittableRandom(rootSeed);

SplittableRandom taskA = root.split();
SplittableRandom taskB = root.split();

// Chuyển mỗi generator con tới đúng một task.
int a = taskA.nextInt(100);
int b = taskB.nextInt(100);
```

Mã trên là đoạn minh họa bên trong một phương thức. Để tái lập:
1. Tạo `root` từ cùng seed.
2. Gọi `split()` theo **cùng thứ tự xác định**, ví dụ tạo sẵn danh sách generator cho các task có ID cố định.
3. Gán mỗi generator con cho đúng task; **không chia sẻ** một instance giữa nhiều task.
4. Duy trì thuật toán và số lần gọi ổn định trong mỗi task.
5. Thu thập kết quả theo thứ tự xác định nếu thao tác gộp phụ thuộc thứ tự, đặc biệt với `double`.

Nếu số worker hoặc thứ tự split thay đổi, kết quả gắn với các task có thể thay đổi dù root seed không đổi. Khi cần task ID độc lập thứ tự scheduling, hãy dùng chiến lược dẫn xuất seed ổn định từ root seed và ID (với hàm mixing có thiết kế/kiểm chứng phù hợp), hoặc generator theo counter/index do ứng dụng quản lý.

### 9.3. Hai yêu cầu khác nhau: tái lập và tính độc lập thống kê

Hai generator dùng **cùng seed** và cùng thuật toán sẽ cho **cùng chuỗi**, không phải hai chuỗi mẫu độc lập. Nếu dùng cùng seed để mô phỏng 1.000 phiên chạy, bạn có thể đang lặp lại một phiên 1.000 lần.

Để đánh giá độ biến thiên giữa các phiên, dùng các luồng sinh khác nhau theo phương án được thiết kế, ví dụ quản lý seed theo ID phiên, hoặc split từ một root generator theo thứ tự xác định. Không mặc định rằng chỉ thay seed bằng `seed + i` chứng minh các luồng hoàn toàn độc lập về mọi tính chất thống kê; với mô phỏng nhạy cảm, cần cân nhắc chất lượng generator và cách chia stream.

## 10. Xây dựng test kiểm chứng sự tái lập

### 10.1. Kiểm tra phát lại byte-for-byte hoặc value-for-value

Ví dụ với mảng xúc xắc:

```java
int[] first = rollDice(2026L, 100);
int[] second = rollDice(2026L, 100);
if (!Arrays.equals(first, second)) {
    throw new AssertionError("Reproducibility violated");
}
```

Trong chương trình Java 17, cần import `java.util.Arrays`. Nếu so sánh `double` yêu cầu đúng bit, có thể dùng `Double.doubleToLongBits()` hoặc `Double.doubleToRawLongBits()` theo hợp đồng, thay vì so với epsilon khi mục tiêu là **phát lại chính xác**. Với Monte Carlo record chứa cùng `inside`/`trials` và cùng phép tính, `equals()` của record là kiểm tra phù hợp trong ví dụ.

### 10.2. Kiểm tra tính hợp lệ của phân phối

Không nhầm **reproducible** với **statistically correct**. Với xúc xắc, tối thiểu cần kiểm tra:

```text
Với mọi kết quả die trong mô phỏng:
    1 <= die <= 6
```

Muốn kiểm tra xác suất gần đồng đều, phải dùng đủ số mẫu và kiểm định thống kê phù hợp (ví dụ kiểm định chi-bình-phương với mức ý nghĩa được định nghĩa), không khẳng định sáu mặt xuất hiện đúng bằng nhau trong một dãy ngắn.

Với mô phỏng π:
- `inside` phải nằm trong `[0, trials]`.
- `piEstimate` phải nằm trong `[0,4]`.
- Với cùng seed, trials và môi trường thuật toán, kết quả phải tái hiện được.
- Để đánh giá sai số ước lượng, chạy **nhiều phiên với seed/stream khác nhau** và phân tích phân phối kết quả; không dùng một seed duy nhất để kết luận độ chính xác thống kê.

### 10.3. Test đầu vào lỗi

Kiểm thử cả:
- số lần tung âm: phải từ chối;
- số mẫu Monte Carlo bằng 0 hoặc âm: phải từ chối;
- các bound không hợp lệ khi gọi `nextInt(bound)` hoặc `nextDouble(origin,bound)`;
- số lượng mẫu đủ lớn làm tràn `int` hoặc làm cạn bộ nhớ khi thiết kế mảng chứa toàn bộ kết quả;
- seed được lưu dưới dạng văn bản: phải parse chính xác, tránh mất bit do chuyển qua `double`/định dạng số không lossless.

Không thử phân bổ mảng hàng tỷ phần tử chỉ để chứng minh cấu hình không hợp lệ; kiểm tra miền và ngân sách tài nguyên trước.

## 11. Ghi lại dữ liệu để phát lại lỗi

Trong báo cáo lỗi hoặc kết quả thí nghiệm, nên ghi:

| Thông tin | Ví dụ | Vì sao cần |
| --- | --- | --- |
| Tên bài toán/thuật toán | Monte Carlo π | Biết quy trình nào sử dụng RNG |
| Seed | `2026` | Khởi tạo lại bộ sinh |
| PRNG | `SplittableRandom` | Cùng seed nhưng khác thuật toán cho chuỗi khác |
| Mô hình và tham số | `trials = 10000`; khoảng `[-1,1)` | Bảo toàn số bước và cách ánh xạ mẫu |
| Phiên bản chương trình | commit/build/version | Tránh thay code làm lệch số lần gọi |
| JDK và môi trường (khi cần) | JDK 17, vendor | Gắn chi tiết runtime ảnh hưởng tính nhất quán |
| Chiến lược song song | task ID, seed con, thứ tự split | Phát lại đúng luồng |
| Dữ liệu đầu vào ngoài RNG | tập dữ liệu, config | Không bỏ sót nguồn không xác định |
| Kết quả/checksum | `inside = 7850` | Đối chiếu nhanh khi phát lại |

Lưu seed theo kiểu `long` hoặc chuỗi số nguyên thập phân chính xác. Không serialize seed 64 bit qua một đường trung gian chuyển thành `double` nếu có thể làm mất độ chính xác số nguyên lớn.

Nếu chạy nhiều phiên mô phỏng, ghi **seed riêng của từng phiên**, hoặc root seed cộng với cách dẫn xuất seed đã được định nghĩa. Thông tin “seed 2026” không đủ nếu số lượng task và thứ tự cấp generator chưa được ghi lại.

## 12. Thiết kế mô phỏng có thể kiểm thử và bảo trì

Một mẫu thiết kế thuận tiện là đưa seed thành tham số đầu vào rõ ràng:

```java
// Phương thức minh họa bên trong một lớp Java.
static SimulationResult simulate(long seed, SimulationConfig config) {
    Random rng = new Random(seed);
    // Sử dụng rng chỉ trong lượt simulate này.
    // Kết quả trả về chứa thống kê và, nếu cần, seed để chẩn đoán.
    throw new UnsupportedOperationException("Ví dụ thiết kế API");
}
```

Đoạn trên **chỉ minh họa hình dạng API**, không phải chương trình đầy đủ (`SimulationResult` và `SimulationConfig` là kiểu do ứng dụng phải tự định nghĩa). Trong phần code chạy độc lập phía trên, `estimatePi(seed,trials)` chính là triển khai cụ thể của ý tưởng này.

Các nguyên tắc:
1. **Seed là đầu vào**, không giấu trong `System.currentTimeMillis()` hoặc một singleton không ai biết trạng thái.
2. Generator thuộc vòng đời của **một lượt mô phỏng**; không tái sử dụng ngoài ý muốn giữa các ca test.
3. Hàm mô phỏng cố gắng không có I/O hoặc tác dụng phụ ngoài, giúp dễ chạy lại và đối chiếu.
4. Mọi quy tắc chuyển đầu ra PRNG thành dữ liệu nghiệp vụ nên được định nghĩa rõ.
5. Nếu cần phân luồng, quản lý seed/nhánh trước khi submit task và ghi mapping ID.
6. Có test **tính xác định**, **giá trị hợp lệ** và **kiểm thử thống kê** tách biệt.

Với bài thực hành hoặc CI, cố định seed để một lỗi không xuất hiện ngẫu nhiên hôm nay rồi biến mất ngày mai. Để tìm thêm lỗi trong hệ thống sản xuất, có thể chạy thêm các seed khác nhau, nhưng **luôn ghi seed gây thất bại**.

## 13. Sai lầm phổ biến và cách phòng tránh

1. **Tạo `new Random()` không truyền seed rồi nghĩ có thể tái hiện.** Có thể mỗi lần khởi tạo nhận seed khác; truyền seed rõ ràng và log seed.
2. **Tạo `new Random(sameSeed)` trong mỗi vòng lặp.** Bạn cứ lấy lại kết quả đầu của cùng dãy; giữ một generator cho toàn bộ mô phỏng.
3. **Tưởng cùng seed luôn cho cùng kết quả khi đổi RNG.** Thuật toán có thể khác; ghi lại tên lớp/thuật toán.
4. **Thêm một lời gọi ngẫu nhiên vào nhánh phụ mà không cập nhật golden test.** Vị trí tiêu thụ thay đổi sẽ làm các mẫu sau lệch; cần kiểm soát flow hoặc thiết kế các stream độc lập.
5. **Chia sẻ một generator cho nhiều luồng và mong từng task luôn nhận cùng mẫu.** Scheduling có thể đổi thứ tự gọi; cấp generator theo task và cấu trúc split xác định.
6. **Cho rằng hai seed khác nhau nhất thiết tạo dữ liệu khác nhau.** Dãy hữu hạn có thể trùng; không dùng bất đẳng thức giữa hai seed làm invariant bắt buộc.
7. **Lấy một seed cố định làm bằng chứng chất lượng mô phỏng.** Nó chỉ đảm bảo phát lại; phải đánh giá nhiều phiên/mẫu để kiểm tra thống kê.
8. **Dùng `SecureRandom` với seed cố định để tạo token có thể phát lại.** Điều này trái với mục đích bảo mật; dùng `SecureRandom` đúng ngữ cảnh bảo mật và không làm lộ entropy.
9. **Không lưu phiên bản/thuật toán và thay cách chạy song song.** Seed đơn lẻ không đủ để tái hiện hệ thống đã thay đổi.
10. **Không ghi bound/đơn vị của mẫu.** `nextInt(6)` cho 0..5, không phải 1..6; `nextDouble(-1,1)` không bao gồm cận trên.
11. **Giữ tất cả kết quả trong mảng khổng lồ dù chỉ cần thống kê.** Có thể tính streaming như bộ đếm `inside` để tiết kiệm bộ nhớ và phòng tràn chỉ số.
12. **Nhầm “ngẫu nhiên khó dự đoán” với PRNG seeded.** PRNG dùng để mô phỏng có thể phát lại, không phải mặc định phù hợp cho mục tiêu mật mã.

## 14. Bài tập tự kiểm tra

**Bài 1 — phát lại xúc xắc.** Tại sao `rollDice(2026L,8)` chạy hai lần trả cùng dãy?  
**Đáp án:** mỗi lần tạo một `Random` từ cùng seed và gọi `nextInt(6)` đúng tám lần, theo cùng thứ tự.

**Bài 2 — hai seed khác nhau.** Có thể khẳng định hai seed khác nhau không bao giờ cho cùng một lần tung xúc xắc không?  
**Đáp án:** không. Dù trạng thái khởi tạo khác, đầu ra có sáu khả năng nên nhiều seed có thể cùng tạo một kết quả (hoặc một chuỗi ngắn).

**Bài 3 — thay đổi vị trí gọi.** Hai generator cùng seed nhưng một bên gọi `nextInt(100)` thêm một lần trước bước quan trọng. Giá trị bước đó có nhất thiết trùng không?  
**Đáp án:** không. Hai chương trình đang lấy đầu ra từ các vị trí khác nhau trong dãy.

**Bài 4 — kiểm tra π.** Mô phỏng 10.000 điểm, có 7.850 điểm trong hình tròn. Ước lượng π bao nhiêu?  
**Đáp án:** `4.0 × 7850 / 10000 = 3.14`.

**Bài 5 — kết quả giống nhau không chứng minh chất lượng.** Nếu 100 lần chạy với cùng seed đều ra π = 3.14, đó có phải 100 mẫu độc lập để đánh giá độ tin cậy?  
**Đáp án:** không. Đó chỉ là 100 lần phát lại cùng một mẫu mô phỏng.

**Bài 6 — đồng thời.** Hai task nhận generator con từ cùng root seed nhưng thứ tự gọi `split()` được đảo ở lần chạy sau. Kết quả từng task có bắt buộc trùng không?  
**Đáp án:** không. Generator gắn với mỗi task đã đổi; cần gán nhánh bằng thứ tự hoặc ID xác định.

**Bài 7 — kiểm thử đúng.** Một test tính tái lập và một test thống kê khác nhau thế nào?  
**Đáp án:** test tái lập kiểm tra cùng seed/cấu hình cho cùng kết quả; test thống kê đánh giá đặc điểm phân phối qua đủ nhiều mẫu hoặc các luồng độc lập.

## 15. Checklist trước khi nộp bài mô phỏng

- [ ] Hàm mô phỏng nhận **seed** và số lượng mẫu rõ ràng.
- [ ] Generator được tạo **một lần cho mỗi phiên**, không reset mỗi mẫu.
- [ ] Các bound ngẫu nhiên đúng quy tắc, ví dụ xúc xắc 1..6 và tọa độ `[-1,1)`.
- [ ] Mỗi phiên kiểm soát thứ tự gọi RNG và kiểm tra tham số đầu vào.
- [ ] Chạy lại cùng seed/cấu hình tạo kết quả giống nhau trong môi trường đã kiểm chứng.
- [ ] Có ít nhất một seed khác để quan sát độ biến thiên; không ép kết quả hai seed phải khác tuyệt đối.
- [ ] Có kiểm tra invariant nghiệp vụ, ví dụ `0 <= inside <= trials`.
- [ ] Nếu chạy song song, chiến lược seed/split/task ID không phụ thuộc scheduling.
- [ ] Ghi seed, thuật toán, tham số, phiên bản chương trình và kết quả vào thông tin chẩn đoán.
- [ ] Không dùng PRNG seeded cho token, mật khẩu hoặc dữ liệu cần bí mật.

## 16. Kết luận

- **Seed tạo khả năng tái lập một chuỗi giả ngẫu nhiên**, với điều kiện thuật toán, các lời gọi và ngữ cảnh được giữ nhất quán.
- Với Java 17, dùng `Random(seed)` cho ví dụ đơn giản; dùng `SplittableRandom(seed)` khi cần bộ sinh có thể tách thành các luồng độc lập theo một kế hoạch xác định.
- **Cùng seed không đảm bảo cùng kết quả toàn chương trình** nếu thay cách tiêu thụ random, dữ liệu, số luồng hoặc thuật toán.
- Mô phỏng Monte Carlo có thể được test **về tính tái lập** bằng cùng seed, đồng thời cần nhiều seed/mẫu để kiểm tra **đặc tính thống kê**.
- Khi cần phát lại lỗi, lưu **seed + tên thuật toán + tham số + phiên bản + thứ tự/chiến lược phân luồng**; không chỉ ghi một con số seed.
- PRNG để mô phỏng và RNG phục vụ bảo mật là **hai mục tiêu khác nhau**.

Tài liệu chính thức Java SE 17:
- [`Random`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Random.html)
- [`SplittableRandom`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/SplittableRandom.html)
- [`RandomGenerator`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/random/RandomGenerator.html)
- [`RandomGeneratorFactory`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/random/RandomGeneratorFactory.html)
- [`ThreadLocalRandom`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadLocalRandom.html)
- [`SecureRandom`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/SecureRandom.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-practice-011">So sánh bộ đếm thường, atomic và adder</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh bộ đếm thường, atomic và adder trong Java

## 1. Vấn đề cần giải quyết

Giả sử ứng dụng có bốn luồng, mỗi luồng xử lý 100.000 sự kiện và phải tăng một bộ đếm chung. **Giá trị mong đợi là 400.000**, bất kể lịch chạy các luồng. Ba cách tiếp cận thường gặp là:

- **Bộ đếm thường:** biến `int` hoặc `long` được cập nhật bằng `count++`. Đơn giản và phù hợp khi chỉ một luồng sở hữu dữ liệu, nhưng **không an toàn** nếu nhiều luồng cùng tăng mà không đồng bộ.
- **Bộ đếm atomic:** `AtomicInteger` hoặc `AtomicLong`. Cung cấp thao tác đọc, tăng, cộng và so sánh-đổi có tính nguyên tử đối với một giá trị chung.
- **Bộ đếm adder:** `LongAdder`. Cho phép nhiều luồng cộng vào bộ đếm, có thể phân tán cập nhật trên nhiều ô để giảm contention; tổng được tính khi cần quan sát.

Một đối chứng rất quan trọng là **bộ đếm thường được bảo vệ bằng `synchronized`**. Đây vẫn là bộ đếm primitive nhưng có một khóa bảo vệ mọi truy cập phù hợp, nên có thể hoàn toàn đúng. Sự khác biệt là *plain unprotected* so với *plain protected*, không phải cứ dùng primitive thì luôn sai.

Cần trả lời cả ba câu hỏi: **Kết quả có đúng khi chạy đồng thời? Mỗi lần đọc có yêu cầu là snapshot nguyên tử không? Và hiệu năng có thật sự tốt hơn trên workload thực?**

## 2. Tại sao count++ có thể làm mất lượt tăng?

~~~java
class Counter {
    long count;

    void increment() {
        count++; // KHÔNG atomic khi nhiều luồng cùng ghi
    }
}
~~~

`count++` là một biểu thức ngắn, nhưng về ngữ nghĩa nó gồm:

1. Đọc giá trị cũ.
2. Tính giá trị cũ cộng 1.
3. Ghi giá trị mới trở lại.

Khi hai luồng cùng thấy `count == 5`, cả hai tính 6 và cả hai ghi 6, tổng chỉ tăng một lần thay vì hai. Đây là *lost update*, một dạng **race condition**.

| Thời điểm | Luồng A | Luồng B | Giá trị lưu |
| --- | --- | --- | ---: |
| 0 | — | — | 5 |
| 1 | Đọc 5 | — | 5 |
| 2 | — | Đọc 5 | 5 |
| 3 | Tính 6 | — | 5 |
| 4 | — | Tính 6 | 5 |
| 5 | Ghi 6 | — | 6 |
| 6 | — | Ghi 6 | 6 |

Đoạn chương trình thực tế có thể luôn in ra số mong đợi trong một vài lượt thử, nhưng điều đó **không chứng minh thread safety**. Lịch xen kẽ khác, số lõi khác hoặc tối ưu JVM khác có thể bộc lộ race.

### Volatile không sửa được ++

~~~java
class Counter {
    volatile long count;

    void increment() {
        count++; // vẫn có thể lost update
    }
}
~~~

`volatile` cung cấp ngữ nghĩa visibility và ordering cho các thao tác trên biến theo Java Memory Model. Nó **không gộp đọc–cộng–ghi thành một thao tác nguyên tử**. Hai luồng vẫn có thể đọc cùng giá trị rồi ghi cùng kết quả. Không dùng `Thread.sleep` hay thêm độ trễ để sửa race: chúng chỉ thay đổi timing.

Nếu bộ đếm **hoàn toàn thuộc về một luồng**, biến thường là lựa chọn tốt; không phải mọi biến đếm đều cần atomic.

## 3. Bảng so sánh hành vi

| Tiêu chí | `long count++` không đồng bộ | `AtomicLong` | `LongAdder` |
| --- | --- | --- | --- |
| Tăng từ nhiều luồng mà không mất lượt? | **Không** | Có | Có |
| Giá trị đọc tức thời có ngữ nghĩa atomic? | Không đảm bảo | `get()` có | `sum()` không phải snapshot atomic |
| Lấy giá trị trước/sau mỗi lượt tăng? | Không an toàn nếu chia sẻ | Có: `getAndIncrement`, `incrementAndGet` | Không |
| So sánh-và-đổi một giá trị? | Không | Có: `compareAndSet` | Không |
| Khi contention tăng cao | Sai về logic | Cạnh tranh cập nhật cùng một trạng thái | Có thể phân tán cập nhật để tăng throughput |
| Chi phí bộ nhớ | Rất nhỏ | Đối tượng chứa trạng thái atomic | Có thể lớn hơn vì các ô phụ |
| Trường hợp thường phù hợp | Single-thread hoặc đồng bộ từ bên ngoài | Cần linearizable operation/CAS/ID | Metrics, nhiều updates, ít đọc chính xác tức thời |

**Không suy từ bảng thành thứ tự tốc độ cố định.** `AtomicLong` có thể nhanh hơn hoặc tương đương `LongAdder` ở contention thấp; `synchronized` cũng có thể đủ nhanh. Chỉ benchmark đúng workload mới kết luận được.

## 4. AtomicInteger và AtomicLong

### 4.1. Phương thức cơ bản

~~~java
import java.util.concurrent.atomic.AtomicLong;

AtomicLong count = new AtomicLong(0);

long afterFirst = count.incrementAndGet(); // 1
long beforeSecond = count.getAndIncrement(); // 1
long current = count.get(); // 2
long afterAdd = count.addAndGet(10); // 12
~~~

Tên phương thức thể hiện giá trị trả về:

- `incrementAndGet()`: tăng, sau đó trả giá trị mới.
- `getAndIncrement()`: trả giá trị cũ, sau đó tăng.
- `addAndGet(delta)`: cộng, sau đó trả giá trị mới.
- `getAndAdd(delta)`: trả giá trị cũ, sau đó cộng.
- `get()` và `set(...)`: đọc/ghi một giá trị chung.
- `compareAndSet(expected, update)`: cập nhật chỉ nếu giá trị hiện tại bằng expected, trả `true` khi thành công.

`AtomicInteger` cung cấp biến thể 32 bit với tên phương thức tương tự. `AtomicLong` dùng 64 bit, phù hợp bộ đếm có thể vượt `Integer.MAX_VALUE`.

### 4.2. Vì sao không mất update?

Các phép tăng atomic được đồng bộ bởi cơ chế mà JVM/phần cứng cung cấp, thường dựa trên **CAS (compare-and-swap)** hoặc một thao tác phần cứng tương đương. Khi hai luồng cùng tăng từ 5, chỉ một luồng có thể cập nhật thành 6 trước; luồng còn lại phải làm việc với trạng thái mới và cập nhật thành 7.

Về hợp đồng, mỗi thao tác atomic có thể được hiểu như xảy ra ở **một điểm nguyên tử** (*linearization point*) đối với biến. Về hiệu năng, nhiều luồng cùng cạnh tranh một trạng thái có thể khiến các thao tác CAS phải thử lại; bởi vậy contention có thể gây chi phí.

Không nên mặc định mọi JDK hay CPU chạy cùng một chuỗi lệnh máy; người lập trình dựa vào hợp đồng API, không dựa vào giả định cách triển khai cụ thể.

### 4.3. Một phép get và một phép increment không tạo giao dịch

~~~java
if (count.get() < 100) {
    count.incrementAndGet();
}
~~~

Dù hai lời gọi **đều atomic riêng lẻ**, toàn bộ cặp “kiểm tra rồi tăng” **không atomic**. Hai luồng có thể cùng đọc 99, cùng thấy điều kiện đúng rồi đưa kết quả thành 101.

Muốn giới hạn chặt, dùng một vòng lặp CAS:

~~~java
static boolean tryIncrementBelow(
        AtomicLong counter, long limit) {
    while (true) {
        long current = counter.get();
        if (current >= limit) {
            return false;
        }
        long next = current + 1;
        if (counter.compareAndSet(current, next)) {
            return true;
        }
    }
}
~~~

Đây là phương thức minh họa đặt trong một lớp có import `AtomicLong`. Nếu `current < limit` và `limit` là giá trị long hợp lệ, `current + 1` không tràn. Để giữ invariant, các luồng sửa cùng bộ đếm phải tuân thủ giao thức cập nhật tương thích.

`AtomicLong.updateAndGet(fn)` cũng hỗ trợ hàm cập nhật, nhưng hàm có thể chạy lại khi CAS thất bại: **không đặt side effect** như ghi cơ sở dữ liệu hoặc gửi thông báo trong `fn`.

### 4.4. Khi nào chọn Atomic?

- Cần phát hành số thứ tự theo từng increment, ví dụ `getAndIncrement()` trong phạm vi không tràn.
- Cần giá trị snapshot tại một thời điểm logic cho **một biến**.
- Cần CAS, cập nhật có điều kiện, `getAndSet` hoặc quản lý trạng thái có mối liên hệ chặt với từng thao tác.
- Contention không quá cao hoặc đã đo thấy atomic đạt yêu cầu.

Atomic cho một biến không tự bảo vệ invariant trải trên **nhiều biến độc lập**. Nếu hai trường phải đổi cùng lúc như một giao dịch, cần đồng bộ phù hợp hoặc đóng gói toàn bộ trạng thái thành một đối tượng immutable được cập nhật qua một `AtomicReference`.

## 5. LongAdder: giảm tranh chấp cập nhật

### 5.1. Cách dùng

~~~java
import java.util.concurrent.atomic.LongAdder;

LongAdder requests = new LongAdder();

requests.increment(); // +1
requests.add(5);      // +5
requests.decrement(); // -1

long total = requests.sum(); // 5 nếu không có update đồng thời khác
~~~

`LongAdder` duy trì giá trị logic như một tổng, nhưng có thể sử dụng nhiều **ô đếm nội bộ**. Dưới contention, các luồng không nhất thiết đều cập nhật cùng một ô. Điều này có thể tăng throughput cho metric dạng “chỉ cần ghi rất nhiều, đọc thỉnh thoảng”.

Nó không thay thế mọi vai trò của atomic vì đánh đổi cơ chế quan sát giá trị.

### 5.2. sum() không phải snapshot nguyên tử khi đang cập nhật

Khi gọi `sum()`, LongAdder tính tổng các ô. Nếu luồng khác vẫn đang tăng, những ô khác nhau có thể được đọc ở **những thời điểm khác nhau**. Vì vậy:

- `sum()` hữu ích cho metric gần thời gian thực.
- `sum()` **không linearizable** như `AtomicLong.get()`.
- Không dùng một phép `sum()` để thực thi giới hạn quota cần kiểm tra–cập nhật nguyên tử.
- Khi **mọi cập nhật đã kết thúc** và được đồng bộ đúng, `sum()` trả tổng cuối chính xác.

Trong chương trình phần 7, tất cả thread đã được `join()` trước khi gọi `sum()`.

### 5.3. Không dùng adder để cấp ID

Đoạn sau **sai về tính duy nhất**:

~~~java
long id = requests.sum();
requests.increment();
~~~

Hai thread có thể cùng đọc tổng trước khi tăng và nhận cùng id. LongAdder đảm bảo không mất phép cộng, **không cung cấp số trước/sau từng phép tăng**. Dùng `AtomicLong.getAndIncrement()` khi cần cấp số dựa trên một trạng thái trong JVM, đồng thời quản lý overflow và yêu cầu phối hợp giữa nhiều JVM.

### 5.4. reset và sumThenReset

~~~java
long sinceLastCheck = requests.sumThenReset();
~~~

`sumThenReset()` hữu ích cho thu số liệu từng giai đoạn khi đã bảo đảm **không có update đồng thời**. Đây không phải snapshot-và-reset nguyên tử với các increment đang tiếp tục. Nếu reset trong lúc các luồng tăng, một số cập nhật có thể không được phân vào các kỳ thống kê như mong đợi.

Khi cần phân kỳ chính xác, thiết kế giao thức dừng/giao nhận update, khóa, hai bucket được chuyển đổi có đồng bộ, hoặc ghi sự kiện theo timestamp phù hợp. Không coi `sumThenReset()` là phép “chốt sổ” an toàn cho mọi concurrency.

### 5.5. Các biến thể liên quan

`DoubleAdder` hỗ trợ cộng các giá trị `double` nhưng vẫn có các vấn đề làm tròn nhị phân; không dùng nó thay `BigDecimal` khi tiền phải chính xác theo quy tắc decimal. `LongAccumulator` áp một hàm kết hợp với identity cho một số phép tổng hợp khác ngoài cộng.

## 6. Bộ đếm synchronized: một đối chứng an toàn

~~~java
class LockedCounter {
    private long count;

    synchronized void increment() {
        count++;
    }

    synchronized long get() {
        return count;
    }
}
~~~

`synchronized` bảo đảm chỉ một thread tại một thời điểm thực thi critical section dưới cùng monitor. Lời gọi `get()` cũng dùng monitor ấy, nên có cơ chế visibility phù hợp.

Cách này có thể tốt khi cần:

- Mã đơn giản, dễ chứng minh tính đúng đắn.
- Một nhóm trường chung cần giữ invariant.
- Logic cập nhật gồm nhiều bước, không chỉ phép cộng.
- Contention thấp hoặc chưa phải bottleneck sau khi đo.

Không nên bảo vệ `increment()` bằng khóa nhưng lại để một đường đọc/ghi khác truy cập `count` không có đồng bộ tương ứng. Khi có nhiều đường cập nhật, tất cả phải theo cùng giao thức bảo vệ.

## 7. Ví dụ Java 17+ có kết quả xác định

Ở một stress test thuần túy, `count++` có thể sai hoặc tình cờ đúng tùy lịch thread, khiến stdout không cố định. Vì vậy phần đầu chương trình **cố ý tách đọc và ghi** và cho cả hai thread đọc `0` trước khi ghi. Điều này mô phỏng đúng cơ chế mất update của `count++`, đồng thời cho kết quả chắc chắn.

Phần thứ hai có bốn thread, mỗi thread tăng ba counter an toàn 100.000 lần. Chỉ sau khi các thread hoàn thành, main mới đọc tổng.

Tạo file **`CounterComparisonDemo.java`**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class CounterComparisonDemo {
    static class PlainCounter {
        int count;
    }

    static class LockedCounter {
        private long count;

        synchronized void increment() {
            count++;
        }

        synchronized long get() {
            return count;
        }
    }

    static int forcedLostUpdate() throws InterruptedException {
        PlainCounter counter = new PlainCounter();
        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Runnable task = () -> {
            int snapshot = counter.count;
            bothRead.countDown();

            try {
                go.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }

            counter.count = snapshot + 1;
        };

        Thread a = new Thread(task);
        Thread b = new Thread(task);
        a.start();
        b.start();

        bothRead.await();
        go.countDown();

        a.join();
        b.join();
        return counter.count;
    }

    public static void main(String[] args)
            throws InterruptedException {
        System.out.println("plain forced collision: "
                + forcedLostUpdate() + " (expected 2)");

        final int threads = 4;
        final int repeats = 100_000;

        AtomicLong atomic = new AtomicLong();
        LongAdder adder = new LongAdder();
        LockedCounter locked = new LockedCounter();

        Thread[] workers = new Thread[threads];
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }

                for (int n = 0; n < repeats; n++) {
                    atomic.incrementAndGet();
                    adder.increment();
                    locked.increment();
                }
            });
            workers[i].start();
        }

        start.countDown();

        for (Thread worker : workers) {
            worker.join();
        }

        long expected = (long) threads * repeats;
        System.out.println("expected: " + expected);
        System.out.println("atomic after join: " + atomic.get());
        System.out.println("adder after join: " + adder.sum());
        System.out.println("locked after join: " + locked.get());
        System.out.println("all safe counters exact: "
                + (atomic.get() == expected
                && adder.sum() == expected
                && locked.get() == expected));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac CounterComparisonDemo.java
java CounterComparisonDemo
~~~

Stdout (đã đối chiếu với Java):

~~~text
plain forced collision: 1 (expected 2)
expected: 400000
atomic after join: 400000
adder after join: 400000
locked after join: 400000
all safe counters exact: true
~~~

**Cách giải thích:** `CountDownLatch` trong `forcedLostUpdate` chỉ điều phối hai thread để cả hai cùng đọc trước khi được phép ghi; nó không giúp bộ đếm nguyên tử. Vì cả hai snapshot đều bằng 0, cả hai cùng ghi 1, và kết quả là 1 thay vì 2. Chương trình minh họa *lost update* có thể tái lập; nếu thay bằng `count++` tự do, việc lỗi có xuất hiện ở lần chạy cụ thể không thể cam kết.

Ba bộ đếm còn lại hoàn tất 400.000 increments chính xác. `join()` bảo đảm luồng đã kết thúc trước khi kiểm tổng. Việc đặt cùng ba counter trong một vòng lặp **chỉ để kiểm đúng/sai**, không phải một benchmark tốc độ, vì sự hiện diện của synchronized và các primitive đồng bộ ảnh hưởng scheduling và tương tác CPU.

## 8. Atomic và Adder không ngăn tràn kiểu số

Thread-safe nghĩa là các lượt cập nhật không mất vì race, **không có nghĩa kết quả nằm trong phạm vi kiểu**.

~~~java
AtomicLong atomic = new AtomicLong(Long.MAX_VALUE);
long wrapped = atomic.incrementAndGet();
// Long.MIN_VALUE do tràn long
~~~

`AtomicInteger` cũng có thể tràn `int`. `LongAdder` dùng giá trị long, nên vẫn cần chính sách riêng khi tổng có thể vượt phạm vi long.

Nếu tràn là lỗi, một hướng trên AtomicLong:

~~~java
AtomicLong safe = new AtomicLong(Long.MAX_VALUE - 1);

long max = safe.updateAndGet(Math::incrementExact);
// Long.MAX_VALUE

// Lần tiếp theo ném ArithmeticException:
// safe.updateAndGet(Math::incrementExact);
~~~

`Math.incrementExact` sẽ ném lỗi thay vì wrap. Hàm cập nhật phải **không có side effect**, vì `updateAndGet` có thể gọi lại khi CAS không thành công. Đây là một cơ chế phù hợp cho tính đúng đắn khi workload/contract cho phép, không phải tuyên bố rằng mọi bộ đếm cần dùng chính xác mẫu này.

Nếu giá trị hợp lệ có thể lớn hơn `Long.MAX_VALUE`, cân nhắc mô hình dùng `BigInteger` với cơ chế đồng bộ được thiết kế rõ; `LongAdder` không phải dạng arbitrary-precision.

## 9. Snapshot và ranh giới quan sát

### 9.1. AtomicLong.get()

`AtomicLong.get()` đọc một giá trị atomic, có ngữ nghĩa linearizable đối với trạng thái của biến đó. Tuy nhiên:

~~~java
long first = atomic.get();
long second = atomic.get();
~~~

hai lần đọc có thể khác nhau vì thread khác tăng ở giữa. **Một thao tác atomic không tự biến mọi chuỗi thao tác liên tiếp thành giao dịch**.

### 9.2. LongAdder.sum()

`sum()` có thể đọc những ô nội bộ tại các thời điểm khác nhau khi updates còn diễn ra. Nó phù hợp metric gần thời gian thực, nhưng không phù hợp cho kiểm tra quota cần chính xác nguyên tử.

Khi các worker đã kết thúc, `join()` và cơ chế đồng bộ tương ứng đảm bảo thay đổi của chúng được quan sát đúng, và `sum()` cho tổng cuối. Đây là điều kiện quan trọng để viết test chính xác cho LongAdder.

### 9.3. Một JVM khác nhiều JVM

`AtomicLong` hoặc `LongAdder` chỉ đồng bộ trạng thái trong **cùng một object/JVM**. Nếu ứng dụng có ba instance trên ba server, mỗi instance có counter riêng; không tự có một bộ đếm toàn cục. Cần thiết kế hệ thống phân tán, cơ sở dữ liệu, event stream hay dịch vụ cấp ID theo yêu cầu nếu phải thống nhất liên node.

## 10. Hiệu năng: đừng chỉ đo thời gian một vòng lặp

Các yếu tố cần quan sát:

- **Contention:** nhiều luồng cùng muốn cập nhật.
- **Throughput:** lượt increment mỗi giây.
- **Latency:** độ trễ mỗi thao tác và phân phối đuôi.
- **Memory footprint:** AtomicLong thường gọn hơn LongAdder đã phải mở rộng nhiều ô.
- **Read frequency:** với LongAdder, việc gọi `sum()` thường xuyên làm thay đổi tỷ lệ chi phí đọc/ghi.
- **Thread scheduling, JIT, GC, CPU topology:** ảnh hưởng lớn đến microbenchmark.

Không có công thức “LongAdder luôn nhanh gấp N lần AtomicLong”. Dưới contention cao và đọc ít, LongAdder thường có ưu thế throughput. Với ít luồng, chi phí phân tán/thu tổng có thể không đáng, AtomicLong đơn giản hơn. Khi cần invariant phức tạp, `synchronized` có thể là thiết kế đúng ngay cả khi thao tác khóa có chi phí.

### 10.1. Cách benchmark bằng JMH

**JMH (Java Microbenchmark Harness)** phù hợp hơn một phép đo `System.nanoTime()` duy nhất. Một benchmark có ý nghĩa cần:

1. Tách riêng ba benchmark `plain/AtomicLong/LongAdder` hoặc `synchronized` theo mục tiêu, không cập nhật tất cả trong cùng một vòng.
2. Thử nhiều số lượng thread: 1, 2, 4, 8... theo tải thực tế.
3. Bảo đảm counter thực sự chia sẻ giữa các thread trong bài test contention.
4. Dùng warmup, nhiều iteration/fork và tiêu thụ kết quả để tránh tối ưu bỏ mã.
5. So cùng số lượng thao tác, cùng mô hình dữ liệu và cùng tần suất đọc.
6. Đo cả throughput, latency và footprint/GC nếu relevant.
7. Phân tích tính đúng đắn trước khi xem kết quả tốc độ.

Nếu một bộ đếm thường không đồng bộ cho throughput lớn vì bỏ mất update, nó **không phải giải pháp tương đương chức năng** để đem ra so tốc độ công bằng.

Ví dụ phần 7 cố ý chạy cùng ba phương án để kiểm chúng trả đúng tổng, **không dùng output đó làm kết luận phương án nào nhanh hơn**.

## 11. Kiểm thử đúng ngữ nghĩa

### 11.1. Test đơn luồng

~~~java
AtomicLong counter = new AtomicLong();

counter.incrementAndGet();
counter.addAndGet(4);
counter.decrementAndGet();
// counter.get() == 4
~~~

Đây là test chức năng cơ bản, **không đủ** xác nhận tính đúng khi nhiều thread cùng cập nhật.

### 11.2. Test đa luồng

Khởi tạo N thread, mỗi thread tăng M lần, chờ `join()` hoặc `Future.get()`, rồi so tổng:

\[
\text{expected}=N\times M.
\]

Đặt phép nhân kỳ vọng trong phạm vi đủ rộng:

~~~java
long expected = (long) threadCount * incrementsPerThread;
~~~

Nếu nhân hai biến int trước khi cast, kỳ vọng của test có thể tự tràn và gây kết luận sai. Với tập dữ liệu rất lớn, phải xét cả nguy cơ tràn long.

### 11.3. Stress test và lost update

Một stress test chạy hàng nghìn lần và chưa thấy lỗi **không chứng minh** `count++` an toàn. Có thể thêm test điều phối hai luồng như chương trình đã cho, hoặc dùng công cụ chuyên kiểm tra tương tác luồng (ví dụ jcstress) nếu bài toán phức tạp.

Với LongAdder, test nên yêu cầu **tổng chính xác sau khi tất cả updates kết thúc**, không yêu cầu `sum()` đang chạy đồng thời là một snapshot nguyên tử. Nếu hợp đồng cần reset từng kỳ chính xác, test phải kiểm cả ranh giới chuyển kỳ và updates đồng thời.

### 11.4. Test trường hợp nghiệp vụ đặc biệt

Kiểm tra thêm:

- Giá trị bắt đầu không phải 0.
- Increment và decrement, giá trị âm hợp lệ hay không.
- Tràn `Integer.MAX_VALUE`/`Long.MAX_VALUE` và chính sách lỗi.
- CAS thất bại khi có cạnh tranh, tính đúng của vòng retry.
- Điều kiện quota/giới hạn không bị vượt.
- Đọc đang diễn ra cùng cập nhật; phân biệt yêu cầu snapshot nguyên tử với metric gần đúng.
- Dừng worker, timeout và lỗi thread; không giả định mọi worker luôn hoàn thành trọn số lượt nếu có exception hoặc interruption.

## 12. Chọn phương án theo tình huống

| Yêu cầu chính | Gợi ý | Cần tránh |
| --- | --- | --- |
| Chỉ một thread hoặc dữ liệu local | `long` bình thường | Thêm đồng bộ không cần thiết |
| Nhiều thread, cần số trước/sau từng lượt | `AtomicLong` | Dùng `LongAdder.sum()` để cấp ID |
| Nhiều thread ghi metric, thỉnh thoảng đọc | `LongAdder` | Coi `sum()` là snapshot atomic |
| Kiểm tra ngưỡng cùng lúc với tăng | CAS loop hoặc khóa chung | `if(get < limit) increment()` |
| Nhiều trường cùng invariant | `synchronized`/lock hoặc mô hình trạng thái atomic đúng | Nghĩ atomic riêng lẻ tạo giao dịch |
| Cần reset đúng một ranh giới khi cập nhật liên tục | Protocol phân kỳ có đồng bộ | `sumThenReset()` không phối hợp |
| Cần phát hiện overflow | Exact arithmetic/policy giới hạn | Nghĩ atomic hoặc adder tự ngăn tràn |
| Nhiều server | Bộ đếm phân tán/DB/service thích hợp | Nghĩ atomic instance là global counter |

## 13. Các lỗi thường gặp

1. **Tin `count++` là nguyên tử vì chỉ một câu lệnh.** Nó có thể lost update.
2. **Thêm `volatile` rồi coi như đã thread-safe.** Volatile không tạo atomic read-modify-write.
3. **Dùng LongAdder để phát ID.** `sum()` và `increment()` không thành một giao dịch.
4. **Tin `LongAdder.sum()` là snapshot nguyên tử.** Khi updates đồng thời, tổng có thể không tương ứng một điểm quan sát.
5. **Dùng `sumThenReset()` khi threads còn cập nhật nhưng đòi chia kỳ tuyệt đối.** Cần protocol chính xác.
6. **Dùng `AtomicLong.get()` rồi cập nhật ở lời gọi riêng để kiểm quota.** Có thể race giữa hai bước.
7. **Cho rằng AtomicLong luôn nhanh hơn synchronized.** Cần đo; yêu cầu nhiều trường cũng quan trọng.
8. **Cho rằng LongAdder luôn nhanh hơn AtomicLong.** Có thể không đúng với ít contention hoặc đọc dày.
9. **Quên overflow.** Thread-safe không thay miền kiểu long/int.
10. **Đọc plain counter bằng đường không đồng bộ trong khi ghi dùng khóa.** Mọi truy cập phải tuân thủ cùng cơ chế.
11. **Cho side effect vào lambda `updateAndGet`.** Có thể chạy lại trong quá trình CAS.
12. **Benchmark khi các phương án cùng được cập nhật trong một vòng.** Không cô lập overhead.
13. **Lấy kết quả một lần stress test làm chứng cứ thread safety.** Race phụ thuộc scheduling.
14. **Tưởng một AtomicLong điều phối nhiều JVM.** Nó chỉ thuộc một đối tượng trong một process.
15. **Kiểm tra kết quả khi thread chưa join.** Khi đó kết quả chưa phải tổng cuối.

## 14. Câu hỏi tự kiểm tra

**Câu 1.** Hai luồng cùng thực hiện `count++`. Có luôn tăng đúng 2 không?  
**Đáp án:** Không, có thể lost update do hai luồng đọc cùng giá trị cũ.

**Câu 2.** `volatile long count; count++;` đã an toàn chưa?  
**Đáp án:** Chưa. Volatile không khiến thao tác đọc–tính–ghi trở thành atomic.

**Câu 3.** Bộ đếm request có rất nhiều luồng cùng increment và chỉ xuất báo cáo mỗi 10 giây. Nên cân nhắc gì?  
**Đáp án:** `LongAdder`, nếu yêu cầu chấp nhận tổng không phải snapshot nguyên tử khi đang cập nhật. Vẫn benchmark theo workload thực.

**Câu 4.** Cần ID khác nhau sau mỗi phép tăng trong một JVM, nên dùng gì?  
**Đáp án:** `AtomicLong.getAndIncrement()` hoặc tương đương, với chính sách overflow và các đường cấp số nhất quán.

**Câu 5.** LongAdder có chính xác sau khi join mọi worker không?  
**Đáp án:** Có nếu không còn update đồng thời và worker hoàn thành đúng; `sum()` là kết quả tổng sau thời điểm quiescent.

**Câu 6.** Bộ đếm primitive được bảo vệ bằng synchronized còn lost update không?  
**Đáp án:** Không nếu mọi lần cập nhật đều dùng cùng monitor và đường đọc tuân thủ đồng bộ; vẫn phải kiểm vấn đề overflow.

**Câu 7.** `new AtomicLong(Long.MAX_VALUE).incrementAndGet()` có ném exception vì tràn không?  
**Đáp án:** Không, kết quả wrap thành `Long.MIN_VALUE`. Cần cơ chế phát hiện tràn nếu nghiệp vụ yêu cầu.

**Câu 8.** `if (atomic.get() < 100) atomic.incrementAndGet();` có luôn giới hạn đúng 100 khi có nhiều thread không?  
**Đáp án:** Không, điều kiện và increment không nguyên tử như một nhóm; nên dùng CAS loop hoặc đồng bộ.

**Câu 9.** Một lần chạy plain counter nhanh và trả số đúng có chứng minh nó thread-safe không?  
**Đáp án:** Không. Cần lý luận Java Memory Model và kiểm thử concurrency phù hợp, không dựa vào một lịch chạy.

**Câu 10.** Khi hai phương án đều đúng, làm sao chọn cái nhanh hơn?  
**Đáp án:** Đo bằng JMH với mức contention, số threads, tần suất đọc, memory và thời gian warmup tương ứng workload thật.

## 15. Tài liệu Java chính thức

- [`AtomicLong` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicLong.html): `get`, `getAndIncrement`, `compareAndSet` và cập nhật nguyên tử.
- [`AtomicInteger` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html): bộ đếm int với API atomic.
- [`LongAdder` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/LongAdder.html): `increment`, `sum`, `sumThenReset` và giới hạn snapshot.
- [`CountDownLatch` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html): điều phối thread.
- [`Math` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html): `incrementExact` để phát hiện tràn.
- [Java Language Specification 17, Memory Model](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4): happens-before, visibility và data races.

**Ghi nhớ:** Bộ đếm primitive không đồng bộ có thể mất update; `AtomicLong` cung cấp read/update atomic, CAS và giá trị cho mỗi lần tăng; `LongAdder` thích hợp cho cập nhật metric nhiều dưới contention nhưng `sum()` không phải snapshot nguyên tử. Ưu tiên hợp đồng chính xác trước, sau đó mới đo hiệu năng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-practice-012">Hoàn thiện ví dụ tích hợp cùng bộ kiểm thử dữ liệu biên</a>

<details>
<summary>Xem chi tiết</summary>

# Hoàn thiện ví dụ tích hợp cùng bộ kiểm thử dữ liệu biên — Java Core Numbers

## 1. Mục tiêu và sản phẩm cuối

Bài thực hành này hoàn thiện **một chương trình Java 17 chạy độc lập**, sử dụng nhiều kiến thức số học của Java trong cùng một luồng xử lý: nhập dữ liệu decimal, chuyển về số nguyên đơn vị nhỏ nhất, kiểm tra số lượng, nhân và cộng có phát hiện overflow, tính thuế có quy tắc làm tròn rõ ràng, định dạng kết quả, và kiểm thử dữ liệu ở biên.

Bài toán minh họa: tính tiền cho **một dòng hóa đơn**. Đầu vào gồm đơn giá decimal dạng chuỗi, số lượng dạng chuỗi và tỷ lệ thuế dạng chuỗi. Đầu ra là ba giá trị số nguyên long: tiền hàng, thuế và tổng tiền, tất cả tính bằng **cents**. Thuật toán có một bộ kiểm thử tự động không cần thư viện ngoài, cho kết quả xác định khi chạy bằng JDK 17 trở lên.

Yêu cầu kiểm chứng cụ thể:

- Đơn giá không âm, chỉ dùng chữ số ASCII; phần thập phân tối đa hai chữ số. Chuỗi có dấu âm, dấu phẩy, khoảng trắng hoặc số mũ bị từ chối.
- Số lượng là số nguyên dương trong miền int, không có dấu và không có phần thập phân.
- Tỷ lệ thuế nằm trong khoảng 0 đến 1 và có tối đa bốn chữ số thập phân; ví dụ 0.075 nghĩa là 7,5%.
- Mọi phép nhân và cộng tạo kết quả long phải **phát hiện tràn**, không được âm thầm wrap.
- Thuế làm tròn tới cents theo **HALF_EVEN** (làm tròn về số chẵn ở trường hợp đúng nửa bước).
- Dữ liệu sai định dạng/miền bị từ chối; số vượt phạm vi khi chuyển đổi hoặc tính toán được báo lỗi.
- Bộ kiểm thử gồm giá trị thông thường, giá trị biên hợp lệ, giá trị ngay bên ngoài biên, trường hợp nửa bước làm tròn, NaN, vô cực và overflow.

Đây là ví dụ thiết kế dữ liệu số, **không phải hệ thống hóa đơn hay thuế thực tế**: đơn vị cents và tỷ lệ thuế chỉ nhằm minh họa, không đại diện cho quy tắc của một quốc gia hay loại tiền cụ thể.

## 2. Xác định hợp đồng trước khi viết code

| Thành phần | Kiểu đầu vào | Quy tắc | Kiểu lưu sau kiểm tra |
| --- | --- | --- | --- |
| Đơn giá | String | Dạng `[0-9]+(?:\\.[0-9]{1,2})?`, không âm | long cents |
| Số lượng | String | Chỉ chữ số, giá trị 1 đến Integer.MAX_VALUE | int |
| Tỷ lệ thuế | String | Từ 0 đến 1, tối đa 4 chữ số thập phân | BigDecimal |
| Tiền hàng | long cents nhân int | Không vượt long | long |
| Thuế | Tiền hàng × tỷ lệ | HALF_EVEN tới cents; còn trong long | long |
| Tổng | Tiền hàng + thuế | Không vượt long | long |

Một vài trường hợp đầu vào:

| Đơn giá | Số lượng | Thuế | Kỳ vọng |
| --- | --- | --- | --- |
| 19.99 | 3 | 0.075 | Hợp lệ: 59.97 + 4.50 = 64.47 |
| 0.05 | 1 | 0.1 | Thuế 0.5 cents, HALF_EVEN → 0 cents |
| 0.15 | 1 | 0.1 | Thuế 1.5 cents, HALF_EVEN → 2 cents |
| 1.000 | 1 | 0 | Từ chối: định dạng có 3 chữ số thập phân |
| -0.01 | 1 | 0 | Từ chối: đơn giá âm |
| 1.00 | 0 | 0 | Từ chối: số lượng phải dương |
| 1.00 | 2147483648 | 0 | Từ chối: vượt miền int |
| 92233720368547758.08 | 1 | 0 | Từ chối: cents vượt Long.MAX_VALUE |

**Lưu ý:** Chính sách “tối đa hai chữ số decimal trong **chuỗi nhập**” khác với “không mất giá trị số khi đổi sang cents”. Theo chính sách đã chọn, `1.000` bị từ chối dù nó có cùng giá trị số với `1.00`. Muốn cho phép các số 0 dư ở cuối, phải sửa đặc tả và kiểm thử tương ứng.

## 3. Luồng xử lý: validate trước, tính toán sau

~~~text
raw price ----------> kiểm tra cú pháp ----------> BigDecimal
                                                   |
                                                   v
                                      movePointRight(2)
                                                   |
                                                   v
                                          longValueExact()
                                                   |
                                                   v
                                              unitCents
                                                   |
raw quantity -------> kiểm tra cú pháp ------> BigInteger
                                                   |
                                                   v
                                             intValueExact()
                                                   |
                                                   v
                                               quantity
                                                   |
                        Math.multiplyExact(unitCents, quantity)
                                                   |
                                                   v
                                             subtotalCents
                                                   |
raw tax rate -------> kiểm tra 0..1 -------> BigDecimal
                                                   |
                        subtotal × tax rate (decimal chính xác)
                                                   |
                                  setScale(0, HALF_EVEN)
                                                   |
                                      longValueExact()
                                                   |
                                                   v
                                                taxCents
                                                   |
                                 Math.addExact(subtotal, tax)
                                                   |
                                                   v
                                               totalCents
~~~

Ta không chuyển đầu vào số tiền qua `double`, bởi một số decimal như 0.1 không thể biểu diễn chính xác bằng binary64. Các phép cộng/nhân trên long chỉ diễn ra khi đầu vào đã qua bước exact; bất cứ khi nào vượt miền, chương trình báo lỗi thay vì tạo một giá trị sai vẫn mang kiểu long hợp lệ.

## 4. Mã nguồn Java 17 hoàn chỉnh

Tạo tệp **NumericBoundaryLab.java** với nội dung sau. Đây là **một chương trình đầy đủ**, gồm cả logic nghiệp vụ và bộ kiểm thử, không cần JUnit, Maven hoặc thư viện ngoài.

~~~java
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public class NumericBoundaryLab {
    record Receipt(long subtotalCents, long taxCents, long totalCents) {}

    private static int passed;

    static long parsePriceCents(String raw) {
        if (raw == null || raw.length() > 40 ||
                !raw.matches("[0-9]+(?:\\.[0-9]{1,2})?")) {
            throw new IllegalArgumentException("Invalid price format");
        }
        return new BigDecimal(raw).movePointRight(2).longValueExact();
    }

    static int parseQuantity(String raw) {
        if (raw == null || raw.length() > 20 ||
                !raw.matches("[0-9]+")) {
            throw new IllegalArgumentException("Invalid quantity format");
        }
        int quantity = new BigInteger(raw).intValueExact();
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        return quantity;
    }

    static BigDecimal parseRate(String raw) {
        if (raw == null || raw.length() > 8 ||
                !raw.matches("(?:0(?:\\.[0-9]{1,4})?|1(?:\\.0{1,4})?)")) {
            throw new IllegalArgumentException(
                    "Rate must be 0..1 with <=4 decimals");
        }
        return new BigDecimal(raw);
    }

    static Receipt quote(String price, String quantity, String rate) {
        long unitCents = parsePriceCents(price);
        int units = parseQuantity(quantity);
        BigDecimal taxRate = parseRate(rate);

        long subtotal = Math.multiplyExact(unitCents, (long) units);
        long tax = BigDecimal.valueOf(subtotal)
                .multiply(taxRate)
                .setScale(0, RoundingMode.HALF_EVEN)
                .longValueExact();
        long total = Math.addExact(subtotal, tax);
        return new Receipt(subtotal, tax, total);
    }

    static int exactInt(double value) {
        if (!Double.isFinite(value) ||
                value < Integer.MIN_VALUE ||
                value > Integer.MAX_VALUE ||
                value != Math.rint(value)) {
            throw new IllegalArgumentException("Not an exact int");
        }
        return (int) value;
    }

    static String money(long cents) {
        return BigDecimal.valueOf(cents, 2).toPlainString();
    }

    static void same(Receipt receipt, long subtotal, long tax, long total) {
        if (receipt.subtotalCents() != subtotal ||
                receipt.taxCents() != tax || receipt.totalCents() != total) {
            throw new AssertionError("Unexpected receipt: " + receipt);
        }
    }

    static void check(String name, Runnable test) {
        try {
            test.run();
            passed++;
        } catch (RuntimeException | AssertionError ex) {
            throw new AssertionError("FAILED: " + name, ex);
        }
    }

    static void rejects(String name, Class<? extends RuntimeException> expected,
                        Runnable test) {
        check(name, () -> {
            try {
                test.run();
            } catch (RuntimeException actual) {
                if (expected.isInstance(actual)) return;
                throw new AssertionError("Wrong exception: " + actual);
            }
            throw new AssertionError("Expected " + expected.getSimpleName());
        });
    }

    public static void main(String[] args) {
        check("normal", () -> same(quote("19.99", "3", "0.075"), 5997, 450, 6447));
        check("zero price", () -> same(quote("0.00", "3", "0.075"), 0, 0, 0));
        check("HALF_EVEN even tie", () -> same(quote("0.05", "1", "0.1"), 5, 0, 5));
        check("HALF_EVEN odd tie", () -> same(quote("0.15", "1", "0.1"), 15, 2, 17));
        check("no tax", () -> same(quote("1.00", "2", "0"), 200, 0, 200));
        check("max cents", () -> same(quote("92233720368547758.07", "1", "0"),
                Long.MAX_VALUE, 0, Long.MAX_VALUE));
        check("int upper", () -> { if (exactInt(2147483647.0) != Integer.MAX_VALUE)
                throw new AssertionError(); });
        check("int lower", () -> { if (exactInt(-2147483648.0) != Integer.MIN_VALUE)
                throw new AssertionError(); });
        check("int normal", () -> { if (exactInt(42.0) != 42)
                throw new AssertionError(); });

        rejects("negative price", IllegalArgumentException.class,
                () -> quote("-0.01", "1", "0"));
        rejects("price too many decimals", IllegalArgumentException.class,
                () -> quote("1.000", "1", "0"));
        rejects("price exponent", IllegalArgumentException.class,
                () -> quote("1E3", "1", "0"));
        rejects("price comma", IllegalArgumentException.class,
                () -> quote("1,00", "1", "0"));
        rejects("price space", IllegalArgumentException.class,
                () -> quote(" 1.00", "1", "0"));
        rejects("price null", IllegalArgumentException.class,
                () -> quote(null, "1", "0"));
        rejects("quantity zero", IllegalArgumentException.class,
                () -> quote("1.00", "0", "0"));
        rejects("quantity negative", IllegalArgumentException.class,
                () -> quote("1.00", "-1", "0"));
        rejects("quantity decimal", IllegalArgumentException.class,
                () -> quote("1.00", "1.5", "0"));
        rejects("tax over one", IllegalArgumentException.class,
                () -> quote("1.00", "1", "1.01"));
        rejects("tax too many digits", IllegalArgumentException.class,
                () -> quote("1.00", "1", "0.12345"));
        rejects("tax negative", IllegalArgumentException.class,
                () -> quote("1.00", "1", "-0.1"));
        rejects("price cents overflow", ArithmeticException.class,
                () -> quote("92233720368547758.08", "1", "0"));
        rejects("quantity int overflow", ArithmeticException.class,
                () -> quote("1.00", "2147483648", "0"));
        rejects("subtotal long overflow", ArithmeticException.class,
                () -> quote("92233720368547758.07", "2", "0"));
        rejects("total long overflow", ArithmeticException.class,
                () -> quote("92233720368547758.07", "1", "1"));
        rejects("fractional double", IllegalArgumentException.class,
                () -> exactInt(12.9));
        rejects("NaN", IllegalArgumentException.class,
                () -> exactInt(Double.NaN));
        rejects("Infinity", IllegalArgumentException.class,
                () -> exactInt(Double.POSITIVE_INFINITY));
        rejects("double out of int range", IllegalArgumentException.class,
                () -> exactInt(2147483648.0));
        rejects("int addition overflow", ArithmeticException.class,
                () -> Math.addExact(Integer.MAX_VALUE, 1));

        Receipt demo = quote("19.99", "3", "0.075");
        System.out.println("subtotal=" + money(demo.subtotalCents()) +
                ", tax=" + money(demo.taxCents()) +
                ", total=" + money(demo.totalCents()));
        System.out.println("PASS " + passed + " boundary cases");
    }
}
~~~

**Cách chạy:**

~~~bash
javac NumericBoundaryLab.java
java NumericBoundaryLab
~~~

**Kết quả khi chương trình vượt qua toàn bộ kiểm thử:**

~~~text
subtotal=59.97, tax=4.50, total=64.47
PASS 30 boundary cases
~~~

Nếu một bài test trả sai kết quả hoặc một phép chuyển đổi không ném exception như dự kiến, phương thức `check` ném `AssertionError` có tên case. Chương trình sẽ dừng với lỗi thay vì in dòng PASS cuối cùng.

Bộ thử dùng plain Java nên thuận tiện cho một bài học chạy trực tiếp. Trong dự án thực tế, có thể chuyển từng nhóm sang JUnit 5 và thiết lập pipeline tự động; logic assert và dữ liệu biên vẫn dùng được.

## 5. Giải thích từng lớp bảo vệ

### 5.1. Kiểm tra cú pháp trước khi parse

`parsePriceCents` từ chối null, chuỗi dài hơn 40 ký tự và chuỗi không khớp một mẫu decimal ASCII không âm, với tối đa hai chữ số sau dấu chấm. Lý do đặt kiểm tra trước `new BigDecimal(raw)`: BigDecimal nhận nhiều cú pháp hợp lệ về mặt toán học, chẳng hạn exponent, nhưng hợp đồng của ứng dụng minh họa **không** cho phép.

Các đầu vào như `1E3`, `1,00`, ` 1.00` hoặc `-0.00` bị từ chối theo chính sách đã chọn. Lưu ý rằng xét về **giá trị số**, `-0.00` có thể bằng 0; cấm dấu trừ là quyết định về cú pháp, không phải phép so sánh số âm.

Giới hạn độ dài trước parse giúp tránh tiếp nhận chuỗi số quá dài không cần thiết trong bài toán này. Với ứng dụng có giao diện đa ngôn ngữ, mẫu ASCII nghiêm ngặt có thể không phù hợp; định dạng hợp lệ cần được công bố rõ, ví dụ cách nhập dấu phân cách thập phân và hàng nghìn.

### 5.2. Đổi decimal thành cents mà không làm tròn

`new BigDecimal(raw).movePointRight(2).longValueExact()` gồm hai thao tác quan trọng:

- `movePointRight(2)` nhân giá trị decimal với 100 theo phép biểu diễn thập phân chính xác, không làm tròn.
- `longValueExact()` chỉ chấp nhận nếu giá trị sau nhân 100 là số nguyên nằm trong miền long; nếu không ném `ArithmeticException`.

Ở đây tính hợp lệ về **định dạng** đã được kiểm tra riêng. Với đơn giá đầu vào tối đa hai chữ số thập phân, kết quả cents sẽ có giá trị nguyên. Nhưng vẫn phải có bước kiểm tra **phạm vi long** vì chuỗi có thể chứa số rất lớn.

Biên trên của long là 9223372036854775807 cents. Vì vậy giá `92233720368547758.07` đi với số lượng 1, thuế 0, còn hợp lệ; `92233720368547758.08` vượt một cent và bị từ chối.

### 5.3. Không ép số lượng từ BigInteger xuống int một cách âm thầm

`parseQuantity` parse chuỗi qua `BigInteger` rồi gọi `intValueExact()`. Cách này cho phép phân biệt:

- `"0"`: cú pháp đúng, **sai miền nghiệp vụ** vì phải lớn hơn 0.
- `"-1"`: sai cú pháp vì không cho dấu trừ.
- `"1.5"`: sai cú pháp vì số lượng phải nguyên.
- `"2147483648"`: cú pháp nguyên không âm đúng, nhưng **vượt miền int**, khiến `intValueExact()` ném ArithmeticException.

Dùng `Integer.parseInt` cũng có thể từ chối số ngoài miền int, nhưng thông báo/loại lỗi sẽ khác. Cách dùng BigInteger làm rõ hai giai đoạn: hiểu giá trị số nguyên trước, kiểm tra phạm vi kiểu đích sau.

### 5.4. Nhân và cộng có kiểm tra overflow

`Math.multiplyExact(unitCents, (long) units)` trả long hoặc ném ArithmeticException nếu tích toán học vượt miền long. Phép ép long của số lượng được làm **trước phép nhân**; dù phép nhân giữa hai int có thể phù hợp nếu kết quả vừa int, không được tính một kết quả int tràn rồi mới gán vào long.

`Math.addExact(subtotal, tax)` dùng sau khi thuế được tính xong để tránh overflow tổng. Ví dụ đơn giá tạo đúng Long.MAX_VALUE cents và số lượng bằng 2 phải bị từ chối tại bước nhân, chứ không được cho ra kết quả âm. Một giá trị Long.MAX_VALUE cents có số lượng 1 và thuế suất 100% bị từ chối tại bước cộng, vì tổng gấp đôi vượt long.

Nếu nghiệp vụ thật sự cần các số lớn hơn long, phải chuyển các toán hạng sang BigInteger **trước khi tính**, thay vì bọc một giá trị long đã tràn bằng BigInteger.

### 5.5. Thuế tính trong BigDecimal và làm tròn đúng một lần

Tiền hàng đã ở dạng cents nguyên. Để tính thuế theo decimal, dùng:

~~~java
BigDecimal.valueOf(subtotal)
        .multiply(taxRate)
        .setScale(0, RoundingMode.HALF_EVEN)
        .longValueExact();
~~~

Đây là mảnh code nằm trong `quote`. `BigDecimal.valueOf(long)` tạo số decimal chính xác từ long. Phép nhân với tỷ lệ thuế cũng chính xác; sau đó mới làm tròn về **số nguyên cents** theo quy tắc đã xác định. `HALF_EVEN` xử lý điểm đúng giữa hai số nguyên bằng cách chọn số nguyên chẵn.

Hai dữ liệu biên quan trọng:

- 5 cents × 10% = **0.5 cents** → làm tròn HALF_EVEN thành **0 cents** (0 là chẵn).
- 15 cents × 10% = **1.5 cents** → thành **2 cents** (2 là chẵn).

Nếu đổi quy tắc sang HALF_UP hoặc DOWN, kết quả ở các điểm biên này có thể khác. Bộ test phải gắn với **chính sách thực tế**, không chỉ kiểm tra phép tính có chạy được.

Một ví dụ tính thuế 7,5% từ 5997 cents: 5997 × 0.075 = 449.775 cents, làm tròn HALF_EVEN tới **450 cents**, tức 4.50. Tổng là 5997 + 450 = 6447 cents, tức 64.47.

### 5.6. Chỉ chuyển về decimal để hiển thị

`money(long cents)` sử dụng:

~~~java
BigDecimal.valueOf(cents, 2).toPlainString()
~~~

Đây là cách tạo biểu diễn decimal có scale 2 từ cents nguyên. Do đó 5997 cents thành 59.97, 450 cents thành 4.50, 0 cents thành 0.00. Chuỗi này dùng dấu chấm thập phân và không kèm mã tiền tệ, chỉ để minh họa cách hiển thị không đánh mất cents.

Nếu hệ thống yêu cầu hiển thị theo locale hay mã tiền tệ, hãy xử lý tại tầng presentation với quy tắc phù hợp; không nên parse lại chuỗi đã format để làm tính toán cốt lõi.

## 6. Bộ kiểm thử: vì sao cần dữ liệu biên thay vì chỉ trường hợp thông thường?

Chỉ test đơn giá 19.99 và số lượng 3 không thể bắt được những lỗi nguy hiểm như overflow, parse chấp nhận cú pháp sai, hoặc làm tròn sai ở đúng nửa bước.

30 case trong chương trình được chia thành các nhóm:

| Nhóm | Case trọng tâm | Điều được kiểm chứng |
| --- | --- | --- |
| Hóa đơn hợp lệ | Bình thường, đơn giá 0, không thuế | Tính subtotal, thuế, tổng |
| Rounding | 0.5 và 1.5 cents | Hai nhánh HALF_EVEN |
| Biên miền số | Long.MAX_VALUE cents | Giá trị vừa miền được chấp nhận |
| Chuyển double → int | 42, Integer.MIN_VALUE, Integer.MAX_VALUE | Giá trị nguyên hữu hạn trong miền |
| Định dạng giá | Âm, 3 chữ số decimal, exponent, dấu phẩy, khoảng trắng, null | Chặn input ngoài hợp đồng |
| Định dạng số lượng | 0, âm, có phần lẻ | Chặn giá trị sai miền hoặc cú pháp |
| Miền thuế | >1, âm, quá 4 chữ số decimal | Chặn tỷ lệ ngoài miền/precision |
| Chuyển đổi bị tràn | Price cents vượt long, quantity vượt int | Không âm thầm mất dữ liệu |
| Tính toán bị tràn | subtotal/tổng vượt long | Math.multiplyExact và Math.addExact |
| Dấu phẩy động | 12.9, NaN, vô cực, quá miền int | Kiểm tra trước khi cast |
| Phép cộng int | Integer.MAX_VALUE + 1 | Math.addExact ném lỗi thay vì wrap |

Đếm case tự động bằng biến `passed` giúp phát hiện việc chương trình chưa chạy đủ số test mong đợi. Nó không thay thế một công cụ test runner chuyên nghiệp, nhưng **chủ động báo lỗi ngay** nếu bất kỳ kỳ vọng nào bị vi phạm.

### Kiểm thử giá trị ngay sát hai phía của biên

Với một miền [min, max], tập dữ liệu nên gồm **min**, **max**, **giá trị ngay bên trong**, và **giá trị ngay ngoài**, nếu kiểu nguồn có biểu diễn phù hợp.

Ví dụ đối với số lượng:

| Đầu vào | Kỳ vọng |
| --- | --- |
| "0" | Không hợp lệ vì số lượng phải >= 1 |
| "1" | Hợp lệ |
| "2147483647" | Giá trị int hợp lệ, nhưng tổng hóa đơn còn có thể overflow |
| "2147483648" | Không thể chuyển chính xác thành int |
| "-1" | Không hợp lệ về cú pháp |
| "1.0" | Không hợp lệ vì quy tắc chỉ nhận chữ số nguyên |

Một dữ liệu có số lượng hợp lệ vẫn có thể tạo tổng vượt long, nên chỉ validate từng trường **không đủ**; phải tiếp tục kiểm tra từng phép tính tạo dữ liệu phái sinh.

## 7. Nhận diện các loại lỗi và thiết kế thông báo

Chương trình minh họa sử dụng:

- `IllegalArgumentException` cho đầu vào không khớp cú pháp hoặc vi phạm miền nghiệp vụ, ví dụ đơn giá âm hay số lượng bằng 0.
- `ArithmeticException` do `intValueExact`, `longValueExact` hoặc `Math.*Exact` ném khi giá trị/kết quả không thể biểu diễn chính xác.

Cả hai cần được nhận diện và chuyển thành thông báo phù hợp tại tầng API hoặc giao diện người dùng. Đừng để exception kỹ thuật kèm dữ liệu nhạy cảm bị trả trực tiếp ra ngoài trong ứng dụng thật.

`rejects(name, expectedType, runnable)` là helper kiểm tra **loại exception kỳ vọng**: nếu thao tác không ném hoặc ném sai loại, case thất bại. Điều này ngăn lỗi viết test kiểu “mọi exception đều được coi là pass”.

Tuy nhiên, chỉ kiểm tra loại exception có thể chưa đủ với phần mềm lớn. Bạn có thể thêm mã lỗi nghiệp vụ cụ thể và xác minh chính xác trường, mã lỗi, thông điệp thích hợp; đừng khóa test vào toàn bộ stack trace vì nó có thể thay đổi khi refactor.

## 8. Phân biệt kiểm thử chuyển đổi kiểu với xử lý tiền

Phương thức `exactInt(double)` trong chương trình là một bài kiểm tra độc lập để tái sử dụng kiến thức về **các phép ép có thể mất dữ liệu**:

~~~java
static int exactInt(double value) {
    if (!Double.isFinite(value) ||
            value < Integer.MIN_VALUE ||
            value > Integer.MAX_VALUE ||
            value != Math.rint(value)) {
        throw new IllegalArgumentException("Not an exact int");
    }
    return (int) value;
}
~~~

Nó từ chối NaN, vô cực, số ngoài miền int và số có phần lẻ. Chỉ khi đã qua tất cả điều kiện mới cast.

Vì sao không kiểm tra sau khi ép?

~~~java
int wrongA = (int) 12.9;        // 12: phần lẻ đã mất
int wrongB = (int) Double.NaN;  // 0: nguồn NaN đã mất
~~~

Giá trị `12` và `0` đều là int hợp lệ, nhưng không chứng minh nguồn là số nguyên tương ứng. Ngay cả việc gán kết quả này sang long sau đó cũng không phục hồi được đầu vào gốc.

Nếu dữ liệu decimal bắt buộc exact ngay từ khi người dùng nhập, không nên đi vòng qua double trước bước này. Nên dùng BigDecimal từ chuỗi và `intValueExact()`.

## 9. Bộ kiểm thử mạnh hơn: các ý tưởng mở rộng có kiểm soát

Bộ 30 case hiện tại chứng minh các nhánh quan trọng, nhưng **không có nghĩa bao phủ mọi đầu vào**. Một hướng mở rộng thích hợp:

### 9.1. Kiểm thử tham số hóa

Thay vì lặp lại câu lệnh cho từng giá, có thể tạo bảng dữ liệu gồm input, kiểu lỗi/kết quả dự kiến và chạy qua từng dòng. Khi chuyển sang JUnit 5, có thể tổ chức kiểm thử tham số hóa qua `@ParameterizedTest` cùng nguồn dữ liệu tương ứng.

### 9.2. Kiểm thử thuộc tính (property-based)

Ví dụ, với mọi đơn giá hợp lệ, số lượng dương và thuế suất 0:

- `subtotal == total` khi tính toán vừa long.
- `tax == 0`.
- `subtotal >= 0` theo miền đầu vào.
- Kết quả ở dạng cents phải là số nguyên long, không tồn tại phần lẻ cents.

Với thuế suất từ 0 đến 1 và subtotal không âm, thuế đã làm tròn không âm; tổng không âm nếu không có overflow. Những thuộc tính này hữu ích hơn một vài mẫu số cụ thể nhưng cần chú ý **loại trừ hoặc kỳ vọng exception ở phép tính overflow**.

### 9.3. Kiểm thử cấu trúc input

Thử null, chuỗi rỗng, Unicode digits nếu hợp đồng chỉ cho ASCII, nhiều dấu chấm, dấu cộng, dấu trừ, dấu phân cách hàng nghìn, số mũ, khoảng trắng ở đầu/cuối và chuỗi cực dài. Một parser có thể chấp nhận nhiều thứ hơn hợp đồng, nên không thể chỉ dựa vào việc tạo được BigDecimal là kết luận input hợp lệ.

### 9.4. Kiểm thử ranh giới làm tròn

Ngoài 0.5 và 1.5 cents, khảo sát các giá trị đúng dưới, đúng tại và đúng trên nửa bước, cùng các trạng thái có chữ số giữ lại chẵn/lẻ. Kiểm tra riêng thời điểm làm tròn: **mỗi dòng hóa đơn trước khi cộng** khác với **cộng các dòng rồi làm tròn toàn bộ**. Không đổi chính sách mà không cập nhật test.

### 9.5. Kiểm thử hiệu năng và tài nguyên

Đo cách hệ thống xử lý danh sách nhiều dòng; giới hạn kích thước input trước khi parse BigInteger/BigDecimal nhằm tránh lạm dụng bộ nhớ/CPU. Test hiệu năng cần môi trường ổn định và không nên trộn trực tiếp vào bộ test đúng-sai có stdout cố định.

## 10. Các lỗi phổ biến được ví dụ này phòng tránh

**Lỗi 1 — Dùng double cho tiền rồi so bằng dấu ==.** Số thập phân như 0.1 không được biểu diễn exact trong binary64. Ví dụ giữ cents long và tính tỷ lệ bằng BigDecimal.

**Lỗi 2 — Đổi số decimal sang int/long bằng cast hoặc intValue().** Có thể cắt phần thập phân hoặc tràn. Mã sử dụng `longValueExact` và `intValueExact`.

**Lỗi 3 — Gán một phép tính đã tràn vào kiểu rộng hơn.** `long x = intA * intB` vẫn có thể tràn int trước khi gán. Mã dùng phép tính long và `Math.multiplyExact`.

**Lỗi 4 — Tin rằng các trường riêng lẻ hợp lệ thì tổng chắc chắn hợp lệ.** Đơn giá bằng Long.MAX_VALUE cents và số lượng 2 tạo overflow ở subtotal. Mỗi phép tính phái sinh cũng cần bảo vệ.

**Lỗi 5 — Làm tròn ở nhiều bước không kiểm soát.** Cần chọn rõ policy và thời điểm làm tròn. Ở đây chỉ làm tròn thuế về cents một lần bằng HALF_EVEN.

**Lỗi 6 — Bỏ qua ranh giới nửa bước.** Test 0.5/1.5 cents giúp phân biệt HALF_EVEN với các mode khác.

**Lỗi 7 — Đồng nhất “parse được” với “đầu vào được phép”.** BigDecimal chấp nhận nhiều cách viết số; app có thể cấm exponent, dấu âm, dấu phẩy hoặc khoảng trắng.

**Lỗi 8 — Bắt mọi exception rồi trả tiền bằng 0.** Dữ liệu không hợp lệ không phải số 0. Nên từ chối có kiểm soát, không biến lỗi thành một giao dịch hợp lệ.

**Lỗi 9 — Chỉ viết happy path.** Bộ test cần cả giá trị hợp lệ tại biên và ngay ngoài biên, NaN/Infinity, overflow, input null và số lượng sai.

**Lỗi 10 — Sửa code rồi chỉ nhìn stdout cuối.** Test nên có assertion độc lập cho subtotal, tax và total. Một tổng đúng tình cờ không đảm bảo các thành phần đúng.

## 11. Bài tập tự thực hành

1. **Thay đổi chính sách giá:** Cho phép chuỗi `1.000` khi giá trị số đổi sang cents vẫn exact. Cập nhật parser và test; phân biệt điều kiện cú pháp với điều kiện giá trị.
2. **Giới hạn nghiệp vụ:** Đặt số lượng tối đa 10.000 thay vì Integer.MAX_VALUE; thêm test 10.000 hợp lệ và 10.001 bị từ chối.
3. **Cấu hình rounding:** Truyền `RoundingMode` vào phương thức quote nhưng chỉ cho phép các mode do nghiệp vụ phê duyệt. Chứng minh trường hợp 0.5 cents thay đổi kết quả khi đổi mode.
4. **Tính nhiều dòng:** Viết hàm cộng nhiều Receipt bằng `Math.addExact`, chọn rõ liệu thuế làm tròn theo từng dòng hay theo tổng.
5. **Phân loại lỗi:** Tạo mã lỗi riêng cho cú pháp, miền giá trị, vượt long và làm tròn không cho phép; viết test xác minh từng loại.
6. **Ngẫu nhiên tái lập:** Tạo dữ liệu test bổ sung bằng một Random với seed cố định, vẫn giữ các case biên viết tay. Dữ liệu ngẫu nhiên không thay thế các phản ví dụ đã biết.
7. **Lưu trữ và hiển thị:** Kiểm tra công cụ format tiền theo locale ở giao diện nhưng vẫn giữ số cents nguyên để tính toán; tránh parse lại chuỗi đã format.

### Hướng giải cho bài nhiều dòng

Giả sử có ba đối tượng Receipt đã tính xong, và chính sách là **thuế được làm tròn theo từng dòng**, thì tổng cộng:

~~~java
long subtotal = 0L;
long tax = 0L;
for (NumericBoundaryLab.Receipt r : receipts) {
    subtotal = Math.addExact(subtotal, r.subtotalCents());
    tax = Math.addExact(tax, r.taxCents());
}
long total = Math.addExact(subtotal, tax);
~~~

Đây là **mảnh code minh họa**, yêu cầu biến `receipts` đã được khai báo trong cùng package/ngữ cảnh truy cập record, và tuân theo chính sách thuế làm tròn từng dòng. Nếu nghiệp vụ quy định chỉ tính thuế trên tổng toàn hóa đơn, thuật toán và các test sẽ khác. Không tự giả định hai cách luôn cho một kết quả.

## 12. Checklist khi chốt ví dụ tích hợp

- Đã định nghĩa rõ **cú pháp input**, **miền nghiệp vụ**, **đơn vị** và **kiểu lưu**?
- Có giữ giá trị nguồn đủ chính xác cho tới khi các kiểm tra xong?
- Những phép chuyển hẹp có dùng các hàm **Exact** hoặc kiểm tra range/phần lẻ?
- Các phép tính có dùng `Math.*Exact` khi kiểu nguyên có thể overflow?
- Có phân biệt rõ **rounding cho phép** với **mất dữ liệu không được phép**?
- Có kiểm tra ngay tại và ngay ngoài giá trị min/max, cũng như điểm giữa làm tròn?
- Có kiểm tra null, chuỗi sai, NaN/Infinity khi đầu vào là kiểu dấu phẩy động?
- Bài test có thật sự thất bại nếu giá trị hoặc loại exception không đúng?
- Test có chạy được bằng lệnh `javac`/`java` trên JDK 17 và cho kết quả xác định?
- Nếu nâng cấp thành ứng dụng sản xuất, đã xem xét giới hạn tài nguyên, thuế/tiền tệ thực tế, lỗi API và khả năng quan sát hệ thống?

**Kết luận:** Một ví dụ số học đúng không chỉ là công thức tính tiền. Nó còn bao gồm **hợp đồng input**, **chuyển đổi chính xác**, **phát hiện overflow**, **rounding minh bạch**, **kiểu dữ liệu phù hợp** và **bộ kiểm thử dữ liệu biên có thể chạy lại**. Chương trình trên chứng minh toàn bộ chuỗi đó trên một bài toán nhỏ, từ chuỗi ban đầu cho tới kết quả cents và 30 case xác minh tự động.


</details>

- [Quay lại đầu trang](#back-to-top)
