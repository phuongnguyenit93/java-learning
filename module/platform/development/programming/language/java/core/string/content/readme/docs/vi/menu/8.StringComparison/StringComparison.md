<a id="back-to-top"></a>

# So sánh

## Menu
- [== so sánh tham chiếu còn equals so sánh nội dung String](#string-comparison-001)
- [equals với null và với đối tượng không phải String](#string-comparison-002)
- [Objects.equals cho hai tham chiếu có thể null](#string-comparison-003)
- [equalsIgnoreCase và giới hạn không phụ thuộc locale](#string-comparison-004)
- [compareTo và thứ tự từ điển theo giá trị char](#string-comparison-005)
- [compareToIgnoreCase và CASE_INSENSITIVE_ORDER](#string-comparison-006)
- [contentEquals khi so sánh với StringBuilder hoặc CharSequence](#string-comparison-007)
- [regionMatches để so sánh một vùng chuỗi](#string-comparison-008)
- [Quan hệ giữa equals, hashCode và việc hai chuỗi băm trùng](#string-comparison-009)

## <a id="string-comparison-001">== so sánh tham chiếu còn equals so sánh nội dung String</a>

<details>
<summary>Xem chi tiết</summary>

# `==` so sánh tham chiếu, còn `equals()` so sánh nội dung `String`

Trong Java, hai biến `String` có thể lưu những chuỗi ký tự **giống hệt nhau** nhưng vẫn tham chiếu tới **hai đối tượng khác nhau**. Vì vậy, biểu thức `a == b` và `a.equals(b)` trả lời hai câu hỏi khác nhau:

- **`a == b`**: hai biến tham chiếu có đang trỏ đến **cùng một đối tượng** hay không?
- **`a.equals(b)`**: theo hợp đồng của lớp `String`, hai đối tượng có chứa **cùng dãy ký tự UTF-16** hay không?

Khi kiểm tra tên, mã, trạng thái, nội dung nhập từ người dùng hoặc chuỗi đọc từ API, **hầu như luôn dùng `equals()` cho so sánh nội dung**. Dùng `==` khi thật sự muốn biết hai tham chiếu có cùng đối tượng, hoặc khi so sánh tham chiếu với `null`.

## 1. Phân biệt “cùng đối tượng” với “cùng giá trị”

Đoạn mã sau minh họa trực tiếp:

```java
String a = new String("Java");
String b = new String("Java");

System.out.println(a == b);      // false
System.out.println(a.equals(b)); // true
```

Hai lời gọi `new String("Java")` tạo hai đối tượng `String` riêng biệt, dù các đối tượng cùng chứa nội dung `"Java"`.

Hãy hình dung:

```text
Biến a ───────► String object #1: "Java"
Biến b ───────► String object #2: "Java"

a == b       → false  (hai đối tượng khác nhau)
a.equals(b)  → true   (nội dung giống nhau)
```

Ngược lại, nếu gán tham chiếu:

```java
String a = new String("Java");
String b = a;

System.out.println(a == b);      // true
System.out.println(a.equals(b)); // true
```

Lúc này `a` và `b` cùng trỏ đến một đối tượng. Vì thế `==` trả về `true`, và dĩ nhiên nội dung cũng bằng nhau.

| Tình huống | `a == b` | `a.equals(b)` khi `a != null` |
| --- | --- | --- |
| Hai biến cùng trỏ tới một `String` | `true` | `true` |
| Hai đối tượng `String` khác nhau nhưng cùng nội dung | `false` | `true` |
| Hai đối tượng `String` có nội dung khác nhau | `false` | `false` |
| `a` khác `null`, `b == null` | `false` | `false` |
| Cả `a` và `b` đều `null` | `true` | **Ném `NullPointerException`** nếu gọi `a.equals(b)` |

Chú ý: với kiểu nguyên thủy như `int` hoặc `boolean`, `==` so sánh **giá trị primitive**. Bài này nói về hai toán hạng có kiểu **tham chiếu `String`**: trong trường hợp đó, `==` so sánh danh tính đối tượng, không so sánh từng ký tự.

## 2. Java định nghĩa `String.equals()` như thế nào?

`String` ghi đè phương thức `equals(Object)` kế thừa từ `Object`. Nó chỉ trả về `true` khi đối số là một `String` chứa **chính xác cùng chuỗi các đơn vị mã UTF-16** với đối tượng nhận lời gọi. Điều đó có một số hệ quả:

1. **Phân biệt chữ hoa và chữ thường**: `"Java".equals("java")` là `false`.
2. **Tính cả khoảng trắng**: `"Java".equals("Java ")` là `false`.
3. **Tính cả ký tự xuống dòng và dấu điều khiển**: `"A\n".equals("A")` là `false`.
4. **Phân biệt ký tự Unicode được biểu diễn bằng các dãy mã khác nhau**: chẳng hạn `"\u00E9"` (é ghép sẵn) và `"e\u0301"` (e cùng dấu sắc kết hợp) có thể hiển thị giống nhau nhưng `equals()` là `false`.
5. **Không tự chuẩn hóa, không tự `trim()` và không tự đổi chữ hoa/chữ thường**. Nếu bài toán cần quy tắc khác, chương trình phải xác định và thực hiện quy tắc đó trước khi so sánh.

Với hai `String` thực sự cùng nội dung, `equals()` luôn trả về `true`, bất kể chúng nằm ở các đối tượng khác nhau hoặc được tạo qua những cách khác nhau. Đây mới là phép so sánh đúng về **giá trị văn bản**.

`String.equals()` nhận tham số kiểu `Object`. Khi đối số không phải một đối tượng `String` (kể cả `null`), kết quả là `false`:

```java
"Java".equals(null)               // false
"Java".equals(new Object())       // false
"Java".equals(new String("Java")) // true
```

Các biểu thức trên là ví dụ minh họa trong một phương thức, không phải chương trình hoàn chỉnh.

**Đừng áp dụng máy móc quy tắc này cho mọi lớp Java.** Phương thức `Object.equals()` mặc định dùng so sánh danh tính; nhiều lớp khác định nghĩa `equals()` theo hợp đồng riêng. `StringBuilder` chẳng hạn không ghi đè `Object.equals()` để so sánh nội dung giống `String`. Chủ đề ở đây là **hợp đồng cụ thể của `String.equals()`**.

## 3. Vì sao so sánh hai chuỗi literal bằng `==` đôi khi lại trả về `true`?

Java có cơ chế **String Pool**: các chuỗi literal cùng nội dung được dùng chung một tham chiếu `String` đã được intern. Vì vậy:

```java
String a = "Java";
String b = "Java";

System.out.println(a == b);      // true
System.out.println(a.equals(b)); // true
```

Kết quả `==` là `true` ở đây là hệ quả của **hai biến đang cùng tham chiếu một đối tượng trong pool**. Nó **không** có nghĩa `==` đã chuyển thành phép so sánh nội dung.

Trong khi đó:

```java
String a = "Java";
String b = new String("Java");

System.out.println(a == b);      // false
System.out.println(a.equals(b)); // true
```

Lời gọi `new String("Java")` tạo một đối tượng riêng, mặc dù cùng giá trị. Vì vậy, việc dựa vào `==` để đánh giá dữ liệu văn bản có thể tạo ra lỗi chỉ xuất hiện khi thay đổi nguồn hoặc cách xây dựng chuỗi.

### Nối chuỗi hằng lúc biên dịch và nối lúc chạy

Một biểu thức nối mà **mọi thành phần là hằng thời gian biên dịch** có thể được tính sẵn và trở thành chuỗi literal được intern:

```java
String a = "Java";
String folded = "Ja" + "va";

System.out.println(a == folded); // true
```

Ở trường hợp khác, giá trị được xây dựng lúc chạy:

```java
String prefix = "Ja";
String runtime = prefix + "va";

System.out.println(a.equals(runtime)); // true
```

Không nên dùng `a == runtime` để kiểm tra nội dung, cho dù cách tạo ở một phiên bản mã/JDK cụ thể khiến chương trình in gì. Đối với **nối chuỗi không phải hằng thời gian biên dịch**, quy tắc ngôn ngữ tạo chuỗi kết quả lúc chạy; và một thay đổi nhỏ trong cách khởi tạo có thể làm việc thử nghiệm `==` trước đó không còn đúng.

Lưu ý về từ khóa `final`: một biến `final String` được khởi tạo bằng **biểu thức hằng** có thể là *constant variable*, nên dùng nó trong biểu thức nối cũng có thể khiến toàn bộ nối chuỗi trở thành hằng thời gian biên dịch. Đừng cho rằng cứ xuất hiện một tên biến trong phép nối thì biểu thức chắc chắn được tính lúc chạy.

## 4. Ví dụ hoàn chỉnh Java 17+: `==`, `equals()`, literal và `intern()`

Lưu mã sau vào tệp `StringEqualityDemo.java`:

```java
public class StringEqualityDemo {
    public static void main(String[] args) {
        String literal = "Java";
        String sameLiteral = "Java";
        String allocated = new String("Java");
        String runtime = new StringBuilder("Ja")
                .append("va").toString();
        String folded = "Ja" + "va";
        String pooled = allocated.intern();

        System.out.println(
                "literal == sameLiteral: " + (literal == sameLiteral));
        System.out.println(
                "literal == allocated: " + (literal == allocated));
        System.out.println(
                "literal.equals(allocated): " + literal.equals(allocated));
        System.out.println(
                "literal == runtime: " + (literal == runtime));
        System.out.println(
                "literal.equals(runtime): " + literal.equals(runtime));
        System.out.println(
                "literal == folded: " + (literal == folded));
        System.out.println(
                "literal == pooled: " + (literal == pooled));
        System.out.println(
                "literal.equals(java): " + literal.equals("java"));
        System.out.println(
                "literal.equals(null): " + literal.equals(null));
    }
}
```

Biên dịch và chạy:

```bash
javac StringEqualityDemo.java
java StringEqualityDemo
```

Kết quả:

```text
literal == sameLiteral: true
literal == allocated: false
literal.equals(allocated): true
literal == runtime: false
literal.equals(runtime): true
literal == folded: true
literal == pooled: true
literal.equals(java): false
literal.equals(null): false
```

**Giải thích từng nhóm kết quả:**

- `literal` và `sameLiteral` là cùng một literal nên tham chiếu cùng đối tượng đã được intern.
- `allocated` là một `String` mới. Vì vậy `literal == allocated` trả `false` nhưng `literal.equals(allocated)` vẫn là `true`.
- `runtime` được tạo từ `StringBuilder.toString()`; nó mang cùng dãy ký tự nhưng là một đối tượng chuỗi độc lập trong ví dụ.
- `folded` là phép nối hai literal tại thời điểm biên dịch nên trùng tham chiếu pool với `"Java"`.
- `allocated.intern()` trả về tham chiếu chuẩn của chuỗi cùng nội dung trong pool, nên `literal == pooled` là `true`.
- `equals()` so sánh **chính xác** dãy ký tự; `"Java"` và `"java"` khác nhau do chữ hoa/chữ thường, còn đối số `null` đơn giản không phải một chuỗi bằng nội dung.

`intern()` là một API thực sự tồn tại nhưng **không cần thiết** để kiểm tra nội dung. Không gọi `intern()` hàng loạt chỉ để khiến `==` cho kết quả mong muốn: dùng `equals()` vừa đơn giản hơn vừa làm rõ đúng ý nghĩa của điều kiện.

## 5. Xử lý `null` khi so sánh nội dung

`equals()` là phương thức thực thể: nếu `input == null` thì `input.equals("OK")` ném `NullPointerException`. Ngược lại, `"OK".equals(input)` trả `false` khi đầu vào thiếu, bởi literal nhận lời gọi luôn khác `null`.

```java
String input = null;
System.out.println("OK".equals(input)); // false
System.out.println(input != null && input.equals("OK")); // false
System.out.println(java.util.Objects.equals(input, "OK")); // false
System.out.println(java.util.Objects.equals(null, null)); // true
```

Đặt literal bên trái khi so với hằng, dùng `&&` để kiểm tra receiver trước khi gọi, hoặc chọn `Objects.equals(a, b)` khi cả hai có thể `null` **và** yêu cầu coi hai giá trị vắng mặt là bằng nhau. Những cách này xử lý dữ liệu nullable; chúng không làm `==` trở thành phép so sánh nội dung.

Các bài riêng về `String.equals(null)` và `Objects.equals` phân tích sâu hơn đối số khác kiểu, hợp đồng null-safe và các trường hợp nghiệp vụ.



## 6. Tại sao `==` có thể tạo lỗi “chạy máy tôi thì đúng”?

Giả sử một ứng dụng kiểm tra một trạng thái:

```java
String status = new String("READY");

if (status == "READY") {
    System.out.println("Accepted");
} else {
    System.out.println("Rejected");
}
```

Kết quả là `Rejected`, dù `status` chứa đúng chữ `"READY"`. Nguyên nhân: `new String("READY")` tạo object khác với literal `"READY"`, vì vậy `==` là `false`.

Đổi thành:

```java
if ("READY".equals(status)) {
    System.out.println("Accepted");
} else {
    System.out.println("Rejected");
}
```

Kết quả là `Accepted`. Điều kiện giờ kiểm tra **nội dung**, đúng với mục tiêu nghiệp vụ.

Lỗi dạng này dễ lọt qua kiểm thử sơ sài nếu thử bằng chuỗi literal trong code, khi hai tham chiếu vô tình được chia sẻ từ String Pool. Khi dữ liệu đến từ API, tệp, người dùng hoặc chuỗi ghép lúc chạy, hành vi dựa trên danh tính đối tượng không còn phù hợp. Vì vậy, tránh dựa vào String Pool để viết điều kiện nội dung.

## 7. Liên hệ với tính bất biến và `hashCode()`

`String` là **immutable**: nội dung của một đối tượng `String` không thể bị thay đổi sau khi tạo bằng API thông thường.

```java
String a = "Java";
String b = a;

a = a.concat(" 17");

System.out.println(a);           // Java 17
System.out.println(b);           // Java
System.out.println(a == b);      // false
System.out.println(a.equals(b)); // false
```

Gán lại biến `a` không sửa đối tượng `String` cũ mà `b` vẫn giữ. Do đó, hai phép so sánh sau phép nối trong ví dụ cùng trả về `false`, nhưng **vì hai lý do khác nhau**: tham chiếu khác nhau và nội dung cũng khác nhau.

Nếu hai `String` bằng nhau theo `equals()` thì `hashCode()` phải bằng nhau. Chiều ngược lại sai khi hai chuỗi khác nội dung có mã băm trùng nhau. Bài riêng về `equals` và `hashCode` giải thích cách `HashMap`/`HashSet` xử lý các trường hợp này.

## 8. Phân biệt các cách so sánh chuỗi thường gặp

| Phương thức/toán tử | Kiểm tra | Khi dùng |
| --- | --- | --- |
| `a == b` | Hai tham chiếu cùng object (hoặc cùng `null`) | Khi danh tính đối tượng thực sự là mục tiêu; kiểm tra `null`. |
| `a.equals(b)` | Hai chuỗi cùng dãy UTF-16 code units | So sánh nội dung chính xác và `a` chắc chắn khác `null`. |
| `"X".equals(a)` | Nội dung của `a` đúng bằng `"X"` | So sánh với hằng literal, an toàn khi `a` là `null`. |
| `Objects.equals(a, b)` | Bằng nhau theo `equals`, xử lý `null` | Khi một hoặc cả hai tham chiếu có thể là `null`. |
| `a.equalsIgnoreCase(b)` | So sánh bỏ qua một số khác biệt hoa/thường theo hợp đồng `String` | Chỉ khi quy tắc cho phép bỏ qua khác biệt hoa/thường. |
| `a.compareTo(b)` | So sánh thứ tự từ điển dựa trên UTF-16 code units | Khi cần sắp xếp/tính thứ tự; trả số âm, 0 hoặc dương. |
| `a.contentEquals(charSequence)` | So sánh nội dung với `CharSequence` | Khi đối tượng kia thuộc kiểu như `StringBuilder` mà yêu cầu là so sánh ký tự. |

`compareTo() == 0` với hai `String` tương đương `equals()` về nội dung; `compareTo()` không nhằm trả lời câu hỏi hai biến có cùng tham chiếu. Cũng cần lưu ý các thao tác sắp xếp dựa trên Unicode/locale có thể cần `Collator` hoặc thuật toán riêng nếu ứng dụng muốn thứ tự ngôn ngữ tự nhiên, không phải thứ tự UTF-16.

## 9. Sai lầm phổ biến và cách tránh

1. **Dùng `==` để kiểm tra dữ liệu văn bản**: `if (input == "yes")` có thể sai vì danh tính khác. Dùng `"yes".equals(input)` để kiểm tra nội dung.
2. **Tin rằng `==` luôn sai với hai chuỗi**: cũng không đúng; hai literal cùng nội dung có thể tham chiếu cùng object do pool, hoặc hai biến cùng trỏ tới một object. Vấn đề nằm ở ý nghĩa phép kiểm tra.
3. **Cho rằng `equals()` không bao giờ ném ngoại lệ**: gọi trên tham chiếu `null` gây `NullPointerException`. Dùng literal ở bên trái hoặc `Objects.equals()` khi cần.
4. **Cho rằng `Objects.equals(null, null)` trả `false`**: kết quả là `true`; hai tham chiếu đều `null` được coi là bằng nhau bởi helper này.
5. **Lạm dụng `intern()` để “sửa” so sánh**: không cần intern để so sánh đúng nội dung. Dùng `equals()`, chỉ xem xét intern nếu có mục đích riêng và đã cân nhắc trade-off.
6. **Nghĩ `equals()` tự bỏ khoảng trắng**: `"Java"` khác `"Java "`. Nếu muốn chuẩn hóa đầu vào, cần quy tắc rõ ràng, chẳng hạn `strip()` khi thực sự cho phép bỏ whitespace ở biên.
7. **Nghĩ `equals()` tự bỏ qua hoa/thường**: `"Java"` khác `"java"`. Chỉ dùng `equalsIgnoreCase()` nếu yêu cầu so sánh quy định điều đó.
8. **Nghĩ mọi chuỗi hiển thị giống nhau đều `equals() == true`**: Unicode có thể biểu diễn văn bản bằng nhiều dãy mã khác nhau. Cân nhắc chuẩn hóa nếu hệ thống cần.
9. **Dùng `hashCode()` để quyết định hai chuỗi bằng nhau**: băm trùng không chứng minh cùng nội dung; `equals()` mới diễn đạt chính xác điều kiện bằng nhau.
10. **Dùng `==` để so sánh chuỗi chỉ vì nó “nhanh hơn”**: trả lời sai bài toán không phải tối ưu. Ưu tiên tính đúng đắn; tối ưu sau khi đo và xác định vấn đề thực.

## 10. Câu hỏi tự kiểm tra

**Câu 1.** Dự đoán kết quả:

```java
String first = "hi";
String second = "hi";
String third = new String("hi");

System.out.println(first == second);
System.out.println(first == third);
System.out.println(first.equals(third));
```

Đáp án:

```text
true
false
true
```

Hai literal được intern cùng tham chiếu; `new String` tạo object khác nhưng cùng dãy ký tự.

**Câu 2.** Hai biến đều là `null` thì điều kiện nào trả `true`, điều kiện nào gây ngoại lệ?

```java
String a = null;
String b = null;

System.out.println(a == b);
System.out.println(java.util.Objects.equals(a, b));
// System.out.println(a.equals(b));
```

Hai dòng in đầu đều là `true`. Nếu bỏ chú thích dòng cuối, lời gọi `a.equals(b)` ném `NullPointerException` do `a` là `null`.

**Câu 3.** Hai chuỗi vừa khác tham chiếu vừa khác nội dung, `a == b` và `a.equals(b)` sẽ ra sao?

Cả hai cùng là `false`, nhưng ý nghĩa vẫn khác: `==` kiểm tra cùng object, `equals()` kiểm tra cùng văn bản. **Không thể suy ngược** từ việc hai kết quả trùng nhau rằng hai cơ chế tương đương.

**Câu 4.** Một chương trình đọc `String command` từ người dùng; cần nhận đúng lệnh viết hoa `"START"`. Viết điều kiện nào?

```java
if ("START".equals(command)) {
    // Thực hiện tác vụ.
}
```

Điều kiện kiểm tra nội dung chính xác, phân biệt hoa/thường và không gặp NPE khi `command == null`. Nếu nghiệp vụ chấp nhận `"start"`, mới cân nhắc `equalsIgnoreCase()` cùng quy tắc chuẩn hóa phù hợp.

## Tổng kết

Với hai tham chiếu `String a` và `String b`, hãy tự hỏi:

- **Có đang cần biết hai biến trỏ cùng một object không?** → `a == b`.
- **Có đang cần biết hai giá trị văn bản giống nhau không?** → `a.equals(b)` nếu `a` không `null`; `Objects.equals(a, b)` khi cần an toàn với `null`.
- **Một vế là chuỗi literal cố định?** → thường dùng `"expected".equals(actual)` cho mục đích kiểm tra nội dung.
- **Yêu cầu có phân biệt hoa/thường, khoảng trắng hoặc Unicode không?** → xác định rõ quy tắc trước khi so sánh; `equals()` mặc định kiểm tra nội dung chính xác.

String Pool có thể khiến một vài phép `==` giữa literal trả `true`, nhưng **đó là tính chất chia sẻ tham chiếu**, không thay đổi hợp đồng so sánh. Mã xử lý dữ liệu văn bản đúng phải phản ánh điều kiện bằng nhau về **nội dung**, không lệ thuộc vào cách JVM tổ chức các đối tượng `String`.

## Tài liệu tham khảo

- [Java SE 21 API — `java.lang.String`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html): `equals`, `equalsIgnoreCase`, `compareTo`, `contentEquals`, `intern`.
- [Java Language Specification §15.21.3 — Reference Equality Operators](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.21.3): ý nghĩa của `==` và `!=` đối với kiểu tham chiếu.
- [Java Language Specification §3.10.5 — String Literals](https://docs.oracle.com/javase/specs/jls/se21/html/jls-3.html#jls-3.10.5): quy định literal chuỗi được intern.
- [Java SE 21 API — `java.util.Objects.equals`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Objects.html#equals(java.lang.Object,java.lang.Object)): phép kiểm tra bằng nhau an toàn với `null`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-comparison-002">equals với null và với đối tượng không phải String</a>

<details>
<summary>Xem chi tiết</summary>

# String.equals với null và đối tượng không phải String

Trong Java, **String.equals(Object)** kiểm tra xem đối số có phải một **String có cùng nội dung** với String ở phía gọi hay không. Hai tình huống dễ nhầm nhất là truyền **null** và truyền **một đối tượng thuộc kiểu khác String**:

- Một String không-null gọi **equals(null)** luôn trả về **false**, không ném ngoại lệ.
- Một String không-null gọi **equals(đối tượng không phải String)** luôn trả về **false**, ngay cả khi đối tượng đó có thể in ra văn bản giống hệt String.
- Nếu **biến ở phía trước dấu chấm là null**, lời gọi **biến.equals(...)** ném **NullPointerException** trước khi String.equals() có cơ hội thực thi.

Phân biệt **đối số null** với **đối tượng gọi phương thức null** là chìa khóa để đọc đúng kết quả và viết điều kiện so sánh an toàn.

## 1. Chữ ký và nguyên tắc so sánh

String cung cấp phương thức:

~~~java
public boolean equals(Object anObject)
~~~

Đây là **chữ ký API để minh họa**, không phải khai báo một phương thức mới mà người học cần thêm vào lớp. Phương thức nhận tham số kiểu Object, nhưng **String.equals() chỉ xét nội dung bằng nhau khi đối số thực sự là một đối tượng String**. Nó không tự đổi Integer, StringBuilder, char[] hoặc bất kỳ kiểu nào khác thành String để so sánh.

Với đối tượng String đang gọi phương thức:

1. Nếu hai tham chiếu cùng trỏ tới một String, kết quả là true.
2. Nếu đối số là một String khác đối tượng nhưng có cùng dãy đơn vị mã UTF-16, kết quả vẫn là true.
3. Nếu đối số là null, kết quả là false.
4. Nếu đối số không phải String, kết quả là false.
5. Nếu cả hai đều là String nhưng nội dung khác nhau, kết quả là false.

**equals() so sánh giá trị nội dung; toán tử == so sánh danh tính tham chiếu** khi cả hai toán hạng là kiểu tham chiếu. Đây là hai phép kiểm tra khác nhau.

~~~java
String first = new String("Java");
String second = new String("Java");

System.out.println(first == second);      // false
System.out.println(first.equals(second)); // true
~~~

Hai lệnh new tạo hai đối tượng String khác nhau, nên == là false. Nội dung "Java" bằng nhau nên equals() là true.

## 2. Truyền null cho String.equals(): kết quả false

Ví dụ:

~~~java
String language = "Java";
String missing = null;

System.out.println(language.equals(null));    // false
System.out.println(language.equals(missing)); // false
System.out.println("Java".equals(null));      // false
~~~

**Vì sao không ném NullPointerException?** Đối tượng ở phía gọi (language hoặc literal "Java") tồn tại. Java thực thi String.equals(), và phương thức xác định null không phải đối tượng String có nội dung bằng nó.

So sánh với cách gọi ngược lại:

~~~java
String missing = null;
System.out.println(missing.equals("Java")); // Ném NullPointerException
~~~

Đoạn trên là **minh họa lỗi**, không phải đoạn cần chạy chung với những ví dụ đang in kết quả phía trước. Phép truy cập phương thức qua biến missing không có đối tượng nhận vì missing bằng null. Đây là lỗi ở **receiver** (đối tượng nhận lời gọi), không phải do equals() “không nhận null”.

Có thể ghi nhớ:

| Biểu thức | Kết quả | Nguyên nhân |
|---|---|---|
| "Java".equals(null) | false | Receiver là String hợp lệ; đối số null |
| "Java".equals("Java") | true | Nội dung bằng nhau |
| "Java".equals("java") | false | Phân biệt chữ hoa/chữ thường |
| ((String) null).equals("Java") | NullPointerException | Receiver là null |
| null == null | true | == cho phép so sánh hai tham chiếu null |
| "Java" == null | false | Tham chiếu String không-null |

**Không dùng trực tiếp null.equals(...):** từ khóa null không phải biểu thức nhận lời gọi phương thức hợp lệ; trường hợp chạy thực tế là một biến tham chiếu có giá trị null, như missing.equals(...).

## 3. Truyền đối tượng khác String: luôn false

Vì chữ ký nhận Object, trình biên dịch cho phép truyền nhiều loại tham số. Nhưng String.equals() **không ép kiểu ngầm thành String**, không gọi toString() rồi so sánh, và không so sánh “hai đối tượng hiển thị giống nhau” nếu đối tượng kia không phải String.

Ví dụ:

~~~java
String text = "42";
Integer number = 42;

System.out.println(text.equals(number)); // false
~~~

Mặc dù number.toString() tạo ra "42", number là Integer nên text.equals(number) là false.

Tương tự:

~~~java
String text = "Java";
StringBuilder builder = new StringBuilder("Java");

System.out.println(text.equals(builder));            // false
System.out.println(text.equals(builder.toString())); // true
~~~

StringBuilder có nội dung hiển thị "Java" nhưng **không phải String**. toString() tạo String mới, lúc đó String.equals() mới so sánh được nội dung.

### Bảng những kiểu đối số khác nhau

Giả sử receiver luôn là **"Java"**:

| Đối số | Loại đối số ở runtime | Kết quả equals() |
|---|---|---|
| "Java" | String | true |
| new String("Java") | String | true |
| "java" | String | false |
| null | null | false |
| new StringBuilder("Java") | StringBuilder | false |
| new StringBuffer("Java") | StringBuffer | false |
| new char[] {'J', 'a', 'v', 'a'} | char[] | false |
| Integer.valueOf(123) | Integer | false |
| new Object() | Object | false |

Nếu cần **cố ý** so sánh nội dung String với một CharSequence khác, có thể cân nhắc **String.contentEquals(CharSequence)**. Chẳng hạn:

~~~java
String text = "Java";
StringBuilder builder = new StringBuilder("Java");

System.out.println(text.equals(builder));        // false
System.out.println(text.contentEquals(builder)); // true
~~~

Hai phương thức trả về khác nhau vì **hợp đồng so sánh khác nhau**: equals() chỉ chấp nhận String có cùng nội dung, còn contentEquals() được thiết kế để so sánh nội dung với CharSequence. Không nên tự thay equals() bằng contentEquals() ở mọi nơi, vì ý nghĩa yêu cầu so sánh kiểu dữ liệu có thể khác.

## 4. Kiểu khai báo Object không quyết định kết quả: kiểu thực tế mới quan trọng

Một biến khai báo kiểu **Object** vẫn có thể trỏ tới String. Trong trường hợp đó, khi truyền nó cho String.equals(), phương thức xét **đối tượng thực tại runtime**, không chỉ nhìn kiểu khai báo.

~~~java
String expected = "Java";

Object stringValue = new String("Java");
Object builderValue = new StringBuilder("Java");
Object nullValue = null;

System.out.println(expected.equals(stringValue));  // true
System.out.println(expected.equals(builderValue)); // false
System.out.println(expected.equals(nullValue));    // false
~~~

Ở dòng thứ nhất, stringValue có kiểu biến Object nhưng giá trị thực là String chứa "Java", nên kết quả true. Dòng thứ hai tham chiếu StringBuilder, nên false. Dòng cuối là null, cũng false.

**Không được suy diễn rằng tham số Object đồng nghĩa các kiểu đều được đổi sang String.** Object là kiểu tham số rộng; logic cụ thể của String.equals() mới quyết định thế nào là bằng nhau.

## 5. Khi receiver có thể `null`: chọn cách gọi phù hợp

Nếu cần kiểm tra một trạng thái cố định, hãy gọi `equals()` trên literal. Nếu muốn gọi trên biến, dùng `&&` để bảo đảm biến khác `null` trước khi gọi:

~~~java
String status = null;
System.out.println("ACTIVE".equals(status)); // false
System.out.println(status != null && status.equals("ACTIVE")); // false
~~~

Với **hai** tham chiếu đều có thể `null`, `Objects.equals(left, right)` trả `true` nếu cùng `null`, trả `false` nếu chỉ một bên `null`, còn khi `left` khác `null` thì dùng `left.equals(right)`. Nếu hai giá trị chưa được cung cấp không được coi là bằng nhau trong nghiệp vụ, phải kiểm tra tính hiện diện riêng.

Bài kế tiếp giải thích đầy đủ `Objects.equals` qua String, mảng và các lớp tự định nghĩa. Ở đây, khác biệt cần nhớ là **đối số `null`** so với **receiver `null`** của chính `String.equals(Object)`.

## 6. Chương trình Java 17+ chạy độc lập

Chương trình dưới đây tổng hợp cả null, đối số không phải String, biến khai báo Object, Objects.equals() và các mẫu so sánh an toàn.

Lưu thành file **StringEqualsNullAndTypeDemo.java**:

~~~java
import java.util.Objects;

public class StringEqualsNullAndTypeDemo {
    public static void main(String[] args) {
        String value = "Java";
        String missing = null;

        Object stringAsObject = new String("Java");
        Object builder = new StringBuilder("Java");
        Object number = Integer.valueOf(42);

        System.out.println("literal-null=" + "Java".equals(null));
        System.out.println("value-null=" + value.equals(missing));
        System.out.println("safe-constant=" + "Java".equals(missing));

        System.out.println("object-string=" + value.equals(stringAsObject));
        System.out.println("object-builder=" + value.equals(builder));
        System.out.println("object-number=" + "42".equals(number));

        System.out.println("builder-content="
                + value.contentEquals((StringBuilder) builder));
        System.out.println("builder-string="
                + value.equals(builder.toString()));

        System.out.println("objects-null-null="
                + Objects.equals(missing, null));
        System.out.println("objects-null-value="
                + Objects.equals(missing, value));
        System.out.println("objects-value-value="
                + Objects.equals(value, stringAsObject));

        System.out.println("different-case=" + value.equals("java"));
        System.out.println("equal-new-instance="
                + value.equals(new String("Java")));

        try {
            System.out.println(missing.equals(value));
        } catch (NullPointerException exception) {
            System.out.println("null-receiver=NullPointerException");
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac StringEqualsNullAndTypeDemo.java
java StringEqualsNullAndTypeDemo
~~~

Đầu ra:

~~~text
literal-null=false
value-null=false
safe-constant=false
object-string=true
object-builder=false
object-number=false
builder-content=true
builder-string=true
objects-null-null=true
objects-null-value=false
objects-value-value=true
different-case=false
equal-new-instance=true
null-receiver=NullPointerException
~~~

Những chi tiết cần quan sát:

1. **Ba dòng đầu** đều false vì receiver là String thực, còn đối số null.
2. **object-string=true** chứng minh Object có thể đang tham chiếu một String thật.
3. **object-builder=false** và **object-number=false** chứng minh String.equals() không chuyển kiểu đối số.
4. **builder-content=true** và **builder-string=true** dùng các phương thức có ngữ nghĩa so sánh khác, nên có thể kiểm tra cùng nội dung theo chủ đích.
5. **objects-null-null=true** thể hiện khác biệt giữa Objects.equals() và String.equals(null).
6. **different-case=false** cho thấy equals() phân biệt chữ hoa/thường.
7. **null-receiver=NullPointerException** là kết quả của khối catch: lời gọi trên missing thật sự gây ngoại lệ và được bắt để chương trình tiếp tục.

## 7. Ứng dụng thực tế: kiểm tra dữ liệu đầu vào, trạng thái và giá trị nullable

### Kiểm tra một trạng thái có thể bị thiếu

Ví dụ một phương thức nhận trạng thái từ một API:

~~~java
static boolean isReady(String status) {
    return "READY".equals(status);
}
~~~

Đây là **phương thức minh họa**, cần đặt bên trong một lớp Java khi sử dụng. Nó trả về true chỉ khi status là String bằng chính xác "READY"; với null, "ready", hoặc "" đều trả về false.

Cách viết này tự nhiên khi ứng dụng muốn kiểm tra **một hằng giá trị duy nhất** và thiếu dữ liệu được coi là không khớp.

### Khi hai giá trị vắng mặt cũng được coi là bằng nhau

Giả sử trước và sau của một trường nullable đều không có dữ liệu. Để phát hiện thay đổi giữa hai String nullable:

~~~java
static boolean hasChanged(String oldValue, String newValue) {
    return !java.util.Objects.equals(oldValue, newValue);
}
~~~

Khi oldValue và newValue đều null, kết quả false (không có thay đổi). Khi một bên null và bên kia có giá trị, kết quả true. Khi hai String cùng nội dung, kết quả false.

**Cần lựa chọn chủ ý:** trong một số nghiệp vụ, “không có dữ liệu” không đồng nghĩa “bằng nhau”; khi đó đừng máy móc dùng Objects.equals() mà không làm rõ quy tắc so sánh.

### Khi giá trị đến dưới dạng Object

Trong xử lý Object hoặc dữ liệu nhiều loại, đừng nhầm toString() với kiểm tra kiểu:

~~~java
static boolean isJavaString(Object candidate) {
    return "Java".equals(candidate);
}
~~~

Hàm trả về true nếu candidate thực sự là String có nội dung "Java", không chấp nhận StringBuilder hiển thị "Java" hoặc đối tượng khác chỉ vì toString() cho cùng văn bản.

Nếu nghiệp vụ **thật sự cho phép nhiều kiểu** và muốn chuẩn hóa thành String, phải thiết kế quy tắc chuyển đổi riêng và cân nhắc null, định dạng số, locale cùng các trường hợp không hợp lệ. String.equals(Object) tự nó không có trách nhiệm thực hiện chuẩn hóa đó.

## 8. Những ngộ nhận và lỗi phổ biến

### Nhầm 1: equals(null) chắc chắn ném NullPointerException

Sai. **"Java".equals(null)** trả về false. **nullable.equals("Java")** chỉ ném NullPointerException khi nullable thực sự null. Vị trí của null trong lời gọi rất quan trọng.

### Nhầm 2: Vì tham số khai báo Object, equals() sẽ so sánh với mọi kiểu

Sai. Chữ ký **Object** cho phép truyền tham số vào phương thức; nó không có nghĩa phương thức sẽ chuyển tham số thành String. "42".equals(42) trả về false (đối số int được boxing thành Integer); "42".equals(Integer.valueOf(42)) cũng false.

### Nhầm 3: Hai đối tượng có toString() giống nhau thì equals() phải true

Sai. toString() chủ yếu cung cấp biểu diễn văn bản; hợp đồng equals() của từng lớp mới quyết định tính bằng nhau. String.equals() không gọi toString() của đối số để so sánh.

### Nhầm 4: Dùng == thay equals() cho String

Sai nếu mục tiêu là so sánh **nội dung**. == chỉ so sánh tham chiếu. String literal có thể được intern nên đôi lúc == có vẻ đúng, nhưng không thể dùng kết quả tình cờ đó làm quy tắc nghiệp vụ.

### Nhầm 5: Objects.equals(null, null) phải false vì String.equals(null) false

Sai. Hai API có hợp đồng khác nhau. Objects.equals(null, null) là true; trong trường hợp này **không có đối tượng String nào nhận lời gọi equals()**.

### Nhầm 6: null, chuỗi rỗng và chuỗi toàn khoảng trắng đều như nhau

Sai. null nghĩa là không có tham chiếu; "" là một String hợp lệ độ dài 0; " " là String chứa một dấu cách. Ví dụ "".equals(null) là false, "".equals("") là true, và "".equals(" ") là false. Nếu nghiệp vụ muốn coi các giá trị đó tương đương, cần quy tắc xử lý dữ liệu khác trước khi so sánh.

### Nhầm 7: equals() tự bỏ qua chữ hoa/thường hoặc chuẩn hóa Unicode

Sai. String.equals() so sánh đúng dãy đơn vị mã UTF-16, phân biệt "Java" với "java". Nếu cần bỏ qua một phần khác biệt chữ hoa/thường, có String.equalsIgnoreCase(String), nhưng phương thức này **không phải** phép đối chiếu chuỗi theo tất cả quy tắc locale hay chuẩn hóa Unicode. Chuẩn hóa theo nghiệp vụ phải được quyết định rõ ràng.

### Nhầm 8: Dùng ép kiểu String cho đối tượng bất kỳ sẽ giải quyết vấn đề

Sai. Nếu candidate trỏ tới StringBuilder, lệnh (String) candidate gây ClassCastException. Kiểm tra kiểu bằng instanceof trước khi cast, hoặc chuyển đổi chỉ khi ứng dụng thực sự muốn chấp nhận loại dữ liệu đó.

~~~java
Object candidate = new StringBuilder("Java");

if (candidate instanceof String text) {
    System.out.println("Java".equals(text));
} else {
    System.out.println("Not a String");
}
~~~

Đây là **đoạn minh họa** với pattern matching instanceof được Java 17 hỗ trợ. Kết quả là **Not a String**; không có cast sai kiểu.

## 9. Tóm tắt theo trường hợp và tự kiểm tra

| Cần xác định | Câu trả lời |
|---|---|
| String không-null gọi equals(null)? | false |
| String không-null gọi equals(StringBuilder cùng nội dung)? | false |
| String không-null gọi equals(Object trỏ tới String cùng nội dung)? | true |
| Biến String null gọi equals("abc")? | NullPointerException |
| Cả hai String nullable đều null, dùng Objects.equals()? | true |
| StringBuilder muốn so sánh nội dung với String? | Chủ động chuyển bằng toString() hoặc dùng contentEquals() theo yêu cầu |
| Muốn kiểm tra với hằng "OK" và biến nullable? | "OK".equals(value) |
| Muốn so sánh hai String nullable theo quy tắc null/null là bằng nhau? | Objects.equals(a, b) |

**Tự trả lời:**

1. Vì sao "123".equals(123) là false dù hai phía có biểu diễn văn bản giống nhau?
2. Vì sao Object x = "Java"; thì "Java".equals(x) là true, nhưng Object y = new StringBuilder("Java"); lại false?
3. Sự khác nhau giữa "Java".equals(nullable) và nullable.equals("Java") là gì khi nullable null?
4. Tại sao Objects.equals(null, null) trả về true trong khi "Java".equals(null) trả về false?
5. Nếu một trường trạng thái có thể null, bạn sẽ chọn "READY".equals(status) hay status.equals("READY"), và vì sao?

**Đáp án cốt lõi:** (1) String.equals() yêu cầu đối số thực tế là String, số nguyên không phải String. (2) Kiểu đối tượng runtime quyết định, không phải kiểu biến Object hay chữ hiển thị. (3) Receiver non-null trả false, receiver null gây ngoại lệ. (4) Objects.equals() xử lý hai tham chiếu null theo hợp đồng riêng. (5) Đặt literal bên trái để kiểm tra an toàn.

**Ghi nhớ:** **Đối số null → false; đối số không phải String → false; receiver null → NullPointerException.** Dùng String literal ở bên trái khi so sánh với hằng, hoặc dùng Objects.equals() cho hai String có thể null nếu quy tắc **null bằng null** là phù hợp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-comparison-003">Objects.equals cho hai tham chiếu có thể null</a>

<details>
<summary>Xem chi tiết</summary>

# Objects.equals cho hai tham chiếu có thể null trong Java

## 1. Vấn đề: so sánh khi một hoặc cả hai tham chiếu có thể là null

Trong Java, **String** là kiểu tham chiếu. Hai biến có thể chứa hai đối tượng String, cùng tham chiếu đến một đối tượng, hoặc chứa **null** (không tham chiếu đến đối tượng nào). Khi dùng **a.equals(b)**, Java phải gọi phương thức trên đối tượng **a**; nếu **a == null**, lệnh này ném **NullPointerException**.

~~~java
String first = null;
String second = "Java";

// Đoạn minh họa lỗi nếu chạy:
boolean equal = first.equals(second); // NullPointerException
~~~

Lớp **java.util.Objects** cung cấp phương thức tĩnh:

~~~java
boolean result = Objects.equals(first, second);
~~~

Phương thức này **so sánh hai tham chiếu an toàn khi có null**. Nó trả về **true** nếu cả hai là null hoặc nếu giá trị thứ nhất khác null và phương thức **equals** của nó đánh giá hai giá trị là bằng nhau. Nếu chỉ một tham chiếu là null, kết quả **false**.

**Objects.equals** hữu ích cho String, các đối tượng giá trị, thuộc tính của đối tượng và các phương thức nhận tham số tùy chọn có thể null. Cần **import java.util.Objects**; đây là API có từ Java 7 và hoàn toàn dùng được trong Java 17+.

## 2. Hợp đồng chính xác của Objects.equals

Chữ ký:

~~~java
public static boolean equals(Object a, Object b)
~~~

Hành vi về cơ bản tương đương:

~~~java
public static boolean nullableEquals(Object a, Object b) {
    return (a == b) || (a != null && a.equals(b));
}
~~~

Đoạn trên là **phương thức minh họa**, đặt trong một lớp nếu muốn biên dịch. Thư viện Java sẽ tự chọn phương thức **equals** phù hợp với kiểu thực tế của đối tượng **a**, nhờ cơ chế gọi phương thức động.

| Tham chiếu a | Tham chiếu b | Kết quả | Giải thích |
| --- | --- | --- | --- |
| **null** | **null** | **true** | Cùng là null; nhánh **a == b** đúng |
| **null** | Đối tượng | **false** | **a != null** sai, không gọi phương thức |
| Đối tượng | **null** | Thường **false** với equals đúng hợp đồng | Gọi **a.equals(null)**; hợp đồng equals yêu cầu trả về false |
| Cùng một đối tượng | Cùng tham chiếu | **true** | **a == b** đúng |
| Hai đối tượng khác tham chiếu | Giá trị tương đương theo **a.equals(b)** | **true** | Chẳng hạn hai String có cùng nội dung |
| Hai đối tượng khác tham chiếu | Giá trị không tương đương | **false** | Kết quả dựa trên **a.equals(b)** |

**Lưu ý về điều kiện:** một triển khai **equals** bị viết sai có thể vi phạm hợp đồng của Java, ném ngoại lệ hoặc trả về kết quả không nhất quán. **Objects.equals** chỉ xử lý trường hợp null ở bên ngoài và chuyển tiếp tới **equals** thông thường; nó không sửa các lỗi nghiệp vụ hay tự kiểm chứng sự đối xứng của một phép so sánh.

### Vì sao hai null lại được xem là bằng nhau?

Đây là quy tắc đã được định nghĩa cho phép so sánh giá trị trong **Objects.equals**: hai tham chiếu đều không có đối tượng được xem là tương đương. Quy tắc này thường phù hợp khi kiểm tra “hai trạng thái có giống nhau không”.

Tuy nhiên, **hai trường dữ liệu đều thiếu** không luôn có nghĩa “hai thực thể giống nhau” trong nghiệp vụ. Khi so mã định danh bắt buộc khác null, hãy kiểm tra tính hợp lệ của mã định danh trước khi gọi **Objects.equals**, hoặc yêu cầu cả hai phải khác null:

~~~java
boolean sameKnownId = idA != null && idB != null
        && idA.equals(idB);
~~~

Đây là **đoạn minh họa** trong một phương thức; nó trả về false nếu mã định danh của một trong hai phía không tồn tại. Không nên dùng **Objects.equals(null, null) == true** như bằng chứng rằng hai thực thể không có ID là cùng một thực thể.

## 3. Ví dụ đầy đủ: hai String có thể null

Lưu mã vào **NullableStringComparisonDemo.java**:

~~~java
import java.util.Objects;

public class NullableStringComparisonDemo {
    public static void main(String[] args) {
        String missingA = null;
        String missingB = null;
        String literal = "Java";
        String newObject = new String("Java");
        String other = "Kotlin";

        System.out.println(Objects.equals(missingA, missingB));
        System.out.println(Objects.equals(missingA, literal));
        System.out.println(Objects.equals(literal, missingA));
        System.out.println(Objects.equals(literal, newObject));
        System.out.println(literal == newObject);
        System.out.println(Objects.equals(literal, other));
        System.out.println("Java".equals(missingA));
        System.out.println(Objects.equals("", missingA));
        System.out.println(Objects.equals("", ""));
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 NullableStringComparisonDemo.java
java NullableStringComparisonDemo
~~~

Stdout:

~~~text
true
false
false
true
false
false
false
false
true
~~~

Giải thích theo thứ tự:

1. Cả hai giá trị **missingA** và **missingB** đều là **null**, kết quả **true**.
2. So **null** với **"Java"** hoặc ngược lại đều cho **false**.
3. **literal** và **newObject** chứa nội dung **"Java"** giống nhau nên **Objects.equals** trả về **true**, dù là hai đối tượng khác nhau.
4. **literal == newObject** là **false** vì **==** kiểm tra tham chiếu.
5. Hai nội dung **"Java"** và **"Kotlin"** không giống nhau.
6. **"Java".equals(missingA)** không gây ngoại lệ, vì đối tượng nhận phương thức là literal chắc chắn khác null; kết quả **false**.
7. **""** là String rỗng thực sự, khác **null**, nhưng hai chuỗi rỗng có nội dung bằng nhau.

Đối với hai String, kết quả **Objects.equals(a, b)** vẫn phân biệt chữ hoa/thường. Ví dụ **Objects.equals("java", "Java")** là **false**.

## 4. Phân biệt Objects.equals, equals và ==

| Biểu thức | Mục đích | Khi a null | Kết luận |
| --- | --- | --- | --- |
| **a == b** | So tham chiếu (với kiểu tham chiếu) | An toàn | Cho biết hai biến có trỏ cùng đối tượng hoặc cùng null hay không |
| **a.equals(b)** | Gọi phép so sánh do lớp của a định nghĩa | Ném **NullPointerException** | Dùng khi biết a không null |
| **Objects.equals(a, b)** | So giá trị có xử lý null | An toàn | Phù hợp khi cả hai tham chiếu đều có thể null |
| **"value".equals(a)** | So nội dung với một giá trị cố định | An toàn | Gọn và rõ khi một vế là hằng String khác null |
| **Objects.requireNonNull(a)** | Xác thực a bắt buộc khác null | Ném **NullPointerException** | Không phải phép so sánh; dùng để thực thi điều kiện đầu vào |

Phép **==** đặc biệt dễ gây nhầm với String:

~~~java
String a = "hello";
String b = new String("hello");

System.out.println(a == b);               // false
System.out.println(a.equals(b));           // true
System.out.println(Objects.equals(a, b));  // true
~~~

Đây là các câu lệnh minh họa đặt trong phương thức có **import java.util.Objects**. Việc một số literal String được JVM tái sử dụng trong **string pool** có thể khiến **==** đôi khi trả về true với hai literal, nhưng không nên dựa vào hiện tượng đó để kiểm tra văn bản. Chọn **equals** hoặc **Objects.equals** để diễn đạt rõ ý định so nội dung.

### Khi nào ưu tiên từng cách?

- Nếu **a** đã được bảo đảm khác null theo hợp đồng, dùng **a.equals(b)** là tự nhiên.
- Nếu **cả hai biến có thể null**, dùng **Objects.equals(a, b)**.
- Nếu so với một hằng String chắc chắn khác null, **"ACTIVE".equals(status)** là lựa chọn ngắn gọn.
- Nếu chỉ muốn kiểm tra chính xác **cùng đối tượng**, dùng **==**.
- Nếu nghiệp vụ quy định không được chấp nhận null, kiểm tra và báo lỗi trước; đừng để **Objects.equals(null, null)** vô tình được coi là trường hợp hợp lệ.

## 5. Những chi tiết về null cần làm rõ

**null khác chuỗi rỗng và chuỗi chỉ chứa khoảng trắng.**

~~~java
String absent = null;
String empty = "";
String spaces = "   ";

System.out.println(Objects.equals(absent, empty)); // false
System.out.println(Objects.equals(empty, spaces)); // false
System.out.println(empty.isEmpty());               // true
System.out.println(spaces.isBlank());              // true
~~~

Các câu lệnh minh họa trong phương thức có import **Objects**. Phương thức **isEmpty()** chỉ kiểm tra độ dài bằng 0; **isBlank()** kiểm tra toàn bộ chuỗi là whitespace theo quy tắc của Java. Việc xem **null**, chuỗi rỗng hoặc chuỗi trắng như nhau phải là **quyết định nghiệp vụ được viết tường minh**, ví dụ bước chuẩn hóa đầu vào trước khi so sánh.

**Không có phép “coi null là chuỗi "null"”** trong **Objects.equals**. Ví dụ:

~~~java
System.out.println(Objects.equals(null, "null")); // false
~~~

Phương thức **String.valueOf((Object) null)** lại tạo ra String có nội dung bốn chữ cái **"null"**; không nên dùng cách này để thay thế phép so sánh null-safe.

**Thứ tự tham số trong một phép equals chuẩn:** hợp đồng **Object.equals** yêu cầu tính đối xứng: **a.equals(b)** và **b.equals(a)** phải tương đương về giá trị logic khi cả hai khác null. Nếu một lớp triển khai equals sai, **Objects.equals(a, b)** và **Objects.equals(b, a)** có thể khác nhau. Phương thức tĩnh không tự thử cả hai hướng để sửa lỗi.

## 6. Objects.equals với các loại đối tượng khác

**Objects.equals** có tên tổng quát vì nhận hai tham số kiểu **Object**. Kết quả khi cả hai khác null phụ thuộc vào **equals** của loại đối tượng đứng bên trái.

### String và các lớp giá trị phổ biến

**String**, các lớp wrapper như **Integer**, **Long**, **Boolean**, cùng nhiều lớp giá trị Java khác có định nghĩa **equals** dựa trên dữ liệu có ý nghĩa. Tuy nhiên, một số loại còn yêu cầu **đúng kiểu**:

~~~java
Object number = Integer.valueOf(10);
Object longNumber = Long.valueOf(10L);

System.out.println(Objects.equals(number, longNumber)); // false
~~~

Giá trị số đều biểu diễn “10”, nhưng hai wrapper khác lớp nên **Integer.equals(Long)** trả về false. **Objects.equals** không tự chuyển đổi kiểu số, không bỏ qua khác biệt locale và cũng không tự chuyển chữ hoa/thường.

### Đối tượng không ghi đè equals

Nếu một lớp chỉ kế thừa **Object.equals** mặc định, phép so sánh về cơ bản dựa trên **cùng đối tượng**. Hai đối tượng mới có cùng dữ liệu trường chưa chắc bằng nhau:

~~~java
class Box {
    int value;
    Box(int value) {
        this.value = value;
    }
}

// Các câu lệnh sau ở bên trong một phương thức:
Box first = new Box(7);
Box second = new Box(7);
System.out.println(Objects.equals(first, second)); // false
~~~

Nếu muốn coi hai **Box** có cùng **value** là tương đương, lớp phải định nghĩa **equals** (và **hashCode**) phù hợp. **Objects.equals** không tự dùng reflection để so tất cả thuộc tính.

### Array không được so nội dung sâu bằng Objects.equals

Mảng Java không ghi đè **Object.equals** để so các phần tử. Vì vậy:

~~~java
String[] first = {"a", "b"};
String[] second = {"a", "b"};

System.out.println(Objects.equals(first, second)); // false
System.out.println(java.util.Arrays.equals(first, second)); // true
System.out.println(Objects.deepEquals(first, second)); // true
~~~

Đây là đoạn minh họa; cần import **java.util.Objects** nếu dùng tên ngắn. Dùng **Arrays.equals** cho mảng một chiều và **Arrays.deepEquals** hoặc **Objects.deepEquals** trong trường hợp cần so các mảng lồng nhau phù hợp. **Objects.deepEquals** cũng xử lý null, nhưng cần tránh lạm dụng với đồ thị mảng có tham chiếu vòng.

Để minh họa đầy đủ, lưu thành **ArrayEqualityDemo.java**:

~~~java
import java.util.Arrays;
import java.util.Objects;

public class ArrayEqualityDemo {
    public static void main(String[] args) {
        String[] one = {"a", "b"};
        String[] two = {"a", "b"};

        System.out.println(Objects.equals(one, two));
        System.out.println(Arrays.equals(one, two));
        System.out.println(Objects.deepEquals(one, two));

        int[][] left = {{1, 2}, {3}};
        int[][] right = {{1, 2}, {3}};

        System.out.println(Arrays.equals(left, right));
        System.out.println(Arrays.deepEquals(left, right));
    }
}
~~~

~~~bash
javac -encoding UTF-8 ArrayEqualityDemo.java
java ArrayEqualityDemo
~~~

~~~text
false
true
true
false
true
~~~

**Vì sao?** Mảng **one** và **two** là hai đối tượng khác nhau, nhưng phần tử tương ứng có cùng nội dung. Với **int[][]**, phép **Arrays.equals** ở tầng ngoài lại so các mảng con theo tham chiếu; **Arrays.deepEquals** mới đệ quy so nội dung các mảng con.

## 7. Viết equals null-safe trong lớp của chính bạn

Trong lớp giá trị do ứng dụng định nghĩa, có thể dùng **Objects.equals** để so các trường tham chiếu có thể null. Cần ghi đè **hashCode** tương thích nếu ghi đè **equals**.

Ví dụ đầy đủ, lưu thành **CustomerEqualityDemo.java**:

~~~java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

final class Customer {
    private final String email;
    private final int level;

    Customer(String email, int level) {
        this.email = email;
        this.level = level;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Customer that)) {
            return false;
        }

        return level == that.level
                && Objects.equals(email, that.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email, level);
    }
}

public class CustomerEqualityDemo {
    public static void main(String[] args) {
        Customer first = new Customer(null, 2);
        Customer second = new Customer(null, 2);
        Customer other = new Customer(null, 3);

        System.out.println(first == second);
        System.out.println(Objects.equals(first, second));
        System.out.println(Objects.equals(first, other));
        System.out.println(Objects.equals(first, null));

        Set<Customer> customers = new HashSet<>();
        customers.add(first);
        System.out.println(customers.contains(second));
    }
}
~~~

~~~bash
javac -encoding UTF-8 CustomerEqualityDemo.java
java CustomerEqualityDemo
~~~

~~~text
false
true
false
false
true
~~~

Đây là ví dụ Java 17+ dùng cú pháp **instanceof Customer that**, được Java 16 chuẩn hóa. Hai đối tượng **Customer** có cùng **level** và cùng **email == null** được coi là bằng nhau theo chính sách của lớp. **HashSet.contains(second)** hoạt động đúng vì **equals** và **hashCode** tương thích.

**Cảnh báo về thiết kế:** trong ví dụ, các trường là **final** để dễ bảo đảm bất biến. Nếu các trường tham gia **equals/hashCode** bị thay đổi sau khi đối tượng đã vào **HashSet** hoặc làm key của **HashMap**, việc tìm kiếm có thể thất bại. Với mã định danh bắt buộc có giá trị, hãy kiểm tra tính hợp lệ ở constructor thay vì cho phép null một cách vô điều kiện.

**Objects.hash(email, level)** tính hash tổng hợp của các trường. Không nhầm với **Objects.hashCode(value)**, tiện ích trả về 0 nếu value null hoặc **value.hashCode()** nếu khác null. Cả hai hỗ trợ việc xây dựng hashCode; lựa chọn phải tương thích với những trường được **equals** sử dụng.

### Record trong Java 17

Một **record** tự tạo **equals/hashCode** dựa trên các thành phần của nó:

~~~java
record Profile(String displayName, int age) {}

Profile a = new Profile(null, 20);
Profile b = new Profile(null, 20);
System.out.println(Objects.equals(a, b)); // true
~~~

Đây là đoạn minh họa trong ngữ cảnh phù hợp với Java 17 và có import **Objects**. **Record** có phép so sánh thành phần theo hợp đồng của Java; tuy vậy, với thành phần kiểu array, không mặc nhiên hiểu rằng record sẽ so sâu nội dung của mảng. Vẫn cần thiết kế rõ semantics của lớp.

## 8. Trường hợp ứng dụng thực tế

### 8.1. Kiểm tra giá trị cũ/mới để quyết định có cập nhật

Khi trạng thái cũ và mới đều có thể null, muốn phát hiện thay đổi:

~~~java
if (!Objects.equals(oldValue, newValue)) {
    System.out.println("Value changed");
}
~~~

Dùng **!Objects.equals** giúp xử lý các chuyển trạng thái **null → giá trị**, **giá trị → null**, hoặc **giá trị A → giá trị B** một cách nhất quán. Hai giá trị đều null được xem là không thay đổi.

### 8.2. So khớp tham số bộ lọc có thể null

~~~java
boolean matches = Objects.equals(requestedCategory, itemCategory);
~~~

Biểu thức này có nghĩa **cả hai cùng null cũng là match**. Nếu null trong bộ lọc nghĩa là **“không lọc”**, cần một điều kiện khác:

~~~java
boolean matches = requestedCategory == null
        || Objects.equals(requestedCategory, itemCategory);
~~~

Hai đoạn trên là minh họa trong một phương thức. Chúng thể hiện hai chính sách nghiệp vụ khác nhau; nên chọn dựa trên định nghĩa của API.

### 8.3. So với một trạng thái cố định

~~~java
boolean active = "ACTIVE".equals(status);
~~~

Khi **status** có thể null, gọi **equals** trên literal vẫn an toàn và ngắn. **Objects.equals(status, "ACTIVE")** cũng đúng, nhưng không mang thêm lợi ích đáng kể trong trường hợp một vế đã chắc chắn khác null.

### 8.4. Hai tham chiếu nullable cần so không phân biệt hoa/thường

**Objects.equals** không tự thực hiện so sánh bỏ qua chữ hoa/thường. Nếu ứng dụng xác định rõ chính sách **equalsIgnoreCase** cho hai String có thể null:

~~~java
static boolean equalsIgnoreCaseNullable(String a, String b) {
    return a == b || (a != null && a.equalsIgnoreCase(b));
}
~~~

Đây là phương thức minh họa: hai null trả về true, một null trả về false, còn hai String khác null được so không phân biệt hoa/thường theo **String.equalsIgnoreCase**. Nó không tự chuẩn hóa Unicode, không giải quyết đầy đủ so sánh ngôn ngữ tự nhiên theo locale. Nếu yêu cầu là sắp xếp/tìm kiếm tiếng Việt, cần quy tắc normalization, locale và collation tách biệt.

## 9. Khi nào Objects.equals chưa đủ?

- **Yêu cầu bắt buộc có dữ liệu:** nếu cả hai null vẫn là trạng thái không hợp lệ, dùng điều kiện kiểm tra tính tồn tại hoặc **Objects.requireNonNull** trước khi so.
- **Mảng:** dùng phương thức của **Arrays** phù hợp với kiểu mảng và nhu cầu so từng tầng.
- **So sánh không phân biệt chữ hoa/thường:** dùng **equalsIgnoreCase** hoặc quy tắc so sánh ngôn ngữ phù hợp; **Objects.equals** phân biệt nội dung chính xác theo **equals**.
- **Unicode normalization:** hai String có hình thức hiển thị giống nhau nhưng khác NFC/NFD vẫn có thể không **equals**. Chuẩn hóa có chủ đích trước khi so nếu nghiệp vụ cần.
- **So sánh dữ liệu số khác kiểu:** **Integer(10)** và **Long(10L)** không tự quy về cùng kiểu.
- **Đối tượng nghiệp vụ không ghi đè equals:** **Objects.equals** không tạo ra bình đẳng theo thuộc tính; lớp phải tự định nghĩa và tuân thủ hợp đồng equals/hashCode.
- **Sắp xếp:** **Objects.equals** chỉ trả về boolean, không cho quan hệ thứ tự. Khi cần thứ tự và null, dùng **Comparator.nullsFirst(...)** hoặc **Comparator.nullsLast(...)** với comparator phù hợp.
- **Bảo vệ tính bí mật của giá trị:** **Objects.equals** không bảo đảm so sánh thời gian cố định. Với byte bí mật, cần API và thiết kế chuyên dụng phù hợp ngữ cảnh bảo mật.

## 10. Những lỗi thường gặp

| Lỗi | Hậu quả | Cách xử lý |
| --- | --- | --- |
| **nullable.equals(value)** | **NullPointerException** nếu nullable null | **Objects.equals(nullable, value)** hoặc gọi trên hằng khác null |
| Dùng **==** để so nội dung String | So tham chiếu, kết quả phụ thuộc việc hai biến có cùng đối tượng | Dùng **equals** hoặc **Objects.equals** |
| Mặc định hai null không bao giờ bằng nhau | Hiểu sai kết quả API | Nhớ **Objects.equals(null, null)** là true; bổ sung điều kiện nghiệp vụ khi cần |
| Dùng **Objects.equals** trên hai mảng mới | Có thể false dù các phần tử bằng nhau | Dùng **Arrays.equals/deepEquals** |
| Cho rằng Objects.equals tự so trường của mọi object | Hai object khác nhau thường false nếu dùng **Object.equals** mặc định | Định nghĩa equals/hashCode trong lớp giá trị |
| Ghi đè **equals** nhưng bỏ quên **hashCode** | Vi phạm hợp đồng, gây lỗi trong HashMap/HashSet | Ghi đè đồng thời, dựa trên các trường so sánh |
| Xem **null** giống **""** | Mất khác biệt giữa vắng giá trị và chuỗi rỗng | Xác định quy tắc normalize đầu vào riêng |
| Giả định so sánh bỏ qua chữ hoa/dấu | So sánh có thể false | Dùng logic chuẩn hóa và so sánh riêng theo yêu cầu |
| Đặt đối tượng có trường equals/hashCode biến đổi vào HashSet | Khó tìm lại object sau khi trường thay đổi | Thiết kế key bất biến hoặc duy trì hợp đồng nhất quán |

## 11. Tự kiểm tra kiến thức

1. **Objects.equals(null, null)** trả về gì? — **true**, vì hai tham chiếu cùng null.
2. **Objects.equals(null, "x")** có ném NullPointerException không? — **Không**, nó trả về **false**.
3. Vì sao **new String("x") == "x"** khác với **Objects.equals(new String("x"), "x")**? — Biểu thức đầu kiểm tra hai tham chiếu (false), biểu thức sau dùng String.equals so nội dung (true).
4. **Objects.equals(new int[]{1, 2}, new int[]{1, 2})** có trả về true không? — **Không**, hai mảng khác tham chiếu; dùng **Arrays.equals** khi muốn so phần tử.
5. Khi cả hai ID đều null, **Objects.equals(idA, idB)** trả về true. Có nên coi đó là cùng một tài khoản? — **Không mặc nhiên**; phải áp dụng ràng buộc mã định danh hợp lệ trong nghiệp vụ.
6. Khi ghi đè **equals** trong lớp có trường String nullable, nên so trường đó bằng gì? — **Objects.equals(this.field, other.field)**, đồng thời bảo đảm **hashCode** phù hợp.
7. Dùng cách nào nếu chỉ muốn biết trạng thái từ null sang một giá trị mới đã thay đổi? — **!Objects.equals(oldValue, newValue)**.
8. Hai String cùng nội dung nhưng khác mã hóa dấu tiếng Việt có luôn equals không? — **Không**; nếu cần so canonical equivalence, phải chuẩn hóa Unicode nhất quán trước.

## 12. Ghi nhớ

**java.util.Objects.equals(a, b)** là cách ngắn gọn và an toàn để so sánh **hai tham chiếu có thể null**. Quy tắc cốt lõi là **(a == b) || (a != null && a.equals(b))**.

Với String, nó so **nội dung**, thay vì dựa vào tính đồng nhất của đối tượng. Nó vẫn tuân theo **equals** do lớp triển khai: không tự chuẩn hóa văn bản, không tự bỏ qua chữ hoa/thường, không tự so sâu mọi mảng, và không tự xác thực rằng **null** là hợp lệ về mặt nghiệp vụ.

Khi dùng trong lớp của mình, hãy giữ đúng hợp đồng **equals/hashCode**, xác định ý nghĩa của null, và chọn phương thức so sánh tương ứng với loại dữ liệu thực tế.

**Tài liệu API:** Java SE 17 — **java.util.Objects.equals(Object, Object)**, **java.lang.Object.equals(Object)**, **java.lang.String.equals(Object)**, **java.util.Arrays.equals(...)** và **Arrays.deepEquals(...)**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-comparison-004">equalsIgnoreCase và giới hạn không phụ thuộc locale</a>

<details>
<summary>Xem chi tiết</summary>

# `String.equalsIgnoreCase()` và giới hạn của so sánh không phụ thuộc `Locale`

## 1. Mục tiêu

`String.equalsIgnoreCase(String anotherString)` so sánh **nội dung hai chuỗi** mà bỏ qua một số khác biệt chữ hoa/chữ thường theo quy tắc Unicode được Java sử dụng, **không dựa vào ngôn ngữ/vùng địa lý (`Locale`) mặc định của JVM**.

Điều đó thuận tiện cho một số giá trị quy ước đơn giản, nhưng không có nghĩa đây là thuật toán so sánh văn bản chính xác cho mọi ngôn ngữ, mọi trường hợp Unicode, hay mọi quy tắc nghiệp vụ.

Sau bài này, bạn sẽ:
- Phân biệt `equals`, `equalsIgnoreCase` và so sánh có ý thức về `Locale`.
- Hiểu cách xử lý các trường hợp đặc biệt như chữ `I/i/İ/ı` trong tiếng Thổ Nhĩ Kỳ và chữ `ß` trong tiếng Đức.
- Biết vì sao `equalsIgnoreCase` không thay thế Unicode normalization hoặc full case folding.
- Chọn đúng kỹ thuật khi so sánh mã định danh, từ ngữ nhập bởi người dùng, và các dạng dữ liệu yêu cầu quy tắc cố định.

## 2. API và hợp đồng cơ bản

Cú pháp:

```java
boolean equal = first.equalsIgnoreCase(second);
```

Ví dụ:

```java
String left = "Java Core";

System.out.println(left.equals("java core"));           // false
System.out.println(left.equalsIgnoreCase("java core")); // true
System.out.println(left.equalsIgnoreCase("JAVA CORE")); // true
System.out.println(left.equalsIgnoreCase("Java"));      // false
```

`equals` chỉ trả về `true` khi hai chuỗi có cùng nội dung theo chuỗi giá trị UTF-16. `equalsIgnoreCase` bỏ qua sự khác biệt hoa/thường *theo quy tắc của phương thức*; nó **không bỏ qua** khoảng trắng, dấu câu, dấu tiếng Việt, khác biệt độ dài hoặc những khác biệt Unicode khác một cách tổng quát.

| Trường hợp | `equalsIgnoreCase` | Giải thích |
| --- | --- | --- |
| `"Java"` và `"jAVA"` | `true` | Các chữ cái khớp khi bỏ qua hoa/thường. |
| `"Java"` và `"Java "` | `false` | Chuỗi sau có thêm dấu cách. |
| `"cat"` và `"cats"` | `false` | Khác độ dài. |
| `"abc"` và `"abd"` | `false` | Khác ký tự, không chỉ khác hoa/thường. |
| `"a"` và `null` | `false` khi gọi `"a".equalsIgnoreCase(null)` | Đối số `null` được xử lý bằng kết quả `false`. |

**Độ dài là một ràng buộc quan trọng:** hai chuỗi chỉ có thể bằng nhau theo `equalsIgnoreCase` khi chúng có cùng số **UTF-16 code unit** (`length()`). API này không phải phép biến đổi tự động xóa dấu, chuẩn hóa ký tự, hoặc mở rộng một ký tự thành nhiều ký tự.

### Xử lý `null` đúng cách

```java
String input = null;

System.out.println("yes".equalsIgnoreCase(input)); // false

// input.equalsIgnoreCase("yes"); // NullPointerException.
```

Nên gọi trên hằng chuỗi không null nếu điều đó phù hợp bài toán. Nếu cả hai biến đều có thể null và bạn muốn `null` bằng `null` trong hợp đồng riêng, hãy viết rõ:

```java
static boolean sameIgnoringCase(String a, String b) {
    return a == null ? b == null : a.equalsIgnoreCase(b);
}
```

`Objects.equals(a, b)` không thay thế được phương thức này nếu yêu cầu **không phân biệt hoa/thường**: `Objects.equals` gọi `equals` và vẫn phân biệt hoa/thường.

## 3. “Không phụ thuộc locale” nghĩa là gì?

`Locale` biểu diễn ngữ cảnh về ngôn ngữ/vùng, ví dụ `Locale.forLanguageTag("tr")` cho tiếng Thổ Nhĩ Kỳ hoặc `Locale.forLanguageTag("en")` cho tiếng Anh. Cách chuyển hoa/thường trong ngôn ngữ tự nhiên **có thể phụ thuộc locale**.

Ngược lại, `equalsIgnoreCase`:
- Không nhận tham số `Locale`.
- Không âm thầm dựa vào `Locale.getDefault()` để quyết định hai chuỗi bằng nhau.
- Dùng các phép so sánh không phân biệt hoa/thường dựa trên thông tin Unicode và quy tắc trong Java.

Vì vậy, **ổn định theo `Locale` không đồng nghĩa đúng theo từng ngôn ngữ**. Cùng một phép so sánh có thể trả cùng kết quả bất kể locale mặc định, nhưng kết quả đó vẫn không khớp cách người nói một ngôn ngữ cụ thể phân biệt từ ngữ.

### Trường hợp tiếng Thổ Nhĩ Kỳ: bốn dạng `I`

Trong tiếng Thổ Nhĩ Kỳ có hai cặp hoa/thường riêng:
- `I` ↔ `ı` (chữ i **không chấm**).
- `İ` ↔ `i` (chữ i **có chấm**).

Tuy nhiên, `equalsIgnoreCase` của Java có thể coi các dạng này bằng nhau theo phép ánh xạ không phụ thuộc locale:

```java
System.out.println("I".equalsIgnoreCase("i"));      // true
System.out.println("I".equalsIgnoreCase("\u0131")); // true: ı
System.out.println("\u0130".equalsIgnoreCase("i")); // true: İ
```

Đây là ví dụ quan trọng cho thấy **so sánh không phụ thuộc locale có thể gom những chữ cái mà một ngôn ngữ xem là khác nhau**.

Trong khi đó, phép chuyển chữ thường có truyền `Locale` có thể cho kết quả khác:

```java
import java.util.Locale;

Locale turkish = Locale.forLanguageTag("tr");

System.out.println("I".toLowerCase(turkish).equals("i")); // false
System.out.println("I".toLowerCase(Locale.ROOT).equals("i")); // true
```

`Locale.ROOT` là locale trung lập về ngôn ngữ, thích hợp cho nhiều cách xử lý dữ liệu theo quy ước cố định. Nhưng dùng `Locale.ROOT` để chuyển chữ thường **không làm cho việc so sánh trở thành so sánh ngôn ngữ tự nhiên của tất cả locale**, và cũng không đồng nghĩa với full Unicode case folding.

## 4. Bài toán Unicode: một chữ có thể biến thành nhiều chữ

### 4.1. Chữ `ß` của tiếng Đức

`ß` (*sharp s*, U+00DF) có một số cách xử lý biến đổi chữ hoa/thường khác các chữ Latin cơ bản. Ví dụ:

```java
import java.util.Locale;

String a = "Stra\u00DFe"; // Straße
String b = "STRASSE";

System.out.println(a.equalsIgnoreCase(b));                // false
System.out.println(a.toUpperCase(Locale.ROOT).equals(b)); // true
```

Tại sao dòng thứ nhất `false`? `"Straße"` có **6** code unit UTF-16, còn `"STRASSE"` có **7**. `equalsIgnoreCase` yêu cầu độ dài phù hợp và **không thực hiện ánh xạ một ký tự thành hai chữ "SS" để làm bằng nhau**.

Dòng thứ hai `true` vì phép chuyển **toàn chuỗi** bằng `toUpperCase(Locale.ROOT)` có thể thực hiện ánh xạ **một thành nhiều** (ở đây `ß` → `SS`). Điều đó cho thấy kết quả của `equalsIgnoreCase` và của cách “chuyển cả hai chuỗi sang hoa/thường rồi so sánh” không nhất thiết giống nhau.

Không nên suy ra rằng `toUpperCase(Locale.ROOT)` là giải pháp tổng quát cho mọi chuỗi Unicode: biến đổi cả chuỗi có thể phụ thuộc ngữ cảnh, độ dài có thể thay đổi, và một hệ thống cần so sánh độc lập với ngôn ngữ thường cần một định nghĩa **case folding/normalization** cụ thể.

### 4.2. Greek sigma và các ánh xạ khác

`Σ` (sigma hoa), `σ` (sigma thường), `ς` (sigma cuối từ) là ví dụ các dạng chữ liên quan về hoa/thường:

```java
System.out.println("\u03A3".equalsIgnoreCase("\u03C3")); // true
System.out.println("\u03A3".equalsIgnoreCase("\u03C2")); // true
```

Cách coi các ký tự bằng nhau phục vụ phép so sánh Unicode không phụ thuộc ngôn ngữ; nó không tự biết vị trí từ hoặc quy tắc chính tả của từng ngôn ngữ.

Với chuỗi Unicode tổng quát, đừng định nghĩa thuật toán của `equalsIgnoreCase` đơn giản là “gọi `toLowerCase()` trên cả hai chuỗi”. Chúng là **các API khác nhau**, với các quy tắc và khả năng biến đổi độ dài khác nhau.

## 5. Unicode normalization: chữ nhìn giống nhau vẫn có thể khác

Unicode có thể biểu diễn cùng một dạng chữ bằng các chuỗi code point khác nhau. Ví dụ:
- `"\u00E9"` là ký tự `é` được mã hóa bởi **một** code point U+00E9.
- `"e\u0301"` là chữ `e` U+0065 theo sau bởi dấu sắc kết hợp U+0301, gồm **hai** code point.

Hai chuỗi có thể nhìn giống nhau nhưng dữ liệu thực khác nhau:

```java
String composed = "\u00E9";
String decomposed = "e\u0301";

System.out.println(composed.equalsIgnoreCase(decomposed)); // false
System.out.println(composed.length());                     // 1
System.out.println(decomposed.length());                   // 2
```

`equalsIgnoreCase` **không tự chuẩn hóa Unicode**. Nếu hợp đồng nghiệp vụ cho phép coi hai dạng *canonically equivalent* là cùng văn bản, chuẩn hóa trước khi so sánh:

```java
import java.text.Normalizer;

String leftNfc = Normalizer.normalize(composed, Normalizer.Form.NFC);
String rightNfc = Normalizer.normalize(decomposed, Normalizer.Form.NFC);

System.out.println(leftNfc.equalsIgnoreCase(rightNfc)); // true
```

`Normalizer.Form.NFC` giúp chuẩn hóa các biểu diễn tương đương về dạng dựng sẵn khi có thể. Nhưng **NFC chỉ giải quyết canonical equivalence**, không phải mọi sự tương đương về hoa/thường, ngôn ngữ, dấu câu hay ký tự nhìn tương tự nhau. Cần quyết định rõ chuẩn hóa đầu vào ở mức nào và có được phép biến đổi dữ liệu gốc không.

## 6. Chương trình Java 17+ chạy độc lập

Lưu thành file `EqualsIgnoreCaseDemo.java`:

```java
import java.text.Normalizer;
import java.util.Locale;

public class EqualsIgnoreCaseDemo {
    public static void main(String[] args) {
        System.out.println("basic = "
                + "Java".equalsIgnoreCase("jAVA"));
        System.out.println("space = "
                + "Java".equalsIgnoreCase("Java "));
        System.out.println("null argument = "
                + "Java".equalsIgnoreCase(null));

        // Hai cặp chữ I/i khác nhau trong tiếng Thổ Nhĩ Kỳ.
        System.out.println("I vs i = "
                + "I".equalsIgnoreCase("i"));
        System.out.println("I vs dotless = "
                + "I".equalsIgnoreCase("\u0131"));
        System.out.println("dotted vs i = "
                + "\u0130".equalsIgnoreCase("i"));

        Locale tr = Locale.forLanguageTag("tr");
        System.out.println("Turkish lowercase I is i = "
                + "I".toLowerCase(tr).equals("i"));
        System.out.println("ROOT lowercase I is i = "
                + "I".toLowerCase(Locale.ROOT).equals("i"));

        String german = "Stra\u00DFe";
        System.out.println("sharp-s ignore-case = "
                + german.equalsIgnoreCase("STRASSE"));
        System.out.println("sharp-s upper ROOT = "
                + german.toUpperCase(Locale.ROOT).equals("STRASSE"));

        String composed = "\u00E9";
        String decomposed = "e\u0301";
        System.out.println("before NFC = "
                + composed.equalsIgnoreCase(decomposed));
        System.out.println("after NFC = "
                + Normalizer.normalize(composed, Normalizer.Form.NFC)
                  .equalsIgnoreCase(
                      Normalizer.normalize(
                          decomposed, Normalizer.Form.NFC)));

        System.out.println("Greek sigma = "
                + "\u03A3".equalsIgnoreCase("\u03C2"));
    }
}
```

Biên dịch và chạy (Java 17 trở lên):

```bash
javac -encoding UTF-8 EqualsIgnoreCaseDemo.java
java EqualsIgnoreCaseDemo
```

Kết quả:

```text
basic = true
space = false
null argument = false
I vs i = true
I vs dotless = true
dotted vs i = true
Turkish lowercase I is i = false
ROOT lowercase I is i = true
sharp-s ignore-case = false
sharp-s upper ROOT = true
before NFC = false
after NFC = true
Greek sigma = true
```

**Diễn giải:** cùng một phương thức `equalsIgnoreCase` làm tốt việc bỏ qua khác biệt hoa/thường đơn giản (`Java`/`jAVA`), nhưng không tự biết quy tắc phân biệt hai chữ `I` trong tiếng Thổ Nhĩ Kỳ, không mở rộng `ß` thành `SS`, cũng không chuyển hai dạng biểu diễn `é` về cùng một chuẩn Unicode. Mỗi khác biệt đó cần được giải quyết theo **quy tắc dữ liệu/ngôn ngữ** cụ thể.

## 7. Khi nào chọn `equalsIgnoreCase`, `Locale.ROOT` hay `Collator`?

Không có một thuật toán “bỏ qua hoa/thường” phù hợp cho mọi dữ liệu. Trước tiên hãy xác định bạn muốn so sánh **mã giao thức**, **dữ liệu định danh ứng dụng**, hay **văn bản ngôn ngữ tự nhiên**.

| Nhu cầu | Cách tiếp cận | Lưu ý |
| --- | --- | --- |
| So sánh giá trị lựa chọn đơn giản như `"yes"`, `"YES"` | `"yes".equalsIgnoreCase(input)` | Đủ khi hợp đồng giá trị rõ ràng và tập ký tự được kiểm soát. |
| Chuẩn hóa mã ASCII theo quy ước cố định | Xác thực alphabet hợp lệ, rồi dùng phép so sánh phù hợp; có thể chuyển bằng `Locale.ROOT` nếu quy ước yêu cầu | Kiểm tra đúng cú pháp trước; không tự chấp nhận mọi chữ Unicode trông giống ASCII. |
| So sánh văn bản theo ngôn ngữ người dùng | `java.text.Collator` theo `Locale` phù hợp | Quy tắc so sánh, dấu và chữ hoa/thường khác nhau theo locale/cấu hình. |
| So sánh Unicode canonical-equivalent | Chuẩn hóa `Normalizer` rồi so sánh theo chính sách đã chọn | NFC **không** phải full case folding. |
| Định danh Unicode không phân biệt hoa/thường theo một tiêu chuẩn nghiệp vụ | Định nghĩa rõ chuẩn hóa, case folding, phiên bản Unicode và xử lý trường hợp đặc biệt | `equalsIgnoreCase` không mặc nhiên tương đương Unicode full case folding. |
| So sánh mật khẩu/token mật được cấp chính xác | Thực hiện theo hợp đồng bảo mật của hệ thống | Không mặc định bỏ qua hoa/thường; với dữ liệu bí mật còn có yêu cầu riêng về so sánh an toàn. |

### `Collator` cho ngôn ngữ tự nhiên

Khi bạn cần so sánh theo quy tắc tiếng Thổ Nhĩ Kỳ, tiếng Việt hoặc ngôn ngữ khác, hãy dùng API collation được cấu hình đúng:

```java
import java.text.Collator;
import java.util.Locale;

Locale locale = Locale.forLanguageTag("tr");
Collator collator = Collator.getInstance(locale);

// Mức SECONDARY thường giữ phân biệt dấu
// và giảm phân biệt chữ hoa/thường, tùy bộ quy tắc collation.
collator.setStrength(Collator.SECONDARY);

boolean same = collator.compare("I", "\u0131") == 0;
```

Đây là đoạn minh họa đặt trong một phương thức Java. `Collator` có nhiều mức độ so sánh (`PRIMARY`, `SECONDARY`, `TERTIARY`, …); lựa chọn mức độ quyết định việc phân biệt dấu, hoa/thường và các biến thể khác theo locale/provider. Đừng đặt `Collator.PRIMARY` rồi mặc nhiên giả định mọi ngôn ngữ sẽ chỉ bỏ qua hoa/thường mà vẫn giữ mọi khác biệt khác.

`Collator` không nên dùng như một bộ định nghĩa chuẩn tắc cho các mã định danh mang tính giao thức. Quy tắc collation có thể chịu ảnh hưởng của locale, provider và dữ liệu ngôn ngữ trong runtime. Nếu cần tính ổn định xuyên các hệ thống/phiên bản, phải quy định, kiểm thử và quản lý phiên bản thuật toán so sánh.

### Dùng `Locale.ROOT` đúng bối cảnh

`toLowerCase(Locale.ROOT)` hoặc `toUpperCase(Locale.ROOT)` hữu ích khi cần một phép biến đổi chữ **không phụ thuộc locale mặc định**, ví dụ xử lý một số khóa cấu hình theo quy ước đã chốt.

Tuy nhiên, hai điểm cần nhớ:

1. Chuyển hoa/thường toàn chuỗi có thể thay đổi **độ dài** hoặc phụ thuộc ngữ cảnh Unicode, nên không tương đương `equalsIgnoreCase`.
2. Một ánh xạ hoa/thường trung lập không phải bộ quy tắc so sánh người dùng cho mọi ngôn ngữ, và cũng không đảm bảo nhận diện mọi cặp mà Unicode full case folding xem là tương đương.

Đặc biệt, tránh `text.toLowerCase()` không truyền tham số khi thuật toán yêu cầu kết quả bất biến theo máy hoặc người dùng: overload không tham số sử dụng **locale mặc định**, nên kết quả có thể thay đổi theo môi trường chạy.

## 8. Lưu ý về Unicode code point và ký tự hiển thị

`String` trong Java sử dụng chỉ số dựa trên UTF-16 code unit, và `length()` trả về số code unit. Một ký tự ngoài BMP có thể chiếm hai `char`; một “ký tự người dùng nhìn thấy” (grapheme cluster) có thể chứa nhiều code point.

`equalsIgnoreCase` so sánh nội dung theo quy tắc case-insensitive được Java định nghĩa, nhưng không tự:
- Phân đoạn chuỗi thành grapheme cluster theo trải nghiệm người dùng.
- Bỏ dấu tiếng Việt hay dấu kết hợp.
- Đồng nhất ký tự nhìn tương tự nhau nhưng có code point khác nhau (ví dụ chữ Latin và chữ Cyrillic trông gần giống).
- Gộp mọi cặp chuỗi khác độ dài bằng phép case folding đầy đủ.
- Thực hiện Unicode normalization trước khi so sánh.

Vì vậy, với tìm kiếm, xác thực tên hiển thị hoặc phân nhóm văn bản quốc tế, cần xác định **đơn vị văn bản và quy tắc tương đương** trước khi viết code. Hai chuỗi nhìn giống nhau không chắc bằng nhau theo Java; hai chữ mà Java coi bằng nhau khi bỏ qua hoa/thường chưa chắc tương đương trong một ngôn ngữ.

## 9. Những lỗi thường gặp

**Lỗi 1 — Xem `equalsIgnoreCase` như so sánh theo locale người dùng.**

`"I".equalsIgnoreCase("i")` là `true` bất kể đang phục vụ giao diện tiếng Anh hay tiếng Thổ Nhĩ Kỳ. Nếu nghiệp vụ yêu cầu khác nhau theo ngôn ngữ, phải dùng cơ chế so sánh có ý thức về locale.

**Lỗi 2 — Gọi `toLowerCase()` không truyền `Locale` để chuẩn hóa mã máy.**

```java
String key = input.toLowerCase(); // Phụ thuộc default Locale.
```

Nếu dữ liệu có hợp đồng trung lập và đúng quy tắc chuyển chữ toàn chuỗi, hãy truyền `Locale.ROOT` tường minh, đồng thời xác nhận domain ký tự và hậu quả ánh xạ Unicode. Nếu mã chỉ được phép ASCII, xác thực ASCII trước.

**Lỗi 3 — Nghĩ `equalsIgnoreCase` sẽ xử lý `ß` bằng `SS`.**

`"Straße".equalsIgnoreCase("STRASSE")` cho kết quả `false` do độ dài khác nhau. Case conversion toàn chuỗi có thể có kết quả khác; không nên dùng thay thế mà không xác định semantics.

**Lỗi 4 — Bỏ qua Unicode normalization.**

`"\u00E9"` và `"e\u0301"` không bằng nhau theo `equalsIgnoreCase` vì biểu diễn khác nhau. Nếu muốn canonical equivalence, cần chuẩn hóa có chủ đích (ví dụ NFC) cho **cả hai** chuỗi.

**Lỗi 5 — Nhầm `null` với chuỗi rỗng hoặc bỏ kiểm tra receiver.**

`"x".equalsIgnoreCase(null)` trả `false`, nhưng `nullable.equalsIgnoreCase("x")` ném `NullPointerException` khi `nullable == null`. Hãy xác định chính sách null rõ ràng.

**Lỗi 6 — Dùng kết quả comparison làm khóa lâu dài mà không chuẩn hóa quy ước.**

Nếu hệ thống cho phép người dùng đặt username, mã tham chiếu, hay nhãn phân nhóm Unicode, cần quyết định một **quan hệ tương đương chuẩn** và cách lưu khóa để tránh va chạm hoặc phân biệt không nhất quán qua database, API, giao diện và phiên bản runtime. `equalsIgnoreCase` tự nó không định nghĩa quy tắc nhận diện Unicode đầy đủ cho mọi hệ thống.

## 10. Bài tập tự kiểm tra

### Bài 1

```java
System.out.println("ABC".equalsIgnoreCase("abc"));
System.out.println("abc".equalsIgnoreCase("abc "));
```

**Đáp án:** `true` rồi `false`. Lời gọi thứ hai không bằng nhau vì có thêm khoảng trắng, không chỉ khác chữ hoa/thường.

### Bài 2

```java
System.out.println("I".equalsIgnoreCase("\u0131"));
System.out.println("I".toLowerCase(
        java.util.Locale.forLanguageTag("tr")).equals("i"));
```

**Đáp án:** `true` rồi `false`. Phương thức thứ nhất dùng quy tắc không phụ thuộc locale; phép chuyển chữ thường thứ hai dùng quy tắc của locale Thổ Nhĩ Kỳ.

### Bài 3

```java
System.out.println("Stra\u00DFe".equalsIgnoreCase("STRASSE"));
System.out.println("Stra\u00DFe".toUpperCase(
        java.util.Locale.ROOT).equals("STRASSE"));
```

**Đáp án:** `false` rồi `true`. Sự khác biệt minh họa rằng `equalsIgnoreCase` không tương đương với chuyển hoa toàn chuỗi rồi gọi `equals`.

### Bài 4

```java
String a = "\u00E9";
String b = "e\u0301";
System.out.println(a.equalsIgnoreCase(b));
```

**Đáp án:** `false`. Khác biểu diễn Unicode, muốn nhận canonical-equivalent thì chuẩn hóa theo quy tắc đã thống nhất trước khi so sánh.

### Bài 5

Một ứng dụng quốc tế muốn kiểm tra tên hiển thị theo ngôn ngữ người dùng; ứng dụng khác muốn kiểm tra tùy chọn ASCII `"ON"`/`"OFF"`. Cả hai có nên mặc định dùng cùng `equalsIgnoreCase` không?

**Đáp án:** Không. Với tùy chọn ASCII có domain và quy tắc rõ, `equalsIgnoreCase` phù hợp. Với tên hiển thị theo ngôn ngữ, cần xác định locale và mức độ collation, chuẩn hóa Unicode khi hợp đồng yêu cầu, rồi chọn API tương ứng.

## 11. Ghi nhớ

- `equalsIgnoreCase` bỏ qua hoa/thường theo quy tắc Java **không phụ thuộc `Locale`**; nó không gọi phép so sánh ngôn ngữ tự nhiên theo mặc định.
- Các chuỗi khác độ dài không bằng nhau; `ß` so với `SS` là ví dụ giới hạn của phương thức.
- `I/i/İ/ı` cho thấy phép so sánh không phụ thuộc locale có thể không phù hợp quy tắc tiếng Thổ Nhĩ Kỳ.
- Unicode normalization và case-insensitive comparison là **hai bước khác nhau**; `equalsIgnoreCase` không làm normalization.
- Chọn `Collator` cho nhu cầu so sánh theo ngôn ngữ, `Locale.ROOT` cho phép chuyển hoa/thường trung lập **khi hợp đồng dữ liệu phù hợp**, và cơ chế full case folding chuyên biệt nếu ứng dụng cần chuẩn tương đương Unicode chặt chẽ.
- Luôn xác định miền dữ liệu, chính sách null, khoảng trắng, dấu, ngôn ngữ, cách chuẩn hóa và tính ổn định giữa các hệ thống trước khi quyết định hai chuỗi “bằng nhau”.

## Tài liệu tham khảo

- [Java SE 17 — `String.equalsIgnoreCase(String)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html#equalsIgnoreCase(java.lang.String))
- [Java SE 17 — `String.toLowerCase(Locale)` và `toUpperCase(Locale)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html)
- [Java SE 17 — `Collator`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/Collator.html)
- [Java SE 17 — `Normalizer`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/Normalizer.html)
- [Unicode Standard — `Default Case Folding` (Chapter 3)](https://www.unicode.org/versions/latest/core-spec/chapter-3/)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-comparison-005">compareTo và thứ tự từ điển theo giá trị char</a>

<details>
<summary>Xem chi tiết</summary>

# `String.compareTo()` và thứ tự từ điển theo giá trị `char` trong Java

## 1. `compareTo()` giải quyết vấn đề gì?

Khi cần biết hai chuỗi có **bằng nhau về nội dung** hay không, Java có `String.equals(...)`. Nhưng nhiều bài toán còn cần biết chuỗi nào **đứng trước hoặc đứng sau** chuỗi khác để sắp xếp tên, từ khóa, mã định danh hay danh sách.

Lớp `java.lang.String` cung cấp:

```java
public int compareTo(String anotherString)
```

Đây là phép **so sánh thứ tự từ điển** (*lexicographical comparison*) của hai chuỗi theo các giá trị `char` (đơn vị mã UTF-16) của Java. Phương thức xét từ trái sang phải, tìm vị trí đầu tiên mà hai chuỗi khác nhau. Nếu mọi vị trí chung đều giống nhau, chuỗi ngắn hơn đứng trước.

Ví dụ:

```java
System.out.println("apple".compareTo("banana")); // Số âm
System.out.println("java".compareTo("java"));     // 0
System.out.println("zebra".compareTo("apple"));   // Số dương
```

Các đoạn mã ngắn trong bài chỉ là **mã minh họa bên trong một phương thức**, trừ ví dụ có lớp `public class` và phương thức `main` được trình bày đầy đủ ở phần sau.

Điều quan trọng nhất khi đọc giá trị trả về là **dấu** của số nguyên:

| Giá trị `left.compareTo(right)` | Kết luận |
|---|---|
| Nhỏ hơn `0` | `left` đứng **trước** `right` theo thứ tự của `String` |
| Bằng `0` | Hai chuỗi có cùng dãy giá trị `char` |
| Lớn hơn `0` | `left` đứng **sau** `right` theo thứ tự của `String` |

**Không nên chỉ kiểm tra `compareTo(...) == -1` hoặc `== 1`.** Phương thức có thể trả `-32`, `2`, `5` hoặc một giá trị âm/dương khác. Khi chỉ cần xác định thứ tự, hãy so sánh với **`0`**.

## 2. Cú pháp và hợp đồng API

Cú pháp cơ bản:

```java
String left = "cat";
String right = "car";

int result = left.compareTo(right);
System.out.println(result); // 2
```

- `left` là đối tượng `String` **nhận lời gọi**.
- `right` là đối số `String` **được so sánh với** `left`.
- Kết quả là một `int`; giá trị mang ý nghĩa **thứ tự**, không phải vị trí ký tự trong chuỗi.
- `compareTo` **không sửa** hai chuỗi vì `String` bất biến.
- Đối số phải là một `String` khác `null`. Nếu truyền `null`, lời gọi ném `NullPointerException`. Nếu biến nhận lời gọi là `null`, việc gọi phương thức cũng ném `NullPointerException`.

`String` triển khai giao diện `Comparable<String>`. Vì thế Java có thể dùng `compareTo` làm **thứ tự tự nhiên** (*natural ordering*) của `String`, chẳng hạn khi gọi `Arrays.sort` hoặc `Collections.sort` mà không cung cấp `Comparator` riêng.

### So với `equals(...)` và `==`

```java
String first = new String("Java");
String second = "Java";

System.out.println(first.equals(second));    // true: cùng nội dung
System.out.println(first.compareTo(second)); // 0: cùng nội dung, bằng thứ tự
System.out.println(first == second);         // false: khác tham chiếu
```

Với hai đối tượng `String` khác `null`, điều kiện `a.compareTo(b) == 0` tương đương với `a.equals(b)` về **nội dung chuỗi**. Tuy nhiên:

- Dùng **`equals`** để trả lời “Nội dung có bằng nhau không?”.
- Dùng **`compareTo`** để trả lời “Chuỗi nào đứng trước/sau?”.
- **`==`** trên hai biến `String` kiểm tra cùng tham chiếu đối tượng, không phải thứ tự và cũng không phù hợp để so nội dung.

## 3. Thuật toán so sánh từng `char` từ trái sang phải

Giả sử hai chuỗi lần lượt là `left` và `right`.

**Quy tắc 1 – Tìm vị trí đầu tiên khác nhau.** Java xét từng giá trị `char` theo chỉ số `0`, `1`, `2`, ... đến hết phần chung. Nếu gặp vị trí `k` mà:

```java
left.charAt(k) != right.charAt(k)
```

thì kết quả mang giá trị:

```java
left.charAt(k) - right.charAt(k)
```

Việc trừ ở đây sử dụng giá trị số của hai `char`. Phần **dấu** quyết định thứ tự. Những ký tự nằm sau vị trí khác nhau đầu tiên **không còn ảnh hưởng** đến phép so sánh.

**Quy tắc 2 – Nếu toàn bộ phần chung giống nhau.** Nếu một chuỗi là tiền tố của chuỗi kia (hoặc cả hai giống hệt), kết quả là:

```java
left.length() - right.length()
```

Vì vậy chuỗi ngắn hơn đứng trước, còn chuỗi có cùng độ dài và cùng dãy `char` thì trả `0`.

### Ví dụ: ký tự khác nhau đầu tiên

```java
"cat".compareTo("car")
```

```text
Chỉ số:  0   1   2
Chuỗi 1: c   a   t
Chuỗi 2: c   a   r
                 ^
              Khác đầu tiên

't' = 116, 'r' = 114
Kết quả: 116 - 114 = 2
```

Do đó `"cat".compareTo("car")` trả `2`. Đảo chiều lời gọi:

```java
"car".compareTo("cat") // -2
```

Kết quả âm vì tại vị trí khác nhau đầu tiên, `'r'` nhỏ hơn `'t'`.

### Ví dụ: một chuỗi là tiền tố của chuỗi còn lại

```java
"app".compareTo("apple") // -2
"apple".compareTo("app") // 2
"same".compareTo("same") // 0
```

`"app"` là tiền tố của `"apple"`. Toàn bộ 3 vị trí chung đều giống nhau, nên kết quả là `3 - 5 = -2`, **không phải vị trí chữ cái `l`**.

Với chuỗi rỗng:

```java
"".compareTo("")      // 0
"".compareTo("Java")  // -4
"Java".compareTo("")  // 4
```

Chuỗi rỗng đứng trước mọi chuỗi không rỗng vì độ dài `0` nhỏ hơn độ dài của chúng.

## 4. Thứ tự này dựa trên giá trị `char`, không phải từ điển tiếng Việt

Trong Java, kiểu `char` biểu diễn **một UTF-16 code unit** có giá trị nguyên không dấu từ `0` đến `65535` (`U+0000` đến `U+FFFF` ở mức giá trị đơn vị mã). Thứ tự tự nhiên của `String` được xác định từ dãy giá trị `char` này.

Một số giá trị dễ nhớ:

| Ký tự | Giá trị thập phân của `char` | Ghi chú |
|---|---:|---|
| `'0'` | 48 | Chữ số dạng ký tự |
| `'9'` | 57 | Vẫn là ký tự, không phải số nguyên 9 |
| `'A'` | 65 | Chữ Latin hoa |
| `'Z'` | 90 | Chữ Latin hoa |
| `'a'` | 97 | Chữ Latin thường |
| `'z'` | 122 | Chữ Latin thường |

Vì vậy:

```java
"A".compareTo("a")     // -32: 65 - 97
"a".compareTo("A")     // 32: 97 - 65
"9".compareTo("A")     // -8: 57 - 65
"Z".compareTo("a")     // -7: 90 - 97
```

Không thể kết luận `"apple"` luôn đứng trước `"Banana"` theo cảm nhận của một ngôn ngữ tự nhiên. Theo `String.compareTo`, `"Banana"` đứng trước `"apple"` vì ký tự đầu tiên `'B'` (66) nhỏ hơn `'a'` (97).

### Phân biệt so sánh số và so sánh chuỗi chứa chữ số

```java
System.out.println("10".compareTo("2")); // -1
System.out.println(Integer.compare(10, 2)); // 1
```

Chuỗi `"10"` đứng **trước** `"2"` vì ký tự đầu tiên `'1'` (49) nhỏ hơn `'2'` (50). Ngược lại, số nguyên `10` lớn hơn `2`.

Khi cần sắp xếp các **số**, hãy chuyển đổi sang kiểu số thích hợp và so sánh số; khi cần sắp xếp “tự nhiên” các mã như `file2`, `file10`, cần một `Comparator` có quy tắc tách phần số. `String.compareTo` không tự nhận biết các đoạn số nằm trong văn bản.

## 5. Ví dụ Java 17+ hoàn chỉnh và stdout

Tạo tệp **`StringCompareToDemo.java`** với nội dung:

```java
import java.util.Arrays;

public class StringCompareToDemo {
    public static void main(String[] args) {
        System.out.println("cat/car = " + "cat".compareTo("car"));
        System.out.println("car/cat = " + "car".compareTo("cat"));
        System.out.println("app/apple = " + "app".compareTo("apple"));
        System.out.println("apple/app = " + "apple".compareTo("app"));
        System.out.println("java/java = " + "java".compareTo("java"));
        System.out.println("A/a = " + "A".compareTo("a"));
        System.out.println("10/2 = " + "10".compareTo("2"));

        String[] words = {"banana", "Apple", "apple", "Banana", "app"};
        Arrays.sort(words);
        System.out.println("sorted = " + Arrays.toString(words));

        String supplementary = "😀";
        String bmpPrivateUse = "\uE000";
        System.out.println("UTF16 order sign = "
                + Integer.signum(supplementary.compareTo(bmpPrivateUse)));
        System.out.println("Code point order sign = "
                + Integer.signum(Integer.compare(
                        supplementary.codePointAt(0),
                        bmpPrivateUse.codePointAt(0))));
    }
}
```

Chạy trên JDK 17 trở lên trong thư mục chứa tệp:

```bash
javac -encoding UTF-8 StringCompareToDemo.java
java StringCompareToDemo
```

**Stdout:**

```text
cat/car = 2
car/cat = -2
app/apple = -2
apple/app = 2
java/java = 0
A/a = -32
10/2 = -1
sorted = [Apple, Banana, app, apple, banana]
UTF16 order sign = -1
Code point order sign = 1
```

**Giải thích kết quả:**

1. `"cat"` và `"car"` khác nhau đầu tiên ở `'t'` và `'r'`, nên trả `2` và `-2` nếu đảo chiều.
2. `"app"` là tiền tố của `"apple"`, kết quả là chênh lệch độ dài `3 - 5 = -2`.
3. Hai literal `"java"` giống hệt nên trả `0`.
4. `'A'` (65) so với `'a'` (97) cho `-32`; `"10"` so với `"2"` so chữ số đầu `'1'` và `'2'` nên `-1`.
5. `Arrays.sort(words)` sử dụng thứ tự tự nhiên của `String`: các mục bắt đầu bằng chữ hoa được đặt trước các mục bắt đầu bằng chữ thường trong tập dữ liệu này.
6. Hai dòng cuối cho thấy một đặc điểm của UTF-16: so sánh theo đơn vị `char` đôi khi có **thứ tự khác** so với so sánh giá trị Unicode code point. Phần 7 giải thích chi tiết.

## 6. Dùng `compareTo` để sắp xếp và rẽ nhánh

### Kiểm tra thứ tự với `if`

```java
String left = "alpha";
String right = "beta";

int order = left.compareTo(right);

if (order < 0) {
    System.out.println("left đứng trước right");
} else if (order > 0) {
    System.out.println("left đứng sau right");
} else {
    System.out.println("hai chuỗi bằng nhau");
}
```

Phép so sánh với `0` là cách đúng. Nếu chỉ cần biết dấu, có thể dùng `Integer.signum(order)` để quy về `-1`, `0` hoặc `1`.

### Sắp xếp tự nhiên bằng `Arrays.sort`

```java
String[] labels = {"zebra", "Apple", "banana"};
java.util.Arrays.sort(labels);
System.out.println(java.util.Arrays.toString(labels));
// [Apple, banana, zebra]
```

`Arrays.sort` sử dụng `compareTo` của các phần tử nếu mảng có kiểu `String[]` và không chỉ định `Comparator`. Tương tự, `java.util.Collections.sort(list)` sắp xếp một `List<String>` có thể thay đổi được theo thứ tự tự nhiên; `list.sort(null)` cũng dùng thứ tự tự nhiên.

Nếu muốn **đảo ngược** thứ tự:

```java
java.util.Arrays.sort(
        labels,
        java.util.Comparator.reverseOrder()
);
```

Lưu ý `Comparator.reverseOrder()` trên chuỗi cũng đảo thứ tự tự nhiên theo UTF-16; nó không biến phép sắp xếp thành từ điển theo ngôn ngữ người dùng.

### Dùng với collection có thứ tự

`TreeSet<String>` hoặc `TreeMap<String, ...>` có thể sử dụng thứ tự tự nhiên của `String` khi không cung cấp comparator. Quy tắc thứ tự ảnh hưởng cả **cách tìm kiếm và xác định khóa tương đương** trong các cấu trúc ấy.

```java
java.util.Set<String> set = new java.util.TreeSet<>();
set.add("apple");
set.add("Apple");

System.out.println(set); // [Apple, apple]
```

Hai chuỗi này khác nhau theo `compareTo`, nên được giữ thành hai phần tử riêng trong `TreeSet` mặc định.

## 7. Chi tiết Unicode: `char` không luôn tương đương một ký tự nhìn thấy

Đây là điểm quan trọng nhất trong cụm từ **“thứ tự từ điển theo giá trị `char`”**.

`String` của Java dùng các đơn vị mã UTF-16. Một Unicode code point trong vùng BMP thường biểu diễn bằng một `char`; nhưng những code point lớn hơn `U+FFFF` (ví dụ nhiều emoji) dùng **một cặp surrogate** gồm hai `char`.

Ví dụ:

```java
String smile = "😀";

System.out.println(smile.length()); // 2
System.out.println(smile.codePointCount(0, smile.length())); // 1
```

Vì thế `compareTo` so sánh **các code unit UTF-16**, không phải luôn so trực tiếp giá trị nguyên của Unicode code point.

### Ví dụ có thể gây bất ngờ

- Emoji `"😀"` có Unicode code point `U+1F600`, được biểu diễn trong UTF-16 bởi cặp `U+D83D U+DE00`.
- Ký tự ở vị trí `U+E000` là một code point vùng BMP, biểu diễn bởi `char` có giá trị `U+E000`.
- Nếu so code point dưới dạng số nguyên, `U+1F600` **lớn hơn** `U+E000`.
- Nhưng `String.compareTo` xét `char` **đầu tiên**: `U+D83D` **nhỏ hơn** `U+E000`, nên `"😀".compareTo("\uE000")` trả một số **âm**.

Đó là lý do hai dòng cuối ví dụ chạy ở phần 5 cho dấu lần lượt là `-1` và `1`.

**Kết luận về Unicode:** thứ tự của `String.compareTo` là thứ tự xác định, ổn định theo chuỗi đơn vị mã UTF-16, nhưng không nên diễn giải nó là “thứ tự tất cả ký tự Unicode theo code point”, càng không phải thứ tự từ điển theo tiếng Việt hoặc một ngôn ngữ khác.

### Ký tự ghép dấu và chuẩn hóa

Hai chuỗi có thể trông giống nhau nhưng chứa các dãy code point khác nhau. Ví dụ chữ `é` có thể được biểu diễn bằng một code point `U+00E9` hoặc bằng chữ `e` (`U+0065`) cộng dấu sắc kết hợp (`U+0301`).

```java
String composed = "\u00E9";
String decomposed = "e\u0301";

System.out.println(composed.equals(decomposed));    // false
System.out.println(composed.compareTo(decomposed)); // Số dương
```

Ngay ở đơn vị mã đầu tiên, `U+00E9` (233) lớn hơn `U+0065` (101), nên `compareTo` trả số dương. Nếu nghiệp vụ xem hai cách biểu diễn là cùng văn bản, có thể **chuẩn hóa Unicode** bằng `java.text.Normalizer` trước khi so sánh. Chọn hình thức NFC/NFD tùy yêu cầu và thực hiện nhất quán cho các bên.

## 8. So sánh không phân biệt hoa/thường và so sánh theo ngôn ngữ

### `compareToIgnoreCase` giải quyết bài toán khác

Khi muốn so sánh chuỗi mà **bỏ qua khác biệt chữ hoa/chữ thường** theo quy tắc không phân biệt chữ của Java, có thể dùng:

```java
System.out.println("JAVA".compareToIgnoreCase("java")); // 0
```

Tuy nhiên, `compareToIgnoreCase` **không phải** bộ sắp xếp theo ngôn ngữ tự nhiên. Các phép chuyển đổi hoa/thường phức tạp của Unicode, quy tắc theo ngôn ngữ và dấu có thể cần xử lý riêng. Đừng giả định rằng bỏ qua hoa/thường cũng tự động bỏ dấu tiếng Việt, đồng nhất mọi kiểu chữ hoặc tạo thứ tự chữ cái theo ngôn ngữ.

Để không phân biệt hoa/thường khi sắp xếp:

```java
String[] words = {"b", "A", "a"};
java.util.Arrays.sort(words, String.CASE_INSENSITIVE_ORDER);
```

Comparator này sử dụng quy tắc so sánh không phân biệt hoa/thường của Java. Với những chuỗi khác nhau nhưng được comparator coi là bằng nhau, cần hiểu quy tắc bảo toàn thứ tự hoặc thêm tiêu chí phụ nếu nghiệp vụ yêu cầu kết quả quyết định duy nhất.

### `Collator` dành cho thứ tự theo ngôn ngữ

Đối với tên người, từ tiếng Việt, nhãn giao diện hoặc nội dung cần so đúng quy tắc của một ngôn ngữ, hãy cân nhắc `java.text.Collator`:

```java
import java.text.Collator;
import java.util.Arrays;
import java.util.Locale;

class VietnameseSortingExample {
    public static void main(String[] args) {
        String[] names = {"Đào", "An", "Ân", "Ánh"};
        Collator vi = Collator.getInstance(Locale.forLanguageTag("vi-VN"));

        Arrays.sort(names, vi);
        System.out.println(Arrays.toString(names));
    }
}
```

Đây là chương trình minh họa **độc lập** (lớp không `public` vẫn có thể chạy khi biên dịch vào `VietnameseSortingExample.java`), nhưng bài không khẳng định một dòng stdout cố định: thứ tự chi tiết có thể phụ thuộc dữ liệu đối chiếu ngôn ngữ và cấu hình phiên bản Java. `Collator` có các thiết lập như độ nhạy với dấu, hoa/thường và các cấp so sánh (*strength*) cần chọn theo yêu cầu.

`compareTo` phù hợp khi bạn muốn **thứ tự tự nhiên theo `char` của Java**; `Collator` phù hợp khi yêu cầu là thứ tự **theo ngôn ngữ**. Đây là hai hợp đồng khác nhau.

## 9. `null`, `Comparator` và tính nhất quán

Với biến tham chiếu có thể `null`, gọi `compareTo` trực tiếp không an toàn:

```java
String value = null;
// value.compareTo("Java");  // NullPointerException
// "Java".compareTo(null);   // NullPointerException
```

Nếu bài toán muốn cho phép `null` xuất hiện trong danh sách, hãy định nghĩa quy tắc thứ tự rõ ràng bằng `Comparator`:

```java
java.util.Comparator<String> order =
        java.util.Comparator.nullsFirst(
                java.util.Comparator.naturalOrder()
        );

System.out.println(order.compare(null, "Java")); // Số âm
System.out.println(order.compare("Java", null)); // Số dương
System.out.println(order.compare(null, null));   // 0
```

`naturalOrder()` dùng `String.compareTo`; `nullsFirst(...)` bổ sung quy tắc đặt `null` trước các chuỗi hợp lệ.

Khi viết comparator riêng cho cấu trúc có thứ tự như `TreeSet` hoặc `TreeMap`, hãy chú ý khái niệm **tương đương theo comparator**: hai giá trị mà comparator trả `0` có thể bị xem là một phần tử/khóa tương đương dù `equals` của chúng là `false`. Ví dụ `String.CASE_INSENSITIVE_ORDER.compare("A","a") == 0`, trong khi `"A".equals("a")` là `false`. Với `TreeSet` sử dụng comparator đó, chỉ một trong hai giá trị có thể được lưu làm phần tử đại diện cho nhóm tương đương.

`String.compareTo` tự nhiên **nhất quán với `equals`**, nên không có bất đồng này khi so các `String` bằng comparator tự nhiên mặc định.

## 10. Những sai lầm thường gặp

| Sai lầm | Vì sao sai | Cách đúng |
|---|---|---|
| `if (a.compareTo(b) == 1)` | Số dương không bắt buộc là `1` | `if (a.compareTo(b) > 0)` |
| Xem `compareTo` là `equals` | `compareTo` trả `int` để xác định thứ tự | Dùng `equals` khi chỉ kiểm tra bằng nhau |
| Nghĩ `"10"` đứng sau `"2"` | Chuỗi được so theo `char` từ trái sang phải | Chuyển sang số hoặc comparator có hiểu số |
| Nghĩ chữ thường luôn đứng trước chữ hoa | `'A'` có giá trị nhỏ hơn `'a'` | Hiểu thứ tự theo UTF-16; hoặc chọn comparator khác |
| Nghĩ so theo `char` luôn là so theo code point | Emoji có thể gồm hai UTF-16 code unit | Hiểu rõ đơn vị mã và quy tắc cần dùng |
| Nghĩ thứ tự theo `compareTo` là thứ tự tiếng Việt | API không dùng quy tắc ngôn ngữ | Dùng `Collator` phù hợp khi cần sắp tên/từ |
| Gọi `text.compareTo(null)` | Đối số `null` gây ngoại lệ | Kiểm tra đầu vào hoặc dùng comparator có xử lý `null` |
| Dùng `==` để kiểm tra từ bằng nhau | `==` so tham chiếu, không so văn bản | Dùng `equals` hoặc `compareTo(...) == 0` |

## 11. Bài tập tự kiểm tra

**Câu 1.** `"dog".compareTo("dot")` trả giá trị gì?

**Đáp án:** `'g'` (103) trừ `'t'` (116) bằng `-13`. Hai ký tự đầu giống nhau, nên xét ký tự thứ ba.

**Câu 2.** `"car".compareTo("carpet")` là âm, bằng không hay dương?

**Đáp án:** **Âm**, cụ thể là `3 - 6 = -3`, vì `"car"` là tiền tố của `"carpet"` và ngắn hơn.

**Câu 3.** Tại sao `"20".compareTo("100")` trả số dương dù số `20` nhỏ hơn số `100`?

**Đáp án:** So sánh ký tự đầu `'2'` (50) với `'1'` (49) cho `+1`; đây là thứ tự chuỗi, không phải so sánh số.

**Câu 4.** Sửa điều kiện sau để kiểm tra `a` đứng trước `b`:

```java
if (a.compareTo(b) == -1) {
    System.out.println("a đứng trước b");
}
```

**Đáp án:** Thay `== -1` bằng `< 0`. Mọi giá trị âm đều có cùng ý nghĩa thứ tự.

**Câu 5.** Hai chuỗi có cùng nội dung nhưng tạo thành hai đối tượng `String` khác nhau thì `compareTo` trả gì?

**Đáp án:** `0`, vì `compareTo` xét dãy `char` của **nội dung**, không xét danh tính đối tượng.

**Câu 6.** Khi sắp xếp tên tiếng Việt hiển thị cho người dùng, có nên mặc định dùng `compareTo` không?

**Đáp án:** Không nên nếu nghiệp vụ đòi thứ tự theo tiếng Việt. `compareTo` chỉ cung cấp thứ tự theo giá trị `char` UTF-16; cần cân nhắc `Collator` và thiết lập ngôn ngữ phù hợp.

## 12. Tổng kết kiến thức

`String.compareTo(other)` duyệt hai chuỗi từ trái sang phải theo **đơn vị mã UTF-16 `char`**. Nếu có vị trí khác nhau đầu tiên, kết quả mang dấu của hiệu hai giá trị `char` tại vị trí ấy; nếu toàn bộ phần chung bằng nhau, kết quả là hiệu độ dài. **Âm / 0 / dương** lần lượt có nghĩa là trước / bằng / sau theo thứ tự tự nhiên của `String`.

Quy tắc này giúp Java sắp xếp chuỗi một cách xác định, nhưng **không tự hiểu số, không tự bỏ qua hoa/thường, không tuân theo thứ tự tiếng Việt**, và có khác biệt đáng chú ý với thứ tự Unicode code point khi gặp ký tự bổ trợ. Hãy chọn `equals` để kiểm tra nội dung, `compareTo` hoặc comparator tự nhiên để sắp theo UTF-16, và `Collator` khi cần thứ tự từ điển của ngôn ngữ con người.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-comparison-006">compareToIgnoreCase và CASE_INSENSITIVE_ORDER</a>

<details>
<summary>Xem chi tiết</summary>

# `String.compareToIgnoreCase()` và `String.CASE_INSENSITIVE_ORDER` trong Java

## 1. Bài toán: sắp xếp chuỗi mà không phân biệt chữ hoa/chữ thường

Khi sắp xếp từ khóa, tên lệnh hoặc danh sách văn bản, thứ tự mặc định của `String.compareTo()` có thể không phù hợp nếu chỉ muốn bỏ qua khác biệt giữa chữ hoa và chữ thường. Java cung cấp hai API bổ sung cho cùng quy tắc so sánh:

- `a.compareToIgnoreCase(b)`: **phương thức trên một `String`**, trả về số âm, 0 hoặc số dương theo thứ tự từ điển *không phân biệt hoa/thường*.
- `String.CASE_INSENSITIVE_ORDER`: **đối tượng `Comparator<String>` có sẵn**, áp dụng thứ tự tương đương `compareToIgnoreCase` cho hai chuỗi được truyền vào. Nó đặc biệt tiện khi một API yêu cầu `Comparator`, chẳng hạn `List.sort`, `Arrays.sort`, `TreeSet` và `TreeMap`.

Cả hai đều so sánh **giá trị văn bản**, không so địa chỉ tham chiếu. Cả hai đều **không phụ thuộc `Locale`**, không biến đổi nội dung đầu vào, và không phải bộ quy tắc sắp xếp ngôn ngữ tự nhiên. Phân biệt rất quan trọng: bỏ qua hoa/thường không đồng nghĩa với bỏ qua dấu tiếng Việt, chuẩn hóa Unicode hay sắp xếp đúng theo từ điển của một quốc gia.

Mục tiêu sau bài:
1. Biết dùng `compareToIgnoreCase` để so hai chuỗi và đọc đúng dấu của kết quả.
2. Biết truyền `CASE_INSENSITIVE_ORDER` cho thuật toán sắp xếp và tập hợp có thứ tự.
3. Hiểu tại sao `compare(...) == 0` có thể khác `String.equals(...)` và hệ quả với `TreeSet`/`TreeMap`.
4. Nhận biết giới hạn Unicode/`Locale`, cách xử lý `null` và cách chọn tie-breaker khi cần giữ các chuỗi khác nhau về chữ hoa/thường.

## 2. `compareToIgnoreCase(String str)`: cú pháp và giá trị trả về

Khai báo API (Java 17+):

```java
public int compareToIgnoreCase(String str)
```

Gọi trên chuỗi bên trái; tham số `str` là chuỗi bên phải. Quy tắc đọc:

| Giá trị trả về | Diễn giải |
| --- | --- |
| `< 0` | Chuỗi bên trái đứng **trước** chuỗi bên phải theo thứ tự bỏ qua hoa/thường |
| `== 0` | Hai chuỗi được coi là **ngang hàng** theo quy tắc so sánh này |
| `> 0` | Chuỗi bên trái đứng **sau** chuỗi bên phải theo quy tắc này |

Ví dụ biểu thức (đặt trong một phương thức, chẳng hạn `main`):

```java
System.out.println("Java".compareToIgnoreCase("jAVA") == 0); // true
System.out.println("alpha".compareToIgnoreCase("BETA") < 0);  // true
System.out.println("Zulu".compareToIgnoreCase("apple") > 0); // true
System.out.println("cat".compareToIgnoreCase("CAtS") < 0);   // true
System.out.println("ab".compareToIgnoreCase("aB") == 0);     // true
```

**Chỉ dựa vào dấu, không dựa vào trị tuyệt đối**: API bảo đảm kết quả âm/0/dương tương ứng thứ tự, không nên viết kiểm tra như `compareToIgnoreCase(b) == -1` để kết luận đứng trước. Dùng `< 0`, `== 0` hoặc `> 0`.

Thứ tự là **từ điển** (lexicographical): so lần lượt các code point sau khi áp dụng quy tắc bỏ qua hoa/thường; tại khác biệt đầu tiên, giá trị đã chuẩn hóa quyết định thứ tự. Nếu phần đầu tương đương nhưng một chuỗi còn phần tiếp theo, chuỗi ngắn hơn đứng trước. Đây là thứ tự có thể dùng để sắp xếp; nó không đánh giá từ vựng, ý nghĩa hay ngôn ngữ tự nhiên.

## 3. Java xác định “không phân biệt hoa/thường” như thế nào?

Theo hợp đồng `String.compareToIgnoreCase` của Java SE 17, so sánh bỏ qua hoa/thường theo phép biến đổi từng **Unicode code point** bằng:

```java
Character.toLowerCase(Character.toUpperCase(codePoint))
```

Biểu thức này là **minh họa cho phép chuẩn hóa từng code point**, không phải chương trình độc lập hay lời khuyên tự viết lại comparator. Java sử dụng định nghĩa API của chính nó; bạn nên dùng trực tiếp `compareToIgnoreCase` hoặc `CASE_INSENSITIVE_ORDER`.

Hệ quả:
- `"Java"` và `"jAVA"` được xếp ngang hàng, vì khác biệt chỉ ở hoa/thường theo quy tắc Java.
- Đây là phép so **không xét `Locale`**. Nó không biết yêu cầu sắp xếp đặc thù tiếng Việt, tiếng Thổ Nhĩ Kỳ hoặc một bộ quy tắc ngôn ngữ khác.
- Quy tắc này không phải phép *full Unicode case folding* có thể mở rộng một ký tự thành nhiều ký tự. Ví dụ chữ `ß` trong `"Straße"` không tự tương đương chuỗi hai chữ `"SS"` trong `"STRASSE"`.
- Nó cũng không tự chuẩn hóa biểu diễn Unicode như dạng tiền tổ hợp so với base character kèm dấu kết hợp; hai chuỗi nhìn giống nhau có thể gồm các code point khác nhau.
- So sánh không chỉnh sửa `String`; chỉ tính thứ tự từ nội dung hiện tại.

Ví dụ với những chữ Latin thường dùng không dấu thì hành vi rất dễ đoán, nhưng không nên suy từ đó rằng đây là bộ công cụ so sánh văn bản theo ngôn ngữ tự nhiên.

## 4. `String.CASE_INSENSITIVE_ORDER`: Comparator dùng lại cùng quy tắc

`String.CASE_INSENSITIVE_ORDER` là một trường `public static final Comparator<String>` có trong lớp `String`. Comparator này so sánh `a` và `b` theo thứ tự tương đương:

```java
a.compareToIgnoreCase(b)
```

Điểm khác nhau nằm ở **cách sử dụng**, không phải mục tiêu so sánh:

| Tiêu chí | `a.compareToIgnoreCase(b)` | `String.CASE_INSENSITIVE_ORDER.compare(a,b)` |
| --- | --- | --- |
| Loại API | Phương thức instance của `String` | Phương thức của một `Comparator<String>` |
| Đầu vào | Biến nhận `a` và một đối số `b` | Hai đối số `a` và `b` |
| Kết quả | Số âm/0/dương | Cùng quy tắc âm/0/dương |
| Dùng trực tiếp khi cần so hai giá trị | Rất thuận tiện | Hợp lệ, nhưng dài hơn |
| Dùng với `List.sort`, `TreeSet`, `TreeMap` | Cần bọc trong lambda/method reference | Truyền trực tiếp comparator có sẵn |
| Phụ thuộc `Locale` | Không | Không |

Ví dụ độc lập, lưu `CaseInsensitiveBasics.java`:

```java
import java.util.Comparator;

public class CaseInsensitiveBasics {
    public static void main(String[] args) {
        String left = "Java";
        String right = "jAVA";
        Comparator<String> order = String.CASE_INSENSITIVE_ORDER;

        System.out.println(left.equals(right));
        System.out.println(left.equalsIgnoreCase(right));
        System.out.println(left.compareTo(right) == 0);
        System.out.println(left.compareToIgnoreCase(right));
        System.out.println(order.compare(left, right));
        System.out.println(order.compare("alpha", "BETA") < 0);
        System.out.println(order.compare("Zulu", "apple") > 0);
    }
}
```

Biên dịch/chạy với JDK 17+:

```bash
javac -encoding UTF-8 CaseInsensitiveBasics.java
java CaseInsensitiveBasics
```

```text
false
true
false
0
0
true
true
```

Hai chuỗi khác nhau về nội dung chính xác, nên `equals` và `compareTo(... ) == 0` đều `false`. Nhưng `equalsIgnoreCase` là `true`, `compareToIgnoreCase` và comparator đều coi chúng ngang hàng.

`CASE_INSENSITIVE_ORDER` là comparator có thể tái sử dụng; nó cũng hỗ trợ serialization theo tài liệu API. Tên `CASE_INSENSITIVE_ORDER` là một **giá trị comparator**, không phải phương thức `CASE_INSENSITIVE_ORDER()`.

## 5. Sắp xếp danh sách: `List.sort`, `Arrays.sort` và method reference

Dùng `CASE_INSENSITIVE_ORDER` khi muốn sắp xếp nhiều phần tử:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IgnoreCaseSortDemo {
    public static void main(String[] args) {
        List<String> words = new ArrayList<>(
                Arrays.asList("banana", "Apple", "cherry", "apple", "BANANA"));

        words.sort(String.CASE_INSENSITIVE_ORDER);
        System.out.println(words);

        String[] cities = {"zoo", "Beta", "alpha"};
        Arrays.sort(cities, String.CASE_INSENSITIVE_ORDER);
        System.out.println(Arrays.toString(cities));
    }
}
```

Lưu thành `IgnoreCaseSortDemo.java`, rồi chạy:

```bash
javac -encoding UTF-8 IgnoreCaseSortDemo.java
java IgnoreCaseSortDemo
```

```text
[Apple, apple, banana, BANANA, cherry]
[alpha, Beta, zoo]
```

`List.sort` và `Arrays.sort` với comparator đều thực hiện sắp xếp ổn định cho các phần tử thích hợp: nếu comparator trả `0`, thứ tự tương đối ban đầu được giữ. Vì vậy `Apple` nằm trước `apple` như dữ liệu đầu vào; `banana` nằm trước `BANANA`. Hai chuỗi vẫn tồn tại trong list/array vì sắp xếp **không khử phần tử**.

Có thể viết:

```java
words.sort((a, b) -> a.compareToIgnoreCase(b));
// Hoặc:
words.sort(String::compareToIgnoreCase);
```

Đây chỉ là **snippet thay thế lệnh sort** bên trong ví dụ có `words` ở trên. Khi không thêm quy tắc khác, dùng `String.CASE_INSENSITIVE_ORDER` thường dễ đọc và nói rõ ý đồ hơn.

Để có danh sách mới mà không thay đổi list gốc, có thể dùng `words.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()` (Java 16+), chú ý phần tử thuộc list trả về không thể thêm/xóa theo API `Stream.toList()`.

## 6. `equals`, `equalsIgnoreCase` và “bằng nhau theo Comparator” là các khái niệm khác nhau

Đặt `a = "API"` và `b = "api"`. Khi đó:
- `a.equals(b)` là `false`: `String.equals` yêu cầu chuỗi code unit bằng nhau chính xác.
- `a.equalsIgnoreCase(b)` là `true`: so bằng nhau theo quy tắc bỏ qua hoa/thường.
- `a.compareToIgnoreCase(b) == 0` là `true`.
- `String.CASE_INSENSITIVE_ORDER.compare(a,b) == 0` là `true`.

`Comparator.compare(a,b) == 0` không có nghĩa hai đối tượng là cùng một đối tượng (`a == b`) và cũng không nhất thiết tương đương theo `a.equals(b)`.

Trong thuật ngữ `Comparator`, nếu `compare(a,b) == 0` luôn đi đôi với `a.equals(b)` đối với các giá trị hợp lệ, thứ tự được gọi là *consistent with equals*. `CASE_INSENSITIVE_ORDER` **không nhất quán với `String.equals`** vì nó có thể coi hai chuỗi khác chính tả hoa/thường là cùng một phần tử xét theo thứ tự.

Điều này không làm comparator sai. Nó chỉ có hệ quả rất quan trọng với các collection được tổ chức bằng comparator.

## 7. `TreeSet` và `TreeMap`: comparator có thể quyết định tính duy nhất của khóa

`TreeSet` dựa vào thứ tự do comparator cung cấp để nhận diện giá trị đã có. Nếu `compare(newElement, existingElement) == 0`, phần tử mới **không được thêm** dù `newElement.equals(existingElement)` là `false`.

`TreeMap` cũng xác định các khóa tương đương theo comparator. Ghi giá trị cho một khóa khác kiểu chữ hoa/thường có thể **thay thế value** của khóa đã tồn tại, thay vì tạo entry thứ hai.

Lưu `CaseInsensitiveCollections.java`:

```java
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class CaseInsensitiveCollections {
    public static void main(String[] args) {
        Set<String> sorted = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        sorted.add("java");
        sorted.add("JAVA");
        sorted.add("Java");
        sorted.add("Go");
        sorted.add("go");

        Set<String> hashed = new HashSet<>();
        hashed.add("java");
        hashed.add("JAVA");
        hashed.add("Java");

        System.out.println(sorted);
        System.out.println(sorted.size());
        System.out.println(sorted.contains("jAvA"));
        System.out.println(hashed.size());

        TreeMap<String, Integer> quantities =
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        quantities.put("API", 10);
        quantities.put("api", 20);
        System.out.println(quantities.size());
        System.out.println(quantities.get("Api"));
        System.out.println(quantities.firstKey());
    }
}
```

```bash
javac -encoding UTF-8 CaseInsensitiveCollections.java
java CaseInsensitiveCollections
```

```text
[Go, java]
2
true
3
1
20
API
```

Phân tích:
1. `TreeSet` chỉ giữ `"java"` đầu tiên và `"Go"` đầu tiên. Các biến thể hoa/thường tiếp theo có `compare == 0` nên không tăng kích thước. `contains("jAvA")` trả `true` vì comparator tìm được nhóm tương đương.
2. `HashSet` thông thường dùng `equals`/`hashCode` của `String`: ba cách viết `java`, `JAVA` và `Java` khác nhau nên kích thước là 3.
3. `TreeMap` có một entry cho hai cách viết `API`/`api`; put thứ hai thay value thành 20. Khóa gốc `"API"` được giữ, nên `firstKey()` là `API` trong chương trình này.
4. Không dùng `CASE_INSENSITIVE_ORDER` cho `TreeSet` nếu yêu cầu phải **giữ đồng thời** các giá trị chỉ khác chữ hoa/thường. Nếu muốn gộp tên khóa bất kể chữ hoa/thường, hành vi gộp này lại có thể chính là điều mong muốn.

**Cẩn thận với hợp đồng `Set`/`Map`:** do comparator không nhất quán với `equals`, việc sử dụng một `TreeSet`/`TreeMap` có thứ tự không phân biệt hoa/thường có thể khiến chúng hành xử khác `HashSet`/`HashMap` hoặc kết quả so sánh collection khác kỳ vọng. Hãy mô tả quy tắc tương đương của khóa trong tài liệu API, nhất là khi truyền collection sang mã không biết comparator đang sử dụng.

## 8. Muốn bỏ qua hoa/thường khi xếp thứ tự, nhưng vẫn giữ mọi chuỗi khác nhau

Bài toán thường gặp: hiển thị `"API"` gần `"api"` nhưng **không muốn chúng bị xem như cùng khóa** trong `TreeSet`. Có thể thêm tiêu chí phụ (*tie-breaker*) sử dụng `String.compareTo` khi tiêu chí đầu coi hai chuỗi là ngang nhau:

```java
import java.util.Comparator;
import java.util.TreeSet;

public class TieBreakerDemo {
    public static void main(String[] args) {
        Comparator<String> order =
                String.CASE_INSENSITIVE_ORDER
                      .thenComparing(Comparator.naturalOrder());

        TreeSet<String> words = new TreeSet<>(order);
        words.add("api");
        words.add("API");
        words.add("Api");
        words.add("go");

        System.out.println(words);
        System.out.println(words.size());
        System.out.println(order.compare("API", "api") == 0);
        System.out.println(order.compare("API", "API") == 0);
    }
}
```

```bash
javac -encoding UTF-8 TieBreakerDemo.java
java TieBreakerDemo
```

```text
[API, Api, api, go]
4
false
true
```

Với `String` không `null`, comparator ghép này chỉ trả 0 khi các chuỗi bằng nhau theo `String.equals`: tiêu chí đầu nhóm theo thứ tự bỏ qua hoa/thường; tiêu chí phụ chỉ áp dụng khi tiêu chí đầu bằng nhau, giúp tách các giá trị khác chữ hoa/thường. Nhờ đó có thể giữ đủ các biến thể trong `TreeSet`.

Lưu ý: đây là **thứ tự hai tầng**, không phải một kiểu so sánh hoàn toàn không phân biệt hoa/thường. Người đọc thấy các biến thể đứng gần nhau, nhưng có thứ tự nội bộ do cách so sánh chính xác của Java quyết định.

## 9. Giới hạn Unicode và `Locale`

### 9.1. Không thay thế full Unicode case folding

Java xử lý hoa/thường trên từng Unicode code point theo quy tắc Java nêu ở trên. Nó không bảo đảm hai chuỗi sẽ được xem ngang hàng nếu phép chuyển đổi ngôn ngữ đòi hỏi **mở rộng thành nhiều code point**.

Ví dụ Java độc lập, lưu `UnicodeCaseDemo.java`:

```java
public class UnicodeCaseDemo {
    public static void main(String[] args) {
        String german = "Stra\u00DFe"; // Straße
        String upper = "STRASSE";

        System.out.println(german.equalsIgnoreCase(upper));
        System.out.println(german.compareToIgnoreCase(upper) == 0);
        System.out.println("I".equalsIgnoreCase("\u0131"));
        System.out.println("\u0130".equalsIgnoreCase("i"));
    }
}
```

```bash
javac -encoding UTF-8 UnicodeCaseDemo.java
java UnicodeCaseDemo
```

```text
false
false
true
true
```

Với hai chuỗi Đức, `ß` và dãy `SS` không được gộp qua phép so sánh từng code point này. Ngược lại, hai ví dụ có `I` không dấu chấm, `ı` (`U+0131`) và `İ` (`U+0130`) cho thấy quy tắc bỏ qua hoa/thường **không xét ngữ cảnh ngôn ngữ**: kết quả đúng theo Java có thể không đúng theo cách người dùng tiếng Thổ Nhĩ Kỳ mong đợi.

Không nên viết `s.toLowerCase()` rồi `compareTo()` như một “bản thay thế tương đương”: `toLowerCase()` mặc định phụ thuộc `Locale.getDefault()`, có thể đổi độ dài chuỗi và có thể cho thứ tự khác `compareToIgnoreCase`. `toLowerCase(Locale.ROOT)` giúp loại bỏ phụ thuộc default locale cho bước chuyển chữ, nhưng vẫn không biến thao tác so sánh thành bộ quy tắc sắp xếp ngôn ngữ hay chuẩn hóa Unicode đầy đủ.

### 9.2. So sánh theo ngôn ngữ bằng `Collator` khi yêu cầu

Khi người dùng cần xếp tên người, tên địa danh hoặc từ vựng theo quy tắc của một ngôn ngữ, Java cung cấp `java.text.Collator`. Nó là giải pháp được thiết kế cho so sánh có xét `Locale`:

```java
import java.text.Collator;
import java.util.Arrays;
import java.util.Locale;

public class LocaleSortExample {
    public static void main(String[] args) {
        String[] names = {"I", "i", "\u0130", "\u0131"};
        Collator collator = Collator.getInstance(
                Locale.forLanguageTag("tr-TR"));
        collator.setStrength(Collator.SECONDARY);
        Arrays.sort(names, collator);
        System.out.println(Arrays.toString(names));
    }
}
```

Lưu thành `LocaleSortExample.java`. Chạy với:

```bash
javac -encoding UTF-8 LocaleSortExample.java
java LocaleSortExample
```

Đây là **chương trình có thể biên dịch/chạy**, nhưng bài không cố định stdout: thứ tự hiển thị với `Collator` có thể chịu ảnh hưởng của dữ liệu so sánh theo vùng/phiên bản JDK. `Collator.SECONDARY` thường cho phép khác biệt dấu trọng âm có ý nghĩa trong khi bỏ qua khác biệt hoa/thường ở nhiều locale; quy tắc cụ thể phụ thuộc ngôn ngữ. Hãy thử nghiệm với locale, tập dữ liệu và phiên bản JDK thực tế của ứng dụng.

Để so sánh theo nhu cầu nghiệp vụ, cần quyết định thêm liệu dấu, ký tự kết hợp, khoảng trắng, ký tự đặc biệt và biến thể chính tả có được coi là ngang nhau hay không. Không có một comparator bỏ qua hoa/thường nào giải quyết tất cả những yêu cầu này.

## 10. `null`: phải chọn quy ước xử lý tường minh

`"abc".compareToIgnoreCase(null)` ném `NullPointerException`. `String.CASE_INSENSITIVE_ORDER.compare("abc", null)` cũng không cung cấp thứ tự cho `null`. Đừng trông chờ comparator mặc định tự đưa `null` lên đầu/cuối.

Khác biệt dễ bỏ sót: `"abc".equalsIgnoreCase(null)` trả `false`, vì đây là **phép kiểm tra bằng nhau**, không phải phép so thứ tự.

Khi một danh sách có thể chứa `null`, bọc comparator bằng `Comparator.nullsFirst` hoặc `Comparator.nullsLast`:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class NullSafeSortDemo {
    public static void main(String[] args) {
        List<String> words = new ArrayList<>(
                Arrays.asList("zulu", null, "Alpha", "beta"));

        Comparator<String> order =
                Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER);

        words.sort(order);
        System.out.println(words);
        System.out.println("abc".equalsIgnoreCase(null));
    }
}
```

```bash
javac -encoding UTF-8 NullSafeSortDemo.java
java NullSafeSortDemo
```

```text
[null, Alpha, beta, zulu]
false
```

`Comparator.nullsFirst` làm `null` đứng trước các chuỗi hợp lệ, `nullsLast` đưa nó về sau. Đối với hàm không cho phép `null` theo thiết kế, thường rõ ràng hơn nếu kiểm tra tham số ngay tại ranh giới API bằng `Objects.requireNonNull`.

## 11. Chọn API theo tình huống

| Nhu cầu | Chọn | Lý do |
| --- | --- | --- |
| So sánh thứ tự của đúng hai chuỗi, bỏ qua hoa/thường | `a.compareToIgnoreCase(b)` | Gọn, trả dấu thứ tự |
| Chỉ kiểm tra hai chuỗi có tương đương bỏ qua hoa/thường không | `a.equalsIgnoreCase(b)` | Diễn đạt ý định boolean trực tiếp; đối số `null` cho `false` |
| Sắp xếp list/array theo thứ tự bỏ qua hoa/thường | `String.CASE_INSENSITIVE_ORDER` | Truyền thẳng vào API nhận `Comparator<String>` |
| `TreeSet`/`TreeMap` cố ý gộp các biến thể hoa/thường | `String.CASE_INSENSITIVE_ORDER` | `compare == 0` định nghĩa cùng nhóm/khóa |
| Muốn nhóm các biến thể gần nhau nhưng giữ riêng từng cách viết | `CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder())` | Tiêu chí phụ tránh đồng nhất khóa khác nhau |
| Danh sách có `null` và cần quy định vị trí của nó | `Comparator.nullsFirst(...)` / `nullsLast(...)` | Tránh NPE, nói rõ chính sách |
| Sắp xếp theo ngôn ngữ của người dùng | `Collator.getInstance(locale)` | Quy tắc sắp xếp theo locale |
| So mã định danh giao thức hoặc khóa hệ thống với quy tắc riêng | Comparator/canonicalization được **định nghĩa theo đặc tả hệ thống** | Không mặc định rằng bỏ qua hoa/thường theo Java phù hợp cho mọi protocol |

Một ranh giới thiết kế khác: `String.CASE_INSENSITIVE_ORDER` dùng được với **`String`**. Nếu nguồn là `CharSequence` khác như `StringBuilder`, bạn phải chủ động quy định cách chuyển thành `String`, thời điểm chụp nội dung và việc dữ liệu mutable có được sửa trong lúc sắp xếp hay không. Không ép kiểu `CharSequence` sang `String` nếu đối tượng thực tế không phải `String`.

## 12. Lỗi phổ biến

1. **Nhầm `compareToIgnoreCase` trả `boolean`.** Nó trả `int`; dùng dấu âm/0/dương. Nếu chỉ hỏi bằng nhau, `equalsIgnoreCase` dễ đọc hơn.
2. **Kiểm tra `== -1` hoặc `== 1`.** API không hứa trả đúng ±1. Chỉ dùng `< 0`, `== 0`, `> 0`.
3. **Cho rằng `compare == 0` đồng nghĩa `equals == true`.** `"API"` và `"api"` là phản ví dụ rõ ràng.
4. **Dùng comparator này trong `TreeSet` rồi ngạc nhiên vì mất biến thể chữ hoa/thường.** Tập hợp có thứ tự xác định tính duy nhất bằng comparator; chọn tie-breaker nếu muốn giữ biến thể.
5. **Dùng `CASE_INSENSITIVE_ORDER` để sắp tên tiếng Việt theo từ điển.** Đây là quy tắc bỏ qua hoa/thường, không phải phép so `Locale`.
6. **Chuyển tất cả chuỗi về chữ thường trước khi so mà không chọn `Locale`.** `String.toLowerCase()` mặc định phụ thuộc locale và có thể làm biến đổi độ dài, ý nghĩa hoặc nội dung dữ liệu.
7. **Cho rằng mọi Unicode case equivalence đã được xử lý.** Phép biến đổi từng code point không phải full case folding có mở rộng hay chuẩn hóa Unicode.
8. **Đưa `null` vào sort mà không có chính sách.** Dùng `nullsFirst`/`nullsLast` hoặc loại bỏ/kiểm tra null rõ ràng.
9. **Sắp xếp rồi mong danh sách tự loại các chuỗi bằng nhau theo comparator.** `List.sort` chỉ sắp thứ tự; `TreeSet` mới có hành vi gộp phần tử bằng comparator.
10. **Tạo comparator tự viết không nhất quán hoặc phụ thuộc dữ liệu mutable.** Comparator phải tuân thủ tính phản đối xứng, bắc cầu và cho kết quả ổn định khi dùng trong cấu trúc dữ liệu có thứ tự.

## 13. Tự kiểm tra

Với chương trình dưới đây, hãy đoán kết quả trước khi chạy:

```java
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

public class CompareQuiz {
    public static void main(String[] args) {
        System.out.println("Code".compareToIgnoreCase("cODE"));
        System.out.println("Code".equals("cODE"));

        String[] values = {"b", "A", "a"};
        Arrays.sort(values, String.CASE_INSENSITIVE_ORDER);
        System.out.println(Arrays.toString(values));

        Set<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        set.add("Key");
        set.add("KEY");
        System.out.println(set.size());
    }
}
```

Lưu thành `CompareQuiz.java`, chạy:

```bash
javac -encoding UTF-8 CompareQuiz.java
java CompareQuiz
```

```text
0
false
[A, a, b]
1
```

Giải thích: hai biến thể `Code/cODE` so thứ tự bằng nhau nhưng không bằng nhau chính xác; mảng được sắp ổn định với `A` và `a` đứng gần nhau; `TreeSet` chỉ giữ một khóa khi comparator cho kết quả 0.

**Ghi nhớ:** `compareToIgnoreCase` và `String.CASE_INSENSITIVE_ORDER` dùng **cùng quy tắc thứ tự bỏ qua chữ hoa/thường**, nhưng cách đưa vào API khác nhau. Comparator ảnh hưởng trực tiếp đến cách `TreeSet`/`TreeMap` nhận diện khóa, vì vậy luôn quyết định rõ có muốn gộp các biến thể hoa/thường hay không và liệu ngôn ngữ/Unicode của bài toán có cần xử lý riêng.

## Tài liệu Java SE 17 chính thức

- [`String.compareToIgnoreCase`, `equalsIgnoreCase` và `String.CASE_INSENSITIVE_ORDER` — Oracle](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html)
- [`Comparator`, `thenComparing`, `nullsFirst`, `nullsLast` — Oracle](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html)
- [`TreeSet` — Oracle](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html)
- [`TreeMap` — Oracle](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html)
- [`Collator` — Oracle](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/Collator.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-comparison-007">contentEquals khi so sánh với StringBuilder hoặc CharSequence</a>

<details>
<summary>Xem chi tiết</summary>

# `String.contentEquals()`: so sánh nội dung với `StringBuilder` và `CharSequence`

## 1. Bài toán và ý tưởng chính

Trong Java, `String` lưu một chuỗi ký tự bất biến (*immutable*). Trong khi đó, `StringBuilder`, `StringBuffer` và nhiều kiểu khác triển khai `CharSequence` có thể cung cấp một dãy ký tự mà không phải là đối tượng `String`. Đôi khi cần hỏi: **“Dãy ký tự hiện tại của đối tượng này có giống chính xác nội dung của một `String` không?”** Đây là trường hợp sử dụng `String.contentEquals(...)`.

Ví dụ, một bộ phân tích dữ liệu đã ghép tên trường vào `StringBuilder`, và cần so với từ khóa `"status"`. Nếu gọi `"status".equals(builder)` thì kết quả là `false`, dù các ký tự trùng nhau, vì `String.equals(Object)` chỉ coi một đối tượng khác là bằng khi đối tượng đó cũng là `String` với cùng nội dung. `"status".contentEquals(builder)` mới kiểm tra trực tiếp dãy ký tự bên trong builder.

~~~java
String expected = "status";
StringBuilder actual = new StringBuilder("status");

System.out.println(expected.equals(actual));        // false
System.out.println(expected.contentEquals(actual)); // true
System.out.println(expected.equals(actual.toString())); // true
~~~

Cả hai cách cuối đều đúng về nội dung. `contentEquals(builder)` tránh việc người gọi phải tạo một `String` trung gian qua `builder.toString()`; hiệu quả thực tế phụ thuộc loại đối tượng, bản JDK, tối ưu của JVM và hoàn cảnh sử dụng.

## 2. Hai overload của `String.contentEquals()`

Lớp `java.lang.String` cung cấp:

~~~java
public boolean contentEquals(StringBuffer sb)
public boolean contentEquals(CharSequence cs)
~~~

Đây là **chữ ký API**, không phải một chương trình Java hoàn chỉnh.

- **`contentEquals(StringBuffer)`:** so nội dung `String` với `StringBuffer`. `StringBuffer` là lớp mutable có đồng bộ hóa trên các thao tác của nó; overload dành riêng cho `StringBuffer` đồng bộ hóa việc quan sát bộ đệm khi so sánh.
- **`contentEquals(CharSequence)`:** so nội dung `String` với bất kỳ đối tượng triển khai `CharSequence`, chẳng hạn `StringBuilder`, `StringBuffer`, `String`, `CharBuffer` hoặc một implementation tự viết.

`StringBuilder` **không** có overload riêng; nó được chấp nhận vì `StringBuilder implements CharSequence`. Hai overload có cùng ý nghĩa kết quả: `true` khi nội dung dãy ký tự tương ứng giống chính xác; `false` nếu khác.

`CharSequence` là một interface đọc dãy `char`, với các thao tác như `length()`, `charAt(int)`, `subSequence(...)`. Interface **không bắt buộc đối tượng bất biến**. Vì vậy kết quả `contentEquals()` phản ánh nội dung tại thời điểm được quan sát trong phép gọi, chứ không phải một cam kết đối tượng sẽ giữ nguyên trong tương lai.

~~~java
CharSequence text = new StringBuilder("Java");
StringBuffer buffer = new StringBuffer("Java");

System.out.println("Java".contentEquals(text));   // true: overload CharSequence
System.out.println("Java".contentEquals(buffer)); // true: overload StringBuffer
System.out.println("Java".contentEquals("Java")); // true: String cũng là CharSequence
~~~

Khi biến có kiểu khai báo là `CharSequence`, trình biên dịch chọn overload `contentEquals(CharSequence)`, dù đối tượng runtime có thể là `StringBuffer`. Điều quan trọng là **ngữ nghĩa so nội dung**, không phải việc buộc người gọi ép kiểu sang overload khác.

## 3. `equals()`, `contentEquals()` và `==` khác gì?

| Biểu thức | Điều kiện trả `true` | Trường hợp dùng |
|---|---|---|
| `a == b` | Cùng tham chiếu đối tượng (với kiểu tham chiếu) | Kiểm tra identity, không dùng mặc định để so văn bản |
| `a.equals(b)` khi `a` là `String` | `b` cũng là `String` có cùng dãy ký tự UTF-16 | So sánh hai String |
| `a.contentEquals(cs)` | `cs` cung cấp cùng dãy ký tự hiện tại, kể cả khi là StringBuilder | So một String với CharSequence |
| `a.equals(cs.toString())` | Kết quả `toString()` là String có nội dung bằng `a` | Hợp lý khi đã cần bản String vì mục đích khác |
| `a.equalsIgnoreCase(b)` | Hai String bằng nhau theo quy tắc bỏ qua hoa/thường của Java | So văn bản không phân biệt hoa/thường; không có phiên bản CharSequence trực tiếp |

Ví dụ với hai đối tượng `String` khác nhau:

~~~java
String a = new String("abc");
String b = new String("abc");
System.out.println(a == b);              // false
System.out.println(a.equals(b));         // true
System.out.println(a.contentEquals(b));  // true
~~~

Nếu hai đầu đều là `String`, `equals()` thường là lựa chọn rõ nhất. Khi vế kia là `StringBuilder` hoặc một `CharSequence` được xây dựng trong quá trình xử lý, `contentEquals()` diễn đạt đúng nhu cầu mà không cần chủ động chuyển về String.

**Lưu ý quan trọng:** `StringBuilder` không override `Object.equals()` để so nội dung; `new StringBuilder("abc").equals(new StringBuilder("abc"))` là `false` vì đó là hai đối tượng khác nhau. Nếu muốn so **hai** `StringBuilder`, trong Java 17 có thể dùng `builder1.compareTo(builder2) == 0` vì `StringBuilder` triển khai `Comparable<StringBuilder>`; hoặc so thông qua một `String` với `contentEquals()`. Không suy ra rằng mọi loại `CharSequence` đều có `equals()` dựa vào nội dung: interface không quy định điều đó.

## 4. So sánh `StringBuilder` khi nội dung thay đổi

`StringBuilder` mutable: các lệnh `append()`, `insert()`, `delete()`, `replace()`, `setCharAt()`, `setLength()` có thể thay dãy ký tự. Lần so sánh sau có thể trả kết quả khác lần trước:

~~~java
String expected = "abc";
StringBuilder builder = new StringBuilder("abc");

System.out.println(expected.contentEquals(builder)); // true
builder.append('d');
System.out.println(expected.contentEquals(builder)); // false
builder.deleteCharAt(3);
System.out.println(expected.contentEquals(builder)); // true
~~~

Trong quá trình parse hoặc xây dựng thông điệp, hãy so sau khi đã có nội dung cần kiểm tra. Nếu cần một **snapshot ổn định** để lưu, truyền sang luồng khác hoặc tái sử dụng lâu dài, lấy `String snapshot = builder.toString();` tại điểm kiểm soát phù hợp rồi so bằng `equals()`; không giả định `CharSequence` mutable sẽ giữ nguyên nội dung.

### So sánh toàn bộ, không phải tiền tố hay chuỗi con

`contentEquals()` kiểm tra **toàn bộ dãy ký tự**. Nó không giống `contains()`, `startsWith()` hay so khớp một đoạn:

~~~java
StringBuilder full = new StringBuilder("status=OK");
System.out.println("status".contentEquals(full)); // false
System.out.println("status=OK".contentEquals(full)); // true
System.out.println("status=OK".startsWith("status")); // true
~~~

Nếu mục đích chỉ tìm một từ khóa trong chuỗi dài, hãy dùng `contains()`, `indexOf()` hoặc bộ parser thay vì `contentEquals()`.

## 5. So sánh với kiểu khai báo `CharSequence`

Một API nhận `CharSequence` giúp chấp nhận nhiều nguồn dữ liệu mà không ép caller chuyển hết sang `String`:

~~~java
static boolean isReady(CharSequence candidate) {
    return "READY".contentEquals(candidate);
}
~~~

Có thể gọi:

~~~java
System.out.println(isReady("READY"));                     // true
System.out.println(isReady(new StringBuilder("READY")));  // true
System.out.println(isReady(new StringBuffer("READY")));   // true
System.out.println(isReady(new StringBuilder("ready")));  // false
~~~

Hai khối trên là **đoạn minh họa**: hãy đặt phương thức `isReady` trong class, và những câu lệnh `System.out.println` trong một phương thức chạy, như chương trình tổng hợp bên dưới.

Interface `CharSequence` có các implementation khác nhau:

- `String`: nội dung bất biến.
- `StringBuilder`: mutable, tối ưu cho xây dựng chuỗi trong một luồng.
- `StringBuffer`: mutable, các phương thức của buffer có đồng bộ hóa.
- `java.nio.CharBuffer`: cung cấp một vùng ký tự theo `position` và `limit`; nội dung có thể phụ thuộc trạng thái và vùng nhìn thấy hiện tại.
- Các implementation tự viết: có thể có cơ chế tạo hoặc đọc nội dung riêng; không giả định `toString()`, `equals()` và `charAt()` có tính chất vượt quá hợp đồng mà API đó công bố.

Ví dụ với một `CharBuffer` được tạo để nhìn đúng bốn ký tự:

~~~java
import java.nio.CharBuffer;

CharBuffer buffer = CharBuffer.wrap("Java");
System.out.println("Java".contentEquals(buffer));  // true
buffer.position(1);
System.out.println("Java".contentEquals(buffer));  // false: phần còn lại là "ava"
System.out.println("ava".contentEquals(buffer));   // true
~~~

`CharBuffer` coi các ký tự từ `position` đến trước `limit` là phần tử còn lại của buffer. Thay `position` có thể thay dãy ký tự mà các phương thức `CharSequence` bộc lộ; do đó không nên xem mọi `CharSequence` là một String cố định.

## 6. Null, chữ hoa/thường, Unicode và khoảng trắng

### 6.1. Null

`"abc".contentEquals((CharSequence) null)` ném `NullPointerException`. Vế String nhận cũng không được null; `String s = null; s.contentEquals(...)` ném `NullPointerException`.

Nếu null là giá trị hợp lệ trong mô hình dữ liệu, kiểm tra trước:

~~~java
static boolean sameText(String expected, CharSequence actual) {
    return expected != null && actual != null
            && expected.contentEquals(actual);
}
~~~

Đây là quy ước **null không khớp**, kể cả khi cả hai null. Nếu nghiệp vụ cần null bằng null, xác định quy tắc đó tường minh; chẳng hạn chỉ với String/String có thể dùng `Objects.equals()`. Đừng giả định `contentEquals(null)` trả `false`.

### 6.2. Chữ hoa/thường và khoảng trắng

`contentEquals()` so chính xác dãy code unit: không bỏ dấu cách, không `trim`/`strip`, không chuyển hoa/thường.

~~~java
System.out.println("Java".contentEquals(new StringBuilder("java")));   // false
System.out.println("Java".contentEquals(new StringBuilder(" Java")));  // false
System.out.println("Java".contentEquals(new StringBuilder("Java")));   // true
~~~

Nếu cần so không phân biệt case hoặc bỏ khoảng trắng, phải làm rõ quy tắc chuẩn hóa đầu vào:

~~~java
import java.util.Locale;

StringBuilder candidate = new StringBuilder(" java ");
String normalized = candidate.toString().strip().toUpperCase(Locale.ROOT);
System.out.println("JAVA".equals(normalized)); // true
~~~

`Locale.ROOT` phù hợp nhiều mã máy không phụ thuộc ngôn ngữ, nhưng **không** thay thế quy tắc so sánh từ ngữ tự nhiên của mọi locale. Chuẩn hóa cần được quyết định theo yêu cầu thực tế; không thêm tự động vào `contentEquals()`.

### 6.3. Unicode: so theo UTF-16, không tự chuẩn hóa

Cả `String` và `CharSequence` bộc lộ một dãy `char` (UTF-16 code unit). Khi dùng `contentEquals()`, hai dãy phải giống nhau chính xác. Hai biểu diễn Unicode nhìn giống nhau có thể khác:

~~~java
import java.text.Normalizer;

String composed = "\u00E9";       // é: một code point
StringBuilder decomposed = new StringBuilder("e\u0301"); // e + dấu sắc

System.out.println(composed.contentEquals(decomposed)); // false
String normalized = Normalizer.normalize(
        decomposed.toString(), Normalizer.Form.NFC);
System.out.println(composed.equals(normalized)); // true
~~~

Emoji ngoài BMP có thể dùng **hai `char`**, nhưng nếu hai dãy code unit hoàn toàn giống nhau thì `contentEquals()` vẫn trả true:

~~~java
System.out.println("🚀".length());                             // 2
System.out.println("🚀".contentEquals(new StringBuilder("🚀"))); // true
~~~

Nếu cần so ngôn ngữ tự nhiên (ví dụ quy tắc sắp xếp tên tiếng Việt, bỏ dấu, case folding), phải chọn thuật toán/locale phù hợp; `contentEquals()` chỉ so nội dung chính xác. Nó cũng **không** thay cho so sánh mật khẩu hoặc secret với yêu cầu thời gian hằng số, vì API không cam kết đặc tính chống timing side-channel.

## 7. Đồng bộ và an toàn luồng

`String` bất biến và an toàn để nhiều luồng đọc nội dung của nó. `StringBuilder` mutable và **không có đồng bộ hóa nội bộ**, nên không được sửa nó đồng thời với lời gọi `contentEquals()` ở luồng khác nếu không có biện pháp phối hợp. Đọc `length()` và từng `charAt()` trên một đối tượng đang bị sửa có thể quan sát trạng thái không nhất quán; kết quả khó dự đoán, không có bảo đảm một snapshot nguyên tử.

`StringBuffer` sử dụng đồng bộ hóa cho các thao tác của nó. Overload `String.contentEquals(StringBuffer)` đồng bộ trên đối tượng buffer trong khi thực hiện so sánh, nên có thể quan sát dãy ký tự nhất quán đối với các thay đổi cũng tuân theo khóa đó. Tuy vậy, dữ liệu **vẫn có thể thay đổi trước hoặc sau lời gọi**. Không nhầm an toàn của một phép so với một chuỗi giao dịch nhiều thao tác.

Ví dụ một đoạn có nhiều bước:

~~~java
// Đoạn minh họa, không phải giao dịch nguyên tử:
StringBuffer shared = new StringBuffer("READY");
if ("READY".contentEquals(shared)) {
    // Luồng khác vẫn có thể sửa shared ngay sau khi if được đánh giá.
    // Không được suy ra shared luôn bằng "READY" trong phần thân này.
}
~~~

Nếu cần kiểm tra và sử dụng nội dung như một giao dịch, đồng bộ một đoạn bao quanh **cả bước kiểm tra lẫn bước sử dụng** bằng cùng khóa mà mọi writer tuân theo, hoặc chuyển dữ liệu sang snapshot bất biến ở ranh giới phù hợp.

~~~java
StringBuilder builder = new StringBuilder("READY");
// Sau khi đã kết thúc tất cả thao tác ghi trên builder:
String snapshot = builder.toString();
System.out.println("READY".equals(snapshot)); // true
~~~

Nếu nhiều luồng cùng truy cập `StringBuilder`, bước tạo snapshot cũng phải được đồng bộ đúng cách; bản thân `toString()` không tự sửa tình trạng ghi song song.

## 8. Hiệu năng: khi nào tránh `toString()`?

Khi đã có một `StringBuilder` và chỉ cần **một phép so toàn bộ nội dung**, `expected.contentEquals(builder)` có thể tránh một bước tạo String rõ ràng trong code. Việc tạo `String` từ builder bằng `toString()` có thể phát sinh cấp phát hoặc sao chép dữ liệu, tùy JVM/JDK; nhưng **không** thể từ đó khẳng định `contentEquals()` luôn không cấp phát hoặc luôn nhanh hơn.

So sánh:

~~~java
String expected = "GET";

// Cách 1: so trực tiếp dãy ký tự.
boolean direct = expected.contentEquals(builder);

// Cách 2: tạo String trung gian rồi so.
boolean converted = expected.equals(builder.toString());
~~~

Trong ví dụ này `builder` được giả định đã tồn tại và chứa văn bản cần kiểm tra. Đây **chỉ là hai cách viết để thảo luận**, không phải file Java độc lập.

Quy tắc chọn thực tế:

- Nếu chỉ cần **đúng một hoặc vài phép so** với một `StringBuilder` đã có, ưu tiên `contentEquals()` vì diễn đạt trực tiếp và không bắt buộc gọi `toString()`.
- Nếu cần lưu/đưa văn bản sang API nhận `String` hay tái sử dụng snapshot nhiều lần, `String snapshot = builder.toString();` có thể hợp lý và rõ ràng hơn.
- Nếu cần so **hai String**, sử dụng `equals()`; không chuyển chúng sang builder chỉ để gọi `contentEquals()`.
- Với đoạn nhạy hiệu năng, đo dữ liệu thực tế bằng profiler hoặc JMH; tránh benchmark vòng lặp đơn giản vì JIT/escape analysis có thể loại bỏ hoặc thay đổi chi phí một cách bất ngờ.

Không sử dụng `contentEquals()` như cách thay thế đồng loạt `equals()`. Nền tảng của nó là **khả năng so trực tiếp String với CharSequence khác String**.

## 9. Chương trình hoàn chỉnh chạy trên Java 17

Đặt toàn bộ đoạn dưới vào file UTF-8 tên **`ContentEqualsDemo.java`**. Chương trình minh họa overload, thay đổi nội dung, kiểm tra toàn chuỗi, CharSequence và một ví dụ chuẩn hóa Unicode.

~~~java
import java.nio.CharBuffer;
import java.text.Normalizer;
import java.util.Locale;

public class ContentEqualsDemo {
    private static boolean isReady(CharSequence value) {
        return value != null && "READY".contentEquals(value);
    }

    public static void main(String[] args) {
        String expected = "Java";
        StringBuilder builder = new StringBuilder("Java");

        System.out.println("equalsBuilder=" + expected.equals(builder));
        System.out.println("contentBuilder=" + expected.contentEquals(builder));
        System.out.println("equalsString=" + expected.equals(builder.toString()));

        builder.append('!');
        System.out.println("afterAppend=" + expected.contentEquals(builder));
        builder.deleteCharAt(4);
        System.out.println("afterDelete=" + expected.contentEquals(builder));

        CharSequence sequence = new StringBuilder("Java");
        StringBuffer buffer = new StringBuffer("Java");
        System.out.println("asSequence=" + expected.contentEquals(sequence));
        System.out.println("asBuffer=" + expected.contentEquals(buffer));

        CharBuffer charBuffer = CharBuffer.wrap("Java");
        System.out.println("charBufferFull=" + expected.contentEquals(charBuffer));
        charBuffer.position(1);
        System.out.println("charBufferSuffix=" + "ava".contentEquals(charBuffer));

        System.out.println("ready=" + isReady(new StringBuilder("READY")));
        System.out.println("nullReady=" + isReady(null));
        System.out.println("caseSensitive=" +
                "Java".contentEquals(new StringBuilder("java")));

        String normalized = new StringBuilder(" java ").toString()
                .strip().toUpperCase(Locale.ROOT);
        System.out.println("normalized=" + "JAVA".equals(normalized));

        String composed = "\u00E9";
        StringBuilder decomposed = new StringBuilder("e\u0301");
        System.out.println("unicodeDirect=" +
                composed.contentEquals(decomposed));
        System.out.println("unicodeNFC=" +
                composed.equals(Normalizer.normalize(
                        decomposed.toString(), Normalizer.Form.NFC)));
    }
}
~~~

Biên dịch bằng JDK 17+ với mã nguồn UTF-8 và chạy:

~~~bash
javac -encoding UTF-8 ContentEqualsDemo.java
java ContentEqualsDemo
~~~

Kết quả stdout chính xác:

~~~text
equalsBuilder=false
contentBuilder=true
equalsString=true
afterAppend=false
afterDelete=true
asSequence=true
asBuffer=true
charBufferFull=true
charBufferSuffix=true
ready=true
nullReady=false
caseSensitive=false
normalized=true
unicodeDirect=false
unicodeNFC=true
~~~

Giải thích quan trọng: `equalsBuilder=false` vì `String.equals(Object)` không coi `StringBuilder` là String. `contentBuilder=true` vì dãy ký tự trùng. Append một `!` làm độ dài khác, nên so thất bại; xóa nó thì bằng lại. `CharBuffer` trả dãy từ vị trí hiện tại tới giới hạn; khi chuyển vị trí sang 1, phần nội dung còn lại là `"ava"`. Chuẩn hóa Unicode NFC cho biểu diễn `e + dấu sắc` về biểu diễn tương đương với `é`.

## 10. Những lỗi phổ biến

| Sai lầm | Hậu quả | Cách làm đúng |
|---|---|---|
| `"abc".equals(new StringBuilder("abc"))` | Luôn false với StringBuilder | `"abc".contentEquals(builder)` |
| `a == b` để kiểm tra văn bản | So identity, không so nội dung | `equals()` hoặc `contentEquals()` phù hợp |
| Gọi `contentEquals(null)` | `NullPointerException` | Null-check trước |
| Coi kết quả so builder là vĩnh viễn | Builder có thể bị sửa sau đó | So tại điểm dùng hoặc tạo snapshot |
| Sửa builder cùng lúc so ở thread khác | Race, trạng thái không ổn định | Phối hợp đồng bộ hoặc snapshot an toàn |
| Mong bỏ qua hoa/thường/space | `contentEquals()` so chính xác | Chuẩn hóa có chủ đích rồi so |
| Mong Unicode nhìn giống sẽ bằng nhau | Hai dãy code point/code unit có thể khác | Chuẩn hóa phù hợp khi nghiệp vụ yêu cầu |
| Mong `contentEquals()` tìm chuỗi con | Nó kiểm tra cả dãy | `contains()`/`indexOf()` hoặc parser |
| Gọi `toString()` trong mọi phép so chỉ vì muốn dùng `equals()` | Có thể tạo bước chuyển đổi không cần thiết | Dùng `contentEquals()` trực tiếp khi chỉ cần so |
| Tin rằng `contentEquals()` luôn nhanh hơn | Tối ưu phụ thuộc trường hợp và JVM | Benchmark/profiler trên tải thật |

## 11. Ghi nhớ khi lựa chọn API

1. **Hai đối tượng là `String`:** `equals()` cho so nội dung chính xác; `equalsIgnoreCase()` nếu yêu cầu so bỏ qua hoa/thường và đã cân nhắc locale/ngữ nghĩa.
2. **Một `String`, một `StringBuilder`/`StringBuffer`/`CharSequence`:** `String.contentEquals()` cho so trực tiếp toàn bộ dãy.
3. **Cần một snapshot bất biến từ builder:** chuyển bằng `toString()` ở thời điểm kiểm soát phù hợp rồi so `equals()`; với dữ liệu dùng chung nhiều luồng, bảo đảm không ghi đồng thời thiếu đồng bộ.
4. **Cần tìm một đoạn:** sử dụng `contains()`, `indexOf()`, `startsWith()` hoặc regex tùy nhu cầu.
5. **Có dữ liệu null hoặc Unicode phức tạp:** đặt quy tắc null, case, khoảng trắng và normalization rõ ràng trước khi gọi API.

`contentEquals()` hữu ích chính vì **so trực tiếp nội dung hiện tại của một `CharSequence` với một `String`**, kể cả khi hai đối tượng thuộc các kiểu khác nhau. Nó không tự chuẩn hóa văn bản, không thay thế bảo đảm an toàn luồng và không biến `equals()` thành thừa.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-comparison-008">regionMatches để so sánh một vùng chuỗi</a>

<details>
<summary>Xem chi tiết</summary>

# regionMatches() để so sánh một vùng chuỗi trong Java

## 1. Mục tiêu và tình huống sử dụng

Khi so sánh hai chuỗi, đôi khi bạn **không cần so sánh toàn bộ nội dung**. Ví dụ: kiểm tra bốn ký tự ở giữa hai mã định danh có trùng nhau không; so sánh một đoạn bắt đầu ở vị trí xác định trong văn bản; hoặc kiểm tra một vùng mà **không phân biệt chữ hoa/chữ thường**.

`String.regionMatches()` làm đúng việc này: so sánh **hai vùng liên tiếp có cùng độ dài** được chỉ định bằng offset và số đơn vị ký tự. Phương thức trả `boolean`, **không sửa chuỗi nguồn** và không trả một chuỗi con mới.

Sau bài này, bạn có thể:

1. Dùng đúng cả **hai overload** của `regionMatches()`.
2. Xác định đúng `toffset`, `ooffset` và `len`; phân biệt **vị trí bắt đầu**, **độ dài** và **chỉ số kết thúc**.
3. Dự đoán `true`/`false` khi vùng trùng, khác ký tự, vượt biên hoặc độ dài bằng `0`.
4. Hiểu rõ `ignoreCase`, giới hạn Unicode/locale và đơn vị chỉ số UTF-16.
5. So sánh với `equals()`, `equalsIgnoreCase()`, `startsWith()`, `substring()` và `matches()`.
6. Viết hàm bao ngoài để kiểm soát `null`, tham số âm và phạm vi trước khi dùng trong nghiệp vụ.

Kiến thức cần có: `String`, chỉ số từ `0`, `length()`, `charAt()`, `boolean` và lời gọi phương thức. Ví dụ độc lập dùng **Java 17+**.

## 2. Hai overload và ý nghĩa từng tham số

Java cung cấp hai dạng:

~~~java
public boolean regionMatches(int toffset,
                             String other,
                             int ooffset,
                             int len);

public boolean regionMatches(boolean ignoreCase,
                             int toffset,
                             String other,
                             int ooffset,
                             int len);
~~~

Cú pháp tổng quát:

~~~java
left.regionMatches(leftOffset, right, rightOffset, length);
left.regionMatches(ignoreCase, leftOffset, right, rightOffset, length);
~~~

Trong đó:

| Tham số | Kiểu | Vai trò |
| --- | --- | --- |
| `left` (đối tượng nhận lời gọi) | `String` | Chuỗi thứ nhất (`this`) |
| `toffset` / `leftOffset` | `int` | Chỉ số bắt đầu của vùng trong `left` |
| `other` / `right` | `String` | Chuỗi thứ hai |
| `ooffset` / `rightOffset` | `int` | Chỉ số bắt đầu của vùng trong `right` |
| `len` / `length` | `int` | **Số đơn vị UTF-16** cần so ở mỗi vùng, không phải chỉ số kết thúc |
| `ignoreCase` | `boolean` | `true`: bỏ qua khác biệt chữ hoa/chữ thường theo quy tắc của `Character`; `false`: so chính xác |

Bản bốn tham số so sánh chính xác (phân biệt case). Bản năm tham số cho phép chọn hành vi case bằng `ignoreCase`.

**Điểm cần nhớ:** `len` là cùng một độ dài áp dụng cho hai vùng; phương thức không cho một bên dài hơn bên còn lại rồi tự cắt cho vừa.

## 3. Đọc offset bằng hình ảnh

Xét hai chuỗi:

~~~text
left  = "prefix-Java-END"
         012345678901234
                ^^^^
                vị trí 7..10 = Java

right = "Start-Java-OK"
         0123456789012
               ^^^^
               vị trí 6..9 = Java
~~~

Ta so **bốn đơn vị UTF-16** bắt đầu ở vị trí `7` của `left` và vị trí `6` của `right`:

~~~java
String left = "prefix-Java-END";
String right = "Start-Java-OK";

boolean same = left.regionMatches(7, right, 6, 4);
System.out.println(same); // true
~~~

Hai vùng đều là `"Java"` nên kết quả `true`, mặc dù hai chuỗi **toàn phần không giống nhau**.

Nếu tăng `len` lên `6`, vùng so sánh trở thành `"Java-E"` và `"Java-O"`, kết quả `false`:

~~~java
System.out.println(left.regionMatches(7, right, 6, 6)); // false
~~~

**Không nhầm với `substring(begin, end)`:** phương thức `substring(7, 11)` có tham số thứ hai là **chỉ số kết thúc loại trừ**, còn `regionMatches(7, right, 6, 4)` có tham số cuối là **độ dài 4**. Nhầm hai kiểu ký hiệu là lỗi rất thường gặp.

## 4. So khớp chính xác: bốn tham số

Cú pháp:

~~~java
boolean result = first.regionMatches(
        firstOffset, second, secondOffset, count);
~~~

Các vị trí được so theo thứ tự từ trái sang phải. Với phiên bản bốn tham số, hai vùng cần có cùng chuỗi **UTF-16 code units**.

~~~java
String a = "--JAVA--";
String b = "xxJAVAyy";
String c = "xxJavaYY";

System.out.println(a.regionMatches(2, b, 2, 4)); // true
System.out.println(a.regionMatches(2, c, 2, 4)); // false
~~~

Chữ hoa `"JAVA"` và `"Java"` khác nhau ở các ký tự tương ứng nên không khớp trong chế độ chính xác.

Nếu chỉ muốn biết toàn bộ chuỗi có cùng nội dung, hãy dùng `equals()`:

~~~java
boolean allEqual = a.equals(b);              // false
boolean areaEqual = a.regionMatches(2, b, 2, 4); // true
~~~

Đừng dùng `regionMatches()` thay `equals()` khi yêu cầu là so **toàn bộ** nội dung. Một phép so vùng có thể cho `true` trong khi phần đầu, phần cuối hoặc độ dài tổng thể khác nhau.

## 5. Bỏ qua chữ hoa/chữ thường: năm tham số

Phiên bản năm tham số bổ sung cờ `ignoreCase` ở đầu:

~~~java
String left = "prefix-Java-END";
String right = "Start-jAvA-OK";

System.out.println(left.regionMatches(7, right, 6, 4));
// false

System.out.println(left.regionMatches(false, 7, right, 6, 4));
// false

System.out.println(left.regionMatches(true, 7, right, 6, 4));
// true
~~~

Ý nghĩa:

- `ignoreCase = false`: kiểm tra các vùng khớp **chính xác**.
- `ignoreCase = true`: kiểm tra tương ứng **không phân biệt case** theo cơ chế của `Character`; `'J'` và `'j'` có thể được coi là tương đương.

Không cần tạo hai chuỗi viết thường bằng `toLowerCase()` trước khi gọi khi nhu cầu chỉ là so một vùng với quy tắc của `regionMatches(true, ...)`.

### Không đồng nghĩa so sánh văn bản theo ngôn ngữ tự nhiên

`regionMatches(true, ...)` **không sử dụng `Locale`** và không thay thế thuật toán *collation* cho mọi ngôn ngữ. Theo Javadoc Java 17, dạng này so sánh các chuỗi **Unicode code points** ở trong vùng; khi bỏ qua case, cơ chế dựa trên phép đổi case bằng `Character.toUpperCase(int)` rồi `Character.toLowerCase(int)`.

Do đó, `ignoreCase`:

- Không phải thuật toán tìm kiếm và so sánh có quy tắc ngôn ngữ cụ thể.
- Không tự bỏ dấu thanh tiếng Việt hoặc tự chuẩn hóa Unicode.
- Không bảo đảm xử lý tất cả quy tắc *full case folding* có thể khiến một chữ biến thành nhiều code points.
- Không cho phép hai vùng có độ dài UTF-16 khác nhau.

Nếu nghiệp vụ yêu cầu thứ tự hay sự tương đương theo locale, cân nhắc `java.text.Collator` hoặc một thư viện xử lý văn bản phù hợp; các yêu cầu an ninh/mã định danh có thể cần **so sánh chính xác** thay vì bỏ qua case.

## 6. Điều kiện biên: offset, độ dài và vùng rỗng

Với `len >= 0`, cách nghĩ an toàn nhất là hai khoảng chỉ số nửa mở phải hợp lệ:

~~~text
Vùng trái: [toffset, toffset + len)
Vùng phải: [ooffset, ooffset + len)
~~~

Khoảng `[a, b)` lấy vị trí `a` nhưng **không lấy** vị trí `b`.

Các điều kiện hợp lệ theo cách kiểm tra chủ động:

~~~text
toffset >= 0
ooffset >= 0
len >= 0
toffset <= left.length()
ooffset <= right.length()
len <= left.length() - toffset
len <= right.length() - ooffset
~~~

Khi một offset âm hoặc vùng vượt khỏi độ dài chuỗi, phương thức trả `false` (với `other` hợp lệ); khác `substring()` vốn thường ném ngoại lệ khi chỉ số sai.

Ví dụ:

~~~java
String a = "Java";
String b = "JAVA";

System.out.println(a.regionMatches(0, b, 0, 1));  // true
System.out.println(a.regionMatches(1, b, 1, 3));  // false
System.out.println(a.regionMatches(-1, b, 0, 2)); // false
System.out.println(a.regionMatches(0, b, 0, 5));  // false
System.out.println(a.regionMatches(4, b, 4, 0));  // true
~~~

Dòng cuối so hai **vùng rỗng** tại vị trí cuối chuỗi; với hai offset hợp lệ và `len = 0`, không có ký tự nào để khác nhau, nên trả `true`.

**Quan trọng:** dùng `len >= 0` như một tiền điều kiện của chương trình. Không dựa vào hành vi với độ dài âm vì đó không phải một yêu cầu so vùng có ý nghĩa và kết quả có thể bất ngờ. Khi nhận offset từ người dùng hoặc dữ liệu không tin cậy, xác minh tham số trước khi gọi.

### Vì sao nên kiểm tra bằng phép trừ?

Trong hàm tự viết, điều kiện:

~~~java
len <= text.length() - offset
~~~

thường dễ bảo vệ hơn `offset + len <= text.length()` nếu offset/len đến từ các số nguyên bất kỳ, vì phép cộng số nguyên có thể **tràn số**. Trước khi trừ, cần chắc chắn `offset` nằm trong `[0, text.length()]`.

### null không được coi là false thông thường

~~~java
String source = "Java";
String other = null;

// source.regionMatches(0, other, 0, 2); // NullPointerException
~~~

Đối số `other == null` gây `NullPointerException`; receiver `source == null` cũng không thể gọi phương thức. Hãy quyết định hợp đồng cho `null`: từ chối bằng ngoại lệ rõ ràng, hoặc trả `false` qua một hàm bao ngoài tự viết.

## 7. Unicode: offset/len đếm UTF-16, không đếm ký tự nhìn thấy

Cả `toffset`, `ooffset` và `len` được tính theo **UTF-16 code units** (đơn vị `char` 16 bit). Một Unicode code point ngoài BMP, chẳng hạn emoji `😀`, được biểu diễn bằng **surrogate pair** gồm hai đơn vị UTF-16.

~~~java
String text = "A😀Z";

System.out.println(text.length()); // 4
System.out.println(text.codePointCount(0, text.length())); // 3
~~~

Sơ đồ:

~~~text
Hiển thị:          A      😀       Z
UTF-16:           [A] [high][low] [Z]
Chỉ số:            0    1    2    3
Độ dài UTF-16:                   4
Code points:                     3
~~~

Để so **toàn bộ emoji**, `len` là `2`, không phải `1`:

~~~java
String a = "A😀Z";
String b = "X😀Y";
System.out.println(a.regionMatches(1, b, 1, 2)); // true
~~~

Nếu `len = 1` thì chỉ một nửa surrogate pair được đưa vào vùng so. Điều này có thể tạo kết quả trông đúng nhưng **không phản ánh so sánh trọn một emoji**:

~~~java
String a = "A😀Z";
String b = "X😁Y";

// Hai emoji khác nhau nhưng high surrogate giống nhau.
System.out.println(a.regionMatches(1, b, 1, 1)); // true
System.out.println(a.regionMatches(1, b, 1, 2)); // false
~~~

**Bài học:** cần đặt ranh giới vùng tại **đầu/cuối một code point**, tránh cắt giữa surrogate pair. Khi cần tính chỉ số theo code points, dùng `offsetByCodePoints()` để đổi thành offset UTF-16 phù hợp trước khi gọi:

~~~java
String text = "A😀Z";
int start = text.offsetByCodePoints(0, 1); // 1
int end = text.offsetByCodePoints(start, 1); // 3
int len = end - start; // 2 UTF-16 code units

System.out.println(text.regionMatches(start, "😀", 0, len)); // true
~~~

**Lưu ý khác:** code point vẫn không nhất thiết là một *grapheme cluster* (ký tự người dùng nhìn thấy). Ví dụ `"é"` có thể biểu diễn bằng một code point hoặc `"e\u0301"` (e + dấu sắc kết hợp). Hai chuỗi đó không có cùng dãy UTF-16 code units; `regionMatches()` không tự chuẩn hóa chúng. Nếu nghiệp vụ cần sự tương đương chuẩn hóa, xem `java.text.Normalizer` và quy tắc so sánh đã thỏa thuận.

## 8. Khi nào dùng regionMatches thay vì các API khác?

| Mục đích | API phù hợp | Lý do |
| --- | --- | --- |
| So **hai chuỗi toàn phần** chính xác | `a.equals(b)` | Rõ nghĩa, không cần tính offset |
| So hai chuỗi toàn phần không phân biệt case | `a.equalsIgnoreCase(b)` | Diễn đạt trực tiếp |
| So **hai vùng bất kỳ** trong hai chuỗi | `a.regionMatches(offsetA, b, offsetB, len)` | Có offset cho cả hai bên |
| So hai vùng, bỏ qua case | `a.regionMatches(true, offsetA, b, offsetB, len)` | Có cờ `ignoreCase` |
| Kiểm tra tiền tố tại một vị trí trong một chuỗi | `a.startsWith(prefix, offset)` | Diễn đạt rõ hơn nếu vùng ở chuỗi so sánh luôn bắt đầu tại 0 |
| Kiểm tra hậu tố | `a.endsWith(suffix)` | Không cần tự tính vị trí bắt đầu |
| Trích vùng để dùng như một giá trị `String` | `a.substring(begin, end)` | Trả chuỗi con thay vì boolean |
| Tìm vị trí của đoạn không biết nằm ở đâu | `a.indexOf(fragment)` | Trả vị trí hoặc `-1` |
| Khớp biểu thức chính quy | `Pattern`/`Matcher` hay `matches()` | Dùng khi mẫu là regex, không phải so hai vùng ở vị trí biết trước |

Ví dụ so tiền tố bắt đầu tại vị trí thứ ba:

~~~java
String log = "--ERROR: disk full";
String prefix = "ERROR";

System.out.println(log.regionMatches(2, prefix, 0, prefix.length())); // true
System.out.println(log.startsWith(prefix, 2)); // true
~~~

Cả hai phù hợp; trong trường hợp chỉ kiểm tra một `prefix`, `startsWith(prefix, 2)` thường dễ đọc hơn.

So với:

~~~java
String a = "prefix-Java-END";
String b = "Start-Java-OK";

boolean same = a.substring(7, 11).equals(b.substring(6, 10));
~~~

Kết quả cũng `true` nếu chỉ số hợp lệ. Nhưng cách này tạo **giá trị chuỗi con** để so; `regionMatches()` diễn đạt trực tiếp thao tác so vùng và không yêu cầu tạo chuỗi con tại phía người gọi. Đừng mặc định nó luôn nhanh hơn trong mọi môi trường; đo thực tế nếu đoạn code là điểm nghẽn.

## 9. Ví dụ chạy độc lập Java 17+

Tạo file **`RegionMatchesDemo.java`**:

~~~java
public class RegionMatchesDemo {
    public static void main(String[] args) {
        String left = "prefix-Java-END";
        String right = "Start-Java-OK";
        String mixed = "Start-jAvA-OK";

        System.out.println(left.regionMatches(7, right, 6, 4));
        System.out.println(left.regionMatches(7, right, 6, 6));
        System.out.println(left.regionMatches(7, mixed, 6, 4));
        System.out.println(left.regionMatches(true, 7, mixed, 6, 4));
        System.out.println(left.regionMatches(false, 7, mixed, 6, 4));

        System.out.println(left.regionMatches(-1, right, 6, 4));
        System.out.println(left.regionMatches(7, right, 6, 99));
        System.out.println(left.regionMatches(
                left.length(), right, right.length(), 0));

        String smile = "A😀Z";
        String grin = "X😁Y";
        System.out.println(smile.regionMatches(1, grin, 1, 1));
        System.out.println(smile.regionMatches(1, grin, 1, 2));

        System.out.println(left.startsWith("Java", 7));
        System.out.println(left.equals(right));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac RegionMatchesDemo.java
java RegionMatchesDemo
~~~

Stdout **chính xác**:

~~~text
true
false
false
true
false
false
false
true
true
false
true
false
~~~

Giải thích theo từng nhóm:

1. `"Java"` tại offset `7` của `left` trùng `"Java"` tại offset `6` của `right`: `true`.
2. So sáu đơn vị gồm `"Java-E"` và `"Java-O"` khác nhau: `false`.
3. Chuỗi `mixed` có `"jAvA"` nên chế độ phân biệt case: `false`; `ignoreCase = true`: `true`; `false`: `false`.
4. Offset âm hoặc `len` quá lớn: `false`; vùng rỗng tại cuối hai chuỗi: `true`.
5. Hai emoji `😀` và `😁` có **high surrogate giống nhau**, nên so `len = 1` (chỉ high surrogate) ra `true`; so đủ `len = 2` ra `false`.
6. `startsWith("Java", 7)` trả `true`, nhưng `equals(right)` trả `false` vì toàn bộ chuỗi khác nhau.

Chương trình là một file độc lập, dùng đúng tên lớp và tên file, không cần thư viện ngoài JDK.

## 10. Viết hàm so vùng có kiểm tra đầu vào

Trong API nhận offset/len từ bên ngoài, bạn có thể công bố một hợp đồng rõ ràng: **trả `false` nếu chuỗi nào `null`, nếu `len` âm hoặc vùng vượt biên**.

~~~java
public final class RegionUtils {
    private RegionUtils() {}

    public static boolean sameRegion(
            String left, int leftOffset,
            String right, int rightOffset,
            int len, boolean ignoreCase) {

        if (left == null || right == null || len < 0
                || leftOffset < 0 || rightOffset < 0) {
            return false;
        }
        if (leftOffset > left.length()
                || rightOffset > right.length()) {
            return false;
        }
        if (len > left.length() - leftOffset
                || len > right.length() - rightOffset) {
            return false;
        }
        return left.regionMatches(
                ignoreCase, leftOffset, right, rightOffset, len);
    }
}
~~~

Đây là **đoạn lớp tiện ích** độc lập về mặt mã nguồn Java; nếu bạn muốn lưu nó riêng, tạo file `RegionUtils.java`. Mỗi điều kiện có mục đích:

- Kiểm tra `null` để không ném `NullPointerException` ngoài ý muốn.
- Yêu cầu `len >= 0` vì độ dài âm không có ý nghĩa trong hợp đồng đã chọn.
- Kiểm tra offset theo độ dài từng chuỗi.
- So sánh với `length() - offset` để tránh phép cộng có thể tràn `int`.
- Cuối cùng mới ủy quyền cho `regionMatches()`.

Kiểm thử hàm:

~~~java
System.out.println(RegionUtils.sameRegion(
        "prefix-Java", 7, "JAVA", 0, 4, true)); // true
System.out.println(RegionUtils.sameRegion(
        null, 0, "JAVA", 0, 4, true));          // false
System.out.println(RegionUtils.sameRegion(
        "Java", 0, "Java", 0, -1, false));      // false
~~~

**Không có chính sách null duy nhất đúng cho mọi chương trình.** Một API khác có thể chọn ném `IllegalArgumentException` hoặc `NullPointerException` để chỉ ra lỗi người gọi. Quan trọng là hợp đồng rõ và kiểm thử nhất quán.

## 11. Độ phức tạp và cách sử dụng hiệu quả

Ở góc nhìn thuật toán, so hai vùng `len` đơn vị UTF-16 cần tối đa công việc tăng theo độ dài vùng, thường mô tả là **O(len)**; nếu gặp khác biệt sớm, phép so có thể dừng sớm. Bộ nhớ bổ sung ở cách sử dụng thông thường là **O(1)** vì không cần tạo hai chuỗi con riêng.

Tuy nhiên, không nên suy luận rằng mọi phiên bản JDK có cùng vi tối ưu hoặc cùng thời gian chạy cho một trường hợp cụ thể; Unicode case folding có chi phí xử lý riêng.

Thực hành hiệu quả:

- Dùng `regionMatches()` khi **đã biết vị trí bắt đầu** ở cả hai chuỗi.
- Nếu phải tìm vị trí chưa biết, dùng `indexOf()` hoặc thuật toán tìm kiếm phù hợp trước.
- Nếu cần **so toàn bộ** thì `equals()`/`equalsIgnoreCase()` sáng nghĩa hơn.
- Nếu cần so văn bản theo locale hoặc tương đương Unicode chuẩn hóa, định nghĩa yêu cầu nghiệp vụ trước khi chọn API.
- Khi so dữ liệu bí mật và cần kháng timing attacks, không giả định `regionMatches()` là phép so thời gian cố định; nó không được thiết kế để bảo đảm thuộc tính đó.

## 12. Bài tập theo mức độ

### Bài 1 — Đọc vị trí

Cho `left = "abc-JAVA-xyz"` và `right = "12-JAVA-45"`. Hãy tính offset cho hai vùng `"JAVA"` và dùng `regionMatches()` so đúng bốn đơn vị. Dự đoán kết quả trước khi chạy.

### Bài 2 — Phân biệt case

So vùng `"Java"` với `"jAvA"` lần lượt bằng dạng bốn và năm tham số. Giải thích vì sao `ignoreCase = true` thay đổi kết quả.

### Bài 3 — Tham số hợp lệ

Với hai chuỗi `"abc"` và `"ABC"`, thử `toffset = -1`, `ooffset = -1`, `len = 4`, rồi `toffset = 3`/`ooffset = 3`/`len = 0`. Ghi lý do trả `false` hoặc `true`; không nhầm vùng rỗng với vùng vượt biên.

### Bài 4 — So bằng substring và regionMatches

Viết hai cách so vùng tương ứng trong `"prefix-Java-END"` và `"Start-Java-OK"`. Đặt tên biến phù hợp để người đọc không nhầm `len` với chỉ số kết thúc của `substring()`. Chỉ ra vì sao `regionMatches()` không cần tạo chuỗi con ở mã gọi.

### Bài 5 — Một ký tự emoji khác nhau

Cho `"A😀B"` và `"X😁Y"`. Thử `len = 1` và `len = 2` tại offset `1`. Giải thích bằng UTF-16 surrogate pair. Tính lại chỉ số khi nối thêm một emoji vào đầu chuỗi.

### Bài 6 — Tạo helper an toàn

Tự cài hàm `sameRegion(...)` nhận đầu vào có thể `null`. Viết ít nhất sáu trường hợp kiểm thử: hai vùng giống, vùng khác, ignoreCase, null, offset âm, vượt biên, vùng rỗng. Giải thích vì sao kiểm tra `len > length - offset` hữu ích hơn kiểm tra cộng trong code nhận số nguyên từ ngoài.

### Bài 7 — Chọn API đúng

Với ba yêu cầu: "hai tên đầy đủ giống nhau", "chuỗi bắt đầu bằng `ERROR` tại vị trí `3`", và "vùng giữa hai đường dẫn giống nhau", hãy chọn lần lượt `equals()`, `startsWith(prefix, offset)`, hoặc `regionMatches()` và giải thích sự khác biệt.

### Bài 8 — Unicode normalization

So `"é"` với `"e\u0301"` theo `regionMatches()`. Sau đó dùng `java.text.Normalizer.normalize(..., Normalizer.Form.NFC)` cho cả hai và so lại. Giải thích vì sao hình thức hiển thị tương tự không đồng nghĩa cùng dãy đơn vị UTF-16.

## 13. Những lỗi phổ biến và checklist

| Lỗi | Hậu quả | Cách tránh |
| --- | --- | --- |
| Nhầm `len` với chỉ số kết thúc | So sai vùng hoặc vượt biên | Nhớ vùng là `[offset, offset + len)` |
| Dùng offset bắt đầu từ `1` như số thứ tự | Lệch một ký tự | Chỉ số Java bắt đầu từ `0` |
| Mong `regionMatches()` so toàn bộ hai chuỗi | Có thể trả `true` khi phần khác nhau nằm ngoài vùng | Dùng `equals()` cho so toàn bộ |
| Dùng bản bốn tham số và mong bỏ qua case | `"Java"` khác `"java"` | Dùng dạng năm tham số với `ignoreCase = true` |
| Giả định `ignoreCase` là so sánh theo locale | Sai trong một số ngôn ngữ và quy tắc nghiệp vụ | Xác định locale, dùng API collation khi phù hợp |
| Cho rằng `len = 1` là một emoji | Surrogate pair có thể cần hai `char` | Đặt cận theo UTF-16 của code point |
| Truyền `other = null` | Có thể ném NPE | Kiểm tra null trước theo contract |
| Dùng `len < 0` và mong một kết quả có ý nghĩa | Yêu cầu so vùng không hợp lệ | Từ chối độ dài âm trong helper |
| Dùng `substring()` với cận sai khi chỉ muốn so | Có thể ném ngoại lệ hoặc tạo giá trị trung gian | Dùng `regionMatches()` khi vị trí biết trước |
| Tin rằng phương thức chuẩn hóa Unicode | Hai cách mã hóa cùng hình thức có thể khác | Chuẩn hóa trước nếu nghiệp vụ yêu cầu |
| Dùng nó làm phép so dữ liệu mật constant-time | Không có bảo đảm thời gian cố định | Dùng API phù hợp với bài toán bảo mật |

**Checklist trước khi gọi:**

1. Có đúng **hai chuỗi** và xác định được **hai offset**?
2. `len` là số **UTF-16 code units**, không phải chỉ số kết thúc?
3. Hai đoạn đủ độ dài? Có xử lý `null` và tham số âm?
4. Có cần bỏ qua case? Nếu có, quy tắc không phụ thuộc locale của `regionMatches(true, ...)` có đáp ứng nghiệp vụ không?
5. Nếu có emoji hoặc ký tự kết hợp, vùng có đặt cận trên đơn vị Unicode thích hợp không?
6. Kết quả `boolean` đã được kiểm tra/thực sự sử dụng chưa?

**Quy tắc nhớ:** `regionMatches()` so sánh **một vùng trong chuỗi hiện tại** với **một vùng cùng độ dài trong chuỗi khác**, trả `true/false`, không sửa dữ liệu. Chỉ số và độ dài tính theo **UTF-16**; cờ `ignoreCase` điều khiển cách so chữ hoa/chữ thường, **không thay thế so sánh ngôn ngữ tự nhiên**.

## 14. Tài liệu tra cứu chuẩn

- [Java SE 17 — String.regionMatches(int, String, int, int)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html#regionMatches(int,java.lang.String,int,int)): overload chính xác, ý nghĩa offset/len.
- [Java SE 17 — String.regionMatches(boolean, int, String, int, int)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html#regionMatches(boolean,int,java.lang.String,int,int)): overload có ignoreCase, quy tắc Unicode/locale.
- [Java SE 17 — String](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html): `length()`, `offsetByCodePoints()`, `equals()`, `startsWith()` và `substring()`.
- [Java SE 17 — Character](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html): Unicode code points, surrogate pairs và chuyển chữ hoa/chữ thường.
- [Java SE 17 — Collator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/Collator.html), [Normalizer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/Normalizer.html): so sánh phụ thuộc locale và chuẩn hóa khi nghiệp vụ cần.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-comparison-009">Quan hệ giữa equals, hashCode và việc hai chuỗi băm trùng</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ giữa equals, hashCode và việc hai chuỗi băm trùng trong Java

Khi so sánh `String` trong Java, `equals()` trả lời câu hỏi **“hai chuỗi có cùng nội dung không?”**; `hashCode()` tính một **giá trị băm `int`** từ nội dung để hỗ trợ các cấu trúc dữ liệu như `HashMap` và `HashSet`. Hai chức năng liên quan chặt chẽ theo một hợp đồng quan trọng:

> Nếu hai `String` bằng nhau theo `equals()`, chúng **bắt buộc** có cùng `hashCode()`. Chiều ngược lại **không đúng**: hai chuỗi **khác nhau** hoàn toàn có thể có giá trị băm trùng (*hash collision*).

Hiểu hợp đồng này giúp tránh nhầm lẫn giữa **định danh**, **so sánh nội dung** và **băm để tìm kiếm**; cũng giải thích vì sao các bộ sưu tập dạng hash vẫn phân biệt được hai chuỗi khác nhau có cùng giá trị băm.

## 1. equals() so sánh điều gì?

`String.equals(Object other)` trả về `true` khi `other` là một đối tượng `String` có cùng **dãy đơn vị mã UTF-16** (cùng chiều dài và cùng giá trị `char` tại từng vị trí). So sánh phân biệt chữ hoa/thường. Ví dụ:

~~~java
String a = "Java";
String b = new String("Java");
String c = "java";

System.out.println(a.equals(b));  // true
System.out.println(a.equals(c));  // false
System.out.println(a == b);       // false
~~~

Đây là **đoạn mã minh họa** đặt trong phương thức Java. `a.equals(b)` bằng `true` vì nội dung giống nhau; `a == b` bằng `false` vì `new String("Java")` tạo một đối tượng riêng. Toán tử `==` với hai tham chiếu đối tượng hỏi chúng có trỏ đến **cùng đối tượng** không; đó không phải phép kiểm tra bằng nhau về nội dung chuỗi.

Lưu ý:

- `equals()` trả về `false` khi đối số `null` hoặc không phải `String` phù hợp.
- Gọi `nullReference.equals(...)` sẽ gây `NullPointerException`. Nếu có thể chứa `null`, dùng `java.util.Objects.equals(a, b)` để so sánh null-safe.
- `String` bất biến: nội dung đã tạo không đổi; vì vậy kết quả `equals()` và `hashCode()` của một đối tượng `String` ổn định suốt vòng đời đối tượng.
- `equalsIgnoreCase()` là **một cách so sánh khác**: nó không biến hai chuỗi khác hoa/thường thành bằng nhau theo `equals()`.

### “Cùng nội dung” trong Java chưa chắc là “trông giống nhau”

`equals()` so sánh dãy UTF-16, không tự chuẩn hóa Unicode. Ví dụ chữ `é` ghép sẵn (`U+00E9`) và chữ `e` cộng dấu sắc kết hợp (`U+0065 U+0301`) có thể trông giống nhau nhưng có dãy code unit khác nhau. Tương tự, `equals()` không tự bỏ khoảng trắng hay không tự chuyển chữ hoa thành chữ thường. Nếu nghiệp vụ muốn coi những cách biểu diễn đó là tương đương, cần **quy định chuẩn hóa/so sánh riêng** trước khi dùng làm khóa.

## 2. hashCode() của String được tính như thế nào?

Theo hợp đồng API Java, `String.hashCode()` được tính từ các giá trị `char` theo công thức đa thức cơ số **31**:

\[
h = s[0]\cdot 31^{n-1} + s[1]\cdot 31^{n-2} + \ldots + s[n-1]
\]

Trong đó `n = s.length()` và `s[i]` là **UTF-16 code unit** ở vị trí `i`, không nhất thiết là một Unicode code point hoàn chỉnh. Phép tính thực hiện với số nguyên `int` 32 bit, nên các phép nhân/cộng có thể **tràn số theo quy tắc Java**, tương ứng kết quả modulo \(2^{32}\); số băm có thể âm. Chuỗi rỗng có `hashCode() == 0`.

Có thể hình dung thuật toán tương đương sau (minh họa logic, không phải mã nguồn nội bộ bắt buộc của JVM):

~~~java
static int manualStringHash(String s) {
    int hash = 0;
    for (int i = 0; i < s.length(); i++) {
        hash = 31 * hash + s.charAt(i);
    }
    return hash;
}
~~~

Đây là **phương thức minh họa** cần đặt trong một lớp Java. Khi tính toán `int`, overflow xảy ra theo quy tắc Java, không tạo ngoại lệ. Đối với `String` thông thường, hàm trên cho cùng kết quả với `s.hashCode()`.

**Ý nghĩa:** cùng một dãy code unit luôn cho cùng một giá trị băm, bất kể hai chuỗi được tạo bằng literal, `new String(...)`, thao tác nối hay đọc từ tệp. Nhưng không có thông tin nào trong giá trị `int` cho phép suy ra duy nhất chuỗi gốc.

## 3. Hợp đồng giữa equals() và hashCode()

| Quan hệ giữa hai chuỗi `a`, `b` | Hệ quả bắt buộc | Điều không thể kết luận |
| --- | --- | --- |
| `a.equals(b) == true` | `a.hashCode() == b.hashCode()` | Không có nghĩa `a == b` |
| `a.hashCode() != b.hashCode()` | `a.equals(b) == false` | Không nói gì về thứ tự từ điển giữa `a` và `b` |
| `a.hashCode() == b.hashCode()` | Chỉ biết hai băm trùng nhau | **Không kết luận** `a.equals(b) == true` |
| `a.equals(b) == false` | Nội dung khác nhau | Hai hash **có thể trùng hoặc khác** |

Một cách ghi nhớ:

~~~text
a.equals(b) == true
        |
        v
a.hashCode() == b.hashCode()

a.hashCode() == b.hashCode()
        |
        +---- có thể a.equals(b) == true
        |
        +---- cũng có thể a.equals(b) == false
              (hash collision)
~~~

Hợp đồng này áp dụng cho các đối tượng có cách triển khai `equals()` và `hashCode()` đúng quy định của `Object`. `String` đã triển khai phù hợp. Khi tự xây dựng lớp làm khóa `HashMap`, nếu ghi đè `equals()` thì thông thường **phải ghi đè `hashCode()` tương thích** để các đối tượng bằng nhau có cùng giá trị băm.

Việc có cùng hash cũng không làm `equals()` trả về `true`, vì `equals()` không định nghĩa sự bằng nhau thông qua mỗi giá trị hash. Giá trị hash chỉ là **bước hỗ trợ tìm kiếm/phân nhóm**.

## 4. Ví dụ va chạm nổi tiếng: "Aa" và "BB"

Hai chuỗi **khác nội dung** sau đây có cùng giá trị hash:

~~~java
String first = "Aa";
String second = "BB";

System.out.println(first.hashCode());      // 2112
System.out.println(second.hashCode());     // 2112
System.out.println(first.equals(second));  // false
~~~

Ta tự tính theo công thức của `String`, với giá trị UTF-16 của các ký tự ASCII:

- `"Aa"`: `'A' = 65`, `'a' = 97` nên `65 * 31 + 97 = 2112`.
- `"BB"`: `'B' = 66` nên `66 * 31 + 66 = 2112`.

Hash bằng nhau vì hai phép tính cho cùng số nguyên; các chữ trong chuỗi vẫn khác nhau. **Đây là va chạm băm**, không phải lỗi của Java.

Va chạm là điều **không thể loại bỏ hoàn toàn** bằng một hàm băm trả về `int`: có vô số chuỗi `String` có thể tạo ra, nhưng giá trị trả về của `hashCode()` chỉ có \(2^{32}\) khả năng. Theo nguyên lý ngăn kéo, nhiều chuỗi khác nhau phải chia sẻ một giá trị hash.

Đặc biệt, ngay cả khi không có overflow, hai chuỗi ngắn như `"Aa"` và `"BB"` đã có thể băm trùng. Vì vậy **không được dùng hash làm bằng chứng duy nhất cho tính bằng nhau**.

## 5. Chương trình Java 17+ kiểm chứng cả equals, HashSet, HashMap

Lưu chương trình sau vào tệp **StringHashCollisionDemo.java**:

~~~java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class StringHashCollisionDemo {
    public static void main(String[] args) {
        String first = "Aa";
        String second = "BB";
        String sameContent = new String("Aa");

        System.out.println("first hash = " + first.hashCode());
        System.out.println("second hash = " + second.hashCode());
        System.out.println("hash collision = "
                + (first.hashCode() == second.hashCode()));
        System.out.println("first equals second = " + first.equals(second));
        System.out.println("first equals sameContent = "
                + first.equals(sameContent));
        System.out.println("first == sameContent = "
                + (first == sameContent));

        Set<String> set = new HashSet<>();
        set.add(first);
        set.add(second);
        set.add(sameContent);
        System.out.println("set size = " + set.size());

        Map<String, Integer> map = new HashMap<>();
        map.put(first, 1);
        map.put(second, 2);
        map.put(sameContent, 3);

        System.out.println("map size = " + map.size());
        System.out.println("map Aa = " + map.get(new String("Aa")));
        System.out.println("map BB = " + map.get("BB"));
    }
}
~~~

Chạy bằng JDK 17 hoặc mới hơn:

~~~bash
javac -encoding UTF-8 StringHashCollisionDemo.java
java StringHashCollisionDemo
~~~

**Stdout chính xác:**

~~~text
first hash = 2112
second hash = 2112
hash collision = true
first equals second = false
first equals sameContent = true
first == sameContent = false
set size = 2
map size = 2
map Aa = 3
map BB = 2
~~~

Giải thích:

1. `first` và `second` có cùng hash `2112` nhưng khác nội dung, nên `equals()` trả về `false`.
2. `sameContent` là một đối tượng `String` khác với `first`, nhưng có nội dung `"Aa"` nên `first.equals(sameContent)` trả về `true` và chúng có cùng hash.
3. `HashSet` lưu **hai** phần tử: `"Aa"` và `"BB"`. Giá trị băm trùng **không khiến một chuỗi bị loại**; phần tử thứ ba `sameContent` không được thêm vì **bằng theo `equals()`** với `"Aa"` đã có.
4. `HashMap` giữ **hai** khóa `"Aa"` và `"BB"`. Lệnh `put(sameContent, 3)` cập nhật giá trị gắn với khóa bằng nội dung `"Aa"` từ `1` thành `3`, không tạo khóa thứ ba.
5. Lời gọi `map.get(new String("Aa"))` tìm đúng giá trị `3` dù tham chiếu khóa truyền vào là một đối tượng mới.

Không dựa vào **thứ tự duyệt** `HashMap`/`HashSet` để kiểm thử: các collection này không bảo đảm thứ tự như danh sách. Ví dụ trên chỉ in kích thước và kết quả truy xuất theo khóa, nên không phụ thuộc thứ tự lặp.

## 6. Vì sao HashMap và HashSet vẫn hoạt động khi hash trùng?

Ý tưởng tổng quát của cấu trúc hash trong Java là kết hợp **mã băm để khoanh vùng tìm kiếm** và **so sánh bằng nhau để xác nhận đúng khóa**:

~~~text
key (String)
     |
     v
hashCode() --> chọn vị trí/vùng tìm kiếm trong bảng
                    |
                    v
                các mục ứng viên
                    |
                    v
             kiểm tra bằng nhau
             (equals/định danh)
                    |
            +-------+--------+
            |                |
         bằng nhau        không bằng
            |                |
      cùng khóa logic     khóa khác, dù
      (cập nhật/tra)      hash có thể trùng
~~~

Sơ đồ là **mô hình khái niệm**. Triển khai thực tế của `HashMap` có bước trộn bit hash, lựa chọn bucket, tổ chức bucket và tối ưu khác theo phiên bản JDK. Điều cốt lõi của ngữ nghĩa công khai là các khóa `String` được phân biệt theo `equals()`, không dựa độc quyền vào `hashCode()`.

- **Hash trùng + `equals()` trả về `true`:** cùng một khóa logic, thường ghi đè/cập nhật giá trị trong `HashMap`; `HashSet.add()` không tăng kích thước.
- **Hash trùng + `equals()` trả về `false`:** hai khóa logic khác nhau, đều có thể tồn tại trong `HashMap` và `HashSet`.
- **Hash khác:** hai `String` không thể bằng nhau theo `equals()` vì vi phạm chiều thuận của hợp đồng nếu chúng bằng nhau.

Việc nhiều khóa băm trùng có thể làm tăng số lần so sánh và ảnh hưởng **hiệu năng**, nhưng không được phép phá vỡ kết quả đúng của tra cứu. Các tối ưu ở cấp triển khai không biến hash thành định danh duy nhất.

### Phân biệt ba thứ dễ nhầm

| Thao tác | Câu hỏi được trả lời | Có thể dùng để thay thế thao tác kia không? |
| --- | --- | --- |
| `a == b` | Có cùng tham chiếu đối tượng không? | Không thay `equals()` khi cần nội dung |
| `a.equals(b)` | Có bằng nhau theo giá trị `String` không? | Đúng cho so sánh nội dung nguyên văn |
| `a.hashCode() == b.hashCode()` | Có cùng mã băm 32 bit không? | **Không** thay `equals()` vì có collision |

Nếu mục tiêu thật sự là **khóa nội dung chuỗi**, hãy truyền chính `String` làm khóa `Map<String, ...>` hoặc phần tử của `Set<String>`. Không biến chuỗi thành một `int` qua `hashCode()` rồi dùng `int` đó làm “mã duy nhất” để lưu trữ hoặc ghi đè dữ liệu.

## 7. Tự triển khai hàm băm tương đương để kiểm chứng

Để hiểu vì sao giá trị băm phụ thuộc **nội dung UTF-16**, có thể tự hiện thực phép tính:

~~~java
public class ManualStringHashDemo {
    static int compute(String s) {
        int result = 0;
        for (int index = 0; index < s.length(); index++) {
            result = 31 * result + s.charAt(index);
        }
        return result;
    }

    public static void main(String[] args) {
        String[] examples = {"", "Aa", "BB", "Java", "😀"};
        for (String text : examples) {
            System.out.println(compute(text) == text.hashCode());
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 ManualStringHashDemo.java
java ManualStringHashDemo
~~~

Kết quả:

~~~text
true
true
true
true
true
~~~

Chương trình trên cho thấy `hashCode()` của `String` được xác định theo công thức công khai, kể cả với chuỗi chứa emoji gồm một cặp surrogate. **Kích thước bộ nhớ nội bộ** hoặc cách JVM tối ưu đối tượng không làm thay đổi giá trị hash mà API cam kết.

Cũng vì hash dùng đơn vị UTF-16 theo công thức, nó **không phải** mã hóa UTF-8, checksum của byte tệp hay hàm đếm Unicode code point. Hash không cho biết chuỗi dài bao nhiêu ký tự hiển thị và không thể dùng để khôi phục nội dung ban đầu.

## 8. So sánh khác quy tắc thì phải thiết kế quy tắc khóa nhất quán

### Chữ hoa/chữ thường

`equalsIgnoreCase()` không tham gia hợp đồng `String.hashCode()`. Ví dụ:

~~~java
System.out.println("A".equalsIgnoreCase("a")); // true
System.out.println("A".equals("a"));           // false
System.out.println("A".hashCode());            // 65
System.out.println("a".hashCode());            // 97
~~~

Đây là **đoạn mã minh họa** đặt trong phương thức. Hai chuỗi có thể bằng nhau dưới **quy tắc bỏ qua hoa/thường**, nhưng hash vẫn khác vì `String.hashCode()` tính theo **nội dung gốc**. Điều này hoàn toàn phù hợp với hợp đồng `equals()` của `String` vì `"A".equals("a")` là `false`.

Muốn một bảng tra cứu không phân biệt hoa/thường, cần chủ động xác định quy tắc nghiệp vụ: chẳng hạn chuẩn hóa các khóa trước khi lưu và tra bằng cùng một quy tắc, hoặc dùng cấu trúc/comparator phù hợp như `TreeMap<>(String.CASE_INSENSITIVE_ORDER)` nếu hành vi so sánh của comparator đáp ứng yêu cầu. Với Unicode đa ngôn ngữ, quy tắc chuyển hoa/thường, locale và so sánh không nhạy hoa/thường cần được xem xét cẩn thận; chuyển `toLowerCase()` tùy tiện không tự bảo đảm mọi hình thức case folding hay chuẩn hóa mong muốn.

### Unicode normalization

Chữ `é` có thể được biểu diễn bằng code point ghép sẵn `U+00E9` hoặc `e` + dấu sắc `U+0301`:

~~~java
import java.text.Normalizer;

// Đoạn mã minh họa bên trong phương thức Java:
String composed = "\u00E9";
String decomposed = "e\u0301";

System.out.println(composed.equals(decomposed)); // false
System.out.println(composed.hashCode());         // 233
System.out.println(decomposed.hashCode());       // 3900

String normalized = Normalizer.normalize(
        decomposed, Normalizer.Form.NFC);
System.out.println(composed.equals(normalized)); // true
~~~

Vì dãy code unit khác nhau, `equals()` và `hashCode()` trên chuỗi **chưa chuẩn hóa** cũng có thể khác. Trong trường hợp này, NFC chuyển chuỗi decomposed thành nội dung ghép sẵn, nên `equals()` trả về `true` và theo hợp đồng hash cũng trùng.

Nếu muốn coi những chuỗi tương đương theo chuẩn Unicode là cùng khóa, phải **chuẩn hóa nhất quán cả lúc thêm và lúc tra cứu**, và quy tắc nào được coi là “tương đương” phải được ghi rõ trong ứng dụng. Không thể dùng một mình `hashCode()` để thực hiện việc chuẩn hóa hay so sánh ngữ nghĩa.

## 9. Những sai lầm thường gặp

| Sai lầm | Vì sao sai | Cách làm đúng |
| --- | --- | --- |
| `a.hashCode() == b.hashCode()` thì chắc chắn `a.equals(b)` | Hash collision tồn tại; `"Aa"` và `"BB"` là phản ví dụ | Khi cần biết cùng nội dung, dùng `equals()` |
| Hai chuỗi có cùng hash nên `HashSet` sẽ chỉ lưu một | `HashSet` phân biệt phần tử bằng logic bằng nhau | `"Aa"` và `"BB"` cùng tồn tại |
| `==` thay cho `equals()` | `==` với tham chiếu kiểm tra danh tính đối tượng | Dùng `equals()` với chuỗi |
| Dùng `hashCode()` làm ID duy nhất của chuỗi | Chỉ là số nguyên 32 bit, có collision | Giữ khóa là chuỗi gốc hoặc dùng ID riêng được quản lý |
| Dùng hash để phát hiện chuỗi bị thay đổi hay dữ liệu bị giả mạo | `String.hashCode()` không phải hash mật mã, dễ cố ý tạo collision | Chọn công cụ cryptographic phù hợp với bài toán an ninh |
| Dùng `equalsIgnoreCase()` nhưng nghĩ hash cũng bỏ qua hoa/thường | `hashCode()` vẫn theo nội dung `String` chính xác | Thiết kế chuẩn hóa/so sánh khóa thống nhất |
| Các ký tự hiển thị giống nhau phải có cùng hash | Dãy Unicode code unit có thể khác | Xác định quy tắc Unicode normalization trước |
| `String.hashCode()` phụ thuộc địa chỉ đối tượng | Hash của `String` tính từ nội dung | Hai đối tượng `String` nội dung bằng nhau luôn hash bằng nhau |
| Chỉ so sánh hash để tiết kiệm thời gian | Có thể báo bằng nhau sai; chi phí hash cũng không miễn phí | Dùng collection và API so sánh chuẩn, để cấu trúc xử lý collision |

**Lưu ý về bảo mật:** `String.hashCode()` là mã băm **không dùng cho mục đích mật mã**; dễ dự đoán và dễ tìm collision. Không dùng nó cho chữ ký, xác thực mật khẩu, kiểm tra tính toàn vẹn chống sửa đổi hoặc chứng minh duy nhất. Bài toán an ninh cần thuật toán và thiết kế phù hợp riêng.

## 10. Checklist khi dùng String làm khóa

1. Xác định **“hai khóa được coi là bằng nhau”** theo quy tắc nào: nguyên văn, bỏ qua hoa/thường, Unicode normalization hay quy tắc nghiệp vụ khác?
2. Nếu là **nguyên văn**, dùng `String` trực tiếp làm khóa `HashMap<String, V>` / `HashSet<String>`; `equals()` và `hashCode()` của `String` đã phù hợp với nhau.
3. Nếu cần **chuẩn hóa**, thực hiện cùng một phép biến đổi cho mọi khóa lúc lưu và truy xuất; lưu ý locale và dữ liệu Unicode.
4. Không so sánh duy nhất bằng hash và không biến `hashCode()` thành mã định danh duy nhất.
5. Kiểm thử cả cặp **khác nội dung nhưng hash trùng** như `"Aa"` và `"BB"`, và cặp **bằng nội dung nhưng khác đối tượng** như literal `"Aa"` và `new String("Aa")`.
6. Khi nhận giá trị có thể `null`, dùng cơ chế kiểm tra null phù hợp; không gọi `s.equals()` hoặc `s.hashCode()` trực tiếp trên tham chiếu `null`.
7. Đánh giá hiệu năng nếu có nhiều khóa do nguồn không tin cậy cố ý chọn gây collision; kết quả đúng và đặc tính hiệu năng là hai mối quan tâm riêng.

## 11. Tự kiểm tra

**Câu 1.** Nếu `a.equals(b)` là `true`, có trường hợp `a.hashCode() != b.hashCode()` với hai `String` Java hợp lệ không?

**Đáp án:** không. Đây là chiều bắt buộc của hợp đồng `equals()` / `hashCode()`.

**Câu 2.** Nếu hai `String` có cùng `hashCode()`, có đủ dữ kiện khẳng định chúng bằng nhau không?

**Đáp án:** không. `"Aa"` và `"BB"` cùng hash `2112` nhưng `equals()` trả về `false`.

**Câu 3.** Đưa `"Aa"` và `"BB"` vào `HashSet<String>` có bao nhiêu phần tử?

**Đáp án:** hai. Chúng có hash trùng nhưng khác nhau theo `equals()`.

**Câu 4.** Với `Map<String, Integer> m`, sau `m.put("Aa", 1); m.put(new String("Aa"), 2);`, kích thước tăng thành hai không?

**Đáp án:** không. Hai khóa bằng nhau theo `equals()`, nên khóa logic `"Aa"` được cập nhật giá trị thành `2`; kích thước vẫn là một.

**Câu 5.** Vì sao `"A".equalsIgnoreCase("a")` bằng `true`, nhưng hash `65` và `97` khác nhau vẫn không vi phạm hợp đồng?

**Đáp án:** hợp đồng dựa trên `equals()`, mà `"A".equals("a")` bằng `false`. `equalsIgnoreCase()` là quy tắc so sánh khác.

**Câu 6.** Vì sao không nên lưu `String.hashCode()` làm ID độc nhất của tên đăng nhập?

**Đáp án:** nhiều chuỗi khác nhau có thể cùng hash; nếu dùng hash làm ID duy nhất, người dùng khác nhau có thể bị đồng nhất nhầm. Cần lưu và so sánh theo khóa đúng hoặc hệ thống ID riêng có kiểm soát.

## Tài liệu tham khảo

- [Java SE 17 — String](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html): hợp đồng của `equals()`, `hashCode()`, `equalsIgnoreCase()` và công thức băm chuỗi.
- [Java SE 17 — Object](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html): hợp đồng tổng quát giữa `equals()` và `hashCode()`.
- [Java SE 17 — HashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html): hành vi bảng băm với khóa và giá trị.
- [Java SE 17 — HashSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html): tập hợp không lưu các phần tử trùng theo quan hệ bằng nhau.
- [Java SE 17 — Normalizer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/Normalizer.html): chuẩn hóa biểu diễn Unicode khi yêu cầu nghiệp vụ cần.


</details>

- [Quay lại đầu trang](#back-to-top)
