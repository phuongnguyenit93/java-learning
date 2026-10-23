<a id="back-to-top"></a>

# Khai báo và tạo chuỗi

## Menu
- [Literal chuỗi và cú pháp dấu nháy kép](#string-creation-001)
- [Escape cho dấu nháy, dấu gạch chéo và ký tự điều khiển](#string-creation-002)
- [Unicode escape trong mã nguồn và thứ tự xử lý của trình biên dịch](#string-creation-003)
- [Tạo String từ char&#91;&#93; hoặc một vùng của mảng](#string-creation-004)
- [Tạo String từ mảng code point](#string-creation-005)
- [Tạo String từ byte&#91;&#93; với bộ mã ký tự](#string-creation-006)
- [Tạo String từ StringBuilder hoặc StringBuffer](#string-creation-007)
- [new String và vì sao thường không cần sao chép một String đã có](#string-creation-008)

## <a id="string-creation-001">Literal chuỗi và cú pháp dấu nháy kép</a>

<details>
<summary>Xem chi tiết</summary>

# Literal chuỗi và cú pháp dấu nháy kép trong Java

## 1. Literal chuỗi là gì?

**String literal** (literal chuỗi) là một đoạn văn bản được viết trực tiếp trong mã nguồn Java và đặt giữa **hai dấu nháy kép** ASCII (`"`). Nó biểu diễn một đối tượng `String` chứa dãy ký tự tương ứng.

```java
String greeting = "Xin chào, Java!";
String empty = "";
String numberAsText = "123";
```

- `"Xin chào, Java!"` và `"123"` đều là biểu thức kiểu `String`, dù nội dung có chữ hay chữ số.
- `""` là **chuỗi rỗng**, có độ dài bằng `0`, không phải `null`.
- Dấu nháy kép **đánh dấu ranh giới** của literal; chúng không trở thành ký tự trong chuỗi.
- `String` là lớp `java.lang.String`; Java tự import `java.lang`, nên không cần câu lệnh `import` riêng.

Đọc `String greeting = "Xin chào, Java!";` theo hai phần: `String greeting` khai báo biến tham chiếu kiểu `String`, còn `"Xin chào, Java!"` là literal dùng để khởi tạo giá trị. Tên biến, kiểu dữ liệu và literal là các thành phần khác nhau.

## 2. Quy tắc cú pháp của dấu nháy kép

Cú pháp cơ bản:

```java
String variableName = "nội dung";
```

1. Literal chuỗi thông thường **mở bằng `"` và đóng bằng `"`** trên cùng một dòng nguồn; trình biên dịch xác định chuỗi kết thúc ở dấu `"` không được escape tiếp theo.
2. Nội dung có thể không có ký tự nào (`""`), có dấu cách (`" "`), ký tự tiếng Việt, ký hiệu, hoặc các **escape sequence** hợp lệ.
3. Muốn đưa một dấu `"` thành **nội dung** của chuỗi, phải viết `\"`. Muốn đưa dấu gạch chéo ngược `\` thành nội dung, phải viết `\\`.
4. Trong literal thông thường, không được chèn trực tiếp một ký tự xuống dòng của mã nguồn ở giữa hai dấu nháy; dùng `\n` để biểu diễn xuống dòng.

Các ví dụ minh họa cú pháp, **không phải chương trình hoàn chỉnh**:

```java
String a = "Java";                 // Hợp lệ
String b = "";                     // Hợp lệ: chuỗi rỗng
String c = " ";                    // Hợp lệ: một dấu cách
String d = "Anh ấy nói: \"Chào\"";  // Hợp lệ: chứa dấu nháy kép
String e = "Dòng 1\nDòng 2";       // Hợp lệ: có ký tự xuống dòng khi chạy
```

Phân biệt dấu nháy:

| Cách viết | Kiểu / ý nghĩa | Ghi chú |
|---|---|---|
| `"A"` | `String` | Một chuỗi dài 1 ký tự |
| `'A'` | `char` | Một giá trị ký tự |
| `""` | `String` | Chuỗi rỗng |
| `"null"` | `String` | Văn bản gồm 4 chữ cái |
| `null` | Literal `null` | Không tham chiếu đến đối tượng nào |

Ví dụ: `String value = 'A';` **không biên dịch** vì `'A'` là `char`, không phải `String`. Muốn gán một chuỗi, viết `String value = "A";`.

## 3. Escape sequence trong literal chuỗi

Dấu `\` mở đầu một **escape sequence**: cách viết đặc biệt trong mã nguồn để đưa ký tự khó gõ trực tiếp hoặc có ý nghĩa cú pháp vào chuỗi.

| Viết trong mã nguồn | Ký tự được tạo ra | Tình huống thường gặp |
|---|---|---|
| `\"` | `"` | Hiển thị dấu nháy kép |
| `\\` | `\` | Đường dẫn Windows, ký tự gạch chéo ngược |
| `\n` | Xuống dòng (line feed) | Văn bản nhiều dòng |
| `\t` | Tab ngang | Phân cách cột |

Các escape còn lại như `\r`, `\b`, `\f`, `\s` và `\'` được trình bày đầy đủ ở bài chuyên về escape ngay sau bài này.

Phân biệt **mã nguồn** và **giá trị thực**:

```java
String newline = "\n";      // Chứa 1 ký tự xuống dòng, không chứa '\' và 'n'
String letters = "\\n";     // Chứa 2 ký tự: '\' và 'n'
String quote = "\"";        // Chứa 1 ký tự: '"'
String windowsPath = "C:\\temp\\log.txt";
```

Đặc biệt, viết đường dẫn như `"C:\temp\log.txt"` là sai ý định: `\t` sẽ thành tab còn `\l` không phải escape hợp lệ nên trình biên dịch báo lỗi. Nếu cần biểu diễn dấu `\` thật, dùng `\\`.

Một escape không được Java nhận biết, chẳng hạn `"\q"`, sẽ gây lỗi biên dịch (*illegal escape character*), không âm thầm giữ nguyên dấu `\`.

### Unicode trong chuỗi

Java có thể dùng ký tự Unicode trực tiếp, chẳng hạn `"Tiếng Việt"`, khi tệp nguồn được đọc đúng encoding. Dạng `\u0041` tạo ký tự `A` ở giai đoạn dịch mã nguồn, **trước khi** phân tích literal. Vì vậy không dùng `\u000A` để viết ký tự xuống dòng trong literal: hãy dùng `\n`. Cơ chế và các bẫy này được phân tích trong bài Unicode escape riêng.

## 4. Ví dụ Java 17+ có thể chạy

Lưu tệp **`StringLiteralDemo.java`** với nội dung:

```java
public class StringLiteralDemo {
    public static void main(String[] args) {
        String greeting = "Xin chào, Java!";
        String empty = "";
        String quoted = "Bạn nói: \"Java\"";
        String path = "C:\\docs\\note.txt";
        String twoLines = "Dòng 1\nDòng 2";
        String tabSeparated = "A\tB";

        System.out.println(greeting);
        System.out.println("empty.length() = " + empty.length());
        System.out.println(quoted);
        System.out.println(path);
        System.out.println(twoLines);
        System.out.println(tabSeparated.replace("\t", "[TAB]"));
    }
}
```

Biên dịch và chạy ở thư mục chứa tệp:

```bash
javac -encoding UTF-8 StringLiteralDemo.java
java StringLiteralDemo
```

**Stdout** (mỗi dòng đúng thứ tự; ví dụ thay tab bằng `[TAB]` để nhìn thấy vị trí tab):

```text
Xin chào, Java!
empty.length() = 0
Bạn nói: "Java"
C:\docs\note.txt
Dòng 1
Dòng 2
A[TAB]B
```

Giải thích:

- Literal `""` tạo chuỗi rỗng nên `empty.length()` là `0`.
- Trong `quoted`, `\"` tạo dấu nháy kép xuất hiện ở stdout.
- Trong `path`, mỗi `\\` tạo ra **một** dấu `\`.
- Trong `twoLines`, `\n` làm `println` hiển thị hai dòng nội dung.
- `\t` tạo tab thật; `replace` chỉ đổi tab thành nhãn dễ quan sát lúc in kết quả.

## 5. Literal chuỗi, biến và đối tượng String

### Literal có thể dùng ở nhiều vị trí

Literal là **biểu thức** kiểu `String`, không chỉ xuất hiện bên phải phép gán:

```java
System.out.println("Sẵn sàng");
boolean isEmpty = "".isEmpty();
int length = "Java".length(); // 4
String message = "Xin " + "chào";
```

Nối bằng `+` tạo một biểu thức chuỗi. Trong Java, `"Java"` không tự biến thành tên biến, `"123"` không tự biến thành số nguyên và `"Xin ${name}"` **không có cơ chế nội suy biến** theo cú pháp `${...}`. Nếu có biến `name`, một cách đơn giản là `"Xin " + name`.

### Vì sao không nên so sánh chuỗi bằng `==`?

Java **intern** các string literal: cùng nội dung literal thường tham chiếu đến cùng đối tượng trong string pool. Trình biên dịch còn có thể gộp những biểu thức nối chỉ chứa hằng tại thời điểm biên dịch. Nhưng `==` trên hai tham chiếu `String` kiểm tra **cùng đối tượng hay không**, không kiểm tra nội dung.

```java
String first = "Java";
String second = "Java";
String builtAtRuntime = new String("Java");

System.out.println(first == second);         // true
System.out.println(first == builtAtRuntime); // false
System.out.println(first.equals(builtAtRuntime)); // true
```

Đây là đoạn minh họa đặt **bên trong một phương thức**, không phải tệp Java độc lập. Sử dụng `.equals(...)` để so sánh nội dung; đừng dựa vào pool để suy luận `==` sẽ luôn đúng với hai chuỗi có chữ giống nhau.

Một literal vẫn là đối tượng `String` bất biến (*immutable*). Ví dụ `text.toUpperCase()` trả về chuỗi mới (hoặc có thể trả lại chính chuỗi nếu không cần thay đổi), chứ không sửa nội dung bên trong đối tượng `String` ban đầu.

## 6. Những lỗi thường gặp

| Đoạn mã | Vấn đề | Cách sửa |
|---|---|---|
| `String s = 'Java';` | Dấu `'...'` không tạo `String`; nhiều ký tự còn không hợp lệ cho `char` | `String s = "Java";` |
| `String s = "Java;` | Thiếu dấu `"` đóng | `String s = "Java";` |
| `String s = "C:\temp\file";` | `\t`, `\f` bị hiểu thành ký tự điều khiển | `String s = "C:\\temp\\file";` |
| `String s = "A "B" C";` | Dấu `"` bên trong làm kết thúc literal sớm | `String s = "A \"B\" C";` |
| `String s = "\q";` | `\q` không phải escape hợp lệ | Sử dụng `\\q` nếu cần ký tự `\` và `q` |
| `String s = ""; if (s == null) ...` | Chuỗi rỗng không phải `null` | Kiểm tra `s.isEmpty()` nếu mục tiêu là rỗng |
| `a == b` với `String` | So sánh tham chiếu | Dùng `a.equals(b)` khi chắc `a` không `null` |

Các dòng lỗi trong bảng là ví dụ chẩn đoán, **không phải mã nguồn chạy được**.

### Chuỗi chứa nhiều dòng thì sao?

Một literal chuỗi **thông thường** dùng `"..."`
không thể xuống dòng trực tiếp trong mã nguồn:

```java
// KHÔNG hợp lệ nếu xuống dòng thật bên trong "..."
String invalid = "Dòng 1
Dòng 2";
```

Viết `"Dòng 1\nDòng 2"` hoặc ghép `"Dòng 1\n" + "Dòng 2"`. Từ Java 15 còn có **text block** sử dụng `"""` cho văn bản nhiều dòng; đó là cú pháp riêng, phù hợp khi cần trình bày nội dung lớn, và khác với cặp dấu `"` của literal thông thường đang xét.

## 7. Tự kiểm tra

1. `"A"` và `'A'` khác nhau về kiểu như thế nào?
2. `" "` có rỗng không? `""` có bằng `null` không?
3. Viết literal biểu diễn nội dung chính xác `C:\java\src`.
4. Vì sao `String text = "Dòng 1\nDòng 2";` có thể in ra hai dòng, dù mã nguồn chỉ có một dòng?
5. Hai biến `String` cùng nội dung thì nên dùng `==` hay `.equals()` để kiểm tra văn bản?

**Đáp án:** (1) `String` và `char`; (2) `" "` chứa một dấu cách, `""` là chuỗi rỗng khác `null`; (3) `"C:\\java\\src"`; (4) `\n` là ký tự xuống dòng trong giá trị của chuỗi; (5) `.equals()`.

Một literal chuỗi hợp lệ cần có đúng ranh giới dấu nháy kép, các escape phù hợp và ý thức rõ sự khác nhau giữa những ký tự **viết trong mã nguồn** và những ký tự **thực sự tồn tại trong đối tượng `String`**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-creation-002">Escape cho dấu nháy, dấu gạch chéo và ký tự điều khiển</a>

<details>
<summary>Xem chi tiết</summary>

# Escape dấu nháy, dấu gạch chéo và ký tự điều khiển trong Java

Khi khai báo chuỗi bằng `String` hoặc ký tự bằng `char`, dấu nháy dùng để **đánh dấu ranh giới literal**. Nếu muốn một dấu nháy hoặc ký tự đặc biệt trở thành **dữ liệu bên trong** literal, Java cung cấp *escape sequence* (chuỗi thoát): một dấu gạch chéo ngược `\` theo sau là ký tự có ý nghĩa đặc biệt. Cần phân biệt rõ **cách viết trong mã nguồn** và **giá trị thật lúc chương trình chạy**.

Bài này tập trung vào Java 17 trở lên: nháy kép `"`, nháy đơn `'`, gạch chéo ngược `\` và các ký tự điều khiển như xuống dòng, tab, carriage return.

## 1. Vì sao phải escape?

`String` literal thông thường được bao bởi nháy kép; `char` literal được bao bởi nháy đơn:

```java
String greeting = "Xin chào";
char initial = 'J';
```

Nếu đặt dấu nháy kép trực tiếp vào giữa `String` literal, trình biên dịch hiểu đó là dấu kết thúc chuỗi. Để đưa dấu `"` vào dữ liệu, viết `\"`:

```java
String message = "Cô ấy nói: \"Xin chào!\"";
System.out.println(message); // Cô ấy nói: "Xin chào!"
```

Trong mã nguồn, `\"` gồm hai ký tự được gõ; trong `message` sau khi biên dịch và chạy, phần đó chỉ là **một ký tự nháy kép**. Dấu `\` phục vụ cú pháp escape, không tự xuất hiện trong giá trị trừ khi ta escape chính nó.

## 2. Bảng escape sequence cần biết

| Viết trong Java literal | Ký tự nhận được | Ý nghĩa và lưu ý |
|---|---|---|
| `\"` | `"` | Nháy kép; cần khi nháy kép nằm trong `String` literal. |
| `\'` | `'` | Nháy đơn; cần khi nháy đơn nằm trong `char` literal. |
| `\\` | `\` | Một dấu gạch chéo **ngược** (backslash). |
| `\n` | LF, U+000A | Xuống dòng (*line feed*). |
| `\r` | CR, U+000D | Đưa vị trí về đầu dòng (*carriage return*); cách hiển thị tùy môi trường. |
| `\t` | TAB, U+0009 | Tab ngang; độ rộng hiển thị tùy thiết bị. |
| `\b` | BS, U+0008 | Backspace; có thể ảnh hưởng vị trí con trỏ, không chắc sẽ xóa ký tự đã in. |
| `\f` | FF, U+000C | Form feed; thường dùng cho ngắt trang trong một số định dạng. |
| `\s` | SPACE, U+0020 | Một dấu cách thông thường; hỗ trợ từ Java 15. |
| `\0` | NUL, U+0000 | Ví dụ một escape bát phân; ít dùng khi viết chuỗi hiện đại. |

`\0` thuộc nhóm *octal escapes* (`\0` đến `\377` theo quy tắc chữ số bát phân), có mặt trong Java nhưng không được nhầm với ký tự kết thúc chuỗi kiểu C: `String` của Java không kết thúc tại NUL. Với ký tự điều khiển thông dụng, nên dùng các escape có tên rõ ràng ở trên.

Dấu gạch chéo **xuôi** `/` không phải là ký tự mở đầu escape trong Java: `"a/b"` dùng trực tiếp, không cần `\/`. Trái lại, gạch chéo **ngược** `\` phải được viết `\\` nếu muốn biểu diễn chính nó.

## 3. Nháy kép và nháy đơn: xem đúng loại literal

### Chuỗi `String` dùng nháy kép

```java
String quoted = "Tên sách: \"Effective Java\"";
String contraction = "It's fine";
String alsoValid = "It\'s fine";
```

- Với `String` được bao bằng `"..."`, dấu `"` bên trong cần escape thành `\"`.
- Dấu `'` bên trong `String` **không bắt buộc** escape. Vì thế `"It's fine"` dễ đọc hơn `"It\'s fine"`.
- `\"` và `\'` là cú pháp hợp lệ trong `String`, nhưng chỉ dùng khi giúp tránh nhầm lẫn.

### Ký tự `char` dùng nháy đơn

```java
char apostrophe = '\'';
char quotationMark = '"';
char backslash = '\\';
char tab = '\t';
```

- Để tạo giá trị `char` là dấu nháy đơn, viết `'\''`.
- Nháy kép bên trong `char` không cần escape: `'"'` hợp lệ.
- `char` biểu diễn đúng **một đơn vị UTF-16**; ví dụ `'\n'` là một ký tự điều khiển, còn `'\\'` là một dấu `\`.
- Không thể viết `char bad = '';` hoặc `char bad = ''';`: đó không phải literal ký tự hợp lệ.

Đừng nhầm biểu diễn chuỗi hai ký tự `"\\n"` với ký tự xuống dòng `'\n'`: kiểu dữ liệu và giá trị của chúng khác nhau.

## 4. Gạch chéo ngược và đường dẫn

`\\` trong source code bắt đầu một escape sequence, vì thế `"C:\temp\new"` **không** chứa đường dẫn mong muốn: `\t` trở thành tab và `\n` trở thành xuống dòng.

```java
String windowsPath = "C:\\temp\\new";
String unixPath = "/home/user/data.txt";
String regexDigits = "\\d+";

System.out.println(windowsPath);  // C:\temp\new
System.out.println(unixPath);     // /home/user/data.txt
System.out.println("123".matches(regexDigits)); // true
```

Với regex, có **hai lớp cú pháp**: Java xử lý literal trước, sau đó trình phân tích regex nhận chuỗi. Muốn regex nhận `\d+` (chữ số), cần viết `"\\d+"` trong Java. Viết `"\d+"` là **lỗi biên dịch** vì `\d` không phải Java escape hợp lệ.

Tương tự, các chuỗi `"\q"` hay `"\x41"` không phải Java string literal hợp lệ. Java không cho phép dùng dấu `\` tùy tiện trước ký tự bất kỳ. Một chuỗi có dấu `\` thật, chẳng hạn nội dung `\n` dưới dạng văn bản, được viết bằng `"\\n"`.

> Khi thao tác đường dẫn thực tế, ưu tiên API `java.nio.file.Path` để ghép hoặc phân tích đường dẫn theo hệ điều hành. Ví dụ đường dẫn Windows ở trên chỉ minh họa cách viết *literal*, không khẳng định tệp tồn tại.

## 5. Ký tự điều khiển và chiều dài chuỗi

`"\n"` là một ký tự LF. `"\\n"` là **hai ký tự**: backslash và chữ `n`.

```java
System.out.println("\n".length());     // 1
System.out.println("\\n".length());    // 2
System.out.println("A\tB".length());   // 3
System.out.println("\r\n".length());   // 2
```

`\r\n` là cặp CR + LF được dùng làm dấu xuống dòng ở một số ngữ cảnh; `\n` là LF đơn lẻ. Khi **tạo xuống dòng theo nền tảng**, cân nhắc `System.lineSeparator()`; khi một giao thức hoặc định dạng yêu cầu LF hoặc CRLF cố định, hãy dùng đúng ký tự mà đặc tả đó quy định.

`\t` thường hiển thị giống nhiều dấu cách nhưng **không phải** nhiều dấu cách. `\b`, `\f` và `\r` có thể làm đầu ra terminal thay đổi hoặc khó quan sát; chúng vẫn là những ký tự bên trong chuỗi, kể cả khi không thấy hình dạng trên màn hình.

### Bài chạy hoàn chỉnh: quan sát đúng ký tự được tạo

Lưu vào `EscapeDemo.java`:

```java
public class EscapeDemo {
    public static void main(String[] args) {
        String greeting = "Cô ấy nói: \"Xin chào!\"";
        char apostrophe = '\'';
        String path = "C:\\temp\\notes.txt";
        String control = "A\n\tB\rC\bD\fE";
        String literalBackslashN = "\\n";
        String regex = "\\d+";

        System.out.println(greeting);
        System.out.println(apostrophe);
        System.out.println(path);
        System.out.println("control: " + visible(control));
        System.out.println("literal: " + visible(literalBackslashN));
        System.out.println("LF length: " + "\n".length());
        System.out.println("literal length: " + literalBackslashN.length());
        System.out.println("digits: " + "123".matches(regex));
    }

    private static String visible(String text) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            switch (text.charAt(i)) {
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                default -> result.append(text.charAt(i));
            }
        }
        return result.toString();
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 EscapeDemo.java
java EscapeDemo
```

Đầu ra:

```text
Cô ấy nói: "Xin chào!"
'
C:\temp\notes.txt
control: A\n\tB\rC\bD\fE
literal: \\n
LF length: 1
literal length: 2
digits: true
```

Hàm `visible` là **công cụ quan sát**: nó đổi những ký tự điều khiển và dấu backslash thật thành cách viết có thể nhìn thấy, nên đầu ra không bị ảnh hưởng bởi cách terminal xử lý backspace hoặc carriage return. Đặc biệt, dòng `literal: \\n` nghĩa là chuỗi đầu vào có **một backslash thật và chữ n**, sau đó hàm `visible` hiển thị backslash đó bằng hai dấu `\\`.

Nếu in `control` trực tiếp, LF/tab/CR/backspace/form feed có thể ảnh hưởng bố cục hiển thị; không nên suy luận chính xác nội dung chuỗi chỉ từ ảnh chụp terminal.

## 6. Unicode escape khác với escape thông thường

Java còn có Unicode escape dạng `\uXXXX`, được xử lý **trước khi** nhận diện token của mã nguồn. Đây là cơ chế khác với các escape `\n`, `\t`, `\\` được phân tích **sau khi** đã nhận diện literal `String` hoặc `char`.

Ví dụ, `String s = "\n";` hợp lệ và chứa một LF; ngược lại, viết `\u000A` trực tiếp giữa dấu nháy có thể tạo xuống dòng trong nguồn quá sớm và làm lỗi cú pháp. Nếu cần ký tự Unicode theo code point lúc chạy, có thể dùng `Character.toString(0x1F600)`; một code point ngoài BMP thường cần hai đơn vị `char`. Bài kế tiếp giải thích toàn bộ thứ tự dịch Unicode và quy tắc backslash của trình biên dịch.

## 7. Text block có làm mất nhu cầu escape không?

Từ Java 15, text block `"""..."""` cho phép viết chuỗi nhiều dòng dễ đọc, tránh việc phải chèn `\n` liên tục. Dấu nháy kép đơn lẻ thường có thể xuất hiện trực tiếp bên trong text block; nhưng **backslash vẫn là ký tự escape**, và các escape như `\t`, `\n` vẫn được xử lý.

```java
String text = """
        Người dùng nói: "Xin chào!"
        Đường dẫn: C:\\temp
        """;
```

Text block trên chứa dấu nháy kép trực tiếp và ký tự xuống dòng thật theo cấu trúc text block; `\\` vẫn cần để tạo backslash cho đường dẫn. Java còn có quy tắc xử lý thụt lề và xuống dòng ở cuối text block; không thể coi mọi khoảng trắng trong source đều được giữ nguyên. Dùng string literal `"..."` cho một dòng ngắn, text block cho nội dung nhiều dòng khi cách trình bày rõ ràng hơn.

## 8. Những lỗi thường gặp

| Hiện tượng | Nguyên nhân | Cách sửa |
|---|---|---|
| `String s = "He said "Hi"";` lỗi biên dịch | Dấu `"` bên trong kết thúc literal quá sớm | Viết `"He said \"Hi\""`. |
| `char c = ''';` lỗi | Chưa escape nháy đơn của `char` | Viết `char c = '\'';`. |
| `String s = "C:\Users";` lỗi `illegal escape character` | `\U` không phải Java escape | Viết `"C:\\Users"` hoặc xử lý bằng `Path`. |
| `"C:\temp"` chạy nhưng có tab | `\t` được hiểu là TAB | Viết `"C:\\temp"`. |
| `String s = "\d+";` lỗi | Nhầm escape của regex với Java | Viết `"\\d+"`. |
| In `"\n"` nhưng muốn hiện ký tự `\` và `n` | `\n` tạo xuống dòng thật | Viết `"\\n"`. |
| Ghép `"a" + "\t" + "b"` rồi thấy khoảng cách không đều | TAB tùy vị trí tab stop của nơi hiển thị | Dùng khoảng trắng hoặc định dạng đầu ra rõ ràng nếu cần canh cột. |
| So sánh `"\r\n".length()` với `"\n".length()` | CRLF là hai ký tự; LF là một | Phân biệt rõ chuẩn xuống dòng của dữ liệu. |

Đừng dùng `String.replace("\\n", "\n")` một cách mặc định cho mọi văn bản nhận từ bên ngoài: thao tác đó biến hai ký tự backslash + n thành một dấu xuống dòng, có thể làm đổi dữ liệu người dùng. Chỉ giải mã escape khi định dạng đầu vào **thực sự** xác định có một lớp escape cần giải mã; Java không tự động giải mã ký tự backslash trong dữ liệu nhận lúc chạy.

## 9. Tự kiểm tra

Hãy đoán giá trị thật, sau đó kiểm chứng bằng `length()` hoặc hàm `visible` phía trên:

```java
String a = "\"";
String b = "\\";
String c = "\\t";
String d = "\t";
String e = "a/b";
char f = '\'';
```

- `a` là **một** dấu nháy kép, `a.length() == 1`.
- `b` là **một** dấu backslash, `b.length() == 1`.
- `c` chứa **hai** ký tự (backslash và `t`); `d` chứa **một** tab.
- `e` chứa ba ký tự; dấu `/` không cần escape.
- `f` là giá trị `char` chứa một dấu nháy đơn.

**Ghi nhớ:** Cú pháp `\...` nằm trong **mã nguồn literal** để biểu diễn ký tự dữ liệu. Muốn nhìn thấy backslash thật trong giá trị `String`/`char`, phải viết `\\`; muốn có xuống dòng/tab thật, dùng `\n`/`\t`. Luôn phân biệt cái được gõ trong source, ký tự tồn tại trong bộ nhớ và cách ký tự đó được hiển thị.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-creation-003">Unicode escape trong mã nguồn và thứ tự xử lý của trình biên dịch</a>

<details>
<summary>Xem chi tiết</summary>

# Unicode escape trong mã nguồn Java và thứ tự xử lý của trình biên dịch

## 1. Mục tiêu

Sau bài này, bạn có thể giải thích vì sao `\u0041` trong mã nguồn Java có thể trở thành chữ `A` **trước khi** trình biên dịch nhận diện chuỗi, ký tự, từ khóa hay chú thích; phân biệt Unicode escape ở mức **mã nguồn** với escape của `String`/`char`; dự đoán các trường hợp có dấu gạch chéo ngược, xuống dòng, dấu nháy; và viết mã an toàn, dễ đọc khi cần xử lý Unicode.

Trọng tâm là **thứ tự xử lý văn bản nguồn của `javac`**. Đây không phải một API xử lý chuỗi khi chương trình đang chạy.

## 2. Unicode escape là gì?

Trong **tệp nguồn `.java`**, Unicode escape có dạng:

~~~text
\uXXXX
~~~

- `\` và `u` là ký tự ASCII; `XXXX` là **đúng bốn chữ số hệ 16** (`0–9`, `a–f`, `A–F`).
- Có thể có nhiều chữ `u` liên tiếp, ví dụ `\uuuu0041` cũng tạo ra `A`.
- Mỗi escape đại diện cho **một đơn vị mã UTF-16** (UTF-16 code unit) từ `U+0000` đến `U+FFFF`. Vì vậy `\u0041` → `A`, `\u03A9` → `Ω`; một code point bổ sung như `U+1F600` cần **hai** đơn vị surrogate (`\uD83D\uDE00`).
- Dạng `\u{1F600}`, `\U0001F600` hay chỉ ba/chín chữ số hex **không phải** cú pháp Unicode escape của Java.
- Escape được xử lý ở **khắp văn bản nguồn**, kể cả vùng sau `//`, giữa `/* ... */`, bên trong literal chuỗi, literal ký tự và text block. Nó không phụ thuộc vào phạm vi cú pháp mà mắt người nhìn thấy.

Ví dụ ở mức văn bản nguồn: `cl\u0061ss` tạo thành từ khóa `class`; `\u002f\u002f` tạo thành hai dấu `//`; `\u000a` tạo thành ký tự LF xuống dòng. **Đó là phép dịch ký tự trước khi Java phân tích cú pháp**, không phải các phép thay thế diễn ra lúc chạy chương trình.

## 3. Trình biên dịch xử lý theo thứ tự nào?

Theo *Java Language Specification* (JLS) `3.2–3.5, tiến trình từ tệp mã nguồn đến token có ba giai đoạn dịch từ vựng:

~~~text
Byte trong tệp .java
    |
    v
Giải mã byte thành ký tự nguồn (chẳng hạn UTF-8)
    |
    v
(1) Dịch Unicode escape hợp lệ: \uXXXX -> một UTF-16 code unit
    |
    v
(2) Nhận diện kết thúc dòng: CR, LF, CRLF
    |
    v
(3) Phân tách input elements: khoảng trắng, comment, token
    |
    v
Phân tích cú pháp, kiểm tra kiểu, tạo bytecode, ...
~~~

Điều quan trọng là **bước (1) xảy ra trước bước (2) và (3)**. Vì thế:

- Nếu `\u000a` xuất hiện trong nội dung một dòng `//`, nó trở thành LF **trước lúc** nhận diện ranh giới chú thích. Phần phía sau LF có thể là mã Java hoạt động.
- Nếu `\u0022` tạo ra dấu `"`, dấu đó tham gia xác định **ranh giới chuỗi**. Nó không được tự động hiểu là một dấu nháy thuộc *giá trị* chuỗi.
- Nếu `\u005c` tạo ra `\`, ký tự đó có thể ảnh hưởng đến cú pháp literal được phân tích **sau đó**; việc dịch Unicode escape không tự chạy lại đệ quy.
- Một Unicode escape tạo ra chữ thường cũng có thể nằm trong tên biến, từ khóa hoặc toán tử: thứ tự này không giới hạn trong `String`.

Lưu ý: mã hóa tệp (`UTF-8`, v.v.) là việc giải mã **byte**; Unicode escape là chuỗi ký tự **ASCII viết trong mã nguồn**. Có thể dùng ký tự `Ω` trực tiếp trong tệp UTF-8 hoặc viết `\u03A9`; hai cách tạo cùng đơn vị mã trong ví dụ này. Khi môi trường mã hóa nguồn không được bảo đảm, chỉ rõ `-encoding UTF-8` lúc biên dịch.

## 4. Ví dụ chạy được với Java 17+

Tạo tệp **`UnicodeEscapeDemo.java`** với nội dung sau (giữ nguyên các dấu `\` trong source):

~~~java
public class UnicodeEscapeDemo {
    public static void main(String[] args) {
        String literalA = "A";
        String sourceEscapeA = "\u0041";
        String visibleEscapeText = "\\u0041";
        char omega = '\u03A9';
        String emoji = "\uD83D\uDE00";

        System.out.println(literalA.equals(sourceEscapeA));
        System.out.println(sourceEscapeA);
        System.out.println(visibleEscapeText);
        System.out.println((int) omega);
        System.out.println(emoji.length());
        System.out.println(emoji.codePointCount(0, emoji.length()));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 UnicodeEscapeDemo.java
java UnicodeEscapeDemo
~~~

Kết quả chuẩn:

~~~text
true
A
\u0041
937
2
1
~~~

Giải thích từng kết quả:

1. `"\u0041"` được dịch thành `"A"` **trước** khi quét token nên so sánh với `"A"` trả về `true`.
2. `sourceEscapeA` chứa đúng ký tự `A`.
3. `"\\u0041"` là một chuỗi gồm **hai** gạch chéo ngược ở mã nguồn, theo sau bởi `u0041`. Quy tắc chọn dấu `\` hợp lệ ngăn dấu thứ hai mở đầu Unicode escape; đến khi phân tích literal, `\\` mới cho ra **một** gạch chéo ngược trong chuỗi kết quả, nên in nguyên văn `\u0041`.
4. `\u03A9` là `Ω`, đơn vị UTF-16 có giá trị thập phân `937`.
5. `\uD83D\uDE00` biểu diễn một code point bổ sung bằng **hai** `char` UTF-16: `length()` bằng `2`, nhưng `codePointCount(...)` bằng `1`. Không lấy độ dài `String` làm số ký tự Unicode theo nghĩa code point.

Lưu ý rằng kết quả trên không cần thư viện Unicode đặc biệt; đó là đặc tả về cách trình biên dịch hiểu **ký tự viết trong nguồn**.

## 5. Các bẫy do dịch Unicode escape quá sớm

### 5.1. `\u000a` có thể kết thúc chú thích `//`

Tệp **`CommentEscapeDemo.java`**:

~~~java
public class CommentEscapeDemo {
    public static void main(String[] args) {
        System.out.println("Truoc");
        // \u000a System.out.println("Sau");
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 CommentEscapeDemo.java
java CommentEscapeDemo
~~~

Output:

~~~text
Truoc
Sau
~~~

Tại sao? Bước (1) biến `\u000a` thành LF. Bước (2) xác định đó là xuống dòng thật. Bước (3) chỉ coi nội dung **trước** LF là phần của comment `//`; lệnh `System.out.println("Sau");` giờ nằm sau ranh giới comment và được thực thi. Một cách tương tự, ký tự CR `\u000d` cũng có thể kết thúc dòng.

**Quy tắc khi review code:** đừng cho rằng các ký tự `\uXXXX` trong comment chắc chắn là văn bản vô hại. Khi phân tích code lạ, cần xét phép dịch trước khi kết luận đoạn nào là chú thích.

### 5.2. Không dùng Unicode escape để viết CR/LF bên trong literal

Hai ví dụ sau **cố ý không biên dịch được**:

~~~java
String badLine = "Dong 1\u000aDong 2";
char badLf = '\u000a';
~~~

`\u000a` trở thành LF **trước lúc** trình biên dịch xử lý chuỗi/`char`, nên một line terminator xuất hiện giữa dấu nháy; hai literal này không hợp lệ. Để đặt ký tự xuống dòng vào **giá trị** chuỗi hoặc ký tự, viết:

~~~java
String goodLine = "Dong 1\nDong 2";
char goodLf = '\n';
String windowsBreak = "\r\n";
~~~

Escape `\n`/`\r` được diễn giải **khi phân tích nội dung literal**, sau giai đoạn Unicode escape. Vì vậy chúng không làm vỡ cú pháp mã nguồn.

### 5.3. Unicode escape thành dấu nháy hoặc gạch chéo ngược

Đây cũng là ví dụ **cố ý sai cú pháp**:

~~~java
String badQuote = "\u0022";
char badApostrophe = '\u0027';
String badSlash = "\u005c";
~~~

- `\u0022` đổi thành dấu nháy kép `"`, khiến dấu nháy mở/đóng literal bị thay đổi (dòng thứ nhất không phải một chuỗi chứa dấu nháy).
- `\u0027` đổi thành `'`, không tạo `char` chứa dấu nháy đơn như ý định.
- `\u005c` đổi thành `\`. Trong dòng thứ ba, dấu `\` ngay trước dấu nháy đóng có thể escape dấu nháy đó và làm literal không kết thúc hợp lệ.

Viết escape **của literal**, không dùng Unicode escape sớm, để biểu diễn các ký tự đặc biệt:

~~~java
String quote = "\"";
char apostrophe = '\'';
String slash = "\\";
~~~

Các câu lệnh cuối đều hợp lệ: `quote` chứa `"`, `apostrophe` chứa `'`, `slash` chứa một ký tự `\`.

### 5.4. Không dịch Unicode escape nhiều lần

Ví dụ raw source `\u005cu005a`: escape `\u005c` sinh ra `\`, sau đó các ký tự `u005a` vẫn giữ nguyên. **Trình biên dịch không xem lại** ký tự `\` vừa được sinh ra để dịch tiếp thành `Z`. Kết quả giai đoạn Unicode là chuỗi ký tự nguồn `\u005a`, **không phải** chữ `Z`. Nếu đặt chuỗi ký tự đó bên trong literal Java, `\u` có thể trở thành escape sequence **không hợp lệ** ở giai đoạn literal; không nên coi ví dụ này là một literal chạy được.

Cần diễn đạt rõ hai ý khác nhau: ký tự tạo ra từ Unicode escape không tự khởi động một lượt dịch mới; nhưng một dấu `\` viết **trực tiếp** trong raw source xuất hiện sau một Unicode escape có thể được xét theo quy tắc chọn dấu gạch chéo tiếp theo.

## 6. Dấu `\` nào được phép mở đầu Unicode escape?

Khi duyệt đầu vào thô, **không phải mọi** `\u` đều được chuyển thành ký tự. JLS `3.3 có quy tắc về *eligible backslash* (dấu gạch chéo ngược đủ điều kiện):

- Với `\` ASCII đang xét, nếu ký tự raw gần nhất **được sinh ra bởi một Unicode escape trước đó**, `\` hiện tại đủ điều kiện mở đầu escape mới.
- Trường hợp còn lại, đếm số dấu `\` liên tiếp ngay trước nó trong kết quả dịch cho tới ký tự không phải `\` (hoặc đầu dữ liệu): nếu số đó **chẵn**, `\` hiện tại đủ điều kiện; nếu **lẻ**, không đủ điều kiện.
- Nếu dấu đủ điều kiện được theo sau bởi một hoặc nhiều `u` nhưng không có đúng bốn ký tự hex hợp lệ theo sau, đó là **lỗi biên dịch** (không được âm thầm coi là văn bản bình thường).
- Ký tự đã được sinh ra bởi Unicode escape **không tự trở thành một escape mới**.

Trong thực hành với `String`, các trường hợp cần nhớ:

| Văn bản viết trong `.java` | Ý nghĩa khi xử lý | Kết quả mong đợi |
| --- | --- | --- |
| `"\u0041"` | Unicode escape được dịch sớm | Chuỗi chứa `A` |
| `"\\u0041"` | Hai dấu `\` thô đứng cạnh; dấu thứ hai không đủ điều kiện Unicode escape | Chuỗi chứa đúng sáu ký tự `\u0041` |
| `"\uu0041"` | Có nhiều `u`, vẫn hợp lệ | Chuỗi chứa `A` |
| `"\n"` | Escape của literal, không phải Unicode escape | Chuỗi có một LF |
| `"\u000a"` | LF thật được tạo trước lúc nhận diện chuỗi | **Không biên dịch** |
| `'\u03A9'` | Escape nguồn thành một đơn vị UTF-16 | `char` có giá trị `Ω` |
| `"\uD83D\uDE00"` | Hai surrogate UTF-16 | `String.length() == 2` |

Ví dụ nâng cao: raw literal `"\\\u006e"` có ba dấu `\` liên quan (hai dấu đầu thô, dấu thứ ba mở đầu `\u006e`). Dịch Unicode cho phần nội dung `\\n`; giai đoạn escape của literal đọc thành ký tự LF. Đây là minh họa vì sao **quy tắc chẵn/lẻ** và **thứ tự hai giai đoạn** phải được áp dụng riêng rẽ. Trong code thường ngày, hãy viết `"\n"` vì rõ ý định hơn.

## 7. Phân biệt ba lớp thường bị nhầm

| Lớp xử lý | Diễn ra khi nào? | Ví dụ | Kết quả |
| --- | --- | --- | --- |
| Giải mã nguồn | Trước dịch từ vựng, đọc byte `.java` | File lưu UTF-8 có `Ω` | Ký tự nguồn `Ω` |
| **Unicode escape nguồn** | Sớm nhất trong ba bước dịch từ vựng của Java | `\u03A9` | Một đơn vị UTF-16 `Ω` trong luồng nguồn |
| **Escape literal** | Khi đã xác định token chuỗi/`char` | `\n`, `\t`, `\\`, `\"` | LF, tab, dấu `\`, dấu `"` trong giá trị |
| Nội dung `String` lúc chạy | Sau biên dịch, phụ thuộc dữ liệu/API | `"\\u0041"` | Sáu ký tự `\`, `u`, `0`, `0`, `4`, `1`; Java không tự dịch thành `A` |

Đặc biệt, `String.translateEscapes()` là API dành cho **escape sequence kiểu literal**; nó không phải bộ phân tích cú pháp `\uXXXX` cho dữ liệu runtime. Nếu nhập từ JSON, database, HTTP hoặc người dùng một chuỗi chứa sáu ký tự `\u0041`, việc có diễn giải thành `A` hay không phụ thuộc **định dạng/protocol/parser** cụ thể. Không thể suy ra từ cơ chế Unicode escape của file `.java`.

## 8. Áp dụng đúng và tránh lỗi

1. **Ưu tiên ký tự trực tiếp** trong nguồn UTF-8 khi dễ đọc: `"Cà phê"`, `"Ω"`. Unicode escape hữu ích khi cần biểu diễn code unit khó gõ hoặc muốn nêu rõ giá trị hex.
2. **Ưu tiên `\n`, `\r`, `\t`, `\"`, `\'`, `\\` trong literal**; tránh `\u000a`, `\u000d`, `\u0022`, `\u0027`, `\u005c` để thay cho các escape ấy, vì việc dịch sớm thay đổi token hóa.
3. **Kiểm tra comment chứa `\uXXXX`**, nhất là escape của CR/LF hay ký tự cấu trúc. IDE syntax highlighting có thể tô comment theo raw text; kết luận cuối cùng phải theo parser/compiler.
4. **Phân biệt code unit và code point**. Một `char` chỉ chứa một code unit UTF-16. Code point ngoài BMP thường cần một cặp surrogate; dùng `codePointCount` hoặc API code point khi đếm ký tự Unicode theo code point.
5. **Đọc lỗi theo từng giai đoạn**. Nếu `javac` báo chuỗi không đóng hoặc escape bất hợp lệ ở vị trí khó hiểu, kiểm tra `\u` trong nguồn trước. Một ký tự tạo sớm có thể gây lỗi ở tokenization hoặc parsing muộn hơn.
6. **Không tạo Unicode escape bằng nối chuỗi tại runtime rồi mong trình biên dịch dịch lại**. Ví dụ `String text = "\\" + "u0041";` tạo ra sáu ký tự thường; Java không biên dịch lại nội dung của `text`.

## 9. Tự kiểm tra

**Câu 1.** Vì sao `"\\u0042"` in `\u0042` chứ không in `B`?

**Đáp án:** vì chuỗi gạch chéo thô liền nhau khiến dấu thứ hai không đủ điều kiện mở Unicode escape; khi phân tích literal, `\\` cho ra một dấu `\` và `u0042` còn nguyên.

**Câu 2.** Đoạn `// \u000a System.out.println("X");` trong thân phương thức có thể in `X` không?

**Đáp án:** có; `\u000a` trở thành LF trước khi xác định điểm kết thúc `//`, nên câu lệnh sau LF được phân tích như mã lệnh.

**Câu 3.** Dùng `'\u000a'` hay `'\n'` để tạo `char` LF?

**Đáp án:** `'\n'`; `'\u000a'` tạo xuống dòng trong mã nguồn quá sớm và gây lỗi biên dịch.

**Câu 4.** Tại sao `"\uD83D\uDE00".length()` bằng `2`, dù đại diện một biểu tượng Unicode?

**Đáp án:** `String.length()` đếm **code unit UTF-16**, còn ký tự ở code point bổ sung cần một cặp surrogate (hai code unit).

## 10. Tài liệu chuẩn

- [Java Language Specification §3.2 — Lexical Translations](https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#jls-3.2).
- [Java Language Specification §3.3 — Unicode Escapes](https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#jls-3.3).
- [Java Language Specification §3.4 — Line Terminators](https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#jls-3.4).
- [Java Language Specification §3.10 — Literals và escape sequences](https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#jls-3.10).

**Ghi nhớ:** Unicode escape của Java được dịch **trước** khi nhận diện dòng, comment và literal. Khi cần một ký tự đặc biệt **trong giá trị chuỗi**, dùng escape của literal; khi cần hiểu cách trình biên dịch đọc **mã nguồn**, áp dụng quy tắc Unicode escape và dấu `\` đủ điều kiện trước tiên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-creation-004">Tạo String từ char&#91;&#93; hoặc một vùng của mảng</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo String từ char[] hoặc một vùng của mảng trong Java

Trong Java, `char[]` là mảng chứa các giá trị `char` có thể thay đổi; `String` là chuỗi ký tự **bất biến** (immutable). Khi đã có dữ liệu ở dạng `char[]` và cần một chuỗi để hiển thị, so sánh, truyền cho API hoặc lưu kết quả xử lý, có thể tạo `String` từ **toàn bộ mảng** hoặc từ **một đoạn liên tiếp** của mảng. Hai constructor cần nắm vững là `new String(char[])` và `new String(char[], int offset, int count)`.

## 1. Hai cách tạo chuỗi

| Cú pháp | Lấy ký tự nào? | Kết quả |
| --- | --- | --- |
| `new String(char[] value)` | Toàn bộ phần tử từ chỉ số `0` đến `value.length - 1` | Một `String` có nội dung tương ứng với toàn bộ mảng tại thời điểm tạo |
| `new String(char[] value, int offset, int count)` | `count` phần tử liên tiếp bắt đầu tại `offset` | Một `String` có nội dung của đúng vùng được chọn |

Với constructor thứ hai:

- `value`: mảng `char[]` nguồn.
- `offset`: **chỉ số bắt đầu**, tính từ 0, bao gồm phần tử tại chỉ số đó.
- `count`: **số phần tử cần lấy**, không phải chỉ số kết thúc.
- Vùng được sao chép có dạng **[offset, offset + count)**: lấy từ `offset` đến `offset + count - 1`. Dấu `)` nghĩa là không lấy phần tử ở chỉ số `offset + count`.

Ví dụ với mảng `{'J', 'a', 'v', 'a', ' ', '1', '7'}`:

| Chỉ số | 0 | 1 | 2 | 3 | 4 | 5 | 6 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Giá trị | J | a | v | a | khoảng trắng | 1 | 7 |

`new String(chars, 0, 4)` lấy bốn ký tự tại chỉ số 0, 1, 2, 3 nên cho `"Java"`. `new String(chars, 5, 2)` lấy hai ký tự tại chỉ số 5, 6 nên cho `"17"`.

## 2. Ví dụ Java 17+ chạy độc lập

Lưu chương trình sau vào tệp **CharArrayToStringDemo.java**:

~~~java
public class CharArrayToStringDemo {
    public static void main(String[] args) {
        char[] chars = {'J', 'a', 'v', 'a', ' ', '1', '7'};

        String whole = new String(chars);
        String word = new String(chars, 0, 4);
        String version = new String(chars, 5, 2);
        String empty = new String(chars, chars.length, 0);

        System.out.println("whole = " + whole);
        System.out.println("word = " + word);
        System.out.println("version = " + version);
        System.out.println("empty length = " + empty.length());

        chars[0] = 'X'; // Thay đổi mảng sau khi đã tạo String
        System.out.println("chars[0] = " + chars[0]);
        System.out.println("whole sau sua mang = " + whole);
        System.out.println("word sau sua mang = " + word);
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 CharArrayToStringDemo.java
java CharArrayToStringDemo
~~~

Kết quả chuẩn:

~~~text
whole = Java 17
word = Java
version = 17
empty length = 0
chars[0] = X
whole sau sua mang = Java 17
word sau sua mang = Java
~~~

Điểm then chốt: các constructor **sao chép nội dung ký tự** tại thời điểm gọi. Thay `chars[0]` từ `'J'` thành `'X'` chỉ thay đổi mảng; `whole` và `word` vẫn giữ nội dung cũ. Cũng không thể sửa một ký tự trong `String` bằng phép gán như `whole.charAt(0) = 'X'` vì `charAt()` trả về giá trị, không phải vị trí có thể gán. Khi cần tạo chuỗi mới từ mảng đã sửa, hãy gọi constructor lần nữa.

## 3. Kiểm tra vị trí bắt đầu và độ dài

Đặt `n = value.length`. Một vùng hợp lệ phải thỏa mãn đồng thời:

- `offset >= 0`;
- `count >= 0`;
- `offset <= n - count` (tương đương chọn vùng không vượt quá cuối mảng; dạng phép trừ còn tránh tràn số khi tự kiểm tra trên `int`).

Nếu `offset` âm, `count` âm hoặc vùng vượt mảng, `new String(value, offset, count)` ném **`IndexOutOfBoundsException`**. Khi mảng `value` là `null`, thao tác không thể đọc ký tự và ném **`NullPointerException`**. Đây là các lỗi runtime; constructor không tự cắt vùng về giới hạn hợp lệ.

Một số trường hợp biên cần phân biệt:

| `value.length` | `offset` | `count` | Hợp lệ? | Ý nghĩa |
| ---: | ---: | ---: | --- | --- |
| 3 | 0 | 3 | Có | Lấy toàn bộ mảng |
| 3 | 1 | 2 | Có | Lấy hai ký tự cuối |
| 3 | 0 | 0 | Có | Chuỗi rỗng |
| 3 | 3 | 0 | Có | Chuỗi rỗng bắt đầu ngay sau ký tự cuối |
| 3 | 4 | 0 | Không | `offset` vượt quá `length` |
| 3 | 2 | 2 | Không | Muốn lấy đến chỉ số 3, vượt mảng |
| 3 | -1 | 1 | Không | `offset` âm |
| 3 | 1 | -1 | Không | `count` âm |

Có thể kiểm tra hành vi bằng chương trình độc lập **RangeValidationDemo.java**:

~~~java
public class RangeValidationDemo {
    public static void main(String[] args) {
        char[] data = {'a', 'b', 'c'};

        System.out.println("valid = " + new String(data, 1, 2));
        System.out.println("empty = " + new String(data, 3, 0).length());

        try {
            new String(data, 2, 2);
        } catch (IndexOutOfBoundsException ex) {
            System.out.println("out of range");
        }

        try {
            new String((char[]) null);
        } catch (NullPointerException ex) {
            System.out.println("null array");
        }
    }
}
~~~

Chạy `javac -encoding UTF-8 RangeValidationDemo.java` rồi `java RangeValidationDemo`:

~~~text
valid = bc
empty = 0
out of range
null array
~~~

Cú pháp ép kiểu `(char[]) null` trong ví dụ giúp Java chọn chính xác constructor nhận `char[]`; lời gọi `new String(null)` thiếu kiểu cụ thể là **mơ hồ** vì `String` có nhiều constructor nhận kiểu tham chiếu.

Khi các chỉ số xuất phát từ đầu vào hoặc thao tác tính toán, nên kiểm tra giới hạn trước khi tạo chuỗi. Chẳng hạn, hàm dưới đây trả về kết quả chỉ khi phạm vi hợp lệ:

~~~java
static String take(char[] source, int offset, int count) {
    if (source == null) {
        throw new IllegalArgumentException("source must not be null");
    }
    if (offset < 0 || count < 0 || offset > source.length - count) {
        throw new IllegalArgumentException("invalid slice");
    }
    return new String(source, offset, count);
}
~~~

Đây là **đoạn phương thức minh họa**, cần đặt bên trong một lớp Java để biên dịch. Kiểu ngoại lệ trong ví dụ là lựa chọn của hàm `take` để báo lỗi đầu vào; constructor của `String` có các ngoại lệ như mô tả phía trên.

## 4. Sao chép, tính bất biến và vòng đời dữ liệu

`char[]` là cấu trúc có thể sửa tại chỗ: `chars[i] = 'x'` làm thay đổi mảng. `String` đã tạo không thay đổi giá trị sau đó. Hai constructor `char[]` sao chép các ký tự được chọn; chúng không cung cấp “cửa sổ sống” liên kết với mảng gốc. Vì thế:

1. Sửa mảng **trước** khi tạo chuỗi sẽ ảnh hưởng đến nội dung chuỗi được tạo sau đó.
2. Sửa mảng **sau** khi tạo chuỗi sẽ **không** ảnh hưởng đến chuỗi cũ.
3. Hai lần `new String(chars)` tạo hai đối tượng `String` riêng dù nội dung có thể bằng nhau; so sánh giá trị chuỗi bằng `equals()`, không dựa vào `==`.
4. Không nên suy ra chi tiết biểu diễn nội bộ của `String` (như kích thước mảng nội bộ) từ constructor; API chỉ bảo đảm nội dung và tính bất biến.

Với mật khẩu hoặc dữ liệu nhạy cảm, chuyển `char[]` thành `String` tạo thêm một bản nội dung bất biến mà chương trình không thể xóa từng ký tự theo yêu cầu; hãy cân nhắc yêu cầu bảo mật và API nhận dữ liệu đầu vào trước khi tạo chuỗi.

## 5. Lưu ý Unicode: `char` không luôn bằng một ký tự người đọc thấy

Mỗi `char` Java biểu diễn **một đơn vị mã UTF-16 (16 bit)**. Một ký tự Unicode nằm ngoài Basic Multilingual Plane (chẳng hạn nhiều emoji) cần **hai `char`**, gọi là một *surrogate pair*. Vì vậy `offset` và `count` đếm số phần tử `char[]`, **không đếm số ký tự hiển thị hay code point**.

Ví dụ dưới đây là **đoạn lệnh đặt bên trong một phương thức Java**:

~~~java
char[] units = "A😀B".toCharArray();
System.out.println(units.length); // 4: A, high surrogate, low surrogate, B

String emoji = new String(units, 1, 2);
System.out.println(emoji.length()); // 2 don vi ma UTF-16
System.out.println(emoji.codePointCount(0, emoji.length())); // 1

String broken = new String(units, 1, 1); // Chi lay high surrogate
System.out.println(broken.length()); // 1, nhung khong la emoji hoan chinh
~~~

Các con số được in ra tương ứng lần lượt là `4`, `2`, `1`, `1`. Vùng `[1, 3)` giữ nguyên cặp surrogate của emoji; vùng `[1, 2)` cắt đôi cặp, tạo chuỗi chứa surrogate lẻ. Khi xử lý văn bản đa ngôn ngữ, phải xác định chỉ số theo đơn vị mã UTF-16 và tránh cắt giữa cặp surrogate. Nếu ứng dụng cần chọn theo **code point**, hãy dùng các API như `codePointCount()`, `offsetByCodePoints()` hoặc thao tác trên `codePoints()` rồi mới xác định đoạn cần lấy. Cắt đúng ranh giới code point cũng chưa bảo đảm giữ nguyên mọi cụm ký tự hiển thị, ví dụ ký tự kèm dấu kết hợp.

**Phân biệt `char[]` với `byte[]`:** constructor nhận `char[]` không giải mã encoding. Với dữ liệu là byte, phải dùng cơ chế giải mã và chỉ định charset phù hợp, chẳng hạn `new String(bytes, StandardCharsets.UTF_8)`. Không ép byte thành char một cách tùy tiện để thay cho decoding.

## 6. Cách viết tương đương và lựa chọn sử dụng

Các lời gọi sau tạo chuỗi có cùng **giá trị nội dung** khi đầu vào hợp lệ:

~~~java
char[] data = {'H', 'e', 'l', 'l', 'o'};
String a = new String(data);
String b = String.valueOf(data);
String c = String.copyValueOf(data);

String partA = new String(data, 1, 3);
String partB = String.valueOf(data, 1, 3);
String partC = String.copyValueOf(data, 1, 3);
~~~

Kết quả: `a`, `b`, `c` cùng có giá trị `"Hello"`; `partA`, `partB`, `partC` cùng có giá trị `"ell"`. Những lời gọi trong nhóm tạo chuỗi từ `char[]` đều lấy nội dung tại thời điểm gọi. Có thể sử dụng `new String(chars, offset, count)` khi muốn diễn đạt rõ việc tạo một phần mảng; `String.valueOf(...)` cũng là lựa chọn quen thuộc.

Không nhầm với:

- `Arrays.toString(data)`: tạo dạng hiển thị phần tử mảng như `"[H, e, l, l, o]"`, **khác** `"Hello"`.
- `new String(data, 1, 4)`: lấy **bốn** ký tự từ chỉ số 1, tức `"ello"`; không phải lấy từ chỉ số 1 **đến** chỉ số 4 theo nghĩa điểm cuối.
- `new String(data, 0, data.length + 1)`: vượt vùng hợp lệ và gây ngoại lệ.
- `StringBuilder`: hữu ích khi cần nối/thay đổi ký tự liên tục trước khi tạo kết quả `String`; nó không bắt buộc cho việc sao chép một đoạn `char[]` đã có sẵn.

## 7. Tình huống thực tế: chỉ lấy phần dữ liệu đã đọc

Khi đọc vào bộ đệm `char[]` từ một `Reader`, số ký tự thực sự được đọc thường nhỏ hơn kích thước toàn bộ bộ đệm. Với đoạn mã **đặt trong phương thức có quyền ném `IOException`** và biến `reader` là một `java.io.Reader` đang mở:

~~~java
char[] buffer = new char[1024];
int count = reader.read(buffer);
if (count != -1) {
    String chunk = new String(buffer, 0, count);
    System.out.println("Read " + chunk.length() + " UTF-16 code units");
}
~~~

`reader.read(buffer)` trả về số đơn vị `char` đã được ghi vào buffer hoặc `-1` nếu hết dữ liệu; dùng `count` để chỉ lấy phần có dữ liệu. Nếu dùng `new String(buffer)`, kết quả có thể chứa các phần tử chưa được đọc hoặc còn sót từ lần đọc trước. Nếu muốn ghép toàn bộ nội dung đọc qua nhiều lần, hãy tích lũy bằng `StringBuilder` hoặc cơ chế xử lý dòng/stream phù hợp. Với dữ liệu Unicode, cần chú ý ranh giới surrogate khi xử lý độc lập từng chunk.

## 8. Tự kiểm tra

**Câu 1.** Cho `char[] a = {'0', '1', '2', '3', '4'};`, `new String(a, 2, 2)` trả về gì?

Đáp án: `"23"`, vì lấy các phần tử chỉ số 2 và 3.

**Câu 2.** Mảng có độ dài 5. `new String(a, 5, 0)` và `new String(a, 5, 1)` khác nhau thế nào?

Đáp án: lời gọi đầu hợp lệ và tạo chuỗi rỗng; lời gọi sau yêu cầu phần tử ở chỉ số 5 (không tồn tại), nên gây `IndexOutOfBoundsException`.

**Câu 3.** Vì sao tạo `String` rồi sửa `char[]` không thay đổi giá trị `String`?

Đáp án: constructor đã sao chép nội dung mảng, còn `String` là kiểu bất biến.

**Câu 4.** `count = 1` có luôn nghĩa là một ký tự Unicode hoàn chỉnh?

Đáp án: không. `count` tính đơn vị mã UTF-16; một code point bổ sung có thể cần hai `char`, và một cụm ký tự hiển thị có thể gồm nhiều code point.

## Tài liệu tham khảo

- [Java SE 17 — String constructors](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html): mô tả chính thức về sao chép mảng, `offset`, `count` và ngoại lệ.
- [Java SE 17 — Character](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html): cách Java biểu diễn Unicode code point và surrogate pair.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-creation-005">Tạo String từ mảng code point</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo `String` từ mảng code point trong Java

## 1. Mục tiêu

Khi dữ liệu văn bản được biểu diễn dưới dạng các giá trị Unicode code point (`int[]`), Java cho phép tạo trực tiếp một `String` bằng constructor `String(int[] codePoints, int offset, int count)`. Cách này đặc biệt hữu ích khi xử lý ký tự ngoài Basic Multilingual Plane (BMP), ví dụ nhiều emoji, mà không cần tự ghép các cặp `char` của UTF-16.

Sau bài này, bạn có thể:
- Phân biệt **Unicode code point**, **UTF-16 code unit (`char`)** và độ dài `String`.
- Tạo chuỗi từ toàn bộ hoặc một đoạn của `int[]`.
- Hiểu ý nghĩa và giới hạn của `offset` / `count`, các lỗi có thể xảy ra và cách kiểm tra đầu vào.
- Chuyển đổi hai chiều giữa `String` và mảng code point khi cần.

## 2. Code point khác `char` như thế nào?

Một **Unicode code point** là giá trị nguyên dùng để định danh một vị trí mã Unicode, thường viết dạng `U+XXXX` hoặc `U+XXXXX`, ví dụ:

| Ký tự | Code point | Giá trị `int` trong Java | Số `char` UTF-16 |
| --- | --- | --- | --- |
| `A` | `U+0041` | `0x0041` | 1 |
| `ế` (dạng dựng sẵn) | `U+1EBF` | `0x1EBF` | 1 |
| `🚀` | `U+1F680` | `0x1F680` | 2 |
| `😀` | `U+1F600` | `0x1F600` | 2 |

Java sử dụng **UTF-16** cho các thao tác theo chỉ số `char` của `String`. Một `char` là một *code unit* 16 bit:
- Code point thuộc BMP (từ `U+0000` đến `U+FFFF`) thường chiếm một code unit. Riêng vùng surrogate `U+D800..U+DFFF` không phải ký tự Unicode độc lập.
- Code point bổ sung (từ `U+10000` đến `U+10FFFF`) cần **hai** code unit, gọi là *surrogate pair*.
- Vì vậy `String.length()` đếm `char` UTF-16, **không phải** số code point và cũng không phải số ký tự người dùng cảm nhận. Một cụm chữ có dấu kết hợp hoặc emoji ghép có thể gồm nhiều code point.

Ví dụ mảng `{0x0041, 0x1F680, 0x0042}` chứa **3 code point** tạo thành `A🚀B`, nhưng `"A🚀B".length()` bằng **4**.

## 3. Constructor `String(int[] codePoints, int offset, int count)`

Cú pháp:

```java
String text = new String(codePoints, offset, count);
```

| Tham số | Ý nghĩa |
| --- | --- |
| `codePoints` | Mảng `int[]` lưu các giá trị Unicode code point theo thứ tự mong muốn. |
| `offset` | Chỉ số **phần tử đầu tiên** được lấy trong `codePoints` (đếm từ 0). |
| `count` | **Số phần tử `int`** cần lấy, không phải chỉ số cuối và không phải số `char` UTF-16. |

Constructor chuyển từng phần tử của đoạn `codePoints[offset .. offset + count)` sang biểu diễn chuỗi. Với code point bổ sung như `0x1F680`, Java tự sinh surrogate pair tương ứng.

Để dùng cả mảng:

```java
int[] points = {0x0048, 0x0069, 0x0021};
String greeting = new String(points, 0, points.length); // "Hi!"
```

Không có constructor `new String(int[])`; phải truyền đủ ba đối số. Điều kiện về chỉ số là `offset >= 0`, `count >= 0` và `offset + count <= codePoints.length`. Với `count == 0` và vị trí hợp lệ, kết quả là chuỗi rỗng. Mảng nguồn được chuyển thành nội dung chuỗi tại thời điểm tạo; thay đổi `int[]` sau đó không làm đổi `String`.

## 4. Ví dụ Java 17+ chạy độc lập

Lưu đoạn sau vào file `CodePointStringDemo.java`:

```java
public class CodePointStringDemo {
    public static void main(String[] args) {
        // H, i, khoảng trắng, rocket, dấu chấm than.
        int[] points = {0x0048, 0x0069, 0x0020, 0x1F680, 0x0021};
        String text = new String(points, 0, points.length);

        System.out.println(text);
        System.out.println("length() = " + text.length());
        System.out.println("codePointCount() = "
                + text.codePointCount(0, text.length()));
        System.out.printf("codePointAt(3) = U+%X%n", text.codePointAt(3));

        // Bỏ hai dấu sao đầu/cuối; chọn 5 code point từ vị trí 1.
        int[] padded = {0x2605, 0x004A, 0x0061, 0x0076, 0x0061, 0x1F680, 0x2605};
        String selected = new String(padded, 1, 5);
        System.out.println("selected = " + selected);

        padded[1] = 0x0058; // Đổi 'J' trong mảng thành 'X'.
        System.out.println("after array change = " + selected);

        System.out.println("empty = [" + new String(points, 0, 0) + "]");
    }
}
```

Biên dịch và chạy (terminal hỗ trợ Unicode):

```bash
javac -encoding UTF-8 CodePointStringDemo.java
java CodePointStringDemo
```

Kết quả chuẩn:

```text
Hi 🚀!
length() = 6
codePointCount() = 5
codePointAt(3) = U+1F680
selected = Java🚀
after array change = Java🚀
empty = []
```

**Giải thích:**
1. `points` có 5 phần tử, nhưng `🚀` chiếm 2 code unit UTF-16: `text.length() == 6` trong khi `text.codePointCount(0, text.length()) == 5`.
2. `codePointAt(3)` nhận **chỉ số `char`**, tại vị trí 3 bắt đầu surrogate pair của `🚀`, nên trả về `0x1F680`. Không nhầm chỉ số của `String` với chỉ số trong `int[]`.
3. `new String(padded, 1, 5)` lấy 5 code point ở các chỉ số 1, 2, 3, 4, 5, thu được `Java🚀`; dấu `★` ở đầu/cuối bị loại.
4. Kết quả không đổi sau khi sửa mảng vì `String` là bất biến và constructor không giữ mảng `int[]` làm dữ liệu sống.

## 5. Xử lý code point và chỉ số đúng cách

Một vài API liên quan:

| API | Tác dụng |
| --- | --- |
| `text.length()` | Số code unit UTF-16. |
| `text.codePointCount(0, text.length())` | Số code point trong chuỗi (surrogate đơn lẻ cũng được tính riêng). |
| `text.codePointAt(charIndex)` | Lấy code point bắt đầu tại một chỉ số UTF-16. |
| `text.offsetByCodePoints(charIndex, delta)` | Di chuyển theo số code point và trả về chỉ số UTF-16 mới. |
| `text.codePoints().toArray()` | Lấy `int[]` code point từ `String`. |
| `Character.toChars(codePoint)` | Chuyển một code point thành một hoặc hai `char`. |
| `StringBuilder.appendCodePoint(codePoint)` | Nối từng code point khi xây dựng chuỗi dần dần. |

Ví dụ chuyển đổi hai chiều:

```java
String original = "A🚀B";
int[] cps = original.codePoints().toArray();
String copy = new String(cps, 0, cps.length);

System.out.println(java.util.Arrays.toString(cps));
System.out.println(copy.equals(original));
System.out.println(copy.length() + " / " + cps.length);
```

Kết quả của đoạn minh họa khi chạy bên trong một phương thức (ví dụ `main`):

```text
[65, 128640, 66]
true
4 / 3
```

`String.codePoints()` cung cấp `IntStream`. Không sử dụng vòng lặp `for (char c : text.toCharArray())` nếu yêu cầu xử lý từng code point: vòng lặp đó có thể tách một ký tự bổ sung thành hai surrogate code unit.

Nếu cần cắt theo **số code point** của một chuỗi đang có, hãy chuyển vị trí đầu/cuối bằng `offsetByCodePoints` rồi dùng `substring`; các đối số của `substring` vẫn là chỉ số UTF-16. Còn `offset` và `count` trong constructor nhận `int[]` thì trực tiếp đếm phần tử của mảng.

## 6. Kiểm tra dữ liệu và lỗi thường gặp

Các tình huống với constructor:

| Đầu vào | Hành vi |
| --- | --- |
| `new String(new int[]{0x41}, 0, 1)` | `"A"`. |
| `new String(new int[]{0x41}, 1, 0)` | Chuỗi rỗng; `offset == length` được phép khi `count == 0`. |
| `new String(new int[]{0x41}, 0, 2)` | `IndexOutOfBoundsException`: vượt phạm vi mảng. |
| `new String(new int[]{0x41}, -1, 1)` | `IndexOutOfBoundsException`: `offset` âm. |
| `new String(new int[]{0x110000}, 0, 1)` | `IllegalArgumentException`: vượt code point tối đa `U+10FFFF`. |
| `new String((int[]) null, 0, 0)` | `NullPointerException` khi mảng code point là `null`. |

Lời gọi `new String(null, 0, 0)` **không biên dịch** vì mơ hồ giữa các overload nhận `int[]`, `char[]` và `byte[]`; ép kiểu `(int[]) null` mới chọn constructor đang xét.

**Phân biệt code point hợp lệ theo Java với Unicode scalar value:** `Character.isValidCodePoint(cp)` kiểm tra `0 <= cp <= 0x10FFFF`, nên **cả giá trị surrogate** `0xD800..0xDFFF` cũng vượt qua phép kiểm tra phạm vi này. Một `String` Java có thể chứa surrogate đơn lẻ, nhưng đó không phải văn bản Unicode well-formed. Nếu đầu vào cần biểu diễn văn bản chuẩn để trao đổi/ghi ra ngoài, nên từ chối surrogate code point riêng lẻ.

Hàm tiện ích sau có thể dùng khi dữ liệu `int[]` đến từ nguồn không tin cậy và bạn muốn chỉ nhận Unicode scalar value. Đây là phương thức hỗ trợ, đặt bên trong một lớp Java:

```java
static String fromUnicodeScalars(int[] points, int offset, int count) {
    java.util.Objects.requireNonNull(points, "points");
    java.util.Objects.checkFromIndexSize(offset, count, points.length);

    for (int i = offset; i < offset + count; i++) {
        int cp = points[i];
        boolean surrogate = cp >= 0xD800 && cp <= 0xDFFF;
        if (!Character.isValidCodePoint(cp) || surrogate) {
            throw new IllegalArgumentException(
                    String.format("Invalid Unicode scalar: U+%X", cp));
        }
    }
    return new String(points, offset, count);
}
```

`Objects.checkFromIndexSize` kiểm tra đoạn chọn (Java 9+). Constructor cơ bản đã tự kiểm tra code point vượt phạm vi; phần kiểm tra bổ sung ở đây dành cho yêu cầu **không chấp nhận surrogate lẻ**.

Đừng lẫn `int[]` code point với `byte[]` UTF-8: nếu dữ liệu là byte được mã hóa, cần giải mã bằng charset rõ ràng, chẳng hạn `new String(bytes, java.nio.charset.StandardCharsets.UTF_8)`. Cũng đừng chuyển mọi code point sang `(char)`: phép ép kiểu sẽ cắt mất phần cao của giá trị lớn hơn `0xFFFF`.

## 7. Chọn giải pháp theo tình huống

- **Đã có `int[]` code point:** dùng `new String(points, offset, count)` trực tiếp; không cần tự tạo surrogate pair.
- **Có từng code point được sinh liên tục:** dùng `StringBuilder.appendCodePoint(cp)`, sau đó `toString()`, giúp tránh nối `String` nhiều lần trong vòng lặp.
- **Đang có một `String` và muốn thao tác từng code point:** dùng `codePoints()`, `codePointAt`, `codePointCount` và `offsetByCodePoints` khi phù hợp.
- **Đang có `char[]`:** `new String(chars)` nhận code unit UTF-16, khác ngữ nghĩa so với mảng `int[]` code point.
- **Dữ liệu là byte UTF-8/UTF-16:** giải mã bằng API charset, không coi mỗi byte là một code point.

Các lỗi tư duy phổ biến là cho rằng `count` là chỉ số cuối, tin rằng một code point luôn chiếm một `char`, dùng `length()` để đếm code point, hoặc giả định một code point luôn tương ứng một *ký tự hiển thị* (grapheme cluster). Ví dụ chữ `e` theo sau bởi dấu sắc kết hợp có thể là hai code point nhưng trông như một chữ; các emoji ghép cũng có thể chứa nhiều code point.

## 8. Tự kiểm tra kiến thức

Cho mảng:

```java
int[] cps = {0x0058, 0x0041, 0x1F600, 0x0042, 0x0059};
String s = new String(cps, 1, 3);
```

1. `s` bằng gì? **`A😀B`**, vì lấy các phần tử chỉ số 1, 2, 3.
2. `s.length()` bằng mấy? **4** code unit UTF-16, vì `😀` chiếm hai `char`.
3. `s.codePointCount(0, s.length())` bằng mấy? **3** code point.
4. Nếu sửa `cps[1] = 0x005A` sau đó, `s` có đổi không? **Không**, vì `String` đã được tạo độc lập với mảng nguồn.
5. `new String(cps, 3, 3)` có hợp lệ không? **Không**, vì cần ba phần tử từ chỉ số 3 trong mảng dài 5.

## Tài liệu tham khảo

- [Java SE 17 — `String(int[] codePoints, int offset, int count)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html#%3Cinit%3E(int%5B%5D,int,int))
- [Java SE 17 — `Character`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-creation-006">Tạo String từ byte&#91;&#93; với bộ mã ký tự</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo `String` từ `byte[]` với bộ mã ký tự trong Java

Khi nhận dữ liệu từ tệp, mạng, cơ sở dữ liệu hoặc một API, ta thường có một mảng byte (`byte[]`). Để biến dữ liệu đó thành văn bản, Java phải **giải mã các byte theo đúng bộ mã ký tự (charset)** mà bên tạo dữ liệu đã sử dụng. Cách thông dụng, rõ ràng và an toàn về tính nhất quán là `new String(bytes, StandardCharsets.UTF_8)`.

## 1. Phân biệt byte, ký tự và charset

- **`byte[]`** là dãy giá trị 8 bit. Bản thân mảng byte không nói đó là văn bản UTF-8, ảnh, tệp ZIP hay dữ liệu nhị phân khác.
- **Charset** quy định cách ánh xạ giữa chuỗi ký tự Unicode và byte khi *mã hóa* (encode), cũng như cách khôi phục văn bản từ byte khi *giải mã* (decode).
- **`String`** lưu văn bản theo mô hình Unicode của Java. Việc tạo `String` từ byte cần biết charset; không thể suy luận chính xác charset bất kỳ chỉ từ một `byte[]`.
- **UTF-8** là bộ mã phổ biến trên web, API, JSON và nhiều định dạng tệp. Một ký tự có thể chiếm từ 1 đến 4 byte trong UTF-8.

Luồng xử lý:

```text
String -- getBytes(charset) --> byte[]
String <-- new String(bytes, charset) -- byte[]
```

Hai chiều chỉ khôi phục nguyên vẹn khi dùng charset thích hợp và dữ liệu được mã hóa, truyền, giải mã hợp lệ. Không nên nhầm số **byte** với số **ký tự**: chẳng hạn `"é"` (U+00E9) trong UTF-8 chiếm 2 byte nhưng `String.length()` là 1; một emoji ngoài BMP thường chiếm 4 byte UTF-8 và 2 UTF-16 code unit trong `String.length()`.

## 2. API cần dùng

| Cách gọi | Ý nghĩa | Khi nào dùng |
| --- | --- | --- |
| `new String(bytes, StandardCharsets.UTF_8)` | Giải mã toàn bộ mảng bằng UTF-8 | Lựa chọn mặc định khi **đặc tả dữ liệu** xác định UTF-8 |
| `new String(bytes, offset, length, charset)` | Chỉ giải mã `length` byte bắt đầu tại chỉ số `offset` | Khi gói dữ liệu có header hoặc chỉ cần một đoạn byte hoàn chỉnh |
| `new String(bytes, Charset.forName("..."))` | Dùng tên charset được cấu hình hoặc quy định bởi giao thức | Khi giao thức bắt buộc charset khác các hằng thông dụng |
| `new String(bytes)` | Giải mã theo charset mặc định của môi trường Java | Chỉ khi giao thức thật sự quy định sử dụng default charset |
| `text.getBytes(charset)` | Mã hóa `String` trở lại `byte[]` | Khi cần ghi hoặc gửi văn bản ra ngoài |

Import thường dùng:

```java
import java.nio.charset.StandardCharsets;
```

`StandardCharsets.UTF_8`, `US_ASCII`, `ISO_8859_1`, `UTF_16` và `UTF_16LE`/`UTF_16BE` là các charset chuẩn sẵn có. Dùng hằng `Charset` giúp tránh lỗi đánh vần tên và làm hợp đồng mã hóa hiển nhiên hơn so với chuỗi `"UTF-8"`. Constructor `new String(byte[], String charsetName)` cũng tồn tại nhưng có thể báo `UnsupportedEncodingException`; trong ứng dụng hiện đại thường ưu tiên overload nhận `Charset`.

**Điểm mấu chốt:** Đừng chọn UTF-8 chỉ vì nó phổ biến nếu nguồn dữ liệu được khai báo là ISO-8859-1, UTF-16LE hay charset khác; phải làm theo định dạng tệp, header giao thức hoặc hợp đồng API.

## 3. Ví dụ đầy đủ: mã hóa rồi giải mã UTF-8

Lưu tệp sau dưới tên `DecodeBytesDemo.java` với UTF-8:

```java
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class DecodeBytesDemo {
    public static void main(String[] args) {
        String original = "Tiếng Việt";
        byte[] payload = original.getBytes(StandardCharsets.UTF_8);

        String decoded = new String(payload, StandardCharsets.UTF_8);
        System.out.println(decoded);
        System.out.println(Arrays.equals(
                payload, decoded.getBytes(StandardCharsets.UTF_8)));

        byte[] packet = {88, 72, 105, 89}; // XHiY trong ASCII
        String middle = new String(
                packet, 1, 2, StandardCharsets.US_ASCII);
        System.out.println(middle);
    }
}
```

Biên dịch và chạy bằng Java 17 trở lên:

```bash
javac -encoding UTF-8 DecodeBytesDemo.java
java DecodeBytesDemo
```

Kết quả:

```text
Tiếng Việt
true
Hi
```

Giải thích:

1. `getBytes(StandardCharsets.UTF_8)` tạo các byte UTF-8 từ văn bản gốc.
2. `new String(payload, StandardCharsets.UTF_8)` diễn giải đúng các byte và khôi phục `"Tiếng Việt"`.
3. `Arrays.equals(...)` so sánh nội dung mảng byte, xác nhận chuyến khứ hồi của dữ liệu hợp lệ ở ví dụ này (không thể dùng `payload.equals(...)` để so sánh nội dung hai mảng).
4. Ở overload có `offset`/`length`, `offset = 1` bắt đầu từ byte thứ hai (`H`), `length = 2` đọc đúng hai byte (`H`, `i`). `length` tính bằng **byte**, không phải số ký tự.

Điều kiện biên của đoạn mảng: `offset >= 0`, `length >= 0` và đoạn `[offset, offset + length)` phải nằm trong mảng; nếu không, Java ném `IndexOutOfBoundsException`. Nếu cắt giữa chừng một chuỗi byte của một ký tự UTF-8 nhiều byte, phần giải mã có thể trở thành dữ liệu lỗi và bị thay thế ký tự.

## 4. Chọn sai charset: lỗi chữ bị biến dạng (mojibake)

Các byte được mã hóa bằng UTF-8 cần được giải mã bằng UTF-8. Cùng một byte có thể được hiểu khác hẳn bởi charset khác:

```java
import java.nio.charset.StandardCharsets;

public class WrongCharsetDemo {
    public static void main(String[] args) {
        byte[] utf8Bytes = "Café".getBytes(StandardCharsets.UTF_8);

        System.out.println(new String(
                utf8Bytes, StandardCharsets.UTF_8));
        System.out.println(new String(
                utf8Bytes, StandardCharsets.ISO_8859_1));
    }
}
```

Kết quả:

```text
Café
CafÃ©
```

`"é"` được biểu diễn bởi hai byte UTF-8 `C3 A9`. Khi hai byte ấy bị đọc như hai ký tự ISO-8859-1 độc lập, kết quả thành `"Ã©"`. Đây là lỗi **giải mã sai charset**, khác với lỗi font hiển thị. Không có phép gọi `new String(...)` nào tự nhận diện đáng tin cậy tất cả các charset; cần tra nguồn gốc hoặc quy định của dữ liệu.

Trong hệ thống thực tế, hãy kiểm tra `Content-Type` và tham số `charset` của giao thức (nếu có), cấu hình định dạng tệp, đặc tả đối tác và bước mã hóa ở phía tạo byte. Không tự giải mã thử nhiều charset rồi coi chuỗi “trông có vẻ đúng” là bằng chứng chắc chắn.

## 5. Byte UTF-8 không hợp lệ: thay thế hay báo lỗi?

Một mảng byte có thể chứa chuỗi UTF-8 không hợp lệ, ví dụ `C3 28`: byte `C3` mở đầu một chuỗi hai byte nhưng `28` không phải byte tiếp nối hợp lệ.

Với constructor `new String(bytes, charset)`, Java **thay thế** chuỗi byte sai định dạng hoặc không ánh xạ được bằng ký tự thay thế của bộ giải mã; với UTF-8 thường là `�` (U+FFFD). Cách này tiện để hiển thị văn bản có lỗi nhưng có thể che mất việc dữ liệu đã hỏng.

Nếu bài toán yêu cầu phát hiện và từ chối byte lỗi (nhập dữ liệu quan trọng, kiểm tra tính toàn vẹn, đọc giao thức nghiêm ngặt), dùng `CharsetDecoder` với `CodingErrorAction.REPORT`:

```java
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public class StrictDecodeDemo {
    public static void main(String[] args) {
        byte[] invalidUtf8 = {(byte) 0xC3, (byte) 0x28};

        String lenient = new String(
                invalidUtf8, StandardCharsets.UTF_8);
        System.out.println(lenient.equals("\uFFFD("));

        CharsetDecoder strict = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            strict.decode(ByteBuffer.wrap(invalidUtf8));
            System.out.println("Dữ liệu hợp lệ");
        } catch (CharacterCodingException e) {
            System.out.println(e.getClass().getSimpleName());
        }
    }
}
```

Chạy `javac -encoding UTF-8 StrictDecodeDemo.java` rồi `java StrictDecodeDemo`, nhận:

```text
true
MalformedInputException
```

`lenient` chứa U+FFFD rồi đến `(` nên phép so sánh trả về `true`. Trái lại, decoder cấu hình `REPORT` ném `MalformedInputException` (lớp con của `CharacterCodingException`). `onUnmappableCharacter(REPORT)` còn xử lý trường hợp byte hợp lệ về mặt cấu trúc nhưng không ánh xạ được trong charset cụ thể. Nếu cần chính sách khác, `CodingErrorAction.REPLACE` chủ động thay thế và `IGNORE` bỏ qua dữ liệu lỗi; việc bỏ qua có nguy cơ mất thông tin.

## 6. Các bẫy thường gặp và cách xử lý

- **Quên chỉ định charset:** `new String(bytes)` phụ thuộc default charset của runtime. Từ Java 18, UTF-8 là mặc định theo JEP 400 trong phần lớn cấu hình, nhưng các phiên bản cũ và tùy chọn runtime có thể khác. Với dữ liệu trao đổi giữa hệ thống, hãy chỉ định charset tường minh.
- **Nhầm dữ liệu nhị phân với văn bản:** ảnh, nội dung nén, dữ liệu mã hóa và byte ngẫu nhiên không nên tùy tiện đưa qua `new String(...)` rồi `getBytes(...)`; phép biến đổi có thể làm thay đổi byte. Dùng `byte[]`/stream để giữ nguyên binary; Base64 chỉ khi cần biểu diễn nhị phân dưới dạng ký tự.
- **Cắt một ký tự nhiều byte:** `new String(bytes, offset, length, UTF_8)` chỉ đúng khi đoạn được chọn là dãy byte mã hóa hoàn chỉnh. Đừng chia các chunk mạng rồi giải mã riêng nếu ranh giới chunk có thể nằm giữa ký tự UTF-8.
- **Giải mã dữ liệu lớn một lần:** với luồng văn bản, dùng `InputStreamReader(input, StandardCharsets.UTF_8)` bọc `BufferedReader` khi phù hợp. Reader xử lý giải mã xuyên ranh giới các lần đọc; với xử lý `ByteBuffer` từng phần, dùng `CharsetDecoder` duy trì trạng thái giữa các chunk.
- **So sánh độ dài sai:** `bytes.length`, `String.length()` và `codePointCount(...)` đo ba thứ khác nhau: byte, đơn vị mã UTF-16 và Unicode code point. Không dùng `String.length()` để tính byte length của giao thức.
- **Tái mã hóa dữ liệu hỏng:** một lần giải mã sai và chứa ký tự thay thế không thể tự khôi phục byte gốc. Sửa từ phía xác định charset/nguồn byte; lưu dữ liệu gốc khi cần chẩn đoán.
- **BOM/tiền tố định dạng:** nếu nguồn có byte order mark hoặc header, xác định đó là phần mã hóa cần xử lý hay một phần giao thức. Không tùy tiện cắt ba byte đầu mọi dữ liệu UTF-8; dữ liệu có thể không có BOM.

## 7. Quy trình áp dụng

1. **Xác minh đó là văn bản** và biết nơi sinh byte (file, HTTP response, database, message queue, v.v.).
2. **Đọc hợp đồng mã hóa**: dùng charset được giao thức/định dạng công bố; ưu tiên `StandardCharsets.UTF_8` khi nguồn thực sự dùng UTF-8.
3. **Lấy đủ dãy byte của đoạn văn bản**, tránh cắt giữa các ký tự mã hóa nhiều byte.
4. **Giải mã:** trường hợp thông thường dùng `new String(bytes, charset)`; khi dữ liệu lỗi phải bị phát hiện, dùng `CharsetDecoder` và `REPORT`.
5. **Kiểm thử với chữ có dấu, ký tự ngoài ASCII và dữ liệu sai:** không chỉ thử văn bản ASCII vì nhiều charset đều cho cùng kết quả ASCII.
6. **Khi ghi/truyền ngược**, sử dụng `getBytes(charset)` cùng charset đã được định nghĩa cho phía nhận; đừng ngầm dùng charset mặc định.

**Ghi nhớ:** `byte[]` chỉ là các byte; **charset là hợp đồng bắt buộc để hiểu chúng thành văn bản**. Đối với byte UTF-8 hợp lệ, `new String(bytes, StandardCharsets.UTF_8)` là cách trực tiếp; đối với dữ liệu không đáng tin cậy cần từ chối byte lỗi, dùng decoder với chính sách `REPORT`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-creation-007">Tạo String từ StringBuilder hoặc StringBuffer</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo `String` từ `StringBuilder` hoặc `StringBuffer` trong Java

`StringBuilder` và `StringBuffer` dùng để xây dựng, nối hoặc chỉnh sửa chuỗi nhiều lần. Khi hoàn tất, chương trình thường cần một giá trị `String` để trả về từ phương thức, truyền cho API nhận `String`, lưu trong đối tượng dữ liệu hoặc hiển thị. Cách chuyển đổi phổ biến và rõ nghĩa nhất là gọi `toString()`.

## 1. Ba kiểu dữ liệu liên quan như thế nào?

- `String` là kiểu chuỗi **bất biến** (immutable): sau khi tạo, nội dung của một `String` không bị sửa tại chỗ. Những thao tác như `concat()` hoặc `replace()` tạo ra kết quả `String` khác khi cần.
- `StringBuilder` là vùng đệm ký tự **có thể thay đổi** (mutable). Các thao tác `append()`, `insert()`, `delete()`, `replace()` và `reverse()` thay đổi vùng đệm. Kiểu này không được thiết kế để nhiều luồng cùng thay đổi mà không có đồng bộ bên ngoài.
- `StringBuffer` cũng có thể thay đổi nội dung, nhưng nhiều phương thức công khai được đồng bộ hóa (`synchronized`). Nó hữu ích khi cần đặc tính đồng bộ hóa đó; tuy nhiên một chuỗi gồm nhiều lời gọi phương thức vẫn cần cơ chế bảo vệ phù hợp nếu phải thực hiện như một thao tác nguyên tử.

Cả hai lớp vùng đệm đều triển khai `CharSequence`, song `CharSequence` không phải là `String`. Một biến hoặc API yêu cầu đúng kiểu `String` cần được cung cấp giá trị chuyển đổi.

## 2. Chuyển đổi bằng `toString()`

Cú pháp chuẩn:

```java
StringBuilder builder = new StringBuilder("Hello");
builder.append(" Java");
String text = builder.toString();

StringBuffer buffer = new StringBuffer("Hello");
buffer.append(" Java");
String otherText = buffer.toString();
```

`toString()` trả về nội dung hiện tại dưới dạng `String`. Lời gọi này **không xóa, không đóng và không thay đổi** vùng đệm nguồn; có thể tiếp tục dùng `builder` hoặc `buffer` sau đó.

### Ví dụ chạy được: chụp nội dung `StringBuilder`

Lưu vào tệp `Main.java`:

```java
public class Main {
    public static void main(String[] args) {
        StringBuilder builder = new StringBuilder("Hello");
        builder.append(" Java");

        String before = builder.toString();
        builder.append("!");

        String after = builder.toString();

        System.out.println("before = " + before);
        System.out.println("after = " + after);
        System.out.println("builder = " + builder);
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac Main.java
java Main
```

Kết quả:

```text
before = Hello Java
after = Hello Java!
builder = Hello Java!
```

`before` là một **ảnh chụp nội dung tại thời điểm chuyển đổi**. Khi `builder` được nối thêm dấu `!`, `before` vẫn giữ giá trị cũ; một lần gọi `toString()` tiếp theo lấy nội dung mới. Đây là khác biệt quan trọng giữa giá trị `String` và vùng đệm có thể thay đổi.

### Ví dụ chạy được: `StringBuffer`

Lưu vào tệp `BufferExample.java`:

```java
public class BufferExample {
    public static void main(String[] args) {
        StringBuffer buffer = new StringBuffer("A");
        buffer.append("B");

        String snapshot = buffer.toString();
        buffer.replace(0, 1, "X");

        System.out.println("snapshot = " + snapshot);
        System.out.println("buffer = " + buffer);
    }
}
```

Chạy bằng `javac BufferExample.java` rồi `java BufferExample`. Đầu ra:

```text
snapshot = AB
buffer = XB
```

Mặc dù `StringBuffer` có các phương thức đồng bộ, kết quả trả về vẫn là một `String` bất biến, không liên kết sống với nội dung của `buffer`.

## 3. Các cách chuyển đổi khác

Java cũng cung cấp constructor nhận trực tiếp đối tượng vùng đệm:

```java
StringBuilder builder = new StringBuilder("Java");
String fromBuilder = new String(builder);

StringBuffer buffer = new StringBuffer("Core");
String fromBuffer = new String(buffer);
```

Hai constructor `String(StringBuilder)` và `String(StringBuffer)` tạo `String` từ nội dung hiện tại của đối tượng nguồn. Với mục đích chuyển đổi thông thường, `toString()` dễ đọc hơn và là lựa chọn mặc định.

Có thể dùng `String.valueOf(Object)`:

```java
StringBuilder builder = new StringBuilder("Java");
String text = String.valueOf(builder); // "Java"
```

Với tham chiếu **không null**, cách gọi trên sử dụng biểu diễn `toString()` của đối tượng. Điểm khác là `String.valueOf(Object)` với tham chiếu `null` trả về chuỗi ký tự `"null"`, còn gọi `toString()` trên tham chiếu `null` gây `NullPointerException`:

```java
StringBuilder builder = null;
String safeRepresentation = String.valueOf(builder); // "null"
// String text = builder.toString();                 // NullPointerException
```

Đừng nhầm chuỗi ký tự `"null"` với giá trị tham chiếu `null`. Nếu quy ước nghiệp vụ muốn chuỗi rỗng, cần xử lý rõ:

```java
StringBuilder builder = null;
String text = builder == null ? "" : builder.toString();
```

| Cách viết | Kết quả | Nên dùng khi |
| --- | --- | --- |
| `builder.toString()` / `buffer.toString()` | `String` chứa nội dung hiện tại | Chuyển đổi thông thường, nguồn được đảm bảo không `null` |
| `new String(builder)` / `new String(buffer)` | `String` từ nội dung vùng đệm | Cần biểu đạt rõ ý định tạo bằng constructor |
| `String.valueOf(builder)` | Chuỗi từ `toString()`; nguồn `null` thành `"null"` | Muốn chuyển đối tượng thành biểu diễn chuỗi và chấp nhận quy tắc `null` này |

Tránh `new String(builder.toString())`: phần bọc constructor thường không mang lại ý nghĩa bổ sung so với `builder.toString()` và có thể tạo thêm công việc không cần thiết.

## 4. Thời điểm chuyển đổi và tính bất biến

Quy trình điển hình:

1. Khởi tạo `StringBuilder` hoặc `StringBuffer`.
2. Thêm, chèn hoặc sửa nội dung bằng các thao tác của vùng đệm.
3. Gọi `toString()` **sau khi đã hoàn thành** nội dung cần dùng.
4. Lưu, truyền hoặc trả về `String` kết quả.

Nếu tiếp tục sửa vùng đệm sau bước 3, kết quả đã tạo không tự cập nhật. Muốn lấy phiên bản mới, phải gọi `toString()` lại.

Không so sánh nội dung hai kết quả bằng `==`. Toán tử này so sánh tham chiếu của hai đối tượng; dùng `equals()` để so sánh nội dung:

```java
StringBuilder builder = new StringBuilder("abc");
String a = builder.toString();
String b = new String(builder);

System.out.println(a.equals(b)); // true
```

Ví dụ trên cố ý chỉ kiểm tra nội dung. Không nên dựa vào việc hai lần chuyển đổi trả về cùng hay khác đối tượng theo toán tử `==`; điều cần bảo đảm trong ứng dụng là giá trị chuỗi.

Chuyển đổi thường cần tạo giá trị `String` tương ứng với lượng nội dung trong vùng đệm, nên không cần gọi `toString()` lặp lại sau **mỗi** lần `append()` nếu chỉ dùng kết quả cuối cùng. Có thể tái sử dụng một biến `String` khi cần nhiều chỗ dùng cùng một ảnh chụp.

## 5. Ví dụ thực tế: xây dựng một dòng báo cáo

Ta ghép nhiều phần bằng `StringBuilder`, rồi trả về `String` cho mã gọi:

```java
public class ReportExample {
    static String buildSummary(String[] names) {
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < names.length; i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append(names[i]);
        }

        return result.toString();
    }

    public static void main(String[] args) {
        String summary = buildSummary(new String[] {"Lan", "Minh", "An"});
        System.out.println(summary);
    }
}
```

Lưu vào `ReportExample.java`, chạy `javac ReportExample.java` và `java ReportExample`. Đầu ra:

```text
Lan, Minh, An
```

Trong `buildSummary()`, vùng đệm là chi tiết triển khai nội bộ; phương thức trả về kiểu `String` bất biến theo hợp đồng đã khai báo. Lời gọi `toString()` cuối phương thức giúp tránh lấy bản trung gian khi vòng lặp chưa hoàn tất. Với nhu cầu nối danh sách đơn giản như ví dụ, `String.join(", ", names)` cũng là một lựa chọn rõ ràng; không cần tự dùng `StringBuilder` trong mọi trường hợp.

Nếu cần xây dựng nội dung trong mã dùng `StringBuffer`, bước trả về cũng tương tự:

```java
static String finish(StringBuffer buffer) {
    return buffer.toString();
}
```

Đoạn `finish` là một **phương thức minh họa**, cần đặt bên trong lớp Java phù hợp; nó không phải chương trình chạy độc lập.

## 6. Lưu ý khi có nhiều luồng

Với `StringBuilder`, không đọc/sửa cùng một đối tượng từ nhiều luồng mà thiếu đồng bộ phù hợp. Việc gọi `toString()` trong khi luồng khác đang thay đổi `StringBuilder` không tự tạo ra một ảnh chụp an toàn và nhất quán.

Với `StringBuffer`, các phương thức như `append()` và `toString()` được đồng bộ trên đối tượng. Một lần `toString()` có thể lấy nội dung nhất quán theo sự đồng bộ đó, nhưng **cả chuỗi hành động nhiều bước** (ví dụ: kiểm tra độ dài, sau đó thêm dữ liệu, rồi lấy chuỗi) không mặc nhiên trở thành một giao dịch nguyên tử giữa các luồng. Nếu phải giữ các bước đi cùng nhau, cần thiết kế đồng bộ cho toàn bộ thao tác.

Không chọn `StringBuffer` chỉ vì kết quả phải là `String`: cả hai lớp đều chuyển đổi được. Trong đoạn mã chỉ dùng một luồng, `StringBuilder` thường là lựa chọn thích hợp.

## 7. Lỗi thường gặp và cách xử lý

**1. Nhầm kết quả với vùng đệm đang thay đổi.** Sau `String snapshot = builder.toString();`, sửa `builder` không sửa `snapshot`. Hãy gọi `toString()` mới nếu muốn nội dung cập nhật.

**2. Gọi `toString()` trên đối tượng `null`.** Lỗi `NullPointerException` xuất hiện trước khi tạo `String`. Kiểm tra `null` hoặc dùng quy ước `String.valueOf()` khi thực sự muốn giá trị `"null"`.

**3. Gọi chuyển đổi quá sớm hoặc quá nhiều.** Nếu API cần bản hoàn chỉnh, nối/sửa xong mới gọi. Khi cần snapshot tại nhiều mốc có chủ đích, mỗi lần gọi đều hợp lý.

**4. Dùng `==` để kiểm tra kết quả.** Dùng `equals()` cho so sánh nội dung, `equalsIgnoreCase()` chỉ khi muốn so sánh bỏ qua hoa/thường.

**5. Tưởng `StringBuffer` khiến mọi chuỗi thao tác nhiều bước tự an toàn.** Đồng bộ từng phương thức không thay thế được thiết kế đồng bộ của cả quy trình.

**6. Tạo `new String(builder.toString())` như một bước bắt buộc.** Không cần bọc thêm `new String(...)` để nhận một `String`; `toString()` đã trả về đúng kiểu.

## 8. Tự kiểm tra

**Câu 1.** Nếu `StringBuilder b = new StringBuilder("A"); String s = b.toString(); b.append("B");`, giá trị của `s` và `b.toString()` là gì?

**Đáp án:** `s` vẫn là `"A"`, còn `b.toString()` là `"AB"`, vì `s` là giá trị bất biến được tạo trước khi nối thêm.

**Câu 2.** Có cần dùng `new String(buffer)` thì mới chuyển được `StringBuffer` sang `String` không?

**Đáp án:** Không. `buffer.toString()` là cách trực tiếp và thường được ưu tiên; constructor `new String(buffer)` cũng hợp lệ.

**Câu 3.** Vì sao có thể chọn `StringBuffer` trong mã nhiều luồng, và cần lưu ý gì?

**Đáp án:** Các phương thức của `StringBuffer` có đồng bộ hóa, bao gồm `toString()`. Tuy nhiên tính nguyên tử của cả một quy trình gồm nhiều lần gọi phương thức vẫn cần được kiểm soát riêng.

**Ghi nhớ:** Dùng `toString()` để nhận `String` từ `StringBuilder` hoặc `StringBuffer`. Giá trị tạo ra phản ánh nội dung tại thời điểm chuyển đổi và không thay đổi theo các lần sửa vùng đệm về sau.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-creation-008">new String và vì sao thường không cần sao chép một String đã có</a>

<details>
<summary>Xem chi tiết</summary>

# `new String` và vì sao thường không cần sao chép một `String` đã có

Trong Java, khi đã có một giá trị `String`, ta thường chỉ cần dùng lại tham chiếu tới nó. Viết `new String(existing)` tạo **đối tượng `String` khác nhưng có cùng nội dung** với `existing`. Vì `String` bất biến, việc tạo đối tượng mới này thường không giúp dữ liệu an toàn hơn và có thể gây chi phí cấp phát không cần thiết.

Bài học phân biệt rõ **biến tham chiếu**, **danh tính đối tượng (identity)**, **giá trị nội dung**, và các trường hợp constructor `String` thật sự hữu ích.

## 1. Gán tham chiếu khác với gọi `new String(...)`

```java
String original = "Java";
String alias = original;
String copy = new String(original);
```

- `original` tham chiếu đến một đối tượng `String` mang nội dung `"Java"`. Chuỗi literal được quản lý trong *string pool*.
- `alias = original` sao chép **giá trị tham chiếu**. `original` và `alias` cùng trỏ đến một đối tượng; không tạo thêm `String`.
- `copy = new String(original)` gọi constructor `String(String original)`. Nó tạo một đối tượng mới, có **nội dung bằng** `original` nhưng **danh tính khác**.

```text
original ──┐
           ├────────> đối tượng A: String "Java"
alias ─────┘

copy ────────────────> đối tượng B: String "Java"
```

Lưu ý kỹ: «đối tượng mới» không đồng nghĩa với «chắc chắn sao chép toàn bộ mảng byte lưu trữ nội bộ». Java bảo đảm ngữ nghĩa tạo một đối tượng `String` mới; cách JVM lưu hay chia sẻ dữ liệu nội bộ là chi tiết triển khai, không phải cam kết của API.

### Ví dụ chạy được trên Java 17+

Tạo tệp `StringCopyDemo.java`:

```java
public class StringCopyDemo {
    public static void main(String[] args) {
        String original = "Java";
        String alias = original;
        String copy = new String(original);

        System.out.println(original == alias);
        System.out.println(original == copy);
        System.out.println(original.equals(copy));
        System.out.println(copy.equals("Java"));
    }
}
```

Biên dịch và chạy:

```bash
javac StringCopyDemo.java
java StringCopyDemo
```

Kết quả chuẩn:

```text
true
false
true
true
```

Giải thích: `==` trên hai biến tham chiếu kiểm tra **cùng đối tượng hay không**, còn `String.equals` kiểm tra **cùng dãy ký tự hay không**. Vì thế `original == copy` sai nhưng `original.equals(copy)` đúng.

Khi so sánh nội dung, ưu tiên `a.equals(b)`. Nếu `a` có thể `null`, dùng `Objects.equals(a, b)` sau khi import `java.util.Objects`; hoặc đặt hằng ở vế trái như `"Java".equals(a)`. Không dựa vào `==` chỉ vì một số literal có thể cùng nằm trong pool.

## 2. `String` bất biến: vì sao không cần sao chép phòng vệ?

Đối tượng `String` là *immutable*. API công khai thông thường không cung cấp thao tác chỉnh sửa dãy ký tự đã lưu bên trong đối tượng đó. Các phương thức `replace`, `concat`, `trim`, `substring`, `toUpperCase`… tạo hoặc trả về một **giá trị chuỗi kết quả**; chúng không làm biến đổi nội dung đối tượng nguồn. Một phương thức có thể trả về cùng tham chiếu khi không có gì thay đổi, tùy hợp đồng của phương thức.

Xét ví dụ `StringImmutabilityDemo.java`:

```java
public class StringImmutabilityDemo {
    public static void main(String[] args) {
        String original = "cat";
        String alias = original;
        String modified = original.replace('c', 'b');

        System.out.println(original);
        System.out.println(alias);
        System.out.println(modified);

        original = "dog";
        System.out.println(alias);
        System.out.println(original);
    }
}
```

```bash
javac StringImmutabilityDemo.java
java StringImmutabilityDemo
```

```text
cat
cat
bat
cat
dog
```

Ở ba dòng đầu, `replace` cho ra chuỗi `"bat"` còn cả `original` và `alias` vẫn thấy `"cat"`. Sau đó, lệnh `original = "dog"` chỉ đổi tham chiếu lưu trong **biến** `original`; `alias` vẫn trỏ đến đối tượng `"cat"` cũ. **Gán lại biến khác với sửa nội dung đối tượng.**

Vì thế, khi viết lớp chứa một trường `String`, ta thường không cần sao chép trong constructor và getter:

```java
public final class UserLabel {
    private final String label;

    public UserLabel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
```

Đây là một lớp minh họa dùng trong chương trình khác, không có `main` riêng. Hai cách viết `this.label = new String(label)` và `return new String(label)` thường chỉ tạo thêm đối tượng mà không tăng mức bảo vệ nội dung. `final` bảo đảm trường `label` không bị gán lại sau khi khởi tạo; tính bất biến của chính đối tượng `String` là tính chất riêng.

Nếu nghiệp vụ yêu cầu `label` không được `null` hoặc không được rỗng, phải **kiểm tra giá trị đầu vào** phù hợp; việc bọc `new String(label)` không thay thế kiểm tra hợp lệ.

## 3. Tác động của String pool

*String pool* cho phép JVM dùng lại tham chiếu chuẩn tắc của literal và chuỗi đã intern. Ví dụ:

```java
String a = "hello";
String b = "hello";
String c = new String("hello");

System.out.println(a == b);      // true
System.out.println(a == c);      // false
System.out.println(a.equals(c));  // true
```

Ở đây `a` và `b` cùng trỏ tới literal đã intern, còn `new String("hello")` tạo đối tượng khác. Không cần viết `new String("hello")` để «kích hoạt» pool: literal đã thuộc cơ chế pool từ đầu.

Phương thức `intern()` trả về tham chiếu chuẩn tắc của chuỗi có nội dung tương ứng trong pool; nó có mục đích khác với `new String(String)`. Dùng `intern()` để ép `==` hoạt động trong logic nghiệp vụ thường gây ràng buộc thừa. `equals` diễn đạt đúng ý định so sánh nội dung.

Nhớ rằng **không phải mọi chuỗi có cùng nội dung đều cùng tham chiếu**. Chuỗi được tính toán lúc chạy, đọc từ tệp, lấy từ API hoặc tạo từ các đối tượng khác có thể bằng nhau về nội dung nhưng khác identity.

## 4. Hệ quả khi lạm dụng `new String(existing)`

Cùng một nhu cầu đặt tên biến mới:

```java
String source = "Java Core";
String first = source;             // Ưu tiên.
String second = new String(source); // Thường dư thừa.
```

Cả `first` và `second` đều cho nội dung `"Java Core"`. Tuy nhiên:

1. **Đối tượng mới**: theo ngữ nghĩa Java, biểu thức `new` tạo một đối tượng riêng, kéo theo khả năng cấp phát và thu gom rác thêm. JIT có thể tối ưu bỏ cấp phát khi không quan sát được, nên không thể khẳng định một mức overhead cố định trong mọi lần chạy.
2. **Danh tính thay đổi**: `first == second` là `false` mặc dù `first.equals(second)` là `true`. Khi code vô tình phụ thuộc vào identity, bản sao mới có thể khiến hành vi khác đi.
3. **Làm mờ ý nghĩa của code**: người đọc có thể nghĩ cần tách hai chuỗi để ngăn một bên sửa nội dung bên kia, trong khi `String` vốn bất biến.
4. **Không xử lý `null` tốt hơn**: phép gán `String alias = source` chấp nhận `source == null`; ngược lại `new String((String) null)` ném `NullPointerException`. Ép kiểu trong ví dụ làm rõ overload `String(String)`.

Hiếm khi một thiết kế thật sự cần *identity* riêng của `String`. Nếu có, yêu cầu này phải được diễn đạt cụ thể và xem xét cẩn thận vì so sánh, đồng bộ hoặc cấu trúc dữ liệu dựa trên identity của chuỗi dễ gây lỗi. Với dữ liệu văn bản thông thường, giá trị nội dung mới là điều quan trọng.

## 5. Khi nào dùng constructor `new String(...)`?

Phân biệt loại đầu vào:

| Cách viết | Đầu vào / mục tiêu | Đánh giá |
| --- | --- | --- |
| `String b = a;` | `a` đã là `String`; dùng lại giá trị bất biến | **Nên dùng** trong trường hợp thường gặp |
| `new String(a)` | `a` đã là `String`; tạo identity mới cùng nội dung | **Hầu như không cần** |
| `new String(chars)` | `char[]` có thể bị thay đổi; chụp nội dung ký tự | **Có ích** khi chuyển mảng thành chuỗi |
| `new String(bytes, StandardCharsets.UTF_8)` | Giải mã `byte[]` theo charset cụ thể | **Có ích** khi xử lý bytes |
| `builder.toString()` | Lấy chuỗi từ `StringBuilder` | **Phù hợp**, không cần bọc `new String(...)` |
| `new String()` | Tạo chuỗi rỗng | Dùng `""` đơn giản hơn |

### Vì sao mảng ký tự là trường hợp khác?

`char[]` có thể thay đổi, còn `String` thì bất biến. Tạo `String` từ mảng lấy nội dung ở thời điểm gọi; dùng lại một tham chiếu `String` đã có không cần bước này. Ví dụ sau so sánh trực tiếp hai trường hợp:

Tạo tệp `StringSourceChoiceDemo.java`:

```java
public class StringSourceChoiceDemo {
    public static void main(String[] args) {
        String original = "Java";
        String alias = original;
        String redundant = new String(original);
        char[] letters = {'J', 'a', 'v', 'a'};
        String fromArray = new String(letters);
        letters[0] = 'K';

        System.out.println(original == alias);
        System.out.println(original == redundant);
        System.out.println(original.equals(redundant));
        System.out.println(fromArray);
        System.out.println(new String(letters));
    }
}
```

Chạy `javac -encoding UTF-8 StringSourceChoiceDemo.java` rồi `java StringSourceChoiceDemo`:

```text
true
false
true
Java
Kava
```

`alias` chỉ sao chép tham chiếu, `redundant` tạo đối tượng chuỗi riêng dù nội dung bằng nhau. `fromArray` giữ giá trị `"Java"` sau khi mảng đổi thành `Kava`. Đó là lý do chuyển từ mảng có ý nghĩa, còn sao chép một `String` đã có thường dư thừa. Các bài về `char[]`, `byte[]` và `StringBuilder` trước đó trình bày chi tiết từng cơ chế chuyển đổi.

## 6. Chọn cách viết đúng theo mục tiêu

### Trường hợp A: dùng lại chuỗi sẵn có

```java
String title = "Java";
String displayTitle = title;
```

Dùng lại tham chiếu. Không có nguy cơ một phép nối chuỗi sau đó làm sửa đối tượng cũ.

### Trường hợp B: tạo giá trị văn bản đã biến đổi

```java
String raw = " java ";
String normalized = raw.trim().toUpperCase();
```

`raw` vẫn có nội dung `" java "`, còn `normalized` có nội dung `"JAVA"`. Ta gọi phương thức xử lý để lấy **giá trị khác**, không sao chép `raw` rồi mong nó tự thay đổi.

### Trường hợp C: xây dựng chuỗi qua nhiều bước

```java
StringBuilder builder = new StringBuilder();
builder.append("Hello");
builder.append(", ");
builder.append("Java");
String message = builder.toString();
```

`message` chứa `"Hello, Java"`. `StringBuilder` thích hợp khi có nhiều lần bổ sung ký tự; `toString()` kết thúc việc xây dựng và cung cấp giá trị `String` bất biến. Không cần thêm `new String(message)`.

### Trường hợp D: cần kiểm tra tham số `String`

```java
import java.util.Objects;

public final class Greeting {
    private final String message;

    public Greeting(String message) {
        this.message = Objects.requireNonNull(message, "message");
    }

    public String message() {
        return message;
    }
}
```

Đây là lớp minh họa độc lập về thiết kế, không có `main`. `Objects.requireNonNull` đặt ra điều kiện đầu vào khác với vấn đề sao chép. Lưu trực tiếp và trả trực tiếp `String` là hợp lý vì `String` bất biến.

## 7. Những nhầm lẫn dễ gặp

**«`b = a` thì sửa `b` sẽ sửa `a`?»** Không. Không có API sửa nội dung đối tượng `String` tại chỗ. Viết `b = b + "!"` làm `b` nhận giá trị/tham chiếu mới; `a` không bị thay đổi.

**«`new String(a)` chắc chắn deep copy dữ liệu nội bộ?»** Không có bảo đảm công khai như vậy. Điều quan trọng ở cấp ngôn ngữ là object identity mới và nội dung chuỗi tương ứng. Không nên dựa vào cách JVM biểu diễn các ký tự trong bộ nhớ.

**«Hai chuỗi cùng chữ thì `==` phải đúng?»** Sai. `equals` dùng để so nội dung. `==` có thể đúng với cùng literal hoặc cùng tham chiếu, nhưng không phải quy tắc tổng quát cho giá trị văn bản.

**«Constructor của một lớp cần phòng vệ tham số `String` bằng `new String(name)`?»** Thường không. Gán lại biến của người gọi không thay đổi chuỗi được trường lưu giữ. Nếu tham số là `char[]` hay đối tượng mutable thì vấn đề khác.

**«Hàm Java có thể thay đổi chuỗi của người gọi do truyền tham chiếu?»** Java truyền **giá trị** của tham số; với đối tượng, giá trị đó là tham chiếu. Gán lại biến tham số trong hàm không gán lại biến của người gọi, và chuỗi không có phương thức sửa nội dung tại chỗ.

**«`new String()` tốt hơn `""` vì tạo giá trị rỗng riêng?»** Với nhu cầu chuỗi rỗng thông thường, literal `""` ngắn, rõ nghĩa và tránh chủ động tạo wrapper thừa.

## 8. Tự kiểm tra

Dự đoán bốn dòng đầu ra:

```java
String first = "core";
String second = first;
String third = new String(first);

second = second.concat("-java");

System.out.println(first);
System.out.println(second);
System.out.println(first == third);
System.out.println(first.equals(third));
```

Đáp án:

```text
core
core-java
false
true
```

`second` được gán lại kết quả `concat`; `first` vẫn là `"core"`. `third` là đối tượng khác vì `new`, nhưng nội dung vẫn bằng `first`.

Hai câu hỏi tự đánh giá:

1. Trong getter trả về một trường `private final String name`, `return new String(name)` có làm dữ liệu an toàn hơn không? **Không:** `String` đã bất biến.
2. Có thể thay `new String(bytes, StandardCharsets.UTF_8)` bằng `String text = bytes` khi cần đọc dữ liệu mạng không? **Không:** `byte[]` không phải `String`; cần giải mã bytes theo charset thích hợp.

**Ghi nhớ:** `String b = a` dùng lại một giá trị bất biến; `new String(a)` tạo đối tượng `String` mới cùng nội dung, thường dư thừa. Constructor `String` hữu ích hơn khi đầu vào là **mảng ký tự, bytes hoặc dữ liệu phải chuyển đổi thành chuỗi**.

</details>

- [Quay lại đầu trang](#back-to-top)
