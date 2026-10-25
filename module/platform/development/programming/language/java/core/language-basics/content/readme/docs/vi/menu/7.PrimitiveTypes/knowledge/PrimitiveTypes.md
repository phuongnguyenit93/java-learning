<a id="back-to-top"></a>

# Kiểu nguyên thủy

## Menu
- [Toàn cảnh tám kiểu nguyên thủy trong Java](#primitive-types-001)
- [byte, short, int và long: độ rộng và miền giá trị](#primitive-types-002)
- [Số nguyên có dấu và biểu diễn bù hai](#primitive-types-003)
- [char là đơn vị mã UTF-16 không dấu](#primitive-types-004)
- [float và double theo mô hình dấu phẩy động nhị phân](#primitive-types-005)
- [boolean chỉ có true và false](#primitive-types-006)
- [Phân biệt kiểu nguyên thủy với kiểu tham chiếu](#primitive-types-007)
- [Chọn kiểu theo miền dữ liệu và yêu cầu tính toán](#primitive-types-008)
- [Giới hạn giá trị với MIN_VALUE, MAX_VALUE và SIZE](#primitive-types-009)
- [Nhận diện vấn đề tràn số và độ chính xác trước khi viết phép tính](#primitive-types-010)

## <a id="primitive-types-001">Toàn cảnh tám kiểu nguyên thủy trong Java</a>

<details>
<summary>Xem chi tiết</summary>

# Toàn cảnh tám kiểu nguyên thủy trong Java (Java 17+)

Java có **tám kiểu nguyên thủy** (*primitive types*): `byte`, `short`, `int`, `long`, `float`, `double`, `char` và `boolean`. Chúng biểu diễn các giá trị cơ bản, khác với kiểu tham chiếu như `String`, mảng hoặc đối tượng. Nắm được miền giá trị, literal và cách các kiểu tương tác giúp tránh tràn số, mất chính xác và lỗi gán kiểu.

## 1. Bảng tổng quan

| Kiểu | Kích thước, miền giá trị | Ví dụ | Công dụng |
| --- | --- | --- | --- |
| `byte` | 8 bit, −128 đến 127 | `byte a = -5;` | Dữ liệu số nhỏ, byte nhị phân |
| `short` | 16 bit, −32.768 đến 32.767 | `short b = 32000;` | Tương thích định dạng cần số nguyên 16 bit |
| `int` | 32 bit, −2.147.483.648 đến 2.147.483.647 | `int c = 42;` | Số nguyên thông thường |
| `long` | 64 bit, −9.223.372.036.854.775.808 đến 9.223.372.036.854.775.807 | `long d = 9_000_000_000L;` | Số nguyên vượt miền `int` |
| `float` | 32 bit IEEE 754, khoảng 6–7 chữ số thập phân có nghĩa | `float e = 1.25F;` | Số thực cần ít dung lượng/độ chính xác hơn |
| `double` | 64 bit IEEE 754, khoảng 15–16 chữ số có nghĩa | `double f = 2.5;` | Số thực mặc định |
| `char` | 16 bit không dấu, 0–65.535 | `char g = 'A';` | Một **đơn vị mã UTF-16** |
| `boolean` | Hai giá trị `true`, `false` | `boolean h = true;` | Logic điều kiện |

Bốn kiểu `byte`, `short`, `int`, `long` là **số nguyên có dấu**. `char` là đơn vị mã 16 bit **không dấu**, không phải mọi ký tự Unicode đầy đủ đều gói được trong một `char`: ví dụ emoji ngoài BMP cần hai `char` khi mã hóa UTF-16.

`float` và `double` lưu giá trị **xấp xỉ**, hỗ trợ cả `NaN`, `Infinity` và `-0.0`. Riêng `boolean` **không** nhận số `0` hoặc `1` thay cho `false`/`true`. Java không quy định kích thước lưu trữ cố định của `boolean`; số bit trong bảng là độ rộng giá trị theo ngôn ngữ, **không phải cam kết số byte RAM cho mọi biến**.

## 2. Ví dụ Java 17+ có thể chạy ngay

Tạo tệp UTF-8 tên **`PrimitiveTypesDemo.java`**:

~~~java
public class PrimitiveTypesDemo {
    static int defaultNumber;       // Field int mac dinh 0.
    static boolean defaultFlag;     // Field boolean mac dinh false.
    static char defaultCharacter;   // Field char mac dinh ma 0.

    public static void main(String[] args) {
        byte smallest = -128;
        short tickets = 30_000;
        int population = 2_000_000_000;
        long distance = 9_000_000_000L;
        float rate = 1.25F;
        double price = 2.5D;
        char grade = 'A';
        boolean active = true;

        System.out.println("byte=" + smallest);
        System.out.println("short=" + tickets);
        System.out.println("int=" + population);
        System.out.println("long=" + distance);
        System.out.println("float=" + rate);
        System.out.println("double=" + price);
        System.out.println("char=" + grade + ", code=" + (int) grade);
        System.out.println("boolean=" + active);

        System.out.println("defaultInt=" + defaultNumber);
        System.out.println("defaultBoolean=" + defaultFlag);
        System.out.println("defaultCharCode=" + (int) defaultCharacter);

        int sum = smallest + tickets;
        System.out.println("promotedSum=" + sum);
        System.out.println("intDivision=" + (5 / 2));
        System.out.println("doubleDivision=" + (5 / 2.0));
        System.out.println("overflow=" + (Integer.MAX_VALUE + 1));
        System.out.println("narrowByte=" + (byte) 130);
        System.out.println("decimalEqual=" + (0.1 + 0.2 == 0.3));
    }
}
~~~

Mở PowerShell (Windows) hoặc Bash (Linux/macOS) tại thư mục chứa tệp:

~~~bash
java -version
javac -version
javac -encoding UTF-8 PrimitiveTypesDemo.java
java PrimitiveTypesDemo
~~~

**Expected output:**

~~~text
byte=-128
short=30000
int=2000000000
long=9000000000
float=1.25
double=2.5
char=A, code=65
boolean=true
defaultInt=0
defaultBoolean=false
defaultCharCode=0
promotedSum=29872
intDivision=2
doubleDivision=2.5
overflow=-2147483648
narrowByte=-126
decimalEqual=false
~~~

Lý giải các dòng cần chú ý:

- `9_000_000_000L` có hậu tố `L` để mang kiểu `long`; `1.25F` có `F` để mang kiểu `float`; `2.5D` là `double`, và `D` có thể bỏ.
- `'A'` là `char`, ép sang `int` cho mã UTF-16 `65`. Ép kiểu `(int)` ở đây chỉ để hiển thị mã số.
- `smallest + tickets` được **nâng kiểu thành `int`**, tính `-128 + 30000 = 29872`.
- `5 / 2` chia số nguyên thành `2`; `5 / 2.0` chia số thực thành `2.5`.
- `Integer.MAX_VALUE + 1` tràn số nguyên `int` về `-2147483648`; ép `130` xuống `byte` cho `-126`.
- `0.1 + 0.2 == 0.3` là `false` vì số thực nhị phân không biểu diễn chính xác nhiều phân số thập phân.

## 3. Literal và phép chuyển kiểu

### Viết literal đúng kiểu

~~~java
int days = 7;                   // int
long distance = 5_000_000_000L; // long
float portion = 0.5F;           // float
double ratio = 0.5;             // double
char separator = ':';           // char
boolean valid = false;          // boolean
~~~

Literal nguyên thập phân như `7` mặc định có kiểu `int` (nếu nằm trong miền); literal số thực như `0.5` mặc định có kiểu `double`. Lỗi phổ biến:

~~~java
// float x = 0.5;         // Sai: double khong tu thu hep thanh float.
float x = 0.5F;
// long y = 5000000000;   // Sai: literal nguyen khong co L vuot mien int.
long y = 5_000_000_000L;
~~~

Java còn cho phép literal hệ 16, hệ 2, ví dụ `0xFF` và `0b1010`. Dấu gạch dưới như `1_000_000` giúp chia nhóm chữ số tại vị trí hợp lệ nhưng không làm thay đổi giá trị.

### Widening (mở rộng) và narrowing (thu hẹp)

Một số phép chuyển đổi số học thông dụng:

~~~text
byte → short → int → long → float → double
               ↑
             char
~~~

Chuyển `int` sang `long` thường không mất giá trị. Nhưng **mở rộng kiểu không luôn bảo toàn độ chính xác**: một số `long` lớn không được `float` hoặc `double` biểu diễn nguyên vẹn. Ngược lại, thu hẹp như `int` sang `byte` có thể thay đổi giá trị:

~~~java
int original = 130;
byte narrowed = (byte) original; // -126
~~~

Phép gán hằng số nguyên đặc biệt được phép khi nằm trong miền, ví dụ `byte b = 100;` dù literal `100` có kiểu `int`. Nhưng biến `int` nói chung không tự thu hẹp về `byte`:

~~~java
int n = 100;
// byte b = n; // Sai neu khong ep kieu.
byte b = (byte) n;
~~~

### Integer promotion trong biểu thức

Với toán tử số học thông thường, `byte`, `short` và `char` được nâng thành `int` trước phép tính:

~~~java
byte left = 10, right = 20;
int sum = left + right;        // Dung.
// byte sum2 = left + right;  // Loi bien dich: ket qua la int.
~~~

**Kiểu của biến ở vế trái không tự đổi kiểu của phép tính.** Vì vậy `double answer = 5 / 2;` cho `2.0`, không phải `2.5`.

## 4. Giá trị mặc định và lỗi chưa khởi tạo

**Field** của đối tượng/lớp nhận giá trị mặc định nếu chưa khởi tạo tường minh: số nguyên `0`, số thực `0.0`, `char` là mã 0, `boolean` là `false`. Đây là lý do ba static fields trong chương trình ví dụ có thể được đọc ngay.

**Biến cục bộ** thì khác:

~~~java
int count;
// System.out.println(count); // Loi bien dich: count chua duoc gan.
count = 3;
System.out.println(count); // 3
~~~

Java yêu cầu biến cục bộ được gán chắc chắn trước khi đọc; không có quy tắc mọi biến primitive tự nhận `0` khi vừa khai báo.

Không primitive nào nhận `null` trực tiếp:

~~~java
// int n = null;         // Sai.
// boolean b = null;     // Sai.
Integer boxed = null;    // Dung: Integer la kieu tham chieu.
~~~

Các lớp bao tương ứng như `Integer`, `Long`, `Double` và `Boolean` là **wrapper/reference types**, không phải tám primitive types. Khi unboxing wrapper đang null về primitive sẽ ném `NullPointerException` khi chạy.


## 5. Từ giá trị cần lưu đến kiểu nên chọn

Bảng tám kiểu phía trên giúp nhận diện khả năng biểu diễn; khi lập trình, còn cần xét **giá trị lớn nhất của kết quả trung gian** và **độ chính xác mong muốn**:

- Với số đếm, thường bắt đầu từ `int`; dùng `long` khi tổng/tích có thể vượt giới hạn `int`. Chuyển toán hạng sang `long` **trước phép tính**, vì biến nhận kiểu `long` không sửa được kết quả `int` đã tràn.
- Với đại lượng đo gần đúng, `double` thường phù hợp; `float` chỉ khi dữ liệu hoặc API yêu cầu 32 bit. Không dùng số thực nhị phân nếu phép tính thập phân phải chính xác tuyệt đối: khi cần, xét `BigDecimal` hoặc đơn vị nguyên nhỏ nhất.
- `byte`/`short` chủ yếu hữu ích khi định dạng dữ liệu yêu cầu đúng độ rộng; `boolean` dùng cho điều kiện; `char` dành cho **một code unit UTF-16**, còn văn bản tổng quát dùng `String`.

Đây chỉ là bản đồ định hướng. Các bài về số nguyên, dấu phẩy động, Unicode và lựa chọn kiểu sẽ lần lượt phân tích điều kiện, rủi ro và cách tự kiểm chứng bằng mã nguồn.

## 6. Bài tập tự kiểm tra

1. **Phân loại tám kiểu:** Kể tên bốn kiểu nguyên có dấu, hai kiểu số thực, kiểu ký tự và kiểu logic.  
   *Đáp án:* `byte`/`short`/`int`/`long`; `float`/`double`; `char`; `boolean`.
2. **Dự đoán:** `double x = 7 / 2;` và `double y = 7 / 2.0;` cho kết quả gì?  
   *Đáp án:* `x = 3.0`, `y = 3.5`.
3. **Giải thích:** Vì sao `float f = 0.25;` không hợp lệ nhưng `float f = 0.25F;` hợp lệ?  
   *Đáp án:* `0.25` là literal `double`; `F` tạo literal `float`.
4. **Tràn số:** Dự đoán `(byte) 130`.  
   *Đáp án:* `-126` do chuyển về giá trị 8 bit có dấu.
5. **Biến cục bộ:** Vì sao `int x; System.out.println(x);` trong phương thức không biên dịch, nhưng đọc một field `int` chưa khai báo initializer được?  
   *Đáp án:* Local variable không có giá trị mặc định; field có giá trị mặc định `0`.
6. **Unicode:** Có thể chứa trọn emoji ngoài BMP trong một `char` không?  
   *Đáp án:* Không; nó cần hai đơn vị mã UTF-16. Dùng `String` khi xử lý văn bản.

## Ghi nhớ

Java 17 có **đúng tám primitive types**. `int` và `double` thường là lựa chọn mặc định cho số nguyên và số thực; `boolean` không phải số; `char` là **một UTF-16 code unit**. Hãy kiểm tra miền giá trị, hậu tố literal, quy tắc nâng/ép kiểu, tràn số và sai số dấu phẩy động **trước khi** quyết định kiểu dữ liệu cho bài toán.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="primitive-types-002">byte, short, int và long: độ rộng và miền giá trị</a>

<details>
<summary>Xem chi tiết</summary>

# `byte`, `short`, `int` và `long`: độ rộng và miền giá trị trong Java 17+

## 1. Bốn kiểu số nguyên có dấu

Java có bốn **kiểu nguyên thủy số nguyên có dấu** (*signed integral primitive types*) thường dùng để lưu số lượng, chỉ số, mã trạng thái và các đại lượng nguyên: `byte`, `short`, `int`, `long`.

Độ rộng và miền giá trị của chúng được **ngôn ngữ Java quy định cố định**; không đổi theo Windows/Linux hay JVM 32-bit/64-bit.

| Kiểu | Độ rộng giá trị | Nhỏ nhất | Lớn nhất | Hằng giới hạn |
|---|---:|---:|---:|---|
| `byte` | 8 bit | -128 | 127 | `Byte.MIN_VALUE`, `Byte.MAX_VALUE` |
| `short` | 16 bit | -32.768 | 32.767 | `Short.MIN_VALUE`, `Short.MAX_VALUE` |
| `int` | 32 bit | -2.147.483.648 | 2.147.483.647 | `Integer.MIN_VALUE`, `Integer.MAX_VALUE` |
| `long` | 64 bit | -9.223.372.036.854.775.808 | 9.223.372.036.854.775.807 | `Long.MIN_VALUE`, `Long.MAX_VALUE` |

Mỗi kiểu có `n` bit và biểu diễn số nguyên có dấu theo **bù hai** (*two's complement*), nên miền giá trị là:

\[
-2^{n-1} \;\leq\; x \;\leq\; 2^{n-1}-1.
\]

Ví dụ `byte` có 8 bit: giá trị nhỏ nhất \(-2^7=-128\), lớn nhất \(2^7-1=127\). Phía âm nhiều hơn phía dương **một** giá trị; đây là lý do `Math.abs(Integer.MIN_VALUE)` không cho ra số dương trong `int`.

**Chú ý:** “8 bit” mô tả **độ rộng giá trị của kiểu**, không phải cam kết rằng mọi biến `byte` trong mọi object chiếm đúng 1 byte bộ nhớ thực tế sau khi JVM căn chỉnh, đóng gói object hoặc tối ưu.

## 2. Khai báo và đọc các giới hạn

~~~java
byte temperature = -20;
short altitudeMeters = 2_500;
int population = 1_000_000;
long distanceMeters = 12_000_000_000L;
~~~

Một số điểm cần chú ý:

- Dấu `_` giữa chữ số chỉ giúp dễ đọc: `1_000_000` bằng `1000000`.
- Literal thập phân như `1_000_000` **mặc định là `int`**, miễn nằm trong miền hợp lệ.
- Với giá trị thập phân lớn hơn `Integer.MAX_VALUE`, phải dùng hậu tố `L` để tạo **literal `long`**: `12_000_000_000L`. Nên dùng chữ **`L` hoa**, vì `l` thường dễ nhìn nhầm thành số 1.
- Biến kiểu `long` **không sửa được** literal `int` đã quá giới hạn. Ví dụ `long x = 3_000_000_000;` không biên dịch, trong khi `long x = 3_000_000_000L;` hợp lệ.

Các class wrapper `Byte`, `Short`, `Integer`, `Long` có sẵn các hằng `MIN_VALUE` và `MAX_VALUE` để tránh nhập nhầm các con số dài.


## 3. Có dấu trong dữ liệu nhị phân

Cùng mẫu bit, kết quả đọc có thể khác tùy cách diễn giải. Bốn kiểu số nguyên đang xét là kiểu **có dấu**. Chẳng hạn, `byte` lưu 8 bit `11111111` được hiểu là `-1` theo bù hai, trong khi dữ liệu giao thức có thể xem cùng tám bit là `255`:

~~~java
byte payload = (byte) 0xFF;
System.out.println(payload);                    // -1
System.out.println(Byte.toUnsignedInt(payload)); // 255
~~~

`Byte.toUnsignedInt` trả về một `int`; nó không biến `byte` thành một kiểu nguyên thủy không dấu. Bài **Số nguyên có dấu và biểu diễn bù hai** ngay sau đây đi sâu vào cách đọc bit, mở rộng dấu và dịch bit; ở đây trọng tâm là **miền có thể lưu** để khai báo và chuyển kiểu đúng.

## 4. Chuyển kiểu mở rộng: từ nhỏ sang lớn

Java cho phép chuyển **mở rộng** (*widening primitive conversion*) giữa bốn kiểu số nguyên theo hướng:

~~~text
byte → short → int → long
~~~

Và có thể đi trực tiếp từ kiểu nhỏ tới kiểu lớn hơn bất kỳ ở bên phải:

~~~java
byte count = 100;
short asShort = count;
int asInt = count;
long asLong = count;

System.out.println(asShort); // 100
System.out.println(asInt);   // 100
System.out.println(asLong);  // 100
~~~

Trong chuỗi này, mọi giá trị `byte` đều biểu diễn chính xác bằng `short`, `int`, `long`. Mở rộng **không đòi ép kiểu tường minh**.

Phân biệt **kiểu của giá trị/biến nguồn** với **kiểu đích**: `long result = 42;` hợp lệ, nhưng literal `42` vẫn là `int` rồi được chuyển mở rộng sang `long`.

## 5. Chuyển kiểu thu hẹp: có thể mất dữ liệu

Hướng ngược lại **không tự động cho mọi giá trị**:

~~~java
int input = 130;
// byte narrowed = input; // LỖI BIÊN DỊCH: int không tự thu hẹp thành byte
byte narrowed = (byte) input;
System.out.println(narrowed); // -126
~~~

`130` vượt miền của `byte` là -128 đến 127. Ép kiểu `(byte)` giữ 8 bit thấp; `130` có bit thấp `1000_0010`, khi diễn giải là `byte` có dấu thì thành **-126**.

**Cast không phải kiểm tra an toàn.** Nếu một giá trị có thể vượt miền, nên kiểm tra trước:

~~~java
static byte checkedByte(int value) {
    if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
        throw new IllegalArgumentException("Out of byte range: " + value);
    }
    return (byte) value;
}
~~~

Khi cần đúng giá trị mà không chấp nhận mất dữ liệu, có thể giữ nguyên kiểu `int`/`long` thay vì cast.

### Ngoại lệ quan trọng: gán hằng int nằm trong phạm vi

Dù `127` là literal `int`, Java cho phép **narrowing conversion của một số constant expressions** trong ngữ cảnh gán/khởi tạo nếu giá trị vừa kiểu nhỏ:

~~~java
byte maxByte = 127;      // Hợp lệ
short maxShort = 32767;  // Hợp lệ
// byte wrong = 128;     // LỖI: ngoài giới hạn byte
// short wrong2 = 40000; // LỖI: ngoài giới hạn short
~~~

Nhưng khi biểu thức là biến `int` thông thường, compiler không được tự coi giá trị runtime là an toàn:

~~~java
int source = 127;
// byte b = source; // LỖI
byte b = (byte) source; // Có cast; lập trình viên chịu trách nhiệm miền
~~~

Trường hợp đặc biệt này **không có nghĩa** literal `127` mang kiểu `byte`; kiểu literal mặc định vẫn là `int`.

## 6. Phép tính với byte/short thường được nâng lên int

Một bẫy quan trọng: Java **nâng** `byte` và `short` thành `int` trong nhiều phép toán số học (*binary numeric promotion*).

~~~java
byte a = 100;
byte b = 20;

// byte sum = a + b; // LỖI: biểu thức a + b có kiểu int
int sum = a + b;
System.out.println(sum); // 120
~~~

Tương tự:

~~~java
short x = 10;
short y = 20;
int product = x * y;
System.out.println(product); // 200
~~~

Nếu chắc chắn kết quả nằm trong miền cần thiết, có thể ép kiểu:

~~~java
byte a = 100;
byte b = 20;
byte sum = (byte) (a + b); // 120, còn trong phạm vi byte
~~~

Nhưng ép kiểu có thể tạo giá trị bất ngờ:

~~~java
byte a = 120;
byte b = 20;
byte wrong = (byte) (a + b);
System.out.println(wrong); // -116, vì 140 vượt phạm vi byte
~~~

Một biến `short` không làm `short + short` tự tính bằng `short`. Ngược lại, phép tính có toán hạng `long` thường thực hiện trong `long` theo quy tắc nâng kiểu số học.


## 7. Tràn số xảy ra trước khi gán kết quả

Độ rộng giá trị cố định nên phép cộng/nhân số nguyên thông thường có thể vượt miền. Phép toán `int` không tự đổi thành `long` hoặc tự ném ngoại lệ:

~~~java
long tooLate = 2_000_000_000 + 2_000_000_000;   // -294967296
long inTime = 2_000_000_000L + 2_000_000_000;   // 4000000000
~~~

Ở `tooLate`, phép cộng đã chạy theo kiểu `int`; phép gán chỉ mở rộng **kết quả bị tràn**. Khi muốn báo lỗi nếu kết quả không vừa miền, dùng `Math.addExact`, `Math.subtractExact` hoặc `Math.multiplyExact` với overload `int`/`long` phù hợp. Các API này ném `ArithmeticException` khi tràn. Cách đọc bit khi tràn, biên `MIN_VALUE` và cách dịch bit được giải thích trong bài kế tiếp.

## 8. Chương trình Java 17 chạy được và expected output

Tạo tệp đúng tên **`IntegerTypesDemo.java`**:

~~~java
public final class IntegerTypesDemo {
    private IntegerTypesDemo() {}

    static byte checkedByte(int value) {
        if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
            throw new IllegalArgumentException("byte overflow: " + value);
        }
        return (byte) value;
    }

    public static void main(String[] args) {
        System.out.println("byte: " + Byte.MIN_VALUE + ".." + Byte.MAX_VALUE);
        System.out.println("short: " + Short.MIN_VALUE + ".." + Short.MAX_VALUE);
        System.out.println("int: " + Integer.MIN_VALUE + ".." + Integer.MAX_VALUE);
        System.out.println("long: " + Long.MIN_VALUE + ".." + Long.MAX_VALUE);

        byte small = 100;
        short widenedShort = small;
        int widenedInt = small;
        long widenedLong = small;
        System.out.println("widen: " + widenedShort + ","
                + widenedInt + "," + widenedLong);

        byte a = 100;
        byte b = 20;
        int promotedSum = a + b;
        System.out.println("byte + byte -> int: " + promotedSum);

        byte castValue = (byte) 130;
        System.out.println("cast 130 to byte: " + castValue);

        byte signedBits = (byte) 0xFF;
        System.out.println("signed byte: " + signedBits);
        System.out.println("unsigned view: "
                + Byte.toUnsignedInt(signedBits));

        int wrapped = Integer.MAX_VALUE + 1;
        System.out.println("int wrap: " + wrapped);

        long wrong = 2_000_000_000 + 2_000_000_000;
        long correct = 2_000_000_000L + 2_000_000_000;
        System.out.println("int overflow first: " + wrong);
        System.out.println("long addition: " + correct);

        System.out.println("checked byte: " + checkedByte(127));
        try {
            checkedByte(128);
        } catch (IllegalArgumentException ex) {
            System.out.println("range check: " + ex.getMessage());
        }

        try {
            Math.addExact(Integer.MAX_VALUE, 1);
        } catch (ArithmeticException ex) {
            System.out.println("Math.addExact: overflow detected");
        }

        System.out.println("abs int min: " + Math.abs(Integer.MIN_VALUE));
    }
}
~~~

Biên dịch và chạy bằng **JDK 17+** tại thư mục chứa file.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 IntegerTypesDemo.java
java -cp . IntegerTypesDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\IntegerTypesDemo.java
java -cp . IntegerTypesDemo
~~~

**Đầu ra dự kiến:**

~~~text
byte: -128..127
short: -32768..32767
int: -2147483648..2147483647
long: -9223372036854775808..9223372036854775807
widen: 100,100,100
byte + byte -> int: 120
cast 130 to byte: -126
signed byte: -1
unsigned view: 255
int wrap: -2147483648
int overflow first: -294967296
long addition: 4000000000
checked byte: 127
range check: byte overflow: 128
Math.addExact: overflow detected
abs int min: -2147483648
~~~

Những dòng quan trọng:

- Bốn dòng đầu đọc miền giá trị từ **wrapper constants**; không phải nhập tay các con số để dự đoán kiểu.
- `byte + byte -> int` cho biết kiểu biểu thức sau nâng kiểu số học.
- `cast 130 to byte` minh họa dữ liệu bị thu hẹp; `unsigned view` chỉ là cách diễn giải các bit.
- `int overflow first` và `long addition` phân biệt việc **tính ở int** với việc **tính ở long**.
- `Math.addExact` báo lỗi thay vì âm thầm dùng giá trị sau tràn.

Ví dụ độc lập, không cần thư viện ngoài JDK.


## 9. Độ rộng kiểu là một ràng buộc dữ liệu

Chọn `byte` khi trao đổi **một octet** nhị phân, nhưng nhớ phải xử lý cách diễn giải có dấu/không dấu theo giao thức. Chọn `short` nếu tài liệu định dạng thực sự yêu cầu số nguyên 16 bit có dấu; đừng dùng `short` chỉ vì một số đếm hiện tại nhỏ. `int` thường khớp chỉ số và API Java phổ biến; `long` thích hợp khi miền dữ liệu hoặc các phép toán trung gian có thể vượt `int`. Khi **ngay cả `long` cũng không đủ** và cần số nguyên chính xác, `BigInteger` là kiểu tham chiếu phù hợp.

Một kiểu có miền rộng không thay thế kiểm tra hợp lệ của nghiệp vụ: giá trị `-5` nằm trong miền `int` nhưng không nhất thiết hợp lệ cho số lượng sản phẩm. Bài **Chọn kiểu theo miền dữ liệu và yêu cầu tính toán** sẽ kết hợp độ rộng, đơn vị dữ liệu và độ chính xác thành một quyết định thiết kế.

## 10. Lỗi thường gặp

| Tình huống | Nguyên nhân | Sửa thế nào? |
|---|---|---|
| `byte n = 128;` | 128 vượt miền byte | Chọn `short`/`int` nếu cần giá trị 128 |
| `short n = 40000;` | 40000 vượt miền short | Dùng `int` |
| `long n = 3000000000;` | Literal decimal mặc định int quá lớn | `3000000000L` |
| `byte sum = a + b;` | `a + b` được nâng thành int | Nhận vào `int` hoặc cast có kiểm tra |
| `byte n = (byte) 130;` | Cast cắt bit, ra -126 | Kiểm tra miền trước cast |
| `long n = 2_000_000_000 + 2_000_000_000;` | Tràn int trước khi gán | Có `L` ở toán hạng trước phép cộng |
| `Integer.MAX_VALUE + 1` | Tràn về `Integer.MIN_VALUE` | `Math.addExact` hoặc đổi miền kiểu |
| `Math.abs(Integer.MIN_VALUE)` | Không thể biểu diễn trị tuyệt đối dương trong int | Kiểm tra giá trị biên, xử lý bằng kiểu phù hợp |

Lưu ý một giới hạn thực tế: `Math.addExact` phát hiện overflow của kiểu được sử dụng; nếu biểu thức **đã overflow trước khi truyền** vào `Math.addExact`, API không thể biết ý định ban đầu. Hãy chọn toán hạng/kiểu đúng trước khi tính.

## 11. Bài tập kèm đáp án

### Bài A — miền giá trị

`byte` 8 bit lưu được `127`, `128`, `-128`, `-129` không?

**Đáp án:** `127` và `-128` nằm trong miền; `128` và `-129` nằm ngoài miền `byte`.

### Bài B — chuyển kiểu mở rộng

~~~java
short s = 32000;
int n = s;
long m = n;
System.out.println(m);
~~~

**Đáp án:** in `32000`. Giá trị vừa miền `short` và được mở rộng chính xác sang `int`, rồi `long`.

### Bài C — phép cộng byte

~~~java
byte a = 120;
byte b = 20;
// byte sum = a + b;
int sum = a + b;
System.out.println(sum);
~~~

**Đáp án:** `140`. Biểu thức `a+b` có kiểu `int`; khai báo `byte sum` sẽ lỗi dù bạn biết cú pháp `byte`.

### Bài D — int overflow trước khi gán long

~~~java
long a = Integer.MAX_VALUE + 1;
long b = (long) Integer.MAX_VALUE + 1;
System.out.println(a);
System.out.println(b);
~~~

**Đáp án:**

~~~text
-2147483648
2147483648
~~~

Ở `a` phép cộng dùng `int` và tràn; ở `b` ép kiểu sang `long` xảy ra trước phép cộng.

### Bài E — signed byte và unsigned view

~~~java
byte b = (byte) 255;
System.out.println(b);
System.out.println(Byte.toUnsignedInt(b));
~~~

**Đáp án:** lần lượt `-1` và `255`; mẫu 8 bit vẫn là `11111111`, chỉ cách diễn giải khác nhau.

## 12. Checklist tự đánh giá

- [ ] Nhớ độ rộng cố định: `byte` 8, `short` 16, `int` 32, `long` 64 bit.
- [ ] Tra đúng `MIN_VALUE`/`MAX_VALUE` thay vì đoán giá trị biên.
- [ ] Hiểu miền có dấu theo bù hai: \(-2^{n-1}\) đến \(2^{n-1}-1\).
- [ ] Biết chuyển mở rộng `byte → short → int → long` không cần cast.
- [ ] Biết ép thu hẹp có thể mất dữ liệu; `(byte)130 == -126`.
- [ ] Phân biệt ngoại lệ gán constant expression int vừa miền với ép biến int runtime.
- [ ] Hiểu phép tính `byte`/`short` thường dùng `int`.
- [ ] Biết `int`/`long` có thể overflow mà không ném lỗi.
- [ ] Dùng hậu tố `L` khi literal số nguyên cần kiểu `long`.
- [ ] Tự biên dịch/chạy `IntegerTypesDemo.java` và đối chiếu output.

**Kết luận:** Độ rộng và miền giá trị của bốn kiểu số nguyên có dấu trong Java là **cố định**. Chọn kiểu theo yêu cầu thực, hiểu phép tính có thể được nâng thành `int` hoặc `long`, và kiểm tra chuyển kiểu/overflow ở những điểm có thể làm sai dữ liệu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="primitive-types-003">Số nguyên có dấu và biểu diễn bù hai</a>

<details>
<summary>Xem chi tiết</summary>

# Số nguyên có dấu và biểu diễn bù hai trong Java

> **Java core — language-basics / Kiểu nguyên thủy**  
> **Môi trường:** JDK 17 trở lên; không cần thư viện ngoài.  
> **Mục tiêu:** Hiểu bit dấu và quy tắc **bù hai (two's complement)** của `byte`, `short`, `int`, `long`; tự đọc một mẫu bit thành số dương/âm; nhận ra tràn số, mở rộng dấu và sự khác nhau giữa dịch phải có dấu/không dấu.


## 1. Vì sao cùng mẫu bit có thể mang số âm?

Bốn kiểu số nguyên có dấu của Java có độ rộng cố định: `byte` **8 bit**, `short` **16 bit**, `int` **32 bit**, `long` **64 bit**. Với `n` bit, miền giá trị là `-2^(n-1)` đến `2^(n-1)-1`. Bài trước đã dùng những miền này để chọn kiểu và nhận biết phép chuyển kiểu; ở đây ta tìm hiểu **vì sao** bù hai tạo ra chúng.

Ở độ rộng xác định, bit cao nhất bằng 1 báo một giá trị âm theo **bù hai**. Tuy nhiên không được đọc bảy bit còn lại như độ lớn rồi đặt dấu trừ (*sign-magnitude*). Chẳng hạn, mẫu `1000_0000` của `byte` mang giá trị **-128**, trong khi `0111_1111` là **127**. `char` cũng rộng 16 bit nhưng là **code unit UTF-16 không dấu**, không theo miền của `short`.

## 2. Cách đọc mẫu bit bù hai

Đừng chỉ xem bit cao nhất là dấu rồi cộng độ lớn của các bit còn lại như cách *sign-magnitude*. Với bù hai `n` bit, ta có quy tắc đọc mẫu bit:

- Nếu bit cao nhất là **0**, giá trị bằng số không dấu của mẫu bit.
- Nếu bit cao nhất là **1**, lấy giá trị **không dấu** của mẫu bit rồi **trừ 2^n**.

Ví dụ ở độ rộng **8 bit**:

| Bit pattern | Giá trị không dấu | Giá trị `byte` có dấu |
| --- | ---: | ---: |
| `0000_0000` | 0 | 0 |
| `0000_0101` | 5 | 5 |
| `0111_1111` | 127 | 127 |
| `1000_0000` | 128 | -128 |
| `1111_1011` | 251 | -5 |
| `1111_1111` | 255 | -1 |

Ví dụ `1111_1011` là 251 theo cách hiểu không dấu; dưới dạng `byte` 8-bit có dấu, giá trị là **251 - 256 = -5**.

### Tạo biểu diễn âm bằng đảo bit rồi cộng 1

Để tìm mẫu bit của **-5** từ **+5** trong 8 bit:

~~~text
+5:                  0000 0101
Đảo từng bit:        1111 1010
Cộng 1:              1111 1011    => -5
~~~

Phép đảo bit rồi cộng một là cách minh họa bù hai khi độ rộng bit **được cố định**. Java có **một biểu diễn số 0** duy nhất trong bù hai: `0000_0000`; không có dạng “âm không” riêng cho số nguyên.

Do chia đôi không gian mẫu bit, kiểu 8-bit có giá trị nhỏ nhất **-128** nhưng giá trị lớn nhất chỉ **127**. Không có số `+128` kiểu `byte`. Vì vậy `-Byte.MIN_VALUE` khi được tính ở độ rộng `int` cho ra 128; việc ép kết quả trở lại `byte` sẽ cho -128.

## 3. Chương trình Java 17+ kiểm chứng

Tạo tệp **TwosComplementDemo.java**:

~~~java
public final class TwosComplementDemo {
    private static String bits8(int value) {
        String bits = Integer.toBinaryString(value & 0xFF);
        return "0".repeat(8 - bits.length()) + bits;
    }

    public static void main(String[] args) {
        byte five = 5;
        byte minusFive = -5;
        byte highBit = (byte) 0x80;

        System.out.println("five=" + bits8(five));
        System.out.println("minusFive=" + bits8(minusFive));
        System.out.println("minusOne=" + bits8(-1));
        System.out.println("highBit=" + bits8(highBit));
        System.out.println("byteRange=" + Byte.MIN_VALUE
                + ".." + Byte.MAX_VALUE);

        System.out.println("highBitAsByte=" + highBit);
        System.out.println("signExtendedHex="
                + Integer.toHexString(highBit));
        System.out.println("maskedHex="
                + Integer.toHexString(highBit & 0xFF));
        System.out.println("unsignedByteFF="
                + Byte.toUnsignedInt((byte) 0xFF));

        int max = Integer.MAX_VALUE;
        System.out.println("overflow=" + (max + 1));
        try {
            Math.addExact(max, 1);
        } catch (ArithmeticException exception) {
            System.out.println("addExact=overflow");
        }

        System.out.println("signedShift=" + (-8 >> 1));
        System.out.println("unsignedShift=" + (-8 >>> 1));
        System.out.println("intMinusOneHex="
                + Integer.toHexString(-1));
    }
}
~~~

Biên dịch và chạy trong Bash, PowerShell hoặc Command Prompt từ thư mục chứa tệp:

~~~bash
javac -encoding UTF-8 TwosComplementDemo.java
java TwosComplementDemo
~~~

**Expected output (chính xác):**

~~~text
five=00000101
minusFive=11111011
minusOne=11111111
highBit=10000000
byteRange=-128..127
highBitAsByte=-128
signExtendedHex=ffffff80
maskedHex=80
unsignedByteFF=255
overflow=-2147483648
addExact=overflow
signedShift=-4
unsignedShift=2147483644
intMinusOneHex=ffffffff
~~~

Hàm `bits8` lấy **8 bit thấp** bằng `& 0xFF` rồi thêm các số 0 ở đầu. Cách này quan trọng vì `Integer.toBinaryString(-5)` không trả chuỗi 8 bit: tham số là `int`, nên biểu diễn của -5 có **32 bit**.

## 4. Mở rộng dấu, ép kiểu và mẫu bit

Khi chuyển một số nguyên âm từ kiểu hẹp sang kiểu rộng hơn, Java **mở rộng dấu** (*sign extension*): những bit mới ở đầu được điền bằng bit dấu.

Ví dụ:

~~~java
byte b = (byte) 0x80; // 1000_0000, có dấu là -128.
int n = b;             // n vẫn là -128.
~~~

Ở `byte`, `b` có mẫu 8 bit `1000_0000`. Sau khi gán sang `int`, `n` có mẫu 32 bit `0xFFFF_FF80` (hệ 16: `ffffff80` khi in qua `Integer.toHexString`).

Ngược lại, `b & 0xFF` cho giá trị `int` **128**, vì mặt nạ chỉ giữ 8 bit thấp và các bit cao của kết quả được xóa. Đó là cách xem mẫu 8 bit dưới góc nhìn không dấu; nó **không đổi kiểu `byte` thành unsigned**.

| Biểu thức Java | Giá trị / kết quả | Vì sao |
| --- | --- | --- |
| `(byte) 0xFF` | -1 | Giữ 8 bit thấp: `11111111` |
| `(byte) 0x80` | -128 | Bit dấu 8-bit bằng 1 |
| `((byte) 0xFF) & 0xFF` | 255 | Thao tác `&` diễn ra ở `int` và dùng mask |
| `Byte.toUnsignedInt((byte) 0xFF)` | 255 | Diễn giải 8 bit dưới dạng không dấu |
| `Integer.toHexString(-1)` | `ffffffff` | Biểu diễn toàn bộ 32 bit của `int` -1 |

**Lưu ý về literal:** `0xFF` trong Java tự nó là một literal `int` có giá trị **255**, không phải `byte` -1. Chỉ sau phép ép kiểu `(byte) 0xFF`, 8 bit thấp mới được diễn giải thành -1. Tương tự, literal `0xFFFF_FFFF` là giá trị `int` -1 vì chiếm đủ 32 bit với bit dấu 1.

## 5. Phép toán, tràn số và đổi dấu

Với số nguyên Java, kết quả của các phép tính `int`/`long` có độ rộng cố định. Nếu vượt giới hạn, phép tính số nguyên thông thường **quay vòng theo 2^n**; Java không tự ném exception chỉ vì phép cộng bị tràn.

Ví dụ:

~~~java
int max = Integer.MAX_VALUE;  // 2147483647
int wrapped = max + 1;
System.out.println(wrapped);  // -2147483648
~~~

Trước phép cộng, bit pattern của `max` là `0x7FFF_FFFF`. Cộng 1 cho `0x8000_0000`, được đọc là **Integer.MIN_VALUE** theo bù hai.

Muốn **phát hiện** tràn đối với phép cộng/trừ/nhân `int`/`long`, dùng `Math.addExact`, `Math.subtractExact`, `Math.multiplyExact`: các hàm này ném `ArithmeticException` khi vượt miền biểu diễn.

### Hai trường hợp đáng chú ý

- `-Integer.MIN_VALUE` vẫn là **Integer.MIN_VALUE** khi tính dưới dạng `int`: giá trị dương tương ứng `2147483648` nằm ngoài phạm vi `int`. `Math.negateExact(Integer.MIN_VALUE)` ném `ArithmeticException`.
- `Math.abs(Integer.MIN_VALUE)` cũng cho ra **giá trị âm** Integer.MIN_VALUE. Không giả định `abs` luôn cho số không âm ở biên nhỏ nhất của kiểu nguyên.

Trong các biểu thức có `byte` hoặc `short`, nhiều toán tử số học sẽ **nâng kiểu về `int`** trước khi tính. Vì thế `-Byte.MIN_VALUE` (trong biểu thức số học thông thường) là `int` **128**, không phải một `byte` bị tràn; chỉ sau ép `(byte)` mới còn 8 bit và thành -128.

## 6. Dịch bit `>>` và `>>>`

Với số âm:

- `x >> k`: **dịch phải số học**, thêm các bit 1 vào phía trái để **giữ bit dấu**.
- `x >>> k`: **dịch phải logic**, thêm các bit 0 vào phía trái; với số âm, kết quả có thể là số dương lớn.

Ví dụ từ `TwosComplementDemo`:

~~~java
System.out.println(-8 >> 1);  // -4
System.out.println(-8 >>> 1); // 2147483644
~~~

`-8` trong `int` là `0xFFFF_FFF8`. Dịch số học một bit cho `0xFFFF_FFFC` (-4); dịch logic một bit cho `0x7FFF_FFFC` (2147483644).

Hai toán tử làm việc theo **độ rộng toán hạng sau quy tắc nâng kiểu**. Nếu dịch một `byte`, biểu thức trước tiên dùng `int`; đừng suy luận kết quả `>>>` có độ rộng 8 bit.

## 7. Bẫy phổ biến và hướng sửa

| Cách hiểu hoặc hiện tượng | Điều đúng |
| --- | --- |
| “Bit cao nhất bằng 1 nên lấy 7 bit còn lại rồi thêm dấu trừ.” | Sai: Java dùng **bù hai**, không dùng sign-magnitude |
| “`0xFF` là `byte` -1.” | Sai: literal này là `int` 255; `(byte) 0xFF` mới là -1 |
| “Biến `byte` âm mở rộng thành `int` có giá trị dương.” | Sai: phép mở rộng kiểu giữ giá trị có dấu bằng **sign extension** |
| “`Integer.MAX_VALUE + 1` ném lỗi tự động.” | Sai: phép cộng `int` thông thường bị tràn, cho Integer.MIN_VALUE |
| “`Math.abs` luôn không âm.” | Sai tại Integer.MIN_VALUE/Long.MIN_VALUE |
| “`>>` và `>>>` cho kết quả giống nhau trên số âm.” | Sai: một bên điền bit dấu, một bên điền 0 |
| “`char` là signed 16-bit giống `short`.” | Sai: `char` là đơn vị mã UTF-16 **không dấu** |

## 8. Bài tập tự luyện

**Bài 1:** Dùng quy tắc 8 bit để tìm biểu diễn bù hai của -6. Kiểm tra bằng `Integer.toBinaryString((-6) & 0xFF)`; nếu muốn đúng 8 ký tự, cần thêm bao nhiêu số 0 ở đầu?

**Bài 2:** Phân loại `0x7F`, `0x80` và `0xFF` khi dùng dạng `int`, rồi dự đoán kết quả của từng biểu thức sau ép `(byte)`.

**Bài 3:** Vì sao `int n = (byte) 0xFF;` cho `n = -1`, nhưng `int n = 0xFF;` cho `n = 255`? Mô tả bước ép xuống 8 bit và mở rộng dấu về 32 bit.

**Bài 4:** Tính `Integer.MAX_VALUE + 1` và `Math.addExact(Integer.MAX_VALUE, 1)`. Cách nào cho giá trị, cách nào báo lỗi?

**Bài 5:** Với số `-8`, vẽ bốn byte của `int` rồi thực hiện `>> 1` và `>>> 1`; lý giải hai đầu ra khác nhau.

### Ghi nhớ

**Java `byte`/`short`/`int`/`long` đều có dấu và biểu diễn bằng bù hai.** Với `n` bit, mẫu có bit cao nhất 1 được đọc là **giá trị không dấu trừ 2^n**; miền là **-2^(n-1) đến 2^(n-1)-1**. Mở rộng kiểu âm giữ nguyên dấu; ép hẹp giữ các bit thấp; phép tính số nguyên có thể tràn mà không báo lỗi nếu không dùng API kiểm tra; `>>` và `>>>` khác nhau ở cách điền bit khi dịch phải.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="primitive-types-004">char là đơn vị mã UTF-16 không dấu</a>

<details>
<summary>Xem chi tiết</summary>

# `char` là đơn vị mã UTF-16 không dấu — Java 17+

Trong Java, `char` là một trong tám **kiểu nguyên thủy**. Nó lưu **đúng một đơn vị mã UTF-16 (UTF-16 code unit) 16 bit không dấu**, tức một giá trị nguyên từ **0 đến 65.535** (`0x0000` đến `0xFFFF`).

Điểm dễ nhầm: **một `char` không đồng nghĩa với một ký tự Unicode hoàn chỉnh hoặc một ký tự người dùng nhìn thấy**. Có Unicode code point được biểu diễn bằng hai đơn vị mã UTF-16, và có ký tự hiển thị được tạo từ nhiều code point.

## 1. `char` lưu gì?

| Biểu thức | Giá trị / ý nghĩa | Lưu ý |
| --- | --- | --- |
| `char c = 'A';` | Một `char` chứa mã `0x0041`, tức 65 | Nháy đơn tạo character literal |
| `char c = '\u00E9';` | Một `char` chứa mã `0x00E9`, tức 233 (é) | Unicode escape trong mã nguồn |
| `char min = Character.MIN_VALUE;` | `0` (`0x0000`) | Không có giá trị `char` âm |
| `char max = Character.MAX_VALUE;` | `65_535` (`0xFFFF`) | Không phải code point Unicode lớn nhất |
| `int n = 'A';` | `65` | Chuyển mở rộng `char` sang `int` |
| `int n = 'A' + 1;` | `66` | Phép toán số học thường nâng `char` thành `int` |
| `String s = "A";` | Chuỗi gồm một UTF-16 code unit | `String` là kiểu tham chiếu, khác `char` |

Khác với `byte`, `short`, `int` và `long` vốn là các kiểu nguyên **có dấu**, `char` là kiểu nguyên **không dấu**. Ép `char` sang `int` sẽ cho giá trị trong miền 0–65.535, không bị hiểu thành số âm.

Một `char` có thể chứa một **surrogate code unit** (`0xD800`–`0xDFFF`). Đơn vị mã này **không phải tự nó là một Unicode scalar value độc lập**; nó có thể là một nửa của cặp surrogate biểu diễn một code point ngoài BMP.

## 2. Chương trình Java 17+ chạy được

Tạo file UTF-8 tên **`CharUtf16Demo.java`**:

~~~java
public class CharUtf16Demo {
    public static void main(String[] args) {
        char letter = 'A';
        char accented = '\u00E9';
        char max = Character.MAX_VALUE;
        char wrapped = (char) (max + 1);

        int rocketCodePoint = 0x1F680;
        String rocket = new String(Character.toChars(rocketCodePoint));

        System.out.println("letter=" + letter);
        System.out.println("letterCode=" + (int) letter);
        System.out.println("accentedCode=" + (int) accented);
        System.out.println("charMin=" + (int) Character.MIN_VALUE);
        System.out.println("charMax=" + (int) max);
        System.out.println("wrapped=" + (int) wrapped);
        System.out.println("letterPlusOne=" + (letter + 1));

        System.out.println("rocketUnits=" + rocket.length());
        System.out.println(
                "rocketPoints=" + rocket.codePointCount(0, rocket.length()));
        System.out.println(
                "highSurrogate=" + Character.isHighSurrogate(rocket.charAt(0)));
        System.out.println(
                "lowSurrogate=" + Character.isLowSurrogate(rocket.charAt(1)));
        System.out.printf("rocketCodePoint=U+%X%n", rocket.codePointAt(0));

        String combining = "e\u0301";
        System.out.println("combiningUnits=" + combining.length());
        System.out.println(
                "combiningPoints="
                + combining.codePointCount(0, combining.length()));
    }
}
~~~

Mở **PowerShell (Windows)** hoặc **Bash (Linux/macOS)** trong thư mục chứa tệp:

~~~bash
java -version
javac -version
javac -encoding UTF-8 CharUtf16Demo.java
java CharUtf16Demo
~~~

**Expected output:**

~~~text
letter=A
letterCode=65
accentedCode=233
charMin=0
charMax=65535
wrapped=0
letterPlusOne=66
rocketUnits=2
rocketPoints=1
highSurrogate=true
lowSurrogate=true
rocketCodePoint=U+1F680
combiningUnits=2
combiningPoints=2
~~~

**Giải thích kết quả:**

- `'A'` mang mã 65; `'\u00E9'` chuyển thành chữ é trước khi mã được phân tích, với đơn vị mã 233.
- `Character.MAX_VALUE + 1` được tính dưới dạng `int` cho 65.536; ép lại `char` lấy 16 bit thấp nên bằng `0`. Đây là **ép kiểu có mất thông tin**, không phải Java tự tạo một `char` âm.
- `letter + 1` có kiểu kết quả `int`, nên lệnh in số `66` thay vì chữ `B`.
- U+1F680 (🚀) lớn hơn `0xFFFF`; `Character.toChars` tạo **hai `char`**, một high surrogate và một low surrogate. Do vậy `String.length()` trả `2` nhưng `codePointCount` trả `1`.
- Chuỗi `"e\u0301"` gồm `e` và dấu sắc kết hợp (hai code point), có thể hiển thị như **một** ký tự. Vì vậy cả `length()` và `codePointCount()` đều bằng 2 dù mắt thường thấy một chữ có dấu.

Ví dụ cố ý chỉ in **ASCII, số và boolean** cho các giá trị Unicode đặc biệt để đầu ra dễ đối chiếu trên terminal có cấu hình phông chữ khác nhau.

## 3. Phân biệt code unit, code point và ký tự hiển thị

| Khái niệm | Đơn vị | Ví dụ |
| --- | --- | --- |
| **UTF-16 code unit** | Một giá trị 16 bit, đúng khả năng chứa của `char` | Chữ `A`: 1 unit; 🚀: 2 units |
| **Unicode code point** | Giá trị dạng `U+XXXX` hoặc `U+XXXXX/XXXXXX` | 🚀 là một code point `U+1F680` |
| **Grapheme cluster** (ký tự hiển thị) | Cụm được người dùng cảm nhận như một ký tự | `e` + dấu sắc kết hợp có thể hiển thị như một chữ é |

UTF-16 biểu diễn các code point từ **U+0000 đến U+FFFF** (Basic Multilingual Plane — BMP), trừ vùng surrogate, bằng **một code unit**. Code point **U+10000 đến U+10FFFF** được biểu diễn bằng **cặp hai code unit** (*surrogate pair*).

Công thức dành cho code point `cp` trong khoảng U+10000…U+10FFFF:

~~~text
v = cp - 0x10000
high = 0xD800 + (v >> 10)
low  = 0xDC00 + (v & 0x3FF)
~~~

Không cần tự viết phép tính trên khi làm việc bình thường: Java có API `Character.toChars(int codePoint)` và `String.codePointAt(int index)`.

### Các API nên chọn

| Muốn làm gì? | API / cách viết | Trả về |
| --- | --- | --- |
| Lấy số đơn vị mã UTF-16 của chuỗi | `text.length()` | Số code unit |
| Đọc một đơn vị mã | `text.charAt(i)` | `char`, có thể là surrogate |
| Đọc code point bắt đầu tại vị trí đơn vị mã | `text.codePointAt(i)` | `int` chứa code point |
| Đếm code point trong chuỗi | `text.codePointCount(0, text.length())` | Số code point |
| Chuyển code point thành UTF-16 | `Character.toChars(cp)` | Mảng `char[]` gồm 1 hoặc 2 phần tử |
| Duyệt code point | `text.codePoints()` | `IntStream` các code point |

**Quan trọng:** Chỉ số của `charAt` và `codePointAt` vẫn là **vị trí code unit UTF-16**, không phải chỉ số code point. Nếu cần đi qua chuỗi có emoji, dùng `codePoints()` hoặc `offsetByCodePoints(...)` để tránh tách đôi một surrogate pair.

`codePointCount` không có nghĩa là đếm đúng số *grapheme cluster*: một ký tự nhìn thấy có thể chứa nhiều code point (dấu kết hợp, các chuỗi emoji ghép). Nếu giao diện cần đếm cụm ký tự, phải chọn thuật toán phân đoạn văn bản phù hợp với yêu cầu sản phẩm.

## 4. Những lỗi dễ gặp

| Mã / suy nghĩ | Kết quả | Cách hiểu đúng |
| --- | --- | --- |
| `char c = 'A';` | Hợp lệ | Một code unit |
| `char c = 65;` | Hợp lệ | Hằng `int` trong miền `char` có thể gán khi khai báo |
| `char c = -1;` | Lỗi biên dịch | Ngoài miền 0–65.535 |
| `char c = 65536;` | Lỗi biên dịch | Vượt `Character.MAX_VALUE` |
| `char c = (char) -1;` | Hợp lệ, giá trị 65.535 | Ép kiểu lấy 16 bit thấp |
| `char c = 'AB';` | Lỗi biên dịch | Literal ký tự không chứa hai code unit |
| `char c = '🚀';` | Lỗi biên dịch | Code point này cần hai code unit |
| `char c = '\uD83D';` | Hợp lệ về cú pháp | Chỉ là **một high surrogate**, không phải toàn bộ 🚀 |
| `String s = "🚀";` | Hợp lệ | `s.length()` bằng 2 |
| `text.length()` bằng số ký tự nhìn thấy | Không luôn đúng | `length()` đếm code unit |

Vì `char` là một kiểu số nguyên nguyên thủy trong phép toán, kết quả của phép cộng/trừ trên `char` thường có kiểu `int`:

~~~java
char c = 'A';
int nextCode = c + 1;     // 66.
char nextChar = (char) (c + 1); // 'B'.
~~~

Không ép kiểu một cách vô thức nếu phép tính có thể vượt miền hoặc chia cắt surrogate pair.

### `char` khác gì `String`?

`'A'` là `char`, còn `"A"` là `String`. `String` có thể dài 0, 1 hoặc nhiều UTF-16 code unit; `char` luôn đúng **một** code unit. Bạn không thể gọi `.length()` trên `char` và không thể đặt một emoji ngoài BMP vào literal `char` duy nhất.

## 5. Bài tập

1. **Dự đoán:** `(int) 'A'` và `'A' + 2` cho kết quả gì?  
   *Đáp án:* 65 và 67.
2. **Giới hạn:** Vì sao `char c = 65535;` được phép nhưng `char c = 65536;` không?  
   *Đáp án:* `char` chỉ chứa 16 bit không dấu, tối đa 65.535.
3. **Emoji:** Vì sao `"🚀".length()` bằng 2 và `codePointCount` bằng 1?  
   *Đáp án:* U+1F680 được mã hóa bằng một surrogate pair, gồm hai code unit.
4. **Duyệt văn bản:** Để lấy một Unicode code point có thể nằm ngoài BMP, chọn `charAt` hay `codePointAt`?  
   *Đáp án:* `codePointAt` (với chỉ số code unit hợp lệ).
5. **Dấu kết hợp:** Một chuỗi hiển thị như một chữ nhưng có hai code point thì `codePointCount` chắc chắn trả 1 không?  
   *Đáp án:* Không; ví dụ `"e\u0301"` có hai code point.

## Checklist ghi nhớ

- [ ] `char` là **16-bit unsigned UTF-16 code unit**, miền 0–65.535.
- [ ] `char` khác Unicode code point và khác grapheme cluster.
- [ ] Surrogate pair tạo từ **hai `char`** có thể biểu diễn một code point ngoài BMP.
- [ ] `String.length()` đếm UTF-16 code unit, không mặc định đếm ký tự nhìn thấy.
- [ ] Phép toán với `char` thường nâng thành `int`; thu hẹp về `char` có thể mất thông tin.
- [ ] Biết dùng `Character.toChars`, `String.codePointAt` và `codePointCount` khi thao tác Unicode.

**Kết luận:** Khi cần lưu **một đơn vị mã UTF-16**, `char` là lựa chọn chính xác. Khi cần lưu **một Unicode code point bất kỳ**, dùng `int` kèm các API Unicode của `Character`; khi cần xử lý **văn bản**, dùng `String` và tránh giả định “một `char` = một ký tự mà mắt thấy”.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="primitive-types-005">float và double theo mô hình dấu phẩy động nhị phân</a>

<details>
<summary>Xem chi tiết</summary>

# `float` và `double` theo mô hình dấu phẩy động nhị phân — Java 17+

## 1. Vì sao số thực trong máy tính không luôn chính xác?

Trong Java, `float` và `double` là **kiểu nguyên thủy dấu phẩy động nhị phân** (*binary floating-point*), phù hợp với mô hình IEEE 754. Chúng lưu một **giá trị nhị phân hữu hạn số bit**, không lưu mọi số thập phân theo đúng dạng con người viết.

Chẳng hạn:

~~~java
double sum = 0.1 + 0.2;
System.out.println(sum);
System.out.println(sum == 0.3);
~~~

Đầu ra:

~~~text
0.30000000000000004
false
~~~

Lý do không phải Java cộng sai. Các giá trị thập phân như `0.1` và `0.2` không có biểu diễn nhị phân hữu hạn chính xác, nên được làm tròn sang giá trị gần nhất có thể lưu trong `double`. Sau phép cộng và làm tròn, kết quả khác giá trị `double` gần nhất đại diện cho literal `0.3`.

**Thông điệp quan trọng:** `float` và `double` biểu diễn **các số thực xấp xỉ hữu hạn độ chính xác**; không nên mặc định mọi phép so sánh bằng `==` sẽ phản ánh đẳng thức toán học trên số thập phân.

## 2. Cấu trúc bit: dấu, số mũ và phần trị

Về mặt biểu diễn, cả hai kiểu gồm một **bit dấu**, phần **số mũ** (*exponent*) và phần **fraction/significand**.

| Đặc tính | `float` | `double` |
|---|---:|---:|
| Tổng số bit | 32 | 64 |
| Bit dấu | 1 | 1 |
| Bit trường số mũ | 8 | 11 |
| Bit fraction được lưu | 23 | 52 |
| Độ chính xác significand hiệu dụng của số normal | 24 bit | 53 bit |
| Chữ số thập phân có nghĩa thường dùng để ước lượng | khoảng 6–7 | khoảng 15–16 |
| Giá trị hữu hạn dương lớn nhất | khoảng `3.4028235E38` | khoảng `1.7976931348623157E308` |

Đối với số **normal** khác 0, giá trị mang dạng khái quát:

\[
(-1)^{sign}\times(1.fraction)_2\times2^{exponent-bias}.
\]

- `sign` cho biết dấu dương/âm.
- `fraction` là các bit phần lẻ sau dấu phẩy nhị phân.
- `exponent` xác định thang độ lớn dựa trên lũy thừa của 2.
- Với số normal, có một bit dẫn đầu `1` được hiểu ngầm, vì vậy độ chính xác hiệu dụng là 24 hoặc 53 bit.

Số **subnormal**, số 0, vô cực và NaN dùng các dạng biểu diễn đặc biệt, không áp dụng công thức số normal một cách nguyên xi.

Từ Java 17, ngữ nghĩa dấu phẩy động được xác định theo hướng **always-strict**: lập trình viên có thể dựa vào các quy tắc làm tròn/độ rộng của ngôn ngữ thay vì giả định JVM tùy tiện giữ kết quả trung gian với độ chính xác mở rộng. Không cần gắn `strictfp` chỉ để bật điều đó trong Java 17.

## 3. Literal, hậu tố F và kiểu mặc định

Một literal có dấu chấm thập phân hoặc số mũ như `1.25` thường có kiểu **`double`** nếu không có hậu tố. Muốn literal kiểu `float`, viết hậu tố `f` hoặc `F`.

~~~java
double distanceKm = 12.5;     // Literal double
double large = 1.23e6;       // 1_230_000.0, kiểu double
float temperature = 26.5f;   // Literal float
float ratio = 1.25F;         // Literal float
double fromFloat = 1.25f;    // float literal được mở rộng sang double
~~~

Không hợp lệ:

~~~java
// float bad = 0.1; // Lỗi compile: double không tự thu hẹp sang float
~~~

Nếu muốn `float`, hãy viết `0.1f` thay vì cast tùy tiện. Nếu bài toán không có lý do đặc biệt chọn `float`, **`double` thường là lựa chọn mặc định** cho phép tính số thực trên Java.

**Đừng nhầm hậu tố:** `L` dành cho literal số nguyên kiểu `long`; `F` dành cho literal dấu phẩy động kiểu `float`; `D` có thể viết cho `double` nhưng thường không cần.

Độ rộng 64 bit của `double` không có nghĩa nó biểu diễn **mọi** số nguyên 64 bit chính xác: khi các số đủ lớn, các số nguyên liên tiếp có thể cùng làm tròn về một `double`.

## 4. Sai số biểu diễn bắt đầu từ bước lưu giá trị

Trong hệ thập phân, `1/10 = 0.1` có một chữ số sau dấu phẩy. Trong hệ nhị phân, `1/10` lặp vô hạn, nên `float`/`double` phải cắt làm tròn trong giới hạn số bit.

~~~java
float f = 0.1f;
double d = 0.1;

System.out.println(f);
System.out.println((double) f);
System.out.println(d);
~~~

Đầu ra:

~~~text
0.1
0.10000000149011612
0.1
~~~

Giải thích:

- `System.out.println(f)` hiển thị một **chuỗi thập phân ngắn đủ để tái tạo** cùng giá trị `float` theo quy tắc chuyển sang chuỗi. Việc in `0.1` không chứng minh giá trị nhị phân được lưu bằng chính xác 1/10.
- Ép `float` sang `double` giữ đúng **giá trị xấp xỉ đã lưu ở float**, vì mọi giá trị `float` hữu hạn đều biểu diễn chính xác được ở `double`. Nó không khôi phục số thập phân gốc `0.1`.
- `double d = 0.1;` chọn một xấp xỉ khác, thường chính xác hơn do có 53 bit significand.

Nếu cần phân tích sâu có thể dùng `Float.floatToRawIntBits` và `Double.doubleToRawLongBits` để nhìn mẫu bit; còn khi học ứng dụng, chỉ cần hiểu `float`/ `double` không phải kiểu thập phân chính xác.

## 5. Miền giá trị, MIN_VALUE và số subnormal

Hai kiểu có miền giá trị rất lớn, nhưng có **độ chính xác hữu hạn**. Java cung cấp các hằng:

| Hằng | Ý nghĩa | Giá trị xấp xỉ |
|---|---|---:|
| `Float.MAX_VALUE` | Số hữu hạn dương lớn nhất của float | `3.4028235E38` |
| `Float.MIN_NORMAL` | Số normal dương nhỏ nhất của float | `1.17549435E-38` |
| `Float.MIN_VALUE` | Số subnormal dương nhỏ nhất khác 0 của float | `1.4E-45` |
| `Double.MAX_VALUE` | Số hữu hạn dương lớn nhất của double | `1.7976931348623157E308` |
| `Double.MIN_NORMAL` | Số normal dương nhỏ nhất của double | `2.2250738585072014E-308` |
| `Double.MIN_VALUE` | Số subnormal dương nhỏ nhất khác 0 của double | `4.9E-324` |

**Bẫy quan trọng:** `Float.MIN_VALUE` và `Double.MIN_VALUE` **không phải số âm nhỏ nhất**. Chúng là **số dương nhỏ nhất khác 0** có thể biểu diễn. Số hữu hạn âm có độ lớn lớn nhất lần lượt là `-Float.MAX_VALUE` và `-Double.MAX_VALUE`.

**Subnormal** cho phép biểu diễn các giá trị gần 0 hơn `MIN_NORMAL` bằng cách hy sinh dần các bit có nghĩa hiệu dụng. Nếu phép tính tạo ra giá trị có độ lớn quá nhỏ, kết quả có thể **underflow** về một giá trị subnormal hoặc thành số 0 (có dấu). Nếu độ lớn vượt miền hữu hạn, kết quả có thể thành **Infinity**.

Không nhầm “miền rộng” với “độ chính xác cao ở mọi giá trị”. Khoảng cách giữa hai số dấu phẩy động liên tiếp **tăng khi độ lớn của số tăng**.

## 6. Độ chính xác, ULP và bước nhảy giữa hai số

**ULP** (*unit in the last place*) giúp mô tả khoảng cách giữa các giá trị biểu diễn gần một số cụ thể. Gần `1.0`:

~~~java
System.out.println(Math.ulp(1.0f)); // 1.1920929E-7
System.out.println(Math.ulp(1.0d)); // 2.220446049250313E-16
~~~

Đối với `float`, số nguyên `16_777_216` (2²⁴) đã đủ lớn để **không thể phân biệt** giá trị đó với giá trị cộng thêm 1 sau khi làm tròn về `float`:

~~~java
float base = 16_777_216f;
System.out.println(base + 1f == base); // true
~~~

Trong `double`, số nguyên liên tiếp được biểu diễn chính xác đến `2^53`, sau đó không còn bảo đảm mọi bước tăng 1 đều được phân biệt.

Như vậy, khả năng lưu **giá trị rất lớn** không có nghĩa vẫn giữ được **sai khác rất nhỏ** trên cùng thang giá trị. Chọn `double` hay `float` phải xét cả độ lớn và độ phân giải cần thiết.

## 7. So sánh: ==, epsilon và giới hạn của dung sai

So sánh trực tiếp:

~~~java
double a = 0.1 + 0.2;
double b = 0.3;

System.out.println(a == b); // false
~~~

Nếu một bài toán cho phép sai số gần đúng, bạn có thể định nghĩa **dung sai có ý nghĩa**:

~~~java
static boolean nearlyEqual(double a, double b, double tolerance) {
    if (tolerance < 0 || !Double.isFinite(tolerance)) {
        throw new IllegalArgumentException("Invalid tolerance");
    }
    return Double.isFinite(a) && Double.isFinite(b)
            && Math.abs(a - b) <= tolerance;
}
~~~

Ví dụ `nearlyEqual(0.1 + 0.2, 0.3, 1e-9)` trả `true`.

Nhưng **không có một epsilon cố định đúng cho mọi bài toán**: dung sai tuyệt đối `1e-9` có thể quá lớn nếu đo nano-đơn vị và quá nhỏ nếu so sánh hai giá trị rất lớn. Khi cần, xét kết hợp dung sai tuyệt đối và tương đối theo miền dữ liệu.

Đặc biệt, **đừng dùng phép so sánh gần đúng** để thay thế định danh, khóa, dữ liệu tiền tệ, hoặc những phép kiểm tra cần sự bằng nhau chính xác theo quy ước nghiệp vụ. Với `NaN` và `Infinity`, cần xác định riêng chính sách chấp nhận giá trị.

## 8. Các giá trị đặc biệt: NaN, Infinity, +0.0 và -0.0

IEEE 754 cho phép nhiều trạng thái ngoài số hữu hạn bình thường:

| Giá trị | Sinh ra như thế nào (ví dụ) | Điều cần nhớ |
|---|---|---|
| `Double.POSITIVE_INFINITY` | `1.0 / 0.0` | Dương vô cực, không phải số hữu hạn |
| `Double.NEGATIVE_INFINITY` | `-1.0 / 0.0` | Âm vô cực |
| `Double.NaN` | `0.0 / 0.0` | Not a Number |
| `+0.0` | `0.0` | Zero dấu dương |
| `-0.0` | `-0.0` | Zero dấu âm, có mẫu bit khác +0.0 |

Ví dụ:

~~~java
double nan = 0.0 / 0.0;
System.out.println(Double.isNaN(nan)); // true
System.out.println(nan == nan);        // false
System.out.println(1.0 / 0.0);         // Infinity
System.out.println(1.0 / -0.0);        // -Infinity
System.out.println(0.0 == -0.0);       // true
~~~

**Phân biệt quan trọng:**

- Mọi phép so sánh số học `==` với NaN đều trả `false`, kể cả `nan == nan`. Dùng `Double.isNaN` hoặc `Float.isNaN` khi cần phát hiện NaN.
- `0.0 == -0.0` là `true` theo toán tử `==`, nhưng dấu có thể ảnh hưởng kết quả phép chia nghịch đảo và mẫu bit lưu trữ.
- Dùng `Double.isInfinite` hoặc `Double.isFinite` để kiểm tra các trường hợp ngoài miền hữu hạn.
- Khác phép chia số nguyên cho 0, phép chia dấu phẩy động `1.0 / 0.0` **không ném `ArithmeticException`**; nó tạo Infinity theo quy tắc floating-point.

Nếu dữ liệu đầu vào không được phép chứa NaN/Infinity, hãy validation **trước** khi tính hoặc lưu dữ liệu vào hệ thống.

## 9. Chương trình Java 17 đầy đủ và kết quả chạy

Tạo đúng tệp **`BinaryFloatingDemo.java`**:

~~~java
import java.math.BigDecimal;

public final class BinaryFloatingDemo {
    private BinaryFloatingDemo() {}

    static boolean nearlyEqual(double a, double b, double tolerance) {
        if (tolerance < 0 || !Double.isFinite(tolerance)) {
            throw new IllegalArgumentException("Invalid tolerance");
        }
        return Double.isFinite(a) && Double.isFinite(b)
                && Math.abs(a - b) <= tolerance;
    }

    public static void main(String[] args) {
        float f = 0.1f;
        double d = 0.1;

        System.out.println("float bits=" + Float.SIZE);
        System.out.println("double bits=" + Double.SIZE);
        System.out.println("float printed=" + f);
        System.out.println("float as double=" + (double) f);
        System.out.println("double printed=" + d);

        double sum = 0.1 + 0.2;
        System.out.println("0.1+0.2=" + sum);
        System.out.println("equals 0.3=" + (sum == 0.3));
        System.out.println("near 0.3=" + nearlyEqual(sum, 0.3, 1e-9));

        System.out.println("float ULP at 1=" + Math.ulp(1.0f));
        System.out.println("double ULP at 1=" + Math.ulp(1.0d));
        float base = 16_777_216f;
        System.out.println("float loses +1=" + (base + 1f == base));

        System.out.println("float min normal=" + Float.MIN_NORMAL);
        System.out.println("float min positive=" + Float.MIN_VALUE);
        System.out.println("double max finite=" + Double.MAX_VALUE);

        double nan = 0.0 / 0.0;
        System.out.println("positive infinity=" + (1.0 / 0.0));
        System.out.println("negative infinity=" + (1.0 / -0.0));
        System.out.println("is NaN=" + Double.isNaN(nan));
        System.out.println("NaN equals itself=" + (nan == nan));
        System.out.println("zero equality=" + (0.0 == -0.0));

        BigDecimal exact = new BigDecimal("0.1")
                .add(new BigDecimal("0.2"));
        System.out.println("decimal exact sum=" + exact);
    }
}
~~~

Yêu cầu: **JDK 17+**, terminal mở tại thư mục chứa tệp.

**Bash (Linux/macOS):**

~~~bash
java -version
javac -version
javac --release 17 -encoding UTF-8 BinaryFloatingDemo.java
java -cp . BinaryFloatingDemo
~~~

**PowerShell (Windows):**

~~~powershell
java -version
javac -version
javac --release 17 -encoding UTF-8 .\BinaryFloatingDemo.java
java -cp . BinaryFloatingDemo
~~~

**Expected output:**

~~~text
float bits=32
double bits=64
float printed=0.1
float as double=0.10000000149011612
double printed=0.1
0.1+0.2=0.30000000000000004
equals 0.3=false
near 0.3=true
float ULP at 1=1.1920929E-7
double ULP at 1=2.220446049250313E-16
float loses +1=true
float min normal=1.17549435E-38
float min positive=1.4E-45
double max finite=1.7976931348623157E308
positive infinity=Infinity
negative infinity=-Infinity
is NaN=true
NaN equals itself=false
zero equality=true
decimal exact sum=0.3
~~~

Trong phần đầu ra, dấu `E` nghĩa là số mũ cơ số 10 của **chuỗi hiển thị**; nó không đổi bản chất dữ liệu nhị phân bên dưới. Chuỗi hiển thị và mẫu bit trong JVM là hai khía cạnh khác nhau của cùng giá trị.

## 10. Khi nào dùng float, double và BigDecimal?

| Nhu cầu | Thường chọn | Lý do |
|---|---|---|
| Tính toán khoa học/thống kê thông thường có dung sai | `double` | Khoảng 15–16 chữ số thập phân có nghĩa; độ chính xác cao hơn `float` |
| Mảng rất lớn, dữ liệu hình ảnh/đồ họa, giao diện yêu cầu 32 bit | `float` khi phù hợp | Dung lượng dữ liệu thấp hơn, nhưng sai số lớn hơn |
| Tiền tệ/quy tắc làm tròn thập phân chặt chẽ | `BigDecimal` hoặc số nguyên đơn vị nhỏ nhất | Cần tính toán và làm tròn theo nghiệp vụ, không theo xấp xỉ nhị phân |
| Chỉ số đếm nguyên | `int` hoặc `long` | Số đếm nguyên không cần dấu phẩy động |

**BigDecimal không phải một hậu tố của float/double.** Nó là kiểu đối tượng trong `java.math`, có quy tắc và API số học riêng.

Khi bạn cần biểu diễn **chính xác các số thập phân trong bài toán tiền tệ**, tạo `BigDecimal` từ **chuỗi thập phân** hoặc từ biểu diễn đã biết, ví dụ:

~~~java
BigDecimal price = new BigDecimal("0.1");
BigDecimal tax = new BigDecimal("0.2");
System.out.println(price.add(tax)); // 0.3
~~~

Tránh `new BigDecimal(0.1)` nếu mục đích là lấy **chính xác giá trị thập phân 0.1**: constructor này nhận giá trị `double` đã xấp xỉ nhị phân và giữ lại sai khác đó. `BigDecimal.valueOf(0.1)` cũng là lựa chọn hợp lý cho nhiều trường hợp chuyển `double` theo chuỗi biểu diễn chuẩn, nhưng **không thể khôi phục độ chính xác đã mất trong phép tính trước đó**.

Với phép chia thập phân có kết quả vô hạn, `BigDecimal` có thể yêu cầu quy tắc làm tròn/`MathContext` phù hợp. Chọn `BigDecimal` không làm bạn hết phải xác định chính sách làm tròn.

## 11. Lỗi thường gặp và cách kiểm tra

| Hiện tượng | Nguyên nhân | Cách xử lý |
|---|---|---|
| `float f = 0.1;` không compile | Literal mặc định `double` | `float f = 0.1f;` |
| `0.1 + 0.2 == 0.3` là false | Làm tròn nhị phân | Dùng dung sai phù hợp hoặc mô hình decimal chính xác |
| `Float.MIN_VALUE` bị hiểu là số âm cực tiểu | Tên hằng dễ gây nhầm | Nhớ đây là số dương khác 0 nhỏ nhất |
| `float` mất một bước cộng 1 ở số lớn | Khoảng cách giữa các giá trị float đã lớn | Chọn `double` hoặc kiểu nguyên nếu bài toán đếm |
| `1.0 / 0.0` cho Infinity | Ngữ nghĩa IEEE 754 | Dùng `isFinite`/validation |
| `NaN == NaN` là false | NaN có quy tắc so sánh đặc biệt | Dùng `isNaN` |
| `0.0 == -0.0` là true | So sánh giá trị không phân biệt dấu zero | Kiểm tra bit/dấu nếu bài toán cần phân biệt |
| `new BigDecimal(0.1)` có số lẻ dài | Nhận binary double gần 0.1 | Dùng chuỗi decimal hoặc `valueOf` đúng mục đích |
| Dùng epsilon tuyệt đối cố định cho mọi thang số | Dung sai không theo miền dữ liệu | Định nghĩa sai số tuyệt đối/tương đối theo yêu cầu |
| Trông đợi `double` giữ nguyên mọi số nguyên 64 bit | Significand chỉ 53 bit | Dùng `long`/`BigInteger` khi cần số nguyên chính xác |

## 12. Bài tập ngắn kèm đáp án

### Bài A — kiểu của literal

~~~java
double a = 1.25;
float b = 1.25f;
// float c = 1.25;
~~~

**Đáp án:** `a` và `b` hợp lệ. Dòng `c` lỗi compile vì `1.25` là literal `double` không có hậu tố `f`. Dù giá trị này biểu diễn chính xác được bằng nhị phân, quy tắc kiểu literal vẫn áp dụng.

### Bài B — giá trị in ra khác với giá trị thật

~~~java
float value = 0.1f;
System.out.println(value);
System.out.println((double) value);
~~~

**Đáp án:**

~~~text
0.1
0.10000000149011612
~~~

`println(float)` và `println(double)` dùng cách chuyển giá trị thành chuỗi theo từng kiểu. Ép `float` thành `double` không biến giá trị thành 0.1 thập phân chính xác.

### Bài C — so sánh số thực

Tại sao `0.1 + 0.2 == 0.3` trả false? Cần làm gì nếu bài toán cho phép sai số 1e-9?

**Đáp án:** các giá trị đã làm tròn ở dạng nhị phân nên sau phép cộng có thể không trùng đúng mẫu bit của `0.3`. Có thể so sánh `Math.abs((0.1 + 0.2) - 0.3) <= 1e-9` nếu dung sai này hợp với miền dữ liệu, đồng thời có quy tắc riêng với NaN/Infinity.

### Bài D — số đặc biệt

~~~java
double x = 0.0 / 0.0;
System.out.println(Double.isNaN(x));
System.out.println(x == x);
~~~

**Đáp án:**

~~~text
true
false
~~~

### Bài E — tiền tệ

Cần cộng hai giá trị tiền theo thập phân là 0.1 và 0.2 để được 0.3 đúng tuyệt đối. Chọn gì?

**Đáp án:** `new BigDecimal("0.1").add(new BigDecimal("0.2"))` cho kết quả `0.3` đúng theo biểu diễn decimal; với đơn vị nhỏ nhất hữu hạn có thể cân nhắc số nguyên. Tránh coi `double` cộng thập phân là phép tính decimal chính xác.

## 13. Checklist kết thúc

- [ ] Biết `float` có 32 bit, `double` có 64 bit; significand hiệu dụng tương ứng 24/53 bit.
- [ ] Hiểu vì sao `0.1` không biểu diễn chính xác bằng một số hữu hạn bit nhị phân.
- [ ] Nhớ literal dấu phẩy động mặc định là `double`, dùng `f`/`F` cho `float`.
- [ ] Phân biệt chuỗi in `0.1` với giá trị nhị phân được lưu.
- [ ] Hiểu `MIN_VALUE` là số dương khác 0 nhỏ nhất, khác `MIN_NORMAL`.
- [ ] Biết độ phân giải giảm khi độ lớn tăng, có thể quan sát qua `Math.ulp`.
- [ ] Biết phát hiện NaN/Infinity bằng API chuyên dụng và phân biệt `+0.0`/`-0.0`.
- [ ] Không dùng epsilon chung cho mọi bài toán mà không xét thang đo.
- [ ] Biết khi nào chuyển sang `BigDecimal` hoặc số nguyên.
- [ ] Biên dịch/chạy `BinaryFloatingDemo.java` bằng `javac`/`java` và đối chiếu từng dòng.

**Kết luận:** `float` và `double` biểu diễn số thực bằng **dấu phẩy động nhị phân**, có phạm vi rất rộng nhưng chỉ giữ hữu hạn bit có nghĩa. Hãy dự đoán sai số làm tròn, chọn kiểu theo yêu cầu độ chính xác, kiểm tra các giá trị đặc biệt và dùng mô hình thập phân thích hợp khi nghiệp vụ đòi kết quả decimal chính xác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="primitive-types-006">boolean chỉ có true và false</a>

<details>
<summary>Xem chi tiết</summary>

# `boolean` chỉ có `true` và `false` trong Java

> **Java core — language-basics / Kiểu nguyên thủy**  
> **Môi trường:** JDK 17 trở lên, không cần thư viện ngoài.  
> **Mục tiêu:** Hiểu hai giá trị của `boolean` nguyên thủy, cách tạo biểu thức điều kiện, toán tử logic và *short-circuit*; phân biệt `boolean` với `Boolean` có thể là `null`.

## 1. Kiểu `boolean` biểu diễn điều kiện đúng hoặc sai

Trong Java, `boolean` là **kiểu nguyên thủy** (*primitive type*) chỉ có **hai giá trị hợp lệ**:

- `true`: đúng.
- `false`: sai.

~~~java
boolean isReady = true;
boolean isClosed = false;
isReady = false; // Hợp lệ: giá trị mới vẫn là boolean.
~~~

Hai từ `true` và `false` là **boolean literal** trong mã nguồn Java. Chúng không phải chuỗi `"true"`/`"false"`, và Java **không** coi số `1` là đúng hay số `0` là sai.

~~~java
// Các dòng cố ý SAI, không đưa vào chương trình chạy:
// boolean ok = 1;       // int không gán được cho boolean.
// boolean no = 0;       // int không được ngầm chuyển thành boolean.
// boolean text = "true"; // String khác boolean.
// boolean missing = null; // primitive boolean không nhận null.
~~~

Một biến kiểu `boolean` không thể giữ giá trị thứ ba như “chưa biết”. Khi cần trạng thái chưa xác định, hãy dùng kiểu tham chiếu `Boolean` có khả năng chứa `null` và xử lý riêng trạng thái đó.

## 2. Biểu thức trả về `boolean`

Kết quả so sánh và phép logic thường có kiểu `boolean`:

~~~java
int age = 20;
boolean adult = age >= 18;       // true
boolean exactly20 = age == 20;  // true
boolean notAdult = !adult;      // false
boolean eligible = adult && exactly20; // true
~~~

Những phép toán thường dùng:

| Nhóm | Toán tử | Ví dụ và kết quả |
| --- | --- | --- |
| Phủ định | `!` | `!true` → `false` |
| VÀ ngắn mạch | `&&` | `true && false` → `false` |
| HOẶC ngắn mạch | `\|\|` | `true \|\| false` → `true` |
| XOR logic | `^` | `true ^ false` → `true` |
| So sánh bằng/khác | `==`, `!=` | `true == false` → `false` |
| So sánh số | `<`, `<=`, `>`, `>=` | `5 > 3` → `true` |

Bảng chân trị đơn giản:

| `a` | `b` | `a && b` | `a \|\| b` | `a ^ b` |
| --- | --- | --- | --- | --- |
| false | false | false | false | false |
| false | true | false | true | true |
| true | false | false | true | true |
| true | true | true | true | false |

Các phép `&&`, `||`, `!` cần toán hạng phù hợp với ngữ cảnh boolean. Java không cho cộng hai giá trị boolean hoặc dùng toán tử `<`/`>` để so thứ tự `true` và `false`.

## 3. Điều kiện `if`, `while` phải là boolean

Java yêu cầu **biểu thức điều kiện** thực sự có kiểu `boolean` (hoặc `Boolean` có thể unbox thành boolean):

~~~java
int quantity = 3;

if (quantity > 0) {
    System.out.println("Co hang");
}
~~~

Đầu ra là `Co hang`. Không viết `if (quantity)` như trong các ngôn ngữ coi mọi số khác 0 là đúng:

~~~java
// if (quantity) { } // Sai: quantity là int, không phải boolean.
// if (1) { }        // Sai: literal số 1 không phải điều kiện boolean.
~~~

**Mẹo dễ đọc:** Khi biến `active` đã là `boolean`, dùng `if (active)` hoặc `if (!active)` thường rõ hơn `if (active == true)` hay `if (active == false)`.

## 4. `&&`/`||` ngắn mạch; `&`/`|` không ngắn mạch

Với `a && b`, nếu `a` là `false` thì Java **không đánh giá `b`**: kết quả đã chắc chắn false. Với `a || b`, nếu `a` là `true` thì Java **không đánh giá `b`**: kết quả đã chắc chắn true. Đó là *short-circuit*.

Ngược lại, khi toán hạng là boolean, `&` và `|` **đánh giá cả hai vế**. `^` cũng không ngắn mạch. Khác biệt này đặc biệt quan trọng nếu vế phải gọi method có tác dụng phụ hoặc có thể gây exception.

Ví dụ an toàn khi tham chiếu có thể `null`:

~~~java
String name = null;
boolean nonEmpty = name != null && !name.isEmpty();
System.out.println(nonEmpty); // false, không gọi name.isEmpty().
~~~

Không thay `&&` bằng `&` trong trường hợp đó: vế phải có thể vẫn được đánh giá và gây `NullPointerException`.

## 5. Chương trình Java 17+ runnable

Tạo tệp **BooleanDemo.java**:

~~~java
public final class BooleanDemo {
    private static boolean sharedFlag;
    private static int checks;

    private static boolean probe() {
        checks++;
        return true;
    }

    public static void main(String[] args) {
        boolean enabled = true;
        boolean blocked = false;
        boolean allowed = enabled && !blocked;

        System.out.println("allowed=" + allowed);
        System.out.println("notAllowed=" + !allowed);
        System.out.println("comparison=" + (5 > 3));

        if (allowed) {
            System.out.println("branch=entered");
        }

        boolean skipAnd = false && probe();
        boolean skipOr = true || probe();
        System.out.println("shortCircuit=" + skipAnd + ","
                + skipOr + ",checks=" + checks);

        boolean eagerAnd = false & probe();
        System.out.println("eagerAnd=" + eagerAnd + ",checks=" + checks);
        System.out.println("xor=" + (true ^ false));

        boolean[] flags = new boolean[2];
        System.out.println("fieldDefault=" + sharedFlag);
        System.out.println("arrayDefault=" + flags[0]);

        Boolean unknown = null;
        System.out.println("wrapperNull=" + (unknown == null));
        System.out.println("parseTrue=" + Boolean.parseBoolean("TrUe"));
        System.out.println("parseOne=" + Boolean.parseBoolean("1"));
    }
}
~~~

Chạy từ thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 BooleanDemo.java
java BooleanDemo
~~~

**Expected output chính xác:**

~~~text
allowed=true
notAllowed=false
comparison=true
branch=entered
shortCircuit=false,true,checks=0
eagerAnd=false,checks=1
xor=true
fieldDefault=false
arrayDefault=false
wrapperNull=true
parseTrue=true
parseOne=false
~~~

Giải thích: `enabled && !blocked` cho true. Hai lời gọi `probe()` ở biểu thức `false && ...` và `true || ...` đều bị bỏ qua nên `checks` vẫn là 0; `false & probe()` **vẫn gọi `probe()`**, làm `checks` thành 1 dù kết quả AND là false. Mảng và field `boolean` mới được khởi tạo mặc định bằng false. `Boolean.parseBoolean("TrUe")` nhận chuỗi không phân biệt hoa/thường; chuỗi `"1"` **không** được hiểu là true.

## 6. `boolean` khác `Boolean` ở chỗ nào?

| Tiêu chí | `boolean` | `Boolean` |
| --- | --- | --- |
| Bản chất | Kiểu nguyên thủy | Lớp wrapper tham chiếu |
| Giá trị của biến đã gán | `true` hoặc `false` | `Boolean.TRUE`, `Boolean.FALSE` hoặc `null` |
| Field chưa khởi tạo tường minh | `false` | `null` |
| Phần tử của mảng mới | `false` trong `boolean[]` | `null` trong `Boolean[]` |
| Dùng làm điều kiện | Trực tiếp | Java unbox về `boolean`; nếu `null` có thể ném `NullPointerException` |

Không nhầm “field có mặc định” với **biến cục bộ chưa gán**. Chẳng hạn, `boolean ready; System.out.println(ready);` trong method không hợp lệ vì `ready` chưa được *definitely assigned*. Nếu biến cục bộ đã gán, nó vẫn chỉ giữ true/false.

Với dữ liệu nhập dạng chuỗi, `Boolean.parseBoolean(text)` chỉ cho true khi `text` bằng `"true"` (không phân biệt hoa/thường); những chuỗi khác, kể cả `"1"`, `"yes"` hoặc `null`, cho false. Nếu cần báo lỗi khi dữ liệu không hợp lệ, hãy **tự kiểm tra đầu vào** thay vì coi mọi giá trị parse ra false đều là người dùng chọn false.

## 7. Lỗi phổ biến và bài tập

| Sai lầm | Cách hiểu đúng |
| --- | --- |
| `boolean b = 1;` | Java không ngầm quy đổi số thành boolean |
| `if (number)` với `number` kiểu `int` | Phải viết một biểu thức boolean, ví dụ `number != 0` |
| `boolean b = "false";` | Chuỗi khác với literal `false` |
| `Boolean b = null; if (b) { ... }` | Tự unbox `null` có thể ném `NullPointerException` |
| Thay `&&` bằng `&` khi cần bảo vệ null | `&` vẫn đánh giá vế phải |
| Nhầm `Boolean.parseBoolean("yes")` với true | Kết quả là false |

**Bài tập 1.** Dự đoán `!false`, `true && false`, `true || false` và `true ^ true`. Giải thích qua bảng chân trị.

**Bài tập 2.** Tạo biến `int amount = 0;` và một biến `boolean hasAmount = amount > 0;`. Thử đưa trực tiếp `amount` vào điều kiện `if` để xem lỗi compile.

**Bài tập 3.** Trong `BooleanDemo.java` đổi `false && probe()` thành `true && probe()`, rồi dự đoán `checks` ngay sau hai biểu thức short-circuit và sau biểu thức `&`.

**Bài tập 4.** Viết kiểm tra `String value = null;` bằng `value != null && !value.isBlank()`. Dự đoán điều gì xảy ra nếu dùng `&` thay cho `&&`.

**Bài tập 5.** Tạo `Boolean unknown = null;` rồi thử `Boolean.TRUE.equals(unknown)` để so sánh an toàn mà không unbox một giá trị null.

### Ghi nhớ

**`boolean` chỉ có `true` và `false`; Java không có quy tắc “số 0 là false, số khác 0 là true”.** Phép so sánh sinh boolean, `if`/`while` cần điều kiện boolean, `&&`/`||` có short-circuit. `Boolean` là **wrapper tham chiếu** có thể mang `null`, vì vậy đừng đồng nhất nó với primitive `boolean`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="primitive-types-007">Phân biệt kiểu nguyên thủy với kiểu tham chiếu</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt kiểu nguyên thủy với kiểu tham chiếu trong Java 17+

Java phân biệt hai nhóm kiểu dữ liệu:

- **Kiểu nguyên thủy (*primitive type*)**: biến chứa trực tiếp một giá trị thuộc một trong tám kiểu `byte`, `short`, `int`, `long`, `float`, `double`, `char`, `boolean`.
- **Kiểu tham chiếu (*reference type*)**: biến chứa một giá trị tham chiếu tới đối tượng hoặc mảng, hoặc chứa `null`. Ví dụ: `String`, `int[]`, một lớp tự định nghĩa, interface, enum, record và wrapper `Integer`.

Điểm cần nhớ: **gán một biến nguyên thủy sang biến cùng kiểu sao chép giá trị; gán một biến tham chiếu sang biến tham chiếu khác sao chép giá trị tham chiếu**, không tự sao chép đối tượng. Vì vậy, hai tên có thể cùng truy cập *một đối tượng*.

## 1. Bảng đối chiếu

| Tiêu chí | Kiểu nguyên thủy | Kiểu tham chiếu |
| --- | --- | --- |
| Ví dụ | `int`, `double`, `char`, `boolean` | `String`, `Integer`, `int[]`, `Counter` |
| Biến chứa gì? | Giá trị primitive | Tham chiếu đến đối tượng/mảng hoặc `null` |
| Có thể gán trực tiếp `null`? | **Không** | Có |
| Gán `b = a` | Sao chép giá trị | Sao chép **tham chiếu**, không nhân bản đối tượng |
| `==` | So sánh giá trị sau quy tắc chuyển kiểu thích hợp | Với hai tham chiếu tương thích: kiểm tra **cùng đối tượng** (hoặc cùng `null`) |
| Truy cập thành viên | Không gọi phương thức trên một giá trị primitive | Thường có field/phương thức, tùy kiểu |
| Giá trị mặc định của **field** | `0`, `0.0`, `false`, `'\u0000'` theo kiểu | `null` |
| Biến cục bộ chưa khởi tạo | Không thể đọc | Cũng **không thể đọc** |

Java **không tự gán giá trị mặc định cho biến cục bộ** thuộc bất kỳ nhóm nào. Một biến cục bộ phải được gán chắc chắn trước khi đọc (*definite assignment*). Còn field của lớp/đối tượng được khởi tạo mặc định theo kiểu khi cần.

Không nên diễn giải hai nhóm thành quy tắc tuyệt đối “primitive luôn ở stack, object luôn ở heap”: JVM có thể tối ưu vị trí lưu trữ. Điều cần hiểu là **ngữ nghĩa giá trị và tham chiếu**, không phải một sơ đồ RAM đơn giản.

## 2. Chương trình Java 17+ runnable: sao chép, alias và tham số

Tạo tệp **`ValueAndReferenceDemo.java`**:

~~~java
public class ValueAndReferenceDemo {
    static class Counter {
        int value;

        Counter(int value) {
            this.value = value;
        }
    }

    static void changePrimitive(int number) {
        number += 10;
        System.out.println("insidePrimitive=" + number);
    }

    static void changeObject(Counter counter) {
        counter.value += 10;
    }

    static void replaceReference(Counter counter) {
        counter = new Counter(99);
        System.out.println("insideReplace=" + counter.value);
    }

    public static void main(String[] args) {
        int original = 5;
        int copied = original;
        copied = 8;
        System.out.println("primitive=" + original + "," + copied);

        Counter first = new Counter(5);
        Counter alias = first;
        alias.value = 8;
        Counter independent = new Counter(8);

        System.out.println("sharedField=" + first.value);
        System.out.println("aliasIdentity=" + (first == alias));
        System.out.println("separateIdentity=" + (first == independent));
        System.out.println(
                "sameFieldValue=" + (first.value == independent.value));

        changePrimitive(original);
        System.out.println("afterPrimitive=" + original);

        changeObject(first);
        System.out.println("afterMutation=" + first.value);

        replaceReference(first);
        System.out.println("afterReassign=" + first.value);

        int[] numbers = {1, 2};
        int[] sameArray = numbers;
        sameArray[0] = 7;
        System.out.println("arrayFirst=" + numbers[0]);

        String text = "Java";
        String oldText = text;
        text = text + "17";
        System.out.println("oldText=" + oldText);
        System.out.println("newText=" + text);

        String a = new String("ok");
        String b = new String("ok");
        System.out.println("stringIdentity=" + (a == b));
        System.out.println("stringContent=" + a.equals(b));

        Integer boxed = 10;
        int unboxed = boxed;
        System.out.println("boxed=" + boxed + ",unboxed=" + unboxed);

        Integer missing = null;
        System.out.println("missingIsNull=" + (missing == null));
        try {
            int unsafe = missing;
            System.out.println("unsafe=" + unsafe);
        } catch (NullPointerException ex) {
            System.out.println("unboxing=NullPointerException");
        }
    }
}
~~~

Mở Windows PowerShell hoặc Linux/Bash ở thư mục chứa tệp. Cần **JDK 17+**:

~~~bash
java -version
javac -version
javac -encoding UTF-8 ValueAndReferenceDemo.java
java ValueAndReferenceDemo
~~~

**Expected output (đúng thứ tự):**

~~~text
primitive=5,8
sharedField=8
aliasIdentity=true
separateIdentity=false
sameFieldValue=true
insidePrimitive=15
afterPrimitive=5
afterMutation=18
insideReplace=99
afterReassign=18
arrayFirst=7
oldText=Java
newText=Java17
stringIdentity=false
stringContent=true
boxed=10,unboxed=10
missingIsNull=true
unboxing=NullPointerException
~~~

### Phân tích những điểm dễ nhầm

**Sao chép primitive:** `copied = original` nhận số `5`. Đổi `copied` thành `8` không thay đổi `original`; hai biến giữ các giá trị độc lập.

**Sao chép reference:** `alias = first` không tạo `Counter` mới. Hai biến cùng tham chiếu một đối tượng nên `alias.value = 8` làm `first.value` bằng `8`. Còn `independent = new Counter(8)` tạo đối tượng **khác**; dù hai field bằng 8, `first == independent` vẫn là `false`.

**Java luôn truyền tham số theo giá trị:** Trong `changePrimitive`, tham số nhận bản sao số nguyên nên cộng 10 không đổi biến `original`. Trong `changeObject`, tham số nhận bản sao **giá trị tham chiếu** tới cùng Counter; thay field của đối tượng nên bên gọi nhìn thấy 18. Trong `replaceReference`, chỉ biến tham số `counter` được gán tham chiếu khác: đối tượng mà `first` đang trỏ tới **không bị thay thế**, nên vẫn có giá trị 18.

**Mảng và chuỗi:** `int[]` là **kiểu tham chiếu** dù các phần tử là `int` nguyên thủy; sửa qua `sameArray` ảnh hưởng tới mảng chung. `String` cũng là kiểu tham chiếu nhưng bản thân đối tượng chuỗi **bất biến**: `text + "17"` tạo nội dung chuỗi mới và gán lại biến `text`, không sửa nội dung chuỗi cũ của `oldText`.

## 3. `==`, `equals` và việc so sánh đúng ý nghĩa

~~~java
int x = 100;
int y = 100;
System.out.println(x == y); // true: gia tri int bang nhau.

String left = new String("hi");
String right = new String("hi");
System.out.println(left == right);      // false: hai doi tuong khac.
System.out.println(left.equals(right)); // true: noi dung bang nhau.
~~~

- Với các số nguyên thủy, `==` so sánh giá trị sau các phép chuyển đổi số cần thiết. Với `boolean`, nó so sánh hai giá trị logic.
- Với hai tham chiếu có kiểu so sánh được, `==` kiểm tra **đồng nhất đối tượng**, không tự so sánh nội dung.
- `equals` là **phương thức**; cách so sánh do lớp quyết định. `String.equals` so sánh nội dung, nhưng một lớp như `Counter` trong ví dụ **không override `equals`** thì kế thừa hành vi nhận diện đồng nhất của `Object`.

**Đừng dùng `==` để so sánh nội dung hai `String` bất kỳ**, kể cả khi đôi lúc `"a" == "a"` trả true vì Java có cơ chế intern string literal. Để xử lý chuỗi có thể null, dùng `java.util.Objects.equals(left, right)` khi thích hợp.

## 4. `null`, boxing và unboxing

`null` biểu thị một tham chiếu không trỏ tới đối tượng:

~~~java
String missing = null; // Hop le.
Integer number = null; // Hop le: Integer la reference type.

// int count = null;    // Loi bien dich: int la primitive.
// boolean ok = null;   // Loi bien dich.
~~~

`Integer` là **wrapper class** cho `int`, không phải một primitive mới. Java có thể tự **boxing** và **unboxing**:

~~~java
Integer boxed = 7;   // boxing: int -> Integer.
int raw = boxed;     // unboxing: Integer -> int.
~~~

Nếu `boxed == null`, việc unboxing sang `int` **ném `NullPointerException` lúc chạy**. Đây là khác biệt giữa lỗi **biên dịch** (`int x = null;` bị từ chối) và lỗi **runtime** (`int x = nullableInteger;` được chấp nhận về kiểu nhưng có thể thất bại).

Một wrapper còn có thể khiến `==` dễ hiểu nhầm: so sánh các **tham chiếu wrapper với nhau** không phải lựa chọn tốt để kiểm tra giá trị số. Khi mục tiêu là giá trị, hãy dùng `equals` thích hợp hoặc unboxing có kiểm tra null. Không dựa vào chuyện JVM có thể dùng lại một số đối tượng wrapper.

## 5. `final` không nhất thiết làm đối tượng bất biến

~~~java
final int limit = 3;
// limit = 4; // Loi: primitive final khong duoc gan lai.

final int[] scores = {1, 2};
scores[0] = 9; // Hop le: sua doi tuong mang.
// scores = new int[]{3}; // Loi: khong gan lai reference final.
~~~

`final` ràng buộc việc **gán lại biến**. Với kiểu tham chiếu, đối tượng được trỏ tới có thể vẫn thay đổi nếu API/kiểu cho phép; với `String`, đối tượng bất biến là do **thiết kế của lớp String**, không phải vì mọi tham chiếu tới nó đều `final`.

## 6. Lỗi phổ biến và cách sửa

| Triệu chứng | Vì sao? | Hướng xử lý |
| --- | --- | --- |
| `int n = null;` không biên dịch | Primitive không chấp nhận `null` | Chọn giá trị hợp lệ hoặc kiểu tham chiếu nếu thật sự cần thiếu giá trị |
| `String s = null; s.length();` | Dereference tham chiếu null | Kiểm tra `s != null` hoặc quy ước đầu vào không null |
| `Integer x = null; int n = x;` | Unboxing giá trị null | Kiểm tra null trước khi chuyển |
| Hai biến thay đổi chung một đối tượng | Cùng trỏ tới một object | Tạo bản sao thực sự khi cần dữ liệu độc lập |
| `new String("x") == new String("x")` cho false | `==` so sánh hai đối tượng | Dùng `equals` để so sánh chuỗi |
| Đổi tham số sang `new` nhưng biến bên gọi không đổi | Tham số nhận bản sao reference | Trả về reference mới và gán lại ở phía gọi nếu cần |
| `int[]` bị xem như primitive | Mảng luôn là reference type | Phân biệt kiểu mảng và kiểu phần tử |

### Quy tắc thực hành

1. Hỏi **biến chứa giá trị trực tiếp hay tham chiếu?** Đó là bước đầu để dự đoán tác động của phép gán.
2. Hỏi **hai biến có đang trỏ cùng đối tượng không?** Khi cùng trỏ, sửa trạng thái đối tượng có thể quan sát qua cả hai tên.
3. Hỏi **đối tượng có mutable không?** Chia sẻ tham chiếu tới `String` khác chia sẻ tham chiếu tới mảng hoặc đối tượng có field thay đổi.
4. Hỏi **tham chiếu có thể null không?** Kiểm tra trước khi gọi phương thức, truy cập field hoặc unboxing.

## 7. Bài tập tự kiểm tra

**Bài 1.** Với `int a = 3; int b = a; b++;`, `a` bằng bao nhiêu?  
*Đáp án:* 3. `b` nhận bản sao giá trị.

**Bài 2.** Với `int[] a = {1}; int[] b = a; b[0] = 5;`, `a[0]` bằng bao nhiêu?  
*Đáp án:* 5. Hai biến cùng trỏ một mảng.

**Bài 3.** Vì sao `new String("ok") == new String("ok")` là false nhưng `equals` là true?  
*Đáp án:* Hai `new` tạo hai đối tượng; `==` kiểm tra identity, `String.equals` so sánh nội dung.

**Bài 4.** Có thể truyền một đối tượng vào phương thức Java rồi gán `param = new Counter(...)` để thay luôn biến của bên gọi không?  
*Đáp án:* Không. Java truyền **bản sao giá trị tham chiếu**; gán lại tham số không đổi biến gốc.

**Bài 5.** Tại sao `Integer n = null;` hợp lệ nhưng `int x = n;` có thể gây lỗi?  
*Đáp án:* `Integer` là reference type chấp nhận null; unboxing null sang `int` gây `NullPointerException`.

## Ghi nhớ

**Primitive variable giữ giá trị; reference variable giữ giá trị tham chiếu.** Java luôn truyền tham số theo giá trị, nên truyền đối tượng nghĩa là sao chép *tham chiếu*, không nhân bản đối tượng. Hãy phân biệt `==` (identity với tham chiếu) và `equals` (theo cách lớp định nghĩa), chú ý `null`/unboxing và kiểm tra liệu trạng thái đối tượng có thể thay đổi. Đây là nền tảng để hiểu mảng, `String`, object, method và cách chia sẻ dữ liệu trong Java.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="primitive-types-008">Chọn kiểu theo miền dữ liệu và yêu cầu tính toán</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn kiểu theo miền dữ liệu và yêu cầu tính toán trong Java

> **Java core — language-basics / Kiểu nguyên thủy**  
> **Yêu cầu:** JDK 17+, không cần thư viện ngoài cho ví dụ chạy chính.  
> **Mục tiêu:** Chọn kiểu đủ miền giá trị, độ chính xác và phép toán; tránh tràn số, chia nguyên ngoài ý muốn và sai số thập phân. Khi cần tính chính xác vượt đặc tính của kiểu nguyên thủy, biết lúc nào chuyển sang kiểu thư viện chuẩn.

## 1. Không chọn kiểu chỉ vì giá trị hiện tại vừa với nó

Một biến có kiểu khai báo cố định, nhưng **kết quả các phép tính trong tương lai** mới là yếu tố quan trọng. Trước khi chọn, tự hỏi:

1. Dữ liệu có **số nguyên, số lẻ, đúng/sai hay ký tự**?
2. Biên **nhỏ nhất/lớn nhất** của giá trị đầu vào và **kết quả trung gian** là bao nhiêu?
3. Có cần **chính xác tuyệt đối** (ví dụ số tiền) hay chấp nhận sai số làm tròn?
4. Có phải lưu số lượng rất lớn trong **mảng/buffer** hoặc giao tiếp với định dạng nhị phân cần độ rộng bit cụ thể?
5. Khi kết quả vượt miền, phải **báo lỗi** hay được phép chấp nhận quy tắc tràn/làm tròn?

Ví dụ: số lượng một đơn hàng có thể chỉ vài trăm, nhưng `quantity * priceInCents` có thể lớn hơn phạm vi `int`. Đừng chọn kiểu kết quả theo riêng biến `quantity`.


## 2. Ma trận quyết định dựa trên yêu cầu

Thay vì chọn kiểu chỉ theo số bit, hãy ghi rõ khả năng **biểu diễn**, **tính toán** và **tích hợp dữ liệu**:

| Nhu cầu thật sự | Chọn ban đầu | Điều phải kiểm chứng |
| --- | --- | --- |
| Đếm/số nguyên trong API Java thông thường | `int` | Tổng/tích lớn nhất có vượt khoảng ±2,1 tỷ không? |
| Đếm/tổng lớn hơn miền `int` | `long` | Trung gian đã nâng sang `long` chưa; liệu `long` có đủ? |
| Dữ liệu byte, giao thức số nguyên 16 bit | `byte`/`short` | Định dạng có dấu hay không dấu; cần diễn giải/kiểm tra miền ra sao? |
| Giá trị đo có dung sai | `double`, đôi khi `float` | Độ phân giải và cách xử lý `NaN`/`Infinity` có đáp ứng không? |
| Tiền hoặc phép tính decimal chính xác | `long` theo đơn vị nhỏ nhất hoặc `BigDecimal` | Quy tắc scale, làm tròn, miền giá trị và phép chia là gì? |
| Trạng thái đúng/sai | `boolean` | Có cần biểu diễn trạng thái chưa xác định bằng `Boolean` không? |
| Văn bản/Unicode | `String`; `char` cho code unit | Cần code unit, code point hay ký tự hiển thị? |

Bảng này là cách đưa **ràng buộc của bài toán** vào quyết định. Các bài trước đã giải thích cấu trúc và giới hạn của từng primitive; phần tiếp theo kiểm tra hậu quả khi chọn sai kiểu toán hạng.

## 3. Chọn `int` hay `long`: xét cả phép tính trung gian

Các literal số nguyên thập phân mặc định thường thuộc `int`. Dùng hậu tố `L` khi cần literal `long`:

~~~java
int count = 12_000;
long customers = 3_000_000_000L;
// long wrong = 3_000_000_000; // Sai: literal đã quá lớn cho int.
~~~

Quan trọng hơn, **kiểu biến nhận kết quả không tự nâng kiểu các toán hạng trước khi tính**:

~~~java
int side = 50_000;
long wrongArea = side * side;        // Phép nhân int đã tràn trước khi gán long.
long correctArea = (long) side * side; // Ít nhất một toán hạng long -> nhân long.
~~~

Giá trị đúng là `2_500_000_000`, vượt `Integer.MAX_VALUE`. `wrongArea` nhận `-1_794_967_296`: việc chuyển kết quả `int` đã tràn sang `long` không thể phục hồi thông tin bị mất.

Nếu muốn **phát hiện tràn**, có thể dùng `Math.multiplyExact`, `Math.addExact` hoặc `Math.subtractExact`; các phương thức đó ném `ArithmeticException` khi vượt miền `int`/`long` tương ứng. Chỉ ép lên `long` giải quyết tình huống `int` quá hẹp, **không** bảo đảm mọi phép nhân `long` đều an toàn.

## 4. Chọn kiểu theo cách phép toán được thực hiện

### Chia số nguyên khác chia số thực

~~~java
int whole = 7 / 4;          // 1: phép chia hai int, phần lẻ bị bỏ.
double stillWhole = 7 / 4;  // 1.0: phép chia int thực hiện trước khi gán.
double fraction = 7.0 / 4;  // 1.75: có toán hạng double.
~~~

Đừng kỳ vọng kiểu `double` của **biến đích** sửa được phép chia nguyên ở vế phải. Cần đưa ít nhất một toán hạng thành `double` trước phép tính nếu muốn kết quả phân số.

### `float` và `double` đều là số thực xấp xỉ

`float` tiết kiệm dung lượng hơn `double` nhưng có ít bit chính xác hơn. Ngay cả `double` cũng **không biểu diễn chính xác mọi số thập phân**, ví dụ kết quả của `0.1 + 0.2` thường không bằng chính xác literal `0.3` theo phép so sánh `==`.

Khi xử lý **tiền tệ/số thập phân cần chính xác**:

- Dùng **`long` chứa đơn vị nhỏ nhất** (ví dụ cents) nếu miền giá trị đủ, các quy tắc làm tròn/chia được xác định rõ và phép tính được kiểm soát tràn.
- Hoặc dùng **`BigDecimal`** trong `java.math`: đây là **kiểu tham chiếu**, không phải primitive. Khởi tạo từ chuỗi như `new BigDecimal("0.1")` để biểu diễn chính xác giá trị thập phân mong muốn; tránh `new BigDecimal(0.1)` khi muốn đúng 0,1 thập phân.
- Khi phép chia thập phân không kết thúc (chẳng hạn `1 / 3`), `BigDecimal.divide` cần quy định độ chính xác/cách làm tròn phù hợp.

Đừng dùng `float` chỉ vì giá trị hiện tại “nhỏ”; **độ chính xác** khác với **độ lớn miền biểu diễn**.

## 5. Chương trình Java 17+ chạy được

Tạo file **TypeChoiceDemo.java**:

~~~java
import java.math.BigDecimal;

public final class TypeChoiceDemo {
    public static void main(String[] args) {
        int units = 1_200;
        long centsPerUnit = 2_499L;
        long totalCents = units * centsPerUnit;

        int side = 50_000;
        long wrongArea = side * side;
        long correctArea = (long) side * side;

        int integerDivide = 7 / 4;
        double floatingDivide = 7.0 / 4;

        float roundedFloat = 16_777_217f;
        double approximate = 0.1 + 0.2;
        BigDecimal exact = new BigDecimal("0.1")
                .add(new BigDecimal("0.2"));

        boolean qualifies = totalCents >= 3_000_000L;
        char grade = 'A';

        System.out.println("totalCents=" + totalCents);
        System.out.println("intArea=" + wrongArea);
        System.out.println("longArea=" + correctArea);

        try {
            Math.multiplyExact(side, side);
        } catch (ArithmeticException ex) {
            System.out.println("checked=overflow");
        }

        System.out.println("integerDivide=" + integerDivide);
        System.out.println("floatingDivide=" + floatingDivide);
        System.out.println("floatRounded=" + (long) roundedFloat);
        System.out.println("doubleEquals0.3=" + (approximate == 0.3));
        System.out.println("exactDecimal=" + exact.toPlainString());
        System.out.println("qualifies=" + qualifies + ",grade=" + grade);
    }
}
~~~

Dùng terminal trong thư mục chứa tệp (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 TypeChoiceDemo.java
java TypeChoiceDemo
~~~

**Expected output chính xác:**

~~~text
totalCents=2998800
intArea=-1794967296
longArea=2500000000
checked=overflow
integerDivide=1
floatingDivide=1.75
floatRounded=16777216
doubleEquals0.3=false
exactDecimal=0.3
qualifies=false,grade=A
~~~

Giải thích: `units * centsPerUnit` dùng phép nhân `long` vì một toán hạng là `long`; kết quả 2.998.800 cents. `side * side` thực hiện `int` nên tràn; ép `side` sang `long` **trước phép nhân** cho 2.500.000.000. `Math.multiplyExact` báo tràn qua exception. `7 / 4` bỏ phần lẻ; `7.0 / 4` là phép chia `double`. Giá trị 16.777.217 không biểu diễn chính xác bằng `float` 32 bit và được làm tròn về 16.777.216. `BigDecimal` tạo từ chuỗi cho kết quả thập phân `0.3` chính xác.

## 6. Chuyển kiểu: khi nào có thể mất dữ liệu?

| Tình huống | Ví dụ | Điều cần lưu ý |
| --- | --- | --- |
| Mở rộng nguyên | `int x = 100; long y = x;` | Bảo toàn giá trị |
| Nguyên lớn sang số thực | `long x = 9_007_199_254_740_993L; double y = x;` | Có thể **mất độ chính xác** |
| Thu hẹp nguyên | `int x = 130; byte y = (byte) x;` | `y == -126` do giữ 8 bit thấp |
| Thực sang nguyên | `int n = (int) 3.9;` | `n == 3`, bỏ phần lẻ |
| `long` về `int` có kiểm tra | `int n = Math.toIntExact(big);` | Ném exception nếu không vừa `int` |

Các toán hạng `byte`, `short`, `char` trong phép cộng/nhân thông thường được nâng lên `int`, nên ví dụ `byte a = 10, b = 20;` rồi `byte c = a + b;` **không hợp lệ** nếu không chuyển kiểu phù hợp.

## 7. Checklist lựa chọn thực tế

1. **Đếm/chỉ số/số nguyên thông thường:** chọn `int`; nếu dự báo vượt miền, chọn `long` và nâng kiểu **trước** phép tính có thể tràn.
2. **Dữ liệu số thực đo lường/xấp xỉ:** ưu tiên `double`; chỉ chọn `float` khi có lý do về bộ nhớ, định dạng hoặc giao diện API.
3. **Số tiền hoặc giá trị cần đúng tới chữ số thập phân:** cân nhắc `long` theo đơn vị nhỏ nhất hoặc `BigDecimal` với quy tắc làm tròn rõ ràng.
4. **Cờ điều kiện:** `boolean`, không dùng `0`/`1` thay thế.
5. **Đơn vị mã ký tự:** `char`; nếu cần code point Unicode đầy đủ, dùng API String tương ứng.
6. **Phép chia, nhân, cộng trung gian:** kiểm tra **kiểu toán hạng**, không chỉ kiểu biến đích; thử các giá trị biên và trường hợp tràn.
7. **Giá trị ngoài `long`:** cân nhắc `BigInteger` nếu cần số nguyên chính xác rất lớn.

### Tự luyện

**Bài 1:** Chọn kiểu cho (a) số lần đăng nhập; (b) tổng cents của giao dịch rất lớn; (c) cân nặng đo xấp xỉ; (d) cờ đã thanh toán. Nêu ràng buộc miền và độ chính xác trước khi quyết định.

**Bài 2:** Giải thích vì sao `long x = 50_000 * 50_000;` có thể sai, rồi sửa mà **không** thay đổi ý nghĩa phép nhân.

**Bài 3:** Thử `double d = 5 / 2;` và `double d = 5.0 / 2;` trong hai trường hợp độc lập. Dự đoán `println`.

**Bài 4:** Đổi `centsPerUnit` thành `3_000_000L` trong chương trình; dự đoán tổng và đánh giá liệu `int` có còn đủ làm **kiểu kết quả** không.

**Tổng kết:** Chọn primitive theo **miền dữ liệu + độ chính xác + phép toán**, không chỉ theo độ lớn của giá trị ban đầu. Đặc biệt, lỗi tràn `int` và chia nguyên xuất hiện **trước khi gán**; còn `float`/`double` không phải giải pháp tính thập phân tuyệt đối chính xác.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="primitive-types-009">Giới hạn giá trị với MIN_VALUE, MAX_VALUE và SIZE</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn giá trị với `MIN_VALUE`, `MAX_VALUE` và `SIZE` — Java 17+

## 1. Vấn đề cần giải quyết

Mỗi kiểu nguyên thủy Java có một **miền giá trị** hoặc một tập giá trị xác định. Muốn chọn kiểu lưu dữ liệu, kiểm tra đầu vào, hoặc tránh tràn số, bạn cần biết:

- **`MIN_VALUE`**: giới hạn dưới theo định nghĩa của kiểu (với số dấu phẩy động có một ngoại lệ rất quan trọng).
- **`MAX_VALUE`**: giá trị hữu hạn lớn nhất được biểu diễn theo kiểu đó.
- **`SIZE`**: số **bit** dùng để biểu diễn một giá trị của kiểu, **không phải số byte** và không phải kích thước object.

Trong Java, các hằng này nằm trong **wrapper classes** thuộc `java.lang`: `Byte`, `Short`, `Integer`, `Long`, `Character`, `Float` và `Double`. Bạn có thể tra chúng mà không cần tự nhập những số dài:

~~~java
System.out.println(Integer.MIN_VALUE); // -2147483648
System.out.println(Integer.MAX_VALUE); // 2147483647
System.out.println(Integer.SIZE);      // 32
~~~

**Điểm chính:** không phải mọi kiểu đều hiểu `MIN_VALUE` giống nhau. Đặc biệt, `Float.MIN_VALUE` và `Double.MIN_VALUE` **không phải giá trị âm thấp nhất**.

## 2. Bảng đầy đủ cho bốn kiểu số nguyên có dấu

| Kiểu primitive | Wrapper | `MIN_VALUE` | `MAX_VALUE` | `SIZE` (bit) | `BYTES` |
|---|---|---:|---:|---:|---:|
| `byte` | `Byte` | -128 | 127 | 8 | 1 |
| `short` | `Short` | -32.768 | 32.767 | 16 | 2 |
| `int` | `Integer` | -2.147.483.648 | 2.147.483.647 | 32 | 4 |
| `long` | `Long` | -9.223.372.036.854.775.808 | 9.223.372.036.854.775.807 | 64 | 8 |

**`SIZE` đo bit; `BYTES` đo byte trong biểu diễn giá trị.** Ví dụ `Integer.SIZE == 32` và `Integer.BYTES == 4`.

Với số nguyên có dấu có độ rộng `n` bit và dùng biểu diễn bù hai, miền giá trị là:

\[
-2^{n-1} \;\leq x \leq\; 2^{n-1}-1.
\]

Nhờ đó `byte` 8 bit có giá trị từ \(-2^7=-128\) đến \(2^7-1=127\). Miền âm có một giá trị nhiều hơn miền dương. Vì thế `Integer.MIN_VALUE` không thể đổi thành số dương bằng `Math.abs` trong cùng kiểu `int`.

### Ví dụ tra cứu không cần ghi nhớ số

~~~java
int smallestInt = Integer.MIN_VALUE;
int largestInt = Integer.MAX_VALUE;
long smallestLong = Long.MIN_VALUE;
long largestLong = Long.MAX_VALUE;

System.out.println(smallestInt); // -2147483648
System.out.println(largestInt);  // 2147483647
System.out.println(Long.SIZE);   // 64
~~~

**Giới hạn cố định trên mọi nền tảng Java:** `int` luôn là giá trị 32 bit và `long` luôn là giá trị 64 bit theo đặc tả, bất kể Windows, Linux, hệ điều hành hay JVM 32/64 bit.

## 3. `char`: 16 bit nhưng không thuộc nhóm số nguyên có dấu

`char` là một kiểu nguyên thủy đại diện cho **một đơn vị mã UTF-16**, không phải kiểu `short` có dấu. Java có:

| Hằng | Giá trị |
|---|---|
| `Character.MIN_VALUE` | U+0000 (giá trị số 0) |
| `Character.MAX_VALUE` | U+FFFF (giá trị số 65535) |
| `Character.SIZE` | 16 bit |
| `Character.BYTES` | 2 byte |

~~~java
System.out.println((int) Character.MIN_VALUE); // 0
System.out.println((int) Character.MAX_VALUE); // 65535
System.out.println(Character.SIZE);           // 16
~~~

**Lưu ý Unicode:** một `char` là một **UTF-16 code unit**; một ký tự Unicode ngoài vùng BMP có thể cần **hai `char`** (surrogate pair). Vì vậy `Character.SIZE == 16` không có nghĩa Java chỉ hỗ trợ Unicode đến U+FFFF, cũng không có nghĩa mọi ký tự người dùng nhìn thấy chiếm đúng một `char`.

`Character.MIN_VALUE` là ký tự điều khiển có mã 0, không phải một ký tự thường dễ nhìn thấy trên màn hình. Khi muốn in giới hạn của `char`, hãy **ép sang `int`** như ví dụ, thay vì cố hiển thị trực tiếp ký tự U+0000.

## 4. `float` và `double`: ý nghĩa `MIN_VALUE` khác số nguyên

Đây là bẫy quan trọng nhất của bài.

| Hằng | `float` | `double` |
|---|---:|---:|
| `SIZE` | 32 bit | 64 bit |
| `BYTES` | 4 byte | 8 byte |
| `MAX_VALUE` | `3.4028235E38` | `1.7976931348623157E308` |
| `MIN_VALUE` | `1.4E-45` | `4.9E-324` |
| `MIN_NORMAL` | `1.17549435E-38` | `2.2250738585072014E-308` |

**`Float.MIN_VALUE` và `Double.MIN_VALUE` là số dương nhỏ nhất khác 0 có thể biểu diễn**, ở dạng **subnormal**. Chúng **không phải** `-Float.MAX_VALUE` hoặc `-Double.MAX_VALUE`.

~~~java
System.out.println(Float.MIN_VALUE);  // 1.4E-45
System.out.println(Float.MAX_VALUE);  // 3.4028235E38
System.out.println(-Float.MAX_VALUE); // -3.4028235E38
~~~

Nếu cần **số hữu hạn thấp nhất** theo thứ tự số học, dùng **`-Float.MAX_VALUE`** hoặc **`-Double.MAX_VALUE`**.

### Vì sao có `MIN_NORMAL`?

Số dấu phẩy động nhị phân có dấu, số mũ và phần định trị. Giá trị rất gần 0 có thể nằm trong vùng **subnormal**, nơi độ chính xác hiệu dụng giảm để tạo ra các bước nhỏ tiếp cận 0. `MIN_NORMAL` là số dương nhỏ nhất ở vùng normal, còn `MIN_VALUE` là số dương subnormal nhỏ nhất khác 0.

Cặp hằng này đo **ngưỡng độ lớn của giá trị**, không bảo đảm một phép tính thập phân là chính xác. Chẳng hạn `0.1 + 0.2` ở `double` có thể khác chính xác `0.3` vì giới hạn độ chính xác nhị phân.

### Vô cực và NaN vẫn tồn tại

`MAX_VALUE` nghĩa là **giá trị hữu hạn lớn nhất**, không có nghĩa tập giá trị `double` chỉ từ `-Double.MAX_VALUE` đến `Double.MAX_VALUE`. Kiểu dấu phẩy động còn có `Infinity`, `-Infinity` và `NaN`.

~~~java
double tooLarge = Double.MAX_VALUE * 2;
System.out.println(tooLarge);           // Infinity
System.out.println(Double.isFinite(tooLarge)); // false
System.out.println(Double.isNaN(0.0 / 0.0));  // true
~~~

Nếu yêu cầu một số thực hữu hạn, **kiểm tra `Double.isFinite(value)`** (hoặc `Float.isFinite`) trước khi sử dụng. Chỉ kiểm tra `value <= Double.MAX_VALUE` chưa đủ để xác định dữ liệu hợp lệ vì NaN và các quy tắc so sánh đặc biệt.

## 5. `boolean` không có MIN_VALUE, MAX_VALUE hoặc SIZE

`boolean` chỉ có **`true` và `false`**; wrapper `Boolean` định nghĩa các giá trị như `Boolean.TRUE`, `Boolean.FALSE`.

Không có API chuẩn:

~~~java
// Boolean.MIN_VALUE // Không tồn tại
// Boolean.MAX_VALUE // Không tồn tại
// Boolean.SIZE      // Không tồn tại
~~~

Ngôn ngữ Java không đưa ra một giá trị `Boolean.SIZE` tương tự `Integer.SIZE`. Đừng tự suy ra “boolean luôn chiếm 1 bit bộ nhớ” từ việc kiểu chỉ có hai trạng thái: cách JVM lưu field, biến cục bộ, array, header và căn chỉnh là chuyện triển khai khác với **miền giá trị trừu tượng** của kiểu `boolean`.

## 6. SIZE và BYTES không đo kích thước object trong heap

Ví dụ:

~~~java
System.out.println(Byte.SIZE);      // 8
System.out.println(Integer.SIZE);   // 32
System.out.println(Long.BYTES);     // 8
System.out.println(Double.BYTES);   // 8
~~~

Các hằng này mô tả **độ rộng biểu diễn giá trị**, không phải kết quả của `sizeof` như một số ngôn ngữ khác. Java không có toán tử `sizeof` cho object.

- Một biến primitive có độ rộng giá trị xác định.
- Một instance `Integer` có thể có overhead đối tượng, tham chiếu và yêu cầu căn chỉnh riêng.
- Một field `byte` trong object không nhất thiết làm kích thước object tăng đúng 1 byte, do bố trí bộ nhớ của JVM.
- Mảng có overhead và layout ngoài số byte tương ứng với từng phần tử.

Nếu làm việc với **buffer nhị phân**, `Integer.BYTES` hữu ích để tính số byte cho giá trị 32 bit. Nhưng nếu đo bộ nhớ thực tế trên heap, cần công cụ đo/quan sát JVM phù hợp, không nhân `SIZE` rồi kết luận tổng dung lượng object.

## 7. Dùng MIN_VALUE/MAX_VALUE kiểm tra trước khi thu hẹp kiểu

Thay vì ép kiểu rồi hy vọng giá trị không mất dữ liệu, hãy **kiểm tra miền trước khi cast**.

~~~java
static boolean fitsInByte(long value) {
    return value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE;
}

static byte toByteChecked(long value) {
    if (!fitsInByte(value)) {
        throw new IllegalArgumentException("Out of byte range: " + value);
    }
    return (byte) value;
}
~~~

Ví dụ:

~~~java
System.out.println(fitsInByte(127));  // true
System.out.println(fitsInByte(128));  // false
System.out.println(toByteChecked(127)); // 127
~~~

Nếu thay bằng `(byte) 128` mà không kiểm tra, kết quả thành `-128`, vì phép thu hẹp giữ các bit thấp và diễn giải theo `byte` có dấu.

Các wrapper limit giúp kiểm tra rõ ràng hơn việc hardcode `-128`/`127`, nhất là khi sau này đổi kiểu tham số hoặc đọc giá trị từ bên ngoài.

### Dùng API kiểm tra chuyển kiểu sẵn có

Khi chuyển một `long` sang `int`, bạn còn có:

~~~java
long input = (long) Integer.MAX_VALUE + 1L;
// int invalid = Math.toIntExact(input); // Ném ArithmeticException khi chạy
~~~

`Math.toIntExact` báo tràn thay vì âm thầm cắt bit. Nó không thay thế việc xác định giá trị nào là hợp lệ theo quy tắc nghiệp vụ, nhưng tránh một lỗi mất dữ liệu phổ biến.


## 8. Ứng dụng `BYTES`: mô tả bản ghi nhị phân

Các hằng `SIZE`/`BYTES` hữu ích khi cần biết **bao nhiêu byte dữ liệu giá trị** sẽ ghi ra một định dạng nhị phân. Ví dụ, bản ghi gồm một số `int` và một số `long` cần `Integer.BYTES + Long.BYTES == 12` byte cho hai giá trị (chưa tính phần header/metadata nếu định dạng có thêm).

~~~java
import java.nio.ByteBuffer;

public class BinaryRecordSizeDemo {
    public static void main(String[] args) {
        ByteBuffer record = ByteBuffer.allocate(Integer.BYTES + Long.BYTES);
        record.putInt(42);
        record.putLong(9_000_000_000L);
        System.out.println(record.position()); // 12
    }
}
~~~

Biên dịch và chạy `BinaryRecordSizeDemo.java` để kiểm chứng đầu ra `12`. `ByteBuffer` mặc định dùng thứ tự byte big-endian; khi đọc/ghi định dạng mạng hoặc file, phải thống nhất byte order với bên còn lại. `BYTES` không nói kích thước thực tế của object `Integer`, `Long` hoặc `ByteBuffer` trong bộ nhớ JVM.

Giới hạn kiểu cũng không tự phát hiện overflow: `Integer.MAX_VALUE + 1` vẫn quay vòng khi cộng `int`; nếu cần kết quả đáng tin, hãy kiểm tra miền hoặc dùng `Math.*Exact`. Chương trình dưới đây có ví dụ xác minh những tình huống biên này.

## 9. Ví dụ Java 17 hoàn chỉnh — tra giới hạn và phát hiện vượt miền

Tạo file **`PrimitiveLimitsDemo.java`** với mã sau:

~~~java
public final class PrimitiveLimitsDemo {
    private PrimitiveLimitsDemo() {}

    static boolean fitsInByte(long value) {
        return value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE;
    }

    static byte toByteChecked(long value) {
        if (!fitsInByte(value)) {
            throw new IllegalArgumentException("Out of byte range: " + value);
        }
        return (byte) value;
    }

    public static void main(String[] args) {
        System.out.println("byte: " + Byte.MIN_VALUE
                + ".." + Byte.MAX_VALUE + ", " + Byte.SIZE + " bits");
        System.out.println("short: " + Short.MIN_VALUE
                + ".." + Short.MAX_VALUE + ", " + Short.SIZE + " bits");
        System.out.println("int: " + Integer.MIN_VALUE
                + ".." + Integer.MAX_VALUE + ", " + Integer.SIZE + " bits");
        System.out.println("long: " + Long.MIN_VALUE
                + ".." + Long.MAX_VALUE + ", " + Long.SIZE + " bits");

        System.out.println("char code units: "
                + (int) Character.MIN_VALUE + ".."
                + (int) Character.MAX_VALUE
                + ", " + Character.SIZE + " bits");
        System.out.println("byte bytes=" + Byte.BYTES);
        System.out.println("int bytes=" + Integer.BYTES);

        System.out.println("float: " + Float.SIZE + " bits"
                + ", min positive=" + Float.MIN_VALUE
                + ", max finite=" + Float.MAX_VALUE);
        System.out.println("float min normal=" + Float.MIN_NORMAL);
        System.out.println("double: " + Double.SIZE + " bits"
                + ", min positive=" + Double.MIN_VALUE
                + ", max finite=" + Double.MAX_VALUE);

        System.out.println("fits 127=" + fitsInByte(127));
        System.out.println("fits 128=" + fitsInByte(128));
        System.out.println("checked byte=" + toByteChecked(127));
        try {
            toByteChecked(128);
        } catch (IllegalArgumentException ex) {
            System.out.println("range error=" + ex.getMessage());
        }

        System.out.println("int wrapped=" + (Integer.MAX_VALUE + 1));
        System.out.println("abs int min=" + Math.abs(Integer.MIN_VALUE));

        try {
            Math.addExact(Integer.MAX_VALUE, 1);
        } catch (ArithmeticException ex) {
            System.out.println("checked add=overflow");
        }

        try {
            Math.toIntExact((long) Integer.MAX_VALUE + 1L);
        } catch (ArithmeticException ex) {
            System.out.println("checked narrowing=overflow");
        }

        double tooLarge = Double.MAX_VALUE * 2.0;
        System.out.println("finite overflow=" + tooLarge);
        System.out.println("is finite=" + Double.isFinite(tooLarge));
    }
}
~~~

Mở terminal trong thư mục chứa file và dùng **JDK 17 trở lên**.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 PrimitiveLimitsDemo.java
java -cp . PrimitiveLimitsDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\PrimitiveLimitsDemo.java
java -cp . PrimitiveLimitsDemo
~~~

**Expected output chính xác:**

~~~text
byte: -128..127, 8 bits
short: -32768..32767, 16 bits
int: -2147483648..2147483647, 32 bits
long: -9223372036854775808..9223372036854775807, 64 bits
char code units: 0..65535, 16 bits
byte bytes=1
int bytes=4
float: 32 bits, min positive=1.4E-45, max finite=3.4028235E38
float min normal=1.17549435E-38
double: 64 bits, min positive=4.9E-324, max finite=1.7976931348623157E308
fits 127=true
fits 128=false
checked byte=127
range error=Out of byte range: 128
int wrapped=-2147483648
abs int min=-2147483648
checked add=overflow
checked narrowing=overflow
finite overflow=Infinity
is finite=false
~~~

Mọi dòng trong ví dụ đều do API và hằng chuẩn của JDK tạo ra; không phụ thuộc file dữ liệu hoặc thư viện ngoài. Đầu ra là kết quả kỳ vọng để tự đối chiếu sau khi biên dịch/chạy.

## 10. Các sai lầm thường gặp

| Nhận định/cách viết | Vấn đề | Cách hiểu đúng |
|---|---|---|
| `Byte.MAX_VALUE == 255` | Sai, `byte` là kiểu có dấu | `Byte.MAX_VALUE == 127` |
| `Integer.SIZE == 4` | Nhầm bit với byte | `Integer.SIZE == 32`; `Integer.BYTES == 4` |
| `Long.MIN_VALUE == -Long.MAX_VALUE` | Quên miền âm có thêm một giá trị | `Long.MIN_VALUE == -Long.MAX_VALUE - 1` |
| `Float.MIN_VALUE` là âm vô cùng hoặc giá trị âm thấp nhất | Sai nghĩa hằng của floating point | Đây là số **dương** nhỏ nhất khác 0 |
| `Double.MAX_VALUE` bằng `Infinity` | Một giá trị hữu hạn bị nhầm với vô cực | `Double.isFinite(Double.MAX_VALUE)` là true |
| `Character.SIZE == 8` | `char` dùng một code unit UTF-16 | `Character.SIZE == 16` |
| `Boolean.SIZE` có tồn tại | Java không công bố hằng đó | Chỉ có true/false, không có `Boolean.SIZE` |
| `(byte) 128` vẫn là 128 | Ép thu hẹp cắt bit | Kết quả là -128 |
| `Integer.MAX_VALUE + 1` được tự mở rộng thành long | Toán hạng vẫn là int | Tràn int, kết quả là `Integer.MIN_VALUE` |
| `Math.abs(Integer.MIN_VALUE)` luôn dương | Trị tuyệt đối không vừa int | Kết quả vẫn là `Integer.MIN_VALUE` |

### Quy tắc kiểm tra dữ liệu số thực

Nếu người dùng nhập giá trị `double` từ bên ngoài, hãy phân biệt:

- **Không hợp lệ về định dạng**: không phân tích được thành số.
- **Không hữu hạn**: `NaN` hoặc `Infinity`.
- **Hữu hạn nhưng ngoài miền nghiệp vụ**: ví dụ số lượng âm hoặc vượt mức cho phép.

`MIN_VALUE`/`MAX_VALUE` của kiểu **không tự xác định miền nghiệp vụ**. Chẳng hạn, số lượng sản phẩm phải không âm có thể bị ràng buộc từ 0 đến 100, không nhất thiết sử dụng toàn bộ miền của `int`.

## 11. Bài tập kèm đáp án

### Bài A — phân biệt bit và byte

Hỏi `Short.SIZE`, `Short.BYTES`, `Long.SIZE`, `Long.BYTES`.

**Đáp án:** lần lượt `16`, `2`, `64`, `8`.

### Bài B — giới hạn của char

~~~java
System.out.println((int) Character.MAX_VALUE);
System.out.println(Character.SIZE);
~~~

**Đáp án:**

~~~text
65535
16
~~~

Vì `char` là UTF-16 code unit 16 bit, khác kiểu `short` có dấu.

### Bài C — giới hạn float

`Float.MIN_VALUE` có âm không? Muốn biểu diễn số hữu hạn âm nhỏ nhất theo thứ tự số học thì dùng gì?

**Đáp án:** không; `Float.MIN_VALUE` là số dương subnormal nhỏ nhất khác 0. Số hữu hạn âm thấp nhất là `-Float.MAX_VALUE`.

### Bài D — phép ép kiểu thu hẹp

~~~java
System.out.println((byte) 128);
System.out.println((byte) 255);
~~~

**Đáp án:**

~~~text
-128
-1
~~~

Dữ liệu vượt miền byte, các bit thấp được giữ lại theo quy tắc chuyển kiểu.

### Bài E — kiểm tra overflow

Bạn muốn tăng `Integer.MAX_VALUE` thêm 1 nhưng **phải báo lỗi nếu tràn**. Dùng phép cộng thông thường hay `Math.addExact`?

**Đáp án:** dùng `Math.addExact(Integer.MAX_VALUE, 1)` và xử lý `ArithmeticException`; phép `+` thông thường sẽ cho giá trị đã tràn.

### Bài F — giới hạn nghiệp vụ

Một input `int age = -5;` hợp lệ theo miền `int`, nhưng có hợp lệ làm tuổi không?

**Đáp án:** không. `Integer.MIN_VALUE`/`MAX_VALUE` chỉ giới hạn **khả năng biểu diễn của kiểu**; miền nghiệp vụ của tuổi phải được kiểm tra bằng điều kiện riêng.

## 12. Checklist hoàn thành

- [ ] Nhớ `Byte`, `Short`, `Integer`, `Long` có `MIN_VALUE`, `MAX_VALUE` và `SIZE`.
- [ ] Đọc đúng các giới hạn `byte`/ `short`/ `int`/ `long` theo kiểu có dấu.
- [ ] Biết `SIZE` là số bit, `BYTES` là số byte của biểu diễn giá trị.
- [ ] Hiểu `Character.SIZE == 16` và `char` là UTF-16 code unit.
- [ ] Phân biệt `Float.MIN_VALUE`/`Double.MIN_VALUE` với số âm hữu hạn thấp nhất.
- [ ] Biết `MIN_NORMAL` của floating-point khác `MIN_VALUE`.
- [ ] Biết `Boolean` không có `MIN_VALUE`, `MAX_VALUE`, `SIZE`.
- [ ] Kiểm tra miền trước khi cast thu hẹp kiểu và chú ý overflow.
- [ ] Không nhầm giới hạn của kiểu với miền giá trị hợp lệ của nghiệp vụ.
- [ ] Biên dịch và chạy `PrimitiveLimitsDemo.java` bằng Java 17+, đối chiếu output.

**Kết luận:** `MIN_VALUE`, `MAX_VALUE` và `SIZE` giúp tra được giới hạn và độ rộng giá trị **đúng theo đặc tả Java**, tránh viết cứng các con số và hỗ trợ validation. Với số nguyên, `MIN_VALUE` là số âm nhỏ nhất; với `float`/`double`, `MIN_VALUE` là số **dương khác 0 nhỏ nhất**. Luôn phân biệt bit với byte, khả năng biểu diễn với yêu cầu nghiệp vụ, và kiểm tra trước những phép toán có thể tràn số.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="primitive-types-010">Nhận diện vấn đề tràn số và độ chính xác trước khi viết phép tính</a>

<details>
<summary>Xem chi tiết</summary>

# Nhận diện vấn đề tràn số và độ chính xác trước khi viết phép tính — Java 17+

Một phép tính **biên dịch thành công** chưa chắc có **kết quả đúng**. Với kiểu nguyên thủy Java, hai nhóm rủi ro quan trọng là:

- **Tràn số nguyên (*integer overflow*)**: phép toán vượt miền biểu diễn của kiểu `int` hoặc `long` và kết quả có thể quay vòng thành số khác.
- **Sai số số thực (*floating-point rounding/precision*)**: `float` và `double` dùng biểu diễn nhị phân hữu hạn, nên nhiều số thập phân không thể lưu chính xác.

Cách phòng ngừa hiệu quả nhất không phải sửa lỗi sau khi xảy ra, mà là **ước lượng miền đầu vào, miền kết quả trung gian và độ chính xác yêu cầu trước khi chọn kiểu hoặc viết phép tính**.

## 1. Bảng tự kiểm tra trước khi lập trình

| Câu hỏi cần trả lời | Ví dụ rủi ro | Hướng xử lý |
| --- | --- | --- |
| Đầu vào lớn nhất và nhỏ nhất là bao nhiêu? | Cộng vào `Integer.MAX_VALUE` | Kiểm tra giới hạn, dùng `long` hoặc phép toán có kiểm tra |
| **Tích/tổng trung gian** có vượt miền kiểu toán hạng? | `int * int` rồi gán vào `long` | Chuyển sang `long` **trước khi nhân** |
| Phép chia cần lấy phần thập phân? | `5 / 2` cho `2` | Chọn `5 / 2.0` hoặc ép kiểu trước chia |
| Cần giá trị thập phân **chính xác**? | `0.1 + 0.2` không bằng đúng `0.3` ở kiểu `double` | Dùng `BigDecimal` từ chuỗi hoặc đơn vị nguyên phù hợp |
| Có phép chuyển kiểu thu hẹp? | Ép `130` sang `byte` cho `-126` | Kiểm tra miền trước khi cast |
| Phép số thực có thể vượt miền hoặc không xác định? | `Double.MAX_VALUE * 2`, `0.0 / 0.0` | Xử lý `Infinity`, `NaN` khi phù hợp |
| Có số nguyên rất lớn chuyển sang `double`? | `2^53 + 1` không giữ nguyên chính xác | Giữ `long` khi cần đúng từng đơn vị |

Giới hạn hay gặp trên Java 17: `byte` từ −128 đến 127; `short` từ −32.768 đến 32.767; `int` từ −2.147.483.648 đến 2.147.483.647; `long` từ −9.223.372.036.854.775.808 đến 9.223.372.036.854.775.807.

## 2. Chương trình Java 17+ runnable

Lưu tệp **`NumericSafetyDemo.java`**:

~~~java
import java.math.BigDecimal;

public class NumericSafetyDemo {
    public static void main(String[] args) {
        int max = Integer.MAX_VALUE;
        System.out.println("wrapped=" + (max + 1));

        try {
            Math.addExact(max, 1);
        } catch (ArithmeticException ex) {
            System.out.println("addExact=overflow");
        }

        int unit = 80_000;
        int quantity = 35_000;

        long badProduct = (long) (unit * quantity);
        long goodProduct = (long) unit * quantity;
        System.out.println("badProduct=" + badProduct);
        System.out.println("goodProduct=" + goodProduct);

        try {
            Math.multiplyExact(unit, quantity);
        } catch (ArithmeticException ex) {
            System.out.println("multiplyExact=overflow");
        }

        System.out.println("intDivision=" + (5 / 2));
        System.out.println("realDivision=" + (5 / 2.0));

        double floatingSum = 0.1 + 0.2;
        System.out.println("floatingSum=" + floatingSum);
        System.out.println("floatingEqual=" + (floatingSum == 0.3));

        BigDecimal exactSum = new BigDecimal("0.1")
                .add(new BigDecimal("0.2"));
        System.out.println("decimalSum=" + exactSum.toPlainString());
        System.out.println("decimalEqual="
                + (exactSum.compareTo(new BigDecimal("0.3")) == 0));

        long big = 9_007_199_254_740_993L;
        double rounded = big;
        System.out.println("longPrecisionLost=" + ((long) rounded != big));

        System.out.println("floatingOverflow="
                + (Double.MAX_VALUE * 2));
        System.out.println("nan=" + (0.0 / 0.0));
    }
}
~~~

Mở PowerShell (Windows) hoặc Bash (Linux/macOS) ở thư mục chứa tệp. Dùng JDK 17 trở lên:

~~~bash
java -version
javac -version
javac -encoding UTF-8 NumericSafetyDemo.java
java NumericSafetyDemo
~~~

**Expected output:**

~~~text
wrapped=-2147483648
addExact=overflow
badProduct=-1494967296
goodProduct=2800000000
multiplyExact=overflow
intDivision=2
realDivision=2.5
floatingSum=0.30000000000000004
floatingEqual=false
decimalSum=0.3
decimalEqual=true
longPrecisionLost=true
floatingOverflow=Infinity
nan=NaN
~~~

## 3. Vì sao kết quả tràn số nguyên?

`int` dùng 32 bit có dấu. Khi cộng vượt `Integer.MAX_VALUE`, phép toán `int` thông thường **không tự ném ngoại lệ**, mà giữ kết quả theo quy tắc tràn của số nguyên Java:

~~~java
int max = Integer.MAX_VALUE;
int wrapped = max + 1; // -2147483648.
~~~

Nếu tràn là **lỗi nghiệp vụ**, nên phát hiện thay vì nhận giá trị đã quay vòng:

~~~java
int checked = Math.addExact(100, 200); // 300.
int product = Math.multiplyExact(10, 20); // 200.

// Math.addExact(Integer.MAX_VALUE, 1)
// nem ArithmeticException khi ket qua vuot mien int.
~~~

`Math.addExact` và `Math.multiplyExact` có overload cho `int`/`long`, ném `ArithmeticException` khi kết quả không biểu diễn được trong kiểu tương ứng. Chúng không tự làm kết quả lớn hơn; muốn chứa số vượt `long`, cân nhắc `BigInteger`.

### Bẫy: đổi sang `long` sau phép nhân là quá muộn

~~~java
int unit = 80_000;
int quantity = 35_000;

long wrong = (long) (unit * quantity); // -1494967296.
long right = (long) unit * quantity;   // 2800000000.
~~~

Ở dòng `wrong`, `unit * quantity` được tính bằng `int` **trước khi cast**, nên đã tràn. `(long)` chỉ mở rộng **giá trị sai**. Dòng `right` chuyển một toán hạng thành `long` **trước phép nhân**; Java thực hiện phép nhân kiểu `long`.

Tuy nhiên, `long` cũng có giới hạn; nếu tích có thể vượt `Long.MAX_VALUE`, tiếp tục cần kiểm tra hoặc kiểu số nguyên tùy ý `BigInteger`.

## 4. Sai số không giống tràn số

`float` (32 bit) và `double` (64 bit) dùng biểu diễn nhị phân IEEE 754. Nhiều phân số thập phân như `0.1` và `0.2` **không biểu diễn chính xác**. Vì vậy:

~~~java
double sum = 0.1 + 0.2;
System.out.println(sum);        // 0.30000000000000004
System.out.println(sum == 0.3); // false
~~~

`double` thường chính xác hơn `float`, nhưng **không biến phép toán thập phân thành chính xác tuyệt đối**.

| Yêu cầu | Cách chọn |
| --- | --- |
| Tính toán khoa học, đồ họa và thống kê chấp nhận sai số | `double` thông thường; `float` khi định dạng/hiệu năng yêu cầu |
| Tiền tệ với đơn vị nhỏ nhất cố định | Số nguyên biểu diễn cents/đồng, kèm kiểm tra miền |
| Phép tính thập phân cần giữ chữ số chính xác | `BigDecimal`, khởi tạo từ `String` |
| Đếm chính xác vượt miền `long` | `BigInteger` |
| Kiểm tra gần bằng nhau ở miền số thực | Dùng sai số tuyệt đối/tương đối theo đặc tả bài toán |

Để giữ giá trị thập phân `0.1`, viết:

~~~java
BigDecimal amount = new BigDecimal("0.1"); // Chinh xac 0.1.
~~~

Tránh `new BigDecimal(0.1)` nếu mục tiêu là giá trị thập phân chính xác 0,1, vì constructor đó nhận **giá trị `double` đã được làm tròn trong hệ nhị phân**. Khi so sánh giá trị số `BigDecimal`, `compareTo` có thể phù hợp hơn `equals` vì `equals` còn xét **scale** (`1.0` khác `1.00` theo `equals`).

### Số nguyên lớn chuyển sang `double` cũng có thể mất chính xác

`double` có 53 bit độ chính xác cho phần định trị; không thể giữ chính xác **mọi** số nguyên rất lớn:

~~~java
long big = 9_007_199_254_740_993L; // 2^53 + 1.
double rounded = big;
System.out.println((long) rounded != big); // true
~~~

Một phép chuyển kiểu được Java cho phép **không có nghĩa giá trị vẫn chính xác**.

## 5. Các bẫy cần kiểm tra trước khi viết công thức

**Chia số nguyên:** `double result = 5 / 2;` cho `2.0`, không phải `2.5`, vì phép chia đã thực hiện bằng `int`. Viết `5 / 2.0` hoặc `(double) 5 / 2` nếu cần phần thập phân.

**Thu hẹp:** `(byte) 130` cho `-126` do giữ 8 bit thấp rồi diễn giải là số có dấu. Hãy so sánh với miền đích trước khi cast.

**Phép chia cho 0:** `1 / 0` trên kiểu số nguyên ném `ArithmeticException` nếu được thực thi; ngược lại `1.0 / 0.0` cho `Infinity`, `0.0 / 0.0` cho `NaN`. Kiểm tra mẫu số và tính hợp lệ của kết quả theo yêu cầu.

**`NaN` không bằng chính nó:** `Double.NaN == Double.NaN` trả `false`; dùng `Double.isNaN(value)` để phát hiện. Dùng `Double.isFinite(value)` để kiểm tra kết quả hữu hạn nếu bài toán không chấp nhận `NaN` hay vô cực.

**Đừng dùng một epsilon cố định cho mọi bài toán:** so sánh xấp xỉ cần xét thang đo, sai số tuyệt đối/tương đối và rủi ro `NaN`. Với dữ liệu có yêu cầu **chính xác tuyệt đối**, hãy thay đổi biểu diễn thay vì chỉ tăng ngưỡng sai số.

## 6. Checklist thiết kế phép tính

Trước khi viết một công thức, hãy trả lời:

1. **Đầu vào/đầu ra lớn nhất và nhỏ nhất?** Ví dụ số lượng tối đa nhân đơn giá tối đa là bao nhiêu?
2. **Trung gian tính bằng kiểu nào?** Cast trước hay sau toán tử? Có phép cộng/nhân `int` tràn trước khi gán `long`?
3. **Chia lấy nguyên hay thập phân?** Kiểu của từng toán hạng là gì?
4. **Cho phép sai số bao nhiêu?** Nếu là tiền hoặc định danh số nguyên, có cần biểu diễn chính xác?
5. **Có thu hẹp kiểu hoặc chuyển nguyên lớn sang số thực?** Giá trị nào có thể bị mất?
6. **Kết quả bất thường xử lý thế nào?** Ném lỗi bằng `Math.*Exact`, kiểm tra mẫu số, `NaN` và `Infinity`.
7. **Đã thử giá trị biên?** Kiểm tra 0, số âm, giới hạn min/max, tích lớn, phân số nhỏ và dữ liệu ngoài phạm vi.

## 7. Bài tập ngắn

**Bài 1.** Vì sao `long x = 100_000 * 100_000;` cho kết quả không đúng 10.000.000.000? Sửa thế nào?

*Đáp án:* Hai toán hạng là `int` nên tích `int` tràn trước khi gán. Dùng `100_000L * 100_000` hoặc `(long) 100_000 * 100_000`.

**Bài 2.** `Math.addExact(Integer.MAX_VALUE, 1)` trả số âm hay ném lỗi?

*Đáp án:* Ném `ArithmeticException`; phép `+` thông thường mới quay vòng.

**Bài 3.** Tại sao `double x = 7 / 2;` cho `3.0`, còn `double y = 7 / 2.0;` cho `3.5`?

*Đáp án:* Kiểu toán hạng quyết định phép chia nguyên hay số thực **trước khi gán**.

**Bài 4.** Tính chính xác `0.1 + 0.2` theo thập phân. Nên dùng cách nào?

*Đáp án:* `new BigDecimal("0.1").add(new BigDecimal("0.2"))` cho đúng `0.3`.

**Bài 5.** `Double.MAX_VALUE * 2` và `0.0 / 0.0` cho gì?

*Đáp án:* Lần lượt `Infinity` và `NaN`, không giống tràn số nguyên.

**Kết luận:** Trước khi viết phép tính Java, hãy kiểm tra **miền dữ liệu**, **kiểu của mỗi kết quả trung gian** và **độ chính xác mong muốn**. Dùng kiểu rộng **trước phép tính**, kiểm tra tràn bằng `Math.*Exact`, và chọn `BigDecimal`/`BigInteger` khi các giới hạn của primitive không đáp ứng yêu cầu.


</details>

- [Quay lại đầu trang](#back-to-top)
