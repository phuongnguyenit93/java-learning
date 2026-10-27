<a id="back-to-top"></a>

# Nâng kiểu biểu thức

## Menu
- [Unary numeric promotion](#numeric-promotion-001)
- [Binary numeric promotion với int, long, float và double](#numeric-promotion-002)
- [Vì sao byte cộng byte thường cho kết quả int](#numeric-promotion-003)
- [Gán biểu thức hằng trong phạm vi byte, short hoặc char](#numeric-promotion-004)
- [Compound assignment và phép thu hẹp ngầm định](#numeric-promotion-005)
- [Kiểu của phép tăng giảm khác phép cộng rồi gán](#numeric-promotion-006)
- [Toán tử điều kiện ?: và các quy tắc đặc biệt cho số](#numeric-promotion-007)
- [Unboxing khi biểu thức chứa wrapper](#numeric-promotion-008)
- [Overload: nâng kiểu primitive, boxing và giới hạn kết hợp](#numeric-promotion-009)
- [var suy luận kiểu từ initializer, không từ cách dùng về sau](#numeric-promotion-010)
- [Số trong switch và giới hạn theo phiên bản ngôn ngữ](#numeric-promotion-011)

## <a id="numeric-promotion-001">Unary numeric promotion</a>

<details>
<summary>Xem chi tiết</summary>

# Unary numeric promotion trong Java

## 1. Khái niệm và mục tiêu

**Unary numeric promotion** (nâng kiểu số một ngôi) là quy tắc chuyển đổi kiểu số mà Java áp dụng cho **một toán hạng tại một thời điểm** trong những ngữ cảnh được Java Language Specification (JLS) quy định. Mục đích là tạo ra kiểu dữ liệu phù hợp để toán tử hay biểu thức có thể thực hiện phép tính.

Điểm cần nhớ: **byte, short và char được nâng thành int** trong unary numeric promotion. Điều này thường khiến kết quả của **+b**, **-s**, **~c** hoặc **b << 1** có kiểu **int**, dù biến ban đầu chỉ là byte, short hay char. Quy tắc cũng có thể **unbox** các kiểu wrapper như **Byte** hoặc **Long**.

Ví dụ:

~~~java
byte b = 5;
int x = +b;       // Hợp lệ: +b có kiểu int và giá trị 5
int y = -b;       // Hợp lệ: -b có kiểu int và giá trị -5
int z = ~b;       // Hợp lệ: ~b tính trên 32 bit kiểu int
// byte bad = +b; // Lỗi biên dịch: kết quả int không tự thu hẹp về byte
~~~

Trong ngữ cảnh trên, dấu **+** là toán tử dương một ngôi, không phải phép cộng hai số. Mọi ví dụ ở bài này dùng quy tắc của **Java 17+**.

## 2. Quy tắc chuyển kiểu

Bảng dưới đây áp dụng khi một toán hạng thực sự nằm trong **ngữ cảnh unary numeric promotion**:

| Kiểu ban đầu của toán hạng | Chuyển đổi | Kiểu sau promotion |
| --- | --- | --- |
| **byte** | Widening primitive conversion | **int** |
| **short** | Widening primitive conversion | **int** |
| **char** | Widening primitive conversion | **int** |
| **int** | Giữ nguyên | **int** |
| **long** | Giữ nguyên | **long** |
| **float** | Giữ nguyên | **float** |
| **double** | Giữ nguyên | **double** |
| **Byte**, **Short**, **Character**, **Integer** | Unboxing, rồi nâng lên int nếu cần | **int** |
| **Long** | Unboxing | **long** |
| **Float** | Unboxing | **float** |
| **Double** | Unboxing | **double** |

**boolean**, **Boolean** và **String** không phải toán hạng số hợp lệ cho unary numeric promotion. Các toán tử còn có ràng buộc riêng: chẳng hạn **~** chỉ chấp nhận kiểu nguyên sau promotion (**int** hoặc **long**), nên **~1.5f** không hợp lệ dù **float** là một kiểu số.

Không phải mọi giá trị được chuyển thành **int**. **long**, **float** và **double** giữ kiểu của mình nếu không có quy tắc khác từ ngữ cảnh bao quanh. Unary numeric promotion cũng **không tự nâng int lên long vì biến nhận kết quả là long**.

### Thứ tự áp dụng

1. Xét kiểu của biểu thức toán hạng tại thời điểm biên dịch.
2. Nếu đó là wrapper số, thực hiện **unboxing**. Khi giá trị wrapper là **null**, thao tác unbox phát sinh **NullPointerException** lúc chạy.
3. Nếu kết quả có kiểu **byte**, **short** hoặc **char**, nâng lên **int**; nếu đã là **int**, **long**, **float**, **double**, giữ nguyên.
4. Thực hiện toán tử hoặc sử dụng biểu thức theo ngữ nghĩa của ngữ cảnh.
5. Sau đó, nếu kết quả được gán cho một biến, áp dụng **quy tắc phép gán** riêng; không tự động đảo ngược bước promotion.

Sơ đồ:

~~~text
byte / short / char -------------> int
int -----------------------------> int
long ----------------------------> long
float ---------------------------> float
double --------------------------> double

Byte / Short / Character / Integer --unboxing--> int
Long ------------------------------unboxing--> long
Float -----------------------------unboxing--> float
Double ----------------------------unboxing--> double
~~~

**Widening** ở bước này không mất giá trị số nguyên của byte/short/char. Tuy nhiên, nếu một phép tính tiếp theo tràn giới hạn **int** hoặc **long**, đó là vấn đề số học sau promotion, không phải mất dữ liệu ngay trong bước mở rộng.

## 3. Các ngữ cảnh áp dụng

Theo JLS, unary numeric promotion xuất hiện trong các trường hợp sau:

| Ngữ cảnh | Ví dụ | Điều quan trọng |
| --- | --- | --- |
| Toán tử dương một ngôi **+** | **+someByte** | Kết quả có kiểu đã promotion |
| Toán tử âm một ngôi **-** | **-someShort** | Đổi dấu sau khi promotion |
| Toán tử bù bit **~** | **~someByte** | Toán hạng phải là int/long sau promotion |
| Toán tử dịch bit **<<**, **>>**, **>>>** | **someByte << distance** | **Mỗi toán hạng được promotion riêng** |
| Biểu thức kích thước khi tạo mảng | **new int[someByte]** | Kích thước phải có kiểu **int** sau promotion |
| Biểu thức chỉ số mảng | **array[someChar]** | Chỉ số phải có kiểu **int** sau promotion |

Các trường hợp tạo/truy cập mảng có kiểm tra bổ sung: **new int[1L]** và **array[1L]** không hợp lệ vì **long** không được tự chuyển thành **int** chỉ để làm kích thước hoặc chỉ số.

Lưu ý rằng một toán tử trông giống “một ngôi” **không nhất thiết** dùng cùng quy tắc. **++**, **--** là các toán tử có ngữ nghĩa cập nhật biến riêng; không được đánh đồng kiểu kết quả của chúng với toán tử **+**, **-**, **~**. Phần 8 giải thích trường hợp này.

## 4. Toán tử dương một ngôi: +x

**+x** tính toán giá trị sau khi promotion và giữ nguyên dấu, độ lớn của nó. Nó có thể **thay đổi kiểu tĩnh** của biểu thức:

~~~java
byte b = 12;
short s = 300;
char c = 'A';

int a = +b;  // 12
int q = +s;  // 300
int r = +c;  // 65 (mã UTF-16 của 'A')
~~~

**+b** và **b** cùng giá trị nhưng khác kiểu biểu thức: **b** là byte, **+b** là int. Điều này ảnh hưởng đến phép gán, overload của phương thức, suy luận kiểu của **var**, và những phép toán tiếp theo.

~~~java
byte b = 12;
var raw = b;        // raw có kiểu byte
var promoted = +b;  // promoted có kiểu int
~~~

Toán tử cộng hai ngôi **a + b** có quy tắc **binary numeric promotion**, khác với **+a** ở đây. Khi có chuỗi ký tự, **"x" + a** lại có thể là phép nối chuỗi, không phải unary numeric promotion.

## 5. Toán tử âm một ngôi: -x

Với **-x**, Java promotion toán hạng trước rồi lấy giá trị đối. Đây là lý do một byte nhỏ nhất có thể đổi dấu mà không bị tràn ở kiểu byte:

~~~java
byte b = -128;
int opposite = -b;  // 128, kiểu int
~~~

Phép trừ trên đã diễn ra trong int nên kết quả **128** hợp lệ. **char** là kiểu nguyên 16 bit không dấu, giá trị từ **0** đến **65535**, nhưng vẫn nâng thành int có dấu:

~~~java
char c = '\uFFFF';   // giá trị số 65535
int value = +c;      // 65535
int negative = -c;   // -65535
~~~

Ở đây không có “mã char âm”: **-c** là **int** âm sau phép tính. Giá trị char gốc không đổi.

### Tràn khi lấy dấu âm của int/long nhỏ nhất

Độ rộng int hoặc long là hữu hạn. Giá trị đối của **Integer.MIN_VALUE** (−2³¹) không biểu diễn được trong int. Java dùng kết quả tràn theo số học bù hai:

~~~java
int x = Integer.MIN_VALUE;
System.out.println(-x); // -2147483648
~~~

**-x** vẫn là int và vẫn bằng **Integer.MIN_VALUE**; không phát sinh ArithmeticException. **-Long.MIN_VALUE** có hiện tượng tương tự với long. Nếu thật sự cần giá trị dương của số int nhỏ nhất, có thể mở rộng **trước khi** lấy dấu âm:

~~~java
long positive = -(long) Integer.MIN_VALUE; // 2147483648L
~~~

Còn **long wide = -Integer.MIN_VALUE;** đã thực hiện phép đổi dấu trong int rồi mới gán sang long, nên nhận giá trị âm, không giải quyết tràn.

Với **float/double**, unary minus có ngữ nghĩa dấu của số chấm động, bao gồm **+0.0** và **-0.0**. Ví dụ **1.0 / -0.0** là **-Infinity**, khác với chia số nguyên cho 0.

## 6. Toán tử bù bit: ~x

**~** đảo từng bit của toán hạng **sau khi** promotion. Kết quả có kiểu **int** hoặc **long**; **~byte**, **~short** và **~char** đều tạo ra int 32 bit.

Với **byte b = -5**, việc nâng từ byte sang int sẽ kéo dài bit dấu:

~~~text
byte -5 (8 bit):          1111 1011
int -5  (32 bit): 11111111 11111111 11111111 11111011
~int -5:          00000000 00000000 00000000 00000100
Giá trị cuối:      4
~~~

Vì vậy **~b** cho ra **4**, không phải một byte âm hay giá trị có 8 bit được giữ nguyên. Với mọi int, đẳng thức số học **~x == -x - 1** đúng theo ngữ nghĩa int 32 bit.

Nếu muốn tính phép đảo trong **8 bit không dấu**, hãy **mask** kết quả trở lại 8 bit:

~~~java
byte b = 5;
int complement32 = ~b;           // -6
int complement8 = (~b) & 0xFF;  // 250 (0xFA)
~~~

Phép **& 0xFF** ở đây dùng toán tử bitwise AND; nó giới hạn mẫu kết quả còn 8 bit. Không nên coi việc này là chuyển kiểu byte có dấu, vì **250** không thuộc phạm vi **byte** của Java.

~~~java
// Hợp lệ
int result = ~((short) 0x00FF);

// Không hợp lệ: ~ yêu cầu kiểu nguyên sau promotion
// float f = ~1.5f;
~~~

## 7. Dịch bit: mỗi toán hạng được promotion độc lập

Java hỗ trợ ba toán tử dịch bit: **<<** (dịch trái), **>>** (dịch phải giữ bit dấu) và **>>>** (dịch phải chèn 0). Với từng biểu thức **left << distance**, **left >> distance**, **left >>> distance**, Java áp dụng unary numeric promotion **riêng cho left và distance**.

- Nếu toán hạng trái là **byte**, **short**, **char** hoặc **int**, kết quả phép dịch có kiểu **int**.
- Nếu toán hạng trái là **long**, kết quả có kiểu **long**.
- Toán hạng phải chỉ định khoảng dịch và có thể là int/long sau promotion. **long ở bên phải không khiến bên trái thành long**.
- Hai toán hạng, sau promotion, bắt buộc là kiểu nguyên; **float** hoặc **double** không hợp lệ để dịch bit.
- Với bên trái kiểu **int**, chỉ 5 bit thấp của khoảng dịch được xét: khoảng thực tế từ 0 đến 31 (**distance & 31**).
- Với bên trái kiểu **long**, chỉ 6 bit thấp của khoảng dịch được xét: khoảng thực tế từ 0 đến 63 (**distance & 63**).

Ví dụ:

~~~java
byte b = 1;
int a = b << 1;         // 2, kiểu int
int c = b << 33L;       // 2, do 33 & 31 == 1
long d = 1L << 33L;     // 8589934592L, kiểu long
long e = b << 33L;      // giá trị 2, int được mở rộng sang long SAU phép dịch
~~~

Đừng suy luận kiểu phép dịch từ biến **long e** ở vế trái dấu bằng: bản thân **b << 33L** vẫn là **int**.

### Dịch phải một byte âm: bẫy sign extension

~~~java
byte b = -5;
int x = b >>> 1;           // 2147483645
int y = (b & 0xFF) >>> 1;  // 125
~~~

Trong phép đầu tiên, **b** được mở rộng dấu từ **0xFB** thành int **0xFFFFFFFB** trước khi dịch phải logic 1 bit, được **0x7FFFFFFD** = **2147483645**. Trong phép thứ hai, **b & 0xFF** tạo ra int **251** (0x000000FB), dịch phải được **125**. Mục tiêu xử lý giá trị byte không dấu cần được nêu rõ và mask trước khi áp dụng phép dịch.

**Lưu ý**: phép **&** trong ví dụ là toán tử hai ngôi có cơ chế numeric promotion riêng; nó tạo ra giá trị int để thao tác tiếp. Còn toán tử dịch áp dụng unary numeric promotion cho từng toán hạng.

## 8. Kích thước và chỉ số mảng

Kích thước trong **new T[size]** và chỉ số trong **array[index]** là các ngữ cảnh sử dụng unary numeric promotion. Vì vậy byte, short, char và các wrapper tương ứng có thể được dùng làm chỉ số hoặc kích thước sau khi chuyển sang int:

~~~java
byte size = 3;
char index = 1;
int[] data = new int[size]; // size được nâng thành int
data[index] = 42;           // index được nâng thành int
~~~

Không nên hiểu là Java cho phép mọi số thực hoặc số nguyên không giới hạn ở đây:

~~~java
// Không biên dịch:
// int[] data = new int[3L];
// int value = data[1L];
// int[] other = new int[2.0];
// int element = data[1.5f];
~~~

Chỉ số phải là **int** sau promotion. Với giá trị nhỏ hơn 0 hoặc vượt độ dài mảng, mã vẫn có thể biên dịch nhưng sẽ thất bại ở lúc chạy (**NegativeArraySizeException** khi tạo mảng với kích thước âm; **ArrayIndexOutOfBoundsException** khi truy cập sai chỉ số).

## 9. Unary promotion khác binary promotion, phép gán và ++/--

Ba cơ chế này liên quan nhau nhưng không thay thế được nhau.

### Unary và binary numeric promotion

~~~java
byte a = 10;
short b = 20;

int first = +a;    // unary promotion: byte -> int
int second = a + b; // binary promotion: byte + short -> int
~~~

Với binary numeric promotion, Java xét hai toán hạng để xác định kiểu phép tính chung; có thể có **double**, **float** hoặc **long** tùy toán hạng. Với unary numeric promotion, Java chuyển **một toán hạng tại một thời điểm**; không có việc tìm kiểu chung giữa hai phía của phép dịch bit.

### Phép gán không tự thu hẹp kết quả int

~~~java
byte b = 10;
int ok = +b;         // Hợp lệ

// Không biên dịch:
// byte invalid1 = +b;
// byte invalid2 = -b;
// byte invalid3 = b << 1;

byte explicit = (byte) (+b); // Có ép kiểu tường minh
~~~

Việc ép kết quả int về byte có thể làm mất các bit cao hoặc khiến số dương thành âm khi vượt giới hạn −128..127. Chỉ cast khi quy tắc miền giá trị đã được xác nhận.

**Ngoại lệ liên quan hằng số:** một biểu thức **constant expression** có kiểu int và giá trị nằm trong phạm vi byte/short/char có thể được gán nhờ **constant narrowing**:

~~~java
byte a = +12;             // Hợp lệ: hằng int 12 nằm trong phạm vi byte
final byte b = 12;
byte c = +b;              // Hợp lệ: +b là constant expression có giá trị 12
byte d = 12;
// byte e = +d;           // Lỗi: d không phải biến hằng final
// byte f = +128;         // Lỗi: hằng 128 vượt phạm vi byte
~~~

Không rút ra kết luận “unary plus giữ kiểu byte” từ ví dụ **byte a = +12**: biểu thức **+12** vẫn có kiểu int, chỉ được phép thu hẹp trong ngữ cảnh gán hằng.

### ++, -- và phép gán kết hợp

Toán tử **++**, **--** làm thay đổi **biến** và có kiểu kết quả bằng kiểu biến đó. Chúng sử dụng quy tắc cập nhật và chuyển kết quả về kiểu của biến, khác với **+b** hoặc **-b** chỉ tạo ra một giá trị mới.

~~~java
byte b = 127;
b++;                     // Hợp lệ; b thành -128 do thu hẹp và tràn
byte after = b;          // Hợp lệ
b += 1;                  // Hợp lệ; phép gán kết hợp tự chuyển kết quả về byte
// b = b + 1;            // Lỗi: b + 1 có kiểu int
~~~

**b++** không phải là “unary numeric promotion khiến byte vẫn là byte”; toán tử cập nhật có đặc tả riêng, phép cộng trung gian và bước thu hẹp khi lưu lại biến. Cần phân biệt **giá trị cũ** của hậu tố **b++** với **giá trị mới** của tiền tố **++b**.

## 10. Wrapper và NullPointerException

Unary numeric promotion có thể thực hiện **unboxing**:

~~~java
Byte b = (byte) 12;
Short s = (short) 3;
Character c = 'A';
Integer i = 20;
Long l = 40L;
Float f = 2.5f;
Double d = 3.5;

int a = +b;  // 12: Byte -> byte -> int
int n = -s;  // -3: Short -> short -> int
int m = +c;  // 65: Character -> char -> int
int p = +i;  // 20: Integer -> int
long q = +l; // 40L: Long -> long
float r = -f;   // -2.5f
double t = -d;  // -3.5
~~~

Nếu wrapper là null, unboxing xảy ra trước khi toán tử có thể tính giá trị:

~~~java
Byte missing = null;
// int x = +missing; // Biên dịch được, khi chạy ném NullPointerException
~~~

Cần làm rõ sự khác biệt giữa **lỗi biên dịch do kiểu không phù hợp** và **lỗi runtime khi unbox null**. Nếu dữ liệu cho phép null, hãy kiểm tra điều kiện hoặc dùng giá trị mặc định phù hợp nghiệp vụ trước phép toán. Đừng mặc nhiên giả sử có giá trị 0.

## 11. Ví dụ tổng hợp Java 17+ có kết quả kiểm chứng

Lưu nguyên văn mã dưới đây thành **UnaryPromotionDemo.java**:

~~~java
public class UnaryPromotionDemo {
    static String type(int value) { return "int"; }
    static String type(long value) { return "long"; }
    static String type(float value) { return "float"; }
    static String type(double value) { return "double"; }

    public static void main(String[] args) {
        byte b = -5;
        short s = 300;
        char c = 'A';
        long l = 8L;
        float f = 1.5f;
        double d = 2.25;

        System.out.println("+byte: " + (+b) + " / " + type(+b));
        System.out.println("-short: " + (-s) + " / " + type(-s));
        System.out.println("-char: " + (-c) + " / " + type(-c));
        System.out.println("~byte: " + (~b) + " / " + type(~b));
        System.out.println("+long: " + (+l) + " / " + type(+l));
        System.out.println("-float: " + (-f) + " / " + type(-f));
        System.out.println("+double: " + (+d) + " / " + type(+d));

        int shifted = b >>> 1;
        int masked = (b & 0xFF) >>> 1;
        int left = ((byte) 1) << 33L;
        long wideLeft = 1L << 33L;
        System.out.println("negative byte >>> 1: " + shifted);
        System.out.println("(byte & 0xFF) >>> 1: " + masked);
        System.out.println("byte << 33L: " + left + " / " + type(left));
        System.out.println("1L << 33L: " + wideLeft + " / " + type(wideLeft));

        byte length = 2;
        char index = 1;
        int[] numbers = new int[length];
        numbers[index] = 99;
        System.out.println("array: length=" + numbers.length + ", a[1]=" + numbers[1]);

        Byte boxed = (byte) 12;
        Long boxedLong = 13L;
        System.out.println("+Byte: " + (+boxed) + " / " + type(+boxed));
        System.out.println("-Long: " + (-boxedLong) + " / " + type(-boxedLong));

        byte edge = 127;
        edge++;
        System.out.println("byte after ++: " + edge);
        System.out.println("-Integer.MIN_VALUE: " + (-Integer.MIN_VALUE));
        System.out.println("1.0 / (-0.0): " + (1.0 / -0.0));
    }
}
~~~

Biên dịch với JDK 17 trở lên:

~~~bash
javac UnaryPromotionDemo.java
java UnaryPromotionDemo
~~~

Kết quả stdout:

~~~text
+byte: -5 / int
-short: -300 / int
-char: -65 / int
~byte: 4 / int
+long: 8 / long
-float: -1.5 / float
+double: 2.25 / double
negative byte >>> 1: 2147483645
(byte & 0xFF) >>> 1: 125
byte << 33L: 2 / int
1L << 33L: 8589934592 / long
array: length=2, a[1]=99
+Byte: 12 / int
-Long: -13 / long
byte after ++: -128
-Integer.MIN_VALUE: -2147483648
1.0 / (-0.0): -Infinity
~~~

Trong ví dụ, các phương thức **type(int)**, **type(long)**, **type(float)**, **type(double)** được overload để cho thấy **kiểu tĩnh** mà trình biên dịch chọn cho kết quả; đây không phải cách kiểm tra kiểu cần dùng trong ứng dụng bình thường. Chuỗi in có dấu **+byte** chỉ là nhãn, còn phép promotion thực sự nằm trong **(+b)**.

Các điểm nên tự giải thích sau khi chạy:

- **-char** cho **-65 / int**, vì char 'A' được nâng thành int 65 trước khi lấy dấu âm.
- **~byte** cho **4 / int**, vì byte -5 được mở rộng dấu lên int trước khi đảo 32 bit.
- **byte << 33L** cho **2 / int**; kiểu long của khoảng dịch không làm bên trái thành long.
- **byte after ++** cho **-128**, vì phép tăng lưu kết quả thu hẹp vào biến byte.
- **-Integer.MIN_VALUE** vẫn là số âm nhỏ nhất của int; phép tính không tự mở rộng thành long.

## 12. Các lỗi phổ biến và cách khắc phục

| Lỗi thường gặp | Nguyên nhân | Hướng xử lý |
| --- | --- | --- |
| Gán **+byte**, **-short** hoặc **~char** vào biến cùng kiểu nhỏ | Kết quả expression đã được nâng thành **int** | Dùng biến int hoặc cast có kiểm tra phạm vi |
| Nghĩ **+x** luôn giữ kiểu của **x** | Unary plus thực hiện numeric promotion | Xét kiểu toán hạng trước khi đoán kiểu biểu thức |
| Thấy **byte b = +12** chạy được rồi kết luận **+b** trả byte | Hằng int được thu hẹp nhờ constant narrowing | Phân biệt kiểu biểu thức và phép chuyển đổi khi gán |
| Nghĩ **byte << long** có kết quả long | Hai toán hạng dịch bit được promotion riêng | Xét kiểu toán hạng **bên trái** |
| Dịch phải **>>>** byte âm và gặp số dương rất lớn | Sign extension trước phép dịch 32 bit | Với byte không dấu, cân nhắc **b & 0xFF** |
| Dùng **~byte** và chỉ mong 8 bit | Toán tử bù áp dụng trên **int 32 bit** | Mask **& 0xFF** khi cần mẫu bit 8 bit |
| Truyền long vào kích thước hoặc chỉ số mảng | Quy tắc yêu cầu int sau promotion | Chuyển đổi có kiểm tra giới hạn trước khi truy cập |
| Dùng wrapper null với unary + hoặc - | Unboxing ném **NullPointerException** | Xử lý null trước thao tác số |
| Nghĩ **++byte** luôn trả int như **+byte** | **++** có quy tắc cập nhật biến riêng | Phân biệt phép toán đọc giá trị và phép toán cập nhật |
| Nghĩ **-Integer.MIN_VALUE** là số dương | Tràn trong phạm vi int | Mở rộng sang long **trước** khi đổi dấu |

Khi đọc một biểu thức số, hãy theo thứ tự: **kiểu gốc của từng toán hạng → có unboxing hay promotion không → kiểu phép toán → giá trị phép toán → chuyển đổi trong phép gán**. Cách phân tích này đặc biệt có ích khi gặp lỗi “possible lossy conversion from int to byte” hoặc kết quả dịch bit khác kỳ vọng.

## 13. Bài tập tự kiểm tra (kèm đáp án)

**Bài 1.** Cho **byte b = 100;**. Các biểu thức **+b**, **-b**, **~b** lần lượt có kiểu gì? Giá trị là gì?

Đáp án: đều kiểu **int**; giá trị lần lượt **100**, **-100**, **-101**. Với int, **~100 == -101**.

**Bài 2.** **byte a = 5; byte x = +a;** có biên dịch được không? **byte x = +5;** thì sao?

Đáp án: câu đầu **không**, vì **+a** là int không phải constant expression; câu thứ hai **có**, vì **+5** là hằng int nằm trong miền byte và được thu hẹp khi gán.

**Bài 3.** **short s = 1; long result = s << 32L;** cho giá trị nào?

Đáp án: **1**. Kiểu trái sau promotion là int, nên khoảng dịch thực tế **32 & 31 = 0**; biểu thức trả int 1 rồi mới mở rộng sang long để gán.

**Bài 4.** **char c = '\uFFFF'; int x = -c;** cho giá trị nào?

Đáp án: **-65535**. Char được chuyển thành int 65535 rồi đổi dấu; không có bước diễn giải char như short có dấu.

**Bài 5.** **Byte x = null; int y = +x;** thất bại ở biên dịch hay lúc chạy?

Đáp án: biên dịch **hợp lệ**, lúc chạy ném **NullPointerException** khi unboxing x.

**Bài 6.** Vì sao **byte b = 127; b++;** hợp lệ, còn **b = b + 1;** không?

Đáp án: **++** cập nhật và chuyển kết quả về kiểu byte theo quy tắc riêng; **b + 1** có kiểu int do binary numeric promotion nên phép gán thường vào byte bị từ chối.

## 14. Tài liệu gốc

- [Java Language Specification, Java SE 17 — §5.6 Numeric Contexts](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.6): định nghĩa unary numeric promotion, unboxing, widening và các ngữ cảnh áp dụng.
- [JLS Java SE 17 — §15.15 Unary Operators](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.15): ngữ nghĩa unary +, -, ~ và sự khác biệt của ++/--.
- [JLS Java SE 17 — §15.19 Shift Operators](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.19): kiểu kết quả, promotion riêng của hai toán hạng và giới hạn khoảng dịch.
- [JLS Java SE 17 — §5.2 Assignment Contexts](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.2): trường hợp gán hằng số int sang byte/short/char.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-promotion-002">Binary numeric promotion với int, long, float và double</a>

<details>
<summary>Xem chi tiết</summary>

# Binary numeric promotion với int, long, float và double trong Java

## 1. Binary numeric promotion là gì?

**Binary numeric promotion** (nâng kiểu số nhị phân) là bộ quy tắc Java dùng để xác định **kiểu chung cho hai toán hạng số** của nhiều toán tử nhị phân. Khi hai toán hạng có kiểu nguyên thủy khác nhau, Java chuyển chúng sang kiểu thích hợp trước khi thực hiện phép toán. Kết quả của phép toán số học có kiểu do quy tắc này quyết định, **không được lựa chọn ngược từ kiểu của biến đang nhận giá trị**.

Ví dụ:

```java
int count = 50_000;
int price = 50_000;

long wrong = count * price;          // Phép nhân là int, tràn trước khi gán long.
long correct = (long) count * price; // Phép nhân là long, kết quả 2500000000.
```

Một phép toán số học giữa hai `int` vẫn là `int`, ngay cả khi kết quả được gán vào `long` hoặc `double`. Chỉ khi **ít nhất một toán hạng** được nâng thành `long`, `float` hoặc `double` *trước phép toán*, loại phép toán mới thay đổi theo quy tắc tương ứng.

Bài này tập trung vào sự kết hợp giữa bốn kiểu: `int`, `long`, `float` và `double`. Các kiểu số nguyên nhỏ hơn cũng được nhắc đến để làm rõ vì sao điểm xuất phát mặc định là `int`.

## 2. Quy tắc chọn kiểu theo thứ tự ưu tiên

Theo Java Language Specification (JLS §5.6), với hai toán hạng số trong ngữ cảnh áp dụng binary numeric promotion, trước tiên các đối tượng wrapper phù hợp được **unbox** nếu cần. Sau đó Java chọn kiểu chung theo thứ tự sau:

1. Nếu **một trong hai toán hạng là `double`**, toán hạng còn lại được chuyển sang `double`.
2. Nếu không có `double` nhưng có **`float`**, toán hạng còn lại được chuyển sang `float`.
3. Nếu không có hai kiểu số thực trên nhưng có **`long`**, toán hạng còn lại được chuyển sang `long`.
4. Nếu không có những kiểu trên, cả hai toán hạng được chuyển thành **`int`** khi cần. Điều này áp dụng cho `byte`, `short`, `char` và `int`.

| Hai kiểu toán hạng | Kiểu chung cho phép toán số học |
| --- | --- |
| `int` + `int` | `int` |
| `int` + `long` | `long` |
| `long` + `float` | `float` |
| `int` + `float` | `float` |
| `float` + `double` | `double` |
| `long` + `double` | `double` |
| `byte` + `short` | `int` |
| `char` + `int` | `int` |

Thứ tự cần nhớ là **`double > float > long > int`** để chọn kiểu chung, nhưng **“kiểu đứng cao hơn” không luôn đồng nghĩa “giữ nguyên tất cả chữ số”**. Đặc biệt, `long → float` có thể làm tròn, và `long → double` có thể làm tròn các số nguyên lớn.

Ở bảng trên, dấu `+` minh họa phép **cộng số**. Nếu một toán hạng là `String`, dấu `+` có thể thực hiện *nối chuỗi* theo những quy tắc khác. `boolean` không phải kiểu số và không thuộc binary numeric promotion.

## 3. Các phép toán nào áp dụng quy tắc này?

Binary numeric promotion tham gia khi Java thực hiện nhiều toán tử số nhị phân:

- Các toán tử số học `+`, `-`, `*`, `/`, `%` khi hai toán hạng là số.
- Các toán tử so sánh thứ tự `<`, `<=`, `>`, `>=` trên các toán hạng số. Hai toán hạng được nâng kiểu để so sánh, nhưng **kết quả phép so sánh là `boolean`**.
- `==` và `!=` khi so sánh hai toán hạng thuộc kiểu số nguyên thủy (hoặc được unbox theo quy tắc áp dụng). Kết quả là `boolean`.
- Các toán tử bit `&`, `|`, `^` ở chế độ **số nguyên**, với kiểu chung là `int` hoặc `long`.

Điều quan trọng là **không áp dụng nhầm quy tắc này cho mọi toán tử**. Các phép dịch `<<`, `>>`, `>>>` sử dụng *unary numeric promotion* riêng trên mỗi toán hạng; kiểu kết quả phụ thuộc vào toán hạng **bên trái** sau khi nâng, không bị toán hạng bên phải kéo lên `long`. Toán tử điều kiện `?:` và toán tử gán ghép cũng có quy tắc định kiểu riêng, dù bên trong có thể cần nâng kiểu số.

Ví dụ:

```java
int a = 7;
long b = 2L;

long sum = a + b;       // long + long => long
long quotient = a / b;  // long / long => long; kết quả 3L.
boolean larger = a > b; // Hai toán hạng so sánh theo long; kết quả boolean.
```

Ngay cả phép chia với kiểu chung `long` vẫn là **chia nguyên** và làm tròn về 0. Để có kết quả `3.5`, phải dùng một toán hạng `float` hoặc `double`.

## 4. Kết hợp `int` và `long`: phép toán ở 64 bit

Nếu một toán hạng là `long` và không có `float`/`double`, Java chuyển toán hạng còn lại sang `long` trước khi thực hiện phép toán:

```java
int n = 2_000_000_000;
long m = 2L;

long product = n * m;  // 4000000000; phép toán int * long => long.
long also = n * 2L;    // 4000000000; hậu tố L tạo long literal.
```

Đối chiếu trường hợp không có toán hạng `long`:

```java
int n = 2_000_000_000;

long tooLate = n * 2;        // -294967296; phép nhân int tràn.
long early = ((long) n) * 2; // 4000000000; phép nhân long.
```

**Cách nghĩ đúng:** phép gán `long tooLate = ...` gồm hai bước: tính **toàn bộ biểu thức vế phải** theo kiểu của nó, rồi chuyển kết quả sang `long`. Nếu phép nhân `int` đã tràn, bước gán không khôi phục các bit đã mất.

Dấu ngoặc có thể làm thay đổi thời điểm nâng kiểu:

```java
int a = 50_000, b = 50_000;
long c = 2L;

long tooLate = (a * b) * c;      // -3589934592
long early = ((long) a * b) * c; // 5000000000
```

Trong `(a * b) * c`, biểu thức con `a * b` đã tràn `int`; việc gặp `c` kiểu `long` sau đó không sửa kết quả cũ. Quy tắc nâng kiểu được áp dụng **tại từng phép toán** của cây biểu thức.

## 5. Kết hợp số nguyên với `float`: cần đề phòng làm tròn

Khi một toán hạng là `float` và không có `double`, Java tính phép toán ở kiểu `float`:

```java
int a = 4;
float b = 1.5f;
float sum = a + b;     // 5.5f

long big = 10L;
float product = big * b; // 15.0f, vì long được chuyển thành float.
```

`float` sử dụng dấu phẩy động nhị phân với **24 bit độ chính xác của phần trị**. Điều này không đủ giữ chính xác mọi giá trị `int` hoặc `long`. Tất cả số nguyên trong khoảng từ `-16_777_216` đến `16_777_216` đều biểu diễn chính xác dưới `float`, nhưng những số nguyên lớn hơn có thể bị làm tròn.

```java
int exactInt = 16_777_217;
float rounded = exactInt + 0.0f;

System.out.println(rounded == 16_777_216f); // true
System.out.println((int) rounded == exactInt); // false
```

Giải thích từng bước:

1. Toán hạng `exactInt` là `int`, toán hạng `0.0f` là `float`.
2. Binary numeric promotion chuyển `exactInt` sang `float`.
3. Giá trị `16_777_217` không biểu diễn chính xác bằng `float`, nên được làm tròn thành `16_777_216.0f` (làm tròn về giá trị gần nhất theo quy tắc IEEE 754).
4. Sau đó mới cộng `0.0f`, cho kết quả đã làm tròn.

Vì thế, **chuyển sang kiểu có miền số lớn hơn không đảm bảo tính chính xác**. `float` có khả năng biểu diễn độ lớn lớn hơn `long`, nhưng lại có độ chính xác số nguyên kém hơn nhiều.

Một ví dụ dễ gặp là `long + float`:

```java
long id = 16_777_217L;
float value = id + 0.0f;

System.out.println((long) value == id); // false
```

Với các mã định danh, bộ đếm hoặc tiền tệ nguyên chính xác, không nên dùng `float` để “tránh tràn” chỉ vì nó đứng trước `long` trong thứ tự nâng kiểu.

## 6. Kết hợp với `double`: ưu tiên cao nhất nhưng không phải chính xác vô hạn

Nếu một trong hai toán hạng là `double`, phép toán số học dùng `double`:

```java
float left = 1.25f;
double right = 2.5;
double sum = left + right; // 3.75.

long count = 7L;
double factor = 0.5;
double half = count * factor; // 3.5.
```

`double` có **53 bit độ chính xác của phần trị**, nên mọi giá trị `int` và mọi số nguyên có trị tuyệt đối không quá `2^53` đều biểu diễn chính xác. Nhưng `long` có thể lớn hơn `2^53`. Một phép tính `long + double` có thể mất độ chính xác khi chuyển `long` thành `double`:

```java
long exact = 9_007_199_254_740_993L; // 2^53 + 1.
double rounded = exact + 0.0;

System.out.println((long) rounded == exact); // false
```

Giá trị `9_007_199_254_740_993` không thể được biểu diễn chính xác bằng `double`. Nó được làm tròn thành `9_007_199_254_740_992.0`. Chuyển ngược `double` sang `long` không phục hồi đơn vị đã mất.

Cũng cần phân biệt **độ lớn biểu diễn**, **độ chính xác** và **quy tắc phép tính**. Ví dụ `0.1 + 0.2` dùng `double` nhưng không biểu diễn chính xác phép cộng thập phân toán học `0.3` do các số thập phân này không biểu diễn hữu hạn trong nhị phân. Đó là giới hạn của dấu phẩy động, không phải lỗi của binary numeric promotion.

## 7. Kiểu biểu thức được quyết định trước khi gán cho biến

Ba ví dụ cùng một quy tắc:

### 7.1. Gán vào `long` sau phép nhân `int`

```java
int price = 50_000;
int quantity = 50_000;

long wrong = price * quantity;          // -1794967296
long correct = (long) price * quantity; // 2500000000
```

### 7.2. Gán vào `double` sau phép chia `int`

```java
int numerator = 1;
int denominator = 2;

double wrong = numerator / denominator;         // 0.0
double correct = (double) numerator / denominator; // 0.5
double alsoCorrect = numerator / 2.0;           // 0.5
```

Vì `int / int` là phép chia nguyên, `numerator / denominator` cho `0`. Gán số `0` đó vào `double` cho `0.0`, không phải `0.5`. Ngược lại, `(double) numerator / denominator` dùng `double` ngay từ phép chia.

### 7.3. Ép kiểu sau biểu thức khác ép kiểu trước biểu thức

```java
int a = 50_000, b = 50_000;

long castAfter = (long) (a * b); // -1794967296
long castBefore = (long) a * b;  // 2500000000
```

Việc đặt dấu ngoặc khiến phép nhân thực hiện trước khi cast. Muốn mở rộng miền tính toán, hãy cast **một toán hạng trước khi thực hiện phép toán nguy hiểm**, không chỉ cast toàn bộ biểu thức sau cùng.

## 8. `byte`, `short` và `char` đều được nâng lên `int`

Binary numeric promotion không có nhánh chọn kiểu chung `byte`, `short` hay `char`. Nếu không có `long`/`float`/`double`, các kiểu nguyên nhỏ đó đều được nâng lên `int`:

```java
byte b = 10;
short s = 20;
char c = 'A';

int sum = b + s;        // 30; kiểu biểu thức là int.
int codePointValue = c + 1; // 66; kiểu biểu thức là int.

// byte wrong = b + s; // Không biên dịch: int không tự thu hẹp thành byte.
```

Ngoại lệ gán một số **biểu thức hằng** kiểu `int` có giá trị nằm trong miền đích (ví dụ `byte k = 10 + 20;`) là quy tắc *assignment conversion* dành cho hằng. Nó **không thay đổi kiểu của biểu thức cộng**: `10 + 20` vẫn là `int`.

Các wrapper như `Integer`/`Long`/`Float`/`Double` có thể tham gia bằng **unboxing**:

```java
Integer boxedInt = 2;
Long boxedLong = 3L;
long sum = boxedInt + boxedLong; // 5L.
```

Tuy nhiên, nếu một wrapper đang là `null` và phải unbox để tính, biểu thức có thể ném `NullPointerException`. Binary numeric promotion không làm `null` trở thành số `0`.

## 9. Hai bẫy tinh tế: so sánh hỗn hợp và phép gán ghép

### 9.1. So sánh `int` với `float` có thể mất tính phân biệt

```java
int n = 16_777_217;
float f = 16_777_216f;

System.out.println(n == f); // true
```

Hai giá trị số nguyên dự định khác nhau, nhưng toán tử `==` áp dụng binary numeric promotion cho cặp `int`/`float`. `n` được làm tròn xuống `16_777_216f` trước khi so sánh, nên kết quả là `true`.

Khi cần so sánh **chính xác các số nguyên**, nên giữ các toán hạng trong kiểu nguyên đủ rộng (`long` nếu đủ miền, hoặc `BigInteger` khi vượt 64 bit). Không sử dụng so sánh hỗn hợp với `float`/`double` một cách vô thức.

### 9.2. Toán tử gán ghép có chuyển kiểu hẹp ngầm

Với phép `E1 += E2`, Java tính phép cộng rồi chuyển kết quả về kiểu của `E1`. Về mặt giá trị, điều này gần với `E1 = (T) (E1 + E2)`, với `T` là kiểu của `E1`, nhưng toán hạng `E1` chỉ được đánh giá một lần trong phép gán ghép.

```java
byte count = 120;
count += 10; // Hợp lệ, phép cộng như int, rồi chuyển hẹp về byte.
System.out.println(count); // -126

int whole = 2;
whole += 1.75; // Hợp lệ, phép cộng như double, rồi chuyển về int.
System.out.println(whole); // 3
```

Các cách viết `count = count + 10` và `whole = whole + 1.75` không biên dịch nếu không cast phù hợp. Nhưng `+=` có cơ chế chuyển kiểu ngầm, có thể dẫn đến tràn hay cắt phần thập phân. Vì thế **gán ghép không phải bằng chứng rằng phép toán ban đầu có kiểu hẹp**.

## 10. Phân biệt với unary numeric promotion và phép dịch bit

Ví dụ dưới đây dễ khiến người học áp dụng sai bảng *binary* cho phép dịch bit:

```java
int bits = 1;
long count = 40L;

int shifted = bits << count; // Kiểu kết quả là int.
```

Dù toán hạng bên phải `count` có kiểu `long`, phép dịch **không đổi toán hạng bên trái `int` thành `long`**. Mỗi toán hạng được *unary numeric promotion* riêng; kết quả có kiểu của toán hạng trái sau nâng kiểu. Với phép dịch `int`, Java chỉ sử dụng 5 bit thấp của khoảng dịch nên `1 << 40` tương đương `1 << 8`, cho `256`.

Nếu thật sự muốn dịch trên 64 bit:

```java
long shifted64 = 1L << 40; // 1099511627776.
```

Đây là khác biệt quan trọng giữa **binary numeric promotion** cho phép toán số học và quy tắc riêng của các toán tử dịch bit. Không suy đoán kiểu kết quả chỉ dựa vào việc toán hạng nào “rộng nhất” nếu toán tử không áp dụng binary numeric promotion.

## 11. Ví dụ Java 17+ hoàn chỉnh

Tạo file **`BinaryNumericPromotionDemo.java`**:

```java
public class BinaryNumericPromotionDemo {
    public static void main(String[] args) {
        int a = 50_000;
        int b = 50_000;

        long late = a * b;
        long early = (long) a * b;
        System.out.println("int * int then long = " + late);
        System.out.println("long * int = " + early);

        int halfAsInt = 1 / 2;
        double halfLate = 1 / 2;
        double halfCorrect = 1 / 2.0;
        System.out.println("int division = " + halfAsInt);
        System.out.println("int division then double = " + halfLate);
        System.out.println("double division = " + halfCorrect);

        int number = 16_777_217;
        float roundedFloat = number + 0.0f;
        System.out.println("int to float exact = "
                + ((int) roundedFloat == number));

        long big = 9_007_199_254_740_993L;
        double roundedDouble = big + 0.0;
        System.out.println("long to double exact = "
                + ((long) roundedDouble == big));

        float f = 1.25f;
        double d = 2.5;
        System.out.println("float + double = " + (f + d));

        byte small = 120;
        small += 10;
        System.out.println("byte += 10 = " + small);

        int n = 16_777_217;
        float compare = 16_777_216f;
        System.out.println("int == float = " + (n == compare));

        long mixed = 2 + 3L;
        System.out.println("int + long = " + mixed);
    }
}
```

Biên dịch và chạy từ thư mục chứa file bằng JDK 17 trở lên:

```bash
javac BinaryNumericPromotionDemo.java
java BinaryNumericPromotionDemo
```

Kết quả chuẩn:

```text
int * int then long = -1794967296
long * int = 2500000000
int division = 0
int division then double = 0.0
double division = 0.5
int to float exact = false
long to double exact = false
float + double = 3.75
byte += 10 = -126
int == float = true
int + long = 5
```

Ở ví dụ này, `int * int` tràn dù biến nhận là `long`; `int / int` làm mất phần phân số dù biến nhận là `double`; các toán hạng `float`/`double` có thể buộc số nguyên làm tròn **trước phép toán hoặc phép so sánh**. Mỗi trường hợp cho thấy kiểu của biểu thức phải được xác định tại **toán tử**, không suy ra từ nơi lưu kết quả.

## 12. Lỗi thường gặp và cách tránh

| Nhầm lẫn | Sự thật | Cách xử lý |
| --- | --- | --- |
| “Vế trái là `long` nên `int * int` thành phép nhân 64 bit.” | Phép nhân `int` đã xảy ra trước phép gán. | Viết `(long) a * b` hoặc dùng toán hạng `long`. |
| “Vế trái là `double` nên `1 / 2` ra `0.5`.” | Phép chia là `int / int`, tạo `0`. | Viết `1.0 / 2`. |
| “`float` rộng hơn `long` nên giữ chính xác mọi `long`.” | `float` có 24 bit độ chính xác, nhiều số nguyên bị làm tròn. | Duy trì kiểu nguyên khi yêu cầu độ chính xác tuyệt đối. |
| “`double` luôn giữ được mọi `long`.” | `double` có 53 bit độ chính xác, không giữ mọi giá trị 64 bit. | Dùng `long`/`BigInteger` khi cần tính nguyên chính xác. |
| “Cộng hai `byte` cho `byte`.” | Phép cộng số học nâng lên `int`. | Nhận kết quả bằng `int`, kiểm tra miền trước khi thu hẹp. |
| “Phép `+= `không mất dữ liệu do không cần cast.” | Gán ghép có chuyển kiểu về toán hạng trái. | Xét tràn và cắt phần phân số rõ ràng. |
| “Mọi toán tử hai toán hạng đều dùng bảng ưu tiên như nhau.” | Dịch bit, nối chuỗi, `?:` có quy tắc riêng. | Xác định toán tử rồi tra đúng ngữ cảnh định kiểu. |
| “So sánh số nguyên với `float` luôn chính xác.” | Ép kiểu trong so sánh có thể làm tròn trước khi đánh giá `==`. | So sánh trong kiểu nguyên đủ rộng. |

**Ghi nhớ:** Với phép toán số học chịu binary numeric promotion, ưu tiên kiểu **`double > float > long > int`**. Chọn kiểu trước khi tính không đảm bảo luôn chính xác: phép `int` có thể tràn, còn chuyển số nguyên sang `float`/`double` có thể làm tròn. Để có kết quả mong muốn, hãy xác định kiểu của **từng phép toán và biểu thức con**, chủ động chọn toán hạng phù hợp và chỉ sau đó xét phép gán kết quả.

**Nguồn tham khảo:** [JLS §5.6 — Numeric Contexts and Promotions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html#jls-5.6), [JLS §15.17 — Multiplicative Operators](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.17), [JLS §15.18 — Additive Operators](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.18), [JLS §15.20 — Relational Operators](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.20), [JLS §15.26 — Assignment Operators](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.26).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-promotion-003">Vì sao byte cộng byte thường cho kết quả int</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao `byte` cộng `byte` thường cho kết quả `int` trong Java?

Trong Java, biểu thức cộng hai biến `byte` được **nâng kiểu thành `int` trước khi tính**. Vì vậy, dù hai toán hạng có kiểu `byte` thì **kiểu của biểu thức `a + b` là `int`**, không phải `byte`. Đây là quy tắc **binary numeric promotion** (*nâng kiểu số học nhị nguyên*) được ngôn ngữ áp dụng khi biên dịch.

```java
byte a = 10;
byte b = 20;
int sum = a + b;  // hợp lệ, sum == 30
// byte wrong = a + b; // lỗi biên dịch: incompatible types / possible lossy conversion
```

Tính toán ra `30` nằm trong miền `byte` không làm compiler tự đổi kiểu kết quả của `a + b`. Trình biên dịch dựa vào **kiểu của biểu thức**, không suy luận mọi giá trị tương lai của biến. Có một ngoại lệ quan trọng cho **biểu thức hằng tại thời điểm biên dịch** như `byte c = 10 + 20;`, được trình bày bên dưới.

## 1. `byte` lưu được gì, vì sao Java không giữ kiểu `byte` khi cộng?

`byte` là primitive nguyên **8 bit có dấu**, miền `-128` đến `127`. Hai giá trị `byte` hoàn toàn hợp lệ có thể có tổng không biểu diễn được trong 8 bit:

```java
byte a = 100;
byte b = 50;
int sum = a + b; // 150: hợp lệ ở int, vượt byte
```

Nếu kết quả tự bị giới hạn vào `byte`, `150` sẽ thành `-106` khi ép kiểu xuống 8 bit. Java tránh làm hẹp kết quả của phép cộng thông thường một cách ngầm định bằng cách chọn `int` cho biểu thức.

Tuy nhiên, lý do **chuẩn xác ở mức ngôn ngữ** là quy tắc binary numeric promotion: các toán hạng kiểu `byte`, `short` và `char` được nâng thành `int` cho các phép toán số học như `+`, `-`, `*`, `/`, `%`. Việc chọn `int` không phụ thuộc vào tổng cụ thể là 30, 150 hay số âm. Với hai `byte`, `int` luôn đủ để chứa tổng toán học của riêng phép cộng đó: miền tổng là `-256` đến `254`.

Bảng nhanh:

| Toán hạng | Biểu thức | Kiểu biểu thức | Ví dụ kết quả |
| --- | --- | --- | --- |
| `byte` và `byte` | `a + b` | `int` | `10 + 20 = 30` |
| `short` và `short` | `s1 + s2` | `int` | `100 + 200 = 300` |
| `char` và `char` | `c1 + c2` | `int` | `'A' + 'B' = 131` |
| `byte` và `int` | `b + i` | `int` | Phụ thuộc giá trị |
| `byte` và `long` | `b + l` | `long` | Phụ thuộc giá trị |
| `byte` và `float` | `b + f` | `float` | Có thể làm tròn |
| `byte` và `double` | `b + d` | `double` | Có thể làm tròn |

Khi `+` có toán hạng kiểu `String`, nó có thể trở thành phép **nối chuỗi**, và quy tắc bảng số học này không còn áp dụng cho phép nối chuỗi ấy.

## 2. Quy tắc binary numeric promotion

Với các phép toán số học nhị nguyên cần nâng kiểu, Java xét hai toán hạng rồi chọn kiểu chung theo thứ tự ưu tiên:

1. Nếu một trong hai toán hạng là `double`, chuyển toán hạng còn lại sang `double`; kết quả `double`.
2. Ngược lại, nếu một toán hạng là `float`, chuyển toán hạng còn lại sang `float`; kết quả `float`.
3. Ngược lại, nếu một toán hạng là `long`, chuyển toán hạng còn lại sang `long`; kết quả `long`.
4. Nếu không thuộc ba trường hợp trên, cả hai toán hạng được chuyển sang `int`; kết quả `int`.

Vì `byte` không phải `double`, `float` hay `long`, phép cộng `byte + byte` rơi vào bước cuối:

```text
byte a ──widening──> int ──┐
                            ├── phép cộng int ──> int
byte b ──widening──> int ──┘
```

Widening `byte` → `int` giữ chính xác giá trị của từng toán hạng; đây là lý do tổng hai `byte` không tự tràn `byte` trước khi có phép cast hoặc gán thu hẹp khác.

```java
byte a = 120;
byte b = 120;

int result = a + b;        // 240
long widened = a + b;      // int 240 được mở rộng sau phép cộng
float asFloat = a + b;     // int 240 được mở rộng sau phép cộng
```

Chú ý: `widened` có kiểu `long` nhưng `a + b` vẫn là **biểu thức `int`**. Trong ví dụ chỉ có hai `byte`, tổng luôn nằm trong `int`; còn với các toán hạng `int` lớn, việc mở rộng **sau** phép cộng có thể quá muộn để ngăn tràn.

### Không phải mọi toán tử đều trả kiểu `int`

`a < b` với hai `byte` cũng có thể nâng hai toán hạng để so sánh, nhưng **kết quả toán tử so sánh là `boolean`**, không phải `int`. Toán tử cộng chuỗi có quy tắc riêng. Bài học này nói về kiểu của **kết quả các phép toán số học** chứ không tuyên bố mọi biểu thức có `byte` đều trả `int`.

## 3. Vì sao `byte c = a + b;` lỗi dù kết quả có thể nhỏ?

Giả sử:

```java
byte a = 10;
byte b = 20;
// byte c = a + b; // lỗi biên dịch
```

Trình biên dịch biết `a + b` có kiểu `int`. Ngữ cảnh gán trực tiếp từ một **biến hoặc biểu thức `int` thông thường** sang `byte` là **narrowing primitive conversion**, không được phép tự động ở đây.

Bất kể bạn đã “tính nhẩm” thấy 10 + 20 = 30, hai biến có thể đổi trong quá trình chạy. Quan trọng hơn: quy tắc gán hoạt động theo thông tin kiểu và ngoại lệ hằng đã được định nghĩa, chứ không chạy chương trình để dự đoán kết quả.

Các cách sửa tùy yêu cầu:

```java
byte a = 10;
byte b = 20;

int exactSum = a + b;           // nên dùng khi muốn giữ giá trị tổng
byte castSum = (byte) (a + b);   // hợp lệ, nhưng có thể mất dữ liệu nếu tổng ngoài byte
```

Nếu cần chắc chắn không vượt miền `byte`, kiểm tra **trước khi cast**:

```java
int candidate = a + b;
if (candidate < Byte.MIN_VALUE || candidate > Byte.MAX_VALUE) {
    throw new ArithmeticException("Sum outside byte range");
}
byte safeSum = (byte) candidate;
```

Đoạn này dùng trong một method/class đã khai báo `a` và `b`, không phải một chương trình độc lập. Trong ứng dụng, thường dễ hiểu hơn khi giữ kết quả dưới kiểu `int` nếu không bị giới hạn bởi API/bộ nhớ/định dạng dữ liệu buộc dùng `byte`.

## 4. Ngoại lệ quan trọng: biểu thức hằng `int` có thể gán vào `byte`

Vì `10` và `20` là literal kiểu `int`, `10 + 20` cũng là biểu thức `int`. Tuy nhiên Java cho phép **narrowing conversion trong ngữ cảnh gán** đối với một số biểu thức hằng tại thời điểm biên dịch, miễn giá trị nằm trong miền kiểu đích.

```java
byte direct = 10 + 20;     // hợp lệ: biểu thức hằng int = 30, vừa byte
byte maximum = 100 + 27;   // hợp lệ: 127
// byte tooLarge = 100 + 28; // lỗi: 128 nằm ngoài byte

short smallShort = 20_000 + 10_000; // hợp lệ: 30000
char letter = 60 + 5;      // hợp lệ: mã 65, tức 'A'
```

**Đây không phải bằng chứng `10 + 20` có kiểu `byte`.** Nó vẫn có kiểu `int`. Compiler cho phép chuyển hẹp một **giá trị hằng xác định được khi biên dịch** vì biết chắc kết quả nằm trong miền đích.

### `final` có thể tạo *constant variable*

Biến primitive được khai báo `final` và khởi tạo bằng biểu thức hằng có thể tham gia biểu thức hằng:

```java
final byte left = 10;
final byte right = 20;
byte result = left + right; // hợp lệ: left + right là constant expression int = 30
```

Tuy nhiên:

```java
byte left = 10;
byte right = 20;
// byte result = left + right; // lỗi: left/right không phải constant variables
```

Nếu biến `final` chỉ được gán bằng một giá trị xác định **lúc chạy** (ví dụ tham số method), nó không đương nhiên là constant variable theo nghĩa compiler sử dụng ở đây.

Đây là lý do nói “`byte` cộng `byte` **thường** cho kết quả `int`”: trên thực tế **biểu thức cộng luôn có kiểu `int`** khi hai toán hạng mang kiểu `byte`; còn việc gán biểu thức vào biến `byte` có thể được cho phép nhờ **ngoại lệ constant expression**, không nhờ thay đổi kiểu kết quả.

## 5. Cast về `byte`: cắt 8 bit thấp và có thể đổi dấu

`byte narrowed = (byte) (a + b);` yêu cầu narrowing từ `int` sang `byte`. Java giữ **8 bit thấp** của giá trị `int`, rồi diễn giải chúng thành số nguyên có dấu theo bù hai.

| Tổng toán học | `int` từ `a + b` | Cast về `byte` | Nhận xét |
| --- | ---: | ---: | --- |
| `10 + 20` | 30 | 30 | Còn trong miền |
| `100 + 50` | 150 | -106 | 150 - 256 = -106 |
| `120 + 120` | 240 | -16 | 240 - 256 = -16 |
| `-100 + (-50)` | -150 | 106 | Giữ bit thấp, đổi dấu |
| `127 + 1` | 128 | -128 | Qua biên dương |

```java
byte a = 100;
byte b = 50;
int trueSum = a + b;            // 150
byte narrowed = (byte) (a + b); // -106
```

Đây không phải `int` bị tràn khi cộng hai `byte`. **Phép cộng `int` tạo 150 đúng**, còn **cast** làm mất thông tin vì kiểu đích chỉ có 8 bit. Phân biệt vị trí phát sinh mất dữ liệu giúp debug đúng nguyên nhân.

Nếu muốn diễn giải một `byte` như số không dấu từ 0 đến 255, có thể dùng `Byte.toUnsignedInt(value)`. Ví dụ `Byte.toUnsignedInt((byte) 0xFF)` cho 255; bản thân `byte` vẫn có dấu và trong Java không tồn tại kiểu primitive tên `unsigned byte`.

## 6. Tại sao `b += n` và `b++` vẫn biên dịch?

Toán tử **gán kết hợp** như `+=` có quy tắc riêng: tính biểu thức và chuyển kết quả trở lại kiểu của biến đích. Vì vậy:

```java
byte b = 120;
b += 10;             // hợp lệ: 130 bị thu hẹp thành -126
// b = b + 10;        // lỗi: b + 10 kiểu int, không được tự gán cho byte
b = (byte) (b + 10); // hợp lệ nếu chủ ý cast
```

Một cách hiểu gần đúng về kiểu của `E1 op= E2` là `E1 = (T) ((E1) op (E2))`, trong đó `T` là kiểu biến `E1`; điểm khác biệt quan trọng là vế trái `E1` **chỉ được đánh giá một lần**. Đây là ngữ nghĩa gán kết hợp của Java, không phải vì `b + 10` đã mang kiểu `byte`.

### Prefix/postfix `++` và `--`

`++` và `--` cũng có quy tắc đặc biệt để cập nhật biến, nên dùng được với `byte`:

```java
byte b = 127;
byte old = b++; // old = 127; b = -128
byte now = ++b; // now = -127; b = -127
```

Khác với `+b` (kết quả `int` do *unary numeric promotion*), giá trị biểu thức `b++` và `++b` có **kiểu `byte`** khi `b` là `byte`. Chúng tự ghi trở lại biến và có thể tràn khi vượt miền.

Mặc dù `b += 1` và `b++` có thể trông giống phép `b = b + 1`, chúng không có cùng quy tắc chuyển kiểu khi gán. Muốn tránh cast mất dữ liệu, hãy giữ phép tính bằng `int` hoặc kiểm tra miền trước khi ghi lại `byte`.

## 7. `Math.addExact` có đổi kết quả về `byte` không?

Không. `Math.addExact(int, int)` trả `int`, và Java tự widening hai đối số `byte` sang `int` khi truyền tham số:

```java
byte a = 100;
byte b = 50;
int sum = Math.addExact(a, b); // 150, vẫn là int
```

`Math.addExact` bảo vệ phép cộng khỏi tràn **theo kiểu `int`** (hoặc kiểu `long` nếu chọn overload `long`). Nó **không** kiểm tra liệu tổng `150` có còn nằm trong miền `byte` hay không. Muốn bảo đảm giá trị thuộc miền `byte`, vẫn cần kiểm tra `Byte.MIN_VALUE`/`Byte.MAX_VALUE` trước cast.

Có thể viết helper rõ ý nghĩa nghiệp vụ:

```java
static byte addByteExactly(byte a, byte b) {
    int sum = a + b; // phép cộng int chính xác cho hai byte
    if (sum < Byte.MIN_VALUE || sum > Byte.MAX_VALUE) {
        throw new ArithmeticException("Byte overflow");
    }
    return (byte) sum;
}
```

Đây là **một method minh họa đặt trong class**, không phải chương trình độc lập. Nó từ chối tổng ngoài miền `byte` thay vì cho phép quay vòng.

Vì giới hạn `byte` thấp hơn `int` rất nhiều, có thể cộng hai `byte` an toàn ở `int` mà không cần `Math.addExact` cho riêng phép cộng đó. Việc kiểm tra miền `byte` mới là bước quyết định nếu API yêu cầu kết quả `byte`.

## 8. Numeric promotion trong chuỗi biểu thức và method overload

Sau phép cộng đầu tiên, **giá trị biểu thức đã có kiểu `int`**, nên các phép toán tiếp theo sẽ dùng kiểu mới đó (trừ khi xuất hiện toán hạng có kiểu cao hơn theo quy tắc binary numeric promotion):

```java
byte a = 10, b = 20, c = 30;
int total = a + b + c;    // (int)(a+b) + c -> int 60
long longer = a + b + 1L; // phép cộng cuối có long, kết quả long
```

Khi có `long` hoặc số thực, kiểu kết quả khác:

```java
byte a = 10;
byte b = 20;
long resultLong = a + 20L;      // phép cộng long
float resultFloat = a + 20.0f; // phép cộng float
double resultDouble = a + 20.0; // phép cộng double
```

Nếu kết hợp `int` lớn, cần chú ý vị trí của việc mở rộng:

```java
int max = Integer.MAX_VALUE;
long overflowed = max + 1;        // phép cộng int tràn trước rồi mới widening
long correct = (long) max + 1;    // phép cộng long, kết quả 2147483648
```

Mặc dù **hai `byte` cộng nhau không làm tràn `int`**, nguyên tắc về kiểu trung gian vẫn quan trọng khi biểu thức đã có `int` hoặc chứa toán hạng lớn hơn.

### Kết quả `int` ảnh hưởng overload

```java
static String classify(byte n) {
    return "byte";
}

static String classify(int n) {
    return "int";
}
```

Nếu `a` và `b` có kiểu `byte`:

- `classify(a)` chọn overload nhận `byte`.
- `classify(a + b)` chọn overload nhận `int`.
- `classify((byte) (a + b))` chọn overload nhận `byte`.

Đây là các method minh họa để đặt trong một class. Khi Java chọn overload cho `a+b`, nó nhìn **kiểu compile-time `int` của biểu thức**, không vì giá trị cuối tình cờ vừa `byte` mà tự chọn method nhận `byte`.

### `var` cũng suy luận từ kiểu biểu thức

```java
byte a = 10, b = 20;
var result = a + b; // result được suy luận là int, không phải byte
```

`var` không suy luận kiểu “nhỏ nhất có thể chứa giá trị”; nó dùng kiểu của biểu thức khởi tạo đã được ngôn ngữ xác định.

## 9. Wrapper `Byte` và unboxing

`Byte` là kiểu tham chiếu (wrapper) khác với primitive `byte`. Nếu hai tham chiếu `Byte` được cộng, Java có thể **unbox** chúng thành `byte` rồi áp dụng binary numeric promotion:

```java
Byte boxedA = Byte.valueOf((byte) 10);
Byte boxedB = Byte.valueOf((byte) 20);
int sum = boxedA + boxedB; // 30; unbox -> byte -> int -> cộng
```

Không thể tự động gán kết quả `int` cho `Byte` chỉ nhờ boxing:

```java
Byte boxedA = Byte.valueOf((byte) 10);
Byte boxedB = Byte.valueOf((byte) 20);
// Byte wrong = boxedA + boxedB; // lỗi: kết quả int không tự narrow rồi box
Byte ok = (byte) (boxedA + boxedB); // narrowing rõ ràng rồi boxing
```

Nếu một wrapper là `null`, bước unboxing có thể ném `NullPointerException` trước khi xảy ra phép cộng. Đây là một lỗi runtime khác với lỗi biên dịch khi cố gán `int` vào `byte`.

## 10. Phân biệt cộng số và nối chuỗi khi in kết quả

Biểu thức cộng số có kiểu `int`, nhưng toán tử `+` cũng dùng để nối chuỗi:

```java
byte a = 10, b = 20;
System.out.println("sum=" + a + b);   // sum=1020
System.out.println("sum=" + (a + b)); // sum=30
```

`+` có kết hợp từ trái sang phải: câu đầu tiên bắt đầu với `String`, tạo `"sum=10"` rồi nối `20`. Dấu ngoặc trong câu thứ hai buộc phép cộng số `a + b` diễn ra trước, cho `int` `30`, sau đó mới chuyển kết quả sang chuỗi.

Đây không phải vấn đề `byte` hay `int` bị tràn; nó là vấn đề **phân nhóm biểu thức và overload của toán tử `+`**.

## 11. Ví dụ Java 17+ đầy đủ, kiểm chứng kiểu và dữ liệu

Tạo tệp **`ByteAdditionDemo.java`**:

```java
public class ByteAdditionDemo {
    static String classify(byte value) {
        return "byte";
    }

    static String classify(int value) {
        return "int";
    }

    static byte addByteExactly(byte a, byte b) {
        int sum = a + b;
        if (sum < Byte.MIN_VALUE || sum > Byte.MAX_VALUE) {
            throw new ArithmeticException("Byte overflow");
        }
        return (byte) sum;
    }

    public static void main(String[] args) {
        byte a = 10;
        byte b = 20;
        int sum = a + b;
        System.out.println("sum=" + sum);
        System.out.println("firstType=" + classify(a));
        System.out.println("sumType=" + classify(a + b));
        System.out.println("castType=" + classify((byte) (a + b)));

        byte constant = 10 + 20;
        final byte f1 = 10;
        final byte f2 = 20;
        byte fromFinals = f1 + f2;
        System.out.println("constant=" + constant);
        System.out.println("fromFinals=" + fromFinals);

        byte largeA = 100;
        byte largeB = 50;
        System.out.println("wideSum=" + (largeA + largeB));
        System.out.println("narrowSum=" + (byte) (largeA + largeB));

        byte compound = 120;
        compound += 10;
        System.out.println("compound=" + compound);

        byte counter = 127;
        byte old = counter++;
        System.out.println("postOld=" + old);
        System.out.println("postCurrent=" + counter);

        var inferred = a + b;
        System.out.println("varType=" + classify(inferred));
        System.out.println("exactSmall=" + addByteExactly(a, b));

        try {
            addByteExactly(largeA, largeB);
            System.out.println("byteOverflow=missed");
        } catch (ArithmeticException ex) {
            System.out.println("byteOverflow=detected");
        }

        System.out.println("concatNoParen=" + a + b);
        System.out.println("concatWithParen=" + (a + b));

        Byte boxedA = Byte.valueOf((byte) 10);
        Byte boxedB = Byte.valueOf((byte) 20);
        System.out.println("boxedSum=" + (boxedA + boxedB));
    }
}
```

Tại thư mục có tệp trên, chạy:

```bash
javac ByteAdditionDemo.java
java ByteAdditionDemo
```

Output chính xác:

```text
sum=30
firstType=byte
sumType=int
castType=byte
constant=30
fromFinals=30
wideSum=150
narrowSum=-106
compound=-126
postOld=127
postCurrent=-128
varType=int
exactSmall=30
byteOverflow=detected
concatNoParen=1020
concatWithParen=30
boxedSum=30
```

Qua `sumType=int`, ta quan sát được kiểu `int` của biểu thức `a + b` ngay từ lúc compiler chọn overload. `constant` và `fromFinals` chứng minh ngoại lệ gán biểu thức hằng. `wideSum` với `narrowSum` cho thấy mất dữ liệu xảy ra lúc cast xuống `byte`. `compound` và `postCurrent` cho thấy toán tử cập nhật có thể thu hẹp kết quả và quay vòng.

## 12. Các lỗi phổ biến và cách ghi nhớ

| Phát biểu hoặc code | Đánh giá | Lý do |
| --- | --- | --- |
| “Hai biến `byte` cộng nhau trả `byte`.” | Sai | Binary numeric promotion tạo `int` |
| `byte r = a + b;` với `a`, `b` biến thường | Sai | Không tự narrowing `int` → `byte` |
| `byte r = 10 + 20;` | Đúng | Biểu thức hằng `int` bằng 30, vừa `byte` |
| `final byte a=10,b=20; byte r=a+b;` | Đúng | Hai biến `final` khởi tạo bằng hằng tạo constant expression |
| `int r = a + b;` | Đúng | Nhận đúng kiểu biểu thức, giữ mọi tổng của hai `byte` |
| `byte r = (byte)(a+b);` | Biên dịch được | Có thể mất dữ liệu nếu tổng ngoài -128..127 |
| `byte b=120; b+=10;` | Biên dịch được | Gán kết hợp thu hẹp ngầm; b thành -126 |
| `byte b=127; b++;` | Biên dịch được | Cập nhật có thể quay vòng về -128 |
| “`Math.addExact(a,b)` tự trả `byte`.” | Sai | Với hai `byte`, overload `int,int` trả `int` |
| “`var x = a+b` suy luận `byte`.” | Sai | `var` suy luận kiểu biểu thức: `int` |
| “`Byte + Byte` trả `Byte`.” | Sai | Unboxing và numeric promotion khiến kết quả là `int` |

Khi gặp phép cộng với kiểu nguyên nhỏ, hãy tự hỏi:

1. **Toán hạng có kiểu gì?** `byte`, `short`, `char` được nâng sang `int` khi dùng toán tử `+` số học thông thường.
2. **Kiểu biểu thức là gì?** Hai `byte` → `int`, kể cả khi tổng nhỏ.
3. **Biểu thức có là hằng tại thời điểm biên dịch không?** Nếu có và kết quả vừa `byte`, phép gán thu hẹp đặc biệt có thể hợp lệ.
4. **Kết quả được gán vào đâu?** `int` giữ giá trị; cast sang `byte` có nguy cơ mất bit; `+=` và `++` có quy tắc ghi lại khác.
5. **Bài toán có yêu cầu từ chối tràn miền `byte` không?** Kiểm tra khoảng `[-128,127]` trước cast, hoặc giữ kết quả dạng `int`.

**Kết luận:** `byte + byte` trong Java có **kiểu biểu thức `int`** theo quy tắc *binary numeric promotion*. Ngoại lệ biểu thức hằng chỉ nới điều kiện **gán xuống `byte`**, không thay đổi kiểu phép cộng. Biết rõ sự khác nhau này giúp tránh cả lỗi biên dịch lẫn việc cắt bit ngoài ý muốn khi cast hoặc dùng phép gán kết hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-promotion-004">Gán biểu thức hằng trong phạm vi byte, short hoặc char</a>

<details>
<summary>Xem chi tiết</summary>

# Gán biểu thức hằng trong phạm vi byte, short hoặc char trong Java

Trong Java, các phép toán số học trên `byte`, `short`, `char` thường được **nâng kiểu lên ít nhất `int`**. Kết quả của `byte + byte` là `int`, do đó thường không được gán thẳng về `byte`. Tuy nhiên, Java cho phép một ngoại lệ hữu ích: khi biểu thức bên phải là **biểu thức hằng tại thời điểm biên dịch** và giá trị vừa kiểu đích `byte`, `short` hoặc `char`, trình biên dịch có thể **thu hẹp kiểu ngầm định trong ngữ cảnh gán**.

Ví dụ:

~~~java
byte quantity = 100;      // hợp lệ, hằng int 100 nằm trong miền byte
byte total = 100 + 20;    // hợp lệ, biểu thức hằng int 120 vừa byte
short high = 32000 + 767;// hợp lệ, hằng int 32767 vừa short
char letter = 'A' + 1;    // hợp lệ, hằng int 66 chuyển sang char 'B'
~~~

Quy tắc này không có nghĩa mọi giá trị số nhỏ đều được Java tự ép kiểu. Trình biên dịch phải kiểm tra **kiểu biểu thức**, **tính chất hằng** và **miền biểu diễn**. Biết giá trị tại lúc chạy vẫn chưa đủ.

## 1. Miền của các kiểu đích

| Kiểu | Bit | Miền số nguyên | Ví dụ trong miền | Ví dụ ngoài miền |
| --- | ---: | ---: | --- | --- |
| `byte` | 8, có dấu | -128 đến 127 | -128, 0, 127 | -129, 128 |
| `short` | 16, có dấu | -32768 đến 32767 | -32768, 32767 | -32769, 32768 |
| `char` | 16, không dấu | 0 đến 65535 | 0, 65, 65535 | -1, 65536 |

Trong ngữ nghĩa văn bản, `char` là một **đơn vị mã UTF-16**, chưa chắc chứa được một ký tự Unicode hoàn chỉnh khi ký tự dùng surrogate pair. Trong chủ đề này, ta xét miền số nguyên 16 bit của `char` để hiểu cơ chế chuyển đổi.

Đối với các literal số nguyên thập phân không có hậu tố `L`, kiểu mặc định là `int` nếu giá trị có thể biểu diễn được ở `int`. Dấu âm là toán tử đơn ngôi. Các khai báo sau đều hợp lệ:

~~~java
byte minByte = -128;
byte maxByte = 127;
short minShort = -32768;
short maxShort = 32767;
char firstChar = 0;
char lastChar = 65535;
~~~

Dù biểu thức nguồn mang kiểu `int`, các giá trị đều là hằng và biểu diễn được ở kiểu đích. Trình biên dịch chấp nhận phép gán mà không đòi cast.

## 2. Quy tắc assignment conversion (JLS §5.2)

Trong **assignment context**, biểu thức có kiểu `byte`, `short`, `char` hoặc `int` và là **constant expression** có thể được thu hẹp sang `byte`, `short` hoặc `char` khi giá trị cuối cùng **biểu diễn được trong kiểu đích**.

Hãy phân tích một phép gán theo thứ tự:

1. Xác định **kiểu biểu thức nguồn**. Ví dụ `100 + 20` có kiểu `int` do binary numeric promotion.
2. Kiểm tra biểu thức có là **hằng tại thời điểm biên dịch** theo định nghĩa của ngôn ngữ hay không.
3. Xác định giá trị của **toàn bộ biểu thức**, không chỉ từng toán hạng.
4. So với miền của kiểu đích. Nếu ngoài miền, không được thu hẹp ngầm định theo ngoại lệ này.

Ví dụ cùng số 128:

~~~java
short okShort = 128;      // hợp lệ: 128 trong miền short
char okChar = 128;        // hợp lệ: 128 trong miền char
// byte badByte = 128;    // lỗi biên dịch: 128 > 127
~~~

Và số âm:

~~~java
byte low = -128;          // hợp lệ
short negative = -128;    // hợp lệ
// char badChar = -128;   // lỗi: char không lưu giá trị âm
~~~

**Giới hạn về kiểu nguồn:** ngoại lệ trên không tự áp dụng cho biểu thức kiểu `long`, `float` hoặc `double`, cho dù giá trị toán học rất nhỏ.

~~~java
// byte a = 42L;          // lỗi: literal kiểu long
// short b = 42.0;        // lỗi: literal kiểu double
// char c = 65L;          // lỗi: literal kiểu long

byte a = 42;             // hợp lệ: hằng int vừa miền byte
short b = 42;            // hợp lệ
char c = 65;             // hợp lệ: mã UTF-16 của 'A'
~~~

Không có kiểu primitive `unsigned byte` hoặc `unsigned short` trong Java. Việc `char` không dấu không đồng nghĩa có thể chuyển mọi số âm sang `char` mà giữ giá trị.

## 3. Hằng biểu thức không chỉ là literal đơn lẻ

Một **constant expression** có thể được tạo bằng literal và các toán tử được Java cho phép, cũng như tham chiếu tới những biến hằng đủ điều kiện. Java tính được kết quả trước khi chạy chương trình.

~~~java
byte sum = 20 + 30;           // 50
byte product = 6 * 7;         // 42
short shifted = 1 << 14;      // 16384
char next = 'A' + 1;          // 'B', mã số 66
byte selected = (8 > 3) ? 10 : 20; // 10
~~~

`'A' + 1` là biểu thức có kiểu `int`; tuy nhiên giá trị hằng là 66, vừa miền `char`, nên phép gán được chấp nhận. Khả năng gán này không đổi kiểu của toán tử `+`.

Trường hợp biên:

~~~java
byte edge = 127;                  // hợp lệ
// byte tooHigh = 127 + 1;        // lỗi: kết quả 128
short shortEdge = 32760 + 7;      // hợp lệ: 32767
// short tooHighShort = 32760 + 8; // lỗi: 32768
char charEdge = 65535;            // hợp lệ
// char tooHighChar = 65535 + 1;   // lỗi: 65536
// char negativeChar = 'A' - 66;   // lỗi: -1
~~~

Hai toán hạng của `127 + 1` đều rất nhỏ, nhưng **kết quả 128** vượt giới hạn của `byte`. Với phép dịch bit cũng phải kiểm tra kết quả:

~~~java
byte flag = 1 << 6;          // 64, hợp lệ
// byte overflow = 1 << 7;   // 128, lỗi biên dịch
~~~

Kết quả của phép dịch có kiểu `int` trong cả hai dòng. Chỉ dòng thứ nhất có giá trị hằng vừa miền kiểu đích.

## 4. final và constant variable không hoàn toàn giống nhau

Một **constant variable** trong JLS là biến `final` có kiểu nguyên thủy hoặc `String` và được khởi tạo bằng một constant expression. Với các kiểu số nguyên, tham chiếu tới constant variable có thể tham gia biểu thức hằng mới.

~~~java
final int MAX = 127;
byte maxByte = MAX;             // hợp lệ

final int BASE = 40;
byte answer = BASE + 2;        // hợp lệ: biểu thức hằng 42

final short LETTER_CODE = 65;
char letter = LETTER_CODE;     // hợp lệ: hằng short có giá trị 65

final char DOT = '.';
byte dotCode = DOT;            // hợp lệ: '.' có mã 46, vừa miền byte
~~~

Dòng `char letter = LETTER_CODE` hợp lệ vì đây là biểu thức hằng kiểu `short` có giá trị vừa `char`. Một biến `short` bình thường không được phép gán ngầm định sang `char` chỉ nhờ giá trị hiện tại.

Ngược lại:

~~~java
int mutable = 42;
// byte a = mutable;          // lỗi: mutable không phải hằng biên dịch

final int runtime = Integer.parseInt("42");
// byte b = runtime;          // lỗi: final nhưng không phải constant variable
byte b = (byte) runtime;      // cast tường minh, cần kiểm tra miền
~~~

`final` chỉ ngăn việc gán lại biến. Giá trị `runtime` phải lấy từ lời gọi phương thức, và lời gọi phương thức không thuộc các dạng constant expression được chấp nhận. Dù ai cũng biết `Integer.parseInt("42")` trả 42, trình biên dịch không dùng hiểu biết đó để áp dụng ngoại lệ thu hẹp.

Một cặp ví dụ khác:

~~~java
final int COMPILE_TIME = 100 + 20;
byte x = COMPILE_TIME;          // hợp lệ

final int RUN_TIME = Math.abs(-120);
// byte y = RUN_TIME;           // lỗi: không phải biểu thức hằng
byte y = (byte) RUN_TIME;       // hợp lệ cú pháp, cần xem giá trị
~~~

Vì thế khi gặp lỗi “possible lossy conversion”, hãy hỏi **biến có phải constant variable không** trước khi thêm cast một cách tự động.

## 5. Liên hệ với numeric promotion

Trong các phép toán số học nhị phân, `byte`, `short` và `char` thường được nâng lên ít nhất `int`. Ví dụ `byte + byte` cho kết quả kiểu `int` dù tổng thực tế vẫn rất nhỏ.

~~~java
byte left = 10;
byte right = 20;
int total = left + right;          // hợp lệ
// byte wrong = left + right;      // lỗi: int không hằng
byte explicit = (byte) (left + right); // hợp lệ cú pháp, phải xem miền
~~~

Dù bạn biết tổng bằng 30, `left` và `right` là các biến thường, nên `left + right` không phải constant expression.

So sánh:

~~~java
final byte left = 10;
final byte right = 20;
byte sum = left + right;          // hợp lệ vì biểu thức hằng int bằng 30
~~~

**Kiểu của `left + right` vẫn là `int`.** Trình biên dịch cho phép thu hẹp tại phép gán chỉ vì kết quả hằng 30 vừa `byte`; không có quy tắc riêng khiến `final byte + final byte` trở thành `byte`.

Một chỗ rất dễ nhầm là **compound assignment**:

~~~java
byte count = 120;
// count = count + 10;           // lỗi: int -> byte không tự thu hẹp
count += 10;                    // hợp lệ, nhưng kết quả sẽ là -126
~~~

`+=` có cơ chế chuyển đổi về kiểu vế trái mang hiệu ứng tương tự cast, ngoại trừ việc vế trái chỉ được đánh giá một lần. Vì `120 + 10 = 130` nằm ngoài miền `byte`, kết quả lưu vào `count` là -126. Quy tắc này **không phải** ngoại lệ thu hẹp hằng có kiểm tra giá trị trong miền; vì vậy `+=` có thể gây tràn mà vẫn biên dịch.

## 6. Ngữ cảnh gán không đồng nhất với truyền tham số phương thức

Ngoại lệ này áp dụng khi **khởi tạo biến, dùng phép gán hoặc khởi tạo từng phần tử mảng**. Nhưng **method invocation context** có quy tắc chuyển đổi khác: đối số hằng `int` không tự thu hẹp thành `byte` chỉ để khớp tham số phương thức.

Chương trình ví dụ lưu thành `InvocationExample.java`:

~~~java
class InvocationExample {
    static void consume(byte value) {
        System.out.println(value);
    }

    public static void main(String[] args) {
        byte local = 42;          // hợp lệ: ngữ cảnh gán
        consume(local);           // hợp lệ: byte -> byte
        consume((byte) 42);       // hợp lệ: ép kiểu rõ ràng
        // consume(42);           // lỗi: literal int không tự thành byte
    }
}
~~~

Kết luận: `byte b = 42` là cú pháp hợp lệ không có nghĩa `consume(42)` cũng hợp lệ. Với lời gọi, hãy cung cấp biểu thức có kiểu tương thích hoặc chuyển đổi tường minh phù hợp.

Trong array initializer, từng phần tử được kiểm tra theo ngữ cảnh gán:

~~~java
byte[] signedSamples = {0, 127, -128};
char[] letters = {'A', 66};
// byte[] badSamples = {128};    // lỗi biên dịch: phần tử ngoài miền byte
~~~

Phép gán phần tử 66 vào mảng `char[]` cũng dựa trên việc hằng `int` 66 vừa miền `char`.

## 7. Cast tường minh và rủi ro mất giá trị

Nếu biểu thức không phải hằng hoặc hằng vượt miền, bạn có thể viết cast để thu hẹp, nhưng cast không bảo đảm giữ nguyên giá trị toán học. Chuyển đổi hẹp số nguyên loại bỏ các bit cao; `byte` và `short` sau đó được diễn giải theo số có dấu.

~~~java
byte wrappedByte = (byte) 128;          // -128
short wrappedShort = (short) 32768;    // -32768
char wrappedChar = (char) -1;          // giá trị số 65535
~~~

Những đoạn trên **biên dịch được** nhưng giá trị đã thay đổi. Đối với số lượng, kích thước, giới hạn hoặc ID, việc wrap âm thầm có thể tạo lỗi nghiệp vụ.

Khi nguồn là `int` tính ở thời điểm chạy, hãy kiểm tra trước khi cast nếu cần giữ đúng giá trị số signed:

~~~java
static byte checkedByte(int value) {
    if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
        throw new IllegalArgumentException("Ngoai mien byte: " + value);
    }
    return (byte) value;
}
~~~

Đây là **đoạn phương thức minh họa**, đặt trong một lớp Java. Với `short` kiểm tra `Short.MIN_VALUE` và `Short.MAX_VALUE`; với `char` kiểm tra 0–65535. Dữ liệu **unsigned** trong protocol là ngữ nghĩa riêng: nên giữ ở kiểu đủ rộng hoặc chuyển đổi bit theo đặc tả, không mặc định đồng nhất nó với miền signed `byte` hoặc `short`.

## 8. Ví dụ Java 17+ hoàn chỉnh

Tạo tệp `ConstantNarrowingDemo.java`:

~~~java
public class ConstantNarrowingDemo {
    static final int BASE = 40;
    static final short LETTER_CODE = 65;

    public static void main(String[] args) {
        byte literal = 127;
        byte arithmetic = 100 + 20;
        short shortMax = 32_760 + 7;
        char nextLetter = 'A' + 1;
        byte fromFinal = BASE + 2;
        char fromShort = LETTER_CODE;

        System.out.println("literal: " + literal);
        System.out.println("arithmetic: " + arithmetic);
        System.out.println("shortMax: " + shortMax);
        System.out.println("nextLetter: " + nextLetter);
        System.out.println("fromFinal: " + fromFinal);
        System.out.println("fromShort: " + fromShort);

        int runtime = Integer.parseInt("42");
        byte narrowed = checkedByte(runtime);
        System.out.println("narrowed: " + narrowed);

        byte wrapped = (byte) (127 + 1);
        System.out.println("wrapped: " + wrapped);

        byte count = 120;
        count += 10;
        System.out.println("compound: " + count);
    }

    static byte checkedByte(int value) {
        if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
            throw new IllegalArgumentException("Ngoai mien byte: " + value);
        }
        return (byte) value;
    }
}
~~~

Biên dịch, chạy bằng JDK 17+:

~~~bash
javac ConstantNarrowingDemo.java
java ConstantNarrowingDemo
~~~

Kết quả chính xác:

~~~text
literal: 127
arithmetic: 120
shortMax: 32767
nextLetter: B
fromFinal: 42
fromShort: A
narrowed: 42
wrapped: -128
compound: -126
~~~

Các trường hợp cần phân biệt:

- `BASE + 2` có kiểu `int` nhưng được xem là constant expression bằng 42 bởi `BASE` là constant variable. Thu hẹp về `byte` hợp lệ.
- `Integer.parseInt("42")` được gọi ở runtime; biến `runtime` dù hiện bằng 42 vẫn không phải constant variable. Phương thức `checkedByte` kiểm tra miền rồi mới cast.
- `(byte) (127 + 1)` được cast vì 128 vượt miền `byte` và cho kết quả -128; phép gán trực tiếp `byte wrapped = 127 + 1` sẽ lỗi biên dịch.
- `count += 10` hợp lệ do luật compound assignment và wrap từ 130 về -126.

## 9. Bảng so sánh nhanh

| Đoạn mã | Hợp lệ? | Lý do |
| --- | --- | --- |
| `byte b = 127;` | Có | Hằng int 127 vừa byte |
| `byte b = 128;` | Không | Hằng 128 vượt byte |
| `short s = 32767;` | Có | Hằng vừa short |
| `short s = 32768;` | Không | Hằng vượt short |
| `char c = 65535;` | Có | Hằng vừa char |
| `char c = -1;` | Không | Char không lưu số âm |
| `byte b = 60 + 60;` | Có | Biểu thức hằng kiểu int bằng 120 |
| `byte b = 60 + 70;` | Không | Biểu thức hằng bằng 130 vượt miền |
| `byte b = 42L;` | Không | Hằng kiểu long không thuộc ngoại lệ |
| `final int n = 42; byte b = n;` | Có | n là constant variable |
| `int n = 42; byte b = n;` | Không | n không phải constant variable |
| `byte b = (byte) 128;` | Có | Cast tường minh; b bằng -128 |
| `byte b = 120; b += 10;` | Có | Compound assignment; b bằng -126 |

## 10. Tự luyện và lời giải

**Câu 1.** Vì sao `byte a = 50 + 70;` biên dịch được, còn `int x = 50; byte b = x + 70;` thì không?  
**Đáp án:** Phép cộng thứ nhất là constant expression kiểu `int` có kết quả 120 vừa miền `byte`. Phép cộng thứ hai có biến thường `x` nên không phải biểu thức hằng.

**Câu 2.** `final int X = 200; byte b = X;` có hợp lệ không?  
**Đáp án:** Không. `X` là constant variable, nhưng giá trị hằng 200 vượt `Byte.MAX_VALUE` là 127.

**Câu 3.** Vì sao `char c = 'A' + 1;` hợp lệ dù `'A' + 1` có kiểu `int`?  
**Đáp án:** Toàn biểu thức là hằng bằng 66, vừa miền `char`. Phép chuyển đổi thu hẹp ngầm định ở phép gán tạo ra `'B'`.

**Câu 4.** `final int x = Integer.parseInt("12"); short s = x;` có hợp lệ không?  
**Đáp án:** Không. `x` là `final` nhưng khởi tạo bằng lời gọi phương thức, không phải constant variable; không thể thu hẹp ngầm định từ `int` về `short`.

**Câu 5.** Phân biệt `short s = 32767 + 1;` và `short s = (short) (32767 + 1);`.  
**Đáp án:** Cách đầu báo lỗi vì hằng 32768 ngoài miền `short`; cách sau ép kiểu tường minh nên biên dịch, nhưng giá trị lưu thành -32768, không giữ được số 32768.

## 11. Ghi nhớ

- Thu hẹp biểu thức hằng từ `byte`/`short`/`char`/`int` sang `byte`, `short` hoặc `char` chỉ xảy ra trong **ngữ cảnh gán phù hợp** và khi giá trị biểu diễn được.
- Numeric promotion vẫn áp dụng: `byte + byte` thường là `int`, dù có thể gán biểu thức **hằng** vừa miền trở lại `byte`.
- `final` không tự đồng nghĩa với constant variable; phần khởi tạo phải là một constant expression.
- Truyền đối số vào phương thức, compound assignment và cast có các quy tắc khác; đặc biệt cast hoặc `+=` có thể làm mất giá trị số.
- Khi gặp lỗi “possible lossy conversion”, hãy kiểm tra **kiểu nguồn, tính hằng và miền của kết quả**, rồi mới quyết định dùng kiểu rộng hơn, đổi logic hay cast có kiểm tra.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-promotion-005">Compound assignment và phép thu hẹp ngầm định</a>

<details>
<summary>Xem chi tiết</summary>

# Compound assignment và phép thu hẹp ngầm định trong Java

**Compound assignment** (phép gán kết hợp) là toán tử vừa thực hiện phép tính, vừa gán kết quả lại cho biến ở vế trái, như `+=`, `-=`, `*=`. Điểm dễ gây nhầm là **phép gán kết hợp có thể tự động thu hẹp kiểu của kết quả về kiểu biến nhận**, ngay cả khi phép gán thông thường với biểu thức tương đương sẽ **không biên dịch**.

```java
byte count = 10;
count += 1;          // Hợp lệ: phép cộng ở kiểu int, thu hẹp về byte.
// count = count + 1; // LỖI biên dịch: count + 1 có kiểu int.
```

Đây là mảnh mã có thể đặt trong một phương thức Java. Quy tắc hợp lệ của `count += 1` **không có nghĩa phép toán luôn an toàn, không tràn hay không mất dữ liệu**. Muốn sử dụng đúng, cần hiểu cách Java nâng kiểu biểu thức trước khi chuyển kết quả trở lại kiểu của vế trái.

## 1. Định nghĩa và các toán tử gán kết hợp

Ở phép gán thường `x = expression`, Java kiểm tra kết quả biểu thức có thể chuyển sang kiểu của `x` bằng **assignment conversion** hay không. Ở phép gán kết hợp, Java cho phép phép toán và chuyển kết quả về kiểu của biến nhận như một phần của ngữ nghĩa toán tử.

| Nhóm | Các toán tử | Ví dụ |
| --- | --- | --- |
| Số học | `+=`, `-=`, `*=`, `/=`, `%=` | `x += 5`, `x *= 2` |
| Bitwise | `&=`, `\|=`, `^=` | `mask &= 0xFF`, `flags ^= 0b10` |
| Dịch bit | `<<=`, `>>=`, `>>>=` | `bits <<= 1`, `bits >>>= 2` |

Các toán tử `&=`, `|=`, `^=` cũng hỗ trợ toán hạng kiểu `boolean` theo quy tắc boolean, chẳng hạn `boolean ok = true; ok &= false;`. Java **không có** toán tử `&&=` hoặc `||=`. Riêng `String +=` là trường hợp phép **nối chuỗi**, không theo quy tắc thu hẹp kiểu số.

## 2. Quy tắc gần tương đương của Java: phép toán rồi ép về kiểu vế trái

Theo ngữ nghĩa Java, đối với phép gán kết hợp hợp lệ:

```text
E1 op= E2
```

gần tương đương với:

```text
E1 = (T) (E1 op E2)
```

trong đó `T` là **kiểu của `E1`**. Khác biệt quan trọng là **biểu thức vế trái `E1` chỉ được đánh giá một lần** trong toán tử gán kết hợp. Công thức chỉ là mô hình để hiểu kiểu và kết quả; **không nên thay máy móc** mọi `op=` bằng đoạn mã trên nếu vế trái có tác dụng phụ.

Với các giá trị nguyên, luồng chuyển kiểu thường diễn ra như sau:

1. Đánh giá vị trí/vế trái và lưu giá trị ban đầu.
2. Đánh giá biểu thức vế phải.
3. Thực hiện phép toán theo **binary numeric promotion** hoặc quy tắc kiểu riêng của toán tử. `byte`, `short`, `char` khi tham gia cộng/trừ/nhân thường được nâng lên `int`; nếu có `long`, `float`, `double`, kiểu phép toán có thể rộng hơn.
4. Chuyển kết quả về **kiểu vế trái** bằng phép chuyển phù hợp, bao gồm thu hẹp số nguyên/float khi cần.
5. Lưu giá trị đã chuyển trở lại biến, phần tử mảng hoặc trường.

Ví dụ `byte b = 10; b += 20;` tính `int` 30 rồi thu hẹp thành `byte` 30. Với `byte b = 127; b += 1;`, giá trị tính toán `int` là 128 nhưng chuyển xuống `byte` cho **-128**.

## 3. Vì sao `byte += int` hợp lệ nhưng `byte = byte + int` có thể không?

Đối với toán tử cộng nhị phân, cả hai toán hạng `byte` và `short` thường được **nâng lên `int`**. Vì vậy `b + 1` có kiểu `int`, bất kể `b` được khai báo là `byte`.

```java
byte b = 10;
int result = b + 1; // Hợp lệ: int 11.
// b = b + 1;        // LỖI: không được tự chuyển int động sang byte.
b += 1;             // Hợp lệ: kết quả phép cộng tự thu hẹp thành byte.
```

Ở dòng gán thường, trình biên dịch không thể đảm bảo một biểu thức `int` động luôn nằm trong -128..127. Gán kết hợp là một quy tắc riêng **có phép thu hẹp ngầm định**, chứ không làm `b + 1` đổi kiểu thành `byte`.

Cần phân biệt với **thu hẹp biểu thức hằng số lúc gán**:

```java
byte a = 1 + 2;    // Hợp lệ: biểu thức hằng int có giá trị 3 trong phạm vi byte.
byte b = 10;
// byte c = b + 1; // LỖI: b + 1 không phải biểu thức hằng compile-time.
b += 1;            // Hợp lệ theo quy tắc compound assignment.
```

Do đó, việc `byte a = 1 + 2` được cho phép **không chứng minh** phép gán thường `b = b + 1` là hợp lệ. Đó là **hai ngoại lệ khác nhau** trong hệ thống kiểu của Java.

## 4. Mất dữ liệu và quay vòng khi thu hẹp sang `byte`, `short`, `char`

Khi chuyển một giá trị nguyên sang kiểu nguyên **hẹp hơn**, Java bỏ các bit cao vượt độ rộng đích và diễn giải phần còn lại theo kiểu đích:

| Trường hợp | Kết quả phép tính trước khi thu hẹp | Kết quả lưu |
| --- | ---: | --- |
| `byte b = 127; b += 1;` | `int` 128 | `byte` -128 |
| `short s = 32767; s += 1;` | `int` 32768 | `short` -32768 |
| `byte b = 100; b *= 2;` | `int` 200 | `byte` -56 |
| `byte b = 1; b += 200L;` | `long` 201 | `byte` -55 |
| `char c = 'A'; c += 1;` | `int` 66 | `char` 'B' |
| `char c = Character.MAX_VALUE; c += 1;` | `int` 65536 | `char` có mã 0 |

`byte` có 8 bit có dấu (-128..127), `short` có 16 bit có dấu, còn `char` là 16 bit **không dấu** (0..65535), biểu diễn một **đơn vị mã UTF-16**. Vì vậy `char` không trở thành số âm khi vượt 65535: sau khi mất các bit cao, nó quay về code unit `0`.

Đối với các toán tử thường dùng, việc thực hiện phép toán trước rồi chuyển hẹp là điều khiến `+=`, `*=`, `-=` có thể làm mất dữ liệu mà không báo lỗi. **Compiler chấp nhận cú pháp** không đồng nghĩa với **kết quả toán học được bảo toàn**.

Ví dụ, dùng `byte` để đếm số lượng và tăng liên tục bằng `+=` sẽ quay vòng sau 127; đây thường là lỗi nghiệp vụ. Chọn `int` hoặc `long` khi cần phạm vi rộng hơn, hoặc chủ động kiểm tra biên.

## 5. Vế phải rộng hơn vẫn có thể được chấp nhận

Java không yêu cầu vế phải của phép gán kết hợp phải có kiểu nhỏ hơn hoặc bằng vế trái. Chẳng hạn:

```java
byte small = 1;
small += 200L;     // Hợp lệ: tính bằng long, sau đó thu hẹp về byte (-55).

int n = 7;
n += 2.9;          // Hợp lệ: tính bằng double 9.9, chuyển về int 9.

long length = 4L;
length += 0.9d;    // Hợp lệ: double 4.9, chuyển về long 4.
```

Ngược lại, phép gán thường không cho phép tự chuyển một giá trị `double` hoặc `long` động thành `int`, `byte` hoặc `long` hẹp hơn một cách tùy ý.

Với chuyển `double`/`float` sang số nguyên, Java **không làm tròn đến số nguyên gần nhất**. Phần thập phân bị loại bỏ theo hướng về 0 khi giá trị hữu hạn nằm trong phạm vi. Với giá trị ngoài phạm vi đích trung gian hoặc `NaN`, còn có các quy tắc chuyển float-to-integer riêng (như bão hòa về cực trị hoặc `NaN` thành 0); nếu đang cần tính số lượng chính xác, **không nên dựa vào `+=` để âm thầm ép số thực về số nguyên**.

Ví dụ `n += 2.9` cho `9`, khác với làm tròn `7 + 2.9 = 9.9` lên 10. Nếu muốn làm tròn, hãy áp dụng quy tắc nghiệp vụ tường minh trước khi gán, chẳng hạn dùng `Math.round` khi phù hợp.

## 6. Phép tính tràn trước khi chuyển hẹp vẫn có thể xảy ra

Phép gán kết hợp có **hai nguồn mất dữ liệu khác nhau**:

- **Phép toán ban đầu có thể đã tràn trong kiểu thực thi**, chẳng hạn phép cộng `int` hoặc `long`.
- **Kết quả hợp lệ của phép toán có thể bị thu hẹp** khi lưu vào biến nhỏ hơn.

```java
int max = Integer.MAX_VALUE;
max += 1;        // -2147483648: phép cộng int quay vòng.

byte b = 100;
b *= 2;          // -56: phép nhân int được 200, thu hẹp thành byte.

long total = 2_000_000_000L;
total += 1_000_000_000; // 3000000000: phép cộng diễn ra ở long.
```

Ở trường hợp `max += 1`, kiểu vế trái là `int`, phép cộng cũng là `int`; giá trị đã quay vòng ở phép toán. Ở `b *= 2`, phép nhân `int` được đúng 200, **sau đó** giá trị bị thay đổi khi chuyển sang `byte`. Hai tình huống có cùng kết quả “ngoài mong đợi” nhưng nguyên nhân khác nhau.

Nếu bắt buộc không được tràn, `Math.addExact`, `Math.multiplyExact`, `Math.toIntExact` hỗ trợ kiểm tra phạm vi cho `int`/`long`. Không có hàm `Math.toByteExact` trong Java 17; với `byte`, có thể tính ở `int` bằng `Math.addExact` rồi kiểm tra `Byte.MIN_VALUE <= result <= Byte.MAX_VALUE` trước khi ép.

```java
byte b = 120;
int computed = Math.addExact((int) b, 10);
if (computed < Byte.MIN_VALUE || computed > Byte.MAX_VALUE) {
    throw new ArithmeticException("byte out of range");
}
b = (byte) computed;
```

Đây là mảnh mã trong phương thức. Thay `b += 10` bằng hướng tiếp cận này khi cần **từ chối dữ liệu ngoài phạm vi**, chứ không phải quấn vòng tự động.

## 7. Vế trái được đánh giá đúng một lần: khác biệt không chỉ là ép kiểu

Hãy xét phép gán kết hợp với truy cập mảng và toán tử tăng hậu tố:

```java
int[] values = {10, 20};
int i = 0;
values[i++] += i;
// values[0] == 11, i == 1, values[1] == 20.
```

Java đánh giá `values[i++]` để tìm phần tử vế trái **một lần**: đọc chỉ số cũ 0 và làm `i` thành 1; sau đó đánh giá vế phải `i` là 1. Giá trị cũ của `values[0]` là 10, nên kết quả 11 được lưu trở lại **đúng phần tử 0**.

Không được viết lại thành:

```java
// KHÔNG tương đương khi vế trái có tác dụng phụ:
values[i++] = (int) (values[i++] + i);
```

Trong biểu thức viết lại, `i++` có thể bị đánh giá **hai lần**, gây thay đổi chỉ số và cả phần tử được cập nhật. Quy tắc vế trái đánh giá một lần áp dụng cả trường đối tượng, truy cập mảng, biểu thức có lời gọi phương thức và những phép tính tạo ra địa chỉ/vị trí lưu.

Ví dụ riêng:

```java
int[] arr = {10};
arr[locate()] += 2; // locate() chỉ được gọi một lần.
```

Ở đây giả sử `locate()` là một phương thức trả 0 và tăng bộ đếm mỗi lần được gọi. Chương trình hoàn chỉnh bên dưới có minh họa runnable cho trường hợp này.

## 8. Phép tăng `++` và giảm `--` cũng đáng lưu ý

Dù cú pháp khác `+=`/`-=`, `++` và `--` trên biến kiểu `byte`, `short`, `char` cũng có thể cập nhật biến về đúng kiểu nhỏ mà không cần ghi ép kiểu rõ ràng:

```java
byte b = 127;
b++;            // b == -128.

char c = 'A';
c++;            // c == 'B'.
```

Tuy nhiên `b++` và `++b` **khác nhau ở giá trị mà biểu thức trả về** (trước tăng hoặc sau tăng). Chúng không phải cú pháp hoán đổi vô điều kiện với `b += 1`, nhất là khi giá trị của toàn biểu thức được sử dụng. Khi cần hiểu ngữ nghĩa kiểu, hãy phân biệt **phép cập nhật biến**, **giá trị trả về của biểu thức** và **thời điểm tác dụng phụ xảy ra**.

## 9. Chương trình Java 17+ biên dịch và chạy độc lập

Lưu tệp **`CompoundAssignmentDemo.java`**:

```java
public class CompoundAssignmentDemo {
    static int evaluations = 0;

    static int locate() {
        evaluations++;
        return 0;
    }

    public static void main(String[] args) {
        byte b = 127;
        b += 1;
        System.out.println("byteWrap=" + b);

        short s = 32_767;
        s += 1;
        System.out.println("shortWrap=" + s);

        char letter = 'A';
        letter += 1;
        System.out.println("nextChar=" + letter);

        char maxChar = Character.MAX_VALUE;
        maxChar += 1;
        System.out.println("charWrapCode=" + (int) maxChar);

        byte product = 100;
        product *= 2;
        System.out.println("byteProduct=" + product);

        byte mixed = 1;
        mixed += 200L;
        System.out.println("byteWithLong=" + mixed);

        int truncated = 7;
        truncated += 2.9;
        System.out.println("doubleToInt=" + truncated);

        long longValue = 4L;
        longValue += 0.9d;
        System.out.println("doubleToLong=" + longValue);

        int bitMask = 0b1010;
        bitMask &= 0b1100;
        System.out.println("bitAnd=" + bitMask);

        String label = "A";
        label += 5;
        System.out.println("stringPlus=" + label);

        Integer boxed = 127;
        boxed += 1;
        System.out.println("boxed=" + boxed);

        int[] values = {10, 20};
        int i = 0;
        values[i++] += i;
        System.out.println("sideEffect=" + values[0] + "," + i + "," + values[1]);

        int[] once = {10};
        once[locate()] += 2;
        System.out.println("lhsOnce=" + once[0] + "," + evaluations);

        int nearMax = Integer.MAX_VALUE;
        nearMax += 1;
        System.out.println("intOverflow=" + nearMax);
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac --release 17 -encoding UTF-8 CompoundAssignmentDemo.java
java CompoundAssignmentDemo
```

Kết quả stdout chính xác:

```text
byteWrap=-128
shortWrap=-32768
nextChar=B
charWrapCode=0
byteProduct=-56
byteWithLong=-55
doubleToInt=9
doubleToLong=4
bitAnd=8
stringPlus=A5
boxed=128
sideEffect=11,1,20
lhsOnce=12,1
intOverflow=-2147483648
```

Trong đó:

- `byteWrap`, `shortWrap`, `byteProduct`, `byteWithLong` cho thấy kết quả bị thu hẹp về kiểu đích và có thể quay vòng.
- `nextChar` và `charWrapCode` minh họa `char` là kiểu nguyên không dấu 16 bit, không tự “mở rộng” khi dùng `+=`.
- `doubleToInt`, `doubleToLong` cho thấy gán kết hợp có thể thu hẹp từ số thực về số nguyên và bỏ phần thập phân.
- `bitAnd` là gán kết hợp cho phép toán bitwise; `stringPlus` minh họa ngoại lệ nối chuỗi.
- `boxed` cho thấy wrapper `Integer` cũng có thể dùng `+=` thông qua unboxing/boxing; đó **không phải** phép toán nguyên tử trên một đối tượng số bất biến.
- `sideEffect`, `lhsOnce` chứng minh vế trái của phép gán kết hợp chỉ được đánh giá một lần.
- `intOverflow` cho thấy `int` có thể tràn khi dùng `+=` như khi cộng thông thường.

## 10. Các toán tử bitwise và shift có thu hẹp ngầm định

Quy tắc gán kết hợp không chỉ áp dụng cho cộng/trừ/nhân. Ví dụ:

```java
byte bits = 0b0000_1111;
bits <<= 1;        // Giá trị được dịch ở kiểu int rồi thu hẹp về byte.
bits &= 0b0001_1110; // Mask tiếp tục cập nhật byte.

int mask = 0b1010;
mask &= 0b1100;    // 0b1000, tức 8.
```

Với `byte` hoặc `short`, phép dịch bit thường sử dụng `int` sau khi nâng kiểu toán hạng trái; kết quả mới được chuyển về kiểu biến ban đầu. Đừng nhầm việc `bits <<= 1` biên dịch với việc bit bị đẩy ra ngoài độ rộng nhỏ sẽ được lưu lại.

Trong các phép dịch, `>>` là dịch phải giữ dấu, `>>>` là dịch phải điền 0; khi kết quả được thu hẹp xuống `byte`, `short` hoặc `char`, các bit cao lại có thể bị loại bỏ. Khi viết mã mask/bit protocol, cần kiểm tra chính xác **kiểu toán hạng sau promotion** và độ rộng muốn giữ.

Các toán tử boolean `&=`, `|=`, `^=` làm việc với giá trị logic, **không có câu chuyện thu hẹp kiểu số**. Và cũng không có `boolean += ...` trong Java.

## 11. Wrapper types, `null`, biến `final` và đa luồng

**Wrapper:** `Integer`, `Byte`, `Short`, `Long`, `Character` là các lớp đóng gói giá trị nguyên thủy. Với kiểu wrapper thích hợp, `+=` có thể **unbox**, thực hiện phép số học, rồi **box** kết quả:

```java
Integer score = 127;
score += 1; // score mang giá trị 128 dưới dạng Integer mới hoặc boxed value.
```

Nếu wrapper bằng `null`, phép tính cần unbox sẽ ném `NullPointerException` tại thời điểm chạy. Không coi `Integer count; count += 1;` là cách tăng an toàn nếu giá trị chưa được khởi tạo hợp lệ. Wrapper bất biến; phép `+=` là cập nhật **biến tham chiếu**, không sửa đối tượng cũ.

**`final`:** gán kết hợp sửa biến nhận, nên không thể dùng cho biến `final` sau khi đã được gán:

```java
final int limit = 10;
// limit += 1; // LỖI biên dịch: không thể gán lại biến final.
```

**Đa luồng:** `counter += 1` gồm đọc giá trị cũ, tính, rồi ghi lại; thao tác này **không nguyên tử chỉ vì dùng một toán tử**. Ngay cả biến `volatile int counter` cũng không làm `counter += 1` tự trở thành atomic read-modify-write. Nếu nhiều luồng cùng cập nhật bộ đếm, cân nhắc `AtomicInteger.incrementAndGet()`, `AtomicLong.addAndGet(...)` hoặc đồng bộ hóa phù hợp, tùy yêu cầu nhất quán.

## 12. Phân biệt gán kết hợp, gán thường và cast tường minh

| Cách viết | Với `byte b = 10` | Kết quả |
| --- | --- | --- |
| `b += 1;` | Hợp lệ | Tính `int` 11, thu hẹp thành `byte` 11 |
| `b = b + 1;` | Không hợp lệ | Biểu thức `b + 1` có kiểu `int`, cần chuyển hẹp |
| `b = (byte) (b + 1);` | Hợp lệ | Diễn đạt tường minh phép chuyển hẹp, giá trị 11 |
| `b += 200;` | Hợp lệ | Tính `int` 210, thu hẹp `byte` thành -46 |
| `b = (byte) 210;` | Hợp lệ (có cast) | Giá trị `byte` -46 |
| `int n = b + 1;` | Hợp lệ | Giá trị ở `int`, không bị thu hẹp về `byte` |

Sự giống nhau giữa `b += 1` và `b = (byte) (b + 1)` ở đây chỉ áp dụng khi **vế trái là biến đơn giản không có tác dụng phụ**. Đối với `a[i++]`, lời gọi phương thức, truy cập trường hoặc biểu thức phức tạp, không được quên nguyên tắc **đánh giá vế trái đúng một lần**.

## 13. Lỗi thường gặp và cách sửa

| Lỗi/nhầm lẫn | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| Cho rằng `b += 1` giữ kết quả ở `int` | Bỏ qua chuyển hẹp implicit ở bước lưu | Xác định kiểu của biến vế trái |
| `byte b = 127; b += 1;` bất ngờ âm | Thu hẹp mẫu bit 128 về `byte` | Dùng `int` hoặc kiểm tra phạm vi trước |
| `short s = 32767; s += 1;` thành âm | Vượt biên `short` | Dùng `int` khi đếm vượt 32767 |
| `int x = 7; x += 2.9;` thành 9 | `double` bị chuyển sang `int`, bỏ phần lẻ | Làm tròn/chuyển kiểu có chủ đích |
| `byte b = 1; b += 200L;` thành -55 | `long` bị thu hẹp về `byte` | Chọn biến đủ rộng, không dựa vào cast ngầm |
| Đổi `a[i++] += 1` thành `a[i++] = a[i++] + 1` | Đánh giá vế trái nhiều lần | Giữ `+=` hoặc lưu index vào biến tạm |
| Nghĩ `Integer x += 1` là atomic | Unbox, toán học, box, cập nhật tham chiếu | Dùng API đồng bộ/atomic phù hợp |
| Dùng `+=` lên `final` | Toán tử sửa giá trị vế trái | Không dùng phép gán sau khi final đã gán |
| Nhầm `String +=` với số học | Với `String`, `+=` nối văn bản | Phân biệt ngữ cảnh kiểu |
| Cho rằng `+=` tự phát hiện overflow | Số nguyên Java thông thường có thể quay vòng | Dùng `Math.*Exact` và kiểm tra phạm vi đích |

## 14. Câu hỏi tự kiểm tra

1. **Vì sao `byte b = 5; b += 1;` hợp lệ, nhưng `b = b + 1;` không hợp lệ?** Vì `b + 1` có kiểu `int`; compound assignment có phép thu hẹp ngầm định về `byte`, còn gán thường không tự thu hẹp một biểu thức động như vậy.
2. **`byte b = 127; b += 1;` cho kết quả nào?** `-128`, do `int` 128 thu hẹp còn 8 bit có dấu.
3. **`short s = 32767; s += 1;` bằng gì?** `-32768`.
4. **`int n = 7; n += 2.9;` bằng gì?** `9`, do kết quả số thực 9.9 được chuyển về `int`, bỏ phần thập phân.
5. **`char c = 'A'; c += 1;` cho ký tự nào?** `'B'` (mã UTF-16 66).
6. **`int[] a = {10,20}; int i = 0; a[i++] += i;` cho mảng nào?** `a[0] == 11`, `a[1] == 20`, `i == 1`; vế trái `a[i++]` được đánh giá một lần.
7. **Có thể dùng `+=` để cộng an toàn không tràn không?** Không mặc định. Dùng kiểu đủ rộng, `Math.addExact`, và kiểm tra dải đích nếu cần thu hẹp.
8. **`String s = "A"; s += 5;` có thực hiện cộng số không?** Không, kết quả chuỗi là `"A5"`.
9. **`volatile int count; count += 1;` có nguyên tử với nhiều luồng không?** Không; cần đồng bộ hóa hoặc kiểu atomic nếu yêu cầu.

**Ghi nhớ:** `E1 op= E2` thực hiện phép toán theo quy tắc promotion, **chuyển kết quả về kiểu của vế trái**, và **chỉ đánh giá vế trái một lần**. Chính sự thu hẹp ngầm định này làm các câu lệnh `byte += int` hoặc `int += double` biên dịch được, nhưng cũng có thể âm thầm gây tràn, cắt bit hoặc mất phần thập phân. Viết ngắn không thay thế việc kiểm tra đúng kiểu và đúng phạm vi dữ liệu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-promotion-006">Kiểu của phép tăng giảm khác phép cộng rồi gán</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểu của phép tăng giảm khác phép cộng rồi gán trong Java

Trong Java, ba cách viết tưởng như tương đương dưới đây **không giống nhau về quy tắc kiểu**:

```java
byte b = 10;
b++;        // Hợp lệ.
b += 1;     // Hợp lệ.
// b = b + 1; // Lỗi biên dịch: b + 1 có kiểu int, không tự gán vào byte.
```

Cả ba đều có ý định tăng giá trị lên 1, nhưng Java áp dụng **binary numeric promotion** cho phép cộng thông thường, trong khi phép tăng `++` và phép gán kết hợp `+=` có **quy tắc chuyển đổi ngược vào kiểu biến**. Nắm được sự khác biệt này giúp giải thích lỗi `possible lossy conversion from int to byte`, tránh mất dữ liệu do thu hẹp kiểu ngầm và hiểu đúng giá trị của biểu thức tiền tố/hậu tố.

Bài học thuộc nhóm **Nâng kiểu biểu thức** của **Java core – numbers**, tập trung vào kiểu của phép tăng/giảm `++`/`--` so với phép cộng rồi gán, cũng như các hệ quả với `byte`, `short`, `char`, `int`, `long` và wrapper.

## 1. Nhìn nhanh: phép tính và phép gán là hai giai đoạn

Xét:

```java
byte b = 10;
int sum = b + 1;    // 11, phép cộng có kiểu int.
```

Dù `b` lưu một `byte` và giá trị 11 nằm trong miền `byte`, **biểu thức `b + 1` có kiểu `int`**. Lý do là quy tắc *binary numeric promotion* của toán tử `+`: với các kiểu `byte`, `short`, `char`, toán hạng thường được nâng thành `int` để thực hiện phép tính.

Do đó:

```java
byte b = 10;
// b = b + 1; // Không biên dịch: biểu thức int không được tự thu hẹp về byte.
b = (byte) (b + 1); // Hợp lệ, có cast tường minh.
```

Trong khi đó:

```java
byte b = 10;
b++;     // Hợp lệ, cập nhật byte.
++b;     // Hợp lệ, cập nhật byte.
b += 1;  // Hợp lệ, cập nhật byte.
```

Bảng tóm tắt:

| Cú pháp | Kiểu phép toán trung gian | Kiểu của biểu thức | Ghi giá trị về biến kiểu hẹp? |
| --- | --- | --- | --- |
| `b + 1` với `b` là `byte` | `int` | `int` | Chưa gán |
| `b = b + 1` | `int` | Biểu thức phép gán có kiểu `byte` **nếu hợp lệ** | Không biên dịch nếu không cast |
| `b = (byte)(b + 1)` | `int`, sau đó ép về `byte` | `byte` | Có, cast tường minh |
| `b += 1` | Tính tổng theo quy tắc số học rồi chuyển về `byte` | `byte` | Có, chuyển kiểu ngầm theo compound assignment |
| `b++` | Tăng giá trị và chuyển về kiểu của biến | `byte` | Có, hậu tố trả **giá trị cũ** |
| `++b` | Tăng giá trị và chuyển về kiểu của biến | `byte` | Có, tiền tố trả **giá trị mới** |

Ghi chú về bảng: phép `b = b + 1` là mã không hợp lệ với biến `byte` thông thường; ô “kiểu của biểu thức phép gán nếu hợp lệ” chỉ mô tả quy tắc chung của toán tử gán, không có nghĩa Java cho phép phép gán ấy. Cũng cần phân biệt **kiểu của biểu thức `++b`** là `byte` với **kiểu số học trung gian** có thể được nâng lên trước khi chuyển trở lại `byte`.

## 2. Vì sao `byte + 1` có kiểu int?

Java định nghĩa nhóm phép toán số học nhị phân gồm `+`, `-`, `*`, `/`, `%`. Khi chúng áp dụng lên các toán hạng nguyên thủy, Java thực hiện *binary numeric promotion*:

1. Nếu một toán hạng là `double`, phép toán theo `double`.
2. Nếu không, nhưng có một toán hạng là `float`, phép toán theo `float`.
3. Nếu không, nhưng có một toán hạng là `long`, phép toán theo `long`.
4. Nếu không, phép toán theo **`int`**, kể cả khi cả hai toán hạng ban đầu là `byte`, `short` hoặc `char`.

Ví dụ:

```java
byte a = 10;
byte b = 20;
int sum = a + b;       // Hợp lệ.
// byte wrong = a + b; // Không biên dịch.

short s = 1;
int nextShort = s + 1; // Kiểu biểu thức: int.

char ch = 'A';
int nextCode = ch + 1; // 66, kiểu int.

long l = 5L;
long nextLong = l + 1; // Kiểu long do có một toán hạng long.
```

Lỗi `byte wrong = a + b` là lỗi **kiểu tĩnh tại lúc biên dịch**. Không có một phép “thử cộng trước rồi xem kết quả có vừa byte không” đối với biểu thức có biến thông thường. Ngay cả khi tại thời điểm chạy `a=10` và `b=20`, biểu thức vẫn được Java định kiểu là `int`.

### Ngoại lệ cần biết: hằng số compile-time

Không được phát biểu quá rộng rằng “không thể gán bất kỳ biểu thức cộng có kiểu int nào cho byte”. Java có quy tắc **thu hẹp hằng số khi khởi tạo hoặc gán**, nếu biểu thức là *constant expression* và giá trị nằm trong miền kiểu đích:

```java
byte a = 10 + 20; // Hợp lệ: hằng số 30, vừa byte.

final byte x = 10;
byte y = x + 20;  // Hợp lệ: x là constant variable, tổng 30 là constant expression.
```

Nhưng:

```java
byte a = 10;
// a = a + 20;    // Không biên dịch: a không phải constant variable.
```

Nếu một hằng số nằm ngoài miền, vẫn bị từ chối:

```java
// byte invalid = 100 + 100; // Không biên dịch: 200 vượt Byte.MAX_VALUE.
```

Quy tắc hằng số này khác với phép cập nhật giá trị của **một biến có thể thay đổi**. `final byte x` không thể dùng `x++` hoặc `x += 1`.

## 3. Cơ chế của ++ và --: tăng giảm và lưu ngược kiểu biến

Hai toán tử `++` và `--` tác động trực tiếp lên **biến** có kiểu số hợp lệ. Chúng cập nhật giá trị biến thêm hoặc bớt 1, thực hiện chuyển đổi cần thiết để lưu trở lại đúng kiểu biến.

```java
byte b = 10;
b++; // b = 11, vẫn là byte.

short s = 10;
--s; // s = 9, vẫn là short.

char c = 'A';
++c; // c là 'B', vẫn là char.
```

Với các kiểu nhỏ như `byte`, `short` và `char`, phép tính trung gian được nâng kiểu theo quy tắc số học phù hợp, rồi giá trị được chuyển về kiểu của biến. Đây là nguyên nhân `b++` hợp lệ dù `b = b + 1` không hợp lệ.

**Kiểu của cả biểu thức tiền tố và hậu tố là kiểu của biến**. Vì vậy, nếu `b` có kiểu `byte`, thì `++b` và `b++` có kiểu `byte`. Khi biểu thức ấy tiếp tục tham gia một phép toán nhị phân khác, nó **lại có thể được nâng lên `int`** ở bước tiếp theo.

Ví dụ:

```java
byte b = 10;
byte afterIncrement = ++b; // Hợp lệ: ++b có kiểu byte.
int computed = b++ + 1;    // Phép cộng ngoài cùng có kiểu int.
// byte other = b++ + 1;   // Không biên dịch nếu không cast.
```

Ở đây, cú pháp `++` không “tắt mọi quy tắc nâng kiểu”. Nó chỉ có quy tắc **riêng cho biểu thức tăng/giảm và phép cập nhật biến**.

### Biến nào có thể được tăng hoặc giảm?

Toán hạng của `++`/`--` phải là một biến kiểu số có thể ghi được. Không thể áp dụng cho literal, kết quả phép cộng, biến `final`, hoặc `boolean`:

```java
int i = 10;
i++; // Hợp lệ.

// 10++;      // Sai: literal không phải biến.
// (i + 1)++; // Sai: kết quả cộng không phải biến.
// boolean flag = false; flag++; // Sai: boolean không phải kiểu số.

final int fixed = 5;
// fixed++;   // Sai: không được cập nhật biến final.
```

Một phần tử mảng, field của object hoặc biến cục bộ có thể dùng `++` nếu thỏa điều kiện là biến có thể gán và kiểu phù hợp.

## 4. Tiền tố và hậu tố khác giá trị biểu thức, không khác kiểu biến

Cả `++x` và `x++` đều làm biến `x` tăng 1. Điểm khác nhau là **giá trị biểu thức**:

- `++x` (*prefix*) cập nhật trước rồi cho giá trị mới.
- `x++` (*postfix*) cho giá trị cũ, sau đó biến vẫn được cập nhật.
- Với `x` kiểu `byte`, **cả hai biểu thức có kiểu `byte`**, không phải `int` chỉ vì đang thực hiện cộng thêm 1.

```java
byte a = 10;
byte oldValue = a++; // oldValue = 10, a = 11.

byte b = 10;
byte newValue = ++b; // newValue = 11, b = 11.
```

Hậu tố **không có nghĩa phép tăng bị hoãn đến dòng tiếp theo**: sau khi biểu thức hậu tố đã được đánh giá, biến đã được cập nhật. Chỉ có **giá trị biểu thức được trả ra** là giá trị cũ.

Ví dụ:

```java
int n = 5;
int result = n++ + 10;
// result = 15 vì n++ tạo giá trị 5.
// Sau biểu thức, n = 6.
```

Ngược lại:

```java
int n = 5;
int result = ++n + 10;
// result = 16; n = 6.
```

Trong ứng dụng thực tế, tránh viết biểu thức lồng nhiều `++`/`--` khi không cần: nó làm người đọc khó theo dõi thứ tự đánh giá dù Java quy định thứ tự cụ thể. Một câu lệnh tăng riêng thường rõ ràng hơn.

## 5. Phép gán kết hợp += khác phép cộng rồi gán

Cú pháp:

```java
byte b = 10;
b += 1;
```

có thể hiểu gần với:

```java
b = (byte) (b + 1);
```

Quy tắc chung: biểu thức `E1 op= E2` tương đương về ý nghĩa kiểu với `E1 = (T)((E1) op (E2))`, trong đó `T` là kiểu của `E1`. **Khác biệt quan trọng:** toán hạng trái `E1` chỉ được đánh giá **một lần** trong phép gán kết hợp. Quy tắc này áp dụng cho nhiều toán tử gán kết hợp, không riêng `+=`.

Với `byte`:

```java
byte b = 10;
b += 1;     // Hợp lệ: 11.
b = (byte) (b + 1); // Hợp lệ: 12.
// b = b + 1;       // Không biên dịch.
```

Đối với giá trị và biến đơn giản, `b++` và `b += 1` thường cùng tăng 1 và lưu về kiểu `byte`. Nhưng khi đặt bên trong biểu thức khác, **`b++` trả giá trị cũ**, còn **`b += 1` trả giá trị đã cập nhật**, tương tự `++b` ở khía cạnh này:

```java
int x = 5;
int fromPostfix = x++; // fromPostfix=5, x=6

int y = 5;
int fromCompound = (y += 1); // fromCompound=6, y=6

int z = 5;
int fromPrefix = ++z; // fromPrefix=6, z=6
```

### Compound assignment có thể thu hẹp kiểu ngầm

```java
byte b = 1;
b += 1000; // Hợp lệ về cú pháp, nhưng mất thông tin: kết quả -23.
```

Ở đây `1 + 1000 = 1001`; khi chuyển về `byte` 8 bit, giữ 8 bit thấp: `1001 mod 256 = 233`; diễn giải có dấu của byte là `233 - 256 = -23`. Không có `ArithmeticException` tự động.

Điểm nguy hiểm là **compiler chấp nhận cú pháp, không bảo đảm kết quả nằm trong miền nghiệp vụ**. Ngay cả các phép gán kết hợp với kiểu toán hạng phải mở rộng hơn như `b += 1L` cũng có thể làm phép tính rồi thu hẹp về `byte`. Hãy dùng kiểu đủ rộng và kiểm tra biên nếu không được mất thông tin.

## 6. Tràn kiểu nhỏ với ++ và --

Vì kết quả cuối phải lưu lại vào kiểu biến, việc tăng giảm tại ranh giới có thể **quay vòng**:

```java
byte b = Byte.MAX_VALUE;  // 127
b++;                      // -128

short s = Short.MIN_VALUE; // -32768
s--;                      // 32767

char c = Character.MAX_VALUE; // '\uFFFF'
c++;                          // '\u0000'
```

Với `char`, cần nhớ đây là **đơn vị mã UTF-16 16 bit không dấu** trong Java, có phạm vi `0..65535`. Có thể tăng/giảm `char` theo số học nhưng **không nên sử dụng cách đó như một phương pháp duyệt Unicode code point**: ký tự ngoài BMP dùng surrogate pair, và tăng từng `char` không tương đương tăng từng ký tự Unicode hoàn chỉnh.

Một biểu thức như:

```java
byte b = 127;
int sum = b + 1; // 128, không tràn ở phép cộng int.
b++;             // b trở thành -128 sau khi chuyển về byte.
```

cho thấy rõ sự khác biệt giữa **giá trị int từ phép cộng** và **giá trị đã gán lại vào byte**.

Khi giá trị 128 có ý nghĩa hợp lệ trong bài toán, hãy **đổi kiểu lưu trữ sang `int`** thay vì cố tăng biến `byte`.

## 7. Với int và long: khác biệt cú pháp vẫn tồn tại, nhưng kiểu cộng thường đủ lớn hơn

Với `int`:

```java
int n = 10;
n = n + 1; // Hợp lệ, vế phải kiểu int.
n += 1;    // Hợp lệ.
n++;       // Hợp lệ.
++n;       // Hợp lệ.
```

Với `long`, dùng `long` trong biểu thức số học có `long`:

```java
long n = 10L;
n = n + 1; // Hợp lệ: long + int được nâng thành long.
n += 1;
n++;
```

Nhưng **`int`/`long` vẫn có thể tràn**:

```java
int n = Integer.MAX_VALUE;
n++; // Integer.MIN_VALUE, không tự ném exception.
```

Sự khác biệt kiểu dễ thấy nhất với `byte`, `short`, `char` vì kết quả `+` là `int`. Với `int` hoặc `long`, cả ba cách cập nhật thường cùng hợp lệ, nhưng cách nhận **giá trị cũ/mới** và cách kiểm tra tràn vẫn khác nhau.

Nếu cần phát hiện tràn `int` hoặc `long` thay vì cho phép quay vòng, sử dụng các phương thức checked:

```java
int next = Math.incrementExact(n); // Ném ArithmeticException nếu n == Integer.MAX_VALUE.
int previous = Math.decrementExact(n); // Kiểm tra tràn khi giảm.
```

Phải **gán lại** kết quả nếu muốn cập nhật biến: `n = Math.incrementExact(n)`. Khác `n++`, lời gọi `Math.incrementExact(n)` riêng lẻ không sửa `n`.

## 8. Có thể dùng Math.incrementExact cho byte hay short không?

`Math.incrementExact` có overload cho `int` và `long`, không có overload trả `byte` hoặc `short`. Nếu gọi:

```java
byte b = 127;
int result = Math.incrementExact(b); // Trả 128, KHÔNG ném exception.
```

Java nâng `b` lên `int` trước khi gọi phương thức. Giá trị 128 **vẫn nằm trong miền `int`**, nên `Math.incrementExact` không coi đây là tràn. Nó không biết bạn định gán lại kết quả vào `byte`.

Muốn tăng `byte` **có kiểm tra đúng miền byte**, có thể viết:

```java
static byte incrementByteExact(byte value) {
    int next = Math.incrementExact((int) value);
    if (next > Byte.MAX_VALUE) {
        throw new ArithmeticException("byte overflow");
    }
    return (byte) next;
}
```

Nếu là phép giảm, cần kiểm tra `next < Byte.MIN_VALUE` và cân nhắc sử dụng `Math.decrementExact`. Với `short`, thay miền kiểm tra bằng `Short.MIN_VALUE`/`Short.MAX_VALUE`. Với `char`, kiểm tra trong `0..Character.MAX_VALUE`.

Thực tế, nhiều bài toán nên lưu số lượng tăng giảm bằng `int` ngay từ đầu. Sử dụng `byte`/`short` cho bộ đếm chỉ vì giá trị lúc đầu nhỏ thường làm code khó hiểu và dễ phát sinh lỗi thu hẹp kiểu.

## 9. Wrapper: ++ với Byte, Short, Integer có thể unbox/box

Java cho phép tăng/giảm biến kiểu wrapper số như `Byte`, `Short`, `Integer` hoặc `Long`:

```java
Byte boxed = 10;
boxed++; // Unbox, cộng 1, chuyển kiểu/box lại; boxed giữ giá trị 11.
```

Wrapper là kiểu đối tượng có thể `null`. Khi toán tử cần lấy giá trị số bên trong, Java phải **unbox**. Do đó:

```java
Integer count = null;
// count++; // Ném NullPointerException khi chạy vì unbox null.
```

Không nhầm lẫn lỗi này với lỗi biên số học. `Integer` wrapper không tự biến `++` thành phép tăng checked, và `Byte` wrapper vẫn có thể quay vòng:

```java
Byte b = Byte.MAX_VALUE;
b++; // b là -128 sau khi box lại.
```

Với collection/DTO có wrapper nullable, hãy xác định cách xử lý `null` trước khi tăng và không dùng `++` mù quáng. Nếu giá trị bắt buộc luôn có, primitive thường thể hiện rõ hợp đồng hơn.

## 10. Thứ tự đánh giá và side effect: không lặp tính toán khi thay bằng phép gán

Với biến đơn giản, `b += 1` gần với `b = (byte)(b + 1)`. Nhưng khi vế trái là một biểu thức có side effect, thay thế máy móc có thể khiến nó bị đánh giá **hai lần**:

```java
int[] values = {10, 20, 30};
int i = 0;
values[i++] += 1; // Chỉ tăng i một lần; values[0] thành 11.
```

Nếu đổi một cách ngây thơ thành:

```java
int[] values = {10, 20, 30};
int i = 0;
values[i++] = values[i++] + 1;
```

thì hai lần `i++` làm chỉ số được đánh giá khác nhau: giá trị ở phía đọc có thể đến từ phần tử kế tiếp. Không còn tương đương.

Chính vì vậy, trong đặc tả Java, phép gán kết hợp được mô tả bằng công thức tương đương **kèm điều kiện vế trái chỉ được đánh giá một lần**. Nếu cần refactor một biểu thức phức tạp, hãy tách vế trái hoặc lưu chỉ số/đối tượng vào biến cục bộ rõ ràng.

Ví dụ dễ đọc:

```java
int[] values = {10, 20, 30};
int index = 0;
values[index] += 1;
index++;
```

`++` với phần tử mảng hay field cũng đánh giá địa chỉ/biến đích theo quy tắc ngôn ngữ; không nên tùy tiện thay thế bằng biểu thức có nhiều lần đánh giá cùng một vế trái.

## 11. Chương trình Java 17+ chạy độc lập

Lưu thành **`IncrementTypeDemo.java`**:

```java
public class IncrementTypeDemo {
    static byte incrementByteExact(byte value) {
        int next = Math.incrementExact((int) value);
        if (next > Byte.MAX_VALUE) {
            throw new ArithmeticException("byte overflow");
        }
        return (byte) next;
    }

    public static void main(String[] args) {
        byte b = 10;
        int added = b + 1;
        System.out.println("addition-int=" + added);

        byte post = b++;
        System.out.println("post-old=" + post);
        System.out.println("post-current=" + b);

        byte pre = ++b;
        System.out.println("pre-new=" + pre);
        System.out.println("pre-current=" + b);

        b += 1;
        System.out.println("compound=" + b);

        byte overflow = Byte.MAX_VALUE;
        overflow++;
        System.out.println("byte-overflow=" + overflow);

        short s = Short.MIN_VALUE;
        s--;
        System.out.println("short-underflow=" + s);

        char ch = Character.MAX_VALUE;
        ch++;
        System.out.println("char-wrap-code=" + (int) ch);

        byte x = 1;
        x += 1000;
        System.out.println("compound-narrowing=" + x);

        int n = 5;
        int postfixResult = n++;
        int prefixResult = ++n;
        System.out.println("int-postfix=" + postfixResult);
        System.out.println("int-prefix=" + prefixResult);
        System.out.println("int-current=" + n);

        byte max = Byte.MAX_VALUE;
        System.out.println("int-exact-result=" +
                Math.incrementExact((int) max));
        try {
            incrementByteExact(max);
        } catch (ArithmeticException ex) {
            System.out.println("byte-exact=overflow");
        }

        Byte boxed = Byte.MAX_VALUE;
        boxed++;
        System.out.println("boxed-byte-wrap=" + boxed);
    }
}
```

Biên dịch/chạy bằng JDK 17 hoặc mới hơn:

```bash
javac IncrementTypeDemo.java
java IncrementTypeDemo
```

Stdout chính xác:

```text
addition-int=11
post-old=10
post-current=11
pre-new=12
pre-current=12
compound=13
byte-overflow=-128
short-underflow=32767
char-wrap-code=0
compound-narrowing=-23
int-postfix=5
int-prefix=7
int-current=7
int-exact-result=128
byte-exact=overflow
boxed-byte-wrap=-128
```

**Phân tích quan trọng:**

- `b + 1` tạo biểu thức `int`, nhưng `b++`, `++b`, `b += 1` cập nhật được `byte`.
- `b++` trả 10 rồi cập nhật thành 11; `++b` cập nhật từ 11 lên 12 và trả 12.
- Tăng `Byte.MAX_VALUE` trở thành `Byte.MIN_VALUE`, giảm `Short.MIN_VALUE` thành `Short.MAX_VALUE`; tăng `Character.MAX_VALUE` quay về 0.
- `x += 1000` được phép nhưng chuyển kết quả về `byte` nên trả -23.
- `Math.incrementExact((int) Byte.MAX_VALUE)` trả 128 vì vẫn vừa `int`. Hàm `incrementByteExact` kiểm tra giới hạn `byte` mới thực sự phát hiện lỗi.
- `Byte` wrapper cũng quay vòng và box lại giá trị mới.

## 12. Lỗi thường gặp và cách tránh

| Lỗi/nhầm lẫn | Vì sao xảy ra? | Cách xử lý |
| --- | --- | --- |
| `byte b = 1; b = b + 1;` không biên dịch | `b + 1` là `int`; gán `int → byte` không tự động | Dùng `b++`, `b += 1`, cast có chủ ý, hoặc đổi sang `int` |
| Tưởng `b++` có kiểu `int` | Nhầm kiểu phép tính trung gian với kiểu của biểu thức `++` | `b++` và `++b` có kiểu của biến, ở đây là `byte` |
| Tưởng `b += 1` có kiểm tra tràn | `+=` có chuyển về kiểu đích, có thể mất bit | Kiểm tra miền hoặc dùng kiểu rộng hơn |
| Dùng `Math.incrementExact(b)` để kiểm tra overflow byte | `b` được nâng lên `int`; phép tính 127+1 vẫn vừa int | Kiểm tra riêng miền byte trước khi cast |
| Dùng `x++` khi cần giá trị mới trong biểu thức | Hậu tố trả giá trị cũ | Dùng `++x` hoặc tách câu lệnh tăng và câu lệnh đọc |
| Dùng `++x` khi cần giá trị trước cập nhật | Tiền tố trả giá trị mới | Dùng `x++` hoặc tách biến cũ |
| Refactor `array[i++] += 1` thành `array[i++] = array[i++] + 1` | Vế trái bị đánh giá nhiều lần | Dùng `+=` hoặc lưu chỉ số trước khi cập nhật |
| Tăng một `Byte`/`Integer` có thể null | Unboxing `null` | Kiểm tra null/hợp đồng dữ liệu |
| Viết bộ đếm bằng `byte` chỉ vì giá trị ban đầu nhỏ | Sớm tràn và khó hiểu khi cộng/gán | Thường chọn `int` cho bộ đếm |
| Coi quy tắc constant expression áp dụng cho mọi biến | Chỉ hằng số compile-time phù hợp miền mới được thu hẹp ngầm đặc biệt | Phân biệt literal/`final` constant và biến cập nhật runtime |

## 13. Bài tập tự kiểm tra

**Bài 1 — Dự đoán lỗi biên dịch.** Xác định dòng nào hợp lệ, lý do và kiểu biểu thức:

```java
byte b = 5;
int a = b + 1;
// byte c = b + 1;
byte d = ++b;
byte e = b++;
b += 1;
b = (byte) (b + 1);
```

Giải thích vì sao `b + 1` là `int`, còn `++b`/`b++` là `byte`.

**Bài 2 — Giá trị cũ và mới.** Dự đoán `a`, `b`, `x`:

```java
int x = 10;
int a = x++;
int b = ++x;
```

Đáp án: `a=10`, `b=12`, `x=12`.

**Bài 3 — Tràn sau phép tăng.** Cho `byte b = 127`, tính kết quả của `b++` và `b += 1`. Vì sao không ném ngoại lệ? Viết phiên bản có kiểm tra để fail fast.

**Bài 4 — Kiểu nhỏ và hằng số.** Tại sao `byte b = 1 + 2;` hợp lệ trong khi `byte b = 1; b = b + 2;` không hợp lệ? Hãy thử `final byte x = 5; byte y = x + 1;` và `final byte x = 120; byte y = x + 20;`.

**Bài 5 — char và Unicode.** Giải thích `char c = Character.MAX_VALUE; c++;`. Vì sao không nên dùng tăng `char` để duyệt mọi ký tự Unicode ngoài BMP?

**Bài 6 — Đánh giá vế trái một lần.** So sánh side effect của `array[i++] += 1` với `array[i++] = array[i++] + 1`. Hãy biến đổi code để giữ semantics mà vẫn dễ đọc.

**Bài 7 — Wrapper nullable.** Với `Integer n = null;`, dự đoán `n++`. Thiết kế validation để phân biệt “chưa nhập” và “đang bằng 0”.

**Bài 8 — Kiểm tra byte.** Viết `decrementByteExact(byte value)` phát hiện `Byte.MIN_VALUE`; kiểm thử `-127`, `-128`, `0`, `127`. Đảm bảo ngoại lệ chỉ xảy ra khi phép giảm không vừa byte.

## 14. Ghi nhớ

- **`byte`/`short`/`char` cộng với số nguyên thông thường được nâng lên `int`**. Vì vậy `b = b + 1` với `byte b` thường không biên dịch.
- **`++b`, `b++`, `b += 1` có thể cập nhật biến kiểu hẹp** nhờ quy tắc chuyển kết quả về kiểu biến.
- **Kiểu biểu thức `++b` và `b++` là kiểu của biến**, dù bước tính toán trung gian có thể được nâng kiểu.
- **Hậu tố trả giá trị cũ, tiền tố trả giá trị mới**; cả hai đều cập nhật biến khi được đánh giá.
- **Compound assignment có chuyển kiểu ngầm và chỉ đánh giá vế trái một lần**. Vì vậy nó không phải luôn tương đương với cách viết vế trái hai lần.
- **Hợp lệ về kiểu không có nghĩa an toàn trước tràn**: `byte`, `short`, `char`, `int`, `long` đều có giới hạn. `Math.incrementExact` chỉ kiểm tra miền của overload `int`/`long` được chọn, không tự kiểm tra miền byte.
- Khi bài toán cần giữ kết quả số học thật sự, **dùng kiểu đủ rộng và kiểm tra biên trước khi thu hẹp**; khi yêu cầu là cập nhật biến kiểu nhỏ, hãy nêu rõ chính sách xử lý overflow.

Sự khác biệt nằm ở **quy tắc của từng toán tử và kiểu của biểu thức**: phép cộng bình thường áp dụng nâng kiểu số học, còn tăng/giảm và gán kết hợp có quy tắc riêng cho cập nhật biến. Nắm rõ hai bước “tính theo kiểu nào” và “lưu về kiểu nào” là cách phân tích chính xác mọi ví dụ thuộc nhóm này.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-promotion-007">Toán tử điều kiện ?: và các quy tắc đặc biệt cho số</a>

<details>
<summary>Xem chi tiết</summary>

# Toán tử điều kiện `?:` và các quy tắc đặc biệt cho số trong Java

## 1. `?:` làm gì, và vì sao kiểu số đôi khi gây bất ngờ?

Toán tử điều kiện ba ngôi (*conditional operator*) của Java có cú pháp:

~~~java
condition ? valueIfTrue : valueIfFalse
~~~

Đây là **cú pháp minh họa**, không phải một chương trình Java độc lập. `condition` phải có kiểu `boolean` hoặc `Boolean` (được unbox thành `boolean`). Khi điều kiện là `true`, Java đánh giá toán hạng thứ hai; khi là `false`, Java đánh giá toán hạng thứ ba. **Chỉ nhánh được chọn mới được đánh giá khi chạy**.

Ví dụ:

~~~java
int stock = 12;
int remaining = stock > 0 ? stock : 0;
~~~

Kết quả `remaining` bằng `12`. Với dữ liệu số học, người học thường dự đoán “Java lấy kiểu của nhánh chạy thực tế”. Điều đó **không đúng**: **kiểu của toàn bộ biểu thức `?:` được xác định lúc biên dịch từ kiểu của *cả hai nhánh***, kể cả nhánh không được chọn lúc chạy.

Đặc biệt, `?:` có các **ngoại lệ so với nâng kiểu số thông thường (*binary numeric promotion*)**. Biểu thức `byte b = 10; byte x = flag ? b : 20;` có thể hợp lệ dù một nhánh là literal `int`, bởi `20` là biểu thức hằng `int` nằm trong miền `byte`. Nhưng thay `20` bằng một biến `int` bình thường có thể khiến kiểu kết quả trở thành `int`, không thể gán trực tiếp cho `byte`.

Để dùng đúng `?:` trong phần số học Java, cần phân biệt:

1. **Nhánh nào chạy?** Chọn đúng một nhánh theo `boolean`.
2. **Kiểu toàn biểu thức là gì?** Trình biên dịch quyết định theo quy tắc của `?:`.
3. **Giá trị nhánh được chọn có cần chuyển kiểu?** Có thể có nâng kiểu số, unboxing hoặc chuyển đổi khác theo loại biểu thức.
4. **Kết quả có an toàn không?** Chuyển sang `float`/`double` có thể mất chính xác; unboxing `null` có thể ném `NullPointerException`.

## 2. Phân loại ba dạng biểu thức điều kiện

Đặc tả ngôn ngữ Java, mục `15.25`, phân biệt ba dạng:

| Dạng | Ví dụ | Quy tắc kiểu đáng chú ý |
|---|---|---|
| **Boolean conditional expression** | `flag ? true : false` | Kiểu `boolean` hoặc `Boolean` tùy kiểu hai nhánh |
| **Numeric conditional expression** | `flag ? 1 : 2L` | Tuân theo các quy tắc riêng cho kiểu số; ví dụ cho `long` |
| **Reference conditional expression** | `flag ? "yes" : "no"` | Có thể là *poly expression* chịu ảnh hưởng ngữ cảnh kiểu đích |

Bài học này tập trung vào **numeric conditional expressions**, tức khi nhánh thứ hai và thứ ba thuộc kiểu số nguyên thủy hoặc lớp bao tương ứng và được phân loại là biểu thức số theo đặc tả. Các nhánh như `String` và `null` có thể dẫn đến **reference conditional expression** với quy tắc riêng, nên không áp dụng máy móc bảng quy tắc số ở đây.

Với biểu thức điều kiện kiểu số, Java xác định kiểu dựa vào **cả hai toán hạng của nhánh**. Đây là *standalone expression*: kiểu của nó **không được thay đổi chỉ vì biến bên trái có kiểu mong muốn**. Ví dụ, nếu hai nhánh khiến kiểu kết quả là `int`, viết `byte result = ...` không tự buộc kết quả trở thành `byte`.

### Sơ đồ đánh giá khi chạy

~~~text
                 condition
                     |
                 boolean?
                  /     \
              true      false
               |          |
        đánh giá nhánh 2  đánh giá nhánh 3
               \          /
               chuyển đổi giá trị đã chọn
                       |
                giá trị biểu thức ?:
~~~

Sơ đồ này mô tả **thứ tự chạy**; kiểu kết quả đã được trình biên dịch xác định **trước** giai đoạn chạy. Nếu điều kiện là đối tượng `Boolean` mang `null`, việc unboxing điều kiện ném `NullPointerException` trước khi chọn nhánh nào.

## 3. Các quy tắc kiểu đặc biệt cho biểu thức số

Theo đặc tả, khi hai nhánh là biểu thức số, kiểm tra lần lượt:

1. **Hai nhánh có cùng kiểu:** kiểu kết quả là chính kiểu đó. Ví dụ `flag ? 1 : 2` là `int`; `flag ? 1L : 2L` là `long`; `flag ? someByte : otherByte` là `byte`.
2. **Một nhánh có kiểu primitive `T` và nhánh kia đúng lớp bao `T`:** kiểu kết quả là primitive `T`; chẳng hạn `byte` với `Byte` cho `byte`. Nhánh lớp bao có thể phải unbox.
3. **Một nhánh là `byte` hoặc `Byte`, nhánh kia là `short` hoặc `Short`:** kiểu kết quả **`short`** (dù dùng lớp bao ở một hoặc cả hai nhánh).
4. **Một nhánh `byte`, `short` hoặc `char`, nhánh còn lại là *constant expression* kiểu `int` nằm trong miền kiểu nhỏ đó:** kết quả có **kiểu nhỏ tương ứng**. Ví dụ `byte` với hằng `int` `100` cho `byte`.
5. **Tương tự với lớp bao `Byte`, `Short`, `Character` và hằng `int` phù hợp miền primitive bên trong:** kết quả là **primitive đã unbox**, ví dụ `Byte` với hằng `int` `100` cho `byte`.
6. **Nếu không thuộc các trường hợp trên:** thực hiện **nâng kiểu số tổng quát** (*numeric promotion*) lên kiểu chung; ví dụ `char` với `short` thành `int`, `int` với `long` thành `long`, `long` với `float` thành `float`, `float` với `double` thành `double`.

Điều thứ tư và thứ năm là hai ngoại lệ dễ bỏ sót. Không phải cứ có một nhánh kiểu `int` thì kết quả luôn là `int`; tuy nhiên **hằng số phải có kiểu `int`, là constant expression và nằm trong miền của kiểu hẹp**.

Bảng ví dụ (giả sử `flag` là `boolean` và các biến đúng kiểu đã ghi):

| Hai nhánh `?:` | Kiểu của cả biểu thức | Vì sao |
|---|---|---|
| `byte / byte` | `byte` | Cùng kiểu |
| `short / short` | `short` | Cùng kiểu |
| `byte / short` | `short` | Ngoại lệ `byte`–`short` |
| `byte / 100` | `byte` | `100` là hằng `int` nằm trong miền `byte` |
| `byte / 200` | `int` | `200` không nằm trong miền `byte` |
| `char / 65` | `char` | Hằng `int` `65` nằm trong miền `char` |
| `char / -1` | `int` | `-1` không nằm trong miền `char` |
| `char / short` | `int` | Numeric promotion |
| `short / intVariable` | `int` | Biến `int` bình thường, không phải constant expression |
| `int / long` | `long` | Numeric promotion |
| `long / float` | `float` | Numeric promotion; có thể mất chính xác |
| `float / double` | `double` | Numeric promotion |

Ở cột đầu, dấu `/` **chỉ phân cách kiểu của hai nhánh**, không biểu thị phép chia. Kiểu chung là quy tắc **biên dịch**, không phụ thuộc vào việc `flag` cuối cùng đúng hay sai.

## 4. Ngoại lệ `byte`–`short`: kết quả có kiểu `short`

Các biểu thức số học hai ngôi như `byteValue + shortValue` thường được nâng lên `int`. Nhưng `flag ? byteValue : shortValue` là trường hợp **đặc biệt**, kiểu kết quả là `short`:

~~~java
boolean flag = true;
byte small = 10;
short medium = 20;

short chosen = flag ? small : medium; // hợp lệ
// byte invalid = flag ? small : medium; // lỗi biên dịch: kết quả short
~~~

Đoạn minh họa có thể đặt vào `main`. Nếu `flag` là `true`, giá trị `10` của `byte` được mở rộng thành `short`; nếu `false`, chọn giá trị `20` kiểu `short`. **Kiểu tổng thể vẫn là `short` trong cả hai trường hợp**.

Những lớp bao tương ứng cũng chịu quy tắc này: `Byte` với `short` hoặc `Short` với `byte` có thể tạo biểu thức kiểu `short` và cần unbox ở nhánh được chọn.

Lưu ý khác biệt khi **cộng**:

~~~java
byte small = 10;
short medium = 20;
int sum = small + medium;  // int, vì phép cộng nâng kiểu
short selection = true ? small : medium; // short
~~~

Cùng hai kiểu toán hạng nhưng **hai toán tử khác nhau có hệ quy tắc kiểu khác nhau**.

## 5. Hằng `int` vừa miền kiểu nhỏ: `byte`, `short`, `char`

Giả sử có `byte small = 10;`:

~~~java
boolean flag = true;
byte small = 10;

byte a = flag ? small : 20;      // byte: 20 nằm trong [-128, 127]
int b = flag ? small : 200;      // int: 200 ngoài miền byte
// byte invalid = flag ? small : 200; // lỗi: toàn biểu thức kiểu int
~~~

Dù nhánh được chọn có thể là `small` và mang giá trị `10`, trình biên dịch vẫn xét **cả hai nhánh**. Đổi `flag` thành `true` cố định không biến biểu thức có kiểu `int` thành `byte` theo quy tắc `?:`; kết quả kiểu phải được suy ra trước khi xét gán vào biến đích.

Với `short` và `char` cũng tương tự:

~~~java
short count = 123;
char letter = 'A';

short s = true ? count : 30_000;   // short: 30000 vừa miền
char c = true ? letter : 65;       // char: 65 vừa miền
int x = true ? letter : -1;        // int: -1 không vừa char
int y = true ? count : 40_000;     // int: 40000 ngoài miền short
~~~

Đoạn mã là minh họa, không phải một class chạy độc lập.

### Biểu thức hằng khác với biến `int`

Một **biến `int` không phải `final`** không được xem là hằng thời điểm biên dịch, ngay cả khi tại runtime luôn bằng 20:

~~~java
byte small = 10;
int normal = 20;
final int constant = 20;

byte first = true ? small : constant; // byte
int second = true ? small : normal;   // int
// byte invalid = true ? small : normal; // lỗi nếu gán vào byte
~~~

Trong ví dụ, `constant` là **constant variable** vì kiểu primitive `int`, `final` và được khởi tạo bằng biểu thức hằng. Một biến `final int` được gán từ kết quả runtime, chẳng hạn lời gọi phương thức, **không tự trở thành constant variable**.

Có thể viết các biểu thức hằng như `10 + 10` hoặc `(1 << 5)` khi chúng đáp ứng quy tắc *constant expression*. Nhưng nếu biểu thức có kiểu `long`, ví dụ `20L`, thì ngoại lệ “hằng kiểu `int`” **không áp dụng**; `byte` với `20L` cho `long` theo numeric promotion.

## 6. Cách kiểm chứng **kiểu biên dịch**, không chỉ nhìn giá trị in ra

Nếu in `System.out.println(flag ? small : 20)`, bạn chỉ thấy số `10` hoặc `20`, không biết biểu thức mang kiểu `byte` hay `int`. Có thể dùng **overload** để quan sát lựa chọn của trình biên dịch.

Tạo tệp `ConditionalNumericTypeDemo.java`:

~~~java
public class ConditionalNumericTypeDemo {
    static String kind(byte value) { return "byte"; }
    static String kind(short value) { return "short"; }
    static String kind(int value) { return "int"; }
    static String kind(long value) { return "long"; }
    static String kind(float value) { return "float"; }
    static String kind(double value) { return "double"; }

    public static void main(String[] args) {
        boolean flag = true;
        byte small = 10;
        short medium = 20;
        char letter = 'A';
        int normal = 20;
        final int constant = 20;

        System.out.println("byteShort=" + kind(flag ? small : medium));
        System.out.println("byteIntConst=" + kind(flag ? small : 20));
        System.out.println("byteFinalConst=" + kind(flag ? small : constant));
        System.out.println("byteIntVariable=" + kind(flag ? small : normal));
        System.out.println("byteIntOutside=" + kind(flag ? small : 200));
        System.out.println("charShort=" + kind(flag ? letter : medium));
        System.out.println("intLong=" + kind(flag ? 1 : 2L));
        System.out.println("intDouble=" + kind(flag ? 1 : 2.5));
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac ConditionalNumericTypeDemo.java
java ConditionalNumericTypeDemo
~~~

Đầu ra:

~~~text
byteShort=short
byteIntConst=byte
byteFinalConst=byte
byteIntVariable=int
byteIntOutside=int
charShort=int
intLong=long
intDouble=double
~~~

Đây là **kiểu tĩnh của kết quả `?:`** được Java chọn tại vị trí gọi `kind`. Đặc biệt, dù `flag` đang là `true` và nhánh `small` có giá trị 10, dòng `byteIntOutside` vẫn gọi overload `kind(int)` vì nhánh không được chọn là hằng `200` vượt miền `byte`.

Bằng cách tương tự, trong môi trường Java 10+ có thể dùng `var result = flag ? small : medium;`. `var` suy diễn **kiểu tĩnh của biểu thức bên phải** là `short`; `var` không tự giữ kiểu của nhánh runtime.

## 7. Wrapper `Byte`, `Short`, `Character` và unboxing

Java hỗ trợ lớp bao cho primitive: `Byte`, `Short`, `Character`, `Integer`, `Long`, `Float`, `Double`. Với `?:` dạng số, **box/unbox có thể xảy ra** để đưa hai nhánh về kiểu kết quả.

### Kết hợp primitive và wrapper đúng cặp

Ví dụ:

~~~java
Byte wrapper = Byte.valueOf((byte) 10);
byte primitive = 20;
byte chosen = true ? wrapper : primitive; // byte: wrapper được unbox
~~~

Biểu thức kết quả là **`byte`**, không phải `Byte`. Nếu wrapper là `null` và nhánh wrapper được chọn, phép unbox ném `NullPointerException`.

### Kết hợp wrapper với hằng `int` nằm trong miền

~~~java
Byte boxed = Byte.valueOf((byte) 5);
int chosen = true ? boxed : 7; // biểu thức có kiểu byte, mở rộng khi gán int
~~~

Ở đây, hằng `7` là `int` nằm trong miền `byte`, nên kết quả `?:` là **primitive `byte`**; khi gán cho `int` mới có thêm bước widening từ `byte` lên `int`.

Ngược lại, `true ? boxed : 200` có kiểu `int` vì `200` không nằm trong miền `byte`, cũng có thể cần unbox nhánh `boxed`.

### Hai wrapper cùng một kiểu

`Byte first = ...; Byte second = ...;` thì `flag ? first : second` có kiểu **`Byte`** theo quy tắc hai nhánh cùng kiểu. Kết quả có thể là `null` mà chưa ném lỗi, miễn là chưa có bước unboxing. Đây là khác biệt quan trọng với một nhánh `Byte` và một nhánh primitive `byte`.

### Ví dụ Java 17+ độc lập: lỗi `null` chỉ khi nhánh cần unbox được chọn

Tạo tệp `ConditionalWrapperDemo.java`:

~~~java
public class ConditionalWrapperDemo {
    public static void main(String[] args) {
        Byte missing = null;

        System.out.println("unselected=" + (false ? missing : 7));

        try {
            int value = true ? missing : 7;
            System.out.println(value);
        } catch (NullPointerException ex) {
            System.out.println("selectedNull=NPE");
        }

        Byte other = Byte.valueOf((byte) 9);
        Byte sameType = true ? missing : other;
        System.out.println("sameTypeKeepsNull=" + (sameType == null));

        Boolean condition = null;
        try {
            int value = condition ? 1 : 2;
            System.out.println(value);
        } catch (NullPointerException ex) {
            System.out.println("nullCondition=NPE");
        }
    }
}
~~~

~~~bash
javac ConditionalWrapperDemo.java
java ConditionalWrapperDemo
~~~

Đầu ra:

~~~text
unselected=7
selectedNull=NPE
sameTypeKeepsNull=true
nullCondition=NPE
~~~

Giải thích:

1. `false ? missing : 7` chọn `7` và không đánh giá biến `missing` như toán hạng được chọn để unbox; do đó không gây lỗi.
2. `true ? missing : 7` chọn `missing`: kiểu biểu thức số là `byte`, cần unbox đối tượng `null`, gây `NullPointerException`.
3. Hai nhánh `Byte` cùng kiểu, kết quả biểu thức vẫn là tham chiếu `Byte`, có thể mang `null`.
4. `condition` thuộc kiểu `Boolean` và chứa `null`; lỗi phát sinh khi unbox **điều kiện**, trước bước chọn nhánh.

Nếu cần diễn đạt rõ chính sách cho giá trị tùy chọn, có thể kiểm tra `null` trước hoặc chuyển sang câu lệnh `if`. Đừng xem `?:` là cơ chế tự động cung cấp giá trị mặc định an toàn khi có wrapper `null`.

## 8. Nâng kiểu số thông thường khi không có ngoại lệ

Nếu không khớp các trường hợp đặc biệt, hai nhánh được đưa tới một kiểu số chung. Quy tắc cốt lõi:

- Nếu có `double`, kết quả thường thành `double`.
- Nếu không có `double` nhưng có `float`, kết quả thường thành `float`.
- Nếu không có hai loại trên nhưng có `long`, kết quả thường thành `long`.
- Các tổ hợp nhỏ còn lại (`byte`, `short`, `char`, `int`) thường thành `int`, **trừ những ngoại lệ ở phần trước**.

Ví dụ:

~~~java
boolean flag = true;
char letter = 'A';
short small = 12;

int result = flag ? letter : small;   // int: char/short
long count = flag ? 12 : 30L;        // long: int/long
double ratio = flag ? 1.25f : 2.0;  // double: float/double
~~~

Đây là đoạn minh họa. Hãy lưu ý rằng kiểu chung có thể dẫn đến **mất chính xác**:

~~~java
long huge = 9_007_199_254_740_993L; // 2^53 + 1
double number = true ? huge : 0.0; // long được chuyển sang double
~~~

`double` không biểu diễn chính xác mọi số nguyên 64 bit. Ở ví dụ này, giá trị `huge` lớn hơn số nguyên chính xác cuối cùng liên tiếp của `double`, và chuyển đổi sang `double` có thể làm tròn thành `9007199254740992.0`. Chỉ dùng kiểu số dấu phẩy động khi sai số phù hợp với bài toán.

### Numeric promotion không phải toán tử cộng

`flag ? a : b` **chọn một nhánh**, khác `a + b` là thực hiện phép cộng. Mặc dù cả hai có thể dùng cơ chế numeric promotion, **chúng không đánh giá cả hai nhánh để cộng lại**, và `?:` có quy tắc đặc biệt cho `byte`/`short`/`char` cùng hằng `int` mà phép cộng không có.

## 9. Chỉ nhánh được chọn chạy: side effect và ngoại lệ

Tạo tệp `ConditionalEvaluationDemo.java`:

~~~java
public class ConditionalEvaluationDemo {
    private static int calls;

    private static int selected() {
        calls++;
        return 42;
    }

    private static int notSelected() {
        calls += 100;
        throw new IllegalStateException("should not execute");
    }

    public static void main(String[] args) {
        int answer = true ? selected() : notSelected();

        System.out.println("answer=" + answer);
        System.out.println("calls=" + calls);

        int second = false ? notSelected() : 7;
        System.out.println("second=" + second);
        System.out.println("callsAfter=" + calls);
    }
}
~~~

~~~bash
javac ConditionalEvaluationDemo.java
java ConditionalEvaluationDemo
~~~

Đầu ra:

~~~text
answer=42
calls=1
second=7
callsAfter=1
~~~

Lý do: `notSelected()` **không chạy** trong cả hai lần dùng `?:` vì điều kiện không chọn nhánh đó. Nhưng trình biên dịch vẫn kiểm tra **kiểu** và cú pháp của cả hai nhánh. Đừng đặt các side effect quan trọng ở nhánh không chắc được chọn. Nếu hai nhánh chứa nhiều lệnh phức tạp, `if/else` thường dễ đọc và dễ kiểm soát hơn.

Cũng cần phân biệt với `Math.max(a, b)`: khi gọi phương thức, Java đánh giá cả hai biểu thức đối số trước khi gọi, trong khi `?:` chỉ đánh giá biểu thức nhánh được chọn.

## 10. Kết hợp nhiều `?:`, độ ưu tiên và khả năng đọc

Toán tử điều kiện liên kết **từ phải sang trái** (*right associative*):

~~~java
int level = 2;
int score = level == 1 ? 10 : level == 2 ? 20 : 30;
~~~

Đây là mã minh họa; nó được hiểu là:

~~~java
int score = level == 1 ? 10 : (level == 2 ? 20 : 30);
~~~

Kết quả bằng `20`. Nếu có nhiều điều kiện và nhánh cùng số, cần bảo đảm kiểu của các biểu thức `?:` lồng nhau, vì kiểu của biểu thức con có thể tác động lên kiểu biểu thức ngoài. Dùng ngoặc và đặt tên biến trung gian cho các trường hợp khó đọc.

### Bẫy ưu tiên khi kết hợp phép toán

Một số biểu thức dễ hiểu sai khi thiếu ngoặc:

~~~java
int a = 5;
int b = 3;
int selected = a > b ? a : b;    // 5
int result = (a > b ? a : b) + 1; // 6
~~~

Đây là minh họa. Ngoặc ở `result` làm rõ phép cộng áp dụng lên **toàn bộ kết quả đã chọn**.

Không nên viết quá nhiều toán tử `?:` nối tiếp cho một quy tắc nghiệp vụ có nhiều điều kiện lồng nhau. Dùng `if/else` hoặc phương thức có tên rõ nghĩa thường dễ đọc, dễ kiểm thử hơn.

## 11. So sánh với biểu thức số học và câu lệnh `if/else`

| Tình huống | `?:` | `if/else` hoặc phép toán khác |
|---|---|---|
| Chọn một trong hai giá trị số ngắn gọn | Phù hợp, ví dụ `flag ? a : b` | `if/else` cũng được nhưng dài hơn |
| Hai nhánh có kiểu khác nhau | Cần hiểu quy tắc kiểu của `?:` | Có thể gán riêng sau kiểm tra miền |
| Nhánh có nhiều bước hoặc side effect | Dễ khó đọc | `if/else` rõ luồng |
| Cần xử lý wrapper có thể `null` | Có thể ném lỗi lúc unbox | Kiểm tra `null` và hợp đồng dữ liệu rõ ràng |
| Cần loại bỏ kết quả mất chính xác | Phải xác định kiểu biểu thức trước | Dùng kiểu chính xác/chuyển đổi có kiểm tra |
| Cần tính giá trị lớn/nhỏ nhất | `a > b ? a : b` | `Math.max(a, b)` thuận tiện cho số, nhưng biểu thức đối số được đánh giá theo cơ chế gọi phương thức |

### Không nhầm kiểu của `?:` với kiểu phép gán

Ví dụ, cả hai nhánh đều là biểu thức `int`, nhưng kết quả lựa chọn nằm trong miền `byte`:

~~~java
int left = 10;
int right = 20;
// byte value = true ? left : right; // không biên dịch
int value = true ? left : right;    // hợp lệ
~~~

Dù tại runtime kết quả là 10, kiểu `?:` vẫn là `int`. Nếu muốn thu hẹp xuống `byte`, hãy kiểm tra miền rồi cast một cách có chủ ý, hoặc khai báo các nhánh là `byte` phù hợp khi mô hình dữ liệu thực sự là số 8 bit.

Trường hợp khác: `flag ? (byte) 10 : 20` cho kiểu `byte` vì một nhánh là `byte` và nhánh kia là hằng `int` `20` nằm trong miền. Đây là ngoại lệ ở quy tắc `15.25.2`, không phải quá trình suy kiểu từ biến gán bên trái.

## 12. Những lỗi phổ biến

| Nhận định | Cách hiểu đúng |
|---|---|
| “`?:` luôn làm binary numeric promotion như `+`.” | Có các ngoại lệ như `byte` với `short` cho `short`, hoặc `byte` với hằng `int` vừa miền cho `byte`. |
| “`flag` đúng nên compiler chỉ xét kiểu nhánh thứ hai.” | Compiler vẫn xét kiểu của cả hai nhánh, dù nhánh kia không chạy. |
| “`byte` với bất kỳ `int` đều thành `byte` nếu chọn nhánh `byte`.” | Chỉ có ngoại lệ khi nhánh `int` là **constant expression** và giá trị nằm trong miền. |
| “`final int` luôn là hằng compile-time.” | Chỉ khi biến là *constant variable* được khởi tạo bằng biểu thức hằng hợp lệ. |
| “`byte` với `short` thành `int` vì phép `+` làm vậy.” | Riêng numeric `?:` có kết quả `short`. |
| “Hai nhánh `Byte` có kết quả `byte`.” | Nếu cùng kiểu `Byte`, biểu thức có kiểu `Byte`; trường hợp kết hợp primitive khác. |
| “Nhánh không chạy vẫn được unbox.” | Nhánh không được chọn không đánh giá lúc chạy, nhưng điều kiện `Boolean` cần unbox trước và nhánh được chọn có thể cần unbox. |
| “`null` an toàn vì Java tự chọn nhánh còn lại.” | Điều kiện mới quyết định nhánh; nếu chọn wrapper `null` cần unbox, sẽ có `NullPointerException`. |
| “Kiểu kết quả được chọn theo biến ở vế trái.” | Numeric conditional là standalone expression, kiểu được xác định từ nhánh. |
| “`long` với `float` không mất dữ liệu vì cả hai đều nhiều bit.” | Chuyển `long` sang `float` có thể làm tròn/mất chính xác. |

## 13. Quy trình đọc một biểu thức `?:` số

Khi gặp `condition ? x : y`:

1. Xác minh `condition` có kiểu `boolean` hoặc `Boolean`; với `Boolean` lưu ý trường hợp `null`.
2. Xác định **kiểu tĩnh** của `x` và `y`, không chỉ giá trị đang giữ.
3. Kiểm tra có cùng kiểu hoặc cặp primitive-wrapper tương ứng không.
4. Kiểm tra ngoại lệ `byte`/`Byte` với `short`/`Short`.
5. Kiểm tra một nhánh `byte`/`short`/`char` (hoặc wrapper tương ứng) và nhánh kia là **hằng `int` nằm trong miền** hay không.
6. Nếu không, áp dụng numeric promotion để xác định kiểu chung.
7. Xác minh phép chuyển từ nhánh được chọn sang kiểu kết quả: có mất chính xác số học hoặc unboxing `null` không?
8. Cuối cùng, xem kiểu biểu thức đó có gán được cho biến đích hay không; nếu không, chọn kiểu đích đủ rộng hoặc kiểm tra/ép kiểu rõ ràng.

### Bài tập tự kiểm tra

Giả sử:

~~~java
boolean flag = true;
byte b = 5;
short s = 300;
char c = 'A';
int ordinary = 5;
final int fixed = 5;
Byte boxed = Byte.valueOf((byte) 5);
~~~

Đây là đoạn khởi tạo dùng chung cho các câu hỏi, chưa phải class hoàn chỉnh.

1. Kiểu của `flag ? b : s` là gì?
2. Kiểu của `flag ? b : 5` là gì?
3. Kiểu của `flag ? b : ordinary` là gì, dù `ordinary` hiện bằng 5?
4. Kiểu của `flag ? b : fixed` là gì?
5. Kiểu của `flag ? c : s` là gì?
6. Kiểu của `flag ? boxed : 5` là `Byte`, `byte` hay `int`?
7. Kiểu của `flag ? b : 500` là gì?
8. Nếu `Byte missing = null;` thì `true ? missing : 5` có thể ném ngoại lệ nào?
9. Viết kiểu kết quả của `flag ? 5L : 2.5f`. Có đảm bảo mọi giá trị `long` chuyển sang kiểu đó chính xác không?
10. Nếu `flag` được đặt `true`, biểu thức `flag ? b : someMethod()` có gọi `someMethod()` không? Kiểu của lời gọi `someMethod()` có được compiler xét không?

**Gợi ý đáp án:** (1) `short`. (2) `byte`. (3) `int`. (4) `byte` vì `fixed` là constant variable. (5) `int`. (6) `byte`. (7) `int`. (8) `NullPointerException` khi cần unbox wrapper được chọn. (9) `float`; chuyển `long` sang `float` có thể mất chính xác. (10) Không gọi ở runtime; compiler vẫn kiểm tra kiểu cả hai nhánh.

## 14. Nguồn đặc tả và ghi nhớ

Các quy tắc trên được mô tả trong **Java Language Specification**, mục **15.25 — Conditional Operator `? :`**, đặc biệt **15.25.2 — Numeric Conditional Expressions**:  
[Java Language Specification §15.25](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.25)

**Ghi nhớ:** `?:` **chỉ đánh giá một nhánh khi chạy**, nhưng **kiểu của cả biểu thức được suy ra từ cả hai nhánh khi biên dịch**. Đối với kiểu số, hãy nhớ ngoại lệ `byte`/`short`, hằng `int` vừa miền kiểu hẹp, wrapper và unboxing; những ngoại lệ đó giải thích vì sao kiểu của `?:` không phải lúc nào cũng là kiểu của phép cộng trên cùng toán hạng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-promotion-008">Unboxing khi biểu thức chứa wrapper</a>

<details>
<summary>Xem chi tiết</summary>

# Unboxing khi biểu thức chứa wrapper trong Java

## 1. Vì sao một biểu thức chứa `Integer` có thể gây lỗi?

Java có các **kiểu nguyên thủy** như `int`, `long`, `double`, `boolean` và các **lớp bao** (*wrapper*) tương ứng như `Integer`, `Long`, `Double`, `Boolean`. Wrapper là đối tượng và có thể mang tham chiếu `null`; primitive lưu giá trị của kiểu nguyên thủy và không thể là `null`.

**Unboxing** là việc Java tự chuyển giá trị của wrapper sang primitive tương ứng khi ngữ cảnh đòi hỏi primitive. Ví dụ:

```java
Integer boxed = 10;
int primitive = boxed; // unboxing: Integer -> int
```

Ngược lại, **boxing** là chuyển giá trị primitive thành wrapper:

```java
int primitive = 10;
Integer boxed = primitive; // boxing: int -> Integer
```

Trong một phép toán số học, Java có thể thực hiện một chuỗi bước: **unboxing → thăng cấp số học → tính toán → (nếu cần) boxing khi gán kết quả**. Vì vậy nhìn thấy các toán hạng có kiểu `Integer` không có nghĩa phép cộng tạo ra `Integer` hay phép cộng được thực hiện giữa các đối tượng.

Ví dụ trực tiếp:

```java
Integer left = 7;
Integer right = 5;

int sum = left + right;      // unbox cả hai, cộng int -> 12
Integer boxedSum = left + right; // unbox, cộng int, boxing kết quả -> 12
```

Nếu một wrapper bị unbox khi đang là `null`, Java ném **`NullPointerException` (NPE)**. Nó không tự coi `null` là 0, `false` hoặc một giá trị mặc định.

## 2. Nhận diện đúng kiểu primitive và lớp bao

| Primitive | Wrapper | Giá trị wrapper có thể là `null`? | Khi unbox |
|---|---|---|---|
| `byte` | `Byte` | Có | `byteValue()` tương ứng |
| `short` | `Short` | Có | `shortValue()` tương ứng |
| `int` | `Integer` | Có | `intValue()` tương ứng |
| `long` | `Long` | Có | `longValue()` tương ứng |
| `float` | `Float` | Có | `floatValue()` tương ứng |
| `double` | `Double` | Có | `doubleValue()` tương ứng |
| `char` | `Character` | Có | `charValue()` tương ứng |
| `boolean` | `Boolean` | Có | `booleanValue()` tương ứng |

Cụm “tương ứng” là cách hình dung quá trình chuyển đổi; mã nguồn thường chỉ viết biểu thức tự nhiên, ví dụ `boxed + 1`. Java thực hiện chuyển đổi cần thiết theo quy tắc của ngữ cảnh.

Các tình huống wrapper hữu ích gồm kiểu tổng quát như `List<Integer>` (Java generics không nhận `List<int>`), API cho phép thiếu dữ liệu dưới dạng `null`, hoặc thư viện yêu cầu đối tượng. Khi thực hiện tính toán thuần túy và không cần khả năng `null`, primitive thường rõ ràng hơn.

## 3. Unboxing trong phép cộng, trừ, nhân và chia

Khi toán tử số học yêu cầu kiểu số, wrapper được unbox trước. Với các kiểu tích phân, Java tiếp tục áp dụng **binary numeric promotion**:

- `Byte`, `Short` và `Character` sau unbox được nâng lên `int` khi dùng toán tử số học nhị phân.
- `Integer` sau unbox thành `int`.
- Nếu có toán hạng `Long` hoặc `long`, biểu thức tích phân dùng `long`.
- Với `Float`/`Double` hoặc `float`/`double`, kết quả theo quy tắc thăng cấp số thực tương ứng.

Ví dụ:

```java
Integer a = 10;
Integer b = 5;
Long wide = 2L;
Short small = 3;

int sum = a + b;           // int 15
int difference = a - b;    // int 5
int product = a * b;       // int 50
int quotient = a / b;      // int 2
long mixed = a + wide;     // long 12
int promoted = small * 4;  // int 12
```

Cách diễn giải `a + wide`:

1. `a` được unbox thành `int` 10.
2. `wide` được unbox thành `long` 2.
3. Thăng cấp `int` 10 thành `long` 10.
4. Cộng `long`, cho **`long` 12**.

**Kiểu kết quả do toán tử và quy tắc thăng cấp quyết định**. Kiểu wrapper của biến nhận kết quả chỉ có thể khiến Java boxing **sau khi tính**:

```java
Integer first = 10;
Integer second = 20;
Integer result = first + second; // int + int -> int 30 -> Integer
Long largeResult = first + 20L;  // int + long -> long 30 -> Long
```

Phép cộng số nguyên vẫn có thể **tràn** giống primitive. `Integer.MAX_VALUE + 1` khi được tính bằng `int` vẫn thành `Integer.MIN_VALUE`; wrapper không làm tăng miền giá trị.

## 4. Chương trình Java 17+: nhìn thấy kiểu và NPE

Lưu thành `UnboxingArithmeticDemo.java`:

```java
public class UnboxingArithmeticDemo {
    private static String typeOf(int ignored) {
        return "int";
    }

    private static String typeOf(long ignored) {
        return "long";
    }

    public static void main(String[] args) {
        Integer count = 7;
        Long increment = 5L;
        Short factor = 3;

        System.out.println("int addition: " + (count + 2));
        System.out.println("mixed result: " + (count + increment));
        System.out.println("type int: " + typeOf(count + 2));
        System.out.println("type long: " + typeOf(count + increment));
        System.out.println("short product: " + (factor * 4));

        Integer boxedSum = count + 2;
        System.out.println("boxed sum: " + boxedSum);

        Integer nullable = null;
        System.out.println("string concat: " + nullable);

        try {
            int invalid = nullable + 1;
            System.out.println(invalid);
        } catch (NullPointerException e) {
            System.out.println("arithmetic: NullPointerException");
        }

        try {
            nullable++;
        } catch (NullPointerException e) {
            System.out.println("increment: NullPointerException");
        }
    }
}
```

Chạy trên Java 17+:

```bash
javac UnboxingArithmeticDemo.java
java UnboxingArithmeticDemo
```

Kết quả:

```text
int addition: 9
mixed result: 12
type int: int
type long: long
short product: 12
boxed sum: 9
string concat: null
arithmetic: NullPointerException
increment: NullPointerException
```

Hai overload của `typeOf` là công cụ minh họa **kiểu của biểu thức tại lúc biên dịch**. Với `count + 2`, Java chọn overload `int`; với `count + increment`, Java chọn `long`.

Một điểm đáng chú ý: `"string concat: " + nullable` là **nối chuỗi**, nên giá trị tham chiếu `null` được biểu diễn bằng văn bản `"null"`. Ngược lại, `nullable + 1` cần lấy giá trị số, dẫn đến unboxing và NPE. Hai biểu thức cùng dùng `+` nhưng thuộc **hai ngữ cảnh khác nhau**.

## 5. Phép gán và phép gán kết hợp

### 5.1. Gán primitive từ wrapper

```java
Integer boxed = 42;
int number = boxed; // unboxing
```

Nếu `boxed == null`, phép gán trên sẽ ném NPE. Nếu muốn mặc định 0 khi thiếu giá trị:

```java
Integer boxed = null;
int number = boxed != null ? boxed : 0; // 0
```

Biểu thức điều kiện chỉ đánh giá nhánh được chọn. Ở đây nhánh `boxed` chỉ được chọn khi khác `null`; do đó quá trình unboxing an toàn trong điều kiện này.

### 5.2. `+=`, `-=`, `*=` và `++` cũng có thể unbox

```java
Integer count = 10;
count += 5; // unboxing -> cộng int -> boxing, count = 15
count++;    // unboxing -> tăng int -> boxing, count = 16
```

`Integer` là **đối tượng bất biến** (*immutable*). Các phép cập nhật trên biến wrapper không sửa giá trị bên trong cùng đối tượng; Java tính một giá trị primitive rồi gán lại tham chiếu wrapper phù hợp.

Nếu `count` đang là `null` thì `count += 5` hoặc `count++` cần unboxing và ném NPE. Không thể dựa vào cú pháp `+=` để suy ra null được coi là 0.

### 5.3. Gán kết quả sang kiểu wrapper khác cần đúng quy tắc chuyển đổi

```java
Integer a = 10;
Long b = 20L;

Long total = a + b; // hợp lệ: long 30 -> Long
// Long invalid = a + a; // lỗi biên dịch: int -> Long không phải chuỗi chuyển đổi gán này
long primitiveTotal = a + a; // hợp lệ: int 20 -> long
```

Dòng comment minh họa một giới hạn: Java không tự kết hợp **mở rộng primitive `int` thành `long` rồi boxing thành `Long`** trong phép gán thông thường từ biểu thức `int`. Nếu cần `Long`, hãy thực hiện phép toán kiểu `long`, ví dụ `Long total = (long) a + a;`.

## 6. So sánh: khi nào `==` unbox, khi nào so tham chiếu?

Toán tử `==` có những quy tắc khác nhau tùy kiểu toán hạng.

### 6.1. Wrapper so với primitive số: có unboxing

```java
Integer boxed = 10;
boolean equal = boxed == 10; // true, boxed được unbox
```

Nếu `boxed` là `null`, biểu thức `boxed == 10` có thể ném NPE vì phải unbox trước khi so sánh số.

```java
Integer boxed = null;
boolean isMissing = boxed == null; // true, so sánh tham chiếu, an toàn
// boolean zero = boxed == 0;     // khi chạy sẽ ném NullPointerException
```

**`boxed == null` không unbox**, vì đang so sánh tham chiếu với `null`. Đó là cách kiểm tra thiếu dữ liệu hợp lệ.

### 6.2. Hai wrapper cùng kiểu: có thể so tham chiếu

```java
Integer a = Integer.valueOf(200);
Integer b = Integer.valueOf(200);

boolean sameReference = a == b; // so tham chiếu; đừng dựa vào true/false
boolean sameValue = a.equals(b); // true
```

Khi hai toán hạng đều là `Integer`, `==` kiểm tra **có cùng đối tượng không**. Việc wrapper có thể tái sử dụng đối tượng qua cache khiến `==` đôi khi trả `true` cho cùng giá trị; **đó không phải quy tắc so sánh giá trị đáng tin cậy**.

Dùng `a.equals(b)` để so giá trị khi chắc `a` khác `null`, hoặc `java.util.Objects.equals(a, b)` khi một hay cả hai có thể là `null`:

```java
Integer a = null;
Integer b = null;

System.out.println(java.util.Objects.equals(a, b)); // true
System.out.println(java.util.Objects.equals(a, 0)); // false
```

Ngoài ra, so hai wrapper **khác kiểu** như `Integer` và `Long` có những hạn chế về khả năng so sánh tham chiếu trong biểu thức `==`. Khi mục tiêu là so giá trị số, hãy xác định kiểu số mong muốn và chuyển đổi có kiểm tra, thay vì kỳ vọng `==` tự so mọi loại wrapper theo giá trị.

### 6.3. So sánh quan hệ `<`, `>`, `<=`, `>=`

Các toán tử quan hệ yêu cầu giá trị số nên wrapper số bị unbox:

```java
Integer min = 3;
Long max = 7L;

System.out.println(min < max); // true
```

Nếu một toán hạng cần unbox mà là `null`, Java ném NPE. Cách an toàn là kiểm tra null trước hoặc thiết lập chính sách mặc định/từ chối dữ liệu thiếu.

## 7. `Boolean` trong điều kiện và toán tử logic

Wrapper `Boolean` có thể mang ba trạng thái ở mức tham chiếu: `Boolean.TRUE`, `Boolean.FALSE`, và `null`. Nhưng một điều kiện `if` hay vòng lặp `while` cần **giá trị primitive `boolean`**.

```java
Boolean enabled = Boolean.TRUE;
if (enabled) { // unbox Boolean -> boolean
    System.out.println("enabled");
}
```

Nếu `enabled == null`, `if (enabled)` ném NPE.

Tương tự, các toán tử `!`, `&&`, `||` khi cần giá trị `boolean` cũng có thể unbox wrapper:

```java
Boolean flag = null;
// boolean inverted = !flag;    // NullPointerException
// boolean result = flag && true; // NullPointerException
```

### Kiểm tra null bằng short-circuit

Toán tử `&&` và `||` có **short-circuit**: nhánh bên phải chỉ được đánh giá khi cần.

```java
Boolean flag = null;

boolean safeEnabled = flag != null && flag; // false, không unbox null
boolean alsoSafe = Boolean.TRUE.equals(flag); // false
```

Với `flag != null && flag`, vế trái sai nên vế phải không được đánh giá. Với `Boolean.TRUE.equals(flag)`, phương thức của đối tượng `Boolean.TRUE` không bị gọi trên null; nó trả false nếu flag null.

Hai cách biểu đạt chính sách khác nhau tùy ứng dụng: coi null là false có thể hợp lý với cờ tùy chọn, nhưng **không** nên mặc định null thành false khi null có nghĩa “chưa xác minh”, “chưa nhập” hoặc “không biết”. Trong trường hợp đó, hãy xử lý trạng thái thiếu dữ liệu một cách tường minh.

## 8. Chương trình Java 17+: so sánh và điều kiện null-safe

Lưu tệp `UnboxingConditionsDemo.java`:

```java
import java.util.Objects;

public class UnboxingConditionsDemo {
    public static void main(String[] args) {
        Boolean enabled = null;
        Integer limit = null;

        System.out.println("enabled == null: " + (enabled == null));
        System.out.println("Boolean.TRUE.equals: "
                + Boolean.TRUE.equals(enabled));
        System.out.println("short circuit boolean: "
                + (enabled != null && enabled));
        System.out.println("short circuit numeric: "
                + (limit != null && limit > 0));

        int defaultLimit = limit != null ? limit : 0;
        System.out.println("default limit: " + defaultLimit);
        System.out.println("Objects.equals: " + Objects.equals(limit, 0));

        try {
            boolean active = enabled;
            System.out.println(active);
        } catch (NullPointerException e) {
            System.out.println("boolean unboxing: NullPointerException");
        }

        try {
            boolean positive = limit > 0;
            System.out.println(positive);
        } catch (NullPointerException e) {
            System.out.println("numeric comparison: NullPointerException");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac UnboxingConditionsDemo.java
java UnboxingConditionsDemo
```

Kết quả:

```text
enabled == null: true
Boolean.TRUE.equals: false
short circuit boolean: false
short circuit numeric: false
default limit: 0
Objects.equals: false
boolean unboxing: NullPointerException
numeric comparison: NullPointerException
```

Ở hai biểu thức `enabled != null && enabled` và `limit != null && limit > 0`, Java dừng tại vế trái khi kết quả false, nên **tránh được unboxing của null**. Còn `boolean active = enabled` và `limit > 0` bắt buộc lấy giá trị primitive nên NPE là kết quả đúng theo quy tắc ngôn ngữ.

## 9. Toán tử ba ngôi `?:` và unboxing ẩn

Biểu thức điều kiện dạng `condition ? left : right` là một nơi rất dễ bỏ sót việc chuyển đổi kiểu.

Trong Java, **chỉ một trong hai nhánh `left` hoặc `right` được đánh giá**; tuy nhiên kiểu của toàn biểu thức được xác định theo quy tắc kiểu của toán tử điều kiện khi biên dịch. Nếu quy tắc kiểu yêu cầu primitive, **nhánh wrapper được chọn có thể bị unbox**.

Ví dụ:

```java
Integer maybe = null;

int safe = maybe != null ? maybe : 0; // 0; nhánh null không được chọn
int chosen = true ? 7 : maybe;       // 7; nhánh maybe không được đánh giá
// int bad = false ? 7 : maybe;       // sẽ ném NullPointerException
```

Không nên dựa vào khả năng “nhánh này thường không chạy” để bỏ qua chính sách null. Khi điều kiện thay đổi ở runtime, một nhánh chứa wrapper null có thể trở thành nhánh được chọn.

Mã rõ ràng hơn cho dữ liệu có thể thiếu:

```java
Integer value = null;
int effective = value == null ? 0 : value;
```

Nếu null là lỗi nghiệp vụ, thay vì mặc định 0, hãy kiểm tra và báo lỗi rõ ràng trước khi thực hiện phép toán.

## 10. Wrapper với nối chuỗi: trường hợp `+` không unbox

Toán tử `+` vừa là phép cộng số học, vừa là phép nối chuỗi. Khi **một toán hạng là `String`**, Java nối biểu diễn văn bản của toán hạng còn lại:

```java
Integer count = null;

String message = "count=" + count; // "count=null"
```

Biến `count` vẫn là `null`; Java chỉ sinh văn bản `"null"` để nối chuỗi. Nó **không** thực hiện unboxing để cộng số.

Sự khác biệt xuất hiện khi có dấu ngoặc:

```java
Integer a = 2;
Integer b = 3;

String first = "sum=" + a + b;     // "sum=23"
String second = "sum=" + (a + b); // "sum=5"
```

- `first`: phép `"sum=" + a` đã là nối chuỗi, phép `+ b` tiếp tục nối.
- `second`: dấu ngoặc buộc `a + b` chạy bằng **phép cộng số**, unbox hai wrapper, cho `int` 5; sau đó mới nối chuỗi.

Với `Integer a = null`, `"sum=" + a` cho `"sum=null"` nhưng `"sum=" + (a + 1)` ném NPE do phép cộng trong ngoặc cần unboxing.

**Lưu ý:** đừng kết luận phép nối với mọi loại đối tượng đều an toàn tuyệt đối; một `toString()` do ứng dụng tự viết vẫn có thể ném ngoại lệ. Với wrapper null, hành vi nối ra văn bản `"null"` là quy tắc đơn giản và ổn định.

## 11. Unboxing trong Collections, Stream và API tổng hợp

Java generics yêu cầu các tham số kiểu là **reference type**. Vì vậy dùng `List<Integer>`, không dùng `List<int>`:

```java
java.util.List<Integer> values = java.util.Arrays.asList(1, 2, 3);
int sum = 0;

for (Integer value : values) {
    sum += value; // unbox Integer -> int
}
```

Danh sách có thể chứa `null` nếu API cho phép, khi đó phép cộng trong vòng lặp ném NPE. Điều tương tự xảy ra ở các API cần primitive:

```java
// values.stream().mapToInt(Integer::intValue).sum()
```

`Integer::intValue` cần được gọi trên từng phần tử; nếu có phần tử null, lời gọi gặp NPE. Có thể **lọc null** trước khi chuyển:

```java
int sum = values.stream()
        .filter(java.util.Objects::nonNull)
        .mapToInt(Integer::intValue)
        .sum();
```

Cách này ngầm áp dụng chính sách “bỏ qua các phần tử null”. Nếu null biểu thị dữ liệu bị lỗi hoặc cần được ghi nhận, hãy thay bằng kiểm tra tường minh thay vì âm thầm loại bỏ.

### Ví dụ runnable với danh sách chứa null

Tạo file `UnboxingCollectionDemo.java`:

```java
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class UnboxingCollectionDemo {
    public static void main(String[] args) {
        List<Integer> values = Arrays.asList(1, null, 3);

        int filteredSum = values.stream()
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        System.out.println("filtered sum: " + filteredSum);

        try {
            int rawSum = values.stream()
                    .mapToInt(Integer::intValue)
                    .sum();
            System.out.println(rawSum);
        } catch (NullPointerException e) {
            System.out.println("raw stream: NullPointerException");
        }
    }
}
```

Chạy:

```bash
javac UnboxingCollectionDemo.java
java UnboxingCollectionDemo
```

Kết quả:

```text
filtered sum: 4
raw stream: NullPointerException
```

Đây là ví dụ về **hành vi thực tế của giá trị null trong pipeline**, không có nghĩa `Stream<Integer>` tự động loại bỏ null hay tự nhận giá trị 0.

## 12. Unboxing không bảo vệ khỏi tràn số và phép chia cho 0

Sau unboxing, các phép toán primitive áp dụng quy tắc bình thường:

```java
Integer max = Integer.MAX_VALUE;
int overflowed = max + 1; // Integer.MIN_VALUE

Integer numerator = 10;
Integer denominator = 0;
// int q = numerator / denominator; // ArithmeticException: / by zero
```

Ở dòng cộng, kết quả vẫn tràn theo miền `int`. Ở phép chia, các wrapper được unbox thành số nguyên trước; phép chia cho 0 ném `ArithmeticException`, **không** phải lỗi của boxing/unboxing.

Nếu mẫu dữ liệu cho phép null, ba tình huống cần phân biệt:

1. **Wrapper null** khi cần unbox → `NullPointerException`.
2. **Số chia bằng 0** sau unbox → `ArithmeticException` đối với phép chia nguyên.
3. **Giá trị vượt miền** khi cộng/trừ/nhân số nguyên bình thường → thường không tự ném ngoại lệ mà cho kết quả theo độ rộng số nguyên.

Khi tràn là lỗi nghiệp vụ, chọn API kiểm tra như `Math.addExact`, `Math.multiplyExact`; chúng vẫn không thay thế bước kiểm tra null nếu đầu vào là wrapper.

## 13. Thiết kế API an toàn với wrapper nullable

Trước khi xử lý, cần quyết định rõ **null có nghĩa gì**:

- **Bắt buộc phải có số:** kiểm tra null và báo lỗi với thông điệp hữu ích, trước khi unbox.
- **Null có nghĩa “mặc định 0”:** chuyển thành primitive 0 bằng biểu thức có kiểm tra.
- **Null có nghĩa “chưa biết”:** lưu trạng thái thiếu và xử lý riêng, không biến thành 0.
- **Một dãy cho phép bỏ giá trị null:** lọc null có chủ đích, đồng thời bảo đảm điều đó phù hợp yêu cầu.

Ví dụ phương thức yêu cầu giá trị:

```java
static int requireQuantity(Integer quantity) {
    if (quantity == null) {
        throw new IllegalArgumentException("quantity is required");
    }
    return quantity; // unboxing an toàn sau khi kiểm tra
}
```

Và ví dụ mặc định bằng 0:

```java
static int quantityOrZero(Integer quantity) {
    return quantity == null ? 0 : quantity;
}
```

Các đoạn trên là **phương thức minh họa**, cần được đặt trong một class mới tạo thành mã chạy độc lập. Chọn phương thức theo hợp đồng API chứ không đặt mặc định 0 ở mọi nơi: thiếu dữ liệu và dữ liệu thật bằng 0 có thể mang ý nghĩa khác nhau.

Với `Boolean` nullable, cách viết `Boolean.TRUE.equals(flag)` đặc biệt dễ đọc khi chính sách là **chỉ trạng thái true mới được tính là bật**. Nếu cần ba trạng thái true/false/unknown, nên giữ `Boolean` hoặc dùng kiểu miền dữ liệu tường minh thay vì ép mọi giá trị về primitive.

### Dùng `Objects.requireNonNull` khi null là lỗi

`java.util.Objects.requireNonNull(quantity, "quantity is required")` có thể kiểm tra không-null ở ranh giới API và ném NPE với thông báo rõ ràng. Sau khi kiểm tra, unboxing từ wrapper đã được xác nhận hợp lệ.

Những phương thức như `Objects.equals(a, b)` phục vụ **so sánh đối tượng null-safe**, không phải phương thức tự động chuyển wrapper null thành số 0.

## 14. Hiệu năng và tính rõ ràng

Autoboxing và unboxing giúp mã Java gọn, nhưng mỗi lần chuyển có thể liên quan đến hoạt động đối tượng, sự kiện null hoặc bộ nhớ/cấp phát tùy ngữ cảnh và tối ưu JVM.

Với vòng lặp tính toán lớn, `int`, `long` và các mảng primitive thường phù hợp nếu không cần nullable hoặc generics:

```java
long total = 0;
int[] numbers = {1, 2, 3};

for (int number : numbers) {
    total += number;
}
```

Điều này không có nghĩa phải thay mọi `List<Integer>` bằng mảng primitive. Hãy chọn kiểu theo tính đúng đắn và ràng buộc API; cân nhắc hiệu năng khi đo được vấn đề thực tế.

Một lỗi thiết kế dễ gặp là dùng wrapper nullable trong mã nghiệp vụ nhưng **không xác định chính sách null**. Hệ thống có thể vẫn chạy ở các ca thông thường, rồi ném NPE khi nhận giá trị thiếu từ database, JSON hoặc lời gọi API. Kiểm tra null tại ranh giới dữ liệu giúp đặt lỗi đúng nơi và dễ truy vết hơn.

## 15. Các nhầm lẫn thường gặp

| Nhầm lẫn | Thực tế | Cách xử lý |
|---|---|---|
| `Integer + Integer` trả `Integer` | Unbox rồi cộng `int`; kết quả biểu thức là `int` | Xác định kiểu của toán tử; boxing chỉ xảy ra sau nếu cần |
| `Integer null + 1` tự xem null là 0 | Unbox null ném NPE | Kiểm tra/định nghĩa null trước khi cộng |
| Gán sang `Integer` bảo vệ khỏi tràn | Phép cộng `int` vẫn có thể tràn | Dùng kiểu rộng, `Math.*Exact` khi phù hợp |
| `Integer a == Integer b` luôn so giá trị | Với hai wrapper cùng kiểu, `==` có thể so tham chiếu | Dùng `equals` hoặc `Objects.equals` |
| `Integer a == 0` luôn an toàn | Wrapper bị unbox; null gây NPE | Kiểm tra null trước khi so số |
| `a == null` cũng unbox | So với null là so tham chiếu | Dùng được để phát hiện thiếu dữ liệu |
| `Boolean flag` dùng trong `if` không cần kiểm tra | `if` cần primitive boolean, có unboxing | Dùng null-check hoặc `Boolean.TRUE.equals` |
| `"value=" + nullable` luôn unbox | Đây là nối chuỗi và có thể hiển thị `null` | Phân biệt nối chuỗi với cộng số |
| `List<Integer>` không thể có null | List có thể chứa null tùy API | Kiểm tra trước phép cộng hoặc mapping primitive |
| `count++` trên `Integer` sửa cùng object | Unbox, tính primitive, boxing rồi gán lại | Hiểu tính bất biến của wrapper |
| `int` → `Long` tự chuyển mọi bước | Không có chuỗi widening primitive rồi boxing trong phép gán này | Tạo biểu thức `long` trước khi boxing |

## 16. Bài tập tự kiểm tra

**Câu 1.** Biểu thức `Integer a = 2; Integer b = 3; a + b` có kiểu gì?  
**Đáp án:** `int`. Java unbox hai wrapper rồi cộng hai số nguyên primitive.

**Câu 2.** Nếu `Integer x = null;`, `x == null` và `x == 0` khác nhau thế nào?  
**Đáp án:** `x == null` trả true vì so tham chiếu; `x == 0` cần unbox và ném `NullPointerException`.

**Câu 3.** Với `Integer x = null;`, `"x=" + x` cho gì?  
**Đáp án:** Chuỗi `"x=null"` vì đây là nối chuỗi, không cần unbox số.

**Câu 4.** `Integer a = 2, b = 3;`: vì sao `"sum=" + a + b` khác `"sum=" + (a + b)`?  
**Đáp án:** Dạng đầu nối hai biểu diễn văn bản, cho `"sum=23"`; dạng sau tính số `a + b` bằng unboxing trước, cho `"sum=5"`.

**Câu 5.** `Short s = 2; int value = s * 4;` có hợp lệ không?  
**Đáp án:** Có. Unbox thành `short`, nâng lên `int` và nhân, cho 8.

**Câu 6.** `Boolean enabled = null; if (enabled) { ... }` có an toàn không?  
**Đáp án:** Không. Điều kiện phải unbox `Boolean` thành `boolean` và ném NPE.

**Câu 7.** Cách nào an toàn nếu null được coi là false: `enabled != null && enabled` hay `Boolean.TRUE.equals(enabled)`?  
**Đáp án:** Cả hai. Cách đầu dựa vào short-circuit; cách thứ hai so với đối tượng `Boolean.TRUE`.

**Câu 8.** Tại sao `Integer count = 1; count++;` hợp lệ còn `count = null; count++;` gây NPE?  
**Đáp án:** `++` trên wrapper cần unbox, cộng 1 và boxing lại. Khi count null, bước unbox thất bại.

**Câu 9.** Với `List<Integer> list = Arrays.asList(1, null, 3)`, lệnh `mapToInt(Integer::intValue)` có an toàn không?  
**Đáp án:** Không nếu gặp null; có thể lọc `Objects::nonNull` nếu hợp đồng dữ liệu cho phép bỏ phần tử đó.

**Câu 10.** `Integer max = Integer.MAX_VALUE; int result = max + 1;` có ném NPE hoặc ArithmeticException không?  
**Đáp án:** Không, vì max không null. Phép cộng `int` tràn và trả `Integer.MIN_VALUE`. Muốn phát hiện tràn dùng `Math.addExact`.

**Câu 11.** `Integer a = 10; Long b = 20L;` thì biểu thức `a + b` có kiểu gì?  
**Đáp án:** `long`. Hai wrapper được unbox; `int` được nâng lên `long` trước phép cộng.

**Câu 12.** Muốn dùng `Long result` nhận tổng hai biến `Integer x, y` mà không mất quy tắc kiểu, nên làm gì?  
**Đáp án:** Nếu đã xác nhận x và y không null, viết `Long result = (long) x + y;` để tính bằng `long` rồi boxing kết quả thành `Long`.

## 17. Kết luận và tài liệu tham khảo

Unboxing là **chuyển wrapper về primitive do ngữ cảnh biểu thức yêu cầu**. Với phép cộng, trừ, nhân, chia, so sánh số, điều kiện `boolean` hoặc phép tăng/giảm, Java có thể unbox mà không có từ khóa cast hiển thị. Sau unboxing, **thăng cấp số học và quy tắc số nguyên primitive** tiếp tục quyết định kiểu, tràn số, chia cho 0 và kết quả.

Ba nguyên tắc thực hành:

1. **Kiểm tra hoặc định nghĩa chính sách null** trước khi wrapper phải tham gia phép toán primitive.
2. **Phân biệt nối chuỗi với cộng số và so tham chiếu với so giá trị**; cùng cú pháp `+` hoặc `==` có thể chịu quy tắc khác nhau.
3. **Đừng nhầm boxing/unboxing với bảo vệ số học**; nếu cần chống tràn, vẫn phải tính bằng kiểu phù hợp hoặc dùng phép toán có kiểm tra.

Tham khảo chuẩn ngôn ngữ: [Java Language Specification — §5.1.8 Unboxing Conversion](https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html#jls-5.1.8), [§5.6 Numeric Contexts](https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html#jls-5.6), [§15.18 Additive Operators](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.18).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-promotion-009">Overload: nâng kiểu primitive, boxing và giới hạn kết hợp</a>

<details>
<summary>Xem chi tiết</summary>

# Overload: nâng kiểu primitive, boxing và giới hạn kết hợp trong Java

Khi một lớp có nhiều phương thức cùng tên nhưng khác danh sách tham số (**overload**), Java chọn chữ ký sẽ gọi **tại thời điểm biên dịch** theo kiểu tĩnh của đối số. Sự lựa chọn này phụ thuộc vào những phép chuyển kiểu được cho phép ở từng giai đoạn xét overload. Ba khái niệm quan trọng là **widening primitive** (mở rộng kiểu nguyên thủy), **boxing/unboxing** (đóng hộp/mở hộp) và giới hạn khi kết hợp chúng.

Sau bài này, bạn có thể dự đoán vì sao `m(7)` chọn `m(long)` thay vì `m(Integer)`; vì sao `int` không tự trở thành `Long`; và khi nào compiler ưu tiên một overload cố định thay vì varargs.

## 1. Hiểu ba nhóm chuyển kiểu

### Widening primitive

Widening primitive mở rộng một kiểu nguyên thủy sang kiểu có miền biểu diễn khác: `byte → short → int → long → float → double`; `char → int → long → float → double`; `short → int → long → float → double`. Không có chuyển đổi số tự động từ `boolean`; `char` không widening sang `short`.

~~~java
static void write(long value) { }

byte number = 7;
write(number); // byte -> long: hợp lệ
write(7);      // int -> long: hợp lệ
~~~

Widening primitive thường không mất phạm vi giá trị nhưng **có thể mất độ chính xác** với số lớn chuyển sang `float` hoặc `double`. Đây không phải narrowing primitive: `long → int` cần cast tường minh.

### Boxing và unboxing

Boxing đổi một giá trị primitive thành wrapper tương ứng, chẳng hạn `int → Integer`, `long → Long`, `byte → Byte`. Unboxing đổi ngược lại, như `Integer → int`. Wrapper là đối tượng và có thể bằng `null`; nếu unbox `null`, Java ném `NullPointerException` khi thực thi.

~~~java
static void takeInteger(Integer value) { }

takeInteger(7);         // boxing int -> Integer
Integer boxed = 7;     // boxing trong phép gán
int plain = boxed;     // unboxing Integer -> int
~~~

`Integer` và `Long` là hai class wrapper riêng, không có quan hệ kế thừa giữa chúng; truyền wrapper này cho tham số wrapper kia không phải là widening reference.

### Widening reference

`Integer → Number → Object` là các chuyển đổi mở rộng kiểu tham chiếu (widening reference). Chúng không cần unboxing.

~~~java
static void takeNumber(Number value) { }

Integer x = 7;
takeNumber(x); // Integer -> Number: widening reference
~~~

Nếu primitive `int` được truyền vào tham số `Object`, Java có thể **boxing `int → Integer` rồi widening reference `Integer → Object`**.

## 2. Java xét overload theo ba pha

Theo Java Language Specification `15.12.2, compiler tìm phương thức có thể áp dụng theo thứ tự sau:

| Pha | Khi nào áp dụng | Chuyển kiểu |
| --- | --- | --- |
| 1 — strict invocation | Số đối số cố định; không boxing/unboxing và không trải varargs | Khớp kiểu, widening primitive, widening reference |
| 2 — loose invocation | Nếu pha 1 không có ứng viên; vẫn số đối số cố định | Thêm boxing/unboxing và các chuỗi chuyển hợp lệ |
| 3 — variable arity | Nếu hai pha trước không có ứng viên | Gọi varargs bằng các đối số phần tử |

**Tìm được ứng viên ở pha nào thì dừng việc tìm pha sau ngay ở pha ấy.** Trong pha đang xét, nếu nhiều overload có thể áp dụng, Java chọn phương thức **cụ thể nhất** (most specific). Nếu không xác định được lựa chọn duy nhất, lỗi `ambiguous method call`; nếu không overload nào áp dụng, lỗi biên dịch.

Điều này giải thích khẩu quyết “widening trước boxing, boxing trước varargs” trong các ví dụ đơn giản. Tuy nhiên “thắng” ở đây nói về **pha xét tính áp dụng**; nó không thay thế quy tắc chọn phương thức cụ thể nhất khi nhiều chữ ký cùng pha cạnh tranh.

Một hàm khai báo `void f(int... values)` thực chất có tham số mảng `int[]`. Khi truyền một `int[]` vào hàm, nó được xét như lời gọi có số đối số cố định ngay pha 1; chỉ khi truyền các phần tử riêng lẻ để Java đóng gói mảng mới cần pha 3.

## 3. Vì sao `int` ưu tiên `long` hơn `Integer`?

~~~java
static String choose(long value) {
    return "long";
}
static String choose(Integer value) {
    return "Integer";
}
~~~

- `choose(7)`: literal `7` có kiểu `int`. `int → long` là widening primitive nên overload `long` áp dụng ở pha 1. `int → Integer` là boxing, chỉ đến pha 2 mới được xét. Chọn `long`.
- `byte b = 7; choose(b)`: `byte → long` là widening primitive ở pha 1. Không có boxing trực tiếp `byte → Integer`. Chọn `long`.
- `choose(Integer.valueOf(7))`: đối số có kiểu `Integer`. Overload `Integer` là khớp chính xác ở pha 1, còn `long` cần unboxing `Integer → int` rồi widening `int → long` ở pha 2. Chọn `Integer`.

Điều đáng ghi nhớ là **kiểu tĩnh của biểu thức đối số**, không chỉ giá trị bằng số. `7`, `7L` và `Integer.valueOf(7)` không có cùng kiểu.

## 4. Những chuỗi kết hợp được phép và bị chặn

Trong ngữ cảnh gọi phương thức, quy tắc `method invocation conversion` không cho phép nối tùy tiện mọi phép chuyển kiểu.

| Đối số | Tham số | Kết quả | Lý do |
| --- | --- | --- | --- |
| `byte` | `long` | Hợp lệ | Widening primitive |
| `int` | `Integer` | Hợp lệ | Boxing |
| `int` | `Object` | Hợp lệ | Boxing rồi widening reference: `int → Integer → Object` |
| `Integer` | `long` | Hợp lệ | Unboxing rồi widening primitive: `Integer → int → long` |
| `Integer` | `Number` | Hợp lệ | Widening reference |
| `int` | `Long` | **Không** | Không tự widening primitive rồi boxing: `int → long → Long` |
| `byte` | `Integer` | **Không** | Không tự widening primitive rồi boxing: `byte → int → Integer` |
| `Integer` | `Long` | **Không** | Không tự unbox, widen rồi box lại |
| `long` | `int` | **Không** | Narrowing primitive cần cast |

Hai mẫu kết hợp phải nhớ là **boxing + widening reference** và **unboxing + widening primitive**. Ngoài ra, quy tắc loose invocation trong JLS còn có trường hợp widening reference rồi unboxing (sau đó có thể widening primitive), nhưng không có nghĩa một `Object` bất kỳ tự động được unbox như kiểu mong muốn: quan hệ kiểu và wrapper phải hợp lệ.

Ví dụ sau cho thấy giới hạn rất cụ thể:

~~~java
static void acceptLong(Long number) { }

// acceptLong(7);          // LỖI: int không tự thành Long
acceptLong(7L);            // long -> Long: hợp lệ
acceptLong((long) 7);      // cast cho biểu thức có kiểu long, sau đó boxing
acceptLong(Long.valueOf(7L));
~~~

Với `acceptLong(7)`, compiler **không** tự thực hiện `int → long → Long`. Chọn literal `7L` hoặc chuyển biểu thức sang `long` trước khi gọi nếu API yêu cầu `Long`. Tương tự, một biến kiểu `Integer` không tự trở thành `Long`.

**Hằng số nguyên nhỏ không được tự narrowing khi gọi hàm.** `byte b = 7` là phép gán hợp lệ do quy tắc hằng số, nhưng `acceptByte(7)` không hợp lệ nếu hàm chỉ nhận `byte`, bởi literal `7` vẫn có kiểu `int`. Phải gọi `acceptByte((byte) 7)`.

## 5. Widening reference có thể được chọn trước unboxing

Xét hai overload:

~~~java
static String fromWrapper(Number value) {
    return "Number";
}
static String fromWrapper(long value) {
    return "long";
}
~~~

Với `Integer x = 7; fromWrapper(x)`, overload `Number` áp dụng bằng widening reference `Integer → Number` ở pha 1, trong khi overload `long` cần unboxing rồi widening `Integer → int → long` ở pha 2. Vì thế Java chọn `fromWrapper(Number)`.

Nếu lớp **chỉ có** `fromWrapper(long)`, lời gọi bằng `Integer` vẫn hoàn toàn hợp lệ vì `Integer → int → long` là một chuỗi cho phép ở pha loose invocation. Kết quả chọn `Number` phía trên xuất phát từ **thứ tự xét pha**, không phải vì mọi tham số object luôn được ưu tiên hơn primitive.

## 6. Ví dụ Java 17+ hoàn chỉnh

Lưu tệp dưới tên `OverloadConversions.java`:

~~~java
public class OverloadConversions {
    static String choose(long x) {
        return "long";
    }
    static String choose(Integer x) {
        return "Integer";
    }

    static String fromWrapper(Number x) {
        return "Number";
    }
    static String fromWrapper(long x) {
        return "long";
    }

    static String unboxAndWiden(long x) {
        return "long";
    }
    static String receiveObject(Object x) {
        return "Object";
    }

    static String dispatch(int x) {
        return "fixed int";
    }
    static String dispatch(int... xs) {
        return "varargs " + xs.length;
    }

    static String boxed(Integer x) {
        return "Integer";
    }

    public static void main(String[] args) {
        byte b = 7;
        System.out.println("choose(7)=" + choose(7));
        System.out.println("choose(b)=" + choose(b));
        System.out.println("choose(Integer.valueOf(7))="
                + choose(Integer.valueOf(7)));
        System.out.println("fromWrapper(Integer)="
                + fromWrapper(Integer.valueOf(7)));
        System.out.println("unboxAndWiden(Integer)="
                + unboxAndWiden(Integer.valueOf(7)));
        System.out.println("receiveObject(7)="
                + receiveObject(7));
        System.out.println("dispatch(7)=" + dispatch(7));
        System.out.println("dispatch(7, 8)=" + dispatch(7, 8));
        System.out.println("boxed(7)=" + boxed(7));
    }
}
~~~

Biên dịch và chạy với Java 17 trở lên:

~~~bash
javac OverloadConversions.java
java OverloadConversions
~~~

Stdout:

~~~text
choose(7)=long
choose(b)=long
choose(Integer.valueOf(7))=Integer
fromWrapper(Integer)=Number
unboxAndWiden(Integer)=long
receiveObject(7)=Object
dispatch(7)=fixed int
dispatch(7, 8)=varargs 2
boxed(7)=Integer
~~~

Trong chín dòng trên, `choose(7)` minh họa widening primitive được xét trước boxing; `fromWrapper(Integer)` minh họa widening reference trước unboxing; `unboxAndWiden(Integer)` và `receiveObject(7)` minh họa hai chuỗi hợp lệ; `dispatch(7)` ưu tiên overload cố định còn `dispatch(7, 8)` cần varargs; `boxed(7)` chứng minh boxing hợp lệ khi pha 1 không có phương thức phù hợp. Các kết quả có thể tái hiện bằng mã trên.

## 7. Hai ví dụ cố ý gây lỗi compile

Các đoạn sau là **mã minh họa lỗi**, không đưa nguyên vào chương trình chạy độc lập ở mục 6.

### Không có chuỗi widening rồi boxing

~~~java
static void accept(Long value) { }

accept(7); // ERROR: int không tự đổi thành Long
~~~

Sửa thành `accept(7L)` hoặc `accept(Long.valueOf(7L))` nếu định nghĩa hàm thực sự đòi wrapper `Long`. Không nên thêm overload chỉ để “dỗ” compiler nếu ngữ nghĩa API chưa rõ.

### Hai wrapper khác nhau gây mơ hồ với `null`

~~~java
static void process(Integer value) { }
static void process(Long value) { }

process(null); // ERROR: ambiguous method call
~~~

Cả hai overload đều nhận tham chiếu `null`, nhưng `Integer` và `Long` không phải subtype của nhau, nên không thể chọn một overload cụ thể hơn duy nhất. Có thể làm rõ ý định bằng `process((Integer) null)` hoặc `process((Long) null)`. Đoạn ví dụ này giả định các phương thức cùng nằm trong một lớp và được gọi ở nơi có thể truy cập.

## 8. Các lỗi suy luận và lưu ý thiết kế API

1. **Nhầm kiểu của literal với miền giá trị.** `7` là `int`; `7L` là `long`; gán được `7` vào `byte` không chứng minh gọi `f(byte)` bằng `f(7)` là hợp lệ.
2. **Nhầm wrapper với primitive cùng ý nghĩa số học.** `Integer` là tham chiếu, không phải alias của `int`. Compiler chỉ unbox ở những ngữ cảnh cho phép. Unboxing một wrapper `null` gây `NullPointerException`.
3. **Chọn overload theo kiểu runtime của object.** Nếu `Number n = Integer.valueOf(7)`, overload resolution nhìn thấy `Number`. Runtime polymorphism chọn implementation của **chữ ký đã chọn**, không quay lại chọn overload `Integer` chỉ vì object thật là `Integer`.
4. **Cho rằng varargs luôn ưu tiên hoặc luôn xếp sau mọi tình huống.** Phân biệt khai báo varargs được sử dụng như tham số mảng ở pha 1 với việc trải nhiều đối số riêng lẻ ở pha 3. Hai khai báo `f(int[])` và `f(int...)` không thể tồn tại như hai overload phân biệt, vì cùng chữ ký phương thức.
5. **Cho rằng kiểu trả về quyết định overload.** Không thể khai báo đồng thời `int f(int)` và `long f(int)` chỉ khác return type. Việc viết `long result = f(7)` không làm compiler “tìm chữ ký trả về long” như một tiêu chí đơn giản để chọn overload.
6. **Chọn theo số bước chuyển đổi.** Quy tắc đúng là phân pha rồi tìm phương thức cụ thể nhất trong pha phù hợp đầu tiên. Một chuỗi hai bước hợp lệ có thể được dùng nếu không có overload ở pha sớm hơn.
7. **Dùng cast để che mâu thuẫn API.** Cast `(long) 7` tạo biểu thức kiểu `long`; cast `(Integer) 7` tạo biểu thức kiểu `Integer`. Cast có thể giúp chủ động chọn overload nhưng làm thay đổi ngữ nghĩa nếu áp dụng tùy tiện.
8. **Mở rộng kết luận sang mọi generic/lambda.** Generic method inference, lambda và method reference có thêm quy tắc chọn overload cụ thể. Sử dụng ba pha như nền tảng, sau đó kiểm tra các quy tắc JLS liên quan và thử compiler với ví dụ thực tế.

## 9. Quy trình tự kiểm tra một lời gọi overload

1. Ghi chính xác **kiểu tĩnh** của từng đối số (`7`, `7L`, wrapper, biến tham chiếu, `null`).
2. Liệt kê các overload cùng tên có thể truy cập và phù hợp về số lượng tham số.
3. Tìm ứng viên **pha 1** chỉ bằng khớp kiểu/widening, không boxing/unboxing.
4. Nếu pha 1 rỗng, xét **pha 2** với boxing/unboxing và các chuỗi hợp lệ; không tự chèn primitive widening rồi boxing.
5. Nếu pha 2 rỗng, mới xét **pha 3** cho variable arity.
6. Trong pha đầu có ứng viên, chọn phương thức **cụ thể nhất**; nếu nhiều ứng viên không có lựa chọn duy nhất thì lỗi biên dịch. Sau đó mới nghĩ về giá trị runtime và unboxing `null`.

**Tự luyện:** Có `m(long)` và `m(Integer)`. `m(1)` chọn gì? `m(Integer.valueOf(1))` chọn gì? Một phương thức chỉ có chữ ký `use(Long)` có gọi được bằng `use(1)` không?

**Đáp án:** `m(1)` chọn `m(long)` (pha 1); `m(Integer.valueOf(1))` chọn `m(Integer)` (pha 1, khớp chính xác); `use(1)` không biên dịch vì `int → long → Long` không phải chuỗi chuyển đổi ngầm hợp lệ trong lời gọi.

## Tài liệu chuẩn

- [Java Language Specification §5.3 — Invocation Contexts](https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html#jls-5.3): các loại chuyển kiểu và chuỗi được phép.
- [Java Language Specification §15.12.2 — Determine Method Signature](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.12.2): thứ tự ba pha và chọn phương thức cụ thể nhất.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-promotion-010">var suy luận kiểu từ initializer, không từ cách dùng về sau</a>

<details>
<summary>Xem chi tiết</summary>

# `var` suy luận kiểu từ initializer, không từ cách dùng về sau

Khi viết `var x = expression;` trong Java, trình biên dịch xác định **kiểu tĩnh của biến `x` tại thời điểm khai báo** từ kiểu của **biểu thức khởi tạo (initializer)**. Kiểu này được cố định cho toàn bộ phạm vi biến. Java **không** theo dõi các lệnh gán, lời gọi phương thức hay cách sử dụng về sau để suy ra một kiểu rộng hơn.

```java
var count = 10;   // Kiểu tĩnh của count: int
count = 20;       // Hợp lệ: int → int
// count = 20L;   // Lỗi biên dịch: long không tự thu hẹp về int

var total = 10L;  // Kiểu tĩnh của total: long ngay từ đầu
total = 20L;      // Hợp lệ
```

`var` là **suy luận kiểu cục bộ lúc biên dịch**, không phải biến “tự đổi kiểu” hay kiểu dữ liệu động. Điều này đặc biệt quan trọng với **literal số**, **nâng kiểu toán hạng**, **phép chia nguyên** và **tràn số**, bởi Java quyết định loại phép tính từ **kiểu của biểu thức** chứ không phải từ ý định mà người viết sẽ thể hiện sau đó.

## 1. Cơ chế: initializer quyết định kiểu tĩnh

Xét khai báo:

```java
var result = 1 + 2L;
```

Trình biên dịch xử lý theo thứ tự hợp lý sau:

1. Phân tích initializer `1 + 2L`. Literal `1` có kiểu `int`; literal `2L` có kiểu `long`.
2. Áp dụng **binary numeric promotion**: `int + long` cho biểu thức có kiểu `long`.
3. Suy luận `result` có kiểu tĩnh `long`, tương đương khai báo tường minh `long result = 1 + 2L;`.
4. Với mọi câu lệnh tiếp theo, `result` được kiểm tra như một biến `long`. Java không suy luận lại kiểu khi bạn gán hay dùng nó.

Có thể hình dung:

```text
initializer → xác định kiểu biểu thức → suy luận kiểu tĩnh của biến
                                                    ↓
                                          kiểm tra mọi cách dùng sau đó
```

Quá trình này diễn ra **lúc biên dịch**, trước khi chương trình thực thi. Ví dụ, `var x = 10;` vẫn là `int` kể cả sau đó có câu lệnh `x += 1`, `System.out.println(x)`, truyền `x` cho một phương thức nhận `long`, hoặc cộng `x` với một `long` để tạo giá trị mới.

**Quan trọng:** `var` chỉ thay phần **khai báo kiểu viết bằng tay**. Những quy tắc Java vốn áp dụng cho `int`, `long`, `double`, boxing/unboxing và chuyển kiểu vẫn áp dụng y hệt.

## 2. Các initializer số tạo ra kiểu nào?

| Khai báo | Kiểu được suy luận | Lý do |
| --- | --- | --- |
| `var a = 1;` | `int` | Literal nguyên thập phân không hậu tố `L` thuộc `int` khi vừa miền |
| `var b = 1L;` | `long` | Hậu tố `L` xác định literal `long` |
| `var c = 1.0;` | `double` | Literal dấu phẩy động không hậu tố mặc định là `double` |
| `var d = 1.0f;` | `float` | Hậu tố `f` hoặc `F` |
| `var e = (byte) 1;` | `byte` | Kiểu biểu thức sau ép kiểu là `byte` |
| `var f = (short) 1;` | `short` | Kiểu biểu thức sau ép kiểu là `short` |
| `var g = 1 + 2L;` | `long` | Nâng kiểu trong biểu thức |
| `var h = 1 + 2.0;` | `double` | Phép toán với `double` |
| `var i = 5 / 2;` | `int` | Chia số nguyên cho thương nguyên |
| `var j = 5.0 / 2;` | `double` | Có toán hạng `double` |
| `var k = true ? 1 : 2L;` | `long` | Biểu thức điều kiện áp dụng quy tắc kiểu của `?:` |
| `var m = Integer.valueOf(7);` | `Integer` | Kiểu trả về khai báo của phương thức là wrapper `Integer` |

Bảng trên cho thấy **không có “kiểu mặc định của `var`”**: kiểu phụ thuộc initializer cụ thể.

### Literal lớn và hậu tố `L`

```java
var small = 2_000_000_000;       // int
var big = 3_000_000_000L;        // long
// var invalid = 3_000_000_000; // Không biên dịch: literal decimal vượt int
```

`var` không tự tìm một kiểu đủ lớn cho literal thập phân vượt miền `int` nếu thiếu `L`. Với mục đích giữ `long`, cần viết initializer có kiểu `long` ngay từ đầu (ví dụ `3_000_000_000L`), tương tự như khai báo tường minh.

### Kiểu của biểu thức điều kiện đã được xác định trước khi chọn nhánh

```java
var value = true ? 1 : 2L;  // Kiểu long, dù nhánh được chọn cho giá trị là 1
```

Java xác định kiểu của biểu thức `?:` theo kiểu của **cả hai nhánh** và các quy tắc ngôn ngữ; không chờ chạy để suy luận từ nhánh được chọn. Chỉ nhìn vào giá trị runtime `1` không đủ kết luận biến thuộc `int`.

## 3. “Dùng sau này” không làm thay đổi kiểu đã suy luận

```java
var number = 10; // int

long wider = number;       // Hợp lệ: widening từ int sang long
long sum = number + 10L;   // Hợp lệ: biểu thức mới có kiểu long

// number = 10L;           // Lỗi: không có implicit narrowing long → int
// number = 3.14;          // Lỗi: không có implicit narrowing double → int
```

Trong `long wider = number;`, Java chỉ **mở rộng giá trị lúc gán**; kiểu gốc của `number` vẫn là `int`. Trong `number + 10L`, một biểu thức **mới** có kiểu `long` do nâng kiểu toán hạng. Biến `number` không bị đổi kiểu.

Nếu dữ liệu thực sự có thể lớn hơn miền `int`, hãy khai báo đúng từ đầu:

```java
var counter = 0L;   // long, có thể tăng đến miền long
counter += 3_000_000_000L;
```

Trình biên dịch còn kiểm tra **từng lần gán** theo kiểu đã suy luận. Nó không phân tích “tất cả các giá trị tương lai” rồi chọn kiểu phù hợp nhất.

### Tính tương đương với khai báo tường minh

Hai đoạn sau có cùng kiểu biến, cùng phép toán và cùng quy tắc chuyển kiểu:

```java
var a = 7;        // int a = 7;
var b = 3L;       // long b = 3L;
var c = a + b;    // long c = a + b;
```

Suy luận kiểu không phụ thuộc vào tên biến `a`/`b`/`c` hay sự kỳ vọng về loại giá trị của lập trình viên.

## 4. `var` không thay đổi numeric promotion

Trong Java, với nhiều toán tử số học như `+`, `-`, `*`, `/`, `%`, hai toán hạng nguyên nhỏ hơn `int` sẽ được nâng ít nhất lên `int`; nếu có `long` thì biểu thức nguyên là `long`; nếu có `float`/`double` thì áp dụng các quy tắc nâng kiểu dấu phẩy động.

```java
byte first = 10;
byte second = 20;

var sum = first + second;  // int, không phải byte
var product = first * second; // int
var diff = first - second; // int
```

Sự có mặt của `var` ở biến nhận **không ngăn numeric promotion**. Chính biểu thức `first + second` có kiểu `int`.

### Phép chia nguyên vẫn loại phần thập phân

```java
var ratio = 5 / 2;          // int, giá trị 2
var ratioLong = 5L / 2;     // long, giá trị 2L
var ratioDouble = 5.0 / 2;  // double, giá trị 2.5
```

Nếu muốn giá trị có phần lẻ, phải làm cho **ít nhất một toán hạng** thuộc kiểu số thực **trước phép chia**. Viết `var ratio = 5 / 2;` không khiến Java tự chọn `double` vì kết quả toán học chứa `.5`.

Tương tự, `var decimal = (double) (5 / 2);` suy luận `double` nhưng giá trị vẫn là `2.0` vì phần lẻ đã mất trong phép chia `int` xảy ra trước cast. Để nhận `2.5`, viết `(double) 5 / 2` hoặc `5.0 / 2`.

### Phép nhân `int` vẫn có thể tràn trước khi suy luận

```java
var x = 50_000;        // int
var y = 50_000;        // int

var wrong = x * y;       // int, -1794967296 vì vượt int
var correct = (long) x * y; // long, 2500000000
```

Java nhân `int * int` theo 32 bit, kết quả có thể tràn; `var wrong` chỉ nhận **kiểu `int` và giá trị đã tính**. `var` không tự kiểm tra overflow và không tự nâng kiểu vì kết quả cần nhiều bit hơn. Muốn phép nhân dùng `long`, ép **một toán hạng** trước phép nhân, dùng literal `long` sớm, hoặc API như `Math.multiplyFull(int,int)` khi phù hợp.

Ngược lại, `var late = (long) (x * y);` suy luận `long`, **nhưng giá trị là tích `int` đã tràn rồi mới cast**. Cần tách biệt hai câu hỏi: “biến có kiểu nào?” và “biểu thức khởi tạo đã tính đúng chưa?”.

## 5. Gán rút gọn không làm `var` đổi kiểu

Các phép gán rút gọn như `+=`, `-=`, `*=`, `/=` giữ kiểu biến đích và bao gồm chuyển đổi cần thiết theo quy tắc compound assignment. Vì vậy:

```java
var value = 10;    // int
value += 0.5;      // Hợp lệ, nhưng value vẫn là int và có giá trị 10
// value = value + 0.5; // Không biên dịch: biểu thức bên phải là double
```

Câu lệnh `value += 0.5` hoạt động tương đương theo giá trị với `value = (int) (value + 0.5)` (ngoại trừ biểu thức bên trái chỉ được đánh giá một lần). Phần `.5` bị cắt khi chuyển lại thành `int`. Suy luận kiểu từ initializer **không chạy lại** ở phép gán rút gọn.

Trường hợp tương tự:

```java
var count = 127; // int, không bị hạn chế như byte
count++;         // int

var tiny = (byte) 127; // byte
tiny++;                // byte, có thể tràn thành -128
```

Nếu muốn chuyển dần sang số thực hoặc kiểu rộng hơn theo yêu cầu mới, hãy tạo biến có kiểu phù hợp và giá trị khởi tạo hợp lệ, thay vì trông đợi biến `var` cũ “nở rộng kiểu”.

## 6. Inference nhìn vào kiểu tĩnh của phương thức và constructor

### Giá trị trả về của phương thức

```java
var rounded = Math.round(3.5);   // long: Math.round(double) trả long
var smaller = Math.round(3.5f);  // int: Math.round(float) trả int
var wrapper = Integer.valueOf(7); // Integer (wrapper), không phải primitive int
```

`var` suy luận từ **kiểu trả về được khai báo của lời gọi**, xét overload và generic inference nếu có. Nó không dựa vào “giá trị trông như số nguyên nhỏ” tại runtime. `var wrapper = Integer.valueOf(7)` mang kiểu `Integer` nên có thể tham gia boxing/unboxing khi dùng trong biểu thức số học; vẫn là một kiểu tĩnh xác định.

### Constructor và toán tử diamond (`<>`)

```java
var names = new java.util.ArrayList<String>();
names.add("Java");
// names.add(42);  // Không biên dịch: ArrayList<String>

var flexible = new java.util.ArrayList<>();
flexible.add("Java");
flexible.add(42); // Hợp lệ: ở đây suy luận ArrayList<Object>
```

Với `var flexible = new ArrayList<>()`, không có kiểu đích tường minh như `List<String>` để ràng buộc tham số generic. Trong trường hợp này type argument suy luận thành `Object`; việc thêm phần tử sau đó **không làm suy luận lại thành `String` hoặc `Integer`**.

Nếu muốn API hoặc collection có kiểu phần tử cụ thể, hãy truyền type argument trong initializer như `new ArrayList<String>()` hoặc khai báo kiểu đích rõ ràng.

Không nên xem `var` như cơ chế kiểu động kiểu JavaScript. Nó tuân theo **static type checking** của Java và kiểu không biến đổi theo runtime.

## 7. `var` dùng được ở đâu, và yêu cầu initializer nào?

`var` được hỗ trợ để suy luận kiểu **biến cục bộ** từ Java 10 (ví dụ trong phương thức, vòng lặp `for`, enhanced `for` và một số tài nguyên trong try-with-resources). Java 11 hỗ trợ `var` cho tham số lambda khi tuân theo quy tắc khai báo lambda thích hợp. `var` **không dùng thay kiểu** cho field, tham số phương thức thông thường hoặc kiểu trả về phương thức.

Ví dụ hợp lệ:

```java
var counter = 0;
for (var i = 0; i < 3; i++) {
    counter += i;
}

var numbers = new int[] {1, 2, 3};
for (var n : numbers) {
    System.out.println(n);
}
```

Các ví dụ sau được **comment lại vì chúng không biên dịch**, dùng để chỉ rõ giới hạn:

```java
// var missing;           // Sai: không có initializer
// var nullOnly = null;   // Sai: không thể suy luận kiểu từ null
// var array = {1, 2};    // Sai: array initializer trần không có target type
// var f = x -> x + 1;    // Sai: lambda không có target functional interface
// var pair1 = 1, pair2 = 2; // Sai: var không khai báo nhiều biến một lần

// class Demo {
//     var field = 1;      // Sai: không dùng var khai báo field
//     var get() { ... }   // Sai: không dùng var làm kiểu trả về
// }
```

Cách thay thế:

```java
var array = new int[] {1, 2};
java.util.function.IntUnaryOperator f = x -> x + 1;
// Hoặc: var f = (java.util.function.IntUnaryOperator) (x -> x + 1);
```

Lambda và method reference cần **target functional interface** để biết kiểu tham số/kết quả; chỉ một initializer lambda “trần” không cung cấp đủ target type cho khai báo `var`. Với `null`, nếu thực sự cần khởi tạo một reference null, dùng kiểu tường minh (`String s = null;`) thay vì `var s = null;`.

**Chú ý:** ví dụ trong mục này là các **mảnh mã ngữ cảnh**, không phải các file Java độc lập. Chương trình hoàn chỉnh để chạy được đặt ngay dưới.

## 8. Ví dụ chạy Java 17+: kiểu từ initializer và kết quả số học

Tạo tệp `VarInferenceDemo.java`:

```java
public class VarInferenceDemo {
    public static void main(String[] args) {
        var count = 1;          // int
        count += 2;
        System.out.println("count: " + count);

        var countLong = 1L;     // long
        countLong = 3_000_000_000L;
        System.out.println("countLong: " + countLong);

        var sum = count + countLong;  // long: int + long
        System.out.println("int + long: " + sum);

        var ratio = 5 / 2;           // int
        var realRatio = 5.0 / 2;     // double
        System.out.println("ratios: " + ratio + ", " + realRatio);

        var first = 50_000;         // int
        var second = 50_000;        // int
        var bad = first * second;   // int overflow
        var good = (long) first * second; // long
        System.out.println("products: " + bad + ", " + good);

        var conditional = true ? 1 : 2L; // long
        System.out.println("conditional: " + conditional);

        var list = new java.util.ArrayList<String>();
        list.add("Java");
        System.out.println("list: " + list);

        var words = new java.util.ArrayList<>(); // ArrayList<Object>
        words.add("text");
        words.add(42);
        System.out.println("diamond: " + words);

        var numbers = new int[] {1, 2};
        System.out.println("array length: " + numbers.length);

        var width = 10;           // int
        width += 0.5;             // int, vẫn là 10
        System.out.println("compound +=: " + width);
    }
}
```

Biên dịch và chạy:

```bash
javac VarInferenceDemo.java
java VarInferenceDemo
```

Stdout:

```text
count: 3
countLong: 3000000000
int + long: 3000000003
ratios: 2, 2.5
products: -1794967296, 2500000000
conditional: 1
list: [Java]
diamond: [text, 42]
array length: 2
compound +=: 10
```

**Điểm cần đọc trong output:**

1. `count` tiếp tục là `int` sau lệnh `+=`; `countLong` là `long` vì initializer `1L`.
2. `sum` là `long` do `int + long`, mặc dù biến `count` gốc vẫn là `int`.
3. `ratio` mang kiểu `int` nên phần `0.5` bị loại bỏ ở phép `5 / 2`. `realRatio` có kiểu `double` nên nhận `2.5`.
4. `bad` chứa kết quả nhân `int` bị tràn; `good` tính theo `long` vì cast nằm **trước phép nhân**.
5. `conditional` được suy luận kiểu `long` từ biểu thức có nhánh `2L` ngay khi biên dịch, kể cả lúc chương trình in `1`.
6. `list` và `words` có kiểu generic đã xác định khi khai báo; việc thêm phần tử sau đó không khiến Java suy luận lại tham số kiểu.
7. `width += 0.5` vẫn gán kết quả về `int`, vì compound assignment không sửa kiểu biến.

## 9. Khi nào nên dùng `var` và khi nào nên viết rõ kiểu?

`var` có ích khi initializer khiến kiểu rất dễ nhận ra:

```java
var total = 0L;
var ratio = (double) numerator / denominator;
var names = new java.util.ArrayList<String>();
```

Ví dụ thứ hai là mảnh mã trong phương thức: `numerator` và `denominator` cần được khai báo trước. Cast `(double)` giúp thể hiện ý định chia số thực ngay trong initializer, tránh suy luận nhầm ý định của `var`.

Khai báo kiểu tường minh thường rõ hơn khi **miền số và độ chính xác quan trọng**, ví dụ:

```java
long transactionCount = 0L;
double probability = 0.0;
int maxRetries = 3;
```

Không phải vì `var` kém an toàn; hai cách vẫn chịu cùng quy tắc của trình biên dịch. Kiểu tường minh giúp người đọc nhận ngay hợp đồng dữ liệu, nhất là nếu initializer là biểu thức khó đọc hoặc lời gọi generic/overload phức tạp.

Một số nguyên tắc thực hành:

- **Định hình kiểu ngay ở initializer.** Muốn `long`, dùng `0L` hoặc biểu thức `long`; muốn chia số thực, dùng literal/ép kiểu số thực trước phép chia.
- **Đánh giá kiểu trung gian**, đặc biệt với `int * int`, `int / int` và các chuỗi phép toán.
- **Giữ initializer rõ ràng**, tránh tin vào giá trị thực tế khi runtime để đoán kiểu.
- **Chọn đúng kiểu collection ngay khi tạo**, vì `var` kết hợp diamond `<>` có thể suy luận `Object` nếu không có đủ ràng buộc.
- **Dùng tên biến thể hiện đơn vị/nghĩa**, như `timeoutMillis`, `priceCents`, `ratio`; kiểu tĩnh và tên rõ nghĩa phối hợp giúp tránh sai sót.

## 10. Những nhầm lẫn thường gặp

1. **“`var` là kiểu động, biến có thể đổi thành `long` khi được gán số lớn.”** Sai: `var x = 1` cố định `x` là `int` lúc biên dịch.
2. **“Java nhìn lệnh gán sau để suy luận kiểu `var`.”** Sai: chỉ initializer tại khai báo quyết định kiểu suy luận.
3. **“`var` tự tránh tràn `int` vì không khai báo kiểu.”** Sai: biểu thức `int * int` vẫn tính theo `int`.
4. **“`var r = 5 / 2` sẽ thành `double` vì 2.5 là kết quả toán học.”** Sai: `5 / 2` là `int` và giá trị 2.
5. **“`var v = (long) (a * b)` chắc chắn giữ tích.”** Sai: phép nhân `int` có thể đã tràn trước cast.
6. **“Sau `x += 0.5`, biến `x` đổi sang `double`.”** Sai: compound assignment chuyển kết quả về kiểu tĩnh `x`, có thể mất phần lẻ.
7. **“`var list = new ArrayList<>()` tự suy luận `String` vì lần đầu thêm chuỗi.”** Sai: type argument không được tái suy luận từ lời gọi `add` về sau.
8. **“`var x = null` hợp lệ vì Java có thể chọn `Object`.”** Sai: null initializer trần không cung cấp kiểu suy luận phù hợp.
9. **“`var f = x -> x + 1` có thể suy ra functional interface từ thân lambda.”** Sai: lambda cần target type.
10. **“`var` thay được mọi khai báo kiểu trong class.”** Sai: `var` có phạm vi áp dụng theo quy tắc ngôn ngữ, chủ yếu khai báo cục bộ có initializer và một số vị trí được hỗ trợ cụ thể.
11. **“Có `var` thì `long a = 1 + 2L` và `var a = 1 + 2L` là hai kiểu khác nhau.”** Sai: trong ví dụ này cả hai biến đều có kiểu `long`.
12. **“Giá trị `conditional` in ra 1 nên chắc chắn kiểu của nó là `int`.”** Sai: `true ? 1 : 2L` có kiểu `long` do quy tắc biểu thức điều kiện.

## 11. Tự kiểm tra

- `var n = 100; n = 100L;` có biên dịch không? **Không**: `n` là `int` từ initializer `100`, không thể gán `long` trực tiếp.
- `var n = 100L; n = 200;` có biên dịch không? **Có**: giá trị `int` 200 được mở rộng thành `long` khi gán.
- `var x = 7 / 2;` có kiểu và giá trị gì? **`int` và 3**, vì chia nguyên cắt phần lẻ về 0.
- `var y = 7.0 / 2;` có kiểu và giá trị gì? **`double` và 3.5**.
- `var p = 50_000 * 50_000;` có kiểu gì? **`int`**, có thể đã tràn trước khi lưu. Muốn `long` đúng giá trị, viết `var p = 50_000L * 50_000;`.
- `var z = true ? 1 : 2L;` có kiểu gì? **`long`**, do Java xét kiểu cả biểu thức điều kiện.
- `var words = new ArrayList<>();` có đổi thành `ArrayList<String>` sau khi gọi `words.add("hi")` không? **Không**.
- Khi bắt buộc có giới hạn kiểu `long` để tránh vượt miền `int`, việc đặt chữ `L` vào initializer có ý nghĩa gì? **Nó xác định kiểu literal và có thể thay đổi kiểu toàn bộ biểu thức trước khi phép toán diễn ra**.

**Ghi nhớ:** `var` để **trình biên dịch viết giúp kiểu biến**, và kiểu ấy được xác định **một lần duy nhất từ initializer**. `var` không xem trước cách dùng ở các dòng sau và không sửa các quy tắc numeric promotion, overflow, division hay assignment của Java. Hãy đặt kiểu mong muốn vào **initializer/kiểu toán hạng trước phép tính**, rồi kiểm tra đúng miền giá trị.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-promotion-011">Số trong switch và giới hạn theo phiên bản ngôn ngữ</a>

<details>
<summary>Xem chi tiết</summary>

# Số trong switch và giới hạn theo phiên bản ngôn ngữ (Java)

## 1. Mục tiêu

Bài này tập trung vào **kiểu của biểu thức điều kiện trong `switch`**, những kiểu số được Java hỗ trợ ở từng phiên bản, và tác động của **nâng kiểu số (numeric promotion)** lên tính hợp lệ của `switch`/`case`. Sau bài học, bạn có thể:

- Chọn kiểu `byte`, `short`, `char`, `int` hoặc lớp bọc tương ứng cho `switch` ổn định trên Java 17.
- Giải thích vì sao `byte + byte` tạo `int`, vì sao `case` phải là hằng hợp lệ và tại sao `long`/`double` không được dùng trực tiếp làm selector trên Java 17/21.
- Phân biệt `switch` statement và expression, kể cả quy tắc `break`, `yield` và tính đầy đủ của các nhánh.
- Đọc đúng giới hạn lịch sử: `String`/`enum`, pattern matching từ Java 21 và mở rộng selector primitive ở các bản Java 23–27 dưới dạng **preview**.
- Tránh ép kiểu làm tràn hoặc mất độ chính xác chỉ để dùng `switch`.

**Phạm vi mã chạy chính:** Java 17 trở lên, không cần preview. Những ví dụ dùng tính năng của Java 21 hoặc preview Java 27 được ghi rõ và tách riêng.

## 2. Selector là gì? Những kiểu được chấp nhận trên Java 17

Trong `switch (expression) { ... }`, `expression` gọi là **selector**. Trước khi xét các nhánh `case`, trình biên dịch kiểm tra *kiểu của toàn bộ biểu thức selector*. Nó không chỉ nhìn kiểu của một biến xuất hiện bên trong biểu thức.

Với Java 17 và các bản Java 21 ở phần switch số nguyên cổ điển, các selector số primitive được hỗ trợ ổn định là:

| Selector | Java 17 | Ghi chú |
| --- | --- | --- |
| `byte` | Có | Giá trị −128..127 |
| `short` | Có | Giá trị −32768..32767 |
| `char` | Có | Giá trị 0..65535 |
| `int` | Có | Giá trị −2³¹..2³¹−1 |
| `Byte`, `Short`, `Character`, `Integer` | Có | Lớp bọc được xử lý qua unboxing; `null` có thể gây `NullPointerException` |
| `long` | Không | Kể cả literal `1L` và kết quả `int + long` |
| `float`, `double` | Không | Không được mặc định làm tròn để so khớp `case` |
| `boolean` | Không | Không phải selector của `switch` số nguyên cổ điển |

`enum` cũng được hỗ trợ kể từ Java 5, `String` kể từ Java 7, nhưng chúng không phải kiểu số. Java 21 còn hỗ trợ **selector có kiểu tham chiếu** khi sử dụng pattern matching cho `switch`; chi tiết ở phần 9. Bản Java 27 có tính năng **preview** cho primitive selector rộng hơn; xem phần 10.

Ví dụ hợp lệ trên Java 17:

~~~java
byte b = 2;
short s = 300;
char c = 'A';
int i = 10;
Integer boxed = 2;

String a = switch (b) { case 2 -> "byte"; default -> "?"; };
String d = switch (s) { case 300 -> "short"; default -> "?"; };
String e = switch (c) { case 'A' -> "char"; default -> "?"; };
String f = switch (i) { case 10 -> "int"; default -> "?"; };
String g = switch (boxed) { case 2 -> "Integer"; default -> "?"; };
~~~

Đây là các câu lệnh minh họa *bên trong một phương thức*. Chúng không khai báo chương trình Java hoàn chỉnh; chương trình đầy đủ có tại phần 8.

## 3. Nâng kiểu biểu thức: byte + byte không còn là byte

Java áp dụng **binary numeric promotion** cho nhiều toán tử số học, bao gồm `+`, `-`, `*`, `/`, `%`. Khi cả hai toán hạng là `byte`, `short` hoặc `char`, chúng thường được nâng lên `int` trước khi phép toán diễn ra; kết quả có kiểu `int`.

~~~java
byte left = 4;
byte right = 3;

int sum = left + right;      // kiểu của (left + right) là int
// byte impossible = left + right; // lỗi biên dịch: int không tự thu hẹp thành byte

String text = switch (left + right) {
    case 7 -> "seven";
    default -> "other";
};
System.out.println(text); // seven
~~~

`switch (left + right)` **hợp lệ**, vì selector có kiểu `int`, cũng là kiểu được Java 17 hỗ trợ. Không cần ép về `byte` để dùng `switch`.

Nếu một toán hạng là `long`, biểu thức cộng sẽ được nâng lên `long` (trừ trường hợp có `float`/`double` với cấp cao hơn) và không dùng trực tiếp làm selector Java 17:

~~~java
int count = 2;
long offset = 1L;

// Không biên dịch trên Java 17 hoặc Java 21:
// switch (count + offset) {
//     case 3L -> System.out.println("three");
// }
~~~

Cùng logic đó, `switch (a * b)` với `int` hay `byte` được chấp nhận về *kiểu*, nhưng phép toán có thể **tràn** theo số học Java trước khi so khớp `case`. Chỉ vì selector hợp lệ không có nghĩa giá trị tính toán nằm trong miền dự kiến.

### Toán tử một ngôi và chuyển đổi tự động

`+b`, `-b` và `~b` với `b` kiểu `byte` cũng có kết quả `int` (unary numeric promotion):

~~~java
byte b = 1;
int negated = -b;
int inverted = ~b;

System.out.println(negated); // -1
System.out.println(inverted); // -2
~~~

Điều cần xem khi viết `switch` là **kiểu tĩnh sau khi áp dụng các quy tắc biểu thức**, không phải trực giác từ kiểu khai báo của toán hạng ban đầu.

## 4. Case phải là hằng hợp lệ và nằm trong miền kiểu selector

Trong `switch` hằng số truyền thống (Java 17), `case` cho selector số nguyên phải là **biểu thức hằng tại thời điểm biên dịch** và phải tương thích gán với kiểu selector; một hằng `int` được phép thu hẹp sang `byte`, `short` hoặc `char` khi giá trị nằm trong miền có thể biểu diễn.

~~~java
byte level = 100;
final int FIRST = 50;    // compile-time constant variable
final int SECOND = 50 + 50;

String category = switch (level) {
    case FIRST -> "first";
    case SECOND -> "second";
    default -> "other";
};
System.out.println(category); // second
~~~

`FIRST`/`SECOND` là `final int` được gán bằng biểu thức hằng. Chúng vẫn dùng được với `byte` vì 50 và 100 nằm trong khoảng −128..127.

Ngược lại:

~~~java
byte b = 1;

// Không biên dịch Java 17:
// String x = switch (b) {
//     case 128 -> "too large"; // 128 không biểu diễn được bởi byte
//     default -> "other";
// };

// Không biên dịch Java 17:
// int dynamic = Integer.parseInt("1");
// String y = switch (b) {
//     case dynamic -> "one";    // dynamic không phải compile-time constant
//     default -> "other";
// };
~~~

Các hằng số có cùng giá trị không được lặp lại giữa các nhánh của cùng một `switch`:

~~~java
int n = 2;

// Không biên dịch: trùng giá trị case
// switch (n) {
//     case 2 -> System.out.println("two");
//     case 1 + 1 -> System.out.println("also two");
//     default -> System.out.println("other");
// }
~~~

`case 1 + 1` là một biểu thức hằng **có giá trị 2** nên trùng `case 2`. Lỗi này được phát hiện lúc biên dịch.

### Khác biệt giữa kiểu hằng và kiểu giá trị runtime

Một biến `int` thường, dù có giá trị 7 lúc chạy, không trở thành hằng `case`. `final` cũng chưa đủ nếu giá trị được tính bằng lời gọi phương thức. Ví dụ `final int x = Integer.parseInt("7")` vẫn **không** phải *constant variable* theo đặc tả Java.

Khi cần phân loại theo phạm vi động (ví dụ `n >= min && n <= max`), dùng `if/else` trên Java 17, hoặc pattern/guard khi dùng Java 21+ đúng ngữ cảnh. Không nên cố nhồi điều kiện runtime vào nhãn `case` hằng số.

## 5. Char là số nguyên không dấu 16 bit, nhưng case có quy tắc miền giá trị

`char` có thể xuất hiện trong `switch` và đại diện một đơn vị mã UTF-16 từ 0 đến 65535. Một hằng ký tự như `'A'` có giá trị 65; Java cho phép viết `case 'A'` hoặc `case 65` khi selector là `char`:

~~~java
char letter = 'A';

String result = switch (letter) {
    case 65 -> "Latin capital A";
    default -> "another code unit";
};
System.out.println(result); // Latin capital A
~~~

Nhưng `case -1` hoặc `case 65536` không hợp lệ cho selector `char`: các hằng đó ngoài miền 0..65535.

`char` không biểu diễn mọi ký tự Unicode thành **một** giá trị duy nhất: ký tự bổ sung nằm ngoài BMP dùng cặp surrogate UTF-16. Nếu yêu cầu phân loại **code point** Unicode, hãy lấy giá trị `int` code point (ví dụ `String.codePointAt(...)`), rồi dùng `switch (intCodePoint)`. Không nên coi mọi ký tự Unicode đều là một `char`.

## 6. Switch statement, switch expression và các nhánh số

### Statement với dấu hai chấm (cú pháp truyền thống)

~~~java
int code = 2;
switch (code) {
    case 1:
        System.out.println("one");
        break;
    case 2:
        System.out.println("two");
        break;
    default:
        System.out.println("other");
}
~~~

Đoạn trên in `two`. Với dạng `case ...:`, nếu thiếu `break`, luồng chạy có thể **fall through** sang các nhánh tiếp theo. Đây là hành vi của câu lệnh, không liên quan đến tự động chuyển đổi kiểu số.

### Switch rule với mũi tên

Từ Java 14, dạng `case ... ->` là cú pháp ổn định; không fall through:

~~~java
int code = 2;
switch (code) {
    case 1 -> System.out.println("one");
    case 2, 3 -> System.out.println("two or three");
    default -> System.out.println("other");
}
~~~

Dạng này in `two or three`. Trong **switch statement**, biểu thức ngay sau mũi tên phải là statement expression, khối lệnh hoặc `throw`; không thể viết bừa một biểu thức số không có tác dụng và coi nó là câu lệnh hợp lệ.

### Switch expression tạo ra giá trị

`switch` **expression** là tính năng chuẩn từ Java 14. Nó phải tạo ra một giá trị cho mọi khả năng có thể đi đến nhánh và cần **exhaustive**; với selector `int` thường thêm `default`.

~~~java
int level = 3;

int score = switch (level) {
    case 1, 2 -> 10;
    case 3 -> {
        int bonus = 5;
        yield 20 + bonus; // 25
    }
    default -> 0;
};

System.out.println(score); // 25
~~~

Trong khối `case 3 -> { ... }`, từ khóa `yield` chuyển **giá trị kết quả của switch expression** ra ngoài. `break` trong switch statement truyền thống chỉ thoát khỏi câu lệnh; không dùng `break value` để trả giá trị trong cú pháp Java 17. 

Ngoài ra, **kiểu của kết quả switch expression** là vấn đề riêng so với **kiểu của selector**. Các nhánh trả kết quả phải tương thích với ngữ cảnh gán/return; đừng suy rằng vì selector là `byte` nên kết quả luôn có kiểu `byte`.

## 7. Wrapper, unboxing, null và giới hạn của kiểu Number

Java 17 hỗ trợ `Byte`, `Short`, `Character` và `Integer` làm selector bằng cách unbox. Giá trị trong wrapper hợp lệ, nhưng **đối tượng null không thể unbox**.

~~~java
Integer status = Integer.valueOf(2);
String message = switch (status) {
    case 1 -> "created";
    case 2 -> "accepted";
    default -> "unknown";
};
System.out.println(message); // accepted
~~~

Nếu `status = null`, `switch (status)` theo dạng hằng số Java 17 sẽ ném `NullPointerException` trước khi chọn nhánh. `default` **không** phải cơ chế xử lý `null`.

`Long`, `Float`, `Double` và `Number` **không** phải selector cho switch theo hằng số truyền thống của Java 17. Ví dụ:

~~~java
Number numeric = Integer.valueOf(2);
// Không biên dịch trên Java 17:
// switch (numeric) {
//     case 2 -> System.out.println("two");
// }
~~~

Nếu hợp đồng API bảo đảm đầu vào đúng `Integer`, có thể lấy `numeric.intValue()` sau khi xác nhận kiểu/miền; nhưng nếu `numeric` là `Long` rất lớn hoặc `BigDecimal` có phần lẻ, `intValue()` có thể làm mất dữ liệu. Trường hợp tổng quát nên dùng cách phân loại khác hoặc tận dụng pattern matching trên Java 21+.

## 8. Chương trình Java 17+ hoàn chỉnh và output

Lưu thành tệp `NumericSwitchDemo.java`:

~~~java
public class NumericSwitchDemo {
    private static String byteLabel(byte code) {
        return switch (code) {
            case -1 -> "sentinel";
            case 0 -> "zero";
            case 127 -> "maximum byte";
            default -> "other";
        };
    }

    private static int score(int code) {
        return switch (code) {
            case 1, 2 -> 10;
            case 3 -> {
                int base = 20;
                yield base + 5;
            }
            default -> 0;
        };
    }

    public static void main(String[] args) {
        byte left = 4;
        byte right = 3;
        int sum = left + right; // binary numeric promotion: byte + byte -> int

        String sumLabel = switch (sum) {
            case 7 -> "seven";
            default -> "other";
        };
        System.out.println("sum=" + sum + ", label=" + sumLabel);

        System.out.println("byte -1=" + byteLabel((byte) -1));

        char letter = 'A';
        String charLabel = switch (letter) {
            case 65 -> "capital A";
            default -> "other";
        };
        System.out.println("char A=" + charLabel);

        Integer boxed = 2;
        System.out.println("boxed 2 score=" + score(boxed));

        System.out.println("code 3 score=" + score(3));

        long outsideInt = 2_147_483_648L;
        try {
            int exact = Math.toIntExact(outsideInt);
            System.out.println("exact=" + exact);
        } catch (ArithmeticException ex) {
            System.out.println("outside int range");
        }
    }
}
~~~

Biên dịch và chạy trên JDK 17 hoặc mới hơn:

~~~bash
javac --release 17 NumericSwitchDemo.java
java NumericSwitchDemo
~~~

Stdout chính xác:

~~~text
sum=7, label=seven
byte -1=sentinel
char A=capital A
boxed 2 score=10
code 3 score=25
outside int range
~~~

Giải thích:

1. `left + right` có kiểu `int`, nên `switch (sum)` hợp lệ.
2. `byteLabel((byte) -1)` so khớp `case -1` hợp lệ trong miền `byte`.
3. Selector `char` so khớp hằng số nguyên `65` vì giá trị có thể biểu diễn bởi `char`.
4. `Integer boxed` tự unbox thành `int` khi truyền cho `score(int)`; giá trị 2 nhận 10 điểm.
5. Nhánh `case 3` dùng `yield` để trả 25 điểm.
6. `Math.toIntExact` phát hiện 2.147.483.648 vượt miền `int`. Không có phép ép ngầm nguy hiểm từ `long` xuống `int`.

## 9. Hạn chế theo phiên bản: Java 21 mở rộng switch theo kiểu tham chiếu

**Java 21** đưa *pattern matching for switch* thành tính năng chuẩn. Điều này mở rộng `switch` cho các **kiểu tham chiếu** như `Object`, `Number` hoặc `Long` khi dùng pattern hợp lệ trong `case`. Nó **không** làm cho `long`, `float`, `double`, `boolean` dạng primitive trở thành selector hợp lệ trực tiếp trong Java 21.

Ví dụ dưới đây yêu cầu JDK 21+, không dùng preview:

~~~java
public class ReferenceNumberSwitch {
    static String describe(Number value) {
        return switch (value) {
            case null -> "missing";
            case Integer i -> "int " + i;
            case Long l -> "long " + l;
            default -> "other number";
        };
    }

    public static void main(String[] args) {
        System.out.println(describe(Long.valueOf(42)));
        System.out.println(describe(null));
        System.out.println(describe(Double.valueOf(1.5)));
    }
}
~~~

Biên dịch/chạy bằng JDK 21 hoặc mới hơn:

~~~bash
javac --release 21 ReferenceNumberSwitch.java
java ReferenceNumberSwitch
~~~

Stdout:

~~~text
long 42
missing
other number
~~~

`case Long l` là **type pattern** áp dụng cho một đối tượng `Long`, không phải hằng `case 42L` cho primitive `long`. `case null` xuất hiện trong switch pattern hiện đại, cho phép xử lý null có chủ đích; Java 17 ổn định không hỗ trợ cùng cú pháp đó. Phân biệt **selector tham chiếu `Long`** với **selector primitive `long`** là then chốt khi đọc code đa phiên bản.

Trong Java 21, có thể thêm guard `when`:

~~~java
static String classify(Number n) {
    return switch (n) {
        case null -> "missing";
        case Integer i when i > 0 -> "positive int";
        case Integer i -> "other int";
        default -> "other number";
    };
}
~~~

Đây là một *phương thức minh họa* dành cho Java 21+, chưa phải chương trình độc lập. Guard là điều kiện runtime của **pattern case**, khác với yêu cầu nhãn `case` số truyền thống phải là hằng tại thời điểm biên dịch.

## 10. Từ Java 23 đến Java 27: các selector primitive mới đang ở preview

Lịch sử phát triển quan trọng:

| Phiên bản | Thay đổi đáng nhớ |
| --- | --- |
| Java 1.0 | `switch` trên `byte`, `short`, `char`, `int` |
| Java 5 | Thêm enum và các wrapper `Byte`, `Short`, `Character`, `Integer` |
| Java 7 | `String` làm selector |
| Java 12–13 | `switch expression` và rule `->` thử nghiệm (preview) |
| Java 14 | `switch expression` và rule `->` trở thành chuẩn |
| Java 17 | Các kiểu selector số truyền thống vẫn giới hạn đến `int`; pattern switch chỉ là preview, không phải cú pháp chuẩn |
| Java 21 | Pattern matching cho `switch` trở thành chuẩn; mở rộng selector sang kiểu tham chiếu |
| Java 23 | *Primitive Types in Patterns, instanceof, and switch*: preview lần đầu |
| Java 24 | Preview lần hai |
| Java 25 | Preview lần ba (JEP 507) |
| Java 26 | Preview lần bốn (JEP 530) |
| Java 27 | Preview lần năm (JEP 532); **chưa phải tính năng ngôn ngữ chuẩn** |

Trong phiên bản Java 27 có bật preview, phạm vi selector primitive được mở rộng, bao gồm `long` và các kiểu primitive trước đó không dùng trực tiếp được. Ví dụ **chỉ dành cho JDK 27 preview**:

~~~java
public class PreviewLongSwitch {
    public static void main(String[] args) {
        long amount = 5_000_000_000L;
        String label = switch (amount) {
            case 0L -> "zero";
            case 5_000_000_000L -> "five billion";
            default -> "other";
        };
        System.out.println(label);
    }
}
~~~

Chỉ khi môi trường đã cài đúng **JDK 27** có hỗ trợ tính năng preview tương ứng, biên dịch/chạy bằng:

~~~bash
javac --enable-preview --release 27 PreviewLongSwitch.java
java --enable-preview PreviewLongSwitch
~~~

Dự kiến stdout:

~~~text
five billion
~~~

**Không** dùng ví dụ preview làm bằng chứng rằng `switch (long)` chạy được dưới `--release 17` hoặc `--release 21`. Preview phụ thuộc **đúng phiên bản JDK và cờ biên dịch/chạy**; hành vi/cú pháp có thể thay đổi hoặc chưa bao giờ trở thành tính năng chuẩn. Đối với module Java 17 hoặc hệ thống cần chạy lâu dài, nên giữ các ví dụ chính ở phạm vi `switch` ổn định.

## 11. Cách giải quyết khi đầu vào thật sự là long, double hoặc số lớn

### 11.1. long nhưng miền giá trị chắc chắn thuộc int

Nếu dữ liệu luôn nằm trong miền `int`, hãy **kiểm tra** trước khi chuyển:

~~~java
static String classifyLongSafely(long value) {
    int small = Math.toIntExact(value); // ném ArithmeticException nếu ngoài int
    return switch (small) {
        case 0 -> "zero";
        case 1 -> "one";
        default -> "other int";
    };
}
~~~

Đây là phương thức minh họa cho Java 17. Không dùng ép kiểu mù `(int) value` nếu ngoài miền: ví dụ `(int) 4_294_967_296L` là `0`, dễ bị nhầm thành giá trị `case 0` dù dữ liệu đầu vào khác xa số 0.

### 11.2. long có miền lớn hơn int

Nếu ứng dụng chạy Java 17/21 với primitive `long` tùy ý, có thể dùng `if/else if` trực tiếp:

~~~java
static String classifyLargeLong(long value) {
    if (value == 5_000_000_000L) {
        return "five billion";
    }
    if (value == 0L) {
        return "zero";
    }
    return "other";
}
~~~

Không cần chuyển qua `String` rồi `switch` chỉ để lách kiểm tra kiểu; cách đó dễ tạo chi phí chuyển đổi và có thể làm mờ ý nghĩa dữ liệu. Nếu đã xử lý một giao thức chuỗi hoặc có enum trạng thái, `switch` trên chuỗi/enum có thể phù hợp vì hợp đồng dữ liệu vốn là loại đó.

### 11.3. double và vấn đề độ chính xác

Trên Java 17/21, `switch (double)` không được hỗ trợ. Không dùng ép sang `int` để phân loại số thực khi phần thập phân còn có ý nghĩa: `(int) 2.9` cho 2, khiến 2.9 vô tình trùng nhánh của 2.0.

Nếu dữ liệu là phép đo, chọn logic phạm vi hoặc ngưỡng sai số đã được định nghĩa cho nghiệp vụ:

~~~java
static String temperatureBand(double celsius) {
    if (Double.isNaN(celsius)) {
        return "unknown";
    }
    if (celsius < 0.0) {
        return "freezing";
    }
    if (celsius < 30.0) {
        return "moderate";
    }
    return "hot";
}
~~~

Đây là một phương thức Java 17 minh họa. Ngưỡng chỉ để trình bày kỹ thuật; ứng dụng thực phải đặt giới hạn theo nghiệp vụ. `NaN` cần kiểm tra riêng vì mọi so sánh thứ tự với `NaN` đều false; `+Infinity` ở ví dụ trên đi vào nhánh `hot` và `-Infinity` vào `freezing`, nên hãy quy định chính sách dữ liệu vô hạn nếu đầu vào có thể gặp các giá trị này.

### 11.4. BigInteger, BigDecimal và Number

Các lớp số lớn có thể được so sánh bằng `BigInteger.compareTo`, `BigDecimal.compareTo` hoặc bằng API kiểu miền cụ thể. Với Java 21+, type pattern `switch (Number n)` giúp **phân loại lớp đối tượng**, nhưng không biến một `BigInteger` bất kỳ thành hằng `case 123`. Nếu muốn phân nhánh theo giá trị, hãy kiểm tra/so sánh giá trị hoặc đổi sang kiểu nhỏ hơn **sau khi xác nhận miền và độ chính xác**.

## 12. Lỗi hay gặp

| Cách viết hoặc suy nghĩ | Vấn đề | Hướng xử lý |
| --- | --- | --- |
| `switch (byteValue + byteValue2)` luôn là switch của byte | Phép cộng nâng thành `int` | Đọc kiểu của toàn biểu thức; case theo miền `int` |
| `switch (longValue)` trên Java 17 | Lỗi biên dịch selector type | `Math.toIntExact` nếu bảo đảm miền; nếu không dùng `if/else` |
| `switch (doubleValue)` trên Java 17 | Lỗi biên dịch | Dùng điều kiện theo miền, tránh làm mất phần thập phân |
| `case 128` với selector `byte` | Hằng ngoài miền byte | Dùng hằng thuộc miền hoặc đổi mô hình selector |
| `case variable` với biến runtime | Không phải constant expression | Dùng hằng tại compile time; điều kiện động qua `if` hoặc guard ở Java 21+ |
| `case 2` và `case 1 + 1` | Nhãn trùng giá trị | Loại bỏ nhãn trùng |
| `switch ((int) hugeLong)` | Có thể tràn thu hẹp | Kiểm tra bằng `Math.toIntExact` trước |
| `default` sẽ bắt được wrapper null | Java 17 ném `NullPointerException` | Kiểm tra null trước; hoặc `case null` với Java 21+ phù hợp |
| Switch expression chỉ có vài `case` của `int`, không có `default` | Không exhaustive | Thêm `default` hoặc các nhánh đầy đủ |
| Lấy code Java 27 preview dùng với Java 21 | Cú pháp không được hỗ trợ | Dùng API chuẩn của phiên bản mục tiêu |

## 13. Bài tập tự kiểm tra

**Bài 1.** Đoạn sau hợp lệ hay không trên Java 17?

~~~java
byte x = 10, y = 20;
String label = switch (x + y) {
    case 30 -> "thirty";
    default -> "other";
};
~~~

**Đáp án:** Hợp lệ. `x + y` là `int` do binary numeric promotion, và `case 30` là hằng `int` hợp lệ. Giá trị `label` bằng `"thirty"`.

**Bài 2.** Vì sao `case 500` không hợp lệ trong `switch (short s)`?

**Đáp án:** Thực ra **hợp lệ**: 500 nằm trong miền `short`. `case 40000` mới không hợp lệ vì vượt 32767. Cần kiểm tra đúng miền của **kiểu selector**, không suy từ cách viết literal `int`.

**Bài 3.** Vì sao khai báo `final int id = Integer.parseInt("2")` vẫn không cho phép `case id`?

**Đáp án:** `parseInt` là lời gọi phương thức, không phải biểu thức hằng theo Java Language Specification. `final` chỉ ngăn gán lại biến; không tự biến kết quả runtime thành hằng lúc biên dịch.

**Bài 4.** Mã Java 21 `switch (Number n) { case Long l -> ... }` có chứng tỏ `switch (long l)` đã được hỗ trợ không?

**Đáp án:** Không. Trường hợp đầu là **pattern matching theo kiểu tham chiếu** `Long`, còn trường hợp sau dùng **primitive** `long`. Mở rộng selector primitive là một preview riêng ở các JDK mới hơn.

**Bài 5.** Khi có `long value = 4_294_967_296L`, điều gì nguy hiểm trong `switch ((int) value)`?

**Đáp án:** `(int) value` bằng 0 do lấy 32 bit thấp, nên `case 0` có thể khớp sai ý định. Nếu giá trị phải thuộc miền `int`, `Math.toIntExact(value)` sẽ báo lỗi thay vì lặng lẽ đổi thành 0.

## 14. Nguồn tham khảo và điểm cần nhớ

- [Java Language Specification, Java SE 17 — switch statements](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.11) và [switch expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.28): quy tắc selector, nhãn case, tính exhaustive, kết quả biểu thức.
- [Dev.java — Using preview features](https://dev.java/learn/first-steps/using-preview/): `switch expression` thành tính năng chuẩn trong Java 14.
- [Java SE 21 — Pattern Matching for switch](https://docs.oracle.com/en/java/javase/21/language/pattern-matching-switch.html): kiểu tham chiếu và nhánh pattern.
- [Java SE 27 — Primitive Types in Patterns, instanceof, and switch](https://docs.oracle.com/en/java/javase/27/language/primitive-types-patterns-instanceof-switch.html): JEP 532, preview lần năm.

**Ghi nhớ:** Trước hết xét **kiểu tĩnh của selector sau numeric promotion**. Với Java 17, switch số nguyên truyền thống nhận `byte`/`short`/`char`/`int` và bốn wrapper tương ứng; case là hằng compile time tương thích miền. Java 21 mở rộng switch theo **kiểu tham chiếu** bằng pattern, còn mở rộng trực tiếp `switch (long/float/double/boolean)` vẫn là **preview trong Java 27**. Khi ép kiểu để dùng switch, luôn xác minh miền và sự chính xác của giá trị.


</details>

- [Quay lại đầu trang](#back-to-top)
