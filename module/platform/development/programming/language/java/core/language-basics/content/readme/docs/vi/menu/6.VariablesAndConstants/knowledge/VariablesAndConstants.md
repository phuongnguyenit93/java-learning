<a id="back-to-top"></a>

# Biến và hằng

## Menu
- [Khai báo biến, khởi tạo và phép gán](#variables-and-constants-001)
- [Kiểu khai báo quyết định giá trị biến có thể giữ](#variables-and-constants-002)
- [Phân biệt biến cục bộ, tham số và trường](#variables-and-constants-003)
- [Yêu cầu gán giá trị trước khi đọc biến cục bộ](#variables-and-constants-004)
- [Giá trị mặc định của trường và phần tử mảng](#variables-and-constants-005)
- [Khai báo nhiều biến và những cách viết dễ gây nhầm](#variables-and-constants-006)
- [final trên biến và quy tắc chỉ gán một lần](#variables-and-constants-007)
- [Hằng tại thời điểm biên dịch và hằng static final](#variables-and-constants-008)
- [Quy ước đặt tên biến, tham số và hằng](#variables-and-constants-009)
- [Tham chiếu final không đổi nhưng đối tượng vẫn có thể thay đổi](#variables-and-constants-010)

## <a id="variables-and-constants-001">Khai báo biến, khởi tạo và phép gán</a>

<details>
<summary>Xem chi tiết</summary>

# Khai báo biến, khởi tạo và phép gán trong Java

## 1. Ba thao tác khác nhau

**Biến (variable)** là một vị trí lưu trữ có tên và có kiểu. Trong Java 17+, kiểu của biến được kiểm tra lúc biên dịch. Học cách sử dụng biến bắt đầu từ ba thao tác:

~~~java
int quantity;       // (1) Khai báo: tên quantity có kiểu int
quantity = 3;       // (2) Gán giá trị đầu tiên cho biến
quantity = 4;       // (3) Gán lại: thay giá trị đang lưu
~~~

Bạn cũng có thể **khai báo và khởi tạo trong cùng một câu lệnh**:

~~~java
int quantity = 3;
~~~

| Thao tác | Ví dụ | Điều xảy ra |
|---|---|---|
| Khai báo (*declaration*) | `int count;` | Giới thiệu biến `count` với kiểu `int` |
| Khởi tạo cùng khai báo (*initialization*) | `int count = 3;` | Tạo biến và cấp ngay giá trị đầu |
| Gán sau khai báo (*assignment*) | `count = 3;` | Đưa giá trị vào biến đã khai báo |
| Gán lại (*reassignment*) | `count = 4;` | Thay giá trị cũ bằng giá trị mới |
| Đọc biến | `System.out.println(count);` | Sử dụng giá trị biến đang giữ |

**Điểm cốt lõi:** dấu `=` ở đây là **phép gán**, không phải dấu “bằng nhau” của toán học. `count = 4` yêu cầu Java lưu giá trị mới; còn `count == 4` là biểu thức **so sánh** trả về `boolean`.

## 2. Cú pháp khai báo và quy tắc kiểu

Khai báo biến cục bộ thông thường có dạng:

~~~java
Type variableName;
Type otherName = expression;
~~~

Ví dụ:

~~~java
int age = 20;
long population = 8_000_000_000L;
double temperature = 26.5;
boolean active = true;
char grade = 'A';
String city = "Ha Noi";
~~~

Ở đây `int`, `long`, `double`, `boolean`, `char` là các kiểu primitive; `String` là kiểu tham chiếu. Biểu thức ở vế phải phải có kiểu **có thể gán** cho biến theo quy tắc chuyển kiểu của Java.

Một khai báo cũng có thể khai báo nhiều biến cùng kiểu:

~~~java
int width = 3, height = 4;
int area = width * height;
~~~

Tuy hợp lệ, trong mã nghiệp vụ nên tách khai báo nếu nhiều biến có ý nghĩa khác nhau hoặc initializer phức tạp. Không thể dùng chung một khai báo để trộn kiểu tùy ý như `int a = 1, b = "hai";`.

### Tên và phạm vi biến

~~~java
int subtotal = 100;
if (subtotal > 0) {
    int discount = 10;
    System.out.println(subtotal - discount); // 90
}
// System.out.println(discount); // Lỗi: discount ngoài phạm vi
~~~

Biến cục bộ thường có phạm vi trong block chứa khai báo, tính từ nơi khai báo theo quy tắc của Java. **Khai báo một lần** rồi gán lại khi cần; không khai báo lại một biến cục bộ cùng tên ở vị trí bị cấm:

~~~java
int count = 1;
count = 2;       // Đúng: gán lại
// int count = 3; // Lỗi: trùng khai báo trong cùng block
~~~


## 3. Điều kiện để một phép đọc hợp lệ

Với **biến cục bộ**, khai báo xong chưa đủ để đọc: Java yêu cầu nó đã được gán chắc chắn trước lần đọc ấy (*definite assignment*).

~~~java
int score;
// System.out.println(score); // Lỗi: có thể chưa được gán
score = 80;
System.out.println(score); // 80
~~~

**Tham số** nhận giá trị từ lời gọi; **field và phần tử mảng** có giá trị mặc định theo kiểu. Những cơ chế này khác nhau, nên đừng suy rằng `int score;` trong phương thức tự nhận 0. Phần phân tích đường đi `if`, vòng lặp và `final` thuộc bài chuyên sâu về *definite assignment*.

## 4. Gán giá trị và đánh giá vế phải

Trong một phép gán đơn giản:

~~~java
int unitPrice = 120;
int quantity = 3;
int subtotal = unitPrice * quantity;
~~~

Java đánh giá biểu thức `unitPrice * quantity` rồi gán kết quả `360` vào `subtotal`.

Nếu sau đó:

~~~java
quantity = 4;
System.out.println(subtotal); // 360
subtotal = unitPrice * quantity;
System.out.println(subtotal); // 480
~~~

Thì `subtotal` **không tự cập nhật** khi `quantity` đổi. Biến Java giữ giá trị đã được tính ở thời điểm gán; nó không phải ô công thức của bảng tính.

Phép gán cũng là một **biểu thức có kết quả**, nên có thể dùng gán nối tiếp:

~~~java
int first, second;
first = second = 9; // Tương đương first = (second = 9)
System.out.println(first + ", " + second); // 9, 9
~~~

Phép gán có tính kết hợp từ phải sang trái. Tuy nhiên, đừng lạm dụng các phép gán lồng nhau làm cho code khó theo dõi; câu lệnh riêng thường dễ kiểm thử hơn.

### `=` khác `==`

~~~java
int points = 10;
points = 20;                      // Gán lại
boolean matches = points == 20;  // So sánh, true
~~~

Trong một điều kiện boolean, viết nhầm phép gán có thể **vẫn biên dịch và gây bug**:

~~~java
boolean approved = false;
if (approved = true) { // Hợp lệ, nhưng đang thay đổi approved!
    System.out.println("APPROVED");
}
~~~

Nếu mục tiêu là kiểm tra, viết `if (approved)`. Với số nguyên, `if (points = 20)` không biên dịch vì biểu thức gán kiểu `int`, không phải `boolean`.


## 5. Giới hạn cần biết trước khi gán

Một phép gán hợp lệ phải **tương thích với kiểu khai báo**; ví dụ `int amount = 10; long total = amount;` hợp lệ, còn `int amount = "10";` là lỗi kiểu. Với `var`, compiler suy ra kiểu **tĩnh** từ initializer; với `final`, việc gán lại biến bị giới hạn. Các chi tiết về miền kiểu, chuyển hẹp, `final` và tính thay đổi của object có bài riêng trong chương.

Ở đây hãy luôn hỏi: biến nào đang được gán, biểu thức nào phải được đánh giá trước, và giá trị đã gán có cần tính lại khi đầu vào thay đổi không?

## 6. Ví dụ Java 17: lần theo từng phép gán

Lưu tệp `AssignmentWalkthrough.java`:

~~~java
public final class AssignmentWalkthrough {
    public static void main(String[] args) {
        int quantity;                 // Khai báo
        quantity = 3;                 // Gán lần đầu
        int unitPrice = 120;          // Khai báo và khởi tạo
        int total = unitPrice * quantity;
        System.out.println("start=" + total);

        quantity = 4;                 // Gán lại một đầu vào
        System.out.println("stored=" + total);
        total = unitPrice * quantity; // Tính lại có chủ đích
        System.out.println("recomputed=" + total);

        int left, right;
        left = right = 9;             // Gán nối tiếp từ phải qua trái
        System.out.println("chain=" + left + "," + right);
        boolean same = total == 480;  // So sánh, không gán
        System.out.println("same=" + same);

        int earlier = quantity;       // Sao chép giá trị int
        quantity = 5;
        System.out.println("copy=" + earlier + ", now=" + quantity);
    }
}
~~~

Dùng JDK 17 trở lên trong thư mục chứa tệp:

~~~bash
javac --release 17 -encoding UTF-8 AssignmentWalkthrough.java
java -cp . AssignmentWalkthrough
~~~

Kết quả:

~~~text
start=360
stored=360
recomputed=480
chain=9,9
same=true
copy=4, now=5
~~~

`stored=360` không tự chuyển thành 480 sau khi `quantity` đổi, vì biến `total` đã nhận giá trị của lần tính trước. `recomputed=480` chỉ xuất hiện sau một **phép gán mới**. `chain=9,9` là kết quả của `left = (right = 9)`; `same=true` đến từ phép so sánh `==`. Cuối cùng, `earlier` vẫn bằng 4 vì phép gán từ biến `int` sao chép giá trị, không tạo liên kết tự cập nhật.

## 7. Những nhầm lẫn thường gặp

| Cách viết | Vấn đề | Cách nhìn đúng |
|---|---|---|
| `int n; System.out.println(n);` trong method | Biến cục bộ chưa được gán | Gán chắc chắn trước khi đọc |
| `int n = 1; int n = 2;` trong cùng block | Khai báo trùng | Viết `n = 2;` nếu muốn gán lại |
| `int a, b = 5;` | Chỉ `b` được khởi tạo | Mỗi tên có initializer riêng |
| `if (approved = true)` | Thay đổi `approved` trong lúc kiểm tra | Viết `if (approved)` nếu chỉ kiểm tra |
| Đổi `quantity` rồi tưởng `total` đổi theo | Phép gán không tạo công thức sống | Tính và gán lại `total` khi cần |

Khi debug, hãy đánh dấu **điểm đọc** và **điểm ghi** của từng biến. Kiểm tra từng RHS theo thứ tự thực thi; đừng đọc một dòng gán như phương trình toán học.

## 8. Luyện tập kèm giải thích

**Bài 1.** Viết riêng hai phiên bản: `int score; score = 90;` và `int score = 90;`. Cả hai in ra bao nhiêu?  
*Đáp án:* Cùng in `90`; khác nhau ở thời điểm thực hiện khai báo/khởi tạo.

**Bài 2.** Dự đoán:

~~~java
int price = 25;
int count = 2;
int total = price * count;
count = 3;
System.out.println(total + "," + (price * count));
~~~

*Đáp án:* `50,75`; `total` giữ kết quả đã gán, biểu thức mới dùng `count = 3`.

**Bài 3.** `int first, second; first = second = 7;` khác gì `first == second`?  
*Đáp án:* Câu đầu gán cả hai biến; biểu thức thứ hai so sánh giá trị và trả về `boolean`.

**Bài 4.** Với `int a = 2; int b = a; a = 9;`, `b` có tự đổi không?  
*Đáp án:* Không, `b` vẫn bằng 2 vì là bản sao của giá trị tại thời điểm gán.

### Tự kiểm tra

- [ ] Phân biệt khai báo, khởi tạo, gán đầu, gán lại và đọc biến.
- [ ] Dự đoán đúng thứ tự đánh giá RHS và tác dụng của phép gán.
- [ ] Biết `=` gán giá trị còn `==` so sánh.
- [ ] Không nhầm biến Java với công thức tự cập nhật.
- [ ] Biết ranh giới cơ bản về kiểu, phạm vi và gán trước khi đọc.
- [ ] Chạy `AssignmentWalkthrough.java` và giải thích từng dòng kết quả.

**Kết luận:** Phép gán thay **giá trị được giữ trong biến tại một thời điểm**. Muốn thay kết quả tính toán, chương trình phải thực hiện phép gán khác. Học được điều này trước sẽ giúp bạn hiểu sâu hơn về kiểu, phạm vi, `final` và giá trị mặc định ở các bài kế tiếp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="variables-and-constants-002">Kiểu khai báo quyết định giá trị biến có thể giữ</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểu khai báo quyết định giá trị biến có thể giữ

> **Java core — language-basics / Biến và hằng**  
> **Môi trường:** JDK 17 trở lên.  
> **Mục tiêu:** Hiểu kiểu khai báo ràng buộc giá trị có thể gán cho biến; phân biệt biến kiểu nguyên thủy và tham chiếu, phép chuyển kiểu, suy luận kiểu với `var` và quy tắc gán lại với `final`.

## 1. Biến có kiểu ngay từ lúc khai báo

Java là ngôn ngữ **kiểm tra kiểu tĩnh** (*statically typed*). Khi khai báo:

~~~java
int age = 20;
String name = "Lan";
boolean active = true;
~~~

compiler biết `age` có kiểu `int`, `name` có kiểu `String`, `active` có kiểu `boolean`. Một lần gán sau đó phải **tương thích với kiểu đã khai báo**, chứ không được tự do biến `age` thành biến chứa chuỗi.

Cấu trúc cơ bản: `Type variableName = initialValue;`. Ví dụ `int count = 3;` gồm kiểu `int`, tên `count` và biểu thức khởi tạo `3`. Biến cục bộ có thể khai báo trước rồi gán sau, nhưng phải được **gán chắc chắn trước khi đọc**:

~~~java
int count;
count = 3;
System.out.println(count); // 3
~~~

Điều này khác với field của object có giá trị mặc định khi object được tạo; đừng suy ra biến cục bộ chưa gán mặc nhiên bằng 0.

## 2. Kiểu nguyên thủy (*primitive*): giữ giá trị thuộc miền của kiểu

Java có tám kiểu nguyên thủy. Biến primitive giữ **giá trị** thuộc loại đó, không phải tham chiếu đến một object:

| Kiểu | Giá trị có thể giữ | Ví dụ |
| --- | --- | --- |
| `byte` | Số nguyên -128 đến 127 (8 bit) | `byte level = 100;` |
| `short` | Số nguyên -32768 đến 32767 (16 bit) | `short pages = 500;` |
| `int` | Số nguyên -2147483648 đến 2147483647 (32 bit) | `int amount = 10_000;` |
| `long` | Số nguyên có dấu 64 bit | `long total = 3_000_000_000L;` |
| `float` | Số thực dấu chấm động IEEE 754 32 bit, độ chính xác hữu hạn | `float rate = 1.5f;` |
| `double` | Số thực dấu chấm động IEEE 754 64 bit, độ chính xác hữu hạn | `double ratio = 1.5;` |
| `char` | Một đơn vị mã UTF-16, từ `'\u0000'` đến `'\uffff'` | `char grade = 'A';` |
| `boolean` | Chỉ `true` hoặc `false` | `boolean ready = true;` |

`char` không bảo đảm chứa trọn mọi ký tự Unicode: một số code point ngoài BMP cần **hai** đơn vị UTF-16. `float` và `double` có cả giá trị đặc biệt như NaN, vô cực; chúng không phải kiểu số thập phân chính xác cho mọi phép tính tiền tệ.

Với integer literal thập phân vượt phạm vi `int`, thêm hậu tố `L` để tạo literal `long`:

~~~java
long visitors = 3_000_000_000L;  // Đúng.
// long wrong = 3_000_000_000;  // Sai: literal mặc định int quá lớn.
~~~

`boolean` không tự chuyển thành số `0`/`1`; `int flag = true;` là lỗi kiểu.

## 3. Kiểu tham chiếu: kiểu khai báo giới hạn đối tượng được trỏ tới

`String`, array, class, interface, record… là **kiểu tham chiếu**. Biến tham chiếu giữ một **reference** tới object tương thích (hoặc `null`), không trực tiếp chứa toàn bộ object như biến primitive chứa một số.

~~~java
String title = "Java";
CharSequence text = title;           // String triển khai CharSequence.
Number number = Integer.valueOf(42); // Integer là Number.
number = Double.valueOf(2.5);       // Double cũng là Number.
Object item = "book";
item = Integer.valueOf(7);          // Integer cũng là Object.
~~~

Một biến khai báo kiểu `Number` có thể trỏ đến `Integer` hoặc `Double` vì chúng tương thích với `Number`. **Kiểu khai báo của biến vẫn là `Number`** sau khi gán lại, dù kiểu runtime của object thay đổi.

Compiler cũng giới hạn những method được gọi dựa trên **kiểu tham chiếu tại vị trí sử dụng**:

~~~java
CharSequence text = "Java";
System.out.println(text.length()); // Đúng: CharSequence có length().
// text.substring(1);              // Sai: CharSequence không khai báo substring().
~~~

Object thực tế có thể là `String`, nhưng compiler không tự cho gọi mọi method riêng của `String` qua biến kiểu `CharSequence`. Khi thật sự cần thao tác phụ thuộc kiểu runtime, hãy kiểm tra trước, ví dụ cú pháp Java 17 `if (item instanceof Integer i) { ... }`.

`null` có thể gán cho biến tham chiếu như `String name = null;`, nhưng không gán được cho primitive `int`. Gọi method trên tham chiếu `null` có thể gây `NullPointerException`.

## 4. Phép gán hợp lệ, chuyển rộng và chuyển hẹp

| Tình huống | Ví dụ | Kết quả |
| --- | --- | --- |
| Cùng kiểu | `int x = 10; x = 20;` | Hợp lệ |
| Mở rộng số nguyên | `byte b = 100; int x = b;` | Hợp lệ, giữ nguyên giá trị |
| `int` sang `long` | `int x = 100; long y = x;` | Hợp lệ |
| Chuyển hẹp có cast | `int x = 130; byte b = (byte) x;` | Hợp lệ về cú pháp, **b = -126** do chỉ giữ 8 bit thấp |
| Số thực sang số nguyên có cast | `int n = (int) 3.9;` | Hợp lệ, **n = 3** (phần lẻ bị bỏ) |
| Gán chuỗi cho số | `int x = "10";` | **Lỗi biên dịch** |
| Gán số sang boolean | `boolean b = 1;` | **Lỗi biên dịch** |
| Gán supertype sang subtype | `Object o = "Hi"; String s = o;` | **Lỗi biên dịch** nếu không có cast/kiểm tra kiểu phù hợp |

**Lưu ý khi chuyển rộng:** từ số nguyên sang `float`/`double` có thể mất độ chính xác với giá trị lớn, dù không cần cast. **Cast không bảo đảm giá trị còn nguyên**; nó chỉ cho phép một phép chuyển kiểu nhất định.

Một ngoại lệ thực tế ở mức compile time: `byte b = 100;` hợp lệ vì `100` là biểu thức hằng kiểu `int` nằm trong phạm vi `byte`; nhưng:

~~~java
int number = 100;
// byte b = number;  // Lỗi: number là biến int, không phải constant expression.
// byte b = 128;     // Lỗi: vượt phạm vi byte.
~~~

Đừng đánh đồng việc **giá trị đang nhỏ** với việc compiler cho phép mọi phép gán thu hẹp.

## 5. Chương trình Java 17+ có thể biên dịch và chạy

Tạo file **DeclaredTypeDemo.java**:

~~~java
public class DeclaredTypeDemo {
    public static void main(String[] args) {
        byte small = 100;
        int quantity = small;
        long revenue = 3_000_000_000L;
        double average = quantity / 4.0;
        boolean available = true;
        char grade = 'A';

        CharSequence text = "Java 17";
        Number number = Integer.valueOf(42);
        number = Double.valueOf(2.5);

        Object item = "book";
        item = Integer.valueOf(7);

        byte narrowed = (byte) 130;
        int whole = (int) 3.9;

        var inferred = 10; // Compiler suy ra int.
        inferred = 12;
        final int MAX = 20;

        System.out.println("quantity=" + quantity);
        System.out.println("revenue=" + revenue);
        System.out.println("average=" + average);
        System.out.println("available=" + available + ", grade=" + grade);
        System.out.println("text=" + text);
        System.out.println("number=" + number);
        System.out.println("item=" + item);
        System.out.println("narrowed=" + narrowed + ", whole=" + whole);
        System.out.println("inferred=" + inferred + ", MAX=" + MAX);

        if (item instanceof Integer value) {
            System.out.println("nextItem=" + (value + 1));
        }
    }
}
~~~

Trong terminal (Bash, PowerShell hoặc Command Prompt) tại thư mục chứa file:

~~~bash
javac -encoding UTF-8 DeclaredTypeDemo.java
java DeclaredTypeDemo
~~~

**Expected output:**

~~~text
quantity=100
revenue=3000000000
average=25.0
available=true, grade=A
text=Java 17
number=2.5
item=7
narrowed=-126, whole=3
inferred=12, MAX=20
nextItem=8
~~~

Đọc kết quả: `quantity` nhận giá trị `byte` dưới dạng `int`; `number` giữ kiểu khai báo `Number` nhưng trỏ tới object `Double` tại lúc in; `narrowed` minh họa chuyển hẹp mất thông tin; `item instanceof Integer value` kiểm tra kiểu object trước khi dùng `value` như `Integer`.


## 6. `var` giữ kiểu tĩnh; `final` hạn chế gán lại

`var` cho phép suy luận kiểu từ initializer ở vị trí được hỗ trợ, nhưng kiểu suy ra vẫn cố định:

~~~java
var count = 10;    // int
count = 20;        // Hợp lệ
// count = "twenty"; // Lỗi kiểu
// var unknown = null; // Không suy ra được kiểu từ null đơn lẻ
~~~

`final` là ràng buộc **số lần gán cho biến**, độc lập với kiểu khai báo:

~~~java
final Number max = Integer.valueOf(10);
// max = Double.valueOf(3.5); // Lỗi: final không cho gán lại
~~~

Vì vậy, câu hỏi “giá trị này **có thể gán vì tương thích kiểu không?**” khác câu hỏi “**biến final này còn được phép gán không?**”. Quy tắc gán `final` theo luồng điều khiển và tính mutable của object được đào sâu ở hai bài riêng.

## 7. Bẫy thường gặp và cách tự kiểm tra

| Mảnh mã | Vấn đề | Cách khắc phục |
| --- | --- | --- |
| `long n = 3_000_000_000;` | Literal thập phân chưa có `L` và quá lớn cho `int` | `3_000_000_000L` |
| `byte b = 128;` | Ngoài phạm vi `byte` | Dùng kiểu rộng hơn hoặc giá trị trong miền |
| `int x = 3.9;` | `double` không tự thu hẹp thành `int` | Xem có thực sự muốn `(int) 3.9` không |
| `String s = 100;` | `int` không được gán trực tiếp thành `String` | `String.valueOf(100)` |
| `var x = 10; x = "A";` | `var` đã suy luận `int` | Dùng biến kiểu phù hợp, không gán khác kiểu |
| `CharSequence t = "Hi"; t.substring(1);` | Kiểu khai báo không có method `substring` | Dùng `String` khi cần, hoặc kiểm tra/chuyển kiểu an toàn |
| `String s = null; s.length();` | Tham chiếu `null` không có object để gọi method | Kiểm tra `null` hoặc thiết kế tránh null |

### Bài tập ngắn

1. Dự đoán kiểu và giá trị của `var x = 5L;`, sau đó thử gán `x = 6;` và `x = "6";`. Tại sao kết quả khác nhau?
2. Giải thích vì sao `byte b = 100;` biên dịch được nhưng `int x = 100; byte b = x;` lại không.
3. Tạo `Number n = Integer.valueOf(3);` rồi gán `Double.valueOf(3.5)`. Kiểu khai báo của `n` có thay đổi không?
4. Thử `final StringBuilder b = new StringBuilder(); b.append("OK");`. Phân biệt thay đổi object với gán lại reference.
5. Trong `DeclaredTypeDemo`, thử thay `(byte) 130` bằng `(byte) 127` và dự đoán kết quả.

**Ghi nhớ:** Với **primitive**, kiểu khai báo xác định miền giá trị và cách biểu diễn. Với **reference**, kiểu khai báo giới hạn đối tượng tương thích và những thành phần có thể truy cập qua biến; object thực tế có thể là subtype. `var` chỉ giúp suy luận kiểu cố định; `final` chỉ hạn chế gán lại biến. Mỗi phép gán đều phải qua quy tắc kiểu của Java.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="variables-and-constants-003">Phân biệt biến cục bộ, tham số và trường</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt biến cục bộ, tham số và trường trong Java 17+

Trong Java, ba khai báo có thể cùng dùng kiểu `int` nhưng có vai trò rất khác nhau:

- **Biến cục bộ (local variable):** khai báo bên trong thân phương thức, constructor hoặc khối; dùng để tính toán tạm trong phạm vi đó.
- **Tham số (parameter):** biến được khai báo trong phần tham số của phương thức/constructor; nhận giá trị do bên gọi truyền vào.
- **Trường (field):** biến khai báo ở cấp lớp hoặc interface, bên ngoài các phương thức; biểu diễn trạng thái của **đối tượng** hoặc dữ liệu **thuộc lớp** nếu là `static`.

Hiểu rõ nơi khai báo, phạm vi sử dụng và cách khởi tạo giúp tránh lỗi `cannot find symbol`, `variable might not have been initialized` và nhầm dữ liệu giữa các đối tượng.

## 1. Nhận diện nhanh bằng vị trí khai báo

~~~java
public class Example {
    private int balance;             // Instance field.
    private static int accountCount; // Static field.

    public Example(int initial) {    // initial: tham số constructor.
        int checked = initial >= 0 ? initial : 0; // Biến cục bộ.
        this.balance = checked;      // Gán vào field của đối tượng.
        accountCount++;
    }

    public int increase(int amount) { // amount: tham số phương thức.
        int next = balance + amount;  // next: biến cục bộ.
        balance = next;               // Cập nhật instance field.
        return balance;
    }
}
~~~

| Tiêu chí | Biến cục bộ | Tham số | Trường instance | Trường `static` |
| --- | --- | --- | --- | --- |
| Khai báo ở đâu? | Trong phương thức, constructor hoặc khối | Danh sách tham số | Trong thân lớp, không có `static` | Trong thân lớp, có `static` |
| Thuộc về ai? | Một lần thực thi/phạm vi thích hợp | Một lần gọi phương thức/constructor | Mỗi đối tượng có bản riêng | Gắn với lớp trong ngữ cảnh tải lớp |
| Có giá trị mặc định tự động? | **Không** | Không; nhận giá trị khi được gọi | **Có** | **Có** |
| Tồn tại/được truy cập theo phạm vi nào? | Phạm vi khai báo và quy tắc luồng điều khiển | Trong thân khai báo tương ứng | Qua đối tượng hoặc `this` | Qua tên lớp, ví dụ `Example.accountCount` |
| Điển hình dùng cho | Kết quả tính tạm | Input của thao tác | Trạng thái từng đối tượng | Đếm đối tượng, cấu hình dùng chung |

Tham số trong Java vẫn là **biến**, nhưng nó khác biến cục bộ tự khai báo ở chỗ **giá trị ban đầu được bên gọi cung cấp**. Nói “tham số là biến cục bộ” có thể đúng theo cách diễn giải rộng về vùng sử dụng, nhưng khi phân loại theo **vị trí khai báo** nên tách chúng ra.

## 2. Chương trình Java chạy được

Lưu file UTF-8 **`VariableKindsDemo.java`**:

~~~java
public class VariableKindsDemo {
    private static int accountCount;  // Static field: ban dau = 0.

    private final String owner;        // Instance field.
    private int balance;               // Instance field: ban dau = 0.
    private boolean active;            // Instance field: ban dau = false.

    public VariableKindsDemo(String owner) {
        this.owner = owner;            // Field = tham so cung ten.
        accountCount++;
    }

    public void deposit(int amount) {  // amount: tham so.
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }

        int before = balance;          // Bien cuc bo.
        balance += amount;             // Cap nhat instance field.
        System.out.println(owner + ": " + before + " -> " + balance);
    }

    public static void main(String[] args) {
        int startingAmount = 50;       // Bien cuc bo cua main.

        VariableKindsDemo mai = new VariableKindsDemo("Mai");
        VariableKindsDemo an = new VariableKindsDemo("An");

        System.out.println("accounts=" + accountCount);
        System.out.println("mai.initialBalance=" + mai.balance);
        System.out.println("mai.initialActive=" + mai.active);

        mai.deposit(startingAmount);
        an.deposit(20);

        System.out.println("mai.finalBalance=" + mai.balance);
        System.out.println("an.finalBalance=" + an.balance);
        System.out.println(
                "accounts.shared=" + VariableKindsDemo.accountCount);
    }
}
~~~

Mở **PowerShell trên Windows** hoặc **Bash trên Linux** ở thư mục chứa tệp:

~~~bash
java -version
javac -version
javac -encoding UTF-8 VariableKindsDemo.java
java VariableKindsDemo
~~~

**Expected output:**

~~~text
accounts=2
mai.initialBalance=0
mai.initialActive=false
Mai: 0 -> 50
An: 0 -> 20
mai.finalBalance=50
an.finalBalance=20
accounts.shared=2
~~~

Vì sao?

1. `accountCount` là **static field**, tăng mỗi lần constructor chạy; có hai đối tượng nên kết quả là 2, bất kể đọc qua `accountCount` hay `VariableKindsDemo.accountCount`.
2. `balance` và `active` là **instance fields** không khởi tạo tường minh: Java đặt giá trị mặc định `0` và `false` cho mỗi đối tượng.
3. `owner` cũng là instance field nhưng được gán trong constructor. Tham số `owner` chỉ dùng để cung cấp giá trị cho trường `this.owner`.
4. `startingAmount` là biến cục bộ của `main`; `mai.deposit(startingAmount)` truyền giá trị 50 vào tham số `amount` của `deposit`.
5. `before` là biến cục bộ **riêng trong từng lần gọi `deposit`**. Trường `balance` của Mai thay đổi thành 50, còn của An thành 20: hai đối tượng không dùng chung instance field.

`main` là phương thức `static`: ở đó bạn **không thể truy cập trực tiếp `balance`** nếu không thông qua đối tượng (ví dụ `mai.balance`). Ngược lại, các phương thức instance như `deposit` có thể truy cập trường instance thông qua `this` ngầm định.


## 3. Khởi tạo theo từng loại biến

**Field** có giá trị mặc định theo kiểu (`int` là 0, `boolean` là `false`, tham chiếu là `null`), dù field initializer và constructor có thể thay giá trị đó. **Tham số** nhận giá trị đối số từ lời gọi. **Biến cục bộ** không có giá trị mặc định được phép đọc; cần gán chắc chắn trước khi đọc.

~~~java
class ExampleState {
    int count; // 0 khi object mới tạo

    int sum(int input) { // input là tham số đã nhận giá trị từ caller
        int result = count + input; // result là local được khởi tạo
        return result;
    }
}
~~~

Một `final` instance field chưa có initializer phải được gán hợp lệ trước khi constructor hoàn tất bình thường, như `owner` trong ví dụ chính. Bài về giá trị mặc định giải thích đầy đủ kiểu mảng, `char`, wrapper và thứ tự khởi tạo; bài về *definite assignment* phân tích các nhánh điều khiển.

## 4. Phạm vi (scope) và tên bị che (shadowing)

Biến cục bộ hoặc tham số có thể **che** field cùng tên. Trong ví dụ:

~~~java
private final String owner;

public VariableKindsDemo(String owner) {
    this.owner = owner;
}
~~~

- `owner` bên phải là **tham số** constructor.
- `this.owner` bên trái là **field** của đối tượng hiện tại.

Nếu chỉ viết `owner = owner;`, cả hai vế sẽ nói tới tham số, không gán cho field; với field `final` chưa được khởi tạo, constructor còn vi phạm yêu cầu gán giá trị bắt buộc.

**Phạm vi khối:** biến khai báo trong `{ ... }` không dùng được ở ngoài phạm vi của nó:

~~~java
if (true) {
    int inside = 10;
    System.out.println(inside);
}
// System.out.println(inside); // Loi: khong thay ten inside.
~~~

Không chỉ có thụt lề: **dấu ngoặc nhọn và vị trí khai báo** mới quyết định phạm vi hợp lệ. Hai biến cục bộ cùng tên ở những phạm vi lồng nhau cũng không thể tùy ý được khai báo chồng lên nhau; khi không chắc, dùng tên rõ nghĩa thay vì cố tạo nhiều tên trùng.

## 5. Truyền tham số trong Java là truyền theo giá trị

Java **luôn truyền giá trị** vào tham số:

~~~java
static void changeNumber(int value) {
    value = 99; // Chi doi bien tham so.
}

// Trong main:
int number = 5;
changeNumber(number);
System.out.println(number); // 5
~~~

Đối với kiểu nguyên thủy, giá trị số được sao chép. Đối với đối tượng, **giá trị tham chiếu** được sao chép; phương thức có thể thay đổi trạng thái của đối tượng chung thông qua tham chiếu đó (nếu đối tượng cho phép), nhưng gán lại biến tham số không tự đổi tham chiếu trong biến của bên gọi.

Đừng diễn giải “tham số lưu ở stack, field lưu ở heap” thành một quy luật vật lý tuyệt đối: cách tối ưu hóa bộ nhớ là chi tiết triển khai của JVM. Khi học ngôn ngữ, quan trọng hơn là **ngữ nghĩa, phạm vi và cách chia sẻ trạng thái**.

## 6. Lỗi thường gặp

| Hiện tượng | Nguyên nhân thường gặp | Cách sửa |
| --- | --- | --- |
| `variable might not have been initialized` | Đọc biến cục bộ trước khi gán chắc chắn | Gán giá trị trên mọi nhánh cần thiết |
| `cannot find symbol` với biến | Dùng ngoài phạm vi hoặc gõ sai tên | Xem nơi khai báo và cặp `{ }` |
| `non-static variable ... cannot be referenced from a static context` | Đọc instance field trực tiếp trong `main` | Tạo/nhận đối tượng rồi truy cập qua đối tượng |
| Field không thay đổi sau constructor | Tham số che tên field, viết `owner = owner` | Viết `this.owner = owner` |
| Hai đối tượng có chung giá trị ngoài dự kiến | Dùng `static` cho dữ liệu đáng ra thuộc từng đối tượng | Bỏ `static` với trạng thái riêng |
| Field luôn bằng 0 hoặc `null` | Quên khởi tạo hoặc quên gán đúng field | Kiểm tra constructor và initializer |
| Gán lại tham số nhưng bên gọi không đổi | Java truyền giá trị của đối số | Trả kết quả rồi gán ở bên gọi nếu cần |

## 7. Bài tập tự kiểm tra

1. **Phân loại:** Trong `VariableKindsDemo`, `accountCount`, `owner`, `balance`, `startingAmount`, `amount`, `before` thuộc nhóm nào?  
   *Đáp án:* `accountCount` là static field; `owner` và `balance` là instance fields; `startingAmount` và `before` là local variables; `amount` là parameter. Ngoài ra còn có tham số constructor `owner` che tên field.
2. **Dự đoán:** Nếu gọi `mai.deposit(10)` thêm một lần, `mai.balance` và `an.balance` là bao nhiêu?  
   *Đáp án:* 60 và 20. `accountCount` vẫn 2 vì không tạo thêm đối tượng.
3. **Khởi tạo:** Tại sao `int balance;` ở cấp lớp đọc ra 0, nhưng `int local; System.out.println(local);` trong phương thức không biên dịch?  
   *Đáp án:* Field nhận giá trị mặc định; local cần được gán chắc chắn.
4. **Shadowing:** Giải thích khác biệt giữa `this.owner = owner;` và `owner = owner;` trong constructor.  
   *Đáp án:* Câu đầu gán tham số cho field; câu sau chỉ gán tham số cho chính nó.
5. **Truyền giá trị:** Vì sao đổi tham số `value` trong `changeNumber` không đổi biến `number` bên gọi?  
   *Đáp án:* Tham số giữ bản sao của giá trị số.

## Checklist

- [ ] Nhận diện được **biến cục bộ**, **tham số**, **instance field**, **static field** theo vị trí khai báo.
- [ ] Biết field có giá trị mặc định nhưng local variable thì không.
- [ ] Dùng `this.field` để giải quyết tên bị tham số hoặc biến cục bộ che.
- [ ] Không dùng local variable ngoài phạm vi khối.
- [ ] Hiểu instance field thuộc từng đối tượng, static field dùng chung ở cấp lớp.
- [ ] Giải thích được cách Java truyền giá trị vào tham số.
- [ ] Biên dịch và chạy `VariableKindsDemo.java`, dự đoán chính xác tám dòng kết quả.

**Ghi nhớ:** Biến cục bộ phục vụ một đoạn tính toán, tham số mang dữ liệu của lời gọi, còn trường lưu trạng thái của đối tượng hoặc lớp. Hãy hỏi ba câu khi gặp một tên biến: **Nó khai báo ở đâu? Ai sở hữu giá trị đó? Và khi nào nó được gán?**


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="variables-and-constants-004">Yêu cầu gán giá trị trước khi đọc biến cục bộ</a>

<details>
<summary>Xem chi tiết</summary>

# Yêu cầu gán giá trị trước khi đọc biến cục bộ trong Java

## 1. Quy tắc cốt lõi: definite assignment

Một **biến cục bộ** (*local variable*) được khai báo bên trong phương thức, constructor hoặc block có thể chưa có initializer ngay tại dòng khai báo:

~~~java
int quantity;             // Được phép khai báo
quantity = 4;             // Gán trước khi sử dụng
System.out.println(quantity); // 4
~~~

Tuy nhiên, **Java 17 yêu cầu biến cục bộ phải được gán chắc chắn trước khi đọc giá trị**. Quy tắc này gọi là **definite assignment** (gán chắc chắn). Nó được trình biên dịch `javac` kiểm tra bằng cách phân tích những đường điều khiển có thể đi tới chỗ đọc biến.

Ví dụ cố tình sai:

~~~java
static void invalid() {
    int quantity;
    System.out.println(quantity); // LỖI BIÊN DỊCH
}
~~~

Thông báo của `javac` thường có dạng:

~~~text
error: variable quantity might not have been initialized
~~~

Đây là lỗi **compile-time**, không phải exception chỉ xuất hiện khi chạy. Trình biên dịch không cho phép sử dụng một biến cục bộ mà có thể chưa được gán.

> **Nhớ:** “đã khai báo” khác “đã được gán giá trị”. Không nhất thiết phải khởi tạo ngay ở câu lệnh khai báo, nhưng mọi đường thực thi đi tới nơi đọc phải bảo đảm biến đã được gán.

## 2. Khai báo, gán và đọc giá trị khác nhau thế nào?

~~~java
int score;              // Khai báo, chưa có giá trị được phép đọc
score = 80;             // Gán lần đầu
int copied = score;     // Đọc score hợp lệ; copied được khởi tạo
score = score + 5;      // Đọc score cũ, rồi gán score mới
System.out.println(score); // 85
~~~

| Câu lệnh | Có đọc giá trị cũ của `score`? | Điều kiện |
|---|---|---|
| `int score;` | Không | Khai báo hợp lệ |
| `score = 80;` | Không | Gán giá trị đầu tiên hợp lệ |
| `int x = score;` | Có | `score` phải đã được gán |
| `score = score + 1;` | Có | Đọc vế phải trước khi gán lại |
| `score++;` | Có | Cần giá trị hiện tại để tăng |
| `score += 1;` | Có | Phép gán kết hợp cũng dùng giá trị cũ |

Ví dụ **không biên dịch** dù có dấu `=`:

~~~java
int count;
// count = count + 1; // Sai: vế phải đọc count chưa gán
// count++;           // Sai: cần đọc count hiện tại
// count += 1;        // Sai: cũng cần giá trị cũ
~~~

Sửa bằng cách khởi tạo trước:

~~~java
int count = 0;
count++;
System.out.println(count); // 1
~~~

## 3. Compiler xét mọi nhánh if/else dẫn tới chỗ đọc

Trường hợp **sai**:

~~~java
static String invalid(boolean passed) {
    String result;
    if (passed) {
        result = "PASS";
    }
    return result; // LỖI: passed có thể false
}
~~~

Nếu `passed` là `false`, câu lệnh gán không được thực hiện. Vì thế `return result` có một đường đi đọc biến chưa gán.

Cách sửa 1 — **gán ở cả hai nhánh**:

~~~java
static String status(boolean passed) {
    String result;
    if (passed) {
        result = "PASS";
    } else {
        result = "FAIL";
    }
    return result;
}
~~~

Cách sửa 2 — **đưa giá trị mặc định vào khai báo**:

~~~java
static String status(boolean passed) {
    String result = "FAIL";
    if (passed) {
        result = "PASS";
    }
    return result;
}
~~~

Cách sửa 3 — **dùng biểu thức điều kiện** khi chỉ cần chọn một giá trị:

~~~java
static String status(boolean passed) {
    return passed ? "PASS" : "FAIL";
}
~~~

Cả ba đều có thể hợp lệ, nhưng hãy chọn cách phản ánh đúng yêu cầu. **Không gán giá trị giả chỉ để làm hết lỗi biên dịch** nếu giá trị ấy khiến nghiệp vụ hiểu nhầm.

## 4. Nhánh return sớm có thể giúp bảo đảm đã gán

Compiler quan tâm đến **những đường có thể đi đến lệnh đọc**, không nhất thiết mọi nhánh đều phải gán cùng một biến.

~~~java
static String sign(int number) {
    if (number < 0) {
        return "NEGATIVE";
    }

    String label;
    if (number == 0) {
        label = "ZERO";
    } else {
        label = "POSITIVE";
    }
    return label;
}
~~~

Nhánh `number < 0` đã `return`, không thể đi xuống `return label`. Những nhánh còn lại đều gán `label`, nên mã hợp lệ.

Một ví dụ ngắn hơn:

~~~java
static int nonnegative(int value) {
    if (value < 0) {
        throw new IllegalArgumentException("Negative value");
    }
    int accepted = value;
    return accepted;
}
~~~

`return` hoặc `throw` có thể kết thúc đường điều khiển hiện tại. Nhưng nếu một nhánh **chỉ in lỗi rồi tiếp tục**, nó vẫn cần bảo đảm biến được gán trước khi đọc.

## 5. Vòng lặp có thể không chạy lần nào

Một `while` hoặc `for` với điều kiện kiểm tra trước có thể thực hiện **0 vòng**, nên không được mặc định xem việc gán bên trong loop là đủ.

~~~java
static void invalid(int limit) {
    int last;
    for (int i = 0; i < limit; i++) {
        last = i;
    }
    // System.out.println(last); // Nếu mở comment: lỗi compile
}
~~~

Nếu `limit <= 0`, không vòng nào chạy. Cách đúng khi cần đọc kết quả sau vòng lặp là định nghĩa một giá trị khởi tạo có ý nghĩa:

~~~java
static long sumFromOneTo(int limit) {
    long sum = 0L;
    for (long i = 1; i <= limit; i++) {
        sum += i;
    }
    return sum;
}
~~~

Với `limit = 4`, kết quả là `10`; với `limit = 0`, kết quả là `0`. Dùng `long` cho cả biến đếm và tổng để tránh tràn `int` khi `limit` rất lớn; vòng lặp vẫn có chi phí thời gian tỷ lệ với `limit` và ví dụ này chủ yếu minh họa quy tắc gán biến.

**Phân biệt:** biến `int i = 1` trong phần khởi tạo của `for` được gán rõ ràng, nên có thể đọc ở điều kiện và thân loop. Vấn đề nảy sinh khi bạn **chỉ** gán một biến khác ở bên trong loop rồi đọc **sau** loop, trong khi loop có thể không chạy.

## 6. Biến final cục bộ: được gán lần đầu nhưng không gán lại

`final` cho phép viết một **blank final local** (khai báo mà chưa gán), miễn là trước khi đọc nó đã được gán hợp lệ và không bị gán lại trên đường thực thi.

~~~java
static String mode(boolean custom) {
    final String selected;
    if (custom) {
        selected = "CUSTOM";
    } else {
        selected = "DEFAULT";
    }
    return selected;
}
~~~

Đoạn trên hợp lệ: mỗi đường thực thi gán `selected` đúng một lần trước khi đọc.

Ngược lại:

~~~java
final int limit = 5;
// limit = 6; // LỖI: biến final không thể được gán lại
~~~

**`final` không tự cấp giá trị ban đầu.** Nếu khai báo `final int limit;` rồi đọc mà chưa có phép gán chắc chắn, `javac` vẫn báo lỗi.


## 7. Phạm vi áp dụng: biến cục bộ và những chỗ dễ nhầm

*Definite assignment* được kiểm tra với biến cục bộ. **Tham số** đã nhận giá trị từ bên gọi; **field** được mặc định theo kiểu (số là 0, `boolean` là `false`, tham chiếu là `null`); **phần tử mảng mới** cũng được mặc định theo kiểu.

~~~java
int[] numbers = new int[2];  // Local numbers đã được gán reference
System.out.println(numbers[0]); // 0: phần tử mảng được mặc định
int[] missing;
// System.out.println(missing[0]); // Lỗi compile: local missing chưa gán
~~~

Đọc được một field tham chiếu có giá trị mặc định `null` không bảo đảm gọi method trên nó an toàn. Bài về giá trị mặc định sẽ giải thích trường hợp mảng nhiều chiều và các kiểu cụ thể.

## 8. `var` cần initializer để suy ra kiểu

Với Java 17, biến cục bộ có thể dùng `var` khi compiler suy ra kiểu từ biểu thức khởi tạo:

~~~java
var count = 5;        // Suy ra int
var name = "Java";    // Suy ra String
count = 6;            // Đúng, vẫn là int
~~~

Các dòng sau không hợp lệ:

~~~java
// var count;          // Sai: thiếu initializer để suy ra kiểu
// var name = null;    // Sai: không thể suy ra kiểu phù hợp chỉ từ null
// count = "six";      // Sai: kiểu String không gán được cho int
~~~

`var` không bỏ kiểm tra kiểu và cũng không làm giảm yêu cầu gán trước khi đọc. Nếu cần khai báo trước và gán sau ở một nhánh điều khiển, hãy dùng **kiểu tường minh** như `String label;`.

## 9. Chương trình Java 17 hoàn chỉnh, chạy được

Tạo tệp chính xác tên **`DefiniteAssignmentDemo.java`**:

~~~java
public final class DefiniteAssignmentDemo {
    private DefiniteAssignmentDemo() {}

    static final class Settings {
        int attempts;       // Field mặc định 0
        boolean enabled;    // Field mặc định false
        String note;        // Field mặc định null
    }

    static String review(boolean accepted) {
        String result;      // Khai báo chưa gán
        if (accepted) {
            result = "ACCEPT";
        } else {
            result = "REJECT";
        }
        return result;      // Cả hai nhánh đều đã gán
    }

    static String classify(int number) {
        if (number < 0) {
            return "NEGATIVE";
        }
        String label;
        if (number == 0) {
            label = "ZERO";
        } else {
            label = "POSITIVE";
        }
        return label;
    }

    static long sum(int limit) {
        long result = 0L;   // Cần giá trị kể cả khi loop không chạy
        for (long i = 1; i <= limit; i++) {
            result += i;
        }
        return result;
    }

    static String chooseMode(boolean custom) {
        final String mode;
        if (custom) {
            mode = "CUSTOM";
        } else {
            mode = "DEFAULT";
        }
        return mode;
    }

    public static void main(String[] args) {
        int quantity;
        quantity = 4;
        System.out.println("quantity=" + quantity);

        System.out.println("review(true)=" + review(true));
        System.out.println("review(false)=" + review(false));
        System.out.println("classify(-2)=" + classify(-2));
        System.out.println("classify(0)=" + classify(0));
        System.out.println("classify(5)=" + classify(5));

        System.out.println("mode=" + chooseMode(args.length > 0));
        System.out.println("sum(4)=" + sum(4));
        System.out.println("sum(0)=" + sum(0));

        int[] numbers = new int[2];
        System.out.println("array[0]=" + numbers[0]);

        Settings settings = new Settings();
        System.out.println("field attempts=" + settings.attempts);
        System.out.println("field enabled=" + settings.enabled);
        System.out.println("field note=" + settings.note);
    }
}
~~~

Dùng **JDK 17 trở lên**, mở terminal tại thư mục chứa tệp nguồn.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 DefiniteAssignmentDemo.java
java -cp . DefiniteAssignmentDemo
java -cp . DefiniteAssignmentDemo custom
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\DefiniteAssignmentDemo.java
java -cp . DefiniteAssignmentDemo
java -cp . DefiniteAssignmentDemo custom
~~~

Đầu ra **lần chạy không có đối số**:

~~~text
quantity=4
review(true)=ACCEPT
review(false)=REJECT
classify(-2)=NEGATIVE
classify(0)=ZERO
classify(5)=POSITIVE
mode=DEFAULT
sum(4)=10
sum(0)=0
array[0]=0
field attempts=0
field enabled=false
field note=null
~~~

Khi chạy lệnh có đối số `custom`, mọi dòng vẫn như trên, **trừ dòng `mode=CUSTOM`**. Trong ví dụ này chỉ kiểm tra sự hiện diện của đối số qua `args.length > 0`; bản thân từ `custom` không được phân tích thêm.

## 10. Cách tự chẩn đoán lỗi gán trước khi đọc

Khi gặp thông báo gần giống `variable x might not have been initialized`, hãy kiểm tra:

1. **Biến thuộc loại nào?** Nếu là biến cục bộ, không giả định có giá trị mặc định giống field.
2. **Vị trí đọc ở đâu?** Đọc có thể ẩn trong `x++`, `x += 1`, `return x` hoặc tham số truyền cho method.
3. **Mọi nhánh có dẫn tới dòng đọc đều đã gán chưa?** Kiểm tra `if/else`, `switch` và các đường `return`/`throw`.
4. **Vòng lặp có thể không chạy không?** Nếu có, giá trị gán *chỉ* trong loop chưa bảo đảm an toàn sau loop.
5. **Đã vô tình khai báo `final` rồi gán lại chưa?** Blank final được gán lần đầu đúng đường đi; không được gán lần nữa.
6. **Có đang dùng `var` mà thiếu initializer không?** Đổi sang kiểu tường minh hoặc khởi tạo ngay.
7. **Giá trị mặc định bạn chọn có đúng ý nghĩa không?** Đừng dùng `0`, `false` hoặc `null` chỉ để che thiếu dữ liệu hợp lệ.

Compiler kiểm tra **tính chắc chắn của phép gán**, chứ không biết giá trị ấy có đúng nghiệp vụ hay không. Chương trình có thể gán một giá trị sai hoàn toàn mà vẫn qua kiểm tra definite assignment.

## 11. Bài tập ngắn kèm đáp án

### Bài A — nhánh thiếu gán

~~~java
static int score(boolean passed) {
    int result;
    if (passed) {
        result = 100;
    }
    return result;
}
~~~

**Đáp án:** lỗi compile vì `passed=false` khiến `result` chưa gán. Có thể thêm `else { result = 0; }` **nếu 0 đúng yêu cầu**, hoặc xử lý nhánh sai bằng `return`/`throw` hợp lý.

### Bài B — phép tăng có đọc biến không?

~~~java
int counter;
// counter++;
~~~

**Đáp án:** có. `counter++` phải đọc giá trị cũ nên chưa hợp lệ. Đặt `int counter = 0;` trước khi tăng.

### Bài C — vì sao field không lỗi?

~~~java
class Sample {
    int value;
    void show() {
        System.out.println(value);
    }
}
~~~

**Đáp án:** `value` là **instance field**, mặc định là `0`. Nếu `value` là biến cục bộ chưa được gán trong method, hành vi compile sẽ khác.

### Bài D — vòng lặp chạy 0 lần

~~~java
int last;
for (int i = 0; i < 0; i++) {
    last = i;
}
// System.out.println(last);
~~~

**Đáp án:** khi bỏ comment dòng in, biến `last` chưa chắc đã được gán. Loop không thực hiện vòng nào nên không thể dựa vào phép gán trong thân để đọc `last` sau đó.

### Bài E — final được gán ở hai nhánh?

~~~java
final String state;
if (ok) {
    state = "YES";
} else {
    state = "NO";
}
System.out.println(state);
~~~

**Đáp án:** hợp lệ nếu `ok` là một biến boolean đã được định nghĩa. Trên mỗi đường chạy, `state` được gán **đúng một lần** trước khi đọc.

## 12. Checklist hoàn thành

- [ ] Biết khai báo biến cục bộ không đồng nghĩa được phép đọc trước khi gán.
- [ ] Nhận ra thông báo `might not have been initialized` là lỗi biên dịch.
- [ ] Phân tích được đường chạy qua `if/else`, `return` và `throw`.
- [ ] Hiểu vòng lặp có thể thực hiện 0 lần.
- [ ] Biết `x++`, `x += 1`, `x = x + 1` đều cần đọc giá trị cũ.
- [ ] Phân biệt biến cục bộ với tham số, field và phần tử mảng.
- [ ] Hiểu `final` có thể gán lần đầu sau khai báo nhưng không gán lại.
- [ ] Hiểu `var` cần initializer để suy ra kiểu.
- [ ] Biên dịch/chạy `DefiniteAssignmentDemo.java` và đối chiếu từng dòng kết quả.
- [ ] Chọn giá trị khởi tạo theo yêu cầu thực, không tùy tiện để tránh lỗi compiler.

**Kết luận:** Java yêu cầu biến cục bộ phải **được gán chắc chắn trên mọi đường có thể đi tới nơi đọc**. `javac` kiểm tra điều này trước khi chạy; field và phần tử mảng có giá trị mặc định nhưng biến cục bộ thì không được đọc khi chưa gán. Hiểu definite assignment giúp viết luồng `if/else`, vòng lặp và `final` chính xác mà không cần khởi tạo giả tạo.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="variables-and-constants-005">Giá trị mặc định của trường và phần tử mảng</a>

<details>
<summary>Xem chi tiết</summary>

# Giá trị mặc định của trường và phần tử mảng trong Java

> **Java core — language-basics / Biến và hằng**  
> **Yêu cầu:** JDK 17 trở lên.  
> **Mục tiêu:** Biết Java tự cấp giá trị nào cho field và phần tử mảng; phân biệt `static`/instance field, primitive/reference, mảng nhiều chiều và biến cục bộ; tránh nhầm `null` với đối tượng đã được cấp phát.

## 1. Những vị trí nào có giá trị mặc định?

Java tự khởi tạo các vị trí lưu trữ sau theo **kiểu khai báo** của chúng:

- **Instance field:** mỗi object mới có bộ field riêng, ban đầu nhận giá trị mặc định; sau đó field initializer và constructor có thể thay đổi.
- **Static field:** thuộc lớp, nhận giá trị mặc định trước khi chạy các khai báo khởi tạo và static initializer tương ứng của lớp.
- **Phần tử mảng:** ngay khi mảng mới được cấp phát bằng `new`, mọi phần tử **thực sự tồn tại** đều có giá trị mặc định. Quy tắc này đúng dù biến giữ tham chiếu mảng nằm trong method.

Ví dụ:

~~~java
class Account {
    int points;         // 0
    boolean active;     // false
    String owner;       // null
    static long total;  // 0L
}
~~~

Không cần tự viết `= 0` hoặc `= null` cho các field trên. Tuy nhiên, giá trị mặc định chỉ là **trạng thái ban đầu**; nếu có initializer hoặc constructor gán lại, giá trị được nhìn thấy sau khi tạo object sẽ khác.

## 2. Bảng giá trị mặc định theo kiểu

| Kiểu field / phần tử | Giá trị mặc định | Diễn giải |
| --- | --- | --- |
| `byte`, `short`, `int` | `0` | Số nguyên không |
| `long` | `0L` | Số nguyên không kiểu `long` |
| `float` | `0.0f` | Số thực dương 0.0 |
| `double` | `0.0d` | Số thực dương 0.0 |
| `char` | `'\u0000'` | Mã UTF-16 bằng 0 (NUL), **không phải** ký tự `'0'` hoặc dấu cách |
| `boolean` | `false` | Giá trị logic sai |
| Mọi **kiểu tham chiếu** | `null` | Chưa có object được tham chiếu |

Kiểu tham chiếu gồm `String`, các class khác, interface và **chính kiểu mảng** như `int[]`. Vì vậy `String[]` chứa phần tử mặc định `null`, chứ không tự tạo `""`. `Integer[]` chứa `null`, **không** tự tạo các wrapper `Integer.valueOf(0)`.

Khi in trực tiếp ký tự mặc định của `char`, bạn khó quan sát được vì nó là NUL. Hãy ép sang `int` để thấy mã **0**: `System.out.println((int) character);`.

## 3. Chương trình Java 17+ độc lập

Tạo file **DefaultValuesDemo.java**:

~~~java
import java.util.Arrays;

public class DefaultValuesDemo {
    static int sharedCount;
    static boolean enabled;

    int quantity;
    double price;
    char initial;
    String label;

    public static void main(String[] args) {
        DefaultValuesDemo first = new DefaultValuesDemo();
        DefaultValuesDemo second = new DefaultValuesDemo();

        int[] scores = new int[3];
        boolean[] flags = new boolean[2];
        char[] letters = new char[1];
        String[] names = new String[2];
        Integer[] wrappers = new Integer[1];

        int[][] partial = new int[2][];
        int[][] complete = new int[2][3];

        System.out.println("static=" + sharedCount + "," + enabled);
        System.out.println("instance=" + first.quantity + ","
                + first.price + "," + (int) first.initial + ","
                + first.label);
        System.out.println("scores=" + Arrays.toString(scores));
        System.out.println("flags=" + Arrays.toString(flags));
        System.out.println("charCode=" + (int) letters[0]);
        System.out.println("names=" + Arrays.toString(names));
        System.out.println("wrapper=" + wrappers[0]);
        System.out.println("partialRow=" + partial[0]);
        System.out.println("completeRow="
                + Arrays.toString(complete[0]));

        first.quantity = 7;
        scores[0] = 9;
        System.out.println("objects=" + first.quantity + ","
                + second.quantity);
        System.out.println("scoresAfter=" + Arrays.toString(scores));
    }
}
~~~

Mở terminal tại thư mục chứa file rồi chạy (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 DefaultValuesDemo.java
java DefaultValuesDemo
~~~

**Expected output:**

~~~text
static=0,false
instance=0,0.0,0,null
scores=[0, 0, 0]
flags=[false, false]
charCode=0
names=[null, null]
wrapper=null
partialRow=null
completeRow=[0, 0, 0]
objects=7,0
scoresAfter=[9, 0, 0]
~~~

Giải thích: field `static` có giá trị 0/false; mỗi object `DefaultValuesDemo` mới có field `quantity` bằng 0, `price` bằng 0.0, `initial` bằng NUL và `label` bằng `null`. Các mảng primitive mới có phần tử 0/false/NUL, trong khi mảng `String` và `Integer` có phần tử `null`.

Sau phép gán `first.quantity = 7`, object `second` vẫn giữ `quantity = 0`: mỗi object có field instance riêng. Sau `scores[0] = 9`, hai vị trí còn lại của mảng vẫn là 0.

## 4. Mảng nhiều chiều: chỉ khởi tạo các mảng thật sự được cấp phát

Hãy phân biệt:

~~~java
int[][] partial = new int[2][];
int[][] complete = new int[2][3];
~~~

- `partial` là **mảng ngoài gồm hai tham chiếu kiểu `int[]`**; hai tham chiếu ấy mặc định `null` vì chưa cấp phát hàng con. Do đó `partial[0]` là `null`.
- `complete` đã cấp phát mảng ngoài và hai hàng con `int[3]`. Sáu phần tử số nguyên đều mặc định bằng **0**.
- Nếu chạy `partial[0][0]` trước khi gán `partial[0] = new int[3];`, chương trình sẽ gặp `NullPointerException`.

Không nhầm `new int[0]` với mảng `null`: `new int[0]` là một object mảng **có độ dài 0**, nhưng không có phần tử nào để đọc giá trị mặc định.

## 5. Biến cục bộ KHÔNG có giá trị mặc định để đọc

Đây là ngoại lệ quan trọng với người mới học. Biến cục bộ bên trong phương thức phải được **gán chắc chắn trước khi đọc** (*definite assignment*):

~~~java
void example() {
    int count;
    // System.out.println(count); // Lỗi biên dịch nếu bỏ comment.
    count = 3;
    System.out.println(count); // 3
}
~~~

Không có quy tắc “biến cục bộ `int` chưa gán sẽ đọc ra 0”. Compiler có thể báo `variable count might not have been initialized`.

Tuy nhiên, **mảng được tạo từ một biến cục bộ** vẫn được gán giá trị mặc định cho từng phần tử:

~~~java
int[] values = new int[2];
System.out.println(values[0]); // 0
~~~

Ở đây biến cục bộ `values` đã được gán tham chiếu tới mảng mới; còn `values[0]` nhận giá trị mặc định 0 từ cơ chế cấp phát mảng. Hai quy tắc không mâu thuẫn.

## 6. Khởi tạo tường minh và `final`

Với field initializer/constructor, giá trị cuối sau khi tạo object có thể khác mặc định:

~~~java
class Ticket {
    int count = 5;

    Ticket() {
        count = 8;
    }
}
~~~

Khi đọc `new Ticket().count` sau constructor, giá trị là **8**. Trước các gán tường minh, cơ chế khởi tạo mặc định đã thiết lập 0.

Field `final` chưa có initializer (blank final) vẫn phải được gán theo quy tắc *definite assignment* thích hợp, ví dụ trong constructor:

~~~java
class Product {
    final int id;

    Product(int id) {
        this.id = id;
    }
}
~~~

`final` ngăn gán lại field sau khi khởi tạo hợp lệ, nhưng không biến object được giữ qua một tham chiếu `final` thành bất biến. Nó cũng không khiến phần tử mảng tự có giá trị khác mặc định.

## 7. Bẫy thường gặp và luyện tập

| Tình huống | Hiểu đúng |
| --- | --- |
| `String[] a = new String[2];` | Các phần tử là **`null`**, không phải `""` |
| `Integer[] a = new Integer[2];` | Các phần tử là `null`; unbox `a[0]` sang `int` gây `NullPointerException` |
| `int[] a = new int[2];` | Hai số 0 |
| `int[][] a = new int[2][];` | Hai hàng chưa cấp phát, đều `null` |
| `int[][] a = new int[2][3];` | Hai hàng đã cấp phát, sáu số 0 |
| `char` mặc định | NUL với mã 0, không phải ký tự in được `'0'` |
| `String label;` là field | `null`; gọi `label.length()` trước khi gán object có thể gây `NullPointerException` |

**Bài tập 1:** Tạo class có field `byte`, `float`, `char`, `boolean`, `String` chưa gán. In giá trị; với `char`, ép sang `int` để xác nhận mã 0.

**Bài tập 2:** In `Arrays.toString(new String[3])`. Sau đó gán phần tử thứ hai thành `"Java"` và in lại: phần tử nào vẫn `null`?

**Bài tập 3:** Tạo `int[][] rows = new int[2][];`. Dự đoán `rows[0]`; sau đó gán `rows[0] = new int[2];` rồi in `rows[0][0]`.

**Bài tập 4:** Giải thích vì sao `int local; System.out.println(local);` không biên dịch, nhưng `int[] array = new int[1]; System.out.println(array[0]);` in 0.

### Ghi nhớ

**Field và phần tử mảng** có giá trị mặc định theo kiểu: primitive nhận giá trị 0/0.0/`false`/NUL tương ứng; tham chiếu nhận `null`. **Biến cục bộ phải được gán chắc chắn trước khi đọc.** Các gán ở initializer, constructor và sau khi cấp phát có thể thay đổi giá trị mặc định.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="variables-and-constants-006">Khai báo nhiều biến và những cách viết dễ gây nhầm</a>

<details>
<summary>Xem chi tiết</summary>

# Khai báo nhiều biến và những cách viết dễ gây nhầm — Java 17+

Java cho phép **khai báo nhiều biến trong cùng một câu lệnh** nếu chúng có kiểu phù hợp. Cách viết này ngắn nhưng dễ gây hiểu nhầm: dấu `=` chỉ khởi tạo **biến được gắn với nó**, dấu `[]` có thể chỉ áp dụng cho **một tên**, và `var` không hỗ trợ danh sách nhiều biến trong một khai báo.

Mục tiêu: đọc đúng từng biến được khai báo, dự đoán kiểu và giá trị của chúng, hiểu lỗi *definite assignment*, rồi chọn cách viết dễ bảo trì.

## 1. Đọc đúng từng tên và biểu thức khởi tạo

~~~java
int width = 3, height = 5; // Hai biến int, đều đã khởi tạo.
int x, y = 10;             // x CHƯA được gán; chỉ y bằng 10.
int start = 2, end = start + 4; // end bằng 6.
~~~

Hình thức chung là:

~~~text
kiểu tên1 [= biểu_thức1], tên2 [= biểu_thức2], ... ;
~~~

Mỗi tên có thể có hoặc không có biểu thức khởi tạo riêng. **Dấu phẩy không sao chép giá trị** từ biến này sang biến khác. Các initializer được đánh giá theo thứ tự khai báo từ trái sang phải khi câu lệnh được thực thi; một tên đã được khởi tạo trước đó có thể được dùng trong initializer phía sau.

| Câu lệnh | Hiểu đúng |
| --- | --- |
| `int a = 1, b = 2;` | `a = 1`, `b = 2` |
| `int a, b = 2;` | `a` chưa gán; chỉ `b = 2` |
| `int a = 2, b;` | `a = 2`; `b` chưa gán |
| `int a = 2, b = a + 3;` | `a = 2`; `b = 5` |
| `double x = 1.5, y = 2;` | Hai biến `double`; `y` được chuyển thành `2.0` |
| `final int a = 1, b = 2;` | Cả hai đều `final`; không thể gán lại |
| `int a = 1; double b = 2.0;` | Hai kiểu khác nhau, cần khai báo riêng |

### Khai báo khác với gán giá trị

Ở biến cục bộ:

~~~java
int a, b = 5;
// System.out.println(a); // Sai: a chưa được gán chắc chắn.
a = b + 1;
System.out.println(a); // 6
~~~

Java kiểm tra **definite assignment**: biến cục bộ phải được gán chắc chắn trước khi đọc. Đừng suy luận rằng `int a, b = 5;` sẽ gán `5` cho cả hai.

Nếu muốn gán cùng một giá trị, có thể viết:

~~~java
int a, b;
a = b = 5; // Gán cho b trước, rồi gán kết quả cho a.
~~~

Tuy nhiên, khi khởi tạo biến trong mã sản phẩm, `int a = 5, b = 5;` hoặc hai khai báo riêng thường dễ hiểu hơn một phép gán nối chuỗi.

## 2. Chương trình Java 17+ chạy được

Tạo tệp **`MultipleVariablesDemo.java`**:

~~~java
public class MultipleVariablesDemo {
    public static void main(String[] args) {
        int first = 2, second = first + 3;

        int later, ready = 7;
        later = 9;

        int[] left = {4, 5}, right = {6, 7};

        // Cú pháp hợp lệ nhưng dễ nhầm:
        int legacyArray[] = {8, 9}, scalar = 10;

        int x, y;
        x = y = 3;

        var inferred = "OK"; // Một biến trong một khai báo var.

        System.out.println("first=" + first + ", second=" + second);
        System.out.println("later=" + later + ", ready=" + ready);
        System.out.println("arrays=" + left.length + "," + right.length);
        System.out.println(
                "legacyArrayLength=" + legacyArray.length
                + ", scalar=" + scalar);
        System.out.println("chain=" + x + "," + y);
        System.out.println("inferred=" + inferred);

        for (int i = 0, j = 3; i < j; i++, j--) {
            System.out.println("pair=" + i + ":" + j);
        }
    }
}
~~~

Mở PowerShell (Windows) hoặc Bash (Linux/macOS) tại thư mục chứa tệp. Sử dụng JDK 17 trở lên:

~~~bash
java -version
javac -version
javac -encoding UTF-8 MultipleVariablesDemo.java
java MultipleVariablesDemo
~~~

**Expected output:**

~~~text
first=2, second=5
later=9, ready=7
arrays=2,2
legacyArrayLength=2, scalar=10
chain=3,3
inferred=OK
pair=0:3
pair=1:2
~~~

Giải thích điểm quan trọng:

1. `second` đọc `first` sau khi `first` đã khởi tạo, nên nhận `5`.
2. `later` chỉ có giá trị sau câu lệnh `later = 9;`; `ready` nhận `7` ngay lúc khai báo.
3. `left` và `right` đều là mảng `int[]`, mỗi mảng dài 2.
4. `legacyArray` là mảng, nhưng `scalar` là **một `int` duy nhất**, dù chúng cùng đứng trong một câu lệnh khai báo.
5. `x = y = 3` đặt cả `x` và `y` bằng 3. Không nên hiểu đó là phép so sánh.
6. Trong vòng `for`, `i` và `j` có cùng kiểu `int`; hai biến được cập nhật sau mỗi lượt. Khi `i == 2` và `j == 1`, điều kiện `i < j` sai nên dừng.

## 3. Bẫy đặc biệt: dấu `[]` có thể chỉ áp dụng cho một tên

Hai cách viết sau **khác nhau**:

~~~java
int[] a = {1}, b = {2}; // a và b đều là int[].
int c[] = {3}, d = 4;   // c là int[], d là int.
~~~

Trong Java, dấu `[]` có thể viết gần **kiểu** hoặc ngay **sau tên biến**. Khi đứng sau tên, chúng chỉ gắn với biến đó. Thậm chí:

~~~java
int[] one = {1}, two[] = {{2, 3}};
~~~

Ở đây `one` có kiểu `int[]`, còn `two` có kiểu `int[][]`. Cú pháp hợp lệ nhưng không dễ quan sát kiểu nếu chỉ đọc phần đầu câu lệnh.

**Khuyến nghị:** viết ngoặc vuông cạnh kiểu và khai báo từng biến mảng riêng biệt:

~~~java
int[] numbers = {1, 2};
int[] others = {3, 4};
int[][] grid = {{1, 2}, {3, 4}};
~~~

Cách này giảm nguy cơ đọc nhầm chiều mảng, đặc biệt khi người khác chỉnh sửa mã.

## 4. `var` không khai báo nhiều biến cùng lúc

Từ Java 10 (vẫn áp dụng trên Java 17), `var` cho phép compiler suy luận **kiểu của một biến cục bộ** từ initializer:

~~~java
var count = 3;     // int
var title = "Java"; // String
~~~

Nhưng các cách viết sau **không hợp lệ**:

~~~java
// var a = 1, b = 2; // Sai: không cho nhiều declarator với var.
// var value;        // Sai: thiếu initializer để suy luận kiểu.
// var unknown = null; // Sai: không suy luận kiểu từ null đơn lẻ.
~~~

Nếu muốn nhiều biến cùng kiểu trong một câu lệnh, hãy viết kiểu tường minh:

~~~java
int a = 1, b = 2;
~~~

Nếu các biến có ý nghĩa khác nhau, ưu tiên tách:

~~~java
int retryCount = 1;
int maxRetries = 3;
~~~

`var` **không làm biến có kiểu động**: kiểu được compiler xác định ngay khi biên dịch. Đổi `count` thành chuỗi sau khi khai báo `var count = 3;` vẫn gây lỗi kiểu.

## 5. Các cách viết dễ gây lỗi

| Viết như sau | Vấn đề | Sửa hoặc giải thích |
| --- | --- | --- |
| `int a, b = 5; System.out.println(a);` | Lỗi biên dịch: `a` chưa được gán | Gán `a` trước khi đọc |
| `int a = 1, b = a + 1;` | **Hợp lệ**, `b = 2` | Thứ tự initializer từ trái sang phải |
| `int a = b + 1, b = 2;` | Lỗi: dùng `b` trước phần khai báo/khởi tạo phù hợp | Khai báo và gán `b` trước |
| `int a = 1, b = 2.5;` | Lỗi kiểu: `b` là `int` | Tách kiểu: `int a = 1; double b = 2.5;` |
| `int[] a, b;` | **Cả hai là mảng** | Dùng `int[]` cạnh kiểu |
| `int a[], b;` | `a` là mảng, `b` là số nguyên | Tránh ngoặc vuông sau tên |
| `var a = 1, b = 2;` | Lỗi: `var` không cho nhiều declarator | Tách hai khai báo |
| `int a = 1; int a = 2;` trong cùng phạm vi | Lỗi khai báo trùng tên | Dùng tên khác hoặc gán lại `a = 2;` |
| `for (int i = 0, j = 5; ... )` | **Hợp lệ** nếu dùng cùng kiểu | Cả `i` và `j` là `int` |

**Lưu ý về dấu phẩy:** trong phần *khởi tạo* của vòng `for`, bạn thường gặp nhiều biến cùng kiểu (`int i = 0, j = 5`). Trong phần **cập nhật** của `for`, `i++, j--` là một danh sách biểu thức cập nhật, **không phải khai báo biến**.

### Side effect làm một dòng ngắn trở nên khó đọc

~~~java
int x = 1, y = ++x; // x = 2, y = 2.
~~~

Cú pháp hợp lệ, nhưng `++x` thay đổi giá trị x ngay trong lúc khởi tạo y. Với logic phức tạp, nên tách ra:

~~~java
int x = 1;
x++;
int y = x;
~~~

Code dài thêm một chút nhưng dễ kiểm tra, đặt breakpoint và tránh sai ý định hơn.

## 6. Bài tập tự luyện

**Bài 1 — Dự đoán:** Với `int a = 3, b = a * 2, c;`, biến nào đã có giá trị ngay sau dòng khai báo? Có thể in `c` ngay không?

*Đáp án:* `a = 3`, `b = 6`; `c` chưa được gán nên chưa thể đọc.

**Bài 2 — Kiểu mảng:** Cho `int[] first = {1}, second[] = {{2}};`. Hai biến có kiểu gì?

*Đáp án:* `first` là `int[]`, `second` là `int[][]`.

**Bài 3 — Sửa `var`:** Viết lại `var width = 2, height = 3;` thành hai câu khai báo dùng `var` và một câu khai báo dùng kiểu tường minh.

*Đáp án:* `var width = 2; var height = 3;` hoặc `int width = 2, height = 3;`.

**Bài 4 — Vòng `for`:** Với `for (int i = 1, j = 4; i < j; i++, j--)`, có bao nhiêu lượt chạy thân?

*Đáp án:* Hai lượt: lúc `(i,j) = (1,4)` và `(2,3)`, rồi dừng khi `(3,2)`.

**Bài 5 — Ưu tiên tính rõ ràng:** Trong mã nghiệp vụ, nên chọn `int[] scores = ...; int[] bonuses = ...;` hay `int scores[], bonuses[];`?

*Gợi ý đáp án:* Cách đầu dễ đọc và giảm bẫy thay đổi kiểu từng biến. Cách thứ hai có thể hợp lệ nhưng khó bảo trì hơn.

## Checklist

- [ ] Biết `int a, b = 5;` chỉ gán `5` cho `b`.
- [ ] Phân biệt khai báo biến với câu lệnh gán và lỗi đọc local chưa khởi tạo.
- [ ] Hiểu các initializer được đánh giá theo thứ tự từ trái sang phải.
- [ ] Nhận diện `int[] a, b` khác `int a[], b`.
- [ ] Biết `var` không hỗ trợ nhiều declarator trong một khai báo.
- [ ] Đọc đúng nhiều biến trong vòng `for`.
- [ ] Ưu tiên những khai báo ngắn, rõ kiểu, ít side effect.

**Ghi nhớ:** Khai báo nhiều biến hợp lệ không đồng nghĩa **mọi biến đều đã được gán**, và cùng một đầu câu lệnh không luôn đồng nghĩa mọi tên có đúng cùng kiểu mảng. Khi mỗi biến mang ý nghĩa riêng, **tách khai báo thành từng dòng** thường là cách rõ ràng và an toàn nhất.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="variables-and-constants-007">final trên biến và quy tắc chỉ gán một lần</a>

<details>
<summary>Xem chi tiết</summary>

# `final` trên biến và quy tắc chỉ gán một lần trong Java 17+

## 1. Ý nghĩa chính xác của `final` đối với biến

Một biến Java bình thường có thể được gán lại nếu kiểu và phạm vi cho phép. Khi đặt **`final`** vào khai báo, biến chỉ có thể nhận **một phép gán hợp lệ trên mỗi đường thực thi**; sau khi đã được gán, không thể gán lại chính biến đó.

~~~java
int normal = 10;
normal = 20; // Hợp lệ

final int limit = 10;
// limit = 20; // LỖI BIÊN DỊCH: cannot assign a value to final variable limit
~~~

Điều này áp dụng cho biến kiểu nguyên thủy như `int`, `boolean` và biến kiểu tham chiếu như `StringBuilder`, mảng hoặc object. Nhưng **`final` khóa khả năng gán lại biến, không tự biến đối tượng mà biến trỏ tới thành bất biến**.

| Cách viết | Ý nghĩa | Kết quả |
|---|---|---|
| `int x = 1; x = 2;` | Biến thường, gán lại được | Hợp lệ |
| `final int x = 1;` | Biến đã gán một lần | Hợp lệ |
| `final int x = 1; x = 2;` | Gán lần hai cho biến final | Lỗi compile |
| `final int x; x = 1;` | Khai báo trước, gán lần đầu sau | Hợp lệ |
| `final int x; System.out.println(x);` | Đọc trước khi được gán | Lỗi compile |
| `final StringBuilder b = new StringBuilder(); b.append("A");` | Thay đổi trạng thái object, không gán lại b | Hợp lệ |

**Quy tắc kép:** biến `final` phải **được gán chắc chắn trước khi đọc** (*definitely assigned*) và **chưa từng được gán trước mỗi phép gán cho nó** (*definitely unassigned*). Bộ kiểm tra của `javac` xét các đường đi của chương trình.

## 2. Khởi tạo ngay hoặc gán sau: blank final

Cách thông dụng nhất là vừa khai báo vừa khởi tạo:

~~~java
final double taxRate = 0.10;
final boolean production = true;
final String title = "Java";
~~~

Java cũng cho phép **blank final local variable**: khai báo mà chưa có giá trị ban đầu, rồi gán sau đó:

~~~java
static int calculate(boolean premium) {
    final int price;
    if (premium) {
        price = 200;
    } else {
        price = 100;
    }
    return price;
}
~~~

Dù trong source có **hai câu lệnh gán** khác nhau, ở **mỗi lần chạy** chỉ **một nhánh** thực hiện. Vì thế mỗi đường tới `return price` đều gán biến đúng một lần, nên chương trình hợp lệ.

~~~java
// Sai: một đường đi chưa gán price
final int price;
if (premium) {
    price = 200;
}
// System.out.println(price); // Không thể đọc khi premium có thể false
~~~

Nếu hai phép gán có thể xảy ra **liên tiếp** trên cùng đường đi, compiler sẽ từ chối:

~~~java
// Sai:
final int number;
number = 5;
// number = 5; // Dù giá trị giống nhau vẫn là phép gán thứ hai
~~~

**Chính xác hơn câu “final bắt buộc được gán một lần”:** không được gán **quá một lần trên một đường thực thi**; nếu cần đọc giá trị thì phải bảo đảm đã được gán. Với blank final local không hề được đọc, Java không buộc bạn thêm một phép gán vô nghĩa.

## 3. Vòng lặp: biến final không được cập nhật lặp đi lặp lại

Trong vòng lặp thông thường, biến đếm thường phải thay đổi:

~~~java
for (int i = 0; i < 3; i++) {
    System.out.println(i);
}
~~~

Không thể chỉ thêm `final` vào `i`:

~~~java
// Sai: phần i++ cố gán lại biến final
// for (final int i = 0; i < 3; i++) { }
~~~

Một **blank final** được gán bên trong thân một `while` cũng thường không hợp lệ nếu vòng lặp có thể chạy nhiều lần: compiler không bảo đảm mỗi lần gán xảy ra trên đường mà biến vẫn chưa được gán.

Ngược lại, **enhanced for** có thể khai báo biến phần tử `final`:

~~~java
int sum = 0;
for (final int value : new int[] {1, 2, 3}) {
    sum += value;
}
System.out.println(sum); // 6
~~~

Mỗi lần lặp có một biến phần tử cho lần lặp đó; trong **thân vòng lặp**, bạn không được viết `value = 99;`. Bạn vẫn có thể gán lại `sum` vì `sum` không phải `final`.


## 4. `final` khóa phép gán biến, không khóa object

~~~java
final int count = 5;
// count++; // Lỗi: cố gán lại biến primitive

final int[] scores = {10, 20};
scores[0] = 99; // Hợp lệ: thay phần tử của object mảng
// scores = new int[] {30}; // Lỗi: cố gán lại biến scores
~~~

Với primitive, không gán lại được biến cũng có nghĩa giá trị primitive giữ nguyên. Với reference, **tham chiếu của biến** giữ nguyên nhưng object vẫn có thể mutable. Điều này không tự cấp an toàn đa luồng hay bất biến sâu; bài riêng về tham chiếu `final` sẽ chứng minh bằng alias, `StringBuilder` và collection. Một biến `final String label = null;` vẫn hợp lệ, nhưng đã gán `null` thì không thể gán lại.

## 5. final trên tham số method và biến cục bộ

Có thể đặt `final` cho tham số:

~~~java
static int doubleOf(final int value) {
    // value = 7; // Lỗi compile
    return value * 2;
}
~~~

`final` không ngăn method **đọc** giá trị được truyền vào. Nó chỉ không cho phép gán lại tham số `value` trong thân method.

Với tham số tham chiếu:

~~~java
static void addMark(final StringBuilder text) {
    text.append("!");      // Hợp lệ, thay đổi object
    // text = new StringBuilder(); // Sai, gán lại tham số
}
~~~

Đây là một công cụ giúp diễn tả chủ ý “tham số này không bị thay thế”. Nó **không** ngăn bên gọi giữ tham chiếu đến cùng object và nhìn thấy thay đổi trạng thái.

## 6. final field: cần hoàn tất khởi tạo đúng chỗ

Một field `final` có thể được khởi tạo tại nơi khai báo:

~~~java
class Config {
    final int maxAttempts = 3;
}
~~~

Hoặc để trống lúc khai báo rồi khởi tạo trong constructor, thường gọi là **blank final instance field**:

~~~java
class User {
    private final int id;

    User(int id) {
        this.id = id; // Constructor gán field lần đầu
    }

    int id() {
        return id;
    }
}
~~~

**Mọi constructor hợp lệ phải bảo đảm field final chưa khởi tạo được gán trước khi constructor hoàn thành bình thường.** Nếu lớp có nhiều constructor, phải thiết kế các đường khởi tạo cho thống nhất; không được bỏ một đường hoặc gán hai lần. Có thể dùng instance initializer khi phù hợp, nhưng không nên cố gán lại field đã khởi tạo.

Với **`static final`**, giá trị có thể được gán ngay khi khai báo hoặc thông qua **static initializer**:

~~~java
class Environment {
    static final String APP_NAME;
    static {
        APP_NAME = "Study";
    }
}
~~~

Một `static final` field chưa được gán phải được khởi tạo hợp lệ trong quá trình khởi tạo tĩnh; không thể chờ đến lúc gọi `main` rồi mới tùy ý gán cho nó.

**Khác biệt với field bình thường:** field `int` mặc định có giá trị 0, nhưng một blank final field vẫn phải tuân thủ các yêu cầu gán rõ ràng khi hoàn tất khởi tạo. Không lấy giá trị mặc định trong JVM làm lý do bỏ qua khởi tạo final field.

## 7. `final` không có nghĩa lúc nào cũng là compile-time constant

Một giá trị được gán một lần **không nhất thiết là hằng số lúc biên dịch**:

~~~java
final int fixed = 10;                    // Biểu thức hằng: 10
final int inputLength = "hello".length();// Giá trị từ lời gọi method
~~~

`fixed` là ví dụ về biến `final` kiểu primitive được khởi tạo bằng một **constant expression**. Còn `inputLength` cần lời gọi method; `final` chỉ đảm bảo biến không được gán lại, **không biến method call thành constant expression**.

Trong Java, một *constant variable* có định nghĩa chặt hơn: biến `final` thuộc kiểu nguyên thủy hoặc `String`, khởi tạo bằng một constant expression. Nhờ phân biệt này, bạn sẽ hiểu vì sao một số vị trí đòi hằng số lúc biên dịch (như `case` label truyền thống) không chấp nhận mọi biến `final`.

Ví dụ:

~~~java
final int ten = 10;
final int eleven = Integer.parseInt("11");

// Hợp lệ trong switch truyền thống:
int x = 10;
switch (x) {
    case ten -> System.out.println("TEN");
    default -> System.out.println("OTHER");
}
// Không thể dùng eleven làm case label hằng số tại compile time.
~~~

Đừng đánh đồng `final`, `static final` và “object bất biến”: đó là các khái niệm liên quan nhưng **không đồng nghĩa**.

## 8. final và lambda: effectively final

Java 17 cho phép lambda/anonymous class **capture** một biến cục bộ nếu biến đó được khai báo `final` **hoặc là effectively final** (không được gán lại sau lần khởi tạo/gán hợp lệ).

~~~java
int factor = 4; // Không ghi final, nhưng không gán lại
java.util.function.IntUnaryOperator times =
        value -> value * factor;
System.out.println(times.applyAsInt(3)); // 12
~~~

Dù `factor` không ghi `final`, compiler thấy nó không bị gán lại nên cho phép capture.

Nếu bạn thêm `factor = 5;` sau khai báo, biến không còn effectively final và lời gọi lambda sử dụng nó sẽ khiến chương trình không biên dịch. Cách xử lý là thiết kế giá trị capture cố định, hoặc nếu thật sự cần trạng thái thay đổi thì mô hình hóa một object quản lý trạng thái phù hợp. Không nên bọc biến mutable tùy tiện vào mảng chỉ để qua kiểm tra compiler.

## 9. Chương trình Java 17 chạy độc lập

Tạo tệp chính xác **`FinalOnceDemo.java`**:

~~~java
public final class FinalOnceDemo {
    private static final int MAX_RETRIES = 3;
    private static final String APP_NAME;

    static {
        APP_NAME = "Study";
    }

    private final int id;
    private final StringBuilder label;

    private FinalOnceDemo(int id) {
        this.id = id;
        this.label = new StringBuilder("item");
    }

    static String classify(int score) {
        final String outcome; // Blank final local
        if (score >= 50) {
            outcome = "PASS";
        } else {
            outcome = "FAIL";
        }
        return outcome;
    }

    static int twice(final int value) {
        return value * 2;
    }

    public static void main(String[] args) {
        final int attempts = 2;
        System.out.println("attempts=" + attempts);
        System.out.println("max=" + MAX_RETRIES);
        System.out.println("app=" + APP_NAME);
        System.out.println("pass=" + classify(70));
        System.out.println("fail=" + classify(40));

        FinalOnceDemo object = new FinalOnceDemo(7);
        object.label.append("-updated"); // Object mutable
        System.out.println("id=" + object.id);
        System.out.println("label=" + object.label);

        final int[] counts = {1, 2};
        counts[0] = 9; // Không gán lại biến counts
        System.out.println("array=" + counts[0] + "," + counts[1]);

        final StringBuilder text = new StringBuilder("A");
        text.append("B");
        System.out.println("ref=" + text);
        System.out.println("twice=" + twice(5));

        int sum = 0;
        for (final int value : new int[] {1, 2, 3}) {
            sum += value;
        }
        System.out.println("sum=" + sum);

        int factor = 4; // Effectively final: không gán lại
        java.util.function.IntUnaryOperator multiply =
                number -> number * factor;
        System.out.println("lambda=" + multiply.applyAsInt(3));
    }
}
~~~

Tại thư mục chứa tệp, dùng **JDK 17 trở lên**.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 FinalOnceDemo.java
java -cp . FinalOnceDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\FinalOnceDemo.java
java -cp . FinalOnceDemo
~~~

**Đầu ra kỳ vọng:**

~~~text
attempts=2
max=3
app=Study
pass=PASS
fail=FAIL
id=7
label=item-updated
array=9,2
ref=AB
twice=10
sum=6
lambda=12
~~~

Những điểm cần tự đối chiếu:

1. `classify` gán một lần ở **đúng một** trong hai nhánh, nên `final outcome` hợp lệ.
2. `id` và `label` là instance final fields được gán khi chạy constructor.
3. `APP_NAME` là static final field được gán trong static initializer.
4. `object.label.append(...)`, `counts[0] = 9` và `text.append(...)` **không phải phép gán lại biến final**.
5. `factor` là effectively final nên có thể được lambda sử dụng.

Đây là một ví dụ duy nhất có thể lưu, biên dịch và chạy độc lập; không cần thư viện hoặc dự án build bên ngoài.

## 10. Các lỗi điển hình và cách xử lý

| Hiện tượng | Vì sao? | Sửa thế nào? |
|---|---|---|
| `final int x = 1; x = 2;` | Gán lần thứ hai | Chỉ gán một lần, hoặc bỏ `final` nếu phải thay đổi |
| `final int x; println(x);` | Đọc blank final chưa gán | Gán chắc chắn trước khi đọc |
| `final int x; if (c) x = 1; println(x);` | Nhánh `c=false` chưa gán | Thêm nhánh `else`/đường xử lý phù hợp |
| `final int x; x = 1; x = 1;` | Gán lại dù cùng giá trị | Bỏ phép gán thứ hai |
| `final int i = 0;` rồi `i++` | Phép tăng phải gán lại `i` | Dùng biến đếm không final |
| Constructor quên gán một blank final field | Không bảo đảm khởi tạo trên mọi đường hoàn thành bình thường | Khởi tạo field hoặc sửa constructor |
| `final List ...` rồi gọi API thêm phần tử | Có thể **hợp lệ**; chỉ object đổi | Dùng collection bất biến nếu cần bất biến thật |
| Lambda dùng biến cục bộ đã gán lại | Không còn effectively final | Tách dữ liệu capture cố định hoặc thiết kế lại |
| `final String s = null;` rồi muốn gán s thành chuỗi | `s` đã được gán null một lần | Chọn giá trị đúng lúc khởi tạo; không dùng null làm giá trị tạm |
| Dùng final method-local như constant expression dù initializer gọi method | Final không tự biến kết quả thành hằng compile-time | Dùng constant expression ở vị trí yêu cầu |

Một lỗi kiểu `cannot assign a value to final variable` thường có nghĩa compiler đã nhận diện **biến cụ thể bị gán lại**, không phải cấm thay đổi mọi thứ liên quan đến đối tượng.

## 11. Bài tập có đáp án

### Bài A — final gán ở if/else

~~~java
final String state;
if (ok) {
    state = "ON";
} else {
    state = "OFF";
}
System.out.println(state);
~~~

**Đáp án:** hợp lệ nếu `ok` là boolean đã khai báo. Mỗi đường chạy gán `state` một lần trước khi in.

### Bài B — phân biệt biến và object

~~~java
final StringBuilder sb = new StringBuilder("Hi");
sb.append("!");
// sb = new StringBuilder("Bye");
System.out.println(sb);
~~~

**Đáp án:** in `Hi!`; `append` hợp lệ, gán lại `sb` không hợp lệ.

### Bài C — final array có bất biến không?

~~~java
final int[] values = {1, 2};
values[0] = 7;
System.out.println(values[0]);
~~~

**Đáp án:** in `7`. Biến `values` không đổi tham chiếu nhưng phần tử mảng vẫn có thể đổi.

### Bài D — tham số final

~~~java
static int plusOne(final int x) {
    // x++; // Sai
    return x + 1;
}
~~~

**Đáp án:** `return x + 1;` hợp lệ vì không thay đổi `x`. `x++` không hợp lệ vì gán lại tham số final.

### Bài E — capture trong lambda

~~~java
int factor = 3;
java.util.function.IntUnaryOperator op = x -> x * factor;
// factor = 4;
~~~

**Đáp án:** hợp lệ nếu không thêm phép gán lại. Nếu bỏ comment của `factor = 4;`, compiler không cho lambda capture biến vì nó không còn effectively final.

## 12. Checklist ghi nhớ

- [ ] `final` cấm gán lại **biến** sau khi được gán hợp lệ, không mặc định cấm thay đổi object.
- [ ] Blank final local có thể khai báo trước, rồi gán một lần trên từng đường đi hợp lệ.
- [ ] Biến final cần được gán chắc chắn trước khi đọc.
- [ ] Câu lệnh `x++`, `x += 1`, `x = x` cũng là gán lại.
- [ ] Enhanced for có thể dùng `final` cho biến phần tử, khác với biến đếm cần `i++`.
- [ ] Blank final field được khởi tạo hợp lệ tại điểm khai báo, initializer hoặc constructor phù hợp.
- [ ] Phân biệt `final` variable với **compile-time constant** và **immutable object**.
- [ ] Hiểu final parameter và effectively final khi capture trong lambda.
- [ ] Tự biên dịch/chạy `FinalOnceDemo.java` và giải thích toàn bộ output.

**Kết luận:** `final` đặt trên biến bảo đảm **không gán lại biến sau khi đã gán một lần hợp lệ**. Java kiểm tra điều này ngay lúc biên dịch, bao gồm các nhánh điều khiển và các đường khởi tạo. Với tham chiếu, `final` cố định **giá trị tham chiếu**, không tự làm dữ liệu bên trong bất biến.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="variables-and-constants-008">Hằng tại thời điểm biên dịch và hằng static final</a>

<details>
<summary>Xem chi tiết</summary>

# Hằng tại thời điểm biên dịch và hằng `static final` trong Java 17+

Trong Java, `final` có nghĩa là **biến không được gán lại sau khi đã được gán hợp lệ**, nhưng không có nghĩa mọi giá trị `final` đều được xác định lúc biên dịch.

Đặc biệt, **`static final` không tự động đồng nghĩa với “hằng tại thời điểm biên dịch”**. Một trường `static final` có thể nhận giá trị từ phép tính cố định hoặc từ một phương thức phải chạy khi lớp được khởi tạo. Hai trường hợp có ảnh hưởng khác nhau tới kiểm tra cú pháp, khởi tạo lớp và mã đã biên dịch.

## 1. Ba khái niệm cần phân biệt

| Khái niệm | Định nghĩa thực tế | Ví dụ |
| --- | --- | --- |
| Biến `final` | Biến chỉ được gán theo quy tắc của `final`, không thể gán lại sau khi đã khởi tạo | `final int count = readCount();` |
| Trường `static final` | Thuộc lớp và không thể gán lại sau khi khởi tạo; giá trị có thể tính lúc chạy | `static final int LIMIT = Integer.parseInt("12");` |
| *Constant variable* (biến hằng tại thời điểm biên dịch) | Biến `final` có kiểu nguyên thủy hoặc `String` và được khởi tạo bằng **constant expression** | `static final int LIMIT = 3 * 4;` |

Theo định nghĩa ngôn ngữ, *constant variable* **không nhất thiết phải là `static`**: `final int x = 2 + 3;` trong phương thức cũng có thể đáp ứng định nghĩa. Tuy nhiên, các hằng dùng chung của ứng dụng thường được đặt trong trường `static final`.

**Công thức ghi nhớ:**

~~~text
final + (kiểu primitive hoặc String) + constant expression
                      │
                      ▼
              constant variable

static final nhưng thiếu một điều kiện trên
                      │
                      ▼
           không phải constant variable
~~~

“Constant expression” là biểu thức mà quy tắc Java cho phép đánh giá tại thời điểm biên dịch, ví dụ literal, một số toán tử trên hằng, phép nối chuỗi hằng hoặc tham chiếu tới constant variable khác. **Gọi phương thức thông thường không phải constant expression**, ngay cả khi phương thức luôn trả cùng một số.

## 2. Ví dụ hợp lệ và không phải hằng biên dịch

~~~java
static final int MAX_USERS = 2 * 3;              // Constant variable.
static final String TITLE = "Java " + 17;        // Constant variable.
static final double RATE = 0.05;                 // Constant variable.

static final int CONFIG = Integer.parseInt("12"); // Khởi tạo lúc chạy.
static final Integer BOXED = 12;                  // Wrapper, không phải primitive/String.
static final int[] SCORES = {4, 5};               // Mảng là reference type.
static final String BUILT = new String("Java");  // Có gọi new, không phải constant expression.
~~~

Tất cả các trường trên đều `static final`. Nhưng **chỉ ba trường đầu** thỏa định nghĩa *constant variable*.

### `static final` không có nghĩa “đối tượng không thể thay đổi”

~~~java
static final int[] NUMBERS = {1, 2};

// NUMBERS = new int[]{3, 4}; // Sai: không gán lại reference final.
NUMBERS[0] = 99;              // Hợp lệ: thay phần tử mảng.
~~~

`final` ngăn gán lại **biến tham chiếu**, không đóng băng dữ liệu của đối tượng được tham chiếu. Do đó, không nên công bố mảng `public static final` rồi nghĩ rằng bên ngoài không thể sửa phần tử của nó.

Ngoài ra, trường `static final` có thể được gán trong **static initializer** đúng quy tắc, thay vì có initializer ngay tại khai báo. Trường hợp đó thường không tạo *constant variable*; một số field `final` còn có thể được xác định bằng cấu hình khi lớp được khởi tạo.

## 3. Chương trình Java 17+ chạy được: quan sát lúc khởi tạo lớp

Tạo tệp **`CompileTimeConstantsDemo.java`**:

~~~java
public class CompileTimeConstantsDemo {
    static class Settings {
        static final int MAX_USERS = 2 * 3;
        static final String LABEL = "Java " + 17;

        static final int RUNTIME_LIMIT = Integer.parseInt("12");
        static final int[] SCORES = {4, 5};

        static {
            System.out.println("Settings initialized");
        }
    }

    public static void main(String[] args) {
        final int local = 3 * 4;

        System.out.println("maxUsers=" + Settings.MAX_USERS);
        System.out.println("label=" + Settings.LABEL);
        System.out.println("local=" + local);

        System.out.println("runtimeLimit=" + Settings.RUNTIME_LIMIT);

        Settings.SCORES[0] = 90;
        System.out.println("score0=" + Settings.SCORES[0]);
    }
}
~~~

Ở PowerShell trên Windows hoặc Bash trên Linux, mở thư mục chứa tệp và chạy:

~~~bash
java -version
javac -version
javac -encoding UTF-8 CompileTimeConstantsDemo.java
java CompileTimeConstantsDemo
~~~

**Expected output:**

~~~text
maxUsers=6
label=Java 17
local=12
Settings initialized
runtimeLimit=12
score0=90
~~~

**Vì sao dòng khởi tạo lớp xuất hiện ở giữa?**

1. `MAX_USERS` và `LABEL` là *constant variables*. Khi mã truy cập bằng biểu thức `Settings.MAX_USERS` và `Settings.LABEL`, compiler có thể dùng trực tiếp giá trị hằng; **chỉ đọc chúng không buộc `Settings` khởi tạo**.
2. `local` là biến `final int` có constant expression `3 * 4`; bản thân nó không phải trường `static`.
3. `RUNTIME_LIMIT` không phải constant variable vì initializer gọi `Integer.parseInt(...)`. Lần đầu đọc field này qua `Settings.RUNTIME_LIMIT` kích hoạt khởi tạo `Settings` (nếu lớp chưa khởi tạo). Khi đó mã khởi tạo static chạy theo thứ tự, in `Settings initialized`, rồi lệnh trong `main` mới in `runtimeLimit=12`.
4. Mảng `SCORES` được tạo trong quá trình khởi tạo lớp. Dù tham chiếu là `final`, thao tác `SCORES[0] = 90` hoàn toàn hợp lệ.

Lưu ý: việc “không khởi tạo lớp khi đọc hằng” là quy tắc đối với **truy cập constant variable**, không áp dụng cho mọi cách sử dụng lớp (`new`, gọi phương thức `static` phù hợp, hoặc đọc field `static` không phải hằng có thể kích hoạt khởi tạo).

## 4. Hệ quả thực tế: hằng có thể bị nhúng vào mã gọi

Compiler có thể **nhúng trực tiếp** giá trị của *constant variable* khi biên dịch lớp sử dụng nó. Ví dụ ở một thư viện:

~~~java
public class LibraryConfig {
    public static final int LIMIT = 10;
}
~~~

Một lớp khác:

~~~java
public class Client {
    public static void main(String[] args) {
        System.out.println(LibraryConfig.LIMIT);
    }
}
~~~

Khi biên dịch `Client` dựa trên `LIMIT = 10`, mã của `Client` có thể chứa chính giá trị 10, thay vì đọc field lúc chạy. Nếu sau này thư viện sửa `LIMIT = 20` và **chỉ biên dịch lại `LibraryConfig`**, file `Client.class` cũ có thể **vẫn in 10**.

**Cách xử lý:** khi thay đổi constant variable công khai trong thư viện, **biên dịch lại các lớp sử dụng** và phát hành lại các artifact phụ thuộc. Đây là lý do không nên tùy tiện dùng `public static final` constant variables cho những giá trị dự kiến thay đổi giữa các phiên bản triển khai.

Với `static final int LIMIT = loadFromConfiguration();` (initializer gọi phương thức), giá trị không phải *compile-time constant* và việc truy cập field diễn ra lúc chạy. Nhưng nó vẫn thường chỉ được khởi tạo **một lần cho mỗi lần khởi tạo lớp**, không tự động cập nhật khi file cấu hình thay đổi.

## 5. Khi nào compiler yêu cầu constant expression?

Một tình huống dễ kiểm chứng trong Java 17 là nhãn `case` của `switch` truyền thống:

~~~java
static final int OK = 2 * 3;                // Constant variable.
static final int DYNAMIC = Math.max(5, 6); // Không phải constant variable.

// Trong phương thức:
int status = 6;
switch (status) {
    case OK:
        System.out.println("OK"); // Hợp lệ.
        break;
    // case DYNAMIC: // Sai: không phải constant expression.
    default:
        System.out.println("Khac");
}
~~~

`case OK:` được chấp nhận vì `OK` là *constant variable*, còn `DYNAMIC` không được chấp nhận ở vị trí nhãn `case` kiểu này vì lời gọi `Math.max` không phải constant expression.

Đây là lý do một giá trị được tuyên bố `final` chưa đủ để dùng trong mọi vị trí đòi hỏi **hằng lúc biên dịch**.

## 6. Lỗi và ngộ nhận thường gặp

| Ngộ nhận/tình huống | Sự thật | Cách làm |
| --- | --- | --- |
| “Có `static final` là constant variable” | Sai: initializer và kiểu còn phải thỏa điều kiện | Xét kiểu primitive/`String` và constant expression |
| “`Integer` cũng giống `int`” | `Integer` là wrapper reference type | Dùng `int` nếu cần primitive constant variable |
| “`new String("x")` là hằng vì nội dung cố định” | `new` không phải constant expression | Nếu cần hằng biên dịch, dùng `"x"` |
| “`final int[]` là mảng bất biến” | Chỉ không thể đổi tham chiếu của biến `final` | Hạn chế quyền truy cập, dùng cấu trúc bất biến phù hợp |
| “Thay `LIMIT` trong thư viện là đủ” | Lớp sử dụng đã biên dịch có thể còn giữ giá trị cũ | Rebuild và phát hành lại mã phụ thuộc |
| “Đọc mọi field `static` đều khởi tạo lớp” | Đọc constant variable là ngoại lệ đáng chú ý | Phân biệt field hằng biên dịch và field runtime |
| “`final` luôn cho phép dùng làm nhãn `case`” | Nhãn ở ví dụ cần constant expression | Dùng literal/constant variable hợp lệ |

### Quy ước đặt tên

Hằng dùng chung thường viết chữ hoa và dấu gạch dưới, ví dụ `MAX_USERS`, `DEFAULT_TIMEOUT_SECONDS`, `APP_NAME`. Đây là **quy ước**, không phải điều kiện của compiler: `static final int maxUsers = 6;` vẫn hợp lệ và vẫn có thể là constant variable.

## 7. Bài tập ngắn

**Bài 1 — Phân loại.** Đâu là *constant variable*?

~~~java
static final int A = 1 + 2;
static final int B = Integer.parseInt("3");
static final String C = "Hi" + " Java";
static final String D = new String("Hi");
static final Integer E = 3;
~~~

*Đáp án:* `A` và `C`. `B` gọi phương thức; `D` dùng `new`; `E` là wrapper.

**Bài 2 — Đọc output.** Tại sao `Settings initialized` trong ví dụ chỉ in trước `runtimeLimit=12`, không in trước `maxUsers=6`?

*Đáp án:* Hai constant variables được dùng mà không buộc lớp `Settings` khởi tạo; đọc non-constant static field `RUNTIME_LIMIT` kích hoạt khởi tạo.

**Bài 3 — Thử tính bất biến.** Với `static final int[] VALUES = {1, 2};`, câu nào hợp lệ: `VALUES[0] = 5;` hay `VALUES = new int[]{5, 6};`?

*Đáp án:* Chỉ câu thứ nhất hợp lệ: phần tử có thể sửa, biến tham chiếu `final` không thể gán lại.

**Bài 4 — Thay đổi thư viện.** Một chương trình đã biên dịch dùng `public static final int VERSION = 1;`. Tại sao thay trong thư viện thành 2 mà không rebuild client có thể vẫn in 1?

*Đáp án:* Giá trị constant variable có thể đã được nhúng vào mã client khi biên dịch.

## Checklist ghi nhớ

- [ ] Phân biệt `final`, `static final` và *constant variable*.
- [ ] Nêu đúng ba điều kiện: `final` + primitive/`String` + constant expression.
- [ ] Biết lời gọi phương thức/`new` thường khiến initializer không phải constant expression.
- [ ] Hiểu vì sao chỉ truy cập constant variable không làm lớp khai báo bắt buộc khởi tạo.
- [ ] Nhận ra `final` của tham chiếu không đồng nghĩa đối tượng bất biến.
- [ ] Biết phải biên dịch lại code phụ thuộc khi thay hằng public có thể được inline.
- [ ] Chạy được `CompileTimeConstantsDemo.java` và giải thích sáu dòng output.

**Kết luận:** `static final` thể hiện **biến cấp lớp không được gán lại**, còn *constant variable* là một trường hợp đặc biệt mà Java xác định từ **kiểu và constant expression**. Phân biệt hai khái niệm này để hiểu khởi tạo lớp, tính bất biến, `switch case` và tác động tới các file `.class` đã phát hành.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="variables-and-constants-009">Quy ước đặt tên biến, tham số và hằng</a>

<details>
<summary>Xem chi tiết</summary>

# Quy ước đặt tên biến, tham số và hằng trong Java 17+

## 1. Vì sao quy ước đặt tên quan trọng?

Tên biến, tham số và hằng là một phần của cách diễn đạt ý nghĩa chương trình. Java **kiểm tra tên có hợp lệ về cú pháp**, còn **quy ước đặt tên** giúp người khác hiểu dữ liệu, giảm nhầm lẫn và dễ tìm kiếm khi bảo trì.

~~~java
int x = 3;                   // Hợp lệ nhưng khó biết x là gì
int itemCount = 3;           // Cùng kiểu dữ liệu, rõ nghĩa hơn

int p = 120_000;             // Không thấy đơn vị hoặc vai trò
int unitPriceDong = 120_000; // Cho biết đây là đơn giá tính bằng đồng
~~~

Ba nguyên tắc thực dụng:

1. **Đúng cú pháp Java**: tên không được là từ khóa hoặc literal bị dành riêng, không bắt đầu bằng chữ số và phải tuân theo quy tắc định danh.
2. **Rõ vai trò**: tên nêu dữ liệu hoặc ý định nghiệp vụ thay vì chỉ lặp lại tên kiểu.
3. **Nhất quán trong toàn dự án**: cùng một khái niệm nên được gọi thống nhất, tránh đổi giữa `amount`, `value`, `money` mà không có lý do.

Tên “đúng chuẩn” không bảo đảm chương trình đúng nghiệp vụ; nhưng tên kém rõ ràng có thể che khuất lỗi ngay cả khi chương trình vẫn biên dịch.

## 2. Bảng quy ước thường dùng

| Thành phần | Phong cách thông dụng | Ví dụ nên dùng | Tránh khi không có lý do |
|---|---|---|---|
| Biến cục bộ | `lowerCamelCase` | `itemCount`, `totalPriceDong` | `ItemCount`, `item_count` |
| Tham số method | `lowerCamelCase` | `unitPriceDong`, `discountPercent` | `UnitPrice`, `arg1` |
| Field instance | `lowerCamelCase` | `customerName`, `isActive` | `CUSTOMER_NAME` cho field thông thường |
| Field static thay đổi được | `lowerCamelCase` | `requestCount` | `REQUEST_COUNT` nếu không phải hằng |
| Hằng được đặt tên | `UPPER_SNAKE_CASE` | `MAX_ITEMS`, `DEFAULT_TIMEOUT_MS` | `max_items`, `DefaultTimeoutMs` |
| Biến cục bộ `final` | Thường vẫn `lowerCamelCase` | `final int maxAllowed = 5;` | Mặc định đổi mọi final local thành `MAX_ALLOWED` |
| Hằng enum | Thường `UPPER_SNAKE_CASE` | `PENDING`, `IN_PROGRESS` | Viết mỗi giá trị một kiểu tên |

Đây là **convention**, không phải bộ quy tắc được `javac` ép buộc. Ví dụ `int ItemCount = 3;` có thể biên dịch nhưng phá vỡ phong cách chung. Các công cụ lint/style có thể áp dụng quy ước chặt hơn theo dự án.

**`lowerCamelCase`**: từ đầu viết thường, các từ tiếp theo viết hoa chữ cái đầu: `maxRetryCount`.

**`UPPER_SNAKE_CASE`**: các từ viết hoa, phân cách bằng `_`: `MAX_RETRY_COUNT`.

## 3. Phân biệt tên hợp lệ và tên nên dùng

Java phân biệt chữ hoa/chữ thường, do đó `count`, `Count` và `COUNT` là **ba định danh khác nhau**. Nhưng dùng cả ba trong cùng phạm vi rất dễ gây hiểu nhầm.

~~~java
int count = 3;
// int Count = 4; // Có thể hợp lệ, nhưng không nên tạo cặp tên quá giống nhau
~~~

Các trường hợp đáng nhớ:

| Định danh | Hợp lệ làm tên biến? | Đánh giá |
|---|---|---|
| `itemCount` | Có | Đúng quy ước và rõ nghĩa |
| `_itemCount` | Có | Thường không cần dấu `_` ở đầu |
| `$itemCount` | Có | Java cho phép `$`, nhưng nên tránh dùng tùy tiện |
| `sốLượng` | Có | Java hỗ trợ Unicode; dự án cần quy ước nhất quán |
| `2items` | Không | Bắt đầu bằng chữ số |
| `item-count` | Không phải một định danh | Dấu `-` là toán tử |
| `item count` | Không | Có khoảng trắng |
| `class` | Không | Từ khóa Java |
| `true` | Không | Boolean literal dành riêng |
| `_` | Không trên Java 17 | Dấu gạch dưới đơn lẻ bị dành riêng |

Một tên hợp lệ vẫn phải được đặt **đúng phạm vi** và **không trùng khai báo trái quy tắc**. Trong một block không thể khai báo hai biến cục bộ cùng tên.

`String` và `string` không phải cùng định danh; khi đặt tên nên tránh phụ thuộc vào người đọc “đoán” chữ hoa/chữ thường.

## 4. Đặt tên biến theo nghĩa, đơn vị và trạng thái

Tên biến nên trả lời được câu hỏi “giá trị này biểu thị điều gì?”:

~~~java
int itemCount = 3;
long totalPriceDong = 360_000L;
int timeoutMillis = 2_500;
double distanceKilometers = 12.5;
String customerEmail = "lan@example.com";
~~~

Nếu hai giá trị cùng kiểu nhưng khác **đơn vị**, tên nên chỉ ra đơn vị:

~~~java
long timeoutMillis = 500;
long timeoutSeconds = 2;

// Không được xem 500 và 2 là cùng một đơn vị chỉ vì đều là long.
~~~

Với những miền quan trọng như thời gian và tiền tệ, có thể dùng kiểu chuyên biệt (ví dụ `Duration`, `BigDecimal`) thay cho một con số trần. Nhưng khi dùng primitive, tên có đơn vị vẫn giúp giảm lỗi.

**Tránh những tên không mang nội dung**: `data`, `value`, `obj`, `temp`, `str` khi bạn có thể dùng một danh từ cụ thể hơn. Biến vòng lặp ngắn `i` có thể chấp nhận trong đoạn 2–3 dòng rất rõ ngữ cảnh; với vòng lặp dài, `itemIndex` hoặc `orderIndex` dễ hiểu hơn.

### Tên boolean như một câu hỏi đúng/sai

Biến `boolean` thường nên đọc như một mệnh đề:

~~~java
boolean isActive = true;
boolean hasPermission = false;
boolean canSubmit = isActive && hasPermission;
boolean isEmpty = itemCount == 0;
~~~

Các tiền tố `is`, `has`, `can`, `should` giúp người đọc hiểu phép kiểm tra:

~~~java
if (hasPermission && isActive) {
    System.out.println("ALLOW");
}
~~~

Đừng đặt `isActive` cho một biến số lượng hoặc một chuỗi; tên phải khớp với kiểu và ý nghĩa thực.

Không bắt buộc mọi boolean phải bắt đầu bằng `is`. Các tên tự nhiên như `enabled`, `valid` có thể vẫn rõ nghĩa khi thống nhất trong codebase.

## 5. Tên tham số: diễn tả hợp đồng của method

Tham số giúp người gọi hiểu **đầu vào** của phương thức. Chọn tên bộc lộ vai trò, miền giá trị và đơn vị:

~~~java
static long calculateTotalDong(
        int unitPriceDong,
        int itemCount,
        int discountPercent) {
    if (unitPriceDong < 0 || itemCount < 0
            || discountPercent < 0 || discountPercent > 100) {
        throw new IllegalArgumentException("Invalid input");
    }

    long subtotalDong = (long) unitPriceDong * itemCount;
    return subtotalDong * (100 - discountPercent) / 100;
}
~~~

`unitPriceDong`, `itemCount` và `discountPercent` giúp người đọc phân biệt **đơn giá**, **số lượng** và **phần trăm giảm**, dù tất cả đều là `int`.

Nếu tham số được đặt tên `a`, `b`, `c`, chữ ký có thể vẫn biên dịch nhưng người gọi phải mở thân method mới hiểu ý nghĩa.

### Tránh tên tham số che khuất field nếu không có chủ đích

Một tình huống hợp lệ:

~~~java
class Order {
    private int itemCount;

    Order(int itemCount) {
        this.itemCount = itemCount;
    }
}
~~~

Tham số và field cùng tên là lựa chọn phổ biến; `this.itemCount` giúp nói rõ field, còn `itemCount` chỉ tham số. Không cần đổi thành `theItemCount` hay `pItemCount` chỉ để né việc dùng `this`.

Tuy nhiên, ngoài những trường hợp có chủ đích như constructor/setter, cần tránh che khuất tên khiến mã trở nên khó hiểu.

## 6. Hằng: tên in hoa và dấu gạch dưới

Khi một giá trị cố định có ý nghĩa và được dùng lại, đặt tên hằng giúp tránh **magic numbers**:

~~~java
private static final int MAX_ITEMS = 5;
private static final int DEFAULT_DISCOUNT_PERCENT = 10;
private static final String CURRENCY_CODE = "VND";
~~~

Ví dụ:

~~~java
if (itemCount > MAX_ITEMS) {
    throw new IllegalArgumentException("Too many items");
}
~~~

Tên `MAX_ITEMS` mô tả vai trò của số `5`; tên `DEFAULT_DISCOUNT_PERCENT` nhắc rằng giá trị `10` là **phần trăm**, không phải số tiền.

### `final` không đồng nghĩa mọi biến phải viết hoa

~~~java
final int selectedItemCount = 3;   // final local: lowerCamelCase
private static final int MAX_ITEMS = 5; // named constant: UPPER_SNAKE_CASE
~~~

`final` là **quy tắc ngôn ngữ** giới hạn việc gán lại biến; còn `UPPER_SNAKE_CASE` là **quy ước tên** cho các hằng được đặt tên.

Với `static final` là tham chiếu đến đối tượng mutable, việc không gán lại biến **không** khiến trạng thái của object bất biến. Vì thế, không nên chỉ nhìn tên viết hoa rồi suy đoán dữ liệu chắc chắn bất biến.

Các framework/nhóm có thể có ngoại lệ (ví dụ một `static final Logger logger` đặt `lowerCamelCase`). Hãy tuân theo style đã thống nhất thay vì áp dụng bảng một cách máy móc.

### Hằng liên quan đến đơn vị

Tên rõ đơn vị giúp người đọc tự kiểm tra khi dùng API:

~~~java
private static final long REQUEST_TIMEOUT_MILLIS = 3_000L;
private static final int MAX_RETRY_COUNT = 3;
~~~

Tránh tên `TIMEOUT` khi không rõ là giây hay mili-giây. Hãy dùng cùng một từ cho cùng một khái niệm trong toàn hệ thống.

## 7. Chương trình Java 17 chạy được

Lưu toàn bộ mã dưới đây vào **`NamingConventionDemo.java`**:

~~~java
public final class NamingConventionDemo {
    private static final int MAX_ITEMS = 5;
    private static final int DEFAULT_DISCOUNT_PERCENT = 10;
    private static final String CURRENCY_CODE = "VND";

    private NamingConventionDemo() {}

    static long calculateTotalDong(
            int unitPriceDong, int itemCount, int discountPercent) {
        if (unitPriceDong < 0 || itemCount < 0 || itemCount > MAX_ITEMS) {
            throw new IllegalArgumentException("Invalid item count or price");
        }
        if (discountPercent < 0 || discountPercent > 100) {
            throw new IllegalArgumentException("Invalid discount percent");
        }
        long subtotalDong = (long) unitPriceDong * itemCount;
        return subtotalDong * (100 - discountPercent) / 100;
    }

    static boolean canCheckout(boolean isActive, boolean hasPermission) {
        return isActive && hasPermission;
    }

    public static void main(String[] args) {
        int unitPriceDong = 120_000;
        int itemCount = 3;
        int discountPercent = DEFAULT_DISCOUNT_PERCENT;
        boolean isExpressDelivery = false;

        long totalDong = calculateTotalDong(
                unitPriceDong, itemCount, discountPercent);
        boolean isActive = true;
        boolean hasPermission = true;

        System.out.println("itemCount=" + itemCount);
        System.out.println("unitPriceDong=" + unitPriceDong);
        System.out.println("discountPercent=" + discountPercent);
        System.out.println("totalDong=" + totalDong);
        System.out.println("currency=" + CURRENCY_CODE);
        System.out.println("isExpressDelivery=" + isExpressDelivery);
        System.out.println("canCheckout=" +
                canCheckout(isActive, hasPermission));
        System.out.println("maxItems=" + MAX_ITEMS);
    }
}
~~~

Mở terminal tại thư mục chứa tệp và sử dụng **JDK 17 hoặc mới hơn**.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 NamingConventionDemo.java
java -cp . NamingConventionDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\NamingConventionDemo.java
java -cp . NamingConventionDemo
~~~

**Expected output:**

~~~text
itemCount=3
unitPriceDong=120000
discountPercent=10
totalDong=324000
currency=VND
isExpressDelivery=false
canCheckout=true
maxItems=5
~~~

Lý do:

- `itemCount`, `unitPriceDong`, `discountPercent`, `totalDong` là các tên biến `lowerCamelCase` có ý nghĩa.
- Các tham số của `calculateTotalDong` cũng sử dụng `lowerCamelCase`.
- `MAX_ITEMS`, `DEFAULT_DISCOUNT_PERCENT`, `CURRENCY_CODE` là các hằng được đặt tên theo `UPPER_SNAKE_CASE`.
- `isActive`, `hasPermission`, `canCheckout` diễn đạt trạng thái/điều kiện boolean.
- `totalDong = 120000 × 3 × (100 - 10) / 100 = 324000` được tính bằng `long` trước khi chia.

Ví dụ tập trung vào quy ước đặt tên. Trong ứng dụng thật, hãy kiểm thử cả dữ liệu biên và xác định quy tắc làm tròn tiền theo hợp đồng nghiệp vụ.

## 8. Nhất quán tên trong API và lúc đổi tên

Giả sử nhiều method dùng chung khái niệm “số lượng sản phẩm”:

~~~java
int itemCount = 3;
void updateItemCount(int itemCount) { /* ... */ }
int getItemCount() { return itemCount; }
~~~

Đoạn trên chỉ nhằm minh họa tên: các method cần được đặt trong một class hợp lệ, với field thích hợp nếu muốn dùng chúng như code hoàn chỉnh.

Nhất quán `itemCount` tốt hơn dùng `num`, `qty`, `size` và `count` lẫn lộn khi tất cả cùng chỉ số món hàng. Tuy nhiên, `itemCount` và `cartSize` có thể là hai khái niệm khác nhau nếu mô hình nghiệp vụ thật sự khác.

### Khi rename

- Đổi tên biến cục bộ và tham số bằng chức năng **Rename Refactor** của IDE khi có thể.
- Nếu đổi tên hằng `MAX_ITEMS`, cập nhật mọi nơi tham chiếu tương ứng.
- Nếu đổi tên public method/field trong API, kiểm tra tác động đến caller, test, reflection, serialization và tài liệu.
- Java phân biệt hoa/thường nên `maxItems` không tự biến thành `MAX_ITEMS`; thay đổi chỉ một chữ hoa cũng là đổi định danh.

Tên không cần hoàn hảo ngay lần đầu, nhưng nên cải thiện khi hiểu chính xác ý nghĩa dữ liệu và trước khi tên trở thành API mà nhiều nơi phụ thuộc.

## 9. Các tên dễ gây nhầm và phương án tốt hơn

| Tên kém rõ | Tên tốt hơn | Điều được làm rõ |
|---|---|---|
| `n` | `itemCount` | Đại lượng được đếm |
| `p` | `unitPriceDong` | Đơn giá và đơn vị |
| `t` | `timeoutMillis` | Thời lượng và đơn vị |
| `flag` | `isActive` | Trạng thái boolean |
| `x1` | `discountPercent` | Vai trò trong phép tính |
| `MAX` | `MAX_ITEMS` | Giới hạn cái gì |
| `DEFAULT` | `DEFAULT_DISCOUNT_PERCENT` | Giá trị mặc định của trường nào |
| `list` | `customerEmails` | Kiểu dữ liệu/chứa gì ở mức nghiệp vụ |
| `result` | `totalDong` | Kết quả có ý nghĩa gì |

Các tên ngắn không phải lúc nào cũng sai. Biến `i` trong vòng lặp hai dòng có thể đủ rõ. Nhưng một tham số thuộc public API tên `x` nên được xem xét kỹ hơn vì người đọc cần hiểu hợp đồng khi gọi method.

**Tránh tên khiến người đọc suy luận sai kiểu hoặc hành vi**: đừng gọi biến số `isReady` nếu nó chứa `int`, đừng gọi timeout tính bằng mili-giây là `timeoutSeconds`.

## 10. Một số lỗi về naming convention cần phân biệt

| Hiện tượng | Là lỗi compiler hay lỗi convention? | Cách xử lý |
|---|---|---|
| `int 2count = 3;` | Lỗi cú pháp | `int count2 = 3;` |
| `int class = 1;` | Lỗi cú pháp vì từ khóa | `int classCount = 1;` |
| `int count_value = 3;` | Có thể biên dịch, sai style thông dụng | `int countValue = 3;` |
| `int Count = 3;` | Có thể biên dịch, tên biến không theo convention | `int count = 3;` |
| `static final int maxItems = 5;` | Có thể biên dịch, tên hằng không nhất quán | `MAX_ITEMS` nếu thực sự là named constant |
| `MAX_ITEMS` và `maxItems` dùng lẫn | Hai định danh khác nhau | Thống nhất ý nghĩa và rename chính xác |
| Đặt `isEnabled` cho `int` | Có thể biên dịch nhưng đánh lừa người đọc | Tên như `enabledItemCount` |
| `TIMEOUT_MS` nhưng giá trị tính bằng giây | Lỗi ý nghĩa, compiler khó phát hiện | Đổi đơn vị/giá trị và tên đồng bộ |

**Compiler báo “cannot find symbol”** có thể xuất phát từ gõ sai chữ hoa/thường khi tham chiếu tên, chứ không phải vì Java bắt `lowerCamelCase`.

## 11. Bài tập có đáp án

### Bài A — đổi tên theo lowerCamelCase

Đổi các tên biến `Total_Price`, `customer_name`, `numberOf_Items` thành tên theo quy ước, giả sử ý nghĩa tương ứng không đổi.

**Đáp án gợi ý:** `totalPrice`, `customerName`, `numberOfItems`. Nếu giá có đơn vị cụ thể, có thể tốt hơn nữa: `totalPriceDong`.

### Bài B — phân biệt final local và named constant

~~~java
final int retryCount = 3;
static final int MAX_RETRIES = 3;
~~~

**Đáp án:** tên `retryCount` theo `lowerCamelCase` phù hợp cho final local; `MAX_RETRIES` theo `UPPER_SNAKE_CASE` phù hợp cho hằng `static final` của class. Dòng thứ hai phải được đặt ở vị trí khai báo field hợp lệ, không đặt `static` trong block method.

### Bài C — tên boolean

Chọn tên rõ ràng cho ba trạng thái: người dùng đã đăng nhập, tài khoản bị khóa, có thể gửi đơn.

**Đáp án gợi ý:** `isLoggedIn`, `isAccountLocked`, `canSubmitOrder`; dùng kiểu `boolean`.

### Bài D — đơn vị không được bỏ qua

Một tham số nhận thời gian chờ tính bằng mili-giây tên `timeout`. Tên nào rõ hơn?

**Đáp án:** `timeoutMillis` hoặc `timeoutMilliseconds`, nhất quán với quy ước của dự án. Nếu dùng kiểu `Duration`, tên `timeout` có thể đủ rõ vì kiểu đã mang ý nghĩa thời lượng.

### Bài E — tên hợp lệ nhưng khó đọc

~~~java
int a = 3;
int b = 120000;
long c = (long) a * b;
~~~

Hãy đổi tên để người đọc hiểu đơn vị và vai trò.

**Đáp án gợi ý:**

~~~java
int itemCount = 3;
int unitPriceDong = 120000;
long subtotalDong = (long) itemCount * unitPriceDong;
~~~

Không đổi công thức; chỉ dùng tên giúp giải thích điều đang được tính.

## 12. Checklist và kết luận

- [ ] Phân biệt quy tắc cú pháp bắt buộc với naming convention.
- [ ] Dùng `lowerCamelCase` nhất quán cho biến và tham số.
- [ ] Dùng `UPPER_SNAKE_CASE` cho hằng có tên khi phù hợp với style của nhóm.
- [ ] Hiểu `final` không tự buộc mọi tên biến phải in hoa.
- [ ] Đặt tên thể hiện đại lượng, đơn vị và trạng thái boolean.
- [ ] Không dùng tên quá giống nhau chỉ khác chữ hoa/thường.
- [ ] Phân biệt tên tham số và field có chủ đích bằng `this`.
- [ ] Tránh magic numbers bằng tên hằng có ý nghĩa.
- [ ] Biên dịch và chạy `NamingConventionDemo.java` trên JDK 17+, đối chiếu output.
- [ ] Dùng rename refactoring để thay đổi tên nhất quán và kiểm tra tác động đến API.

**Kết luận:** Tên biến và tham số nên mô tả **dữ liệu hoặc vai trò** theo `lowerCamelCase`; hằng được đặt tên thường dùng `UPPER_SNAKE_CASE`. Đây là quy ước giúp mã rõ ràng và nhất quán, không phải phép màu làm chương trình tự đúng. Hãy kết hợp tên dễ đọc với kiểu phù hợp, đơn vị minh bạch và kiểm thử hành vi thực tế.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="variables-and-constants-010">Tham chiếu final không đổi nhưng đối tượng vẫn có thể thay đổi</a>

<details>
<summary>Xem chi tiết</summary>

# Tham chiếu `final` không đổi nhưng đối tượng vẫn có thể thay đổi

> **Java core — language-basics / Biến và hằng**  
> **Môi trường:** Java 17+, chỉ sử dụng thư viện chuẩn.  
> **Mục tiêu:** Giải thích chính xác `final` ràng buộc **biến tham chiếu** chứ không tự khóa **trạng thái đối tượng**; phân biệt gán lại tham chiếu với thay đổi object, hiểu alias, mảng và collection, và nhận ra khi nào object thật sự bất biến.

## 1. `final` ràng buộc cái gì?

Khi khai báo:

~~~java
final StringBuilder message = new StringBuilder("Java");
message.append(" 17");                   // Hợp lệ: sửa object đang được tham chiếu.
// message = new StringBuilder("Kotlin"); // Lỗi biên dịch: gán lại biến final.
~~~

Có **hai đối tượng cần phân biệt về mặt khái niệm**:

- **Biến `message`:** một biến tham chiếu đã được gán đúng một lần. Từ khóa `final` ngăn **gán lại biến ấy** sang tham chiếu khác.
- **Đối tượng `StringBuilder`:** dữ liệu bên trong có thể được phương thức `append` thay đổi. `final` trên biến `message` **không** biến đối tượng này thành bất biến.

Bảng so sánh:

| Thao tác với `final StringBuilder message` | Hợp lệ? | Lý do |
| --- | --- | --- |
| `message.append("!");` | Có | Thay đổi trạng thái của object |
| `message.setLength(0);` | Có | Sửa object qua method |
| `message = new StringBuilder();` | Không | Gán lại biến `final` |
| `message = message;` | Không | Vẫn là phép gán lại, kể cả RHS là chính tham chiếu đó |
| `System.out.println(message);` | Có | Đọc/tham chiếu object, không gán lại |

**Ghi nhớ:** `final` trên tham chiếu mang nghĩa “**không đổi đối tượng mà biến này trỏ tới**”, chứ không có nghĩa “**mọi byte của đối tượng đều bị khóa**”.

## 2. Chương trình Java 17+ chạy được

Tạo tệp **FinalReferenceDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FinalReferenceDemo {
    static class Counter {
        private int value;

        Counter(int value) {
            this.value = value;
        }

        void increment() {
            value++;
        }

        int getValue() {
            return value;
        }
    }

    private static void increase(final Counter target) {
        target.increment(); // Được: sửa object.
//      target = new Counter(0); // Sai: gán lại tham số final.
    }

    public static void main(String[] args) {
        final Counter counter = new Counter(10);
        Counter alias = counter;

        increase(counter);
        alias.increment();
        System.out.println("counter=" + counter.getValue());
        System.out.println("sameObject=" + (counter == alias));

        final int[] numbers = {1, 2, 3};
        numbers[0] = 9;
        System.out.println("array=" + Arrays.toString(numbers));

        final StringBuilder builder = new StringBuilder("Java");
        builder.append(" 17");
        System.out.println("builder=" + builder);

        final List<String> names = new ArrayList<>();
        names.add("Lan");
        names.add("Mai");
        System.out.println("names=" + names);

        final String language = "java";
        String upper = language.toUpperCase();
        System.out.println("string=" + language + ", upper=" + upper);

        final List<String> unmodifiable = List.of("A", "B");
        try {
            unmodifiable.add("C");
        } catch (UnsupportedOperationException exception) {
            System.out.println("listOfAdd=blocked");
        }

//      counter = new Counter(99); // Sai: gán lại biến final.
//      numbers = new int[3];      // Sai: gán lại biến final.
//      builder = new StringBuilder(); // Sai: gán lại biến final.
    }
}
~~~

Chạy trong thư mục chứa tệp bằng Bash, PowerShell hoặc Command Prompt:

~~~bash
javac -encoding UTF-8 FinalReferenceDemo.java
java FinalReferenceDemo
~~~

**Expected output chính xác:**

~~~text
counter=12
sameObject=true
array=[9, 2, 3]
builder=Java 17
names=[Lan, Mai]
string=java, upper=JAVA
listOfAdd=blocked
~~~

**Diễn giải:**

1. `counter` và `alias` trỏ tới **cùng một object**. `increase(counter)` tăng từ 10 lên 11; `alias.increment()` tăng tiếp thành 12. Biến `counter` không được gán lại lần nào.
2. `numbers` luôn giữ tham chiếu tới cùng mảng, nhưng `numbers[0] = 9` được phép: `final` **không khóa phần tử mảng**.
3. `builder.append` và `names.add` thay đổi các object có tính mutable.
4. `String` là kiểu **immutable**. `toUpperCase()` cho ra chuỗi khác `"JAVA"`, không sửa nội dung chuỗi `language` đang trỏ tới.
5. `List.of("A", "B")` tạo danh sách **không cho thêm/xóa/thay thế phần tử**; gọi `add` ném `UnsupportedOperationException`. Điều này là **tính chất của đối tượng danh sách**, không đến từ biến `final`.

Những dòng mã sai về phép gán lại đang được comment để chương trình chính vẫn biên dịch. Bạn có thể **bỏ comment từng dòng riêng** để xem thông báo lỗi compiler, rồi đưa lại về comment trước khi chạy.

## 3. Vì sao alias vẫn sửa được object?

Giả sử:

~~~java
final Counter original = new Counter(1);
Counter otherName = original;

otherName.increment(); // original.getValue() cũng trở thành 2.
~~~

Sơ đồ quan hệ tham chiếu:

~~~mermaid
flowchart LR
    A["final original"] --> O["Counter object: value = 2"]
    B["otherName"] --> O
    C["otherName.increment()"] --> O
~~~

`final` chỉ ràng buộc phép gán cho tên `original`. Nó **không thu hồi** các tham chiếu khác và không cấm object nhận thay đổi qua:

- Một **alias** (`otherName`).
- Một **phương thức** nhận tham số tham chiếu (`increase(final Counter target)`).
- Một **field** hoặc một collection khác đang giữ tham chiếu tới object.

Bởi vậy, chỉ nhìn thấy `final` ở nơi khai báo là **chưa đủ** để kết luận trạng thái object sẽ giữ nguyên suốt vòng đời chương trình.

## 4. Phân biệt `final`, immutable và unmodifiable

| Khái niệm | Ràng buộc điều gì? | Ví dụ |
| --- | --- | --- |
| **Biến tham chiếu `final`** | Không được gán lại tham chiếu đã gán | `final StringBuilder b = new StringBuilder();` |
| **Object mutable** | Trạng thái bên trong có thể đổi | `b.append("X")`, `list.add("X")`, `array[0] = 2` |
| **Object immutable** | API/thiết kế object không cho đổi trạng thái quan sát được sau khi tạo | `String`, `Integer` |
| **Unmodifiable collection** | Không thể sửa cấu trúc/phần tử qua các thao tác thay đổi của collection | `List.of("A", "B")` |

Một biến **không có `final`** vẫn có thể giữ object bất biến:

~~~java
String label = "A";
label = "B"; // Đổi tham chiếu tới String khác; không sửa object "A".
~~~

Một biến **có `final`** lại hoàn toàn có thể trỏ tới object mutable:

~~~java
final List<String> labels = new ArrayList<>();
labels.add("A"); // Được.
~~~

`List.of` ngăn thay đổi **cấu trúc/danh sách phần tử** qua API của nó; nhưng nếu phần tử bên trong là object mutable, **trạng thái phần tử đó vẫn có thể đổi**. Đừng xem “danh sách không sửa được” là bảo đảm bất biến sâu (*deep immutability*) của cả đồ thị object.

## 5. Áp dụng với biến cục bộ, tham số và field

`final` có thể gắn với **biến cục bộ**, **tham số phương thức** hoặc **field**:

~~~java
class Basket {
    private final List<String> items = new ArrayList<>();

    void add(String item) {
        items.add(item); // Được: sửa List đang tham chiếu.
    }

    int size() {
        return items.size();
    }

//  void reset() {
//      items = new ArrayList<>(); // Sai: gán lại final field.
//  }
}
~~~

- Với **biến cục bộ `final`**, Java cho phép gán lần đầu theo đúng quy tắc definite assignment, nhưng không cho phép gán lại sau đó.
- Với **tham số `final`**, code trong phương thức không được gán tham số sang object khác; nó vẫn có thể gọi method làm đổi object truyền vào.
- Với **field `final`**, nó phải được khởi tạo hợp lệ (ví dụ ngay tại khai báo hoặc trong constructor với instance field). Sau khi được gán, field không được gán lại theo cách Java thông thường.

Đối với **primitive**, `final` ngăn thay đổi chính giá trị của biến vì primitive lưu giá trị thay vì tham chiếu:

~~~java
final int maxItems = 10;
// maxItems++; // Lỗi: cố thay đổi biến final.
~~~

Đó là lý do `final int` thường trông như một hằng, còn `final SomeMutableObject` **không** hứa rằng object là hằng.

## 6. Bẫy thường gặp và hướng xử lý

| Hiểu nhầm / triệu chứng | Điều đúng |
| --- | --- |
| “`final` làm cho `ArrayList` bất biến.” | Sai. `final` cấm gán lại biến, `add` vẫn sửa được danh sách mutable. |
| “`final int[] a` không sửa được `a[0]`.” | Sai. Phần tử mảng vẫn có thể đổi. |
| “`final String s` rồi gọi `toUpperCase()` sẽ đổi `s`.” | Sai. `String` bất biến; method trả String khác hoặc một kết quả phù hợp, không sửa `s`. |
| “Gán `x = x` không tính là gán lại.” | Sai khi `x` là biến `final` đã được gán. |
| “Đổi object qua alias thì biến `final` sẽ chặn.” | Sai. Alias có thể sửa trạng thái object, không gán lại biến `final`. |
| “`List.of(...)` không sửa được nhờ từ khóa `final`.” | Sai. Đối tượng List không hỗ trợ thao tác sửa; dù biến không `final`, các phép `add` vẫn bị chặn. |
| “`final` bảo đảm an toàn giữa các thread.” | Sai. Object mutable vẫn có thể bị truy cập/sửa đồng thời; cần thiết kế đồng bộ hoặc immutable phù hợp. |

Nếu cần bảo vệ dữ liệu khỏi thay đổi không mong muốn, hãy xem xét **loại object và cách chia sẻ tham chiếu**, không chỉ thêm `final`:

- Chọn **kiểu immutable** khi phù hợp, ví dụ `String` và các kiểu giá trị được thiết kế bất biến.
- Tránh để lộ object mutable trực tiếp; dùng **defensive copy** nếu cần cách ly dữ liệu.
- Với collection, cân nhắc API tạo bản sao không sửa được như `List.copyOf` theo hợp đồng dữ liệu; chú ý không đồng nghĩa sao chép sâu mọi phần tử.
- Với truy cập đa luồng, cần kiểm soát đồng bộ/trạng thái chung; `final` một mình không phải khóa (*lock*).

## 7. Bài tập tự kiểm tra

**Bài 1.** Cho `final int[] a = {1, 2};`. Dòng `a[1] = 5;` có biên dịch không? Còn `a = new int[]{3, 4};`? Giải thích thứ gì thay đổi.

**Bài 2.** Dùng hai tên tham chiếu cùng một `StringBuilder`, trong đó tên thứ nhất có `final`. Gọi `append` từ tên thứ hai rồi in tên thứ nhất: dự đoán kết quả.

**Bài 3.** Cho `final List<String> a = new ArrayList<>();`. `a.add("x")` hợp lệ không? Đổi cách tạo thành `List.of("x")` rồi thử `add("y")`; xác định bước nào là lỗi compiler, bước nào là exception khi chạy.

**Bài 4.** Viết `final String s = "Java";` rồi gọi `s.concat(" 17");`. Chuỗi `s` có đổi không? Làm thế nào dùng được kết quả nối mà không gán lại `s`?

**Bài 5.** Trong `FinalReferenceDemo`, để `Counter alias = counter;` rồi gọi `alias.increment()` hai lần; kiểm tra giá trị in từ `counter`. Biến `final` đã đổi hay object đã đổi?

### Kết luận

**`final` trên biến tham chiếu ngăn phép gán lại biến, không đóng băng object.** Đối tượng có thể thay đổi qua cùng biến, method nhận tham chiếu hoặc alias nếu bản thân nó mutable. Tính bất biến là **thuộc tính của thiết kế object/API**; còn an toàn đa luồng và chống thay đổi qua alias là những yêu cầu riêng cần được giải quyết bằng thiết kế phù hợp.


</details>

- [Quay lại đầu trang](#back-to-top)
