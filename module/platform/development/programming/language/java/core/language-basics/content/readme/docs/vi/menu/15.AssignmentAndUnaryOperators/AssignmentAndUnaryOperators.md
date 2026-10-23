<a id="back-to-top"></a>

# Gán và toán tử một ngôi

## Menu
- [Phép gán = và giá trị của biểu thức gán](#assignment-and-unary-operators-001)
- [Gán liên tiếp và lý do nên hạn chế](#assignment-and-unary-operators-002)
- [Phép gán kết hợp +=, -=, *=, /= và %=](#assignment-and-unary-operators-003)
- [Chuyển kiểu ngầm định trong phép gán kết hợp](#assignment-and-unary-operators-004)
- [Tăng giảm tiền tố và hậu tố](#assignment-and-unary-operators-005)
- [Phân biệt giá trị biểu thức với giá trị biến sau ++ hoặc --](#assignment-and-unary-operators-006)
- [Toán tử dấu +, dấu - và phủ định !](#assignment-and-unary-operators-007)
- [Tránh nhiều phép tăng giảm trên cùng biến trong một biểu thức](#assignment-and-unary-operators-008)

## <a id="assignment-and-unary-operators-001">Phép gán = và giá trị của biểu thức gán</a>

<details>
<summary>Xem chi tiết</summary>

# Phép gán `=` và giá trị của biểu thức gán — Java 17+

## 1. Mục tiêu và khái niệm

Trong Java, toán tử `=` dùng để **gán một giá trị cho một biến** (*assignment*). Quan trọng hơn: **bản thân phép gán cũng là một biểu thức có kiểu và có giá trị** nếu được đánh giá thành công.

~~~java
int count = 1;
int result = (count = 5) + 2;

System.out.println(count);  // 5
System.out.println(result); // 7
~~~

Diễn biến:

1. `count = 5` thay giá trị của biến `count` thành `5`.
2. Biểu thức gán `(count = 5)` có **kiểu `int`** và **giá trị `5`**.
3. `5 + 2` bằng `7`, gán vào `result`.

Từ đây, cần tách rõ **hai hiệu ứng** của phép gán:

- **Cập nhật nơi lưu giá trị** (*side effect*): thay giá trị của biến bên trái.
- **Tạo giá trị biểu thức**: biểu thức `a = b` tự nó cho một giá trị có thể dùng trong biểu thức bao ngoài.

Đây là ngữ nghĩa **kiểu tĩnh** của Java 17+, không phải biến động kiểu hay cú pháp đặc biệt chỉ dành cho ví dụ.

## 2. `=` khác `==` như thế nào?

| Toán tử | Mục đích | Ví dụ | Kết quả |
|---|---|---|---|
| `=` | **Gán** giá trị vào biến | `x = 10` | Cập nhật `x`; biểu thức gán có giá trị 10 |
| `==` | **So sánh bằng** | `x == 10` | Biểu thức có kiểu `boolean` |
| `!=` | **So sánh khác** | `x != 10` | Biểu thức có kiểu `boolean` |

Ví dụ:

~~~java
int x = 10;

System.out.println(x == 10); // true
System.out.println(x != 10); // false

x = 20;
System.out.println(x);       // 20
~~~

**Bẫy thường gặp:**

~~~java
boolean allowed = false;

if (allowed = true) { // Hợp lệ nhưng là PHÉP GÁN, không phải so sánh
    System.out.println("entered");
}
~~~

Dòng `if` trên đặt `allowed = true`; biểu thức gán trả `true`, nên nhánh được thực hiện. Đây là **lỗi logic dễ lọt qua compiler** vì cả kiểu và cú pháp đều hợp lệ.

Nếu mục đích là kiểm tra, viết `if (allowed)` hoặc `if (allowed == true)` (cách đầu thường gọn hơn). Nếu muốn kiểm tra `false`, dùng `if (!allowed)`.

## 3. Các thành phần của phép gán đơn giản

Cú pháp cơ bản:

~~~java
variable = expression
~~~

- **Vế trái** (*left-hand side*) phải biểu thị một **biến có thể gán**, chẳng hạn biến cục bộ, field hoặc phần tử mảng.
- **Vế phải** (*right-hand side*) được đánh giá để lấy giá trị phù hợp với kiểu biến bên trái.
- Khi hoàn tất bình thường, biến bên trái nhận giá trị được gán.

Ví dụ:

~~~java
int local = 0;
local = 12; // Biến cục bộ

int[] data = {1, 2};
data[1] = 9; // Phần tử mảng

Box box = new Box();
box.value = 4; // Field của object (class Box định nghĩa ở ví dụ độc lập bên dưới)
~~~

Không phải mọi biểu thức có giá trị đều có thể đứng bên trái `=`:

~~~java
// 5 = local;                // SAI: literal không phải biến
// (local + 1) = 10;         // SAI: kết quả phép cộng không phải biến
// Math.abs(local) = 10;     // SAI: kết quả gọi method không phải biến
~~~

**`=` không giống dấu bằng toán học đối xứng.** `x = y` và `y = x` là **hai thao tác khác nhau**: nơi được cập nhật là biến ở vế trái.

## 4. Giá trị và kiểu của biểu thức gán

Kiểu của `x = expression` liên quan tới **kiểu của biến bên trái** theo quy tắc ngôn ngữ (có các chi tiết *capture conversion* trong trường hợp generic). Giá trị biểu thức là **giá trị của biến sau khi gán**, không phải một kiểu mới được suy ra tùy tiện.

~~~java
int n = 0;
long wider = (n = 7); // n=7; giá trị int 7 được widening thành long

System.out.println(n);     // 7
System.out.println(wider); // 7
~~~

Ở đây `(n = 7)` có kiểu `int`. Phép gán ngoài `long wider = ...` tiếp tục chuyển rộng từ `int` sang `long`.

Với số nhỏ:

~~~java
byte small = 0;
int combined = (small = 42) + 1;

System.out.println(small);    // 42
System.out.println(combined); // 43
~~~

`small = 42` có kiểu `byte` và giá trị 42; khi cộng 1, Java áp dụng *binary numeric promotion*, nên `(small = 42) + 1` có kiểu `int` và giá trị 43.

**Đừng suy rằng mọi phép gán đều trả về một biến tham chiếu mới hoặc một bản sao object.** Biểu thức cho **giá trị** (đối với tham chiếu là giá trị tham chiếu) và có cập nhật ở nơi được gán; đó là hai khía cạnh khác nhau.

## 5. Chuyển đổi kiểu khi gán: hợp lệ và không hợp lệ

Java kiểm tra kiểu tĩnh của vế phải theo **assignment conversion**. Một vài trường hợp thường gặp:

| Mẫu code | Kết quả | Giải thích |
|---|---|---|
| `long a = 7;` | Hợp lệ | `int` sang `long` (widening) |
| `double d = 7;` | Hợp lệ | `int` sang `double` |
| `byte b = 42;` | Hợp lệ | Literal hằng `int` trong miền byte được phép thu hẹp khi gán |
| `byte b = 128;` | Lỗi compile | Hằng 128 ngoài miền byte |
| `int n = 3.5;` | Lỗi compile | `double` không tự thu hẹp thành `int` |
| `int n = (int) 3.5;` | Hợp lệ | Cast tường minh, giá trị 3 |
| `Integer boxed = 7;` | Hợp lệ | Autoboxing `int` thành `Integer` |
| `Long boxed = 7;` | Lỗi compile | Không tự kết hợp `int → long → Long` trong phép gán này |
| `Long boxed = 7L;` | Hợp lệ | Boxing `long` thành `Long` |

Một đặc điểm quan trọng: **constant expression narrowing** trong phép gán:

~~~java
byte small = 100;  // Hằng int 100 nằm trong miền byte
int variable = 100;
// byte other = variable; // Lỗi compile: biến int không phải hằng được phép thu hẹp
byte other = (byte) variable; // Hợp lệ về cú pháp; cần tự kiểm tra miền
~~~

Nếu dùng cast sai miền, giá trị có thể bị mất hoặc tràn. Phép gán biên dịch được **không bảo đảm** dữ liệu thỏa yêu cầu nghiệp vụ.

## 6. Phép gán liên tiếp: hệ quả của giá trị biểu thức gán

Vì `(b = 9)` vừa cập nhật `b` vừa cho giá trị `9`, Java cho phép dùng nó làm vế phải của một phép gán khác:

~~~java
int a, b, c;
a = b = c = 9; // a, b, c đều bằng 9
~~~

Dấu `=` **kết hợp từ phải sang trái**: `a = (b = (c = 9))`. Điều này nói về cách **nhóm biểu thức**, không nói rằng mọi toán hạng trong Java đều chạy từ phải sang trái. Mỗi phép gán vẫn phải qua kiểm tra kiểu riêng; chẳng hạn `long wide; int narrow; wide = narrow = 8;` hợp lệ, còn phép gán kết quả `long` về `int` không tự hợp lệ.

Để hiểu đầy đủ khi nào nên tránh gán liên tiếp, đặc biệt với chỉ số mảng, lời gọi hàm và nhiều biến tham chiếu, xem bài **“Gán liên tiếp và lý do nên hạn chế”**. Ở bài này, điều cần giữ lại là **giá trị của một phép gán có thể tiếp tục làm đầu vào cho phép gán khác**.

## 7. Assignment expression được dùng ở những đâu?

Một phép gán có thể làm **câu lệnh biểu thức** (*statement expression*) hoặc là một phần của biểu thức lớn hơn:

~~~java
int x = 1;

x = 5;                 // Câu lệnh gán
int y = (x = 6) + 2;   // Gán dùng như biểu thức con
boolean changed = (x = 7) > 5; // Kết quả x=7 được đem so

System.out.println(x);       // 7
System.out.println(y);       // 8
System.out.println(changed); // true
~~~

Ngoài ra, với biến boolean:

~~~java
boolean ready = false;
boolean copied = (ready = true);

System.out.println(ready);  // true
System.out.println(copied); // true
~~~

Tuy nhiên, **được phép dùng không đồng nghĩa nên lạm dụng**. Code business thường sáng sủa hơn khi tách:

~~~java
x = 7;
boolean changed = x > 5;
~~~

Điều này đặc biệt quan trọng nếu vế phải chứa method có tác dụng phụ hoặc phép tính có thể ném ngoại lệ.

## 8. Biểu thức gán và biểu thức điều kiện

Ví dụ biểu thức điều kiện:

~~~java
int count = 0;
boolean valid = true;

int selected = valid ? (count = 10) : (count = -1);

System.out.println(selected); // 10
System.out.println(count);    // 10
~~~

Khi `valid` là true, chỉ nhánh `count = 10` được đánh giá; nhánh còn lại không chạy. Giá trị của phép gán được chọn trở thành kết quả biểu thức điều kiện.

**Điểm quan trọng:** việc gán trạng thái bên trong `?:` có thể khó kiểm tra; cách thông thường dễ đọc hơn:

~~~java
if (valid) {
    count = 10;
} else {
    count = -1;
}
int selected = count;
~~~

Cách nào đúng phụ thuộc quy mô logic, nhưng không nên đặt các side effect quan trọng vào biểu thức lồng nhiều tầng.

## 9. Thứ tự đánh giá khi dùng giá trị gán trong phép tính

~~~java
int n = 1;
int result = (n = 4) + n;
System.out.println(result); // 8
System.out.println(n);      // 4
~~~

Toán hạng trái `(n = 4)` được đánh giá trước toán hạng phải `n`. Nó cập nhật `n` và cung cấp `4`; lần đọc sau cũng thấy `4`. Hai việc “cung cấp giá trị” và “đổi trạng thái” xảy ra trong một biểu thức, nên hãy thận trọng khi đặt phép gán vào phép tính hoặc điều kiện.

Các tình huống nhiều lần `++`/`--` trên cùng biến cần một cách truy vết khác và được phân tích ở bài cuối chương. Ở đây, mẫu an toàn khi không cần side effect trong biểu thức là tách rõ:

~~~java
n = 4;
int simplerResult = n + n; // 8
~~~

## 10. Field và phần tử mảng cũng dùng phép gán có giá trị

Ví dụ field:

~~~java
static final class Box {
    int value;
}

Box box = new Box();
int assignedField = (box.value = 12);

System.out.println(box.value);     // 12
System.out.println(assignedField); // 12
~~~

Ví dụ mảng:

~~~java
int[] scores = {3, 7};
int assignedElement = (scores[1] = 20);

System.out.println(scores[1]);      // 20
System.out.println(assignedElement); // 20
~~~

Trong cả hai trường hợp, biểu thức gán cập nhật **một biến thực sự**: một field hoặc một thành phần mảng. Giá trị biểu thức phản ánh giá trị sau gán.

### Chỉ số mảng có thể có tác dụng phụ riêng

~~~java
int[] values = {10, 20};
int index = 0;
int result = (values[index++] = 99) + index;

System.out.println(values[0]); // 99
System.out.println(index);     // 1
System.out.println(result);    // 100
~~~

`index++` cung cấp chỉ số 0 rồi tăng `index` lên 1. Phép gán phần tử cho giá trị 99; vế phải `+ index` dùng 1. Ví dụ hợp lệ nhưng **khó đọc và dễ sửa sai** hơn cách tách việc chọn chỉ số, cập nhật mảng và tính tổng.

## 11. Gán tham chiếu không sao chép đối tượng

~~~java
StringBuilder first = new StringBuilder("A");
StringBuilder alias = first;

alias.append("B");
System.out.println(first); // AB
~~~

`alias = first` sao chép **giá trị tham chiếu**, nên hai biến trỏ đến **cùng object**; phép gán không tạo bản sao `StringBuilder`.

Khi gán tham chiếu mới vào một biến:

~~~java
StringBuilder original = first;
first = new StringBuilder("C");

System.out.println(original); // AB
System.out.println(first);    // C
~~~

`first` trỏ sang object mới; `original` vẫn trỏ object cũ. Không nhầm **gán lại biến tham chiếu** với **sửa nội dung object**.

Nếu muốn object độc lập, phải dùng cơ chế copy thích hợp do class cung cấp, chẳng hạn `new StringBuilder(first.toString())` trong ngữ cảnh này.

## 12. Ranh giới giữa `=` và các phép gán kết hợp

Phép gán đơn giản `x = value` chuyển giá trị vế phải theo quy tắc *assignment conversion*. Phép gán kết hợp như `x += value` còn thực hiện phép toán rồi có thể **thu hẹp ngầm** kết quả về kiểu vế trái; với vế trái phức tạp, nó chỉ đánh giá vế trái **một lần**. Đây là khác biệt về ngữ nghĩa, không chỉ cách viết ngắn:

~~~java
byte b = 120;
b += 10;             // Hợp lệ; 130 thu hẹp về byte thành -126.
// b = b + 10;       // Lỗi compile: phép cộng thông thường cho int.

String label = "value=";
label += 7;          // Nối chuỗi: "value=7".
~~~

Bài này chỉ dùng những ví dụ đó để **đặt ranh giới với toán tử `=`**. Khi tính toán thật, kiểm tra miền số và tác dụng phụ; đừng tự thay `op=` bằng `x = x op y` khi vế trái là biểu thức có side effect.

## 13. Gán và ràng buộc của `final`

`final` giới hạn việc **gán lại biến**, không phải luôn làm đối tượng mà tham chiếu trỏ đến bất biến:

~~~java
final int once;
once = 10; // Hợp lệ: biến local final được gán chắc chắn đúng một lần.
// once = 11; // Lỗi compile: gán lại.

final StringBuilder builder = new StringBuilder("A");
builder.append("B"); // Hợp lệ: sửa trạng thái object.
// builder = new StringBuilder("C"); // Lỗi: gán lại tham chiếu final.
~~~

Giá trị của phép gán `=` không bỏ qua các quy tắc definite assignment và `final`. Khi chỉ cần cập nhật một biến thông thường, hãy ưu tiên câu lệnh độc lập dễ kiểm tra thay vì lồng trong biểu thức lớn.

## 14. Exception và lỗi trong vế phải

Một phép gán chỉ hoàn thành bình thường khi những bước cần thiết đều thành công. Ví dụ vế phải ném ngoại lệ:

~~~java
int value = 3;
int divisor = 0;

try {
    value = 10 / divisor;
} catch (ArithmeticException ex) {
    System.out.println("division failed");
}
System.out.println(value); // 3
~~~

Với biến cục bộ đơn giản này, phép chia `10 / divisor` thất bại **trước khi giá trị mới được gán**, nên `value` vẫn bằng 3.

Nhưng không nên suy rộng rằng mọi biểu thức gán thất bại đều **không có tác dụng phụ**: quá trình đánh giá vế trái hoặc vế phải có thể đã gọi method, tăng chỉ số, sửa object hoặc ghi I/O trước khi exception xảy ra.

**Khuyến nghị:** kiểm tra input và hạn chế nhiều side effect trong một biểu thức gán, nhất là trong logic giao dịch.

## 15. Các lỗi phổ biến và cách khắc phục

| Lỗi / ngộ nhận | Vì sao sai? | Cách đúng |
|---|---|---|
| `x = 5` là phép so sánh | `=` gán, không so | `x == 5` |
| `if (ready = true)` để kiểm tra `ready` | Thực tế gán true rồi điều kiện luôn true | `if (ready)` |
| `5 = x` | Literal không phải biến có thể gán | `x = 5` |
| `a + 1 = 3` | Kết quả phép cộng không gán được | Gán vào biến thích hợp |
| `int x = 3.5;` | Double không tự chuyển thành int | Cast có kiểm tra hoặc dùng double |
| `Long boxed = 7;` | Không tự int → long → Long | `Long boxed = 7L;` |
| `byte b = 128;` | Hằng không vừa miền byte | Chọn kiểu đủ lớn, không che bằng cast |
| Nghĩ `a = b = 9` nhóm từ trái | Phép gán kết hợp phải | `a = (b = 9)` |
| `i = i++` để tăng i | Phép gán ngoài ghi đè tăng hậu tố | `i++;` |
| Nghĩ assignment expression không có giá trị | Nó có giá trị sau gán | Có thể dùng `(x = 5) + 2`, nhưng cân nhắc dễ đọc |
| `byte b; b = b + 1;` | Kết quả int không tự thu hẹp | Chọn int hoặc cast có kiểm tra |
| Nghĩ `b += 1` luôn y như `b = b + 1` | Compound assignment có chuyển kiểu và đánh giá vế trái đặc thù | Hiểu quy tắc trước khi đổi code |
| Gán tham chiếu tưởng đã clone object | Sao chép tham chiếu, không copy object | Dùng copy riêng khi cần |
| Gán lại biến `final` | Vi phạm quy tắc definite assignment | Thiết kế init một lần |
| Cho rằng `catch` luôn rollback phép gán | Side effect trước lỗi có thể đã xảy ra | Tách bước/validate/transaction phù hợp |

## 16. Chương trình Java 17 đầy đủ — quan sát riêng phép gán

Lưu thành `AssignmentExpressionDemo.java`. Ví dụ gom các trường hợp **phép gán đơn giản trả giá trị** và phân biệt với phép so sánh; những chương trình về `op=`, gán chuỗi và `++` được triển khai ở bài riêng.

~~~java
public final class AssignmentExpressionDemo {
    static final class Box {
        int value;
    }

    public static void main(String[] args) {
        int count = 1;
        int result = (count = 5) + 2;
        System.out.println("assignment=" + result + ",count=" + count);

        boolean ready = false;
        boolean expression = (ready = true);
        System.out.println("boolean=" + expression + ",ready=" + ready);

        int number = 0;
        long widened = (number = 7);
        System.out.println("widened=" + widened + ",number=" + number);

        Box box = new Box();
        int assignedField = (box.value = 12);
        System.out.println("field=" + assignedField + ",stored=" + box.value);

        int[] scores = {3, 7};
        int assignedElement = (scores[1] = 20);
        System.out.println("element=" + assignedElement + ",stored=" + scores[1]);

        StringBuilder first = new StringBuilder("A");
        StringBuilder alias = first;
        alias.append("B");
        System.out.println("alias=" + first);

        int unchanged = 3;
        int zero = 0;
        try {
            unchanged = 10 / zero;
        } catch (ArithmeticException ex) {
            System.out.println("rhs failed");
        }
        System.out.println("unchanged=" + unchanged);
    }
}
~~~

Biên dịch và chạy với **JDK 17+** trong thư mục chứa file:

~~~bash
javac --release 17 -encoding UTF-8 AssignmentExpressionDemo.java
java AssignmentExpressionDemo
~~~

**Expected stdout:**

~~~text
assignment=7,count=5
boolean=true,ready=true
widened=7,number=7
field=12,stored=12
element=20,stored=20
alias=AB
rhs failed
unchanged=3
~~~

`(count = 5)` trả `5`; gán `boolean` trả `true`; giá trị `int` từ `(number = 7)` có thể chuyển rộng thành `long`. Phép gán field/mảng cập nhật đúng nơi chứa giá trị, còn gán tham chiếu chỉ sao chép tham chiếu. Cuối cùng, phép chia cho 0 làm vế phải thất bại trước khi `unchanged` nhận giá trị mới.

## 17. Bài tập có đáp án

### Bài A — giá trị biểu thức gán

~~~java
int x = 2;
int y = (x = 6) * 3;
System.out.println(x);
System.out.println(y);
~~~

**Đáp án:**

~~~text
6
18
~~~

### Bài B — chuỗi gán

~~~java
int a, b, c;
a = b = c = 4;
System.out.println(a + b + c);
~~~

**Đáp án:** `12`. Cả ba biến nhận 4 rồi cộng số nguyên.

### Bài C — lỗi logic `=` và `==`

~~~java
boolean enabled = false;
if (enabled = true) {
    System.out.println("enabled");
}
~~~

**Đáp án:** in `enabled`; đoạn code đã **gán true**, không phải so sánh. Nếu chỉ muốn kiểm tra giá trị ban đầu, viết `if (enabled)`.

### Bài D — khác nhau giữa `=` và `+=`

~~~java
byte a = 120;
a += 10;
System.out.println(a);
~~~

**Đáp án:** `-126`. Compound assignment tự chuyển về byte; `a = a + 10` không tự hợp lệ vì phép cộng thông thường trả `int`.

### Bài E — biểu thức gán trong mảng

~~~java
int[] data = {5, 8};
int result = (data[0] = 12) + data[1];
System.out.println(result);
System.out.println(data[0]);
~~~

**Đáp án:**

~~~text
20
12
~~~

### Bài F — local final

~~~java
final int value;
value = 3;
// value = 4;
~~~

**Đáp án:** gán `3` là khởi tạo hợp lệ; bật dòng `value = 4` sẽ lỗi compile do gán lại biến local final.

### Bài G — xử lý exception

~~~java
int x = 10;
int zero = 0;
try {
    x = 1 / zero;
} catch (ArithmeticException ex) {
    System.out.println(x);
}
~~~

**Đáp án:** `10`. Vế phải lỗi trước khi phép gán đơn giản cập nhật `x`.

## 18. Checklist và kết luận

- [ ] Hiểu toán tử `=` là **gán**, `==` là **so sánh bằng**.
- [ ] Biết vế trái phải là một biến gán được (local, field, array component).
- [ ] Biết phép gán là biểu thức có kiểu và giá trị sau gán.
- [ ] Hiểu `int y = (x = 5) + 2;` vừa sửa `x` vừa tạo giá trị 7.
- [ ] Hiểu phép gán kiểm tra *assignment conversion* theo kiểu đích.
- [ ] Biết chuỗi `a = b = c = 9` được nhóm từ phải sang trái.
- [ ] Không dùng nhầm `if (condition = true)` thay cho kiểm tra boolean.
- [ ] Nhận diện side effect khi lồng phép gán vào biểu thức khác.
- [ ] Biết gán field/mảng cũng tạo giá trị; chú ý chỉ số thay đổi.
- [ ] Biết gán tham chiếu không tự sao chép object.
- [ ] Phân biệt `=` đơn giản với compound assignment như `+=`.
- [ ] Hiểu `final` ngăn gán lại biến đã khởi tạo.
- [ ] Biết vế phải ném exception có thể khiến phép gán không hoàn tất.
- [ ] Chạy `AssignmentExpressionDemo.java` bằng `javac` và `java` trên JDK 17+; so khớp toàn bộ stdout.

**Kết luận:** `a = b` trong Java 17+ vừa **cập nhật biến `a`** vừa **tạo một giá trị biểu thức** theo kiểu được kiểm tra tại compile time. Khả năng dùng phép gán trong biểu thức lớn hơn giải thích được gán chuỗi, gán trong điều kiện và biểu thức số học — nhưng cũng tạo nguy cơ side effect khó đọc. Ưu tiên các câu lệnh gán riêng, có tên biến rõ ràng, và chỉ tận dụng giá trị của biểu thức gán khi thực sự giúp code đơn giản hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="assignment-and-unary-operators-002">Gán liên tiếp và lý do nên hạn chế</a>

<details>
<summary>Xem chi tiết</summary>

# Gán liên tiếp và lý do nên hạn chế trong Java

> **Java core — language-basics / Gán và toán tử một ngôi**  
> **Phiên bản:** Java 17+; ví dụ một file, không cần thư viện ngoài.  
> **Mục tiêu:** Hiểu `a = b = c = value` được nhóm như thế nào, vì sao biểu thức gán có giá trị và phải tương thích kiểu; nhận diện vấn đề về khả năng đọc, thứ tự đánh giá, tác dụng phụ và các tham chiếu cùng trỏ tới một đối tượng.

## 1. Gán liên tiếp là gì?

Một phép gán `left = right` lưu giá trị của vế phải vào biến được chỉ định ở vế trái. Java cho phép **biểu thức gán** nằm ở vế phải của phép gán khác:

~~~java
int a, b, c;
a = b = c = 7;
System.out.println(a + "," + b + "," + c); // 7,7,7
~~~

Toán tử gán `=` **kết hợp từ phải sang trái** (*right-associative*). Cách nhóm của dòng trên là:

~~~text
a = (b = (c = 7))
~~~

Diễn giải trường hợp các biến đơn giản:

1. `c = 7` gán 7 cho `c`; toàn bộ biểu thức này có giá trị 7.
2. `b = (c = 7)` nhận 7 rồi gán vào `b`.
3. `a = (b = (c = 7))` nhận 7 rồi gán vào `a`.

Kết thúc, cả ba biến đều bằng 7. **Gán liên tiếp không khai báo biến mới**: `a`, `b`, `c` phải tồn tại trước và kiểu của chúng phải phù hợp.

## 2. Biểu thức gán có giá trị và có kiểu

Với phép gán đơn giản, biểu thức `variable = expression` mang **giá trị đã gán** và có kiểu của biến bên trái. Vì thế có thể dùng nó bên trong phép gán khác:

~~~java
int left, right;
left = right = 12; // Cả hai đều nhận 12.
~~~

Không phải mọi phép gán liên tiếp đều bắt buộc các biến có cùng kiểu. Từng bước gán phải hợp lệ theo quy tắc chuyển kiểu Java:

~~~java
int whole;
double decimal;
decimal = whole = 3; // Hợp lệ: whole nhận int 3, decimal nhận double 3.0.
~~~

Ngược lại:

~~~java
int whole;
double decimal;
// whole = decimal = 3.5; // Lỗi biên dịch: không thể gán double vào int.
~~~

**Điểm cần nhớ:** Java không tự suy đoán một kiểu chung tùy ý chỉ vì phép gán nằm trên cùng một dòng. Dấu ngoặc cho biết cách **nhóm**, còn quy tắc tương thích kiểu vẫn được áp dụng ở mỗi phép gán.

Gán liên tiếp có thể chứa biểu thức khác:

~~~java
int a, b, c = 2;
a = b = c + 5; // a = 7, b = 7, c vẫn là 2.
~~~

Ở đây `c + 5` **không gán lại** `c`. Phân biệt `c + 5` với `c += 5`: phép thứ hai thay đổi `c`.

## 3. Biến tham chiếu: gán cùng object, không nhân bản object

Với kiểu tham chiếu, gán liên tiếp có thể khiến nhiều biến cùng trỏ đến **một object**:

~~~java
int[] first, second;
first = second = new int[] {10, 20};

first[0] = 99;
System.out.println(second[0]); // 99
~~~

Dòng `new int[] {10, 20}` tạo **một mảng duy nhất**. Hai phép gán chỉ sao chép giá trị **tham chiếu**, không tạo hai mảng độc lập. Vì thế sửa phần tử qua `first` cũng được nhìn thấy qua `second`.

Nếu cần hai đối tượng riêng, hãy tạo/copy có chủ đích, chẳng hạn:

~~~java
int[] first = {10, 20};
int[] second = first.clone(); // Mảng mới với các phần tử được sao chép.
~~~

Với object có trạng thái thay đổi được, sử dụng gán liên tiếp mà không để ý đến việc chia sẻ tham chiếu có thể tạo lỗi khó truy vết. Với các kiểu bất biến như `String`, việc chia sẻ tham chiếu thường ít rủi ro hơn, nhưng vẫn phải hiểu ngữ nghĩa gán.

## 4. Vì sao nên hạn chế gán liên tiếp?

| Vấn đề | Ví dụ hoặc hậu quả | Cách diễn đạt rõ hơn |
| --- | --- | --- |
| Khó đọc cách dữ liệu đi qua biến | `a = b = c = compute();` | Tạo giá trị trung gian có tên, rồi gán rõ từng biến |
| Dễ bỏ sót kiểu không tương thích | `int x; double y; x = y = 1.5;` | Chuyển kiểu có chủ đích, tách từng phép gán |
| Che khuất việc nhiều biến cùng tham chiếu | `x = y = new int[3];` | Đặt tên thể hiện chia sẻ, hoặc clone/tạo riêng |
| Trộn tác dụng phụ | `array[i++] = array[i++] = 8;` | Tách việc chọn chỉ số và việc gán |
| Khó đặt breakpoint/log theo bước | Một dòng sửa nhiều biến | Mỗi câu lệnh sửa một biến khi nghiệp vụ phức tạp |
| Dễ hiểu sai giữa nhóm và thứ tự đánh giá | Gán kết hợp phải không có nghĩa mọi biểu thức con chạy từ phải sang trái | Tách bước hoặc dùng ngoặc kết hợp với biến trung gian |

Gán liên tiếp **không sai về kỹ thuật**. Với ví dụ ngắn `a = b = 0;`, ý định có thể rất rõ. Nhưng khi liên quan đến trạng thái, object, lời gọi hàm hoặc chỉ số mảng, **ưu tiên khả năng đọc và kiểm thử hơn số dòng ít nhất**.

### Một lời gọi không chạy ba lần

~~~java
int a, b, c;
a = b = c = calculate();
~~~

`calculate()` được gọi **một lần**; kết quả của nó được dùng cho ba phép gán. Nếu muốn ba giá trị được tính **độc lập**, phải gọi hàm riêng cho mỗi biến:

~~~java
a = calculate();
b = calculate();
c = calculate();
~~~

Đây là khác biệt nghiệp vụ quan trọng khi `calculate()` đọc thời gian, sinh số ngẫu nhiên hoặc thay đổi trạng thái.

## 5. Tính kết hợp không phải thứ tự đánh giá tác dụng phụ

`a = b = c = value` nhóm **từ phải sang trái**, nhưng với một **vế trái phức tạp** như truy cập mảng, Java vẫn có quy tắc về **thứ tự đánh giá biểu thức**.

~~~java
int[] values = {1, 2, 3};
int i = 0;

values[i++] = values[i++] = 8;
~~~

Ở phép gán mảng bên ngoài, Java đánh giá tham chiếu mảng và **chỉ số bên trái** trước khi đánh giá biểu thức gán ở bên phải. Lần `i++` đầu chọn `values[0]`; lần thứ hai chọn `values[1]`; sau đó cả hai vị trí nhận 8. Kết quả là `values = {8, 8, 3}` và `i = 2`.

Đừng suy từ “gán kết hợp phải” rằng lần `i++` nằm bên phải **luôn** chạy trước. Độ kết hợp quyết định **cây biểu thức**, còn thứ tự đánh giá có quy tắc khác.

**Cách viết rõ hơn:**

~~~java
int firstIndex = i++;
int secondIndex = i++;
values[secondIndex] = 8;
values[firstIndex] = 8;
~~~

Đoạn tách bước có thể dài hơn nhưng cho phép quan sát/chỉnh sửa và đặt breakpoint tại từng bước. Thực tế, nếu không cần tác dụng phụ của `i++`, hãy chọn chỉ số tường minh và tránh phép gán lồng nhau.

## 6. Chương trình Java 17+ runnable

Tạo file **ChainedAssignmentDemo.java**:

~~~java
public final class ChainedAssignmentDemo {
    private static int callCount;

    private static int calculate() {
        callCount++;
        return 42;
    }

    public static void main(String[] args) {
        int a, b, c;
        a = b = c = 7;
        System.out.println("basic=" + a + "," + b + "," + c);

        a = b = c + 5;
        System.out.println("expression=" + a + "," + b + "," + c);

        a = b = c = calculate();
        System.out.println("calculated=" + a + "," + b + "," + c);
        System.out.println("calls=" + callCount);

        int whole;
        double decimal;
        decimal = whole = 3;
        System.out.println("mixedTypes=" + whole + "," + decimal);

        boolean active, visible;
        active = visible = true;
        System.out.println("booleans=" + active + "," + visible);

        String left, right;
        left = right = new String("ready");
        System.out.println("sameStringReference=" + (left == right));

        int[] first, second;
        first = second = new int[] {10, 20};
        first[0] = 99;
        System.out.println("sharedArray=" + first[0] + "," + second[0]);

        int[] indexed = {1, 2, 3};
        int i = 0;
        indexed[i++] = indexed[i++] = 8;
        System.out.println("indexed=" + indexed[0] + ","
                + indexed[1] + "," + indexed[2]);
        System.out.println("indexAfter=" + i);
    }
}
~~~

Chạy tại thư mục chứa tệp (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 ChainedAssignmentDemo.java
java ChainedAssignmentDemo
~~~

**Expected stdout chính xác:**

~~~text
basic=7,7,7
expression=12,12,7
calculated=42,42,42
calls=1
mixedTypes=3,3.0
booleans=true,true
sameStringReference=true
sharedArray=99,99
indexed=8,8,3
indexAfter=2
~~~

Đối chiếu: `expression=12,12,7` cho thấy `c` không đổi khi chỉ dùng `c + 5`; `calls=1` xác nhận hàm được gọi một lần; `sameStringReference=true` và `sharedArray=99,99` cho thấy gán chia sẻ tham chiếu. `indexed` minh họa vì sao có tác dụng phụ trong vế trái thì không nên chỉ dựa vào cách nhóm từ phải.

## 7. Cách thay thế dễ đọc hơn

Nếu kết quả dùng nhiều lần, cân nhắc tên biến có nghĩa:

~~~java
int defaultLimit = 100;
int readLimit = defaultLimit;
int writeLimit = defaultLimit;
~~~

Để gán các biến ban đầu cùng một giá trị một cách đơn giản:

~~~java
int x = 0;
int y = 0;
~~~

Khi gọi hàm có side effect, hãy quyết định rõ là **một kết quả chia sẻ** hay **các kết quả tính riêng**. Khi làm việc với object/mảng, hãy quyết định rõ **chia sẻ tham chiếu** hay **tạo bản sao**.

Không nhất thiết loại bỏ mọi `a = b = 0;`; hãy tránh khi dấu `=` xuất hiện quá nhiều, biến có ý nghĩa nghiệp vụ khác nhau, hoặc việc gán tác động đến những trạng thái cần truy vết riêng.

## 8. Bài tập tự kiểm tra

**Bài 1.** Viết lại `a = b = c = 5;` có dấu ngoặc thể hiện cách nhóm. Giá trị của từng biến sau dòng lệnh là gì?

**Bài 2.** Cho `int c = 2; int a, b; a = b = c + 5;`. Giá trị `a`, `b`, `c` thế nào? Điều gì thay đổi nếu viết `a = b = (c += 5);`?

**Bài 3.** Vì sao `double d; int i; d = i = 3;` hợp lệ nhưng `i = d = 3.5;` không hợp lệ?

**Bài 4.** Với hai biến mảng `x` và `y` được gán từ cùng một `new int[2]`, sửa `x[0]` có làm `y[0]` thay đổi không? Viết cách tạo mảng riêng nếu không muốn chia sẻ.

**Bài 5.** Giải thích vì sao biểu thức `array[i++] = array[i++] = 8;` khó bảo trì dù Java quy định rõ cách nhóm và thứ tự đánh giá.

### Ghi nhớ

**Gán liên tiếp dùng tính kết hợp phải của `=`: `a = b = c = value` nhóm thành `a = (b = (c = value))`.** Mỗi phép gán phải tương thích kiểu; giá trị tham chiếu được **sao chép**, không phải object được nhân bản. **Hạn chế gán liên tiếp** khi có nhiều trạng thái, tác dụng phụ, mảng hoặc object chia sẻ, vì tách các câu lệnh thường làm ý định rõ hơn và dễ kiểm thử.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="assignment-and-unary-operators-003">Phép gán kết hợp +=, -=, *=, /= và %=</a>

<details>
<summary>Xem chi tiết</summary>

# Phép gán kết hợp `+=`, `-=`, `*=`, `/=` và `%=` — Java 17+

**Phép gán kết hợp** (*compound assignment*) vừa thực hiện phép toán vừa **gán kết quả trở lại biến hoặc vị trí lưu trữ ở vế trái**. Năm toán tử số học thường gặp là:

| Toán tử | Ý nghĩa | Ví dụ với `x = 10` | Giá trị `x` sau phép gán |
| --- | --- | --- | ---: |
| `+=` | Cộng rồi gán | `x += 5;` | 15 |
| `-=` | Trừ rồi gán | `x -= 3;` | 7 |
| `*=` | Nhân rồi gán | `x *= 2;` | 20 |
| `/=` | Chia rồi gán | `x /= 4;` | 2 nếu `x` là `int` |
| `%=` | Lấy phần dư rồi gán | `x %= 3;` | 1 |

**Lưu ý:** mỗi hàng trong bảng được hiểu là ví dụ độc lập, đều bắt đầu từ `x = 10`. Các toán tử này không tạo một loại biến mới: sau phép gán, **kiểu khai báo của biến vẫn giữ nguyên**.

~~~java
int score = 10;
score += 5;        // score = 15.
score *= 2;        // score = 30.
score -= 4;        // score = 26.
~~~

## 1. Cách hiểu chính xác hơn “viết tắt của phép gán”

Về mặt ý nghĩa, đối với vế trái có kiểu `T`, phép `E1 op= E2` tương ứng với phép tính rồi **chuyển kết quả về `T`**, với hai khác biệt quan trọng so với tự mở rộng cú pháp: **`E1` chỉ được đánh giá một lần**, và **phép gán kết hợp có chuyển kiểu về kiểu vế trái**.

~~~text
E1 op= E2

Gần tương đương về giá trị với:
E1 = (T) ((E1) op (E2))

Ngoại lệ quan trọng: E1 chỉ được đánh giá MỘT lần trong op=.
~~~

Ví dụ:

~~~java
int total = 10;
total += 4; // 14.
~~~

Với `int` đơn giản, kết quả giống `total = total + 4`. Nhưng không nên coi đây là một thao tác thay thế văn bản máy móc cho mọi kiểu và mọi biểu thức vế trái.

**Phép gán kết hợp vẫn là một biểu thức có giá trị**; tuy nhiên, khi không cần giá trị của phép gán, cách viết một câu lệnh riêng thường dễ đọc hơn:

~~~java
int count = 3;
count += 2; // Nen dung khi chi muon cap nhat bien.
~~~

## 2. Chương trình Java 17+ chạy độc lập

Tạo tệp UTF-8 tên **`CompoundAssignmentDemo.java`**:

~~~java
import java.util.Arrays;

public class CompoundAssignmentDemo {
    public static void main(String[] args) {
        int value = 10;
        value += 5;
        System.out.println("plus=" + value);
        value -= 3;
        System.out.println("minus=" + value);
        value *= 2;
        System.out.println("multiply=" + value);
        value /= 5;
        System.out.println("divide=" + value);
        value %= 3;
        System.out.println("remainder=" + value);

        byte small = 120;
        small += 10;
        System.out.println("byteOverflow=" + small);

        short medium = 30_000;
        medium *= 2;
        System.out.println("shortOverflow=" + medium);

        char letter = 'A';
        letter += 1;
        System.out.println("charNext=" + letter);

        int fractional = 3;
        fractional += 2.5;
        System.out.println("fractionalNarrowing=" + fractional);

        int negative = -10;
        negative %= 3;
        System.out.println("negativeRemainder=" + negative);

        int captured = 5;
        captured += (captured = 10);
        System.out.println("capturedOldValue=" + captured);

        int[] data = {10, 20};
        int index = 0;
        data[index++] += 7;
        System.out.println("array=" + Arrays.toString(data)
                + ",index=" + index);

        String message = "Java";
        message += " 17";
        message += ": " + (1 + 2);
        System.out.println("message=" + message);

        int numerator = 12;
        int zero = 0;
        try {
            numerator /= zero;
        } catch (ArithmeticException ex) {
            System.out.println("integerZero=ArithmeticException");
        }
        System.out.println("unchangedAfterException=" + numerator);

        double floating = 1.0;
        floating /= 0.0;
        System.out.println("floatingDivision=" + floating);

        double floatRemainder = 0.0;
        floatRemainder %= 0.0;
        System.out.println("floatingRemainderNaN="
                + Double.isNaN(floatRemainder));

        int overflowing = Integer.MAX_VALUE;
        overflowing += 1;
        System.out.println("intOverflow=" + overflowing);
    }
}
~~~

Tại thư mục chứa file, mở PowerShell (Windows) hoặc Bash (Linux/macOS), sử dụng **JDK 17 trở lên**:

~~~bash
java -version
javac -version
javac -encoding UTF-8 CompoundAssignmentDemo.java
java CompoundAssignmentDemo
~~~

**Expected stdout chính xác** của lệnh `java CompoundAssignmentDemo`:

~~~text
plus=15
minus=12
multiply=24
divide=4
remainder=1
byteOverflow=-126
shortOverflow=-5536
charNext=B
fractionalNarrowing=5
negativeRemainder=-1
capturedOldValue=15
array=[17, 20],index=1
message=Java 17: 3
integerZero=ArithmeticException
unchangedAfterException=12
floatingDivision=Infinity
floatingRemainderNaN=true
intOverflow=-2147483648
~~~

`java -version` và `javac -version` có đầu ra phụ thuộc máy và không thuộc khối expected stdout trên.

## 3. Điểm khác phép gán thông thường: chuyển kiểu ngầm

Khi thực hiện phép gán kết hợp, Java dùng kiểu của các toán hạng để tính phép toán, sau đó **chuyển kết quả về kiểu của biến vế trái** theo quy tắc của compound assignment.

~~~java
byte b = 120;
b += 10; // Hop le, b thanh -126 vi ket qua 130 bi thu hep ve byte.
~~~

Trong khi đó:

~~~java
byte b = 120;
// b = b + 10; // Loi bien dich: b + 10 co kieu int, khong tu gan ve byte.
b = (byte) (b + 10); // Hop le voi cast tuong minh.
~~~

Trong ví dụ trên, `byte` có miền từ `-128` đến `127`. Kết quả toán học 130 không biểu diễn được bằng `byte`; phép chuyển kiểu thu hẹp giữ những bit thấp thích hợp, khiến giá trị thành **-126**. `+=` **không tự kiểm tra overflow**.

Với `short` và `char` cũng cần chú ý việc phép toán số học thường thực hiện qua numeric promotion:

~~~java
short amount = 30_000;
amount *= 2; // 60000 thu hep ve short: -5536.

char ch = 'A';
ch += 1; // 'B'.
~~~

Một trường hợp còn khó nhận ra hơn:

~~~java
int count = 3;
count += 2.5; // Hop le, ket qua int la 5.
// count = count + 2.5; // Loi: double khong tu gan duoc vao int.
~~~

Ở `count += 2.5`, phép cộng tạo `double 5.5`, rồi kết quả được chuyển về `int`, thành **5**. Phép thu hẹp số thực thành số nguyên ở Java **cắt phần thập phân về 0** trong miền giá trị biểu diễn được. Do đó tránh gán kết hợp giữa các kiểu có thể gây mất dữ liệu nếu bài toán yêu cầu chính xác.

| Biểu thức | Có biên dịch? | Diễn giải |
| --- | --- | --- |
| `byte b = 1; b += 1;` | Có | `byte` được cập nhật sau chuyển kiểu |
| `byte b = 1; b = b + 1;` | Không | `b + 1` có kiểu `int` |
| `char c = 'A'; c += 1;` | Có | Ký tự kế tiếp theo đơn vị mã `char` |
| `int x = 3; x += 2.5;` | Có | `double 5.5` bị thu hẹp về `int 5` |
| `int x = 3; x = x + 2.5;` | Không | `double` không tự gán vào `int` |

**Nguyên tắc thực hành:** Khi các toán hạng có kiểu khác nhau, đừng chỉ nhìn vào tính hợp lệ khi biên dịch; hãy kiểm tra **kiểu phép toán trung gian và chuyển kiểu cuối cùng**.

## 4. Vế trái chỉ được đánh giá một lần

Đây là lý do **`a[i++] += 7` không tương đương** với phép thay thế văn bản `a[i++] = a[i++] + 7`.

~~~java
int[] numbers = {10, 20};
int i = 0;
numbers[i++] += 7;
// numbers = [17, 20]; i = 1.
~~~

Phép gán kết hợp trước tiên xác định vị trí `numbers[i++]` đúng một lần, lưu giá trị cũ của phần tử, đánh giá biểu thức bên phải và gán kết quả trở lại **chính vị trí đó**. `i++` chỉ tăng `i` một lần.

Nếu thay thủ công bằng `numbers[i++] = numbers[i++] + 7`, `i++` xuất hiện hai lần: các vị trí đọc/ghi và trạng thái sau phép gán có thể khác. Vì vậy **không tự khai triển `op=` khi vế trái có tác dụng phụ**.

Đối với biến đơn giản, thứ tự đánh giá cũng có thể quan sát được:

~~~java
int n = 5;
n += (n = 10);
System.out.println(n); // 15.
~~~

Giá trị cũ của `n` là **5** được giữ cho phép cộng, rồi vế phải gán `n = 10` và trả giá trị 10; cuối cùng `+=` ghi kết quả **15**. Biểu thức này hợp lệ nhưng **khó đọc**, không nên bắt chước khi mã có thể viết thành nhiều bước.

## 5. `/=`: phân biệt phép chia nguyên và chia số thực

~~~java
int count = 10;
count /= 4;   // 2, vi phep chia int cat phan thap phan.

double rate = 10.0;
rate /= 4;    // 2.5.
~~~

- `int /= int` thực hiện chia nguyên; phần dư thập phân không được giữ lại.
- `double /= int` áp dụng numeric promotion rồi cho kết quả `double`.

**Chia cho 0** có hành vi khác nhau:

~~~java
int x = 12;
int zero = 0;
try {
    x /= zero;
} catch (ArithmeticException ex) {
    System.out.println("division failed");
}
// x van bang 12, do phep gan chua hoan tat binh thuong.

double y = 1.0;
y /= 0.0; // Infinity, khong nem ArithmeticException.
~~~

Với số nguyên, `/= 0` ném `ArithmeticException` và phép gán kết hợp **không ghi kết quả**. Với số thực, `Infinity`/`NaN` tuân theo quy tắc IEEE 754; nếu cần giá trị hữu hạn, phải kiểm tra bằng `Double.isFinite` hoặc `Float.isFinite`.

## 6. `%=`: phần dư không phải lúc nào cũng không âm

~~~java
int n = -10;
n %= 3; // -1.
~~~

Toán tử `%` trên số nguyên dùng thương chia **cắt về 0**. Phần dư khác 0 có **cùng dấu với số bị chia** (toán hạng trái). Đổi dấu số chia không bắt buộc đổi dấu phần dư.

~~~java
int a = 10;
a %= -3; // 1.

int b = -10;
b %= -3; // -1.
~~~

Nếu cần **chỉ số vòng không âm** với modulus dương, `Math.floorMod(value, positiveModulus)` thường đúng ý hơn `value % positiveModulus` khi `value` có thể âm. Đó là phép tính mới, không có toán tử gán kết hợp riêng `floorMod=`.

Với `double`, `0.0 % 0.0` tạo `NaN` chứ không ném `ArithmeticException` như `int % 0`. Phải phân biệt kiểu toán hạng trước khi suy luận kết quả.

## 7. `+=` cũng dùng cho `String`

`+=` có thể **nối chuỗi**, không chỉ cộng số:

~~~java
String label = "Java";
label += " 17";
label += ": " + (1 + 2);
System.out.println(label); // Java 17: 3.
~~~

`String` bất biến: mỗi phép nối tạo giá trị chuỗi kết quả rồi gán lại biến `label`; nó không sửa nội dung của `String` cũ. Với phép nối nhiều lần trong vòng lặp lớn, `StringBuilder` thường thích hợp hơn.

Trong `label += ": " + (1 + 2)`, dấu ngoặc đảm bảo `1 + 2` được cộng số trước khi nối chuỗi. Nếu viết `label += ": " + 1 + 2`, phần nối với chuỗi có thể cho `": 12"` theo cách nhóm của toán tử `+`.

**`-=`, `*=`, `/=` và `%=` không phải toán tử hợp lệ để thao tác `String` theo phép toán số học.**

## 8. Tràn số, chuyển kiểu và giới hạn an toàn

~~~java
int count = Integer.MAX_VALUE;
count += 1; // -2147483648: tran int, khong nem exception.
~~~

Phép gán kết hợp nguyên **không tự phát hiện overflow**. Nếu yêu cầu báo lỗi thay vì cho kết quả bị tràn, dùng phép toán kiểm tra:

~~~java
int count = Integer.MAX_VALUE;
// count = Math.addExact(count, 1);
// Nem ArithmeticException neu vuot mien int.
~~~

Với phép toán số học chính xác hơn, lựa chọn giải pháp theo nhu cầu:

- `Math.addExact`, `subtractExact`, `multiplyExact` dành cho kiểm tra overflow trong phạm vi nguyên thủy tương ứng.
- `BigInteger` dùng cho số nguyên vượt phạm vi `long`.
- `BigDecimal` có thể dùng khi cần phép tính thập phân với chính sách scale/rounding rõ ràng.

Các lớp `BigInteger` và `BigDecimal` không cho dùng `+=` trực tiếp giữa hai đối tượng như primitive; phải gọi phương thức thích hợp và gán lại biến:

~~~java
java.math.BigDecimal total = new java.math.BigDecimal("1.25");
total = total.add(new java.math.BigDecimal("2.50"));
~~~

## 9. Lỗi thường gặp

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| “`b += 1` giống hệt `b = b + 1` với `byte`.” | `b += 1` cho phép chuyển kiểu thu hẹp; `b = b + 1` không biên dịch |
| “`+=` luôn bảo toàn đầy đủ phần thập phân.” | `int += double` có thể cắt phần thập phân |
| “Vế trái được đánh giá hai lần.” | Compound assignment chỉ đánh giá **vị trí vế trái một lần** |
| “`x /= 4` luôn là phép chia số thực.” | Nếu `x` và `4` là `int`, đây là chia nguyên |
| “`%` luôn cho phần dư không âm.” | Số bị chia âm có thể cho phần dư âm |
| “`+=` trên `String` cộng các giá trị số.” | Khi tham gia nối chuỗi, `+` có thể tạo văn bản |
| “`x += 1` ném lỗi khi `int` tràn.” | Không; dùng `Math.addExact` khi cần kiểm tra |
| “Nếu `x /= 0` ném lỗi, `x` được gán 0.” | Phép gán không hoàn tất; giá trị cũ vẫn giữ |
| “`/ 0.0` và `/ 0` luôn giống nhau.” | Phép chia số thực có thể cho `Infinity`/`NaN` |

## 10. Bài tập tự kiểm tra

1. `int x = 10; x += 3; x *= 2;` cho `x` bằng bao nhiêu?  
   *Đáp án:* **26**.
2. `byte b = 120; b += 10;` cho kết quả gì?  
   *Đáp án:* **-126**, do kết quả 130 bị chuyển về kiểu `byte`.
3. `int n = 3; n += 2.5;` cho `n` bằng bao nhiêu?  
   *Đáp án:* **5**, vì `double 5.5` bị thu hẹp về `int`.
4. `int n = 10; n /= 4;` cho kết quả gì?  
   *Đáp án:* **2**, phép chia nguyên.
5. `int n = -10; n %= 3;` cho kết quả gì?  
   *Đáp án:* **-1**, dấu theo số bị chia.
6. `int[] a = {10, 20}; int i = 0; a[i++] += 7;` thay đổi mảng và `i` ra sao?  
   *Đáp án:* Mảng thành **`[17, 20]`**, `i = 1`; vị trí vế trái chỉ được đánh giá một lần.
7. `int x = 5; x += (x = 10);` cho `x` bằng bao nhiêu?  
   *Đáp án:* **15**, vì toán hạng trái đã lưu giá trị 5.
8. `String s = "A"; s += 1 + 2;` cho chuỗi gì?  
   *Đáp án:* **`"A3"`** vì toàn biểu thức bên phải tính `1 + 2` trước khi nối vào `s`.

## Checklist

- [ ] Phân biệt `+=`, `-=`, `*=`, `/=`, `%=` theo phép toán của từng loại toán hạng.
- [ ] Hiểu phép gán kết hợp **gán lại về cùng kiểu vế trái** và có thể thu hẹp ngầm.
- [ ] Nhớ vế trái `E1` chỉ được đánh giá **một lần**.
- [ ] Với số nguyên, chú ý phép chia cắt phần thập phân và dấu của `%`.
- [ ] Phân biệt chia/lấy dư với số chia zero của số nguyên và số thực.
- [ ] Nhận diện overflow và giá trị bị cắt khi chuyển về `byte`/`short`/`int`.
- [ ] Biết `String +=` là nối chuỗi, không phải phép cộng số.
- [ ] Chọn `Math.*Exact` hoặc lớp số lớn/chính xác nếu yêu cầu vượt khả năng primitive.

**Kết luận:** Phép gán kết hợp trong Java là phép toán **và gán lại**, với hai điểm không thể bỏ qua: **vế trái chỉ được đánh giá một lần**, và **kết quả có thể bị chuyển thu hẹp về kiểu vế trái**. Hiểu các quy tắc đó giúp tránh lỗi với `byte`/`short`, số thực, chỉ số mảng, tràn số và phép nối chuỗi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="assignment-and-unary-operators-004">Chuyển kiểu ngầm định trong phép gán kết hợp</a>

<details>
<summary>Xem chi tiết</summary>

# Chuyển kiểu ngầm định trong phép gán kết hợp — Java 17+

## 1. Bài toán: vì sao `b += 10` được mà `b = b + 10` không được?

Trong Java, các toán tử gán kết hợp (*compound assignment*) như `+=`, `-=`, `*=`, `/=` vừa tính toán vừa cập nhật biến ở vế trái. Với kiểu số nguyên nhỏ, chúng thực hiện **chuyển đổi về kiểu đích một cách ngầm định**, kể cả khi phép tính trung gian được nâng lên kiểu lớn hơn.

~~~java
byte b = 120;
b += 10; // Biên dịch được
System.out.println(b); // -126
~~~

Trong khi đó:

~~~java
byte b = 120;
// b = b + 10; // Lỗi compile: byte + int tạo int, không tự gán vào byte
~~~

**Vì sao `-126`?** Java nâng `b` thành `int`, tính `120 + 10 = 130`, rồi thu hẹp về `byte` 8 bit. Giá trị 130 vượt `Byte.MAX_VALUE` (127) nên kết quả lưu thành -126. Compiler **không** tự từ chối mất dữ liệu ở phép `+=` này.

Bài học cốt lõi: **cú pháp gán kết hợp có quy tắc chuyển kiểu khác phép gán `=` thông thường**. Dùng ngắn hơn không có nghĩa an toàn hơn.

## 2. Công thức ngữ nghĩa của phép gán kết hợp

Với một biến số nguyên thủy `x` có kiểu `T`, có thể hình dung:

~~~java
x op= y;
~~~

gần tương đương:

~~~java
x = (T) (x op y);
~~~

**Ngoại lệ quan trọng:** vế trái của `op=` **chỉ được đánh giá một lần**; thay máy móc bằng biểu thức dài `x = (T)(x op y)` có thể thay đổi side effect nếu `x` là field hoặc phần tử mảng truy cập qua biểu thức.

Với toán hạng số học nguyên thủy, thứ tự ý nghĩa cơ bản là:

1. Xác định nơi chứa giá trị ở vế trái, lấy giá trị hiện tại.
2. Đánh giá toán hạng phải theo quy tắc thứ tự của Java.
3. Thực hiện phép toán `op` với **numeric promotion** khi cần.
4. Chuyển kết quả về **kiểu của vế trái**, kể cả narrowing.
5. Gán lại giá trị đã chuyển; biểu thức gán kết hợp cho giá trị sau cập nhật.

~~~mermaid
flowchart TD
    A["byte b = 120"] --> B["b += 10"]
    B --> C["Nâng byte thành int"]
    C --> D["120 + 10 = 130, kiểu int"]
    D --> E["Thu hẹp int về byte"]
    E --> F["b trở thành -126"]
~~~

Công thức trên minh họa tốt cho **kiểu nguyên thủy số**. Với `String +=`, toán tử bitwise boolean hoặc wrapper, có thêm quy tắc nối chuỗi, unboxing/boxing hoặc phép toán tương ứng; không diễn giải chúng như một phép cast số duy nhất.

## 3. So sánh gán thường và gán kết hợp

| Cú pháp | Hành vi | Ví dụ |
|---|---|---|
| `x = y` | Vế phải phải tương thích qua *assignment conversion* | `int x = 3;` |
| `x += y` | Cộng/nối rồi cập nhật; với primitive số có narrowing ngầm về kiểu đích | `byte b = 120; b += 10;` |
| `x -= y` | Trừ rồi cập nhật, có thể narrowing | `short s = 5; s -= 2;` |
| `x *= y` | Nhân rồi cập nhật, có thể overflow/narrowing | `int n = 5; n *= 3;` |
| `x /= y` | Chia rồi cập nhật; với số nguyên vẫn cắt thương về 0 | `int n = 7; n /= 2;` |
| `x %= y` | Lấy dư rồi cập nhật | `int n = 7; n %= 2;` |
| `x <<= y`, `>>=`, `>>>=` | Dịch bit rồi gán | `int bits = 1; bits <<= 2;` |
| `x &= y`, `^=`, `|=` | Phép bitwise hoặc boolean phù hợp kiểu | `int mask = 7; mask &= 3;` |

**Không** có toán tử `&&=` hoặc `||=` trong Java. Với boolean, `&=` và `|=` **không áp dụng short-circuit như `&&`/`||`**; vế phải vẫn được đánh giá.

## 4. Binary numeric promotion và narrowing tự động

Một phép cộng giữa `byte` và `int` thông thường tạo **`int`**:

~~~java
byte b = 100;
int promoted = b + 1;
System.out.println(promoted); // 101
~~~

Nhưng:

~~~java
byte b = 100;
b += 1;           // Hợp lệ, kết quả lưu vào byte
// b = b + 1;     // Lỗi compile: int không tự thu hẹp về byte
b = (byte) (b + 1); // Hợp lệ, ép kiểu tường minh
~~~

| Vế trái | Phép tính trung gian | Kiểu lưu sau `op=` |
|---|---|---|
| `byte` | Thường nâng lên `int` hoặc rộng hơn | `byte` |
| `short` | Thường nâng lên `int` hoặc rộng hơn | `short` |
| `char` | Thường nâng lên `int` hoặc rộng hơn | `char` |
| `int` | `int`, `long`, `float` hoặc `double` tùy toán hạng | `int` |
| `long` | `long`, `float` hoặc `double` tùy toán hạng | `long` |
| `float` | `float` hoặc `double` | `float` |
| `double` | `double` | `double` |

Ví dụ thu hẹp từ `long` ngay trong phép `+=`:

~~~java
byte n = 1;
n += 255L; // int/long arithmetic, sau đó về byte
System.out.println(n); // 0: 1 + 255 = 256, byte chỉ giữ 8 bit thấp
~~~

Còn phép gán thường:

~~~java
byte n = 1;
// n = n + 255L; // Lỗi compile: kết quả long không tự gán vào byte
~~~

**Cảnh báo:** trình biên dịch cho phép nhiều compound assignment làm mất dữ liệu **dù vế phải không phải hằng và không vừa miền**. Đừng coi khả năng compile là chứng nhận an toàn số học.

## 5. Ví dụ `short`, `char`, `int`, `long`

### `short` bị tràn

~~~java
short s = 32767;
s += 1;
System.out.println(s); // -32768
~~~

Phép cộng có kết quả `int` 32768, thu hẹp về `short` 16 bit có dấu thành -32768.

### `char` tăng mã UTF-16

~~~java
char letter = 'A';
letter += 1;
System.out.println(letter); // B
~~~

`char + int` được tính qua `int`, sau đó gán trở lại `char`. Vì `char` là một UTF-16 code unit (0..65535), nó không phải kiểu biểu diễn mọi ký tự Unicode độc lập, nhất là các ký tự cần surrogate pair.

### `int` thu hẹp kết quả `double`

~~~java
int count = 5;
count += 2.9;
System.out.println(count); // 7
~~~

Phép cộng tạo `double` 7.9, sau đó chuyển về `int` theo quy tắc thu hẹp số thực: **cắt phần lẻ về 0** đối với giá trị hữu hạn trong miền.

Đoạn tương đương về ý nghĩa số với vế trái đơn giản:

~~~java
count = (int) (count + 2.9);
~~~

Không thể thay bằng `count = count + 2.9;` vì `double` không tự gán vào `int`.

### `long` nhận phép cộng với `double`

~~~java
long total = 10L;
total += 2.9;
System.out.println(total); // 12
~~~

Kiểu kết quả cuối vẫn là `long`; phần lẻ bị loại khi thu hẹp từ `double`.

**Nguyên tắc nghiệp vụ:** nếu độ chính xác của số thực có ý nghĩa, việc tự động cắt phần lẻ trong `+=` có thể là bug. Tốt hơn hãy chuyển đổi tường minh kèm kiểm tra hoặc giữ loại dữ liệu đáp ứng yêu cầu.

## 6. Overflow và mất dữ liệu không phát sinh exception mặc định

~~~java
int count = Integer.MAX_VALUE;
count += 1;
System.out.println(count); // -2147483648
~~~

Với `int`, kết quả tràn được tính theo quy tắc số học integer của Java; **không tự ném ngoại lệ overflow**.

Nếu phải phát hiện tràn:

~~~java
int count = Integer.MAX_VALUE;
try {
    count = Math.addExact(count, 1);
} catch (ArithmeticException ex) {
    System.out.println("overflow detected");
}
~~~

**Expected output:**

~~~text
overflow detected
~~~

`Math.addExact` phát hiện tràn `int`/`long` phù hợp overload. Đừng dùng `+=` với giả định nó sẽ tự ngăn overflow hoặc mất thông tin.

Một trường hợp khác là thu hẹp từ `double` sang `int`: nếu giá trị quá lớn, Java có quy tắc bão hòa theo miền số nguyên khi narrowing floating-to-integral (và các quy tắc với NaN). **Không tự coi kết quả ép kiểu này là một phép validate miền.**

## 7. Giới hạn của quy tắc narrowing

Phần **cộng, trừ, nhân, chia, lấy dư, nối `String`** và hành vi vế trái chỉ đánh giá một lần đã được trình bày cùng các ví dụ có thể chạy trong bài “Phép gán kết hợp `+=`, `-=`, `*=`, `/=` và `%=`”. Ở đây ta phân tích thêm **kiểu đích** và những giá trị biên mà bước chuyển đổi âm thầm tạo ra.

| Biểu thức | Phép toán trung gian | Giá trị cuối sau gán |
|---|---|---|
| `byte b = 1; b += 255L;` | `long` 256 | `byte` 0 |
| `int n = 5; n += 2.9;` | `double` 7.9 | `int` 7 |
| `long n = 10L; n += 2.9;` | `double` 12.9 | `long` 12 |
| `short s = 32767; s += 1;` | `int` 32768 | `short` -32768 |

Điểm chung của bốn trường hợp là **kiểu tính toán trung gian không nhất thiết trùng kiểu lưu trữ**. Biến sau phép gán vẫn giữ kiểu đã khai báo, nhưng giá trị có thể thay đổi vì narrowing.

## 8. Trường hợp đặc biệt khi thu hẹp `double` thành `int`

Java có quy tắc cụ thể cho NaN và infinity khi chuyển `double` sang `int`: NaN thành `0`; infinity dương bị chặn tại `Integer.MAX_VALUE`; infinity âm tại `Integer.MIN_VALUE`. Điều này cũng xuất hiện trong compound assignment:

~~~java
int a = 5;
a += Double.NaN;               // a = 0

int b = 0;
b += Double.POSITIVE_INFINITY; // b = 2147483647

int c = 0;
c += Double.NEGATIVE_INFINITY; // c = -2147483648
~~~

Không nhầm việc **chặn giá trị khi narrowing** với kiểm tra hợp lệ nghiệp vụ. Nếu không chấp nhận NaN, infinity hay mất phần thập phân, cần kiểm tra bằng `Double.isFinite` và kiểm tra miền trước khi chuyển.

## 9. Bitwise, boolean và `String` nằm ngoài công thức cast số giản lược

`<<=`, `>>=` và `>>>=` dùng quy tắc dịch bit của Java; `int` chỉ dùng 5 bit thấp của lượng dịch và `long` dùng 6 bit thấp. `&=`, `|=`, `^=` có thể là phép bitwise hoặc boolean tùy kiểu; trên boolean, `&` và `|` **không short-circuit**. `String +=` dùng nối chuỗi chứ không dùng cast số.

~~~java
int bit = 1;
bit <<= 33; // 2, vì lượng dịch được lấy modulo 32

boolean active = false;
active |= true; // true

String label = "item=";
label += 7; // "item=7"
~~~

Các phép này giúp xác định **ranh giới áp dụng** của giải thích “tính số rồi cast về `T`”, không phải là những phép chuyển kiểu số tương tự byte/short.

## 10. Đừng bỏ qua vị trí chứa giá trị ở vế trái

`numbers[index++] += 5` đánh giá vị trí phần tử một lần, lưu giá trị cũ, tính với `5` và gán trở lại phần tử đã chọn. Không được khai triển thành `numbers[index++] = numbers[index++] + 5`: cách viết sau tăng chỉ số **hai lần**. Đây là khác biệt độc lập với narrowing; khi kiểm tra một phép gán kết hợp, cần xem **cả kiểu kết quả lẫn số lần đánh giá vế trái**.

## 11. Wrapper và auto-unboxing/boxing

Với lớp bao như `Integer`, gán kết hợp có thể thực hiện **unboxing → phép tính → boxing**:

~~~java
Integer boxed = 10;
boxed += 5;
System.out.println(boxed); // 15
~~~

Không nên coi việc này là cập nhật giá trị bên trong một object `Integer`; wrapper là immutable. Biến `boxed` được gán lại một giá trị tham chiếu wrapper đại diện cho số mới.

Nếu wrapper là null, phép tính cần unbox sẽ ném `NullPointerException`:

~~~java
Integer boxed = null;
try {
    boxed += 1;
} catch (NullPointerException ex) {
    System.out.println("null unboxing");
}
~~~

**Expected output:**

~~~text
null unboxing
~~~

So với primitive, wrapper cho phép null và được dùng trong generic; `+=` không xóa đi rủi ro null. Kiểm tra dữ liệu trước khi thực hiện phép toán hoặc sử dụng primitive khi trạng thái nullable không cần thiết.

## 12. Biểu thức gán kết hợp cũng trả giá trị

~~~java
int count = 2;
int result = (count += 3) * 4; // count=5, result=20
~~~

`count += 3` có kiểu của biến đích và cung cấp giá trị sau cập nhật. Khi không cần giá trị trung gian, nên viết `count += 3; int result = count * 4;` để giảm side effect bên trong biểu thức.

## 13. `final` không cho phép phép gán kết hợp

~~~java
final int fixed = 3;
// fixed += 1; // Lỗi compile: vẫn là gán lại biến final
~~~

Không có ngoại lệ dành riêng cho `op=`: dù được phép narrowing ngầm, toán tử vẫn phải tuân thủ khả năng gán của biến đích.

## 14. Chương trình Java 17 — kiểm tra chuyển đổi kiểu và giá trị biên

Lưu thành `CompoundAssignmentConversionDemo.java` và chạy với JDK 17+:

~~~java
public final class CompoundAssignmentConversionDemo {
    public static void main(String[] args) {
        byte b = 120;
        b += 10;
        System.out.println("byte=" + b);

        short s = Short.MAX_VALUE;
        s += 1;
        System.out.println("short=" + s);

        int mixed = 5;
        mixed += 2.9;
        System.out.println("intFromDouble=" + mixed);

        long wide = 10L;
        wide += 2.9;
        System.out.println("longFromDouble=" + wide);

        int nan = 5;
        nan += Double.NaN;
        System.out.println("nan=" + nan);

        int positiveInfinity = 0;
        positiveInfinity += Double.POSITIVE_INFINITY;
        System.out.println("positiveInfinity=" + positiveInfinity);

        Integer boxed = 10;
        boxed += 5;
        System.out.println("boxed=" + boxed);

        Integer missing = null;
        try {
            missing += 1;
        } catch (NullPointerException ex) {
            System.out.println("nullWrapper=NullPointerException");
        }

        int[] numbers = {10};
        int index = 0;
        numbers[index++] += 5;
        System.out.println("array=" + numbers[0] + ",index=" + index);
    }
}
~~~

~~~bash
javac --release 17 -encoding UTF-8 CompoundAssignmentConversionDemo.java
java CompoundAssignmentConversionDemo
~~~

**Expected stdout:**

~~~text
byte=-126
short=-32768
intFromDouble=7
longFromDouble=12
nan=0
positiveInfinity=2147483647
boxed=15
nullWrapper=NullPointerException
array=15,index=1
~~~

Các dòng `nan` và `positiveInfinity` cho thấy narrowing không tự báo lỗi; `boxed` và `nullWrapper` cho thấy wrapper cần unboxing/boxing; dòng `array` xác nhận phép gán kết hợp chỉ đánh giá vị trí phần tử một lần.

## 15. Sai lầm dễ gây mất dữ liệu

| Suy đoán sai | Cách kiểm chứng |
|---|---|
| “`b += 10` luôn giống `b = b + 10`.” | Kiểm tra numeric promotion và cast ngầm; `byte` có thể compile ở `+=` nhưng không ở `= b + ...`. |
| “Compile được nghĩa là không mất dữ liệu.” | Thử biên `byte`, `short` và `int += double`; kiểm tra miền/precision trước. |
| “NaN và infinity sẽ gây ngoại lệ khi cộng vào `int`.” | Narrowing `double` sang `int` theo các giá trị định nghĩa của Java, không mặc định ném lỗi. |
| “`Integer` luôn xử lý như `int`.” | `Integer` nullable, việc unbox null có thể ném `NullPointerException`. |
| “Có thể sao chép biểu thức vế trái khi thay `op=`.” | Với mảng/field chứa lời gọi hoặc `index++`, số lần đánh giá thay đổi. |
| “`+=` là thao tác nguyên tử giữa nhiều thread.” | Đọc–tính–ghi không tự đồng bộ; cần cơ chế đồng bộ/atomic tương ứng. |

## 16. Bài tập phân tích kiểu và miền

1. Với `byte x = 127; x += 1;`, dự đoán `x`. **Đáp án:** -128 sau narrowing từ `int` 128.
2. Với `int n = 10; n += -2.8;`, dự đoán `n`. **Đáp án:** 7; phép cộng trung gian cho `double` 7.2 rồi cắt phần lẻ.
3. Với `int n = 8; n += Double.NaN;`, dự đoán `n`. **Đáp án:** 0 do quy tắc narrowing từ NaN sang `int`.
4. Với `Integer n = null; n += 1;`, điều gì xảy ra? **Đáp án:** `NullPointerException` khi unboxing trước phép cộng.
5. Với `int[] a = {5}; int i = 0; a[i++] += 2;`, giá trị cuối là gì? **Đáp án:** `a[0] == 7`, `i == 1`; vị trí được đánh giá một lần.

## 17. Checklist và kết luận

- [ ] Biết phân biệt kiểu **tính toán trung gian** với kiểu **đích** của compound assignment.
- [ ] Có thể giải thích byte/short overflow và cắt phần lẻ khi `int += double`.
- [ ] Nắm quy tắc Java với NaN, infinity và giá trị ngoài miền khi narrowing từ số thực sang số nguyên.
- [ ] Biết wrapper nullable có thể gây NPE khi unbox.
- [ ] Không thay máy móc `op=` bằng hai lần đánh giá vế trái.
- [ ] Chạy ví dụ và đối chiếu kết quả, kiểm tra miền và precision nếu bài toán cần dữ liệu chính xác.

**Kết luận:** Compound assignment thuận tiện nhưng cho phép **chuyển kiểu hẹp ngầm**, có thể mất bit, phần thập phân hoặc nhận giá trị chặn ở miền số nguyên mà không báo lỗi. Hãy xác định kiểu trung gian, kiểu đích và điều kiện hợp lệ của dữ liệu trước khi chọn `op=`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="assignment-and-unary-operators-005">Tăng giảm tiền tố và hậu tố</a>

<details>
<summary>Xem chi tiết</summary>

# Tăng giảm tiền tố và hậu tố trong Java

> **Java core — language-basics / Gán và toán tử một ngôi**  
> **Phiên bản:** Java 17+; ví dụ độc lập, chỉ cần JDK.  
> **Mục tiêu:** Phân biệt `++x`, `x++`, `--x`, `x--`; xác định **giá trị của biểu thức** và **giá trị biến sau khi cập nhật**; sử dụng đúng trong vòng lặp và tránh những biểu thức chứa nhiều tác dụng phụ khó hiểu.

## 1. Bốn cách tăng giảm một đơn vị

`++` tăng giá trị biến thêm 1, còn `--` giảm đi 1. Hai toán tử này có hai vị trí viết:

| Cú pháp | Tên | Thay đổi biến | Giá trị của biểu thức |
| --- | --- | --- | --- |
| `++x` | Tăng **tiền tố** (*prefix increment*) | `x` tăng 1 | Giá trị **mới** |
| `x++` | Tăng **hậu tố** (*postfix increment*) | `x` tăng 1 | Giá trị **cũ** |
| `--x` | Giảm **tiền tố** (*prefix decrement*) | `x` giảm 1 | Giá trị **mới** |
| `x--` | Giảm **hậu tố** (*postfix decrement*) | `x` giảm 1 | Giá trị **cũ** |

Ví dụ ban đầu `x = 5`:

~~~java
int x = 5;
int before = x++; // before = 5, x = 6.
int after  = ++x; // x = 7, after = 7.
~~~

Cả tiền tố và hậu tố đều thay đổi biến **một đơn vị**. Khác biệt chỉ hiện rõ khi bạn **dùng giá trị do biểu thức trả ra** trong một phép gán, phép tính, điều kiện hoặc lời gọi phương thức.

## 2. Giá trị biểu thức khác với trạng thái biến sau câu lệnh

Xét hai đoạn độc lập:

~~~java
int a = 10;
int first = a++; // first = 10, a = 11.

int b = 10;
int second = ++b; // second = 11, b = 11.
~~~

- Với `a++`: lấy **giá trị cũ** 10 làm kết quả biểu thức, đồng thời tăng `a` thành 11.
- Với `++b`: tăng `b` thành 11 rồi cho biểu thức giá trị **mới** 11.

Giảm tiền tố/hậu tố tương tự:

~~~java
int a = 10;
int old = a--;  // old = 10, a = 9.

int b = 10;
int now = --b;  // now = 9, b = 9.
~~~

Nếu chỉ cần tăng/giảm biến mà **không sử dụng kết quả biểu thức**, hai cách là tương đương về giá trị sau câu lệnh:

~~~java
int count = 0;
count++; // count = 1.
++count; // count = 2.
count--; // count = 1.
--count; // count = 0.
~~~

Đừng hiểu “hậu tố thực hiện sau” là biến sẽ chỉ thay đổi ở **câu lệnh tiếp theo**: tác dụng tăng/giảm đã xảy ra trong quá trình đánh giá biểu thức hiện tại.

## 3. Có thể áp dụng lên đâu?

Toán hạng của `++` và `--` phải là **biến có thể cập nhật** thuộc kiểu số phù hợp (bao gồm `char` và các lớp bao số qua unboxing/boxing). Ví dụ hợp lệ:

~~~java
int counter = 2;
counter++;

int[] scores = {5, 8};
scores[0]++;

class Box {
    int size = 1;
}
Box box = new Box();
++box.size;

char letter = 'A';
letter++; // 'B'.
~~~

Không thể tăng một literal, giá trị tính toán không phải biến, biến `final`, hoặc kiểu `boolean`:

~~~java
// KHÔNG BIÊN DỊCH:
// 5++;
// (counter + 1)++;
// final int fixed = 2;
// fixed++;
// boolean enabled = true;
// enabled++;
~~~

Đối với wrapper, giá trị `null` cần chú ý:

~~~java
Integer boxed = 4;
boxed++; // Hợp lệ: unbox, tăng, rồi box lại thành Integer 5.

// Integer absent = null;
// absent++; // Nếu chạy: NullPointerException khi unboxing.
~~~

Thao tác với `byte`, `short` hay `char` vẫn trả kết quả theo kiểu biến; việc tăng giảm có thể vượt miền và làm thay đổi/đảo giá trị, không tự ném exception tràn số.

## 4. Dùng trong biểu thức và thứ tự đánh giá

Java đánh giá toán hạng theo quy tắc xác định. Ví dụ:

~~~java
int n = 1;
int result = n++ + ++n;
// n++ trả 1, n trở thành 2.
// ++n tăng n thành 3 và trả 3.
// result = 1 + 3 = 4; n = 3.
~~~

Kết quả này **được Java quy định**, nhưng diễn đạt nhiều cập nhật lên cùng biến trong một biểu thức như vậy rất khó đọc và dễ sửa sai. Ưu tiên tách ra:

~~~java
int n = 1;
int oldValue = n;
n++;
n++;
int newValue = n;
int result = oldValue + newValue; // result = 4.
~~~

Một trường hợp phổ biến, dễ hiểu hơn, là **dùng chỉ số mảng rồi tăng chỉ số**:

~~~java
int[] numbers = {10, 20, 30};
int i = 0;
int selected = numbers[i++]; // selected = 10, i = 1.
~~~

`i++` trả **0** để truy cập phần tử đầu tiên, sau đó `i` trở thành 1. Tương đương ý định với:

~~~java
int selected = numbers[i];
i++;
~~~

**Khuyến nghị:** Chỉ dùng tiền tố/hậu tố bên trong biểu thức khi người đọc có thể hiểu ngay cả **giá trị biểu thức** lẫn **tác dụng phụ**. Khi nhiều biến bị thay đổi, dùng câu lệnh độc lập hoặc biến trung gian.

## 5. Vòng lặp `for` và `while`

Trong vòng `for`, phần cập nhật thường không cần giá trị do `i++` hay `++i` trả ra:

~~~java
for (int i = 0; i < 3; i++) {
    System.out.println("index=" + i);
}
~~~

Ở đây đổi `i++` thành `++i` **không đổi chuỗi giá trị được in**, vì kết quả biểu thức tăng không được sử dụng. Cả hai đều tăng `i` một đơn vị ở cuối mỗi lượt.

Vòng `while` cũng vậy:

~~~java
int remaining = 3;
while (remaining > 0) {
    System.out.println(remaining);
    remaining--;
}
~~~

Không nên chọn tiền tố thay hậu tố chỉ vì nghĩ nó “luôn nhanh hơn”. Với kiểu nguyên primitive trong Java 17, chọn cú pháp **diễn đạt rõ ý định**; hiệu năng thực tế phụ thuộc compiler/JIT và ngữ cảnh, không dựa vào mẹo này.

## 6. Tràn số và những bẫy khác

Các toán tử này không tự kiểm tra tràn cho phép tăng/giảm nguyên thông thường:

~~~java
int maximum = Integer.MAX_VALUE;
maximum++; // -2147483648, không tự ném ArithmeticException.

byte tiny = 127;
tiny++; // -128 vì byte có miền -128..127.
~~~

Nếu yêu cầu **phát hiện tràn** thay vì chấp nhận kết quả quay vòng, dùng:

~~~java
int next = Math.incrementExact(Integer.MAX_VALUE); // Ném ArithmeticException.
int prev = Math.decrementExact(Integer.MIN_VALUE); // Ném ArithmeticException.
~~~

Một bẫy khác là kết hợp phép gán với hậu tố:

~~~java
int n = 5;
int previous = n++; // Dễ hiểu: previous=5, n=6.

// Tránh viết câu khó đọc kiểu: n = n++;
// Phép gán ngoài có thể ghi đè giá trị vừa cập nhật.
~~~

Việc `n = n++` không làm tăng giá trị `n` cuối cùng: `n++` trả **giá trị cũ**, rồi phép gán ngoài gán lại giá trị cũ đó. Nếu muốn tăng, chỉ cần `n++;` hoặc `n += 1;`.

## 7. Ví dụ Java 17+ runnable

Tạo tệp **PrefixPostfixDemo.java**:

~~~java
public final class PrefixPostfixDemo {
    public static void main(String[] args) {
        int a = 5;
        int postIncrement = a++;
        System.out.println("postIncrement=" + postIncrement + ",a=" + a);

        int b = 5;
        int preIncrement = ++b;
        System.out.println("preIncrement=" + preIncrement + ",b=" + b);

        int c = 5;
        int postDecrement = c--;
        System.out.println("postDecrement=" + postDecrement + ",c=" + c);

        int d = 5;
        int preDecrement = --d;
        System.out.println("preDecrement=" + preDecrement + ",d=" + d);

        int count = 0;
        count++;
        ++count;
        count--;
        --count;
        System.out.println("standaloneCount=" + count);

        int[] numbers = {10, 20, 30};
        int index = 0;
        int selected = numbers[index++];
        System.out.println("arraySelection=" + selected + ",index=" + index);

        int n = 1;
        int combined = n++ + ++n;
        System.out.println("combined=" + combined + ",n=" + n);

        int overwritten = 5;
        overwritten = overwritten++;
        System.out.println("assignmentTrap=" + overwritten);

        byte tiny = 127;
        tiny++;
        System.out.println("byteOverflow=" + tiny);

        int maximum = Integer.MAX_VALUE;
        maximum++;
        System.out.println("intOverflow=" + maximum);

        try {
            Math.incrementExact(Integer.MAX_VALUE);
        } catch (ArithmeticException ex) {
            System.out.println("incrementExact=ArithmeticException");
        }

        Integer boxed = 4;
        boxed++;
        System.out.println("boxed=" + boxed);

        for (int i = 0; i < 3; i++) {
            System.out.println("loop=" + i);
        }
    }
}
~~~

Chạy trong thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 PrefixPostfixDemo.java
java PrefixPostfixDemo
~~~

**Expected stdout chính xác:**

~~~text
postIncrement=5,a=6
preIncrement=6,b=6
postDecrement=5,c=4
preDecrement=4,d=4
standaloneCount=0
arraySelection=10,index=1
combined=4,n=3
assignmentTrap=5
byteOverflow=-128
intOverflow=-2147483648
incrementExact=ArithmeticException
boxed=5
loop=0
loop=1
loop=2
~~~

Các dòng `postIncrement` và `preIncrement` cho thấy biến đều tăng nhưng **giá trị được trả ra** khác nhau; `combined` xác nhận thứ tự cập nhật, `assignmentTrap` cảnh báo kết hợp hậu tố với gán ngoài, còn `byteOverflow` và `intOverflow` minh họa tràn không tự ném ngoại lệ.

## 8. Bài tập tự luyện

**Bài 1.** Cho `int x = 8; int y = x++;`. Xác định `x` và `y`. So sánh với `int x = 8; int y = ++x;`.

**Bài 2.** Cho `int x = 8; int y = x--;` và `int x = 8; int y = --x;`. Hai trường hợp khác nhau ở giá trị nào?

**Bài 3.** Dự đoán `int i = 1; int result = i++ + ++i;`. Viết lại thành nhiều câu lệnh mà không thay đổi kết quả.

**Bài 4.** Với `int[] arr = {4, 5, 6}; int i = 0;`, giải thích `arr[i++]` trả phần tử nào và `i` sau đó bằng bao nhiêu.

**Bài 5.** Vì sao `int x = 5; x = x++;` vẫn để `x` bằng 5? Viết cách tăng đơn giản hơn.

**Bài 6.** Kiểm tra `byte x = 127; x++;` và nêu cách dùng `Math.incrementExact` khi chương trình cần phát hiện tràn `int`.

### Ghi nhớ

**Tiền tố `++x`/`--x` trả giá trị sau khi cập nhật; hậu tố `x++`/`x--` trả giá trị trước khi cập nhật.** Cả hai cùng thay đổi biến trong biểu thức hiện tại. Nếu không dùng giá trị biểu thức, chọn cách dễ đọc. Tránh trộn nhiều lần tăng giảm với phép gán và tác dụng phụ khác; cân nhắc `Math.incrementExact`/`decrementExact` khi yêu cầu phát hiện tràn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="assignment-and-unary-operators-006">Phân biệt giá trị biểu thức với giá trị biến sau ++ hoặc --</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt giá trị biểu thức với giá trị biến sau `++` hoặc `--`

## 1. Luôn theo dõi hai đại lượng

Mỗi lần gặp tiền tố/hậu tố, hãy ghi riêng **giá trị biểu thức cung cấp cho phép tính bên ngoài** và **trạng thái của biến sau cập nhật**. Bốn quy tắc nền tảng là: `x++`/`x--` cung cấp giá trị **cũ** rồi cập nhật biến; `++x`/`--x` cập nhật biến và cung cấp giá trị **mới**. Việc cập nhật đã xảy ra ngay trong lần đánh giá này.

Với hai ví dụ độc lập:

~~~java
int x = 5;
int a = x++; // a=5, x=6

int y = 5;
int b = ++y; // b=6, y=6
~~~

| Biểu thức | Giá trị đưa vào phép gán ngoài | Trạng thái biến sau đó |
|---|---:|---:|
| `x++` với `x=5` | 5 | `x=6` |
| `++x` với `x=5` | 6 | `x=6` |
| `x--` với `x=5` | 5 | `x=4` |
| `--x` với `x=5` | 4 | `x=4` |

Đây là **hai thông tin khác nhau**, dù một số ví dụ có cùng số ở cả hai cột. Bài “Tăng giảm tiền tố và hậu tố” giải thích cú pháp và cách dùng trong vòng lặp; bài này tập trung vào **phương pháp truy vết kết quả khi biểu thức được sử dụng**.

## 2. Bảng truy vết: đọc giá trị ở đúng thời điểm

Với mỗi dòng Java, có thể làm theo trình tự:

1. Viết trạng thái biến **trước biểu thức**.
2. Xác định vị trí của `++`/`--` và ghi **giá trị mà toán tử cung cấp**.
3. Cập nhật trạng thái biến ngay khi toán tử được đánh giá.
4. Đọc các toán hạng tiếp theo theo thứ tự đánh giá của Java, tính kết quả ngoài.
5. Ghi **cả kết quả và trạng thái cuối**; không thay một giá trị bằng giá trị kia.

Ví dụ có **một** lần hậu tố và một lần đọc trạng thái sau:

~~~java
int x = 5;
int result = x++ * 10 + x;
~~~

| Bước | Giá trị dùng cho phép tính | `x` đang giữ |
|---|---:|---:|
| Ban đầu | — | 5 |
| `x++` | 5 | 6 |
| `5 * 10` | 50 | 6 |
| Đọc `x` ở toán hạng cuối | 6 | 6 |
| Cộng | `result = 56` | 6 |

Với tiền tố:

~~~java
int y = 5;
int result = ++y * 10 + y;
// result=66, y=6
~~~

`++y` cung cấp 6 và biến đã bằng 6 khi toán hạng sau đọc. Kết quả cuối **không thể suy ra chỉ từ số lần xuất hiện của ký hiệu `++`** mà phải theo dõi thời điểm đọc.

## 3. Biến nhận kết quả và biến bị cập nhật có thể khác nhau

~~~java
int score = 10;
int snapshot = score++;
int current = score;
System.out.println(snapshot); // 10
System.out.println(current);  // 11
~~~

`snapshot` lưu **giá trị biểu thức hậu tố**; `current` đọc **trạng thái sau hậu tố**. Nếu ghi log “`score++` trả 11” chỉ vì log sau đó thấy `score=11`, bạn đã nhập nhằng hai thời điểm khác nhau.

Một bẫy khi biến nhận và biến bị cập nhật là cùng một biến:

~~~java
int n = 5;
n = n++;
System.out.println(n); // 5
~~~

`n++` trước hết làm `n` thành 6 nhưng **trả 5**; phép gán ngoài lưu lại 5. Vì vậy dòng này không dùng để tăng biến. Khi chỉ muốn cập nhật trạng thái, viết riêng `n++;`. Những chuỗi có **nhiều** lần tăng giảm trong một biểu thức được phân tích và viết lại ở bài cuối chương.

## 4. Phân biệt tăng chỉ số với tăng phần tử mảng

Hai dòng sau có toán tử đặt tại **hai vị trí khác nhau**:

~~~java
int[] items = {7, 9};
int index = 0;
int picked = items[index++];
// picked=7, index=1, items vẫn là {7,9}
~~~

`index++` trả chỉ số cũ 0, sau đó tăng **index**; không thay đổi giá trị của `items[0]`.

~~~java
int[] items = {7, 9};
int previous = items[0]++;
// previous=7, items[0]=8, items[1]=9
~~~

Ở đây `++` gắn với **biến phần tử mảng `items[0]`**, không phải chỉ số. Khi debug, ghi rõ “biến nào bị thay đổi” cùng với “biểu thức trả gì”.

## 5. Giá trị cũ tại biên số và với wrapper

~~~java
int max = Integer.MAX_VALUE;
int old = max++;
// old=2147483647, max=-2147483648
~~~

Hậu tố cung cấp giá trị cũ ngay cả khi cập nhật làm `int` tràn. Java không tự ném ngoại lệ overflow ở phép `++` này. Nếu cần phát hiện tràn, cân nhắc `Math.incrementExact` và thiết kế riêng cách trả giá trị cũ.

Với `Integer nullable`, `++` cần unboxing; `Integer absent = null; absent++;` sẽ ném `NullPointerException`. Bảng truy vết chỉ kết thúc bình thường khi từng bước đánh giá thành công.

## 6. Chương trình Java 17+ để đối chiếu bảng

Lưu thành `IncrementExpressionValueDemo.java`:

~~~java
public final class IncrementExpressionValueDemo {
    public static void main(String[] args) {
        int post = 5;
        int postValue = post++;
        System.out.println("post=" + postValue + "," + post);

        int pre = 5;
        int preValue = ++pre;
        System.out.println("pre=" + preValue + "," + pre);

        int x = 5;
        int withPost = x++ * 10 + x;
        System.out.println("tracePost=" + withPost + "," + x);

        int y = 5;
        int withPre = ++y * 10 + y;
        System.out.println("tracePre=" + withPre + "," + y);

        int overwritten = 5;
        overwritten = overwritten++;
        System.out.println("overwritten=" + overwritten);

        int[] items = {7, 9};
        int index = 0;
        int picked = items[index++];
        System.out.println("index=" + picked + "," + index + "," + items[0]);

        int previous = items[0]++;
        System.out.println("element=" + previous + "," + items[0]);

        int max = Integer.MAX_VALUE;
        int old = max++;
        System.out.println("overflow=" + old + "," + max);
    }
}
~~~

~~~bash
javac --release 17 -encoding UTF-8 IncrementExpressionValueDemo.java
java IncrementExpressionValueDemo
~~~

**Expected stdout:**

~~~text
post=5,6
pre=6,6
tracePost=56,6
tracePre=66,6
overwritten=5
index=7,1,7
element=7,8
overflow=2147483647,-2147483648
~~~

Các dòng `tracePost`/`tracePre` kiểm tra thời điểm toán hạng cuối đọc biến; `index`/`element` cho thấy vị trí tăng khác nhau; `overwritten` phân biệt tác dụng phụ tạm thời với trạng thái cuối sau phép gán ngoài.

## 7. Bài tập có đáp án

**Bài A.** `int x = 3; int y = x-- * 10 + x;`. **Đáp án:** `y=32`, `x=2` vì `x--` cung cấp 3 rồi `x` mới là 2.

**Bài B.** `int x = 3; int y = --x * 10 + x;`. **Đáp án:** `y=22`, `x=2` vì `--x` cung cấp 2 và lần đọc sau vẫn là 2.

**Bài C.** `int[] data = {4,5}; int i=0; int a=data[i++];`. **Đáp án:** `a=4`, `i=1` và mảng không đổi.

**Bài D.** `int[] data = {4,5}; int a=data[0]++;`. **Đáp án:** `a=4` và `data[0]=5`.

**Bài E.** Giải thích `int n=9; n=n++;`. **Đáp án:** `n` cuối cùng vẫn là 9 vì kết quả cũ của hậu tố bị phép gán ngoài ghi trở lại.

## 8. Tự kiểm tra trước khi chuyển bài

- [ ] Phân biệt được **giá trị biểu thức** và **giá trị đang lưu**.
- [ ] Biết hậu tố cập nhật ngay, không chờ lệnh kế tiếp.
- [ ] Vẽ được bảng truy vết của một phép tăng/giảm nằm trong phép tính.
- [ ] Chỉ ra biến nào đổi khi `++` nằm ở chỉ số hoặc phần tử mảng.
- [ ] Giải thích được phép gán ngoài ghi đè `x++` mà không gọi Java là undefined.
- [ ] Biết giá trị cũ vẫn được trả khi cập nhật bị overflow.

**Ghi nhớ:** Để hiểu `++`/`--` bên trong biểu thức, hãy trả lời **hai câu riêng**: *toán tử vừa cung cấp giá trị gì* và *biến nào đang giữ giá trị gì*. Với biểu thức có nhiều lần cập nhật trên cùng biến, cần thêm bảng thứ tự đánh giá và cân nhắc tách các side effect thành từng câu lệnh.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="assignment-and-unary-operators-007">Toán tử dấu +, dấu - và phủ định !</a>

<details>
<summary>Xem chi tiết</summary>

# Toán tử dấu `+`, dấu `-` và phủ định `!` — Java 17+

Trong Java, `+`, `-` và `!` là **toán tử một ngôi** (*unary operators*) khi được đặt trước **một** toán hạng. Chúng cần được phân biệt với phép cộng/trừ hai ngôi.

~~~java
int number = 7;
int positive = +number;   // 7.
int negative = -number;   // -7.
boolean online = true;
boolean offline = !online; // false.
~~~

`+x` giữ giá trị số sau khi áp dụng **unary numeric promotion**; `-x` đổi dấu giá trị số; `!condition` đảo giá trị boolean. Chúng **không tự thay đổi biến nguồn** (khác với `++x`, `x++` hoặc `--x`).

## 1. Nhận biết ba toán tử

| Biểu thức | Tên | Kiểu toán hạng hợp lệ | Kết quả |
| --- | --- | --- | --- |
| `+x` | Dấu cộng một ngôi | Primitive số hoặc wrapper số được unbox | Giá trị số sau nâng kiểu |
| `-x` | Dấu trừ một ngôi | Primitive số hoặc wrapper số được unbox | Giá trị đổi dấu sau nâng kiểu |
| `!x` | Phủ định logic | `boolean` hoặc `Boolean` được unbox | Giá trị `boolean` ngược lại |

Ví dụ, `+5` không phải phép cộng hai toán hạng, còn `5 + 2` mới là phép cộng hai ngôi. Tương tự, `-5` là dấu trừ một ngôi nhưng `5 - 2` là phép trừ hai ngôi. `!` không dùng được với `int`, `double`, `String` hoặc đối tượng bất kỳ nếu đối tượng đó không được unbox thành `boolean`.

## 2. Chương trình Java 17+ độc lập

Tạo file UTF-8 **`UnarySignsAndNotDemo.java`**:

~~~java
public class UnarySignsAndNotDemo {
    static String typeOf(int unused) {
        return "int";
    }

    static String typeOf(long unused) {
        return "long";
    }

    static String typeOf(double unused) {
        return "double";
    }

    public static void main(String[] args) {
        int number = 7;
        System.out.println("unaryPlus=" + (+number));
        System.out.println("unaryMinus=" + (-number));
        System.out.println("binaryPlus=" + (10 + number));
        System.out.println("binaryMinus=" + (10 - number));
        System.out.println("originalNumber=" + number);

        byte small = 5;
        short medium = 6;
        char letter = 'A';
        long large = 7L;
        double fraction = 2.5;

        System.out.println("plusByteType=" + typeOf(+small));
        System.out.println("minusShortType=" + typeOf(-medium));
        System.out.println("plusCharType=" + typeOf(+letter));
        System.out.println("plusCharValue=" + (+letter));
        System.out.println("plusLongType=" + typeOf(+large));
        System.out.println("minusDoubleType=" + typeOf(-fraction));
        System.out.println("minusDoubleValue=" + (-fraction));

        Integer boxedNumber = 8;
        Boolean boxedFalse = Boolean.FALSE;
        System.out.println("boxedMinusType=" + typeOf(-boxedNumber));
        System.out.println("boxedMinusValue=" + (-boxedNumber));
        System.out.println("boxedNot=" + (!boxedFalse));

        boolean ready = true;
        boolean enabled = false;
        System.out.println("notReady=" + (!ready));
        System.out.println("doubleNot=" + (!!ready));
        System.out.println("groupedNot=" + (!(ready && enabled)));
        System.out.println("notComparison=" + (!(number > 10)));
        System.out.println("deMorgan="
                + ((!(ready && enabled))
                        == ((!ready) || (!enabled))));

        int minimum = Integer.MIN_VALUE;
        System.out.println("minNegatedInt=" + (-minimum));
        System.out.println("minNegatedAsLong=" + (-(long) minimum));
        try {
            Math.negateExact(minimum);
            System.out.println("unexpected=no-error");
        } catch (ArithmeticException ex) {
            System.out.println("negateExact=ArithmeticException");
        }

        double negativeZero = -0.0;
        System.out.println("negativeZero=" + negativeZero);
        System.out.println("zerosEqual=" + (negativeZero == +0.0));
        System.out.println("negativeZeroReciprocal=" + (1.0 / negativeZero));

        double notANumber = Double.NaN;
        System.out.println("notNaNEquality="
                + (!(notANumber == notANumber)));

        Integer missingNumber = null;
        try {
            int unused = -missingNumber;
            System.out.println("unexpectedNumber=" + unused);
        } catch (NullPointerException ex) {
            System.out.println("nullNumber=NullPointerException");
        }

        Boolean missingFlag = null;
        try {
            boolean unused = !missingFlag;
            System.out.println("unexpectedFlag=" + unused);
        } catch (NullPointerException ex) {
            System.out.println("nullBoolean=NullPointerException");
        }
    }
}
~~~

Chạy bằng JDK 17 trở lên, tại thư mục chứa file:

~~~bash
java -version
javac -version
javac -encoding UTF-8 UnarySignsAndNotDemo.java
java UnarySignsAndNotDemo
~~~

**Expected stdout chính xác** của lệnh `java UnarySignsAndNotDemo` (không bao gồm đầu ra phiên bản JDK):

~~~text
unaryPlus=7
unaryMinus=-7
binaryPlus=17
binaryMinus=3
originalNumber=7
plusByteType=int
minusShortType=int
plusCharType=int
plusCharValue=65
plusLongType=long
minusDoubleType=double
minusDoubleValue=-2.5
boxedMinusType=int
boxedMinusValue=-8
boxedNot=true
notReady=false
doubleNot=true
groupedNot=true
notComparison=true
deMorgan=true
minNegatedInt=-2147483648
minNegatedAsLong=2147483648
negateExact=ArithmeticException
negativeZero=-0.0
zerosEqual=true
negativeZeroReciprocal=-Infinity
notNaNEquality=true
nullNumber=NullPointerException
nullBoolean=NullPointerException
~~~

Các overload `typeOf(int)`, `typeOf(long)`, `typeOf(double)` biểu thị **kiểu tĩnh của biểu thức tại compile time**. Chúng giúp chứng minh `+small` có kiểu `int`, không còn kiểu `byte`. Code đã chủ động bắt các ngoại lệ được minh họa để giữ stdout ổn định.

## 3. Dấu cộng `+` một ngôi và toán tử `+` hai ngôi

~~~java
int n = 4;
int unchanged = +n; // 4.
int total = n + 5;   // 9.
~~~

- `+n` chỉ có một toán hạng số, không tự tăng `n`.
- `n + 5` có hai toán hạng và thực hiện phép cộng.
- `"count=" + n` là `+` hai ngôi **nối chuỗi** vì có toán hạng `String`.

`+` một ngôi **không** đọc chuỗi rồi phân tích thành số:

~~~java
// int invalid = +"42";  // Loi bien dich.
int parsed = Integer.parseInt("42"); // 42.
~~~

Ngay cả khi giá trị không đổi, unary `+` vẫn áp dụng phép nâng kiểu số. Điều này quan trọng với `byte`, `short` và `char`.

## 4. Nâng kiểu số một ngôi

Với dấu `+` và `-` một ngôi, Java áp dụng **unary numeric promotion**:

| Toán hạng ban đầu | Kiểu của `+x` hoặc `-x` |
| --- | --- |
| `byte`, `short`, `char` | `int` |
| `int` | `int` |
| `long` | `long` |
| `float` | `float` |
| `double` | `double` |
| `Byte`, `Short`, `Character` | Unbox rồi nâng lên `int` |
| `Integer`, `Long`, `Float`, `Double` | Unbox thành primitive tương ứng |

~~~java
byte b = 5;
int a = +b;  // 5, kieu int.
int c = -b;  // -5, kieu int.
// byte d = -b; // Loi: khong tu gan int vao byte.

char letter = 'A';
int codeUnit = +letter; // 65, kieu int.
~~~

`char` là đơn vị mã UTF-16, nên `+'A'` trả về `int` 65 chứ không phải một ký tự đã gắn dấu. Với wrapper số, quá trình **unboxing** có thể ném `NullPointerException`:

~~~java
Integer missing = null;
// int result = -missing; // Nem NullPointerException khi chay.
~~~

Hãy kiểm tra null nếu dữ liệu có thể thiếu.

## 5. Dấu trừ `-`: đổi dấu không phải lúc nào cũng an toàn

~~~java
int n = 12;
int opposite = -n; // -12; n van la 12.
~~~

Đổi dấu một số nguyên ở biên âm có thể **tràn** vì miền `int` bất đối xứng:

~~~java
int min = Integer.MIN_VALUE; // -2147483648.
int stillMin = -min;         // -2147483648 (tran int).
long correct = -(long) min; // 2147483648.
~~~

Giá trị toán học `2147483648` không biểu diễn được bằng `int`. Java không tự ném ngoại lệ ở phép đổi dấu nguyên thông thường. Nếu muốn **phát hiện tràn**, dùng `Math.negateExact(min)`, sẽ ném `ArithmeticException`. `Long.MIN_VALUE` có trường hợp tương tự.

Với số thực, dấu âm của **zero** được lưu lại:

~~~java
double negZero = -0.0;
System.out.println(negZero == 0.0); // true.
System.out.println(1.0 / negZero);  // -Infinity.
~~~

`+0.0` và `-0.0` bằng nhau theo primitive `==` nhưng khác bit dấu; dấu trừ một ngôi tạo `-0.0` từ `+0.0`, và dấu cộng một ngôi không xóa dấu âm của `-0.0`.

## 6. Phủ định `!` chỉ cho boolean

~~~java
boolean online = true;
boolean offline = !online; // false.

boolean ready = true;
boolean enabled = false;
boolean blocked = !(ready && enabled); // true.
~~~

| Biểu thức | Kết quả |
| --- | --- |
| `!true` | `false` |
| `!false` | `true` |
| `!!true` | `true` |
| `!(true && false)` | `true` |
| `!(true || false)` | `false` |

Đừng sử dụng `!` trên số nguyên như trong một số ngôn ngữ có truthiness:

~~~java
int count = 0;
// boolean wrong = !count; // Loi bien dich.
boolean empty = !(count > 0); // true.
~~~

`!` là **logical complement**; `~` mới là toán tử **bitwise complement** trên kiểu nguyên. Hai toán tử không thể thay thế cho nhau.

Với `Boolean`, Java tự **unbox** để áp dụng `!`, nên null có thể gây ngoại lệ:

~~~java
Boolean setting = null;
// boolean disabled = !setting; // Nem NullPointerException.
~~~

### Quy tắc De Morgan

Với biểu thức boolean `a` và `b`:

| Phủ định biểu thức ghép | Dạng biến đổi tương đương |
| --- | --- |
| `!(a && b)` | `!a || !b` |
| `!(a || b)` | `!a && !b` |

Trong trường hợp `a` và `b` có tác dụng phụ (gọi phương thức, thay đổi trạng thái), cần đánh giá ngữ nghĩa từng biểu thức khi refactor; với biến boolean thông thường, bảng giúp phủ định điều kiện đúng và dễ đọc.

## 7. Thứ tự ưu tiên và dấu ngoặc

Các toán tử một ngôi `+`, `-`, `!` có mức ưu tiên cao hơn các phép toán cộng/trừ hai ngôi và toán tử so sánh/logical:

~~~java
int a = 6;
int b = 2;
int first = -a + b;   // -4.
int second = -(a + b); // -8.

boolean smaller = !(a > b); // false.
~~~

Trong `-a + b`, phần `-a` là biểu thức con. Trong `-(a + b)`, dấu trừ áp dụng cho **toàn tổng**. Với `!` cũng phải phủ định **toàn điều kiện** khi cần:

~~~java
// boolean wrong = !a > b; // Sai kieu: !a yeu cau boolean.
boolean correct = !(a > b);
~~~

## 8. Bẫy thường gặp

| Nhầm lẫn | Điều đúng |
| --- | --- |
| “`+x` luôn giữ nguyên kiểu `x`.” | `byte`, `short`, `char` được nâng thành `int` |
| “`-x` thay đổi trực tiếp `x`.” | Không, trả một giá trị mới |
| “`+ "12"` biến chuỗi thành số.” | Sai; dùng parsing như `Integer.parseInt` |
| “`!0` là true.” | Không hợp lệ: `!` cần boolean |
| “`!` giống `~`.” | `!` đảo logic; `~` đảo bit |
| “`-Integer.MIN_VALUE` là số dương.” | Tràn `int`, kết quả vẫn bằng `MIN_VALUE` |
| “`Boolean` có thể dùng `!` kể cả khi null.” | Null gây `NullPointerException` khi unbox |
| “`-0.0` giống `+0.0` trong mọi phép toán.” | Cùng `==` nhưng khác dấu ở nghịch đảo và bit biểu diễn |

## 9. Tự kiểm tra

1. `int x = 6; int y = -x;`, `x` và `y` bằng bao nhiêu?  
   *Đáp án:* 6 và -6.
2. `-(3 + 2)` khác `-3 + 2` thế nào?  
   *Đáp án:* -5 và -1.
3. `byte b = 7; var n = -b;`, kiểu `n` là gì?  
   *Đáp án:* `int`.
4. `var code = +'A';`, kiểu và giá trị `code` là gì?  
   *Đáp án:* `int`, 65.
5. `!(true && false)` có giá trị nào?  
   *Đáp án:* `true`.
6. `Boolean flag = null; boolean x = !flag;` có chạy bình thường không?  
   *Đáp án:* Không; ném `NullPointerException`.
7. `-Integer.MIN_VALUE` và `-(long) Integer.MIN_VALUE` khác nhau ra sao?  
   *Đáp án:* Kết quả `int` là -2147483648, còn `long` là 2147483648.
8. `1.0 / -0.0` trả gì?  
   *Đáp án:* `-Infinity`.

## Checklist

- [ ] Phân biệt `+x`/`-x` một ngôi với `a+b`/`a-b` hai ngôi.
- [ ] Hiểu `+` một ngôi không đổi giá trị nhưng có thể nâng kiểu số.
- [ ] Hiểu `-` đổi dấu và có thể tràn ở giá trị nguyên nhỏ nhất.
- [ ] Chỉ dùng `!` cho boolean, không cho số nguyên hoặc String.
- [ ] Kiểm tra null trước khi unbox wrapper.
- [ ] Nhớ số thực có `+0.0` và `-0.0` với bit dấu khác nhau.
- [ ] Dùng dấu ngoặc để phủ định đúng biểu thức mong muốn.
- [ ] Dùng `Math.negateExact` nếu nghiệp vụ yêu cầu phát hiện overflow khi đổi dấu.

**Kết luận:** Unary `+` và `-` áp dụng trên **toán hạng số sau khi nâng kiểu**, trong khi unary `!` chỉ áp dụng cho **boolean**. Nắm rõ kiểu kết quả, phạm vi ưu tiên và các tình huống biên như null, overflow và zero có dấu sẽ giúp sử dụng chính xác ba toán tử này.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="assignment-and-unary-operators-008">Tránh nhiều phép tăng giảm trên cùng biến trong một biểu thức</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh nhiều phép tăng giảm trên cùng biến trong một biểu thức — Java 17+

## 1. Mục tiêu

Trong Java, toán tử `++` tăng biến số lên 1, `--` giảm biến số đi 1. Cả hai **vừa trả một giá trị biểu thức, vừa thay đổi biến**. Khi cùng một biến bị tăng/giảm và đọc nhiều lần trong một biểu thức, chương trình có thể có kết quả hợp lệ nhưng rất khó đọc và dễ sửa sai.

~~~java
int i = 1;
int result = i++ + ++i;
System.out.println(result); // 4
System.out.println(i);      // 3
~~~

**Java 17 xác định rõ kết quả này**, không phải “undefined behavior”. Tuy nhiên, người đọc phải theo dõi nhiều trạng thái trung gian của `i` trong cùng một dòng; vì thế **không nên dùng kiểu biểu thức này trong code nghiệp vụ**.

Sau bài, bạn cần: phân biệt hậu tố/tiền tố; dự đoán thứ tự đánh giá trong Java; nhận diện bẫy với gán, lời gọi phương thức, mảng và điều kiện; viết lại biểu thức có nhiều side effect thành các bước dễ kiểm tra.

## 2. Nhắc nhanh ngữ nghĩa cần dùng để nhận diện mã khó đọc

Hậu tố `i++`/`i--` cung cấp **giá trị cũ** và cập nhật biến; tiền tố `++i`/`--i` cung cấp **giá trị mới** sau cập nhật. Tất cả diễn ra trong lần đánh giá hiện tại. Đây là phần kiến thức nền của bài “Tăng giảm tiền tố và hậu tố”; kỹ năng theo dõi riêng hai giá trị cũng đã được luyện ở bài ngay trước.

Bài này tập trung vào **vấn đề phát sinh khi một biến bị cập nhật nhiều lần trong cùng biểu thức**: thứ tự đánh giá, các đối số phương thức, truy cập mảng, short-circuit, exception và cách refactor mà không đổi hành vi. Riêng `i++` trong phần cập nhật vòng `for` hoặc câu lệnh `i++;` đơn giản vẫn thường rõ nghĩa, không cần né tránh.

## 3. Vì sao `i++ + ++i` cho kết quả 4?

~~~java
int i = 1;
int result = i++ + ++i;
~~~

Java đánh giá các toán hạng của `+` **từ trái sang phải**:

| Bước | Thao tác | Giá trị của toán hạng | `i` sau bước |
|---|---|---:|---:|
| 1 | `i++` | 1 (giá trị cũ) | 2 |
| 2 | `++i` | 3 (giá trị mới) | 3 |
| 3 | Cộng `1 + 3` | 4 | 3 |

Vì vậy `result == 4` và `i == 3`.

Một ví dụ cũng dễ nhầm:

~~~java
int j = 1;
int sum = j++ + j++;
System.out.println(sum); // 3 = 1 + 2
System.out.println(j);   // 3
~~~

**Không kết luận đây là kết quả không xác định.** Java quy định thứ tự đánh giá; rủi ro thực tế là phép tính nhiều tác dụng phụ **khó dự đoán bằng mắt, khó review và khó bảo trì**. Đặc biệt, đừng áp dụng trực giác từ ngôn ngữ khác để giải thích Java.

### Thứ tự ưu tiên không đồng nghĩa thứ tự thực hiện mọi toán hạng

Ví dụ `a++ + b++ * c++`: phép nhân được **nhóm** trước phép cộng theo precedence, nhưng Java vẫn đánh giá các toán hạng theo quy tắc xác định từ trái sang phải. Để tránh phải suy luận dài, hãy **tách các lần tăng thành câu lệnh rõ ràng**.

## 4. Bẫy `i = i++`: tăng rồi gán đè bằng giá trị cũ

~~~java
int i = 5;
i = i++;
System.out.println(i); // 5
~~~

Diễn biến:

1. Biểu thức hậu tố `i++` trả về **5**, đồng thời tăng `i` lên **6**.
2. Phép gán `i = ...` bên ngoài lại gán **5** trở lại `i`.
3. Cuối cùng `i` vẫn bằng **5**.

So sánh:

~~~java
int a = 5;
a++;           // a=6

int b = 5;
b = ++b;       // b=6, nhưng cách viết dư thừa
~~~

**Cách đúng:** nếu mục đích là tăng, hãy viết `i++;` hoặc `i += 1;`. Nếu mục đích là gán, hãy tính giá trị mong muốn rồi `i = value;`. Đừng kết hợp hai hành động lên cùng biến mà không cần thiết.

## 5. Đọc lại biến sau khi tăng trong cùng biểu thức

~~~java
int i = 3;
int value = i++ + i;

System.out.println(value); // 7 = 3 + 4
System.out.println(i);     // 4
~~~

Vế trái cung cấp giá trị cũ `3` rồi `i` thành `4`; vế phải đọc `4`.

Mặc dù đoạn code có quy tắc rõ, có thể viết:

~~~java
int i = 3;
int oldValue = i;
i++;
int currentValue = i;
int value = oldValue + currentValue;
~~~

Phiên bản tách bước dài hơn một chút nhưng **thể hiện chính xác biến nào là giá trị cũ và biến nào là giá trị mới**. Nếu nghiệp vụ không yêu cầu cả hai, hãy đơn giản hóa thêm.

## 6. Nhiều `++` trong đối số phương thức

Java đánh giá các **đối số của lời gọi phương thức từ trái sang phải**, trước khi thực hiện thân phương thức:

~~~java
static void show(int left, int right) {
    System.out.println(left + "," + right);
}

// Trong main:
int i = 1;
show(i++, ++i); // In "1,3"; i kết thúc bằng 3
~~~

Thứ tự:

1. Đối số đầu `i++`: truyền `1`, `i` thành 2.
2. Đối số hai `++i`: `i` thành 3, truyền `3`.
3. Method nhận `(1, 3)`.

**Nên viết:**

~~~java
int i = 1;
int first = i;
i++;
i++;
int second = i;
show(first, second);
~~~

Điều này giữ nguyên giá trị truyền vào và thứ tự thay đổi trạng thái trong ví dụ, đồng thời giúp người đọc không phải tính nhẩm thứ tự của các đối số.

Nếu method nhận nhiều biểu thức phức tạp chứa I/O hoặc sửa trạng thái, tách chúng thành biến tạm còn giúp xác định **thao tác nào được thực hiện trước**.

## 7. Chỉ số mảng thay đổi ở những vị trí nào?

~~~java
int[] items = {10, 20, 30};
int index = 0;
int total = items[index++] + items[index++];

System.out.println(total); // 30 = items[0] + items[1]
System.out.println(index); // 2
~~~

Với hai lần `index++`, Java lần lượt dùng chỉ số **0**, rồi **1**. Không nên làm người đọc nhầm rằng biểu thức đang đọc cùng một phần tử hai lần.

Phiên bản dễ bảo trì:

~~~java
int first = items[index];
index++;
int second = items[index];
index++;
int total = first + second;
~~~

Dạng `array[index++]` đơn lẻ có thể phù hợp trong thuật toán tuần tự rõ ràng; **nguy cơ tăng mạnh khi dùng cùng biến chỉ số nhiều lần trong một biểu thức**.

Đặc biệt:

~~~java
int[] values = {10, 20};
int index = 0;
values[index++]++;  // values[0] thành 11, index thành 1
~~~

Ở đây có **hai tác dụng phụ khác nhau**: tăng `index` để chọn phần tử và tăng chính **phần tử mảng**. Nếu cần giải thích logic cho đồng đội, cách tách bước thường sáng sủa hơn:

~~~java
int selectedIndex = index;
index++;
values[selectedIndex]++;
~~~

## 8. Short-circuit khiến một phép tăng có thể không chạy

Với `&&`, khi vế trái là `false`, Java **không đánh giá** vế phải. Với `||`, khi vế trái là `true`, Java cũng bỏ qua vế phải:

~~~java
int i = 0;
boolean result = false && (i++ > 0);

System.out.println(result); // false
System.out.println(i);      // 0: i++ không chạy
~~~

Nếu điều kiện đầu true:

~~~java
int i = 0;
boolean result = true && (i++ == 0);

System.out.println(result); // true
System.out.println(i);      // 1
~~~

Phân biệt với `&` trên `boolean`: `&` **đánh giá cả hai toán hạng**, nên:

~~~java
int i = 0;
boolean result = false & (i++ > 0);

System.out.println(result); // false
System.out.println(i);      // 1
~~~

| Toán tử | Vế phải có thể bị bỏ qua? | Tác động đến `i++` trong vế phải |
|---|---|---|
| `&&` | Có, khi vế trái false | Có thể không tăng |
| `||` | Có, khi vế trái true | Có thể không tăng |
| `&` trên boolean | Không | Luôn đánh giá vế phải khi tới biểu thức |
| `|` trên boolean | Không | Luôn đánh giá vế phải khi tới biểu thức |

**Khuyến nghị:** điều kiện boolean nên diễn đạt **điều kiện**, còn cập nhật trạng thái nên ở câu lệnh riêng, trừ những trường hợp thuật toán thực sự yêu cầu side effect và đã được diễn giải rõ.

## 9. Exception có thể để lại trạng thái đã thay đổi một phần

~~~java
int count = 0;
int divisor = 0;

try {
    int answer = count++ + 10 / divisor;
    System.out.println(answer);
} catch (ArithmeticException ex) {
    System.out.println(count); // 1
}
~~~

`count++` đã được đánh giá, nên `count` tăng từ 0 lên 1 **trước khi** phép chia nguyên cho 0 ném `ArithmeticException`. Bắt ngoại lệ không tự hoàn tác phép tăng.

Nếu toàn bộ thao tác phải hoặc **cùng thành công hoặc cùng thất bại**, hãy **validate trước khi thay đổi trạng thái** hoặc dùng cơ chế transaction/rollback theo yêu cầu; dấu ngoặc và `try/catch` không tạo ra tính nguyên tử.

## 10. Dùng `++`/`--` đúng chỗ; không né quá mức

**Có thể dùng** khi ý định cập nhật hiển nhiên:

~~~java
int count = 0;
count++;
count--;
~~~

Vòng lặp đơn giản:

~~~java
for (int i = 0; i < 3; i++) {
    System.out.println(i);
}
~~~

Tuy nhiên, tránh:

~~~java
// Khó đọc: nhiều lần tác động vào cùng biến trong một biểu thức
// int result = i++ + ++i - i--;

// Khó audit: vừa sửa biến vừa tạo đối số
// send(i++, ++i);
~~~

Dấu ngoặc như `(i++) + (++i)` có thể làm rõ **cách nhóm** nhưng **không loại bỏ tác dụng phụ**. Việc thêm ngoặc không giải quyết vấn đề “có quá nhiều thay đổi trạng thái trong một biểu thức”.

## 11. Cách refactor mà không đổi hành vi

Ví dụ trước:

~~~java
int i = 1;
int result = i++ + ++i;
~~~

Sau:

~~~java
int i = 1;

int left = i;  // Lấy giá trị mà i++ sẽ trả
i++;          // Thực hiện tăng hậu tố

i++;          // Thực hiện tăng của ++i
int right = i; // Lấy giá trị mới

int result = left + right;
~~~

Hai phiên bản đều cho `result = 4`, `i = 3`. Phiên bản sau cho phép đặt breakpoint giữa các lần cập nhật.

Khi thay code thật, cần giữ các bất biến:

1. **Giữ thứ tự đánh giá** các method/toán hạng có side effect.
2. **Giữ số lần cập nhật** nếu chưa chủ động thay đổi thuật toán.
3. Phân biệt **giá trị cũ** và **giá trị mới** của tiền tố/hậu tố.
4. Không tự dời câu lệnh qua các nhánh `&&`, `||`, `?:`, `if` hoặc vị trí có thể ném exception.
5. Viết test kiểm tra **kết quả biểu thức lẫn trạng thái biến sau biểu thức**.

Nếu nhiều lần tăng giảm không thực sự cần thiết, phương án tốt nhất là **giảm số side effect** chứ không chỉ sao chép chúng sang nhiều dòng.

## 12. Một số bẫy kỹ thuật liên quan

| Trường hợp | Nguy cơ | Hướng xử lý |
|---|---|---|
| `i++ + ++i` | Khó theo dõi giá trị cũ/mới | Tách lần đọc và lần tăng |
| `i++ + i++` | Đọc và cập nhật cùng biến hai lần | Tách biến trung gian |
| `i = i++` | Gán đè giá trị sau tăng | Chỉ dùng `i++` nếu muốn tăng |
| `show(i++, ++i)` | Đối số thứ hai đọc trạng thái đã đổi | Tính đối số từng bước |
| `array[i++] + array[i++]` | Khó kiểm soát chỉ số/giới hạn mảng | Tách chỉ số, kiểm tra miền |
| `false && (i++ > 0)` | `i++` không chạy | Đừng dựa vào side effect bị điều kiện chi phối |
| `false & (i++ > 0)` | `i++` vẫn chạy | Hiểu `&` không short-circuit |
| `byte b = 127; b++;` | Overflow/narrowing khiến b thành -128 | Chọn kiểu đủ rộng hoặc kiểm tra miền |
| `Integer n = null; n++;` | Unboxing null gây `NullPointerException` | Kiểm tra null hoặc dùng primitive |
| `final int n = 1; n++;` | Không được gán lại biến `final` | Thiết kế biến thay đổi riêng |
| `sharedCount++` trong nhiều thread | Đọc–tính–ghi không nguyên tử | Đồng bộ hoặc `AtomicInteger` |

**Overflow:** `int i = Integer.MAX_VALUE; i++;` cho `Integer.MIN_VALUE` theo quy tắc Java, không ném exception mặc định. Nếu nghiệp vụ không chấp nhận tràn, có thể dùng `Math.incrementExact(i)` để kiểm tra.

**Wrapper:** `Integer n = 1; n++;` thực hiện unboxing, cộng rồi boxing/gán lại tham chiếu; không sửa nội dung của object `Integer` bất biến.

**Đa luồng:** Java có thứ tự đánh giá rõ trong một thread, nhưng `i++` **không tự nguyên tử** khi nhiều thread cùng cập nhật. Đây là vấn đề đồng bộ độc lập với việc biểu thức dài hay ngắn.

## 13. Chương trình Java 17 chạy độc lập

Tạo file đúng tên **`IncrementDecrementClarityDemo.java`**:

~~~java
public final class IncrementDecrementClarityDemo {
    private IncrementDecrementClarityDemo() {}

    static void show(int left, int right) {
        System.out.println("show=" + left + "," + right);
    }

    public static void main(String[] args) {
        int x = 4;
        int post = x++;
        int pre = ++x;
        System.out.println("basic: post=" + post
                + ", pre=" + pre + ", x=" + x);

        int a = 1;
        int mixed = a++ + ++a;
        System.out.println("mixed: value=" + mixed + ", a=" + a);

        int b = 1;
        int twicePost = b++ + b++;
        System.out.println("twice post: value=" + twicePost + ", b=" + b);

        int c = 5;
        c = c++;
        System.out.println("self assign: c=" + c);

        int d = 5;
        d = ++d;
        System.out.println("prefix assign: d=" + d);

        int e = 3;
        int readAfterPost = e++ + e;
        System.out.println("read after post: value=" + readAfterPost
                + ", e=" + e);

        int[] items = {10, 20, 30};
        int index = 0;
        int sum = items[index++] + items[index++];
        System.out.println("array: sum=" + sum + ", index=" + index);

        int[] values = {10, 20};
        int nextIndex = 0;
        values[nextIndex++]++;
        System.out.println("array update: first=" + values[0]
                + ", index=" + nextIndex);

        int m = 1;
        show(m++, ++m);
        System.out.println("after args: m=" + m);

        int j = 0;
        boolean skipped = false && (j++ > 0);
        System.out.println("short circuit: result=" + skipped
                + ", j=" + j);

        boolean evaluated = true && (j++ == 0);
        System.out.println("evaluated: result=" + evaluated
                + ", j=" + j);

        boolean eager = false & (j++ == 1);
        System.out.println("eager &: result=" + eager + ", j=" + j);

        int k = 0;
        int zero = 0;
        try {
            int result = k++ + 10 / zero;
            System.out.println(result);
        } catch (ArithmeticException ex) {
            System.out.println("exception: k=" + k);
        }

        int overflow = Integer.MAX_VALUE;
        overflow++;
        System.out.println("overflow: " + overflow);

        int clear = 1;
        int left = clear;
        clear++;
        clear++;
        int right = clear;
        int separated = left + right;
        System.out.println("separated: value=" + separated
                + ", final=" + clear);
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên; terminal mở ở thư mục chứa file. Không dùng thư viện bên ngoài JDK.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 IncrementDecrementClarityDemo.java
java -cp . IncrementDecrementClarityDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\IncrementDecrementClarityDemo.java
java -cp . IncrementDecrementClarityDemo
~~~

**Exact expected stdout:**

~~~text
basic: post=4, pre=6, x=6
mixed: value=4, a=3
twice post: value=3, b=3
self assign: c=5
prefix assign: d=6
read after post: value=7, e=4
array: sum=30, index=2
array update: first=11, index=1
show=1,3
after args: m=3
short circuit: result=false, j=0
evaluated: result=true, j=1
eager &: result=false, j=2
exception: k=1
overflow: -2147483648
separated: value=4, final=3
~~~

### Đọc kết quả quan trọng

- `post=4` và `pre=6` chứng minh hậu tố dùng giá trị cũ, tiền tố dùng giá trị mới.
- `mixed=4`, `a=3`: Java đánh giá từ trái sang phải, không có hành vi không xác định.
- `twice post=3`: hai lần tăng nối tiếp lấy các giá trị 1 và 2.
- `c = c++` ghi đè việc tăng; `d = ++d` cho 6 nhưng là cách viết dư thừa.
- `array` và `show`: chỉ số mảng/đối số lần sau nhìn thấy trạng thái đã cập nhật.
- `false && ...` bỏ qua `j++`; `false & ...` vẫn thực hiện `j++`.
- Phép chia cho 0 xảy ra sau `k++`; bắt exception không hoàn tác `k`.
- Dòng `separated` là phương án tách bước dễ đọc nhưng giữ cùng kết quả và trạng thái như `a++ + ++a`.

## 14. Bài tập có đáp án

### Bài A — hai lần hậu tố

~~~java
int i = 2;
int sum = i++ + i++;
System.out.println(sum);
System.out.println(i);
~~~

**Đáp án:**

~~~text
5
4
~~~

Các toán hạng trả lần lượt 2 và 3, còn i tăng lên 4.

### Bài B — trộn giảm hậu tố và tiền tố

~~~java
int n = 5;
int value = n-- + --n;
System.out.println(value);
System.out.println(n);
~~~

**Đáp án:**

~~~text
8
3
~~~

`n--` trả 5 rồi n thành 4; `--n` giảm n thành 3 rồi trả 3.

### Bài C — gán chồng lên tăng

~~~java
int x = 9;
x = x++;
System.out.println(x);
~~~

**Đáp án:** `9`. Phép gán ngoài ghi đè giá trị mới bằng giá trị cũ của `x++`.

### Bài D — chỉ số mảng

~~~java
int[] a = {4, 8, 12};
int i = 0;
int sum = a[i++] + a[i++];
System.out.println(sum);
System.out.println(i);
~~~

**Đáp án:**

~~~text
12
2
~~~

### Bài E — short-circuit

~~~java
int n = 0;
boolean result = true || (n++ > 0);
System.out.println(result);
System.out.println(n);
~~~

**Đáp án:**

~~~text
true
0
~~~

Vế trái `true` khiến `||` bỏ qua toàn bộ vế phải.

### Bài F — viết lại để tránh nhiều side effect

Cho:

~~~java
int i = 1;
int result = i++ + ++i;
~~~

**Một đáp án giữ đúng ngữ nghĩa:**

~~~java
int i = 1;
int left = i;
i++;
i++;
int right = i;
int result = left + right;
~~~

Sau cùng `result = 4` và `i = 3`. Nếu không thật cần tăng hai lần, hãy thiết kế lại công thức thay vì giữ một chuỗi side effect vô ích.

## 15. Checklist và kết luận

- [ ] Biết `i++` trả giá trị cũ, `++i` trả giá trị mới; `--` có quy tắc tương tự.
- [ ] Phân biệt **giá trị biểu thức** với **trạng thái sau side effect**.
- [ ] Biết Java 17 có thứ tự đánh giá xác định trong một thread.
- [ ] Tự giải thích được `i++ + ++i` và `i++ + i++` mà không gọi chúng là undefined.
- [ ] Nhận ra `i = i++` là một bẫy gán đè.
- [ ] Không lồng nhiều lần cập nhật cùng biến vào phép tính, đối số hay truy cập mảng khi không cần.
- [ ] Biết `&&`, `||` có thể bỏ qua phép tăng ở vế phải.
- [ ] Biết tác dụng phụ đã xảy ra không được rollback tự động khi exception phát sinh.
- [ ] Biết `++` có thể overflow hoặc gây NPE với wrapper nullable.
- [ ] Không coi `++` là atomic khi nhiều thread cùng ghi.
- [ ] Biết tách biến tạm, giữ thứ tự thao tác và kiểm thử cả kết quả lẫn trạng thái.
- [ ] Chạy `IncrementDecrementClarityDemo.java` bằng `javac`/`java` với JDK 17+, đối chiếu stdout chính xác.

**Kết luận:** Nhiều phép `++`/`--` trên cùng biến trong một biểu thức **không làm Java mất tính xác định**, nhưng khiến người đọc phải mô phỏng nhiều trạng thái giữa chừng, dễ dẫn đến bug và refactor sai. Hãy dành `++`/`--` cho các thao tác cập nhật rõ ràng, **tách side effect ra khỏi phép tính** khi có nhiều lần đọc/ghi cùng biến, và dùng test để xác nhận cả giá trị tính được lẫn trạng thái cuối.

</details>

- [Quay lại đầu trang](#back-to-top)
