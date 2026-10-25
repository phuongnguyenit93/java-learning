<a id="back-to-top"></a>

# Chuyển đổi kiểu

## Menu
- [Chuyển đổi ngầm định và ép kiểu tường minh](#type-conversion-001)
- [Mở rộng và thu hẹp kiểu nguyên thủy](#type-conversion-002)
- [Mở rộng số nguyên sang số thực vẫn có thể mất độ chính xác](#type-conversion-003)
- [Thu hẹp số nguyên và phần bit bị loại bỏ](#type-conversion-004)
- [Chuyển số thực sang số nguyên và cắt phần thập phân](#type-conversion-005)
- [Unary numeric promotion và binary numeric promotion](#type-conversion-006)
- [Vì sao phép cộng byte hoặc short thường cho kết quả int](#type-conversion-007)
- [Chuyển đổi trong phép gán, lời gọi phương thức và biểu thức](#type-conversion-008)
- [Phép gán hằng nằm trong miền giá trị của byte, short hoặc char](#type-conversion-009)
- [Ép kiểu tham chiếu, instanceof và ClassCastException](#type-conversion-010)

## <a id="type-conversion-001">Chuyển đổi ngầm định và ép kiểu tường minh</a>

<details>
<summary>Xem chi tiết</summary>

# Chuyển đổi ngầm định và ép kiểu tường minh trong Java 17+

**Chuyển đổi kiểu (*type conversion*)** là cách Java đưa giá trị của một biểu thức từ kiểu này sang kiểu khác. Hai trường hợp cần phân biệt khi viết mã:

- **Chuyển đổi ngầm định (*implicit conversion*)**: Java áp dụng quy tắc chuyển kiểu được phép mà không cần viết toán tử ép kiểu, ví dụ chuyển `int` sang `long` khi gán hoặc nâng các toán hạng số trước phép toán.
- **Ép kiểu tường minh (*explicit cast*)**: người lập trình viết toán tử `(TargetType) expression`, thường khi chuyển kiểu có thể mất dữ liệu hoặc khi cần thu hẹp một tham chiếu. **Cast không bảo đảm kết quả chính xác hoặc an toàn lúc chạy.**

~~~java
int count = 120;
long wide = count;         // Ngam dinh: int -> long.
byte small = (byte) count; // Tuong minh: int -> byte.
~~~

Trong ví dụ, cả `wide` và `small` đều là 120 vì giá trị 120 biểu diễn được bằng `byte`. Nhưng nếu `count = 130`, phép cast sang `byte` cho `-126`.

## 1. Chuyển đổi số nguyên thủy: widening và narrowing

| Loại | Ví dụ | Compiler xử lý | Điều cần lưu ý |
| --- | --- | --- | --- |
| **Widening primitive** (mở rộng) | `long a = 42;` | Tự động cho phép | Không phải mọi chuyển sang số thực đều giữ độ chính xác |
| **Narrowing primitive** (thu hẹp) | `byte b = (byte) 130;` | Thường cần cast | Có thể mất bit, thay đổi giá trị |
| Mở rộng `char` | `int n = 'A';` | Tự động | `char` không dấu, `'A'` thành 65 |
| Thu hẹp thành `char` | `char c = (char) 66;` | Thường cần cast | Kết quả trong miền 0–65.535 |
| Chuyển `double` sang `int` | `int n = (int) 12.9;` | Cần cast | Bỏ phần thập phân, **không làm tròn gần nhất** |
| Ngầm định tại biểu thức số học | `int sum = byteA + byteB;` | Hai `byte` thường được nâng thành `int` | Kiểu kết quả phụ thuộc toán hạng |
| Chuyển `boolean` và kiểu số | `(int) true` | **Không hợp lệ** | Java không chuyển `boolean` sang số bằng cast |

Một chuỗi chuyển đổi mở rộng quen thuộc:

~~~text
byte -> short -> int -> long -> float -> double
                  ^
                 char  (char -> int -> long -> float -> double)
~~~

Đây là sơ đồ ghi nhớ **một số đường hợp lệ**, không phải danh sách mọi phép chuyển riêng lẻ. Chú ý `char -> short` và `byte -> char` **không** phải widening hợp lệ. `boolean` đứng ngoài các phép chuyển kiểu số này.

Widening **không nhất thiết bảo toàn toàn bộ chữ số**: ví dụ `long` lớn chuyển sang `double` có thể bị làm tròn vì `double` chỉ có 53 bit độ chính xác phần định trị. Ngược lại, `int -> long` bảo toàn đúng giá trị của mọi `int`.

## 2. Chương trình Java 17+ runnable

Tạo tệp UTF-8 tên **`TypeConversionDemo.java`**:

~~~java
public class TypeConversionDemo {
    public static void main(String[] args) {
        int original = 120;
        long asLong = original;
        double asDouble = original;

        System.out.println("widenIntToLong=" + asLong);
        System.out.println("widenIntToDouble=" + asDouble);

        long huge = 9_007_199_254_740_993L;
        double approximate = huge;
        System.out.println("largeLongPreserved="
                + ((long) approximate == huge));

        int outsideByte = 130;
        byte wrappedByte = (byte) outsideByte;
        System.out.println("byteFrom130=" + wrappedByte);

        System.out.println("positiveToInt=" + (int) 12.9);
        System.out.println("negativeToInt=" + (int) -12.9);
        System.out.println("hugeDoubleToInt=" + (int) 1.0e20);
        System.out.println("nanToInt=" + (int) Double.NaN);

        char letter = 'A';
        int letterCode = letter;
        System.out.println("charToInt=" + letterCode);
        System.out.println("intToChar=" + (char) 66);

        byte literalByte = 100;
        byte left = 10;
        byte right = 20;
        int sum = left + right;
        System.out.println("literalByte=" + literalByte);
        System.out.println("sumTypeInt=" + sum);

        int five = 5;
        int two = 2;
        double integerDivision = five / two;
        double floatingDivision = (double) five / two;
        System.out.println("integerDivision=" + integerDivision);
        System.out.println("floatingDivision=" + floatingDivision);

        Object general = "Java";
        System.out.println("upcastIsString=" + (general instanceof String));

        String specific = (String) general;
        System.out.println("downcast=" + specific);

        try {
            Integer wrong = (Integer) general;
            System.out.println("unexpected=" + wrong);
        } catch (ClassCastException ex) {
            System.out.println("badDowncast=ClassCastException");
        }
    }
}
~~~

Mở PowerShell (Windows) hoặc Bash (Linux/macOS) trong thư mục chứa file; dùng **JDK 17 trở lên**:

~~~bash
java -version
javac -version
javac -encoding UTF-8 TypeConversionDemo.java
java TypeConversionDemo
~~~

**Expected output chính xác** của lệnh `java TypeConversionDemo` (không gồm thông tin phiên bản JDK):

~~~text
widenIntToLong=120
widenIntToDouble=120.0
largeLongPreserved=false
byteFrom130=-126
positiveToInt=12
negativeToInt=-12
hugeDoubleToInt=2147483647
nanToInt=0
charToInt=65
intToChar=B
literalByte=100
sumTypeInt=30
integerDivision=2.0
floatingDivision=2.5
upcastIsString=true
downcast=Java
badDowncast=ClassCastException
~~~

### Giải thích các kết quả quan trọng

1. `int -> long` và `int -> double` là chuyển đổi mở rộng tự động. Giá trị 120 được giữ chính xác; vì biến `asDouble` là `double`, khi in hiển thị `120.0`.
2. `long huge = 2^53 + 1` không còn biểu diễn chính xác khi chuyển sang `double`. Giá trị bị làm tròn, nên so sánh sau khi chuyển lại thành `long` là `false`. **Không đồng nhất widening với “luôn chính xác”.**
3. `(byte) 130` thu hẹp xuống 8 bit có dấu: 130 vượt 127, kết quả `-126`. Cast vẫn được Java cho phép, không báo tràn.
4. `(int) 12.9` trả 12 và `(int) -12.9` trả -12: phần lẻ bị cắt **về phía 0**. Với `double` hữu hạn vượt miền `int`, kết quả khi cast được giới hạn vào biên gần nhất (`(int) 1.0e20` thành `Integer.MAX_VALUE`). `(int) Double.NaN` thành 0.
5. `char 'A'` mở rộng sang `int` thành mã số 65; cast `(char) 66` thành ký tự `B`.
6. Hai biến `byte` trong `left + right` được nâng thành `int`. Gán kết quả vào `int sum` là hợp lệ; gán trực tiếp vào một biến `byte` không được compiler cho phép nếu kết quả không phải biểu thức hằng phù hợp.
7. `five / two` diễn ra giữa hai `int`, cho 2 **trước khi** gán sang `double` thành `2.0`. Ép `five` sang `double` **trước phép chia** tạo `2.5`.
8. `Object general = "Java"` là phép mở rộng tham chiếu (*upcast*) được phép. Cast xuống `String` hợp lệ khi đối tượng thật là String. Cast xuống `Integer` vẫn có thể biên dịch từ `Object` nhưng thất bại **lúc chạy**, ném `ClassCastException`.

## 3. Hằng int trong ngữ cảnh gán

Java cho phép gán biểu thức hằng kiểu `int` nằm trong miền `byte`, `short` hoặc `char` mà không viết cast. Ngoại lệ này chỉ áp dụng ở những ngữ cảnh được đặc tả; không suy rộng thành chuyển đổi ngầm định cho biến `int` thông thường.

~~~java
byte threshold = 100;  // Hằng int vừa miền byte.
int runtime = 100;
// byte wrong = runtime; // Không biên dịch.
byte checked = (byte) runtime;
~~~

Việc biểu thức có phải hằng tại compile time khác với việc biến tình cờ chứa giá trị nhỏ lúc chạy. Bài chuyên sâu về gán hằng sẽ phân biệt thêm `final` constant variable và phạm vi áp dụng.
## 4. Cast đúng thời điểm trong biểu thức

Kiểu toán hạng quyết định kiểu phép toán **trước** khi kết quả được gán. Vì vậy `long wrong = (long) (50_000 * 50_000);` vẫn nhận tích `int` đã tràn, còn `long right = (long) 50_000 * 50_000;` thực hiện nhân ở `long`, nhận `2500000000`. Cast sau phép tính không khôi phục được bit đã mất. Với phép chia, `(double) 5 / 2` cho `2.5`, nhưng `(double) (5 / 2)` chỉ cho `2.0`.

## 5. Cast tham chiếu: kiểm tra đối tượng thực

`Object o = "Java";` có thể được downcast về `String` vì đối tượng thực là chuỗi. Nếu cùng tham chiếu ấy được cast sang `Integer`, chương trình ném `ClassCastException`. Cast **không tạo đối tượng mới** và không phải chuyển đổi nội dung thành chuỗi.

~~~java
Object o = "Java";
if (o instanceof String text) {
    System.out.println(text.length()); // 4
}
~~~

Pattern matching `instanceof` có trong Java 17; biểu thức `null instanceof String` là `false`, còn cast một tham chiếu `null` hợp lệ thì vẫn ra `null`.

## 6. Những cơ chế khác với numeric cast

`Integer boxed = 42; int raw = boxed;` lần lượt dùng boxing và unboxing; nếu wrapper là `null`, unboxing ném `NullPointerException`. `Integer.parseInt("42")` phân tích chuỗi, không phải cast tham chiếu. Java cũng không hỗ trợ `(int) true`; nếu cần mã số từ boolean phải viết chính sách riêng như `flag ? 1 : 0`.
## 7. Bài tập tự kiểm tra

1. **Ngầm định hay tường minh?** `long x = 17;` và `byte y = (byte) 130;` thuộc loại nào?  
   *Đáp án:* Câu đầu widening implicit, câu sau narrowing explicit và `y = -126`.
2. **Khi nào mất chính xác?** `double d = 9_007_199_254_740_993L;` có giữ đúng mọi đơn vị không?  
   *Đáp án:* Không; `double` không thể biểu diễn chính xác số nguyên `2^53 + 1`.
3. **Kiểu trung gian:** Với `int a = 50_000, b = 50_000;`, cách nào đúng cho tích `long`: `(long)(a*b)` hay `(long)a*b`?  
   *Đáp án:* `(long) a * b`, vì cast xảy ra trước phép nhân.
4. **Phép chia:** `double z = 7 / 2;` và `double w = (double) 7 / 2;` lần lượt bằng gì?  
   *Đáp án:* `3.0` và `3.5`.
5. **Reference cast:** `Object o = "Hi"; String a = (String) o;` có hợp lệ không? Còn `Integer b = (Integer) o;`?  
   *Đáp án:* Cast về String thành công; cast về Integer ném `ClassCastException` khi chạy.

## Checklist trước khi chuyển kiểu

- [ ] Phân biệt **implicit widening** và **explicit narrowing**.
- [ ] Kiểm tra **miền giá trị và độ chính xác**, không chỉ kích thước kiểu.
- [ ] Xác định kiểu **từng biểu thức trung gian** trước khi cast/gán kết quả.
- [ ] Biết `byte`/`short`/`char` thường được nâng lên `int` trong phép số học.
- [ ] Biết cast số thực sang số nguyên cắt về 0 và xử lý các giá trị ngoài miền theo quy tắc Java.
- [ ] Phân biệt reference cast với tạo đối tượng, parsing và boxing.
- [ ] Không mặc định downcast luôn an toàn; cân nhắc `instanceof`.

**Kết luận:** Java thực hiện nhiều chuyển kiểu **ngầm định** khi ngữ cảnh cho phép, nhưng ép kiểu `(TargetType)` là **yêu cầu chuyển đổi**, không phải lời cam kết giữ nguyên giá trị hoặc thành công ở runtime. Hãy xem xét miền giá trị, loại biểu thức và kiểu động của đối tượng trước khi chọn cách chuyển.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-conversion-002">Mở rộng và thu hẹp kiểu nguyên thủy</a>

<details>
<summary>Xem chi tiết</summary>

# Mở rộng và thu hẹp kiểu nguyên thủy trong Java 17+

## 1. Mục tiêu: chuyển đổi kiểu là gì?

**Chuyển đổi kiểu nguyên thủy** (*primitive type conversion*) là quá trình Java chuyển một giá trị giữa các kiểu primitive tương thích, ví dụ từ `byte` sang `int` hoặc từ `double` sang `int`.

~~~java
int count = 120;
long widened = count;       // Mở rộng: int -> long
byte narrowed = (byte) count; // Thu hẹp tường minh: int -> byte

System.out.println(widened);  // 120
System.out.println(narrowed); // 120
~~~

Có hai nhóm quan trọng:

- **Widening primitive conversion (chuyển đổi mở rộng)**: đi theo quan hệ cho phép Java chuyển tự động trong ngữ cảnh phù hợp, thường là từ kiểu có phạm vi đại diện rộng hơn. **Không đồng nghĩa luôn giữ độ chính xác số học**.
- **Narrowing primitive conversion (chuyển đổi thu hẹp)**: đi từ kiểu nguồn sang kiểu đích có thể không chứa trọn giá trị nguồn; thường cần **ép kiểu tường minh** `(TargetType)`, và có thể mất dữ liệu.

Java kiểm tra tính hợp lệ **khi biên dịch** đối với kiểu dữ liệu; nhưng phép cast hợp lệ không tự bảo đảm giá trị sau khi chuyển còn đúng nghiệp vụ.

## 2. Nhớ các kiểu primitive trước khi chuyển đổi

| Kiểu | Độ rộng | Đặc điểm |
|---|---:|---|
| `byte` | 8 bit | Số nguyên có dấu, -128 đến 127 |
| `short` | 16 bit | Số nguyên có dấu, -32768 đến 32767 |
| `char` | 16 bit | UTF-16 code unit không dấu, 0 đến 65535 |
| `int` | 32 bit | Số nguyên có dấu |
| `long` | 64 bit | Số nguyên có dấu |
| `float` | 32 bit | Số thực dấu phẩy động nhị phân, khoảng 24 bit có nghĩa |
| `double` | 64 bit | Số thực dấu phẩy động nhị phân, khoảng 53 bit có nghĩa |
| `boolean` | Theo ngữ nghĩa true/false | **Không có** chuyển đổi số học sang/từ các kiểu còn lại |

**Lưu ý:** `char` và `short` đều 16 bit nhưng không có cùng miền giá trị. `char` không âm; `short` có dấu. Vì thế Java không cho phép chuyển mở rộng trực tiếp từ `char` sang `short` hoặc ngược lại.

`String` không phải kiểu nguyên thủy. Các thao tác `Integer.parseInt("123")`, `String.valueOf(123)` là **chuyển đổi văn bản và giá trị**, không phải một widening/narrowing primitive conversion.

## 3. Các đường chuyển đổi mở rộng hợp lệ

Trong Java, những quan hệ mở rộng primitive có thể ghi nhớ bằng bảng sau:

| Kiểu nguồn | Kiểu đích mở rộng trực tiếp hợp lệ |
|---|---|
| `byte` | `short`, `int`, `long`, `float`, `double` |
| `short` | `int`, `long`, `float`, `double` |
| `char` | `int`, `long`, `float`, `double` |
| `int` | `long`, `float`, `double` |
| `long` | `float`, `double` |
| `float` | `double` |

Không có widening từ `double` sang kiểu nào khác; và `boolean` không tham gia bảng này.

~~~java
byte small = 100;
short medium = small; // byte -> short
int count = medium;   // short -> int
long total = count;   // int -> long
double reading = total; // long -> double

char letter = 'A';
int codeUnit = letter; // char -> int: 65

System.out.println(reading);  // 100.0
System.out.println(codeUnit); // 65
~~~

Ví dụ `char` là nhánh mở rộng riêng, không nằm trong chuỗi `byte -> short -> int -> long`.

**Không cần viết cast** ở các phép gán widening trên. Viết `(long) count` cũng có thể hợp lệ, nhưng thường không cần thiết trừ khi muốn làm rõ kiểu trước một phép toán.

## 4. “Mở rộng” không bảo đảm chính xác tuyệt đối

Đây là một điểm dễ hiểu sai. Các chuyển đổi mở rộng **giữa kiểu nguyên và kiểu nguyên** trong bảng trên giữ giá trị chính xác. Tuy nhiên, khi mở rộng sang kiểu dấu phẩy động, một số giá trị lớn **không thể biểu diễn chính xác bằng significand hữu hạn**.

Ví dụ một `long` có giá trị \(2^{53}+1\):

~~~java
long exact = 9_007_199_254_740_993L;
double approximate = exact; // Widening long -> double

System.out.println(exact);               // 9007199254740993
System.out.println(approximate);         // 9.007199254740992E15
System.out.println((long) approximate);  // 9007199254740992
~~~

`long` giữ chính xác số nguyên trên, nhưng `double` chỉ có 53 bit significand hiệu dụng: giá trị được làm tròn thành \(2^{53}\), mất đơn vị cuối.

Tương tự:

~~~java
float big = 16_777_216f; // 2^24
System.out.println(big + 1f == big); // true
~~~

Ở độ lớn này, `float` không còn phân biệt được mọi bước tăng 1.

Một ví dụ khác là `float -> double`:

~~~java
float f = 0.1f;
double d = f;
System.out.println(d); // 0.10000000149011612
~~~

Chuyển `float` sang `double` **giữ chính xác giá trị nhị phân đang có trong float**, không khôi phục số thập phân `0.1` ban đầu.

**Quy tắc ứng dụng:** khi chuyển số nguyên lớn sang `float`/`double`, hãy xem xét độ chính xác yêu cầu. Nếu mọi đơn vị đếm cần được bảo toàn, giữ `long`/`int` hoặc chọn biểu diễn chính xác hơn.

## 5. Thu hẹp kiểu nguyên: cast và mất dữ liệu

Khi muốn chuyển từ một kiểu nguyên sang một kiểu nguyên nhỏ hơn, thường phải viết cast:

~~~java
int value = 130;
// byte invalid = value; // LỖI BIÊN DỊCH
byte narrowed = (byte) value;
System.out.println(narrowed); // -126
~~~

Vì `byte` chỉ biểu diễn từ -128 đến 127, nên chuyển 130 sang `byte` giữ **8 bit thấp** của kết quả theo quy tắc narrowing. Mẫu bit `1000_0010` được diễn giải thành -126 theo biểu diễn có dấu.

Tương tự:

~~~java
int value = 65_537;
char c = (char) value;
System.out.println((int) c); // 1
~~~

`char` 16 bit nhận 16 bit thấp; 65537 tương đương \(65536+1\), nên code unit sau cast có giá trị 1.

| Chuyển đổi | Input | Kết quả |
|---|---:|---:|
| `(byte) 127` | 127 | 127 |
| `(byte) 128` | 128 | -128 |
| `(byte) 130` | 130 | -126 |
| `(short) 65535` | 65535 | -1 |
| `(char) -1` | -1 | 65535 |

**Cast không tự cảnh báo tràn.** Nó chỉ yêu cầu Java thực hiện một phép chuyển kiểu hợp lệ về cú pháp. Nếu không cho phép thay đổi giá trị số học, hãy kiểm tra giới hạn trước khi cast.

## 6. Ngoại lệ: gán hằng int vừa phạm vi cho byte, short, char

Phép gán `int -> byte`, `short` hoặc `char` thường là narrowing. Tuy nhiên Java có **narrowing conversion dành riêng cho biểu thức hằng tại thời điểm biên dịch** (*constant expressions*) khi giá trị biểu diễn được trong kiểu đích.

~~~java
byte b = 127;         // Hợp lệ dù literal 127 có kiểu int
short s = 32_000;     // Hợp lệ
char c = 65;          // Hợp lệ: mã UTF-16 65
// byte wrong = 128;  // LỖI: ngoài miền byte
// char wrong2 = -1;  // LỖI: ngoài miền char
~~~

Một biến `int` bình thường **không được chuyển tự động**, kể cả khi hiện đang chứa số 127:

~~~java
int value = 127;
// byte b = value;    // Lỗi compile: int không tự thu hẹp
byte b = (byte) value; // Hợp lệ nhưng cần tự chịu trách nhiệm
~~~

Một **final constant variable** kiểu `int` được gán bằng constant expression cũng có thể được compiler xét trong ngữ cảnh gán:

~~~java
final int LIMIT = 100;
byte threshold = LIMIT; // Hợp lệ vì LIMIT là constant variable
~~~

Nhưng `final int runtime = Integer.parseInt("100");` không phải constant variable theo nghĩa này; giá trị xuất phát từ lời gọi method, nên không dùng được phép gán narrowing đặc biệt.

**Giới hạn quy tắc:** ngoại lệ trên thuộc ngữ cảnh assignment (cũng bao gồm initializer), không biến mọi lời gọi method hay biểu thức đều được phép implicit narrowing. Tránh suy từ `byte b = 127;` rằng mọi nơi đều có thể truyền một `int` bất kỳ vào tham số `byte` không cần cast.

## 7. Thu hẹp từ float/double sang số nguyên

Chuyển `float` hoặc `double` sang kiểu nguyên yêu cầu cast trong các trường hợp thông thường:

~~~java
double positive = 12.9;
double negative = -12.9;

System.out.println((int) positive); // 12
System.out.println((int) negative); // -12
~~~

Java **cắt phần thập phân về 0**, không dùng quy tắc làm tròn tới số nguyên gần nhất như `Math.round`.

Cần nhớ các trường hợp đặc biệt theo đặc tả:

| Đầu vào double | `(int)` | Giải thích |
|---|---:|---|
| `12.9` | 12 | Cắt phần thập phân |
| `-12.9` | -12 | Hướng về 0 |
| `Double.NaN` | 0 | Quy tắc NaN khi chuyển về số nguyên |
| `Double.POSITIVE_INFINITY` | 2147483647 | Bão hòa tại `Integer.MAX_VALUE` |
| `Double.NEGATIVE_INFINITY` | -2147483648 | Bão hòa tại `Integer.MIN_VALUE` |
| `1.0e30` | 2147483647 | Bão hòa khi vượt miền int |

Nếu đích là `byte` hoặc `short`, phép thu hẹp từ dấu phẩy động trước hết chuyển qua `int` rồi thu hẹp tiếp, nên có thể cho kết quả càng khó đoán nếu bạn không kiểm tra đầu vào.

Không dùng `(int) doubleValue` làm “validation”: NaN, Infinity hoặc giá trị ngoài miền **không nhất thiết ném exception**. Nếu cần dữ liệu hữu hạn, số nguyên và nằm trong khoảng xác định, phải kiểm tra từng điều kiện trước phép cast.

## 8. Thu hẹp double -> float: mất độ chính xác, overflow, underflow

~~~java
double source = 0.1;
float narrowed = (float) source;

System.out.println(narrowed);        // 0.1
System.out.println((double) narrowed); // 0.10000000149011612
~~~

Chuỗi hiển thị `0.1` là cách Java in một giá trị `float` gần `0.1`, không có nghĩa `float` lưu chính xác 1/10.

Giá trị hữu hạn `double` quá lớn khi chuyển sang `float` có thể thành **Infinity**:

~~~java
double huge = 1.0e40;
float overflow = (float) huge;
System.out.println(overflow); // Infinity
~~~

Giá trị có độ lớn quá nhỏ có thể chuyển thành zero (có dấu) hoặc một giá trị subnormal. Khi yêu cầu kết quả `float` hữu hạn và khác 0, cần validation phù hợp.

**Với float/double:** đừng đồng nhất “độ rộng ít bit hơn” với quy tắc giữ các bit thấp như chuyển `int -> byte`. Dấu phẩy động thu hẹp dùng quy tắc làm tròn/giới hạn của mô hình dấu phẩy động.

## 9. Chuyển kiểu trong biểu thức số học

Java thực hiện **numeric promotion** đối với nhiều toán tử số học. Đặc biệt, `byte`, `short` và `char` được nâng lên `int` trong phép cộng thông thường:

~~~java
byte a = 100;
byte b = 20;

// byte sum = a + b; // LỖI: a + b có kiểu int
int sum = a + b;
System.out.println(sum); // 120

char ch = 'A';
// char next = ch + 1; // LỖI: ch + 1 là int
char next = (char) (ch + 1);
System.out.println(next); // B
~~~

Với toán hạng `long`, `float` hoặc `double`, biểu thức được nâng kiểu theo quy tắc tương ứng trước khi tính.

Một bẫy quan trọng là **tràn xảy ra trước khi gán sang long**:

~~~java
int x = Integer.MAX_VALUE;
long wrong = x + 1;        // int + int tràn trước khi gán long
long correct = (long) x + 1; // Một toán hạng đã là long

System.out.println(wrong);   // -2147483648
System.out.println(correct); // 2147483648
~~~

Chỉ khai báo biến **đích** kiểu `long` không thay đổi phép cộng đã diễn ra với toán hạng `int`.

### Phép gán kết hợp có cast ngầm

~~~java
byte count = 120;
count += 10;
System.out.println(count); // -126
~~~

`count += 10` có thể biên dịch bởi ngữ nghĩa compound assignment bao gồm chuyển kiểu về kiểu của biến nhận. Nhưng:

~~~java
byte count = 120;
// count = count + 10; // LỖI: count + 10 có kiểu int
~~~

Hai cách nhìn gần giống nhau nhưng **khác kết quả compile**. Đừng dùng `+=` như cách lách kiểm tra tràn: `count += 10` vẫn mất dữ liệu số học.

## 10. Kiểm tra an toàn trước khi narrowing

Có nhiều cách tùy miền kiểu.

### Trường hợp long -> int

Java cung cấp `Math.toIntExact(long)`, trả `int` nếu vừa miền; nếu vượt miền, nó ném `ArithmeticException`:

~~~java
long big = 2_147_483_648L;

try {
    int safe = Math.toIntExact(big);
    System.out.println(safe);
} catch (ArithmeticException ex) {
    System.out.println("outside int range");
}
~~~

Đầu ra: `outside int range`.

### Trường hợp long -> byte

Không có `Math.toByteExact(long)` tương ứng trong Java 17; bạn có thể kiểm tra:

~~~java
static byte checkedByte(long value) {
    if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
        throw new IllegalArgumentException("outside byte range");
    }
    return (byte) value;
}
~~~

### Trường hợp double -> int cần đúng số nguyên

~~~java
static int checkedWholeInt(double value) {
    if (!Double.isFinite(value)
            || value < Integer.MIN_VALUE
            || value >= 2147483648.0
            || value != Math.rint(value)) {
        throw new IllegalArgumentException("not a finite int");
    }
    return (int) value;
}
~~~

Ở đây chúng ta kiểm tra **hữu hạn**, **nằm trong miền int**, và **không có phần lẻ**. Cận trên dùng `2147483648.0` (2³¹) và điều kiện `>=` để tránh bẫy biểu diễn khi so sánh số thực với `Integer.MAX_VALUE`.

Nếu cần hỗ trợ chính xác theo decimal, quy tắc làm tròn cụ thể hoặc miền số lớn, hãy chọn kiểu/thuật toán phù hợp thay vì cast trực tiếp.

## 11. `boolean` không có chuyển đổi kiểu số học

Java **không cho** chuyển `boolean` thành `int` hoặc ngược lại bằng widening/narrowing:

~~~java
boolean enabled = true;
// int number = (int) enabled; // LỖI BIÊN DỊCH

int number = 1;
// boolean flag = (boolean) number; // LỖI BIÊN DỊCH
~~~

Nếu nghiệp vụ quy định `true` tương ứng với `1`, bạn phải diễn đạt quy tắc đó rõ ràng:

~~~java
boolean enabled = true;
int numericFlag = enabled ? 1 : 0;
System.out.println(numericFlag); // 1
~~~

Đây là biểu thức điều kiện sinh số nguyên theo quy tắc bạn đặt ra, **không phải** một primitive conversion từ boolean.

## 12. Ví dụ Java 17 hoàn chỉnh, runnable

Tạo đúng tệp **`PrimitiveConversionDemo.java`**:

~~~java
public final class PrimitiveConversionDemo {
    private PrimitiveConversionDemo() {}

    static byte checkedByte(long value) {
        if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
            throw new IllegalArgumentException("outside byte range");
        }
        return (byte) value;
    }

    static int checkedWholeInt(double value) {
        if (!Double.isFinite(value)
                || value < Integer.MIN_VALUE
                || value >= 2147483648.0
                || value != Math.rint(value)) {
            throw new IllegalArgumentException("not a finite int");
        }
        return (int) value;
    }

    public static void main(String[] args) {
        byte small = 100;
        short asShort = small;
        int asInt = small;
        long asLong = asInt;
        double asDouble = asLong;
        System.out.println("widen=" + asShort + "," + asInt
                + "," + asLong + "," + asDouble);

        char letter = 'A';
        int codeUnit = letter;
        System.out.println("char code=" + codeUnit);

        int input = 130;
        byte narrowed = (byte) input;
        System.out.println("int to byte=" + narrowed);
        System.out.println("int to char=" + (int) (char) -1);

        final int limit = 100;
        byte fromConstant = limit;
        System.out.println("constant=" + fromConstant);

        long bigInteger = 9_007_199_254_740_993L;
        double approximate = bigInteger;
        System.out.println("long original=" + bigInteger);
        System.out.println("long through double=" + (long) approximate);

        float decimalFloat = 0.1f;
        double widenedFloat = decimalFloat;
        System.out.println("float as double=" + widenedFloat);

        System.out.println("positive trunc=" + (int) 12.9);
        System.out.println("negative trunc=" + (int) -12.9);
        System.out.println("NaN to int=" + (int) Double.NaN);
        System.out.println("infinity to int="
                + (int) Double.POSITIVE_INFINITY);
        System.out.println("double to float=" + (float) 1.0e40);

        byte a = 100;
        byte b = 20;
        int sum = a + b;
        System.out.println("byte addition=" + sum);

        byte count = 120;
        count += 10;
        System.out.println("compound assignment=" + count);

        int max = Integer.MAX_VALUE;
        long wrong = max + 1;
        long right = (long) max + 1;
        System.out.println("int overflow first=" + wrong);
        System.out.println("long arithmetic=" + right);

        System.out.println("checked byte=" + checkedByte(127));
        try {
            checkedByte(128);
        } catch (IllegalArgumentException ex) {
            System.out.println("checked byte error=" + ex.getMessage());
        }

        System.out.println("checked whole int=" + checkedWholeInt(42.0));
        try {
            checkedWholeInt(42.5);
        } catch (IllegalArgumentException ex) {
            System.out.println("checked whole error=" + ex.getMessage());
        }

        try {
            Math.toIntExact(2_147_483_648L);
        } catch (ArithmeticException ex) {
            System.out.println("toIntExact=overflow");
        }
    }
}
~~~

Yêu cầu: cài **JDK 17 trở lên**, terminal đang đứng tại thư mục chứa tệp.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 PrimitiveConversionDemo.java
java -cp . PrimitiveConversionDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\PrimitiveConversionDemo.java
java -cp . PrimitiveConversionDemo
~~~

**Expected output:**

~~~text
widen=100,100,100,100.0
char code=65
int to byte=-126
int to char=65535
constant=100
long original=9007199254740993
long through double=9007199254740992
float as double=0.10000000149011612
positive trunc=12
negative trunc=-12
NaN to int=0
infinity to int=2147483647
double to float=Infinity
byte addition=120
compound assignment=-126
int overflow first=-2147483648
long arithmetic=2147483648
checked byte=127
checked byte error=outside byte range
checked whole int=42
checked whole error=not a finite int
toIntExact=overflow
~~~

**Đọc kết quả theo nhóm:**

1. `widen` và `char code` cho thấy mở rộng giữ chính xác những giá trị nhỏ.
2. `int to byte`, `int to char` cho thấy thu hẹp kiểu nguyên có thể đổi giá trị.
3. `constant` minh họa ngoại lệ gán constant variable kiểu int vừa miền byte.
4. `long through double` và `float as double` chứng minh widening không phải lúc nào cũng khôi phục độ chính xác mong muốn.
5. `positive/negative trunc`, `NaN`, `infinity` cho thấy casting dấu phẩy động về số nguyên có quy tắc đặc biệt.
6. `compound assignment` và `int overflow first` nhắc rằng phép tính có thể đã mất giá trị trước khi gán.
7. Hai hàm `checkedByte`/`checkedWholeInt` và `Math.toIntExact` thể hiện cách ngăn mất dữ liệu khi nghiệp vụ không chấp nhận.

Ví dụ không cần thư viện bên ngoài JDK, đầu ra không phụ thuộc hệ điều hành hoặc dữ liệu nhập bàn phím.

## 13. Các lỗi thường gặp

| Tình huống | Nguyên nhân | Cách xử lý |
|---|---|---|
| `byte b = intVariable;` | Narrowing int -> byte không tự động | Kiểm tra miền rồi cast |
| `byte b = 128;` | Constant int không vừa miền byte | Sử dụng kiểu lớn hơn nếu cần 128 |
| `short s = charVariable;` | Không có widening char -> short | Kiểm tra miền rồi cast, hoặc dùng int |
| `char c = shortVariable;` | Short có thể âm, char không có dấu | Kiểm tra trước khi cast |
| `long x = 3_000_000_000;` | Decimal integer literal mặc định int quá giới hạn | `3_000_000_000L` |
| `float f = 0.1;` | Literal 0.1 có kiểu double | `0.1f` hoặc cast có chủ đích |
| `long result = intA + intB;` bị tràn | Phép cộng int xảy ra trước widening | Đổi ít nhất một toán hạng sang long trước |
| `byte s = a + b;` với a,b là byte | Binary numeric promotion thành int | Nhận `int`, hoặc cast có kiểm tra |
| `byte count; count += delta;` mất dữ liệu | Compound assignment thu hẹp ngầm | Tính bằng int, kiểm tra miền rồi gán |
| `(int) 12.9` cho 12 | Cast cắt phần lẻ về 0 | Nếu muốn làm tròn, dùng quy tắc/ API riêng |
| `(int) Double.NaN` cho 0 | Quy tắc chuyển đổi đặc biệt | Validation trước cast |
| `(int) Double.POSITIVE_INFINITY` cho max int | Giá trị bão hòa | Kiểm tra `isFinite`/range |
| `long -> double` mất chữ số cuối | Giới hạn 53 bit significand | Duy trì long nếu cần số nguyên chính xác |
| Cast boolean sang int | Không có primitive conversion như vậy | Dùng biểu thức điều kiện theo nghiệp vụ |

**Phân biệt rõ:** Java có thể chấp nhận một phép cast về cú pháp nhưng tạo kết quả sai yêu cầu. Việc chọn biểu diễn/validation là trách nhiệm của thiết kế chương trình.

## 14. Bài tập có đáp án

### Bài A — mở rộng

~~~java
byte b = -10;
int i = b;
long l = i;
System.out.println(l);
~~~

**Đáp án:** `-10`. Hai bước mở rộng giữa số nguyên giữ đúng giá trị âm.

### Bài B — narrowing

~~~java
int n = 255;
System.out.println((byte) n);
~~~

**Đáp án:** `-1`. Mẫu 8 bit thấp `11111111` là -1 khi diễn giải dưới dạng byte có dấu.

### Bài C — char và short

~~~java
char c = 65;
int code = c;
System.out.println(code);
~~~

**Đáp án:** `65`. `char -> int` là widening; nhưng `char -> short` không phải widening dù cả hai có 16 bit.

### Bài D — float và double

~~~java
float f = 0.1f;
double d = f;
System.out.println(d);
~~~

**Đáp án:** `0.10000000149011612`. Chuyển mở rộng giữ đúng **giá trị xấp xỉ đã lưu** trong `float`.

### Bài E — phép cộng int

~~~java
int max = Integer.MAX_VALUE;
long result = max + 1;
System.out.println(result);
~~~

**Đáp án:** `-2147483648`, vì phép cộng tràn trong `int` trước. Sửa bằng `long result = (long) max + 1;`.

### Bài F — không chấp nhận phần lẻ

Muốn chuyển `double value = 12.5;` thành số nguyên nhưng nghiệp vụ **không cho phép mất phần lẻ**, có nên viết `(int) value`?

**Đáp án:** không. Phép cast cho `12`, làm mất 0.5. Trước khi cast cần kiểm tra dữ liệu hữu hạn, nằm trong miền kiểu nguyên và có giá trị nguyên; nếu không hợp lệ phải từ chối hoặc xử lý theo quy tắc nghiệp vụ.

## 15. Checklist tổng kết

- [ ] Biết phân biệt widening và narrowing primitive conversion.
- [ ] Nhớ `char` không phải `short` có dấu và `boolean` không tham gia chuyển kiểu số học.
- [ ] Biết widening giữa kiểu nguyên giữ chính xác giá trị, còn sang float/double có thể làm tròn.
- [ ] Biết narrowing nguyên bằng cast có thể giữ các bit thấp và đổi giá trị số.
- [ ] Hiểu ngoại lệ gán constant int vừa miền byte/short/char.
- [ ] Biết cast float/double sang int/long thường cắt phần lẻ về 0 và có quy tắc đặc biệt cho NaN/Infinity.
- [ ] Biết double -> float có thể mất độ chính xác, overflow hoặc underflow.
- [ ] Nhớ byte/short/char được nâng thành int trong nhiều phép tính.
- [ ] Biết compound assignment như `+=` có thể thu hẹp kiểu ngầm.
- [ ] Chọn kiểu toán hạng **trước** khi phép cộng có thể bị overflow.
- [ ] Kiểm tra miền trước khi cast, dùng `Math.toIntExact` khi thích hợp.
- [ ] Chạy `PrimitiveConversionDemo.java` bằng JDK 17+ và đối chiếu từng dòng output.

**Kết luận:** **Chuyển kiểu mở rộng** thường được Java thực hiện tự động, nhưng có trường hợp mất độ chính xác khi sang floating-point. **Chuyển kiểu thu hẹp** thường cần cast và có thể thay đổi giá trị mà không ném exception. Phân biệt kiểu của toán hạng, giới hạn biểu diễn và yêu cầu nghiệp vụ là cách viết chuyển đổi primitive an toàn trong Java 17+.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-conversion-003">Mở rộng số nguyên sang số thực vẫn có thể mất độ chính xác</a>

<details>
<summary>Xem chi tiết</summary>

# Mở rộng số nguyên sang số thực vẫn có thể mất độ chính xác

> **Java core — language-basics / Chuyển đổi kiểu**  
> **Môi trường:** JDK 17+; chỉ dùng thư viện chuẩn.  
> **Mục tiêu:** Hiểu tại sao `int → float` và `long → double` là các phép chuyển kiểu mở rộng hợp lệ nhưng có thể làm tròn mất dữ liệu. Biết ngưỡng chính xác 24/53 bit, nguy cơ so sánh hỗn hợp và cách xử lý giá trị nguyên cần giữ tuyệt đối.

## 1. Mở rộng kiểu không bảo đảm giữ nguyên giá trị

Java cho phép chuyển số nguyên sang số thực mà không cần ép kiểu:

~~~java
int count = 123;
float f = count;   // Hợp lệ.
double d = count;  // Hợp lệ.

long id = 9_007_199_254_740_993L;
double approximateId = id; // Hợp lệ, nhưng có thể mất chính xác.
~~~

`int` và `long` biểu diễn chính xác từng số nguyên trong miền có dấu của chúng. `float` và `double` biểu diễn số thực dạng nhị phân IEEE 754. Chúng có miền độ lớn rất rộng nhưng phần trị chỉ có số bit chính xác hữu hạn:

- **`float`:** 24 bit chính xác phần trị, tính cả bit dẫn đầu của số chuẩn hóa.
- **`double`:** 53 bit chính xác phần trị.
- **`int`:** 32 bit có dấu; **`long`:** 64 bit có dấu.

Khi số nguyên không biểu diễn chính xác được bằng kiểu số thực đích, Java làm tròn sang một giá trị số thực có thể biểu diễn; với trường hợp chính giữa hai giá trị, phép chuyển đổi chọn giá trị có bit cuối phần trị chẵn (*round to nearest, ties to even*).

**Chuyển kiểu widening** nghĩa là chuyển đổi được ngôn ngữ cho phép mà không cần cast. Nó **không** có nghĩa luôn giữ được mọi chữ số của số nguyên.

## 2. Chuyển đổi nào luôn chính xác?

| Phép chuyển đổi | Widening tự động? | Chính xác với **mọi** giá trị nguồn? |
| --- | --- | --- |
| `byte` → `float`/`double` | Có | Có |
| `short` → `float`/`double` | Có | Có |
| `char` → `float`/`double` | Có | Có (giá trị code unit 0–65535) |
| `int` → `double` | Có | **Có** |
| `int` → `float` | Có | **Không** |
| `long` → `double` | Có | **Không** |
| `long` → `float` | Có | **Không** |

Giới hạn quan trọng:

- `float` biểu diễn được **mọi** số nguyên từ `-2^24` đến `2^24` (±16.777.216).
- `double` biểu diễn được **mọi** số nguyên từ `-2^53` đến `2^53` (±9.007.199.254.740.992).
- Trên các ngưỡng này, **một số** số nguyên vẫn biểu diễn chính xác (ví dụ số chẵn ở ngay trên `2^24` có thể vừa `float`), nhưng **không phải tất cả số nguyên liên tiếp**.

Bởi vậy toàn bộ giá trị `int` chuyển sang `double` vẫn chính xác, trong khi `int` chuyển sang `float` hoặc `long` chuyển sang `double` có nguy cơ làm tròn.

## 3. Ranh giới `2^24 + 1` khi đổi `int` sang `float`

~~~java
int first = 16_777_216;       // 2^24
int second = 16_777_217;      // 2^24 + 1
float a = first;
float b = second;
~~~

`a` bằng 16.777.216 chính xác. Ở độ lớn này, khoảng cách giữa các giá trị `float` liên tiếp là **2**, nên số 16.777.217 nằm giữa 16.777.216 và 16.777.218. Quy tắc ties-to-even làm tròn `b` thành **16.777.216**.

Hệ quả:

~~~java
System.out.println(first == second); // false
System.out.println(a == b);          // true
~~~

`Math.ulp(a)` cho khoảng cách giữa `a` và giá trị `float` kế tiếp tại độ lớn này: **2.0f**. Nếu cần lưu đúng bất kỳ giá trị `int`, nên giữ `int` hoặc chuyển thành `double`, thay vì ép thành `float`.

## 4. Ranh giới `2^53 + 1` khi đổi `long` sang `double`

~~~java
long first = 9_007_199_254_740_992L; // 2^53
long second = first + 1;              // 2^53 + 1
double a = first;
double b = second;
~~~

`a` và `b` trở thành **cùng một giá trị `double`** là 9.007.199.254.740.992, dù hai số nguyên nguồn khác nhau. Chú ý phép so sánh hỗn hợp:

~~~java
System.out.println(second == b);       // true - dễ nhầm!
System.out.println(second == (long) b); // false
~~~

Trong `second == b`, Java **nâng kiểu số** (*binary numeric promotion*): chuyển số `long second` sang `double` trước khi so sánh. Khi hai vế cùng bị làm tròn, phép `==` cho true **không chứng minh dữ liệu được giữ chính xác**.

Ép `b` trở lại `long` trong ví dụ này cho 9.007.199.254.740.992 và giúp thấy sai lệch 1. Tuy nhiên, cast số thực sang số nguyên còn có quy tắc bỏ phần lẻ và giới hạn giá trị; nó **không phải phương pháp tổng quát** để kiểm tra chuyển đổi có chính xác hay không.

## 5. Ví dụ Java 17+ chạy được

Tạo tệp **IntegerFloatPrecisionDemo.java**:

~~~java
import java.math.BigDecimal;

public final class IntegerFloatPrecisionDemo {
    public static void main(String[] args) {
        int baseInt = 16_777_216;
        int nextInt = baseInt + 1;

        float fa = baseInt;
        float fb = nextInt;
        double preciseInt = nextInt;

        System.out.println("intNext=" + nextInt);
        System.out.println("floatAsInt=" + (int) fb);
        System.out.println("doubleIntAsInt=" + (int) preciseInt);
        System.out.println("floatEqual=" + (fa == fb));
        System.out.println("floatUlp=" + (int) Math.ulp(fa));

        long baseLong = 9_007_199_254_740_992L;
        long nextLong = baseLong + 1;
        double da = baseLong;
        double db = nextLong;

        System.out.println("longNext=" + nextLong);
        System.out.println("doubleAsLong=" + (long) db);
        System.out.println("doubleEqual=" + (da == db));
        System.out.println("mixedEqual=" + (nextLong == db));
        System.out.println("roundTripEqual=" + (nextLong == (long) db));
        System.out.println("doubleUlp=" + (int) Math.ulp(da));

        System.out.println("allIntToDoubleExact="
                + ((long) (double) Integer.MAX_VALUE
                        == Integer.MAX_VALUE));
        System.out.println("exactDecimal="
                + BigDecimal.valueOf(nextLong).toPlainString());
    }
}
~~~

Mở terminal tại thư mục chứa file (Bash, PowerShell hoặc Command Prompt) và chạy:

~~~bash
javac -encoding UTF-8 IntegerFloatPrecisionDemo.java
java IntegerFloatPrecisionDemo
~~~

**Expected output chính xác:**

~~~text
intNext=16777217
floatAsInt=16777216
doubleIntAsInt=16777217
floatEqual=true
floatUlp=2
longNext=9007199254740993
doubleAsLong=9007199254740992
doubleEqual=true
mixedEqual=true
roundTripEqual=false
doubleUlp=2
allIntToDoubleExact=true
exactDecimal=9007199254740993
~~~

Các dòng `floatAsInt` và `doubleAsLong` cho thấy **giá trị sau khi làm tròn**, chứ không phải kiểu mới tự lưu thêm số. Hai kết quả `floatUlp=2` và `doubleUlp=2` thể hiện bước nhảy giữa các giá trị số thực tại các ngưỡng đang xét (đã cast sang `int` để dễ đọc). `BigDecimal.valueOf(nextLong)` lấy **trực tiếp từ số nguyên gốc**, giữ nguyên số 9.007.199.254.740.993.

## 6. Cách chọn kiểu khi cần chính xác

| Nhu cầu | Khuyến nghị |
| --- | --- |
| ID, số lượng, timestamp nguyên, tiền lưu bằng đơn vị nguyên | Dùng `int`/`long` nếu miền giá trị phù hợp |
| Chuyển toàn bộ miền `int` sang số thực mà chưa tính tiếp | `double` giữ chính xác mọi giá trị `int` |
| `long` lớn và **không được sai một đơn vị** | Tránh chuyển sang `double` để lưu hoặc so sánh |
| Số nguyên cần vượt miền `long` | Dùng `BigInteger` |
| Thập phân cần chính xác theo quy tắc nghiệp vụ | Dùng `BigDecimal` từ `String` hoặc số nguyên gốc, với scale/rounding rõ ràng |
| Đo lường hay đồ họa chấp nhận xấp xỉ | `float`/`double` nếu sai số chấp nhận được |

**Không có cách “lấy lại” các bit đã mất** chỉ bằng `Math.round`, cast ngược hoặc tạo `BigDecimal` từ giá trị `double` đã làm tròn. Nếu đòi hỏi số nguyên chính xác, giữ số nguyên gốc xuyên suốt luồng dữ liệu và các phép so sánh.

`int → double` đúng tuyệt đối **ở bước chuyển đổi**, nhưng các phép tính `double` về sau vẫn có thể sinh sai số làm tròn, `NaN` hoặc vô cực. Độ chính xác của phép gán không bảo đảm độ chính xác của toàn bộ thuật toán.

## 7. Bài tập kiểm tra

**Bài 1.** Vì sao `(float) 16_777_216 == (float) 16_777_217` cho true, dù hai literal số nguyên khác nhau?

**Bài 2.** Nêu vì sao `int → double` chính xác với mọi `int`, nhưng `long → double` thì không. Giải thích vai trò của 53 bit phần trị.

**Bài 3.** Cho `long id = 9_007_199_254_740_993L; double d = id;`. Dự đoán `id == d` và `id == (long) d`; chỉ ra bước numeric promotion.

**Bài 4.** Đổi `BigDecimal.valueOf(id)` thành `BigDecimal.valueOf((double) id)`. So sánh chuỗi số thập phân thu được và giải thích tại sao không thể phục hồi phần đã mất.

**Bài 5.** Thiết kế một ứng dụng lưu ID 64 bit chính xác, rồi xuất dạng văn bản. Đề xuất cách chuyển thẳng `long` thành `String` mà không đi qua `double`.

### Ghi nhớ

**Chuyển số nguyên sang số thực là widening hợp lệ nhưng không luôn lossless.** `float` chỉ có 24 bit chính xác, `double` có 53 bit. Vì vậy `int → float` và `long → double` có thể làm tròn; ngược lại **mọi `int → double` đều chính xác**. Nếu cần lưu đúng từng đơn vị của số nguyên lớn, đừng đưa giá trị qua số thực xấp xỉ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-conversion-004">Thu hẹp số nguyên và phần bit bị loại bỏ</a>

<details>
<summary>Xem chi tiết</summary>

# Thu hẹp số nguyên và phần bit bị loại bỏ — Java 17+

Khi chuyển một giá trị số nguyên sang kiểu nguyên **hẹp hơn** trong Java, kết quả không được bảo đảm giữ nguyên giá trị. Với các phép chuyển `long -> int`, `int/long -> short`, `int/long -> byte` hoặc `int/long -> char`, Java **loại bỏ các bit cao** và giữ các bit thấp phù hợp với độ rộng kiểu đích. Sau đó, giá trị được diễn giải theo quy tắc **có dấu hoặc không dấu** của kiểu đích.

Đây là hành vi được ngôn ngữ quy định, **không phải ngoại lệ tràn số**. Viết cast `(byte)` hoặc `(short)` chỉ yêu cầu chuyển kiểu; nó không kiểm tra giá trị có nằm trong miền đích hay không.

## 1. Nhớ miền giá trị và số bit

| Kiểu | Độ rộng | Diễn giải | Miền |
| --- | ---: | --- | --- |
| `byte` | 8 bit | Số nguyên có dấu, bù hai | −128 đến 127 |
| `short` | 16 bit | Số nguyên có dấu, bù hai | −32.768 đến 32.767 |
| `char` | 16 bit | Đơn vị mã UTF-16 **không dấu** | 0 đến 65.535 |
| `int` | 32 bit | Số nguyên có dấu, bù hai | −2.147.483.648 đến 2.147.483.647 |
| `long` | 64 bit | Số nguyên có dấu, bù hai | −2⁶³ đến 2⁶³−1 |

**Đặc biệt:** `short` và `char` cùng có 16 bit nhưng diễn giải **khác nhau**. Cùng một mẫu bit `0xABCD` sẽ là `-21555` nếu nhìn như `short`, nhưng là `43981` nếu nhìn như `char`.

## 2. Quy tắc giữ bit thấp

Khi thu hẹp số nguyên xuống `n` bit:

1. Lấy biểu diễn nhị phân của giá trị nguồn.
2. **Giữ đúng `n` bit thấp nhất**, bỏ tất cả bit cao hơn.
3. Đọc mẫu bit còn lại theo kiểu đích: `byte`/`short`/`int` có dấu; `char` không dấu.

Ví dụ `int 130` có 16 bit thấp như sau:

~~~text
Giá trị gốc 130:      00000000 10000010
Giữ 8 bit thấp:                10000010
Bỏ 8 bit cao:         xxxxxxxx
                                     
(byte) 130:                    10000010  →  -126
Diễn giải không dấu:           10000010  →   130
~~~

Mẫu bit `10000010` có bit cao nhất bằng 1, nên `byte` diễn giải theo bù hai thành `-126`. Trong khi đó, `Byte.toUnsignedInt((byte) 130)` cho `130` vì diễn giải tám bit đó như số **không dấu**.

Ví dụ thứ hai, `int 0x1234ABCD` chuyển sang 16 bit:

~~~text
int 32 bit:    0x1234 ABCD
Loại bit cao:  0x1234 ----
Giữ bit thấp:  0x---- ABCD

(short) 0x1234ABCD → -21555
(char)  0x1234ABCD →  43981
~~~

## 3. Chương trình Java 17+ chạy được

Tạo tệp UTF-8 tên **`NarrowingBitsDemo.java`**:

~~~java
public class NarrowingBitsDemo {
    private static String bits8(int value) {
        return String.format("%8s",
                Integer.toBinaryString(value & 0xFF))
                .replace(' ', '0');
    }

    private static String bits16(int value) {
        return String.format("%16s",
                Integer.toBinaryString(value & 0xFFFF))
                .replace(' ', '0');
    }

    public static void main(String[] args) {
        int source = 130;
        byte narrowed = (byte) source;

        System.out.println("130.binary16=" + bits16(source));
        System.out.println("130.low8=" + bits8(narrowed));
        System.out.println("130.asByte=" + narrowed);
        System.out.println("130.asUnsignedByte="
                + Byte.toUnsignedInt(narrowed));

        System.out.println("255.asByte=" + (byte) 255);
        System.out.println("256.asByte=" + (byte) 256);
        System.out.println("-129.asByte=" + (byte) -129);

        int hex = 0x1234ABCD;
        System.out.printf("hex.original=0x%08X%n", hex);
        System.out.printf("hex.low16=0x%04X%n", hex & 0xFFFF);
        System.out.println("hex.asShort=" + (short) hex);
        System.out.println("hex.asCharCode=" + (int) (char) hex);

        long large = 0x1_0000_0001L;
        System.out.println("long.asInt=" + (int) large);

        byte counter = 127;
        counter += 1;
        System.out.println("compoundWrap=" + counter);

        try {
            Math.toIntExact(large);
            System.out.println("toIntExact=ok");
        } catch (ArithmeticException ex) {
            System.out.println("toIntExact=overflow");
        }

        boolean fitsByte = source >= Byte.MIN_VALUE
                && source <= Byte.MAX_VALUE;
        System.out.println("fitsByte130=" + fitsByte);
    }
}
~~~

Mở **PowerShell** (Windows) hoặc **Bash** (Linux/macOS) trong thư mục chứa file. Cần JDK 17 hoặc mới hơn:

~~~bash
java -version
javac -version
javac -encoding UTF-8 NarrowingBitsDemo.java
java NarrowingBitsDemo
~~~

**Expected output chính xác của lệnh `java NarrowingBitsDemo`:**

~~~text
130.binary16=0000000010000010
130.low8=10000010
130.asByte=-126
130.asUnsignedByte=130
255.asByte=-1
256.asByte=0
-129.asByte=127
hex.original=0x1234ABCD
hex.low16=0xABCD
hex.asShort=-21555
hex.asCharCode=43981
long.asInt=1
compoundWrap=-128
toIntExact=overflow
fitsByte130=false
~~~

Các lệnh `java -version` và `javac -version` hiển thị thông tin phụ thuộc môi trường; khối trên chỉ mô tả kết quả chương trình.

## 4. Giải thích từng nhóm kết quả

### Từ `int` sang `byte`

`130` viết dưới dạng 16 bit là `0000000010000010`. Ép thành `byte` giữ tám bit thấp `10000010`, được Java diễn giải có dấu thành `-126`.

`Byte.toUnsignedInt(narrowed)` **không đổi lại đối tượng hay bit trong byte**. Phương thức này tạo giá trị `int` biểu diễn chính tám bit của `byte` như số nguyên không dấu từ 0 đến 255.

| Giá trị nguồn | 8 bit thấp | Giá trị `byte` sau cast |
| ---: | --- | ---: |
| `127` | `01111111` | 127 |
| `128` | `10000000` | −128 |
| `130` | `10000010` | −126 |
| `255` | `11111111` | −1 |
| `256` | `00000000` | 0 |
| `-129` | `01111111` | 127 |

Có thể hình dung kết quả không dấu là phần dư theo `2^8 = 256`, sau đó nếu bit dấu bằng 1 thì `byte` diễn giải kết quả trong miền âm. **Không** hiểu phép cast là “chặn giá trị ở −128 hoặc 127”: Java không làm điều đó cho phép thu hẹp số nguyên.

### Từ `int` sang `short` và `char`

Giá trị nguồn `0x1234ABCD` gồm 32 bit. Hai kiểu đích chỉ giữ **16 bit thấp** là `0xABCD`.

- Với `short`, `0xABCD` bằng 43981 nếu xem không dấu, nhưng vì bit dấu 16 bit bằng 1, giá trị có dấu là `43981 - 65536 = -21555`.
- Với `char`, `0xABCD` được đọc **không dấu**, nên giá trị là `43981`. Đây là một UTF-16 code unit, không mặc định là một ký tự hiển thị hoàn chỉnh.

Chỉ việc ép sang `char` không làm giá trị trở thành chuỗi `String`. Để in mã số của `char`, ví dụ dùng `(int) (char) hex` như chương trình.

### Từ `long` sang `int`

`0x1_0000_0001L` tương đương `4294967297` (một lớn hơn `2^32`). Khi ép sang `int`, Java giữ 32 bit thấp `0x00000001`, nên kết quả bằng **1**. Việc mất bit cao này có thể khiến mã định danh, bộ đếm hoặc phép tính sai nghiêm trọng nếu lập trình viên không kiểm tra miền.

## 5. Ép kiểu tường minh không có nghĩa “an toàn”

~~~java
int source = 130;
byte b = (byte) source; // Hop le ve cu phap, nhung b = -126.
~~~

Compiler chấp nhận cast vì người lập trình đã yêu cầu chuyển đổi. Nhưng compiler **không suy ra đây là lựa chọn đúng về nghiệp vụ**.

Cách kiểm tra trước khi ép về `byte`:

~~~java
int value = 130;

if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
    throw new IllegalArgumentException("Out of byte range");
}

byte narrowed = (byte) value;
~~~

Cách tương tự áp dụng cho `short` (`Short.MIN_VALUE`/`Short.MAX_VALUE`), `char` (0 đến `Character.MAX_VALUE`) và các kiểu số nguyên khác. Khi thu hẹp `long` sang `int`, Java 17 hỗ trợ:

~~~java
int exact = Math.toIntExact(42L); // 42.

// Math.toIntExact(0x1_0000_0001L) nem ArithmeticException.
~~~

`Math.toIntExact` kiểm tra **toàn bộ giá trị `long` có nằm trong miền `int` không**, không chỉ lấy các bit thấp. Không có phương thức `Math.toByteExact` chuẩn tương đương; muốn ép `int` về `byte` cần kiểm tra miền như trên hoặc dùng thư viện được lựa chọn rõ ràng.

## 6. Phép gán hằng và phép toán gán kết hợp

Hai quy tắc Java dễ khiến người học nhầm là Java luôn tự động thu hẹp:

**A. Hằng số nguyên nằm trong miền:**

~~~java
byte ok = 100;          // Hop le: hang int 100 nam trong mien byte.
// byte bad = 130;      // Loi bien dich: vuot mien byte.
byte casted = (byte) 130; // Hop le, gia tri -126.
~~~

Compiler cho phép một số biểu thức hằng kiểu `int` được gán cho `byte`/`short`/`char` nếu giá trị nằm trong miền. Nhưng một biến `int` bất kỳ **không** tự được gán sang `byte`:

~~~java
int n = 100;
// byte x = n;      // Loi: can cast.
byte x = (byte) n;  // Hop le.
~~~

**B. Phép gán kết hợp có ép kiểu ngầm:**

~~~java
byte count = 127;
count += 1; // Hop le: ket qua ve byte -> -128.

// count = count + 1; // Loi bien dich: phep cong la int.
~~~

Ở biểu thức số học `count + 1`, `count` được nâng thành `int` và kết quả có kiểu `int`. Với `count += 1`, Java áp dụng phép toán gán kết hợp và chuyển về kiểu bên trái, tương đương về hiệu ứng kiểu với phép cast xuống `byte`. Vì vậy phép tăng này **cũng có thể làm mất bit**.

## 7. Phân biệt hai cơ chế thường bị lẫn

**Thu hẹp số nguyên → số nguyên:** giữ các bit thấp và diễn giải kiểu đích theo quy tắc có dấu/không dấu; ví dụ `(byte) 130 == -126`.

**Ép số thực → số nguyên:** **không** đơn giản lấy bit thấp của biểu diễn số thực. Java áp dụng quy tắc chuyển từ floating-point sang integral, gồm cắt phần thập phân về 0, xử lý `NaN` và giới hạn giá trị khi vượt miền. Ví dụ `(int) 12.9 == 12`.

Bài này tập trung vào **phần bit bị loại bỏ khi thu hẹp giữa các kiểu số nguyên**. Không dùng mô hình “giữ bit thấp” để giải thích mọi phép cast trong Java.

## 8. Bài tập tự kiểm tra

**Bài 1.** Kết quả `(byte) 128` và `(byte) 256` là bao nhiêu?

*Đáp án:* `-128` và `0`, vì tám bit thấp lần lượt là `10000000` và `00000000`.

**Bài 2.** Tại sao `(short) 0x1234ABCD` là `-21555`?

*Đáp án:* Chỉ giữ 16 bit thấp `0xABCD`; diễn giải 16 bit có dấu thành `43981 - 65536 = -21555`.

**Bài 3.** Với cùng nguồn trên, `(int) (char) 0x1234ABCD` bằng bao nhiêu?

*Đáp án:* `43981`, vì `char` giữ 16 bit thấp và không có dấu.

**Bài 4.** `(int) 0x1_0000_0001L` trả giá trị nào? Có ném exception không?

*Đáp án:* Trả **1**, không tự ném ngoại lệ khi cast. `Math.toIntExact` mới báo `ArithmeticException` do vượt miền `int`.

**Bài 5.** `byte b = 127; b += 1;` cho kết quả gì? Tại sao?

*Đáp án:* `-128`. Phép cộng tính bằng `int` nhưng `+=` có chuyển về kiểu `byte`, làm mất các bit cao hơn tám bit.

## Checklist ghi nhớ

- [ ] Xác định **độ rộng bit** của kiểu nguồn và kiểu đích.
- [ ] Thu hẹp **nguyên → nguyên** giữ các bit thấp, loại bit cao.
- [ ] `byte`/`short`/`int` có dấu; `char` là 16 bit không dấu.
- [ ] Cast hợp lệ về cú pháp không có nghĩa giá trị không thay đổi.
- [ ] Kiểm tra miền trước khi cast khi việc mất dữ liệu là lỗi.
- [ ] `Math.toIntExact` giúp kiểm tra `long -> int`.
- [ ] Lưu ý chuyển kiểu ở `+=` và các phép toán số học với `byte`, `short`.
- [ ] Không áp dụng quy tắc giữ bit thấp để giải thích cast `double -> int`.

**Kết luận:** Khi thu hẹp giữa các kiểu số nguyên của Java, câu hỏi chính là **“những bit nào bị bỏ và những bit còn lại sẽ được diễn giải có dấu hay không dấu?”**. Nếu cần dữ liệu chính xác, đừng cast mù; hãy kiểm tra miền hoặc chọn kiểu đủ rộng từ đầu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-conversion-005">Chuyển số thực sang số nguyên và cắt phần thập phân</a>

<details>
<summary>Xem chi tiết</summary>

# Chuyển số thực sang số nguyên và cắt phần thập phân — Java 17+

## 1. Hiểu đúng phép chuyển đổi

Trong Java, ép kiểu một giá trị `float` hoặc `double` sang `int` hay `long` là **narrowing primitive conversion** (chuyển kiểu nguyên thủy thu hẹp). Với số **hữu hạn** nằm trong miền kiểu đích, Java **loại bỏ phần thập phân theo hướng về 0** (*truncate toward zero*).

~~~java
double price = 12.9;
int whole = (int) price;
System.out.println(whole); // 12

double adjustment = -12.9;
int negative = (int) adjustment;
System.out.println(negative); // -12
~~~

Không được nhầm phép cast này với **làm tròn đến số nguyên gần nhất**:

- `(int) 12.9` → `12`.
- `(int) -12.9` → `-12`.
- Không có chuyện số âm được tự làm tròn xuống `-13`.
- Phép cast không tự xác thực xem dữ liệu có phần lẻ hay có nằm trong giới hạn ứng dụng hay không.

**Ý tưởng chủ đạo:** `(int) value` là **chuyển kiểu**, không phải một hàm làm tròn tùy chọn và cũng không phải cơ chế kiểm tra an toàn dữ liệu.

## 2. Cú pháp cast: kiểu đích trong dấu ngoặc

~~~java
double reading = 17.75;
int valueAsInt = (int) reading;
long valueAsLong = (long) reading;

System.out.println(valueAsInt);  // 17
System.out.println(valueAsLong); // 17
~~~

Dấu `(int)` hoặc `(long)` nói với compiler rằng bạn chủ động yêu cầu **chuyển giá trị**. Nếu nguồn là biến `double`, Java không cho gán trực tiếp sang `int` không có cast:

~~~java
double reading = 17.75;
// int wrong = reading; // Lỗi biên dịch: possible lossy conversion
int correct = (int) reading;
~~~

Bảng ví dụ cho **giá trị hữu hạn và vừa miền `int`**:

| Giá trị nguồn | `(int)` | Giải thích |
|---|---:|---|
| `12.9` | 12 | Bỏ phần lẻ, tiến về 0 |
| `12.1` | 12 | Không làm tròn lên |
| `-12.9` | -12 | Không phải -13 |
| `-12.1` | -12 | Tiến về 0 |
| `0.99` | 0 | Phần nguyên bằng 0 |
| `-0.99` | 0 | `int` không có âm 0 |
| `12.0` | 12 | Không có phần lẻ |
| `-0.0` | 0 | Mất dấu âm của zero khi thành `int` |

Ở đây **cắt phần thập phân** có nghĩa với các số hữu hạn thông thường: kết quả là số nguyên có độ lớn **không vượt quá** độ lớn của nguồn, trừ khi xuất hiện các quy tắc giới hạn miền cần xử lý riêng.

## 3. Cắt về 0 khác `floor`, `ceil`, `round` và `rint`

Java có nhiều API liên quan tới số thực, nhưng chúng giải quyết **những mục đích khác nhau**.

| Biểu thức | Giá trị | Quy tắc |
|---|---:|---|
| `(int) 12.9` | `12` | Cắt về 0 |
| `(int) -12.9` | `-12` | Cắt về 0 |
| `Math.floor(12.9)` | `12.0` | Số nguyên lớn nhất không vượt giá trị nguồn |
| `Math.floor(-12.9)` | `-13.0` | Làm tròn xuống theo trục số |
| `Math.ceil(12.1)` | `13.0` | Làm tròn lên theo trục số |
| `Math.ceil(-12.9)` | `-12.0` | Làm tròn lên theo trục số |
| `Math.round(12.5)` | `13` | Làm tròn theo quy tắc của `Math.round` |
| `Math.round(-12.5)` | `-12` | Trường hợp nửa đơn vị theo hướng +∞ |
| `Math.rint(12.5)` | `12.0` | Nửa đường chọn số nguyên chẵn |
| `Math.rint(13.5)` | `14.0` | Nửa đường chọn số nguyên chẵn |

**Kiểu trả về cũng khác:**

- `(int) d` trả `int`; `(long) d` trả `long`.
- `Math.floor(double)`, `Math.ceil(double)`, `Math.rint(double)` trả **`double`**, dù giá trị có phần lẻ bằng 0.
- `Math.round(double)` trả **`long`**; còn `Math.round(float)` trả **`int`**.

Ví dụ:

~~~java
double value = -12.9;
System.out.println((int) value);       // -12
System.out.println(Math.floor(value)); // -13.0
System.out.println(Math.ceil(value));  // -12.0
~~~

**Đừng chọn một API chỉ vì tên nghe giống “làm tròn”.** Hãy xác định rõ nghiệp vụ muốn **cắt**, **làm tròn xuống**, **làm tròn lên** hay **chọn số nguyên gần nhất**.

## 4. Trường hợp đặc biệt: NaN, vô cực và vượt miền

Ép `double`/`float` sang kiểu nguyên **không mặc định ném exception** khi đầu vào bất thường. Java có những quy tắc bắt buộc:

1. **NaN** chuyển thành `0`.
2. **Dương vô cực** hoặc giá trị quá lớn theo hướng dương chuyển thành **giá trị lớn nhất** của kiểu đích nguyên đang xét.
3. **Âm vô cực** hoặc giá trị quá nhỏ theo hướng âm chuyển thành **giá trị nhỏ nhất** của kiểu đích nguyên đang xét.
4. Với giá trị hữu hạn trong miền có thể biểu diễn, bỏ phần lẻ theo hướng về 0.

~~~java
System.out.println((int) Double.NaN);               // 0
System.out.println((int) Double.POSITIVE_INFINITY); // 2147483647
System.out.println((int) Double.NEGATIVE_INFINITY); // -2147483648
System.out.println((int) 1.0e100);                  // 2147483647
System.out.println((long) 1.0e100);                 // 9223372036854775807
~~~

Giá trị trên thuộc **narrowing conversion**, không phải một phép kiểm tra giá trị hợp lệ. Nếu đầu vào NaN mà cast ra 0, không thể suy ra người dùng thực sự cung cấp số 0.

### Sơ đồ quyết định cho dữ liệu cần chuyển an toàn

~~~mermaid
flowchart TD
    A["Giá trị float / double"] --> B{"Double.isFinite(value)?"}
    B -- "Không" --> X["Từ chối hoặc xử lý NaN / Infinity"]
    B -- "Có" --> C{"Trong miền kiểu đích?"}
    C -- "Không" --> Y["Từ chối hoặc chọn kiểu rộng hơn"]
    C -- "Có" --> D{"Nghiệp vụ cho mất phần lẻ?"}
    D -- "Có" --> E["Cast (int) hoặc (long): cắt về 0"]
    D -- "Không" --> F{"Giá trị là số nguyên?"}
    F -- "Không" --> Z["Từ chối hoặc áp dụng quy tắc làm tròn"]
    F -- "Có" --> E
~~~

Khi làm việc với dữ liệu tài chính, số lượng hoặc chỉ số mảng, **không nên coi NaN → 0 hoặc Infinity → MAX_VALUE là lựa chọn an toàn mặc định**.

## 5. Đúng miền `int`: chú ý cận trên khi so với double

`int` có miền:

~~~text
Integer.MIN_VALUE = -2147483648
Integer.MAX_VALUE =  2147483647
~~~

Khi nguồn là `double`, cách kiểm tra chính xác và dễ lý giải là:

~~~java
static boolean inIntInputRange(double value) {
    return Double.isFinite(value)
            && value >= -2147483648.0
            && value <  2147483648.0;
}
~~~

Dùng cận trên **loại trừ `2147483648.0` (2³¹)**. Vì sao không viết đơn giản `value <= Integer.MAX_VALUE` ở mọi ngữ cảnh? Phép so sánh hỗn hợp có thể chuyển `Integer.MAX_VALUE` thành `double`; với `int` việc này vẫn chính xác, nhưng viết cận trên loại trừ làm rõ ranh giới kiểu đích và tránh vô ý chấp nhận một giá trị ngoài miền.

Cần hiểu chính sách kiểm tra: `-2147483648.5` là một số có phần nguyên sau khi cắt có thể bằng `Integer.MIN_VALUE`, nhưng **đầu vào thực ban đầu nằm ngoài miền giá trị nguyên**. Hàm trên chủ động từ chối giá trị nằm ngoài miền **trước khi cắt**, để không che lỗi dữ liệu.

`double` cũng biểu diễn chính xác hai giá trị cận `-2147483648.0`, `2147483648.0`, nên so sánh này rõ ràng.

### Miền `long` có bẫy lớn hơn

`Long.MAX_VALUE` bằng `9223372036854775807`, nhưng **`double` không thể biểu diễn chính xác số nguyên ấy**. Giá trị `double` của `Long.MAX_VALUE` khi chuyển đổi mở rộng được làm tròn lên **2⁶³**.

Vì vậy **không dùng đơn thuần `value <= Long.MAX_VALUE` để chứng minh giá trị double nằm trong miền long**. Một phép kiểm tra phạm vi theo chính `double` là:

~~~java
static boolean inLongInputRange(double value) {
    return Double.isFinite(value)
            && value >= -0x1.0p63
            && value <   0x1.0p63;
}
~~~

`0x1.0p63` là literal `double` hệ 16 dạng dấu phẩy động, biểu diễn chính xác \(2^{63}\). Cận dưới \(-2^{63}\) được phép, cận trên \(2^{63}\) bị loại.

Nếu dữ liệu ban đầu là một số nguyên `long`, **đừng đi vòng qua double** để rồi hy vọng nhận lại chính xác mọi chữ số. Hãy giữ dữ liệu ở kiểu nguyên khi yêu cầu tính chính xác từng đơn vị.

## 6. Khi cần loại bỏ phần lẻ nhưng từ chối NaN và overflow

Đây là hàm cho trường hợp **cho phép cắt phần lẻ**, nhưng đầu vào phải hữu hạn và nằm trong miền int:

~~~java
static int truncateIntChecked(double value) {
    if (!Double.isFinite(value)
            || value < -2147483648.0
            || value >= 2147483648.0) {
        throw new IllegalArgumentException("outside int input range");
    }
    return (int) value;
}
~~~

Ví dụ:

~~~java
System.out.println(truncateIntChecked(42.75)); // 42
System.out.println(truncateIntChecked(-42.75)); // -42
~~~

Những giá trị `Double.NaN`, `Double.POSITIVE_INFINITY`, `2147483648.0` sẽ bị **từ chối có chủ đích** thay vì âm thầm chuyển thành 0 hoặc giá trị sát giới hạn.

**Đặc điểm nghiệp vụ:** hàm này cho phép mất phần lẻ sau cast. Nếu yêu cầu “phải là số nguyên thật sự”, cần thêm kiểm tra khác trước khi cast.

## 7. Khi không được phép mất phần lẻ

Ví dụ số lượng món hàng phải nguyên; nhận dữ liệu `42.75` rồi cắt thành 42 có thể là lỗi nghiệp vụ. Hãy kiểm tra:

~~~java
static int requireWholeInt(double value) {
    if (!Double.isFinite(value)
            || value < -2147483648.0
            || value >= 2147483648.0
            || value != Math.rint(value)) {
        throw new IllegalArgumentException("not a finite whole int");
    }
    return (int) value;
}
~~~

`Math.rint(value)` trả về một giá trị double đã làm tròn tới số nguyên gần nhất theo quy tắc nửa tới số chẵn. Ở đây **không dùng kết quả ấy để sửa giá trị**; chỉ dùng phép so sánh `value == Math.rint(value)` để kiểm tra giá trị nguồn **đã nguyên**.

Ví dụ:

~~~java
System.out.println(requireWholeInt(42.0)); // 42
// requireWholeInt(42.5); // Ném IllegalArgumentException
~~~

**Chú ý sai số trước đó:** nếu một phép tính floating-point đáng lẽ ra số nguyên nhưng thực tế tạo `41.99999999999999`, kiểm tra exact sẽ từ chối. Đây không phải bug của `Math.rint`, mà là yêu cầu nghiệp vụ cần quyết định **chấp nhận xấp xỉ có dung sai hay cần tính toán thập phân chính xác**. Không tùy tiện “cứ gần số nguyên là ép kiểu” cho dữ liệu quan trọng.

## 8. Thu hẹp tiếp thành `byte`, `short`, `char`

Chuyển từ dấu phẩy động sang kiểu nguyên nhỏ hơn diễn ra qua **bước chuyển sang `int` rồi thu hẹp tiếp**. Hành vi có thể khác nhiều so với việc chỉ bỏ phần lẻ.

~~~java
double input = 130.9;
byte asByte = (byte) input;
System.out.println(asByte); // -126
~~~

Giải thích theo hai bước:

1. `130.9` được chuyển thành giá trị `int` là `130`.
2. `130` bị thu hẹp về `byte`, giữ 8 bit thấp và diễn giải thành `-126`.

Với `char`:

~~~java
double input = 65536.9;
char asChar = (char) input;
System.out.println((int) asChar); // 0
~~~

`65536.9` → `int` 65536 → `char` giữ 16 bit thấp thành 0.

**Không nhầm hai cơ chế:** cắt phần lẻ là chuyện chuyển float/double thành int/long; cắt bit thấp là chuyện **thu hẹp tiếp từ int xuống byte/short/char**. Nếu nghiệp vụ cần một giá trị byte/short/char chính xác, hãy validation theo miền của **kiểu đích cuối cùng**.

## 9. Sai số biểu diễn có thể thay đổi phần nguyên trước khi cast

`float` và `double` biểu diễn số theo dấu phẩy động **nhị phân**. Một kết quả tính toán có thể nhỏ hơn hoặc lớn hơn một số nguyên dự kiến vì sai số làm tròn:

~~~java
double computed = 2.9999999999999996;
System.out.println(computed);       // 2.9999999999999996
System.out.println((int) computed); // 2
~~~

Mặc dù người dùng có thể nghĩ tới kết quả toán học là 3, Java **chuyển chính giá trị đang lưu** và cắt về 0 nên cho 2.

Không nên tự động áp dụng `(int) (computed + 0.000001)` như một “mẹo sửa” chung: nó thay đổi dữ liệu, có thể sai với số âm, số gần ranh giới hoặc các thang đo khác.

Nếu cần làm tròn, hãy chọn `Math.round`, `Math.floor`, `Math.ceil` hoặc quy tắc thập phân phù hợp. Nếu cần tính chính xác theo decimal, hãy xem xét `BigDecimal` hoặc số nguyên biểu diễn đơn vị nhỏ nhất.

## 10. BigDecimal khi nghiệp vụ không chấp nhận sai số/cắt ngầm

Khi giá trị đầu vào vốn là **số thập phân chính xác** (ví dụ nhập `"42.75"`), `BigDecimal` có thể diễn đạt rõ hành vi cần thiết:

~~~java
java.math.BigDecimal value = new java.math.BigDecimal("42.75");

System.out.println(value.toBigInteger()); // 42: cắt phần lẻ
// value.intValueExact(); // Ném ArithmeticException: không phải số nguyên
~~~

`toBigInteger()` trả phần nguyên bằng cách cắt phần lẻ về 0. Trong khi đó `intValueExact()` yêu cầu không mất phần thập phân và nằm trong miền `int`; nếu không, nó ném `ArithmeticException`.

~~~java
java.math.BigDecimal whole = new java.math.BigDecimal("42");
System.out.println(whole.intValueExact()); // 42
~~~

Đừng tạo `new BigDecimal(0.1)` rồi kỳ vọng có chính xác giá trị decimal 0.1; constructor từ `double` nhận giá trị nhị phân đã được làm tròn trước đó. Với dữ liệu decimal đầu vào, dùng chuỗi decimal hoặc một cơ chế chuyển tương đương có chủ đích.

**Điểm quyết định:** dùng cast khi bạn **chấp nhận cắt phần lẻ và đã kiểm tra miền**, hoặc dùng API exact khi **mất phần lẻ phải bị từ chối**.

## 11. Chương trình Java 17 hoàn chỉnh, runnable

Tạo đúng tệp **`FloatingToIntegerDemo.java`**:

~~~java
import java.math.BigDecimal;

public final class FloatingToIntegerDemo {
    private FloatingToIntegerDemo() {}

    static int truncateIntChecked(double value) {
        if (!Double.isFinite(value)
                || value < -2147483648.0
                || value >= 2147483648.0) {
            throw new IllegalArgumentException("outside int input range");
        }
        return (int) value;
    }

    static int requireWholeInt(double value) {
        if (!Double.isFinite(value)
                || value < -2147483648.0
                || value >= 2147483648.0
                || value != Math.rint(value)) {
            throw new IllegalArgumentException("not a finite whole int");
        }
        return (int) value;
    }

    public static void main(String[] args) {
        double positive = 12.9;
        double negative = -12.9;
        float small = 3.99f;

        System.out.println("positive int=" + (int) positive);
        System.out.println("negative int=" + (int) negative);
        System.out.println("positive long=" + (long) positive);
        System.out.println("negative long=" + (long) negative);
        System.out.println("small negative int=" + (int) -0.99);
        System.out.println("float int=" + (int) small);

        System.out.println("byte after double=" + (byte) 130.9);
        System.out.println("char after double=" + (int) (char) 65536.9);

        System.out.println("NaN int=" + (int) Double.NaN);
        System.out.println("+Infinity int="
                + (int) Double.POSITIVE_INFINITY);
        System.out.println("-Infinity int="
                + (int) Double.NEGATIVE_INFINITY);
        System.out.println("large int=" + (int) 1.0e100);
        System.out.println("large long=" + (long) 1.0e100);

        System.out.println("round positive half=" + Math.round(12.5));
        System.out.println("round negative half=" + Math.round(-12.5));
        System.out.println("floor negative=" + Math.floor(-12.9));
        System.out.println("ceil negative=" + Math.ceil(-12.9));
        System.out.println("rint even=" + Math.rint(12.5));
        System.out.println("rint odd=" + Math.rint(13.5));

        double computed = 2.9999999999999996;
        System.out.println("precision before cast=" + computed);
        System.out.println("precision after cast=" + (int) computed);

        System.out.println("checked truncate=" + truncateIntChecked(42.75));
        System.out.println("checked exact=" + requireWholeInt(42.0));

        try {
            requireWholeInt(42.75);
        } catch (IllegalArgumentException ex) {
            System.out.println("fraction rejected");
        }
        try {
            truncateIntChecked(Double.NaN);
        } catch (IllegalArgumentException ex) {
            System.out.println("NaN rejected");
        }
        try {
            truncateIntChecked(2147483648.0);
        } catch (IllegalArgumentException ex) {
            System.out.println("overflow rejected");
        }

        System.out.println("decimal whole="
                + new BigDecimal("42").intValueExact());
        try {
            new BigDecimal("42.75").intValueExact();
        } catch (ArithmeticException ex) {
            System.out.println("decimal fractional rejected");
        }
    }
}
~~~

Yêu cầu **JDK 17 hoặc mới hơn**, terminal đặt trong thư mục có tệp.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 FloatingToIntegerDemo.java
java -cp . FloatingToIntegerDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\FloatingToIntegerDemo.java
java -cp . FloatingToIntegerDemo
~~~

**Expected output chính xác:**

~~~text
positive int=12
negative int=-12
positive long=12
negative long=-12
small negative int=0
float int=3
byte after double=-126
char after double=0
NaN int=0
+Infinity int=2147483647
-Infinity int=-2147483648
large int=2147483647
large long=9223372036854775807
round positive half=13
round negative half=-12
floor negative=-13.0
ceil negative=-12.0
rint even=12.0
rint odd=14.0
precision before cast=2.9999999999999996
precision after cast=2
checked truncate=42
checked exact=42
fraction rejected
NaN rejected
overflow rejected
decimal whole=42
decimal fractional rejected
~~~

Chương trình không cần Maven/Gradle hoặc thư viện ngoài JDK. Không in thông điệp exception phụ thuộc cài đặt hệ thống; các dòng báo lỗi do code chủ động xác định.

**Cần hiểu rõ các nhóm kết quả:**

- Hai dấu của `12.9` chứng minh **cắt về 0**, không luôn “làm tròn xuống”.
- Các dòng NaN/vô cực minh họa **quy tắc ngôn ngữ**, không có nghĩa các đầu vào đó nên được chấp nhận.
- `byte after double`/`char after double` diễn ra theo **hai bước thu hẹp**.
- Các dòng `Math.round`, `Math.floor`, `Math.ceil`, `Math.rint` cho thấy cast **khác làm tròn**.
- Hai hàm kiểm tra thể hiện **hai hợp đồng khác nhau**: được phép cắt phần lẻ hoặc yêu cầu số nguyên chính xác.

## 12. Các lỗi thường gặp

| Lỗi hoặc giả định | Tại sao sai? | Cách xử lý |
|---|---|---|
| `int n = doubleValue;` | Double → int là narrowing, không gán ngầm | Cast có chủ đích và kiểm tra |
| `(int) -12.9` được nghĩ là -13 | Cast cắt về 0 | Kết quả -12; muốn floor dùng `Math.floor` |
| `(int) 12.9` được nghĩ là 13 | Cast không làm tròn gần nhất | Kết quả 12; xem `Math.round` |
| NaN được cast thành 0 rồi coi là dữ liệu hợp lệ | Cast không validation | Dùng `Double.isFinite` |
| Dương Infinity cast về int thành max mà không báo lỗi | Quy tắc bão hòa | Từ chối dữ liệu vô cực nếu nghiệp vụ yêu cầu |
| Dùng `(int) 1e100` để kiểm tra miền | Cast bão hòa về MAX_VALUE | Kiểm tra giới hạn trước |
| `(byte) 130.9` cho 130 | Hai lần narrowing tạo -126 | Kiểm tra miền byte |
| `(int) 2.9999999999999996` cho 3 | Không có làm tròn | Giá trị thực đang lưu dẫn tới 2 |
| So `double <= Long.MAX_VALUE` đủ kiểm tra long | MAX_VALUE long làm tròn lên khi đổi thành double | Dùng ranh giới `±2^63` phù hợp |
| `Math.round` luôn cùng quy tắc với `Math.rint` | Khác quy tắc tie và kiểu trả về | Chọn đúng API theo yêu cầu |
| Cast sang int để loại bỏ phần lẻ dù dữ liệu cần giữ chính xác | Mất dữ liệu không có cảnh báo | `intValueExact` của BigDecimal hoặc validation rõ |
| Dùng `new BigDecimal(double)` cho decimal chính xác | Lấy binary approximation của double | Tạo từ chuỗi decimal khi dữ liệu nguồn là decimal |

## 13. Bài tập có đáp án

### Bài A — số dương và âm

~~~java
System.out.println((int) 8.99);
System.out.println((int) -8.99);
System.out.println((int) -0.75);
~~~

**Đáp án:**

~~~text
8
-8
0
~~~

Cast loại bỏ phần lẻ theo hướng về 0.

### Bài B — làm tròn xuống khác cắt

~~~java
double x = -3.8;
System.out.println((int) x);
System.out.println(Math.floor(x));
~~~

**Đáp án:**

~~~text
-3
-4.0
~~~

### Bài C — NaN và vô cực

~~~java
System.out.println((int) Double.NaN);
System.out.println((int) Double.POSITIVE_INFINITY);
System.out.println((long) Double.NEGATIVE_INFINITY);
~~~

**Đáp án:**

~~~text
0
2147483647
-9223372036854775808
~~~

Những giá trị này do quy tắc narrowing quy định. Chúng không chứng minh đầu vào được phép dùng làm số lượng hợp lệ.

### Bài D — byte sau khi chuyển từ double

~~~java
double value = 255.9;
System.out.println((byte) value);
~~~

**Đáp án:** `-1`. Đầu tiên `255.9` thành `int` 255, sau đó thành `byte` có dấu -1.

### Bài E — số lượng phải là số nguyên

Bạn nhận một giá trị `double quantity = 7.25;` và **không được phép làm mất phần lẻ**. Có nên dùng `(int) quantity`?

**Đáp án:** không. Cast cho 7 và làm mất 0.25. Hãy yêu cầu số hữu hạn, trong miền kiểu đích và có giá trị nguyên trước khi chuyển. Với nguồn decimal chính xác, có thể dùng `BigDecimal.intValueExact()`.

### Bài F — kiểm tra cận `long` từ double

Vì sao so sánh `value <= Long.MAX_VALUE` không phải cách đủ an toàn để xác nhận mọi `double` có thể chuyển về `long` đúng giá trị?

**Đáp án:** `Long.MAX_VALUE = 2^63-1` không biểu diễn chính xác được bởi `double`; khi đổi sang double nó làm tròn thành `2^63`. Cần kiểm tra `value < 0x1.0p63` và `value >= -0x1.0p63`, đồng thời kiểm tra hữu hạn; nếu yêu cầu không mất phần lẻ, thêm điều kiện giá trị đã nguyên.

## 14. Checklist và kết luận

- [ ] Phân biệt **cast về int/long** với **làm tròn**.
- [ ] Biết `(int) 12.9 == 12`, `(int) -12.9 == -12`.
- [ ] Nhớ `Math.floor`/`ceil`/`round`/`rint` có quy tắc khác nhau.
- [ ] Hiểu chuyển đổi NaN → 0, ±Infinity → giới hạn kiểu đích.
- [ ] Biết ép kiểu không tự ném lỗi khi giá trị floating-point vượt miền.
- [ ] Kiểm tra `Double.isFinite` trước khi dùng dữ liệu yêu cầu hữu hạn.
- [ ] Hiểu vì sao cận `long` cần so với `±2^63` bằng double.
- [ ] Phân biệt cast sang byte/short/char với cast trực tiếp sang int/long.
- [ ] Biết lỗi xấp xỉ floating-point có thể làm phần nguyên sau cast thay đổi.
- [ ] Chọn hàm kiểm tra **cho cắt phần lẻ** hoặc **không cho mất phần lẻ** theo yêu cầu.
- [ ] Biết `BigDecimal.intValueExact()` từ chối dữ liệu có phần lẻ.
- [ ] Chạy `FloatingToIntegerDemo.java` bằng `javac`/`java` Java 17+ và đối chiếu từng dòng.

**Kết luận:** Ép `float`/`double` sang số nguyên trong Java **cắt phần thập phân theo hướng về 0** khi giá trị hữu hạn nằm trong miền. Với NaN, vô cực hoặc dữ liệu vượt miền, Java áp dụng các quy tắc chuyển đổi đặc biệt mà không tự xem đó là lỗi nghiệp vụ. Hãy lựa chọn rõ yêu cầu **cắt**, **làm tròn**, hay **chuyển đổi chính xác**, rồi kiểm tra dữ liệu trước khi cast.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-conversion-006">Unary numeric promotion và binary numeric promotion</a>

<details>
<summary>Xem chi tiết</summary>

# Unary numeric promotion và binary numeric promotion — Java 17+

Trong Java, **numeric promotion (nâng kiểu số)** là các phép chuyển đổi ngầm định mà ngôn ngữ áp dụng để xác định kiểu dùng khi đánh giá một biểu thức số. Hai cơ chế cần phân biệt:

- **Unary numeric promotion**: nâng kiểu cho **một toán hạng số** trong những ngữ cảnh được quy định, ví dụ toán tử một ngôi `+`, `-`, `~`.
- **Binary numeric promotion**: đưa **hai toán hạng số** về một kiểu chung trước một số phép toán hai ngôi, ví dụ cộng số `+`, nhân `*`, so sánh số `<`.

Cả hai đều được compiler quyết định từ **kiểu của biểu thức**, không phải từ giá trị thực tế. Vì vậy hai biến `byte` cộng nhau có kiểu kết quả `int`, ngay cả khi tổng nhỏ và nằm trong miền `byte`.

## 1. Unary numeric promotion: một toán hạng

Với một biểu thức phải áp dụng *unary numeric promotion*:

| Kiểu ban đầu | Kiểu sau promotion |
| --- | --- |
| `byte` | `int` |
| `short` | `int` |
| `char` | `int` |
| `int` | `int` |
| `long` | `long` |
| `float` | `float` |
| `double` | `double` |

Với wrapper số thích hợp như `Byte`, `Short`, `Character` hoặc `Integer`, Java có thể **unbox** trước rồi áp dụng promotion; wrapper `null` có thể gây `NullPointerException`.

Ví dụ:

~~~java
byte b = 100;
int a = +b; // Unary +: byte -> int.
int c = -b; // Unary -: byte -> int, ket qua -100.

short min = Short.MIN_VALUE; // -32768.
int positive = -min;        // int 32768, khong phai short.

byte zero = 0;
int inverted = ~zero;       // int -1, vi ~ ap dung tren 32 bit int.
~~~

**Bẫy phổ biến:** `-Short.MIN_VALUE` không tràn kiểu `short`, vì toán hạng đã được nâng thành `int`. Tuy nhiên, `-Integer.MIN_VALUE` vẫn là phép toán `int`; kết quả tràn và quay về `Integer.MIN_VALUE`. Promotion **không tự phát hiện tràn**.

Với `char`, giá trị 16 bit không dấu được nâng thành `int`. Ví dụ `'A' + 1` cho giá trị số `66` có kiểu `int`, **không phải** `char 'B'`.

## 2. Binary numeric promotion: hai toán hạng

Khi ngữ cảnh yêu cầu *binary numeric promotion*, Java đưa các toán hạng về cùng kiểu theo thứ tự ưu tiên:

1. Nếu có `double`, toán hạng kia chuyển sang `double`.
2. Ngược lại, nếu có `float`, toán hạng kia chuyển sang `float`.
3. Ngược lại, nếu có `long`, toán hạng kia chuyển sang `long`.
4. Nếu không, **cả hai thành `int`** (gồm `byte`, `short`, `char`).

| Biểu thức | Kiểu dùng để tính | Vì sao |
| --- | --- | --- |
| `byte + byte` | `int` | Các kiểu nguyên nhỏ được nâng thành `int` |
| `short + char` | `int` | Cả hai được nâng thành `int` |
| `int + long` | `long` | `long` ưu tiên hơn `int` |
| `long + float` | `float` | `float` ưu tiên hơn `long` |
| `float + double` | `double` | `double` ưu tiên cao nhất |
| `int < double` | Hai toán hạng chuyển thành `double` | Kết quả so sánh là **`boolean`** |
| `int == long` | Hai toán hạng chuyển thành `long` | Kết quả so sánh là **`boolean`** |

Thứ tự ưu tiên nói trên là **quy tắc kiểu**, không phải lời bảo đảm giữ nguyên giá trị: chuyển một `long` rất lớn sang `float` hoặc `double` có thể **mất độ chính xác**.

### Không phải mọi toán tử hai ngôi đều dùng binary numeric promotion

- `"n=" + 1 + 2` là **nối chuỗi** sau khi toán hạng đầu tiên là `String`; kết quả `"n=12"`. `"n=" + (1 + 2)` cho `"n=3"`.
- `boolean && boolean` hay `boolean || boolean` là phép logic, **không** phải phép toán số.
- Toán tử dịch bit `<<`, `>>`, `>>>` **không áp dụng binary numeric promotion cho hai toán hạng**: *mỗi* toán hạng được **unary numeric promotion** riêng. Kiểu kết quả là kiểu đã nâng của **toán hạng bên trái**.

## 3. Chương trình Java 17+ runnable

Tạo file **`NumericPromotionDemo.java`** với nội dung dưới đây:

~~~java
public class NumericPromotionDemo {
    private static void show(String label, Object value) {
        System.out.println(label + "=" + value
                + " (boxedType=" + value.getClass().getSimpleName() + ")");
    }

    public static void main(String[] args) {
        byte first = 100;
        byte second = 27;
        short minimum = Short.MIN_VALUE;
        byte zero = 0;
        char letter = 'A';

        show("bytePlusByte", first + second);
        show("unaryPlusByte", +first);
        show("unaryMinusShort", -minimum);
        show("unaryComplementByte", ~zero);
        show("charPlusOne", letter + 1);
        show("shortPlusChar", (short) 300 + letter);

        show("intPlusLong", 2 + 3L);
        show("floatPlusLong", 0.5f + 2L);
        show("doublePlusFloat", 1.25d + 0.5f);

        short count = 3;
        show("shiftShort", count << 2);
        show("shiftIntByLong", 1 << 2L);
        show("shiftLongByInt", 1L << 2);

        show("intDivision", 5 / 2);
        show("divisionWithDouble", 5 / 2.0);

        byte compound = 100;
        compound += 200;
        show("compoundByte", compound);

        Integer wrapper = 4;
        show("unboxPlusLong", wrapper + 2L);

        System.out.println("numericComparison=" + (first < 200L));
        System.out.println("stringConcat=" + "n=" + 1 + 2);
        System.out.println("stringGrouped=" + "n=" + (1 + 2));

        Integer missing = null;
        try {
            show("missingPlusOne", missing + 1);
        } catch (NullPointerException ex) {
            System.out.println("unboxNull=NullPointerException");
        }
    }
}
~~~

Mở PowerShell (Windows) hoặc Bash (Linux/macOS) tại thư mục có file và sử dụng **JDK 17+**:

~~~bash
java -version
javac -version
javac -encoding UTF-8 NumericPromotionDemo.java
java NumericPromotionDemo
~~~

**Expected output chính xác** của lệnh `java NumericPromotionDemo` (không gồm các dòng phiên bản JDK):

~~~text
bytePlusByte=127 (boxedType=Integer)
unaryPlusByte=100 (boxedType=Integer)
unaryMinusShort=32768 (boxedType=Integer)
unaryComplementByte=-1 (boxedType=Integer)
charPlusOne=66 (boxedType=Integer)
shortPlusChar=365 (boxedType=Integer)
intPlusLong=5 (boxedType=Long)
floatPlusLong=2.5 (boxedType=Float)
doublePlusFloat=1.75 (boxedType=Double)
shiftShort=12 (boxedType=Integer)
shiftIntByLong=4 (boxedType=Integer)
shiftLongByInt=4 (boxedType=Long)
intDivision=2 (boxedType=Integer)
divisionWithDouble=2.5 (boxedType=Double)
compoundByte=44 (boxedType=Byte)
unboxPlusLong=6 (boxedType=Long)
numericComparison=true
stringConcat=n=12
stringGrouped=n=3
unboxNull=NullPointerException
~~~

**Cách đọc tên `boxedType` trong output:** Các biểu thức số được truyền vào tham số `Object value` của `show`, nên Java thực hiện **autoboxing** kết quả nguyên thủy thành wrapper (`int -> Integer`, `long -> Long`...). Nhờ đó, `value.getClass().getSimpleName()` giúp quan sát kiểu nguyên thủy của **kết quả biểu thức trước khi boxing**. Điều này không có nghĩa mọi giá trị số trong Java vốn là đối tượng.

### Phân tích các nhóm kết quả

**Phép một ngôi:** `+first`, `-minimum` và `~zero` đều có kết quả `int`. Riêng `~zero` là phép đảo bit của **32-bit int 0**, nên kết quả `-1`.

**Phép hai ngôi:** `first + second` là `int 127`; `(short) 300 + letter` là `int 365`. `2 + 3L` có kiểu `long`, `0.5f + 2L` có kiểu `float`, `1.25d + 0.5f` có kiểu `double`.

**Phép dịch bit:** `count << 2` có kết quả `int` vì `count` là `short` được nâng lên `int`. Với `1 << 2L`, toán hạng phải là `long` nhưng kết quả **vẫn là `int`**. Còn `1L << 2` trả `long` vì toán hạng trái là `long`.

**Chia nguyên:** `5 / 2` được thực hiện trên `int` nên cho `2`. `5 / 2.0` áp dụng binary numeric promotion sang `double` trước phép chia, cho `2.5`.

**Gán kết hợp:** `compound += 200` cho phép ghi lại kết quả về `byte` dù phép cộng trung gian là `int 300`. Sau chuyển thu hẹp về 8 bit, kết quả là `44`.

**Wrapper:** `wrapper + 2L` unbox `Integer` thành `int` rồi nâng sang `long`; kết quả là `long 6`. Nếu wrapper bằng null, bước unbox gây `NullPointerException`.

## 4. Vì sao `byte` + `byte` không thể gán thẳng vào `byte`?

~~~java
byte x = 10;
byte y = 20;

int correct = x + y;       // Hop le: kieu ket qua la int.
// byte invalid = x + y;   // Loi bien dich, vi x+y la int.
byte narrowed = (byte) (x + y); // Co cast tuong minh.
~~~

Nếu cố gán `x + y` vào một biến `byte` mà không cast, compiler báo lỗi vì **kiểu biểu thức là `int`**, dù bạn biết tổng cụ thể vẫn nằm trong miền `byte`.

**Ngoại lệ đối với biểu thức hằng:** `byte b = 10 + 20;` có thể biên dịch, vì `10 + 20` là biểu thức hằng kiểu `int` với giá trị nằm trong miền `byte`. Quy tắc thu hẹp hằng số ở ngữ cảnh gán **không làm thay đổi** việc `x + y` giữa hai biến `byte` có kiểu `int`.

Một cast ngược về `byte` cũng không tự bảo đảm giữ nguyên giá trị; ví dụ `(byte) (100 + 100)` cho `-56` do kết quả 200 vượt miền `byte`.

## 5. Binary promotion khi so sánh và tính toán

Cùng một quy tắc binary numeric promotion có thể áp dụng cho biểu thức số học và so sánh số:

~~~java
short left = 10;
long right = 20L;

long total = left + right; // long 30.
boolean smaller = left < right; // true.
~~~

Với phép cộng, kiểu kết quả là kiểu số được chọn sau promotion. Với phép so sánh `<`, `<=`, `>`, `>=` hoặc so sánh bằng giữa hai biểu thức số, **các toán hạng** được nâng theo quy tắc thích hợp nhưng **kết quả phép so sánh là `boolean`**, không phải kiểu số.

Do chuyển sang kiểu floating-point có thể mất độ chính xác, hãy cẩn thận khi so sánh `long` rất lớn với `float`/`double`. Không dùng “được Java tự nâng kiểu” như chứng minh rằng mọi phép so sánh đều giữ đúng từng đơn vị nguyên.

## 6. Phép dịch bit: hai toán hạng nâng kiểu riêng

Đây là trường hợp rất hay bị nhầm với binary numeric promotion:

~~~java
short s = 3;
int result = s << 2; // left short -> int; result int.

int x = 1 << 2L;     // RHS long duoc chap nhan; ket qua van int.
long y = 1L << 2;    // Left long; ket qua long.
~~~

Trong phép dịch bit, Java áp dụng **unary numeric promotion** cho *mỗi toán hạng*, rồi lấy **kiểu của toán hạng trái đã nâng** làm kiểu kết quả. Toán hạng phải cung cấp số vị trí dịch; nó không biến kết quả thành `long` chỉ vì nó có kiểu `long`.

Ngoài ra, với toán hạng trái kiểu `int`, Java chỉ dùng **5 bit thấp** của khoảng dịch (tương đương lấy modulo 32); với `long`, Java dùng **6 bit thấp** (tương đương lấy modulo 64). Đây là quy tắc dịch bit riêng, không phải quy tắc chọn kiểu của binary numeric promotion.

## 7. Promotion, ép kiểu và toán tử gán kết hợp khác nhau

| Cơ chế | Ví dụ | Điểm cần nhớ |
| --- | --- | --- |
| Unary numeric promotion | `+((byte) 5)` | Kết quả kiểu `int` |
| Binary numeric promotion | `2 + 3L` | Kết quả kiểu `long` |
| Cast tường minh | `(byte) 200` | Có thể mất dữ liệu |
| Gán kết hợp | `byte b = 100; b += 200;` | Chuyển ngầm kết quả phép toán về kiểu vế trái |
| Autoboxing | `Object o = 2 + 3L;` | Kết quả `long` được boxing thành `Long` |
| Nối chuỗi | `"n=" + 1 + 2` | Không phải cộng số sau khi `String` tham gia |

Khi kết quả có thể vượt miền kiểu đích, nên kiểm tra miền trước khi cast hoặc dùng kiểu đủ rộng. Nếu cần kiểm tra tràn `int`/`long` trong phép cộng, nhân, có thể dùng các phương thức `Math.addExact`, `Math.multiplyExact` theo yêu cầu.

## 8. Bài tập tự kiểm tra

1. **Unary promotion:** `byte b = 5;` thì `+b` có kiểu gì?  
   *Đáp án:* `int`, giá trị 5.
2. **Binary promotion:** `short s = 10; char c = 'A';` thì `s + c` có kiểu và giá trị nào?  
   *Đáp án:* `int`, giá trị 75.
3. **Ưu tiên:** `1L + 0.5f` có kiểu gì?  
   *Đáp án:* `float`, giá trị 1.5f trong trường hợp này.
4. **Dịch bit:** `1 << 2L` có kiểu kết quả `long` không?  
   *Đáp án:* Không, kết quả `int 4`, vì loại toán tử này nâng mỗi toán hạng riêng.
5. **Chia:** `double result = 5 / 2;` có giá trị bao nhiêu?  
   *Đáp án:* `2.0` do phép chia kiểu `int` xảy ra trước khi gán sang `double`.
6. **Gán kết hợp:** `byte b = 100; b += 200;` cho gì?  
   *Đáp án:* `44` sau phép cộng trung gian `int 300` và chuyển thu hẹp về `byte`.
7. **Null wrapper:** `Integer x = null; int y = x + 1;` lỗi ở đâu?  
   *Đáp án:* Khi unboxing `x` để thực hiện phép cộng, ném `NullPointerException`.

## Checklist ghi nhớ

- [ ] Unary promotion: `byte`/`short`/`char` nâng thành `int`; các kiểu số lớn giữ nguyên.
- [ ] Binary promotion: ưu tiên `double > float > long > int` trong các ngữ cảnh số thích hợp.
- [ ] Promotion là quy tắc **kiểu biểu thức**, không phải bảo đảm không mất giá trị.
- [ ] `byte + byte` có kiểu `int`; phép gán hằng có thể có ngoại lệ riêng.
- [ ] Toán tử dịch bit nâng **từng toán hạng riêng**, kiểu kết quả dựa vào vế trái.
- [ ] `+=` có bước chuyển về kiểu biến đích; có thể làm mất dữ liệu.
- [ ] Phân biệt cộng số, nối chuỗi và boxing/unboxing.

**Kết luận:** Trước khi viết phép tính Java, hãy xác định **toán tử có dùng unary hay binary numeric promotion không**, **kiểu của từng toán hạng sau nâng kiểu** và **kiểu kết quả**. Đặc biệt cẩn thận với `byte`/`short`/`char`, phép dịch bit, phép gán kết hợp và chuyển đổi có thể mất độ chính xác.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-conversion-007">Vì sao phép cộng byte hoặc short thường cho kết quả int</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao phép cộng `byte` hoặc `short` thường cho kết quả `int` trong Java?

> **Java core — language-basics / Chuyển đổi kiểu**  
> **Môi trường:** JDK 17 trở lên; không cần thư viện ngoài.  
> **Mục tiêu:** Hiểu quy tắc *binary numeric promotion* của Java, phân biệt kiểu của **biểu thức cộng** với kiểu của biến đích, nhận diện ngoại lệ hằng số, xử lý `+=`/`++` và dự đoán chính xác kết quả khi ép hẹp.

## 1. Giá trị nhỏ không quyết định kiểu kết quả

Xem đoạn mã:

~~~java
byte left = 10;
byte right = 20;

int sum = left + right; // Hợp lệ, sum = 30.
// byte bad = left + right; // Lỗi biên dịch: int cannot be converted to byte.
~~~

Hai biến nguồn đều là `byte` nhưng biểu thức `left + right` có kiểu **`int`**, kể cả khi giá trị 30 hoàn toàn nằm trong phạm vi `byte`.

Tương tự:

~~~java
short a = 100;
short b = 200;
int total = a + b; // total = 300; biểu thức có kiểu int.
// short bad = a + b; // Lỗi biên dịch.
~~~

**Vì sao?** Java quy định phép cộng số học hai ngôi `+` dùng **binary numeric promotion** (*nâng kiểu số học hai ngôi*) để chọn kiểu chung **trước khi thực hiện phép cộng**. Với kiểu nguyên nhỏ `byte`, `short` hoặc `char`, các toán hạng được nâng lên `int` (trừ khi có toán hạng `long`/`float`/`double` làm kiểu chung rộng hơn).

Kiểu của biểu thức được xác định theo **quy tắc ngôn ngữ và kiểu toán hạng**, không dựa trên việc kết quả chạy thực tế có vừa `byte` hay `short` hay không.

## 2. Vì sao kiểu kết quả là `int`?

Toán tử cộng số `+` sử dụng *binary numeric promotion*. Nếu có toán hạng `double`, `float` hoặc `long`, Java chọn kiểu tương ứng theo thứ tự đó; trong các trường hợp còn lại, các toán hạng số được nâng thành `int`. Vì thế `byte + byte`, `short + short`, `char + byte` đều có kiểu `int`. Quy tắc dựa trên **kiểu khai báo của biểu thức**, không phụ thuộc giá trị tổng tại runtime.

Chú ý: `"x=" + byteValue` là nối chuỗi, còn toán tử `+x` là phép một ngôi có quy tắc nâng kiểu riêng. Các ví dụ dưới đây tập trung vào lỗi gán kết quả cộng số nhỏ về kiểu hẹp và cách xử lý chính xác.
## 3. Vì sao `byte result = a + b;` báo lỗi dù tổng nằm trong miền?

`byte` có miền giá trị **-128 đến 127**; `short` từ **-32768 đến 32767**. Tuy nhiên Java kiểm tra kiểu của phép gán theo **kiểu biểu thức**, không suy đoán giới hạn của biến tại runtime.

~~~java
byte x = 10;
byte y = 20;
// byte z = x + y; // KHÔNG biên dịch: x + y có kiểu int.

int safe = x + y;        // Nên dùng khi xử lý số học.
byte explicit = (byte) (x + y); // Hợp lệ: cast tường minh.
~~~

Với phép ép `(byte)`, Java giữ **8 bit thấp** của `int` rồi diễn giải kết quả như `byte` có dấu. Nếu kết quả số học nằm ngoài miền `byte`, giá trị sẽ đổi theo các bit còn lại.

~~~java
byte a = 120;
byte b = 10;
int correctSum = a + b;       // 130
byte narrowed = (byte) (a + b); // -126
~~~

Với `short`, khi ép hẹp từ `int` Java giữ **16 bit thấp**:

~~~java
short a = 30_000;
short b = 10_000;
int sum = a + b;          // 40000
short narrowed = (short) sum; // -25536
~~~

Bước cộng từ hai `byte` hoặc hai `short` là phép cộng `int` nên **không bị tràn ở kiểu nhỏ trong chính bước đó**. Giá trị âm lạ xuất hiện do **ép hẹp / lưu trở lại kiểu nhỏ**. Với những biểu thức có nhiều phép tính `int`, vẫn phải xét khả năng tràn `int` ở các bước trung gian.

## 4. Vì sao `byte c = 10 + 20;` lại biên dịch được?

Có **ngoại lệ cho một số phép gán hằng số nguyên tại thời điểm biên dịch** (*constant expression narrowing*).

~~~java
byte a = 10 + 20;       // Hợp lệ: biểu thức hằng int = 30, vừa byte.
short b = 1_000 + 200;  // Hợp lệ: hằng 1200 vừa short.

// byte bad = 100 + 30; // Lỗi: 130 vượt miền byte.
~~~

Ở đây phép `10 + 20` **vẫn có kiểu `int`**; Java cho phép **ngầm thu hẹp hằng số int** khi giá trị tính ngay lúc biên dịch vừa miền của `byte`, `short` hoặc `char` trong ngữ cảnh gán phù hợp.

Biến `final` có thể tham gia nếu thật sự là **biến hằng tại thời điểm biên dịch**:

~~~java
final byte x = 10;
final byte y = 20;
byte c = x + y; // Hợp lệ: biểu thức hằng có giá trị 30.

byte p = 10;
byte q = 20;
// byte d = p + q; // KHÔNG hợp lệ: p và q không phải compile-time constants.
~~~

Không suy ra rằng `x + y` ở ví dụ `final` có kiểu `byte`: kiểu biểu thức **vẫn là `int`**. Chỉ riêng quy tắc chuyển hằng khi **gán** cho phép lưu kết quả 30 vào `byte`. Đọc dữ liệu runtime, field không hằng hoặc tham số method không được hưởng ngoại lệ này.

## 5. `+=` và `++`: cú pháp ngắn, có chuyển kiểu ngầm

Với biến `byte`/`short`:

~~~java
byte score = 120;
// score = score + 10; // Lỗi: vế phải có kiểu int.
score += 10;           // Hợp lệ: cộng rồi chuyển ngầm trở lại byte.
System.out.println(score); // -126
~~~

`score += 10` gần tương đương với `score = (byte) (score + 10)`, nhưng biến ở vế trái chỉ được **đánh giá một lần** (quan trọng với biểu thức có tác dụng phụ). Vì có bước chuyển hẹp ngầm nên `+=` **không tự bảo vệ khỏi mất dữ liệu**.

`score++` cũng tăng rồi lưu trở lại kiểu của biến. Ví dụ `byte n = 127; n++;` cho `n == -128`. Đó là quy tắc tràn khi kết quả được lưu về `byte`, không phải phép `+` hai ngôi giữ nguyên kiểu `byte`.

**Khuyến nghị:** Khi yêu cầu bảo toàn giá trị tính toán, giữ kết quả ở `int` hoặc rộng hơn thay vì dùng `+=` trên `byte`/`short` và mặc nhiên coi kết quả an toàn.

## 6. Chương trình Java 17+ chạy được

Tạo tệp **SmallIntegerAdditionDemo.java**:

~~~java
public final class SmallIntegerAdditionDemo {
    public static void main(String[] args) {
        byte a = 10;
        byte b = 20;
        int byteSum = a + b;
        System.out.println("bytePlusByte=" + byteSum);

        short s1 = 20_000;
        short s2 = 20_000;
        int shortSum = s1 + s2;
        System.out.println("shortPlusShort=" + shortSum);

        byte small = 10;
        short middle = 20;
        int mixedSum = small + middle;
        System.out.println("bytePlusShort=" + mixedSum);

        byte high = 120;
        byte extra = 10;
        int expanded = high + extra;
        byte narrowed = (byte) (high + extra);
        System.out.println("expanded=" + expanded);
        System.out.println("narrowedByte=" + narrowed);

        short narrowedShort = (short) shortSum;
        System.out.println("narrowedShort=" + narrowedShort);

        byte constantSum = 10 + 20;
        final byte fa = 10;
        final byte fb = 20;
        byte finalConstantSum = fa + fb;
        System.out.println("constantByte=" + constantSum);
        System.out.println("finalConstantByte=" + finalConstantSum);

        byte compound = 120;
        compound += 10;
        System.out.println("compoundByte=" + compound);

        byte incremented = 127;
        incremented++;
        System.out.println("incrementedByte=" + incremented);

        byte base = 5;
        System.out.println("bytePlusLong=" + (base + 1L));
        System.out.println("bytePlusFloat=" + (base + 1.0f));
        System.out.println("charPlusByte=" + ('A' + (byte) 1));

        byte divisor = 2;
        System.out.println("integerDivision=" + (base / divisor));
    }
}
~~~

Biên dịch và chạy từ thư mục chứa tệp (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 SmallIntegerAdditionDemo.java
java SmallIntegerAdditionDemo
~~~

**Expected output chính xác:**

~~~text
bytePlusByte=30
shortPlusShort=40000
bytePlusShort=30
expanded=130
narrowedByte=-126
narrowedShort=-25536
constantByte=30
finalConstantByte=30
compoundByte=-126
incrementedByte=-128
bytePlusLong=6
bytePlusFloat=6.0
charPlusByte=66
integerDivision=2
~~~

Giải thích nhanh:

- `a + b`, `s1 + s2` và `small + middle` đều có kiểu `int`. `shortSum` là 40.000 dù không thể cất trong `short`.
- `(byte) 130` thành **-126** vì giữ 8 bit thấp; `(short) 40000` thành **-25536** vì giữ 16 bit thấp.
- Hai phép gán hằng biên dịch được nhờ giá trị compile-time nằm trong miền `byte`, **không** làm thay đổi kiểu `int` của phép cộng.
- `compound += 10` và `incremented++` lưu kết quả trở lại `byte`, nên có thể đổi dấu ở biên.
- Khi cộng với `long` hoặc `float`, kiểu chung lần lượt là `long`/`float`. `'A' + (byte) 1` là `int` 66 vì `char` có code unit giá trị 65.
- `base / divisor` là phép chia hai số nguyên đã được nâng thành `int` nên 5 chia 2 cho **2**, không phải 2.5.

## 7. Mẫu viết nên dùng khi làm bài toán thật

| Nhu cầu | Nên viết | Lý do |
| --- | --- | --- |
| Cộng `byte`/`short` để tính toán | `int total = a + b;` | Giữ kết quả không bị cắt về kiểu nhỏ |
| Tổng có thể vượt `int` | `long total = (long) a + b;` | Nâng toán hạng **trước** phép tính khi cần |
| Bắt buộc lưu lại dưới dạng `byte` | Kiểm tra `sum >= Byte.MIN_VALUE && sum <= Byte.MAX_VALUE` trước cast | Không mất giá trị ngoài ý muốn |
| Bắt buộc lưu lại dưới dạng `short` | Kiểm tra `sum` trong miền `Short.MIN_VALUE` … `Short.MAX_VALUE` | Phát hiện out-of-range |
| Dùng `+=` cho bộ đếm nhỏ | Chỉ khi đã chủ động kiểm tra giới hạn | `+=` có chuyển hẹp ngầm |
| Phép chia cần số lẻ | `double ratio = (double) a / b;` | Không chia nguyên trước khi gán `double` |

Ví dụ kiểm tra trước khi ép hẹp:

~~~java
int sum = a + b;
if (sum < Byte.MIN_VALUE || sum > Byte.MAX_VALUE) {
    throw new ArithmeticException("sum out of byte range");
}
byte safe = (byte) sum;
~~~

Đây là cách chọn có chủ đích khi một giao thức/tệp nhị phân yêu cầu đúng **8 bit**; còn trong code tính toán thông thường, giữ `int` cho phép cộng các giá trị nhỏ thường rõ ràng hơn.

## 8. Bài tập tự luyện

**Bài 1.** Cho `byte x = 7, y = 8;`. Vì sao `byte z = x + y;` lỗi biên dịch, dù 15 vừa `byte`? Viết hai cách hợp lệ và phân tích độ an toàn của từng cách.

**Bài 2.** Dự đoán `int total = (short) 30_000 + (short) 10_000;` và `short stored = (short) total;`. Thời điểm nào có mất giá trị?

**Bài 3.** So sánh `byte a = 10 + 20;`, `final byte x = 10, y = 20; byte b = x + y;` và `byte p = 10, q = 20; byte c = p + q;`. Dòng nào biên dịch được? Kiểu biểu thức `+` có khác nhau không?

**Bài 4.** Cho `byte n = 127;`. Thử `n++;` và `n += 1;` ở hai lần chạy riêng. Vì sao hai thao tác có thể đưa `n` về -128?

**Bài 5.** Cho `byte a = 5, b = 2;`. Dự đoán `a / b` và `(double) a / b`. Phân biệt kiểu số học trước phép chia.

### Ghi nhớ

**`byte + byte`, `short + short` và `byte + short` đều cho biểu thức kiểu `int`** vì quy tắc **binary numeric promotion**. Kiểu kết quả không phụ thuộc việc tổng có vừa kiểu nhỏ hay không. Gán một **biểu thức hằng `int`** vừa miền là ngoại lệ của luật chuyển hằng, không thay đổi kiểu phép cộng. Ép kiểu, `+=` và `++` có thể làm mất dữ liệu khi lưu trở lại `byte`/`short`: hãy chủ động kiểm tra giới hạn hoặc giữ kết quả bằng `int`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-conversion-008">Chuyển đổi trong phép gán, lời gọi phương thức và biểu thức</a>

<details>
<summary>Xem chi tiết</summary>

# Chuyển đổi trong phép gán, lời gọi phương thức và biểu thức — Java 17+

## 1. Mục tiêu: cùng một giá trị, ba ngữ cảnh chuyển kiểu

Java không áp dụng một quy tắc “tự đổi kiểu” duy nhất ở mọi nơi. Compiler xét **ngữ cảnh** của giá trị:

1. **Phép gán (assignment context)**: đưa giá trị vào biến, field hoặc phần tử mảng có kiểu xác định.
2. **Lời gọi phương thức (method invocation context)**: truyền biểu thức đối số cho tham số, đồng thời xử lý lựa chọn overload.
3. **Biểu thức (expression context)**: xác định kiểu và cách chuyển đổi các toán hạng của phép cộng, nhân, so sánh, nối chuỗi, toán tử điều kiện, v.v.

~~~java
byte value = 7;              // Phép gán với hằng int vừa miền byte
long wide = value;            // Phép gán: byte -> long
System.out.println(value + 1); // Biểu thức: byte được nâng thành int
~~~

Ba dòng có cùng biến `value` nhưng ý nghĩa chuyển đổi **không giống nhau**. Một chương trình chạy đúng khi kiểu được compiler chấp nhận **và** giá trị sau chuyển đổi vẫn đáp ứng nghiệp vụ.

Bài này tập trung vào các quy tắc thường dùng trong Java 17+, đặc biệt là **widening**, **narrowing**, **boxing/unboxing**, biểu thức số học và lời gọi method có overload.

## 2. Nền tảng: widening, narrowing, boxing và unboxing

| Cơ chế | Ví dụ | Ý nghĩa |
|---|---|---|
| Identity conversion | `int x = 5;` | Kiểu nguồn khớp kiểu đích |
| Widening primitive | `long x = 5;` | `int` chuyển thành `long` |
| Narrowing primitive | `byte b = (byte) 130;` | Ép về kiểu nhỏ, có thể mất dữ liệu |
| Boxing | `Integer x = 5;` | Chuyển giá trị `int` thành `Integer` |
| Unboxing | `int x = Integer.valueOf(5);` | Lấy `int` từ `Integer` |
| Widening reference | `Object o = "Hi";` | Tham chiếu String gán cho kiểu Object |
| Boxing + widening reference | `Number n = 5;` | `int` → `Integer` → `Number` |
| Unboxing + widening primitive | `long n = Integer.valueOf(5);` | `Integer` → `int` → `long` |

Không phải mọi cách kết hợp đều được thực hiện tự động. Chẳng hạn:

~~~java
Long good = 5L;
// Long wrong = 5; // Sai: int không tự widening sang long rồi boxing thành Long
~~~

`5` là literal `int`, còn `5L` là literal `long`. Java không tự cho phép **widening primitive rồi boxing sang Long** trong phép gán này.

`boolean` không chuyển sang/từ các kiểu số bằng widening hoặc narrowing primitive. `String` cũng không phải kiểu nguyên thủy: `Integer.parseInt("5")` là gọi API phân tích chuỗi, không phải conversion ngầm từ String sang int.

## 3. Ngữ cảnh phép gán (assignment context)

Khi bên trái phép gán có kiểu xác định, compiler phải kiểm tra biểu thức bên phải có thể chuyển thành kiểu đó hay không.

~~~java
byte small = 8;
int asInt = small;       // byte -> int
long asLong = asInt;     // int -> long
double asDouble = asLong; // long -> double
~~~

Những phép gán trên hợp lệ mà không cần cast. Nhưng **hợp lệ không luôn đồng nghĩa chính xác tuyệt đối**: một giá trị `long` rất lớn chuyển thành `double` có thể mất các chữ số cuối vì độ chính xác significand hữu hạn.

Phép gán thu hẹp thường **không được tự thực hiện** với biến runtime:

~~~java
int source = 130;
// byte b = source;   // Lỗi biên dịch: possible lossy conversion
byte b = (byte) source;
System.out.println(b); // -126
~~~

Cast `(byte)` chỉ yêu cầu chuyển kiểu, **không** kiểm tra rằng giá trị 130 vừa miền -128..127. Sau narrowing, giá trị bị thay đổi thành -126.

### Các nơi áp dụng ý tưởng phép gán

Ngoài câu lệnh `x = expression`, quy tắc assignment context xuất hiện trong khởi tạo biến, field, phần tử mảng và các ngữ cảnh trả về giá trị tương ứng với kiểu khai báo:

~~~java
long[] values = {1, 2, 3}; // Các int literal có thể gán vào phần tử long

static long getCount() {
    int count = 7;
    return count; // int -> long trong ngữ cảnh return
}
~~~

Không được suy từ việc một phép gán được phép narrowing hằng số rằng **mọi ngữ cảnh Java** đều cho phép tương tự.


**Riêng hằng `int` vừa miền `byte`/`short`/`char`**, phép gán có một ngoại lệ thu hẹp tại compile time (`byte b = 7;` hợp lệ); đối số method `takeByte(7)` thì không được hưởng ngoại lệ này. Phân biệt hai ngữ cảnh trước khi thêm cast.

## 4. Ngữ cảnh lời gọi phương thức: đối số phải hợp kiểu tham số

Method khai báo kiểu tham số. Khi gọi, Java xét kiểu biểu thức đối số để tìm method có thể áp dụng.

~~~java
static void acceptLong(long value) {
    System.out.println("long=" + value);
}

// Trong main:
byte b = 7;
acceptLong(b); // Hợp lệ: byte -> long
~~~

Đây là **widening primitive trong method invocation context**.

Ngược lại, **quy tắc gán hằng int vừa miền không tự áp dụng cho tham số byte**:

~~~java
static void acceptByte(byte value) {
    System.out.println("byte=" + value);
}

// acceptByte(7);       // Lỗi biên dịch: int không tự narrowing thành byte
acceptByte((byte) 7);   // Hợp lệ: cast tường minh
~~~

Phân biệt rõ:

| Cú pháp | Có biên dịch? | Vì sao |
|---|---|---|
| `byte b = 7;` | Có | Ngoại lệ narrowing constant expression trong phép gán |
| `acceptByte(7);` | Không | Method invocation không cho narrowing hằng int thành byte |
| `acceptByte((byte) 7);` | Có | Đối số sau cast đã có kiểu byte |
| `acceptLong(7);` | Có | int widening sang long |
| `acceptLong((byte) 7);` | Có | byte widening sang long |

**Cách viết tốt:** truyền đúng kiểu tham số bằng literal/hàm sinh giá trị tương ứng; chỉ dùng cast khi có lý do và đã xét miền giá trị.

## 5. Boxing và unboxing trong lời gọi method

Từ Java 5, Java hỗ trợ **autoboxing** và **unboxing** trong những ngữ cảnh thích hợp; Java 17 vẫn dùng cơ chế này.

~~~java
static void showNumber(Number number) {
    System.out.println(number.getClass().getSimpleName());
}

// Trong main:
showNumber(5); // In Integer
~~~

`5` ban đầu là `int`. Trong lời gọi trên, Java có thể boxing thành `Integer`, rồi widening reference thành `Number`.

Ngược lại:

~~~java
static void takeLong(long value) {
    System.out.println(value);
}

Integer boxed = Integer.valueOf(42);
takeLong(boxed); // Integer unboxing -> int, rồi widening thành long
~~~

**Bẫy null:** unboxing tham chiếu `null` gây `NullPointerException` lúc chạy:

~~~java
Integer missing = null;
// long value = missing; // Biên dịch được, nhưng chạy sẽ ném NullPointerException
~~~

Compiler kiểm tra kiểu chuyển đổi, không bảo đảm wrapper khác `null` tại runtime.

## 6. Overloading: chọn method trước khi thực thi chuyển đổi

Khi có nhiều method **cùng tên** nhưng khác tham số, compiler giải quyết overload dựa trên kiểu đối số và các giai đoạn tìm method phù hợp. Về cơ bản, Java ưu tiên xét những lời gọi áp dụng bằng các chuyển đổi *strict* như widening trước; nếu chưa tìm được thì mới xét những khả năng có boxing/unboxing, rồi các trường hợp varargs phù hợp.

~~~java
static void choose(long value) {
    System.out.println("choose(long)");
}

static void choose(Integer value) {
    System.out.println("choose(Integer)");
}
~~~

Với:

~~~java
choose(7);                  // choose(long)
choose(Integer.valueOf(7)); // choose(Integer)
~~~

Vì sao?

- `7` có kiểu **`int`**; `int -> long` là widening primitive, nên method `choose(long)` được chọn trong giai đoạn áp dụng strict.
- `Integer.valueOf(7)` có kiểu **`Integer`**; method `choose(Integer)` nhận đúng kiểu tham chiếu, được chọn trước khả năng unboxing rồi widening.

Đây không phải quy tắc đơn giản “luôn ưu tiên primitive”. **Kiểu đối số và các overload hiện diện** mới quyết định. Overload với nhiều tham số, subtype, varargs hoặc generic còn có các quy tắc lựa chọn cụ thể hơn.

**Thực hành:** tránh tạo bộ overload có ý nghĩa nghiệp vụ khác nhau chỉ vì boxing/ví dụ số hạng; API dễ gây hiểu nhầm khi người gọi đổi từ primitive sang wrapper.

## 7. Ngữ cảnh biểu thức: số học tự nâng kiểu

Trong biểu thức số học với các toán hạng số nguyên nhỏ, Java áp dụng **numeric promotion**. Cụ thể, `byte`, `short`, `char` thường được nâng thành `int` trước các phép cộng, trừ, nhân hoặc chia số học thông thường.

~~~java
byte a = 100;
byte b = 20;

int sum = a + b; // a+b có kiểu int
System.out.println(sum); // 120

// byte wrong = a + b; // Lỗi: kết quả biểu thức là int
byte castResult = (byte) (a + b); // Chỉ an toàn nếu giá trị vừa byte
~~~

Ngoại lệ gán hằng lại khác:

~~~java
byte constantSum = 100 + 20; // Hợp lệ vì đây là constant expression int = 120
~~~

**Hai dòng cùng ra 120 nhưng compiler xử lý khác nhau:**

- `byte constantSum = 100 + 20;`: biểu thức hằng, cho phép narrowing theo assignment context.
- `byte s = a + b;`: biểu thức dựa trên các biến byte, kết quả `int` và không tự narrowing về byte.

Khi có toán hạng `long`, `float` hoặc `double`, kết quả được tính theo những quy tắc nâng kiểu tương ứng. Ví dụ:

~~~java
double result = 3 + 0.5;
System.out.println(result); // 3.5
~~~

Một toán hạng kiểu `double` làm phép cộng được thực hiện theo dấu phẩy động double.

## 8. Bảng so sánh ba ngữ cảnh

| Tình huống | Code ví dụ | Hợp lệ? | Điểm cần nhớ |
|---|---|---|---|
| Gán widening | `long n = 7;` | Có | int → long |
| Gán hằng int vừa miền byte | `byte b = 7;` | Có | Quy tắc đặc biệt assignment |
| Gán biến int sang byte | `byte b = intValue;` | Thường không | Cần cast/kiểm tra |
| Gán boxed rồi widening reference | `Number n = 7;` | Có | int → Integer → Number |
| Gán int vào Long | `Long n = 7;` | Không | Không tự int → long → Long |
| Truyền int literal vào tham số byte | `acceptByte(7);` | Không | Method invocation không narrowing hằng |
| Truyền byte vào tham số long | `acceptLong((byte) 7);` | Có | Widening primitive |
| Gọi tham số Number bằng int | `acceptNumber(7);` | Có | Boxing + widening reference |
| Biểu thức byte + byte | `int s = a + b;` | Có | Numeric promotion ra int |
| Gán kết quả byte + byte vào byte | `byte s = a + b;` | Không | Không tự narrowing kết quả runtime |
| Gán biểu thức hằng vào byte | `byte s = 100 + 20;` | Có | Constant expression vừa miền |
| Compound assignment byte | `b += 10;` | Có | Có narrowing ngầm, có thể tràn |
| Nối String với số | `"n=" + 7` | Có | Kết quả String `"n=7"` |

Không phải mọi phép chuyển đổi chỉ thuộc đúng một ô trong bảng: một biểu thức có thể trải qua **numeric promotion trước**, rồi được **assignment conversion sau**. Hãy phân tích theo từng bước.

## 9. Chương trình Java 17 chạy độc lập

Tạo tệp đúng tên **`ConversionContextsDemo.java`**:

~~~java
public final class ConversionContextsDemo {
    private ConversionContextsDemo() {}

    static void acceptLong(long value) {
        System.out.println("call long=" + value);
    }

    static void acceptByte(byte value) {
        System.out.println("call byte=" + value);
    }

    static void acceptNumber(Number value) {
        System.out.println("call Number type="
                + value.getClass().getSimpleName());
    }

    static void choose(long value) {
        System.out.println("choose=long");
    }

    static void choose(Integer value) {
        System.out.println("choose=Integer");
    }

    public static void main(String[] args) {
        byte small = 7;
        long widened = small;
        System.out.println("assignment widening=" + widened);

        byte constant = 120;
        final int KNOWN = 12;
        byte fromFinalConstant = KNOWN;
        Byte boxedConstant = 9;
        System.out.println("assignment constant=" + constant);
        System.out.println("assignment final=" + fromFinalConstant);
        System.out.println("assignment boxed constant=" + boxedConstant);

        int runtime = 130;
        byte narrowed = (byte) runtime;
        System.out.println("assignment explicit cast=" + narrowed);

        Integer boxed = Integer.valueOf(42);
        long unboxedAndWidened = boxed;
        System.out.println("assignment unbox widen=" + unboxedAndWidened);
        Number asNumber = 17;
        System.out.println("assignment Number type="
                + asNumber.getClass().getSimpleName());
        Long fromLongLiteral = 17L;
        System.out.println("assignment Long=" + fromLongLiteral);

        acceptLong(small);
        acceptByte((byte) 7);
        acceptNumber(5);

        choose(7);
        choose(Integer.valueOf(7));

        byte a = 100;
        byte b = 20;
        int sum = a + b;
        byte constantSum = 100 + 20;
        System.out.println("expression promoted=" + sum);
        System.out.println("expression constant sum=" + constantSum);
        System.out.println("expression mixed=" + (3 + 0.5));

        long wrong = 2_000_000_000 + 2_000_000_000;
        long correct = 2_000_000_000L + 2_000_000_000;
        System.out.println("expression overflow int=" + wrong);
        System.out.println("expression safe long=" + correct);

        byte counter = 120;
        counter += 10;
        System.out.println("compound assignment=" + counter);

        System.out.println("concat=" + 1 + 2);
        System.out.println("parenthesized=" + (1 + 2));

        double fractional = 8.9;
        System.out.println("explicit double cast=" + (int) fractional);

        Integer missing = null;
        try {
            long shouldFail = missing;
            System.out.println(shouldFail);
        } catch (NullPointerException ex) {
            System.out.println("null unboxing=NullPointerException");
        }
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên, terminal mở tại thư mục chứa file. Không cần Maven, Gradle hay thư viện ngoài JDK.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 ConversionContextsDemo.java
java -cp . ConversionContextsDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\ConversionContextsDemo.java
java -cp . ConversionContextsDemo
~~~

**Expected output:**

~~~text
assignment widening=7
assignment constant=120
assignment final=12
assignment boxed constant=9
assignment explicit cast=-126
assignment unbox widen=42
assignment Number type=Integer
assignment Long=17
call long=7
call byte=7
call Number type=Integer
choose=long
choose=Integer
expression promoted=120
expression constant sum=120
expression mixed=3.5
expression overflow int=-294967296
expression safe long=4000000000
compound assignment=-126
concat=12
parenthesized=3
explicit double cast=8
null unboxing=NullPointerException
~~~

**Điểm quan trọng để đối chiếu:**

1. `byte constant = 120` và `byte fromFinalConstant = KNOWN` hợp lệ nhờ ngoại lệ hằng int trong assignment context.
2. `acceptByte((byte) 7)` cần cast vì method invocation không tự thu hẹp literal int thành byte.
3. `choose(7)` chọn `long` theo strict invocation; `choose(Integer.valueOf(7))` chọn `Integer`.
4. `a + b` có kiểu `int` dù hai biến là byte; `100 + 20` là constant expression có thể gán byte.
5. `wrong` cho kết quả tràn int trước khi gán long; `correct` dùng long ngay lúc cộng.
6. `counter += 10` có narrowing ngầm và biến thành -126.
7. `"concat=" + 1 + 2` nối String ra `12`, khác phép cộng số trong ngoặc.
8. `missing` là `Integer` null; unboxing lúc chạy gây `NullPointerException`, được catch theo chủ đích.

## 10. Bài tập và đáp án

### Bài A — cùng giá trị nhưng khác context

Giả sử có:

~~~java
static void takeByte(byte value) {}

byte a = 100;
takeByte(a);
// takeByte(100);
~~~

**Đáp án:** `byte a = 100` hợp lệ bởi ngoại lệ hằng int trong phép gán. `takeByte(a)` hợp lệ vì đối số đã có kiểu byte; `takeByte(100)` không hợp lệ vì method invocation không tự narrowing literal int. Có thể viết `takeByte((byte) 100)`.

### Bài B — hằng hay biến runtime?

~~~java
byte a = 100 + 20;
byte x = 100;
byte y = 20;
// byte b = x + y;
~~~

**Đáp án:** dòng đầu hợp lệ vì `100+20` là hằng int bằng 120. Dòng `byte b = x+y` lỗi vì toán tử cộng nâng cả byte lên int, kết quả không tự thu hẹp về byte.

### Bài C — boxing và widening

~~~java
Number a = 5;
Integer b = 5;
long c = b;
// Long d = 5;
~~~

**Đáp án:** `a` hợp lệ qua boxing `int -> Integer` rồi widening reference; `b` hợp lệ qua boxing; `c` hợp lệ qua unboxing rồi widening int → long. `Long d = 5` không hợp lệ; viết `5L`.

### Bài D — quá trình cộng xảy ra trước phép gán

~~~java
int x = 2_000_000_000;
long a = x + x;
long b = (long) x + x;
System.out.println(a);
System.out.println(b);
~~~

**Đáp án:**

~~~text
-294967296
4000000000
~~~

`a` nhận kết quả int đã tràn, còn `b` thực hiện phép cộng long vì toán hạng đầu đã được cast trước.

### Bài E — chọn overload

~~~java
static void choose(long x) { System.out.println("long"); }
static void choose(Integer x) { System.out.println("Integer"); }

// Trong main:
choose(3);
choose(Integer.valueOf(3));
~~~

**Đáp án:**

~~~text
long
Integer
~~~

### Bài F — gán kết hợp

~~~java
byte b = 127;
b += 1;
System.out.println(b);
~~~

**Đáp án:** `-128`. Toán tử `+=` có chuyển kiểu thu hẹp về byte; không tự báo overflow. Nếu không cho phép giá trị âm bất ngờ, hãy tính bằng int và kiểm tra miền trước.

## 11. Checklist tổng kết

- [ ] Phân biệt **assignment**, **method invocation** và **expression context**.
- [ ] Biết widening primitive cho phép gán hoặc truyền đối số khi kiểu tương thích.
- [ ] Biết ngoại lệ gán hằng int vừa miền vào byte/short/char.
- [ ] Hiểu ngoại lệ đó **không** đồng nghĩa method nhận byte cho phép truyền literal int tự động.
- [ ] Biết boxing/unboxing và rủi ro unboxing null.
- [ ] Hiểu `Number n = 5` hợp lệ nhưng `Long n = 5` không.
- [ ] Biết overload resolution có thể chọn widening trước boxing.
- [ ] Nhớ byte/short/char được nâng thành int trong phép tính số học.
- [ ] Phân biệt compound assignment với phép gán `=` thông thường.
- [ ] Xác định kiểu toán hạng **trước** khi cộng để tránh overflow.
- [ ] Không nhầm phép nối String với phép cộng số.
- [ ] Dùng cast sau khi kiểm tra giá trị và đúng yêu cầu nghiệp vụ.
- [ ] Chạy `ConversionContextsDemo.java` trên JDK 17+ và so từng dòng expected output.

**Kết luận:** Java quyết định chuyển đổi theo **nơi giá trị được sử dụng**. Phép gán cho phép một số narrowing hằng int đặc biệt; lời gọi method dùng quy tắc thích ứng kiểu tham số và phân giải overload; biểu thức lại có numeric promotion và ngữ nghĩa toán tử riêng. Muốn code đúng, hãy đọc **kiểu nguồn → ngữ cảnh → chuyển đổi → giá trị thực tế**, thay vì chỉ nhìn kiểu của biến nhận.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-conversion-009">Phép gán hằng nằm trong miền giá trị của byte, short hoặc char</a>

<details>
<summary>Xem chi tiết</summary>

# Phép gán hằng nằm trong miền giá trị của `byte`, `short` hoặc `char` — Java 17+

Trong Java, các literal số nguyên thông thường như `100` có kiểu `int`, nhưng đoạn mã **`byte b = 100;` vẫn biên dịch**. Lý do là Java cho phép **chuyển đổi thu hẹp đặc biệt trong ngữ cảnh gán** (*assignment context*) đối với một **biểu thức hằng tại thời điểm biên dịch** (*constant expression*), khi giá trị của nó nằm trọn trong miền biểu diễn của kiểu đích `byte`, `short` hoặc `char`.

Đây là quy tắc riêng trong Java, **không phải** chuyển đổi ngầm định từ mọi biến `int` sang `byte`.

~~~java
byte age = 100;        // Hop le: hang int 100 trong mien byte.
short count = 30_000;  // Hop le: hang int trong mien short.
char code = 65;        // Hop le: hang int 65 trong mien char ('A').

// byte bad = 128;     // LOI: 128 vuot Byte.MAX_VALUE.
// byte bad2 = -129;   // LOI: nho hon Byte.MIN_VALUE.
// short bad3 = 32768; // LOI: vuot Short.MAX_VALUE.
// char bad4 = -1;     // LOI: char khong dau.
~~~

## 1. Quy tắc chính xác

Trong **phép gán**, nếu biểu thức nguồn là *constant expression* có kiểu `byte`, `short`, `char` hoặc `int` và có **giá trị biểu diễn được** bằng kiểu đích `byte`, `short` hoặc `char`, Java có thể thực hiện chuyển đổi thu hẹp mà **không cần cast tường minh**.

| Kiểu đích | Miền giá trị | Gán hằng `int` hợp lệ | Gán hằng vượt miền |
| --- | --- | --- | --- |
| `byte` | −128 đến 127 | `byte b = 127;` | `byte b = 128;` |
| `short` | −32.768 đến 32.767 | `short s = 32_767;` | `short s = 32_768;` |
| `char` | 0 đến 65.535 | `char c = 65_535;` | `char c = 65_536;` hoặc `char c = -1;` |

**Hai điều kiện phải cùng đúng:** biểu thức phải là **hằng ở compile time** và **giá trị hằng nằm trong miền kiểu đích**.

Một biến `int` thông thường có giá trị 100 **không** thuộc ngoại lệ này:

~~~java
int value = 100;
// byte b = value;     // LOI bien dich: int -> byte can cast.
byte b = (byte) value; // Hop le; boi vi 100 nam trong mien nen b = 100.
~~~

Việc có cast không giúp compiler hoặc JVM tự bảo đảm giá trị vẫn nằm trong miền; nếu `value` thành 130, `(byte) value` cho `-126` do mất các bit cao.

## 2. Chương trình Java 17+ runnable

Tạo tệp UTF-8 **`ConstantAssignmentDemo.java`**:

~~~java
public class ConstantAssignmentDemo {
    static final int CLASS_LIMIT = 127;

    public static void main(String[] args) {
        byte maxByte = 127;
        byte minByte = -128;
        short maxShort = 32_767;
        short minShort = -32_768;
        char maxChar = 65_535;

        System.out.println("byteMax=" + maxByte);
        System.out.println("byteMin=" + minByte);
        System.out.println("shortMax=" + maxShort);
        System.out.println("shortMin=" + minShort);
        System.out.println("charMaxCode=" + (int) maxChar);

        byte foldedByte = 40 + 60;
        short foldedShort = 30_000 + 2_767;
        char nextLetter = 'A' + 1;
        System.out.println("foldedByte=" + foldedByte);
        System.out.println("foldedShort=" + foldedShort);
        System.out.println("letter=" + nextLetter);

        final int localConstant = 120;
        byte fromLocalFinal = localConstant;

        final int foldedConstant = 60 + 67;
        byte fromFoldedFinal = foldedConstant;
        byte fromClassFinal = CLASS_LIMIT;

        System.out.println("localFinal=" + fromLocalFinal);
        System.out.println("foldedFinal=" + fromFoldedFinal);
        System.out.println("classFinal=" + fromClassFinal);

        int variable = 120;
        byte castVariable = (byte) variable;

        final int runtimeFinal = Integer.parseInt("12");
        byte castRuntimeFinal = (byte) runtimeFinal;
        System.out.println("castVariable=" + castVariable);
        System.out.println("runtimeFinal=" + castRuntimeFinal);

        byte castOverflow = (byte) 128;
        short castShortOverflow = (short) 32_768;
        char castCharOverflow = (char) 65_536;
        System.out.println("castByteOverflow=" + castOverflow);
        System.out.println("castShortOverflow=" + castShortOverflow);
        System.out.println("castCharOverflow=" + (int) castCharOverflow);

        byte compounded = 100;
        compounded += 30;
        System.out.println("compoundAssignment=" + compounded);
    }
}
~~~

Tại thư mục chứa file, dùng **JDK 17 trở lên** trên PowerShell (Windows) hoặc Bash (Linux/macOS):

~~~bash
java -version
javac -version
javac -encoding UTF-8 ConstantAssignmentDemo.java
java ConstantAssignmentDemo
~~~

**Expected output chính xác của lệnh `java ConstantAssignmentDemo`:**

~~~text
byteMax=127
byteMin=-128
shortMax=32767
shortMin=-32768
charMaxCode=65535
foldedByte=100
foldedShort=32767
letter=B
localFinal=120
foldedFinal=127
classFinal=127
castVariable=120
runtimeFinal=12
castByteOverflow=-128
castShortOverflow=-32768
castCharOverflow=0
compoundAssignment=-126
~~~

Các lệnh kiểm tra phiên bản `java -version` và `javac -version` có đầu ra khác nhau tùy môi trường và không nằm trong khối expected output.

## 3. Tại sao biểu thức `40 + 60` vẫn gán được vào `byte`?

Khi tính `40 + 60`, cả hai literal mang kiểu `int` nên biểu thức có **kiểu `int`**. Tuy vậy, `40 + 60` là một **constant expression**: compiler tính được giá trị **100** trong lúc biên dịch. Vì 100 nằm trong miền `byte`, câu sau hợp lệ:

~~~java
byte result = 40 + 60; // 100: constant int nam trong mien byte.
~~~

Ngược lại, nếu dùng biến thông thường:

~~~java
int left = 40;
int right = 60;

// byte result = left + right; // LOI: ket qua la int, khong la hang.
int result = left + right;     // Hop le: int 100.
~~~

Ngay cả `byte + byte` thông thường cũng có kiểu kết quả `int` (theo *binary numeric promotion*):

~~~java
byte a = 40;
byte b = 60;
// byte sum = a + b; // LOI: int khong duoc tu thu hep ve byte.
int sum = a + b;    // 100.
~~~

**Không suy luận** rằng compiler nhìn thấy giá trị nhỏ thì sẽ tự gán vào kiểu hẹp. Nó chỉ áp dụng ngoại lệ đúng cho **biểu thức hằng thỏa điều kiện**.

## 4. `final int` có thể là *constant variable* — nhưng không phải lúc nào cũng vậy

Một **constant variable** trong Java là biến `final` có kiểu nguyên thủy hoặc `String` và được khởi tạo bằng một *constant expression*. Với bài toán gán hằng số nguyên, trường hợp điển hình là `final int`:

~~~java
final int FIXED = 120;
byte b = FIXED; // Hop le: FIXED la constant variable, gia tri vua byte.

final int CALCULATED = 60 + 67;
byte max = CALCULATED; // Hop le: 127 vua byte.
~~~

`static final int` cũng có thể là constant variable nếu initializer là constant expression:

~~~java
static final int BYTE_MAX = 127;

// Trong mot phuong thuc:
// byte b = BYTE_MAX; // Hop le.
~~~

Nhưng **từ khóa `final` một mình không đủ**:

~~~java
final int parsed = Integer.parseInt("12");
// byte b = parsed;        // LOI: parsed KHONG la constant variable.
byte b = (byte) parsed;    // Hop le, nhung phai can nhac mien gia tri.
~~~

Hàm `Integer.parseInt` chạy khi chương trình chạy, nên initializer trên **không phải constant expression**, bất kể giá trị trả về là 12. Ngược lại, các biểu thức có toán tử số học trên literal/hằng thích hợp như `60 + 67` có thể được compiler tính sẵn.

Tương tự, biến `final Integer boxed = 100;` không phải constant variable kiểu nguyên thủy, vì `Integer` là **kiểu tham chiếu**, không phải kiểu primitive hoặc `String`. Đừng dùng `final` và giá trị “tình cờ vừa miền” làm bằng chứng một biến tham gia ngoại lệ gán hằng.

## 5. Ranh giới miền: `byte`, `short` và `char`

### `byte` là số nguyên có dấu 8 bit

~~~java
byte smallest = -128;
byte largest = 127;

// byte under = -129; // Ngoai mien.
// byte over = 128;   // Ngoai mien.
~~~

Một phép cast vẫn được phép nhưng **không bảo toàn giá trị** khi số vượt miền:

~~~java
byte wrapped = (byte) 128; // -128.
~~~

Phép thu hẹp nguyên-sang-nguyên giữ **các bit thấp** phù hợp với kiểu đích; với `byte`, mẫu 8 bit được diễn giải có dấu. Java không “chặn” giá trị ở 127 và cũng không tự ném `ArithmeticException` khi cast.

### `short` là số nguyên có dấu 16 bit

~~~java
short min = -32_768;
short max = 32_767;
// short bad = 32_768; // Ngoai mien.
short wrapped = (short) 32_768; // -32768.
~~~

### `char` là đơn vị mã UTF-16 không dấu 16 bit

~~~java
char zero = 0;
char letter = 65;      // 'A'.
char max = 65_535;

// char negative = -1; // Ngoai mien char.
// char tooBig = 65_536; // Ngoai mien char.
char wrapped = (char) 65_536; // Co gia tri so 0.
~~~

`char` khác `short`: cùng có 16 bit nhưng `char` có miền 0–65535. Khi in trực tiếp `char`, terminal hiển thị **ký tự tương ứng** chứ không phải mã số. Để xem giá trị số, dùng `(int) letter` hoặc `(int) max`.

## 6. Vì sao `long` literal và `double` literal không áp dụng ngoại lệ?

Phép thu hẹp hằng trong ngữ cảnh gán primitive đích `byte`, `short` hoặc `char` chỉ cho phép **kiểu của constant expression nguồn** thuộc `byte`, `short`, `char` hoặc `int`. Vì vậy:

~~~java
byte a = 100;          // Hop le: constant int.
// byte b = 100L;       // LOI: constant long, du gia tri 100.
// byte c = 100.0;      // LOI: literal double.
byte b = (byte) 100L;   // Cast tuong minh.
byte c = (byte) 100.0;  // Cast tuong minh.
~~~

Nếu giá trị kiểu `long` thực tế nhỏ, điều đó **không** làm chuyển đổi `long -> byte` thành ngoại lệ gán hằng. Cast có thể cho phép đoạn mã chạy, nhưng chính bạn phải kiểm tra mất dữ liệu.

Khi dữ liệu đến từ đầu vào, API, biến hay kết quả tính toán lúc chạy, cách thực tế là chọn kiểu đủ rộng, kiểm tra miền trước khi chuyển hoặc dùng phương thức kiểm tra tràn thích hợp, ví dụ `Math.toIntExact` khi thu hẹp `long -> int`.

## 7. Phân biệt phép gán trực tiếp với `+=`

~~~java
byte b = 100;
// b = b + 30;  // LOI: b + 30 la int, khong la constant expression.
b += 30;        // Hop le; ket qua la -126.
~~~

`b += 30` hợp lệ **không phải nhờ ngoại lệ gán hằng**. Toán tử gán kết hợp có quy tắc riêng: cộng trước rồi **chuyển về kiểu của vế trái**. Với `byte b = 100`, phép cộng cho `int 130`, chuyển về `byte` thành `-126`.

Nếu giá trị nằm ngoài miền đích, đừng dùng cast hoặc `+=` như cách “lách lỗi compiler” khi nghiệp vụ yêu cầu số chính xác.

## 8. Bảng phân biệt hợp lệ / lỗi biên dịch

| Mã Java | Hợp lệ? | Lý do |
| --- | --- | --- |
| `byte b = 127;` | Có | Constant `int` vừa `byte` |
| `byte b = 128;` | Không | Constant `int` vượt miền `byte` |
| `short s = 30_000 + 2_767;` | Có | Constant expression `int 32767` vừa `short` |
| `char c = 'A' + 1;` | Có | Constant expression `int 66` vừa `char` |
| `char c = -1;` | Không | `char` không có số âm |
| `byte b = 100L;` | Không | Nguồn là `long`, không thuộc ngoại lệ |
| `int x = 100; byte b = x;` | Không | `x` không là constant variable |
| `final int X = 100; byte b = X;` | Có | `X` là constant variable primitive |
| `final int X = Integer.parseInt("100"); byte b = X;` | Không | Initializer không là constant expression |
| `byte b = (byte) 128;` | Có | Cast tường minh, nhưng kết quả là −128 |

Nếu thấy thông báo compiler kiểu *possible lossy conversion from int to byte*, hãy hỏi: biểu thức có phải hằng không? Giá trị có nằm trong miền đích không? Hay bạn đang dùng biến thông thường và thực sự cần giữ kiểu rộng hơn?

## 9. Bài tập tự kiểm tra

1. **Biên của `byte`:** `byte a = -128; byte b = 127;` hợp lệ không? `byte c = 128;` thì sao?  
   *Đáp án:* Hai câu đầu hợp lệ; câu cuối lỗi biên dịch.
2. **Hằng biểu thức:** `short s = 10_000 + 20_000;` có cần cast không?  
   *Đáp án:* Không. Biểu thức hằng `int 30000` nằm trong miền `short`.
3. **Biến thông thường:** `int x = 100; byte b = x;` có hợp lệ không?  
   *Đáp án:* Không; `x` không là constant variable, dù giá trị chạy thử là 100.
4. **Hằng `final`:** `final int X = 127; byte b = X;` có hợp lệ không? Nếu `X = 128`?  
   *Đáp án:* 127 hợp lệ; 128 vượt miền `byte` và bị compiler từ chối.
5. **`char`:** `char c = 65_535;` và `char d = 65_536;` thì sao?  
   *Đáp án:* `c` hợp lệ, `d` không; `char` không dấu 16 bit.
6. **`long` literal:** Tại sao `byte b = 100L;` không hợp lệ nhưng `byte b = 100;` hợp lệ?  
   *Đáp án:* Chuyển thu hẹp hằng trong ngữ cảnh gán không áp dụng cho nguồn kiểu `long`.
7. **Cast vượt miền:** `(byte) 128` cho bao nhiêu?  
   *Đáp án:* `-128` do giữ tám bit thấp và diễn giải có dấu.

## Checklist

- [ ] Xác định **kiểu nguồn của constant expression**, không chỉ giá trị.
- [ ] Kiểm tra giá trị nằm trong miền `byte`/`short`/`char`.
- [ ] Phân biệt literal hằng và biến `int` thông thường.
- [ ] `final int` chỉ tham gia như constant variable nếu initializer là constant expression.
- [ ] Biểu thức constant như `'A' + 1` và `40 + 60` có thể được compiler tính trước.
- [ ] `100L` và `100.0` không thuộc ngoại lệ gán hằng `int`.
- [ ] Cast hoặc gán `+=` có thể **làm mất dữ liệu**, không tự kiểm tra miền.
- [ ] Nếu không chắc kết quả nằm trong miền, ưu tiên kiểu rộng hơn hoặc kiểm tra miền trước khi chuyển.

**Kết luận:** `byte b = 100;` hợp lệ nhờ **ngoại lệ thu hẹp biểu thức hằng trong ngữ cảnh gán**, chứ không phải vì mọi `int` đều tự chuyển về `byte`. Compiler cần biết **giá trị tại thời điểm biên dịch**, và giá trị phải **nằm trong miền kiểu đích**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-conversion-010">Ép kiểu tham chiếu, instanceof và ClassCastException</a>

<details>
<summary>Xem chi tiết</summary>

# Ép kiểu tham chiếu, `instanceof` và `ClassCastException` trong Java

> **Java core — language-basics / Chuyển đổi kiểu**  
> **Yêu cầu:** JDK 17+; một tệp Java độc lập, không cần thư viện ngoài.  
> **Mục tiêu:** Phân biệt *upcasting* và *downcasting* tham chiếu, hiểu **kiểu khai báo** và **kiểu thực tế** của đối tượng, kiểm tra bằng `instanceof` (bao gồm *pattern matching* Java 17), và nhận diện/né tránh `ClassCastException` khi ép kiểu không hợp lệ lúc chạy.

## 1. Ép kiểu tham chiếu không biến đối tượng thành đối tượng khác

Xét hệ phân cấp:

~~~java
abstract class Animal {
    abstract String sound();
}
class Dog extends Animal {
    @Override String sound() { return "woof"; }
    void fetch() { /* chỉ Dog có hành vi này */ }
}
class Cat extends Animal {
    @Override String sound() { return "meow"; }
}
~~~

Một biến kiểu `Animal` có thể tham chiếu một đối tượng thật sự thuộc `Dog`:

~~~java
Animal pet = new Dog(); // Upcast ngầm từ Dog lên Animal.
~~~

Phân biệt rõ hai khái niệm:

- **Kiểu khai báo / static type:** `Animal` là kiểu compiler nhìn thấy cho biến `pet`. Vì vậy gọi `pet.sound()` được, nhưng `pet.fetch()` **không** biên dịch vì `Animal` không khai báo phương thức `fetch()`.
- **Kiểu thực tế / runtime class:** object mà `pet` trỏ đến vẫn là một `Dog`. Java dispatch `pet.sound()` tới bản ghi đè của `Dog`.
- **Ép kiểu tham chiếu:** thay cách compiler xem một tham chiếu theo quan hệ kiểu cho phép; **không tạo, sao chép, thay class hoặc thay dữ liệu** của object.

Nếu cần phương thức riêng của `Dog` và biết chắc object thật sự là Dog:

~~~java
Dog dog = (Dog) pet; // Downcast tường minh.
dog.fetch();
~~~

Sau cast, `pet` và `dog` vẫn trỏ tới **cùng object Dog**. Việc ép kiểu này không giống `Integer.parseInt` hay chuyển đổi chuỗi thành số: nó không chuyển dữ liệu bên trong đối tượng.

## 2. Upcast và downcast khác nhau thế nào?

| Trường hợp | Ví dụ | Có cần cast tường minh? | Kiểm tra lúc chạy |
| --- | --- | --- | --- |
| **Upcast** lớp con → lớp cha | `Animal a = new Dog();` | Không | An toàn theo quan hệ kế thừa |
| Upcast đến interface được thực thi | `Runnable r = new Thread();` | Không | An toàn theo quan hệ implements |
| **Downcast** cha → con | `Dog d = (Dog) a;` | Có | Object thực tế phải tương thích với Dog |
| Từ `Object` → lớp cụ thể | `String s = (String) obj;` | Có | Object thực tế phải là String (hoặc subtype phù hợp) |
| Cast `null` sang kiểu tham chiếu | `Dog d = (Dog) null;` | Có thể viết | Kết quả vẫn là `null`, **không ném `ClassCastException`** |

Cast từ kiểu cha xuống kiểu con **có thể biên dịch** mà vẫn lỗi lúc chạy. Compiler chỉ chứng minh phép chuyển đổi *có thể* có ý nghĩa, không biết chắc runtime class của mọi object biến đó có thể trỏ tới.

Ví dụ:

~~~java
Animal pet = new Cat();
// Dog wrong = (Dog) pet; // Nếu chạy: ClassCastException.
~~~

`Cat` là một `Animal`, **không** phải một `Dog`; ép tham chiếu sang Dog không thể thay đổi sự thật đó.

Ở chiều ngược lại, một số phép ép giữa các lớp hoàn toàn không có quan hệ chuyển kiểu khả dĩ bị **từ chối ngay tại thời điểm biên dịch**, thay vì đợi đến `ClassCastException`. Không phải mọi cú pháp `(Type) value` đều hợp lệ.

## 3. `instanceof` kiểm tra kiểu runtime trước khi downcast

Toán tử `instanceof` hỏi liệu object thật sự được tham chiếu có tương thích với kiểu được kiểm tra hay không:

~~~java
Animal pet = new Dog();

System.out.println(pet instanceof Dog); // true
System.out.println(pet instanceof Cat); // false
System.out.println(pet instanceof Animal); // true
~~~

**Java 17** hỗ trợ *pattern matching for instanceof*, gộp kiểm tra với khai báo biến đã được thu hẹp kiểu:

~~~java
if (pet instanceof Dog dog) {
    dog.fetch(); // Compiler biết dog có kiểu Dog.
}
~~~

Điều này rõ ràng hơn mẫu cũ:

~~~java
if (pet instanceof Dog) {
    Dog dog = (Dog) pet;
    dog.fetch();
}
~~~

Phạm vi của **pattern variable** phụ thuộc luồng điều khiển: trong khối `if` trên, `dog` chỉ dùng được tại nhánh mà kiểm tra chắc chắn thành công. Đừng dùng tên đó bên ngoài vị trí compiler bảo đảm đã được gán.

**Lưu ý về `null`:** `null instanceof Dog` và `variableNull instanceof Dog` đều cho **`false`**; cú pháp `instanceof` không gây NPE chỉ vì toán hạng trái bằng `null`. Nếu một tham chiếu `null` được ép sang kiểu tham chiếu hợp lệ, kết quả vẫn là `null`; lỗi NPE chỉ có thể xảy ra **khi sau đó cố sử dụng nó như một object**.

## 4. Chương trình Java 17+ có thể biên dịch và chạy

Tạo tệp **ReferenceCastDemo.java**:

~~~java
public final class ReferenceCastDemo {
    private abstract static class Animal {
        private final String name;

        Animal(String name) {
            this.name = name;
        }

        String name() {
            return name;
        }

        abstract String sound();
    }

    private static final class Dog extends Animal {
        Dog(String name) {
            super(name);
        }

        @Override
        String sound() {
            return "woof";
        }

        String fetch() {
            return name() + " fetched";
        }
    }

    private static final class Cat extends Animal {
        Cat(String name) {
            super(name);
        }

        @Override
        String sound() {
            return "meow";
        }
    }

    private static String describe(Animal animal) {
        if (animal instanceof Dog dog) {
            return "dog:" + dog.name();
        }
        if (animal instanceof Cat cat) {
            return "cat:" + cat.name();
        }
        return "none";
    }

    public static void main(String[] args) {
        Animal pet = new Dog("Milo"); // Upcast.
        System.out.println("runtimeType="
                + pet.getClass().getSimpleName());
        System.out.println("polymorphicSound=" + pet.sound());
        System.out.println("isDog=" + (pet instanceof Dog));
        System.out.println("isCat=" + (pet instanceof Cat));

        Dog dog = (Dog) pet; // Downcast hợp lệ.
        System.out.println("sameObject=" + (pet == dog));
        System.out.println("fetch=" + dog.fetch());
        System.out.println("describeDog=" + describe(pet));

        Animal another = new Cat("Luna");
        System.out.println("catSound=" + another.sound());
        System.out.println("describeCat=" + describe(another));

        try {
            Dog wrong = (Dog) another; // Runtime là Cat.
            System.out.println(wrong.fetch());
        } catch (ClassCastException ex) {
            System.out.println("animalCastFailed=true");
        }

        Object number = Integer.valueOf(7);
        System.out.println("objectIsString="
                + (number instanceof String));
        try {
            String wrong = (String) number;
            System.out.println("unexpected=" + wrong);
        } catch (ClassCastException ex) {
            System.out.println("objectCastFailed=true");
        }

        Animal absent = null;
        System.out.println("nullInstanceof=" + (absent instanceof Dog));
        Dog nullDog = (Dog) absent; // Cast null tham chiếu hợp lệ.
        System.out.println("nullCast=" + (nullDog == null));
        System.out.println("describeNull=" + describe(absent));
    }
}
~~~

Mở terminal tại thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 ReferenceCastDemo.java
java ReferenceCastDemo
~~~

**Expected output chính xác:**

~~~text
runtimeType=Dog
polymorphicSound=woof
isDog=true
isCat=false
sameObject=true
fetch=Milo fetched
describeDog=dog:Milo
catSound=meow
describeCat=cat:Luna
animalCastFailed=true
objectIsString=false
objectCastFailed=true
nullInstanceof=false
nullCast=true
describeNull=none
~~~

Giải thích:

1. `pet` khai báo `Animal` nhưng runtime class là `Dog`; gọi `pet.sound()` chạy phiên bản `Dog.sound()` nhờ đa hình.
2. `(Dog) pet` thành công và `pet == dog` là `true` vì cast **không tạo object mới**.
3. `describe` dùng pattern matching `instanceof` để kiểm tra kiểu và có sẵn biến `dog`/`cat` đúng kiểu mà không cần cast riêng.
4. `(Dog) another` biên dịch vì biến `another` có kiểu `Animal`, nhưng runtime là `Cat` nên ném `ClassCastException`.
5. `number` thật sự chứa `Integer`. `number instanceof String` cho false; `(String) number` ném `ClassCastException`.
6. `absent` bằng `null`: `instanceof` cho false, cast sang `Dog` vẫn nhận `null`. Truy cập phương thức qua tham chiếu ấy mới có nguy cơ gây `NullPointerException`.

Các khối `catch` dùng **duy nhất để minh họa lỗi kiểm tra runtime**; trong code sản xuất, ưu tiên kiểm tra, thiết kế API hoặc đa hình phù hợp thay vì dùng exception làm điều kiện phân nhánh thường xuyên.

## 5. Phân biệt `ClassCastException` và những lỗi gần giống

| Hiện tượng | Loại lỗi / hành vi | Lý do |
| --- | --- | --- |
| `Animal x = new Cat(); Dog d = (Dog) x;` | **ClassCastException** lúc chạy | Object runtime là Cat, không phải Dog |
| `Object x = Integer.valueOf(7); String s = (String) x;` | **ClassCastException** lúc chạy | Integer không thể xem là String |
| `Dog d = (Dog) null;` | Thành công, `d == null` | Cast null không phát sinh ClassCastException |
| `d.fetch()` khi `d == null` | **NullPointerException** | Gọi method trên tham chiếu null |
| `Animal a = new Dog(); a.fetch();` | **Lỗi biên dịch** | Kiểu khai báo Animal không có `fetch()` |
| `String s = (String) new Dog(...);` với lớp không liên quan | **Lỗi biên dịch** | Không tồn tại cast được Java chấp nhận giữa hai kiểu lớp đó |
| Gán `Dog[]` cho `Animal[]` rồi lưu một `Cat` | Có thể **ArrayStoreException** | Runtime kiểm tra loại phần tử mảng, không phải phép ép tham chiếu trực tiếp |

`ClassCastException` phản ánh **một tham chiếu không tương thích với kiểu mà chương trình muốn ép**; NPE nói rằng không có object tại tham chiếu được sử dụng. Hai lỗi có nguyên nhân và cách sửa khác nhau.

## 6. Khi nào nên cast, khi nào tránh cast?

**Ưu tiên đa hình** nếu hành vi có thể được mô tả bởi kiểu cha hoặc interface:

~~~java
Animal pet = new Dog("Milo");
System.out.println(pet.sound()); // Không cần biết subtype.
~~~

Chỉ downcast khi thật sự cần API đặc thù của subtype và điều kiện tương thích được kiểm tra:

~~~java
if (pet instanceof Dog dog) {
    System.out.println(dog.fetch());
}
~~~

Một số nguyên tắc:

- **Dựa vào hợp đồng kiểu:** API trả `Animal` không có nghĩa đối tượng luôn là `Dog`. Đừng cast chỉ vì “test thử thấy chạy”.
- **Không bắt `ClassCastException` để thay thế `instanceof`** trong luồng bình thường; bắt exception phù hợp khi xử lý lỗi bất thường hoặc ở ranh giới cần kiểm soát lỗi.
- **Dùng pattern matching đúng mức:** `instanceof Dog dog` gọn hơn kiểm tra rồi cast lặp, nhưng dùng lạm dụng nhiều điều kiện subtype có thể báo hiệu thiết kế cần đa hình/strategy.
- **Đừng nhầm cast với chuyển đổi giá trị:** `(String) Integer.valueOf(7)` không phải cách biến số thành chuỗi; dùng `String.valueOf(7)` nếu thật sự muốn chuyển nội dung.
- **Lưu ý generic type erasure:** không thể dùng `obj instanceof java.util.List<String>` để kiểm tra đầy đủ tham số kiểu ở runtime; việc kiểm tra `instanceof List<?>` chỉ xác nhận lớp/kiểu gốc, không xác nhận mọi phần tử là String.

## 7. Bài tập tự luyện

**Bài 1.** Cho `Animal a = new Dog("Max");`. Tại sao `a.sound()` hợp lệ còn `a.fetch()` không biên dịch? Viết một cách gọi `fetch()` an toàn.

**Bài 2.** Cho `Animal animal = new Cat("Miu");`. Dự đoán `animal instanceof Dog` và hành vi của `(Dog) animal`; chỉ ra thời điểm lỗi xảy ra.

**Bài 3.** Thay `Animal animal = null;` trong phép kiểm tra `instanceof`. Kết quả là gì? Vì sao `(Dog) animal` tự nó không phát sinh NPE hoặc ClassCastException?

**Bài 4.** Cho `Object obj = Integer.valueOf(5);`. So sánh hành vi `obj instanceof String`, `(String) obj` và `String.valueOf(obj)`.

**Bài 5.** Sửa hàm `describe` trong chương trình để dùng mẫu `instanceof` cho subtype; nếu truyền object là Dog, Cat hoặc null, kết quả in ra phải tương ứng.

### Ghi nhớ

**Ép kiểu tham chiếu thay đổi cách nhìn kiểu của biến, không thay đổi object.** Upcast thường an toàn và không cần cast. Downcast yêu cầu object runtime tương thích, nếu không sẽ ném **`ClassCastException`**. Dùng **`instanceof`** hoặc pattern matching Java 17 để kiểm tra khi cần API của subtype, nhớ rằng **`null instanceof Type` là false** và **cast null tham chiếu vẫn cho null**. Khi chỉ cần hành vi chung, hãy ưu tiên **đa hình** thay vì cast.


</details>

- [Quay lại đầu trang](#back-to-top)
