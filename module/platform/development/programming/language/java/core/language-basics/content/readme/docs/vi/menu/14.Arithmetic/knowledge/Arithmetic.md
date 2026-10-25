<a id="back-to-top"></a>

# Số học

## Menu
- [Cộng, trừ, nhân, chia và phép lấy phần dư](#arithmetic-001)
- [Chia số nguyên và cắt phần thập phân về phía không](#arithmetic-002)
- [Ép kiểu trước phép chia để nhận kết quả số thực](#arithmetic-003)
- [Dấu của phần dư với toán hạng âm](#arithmetic-004)
- [Chia số nguyên cho không gây ArithmeticException](#arithmetic-005)
- [Tràn số nguyên không tự động ném ngoại lệ](#arithmetic-006)
- [NaN, vô cực và số không có dấu trong phép tính số thực](#arithmetic-007)
- [Sai số nhị phân khi tính với float hoặc double](#arithmetic-008)
- [Giới thiệu Math cho trị tuyệt đối, lũy thừa và làm tròn](#arithmetic-009)
- [Giới thiệu phép toán exact và BigDecimal khi yêu cầu vượt khả năng kiểu cơ bản](#arithmetic-010)

## <a id="arithmetic-001">Cộng, trừ, nhân, chia và phép lấy phần dư</a>

<details>
<summary>Xem chi tiết</summary>

# Cộng, trừ, nhân, chia và phép lấy phần dư trong Java

> **Java core — language-basics / Số học**  
> **Yêu cầu:** JDK 17 trở lên; một file Java độc lập, không dùng thư viện ngoài.  
> **Mục tiêu:** Dùng đúng năm toán tử số học `+`, `-`, `*`, `/`, `%`; dự đoán kiểu và kết quả, phân biệt chia nguyên/số thực, hiểu dấu của phần dư, chia cho 0 và những giới hạn của số nguyên/số thực.

## 1. Năm phép toán số học cơ bản

Với `int a = 10, b = 3;`:

| Toán tử | Ý nghĩa | Ví dụ | Kết quả |
| --- | --- | --- | ---: |
| `+` | Cộng | `a + b` | 13 |
| `-` | Trừ | `a - b` | 7 |
| `*` | Nhân | `a * b` | 30 |
| `/` | Chia | `a / b` | 3 |
| `%` | Lấy phần dư | `a % b` | 1 |

~~~java
int a = 10;
int b = 3;

int sum = a + b;
int difference = a - b;
int product = a * b;
int quotient = a / b;
int remainder = a % b;
~~~

**Lưu ý quan trọng:** Với hai số nguyên, `10 / 3` cho **3**, không phải 3.333...; `10 % 3` cho **1** vì `10 = 3 * 3 + 1`. Toán tử `%` trả **phần dư của phép chia**, không phải phần trăm.

`+` còn dùng để **nối chuỗi**:

~~~java
System.out.println("sum=" + 10 + 3);   // sum=103
System.out.println("sum=" + (10 + 3)); // sum=13
~~~

Dấu ngoặc phân biệt cộng số trước khi nối chuỗi.

## 2. Kiểu dữ liệu quyết định phép chia

Khi **cả hai toán hạng đều là số nguyên**, `/` là **chia nguyên**: kết quả bỏ phần lẻ theo hướng về 0.

~~~java
int integerResult = 7 / 2;           // 3
double stillThree = 7 / 2;           // 3.0: chia nguyên xảy ra trước khi gán.
double fractional = 7.0 / 2;         // 3.5
double byCast = (double) 7 / 2;      // 3.5
~~~

Muốn có phần lẻ, hãy chuyển **ít nhất một toán hạng** sang `float` hoặc `double` **trước phép chia**. Gán kết quả phép chia nguyên vào biến `double` **không khôi phục** phần thập phân đã bị bỏ.

Với `byte`, `short` và `char`, Java thực hiện *binary numeric promotion*: toán hạng nhỏ được nâng lên `int` nếu không có toán hạng `long`/`float`/`double`. Ví dụ:

~~~java
byte x = 5, y = 2;
var value = x / y; // Kiểu int, giá trị 2.
~~~

Quy tắc kiểu chung của các phép toán số học hai ngôi: có `double` → `double`; nếu không, có `float` → `float`; nếu không, có `long` → `long`; còn lại là `int`.

## 3. Phần dư `%` và số âm

Với số nguyên và số chia khác 0, Java bảo đảm mối quan hệ:

~~~text
a == (a / b) * b + (a % b)
~~~

Phép chia nguyên lấy phần nguyên hướng về 0. **Phần dư mang dấu của số bị chia** (hoặc bằng 0), không phải luôn không âm:

| Biểu thức | Thương `/` | Phần dư `%` |
| --- | ---: | ---: |
| `7 / 3` và `7 % 3` | 2 | 1 |
| `-7 / 3` và `-7 % 3` | -2 | -1 |
| `7 / -3` và `7 % -3` | -2 | 1 |
| `-7 / -3` và `-7 % -3` | 2 | -1 |

Nếu cần **modulo không âm với mẫu số dương**, chẳng hạn chỉ số vòng tròn từ 0 đến `size - 1`, có thể dùng `Math.floorMod(a, positiveSize)`:

~~~java
System.out.println(-7 % 3);           // -1
System.out.println(Math.floorMod(-7, 3)); // 2
~~~

Không nên thay `floorMod` bằng `a % b` mà không xét đầu vào âm. Với toán hạng thực, Java cũng cho phép `%` (ví dụ `7.5 % 2.0` bằng `1.5`); kết quả mang tính **phần dư số thực**, không phải quy tắc modulo luôn không âm.

## 4. Thứ tự ưu tiên và dấu ngoặc

`*`, `/` và `%` có **cùng mức ưu tiên**, cao hơn `+` và `-`. Các toán tử số học hai ngôi trong cùng mức nhóm từ trái sang phải:

~~~java
int first = 2 + 3 * 4;      // 14: 2 + (3 * 4)
int second = (2 + 3) * 4;  // 20
int third = 20 / 5 * 2;    // 8: (20 / 5) * 2
int fourth = 20 / (5 * 2); // 2
~~~

Dấu ngoặc diễn tả rõ cách nhóm. **Độ ưu tiên không đồng nghĩa thứ tự đánh giá toán hạng**: Java đánh giá các toán hạng của phép toán số học thông thường theo thứ tự trái sang phải, ngay cả khi biểu thức được nhóm theo ưu tiên `*`/`/`.

Tránh nhồi nhiều phép tăng/giảm biến hoặc lời gọi có tác dụng phụ vào một biểu thức dài. Tách biến trung gian khi phép tính cần được kiểm toán.

## 5. Chia cho 0, tràn số và độ chính xác

### Số nguyên chia hoặc lấy dư với 0

~~~java
int x = 10;
int zero = 0;
// int q = x / zero; // Ném ArithmeticException lúc chạy.
// int r = x % zero; // Cũng ném ArithmeticException.
~~~

Khi mẫu số nguyên bằng 0, cả `/` và `%` đều gây `ArithmeticException`. Kiểm tra điều kiện mẫu số trước khi tính nếu nó xuất phát từ đầu vào không tin cậy.

### Số thực có cách xử lý 0 khác

~~~java
double zero = 0.0;
double infinity = 1.0 / zero; // Infinity
double notANumber = zero / zero; // NaN
~~~

Các phép này dùng số thực và **không ném `ArithmeticException`**. Nếu ứng dụng chỉ chấp nhận kết quả hữu hạn, sử dụng `Double.isFinite(result)`; đừng coi mọi kết quả `double` đều là một số hữu hạn thông thường.

### Tràn `int` và sai số số thực

~~~java
int overflow = Integer.MAX_VALUE + 1; // -2147483648
long safe = (long) Integer.MAX_VALUE + 1; // 2147483648
~~~

`int` tràn **không tự nâng thành `long`**. Cần đưa một toán hạng lên kiểu rộng **trước phép tính**; với phép cộng nguyên phải chính xác và cần phát hiện tràn, có thể dùng `Math.addExact`.

Số thực nhị phân thường là **xấp xỉ**:

~~~java
double value = 0.1 + 0.2; // In ra 0.30000000000000004.
~~~

Đối với số tiền cần chính xác theo chữ số thập phân, cân nhắc `BigDecimal` tạo từ chuỗi và quy tắc làm tròn rõ ràng. Không tự sửa sai số bằng cách ép về `int` khi cần giữ phần lẻ.

## 6. Chương trình Java 17+ chạy độc lập

Tạo file **ArithmeticOperatorsDemo.java**:

~~~java
public final class ArithmeticOperatorsDemo {
    public static void main(String[] args) {
        int a = 10;
        int b = 3;
        System.out.println("add=" + (a + b));
        System.out.println("subtract=" + (a - b));
        System.out.println("multiply=" + (a * b));
        System.out.println("intDivide=" + (a / b));
        System.out.println("intRemainder=" + (a % b));

        System.out.println("realDivide=" + (7.0 / 2));
        double assignedAfterDivision = 7 / 2;
        System.out.println("assignedAfterDivision="
                + assignedAfterDivision);

        System.out.println("negativeDivide=" + (-7 / 3));
        System.out.println("negativeRemainder=" + (-7 % 3));
        System.out.println("negativeDivisorRemainder=" + (7 % -3));
        System.out.println("floorMod=" + Math.floorMod(-7, 3));

        System.out.println("precedence=" + (2 + 3 * 4));
        System.out.println("parentheses=" + ((2 + 3) * 4));
        System.out.println("floatRemainder=" + (7.5 % 2.0));

        int zero = 0;
        try {
            System.out.println("invalid=" + (a / zero));
        } catch (ArithmeticException ex) {
            System.out.println("integerZero=ArithmeticException");
        }
        try {
            System.out.println("invalidRemainder=" + (a % zero));
        } catch (ArithmeticException ex) {
            System.out.println("integerRemainderZero=ArithmeticException");
        }

        double realZero = 0.0;
        System.out.println("floatingZero=" + (1.0 / realZero));
        System.out.println("zeroOverZero=" + (realZero / realZero));

        System.out.println("overflow=" + (Integer.MAX_VALUE + 1));
        System.out.println("widenBeforeAdd="
                + ((long) Integer.MAX_VALUE + 1));
        try {
            Math.addExact(Integer.MAX_VALUE, 1);
        } catch (ArithmeticException ex) {
            System.out.println("addExactOverflow=ArithmeticException");
        }

        System.out.println("decimalApproximation=" + (0.1 + 0.2));
        System.out.println("stringAdd=" + ("sum=" + 10 + 3));
    }
}
~~~

Biên dịch/chạy bằng Bash, PowerShell hoặc Command Prompt tại thư mục chứa file:

~~~bash
javac -encoding UTF-8 ArithmeticOperatorsDemo.java
java ArithmeticOperatorsDemo
~~~

**Expected output chính xác:**

~~~text
add=13
subtract=7
multiply=30
intDivide=3
intRemainder=1
realDivide=3.5
assignedAfterDivision=3.0
negativeDivide=-2
negativeRemainder=-1
negativeDivisorRemainder=1
floorMod=2
precedence=14
parentheses=20
floatRemainder=1.5
integerZero=ArithmeticException
integerRemainderZero=ArithmeticException
floatingZero=Infinity
zeroOverZero=NaN
overflow=-2147483648
widenBeforeAdd=2147483648
addExactOverflow=ArithmeticException
decimalApproximation=0.30000000000000004
stringAdd=sum=103
~~~

**Đọc output:** `intDivide` bỏ phần lẻ; `realDivide` giữ phần lẻ vì có toán hạng `double`; `negativeRemainder` và `floorMod` khác nhau khi số bị chia âm; phép chia nguyên cho 0 ném ngoại lệ, phép chia thực cho 0 có thể ra `Infinity`/`NaN`; `widenBeforeAdd` tránh tràn trước khi tính.

## 7. Khi áp dụng vào bài toán thật

| Tình huống | Cách làm phù hợp |
| --- | --- |
| Tính tổng hoặc tích số nguyên | Chọn `int`/`long` đủ miền; kiểm tra tràn nếu bắt buộc chính xác |
| Tính trung bình hoặc tỉ lệ có phần lẻ | Chuyển toán hạng sang `double` trước `/` |
| Tính thương và số dư số nguyên | Dùng `/` và `%`; ghi rõ chính sách với số âm |
| Tạo chỉ số vòng tròn từ số âm, mẫu số dương | Dùng `Math.floorMod` |
| Mẫu số có thể bằng 0 | Kiểm tra đầu vào trước phép chia/lấy dư |
| Số thực cần hữu hạn | Kiểm tra `Double.isFinite` |
| Tiền tệ theo quy tắc thập phân chính xác | Xem xét `BigDecimal` với scale/rounding tường minh |

**Bài tập 1.** Dự đoán `9 / 4`, `9 % 4` và `9.0 / 4`; giải thích kiểu của mỗi kết quả.

**Bài tập 2.** Tính `-9 / 4`, `-9 % 4` và `Math.floorMod(-9, 4)`. Phân biệt phần dư với modulo không âm.

**Bài tập 3.** Cho `int a = 7, b = 0;`. Viết đoạn kiểm tra để không thực hiện `a / b` khi `b == 0`.

**Bài tập 4.** Vì sao `long result = Integer.MAX_VALUE + 1;` vẫn chứa giá trị âm? Sửa bằng cách chuyển một toán hạng sang `long` trước khi cộng.

**Bài tập 5.** Dự đoán `2 + 3 * 4` và `(2 + 3) * 4`. Giải thích dấu ngoặc thay đổi điều gì.

### Ghi nhớ

**`+`, `-`, `*`, `/`, `%` là năm phép toán số học cơ bản.** Với số nguyên, phép chia bỏ phần lẻ và `%` lấy phần dư có dấu theo số bị chia. Kiểu toán hạng quyết định kiểu phép tính; toán tử ưu tiên và dấu ngoặc quyết định cách nhóm. Hãy xét **0 ở mẫu số, tràn số nguyên và độ chính xác số thực** để tránh kết quả bất ngờ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="arithmetic-002">Chia số nguyên và cắt phần thập phân về phía không</a>

<details>
<summary>Xem chi tiết</summary>

# Chia số nguyên và cắt phần thập phân về phía không — Java 17+

## 1. Mục tiêu: hiểu đúng phép chia `/` trên số nguyên

Trong Java 17+, khi **hai toán hạng của `/` đều thuộc kiểu số nguyên** (`byte`, `short`, `char`, `int`, `long` sau các quy tắc nâng kiểu), kết quả là một **thương số nguyên**. Java lấy phần nguyên của thương toán học bằng cách **cắt phần thập phân theo hướng về 0** (*truncation toward zero*).

~~~java
int a = 7;
int b = 2;

int quotient = a / b;
System.out.println(quotient); // 3
~~~

Về mặt toán học, `7 / 2 = 3.5`; phép chia nguyên trả **3** vì phần `.5` bị loại. Nếu dấu thay đổi:

~~~java
System.out.println(-7 / 2);  // -3, không phải -4
System.out.println(7 / -2);  // -3
System.out.println(-7 / -2); // 3
~~~

Với số âm, “cắt về 0” khác với “làm tròn xuống”:

- `-3.5` cắt về 0 → `-3`.
- `-3.5` làm tròn xuống theo trục số → `-4`.

**Ý chính:** `/` trên hai số nguyên **không tự tính số thực**, không làm tròn gần nhất và không phải `Math.floor`. Muốn kết quả có phần lẻ, phải sử dụng ít nhất một toán hạng dấu phẩy động **trước phép chia**.

## 2. Quy tắc dấu và các ví dụ cơ bản

| Biểu thức Java | Thương số học chính xác | Kết quả chia nguyên | Giải thích |
|---|---:|---:|---|
| `7 / 2` | 3.5 | `3` | Bỏ .5 về 0 |
| `-7 / 2` | -3.5 | `-3` | Bỏ -.5 về 0, không thành -4 |
| `7 / -2` | -3.5 | `-3` | Một dấu âm |
| `-7 / -2` | 3.5 | `3` | Hai dấu âm |
| `-1 / 2` | -0.5 | `0` | `int` không có âm 0 |
| `1 / -2` | -0.5 | `0` | Cắt về 0 |
| `8 / 2` | 4 | `4` | Chia hết |
| `0 / 3` | 0 | `0` | Số bị chia là 0, mẫu khác 0 |

Dấu của **thương khác 0** là âm khi hai toán hạng trái dấu, dương khi cùng dấu. Nếu độ lớn thương nhỏ hơn 1, kết quả nguyên là 0 bất kể dấu số học; Java không có `-0` trong `int` hoặc `long`.

### Không hiểu “cắt phần thập phân” là thao tác trên chuỗi

Phép `7 / 2` hoạt động ở mức **quy tắc số học của ngôn ngữ**, không phải biến `3.5` thành một chuỗi rồi xóa ký tự `.5`. Giá trị trung gian không cần tồn tại dưới dạng `double`:

~~~java
var result = 7 / 2; // result có kiểu int và giá trị 3
~~~

Dùng `var` không đổi kiểu của biểu thức chia.

## 3. Kiểu của toán hạng quyết định kiểu kết quả

| Toán hạng | Biểu thức | Kiểu kết quả | Ví dụ |
|---|---|---|---|
| `int` và `int` | `7 / 2` | `int` | `3` |
| `long` và `int` | `7L / 2` | `long` | `3L` |
| `int` và `long` | `7 / 2L` | `long` | `3L` |
| `float` và `int` | `7f / 2` | `float` | `3.5f` |
| `double` và `int` | `7.0 / 2` | `double` | `3.5` |
| `int` và `double` | `7 / 2.0` | `double` | `3.5` |

Trong Java, `byte`, `short` và `char` được **nâng lên `int`** khi tham gia phép chia số học thông thường:

~~~java
byte first = 9;
short second = 2;

int quotient = first / second; // Kết quả biểu thức là int
System.out.println(quotient); // 4

// byte wrong = first / second; // Lỗi compile: int không tự gán về byte
~~~

Vì sao? Quy tắc *binary numeric promotion* áp dụng trước khi phép `/` được thực hiện. Đừng suy kiểu biểu thức từ kiểu biến nhận hoặc từ việc kết quả tình cờ vừa miền `byte`.

**`boolean` không thể dùng với phép chia.** Các wrapper như `Integer`, `Long` có thể được **unbox** trong phép toán số học; nếu wrapper là `null`, quá trình unboxing có thể ném `NullPointerException` trước khi chia.

## 4. Kiểu biến nhận không thay đổi thương đã tính

~~~java
int numerator = 7, denominator = 2;
double assignedAfter = numerator / denominator;       // 3.0
double castAfter = (double) (numerator / denominator); // 3.0
double castBefore = (double) numerator / denominator;  // 3.5
~~~

Hai dòng đầu dùng phép chia `int / int`, nên phần lẻ đã mất trước khi gán hoặc ép kiểu kết quả. Dòng cuối chuyển **toán hạng trước `/`**, do đó là phép chia số thực. Bài kế tiếp đi sâu vào vị trí cast, kiểu kết quả và giới hạn độ chính xác; trọng tâm ở đây là nhận ra bước **cắt thương về 0**.

## 5. Phần dư là dấu vết của thương cắt về 0

Với `b != 0`, thương nguyên `q = a / b` và dư `r = a % b` thỏa quan hệ toán học `a = q * b + r`. Chẳng hạn `-7 / 2` bằng `-3`, nên `-7 % 2` bằng `-1`: `-7 = (-3) * 2 + (-1)`.

~~~java
System.out.println(-7 / 2); // -3
System.out.println(-7 % 2); // -1
~~~

Phần dư nguyên khác 0 cùng dấu với **số bị chia**. Bài về dấu của phần dư phân tích đầy đủ bốn cặp dấu, `Math.floorMod` và các trường hợp biên.

## 6. `Math.floorDiv`: khác chia nguyên ở số âm

Java 17 có `Math.floorDiv(int, int)` và `Math.floorDiv(long, long)` cùng các overload liên quan. Khác `/` là cắt thương về 0, **`Math.floorDiv` làm tròn thương toán học xuống âm vô cực**.

~~~java
System.out.println(-7 / 2);           // -3
System.out.println(Math.floorDiv(-7, 2)); // -4

System.out.println(7 / -2);           // -3
System.out.println(Math.floorDiv(7, -2)); // -4
~~~

| Cặp số | `a / b` (cắt về 0) | `Math.floorDiv(a,b)` (floor) |
|---|---:|---:|
| 7, 2 | 3 | 3 |
| -7, 2 | -3 | -4 |
| 7, -2 | -3 | -4 |
| -7, -2 | 3 | 3 |
| -1, 2 | 0 | -1 |

Muốn cặp **thương floor và phần dư tương thích**, dùng `Math.floorMod`:

~~~java
int a = -7;
int b = 2;

int q = Math.floorDiv(a, b); // -4
int r = Math.floorMod(a, b); // 1

System.out.println(q * b + r); // -7
~~~

Với số chia **dương**, `Math.floorMod` cho dư **không âm**. Khi số chia âm, kết quả `floorMod` có thể âm; **không nên khái quát** rằng hàm này luôn trả số không âm.

**Chọn quy tắc theo nghiệp vụ:** đếm số phần đầy đủ khi giá trị âm có thể phát sinh, chia thời gian thành các khoảng âm hoặc chuẩn hóa chỉ số là những trường hợp cần cân nhắc `floorDiv`/`floorMod`. Với phép chia nguyên thông thường của Java, toán tử `/` dùng quy tắc cắt về 0.

## 7. Dấu ngoặc và tính kết hợp của `/`

Các toán tử `*`, `/`, `%` cùng nhóm ưu tiên và **kết hợp từ trái sang phải**:

~~~java
System.out.println(20 / 3 / 2);   // 3: (20 / 3) / 2
System.out.println(20 / (3 / 2)); // 20: 20 / 1
~~~

Chú ý mỗi lần chia nguyên đều cắt phần lẻ:

- `20 / 3 / 2`: `20 / 3 = 6`, rồi `6 / 2 = 3`.
- `20 / (3 / 2)`: `3 / 2 = 1`, rồi `20 / 1 = 20`.

**Không thể tùy ý chuyển ngoặc** trong chuỗi phép chia nguyên. Cách nhóm quyết định vị trí mất phần lẻ và do đó thay đổi kết quả.

Trong công thức nghiệp vụ có nhiều lần chia, hãy đặt tên biến trung gian hoặc ghi ngoặc thể hiện ý nghĩa của từng bước.

## 8. Giới hạn: chia nguyên cho 0

Cả `/` và `%` trên số nguyên đều ném `ArithmeticException` khi số chia bằng `0`. Với `double`, `7.0 / 0.0` lại cho `Infinity`, còn `0.0 / 0.0` cho `NaN`; đây **không** phải thương nguyên hợp lệ. Hãy kiểm tra mẫu số trước phép chia nếu mẫu đến từ đầu vào và cần kết quả hữu hạn hoặc có hợp đồng lỗi rõ ràng. Bài chuyên chia cho 0 phân tích cách guard, `OptionalInt`, unboxing và tác dụng phụ.

## 9. Ngoại lệ biên: `MIN_VALUE / -1`

Thương toán học của `Integer.MIN_VALUE / -1` vượt miền `int`, nhưng Java trả `-2147483648` và **không ném ngoại lệ**:

~~~java
System.out.println(Integer.MIN_VALUE / -1);          // -2147483648
System.out.println(((long) Integer.MIN_VALUE) / -1); // 2147483648
~~~

`Long.MIN_VALUE / -1L` cũng tràn miền `long`; muốn giá trị toán học cần kiểu lớn hơn như `BigInteger`. `Math.floorDiv` không phải phép kiểm tra tràn. Bài về tràn số tập trung vào `Math.*Exact`, mở rộng toán hạng đúng lúc và kiểm tra miền.

## 10. Viết hàm chia nguyên có kiểm tra cho nghiệp vụ

Hàm minh họa dưới đây **không chấp nhận** chia cho 0 hoặc tràn `int`:

~~~java
static int divideChecked(int a, int b) {
    if (b == 0) {
        throw new IllegalArgumentException("zero divisor");
    }
    if (a == Integer.MIN_VALUE && b == -1) {
        throw new ArithmeticException("int quotient overflow");
    }
    return a / b;
}
~~~

Kiểm tra `b == 0` là cần thiết vì phép chia nguyên Java ném exception; kiểm tra `MIN_VALUE/-1` là cần thiết **nếu yêu cầu không được âm thầm overflow**.

Tùy API, bạn có thể để `ArithmeticException` tự phát sinh với mẫu số 0, nhưng nên **viết rõ hợp đồng** mà caller có thể dựa vào. Ví dụ trên cố ý ném `IllegalArgumentException` cho đầu vào mẫu số 0 để mô tả yêu cầu dữ liệu.

Nếu nghiệp vụ cho phép trường hợp `MIN_VALUE/-1` theo đúng hành vi tràn Java, không cần hàm riêng; hãy hiểu rõ lựa chọn đó và tránh giả định thương luôn bằng thương toán học chính xác.

## 11. `>>` không phải chia cho lũy thừa 2 trong mọi trường hợp âm

Với số nguyên **không âm**, dịch phải số học một bit thường cho giá trị giống chia nguyên cho 2:

~~~java
System.out.println(7 / 2);  // 3
System.out.println(7 >> 1); // 3
~~~

Nhưng số âm cho kết quả khác:

~~~java
System.out.println(-7 / 2);  // -3: cắt về 0
System.out.println(-7 >> 1); // -4: dịch bit giữ dấu
~~~

Vì `>>` là phép toán dịch bit và không áp dụng quy tắc cắt thương về 0 như `/`. **Đừng thay `x / 2` bằng `x >> 1` khi `x` có thể âm** chỉ với giả định đây là một tối ưu tương đương.

Các JIT hiện đại có thể tối ưu phép chia khi an toàn, nhưng tiêu chí chọn toán tử trong source phải là **ngữ nghĩa đúng**, không phải đoán tốc độ.

## 12. Phép gán kết hợp `/=` và kiểu biểu thức

~~~java
int count = 20;
count /= 3;
System.out.println(count); // 6
~~~

Với kiểu `int`, `count /= 3` cập nhật `count` bằng kết quả chia nguyên `20 / 3 = 6`. Phép gán kết hợp cũng là một biểu thức có tác dụng phụ, nhưng viết `count /= 3;` riêng thường dễ hiểu.

Với toán hạng nhỏ như `byte`:

~~~java
byte value = 9;
value /= 2;
System.out.println(value); // 4
~~~

Lệnh trên **biên dịch**, dù phép `value / 2` được nâng kiểu thành `int`, bởi compound assignment áp dụng chuyển đổi về kiểu biến nhận. Ngược lại:

~~~java
byte value = 9;
// value = value / 2; // Lỗi compile: kết quả chia là int
~~~

Khi sử dụng `/=`, vẫn cần chú ý phép chia cho 0 và các trường hợp mất dữ liệu khi chuyển về kiểu biến đích; cú pháp ngắn hơn không phải cơ chế validation.

## 13. Ứng dụng: chia thành nhóm đầy đủ và phần còn lại

Một ứng dụng gần gũi là chia **11 mục** thành các nhóm chứa tối đa **5 mục**, với `items >= 0`, `size > 0`:

~~~java
int items = 11;
int size = 5;

int fullGroups = items / size;
int remaining = items % size;
int totalGroups = fullGroups + (remaining == 0 ? 0 : 1);

System.out.println(fullGroups); // 2
System.out.println(remaining);  // 1
System.out.println(totalGroups); // 3
~~~

Giải thích: có 2 nhóm đầy đủ, còn dư 1 mục cần thêm 1 nhóm. Công thức này không tự áp dụng cho mọi cách chia có thể có dữ liệu âm, vì **ý nghĩa của nhóm âm và làm tròn số nhóm** cần được định nghĩa riêng.

Trước khi dùng `totalGroups` làm kích thước mảng hoặc số lượt xử lý, hãy đảm bảo miền hợp lệ (`items >= 0`, `size > 0`) và xét giới hạn integer nếu tính toán lớn.

## 14. Chương trình Java 17 hoàn chỉnh, chạy độc lập

Tạo file đúng tên **`IntegerDivisionDemo.java`**:

~~~java
import java.math.BigInteger;

public final class IntegerDivisionDemo {
    private IntegerDivisionDemo() {}

    static void showPair(int a, int b) {
        System.out.println("pair " + a + "/" + b
                + ": q=" + (a / b) + ", r=" + (a % b));
    }

    static int divideChecked(int a, int b) {
        if (b == 0) {
            throw new IllegalArgumentException("zero divisor");
        }
        if (a == Integer.MIN_VALUE && b == -1) {
            throw new ArithmeticException("int quotient overflow");
        }
        return a / b;
    }

    public static void main(String[] args) {
        showPair(7, 2);
        showPair(-7, 2);
        showPair(7, -2);
        showPair(-7, -2);
        showPair(-1, 2);

        System.out.println("int result=" + (7 / 2));
        System.out.println("widen after=" + ((double) (7 / 2)));
        System.out.println("double first=" + (((double) 7) / 2));
        System.out.println("double literal=" + (7 / 2.0));
        System.out.println("long operands=" + (7L / 2));

        byte a = 9;
        short b = 2;
        var inferred = a / b;
        System.out.println("byte/short promoted=" + inferred);

        System.out.println("remainder negative=" + (-7 % 2));
        System.out.println("identity=" + ((-7 / 2) * 2 + (-7 % 2)));
        System.out.println("floorDiv=" + Math.floorDiv(-7, 2));
        System.out.println("floorMod=" + Math.floorMod(-7, 2));
        System.out.println("floor reconstructed="
                + (Math.floorDiv(-7, 2) * 2 + Math.floorMod(-7, 2)));

        System.out.println("group left=" + (20 / 3 / 2));
        System.out.println("group right=" + (20 / (3 / 2)));
        System.out.println("shift positive=" + (7 >> 1));
        System.out.println("shift negative=" + (-7 >> 1));
        System.out.println("divide negative=" + (-7 / 2));

        int count = 20;
        count /= 3;
        System.out.println("compound int=" + count);
        byte small = 9;
        small /= 2;
        System.out.println("compound byte=" + small);

        System.out.println("min int / -1=" + (Integer.MIN_VALUE / -1));
        System.out.println("min int widened="
                + (((long) Integer.MIN_VALUE) / -1));
        System.out.println("min long / -1=" + (Long.MIN_VALUE / -1L));
        BigInteger exactLong = BigInteger.valueOf(Long.MIN_VALUE)
                .divide(BigInteger.valueOf(-1));
        System.out.println("min long BigInteger=" + exactLong);

        int zero = 0;
        try {
            int impossible = 7 / zero;
            System.out.println(impossible);
        } catch (ArithmeticException ex) {
            System.out.println("plain zero=ArithmeticException");
        }
        try {
            divideChecked(7, 0);
        } catch (IllegalArgumentException ex) {
            System.out.println("checked zero=" + ex.getMessage());
        }
        try {
            divideChecked(Integer.MIN_VALUE, -1);
        } catch (ArithmeticException ex) {
            System.out.println("checked overflow=" + ex.getMessage());
        }
        System.out.println("checked normal=" + divideChecked(-7, 2));

        System.out.println("floating / zero=" + (7.0 / 0.0));
        System.out.println("floating 0 / 0=" + (0.0 / 0.0));

        int items = 11;
        int size = 5;
        int full = items / size;
        int remaining = items % size;
        int groups = full + (remaining == 0 ? 0 : 1);
        System.out.println("full groups=" + full);
        System.out.println("remaining=" + remaining);
        System.out.println("total groups=" + groups);
    }
}
~~~

Yêu cầu **JDK 17 hoặc mới hơn** và terminal đang ở thư mục chứa `IntegerDivisionDemo.java`. Không cần Maven, Gradle hay thư viện bên ngoài JDK.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 IntegerDivisionDemo.java
java -cp . IntegerDivisionDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\IntegerDivisionDemo.java
java -cp . IntegerDivisionDemo
~~~

**Expected output chính xác:**

~~~text
pair 7/2: q=3, r=1
pair -7/2: q=-3, r=-1
pair 7/-2: q=-3, r=1
pair -7/-2: q=3, r=-1
pair -1/2: q=0, r=-1
int result=3
widen after=3.0
double first=3.5
double literal=3.5
long operands=3
byte/short promoted=4
remainder negative=-1
identity=-7
floorDiv=-4
floorMod=1
floor reconstructed=-7
group left=3
group right=20
shift positive=3
shift negative=-4
divide negative=-3
compound int=6
compound byte=4
min int / -1=-2147483648
min int widened=2147483648
min long / -1=-9223372036854775808
min long BigInteger=9223372036854775808
plain zero=ArithmeticException
checked zero=zero divisor
checked overflow=int quotient overflow
checked normal=-3
floating / zero=Infinity
floating 0 / 0=NaN
full groups=2
remaining=1
total groups=3
~~~

**Cách đối chiếu kết quả:**

1. Các dòng `pair` cho thấy thương **cắt về 0** và số dư **theo dấu số bị chia**.
2. `widen after` khác `double first`: chuyển kiểu **sau** chia không khôi phục phần lẻ.
3. `byte/short promoted` cho thấy hai toán hạng nhỏ vẫn tạo kết quả phép chia `int`.
4. `floorDiv`/`floorMod` khác `/`/`%` ở số âm, nhưng vẫn tái tạo đúng -7 trong ví dụ.
5. Hai nhóm `20 / 3 / 2` và `20 / (3 / 2)` khác nhau vì chia nguyên cắt sau **mỗi bước**.
6. Dịch bit `-7 >> 1` cho -4, khác `-7 / 2` cho -3.
7. Trường hợp `MIN_VALUE/-1` tràn mà Java **không tự ném exception**; hàm `divideChecked` chủ động phát hiện tràn.
8. Chia cho 0 kiểu nguyên ném exception; kiểu `double` cho Infinity hoặc NaN theo quy tắc số thực.
9. Phần chia nhóm cuối áp dụng cả thương và dư cho dữ liệu không âm.

## 15. Lỗi thường gặp

| Cách viết/ngộ nhận | Vì sao sai? | Hướng xử lý |
|---|---|---|
| `7 / 2` cho 3.5 | Hai toán hạng `int`; chia nguyên | Dùng `7.0 / 2` nếu muốn số thực |
| `double d = 7 / 2;` cho 3.5 | 3 được tạo ra trước rồi mới mở rộng | Cast một toán hạng **trước khi chia** |
| `(double)(7 / 2)` khôi phục .5 | Cast sau khi đã cắt | `((double)7)/2` |
| `-7 / 2` cho -4 | Java cắt về 0, không floor | Dùng `Math.floorDiv(-7, 2)` nếu cần floor |
| `-7 % 2` cho 1 | Dư Java theo dấu số bị chia | Dùng `Math.floorMod` nếu muốn dư không âm với mẫu dương |
| `20 / 3 / 2` luôn như `20 / (3/2)` | Chia nguyên và grouping khác | Giữ ngoặc có chủ đích |
| `long q = intA / intB` luôn chính xác trong long | Phép chia diễn ra trong int trước | Cast một toán hạng sang long khi cần |
| `byte q = byteA / byteB;` | Numeric promotion cho kết quả int | Nhận int hoặc cast có kiểm tra |
| `int q = 7 / 0` cho Infinity | Chia nguyên mẫu 0 ném exception | Kiểm tra mẫu, xử lý lỗi |
| `Integer.MIN_VALUE / -1` ném overflow | Java trả MIN_VALUE khi thương không vừa int | Kiểm tra riêng, dùng long khi thích hợp |
| `Long.MIN_VALUE / -1L` xử lý được bằng long | Thương dương vượt miền long | Dùng `BigInteger` khi cần giá trị exact |
| `x >> 1` luôn tương đương `x / 2` | Số âm có quy tắc khác | Giữ `/` nếu cần cắt về 0 |
| `value /= 2` luôn không mất thông tin | Chia nguyên và compound conversion có thể giảm giá trị | Xác nhận nghiệp vụ trước |
| Chia nhóm với `size = 0` | Mẫu số 0 | Validate `size > 0` |

**Mẹo debug:** luôn hỏi **toán hạng có kiểu gì → phép chia thuộc loại nào → dấu của số → mất phần lẻ ở bước nào → mẫu số có bằng 0 → thương có vượt miền?**

## 16. Bài tập có đáp án

### Bài A — số âm và dư

~~~java
System.out.println(-11 / 4);
System.out.println(-11 % 4);
~~~

**Đáp án:**

~~~text
-2
-3
~~~

Thương -2 vì cắt -2.75 về 0; dư -3 vì `-11 = (-2)*4 + (-3)`.

### Bài B — chia nguyên và chia thực

~~~java
int a = 9, b = 4;
double x = a / b;
double y = ((double) a) / b;
System.out.println(x);
System.out.println(y);
~~~

**Đáp án:**

~~~text
2.0
2.25
~~~

### Bài C — kiểm tra `floorDiv`

~~~java
System.out.println(-11 / 4);
System.out.println(Math.floorDiv(-11, 4));
System.out.println(Math.floorMod(-11, 4));
~~~

**Đáp án:**

~~~text
-2
-3
1
~~~

Vì `-11 = -3 * 4 + 1`.

### Bài D — kiểu biểu thức

~~~java
short a = 9;
short b = 2;
var q = a / b;
System.out.println(q);
~~~

**Đáp án:** in `4`, và `q` có kiểu **int**, không phải short.

### Bài E — chia cho 0

~~~java
int denominator = 0;
// int result = 9 / denominator;
~~~

**Đáp án:** nếu chạy dòng comment, xảy ra `ArithmeticException` trong phép chia nguyên. Phải validate mẫu số trước; không có infinity kiểu `int`.

### Bài F — overflow ở ranh giới

~~~java
int a = Integer.MIN_VALUE;
int result = a / -1;
System.out.println(result);
~~~

**Đáp án:** `-2147483648`, không ném exception. Nếu muốn thương chính xác, dùng `((long) a) / -1` để nhận `2147483648`.

### Bài G — chia thành nhóm

Có 14 vật phẩm và mỗi hộp chứa tối đa 6 vật phẩm. Tính số hộp đầy đủ, số vật phẩm còn lại, và số hộp cần dùng (dữ liệu không âm, sức chứa dương).

**Đáp án:**

~~~java
int items = 14, capacity = 6;
int full = items / capacity;  // 2
int rest = items % capacity;  // 2
int boxes = full + (rest == 0 ? 0 : 1); // 3
~~~

## 17. Checklist và kết luận

- [ ] Biết hai số nguyên chia nhau trả thương nguyên, cắt phần lẻ **về 0**.
- [ ] Phân biệt cắt về 0 với `Math.floorDiv` ở số âm.
- [ ] Biết dấu của thương và vì sao `-1 / 2 == 0`.
- [ ] Hiểu `int / int` được thực hiện trước khi gán vào `double`.
- [ ] Biết đổi một toán hạng sang `double` trước khi chia nếu cần số thực.
- [ ] Biết `byte`/`short`/`char` được nâng lên `int` trong phép chia.
- [ ] Nhớ quy tắc số dư `%` và mối liên hệ thương–dư.
- [ ] Phân biệt `Math.floorMod` với `%` ở dữ liệu âm.
- [ ] Biết dấu ngoặc làm thay đổi kết quả khi nhóm nhiều phép chia nguyên.
- [ ] Kiểm tra mẫu số khác 0 trước khi chia nguyên nếu cần xử lý chủ động.
- [ ] Hiểu trường hợp `Integer.MIN_VALUE / -1` và `Long.MIN_VALUE / -1L`.
- [ ] Không thay chia `/2` bằng dịch phải `>>1` nếu có số âm.
- [ ] Biết `/=` có thể thực hiện phép chia và chuyển kiểu ở biến đích.
- [ ] Chạy `IntegerDivisionDemo.java` bằng Java 17+ và so từng dòng expected output.

**Kết luận:** Toán tử `/` trên kiểu nguyên trong Java 17+ **cắt thương về 0**, kể cả khi một toán hạng âm. Muốn làm tròn xuống dùng `Math.floorDiv`; muốn giữ phần lẻ phải chọn toán hạng dấu phẩy động trước khi chia. Kết hợp hiểu đúng `%`, kiểu của biểu thức, chia cho 0 và trường hợp tràn `MIN_VALUE / -1` sẽ giúp bạn viết phép chia số nguyên chính xác, dự đoán được và an toàn theo yêu cầu nghiệp vụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="arithmetic-003">Ép kiểu trước phép chia để nhận kết quả số thực</a>

<details>
<summary>Xem chi tiết</summary>

# Ép kiểu trước phép chia để nhận kết quả số thực trong Java

> **Java core — language-basics / Số học**  
> **Môi trường:** JDK 17+; ví dụ một file Java độc lập, không cần thư viện ngoài.  
> **Mục tiêu:** Phân biệt phép chia nguyên với phép chia số thực; biết vì sao cần chuyển **toán hạng** sang `double`/`float` **trước dấu chia**, không phải ép kiểu kết quả sau khi phần lẻ đã mất.

## 1. Vì sao `7 / 2` không phải `3.5`?

Khi **hai toán hạng của `/` đều là kiểu số nguyên**, Java thực hiện phép **chia nguyên**, bỏ phần lẻ theo hướng về 0:

~~~java
int a = 7;
int b = 2;
int quotient = a / b; // 3, không phải 3.5.
~~~

Kể cả khi gán vào `double`, phép chia vẫn được thực hiện **trước phép gán**:

~~~java
double misleading = a / b; // 3.0, không phải 3.5.
~~~

Trình tự là: `a / b` có **kiểu `int`**, tính ra `3`; sau đó Java mới mở rộng `int 3` thành `double 3.0` để gán. Kiểu biến đích **không quay ngược lại để đổi phép toán đã xảy ra**.

Với `byte`, `short` hoặc `char`, Java còn nâng toán hạng nhỏ lên `int` qua *binary numeric promotion*, nên `byte / byte` cũng cho kết quả nguyên kiểu `int`.

## 2. Ép kiểu **trước** dấu chia mới giữ được phần lẻ

Muốn `7 / 2` ra `3.5`, hãy đổi **ít nhất một toán hạng** sang kiểu số thực **trước khi toán tử `/` chạy**:

~~~java
int a = 7;
int b = 2;

double r1 = (double) a / b; // 3.5
double r2 = a / (double) b; // 3.5
double r3 = a / 2.0;        // 3.5: literal 2.0 là double.
float  r4 = (float) a / b;  // 3.5f: kiểu biểu thức là float.
~~~

Quy tắc *binary numeric promotion* của toán tử `/` chọn kiểu chung: nếu có toán hạng `double` thì phép chia là `double`; nếu không có `double` nhưng có `float` thì phép chia là `float`; với các toán hạng nguyên, kết quả là `long` nếu có `long`, nếu không là `int`.

**Điểm cốt lõi:** `(double) a / b` biến `a` thành `double` **trước khi chia**. `a / (double) b` tương tự. Chỉ cần một toán hạng là số thực, không cần cast cả hai.

## 3. Ép kiểu **sau** phép chia không khôi phục được phần lẻ

Ba biểu thức trông gần giống nhưng khác kết quả:

| Biểu thức khi `a = 7`, `b = 2` | Kiểu phép chia | Kết quả |
| --- | --- | --- |
| `a / b` | `int` | `3` |
| `double result = a / b;` | `int`, rồi mở rộng khi gán | `3.0` |
| `(double) (a / b)` | `int`, rồi cast kết quả | `3.0` |
| `(double) a / b` | `double` | `3.5` |
| `a / (double) b` | `double` | `3.5` |
| `a / 2.0` | `double` | `3.5` |
| `(float) a / b` | `float` | `3.5f` |

Quan sát vị trí dấu ngoặc:

~~~java
double wrong = (double) (a / b); // Cast sau: 3.0.
double right = ((double) a) / b; // Cast trước: 3.5.
~~~

`(double)(a / b)` không phải “ép kiểu trước phép chia” chỉ vì chữ `(double)` nằm ở đầu dòng: toàn bộ `a / b` nằm **trong ngoặc**, nên bị tính như số nguyên rồi mới cast.

Một bẫy tương tự là thứ tự phép nhân:

~~~java
double wrongAgain = a / b * 1.0; // 3.0: chia nguyên trước.
double rightAgain = a * 1.0 / b; // 3.5: nhân với double trước.
~~~

Dùng `(double) a / b` thường biểu đạt mục đích trực tiếp hơn `a * 1.0 / b`.

## 4. Số âm, `long` và kiểu nguyên nhỏ

Chia nguyên âm vẫn bỏ phần lẻ theo hướng về **0**, không làm tròn xuống âm vô cực:

~~~java
int negative = -7;
System.out.println(negative / 2);           // -3
System.out.println((double) negative / 2);  // -3.5
~~~

Số nguyên `long` cũng là **kiểu nguyên**, nên `9L / 4L` cho **2**, không phải `2.25`:

~~~java
long total = 9L;
long parts = 4L;
double average = (double) total / parts; // 2.25.
~~~

Tương tự:

~~~java
byte small = 5;
byte divisor = 2;
int whole = small / divisor;              // 2 (kiểu int).
double fraction = (double) small / divisor; // 2.5.
~~~

**Lưu ý độ chính xác:** Ép `long` rất lớn sang `double` có thể **làm tròn số nguyên nguồn** trước phép chia (vì `double` chỉ có 53 bit chính xác phần trị). Cast trước giúp tạo kết quả số thực, **không bảo đảm chính xác tuyệt đối** với mọi số lớn.

## 5. Chương trình Java 17+ runnable

Tạo file **CastBeforeDivisionDemo.java**:

~~~java
public final class CastBeforeDivisionDemo {
    public static void main(String[] args) {
        int numerator = 7;
        int denominator = 2;

        System.out.println("integer=" + (numerator / denominator));

        double assigned = numerator / denominator;
        System.out.println("assignedDouble=" + assigned);

        double castAfter = (double) (numerator / denominator);
        double castBeforeLeft = (double) numerator / denominator;
        double castBeforeRight = numerator / (double) denominator;
        double literalDouble = numerator / 2.0;
        float castToFloat = (float) numerator / denominator;

        System.out.println("castAfter=" + castAfter);
        System.out.println("castBeforeLeft=" + castBeforeLeft);
        System.out.println("castBeforeRight=" + castBeforeRight);
        System.out.println("literalDouble=" + literalDouble);
        System.out.println("castToFloat=" + castToFloat);

        System.out.println("multiplyAfter=" +
                (numerator / denominator * 1.0));
        System.out.println("multiplyBefore=" +
                (numerator * 1.0 / denominator));

        int negative = -7;
        System.out.println("negativeInteger=" + (negative / 2));
        System.out.println("negativeReal=" + ((double) negative / 2));

        long big = 9L;
        long four = 4L;
        System.out.println("longInteger=" + (big / four));
        System.out.println("longReal=" + ((double) big / four));

        byte small = 5;
        byte pair = 2;
        System.out.println("byteInteger=" + (small / pair));
        System.out.println("byteReal=" + ((double) small / pair));

        int zero = 0;
        try {
            System.out.println("invalid=" + (numerator / zero));
        } catch (ArithmeticException ex) {
            System.out.println("integerZero=ArithmeticException");
        }

        System.out.println("floatingZero=" + ((double) numerator / zero));
        System.out.println("zeroOverZero=" + ((double) zero / zero));
        System.out.println("oneThird=" + (1.0 / 3));
    }
}
~~~

Tại thư mục chứa file, chạy bằng Bash, PowerShell hoặc Command Prompt:

~~~bash
javac -encoding UTF-8 CastBeforeDivisionDemo.java
java CastBeforeDivisionDemo
~~~

**Expected output chính xác:**

~~~text
integer=3
assignedDouble=3.0
castAfter=3.0
castBeforeLeft=3.5
castBeforeRight=3.5
literalDouble=3.5
castToFloat=3.5
multiplyAfter=3.0
multiplyBefore=3.5
negativeInteger=-3
negativeReal=-3.5
longInteger=2
longReal=2.25
byteInteger=2
byteReal=2.5
integerZero=ArithmeticException
floatingZero=Infinity
zeroOverZero=NaN
oneThird=0.3333333333333333
~~~

Đối chiếu ba dòng quan trọng: `assignedDouble=3.0` và `castAfter=3.0` cho thấy **phần lẻ đã bị loại bỏ trong phép chia nguyên**; `castBeforeLeft=3.5` cho thấy ép kiểu toán hạng trước dấu `/` đã tạo phép chia số thực.

## 6. Mẫu số bằng 0 và giới hạn của `double`

- Với `int`/`long`, chia hoặc lấy dư cho **0** ném `ArithmeticException`. Phải kiểm tra mẫu số nguyên nếu nó có thể bằng 0.
- Với `double`/`float`, phép chia cho **0.0** tuân theo số thực IEEE 754: `7.0 / 0.0` cho `Infinity`, `0.0 / 0.0` cho `NaN`, không ném `ArithmeticException`.
- Nếu hợp đồng yêu cầu giá trị hữu hạn, kiểm tra `Double.isFinite(value)` và quyết định cách xử lý mẫu số 0 theo nghiệp vụ **trước phép chia**.
- `double` có thể biểu diễn **xấp xỉ** kết quả như `1.0 / 3`, không phải mọi phân số đều chính xác trong hệ nhị phân. Dùng `BigDecimal` kèm `RoundingMode` hoặc `MathContext` khi cần quy tắc độ chính xác thập phân rõ ràng.

Ví dụ có guard thay vì chấp nhận `Infinity`:

~~~java
static double ratio(int numerator, int denominator) {
    if (denominator == 0) {
        throw new IllegalArgumentException("denominator must not be zero");
    }
    return (double) numerator / denominator;
}
~~~

Hàm có kết quả số thực khi mẫu số hợp lệ, nhưng vẫn buộc người gọi xử lý điều kiện chia cho 0 một cách có chủ đích.

## 7. Lỗi thường gặp và bài tập

| Sai lầm | Cách sửa |
| --- | --- |
| `double result = a / b;` khi a, b là `int` | `double result = (double) a / b;` |
| `(double) (a / b)` rồi kỳ vọng có phần lẻ | Cast một toán hạng **trước dấu chia** |
| `a / b * 1.0` và nghĩ phép chia sẽ là số thực | Đưa `1.0` vào trước phép chia hoặc dùng cast |
| Nghĩ `long / long` tự ra `double` | `long` vẫn là số nguyên; phải cast một toán hạng |
| Nghĩ cast bảo đảm chính xác mọi số nguyên lớn | `double` vẫn có giới hạn độ chính xác |
| Nghĩ phép chia số thực cho 0 ném `ArithmeticException` | Số thực có thể ra `Infinity`/`NaN`; cần kiểm tra riêng |

**Bài 1.** Cho `int a = 9, b = 4;`. Dự đoán `a / b`, `(double) (a / b)` và `(double) a / b`.

**Bài 2.** Giải thích vì sao `double d = 7 / 2;` cho 3.0 mà `double d = 7 / 2.0;` cho 3.5.

**Bài 3.** Cho `int a = -9, b = 4;`. So sánh chia nguyên `a / b` với `(double) a / b`.

**Bài 4.** Viết hàm tính trung bình từ `int sum` và `int count`, yêu cầu trả `double` và từ chối `count == 0`. Không dùng `(double) (sum / count)`.

**Bài 5.** Cho `long a = 9L, b = 4L;`. Tại sao `double x = a / b;` ra 2.0? Sửa để ra 2.25.

### Ghi nhớ

Muốn phép chia hai số nguyên có kết quả số thực, hãy **ép ít nhất một toán hạng sang `double`/`float` trước toán tử `/`**: `(double) a / b` hoặc `a / (double) b`. Ép kiểu **sau** như `(double) (a / b)` chỉ chuyển kết quả nguyên đã mất phần lẻ thành số thực. Luôn xét mẫu số 0 và độ chính xác số thực khi áp dụng vào chương trình thật.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="arithmetic-004">Dấu của phần dư với toán hạng âm</a>

<details>
<summary>Xem chi tiết</summary>

# Dấu của phần dư với toán hạng âm — Java 17+

Trong Java, toán tử **`%`** tính **phần dư của phép chia**, không phải phép toán modulo luôn cho kết quả không âm. Với hai số nguyên `a` và `b` (trong đó `b != 0`), Java chia số nguyên theo quy tắc **cắt phần thương về 0** (*truncation toward zero*), rồi tính phần dư:

~~~text
q = a / b      (thuong nguyen cat ve 0)
r = a % b
a = q * b + r  (dang thuc toan hoc)
~~~

**Quy tắc chính:** Phần dư nguyên khác 0 mang **cùng dấu với số bị chia `a`** (toán hạng trái), **không theo dấu số chia `b`** (toán hạng phải). Nếu chia hết, phần dư nguyên bằng `0`.

~~~java
System.out.println( 7 %  3); //  1
System.out.println(-7 %  3); // -1
System.out.println( 7 % -3); //  1
System.out.println(-7 % -3); // -1
~~~

Đừng nhầm `-7 % 3` với `Math.floorMod(-7, 3)`: chúng lần lượt là **`-1`** và **`2`**. Hai biểu thức dùng **quy tắc thương khác nhau**.

## 1. Bảng dấu của phần dư nguyên

| Số bị chia `a` | Số chia `b` | Thương nguyên `a / b` | Phần dư `a % b` | Kiểm tra |
| ---: | ---: | ---: | ---: | --- |
| 7 | 3 | 2 | 1 | `7 = 2 * 3 + 1` |
| −7 | 3 | −2 | −1 | `-7 = (-2) * 3 + (-1)` |
| 7 | −3 | −2 | 1 | `7 = (-2) * (-3) + 1` |
| −7 | −3 | 2 | −1 | `-7 = 2 * (-3) + (-1)` |
| −9 | 3 | −3 | 0 | Chia hết |
| 0 | −3 | 0 | 0 | Số bị chia bằng 0 |

Với số chia nguyên khác 0, độ lớn toán học của phần dư thỏa **`|r| < |b|`**; khi `r != 0` thì dấu của `r` là dấu của `a`. Lưu ý `Math.abs(Integer.MIN_VALUE)` vẫn có giá trị âm vì giới hạn biểu diễn `int`, nên không dùng `Math.abs` một cách mù quáng để kiểm chứng bất đẳng thức ở mọi biên.

### Vì sao `-7 % 3 == -1`?

Java lấy thương nguyên của `-7 / 3` là **`-2`**, không phải `-3`:

~~~text
-7 / 3 = -2       (cat ve 0)
-7 = (-2) * 3 + r
 r = -7 - (-6) = -1
~~~

Nếu thay thương bằng `floor(-7/3) = -3` thì phần dư tương ứng là 2. Đó là cách tiếp cận của **floor division / floor modulus**, không phải cách Java tính `%` nguyên.

## 2. Chương trình Java 17+ chạy được

Lưu file UTF-8 tên **`RemainderSignsDemo.java`**:

~~~java
public class RemainderSignsDemo {
    private static void show(int a, int b) {
        System.out.println(a + "/" + b + "=" + (a / b)
                + ",remainder=" + (a % b));
    }

    public static void main(String[] args) {
        System.out.println("== integer signs ==");
        show(7, 3);
        show(-7, 3);
        show(7, -3);
        show(-7, -3);
        show(-9, 3);
        show(0, -3);

        int dividend = -7;
        int divisor = 3;
        int quotient = dividend / divisor;
        int remainder = dividend % divisor;
        System.out.println("recombined="
                + (quotient * divisor + remainder));

        System.out.println("== floor modulus ==");
        System.out.println("remainderNeg=" + (-7 % 3));
        System.out.println("floorModNeg=" + Math.floorMod(-7, 3));
        System.out.println("floorModPosNeg=" + Math.floorMod(7, -3));
        System.out.println("floorModNegNeg=" + Math.floorMod(-7, -3));

        int slots = 3;
        int rawIndex = -7 % slots;
        int safeIndex = Math.floorMod(-7, slots);
        System.out.println("rawIndex=" + rawIndex);
        System.out.println("safeIndex=" + safeIndex);

        System.out.println("== edge cases ==");
        System.out.println("longRemainder=" + (-7L % 3L));
        System.out.println("minByMinusOne="
                + (Integer.MIN_VALUE % -1));

        try {
            int invalid = 7 % 0;
            System.out.println("unexpected=" + invalid);
        } catch (ArithmeticException ex) {
            System.out.println("integerZero=ArithmeticException");
        }

        System.out.println("== floating-point ==");
        System.out.println("doubleNeg=" + (-7.5 % 3.0));
        System.out.println("doubleNegDivisor=" + (7.5 % -3.0));
        System.out.println("negativeZero=" + (-0.0 % 3.0));
        System.out.println("floatingZeroIsNaN="
                + Double.isNaN(7.0 % 0.0));
        System.out.println("infiniteDividendIsNaN="
                + Double.isNaN(Double.POSITIVE_INFINITY % 3.0));
        System.out.println("infiniteDivisor="
                + (-7.5 % Double.POSITIVE_INFINITY));
    }
}
~~~

Với **JDK 17 trở lên**, mở PowerShell hoặc Bash trong thư mục chứa tệp và chạy:

~~~bash
java -version
javac -version
javac -encoding UTF-8 RemainderSignsDemo.java
java RemainderSignsDemo
~~~

**Expected output chính xác** từ lệnh `java RemainderSignsDemo` (không tính đầu ra phiên bản JDK):

~~~text
== integer signs ==
7/3=2,remainder=1
-7/3=-2,remainder=-1
7/-3=-2,remainder=1
-7/-3=2,remainder=-1
-9/3=-3,remainder=0
0/-3=0,remainder=0
recombined=-7
== floor modulus ==
remainderNeg=-1
floorModNeg=2
floorModPosNeg=-2
floorModNegNeg=-1
rawIndex=-1
safeIndex=2
== edge cases ==
longRemainder=-1
minByMinusOne=0
integerZero=ArithmeticException
== floating-point ==
doubleNeg=-1.5
doubleNegDivisor=1.5
negativeZero=-0.0
floatingZeroIsNaN=true
infiniteDividendIsNaN=true
infiniteDivisor=-7.5
~~~

## 3. Toán hạng âm nằm ở đâu?

Với biểu thức `a % b`:

- `a`: **số bị chia** (*dividend*), nằm **bên trái** dấu `%`.
- `b`: **số chia** (*divisor*), nằm **bên phải** dấu `%`.
- Dấu của phần dư nguyên khác 0 tuân theo **`a`**, dù `b` dương hay âm.

Ví dụ cùng số bị chia `7`:

~~~java
int a = 7;
System.out.println(a % 3);  // 1.
System.out.println(a % -3); // 1.
~~~

Đổi dấu **số chia** làm thay đổi thương nhưng **không đảo dấu phần dư**. Ngược lại, đổi dấu số bị chia từ 7 sang −7 làm phần dư đổi dấu khi khác 0:

~~~java
System.out.println(-7 % 3);  // -1.
System.out.println(-7 % -3); // -1.
~~~

Không được kết luận rằng `%` “luôn trả số dương trong miền 0 đến `b-1`”. Điều đó chỉ đúng trong một số điều kiện (ví dụ `a >= 0` và `b > 0`), chứ **không đúng khi `a` âm**.

## 4. Khác nhau giữa `%` và `Math.floorMod()`

Java cung cấp `Math.floorMod` để tính phần dư tương ứng với **thương làm tròn xuống âm vô cực** (*floor division*). Với số nguyên:

~~~text
%:            a = (a / b) * b + (a % b)         (thuong cat ve 0)
floorMod:     a = floorDiv(a,b) * b + floorMod(a,b)
~~~

| Biểu thức | Kết quả `%` | Kết quả `Math.floorMod` |
| --- | ---: | ---: |
| `-7` và `3` | −1 | 2 |
| `7` và `-3` | 1 | −2 |
| `-7` và `-3` | −1 | −1 |
| `7` và `3` | 1 | 1 |

Với **số chia dương**, `Math.floorMod(a, b)` có kết quả trong khoảng từ **0 đến `b - 1`**, nên hữu ích khi cần chuẩn hóa chỉ số vòng, số thứ tự tuần hoàn hoặc nhóm theo một số lượng ô cố định.

~~~java
int slotCount = 3;
int raw = -7 % slotCount;              // -1: chi so am.
int index = Math.floorMod(-7, slotCount); // 2: trong 0..2.
~~~

Nếu `b` âm, `Math.floorMod(a, b)` cho kết quả **bằng 0 hoặc mang dấu của số chia** (có thể âm). Vì vậy khi dùng `floorMod` để tạo chỉ số mảng, cần bảo đảm **modulus dương**.

**Không nên tự viết `(a % n + n) % n` như công thức vạn năng**, bởi phép cộng trung gian có thể tràn `int` khi `n` lớn. `Math.floorMod(a, n)` thể hiện đúng ý đồ hơn cho `n > 0`. Nếu `n == 0`, `floorMod` nguyên cũng ném `ArithmeticException`.

## 5. Lưu ý biên của `int` và `long`

Các phép `%` giữa `int` và `long` được thực hiện với các toán hạng số nguyên sau khi Java áp dụng quy tắc nâng kiểu số thích hợp. Kết quả có kiểu số nguyên tương ứng:

~~~java
int i = -7 % 3;     // int -1.
long j = -7L % 3L;  // long -1.
~~~

Một tình huống biên dễ gây ngạc nhiên:

~~~java
int rem = Integer.MIN_VALUE % -1; // 0.
~~~

Về toán học, `Integer.MIN_VALUE` chia hết cho −1, nên phần dư bằng 0. Phép chia `Integer.MIN_VALUE / -1` riêng rẽ tạo thương không biểu diễn được trong `int` (Java xử lý tràn theo ngữ nghĩa số nguyên), nhưng **toán tử `%` vẫn trả 0 và không ném ngoại lệ**.

**Cảnh báo chia cho 0:**

~~~java
// int bad = 7 % 0; // Runtime ArithmeticException.
int divisor = 3;
if (divisor != 0) {
    System.out.println(7 % divisor); // 1.
}
~~~

Với `%` nguyên, số chia **bằng 0** ném `ArithmeticException`. Số chia âm **không** ném ngoại lệ chỉ vì dấu âm.

## 6. `%` cho `float` và `double` có khác không?

Có. Toán tử `%` cũng dùng được với kiểu số thực. Nó vẫn tuân theo logic phần dư dựa trên **thương cắt về 0**, **không phải** phép `Math.IEEEremainder` (dùng quy tắc khác cho thương). Khi các toán hạng hữu hạn và số chia khác 0, phần dư khác 0 thường mang dấu số bị chia.

~~~java
System.out.println(-7.5 % 3.0);  // -1.5.
System.out.println(7.5 % -3.0);  //  1.5.
~~~

Nhưng số thực IEEE 754 có những trường hợp đặc biệt:

| Biểu thức | Kết quả | Giải thích |
| --- | --- | --- |
| `-7.5 % 3.0` | `-1.5` | Phần dư mang dấu số bị chia |
| `7.5 % -3.0` | `1.5` | Dấu số chia không quyết định dấu phần dư |
| `-0.0 % 3.0` | `-0.0` | Số thực phân biệt âm không và dương không |
| `7.0 % 0.0` | `NaN` | **Không** ném `ArithmeticException` như số nguyên |
| `Double.POSITIVE_INFINITY % 3.0` | `NaN` | Số bị chia vô cực |
| `-7.5 % Double.POSITIVE_INFINITY` | `-7.5` | Số chia vô cực, số bị chia hữu hạn |

Đừng nhầm `-0.0` (giá trị số thực có bit dấu) với `0` của kiểu `int`: phần dư nguyên không có trạng thái “âm không”.

Khi logic chương trình cần kiểm soát số thực không hợp lệ, cân nhắc `Double.isFinite(...)`, `Double.isNaN(...)` và yêu cầu làm tròn/độ chính xác của bài toán. Không dùng quy tắc “chắc chắn ném exception khi modulo 0” chung cho cả số nguyên và số thực.

## 7. Những ngộ nhận thường gặp

| Ngộ nhận | Cách hiểu đúng |
| --- | --- |
| “`-7 % 3` bằng 2.” | Trong Java, kết quả là **−1** |
| “Phần dư theo dấu của số chia.” | Phần dư **nguyên khác 0 theo dấu số bị chia** |
| “`7 % -3` phải âm.” | Kết quả là **1** |
| “`%` luôn trả chỉ số không âm.” | Không, nếu số bị chia âm |
| “`%` và `Math.floorMod` luôn giống nhau.” | Khác nhau khi dấu và cách lấy thương làm phần dư khác |
| “Số chia âm gây `ArithmeticException`.” | Chỉ chia cho **0** mới gây lỗi số học này với số nguyên |
| “`double % 0.0` cũng ném `ArithmeticException`.” | Kết quả là `NaN` |
| “Dấu âm của số 0 không đáng phân biệt với `double`.” | Có thể nhận được `-0.0` như phần dư số thực |
| “`Math.abs` luôn trả số không âm cho mọi `int`.” | `Math.abs(Integer.MIN_VALUE)` vẫn âm do tràn |

## 8. Bài tập tự kiểm tra

1. `-10 % 3` bằng bao nhiêu?  
   *Đáp án:* **−1** vì `-10 / 3` cho thương nguyên −3 và `-10 - (-3)*3 = -1`.
2. `10 % -3` bằng bao nhiêu?  
   *Đáp án:* **1**: số bị chia dương.
3. `-10 % -3` bằng bao nhiêu?  
   *Đáp án:* **−1**: số bị chia âm.
4. `-12 % 4` bằng bao nhiêu?  
   *Đáp án:* **0** vì chia hết.
5. `Math.floorMod(-10, 3)` và `-10 % 3` khác nhau thế nào?  
   *Đáp án:* **2** so với **−1**; `floorMod` với số chia dương giúp chuẩn hóa phần dư không âm.
6. `int index = -1 % 5;` có phù hợp để truy cập mảng năm phần tử không?  
   *Đáp án:* Không; `index == -1` là chỉ số âm. Dùng `Math.floorMod(-1, 5) == 4` nếu cần vòng chỉ số.
7. `7.0 % 0.0` có ném `ArithmeticException` không?  
   *Đáp án:* Không; kết quả số thực là `NaN`, khác `7 % 0` nguyên.
8. `Integer.MIN_VALUE % -1` trả gì?  
   *Đáp án:* **0**, vì số bị chia chia hết cho −1.

## Checklist

- [ ] Phân biệt **số bị chia bên trái** và **số chia bên phải** dấu `%`.
- [ ] Nhớ `/` nguyên cắt thương về 0, nên phần dư nguyên khác 0 theo **dấu số bị chia**.
- [ ] Dùng bảng dấu để kiểm tra bốn trường hợp dương/âm của hai toán hạng.
- [ ] Không lấy `%` trực tiếp làm chỉ số không âm nếu dữ liệu có thể âm.
- [ ] Với `n > 0`, cân nhắc `Math.floorMod(a, n)` để có phần dư từ 0 đến `n - 1`.
- [ ] Kiểm tra số chia bằng 0 trước `%` nguyên.
- [ ] Phân biệt `%` số thực với số nguyên, nhất là `NaN`, vô cực và `-0.0`.
- [ ] Không suy ra rằng việc đổi dấu số chia sẽ tự đổi dấu phần dư.

**Kết luận:** Trong Java, `%` là **remainder theo thương cắt về 0**. Với số nguyên, **dấu phần dư khác 0 đi theo toán hạng trái**. Khi cần modulo không âm để tính chỉ số vòng, dùng `Math.floorMod(a, n)` với **`n > 0`** thay vì giả định `a % n` luôn không âm.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="arithmetic-005">Chia số nguyên cho không gây ArithmeticException</a>

<details>
<summary>Xem chi tiết</summary>

# Chia số nguyên cho không gây ArithmeticException — Java 17+

## 1. Hiện tượng cần hiểu

Trong Java, **phép chia số nguyên bằng toán tử `/` với số chia bằng 0** ném `java.lang.ArithmeticException` khi biểu thức được đánh giá.

~~~java
int numerator = 10;
int denominator = 0;

// int result = numerator / denominator;
// Nếu chạy: ArithmeticException vì phép chia số nguyên cho 0.
~~~

Đây là **lỗi lúc chạy (runtime exception)**, không phải kết quả nguyên đặc biệt như `Infinity`. Không có giá trị `int` hay `long` nào đại diện cho thương của một phép chia nguyên cho 0.

Một ví dụ xử lý có kiểm soát:

~~~java
int numerator = 10;
int denominator = 0;

try {
    int quotient = numerator / denominator;
    System.out.println(quotient);
} catch (ArithmeticException ex) {
    System.out.println("Cannot divide integers by zero");
}
~~~

**Expected output:**

~~~text
Cannot divide integers by zero
~~~

**Kết luận chính:** phải **bảo đảm số chia khác 0 trước khi chia**, hoặc có hợp đồng xử lý lỗi rõ ràng. `try/catch` có thể xử lý ngoại lệ nhưng **không biến phép chia cho 0 thành phép tính hợp lệ**.

## 2. Kiểu số nguyên nào chịu quy tắc này?

Trong Java 17+, phép chia dùng kiểu toán hạng sau các quy tắc *binary numeric promotion*. Với các kiểu số nguyên, khi số chia bằng 0, phép `/` và `%` đều ném `ArithmeticException`.

| Trường hợp | Kiểu biểu thức | Số chia bằng 0 | Kết quả |
|---|---|---|---|
| `int / int` | `int` | `10 / 0` | `ArithmeticException` |
| `long / long` | `long` | `10L / 0L` | `ArithmeticException` |
| `byte / byte` | `int` | Hai `byte`, mẫu bằng 0 | `ArithmeticException` |
| `short / short` | `int` | Hai `short`, mẫu bằng 0 | `ArithmeticException` |
| `char / int` | `int` | Mẫu `int` bằng 0 | `ArithmeticException` |
| `int % int` | `int` | `10 % 0` | `ArithmeticException` |
| `long % long` | `long` | `10L % 0L` | `ArithmeticException` |

Ví dụ hợp lệ khi mẫu khác 0:

~~~java
byte a = 9;
byte b = 2;

int quotient = a / b; // byte được nâng thành int khi chia
int remainder = a % b;

System.out.println(quotient);  // 4
System.out.println(remainder); // 1
~~~

**Đừng lẫn nguyên nhân:** nếu compiler từ chối `byte result = a / b;`, đó là vấn đề **chuyển kiểu int về byte**. Nếu biểu thức `a / b` chạy với `b == 0`, đó mới là vấn đề **chia nguyên cho 0**.

## 3. Phép dư `%` cũng không chấp nhận số chia 0

Nhiều người chỉ kiểm tra toán tử `/` mà bỏ qua `%`:

~~~java
int count = 10;
int groupSize = 0;

// int quotient = count / groupSize;  // Ném ArithmeticException
// int remainder = count % groupSize; // Cũng ném ArithmeticException
~~~

Với mẫu số khác 0, hai phép toán liên quan nhau qua quy tắc thương và dư của Java. Ví dụ:

~~~java
System.out.println(10 / 3); // 3
System.out.println(10 % 3); // 1
~~~

Tuy nhiên, cả thương lẫn dư đều **không được định nghĩa là giá trị số nguyên bình thường khi mẫu bằng 0**.

Một hệ thống chia dữ liệu theo batch có thể cần đồng thời `items / batchSize` và `items % batchSize`. Hãy **validate `batchSize > 0` một lần ở ranh giới** trước cả hai phép toán; không đợi ngoại lệ xảy ra giữa chừng sau khi đã cập nhật trạng thái khác.

## 4. Khác hoàn toàn với phép chia dấu phẩy động

Nếu ít nhất một toán hạng là `float` hoặc `double`, phép chia sử dụng số học dấu phẩy động. Chia cho zero trong floating-point có thể trả **Infinity**, **-Infinity** hoặc **NaN**, không ném `ArithmeticException` chỉ vì mẫu bằng 0.

~~~java
System.out.println(10.0 / 0.0);  // Infinity
System.out.println(-10.0 / 0.0); // -Infinity
System.out.println(0.0 / 0.0);   // NaN
~~~

So sánh:

| Biểu thức | Quy tắc | Kết quả |
|---|---|---|
| `10 / 2` | Chia nguyên | `5` |
| `10 / 0` | Chia nguyên | `ArithmeticException` |
| `10L / 0L` | Chia `long` | `ArithmeticException` |
| `10.0 / 2.0` | Chia `double` | `5.0` |
| `10.0 / 0.0` | Chia `double` | `Infinity` |
| `-10.0 / 0.0` | Chia `double` | `-Infinity` |
| `0.0 / 0.0` | Chia `double` | `NaN` |

**Không “sửa” lỗi chia nguyên cho 0 bằng cách ép sang `double` một cách máy móc.** Một giá trị Infinity hoặc NaN có thể khiến kết quả downstream sai nghiêm trọng nếu nghiệp vụ yêu cầu giá trị hữu hạn.

Nếu kết quả cần hữu hạn, kiểm tra mẫu đầu vào và sau phép tính xem xét `Double.isFinite(result)` theo hợp đồng ứng dụng.

## 5. Kiểm tra mẫu số trước khi thực hiện phép chia

Cách dễ đọc nhất là một **guard clause**:

~~~java
static int divideNonZero(int numerator, int denominator) {
    if (denominator == 0) {
        throw new IllegalArgumentException("denominator must not be zero");
    }
    return numerator / denominator;
}
~~~

Ở đây `IllegalArgumentException` là một **quyết định thiết kế API** nhằm báo đầu vào không hợp lệ. Nếu bỏ kiểm tra và thực hiện phép chia, Java sẽ tự ném `ArithmeticException`.

Hai trường hợp có hợp đồng khác nhau:

- **Không hợp lệ do tham số:** chủ động ném `IllegalArgumentException`, giúp người gọi biết dữ liệu đầu vào đã bị từ chối.
- **Phép toán gặp điều kiện số học bất hợp lệ:** để `ArithmeticException` phát sinh, hoặc bắt ngoại lệ nếu có chính sách phục hồi.

Điều quan trọng là **nhất quán** và mô tả cho caller biết cần xử lý gì.

### Ví dụ không cần exception: chỉ thực hiện khi hợp lệ

~~~java
int numerator = 10;
int denominator = 0;

if (denominator != 0) {
    int quotient = numerator / denominator;
    System.out.println(quotient);
} else {
    System.out.println("No valid quotient");
}
~~~

**Expected output:**

~~~text
No valid quotient
~~~

Không có “thương mặc định” nào mà ngôn ngữ tự quyết giúp bạn. Nếu ứng dụng muốn trả giá trị dự phòng, phải **định nghĩa rõ** khi nào được dùng dự phòng và phân biệt nó với kết quả tính toán thật.

## 6. Không lấy `0` làm thương dự phòng nếu 0 là dữ liệu hợp lệ

Một API như sau dễ gây sai nghiệp vụ:

~~~java
static int unsafeFallback(int a, int b) {
    return b == 0 ? 0 : a / b;
}
~~~

Với `1 / 2`, thương nguyên **thật sự** cũng bằng 0. Vì vậy caller không thể phân biệt:

- `0` do kết quả phép chia hợp lệ `1 / 2`;
- `0` do phép chia bất hợp lệ `1 / 0` bị âm thầm thay thế.

Nếu việc không có thương là tình huống bình thường, có thể dùng `OptionalInt`:

~~~java
static java.util.OptionalInt tryDivide(int a, int b) {
    return b == 0
            ? java.util.OptionalInt.empty()
            : java.util.OptionalInt.of(a / b);
}
~~~

Cách sử dụng:

~~~java
System.out.println(tryDivide(1, 2).orElse(-1)); // 0
System.out.println(tryDivide(1, 0).isEmpty());  // true
~~~

`OptionalInt.empty()` diễn đạt **không có kết quả**, khác `OptionalInt.of(0)` biểu diễn thương bằng 0 hợp lệ.

**Lưu ý:** hàm `tryDivide` trên xử lý mẫu số 0, nhưng chưa yêu cầu phát hiện trường hợp tràn thương `Integer.MIN_VALUE / -1`. Nếu nghiệp vụ cần kết quả toán học chính xác trong miền int, thêm kiểm tra như phần dưới.

## 7. Tràn thương khác với chia cho 0

Trường hợp `Integer.MIN_VALUE / -1` trả `-2147483648` mà **không ném exception**, dù thương toán học là `2147483648` nằm ngoài miền `int`. Một guard chỉ kiểm tra `b == 0` **không bảo đảm** thương luôn chính xác.

~~~java
static int divideExactInt(int a, int b) {
    if (b == 0) throw new IllegalArgumentException("zero denominator");
    if (a == Integer.MIN_VALUE && b == -1)
        throw new ArithmeticException("int quotient overflow");
    return a / b;
}
~~~

Đây là chính sách nghiệp vụ tách **mẫu số không hợp lệ** và **thương vượt miền**. Bài tràn số xử lý tổng quát `Math.*Exact` và `BigInteger`; bài này tập trung vào hợp đồng lỗi khi chia.

## 8. `Math.floorDiv`, `Math.floorMod` cũng cần mẫu khác 0

Java có các phương thức:

~~~java
System.out.println(Math.floorDiv(-7, 2)); // -4
System.out.println(Math.floorMod(-7, 2)); // 1
~~~

Chúng khác `/` và `%` ở quy tắc thương/dư khi có số âm. Tuy nhiên, **không phương thức nào cho phép chia số nguyên cho 0**:

~~~java
int divisor = 0;
// Math.floorDiv(7, divisor); // ArithmeticException
// Math.floorMod(7, divisor); // ArithmeticException
~~~

`Math.floorDiv` không phải “phiên bản an toàn với zero” của `/`. Nếu dùng `floorDiv` cho xử lý timestamp, chia ô lưới hoặc chỉ số nhóm, vẫn phải validate mẫu đầu vào.

## 9. Short-circuit bảo vệ một biểu thức chia nguyên

Toán tử logic `&&` chỉ đánh giá vế phải nếu vế trái là `true`. Ta có thể tận dụng điều đó:

~~~java
int denominator = 0;
boolean largeQuotient = (denominator != 0)
        && (10 / denominator > 3);

System.out.println(largeQuotient); // false, không thực hiện phép chia
~~~

Vế trái false làm Java **bỏ qua** biểu thức `10 / denominator > 3`, nhờ vậy không có chia cho 0.

Tương tự, `||` có thể bỏ qua vế phải khi vế trái `true`:

~~~java
boolean invalidOrLarge = (denominator == 0)
        || (10 / denominator > 3);

System.out.println(invalidOrLarge); // true, không thực hiện phép chia
~~~

**Không nhầm `&&` với `&`, `||` với `|` trên boolean:** các toán tử `&` và `|` **đánh giá cả hai toán hạng**. Đổi `&&` thành `&` trong ví dụ guard có thể khiến phép chia bị thực hiện và ném lỗi.

Nếu logic quan trọng hoặc có nhiều thao tác, tách `if` rõ ràng thường dễ đọc hơn một biểu thức boolean lồng nhau.

## 10. Wrapper `Integer` có thể ném lỗi khác trước khi chia

Java cho phép dùng wrapper nhờ *auto-unboxing*:

~~~java
Integer boxedNumerator = 10;
Integer boxedDenominator = 2;

System.out.println(boxedNumerator / boxedDenominator); // 5
~~~

Nhưng nếu mẫu là `null`:

~~~java
Integer boxedDenominator = null;
// int value = 10 / boxedDenominator;
// Runtime: NullPointerException khi unbox null.
~~~

Đây là **`NullPointerException`**, không phải `ArithmeticException`. Do đó cần phân biệt:

| Dữ liệu mẫu | Khi tính `10 / boxedDenominator` |
|---|---|
| `Integer.valueOf(2)` | Trả `5` |
| `Integer.valueOf(0)` | Unbox thành 0 rồi ném `ArithmeticException` |
| `null` | Ném `NullPointerException` lúc unboxing |

Một chính sách nhập liệu đơn giản:

~~~java
static int divideNullable(int numerator, Integer denominator) {
    if (denominator == null) {
        throw new IllegalArgumentException("denominator is missing");
    }
    if (denominator == 0) {
        throw new IllegalArgumentException("denominator must not be zero");
    }
    return numerator / denominator;
}
~~~

Sau các guard, compiler vẫn tự unbox wrapper khi cần chia. Nhưng bạn đã phân biệt rõ **thiếu dữ liệu** và **giá trị 0**.

## 11. Khi exception xảy ra, tác dụng phụ trước đó không tự rollback

Java đánh giá các phần của biểu thức theo thứ tự được ngôn ngữ quy định. Một phép chia cho 0 có thể làm việc xử lý **dừng giữa chừng**:

~~~java
int count = 0;
int denominator = 0;

try {
    int value = (++count) / denominator;
    System.out.println(value);
} catch (ArithmeticException ex) {
    System.out.println("counter after failure=" + count);
}
~~~

**Expected output:**

~~~text
counter after failure=1
~~~

`++count` đã tăng bộ đếm trước khi phép chia gặp mẫu 0. `catch` **không khôi phục** `count` về 0.

Với dữ liệu quan trọng, hãy **validate tất cả đầu vào liên quan trước khi thực hiện thay đổi trạng thái**, hoặc dùng cơ chế giao dịch/rollback thích hợp. `try/catch` chỉ điều khiển luồng xử lý ngoại lệ, không tự biến một nhóm thao tác thành *atomic transaction*.

## 12. Chọn xử lý: kiểm tra trước, ném ngoại lệ hay trả trạng thái?

| Yêu cầu nghiệp vụ | Cách thường phù hợp |
|---|---|
| Mẫu số luôn bắt buộc khác 0 | Kiểm tra `b != 0` ở ranh giới; reject đầu vào sai |
| Đây là lỗi lập trình, không có đường phục hồi | Cho exception lan lên tầng có trách nhiệm xử lý |
| Người dùng nhập mẫu có thể sai | Validate, hiển thị lỗi có nghĩa và cho nhập lại |
| “Không có kết quả” là trạng thái hợp lệ | `OptionalInt` hoặc kiểu kết quả có trạng thái |
| Chỉ cần phép chia nguyên theo Java | `a / b`, sau khi bảo đảm `b != 0` |
| Cần thương int không được overflow | Kiểm tra thêm `a == MIN_VALUE && b == -1` |
| Muốn chia số thực | Chọn toán hạng `double`/`float`; vẫn kiểm tra tính hữu hạn khi cần |
| Muốn chia với quy tắc floor | `Math.floorDiv` và validate mẫu khác 0 |

**Không nên nuốt exception rồi âm thầm trả 0** trừ khi ứng dụng có một chính sách dự phòng rõ ràng, được tài liệu hóa và kiểm thử.

## 13. Chương trình Java 17 hoàn chỉnh — runnable

Lưu tệp đúng tên **`IntegerDivideByZeroDemo.java`**:

~~~java
import java.math.BigInteger;
import java.util.OptionalInt;

public final class IntegerDivideByZeroDemo {
    private IntegerDivideByZeroDemo() {}

    static int divideNonZero(int a, int b) {
        if (b == 0) {
            throw new IllegalArgumentException("denominator must not be zero");
        }
        return a / b;
    }

    static int divideExactInt(int a, int b) {
        if (b == 0) {
            throw new IllegalArgumentException("zero denominator");
        }
        if (a == Integer.MIN_VALUE && b == -1) {
            throw new ArithmeticException("int quotient overflow");
        }
        return a / b;
    }

    static OptionalInt tryDivide(int a, int b) {
        return b == 0 ? OptionalInt.empty() : OptionalInt.of(a / b);
    }

    static int divideNullable(int a, Integer b) {
        if (b == null) {
            throw new IllegalArgumentException("denominator is missing");
        }
        if (b == 0) {
            throw new IllegalArgumentException("denominator must not be zero");
        }
        return a / b;
    }

    public static void main(String[] args) {
        System.out.println("valid quotient=" + (10 / 3));
        System.out.println("valid remainder=" + (10 % 3));
        System.out.println("long quotient=" + (10L / 2L));

        byte small = 9;
        byte two = 2;
        System.out.println("byte division=" + (small / two));

        int zero = 0;
        try {
            int result = 10 / zero;
            System.out.println(result);
        } catch (ArithmeticException ex) {
            System.out.println("int / zero=ArithmeticException");
        }

        try {
            int result = 10 % zero;
            System.out.println(result);
        } catch (ArithmeticException ex) {
            System.out.println("int % zero=ArithmeticException");
        }

        try {
            long result = 10L / (long) zero;
            System.out.println(result);
        } catch (ArithmeticException ex) {
            System.out.println("long / zero=ArithmeticException");
        }

        try {
            System.out.println(Math.floorDiv(10, zero));
        } catch (ArithmeticException ex) {
            System.out.println("floorDiv zero=ArithmeticException");
        }

        System.out.println("floating positive=" + (10.0 / 0.0));
        System.out.println("floating negative=" + (-10.0 / 0.0));
        System.out.println("floating undefined=" + (0.0 / 0.0));

        System.out.println("guarded normal=" + divideNonZero(10, 2));
        try {
            divideNonZero(10, zero);
        } catch (IllegalArgumentException ex) {
            System.out.println("guarded invalid=" + ex.getMessage());
        }

        System.out.println("optional valid zero="
                + tryDivide(1, 2).orElse(-1));
        System.out.println("optional no result=" + tryDivide(1, 0).isEmpty());

        boolean safeAnd = (zero != 0) && (10 / zero > 2);
        boolean safeOr = (zero == 0) || (10 / zero > 2);
        System.out.println("short circuit and=" + safeAnd);
        System.out.println("short circuit or=" + safeOr);

        Integer boxedZero = Integer.valueOf(0);
        Integer missing = null;
        try {
            int result = 10 / boxedZero;
            System.out.println(result);
        } catch (ArithmeticException ex) {
            System.out.println("boxed zero=ArithmeticException");
        }
        try {
            int result = 10 / missing;
            System.out.println(result);
        } catch (NullPointerException ex) {
            System.out.println("boxed null=NullPointerException");
        }
        try {
            divideNullable(10, missing);
        } catch (IllegalArgumentException ex) {
            System.out.println("nullable rejected=" + ex.getMessage());
        }

        System.out.println("min / -1=" + (Integer.MIN_VALUE / -1));
        System.out.println("min widened="
                + (((long) Integer.MIN_VALUE) / -1));
        try {
            divideExactInt(Integer.MIN_VALUE, -1);
        } catch (ArithmeticException ex) {
            System.out.println("checked overflow=" + ex.getMessage());
        }
        BigInteger exact = BigInteger.valueOf(Long.MIN_VALUE)
                .divide(BigInteger.valueOf(-1));
        System.out.println("big exact=" + exact);

        int count = 0;
        try {
            int result = (++count) / zero;
            System.out.println(result);
        } catch (ArithmeticException ex) {
            System.out.println("counter after failure=" + count);
        }
    }
}
~~~

**Yêu cầu:** JDK 17+; terminal mở tại thư mục chứa file. Không cần Maven/Gradle hoặc thư viện ngoài JDK.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 IntegerDivideByZeroDemo.java
java -cp . IntegerDivideByZeroDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\IntegerDivideByZeroDemo.java
java -cp . IntegerDivideByZeroDemo
~~~

**Exact expected output:**

~~~text
valid quotient=3
valid remainder=1
long quotient=5
byte division=4
int / zero=ArithmeticException
int % zero=ArithmeticException
long / zero=ArithmeticException
floorDiv zero=ArithmeticException
floating positive=Infinity
floating negative=-Infinity
floating undefined=NaN
guarded normal=5
guarded invalid=denominator must not be zero
optional valid zero=0
optional no result=true
short circuit and=false
short circuit or=true
boxed zero=ArithmeticException
boxed null=NullPointerException
nullable rejected=denominator is missing
min / -1=-2147483648
min widened=2147483648
checked overflow=int quotient overflow
big exact=9223372036854775808
counter after failure=1
~~~

### Giải thích kết quả chính

1. `10 / 3` và `10 % 3` bình thường vì mẫu bằng 3, kết quả là thương 3 và dư 1.
2. `byte` được nâng lên `int` khi chia; `9 / 2` cho 4.
3. Cả `int / zero`, `int % zero`, `long / zero` và `Math.floorDiv` đều phát sinh `ArithmeticException`.
4. `double` chia cho 0 trả Infinity/-Infinity/NaN; đó là ngữ nghĩa **khác kiểu nguyên**.
5. Guard chủ động ném `IllegalArgumentException` với thông báo xác định.
6. `OptionalInt` tách được thương nguyên hợp lệ bằng 0 khỏi trường hợp không có thương.
7. `&&` và `||` tránh đánh giá phép chia nhờ short-circuit.
8. Wrapper `Integer.valueOf(0)` gây lỗi chia cho 0; wrapper `null` gây lỗi unboxing trước khi chia.
9. `MIN_VALUE / -1` là trường hợp **tràn không tự ném lỗi**; dùng kiểm tra riêng hoặc kiểu đủ rộng.
10. Bộ đếm tăng trước khi phép chia thất bại; `catch` không rollback tác dụng phụ trước đó.

## 14. Các lỗi thường gặp

| Ngộ nhận / lỗi | Vì sao sai? | Cách xử lý |
|---|---|---|
| `10 / 0` trả Infinity | Đó là quy tắc của floating-point, không phải integer | Kiểm tra mẫu khác 0 |
| `10 % 0` vẫn hợp lệ | Phép dư nguyên cũng không cho mẫu 0 | Validate trước cả `/` và `%` |
| `long a = 10L / 0L` an toàn vì `long` rộng | Rộng hơn không cho phép chia 0 | Cùng quy tắc ngoại lệ |
| `Math.floorDiv(10, 0)` an toàn | `floorDiv` không hỗ trợ mẫu 0 | Kiểm tra đầu vào |
| `10.0 / 0.0` là cách sửa chia nguyên | Kết quả Infinity có thể sai nghiệp vụ | Chỉ dùng floating-point khi đúng mô hình |
| `int result = 10 / boxed;` với boxed null | Unboxing null gây `NullPointerException` | Kiểm tra null riêng |
| Bắt mọi `RuntimeException` rồi trả 0 | Che cả lỗi khác, không phân biệt trường hợp | Bắt đúng loại hoặc validate |
| Dùng 0 để đại diện cả thương 0 và phép chia lỗi | Làm mất trạng thái “không có kết quả” | `OptionalInt`/error model rõ |
| Dùng `&&` như `&` | `&` đánh giá cả hai vế | Dùng `&&` khi cần guard |
| Cho rằng không exception luôn có thương chính xác | `MIN_VALUE/-1` tràn đặc biệt | Kiểm tra overflow |
| Bắt exception sẽ hoàn tác mọi side effect | `catch` không rollback | Validate trước hoặc giao dịch/rollback |
| Chia theo số lượng batch mà không kiểm tra `batchSize` | Batch size 0 sẽ gây lỗi | Yêu cầu `batchSize > 0` |

## 15. Bài tập và đáp án

### Bài A — hai toán tử cùng lỗi khi mẫu 0

~~~java
int numerator = 8;
int denominator = 0;
// System.out.println(numerator / denominator);
// System.out.println(numerator % denominator);
~~~

**Đáp án:** nếu chạy từng dòng, cả phép chia nguyên và phép dư nguyên đều ném `ArithmeticException`. Thực hiện `denominator != 0` trước.

### Bài B — cách bảo vệ bằng logic

~~~java
int b = 0;
boolean condition = (b != 0) && (12 / b > 2);
System.out.println(condition);
~~~

**Đáp án:** `false`; Java không đánh giá vế phải của `&&` vì vế trái false.

### Bài C — có nên tự trả 0?

Giả sử API trả kết quả `int` bằng 0 nếu mẫu bằng 0. Vì sao nguy hiểm?

**Đáp án:** `1 / 2` cũng có thương hợp lệ bằng 0. Caller không phân biệt được kết quả đúng và lỗi. Có thể reject input bằng exception hoặc trả kiểu có trạng thái như `OptionalInt`.

### Bài D — wrapper nullable

~~~java
Integer b = null;
// int result = 12 / b;
~~~

**Đáp án:** nếu chạy, có `NullPointerException` do auto-unboxing `null`, không phải `ArithmeticException` chia cho 0.

### Bài E — int overflow

~~~java
System.out.println(Integer.MIN_VALUE / -1);
~~~

**Đáp án:** `-2147483648`, **không** ném lỗi. Nếu không chấp nhận tràn, kiểm tra trường hợp này và dùng kiểu đủ rộng khi cần.

### Bài F — phân biệt floating-point

~~~java
System.out.println(5.0 / 0.0);
System.out.println(0.0 / 0.0);
~~~

**Đáp án:**

~~~text
Infinity
NaN
~~~

Chúng không phải những giá trị nguyên hợp lệ.

## 16. Checklist kết thúc

- [ ] Biết `int` và `long` chia cho 0 ném `ArithmeticException`.
- [ ] Biết `%` với mẫu 0 cũng ném `ArithmeticException`.
- [ ] Hiểu `byte`, `short`, `char` thường được nâng lên `int` khi chia.
- [ ] Phân biệt chia nguyên cho 0 với floating-point trả Infinity/NaN.
- [ ] Biết dùng `if (denominator == 0)` hoặc guard clause để reject input.
- [ ] Không dùng `0` làm giá trị dự phòng mơ hồ nếu 0 là kết quả hợp lệ.
- [ ] Biết `OptionalInt` khi kết quả có thể vắng mặt là điều bình thường.
- [ ] Biết `Math.floorDiv`/`Math.floorMod` cũng không hỗ trợ mẫu 0.
- [ ] Dùng `&&`/`||` đúng cách để tránh đánh giá phép chia không hợp lệ.
- [ ] Phân biệt wrapper 0 gây `ArithmeticException` với wrapper null gây `NullPointerException`.
- [ ] Hiểu ngoại lệ tràn đặc biệt `MIN_VALUE / -1`.
- [ ] Không giả định `catch` hoàn tác tác dụng phụ đã xảy ra.
- [ ] Chạy `IntegerDivideByZeroDemo.java` trên JDK 17+ và đối chiếu exact expected output.

**Kết luận:** Trong Java 17+, **chia hoặc lấy dư số nguyên với mẫu số 0** gây `ArithmeticException` tại thời điểm đánh giá; đó không phải Infinity. Thiết kế an toàn là **kiểm tra đầu vào sớm**, phân biệt dữ liệu thiếu với zero, chọn rõ hợp đồng lỗi/không có kết quả và tránh che giấu các trường hợp bất hợp lệ bằng giá trị dự phòng mơ hồ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="arithmetic-006">Tràn số nguyên không tự động ném ngoại lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Tràn số nguyên không tự động ném ngoại lệ trong Java

> **Java core — language-basics / Số học**  
> **Môi trường:** JDK 17+; chỉ dùng thư viện chuẩn Java.  
> **Mục tiêu:** Nhận diện tràn số ở `int`/`long`, hiểu vì sao phép tính vẫn tiếp tục mà **không tự ném ngoại lệ**, và biết cách chọn `Math.*Exact`, chuyển kiểu đúng thời điểm hoặc `BigInteger` để không âm thầm mất kết quả.

## 1. Thế nào là tràn số nguyên?

`int` là kiểu nguyên **32 bit có dấu**, từ **-2.147.483.648** đến **2.147.483.647**. `long` là kiểu nguyên **64 bit có dấu**, từ **-9.223.372.036.854.775.808** đến **9.223.372.036.854.775.807**.

Một phép cộng, trừ hoặc nhân có kết quả toán học vượt miền biểu diễn sẽ **tràn số** (*integer overflow*).

~~~java
int max = Integer.MAX_VALUE; // 2147483647
int wrapped = max + 1;
System.out.println(wrapped); // -2147483648
~~~

Java **không tự động ném `ArithmeticException` khi cộng, trừ hoặc nhân tràn `int`/`long`**. Phần bit kết quả còn lại được diễn giải theo biểu diễn số nguyên có dấu, nên kết quả có thể đổi dấu hoặc khác xa giá trị toán học mong đợi.

- `int` sử dụng 32 bit; phép toán nguyên tràn có hiệu ứng quay vòng theo **modulo `2^32`**.
- `long` sử dụng 64 bit; tương tự theo **modulo `2^64`**.
- Điều này là hành vi **được Java quy định**, không phải hành vi ngẫu nhiên hoặc lỗi bộ nhớ.
- `float`/`double` dùng quy tắc số thực khác: có thể ra `Infinity`/`NaN`. Chúng không phải giải pháp giữ nguyên mọi giá trị nguyên.

**Kết luận:** Chương trình chạy thành công **không chứng minh phép tính đúng về mặt toán học**.

## 2. Các phép toán dễ tràn nhưng vẫn chạy

| Biểu thức | Giá trị toán học | Kết quả `int` thực tế |
| --- | ---: | ---: |
| `Integer.MAX_VALUE + 1` | 2147483648 | -2147483648 |
| `Integer.MIN_VALUE - 1` | -2147483649 | 2147483647 |
| `50_000 * 50_000` | 2500000000 | -1794967296 |
| `-Integer.MIN_VALUE` | 2147483648 | -2147483648 |
| `Integer.MIN_VALUE / -1` | 2147483648 | -2147483648 |

Phép `Integer.MIN_VALUE / -1` là **trường hợp đặc biệt**: phép chia nguyên này có kết quả toán học vượt `int`, Java vẫn trả `Integer.MIN_VALUE` mà **không ném ngoại lệ**. Phần dư `Integer.MIN_VALUE % -1` bằng **0**.

Tương tự với `long`:

~~~java
long max = Long.MAX_VALUE;
System.out.println(max + 1L); // -9223372036854775808
~~~

Thao tác tăng `++`, giảm `--` hoặc gán cộng `+=` cũng có thể tràn khi tính và lưu vào kiểu nguyên. Ví dụ `int n = Integer.MAX_VALUE; n++;` khiến `n` trở thành `Integer.MIN_VALUE`.

## 3. Chuyển sang `long`: phải làm **trước** phép tính

Một bẫy thường gặp:

~~~java
int max = Integer.MAX_VALUE;

long wrong = max + 1;        // -2147483648, rồi mới mở rộng sang long.
long right = (long) max + 1; // 2147483648, phép cộng thực hiện bằng long.
~~~

Trong `long wrong = max + 1`, hai toán hạng đều có kiểu `int` nên **cộng kiểu `int` trước**, tràn rồi mới gán sang `long`. Kiểu biến đích không cứu được phép toán đã xảy ra.

Ngay cả cast sau ngoặc cũng quá muộn:

~~~java
long wrongAgain = (long) (max + 1); // -2147483648
long rightAgain = ((long) max) + 1; // 2147483648
~~~

**Đối với phép nhân**, hãy chuyển toán hạng trước phép nhân:

~~~java
int a = 50_000, b = 50_000;
long wrongProduct = a * b;         // -1794967296
long rightProduct = (long) a * b; // 2500000000
~~~

**Giới hạn:** `long` cũng có thể tràn. Việc chuyển `int` sang `long` chỉ giải quyết được khi kết quả **nằm trong miền `long`**; không có kiểu primitive nguyên nào của Java tự mở rộng vô hạn.

## 4. Muốn phát hiện tràn: dùng các phép toán `Math.*Exact`

Java có các API **kiểm tra tràn** chủ động. Khi kết quả không nằm trong miền kiểu số nguyên được yêu cầu, chúng ném **`ArithmeticException`**:

| Phương thức | Vai trò |
| --- | --- |
| `Math.addExact(a, b)` | Cộng, phát hiện tràn |
| `Math.subtractExact(a, b)` | Trừ, phát hiện tràn |
| `Math.multiplyExact(a, b)` | Nhân, phát hiện tràn |
| `Math.incrementExact(a)` | Tăng 1, phát hiện tràn |
| `Math.decrementExact(a)` | Giảm 1, phát hiện tràn |
| `Math.negateExact(a)` | Đổi dấu, phát hiện trường hợp giá trị nhỏ nhất |
| `Math.toIntExact(value)` | Chuyển `long` sang `int`, kiểm tra ngoài miền |

Ví dụ:

~~~java
int a = Integer.MAX_VALUE;

try {
    int sum = Math.addExact(a, 1);
    System.out.println(sum);
} catch (ArithmeticException ex) {
    System.out.println("overflow detected");
}
~~~

Khi hệ thống yêu cầu **không được sai một đơn vị**, dùng API checked arithmetic có ý nghĩa hơn tự ngầm chấp nhận phép tính quay vòng. Đừng dùng `Math.addExact` trên một kết quả **đã tràn trước đó** rồi hy vọng nó có thể khôi phục giá trị gốc: phải gọi hàm kiểm tra **ngay tại phép tính có nguy cơ tràn**.

## 5. Chia cho 0 là ngoại lệ khác

Đừng nhầm **tràn số** với **chia nguyên cho 0**:

~~~java
int a = 10;
int zero = 0;
// a / zero; // ArithmeticException tại runtime.
// a % zero; // ArithmeticException tại runtime.
~~~

- `int`/`long` chia hoặc lấy phần dư cho **0** → `ArithmeticException`.
- `int`/`long` cộng, trừ, nhân bị tràn → **không tự ném**.
- `Integer.MIN_VALUE / -1` → **tràn theo quy tắc đặc biệt nhưng không ném**.
- `Math.addExact`/`multiplyExact`/`negateExact`... → **có ném** nếu không thể biểu diễn chính xác.

Nếu mẫu số có thể bằng 0, cần kiểm tra đầu vào. Việc chọn API `Exact` không thay thế guard chia cho 0.

## 6. Chương trình Java 17+ chạy độc lập

Tạo file **IntegerOverflowDemo.java**:

~~~java
import java.math.BigInteger;

public final class IntegerOverflowDemo {
    private static void checked(String label, Runnable action) {
        try {
            action.run();
            System.out.println(label + "=ok");
        } catch (ArithmeticException ex) {
            System.out.println(label + "=ArithmeticException");
        }
    }

    public static void main(String[] args) {
        int max = Integer.MAX_VALUE;
        int min = Integer.MIN_VALUE;
        long maxLong = Long.MAX_VALUE;

        System.out.println("addWrap=" + (max + 1));
        System.out.println("subtractWrap=" + (min - 1));
        System.out.println("multiplyWrap=" + (50_000 * 50_000));
        System.out.println("longWrap=" + (maxLong + 1L));

        long lateWiden = max + 1;
        long earlyWiden = (long) max + 1;
        System.out.println("lateWiden=" + lateWiden);
        System.out.println("earlyWiden=" + earlyWiden);

        System.out.println("negateMinimum=" + (-min));
        System.out.println("minDivNegOne=" + (min / -1));
        System.out.println("minRemNegOne=" + (min % -1));

        checked("addExact", () -> Math.addExact(max, 1));
        checked("subtractExact", () -> Math.subtractExact(min, 1));
        checked("multiplyExact", () -> Math.multiplyExact(50_000, 50_000));
        checked("negateExact", () -> Math.negateExact(min));
        checked("toIntExact", () -> Math.toIntExact(2_147_483_648L));

        int zero = 0;
        checked("divideZero", () -> {
            int result = max / zero;
            System.out.println(result);
        });

        BigInteger exact = BigInteger.valueOf(maxLong).add(BigInteger.ONE);
        System.out.println("bigInteger=" + exact);
    }
}
~~~

Mở terminal tại thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 IntegerOverflowDemo.java
java IntegerOverflowDemo
~~~

**Expected output chính xác:**

~~~text
addWrap=-2147483648
subtractWrap=2147483647
multiplyWrap=-1794967296
longWrap=-9223372036854775808
lateWiden=-2147483648
earlyWiden=2147483648
negateMinimum=-2147483648
minDivNegOne=-2147483648
minRemNegOne=0
addExact=ArithmeticException
subtractExact=ArithmeticException
multiplyExact=ArithmeticException
negateExact=ArithmeticException
toIntExact=ArithmeticException
divideZero=ArithmeticException
bigInteger=9223372036854775808
~~~

Các dòng `addWrap`, `subtractWrap`, `multiplyWrap`, `longWrap` và `minDivNegOne` **không có exception**, mặc dù giá trị toán học không vừa kiểu đích. Các dòng `...Exact=ArithmeticException` cho thấy ngoại lệ chỉ xuất hiện khi chủ động dùng phép tính có kiểm tra. `divideZero` là lỗi **chia nguyên cho 0**, độc lập với tràn số.

## 7. Khi kết quả vượt cả `long`: dùng `BigInteger`

`BigInteger` trong `java.math` lưu số nguyên với độ lớn không bị giới hạn bởi 32/64 bit primitive (trong thực tế còn giới hạn bộ nhớ):

~~~java
BigInteger x = BigInteger.valueOf(Long.MAX_VALUE);
BigInteger y = x.add(BigInteger.ONE);
System.out.println(y); // 9223372036854775808
~~~

Các phép tính với `BigInteger` dùng method như `add`, `subtract`, `multiply`. Nó **không** hỗ trợ viết `x + y` như primitive.

Nếu vẫn cần xuất `int`/`long`, kiểm tra miền hoặc dùng các phép chuyển đổi chính xác như `intValueExact()`/`longValueExact()` để **không âm thầm mất số**. Chuyển `BigInteger` sang primitive bằng `intValue()` đơn thuần có thể cắt mất các bit quan trọng.

## 8. Thực hành an toàn và bài tập

| Yêu cầu | Lựa chọn |
| --- | --- |
| Đếm, tính tổng nhỏ chắc chắn vừa `int` | Dùng `int` và kiểm soát miền dữ liệu |
| Kết quả có thể vượt `int` nhưng vừa `long` | Chuyển một toán hạng sang `long` **trước khi tính** |
| Không được phép tràn âm thầm | `Math.addExact`, `subtractExact`, `multiplyExact`, ... |
| Có thể vượt cả `long` | `BigInteger`, cùng xử lý giới hạn tài nguyên |
| Chuyển `long` về `int` mà phải giữ nguyên giá trị | `Math.toIntExact` |
| Chia cho mẫu số có thể bằng 0 | Guard đầu vào trước `/` hoặc `%` |

**Bài 1.** Tính `Integer.MAX_VALUE + 1` và `Integer.MIN_VALUE - 1` trong Java. Vì sao chương trình không dừng?

**Bài 2.** So sánh `long x = 50_000 * 50_000;` với `long y = 50_000L * 50_000;`. Phép nào tràn? Khi nào Java quyết định kiểu phép nhân?

**Bài 3.** Viết `Math.multiplyExact(50_000, 50_000)` trong `try/catch`. Giải thích khác biệt với toán tử `*`.

**Bài 4.** Dự đoán `Integer.MIN_VALUE / -1` và `Integer.MIN_VALUE % -1`. Tại sao đây không giống trường hợp chia cho 0?

**Bài 5.** Tính `Long.MAX_VALUE + 1` bằng `BigInteger` mà không mất giá trị nguyên ban đầu.

### Ghi nhớ

**Java không tự động ném ngoại lệ khi các phép toán nguyên `int`/`long` thông thường bị tràn.** Giá trị có thể quay vòng theo giới hạn bit và làm sai dữ liệu mà chương trình vẫn chạy. Nếu cần kết quả chính xác, hãy **mở rộng toán hạng trước khi tính** (khi `long` đủ miền), dùng **`Math.*Exact`** để phát hiện tràn, hoặc **`BigInteger`** khi vượt miền `long`. Chia nguyên cho 0 là một lỗi riêng: nó **ném `ArithmeticException`**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="arithmetic-007">NaN, vô cực và số không có dấu trong phép tính số thực</a>

<details>
<summary>Xem chi tiết</summary>

# NaN, vô cực và số 0 có dấu trong phép tính số thực — Java 17+

`float` và `double` trong Java biểu diễn số thực nhị phân theo IEEE 754. Ngoài các số hữu hạn thông thường, chúng có ba nhóm giá trị đặc biệt:

- **`NaN` (*Not a Number*)**: giá trị báo kết quả số học không xác định, chẳng hạn `0.0 / 0.0`.
- **Vô cực (`Infinity` và `-Infinity`)**: có thể xuất hiện khi số thực khác 0 chia cho số không hoặc khi kết quả quá lớn.
- **Số 0 có dấu (*signed zero*)**: có `+0.0` và `-0.0`. Hai giá trị bằng nhau khi dùng toán tử `==`, nhưng **bit dấu khác nhau** và có thể tạo kết quả khác nhau ở một số phép toán.

~~~java
double positive = 1.0 / 0.0;  // Infinity.
double negative = 1.0 / -0.0; // -Infinity.
double undefined = 0.0 / 0.0; // NaN.
~~~

**Không áp dụng quy tắc chia số nguyên cho 0 cho số thực.** `int` chia cho 0 ném `ArithmeticException` khi chạy; `double`/`float` chia cho số không tuân theo quy tắc IEEE 754.

## 1. Bảng kết quả tiêu biểu

| Biểu thức Java | Kết quả | Nguyên nhân |
| --- | --- | --- |
| `1.0 / 0.0` | `Infinity` | Số dương chia cho dương không |
| `1.0 / -0.0` | `-Infinity` | Số dương chia cho âm không |
| `0.0 / 0.0` | `NaN` | Dạng không xác định |
| `Double.POSITIVE_INFINITY - Double.POSITIVE_INFINITY` | `NaN` | Vô cực trừ vô cực |
| `Double.POSITIVE_INFINITY * 0.0` | `NaN` | Vô cực nhân không |
| `Double.POSITIVE_INFINITY / Double.POSITIVE_INFINITY` | `NaN` | Vô cực chia vô cực |
| `Double.MAX_VALUE * 2.0` | `Infinity` | Overflow số thực |
| `Double.MIN_VALUE / 2.0` | `0.0` | Underflow về dương không |
| `-Double.MIN_VALUE / 2.0` | `-0.0` | Underflow về âm không |

**Cẩn thận với tên `Double.MIN_VALUE`:** đây là **số `double` dương nhỏ nhất lớn hơn 0**, không phải số âm nhỏ nhất. Muốn có một giá trị hữu hạn âm có độ lớn rất lớn, có thể dùng `-Double.MAX_VALUE`.

## 2. Ví dụ Java 17+ chạy độc lập

Tạo tệp UTF-8 **`FloatingSpecialValuesDemo.java`**:

~~~java
public class FloatingSpecialValuesDemo {
    public static void main(String[] args) {
        double positiveInfinity = 1.0 / 0.0;
        double negativeInfinity = 1.0 / -0.0;
        double nan = 0.0 / 0.0;

        System.out.println("positiveInfinity=" + positiveInfinity);
        System.out.println("negativeInfinity=" + negativeInfinity);
        System.out.println("zeroOverZeroIsNaN="
                + Double.isNaN(nan));

        System.out.println("infinityMinusInfinityIsNaN="
                + Double.isNaN(Double.POSITIVE_INFINITY
                        - Double.POSITIVE_INFINITY));
        System.out.println("infinityTimesZeroIsNaN="
                + Double.isNaN(Double.POSITIVE_INFINITY * 0.0));
        System.out.println("infinityOverInfinityIsNaN="
                + Double.isNaN(Double.POSITIVE_INFINITY
                        / Double.POSITIVE_INFINITY));

        System.out.println("nanEqualsSelf=" + (nan == nan));
        System.out.println("nanNotEqualsSelf=" + (nan != nan));
        System.out.println("nanLessThanZero=" + (nan < 0.0));
        System.out.println("nanCompareSelf="
                + Double.compare(nan, nan));
        System.out.println("nanWrapperEquals="
                + Double.valueOf(nan).equals(Double.valueOf(nan)));

        System.out.println("finite42=" + Double.isFinite(42.0));
        System.out.println("finiteInfinity="
                + Double.isFinite(positiveInfinity));
        System.out.println("maxOverflow="
                + (Double.MAX_VALUE * 2.0));
        System.out.println("minUnderflow="
                + (Double.MIN_VALUE / 2.0));
        System.out.println("negativeMinUnderflow="
                + (-Double.MIN_VALUE / 2.0));

        double positiveZero = 0.0;
        double negativeZero = -0.0;
        System.out.println("zerosEqual="
                + (positiveZero == negativeZero));
        System.out.println("zeroWrapperEquals="
                + Double.valueOf(positiveZero)
                        .equals(Double.valueOf(negativeZero)));
        System.out.println("zeroCompare="
                + Double.compare(positiveZero, negativeZero));
        System.out.println("positiveZeroBits="
                + Long.toHexString(
                        Double.doubleToRawLongBits(positiveZero)));
        System.out.println("negativeZeroBits="
                + Long.toHexString(
                        Double.doubleToRawLongBits(negativeZero)));

        System.out.println("reciprocalPositiveZero="
                + (1.0 / positiveZero));
        System.out.println("reciprocalNegativeZero="
                + (1.0 / negativeZero));
        System.out.println("minZero="
                + Math.min(negativeZero, positiveZero));
        System.out.println("maxZero="
                + Math.max(negativeZero, positiveZero));
        System.out.println("copiedNegativeSign="
                + Math.copySign(5.0, negativeZero));
        System.out.println("nanMinIsNaN="
                + Double.isNaN(Math.min(nan, 3.0)));
        System.out.println("negativeZeroRemainder="
                + (negativeZero % 3.0));

        float floatNegativeInfinity = 1.0f / -0.0f;
        System.out.println("floatNegativeInfinity="
                + floatNegativeInfinity);
        System.out.println("parsedNaN="
                + Double.isNaN(Double.parseDouble("NaN")));

        double sum = 0.1 + 0.2;
        System.out.println("sum=" + sum);
        System.out.println("exactDecimalEquality="
                + (sum == 0.3));
    }
}
~~~

Mở PowerShell (Windows) hoặc Bash (Linux/macOS) tại thư mục chứa tệp. Yêu cầu **JDK 17 trở lên**:

~~~bash
java -version
javac -version
javac -encoding UTF-8 FloatingSpecialValuesDemo.java
java FloatingSpecialValuesDemo
~~~

**Expected output chính xác** của riêng lệnh `java FloatingSpecialValuesDemo`:

~~~text
positiveInfinity=Infinity
negativeInfinity=-Infinity
zeroOverZeroIsNaN=true
infinityMinusInfinityIsNaN=true
infinityTimesZeroIsNaN=true
infinityOverInfinityIsNaN=true
nanEqualsSelf=false
nanNotEqualsSelf=true
nanLessThanZero=false
nanCompareSelf=0
nanWrapperEquals=true
finite42=true
finiteInfinity=false
maxOverflow=Infinity
minUnderflow=0.0
negativeMinUnderflow=-0.0
zerosEqual=true
zeroWrapperEquals=false
zeroCompare=1
positiveZeroBits=0
negativeZeroBits=8000000000000000
reciprocalPositiveZero=Infinity
reciprocalNegativeZero=-Infinity
minZero=-0.0
maxZero=0.0
copiedNegativeSign=-5.0
nanMinIsNaN=true
negativeZeroRemainder=-0.0
floatNegativeInfinity=-Infinity
parsedNaN=true
sum=0.30000000000000004
exactDecimalEquality=false
~~~

`java -version` và `javac -version` in thông tin JDK tùy máy, không thuộc phần expected output của chương trình.

## 3. `NaN`: kiểm tra và so sánh

`NaN` không phải `null`, số 0 hay chuỗi báo lỗi. Đây là **giá trị số thực đặc biệt** có thể được truyền tiếp qua nhiều phép tính.

~~~java
double value = 0.0 / 0.0;
System.out.println(value == Double.NaN); // false.
System.out.println(value != value);      // true.
System.out.println(Double.isNaN(value)); // true.
~~~

Với primitive `double` hoặc `float`, các phép `==`, `<`, `<=`, `>`, `>=` có toán hạng NaN cho `false`; phép `!=` cho `true`. Do đó **không kiểm tra NaN bằng `value == Double.NaN`**. Dùng `Double.isNaN(value)` hoặc `Float.isNaN(value)`.

### Phân biệt primitive `==`, `Double.equals` và `Double.compare`

~~~java
double nan = Double.NaN;
System.out.println(nan == nan); // false.
System.out.println(Double.compare(nan, nan)); // 0.
System.out.println(Double.valueOf(nan)
        .equals(Double.valueOf(nan))); // true.
~~~

| Phép so sánh | Hai giá trị NaN | Hai giá trị `+0.0` và `-0.0` |
| --- | --- | --- |
| Primitive `==` | `false` | `true` |
| Wrapper `Double.equals` | `true` | `false` |
| `Double.compare(a,b)` | Trả 0 khi cả hai NaN | Phân biệt dấu, `compare(+0.0,-0.0) > 0` |

`Double.equals` và `Double.compare` được thiết kế để có quy tắc bình đẳng/thứ tự thích hợp cho wrapper và collection. Chúng **không giống hoàn toàn** ngữ nghĩa so sánh số thực primitive bằng `==`.

### NaN lan truyền và parsing

~~~java
double nan = Double.NaN;
System.out.println(Double.isNaN(nan + 2.0)); // true.
System.out.println(Double.isNaN(nan * 0.0)); // true.
System.out.println(Double.isNaN(Double.parseDouble("NaN"))); // true.
~~~

Một chuỗi `"NaN"` có thể được `Double.parseDouble` chấp nhận. **Parsing thành công không đồng nghĩa giá trị hợp lệ theo nghiệp vụ**, nhất là khi ứng dụng chỉ muốn số hữu hạn.

## 4. Vô cực và cách nhận biết giá trị hữu hạn

Ngoài `Double.POSITIVE_INFINITY` và `Double.NEGATIVE_INFINITY`, phép tính quá lớn có thể cho vô cực:

~~~java
double overflow = Double.MAX_VALUE * 2.0; // Infinity.
double reciprocal = 1.0 / -0.0;          // -Infinity.
~~~

Một số phép toán với vô cực vẫn cho vô cực, ví dụ `Infinity + 3.0`. Nhưng `Infinity + (-Infinity)`, `Infinity * 0.0` hay `Infinity / Infinity` cho NaN.

Nếu cần xác nhận số thực hữu hạn:

~~~java
double input = Double.parseDouble("12.5");
if (!Double.isFinite(input)) {
    throw new IllegalArgumentException("Expected a finite number");
}
~~~

- `Double.isNaN(x)`: giá trị NaN?
- `Double.isInfinite(x)`: giá trị vô cực dương hoặc âm?
- `Double.isFinite(x)`: giá trị không phải NaN và không phải vô cực?
- `Float` cũng có những phương thức tương ứng với `float`.

`Double.isFinite(-0.0)` trả **`true`**. Zero có bit dấu vẫn là số **hữu hạn**.

## 5. Hai số không `+0.0` và `-0.0` khác nhau ở đâu?

Cả `+0.0` và `-0.0` đều là zero theo phép `==`:

~~~java
double plus = 0.0;
double minus = -0.0;

System.out.println(plus == minus); // true.
System.out.println(1.0 / plus);    // Infinity.
System.out.println(1.0 / minus);   // -Infinity.
~~~

Tuy nhiên, với IEEE 754 chúng có **bit dấu khác nhau**. `Double.doubleToRawLongBits` giúp xem mẫu bit:

| Biểu thức | Mẫu bit 64-bit (hexadecimal) |
| --- | --- |
| `Double.doubleToRawLongBits(+0.0)` | `0x0000000000000000` |
| `Double.doubleToRawLongBits(-0.0)` | `0x8000000000000000` |

Demo in `positiveZeroBits=0` và `negativeZeroBits=8000000000000000` vì `Long.toHexString` bỏ các chữ số 0 ở đầu.

Ngoài phép nghịch đảo, dấu zero có thể quan trọng trong `Math.min`, `Math.max`, `Math.copySign`, phép chia và phần dư số thực:

~~~java
System.out.println(Math.min(-0.0, 0.0));    // -0.0.
System.out.println(Math.max(-0.0, 0.0));    //  0.0.
System.out.println(Math.copySign(5.0, -0.0)); // -5.0.
System.out.println(-0.0 % 3.0);            // -0.0.
~~~

**Không dùng `x == 0.0` để phân biệt dấu của zero**. Tùy nhu cầu, dùng `Math.copySign` hoặc kiểm tra bit. `float` cũng có `+0.0f` và `-0.0f` nhưng có biểu diễn 32-bit.

## 6. Overflow/underflow không giống sai số làm tròn thông thường

**Overflow số thực** có thể sinh `Infinity` khi độ lớn vượt miền hữu hạn. **Underflow** xảy ra với giá trị quá gần 0, có thể thành subnormal hoặc được làm tròn về **zero có dấu**:

~~~java
System.out.println(Double.MAX_VALUE * 2.0); // Infinity.
System.out.println(Double.MIN_VALUE / 2.0); // 0.0.
System.out.println(-Double.MIN_VALUE / 2.0); // -0.0.
~~~

`Double.MIN_VALUE` là số **dương nhỏ nhất khác 0**, không phải giá trị âm lớn nhất về độ lớn. Các phép này không tự ném `ArithmeticException` như chia nguyên cho 0.

Ngay cả số **hữu hạn** vẫn có thể chỉ là xấp xỉ: `0.1 + 0.2` với `double` cho `0.30000000000000004`. `Double.isFinite` kiểm tra NaN/vô cực, **không** chứng minh giá trị thập phân chính xác. Bài tiếp theo tập trung binary32/binary64, ULP, sai số tích lũy, dung sai và mô hình decimal exact.


## 7. Lỗi thường gặp

| Hiểu nhầm | Thực tế trong Java 17+ |
| --- | --- |
| “`0.0 / 0.0` ném `ArithmeticException`.” | Kết quả là `NaN` |
| “`1.0 / 0.0` giống `1 / 0`.” | `double` cho `Infinity`; `int` chia 0 ném ngoại lệ |
| “`NaN == NaN` luôn đúng.” | Với primitive `double`: `false` |
| “`x == Double.NaN` kiểm tra được NaN.” | Sai; dùng `Double.isNaN(x)` |
| “`Double.equals` và primitive `==` hoàn toàn giống nhau.” | Khác với NaN và ±0.0 |
| “`+0.0` và `-0.0` không khác biệt nào.” | Chúng khác bit dấu và có thể khác kết quả phép toán |
| “`Double.MIN_VALUE` là giá trị âm nhỏ nhất.” | Đây là số dương nhỏ nhất lớn hơn 0 |
| “`parseDouble` thành công thì luôn được số hữu hạn.” | Chuỗi `"NaN"` và `"Infinity"` cũng có thể hợp lệ |
| “Số hữu hạn nghĩa là số chính xác thập phân.” | Không; `0.1 + 0.2` là phản ví dụ |

## 8. Bài tập tự kiểm tra

1. `1.0 / 0.0`, `1.0 / -0.0`, `0.0 / 0.0` lần lượt là gì?  
   *Đáp án:* `Infinity`, `-Infinity`, `NaN`.
2. Tại sao `Double.NaN == Double.NaN` cho false?  
   *Đáp án:* Toán tử primitive `==` tuân theo quy tắc NaN; kiểm tra NaN bằng `Double.isNaN`.
3. `0.0 == -0.0` cho gì và `1.0 / -0.0` cho gì?  
   *Đáp án:* `true` và `-Infinity`.
4. Hai wrapper `Double.valueOf(0.0)` và `Double.valueOf(-0.0)` có `equals` nhau không?  
   *Đáp án:* Không; `Double.equals` phân biệt bit dấu của zero.
5. `Double.MIN_VALUE / 2.0` có thể tạo gì?  
   *Đáp án:* `0.0` do underflow.
6. Cần từ chối cả NaN lẫn Infinity trong đầu vào số thực thì dùng gì?  
   *Đáp án:* `Double.isFinite(value)`.
7. `Double.isFinite(0.1 + 0.2)` trả true, vậy `0.1 + 0.2 == 0.3` có chắc true?  
   *Đáp án:* Không; trong `double`, kết quả là `0.30000000000000004`.

## Checklist

- [ ] Biết `NaN`, `Infinity` và `-Infinity` là **giá trị số thực đặc biệt**, không phải `null`.
- [ ] Dùng `Double.isNaN`, `Double.isInfinite`, `Double.isFinite` thay vì đoán từ `==`.
- [ ] Phân biệt phép `==` primitive với `Double.equals` và `Double.compare`.
- [ ] Hiểu `+0.0` và `-0.0` bằng nhau theo `==` nhưng khác bit dấu.
- [ ] Biết overflow có thể ra vô cực, underflow có thể ra zero có dấu.
- [ ] Phân biệt phép chia cho 0 của số nguyên và số thực.
- [ ] Không đồng nhất **hữu hạn** với **chính xác thập phân**.

**Kết luận:** `float`/`double` có những quy tắc quan trọng dành riêng cho **NaN, vô cực và hai giá trị zero có dấu**. Đọc đúng các giá trị này bằng API kiểm tra chuyên dụng, nắm sự khác nhau giữa các phép so sánh, và kiểm tra độ chính xác độc lập với tính hữu hạn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="arithmetic-008">Sai số nhị phân khi tính với float hoặc double</a>

<details>
<summary>Xem chi tiết</summary>

# Sai số nhị phân khi tính với `float` hoặc `double` — Java 17+

## 1. Hiện tượng: vì sao 0.1 + 0.2 không bằng đúng 0.3?

Trong Java, `float` và `double` lưu trữ số thực theo định dạng **dấu phẩy động nhị phân IEEE 754**. Nhiều phân số thập phân hữu hạn — như `0.1`, `0.2` và `0.3` — **không thể biểu diễn chính xác bằng một số bit nhị phân hữu hạn**.

~~~java
double a = 0.1;
double b = 0.2;
double sum = a + b;

System.out.println(sum);         // 0.30000000000000004
System.out.println(sum == 0.3);  // false
~~~

Kết quả **không phải bug của Java**. Các giá trị đầu vào đã được làm tròn tới số nhị phân biểu diễn được; phép cộng cũng tuân theo quy tắc làm tròn của dấu phẩy động. Vì vậy kết quả biểu diễn của `0.1 + 0.2` khác một chút so với biểu diễn của literal `0.3`.

Ba điều cần nắm:

1. **Số toán học** `0.1` khác với **giá trị nhị phân gần nhất** được lưu trong `double`.
2. `double` có **nhiều độ chính xác hơn `float`**, nhưng vẫn không biểu diễn chính xác mọi phân số thập phân.
3. Sai số có thể **lan truyền, tích lũy hoặc bị khuếch đại** qua các phép toán. Nếu cần tính decimal chính xác theo nghiệp vụ, nên chọn mô hình dữ liệu phù hợp thay vì chỉ tăng số chữ số hiển thị.

## 2. Vì sao có sai số? Mô hình nhị phân hữu hạn

Một phân số có biểu diễn hữu hạn trong cơ số 2 khi mẫu số sau khi rút gọn chỉ có thừa số nguyên tố **2**. Ví dụ:

| Phân số toán học | Trong hệ nhị phân | Có biểu diễn hữu hạn cơ số 2? |
|---|---|---|
| `1/2 = 0.5` | `0.1₂` | Có |
| `1/4 = 0.25` | `0.01₂` | Có |
| `3/8 = 0.375` | `0.011₂` | Có |
| `1/10 = 0.1` | Chuỗi bit lặp vô hạn | Không |
| `1/5 = 0.2` | Chuỗi bit lặp vô hạn | Không |
| `3/10 = 0.3` | Chuỗi bit lặp vô hạn | Không |

Chẳng hạn, `0.1` trong cơ số 2 phải dùng các bit lặp vô hạn. `float` và `double` có số bit hữu hạn nên lưu **giá trị xấp xỉ gần nhất** theo quy tắc làm tròn áp dụng.

~~~mermaid
flowchart TD
    A["Literal thập phân: 0.1"] --> B["Chuyển sang giá trị nhị phân biểu diễn được gần nhất"]
    B --> C["Lưu vào float hoặc double hữu hạn bit"]
    C --> D["Thực hiện phép tính dấu phẩy động"]
    D --> E["Làm tròn kết quả theo định dạng"]
    E --> F["In số thập phân đủ để round-trip"]
~~~

**Lưu ý:** dòng in `0.1` không có nghĩa máy lưu **chính xác** số thập phân 0.1. Java dùng chuyển đổi chuỗi sao cho có thể đọc lại thành cùng giá trị `double`/`float`, nên chuỗi biểu diễn có thể ngắn hơn khai triển thập phân chính xác của giá trị nhị phân bên trong.

## 3. `float` và `double`: số bit và độ chính xác

| Đặc điểm | `float` | `double` |
|---|---|---|
| Định dạng | IEEE 754 binary32 | IEEE 754 binary64 |
| Tổng số bit | 32 | 64 |
| Bit phần mũ | 8 | 11 |
| Độ chính xác significand | 24 bit, gồm bit ẩn với số chuẩn hóa | 53 bit, gồm bit ẩn với số chuẩn hóa |
| Số chữ số thập phân chính xác xấp xỉ | 6–9 chữ số có nghĩa | 15–17 chữ số có nghĩa |
| Literal thông dụng | `0.1f` | `0.1`, `0.1d` |
| Hằng mô tả bit | `Float.SIZE` | `Double.SIZE` |
| Phù hợp | Dữ liệu cho phép sai số lớn hơn/tiết kiệm lưu trữ | Tính toán dấu phẩy động thông dụng cần độ chính xác cao hơn |

`double` **không** nghĩa là “độ chính xác tuyệt đối”, và `float` cũng không phải luôn sai: cả hai có miền ứng dụng khi dung sai được xác định.

~~~java
float f = 0.1f;
double d = 0.1;

System.out.println((double) f); // 0.10000000149011612
System.out.println(d);          // 0.1
~~~

Khi đổi `float` sang `double`, bạn **mở rộng khả năng biểu diễn nhưng không khôi phục** giá trị thập phân 0.1 vốn đã được làm tròn thành giá trị `float`. Chuỗi của `double d` in là 0.1 nhưng giá trị nhị phân lưu trong `d` cũng chỉ xấp xỉ 0.1.

## 4. Literal, phép tính và kiểu biểu thức ảnh hưởng sai số

~~~java
var a = 0.1;     // Suy luận double
var b = 0.1f;    // Suy luận float
var c = 0.1d;    // double tường minh

System.out.println(a + 0.2); // 0.30000000000000004
~~~

Trong biểu thức số học hỗn hợp, Java áp dụng *binary numeric promotion*. Ví dụ:

~~~java
float f = 0.1f;
double d = 0.2;
double mixed = f + d; // float được mở rộng sang double rồi cộng
~~~

`mixed` có kiểu `double`, nhưng toán hạng `f` **vẫn mang sai số từng có khi được lưu dưới dạng float**. Không được suy rằng “kiểu kết quả là double” nên cả hai đầu vào bỗng có 53 bit độ chính xác từ đầu.

Với số nguyên lớn, conversion sang floating-point cũng có thể mất chi tiết:

~~~java
double exactLimit = 9_007_199_254_740_992d; // 2^53
System.out.println(exactLimit + 1.0 == exactLimit); // true
~~~

Ở cỡ `2^53`, `double` không còn phân biệt được mọi cặp số nguyên liên tiếp; phép cộng 1 được làm tròn trở lại cùng giá trị.

## 5. ULP và độ phân giải phụ thuộc độ lớn

**ULP** (*unit in the last place*) biểu thị khoảng cách giữa các giá trị dấu phẩy động lân cận ở một vùng nhất định. Khi độ lớn của số tăng, khoảng cách biểu diễn thường tăng tương ứng.

~~~java
System.out.println(Math.ulp(1.0));          // 2.220446049250313E-16
System.out.println(Math.ulp(16_777_216f));  // 2.0
~~~

`Math.ulp(1.0)` mô tả bước `double` tại 1.0. Với `float`, quanh giá trị `2^24 = 16_777_216`, bước là 2, nên cộng 1 có thể không làm giá trị thay đổi:

~~~java
float x = 16_777_216f;
System.out.println(x + 1f == x); // true
~~~

**Không có một epsilon cố định bảo đảm đủ tốt cho mọi phạm vi số.** Với giá trị rất lớn, chênh lệch tuyệt đối hợp lệ có thể lớn; với giá trị rất gần 0, so sánh chỉ theo sai số tương đối có thể không phù hợp.

`Float.MIN_VALUE`/`Double.MIN_VALUE` còn là giá trị **dương nhỏ nhất khác 0**, không phải số âm nhỏ nhất. Muốn giới hạn độ lớn hữu hạn hãy xem `MAX_VALUE`; muốn xét âm thì có thể dùng `-MAX_VALUE`.

## 6. `==`: so đúng giá trị nhị phân, không so độ gần

~~~java
double calculated = 0.1 + 0.2;
double expected = 0.3;

System.out.println(calculated == expected); // false
System.out.println(calculated - expected);  // 5.551115123125783E-17
~~~

Toán tử `==` so **giá trị dấu phẩy động theo quy tắc ngôn ngữ**, không có “dung sai ngầm” dành cho sai số tính toán.

Nhưng **không** được tuyên bố rằng mọi phép so `==` trên số thực đều sai:

- So với một **hằng đặc biệt có ý nghĩa chính xác** (ví dụ `value == 0.0` khi thật sự muốn đúng zero) có thể là một yêu cầu hợp lệ, nhưng cần xem cách dữ liệu được tạo ra.
- So tính đồng nhất của **giá trị biểu diễn** đôi khi là chủ ý của thuật toán.
- Nếu cần **“gần bằng nhau” về mặt đo lường hoặc nghiệp vụ**, phải định nghĩa ngưỡng sai số phù hợp thay vì `==`.

**Đừng lấy một ngưỡng tùy tiện như `1e-9` rồi cho rằng mọi miền số đều đúng.** Ngưỡng phải phản ánh đơn vị, độ lớn, sai số đầu vào và yêu cầu của hệ thống.

## 7. So sánh xấp xỉ: dung sai tuyệt đối và tương đối

Một hàm minh họa cho bài toán cần so **gần bằng nhau**:

~~~java
static boolean nearlyEqual(
        double a, double b, double absTol, double relTol) {
    if (!Double.isFinite(absTol) || !Double.isFinite(relTol)
            || absTol < 0 || relTol < 0) {
        throw new IllegalArgumentException("invalid tolerance");
    }
    if (a == b) {
        return true; // Bao gồm +0.0/-0.0 và hai infinity cùng dấu
    }
    if (!Double.isFinite(a) || !Double.isFinite(b)) {
        return false; // NaN hoặc hai giá trị hữu hạn/vô cực không bằng nhau
    }
    double diff = Math.abs(a - b);
    double scale = Math.max(Math.abs(a), Math.abs(b));
    return diff <= absTol || diff <= relTol * scale;
}
~~~

Ví dụ:

~~~java
System.out.println(nearlyEqual(0.1 + 0.2, 0.3, 1e-12, 1e-12)); // true
System.out.println(nearlyEqual(1.0, 1.1, 1e-12, 1e-12));       // false
~~~

**Hiểu giới hạn của hàm mẫu:** phép trừ `a-b` và phép nhân `relTol*scale` cũng là số học `double`, có thể overflow/underflow ở các cực trị; hàm này phù hợp để minh họa, không phải thư viện số học tổng quát. Với giá trị Infinity, chính sách trả `true` khi cùng Infinity ở dòng `a == b` là **lựa chọn có chủ đích**, có thể đổi thành false nếu nghiệp vụ chỉ cho phép so dữ liệu hữu hạn.

Với các phép so độ chính xác nghiêm ngặt (khoa học, mô phỏng, thống kê), nên xác định rõ *error budget* và chọn thuật toán so sánh phù hợp — đôi khi dùng ULP, sai số tương đối, sai số tuyệt đối, hoặc kết hợp.

## 8. Sai số tích lũy qua vòng lặp

Một phép cộng riêng lẻ có sai số nhỏ; nhiều lần cộng có thể tích lũy sai số:

~~~java
double total = 0.0;
for (int i = 0; i < 10; i++) {
    total += 0.1;
}
System.out.println(total); // 0.9999999999999999
System.out.println(0.1 * 10); // 1.0
~~~

Hai phép toán khác nhau về đường làm tròn:

- **Cộng 10 lần:** mỗi lần cộng có thể làm tròn giá trị tạm.
- **Nhân một lần:** dùng một phép nhân dấu phẩy động, nên chỉ có trình tự làm tròn khác.

Không được suy rằng phép nhân luôn chính xác hơn phép cộng cho mọi tập dữ liệu; đây là ví dụ về **thuật toán và thứ tự tính** ảnh hưởng sai số.

Với tổng lớn hoặc nhiều số có độ lớn khác nhau, hãy xem xét **thuật toán cộng có bù** (*compensated summation*, như Kahan) hoặc các hàm/thư viện số phù hợp, rồi kiểm thử trên dữ liệu đại diện.

## 9. Phép cộng floating-point không hoàn toàn có tính kết hợp

Trong số học toán học: `(a+b)+c = a+(b+c)`. Với `double` hữu hạn, quy tắc làm tròn sau từng bước có thể khiến hai cách nhóm trả kết quả khác nhau:

~~~java
double left = (1.0e16 + -1.0e16) + 1.0;
double right = 1.0e16 + (-1.0e16 + 1.0);

System.out.println(left);  // 1.0
System.out.println(right); // 0.0
~~~

Lý do:

1. Vế `left` triệt tiêu hai số lớn thành 0 trước, rồi cộng 1.
2. Vế `right` cộng 1 vào `-1e16`; giá trị 1 quá nhỏ so với khoảng cách các số double quanh `1e16` nên bị làm tròn mất, rồi cộng `1e16` thành 0.

**Đừng tùy ý thay đổi thứ tự cộng** khi refactor một thuật toán số thực với giả định phép cộng hoàn toàn có tính kết hợp. Thứ tự, dấu ngoặc và thuật toán cộng đều có thể ảnh hưởng kết quả.

## 10. Triệt tiêu chữ số có nghĩa (*catastrophic cancellation*)

Một kiểu sai số khác xuất hiện khi **trừ hai số gần nhau nhưng rất lớn**:

~~~java
double a = 1.0e16 + 1.0;
double b = 1.0e16;

System.out.println(a - b); // 0.0
~~~

Về toán học, nếu `a` thực sự là `10^16 + 1` và `b` là `10^16`, hiệu sẽ là 1. Nhưng **khi `a` được tính và lưu trong double**, giá trị cộng 1 đã bị làm tròn mất, nên `a` và `b` có cùng biểu diễn, trừ ra 0.

Tình huống này thường quan trọng khi tính độ chênh của các đại lượng rất lớn, gần nhau. **Không thể khôi phục chữ số đã mất chỉ bằng cách ép `double` sang kiểu khác sau đó.** Cần cân nhắc thay đổi công thức, chuẩn hóa giá trị, dùng độ chính xác cao hơn từ **đầu vào**, hoặc mô hình số khác.

## 11. Kiểm tra kết quả số thực đặc biệt trước khi so sánh

`double` còn có `NaN`, `Infinity`, `-Infinity` và hai dạng zero có dấu. Chúng có thể xuất hiện do chia cho 0 hoặc phép toán vượt miền hữu hạn:

~~~java
System.out.println(Double.isNaN(0.0 / 0.0));    // true
System.out.println(Double.isFinite(1.0 / 0.0)); // false
System.out.println(0.0 == -0.0);                // true
~~~

`Double.isFinite` **không kiểm tra độ chính xác**: `0.1 + 0.2` vẫn hữu hạn nhưng xấp xỉ. Khi kiểm tra giá trị đầu vào, cần phân biệt dữ liệu bất hợp lệ với sai số cho phép. Bài trước đã trình bày đầy đủ quy tắc NaN, so sánh wrapper, dấu zero và underflow.

## 12. Định dạng hiển thị không sửa giá trị đã tính

~~~java
double sum = 0.1 + 0.2;

System.out.println(sum); // 0.30000000000000004
System.out.printf(java.util.Locale.ROOT, "%.2f%n", sum); // 0.30
~~~

`printf("%.2f")` hoặc `String.format` chỉ **làm tròn biểu diễn văn bản** theo định dạng đã chọn. Biến `sum` vẫn chứa giá trị `double` ban đầu.

Các nhầm lẫn cần tránh:

- Hiển thị `0.30` **không** có nghĩa số được lưu chính xác bằng 0.30 toán học.
- Gọi `Math.round(...)` không thay thế một mô hình tính tiền theo số thập phân.
- Ép `float` thành `double` **sau khi** sai số xuất hiện không khôi phục dữ liệu đã mất.

Hãy phân biệt **số được lưu**, **quy tắc tính toán** và **cách định dạng đầu ra**.

## 13. Từ sai số binary floating-point đến mô hình decimal exact

Khi nghiệp vụ cần `0.1 + 0.2` thành **0.3 đúng thập phân**, giữ nguồn thập phân chính xác ngay từ đầu:

~~~java
java.math.BigDecimal a = new java.math.BigDecimal("0.1");
java.math.BigDecimal b = new java.math.BigDecimal("0.2");
System.out.println(a.add(b)); // 0.3.
~~~

Nguồn tạo đối tượng quyết định giá trị đầu vào:

| Cách tạo | Ý nghĩa |
| --- | --- |
| `new BigDecimal("0.1")` | Lưu số thập phân đúng 0.1 |
| `BigDecimal.valueOf(0.1)` | Dựa vào dạng thập phân của `Double.toString(0.1)` |
| `new BigDecimal(0.1)` | Lưu **chính xác giá trị nhị phân xấp xỉ** đang ở `double` |

~~~java
System.out.println(
        new java.math.BigDecimal("0.1")
                .compareTo(new java.math.BigDecimal(0.1)) == 0); // false.
~~~

`BigDecimal` cũng không khiến mọi phép chia tự kết thúc. `1/3` có khai triển thập phân vô hạn, nên cần chỉ định scale/rounding hoặc `MathContext`:

~~~java
java.math.BigDecimal third = java.math.BigDecimal.ONE.divide(
        new java.math.BigDecimal("3"), 2,
        java.math.RoundingMode.HALF_UP);
System.out.println(third); // 0.33.
~~~

`BigDecimal.equals` xét **giá trị và scale**: `new BigDecimal("1.00").equals(new BigDecimal("1.0"))` trả false; `compareTo` của cặp đó trả 0. Phần ranh giới này đủ để hiểu **vì sao không thể phục hồi decimal chính xác từ một `double` đã sai số**; bài `Math.*Exact`/`BigDecimal` cuối chương đi sâu về scale, precision, làm tròn, phép chia và chuyển đổi exact.

`BigDecimal` không mặc định tốt hơn `double` trong mọi ứng dụng. Tính toán khoa học chấp nhận sai số có thể ưu tiên `double` và thuật toán ổn định. Hãy chọn theo miền, độ chính xác, đơn vị và chính sách làm tròn.


## 14. Quy trình chọn kiểu số theo bài toán

| Nhu cầu | Lựa chọn thường phù hợp | Điểm phải kiểm tra |
|---|---|---|
| Bộ đếm nguyên trong miền 32 bit | `int` | Tràn số nguyên |
| Bộ đếm lớn hơn 32 bit | `long` | Vẫn có giới hạn 64 bit |
| Đo đạc khoa học, phép tính chấp nhận sai số | `double` | Dung sai, NaN, Infinity, thuật toán |
| Dữ liệu float cho API/phần cứng yêu cầu binary32 | `float` | Độ chính xác ~24 bit significand |
| Tiền lưu bằng đơn vị nhỏ nhất cố định | `long` khi miền đủ | Đơn vị, quy đổi và overflow |
| Giá trị thập phân phải chính xác | `BigDecimal` | Tạo từ text, scale, rounding |
| Tổng rất nhiều số thực | `double` + thuật toán phù hợp | Sai số tích lũy và thứ tự tính |
| Kiểm tra gần bằng nhau | Dung sai theo nghiệp vụ | Sai số tuyệt đối/tương đối, cực trị |

**Không có “kiểu đúng cho mọi số thực”.** Quy tắc thiết kế là xác định **miền giá trị, độ chính xác yêu cầu, đơn vị đo, dung sai và quy tắc làm tròn**, rồi mới chọn kiểu và thuật toán.

## 15. Ví dụ Java 17 chạy độc lập

Tạo file chính xác **`BinaryFloatingPointErrorDemo.java`**:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public final class BinaryFloatingPointErrorDemo {
    private BinaryFloatingPointErrorDemo() {}

    static boolean nearlyEqual(
            double a, double b, double absTol, double relTol) {
        if (!Double.isFinite(absTol) || !Double.isFinite(relTol)
                || absTol < 0 || relTol < 0) {
            throw new IllegalArgumentException("invalid tolerance");
        }
        if (a == b) {
            return true;
        }
        if (!Double.isFinite(a) || !Double.isFinite(b)) {
            return false;
        }
        double difference = Math.abs(a - b);
        double scale = Math.max(Math.abs(a), Math.abs(b));
        return difference <= absTol || difference <= relTol * scale;
    }

    public static void main(String[] args) {
        double sum = 0.1 + 0.2;
        System.out.println("double sum=" + sum);
        System.out.println("sum == 0.3=" + (sum == 0.3));
        System.out.println("difference=" + (sum - 0.3));
        System.out.println("hex 0.1=" + Double.toHexString(0.1));

        float floatApprox = 0.1f;
        System.out.println("float 0.1 as double=" + (double) floatApprox);
        System.out.println("double literal printed=" + 0.1);
        System.out.println("float ulp at 2^24=" + Math.ulp(16_777_216f));
        System.out.println("float loses one="
                + (16_777_216f + 1f == 16_777_216f));
        double limit = 9_007_199_254_740_992d;
        System.out.println("double loses one=" + (limit + 1 == limit));

        double total = 0.0;
        for (int i = 0; i < 10; i++) {
            total += 0.1;
        }
        System.out.println("sum ten times=" + total);
        System.out.println("multiply ten=" + (0.1 * 10));

        double left = (1.0e16 + -1.0e16) + 1.0;
        double right = 1.0e16 + (-1.0e16 + 1.0);
        System.out.println("group left=" + left);
        System.out.println("group right=" + right);
        System.out.println("cancellation=" + ((1.0e16 + 1.0) - 1.0e16));

        System.out.println("near 0.3="
                + nearlyEqual(sum, 0.3, 1e-12, 1e-12));
        System.out.println("far from 1.1="
                + nearlyEqual(1.0, 1.1, 1e-12, 1e-12));
        System.out.println("NaN equals itself=" + (Double.NaN == Double.NaN));
        System.out.println("is NaN=" + Double.isNaN(0.0 / 0.0));
        System.out.println("infinite finite=" + Double.isFinite(1.0 / 0.0));
        System.out.println("signed zero ==" + (0.0 == -0.0));
        System.out.println("signed zero compare="
                + (Double.compare(0.0, -0.0) == 0));

        System.out.println("formatted="
                + String.format(Locale.ROOT, "%.2f", sum));

        BigDecimal decimal = new BigDecimal("0.1")
                .add(new BigDecimal("0.2"));
        System.out.println("decimal exact=" + decimal);
        System.out.println("valueOf decimal="
                + BigDecimal.valueOf(0.1).add(BigDecimal.valueOf(0.2)));
        System.out.println("double constructor exact decimal="
                + (new BigDecimal(0.1)
                        .compareTo(new BigDecimal("0.1")) == 0));
        System.out.println("decimal division="
                + new BigDecimal("1").divide(
                        new BigDecimal("3"), 2, RoundingMode.HALF_UP));
        BigDecimal scaleA = new BigDecimal("1.00");
        BigDecimal scaleB = new BigDecimal("1.0");
        System.out.println("BigDecimal equals=" + scaleA.equals(scaleB));
        System.out.println("BigDecimal compare="
                + (scaleA.compareTo(scaleB) == 0));
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên, terminal đang ở thư mục chứa tệp. Không cần Maven, Gradle hoặc thư viện ngoài JDK.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 BinaryFloatingPointErrorDemo.java
java -cp . BinaryFloatingPointErrorDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\BinaryFloatingPointErrorDemo.java
java -cp . BinaryFloatingPointErrorDemo
~~~

**Exact expected output:**

~~~text
double sum=0.30000000000000004
sum == 0.3=false
difference=5.551115123125783E-17
hex 0.1=0x1.999999999999ap-4
float 0.1 as double=0.10000000149011612
double literal printed=0.1
float ulp at 2^24=2.0
float loses one=true
double loses one=true
sum ten times=0.9999999999999999
multiply ten=1.0
group left=1.0
group right=0.0
cancellation=0.0
near 0.3=true
far from 1.1=false
NaN equals itself=false
is NaN=true
infinite finite=false
signed zero ==true
signed zero compare=false
formatted=0.30
decimal exact=0.3
valueOf decimal=0.3
double constructor exact decimal=false
decimal division=0.33
BigDecimal equals=false
BigDecimal compare=true
~~~

### Đọc kết quả theo từng nhóm

1. `double sum` và `difference` cho thấy sai lệch nhỏ nhưng có thể quan sát được; `Double.toHexString` biểu diễn giá trị lưu trong dạng hex floating-point.
2. `float 0.1 as double` cho thấy nâng một giá trị float sang double **không phục hồi** số thập phân ban đầu.
3. `float ulp at 2^24` và `double loses one` minh họa độ phân giải giảm theo độ lớn số.
4. Tổng cộng 0.1 mười lần khác với nhân 0.1 một lần; thứ tự và số lần làm tròn ảnh hưởng kết quả.
5. `group left/right` và `cancellation` minh họa không kết hợp hoàn toàn và mất chữ số có nghĩa.
6. `nearlyEqual` chỉ trả true cho hai số gần nhau theo dung sai minh họa; nó **không phải so sánh chính xác toán học**.
7. `NaN`, Infinity và signed zero có những quy tắc đặc biệt cần xử lý tường minh.
8. `formatted=0.30` chỉ là chuỗi hiển thị; `BigDecimal` từ chuỗi mới thực hiện phép cộng thập phân chính xác trong ví dụ.
9. Hai kết quả cuối nhắc rằng `BigDecimal.equals` phân biệt scale, còn `compareTo` so giá trị số.

## 16. Lỗi thường gặp và cách khắc phục

| Ngộ nhận / lỗi | Vấn đề | Cách xử lý |
|---|---|---|
| `0.1 + 0.2 == 0.3` phải true | Các số decimal không biểu diễn chính xác trong binary64 | Dùng dung sai nếu bài toán cho phép, hoặc decimal exact |
| Đổi `float` sang `double` là hết sai số | Giá trị float đã làm tròn | Chọn độ chính xác phù hợp **từ đầu** |
| In `0.1` nghĩa là lưu đúng thập phân 0.1 | `toString` dùng chuỗi round-trip | Phân biệt chuỗi hiển thị với giá trị nhị phân |
| `double` lưu được mọi số nguyên lớn | Quá 2^53 không phân biệt mọi số nguyên kề nhau | Dùng `long`/`BigInteger` theo miền |
| Một `epsilon` phù hợp mọi bài toán | Sai số phụ thuộc đơn vị và độ lớn | Xác định absolute/relative tolerance |
| Cộng 0.1 nhiều lần luôn bằng nhân một lần | Các bước làm tròn khác nhau | Kiểm thử thuật toán tổng và độ chính xác |
| Có thể đổi nhóm phép cộng số thực tùy ý | Floating-point không kết hợp hoàn toàn | Giữ thứ tự/nghiệp vụ của thuật toán |
| `(1e16 + 1) - 1e16` luôn là 1 | Mất 1 khi cộng số quá lớn | Chọn thuật toán/kiểu khác từ trước |
| NaN so bằng `==` với chính nó | `NaN == NaN` là false | Dùng `Double.isNaN` |
| Formatter `%.2f` sửa sai số tính toán | Chỉ đổi biểu diễn text | Quy định kiểu dữ liệu và rounding thực |
| `new BigDecimal(0.1)` cho decimal 0.1 | Constructor đọc chính xác giá trị double xấp xỉ | `new BigDecimal("0.1")` hoặc `valueOf` |
| `BigDecimal.divide(1/3)` luôn tự chạy | Decimal vô hạn cần quy tắc rounding | Dùng scale/MathContext phù hợp |
| `BigDecimal("1.00").equals(BigDecimal("1.0"))` | `equals` xét cả scale | `compareTo == 0` nếu muốn so giá trị |

## 17. Bài tập và đáp án

### Bài A — quan sát sai số cơ bản

~~~java
double a = 0.1 + 0.2;
System.out.println(a);
System.out.println(a == 0.3);
~~~

**Đáp án:**

~~~text
0.30000000000000004
false
~~~

### Bài B — `float` không chính xác hơn khi cast

~~~java
float value = 0.1f;
double promoted = value;
System.out.println(promoted);
~~~

**Đáp án:** `0.10000000149011612`. Chuyển sang double giữ nguyên giá trị xấp xỉ đang có trong float.

### Bài C — số nguyên lớn

~~~java
double x = 9_007_199_254_740_992d;
System.out.println(x + 1.0 == x);
~~~

**Đáp án:** `true`. Tại `2^53`, binary64 không biểu diễn được mọi số nguyên kề nhau.

### Bài D — dùng thập phân exact

~~~java
java.math.BigDecimal a = new java.math.BigDecimal("0.1");
java.math.BigDecimal b = new java.math.BigDecimal("0.2");
System.out.println(a.add(b));
~~~

**Đáp án:** `0.3`.

### Bài E — tại sao `BigDecimal` chia cần rounding?

~~~java
// new BigDecimal("1").divide(new BigDecimal("3"))
~~~

**Đáp án:** `1/3` là thập phân vô hạn; không thể biểu diễn thành số thập phân hữu hạn chính xác. Chỉ định scale và `RoundingMode`, hoặc `MathContext`, theo yêu cầu nghiệp vụ.

### Bài F — dấu ngoặc số thực

~~~java
System.out.println((1e16 + -1e16) + 1.0);
System.out.println(1e16 + (-1e16 + 1.0));
~~~

**Đáp án:**

~~~text
1.0
0.0
~~~

Sự khác biệt đến từ việc làm tròn ở các bước trung gian.

## 18. Checklist và kết luận

- [ ] Hiểu `float`/`double` biểu diễn **nhị phân hữu hạn**, không phải số thập phân tùy ý.
- [ ] Biết `0.1` và `0.2` không có biểu diễn nhị phân hữu hạn chính xác.
- [ ] Phân biệt binary32 (float) và binary64 (double), độ chính xác hữu hạn.
- [ ] Biết literal `0.1f` là float, `0.1` là double.
- [ ] Hiểu chuyển từ float sang double **không phục hồi** sai số đã phát sinh.
- [ ] Hiểu ý nghĩa ULP và việc số lớn có bước biểu diễn lớn hơn.
- [ ] Không so số đo/tính gần bằng bằng `==` nếu yêu cầu có dung sai.
- [ ] Biết chọn dung sai tuyệt đối/tương đối theo miền và đơn vị nghiệp vụ.
- [ ] Nhận diện sai số tích lũy và kết quả phụ thuộc thứ tự cộng.
- [ ] Nhận diện catastrophic cancellation khi trừ số gần nhau.
- [ ] Xử lý NaN/Infinity/signed zero rõ ràng khi cần.
- [ ] Biết định dạng số chỉ tác động **chuỗi hiển thị**.
- [ ] Biết tạo `BigDecimal` từ String khi cần decimal chính xác.
- [ ] Biết `BigDecimal.divide` cần rounding khi thương không kết thúc.
- [ ] Phân biệt `BigDecimal.equals` với `compareTo` về scale.
- [ ] Biên dịch và chạy `BinaryFloatingPointErrorDemo.java` trên JDK 17+, đối chiếu exact expected output.

**Kết luận:** Sai số của `float`/`double` là hệ quả tự nhiên của biểu diễn dấu phẩy động nhị phân hữu hạn, không phải Java tính toán ngẫu nhiên. Hãy chọn kiểu dữ liệu theo **mức chính xác và miền giá trị**, tránh so gần bằng bằng `==` một cách máy móc, cân nhắc tác động của thứ tự phép tính, và dùng `BigDecimal`/số nguyên theo đơn vị cố định nếu nghiệp vụ đòi hỏi số thập phân chính xác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="arithmetic-009">Giới thiệu Math cho trị tuyệt đối, lũy thừa và làm tròn</a>

<details>
<summary>Xem chi tiết</summary>

# Giới thiệu Math: trị tuyệt đối, lũy thừa và làm tròn trong Java

> **Java core — language-basics / Số học**  
> **Môi trường:** Java 17+, chỉ dùng thư viện chuẩn.  
> **Mục tiêu:** Dùng đúng `Math.abs`, `Math.pow`, `Math.round`, `Math.floor` và `Math.ceil`; hiểu kiểu trả về, cách xử lý số âm, giới hạn kiểu nguyên và sai số số thực.

## 1. Lớp `Math` dùng như thế nào?

`java.lang.Math` cung cấp các phương thức `static` cho tính toán số học; không cần tạo đối tượng `Math` hay viết lệnh `import`:

~~~java
int distance = Math.abs(-12);         // 12
double area = Math.pow(3, 2);        // 9.0
long rounded = Math.round(2.7);      // 3L
double below = Math.floor(2.7);      // 2.0
double above = Math.ceil(2.7);       // 3.0
~~~

**Quan trọng:** `Math.pow`, `Math.floor` và `Math.ceil` trả `double`. `Math.round(float)` trả `int`, còn `Math.round(double)` trả `long`. Phương thức `Math.abs` có các overload cho `int`, `long`, `float`, `double` và trả kiểu tương ứng.

## 2. Trị tuyệt đối với `Math.abs`

Trị tuyệt đối thể hiện độ lớn không xét dấu:

~~~java
Math.abs(-8);     // int 8
Math.abs(-3.25);  // double 3.25
Math.abs(0);      // int 0
~~~

Dùng trong tính khoảng cách hoặc sai lệch, nhưng lưu ý **ngoại lệ về miền giá trị nguyên**. `int` có miền từ `-2147483648` đến `2147483647`, nên trị tuyệt đối toán học của `Integer.MIN_VALUE` không chứa được trong `int`:

~~~java
Math.abs(Integer.MIN_VALUE); // Vẫn là -2147483648, không ném lỗi!
~~~

Tương tự, `Math.abs(Long.MIN_VALUE)` vẫn âm. Nếu muốn **phát hiện trường hợp không biểu diễn được**, Java 17 hỗ trợ `Math.absExact(int)` và `Math.absExact(long)`, ném `ArithmeticException` khi gặp giá trị nhỏ nhất. Nếu cần trị tuyệt đối của một `int` dưới dạng `long`, hãy **chuyển kiểu trước**:

~~~java
long safe = Math.abs((long) Integer.MIN_VALUE); // 2147483648.
~~~

Đừng ép kiểu sau `Math.abs(Integer.MIN_VALUE)` vì phép tính ở kiểu `int` đã cho kết quả ngoài mong đợi.

## 3. Lũy thừa với `Math.pow`

`Math.pow(base, exponent)` trả `double`, kể cả khi truyền vào hai số nguyên:

~~~java
Math.pow(2, 3);    // 8.0
Math.pow(10, 2);   // 100.0
Math.pow(4, -1);   // 0.25
Math.pow(9, 0.5);  // 3.0: tương đương căn bậc hai của 9.
~~~

| Trường hợp | Kết quả và lưu ý |
| --- | --- |
| `Math.pow(2, 10)` | `1024.0`, kiểu `double` |
| `Math.pow(4, -1)` | `0.25`, số mũ âm |
| `Math.pow(-2, 0.5)` | `NaN`: không có kết quả thực |
| `Math.pow(2, 0)` | `1.0` |
| `Math.sqrt(81)` | `9.0`, API chuyên dụng để lấy căn bậc hai |

`Math.pow` tính bằng số thực, **không bảo đảm chính xác tuyệt đối** đối với số nguyên lớn. Tránh chuyển thẳng kết quả `double` về `int`/`long` để tính lũy thừa nguyên lớn. Nếu cần phép lũy thừa nguyên chính xác, dùng `BigInteger.pow(int)` khi miền số có thể vượt kiểu nguyên; nếu cần phát hiện tràn trong phép nhân nguyên, xem `Math.multiplyExact`.

## 4. Làm tròn: `round`, `floor`, `ceil` và `rint`

| Đầu vào | `Math.round(double)` | `Math.floor(double)` | `Math.ceil(double)` |
| --- | ---: | ---: | ---: |
| `2.2` | `2` | `2.0` | `3.0` |
| `2.5` | `3` | `2.0` | `3.0` |
| `-2.2` | `-2` | `-3.0` | `-2.0` |
| `-2.5` | `-2` | `-3.0` | `-2.0` |
| `-2.7` | `-3` | `-3.0` | `-2.0` |

- **`Math.floor(x)`:** số nguyên lớn nhất không vượt quá `x`, nhưng **kiểu trả về là `double`**. Với số âm, `floor(-2.7) == -3.0`.
- **`Math.ceil(x)`:** số nguyên nhỏ nhất không nhỏ hơn `x`, kiểu trả về `double`. Với số âm, `ceil(-2.7) == -2.0`.
- **`Math.round(x)`:** làm tròn về số nguyên gần nhất; khi đúng giữa hai số nguyên, chọn số **về phía dương**. Vì vậy `round(2.5) == 3` nhưng `round(-2.5) == -2`, không phải -3.
- **`Math.rint(x)`:** trả `double`, trường hợp đúng nửa chọn **số nguyên chẵn gần nhất** (*ties to even*): `rint(2.5) == 2.0` và `rint(3.5) == 4.0`.

Không nhầm `Math.round` với cách làm tròn luôn ra xa số 0. Giá trị `Double.NaN` và vô cực có xử lý đặc biệt trong `round`; nếu nghiệp vụ cần số hữu hạn, kiểm tra `Double.isFinite` trước.

## 5. Chương trình Java 17+ chạy được

Tạo file **MathBasicsDemo.java**:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MathBasicsDemo {
    public static void main(String[] args) {
        System.out.println("absInt=" + Math.abs(-17));
        System.out.println("absDouble=" + Math.abs(-4.25));
        System.out.println("absMinInt=" + Math.abs(Integer.MIN_VALUE));
        System.out.println("absMinAsLong="
                + Math.abs((long) Integer.MIN_VALUE));

        try {
            System.out.println("absExact="
                    + Math.absExact(Integer.MIN_VALUE));
        } catch (ArithmeticException ex) {
            System.out.println("absExact=ArithmeticException");
        }

        System.out.println("power=" + Math.pow(2, 10));
        System.out.println("negativePower=" + Math.pow(4, -1));
        System.out.println("rootViaPow=" + Math.pow(9, 0.5));
        System.out.println("negativeRootNaN="
                + Double.isNaN(Math.pow(-2, 0.5)));

        int roundedFloat = Math.round(2.5f);
        long roundedDouble = Math.round(2.5);
        System.out.println("roundFloat=" + roundedFloat);
        System.out.println("roundDouble=" + roundedDouble);
        System.out.println("roundNegative=" + Math.round(-2.5));
        System.out.println("roundBelowHalf=" + Math.round(2.49));

        System.out.println("floorPositive=" + Math.floor(2.7));
        System.out.println("ceilPositive=" + Math.ceil(2.7));
        System.out.println("floorNegative=" + Math.floor(-2.7));
        System.out.println("ceilNegative=" + Math.ceil(-2.7));
        System.out.println("rintEven2=" + Math.rint(2.5));
        System.out.println("rintEven4=" + Math.rint(3.5));

        BigDecimal value = new BigDecimal("2.345");
        System.out.println("moneyHalfUp="
                + value.setScale(2, RoundingMode.HALF_UP));
        System.out.println("moneyHalfEven="
                + value.setScale(2, RoundingMode.HALF_EVEN));
    }
}
~~~

Mở terminal tại thư mục chứa file và chạy (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 MathBasicsDemo.java
java MathBasicsDemo
~~~

**Expected output chính xác:**

~~~text
absInt=17
absDouble=4.25
absMinInt=-2147483648
absMinAsLong=2147483648
absExact=ArithmeticException
power=1024.0
negativePower=0.25
rootViaPow=3.0
negativeRootNaN=true
roundFloat=3
roundDouble=3
roundNegative=-2
roundBelowHalf=2
floorPositive=2.0
ceilPositive=3.0
floorNegative=-3.0
ceilNegative=-2.0
rintEven2=2.0
rintEven4=4.0
moneyHalfUp=2.35
moneyHalfEven=2.34
~~~

Giải thích các dòng dễ nhầm: `absMinInt` vẫn âm vì không có `int` dương tương ứng; `absExact` chủ động phát hiện tràn; `negativeRootNaN` là true vì lũy thừa có cơ số âm và số mũ phân số không cho giá trị thực; `roundNegative=-2` do quy tắc chọn phía dương tại điểm chính giữa. Hai kiểu làm tròn `BigDecimal` thể hiện chính sách khác nhau với chữ số 5.

## 6. Khi nào nên dùng API nào?

| Bài toán | API gợi ý | Cần kiểm tra thêm |
| --- | --- | --- |
| Độ lớn sai lệch, bỏ dấu | `Math.abs` | `MIN_VALUE` của `int`/`long` |
| Trị tuyệt đối nguyên, không chấp nhận tràn | `Math.absExact` | Bắt/đưa tiếp `ArithmeticException` |
| Lũy thừa thực | `Math.pow` | `NaN`, vô cực, sai số `double` |
| Căn bậc hai số không âm | `Math.sqrt` | Đầu vào âm cho `NaN` |
| Làm tròn gần nhất theo `Math` | `Math.round` | Quy tắc nửa về phía dương, kiểu trả về |
| Luôn làm tròn xuống / lên | `Math.floor` / `Math.ceil` | Số âm và kiểu `double` |
| Nửa chọn số nguyên chẵn | `Math.rint` | Kiểu trả về `double` |
| Tiền tệ cần quy định số chữ số thập phân | `BigDecimal.setScale` | Chọn `RoundingMode` theo nghiệp vụ |

**Đừng chuyển `double` sang `int` để “làm tròn”**: ép kiểu số thực sang số nguyên bỏ phần lẻ theo hướng về 0, không tương đương với `Math.round` hay `Math.floor`. Tính tiền hoặc số liệu cần thập phân chính xác phải quy định rõ độ chính xác, không chỉ gọi `Math.round(x * 100) / 100.0` rồi mặc định tránh được sai số nhị phân.

## 7. Bài tập tự luyện

**Bài 1.** Tính `Math.abs(-25)` và `Math.abs(-3.75)`. Chúng trả về kiểu nào?

**Bài 2.** Vì sao `Math.abs(Integer.MIN_VALUE)` vẫn âm? Viết hai cách xử lý: chuyển sang `long` trước, hoặc phát hiện lỗi bằng `Math.absExact`.

**Bài 3.** Dự đoán `Math.pow(2, 5)`, `Math.pow(4, -1)` và `Math.pow(-2, 0.5)`. Kết quả thuộc kiểu gì?

**Bài 4.** So sánh `Math.round(-2.5)`, `Math.floor(-2.5)`, `Math.ceil(-2.5)` và `Math.rint(2.5)`. Chú ý quy tắc tại giá trị đúng nửa.

**Bài 5.** Chọn một `RoundingMode` thích hợp cho báo giá thập phân có hai chữ số; nêu vì sao phải ghi rõ chính sách làm tròn trước khi cài đặt.

### Ghi nhớ

`Math.abs` tính trị tuyệt đối nhưng có ngoại lệ với giá trị nhỏ nhất của `int`/`long`; `Math.pow` trả `double` và có thể mang sai số/`NaN`. `Math.round` làm tròn gần nhất (đúng nửa chọn phía dương), còn `Math.floor` và `Math.ceil` luôn về phía âm/dương vô cực; `Math.rint` chọn số chẵn khi đúng nửa. Hãy chọn hàm theo **kiểu dữ liệu và chính sách làm tròn** thật sự cần.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="arithmetic-010">Giới thiệu phép toán exact và BigDecimal khi yêu cầu vượt khả năng kiểu cơ bản</a>

<details>
<summary>Xem chi tiết</summary>

# Phép toán exact và `BigDecimal` khi kiểu cơ bản không đáp ứng yêu cầu — Java 17+

Các kiểu số nguyên cơ bản (`int`, `long`) và số thực (`float`, `double`) rất hữu ích nhưng có giới hạn:

- **`int` và `long` có miền giá trị hữu hạn.** Phép cộng, trừ hoặc nhân nguyên thông thường có thể **tràn số** (*overflow*) và trả giá trị sai theo phép tính toán học mà không ném ngoại lệ.
- **`float` và `double` biểu diễn số thực bằng hệ nhị phân.** Một số giá trị thập phân như `0.1` không thể biểu diễn chính xác; chúng còn có thể làm tròn, underflow hoặc overflow.
- Khi hệ thống cần **phát hiện tràn số nguyên**, dùng các phương thức `Math.*Exact()`; khi cần **số nguyên rất lớn**, dùng `BigInteger`; khi cần **phép tính thập phân với độ chính xác được quản lý rõ**, dùng `BigDecimal`.

Đây là ba giải pháp cho **những nhu cầu khác nhau**. `Math.addExact` không mở rộng miền `int`; `BigDecimal` không tự động quyết định cách làm tròn cho tất cả bài toán.

## 1. Bảng lựa chọn nhanh

| Vấn đề | Giải pháp Java 17+ | Ý nghĩa |
| --- | --- | --- |
| `int` cộng/trừ/nhân có thể tràn | `Math.addExact`, `subtractExact`, `multiplyExact` | Ném `ArithmeticException` khi kết quả ngoài miền |
| `long` cộng/trừ/nhân có thể tràn | Overload nhận `long` của các phương thức trên | Vẫn dùng kiểu `long`, nhưng kiểm tra overflow |
| Tăng/giảm/đổi dấu ở biên | `incrementExact`, `decrementExact`, `negateExact` | Phát hiện kết quả vượt miền |
| Chuyển `long` sang `int` an toàn | `Math.toIntExact(long)` | Không âm thầm cắt các bit cao |
| Số nguyên lớn hơn `long` | `java.math.BigInteger` | Độ dài số nguyên không bị giới hạn bởi 64 bit, nhưng vẫn phụ thuộc bộ nhớ |
| Tiền, thuế, số lượng thập phân | `java.math.BigDecimal` | Phép tính thập phân theo `unscaled value × 10^(-scale)` |
| Phép chia thập phân không kết thúc | `BigDecimal.divide` cùng `scale`/`RoundingMode` hoặc `MathContext` | Quy định độ chính xác và làm tròn |
| Số thực xấp xỉ, đo đạc khoa học | `double`, kèm chính sách sai số | Thường thích hợp khi độ chính xác nhị phân là chấp nhận được |

**Nguyên tắc:** chọn kiểu số theo **miền giá trị và yêu cầu chính xác của nghiệp vụ**, không chỉ vì cú pháp tiện.

## 2. Phép toán nguyên `exact`: phát hiện overflow

Java không mặc định ném ngoại lệ khi tràn số nguyên:

~~~java
int wrapped = Integer.MAX_VALUE + 1;
System.out.println(wrapped); // -2147483648.
~~~

Giá trị toán học của `2_147_483_647 + 1` là `2_147_483_648`, ngoài miền `int`; phép tính nguyên thông thường cho kết quả bị tràn theo biểu diễn 32-bit.

Để **yêu cầu phép tính phải nằm trong miền**, dùng:

~~~java
int total = Math.addExact(100, 250); // 350.

// Math.addExact(Integer.MAX_VALUE, 1)
// -> nem ArithmeticException khi chay.
~~~

| Phương thức | Ví dụ | Kết quả nếu vượt miền |
| --- | --- | --- |
| `Math.addExact(a, b)` | `Math.addExact(100, 250)` → `350` | `ArithmeticException` |
| `Math.subtractExact(a, b)` | `Math.subtractExact(10, 15)` → `-5` | `ArithmeticException` |
| `Math.multiplyExact(a, b)` | `Math.multiplyExact(100, 200)` → `20000` | `ArithmeticException` |
| `Math.incrementExact(a)` | `Math.incrementExact(5)` → `6` | `ArithmeticException` |
| `Math.decrementExact(a)` | `Math.decrementExact(5)` → `4` | `ArithmeticException` |
| `Math.negateExact(a)` | `Math.negateExact(-5)` → `5` | `ArithmeticException` |
| `Math.toIntExact(x)` | `Math.toIntExact(42L)` → `42` | `ArithmeticException` nếu không vừa `int` |

Các phương thức này **không trả về số có độ rộng tùy ý**. `Math.multiplyExact(int, int)` vẫn trả `int`; muốn nhận một tích có thể lớn hơn `int` thì dùng toán hạng và kiểu `long` phù hợp, hoặc `BigInteger`.

**Cẩn thận thứ tự:** `Math.addExact(a * b, c)` không phát hiện overflow đã xảy ra **trong `a * b`**. Hãy kiểm tra chính phép nhân bằng `Math.multiplyExact(a, b)` trước khi đưa kết quả vào phép cộng.

`Math.*Exact` không biến phép tính số thực `double` thành thập phân chính xác; chúng dành cho số nguyên và chuyển đổi số nguyên theo các overload được hỗ trợ.

## 3. Chương trình Java 17+ độc lập và chạy được

Lưu tệp UTF-8 tên **`ExactAndBigDecimalDemo.java`**:

~~~java
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public class ExactAndBigDecimalDemo {
    public static void main(String[] args) {
        System.out.println("== exact integers ==");
        System.out.println("wrappedInt=" + (Integer.MAX_VALUE + 1));
        System.out.println("safeAdd=" + Math.addExact(100, 250));
        System.out.println("safeLongMultiply="
                + Math.multiplyExact(50_000L, 50_000L));

        try {
            Math.addExact(Integer.MAX_VALUE, 1);
        } catch (ArithmeticException ex) {
            System.out.println("addOverflow=ArithmeticException");
        }

        try {
            Math.multiplyExact(50_000, 50_000);
        } catch (ArithmeticException ex) {
            System.out.println("multiplyOverflow=ArithmeticException");
        }

        try {
            Math.subtractExact(Integer.MIN_VALUE, 1);
        } catch (ArithmeticException ex) {
            System.out.println("subtractOverflow=ArithmeticException");
        }

        try {
            Math.toIntExact(3_000_000_000L);
        } catch (ArithmeticException ex) {
            System.out.println("narrowingOverflow=ArithmeticException");
        }

        System.out.println("== big integers ==");
        BigInteger left = new BigInteger("3000000000");
        BigInteger right = new BigInteger("4000000000");
        BigInteger product = left.multiply(right);
        System.out.println("bigProduct=" + product);
        System.out.println("bigProductPlusOne="
                + product.add(BigInteger.ONE));

        System.out.println("== exact decimals ==");
        BigDecimal a = new BigDecimal("0.1");
        BigDecimal b = new BigDecimal("0.2");
        System.out.println("decimalSum=" + a.add(b).toPlainString());
        System.out.println("binaryDoubleSum=" + (0.1 + 0.2));

        BigDecimal price = new BigDecimal("19.99");
        BigDecimal quantity = new BigDecimal("3");
        System.out.println("lineTotal="
                + price.multiply(quantity).toPlainString());

        BigDecimal amount = new BigDecimal("12.50");
        amount.add(new BigDecimal("0.50"));
        System.out.println("unchangedAmount=" + amount.toPlainString());
        amount = amount.add(new BigDecimal("0.50"));
        System.out.println("updatedAmount=" + amount.toPlainString());

        System.out.println("== division and rounding ==");
        System.out.println("oneEighth="
                + BigDecimal.ONE.divide(new BigDecimal("8")));

        try {
            BigDecimal.ONE.divide(new BigDecimal("3"));
        } catch (ArithmeticException ex) {
            System.out.println("nonTerminating=ArithmeticException");
        }

        System.out.println("oneThirdRounded="
                + BigDecimal.ONE.divide(
                        new BigDecimal("3"), 2, RoundingMode.HALF_UP));
        System.out.println("halfUp="
                + new BigDecimal("10.005").setScale(
                        2, RoundingMode.HALF_UP));
        System.out.println("halfEvenDown="
                + new BigDecimal("2.345").setScale(
                        2, RoundingMode.HALF_EVEN));
        System.out.println("halfEvenUp="
                + new BigDecimal("2.355").setScale(
                        2, RoundingMode.HALF_EVEN));

        System.out.println("== construction and equality ==");
        BigDecimal fromText = new BigDecimal("0.1");
        BigDecimal fromDouble = new BigDecimal(0.1);
        System.out.println("doubleConstructorEquals="
                + fromText.equals(fromDouble));
        System.out.println("valueOfEquals="
                + fromText.equals(BigDecimal.valueOf(0.1)));

        BigDecimal twoOneDecimal = new BigDecimal("2.0");
        BigDecimal twoTwoDecimals = new BigDecimal("2.00");
        System.out.println("scaleSensitiveEquals="
                + twoOneDecimal.equals(twoTwoDecimals));
        System.out.println("numericCompare="
                + twoOneDecimal.compareTo(twoTwoDecimals));
        System.out.println("stripZeros="
                + twoTwoDecimals.stripTrailingZeros().toPlainString());

        System.out.println("intExact="
                + new BigDecimal("123.00").intValueExact());
        try {
            new BigDecimal("123.5").intValueExact();
        } catch (ArithmeticException ex) {
            System.out.println("fractionToInt=ArithmeticException");
        }
    }
}
~~~

Dùng **JDK 17 trở lên**, mở PowerShell hoặc Bash tại thư mục chứa tệp:

~~~bash
java -version
javac -version
javac -encoding UTF-8 ExactAndBigDecimalDemo.java
java ExactAndBigDecimalDemo
~~~

**Expected stdout chính xác** của riêng lệnh `java ExactAndBigDecimalDemo`:

~~~text
== exact integers ==
wrappedInt=-2147483648
safeAdd=350
safeLongMultiply=2500000000
addOverflow=ArithmeticException
multiplyOverflow=ArithmeticException
subtractOverflow=ArithmeticException
narrowingOverflow=ArithmeticException
== big integers ==
bigProduct=12000000000000000000
bigProductPlusOne=12000000000000000001
== exact decimals ==
decimalSum=0.3
binaryDoubleSum=0.30000000000000004
lineTotal=59.97
unchangedAmount=12.50
updatedAmount=13.00
== division and rounding ==
oneEighth=0.125
nonTerminating=ArithmeticException
oneThirdRounded=0.33
halfUp=10.01
halfEvenDown=2.34
halfEvenUp=2.36
== construction and equality ==
doubleConstructorEquals=false
valueOfEquals=true
scaleSensitiveEquals=false
numericCompare=0
stripZeros=2
intExact=123
fractionToInt=ArithmeticException
~~~

Đầu ra của `java -version` và `javac -version` tùy thuộc phiên bản JDK cài trên máy nên không tính trong khối stdout cố định.

## 4. `BigInteger` khi `long` không đủ lớn

`BigInteger` trong `java.math` lưu số nguyên có độ chính xác tùy ý (giới hạn thực tế bởi tài nguyên máy và các giới hạn triển khai). Nó phù hợp khi kết quả vượt `Long.MAX_VALUE`:

~~~java
BigInteger a = new BigInteger("3000000000");
BigInteger b = new BigInteger("4000000000");
BigInteger result = a.multiply(b);
// result = 12000000000000000000.
~~~

| Kiểu | Phép tính minh họa | Ghi chú |
| --- | --- | --- |
| `int` | `int c = a + b;` | Tràn số nếu giá trị ngoài miền |
| `long` | `long c = a + b;` | Miền rộng hơn `int` nhưng vẫn hữu hạn |
| `BigInteger` | `a.add(b)`, `a.multiply(b)` | Phương thức trả **đối tượng mới** chứa giá trị mới |

`BigInteger` là **immutable**: `a.add(b)` không tự sửa `a`; muốn cập nhật biến, viết `a = a.add(b)`. Nó không có phép cộng trực tiếp bằng toán tử `+` giữa hai đối tượng `BigInteger`.

Đừng nghĩ `BigInteger` thay thế `BigDecimal` cho mọi dữ liệu: `BigInteger` là **số nguyên**, không lưu phần thập phân.

## 5. `BigDecimal`: thập phân có scale, không phải kiểu số thực vô hạn

`BigDecimal` biểu diễn số bằng **unscaled integer** cùng **scale**:

~~~text
Gia tri = unscaledValue × 10^(-scale)

"19.99" -> unscaledValue = 1999, scale = 2
"2.00"  -> unscaledValue = 200,  scale = 2
~~~

Với đầu vào thập phân được biểu diễn bằng chuỗi, phép cộng, trừ và nhân của `BigDecimal` có thể tính **chính xác theo hệ thập phân** mà không mang sai số nhị phân kiểu `double`:

~~~java
BigDecimal x = new BigDecimal("0.1");
BigDecimal y = new BigDecimal("0.2");
BigDecimal sum = x.add(y); // 0.3.
~~~

### Tạo `BigDecimal` đúng cách

| Cách tạo | Khuyến nghị | Lý do |
| --- | --- | --- |
| `new BigDecimal("0.1")` | **Ưu tiên** nếu nguồn là dữ liệu thập phân | Diễn đạt chính xác 0.1 |
| `BigDecimal.valueOf(0.1)` | Thường phù hợp khi đã có `double` | Dùng biểu diễn thập phân của `Double.toString` |
| `new BigDecimal(0.1)` | **Tránh nếu chủ đích là 0.1 thập phân** | Chuyển chính xác giá trị nhị phân xấp xỉ sẵn có |
| `BigDecimal.valueOf(100L)` | Phù hợp với số nguyên | Tạo giá trị đúng từ `long` |

`new BigDecimal(0.1)` không làm tròn về `0.1` thập phân; nó biểu diễn chính xác giá trị `double` đang có. Vì thế demo in `doubleConstructorEquals=false` khi so với `new BigDecimal("0.1")`.

**Sai số đã phát sinh trước lúc tạo `BigDecimal` không được tự động sửa**. Nếu cần tính tiền, nên đọc số tiền thập phân trực tiếp từ chuỗi/kiểu dữ liệu chính xác, không tính qua `double` rồi mới chuyển.

## 6. Phép chia có thể cần quy tắc làm tròn

Không phải thương của hai số thập phân đều có biểu diễn thập phân **hữu hạn**:

~~~java
BigDecimal one = BigDecimal.ONE;
BigDecimal three = new BigDecimal("3");

// one.divide(three); // ArithmeticException: 1/3 vo han chu so thap phan.
BigDecimal rounded = one.divide(three, 2, RoundingMode.HALF_UP);
// 0.33.
~~~

| Cách chia | Hành vi |
| --- | --- |
| `one.divide(new BigDecimal("8"))` | Chính xác, trả `0.125` |
| `one.divide(new BigDecimal("3"))` | Ném `ArithmeticException` vì khai triển thập phân không kết thúc |
| `one.divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP)` | Làm tròn hai chữ số sau dấu thập phân, trả `0.33` |
| `one.divide(divisor, new MathContext(10, RoundingMode.HALF_EVEN))` | Giới hạn **10 chữ số có nghĩa**, khác với scale = 10 |

**`scale` và `precision` không giống nhau**:

- `scale = 2` biểu thị **hai chữ số sau dấu thập phân** (đối với scale dương).
- `MathContext(10, ...)` quy định **10 chữ số có nghĩa** cho phép tính.

`RoundingMode.HALF_UP` làm tròn trường hợp đúng nửa theo hướng ra xa 0. `RoundingMode.HALF_EVEN` chọn chữ số cuối chẵn khi đúng trường hợp giữa hai kết quả, hữu ích trong một số quy tắc kế toán nhưng **không được tự ý coi là yêu cầu nghiệp vụ của mọi hệ thống**.

### Làm tròn có chủ đích

~~~java
BigDecimal price = new BigDecimal("10.005");
BigDecimal rounded = price.setScale(2, RoundingMode.HALF_UP);
// 10.01.
~~~

Nếu dùng `RoundingMode.UNNECESSARY`, phép toán sẽ ném `ArithmeticException` khi yêu cầu một phép làm tròn không thể thực hiện mà vẫn chính xác. Đây là cơ chế hữu ích khi muốn **chứng minh kết quả chính xác ở scale định trước**.

## 7. So sánh `BigDecimal`: `equals` khác `compareTo`

~~~java
BigDecimal first = new BigDecimal("2.0");
BigDecimal second = new BigDecimal("2.00");

System.out.println(first.equals(second)); // false.
System.out.println(first.compareTo(second) == 0); // true.
~~~

- `equals` xét **giá trị cùng scale**; `2.0` và `2.00` khác scale nên không `equals`.
- `compareTo` so **giá trị số học**; cả hai bằng nhau về mặt số học, nên trả 0.

Nếu mục đích là kiểm tra **giá trị tiền tương đương**, thường sử dụng `compareTo(...) == 0` sau khi đã xác định chính sách scale. Nếu cấu trúc dữ liệu dùng `HashSet` hoặc `HashMap` thì chúng dựa trên `equals` và `hashCode`, nên việc coi `2.0` và `2.00` là hai giá trị khác nhau có thể ảnh hưởng hành vi. Đừng trộn hai khái niệm bình đẳng mà không quy định rõ.

`stripTrailingZeros()` có thể tạo một `BigDecimal` với scale khác nhưng cùng giá trị số học. Khi cần biểu diễn văn bản ổn định, `toPlainString()` tránh notation khoa học mà `toString()` có thể chọn.

## 8. Chọn công cụ theo đúng giới hạn

Một số nguyên **quá lớn** và một số thập phân **cần chính xác** là hai vấn đề khác nhau. Kết hợp hợp lý:

~~~java
// Chi can phat hien overflow khi van muon int:
int count = Math.addExact(currentCount, addedCount);

// Neu so nguyen co the vuot long:
BigInteger idSequence = new BigInteger("999999999999999999999");

// Neu thanh tien can tinh theo thap phan:
BigDecimal unitPrice = new BigDecimal("19.99");
BigDecimal total = unitPrice.multiply(new BigDecimal("3"));
~~~

Đoạn này là ví dụ khái niệm, cần khai báo `currentCount`/`addedCount` ở phạm vi sử dụng thực tế; chương trình runnable ở mục 3 đã tự khai báo đầy đủ mọi biến.

**Đừng coi bất kỳ kiểu nào là “vô hạn” về tài nguyên.** `BigInteger`/`BigDecimal` có thể dùng nhiều bộ nhớ và CPU hơn primitive; chúng cũng không tự giải quyết lỗi đầu vào, đơn vị tiền tệ, chính sách làm tròn, overflow khi chuyển ngược về primitive hoặc yêu cầu hiệu năng.

Khi chuyển một `BigDecimal` có phần lẻ sang `int`, `intValue()` có thể mất thông tin. Nếu bắt buộc **không mất phần lẻ** và không vượt miền, dùng `intValueExact()` (hoặc `longValueExact()`). Demo cho thấy `"123.00"` chuyển được thành 123, còn `"123.5"` ném `ArithmeticException`.

## 9. Những hiểu nhầm thường gặp

| Hiểu nhầm | Điều đúng |
| --- | --- |
| “`Math.addExact` tự mở rộng `int` thành `long`.” | Không; nó **ném lỗi** nếu vượt miền |
| “`Math.addExact(a * b, c)` phát hiện mọi overflow.” | Không; `a * b` có thể tràn trước |
| “`long` đủ cho mọi số nguyên thực tế.” | Không; `BigInteger` dùng khi cần vượt miền `long` |
| “`BigDecimal` dùng toán tử `+` như primitive.” | Không; dùng `add`, `subtract`, `multiply`, `divide` |
| “`new BigDecimal(0.1)` bằng đúng 0.1 thập phân.” | Không; nó phản ánh chính xác giá trị `double` xấp xỉ |
| “`BigDecimal.divide` luôn trả kết quả.” | Phép chia như 1/3 cần quy tắc làm tròn |
| “`scale` và `precision` là một.” | Không; một bên là vị trí dấu thập phân, một bên là số chữ số có nghĩa |
| “`equals` chỉ nhìn giá trị số của `BigDecimal`.” | Không; `equals` còn xét scale |
| “Chuyển `BigDecimal` về `int` luôn an toàn.” | Có thể mất phần lẻ/vượt miền; dùng `intValueExact` khi cần |
| “`BigDecimal` tự đảm bảo mọi quy tắc tài chính.” | Không; phải quy định rounding, scale, đơn vị và điều kiện nhập liệu |

## 10. Bài tập tự kiểm tra

1. `Math.addExact(Integer.MAX_VALUE, 1)` trả giá trị gì?  
   *Đáp án:* Không trả số `int`; ném `ArithmeticException`.
2. Muốn tính chính xác tích của hai số nguyên có thể vượt `long`, chọn kiểu nào?  
   *Đáp án:* `BigInteger`.
3. Vì sao `new BigDecimal("0.1").add(new BigDecimal("0.2"))` cho 0.3 trong khi `0.1 + 0.2` bằng 0.30000000000000004?  
   *Đáp án:* Chuỗi vào `BigDecimal` xác định giá trị thập phân đúng; `double` dùng biểu diễn nhị phân xấp xỉ.
4. `BigDecimal.ONE.divide(new BigDecimal("3"))` làm gì?  
   *Đáp án:* Ném `ArithmeticException` nếu không cung cấp quy tắc rounding/precision phù hợp cho thương vô hạn.
5. `new BigDecimal("2.0").equals(new BigDecimal("2.00"))` và `compareTo` lần lượt thế nào?  
   *Đáp án:* `false` và `0`.
6. `new BigDecimal("10.005").setScale(2, RoundingMode.HALF_UP)` bằng bao nhiêu?  
   *Đáp án:* `10.01`.
7. `new BigDecimal("123.5").intValueExact()` trả gì?  
   *Đáp án:* Ném `ArithmeticException` vì có phần lẻ.

## Checklist

- [ ] Biết primitive `int`/`long` có thể tràn âm thầm.
- [ ] Dùng `Math.*Exact` khi muốn phép toán nguyên **báo lỗi ngay khi tràn**.
- [ ] Dùng `BigInteger` cho số nguyên vượt miền primitive.
- [ ] Tạo `BigDecimal` từ chuỗi thập phân chính xác khi dữ liệu ban đầu là thập phân.
- [ ] Hiểu `BigDecimal` là immutable, dùng phương thức thay toán tử số học.
- [ ] Xác định `scale`, `precision` và `RoundingMode` trước các phép chia/làm tròn cần thiết.
- [ ] Phân biệt `equals` (giá trị và scale) với `compareTo` (giá trị số học).
- [ ] Dùng `intValueExact`/`longValueExact` khi chuyển ngược về primitive mà không được mất dữ liệu.

**Kết luận:** Khi `int`/`long` không đáp ứng yêu cầu, hãy quyết định rõ **phát hiện overflow** (`Math.*Exact`) hay **nâng miền số nguyên** (`BigInteger`). Khi `double` không phù hợp cho phép tính thập phân, dùng `BigDecimal` với **nguồn dữ liệu chính xác và chính sách làm tròn được thiết kế rõ ràng**.


</details>

- [Quay lại đầu trang](#back-to-top)
