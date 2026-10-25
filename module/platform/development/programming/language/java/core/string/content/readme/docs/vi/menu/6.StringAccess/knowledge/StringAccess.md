<a id="back-to-top"></a>

# Độ dài và truy cập

## Menu
- [length và isEmpty](#string-access-001)
- [charAt và điều kiện chỉ số hợp lệ](#string-access-002)
- [codePointAt và codePointBefore](#string-access-003)
- [codePointCount trên một khoảng UTF-16](#string-access-004)
- [offsetByCodePoints để di chuyển theo code point](#string-access-005)
- [getChars và sao chép một vùng sang char&#91;&#93;](#string-access-006)
- [toCharArray và bản sao dữ liệu ký tự](#string-access-007)
- [Chọn đơn vị chỉ số phù hợp trước khi duyệt hoặc cắt chuỗi](#string-access-008)

## <a id="string-access-001">length và isEmpty</a>

<details>
<summary>Xem chi tiết</summary>

# `String.length()` và `String.isEmpty()` trong Java

Một chương trình xử lý văn bản thường phải trả lời hai câu hỏi: **chuỗi dài bao nhiêu?** và **chuỗi có rỗng hay không?** Java cung cấp trực tiếp hai phương thức của lớp `java.lang.String`:

- `int length()`: trả về **số đơn vị mã UTF-16 (UTF-16 code units)** có trong chuỗi.
- `boolean isEmpty()`: trả về `true` khi và chỉ khi `length() == 0`.

Hai phương thức không sửa nội dung `String`, không nhận tham số và rất phù hợp cho các điều kiện kiểm tra thông thường. Điều quan trọng nhất là hiểu chính xác từ **“độ dài”** và **“rỗng”** ở đây, đặc biệt khi chuỗi chứa dấu cách, emoji hoặc dữ liệu có thể là `null`.

## 1. `length()` trả về gì?

Cú pháp:

```java
int count = text.length();
```

`length()` trả về một số nguyên không âm. Với chuỗi ASCII đơn giản, kết quả thường trùng với số ký tự người đọc nhìn thấy:

```java
"".length()       // 0
"A".length()      // 1
"Java".length()   // 4
"Java 17".length() // 7: dấu cách ở giữa cũng được tính
"  ".length()     // 2: hai dấu cách
"\n".length()     // 1: một ký tự xuống dòng
"\r\n".length()   // 2: hai đơn vị mã (CR và LF)
```

Đây là các biểu thức minh họa, không phải tệp Java độc lập. Kết quả `length()` bao gồm **mọi** đơn vị mã UTF-16 trong chuỗi, kể cả khoảng trắng và ký tự điều khiển; phương thức không tự loại bỏ, cắt tỉa hay bỏ qua ký tự nào.

### Vì sao không gọi đơn giản là “số ký tự”?

Java `String` sử dụng mô hình chỉ số UTF-16 cho `length()`, `charAt()` và `substring()`. Một ký tự Unicode có thể được biểu diễn bằng **một** hoặc **hai** đơn vị mã UTF-16:

- Phần lớn ký tự thường gặp trong BMP (Basic Multilingual Plane), ví dụ `A`, `á` hoặc chữ Hán phổ biến: **1** đơn vị mã UTF-16.
- Một ký tự Unicode ngoài BMP, ví dụ nhiều emoji: **2** đơn vị mã UTF-16, dưới dạng một *surrogate pair*.

Vì vậy, `length()` **không phải** phép đếm số Unicode code points, không phải số chữ/grapheme mà người dùng nhìn thấy, và cũng không phải số byte khi lưu UTF-8.

Có ba khái niệm khác nhau:

| Thước đo | Ý nghĩa | Cách kiểm tra |
| --- | --- | --- |
| UTF-16 code units | Đơn vị dùng làm chỉ số trong `String` | `s.length()` |
| Unicode code points | Các giá trị Unicode logic trong một dãy UTF-16 | `s.codePointCount(0, s.length())` |
| Bytes theo một encoding | Kích thước khi mã hóa chuỗi thành byte | `s.getBytes(StandardCharsets.UTF_8).length` cho UTF-8 |

Một **grapheme cluster** (ví dụ chữ cái kết hợp dấu hoặc emoji có nhiều thành phần) có thể gồm nhiều Unicode code points; do đó ngay cả `codePointCount()` cũng không bảo đảm đếm đúng số ký tự người dùng cảm nhận. Khi phải giới hạn “số ký tự hiển thị”, cần tiêu chuẩn phân đoạn grapheme phù hợp, thay vì chỉ dựa vào `length()`.

## 2. `isEmpty()` kiểm tra gì?

Cú pháp:

```java
boolean empty = text.isEmpty();
```

Với một đối tượng `String` khác `null`, hai điều kiện dưới đây **tương đương về kết quả**:

```java
text.isEmpty()
text.length() == 0
```

Có thể coi `isEmpty()` là cách diễn đạt trực tiếp ý định: **“Chuỗi có độ dài bằng 0 không?”**.

Ví dụ:

| Giá trị chuỗi | `length()` | `isEmpty()` | Giải thích |
| --- | ---: | --- | --- |
| `""` | 0 | `true` | Không có đơn vị mã nào. |
| `"Java"` | 4 | `false` | Bốn chữ cái ASCII. |
| `" "` | 1 | `false` | Có một dấu cách. |
| `"  "` | 2 | `false` | Có hai dấu cách. |
| `"\t"` | 1 | `false` | Có một tab. |
| `"\n"` | 1 | `false` | Có một ký tự xuống dòng. |
| `" \t\n"` | 3 | `false` | Chỉ có whitespace nhưng vẫn có nội dung theo nghĩa mã UTF-16. |

`isEmpty()` **không** tự động loại bỏ khoảng trắng, và **không** trả về `true` chỉ vì chuỗi “nhìn như không có chữ”. Nếu yêu cầu nghiệp vụ là “người dùng chỉ nhập dấu cách cũng bị xem như chưa nhập gì”, hãy xem `isBlank()` ở phần sau.

## 3. Chương trình Java 17+ đầu tiên: độ dài, rỗng và khoảng trắng

Tạo tệp `LengthAndEmptyDemo.java`:

```java
public class LengthAndEmptyDemo {
    public static void main(String[] args) {
        String empty = "";
        String spaces = " \t";
        String text = "Java";

        System.out.println("empty.length = " + empty.length());
        System.out.println("empty.isEmpty = " + empty.isEmpty());

        System.out.println("spaces.length = " + spaces.length());
        System.out.println("spaces.isEmpty = " + spaces.isEmpty());
        System.out.println("spaces.isBlank = " + spaces.isBlank());

        System.out.println("text.length = " + text.length());
        System.out.println("text.isEmpty = " + text.isEmpty());

        String maybeNull = null;
        System.out.println(
            "null-or-empty = " + (maybeNull == null || maybeNull.isEmpty())
        );
        System.out.println(
            "has-text = " + (text != null && !text.isEmpty())
        );
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac LengthAndEmptyDemo.java
java LengthAndEmptyDemo
```

Kết quả:

```text
empty.length = 0
empty.isEmpty = true
spaces.length = 2
spaces.isEmpty = false
spaces.isBlank = true
text.length = 4
text.isEmpty = false
null-or-empty = true
has-text = true
```

Điểm cần quan sát:

1. `""` không có đơn vị mã nào nên `length() == 0` và `isEmpty() == true`.
2. `" \t"` có hai đơn vị mã, vì vậy `isEmpty() == false` dù không có chữ cái nào. Nó là *blank* theo `isBlank()`.
3. Biểu thức `maybeNull == null || maybeNull.isEmpty()` an toàn nhờ **short-circuit evaluation**: nếu vế đầu `true`, Java không đánh giá vế sau, tránh gọi phương thức trên `null`.
4. `text != null && !text.isEmpty()` chỉ trả về `true` khi tham chiếu tồn tại và chuỗi có độ dài lớn hơn 0.

## 4. Phân biệt chuỗi rỗng, chuỗi trắng và `null`

Ba trường hợp thường gặp khi xử lý dữ liệu nhập:

**Chuỗi rỗng — empty:** `String s = "";`. Đây là một đối tượng `String` hợp lệ có độ dài 0; `s.isEmpty()` là `true`.

**Chuỗi chỉ gồm khoảng trắng — blank:** `String s = "  \t";`. Chuỗi có độ dài lớn hơn 0 nên `isEmpty()` là `false`; `isBlank()` là `true`.

**Tham chiếu `null`:** `String s = null;`. Không có đối tượng `String` được tham chiếu; gọi `s.length()` hoặc `s.isEmpty()` đều gây `NullPointerException`. Không thể nói “`null` có độ dài 0”: `null` không phải một chuỗi rỗng.

### `isEmpty()` và `isBlank()`

`String.isBlank()` có từ Java 11. Nó trả về `true` nếu chuỗi rỗng **hoặc** chỉ chứa các Unicode code points được `Character.isWhitespace(int)` coi là whitespace.

```java
String a = "";
String b = " \t\n";
String c = " Java ";

System.out.println(a.isEmpty()); // true
System.out.println(a.isBlank()); // true

System.out.println(b.isEmpty()); // false
System.out.println(b.isBlank()); // true

System.out.println(c.isEmpty()); // false
System.out.println(c.isBlank()); // false
```

Đoạn này là ví dụ đặt trong phương thức, và các chú thích chỉ rõ kết quả in tương ứng.

Chọn phương thức theo yêu cầu thực tế:

- Chỉ muốn biết **chuỗi có độ dài bằng 0**: dùng `isEmpty()`.
- Muốn coi **chuỗi toàn whitespace như không có nội dung**: dùng `isBlank()` khi phù hợp với quy tắc whitespace của Java.
- Muốn loại bỏ whitespace ở hai đầu trước khi kiểm tra: cân nhắc `strip().isEmpty()` (Java 11+) hoặc `trim().isEmpty()` tùy chuẩn mong muốn. `strip()` và `trim()` có cách nhận diện ký tự ở đầu/cuối khác nhau; đừng giả định chúng hoàn toàn đồng nghĩa với nhau hoặc với `isBlank()` trong mọi trường hợp Unicode.

Lưu ý: định nghĩa whitespace ở `Character.isWhitespace` **không bao gồm mọi dấu cách Unicode có thể nhìn giống khoảng trắng** (ví dụ một số non-breaking spaces). Nếu quy tắc nghiệp vụ cần xử lý các ký tự đó, hãy định nghĩa và kiểm thử quy tắc riêng.

### Mẫu kiểm tra `null` an toàn

```java
// true nếu biến không tham chiếu String nào hoặc trỏ tới String rỗng:
boolean missing = s == null || s.isEmpty();

// true nếu biến có String không rỗng:
boolean nonEmpty = s != null && !s.isEmpty();

// true nếu không null và có ít nhất một code point không phải whitespace:
boolean hasNonBlankText = s != null && !s.isBlank();
```

Đây là fragment trong một phương thức có biến `String s` đã được khai báo. Dùng thứ tự như trên để tránh `NullPointerException`. Nếu quy tắc cần phân biệt `null` (chưa có dữ liệu) và `""` (dữ liệu rỗng), hãy giữ chúng thành **hai trạng thái riêng**, đừng mặc định gộp lại.

## 5. UTF-16: `length()` có thể khác số Unicode code points

Tạo tệp `UnicodeLengthDemo.java`:

```java
import java.nio.charset.StandardCharsets;

public class UnicodeLengthDemo {
    public static void main(String[] args) {
        String ascii = "Java";
        String accented = "\u00E9";           // é, một code point trong BMP.
        String emoji = "\uD83D\uDE00";       // 😀, surrogate pair.
        String combining = "e\u0301";         // e + dấu sắc kết hợp.

        printLengths("ascii", ascii);
        printLengths("accented", accented);
        printLengths("emoji", emoji);
        printLengths("combining", combining);

        String mixed = "A" + emoji + "B";
        System.out.println("mixed.length = " + mixed.length());
        System.out.println(
            "mixed.codePoints = " +
            mixed.codePointCount(0, mixed.length())
        );
        System.out.println(
            "highSurrogateAt1 = " +
            Character.isHighSurrogate(mixed.charAt(1))
        );
        System.out.println(
            "emojiSubstringEquals = " + mixed.substring(1, 3).equals(emoji)
        );
    }

    private static void printLengths(String name, String value) {
        System.out.println(
            name + ": units=" + value.length()
            + ", codePoints=" + value.codePointCount(0, value.length())
            + ", utf8Bytes=" +
            value.getBytes(StandardCharsets.UTF_8).length
        );
    }
}
```

Chạy:

```bash
javac UnicodeLengthDemo.java
java UnicodeLengthDemo
```

Kết quả:

```text
ascii: units=4, codePoints=4, utf8Bytes=4
accented: units=1, codePoints=1, utf8Bytes=2
emoji: units=2, codePoints=1, utf8Bytes=4
combining: units=2, codePoints=2, utf8Bytes=3
mixed.length = 4
mixed.codePoints = 3
highSurrogateAt1 = true
emojiSubstringEquals = true
```

Giải thích:

- `"\u00E9"` biểu diễn ký tự `é` có một code point, một UTF-16 code unit, nhưng **hai byte** khi mã hóa UTF-8.
- `"\uD83D\uDE00"` biểu diễn emoji 😀 bằng một cặp surrogate gồm hai code units; nó là **một code point**, **bốn byte UTF-8**.
- `"e\u0301"` chứa chữ `e` và dấu sắc kết hợp. Nó thường **hiển thị tương tự** `é` nhưng có **hai code points**, **hai UTF-16 code units**, **ba byte UTF-8**. Vì vậy, các giá trị `length()` khác nhau không nhất thiết có nghĩa số “chữ” người dùng nhìn thấy khác nhau.
- `mixed` là `"A" + emoji + "B"`. Chỉ số UTF-16 tương ứng: `A` ở 0; hai nửa surrogate ở 1 và 2; `B` ở 3. Do đó `length() == 4` và `codePointCount(...) == 3`. `substring(1, 3)` lấy cả hai đơn vị mã của emoji nên tạo ra nội dung bằng `emoji`.

**Không cắt ở giữa surrogate pair:** `mixed.substring(1, 2)` chỉ lấy một surrogate, không phải emoji nguyên vẹn. Khi xử lý dữ liệu theo Unicode code point, cân nhắc `codePointAt()`, `codePoints()` và cách chuyển từ chỉ số code point sang chỉ số UTF-16 như `offsetByCodePoints()`. Với chuỗi người dùng nhập chứa emoji ghép, còn cần thêm phân đoạn grapheme nếu thao tác được mô tả bằng “ký tự hiển thị”.

## 6. Dùng `length()` làm cận khi truy cập chuỗi

`charAt(index)` nhận chỉ số **UTF-16 code unit**, bắt đầu từ 0. Một chỉ số chỉ hợp lệ khi `0 <= index < s.length()`; giá trị `length()` chính là ranh giới **sau** đơn vị cuối cùng.

```java
String s = "Java";
if (!s.isEmpty()) {
    System.out.println(s.charAt(s.length() - 1)); // a
}
```

Với chuỗi rỗng, `length() - 1 == -1`, nên không thể đọc `charAt(-1)`. Nếu `s` có thể là `null`, hãy kiểm tra `s != null && !s.isEmpty()` trước khi gọi. Phần kiểm tra chỉ số âm, vòng lặp và xử lý lỗi được luyện kỹ ở bài **charAt và điều kiện chỉ số hợp lệ**; ở đây cần nhớ rằng `length()` đo *code unit*, nên `charAt(length() - 1)` có thể lấy **low surrogate** thay vì toàn bộ code point khi emoji nằm cuối chuỗi.
## 7. Các tình huống ứng dụng

### Kiểm tra tham số tùy chọn

Nếu chuỗi trống nghĩa là người dùng không cung cấp giá trị, có thể dùng:

```java
static boolean isProvided(String value) {
    return value != null && !value.isEmpty();
}
```

Nếu dấu cách đơn thuần không được chấp nhận, hãy dùng `!value.isBlank()` thay vì `!value.isEmpty()`. Cần đặt quy tắc theo yêu cầu của dữ liệu: có trường hợp một dấu cách thực sự là dữ liệu hợp lệ.

### Giới hạn độ dài dữ liệu nhập

```java
static boolean fitsUtf16Limit(String input, int maxUnits) {
    return input != null
        && maxUnits >= 0
        && input.length() <= maxUnits;
}
```

Hàm này áp dụng **giới hạn số UTF-16 code units**. Nó phù hợp nếu hợp đồng dữ liệu quy định chính xác thước đo đó. Nếu hệ thống yêu cầu tối đa một số Unicode code points, nên đếm với `input.codePointCount(0, input.length())`; nếu giới hạn payload là byte UTF-8, nên kiểm tra độ dài byte sau khi mã hóa. Không được dùng `length()` để khẳng định kích thước byte của dữ liệu.

### Duyệt nội dung chuỗi

```java
String s = "ABC";
for (int index = 0; index < s.length(); index++) {
    System.out.print(s.charAt(index));
}
System.out.println(); // ABC
```

Đây là một cách duyệt **UTF-16 code units**. Nó phù hợp khi thao tác theo chỉ số `char`, nhưng cần cách khác nếu mỗi bước phải xử lý trọn vẹn Unicode code point hoặc grapheme. `String` là immutable nên các thao tác `length()`, `isEmpty()` và `charAt()` không biến đổi chính đối tượng chuỗi.

### Hiệu năng

Trong các JDK Java hiện đại, `length()` và `isEmpty()` kiểm tra thông tin độ dài đã có và không cần duyệt toàn bộ nội dung chuỗi. Hãy ưu tiên **cú pháp diễn đạt đúng mục đích**: `isEmpty()` khi hỏi có rỗng không; `length()` khi thật sự cần số đơn vị mã hoặc làm việc với chỉ số. Ngược lại, `codePointCount()` phải xét các code units trong phạm vi để xác định surrogate pairs; kiểm tra độ dài byte theo một encoding cần thực hiện mã hóa.

Không cần thay thế `isEmpty()` bằng `length() == 0` chỉ để “tối ưu” nếu không có số liệu chứng minh, vì hai cách có ý nghĩa giống nhau và thường không tạo khác biệt đáng kể.

## 8. Sai lầm thường gặp

| Sai lầm | Vì sao sai? | Cách đúng |
| --- | --- | --- |
| “`" ".isEmpty()` là `true`.” | Dấu cách vẫn là một code unit. | Dùng `isBlank()` nếu muốn coi whitespace là rỗng về mặt nghiệp vụ. |
| “`null.isEmpty()` trả về `true`.” | Không có đối tượng để gọi phương thức; ném NPE. | Kiểm tra `s == null` trước khi gọi. |
| “`length()` là số byte UTF-8.” | `length()` đếm UTF-16 code units. | Dùng `getBytes(StandardCharsets.UTF_8).length` nếu cần byte UTF-8. |
| “`length()` luôn bằng số Unicode code points.” | Ký tự ngoài BMP cần hai code units. | Dùng `codePointCount(0, s.length())` cho code points. |
| “Số code points luôn là số ký tự hiển thị.” | Dấu ghép và emoji nhiều thành phần có thể gồm nhiều code points. | Dùng thuật toán phân đoạn grapheme khi cần. |
| “`s.charAt(s.length() - 1)` luôn an toàn.” | Chuỗi rỗng tạo chỉ số `-1`, chuỗi `null` ném NPE. | Kiểm tra `s != null && !s.isEmpty()` trước. |
| “`isEmpty()` loại bỏ khoảng trắng trước khi kiểm tra.” | Nó chỉ tương đương `length() == 0`. | Áp dụng rõ ràng chính sách whitespace mong muốn. |
| “`isBlank()` tồn tại trong mọi bản Java.” | `String.isBlank()` được bổ sung trong Java 11. | Với Java 17+ dùng được; khi dùng JDK cũ cần phương án khác. |

## 9. Tự kiểm tra

**Bài 1.** Cho:

```java
String s = "  ";
System.out.println(s.length());
System.out.println(s.isEmpty());
System.out.println(s.isBlank());
```

Kết quả:

```text
2
false
true
```

Giải thích: hai dấu cách là hai UTF-16 code units; chuỗi không rỗng nhưng chỉ gồm whitespace.

**Bài 2.** Cho:

```java
String s = "";
boolean result = s == null || s.isEmpty();
System.out.println(result);
```

Kết quả: `true`. Nếu đổi `s` thành `null`, kết quả vẫn là `true` nhờ toán tử `||` đánh giá ngắn mạch.

**Bài 3.** Cho `String s = "\uD83D\uDE00";`. `s.length()` là bao nhiêu, và `s.codePointCount(0, s.length())` là bao nhiêu?

Đáp án lần lượt là **2** và **1**: một Unicode code point ngoài BMP được biểu diễn bằng hai UTF-16 code units.

**Bài 4.** Một biểu mẫu quy định mật khẩu dài tối đa 20 **byte UTF-8**. Dùng `password.length() <= 20` đã đúng chưa?

Chưa. `length()` đo UTF-16 code units, không đo UTF-8 bytes. Phải mã hóa theo UTF-8 rồi kiểm tra số byte, hoặc để tầng xử lý nhận dữ liệu kiểm tra đúng chuẩn đã quy định.

## Tổng kết

- `length()` cho biết **số UTF-16 code units** trong `String`.
- `isEmpty()` chỉ kiểm tra **`length() == 0`**.
- Chuỗi chỉ chứa khoảng trắng không rỗng theo `isEmpty()`; `isBlank()` giải quyết nhu cầu khác.
- `null` không phải chuỗi rỗng; cần kiểm tra tham chiếu trước khi gọi phương thức.
- Khi yêu cầu dùng “độ dài”, phải xác định rõ đang tính **UTF-16 units**, **Unicode code points**, **grapheme clusters**, hay **bytes theo encoding**.

## Tài liệu tham khảo

- [Java SE 21 API — `java.lang.String`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html): hợp đồng `length()`, `isEmpty()`, `isBlank()`, `codePointCount()`, `charAt()` và `substring()`.
- [Java SE 21 API — `java.lang.Character`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Character.html): định nghĩa code point, surrogate pair và whitespace.
- [Java Language Specification §3.10.5 — String Literals](https://docs.oracle.com/javase/specs/jls/se21/html/jls-3.html#jls-3.10.5): cú pháp chuỗi và escape sequences.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-access-002">charAt và điều kiện chỉ số hợp lệ</a>

<details>
<summary>Xem chi tiết</summary>

# `String.charAt()` và điều kiện chỉ số hợp lệ trong Java

## 1. Mục tiêu bài học

`String.charAt(int index)` là cách đọc **một đơn vị mã UTF-16 (`char`)** tại một vị trí trong `String`. Để dùng đúng, cần nắm quy tắc chỉ số bắt đầu từ `0`, điều kiện `0 <= index < length()`, cách xử lý chuỗi rỗng và chỉ số vượt biên, cùng giới hạn của `charAt` đối với ký tự Unicode cần hai đơn vị UTF-16.

Sau bài này, bạn có thể:

- Xác định chỉ số đầu/cuối hợp lệ trong một `String` và viết vòng lặp không vượt biên.
- Giải thích `String.length()`, `charAt(0)` và `charAt(length() - 1)` hoạt động thế nào với chuỗi rỗng và không rỗng.
- Kiểm tra đầu vào trước khi truy cập hoặc chủ động xử lý ngoại lệ đúng trường hợp.
- Phân biệt `charAt` với `codePointAt` khi chuỗi chứa emoji, ký tự bổ sung hoặc surrogate pair.

## 2. Cú pháp và hợp đồng của `charAt`

Phương thức của `java.lang.String`:

~~~java
public char charAt(int index)
~~~

- **Đầu vào:** `index` là **chỉ số của một code unit UTF-16** trong chuỗi.
- **Đầu ra:** một giá trị `char` tại vị trí đó. Một `char` rộng 16 bit, không nhất thiết đại diện cho một Unicode code point hoàn chỉnh.
- **Chỉ số hợp lệ:** với chuỗi `s` có độ dài `n = s.length()`, chỉ số `index` phải thỏa **`0 <= index && index < n`**.
- **Vượt biên:** API quy định ném `IndexOutOfBoundsException`; ở triển khai `String` của Java thông dụng, ngoại lệ thực tế là lớp con `StringIndexOutOfBoundsException`.
- **Tham chiếu null:** gọi `s.charAt(index)` khi `s == null` gây `NullPointerException` trước khi có thể xét nội dung chuỗi.

Ví dụ:

~~~java
String s = "Java";  // length() == 4
char a = s.charAt(0); // 'J'
char b = s.charAt(1); // 'a'
char c = s.charAt(2); // 'v'
char d = s.charAt(3); // 'a'
~~~

Biểu diễn:

~~~text
Chuỗi:     J   a   v   a
Chỉ số:    0   1   2   3
length():  4
Biên hợp lệ: [0, 4) = 0, 1, 2, 3
~~~

Ký hiệu `[0, 4)` nghĩa là **bao gồm `0` nhưng không bao gồm `4`**. `length()` là độ dài, **không phải chỉ số cuối**.

| Biểu thức trên `"Java"` | Kết quả | Vì sao? |
| --- | --- | --- |
| `s.charAt(0)` | `'J'` | Chỉ số đầu hợp lệ |
| `s.charAt(3)` | `'a'` | Chỉ số cuối hợp lệ |
| `s.charAt(s.length() - 1)` | `'a'` | `4 - 1 = 3` |
| `s.charAt(-1)` | Ngoại lệ vượt biên | Chỉ số âm |
| `s.charAt(4)` | Ngoại lệ vượt biên | `4 == length()` |
| `s.charAt(100)` | Ngoại lệ vượt biên | Vượt xa cuối chuỗi |

**Công thức trọng tâm:**

~~~java
boolean valid = index >= 0 && index < s.length();
~~~

Điều kiện này áp dụng khi `s != null`. Không chỉ kiểm tra `index < s.length()`, vì giá trị âm vẫn lọt qua; cũng không dùng `index <= s.length()`, vì `length()` đã nằm ngoài phạm vi.

## 3. Phần tử đầu, phần tử cuối và chuỗi rỗng

Với một `String` **không rỗng**:

~~~java
String name = "Java";
char first = name.charAt(0);
char last = name.charAt(name.length() - 1);
~~~

Hai phép đọc trên chỉ an toàn khi `name.length() > 0`. Nếu `name` là `""`:

- `name.length()` bằng `0`.
- **Không tồn tại chỉ số hợp lệ** vì không có số nguyên `i` thỏa `0 <= i < 0`.
- `name.charAt(0)` vượt biên.
- `name.charAt(name.length() - 1)` trở thành `name.charAt(-1)` và cũng vượt biên.

Ví dụ kiểm tra trước khi lấy ký tự cuối:

~~~java
String text = "";

if (!text.isEmpty()) {
    char last = text.charAt(text.length() - 1);
    System.out.println(last);
} else {
    System.out.println("Chuỗi rỗng: không có ký tự cuối");
}
~~~

`String.isEmpty()` tương đương kiểm tra `length() == 0`. Nếu biến có thể `null`, `text.isEmpty()` tự nó cũng gây `NullPointerException`; có thể dùng `text != null && !text.isEmpty()` hoặc đặt yêu cầu không nhận null từ đầu theo hợp đồng của hàm.

### Chuỗi chứa khoảng trắng có phải rỗng không?

~~~java
String spaces = "   ";
System.out.println(spaces.length()); // 3
System.out.println(spaces.isEmpty()); // false
~~~

`"   "` không rỗng: có ba code unit là dấu cách, nên `charAt(0)` hợp lệ. Nếu bài toán muốn biết chuỗi *chỉ có khoảng trắng* thì đó là câu hỏi khác; có thể xem xét `isBlank()`, nhưng đừng dùng `isBlank()` thay cho điều kiện chỉ số hợp lệ. Chuỗi trống và chuỗi toàn khoảng trắng là hai trường hợp khác nhau.

## 4. Vòng lặp truy cập từng `char` đúng biên

Mẫu vòng lặp cơ bản:

~~~java
String text = "Java";

for (int i = 0; i < text.length(); i++) {
    char current = text.charAt(i);
    System.out.println(i + ": " + current);
}
~~~

`stdout`:

~~~text
0: J
1: a
2: v
3: a
~~~

Điều kiện `i < text.length()` dừng vòng lặp trước khi `i == 4`. Đối với chuỗi rỗng, vòng lặp không chạy lần nào, nên không gọi `charAt` với chỉ số sai.

Mẫu lỗi phổ biến:

~~~java
// SAI: nếu text = "Java", lần cuối sẽ gọi charAt(4).
for (int i = 0; i <= text.length(); i++) {
    char current = text.charAt(i);
}
~~~

Sửa `<=` thành `<`. Tương tự, nếu duyệt ngược, khởi đầu từ `length() - 1` và dừng ở `i >= 0`:

~~~java
for (int i = text.length() - 1; i >= 0; i--) {
    System.out.println(text.charAt(i));
}
~~~

Mẫu duyệt ngược theo `char` này cũng an toàn với chuỗi rỗng vì khi đó `i` ban đầu là `-1`, điều kiện `i >= 0` sai ngay. Tuy nhiên cả hai vòng lặp đều duyệt **UTF-16 code unit**; với supplementary Unicode code point, chúng đi qua hai nửa surrogate riêng.

## 5. Ví dụ đầy đủ Java 17+: hợp lệ, vượt biên và Unicode

Lưu tệp **`CharAtBoundsDemo.java`**:

~~~java
public class CharAtBoundsDemo {
    public static void main(String[] args) {
        String word = "Java";
        System.out.println("length=" + word.length());
        System.out.println("first=" + word.charAt(0));
        System.out.println("last=" + word.charAt(word.length() - 1));

        for (int i = 0; i < word.length(); i++) {
            System.out.println("charAt[" + i + "]=" + word.charAt(i));
        }

        printIfValid(word, 2);
        printIfValid(word, 4);
        printIfValid("", 0);

        showInvalid(word, -1);
        showInvalid(word, word.length());
        showInvalid("", 0);

        String mixed = "A" + Character.toString(0x1F600) + "B";
        System.out.println("mixed.length=" + mixed.length());
        for (int i = 0; i < mixed.length(); i++) {
            System.out.printf("mixed[%d]=U+%04X%n", i, (int) mixed.charAt(i));
        }
        System.out.printf("codePointAt(1)=U+%X%n", mixed.codePointAt(1));
        System.out.println("codePointCount="
                + mixed.codePointCount(0, mixed.length()));
    }

    private static void printIfValid(String text, int index) {
        if (index >= 0 && index < text.length()) {
            System.out.println("safe[" + index + "]=" + text.charAt(index));
        } else {
            System.out.println("out of range: index=" + index
                    + ", length=" + text.length());
        }
    }

    private static void showInvalid(String text, int index) {
        try {
            text.charAt(index);
        } catch (StringIndexOutOfBoundsException e) {
            System.out.println("caught: " + e.getClass().getSimpleName()
                    + " (index=" + index + ", length=" + text.length() + ")");
        }
    }
}
~~~

Chạy trên Java 17+:

~~~bash
javac -encoding UTF-8 CharAtBoundsDemo.java
java CharAtBoundsDemo
~~~

Kết quả `stdout` đã đối chiếu bằng cách biên dịch và chạy chương trình:

~~~text
length=4
first=J
last=a
charAt[0]=J
charAt[1]=a
charAt[2]=v
charAt[3]=a
safe[2]=v
out of range: index=4, length=4
out of range: index=0, length=0
caught: StringIndexOutOfBoundsException (index=-1, length=4)
caught: StringIndexOutOfBoundsException (index=4, length=4)
caught: StringIndexOutOfBoundsException (index=0, length=0)
mixed.length=4
mixed[0]=U+0041
mixed[1]=U+D83D
mixed[2]=U+DE00
mixed[3]=U+0042
codePointAt(1)=U+1F600
codePointCount=3
~~~

**Phân tích kết quả:**

1. `"Java"` có `length() == 4` và index `0..3`, nên lấy ký tự đầu/cuối thành công.
2. `printIfValid` kiểm tra cả hai cận trước khi truy cập; index `4` và index `0` của chuỗi rỗng chỉ sinh ra thông báo, không gây ngoại lệ.
3. `showInvalid` cố ý gọi ba chỉ số sai để minh họa lớp ngoại lệ từ `String.charAt`. Ví dụ bắt ngoại lệ **chỉ để trình diễn và thử biên**, không có nghĩa nên lạm dụng ngoại lệ trong mọi vòng lặp thông thường.
4. `mixed` chứa `"A"`, một emoji `U+1F600` và `"B"`. Emoji cần **hai `char` UTF-16**: `U+D83D` và `U+DE00`. Do đó `mixed.length()` bằng `4`, mặc dù có **ba code point**.
5. `charAt(1)` và `charAt(2)` trả về hai code unit của emoji; `codePointAt(1)` trả về code point đầy đủ `0x1F600`.

## 6. Ngoại lệ: lúc nào kiểm tra, lúc nào bắt?

Có hai cách kiểm soát chỉ số, tùy hợp đồng API và dữ liệu đầu vào.

### Cách A — kiểm tra trước khi đọc

Thích hợp khi index đến từ người dùng, cấu hình hoặc dữ liệu ngoài:

~~~java
static boolean isValidCharIndex(String text, int index) {
    return text != null && index >= 0 && index < text.length();
}
~~~

Sử dụng:

~~~java
String text = "Java";
int index = 2;

if (isValidCharIndex(text, index)) {
    System.out.println(text.charAt(index));
} else {
    System.out.println("Không thể truy cập chỉ số yêu cầu");
}
~~~

Ở đây kết quả hợp lệ in `v`. Điều kiện `text != null` đứng đầu và toán tử `&&` **short-circuit** giúp không gọi `length()` trên `null`. Hàm kiểm tra phải phản ánh chính sách nghiệp vụ: khi `null` là lỗi lập trình, có thể chọn chặn ngay bằng `Objects.requireNonNull` thay vì âm thầm coi như index không hợp lệ.

### Cách B — dựa vào hợp đồng và cho phép ném ngoại lệ

Nếu một phương thức yêu cầu người gọi luôn cung cấp index hợp lệ, có thể gọi trực tiếp:

~~~java
static char requiredCharAt(String text, int index) {
    return text.charAt(index);
}
~~~

Nếu truyền index sai, ngoại lệ làm lỗi hiển lộ ở nơi phát sinh. Khi cần xử lý lỗi do **đầu vào không tin cậy**, kiểm tra trước thường giúp trả về thông báo có ý nghĩa. Tránh dùng `try/catch` cho mỗi lần duyệt trong một vòng lặp mà bạn đã biết giới hạn qua `length()`.

**Phân biệt hai dạng lỗi:**

| Trường hợp | Nguyên nhân | Ngoại lệ thường thấy |
| --- | --- | --- |
| `"Java".charAt(-1)` | Index âm | `StringIndexOutOfBoundsException` |
| `"Java".charAt(4)` | Index bằng `length()` | `StringIndexOutOfBoundsException` |
| `"".charAt(0)` | Chuỗi rỗng, không có index hợp lệ | `StringIndexOutOfBoundsException` |
| `((String) null).charAt(0)` | Gọi phương thức qua tham chiếu `null` | `NullPointerException` |

Bản thân `String` không tự đổi index ngoài biên thành ký tự mặc định, không trả về `null` và không “quay vòng” index âm. Nếu muốn cơ chế giá trị mặc định, ứng dụng phải định nghĩa rõ.

## 7. Unicode: một chỉ số hợp lệ vẫn có thể nằm giữa emoji

`charAt(index)` đọc đúng **một UTF-16 code unit**, dù chỉ số hợp lệ. Nhiều code point ngoài BMP (như 😀) được mã hóa bởi cặp **high surrogate** rồi **low surrogate**, nên đọc từng `char` có thể lấy riêng một nửa cặp.

```java
String text = "A" + Character.toString(0x1F600) + "B";
System.out.printf("%04X%n", (int) text.charAt(1)); // D83D
System.out.printf("%04X%n", (int) text.charAt(2)); // DE00
System.out.printf("%X%n", text.codePointAt(1));    // 1F600
```

Cả index 1 và 2 đều hợp lệ theo `charAt` nhưng không tương đương hai Unicode code point. `codePointAt(1)` nhận biết cặp surrogate vì bắt đầu ở high surrogate; `codePointAt(2)` bắt đầu ở low surrogate nên trả về `0xDE00`, không tự ghép ngược về emoji đầy đủ.

Khi cần duyệt Unicode code point, hãy bắt đầu tại ranh giới phù hợp và tiến theo `Character.charCount(codePoint)` hoặc dùng `codePoints()`. Bài tiếp theo **codePointAt và codePointBefore** sẽ đi sâu vào cách đọc code point từ hai hướng. Với dấu kết hợp và emoji nối nhiều thành phần, ngay cả một code point cũng chưa chắc là một ký tự hiển thị trọn vẹn.
## 8. `charAt`, `substring` và lỗi cắt ngang surrogate pair

`substring(beginIndex, endIndex)` dùng hai **chỉ số UTF-16**, với `beginIndex` được bao gồm và `endIndex` không được bao gồm. Điều kiện hợp lệ tương ứng là `0 <= beginIndex <= endIndex <= length()`. Khác `charAt`, `substring(length(), length())` là **chuỗi rỗng hợp lệ** vì không lấy phần tử nào.

Nhưng index **hợp lệ về số học** chưa chắc là ranh giới code point. Với `"A😀B"`, lệnh sau không gây lỗi index, nhưng cắt giữa high và low surrogate:

~~~java
String text = "A" + Character.toString(0x1F600) + "B";
String broken = text.substring(0, 2); // 'A' + high surrogate lẻ
~~~

Không nên coi `broken` là “hai ký tự Unicode đầu” hoàn chỉnh. Nếu muốn hai **code point** đầu:

~~~java
int end = text.offsetByCodePoints(0, 2); // 3, UTF-16 index
String firstTwoCodePoints = text.substring(0, end); // "A😀"
~~~

Hợp đồng `charAt` chỉ bảo đảm đọc một `char`; còn hợp đồng hiển thị/trao đổi Unicode có thể cần thêm kiểm tra surrogate pair và ranh giới code point.

## 9. Lỗi thường gặp và cách sửa

1. **Dùng `<= length()` trong vòng lặp.** Với `charAt`, cận trên là **nhỏ hơn** `length()`. Viết `for (int i = 0; i < s.length(); i++)`.
2. **Gọi `charAt(length())` để lấy ký tự cuối.** Index cuối là `length() - 1`, nhưng trước đó phải bảo đảm chuỗi không rỗng.
3. **Chỉ kiểm tra `index < length()`.** Index âm vẫn thỏa điều kiện; thêm `index >= 0`.
4. **Nhầm `null` với `""`.** Chuỗi rỗng là một đối tượng `String` hợp lệ với `length() == 0`; `null` là không có đối tượng để gọi phương thức.
5. **Cho rằng `charAt(i)` luôn đọc nguyên một ký tự Unicode.** Với supplementary code point, mỗi lần chỉ lấy một nửa surrogate; chọn `codePointAt` và tiến theo `Character.charCount` nếu cần đọc code point.
6. **Dùng số code point như thể là index UTF-16.** `codePointCount` trả số lượng, không chuyển đổi tự động sang vị trí `charAt`. Dùng `offsetByCodePoints` để chuyển.
7. **Bắt `StringIndexOutOfBoundsException` trong từng bước xử lý dữ liệu vốn đã kiểm tra được.** Khi duyệt theo `length()`, hãy viết biên đúng. Chỉ bắt lỗi nếu thực sự có chiến lược khôi phục hoặc thông báo cho người gọi.
8. **Cho rằng index được bảo đảm hợp lệ trước khi chuỗi thay đổi.** `String` bất biến; tuy nhiên biến có thể được gán sang đối tượng `String` khác. Nếu kiểm tra `a` mà sau đó gọi `charAt` trên giá trị mới khác, điều kiện cũ không còn bảo đảm. Dùng cùng một tham chiếu chuỗi ổn định trong phép kiểm tra và truy cập.

## 10. Bài tập tự kiểm tra

**Câu 1.** Với `String s = "Core";`, index nào hợp lệ và `s.charAt(s.length())` trả về gì?

**Đáp án:** `0, 1, 2, 3` hợp lệ. `length()` bằng `4`; `charAt(4)` ném ngoại lệ vượt biên.

**Câu 2.** Vì sao `"".charAt(0)` và `"".charAt("".length() - 1)` đều sai?

**Đáp án:** `length() == 0`, không có index hợp lệ. Hai biểu thức truy cập lần lượt dùng `0` và `-1`.

**Câu 3.** Điều kiện kiểm tra `i < s.length()` đã đủ chưa?

**Đáp án:** chưa, vì `i` có thể âm. Cần `i >= 0 && i < s.length()` và `s != null` nếu dữ liệu có thể null.

**Câu 4.** `"A😀B".length()`, `charAt(1)` và `codePointAt(1)` khác nhau thế nào?

**Đáp án:** độ dài là **4 code unit UTF-16**; `charAt(1)` là **high surrogate U+D83D**; `codePointAt(1)` là **code point U+1F600** vì ghép high/low surrogate hợp lệ.

**Câu 5.** Cho `String s = "ab";`, vòng lặp `for (int i = 0; i <= s.length(); i++)` lỗi ở đâu?

**Đáp án:** lượt `i == 2` gọi `charAt(2)`, vượt cận trên. Sửa `<=` thành `<`.

**Câu 6.** `substring(s.length(), s.length())` có giống `charAt(s.length())` về hợp lệ không?

**Đáp án:** không. `substring` với hai biên bằng `length()` trả chuỗi rỗng hợp lệ; `charAt(length())` luôn vượt biên vì phải đọc một phần tử thật.

## 11. Tài liệu tham khảo

- [Oracle Java SE 17 — `String.charAt(int)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html#charAt(int)): kiểu trả về, điều kiện index, ngoại lệ.
- [Oracle Java SE 17 — `String.length()`, `isEmpty()`, `codePointAt()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html): độ dài UTF-16 và thao tác với code point.
- [Oracle Java SE 17 — `Character`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html): `char`, surrogate pair, `charCount` và các phép kiểm tra.
- [Java Language Specification §3.10.5 — String Literals](https://docs.oracle.com/javase/specs/jls/se17/html/jls-3.html#jls-3.10.5): literal chuỗi trong mã nguồn Java.

**Ghi nhớ:** `charAt(index)` chỉ hợp lệ khi **`0 <= index < s.length()`** và `s` không null. `String.length()` và `charAt` làm việc theo **code unit UTF-16**. Nếu bài toán thực sự yêu cầu **Unicode code point**, hãy dùng các API `codePoint...` với ranh giới index thích hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-access-003">codePointAt và codePointBefore</a>

<details>
<summary>Xem chi tiết</summary>

# codePointAt và codePointBefore trong Java String

Hai phương thức **String.codePointAt(int index)** và **String.codePointBefore(int index)** cho phép đọc một *Unicode code point* từ chuỗi Java theo hai hướng: bắt đầu tại vị trí chỉ định hoặc ngay trước vị trí chỉ định. Điểm quyết định để sử dụng đúng là: **index của cả hai phương thức đều đo bằng đơn vị mã UTF-16 (vị trí char), không phải số thứ tự code point hay số ký tự mà mắt nhìn thấy**.

Bài học tập trung vào cách lấy code point, nhận biết cặp surrogate, chọn chỉ số hợp lệ và duyệt chuỗi mà không vô tình tách đôi một code point nằm ngoài Basic Multilingual Plane (BMP).

## 1. Vì sao charAt() chưa đủ để đọc mọi ký tự Unicode?

Java String lưu dãy đơn vị mã UTF-16. Một đơn vị mã là một giá trị 16 bit; thao tác **charAt(index)** trả về đúng một đơn vị mã kiểu char.

- Các code point thuộc BMP không nằm trong vùng surrogate (ví dụ U+0041 là "A") thường được biểu diễn bởi **một** đơn vị mã UTF-16.
- Một code point bổ sung ngoài BMP (từ U+10000 đến U+10FFFF, ví dụ U+1F600 là emoji 😀) cần **hai** đơn vị mã: *high surrogate* rồi *low surrogate*.
- Nếu gọi charAt() vào một nửa của cặp surrogate, giá trị nhận được chỉ là một char đại diện cho nửa đó; nó không tương đương code point ban đầu.

Ví dụ với chuỗi **"A😀B"**:

| UTF-16 index | Đơn vị mã tại vị trí | Vai trò |
|---:|---|---|
| 0 | U+0041 | Chữ A |
| 1 | U+D83D | High surrogate của 😀 |
| 2 | U+DE00 | Low surrogate của 😀 |
| 3 | U+0042 | Chữ B |

Vì thế, **length() bằng 4**, dù chuỗi có **3 Unicode code point**: A, 😀 và B. Độ dài UTF-16 không phải số code point và cũng không mặc nhiên là số ký tự hiển thị (*grapheme cluster*).

Hai phương thức codePointAt()/codePointBefore() nhận diện **một cặp surrogate hợp lệ liền kề** và ghép thành code point tương ứng, thay vì chỉ trả về một char như charAt().

## 2. String.codePointAt(int index): đọc từ vị trí bắt đầu

Cú pháp:

~~~java
int codePoint = text.codePointAt(index);
~~~

Quy tắc:

1. index là vị trí đơn vị mã UTF-16 đầu tiên cần đọc.
2. Nếu char tại index là **high surrogate** và ngay sau nó là **low surrogate**, phương thức ghép cả hai và trả về code point bổ sung dạng int.
3. Trong các trường hợp khác, phương thức trả về giá trị đơn vị mã char tại index dưới dạng int. Điều này bao gồm một surrogate đơn lẻ hoặc việc bắt đầu ở **low surrogate**, tức giữa một cặp.
4. Phương thức **không làm thay đổi** String.

Với "A😀B":

| Lời gọi | Kết quả hệ 16 | Giải thích |
|---|---|---|
| codePointAt(0) | U+0041 | Đọc A |
| codePointAt(1) | U+1F600 | Ghép high surrogate index 1 và low surrogate index 2 |
| codePointAt(2) | U+DE00 | Bắt đầu ở low surrogate: chỉ trả về đơn vị mã này |
| codePointAt(3) | U+0042 | Đọc B |

**Sai lầm quan trọng:** codePointAt(2) không có nghĩa là “lấy code point thứ 3”. Nó là đọc code point **bắt đầu ở chỉ số UTF-16 2**. Nếu chỉ số ở giữa cặp surrogate, phương thức không tự quay lại tìm high surrogate.

Giới hạn chỉ số hợp lệ với chuỗi độ dài n:

- **0 <= index < n** cho codePointAt(index).
- Với chuỗi rỗng, không có index hợp lệ.
- index âm hoặc index >= n gây **StringIndexOutOfBoundsException**.

### Khi nào sử dụng?

Dùng codePointAt() khi bạn đã có **UTF-16 offset** và muốn lấy code point bắt đầu tại đó. Ví dụ, duyệt chuỗi từ trái qua phải, phân loại code point bằng Character.isLetter(int), Character.isDigit(int) hoặc xử lý một ký tự Unicode không giới hạn trong 16 bit.

## 3. String.codePointBefore(int index): đọc ngay trước một ranh giới

Cú pháp:

~~~java
int codePoint = text.codePointBefore(index);
~~~

Khác với codePointAt(), tham số index **không** chỉ phần tử cần đọc, mà chỉ **ranh giới ngay sau đơn vị mã cuối cùng cần xét**.

Quy tắc:

1. Phương thức nhìn vào char tại **index - 1**.
2. Nếu đó là **low surrogate** và char tại **index - 2** là **high surrogate**, phương thức ghép cả cặp rồi trả về code point bổ sung.
3. Nếu không có cặp hợp lệ về phía trước, phương thức trả về giá trị char tại index - 1 dưới dạng int.
4. String nguồn không thay đổi.

Với "A😀B":

| Lời gọi | Kết quả hệ 16 | Giải thích |
|---|---|---|
| codePointBefore(1) | U+0041 | Ký tự ngay trước ranh giới 1 là A |
| codePointBefore(2) | U+D83D | Ngay trước ranh giới 2 là high surrogate; chưa có low surrogate ở bên trái để ghép |
| codePointBefore(3) | U+1F600 | Nhìn từ index 3 về trước: low surrogate tại 2 ghép với high tại 1 |
| codePointBefore(4) | U+0042 | Ký tự ngay trước ranh giới 4 là B |

**Khác biệt cần ghi nhớ:** để lấy emoji 😀 đầy đủ, codePointAt(1) bắt đầu tại high surrogate; codePointBefore(3) bắt đầu từ ranh giới **sau** low surrogate.

Giới hạn chỉ số hợp lệ với chuỗi độ dài n:

- **1 <= index <= n** cho codePointBefore(index).
- index bằng n hoàn toàn hợp lệ và giúp đọc code point cuối.
- index bằng 0, âm hoặc lớn hơn n gây **StringIndexOutOfBoundsException**.

### Khi nào sử dụng?

Dùng codePointBefore() khi bạn có offset nằm **sau** vị trí cần đọc: ví dụ duyệt ngược từ cuối chuỗi, đọc code point đứng trước con trỏ hoặc đi lùi theo ranh giới UTF-16.

## 4. So sánh trực tiếp hai phương thức

| Câu hỏi | codePointAt(index) | codePointBefore(index) |
|---|---|---|
| Đọc về hướng nào? | Từ vị trí index sang phải | Từ trước index sang trái |
| Đơn vị của index? | UTF-16 code unit | UTF-16 code unit |
| Bắt đầu xét char nào? | index | index - 1 |
| Cặp surrogate được ghép khi nào? | index là high và index+1 là low | index-1 là low và index-2 là high |
| Chỉ số hợp lệ? | [0, length()-1] | [1, length()] |
| Lấy code point đầu tiên | codePointAt(0) | codePointBefore(độ dài UTF-16 của code point đầu) |
| Lấy code point cuối cùng | codePointAt(offset đầu của code point cuối) | codePointBefore(length()) |
| Kiểu trả về | int | int |
| Chuỗi bị sửa? | Không | Không |

**Mẹo nhớ:** *At* đọc code point **bắt đầu tại** index; *Before* đọc code point **kết thúc ngay trước** index.

Nếu index trỏ tới ranh giới tự nhiên giữa hai code point, cả hai phương thức hữu ích và nhất quán. Nếu index cắt đôi cặp surrogate, kết quả có thể chỉ là một nửa surrogate. Java không tự bảo đảm index do ứng dụng truyền vào nằm tại ranh giới code point.

## 5. Chương trình Java 17+ chạy độc lập

Ví dụ sau in giá trị dạng U+... để tránh phụ thuộc font hiển thị emoji trong console. Nó minh họa cả trường hợp truy cập đúng ranh giới và trường hợp truy cập giữa hai nửa surrogate.

Tạo file **CodePointAccessDemo.java** với nội dung:

~~~java
public class CodePointAccessDemo {
    public static void main(String[] args) {
        String text = "A😀B";

        System.out.println("length=" + text.length());
        System.out.println("codePoints=" + text.codePointCount(0, text.length()));

        for (int i = 0; i < text.length(); i++) {
            System.out.printf("at(%d)=U+%X%n", i, text.codePointAt(i));
        }

        for (int i = 1; i <= text.length(); i++) {
            System.out.printf("before(%d)=U+%X%n", i, text.codePointBefore(i));
        }

        System.out.println("highSurrogate="
                + Character.isHighSurrogate(text.charAt(1)));
        System.out.println("lowSurrogate="
                + Character.isLowSurrogate(text.charAt(2)));
    }
}
~~~

Lưu mã nguồn bằng UTF-8, rồi biên dịch/chạy:

~~~bash
javac -encoding UTF-8 CodePointAccessDemo.java
java CodePointAccessDemo
~~~

Kết quả chính xác:

~~~text
length=4
codePoints=3
at(0)=U+41
at(1)=U+1F600
at(2)=U+DE00
at(3)=U+42
before(1)=U+41
before(2)=U+D83D
before(3)=U+1F600
before(4)=U+42
highSurrogate=true
lowSurrogate=true
~~~

Giải thích:

- length() đếm **4 đơn vị mã UTF-16**, còn codePointCount(0, length()) đếm **3 code point**.
- codePointAt(1) và codePointBefore(3) cùng nhận ra emoji U+1F600.
- codePointAt(2) chỉ trả về low surrogate U+DE00; codePointBefore(2) chỉ trả về high surrogate U+D83D.
- %X trong printf hiển thị int dưới dạng hệ 16 với chữ A–F viết hoa, nên các dòng trên không phụ thuộc ngôn ngữ hệ thống.

## 6. Duyệt chuỗi mà không tách đôi surrogate pair

### 6.1. Duyệt từ trái sang phải

Sai lầm thường gặp:

~~~java
for (int i = 0; i < text.length(); i++) {
    int codePoint = text.codePointAt(i);
    // Xử lý codePoint...
}
~~~

Đây là **đoạn mã minh họa**, giả định biến text đã được khai báo. Với chuỗi có code point bổ sung, vòng lặp tăng i đúng **1 đơn vị mã**; sau khi đọc đủ emoji tại high surrogate, vòng tiếp theo lại đọc riêng low surrogate. Như vậy có thể xử lý dư một lần.

Cách đúng là tăng i theo **số đơn vị mã của code point vừa đọc**:

~~~java
for (int i = 0; i < text.length(); ) {
    int codePoint = text.codePointAt(i);
    System.out.printf("%d: U+%X%n", i, codePoint);
    i += Character.charCount(codePoint);
}
~~~

Character.charCount(codePoint) trả về 2 cho code point bổ sung hợp lệ và 1 cho các giá trị còn lại. Với text = "A😀B", kết quả:

~~~text
0: U+41
1: U+1F600
3: U+42
~~~

Con trỏ nhảy qua cả hai đơn vị mã của emoji: từ vị trí 1 đến vị trí 3. Nó không đứng ở low surrogate tại vị trí 2.

### 6.2. Duyệt từ phải sang trái

Khởi tạo con trỏ ở **length()** rồi dùng codePointBefore(index); sau mỗi lần đọc, trừ đúng số đơn vị mã của code point:

~~~java
for (int i = text.length(); i > 0; ) {
    int codePoint = text.codePointBefore(i);
    i -= Character.charCount(codePoint);
    System.out.printf("%d: U+%X%n", i, codePoint);
}
~~~

Với text = "A😀B", kết quả:

~~~text
3: U+42
1: U+1F600
0: U+41
~~~

**Cần lưu ý:** hai vòng lặp trên hoạt động trên các ranh giới code point khi xuất phát từ đầu/cuối chuỗi. Nếu tự đặt con trỏ ban đầu ở giữa một cặp surrogate, cả codePointAt() và codePointBefore() có thể trả về một surrogate đơn lẻ. Cần xác định offset hợp lệ từ đầu hoặc cuối chuỗi, hoặc dùng offsetByCodePoints() khi chuyển qua số lượng code point.

### 6.3. Nếu cần vị trí code point thứ n

Các phương thức codePointAt()/codePointBefore() không nhận trực tiếp **thứ tự code point**. Để tìm UTF-16 offset sau khi đi qua n code point, dùng String.offsetByCodePoints(startUtf16Offset, n), rồi mới gọi codePointAt():

~~~java
String text = "A😀B";
int utf16Offset = text.offsetByCodePoints(0, 2);
int codePoint = text.codePointAt(utf16Offset);

System.out.println(utf16Offset); // 3
System.out.printf("U+%X%n", codePoint); // U+42
~~~

Ở ví dụ này, sau hai code point A và 😀, offset UTF-16 là 3 chứ không phải 2. offsetByCodePoints() cũng cần chỉ số và khoảng dịch hợp lệ; nó có thể ném IndexOutOfBoundsException nếu vượt vùng khả dụng.

Nếu chỉ cần duyệt tất cả code point về phía trước và không cần UTF-16 offset, có thể dùng **text.codePoints()**, một IntStream các giá trị int của code point:

~~~java
String text = "A😀B";
text.codePoints().forEach(cp -> System.out.printf("U+%X%n", cp));
~~~

Đầu ra:

~~~text
U+41
U+1F600
U+42
~~~

codePoints() hữu ích khi xử lý từng code point dưới dạng luồng số, còn codePointAt()/codePointBefore() phù hợp khi cần điều khiển vị trí UTF-16 một cách tường minh.

## 7. Xử lý index rỗng, biên và lỗi

Hãy kiểm tra **giới hạn khác nhau** của hai API trước khi gọi.

| Chuỗi | Lời gọi | Kết quả |
|---|---|---|
| "AB" | codePointAt(0) | 65, tức U+0041 |
| "AB" | codePointAt(2) | Lỗi StringIndexOutOfBoundsException |
| "AB" | codePointBefore(2) | 66, tức U+0042 |
| "AB" | codePointBefore(0) | Lỗi StringIndexOutOfBoundsException |
| "" | codePointAt(0) | Lỗi StringIndexOutOfBoundsException |
| "" | codePointBefore(0) | Lỗi StringIndexOutOfBoundsException |

Hai phương thức có kiểu trả về int bởi một code point bổ sung cần hơn 16 bit; không nên ép kết quả thành char khi muốn giữ nguyên code point U+10000 trở lên. Nếu cần chuyển một code point hợp lệ thành dãy char UTF-16, có thể dùng **Character.toChars(codePoint)**.

Ví dụ chương trình độc lập kiểm tra biên:

~~~java
public class CodePointBoundaryDemo {
    public static void main(String[] args) {
        String text = "AB";
        System.out.println(text.codePointAt(0));
        System.out.println(text.codePointBefore(text.length()));

        try {
            text.codePointBefore(0);
        } catch (StringIndexOutOfBoundsException ex) {
            System.out.println("Invalid before index");
        }

        try {
            text.codePointAt(text.length());
        } catch (StringIndexOutOfBoundsException ex) {
            System.out.println("Invalid at index");
        }
    }
}
~~~

~~~bash
javac CodePointBoundaryDemo.java
java CodePointBoundaryDemo
~~~

Đầu ra:

~~~text
65
66
Invalid before index
Invalid at index
~~~

Trong mã thực tế, nếu đầu vào là một offset do người dùng hoặc API khác cung cấp, hãy kiểm tra miền chỉ số hoặc xử lý ngoại lệ phù hợp. Tránh tự động trừ/cộng 1 trước khi hiểu offset đó đại diện cho **đơn vị mã UTF-16**, **số thứ tự code point**, hay **vị trí hiển thị**.

## 8. Trường hợp Unicode khó: surrogate đơn lẻ và grapheme cluster

### 8.1. Surrogate không ghép thành cặp hợp lệ

Một String Java có thể chứa **surrogate đơn lẻ** (chuỗi UTF-16 không chuẩn hóa thành cặp đầy đủ). Khi codePointAt()/codePointBefore() không tìm thấy cặp high–low thích hợp, nó trả về giá trị char surrogate dưới dạng int. Phương thức không tự sửa dữ liệu lỗi thành U+FFFD và không tự ném lỗi chỉ vì gặp surrogate đơn lẻ.

Vì vậy, “giá trị int trả về từ codePointAt” không luôn có nghĩa là một *Unicode scalar value* hợp lệ: giá trị trong dải surrogate U+D800–U+DFFF là dấu hiệu của đơn vị mã surrogate chưa ghép. Nếu xử lý dữ liệu ngoài hệ thống, cần xem xét chất lượng chuỗi và quy tắc mã hóa/giải mã đầu vào.

### 8.2. Code point không phải ký tự người dùng nhìn thấy

Ví dụ một ký tự hiển thị giống "é" có thể được biểu diễn bằng chữ e (U+0065) kết hợp dấu sắc (U+0301), tức **hai code point**. Một số emoji kết hợp nhiều code point, variation selector hoặc dấu nối zero-width joiner. codePointAt()/codePointBefore() đọc **code point**, không tự gom toàn bộ *grapheme cluster* thành một ký tự hiển thị.

Vì thế:

- Muốn duyệt giá trị Unicode riêng lẻ: dùng codePointAt()/codePointBefore() hoặc codePoints().
- Muốn đếm/định vị code point: dùng codePointCount() và offsetByCodePoints().
- Muốn tách các cụm ký tự như người dùng nhìn thấy trong giao diện: cần một thuật toán phân đoạn grapheme cluster phù hợp với yêu cầu và phiên bản Unicode; codePointAt() một mình chưa đủ.

## 9. Checklist chọn API và lỗi thường gặp

1. **Đọc một đơn vị mã UTF-16:** charAt(index).
2. **Đọc một code point bắt đầu tại UTF-16 offset:** codePointAt(index).
3. **Đọc một code point trước UTF-16 offset:** codePointBefore(index).
4. **Đi qua code point khi duyệt thuận/ngược:** cộng hoặc trừ Character.charCount(codePoint), không luôn tăng/giảm 1.
5. **Chuyển số lượng code point thành UTF-16 offset:** offsetByCodePoints().
6. **Đếm code point trong một khoảng:** codePointCount(startUtf16, endUtf16).
7. **Duyệt tất cả code point dạng số:** codePoints().

Những lỗi hay gặp cần tránh:

- Xem **index** trong codePointAt()/codePointBefore() là thứ tự code point.
- Gọi codePointBefore(0) với suy nghĩ đó là “code point đầu tiên”; chỉ số 0 là một ranh giới **không có đơn vị mã nào phía trước**.
- Gọi codePointAt(length()) để lấy code point cuối; chỉ số đó đã ở **sau** cuối chuỗi.
- Duyệt bằng i++ và codePointAt(i) rồi đếm trùng một low surrogate sau khi đã ghép cặp.
- Cho rằng codePointAt() có thể ghép cặp khi index đang ở low surrogate.
- Ép code point bổ sung từ int sang char và làm mất thông tin.
- Đồng nhất code point với một “chữ” hiển thị duy nhất, đặc biệt trong tiếng Việt có dấu kết hợp hoặc emoji phức hợp.

**Kết luận:** cả **codePointAt(index)** lẫn **codePointBefore(index)** đều làm việc với offset UTF-16. codePointAt() đọc từ một vị trí về bên phải; codePointBefore() đọc ngay trước vị trí đó về bên trái. Dùng chỉ số đúng ranh giới và dịch con trỏ theo Character.charCount() để xử lý đầy đủ code point bổ sung mà không vô tình tách cặp surrogate.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-access-004">codePointCount trên một khoảng UTF-16</a>

<details>
<summary>Xem chi tiết</summary>

# codePointCount trên một khoảng UTF-16 trong Java

## 1. Mục tiêu và ý nghĩa

Phương thức **String.codePointCount(int beginIndex, int endIndex)** đếm số **Unicode code point** được biểu diễn trong **một khoảng của chuỗi Java**. Điểm dễ nhầm nhất: hai tham số **beginIndex** và **endIndex** đều là **chỉ số đơn vị mã UTF-16 (UTF-16 code unit)**, không phải chỉ số code point hay chỉ số ký tự người dùng nhìn thấy.

Cú pháp:

~~~java
int count = text.codePointCount(beginIndex, endIndex);
~~~

Các câu lệnh minh họa trong bài có thể đặt trong một phương thức Java. Quy ước của API:

- **beginIndex**: vị trí bắt đầu trong String, **bao gồm** vị trí này.
- **endIndex**: vị trí kết thúc, **không bao gồm** vị trí này; khoảng được viết **[beginIndex, endIndex)**.
- Chỉ số hợp lệ thỏa **0 <= beginIndex <= endIndex <= text.length()**.
- Nếu **beginIndex == endIndex**, kết quả là **0**.
- Nếu bắt đầu nhỏ hơn 0, kết thúc lớn hơn **length()**, hoặc bắt đầu lớn hơn kết thúc, phương thức ném **IndexOutOfBoundsException**.
- Mỗi **surrogate pair hợp lệ nằm trọn trong khoảng** được đếm là **1 code point**.
- Mỗi **surrogate đơn lẻ** trong khoảng, kể cả một nửa của cặp bị cắt bởi ranh giới, được đếm là **1**.

**Điều quan trọng:** phương thức đếm theo đoạn UTF-16 **đúng như được truyền vào**. Nó không tự kéo giãn khoảng để giữ nguyên các cặp surrogate, không chuẩn hóa Unicode, không đếm grapheme cluster và không tự biến tham số thành vị trí code point.

## 2. Ba khái niệm cần phân biệt

| Khái niệm | Trong Java | Cách hiểu |
| --- | --- | --- |
| **UTF-16 code unit** | **char**, **String.length()**, chỉ số của **charAt()**/**substring()** | Một đơn vị 16 bit; code point ngoài vùng BMP cần hai đơn vị |
| **Unicode code point** | **codePointAt()**, **codePoints()**, **codePointCount()** | Giá trị Unicode; một cặp high surrogate + low surrogate biểu diễn một code point bổ sung |
| **Extended grapheme cluster** | Có thể phân đoạn bằng regex **\\X** | Một cụm thường được người dùng cảm nhận như một ký tự, có thể chứa nhiều code point |

**Basic Multilingual Plane (BMP)** là các code point từ U+0000 đến U+FFFF, trừ dải surrogate vốn không phải Unicode scalar value độc lập. Những code point bổ sung từ U+10000 đến U+10FFFF được mã hóa bằng **hai UTF-16 code unit**: **high surrogate** theo sau bởi **low surrogate**.

Do đó:

- Chữ Latin thông thường **A**: thường 1 code unit và 1 code point.
- Emoji **😊** (U+1F60A): 2 code unit, 1 code point.
- Chữ **a** cộng dấu sắc kết hợp **a\u0301**: 2 code unit, 2 code point, nhưng thường 1 grapheme cluster.
- Emoji **👩‍💻**: 5 code unit, 3 code point, thường 1 grapheme cluster.

**codePointCount()** trả lời câu hỏi về **code point trong khoảng UTF-16**; nó không nhất thiết trả lời câu hỏi “có bao nhiêu chữ, bao nhiêu emoji hay bao nhiêu ký tự hiển thị?”.

## 3. Minh họa chính: "A😊B"

Xét chuỗi:

~~~java
String text = "A😊B";
~~~

Bố cục của chuỗi theo chỉ số **UTF-16**:

| Vị trí code unit | Nội dung | Vai trò |
| ---: | --- | --- |
| 0 | **A** | Chữ BMP, 1 code unit |
| 1 | **U+D83D** | High surrogate của **😊** |
| 2 | **U+DE0A** | Low surrogate của **😊** |
| 3 | **B** | Chữ BMP, 1 code unit |
| 4 | Ranh giới cuối chuỗi | Vị trí **endIndex** hợp lệ, không phải ký tự |

Vậy **text.length() == 4**, nhưng toàn chuỗi chỉ có **3 code point**: **A**, **😊**, **B**.

Các khoảng tiêu biểu:

| Khoảng UTF-16 | Phần được lấy | **codePointCount** | Giải thích |
| --- | --- | ---: | --- |
| **[0, 4)** | **A😊B** | 3 | A (1) + cặp surrogate của 😊 (1) + B (1) |
| **[1, 3)** | **😊** | 1 | Hai code unit kết hợp thành một code point |
| **[0, 1)** | **A** | 1 | Một chữ BMP |
| **[3, 4)** | **B** | 1 | Một chữ BMP |
| **[1, 1)** | Rỗng | 0 | Không có code unit nào |
| **[0, 2)** | A + high surrogate riêng | 2 | Ranh giới 2 cắt đôi surrogate pair |
| **[2, 4)** | Low surrogate riêng + B | 2 | Ranh giới 2 cắt đôi surrogate pair |

Điều này dẫn đến một tính chất cần thận trọng:

~~~java
int whole = text.codePointCount(0, 4);   // 3
int left = text.codePointCount(0, 2);    // 2
int right = text.codePointCount(2, 4);   // 2
~~~

**left + right == 4**, khác với **whole == 3**, vì điểm chia **2** nằm **giữa hai nửa surrogate pair**. Việc cộng số code point của các khoảng chỉ tương thích với số đếm toàn đoạn nếu mọi ranh giới chia **không tách** một surrogate pair hợp lệ.

Nếu đổi điểm chia thành **3**, tức ngay sau emoji, kết quả:

~~~java
int left = text.codePointCount(0, 3);    // 2 (A, 😊)
int right = text.codePointCount(3, 4);   // 1 (B)
~~~

Khi đó **left + right == 3**, đúng bằng số code point toàn chuỗi.

## 4. Ví dụ Java 17+ chạy độc lập

Lưu thành tệp **CodePointCountRangeDemo.java** (mã hóa UTF-8):

~~~java
public class CodePointCountRangeDemo {
    public static void main(String[] args) {
        String text = "A😊B";

        System.out.println("UTF16 units: " + text.length());
        System.out.println("All: " +
                text.codePointCount(0, text.length()));
        System.out.println("Emoji: " +
                text.codePointCount(1, 3));
        System.out.println("Left split: " +
                text.codePointCount(0, 2));
        System.out.println("Right split: " +
                text.codePointCount(2, 4));
        System.out.println("Empty: " +
                text.codePointCount(1, 1));

        System.out.println("High surrogate: " +
                Character.isHighSurrogate(text.charAt(1)));
        System.out.println("Low surrogate: " +
                Character.isLowSurrogate(text.charAt(2)));

        int endAfterTwoPoints = text.offsetByCodePoints(0, 2);
        System.out.println("Index after 2 points: " +
                endAfterTwoPoints);
        System.out.println("Prefix: " +
                text.substring(0, endAfterTwoPoints));
    }
}
~~~

Chạy bằng JDK 17 hoặc mới hơn:

~~~bash
javac -encoding UTF-8 CodePointCountRangeDemo.java
java CodePointCountRangeDemo
~~~

Stdout:

~~~text
UTF16 units: 4
All: 3
Emoji: 1
Left split: 2
Right split: 2
Empty: 0
High surrogate: true
Low surrogate: true
Index after 2 points: 3
Prefix: A😊
~~~

Các con số được tính dựa trên cặp surrogate của emoji **😊**: high surrogate U+D83D và low surrogate U+DE0A.

Điểm cần ghi nhớ từ chương trình:

1. **length()** đo số code unit; **codePointCount(0, length())** đo số code point trong cả String.
2. **[1, 3)** chứa trọn surrogate pair nên kết quả bằng 1.
3. **[0, 2)** và **[2, 4)** tạo ra các khoảng có một surrogate lẻ; mỗi surrogate lẻ được tính là 1.
4. **offsetByCodePoints(0, 2)** trả về **chỉ số UTF-16 bằng 3** sau hai code point đầu, thích hợp để truyền vào **substring()**.

**Quan trọng:** **charAt(1)** cho ra high surrogate, không phải ký tự emoji hoàn chỉnh. Muốn đọc code point bắt đầu ở vị trí đó, dùng **text.codePointAt(1)**; kết quả là U+1F60A.

## 5. Giới hạn beginIndex/endIndex và khoảng rỗng

Khoảng trong Java là **nửa mở**:

\[
[beginIndex,endIndex)
\]

Ví dụ với **"Java"** (mỗi ký tự trong ví dụ này là một code unit):

| Lệnh | Kết quả | Vì sao |
| --- | ---: | --- |
| **"Java".codePointCount(0, 4)** | 4 | Đếm cả chuỗi |
| **"Java".codePointCount(1, 3)** | 2 | Chứa ký tự ở vị trí 1 và 2 |
| **"Java".codePointCount(0, 0)** | 0 | Khoảng rỗng đầu chuỗi |
| **"Java".codePointCount(4, 4)** | 0 | Khoảng rỗng cuối chuỗi |

Các lệnh sau không hợp lệ:

~~~java
String text = "Java";
text.codePointCount(-1, 2); // beginIndex âm
text.codePointCount(1, 5);  // endIndex vượt length()
text.codePointCount(3, 2);  // beginIndex > endIndex
~~~

Đây là đoạn **minh họa lỗi**, không nên chạy ba lệnh liên tiếp nếu muốn quan sát từng lỗi, vì lệnh đầu tiên đã ném **IndexOutOfBoundsException** và có thể làm dừng chương trình. Các ranh giới đúng cần thoả đồng thời **0 <= beginIndex <= endIndex <= text.length()**.

Đừng nhầm các trường hợp **endIndex == length()** (hợp lệ) với truy cập **charAt(length())** (không hợp lệ, vì đây là vị trí ngay sau phần tử cuối).

Nếu phương thức chỉ nhận khoảng code point từ người dùng hoặc từ một API khác, **không được** đưa trực tiếp chúng vào **codePointCount(beginIndex, endIndex)**. Trước tiên phải chuyển các offset theo code point thành offset UTF-16.

## 6. Chuyển giữa vị trí UTF-16 và vị trí code point

**codePointCount(begin, end)** trả lại **số code point**, không trả lại chỉ số. Khi cần một offset UTF-16 tương ứng với một số code point, Java cung cấp:

~~~java
int endUtf16 = text.offsetByCodePoints(startUtf16, numberOfCodePoints);
~~~

Ví dụ:

~~~java
String text = "A😊B";
int endUtf16 = text.offsetByCodePoints(0, 2); // 3
String firstTwo = text.substring(0, endUtf16); // "A😊"
~~~

Vì ký tự bổ sung **😊** chiếm hai code unit nên offset sau hai code point đầu là **3**, không phải **2**.

Một phép đếm prefix cũng hữu ích khi chuyển **offset UTF-16** thành thứ tự code point:

~~~java
String text = "A😊B";
int utf16Index = 3; // bắt đầu chữ B
int codePointsBeforeB = text.codePointCount(0, utf16Index); // 2
~~~

Điều kiện cần chú ý là **utf16Index** phải nằm ở ranh giới code point khi bạn định diễn giải kết quả như số code point **trước một ký tự nguyên vẹn**. Nếu dùng **utf16Index == 2** (giữa emoji), **codePointCount(0, 2)** vẫn trả về **2**, nhưng đó không phải vị trí hợp lệ để đặt con trỏ “sau hai code point” trong chuỗi gốc.

**offsetByCodePoints()** cũng cần điểm khởi đầu và số code point hợp lệ. Nó có thể di chuyển từ một vị trí UTF-16 tùy ý, kể cả vị trí ở giữa surrogate pair; nếu muốn đảm bảo không chia đôi code point, **đầu vào phải là ranh giới hợp lệ**.

### Hàm hỗ trợ kiểm tra ranh giới surrogate

Một **ranh giới code point hợp lệ** không được nằm giữa high surrogate và low surrogate kề nhau. Phương thức sau kiểm tra điều kiện đó:

~~~java
static boolean isCodePointBoundary(String text, int index) {
    if (index < 0 || index > text.length()) {
        return false;
    }
    if (index == 0 || index == text.length()) {
        return true;
    }
    return !(Character.isHighSurrogate(text.charAt(index - 1))
            && Character.isLowSurrogate(text.charAt(index)));
}
~~~

Đây là **đoạn mã phương thức**; cần đặt trong một lớp Java. Ví dụ với **"A😊B"**: chỉ số **1** và **3** là ranh giới, **2** không phải. Hai đầu **0** và **4** cũng là ranh giới.

Phương thức này chỉ đảm bảo **không cắt giữa một surrogate pair**, chứ **không xác nhận toàn bộ String là UTF-16 hợp lệ**. Java String có thể chứa các surrogate lẻ vốn đã có sẵn trong dữ liệu.

## 7. Ví dụ Java 17+: kiểm tra ranh giới trước khi đếm

Ví dụ sau thích hợp khi API của bạn muốn đếm **các code point hoàn chỉnh** và cần từ chối khoảng cắt đôi surrogate pair. Lưu thành **SafeCodePointRangeDemo.java**:

~~~java
public class SafeCodePointRangeDemo {
    static boolean isBoundary(String text, int index) {
        if (index < 0 || index > text.length()) {
            return false;
        }
        if (index == 0 || index == text.length()) {
            return true;
        }
        return !(Character.isHighSurrogate(text.charAt(index - 1))
                && Character.isLowSurrogate(text.charAt(index)));
    }

    static int countOnBoundaries(String text, int begin, int end) {
        if (text == null) {
            throw new IllegalArgumentException("text is null");
        }
        if (begin > end ||
                !isBoundary(text, begin) ||
                !isBoundary(text, end)) {
            throw new IllegalArgumentException(
                    "range splits a surrogate pair or is invalid");
        }
        return text.codePointCount(begin, end);
    }

    public static void main(String[] args) {
        String text = "A😊B";

        System.out.println(isBoundary(text, 1));
        System.out.println(isBoundary(text, 2));
        System.out.println(isBoundary(text, 3));
        System.out.println(countOnBoundaries(text, 0, 3));

        try {
            System.out.println(countOnBoundaries(text, 0, 2));
        } catch (IllegalArgumentException ex) {
            System.out.println("Rejected a split surrogate pair");
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 SafeCodePointRangeDemo.java
java SafeCodePointRangeDemo
~~~

Stdout:

~~~text
true
false
true
2
Rejected a split surrogate pair
~~~

Phương thức **countOnBoundaries** chọn chính sách **từ chối khoảng nằm giữa một surrogate pair**. Đây là ràng buộc **tự viết thêm**, không phải hành vi mặc định của **String.codePointCount()**. API chuẩn cho phép đếm cả các khoảng như **[0, 2)** và sẽ tính surrogate lẻ là một code point.

Các ứng dụng cần bảo đảm dữ liệu Unicode chặt chẽ còn phải có bước xác thực **không có surrogate lẻ trong toàn bộ vùng dữ liệu**. Kiểm tra ranh giới không thay thế cho xác thực đó.

## 8. Dấu tiếng Việt và emoji phức hợp: đếm code point khác đếm grapheme

Một dấu tiếng Việt có thể được mã hóa bằng **combining mark**; ví dụ:

~~~java
String composed = "\u00E1";     // á, một code point dựng sẵn
String decomposed = "a\u0301"; // a + dấu sắc kết hợp

System.out.println(composed.codePointCount(0, composed.length()));     // 1
System.out.println(decomposed.codePointCount(0, decomposed.length())); // 2
~~~

Hai giá trị thường **hiển thị** giống nhau, nhưng **codePointCount()** có kết quả khác nhau. Nguyên nhân là phương thức này **không tự chuẩn hóa** NFC/NFD. Nếu nghiệp vụ cần so sánh hai dạng canonical tương đương, có thể áp dụng **java.text.Normalizer** nhất quán:

~~~java
import java.text.Normalizer;

// Các lệnh sau đặt bên trong một phương thức:
String a = Normalizer.normalize("\u00E1", Normalizer.Form.NFC);
String b = Normalizer.normalize("a\u0301", Normalizer.Form.NFC);
System.out.println(a.equals(b)); // true
~~~

Phần code phía trên là **đoạn minh họa có import**, không phải chương trình độc lập. Chuẩn hóa không làm mất dấu tiếng Việt, còn phép bỏ dấu là một yêu cầu nghiệp vụ khác.

Tương tự:

- Emoji **😊**: 2 UTF-16 code unit nhưng 1 code point.
- Emoji **👍🏽**: 4 UTF-16 code unit, 2 code point, thường hiển thị thành một biểu tượng.
- Emoji **👩‍💻**: 5 UTF-16 code unit, 3 code point, thường hiển thị thành một biểu tượng nhờ ZERO WIDTH JOINER.
- Emoji quốc kỳ **🇻🇳**: 4 UTF-16 code unit, 2 code point, thường hiển thị thành một lá cờ.

Vì vậy, ngay cả khi đưa toàn bộ chuỗi vào **codePointCount(0, length())**, con số nhận được có thể **khác số ký tự người dùng nhìn thấy**. Nếu yêu cầu là cắt hoặc giới hạn ký tự hiển thị, cần phân đoạn **extended grapheme cluster**, có thể dùng regex Java **Pattern.compile("\\X")** trong JDK hỗ trợ hoặc thư viện Unicode chuyên dụng khi cần kiểm soát phiên bản Unicode.

## 9. So sánh codePointCount với các API liên quan

| API | Trả về | Chỉ số/đầu vào | Lưu ý |
| --- | --- | --- | --- |
| **length()** | Số UTF-16 code unit trong toàn String | Không tham số | Không phải số code point với emoji ngoài BMP |
| **charAt(i)** | Một **char** (một code unit) | **i** là UTF-16 index | Có thể trả về một nửa surrogate pair |
| **codePointAt(i)** | Code point bắt đầu ở **i**, nếu có cặp hợp lệ | **i** là UTF-16 index | Nếu bắt đầu tại low surrogate lẻ, trả về giá trị code unit đó |
| **codePointBefore(i)** | Code point ngay trước **i** | **i** là UTF-16 index, bắt đầu từ 1 | Xét cặp surrogate theo hướng lùi |
| **codePointCount(begin, end)** | Số code point trong đoạn UTF-16 nửa mở | Hai offset UTF-16 | Surrogate lẻ trong đoạn đếm là 1 |
| **offsetByCodePoints(i, delta)** | Offset UTF-16 sau khi di chuyển theo code point | **i** là UTF-16 index; delta là số code point có dấu | Có thể dùng để tìm điểm cuối phù hợp cho substring |
| **codePoints().count()** | Số code point trong cả String dưới dạng **long** | Không có chỉ số đoạn trực tiếp | Nếu chỉ cần tổng số toàn chuỗi, có thể dùng **codePointCount(0, length())** |
| **substring(begin, end)** | String con trên khoảng UTF-16 | Hai offset UTF-16 | Có thể cắt đôi surrogate pair nếu dùng sai offset |

**codePoints()** trả về **IntStream** các giá trị code point. Số lượng điểm mã của **toàn chuỗi** thu được bằng **text.codePoints().count()** và **text.codePointCount(0, text.length())** sẽ bằng nhau khi cùng xét một String; **codePoints().count()** trả về **long**, còn **codePointCount()** trả về **int**.

Về chi phí, việc đếm số code point trong khoảng cần duyệt các code unit liên quan để nhận diện surrogate pair; nó không được hiểu là thao tác lấy độ dài **O(1)** giống như đọc thuộc tính **length()** của String. Với dữ liệu lớn và nhiều phép truy vấn, cân nhắc lưu sẵn thông tin chỉ mục ở các **ranh giới hợp lệ** hoặc thay đổi cấu trúc dữ liệu phù hợp, thay vì liên tục quét lại các khoảng.

## 10. Mẫu áp dụng vào bài toán thực tế

### Tình huống 1: Đếm nội dung giữa hai vị trí Java đã biết

Nếu **begin** và **end** đến từ **Matcher.start()/end()**, **String.indexOf()**, hoặc một API Java trả về offset UTF-16, có thể gọi:

~~~java
int codePoints = text.codePointCount(begin, end);
~~~

Trước khi diễn giải kết quả như số ký tự đầy đủ, kiểm tra ranh giới có tách surrogate pair hay không. Không phải mọi đoạn con hoặc vị trí do ứng dụng tự tính đều được bảo đảm nằm tại ranh giới code point.

### Tình huống 2: Lấy 10 code point đầu

Không dùng **substring(0, 10)** nếu có thể xuất hiện emoji, vì 10 code point có thể cần nhiều hơn 10 code unit UTF-16.

~~~java
int amount = Math.min(10, text.codePointCount(0, text.length()));
int endIndex = text.offsetByCodePoints(0, amount);
String prefix = text.substring(0, endIndex);
~~~

Đoạn này giữ tối đa 10 code point tính từ đầu String, không tự bảo đảm giữ trọn một grapheme gồm nhiều code point. Với yêu cầu “10 ký tự nhìn thấy”, thay bằng phân đoạn grapheme.

### Tình huống 3: Đếm phần đã nhập từ đầu tới vị trí caret

Nếu caret tính bằng offset UTF-16 **và** trỏ vào ranh giới code point hợp lệ:

~~~java
int pointsBeforeCaret = text.codePointCount(0, caretUtf16Index);
~~~

Nếu con trỏ có thể nằm giữa surrogate pair, cần điều chỉnh về một ranh giới hợp lệ trước khi diễn giải kết quả. Nếu giao diện đếm theo grapheme thay vì code point, sử dụng chỉ số grapheme độc lập.

### Tình huống 4: Chia đoạn để xử lý song song hoặc phân trang

Khi chia tại các offset UTF-16, một điểm chia không được đi qua surrogate pair nếu các phần sẽ được xử lý như văn bản Unicode hoàn chỉnh. Nếu cắt tại vị trí giữa cặp, tổng **codePointCount** của các phần có thể lớn hơn tổng thực sự của đoạn ban đầu. Nên xác định ranh giới chia dựa trên **offsetByCodePoints()** hoặc kiểm tra ranh giới trước khi cắt; nếu cần bảo toàn ký tự hiển thị, dùng grapheme boundary.

## 11. Những lỗi phổ biến và cách tránh

1. **Tưởng beginIndex/endIndex là chỉ số code point.** Đây là offset UTF-16. Chuyển chỉ số bằng **offsetByCodePoints()** trước khi gọi phương thức cần offset.
2. **Tưởng đếm từng đoạn rồi cộng luôn bằng tổng.** Điều này sai nếu điểm chia cắt một surrogate pair; hãy chia tại ranh giới code point hợp lệ.
3. **Lấy beginIndex == endIndex làm lỗi.** Khoảng rỗng hợp lệ và trả về 0.
4. **Dùng endIndex = length() - 1 để đếm toàn chuỗi.** Vì khoảng nửa mở, muốn đếm toàn bộ phải truyền **endIndex = length()**.
5. **Coi surrogate lẻ là lỗi tự động ném exception.** API **codePointCount()** đếm chúng là 1; xác thực UTF-16 hợp lệ là trách nhiệm riêng.
6. **Coi số code point là số chữ nhìn thấy.** Combining mark và emoji ghép có thể là nhiều code point trong một grapheme.
7. **Tự cắt bằng substring ở offset tùy ý.** Có thể sinh surrogate lẻ, gây kết quả khó đoán khi hiển thị hoặc mã hóa sang UTF-8.
8. **Nghĩ codePointCount tự chuẩn hóa chữ tiếng Việt.** NFC và NFD có thể khác số code point; chuẩn hóa phải là một bước tường minh theo nghiệp vụ.
9. **Dùng codePointCount để suy ra số byte lưu trữ.** Độ dài byte phụ thuộc charset; để biết byte UTF-8, dùng **text.getBytes(StandardCharsets.UTF_8).length**.

## 12. Tự kiểm tra

**Câu 1.** Với **String s = "A😊B"**, kết quả **s.length()** và **s.codePointCount(0, s.length())** là gì?

**Đáp án:** 4 code unit và 3 code point.

**Câu 2.** Vì sao **s.codePointCount(0, 2) + s.codePointCount(2, 4)** bằng 4 thay vì 3?

**Đáp án:** Ranh giới UTF-16 tại vị trí 2 nằm giữa high và low surrogate của emoji; mỗi nửa khi đứng riêng trong một khoảng được đếm là 1.

**Câu 3.** Khoảng **[1, 1)** trả về gì?

**Đáp án:** 0 vì đây là khoảng rỗng, hợp lệ.

**Câu 4.** Muốn lấy hai code point đầu của **"A😊B"** thì dùng điểm cuối UTF-16 nào?

**Đáp án:** **s.offsetByCodePoints(0, 2)** trả về 3; dùng **s.substring(0, 3)** để lấy **"A😊"**.

**Câu 5.** **codePointCount** có tự từ chối surrogate lẻ hoặc tự đếm emoji **👩‍💻** là một ký tự không?

**Đáp án:** Không. Surrogate lẻ tính một đơn vị trong phép đếm; emoji ZWJ này gồm 3 code point và thường là 1 grapheme cluster.

**Câu 6.** Với chữ **"á"** ở NFC và chữ **"a\u0301"** ở NFD, vì sao kết quả có thể khác?

**Đáp án:** NFC có một code point dựng sẵn, còn dạng kết hợp gồm hai code point. Phương thức không tự normalize.

## 13. Ghi nhớ

- **String.codePointCount(beginIndex, endIndex)** đếm **Unicode code point** trên khoảng **UTF-16 nửa mở [beginIndex, endIndex)**.
- **Hai chỉ số đều là offset UTF-16**; giá trị trả về là **số code point**, không phải vị trí hay số grapheme.
- **Một surrogate pair đầy đủ được đếm một lần; surrogate lẻ trong khoảng được đếm một lần mỗi phần**.
- **Không cắt surrogate pair** khi muốn tách chuỗi thành những vùng văn bản Unicode nguyên vẹn; khi cần tính tổng giữa nhiều đoạn, mọi ranh giới chia phải được kiểm tra.
- **offsetByCodePoints()** giúp di chuyển theo code point và trả lại offset UTF-16 đúng loại để dùng với **substring()**.
- Tiếng Việt có dấu kết hợp và emoji nhiều thành phần cho thấy rõ **code point không tương đương ký tự hiển thị**.

Tham khảo API: **Java SE 17, java.lang.String#codePointCount(int,int)**, **String#offsetByCodePoints(int,int)**, **Character#isHighSurrogate(char)**, **Character#isLowSurrogate(char)** và quy tắc Unicode về UTF-16.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-access-005">offsetByCodePoints để di chuyển theo code point</a>

<details>
<summary>Xem chi tiết</summary>

# `String.offsetByCodePoints()` — di chuyển theo Unicode code point trong Java

## 1. Mục tiêu: vị trí UTF-16 khác với số lượng code point

Trong Java, `String` là dãy các **đơn vị mã UTF-16** (`char`). Chỉ số dùng bởi `String.charAt()`, `substring()`, `codePointAt()` và `offsetByCodePoints()` đều là **chỉ số UTF-16**. Một Unicode code point trong Basic Multilingual Plane (BMP) thường chiếm một `char`; code point ngoài BMP chiếm **hai `char`** (một cặp *high surrogate* và *low surrogate*).

Vì vậy, di chuyển chỉ số thêm `1` đơn vị UTF-16 (`index++`) không luôn đồng nghĩa với di chuyển thêm một code point. `offsetByCodePoints` giải quyết đúng vấn đề này: **nhận một vị trí UTF-16 ban đầu và số code point muốn đi qua; trả về một vị trí UTF-16 mới**.

Ví dụ với chuỗi `"A🙂B"`:

```text
Nội dung nhìn thấy:       A       🙂        B
Unicode code point:      U+0041  U+1F642   U+0042
Độ dài UTF-16:              1       2        1

Chỉ số UTF-16:            0   1   2   3   4
                          | A |  🙂   | B |
Vị trí hợp lệ giữa
các code point:          ^   ^       ^   ^
                         0   1       3   4

index 2 nằm GIỮA hai char tạo thành emoji.
```

`"A🙂B".length()` bằng **4**, còn `codePointCount(0, length())` bằng **3**. Muốn di chuyển hai code point từ đầu chuỗi phải đến chỉ số UTF-16 **3**, không phải **2**.

## 2. Chữ ký và hợp đồng của `offsetByCodePoints`

API thuộc `java.lang.String` (có từ Java 5, dùng trực tiếp trong Java 17+):

```java
public int offsetByCodePoints(int index, int codePointOffset)
```

| Tham số / kết quả | Đơn vị | Ý nghĩa |
| --- | --- | --- |
| `index` | Chỉ số UTF-16 | Vị trí bắt đầu di chuyển; hợp lệ trong khoảng `0..length()`, **bao gồm** `length()` |
| `codePointOffset` | Số code point | Số lượng code point cần đi qua: dương tiến về cuối, âm lùi về đầu, 0 đứng yên |
| Giá trị trả về `int` | Chỉ số UTF-16 | Vị trí mới có thể truyền vào các API dùng offset UTF-16, ví dụ `substring` |
| Ngoại lệ | — | `IndexOutOfBoundsException` nếu vị trí ban đầu sai hoặc số bước yêu cầu vượt phần văn bản còn lại theo hướng di chuyển |

Ví dụ về cách hiểu (giả sử `String text = "A🙂B";`):

```java
text.offsetByCodePoints(0, 0);  // 0: không di chuyển
text.offsetByCodePoints(0, 1);  // 1: đi qua 'A'
text.offsetByCodePoints(0, 2);  // 3: đi qua 'A' và '🙂'
text.offsetByCodePoints(0, 3);  // 4: đi qua cả ba code point
text.offsetByCodePoints(4, -1); // 3: lùi qua 'B'
text.offsetByCodePoints(4, -2); // 1: lùi qua 'B' và '🙂'
text.offsetByCodePoints(4, -3); // 0: về đầu chuỗi
```

Những dòng trên là **snippet biểu thức**, chưa phải chương trình độc lập. Điểm cần ghi nhớ: tham số thứ hai tính bằng **code point**, còn **kết quả vẫn là chỉ số UTF-16**.

`offsetByCodePoints` không thay đổi `String`, không trả code point, không trả số ký tự và không cắt chuỗi. Nó chỉ tính một offset mới từ vị trí hiện tại.

## 3. Cơ chế tiến và lùi qua surrogate pair

Một code point ngoài BMP được mã hóa trong UTF-16 thành hai đơn vị `char` liên tiếp:
- *High surrogate*: giá trị `char` trong khoảng `U+D800..U+DBFF`.
- *Low surrogate*: giá trị `char` trong khoảng `U+DC00..U+DFFF`.

Khi bắt đầu tại **ranh giới code point hợp lệ**:
- Di chuyển **tiến** qua ký tự BMP thông thường tăng chỉ số UTF-16 thêm 1; tiến qua một cặp surrogate hợp lệ tăng thêm 2.
- Di chuyển **lùi** qua ký tự BMP thông thường giảm chỉ số 1; lùi qua một cặp surrogate hợp lệ giảm 2.
- Nếu dãy UTF-16 có **surrogate lẻ, không ghép cặp**, Java tính mỗi surrogate lẻ là một code point khi di chuyển. Phương thức không tự chuẩn hóa hoặc sửa dữ liệu đầu vào.

Do đó bạn không thể thay thế tổng quát bằng `index + codePointOffset`, kể cả khi chuỗi “trông có vẻ” chỉ gồm vài ký tự. Nội dung Unicode quyết định số đơn vị UTF-16 phải đi qua.

### Ví dụ Java 17+ chạy độc lập: tiến, lùi và cắt đúng ranh giới

Lưu thành `OffsetByCodePointsDemo.java`:

```java
public class OffsetByCodePointsDemo {
    public static void main(String[] args) {
        String text = "A🙂B";

        int p0 = 0;
        int p1 = text.offsetByCodePoints(p0, 1);
        int p2 = text.offsetByCodePoints(p1, 1);
        int p3 = text.offsetByCodePoints(p2, 1);

        System.out.println("UTF-16 length: " + text.length());
        System.out.println(
                "Code points: " + text.codePointCount(0, text.length()));
        System.out.println(
                "Boundaries: " + p0 + " -> " + p1 + " -> " + p2 + " -> " + p3);
        System.out.println("Forward 2: " + text.offsetByCodePoints(0, 2));
        System.out.println(
                "Back 2: " + text.offsetByCodePoints(text.length(), -2));
        System.out.println("Emoji: " + text.substring(p1, p2));
        System.out.println(
                "Code point: U+" +
                Integer.toHexString(text.codePointAt(p1)).toUpperCase());
    }
}
```

Biên dịch và chạy bằng JDK 17+ (nguồn mã UTF-8):

```bash
javac -encoding UTF-8 OffsetByCodePointsDemo.java
java OffsetByCodePointsDemo
```

```text
UTF-16 length: 4
Code points: 3
Boundaries: 0 -> 1 -> 3 -> 4
Forward 2: 3
Back 2: 1
Emoji: 🙂
Code point: U+1F642
```

Chỉ số `p1 = 1` ngay trước emoji; vì emoji chiếm hai `char` nên `p2 = 3`. `substring(p1,p2)` lấy đúng hai đơn vị UTF-16 biểu diễn **một** code point emoji. `codePointAt(p1)` đọc code point tại chỉ số UTF-16 1, trả `0x1F642`; `charAt(p1)` chỉ trả high surrogate.

## 4. Di chuyển từng code point trong vòng lặp

### Duyệt tiến

Khi viết thuật toán duyệt từng code point, dùng một biến `int index` đại diện cho **vị trí UTF-16 ở đầu code point tiếp theo**. Bắt đầu tại 0, tiếp tục đến khi `index == text.length()`:

```java
for (int index = 0; index < text.length(); ) {
    int codePoint = text.codePointAt(index);
    // Xử lý codePoint ở đây.
    index = text.offsetByCodePoints(index, 1);
}
```

Không thêm `index++` vào phần cuối vòng lặp: số `char` của code point vừa đọc có thể là 1 hoặc 2. Có thể thay bước di chuyển bằng `index += Character.charCount(codePoint)` khi `codePoint` được đọc đúng từ `codePointAt(index)`, nhưng `offsetByCodePoints` thể hiện trực tiếp ý định di chuyển theo code point và hỗ trợ cả hướng âm.

### Duyệt lùi

Muốn đi ngược từ cuối chuỗi, đặt `index = text.length()` và lùi một code point **trước khi** gọi `codePointAt`:

```java
for (int index = text.length(); index > 0; ) {
    index = text.offsetByCodePoints(index, -1);
    int codePoint = text.codePointAt(index);
    // Xử lý codePoint ở đây.
}
```

Vị trí `length()` hợp lệ đối với `offsetByCodePoints`; nó là ranh giới **sau ký tự cuối**. Nhưng `text.codePointAt(text.length())` không hợp lệ, vì `codePointAt` cần vị trí của một phần tử `char` thực tế, từ 0 đến `length()-1`.

### Ví dụ hai chiều chạy độc lập

Lưu thành `WalkCodePoints.java`:

```java
public class WalkCodePoints {
    public static void main(String[] args) {
        String value = "A🙂B";

        StringBuilder forward = new StringBuilder();
        for (int i = 0; i < value.length(); ) {
            int next = value.offsetByCodePoints(i, 1);
            forward.append('[').append(value, i, next).append(']');
            i = next;
        }

        StringBuilder backward = new StringBuilder();
        for (int i = value.length(); i > 0; ) {
            int previous = value.offsetByCodePoints(i, -1);
            backward.append(value, previous, i);
            i = previous;
        }

        System.out.println(forward);
        System.out.println(backward);
    }
}
```

```bash
javac -encoding UTF-8 WalkCodePoints.java
java WalkCodePoints
```

```text
[A][🙂][B]
B🙂A
```

Bước `append(value, i, next)` lấy một đoạn `CharSequence` theo **chỉ số UTF-16 [i, next)**, nên emoji không bị cắt ở giữa hai surrogate. Vòng lặp lùi thu được các code point theo thứ tự đảo ngược. Tuy nhiên, đảo thứ tự **code point** không phải lúc nào cũng tương đương đảo thứ tự **ký tự hiển thị** (ví dụ ký tự có dấu tổ hợp), vì một grapheme cluster có thể gồm nhiều code point.

## 5. Dùng `offsetByCodePoints` với `substring`

`String.substring(beginIndex, endIndex)` nhận **chỉ số UTF-16**. Nếu yêu cầu nghiệp vụ lại nói “lấy từ code point thứ `a` đến trước code point thứ `b`”, không nên truyền `a` và `b` trực tiếp vào `substring`.

Cách đúng là:
1. Kiểm tra chỉ số tính theo code point và tổng số code point.
2. Đổi offset code point đầu thành chỉ số UTF-16 qua `offsetByCodePoints(0, beginCodePoint)`.
3. Từ ranh giới đầu, tiến `endCodePoint - beginCodePoint` code point để tìm ranh giới cuối.
4. Gọi `substring(beginUtf16, endUtf16)`.

Ví dụ hoàn chỉnh, lưu `SliceByCodePoints.java`:

```java
import java.util.Objects;

public class SliceByCodePoints {
    static String slice(String text, int beginCodePoint, int endCodePoint) {
        Objects.requireNonNull(text, "text");

        int total = text.codePointCount(0, text.length());
        if (beginCodePoint < 0
                || endCodePoint < beginCodePoint
                || endCodePoint > total) {
            throw new IndexOutOfBoundsException("Invalid code point range");
        }

        int beginUtf16 = text.offsetByCodePoints(0, beginCodePoint);
        int endUtf16 = text.offsetByCodePoints(
                beginUtf16, endCodePoint - beginCodePoint);
        return text.substring(beginUtf16, endUtf16);
    }

    public static void main(String[] args) {
        String text = "A🙂B🚀";

        System.out.println(slice(text, 1, 3));
        System.out.println(slice(text, 0, 2));
        System.out.println(slice(text, 3, 4));
        System.out.println(slice(text, 2, 2).isEmpty());
    }
}
```

```bash
javac -encoding UTF-8 SliceByCodePoints.java
java SliceByCodePoints
```

```text
🙂B
A🙂
🚀
true
```

Hàm này xem `beginCodePoint` là vị trí đầu **tính theo số code point**, `endCodePoint` là ranh giới cuối **loại trừ**. Nó chuyển hai vị trí đó về offset UTF-16 trước khi cắt. Chỉ số trong `substring` là 0-based theo đơn vị `char`; chỉ số đầu/cuối mà hàm `slice` nhận là 0-based theo ranh giới code point. Hai hệ tọa độ không được dùng lẫn nhau.

**Khi dữ liệu có surrogate lẻ:** phép đếm và di chuyển của Java vẫn có quy tắc nhất quán (mỗi surrogate lẻ tính một code point), nhưng hàm trên không phải bộ kiểm định chuỗi Unicode hợp lệ. Ứng dụng đòi hỏi Unicode scalar value hợp lệ nên kiểm tra dữ liệu riêng.

## 6. Ranh giới và ngoại lệ

`offsetByCodePoints(index, codePointOffset)` sẽ ném `IndexOutOfBoundsException` nếu:
- `index < 0` hoặc `index > text.length()`.
- `codePointOffset > 0` nhưng không còn đủ code point ở đoạn sau `index`.
- `codePointOffset < 0` nhưng không có đủ code point ở đoạn trước `index`.

Các tình huống điển hình với `String text = "A🙂B";`:

| Lệnh | Kết quả | Vì sao |
| --- | --- | --- |
| `text.offsetByCodePoints(0, 0)` | `0` | Giữ nguyên vị trí đầu |
| `text.offsetByCodePoints(4, 0)` | `4` | Giữ nguyên vị trí cuối |
| `text.offsetByCodePoints(0, 3)` | `4` | Đi qua đúng 3 code point |
| `text.offsetByCodePoints(4, -3)` | `0` | Lùi đúng 3 code point |
| `text.offsetByCodePoints(4, 1)` | Ngoại lệ | Không còn code point phía sau |
| `text.offsetByCodePoints(0, -1)` | Ngoại lệ | Không có code point phía trước |
| `text.offsetByCodePoints(0, 4)` | Ngoại lệ | Chuỗi chỉ có 3 code point |
| `text.offsetByCodePoints(5, 0)` | Ngoại lệ | Chỉ số UTF-16 ngoài phạm vi |

Nếu cần xử lý việc “không đủ bước” như một kết quả nghiệp vụ bình thường, hãy kiểm tra trước số code point khả dụng bằng `codePointCount(index, length())` khi tiến, hoặc `codePointCount(0, index)` khi lùi, **với điều kiện `index` đã ở một ranh giới code point thích hợp**. Đừng nuốt mọi `IndexOutOfBoundsException`: lỗi có thể cho thấy chương trình đã trộn lẫn chỉ số UTF-16 và chỉ số code point.

### Không tự động sửa vị trí nằm giữa surrogate pair

Đây là một trường hợp tinh tế. `index` về mặt API chỉ cần thỏa `0 <= index <= length()`; phương thức **không yêu cầu kiểm tra** rằng `index` bắt đầu tại ranh giới code point và cũng **không tự dịch** nó về đầu cặp surrogate.

Với `text = "A🙂B"`, `index = 2` nằm ở **low surrogate** của emoji:

```java
String text = "A🙂B";
System.out.println(Character.isLowSurrogate(text.charAt(2))); // true
System.out.println(text.offsetByCodePoints(2, 1));            // 3
System.out.println(text.offsetByCodePoints(2, -1));           // 1
```

Đây là snippet đặt trong `main`. Khi tiến từ vị trí giữa cặp, nửa low surrogate ở phía sau được tính như một đơn vị lẻ trong phạm vi cần đi; khi lùi, nửa high surrogate ở phía trước cũng được tính riêng. Kết quả **không** có nghĩa vị trí 2 là ranh giới hợp lý để xử lý emoji trọn vẹn.

**Quy tắc thực hành:** luôn tạo con trỏ từ đầu hoặc cuối chuỗi rồi di chuyển bằng `offsetByCodePoints`; hoặc chuyển vị trí code point sang UTF-16 một lần trước khi dùng. Không lấy một chỉ số `charAt` bất kỳ rồi mặc định nó nằm ở đầu code point.

## 7. Surrogate lẻ và những điều `offsetByCodePoints` không làm

Một chuỗi Java có thể chứa một high surrogate không đi kèm low surrogate hợp lệ, hoặc một low surrogate đơn độc. Trong phạm vi xét bởi `offsetByCodePoints`, **surrogate lẻ được đếm là một code point** theo hợp đồng Java. Điều này không biến nó thành Unicode scalar value hợp lệ và không có nghĩa Java “sửa” văn bản.

Ví dụ độc lập, lưu `UnpairedSurrogateDemo.java`:

```java
public class UnpairedSurrogateDemo {
    public static void main(String[] args) {
        char[] chars = {'A', '\uD83D', 'B'};
        String text = new String(chars);

        System.out.println(text.length());
        System.out.println(text.codePointCount(0, text.length()));
        System.out.println(text.offsetByCodePoints(1, 1));
        System.out.println(
                Integer.toHexString(text.codePointAt(1)));
    }
}
```

```bash
javac -encoding UTF-8 UnpairedSurrogateDemo.java
java UnpairedSurrogateDemo
```

```text
3
3
2
d83d
```

Mảng có ba đơn vị UTF-16: `'A'`, một high surrogate lẻ `\uD83D`, rồi `'B'`. Phương thức đếm ba code point theo quy tắc Java và di chuyển từ offset 1 sang 2 qua surrogate lẻ. Đây là cách API xử lý dữ liệu chưa ghép cặp, không phải ví dụ một ký tự Unicode hợp lệ có mã U+D83D.

Một giới hạn khác: **code point không đồng nghĩa với grapheme cluster**. Ví dụ `"e\u0301"` là chữ `e` theo sau bằng dấu sắc kết hợp. Nó có **hai code point** nhưng thường hiển thị như một chữ “é”. Di chuyển một code point sẽ đứng giữa chữ cơ sở và dấu kết hợp. Các chuỗi emoji nối bằng ZWJ hay biểu tượng cờ còn có thể gồm nhiều code point cho một ký tự người dùng nhìn thấy. Nếu yêu cầu là di chuyển theo *grapheme cluster* để xử lý caret/chọn văn bản, cần thuật toán phân đoạn ký tự phù hợp với Unicode; `offsetByCodePoints` chỉ đáp ứng mức code point.

## 8. Đặt cạnh các phương thức liên quan

| API | Đầu vào là chỉ số gì? | Đầu ra là gì? | Công dụng |
| --- | --- | --- | --- |
| `length()` | Không có | Số **char** UTF-16 | Độ dài bộ lưu trữ logic theo UTF-16 |
| `charAt(i)` | UTF-16 | Một `char` | Đọc đúng một đơn vị UTF-16 |
| `codePointAt(i)` | UTF-16 | Một `int` code point | Đọc code point tại vị trí đầu (nếu là high surrogate có low theo sau thì ghép đôi) |
| `codePointBefore(i)` | UTF-16 ranh giới sau phần tử | Một `int` code point | Đọc code point ngay trước vị trí đó |
| `codePointCount(begin,end)` | Hai chỉ số UTF-16 | **Số** code point trong đoạn | Đếm, không trả chỉ số |
| `offsetByCodePoints(index,n)` | UTF-16 + **số** code point | Chỉ số UTF-16 | Di chuyển con trỏ |
| `substring(begin,end)` | Hai chỉ số UTF-16 | `String` | Cắt đoạn theo ranh giới cung cấp |
| `codePoints()` | Không có | `IntStream` | Duyệt giá trị code point |
| `chars()` | Không có | `IntStream` của đơn vị `char` | Duyệt từng đơn vị UTF-16 |

Cần phân biệt đặc biệt hai lời gọi:

```java
int howMany = text.codePointCount(0, text.length()); // SỐ lượng
int where = text.offsetByCodePoints(0, 2);           // VỊ TRÍ UTF-16
```

Không đưa `howMany` thẳng vào `substring` như một chỉ số UTF-16. `codePointCount` và `offsetByCodePoints` là hai phép toán khác nhau: **đếm** và **đổi vị trí sau khi di chuyển**.

Nếu chỉ muốn duyệt tuần tự toàn bộ chuỗi theo code point mà không cần vị trí, `text.codePoints()` có thể đơn giản hơn. Nếu cần vị trí để tương tác với `substring`, `regionMatches`, hoặc ghi nhớ con trỏ UTF-16, `offsetByCodePoints` hữu ích.

Java cũng cung cấp `Character.offsetByCodePoints(CharSequence seq, int index, int codePointOffset)`; phiên bản này nhận một `CharSequence`, cho phép thuật toán dùng với các lớp triển khai ngoài `String`. Ngoài ra còn overload cho một vùng `char[]` với `start`, `count` và `index`; ở đó cần tuân thủ ranh giới **của vùng mảng được chỉ định**, không mặc định toàn bộ mảng. Dù API nào, kiểu chỉ số trả về vẫn là offset theo đơn vị UTF-16.

## 9. Thiết kế hàm di chuyển an toàn

Một API lưu vị trí trỏ vào `String` nên xác định rõ hệ tọa độ. Ví dụ:
- Tên `utf16Offset` cho vị trí đưa vào `charAt`/`substring`/`offsetByCodePoints`.
- Tên `codePointOffset` hoặc `codePointIndex` cho số bước hoặc thứ tự code point.
- Khi cần đi từ code point thứ `k` về chỉ số UTF-16, dùng `text.offsetByCodePoints(0, k)` sau khi đã kiểm tra `0 <= k <= text.codePointCount(0,text.length())`.
- Khi cần đi từ vị trí UTF-16 `p` tới vị trí tương ứng code point thứ mấy từ đầu, dùng `text.codePointCount(0,p)` **nếu `p` là ranh giới đúng**; nếu `p` cắt surrogate pair thì đây không còn là phép đổi vị trí nguyên vẹn.
- Nếu phải di chuyển nhiều bước liên tục, giữ và cập nhật vị trí hiện tại; tránh quét lại từ 0 sau mỗi bước.

Về hiệu năng, `offsetByCodePoints` phải xem xét nội dung trong phạm vi đi qua để nhận ra cặp surrogate; không giả định truy cập code point thứ `k` có chi phí hằng số. Duyệt tuần tự với vị trí được cập nhật là lựa chọn rõ ràng; nếu cần truy cập ngẫu nhiên rất nhiều vị trí code point trên cùng văn bản lớn, cân nhắc lưu chỉ số ranh giới đã tính trước hoặc dùng biểu diễn dữ liệu phù hợp và đo đạc thực tế trước khi tối ưu.

Với `StringBuilder` mutable, dữ liệu có thể thay đổi và các vị trí UTF-16 cũ trở nên không còn chính xác sau thao tác chèn/xóa. Không duyệt dựa trên các offset đã tính cũ trong lúc nội dung đang thay đổi mà không có quy tắc cập nhật vị trí thích hợp.

## 10. Các lỗi phổ biến

1. **Dùng `index++` để đi tới “ký tự Unicode kế tiếp”.** Nếu gặp emoji ngoài BMP, chỉ số sẽ đi vào giữa cặp surrogate. Dùng `offsetByCodePoints(index,1)` từ ranh giới hợp lệ.
2. **Nhầm giá trị trả về là số code point.** Kết quả của `offsetByCodePoints` là offset UTF-16; số code point trong đoạn lấy từ `codePointCount`.
3. **Dùng chỉ số code point trực tiếp với `substring`.** Chuyển hai đầu đoạn thành vị trí UTF-16 trước.
4. **Gọi `codePointAt(length())`.** Vị trí cuối hợp lệ làm **điểm xuất phát** để lùi, nhưng không hợp lệ để đọc code point phía sau.
5. **Không xét bước âm.** `codePointOffset` âm là hợp lệ và cần thiết cho duyệt lùi; nó có thể gây ngoại lệ nếu lùi quá đầu.
6. **Giả định chỉ số UTF-16 bất kỳ đều ở đầu code point.** Phương thức không tự sửa khi điểm bắt đầu nằm giữa surrogate pair.
7. **Cho rằng API luôn có dữ liệu Unicode hợp lệ.** Surrogate lẻ có thể tồn tại trong `String` và được Java tính như một code point trong các API liên quan.
8. **Nhầm code point với ký tự hiển thị.** Dấu tổ hợp, cờ, emoji ZWJ có thể chứa nhiều code point; cần phân đoạn grapheme nếu bài toán yêu cầu.
9. **Đo lường chi phí như truy cập phần tử mảng.** Di chuyển theo code point cần quét nội dung giữa các ranh giới; tránh tính lại từ đầu vô ích.

## 11. Câu hỏi tự kiểm tra

Với `String s = "X🙂Y";`, hãy tự trả lời:

1. `s.length()` và `s.codePointCount(0,s.length())` khác nhau thế nào?
2. `s.offsetByCodePoints(0,2)` trả về chỉ số mấy? Vì sao không phải `2`?
3. `s.offsetByCodePoints(s.length(),-2)` trả về chỉ số mấy?
4. Nếu muốn lấy riêng emoji bằng `substring`, phải truyền hai chỉ số UTF-16 nào?
5. Vì sao `s.offsetByCodePoints(2,1)` không phải cách đúng để tiến **từ đầu emoji** sang code point sau?

Đáp án: (1) `4` và `3`; (2) `3`; (3) `1`; (4) `substring(1,3)`; (5) vị trí `2` đã nằm giữa hai surrogate của emoji, không phải ranh giới code point để bắt đầu duyệt.

**Quy tắc cần nhớ:** `offsetByCodePoints` nhận **offset UTF-16 hiện tại** và **số bước code point**, sau đó trả lại **offset UTF-16 mới**. Hãy sinh và duy trì các vị trí ở ranh giới code point khi chuyển qua lại giữa duyệt Unicode và các API Java dùng chỉ số `char`.

## Tài liệu API chính thức (Java SE 17)

- [`String.offsetByCodePoints`, `codePointAt`, `codePointCount`, `substring` — Oracle Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html)
- [`Character.offsetByCodePoints`, `charCount`, surrogate helpers — Oracle Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html)
- [`CharSequence` và `codePoints()` — Oracle Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/CharSequence.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-access-006">getChars và sao chép một vùng sang char&#91;&#93;</a>

<details>
<summary>Xem chi tiết</summary>

# `String.getChars()`: sao chép một vùng của chuỗi sang `char[]`

## 1. Mục tiêu

`String.getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin)` sao chép **một vùng liên tiếp của `String` vào mảng ký tự `char[]` do người gọi cung cấp**. API hữu ích khi bạn cần đưa một phần văn bản vào bộ đệm có sẵn, giữ nguyên phần khác của bộ đệm, hoặc tích hợp với một API nhận `char[]`.

Điểm quan trọng nhất: các chỉ số của `String` và độ dài vùng sao chép được tính bằng **UTF-16 code unit** (`char`), không phải bằng số Unicode code point hay số ký tự người dùng nhìn thấy. Cần biết điều này để không vô tình chia đôi một ký tự ngoài BMP như `🚀`.

Sau bài này, bạn sẽ biết:
- Ý nghĩa chính xác của bốn tham số và ranh giới **`[srcBegin, srcEnd)`**.
- Cách kiểm tra vùng nguồn, vị trí đích và dung lượng mảng.
- Vì sao `getChars` có thể sao chép *một nửa surrogate pair*.
- Cách sao chép theo ranh giới code point một cách có chủ đích.
- Khi nào nên dùng `getChars` thay vì `toCharArray()`, `substring()` hoặc `getBytes()`.

## 2. Cú pháp và cơ chế

Phương thức trên đối tượng `String`:

```java
public void getChars(int srcBegin, int srcEnd,
                     char[] dst, int dstBegin)
```

Ví dụ ngắn:

```java
String source = "Java Core";
char[] buffer = {'_', '_', '_', '_', '_', '_', '_', '_'};

source.getChars(0, 4, buffer, 2);
System.out.println(new String(buffer)); // "__Java__"
```

Giải thích:
1. Từ chuỗi nguồn `"Java Core"`, lấy vùng **`[0, 4)`**, tức bốn code unit `J`, `a`, `v`, `a`.
2. Ghi bắt đầu ở phần tử **`buffer[2]`**. Các phần tử `buffer[2]` đến `buffer[5]` được thay bằng `J`, `a`, `v`, `a`.
3. Các phần tử `buffer[0]`, `buffer[1]`, `buffer[6]`, `buffer[7]` giữ nguyên dấu `_`.
4. Phương thức trả về **`void`**: nó **sửa nội dung mảng đích**; không trả về một mảng mới và không sửa `String` nguồn.

### Ý nghĩa bốn tham số

| Tham số | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| `srcBegin` | Chỉ số UTF-16 đầu tiên trong chuỗi nguồn, **bao gồm**. | `0` lấy từ đầu chuỗi. |
| `srcEnd` | Chỉ số UTF-16 ngay sau phần cuối cần lấy, **không bao gồm**. | `4` lấy đến trước vị trí 4. |
| `dst` | Mảng `char[]` đích đã tồn tại; các phần tử được ghi đè trong vùng nhận. | `buffer` |
| `dstBegin` | Chỉ số trong mảng đích để bắt đầu ghi. | `2` ghi từ `buffer[2]`. |

Số phần tử được sao chép là:

\[
n = \text{srcEnd} - \text{srcBegin}
\]

Với `i` chạy từ `0` đến `n - 1`, hiệu ứng tương đương về mặt nội dung:

```java
dst[dstBegin + i] = source.charAt(srcBegin + i);
```

Đây là diễn giải thao tác, **không phải** yêu cầu phải tự viết vòng lặp. Dùng trực tiếp `getChars` giúp thể hiện rõ ý định sao chép một vùng.

## 3. Chỉ số đầu/cuối và vị trí đích

Hãy nhìn vào ví dụ `"Java Core"` theo từng vị trí `char`:

| Chỉ số UTF-16 | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Ký tự | J | a | v | a | dấu cách | C | o | r | e |

Gọi `source.getChars(5, 9, buffer, 0)` sẽ lấy **`"Core"`** bởi `srcEnd = 9` là ranh giới **sau** chữ `e`. Với nguồn dài `9`, `srcEnd == source.length()` hoàn toàn hợp lệ.

### Những trường hợp biên thường gặp

- `source.getChars(0, source.length(), dst, 0)`: sao chép toàn bộ chuỗi, với điều kiện `dst` có đủ chỗ.
- `source.getChars(2, 2, dst, 0)`: vùng nguồn rỗng, **không ghi ký tự nào**.
- `source.getChars(source.length(), source.length(), dst, dst.length)`: vùng rỗng tại cuối chuỗi và cuối mảng cũng hợp lệ.
- `source.getChars(2, 5, dst, 4)`: sao chép `3` `char` vào các vị trí đích `4, 5, 6`; mảng đích phải có tối thiểu `7` phần tử.

Đừng nhầm `srcEnd` là số phần tử cần sao chép. Nếu muốn sao chép `count` phần tử bắt đầu từ `start`, dùng `srcBegin = start` và `srcEnd = start + count`, sau khi kiểm tra phạm vi.

## 4. Điều kiện hợp lệ và ngoại lệ

Với `int n = srcEnd - srcBegin`, các điều kiện về chỉ số là:

```text
0 <= srcBegin <= srcEnd <= source.length()
0 <= dstBegin <= dst.length
n <= dst.length - dstBegin
```

Trong đó, `n` được tính theo số **`char` UTF-16** cần ghi. Điều kiện cuối tránh ghi vượt kích thước mảng đích.

| Tình huống | Ví dụ | Kết quả |
| --- | --- | --- |
| Vùng nguồn hợp lệ | `"abc".getChars(0, 2, new char[2], 0)` | Sao chép `a`, `b`. |
| `srcBegin` âm | `"abc".getChars(-1, 2, new char[3], 0)` | `IndexOutOfBoundsException`. |
| `srcBegin > srcEnd` | `"abc".getChars(2, 1, new char[3], 0)` | `IndexOutOfBoundsException`. |
| `srcEnd` vượt độ dài | `"abc".getChars(0, 4, new char[4], 0)` | `IndexOutOfBoundsException`. |
| `dstBegin` âm | `"abc".getChars(0, 1, new char[3], -1)` | `IndexOutOfBoundsException`. |
| Thiếu chỗ ở đích | `"abc".getChars(0, 3, new char[3], 1)` | `IndexOutOfBoundsException`. |
| Mảng đích `null` | `"abc".getChars(0, 1, null, 0)` | `NullPointerException`. |
| Sao chép rỗng | `"abc".getChars(3, 3, new char[0], 0)` | Không sao chép; hợp lệ. |

Khi **nhiều điều kiện cùng sai**, không nên dựa vào thông báo ngoại lệ cụ thể hoặc thứ tự kiểm tra nội bộ. Tài liệu API nêu hợp đồng; chương trình của bạn nên xác thực cả giới hạn vùng nguồn và dung lượng đích.

Lưu ý: nếu tính `start + count` từ dữ liệu nguyên rất lớn, phép cộng kiểu `int` có thể tràn. Có thể kiểm tra không tràn bằng `Objects.checkFromIndexSize(start, count, source.length())` (Java 9+) trước khi tính ranh giới, rồi kiểm tra vùng đích tương tự.

## 5. Chương trình Java 17+ chạy độc lập

Lưu mã sau vào file `GetCharsDemo.java`:

```java
import java.util.Arrays;

public class GetCharsDemo {
    public static void main(String[] args) {
        String text = "A🚀B";
        char[] target = {'_', '_', '_', '_', '_', '_'};

        // Chép toàn bộ emoji, gồm hai code unit UTF-16.
        text.getChars(1, 3, target, 2);

        System.out.println("original = " + text);
        System.out.println("length = " + text.length());
        System.out.println("copied = [" + new String(target) + "]");
        System.out.println("target length = " + target.length);
        System.out.printf("dst[2] = U+%04X%n", (int) target[2]);
        System.out.printf("dst[3] = U+%04X%n", (int) target[3]);

        // Một vùng không an toàn: chỉ chép high surrogate.
        char[] partial = new char[1];
        text.getChars(1, 2, partial, 0);
        System.out.println("partial high surrogate = "
                + Character.isHighSurrogate(partial[0]));

        // Khoảng rỗng không thay đổi mảng đích.
        char[] unchanged = {'?', '?', '?'};
        text.getChars(2, 2, unchanged, 1);
        System.out.println("empty region = "
                + Arrays.toString(unchanged));
    }
}
```

Biên dịch và chạy trong terminal hỗ trợ Unicode:

```bash
javac -encoding UTF-8 GetCharsDemo.java
java GetCharsDemo
```

Đầu ra kỳ vọng:

```text
original = A🚀B
length = 4
copied = [__🚀__]
target length = 6
dst[2] = U+D83D
dst[3] = U+DE80
partial high surrogate = true
empty region = [?, ?, ?]
```

**Vì sao kết quả như vậy?**
- Chuỗi hiển thị là `A🚀B`, nhưng `length() == 4` vì emoji `🚀` là code point `U+1F680`, có hai code unit UTF-16: **high surrogate `U+D83D`** ở chỉ số `1` và **low surrogate `U+DE80`** ở chỉ số `2`.
- `getChars(1, 3, target, 2)` lấy vùng `[1, 3)`, tức **hai `char`** của emoji, đặt vào `target[2]` và `target[3]`. Hai dấu gạch dưới đầu và hai dấu gạch dưới cuối vẫn nguyên vẹn.
- `getChars(1, 2, partial, 0)` chỉ lấy **high surrogate**. Phương thức sao chép theo code unit nên không tự ghép hoặc bổ sung low surrogate còn thiếu.
- Khi `srcBegin == srcEnd`, số phần tử sao chép là `0`; `unchanged` vẫn giữ ba dấu hỏi.

Chương trình cố ý kiểm tra code unit bằng `%04X` thay vì in riêng từng nửa surrogate, vì một surrogate đơn lẻ có thể hiển thị như ký tự thay thế hoặc dấu hỏi tùy môi trường.

## 6. `getChars` sao chép `char`, không tự sao chép theo code point

Để hiểu quy tắc chỉ số, xét chuỗi `"A🚀B"`:

| UTF-16 index | `0` | `1` | `2` | `3` |
| --- | --- | --- | --- | --- |
| `charAt(index)` | `'A'` | `'\uD83D'` | `'\uDE80'` | `'B'` |

Ranh giới code point đầy đủ là `0, 1, 3, 4`. Vị trí `2` nằm **giữa** surrogate pair của emoji.

`String.getChars` **không xác thực ranh giới surrogate pair** và không có chế độ tự dịch chỉ số code point:

```java
String text = "A🚀B";

char[] correct = new char[2];
text.getChars(1, 3, correct, 0); // Cả hai char của 🚀.

char[] broken = new char[1];
text.getChars(1, 2, broken, 0); // Chỉ high surrogate.
```

Bởi vậy:
- Dùng chỉ số trực tiếp nếu mục tiêu thực sự là **UTF-16 code unit**.
- Nếu mục tiêu là **Unicode code point**, trước tiên tính ra ranh giới UTF-16 đúng.
- Nếu mục tiêu là một **ký tự hiển thị** (grapheme cluster) gồm nhiều code point, thao tác theo code point vẫn có thể tách cụm; cần quy tắc phân đoạn văn bản phù hợp.

### Chuyển khoảng code point thành khoảng UTF-16 rồi mới gọi `getChars`

Đoạn sau là một **phương thức hỗ trợ** đặt trong lớp Java:

```java
static char[] copyCodePoints(String text, int from, int count) {
    java.util.Objects.requireNonNull(text, "text");

    int total = text.codePointCount(0, text.length());
    java.util.Objects.checkFromIndexSize(from, count, total);

    int startUtf16 = text.offsetByCodePoints(0, from);
    int endUtf16 = text.offsetByCodePoints(startUtf16, count);

    char[] result = new char[endUtf16 - startUtf16];
    text.getChars(startUtf16, endUtf16, result, 0);
    return result;
}
```

Ví dụ gọi từ một `main`:

```java
String text = "A🚀B";
char[] rocket = copyCodePoints(text, 1, 1);
System.out.println(new String(rocket)); // 🚀
System.out.println(rocket.length);       // 2
```

Giải thích: `from = 1` bắt đầu tại code point thứ hai (`🚀`). `offsetByCodePoints` đổi vị trí code point sang chỉ số UTF-16: đầu `1`, cuối `3`. Mảng cần `3 - 1 = 2` `char` để giữ trọn emoji.

**Giới hạn:** `codePointCount` và `offsetByCodePoints` xem surrogate đơn lẻ có trong chuỗi là một code point theo cách xử lý của `String`. Chúng không tự sửa văn bản Unicode vốn đã có surrogate không ghép cặp và cũng không phân đoạn grapheme cluster.

## 7. Bộ đệm đích: ghi đè đúng vùng, phần khác giữ nguyên

`getChars` có lợi khi bạn cần tái sử dụng một mảng hoặc tạo thông điệp gồm nhiều phần. Ví dụ:

```java
String source = "học Java";
char[] destination = "........".toCharArray();

source.getChars(4, 8, destination, 2);
System.out.println(new String(destination)); // "..Java.."
```

Mảng đích ban đầu gồm tám dấu chấm. Bốn code unit của `"Java"` ghi vào các ô `2, 3, 4, 5`; các ô khác không bị ghi đè.

Để tránh lỗi khi tái sử dụng:
1. Tính `n = srcEnd - srcBegin` (sau khi kiểm tra chỉ số hợp lệ).
2. Bảo đảm `dstBegin + n` không vượt `dst.length` (có thể kiểm tra bằng `n <= dst.length - dstBegin` để tránh tràn số).
3. Theo dõi riêng **độ dài dữ liệu thật** đang nằm trong buffer. `new String(dst)` tạo chuỗi từ **toàn bộ** mảng, kể cả `char` chưa ghi (`'\u0000'` trong mảng vừa tạo bằng `new char[size]`).
4. Nếu chỉ muốn chuyển phần vừa ghi thành `String`, dùng `new String(dst, dstBegin, n)` hoặc quản lý các đoạn hợp lệ theo nhu cầu.

Ví dụ:

```java
char[] buffer = new char[10];
String text = "Hello";
text.getChars(0, text.length(), buffer, 3);

String copiedOnly = new String(buffer, 3, text.length());
System.out.println(copiedOnly); // Hello
```

`new String(buffer, 3, 5)` lấy đúng năm code unit đã ghi vào mảng. Trái lại, `new String(buffer)` sẽ chứa cả những phần tử `'\u0000'` chưa được ghi; chúng thường không hiển thị rõ trên console.

Mảng `char[]` **mutable**. Nếu nhiều luồng cùng viết vào cùng mảng, các luồng cần đồng bộ hoặc phân chia vùng không xung đột và kiểm soát việc công bố/kết hợp dữ liệu. Việc `String` nguồn bất biến **không** làm cho bộ đệm đích an toàn đa luồng.

## 8. So sánh với các API thường dùng

| API | Đầu ra hoặc tác động | Khi nên dùng |
| --- | --- | --- |
| `text.getChars(begin, end, dst, dstBegin)` | Ghi `end - begin` code unit vào mảng **có sẵn**; trả `void`. | Cần sao chép một vùng vào buffer tại vị trí xác định. |
| `text.toCharArray()` | Trả về `char[]` **mới** chứa toàn bộ code unit UTF-16 của chuỗi. | Muốn một bản mảng của toàn bộ chuỗi. |
| `text.substring(begin, end)` | Trả về một `String` chứa vùng chỉ định theo chỉ số UTF-16. | Muốn tiếp tục xử lý kết quả dạng `String`. |
| `text.getBytes(StandardCharsets.UTF_8)` | Trả về `byte[]` với dữ liệu **đã mã hóa UTF-8**. | Ghi file/gửi dữ liệu byte với charset được chỉ định. |
| `text.codePoints().toArray()` | Trả về `int[]` theo code point (kết hợp surrogate pair hợp lệ). | Cần giá trị code point thay vì các đơn vị `char`. |

Ví dụ đối chiếu:

```java
String text = "A🚀B";

char[] chars = text.toCharArray(); // 4 char UTF-16.
int[] points = text.codePoints().toArray(); // 3 code point.

System.out.println(chars.length);  // 4
System.out.println(points.length); // 3
```

Đừng dùng `getChars` để “giải mã UTF-8”: nó không nhận `byte[]` và cũng không thực hiện chuyển đổi charset. Đối với byte, hãy sử dụng API mã hóa/giải mã thích hợp.

Về hiệu năng, `getChars` tránh yêu cầu tạo một `char[]` mới **nếu bạn đã có mảng đích**. Nó không bảo đảm lúc nào cũng nhanh hơn các API khác; nên chọn theo yêu cầu về kiểu kết quả, phân bổ bộ nhớ, khả năng bảo trì và đo hiệu năng trong bối cảnh thực tế.

## 9. Lỗi phổ biến và cách khắc phục

**Lỗi 1: Nhầm `srcEnd` với số lượng.**

```java
String text = "abcdef";
char[] dst = new char[3];
text.getChars(2, 5, dst, 0); // Đúng: "cde", 3 char.
```

`srcEnd = 5` là **chỉ số cuối loại trừ**; số lượng bằng `5 - 2 = 3`, không phải `5`.

**Lỗi 2: Tạo mảng nhỏ hơn đoạn cần chép.**

```java
String text = "Hello";
char[] tooSmall = new char[3];
// text.getChars(0, 5, tooSmall, 0); // IndexOutOfBoundsException.
```

Mảng cần ít nhất **5 phần tử trống** từ `dstBegin = 0` để chứa cả `"Hello"`.

**Lỗi 3: Dùng số code point làm số `char`.**

```java
String text = "A🚀B";
int pointCount = text.codePointCount(0, text.length()); // 3
char[] tooShortForWholeString = new char[pointCount];  // 3
// text.getChars(0, text.length(), tooShortForWholeString, 0);
// Không đủ: đoạn nguồn có 4 code unit.
```

Nếu chép toàn chuỗi, mảng cần `text.length()` phần tử, **không nhất thiết bằng** `codePointCount`.

**Lỗi 4: Cắt đôi surrogate pair.**

```java
String text = "A🚀B";
char[] half = new char[1];
text.getChars(1, 2, half, 0); // Chỉ high surrogate.
```

Nếu muốn trọn một code point ngoài BMP, phải xác định khoảng UTF-16 bao gồm cả high và low surrogate, chẳng hạn `[1, 3)` trong ví dụ.

**Lỗi 5: In toàn bộ mảng đích rồi hiểu nhầm phần chưa ghi.**

`new char[10]` chứa mười ký tự `'\u0000'` trước khi gán giá trị. Nếu chỉ chép ba code unit, các phần tử còn lại không tự biến mất. Lưu số ký tự hợp lệ hoặc tạo chuỗi từ đúng đoạn dữ liệu đã ghi bằng `new String(dst, offset, count)`.

**Lỗi 6: Giả định nội dung `String` bị thay đổi.**

`getChars` ghi vào **mảng đích**. Ghi đè một phần tử của `dst` sau đó cũng không làm thay đổi `String` đã dùng làm nguồn hoặc chuỗi mới đã được tạo độc lập từ mảng.

## 10. Bài tập tự kiểm tra

### Bài 1: Dự đoán vùng đích

```java
String text = "abcdef";
char[] out = {'-', '-', '-', '-', '-', '-'};
text.getChars(1, 4, out, 2);
System.out.println(new String(out));
```

**Đáp án:** `"--bcd-"`. Nguồn `[1, 4)` lấy `"bcd"` (3 `char`), ghi vào các phần tử `2..4`; hai phần tử đầu và phần tử cuối vẫn là `'-'`.

### Bài 2: Sao chép chuỗi có emoji

```java
String text = "Hi😀!";
char[] out = new char[2];
text.getChars(2, 4, out, 0);
System.out.println(new String(out));
```

**Đáp án:** `"😀"`. Emoji `😀` chiếm hai `char` UTF-16 ở vị trí `2` và `3`, nên vùng `[2, 4)` lấy trọn.

### Bài 3: Đủ dung lượng hay không?

```java
String text = "Hello";
char[] out = new char[5];
text.getChars(1, 5, out, 2);
```

**Đáp án:** Không đủ. Cần chép `4` code unit bắt đầu từ `dstBegin = 2`, tức cần vị trí đích `2, 3, 4, 5`, nhưng mảng dài `5` chỉ có các vị trí `0..4`. Lời gọi ném `IndexOutOfBoundsException`.

### Bài 4: Sao chép rỗng

```java
char[] out = {'x', 'y'};
"abc".getChars(3, 3, out, 2);
System.out.println(java.util.Arrays.toString(out));
```

**Đáp án:** `[x, y]`. Cả vị trí nguồn `3` và vị trí đích `2` là ranh giới hợp lệ tại cuối; số phần tử cần chép bằng `0`.

### Bài 5: Chép theo code point

Cho `String text = "A🚀B";`. Muốn chép **code point thứ hai** (đếm từ 1) vào `char[]` mà không viết cứng chỉ số UTF-16 thì làm gì?

**Đáp án:** Lấy `int from = text.offsetByCodePoints(0, 1);`, `int to = text.offsetByCodePoints(from, 1);`, cấp phát `char[] out = new char[to - from];` rồi gọi `text.getChars(from, to, out, 0);`. Kết quả là mảng dài `2` lưu cả hai đơn vị của `🚀`.

## 11. Ghi nhớ

- `getChars(srcBegin, srcEnd, dst, dstBegin)` sao chép **`srcEnd - srcBegin` code unit UTF-16** vào mảng đã có.
- Đoạn nguồn là **`[srcBegin, srcEnd)`**: có đầu, không có cuối.
- Việc ghi bắt đầu tại `dst[dstBegin]`; phần đích nằm ngoài đoạn ghi không bị thay đổi.
- Phương thức trả `void`, sửa `char[]` đích và **không làm thay đổi `String` nguồn**.
- Kiểm tra cả **giới hạn nguồn** lẫn **dung lượng đích**; vùng rỗng là hợp lệ nếu chỉ số hợp lệ.
- Với ký tự ngoài BMP, một code point có thể chiếm **hai `char`**. Nếu xử lý theo code point, chuyển ranh giới bằng `offsetByCodePoints` trước khi sao chép.

## Tài liệu tham khảo

- [Java SE 17 — `String.getChars(int, int, char[], int)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html#getChars(int,int,char%5B%5D,int))
- [Java SE 17 — `String`, các phương thức `length` và `offsetByCodePoints`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html)
- [Java SE 17 — `Character` và UTF-16 surrogate](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-access-007">toCharArray và bản sao dữ liệu ký tự</a>

<details>
<summary>Xem chi tiết</summary>

# toCharArray() và bản sao dữ liệu ký tự trong Java

## 1. Mục tiêu học tập

Phương thức `String.toCharArray()` chuyển nội dung một đối tượng `String` thành một **mảng `char[]` mới**, chứa các **đơn vị mã UTF-16** theo đúng thứ tự trong chuỗi. Mỗi lần gọi, phương thức cung cấp một mảng được cấp phát mới; thay đổi phần tử trong mảng này **không làm thay đổi chuỗi nguồn**.

Sau bài học, bạn có thể:

1. Dùng `toCharArray()` đúng cú pháp và xác định chính xác kiểu dữ liệu trả về.
2. Chứng minh mảng trả về là **bản sao độc lập** với `String` gốc và với các mảng từ lời gọi khác.
3. Đọc, duyệt, chỉnh sửa từng phần tử `char[]`; tạo `String` mới từ dữ liệu đã chỉnh sửa.
4. Hiểu sự khác nhau giữa **`char` (UTF-16 code unit)**, **Unicode code point** và **ký tự hiển thị**.
5. Chọn đúng giữa `toCharArray()`, `charAt()`, `getChars()` và các API xử lý code point.
6. Tránh lỗi liên quan đến chỉ số, giá trị `null`, surrogate pair, sao chép dữ liệu, bộ nhớ và cách in mảng.

Đây là phần học thuộc nhóm **Độ dài và truy cập** của Java Core String. Kiến thức cần có: `String`, mảng một chiều, kiểu `char`, vòng lặp, phép gán, tham chiếu và tính bất biến của `String`. Ví dụ đầy đủ sử dụng Java 17+.

## 2. toCharArray() làm gì?

Chữ ký phương thức theo Java API:

~~~java
public char[] toCharArray()
~~~

Cách dùng cơ bản:

~~~java
String word = "Java";
char[] letters = word.toCharArray();

System.out.println(letters.length);  // 4
System.out.println(letters[0]);      // J
System.out.println(letters[3]);      // a
~~~

`word` có kiểu `String`, còn `letters` có kiểu **`char[]`**. Mỗi phần tử của mảng chứa một đơn vị UTF-16 trong chuỗi. Mảng có độ dài bằng `word.length()`, vì cả hai đều đếm **UTF-16 code units** chứ không nhất thiết đếm ký tự người dùng nhìn thấy.

Cần phân biệt cú pháp:

| Tác vụ | Với String | Với char[] |
| --- | --- | --- |
| Độ dài | `word.length()` (phương thức) | `letters.length` (thuộc tính mảng) |
| Lấy phần tử ở vị trí `i` | `word.charAt(i)` | `letters[i]` |
| Thay phần tử `i` | Không có thao tác sửa tại chỗ | `letters[i] = 'K'` |
| Chuyển thành dữ liệu bên kia | `word.toCharArray()` | `new String(letters)` |
| Kiểu giá trị | `String` bất biến | `char[]` mutable, độ dài cố định |

Ví dụ duyệt mảng sau khi chuyển:

~~~java
String word = "Code";
char[] units = word.toCharArray();

for (int i = 0; i < units.length; i++) {
    System.out.println(i + ": " + units[i]);
}
~~~

Kết quả:

~~~text
0: C
1: o
2: d
3: e
~~~

Chỉ số bắt đầu từ `0` và kết thúc ở `length - 1`. Truy cập `units[-1]` hoặc `units[units.length]` sẽ ném `ArrayIndexOutOfBoundsException`.

## 3. Bản sao độc lập: thay đổi mảng không làm đổi chuỗi nguồn

Đây là cơ chế quan trọng nhất.

~~~java
String source = "Java";
char[] chars = source.toCharArray();

chars[0] = 'K';

System.out.println(source);           // Java
System.out.println(new String(chars)); // Kava
~~~

Sau `chars[0] = 'K'`, đối tượng `String` vẫn mang nội dung `"Java"`. Việc sửa mảng chỉ tác động lên **bản sao**. Minh họa:

~~~text
source ───────────────► String "Java"  (bất biến)
                            │
                            │ toCharArray()
                            ▼ sao chép giá trị
chars  ───────────────► char[] ['J', 'a', 'v', 'a']
                                   │
                                   │ chars[0] = 'K'
                                   ▼
                        char[] ['K', 'a', 'v', 'a']

source vẫn là "Java"
~~~

**Không nhầm "bản sao" với "view":** `toCharArray()` không trả về cửa sổ nhìn trực tiếp vào bộ nhớ mà `String` dùng để lưu nội dung. Bạn có quyền chỉnh sửa mảng nhận được nhưng không thể dùng việc đó để chỉnh sửa chuỗi. Cách biểu diễn nội bộ của `String` là chi tiết triển khai của JDK; từ Java 9 nhiều JDK sử dụng *Compact Strings*, không nên giả định đối tượng `String` tự lưu bằng `char[]`.

### Mỗi lần gọi có mảng riêng

~~~java
String text = "ABC";
char[] first = text.toCharArray();
char[] second = text.toCharArray();

System.out.println(first == second); // false

first[0] = 'X';
System.out.println(new String(first));  // XBC
System.out.println(new String(second)); // ABC
System.out.println(text);               // ABC
~~~

Hai lời gọi độc lập trả **hai mảng khác nhau**. Toán tử `==` trên hai mảng so sánh **tham chiếu**, không so sánh nội dung.

Nếu cần so nội dung mảng:

~~~java
char[] a = "java".toCharArray();
char[] b = "java".toCharArray();

System.out.println(a == b);                         // false
System.out.println(java.util.Arrays.equals(a, b)); // true
~~~

Sự khác biệt này quan trọng vì `String.equals()` so **nội dung** hai chuỗi, trong khi mảng `char[]` không tự ghi đè `Object.equals()` để so từng phần tử.

## 4. Từ char[] trở lại String cũng tạo ra giá trị độc lập

Nếu đã chỉnh sửa các ký tự và muốn có chuỗi tương ứng, hãy tạo đối tượng `String` từ mảng:

~~~java
char[] chars = {'J', 'a', 'v', 'a'};
String snapshot = new String(chars);

chars[0] = 'K';

System.out.println(snapshot);        // Java
System.out.println(new String(chars)); // Kava
~~~

`new String(chars)` **sao chép nội dung mảng** tại thời điểm tạo chuỗi. Thay đổi mảng sau đó không làm sửa `snapshot`.

Như vậy hai chiều đều có tính **snapshot độc lập** đối với những dữ liệu đang xét:

~~~text
String nguồn --toCharArray()--> char[] bản sao có thể sửa
char[] dữ liệu --new String(...)--> String mới bất biến
~~~

Từ `char[]` còn có thể tạo `String` bằng `String.valueOf(chars)`. Để tạo từ **một đoạn** của mảng, dùng constructor `new String(chars, offset, count)`; lưu ý `count` là **số phần tử**, không phải chỉ số kết thúc:

~~~java
char[] letters = {'J', 'a', 'v', 'a'};
String part = new String(letters, 1, 2);
System.out.println(part); // av
~~~

Chỉ số hoặc độ dài đoạn không hợp lệ gây `IndexOutOfBoundsException`. Dù chỉ chọn một đoạn, `String` tạo ra vẫn độc lập với thay đổi tiếp theo của mảng.

### Lưu ý: char[].toString() không chuyển nội dung mảng thành String

Một mảng là đối tượng. Gọi `chars.toString()` dùng cách biểu diễn mặc định của đối tượng, **không cho ra** nội dung ký tự mong muốn. Dùng:

~~~java
char[] chars = {'O', 'K'};

System.out.println(new String(chars));          // OK
System.out.println(String.valueOf(chars));       // OK
System.out.println(java.util.Arrays.toString(chars)); // [O, K]
~~~

`System.out.println(chars)` cũng hiển thị nội dung mảng bởi `PrintStream` có overload riêng nhận `char[]`. Đừng suy rộng hành vi này sang `chars.toString()` hoặc sang mọi API nhận `Object`.

## 5. char[] là mảng có thể sửa, nhưng có độ dài cố định

Mảng cho phép **thay phần tử**; kích thước không tự tăng giảm:

~~~java
char[] data = "cats".toCharArray();
data[0] = 'b';

System.out.println(new String(data)); // bats
System.out.println(data.length);      // 4
~~~

Nếu cần **chèn hoặc xóa** làm thay đổi độ dài, `StringBuilder` thường thuận tiện hơn. Nếu mục tiêu chỉ là đọc vài ký tự mà không cần một mảng độc lập, `String.charAt()` tránh phải cấp phát mảng `char[]` đầy đủ.

Cần nhớ ba tầng trạng thái:

1. **Chuỗi nguồn:** immutable.
2. **Mảng bản sao:** mutable theo từng phần tử, kích thước cố định.
3. **Chuỗi tạo lại từ mảng:** snapshot immutable, không theo dõi những thay đổi sau đó của mảng.

### Sao chép mảng sang mảng khác

Một phép gán mảng **không sao chép phần tử**:

~~~java
char[] first = "Hi".toCharArray();
char[] alias = first;

alias[0] = 'B';
System.out.println(new String(first)); // Bi
~~~

`alias` và `first` là hai tham chiếu đến **cùng một mảng**. Nếu muốn bản sao độc lập của `char[]`:

~~~java
char[] first = "Hi".toCharArray();
char[] copy = first.clone();

copy[0] = 'B';
System.out.println(new String(first)); // Hi
System.out.println(new String(copy));  // Bi
~~~

Có thể dùng `Arrays.copyOf(first, first.length)` hoặc `System.arraycopy(...)` khi phù hợp. Đừng nhầm việc **sao chép từ String bằng toCharArray()** với việc **gán tham chiếu giữa hai biến char[]**.

## 6. Quan hệ với length() và charAt()

Đối với mọi chỉ số hợp lệ `i`, giá trị `source.toCharArray()[i]` tương ứng với `source.charAt(i)` tại thời điểm chuyển. Cả hai API đều thao tác với **UTF-16 code unit**.

~~~java
String word = "Java";
char[] copy = word.toCharArray();

for (int i = 0; i < word.length(); i++) {
    System.out.println(word.charAt(i) == copy[i]);
}
~~~

Cả bốn dòng in `true`. Tuy nhiên cách dùng khác nhau:

- `charAt(i)`: đọc một đơn vị ký tự tại vị trí `i` từ `String` mà không tạo một `char[]` hoàn chỉnh.
- `toCharArray()`: lấy toàn bộ nội dung thành mảng mới để có thể duyệt nhiều lần, dùng API yêu cầu `char[]`, hoặc thay dữ liệu trong **bản sao**.
- Sau khi đã chỉnh sửa mảng, `word.charAt(i)` vẫn đọc từ chuỗi gốc, nên không phản ánh thay đổi trên mảng.

Cả `length()` và `toCharArray().length` đều sử dụng đơn vị UTF-16; không được xem kết quả này luôn là số chữ cái, code points hay grapheme.

### Chuỗi rỗng và null

~~~java
String empty = "";
char[] emptyArray = empty.toCharArray();

System.out.println(emptyArray.length); // 0
~~~

Mảng trả về hợp lệ và có độ dài `0`. Ngược lại:

~~~java
String missing = null;
// missing.toCharArray(); // NullPointerException
~~~

`null` không phải chuỗi rỗng. Khi API nhận dữ liệu không chắc chắn, xác định quy tắc của hàm rõ ràng:

~~~java
static char[] orEmpty(String input) {
    return input == null ? new char[0] : input.toCharArray();
}
~~~

Hàm minh họa trên **chọn** biến `null` thành mảng rỗng. Trong các tình huống khác, trả `null` hoặc ném ngoại lệ với thông báo cụ thể có thể đúng hơn; không mặc định mọi ứng dụng nên làm giống ví dụ.

## 7. Unicode: char[] chứa UTF-16 code units, không phải "ký tự nhìn thấy"

Đây là điểm quan trọng nhất khi dùng `toCharArray()` cho tiếng Việt, emoji và văn bản quốc tế.

Java `char` là một **đơn vị 16 bit**. Một code point Unicode ở ngoài **Basic Multilingual Plane (BMP)** cần **hai** đơn vị UTF-16 gọi là *surrogate pair*. Ví dụ emoji `😀` là một code point nhưng chiếm hai phần tử `char[]`.

~~~java
String text = "A😀B";
char[] units = text.toCharArray();

System.out.println(text.length());                       // 4
System.out.println(units.length);                        // 4
System.out.println(text.codePointCount(0, text.length())); // 3
System.out.println(Character.isHighSurrogate(units[1])); // true
System.out.println(Character.isLowSurrogate(units[2]));  // true
~~~

Sơ đồ:

~~~text
Văn bản hiển thị:   A     😀       B
Code points:        A   U+1F600    B       (3 code points)
UTF-16 code units: [A] [high] [low] [B]   (4 char)
Chỉ số mảng:        0     1     2    3
~~~

**Hậu quả quan trọng:**

- `units[1]` là **high surrogate**, `units[2]` là **low surrogate**. In riêng từng đơn vị có thể không hiển thị thành emoji mong đợi.
- Nếu sửa một nửa surrogate pair mà không xử lý nửa kia, mảng (và `String` tạo từ nó) có thể chứa **surrogate đơn lẻ** và dữ liệu Unicode không hợp lệ về mặt cặp surrogate.
- `toCharArray()` chỉ sao chép các code units. Nó **không** tự tách văn bản thành Unicode code points hoặc ký tự người dùng cảm nhận.

### Khi cần duyệt code points

Sử dụng `String.codePoints()`, trả về `IntStream` của các code points, rồi chuyển thành `int[]` khi cần mảng độc lập:

~~~java
String text = "A😀B";
int[] points = text.codePoints().toArray();

System.out.println(points.length);       // 3
System.out.println(points[1] == 0x1F600); // true
~~~

Có thể duyệt code point bằng `Character.toChars(codePoint)` hoặc khôi phục bằng constructor `new String(int[] codePoints, int offset, int count)`. Mảng `int[]` phù hợp khi thuật toán làm việc trên code points hơn là code units.

**Code point chưa chắc là một "ký tự nhìn thấy".** Chẳng hạn `"e\u0301"` có hai code points (chữ e và dấu sắc kết hợp) nhưng có thể hiển thị như một ký tự. Một số emoji là chuỗi nhiều code points ghép lại. Nếu thuật toán yêu cầu phân đoạn theo *grapheme cluster*, cần dùng công cụ phân đoạn Unicode phù hợp chứ không chỉ `toCharArray()` hoặc `codePoints()`.

### Không nhầm với byte và mã hóa ký tự

`char[]` chứa **đơn vị UTF-16**. `byte[]` từ `getBytes(StandardCharsets.UTF_8)` chứa **các byte đã mã hóa theo UTF-8**. Hai cấu trúc khác nhau về phần tử, độ dài và ý nghĩa. Với nhiều emoji, số byte UTF-8 là 4, số `char` UTF-16 là 2, số code point là 1.

~~~java
String emoji = "😀";
char[] chars = emoji.toCharArray();
byte[] bytes = emoji.getBytes(java.nio.charset.StandardCharsets.UTF_8);

System.out.println(chars.length); // 2
System.out.println(bytes.length); // 4
~~~

`toCharArray()` không thực hiện mã hóa hay giải mã UTF-8, nên không dùng nó thay `getBytes()` khi ghi dữ liệu ra file/mạng.

## 8. So với getChars(), charAt() và StringBuilder

| Nhu cầu | Lựa chọn thường phù hợp | Vì sao |
| --- | --- | --- |
| Đọc 1–vài vị trí trong chuỗi | `charAt(i)` | Tránh tạo mảng chứa toàn bộ chuỗi |
| Cần mảng ký tự **độc lập** toàn bộ chuỗi | `toCharArray()` | Cấp phát và trả `char[]` mới |
| Chép **một đoạn** vào mảng đã có | `getChars(srcBegin, srcEnd, dst, dstBegin)` | Điều khiển phạm vi nguồn và điểm bắt đầu đích |
| Cần chỉnh sửa nội dung theo nhiều bước, độ dài có thể đổi | `StringBuilder` | Hỗ trợ thêm, xóa, chèn, thay mà không cố định độ dài |
| Cần duyệt Unicode code points | `codePoints()` | Tránh tách một surrogate pair thành hai đơn vị độc lập |
| Cần UTF-8 để truyền dữ liệu | `getBytes(StandardCharsets.UTF_8)` | Mã hóa thành byte[] theo charset chỉ định |

### getChars(): chép đoạn vào mảng đích có sẵn

~~~java
String text = "Hello";
char[] destination = {'_', '_', '_', '_', '_', '_'};

text.getChars(1, 4, destination, 2);

System.out.println(new String(destination)); // __ell_
~~~

Cận `srcEnd` là **exclusive**: `getChars(1, 4, ...)` chép `'e'`, `'l'`, `'l'` (ba ký tự), bắt đầu ở `destination[2]`. Phương thức **trả về `void`**, thực hiện sao chép vào mảng đích mà người gọi đưa vào; nó không đổi `text`.

Khác với `toCharArray()`, `getChars()` không tạo mảng đích cho bạn. Bạn phải cấp phát đủ phần tử. Khoảng chỉ số không hợp lệ có thể ném `IndexOutOfBoundsException`.

### StringBuilder khi cần thay đổi độ dài

~~~java
StringBuilder builder = new StringBuilder("Java");
builder.insert(0, "Core ");
builder.append(" 17");

System.out.println(builder.toString()); // Core Java 17
~~~

Mảng `char[]` không thể tự mở rộng kích thước để thêm một đoạn dài hơn. `StringBuilder` có thể phát triển dung lượng và thuận tiện hơn cho thao tác lặp đi lặp lại. Tuy nhiên nó là đối tượng **mutable**, không giống `String`.

## 9. Bộ nhớ, hiệu năng và tính an toàn dữ liệu

**Chi phí của toCharArray():** để chép một chuỗi có `n` đơn vị UTF-16, phương thức phải tạo mảng đủ `n` phần tử và sao chép nội dung. Vì vậy, thời gian và bộ nhớ thêm là **O(n)** theo độ dài chuỗi. Khi cần tạo bản sao để sửa hoặc chuyển cho API yêu cầu mảng, chi phí này có mục đích rõ ràng. Khi chỉ muốn đọc một vài vị trí, `charAt()` có thể hiệu quả hơn vì không phải tạo cả mảng.

**Không giữ bản sao lâu hơn cần thiết:** mỗi `char[]` là một đối tượng có dữ liệu riêng; lặp `toCharArray()` hàng nghìn lần trên chuỗi lớn có thể làm tăng phân bổ bộ nhớ không cần thiết. Nếu cần dùng mảng trong một khối xử lý, chuyển một lần rồi tái sử dụng mảng đó khi đúng logic.

**Không tự tạo độ an toàn tuyệt đối từ bản sao:** bản sao độc lập giúp code sửa mảng mà không ảnh hưởng `String` nguồn, nhưng `char[]` vẫn mutable. Nếu chia sẻ **cùng mảng** cho nhiều đoạn code, một đoạn có thể sửa dữ liệu của đoạn khác. Hãy cân nhắc `clone()` hoặc `Arrays.copyOf()` ở ranh giới API khi cần bảo vệ dữ liệu.

**Dữ liệu nhạy cảm:** một `char[]` có thể được ghi đè khi không còn dùng:

~~~java
char[] sensitive = {'1', '2', '3'};
java.util.Arrays.fill(sensitive, '\0');
~~~

Tuy nhiên, nếu mảng được tạo bằng `existingString.toCharArray()` thì xóa mảng **không xóa chuỗi nguồn** hoặc các bản sao khác từng tồn tại trong bộ nhớ. Không được coi `toCharArray()` + `Arrays.fill()` là bảo đảm xóa sạch mọi dấu vết của dữ liệu. Với API nhận bí mật, ưu tiên cơ chế quản lý bí mật phù hợp ngay từ lúc nhận dữ liệu, thay vì chủ động tạo `String` chứa bí mật.

**Lưu ý:** `String.toCharArray()` không lấy đi quyền sở hữu của `String`; nó chỉ tạo một bản sao. Tính bất biến của `String` giúp nội dung gốc không bị thay đổi qua mảng nhận được, nhưng việc công bố/chia sẻ một mảng mutable cần thiết kế rõ ràng.

## 10. Ví dụ tổng hợp có thể chạy độc lập (Java 17+)

Tạo file tên **`CharArrayCopyDemo.java`**:

~~~java
import java.util.Arrays;

public class CharArrayCopyDemo {
    public static void main(String[] args) {
        String source = "Java";
        char[] first = source.toCharArray();
        char[] second = source.toCharArray();

        System.out.println(first == second);
        first[0] = 'K';
        System.out.println(source);
        System.out.println(new String(first));
        System.out.println(new String(second));

        String snapshot = new String(first);
        first[1] = 'o';
        System.out.println(new String(first));
        System.out.println(snapshot);

        char[] destination = {'_', '_', '_', '_', '_', '_'};
        "Hello".getChars(1, 4, destination, 2);
        System.out.println(new String(destination));

        String unicode = "A😀B";
        char[] units = unicode.toCharArray();
        System.out.println(unicode.length());
        System.out.println(units.length);
        System.out.println(unicode.codePointCount(0, unicode.length()));
        System.out.println(Character.isHighSurrogate(units[1]));
        System.out.println(Character.isLowSurrogate(units[2]));

        System.out.println(Arrays.toString("OK".toCharArray()));
    }
}
~~~

Biên dịch và chạy trong cùng thư mục với file:

~~~bash
javac CharArrayCopyDemo.java
java CharArrayCopyDemo
~~~

Stdout **chính xác**:

~~~text
false
Java
Kava
Java
Kova
Kava
__ell_
4
4
3
true
true
[O, K]
~~~

Đọc hiểu output theo nhóm:

1. `first == second` là `false` vì hai lần `toCharArray()` trả hai mảng độc lập.
2. Sửa `first[0]` thành `'K'` khiến mảng thứ nhất hiển thị `"Kava"`, trong khi `source` và `second` vẫn là `"Java"`.
3. `snapshot` nhận bản chụp `"Kava"`; sửa tiếp `first[1]` thành `'o'` cho `"Kova"` không làm đổi `snapshot`.
4. `getChars(1, 4, ...)` chép ba ký tự `"ell"` vào mảng đã có ở vị trí `2`, kết quả `"__ell_"`.
5. `"A😀B"` chứa **4 code units** nhưng **3 Unicode code points**, vị trí `1` và `2` là high/low surrogate.
6. `Arrays.toString()` hiển thị danh sách phần tử dạng `[O, K]` thay vì nối trực tiếp thành `"OK"`.

Muốn kiểm chứng thêm, hãy sửa `source` thành `"Hi"` và điều chỉnh các vị trí đang truy cập. Đừng chỉ thay input mà giữ nguyên chỉ số vì điều đó sẽ gây ngoại lệ ngoài biên.

## 11. Bài tập luyện tập theo mức độ

### Bài tập 1 — Chuyển và duyệt

Viết `printIndexedChars(String text)` dùng `toCharArray()` và vòng `for` để in các đơn vị UTF-16 cùng chỉ số. Kiểm thử với `"Java"`, `""` và `"A😀B"`. Giải thích vì sao đầu vào emoji cần thận trọng khi in từng `char`.

### Bài tập 2 — Chứng minh bản sao

Tạo hai mảng bằng hai lần gọi `"Code".toCharArray()`. Đổi phần tử đầu tiên của mảng thứ nhất thành `'N'`; in chuỗi nguồn và hai mảng bằng `new String(...)`. Dự đoán trước kết quả và giải thích vì sao mảng thứ hai giữ nguyên.

### Bài tập 3 — Snapshot khi chuyển ngược

Tạo `char[] chars = {'C', 'a', 't'};` rồi `String animal = new String(chars);`. Sau đó sửa `chars[0] = 'B';`. In cả `animal` và `new String(chars)`, mô tả cơ chế độc lập giữa hai giá trị.

### Bài tập 4 — So sánh mảng đúng cách

Tạo `char[] a = "abc".toCharArray();`, `char[] b = "abc".toCharArray();`. So sánh bằng `==` và `Arrays.equals()`. Tại sao một phương thức có kết quả `false` và phương thức kia cho `true`?

### Bài tập 5 — Sao chép một đoạn

Với chuỗi `"012345"` và mảng đích sáu dấu `'_'`, sử dụng `getChars(2, 5, dst, 1)`. Kết quả mong đợi là `"_234__"`. Giải thích cách tính cận cuối và số phần tử được chép.

### Bài tập 6 — Unicode

Đo `length()`, `toCharArray().length`, `codePointCount()` và `codePoints().toArray().length` của `"😀😀"`. Kỳ vọng lần lượt `4`, `4`, `2`, `2`. Giải thích tại sao chỉ số mảng `char[]` không tương ứng trực tiếp với số emoji.

### Bài tập 7 — Chọn giải pháp

Viết hai phiên bản đếm số ký tự ASCII `'a'` trong một chuỗi: phiên bản đọc bằng `charAt()` và phiên bản dùng `toCharArray()`. Cả hai cần cho cùng kết quả. Phân tích sự đánh đổi: `charAt()` không cấp phát mảng; `toCharArray()` tạo một snapshot mà bạn có thể thao tác độc lập.

### Bài tập 8 — Thao tác nhạy cảm

Cho một mảng `char[]` nhận được từ API, dùng `Arrays.fill(chars, '\0')` sau khi sử dụng. Giải thích sự khác biệt giữa xóa **mảng ấy** và khẳng định dữ liệu đã bị xóa khỏi **toàn bộ bộ nhớ JVM** (khẳng định sau không được bảo đảm). Thảo luận vì sao chuyển bí mật thành `String` có thể làm tăng số bản lưu trong bộ nhớ.

## 12. Lỗi thường gặp và tiêu chí hoàn thành

| Lỗi | Vì sao sai | Cách đúng |
| --- | --- | --- |
| Nghĩ `toCharArray()` sửa String | String immutable; phương thức chỉ trả bản sao | Nhận `char[]`, sửa bản sao và tạo String mới khi cần |
| Gọi `text.toCharArray()` rồi bỏ kết quả | Mảng được trả về không được lưu hoặc sử dụng | `char[] units = text.toCharArray();` |
| Dùng `chars.length()` | Mảng có thuộc tính `length`, không có phương thức này | `chars.length` |
| Dùng `chars.toString()` để lấy văn bản | Trả biểu diễn đối tượng mảng | `new String(chars)` hoặc `String.valueOf(chars)` |
| So `char[]` bằng `==` khi cần so nội dung | `==` so tham chiếu | `Arrays.equals(a, b)` |
| Tin rằng `char[] alias = chars` là bản sao | Chỉ sao chép tham chiếu | `chars.clone()` hoặc `Arrays.copyOf(...)` |
| Nghĩ một `char` luôn là một Unicode code point | Surrogate pair cần hai `char` | Dùng `codePoints()` khi xử lý code points |
| Cắt hoặc sửa riêng một nửa surrogate pair | Có thể tạo surrogate lẻ | Bảo toàn cặp hoặc xử lý bằng code point |
| Không xử lý `null` | Gọi phương thức trên `null` ném NPE | Xác định contract và kiểm tra trước |
| Nhầm `char[]` với `byte[]` | Code units UTF-16 khác byte mã hóa | `getBytes(UTF_8)` khi cần mã hóa |
| Gọi `toCharArray()` lặp lại không cần thiết | Tạo bản sao và chi phí O(n) mỗi lần | Tái sử dụng mảng hoặc dùng `charAt()` |
| Tưởng xóa mảng cũng xóa mọi bản sao String | Các snapshot độc lập | Quản lý bí mật ngay từ lúc nhập, tránh tạo String không cần |

**Tự kiểm tra trước khi hoàn thành bài:**

1. `toCharArray()` trả về kiểu gì và có thay đổi chuỗi nguồn không?
2. Vì sao hai lời gọi `toCharArray()` trả hai mảng có thể có nội dung giống nhau nhưng `==` là `false`?
3. Khi `chars[0]` thay đổi, điều gì xảy ra với `String` nguồn và với một `String` đã tạo bằng `new String(chars)` trước đó?
4. Khi nào nên dùng `charAt()` thay vì `toCharArray()`? Khi nào nên dùng `getChars()`?
5. Vì sao `"😀".toCharArray().length` bằng `2` mà `"😀".codePointCount(0, 2)` bằng `1`?
6. Mảng `char[]` có thể thay đổi kích thước không? Nếu cần nối, chèn, xóa nhiều lần thì công cụ nào phù hợp hơn?
7. Vì sao `Arrays.fill(chars, '\0')` không đồng nghĩa mọi dấu vết dữ liệu nhạy cảm đã biến mất?

**Quy tắc ghi nhớ:** `String.toCharArray()` tạo **mảng `char[]` mới chứa bản sao các đơn vị UTF-16**. Bạn có thể thay đổi **mảng ấy**, nhưng **không thể sửa String nguồn thông qua mảng**. Muốn biến đổi dữ liệu rồi có chuỗi, hãy tạo một `String` mới từ mảng đã sửa.

### Tài liệu tham khảo

- [Java SE 17 — String](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html): `toCharArray()`, `length()`, `charAt()`, `getChars()`, constructors và `codePoints()`.
- [Java SE 17 — Character](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html): surrogate pairs, code points và `Character.toChars()`.
- [Java SE 17 — Arrays](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html): `Arrays.equals()`, `Arrays.copyOf()`, `Arrays.fill()` và `Arrays.toString()`.
- [Java SE 17 — StringBuilder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StringBuilder.html): thao tác thay đổi nội dung và chuyển sang `String`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-access-008">Chọn đơn vị chỉ số phù hợp trước khi duyệt hoặc cắt chuỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn đơn vị chỉ số phù hợp trước khi duyệt hoặc cắt chuỗi trong Java

Khi duyệt hay cắt `String`, câu hỏi đầu tiên không phải “dùng `charAt()` hay `substring()`?” mà là **một vị trí trong yêu cầu nghiệp vụ đại diện cho đơn vị nào?** Java cung cấp chỉ số theo **UTF-16 code unit** (đơn vị mã), API xử lý **Unicode code point** (điểm mã), và công cụ phân đoạn văn bản theo **ký tự người dùng nhìn thấy** (grapheme cluster). Ba đơn vị này đôi khi có cùng độ dài với chữ ASCII, nhưng khác nhau với emoji, dấu kết hợp và nhiều hệ chữ viết.

Chọn sai đơn vị có thể khiến chương trình cắt đôi emoji, làm rơi dấu tiếng Việt, đếm sai độ dài hoặc đặt con trỏ ở một vị trí không phù hợp. Bài này tập trung vào cách xác định đúng đơn vị **trước khi duyệt hoặc cắt chuỗi**, sau đó chọn API tương ứng.

## 1. Ba đơn vị cần phân biệt

| Đơn vị | Ý nghĩa | API hoặc công cụ trong Java | Khi nào nên dùng |
| --- | --- | --- | --- |
| **UTF-16 code unit** | Một đơn vị mã 16 bit, tương ứng giá trị `char` | `length()`, `charAt()`, `substring()`, `toCharArray()`, `chars()` | Làm việc với các API chấp nhận chỉ số `String` của Java, ký tự ASCII hoặc dữ liệu đã định nghĩa theo `char` |
| **Unicode code point** | Một giá trị Unicode; có thể cần một hoặc hai code unit UTF-16 | `codePointAt()`, `codePointCount()`, `offsetByCodePoints()`, `codePoints()`, `Character.charCount()` | Duyệt các Unicode code point, nhận diện emoji/code point ngoài BMP, tính khoảng cắt theo số code point |
| **Grapheme cluster** | Cụm ký tự thường được người dùng cảm nhận như một ký tự hiển thị | Công cụ phân đoạn văn bản như `BreakIterator`; với yêu cầu đầy đủ UAX #29, cân nhắc ICU4J hoặc thư viện đã kiểm thử | Đặt con trỏ, xóa “một ký tự” trong giao diện, giới hạn số ký tự người dùng nhìn thấy, cắt nhãn hiển thị |

**Không có đơn vị chỉ số đúng cho mọi tình huống.** Nếu một API Java yêu cầu `beginIndex` của `substring()`, nó cần **UTF-16 offset**. Nếu yêu cầu là “lấy ba Unicode code point đầu”, phải chuyển vị trí theo code point thành UTF-16 offset rồi mới gọi `substring()`. Nếu là “xóa đúng một ký tự mà người dùng thấy”, code point cũng có thể quá nhỏ vì một cụm hiển thị có thể gồm nhiều code point.

Lưu ý thêm: **byte của UTF-8 không phải chỉ số `String`**. Vị trí byte trong tệp/luồng là đơn vị thứ tư, được tính sau khi mã hóa; không thể dùng trực tiếp làm `substring()` index.

## 2. Ví dụ nền tảng: cùng một chuỗi, ba cách đếm

Dùng chuỗi Java sau:

~~~java
String text = "A😀e\u0301B";
~~~

Trong đoạn mã, `e\u0301` là chữ `e` theo sau bởi dấu sắc kết hợp `U+0301`. Khi hiển thị, chúng thường trông như một chữ “é”, nhưng **đang là hai code point**. Chuỗi có cấu trúc:

| UTF-16 index | Phần tử `char` | Vai trò |
| ---: | --- | --- |
| 0 | `A` | Một code unit, một code point |
| 1 | `\uD83D` | High surrogate của 😀 |
| 2 | `\uDE00` | Low surrogate của 😀 |
| 3 | `e` | Code point chữ e |
| 4 | `\u0301` | Code point dấu sắc kết hợp |
| 5 | `B` | Một code unit, một code point |

Vì vậy:

- **6 UTF-16 code unit:** `text.length() == 6`.
- **5 Unicode code point:** A, 😀, e, dấu sắc kết hợp, B.
- **Thường 4 grapheme cluster:** A, 😀, e + dấu sắc, B (cách hiển thị và ranh giới cụm phải được xác định theo chuẩn phân đoạn tương ứng).

Emoji 😀 là code point `U+1F600`, nằm ngoài Basic Multilingual Plane (BMP), nên cần **hai code unit** UTF-16 (một surrogate pair). Trái lại, chữ `e` và dấu `U+0301` mỗi thứ chỉ cần một code unit nhưng kết hợp thành một cụm người đọc cảm nhận là một ký tự.

Trường hợp chữ ghép sẵn `"é"` (`U+00E9`) khác về dãy code point so với `"e\u0301"`. Hai chuỗi có thể trông giống nhau, nhưng độ dài Java và nội dung code point khác nhau. Unicode normalization, ví dụ NFC, có thể giúp xử lý một số dạng biểu diễn tương đương; normalization **không thay thế** việc xác định ranh giới grapheme cluster.

## 3. Nếu mục tiêu là chỉ số UTF-16: dùng charAt và substring

Các API thông dụng làm việc trực tiếp trên chỉ số **UTF-16 code unit**:

- `text.length()`: số đơn vị UTF-16, không mặc nhiên là số ký tự người dùng thấy.
- `text.charAt(index)`: trả về đúng **một `char`** tại vị trí `index`. Chỉ số hợp lệ từ `0` đến `length() - 1`.
- `text.substring(beginIndex, endIndex)`: lấy đoạn **[beginIndex, endIndex)**, gồm vị trí bắt đầu, không gồm vị trí kết thúc. `beginIndex == endIndex` hợp lệ nếu trong phạm vi và trả về chuỗi rỗng.
- `text.subSequence(begin, end)` cũng dùng đơn vị chỉ số UTF-16.
- `text.chars()` trả về `IntStream` chứa các giá trị UTF-16 `char` được mở rộng thành `int`; **khác** `codePoints()`.

Với ví dụ trên:

~~~java
String text = "A😀e\u0301B";
System.out.println(text.length());                  // 6
System.out.println(text.substring(1, 3).equals("😀")); // true
System.out.println(text.substring(1, 2).length());  // 1
System.out.println(
        Character.isHighSurrogate(text.substring(1, 2).charAt(0))); // true
~~~

Đây là **đoạn code minh họa đặt trong một phương thức**, không phải lớp Java độc lập. Kết quả lần lượt là `6`, `true`, `1`, `true`.

Vùng `[1, 3)` lấy cả high và low surrogate, nên giữ trọn emoji 😀. Vùng `[1, 2)` chỉ lấy **một nửa** của cặp surrogate. Java vẫn có thể tạo `String` chứa một surrogate lẻ, nhưng đó không còn là emoji hoàn chỉnh; khi mã hóa hoặc hiển thị có thể gặp ký tự thay thế. Tương tự, vùng `[3, 4)` lấy chữ e mà không lấy dấu sắc ở `[4, 5)`.

**Quy tắc:** chỉ số UTF-16 phù hợp khi hợp đồng API hoặc dữ liệu đã được quy định theo `char`. Khi cắt văn bản tự nhiên, hãy xác minh điểm bắt đầu/kết thúc **không chia đôi cặp surrogate** và, nếu quan trọng với giao diện, **không chia cụm ký tự hiển thị**.

## 4. Nếu mục tiêu là Unicode code point: duyệt theo codePointAt

Một Unicode code point ngoài BMP chiếm **hai** `char` UTF-16. Vì vậy, vòng lặp `for (int i = 0; i < text.length(); i++)` kết hợp `charAt(i)` là vòng lặp theo **code unit**, không phải theo code point.

Để duyệt các code point liên tiếp, dùng `codePointAt(offset)` và tăng offset bằng `Character.charCount(cp)`:

~~~java
String text = "A😀e\u0301B";

// Code bên trong phương thức Java:
for (int offset = 0; offset < text.length(); ) {
    int cp = text.codePointAt(offset);
    System.out.printf("U+%04X%n", cp);
    offset += Character.charCount(cp);
}
~~~

Kết quả:

~~~text
U+0041
U+1F600
U+0065
U+0301
U+0042
~~~

Tại vị trí của 😀, `codePointAt(1)` hợp nhất high và low surrogate thành `U+1F600`; `Character.charCount(0x1F600)` bằng `2` nên chỉ số nhảy từ 1 lên 3. Với A, e, dấu sắc, B, `charCount(cp)` bằng `1`.

**Quan trọng:** `codePointAt(index)` nhận **chỉ số UTF-16** chứ không phải “code point index”. Hàm trả về **code point bắt đầu tại offset đó** (nếu là high surrogate có low surrogate ngay sau); gọi với một chỉ số trỏ vào low surrogate có thể nhận riêng giá trị low surrogate. Vì vậy hãy luôn chuyển đổi offset đúng cách trước khi truy cập.

### Hai lựa chọn khác: codePoints và codePointCount

- `text.codePoints()` tạo `IntStream` duyệt code point. Dùng khi muốn `map`, `filter` hoặc xử lý stream mà không tự cập nhật UTF-16 offset.
- `text.codePointCount(beginIndex, endIndex)` đếm code point trong vùng có ranh giới vẫn là **UTF-16 offset**. Với chuỗi nguyên vẹn, `text.codePointCount(0, text.length())` cho số code point.
- `text.chars()` tạo stream các code unit, nên khi có emoji ngoài BMP, nó cho **hai phần tử** thay vì một.

Một `String` có thể chứa surrogate lẻ; các API code point của Java xử lý surrogate không ghép cặp như một giá trị riêng khi đếm/duyệt. Đừng giả định mọi chuỗi Java đang chứa dãy Unicode scalar value hợp lệ nếu dữ liệu đầu vào có thể lỗi.

## 5. Nếu muốn cắt theo số code point: chuyển sang UTF-16 offset

`substring()` **không có overload nhận chỉ số code point**. Phải chuyển từ vị trí theo code point sang UTF-16 offset bằng `offsetByCodePoints()`:

~~~java
String text = "A😀e\u0301B";
int begin = text.offsetByCodePoints(0, 1); // 1 code point sau đầu chuỗi
int end = text.offsetByCodePoints(begin, 1); // sau emoji, UTF-16 offset = 3
String emoji = text.substring(begin, end);
System.out.println(emoji.equals("😀")); // true
~~~

Giải thích: `offsetByCodePoints(index, codePointOffset)` nhận **UTF-16 index** và khoảng dịch chuyển tính bằng **code point**, trả về **UTF-16 index** mới. Cách tính này xử lý surrogate pair hợp lệ; nếu yêu cầu đi ra ngoài đoạn hợp lệ, phương thức ném `IndexOutOfBoundsException`.

Công thức chuyển hai ranh giới thường gặp:

~~~java
int utf16Start = text.offsetByCodePoints(0, startPointIndex);
int utf16End = text.offsetByCodePoints(utf16Start, pointCount);
String part = text.substring(utf16Start, utf16End);
~~~

Đây là **đoạn code minh họa**: `startPointIndex` và `pointCount` phải được kiểm tra hợp lệ trước. Giá trị `pointCount` là **số code point cần lấy**, khác với “chỉ số kết thúc”.

### Hàm tiện ích có kiểm tra phạm vi

~~~java
static String sliceByCodePoints(String text, int startPoint, int count) {
    java.util.Objects.requireNonNull(text, "text");
    int total = text.codePointCount(0, text.length());

    if (startPoint < 0 || count < 0 || startPoint > total - count) {
        throw new IndexOutOfBoundsException("Invalid code point range");
    }

    int begin = text.offsetByCodePoints(0, startPoint);
    int end = text.offsetByCodePoints(begin, count);
    return text.substring(begin, end);
}
~~~

Đây là **phương thức minh họa**, đặt trong một lớp Java. Kiểm tra `startPoint > total - count` giúp tránh dùng phép cộng `startPoint + count` có khả năng tràn `int`. Vùng trống ở điểm kết thúc `startPoint == total, count == 0` là hợp lệ. Nếu `count` âm hoặc chọn vượt số code point, hàm chủ động báo lỗi.

**Giới hạn của giải pháp:** nó giữ nguyên **code point**, nhưng có thể cắt giữa chữ e và dấu kết hợp nếu yêu cầu một code point. Khi cần bảo toàn một ký tự hiển thị, nên phân đoạn theo grapheme cluster.

## 6. Chương trình Java 17+ chạy độc lập

Tạo tệp **StringIndexUnitsDemo.java**:

~~~java
import java.util.Objects;

public class StringIndexUnitsDemo {
    static String sliceByCodePoints(String text, int startPoint, int count) {
        Objects.requireNonNull(text, "text");
        int total = text.codePointCount(0, text.length());
        if (startPoint < 0 || count < 0 || startPoint > total - count) {
            throw new IndexOutOfBoundsException("Invalid code point range");
        }
        int begin = text.offsetByCodePoints(0, startPoint);
        int end = text.offsetByCodePoints(begin, count);
        return text.substring(begin, end);
    }

    public static void main(String[] args) {
        String text = "A😀e\u0301B";

        System.out.println("UTF-16 units = " + text.length());
        System.out.println("Code points = "
                + text.codePointCount(0, text.length()));
        System.out.println("index 1 is high surrogate = "
                + Character.isHighSurrogate(text.charAt(1)));
        System.out.println("substring(1, 2) keeps whole emoji = "
                + text.substring(1, 2).equals("😀"));
        System.out.println("substring(1, 3) keeps whole emoji = "
                + text.substring(1, 3).equals("😀"));

        String firstTwo = sliceByCodePoints(text, 0, 2);
        String emoji = sliceByCodePoints(text, 1, 1);
        String accentPair = sliceByCodePoints(text, 2, 2);

        System.out.println("firstTwo code points = "
                + firstTwo.codePointCount(0, firstTwo.length()));
        System.out.println("emoji UTF-16 units = " + emoji.length());
        System.out.println("accentPair code points = "
                + accentPair.codePointCount(0, accentPair.length()));
        System.out.println("accentPair UTF-16 units = " + accentPair.length());

        String onePoint = sliceByCodePoints(text, 2, 1);
        System.out.println("onePoint includes combining accent = "
                + onePoint.equals("e\u0301"));

        StringBuilder codePoints = new StringBuilder();
        text.codePoints().forEach(cp -> {
            if (codePoints.length() > 0) {
                codePoints.append(' ');
            }
            codePoints.append(String.format("U+%04X", cp));
        });
        System.out.println("Values = " + codePoints);
    }
}
~~~

Biên dịch và chạy bằng JDK 17 hoặc mới hơn:

~~~bash
javac -encoding UTF-8 StringIndexUnitsDemo.java
java StringIndexUnitsDemo
~~~

Kết quả:

~~~text
UTF-16 units = 6
Code points = 5
index 1 is high surrogate = true
substring(1, 2) keeps whole emoji = false
substring(1, 3) keeps whole emoji = true
firstTwo code points = 2
emoji UTF-16 units = 2
accentPair code points = 2
accentPair UTF-16 units = 2
onePoint includes combining accent = false
Values = U+0041 U+1F600 U+0065 U+0301 U+0042
~~~

**Diễn giải:** `substring(1, 2)` cắt mất low surrogate nên không giữ nguyên emoji; `substring(1, 3)` lấy trọn cặp. Hàm `sliceByCodePoints()` giúp chọn đủ emoji theo một code point nhưng, nếu chọn riêng code point e, **không tự kéo theo dấu sắc kết hợp** ở code point kế tiếp. Đây là bằng chứng rằng *cắt an toàn theo code point chưa đủ để cắt an toàn theo ký tự hiển thị*.

## 7. Nếu cần một ký tự mà người dùng nhìn thấy: grapheme cluster

Grapheme cluster là đơn vị phù hợp hơn khi ứng dụng cần:

- Xóa ký tự tại con trỏ trong ô nhập văn bản theo cách người dùng kỳ vọng.
- Giới hạn độ dài tên hiển thị theo số ký tự cảm nhận, thay vì `char` hoặc code point.
- Cắt nhãn hay lời nhắn mà không tách dấu kết hợp và các chuỗi emoji phức tạp.
- Di chuyển con trỏ qua các cụm ký tự trong văn bản đa ngôn ngữ.

Ví dụ những thứ có thể gồm **nhiều code point** nhưng tạo một cụm hiển thị: `e\u0301` (chữ và dấu), cờ tạo từ các *regional indicator*, emoji có bộ biến đổi tông da, hoặc chuỗi emoji có *zero-width joiner* (ZWJ). Chỉ số code point sẽ thấy từng thành phần; ranh giới của một ký tự hiển thị cần thêm quy tắc phân đoạn Unicode.

Java có `java.text.BreakIterator.getCharacterInstance(Locale)` để tìm ranh giới ký tự theo locale và phiên bản JDK. API trả về các **UTF-16 offset**, vì vậy dùng được trực tiếp với `substring()`. Tuy nhiên, hành vi phân đoạn với emoji ZWJ, cờ và các tình huống Unicode mới có thể phụ thuộc JDK/dữ liệu Unicode/locale. Khi yêu cầu **đầy đủ Extended Grapheme Cluster theo Unicode UAX #29** hoặc tương thích giao diện trên nhiều nền tảng, hãy kiểm thử các trường hợp thực tế và cân nhắc thư viện phân đoạn cập nhật như ICU4J.

### Cách sử dụng BreakIterator theo từng ranh giới

~~~java
import java.text.BreakIterator;
import java.util.Locale;

// Đoạn code bên trong phương thức Java:
String text = "Cafe\u0301!";
BreakIterator boundaries = BreakIterator.getCharacterInstance(Locale.ROOT);
boundaries.setText(text);

int start = boundaries.first();
for (int end = boundaries.next();
     end != BreakIterator.DONE;
     start = end, end = boundaries.next()) {
    String cluster = text.substring(start, end);
    System.out.println(cluster);
}
~~~

Đây là **đoạn mã minh họa**, không phải một chương trình độc lập. `BreakIterator` trả về các khoảng `[start, end)` theo **UTF-16 index** và `substring()` lấy đoạn tương ứng. Kết quả phân đoạn chi tiết được quyết định bởi phiên bản/cấu hình bộ phân đoạn; ứng dụng cần thử trên văn bản thực tế trước khi dùng để giới hạn hoặc biên tập nội dung.

**Không dùng “một `char` = một ký tự người dùng”.** Tương tự, không mặc định “một Unicode code point = một ký tự người dùng”. Với các cụm hiển thị phức tạp, đơn vị grapheme mới gần với kỳ vọng giao diện.

### Ba phép cắt minh họa cho cùng yêu cầu

Với `String text = "A😀e\u0301B";`:

| Yêu cầu thực sự | Đơn vị nên chọn | Cách tiếp cận |
| --- | --- | --- |
| Lấy `char` tại UTF-16 offset 1 | UTF-16 code unit | `text.charAt(1)` → high surrogate, **không phải emoji trọn vẹn** |
| Lấy Unicode code point thứ hai | Code point | Chuyển điểm thứ hai thành UTF-16 offset, `codePointAt(offset)` → `U+1F600` |
| Lấy cụm thứ ba theo cách hiển thị | Grapheme cluster | Phân đoạn thành các cụm A, 😀, e+dấu, B bằng thuật toán grapheme phù hợp, sau đó cắt theo ranh giới trả về |
| Lấy nội dung trong vùng UTF-16 `[3, 5)` | UTF-16 code unit | `text.substring(3, 5)` → e cộng dấu kết hợp, vẫn là hai code point |
| Lấy hai code point từ vị trí code point 2 | Code point | `sliceByCodePoints(text, 2, 2)` → e cộng dấu; đúng vì yêu cầu cụ thể là hai code point |

Trong bảng, “thứ hai”, “thứ ba” là cách đếm **từ 1** trong câu tự nhiên; API Java vẫn đếm offset **từ 0**. Phải thống nhất quy ước đầu vào trước khi viết code.

## 8. Cách chọn đơn vị theo mục tiêu thực tế

| Tình huống | Quyết định khuyến nghị | Lý do |
| --- | --- | --- |
| Đang nhận chỉ số từ `String.indexOf()` hoặc truyền vào `substring()` | Giữ **UTF-16 offset** | Các API trả/nhận vị trí theo cùng đơn vị; tránh trộn chỉ số |
| Duyệt từng `char` để xử lý dữ liệu ASCII hoặc định dạng giới hạn ASCII | **Code unit**, với giả định ASCII được kiểm tra | Mỗi ký tự ASCII chiếm một đơn vị UTF-16 |
| Đếm/duyệt các Unicode code point của văn bản | **Code point** | Một code point ngoài BMP có thể cần hai `char` |
| Lấy *n* code point đầu của chuỗi | **Code point** + `offsetByCodePoints()` | Chuyển sang UTF-16 offset an toàn rồi gọi `substring()` |
| Cắt theo “n ký tự người dùng nhìn thấy” | **Grapheme cluster** | Tránh chia dấu kết hợp, emoji ghép và nhiều cụm đa code point |
| Tính kích thước tệp hoặc giới hạn số byte trên giao thức | **Byte theo charset xác định** | `length()` không tính số byte UTF-8 |
| Cắt chuỗi theo độ rộng hiển thị trong terminal | Cần thêm **độ rộng glyph/cell** | Số grapheme cũng không nhất thiết bằng số ô hiển thị |
| Tìm ranh giới từ/câu | Phân đoạn **từ/câu**, không chỉ grapheme | Ranh giới từ/câu có quy tắc riêng |

**Ví dụ quan trọng với `indexOf()`:** nếu `int p = text.indexOf("B");`, giá trị `p` là UTF-16 offset; có thể gọi ngay `text.substring(0, p)`. Không coi `p` là số code point rồi đưa vào `offsetByCodePoints(0, p)`, vì khi có emoji, việc “chuyển lần nữa” dẫn đến vị trí sai hoặc ngoại lệ.

## 9. Những lỗi phổ biến

1. **Duyệt `charAt(i)` rồi coi mỗi lần là một Unicode ký tự.** Với emoji ngoài BMP, mã nguồn xử lý hai surrogate riêng lẻ. Nếu cần code point, dùng `codePointAt()` và tăng `i` theo `Character.charCount(cp)`, hoặc dùng `codePoints()`.
2. **Dùng `substring(0, n)` để lấy n code point.** `n` trong lời gọi là UTF-16 offset. Hãy đổi qua `offsetByCodePoints()`.
3. **Đếm bằng `length()` rồi áp dụng quy định “tối đa 10 ký tự hiển thị”.** Quy định UI thường đòi hỏi grapheme cluster và đôi lúc chính sách sản phẩm cụ thể; `length()` chỉ đếm đơn vị mã.
4. **Nhận code point offset rồi truyền thẳng vào `charAt()`.** `charAt()` luôn cần UTF-16 index.
5. **Giả sử code point luôn tương ứng một glyph.** Dấu kết hợp, ZWJ sequence, variation selector, cờ và emoji bổ sung có thể tạo nhiều code point trên một cụm hiển thị.
6. **Dùng `codePoints()` để cắt văn bản hiển thị mà không kiểm tra cụm.** Không phá surrogate pair nhưng vẫn có thể tách dấu hoặc thành phần emoji.
7. **Giả sử `BreakIterator` ở mọi JDK đều cho cùng ranh giới với emoji phức tạp.** Cần kiểm thử phiên bản, locale và bộ dữ liệu Unicode cho yêu cầu cụ thể.
8. **Trộn vị trí byte của UTF-8 với chỉ số `String`.** Byte offset phải giải mã/chuyển theo charset; không thể làm `substring()` trực tiếp.
9. **Quên kiểm tra ranh giới và số âm.** Với `charAt()`, chỉ số âm hoặc bằng `length()` gây `StringIndexOutOfBoundsException`; `substring()` cũng báo lỗi cho vùng ngoài phạm vi hoặc `begin > end`. Với hàm cắt theo code point, cần kiểm tra số code point thực.
10. **Quên rằng `String` có thể chứa surrogate lẻ.** Các API Java vẫn có thể quan sát giá trị này, nên khi nhận văn bản từ nguồn không tin cậy phải kiểm tra hợp lệ nếu yêu cầu dữ liệu Unicode chuẩn.

## 10. Checklist trước khi viết vòng lặp hoặc substring

Hãy tự trả lời lần lượt:

1. **Yêu cầu là gì?** Ký tự Java `char`, Unicode code point, ký tự giao diện hay byte của tệp?
2. **Nguồn chỉ số là API nào?** Nếu đến từ `indexOf()`, `Matcher.start()`, `BreakIterator` hoặc `substring()`, kiểm tra hợp đồng chỉ số UTF-16; nếu là con số trong yêu cầu nghiệp vụ, xác định đơn vị được đếm.
3. **Có Unicode ngoài BMP không?** Hãy thử một emoji như 😀 để phát hiện vòng lặp đang cắt surrogate pair.
4. **Có dấu kết hợp hoặc emoji ZWJ không?** Thử `e\u0301` và các emoji ghép để kiểm tra xem code point có đủ hay cần grapheme cluster.
5. **Ranh giới có hợp lệ?** Với UTF-16: `0 <= begin <= end <= length()`. Với code point: `0 <= startPoint`, `0 <= count`, `startPoint + count <= totalCodePoints` (kiểm tra an toàn bằng phép trừ nếu cần).
6. **Mỗi lần chuyển đổi phải rõ ràng.** Biến như `utf16Offset`, `codePointIndex`, `graphemeBoundary` giúp tránh sử dụng nhầm đơn vị.
7. **Kiểm tra với đầu vào mẫu đa dạng.** Chuỗi rỗng, ASCII, tiếng Việt, emoji ngoài BMP, chữ + dấu kết hợp, emoji ghép, và dữ liệu chứa surrogate lẻ nếu có thể gặp.

## 11. Câu hỏi tự kiểm tra

**Câu 1.** Với `String s = "😀X";`, vì sao `s.charAt(1)` không trả về X?

**Đáp án:** emoji chiếm UTF-16 offset 0 và 1; `s.charAt(1)` là low surrogate, còn X ở offset 2.

**Câu 2.** `s.substring(0, 1)` có phải “lấy một Unicode code point đầu” không?

**Đáp án:** không. Nó lấy một UTF-16 code unit, có thể chỉ là high surrogate. Để lấy một code point, dùng `s.substring(0, s.offsetByCodePoints(0, 1))`.

**Câu 3.** Tại sao chuỗi `"e\u0301"` có `length() == 2` và `codePointCount(0, 2) == 2` nhưng thường chỉ hiển thị một chữ?

**Đáp án:** chữ e và dấu sắc kết hợp là hai code point BMP, mỗi điểm mã chiếm một UTF-16 code unit; chúng thường tạo một grapheme cluster hiển thị.

**Câu 4.** Nếu `indexOf("B")` trả về offset 5 trong `"A😀e\u0301B"`, có cần đổi số 5 sang code point index trước khi gọi `substring(0, 5)` không?

**Đáp án:** không. `indexOf()` và `substring()` đều dùng UTF-16 offset, nên giá trị 5 được dùng trực tiếp.

**Câu 5.** Giao diện yêu cầu giới hạn “không quá 20 ký tự hiển thị”. Dùng `codePointCount()` đã bảo đảm đúng chưa?

**Đáp án:** chưa. Một ký tự hiển thị có thể gồm nhiều code point. Cần chọn bộ phân đoạn grapheme cluster phù hợp và kiểm thử trường hợp emoji ghép, dấu kết hợp và các ngôn ngữ mục tiêu.

## Tài liệu tham khảo

- [Java SE 17 — String](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html): `length()`, `charAt()`, `substring()`, `codePointAt()`, `codePointCount()`, `offsetByCodePoints()`, `chars()` và `codePoints()`.
- [Java SE 17 — Character](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html): surrogate pair, `charCount()`, code point và UTF-16.
- [Java SE 17 — BreakIterator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/BreakIterator.html): tìm ranh giới ký tự, từ và câu dựa trên locale.
- [Unicode Standard Annex #29 — Unicode Text Segmentation](https://unicode.org/reports/tr29/): quy tắc phân đoạn grapheme cluster, từ và câu.
- [Unicode Standard Annex #15 — Unicode Normalization Forms](https://unicode.org/reports/tr15/): những dạng biểu diễn Unicode tương đương và chuẩn hóa.


</details>

- [Quay lại đầu trang](#back-to-top)
