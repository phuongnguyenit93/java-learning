<a id="back-to-top"></a>

# So sánh

## Menu
- [Toán tử &lt;, &lt;=, &gt; và &gt;= cho giá trị số](#comparisons-001)
- [== và != trên kiểu nguyên thủy](#comparisons-002)
- [So sánh boolean và giới hạn dùng toán tử quan hệ](#comparisons-003)
- [Phân biệt so sánh giá trị với so sánh tham chiếu](#comparisons-004)
- [NaN và những kết quả so sánh khác trực giác](#comparisons-005)
- [So sánh số thực bằng sai số phù hợp yêu cầu](#comparisons-006)
- [Không viết chuỗi so sánh kiểu toán học như 0 &lt; x &lt; 10](#comparisons-007)
- [Xây dựng điều kiện kiểm tra miền giá trị rõ ràng](#comparisons-008)

## <a id="comparisons-001">Toán tử &lt;, &lt;=, &gt; và &gt;= cho giá trị số</a>

<details>
<summary>Xem chi tiết</summary>

# Toán tử `<`, `<=`, `>` và `>=` cho giá trị số — Java 17+

Java cung cấp bốn **toán tử so sánh quan hệ** (*relational operators*) để kiểm tra thứ tự của hai giá trị số. Mỗi phép so sánh có **hai toán hạng** và trả về **`boolean`**:

| Toán tử | Ý nghĩa | Ví dụ | Kết quả |
| --- | --- | --- | --- |
| `a < b` | `a` nhỏ hơn `b` | `3 < 5` | `true` |
| `a <= b` | `a` nhỏ hơn **hoặc bằng** `b` | `5 <= 5` | `true` |
| `a > b` | `a` lớn hơn `b` | `9 > 5` | `true` |
| `a >= b` | `a` lớn hơn **hoặc bằng** `b` | `5 >= 9` | `false` |

**Phân biệt quan trọng:** `<` và `>` là so sánh **nghiêm ngặt**, không bao gồm bằng nhau; `<=` và `>=` bao gồm trường hợp hai giá trị bằng nhau. Các dấu `<=`, `>=` được viết liền nhau; `=<` hoặc `=>` không phải toán tử Java.

~~~java
int age = 18;
boolean adult = age >= 18;   // true.
boolean minor = age < 18;    // false.
~~~

Toán tử không thay đổi giá trị của `age`. Nó chỉ sinh ra kết quả `boolean` có thể sử dụng trong `if`, `while`, `&&`, `||` hoặc biến boolean.

## 1. Điều kiện biên và tính đúng sai

Với hai giá trị số bình thường và không chứa NaN:

| Trường hợp | `a < b` | `a <= b` | `a > b` | `a >= b` |
| --- | --- | --- | --- | --- |
| `a = 3`, `b = 5` | true | true | false | false |
| `a = 5`, `b = 5` | false | true | false | true |
| `a = 9`, `b = 5` | false | false | true | true |

Một cách đọc dễ nhớ là điều kiện ở dấu `<=` hoặc `>=` đúng **khi một trong hai điều kiện “nhỏ/lớn hơn” hoặc “bằng” được thỏa mãn**.

Đừng lẫn với `==` và `!=`: đó là các phép kiểm tra **bằng/khác**, không phải toán tử xác định thứ tự. `a >= b` cũng không có nghĩa “`a` khác `b`”.

## 2. Demo Java 17+ chạy độc lập

Tạo file UTF-8 tên **`NumericRelationalDemo.java`**:

~~~java
public class NumericRelationalDemo {
    public static void main(String[] args) {
        int a = 7;
        int b = 10;

        System.out.println("less=" + (a < b));
        System.out.println("lessOrEqual=" + (a <= b));
        System.out.println("greater=" + (a > b));
        System.out.println("greaterOrEqual=" + (a >= b));

        System.out.println("equalLess=" + (a < a));
        System.out.println("equalLessOrEqual=" + (a <= a));
        System.out.println("equalGreater=" + (a > a));
        System.out.println("equalGreaterOrEqual=" + (a >= a));

        char firstLetter = 'A';
        char secondLetter = 'B';
        System.out.println("charComparison="
                + (firstLetter < secondLetter));

        int whole = 5;
        double fraction = 5.5;
        System.out.println("mixedIntDouble="
                + (whole < fraction));
        System.out.println("mixedLongDouble="
                + (10L > 9.5));

        Integer boxedInt = 7;
        Long boxedLong = 8L;
        System.out.println("wrapperComparison="
                + (boxedInt < boxedLong));

        int preciseInt = 16_777_217;
        float roundedFloat = 16_777_216f;
        System.out.println("intFloatGreater="
                + (preciseInt > roundedFloat));
        System.out.println("intFloatLessOrEqual="
                + (preciseInt <= roundedFloat));

        double nan = Double.NaN;
        System.out.println("nanLess=" + (nan < 5.0));
        System.out.println("nanLessOrEqual=" + (nan <= 5.0));
        System.out.println("nanGreater=" + (nan > 5.0));
        System.out.println("nanGreaterOrEqual=" + (nan >= 5.0));
        System.out.println("notNanGreater="
                + (!(nan > 5.0)));
        System.out.println("isNaN=" + Double.isNaN(nan));

        double positiveZero = 0.0;
        double negativeZero = -0.0;
        System.out.println("zeroLess="
                + (negativeZero < positiveZero));
        System.out.println("zeroLessOrEqual="
                + (negativeZero <= positiveZero));
        System.out.println("zeroGreaterOrEqual="
                + (negativeZero >= positiveZero));

        System.out.println("positiveInfinityGreater="
                + (Double.POSITIVE_INFINITY > Double.MAX_VALUE));
        System.out.println("negativeInfinityLess="
                + (Double.NEGATIVE_INFINITY < -Double.MAX_VALUE));
        System.out.println("infinityGreaterOrEqualSelf="
                + (Double.POSITIVE_INFINITY
                        >= Double.POSITIVE_INFINITY));

        Integer missing = null;
        try {
            boolean unused = missing < 10;
            System.out.println("unexpected=" + unused);
        } catch (NullPointerException ex) {
            System.out.println("nullUnboxing=NullPointerException");
        }

        int overflowed = Integer.MAX_VALUE + 1;
        System.out.println("overflowedValue=" + overflowed);
        System.out.println("overflowedGreaterZero="
                + (overflowed > 0));
        long promotedBeforeAdd = (long) Integer.MAX_VALUE + 1;
        System.out.println("safeGreaterZero="
                + (promotedBeforeAdd > 0));

        boolean inRange = (a >= 0) && (a < b);
        System.out.println("inRange=" + inRange);
    }
}
~~~

Sử dụng **JDK 17 trở lên**, mở PowerShell (Windows) hoặc Bash (Linux/macOS) trong thư mục chứa file:

~~~bash
java -version
javac -version
javac -encoding UTF-8 NumericRelationalDemo.java
java NumericRelationalDemo
~~~

**Exact expected stdout** của riêng lệnh `java NumericRelationalDemo`:

~~~text
less=true
lessOrEqual=true
greater=false
greaterOrEqual=false
equalLess=false
equalLessOrEqual=true
equalGreater=false
equalGreaterOrEqual=true
charComparison=true
mixedIntDouble=true
mixedLongDouble=true
wrapperComparison=true
intFloatGreater=false
intFloatLessOrEqual=true
nanLess=false
nanLessOrEqual=false
nanGreater=false
nanGreaterOrEqual=false
notNanGreater=true
isNaN=true
zeroLess=false
zeroLessOrEqual=true
zeroGreaterOrEqual=true
positiveInfinityGreater=true
negativeInfinityLess=true
infinityGreaterOrEqualSelf=true
nullUnboxing=NullPointerException
overflowedValue=-2147483648
overflowedGreaterZero=false
safeGreaterZero=true
inRange=true
~~~

Đầu ra của `java -version` và `javac -version` phụ thuộc JDK cài đặt, nên không nằm trong khối expected stdout cố định.

## 3. Kiểu toán hạng và phép nâng kiểu số hai ngôi

Bốn toán tử quan hệ trên dùng cho **kiểu số**: `byte`, `short`, `char`, `int`, `long`, `float`, `double` và các lớp bao số thích hợp được **unbox**. Khi hai toán hạng có kiểu khác nhau, Java áp dụng **binary numeric promotion** trước khi so sánh.

| Hai kiểu toán hạng | Kiểu được dùng khi so sánh | Ví dụ |
| --- | --- | --- |
| `byte` với `short` | `int` | `(byte) 1 < (short) 2` |
| `char` với `int` | `int` | `'A' < 66` |
| `int` với `long` | `long` | `5 < 6L` |
| `int` với `float` | `float` | `5 < 5.5f` |
| `long` với `double` | `double` | `10L > 9.5` |
| `float` với `double` | `double` | `1.5f < 2.0` |
| `Integer` với `Long` | Unbox rồi so theo `long` | `Integer.valueOf(7) < Long.valueOf(8)` |

**Kiểu của phép so sánh luôn là `boolean`**, không phải `int`/`double`. Trong `int n = 3; boolean result = n < 5;`, `n` vẫn là `int` và `result` là `boolean`.

### So sánh `char`

`char` là **đơn vị mã UTF-16 không dấu**, nên Java so sánh **giá trị số của đơn vị mã**, không dùng thứ tự từ điển theo ngôn ngữ:

~~~java
char a = 'A';
char b = 'B';
System.out.println(a < b); // true: 65 < 66.
~~~

Dùng `Collator` hoặc quy tắc so sánh chuỗi phù hợp nếu bài toán là sắp xếp tên/chuỗi theo ngôn ngữ tự nhiên. Không coi so sánh hai `char` là một bộ máy so sánh văn bản hoàn chỉnh.

### Wrapper và nguy cơ `null`

~~~java
Integer number = 7;
System.out.println(number < 10); // true do unboxing.

Integer missing = null;
// boolean check = missing < 10; // Khi chay nem NullPointerException.
~~~

Vì relational operator cần giá trị số nguyên thủy, wrapper thích hợp được unbox. Nếu wrapper là `null`, việc unbox ném `NullPointerException`. Trước khi so sánh dữ liệu có thể thiếu, hãy xác định quy tắc xử lý null, ví dụ:

~~~java
Integer input = null;
boolean valid = input != null && input >= 0;
// false, vế phải && không chạy nếu input null.
~~~

Với các kiểu tham chiếu như `String`, `Boolean` hoặc đối tượng không phải giá trị số có thể unbox, `<`/`<=`/`>`/`>=` không hợp lệ.

## 4. So sánh số nguyên và số thực: làm tròn khi nâng kiểu

Không phải mọi phép so sánh giữa `int`/`long` và `float`/`double` đều giữ nguyên khả năng phân biệt từng số nguyên.

`float` có khoảng **24 bit độ chính xác của phần trị**. Hai số nguyên lớn khác nhau có thể trở thành cùng một giá trị khi được chuyển thành `float`:

~~~java
int preciseInt = 16_777_217;
float roundedFloat = 16_777_216f;

System.out.println(preciseInt > roundedFloat);  // false.
System.out.println(preciseInt <= roundedFloat); // true.
~~~

Tại phép so sánh, `preciseInt` được đổi sang `float` và làm tròn thành **16_777_216f**, bằng `roundedFloat`. Đây không phải Java “so sánh sai”; kết quả tuân theo **quy tắc chuyển kiểu trước phép so sánh**.

`double` có khoảng **53 bit độ chính xác của phần trị**, vì vậy `long` rất lớn có thể gặp vấn đề tương tự khi so với `double`.

Nếu nghiệp vụ cần so sánh chính xác các số nguyên rất lớn, hãy chọn biểu diễn và kiểu toán hạng thích hợp (ví dụ cùng `long`, hoặc `BigInteger.compareTo` khi vượt miền `long`) thay vì âm thầm chuyển qua số thực xấp xỉ.

## 5. Trường hợp số thực đặc biệt: NaN, vô cực và zero có dấu

Các phép quan hệ `<`, `<=`, `>` và `>=` với `NaN` đều trả `false`; vì vậy phủ định một quan hệ không nhất thiết tương đương quan hệ đối nghịch:

~~~java
double x = Double.NaN;
System.out.println(x > 5.0);    // false
System.out.println(!(x > 5.0)); // true
System.out.println(x <= 5.0);   // false
~~~

Dùng `Double.isNaN(x)` để nhận diện NaN; khi dữ liệu chỉ được phép là số hữu hạn, dùng `Double.isFinite(x)` để loại cả NaN và vô cực.

`Double.POSITIVE_INFINITY` lớn hơn mọi số `double` hữu hạn; `Double.NEGATIVE_INFINITY` nhỏ hơn mọi số hữu hạn. Hai giá trị `+0.0` và `-0.0` không nhỏ hơn/lớn hơn nhau theo toán tử quan hệ primitive, nhưng `<=` và `>=` giữa chúng đều đúng. Các API như `Double.compare` và `Double.equals` có quy tắc riêng cho NaN và zero có dấu, được giải thích kỹ trong bài **NaN và những kết quả so sánh khác trực giác**.

## 6. Cú pháp, độ ưu tiên và điều kiện ghép

Bốn toán tử này có **độ ưu tiên thấp hơn phép cộng/nhân** nhưng **cao hơn `&&` và `||`**:

~~~java
int price = 8;
int tax = 2;
boolean affordable = price + tax <= 10; // true.

boolean range = (price >= 0) && (price < 10); // true.
~~~

Dấu ngoặc giúp biểu thị rõ ý nghĩa của điều kiện khoảng:

~~~java
int value = 7;
boolean inRange = value >= 0 && value < 10; // true.
~~~

Java **không hỗ trợ viết liên tiếp ba giá trị theo kiểu toán học `0 <= x < 10`**:

~~~java
int x = 7;
// boolean invalid = 0 <= x < 10; // Loi bien dich.
boolean valid = (0 <= x) && (x < 10);
~~~

Biểu thức `0 <= x` đã trả `boolean`, không thể làm toán hạng số của phép `< 10` tiếp theo. Cần tách thành hai phép so sánh rồi ghép bằng `&&`.

Lưu ý toán tử quan hệ trả `boolean` nên không dùng phép cộng hoặc trừ trực tiếp hai kết quả quan hệ như với số nguyên.

## 7. Tràn số trong **biểu thức trước** phép so sánh

Đúng toán tử quan hệ chưa đủ nếu toán hạng đã được tính sai vì overflow:

~~~java
int a = Integer.MAX_VALUE;
int overflowed = a + 1;

System.out.println(overflowed);     // -2147483648.
System.out.println(overflowed > 0); // false.
~~~

`a + 1` được đánh giá theo `int` trước khi phép so sánh diễn ra. Để cần kết quả lớn hơn `int`, **nâng kiểu trước phép tính**, không chỉ ép kiểu kết quả sau phép tính:

~~~java
int a = Integer.MAX_VALUE;

long correct = (long) a + 1;
System.out.println(correct > 0); // true.
~~~

Nếu bài toán yêu cầu **báo lỗi khi overflow** thay vì chuyển sang `long`, cân nhắc `Math.addExact`; với miền số nguyên vượt `long`, dùng `BigInteger.compareTo`. Với tính toán thập phân đòi hỏi chính xác, cân nhắc `BigDecimal.compareTo` và kiểm soát chính sách scale/rounding.

## 8. So sánh kiểu số và so sánh chuỗi là hai việc khác nhau

~~~java
int number = 12;
System.out.println(number > 9); // true.

// String text = "12";
// boolean invalid = text > "9"; // Loi bien dich.
~~~

`String` không phải kiểu số, ngay cả khi chứa chữ số. Muốn so **giá trị số**, phải parse/validate trước:

~~~java
int parsed = Integer.parseInt("12");
boolean numericGreater = parsed > 9; // true.
~~~

Muốn so **thứ tự từ điển của String**, dùng `String.compareTo(...)` hoặc bộ so sánh phù hợp thay vì toán tử `>`. Ví dụ `"12".compareTo("9") < 0` theo thứ tự từ điển ký tự, khác so sánh số 12 và 9.

## 9. Các lỗi thường gặp

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| “`<`/`<=` trả số 0 hoặc 1.” | Chúng trả `boolean` |
| “`a <= b` chỉ đúng khi `a` nhỏ hơn `b`.” | Cũng đúng khi bằng nhau (trừ trường hợp bất thường như NaN) |
| “`0 <= x < 10` hợp lệ trong Java.” | Không; phải viết `0 <= x && x < 10` |
| “Hai wrapper số đem so bằng `<` sẽ so địa chỉ.” | Các wrapper số được unbox để so giá trị số |
| “Wrapper null so với số cho false.” | Có thể ném `NullPointerException` do unboxing |
| “`int` so với `float` luôn chính xác tuyệt đối.” | Numeric promotion có thể làm tròn số nguyên lớn |
| “`!(x > y)` luôn giống `x <= y`.” | Sai khi `x` hoặc `y` là NaN |
| “`+0.0` nhỏ hơn `-0.0` vì bit dấu khác.” | Chúng bằng nhau theo phép quan hệ primitive |
| “Thêm `> 0` tự sửa tràn số của toán hạng.” | Phải xử lý overflow **trước** khi so sánh |

## 10. Bài tập tự kiểm tra

1. `int a = 5, b = 5;` thì `a < b`, `a <= b`, `a > b` và `a >= b` là gì?  
   *Đáp án:* `false`, `true`, `false`, `true`.
2. `char a = 'A'; boolean c = a < 'B';` cho gì?  
   *Đáp án:* `true` vì so giá trị mã UTF-16 65 và 66.
3. `Integer a = 4; Long b = 5L; boolean c = a < b;` có hợp lệ không?  
   *Đáp án:* Có; unboxing và numeric promotion cho kết quả `true`.
4. `Integer x = null; boolean c = x < 10;` cho gì?  
   *Đáp án:* `NullPointerException` khi unbox.
5. `Double.NaN >= 0.0` và `!(Double.NaN < 0.0)` lần lượt là gì?  
   *Đáp án:* `false` và `true`.
6. Vì sao `int x = 16_777_217; float f = 16_777_216f; x > f` có thể là false?  
   *Đáp án:* `x` được chuyển sang `float` và làm tròn thành giá trị bằng `f`.
7. Viết điều kiện `10 <= score <= 20` trong Java.  
   *Đáp án:* `score >= 10 && score <= 20`.
8. `(Integer.MAX_VALUE + 1) > 0` là gì?  
   *Đáp án:* `false` do phép cộng `int` tràn trước khi so sánh.

## Checklist

- [ ] Phân biệt `<`, `<=`, `>`, `>=` và nhớ kết quả đều là `boolean`.
- [ ] Áp dụng so sánh quan hệ lên giá trị số, không lên `String`/`boolean`.
- [ ] Hiểu **unboxing** của wrapper và **binary numeric promotion**.
- [ ] Cẩn thận mất chính xác khi so số nguyên lớn với `float`/`double`.
- [ ] Với NaN, cả bốn phép quan hệ đều false; `!(a > b)` không luôn giống `a <= b`.
- [ ] Hiểu hai zero `+0.0` và `-0.0` so quan hệ bằng nhau.
- [ ] Viết điều kiện khoảng bằng hai phép so sánh nối bởi `&&`.
- [ ] Kiểm tra phép tính toán hạng có overflow trước khi tin vào kết quả so sánh.

**Kết luận:** `<`, `<=`, `>`, `>=` là các **toán tử quan hệ cho giá trị số** trong Java. Dùng chúng đúng không chỉ là nhớ dấu mà còn cần hiểu **kiểu số được so sánh**, **nâng kiểu**, **NaN** và **các phép tính xảy ra trước phép so sánh**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="comparisons-002">== và != trên kiểu nguyên thủy</a>

<details>
<summary>Xem chi tiết</summary>

# `==` và `!=` trên kiểu nguyên thủy — Java 17+

## 1. Mục tiêu và quy tắc chính

Trong Java, hai toán tử so sánh bằng trên **kiểu nguyên thủy** (*primitive*) là:

- `a == b`: trả `true` nếu hai giá trị bằng nhau theo quy tắc của kiểu dữ liệu.
- `a != b`: trả `true` nếu hai giá trị không bằng nhau theo cùng quy tắc.

**Kết quả của cả hai toán tử luôn có kiểu `boolean`.** `!=` là phủ định kết quả của `==` khi đánh giá cùng hai toán hạng và cùng trạng thái.

~~~java
int x = 7;
int y = 7;
int z = 9;

System.out.println(x == y); // true
System.out.println(x != z); // true
System.out.println(x == z); // false
~~~

Với số nguyên, Java so sánh **giá trị số**, không so địa chỉ bộ nhớ. Với `boolean`, so sánh đúng/sai. Với `float` và `double`, so sánh theo quy tắc dấu phẩy động, có các trường hợp đặc biệt như `NaN`, `+0.0` và `-0.0`.

Bài này nói về **so sánh giá trị nguyên thủy**. Toán tử `==` giữa hai biến tham chiếu đối tượng có quy tắc khác; không được áp dụng suy luận của kiểu nguyên thủy một cách máy móc cho `String` hoặc wrapper.

## 2. Các kiểu nguyên thủy được so sánh như thế nào?

Java có tám kiểu nguyên thủy:

| Nhóm | Kiểu | `==`/`!=` hoạt động thế nào? |
|---|---|---|
| Số nguyên có dấu | `byte`, `short`, `int`, `long` | So sánh giá trị số sau chuyển đổi số học nếu cần |
| Mã UTF-16 không dấu | `char` | So sánh giá trị số của code unit, có thể so với kiểu số khác |
| Dấu phẩy động | `float`, `double` | So giá trị floating-point, có quy tắc `NaN` và zero có dấu |
| Logic | `boolean` | `true == true`, `false == false` là true; khác nhau là false |

Các kiểu số có thể tham gia so sánh số học với nhau, ví dụ `int == long` hoặc `char == int`. Nhưng **`boolean` không được trộn với kiểu số**:

~~~java
boolean ready = true;
System.out.println(ready == true);  // true
System.out.println(ready != false); // true

// boolean invalid = ready == 1; // LỖI COMPILE: boolean không so với int
~~~

Java không xem `true` như số nguyên 1 hoặc `false` như số 0.

## 3. So sánh `int`, `long`, `byte`, `short` và `char`

Với toán hạng số, Java áp dụng **binary numeric promotion** trước khi so sánh:

1. Nếu một toán hạng là `double`, chuyển toán hạng còn lại sang `double`.
2. Nếu không, nếu có `float`, chuyển sang `float`.
3. Nếu không, nếu có `long`, chuyển sang `long`.
4. Còn lại, so ở kiểu `int`; `byte`, `short`, `char` được nâng lên `int`.

Ví dụ:

~~~java
byte small = 10;
short medium = 10;
int count = 10;
long wide = 10L;

System.out.println(small == medium); // true
System.out.println(count == wide);   // true
System.out.println('A' == 65);       // true
System.out.println(10 != 11L);       // true
~~~

Ở `'A' == 65`, `char` mang giá trị UTF-16 bằng 65 và được chuyển sang `int` để so; điều này **không đồng nghĩa** Java đang so sánh chữ `"A"` kiểu `String` với một số.

Khi chỉ so các **kiểu số nguyên** ở miền giá trị biểu diễn được, widening từ `int` sang `long` không làm mất độ chính xác. Nhưng khi có `float` hoặc `double`, việc chuyển từ số nguyên rất lớn **có thể làm tròn**, cần chú ý phần sau.

### Giá trị và kiểu của biểu thức so sánh

~~~java
int left = 4;
long right = 4L;

var same = left == right; // same có kiểu boolean, giá trị true
System.out.println(same); // true
~~~

`var` suy ra `boolean` từ biểu thức `==`, không biến giá trị so sánh thành số hoặc chuỗi.

## 4. `==` không phải `=`

| Cú pháp | Ý nghĩa | Ví dụ |
|---|---|---|
| `=` | Gán giá trị vào biến | `x = 7;` |
| `==` | Kiểm tra hai giá trị bằng nhau | `x == 7` |
| `!=` | Kiểm tra hai giá trị khác nhau | `x != 7` |

Ví dụ dễ mắc lỗi:

~~~java
boolean allowed = false;
if (allowed = true) { // Hợp lệ, nhưng GÁN true thay vì so sánh
    System.out.println("entered");
}
~~~

**Expected output:**

~~~text
entered
~~~

Trong Java, phép gán `allowed = true` là biểu thức có giá trị boolean `true`. Compiler chấp nhận, nhưng chương trình có thể sai nghiệp vụ.

Để kiểm tra boolean, ưu tiên:

~~~java
if (allowed) {
    // Chỉ chạy nếu allowed vốn đã true
}
if (!allowed) {
    // Chỉ chạy nếu allowed vốn là false
}
~~~

Với biến số `int`, `if (x = 1)` **không hợp lệ** do điều kiện `if` không thể là `int`; đừng áp dụng kết luận đó sang trường hợp gán `boolean`.

## 5. `float`/`double`: so chính xác giá trị đã biểu diễn, không có dung sai ngầm

Điểm dễ sai nhất khi dùng `==` với số thực:

~~~java
double calculated = 0.1 + 0.2;

System.out.println(calculated);        // 0.30000000000000004
System.out.println(calculated == 0.3); // false
System.out.println(calculated != 0.3); // true
~~~

`0.1`, `0.2` và `0.3` có thể không biểu diễn chính xác trong nhị phân hữu hạn. Biểu thức `0.1 + 0.2` được làm tròn và tạo giá trị `double` khác với giá trị biểu diễn của literal `0.3`.

**`==` không tự so “gần bằng”.** Hai số cùng hiển thị thành `0.30` khi định dạng hai chữ số sau dấu phẩy không nhất thiết có cùng giá trị nhị phân.

~~~java
double difference = (0.1 + 0.2) - 0.3;
System.out.println(difference); // 5.551115123125783E-17
~~~

Tuy nhiên, không nên kết luận rằng `==` trên `double` **luôn sai**: nếu bài toán cố ý kiểm tra sự bằng nhau của **giá trị floating-point đã được biểu diễn**, `==` là toán tử hợp lệ. Vấn đề là không được dùng nó để thay một khái niệm **bằng nhau trong phạm vi sai số** mà chưa định nghĩa rõ.

## 6. `float` so với `double` có thể khác dù cùng literal thập phân

~~~java
float f = 0.1f;
double d = 0.1;

System.out.println((double) f); // 0.10000000149011612
System.out.println(f == d);     // false
~~~

`0.1f` được làm tròn về định dạng **binary32**, còn `0.1` là literal **binary64**. Khi so sánh, `float f` được nâng sang `double`, nhưng **giá trị đã bị làm tròn ở float không được phục hồi**.

Quy tắc tổng quát: cùng cách viết thập phân **không bảo đảm** hai biến `float` và `double` mang cùng giá trị sau chuyển kiểu.

## 7. So sánh số nguyên lớn với số thực: bẫy làm tròn

Khi một toán hạng số nguyên được chuyển sang `float` hoặc `double`, những số nguyên đủ lớn có thể **mất khả năng phân biệt từng đơn vị**.

~~~java
int exactInt = 16_777_217;          // 2^24 + 1
float roundedFloat = 16_777_216f;   // 2^24

System.out.println(exactInt == roundedFloat); // true
~~~

Kết quả tưởng vô lý nhưng hợp lệ: Java chuyển `int` sang `float` theo numeric promotion. Tại độ lớn `2^24`, binary32 không biểu diễn mọi số nguyên liên tiếp; `16_777_217` được làm tròn về `16_777_216f`, nên hai toán hạng cuối cùng bằng nhau.

Tương tự:

~~~java
long exactLong = 9_007_199_254_740_993L;   // 2^53 + 1
double roundedDouble = 9_007_199_254_740_992d; // 2^53

System.out.println(exactLong == roundedDouble); // true
~~~

Đây **không** phải `long` có cùng giá trị toán học với `double` ban đầu; kiểu `long` bị chuyển thành `double` cho phép so sánh và mất một đơn vị.

**Khuyến nghị:** khi nghiệp vụ cần so ID, số đếm hay tiền dưới dạng số nguyên lớn, **giữ cùng họ kiểu số nguyên** (`long`, `BigInteger` nếu cần) thay vì trộn với floating-point.

## 8. `NaN`: ngoại lệ của phép bằng và khác

~~~java
double nan = Double.NaN;
System.out.println(nan == nan); // false
System.out.println(nan != nan); // true
~~~

Với kiểu nguyên thủy `float`/`double`, NaN không bằng bất cứ giá trị nào kể cả chính nó; `!=` trả `true` nếu có NaN. Dùng `Double.isNaN` hoặc `Float.isNaN` để nhận diện, thay vì so với hằng NaN. `Double.equals` và `Double.compare` dùng các quy tắc khác cho wrapper và thứ tự sắp xếp; bài riêng về **NaN** trình bày ví dụ chi tiết.

## 9. `+0.0` và `-0.0` bằng nhau dưới `==`

~~~java
System.out.println(0.0 == -0.0); // true
System.out.println(0.0 != -0.0); // false
System.out.println(1.0 / -0.0);  // -Infinity
~~~

Zero có dấu khác nhau về bit biểu diễn và có thể ảnh hưởng các phép tính như nghịch đảo, nhưng primitive `==` không phân biệt. Khi yêu cầu phải phân biệt dấu zero hoặc bit, chọn API như `Double.compare` hay `Double.doubleToRawLongBits` theo hợp đồng.

## 10. Infinity và dữ liệu hữu hạn

~~~java
System.out.println(Double.POSITIVE_INFINITY == Double.POSITIVE_INFINITY); // true
System.out.println(Double.POSITIVE_INFINITY != Double.NEGATIVE_INFINITY); // true
~~~

Infinity cùng dấu bằng nhau và Infinity trái dấu khác nhau khi dùng primitive `==`/`!=`. Nhưng bằng chính nó không chứng minh số đó hữu hạn: hãy dùng `Double.isFinite` khi đầu vào phải là số thực hữu hạn.

## 11. Khi nào cần so gần bằng nhau?

Giá trị `0.1 + 0.2` khác `0.3` ở biểu diễn binary64, nên `==` trả `false`. Nếu bài toán muốn so kết quả đo hoặc tính toán trong **một mức sai số được phép**, cần định nghĩa dung sai theo đơn vị và quy mô thay vì thay bằng một epsilon tùy ý.

Trong demo cuối bài, `nearlyEqual(a, b, tolerance)` là ví dụ so bằng dung sai tuyệt đối khi **cả hai đầu vào và dung sai hữu hạn**. Bài **So sánh số thực bằng sai số phù hợp yêu cầu** phân tích đầy đủ sai số tuyệt đối, tương đối, kết hợp, miền gần 0 và các giới hạn tràn số.

## 12. Phân biệt primitive với reference `==`

Ở primitive:

~~~java
int a = 1000;
int b = 1000;
System.out.println(a == b); // true
~~~

Với hai object `String` được tạo riêng:

~~~java
String first = new String("Java");
String second = new String("Java");

System.out.println(first == second);      // false: hai tham chiếu khác nhau
System.out.println(first.equals(second)); // true: nội dung bằng nhau
~~~

**Không coi `==` là toán tử so nội dung của mọi object.** Nó so giá trị đối với primitive, nhưng giữa hai reference phù hợp thì so cùng/khác object. `!=` có phân biệt tương ứng.

Một chi tiết quan trọng khi có wrapper:

~~~java
int primitive = 10;
Integer boxed = 10;

System.out.println(primitive == boxed); // true: Integer được unbox để so số
~~~

Nhưng `Integer` có thể là `null`:

~~~java
Integer nullable = null;
// System.out.println(primitive == nullable);
// Có thể ném NullPointerException khi unbox null.
~~~

So **hai reference wrapper** mà không unbox không đồng nghĩa so hai primitive. Khi mục đích là so **giá trị object**, chọn API so sánh phù hợp như `equals` hoặc `Objects.equals`, dựa trên chính sách xử lý null của ứng dụng.

## 13. Lỗi biên dịch và bẫy logic phổ biến

| Viết hoặc suy luận sai | Vấn đề | Cách đúng |
|---|---|---|
| `if (x = 1)` với x là int | Biểu thức gán cho int, không phải boolean | `if (x == 1)` |
| `if (flag = true)` để so boolean | Vô tình gán true; đoạn code vẫn compile | `if (flag)` |
| `true == 1` | Boolean không chuyển sang số | Chỉ so boolean với boolean |
| `'A' == 65` chắc chắn false vì khác kiểu | Numeric promotion so giá trị số | Kết quả `true` |
| `0.1 + 0.2 == 0.3` phải true | Sai số biểu diễn binary64 | Dung sai có chủ đích hoặc decimal exact |
| `0.1f == 0.1` phải true | float/double lưu xấp xỉ khác nhau | Hiểu conversion trước khi so |
| `nan == nan` phải true | NaN không bằng bất kỳ số nào, kể cả nó | `Double.isNaN(nan)` |
| `0.0 != -0.0` phải true | Signed zero so bằng nhau | So bit nếu cần phân biệt |
| `intLarge == floatValue` luôn chính xác | int chuyển sang float có thể làm tròn | Giữ kiểu nguyên khi so giá trị nguyên lớn |
| `Integer a == Integer b` luôn so số | Nếu đều reference, có thể so cùng object | Dùng `equals` hoặc unbox có kiểm tra |
| `wrapper == primitive` không bao giờ lỗi | Wrapper null có thể NPE khi unbox | Kiểm tra null |
| `a != b` là so gần khác nhau | `!=` phủ định phép so bằng chính xác | Định nghĩa tolerance cho dữ liệu đo |

## 14. Ví dụ Java 17 độc lập, chạy được

Lưu file đúng tên **`PrimitiveEqualityDemo.java`**:

~~~java
public final class PrimitiveEqualityDemo {
    private PrimitiveEqualityDemo() {}

    static boolean nearlyEqual(double a, double b, double tolerance) {
        if (!Double.isFinite(tolerance) || tolerance < 0) {
            throw new IllegalArgumentException("invalid tolerance");
        }
        if (!Double.isFinite(a) || !Double.isFinite(b)) {
            return false;
        }
        return Math.abs(a - b) <= tolerance;
    }

    public static void main(String[] args) {
        int a = 7;
        int b = 7;
        int c = 9;
        System.out.println("int equal=" + (a == b));
        System.out.println("int not equal=" + (a != c));

        byte small = 10;
        short medium = 10;
        System.out.println("byte short=" + (small == medium));

        int number = 10;
        long wide = 10L;
        System.out.println("int long=" + (number == wide));
        System.out.println("char numeric=" + ('A' == 65));

        boolean ready = true;
        boolean enabled = false;
        System.out.println("boolean equal=" + (ready == enabled));
        System.out.println("boolean not equal=" + (ready != enabled));

        double calculated = 0.1 + 0.2;
        System.out.println("decimal sum=" + calculated);
        System.out.println("decimal equal=" + (calculated == 0.3));
        System.out.println("decimal not equal=" + (calculated != 0.3));
        System.out.println("decimal near="
                + nearlyEqual(calculated, 0.3, 1e-12));

        float f = 0.1f;
        double d = 0.1;
        System.out.println("float double equal=" + (f == d));

        int exactInt = 16_777_217;
        float roundedFloat = 16_777_216f;
        System.out.println("int float equal=" + (exactInt == roundedFloat));

        long exactLong = 9_007_199_254_740_993L;
        double roundedDouble = 9_007_199_254_740_992d;
        System.out.println("long double equal=" + (exactLong == roundedDouble));

        double nan = Double.NaN;
        System.out.println("NaN equal=" + (nan == nan));
        System.out.println("NaN not equal=" + (nan != nan));
        System.out.println("isNaN=" + Double.isNaN(nan));

        System.out.println("signed zero equal=" + (0.0 == -0.0));
        System.out.println("signed zero not equal=" + (0.0 != -0.0));
        System.out.println("positive zero reciprocal=" + (1.0 / 0.0));
        System.out.println("negative zero reciprocal=" + (1.0 / -0.0));

        System.out.println("same infinity="
                + (Double.POSITIVE_INFINITY == (1.0 / 0.0)));
        System.out.println("opposite infinity="
                + (Double.POSITIVE_INFINITY != Double.NEGATIVE_INFINITY));

        Integer boxed = 10;
        System.out.println("boxed primitive=" + (boxed == number));
        Integer missing = null;
        try {
            System.out.println(number == missing);
        } catch (NullPointerException ex) {
            System.out.println("boxed null=NullPointerException");
        }

        String first = new String("Java");
        String second = new String("Java");
        System.out.println("reference equal=" + (first == second));
        System.out.println("content equal=" + first.equals(second));
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên, terminal ở thư mục chứa `PrimitiveEqualityDemo.java`; không cần Maven/Gradle hoặc thư viện bên ngoài JDK.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 PrimitiveEqualityDemo.java
java -cp . PrimitiveEqualityDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\PrimitiveEqualityDemo.java
java -cp . PrimitiveEqualityDemo
~~~

**Exact expected stdout:**

~~~text
int equal=true
int not equal=true
byte short=true
int long=true
char numeric=true
boolean equal=false
boolean not equal=true
decimal sum=0.30000000000000004
decimal equal=false
decimal not equal=true
decimal near=true
float double equal=false
int float equal=true
long double equal=true
NaN equal=false
NaN not equal=true
isNaN=true
signed zero equal=true
signed zero not equal=false
positive zero reciprocal=Infinity
negative zero reciprocal=-Infinity
same infinity=true
opposite infinity=true
boxed primitive=true
boxed null=NullPointerException
reference equal=false
content equal=true
~~~

### Những dòng dễ bất ngờ

- `int long=true`: giá trị nguyên 10 vẫn giữ chính xác khi chuyển rộng từ int sang long.
- `char numeric=true`: `'A'` có giá trị số 65.
- `decimal equal=false`: `0.1 + 0.2` và `0.3` không phải cùng giá trị binary64.
- `float double equal=false`: `0.1f` và `0.1` được làm tròn ở độ chính xác khác nhau.
- `int float equal=true` / `long double equal=true`: **chuyển kiểu để so có thể mất độ chính xác**, không phải các số nguyên ban đầu bằng nhau về toán học.
- `NaN equal=false`, `NaN not equal=true`: quy tắc NaN đặc biệt.
- `signed zero equal=true`: `+0.0` và `-0.0` bằng nhau dưới toán tử `==`, dù nghịch đảo cho Infinity khác dấu.
- `boxed primitive=true`: unboxing cho phép so giá trị số, còn wrapper null gây NPE.
- `reference equal=false`: `==` giữa hai String reference không so nội dung; `equals` mới cho true.

## 15. Bài tập có đáp án

### Bài A — nguyên thủy với kiểu khác nhau

~~~java
byte a = 65;
char b = 'A';
System.out.println(a == b);
~~~

**Đáp án:** `true`, vì numeric promotion đưa hai toán hạng về int có cùng giá trị 65.

### Bài B — boolean và số

~~~java
boolean active = true;
// System.out.println(active == 1);
~~~

**Đáp án:** dòng comment nếu bỏ comment sẽ **lỗi compile**. Java không so boolean với int.

### Bài C — NaN

~~~java
double x = Double.NaN;
System.out.println(x == x);
System.out.println(x != x);
~~~

**Đáp án:**

~~~text
false
true
~~~

Dùng `Double.isNaN(x)` khi muốn nhận diện NaN.

### Bài D — sai số số thực

~~~java
double sum = 0.1 + 0.2;
System.out.println(sum == 0.3);
~~~

**Đáp án:** `false`. Nếu mục đích là gần bằng theo sai số cho phép, cần quy tắc dung sai; nếu nghiệp vụ yêu cầu thập phân chính xác, cân nhắc `BigDecimal` từ chuỗi.

### Bài E — số nguyên lớn và float

~~~java
int n = 16_777_217;
float f = 16_777_216f;
System.out.println(n == f);
~~~

**Đáp án:** `true` vì `n` bị làm tròn khi chuyển sang float trước khi so.

### Bài F — khác `=` và `==`

~~~java
boolean ok = false;
if (ok = true) {
    System.out.println("entered");
}
~~~

**Đáp án:** in `entered`. Đây là **gán** `true` cho `ok`, không phải kiểm tra giá trị cũ.

### Bài G — reference hay primitive?

~~~java
int a = 5, b = 5;
String x = new String("5");
String y = new String("5");
System.out.println(a == b);
System.out.println(x == y);
System.out.println(x.equals(y));
~~~

**Đáp án:**

~~~text
true
false
true
~~~

`int` được so theo giá trị; `String` cần `equals` để so nội dung.

## 16. Checklist và kết luận

- [ ] Biết `==` so bằng, `!=` so khác và trả kết quả `boolean`.
- [ ] Phân biệt `=` gán và `==` so sánh.
- [ ] Hiểu `boolean` chỉ so được với biểu thức boolean phù hợp.
- [ ] Biết `byte`, `short`, `char` có thể được nâng lên `int` khi so.
- [ ] Biết `int` và `long` có thể so chính xác khi chuyển rộng int → long.
- [ ] Nhận ra numeric promotion sang `float`/`double` có thể mất độ chính xác số nguyên lớn.
- [ ] Không xem `==` trên số thực là so “gần bằng”.
- [ ] Biết `NaN == NaN` là false, `NaN != NaN` là true.
- [ ] Biết `+0.0 == -0.0` là true, dù bit và một số phép tính khác phân biệt.
- [ ] Biết Infinity cùng dấu có thể so bằng; dữ liệu hữu hạn cần kiểm tra riêng.
- [ ] Có thể định nghĩa dung sai dựa trên miền/đơn vị nếu bài toán cần gần bằng.
- [ ] Phân biệt primitive comparison với reference identity; wrapper có thể unbox và gặp null.
- [ ] Biên dịch/chạy `PrimitiveEqualityDemo.java` bằng `javac`/`java`, đối chiếu stdout chính xác.

**Kết luận:** Với primitive Java 17+, `==`/`!=` kiểm tra **giá trị**, không so tham chiếu. Các kiểu số áp dụng numeric promotion trước khi so; riêng floating-point cần đặc biệt chú ý sai số biểu diễn, NaN, zero có dấu và việc chuyển từ số nguyên lớn sang float/double. Hãy dùng toán tử chính xác khi muốn **giá trị đúng bằng nhau**, và định nghĩa quy tắc dung sai khi muốn **gần bằng nhau** theo yêu cầu bài toán.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="comparisons-003">So sánh boolean và giới hạn dùng toán tử quan hệ</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh `boolean` và giới hạn dùng toán tử quan hệ trong Java

> **Java core — language-basics / So sánh**  
> **Phiên bản:** Java 17+; ví dụ độc lập, chỉ cần JDK.  
> **Mục tiêu:** Biết dùng `==`, `!=` để so sánh hai giá trị `boolean`; hiểu vì sao `<`, `<=`, `>`, `>=` không dùng được với `boolean`; phân biệt phép so sánh với phép logic và xử lý đúng `Boolean` nullable.

## 1. `boolean` chỉ có hai giá trị

Kiểu nguyên thủy `boolean` biểu diễn điều kiện logic, với đúng hai giá trị `true` và `false`:

~~~java
boolean isReady = true;
boolean isDone = false;
~~~

`boolean` **không phải kiểu số nguyên** trong Java: không thể ngầm chuyển `true` thành 1, `false` thành 0 hoặc ngược lại. Vì vậy:

~~~java
// KHÔNG BIÊN DỊCH:
// boolean flag = 1;
// int number = true;
// boolean equal = (true == 1);
~~~

Không cần viết `if (isReady == true)` để kiểm tra điều kiện thông thường; `if (isReady)` dễ đọc hơn. Với phủ định, dùng `!isReady`.

## 2. `==` và `!=` hợp lệ với `boolean`

Hai toán tử bằng/khác áp dụng được cho **hai biểu thức kiểu `boolean`**:

~~~java
boolean a = true;
boolean b = false;

boolean same = (a == b);      // false
boolean different = (a != b); // true
boolean sameAsTrue = (a == true); // true, nhưng thường không cần viết dài.
~~~

| Vế trái | Vế phải | `a == b` | `a != b` |
| --- | --- | --- | --- |
| `true` | `true` | `true` | `false` |
| `true` | `false` | `false` | `true` |
| `false` | `true` | `false` | `true` |
| `false` | `false` | `true` | `false` |

**Ý nghĩa:** `==` trả `true` nếu hai giá trị logic bằng nhau; `!=` trả `true` nếu khác nhau. Kết quả phép so sánh cũng có kiểu `boolean`, sử dụng được trong `if`, `while` hoặc trong phép logic khác.

## 3. Vì sao `<`, `<=`, `>`, `>=` không dùng với `boolean`?

Các toán tử **quan hệ** (relational operators) dùng để so sánh thứ tự của **giá trị số** trong Java, không nhận toán hạng `boolean`.

~~~java
// Các dòng sau là ví dụ SAI, cố ý comment để chương trình không lỗi:
// boolean smaller = false < true;
// boolean greater = true > false;
// boolean atLeast = true >= false;
// boolean atMost = false <= true;
~~~

Java không định nghĩa quy tắc kiểu primitive `false < true` hay `true > false`, dù một số ngôn ngữ khác có thể xem false/true như 0/1.

So sánh hợp lệ với các kiểu số và `char`:

~~~java
int minimum = 3, maximum = 7;
boolean less = minimum < maximum; // true
boolean range = minimum >= 0 && maximum <= 10; // true
boolean charOrder = 'A' < 'B'; // true: char tham gia so sánh theo giá trị ký tự.
~~~

| Toán tử | `int`, `long`, `float`, `double`, `char` | `boolean` |
| --- | --- | --- |
| `==`, `!=` | Hợp lệ | Hợp lệ |
| `<`, `<=` | Hợp lệ | **Không hợp lệ** |
| `>`, `>=` | Hợp lệ | **Không hợp lệ** |

Lưu ý: đây là bảng về **kiểu nguyên thủy**. `String` cũng không so sánh thứ tự bằng `<`/`>`; dùng `compareTo` hoặc `Comparator` phù hợp khi muốn sắp xếp chuỗi.

## 4. Phép so sánh khác phép logic

Sau khi có các giá trị `boolean`, ta có thể kết hợp hoặc đảo điều kiện bằng toán tử logic:

| Toán tử | Ý nghĩa với `boolean` | Có bỏ qua vế phải? |
| --- | --- | --- |
| `!a` | NOT: đảo `true`/`false` | Toán tử một ngôi |
| `a && b` | AND có short-circuit | Có, nếu `a` là `false` |
| `a \|\| b` | OR có short-circuit | Có, nếu `a` là `true` |
| `a & b` | AND không short-circuit | Không |
| `a \| b` | OR không short-circuit | Không |
| `a ^ b` | XOR, chỉ đúng khi khác nhau | Không |

~~~java
boolean ready = true;
boolean finished = false;

boolean allowed = ready && !finished; // true
boolean any = ready || finished;      // true
boolean exactlyOne = ready ^ finished; // true
~~~

`^` trên hai giá trị `boolean` có kết quả tương đương `!=` về **giá trị logic**, nhưng không nên thay lẫn tùy tiện vì mục đích diễn đạt có thể khác: `!=` nhấn mạnh **so sánh khác nhau**; `^` nhấn mạnh **XOR**. Với boolean, cả hai vế của `^` được đánh giá.

**Short-circuit rất quan trọng khi có `null` hoặc phép tính có thể lỗi:**

~~~java
String name = null;
boolean hasName = name != null && !name.isBlank(); // false, không NPE.
~~~

Vế phải bị bỏ qua vì điều kiện bên trái `false`. Viết ngược thành `!name.isBlank() && name != null` sẽ lỗi trước khi guard được áp dụng.

## 5. Khi cần thứ tự của hai giá trị logic, hãy chỉ định rõ

Đôi khi nghiệp vụ muốn sắp xếp `false` **trước** `true`, chẳng hạn khi sort một danh sách trạng thái. **Không** viết `false < true`.

Java có phương thức:

~~~java
int order = Boolean.compare(false, true); // -1
int equal = Boolean.compare(true, true);  // 0
int reverse = Boolean.compare(true, false); // 1
~~~

`Boolean.compare(a, b)` trả giá trị âm/0/dương khi a đứng trước/bằng/sau b trong thứ tự **false trước true**. Đây là **thứ tự do API định nghĩa**, không phải việc `<` trở thành toán tử hợp lệ đối với `boolean`.

Nếu nghiệp vụ cần đảo thứ tự, có thể hoán đổi hai tham số hoặc sử dụng `Comparator` theo yêu cầu; đừng mã hóa boolean thành số một cách tùy tiện rồi so sánh khi không cần thiết.

## 6. Phân biệt `boolean` và `Boolean`

`boolean` là kiểu nguyên thủy **không thể là `null`**. `Boolean` là lớp bao tham chiếu; một biến `Boolean` có thể nhận `true`, `false` hoặc `null`:

~~~java
boolean primitive = true;
Boolean boxed = Boolean.TRUE;
Boolean unknown = null;
~~~

Có ba bẫy cần nhớ:

**(1) So sánh hai `Boolean` bằng `==` có thể so sánh tham chiếu.**

Khi cả hai toán hạng đều thuộc kiểu tham chiếu `Boolean`, `==` là phép so sánh **cùng object**; đừng dùng nó để đảm bảo so sánh **giá trị** trong mọi trường hợp. Dùng `equals` hoặc `Objects.equals` khi cần so sánh giá trị, kể cả nullable:

~~~java
import java.util.Objects;

// Trong một phương thức:
Boolean first = Boolean.TRUE;
Boolean second = Boolean.TRUE;
boolean sameValue = Objects.equals(first, second); // true
~~~

Không dựa vào kết quả trùng hợp của `first == second` khi object được cache/intern hoặc tạo từ các nguồn khác nhau.

**(2) So sánh `Boolean` với literal `true` hoặc `false` có thể unbox.**

~~~java
Boolean unknown = null;
// boolean result = (unknown == true); // Nếu chạy: NullPointerException.
~~~

Trong biểu thức `unknown == true`, Java phải unbox `unknown` thành `boolean`. Nếu nó là `null`, sẽ ném `NullPointerException`.

**(3) Kiểm tra nullable an toàn:**

~~~java
Boolean unknown = null;
boolean enabled = Boolean.TRUE.equals(unknown); // false, không NPE.
boolean known = unknown != null;                 // false.
~~~

`Boolean.TRUE.equals(unknown)` trả true **chỉ khi** giá trị là `Boolean.TRUE`; cả `false` và `null` đều cho false. Nếu ứng dụng cần phân biệt ba trạng thái *true / false / unknown*, hãy kiểm tra null riêng thay vì đánh đồng unknown với false.

## 7. Ví dụ Java 17+ chạy độc lập

Tạo file **BooleanComparisonDemo.java**:

~~~java
import java.util.Objects;

public final class BooleanComparisonDemo {
    private static boolean logged(
            StringBuilder order, String name, boolean value) {
        order.append(name);
        return value;
    }

    public static void main(String[] args) {
        boolean ready = true;
        boolean done = false;

        System.out.println("primitiveEqual=" + (ready == done));
        System.out.println("primitiveNotEqual=" + (ready != done));
        System.out.println("negation=" + !done);
        System.out.println("logicalAnd=" + (ready && done));
        System.out.println("logicalOr=" + (ready || done));
        System.out.println("logicalXor=" + (ready ^ done));

        int low = 3;
        int high = 7;
        System.out.println("numericLess=" + (low < high));
        System.out.println("charLess=" + ('A' < 'B'));

        Boolean boxedTrue = Boolean.TRUE;
        Boolean boxedNull = null;
        System.out.println("boxedValue="
                + Boolean.TRUE.equals(boxedTrue));
        System.out.println("nullSafe="
                + Boolean.TRUE.equals(boxedNull));
        System.out.println("bothNull="
                + Objects.equals(boxedNull, null));

        boolean guarded = boxedNull != null && boxedNull;
        System.out.println("nullGuard=" + guarded);

        try {
            boolean unboxed = boxedNull == true;
            System.out.println("unboxed=" + unboxed);
        } catch (NullPointerException ex) {
            System.out.println("unboxNull=NullPointerException");
        }

        System.out.println("booleanOrdering="
                + Boolean.compare(false, true));

        String a = new String("Java");
        String b = new String("Java");
        System.out.println("stringValue=" + a.equals(b));
        System.out.println("stringReference=" + (a == b));

        StringBuilder order = new StringBuilder();
        boolean shortAnd = logged(order, "F", false)
                && logged(order, "X", true);
        boolean eagerAnd = logged(order, "A", false)
                & logged(order, "B", true);
        System.out.println("shortAnd=" + shortAnd);
        System.out.println("eagerAnd=" + eagerAnd);
        System.out.println("evaluationOrder=" + order);
    }
}
~~~

Biên dịch và chạy trong thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 BooleanComparisonDemo.java
java BooleanComparisonDemo
~~~

**Expected stdout chính xác:**

~~~text
primitiveEqual=false
primitiveNotEqual=true
negation=true
logicalAnd=false
logicalOr=true
logicalXor=true
numericLess=true
charLess=true
boxedValue=true
nullSafe=false
bothNull=true
nullGuard=false
unboxNull=NullPointerException
booleanOrdering=-1
stringValue=true
stringReference=false
shortAnd=false
eagerAnd=false
evaluationOrder=FAB
~~~

Giải thích dòng cuối: F cho false nên `&&` bỏ qua X; toán tử boolean `&` vẫn đánh giá cả A và B, do đó dấu vết là `FAB`. Dòng `unboxNull` thể hiện ngoại lệ của so sánh giữa `Boolean` nullable với primitive `true`, không phải giới hạn của `==` trên hai `boolean` nguyên thủy.

## 8. Bài tập tự kiểm tra

**Bài 1.** Cho `boolean a = true, b = false;`. Tính `a == b`, `a != b`, `!a`, `a && b`, `a || b` và `a ^ b`.

**Bài 2.** Giải thích tại sao `true < false`, `false >= true` và `true == 1` không biên dịch trong Java. Có được dùng `'A' < 'B'` không? Vì sao?

**Bài 3.** Khi cần sắp xếp trạng thái false trước true, dùng API nào thay vì `a < b`? Cho ví dụ.

**Bài 4.** Cho `Boolean flag = null;`. Điều gì xảy ra với `flag == true`? So sánh kết quả với `Boolean.TRUE.equals(flag)`.

**Bài 5.** Giải thích sự khác nhau giữa `boolean` và `Boolean`, giữa `Objects.equals(a, b)` và `a == b` khi a, b là các tham chiếu `Boolean`.

### Ghi nhớ

**Hai giá trị `boolean` so sánh được bằng `==` và `!=`, nhưng không dùng được `<`, `<=`, `>`, `>=`.** Boolean không phải số nguyên. Dùng `!`, `&&`, `||` và các phép logic khi làm việc với điều kiện; dùng `Boolean.compare` nếu thật sự cần thứ tự false/true. Với `Boolean` dạng tham chiếu, cẩn thận phép so sánh object và `null` khi unboxing.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="comparisons-004">Phân biệt so sánh giá trị với so sánh tham chiếu</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt so sánh giá trị với so sánh tham chiếu trong Java

> **Java core — language-basics / So sánh**  
> **Phiên bản:** Java 17+; một chương trình độc lập, không dùng thư viện ngoài.  
> **Mục tiêu:** Hiểu `==` so sánh **giá trị của kiểu nguyên thủy** nhưng so sánh **danh tính đối tượng** khi hai toán hạng là tham chiếu; chọn `equals()`, `Objects.equals()` hoặc API so sánh mảng phù hợp để kiểm tra nội dung.

## 1. Câu hỏi khác nhau: bằng giá trị hay cùng đối tượng?

Trong Java, có hai kiểu câu hỏi:

- **Bằng giá trị:** Hai số có bằng nhau không? Hai chuỗi có cùng ký tự không? Hai đối tượng có được coi là tương đương theo quy tắc của lớp không?
- **Cùng tham chiếu:** Hai biến tham chiếu có cùng trỏ tới **chính một đối tượng** trong bộ nhớ không?

~~~java
int x = 42;
int y = 42;
System.out.println(x == y); // true: so sánh giá trị int.

String a = new String("Java");
String b = new String("Java");
System.out.println(a == b);      // false: hai đối tượng khác nhau.
System.out.println(a.equals(b)); // true: cùng nội dung.
~~~

`a == b` không tự động so từng ký tự của chuỗi. Với cả hai toán hạng thuộc **kiểu tham chiếu**, `==` trả `true` khi cùng tham chiếu (hoặc cả hai `null`), chứ không phải khi nội dung giống nhau.

## 2. `==` và `!=` với kiểu nguyên thủy

Với các toán hạng nguyên thủy thích hợp, `==` và `!=` xét **giá trị**, có áp dụng quy tắc chuyển kiểu số khi so sánh:

~~~java
int a = 10;
long b = 10L;
boolean equal = (a == b);  // true: so sánh số.
boolean unequal = (a != b); // false.

boolean ready = true;
boolean enabled = true;
boolean sameFlag = ready == enabled; // true.
~~~

| Kiểu toán hạng | `==` có ý nghĩa gì? | Ví dụ |
| --- | --- | --- |
| `int`, `long`, `char`, ... | So sánh giá trị số sau chuyển kiểu phù hợp | `10 == 10L` → `true` |
| `boolean` | So sánh giá trị logic | `true == false` → `false` |
| `float`, `double` | So sánh giá trị số thực theo quy tắc dấu phẩy động | `0.0 == -0.0` → `true` |
| Hai tham chiếu có thể so sánh | So sánh có phải cùng đối tượng hoặc cùng `null` | `new Object() == new Object()` → `false` |

**Ngoại lệ cần biết với số thực:** `Double.NaN == Double.NaN` là `false`, dù cả hai biểu thức đều cho `NaN`. Dùng `Double.isNaN(...)` để kiểm tra NaN. So sánh trực tiếp hai số thực có kết quả tính toán thường cần cân nhắc sai số làm tròn.

`==` không hợp lệ để so sánh hai kiểu không tương thích, ví dụ không thể viết `"1" == 1`.

## 3. `==` trên tham chiếu: cùng object, không phải cùng nội dung

~~~java
String first = new String("hello");
String alias = first;
String second = new String("hello");

System.out.println(first == alias);  // true: alias cùng object.
System.out.println(first == second); // false: hai object riêng.
System.out.println(first.equals(second)); // true: cùng nội dung.
~~~

Một phép gán tham chiếu đơn thuần không tạo đối tượng mới: `alias = first` sao chép **giá trị tham chiếu**. Thay đổi trạng thái của một object có thể nhìn thấy qua các alias; còn gán lại biến tham chiếu không thay đổi biến kia.

Điều này đặc biệt quan trọng với các object có trạng thái thay đổi được, như mảng hoặc một lớp tự viết. Đừng dùng `==` để quyết định hai đối tượng “có cùng dữ liệu” trừ khi **danh tính đối tượng** chính là yêu cầu.

## 4. `equals()` và `Objects.equals()`: so sánh theo ngữ nghĩa của lớp

`equals(Object)` là một phương thức của `Object`. Nhưng kết quả phụ thuộc vào cách lớp triển khai:

- `Object.equals()` mặc định về cơ bản kiểm tra **cùng tham chiếu**.
- `String.equals()` so sánh **chuỗi ký tự**.
- Các lớp giá trị hoặc `record` có thể định nghĩa so sánh theo **thành phần dữ liệu**.
- Một lớp tự tạo **không override `equals()`** thì hai instance khác nhau vẫn có thể `equals() == false` dù field giống nhau.

~~~java
record Point(int x, int y) {}

Point p = new Point(2, 3);
Point q = new Point(2, 3);

System.out.println(p == q);      // false: hai đối tượng riêng.
System.out.println(p.equals(q)); // true: record so sánh thành phần.
~~~

Khi cần so sánh có thể gặp `null`, dùng `java.util.Objects.equals(a, b)`:

~~~java
import java.util.Objects;

// Trong một phương thức:
String a = null;
String b = null;

boolean bothMissing = Objects.equals(a, b); // true.
boolean missingVsText = Objects.equals(a, "Java"); // false.
~~~

`Objects.equals(a, b)` trả `true` nếu cả hai null, `false` nếu chỉ một null, và nếu cả hai không null thì dùng `a.equals(b)`. Điều này **không tự biến `equals` của lớp thành phép so sánh nội dung**; nó chỉ bảo vệ khỏi gọi method trên tham chiếu null.

## 5. Bẫy `String` literal và string pool

Java lưu các string literal và một số chuỗi hằng ở string pool. Bởi vậy đôi khi `==` trên chuỗi lại ra `true`, dễ khiến người học tưởng rằng `==` là phép so sánh nội dung:

~~~java
String a = "Java";
String b = "Ja" + "va"; // Biểu thức hằng: có thể gộp thành cùng literal.
String c = new String("Java");

System.out.println(a == b); // true: cùng tham chiếu tới chuỗi intern.
System.out.println(a == c); // false: new tạo String riêng.
System.out.println(a.equals(c)); // true: cùng ký tự.
~~~

**Không dựa vào string pool để kiểm tra dữ liệu người dùng.** Chuỗi đọc từ file, tham số, API hoặc được tạo trong runtime không bảo đảm dùng cùng tham chiếu chỉ vì có nội dung giống nhau.

Quy tắc tốt: muốn biết hai chuỗi có cùng văn bản, dùng `a.equals(b)` khi `a` chắc chắn không null, `"Java".equals(a)` khi so với hằng, hoặc `Objects.equals(a, b)` khi cả hai có thể null.

## 6. Lớp bao (`Integer`, `Boolean`...) và autoboxing

Lớp bao chứa giá trị nguyên thủy nhưng bản thân biến `Integer` là **tham chiếu**.

~~~java
Integer a = Integer.valueOf(1000);
Integer b = Integer.valueOf(1000);

System.out.println(a.equals(b)); // true: hai giá trị int bằng nhau.
~~~

**Đừng dựa vào `a == b` để so sánh giá trị hai wrapper.** Các giá trị nhỏ có thể dùng cache, nhưng danh tính instance phụ thuộc cách tạo/cache; kết quả `==` dễ khác nhau trong các tình huống khác nhau.

Khi một bên là primitive và một bên là wrapper tương ứng, Java có thể **unbox** wrapper để so sánh giá trị:

~~~java
Integer wrapped = Integer.valueOf(1000);
int primitive = 1000;
System.out.println(wrapped == primitive); // true: unbox rồi so sánh số.
~~~

Nhưng unboxing tham chiếu `null` gây `NullPointerException`:

~~~java
Integer missing = null;
// boolean equal = (missing == 0); // NPE khi chạy.
boolean safe = Objects.equals(missing, Integer.valueOf(0)); // false.
~~~

Phân biệt **hai wrapper** (thường so sánh tham chiếu qua `==`) với **wrapper so primitive** (có thể unbox rồi so sánh giá trị). Đây là lỗi rất dễ xuất hiện khi đổi kiểu tham số hoặc dùng generics.

## 7. Mảng: `==`, `equals()` và `Arrays.equals()`

Mảng là object. Hai mảng khác nhau có cùng phần tử vẫn không phải cùng object:

~~~java
int[] first = {1, 2, 3};
int[] second = {1, 2, 3};

System.out.println(first == second);              // false.
System.out.println(first.equals(second));         // false.
System.out.println(java.util.Arrays.equals(first, second)); // true.
~~~

Mảng **không override** `Object.equals()` để so sánh phần tử. Sử dụng `Arrays.equals` cho mảng một chiều; với mảng lồng nhau, `Arrays.deepEquals` có thể so sánh nội dung mảng con:

~~~java
int[][] a = {{1, 2}};
int[][] b = {{1, 2}};
System.out.println(java.util.Arrays.equals(a, b)); // false: inner arrays riêng.
System.out.println(java.util.Arrays.deepEquals(a, b)); // true.
~~~

Bảng chọn nhanh:

| Dữ liệu cần so sánh | Nên dùng | Tránh hiểu nhầm |
| --- | --- | --- |
| Hai `int`/`boolean` | `==` hoặc `!=` | Đây là so sánh giá trị primitive |
| Hai tham chiếu, cần kiểm tra cùng object | `==` | Không đại diện cho cùng nội dung |
| Hai `String`, có thể không null | `equals()` | `==` dễ bị ảnh hưởng bởi intern/string pool |
| Hai object nullable | `Objects.equals(a, b)` | Vẫn phụ thuộc `equals` của lớp |
| Hai mảng primitive một chiều | `Arrays.equals(a, b)` | Mảng kế thừa `Object.equals` |
| Hai mảng đa chiều | `Arrays.deepEquals(a, b)` | `Arrays.equals` chỉ so phần tử cấp trực tiếp |
| Hai object của lớp tự định nghĩa | `equals()` được override đúng hợp đồng | Mặc định `Object.equals()` là identity |

Nếu tự override `equals()`, hãy override cả `hashCode()` theo cùng logic để giữ hợp đồng trong `HashSet`/`HashMap`. `record` là lựa chọn hữu ích cho dữ liệu bất biến đơn giản; riêng thành phần kiểu mảng trong record không tự động được deep compare.

## 8. Chương trình Java 17+ runnable

Tạo file **ValueVsReferenceDemo.java**:

~~~java
import java.util.Arrays;
import java.util.Objects;

public final class ValueVsReferenceDemo {
    record Point(int x, int y) {}

    static final class Box {
        final int value;
        Box(int value) { this.value = value; }
    }

    public static void main(String[] args) {
        int x = 42;
        int y = 42;
        System.out.println("primitiveEqual=" + (x == y));

        String literal = "Java";
        String constant = "Ja" + "va";
        String created = new String("Java");
        String alias = created;
        System.out.println("literalSame=" + (literal == constant));
        System.out.println("literalVsCreated=" + (literal == created));
        System.out.println("createdAlias=" + (created == alias));
        System.out.println("stringValue=" + literal.equals(created));

        String missing = null;
        System.out.println("bothNull=" + Objects.equals(missing, null));
        System.out.println("nullVsText=" + Objects.equals(missing, literal));
        System.out.println("literalEqualsNull=" + "Java".equals(missing));

        Integer boxed = Integer.valueOf(1000);
        Integer anotherBoxed = Integer.valueOf(1000);
        int primitive = 1000;
        System.out.println("wrapperValue=" + boxed.equals(anotherBoxed));
        System.out.println("wrapperVsPrimitive=" + (boxed == primitive));
        Integer absent = null;
        try {
            System.out.println("unsafe=" + (absent == 0));
        } catch (NullPointerException ex) {
            System.out.println("wrapperNull=NullPointerException");
        }

        int[] a = {1, 2, 3};
        int[] b = {1, 2, 3};
        System.out.println("arrayIdentity=" + (a == b));
        System.out.println("arrayDefaultEquals=" + a.equals(b));
        System.out.println("arrayContent=" + Arrays.equals(a, b));

        int[][] grid1 = {{1, 2}};
        int[][] grid2 = {{1, 2}};
        System.out.println("nestedShallow=" + Arrays.equals(grid1, grid2));
        System.out.println("nestedDeep=" + Arrays.deepEquals(grid1, grid2));

        Box first = new Box(7);
        Box second = new Box(7);
        System.out.println("plainObjectEquals=" + first.equals(second));

        Point p = new Point(2, 3);
        Point q = new Point(2, 3);
        System.out.println("recordIdentity=" + (p == q));
        System.out.println("recordValue=" + p.equals(q));
        System.out.println("recordHashEqual=" + (p.hashCode() == q.hashCode()));

        double nan = Double.NaN;
        System.out.println("nanPrimitiveEqual=" + (nan == nan));
        System.out.println("nanDetected=" + Double.isNaN(nan));
    }
}
~~~

Biên dịch và chạy tại thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 ValueVsReferenceDemo.java
java ValueVsReferenceDemo
~~~

**Expected stdout chính xác:**

~~~text
primitiveEqual=true
literalSame=true
literalVsCreated=false
createdAlias=true
stringValue=true
bothNull=true
nullVsText=false
literalEqualsNull=false
wrapperValue=true
wrapperVsPrimitive=true
wrapperNull=NullPointerException
arrayIdentity=false
arrayDefaultEquals=false
arrayContent=true
nestedShallow=false
nestedDeep=true
plainObjectEquals=false
recordIdentity=false
recordValue=true
recordHashEqual=true
nanPrimitiveEqual=false
nanDetected=true
~~~

Output cố ý không in `boxed == anotherBoxed` vì kết quả identity của hai wrapper tạo qua cache không nên dùng để kiểm tra giá trị. Ngược lại, chương trình kiểm tra rõ `boxed.equals(anotherBoxed)` và `boxed == primitive`; phép sau unbox và so sánh số.

## 9. Bài tập và nguyên tắc nhớ

**Bài 1.** Cho hai biến `int` có giá trị 5 và hai biến `String` cùng văn bản "5" nhưng được tạo bởi hai lệnh `new String("5")`. `==` đang hỏi điều gì trong từng trường hợp?

**Bài 2.** Viết đoạn so sánh chuỗi nullable bằng `Objects.equals`. Vì sao gọi `a.equals(b)` khi `a == null` không an toàn?

**Bài 3.** Tạo hai mảng `int[]` cùng phần tử. So sánh kết quả `a == b`, `a.equals(b)` và `Arrays.equals(a, b)`.

**Bài 4.** Khi `Integer wrapped = null;`, điều gì xảy ra với `wrapped == 0`? Phân biệt với `Objects.equals(wrapped, Integer.valueOf(0))`.

**Bài 5.** Tạo hai `record Point(int x, int y)` cùng tọa độ. So sánh `==` và `equals()`. Nếu viết class thông thường mà không override `equals()` thì sao?

### Ghi nhớ

**`==` so sánh giá trị với kiểu nguyên thủy, nhưng so sánh danh tính với hai tham chiếu.** `equals()` kiểm tra bằng nhau theo cách lớp định nghĩa; `Objects.equals()` hỗ trợ `null` an toàn. Với `String` dùng so sánh nội dung, với mảng dùng `Arrays.equals`/`Arrays.deepEquals`, và với lớp bao hãy nhớ bẫy cache/autoboxing. Chỉ dùng `==` trên tham chiếu khi thực sự muốn hỏi “có cùng object không?”.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="comparisons-005">NaN và những kết quả so sánh khác trực giác</a>

<details>
<summary>Xem chi tiết</summary>

# NaN và những kết quả so sánh khác trực giác — Java 17+

`NaN` (*Not a Number*) là một giá trị đặc biệt thuộc các kiểu số thực `float` và `double` của Java, không phải `null` hoặc ngoại lệ. Các phép tính không xác định có thể trả về NaN:

~~~java
double nan = 0.0 / 0.0;
double alsoNan = Math.sqrt(-1.0);
~~~

**Điểm khác trực giác:** với các phép so sánh số thực primitive, NaN **không bằng chính nó**. Nếu ít nhất một toán hạng là NaN thì các phép `==`, `<`, `<=`, `>` và `>=` đều **false**, trong khi `!=` là **true**.

~~~java
double x = Double.NaN;
System.out.println(x == x);    // false.
System.out.println(x != x);    // true.
System.out.println(x < 10.0);  // false.
System.out.println(x >= 10.0); // false.
~~~

Vì vậy **`!(x > 10)` không luôn tương đương `x <= 10`**. Khi `x` là NaN, biểu thức đầu là true còn biểu thức sau false.

## 1. Bảng quy tắc so sánh NaN

Giả sử `double x = Double.NaN`:

| Biểu thức | Kết quả | Giải thích |
| --- | --- | --- |
| `x == x` | false | NaN không bằng chính nó theo `==` |
| `x != x` | true | Quy tắc so sánh khác với NaN |
| `x < 5.0` | false | Không xác định quan hệ thứ tự |
| `x <= 5.0` | false | Không thể suy từ “không lớn hơn” |
| `x > 5.0` | false | Không xác định quan hệ thứ tự |
| `x >= 5.0` | false | Không xác định quan hệ thứ tự |
| `5.0 < x` | false | NaN ở toán hạng phải cũng vậy |
| `!(x > 5.0)` | true | Phủ định một phép so sánh trả false |
| `Double.isNaN(x)` | true | Kiểm tra NaN đúng mục đích |

Các quy tắc tương ứng cũng áp dụng cho `float`. **Không dùng `x == Double.NaN` để kiểm tra NaN**; dùng `Double.isNaN(x)` hoặc `Float.isNaN(x)`.

## 2. Demo Java 17+ tự chứa, chạy bằng CLI

Tạo tệp UTF-8 có tên **`NaNComparisonDemo.java`**:

~~~java
import java.util.TreeSet;

public class NaNComparisonDemo {
    public static void main(String[] args) {
        double nan = 0.0 / 0.0;

        System.out.println("== primitive ==");
        System.out.println("nanIsNaN=" + Double.isNaN(nan));
        System.out.println("nanEqSelf=" + (nan == nan));
        System.out.println("nanNeSelf=" + (nan != nan));
        System.out.println("nanLess=" + (nan < 5.0));
        System.out.println("nanLessEqual=" + (nan <= 5.0));
        System.out.println("nanGreater=" + (nan > 5.0));
        System.out.println("nanGreaterEqual=" + (nan >= 5.0));
        System.out.println("reverseLess=" + (5.0 < nan));
        System.out.println("notGreater=" + (!(nan > 5.0)));
        System.out.println("lessOrEqual=" + (nan <= 5.0));

        System.out.println("== detecting ==");
        System.out.println("sqrtNaN="
                + Double.isNaN(Math.sqrt(-1.0)));
        System.out.println("infinityMinusInfinityNaN="
                + Double.isNaN(Double.POSITIVE_INFINITY
                        - Double.POSITIVE_INFINITY));
        System.out.println("nanPlusOne="
                + Double.isNaN(nan + 1.0));
        System.out.println("parsedNaN="
                + Double.isNaN(Double.parseDouble("NaN")));
        System.out.println("nanFinite=" + Double.isFinite(nan));
        System.out.println("fiveFinite=" + Double.isFinite(5.0));
        System.out.println("infinityFinite="
                + Double.isFinite(Double.POSITIVE_INFINITY));
        System.out.println("mathMinNaN="
                + Double.isNaN(Math.min(nan, 3.0)));

        System.out.println("== wrapper and ordering ==");
        Double first = Double.valueOf(Double.NaN);
        Double second = Double.valueOf(Double.NaN);
        System.out.println("wrapperEquals=" + first.equals(second));
        System.out.println("compareNaNs=" + Double.compare(nan, nan));
        System.out.println("nanAfterInfinity="
                + (Double.compare(nan, Double.POSITIVE_INFINITY) > 0));

        double positiveZero = 0.0;
        double negativeZero = -0.0;
        System.out.println("zerosPrimitiveEqual="
                + (positiveZero == negativeZero));
        System.out.println("zerosWrapperEqual="
                + Double.valueOf(positiveZero)
                        .equals(Double.valueOf(negativeZero)));
        System.out.println("compareZeros="
                + Double.compare(positiveZero, negativeZero));
        System.out.println("reciprocalNegZero="
                + (1.0 / negativeZero));

        TreeSet<Double> sorted = new TreeSet<>();
        sorted.add(Double.NaN);
        sorted.add(Double.NaN);
        sorted.add(Double.POSITIVE_INFINITY);
        sorted.add(Double.NEGATIVE_INFINITY);
        sorted.add(positiveZero);
        sorted.add(negativeZero);
        System.out.println("sorted=" + sorted);
        System.out.println("sortedSize=" + sorted.size());

        System.out.println("== float and precision ==");
        float fn = Float.NaN;
        System.out.println("floatEqualsSelf=" + (fn == fn));
        System.out.println("floatIsNaN=" + Float.isNaN(fn));

        double sum = 0.1 + 0.2;
        System.out.println("decimalSum=" + sum);
        System.out.println("decimalExactlyEqual=" + (sum == 0.3));

        boolean validScore = Double.isFinite(nan)
                && nan >= 0.0 && nan <= 100.0;
        System.out.println("nanValidScore=" + validScore);
    }
}
~~~

Yêu cầu **JDK 17 trở lên**. Mở PowerShell (Windows) hoặc Bash (Linux/macOS) tại thư mục chứa file:

~~~bash
java -version
javac -version
javac -encoding UTF-8 NaNComparisonDemo.java
java NaNComparisonDemo
~~~

**Expected stdout chính xác** của lệnh `java NaNComparisonDemo` (không tính đầu ra phiên bản JDK):

~~~text
== primitive ==
nanIsNaN=true
nanEqSelf=false
nanNeSelf=true
nanLess=false
nanLessEqual=false
nanGreater=false
nanGreaterEqual=false
reverseLess=false
notGreater=true
lessOrEqual=false
== detecting ==
sqrtNaN=true
infinityMinusInfinityNaN=true
nanPlusOne=true
parsedNaN=true
nanFinite=false
fiveFinite=true
infinityFinite=false
mathMinNaN=true
== wrapper and ordering ==
wrapperEquals=true
compareNaNs=0
nanAfterInfinity=true
zerosPrimitiveEqual=true
zerosWrapperEqual=false
compareZeros=1
reciprocalNegZero=-Infinity
sorted=[-Infinity, -0.0, 0.0, Infinity, NaN]
sortedSize=5
== float and precision ==
floatEqualsSelf=false
floatIsNaN=true
decimalSum=0.30000000000000004
decimalExactlyEqual=false
nanValidScore=false
~~~

## 3. Phủ định một quan hệ không tự tạo ra quan hệ đối nghịch

Với hai số hữu hạn thông thường, lập trình viên có thể xem `!(a > b)` tương đương `a <= b`. Nhưng với NaN:

~~~java
double a = Double.NaN;
double b = 5.0;
boolean negated = !(a > b); // true.
boolean opposite = a <= b; // false.
~~~

Bảng sau cho thấy những biến đổi **không được dùng mù quáng** khi có NaN:

| Biểu thức phủ định | Biểu thức thay thế tưởng tương đương | Kết quả khi `a` là NaN |
| --- | --- | --- |
| `!(a > b)` | `a <= b` | true so với false |
| `!(a >= b)` | `a < b` | true so với false |
| `!(a < b)` | `a >= b` | true so với false |
| `!(a <= b)` | `a > b` | true so với false |
| `!(a == b)` | `a != b` | Cả hai true |

Chỉ hàng cuối luôn giữ quan hệ phủ định trực tiếp giữa `==` và `!=`. Những cặp toán tử thứ tự không còn là các phép phủ định đối nhau khi có NaN.

Đặc biệt, điều kiện `!(score > 100)` sẽ nhận cả giá trị NaN, nên **không dùng nó một mình để xác thực điểm**. Nếu chỉ chấp nhận số hữu hạn trong đoạn [0, 100]:

~~~java
static boolean validScore(double score) {
    return Double.isFinite(score)
            && score >= 0.0
            && score <= 100.0;
}
~~~

`Double.isFinite` từ chối NaN và cả ±Infinity. Toán tử `&&` ngắn mạch nên nếu phần kiểm tra hữu hạn false thì các điều kiện sau không cần đánh giá.

## 4. Primitive `==`, wrapper `equals` và `Double.compare` khác nhau

~~~java
double nan = Double.NaN;

System.out.println(nan == nan); // false.
System.out.println(Double.valueOf(nan)
        .equals(Double.valueOf(nan))); // true.
System.out.println(Double.compare(nan, nan)); // 0.
~~~

| Cơ chế | Hai NaN | `+0.0` và `-0.0` |
| --- | --- | --- |
| Primitive `double ==` | Không bằng | Bằng |
| `Double.equals` | Bằng | Không bằng |
| `Double.compare` | Trả 0 | Phân biệt, `compare(+0.0, -0.0) > 0` |

`Double.compare(a, b)` xác lập thứ tự tổng phù hợp để sắp xếp: NaN **đứng sau mọi giá trị `double` khác**, kể cả `Infinity`; hai NaN được xem là tương đương theo thứ tự đó. Đây là quy tắc **phương thức so sánh**, không thay đổi hành vi của toán tử quan hệ primitive:

~~~java
Double.compare(Double.NaN, Double.POSITIVE_INFINITY) > 0 // true.
Double.NaN > Double.POSITIVE_INFINITY // false.
~~~

Lưu ý: `==` giữa **hai đối tượng `Double`** là phép so sánh tham chiếu. Để so giá trị wrapper, dùng `equals` hoặc `compare` đúng hợp đồng; muốn dùng toán tử primitive thì phải unbox và kiểm tra null khi cần.

## 5. Vì sao `TreeSet<Double>` sắp xếp NaN ở cuối?

`TreeSet<Double>` sử dụng thứ tự tự nhiên của `Double.compareTo`. Ví dụ:

~~~java
TreeSet<Double> set = new TreeSet<>();
set.add(Double.NaN);
set.add(-0.0);
set.add(+0.0);
set.add(Double.POSITIVE_INFINITY);
set.add(Double.NEGATIVE_INFINITY);
~~~

Thứ tự phần tử là:

~~~text
[-Infinity, -0.0, 0.0, Infinity, NaN]
~~~

- `-Infinity` đứng trước các số hữu hạn.
- `-0.0` và `+0.0` là **hai phần tử khác nhau** theo comparator.
- `Infinity` đứng trước NaN.
- Thêm NaN lần nữa không tạo phần tử NaN thứ hai vì `Double.compare` coi các NaN tương đương.

Kết quả không mâu thuẫn với `NaN == NaN` false: container dùng **comparator**, còn biểu thức dùng phép toán trên giá trị primitive.

## 6. Hai số không có dấu `+0.0` và `-0.0`

~~~java
double plus = 0.0;
double minus = -0.0;

System.out.println(plus == minus);       // true.
System.out.println(Double.compare(plus, minus)); // 1.
System.out.println(1.0 / minus);         // -Infinity.
~~~

Primitive `==` coi hai giá trị zero bằng nhau, nhưng dấu vẫn được giữ trong biểu diễn số thực và các phép tính như nghịch đảo. `Double.equals` và `Double.compare` phân biệt chúng.

Vì vậy, cũng đừng dùng các phép `<`, `>` primitive để suy luận về bit dấu của zero: `-0.0 < +0.0` là false, còn `-0.0 <= +0.0` là true.

## 7. NaN khác vô cực và sai số nhị phân

| Hiện tượng | Ví dụ | Cách kiểm tra hoặc xử lý |
| --- | --- | --- |
| NaN | `0.0 / 0.0`, `Math.sqrt(-1.0)` | `Double.isNaN` |
| ±Infinity | `1.0 / 0.0`, `-1.0 / 0.0` | `Double.isInfinite` |
| Số hữu hạn | `5.0`, `-0.0` | `Double.isFinite` |
| Sai số làm tròn | `0.1 + 0.2` | Phân tích độ chính xác của `double` |

~~~java
double sum = 0.1 + 0.2;
System.out.println(sum);         // 0.30000000000000004.
System.out.println(sum == 0.3);  // false.
~~~

`sum` là số **hữu hạn**, không phải NaN. Sai khác là do biểu diễn số thực **nhị phân**, không do quy tắc so sánh NaN. Nếu yêu cầu thập phân chính xác như tiền tệ, cân nhắc `BigDecimal` tạo từ chuỗi và quy tắc scale/làm tròn; với đại lượng đo lường, cần quy tắc sai số thích hợp theo miền giá trị.

## 8. Các hiểu nhầm thường gặp

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| “`x == Double.NaN` phát hiện NaN.” | Luôn false khi so primitive; dùng `Double.isNaN(x)` |
| “Nếu không nhỏ hơn thì chắc lớn hơn hoặc bằng.” | Sai với NaN |
| “`NaN != NaN` phải false vì cùng tên.” | Primitive `!=` trả true |
| “`Double.compare(NaN, NaN)` trả khác 0.” | Trả 0 |
| “`Double.equals` luôn giống `double ==`.” | Khác nhau với NaN và ±0.0 |
| “`0.0` và `-0.0` giống nhau trong mọi API.” | Comparator, wrapper và phép nghịch đảo phân biệt |
| “`Double.parseDouble("NaN")` ném lỗi.” | Có thể parse thành NaN |
| “`Double.isFinite` xác nhận số thập phân chính xác.” | Chỉ xác nhận không phải NaN/Infinity |

## 9. Bài tập và đáp án

1. `Double.NaN == Double.NaN` và `Double.NaN != Double.NaN` là gì?  
   *Đáp án:* `false` và `true`.
2. Với `double x = Double.NaN`, bốn quan hệ `x < 1`, `x <= 1`, `x > 1`, `x >= 1`?  
   *Đáp án:* Tất cả false.
3. `!(x > 1)` và `x <= 1` ra sao khi x là NaN?  
   *Đáp án:* true và false.
4. `Double.valueOf(Double.NaN).equals(Double.valueOf(Double.NaN))`?  
   *Đáp án:* true.
5. `Double.compare(Double.NaN, Double.POSITIVE_INFINITY) > 0`?  
   *Đáp án:* true, theo thứ tự `Double.compare`.
6. `0.0 == -0.0` và wrapper `equals` hai zero?  
   *Đáp án:* true và false.
7. `Double.isNaN(0.1 + 0.2)` có true không?  
   *Đáp án:* false; kết quả xấp xỉ hữu hạn `0.30000000000000004`.
8. Cách kiểm tra `score` thuộc [0,100] và từ chối cả NaN/Infinity?  
   *Đáp án:* `Double.isFinite(score) && score >= 0 && score <= 100`.

## Checklist ghi nhớ

- [ ] NaN thuộc `float`/`double`, không phải null hay exception.
- [ ] Với NaN, `==`/`<`/`<=`/`>`/`>=` trả false và `!=` trả true.
- [ ] Dùng `Double.isNaN` hoặc `Float.isNaN`, không so với hằng NaN.
- [ ] `!(a > b)` không tương đương `a <= b` khi có NaN.
- [ ] Phân biệt primitive `==`, wrapper `Double.equals` và `Double.compare`.
- [ ] `Double.compare` xếp NaN sau Infinity, phân biệt signed zero.
- [ ] Dùng `Double.isFinite` nếu chỉ chấp nhận giá trị hữu hạn.
- [ ] Không nhầm sai số nhị phân hữu hạn với NaN.

**Kết luận:** NaN khiến nhiều kết quả so sánh số thực Java **khác trực giác**. Muốn viết điều kiện đúng, phải hiểu **quy tắc phép toán primitive**, phân biệt nó với **wrapper/comparator**, rồi kiểm tra rõ các giá trị đặc biệt mà nghiệp vụ cho phép.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="comparisons-006">So sánh số thực bằng sai số phù hợp yêu cầu</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh số thực bằng sai số phù hợp yêu cầu — Java 17+

## 1. Mục tiêu

Khi làm việc với `float` và `double`, kết quả phép tính có thể khác giá trị toán học mong đợi một lượng nhỏ do **biểu diễn nhị phân hữu hạn và làm tròn**. Vì thế, việc kiểm tra hai kết quả tính toán “đủ gần nhau” phải sử dụng **sai số chấp nhận được theo yêu cầu thực tế**, không phải một hằng `epsilon` tùy ý.

~~~java
double sum = 0.1 + 0.2;
System.out.println(sum);        // 0.30000000000000004
System.out.println(sum == 0.3); // false
~~~

Mục tiêu của bài:

- Phân biệt **so bằng chính xác** với **so gần bằng**.
- Biết chọn **sai số tuyệt đối**, **sai số tương đối** hoặc cách kết hợp.
- Hiểu cách xử lý số gần 0, số rất lớn, `NaN`, `Infinity` và `-0.0`.
- Biết khi nào **không nên dùng tolerance** mà phải chuyển sang số nguyên hoặc `BigDecimal`.
- Viết và kiểm thử được hàm so sánh gần bằng trong Java 17+.

**Nguyên tắc cốt lõi:** “Gần” chỉ có ý nghĩa khi được định nghĩa bằng **đơn vị đo, độ chính xác thiết bị/dữ liệu và mức chênh lệch mà nghiệp vụ chấp nhận**.

## 2. Vì sao `==` thường không diễn tả điều bạn muốn?

`==` kiểm tra hai giá trị `double` có bằng nhau theo quy tắc dấu phẩy động Java; nó **không tự cho phép dung sai**.

~~~java
double actual = 0.1 + 0.2;
double expected = 0.3;

System.out.println(actual == expected); // false
System.out.println(actual - expected);  // 5.551115123125783E-17
~~~

`0.1`, `0.2` và `0.3` không biểu diễn đúng hoàn toàn bằng số nhị phân hữu hạn. Kết quả cộng và literal `0.3` có thể nằm ở **hai giá trị `double` kế cận nhau**.

Tuy nhiên, **`==` không phải luôn sai**. Nó phù hợp khi cần so chính xác một giá trị biểu diễn có chủ đích, chẳng hạn những trạng thái số hữu hạn/giá trị sentinel đã được đặc tả rõ. Khi yêu cầu là “chênh lệch không vượt quá sai số cho phép”, hãy định nghĩa phép so gần bằng riêng.

**Phân biệt:**

| Yêu cầu | Cách tiếp cận |
|---|---|
| Hai giá trị primitive phải bằng nhau đúng theo Java | `a == b` |
| Hai kết quả đo được chấp nhận nếu lệch không quá 0,05 mm | Sai số tuyệt đối **0,05 mm** |
| Sai số chấp nhận phụ thuộc quy mô đại lượng | Sai số tương đối |
| Cần chấp nhận chênh lệch cả gần 0 và số rất lớn | Kết hợp tuyệt đối + tương đối |
| Số tiền thập phân phải chính xác đến cent | Mô hình số nguyên theo cent hoặc `BigDecimal` với rounding rõ |
| Cần so một ID hoặc bộ đếm nguyên lớn | `int`/`long`/`BigInteger`, không dùng float/double |

## 3. Sai số tuyệt đối (*absolute tolerance*)

Hai giá trị `a` và `b` được coi là gần nhau theo sai số tuyệt đối `absTol` khi:

\[
|a-b| \le \text{absTol}
\]

Ví dụ một cảm biến đo chiều dài, yêu cầu lệch tối đa **0,05 mm**:

~~~java
double targetMm = 12.0;
double measuredMm = 12.04;
double allowedErrorMm = 0.05;

boolean acceptable =
        Math.abs(measuredMm - targetMm) <= allowedErrorMm;
System.out.println(acceptable); // true
~~~

Nếu kết quả đo là `12.08` mm, mức chênh lệch lớn hơn 0,05 mm và không đạt.

**Ưu điểm:** dễ giải thích theo đơn vị thực (`mm`, `°C`, `s`, `V`). **Hạn chế:** cùng một sai số tuyệt đối có thể quá dễ dãi với số rất nhỏ hoặc quá khắt khe với số rất lớn.

Ví dụ: sai số 0,05 m phù hợp hay không còn tùy đang đo kích thước linh kiện vài milimét hay chiều dài hàng kilomet.

**Không quên đơn vị:** nếu `a` đo bằng mét và `b` đo bằng milimét, phải **quy đổi về cùng đơn vị trước khi so sánh**. Bản thân hàm tính chênh lệch không nhận biết đơn vị.

## 4. Sai số tương đối (*relative tolerance*)

Sai số tương đối đánh giá độ chênh so với **quy mô của giá trị**. Một quy tắc đối xứng thường dùng:

\[
|a-b| \le \text{relTol}\times\max(|a|,|b|)
\]

Ví dụ:

~~~java
double a = 1_000_000_000.0;
double b = 1_000_000_001.0;
double relativeTolerance = 1e-8;

double difference = Math.abs(a - b);
double scale = Math.max(Math.abs(a), Math.abs(b));
boolean close = difference <= relativeTolerance * scale;

System.out.println(close); // true
~~~

Chênh lệch tuyệt đối là 1, nhưng tỷ lệ so với quy mô 1 tỷ chỉ xấp xỉ `1e-9`; mức cho phép `1e-8` chấp nhận được.

**Ưu điểm:** tỉ lệ sai số tự thay đổi theo độ lớn. **Hạn chế lớn:** khi một giá trị bằng 0, công thức thuần tương đối rất khắt khe:

~~~java
double a = 4e-10;
double b = 0.0;
double relTol = 1e-5;

// |a-b| = 4e-10; relTol * max(|a|, |b|) = 4e-15
// Kết quả: không gần nhau theo quy tắc chỉ tương đối này.
~~~

Nếu nghiệp vụ cho rằng `4e-10` gần 0 đủ để chấp nhận, phải có một **ngưỡng tuyệt đối gần 0**. Đây là lý do nhiều ứng dụng dùng cả hai loại dung sai.

## 5. Kết hợp tuyệt đối và tương đối

Một lựa chọn phổ biến khi muốn “đủ gần theo ít nhất một tiêu chí”:

\[
|a-b| \le \max(\text{absTol},\,\text{relTol}\times\max(|a|,|b|))
\]

Tương đương kiểm tra:

~~~java
difference <= absTol || difference <= relTol * scale
~~~

Cách hiểu:

- **Gần 0:** `absTol` xử lý chênh lệch nhỏ mang ý nghĩa tuyệt đối.
- **Ở quy mô lớn:** `relTol` cho phép chênh lệch tăng theo độ lớn đại lượng.
- **Miền trung gian:** dùng tiêu chí có ngưỡng rộng hơn.

| Trường hợp | absTol | relTol | Điều được kiểm tra |
|---|---:|---:|---|
| `0.1+0.2` so với `0.3` | `1e-12` | `1e-12` | Sai số tính toán rất nhỏ |
| `4e-10` so với `0` | `1e-9` | `1e-5` | Chấp nhận gần zero bằng ngưỡng tuyệt đối |
| `1e9` so với `1e9+1` | `0.01` | `1e-8` | Chấp nhận sai lệch theo quy mô lớn |
| `12.04` so với `12.0` mm | `0.05` | `0` | Dung sai thiết bị cố định 0,05 mm |

**Đây là quy tắc ví dụ, không phải định lý rằng OR luôn đúng cho mọi lĩnh vực.** Có hệ thống yêu cầu phải đạt **đồng thời** hai ràng buộc, hoặc cần kiểm tra giới hạn một chiều (chỉ được cao hơn/thấp hơn một giá trị); khi đó phải viết đúng chính sách nghiệp vụ.

## 6. Hàm Java 17 minh họa: `nearlyEqualFinite`

Hàm dưới đây dành cho **hai số hữu hạn** và hai ngưỡng không âm, hữu hạn. Nó **cố ý trả false** nếu đầu vào là `NaN` hoặc `Infinity`.

~~~java
static boolean nearlyEqualFinite(
        double a, double b, double absTol, double relTol) {
    if (!Double.isFinite(absTol) || !Double.isFinite(relTol)
            || absTol < 0 || relTol < 0 || relTol > 1) {
        throw new IllegalArgumentException("invalid tolerances");
    }

    if (!Double.isFinite(a) || !Double.isFinite(b)) {
        return false;
    }

    if (a == b) {
        return true; // Bao gồm +0.0 và -0.0
    }

    double difference = Math.abs(a - b);
    double scale = Math.max(Math.abs(a), Math.abs(b));

    return difference <= absTol || difference <= relTol * scale;
}
~~~

Sử dụng:

~~~java
System.out.println(
        nearlyEqualFinite(0.1 + 0.2, 0.3, 1e-12, 1e-12)
); // true

System.out.println(
        nearlyEqualFinite(1.0, 1.1, 1e-12, 1e-12)
); // false
~~~

**Lựa chọn chính sách trong hàm:**

1. Ngưỡng âm, `NaN`, Infinity hoặc `relTol > 1` là **tham số không hợp lệ**, nên ném `IllegalArgumentException`.
2. Hai đầu vào cần **hữu hạn**; cùng Infinity cũng trả `false` vì đây không phải một phép so hai đại lượng đo hữu hạn.
3. Hai giá trị bằng nhau theo `==` được chấp nhận; `+0.0` và `-0.0` vì thế được xem là gần nhau.
4. Nếu khác nhau, chênh lệch phải nằm trong **ít nhất một trong hai ngưỡng**.

**Giới hạn số học:** `a-b` vẫn có thể overflow thành Infinity nếu `a` và `b` hữu hạn nhưng cực lớn và trái dấu; các phép nhân và trừ `double` cũng có thể underflow/làm tròn. Hàm được thiết kế để **minh họa và dùng cho các miền đã kiểm soát**, không thay thế thuật toán so sánh chuẩn số học cho toàn bộ cực trị binary64. Với dữ liệu cực trị, cần đặc tả và kiểm thử riêng.

## 7. Chọn tolerance dựa trên yêu cầu, không dựa trên thói quen

Không có con số **`1e-6`**, **`1e-9`** hay **`1e-12`** nào đúng cho mọi bài toán.

Hãy trả lời:

1. **Đang đo đại lượng gì?** Độ dài, tiền, xác suất, áp suất, thời gian, tọa độ, nhiệt độ?
2. **Đơn vị và khoảng giá trị?** Mét hay millimét, giây hay nano giây, quanh 0 hay hàng tỷ?
3. **Sai số đầu vào là bao nhiêu?** Độ phân giải cảm biến, rounding từ dữ liệu, chất lượng thuật toán?
4. **Hậu quả khi chấp nhận nhầm?** Lỗi hiển thị đơn giản hay ảnh hưởng tính tiền, cảnh báo an toàn?
5. **Dùng so sánh để làm gì?** Kiểm tra kết quả tính toán, kiểm thử phần mềm, phân nhóm dữ liệu hay quyết định điều khiển?
6. **Ngưỡng có cần tính vào biên không?** `<=` cho phép bằng đúng biên, còn `<` thì không.

Ví dụ, cảm biến có sai số công bố ±0,05 mm thì một lựa chọn **có cơ sở nghiệp vụ** là `absTol = 0.05` mm sau khi quy đổi đơn vị, không phải mặc định dùng `Double.MIN_VALUE` hay `1e-12`.

Đối với kiểm thử thuật toán số, ngưỡng cần xét cả **sai số thuật toán**, độ nhạy bài toán và định dạng số; không nên chỉ nhìn vào số chữ số được hiển thị.

## 8. Hiểu `Math.ulp` và độ phân giải

`Math.ulp(x)` biểu diễn độ lớn một đơn vị ở vị trí thấp nhất của định dạng số thực tại giá trị `x` theo định nghĩa API. Nó hữu ích khi xem **khoảng cách biểu diễn** gần một số.

~~~java
double one = 1.0;
double next = Math.nextUp(one);

System.out.println(next);          // 1.0000000000000002
System.out.println(Math.ulp(one)); // 2.220446049250313E-16
System.out.println(next == one);   // false
~~~

`Math.nextUp(1.0)` là giá trị `double` biểu diễn được ngay sau 1.0. Tại 1.0, khoảng cách ấy bằng `Math.ulp(1.0)`.

**Không nhầm `Double.MIN_VALUE` với epsilon cho mọi phép so**: `Double.MIN_VALUE` là số dương nhỏ nhất khác 0 biểu diễn được dưới dạng `double`, không phải “sai số máy chung” quanh các giá trị bất kỳ.

So sánh theo số ULP có thể hữu ích trong kiểm thử thuật toán dấu phẩy động, nhưng cần quy tắc riêng cho số âm, zero, subnormal, Infinity, NaN và độ lớn. Nó **không thay thế tự động** dung sai nghiệp vụ như 0,05 mm.

## 9. `NaN`, Infinity và signed zero

### `NaN`

~~~java
double nan = Double.NaN;
System.out.println(nan == nan);       // false
System.out.println(Double.isNaN(nan)); // true
~~~

`NaN` không bằng ngay cả chính nó dưới toán tử `==`. Do đó kiểm tra `Math.abs(a-b) <= tolerance` cũng không cung cấp một quy tắc gần bằng có ý nghĩa khi một toán hạng là NaN. Cần **quy định chính sách rõ ràng**: reject, trả false hoặc báo lỗi.

### Infinity

~~~java
System.out.println(Double.POSITIVE_INFINITY
        == Double.POSITIVE_INFINITY); // true
~~~

Toán tử `==` cho true với cùng Infinity, nhưng hàm `nearlyEqualFinite` ở trên **trả false có chủ đích** vì chỉ so đại lượng hữu hạn. Không có một lựa chọn đúng chung: tùy hợp đồng phần mềm.

### Zero có dấu

~~~java
System.out.println(+0.0 == -0.0); // true
~~~

`+0.0` và `-0.0` có thể khác biểu diễn bit và tạo nghịch đảo Infinity khác dấu, nhưng `==` xem là bằng nhau. Nếu nghiệp vụ cần phân biệt **dấu zero hoặc bit pattern**, dùng API chuyên biệt thay vì phép so gần bằng.

**Chốt lại:** nên tách việc **kiểm tra dữ liệu đầu vào** (`Double.isFinite`) khỏi việc **so gần bằng**.

## 10. Khi `float`/`double` không phải giải pháp đúng

Dùng dung sai không phải cách hợp thức hóa một mô hình dữ liệu sai.

**Tiền tệ:** Nếu phải cộng `0.1 + 0.2` chính xác theo thập phân, cân nhắc:

~~~java
java.math.BigDecimal amount =
        new java.math.BigDecimal("0.1")
                .add(new java.math.BigDecimal("0.2"));

System.out.println(amount); // 0.3
~~~

Tạo `BigDecimal` từ chuỗi decimal giúp giữ đúng giá trị nhập. Dùng `BigDecimal.compareTo` khi muốn so **giá trị số**, và xác định `scale`/`RoundingMode` cho phép chia hoặc nghiệp vụ tiền tệ.

**ID / bộ đếm:** Giữ `long` hoặc `BigInteger`, không đổi sang `double` để “so gần bằng”: số nguyên lớn có thể mất độ chính xác khi chuyển thành floating-point.

**Giới hạn an toàn:** Với ràng buộc “nhiệt độ không được vượt 80 °C”, cần chính sách **so biên** rõ ràng, không tự thay bằng `nearlyEqual(temp, 80)` và coi mọi giá trị gần 80 là an toàn. Tolerance có thể hợp lý với cảm biến, nhưng phải gắn với độ bất định, cơ chế fail-safe và yêu cầu an toàn thực tế.

## 11. Tích lũy sai số và so sánh sau vòng lặp

~~~java
double total = 0.0;
for (int i = 0; i < 10; i++) {
    total += 0.1;
}

System.out.println(total);        // 0.9999999999999999
System.out.println(total == 1.0); // false
~~~

Mỗi phép cộng có thể làm tròn một lần. Kiểm thử chỉ dựa vào `total == 1.0` có thể thất bại dù thuật toán có sai số nằm trong giới hạn chấp nhận.

~~~java
System.out.println(
        nearlyEqualFinite(total, 1.0, 1e-12, 1e-12)
); // true
~~~

Nhưng **không đặt tolerance quá rộng** chỉ để làm test xanh. Nếu thuật toán đáng lẽ trả kết quả chính xác (ví dụ xử lý số tiền theo cent), nên sửa **mô hình tính**. Với thuật toán số học, ngưỡng nên xuất phát từ **error budget** và dữ liệu đại diện.

## 12. Các lỗi thường gặp

| Lỗi/nhầm lẫn | Vì sao nguy hiểm? | Hướng xử lý |
|---|---|---|
| `0.1 + 0.2 == 0.3` luôn true | Binary64 làm tròn | Dung sai được đặc tả, hoặc decimal exact |
| Luôn đặt epsilon `1e-9` | Đơn vị/miền số khác nhau | Chọn ngưỡng theo bài toán |
| Dùng chỉ sai số tương đối gần zero | Ngưỡng tương đối tiến về 0 | Kết hợp ngưỡng tuyệt đối |
| Dùng chỉ sai số tuyệt đối cho giá trị cực lớn | Có thể quá khắt khe | Xét relative tolerance |
| Cho rằng `Double.MIN_VALUE` là epsilon phổ dụng | Đây là dương nhỏ nhất khác 0 | Xem `Math.ulp` và sai số được phép |
| Không validate tolerance âm/NaN/Infinity | Điều kiện so trở nên vô nghĩa | Kiểm tra ngưỡng trước |
| Coi NaN gần bằng NaN vì cùng trạng thái | Không phải số hữu hạn | Quy định chính sách NaN riêng |
| Dùng `==` với Infinity và coi là số đo hợp lệ | Infinity không hữu hạn | `Double.isFinite` khi cần |
| So đại lượng khác đơn vị | Kết luận sai dù code đúng cú pháp | Quy đổi về cùng đơn vị |
| Chấp nhận lệch quá lớn vì `relTol` rộng | False positive | Dựa vào giới hạn nghiệp vụ |
| Dùng tolerance cho tiền/ID nguyên | Che sai mô hình số | `long` theo đơn vị cố định hoặc `BigDecimal` |
| Không test tại biên tolerance | Bug do `<` vs `<=`, rounding | Kiểm thử sát biên và miền cực trị |
| Xem hàm mẫu là giải pháp tổng quát mọi `double` | Overflow/underflow trong `a-b` và phép nhân | Thuật toán chuyên biệt khi cần |

## 13. Ví dụ Java 17 hoàn chỉnh, chạy độc lập

Tạo file đúng tên **`FloatingPointToleranceDemo.java`**:

~~~java
import java.math.BigDecimal;

public final class FloatingPointToleranceDemo {
    private FloatingPointToleranceDemo() {}

    static boolean nearlyEqualFinite(
            double a, double b, double absTol, double relTol) {
        if (!Double.isFinite(absTol) || !Double.isFinite(relTol)
                || absTol < 0 || relTol < 0 || relTol > 1) {
            throw new IllegalArgumentException("invalid tolerances");
        }
        if (!Double.isFinite(a) || !Double.isFinite(b)) {
            return false;
        }
        if (a == b) {
            return true;
        }
        double difference = Math.abs(a - b);
        double scale = Math.max(Math.abs(a), Math.abs(b));
        return difference <= absTol || difference <= relTol * scale;
    }

    public static void main(String[] args) {
        double computed = 0.1 + 0.2;
        System.out.println("computed=" + computed);
        System.out.println("exact equality=" + (computed == 0.3));
        System.out.println("decimal near="
                + nearlyEqualFinite(computed, 0.3, 1e-12, 1e-12));
        System.out.println("decimal too strict="
                + nearlyEqualFinite(computed, 0.3, 1e-18, 0));
        System.out.println("different number="
                + nearlyEqualFinite(computed, 0.4, 1e-12, 1e-12));

        System.out.println("near zero absolute="
                + nearlyEqualFinite(4e-10, 0.0, 1e-9, 0.0));
        System.out.println("near zero relative only="
                + nearlyEqualFinite(4e-10, 0.0, 0, 1e-5));

        System.out.println("large relative="
                + nearlyEqualFinite(1e9, 1e9 + 1, 0, 1e-8));
        System.out.println("large absolute only="
                + nearlyEqualFinite(1e9, 1e9 + 1, 0.01, 0));
        System.out.println("large different="
                + nearlyEqualFinite(1e9, 1e9 + 100, 0.01, 1e-8));

        System.out.println("sensor acceptable="
                + nearlyEqualFinite(12.04, 12.0, 0.05, 0));
        System.out.println("sensor rejected="
                + nearlyEqualFinite(12.08, 12.0, 0.05, 0));

        double next = Math.nextUp(1.0);
        System.out.println("next double=" + next);
        System.out.println("ULP at one=" + Math.ulp(1.0));
        System.out.println("next exact=" + (next == 1.0));

        double total = 0;
        for (int i = 0; i < 10; i++) {
            total += 0.1;
        }
        System.out.println("repeated sum=" + total);
        System.out.println("repeated near one="
                + nearlyEqualFinite(total, 1.0, 1e-12, 1e-12));

        System.out.println("signed zero="
                + nearlyEqualFinite(0.0, -0.0, 0, 0));
        System.out.println("NaN near="
                + nearlyEqualFinite(Double.NaN, Double.NaN, 1e-6, 1e-6));
        System.out.println("Infinity near="
                + nearlyEqualFinite(Double.POSITIVE_INFINITY,
                        Double.POSITIVE_INFINITY, 1e-6, 1e-6));

        BigDecimal decimal = new BigDecimal("0.1")
                .add(new BigDecimal("0.2"));
        System.out.println("decimal exact sum=" + decimal);
        System.out.println("decimal compare="
                + (decimal.compareTo(new BigDecimal("0.3")) == 0));

        try {
            nearlyEqualFinite(1.0, 1.0, -1.0, 1e-6);
        } catch (IllegalArgumentException ex) {
            System.out.println("bad tolerance=" + ex.getMessage());
        }
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên. Đặt terminal tại thư mục có file. Không cần Maven, Gradle hay thư viện ngoài JDK.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 FloatingPointToleranceDemo.java
java -cp . FloatingPointToleranceDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\FloatingPointToleranceDemo.java
java -cp . FloatingPointToleranceDemo
~~~

**Exact expected stdout:**

~~~text
computed=0.30000000000000004
exact equality=false
decimal near=true
decimal too strict=false
different number=false
near zero absolute=true
near zero relative only=false
large relative=true
large absolute only=false
large different=false
sensor acceptable=true
sensor rejected=false
next double=1.0000000000000002
ULP at one=2.220446049250313E-16
next exact=false
repeated sum=0.9999999999999999
repeated near one=true
signed zero=true
NaN near=false
Infinity near=false
decimal exact sum=0.3
decimal compare=true
bad tolerance=invalid tolerances
~~~

### Cách đọc kết quả

1. `computed` và `exact equality`: hai biểu thức thập phân gần nhau nhưng không có cùng biểu diễn binary64.
2. `decimal near=true` nhưng `decimal too strict=false`: **chọn tolerance ảnh hưởng kết luận**; không có một ngưỡng đúng toàn cục.
3. `near zero absolute=true` / `relative only=false`: ngưỡng tuyệt đối có vai trò đặc biệt quanh zero.
4. `large relative=true` nhưng `large absolute only=false`: mức chênh 1 có thể nhỏ theo quy mô 1 tỷ.
5. Hai dòng cảm biến minh họa việc đặt ngưỡng theo **mm**, không theo mặc định của ngôn ngữ.
6. `Math.nextUp` và `Math.ulp` cho thấy các giá trị double biểu diễn được nằm cách nhau một khoảng hữu hạn.
7. Cộng 0.1 lặp nhiều lần có thể tích lũy sai số; so gần bằng chấp nhận sai lệch theo quy định.
8. `NaN` và Infinity không được hàm **finite-only** chấp nhận; signed zero thì được.
9. `BigDecimal` từ chuỗi cho phép cộng decimal chính xác trong ví dụ.
10. Tolerance âm bị từ chối để không âm thầm trả kết quả không có ý nghĩa.

## 14. Bài tập có đáp án

### Bài A — so gần bằng

~~~java
double result = 0.1 + 0.2;
System.out.println(result == 0.3);
System.out.println(nearlyEqualFinite(result, 0.3, 1e-12, 1e-12));
~~~

**Đáp án:**

~~~text
false
true
~~~

Hàm `nearlyEqualFinite` đã được định nghĩa ở trên. `==` so đúng giá trị nhị phân, còn hàm cho phép sai lệch nhỏ được khai báo.

### Bài B — gần 0

Hai kết quả `a = 5e-10`, `b = 0`. Nghiệp vụ chấp nhận sai lệch tối đa `1e-9` (cùng đơn vị).

**Đáp án:**

~~~java
boolean accepted = nearlyEqualFinite(5e-10, 0.0, 1e-9, 0.0);
System.out.println(accepted); // true
~~~

Trong trường hợp này, sai số tuyệt đối mô tả đúng yêu cầu.

### Bài C — giá trị lớn

Hai số lần lượt là `1_000_000_000` và `1_000_000_001`. Cho phép mức chênh tương đối `1e-8`.

**Đáp án:**

~~~java
boolean accepted =
        nearlyEqualFinite(1e9, 1e9 + 1, 0, 1e-8);
System.out.println(accepted); // true
~~~

### Bài D — dữ liệu NaN

~~~java
double x = Double.NaN;
System.out.println(x == x);
System.out.println(nearlyEqualFinite(x, x, 1e-3, 1e-3));
~~~

**Đáp án:**

~~~text
false
false
~~~

Hàm ở bài này có chính sách **không chấp nhận số không hữu hạn**.

### Bài E — khi nào không nên dùng tolerance?

Yêu cầu cộng giá tiền thập phân `0.1` và `0.2`, sau đó so với `0.3` chính xác.

**Đáp án:** không nên “nới epsilon” để hợp thức hóa tiền; dùng số nguyên theo đơn vị nhỏ nhất khi thích hợp hoặc `BigDecimal` từ chuỗi:

~~~java
BigDecimal sum = new BigDecimal("0.1")
        .add(new BigDecimal("0.2"));
boolean exact = sum.compareTo(new BigDecimal("0.3")) == 0;
~~~

### Bài F — thiết kế kiểm thử tại biên

Nếu dung sai cho phép là 0,05 mm, nên thử các giá trị nào?

**Đáp án:** ít nhất nên thử chênh lệch 0, chênh lệch nhỏ hơn ngưỡng, sát ngưỡng dưới/trên và lớn hơn ngưỡng; đồng thời kiểm tra số âm, zero và dữ liệu không hữu hạn theo chính sách. Do chính `0.05` không có biểu diễn nhị phân chính xác, nếu hợp đồng đòi biên decimal tuyệt đối chính xác, có thể cần mô hình decimal thay vì kỳ vọng bit của `double` luôn trùng biên toán học.

## 15. Checklist và kết luận

- [ ] Phân biệt phép `==` chính xác với so gần bằng theo yêu cầu.
- [ ] Hiểu nguồn sai lệch của `float`/`double` là biểu diễn nhị phân hữu hạn và rounding.
- [ ] Biết chọn **absolute tolerance** theo đơn vị/độ phân giải dữ liệu.
- [ ] Biết chọn **relative tolerance** khi miền giá trị thay đổi theo quy mô.
- [ ] Hiểu vì sao gần zero cần cân nhắc ngưỡng tuyệt đối.
- [ ] Định nghĩa rõ **OR/AND** của hai ngưỡng theo chính sách chấp nhận sai số.
- [ ] Không áp đặt một epsilon cho mọi bài toán.
- [ ] Kiểm tra tính hữu hạn và tính hợp lệ của ngưỡng.
- [ ] Phân biệt `Double.MIN_VALUE` với ULP/độ phân giải.
- [ ] Hiểu `NaN`, Infinity và signed zero cần chính sách tường minh.
- [ ] Không dùng tolerance để so tiền, ID hoặc dữ liệu đòi tính chính xác tuyệt đối.
- [ ] Biết phép trừ `a-b` cũng có rủi ro rounding/overflow ở cực trị.
- [ ] Chạy `FloatingPointToleranceDemo.java` bằng `javac`/`java` trên Java 17+ và đối chiếu exact stdout.

**Kết luận:** So sánh số thực “đủ gần” không bắt đầu từ `1e-9`, mà bắt đầu từ câu hỏi **“mức lệch nào là chấp nhận được, tính bằng đơn vị gì, ở quy mô nào?”**. Dựa trên câu trả lời, hãy chọn sai số tuyệt đối, tương đối hoặc kết hợp chúng, kiểm soát `NaN`/Infinity và kiểm thử trường hợp biên. Nếu yêu cầu là chính xác thập phân hoặc số nguyên, hãy chọn **mô hình dữ liệu chính xác** thay vì dùng dung sai để che lỗi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="comparisons-007">Không viết chuỗi so sánh kiểu toán học như 0 &lt; x &lt; 10</a>

<details>
<summary>Xem chi tiết</summary>

# Không viết chuỗi so sánh kiểu toán học như `0 < x < 10` trong Java

> **Java core — language-basics / So sánh**  
> **Phiên bản:** Java 17+, một file runnable không cần thư viện ngoài.  
> **Mục tiêu:** Giải thích lỗi biên dịch của `0 < x < 10`; chuyển thành các phép so sánh độc lập nối bằng `&&`, chọn đúng biên mở/đóng và tránh tác dụng phụ khi đánh giá biểu thức.

## 1. Vì sao `0 < x < 10` không biên dịch?

Trong toán học, `0 < x < 10` có nghĩa `x` **đồng thời** lớn hơn 0 và nhỏ hơn 10. Trong Java, toán tử quan hệ `<` trả **`boolean`**, không phải một số.

~~~java
int x = 5;
// boolean valid = 0 < x < 10; // LỖI BIÊN DỊCH.
~~~

Hai toán tử `<` ở cùng mức ưu tiên và nhóm từ trái sang phải, nên cách hiểu cú pháp là:

~~~text
(0 < x) < 10
 \___/
 boolean
~~~

`0 < x` trả true/false, sau đó chương trình cố so sánh `boolean < int` — không hợp lệ. Đây là **lỗi kiểu khi biên dịch**, không phải một phép kiểm tra khoảng trả false.

Lỗi tương tự xuất hiện với `0 <= x <= 10`, `a < b < c` hoặc `a > b >= c`. Java không biến chuỗi so sánh quan hệ thành phép AND một cách tự động.

## 2. Viết đúng bằng `&&`

~~~java
int x = 5;
boolean inside = 0 < x && x < 10; // true.
~~~

Tương đương:

~~~java
boolean inside = (0 < x) && (x < 10);
~~~

Đây là **hai** phép so sánh độc lập, mỗi phép tạo một giá trị `boolean`. `&&` kết hợp các kết quả: toàn bộ true chỉ khi **cả hai đều true**.

Toán tử quan hệ có độ ưu tiên cao hơn `&&`, nên dấu ngoặc là tùy chọn ở biểu thức đơn giản. Với điều kiện dài hoặc trộn nhiều phép toán, dấu ngoặc giúp làm rõ ý định.

**Không dùng OR để thay thế:** `0 < x || x < 10` là điều kiện “hoặc”; với `x = -5`, vế thứ hai true; với `x = 20`, vế thứ nhất true. Nó không có nghĩa “x nằm giữa 0 và 10”.

## 3. Chọn đúng dấu ở các biên

| Khoảng cần kiểm tra | Biểu thức Java | Với `x=0` | Với `x=10` |
| --- | --- | --- | --- |
| Mở: lớn hơn 0, nhỏ hơn 10 | `0 < x && x < 10` | false | false |
| Đóng: từ 0 đến 10 | `0 <= x && x <= 10` | true | true |
| Lấy 0, loại 10 | `0 <= x && x < 10` | true | false |
| Loại 0, lấy 10 | `0 < x && x <= 10` | false | true |

Với khoảng mở `(0, 10)`, các giá trị 1 đến 9 (nếu `x` là số nguyên) hợp lệ; 0, 10 và mọi số ngoài khoảng đều không hợp lệ. Với chỉ số mảng, khoảng thường là **`0 <= index && index < array.length`**, vì chỉ số cuối là `length - 1`.

Muốn kiểm tra **nằm ngoài khoảng mở** thì dùng `x <= 0 || x >= 10`. Quy tắc De Morgan cho phép viết phủ định của `0 < x && x < 10` dưới dạng `!(0 < x && x < 10)`; chú ý với số thực `NaN`, hai cách diễn đạt phủ định phải được xét theo đúng semantics.

## 4. Short-circuit và kiểm tra an toàn

`&&` đánh giá vế trái trước. Nếu vế trái false, Java **bỏ qua vế phải**. Ví dụ bảo vệ chỉ số mảng:

~~~java
int[] values = {10, 20, 30};
int index = -1;

boolean safe = index >= 0
        && index < values.length
        && values[index] > 0;
// safe=false; values[-1] không được truy cập.
~~~

Nếu đặt `values[index] > 0` trước kiểm tra `index >= 0`, chương trình có thể ném `ArrayIndexOutOfBoundsException`. Với boolean `&`, Java đánh giá cả hai vế, không short-circuit, nên không thích hợp để bảo vệ vế phải trong tình huống này.

`&&` không làm cho mọi lỗi biến mất; chỉ bỏ qua **vế phải chưa cần đánh giá**. Vẫn phải viết điều kiện bảo vệ đúng thứ tự, bao gồm kiểm tra `null` trước khi gọi phương thức trên đối tượng.

## 5. Không đọc hoặc tính lại một giá trị có tác dụng phụ

Cách viết sau có thể kiểm tra **hai giá trị khác nhau**, không phải một số `x`:

~~~java
// Không nên dùng nếu readValue() có thể trả kết quả khác mỗi lần:
boolean result = 0 < readValue() && readValue() < 10;
~~~

Nếu lời gọi đầu tiên trả 5 và lời gọi thứ hai trả 50, toàn bộ điều kiện false, dù 5 thực sự nằm trong khoảng mở. Short-circuit còn làm cho lời gọi thứ hai **không được thực hiện** khi lời gọi đầu không thỏa giới hạn dưới.

Khi muốn kiểm tra **một lần đọc duy nhất**, hãy lưu kết quả:

~~~java
int x = readValue();
boolean inside = 0 < x && x < 10;
~~~

Điều này đặc biệt quan trọng với việc đọc input, đo thời gian, lấy dữ liệu thay đổi, random hoặc hàm có side effect.

## 6. Hai bẫy: `==` giữa boolean và `NaN`

Không nên cố thay `&&` bằng một phép so sánh bằng nhau giữa hai điều kiện:

~~~java
// Biên dịch được nhưng KHÔNG tương đương kiểm tra trong khoảng:
boolean wrong = (0 < x) == (x < 10);
~~~

Phép `==` chỉ hỏi **hai giá trị boolean có giống nhau không**. Nếu cả hai cùng false, kết quả vẫn true, trong khi AND phải false.

Điều này có thể được chứng minh bằng số thực `Double.NaN`:

~~~java
double x = Double.NaN;
boolean valid = 0.0 < x && x < 10.0; // false.
boolean wrong = (0.0 < x) == (x < 10.0); // true.
~~~

Vì mọi so sánh thứ tự `<`/`>` đối với `NaN` là false, biểu thức `false == false` trở thành true. Khi cần kiểm tra đầu vào `double` phải **hữu hạn**, thêm `Double.isFinite(x)`:

~~~java
boolean inside = Double.isFinite(x) && 0.0 < x && x < 10.0;
~~~

Đừng cố chuyển `boolean` thành số để viết chuỗi so sánh: Java không hỗ trợ ép `boolean` thành `int`. Dùng trực tiếp các phép toán logic.

## 7. Chương trình Java 17+ chạy độc lập

Tạo file **RangeComparisonDemo.java**:

~~~java
public final class RangeComparisonDemo {
    private static int reads;

    private static int readValue() {
        reads++;
        return reads == 1 ? 5 : 50;
    }

    private static boolean open(int x) {
        return 0 < x && x < 10;
    }

    private static boolean closed(int x) {
        return 0 <= x && x <= 10;
    }

    public static void main(String[] args) {
        for (int x : new int[] {-1, 0, 1, 9, 10, 11}) {
            System.out.println("x=" + x
                    + ",open=" + open(x)
                    + ",closed=" + closed(x));
        }

        int[] values = {10, 20, 30};
        int index = -1;
        boolean safe = index >= 0
                && index < values.length
                && values[index] > 0;
        System.out.println("guardedIndex=" + safe);

        double nan = Double.NaN;
        System.out.println("nanInRange="
                + (Double.isFinite(nan) && 0.0 < nan && nan < 10.0));
        System.out.println("nanEqualityTrap="
                + ((0.0 < nan) == (nan < 10.0)));

        reads = 0;
        boolean repeated = 0 < readValue() && readValue() < 10;
        System.out.println("repeated=" + repeated + ",reads=" + reads);

        reads = 0;
        int x = readValue();
        boolean sampled = 0 < x && x < 10;
        System.out.println("sampled=" + sampled + ",reads=" + reads);

        reads = 0;
        boolean skipped = readValue() < 0 && readValue() < 10;
        System.out.println("shortCircuit=" + skipped + ",reads=" + reads);
    }
}
~~~

Biên dịch và chạy ở thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 RangeComparisonDemo.java
java RangeComparisonDemo
~~~

**Expected stdout chính xác:**

~~~text
x=-1,open=false,closed=false
x=0,open=false,closed=true
x=1,open=true,closed=true
x=9,open=true,closed=true
x=10,open=false,closed=true
x=11,open=false,closed=false
guardedIndex=false
nanInRange=false
nanEqualityTrap=true
repeated=false,reads=2
sampled=true,reads=1
shortCircuit=false,reads=1
~~~

Trong ví dụ, `readValue()` trả 5 ở lần gọi đầu và 50 ở lần gọi thứ hai. `repeated` dùng **cả hai giá trị** nên false, còn `sampled` chỉ lấy một giá trị 5 nên true. Với `shortCircuit`, vế trái `5 < 0` false nên Java chỉ gọi `readValue()` một lần. `nanEqualityTrap=true` chứng minh so sánh **bằng nhau** giữa hai boolean không thay được `&&`.

## 8. Quy tắc thực hành và bài tập

| Trường hợp | Viết như thế nào? |
| --- | --- |
| Kiểm tra giá trị nằm giữa hai biên mở | `min < x && x < max` |
| Kiểm tra giá trị thuộc cả hai biên | `min <= x && x <= max` |
| Kiểm tra chỉ số mảng hợp lệ | `0 <= i && i < array.length` |
| Điều kiện ở vế phải có thể gây lỗi | Đặt guard bên trái `&&` |
| Giá trị lấy từ hàm có side effect | Gọi một lần, lưu vào biến trước khi so sánh |
| Số thực phải hữu hạn | Kết hợp `Double.isFinite(x)` với kiểm tra khoảng |

**Bài 1.** Viết điều kiện hợp lệ Java cho `-5 < x < 5` và `0 ≤ x ≤ 100`. Đừng dùng chuỗi quan hệ.

**Bài 2.** Giải thích vì sao `1 < x < 10` lỗi ngay từ lúc biên dịch; kiểu của `1 < x` là gì?

**Bài 3.** Kiểm tra `0 <= index && index < length` ở ba giá trị `index=-1`, `index=0`, `index=length`.

**Bài 4.** Vì sao `0 < getValue() && getValue() < 10` không nhất thiết kiểm tra một giá trị? Viết lại chỉ gọi `getValue()` một lần.

**Bài 5.** Chứng minh `(0.0 < Double.NaN) == (Double.NaN < 10.0)` true nhưng điều kiện AND trả false.

### Ghi nhớ

**Java không hỗ trợ `0 < x < 10`.** Phải viết **`0 < x && x < 10`** — hai phép so sánh độc lập nối bằng AND. Chọn `<` hoặc `<=` phù hợp với biên, tận dụng short-circuit cho guard và tránh gọi lại hàm có tác dụng phụ khi muốn kiểm tra **một** giá trị nhất quán.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="comparisons-008">Xây dựng điều kiện kiểm tra miền giá trị rõ ràng</a>

<details>
<summary>Xem chi tiết</summary>

# Xây dựng điều kiện kiểm tra miền giá trị rõ ràng — Java 17+

## 1. Mục tiêu

**Kiểm tra miền giá trị** (*range validation*) là xác định một giá trị có thuộc tập giá trị được phép hay không: tuổi từ 18 đến 65, chỉ số mảng từ 0 đến `length - 1`, tỷ lệ trong khoảng 0–1, một giá trị không được rơi vào vùng cấm, v.v.

Trong Java, điều kiện đúng không chỉ cần **biên dưới và biên trên**, mà còn phải quy định:

- Hai biên **được lấy hay loại trừ**?
- Chấp nhận **số âm**, số 0 và các giá trị cực trị không?
- Hai điều kiện nối bằng **`&&` (và)** hay **`||` (hoặc)**?
- Giá trị đến từ `int`/`long`, `float`/`double`, wrapper có thể null hay số decimal?
- Nếu giới hạn bị khai báo sai (`min > max`) thì báo lỗi hay trả false?

Một biểu thức dễ đọc, đúng về ý nghĩa:

~~~java
int age = 20;
boolean validAge = (age >= 18) && (age <= 65);

System.out.println(validAge); // true
~~~

Đây là **khoảng đóng** `[18, 65]`: bao gồm cả 18 và 65. Việc đặt dấu ngoặc riêng cho mỗi so sánh giúp làm rõ hai điều kiện; với biểu thức ngắn, có thể viết `age >= 18 && age <= 65`.

## 2. Dịch yêu cầu thành khoảng toán học và điều kiện Java

| Ý nghĩa | Ký hiệu toán học | Điều kiện Java |
|---|---|---|
| Từ `min` đến `max`, lấy hai biên | `[min, max]` | `value >= min && value <= max` |
| Nằm giữa, không lấy hai biên | `(min, max)` | `value > min && value < max` |
| Lấy biên dưới, không lấy biên trên | `[min, max)` | `value >= min && value < max` |
| Không lấy biên dưới, lấy biên trên | `(min, max]` | `value > min && value <= max` |
| Nhỏ hơn `min` hoặc lớn hơn `max` | Ngoài `[min, max]` | `value < min || value > max` |
| Không nhỏ hơn `min` | `[min, +∞)` | `value >= min` |
| Nhỏ hơn `max` | `(-∞, max)` | `value < max` |
| Bằng một đầu mút | `value = min` | `value == min` |

**Chọn `<` hay `<=` từ yêu cầu, không chọn tùy ý.** Ví dụ chỉ số mảng có `length` phần tử thường phải thuộc **`[0, length)`**, vì vị trí `length` không tồn tại:

~~~java
int[] numbers = {10, 20, 30};
int index = 2;
boolean validIndex = index >= 0 && index < numbers.length;
System.out.println(validIndex); // true
~~~

Nếu `index == numbers.length`, điều kiện trả false. Trước khi truy cập mảng, vẫn cần kiểm tra rằng tham chiếu mảng không null nếu đầu vào có thể thiếu.

## 3. Java không hỗ trợ so sánh dây chuyền kiểu toán học

Trong toán học có thể viết:

~~~text
18 <= age <= 65
~~~

Nhưng Java **không** cho phép:

~~~java
// 18 <= age <= 65 // LỖI BIÊN DỊCH
~~~

Biểu thức `18 <= age` tạo `boolean`, không thể tiếp tục dùng `<= 65` với boolean và int.

Viết hai phép so sánh đầy đủ:

~~~java
boolean valid = (age >= 18) && (age <= 65);
~~~

**Không** dùng `||` khi muốn nằm trong cả hai biên:

~~~java
int age = 100;
boolean wrong = (age >= 18) || (age <= 65);
System.out.println(wrong); // true: điều kiện sai về ý nghĩa
~~~

Với `min <= max`, điều kiện `value >= min || value <= max` hầu như luôn true đối với số nguyên: nó đang hỏi **“thỏa ít nhất một phía”**, không phải “ở giữa”.

## 4. Kiểm tra ngoài miền: dùng `||`, không dùng `&&`

Nếu miền hợp lệ là `[min, max]`, một số nguyên nằm **ngoài miền** khi:

~~~java
boolean outside = (value < min) || (value > max);
~~~

Một số nguyên không thể đồng thời nhỏ hơn min **và** lớn hơn max nếu `min <= max`, nên viết:

~~~java
// value < min && value > max // Sai khi định kiểm tra ngoài miền
~~~

Các cặp điều kiện tương đương trên số nguyên khi giới hạn hợp lệ:

| Ý định | Biểu thức |
|---|---|
| Trong khoảng đóng | `value >= min && value <= max` |
| Ngoài khoảng đóng | `value < min || value > max` |
| Phủ định điều kiện trong khoảng | `!(value >= min && value <= max)` |
| Trong khoảng mở | `value > min && value < max` |
| Ngoài khoảng mở | `value <= min || value >= max` |

Đây là ứng dụng quy tắc **De Morgan**: `!(A && B)` tương đương `!A || !B`. Khi điều kiện phức tạp, nên đặt tên rõ:

~~~java
boolean inside = (value >= min) && (value <= max);
boolean rejected = !inside;
~~~

**Lưu ý với `double` và `NaN`:** cách viết `value < min || value > max` **không nhận diện được NaN**, vì cả hai phép so sánh đều false khi `value` là NaN. Cách xử lý riêng sẽ có ở phần số thực.

## 5. `&&`, `||`, dấu ngoặc và short-circuit

`&&` có mức ưu tiên cao hơn `||`. Khi kết hợp nhiều nhóm, đặt ngoặc theo **quy tắc nghiệp vụ**:

~~~java
boolean adult = true;
boolean verified = false;
boolean admin = true;

boolean ruleA = adult && (verified || admin);
boolean ruleB = (adult && verified) || admin;

System.out.println(ruleA); // true
System.out.println(ruleB); // true
~~~

Với bộ dữ liệu trên, cả hai đều true; nhưng **không đồng nghĩa** chúng luôn tương đương. Hãy xét `adult = false`, `verified = false`, `admin = true`: `ruleA` là false còn `ruleB` là true. Nếu điều kiện tuổi bắt buộc với mọi người, chỉ `adult && (verified || admin)` phản ánh đúng yêu cầu.

Toán tử `&&` và `||` **đánh giá ngắn mạch**:

~~~java
Integer age = null;
boolean validAge = (age != null) && (age >= 18) && (age <= 65);
System.out.println(validAge); // false, không unbox null
~~~

Nếu `age == null`, vế sau không chạy, tránh `NullPointerException`. Đừng thay `&&` bằng `&` nếu mục đích là bỏ qua phép kiểm tra tiếp theo khi điều kiện trước thất bại.

## 6. Validate cấu hình biên trước khi dùng

Một miền `[min, max]` chỉ hợp lệ nếu `min <= max`. Nếu API nhận giới hạn đảo ngược:

~~~java
int min = 100;
int max = 50;
~~~

thì biểu thức `value >= min && value <= max` trả false cho mọi `int`, **che giấu lỗi cấu hình**.

Tạo hàm có hợp đồng rõ:

~~~java
static boolean inClosedRange(int value, int min, int max) {
    if (min > max) {
        throw new IllegalArgumentException("min > max");
    }
    return value >= min && value <= max;
}
~~~

**Hai lựa chọn khác nhau:**

1. **Giới hạn sai là lỗi cấu hình:** ném `IllegalArgumentException`.
2. **Khoảng rỗng là trạng thái có chủ đích:** có thể trả false, nhưng phải ghi rõ trong hợp đồng.

Bài này chọn cách đầu. Với miền `[min, max)`, nếu `min == max`, miền rỗng theo định nghĩa; không nhất thiết là lỗi — hãy quyết định phù hợp với API. Trong ví dụ Java đầy đủ, khoảng nửa mở cho phép hai biên bằng nhau và luôn trả false.

## 7. Không kiểm tra miền số nguyên bằng phép trừ dễ tràn

Dễ gặp cách viết:

~~~java
// KHÔNG NÊN:
// value - min >= 0 && max - value >= 0
~~~

Với `int`, phép trừ có thể **overflow** trước khi so sánh. Ví dụ:

~~~java
int value = Integer.MAX_VALUE;
int min = -1;

System.out.println(value - min); // -2147483648: tràn int
~~~

Trong khi đó so sánh trực tiếp:

~~~java
boolean atLeastMin = value >= min;
System.out.println(atLeastMin); // true
~~~

đúng với miền giá trị nguyên mà không thực hiện phép trừ có nguy cơ overflow.

**Dùng so sánh trực tiếp** cho điều kiện miền; nếu thực sự cần tính độ dài khoảng, hãy cân nhắc chuyển rộng **trước phép trừ**:

~~~java
long width = (long) max - (long) min;
~~~

Đừng gán một kết quả trừ int đã tràn sang long rồi mong số được sửa lại.

## 8. Số thực: `NaN`, Infinity và lựa chọn miền hợp lệ

Với `double`:

~~~java
double value = Double.NaN;

boolean inside = value >= 0.0 && value <= 1.0;
boolean naivelyOutside = value < 0.0 || value > 1.0;

System.out.println(inside);         // false
System.out.println(naivelyOutside); // false
~~~

Cả hai đều false vì mọi phép so sánh thứ tự (`<`, `<=`, `>`, `>=`) có toán hạng NaN đều false. Nếu chỉ kiểm tra `outside` rồi suy rằng `!outside` là hợp lệ, NaN **có thể lọt qua**.

Nếu miền dữ liệu cho phép **chỉ số hữu hạn**, dùng:

~~~java
static boolean inFiniteClosedRange(
        double value, double min, double max) {
    if (!Double.isFinite(min) || !Double.isFinite(max) || min > max) {
        throw new IllegalArgumentException("invalid floating bounds");
    }
    return Double.isFinite(value) && value >= min && value <= max;
}
~~~

Ở đây:

- `min`/`max` phải hữu hạn và theo đúng thứ tự.
- `value` là `NaN` hoặc Infinity → false.
- Cả `+0.0` và `-0.0` thỏa một miền đóng chứa 0.
- Các so sánh là so đúng **giá trị `double` đã biểu diễn**.

**Ranh giới số thực cần đặc tả rõ**: `0.1 + 0.2` trong double cho `0.30000000000000004`; vì vậy phép kiểm tra `value <= 0.3` trả false. Không tự mở rộng ngưỡng để làm kết quả “đẹp” nếu nghiệp vụ yêu cầu biên cứng. Với dữ liệu đo có dung sai thực tế, phải quy định và kiểm chứng **chính sách sai số** phù hợp; với giá trị thập phân đòi chính xác, cân nhắc `BigDecimal`.

## 9. `BigDecimal`: so miền theo giá trị thập phân

Với số tiền hoặc tỷ lệ cần so đúng decimal, dùng `BigDecimal.compareTo`:

~~~java
java.math.BigDecimal rate = new java.math.BigDecimal("0.30");
java.math.BigDecimal min = new java.math.BigDecimal("0.00");
java.math.BigDecimal max = new java.math.BigDecimal("1.00");

boolean valid = rate.compareTo(min) >= 0 && rate.compareTo(max) <= 0;
System.out.println(valid); // true
~~~

`compareTo` so **giá trị số**, không yêu cầu cùng scale. Ví dụ `new BigDecimal("1.0")` và `new BigDecimal("1.00")` có `compareTo == 0`, nhưng `equals` có thể false do khác scale.

**Không khởi tạo BigDecimal từ `new BigDecimal(0.1)` nếu muốn chính xác giá trị decimal 0.1**. Tạo từ chuỗi `"0.1"` hoặc theo một đường chuyển đổi được đặc tả, và chuẩn hóa đơn vị/rounding theo nghiệp vụ.

## 10. Miền hợp lệ có nhiều khoảng hoặc vùng cấm

Ví dụ cho phép điểm nằm trong **`[1, 5]` hoặc `[10, 12]`**:

~~~java
boolean allowed =
        (score >= 1 && score <= 5)
        || (score >= 10 && score <= 12);
~~~

Dấu ngoặc giúp thể hiện hai khoảng được **nối bằng OR**. Nếu đã có hàm:

~~~java
boolean allowed =
        inClosedRange(score, 1, 5)
        || inClosedRange(score, 10, 12);
~~~

Ví dụ chỉ chấp nhận `[1, 10]` nhưng **loại trừ `[4, 6]`**:

~~~java
boolean allowed = inClosedRange(score, 1, 10)
        && !inClosedRange(score, 4, 6);
~~~

**Bảo đảm ý nghĩa tại biên:** nếu `score = 4` hoặc 6, điều kiện vùng cấm đóng trả true và toàn điều kiện trả false. Nếu muốn cấm chỉ `(4, 6)`, phải dùng toán tử `>` và `<` thay vì `>=`, `<=`.

## 11. Guard clause: tách validation khỏi xử lý

Thay vì nhồi điều kiện vào nhiều nhánh lồng nhau, một hàm có thể reject dữ liệu ngoài miền ngay đầu vào:

~~~java
static int requireAge(int age) {
    if (!inClosedRange(age, 18, 65)) {
        throw new IllegalArgumentException("age out of range");
    }
    return age;
}
~~~

Cách này giúp:

- Biên miền được kiểm tra ở **một nơi rõ ràng**.
- Code xử lý chính chỉ chạy sau khi input hợp lệ.
- Đặt test cho **giới hạn dưới, giới hạn trên và giá trị sát hai phía** dễ hơn.
- Phân biệt rõ **lỗi tham số** với kết quả hợp lệ có giá trị `0` hoặc `false`.

Với ứng dụng nhận dữ liệu người dùng, có thể trả đối tượng kết quả validation thay vì ném ngoại lệ; đây là lựa chọn giao diện API, không thay đổi toán tử so sánh cơ bản.

~~~mermaid
flowchart TD
    A["Nhận value, min, max"] --> B{"Giới hạn hợp lệ?"}
    B -- "Không" --> C["Báo lỗi cấu hình/đầu vào"]
    B -- "Có" --> D{"Kiểu số thực? Cần finite?"}
    D -- "Có" --> E{"Giá trị hữu hạn?"}
    E -- "Không" --> F["Từ chối giá trị"]
    E -- "Có" --> G{"Đạt hai điều kiện biên?"}
    D -- "Không" --> G
    G -- "Có" --> H["Hợp lệ"]
    G -- "Không" --> F
~~~

## 12. Bảng kiểm thử biên bắt buộc

Với miền đóng **`[10, 20]`**:

| Giá trị | Kết quả đúng | Lý do |
|---:|---|---|
| `9` | false | Ngay dưới biên |
| `10` | true | Bằng biên dưới |
| `11` | true | Bên trong |
| `20` | true | Bằng biên trên |
| `21` | false | Ngay trên biên |

Với miền nửa mở **`[0, 3)`** dành cho chỉ số:

| Giá trị | Kết quả đúng |
|---:|---|
| `-1` | false |
| `0` | true |
| `2` | true |
| `3` | false |

Ngoài các giá trị thông thường, nên kiểm tra:

- `Integer.MIN_VALUE`, `Integer.MAX_VALUE` và các mốc gần chúng.
- `min == max`, `min > max` theo hợp đồng hàm.
- `null` với wrapper hoặc object đầu vào.
- `Double.NaN`, hai dấu Infinity và signed zero nếu dùng floating-point.
- Giá trị rất gần biên floating-point do làm tròn.
- Các miền bị nối `||` hoặc loại trừ bằng `!`.

## 13. Chương trình Java 17 hoàn chỉnh — chạy được bằng CLI

Tạo đúng file **`RangeConditionDemo.java`**:

~~~java
import java.math.BigDecimal;

public final class RangeConditionDemo {
    private RangeConditionDemo() {}

    static boolean inClosedRange(int value, int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min > max");
        }
        return value >= min && value <= max;
    }

    static boolean inOpenRange(int value, int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min > max");
        }
        return value > min && value < max;
    }

    static boolean inHalfOpenRange(int value, int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min > max");
        }
        return value >= min && value < max;
    }

    static boolean inFiniteClosedRange(
            double value, double min, double max) {
        if (!Double.isFinite(min) || !Double.isFinite(max)
                || min > max) {
            throw new IllegalArgumentException("invalid floating bounds");
        }
        return Double.isFinite(value)
                && value >= min && value <= max;
    }

    static int requireAge(int age) {
        if (!inClosedRange(age, 18, 65)) {
            throw new IllegalArgumentException("age out of range");
        }
        return age;
    }

    public static void main(String[] args) {
        System.out.println("closed lower="
                + inClosedRange(10, 10, 20));
        System.out.println("closed upper="
                + inClosedRange(20, 10, 20));
        System.out.println("closed below="
                + inClosedRange(9, 10, 20));
        System.out.println("closed above="
                + inClosedRange(21, 10, 20));

        System.out.println("open lower="
                + inOpenRange(10, 10, 20));
        System.out.println("open middle="
                + inOpenRange(15, 10, 20));
        System.out.println("half open start="
                + inHalfOpenRange(0, 0, 3));
        System.out.println("half open end="
                + inHalfOpenRange(3, 0, 3));
        System.out.println("half open empty="
                + inHalfOpenRange(3, 3, 3));

        int score = 4;
        boolean union = inClosedRange(score, 1, 5)
                || inClosedRange(score, 10, 12);
        System.out.println("union accepted=" + union);

        score = 7;
        boolean gap = inClosedRange(score, 1, 5)
                || inClosedRange(score, 10, 12);
        System.out.println("union gap=" + gap);

        boolean excluded = inClosedRange(score, 1, 10)
                && !inClosedRange(score, 4, 6);
        System.out.println("exclude forbidden=" + excluded);
        System.out.println("negative accepted="
                + inClosedRange(-2, -5, 0));
        System.out.println("near int max="
                + inClosedRange(Integer.MAX_VALUE,
                        Integer.MAX_VALUE - 1, Integer.MAX_VALUE));

        int value = Integer.MAX_VALUE;
        int min = -1;
        System.out.println("unsafe subtraction=" + (value - min));
        System.out.println("direct comparison=" + (value >= min));

        double computed = 0.1 + 0.2;
        System.out.println("floating upper strict="
                + inFiniteClosedRange(computed, 0.0, 0.3));
        System.out.println("floating inside="
                + inFiniteClosedRange(computed, 0.0, 0.4));
        System.out.println("NaN accepted="
                + inFiniteClosedRange(Double.NaN, 0.0, 1.0));
        System.out.println("Infinity accepted="
                + inFiniteClosedRange(Double.POSITIVE_INFINITY, 0.0, 1.0));
        System.out.println("negative zero accepted="
                + inFiniteClosedRange(-0.0, 0.0, 1.0));

        double nan = Double.NaN;
        System.out.println("naive NaN outside="
                + (nan < 0.0 || nan > 1.0));
        System.out.println("validated NaN invalid="
                + !inFiniteClosedRange(nan, 0.0, 1.0));

        BigDecimal rate = new BigDecimal("0.30");
        BigDecimal low = new BigDecimal("0.00");
        BigDecimal high = new BigDecimal("1.00");
        boolean validDecimal = rate.compareTo(low) >= 0
                && rate.compareTo(high) <= 0;
        System.out.println("decimal within=" + validDecimal);

        Integer nullableAge = null;
        boolean validNullableAge = nullableAge != null
                && inClosedRange(nullableAge, 18, 65);
        System.out.println("nullable age=" + validNullableAge);

        int denominator = 0;
        boolean guarded = denominator != 0
                && 10 / denominator > 1;
        System.out.println("short circuit guard=" + guarded);

        System.out.println("age valid=" + requireAge(20));
        try {
            requireAge(17);
        } catch (IllegalArgumentException ex) {
            System.out.println("age invalid=" + ex.getMessage());
        }
        try {
            inClosedRange(10, 20, 5);
        } catch (IllegalArgumentException ex) {
            System.out.println("bad integer bounds=" + ex.getMessage());
        }
        try {
            inFiniteClosedRange(0.5, 1.0, 0.0);
        } catch (IllegalArgumentException ex) {
            System.out.println("bad floating bounds=" + ex.getMessage());
        }
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên, terminal mở tại thư mục chứa file; không cần Maven, Gradle hoặc thư viện bên ngoài JDK.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 RangeConditionDemo.java
java -cp . RangeConditionDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\RangeConditionDemo.java
java -cp . RangeConditionDemo
~~~

**Exact expected stdout:**

~~~text
closed lower=true
closed upper=true
closed below=false
closed above=false
open lower=false
open middle=true
half open start=true
half open end=false
half open empty=false
union accepted=true
union gap=false
exclude forbidden=true
negative accepted=true
near int max=true
unsafe subtraction=-2147483648
direct comparison=true
floating upper strict=false
floating inside=true
NaN accepted=false
Infinity accepted=false
negative zero accepted=true
naive NaN outside=false
validated NaN invalid=true
decimal within=true
nullable age=false
short circuit guard=false
age valid=20
age invalid=age out of range
bad integer bounds=min > max
bad floating bounds=invalid floating bounds
~~~

### Giải thích các nhóm kết quả

1. **Khoảng đóng/mở/nửa mở:** `[10,20]` lấy cả 10 và 20, `(10,20)` không lấy 10, `[0,3)` lấy 0 nhưng loại 3.
2. **Hai khoảng và vùng cấm:** `||` diễn đạt hợp của hai miền, còn `&& !(...)` loại một miền con.
3. **Số âm và cận lớn:** so sánh trực tiếp vẫn đúng; `value-min` trên `int` có thể tràn và tạo giá trị sai.
4. **Floating-point:** `0.1 + 0.2` lớn hơn literal `0.3` một chút; hàm finite-only từ chối NaN và Infinity.
5. **NaN:** hai phép kiểm tra `nan < min`, `nan > max` đều false; cần chính sách kiểm tra tính hữu hạn trước.
6. **Decimal:** `BigDecimal.compareTo` kiểm tra giá trị thập phân chính xác.
7. **Wrapper và short-circuit:** guard `null`/mẫu số 0 ngăn các phép toán nguy hiểm ở vế sau.
8. **Guard clauses:** trả giá trị hợp lệ hoặc báo ngoại lệ có thông điệp rõ khi giá trị/biên không hợp lệ.

## 14. Lỗi thường gặp và cách sửa

| Lỗi hoặc ngộ nhận | Vấn đề | Cách đúng |
|---|---|---|
| `min <= x <= max` | Java không có so sánh dây chuyền | `x >= min && x <= max` |
| `x >= min || x <= max` để kiểm tra trong khoảng | `||` chỉ yêu cầu một vế đúng | Dùng `&&` |
| `x < min && x > max` để kiểm tra ngoài khoảng | Không thể cùng đúng nếu min ≤ max | Dùng `||` |
| Dùng `<= length` cho index mảng | Truy cập index bằng length vượt giới hạn | Dùng `index < length` |
| Quên xét biên đầu và biên cuối | Off-by-one | Test sát hai biên |
| Không kiểm tra `min > max` | Lỗi cấu hình bị che thành false | Validate giới hạn |
| `x - min >= 0` thay cho `x >= min` | Integer subtraction có thể overflow | So sánh trực tiếp |
| `! (x < min || x > max)` để duyệt double mà không xét NaN | NaN làm hai phép so false, phủ định cho true | `Double.isFinite(x)` và kiểm tra trong miền |
| Xem Infinity là dữ liệu đo hợp lệ | Infinity không hữu hạn | Chính sách chấp nhận rõ ràng |
| Dùng biên double 0.3 rồi mong mọi phép tính gần 0.3 đều lọt | Sai số nhị phân | Dung sai có cơ sở hoặc `BigDecimal` |
| Dùng `BigDecimal.equals` để so giá trị decimal mọi scale | `equals` còn xét scale | `compareTo` |
| Dùng `&` thay `&&` để guard null | `&` đánh giá cả hai vế | `&&` |
| Gộp nhiều nhóm `&&`/`||` không có ngoặc | Dễ hiểu sai logic | Đặt ngoặc theo quy tắc nghiệp vụ |
| Cắt `double` thành `int` rồi mới kiểm tra miền | Mất phần lẻ, có thể chấp nhận nhầm | Validate đúng kiểu trước chuyển đổi |

## 15. Bài tập có đáp án

### Bài A — khoảng đóng

Kiểm tra `score` từ 0 tới 100, **lấy hai biên**.

~~~java
boolean valid = score >= 0 && score <= 100;
~~~

**Đáp án:** với `score = 0` hoặc `100`, true; `-1` hoặc `101`, false.

### Bài B — chỉ số mảng

Mảng có `length = 5`. Viết điều kiện cho index hợp lệ.

~~~java
boolean validIndex = index >= 0 && index < 5;
~~~

**Đáp án:** `0..4` là hợp lệ; `-1` và `5` không hợp lệ.

### Bài C — ngoài khoảng

Viết điều kiện để reject tuổi **ngoài `[18,65]`**.

~~~java
boolean rejected = age < 18 || age > 65;
~~~

**Đáp án:** với tuổi 17 hoặc 66, true; tuổi 18 hoặc 65, false.

### Bài D — khoảng rỗng

~~~java
System.out.println(inHalfOpenRange(5, 5, 5));
~~~

**Đáp án:** `false`, vì `[5,5)` rỗng. Hàm ví dụ cho phép `min == max` trong miền nửa mở.

### Bài E — NaN

~~~java
double x = Double.NaN;
boolean outside = x < 0 || x > 1;
System.out.println(outside);
~~~

**Đáp án:** `false`, nhưng **không có nghĩa x hợp lệ**. Cần reject NaN tường minh bằng `Double.isFinite(x)` hoặc `Double.isNaN(x)` theo chính sách.

### Bài F — hai miền được phép

Chấp nhận `x` trong `[1,3]` **hoặc** `[7,9]`.

~~~java
boolean accepted =
        (x >= 1 && x <= 3)
        || (x >= 7 && x <= 9);
~~~

**Đáp án:** 1, 3, 7, 9 đều được chấp nhận; 4, 5, 6 bị từ chối.

### Bài G — lỗi overflow khi so miền

~~~java
int value = Integer.MAX_VALUE;
int min = -1;
System.out.println(value - min >= 0);
System.out.println(value >= min);
~~~

**Đáp án:**

~~~text
false
true
~~~

Dòng đầu bị tràn khi trừ int; dòng sau dùng so sánh trực tiếp và chính xác.

## 16. Checklist và kết luận

- [ ] Chọn rõ khoảng đóng, mở, nửa mở và ý nghĩa tại biên.
- [ ] Dùng `&&` để yêu cầu đồng thời đạt biên dưới và biên trên.
- [ ] Dùng `||` để nhận diện ngoài khoảng hoặc hợp nhiều khoảng.
- [ ] Không viết `min <= value <= max` trong Java.
- [ ] Validate `min <= max` theo hợp đồng API.
- [ ] Kiểm thử `min - 1`, `min`, trong miền, `max`, `max + 1` khi không overflow.
- [ ] Với index mảng, dùng miền `[0,length)`.
- [ ] Tránh công thức kiểm tra miền số nguyên dùng phép trừ có thể overflow.
- [ ] Với `double`, có chính sách rõ cho NaN, Infinity và sai số tại biên.
- [ ] Không dùng tolerance tùy tiện để vượt qua ràng buộc an toàn.
- [ ] Với decimal exact, xét `BigDecimal.compareTo`.
- [ ] Dùng guard `null` với wrapper và `&&` để tránh unboxing null.
- [ ] Đặt ngoặc giúp người đọc nhận ra các nhóm điều kiện nghiệp vụ.
- [ ] Chạy `RangeConditionDemo.java` bằng Java 17+ và đối chiếu từng dòng expected output.

**Kết luận:** Một điều kiện miền giá trị tốt phải **biểu đạt đúng tập giá trị cho phép**, xử lý rõ hai biên, dùng đúng `&&`/`||`, tránh overflow và định nghĩa chính sách cho dữ liệu số thực bất thường. Hãy đặt tên cho điều kiện quan trọng, validate giới hạn và kiểm thử ngay tại ranh giới để code Java 17+ dễ đọc, dễ bảo trì và không chấp nhận nhầm dữ liệu.

</details>

- [Quay lại đầu trang](#back-to-top)
