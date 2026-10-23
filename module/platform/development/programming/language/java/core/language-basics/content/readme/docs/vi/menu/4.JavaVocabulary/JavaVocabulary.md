<a id="back-to-top"></a>

# Từ vựng Java

## Menu
- [Token: tên định danh, từ khóa, literal, toán tử và dấu phân cách](#java-vocabulary-001)
- [Quy tắc tên định danh và phân biệt chữ hoa, chữ thường](#java-vocabulary-002)
- [Từ khóa dành riêng và từ khóa theo ngữ cảnh](#java-vocabulary-003)
- [Phân biệt true, false, null với từ khóa](#java-vocabulary-004)
- [Giới hạn của tên _, const và goto](#java-vocabulary-005)
- [Khoảng trắng, xuống dòng và phân tách token](#java-vocabulary-006)
- [Chú thích một dòng, nhiều dòng và chú thích tài liệu](#java-vocabulary-007)
- [Unicode trong mã nguồn và xử lý Unicode escape trước phân tích token](#java-vocabulary-008)

## <a id="java-vocabulary-001">Token: tên định danh, từ khóa, literal, toán tử và dấu phân cách</a>

<details>
<summary>Xem chi tiết</summary>

# Token trong Java: tên định danh, từ khóa, literal, toán tử và dấu phân cách

> **Java core — language-basics / Từ vựng Java**  
> **Áp dụng:** Java 17 trở lên, dùng JDK để biên dịch và chạy.  
> **Mục tiêu:** Nhận diện năm loại token cơ bản trong mã nguồn Java; phân biệt tên định danh với từ khóa, literal với toán tử, và dấu phân cách với nội dung có ý nghĩa ngữ pháp. Tự đọc được một dòng Java dưới góc nhìn của bộ phân tích từ vựng (*lexer*), dự đoán lỗi đặt tên và chạy một chương trình tối thiểu.

## 1. Token là gì và vì sao cần hiểu?

Khi nhìn dòng mã:

~~~java
int total = quantity * price;
~~~

người đọc hiểu đây là phép tính. Trước khi phân tích cấu trúc câu lệnh, Java xử lý mã nguồn thành những đơn vị từ vựng gọi là **token**. Có thể tách dòng trên thành:

| Thứ tự | Token | Loại | Vai trò |
| ---: | --- | --- | --- |
| 1 | `int` | Từ khóa | Kiểu số nguyên nguyên thủy |
| 2 | `total` | Tên định danh | Tên biến nhận kết quả |
| 3 | `=` | Toán tử | Phép gán |
| 4 | `quantity` | Tên định danh | Tên biến đầu vào |
| 5 | `*` | Toán tử | Phép nhân |
| 6 | `price` | Tên định danh | Tên biến đầu vào |
| 7 | `;` | Dấu phân cách | Kết thúc câu lệnh khai báo |

**Token không đồng nghĩa với từ ngữ có nghĩa trong tiếng Anh.** Chẳng hạn, `total` không phải từ khóa Java; đó là tên do lập trình viên tự đặt. Dấu `;` cũng là token dù chỉ dài một ký tự.

Sơ đồ khái niệm:

~~~mermaid
flowchart LR
    A["Ký tự mã nguồn Java"] --> B["Xử lý từ vựng"]
    B --> C["Dãy token"]
    C --> D["Phân tích cú pháp"]
    D --> E["Cấu trúc chương trình"]
~~~

**Khoảng trắng, xuống dòng và chú thích** giúp phân tách hoặc giải thích mã nguồn, nhưng không được tính là token Java theo nhóm năm loại đang học. Riêng dấu cách **bên trong** một chuỗi literal lại là một phần nội dung của literal đó.

## 2. Năm nhóm token theo Java

| Nhóm | Ví dụ | Cách nhận diện |
| --- | --- | --- |
| **Identifier — tên định danh** | `studentCount`, `totalPrice`, `println` | Tên của biến, lớp, phương thức, package… được khai báo hoặc tham chiếu |
| **Keyword — từ khóa** | `class`, `public`, `int`, `if`, `return` | Các từ được ngôn ngữ dành riêng theo quy tắc cú pháp |
| **Literal — giá trị viết trực tiếp** | `42`, `12_000L`, `3.14`, `'A'`, `"Java"`, `true`, `null` | Biểu diễn trực tiếp một giá trị trong mã |
| **Operator — toán tử** | `+`, `=`, `==`, `&&`, `++`, `->` | Thực hiện phép tính, so sánh, gán hoặc thể hiện thao tác trong biểu thức |
| **Separator — dấu phân cách** | `( )`, `{ }`, `[ ]`, `;`, `,`, `.`, `@`, `::` | Phân định thành phần, danh sách, khối, truy cập thành viên hoặc cú pháp liên quan |

Các từ như “identifier” hay “operator” là **loại token ở mức từ vựng**. Việc ghép token thành khai báo, biểu thức, câu lệnh hoặc lớp là bước **phân tích cú pháp**; một token hợp lệ chưa bảo đảm toàn bộ dòng mã hợp lệ.

Ví dụ `int = ;` gồm những token Java nhận diện được, nhưng không phải câu lệnh khai báo hợp lệ vì thiếu tên biến và giá trị/biểu thức thích hợp.

## 3. Tên định danh (identifier): quy tắc và cách đặt tên

Tên định danh được dùng cho lớp, phương thức, biến, field, package và nhiều thành phần khác. Java **phân biệt chữ hoa và chữ thường**:

~~~java
int count = 2;
int Count = 5;
System.out.println(count + Count); // 7
~~~

`count` và `Count` là **hai tên khác nhau**.

### Quy tắc tên trong Java 17

- Ký tự đầu phải hợp lệ cho vị trí bắt đầu identifier theo quy tắc Unicode của Java; những ký tự tiếp theo có thể bao gồm chữ số. Các chữ cái ASCII, `_` và `$` có thể tham gia tên theo quy tắc đó.
- **Không** bắt đầu một identifier ASCII bằng chữ số: `2items` không phải tên hợp lệ.
- **Không** sử dụng các từ khóa dành riêng như `class`, `int` hoặc `return` để đặt tên identifier thông thường.
- Ký tự gạch dưới đơn độc `_` **không được dùng làm identifier** trong Java 17 (từ Java 9 nó là một từ khóa dành riêng).
- Java hỗ trợ nhiều chữ cái Unicode trong identifier, chẳng hạn `soLuong` và `sốLượng`, nhưng viết tên ổn định, dễ đọc giúp code nhất quán hơn.

| Ví dụ | Hợp lệ như tên biến Java 17? | Giải thích |
| --- | --- | --- |
| `studentCount` | Có | Bắt đầu bằng chữ, các ký tự tiếp theo hợp lệ |
| `_count` | Có | Dấu `_` kết hợp với ký tự khác, không đứng một mình |
| `$cache` | Có | `$` được phép, nhưng nên tránh trong tên tự viết nếu không cần |
| `sốLượng` | Có | Java hỗ trợ identifier Unicode phù hợp |
| `2ndValue` | Không | Bắt đầu bằng chữ số |
| `item-count` | Không phải một identifier | Java đọc thành `item`, toán tử `-` và `count` |
| `class` | Không | Từ khóa dành riêng |
| `_` | Không | Dấu gạch dưới đơn độc được dành riêng từ Java 9 |

**Quy ước nên dùng:** tên biến/method là `lowerCamelCase` (`itemCount`), tên class là `UpperCamelCase` (`InvoiceCalculator`), hằng `static final` thường theo `UPPER_SNAKE_CASE` (`MAX_ITEMS`). Đây là **quy ước đặt tên** chứ không phải các bộ luật khác nhau của lexer.

### Identifier không nhất thiết là từ khóa

`String` là tên một lớp chuẩn của Java; `System` là tên một lớp; `println` là tên method; `main` và `args` là tên identifier. Chúng không vì “đặc biệt” hay “được Java dùng nhiều” mà biến thành từ khóa ngôn ngữ.

## 4. Từ khóa (keyword) và các từ bị hạn chế theo ngữ cảnh

Từ khóa có vai trò do ngôn ngữ Java quy định. Những ví dụ phổ biến:

| Nhóm ý nghĩa | Một số từ khóa Java 17 | Ví dụ sử dụng |
| --- | --- | --- |
| Khai báo lớp/kiểu | `class`, `interface`, `enum` | `class Person { }` |
| Quyền truy cập và thành phần | `public`, `private`, `protected`, `static`, `final` | `private int age;` |
| Điều khiển chương trình | `if`, `else`, `switch`, `for`, `while`, `return`, `break` | `if (ready) { ... }` |
| Kiểu nguyên thủy | `int`, `long`, `double`, `boolean`, `char`, `void` | `double rate = 1.5;` |
| Đối tượng và ngoại lệ | `new`, `this`, `super`, `try`, `catch`, `throw` | `new Object()` |

**Một số trường hợp dễ nhầm:**

- `true` và `false` là **boolean literals**; `null` là **null literal**. Chúng không nằm trong danh sách từ khóa Java dù cũng không thể dùng làm tên biến thông thường.
- `const` và `goto` được Java **dành riêng như từ khóa**, dù Java không có những câu lệnh hoạt động bình thường mang hai tên đó.
- Java 17 có những **restricted/contextual identifiers hoặc keywords** như `var`, `record`, `yield`, `sealed`, `permits` và `non-sealed`. Chúng chịu các quy tắc riêng tùy vị trí cú pháp; **không nên coi mọi lần xuất hiện của chúng giống một từ khóa luôn bị cấm như `class`**.

Ví dụ Java 17:

~~~java
var message = "Hello";   // var dùng trong khai báo biến cục bộ có khởi tạo.
System.out.println(message);
~~~

`var` không phải tên một kiểu class có thể dùng ở bất kỳ chỗ nào; compiler suy luận kiểu của biến trong các ngữ cảnh mà ngôn ngữ cho phép. Bài này chỉ nhằm nhận diện vai trò từ vựng, chưa đi sâu tính năng suy luận kiểu.

## 5. Literal: giá trị được viết trực tiếp trong mã

**Literal** là cách đặt một giá trị cụ thể ngay trong mã nguồn, không cần tham chiếu biến.

| Loại literal | Ví dụ hợp lệ | Ghi chú |
| --- | --- | --- |
| Số nguyên thập phân | `0`, `42`, `1_000` | `_` giữa các chữ số tăng khả năng đọc |
| Số nguyên hệ khác | `0b1010`, `0xFF`, `077` | Nhị phân, thập lục phân và bát phân |
| Số nguyên `long` | `12_000L` | Hậu tố `L`; chữ `L` hoa dễ phân biệt với số 1 |
| Số thực | `3.14`, `2.5e3`, `1.5f` | Mặc định là `double`; `f` tạo `float` |
| Ký tự `char` | `'A'`, `'\n'` | Dùng nháy đơn, có thể dùng escape |
| Chuỗi `String` | `"Java 17"`, `"A\nB"` | Dùng nháy kép |
| Boolean | `true`, `false` | Hai giá trị logic |
| Null | `null` | Giá trị null reference |

Trong Java 17, **text block** cũng là cú pháp literal để viết chuỗi nhiều dòng:

~~~java
String message = """
        Xin chào
        Java 17
        """;
~~~

Dấu `"""` mở đầu text block phải được theo sau bởi một dấu kết thúc dòng; nội dung dòng và xử lý thụt lề theo quy tắc Java text block. Không nhầm dấu `"""` với ba chuỗi rỗng ghép nhau.

### Ba lỗi lexical/literal thường gặp

**1. Dấu âm là toán tử riêng.** Ở biểu thức `int temperature = -5;`, lexer nhận dấu `-` và literal số nguyên `5` riêng rẽ. `-5` không phải một “literal âm” đơn lẻ theo cách phân loại token này.

**2. Dấu gạch dưới trong số phải đúng vị trí.** `1_000` hợp lệ, nhưng `_1000` không phải integer literal có dấu gạch dưới: lexer có thể nhận đó là identifier; đặt nó làm giá trị số sẽ lỗi cú pháp/ngữ nghĩa. Dạng `1000_` cũng không phải integer literal hợp lệ.

**3. Nháy đơn và nháy kép không thay thế nhau.** `'A'` là char literal; `"A"` là String literal. `'AB'` không phải một char literal hợp lệ.

Những từ `true`, `false` và `null` phải được hiểu là **literal**, ngay cả khi xuất hiện tại vị trí mà cú pháp Java không cho phép dùng chúng.

## 6. Toán tử (operator): đọc đúng token rồi mới hiểu phép toán

Các toán tử nhận giá trị hoặc đối tượng theo quy tắc của Java:

| Nhóm | Token tiêu biểu | Ví dụ |
| --- | --- | --- |
| Số học | `+`, `-`, `*`, `/`, `%` | `amount * count` |
| Gán | `=`, `+=`, `-=`, `*=`, `/=` | `total += 10` |
| So sánh | `==`, `!=`, `<`, `<=`, `>`, `>=` | `count >= 2` |
| Logic ngắn mạch | `&&`, `\|\|`, `!` | `ready && enabled` |
| Tăng/giảm | `++`, `--` | `count++` |
| Bit và dịch bit | `&`, `\|`, `^`, `~`, `<<`, `>>`, `>>>` | `flags & 0xFF` |
| Điều kiện và lambda | `?`, `:`, `->` | `ok ? 1 : 0`; `x -> x + 1` |

**Đừng nhầm `=` với `==`:** `=` thực hiện phép gán, `==` dùng để so sánh theo quy tắc kiểu. Đặc biệt, với reference object, `==` so sánh tham chiếu chứ không tự kiểm tra nội dung bằng `equals`.

### Ưu tiên ghép token dài hơn khi có thể

Java sử dụng nguyên tắc nhận diện token dài nhất hợp lệ trong quá trình phân tích từ vựng (*maximal munch*). Chẳng hạn:

~~~java
count++;         // ++ là một toán tử, không phải hai token + liền nhau.
total += 3;      // += là một toán tử gán kết hợp.
boolean ok = a >= b && b != 0;
~~~

Trong dòng cuối, `>=`, `&&`, `!=` đều là những token toán tử hoàn chỉnh. Khoảng trắng giúp đọc mã nhưng không được dùng để “tách đôi” một toán tử cố định tùy tiện. Ngược lại, `a + +b` dùng **hai toán tử cộng khác nhau** ở hai vị trí cú pháp.

**Phân tích token khác với phân tích thứ tự tính toán.** Nhận ra `+`, `*` và `=` là token chỉ là bước đầu; độ ưu tiên toán tử, kiểu dữ liệu và quy tắc đánh giá biểu thức sẽ quyết định ý nghĩa tính toán.

## 7. Dấu phân cách (separator): ranh giới của cấu trúc

Các dấu phân cách phổ biến trong Java 17:

| Token | Chức năng quen thuộc | Ví dụ |
| --- | --- | --- |
| `(`, `)` | Nhóm điều kiện, tham số, biểu thức | `if (ready)`, `print(x)` |
| `{`, `}` | Thân class/method/block hoặc initializer | `class App { ... }` |
| `[`, `]` | Khai báo hoặc truy cập mảng | `int[] a`, `a[0]` |
| `;` | Kết thúc khai báo/câu lệnh theo ngữ pháp | `int x = 1;` |
| `,` | Phân tách phần tử, tham số hoặc khai báo phù hợp | `print(a, b)` |
| `.` | Truy cập tên thành viên/định danh được phân tách | `System.out.println` |
| `...` | Ký hiệu tham số biến số lượng (*varargs*) | `String... args` |
| `@` | Mở đầu một annotation | `@Override` |
| `::` | Method reference | `String::valueOf` |

Trong `System.out.println("OK");`, lexer nhìn thấy các identifier `System`, `out`, `println`, các dấu `.`, `(`, `)`, `;` và chuỗi literal `"OK"`. Không có một token duy nhất tên `System.out.println`.

**Lưu ý:** `::` là dấu phân cách trong phân loại lexical token của Java, còn `->` là toán tử. Chúng thường được nhắc cùng nhau khi học lambda/method reference, nhưng không thuộc cùng một nhóm từ vựng.

## 8. Chương trình Java 17+ minh họa năm nhóm token

Tạo một tệp **TokenDemo.java**:

~~~java
public class TokenDemo {
    public static void main(String[] args) {
        int quantity = 3;
        long unitPrice = 12_000L;
        long total = quantity * unitPrice;

        boolean qualified = total >= 30_000L && quantity > 0;
        String productName = "Java";
        char grade = 'A';

        System.out.println(productName + ": " + total);
        System.out.println("qualified=" + qualified + ", grade=" + grade);
    }
}
~~~

Chạy trong terminal (Bash, PowerShell hoặc Command Prompt) tại thư mục chứa tệp:

~~~bash
javac -encoding UTF-8 TokenDemo.java
java TokenDemo
~~~

Kết quả **chính xác**:

~~~text
Java: 36000
qualified=true, grade=A
~~~

Trong mã:

- `public`, `class`, `static`, `void`, `int`, `long`, `boolean` và `char` là **keywords**.
- `TokenDemo`, `main`, `args`, `quantity`, `unitPrice`, `total`, `qualified`, `String`, `productName` và `grade` là **identifiers** (trong đó `String` là tên lớp).
- `3`, `12_000L`, `30_000L`, `0`, `"Java"`, `'A'` và các chuỗi truyền cho `println` là **literals**.
- `=`, `*`, `>=`, `&&`, `>`, `+` là **operators**.
- `{ }`, `( )`, `[ ]`, `;`, `,` và `.` là **separators**.

Ví dụ có chứa khoảng trắng và dòng trống để dễ đọc; chúng không làm thay đổi loại token. Chương trình sử dụng cú pháp class và `main` quen thuộc, không cần cài thư viện phụ trợ ngoài JDK.

## 9. Các bẫy thường gặp: token hợp lệ nhưng chương trình vẫn lỗi

| Mảnh mã hoặc trường hợp | Chẩn đoán | Cách sửa |
| --- | --- | --- |
| `int class = 1;` | `class` là từ khóa, không thể dùng làm identifier | Đổi thành `classCount` hoặc tên thích hợp |
| `int 2items = 3;` | Chữ số không thể bắt đầu tên biến kiểu này | Dùng `items2` |
| `int item-count = 3;` | `-` là toán tử, không phải ký tự nối trong identifier | Dùng `itemCount` hoặc `item_count` |
| `int _ = 1;` | `_` đơn độc là từ khóa dành riêng trong Java 17 | Chọn tên cụ thể như `unusedCount` |
| `int value = "7";` | Chuỗi literal không gán trực tiếp vào biến `int` | Dùng `7` hoặc chuyển chuỗi với `Integer.parseInt` khi thích hợp |
| `char c = "A";` | `"A"` là String, không phải char | Dùng `'A'` |
| `boolean ok = True;` | `True` không phải boolean literal `true`; Java phân biệt hoa/thường | Dùng `true` |
| `int v = 1__000;` | **Hợp lệ**: nhiều dấu gạch dưới liên tiếp giữa các chữ số được Java chấp nhận | Không tự kết luận mọi `__` đều sai |
| `int x = -5;` | Dấu trừ là toán tử riêng, không phải thành phần của literal `5` | Đọc token theo `=`, `-`, `5`, `;` |
| `int n = 8 / 0;` | Token hợp lệ nhưng phép toán số nguyên tại runtime gây lỗi | Kiểm tra mẫu số; cú pháp từ vựng không bảo đảm phép toán an toàn |

**Một lưu ý kỹ thuật:** một số lỗi trong bảng có thể được compiler phát hiện ngay khi phân tích cú pháp hoặc kiểm tra kiểu; một số khác chỉ xảy ra lúc chạy. Biết token không thay thế cho kiểm tra **kiểu**, **phạm vi biến** và **ngữ nghĩa chương trình**.

## 10. Thực hành phân tích dãy token

Hãy thử phân loại dòng sau, không cần chạy trước:

~~~java
boolean accepted = total >= 100 && quantity != 0;
~~~

| Nhóm | Các token có mặt |
| --- | --- |
| Keywords | `boolean` |
| Identifiers | `accepted`, `total`, `quantity` |
| Literals | `100`, `0` |
| Operators | `=`, `>=`, `&&`, `!=` |
| Separators | `;` |

Sau đó thay `>=` thành `> =` và thử biên dịch trong một chương trình có đủ khai báo biến: hai token `>` và `=` **không thay thế được** một toán tử `>=` trong biểu thức so sánh đó.

Một bài phân tích khác:

~~~java
System.out.println("Hi");
~~~

Hãy đếm: identifier `System`, separator `.`, identifier `out`, separator `.`, identifier `println`, separator `(`, string literal `"Hi"`, separator `)`, separator `;`. Tổng cộng **9 token**.

**Chú thích không biến thành token:** ở `int x = 1; // note`, các token vẫn kết thúc ở `;`; phần `// note` là comment. Ở `String s = "a b";`, dấu cách **nằm trong literal** và vì vậy là một phần giá trị chuỗi.

## 11. Bài tập tự luyện và đáp án định hướng

**Bài 1 — Nhận diện.** Với `long distance = 10_000L;`, hãy chỉ ra từ khóa, identifier, operator, literal và separator.

*Đáp án:* `long`; `distance`; `=`; `10_000L`; `;`.

**Bài 2 — Đặt tên.** Trong `price2`, `2price`, `price_total`, `total-price`, `_` và `$cache`, tên nào có thể là identifier Java 17? Vì sao vẫn nên hạn chế dùng `$` trong tên tự đặt?

*Định hướng:* `price2`, `price_total` và `$cache` hợp lệ; `2price`, `total-price`, `_` không là một identifier hợp lệ. `$` được phép nhưng kém phù hợp với quy ước đặt tên thông thường.

**Bài 3 — Keyword hay literal?** Phân biệt `boolean` với `true`; phân biệt `String` với `class`; giải thích tại sao `null` không phải identifier.

**Bài 4 — Toán tử dài nhất.** Phân tích `i++;` thành các token. Vì sao không đọc thành `i`, `+`, `+`, `;`? Phân tích tiếp `total += 1;`.

**Bài 5 — Sửa lỗi.** Sửa `int class = 3;` và `char unit = "A";`. Chỉ ra loại lỗi của mỗi dòng thay vì chỉ đưa đáp án.

**Bài 6 — Tự chạy.** Biên dịch `TokenDemo.java`, đổi literal `12_000L` thành `15_000L` và dự đoán cả hai dòng kết quả; tiếp tục thử dùng `'A'` thay cho `"Java"` ở biến `String` để quan sát lỗi kiểu.

## 12. Checklist cuối bài

- [ ] Biết **token là đơn vị từ vựng**, khác với câu lệnh/chương trình hợp lệ.
- [ ] Phân biệt được identifier, keyword, literal, operator và separator ngay trong một dòng Java.
- [ ] Nhớ `String`, `System`, `println` không phải từ khóa; `true`, `false`, `null` là literals.
- [ ] Biết tên biến Java 17 phân biệt chữ hoa/thường, không bắt đầu bằng số, và `_` không là identifier hợp lệ khi đứng đơn độc.
- [ ] Nhận ra `-5` gồm toán tử `-` và literal `5`; `++` là một token toán tử.
- [ ] Giải thích được vai trò của `;`, `.`, `( )` và `{ }` mà không nhầm chúng với toán tử.
- [ ] Tự biên dịch `TokenDemo.java` bằng `javac` và chạy bằng `java`, đối chiếu kết quả.

**Tổng kết:** Hiểu từ vựng Java giúp bạn đọc mã chính xác từ từng ký hiệu, chọn tên hợp lệ và phân biệt lỗi token với lỗi cú pháp/kiểu/ngữ nghĩa. Token là **vật liệu đầu vào** để ngôn ngữ ghép thành những cấu trúc chương trình có ý nghĩa.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-vocabulary-002">Quy tắc tên định danh và phân biệt chữ hoa, chữ thường</a>

<details>
<summary>Xem chi tiết</summary>

# Quy tắc tên định danh và phân biệt chữ hoa, chữ thường trong Java

## 1. Định danh là gì?

**Định danh (identifier)** là tên lập trình viên đặt cho một thành phần trong mã Java: biến, phương thức, lớp, interface, package, tham số hoặc hằng số. Ví dụ:

~~~java
int itemCount = 3;                 // itemCount: tên biến
String customerName = "Lan";      // customerName: tên biến
~~~

Trong khai báo `public class IdentifierDemo`, `IdentifierDemo` là tên lớp. Trong `System.out.println(itemCount)`, `System`, `out`, `println`, `itemCount` là các tên được dùng để tham chiếu các thành phần khác nhau; dấu chấm `.` là ký hiệu truy cập thành viên, **không phải một phần của định danh đơn**.

**Hai câu hỏi cần tách biệt:**

1. Tên đó **có hợp lệ theo cú pháp Java** hay không?
2. Tên đó **có rõ nghĩa và phù hợp quy ước** hay không?

Một tên có thể hợp lệ nhưng khó đọc, chẳng hạn `a1` hoặc `$x`. Ngược lại, tên `item-count` nhìn giống cách đặt tên trong văn bản nhưng **không phải một định danh đơn hợp lệ** của Java.

## 2. Quy tắc ký tự đầu và ký tự tiếp theo

Một định danh thông thường phải tuân thủ quy tắc ký tự của Java:

- Ký tự đầu phải là ký tự mà Java cho phép **bắt đầu định danh** (thường là chữ cái Unicode, `_` hoặc `$`).
- Những ký tự tiếp theo có thể gồm các ký tự hợp lệ ở đầu, chữ số và một số ký tự Unicode khác được Java cho phép.
- Không được có khoảng trắng, dấu `-`, dấu `.`, dấu `@` hoặc các ký hiệu toán tử như một phần của cùng một định danh.
- Định danh không được là **từ khóa bị cấm ở vị trí đó**, literal `true`, `false`, `null`, hoặc định danh đặc biệt **`_` đứng một mình** (bị dành riêng từ Java 9).
- Java **phân biệt chữ hoa và chữ thường**: `total`, `Total` và `TOTAL` là ba tên khác nhau.

| Tên đề xuất | Hợp lệ làm tên biến thông thường? | Giải thích |
|---|---|---|
| `count` | Có | Bắt đầu bằng chữ |
| `count2` | Có | Chữ số ở vị trí sau |
| `_count` | Có | Dấu gạch dưới đầu tên được phép |
| `$count` | Có | `$` được Java cho phép, nhưng không nên lạm dụng |
| `sốLượng` | Có | Java hỗ trợ nhiều chữ cái Unicode |
| `2count` | Không | Bắt đầu bằng chữ số |
| `item-count` | Không | `-` được hiểu là toán tử trừ, không thuộc định danh |
| `item count` | Không | Có khoảng trắng |
| `item.count` | Không phải *một* định danh | Gồm hai định danh và dấu `.` |
| `_ ` | Không | `_` đơn lẻ là ký hiệu dành riêng |
| `class` | Không | Từ khóa Java |
| `true`, `null` | Không | Literal dành riêng, không dùng làm tên biến |

**Chi tiết:** Các quy tắc Unicode thực tế được mô tả bởi các phép kiểm tra tương đương `Character.isJavaIdentifierStart(int)` và `Character.isJavaIdentifierPart(int)`, nhưng **kiểm tra ký tự thôi chưa đủ**: vẫn phải loại trừ từ khóa, một số tên bị dành riêng và xem ngữ cảnh cú pháp. Không nên dùng một regex ASCII đơn giản để kết luận mọi định danh Unicode đều không hợp lệ.

## 3. Java phân biệt hoa/thường như thế nào?

Java là ngôn ngữ **case-sensitive**. Đổi một ký tự từ hoa sang thường có thể đổi sang **một định danh khác** hoặc gây lỗi vì không tìm được tên.

~~~java
int price = 100;
int Price = 200;

System.out.println(price); // 100
System.out.println(Price); // 200
~~~

Cả hai khai báo có thể cùng tồn tại trong một phạm vi vì tên khác nhau, nhưng cách đặt như vậy dễ gây nhầm lẫn và thường nên tránh.

Tương tự:

- `String` là tên lớp thư viện chuẩn; `string` không tự động được hiểu là `String`.
- `println` và `printLn` không phải một tên phương thức.
- `getName` và `getname` là hai tên khác nhau.
- `Customer` và `customer` là hai tên khác nhau (nếu được khai báo).
- `TRUE` có thể là một tên biến thông thường, còn `true` là literal boolean.

Ví dụ sai tên:

~~~java
int total = 10;
// System.out.println(Total); // Lỗi biên dịch: không có biến Total
~~~

`javac` thường báo `cannot find symbol` cho `Total`. Cách sửa là dùng **`total`** đúng với khai báo, thay vì đổi tên tùy tiện ở nhiều nơi.

**Không nhầm `case-sensitive` với cách hệ điều hành lưu tệp.** Hệ thống tệp Windows và Linux có thể xử lý chữ hoa/thường trong tên file khác nhau, nhưng **tên biến/lớp/method trong Java vẫn phân biệt hoa/thường**.

## 4. Từ khóa, literal và tên bị hạn chế

Một tên có thể dùng ký tự hoàn toàn hợp lệ nhưng vẫn bị từ chối vì trùng **từ dành riêng** hoặc xuất hiện ở **ngữ cảnh cú pháp bị hạn chế**:

- `class`, `int`, `return`, `const`, `goto` là các từ khóa dành riêng, không dùng làm tên biến.
- `true`, `false`, `null` là **literal**, không phải từ khóa, nhưng cũng không thể làm tên biến.
- `var`, `record`, `yield`, `sealed`, `permits` chịu những quy tắc theo ngữ cảnh riêng; ví dụ `int var = 7;` hợp lệ, còn `var` bị hạn chế ở vị trí tên kiểu.
- `_` đứng một mình bị dành riêng từ Java 9, nhưng `_count` và `__` là những tên hợp lệ trên Java 17.

~~~java
int _count = 5;
int __ = 6;
int var = 7;
// int _ = 8;      // Sai: _ đứng một mình.
// int class = 9;  // Sai: class là keyword.
~~~

Điểm cần nhớ khi **kiểm tra tên định danh** là tách việc kiểm tra ký tự khỏi việc kiểm tra từ dành riêng và vị trí sử dụng. Các bài về keyword theo ngữ cảnh và `_`/`const`/`goto` phân tích sâu hơn những trường hợp này.

## 5. Ví dụ Java 17 chạy được: các tên tương tự nhưng khác nhau

Tạo tệp tên chính xác **`IdentifierDemo.java`** với nội dung:

~~~java
public final class IdentifierDemo {
    private IdentifierDemo() {}

    public static void main(String[] args) {
        int count = 2;
        int Count = 5;
        int _count = 7;
        int $count = 9;
        int sốLượng = 3;

        System.out.println("count = " + count);
        System.out.println("Count = " + Count);
        System.out.println("_count = " + _count);
        System.out.println("$count = " + $count);
        System.out.println("soLuong = " + sốLượng);
    }
}
~~~

Mở terminal trong thư mục chứa file.

**Bash (Linux/macOS):**

~~~bash
java -version
javac -version
javac --release 17 -encoding UTF-8 IdentifierDemo.java
java -cp . IdentifierDemo
~~~

**PowerShell (Windows):**

~~~powershell
java -version
javac -version
javac --release 17 -encoding UTF-8 .\IdentifierDemo.java
java -cp . IdentifierDemo
~~~

Đầu ra dự kiến:

~~~text
count = 2
Count = 5
_count = 7
$count = 9
soLuong = 3
~~~

Qua chương trình này:

- `count` và `Count` không cùng biến.
- `_count` và `$count` hợp lệ về mặt cú pháp, dù không phải lựa chọn thông thường để đặt tên biến ứng dụng.
- `sốLượng` hợp lệ vì Java hỗ trợ Unicode; cờ `-encoding UTF-8` giúp compiler đọc source lưu bằng UTF-8 chính xác.
- Tên class `IdentifierDemo` phải khớp tệp nguồn `IdentifierDemo.java` vì đây là top-level `public class`. Khi chạy class mode, dùng tên class `IdentifierDemo`, không thêm `.java`.

**Khuyến nghị thực tế:** mặc dù Java cho phép nhiều ký tự Unicode, trong các dự án nhiều công cụ và nhiều quốc gia, nhóm phát triển thường thống nhất cách đặt tên dễ nhập, dễ tìm kiếm và tương thích với quy ước dự án. Đây là **quy ước**, không phải giới hạn cú pháp của Java.

## 6. Tạo lỗi có chủ đích và biết đọc thông báo compiler

Tạo một file thử tên **`BadIdentifiers.java`**:

~~~java
public class BadIdentifiers {
    public static void main(String[] args) {
        int 2items = 4;
        int total-price = 100;
        int class = 3;
        int _ = 5;
    }
}
~~~

Lệnh:

~~~bash
javac --release 17 BadIdentifiers.java
~~~

**Kết quả:** `javac` **không tạo được class file mới thành công** cho mã sai này; sẽ báo lỗi cú pháp/từ khóa. Nội dung chi tiết và số dòng báo lỗi có thể khác theo JDK.

Cách sửa những khai báo sai:

~~~java
int items2 = 4;
int totalPrice = 100;
int classNumber = 3;
int _value = 5;
~~~

Những tên sửa đều hợp lệ. Tuy nhiên, **không phải đổi tên là đủ để chương trình đúng nghiệp vụ**: vẫn cần đặt tên mang ý nghĩa thực sự của từng giá trị.

### Lỗi do sai chữ hoa/thường

~~~java
int maxScore = 100;
// System.out.println(maxscore); // Sai: maxscore chưa khai báo
System.out.println(maxScore);     // Đúng
~~~

Nếu gặp `cannot find symbol: variable maxscore`, hãy đối chiếu ký tự với dòng khai báo; không tự suy đoán Java đã chuyển tên về chữ thường.

## 7. Phân biệt quy tắc bắt buộc và quy ước đặt tên

Java cho phép nhiều tên hợp lệ, nhưng code rõ ràng nên dùng quy ước thống nhất. Các quy ước phổ biến trong Java:

| Thành phần | Quy ước thường dùng | Ví dụ |
|---|---|---|
| Biến và tham số | `lowerCamelCase` | `itemCount`, `unitPrice` |
| Method | `lowerCamelCase` | `calculateTotal()`, `isAvailable()` |
| Class và interface | `UpperCamelCase` | `OrderService`, `Runnable` |
| Hằng số `static final` | `UPPER_SNAKE_CASE` | `MAX_RETRIES`, `DEFAULT_PORT` |
| Package | Chữ thường, các phần ngăn bằng dấu `.` | `com.example.catalog` |
| Enum type | `UpperCamelCase` | `OrderStatus` |
| Enum constants | `UPPER_SNAKE_CASE` thường thấy | `PENDING`, `IN_PROGRESS` |

**Cú pháp hợp lệ không đồng nghĩa bắt buộc đặt tên theo bảng.** Ví dụ biến `MAX_RETRIES` hợp lệ dù không `static final`; nhưng dùng chữ hoa như hằng số sẽ khiến người đọc hiểu nhầm. Ngược lại, compiler không bắt mọi tên class đều bắt đầu bằng chữ hoa.

Tránh những tên quá ngắn hoặc che mất ý nghĩa (`x1`, `aaa`, `temp2`) khi không có lý do rõ ràng. Với vòng lặp rất ngắn, `i` hoặc `j` vẫn có thể chấp nhận khi ngữ cảnh hiển nhiên.

### Phân biệt tên đầy đủ và một định danh đơn

~~~java
package com.example.catalog;

public class OrderService {
    public int calculateTotal(int unitPrice, int quantity) {
        return Math.multiplyExact(unitPrice, quantity);
    }
}
~~~

`com.example.catalog` là **tên package đầy đủ**, gồm các định danh `com`, `example`, `catalog` được ngăn bằng dấu `.`. Dấu chấm không biến toàn bộ chuỗi thành một định danh đơn.

`OrderService`, `calculateTotal`, `unitPrice`, `quantity` đều là các định danh riêng, mỗi tên có vai trò trong khai báo.

## 8. Unicode và ký tự đặc biệt: dùng đúng nhưng thận trọng

Java hỗ trợ nhiều ký tự Unicode ngoài ASCII trong tên biến/lớp:

~~~java
int sốLượng = 2;
int giá = 15;
int thànhTiền = sốLượng * giá;
System.out.println(thànhTiền); // 30
~~~

Nếu nguồn lưu UTF-8, hãy biên dịch với `javac -encoding UTF-8` (khi cần có thể kết hợp `--release 17`). Việc console hiển thị Unicode đúng còn phụ thuộc terminal và cấu hình hệ thống; **khả năng đọc tên trong source** và **khả năng hiển thị output** là hai chuyện khác nhau.

Không giả định **mọi ký tự nhìn giống chữ** đều được Java chấp nhận hoặc được chuẩn hóa giống nhau. Ví dụ một chữ có dấu có thể có những cách biểu diễn Unicode khác nhau; việc dùng không nhất quán dễ làm mã khó tìm kiếm và review. Trong đội nhóm, dùng bộ ký tự/quy ước nhất quán có thể quan trọng hơn việc tận dụng mọi ký tự Java cho phép.

### Vì sao Java cho phép `$`?

Ký tự `$` hợp lệ trong tên, nhưng **quy ước Java khuyến khích hạn chế sử dụng trong định danh do lập trình viên tạo**; một số công cụ sinh mã hoặc tên class lồng nhau cũng có thể dùng `$`. Người học nên ưu tiên `itemCount` hơn `$itemCount` để mã dễ đọc và tránh nhập nhằng với tên do công cụ sinh ra.

## 9. Cùng tên, khác phạm vi: đừng nhầm với phân biệt hoa/thường

Trong **một block**, không thể khai báo hai biến cục bộ trùng tên:

~~~java
int count = 1;
// int count = 2; // Sai: biến cục bộ count đã được định nghĩa
int Count = 2;    // Đúng về cú pháp, nhưng dễ gây nhầm
~~~

Ngoài tên, Java còn xét **phạm vi (scope)** và **ngữ cảnh**. Một tham số method, field và biến cục bộ có thể có những quy tắc đặt tên/trùng tên khác nhau.

Ví dụ về che khuất tên field:

~~~java
class Box {
    private int size = 10;

    void show(int size) {
        System.out.println(size);      // Tham số size
        System.out.println(this.size); // Field của object
    }
}
~~~

Khi gọi `new Box().show(4)`, sẽ in `4` rồi `10`. Cả hai đều tên `size`, nhưng `this.size` chỉ rõ field. Trường hợp này là **name shadowing**, không phải cơ chế Java tự bỏ qua chữ hoa/chữ thường.

## 10. Những lỗi thực tế hay gặp

| Triệu chứng | Vấn đề cần kiểm tra | Cách sửa |
|---|---|---|
| `cannot find symbol` ở tên biến | Gõ sai chữ hoa/thường, sai chính tả, nằm ngoài scope | Đối chiếu tên khai báo và phạm vi |
| `';' expected` hoặc lỗi cú pháp gần tên | Tên có dấu `-`, khoảng trắng hoặc ký hiệu lạ | Dùng định danh hợp lệ, ví dụ `totalPrice` |
| `<identifier> expected` | Tên sai vị trí, bắt đầu bằng số hoặc từ khóa | Chọn tên hợp lệ theo Java |
| `as of release 9, '_' is a keyword` (hoặc thông báo tương đương) | Dùng `_` đơn lẻ | Đổi thành `_value` hoặc tên có nghĩa |
| `variable x is already defined` | Khai báo hai biến cùng tên tại vị trí cấm trùng | Đổi tên có ý nghĩa hoặc bỏ khai báo dư |
| `class IdentifierDemo is public, should be declared ...` | Tên source và top-level public class không khớp | Đặt tệp `IdentifierDemo.java` |
| Source chứa tên Unicode biên dịch sai | Encoding không khớp | Lưu UTF-8 và dùng `-encoding UTF-8` |
| Class/method tên khác hoa thường bị thiếu | Java phân biệt `Demo` và `demo`, `run` và `Run` | Sửa đúng tên ở khai báo/lời gọi |

Khi sửa một lỗi, **đừng thay hàng loạt tên chỉ để compiler im lặng**. Đổi tên class, method hoặc public API còn có thể yêu cầu cập nhật những nơi tham chiếu và tên file tương ứng.

## 11. Bài tập có đáp án

### Bài A — tên nào hợp lệ?

Phân loại các tên sau khi dùng làm **tên biến** trong Java 17: `age`, `Age`, `age2`, `2age`, `_age`, `_`, `$age`, `class`, `true`, `total-price`.

**Đáp án:** hợp lệ: `age`, `Age`, `age2`, `_age`, `$age`. Không hợp lệ: `2age` (đầu là số), `_` (dành riêng), `class` (từ khóa), `true` (literal), `total-price` (chứa dấu trừ).

### Bài B — dự đoán đầu ra

~~~java
int score = 10;
int Score = 20;
System.out.println(score + Score);
~~~

**Đáp án:** `30`. `score` và `Score` là hai biến riêng, không phải cùng một biến viết khác kiểu.

### Bài C — sửa lỗi do case-sensitive

~~~java
String customerName = "Lan";
// System.out.println(customername);
~~~

**Đáp án:** dùng `customerName` trong lời gọi `println`, hoặc thực hiện đổi tên đồng nhất ở mọi nơi. Không phải Java tự hiểu `customername` trùng `customerName`.

### Bài D — đặt tên rõ nghĩa

Đổi ba tên `a`, `b`, `c` trong phép tính tiền hàng để người đọc đoán được ý nghĩa.

**Đáp án gợi ý:** `unitPrice`, `quantity`, `subtotal`. Tên có nghĩa phản ánh dữ liệu và công thức, dù bản thân compiler không bắt buộc.

### Bài E — Unicode và quy tắc đặt tên

Đoạn `int sốLượng = 2;` có sai chỉ vì tên chứa dấu tiếng Việt không?

**Đáp án:** Không; đó là định danh hợp lệ. Nên lưu mã nguồn bằng UTF-8, đồng bộ encoding và quy ước nhóm. Không cần đổi sang ASCII chỉ để hợp lệ về cú pháp.

## 12. Checklist hoàn thành

- [ ] Giải thích được định danh là gì và khác dấu phân cách/toán tử.
- [ ] Biết chữ số được đứng sau, nhưng không phải ký tự bắt đầu thông thường của tên.
- [ ] Phân biệt `count`, `Count`, `COUNT` trong Java.
- [ ] Nhận ra từ khóa, literal `true`/`false`/`null` và `_` đơn lẻ không hợp lệ làm tên biến.
- [ ] Biết `_` và `$` có thể là **một phần** của tên, nhưng cần cân nhắc quy ước.
- [ ] Biết Java hỗ trợ định danh Unicode, không giới hạn trong `A-Z`/`a-z`.
- [ ] Áp dụng `lowerCamelCase`, `UpperCamelCase`, `UPPER_SNAKE_CASE` đúng vai trò theo quy ước.
- [ ] Biên dịch và chạy `IdentifierDemo.java` trên JDK 17+; kết quả đúng bảng dự kiến.
- [ ] Tự tìm lỗi `cannot find symbol` do gõ sai chữ hoa/thường và lỗi đặt tên không hợp lệ.
- [ ] Phân biệt **sai tên**, **trùng tên trong scope** và **che khuất tên field**.

**Kết luận:** Một định danh Java phải dùng ký tự hợp lệ, không đụng tên bị dành riêng trong ngữ cảnh cú pháp, và được viết **đúng chữ hoa/chữ thường**. Quy tắc ngôn ngữ cho biết tên nào có thể biên dịch; quy ước đặt tên giúp chương trình rõ nghĩa, nhất quán và dễ bảo trì.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-vocabulary-003">Từ khóa dành riêng và từ khóa theo ngữ cảnh</a>

<details>
<summary>Xem chi tiết</summary>

# Từ khóa dành riêng và từ khóa theo ngữ cảnh trong Java 17

Trong Java, không phải từ nào trông giống tiếng Anh cũng được dùng làm tên biến hoặc tên lớp. Một số từ có ý nghĩa **cố định** với ngôn ngữ; một số chỉ có vai trò đặc biệt trong **cấu trúc cú pháp cụ thể**.

Ví dụ, `class` luôn là từ khóa dành riêng, nhưng `module` có thể là tên biến bình thường ở ngoài cú pháp module. Phân biệt đúng hai nhóm này giúp bạn đặt tên hợp lệ, đọc code Java 17 và hiểu thông báo lỗi của `javac`.

**Mục tiêu:** nhận diện từ khóa dành riêng, từ khóa theo ngữ cảnh, literal và identifier thường; viết/chạy một ví dụ kết hợp `var`, `record`, `yield` và `sealed`; sửa các lỗi đặt tên.

## 1. Bốn loại dễ nhầm

| Loại | Ví dụ Java 17 | Quy tắc quan trọng |
| --- | --- | --- |
| **Từ khóa dành riêng** (*reserved keyword*) | `class`, `int`, `public`, `return`, `if`, `switch` | Không được dùng làm identifier thông thường, bất kể vị trí |
| **Từ khóa theo ngữ cảnh** (*contextual/restricted keyword*) | `var`, `record`, `yield`, `sealed`, `permits`, `non-sealed`, `module`, `requires` | Chỉ có ý nghĩa đặc biệt trong những ngữ cảnh Java quy định; mỗi từ có hạn chế riêng |
| **Literal** | `true`, `false`, `null` | **Không phải từ khóa**, nhưng cũng không được dùng làm tên biến/lớp |
| **Identifier thường** | `score`, `main`, `String`, `System` | Là tên do lập trình viên khai báo hoặc tên API, chịu quy tắc đặt tên và phạm vi |

`String` là một **lớp**, không phải từ khóa kiểu primitive như `int`. `main` là tên phương thức theo quy ước điểm vào, cũng không phải từ khóa. Java phân biệt chữ hoa/thường: `Class` không phải token `class`, nhưng chọn tên `Class` dễ gây nhầm nên thường không đáng làm.

## 2. Từ khóa dành riêng: bị cấm làm tên ở mọi ngữ cảnh identifier

Các nhóm thông dụng:

| Nhóm | Từ khóa | Công dụng ví dụ |
| --- | --- | --- |
| Khai báo và truy cập | `class`, `interface`, `enum`, `public`, `private`, `protected` | Định nghĩa kiểu, chỉ định phạm vi truy cập |
| Điều khiển luồng | `if`, `else`, `for`, `while`, `switch`, `case`, `break`, `continue`, `return` | Chọn nhánh, lặp, kết thúc |
| Kiểu primitive | `boolean`, `byte`, `short`, `int`, `long`, `char`, `float`, `double` | Khai báo dữ liệu primitive |
| Đối tượng và kế thừa | `new`, `this`, `super`, `extends`, `implements`, `instanceof` | Tạo đối tượng, truy cập, quan hệ kiểu |
| Ngoại lệ | `try`, `catch`, `finally`, `throw`, `throws` | Xử lý và khai báo ngoại lệ |
| Thành viên/hành vi khác | `static`, `final`, `abstract`, `void`, `synchronized`, `volatile`, `transient`, `native`, `strictfp` | Định nghĩa cách dùng thành viên và kiểu |
| Tổ chức mã | `package`, `import`, `default`, `do`, `assert` | Package, nhập kiểu, cú pháp tương ứng |

Đây là **nhóm ví dụ minh họa**, không phải danh sách được sắp theo thứ tự xuất hiện trong đặc tả Java. Hai từ `const` và `goto` cũng **được dành riêng** dù không được Java dùng làm câu lệnh. Ký tự gạch dưới đơn lẻ `_` **không thể** làm identifier kể từ Java 9; nhưng `_value` vẫn là một identifier hợp lệ trong Java 17.

Các dòng sau **cố ý không biên dịch**:

~~~java
int class = 3;       // Sai: class là từ khóa dành riêng.
int return = 5;      // Sai: return là từ khóa dành riêng.
int _ = 1;           // Sai trên Java 17: _ bị dành riêng.
int true = 2;        // Sai: true là literal, không phải identifier.
~~~

Đổi tên để biểu thị nghĩa của giá trị:

~~~java
int classSize = 3;
int returnCount = 5;
int _value = 1;
boolean enabled = true;
~~~

**Lưu ý:** `true`, `false` và `null` là literal (`boolean`/null literal), *không nằm trong nhóm keyword*, nhưng việc dùng chúng như identifier vẫn sai. “Không phải keyword” không đồng nghĩa “dùng làm tên được”.

## 3. Từ khóa theo ngữ cảnh: vị trí cú pháp quyết định ý nghĩa

Java 17 có những từ chỉ được coi là thành phần cú pháp đặc biệt ở ngữ cảnh phù hợp:

| Từ | Khi mang nghĩa đặc biệt | Ngoài ngữ cảnh đó |
| --- | --- | --- |
| `var` | Suy luận kiểu của một biến cục bộ từ biểu thức khởi tạo | Có thể dùng `var` làm **tên biến**, nhưng không dùng làm tên kiểu do đây là *restricted type name* |
| `record` | Khai báo record, ví dụ `record Point(int x, int y) {}` | Có thể dùng `record` làm tên biến bình thường |
| `yield` | Trả một giá trị từ khối trong `switch` expression | Có thể là tên biến; một số cách dùng làm tên lời gọi phương thức không đủ điều kiện bị hạn chế |
| `sealed`, `permits` | Khai báo kiểu sealed và các kiểu được phép kế thừa/triển khai | Không tự động bị cấm làm tên biến ở mọi nơi |
| `non-sealed` | Cho phép một subtype trực tiếp của sealed type mở lại khả năng kế thừa | Đây là dạng có dấu nối đặc biệt; **không** phải một identifier thông thường |
| `module`, `open`, `requires`, `exports`, `opens`, `to`, `uses`, `provides`, `with`, `transitive` | Cú pháp khai báo module trong `module-info.java` | Nhiều từ trong số này vẫn có thể dùng làm identifier ở mã Java thường |

Một số thuật ngữ đặc tả Java phân biệt *contextual keywords*, *restricted keywords* và *restricted identifiers/type names*. Khi học, điểm cần nắm không phải tất cả cùng một quy tắc cấm, mà là **từng từ có vị trí được phép và bị hạn chế riêng**.

### Ví dụ 1: `var` không làm Java trở thành ngôn ngữ kiểu động

~~~java
var total = 12;       // Trình biên dịch suy luận total có kiểu int.
total = 15;           // Hợp lệ.
// total = "muoi lam"; // Sai: String không gán cho int.

int var = 7;          // Hợp lệ trong Java 17: var là tên biến.
~~~

`var` dùng để **suy luận kiểu của biến cục bộ**, không phải kiểu “nhận mọi giá trị”. Những đoạn sau không hợp lệ:

~~~java
// var x = null;     // Không suy luận được kiểu từ null.
// var x;            // Không có initializer để suy luận kiểu.
// class var {}      // Không hợp lệ: var là restricted type name.
~~~

Đặt tên biến `var` tuy hợp lệ nhưng dễ làm người đọc hiểu nhầm; nên chọn tên theo ý nghĩa dữ liệu.

### Ví dụ 2: `record` và `yield` chỉ đặc biệt khi dùng đúng cú pháp

~~~java
record Point(int x, int y) {} // Java 17: khai báo kiểu record.

int record = 5;                // Hợp lệ: tên biến thông thường.
~~~

Một `switch` expression có thể dùng `yield` trong khối để cung cấp giá trị:

~~~java
int score = 8;
String result = switch (score) {
    case 0 -> "Chua hoc";
    default -> {
        yield score > 0 ? "Da hoc" : "Khong hop le";
    }
};
~~~

Ở đây, `switch`, `case` và `default` là từ khóa dành riêng; `yield` mang nghĩa đặc biệt trong khối của `switch` expression. Đừng hiểu `yield` như `return` cho cả phương thức: nó trả giá trị cho **switch expression**, không tự kết thúc phương thức bao ngoài.

### Ví dụ 3: `sealed` kiểm soát kiểu con

~~~java
sealed interface Result permits Success, Failure {}

record Success(String message) implements Result {}

record Failure(int code) implements Result {}
~~~

`Result` là sealed interface; chỉ các subtype được cho phép mới trực tiếp triển khai nó. Hai record trên ngầm là `final`, nên thỏa yêu cầu đối với các kiểu trực tiếp được phép kế thừa/triển khai. Khi cần giữ một nhánh cho phép kế thừa tiếp, Java còn có modifier `non-sealed` cho subtype trực tiếp thích hợp.

Không nên cố dùng `non-sealed` như một tên biến có dấu `-`: dấu nối ở đây thuộc cú pháp đặc biệt, không phải ký tự hợp lệ tùy ý trong identifier.

## 4. Chương trình Java 17+ chạy được: so sánh ngay trong mã

Tạo tệp **`KeywordContextDemo.java`**:

~~~java
public class KeywordContextDemo {
    record Session(String title, int minutes) {}

    sealed interface Outcome permits Good, Bad {}

    record Good(String message) implements Outcome {}

    record Bad(int code) implements Outcome {}

    static String label(int minutes) {
        return switch (minutes) {
            case 0 -> "chua hoc";
            default -> {
                yield minutes > 0 ? "da hoc" : "gia tri am";
            }
        };
    }

    public static void main(String[] args) {
        int module = 2;  // module là tên biến hợp lệ ở đây.
        int record = 3;  // record là tên biến hợp lệ ở đây.

        var session = new Session("Java", 45);
        Outcome outcome = new Good("ok");

        System.out.println("module=" + module);
        System.out.println("record=" + record);
        System.out.println(
                "session=" + session.title() + ":" + session.minutes());
        System.out.println("label(0)=" + label(0));
        System.out.println("label(3)=" + label(record));
        System.out.println(
                "outcome=" + outcome.getClass().getSimpleName());
    }
}
~~~

**Giải thích:** `record Session` khai báo một record; `int record = 3` dùng cùng chuỗi ký tự làm tên biến ở ngữ cảnh khác. `var session` suy luận kiểu `Session`. `yield` trả giá trị cho `switch` expression. `sealed` và `permits` đặt ranh giới các subtype của `Outcome`. `module` là tên biến, không phải khai báo module.

### Biên dịch và chạy (Windows PowerShell hoặc Linux/Bash)

Lưu mã UTF-8, mở terminal trong thư mục chứa tệp, xác minh JDK 17+:

~~~bash
java -version
javac -version
~~~

Sau đó dùng cùng lệnh ở cả hai môi trường:

~~~bash
javac --release 17 -encoding UTF-8 KeywordContextDemo.java
java KeywordContextDemo
~~~

**Kết quả dự kiến:**

~~~text
module=2
record=3
session=Java:45
label(0)=chua hoc
label(3)=da hoc
outcome=Good
~~~

`--release 17` yêu cầu compiler hỗ trợ cú pháp và API nền tảng Java 17. Nếu dùng đúng JDK 17, chỉ cần `javac -encoding UTF-8 KeywordContextDemo.java` cũng đủ cho ví dụ này.

Một lợi ích của chương trình là **không cần tự tạo package, module hoặc thư viện ngoài** để thấy sự khác biệt giữa các ngữ cảnh.

## 5. Vì sao tên trong `module-info.java` trông như keyword?

Hệ thống module của Java có cú pháp dành riêng cho tệp khai báo module. Ví dụ **minh họa cú pháp**, không phải một dự án hoàn chỉnh:

~~~java
module learning.demo {
    requires java.logging;
}
~~~

Trong nội dung `module-info.java`, từ `module` và `requires` là thành phần cú pháp module declaration. Nhưng ở chương trình thông thường, `int module = 2;` vẫn hợp lệ. Đừng đặt đoạn minh họa module này vào trong lớp `KeywordContextDemo`: đó là **vị trí cú pháp khác** và cần dự án module được bố trí tương ứng.

Ví dụ này chỉ nhằm nhận biết *context*. Việc tổ chức module path, đóng gói module và `requires` nằm ngoài mục tiêu bài học về từ vựng Java.

## 6. Lỗi biên dịch thường gặp và cách sửa

| Đoạn mã / tình huống | Loại lỗi hoặc nhầm lẫn | Cách xử lý |
| --- | --- | --- |
| `int class = 1;` | `class` là reserved keyword | Đổi thành `classCount` |
| `int _ = 1;` | `_` bị dành riêng trong Java 17 | Đổi thành `_count` hoặc `count` |
| `int true = 1;` | `true` là literal, không là identifier | Dùng `boolean enabled = true;` |
| `class record {}` | `record` bị hạn chế khi làm tên kiểu | Đổi tên lớp, ví dụ `RecordExample` |
| `var value = null;` | Không thể suy luận kiểu | Dùng kiểu tường minh, nếu null là giá trị hợp lệ |
| `var number;` | Không có initializer | Khởi tạo khi khai báo hoặc chọn kiểu tường minh |
| `yield 1;` ngoài ngữ cảnh cho phép | `yield` không phải statement trả giá trị chung | Dùng `return` trong phương thức hoặc `yield` trong switch expression phù hợp |
| `non-sealed` ở lớp không liên quan sealed hierarchy | Modifier không hợp lệ ở vị trí đó | Chỉ dùng trên subtype trực tiếp của sealed type khi đáp ứng quy tắc |

**Phân biệt:** từ khóa sai vị trí có thể dẫn đến **lỗi cú pháp**; tên kiểu bị hạn chế có thể dẫn đến lỗi **khai báo**; `var` không thể suy luận kiểu thường dẫn đến lỗi **kiểu**. Thông báo `javac` cụ thể thay đổi theo phiên bản, nên tập trung vào cấu trúc mã và ngữ cảnh.

## 7. Bài tập thực hành

**Bài 1 — Phân loại:** Trong `class`, `record`, `main`, `true`, `var`, `int`, `String`, `null`, hãy chia thành keyword dành riêng, contextual/restricted keyword, literal và identifier/tên API.

*Đáp án:* Dành riêng: `class`, `int`. Theo ngữ cảnh/hạn chế: `record`, `var`. Literal: `true`, `null`. Tên thường/API: `main`, `String`.

**Bài 2 — Dự đoán:** Vì sao `int module = 2;` chạy được nhưng `int class = 2;` không biên dịch?

*Đáp án:* `module` có nghĩa đặc biệt trong ngữ cảnh khai báo module, còn `class` luôn là reserved keyword.

**Bài 3 — Tự thử lỗi:** Trong chương trình `KeywordContextDemo.java`, thêm thử `int return = 1;` vào `main`, biên dịch, đọc thông báo rồi bỏ dòng sai. Có thể dùng `returnCount` thay thế không?

*Đáp án:* Có, `returnCount` là identifier hợp lệ; `return` thì không.

**Bài 4 — Thay đổi switch:** Gọi thêm `label(-1)` rồi dự đoán kết quả. Giải thích vì sao `yield` trong nhánh `default` có thể đưa kết quả về biến/lời gọi chứa `switch` expression.

*Đáp án:* Trả chuỗi `gia tri am`. `yield` cung cấp giá trị của `switch` expression.

**Bài 5 — Không nhầm từ khóa với tên API:** Tại sao `String`, `System`, `println`, `main` không phải keyword, dù xuất hiện nhiều trong chương trình Java?

*Đáp án:* Chúng là tên kiểu/phương thức theo thư viện hoặc quy ước, chứ không phải token cú pháp dành riêng của ngôn ngữ. Cách sử dụng chúng phải đúng khai báo, phạm vi và kiểu.

## Checklist ghi nhớ

- [ ] Nhận diện reserved keyword như `class`, `if`, `return` và `int`.
- [ ] Không dùng `true`, `false`, `null` hoặc `_` làm identifier.
- [ ] Giải thích được vì sao `var` chỉ suy luận kiểu ở những vị trí cho phép nhưng biến vẫn có kiểu tĩnh.
- [ ] Phân biệt `record` dùng khai báo kiểu với `record` làm tên biến.
- [ ] Biết `yield` có ý nghĩa đặc biệt trong `switch` expression.
- [ ] Nhận diện `sealed`, `permits`, `non-sealed` mà không nhầm chúng thành tên tự do.
- [ ] Biết `module`, `requires` và các từ liên quan có ngữ cảnh khai báo module.
- [ ] Biên dịch, chạy được ví dụ bằng `javac --release 17` và `java`.

**Ghi nhớ:** Từ khóa **dành riêng** bị khóa ở mọi vị trí identifier; từ khóa **theo ngữ cảnh** chỉ mang ý nghĩa cú pháp đặc biệt ở cấu trúc thích hợp và có những giới hạn riêng. `true`/`false`/`null` là literal, không phải keyword, nhưng cũng không phải tên hợp lệ. Khi không chắc, hãy nhìn **ngữ cảnh cú pháp** và thử biên dịch một ví dụ Java 17 tối thiểu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-vocabulary-004">Phân biệt true, false, null với từ khóa</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt `true`, `false`, `null` với từ khóa trong Java

## 1. Điểm mấu chốt: literal không phải keyword

Trong Java 17, **`true`** và **`false`** là hai *boolean literals* (giá trị hằng trực tiếp kiểu `boolean`); **`null`** là *null literal* (giá trị đặc biệt biểu thị không có tham chiếu tới đối tượng).

Chúng dễ bị nhầm là từ khóa vì có cách viết được ngôn ngữ dành riêng và **không được dùng làm tên định danh**. Nhưng theo phân loại từ vựng Java, cả ba là **literal, không phải keyword**.

| Chuỗi trong source | Phân loại | Kiểu/ý nghĩa | Dùng làm tên biến? |
|---|---|---|---|
| `true` | Boolean literal | Giá trị `boolean` đúng | Không |
| `false` | Boolean literal | Giá trị `boolean` sai | Không |
| `null` | Null literal | Giá trị null, chuyển được sang kiểu tham chiếu phù hợp | Không |
| `boolean` | Keyword | Tên kiểu nguyên thủy boolean | Không |
| `if`, `else` | Keywords | Cấu trúc điều khiển | Không |
| `class`, `return` | Keywords | Khai báo lớp / trả kết quả | Không |
| `Boolean` | Tên lớp trong `java.lang` | Wrapper object cho boolean | Có thể dùng để tham chiếu lớp; **không phải keyword** |

**Ghi nhớ:** Đừng nói “`true` là từ khóa của kiểu `boolean`”. Câu chính xác là “`true` là **boolean literal**; `boolean` là **keyword** dùng để viết tên kiểu nguyên thủy”.

## 2. Boolean literals: `true` và `false`

Khai báo và gán giá trị:

~~~java
boolean loggedIn = true;
boolean suspended = false;

if (loggedIn && !suspended) {
    System.out.println("ALLOW");
} else {
    System.out.println("DENY");
}
~~~

Kết quả là `ALLOW`. Trong đoạn này:

- `boolean` là keyword cho kiểu dữ liệu.
- `true` và `false` là các literal, không phải tên biến.
- `loggedIn` và `suspended` là hai định danh do lập trình viên chọn.
- `if` và `else` là keywords, còn biểu thức `loggedIn && !suspended` tạo ra một giá trị `boolean`.

**Java không tự xem số nguyên là boolean.** Những đoạn sau sai cú pháp/kiểu:

~~~java
// boolean ready = 1;    // Sai: int không tự đổi thành boolean
// if (1) { }            // Sai: if cần điều kiện boolean
// boolean done = "true"; // Sai: String khác boolean
~~~

Nếu bạn nhận chuỗi `"true"` từ đầu vào, đó vẫn là **`String`**, chưa phải boolean literal trong mã nguồn. Có thể chuyển bằng API thích hợp, ví dụ `Boolean.parseBoolean("true")`.

### Phân biệt chữ hoa, chữ thường

Java phân biệt hoa/thường:

- `true` và `false` viết thường chính xác là literal.
- `True`, `False`, `TRUE`, `FALSE` **không tự động là literal**. Chúng có thể là định danh nếu được khai báo hợp lệ, hoặc gây lỗi tên chưa khai báo.
- `Boolean.TRUE` và `Boolean.FALSE` là **các field của lớp `Boolean`**, không phải chính cách viết literal `true` và `false`.

Ví dụ:

~~~java
boolean ready = true;
boolean approved = Boolean.TRUE;  // Unboxing từ Boolean sang boolean
// boolean wrong = True;         // Lỗi nếu không có biến/field tên True
~~~

Trong ứng dụng Java, nên dùng `true`/`false` trực tiếp khi cần giá trị `boolean`; dùng `Boolean` khi cần kiểu đối tượng theo hợp đồng API.

## 3. `null`: không có tham chiếu, không phải chuỗi "null"

`null` có thể được gán cho biến thuộc **kiểu tham chiếu** phù hợp:

~~~java
String name = null;
Integer score = null;
int[] numbers = null;
Object object = null;
~~~

Một biến tham chiếu chứa `null` nghĩa là **không tham chiếu đến object nào** vào thời điểm đó. Điều này không có nghĩa object bị xóa ngay, và cũng không phải một đối tượng `String` đặc biệt.

So sánh:

~~~java
String missing = null;
String text = "null";

System.out.println(missing == null); // true
System.out.println(text == null);    // false
System.out.println("null".equals(text));    // true
System.out.println("null".equals(missing)); // false
~~~

`"null"` là một chuỗi gồm bốn ký tự, hoàn toàn khác **null literal**.

**Không thể gán `null` cho kiểu primitive** như `int` và `boolean`:

~~~java
// int count = null;         // Sai ở compile time
// boolean enabled = null;   // Sai ở compile time
~~~

Một biến `Boolean` có thể nhận `null` vì `Boolean` là lớp wrapper (kiểu tham chiếu); biến `boolean` thì chỉ có hai giá trị `true` hoặc `false`.

### Kiểu của biểu thức `null`

Theo đặc tả Java, null literal có **null type** đặc biệt (không có tên kiểu để khai báo trực tiếp). Giá trị null có thể chuyển sang các kiểu tham chiếu, nhưng không phải mọi biểu thức Java đều có thể suy ra kiểu từ `null` đơn lẻ:

~~~java
String label = null;   // Đúng
Object value = null;   // Đúng
// var unknown = null; // Sai: var không suy ra kiểu thích hợp
~~~

Đừng nhầm **kiểu của null literal** với tên lớp `Null`: Java không có lớp chuẩn tên `Null` tương đương một “đối tượng null”.

## 4. Kiểm tra null và tránh NullPointerException

Một phép kiểm tra null thông dụng là:

~~~java
String userName = null;
boolean hasName = userName != null && !userName.isBlank();
System.out.println(hasName); // false
~~~

`&&` có tính **ngắn mạch**: nếu `userName != null` là `false` thì JVM không đánh giá `!userName.isBlank()`. Nhờ vậy không gọi method qua tham chiếu null.

Ngược lại:

~~~java
// String userName = null;
// System.out.println(userName.length()); // Ném NullPointerException
~~~

Đoạn trên **hợp lệ khi biên dịch** nhưng ném `NullPointerException` khi được thực thi. Đây là lỗi runtime, không phải bằng chứng `null` là keyword.

So sánh nội dung chuỗi an toàn hơn với literal:

~~~java
String state = null;
System.out.println("ACTIVE".equals(state)); // false
~~~

Trong các trường hợp kiểm tra hai đối tượng có thể null, `java.util.Objects.equals(a, b)` có thể phù hợp. Nó trả `true` khi cả hai cùng null, hoặc khi hai giá trị khác null được xem là bằng nhau theo `equals`.

**Bẫy:** `instanceof` với một giá trị null cho ra `false`:

~~~java
String s = null;
System.out.println(s instanceof String); // false
~~~

Một biến được khai báo kiểu `String` vẫn có thể chứa null; `instanceof` không kết luận rằng một giá trị null là instance của `String`.

## 5. Khi Boolean wrapper có null: unboxing cần cẩn thận

Java có hai kiểu dễ nhầm:

| Kiểu | Bản chất | Các giá trị có thể giữ |
|---|---|---|
| `boolean` | Kiểu nguyên thủy | `true`, `false` |
| `Boolean` | Kiểu tham chiếu (wrapper) | Đối tượng `Boolean` hoặc `null` |

Ví dụ:

~~~java
Boolean answer = null;
// if (answer) { } // Hợp lệ về kiểu, nhưng gây NullPointerException khi unboxing
~~~

Điều kiện `if` có thể nhận `Boolean` nhờ **auto-unboxing**, nhưng nếu tham chiếu là `null`, việc chuyển sang `boolean` sẽ gây `NullPointerException`.

Hai cách có ý nghĩa khác nhau:

~~~java
Boolean answer = null;

boolean definitelyTrue = Boolean.TRUE.equals(answer); // false
boolean hasValue = answer != null;                    // false
~~~

Cách đầu chỉ đúng nếu wrapper giữ giá trị true; cách sau chỉ kiểm tra **có giá trị hay không**, không quan tâm giá trị đó là true hay false.

Nếu nghiệp vụ có ba trạng thái “đúng, sai, chưa biết”, `Boolean` cho phép biểu diễn cả ba, nhưng phải quy định rõ cách xử lý null. Không tùy tiện xem null luôn là false: trong nhiều tình huống, “chưa biết” khác hoàn toàn “sai”.

## 6. Demo Java 17 chạy được: quan sát cả ba literal

Tạo tệp **`LiteralDemo.java`**:

~~~java
public final class LiteralDemo {
    private LiteralDemo() {}

    static class Defaults {
        boolean ready; // Mặc định false
        String note;   // Mặc định null
    }

    public static void main(String[] args) {
        boolean enabled = true;
        boolean blocked = false;
        String name = null;
        String word = "null";
        Boolean optional = null;

        System.out.println("enabled = " + enabled);
        System.out.println("blocked = " + blocked);
        System.out.println("name == null: " + (name == null));
        System.out.println("\"null\" == null: " + (word == null));
        System.out.println("word equals null text: " + "null".equals(word));
        System.out.println("name is nonblank: "
                + (name != null && !name.isBlank()));
        System.out.println("Boolean.TRUE.equals(optional): "
                + Boolean.TRUE.equals(optional));
        System.out.println("optional == null: " + (optional == null));
        System.out.println("parseBoolean(TRUE): "
                + Boolean.parseBoolean("TRUE"));
        System.out.println("parseBoolean(yes): "
                + Boolean.parseBoolean("yes"));
        System.out.println("null instanceof String: "
                + (name instanceof String));

        Defaults defaults = new Defaults();
        System.out.println("default ready: " + defaults.ready);
        System.out.println("default note is null: "
                + (defaults.note == null));

        if (enabled && !blocked) {
            System.out.println("decision: ALLOW");
        }
    }
}
~~~

Với **JDK 17 hoặc mới hơn**, mở terminal tại thư mục chứa file.

**Bash (Linux/macOS):**

~~~bash
java -version
javac -version
javac --release 17 -encoding UTF-8 LiteralDemo.java
java -cp . LiteralDemo
~~~

**PowerShell (Windows):**

~~~powershell
java -version
javac -version
javac --release 17 -encoding UTF-8 .\LiteralDemo.java
java -cp . LiteralDemo
~~~

Đầu ra kỳ vọng:

~~~text
enabled = true
blocked = false
name == null: true
"null" == null: false
word equals null text: true
name is nonblank: false
Boolean.TRUE.equals(optional): false
optional == null: true
parseBoolean(TRUE): true
parseBoolean(yes): false
null instanceof String: false
default ready: false
default note is null: true
decision: ALLOW
~~~

Giải thích thêm về `Boolean.parseBoolean(String)`: kết quả chỉ `true` khi chuỗi đầu vào biểu thị `"true"` (không phân biệt hoa/thường). Các chuỗi khác như `"yes"`, `"1"` và cả null cho kết quả `false`, **không ném lỗi nhập liệu**. Vì vậy, nếu nghiệp vụ phải từ chối đầu vào không hợp lệ, bạn cần kiểm tra chuỗi được phép thay vì chỉ gọi `parseBoolean`.

### Giá trị mặc định không áp dụng cho mọi biến

Trong `Defaults`, field `boolean ready` có mặc định `false`, còn field tham chiếu `String note` có mặc định `null`. Đây là quy tắc **khởi tạo field** của Java.

**Biến cục bộ** trong method không được tùy ý đọc khi chưa gán chắc chắn:

~~~java
// Trong method:
// boolean flag;
// System.out.println(flag); // Lỗi compile: biến cục bộ chưa được khởi tạo
~~~

Đừng suy ra vì field boolean mặc định false nên mọi biến boolean cục bộ cũng được cấp sẵn false.

## 7. Những biểu thức sai cần biết cách đọc lỗi

Các ví dụ **cố tình sai** dưới đây không nên đặt vào file chạy chính:

~~~java
// boolean a = True;       // Tên True chưa khai báo (nếu không có định danh đó)
// int x = null;           // null không chuyển sang int
// boolean ok = null;      // null không chuyển sang boolean primitive
// var item = null;        // Không suy ra được kiểu chỉ từ null
// int true = 1;           // true là boolean literal, không phải tên biến
// String null = "x";      // null literal không phải tên biến
// int class = 1;          // class là keyword
~~~

| Lỗi | Giai đoạn | Bản chất |
|---|---|---|
| Dùng `true` làm tên biến | Compile | Literal không được làm định danh |
| Dùng `class` làm tên biến | Compile | Keyword không được làm định danh |
| Gán `null` cho `int` | Compile | Null không chuyển sang primitive |
| `var x = null` | Compile | Không suy ra kiểu phù hợp |
| Gọi `name.length()` khi name null | Runtime | `NullPointerException` |
| Auto-unbox `Boolean` null | Runtime | `NullPointerException` |
| Nhận `"yes"` từ `parseBoolean` | Chạy đúng theo API | Trả false; cần validation nếu muốn báo dữ liệu sai |

Compiler không tự xác định ý nghĩa nghiệp vụ của `null`. Với API thật, hãy hỏi liệu null có nghĩa là “không có dữ liệu”, “chưa nhập”, hay “lỗi”, rồi chọn mô hình dữ liệu và validation phù hợp.

## 8. Không nhầm boolean với điều kiện và giá trị ba trạng thái

`true`/`false` là **giá trị**; `if`/`while` là **keywords điều khiển luồng**, còn phần trong ngoặc phải tạo ra một biểu thức boolean hợp lệ.

~~~java
boolean paid = true;
if (paid) {
    System.out.println("Da thanh toan");
}
~~~

Ở đây:

- `paid` là định danh, `true` là literal gán cho biến.
- `if` là keyword; `paid` là điều kiện boolean.
- `"Da thanh toan"` là string literal dùng để in, không phải giá trị boolean.

Java không có **primitive boolean thứ ba** biểu thị null. Khi cần trạng thái “chưa biết” cùng đúng/sai, bạn có thể dùng một mô hình minh bạch như enum:

~~~java
enum ApprovalState {
    APPROVED, REJECTED, UNKNOWN
}
~~~

Enum tránh phải đoán ý nghĩa `null`; tuy vậy nó chỉ đáng dùng khi nghiệp vụ thật sự có ba trạng thái và các luồng xử lý cần phân biệt.

## 9. Một số edge cases đáng nhớ

**1. `null == null` là true.** Cả hai vế là null literal nên phép so sánh tham chiếu phù hợp, kết quả là `true`. Nhưng nó không khiến null trở thành object có thể gọi method.

**2. `"false"` khác `false`.** Chuỗi `"false"` là `String`, còn `false` là `boolean`. Không thể viết `boolean valid = "false";` rồi trông đợi Java tự ép kiểu.

**3. `Boolean.FALSE` không phải null.** `Boolean.FALSE` là đối tượng wrapper biểu thị false; nếu field `Boolean` null, cần kiểm tra riêng trạng thái thiếu giá trị.

**4. `== null` thích hợp để kiểm tra có tham chiếu hay không.** Nhưng đừng dùng `==` để mặc định so sánh **nội dung** hai `String` không null; thường dùng `equals`.

**5. `null` có thể gây mơ hồ overload.** Nếu có hai overload không có quan hệ kiểu đủ rõ, như `work(String)` và `work(Integer)`, gọi `work(null)` có thể lỗi compile vì cả hai đều nhận null nhưng không có overload rõ ràng tốt hơn. Có thể ép kiểu `work((String) null)` nếu đó là chủ đích.

**6. Lưu ý `switch` với null trên Java 17.** Một switch expression/statement truyền thống nhận selector tham chiếu null thường có thể ném `NullPointerException` khi đánh giá. Không tự coi null là nhánh `default`; cần kiểm tra null trước hoặc dùng kỹ thuật phù hợp với phiên bản Java và yêu cầu.

**7. Null không tự đồng nghĩa “dữ liệu không tồn tại vĩnh viễn”.** Một biến đang null có thể được gán tham chiếu khác sau đó nếu cho phép gán lại. Trạng thái null là trạng thái của **giá trị tham chiếu**, không phải nhãn riêng bất biến của object.

## 10. Bài tập ngắn kèm đáp án

### Bài A — phân loại từ vựng

Phân loại các token `boolean`, `true`, `false`, `null`, `if`, `Boolean`.

**Đáp án:** `boolean` và `if` là keywords; `true`/`false` là boolean literals; `null` là null literal; `Boolean` là tên lớp wrapper trong `java.lang` (không phải keyword).

### Bài B — đúng hay lỗi?

~~~java
boolean x = false;
Boolean y = null;
String z = "null";
~~~

**Đáp án:** cả ba khai báo hợp lệ. `x` là primitive false, `y` là tham chiếu null, `z` là một chuỗi không null có nội dung `null`.

### Bài C — dự đoán đầu ra

~~~java
String s = null;
System.out.println(s == null);
System.out.println("abc".equals(s));
System.out.println(s instanceof String);
~~~

**Đáp án:**

~~~text
true
false
false
~~~

Không có lời gọi method thông qua `s`, nên không xảy ra `NullPointerException`.

### Bài D — chọn cách tránh lỗi unboxing

~~~java
Boolean flag = null;
// if (flag) System.out.println("ON");
~~~

**Đáp án:** `if (flag)` sẽ gây `NullPointerException` khi Java tự unbox giá trị null. Nếu mong muốn chỉ cho phép true, viết `if (Boolean.TRUE.equals(flag)) { ... }`. Nếu null mang ý nghĩa “chưa xác nhận”, xử lý riêng thay vì âm thầm quy thành false.

### Bài E — API nhận chuỗi từ người dùng

Ứng dụng chỉ chấp nhận `true` hoặc `false` (bất kể hoa/thường). Người dùng nhập `yes`. Có nên dùng `Boolean.parseBoolean("yes")` và coi đó là dữ liệu hợp lệ không?

**Đáp án:** Không. `parseBoolean("yes")` trả false theo API; nếu hệ thống yêu cầu **chỉ nhận hai chuỗi**, phải validation trước và từ chối `yes` bằng thông báo thích hợp.

## 11. Checklist tự kiểm tra

- [ ] Nêu đúng `true`/`false` là **boolean literals**, `null` là **null literal**; không gọi chúng là Java keywords.
- [ ] Phân biệt keyword `boolean` với literal `true` và `false`.
- [ ] Hiểu `Boolean` là class wrapper, khác primitive `boolean`.
- [ ] Biết `null` không phải string `"null"` và không gán được cho `int`/`boolean`.
- [ ] Không dùng ba literal `true`, `false`, `null` làm tên biến.
- [ ] Dùng `== null`, `!= null && ...` và `"literal".equals(value)` đúng mục đích.
- [ ] Phân biệt lỗi compile (kiểu không hợp lệ) với runtime `NullPointerException`.
- [ ] Biết field primitive boolean mặc định false, field tham chiếu mặc định null, nhưng biến cục bộ cần gán trước khi đọc.
- [ ] Chạy thành công `LiteralDemo.java` bằng `javac`/`java` và đối chiếu output.
- [ ] Không coi test chạy được là bằng chứng null có cùng ý nghĩa trong mọi nghiệp vụ.

**Kết luận:** **`true` và `false` là literal kiểu `boolean`; `null` là null literal; cả ba không phải từ khóa Java**, dù được dành riêng và không được dùng làm định danh. Từ khóa như `boolean`, `if`, `class` tạo cấu trúc ngôn ngữ; literal biểu diễn giá trị. Hiểu đúng phân loại này giúp đọc mã chính xác, tránh lỗi kiểu và xử lý null có chủ đích.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-vocabulary-005">Giới hạn của tên _, const và goto</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn của tên `_`, `const` và `goto` trong Java 17+

Ba trường hợp dễ gây nhầm khi đặt tên trong Java là dấu gạch dưới đơn lẻ `_` cùng hai từ `const` và `goto`. Chúng **không được dùng làm identifier** (tên biến, lớp, phương thức, v.v.) trên Java 17, nhưng lý do và lịch sử của chúng không hoàn toàn giống nhau:

- `_` (chỉ một dấu gạch dưới) trở thành từ khóa dành riêng từ **Java 9**. Dấu `_` vẫn được phép **nằm trong tên dài hơn** như `_count`, `value_` hoặc `__`.
- `const` và `goto` được Java **dành riêng**, nhưng Java **không cung cấp cú pháp khai báo hằng bằng `const` hoặc câu lệnh nhảy `goto`**.
- Thay cho `const` khi cần ngăn gán lại, Java sử dụng `final`; hằng số cấp lớp thường khai báo với `static final`. Thay cho ý tưởng nhảy vô điều kiện, Java cung cấp các cấu trúc điều khiển có quy tắc rõ ràng như `if`, `for`, `while`, `break`, `continue` và `return`.

Bài này tập trung vào **Java/JDK 17 trở lên**, chỉ ra tên nào hợp lệ, cách thay bằng cú pháp đúng và cách tự xác minh bằng `javac`.

## 1. Vì sao ba tên này không hợp lệ?

| Cách viết | Trên Java 17 | Giải thích |
| --- | --- | --- |
| `int _ = 1;` | **Lỗi biên dịch** | `_` đơn lẻ là từ khóa dành riêng |
| `int const = 2;` | **Lỗi biên dịch** | `const` là từ khóa dành riêng, dù Java không thực thi một câu lệnh `const` |
| `int goto = 3;` | **Lỗi biên dịch** | `goto` là từ khóa dành riêng, dù Java không có câu lệnh `goto` |
| `int _count = 1;` | **Hợp lệ** | Dấu gạch dưới chỉ là một phần của tên |
| `int count_ = 2;` | **Hợp lệ** | Tên không phải `_` đơn lẻ |
| `int __ = 3;` | **Hợp lệ** | Hai dấu gạch dưới tạo thành một identifier khác `_` |
| `int constValue = 4;` | **Hợp lệ** | Không trùng nguyên token `const` |
| `int gotoCount = 5;` | **Hợp lệ** | Không trùng nguyên token `goto` |
| `String text = "const goto _";` | **Hợp lệ** | Các ký tự nằm trong *string literal*, không phải identifier |

**Điểm cốt lõi:** trình biên dịch xem `const` là một token dành riêng, nhưng `constValue` là **một identifier riêng biệt**, không bị tách thành keyword `const` và phần đuôi `Value`. Tương tự với `gotoCount`.

Trong Java, tên có phân biệt chữ hoa/chữ thường. `GotoCount` không phải token `goto`, nhưng cách đặt tên vẫn nên tuân theo quy ước dễ đọc, ví dụ `gotoCount` cho biến và `GotoCounter` cho lớp.

### Quy tắc về identifier cần nhớ

Một tên Java thông thường có thể bắt đầu bằng chữ cái, dấu `_` hoặc `$` theo quy tắc ký tự Java, nhưng **không bắt đầu bằng chữ số**. Sau ký tự đầu tiên có thể có chữ số. Từ khóa dành riêng và literal như `true`, `false`, `null` cũng không dùng làm tên.

Dấu `$` hợp lệ về ngữ pháp nhưng thường dành cho mã sinh tự động hoặc tình huống đặc biệt; trong mã học tập, ưu tiên tên có nghĩa như `totalCount` hoặc `maxRetries`.

Phân biệt hai trường hợp:

~~~java
int item_2 = 10; // Hợp lệ.
int _item = 20;  // Hợp lệ.
// int 2item = 30; // Sai: bắt đầu bằng chữ số.
// int _ = 40;     // Sai trên Java 17: "_" đơn lẻ.
~~~

## 2. Thử nhìn thấy lỗi từ `javac`

Tạo tệp **`BadNames.java`** sau để **cố ý kiểm tra lỗi**:

~~~java
public class BadNames {
    public static void main(String[] args) {
        int _ = 1;
        int const = 2;
        int goto = 3;
        System.out.println("Khong chay den day");
    }
}
~~~

Chạy trong thư mục chứa tệp:

~~~bash
javac --release 17 -encoding UTF-8 BadNames.java
~~~

**Kết quả:** biên dịch **thất bại**. `javac` sẽ chỉ ra những token không thể dùng làm identifier; số lượng và lời văn chẩn đoán có thể thay đổi theo phiên bản compiler. **Không có đầu ra chương trình**, vì phương thức `main` chưa được thực thi.

Khi thử sửa lỗi, nên bỏ từng dòng sai hoặc đổi chúng thành:

~~~java
int _value = 1;
int constValue = 2;
int gotoCount = 3;
System.out.println(_value + constValue + gotoCount); // 6
~~~

Bốn câu lệnh trên là đoạn **đặt trong một phương thức**, không phải một tệp Java độc lập.

## 3. `const` không thay thế được `final` trong Java

Nếu muốn giá trị không được gán lại sau khi khởi tạo, dùng `final`:

~~~java
final int retryLimit = 3;
// retryLimit = 4; // Sai: không thể gán lại biến final.
~~~

Nếu cần một hằng số dùng chung trong lớp, một cách thông dụng là:

~~~java
static final int MAX_RETRIES = 3;
~~~

| Ý định | Cách làm Java | Lưu ý |
| --- | --- | --- |
| Không cho gán lại biến cục bộ | `final int limit = 3;` | `final` ngăn gán lại, không phải token `const` |
| Hằng số thuộc lớp | `static final int MAX_RETRIES = 3;` | Thường viết tên hằng bằng chữ hoa và `_` |
| Giá trị không đổi của chuỗi | `final String APP_NAME = "Demo";` | `String` vốn không thay đổi nội dung đối tượng |
| Không gán lại tham chiếu | `final StringBuilder b = new StringBuilder();` | **Vẫn có thể** gọi `b.append(...)` để sửa nội dung đối tượng |

Một field `static final` không nhất thiết là *compile-time constant*: điều đó còn phụ thuộc kiểu và **biểu thức khởi tạo**. Ví dụ `static final int X = 3;` là hằng số biểu thức đơn giản; `static final int X = Integer.parseInt("3");` là field chỉ được gán một lần nhưng giá trị tính lúc chạy.

**Đừng nhầm:** `final` không tự khiến mọi đối tượng trở nên bất biến (*immutable*). Nó hạn chế việc gán lại biến/tham chiếu theo quy tắc Java.

## 4. Không có `goto`: dùng điều khiển luồng có cấu trúc

Java không có câu lệnh `goto label;`. Một số cách điều khiển luồng gần với mục đích thực tế:

| Mục đích | Cấu trúc phù hợp |
| --- | --- |
| Chọn nhánh theo điều kiện | `if` / `else`, `switch` |
| Lặp nhiều lần | `for`, `while`, `do-while` |
| Thoát một vòng lặp | `break;` |
| Bỏ phần còn lại của một lượt lặp | `continue;` |
| Thoát nhiều mức lặp có chủ đích | `break label;` tại vòng/khối được gắn nhãn |
| Chuyển ngay sang lượt của vòng lặp ngoài | `continue label;` với **nhãn đặt trên một vòng lặp** |
| Kết thúc phương thức | `return;` hoặc `return value;` |

Nhãn là **identifier theo sau dấu hai chấm**. Bạn có thể viết `scan:` nhưng **không** viết `goto:` vì `goto` là từ khóa dành riêng.

Với `break outer;`, Java kết thúc statement mang nhãn `outer`. Với `continue outer;`, Java chuyển đến lượt tiếp theo của **vòng lặp** được gắn nhãn `outer`; không thể `continue` đến một nhãn đặt trên khối bất kỳ không phải vòng lặp.

Nhãn nên dùng có chừng mực: nhiều nhãn chồng chéo khiến luồng chạy khó theo dõi. Đối với tìm kiếm đơn giản, `return` từ một phương thức nhỏ thường còn rõ ràng hơn.

## 5. Ví dụ Java 17 chạy được: tên hợp lệ và nhãn thay cho `goto`

Tạo tệp **`ReservedNamesDemo.java`**:

~~~java
public class ReservedNamesDemo {
    private static final int MAX_RETRIES = 3;

    static int countProcessed(int[][] rows) {
        int processed = 0;

        scan:
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < rows[row].length; column++) {
                int value = rows[row][column];

                if (value == -1) {
                    continue scan; // Bo qua phan con lai cua hang.
                }

                if (value == 99) {
                    break scan; // Thoat ca hai vong lap.
                }

                processed++;
            }
        }
        return processed;
    }

    public static void main(String[] args) {
        int _count = 2;
        int __ = 3;
        int constValue = 5;
        int gotoCount = 7;

        int[][] rows = {
            {4, 5},
            {7, -1, 8},
            {9, 99, 10}
        };

        System.out.println("maxRetries=" + MAX_RETRIES);
        System.out.println(
                "legalNamesSum=" + (_count + __ + constValue + gotoCount));
        System.out.println("processed=" + countProcessed(rows));
    }
}
~~~

**Biên dịch/chạy trên Windows PowerShell hoặc Linux/Bash** — cùng một bộ lệnh:

~~~bash
java -version
javac -version
javac --release 17 -encoding UTF-8 ReservedNamesDemo.java
java ReservedNamesDemo
~~~

Kết quả dự kiến:

~~~text
maxRetries=3
legalNamesSum=17
processed=4
~~~

### Tại sao kết quả là 4?

Dữ liệu được duyệt lần lượt:

1. Hàng đầu tiên chứa `4`, `5`: cả hai được tính, `processed = 2`.
2. Hàng thứ hai: `7` được tính, `processed = 3`. Gặp `-1` thì `continue scan;` **bỏ qua số 8** và chuyển sang hàng thứ ba.
3. Hàng thứ ba: `9` được tính, `processed = 4`. Gặp `99` thì `break scan;` thoát cả hai vòng lặp, **không xét số 10**.

Lệnh `continue scan` và `break scan` có phạm vi điều khiển xác định, khác với một lệnh nhảy tùy ý kiểu `goto`.

Ở phần tên biến: `_count`, `__`, `constValue` và `gotoCount` đều hợp lệ; tổng `2 + 3 + 5 + 7 = 17`. Tên `__` tuy hợp lệ về cú pháp, nhưng khi viết chương trình thực tế bạn nên đặt tên biểu đạt ý nghĩa hơn.

`MAX_RETRIES` minh họa cách khai báo `static final`, **không phải** một câu lệnh `const`.

## 6. Các lỗi và ngộ nhận thường gặp

| Nhầm lẫn | Sự thật trên Java 17 | Cách xử lý |
| --- | --- | --- |
| “`_` là tên biến ngắn nên chắc hợp lệ” | `_` đứng một mình là keyword từ Java 9 | Đặt `_value` hoặc tên mô tả như `count` |
| “`const` không có tính năng nên được dùng đặt tên” | Vẫn là keyword dành riêng | Dùng tên như `constValue`; dùng `final` để ngăn gán lại |
| “`goto` không chạy nên có thể đặt làm nhãn” | Không; từ khóa dành riêng không làm identifier được | Đặt nhãn `scan:` hoặc `outer:` |
| “Mọi tên chứa `_` đều sai” | Chỉ `_` **đơn lẻ** bị cấm theo quy tắc này | `_count`, `item_2`, `__` hợp lệ |
| “`final` làm đối tượng bất biến hoàn toàn” | Không đúng với mọi đối tượng | Phân biệt gán lại tham chiếu và thay đổi trạng thái đối tượng |
| “`break outer` giống hệt `goto`” | `break` chỉ nhảy đến điểm kết thúc statement gắn nhãn hợp lệ | Dùng điều khiển luồng có cấu trúc |
| “`continue label` có thể nhảy tới mọi nhãn” | Nhãn đích phải đặt trên vòng lặp thích hợp | Chỉ dùng `continue` với nhãn vòng lặp |
| “Mã `const`/`goto` sẽ chạy rồi báo lỗi” | Lỗi bị bắt **khi biên dịch**, trước khi chạy `main` | Sửa identifier/cú pháp trước khi dùng `java` |

Một lưu ý nữa: dấu gạch dưới trong literal số, ví dụ `1_000`, chỉ giúp dễ đọc **chữ số**, không phải một biến tên `_`. Đây là hai ngữ cảnh cú pháp hoàn toàn khác nhau.

## 7. Bài tập tự luyện

**Bài 1 — Phân loại tên.** Các tên nào có thể khai báo thành biến `int` trên Java 17?

~~~text
_    __    _score    score_    const    constValue
goto    gotoCount    2score    score2
~~~

*Đáp án:* Hợp lệ: `__`, `_score`, `score_`, `constValue`, `gotoCount`, `score2`. Không hợp lệ: `_`, `const`, `goto` và `2score` (bắt đầu bằng chữ số).

**Bài 2 — Sửa lỗi.** Đoạn sau sai ở đâu?

~~~java
// Đoạn cố ý SAI, không đặt vào ví dụ đang chạy.
final int const = 3;
goto: for (int i = 0; i < 2; i++) {
    System.out.println(i);
}
~~~

*Gợi ý đáp án:* Cả `const` làm tên biến lẫn `goto` làm nhãn đều không hợp lệ. Đổi thành `final int maxTries = 3;` và nhãn `loop:` hoặc bỏ nhãn nếu không cần.

**Bài 3 — Dự đoán trước khi chạy.** Trong `ReservedNamesDemo`, nếu đổi `99` thành `11` thì `processed` bằng bao nhiêu?

*Đáp án:* `6`. Không còn giá trị chặn 99; vòng lặp vẫn bỏ qua `8` vì `-1`, sau đó đếm `9`, `11` và `10`.

**Bài 4 — Thử `continue`.** Bỏ nhãn trong `continue scan;` để thành `continue;`. Với dữ liệu gốc, kết quả `processed` có thay đổi không?

*Đáp án:* Có: thành `5`. `continue;` chỉ chuyển sang lượt tiếp theo của **vòng lặp trong**, nên sau `-1` còn xử lý `8`, trước khi gặp `99` ở hàng sau.

**Bài 5 — `final` và object.** Cho:

~~~java
final StringBuilder message = new StringBuilder("Java");
message.append(" 17"); // Hợp lệ.
// message = new StringBuilder("Other"); // Sai: gán lại biến final.
~~~

Tại sao câu `append` hợp lệ? *Đáp án:* `final` ngăn đổi tham chiếu được lưu trong biến, không nhất thiết ngăn thay đổi trạng thái đối tượng tham chiếu tới.

## Ghi nhớ

Trên **Java 17+**, `_` đơn lẻ, `const` và `goto` đều **không thể dùng làm tên**. `const` và `goto` là những từ dành riêng dù Java không triển khai câu lệnh mang các tên ấy; `_` đơn lẻ bị dành riêng từ Java 9, nhưng dấu `_` vẫn có thể là một phần trong identifier dài hơn. Hãy dùng `final`/`static final` cho mục đích hạn chế gán lại, và dùng `break`/`continue` có nhãn, `return` hoặc cấu trúc điều khiển phù hợp thay vì tìm cú pháp `goto`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-vocabulary-006">Khoảng trắng, xuống dòng và phân tách token</a>

<details>
<summary>Xem chi tiết</summary>

# Khoảng trắng, xuống dòng và phân tách token trong Java

> **Java core — language-basics / Từ vựng Java**  
> **Môi trường thực hành:** JDK 17 trở lên, không cần thư viện ngoài.  
> **Mục tiêu:** Hiểu Java xử lý khoảng trắng, tab, xuống dòng và chú thích ra sao trước khi phân tích cú pháp; biết khi nào hai token cần được tách, khi nào xuống dòng không thay thế dấu chấm phẩy; giải thích quy tắc nhận token dài nhất; phân biệt khoảng trắng ở bên ngoài với nội dung bên trong chuỗi hoặc text block. Có thể tự biên dịch, chạy và dự đoán đầu ra ví dụ.

## 1. Từ ký tự nguồn tới dãy token

Mã nguồn Java được viết bằng các ký tự. Trước khi kiểm tra xem một dòng có tạo thành biểu thức hoặc câu lệnh hợp lệ hay không, compiler phải nhận ra những **đơn vị từ vựng (token)**.

Ví dụ:

~~~java
int total = unitPrice * quantity;
~~~

Bộ phân tích từ vựng nhận những token: `int`, `total`, `=`, `unitPrice`, `*`, `quantity`, `;`. Khoảng trắng giữa chúng giúp nhận ranh giới, nhưng **không phải token** trong năm nhóm identifier/keyword/literal/operator/separator.

~~~mermaid
flowchart LR
    A["Ký tự trong file .java"] --> B["Xử lý Unicode escape và ký tự dòng"]
    B --> C["Nhận biết token, whitespace, comment"]
    C --> D["Dãy token"]
    D --> E["Phân tích cú pháp Java"]
~~~

Từ một dãy token hợp lệ, Java **chưa chắc tạo được câu lệnh hợp lệ**. Ví dụ `int = 2;` vẫn gồm các token nhận ra được nhưng sai ngữ pháp khai báo biến.

**Điểm chính:** khoảng trắng và xuống dòng chủ yếu điều khiển **ranh giới lexical**, chứ không phải tín hiệu mặc định để chương trình “chạy sang lệnh tiếp theo”.

## 2. Java công nhận những loại khoảng trắng nào?

Ở cấp xử lý từ vựng thông thường, Java nhận diện các loại ký tự khoảng trắng và dấu hết dòng quan trọng sau:

| Ký tự | Cách gọi | Tình huống thường gặp |
| --- | --- | --- |
| Space `U+0020` | Dấu cách | Giữa `int` và tên biến |
| Horizontal tab `U+0009` | Tab ngang | Thụt lề trong trình soạn thảo |
| Form feed `U+000C` | Ngắt trang | Ít gặp trong mã nguồn hiện đại |
| LF `U+000A` | Line feed | Xuống dòng trên nhiều hệ thống Unix |
| CR `U+000D` | Carriage return | Dấu dòng từ các định dạng cũ |
| CRLF | Cặp carriage return + line feed | Kết thúc dòng phổ biến trong tệp Windows |

Với Java, **CRLF được nhận như một dấu kết thúc dòng**, không phải hai lần ngắt dòng độc lập. Sự khác nhau LF/CRLF thường không thay đổi ngữ nghĩa một câu lệnh Java hợp lệ; nhưng có thể ảnh hưởng comment dòng, vị trí báo lỗi và nội dung literal ở những vị trí đặc biệt.

Không được giả định **mọi ký tự trông giống khoảng trắng Unicode đều được lexer Java xử lý như dấu cách ASCII**. Chẳng hạn, một ký tự khoảng trắng không ngắt (*non-breaking space*) sao chép từ trang web có thể gây kết quả khác thường hoặc lỗi từ vựng. Khi mã nhìn đúng nhưng compiler báo ký tự không hợp lệ, hãy kiểm tra các ký tự vô hình.

## 3. Khi nào khoảng trắng là bắt buộc?

Một số cặp token là chữ/tên sẽ dính thành **một token khác** nếu viết liền.

| Ý định đúng | Viết liền | Điều xảy ra |
| --- | --- | --- |
| `int count = 1;` | `intcount = 1;` | `intcount` là một identifier, **không** còn keyword `int` cộng tên `count` |
| `long total = 2L;` | `longtotal = 2L;` | Lexer nhận `longtotal` là identifier |
| `new Example()` | `newExample()` | Một identifier `newExample`, không còn keyword `new` |
| `return result;` | `returnresult;` | `returnresult` là tên identifier, không phải câu lệnh `return` |

Để tách token không nhất thiết chỉ dùng dấu cách. Dòng mới và **chú thích block** cũng có thể làm ranh giới:

~~~java
int
count = 1;

int /* chú thích giữa hai token */ total = 2;

int/**/other = 3;
~~~

Ba biến `count`, `total`, `other` được khai báo hợp lệ khi những dòng trên nằm **trong một phương thức hoặc block được phép khai báo biến cục bộ**. Trong dòng `int/**/other`, chú thích được xử lý như khoảng phân cách từ vựng giữa từ khóa và identifier.

Ngược lại, nhiều token có thể chạm nhau mà vẫn phân biệt rõ:

~~~java
int x=1;
x++;
System.out.println(x);
~~~

Dấu `=`, `;`, `(`, `)` và `.` đã tự tạo ranh giới phù hợp. Thêm dấu cách nhằm dễ đọc là tốt, nhưng **không có quy tắc phải đặt dấu cách quanh mọi dấu câu**.

## 4. Xuống dòng không tự kết thúc câu lệnh Java

Java không dùng “xuống dòng là dấu chấm phẩy tự động” cho các statement thông thường. Ví dụ dưới đây hợp lệ bên trong một phương thức:

~~~java
int value
        =
        10
        +
        5
        ;
System.out.println
        (
            value
        )
        ;
~~~

Đoạn đó tương đương về ý nghĩa với:

~~~java
int value = 10 + 5;
System.out.println(value);
~~~

Cả hai in `15`. Compiler quan tâm tới dãy token và ngữ pháp, **không phải cách code được chia thành nhiều dòng**.

Những trường hợp cần nhớ:

- `int value = 10` rồi xuống dòng **không** tự hoàn thành một khai báo; thông thường vẫn cần dấu `;`.
- `if (ready)` rồi xuống dòng trước `{ ... }` là hợp lệ; block vẫn là thân của `if`.
- Có thể đặt nhiều statement trên cùng dòng, chẳng hạn `int a = 1; int b = 2;`.
- Với dấu `;` đứng riêng sau `if` hoặc `while`, bạn có thể vô tình tạo một **empty statement**: đây là một token có ý nghĩa cú pháp, không liên quan đến việc xuống dòng.

**Tách biệt:** Dấu kết thúc dòng vẫn quan trọng để **kết thúc chú thích `//`** và đối với cách viết literal chuỗi; điều đó **không** biến xuống dòng thành token kết thúc statement.

## 5. Chú thích có thể phân tách token

Java nhận diện `//` tới hết dòng và `/* ... */` tới dấu đóng đầu tiên; `/** ... */` là dạng chú thích tài liệu được công cụ Javadoc xử lý. **Nội dung chú thích không trở thành token chương trình**, nhưng dấu kết thúc của chúng quyết định phần mã nào còn được phân tích.

~~~java
int count // Chú thích dòng kết thúc ở đây.
        = 3;

int /* Chú thích khối nằm giữa các token */ total = count * 2;
~~~

Cả hai khai báo đều hợp lệ trong một phương thức: sau khi bỏ nội dung chú thích, lexer vẫn nhận các token tương ứng với `int count = 3;` và `int total = count * 2;`. Ngược lại, `// int count = 3;` **không tạo biến** vì cả khai báo nằm trong comment.

Bài về các loại chú thích sẽ giải thích riêng việc dùng `//`, `/* ... */`, `/** ... */`, giới hạn không lồng block comment và cách tạo tài liệu Javadoc.

## 6. Quy tắc nhận token dài nhất: khoảng trắng có thể đổi ý nghĩa

Khi một chuỗi ký tự có thể tạo thành nhiều token, lexer Java ưu tiên token dài nhất có thể nhận hợp lệ ở vị trí đó (*maximal munch*). Đây là lý do các toán tử dài cần được nhận diện nguyên khối.

| Chuỗi mã | Phân tích quan trọng | Ý nghĩa hoặc hệ quả |
| --- | --- | --- |
| `count++;` | `count`, `++`, `;` | `++` là một toán tử tăng hậu tố |
| `total += 2;` | `total`, `+=`, `2`, `;` | `+=` là toán tử gán kết hợp |
| `x >= 3` | `x`, `>=`, `3` | `>=` là một toán tử so sánh |
| `x > = 3` | `x`, `>`, `=`, `3` | Không tương đương `x >= 3` và không hợp lệ trong ngữ cảnh này |
| `x+++y` | `x`, `++`, `+`, `y` | Được phân tích như `x++ + y`, **không** phải `x + ++y` |
| `x + ++y` | `x`, `+`, `++`, `y` | Tăng `y` trước khi cộng với `x` |

Ví dụ với `int x = 4; int y = 3;`:

- `int result = x+++y;` tạo `result = 7` và sau đó `x = 5`, `y = 3`.
- `int result = x + ++y;` (nếu bắt đầu lại với cùng `x = 4`, `y = 3`) tạo `result = 8` và `y = 4`.

**Khoảng trắng có thể thay đổi token được nhận**, nhưng không phải mọi cách chèn khoảng trắng đều có thể biến thành mã hợp lệ. Ví dụ cố viết `a + + b` là hai toán tử `+` liên tiếp theo ngữ pháp *binary plus* rồi *unary plus*, khác với toán tử `++`.

Quy tắc này xảy ra **trước** khi compiler quyết định phép toán nào được tính trước hoặc sau. Đó là hai tầng khác nhau: nhận token và đánh giá biểu thức.

## 7. Khoảng trắng trong chuỗi và ký tự thoát

Ngoài các chuỗi literal, khoảng trắng thường chỉ phân tách hoặc trình bày mã. **Bên trong một string literal**, dấu cách là dữ liệu và được giữ nguyên:

~~~java
String one = "A B";
String two = "A  B";
System.out.println(one.length()); // 3
System.out.println(two.length()); // 4
~~~

Hai giá trị khác nhau vì `two` có **hai dấu cách giữa A và B**.

**Xuống dòng thật** không được phép xuất hiện ngay giữa một literal chuỗi thông thường:

~~~java
// Mảnh ví dụ CỐ Ý KHÔNG HỢP LỆ:
// String invalid = "Dong 1
// Dong 2";
~~~

Nếu bỏ dấu comment ở hai dòng để tạo một literal chuỗi vỡ qua dòng theo cách đó, compiler sẽ báo lỗi. Cách hợp lệ để chứa newline trong một chuỗi là dùng escape sequence:

~~~java
String valid = "Dong 1\nDong 2";
System.out.println(valid.lines().count()); // 2
~~~

Các ký tự `\` và `n` trong `"Dong 1\nDong 2"` tạo biểu diễn escape của newline trong chuỗi khi chương trình chạy; đó **không** phải việc gõ Enter làm vỡ chuỗi trong file source.

### Text block trong Java 17

Java 17 hỗ trợ *text block* bằng ba dấu nháy kép. Dòng đầu phải có dấu kết thúc dòng sau `"""` mở:

~~~java
String text = """
        First
        Second
        """;
System.out.println(text.replace("\n", "|")); // First|Second|
~~~

Java sẽ xử lý thụt lề chung và nội dung newline của text block theo các quy tắc riêng. Với cấu trúc trên, chuỗi giữ dấu xuống dòng **sau cả dòng `Second`**, nên kết quả có dấu `|` cuối. Đừng suy ra mọi khoảng trắng trong text block được giữ nguyên từng byte: indentation chung thường bị loại bỏ khi dựng giá trị chuỗi.

## 8. Cẩn thận với Unicode escape và ký tự vô hình

Việc xử lý Unicode escape của Java xảy ra **rất sớm**, trước khi nhận biết comment và các token. Vì vậy các chuỗi escape hợp lệ có thể tác động tới ranh giới dòng hoặc cú pháp ngay cả khi chúng xuất hiện trong phần nguồn trông giống comment.

Ví dụ, dạng Unicode escape viết bằng dấu gạch chéo ngược, chữ `u` và bốn chữ số hex để tạo **LF** có thể trở thành một dấu kết thúc dòng trước khi compiler nhận diện một chú thích `//`. Đây là trường hợp biên để hiểu quy trình từ vựng, **không phải kỹ thuật nên sử dụng** để xuống dòng trong code.

Khi gặp lỗi kỳ lạ:

- Kiểm tra dấu tab, dấu cách không ngắt (*NBSP*) và các ký tự vô hình do copy/paste.
- Kiểm tra rằng file được lưu bằng mã hóa nhất quán; với `javac` có thể truyền `-encoding UTF-8`.
- Kiểm tra Unicode escape trong source thay vì đoán rằng mọi ký tự trong comment đều hoàn toàn bị bỏ qua ở bước đầu tiên.
- Không dùng việc xuống dòng hoặc chèn comment để “chữa” một lỗi ngữ pháp chưa được hiểu.

## 9. Chương trình Java 17+ tự chạy: WhitespaceDemo.java

Tạo file **WhitespaceDemo.java** (đúng tên `public class`) và nhập:

~~~java
public final class WhitespaceDemo {
    public static void main(String[] args) {
        int base = 7;
        int extra // Chú thích kết thúc ở hết dòng.
                = 5;

        int sum = base
                + extra;
        int/**/factor = 2; // Block comment tách int và factor.

        boolean ready = sum >= 10
                && factor > 0;

        int x = 4;
        int y = 3;
        int glued = x+++y; // Lexer: x++ + y.

        String spaces = "A  B";
        String escaped = "first\nsecond";
        String block = """
                one
                two
                """;

        System.out.println("sum=" + sum);
        System.out.println("ready=" + ready);
        System.out.println("glued=" + glued + ", x=" + x + ", y=" + y);
        System.out.println("literalLength=" + spaces.length());
        System.out.println("escapedLines=" + escaped.lines().count());
        System.out.println("textBlock=" + block.replace("\n", "|"));
    }
}
~~~

**Biên dịch và chạy** trong terminal tại thư mục chứa file, áp dụng cho Bash, PowerShell và Command Prompt:

~~~bash
javac -encoding UTF-8 WhitespaceDemo.java
java WhitespaceDemo
~~~

**Kết quả mong đợi chính xác:**

~~~text
sum=12
ready=true
glued=7, x=5, y=3
literalLength=4
escapedLines=2
textBlock=one|two|
~~~

### Giải thích kết quả

1. `int extra` bị tách qua một dòng comment, nhưng vẫn là khai báo hợp lệ; `extra = 5`.
2. `sum` bằng `7 + 5 = 12` dù biểu thức trải qua nhiều dòng; xuống dòng không ngầm chèn `;`.
3. `int/**/factor` tương đương đặt khoảng phân tách giữa `int` và `factor`; `factor = 2` nên điều kiện `ready` đúng.
4. `x+++y` được nhận thành `x++ + y`; `glued` nhận `4 + 3 = 7`, `x` sau đó bằng `5`, `y` giữ `3`.
5. Literal `"A  B"` có hai dấu cách nên độ dài bằng `4`.
6. Literal `"first\nsecond"` có một newline escape nên `lines().count()` trả `2`.
7. Text block có hai dòng nội dung và newline ở cuối; sau khi thay newline bằng `|`, kết quả là `one|two|`.

**Phạm vi ví dụ:** mã này minh họa lexical rules bằng kết quả quan sát được. Java compiler không in danh sách token ra console; để phân tích token chi tiết cần công cụ/parser chuyên dụng, không phải `javac` thông thường.

## 10. Những lỗi thường gặp và hướng kiểm tra

| Hiện tượng | Nguyên nhân có khả năng | Cách giải quyết |
| --- | --- | --- |
| `cannot find symbol` với `intcount` | Viết dính keyword và identifier nên thành một tên khác | Tách `int count` |
| `';' expected` sau khai báo bị chia dòng | Quên dấu `;`, không phải Java “tự hiểu xuống dòng” | Thêm `;` vào đúng vị trí kết thúc |
| `unclosed string literal` | Chuỗi `"..."` bị ngắt dòng thật hoặc thiếu nháy kép đóng | Dùng `\n` trong chuỗi hoặc text block phù hợp |
| `illegal character` | Có ký tự Unicode không được phép ở vị trí từ vựng, ví dụ khoảng trắng lạ | Kiểm tra byte/ký tự vô hình, gõ lại ký tự đúng |
| `x+++y` cho kết quả không như dự kiến | Lexer nhận `x++` trước rồi `+ y` | Thêm khoảng trắng hoặc ngoặc để thể hiện rõ ý định |
| Đặt `//` nhưng phần sau cùng dòng “mất” | `//` bắt đầu comment tới hết dòng | Đặt phần mã cần chạy trên dòng khác hoặc sửa comment |
| Chú thích `/* ... */` không đóng | Comment block thiếu `*/` | Đóng comment đúng cặp, không lồng block comment |
| Text block thụt lề/xuống dòng sai kết quả | Có quy tắc indentation và trailing newline | In ra chuỗi thực tế, xem vị trí dấu `"""` đóng |

**Lưu ý:** Một lỗi trỏ tới dòng sau chỗ sai thường liên quan việc lexer/parser đang chờ một token còn thiếu. Đọc cả dòng trước đó, đặc biệt dấu nháy, dấu `;` và điểm kết thúc comment.

## 11. Bài tập tự luyện

**Bài 1 — Phân tách tên.** Giả sử bạn muốn khai báo biến `count` kiểu `int`. Vì sao `intcount=3;` không tương đương `int count=3;`? Hãy thử dạng `int/**/count=3;` trong một phương thức để tự xác nhận.

**Bài 2 — Dự đoán biểu thức.** Bắt đầu với `int x=4, y=3;`. So sánh `int a=x+++y;` với `int a=x+ ++y;` sau khi **khởi tạo lại x và y** cho từng trường hợp. Ghi lại kết quả `a` và giá trị cuối của từng biến.

**Bài 3 — Xuống dòng.** Viết một câu lệnh khai báo số và một lời gọi `println` trải qua ít nhất ba dòng. Xác định token `;` nào thực sự kết thúc statement.

**Bài 4 — Comment.** Viết `int number // comment` rồi đặt `= 10;` ở dòng tiếp theo; giải thích vì sao compiler vẫn chấp nhận. Thử thay bằng chú thích block.

**Bài 5 — Chuỗi.** Dự đoán `"a b".length()` và `"a  b".length()`; giải thích vì sao khoảng trắng trong literal có tác dụng khác với khoảng trắng giữa token.

**Bài 6 — Text block.** Thay text block trong `WhitespaceDemo` bằng ba dòng. In bằng `replace("\n", "|")` để xác định chỗ nào có newline. Thử thay đổi vị trí dấu `"""` đóng để quan sát cách xử lý indentation.

## 12. Tổng kết

- **Khoảng trắng** có thể cần để tách keyword/identifier; ở nhiều vị trí khác chỉ giúp mã dễ đọc.
- **Xuống dòng** không phải dấu kết thúc statement chung của Java, nhưng có vai trò rõ ràng với `//` comment và cú pháp literal.
- **Comment** không sinh ra token cho parser thông thường và có thể nằm giữa hai token.
- **Token dài nhất** giải thích vì sao `++`, `>=` được nhận nguyên khối và `x+++y` khác `x + ++y`.
- **Trong literal**, khoảng trắng và escape có thể là dữ liệu; **text block** có quy tắc newline/indentation riêng.

**Tự kiểm tra:** Bạn có thể giải thích vì sao `intcount` khác `int count`, vì sao `x+++y` được đọc theo cách cụ thể, và vì sao một chuỗi bị ngắt dòng trực tiếp có thể lỗi dù câu lệnh Java thông thường có thể trải nhiều dòng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-vocabulary-007">Chú thích một dòng, nhiều dòng và chú thích tài liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Chú thích một dòng, nhiều dòng và chú thích tài liệu trong Java

## 1. Ba dạng chú thích cần phân biệt

Chú thích (*comment*) là phần văn bản dành cho người đọc mã nguồn. Trong Java 17+, bạn sẽ gặp ba dạng chính:

| Dạng | Cú pháp | Phạm vi | Mục đích thường gặp |
|---|---|---|---|
| Chú thích một dòng | `// nội dung` | Từ `//` tới hết dòng | Giải thích một quyết định ngắn, cảnh báo một trường hợp |
| Chú thích khối | `/* nội dung */` | Từ `/*` đến `*/`, có thể qua nhiều dòng | Diễn giải ngắn nhiều dòng, ghi chú nội bộ |
| Chú thích tài liệu | `/** nội dung */` | Một khối đặc biệt được công cụ Javadoc nhận diện | Mô tả API: lớp, phương thức, tham số, giá trị trả về, ngoại lệ |

Trong chương trình Java thông thường, **nội dung chú thích không phải câu lệnh để JVM thực thi**. Trình biên dịch xử lý các dấu chú thích khi phân tích mã nguồn; nội dung của chúng không trở thành logic chạy của chương trình. Công cụ **`javadoc`** có thể đọc chú thích tài liệu để sinh trang HTML.

Ví dụ phân biệt trực quan:

~~~java
int price = 100; // Giải thích ngay sau câu lệnh

/* Đây là chú thích khối
   trải qua nhiều dòng. */

/**
 * Đây là chú thích tài liệu.
 * Thường đặt trước khai báo cần được mô tả.
 */
class Product {
}
~~~

**Quy tắc cốt lõi:** dùng chú thích để giải thích **tại sao** mã được viết như vậy, điều kiện hay cam kết của API; không cần diễn đạt lại mọi phép gán hoặc mọi dấu chấm phẩy mà mã đã thể hiện rõ.

## 2. Chú thích một dòng: `//`

Từ hai dấu gạch chéo liên tiếp ở ngoài chuỗi ký tự, phần còn lại của **dòng nguồn** được xem là chú thích.

~~~java
int minimumAge = 18;
int userAge = 17;

// Quy định nghiệp vụ: người dưới 18 tuổi không được đăng ký.
boolean eligible = userAge >= minimumAge;
System.out.println(eligible); // false
~~~

Nếu một dòng có lệnh trước `//`, lệnh **vẫn được thực hiện**; chỉ phần sau dấu `//` là chú thích. Hai cách sử dụng phù hợp:

- Đặt chú thích ở **dòng riêng phía trên** đoạn xử lý khi cần lý giải một quy tắc.
- Đặt chú thích **cuối dòng** khi thực sự ngắn và không làm mã khó đọc.

Ví dụ không nên viết chú thích dư thừa:

~~~java
int total = 10; // Gán giá trị 10 cho total
~~~

Giá trị đã rõ từ code. Thông tin hữu ích hơn có thể là lý do dùng 10, điều kiện của bài toán hoặc đơn vị tính.

**Chú thích `//` không kéo dài sang dòng tiếp theo**:

~~~java
int a = 1; // chỉ dòng này
int b = 2; // câu lệnh dòng sau vẫn hoạt động
~~~

## 3. Chú thích nhiều dòng: `/* ... */`

Chú thích khối bắt đầu ở `/*` và kết thúc tại **cặp `*/` đầu tiên** tương ứng theo quá trình phân tích ký tự của Java. Nó có thể nằm cùng dòng hoặc trải qua nhiều dòng.

~~~java
/*
 * Công thức demo:
 * thành tiền = số lượng × đơn giá.
 * Dữ liệu đều là số nguyên không âm.
 */
int quantity = 3;
long unitPrice = 120_000L;
long subtotal = unitPrice * quantity;
System.out.println(subtotal); // 360000
~~~

Chú thích khối cũng có thể đặt xen giữa một số token khi cú pháp cho phép:

~~~java
int result = 2 /* giữ phép nhân minh họa */ * 3;
System.out.println(result); // 6
~~~

Dùng kiểu xen giữa biểu thức quá thường xuyên sẽ làm code khó đọc. Thực tế, chú thích khối thường đặt thành một đoạn riêng.

### Chú thích khối **không lồng nhau**

Đây là lỗi dễ gặp:

~~~java
/* Khối ngoài
   /* Khối trong */
   phần còn lại ở đây không phải chú thích
*/
~~~

`*/` đầu tiên kết thúc khối ngoài. Phần chữ còn lại sẽ bị Java xem như mã nguồn và có thể gây lỗi biên dịch. Java **không hỗ trợ chú thích khối lồng nhau**. Khi cần hai đoạn giải thích độc lập, dùng hai khối riêng hoặc các dòng `//`.

Một khối thiếu `*/` cũng gây lỗi biên dịch, chẳng hạn thông báo về *unclosed comment*. Nếu editor tô màu cả phần còn lại của tệp như chú thích, hãy kiểm tra dấu đóng.

## 4. Chú thích tài liệu: `/** ... */`

**Javadoc comment** có cú pháp bắt đầu bằng `/**` và kết thúc bằng `*/`. Nội dung bên trong thường mô tả **khai báo đứng ngay sau chú thích**, ví dụ public class hoặc public method.

~~~java
/**
 * Các phép tính đơn giản dùng trong ví dụ.
 */
public class Calculator {

    /**
     * Cộng hai số nguyên và phát hiện tràn số.
     *
     * @param a số hạng thứ nhất
     * @param b số hạng thứ hai
     * @return tổng của a và b
     * @throws ArithmeticException nếu phép cộng vượt miền int
     */
    public static int add(int a, int b) {
        return Math.addExact(a, b);
    }
}
~~~

Phần mở đầu là **mô tả**. Các dòng bắt đầu bằng `@` trong chú thích tài liệu là **block tags của Javadoc**, ví dụ `@param`, `@return`, `@throws`.

Dấu `*` ở đầu mỗi dòng nội dung là **kiểu trình bày quen thuộc**, không phải phép nhân Java; `javadoc` hiểu cấu trúc của khối chú thích tài liệu.

### Các tag thường dùng

| Tag | Ý nghĩa | Khi nên dùng |
|---|---|---|
| `@param name` | Ý nghĩa một tham số; `name` phải khớp tên tham số | Method/constructor có tham số |
| `@return` | Giá trị/kết quả trả về | Method trả giá trị, không dùng để mô tả kết quả của method `void` |
| `@throws Type` | Điều kiện có thể phát sinh exception | API có hành vi lỗi cần giải thích |
| `@since` | Phiên bản kể từ khi API tồn tại | API ổn định hoặc thư viện phát hành phiên bản |
| `@see` | Tham chiếu tài liệu liên quan | Người đọc cần xem thêm type/method khác |
| `@deprecated` | Giải thích API không nên tiếp tục dùng | Thường kết hợp annotation `@Deprecated` |
| `{@code ...}` | Hiển thị đoạn code trong phần mô tả | Ví dụ `{@code count > 0}` |
| `{@link Type}` | Tạo liên kết tới phần tử tài liệu được nhận diện | Khi dẫn tới API liên quan |

Hai dạng có dấu `{@...}` là **inline tags**, có thể xuất hiện giữa câu; `@param` và `@return` là **block tags**, thường mỗi tag bắt đầu ở một dòng riêng trong Javadoc comment.

**Chú thích tài liệu không phải annotation.** `@Deprecated` đặt **trong mã** là annotation; `@deprecated` nằm **trong Javadoc** là tag mô tả. Chỉ thêm văn bản `@deprecated` vào Javadoc không tự thay thế mọi tác dụng của annotation `@Deprecated`.

## 5. Ví dụ Java 17 hoàn chỉnh: code, comment và Javadoc

Tạo tệp đúng tên **`CommentDemo.java`**, lưu bằng UTF-8:

~~~java
/**
 * Minh họa ba dạng chú thích trong Java.
 *
 * <p>Ví dụ tính tiền hàng nguyên đơn vị đồng.
 *
 * @since 1.0
 */
public final class CommentDemo {
    private CommentDemo() {
        // Không tạo instance của class chỉ chứa tiện ích.
    }

    /**
     * Tính tiền hàng bằng phép nhân kiểm tra tràn số.
     *
     * @param quantity số lượng, phải không âm
     * @param unitPrice đơn giá nguyên đồng, phải không âm
     * @return thành tiền theo đơn vị đồng
     * @throws IllegalArgumentException nếu đầu vào âm
     * @throws ArithmeticException nếu phép nhân tràn long
     */
    public static long total(int quantity, long unitPrice) {
        if (quantity < 0 || unitPrice < 0) {
            throw new IllegalArgumentException("Gia tri am");
        }

        /*
         * Dùng multiplyExact để không âm thầm trả về
         * một số tiền sai do tràn số nguyên.
         */
        return Math.multiplyExact((long) quantity, unitPrice);
    }

    /**
     * Điểm vào chương trình.
     *
     * @param args đối số dòng lệnh; demo không sử dụng
     */
    public static void main(String[] args) {
        long result = total(3, 120_000L);
        System.out.println("Tong: " + result); // 360000

        // Những dấu giống comment trong chuỗi vẫn là nội dung chuỗi.
        String url = "https://example.com/items";
        String marker = "/* khong phai comment */";
        System.out.println("URL: " + url);
        System.out.println("Marker: " + marker);
    }
}
~~~

### Biên dịch và chạy trên JDK 17+

Mở terminal tại thư mục chứa `CommentDemo.java`.

**Bash (Linux/macOS):**

~~~bash
javac -version
javac --release 17 -encoding UTF-8 CommentDemo.java
java -cp . CommentDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac -version
javac --release 17 -encoding UTF-8 .\CommentDemo.java
java -cp . CommentDemo
~~~

Kết quả của lệnh `java`:

~~~text
Tong: 360000
URL: https://example.com/items
Marker: /* khong phai comment */
~~~

Chú thích một dòng, khối và Javadoc comment **không được in**. Những ký tự `//`, `/*` và `*/` trong **string literal** được in như ký tự thông thường vì chúng nằm trong chuỗi.

**Điều cần nhận ra:** chú thích giải thích ý định dùng `Math.multiplyExact`, nhưng chính **lệnh gọi method** mới phát hiện tràn số. Viết chú thích “đã xử lý tràn số” mà code chỉ nhân bằng `*` thông thường sẽ không khiến chương trình an toàn hơn.

## 6. Sinh tài liệu HTML bằng công cụ javadoc

JDK không chỉ có `javac` và `java` mà còn có **`javadoc`**. Đây là công cụ đọc chú thích tài liệu và thông tin khai báo để sinh trang API.

Từ thư mục chứa file mẫu, chạy:

**Bash:**

~~~bash
javadoc -quiet -encoding UTF-8 -docencoding UTF-8 \
  -charset UTF-8 -d docs CommentDemo.java
~~~

**PowerShell (một dòng):**

~~~powershell
javadoc -quiet -encoding UTF-8 -docencoding UTF-8 -charset UTF-8 -d docs .\CommentDemo.java
~~~

Thư mục **`docs`** được tạo để lưu HTML. Với class public ở default package như ví dụ, tệp tài liệu của class thường là:

~~~text
docs/CommentDemo.html
~~~

Có thể kiểm tra tệp:

**Bash:**

~~~bash
ls docs/CommentDemo.html
~~~

**PowerShell:**

~~~powershell
Test-Path .\docs\CommentDemo.html
~~~

`Test-Path` trả **`True`** nếu tệp tồn tại. Bạn có thể mở tệp HTML bằng trình duyệt để xem mô tả class, method `total`, các tham số, giá trị trả về và các ngoại lệ được ghi trong Javadoc.

**Không nên cam kết nguyên văn đầu ra của lệnh `javadoc`**, vì số dòng thông báo, cảnh báo DocLint và danh sách file tạo ra có thể khác theo JDK/tùy chọn. Tiêu chí kiểm tra chính là lệnh kết thúc thành công và tài liệu HTML được sinh đúng.

Nếu tài liệu public API không chứa một method hoặc field, kiểm tra **mức hiển thị** của phần tử và các tùy chọn của `javadoc`. Mặc định công cụ không nhất thiết liệt kê mọi thành viên private. Có thể dùng `javadoc -private ...` nếu bạn thật sự cần tài liệu cho các thành viên private trong mục đích nội bộ.

## 7. Chú thích ở đúng vị trí và đúng mục tiêu

### Chú thích tài liệu cho API; chú thích thường cho giải thích cục bộ

Chú thích tài liệu nên đặt **ngay trước khai báo cần mô tả**:

~~~java
/**
 * Tính diện tích hình chữ nhật.
 *
 * @param width chiều rộng không âm
 * @param height chiều cao không âm
 * @return diện tích theo đơn vị bình phương
 */
static long area(long width, long height) {
    if (width < 0 || height < 0) {
        throw new IllegalArgumentException("Negative size");
    }
    return Math.multiplyExact(width, height);
}
~~~

Nếu đoạn code chỉ cần giải thích một quyết định cục bộ, ví dụ lý do dùng một thuật toán hoặc giới hạn hiệu năng, `//` hay `/* ... */` thường hợp lý hơn.

**Javadoc không tự thực thi hợp đồng.** `@param width chiều rộng không âm` không làm JVM từ chối số âm; phương thức cần có validation phù hợp hoặc bên gọi phải bảo đảm tiền điều kiện theo thiết kế. Tài liệu phải **khớp với hành vi thật**.

### Mô tả *vì sao*, không sao chép *cái gì*

Ít hữu ích:

~~~java
quantity++; // Tăng quantity thêm 1.
~~~

Hữu ích hơn nếu có quy tắc rõ ràng:

~~~java
// Chỉ tăng sau khi kiểm tra còn đủ hàng trong kho.
quantity++;
~~~

Ví dụ trên vẫn cần **có code kiểm tra kho thực sự** ở nơi phù hợp; chú thích không thay cho thực thi quy tắc.

## 8. Những bẫy từ vựng liên quan đến dấu comment

### 8.1. Comment marker trong string/char/text block không phải comment

~~~java
String website = "https://example.org";  // Có // bên trong chuỗi
String marker = "/* sample */";          // Có /* */ bên trong chuỗi
char slash = '/';
~~~

Nội dung bên trong string literal hoặc character literal được nhận diện là **ký tự của literal**, không phải dấu mở chú thích. Với **text block** (`"""`) của Java 17, nguyên tắc tương tự: các dấu `//` hoặc `/* ... */` nằm trong nội dung của text block không tự tạo chú thích.

### 8.2. Đừng dựa vào comment để "vô hiệu hóa" mã lâu dài

Đoạn:

~~~java
// sendEmail(customer);
~~~

có thể hữu ích khi thử nghiệm ngắn, nhưng giữ mã đã bị comment lâu dài làm chương trình khó đọc và mơ hồ về chức năng. Tốt hơn là xóa mã không còn dùng (version control lưu lịch sử), hoặc dùng điều kiện/cấu hình rõ ràng nếu hành vi thực sự cần bật/tắt.

### 8.3. Chú ý Unicode escape trong Java source

Java xử lý **Unicode escapes** ở một giai đoạn rất sớm của quá trình đọc nguồn, **trước khi xác định comment và string literal** theo từ vựng. Vì vậy các chuỗi ký tự thoạt nhìn giống comment có chứa dạng escape đặc biệt có thể không được phân tích như bạn đoán. Đừng dựa vào comment để che giấu các Unicode escapes mang ý nghĩa cú pháp; nếu nghi ngờ, kiểm tra mã nguồn thực tế và thử biên dịch bằng đúng JDK mục tiêu.

### 8.4. Comment có thể che lỗi khi xóa/mở rộng code

Một dấu `/*` bị bỏ sót dấu kết thúc có thể khiến nhiều dòng mã sau đó nằm trong comment hoặc tạo lỗi `unclosed comment`. Một dấu `*/` thừa ngoài comment thường làm mã lỗi cú pháp. Hãy nhìn highlight của editor và kiểm tra các cặp dấu.

## 9. Javadoc: những lỗi viết tài liệu hay gặp

| Vấn đề | Vì sao không tốt? | Cách khắc phục |
|---|---|---|
| Viết `@param name` nhưng method có tham số `userName` | Tag không khớp khai báo | Dùng đúng tên tham số |
| Method trả `int` nhưng mô tả `@return` sai đơn vị | Người gọi API dễ hiểu sai | Nêu kết quả và đơn vị chính xác |
| Method có thể ném exception nhưng tài liệu không nói điều kiện | Khó dùng API an toàn | Bổ sung `@throws` với điều kiện cụ thể |
| Viết ký tự `<` trực tiếp trong đoạn tài liệu muốn hiển thị như code | Có thể bị hiểu là markup HTML | Dùng `{@code x < 10}` hoặc escape thích hợp |
| Chỉ ghi `/** TODO */` cho mọi method | Không mô tả hợp đồng thực sự | Viết nội dung hữu ích hoặc bỏ comment không cần thiết |
| Đổi chữ ký method nhưng không cập nhật Javadoc | Tài liệu lỗi thời, có thể có cảnh báo | Sửa tag và ví dụ cùng lúc |
| Xem Javadoc như cơ chế validation | Comment không được chạy | Viết check trong code và test |

**Không nhầm** cú pháp `/** ... */` với `/* ... */`: cả hai đều là khối comment, nhưng `/**` đặt trước khai báo được công cụ Javadoc xử lý như tài liệu API. Một đoạn comment Javadoc không có khai báo phù hợp ngay sau không thay thế cho việc gắn tài liệu vào thành phần mong muốn.

## 10. Thực hành phân biệt ba loại

Xét đoạn mã:

~~~java
/**
 * Chuyển một độ dài từ mét sang centimet.
 *
 * @param meters số mét không âm
 * @return số centimet
 * @throws IllegalArgumentException nếu đầu vào âm
 */
static long toCentimeters(long meters) {
    if (meters < 0) {
        throw new IllegalArgumentException("Negative meters");
    }
    // 1 mét = 100 centimet.
    return Math.multiplyExact(meters, 100L);
}

/* Dùng trong ví dụ chạy đơn giản:
   toCentimeters(3) => 300 */
~~~

Tự trả lời:

1. Đoạn nào là chú thích một dòng?
2. Đoạn nào tạo nội dung tài liệu cho method?
3. Đoạn nào là chú thích khối thông thường?
4. Khi gọi `toCentimeters(3)`, các comment có được in ra không?

**Đáp án:** dòng `// 1 mét...` là chú thích một dòng; khối `/** ... */` là Javadoc của `toCentimeters`; khối `/* Dùng ... */` là chú thích khối thông thường. Không comment nào được in bởi lời gọi method; kết quả method là số `300`.

## 11. Bài tập có đáp án

### Bài A — chọn đúng kiểu chú thích

Bạn muốn ghi một giải thích ngắn cho quy tắc ở dòng tiếp theo; muốn ghi ghi chú nội bộ nhiều dòng; và muốn công khai hợp đồng của một method có hai tham số.

**Đáp án:** lần lượt dùng `//`, `/* ... */`, và `/** ... */` với `@param`, `@return` (nếu trả giá trị), `@throws` (khi cần).

### Bài B — sửa comment chưa đóng

Đoạn:

~~~java
int a = 1;
/* giai thich
int b = 2;
~~~

**Đáp án:** thêm `*/` vào điểm kết thúc đúng của comment. Nếu `int b = 2;` cần được thực thi, đặt nó **sau** `*/`.

### Bài C — dự đoán đầu ra

~~~java
int x = 3 /* vẫn đang trong phép tính */ + 2;
String s = "http://localhost";
System.out.println(x);
System.out.println(s);
~~~

**Đáp án:**

~~~text
5
http://localhost
~~~

`/* ... */` không làm mất toán tử `+` ở ngoài comment; `//` trong chuỗi URL không mở chú thích.

### Bài D — viết Javadoc đủ hợp đồng

Cho:

~~~java
static int divide(int numerator, int denominator) {
    if (denominator == 0) {
        throw new IllegalArgumentException("Zero denominator");
    }
    return numerator / denominator;
}
~~~

Hãy viết chú thích tài liệu nêu hai tham số, kết quả và điều kiện lỗi.

**Đáp án gợi ý:**

~~~java
/**
 * Chia hai số nguyên theo quy tắc chia nguyên của Java.
 *
 * @param numerator số bị chia
 * @param denominator số chia, phải khác 0
 * @return thương nguyên, làm tròn về 0
 * @throws IllegalArgumentException nếu denominator bằng 0
 */
~~~

Lưu ý: phép chia `Integer.MIN_VALUE / -1` là trường hợp đặc biệt có kết quả `Integer.MIN_VALUE` theo quy tắc tràn int; tài liệu chi tiết hơn nên nêu nếu hợp đồng ứng dụng yêu cầu xử lý khác.

### Bài E — tạo và kiểm tra HTML

Dùng file `CommentDemo.java` ở mục 5, chạy `javadoc`, rồi mở `docs/CommentDemo.html`. Tìm tài liệu của `total`, kiểm tra các mô tả `quantity`, `unitPrice`, `@return` và `@throws`.

**Đáp án kỳ vọng:** trang API có phần mô tả method và các mục Parameters/Returns/Throws tương ứng (cách trình bày thay đổi theo JDK). Nếu file không được tạo, kiểm tra kết quả lệnh, đường dẫn hiện hành và cảnh báo DocLint trước khi kết luận nội dung source sai.

## 12. Checklist tự đánh giá

- [ ] Nhận diện được ba cú pháp `//`, `/* ... */`, `/** ... */`.
- [ ] Hiểu `//` hết hiệu lực tại cuối dòng và comment khối phải đóng `*/`.
- [ ] Biết block comment **không lồng nhau**.
- [ ] Hiểu `//` và `/* ... */` bên trong chuỗi không trở thành comment.
- [ ] Biết Javadoc comment dùng cho tài liệu API, không phải câu lệnh thực thi.
- [ ] Viết được `@param`, `@return`, `@throws` theo chữ ký/hợp đồng method.
- [ ] Phân biệt tag Javadoc `@deprecated` với annotation `@Deprecated`.
- [ ] Biên dịch/chạy `CommentDemo.java` bằng `javac`/`java` và đối chiếu đầu ra.
- [ ] Dùng `javadoc -d docs` để sinh và xem tài liệu HTML.
- [ ] Viết chú thích giải thích quyết định, quy tắc và giới hạn, thay vì lặp lại điều mã đã nói.

**Kết luận:** `//` phù hợp cho chú thích ngắn; `/* ... */` cho chú thích khối; `/** ... */` cho tài liệu API sinh được bằng `javadoc`. Chú thích giúp người đọc hiểu mã nhưng **không tự thay đổi hành vi JVM**. Một comment tốt phải đúng với code hiện tại, rõ ràng về ý định và được cập nhật khi hành vi thay đổi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-vocabulary-008">Unicode trong mã nguồn và xử lý Unicode escape trước phân tích token</a>

<details>
<summary>Xem chi tiết</summary>

# Unicode trong mã nguồn và xử lý Unicode escape trước phân tích token — Java 17+

Trong Java, một chuỗi ký tự giống `\u0041` không nhất thiết được chờ đến lúc chương trình chạy mới chuyển thành chữ `A`. **Unicode escape hợp lệ trong mã nguồn được xử lý rất sớm, trước khi Java nhận diện comment, string literal, identifier hoặc từ khóa.**

Đây là một đặc điểm quan trọng của *quá trình dịch mã nguồn* trong Java. Nó giải thích vì sao một ký tự viết dưới dạng `\u0061` có thể trở thành một phần tên biến; vì sao `\u000a` có thể kết thúc comment; và vì sao một cách viết tưởng như nằm trong string lại gây lỗi biên dịch.

Mục tiêu của bài: hiểu thứ tự xử lý, phân biệt Unicode escape của **mã nguồn** với escape của **string/char literal**, và tự kiểm chứng trên **JDK 17 trở lên**.

## 1. Ba tầng xử lý dễ bị nhầm

Hãy hình dung tệp `.java` được xử lý theo thứ tự:

~~~text
Tệp .java lưu dưới UTF-8
        │ javac -encoding UTF-8
        ▼
Ký tự đầu vào đã được giải mã từ byte
        │ Bước xử lý Unicode escape hợp lệ: \uXXXX
        ▼
Chuỗi ký tự Java sau chuyển đổi Unicode escape
        │ Nhận diện kết thúc dòng, comment, identifier,
        │ keyword, toán tử, string/char literal ...
        ▼
Các token để phân tích cú pháp và kiểm tra kiểu
        │
        ▼
Bytecode (.class), nếu mã hợp lệ
~~~

**Thứ tự là điểm cốt lõi.** Unicode escape được xử lý **trước khi** token hóa; điều này áp dụng ngay cả khi văn bản trông như đang nằm trong comment hoặc string.

| Thứ cần phân biệt | Ví dụ | Giai đoạn/ý nghĩa |
| --- | --- | --- |
| Mã hóa tệp nguồn | UTF-8 | Quy tắc giải mã byte thành ký tự khi đọc tệp |
| Unicode escape trong nguồn | `\u0041` | Được chuyển thành ký tự `A` trước khi phân tích token, nếu đủ điều kiện |
| Escape trong Java string | `\n`, `\t`, `\\`, `\"` | Được hiểu theo cú pháp string/char literal sau bước Unicode escape |
| Unicode code point | U+1F680 (🚀) | Một ký tự Unicode có thể cần **hai** đơn vị mã UTF-16 |
| Kiểu `char` | `'A'` | Một **UTF-16 code unit**, không nhất thiết là một Unicode code point đầy đủ |

Cú pháp Unicode escape thông thường là **dấu gạch chéo ngược**, một hoặc nhiều chữ `u` thường, rồi **chính xác bốn chữ số thập lục phân** (`0–9`, `a–f`, `A–F`). Ví dụ `\u0041` và `\uuuu0041` đều biểu diễn `A` ở bước chuyển đổi của mã nguồn. Nó **không** có dạng `\u{1F680}` trong Java.

## 2. Ví dụ runnable: escape làm thay đổi cả tên biến

Tạo tệp **`UnicodeStageDemo.java`** dưới dạng UTF-8:

~~~java
public class UnicodeStageDemo {
    public static void main(String[] args) {
        int ca\u0074 = 3;             // Ten bien sau dich: cat.
        String fromEscape = "\u0041";  // Sau dich: "A".
        String literalText = "\\u0041";
        String rocket = "🚀";

        System.out.println("cat=" + cat);
        System.out.println("fromEscape=" + fromEscape);
        System.out.println("literalText=" + literalText);
        System.out.println("utf16Length=" + rocket.length());
        System.out.println(
                "codePoints=" +
                rocket.codePointCount(0, rocket.length()));
    }
}
~~~

**Biên dịch và chạy** trong thư mục chứa tệp, trên Windows PowerShell hoặc Linux/Bash:

~~~bash
java -version
javac -version
javac -encoding UTF-8 UnicodeStageDemo.java
java UnicodeStageDemo
~~~

Kết quả dự kiến:

~~~text
cat=3
fromEscape=A
literalText=\u0041
utf16Length=2
codePoints=1
~~~

**Giải thích từng kết quả:**

1. `ca\u0074` được chuyển thành `cat` **trước khi phân tích identifier**. Vì vậy mã có thể khai báo bằng cách viết có escape và sử dụng biến bằng `cat`.
2. `"\u0041"` trở thành chuỗi nguồn `"A"` trước khi token hóa; giá trị chạy là `A`.
3. `"\\u0041"` chứa **hai dấu backslash trong tệp nguồn**. Theo quy tắc *điều kiện nhận Unicode escape* của Java, dấu thứ hai trong cặp đó không bắt đầu Unicode escape; khi xử lý string literal, `\\` biểu diễn **một** backslash. Vì vậy chương trình thực sự in chuỗi ký tự `\u0041`.
4. `🚀` là Unicode code point U+1F680, nằm ngoài Basic Multilingual Plane. Một `String` Java lưu dưới dạng các đơn vị mã UTF-16: `length()` trả `2` cho surrogate pair, trong khi `codePointCount(...)` trả `1`.

Đoạn ví dụ chỉ in chữ ASCII và số ở đầu ra, nên kết quả ít phụ thuộc cách terminal hiển thị emoji; tuy nhiên **tệp nguồn phải được lưu đúng UTF-8**.

## 3. Unicode escape được xử lý cả ở nơi trông như comment

Xem đoạn sau — một ví dụ **cố ý gây bất ngờ**. Tạo riêng tệp **`CommentBoundary.java`**:

~~~java
public class CommentBoundary {
    public static void main(String[] args) {
        // \u000a System.out.println("THIS RUNS");
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 CommentBoundary.java
java CommentBoundary
~~~

Đầu ra:

~~~text
THIS RUNS
~~~

**Tại sao dòng tưởng là comment vẫn in ra?**

- `\u000a` biểu diễn ký tự xuống dòng LF.
- Trước khi Java nhận diện comment `//`, Unicode escape được chuyển thành **xuống dòng thật**.
- Vì vậy, Java nhìn thấy comment kết thúc ngay tại dòng mới ấy; câu lệnh `System.out.println(...)` nằm **sau comment** và được thực thi.

Nếu diễn tả mã **sau bước dịch Unicode escape**, nó tương đương về ý nghĩa với:

~~~java
// comment ket thuc tai day
System.out.println("THIS RUNS");
~~~

**Đừng dùng mẹo này trong mã thực tế.** Nó gây khó đọc, dễ làm công cụ phân tích/lint không theo đúng bước chuyển đổi hiểu sai và tạo nguy cơ khi review mã. Dùng xuống dòng thật và comment rõ ràng.

### Bẫy tương tự trong string và char literal

Các đoạn sau **cố ý không biên dịch** nếu đưa vào một phương thức:

~~~java
// String s = "\u000a"; // Khong dung: escape tao xuong dong
// char c = '\u000a';   // Khong dung: escape tao xuong dong
~~~

Hai dòng ở trên được viết dưới dạng comment để minh họa, **nhưng chính Unicode escape trong comment cũng vẫn có thể được chuyển đổi**. Vì thế **không nên chép chúng vào tệp Java để tắt lỗi bằng `//`**.

Trong tài liệu, ta hiểu các mẫu `"\u000a"` và `'\u000a'` là văn bản **trước** xử lý Unicode escape: chúng sẽ làm đứt literal thành nhiều dòng, gây lỗi biên dịch. Nếu muốn chứa ký tự xuống dòng trong một chuỗi Java bình thường, hãy dùng escape của string literal:

~~~java
String safe = "\n"; // Hop le, 1 ky tu xuong dong luc chay.
System.out.println(safe.length()); // 1
~~~

**Lưu ý an toàn về cách ghi ví dụ lỗi:** Đoạn `// String s = "\u000a";` trên được hiển thị trong README để phân tích, không phải câu lệnh để đưa vào file nguồn. Trong Java thật, lexer vẫn xử lý `\u000a` trong comment, nên cách comment-out thông thường **không che được** nó. Đây chính là nội dung bài học.

## 4. Phân biệt `\uXXXX` với các escape ở thời điểm chạy

| Trong tệp `.java` | Kết quả của bước Unicode escape | Giá trị/hiệu ứng |
| --- | --- | --- |
| `"\u0041"` | `"A"` | Chuỗi chứa chữ `A` |
| `'\u0042'` | `'B'` | `char` chứa chữ `B` |
| `"\\u0041"` | Không chuyển cặp `\\u0041` thành `A` | String chứa backslash và năm ký tự `u0041` |
| `"\n"` | Không phải Unicode escape | String có một ký tự xuống dòng nhờ escape của literal |
| `\u0069nt number = 1;` | `int number = 1;` | `int` được nhận diện là keyword |
| `// \u000a printSomething();` | Xuống dòng được chèn **trước** phân tích comment | Phần sau có thể trở thành mã Java ngoài comment |

Không phải cứ thấy backslash + `u` là mã chắc chắn chuyển đổi ngay. Java có **quy tắc xác định backslash nào đủ điều kiện mở Unicode escape**, phụ thuộc vào chuỗi backslash liền trước. Ví dụ nguồn `"\\u0041"` không dịch thành `"A"`. Không cần học thuộc công thức đếm mọi backslash khi mới bắt đầu; hãy nhớ **khác biệt giữa một và hai backslash**, và dùng `javac` để tự kiểm chứng khi mã phức tạp.

Cũng đừng nghĩ có thể viết `\u005c` (escape tạo ra ký tự backslash) rồi ghép thêm `u0041` để khiến compiler dịch nhiều lượt. **Unicode escape không được xử lý lặp vô hạn**; ký tự backslash sinh ra từ bước dịch không tự bắt đầu một Unicode escape mới.

## 5. Unicode trong identifier và quy tắc đặt tên

Java cho phép nhiều ký tự Unicode trong identifier, tùy các quy tắc `Character.isJavaIdentifierStart` / `Character.isJavaIdentifierPart` và các ngoại lệ theo đặc tả ngôn ngữ. Vì vậy, identifier không nhất thiết chỉ gồm `A-Z`, `a-z` và chữ số ASCII.

Ví dụ có thể khai báo identifier tiếng Việt:

~~~java
int sốNgày = 3;
System.out.println(sốNgày); // 3
~~~

Tuy nhiên:

- Java **phân biệt chữ hoa và chữ thường**; tên phải khớp chính xác.
- Hai ký tự **trông giống nhau** không bảo đảm là cùng code point. Ví dụ ký tự Latin `a` và một ký tự từ hệ chữ khác có hình tương tự có thể tạo hai identifier khác nhau.
- Dạng ký tự tổ hợp và dạng ký tự dựng sẵn có thể hiển thị gần giống; đừng giả định Java tự chuẩn hóa mọi identifier theo cách bạn mong muốn.
- Với code nhóm, ưu tiên tên dễ đánh máy, dễ tìm kiếm và nhất quán theo convention. Unicode escape để “giấu” một phần tên thường làm mã khó review.

Với chữ ngoài Basic Multilingual Plane, `String` Java có thể lưu bằng **surrogate pair**; `char` vẫn chỉ đại diện một đơn vị mã UTF-16. Vì vậy, muốn xử lý Unicode theo code point thay vì code unit, dùng các API như `codePointAt`, `codePointCount` và `String.codePoints()` khi phù hợp.

## 6. Lỗi thường gặp và cách xác định nguyên nhân

| Triệu chứng | Nguyên nhân cần xem | Cách xử lý |
| --- | --- | --- |
| `illegal unicode escape` | `\u` không đủ bốn chữ số hex, hoặc chữ số sai | Sửa đúng escape hay bỏ backslash nếu muốn văn bản thuần |
| `unclosed string literal` | `\u000a` hoặc `\u000d` tạo line terminator trước khi phân tích literal | Dùng `\n` hoặc `\r` bên trong string literal |
| Dòng tưởng comment vẫn chạy | Escape trong `//` tạo newline | Không viết Unicode escape có hiệu ứng tách dòng trong comment |
| `cannot find symbol` với identifier | Tên sau xử lý escape khác tên bạn tham chiếu | Xem ký tự thật, chữ hoa/thường, code point |
| Ký tự tiếng Việt hoặc emoji bị lỗi khi biên dịch | Mã hóa file không khớp cách `javac` đọc nguồn | Lưu UTF-8, biên dịch với `-encoding UTF-8` |
| `String.length()` lớn hơn số ký tự nhìn thấy | Java đếm UTF-16 code unit, không phải code point hoặc grapheme cluster | Dùng API code point nếu đó là đơn vị muốn đếm |

`codePointCount` cũng **không** tự đếm “số ký tự người dùng nhìn thấy” (*grapheme clusters*): emoji ghép hoặc chữ có dấu kết hợp có thể gồm nhiều code point nhưng được hiển thị như một ký tự. Đừng dùng `length()` hay `codePointCount()` như một bộ đếm ký tự giao diện người dùng hoàn hảo nếu chưa xác định đơn vị cần đếm.

### Checklist kiểm tra khi gặp lỗi liên quan Unicode

1. File đang được lưu ở **mã hóa** nào? `javac` đang dùng encoding nào?
2. Chuỗi `\u....` có đủ điều kiện để trở thành Unicode escape không?
3. Sau khi dịch escape, ký tự đó có thể biến thành newline, dấu nháy, ký tự phân cách token hoặc một phần identifier không?
4. Thông báo lỗi xảy ra **khi biên dịch** hay sau khi chương trình đã chạy?
5. Bạn muốn đếm **UTF-16 code unit**, **code point**, hay **ký tự hiển thị**?

## 7. Bài tập thực hành

**Bài 1 — Dự đoán trước khi chạy.** Trong `UnicodeStageDemo`, nếu đổi `ca\u0074` thành `nu\u006Dber`, biến thực sự có tên gì?

*Đáp án:* `number`, vì `\u006D` được chuyển thành `m` trước khi lexer nhận diện identifier. Nhớ đổi nơi sử dụng biến tương ứng.

**Bài 2 — Một hay hai backslash?** So sánh hai dòng:

~~~java
String a = "\u0041";
String b = "\\u0041";
~~~

*Đáp án:* `a` chứa `A`; `b` chứa sáu ký tự gồm một backslash và `u0041`. Khi in, `b` hiển thị `\u0041`.

**Bài 3 — Vì sao comment có thể nguy hiểm?** Giải thích kết quả của `CommentBoundary` trước khi chạy. Tìm bước nào phải xảy ra **trước** phân tích `//`.

*Đáp án:* Unicode escape `\u000a` chuyển thành newline trước bước nhận diện comment, khiến phần sau trở thành câu lệnh thật.

**Bài 4 — Dùng string escape đúng.** Muốn string Java chứa một ký tự xuống dòng, nên viết `"\u000a"` hay `"\n"`?

*Đáp án:* `"\n"`. Unicode escape `\u000a` bị dịch thành line terminator quá sớm; `\n` là escape được phân tích trong string literal, tạo ký tự xuống dòng đúng ý định.

**Bài 5 — Code point và `char`.** Vì sao `"🚀".length()` trả về 2 nhưng `"🚀".codePointCount(0, "🚀".length())` trả về 1? Có thể dùng một `char` duy nhất để chứa toàn bộ code point U+1F680 không?

*Đáp án:* `String` dùng UTF-16; U+1F680 cần hai code unit (*surrogate pair*). Một `char` riêng lẻ không đủ để biểu diễn trọn code point đó.

## Ghi nhớ

**Unicode escape trong mã nguồn Java được xử lý trước khi tách token**, nên nó có thể thay đổi cả identifier, từ khóa, comment và ranh giới string literal. Điều này khác với escape như `\n` bên trong Java string, khác với encoding UTF-8 của tệp nguồn, và khác với quy tắc đếm UTF-16 của `String`. Khi gặp mã Unicode khó hiểu, hãy hình dung **tệp nguồn → dịch Unicode escape → nhận diện token → phân tích cú pháp**, rồi mới xem hành vi khi chạy.


</details>

- [Quay lại đầu trang](#back-to-top)
