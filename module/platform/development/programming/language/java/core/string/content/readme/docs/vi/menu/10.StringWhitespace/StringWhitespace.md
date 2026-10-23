<a id="back-to-top"></a>

# Chuỗi trắng

## Menu
- [isBlank so với isEmpty](#string-whitespace-001)
- [trim và tập ký tự không vượt U+0020](#string-whitespace-002)
- [strip, stripLeading và stripTrailing](#string-whitespace-003)
- [Character.isWhitespace và giới hạn với khoảng trắng Unicode](#string-whitespace-004)
- [Khoảng trắng không ngắt dòng không luôn được strip loại bỏ](#string-whitespace-005)
- [Phân biệt null, chuỗi rỗng và dữ liệu chỉ có khoảng trắng](#string-whitespace-006)
- [Xác định chính sách làm sạch trước khi kiểm tra đầu vào](#string-whitespace-007)
- [Vì sao không tự động xóa mọi khoảng trắng trong dữ liệu người dùng](#string-whitespace-008)

## <a id="string-whitespace-001">isBlank so với isEmpty</a>

<details>
<summary>Xem chi tiết</summary>

# `String.isBlank()` so với `String.isEmpty()` trong Java

Trong Java, **chuỗi rỗng** và **chuỗi chỉ chứa khoảng trắng** là hai khái niệm khác nhau. Hai phương thức `String.isEmpty()` và `String.isBlank()` giúp kiểm tra hai tính chất này nhưng không thay thế nhau. Chọn sai phương thức có thể làm chương trình chấp nhận tên chỉ có dấu cách, từ chối đầu vào hợp lệ hoặc xử lý nhầm các ký tự Unicode nhìn như khoảng trắng.

Mục tiêu bài học:

- Hiểu chính xác điều kiện trả về `true` của `isEmpty()` và `isBlank()`.
- Phân biệt chuỗi `""`, `"   "`, tab, newline, khoảng trắng Unicode, non-breaking space và ký tự không nhìn thấy.
- Chọn phép kiểm tra phù hợp cho dữ liệu đầu vào, nhận biết `null` và quan hệ với `strip()`/`trim()`.
- Sử dụng ví dụ Java 17+ có thể biên dịch và kiểm chứng kết quả.

## 1. Khái niệm: empty và blank

### `isEmpty()`: chuỗi có độ dài bằng 0

`String.isEmpty()` trả về `true` khi và chỉ khi `length() == 0`. Nó **không đọc nội dung** để xem đó có phải dấu cách hay không.

~~~java
System.out.println("".isEmpty());       // true
System.out.println(" ".isEmpty());      // false
System.out.println("\t".isEmpty());     // false
System.out.println("Java".isEmpty());   // false
~~~

Một chuỗi có một dấu cách vẫn có `length() == 1`, nên không rỗng. Một chuỗi gồm tab hoặc xuống dòng cũng không rỗng. `isEmpty()` có sẵn từ Java 6; biểu thức `s.isEmpty()` diễn đạt rõ nghĩa hơn `s.length() == 0` nhưng có cùng điều kiện logic.

### `isBlank()`: rỗng hoặc chỉ chứa whitespace

`String.isBlank()`, được bổ sung trong Java 11, trả `true` khi chuỗi **rỗng hoặc tất cả code point của chuỗi đều được Java coi là whitespace** theo `Character.isWhitespace(int)`. Nếu có ít nhất một code point không thuộc nhóm đó, kết quả là `false`.

~~~java
System.out.println("".isBlank());          // true
System.out.println(" ".isBlank());         // true
System.out.println("\t\n".isBlank());      // true
System.out.println("  Java  ".isBlank());  // false
~~~

Có thể nhớ như sau:

- `isEmpty()` hỏi: **“Có ký tự nào không?”**
- `isBlank()` hỏi: **“Có ký tự nào không phải whitespace theo quy tắc Java không?”**

Vì không có ký tự nào để vi phạm điều kiện “chỉ chứa whitespace”, chuỗi rỗng cũng được coi là blank. Với mọi `String s` không null, **`s.isEmpty()` là `true` thì `s.isBlank()` cũng là `true`**; chiều ngược lại không đúng.

## 2. So sánh trực tiếp bằng bảng

| Nội dung String | `isEmpty()` | `isBlank()` | Giải thích |
|---|---:|---:|---|
| `""` | `true` | `true` | Không có code unit nào |
| `" "` | `false` | `true` | Một dấu cách ASCII |
| `"   "` | `false` | `true` | Ba dấu cách |
| `"\t"` | `false` | `true` | Tab là whitespace |
| `"\n"` | `false` | `true` | Line feed là whitespace |
| `" \t\n "` | `false` | `true` | Toàn whitespace |
| `"Java"` | `false` | `false` | Có chữ cái |
| `" Java "` | `false` | `false` | Có chữ cái dù hai đầu là dấu cách |
| `"\u2003"` | `false` | `true` | U+2003 EM SPACE được `Character.isWhitespace` công nhận |
| `"\u00A0"` | `false` | `false` | U+00A0 NO-BREAK SPACE không được `Character.isWhitespace` công nhận |
| `"\u200B"` | `false` | `false` | U+200B ZERO WIDTH SPACE không phải whitespace theo API này |
| Chuỗi chứa code unit U+0000 | `false` | `false` | Null character không phải whitespace theo `Character.isWhitespace` |

**Lưu ý về bảng:** `"\t"`, `"\n"` và `"\u2003"` là cách biểu diễn **trong mã nguồn Java**, không phải văn bản chứa dấu backslash và chữ `t`, `n` hay `u2003`. Vì một số dấu cách Unicode không nhìn thấy, nên khi kiểm thử hãy hiển thị mã Unicode hoặc nhãn rõ ràng thay vì chỉ in chuỗi ra terminal.

## 3. “Whitespace” của Java không có nghĩa là mọi ký tự nhìn như khoảng trống

Điểm dễ nhầm nhất của `isBlank()` là cách định nghĩa whitespace. Java dựa vào `Character.isWhitespace(int)`. Nó nhận diện nhiều ký tự như ASCII space, tab, xuống dòng và một số Unicode space, nhưng **không bao gồm mọi ký tự Unicode có tên hoặc hình dạng liên quan đến khoảng trống**.

Ví dụ:

~~~java
System.out.println(Character.isWhitespace(' '));       // true
System.out.println(Character.isWhitespace('\t'));      // true
System.out.println(Character.isWhitespace(0x2003));    // true: EM SPACE
System.out.println(Character.isWhitespace(0x00A0));    // false: NO-BREAK SPACE
System.out.println(Character.isSpaceChar(0x00A0));     // true
System.out.println(Character.isWhitespace(0x200B));    // false: ZERO WIDTH SPACE
~~~

`Character.isSpaceChar(int)` là API khác, dựa trên các Unicode category về dấu phân cách, nên **không tương đương** `Character.isWhitespace(int)`. Chẳng hạn U+00A0 là một *space separator* và `isSpaceChar(0x00A0)` trả `true`, nhưng `isWhitespace(0x00A0)` trả `false`. Một số dấu cách không ngắt dòng như U+2007 FIGURE SPACE và U+202F NARROW NO-BREAK SPACE cũng cần xem xét riêng khi nghiệp vụ muốn coi chúng là blank.

U+200B ZERO WIDTH SPACE thường vô hình nhưng không được `Character.isWhitespace` xem là whitespace. Các ký tự định dạng vô hình khác cũng có thể khiến người dùng nhìn thấy ô trống trong khi `isBlank()` lại trả `false`.

Vì vậy:

- **Trường nhập văn bản phổ thông:** `isBlank()` thường là điểm bắt đầu hợp lý để từ chối chuỗi chỉ có dấu cách, tab hoặc xuống dòng.
- **Hệ thống có quy tắc Unicode riêng:** cần định nghĩa tập code point được bỏ qua hay chấp nhận, rồi viết quy tắc kiểm tra tương ứng.
- **Mã định danh, mật khẩu, dữ liệu bảo mật:** không tùy tiện loại bỏ hoặc gộp các ký tự vô hình khi chưa có đặc tả; việc chuẩn hóa có thể thay đổi giá trị gốc.

Không nên tuyên bố “`isBlank()` nhận diện toàn bộ các loại khoảng trắng của Unicode” hoặc “mọi ký tự vô hình đều là blank”.

## 4. Quan hệ với `strip()`, `trim()` và `length()`

### `strip()` và `isBlank()` dùng tiêu chí whitespace gần nhau

`strip()` (Java 11+) bỏ các code point ở **đầu và cuối** chuỗi được `Character.isWhitespace` nhận diện. Với một `String` bình thường, `s.isBlank()` tương ứng về ý nghĩa với việc `s.strip().isEmpty()`: nếu tất cả ký tự đều là whitespace, sau `strip()` sẽ không còn gì.

~~~java
String text = " \t ";
System.out.println(text.isBlank());         // true
System.out.println(text.strip().isEmpty()); // true

String mixed = "  Java \t";
System.out.println(mixed.isBlank());         // false
System.out.println(mixed.strip());           // Java
~~~

Tuy nhiên, khi **chỉ cần biết có blank hay không**, nên gọi trực tiếp `isBlank()`: code diễn đạt đúng mục đích và không phải chủ động tạo kết quả chuỗi sau khi strip. Nếu cần cả nội dung đã chuẩn hóa để lưu, xử lý hoặc so sánh, dùng `strip()` tại điểm phù hợp.

### `trim()` dùng tiêu chí cũ, khác `strip()`

`trim()` bỏ các code unit có giá trị **không lớn hơn U+0020** ở hai đầu. Điều này khác quy tắc Unicode whitespace của `strip()`. Do đó `s.trim().isEmpty()` **không tương đương** `s.isBlank()`.

Ví dụ đối chứng:

~~~java
String emSpace = "\u2003";
System.out.println(emSpace.isBlank());          // true
System.out.println(emSpace.trim().isEmpty());   // false
System.out.println(emSpace.strip().isEmpty());  // true

String nul = String.valueOf((char) 0);
System.out.println(nul.isBlank());              // false
System.out.println(nul.trim().isEmpty());       // true
System.out.println(nul.strip().isEmpty());      // false
~~~

Dòng liên quan `nul` cho thấy có những code unit bị `trim()` loại bỏ dù `isBlank()` không coi chúng là whitespace. Ngược lại, U+2003 được `isBlank()` nhận diện nhưng `trim()` không bỏ. Dùng `trim().isEmpty()` khi thật sự muốn áp dụng quy tắc `trim()` cũ; đừng coi đây là thay thế chính xác của `isBlank()`.

### `length()` không phải thước đo “ký tự có nghĩa”

`String.length()` đếm **UTF-16 code unit**, không phải số từ, số ký tự hiển thị hoặc số code point. `"🚀".length() == 2`, và `"🚀".isBlank()` là `false`. Điều kiện `isEmpty()` chỉ dựa trên số code unit bằng 0, nên vấn đề biểu diễn Unicode không làm thay đổi ý nghĩa của nó. `isBlank()` kiểm tra whitespace theo code point Unicode của String và không có tác dụng “xóa” nội dung.

## 5. Xử lý `null` đúng cách

Cả hai phương thức đều là **instance method của `String`**. Nếu biến chứa `null`, gọi `value.isEmpty()` hoặc `value.isBlank()` đều ném `NullPointerException`. `null` không phải một chuỗi rỗng.

~~~java
String value = null;
// value.isEmpty(); // NullPointerException
// value.isBlank(); // NullPointerException
~~~

Có hai quy ước thông dụng; hãy chọn rõ ràng theo yêu cầu ứng dụng.

**Quy ước 1: null được xem là “không có dữ liệu”.** Dùng kiểm tra kết hợp:

~~~java
static boolean isMissing(String input) {
    return input == null || input.isBlank();
}
~~~

`||` có đánh giá ngắn mạch, nên khi `input == null` là true, Java không gọi `input.isBlank()`.

**Quy ước 2: null là lỗi lập trình hoặc dữ liệu bắt buộc.** Đưa ra lỗi rõ ràng trước khi kiểm tra chuỗi:

~~~java
import java.util.Objects;

static boolean blankRequiredString(String input) {
    Objects.requireNonNull(input, "input must not be null");
    return input.isBlank();
}
~~~

Việc nên chấp nhận null, từ chối null hay coi null tương đương rỗng là **quy tắc nghiệp vụ**, không phải hành vi mặc định của `isBlank()` hoặc `isEmpty()`.

## 6. Chọn đúng phương thức theo nghiệp vụ

| Nhu cầu | Thường chọn | Lý do |
|---|---|---|
| Kiểm tra chuỗi chưa có nội dung nào | `isEmpty()` | Điều kiện đúng bằng độ dài 0 |
| Trường họ tên/nội dung yêu cầu ít nhất một ký tự không phải whitespace Java | `isBlank()` | Từ chối cả `""` và `"   "` |
| Xác minh giá trị có thể null và không có nội dung đáng kể | `s == null || s.isBlank()` | Bao gồm cả null và chuỗi blank |
| Phân biệt `""` với một dấu cách do người dùng nhập | `isEmpty()` | Dấu cách là dữ liệu, không phải chuỗi rỗng |
| Trường yêu cầu dữ liệu có cấu trúc như email, mã, số | `isBlank()` chỉ là kiểm tra đầu tiên | Cần kiểm tra định dạng, độ dài và quy tắc cụ thể |
| Chuỗi cần loại bỏ khoảng trắng hai đầu trước khi xử lý | `strip()` rồi validation thích hợp | `isBlank()` chỉ trả boolean, không đổi chuỗi |
| Phân tích dữ liệu có U+00A0/U+200B | Quy tắc Unicode tường minh | `isBlank()` không coi mọi ký tự vô hình là whitespace |
| Kiểm tra String có ít nhất một code unit | `!isEmpty()` | Không bảo đảm có nội dung hữu ích cho người dùng |
| Kiểm tra String có ít nhất một code point không phải whitespace Java | `!isBlank()` | Có thể vẫn chứa ký tự vô hình hoặc control character không thuộc tập whitespace |

### Ví dụ: validate trường tên bắt buộc

~~~java
static String validateDisplayName(String raw) {
    if (raw == null || raw.isBlank()) {
        throw new IllegalArgumentException("Name is required");
    }
    String cleaned = raw.strip();
    if (cleaned.length() > 50) {
        throw new IllegalArgumentException("Name is too long");
    }
    return cleaned;
}
~~~

Đây là **ví dụ chính sách đơn giản**, không phải bộ quy tắc hoàn chỉnh cho mọi tên người. Nó dùng `length()` theo UTF-16 code unit và chỉ `strip()` những ký tự mà `Character.isWhitespace` công nhận; U+00A0 hoặc U+200B còn có thể tồn tại. Nếu thực sự cần giới hạn theo code point, số ký tự hiển thị hoặc xử lý các ký tự vô hình, phải thiết kế validation riêng. Đừng tùy tiện đổi `strip()` thành cách xóa mọi whitespace bên trong: tên gồm nhiều từ có khoảng trắng hợp lệ.

### Vì sao không luôn `strip().isEmpty()`?

Nếu chỉ cần quyết định “chuỗi có blank không”, `isBlank()` nêu đúng ý định và đơn giản. Khi vừa cần kiểm tra vừa cần giữ giá trị đã chuẩn hóa, có thể `strip()` một lần:

~~~java
String raw = "  Java  ";
String normalized = raw.strip();
if (!normalized.isEmpty()) {
    System.out.println(normalized); // Java
}
~~~

Không có nghĩa `isBlank()` luôn nhanh hơn mọi cách khác trong mọi bản JVM. Với code nhạy hiệu năng, đo đạc trước khi tối ưu; ưu tiên biểu thức rõ ràng và đúng nghĩa.

## 7. Chương trình Java 17+ hoàn chỉnh

Đặt đoạn sau vào tệp UTF-8 tên **`BlankVsEmptyDemo.java`**. Chương trình hiển thị đầu ra cho các trường hợp quan trọng, so sánh `trim()` với `strip()` và minh họa xử lý null. Các đoạn ngắn ở phần trước chỉ là snippet minh họa, có thể cần đặt trong method/class để chạy độc lập.

~~~java
public class BlankVsEmptyDemo {
    private static void check(String label, String value) {
        System.out.printf("%s: empty=%s, blank=%s%n",
                label, value.isEmpty(), value.isBlank());
    }

    private static boolean isMissing(String value) {
        return value == null || value.isBlank();
    }

    public static void main(String[] args) {
        check("empty", "");
        check("space", " ");
        check("tab-newline", "\t\n");
        check("em-space", "\u2003");
        check("no-break-space", "\u00A0");
        check("zero-width-space", "\u200B");
        check("nul", String.valueOf((char) 0));
        check("word", " Java ");

        String emSpace = "\u2003";
        String nul = String.valueOf((char) 0);
        System.out.println("em-space-trim-empty="
                + emSpace.trim().isEmpty());
        System.out.println("em-space-strip-empty="
                + emSpace.strip().isEmpty());
        System.out.println("nul-trim-empty="
                + nul.trim().isEmpty());
        System.out.println("nul-strip-empty="
                + nul.strip().isEmpty());

        System.out.println("missing-null=" + isMissing(null));
        System.out.println("missing-spaces=" + isMissing(" \t "));
        System.out.println("missing-word=" + isMissing("Java"));
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 BlankVsEmptyDemo.java
java BlankVsEmptyDemo
~~~

Stdout chính xác:

~~~text
empty: empty=true, blank=true
space: empty=false, blank=true
tab-newline: empty=false, blank=true
em-space: empty=false, blank=true
no-break-space: empty=false, blank=false
zero-width-space: empty=false, blank=false
nul: empty=false, blank=false
word: empty=false, blank=false
em-space-trim-empty=false
em-space-strip-empty=true
nul-trim-empty=true
nul-strip-empty=false
missing-null=true
missing-spaces=true
missing-word=false
~~~

Giải thích phần dễ bất ngờ: `"\\u2003"` (trong ví dụ thực tế là escape `\u2003` của Java source) đại diện cho EM SPACE, một whitespace theo Java, nên `isBlank()` đúng và `strip()` bỏ được. `"\u00A0"` là NO-BREAK SPACE và `"\u200B"` là ZERO WIDTH SPACE: cả hai không được `Character.isWhitespace` coi là whitespace. `String.valueOf((char) 0)` tạo chuỗi chứa U+0000, không blank nhưng lại được quy tắc cũ của `trim()` loại bỏ ở hai đầu.

## 8. Những lỗi phổ biến

### Lỗi 1: dùng `isEmpty()` để kiểm tra trường bắt buộc

~~~java
String input = "   ";
if (!input.isEmpty()) {
    // Đi vào nhánh này dù người dùng chưa nhập ký tự có nghĩa.
}
~~~

Nếu chính sách là không chấp nhận chuỗi chỉ có whitespace Java, kiểm tra `!input.isBlank()` sau khi xử lý null.

### Lỗi 2: mặc định `isBlank()` bỏ qua mọi Unicode space

NO-BREAK SPACE và một số ký tự vô hình khác không được `Character.isWhitespace` nhận diện. Nếu nghiệp vụ cần coi chúng là trống, tự định nghĩa kiểm tra dựa trên tập ký tự cho phép, tiêu chí Unicode thích hợp và quy tắc chuẩn hóa đã thống nhất. Phải quyết định riêng việc có loại bỏ zero-width formatting characters hay không.

### Lỗi 3: coi `trim().isEmpty()` bằng `isBlank()`

Hai biểu thức có tiêu chí khác nhau: U+2003 và U+0000 là hai phản ví dụ điển hình. Từ Java 11, dùng trực tiếp `isBlank()` khi mong muốn chính xác là Java whitespace.

### Lỗi 4: không xét `null`

`null.isBlank()` không phải biểu thức Java hợp lệ theo nghĩa literal; còn gọi phương thức trên biến `String` đang null gây `NullPointerException`. Viết guard ở điểm nhận dữ liệu và chọn cách xử lý rõ ràng.

### Lỗi 5: chỉ kiểm tra blank rồi cho rằng dữ liệu đã hợp lệ

`"@"`, `"!"`, `"0"`, U+200B hoặc một chuỗi dài vô hạn đều không blank. Điều đó không chứng minh chúng là email, tên hợp lệ, số đúng định dạng hoặc dữ liệu an toàn. `isBlank()` là kiểm tra **thiếu nội dung theo định nghĩa whitespace**, không phải bộ xác thực nội dung.

### Lỗi 6: thay đổi chuỗi trong khi tưởng đang kiểm tra

Cả `isEmpty()` và `isBlank()` chỉ trả boolean, không sửa String. `strip()` trả lại chuỗi đã xử lý; vì `String` bất biến, gọi `raw.strip();` mà không dùng giá trị trả về sẽ không cập nhật `raw`.

## 9. Checklist lựa chọn

1. **Muốn phát hiện đúng chuỗi độ dài 0?** Dùng `isEmpty()`.
2. **Muốn phát hiện rỗng hoặc chỉ gồm Java whitespace?** Dùng `isBlank()` (Java 11+).
3. **Đầu vào có thể null?** Kiểm tra null trước; tùy nghiệp vụ dùng `s == null || s.isBlank()`.
4. **Cần loại bỏ whitespace đầu/cuối?** Dùng `strip()` khi phù hợp; tránh coi `trim()` và `strip()` là đồng nghĩa.
5. **Có Unicode space/format characters đặc biệt?** Kiểm tra tiêu chí `Character.isWhitespace` trước khi dựa vào `isBlank()`.
6. **Dữ liệu có ràng buộc khác?** Kết hợp với kiểm tra độ dài, cấu trúc, miền giá trị và nghiệp vụ cụ thể.

**Ghi nhớ:** `isEmpty()` kiểm tra **độ dài bằng 0**; `isBlank()` kiểm tra **rỗng hoặc chỉ có whitespace theo Java**. Dấu cách thông thường là điểm khác biệt dễ gặp nhất, còn non-breaking space và các ký tự Unicode vô hình là lý do cần hiểu chính xác định nghĩa whitespace.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-whitespace-002">trim và tập ký tự không vượt U+0020</a>

<details>
<summary>Xem chi tiết</summary>

# `String.trim()` và tập ký tự không vượt `U+0020` trong Java

## 1. `trim()` dùng để làm gì?

Khi nhận chuỗi có khoảng trắng hoặc một số ký tự điều khiển ở **đầu và cuối**, ta thường cần loại bỏ chúng trước khi hiển thị, kiểm tra hoặc xử lý tiếp. Lớp `java.lang.String` có phương thức:

```java
public String trim()
```

`trim()` trả về một chuỗi có nội dung được cắt bỏ từ **đầu** và **cuối** theo quy tắc xác định bởi Java: loại bỏ liên tiếp những `char` có giá trị **không lớn hơn `U+0020`** (tức `0x0000` đến `0x0020`, bao gồm hai đầu). Phương thức không xóa các ký tự tương tự nếu chúng nằm **ở giữa** nội dung.

```java
String original = " \tJava Core\n ";
String cleaned = original.trim();

System.out.println("[" + cleaned + "]"); // [Java Core]
```

Giá trị trả về là `"Java Core"`; dấu cách đầu, tab `\t`, ký tự xuống dòng `\n` và dấu cách cuối bị loại. Dấu cách **giữa** hai từ vẫn còn.

**Điểm mấu chốt:** Tên `trim` dễ làm ta nghĩ nó xử lý toàn bộ “khoảng trắng Unicode”. Thực tế, tiêu chí của `String.trim()` chính là giá trị số của **UTF-16 `char`**, không phải một phép phân loại “whitespace” theo Unicode.

## 2. Quy tắc chính xác: mọi `char` có giá trị `<= U+0020`

`U+0020` là Unicode code point của dấu cách ASCII thông thường (`SPACE`), tương ứng giá trị thập phân `32`. Với `String.trim()`, những `char` có giá trị **từ `0` đến `32`** được xem là có thể cắt bỏ ở rìa chuỗi.

Nói cách khác:

```text
Khoảng giá trị char bị trim ở đầu/cuối:

U+0000  U+0001  ...  U+001F  U+0020
  0       1              31      32
|----------------------------------|
         Được loại bỏ ở rìa
```

Một số ví dụ:

| Ký tự / mã | Giá trị | `trim()` loại ở đầu/cuối? | Ý nghĩa |
|---|---:|---|---|
| `U+0000` (NUL) | 0 | Có | Ký tự điều khiển NUL, không phải dấu cách |
| `U+0009` (`\t`) | 9 | Có | Tab ngang |
| `U+000A` (`\n`) | 10 | Có | Line feed |
| `U+000D` (`\r`) | 13 | Có | Carriage return |
| `U+001F` | 31 | Có | Ký tự điều khiển |
| `U+0020` (`' '`) | 32 | Có | Dấu cách ASCII |
| `U+0021` (`'!'`) | 33 | Không | Dấu chấm than |
| `U+00A0` | 160 | Không | NO-BREAK SPACE (NBSP) |
| `U+2003` | 8195 | Không | EM SPACE |
| `U+3000` | 12288 | Không | IDEOGRAPHIC SPACE |

**Không chỉ có space, tab và newline:** Ký tự NUL (`U+0000`) cũng nằm trong khoảng bị `trim()` xóa, dù nó **không được coi là whitespace** theo `Character.isWhitespace(...)`. Đây là lý do định nghĩa “xóa whitespace” tuy thuận tiện nhưng chưa mô tả đúng toàn bộ hợp đồng của `trim()`.

Ngược lại, NBSP hoặc EM SPACE có thể nhìn giống dấu cách, nhưng giá trị `char` của chúng **lớn hơn** `U+0020`; `trim()` không xóa chúng.

### Vì sao nói về `char`, không chỉ “ký tự Unicode”?

Các API chỉ số và phép xử lý cơ bản của `String` trong Java được diễn đạt theo đơn vị mã **UTF-16** (`char`). Một `char` có giá trị không dấu từ `0` đến `65535`. `trim()` kiểm tra những đơn vị `char` ở rìa theo ngưỡng `U+0020`. Các ký tự Unicode bổ trợ như nhiều emoji được biểu diễn bằng hai `char` surrogate với giá trị lớn hơn ngưỡng này, vì vậy không bị cắt bỏ chỉ vì chúng là ký tự đặc biệt.

## 3. `trim()` kiểm tra từ hai đầu vào giữa

Có thể hình dung phương thức tiến hành theo các bước:

1. Bắt đầu từ ký tự đầu tiên. Khi ký tự đó có giá trị `<= U+0020`, bỏ qua và tiếp tục với ký tự sau.
2. Xét từ cuối chuỗi về phía trước, bỏ qua liên tiếp các ký tự có giá trị `<= U+0020`.
3. Giữ lại **đoạn liên tục nằm giữa hai ranh giới còn lại** và trả về dưới dạng `String`.

Đây là **mô hình diễn giải hành vi**, không phải yêu cầu rằng mọi bản JDK phải cài đặt thuật toán theo đúng số bước hoặc luôn sao chép dữ liệu.

```text
Giá trị đầu vào:

[TAB] [SPACE] [SPACE] J a v a [SPACE] C o r e [LF] [SPACE]
 \__________________/ |________________| \__________/
     trim ở đầu          Giữ nguyên       trim ở cuối

Giá trị trả về:

J a v a [SPACE] C o r e
```

Vì chỉ cắt **đầu và cuối**, một chuỗi có nhiều khoảng trắng giữa hai từ vẫn giữ nguyên phần ở giữa:

```java
String s = " \tA \t B\n";
String result = s.trim();

// result chứa: A, dấu cách, tab, dấu cách, B
```

`trim()` **không** chuẩn hóa nhiều dấu cách giữa các từ thành một, không xóa mọi khoảng trắng trong văn bản và không thực hiện “clean” toàn bộ chuỗi.

### Ba trường hợp ranh giới

```java
"Java".trim()     // "Java": không có ký tự đầu/cuối cần cắt
" \t ".trim()     // "": chỉ chứa các ký tự thuộc tập cần cắt
"".trim()         // "": chuỗi đầu vào vốn rỗng
```

Nếu toàn bộ chuỗi chỉ có các `char` không vượt `U+0020`, kết quả là chuỗi rỗng `""`. Đây là một chuỗi hợp lệ có `length() == 0`, **khác `null`**.

## 4. Ví dụ Java 17+ chạy được và nhìn thấy ký tự vô hình

Các dấu cách, tab, NUL và EM SPACE rất khó phân biệt trong stdout. Vì vậy ví dụ sau định nghĩa `visible(...)` để đổi những ký tự đang quan sát thành nhãn dễ đọc; việc đổi nhãn này **chỉ phục vụ in kết quả**.

Lưu thành **`TrimU0020Demo.java`**:

```java
public class TrimU0020Demo {

    static String visible(String text) {
        return text
                .replace(String.valueOf((char) 0x0000), "[NUL]")
                .replace("\t", "[TAB]")
                .replace("\n", "[LF]")
                .replace(" ", "[SPACE]")
                .replace(String.valueOf((char) 0x00A0), "[NBSP]")
                .replace(String.valueOf((char) 0x2003), "[EM_SPACE]");
    }

    static void show(String label, String input) {
        System.out.println(label + " original=" + visible(input));
        System.out.println(label + " trim=" + visible(input.trim()));
        System.out.println(label + " strip=" + visible(input.strip()));
    }

    public static void main(String[] args) {
        show("ASCII", "\t  Java  \n");

        String nul = String.valueOf((char) 0x0000);
        show("NUL", nul + "Java" + nul);

        String nbsp = String.valueOf((char) 0x00A0);
        show("NBSP", nbsp + "Java" + nbsp);

        String emSpace = String.valueOf((char) 0x2003);
        show("EM", emSpace + "Java" + emSpace);

        String middle = "A \t B";
        System.out.println("middle=" + visible(middle.trim()));

        String source = "  Java  ";
        source.trim(); // Bỏ qua giá trị trả về
        System.out.println("discard=" + visible(source));
        source = source.trim();
        System.out.println("saved=" + visible(source));
    }
}
```

Biên dịch và chạy bằng **JDK 17 trở lên**:

```bash
javac -encoding UTF-8 TrimU0020Demo.java
java TrimU0020Demo
```

**Stdout (nhãn trong ngoặc vuông là cách minh họa ký tự vô hình):**

```text
ASCII original=[TAB][SPACE][SPACE]Java[SPACE][SPACE][LF]
ASCII trim=Java
ASCII strip=Java
NUL original=[NUL]Java[NUL]
NUL trim=Java
NUL strip=[NUL]Java[NUL]
NBSP original=[NBSP]Java[NBSP]
NBSP trim=[NBSP]Java[NBSP]
NBSP strip=[NBSP]Java[NBSP]
EM original=[EM_SPACE]Java[EM_SPACE]
EM trim=[EM_SPACE]Java[EM_SPACE]
EM strip=Java
middle=A[SPACE][TAB][SPACE]B
discard=[SPACE][SPACE]Java[SPACE][SPACE]
saved=Java
```

**Đọc kết quả:**

- **ASCII:** tab, dấu cách và line feed ở rìa đều có mã `<= U+0020`, nên `trim()` và `strip()` cùng cho `"Java"`.
- **NUL:** `trim()` xóa ký tự `U+0000` ở hai đầu. `strip()` giữ lại vì NUL không thỏa `Character.isWhitespace(...)`.
- **NBSP:** cả hai phương thức trong ví dụ đều giữ NBSP (`U+00A0`), mặc dù trông giống khoảng trắng. Đây là giới hạn dễ bị bỏ sót khi xử lý văn bản sao chép từ trang web.
- **EM SPACE:** `trim()` giữ `U+2003` vì lớn hơn ngưỡng; `strip()` loại bỏ vì `Character.isWhitespace` nhận diện ký tự này là whitespace.
- **middle:** `trim()` không động đến khoảng trắng và tab ở giữa hai ký tự `A` và `B`.
- **discard / saved:** `trim()` trả về chuỗi kết quả; nếu không sử dụng giá trị trả về, biến `source` không đổi.

## 5. Phân biệt `trim()` với `strip()` trong Java 11+

Đây là hai phương thức khác nhau về **tiêu chí phân loại ký tự**:

| Phương thức | Tiêu chí xóa ở hai đầu | Hệ quả đáng nhớ |
|---|---|---|
| `trim()` | `char` có giá trị `<= U+0020` | Xóa dấu cách ASCII, một số control char kể cả NUL |
| `strip()` | Unicode code point thỏa `Character.isWhitespace(...)` | Xóa nhiều whitespace Unicode như EM SPACE |
| `stripLeading()` | Tiêu chí như `strip()`, chỉ xét **đầu** | Giữ nguyên phần cuối |
| `stripTrailing()` | Tiêu chí như `strip()`, chỉ xét **cuối** | Giữ nguyên phần đầu |

`strip()`, `stripLeading()` và `stripTrailing()` có từ Java 11. Cả ba cũng trả về giá trị `String`; chúng **không thay đổi** đối tượng ban đầu.

```java
String s = "  Java  ";

System.out.println("[" + s.stripLeading() + "]");  // [Java  ]
System.out.println("[" + s.stripTrailing() + "]"); // [  Java]
System.out.println("[" + s.strip() + "]");         // [Java]
```

### `strip()` không xóa mọi thứ nhìn giống khoảng trắng

Tên “Unicode-aware” có thể dẫn đến suy luận quá mức. `strip()` dựa vào **`Character.isWhitespace`**, và Java không coi mọi ký tự phân cách hoặc dấu cách không ngắt dòng là `isWhitespace == true`.

Ví dụ: `U+00A0` (NBSP), `U+2007` (FIGURE SPACE) và `U+202F` (NARROW NO-BREAK SPACE) không thỏa quy tắc `Character.isWhitespace` của Java. Bởi vậy **đổi `trim()` thành `strip()` không có nghĩa đã xử lý hết các loại khoảng trắng trong dữ liệu nhập**.

Phân biệt thêm `Character.isSpaceChar(...)`: nó có phạm vi nhận diện khác `Character.isWhitespace(...)`, bao gồm một số ký tự space separator mà `isWhitespace` loại trừ. Tuy nhiên, `String.strip()` **không dùng** `Character.isSpaceChar` làm điều kiện của nó.

### `isBlank()` cũng không tương đương `trim().isEmpty()`

Từ Java 11, `String.isBlank()` trả `true` khi chuỗi rỗng hoặc mọi code point thuộc tập `Character.isWhitespace(...)`. Điều này khác với việc kiểm tra xem có còn ký tự sau `trim()` hay không.

Ví dụ, các biểu thức dưới đây có kết quả khác nhau:

```java
String nul = String.valueOf((char) 0x0000);
String em = String.valueOf((char) 0x2003);

System.out.println(nul.trim().isEmpty()); // true
System.out.println(nul.isBlank());        // false

System.out.println(em.trim().isEmpty());  // false
System.out.println(em.isBlank());         // true
```

**Chọn API dựa trên định nghĩa “trắng” của nghiệp vụ**, thay vì nghĩ tất cả các phương thức này có cùng quy tắc.

## 6. Tính bất biến: `trim()` không cập nhật biến tự động

`String` là immutable. Phương thức `trim()` trả về giá trị cần được **sử dụng**, ví dụ bằng phép gán lại, tạo biến mới, `return` hoặc truyền trực tiếp cho một phương thức khác.

**Sai ý định nếu muốn cập nhật biến:**

```java
String username = "  Alice  ";
username.trim();
System.out.println("[" + username + "]"); // [  Alice  ]
```

Lời gọi thứ hai tính kết quả nhưng **bỏ qua** nó. Cách đúng:

```java
username = username.trim();
System.out.println("[" + username + "]"); // [Alice]
```

Hoặc giữ nguyên chuỗi gốc:

```java
String rawUsername = "  Alice  ";
String normalizedUsername = rawUsername.trim();
```

Khi muốn dùng trực tiếp:

```java
System.out.println("[" + rawUsername.trim() + "]");
```

Đây cũng là cách dùng hợp lệ: giá trị trả về được chuyển vào `println`, nên không bị bỏ phí.

Không nên suy đoán danh tính đối tượng chỉ từ lời gọi `trim()`. JDK có thể trả lại chính tham chiếu cũ nếu không có nội dung cần cắt; ở trường hợp khác có thể trả đối tượng `String` mới. **Điều cần dựa vào là nội dung kết quả, không phải `==`**.

## 7. Những tình huống áp dụng và giới hạn

### Tình huống 1: dữ liệu từ biểu mẫu có dấu cách ASCII thừa

```java
String entered = "   learner@example.com   ";
String email = entered.trim();

System.out.println(email); // learner@example.com
```

Phù hợp khi chỉ muốn bỏ dấu cách ASCII ở rìa. Tuy nhiên, xử lý input thật có thể cần xác thực cú pháp, giới hạn chiều dài, chuẩn hóa Unicode theo chính sách và kiểm soát các ký tự không mong muốn. `trim()` **không phải một bước kiểm tra tính hợp lệ tổng quát**.

### Tình huống 2: đọc dòng văn bản có ký tự xuống dòng

```java
String rawLine = "\tstatus=OK\r\n";
String status = rawLine.trim();

System.out.println(status); // status=OK
```

`trim()` có thể loại CR/LF và tab ở hai đầu. Nhưng nếu chương trình cần bảo toàn chính xác định dạng file hoặc khoảng trắng có ý nghĩa, việc tự ý `trim()` có thể làm **mất dữ liệu**.

Ví dụ, trong một giá trị bí mật, chuỗi đã được ký số, dữ liệu fixed-width hoặc văn bản cần giữ nguyên whitespace, không nên loại bỏ ký tự đầu/cuối nếu đặc tả dữ liệu không cho phép.

### Tình huống 3: văn bản sao chép từ trang web có NBSP

```java
String nbsp = String.valueOf((char) 0x00A0);
String copied = nbsp + "Java" + nbsp;

System.out.println(copied.trim().length()); // 6
```

NBSP ở hai đầu **không bị `trim()` loại**, vì vậy chuỗi kết quả vẫn dài `6` UTF-16 code unit: `NBSP` + `Java` (4) + `NBSP`.

Ngay cả `strip()` cũng không xóa NBSP theo `Character.isWhitespace`. Nếu yêu cầu là “loại thêm NBSP ở hai đầu”, cần xác định và triển khai một chính sách chuẩn hóa riêng, có kiểm thử; tránh âm thầm coi mọi ký tự Unicode là dấu cách thông thường.

### Tình huống 4: chuẩn hóa trong phương thức xử lý

```java
static String normalizeLabel(String label) {
    if (label == null) {
        return "";
    }
    return label.trim();
}
```

Trong ví dụ này, `null` được chuyển thành chuỗi rỗng theo **quyết định thiết kế của phương thức**, không phải hành vi có sẵn của `trim()`. Nếu `label` là `null`, gọi trực tiếp `label.trim()` sẽ ném `NullPointerException`.

## 8. Lỗi thường gặp

| Lỗi | Vì sao là lỗi | Cách xử lý |
|---|---|---|
| Nghĩ `trim()` chỉ xóa `' '` | Nó còn xóa những `char` có giá trị nhỏ hơn `U+0020` | Hiểu chính xác ngưỡng `U+0020` |
| Nghĩ `trim()` xóa mọi Unicode space | NBSP, EM SPACE, IDEOGRAPHIC SPACE có giá trị lớn hơn ngưỡng | Chọn `strip()` hoặc chính sách riêng tùy dữ liệu |
| Nghĩ `strip()` xóa mọi dấu cách đặc biệt | `Character.isWhitespace` vẫn không gồm một số non-breaking space | Kiểm tra đúng tập ký tự cần xử lý |
| Gọi `s.trim()` rồi vẫn in `s` | `String` bất biến; kết quả trả về không tự gán | Dùng `s = s.trim()` hoặc lưu biến mới |
| Tưởng `trim()` xóa khoảng trắng ở giữa | Nó chỉ xét hai rìa | Xử lý nội dung giữa theo yêu cầu riêng |
| Gọi `trim()` trên `null` | `null` không có phương thức | Kiểm tra `null` khi cần |
| Dùng `trim()` cho dữ liệu cần giữ nguyên byte/ký tự | Có thể xóa ký tự đầu/cuối mang ý nghĩa | Làm theo hợp đồng định dạng dữ liệu |
| Dùng `==` để so kết quả | So sánh danh tính đối tượng `String` | Dùng `.equals(...)` để so nội dung |

### Phân biệt khoảng trắng có ý nghĩa và ký tự thừa

Các trường hợp sau khác nhau về yêu cầu:

- Tên người dùng được cho phép có dấu cách ở đầu hoặc cuối hay không?
- Trong một biểu mẫu, hai ký tự `U+0020` ở đầu thường là dữ liệu thừa; trong một văn bản định dạng, chúng có thể là dấu thụt dòng.
- Dữ liệu đọc từ hệ thống ngoài có thể chứa NBSP hoặc whitespace Unicode; không nên giả định `trim()` sẽ làm nó giống với văn bản gõ bằng bàn phím.
- Một hệ thống đôi khi cần chỉ báo lỗi cho đầu vào có ký tự điều khiển thay vì đơn giản xóa đi. `trim()` loại cả một số control char, nên có thể che giấu đầu vào không hợp lệ nếu dùng tùy tiện.

Vì vậy cần biết rõ **định nghĩa khoảng trắng hợp lệ** trong nghiệp vụ trước khi chọn `trim`, `strip` hay cách xử lý riêng.

## 9. Kiểm tra chương trình bằng những ca biên

Để bảo đảm không nhầm tiêu chí của `trim()`, có thể viết các khẳng định đơn giản trong phương thức `main`:

```java
String nul = String.valueOf((char) 0x0000);
String nbsp = String.valueOf((char) 0x00A0);
String emSpace = String.valueOf((char) 0x2003);

if (!"Java".equals(("\t Java \n").trim())) {
    throw new AssertionError("Không trim đúng ASCII whitespace");
}

if (!"Java".equals((nul + "Java" + nul).trim())) {
    throw new AssertionError("Không trim đúng NUL");
}

if (!(nbsp + "Java" + nbsp).equals(
        (nbsp + "Java" + nbsp).trim())) {
    throw new AssertionError("NBSP không nên bị trim");
}

if (!(emSpace + "Java" + emSpace).equals(
        (emSpace + "Java" + emSpace).trim())) {
    throw new AssertionError("EM SPACE không nên bị trim");
}

if (!"Java".equals((emSpace + "Java" + emSpace).strip())) {
    throw new AssertionError("strip phải loại EM SPACE");
}
```

Các đoạn ở phần này là mã **nằm trong một phương thức** và có thể chèn vào `main` của lớp Java ở phần 4; riêng lẻ chúng không phải tệp Java hoàn chỉnh. Nếu mọi điều kiện đều đúng, chương trình đi qua khối kiểm tra mà không ném `AssertionError`. Khẳng định thủ công bằng `throw` ở ví dụ này không phụ thuộc tùy chọn JVM `-ea`.

## 10. Câu hỏi tự kiểm tra

**Câu 1.** `"\t Java \n".trim()` trả về gì?

**Đáp án:** `"Java"`. Tab, dấu cách và newline đều thuộc khoảng `<= U+0020` ở hai đầu.

**Câu 2.** `("A " + " B").trim()` có tự xóa hai dấu cách ở giữa không?

**Đáp án:** Không. Kết quả vẫn là `"A  B"`, vì các dấu cách đó **không nằm ở rìa**.

**Câu 3.** Chuỗi chỉ chứa các ký tự `U+0000`, `U+0009` và `U+0020` có kết quả `trim()` như thế nào?

**Đáp án:** Chuỗi rỗng `""`, vì tất cả ký tự đều thuộc tập có thể cắt bỏ.

**Câu 4.** Vì sao `"\u00A0Java\u00A0".trim()` không trả `"Java"`?

**Đáp án:** `U+00A0` là NBSP có giá trị lớn hơn `U+0020`. `trim()` không loại bỏ nó. (Trong Java, Unicode escape `\u00A0` được chuyển sớm trong quá trình xử lý mã nguồn; ở ví dụ này nó đại diện cho NBSP hợp lệ trong literal chuỗi.)

**Câu 5.** Khi hai đầu chứa EM SPACE (`U+2003`), nên dùng `trim()` hay `strip()` để bỏ ký tự ấy?

**Đáp án:** Với ngữ nghĩa mặc định của Java, dùng `strip()` vì `Character.isWhitespace(U+2003)` là `true`; `trim()` giữ nguyên EM SPACE.

**Câu 6.** Vì sao `s.trim();` có thể chạy bình thường nhưng không làm biến `s` thay đổi?

**Đáp án:** `String` bất biến, `trim()` trả giá trị mới hoặc giá trị hiện tại phù hợp quy tắc; nếu kết quả bị bỏ qua, biến `s` vẫn tham chiếu đến chuỗi trước lời gọi.

**Câu 7.** `trim()` và `isBlank()` có nhận diện ký tự NUL (`U+0000`) giống nhau không?

**Đáp án:** Không. `trim()` xóa NUL ở rìa do mã `0 <= U+0020`, trong khi `isBlank()` dùng `Character.isWhitespace` và không xem NUL là whitespace.

## 11. Ghi nhớ

`String.trim()` loại bỏ từ **hai đầu chuỗi** những đơn vị UTF-16 `char` có giá trị **không vượt `U+0020`**; nó không sửa nội dung ở giữa và không thay đổi đối tượng `String` ban đầu. Ngưỡng này bao gồm cả ký tự điều khiển như NUL và bỏ qua nhiều khoảng trắng Unicode có mã lớn hơn `U+0020`.

`String.strip()` (Java 11+) dùng `Character.isWhitespace`, xử lý được nhiều Unicode whitespace hơn nhưng vẫn không bao gồm mọi dấu cách không ngắt dòng. Khi làm việc với dữ liệu đầu vào, hãy xác định tập ký tự cần loại bỏ, giữ kết quả trả về của phương thức và kiểm thử những ca biên như NUL, NBSP và EM SPACE.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-whitespace-003">strip, stripLeading và stripTrailing</a>

<details>
<summary>Xem chi tiết</summary>

# String.strip(), stripLeading() và stripTrailing() trong Java

Trong Java, khi nhận tên người dùng, tiêu đề, nội dung biểu mẫu hay các giá trị văn bản, ta thường cần loại bỏ **ký tự khoảng trắng ở rìa chuỗi**. Từ Java 11, lớp `java.lang.String` có ba phương thức: `strip()`, `stripLeading()` và `stripTrailing()`. Chúng đều trả về một `String` và sử dụng khái niệm **whitespace của Java** thông qua `Character.isWhitespace(...)`.

Điểm cần nắm: **ba phương thức khác nhau ở phía của chuỗi được xử lý**; không phương thức nào có nhiệm vụ xóa khoảng trắng ở giữa nội dung. Chúng cũng khác `trim()` về tiêu chí nhận diện ký tự khoảng trắng, đặc biệt đối với Unicode.

## 1. So sánh ba phương thức

| Phương thức | Tác động | Ví dụ đầu vào | Kết quả |
|---|---|---|---|
| `s.strip()` | Bỏ whitespace ở **cả đầu lẫn cuối** | `"  Hello  "` | `"Hello"` |
| `s.stripLeading()` | Bỏ whitespace **chỉ ở đầu** | `"  Hello  "` | `"Hello  "` |
| `s.stripTrailing()` | Bỏ whitespace **chỉ ở cuối** | `"  Hello  "` | `"  Hello"` |

Chữ *leading* nghĩa là phần đầu; *trailing* nghĩa là phần đuôi. Trong ví dụ bảng, các ký tự khoảng trắng đều là dấu cách ASCII `U+0020` nên có thể quan sát trực tiếp. Với tab, xuống dòng và các ký tự trắng Unicode, đầu ra trên terminal không phải lúc nào cũng có dấu hiệu trực quan rõ ràng.

Cú pháp API:

```java
String both = source.strip();
String left = source.stripLeading();
String right = source.stripTrailing();
```

Đoạn trên là mã minh họa, giả sử `source` đã được khai báo. Cả ba phương thức **không nhận tham số**, có từ **Java 11**, và không ném lỗi khi `source` là chuỗi rỗng hay chỉ có whitespace. Tuy nhiên, nếu biến tham chiếu là `null`, gọi `source.strip()` sẽ gây `NullPointerException`.

## 2. Chuỗi được xử lý từ đâu đến đâu?

Giả sử một chuỗi có ba vùng:

```text
[ whitespace đầu ][ NỘI DUNG ][ whitespace cuối ]
  stripLeading()      giữ lại        stripTrailing()
             \         |         /
              \----- strip() ---/
```

Các phương thức chỉ quét các ký tự **liên tiếp** thuộc nhóm whitespace từ rìa được xét, và dừng khi gặp nội dung **không phải whitespace**. Chúng không “tìm và xóa mọi whitespace” trong cả chuỗi.

Ví dụ:

```java
String input = " \tJava  Core\n";
System.out.println(input.strip().equals("Java  Core")); // true
```

`strip()` bỏ dấu cách và tab ở đầu, bỏ newline ở cuối, nhưng **giữ nguyên hai dấu cách ở giữa** `Java` và `Core`. Nếu mục tiêu là chuẩn hóa khoảng trắng giữa các từ, đó là tác vụ khác với việc cắt khoảng trắng ở hai mép.

Một số ca biên:

- `"".strip()` trả `""`.
- `"   ".strip()` trả `""` vì toàn bộ nội dung là whitespace.
- `"   ".stripLeading()` và `"   ".stripTrailing()` cũng trả chuỗi rỗng.
- `"abc".strip()` có cùng nội dung `"abc"`.
- `" a  b ".strip()` là `"a  b"`: vẫn còn **hai** dấu cách giữa `a` và `b`.
- `"a\nb".strip()` vẫn có newline **bên trong** vì nó không nằm ở rìa chuỗi.

Có thể kết hợp `stripLeading()` và `stripTrailing()` theo thứ tự nào cũng được để đạt **giá trị chuỗi** tương đương `strip()`. Dùng trực tiếp `strip()` khi cần xử lý hai phía là cách rõ ý hơn.

## 3. “Whitespace” theo Java là gì?

Theo API `String`, các phương thức `strip...` nhận diện whitespace dựa trên `Character.isWhitespace(int codePoint)`. Đây là một quy tắc Unicode cụ thể do Java cung cấp, **không phải tất cả những ký tự trông giống khoảng trống trên màn hình**.

Những ví dụ thường gặp:

| Ký tự | Mã Unicode | `Character.isWhitespace` | `strip()` ở rìa |
|---|---|---|---|
| Dấu cách thông thường | `U+0020` (SPACE) | `true` | Bỏ |
| Tab ngang | `U+0009` (TAB) | `true` | Bỏ |
| Line feed | `U+000A` (LF) | `true` | Bỏ |
| Carriage return | `U+000D` (CR) | `true` | Bỏ |
| Em Space | `U+2003` | `true` | Bỏ |
| Ideographic Space | `U+3000` | `true` | Bỏ |
| Non-Breaking Space | `U+00A0` (NBSP) | `false` | **Giữ lại** |
| Figure Space | `U+2007` | `false` | **Giữ lại** |
| Narrow No-Break Space | `U+202F` | `false` | **Giữ lại** |
| Zero Width Space | `U+200B` | `false` | Giữ lại |
| Byte Order Mark (khi là ký tự trong văn bản) | `U+FEFF` | `false` | Giữ lại |

Bảng đưa ra những ca thực tế để **minh họa**, không phải toàn bộ bảng Unicode. Cách phân loại dựa trên quy tắc `Character.isWhitespace` của phiên bản Java bạn dùng; khi nâng cấp JDK, dữ liệu Unicode có thể được cập nhật.

Đặc biệt, tên **Non-Breaking Space** có chữ *Space* nhưng Java cố ý **không coi đây là whitespace theo `isWhitespace`**. Do đó, đoạn sau **không** loại bỏ NBSP:

```java
String data = "\u00A0Hello\u00A0";
System.out.println(data.strip().length()); // 7: NBSP + 5 chữ + NBSP
```

Nếu nghiệp vụ cần coi NBSP như một dấu cách có thể xóa, phải chỉ định chính sách chuyển đổi hoặc chuẩn hóa riêng; không nên giả định rằng `strip()` tự làm điều này.

### Vì sao NBSP có thể gây lỗi tưởng như “không strip được”?

Một chuỗi hiển thị giống `" Hello "` có thể thực ra bắt đầu hoặc kết thúc bằng `U+00A0`. Vì `Character.isWhitespace(0x00A0)` là `false`, `strip()` sẽ dừng tại ký tự đó ở rìa.

Ví dụ: `"\u00A0  Hello  "` bắt đầu bằng NBSP, nên `stripLeading()` **không** vượt qua NBSP để xóa hai dấu cách đứng sau nó. Ở phía phải, `stripTrailing()` vẫn có thể xóa các dấu cách thông thường liên tiếp ở cuối. Nguyên tắc quét từ mép giải thích chính xác hành vi này.

Khi debug, hãy in **code point** hoặc biểu diễn từng ký tự bằng ký hiệu rõ ràng thay vì chỉ nhìn khoảng trống trong output.

## 4. `strip()` khác `trim()` như thế nào?

`String.trim()` là phương thức có từ các phiên bản Java cũ. Nó dùng tiêu chí dựa trên **giá trị đơn vị mã không vượt quá `U+0020`** ở hai rìa (các ký tự có giá trị `<= '\u0020'`). Tiêu chí này không đồng nhất với Unicode whitespace của `Character.isWhitespace`.

| Vấn đề | `trim()` | `strip()` |
|---|---|---|
| Xuất hiện từ | Java rất lâu trước Java 11 | Java 11 |
| Tiêu chí chính | Giá trị đầu/cuối `<= U+0020` | `Character.isWhitespace` |
| SPACE `U+0020`, TAB `U+0009`, LF `U+000A` | Loại bỏ | Loại bỏ |
| EM SPACE `U+2003` | **Giữ lại** | **Loại bỏ** |
| IDEOGRAPHIC SPACE `U+3000` | **Giữ lại** | **Loại bỏ** |
| NBSP `U+00A0` | Giữ lại | Giữ lại |
| NULL `U+0000` ở rìa | Có thể loại bỏ vì `U+0000 <= U+0020` | Không thuộc whitespace nên giữ lại |

Điều đó có nghĩa **`strip()` không đơn giản là một “`trim()` mạnh hơn” trên mọi dữ liệu**. Với những đơn vị mã điều khiển có giá trị nhỏ mà Java không coi là whitespace, `trim()` có thể loại bỏ trong khi `strip()` giữ nguyên. Khi mục tiêu thực sự là loại bỏ **Unicode whitespace ở rìa**, nên ưu tiên `strip()` trong Java 11+.

Một ví dụ dễ thấy bằng mã nguồn:

```java
String text = "\u2003 Java \u2003";
System.out.println(text.strip().equals("Java")); // true
System.out.println(text.trim().equals(text));     // true
```

`trim()` nhìn thấy Em Space ở mép là ký tự `> U+0020` nên dừng ngay; `strip()` nhận ra Em Space là whitespace và loại bỏ nó cùng các dấu cách kề bên.

## 5. Ví dụ Java 17+: nhìn rõ kết quả khi có TAB, Em Space, NBSP

Vì tab và khoảng trắng Unicode khó phân biệt trong console, ví dụ dùng hàm `visible` để **chỉ phục vụ in log**. Nó hiển thị TAB thành `<TAB>`, Em Space thành `<EM>`, NBSP thành `<NBSP>` và dấu cách ASCII thành ký hiệu `_`. Hàm này không tham gia vào việc cắt chuỗi; nó chỉ giúp người học quan sát kết quả.

Lưu toàn bộ vào **`StripWhitespaceDemo.java`**:

```java
public class StripWhitespaceDemo {
    private static String visible(String text) {
        return text
                .replace("\t", "<TAB>")
                .replace("\u2003", "<EM>")
                .replace("\u00A0", "<NBSP>")
                .replace(" ", "_");
    }

    public static void main(String[] args) {
        String input = "\t\u2003 Hello \t";
        String nbsp = "\u00A0Hello\u00A0";
        String allWhitespace = " \t\u2003 ";

        System.out.println("source: [" + visible(input) + "]");
        System.out.println("strip: [" + visible(input.strip()) + "]");
        System.out.println("leading: ["
                + visible(input.stripLeading()) + "]");
        System.out.println("trailing: ["
                + visible(input.stripTrailing()) + "]");
        System.out.println("trim: [" + visible(input.trim()) + "]");

        System.out.println("NBSP strip: [" + visible(nbsp.strip()) + "]");
        System.out.println("EM isWhitespace: "
                + Character.isWhitespace('\u2003'));
        System.out.println("NBSP isWhitespace: "
                + Character.isWhitespace('\u00A0'));
        System.out.println("all blank: " + allWhitespace.isBlank());
        System.out.println("all strip empty: "
                + allWhitespace.strip().isEmpty());
        System.out.println("middle unchanged: "
                + " a  b ".strip().equals("a  b"));
    }
}
```

Biên dịch và chạy với **JDK 17 trở lên**:

```bash
javac -encoding UTF-8 StripWhitespaceDemo.java
java StripWhitespaceDemo
```

Kết quả:

```text
source: [<TAB><EM>_Hello_<TAB>]
strip: [Hello]
leading: [Hello_<TAB>]
trailing: [<TAB><EM>_Hello]
trim: [<EM>_Hello]
NBSP strip: [<NBSP>Hello<NBSP>]
EM isWhitespace: true
NBSP isWhitespace: false
all blank: true
all strip empty: true
middle unchanged: true
```

Diễn giải:

1. Đầu vào có TAB, Em Space và một dấu cách ở phía đầu; phía cuối có dấu cách và TAB.
2. `strip()` bỏ hết các ký tự được xem là whitespace ở **cả hai** mép: `"Hello"`.
3. `stripLeading()` chỉ bỏ phần đầu: `"Hello \t"`.
4. `stripTrailing()` chỉ bỏ phần cuối: `"\t\u2003 Hello"`.
5. `trim()` chỉ bỏ TAB ngoài cùng ở đầu, rồi gặp Em Space nên dừng. Ở cuối, nó vẫn bỏ TAB và dấu cách vì các ký tự đó `<= U+0020`; kết quả còn Em Space và dấu cách ở đầu: `"\u2003 Hello"`.
6. `NBSP` đứng ở hai đầu vẫn tồn tại sau `strip()` vì `isWhitespace` trả `false` cho NBSP.
7. `allWhitespace.isBlank()` là `true`, và `strip()` biến chuỗi chỉ chứa whitespace thành `""`. Đây là hai cách kiểm tra nội dung trắng có liên quan, nhưng mục đích sử dụng khác nhau.

Hãy chú ý `<TAB>`, `<EM>`, `<NBSP>` và `_` chỉ là ký hiệu để **hiển thị kết quả trong chương trình demo**. Chúng không phải các ký tự mà `strip()` chèn vào chuỗi.

## 6. `strip` có thay đổi chuỗi gốc không?

Không. `String` trong Java là **immutable**: các phương thức `strip()`, `stripLeading()` và `stripTrailing()` trả về giá trị `String`; chúng không thay đổi nội dung của đối tượng `String` đang được tham chiếu bởi biến ban đầu.

```java
String name = "  Mai  ";
name.strip();
System.out.println("[" + name + "]"); // [  Mai  ]

name = name.strip();
System.out.println("[" + name + "]"); // [Mai]
```

Lời gọi `name.strip()` đầu tiên chỉ tính và trả kết quả rồi bị bỏ qua. Muốn dùng nội dung đã được cắt, cần **gán lại** cho biến hoặc lưu vào một biến mới:

```java
String raw = "  Mai  ";
String normalized = raw.strip();
```

Ở đây `raw` vẫn là `"  Mai  "`; `normalized` là `"Mai"`. Đừng dựa vào `==` để kiểm tra “đã strip chưa”: đặc tả quan tâm đến **giá trị văn bản**, không bảo đảm một quy tắc identity có ích cho nghiệp vụ. Dùng `equals()` khi so sánh nội dung.

## 7. `strip()` liên quan thế nào tới `isBlank()`?

`String.isBlank()`, cũng được thêm từ Java 11, trả `true` nếu chuỗi **rỗng hoặc chỉ gồm các code point được `Character.isWhitespace` nhận diện**.

```java
System.out.println(" \t\u2003 ".isBlank()); // true
System.out.println(" \t\u2003 ".strip().isEmpty()); // true
System.out.println("".isBlank());          // true
System.out.println("\u00A0".isBlank());    // false
```

Với các chuỗi Java thông thường, nhận xét sau rất hữu ích về mặt giá trị: nếu `s` chỉ gồm Unicode whitespace theo đúng tiêu chí `isBlank()` thì `s.strip()` là chuỗi rỗng. Tuy vậy, nên chọn API phù hợp với **ý định**:

- **`isBlank()`:** câu hỏi “chuỗi chỉ có whitespace hay không?”; trả `boolean`.
- **`strip()`:** yêu cầu tạo **giá trị văn bản mới** không còn whitespace ở hai đầu; trả `String`.
- **`isEmpty()`:** hỏi chiều dài chuỗi bằng 0 hay không, **không** đồng nghĩa chỉ chứa whitespace. `" ".isEmpty()` là `false`.

Một cách kiểm tra đầu vào phổ biến:

```java
static boolean hasMeaningfulText(String input) {
    return input != null && !input.isBlank();
}
```

Đoạn mã là phương thức minh họa, không phải chương trình độc lập. Nếu yêu cầu lưu một giá trị đã cắt rìa, dùng `input.strip()` khi đã kiểm tra `input != null`.

## 8. Dùng đúng phương thức trong từng tình huống

### Chuẩn hóa dữ liệu biểu mẫu

```java
String username = rawUsername.strip();
```

Nếu yêu cầu là xóa whitespace Unicode ở hai mép tên người dùng, `strip()` diễn đạt ý định tốt. Tuy nhiên, phải quy định rõ chính sách với NBSP và các ký tự định dạng vô hình: **strip không tự xóa mọi thứ trông như khoảng trắng**. Nếu hệ thống coi NBSP là khoảng trắng cần loại bỏ, quyết định đó thuộc quy tắc kiểm tra dữ liệu, không phải bảo đảm mặc định của API.

### Giữ indentation bên trái, bỏ trailing whitespace

```java
String cleanedLine = sourceLine.stripTrailing();
```

Hữu ích khi cần giữ thụt lề có ý nghĩa (ví dụ mã nguồn hoặc văn bản căn lề) nhưng bỏ khoảng trắng không mong muốn ở cuối. Với file có nhiều dòng, muốn xử lý từng dòng thì phải tách/duyệt từng dòng và áp dụng đúng chính sách, vì `stripTrailing()` trên toàn chuỗi chỉ xử lý **mép cuối của toàn chuỗi**, không tự làm sạch cuối **mọi dòng**.

### Bỏ phần thụt lề đầu nhưng giữ ký tự cuối

```java
String lineWithoutIndent = line.stripLeading();
```

Dùng khi indentation đầu là thứ cần bỏ nhưng whitespace cuối phải được giữ. Lưu ý text block của Java có thêm các phương thức khác liên quan indentation; không đồng nhất `stripLeading()` với `stripIndent()` hoặc `indent()`, vì chúng có những quy tắc riêng và mục tiêu khác.

### Phân biệt làm sạch rìa với sửa nội dung trong chuỗi

```java
String data = " a   b ";
System.out.println(data.strip()); // a   b
```

Bài toán biến nhiều khoảng trắng giữa `a` và `b` thành một khoảng trắng cần bước xử lý riêng. Nếu phải giữ nguyên định dạng bên trong (tên nhiều thành phần, mã định danh, văn bản có cấu trúc), đừng tự động thay đổi whitespace nội bộ.

## 9. Các lỗi thường gặp

| Lỗi hoặc hiểu nhầm | Hậu quả | Cách xử lý |
|---|---|---|
| Cho rằng `strip()` xóa mọi whitespace trong chuỗi | Các dấu cách/tab ở giữa vẫn còn | `strip()` chỉ áp dụng ở **rìa** |
| Cho rằng `stripLeading()` cũng bỏ phần đuôi | Kết quả vẫn có trailing whitespace | Dùng `strip()` hoặc kết hợp thêm `stripTrailing()` |
| Gọi `s.strip()` rồi vẫn dùng `s` mà không gán | Dữ liệu gốc không thay đổi | Gán lại hoặc lưu kết quả |
| Dùng `trim()` thay `strip()` khi đầu vào có Em Space | Không loại bỏ `U+2003` ở mép | Dùng `strip()` trong Java 11+ |
| Tưởng NBSP chắc chắn bị `strip()` xóa | Chuỗi vẫn có `U+00A0` | Kiểm tra code point và quy tắc chuẩn hóa |
| Cho rằng `Character.isSpaceChar` và `Character.isWhitespace` giống nhau | Nhận diện sai tập ký tự trắng | Áp dụng đúng `Character.isWhitespace` theo API `String.strip` |
| Dùng `strip()` để làm sạch trailing whitespace từng dòng trong văn bản nhiều dòng | Chỉ phần cuối **toàn chuỗi** bị xử lý | Duyệt dòng theo quy tắc mong muốn |
| Gọi `strip()` trên `null` | `NullPointerException` | Kiểm tra null hoặc xác định trước hợp đồng đầu vào |
| Dùng `strip()` để đo chuỗi có rỗng hay không mà không quan tâm kết quả | Cấp phát/xử lý không cần thiết trong một số ca | Dùng `isBlank()` khi chỉ cần `boolean` |
| Giả định mọi phiên bản Java đều có ba API | Không biên dịch trên Java 8 | Nâng JDK lên 11+ hoặc viết cách xử lý phù hợp với phiên bản cũ |

## 10. Bài tập tự kiểm tra

Cho dữ liệu:

```java
String a = "\t abc \t";
String b = "\u2003abc\u2003";
String c = "\u00A0abc\u00A0";
String d = "   ";
String e = " a  b ";
```

Hãy dự đoán:

1. `a.strip()`, `a.stripLeading()` và `a.stripTrailing()` giữ lại phía nào?
2. Vì sao `b.strip().equals("abc")` là `true` trong khi `b.trim().equals("abc")` là `false`?
3. `c.strip().equals("abc")` có trả về `true` không?
4. `d.strip().isEmpty()` và `d.isBlank()` có cùng kết quả không?
5. `e.strip()` còn mấy dấu cách ở giữa `a` và `b`?

**Đáp án:**

1. `a.strip()` là `"abc"`; `stripLeading()` là `"abc \t"`, `stripTrailing()` là `"\t abc"`.
2. `U+2003` được Java nhận diện là whitespace; `trim()` chỉ loại đơn vị mã `<= U+0020`.
3. **Không**. NBSP `U+00A0` không thuộc nhóm được `Character.isWhitespace` chấp nhận, nên vẫn ở đầu và cuối.
4. Cả hai biểu thức đều là `true`; `strip()` trả chuỗi rỗng, `isBlank()` trực tiếp kiểm tra chỉ chứa whitespace.
5. Vẫn còn **hai** dấu cách ở giữa vì `strip()` không sửa nội dung bên trong.

## 11. Tài liệu chính thức

- [Java SE 17 — `String.strip()`, `stripLeading()`, `stripTrailing()`, `trim()` và `isBlank()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html)
- [Java SE 17 — `Character.isWhitespace(int)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html#isWhitespace(int))

**Ghi nhớ:** Dùng `strip()` để bỏ Java whitespace ở **hai mép**, `stripLeading()` chỉ ở **đầu**, `stripTrailing()` chỉ ở **cuối**. Chúng dùng quy tắc Unicode `Character.isWhitespace`, không đồng nghĩa “mọi ký tự trông trắng”, và không sửa khoảng trắng ở giữa. So với `trim()`, sự khác nhau nằm ở **tập ký tự được coi là có thể loại bỏ**, không chỉ ở tên phương thức.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-whitespace-004">Character.isWhitespace và giới hạn với khoảng trắng Unicode</a>

<details>
<summary>Xem chi tiết</summary>

# Character.isWhitespace và giới hạn với khoảng trắng Unicode trong Java

Các chuỗi `" "`, `"\t"`, `"\u2003"` và `"\u00A0"` có thể đều trông giống khoảng trắng, nhưng Java **không coi chúng giống nhau trong mọi API**. Khi cần kiểm tra chuỗi rỗng về mặt nội dung, xóa khoảng trắng đầu/cuối hay chuẩn hóa văn bản đa ngôn ngữ, cần hiểu chính xác `Character.isWhitespace()`, phân biệt nó với `Character.isSpaceChar()` và tập **Unicode White_Space**.

Điểm quan trọng nhất của bài:

> **`Character.isWhitespace()` không nhận diện mọi ký tự thuộc Unicode White_Space.** Đặc biệt, các khoảng trắng không ngắt dòng như NO-BREAK SPACE (`U+00A0`), FIGURE SPACE (`U+2007`) và NARROW NO-BREAK SPACE (`U+202F`) được `Character.isSpaceChar()` nhận diện nhưng **không** được `Character.isWhitespace()` nhận diện. Vì `String.isBlank()` và `String.strip()` dựa trên định nghĩa whitespace của Java, chúng cũng có giới hạn tương ứng.

## 1. Ba khái niệm không thể dùng thay nhau

| Khái niệm/API | Kiểm tra điều gì? | Điểm cần lưu ý |
| --- | --- | --- |
| `Character.isWhitespace(int codePoint)` | Theo quy tắc “Java whitespace” trong API `Character` | Có các dấu phân cách Unicode nhất định và ký tự điều khiển như tab/newline, nhưng **loại trừ ba khoảng trắng không ngắt dòng** nêu trên |
| `Character.isSpaceChar(int codePoint)` | Unicode category `SPACE_SEPARATOR` (`Zs`), `LINE_SEPARATOR` (`Zl`), `PARAGRAPH_SEPARATOR` (`Zp`) | Có thể nhận diện khoảng trắng không ngắt dòng nhưng **không** nhận diện tab hay newline là “space character” |
| Unicode binary property `White_Space` | Thuộc tính Unicode dùng trong nhiều thuật toán phân loại và regex Unicode | Bao gồm những ký tự như NBSP và NEXT LINE (`U+0085`); cũng khác với `isWhitespace`/`isSpaceChar` riêng lẻ |

`Character.isWhitespace()` có hai overload chính:

~~~java
Character.isWhitespace(char ch);
Character.isWhitespace(int codePoint);
~~~

Đây là **chữ ký phương thức minh họa**, không phải chương trình Java độc lập. Khi dữ liệu là Unicode code point kiểu `int`, ưu tiên overload nhận `int`. Overload nhận `char` chỉ xét một **UTF-16 code unit**; nếu nhận một phần của surrogate pair, nó không phải toàn bộ code point. Với các ký tự whitespace thường gặp, chúng nằm trong BMP, nhưng thói quen duyệt code point giúp tránh lỗi khi mở rộng xử lý Unicode.

### Quy tắc Character.isWhitespace(int)

Một code point được coi là “Java whitespace” khi nó thuộc các nhóm:

1. Unicode `SPACE_SEPARATOR`, `LINE_SEPARATOR` hoặc `PARAGRAPH_SEPARATOR`, **trừ** ba ký tự `U+00A0`, `U+2007`, `U+202F`.
2. Ký tự điều khiển `U+0009` (tab), `U+000A` (line feed), `U+000B` (vertical tab), `U+000C` (form feed), `U+000D` (carriage return).
3. Ký tự điều khiển `U+001C`, `U+001D`, `U+001E`, `U+001F`.

Đây là **định nghĩa cụ thể của Java**, không phải một lời hứa rằng mọi thứ trông trống trên màn hình, mọi ký tự “invisible”, hay mọi thành viên của Unicode White_Space sẽ trả về `true`. Phân loại Unicode có thể thay đổi khi cập nhật dữ liệu Unicode của JDK; mã nguồn nên căn cứ vào hợp đồng API và phiên bản nền tảng đang chạy.

## 2. Bảng so sánh các code point quan trọng

| Code point | Tên hoặc ý nghĩa | `isWhitespace` | `isSpaceChar` | Lý do/ghi chú |
| --- | --- | :---: | :---: | --- |
| `U+0009` | CHARACTER TABULATION (tab) | **true** | false | Ký tự điều khiển được Java coi là whitespace |
| `U+000A` | LINE FEED | **true** | false | Ký tự xuống dòng |
| `U+001C` | FILE SEPARATOR | **true** | false | Java đặc biệt đưa vào nhóm whitespace |
| `U+0020` | SPACE thông thường | **true** | **true** | Khoảng trắng ASCII |
| `U+00A0` | NO-BREAK SPACE (NBSP) | **false** | **true** | Khoảng trắng không ngắt dòng; phản ví dụ quan trọng |
| `U+1680` | OGHAM SPACE MARK | **true** | **true** | Dấu cách Unicode |
| `U+2003` | EM SPACE | **true** | **true** | Khoảng trắng rộng trong typography |
| `U+2007` | FIGURE SPACE | **false** | **true** | Khoảng trắng không ngắt dòng |
| `U+2028` | LINE SEPARATOR | **true** | **true** | Loại `Zl` |
| `U+2029` | PARAGRAPH SEPARATOR | **true** | **true** | Loại `Zp` |
| `U+202F` | NARROW NO-BREAK SPACE | **false** | **true** | Khoảng trắng hẹp không ngắt dòng |
| `U+205F` | MEDIUM MATHEMATICAL SPACE | **true** | **true** | Khoảng trắng Unicode |
| `U+3000` | IDEOGRAPHIC SPACE | **true** | **true** | Dùng trong một số ngữ cảnh chữ Đông Á |
| `U+0085` | NEXT LINE (NEL) | **false** | false | Thuộc Unicode White_Space, nhưng **không** được hai API này nhận diện |
| `U+200B` | ZERO WIDTH SPACE | **false** | false | Tên có chữ “SPACE” nhưng **không** thuộc Unicode White_Space |
| `U+FEFF` | ZERO WIDTH NO-BREAK SPACE / BOM | **false** | false | Thường gặp trong xử lý BOM; không phải whitespace theo hai API |

Bảng trên được mô tả cho Java hiện đại (ví dụ JDK 17+). Điểm phân biệt đáng nhớ:

- **NBSP**, **FIGURE SPACE** và **NARROW NO-BREAK SPACE**: `isSpaceChar == true` nhưng `isWhitespace == false`.
- **Tab** và **line feed**: `isWhitespace == true` nhưng `isSpaceChar == false`.
- **NEXT LINE U+0085**: Unicode White_Space nhận diện, nhưng cả `isWhitespace` và `isSpaceChar` đều `false`.
- **ZERO WIDTH SPACE U+200B**: vô hình trong nhiều môi trường, nhưng **không** thuộc Unicode White_Space; đừng tự động coi mọi ký tự vô hình là dấu cách.

Điều này giải thích vì sao phép kiểm tra `Character.isWhitespace(cp) || Character.isSpaceChar(cp)` có thể bao phủ nhiều dấu cách hơn từng API riêng, nhưng **vẫn không tương đương với toàn bộ thuộc tính Unicode White_Space** (vì thiếu `U+0085`).

## 3. Ảnh hưởng tới String.isBlank(), strip() và trim()

Java có nhiều phương thức tên gần giống nhưng tiêu chí khác nhau:

| Phương thức | Quy tắc | Tác động với NBSP `U+00A0` |
| --- | --- | --- |
| `String.isEmpty()` | Chỉ `length() == 0` | `"\u00A0".isEmpty()` là `false` |
| `String.isBlank()` (Java 11+) | Rỗng hoặc chỉ có code point được Java coi là whitespace | `"\u00A0".isBlank()` là `false` |
| `String.strip()` (Java 11+) | Bỏ whitespace theo định nghĩa `Character.isWhitespace` ở **đầu và cuối** chuỗi | Không bỏ NBSP ở đầu/cuối |
| `String.stripLeading()` | Chỉ bỏ Java whitespace ở đầu chuỗi | Không bỏ NBSP đầu |
| `String.stripTrailing()` | Chỉ bỏ Java whitespace ở cuối chuỗi | Không bỏ NBSP cuối |
| `String.trim()` | Bỏ các `char` ở hai đầu có giá trị **không vượt `U+0020`** | Không bỏ NBSP; đồng thời không bỏ EM SPACE `U+2003` |

Một số hệ quả:

1. `"".isEmpty()` và `"".isBlank()` đều `true`; nhưng `" \t".isEmpty()` là `false` trong khi `" \t".isBlank()` là `true`.
2. `"\u2003".isBlank()` là `true` vì EM SPACE được `Character.isWhitespace` nhận diện.
3. `"\u00A0".isBlank()` là `false` dù NBSP thường hiển thị giống một chỗ trống.
4. `"\u200B".isBlank()` là `false`: ZERO WIDTH SPACE không tự động là Java whitespace.
5. `trim()` là phép cắt theo ngưỡng code unit lịch sử, khác với `strip()`. Vì vậy `trim()` cũng không phải bộ lọc “toàn bộ whitespace Unicode”.

**Không dùng `strip().isEmpty()` với kỳ vọng xóa mọi loại khoảng trắng Unicode.** Với chuỗi chỉ chứa NBSP, kết quả `strip()` vẫn chứa NBSP và `isEmpty()` là `false`; điều này phù hợp với `isBlank()` của Java.

## 4. Ví dụ Java 17+ chạy độc lập

Lưu đoạn mã sau trong tệp **JavaWhitespaceDemo.java**:

~~~java
public class JavaWhitespaceDemo {
    private static void inspect(int codePoint) {
        String text = new String(Character.toChars(codePoint));
        System.out.printf(
                "U+%04X whitespace=%s spaceChar=%s blank=%s%n",
                codePoint,
                Character.isWhitespace(codePoint),
                Character.isSpaceChar(codePoint),
                text.isBlank());
    }

    public static void main(String[] args) {
        inspect(0x0020); // SPACE
        inspect(0x0009); // TAB
        inspect(0x00A0); // NO-BREAK SPACE
        inspect(0x2003); // EM SPACE
        inspect(0x2007); // FIGURE SPACE
        inspect(0x202F); // NARROW NO-BREAK SPACE
        inspect(0x0085); // NEXT LINE
        inspect(0x200B); // ZERO WIDTH SPACE
        inspect(0x3000); // IDEOGRAPHIC SPACE

        String nbsp = "\u00A0X\u00A0";
        String emSpace = "\u2003X\u2003";

        System.out.println("NBSP trim length = " + nbsp.trim().length());
        System.out.println("NBSP strip length = " + nbsp.strip().length());
        System.out.println("EM SPACE trim length = "
                + emSpace.trim().length());
        System.out.println("EM SPACE strip length = "
                + emSpace.strip().length());
        System.out.println("Only tab isBlank = " + "\t".isBlank());
        System.out.println("Only NBSP isBlank = " + "\u00A0".isBlank());

        System.out.println("NBSP Unicode-regex whitespace = "
                + "\u00A0".matches("(?U)\\s"));
        System.out.println("NEL Unicode-regex whitespace = "
                + "\u0085".matches("(?U)\\s"));
        System.out.println("ZWSP Unicode-regex whitespace = "
                + "\u200B".matches("(?U)\\s"));

        String unicodeStripped =
                nbsp.replaceAll("(?U)\\A\\s+|\\s+\\z", "");
        System.out.println("Unicode regex strip result = ["
                + unicodeStripped + "]");
    }
}
~~~

Biên dịch và chạy bằng Java 17 trở lên:

~~~bash
javac -encoding UTF-8 JavaWhitespaceDemo.java
java JavaWhitespaceDemo
~~~

**Stdout:**

~~~text
U+0020 whitespace=true spaceChar=true blank=true
U+0009 whitespace=true spaceChar=false blank=true
U+00A0 whitespace=false spaceChar=true blank=false
U+2003 whitespace=true spaceChar=true blank=true
U+2007 whitespace=false spaceChar=true blank=false
U+202F whitespace=false spaceChar=true blank=false
U+0085 whitespace=false spaceChar=false blank=false
U+200B whitespace=false spaceChar=false blank=false
U+3000 whitespace=true spaceChar=true blank=true
NBSP trim length = 3
NBSP strip length = 3
EM SPACE trim length = 3
EM SPACE strip length = 1
Only tab isBlank = true
Only NBSP isBlank = false
NBSP Unicode-regex whitespace = true
NEL Unicode-regex whitespace = true
ZWSP Unicode-regex whitespace = false
Unicode regex strip result = [X]
~~~

**Giải thích:**

- NBSP không được coi là Java whitespace nên cả `trim()` lẫn `strip()` đều giữ nguyên hai ký tự NBSP hai bên `X`; chiều dài vẫn bằng 3.
- EM SPACE vượt ngưỡng `U+0020` của `trim()` nên `trim()` không loại bỏ; `strip()` nhận diện theo `isWhitespace()` nên trả về `"X"` có độ dài 1.
- Regex `(?U)\\s` bật chế độ `UNICODE_CHARACTER_CLASS`, khi đó `\s` dùng định nghĩa whitespace theo Unicode và khớp cả NBSP lẫn NEXT LINE. Nó không khớp ZERO WIDTH SPACE vì ký tự đó không thuộc Unicode White_Space.
- Trong biểu thức `"(?U)\\A\\s+|\\s+\\z"`, `\A` và `\z` neo tại đầu và cuối toàn bộ chuỗi; hai nhánh thay thế loại các khoảng Unicode White_Space liên tiếp ở **đầu hoặc cuối**, giữ nội dung bên trong. Đây là chính sách khác với `String.strip()`.

Các ký tự `\u00A0` và `\u2003` trong Java source được viết bằng escape Unicode để bài học dễ nhìn. Khi xuất ra terminal, không cần in trực tiếp khoảng trắng vô hình để quan sát vì các giá trị Boolean và chiều dài đã cho thấy sự khác biệt.

## 5. Regex \s cũng có hai chế độ đáng chú ý

Trong Java regex, `\s` không nên được hiểu máy móc là “toàn bộ dấu cách Unicode” trong mọi cấu hình.

**Chế độ mặc định** (không bật `UNICODE_CHARACTER_CLASS`): `\s` có tập ký tự whitespace truyền thống như SPACE, TAB, LINE FEED, VERTICAL TAB, FORM FEED và CARRIAGE RETURN. Điều đó không bao gồm mọi khoảng trắng Unicode.

**Chế độ Unicode**, dùng cờ `Pattern.UNICODE_CHARACTER_CLASS` hoặc cờ nội tuyến `(?U)`: `\s` sử dụng cách phân loại Unicode cho whitespace và nhận ra thêm NBSP, NARROW NO-BREAK SPACE, FIGURE SPACE, NEXT LINE cùng các khoảng trắng Unicode khác.

Ví dụ đoạn mã sau **đặt bên trong một phương thức**:

~~~java
String nbsp = "\u00A0";
System.out.println(nbsp.matches("\\s"));     // false
System.out.println(nbsp.matches("(?U)\\s")); // true
~~~

Nếu muốn regex có cờ tường minh ở mức đối tượng:

~~~java
java.util.regex.Pattern unicodeSpaces =
        java.util.regex.Pattern.compile(
                "\\s+",
                java.util.regex.Pattern.UNICODE_CHARACTER_CLASS);
System.out.println(unicodeSpaces.matcher("\u00A0").matches()); // true
~~~

Hai đoạn trên là **mã minh họa**, không phải chương trình độc lập. Với tác vụ phức tạp hoặc xử lý nhiều chuỗi, có thể biên dịch `Pattern` một lần và dùng lại. Chọn regex Unicode khi nghiệp vụ yêu cầu Unicode White_Space, thay vì ngầm đổi quy tắc toàn ứng dụng.

**Cảnh báo về mục tiêu:** Thuộc tính White_Space của Unicode vẫn **không** phải “mọi ký tự vô hình”. Chẳng hạn ZERO WIDTH SPACE (`U+200B`), ZERO WIDTH JOINER (`U+200D`), BOM/`U+FEFF` không được phép tùy tiện loại bỏ như khoảng trắng thông thường. Một số ký tự này có tác dụng định dạng văn bản, kết hợp emoji hoặc thông tin tệp.

## 6. Làm thế nào nếu muốn loại bỏ cả NBSP?

Trước khi viết hàm loại khoảng trắng, hãy xác định **định nghĩa đầu vào** của ứng dụng:

- Với một trường dữ liệu chỉ cần theo quy tắc whitespace của Java, `String.isBlank()` và `strip()` thường đủ rõ ràng.
- Với dữ liệu web, tài liệu văn bản hoặc CSV có thể chứa NBSP/NNBSP, hãy quy định rõ có coi chúng là khoảng trắng cần loại không. Nếu muốn **Unicode White_Space** theo regex Java, có thể dùng `(?U)\\s`.
- Nếu mục tiêu là bảo toàn khoảng trắng không ngắt dòng trong văn bản hiển thị (ví dụ đơn vị số, tên riêng có định dạng), **không** nên xóa NBSP chỉ vì nó trông như space. NBSP có ý nghĩa cấm ngắt dòng ở vị trí đó.
- Nếu ứng dụng cần xử lý Unicode theo một phiên bản chuẩn xác tuyệt đối giữa nhiều môi trường, hãy quản lý phiên bản Unicode/JDK hoặc thư viện phân loại phù hợp; không mặc định mọi JDK luôn có cùng bảng thuộc tính cho mã mới.

### Ví dụ hàm cắt Unicode White_Space ở hai đầu

~~~java
static String stripUnicodeWhiteSpace(String input) {
    java.util.Objects.requireNonNull(input, "input");
    return input.replaceAll("(?U)\\A\\s+|\\s+\\z", "");
}
~~~

Đây là **phương thức minh họa**, cần đặt trong một lớp Java. Hàm cắt theo chính sách Unicode regex, có thể bỏ NBSP và NEXT LINE hai bên, nhưng không có ý định xóa mọi ký tự format/zero-width. Nó không thay thế `strip()` một cách mặc định cho mọi ứng dụng: hai hàm có **hợp đồng khác nhau**.

Nếu muốn phát hiện “toàn bộ chuỗi chỉ là Unicode White_Space” theo chính sách này, có thể dùng:

~~~java
static boolean isUnicodeWhiteSpaceOnly(String input) {
    java.util.Objects.requireNonNull(input, "input");
    return input.isEmpty() || input.matches("(?U)\\s+");
}
~~~

Đây là phương thức minh họa; với chuỗi rỗng trả về `true` tương tự `String.isBlank()`. Mẫu `(?U)\\s+` yêu cầu **một hoặc nhiều** code point thuộc Unicode White_Space, còn điều kiện `isEmpty()` xử lý riêng chuỗi rỗng. Nếu yêu cầu tối ưu cho nhiều lần gọi, dùng `Pattern` đã biên dịch thay vì tạo regex cho mỗi lời gọi.

### Trường hợp đặc biệt: xử lý dữ liệu “trông trống”

Một ô dữ liệu từ web chứa NBSP có thể **không rỗng theo `isBlank()`**; đây là điều dễ gây lỗi khi kiểm tra đầu vào. Nhưng một mã zero-width như `U+200B` còn có thể vô hình mà **không thuộc cả Java whitespace lẫn Unicode White_Space**. Do đó, câu “ô nhập trông trống” có thể cần **một chính sách riêng** về ký tự format, dấu điều khiển, chuẩn hóa và an toàn dữ liệu. Đừng âm thầm áp dụng `replaceAll("\\s+", "")` rồi cho rằng đã làm sạch mọi thứ.

Ví dụ, biến đổi `replaceAll("(?U)\\s+", " ")` gom các nhóm Unicode White_Space thành một dấu cách ASCII và có thể **thay đổi ý nghĩa trình bày** (mất NBSP ngăn ngắt dòng, mất các dấu xuống dòng). Nếu ứng dụng cần giữ cấu trúc đoạn văn hay layout, quy tắc ấy quá mạnh. Nên xác định rõ ngữ cảnh dữ liệu trước khi chọn bước chuẩn hóa.

## 7. UTF-16 và overload char/int: khi duyệt Unicode

Vì `String` đánh chỉ số theo UTF-16, một code point ngoài BMP có thể cần hai giá trị `char`. Khi kiểm tra từng Unicode code point nói chung, dùng:

~~~java
String text = "A😀\u00A0B";
for (int i = 0; i < text.length(); ) {
    int cp = text.codePointAt(i);
    System.out.printf("U+%04X whitespace=%s%n",
            cp, Character.isWhitespace(cp));
    i += Character.charCount(cp);
}
~~~

Đây là **đoạn mã đặt trong một phương thức Java**, không phải lớp độc lập. Kết quả:

~~~text
U+0041 whitespace=false
U+1F600 whitespace=false
U+00A0 whitespace=false
U+0042 whitespace=false
~~~

Phép duyệt `codePointAt(i)` và tăng `Character.charCount(cp)` tránh lặp qua từng nửa surrogate của emoji. Với mỗi code point, hàm nhận `int` áp dụng tiêu chí Java whitespace. Vì vậy, dù xử lý Unicode bằng code point chính xác, NBSP **vẫn** bị `isWhitespace()` xem là `false`: đây là quyết định phân loại của API, không phải lỗi vì duyệt bằng UTF-16.

Để xử lý code point bằng Stream, có thể dùng `text.codePoints().allMatch(Character::isWhitespace)`, nhưng biểu thức này cũng dùng **đúng tiêu chí Java whitespace**, không tự mở rộng sang Unicode White_Space. Với chuỗi rỗng, `allMatch` cho `true` theo quy tắc của stream rỗng.

## 8. Những nhầm lẫn phổ biến

| Nhận định hoặc cách làm | Vấn đề | Điều nên làm |
| --- | --- | --- |
| “Unicode space thì `Character.isWhitespace` luôn `true`” | NBSP, FIGURE SPACE, NNBSP là phản ví dụ | Kiểm tra chính sách: Java whitespace, `isSpaceChar` hay Unicode White_Space |
| “`isWhitespace` và `isSpaceChar` là hai tên của một hàm” | Tab chỉ thuộc `isWhitespace`; NBSP chỉ thuộc `isSpaceChar` | Hiểu hai hợp đồng trước khi dùng |
| “`strip()` xóa mọi khoảng trắng mà mắt nhìn thấy” | `strip()` không xóa NBSP/NNBSP | Dùng quy tắc rõ ràng cho Unicode whitespace nếu cần |
| “`trim()` tương đương `strip()`” | `trim()` dựa trên ngưỡng `U+0020`, còn `strip()` dựa trên `isWhitespace` | Chọn theo phạm vi ký tự cần xử lý |
| “Chuỗi chứa NBSP luôn là `isBlank() == true`” | NBSP không phải Java whitespace | Kiểm tra riêng hoặc dùng Unicode regex khi nghiệp vụ yêu cầu |
| “Regex `\\s` luôn khớp mọi Unicode whitespace” | Chế độ mặc định khác chế độ Unicode | Chọn `(?U)\\s` hoặc `UNICODE_CHARACTER_CLASS` |
| “Ký tự zero-width nào cũng nên xóa” | Nhiều ký tự có vai trò format, emoji, BOM | Xác định chính sách xử lý ký tự vô hình |
| “Kết hợp `isWhitespace || isSpaceChar` đúng bằng Unicode White_Space” | Unicode White_Space còn có NEXT LINE `U+0085` | Dùng property/regex Unicode khi cần tập Unicode tương ứng |
| “`charAt()` luôn trả về một code point hoàn chỉnh” | Code point ngoài BMP có cặp surrogate | Dùng `codePointAt()` và bước nhảy theo `Character.charCount()` |
| “Một đoạn `String` toàn space sẽ luôn có `length() == 0` sau `strip()`” | Không đúng với NBSP, NNBSP, FIGURE SPACE | Kiểm thử dữ liệu thực tế, theo tiêu chí cụ thể |

## 9. Checklist thiết kế thao tác kiểm tra chuỗi trắng

1. **Xác định ý nghĩa của “trắng”:** hoàn toàn không có ký tự (`isEmpty`), chỉ Java whitespace (`isBlank`), Unicode White_Space, hay “không có ký tự hiển thị” theo tiêu chí của giao diện?
2. **Nhận diện nguồn dữ liệu:** bàn phím, HTML, bản sao từ tài liệu, tệp UTF-8/UTF-16, dữ liệu nhập từ đối tác; nguồn web và tài liệu thường có NBSP.
3. **Chọn đúng API:** `Character.isWhitespace` cho tiêu chí Java; `Character.isSpaceChar` cho các Unicode separator; regex Unicode White_Space khi cần bao gồm NBSP/NEL.
4. **Xác định phạm vi chuẩn hóa:** chỉ cắt đầu/cuối hay thay toàn bộ khoảng trắng bên trong? Giữ dòng mới và NBSP khi chúng có ý nghĩa.
5. **Kiểm tra chuỗi rỗng và `null`:** `"".isBlank()` trả `true`, còn gọi phương thức lên tham chiếu `null` sẽ ném `NullPointerException`.
6. **Viết test theo code point**, không dựa vào độ rộng hiển thị: `U+0020`, `U+0009`, `U+00A0`, `U+2003`, `U+2007`, `U+202F`, `U+0085`, `U+200B` và `U+FEFF`.
7. **Ghi rõ chính sách của ứng dụng** để người duy trì hiểu vì sao `strip()` hoặc Unicode regex được chọn, tránh tự ý thay đổi sau này.

## 10. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao `Character.isWhitespace('\u00A0')` trả về `false`, dù NBSP trông giống dấu cách?

**Đáp án:** NBSP là khoảng trắng **không ngắt dòng**, thuộc nhóm separator nhưng được **loại trừ** khỏi tiêu chí Java whitespace. `Character.isSpaceChar('\u00A0')` vẫn trả về `true`.

**Câu 2.** `"\u2003".isBlank()` và `"\u00A0".isBlank()` trả về gì?

**Đáp án:** lần lượt là `true` và `false`: EM SPACE được `isWhitespace` nhận diện, còn NBSP bị loại trừ.

**Câu 3.** `"\u2003X\u2003".strip()` và `"\u2003X\u2003".trim()` khác nhau thế nào?

**Đáp án:** `strip()` trả `"X"`; `trim()` giữ nguyên hai EM SPACE vì code unit `U+2003` vượt ngưỡng `U+0020`.

**Câu 4.** Bật `(?U)` trong Java regex `\\s` có mục đích gì?

**Đáp án:** bật `UNICODE_CHARACTER_CLASS` để `\\s` dùng tập Unicode White_Space rộng hơn, bao gồm NBSP và NEXT LINE.

**Câu 5.** ZERO WIDTH SPACE `U+200B` có được xem là whitespace theo hai API Java trên hoặc theo Unicode White_Space không?

**Đáp án:** không. Ký tự có thể vô hình nhưng tên gọi/hình thức hiển thị không đủ để kết luận thuộc một tập whitespace.

**Câu 6.** Nếu ứng dụng cần bỏ khoảng Unicode White_Space đầu/cuối, dùng `String.strip()` có đáp ứng hoàn toàn không?

**Đáp án:** không. `strip()` dùng Java whitespace, nên giữ NBSP/NNBSP/FIGURE SPACE. Có thể dùng regex Unicode với `(?U)\\s` và neo đầu/cuối khi đó đúng yêu cầu nghiệp vụ.

## Tài liệu tham khảo

- [Java SE 17 — Character](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html): hợp đồng chính thức của `isWhitespace(char/int)` và `isSpaceChar(char/int)`.
- [Java SE 17 — String](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html): `isBlank()`, `strip()`, `stripLeading()`, `stripTrailing()`, `trim()`, `codePoints()`.
- [Java SE 17 — Pattern](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html): `\\s`, `UNICODE_CHARACTER_CLASS`, `(?U)` và Unicode properties.
- [Unicode Character Database — PropList.txt](https://www.unicode.org/Public/UCD/latest/ucd/PropList.txt): danh sách Unicode binary property `White_Space`; dữ liệu có thể khác theo phiên bản Unicode.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-whitespace-005">Khoảng trắng không ngắt dòng không luôn được strip loại bỏ</a>

<details>
<summary>Xem chi tiết</summary>

# Khoảng trắng không ngắt dòng không luôn được `String.strip()` loại bỏ

## 1. Mục tiêu và hiện tượng cần nhớ

Trong Java 11+ (bao gồm Java 17), `String.strip()` xóa các ký tự được `Character.isWhitespace(...)` phân loại là khoảng trắng **ở đầu và cuối chuỗi**. Tên phương thức dễ làm ta hiểu nhầm rằng nó xóa **mọi** loại ký tự trông như dấu cách. Thực tế, một số **dấu cách không ngắt dòng** (*non-breaking spaces*) vẫn được giữ nguyên.

Ví dụ:

```java
String text = "\u00A0Java\u00A0"; // NBSP + Java + NBSP
System.out.println(text.strip().equals(text)); // true
System.out.println(text.isBlank());             // false
```

Hai dấu cách hai bên có mã Unicode **U+00A0 NO-BREAK SPACE**, không phải dấu cách ASCII U+0020. `strip()` giữ nguyên chúng, còn `isBlank()` không coi một chuỗi chỉ chứa NBSP là “blank”.

Bài học này giúp bạn:
- Phân biệt **dấu cách nhìn thấy được**, **Unicode space character** và các ký tự mà Java xem là `whitespace`.
- Hiểu sự khác nhau giữa `strip()`, `trim()`, `isBlank()` và `Character.isSpaceChar()`.
- Xử lý đúng NBSP, FIGURE SPACE, NARROW NO-BREAK SPACE khi làm sạch văn bản.
- Chọn chính sách loại bỏ khoảng trắng phù hợp để không làm thay đổi ý nghĩa của dữ liệu.

## 2. Dấu cách không ngắt dòng là gì?

Trong văn bản được dàn trang, một số khoảng trắng có mục đích **giữ hai phần văn bản trên cùng một dòng**. Ví dụ, khoảng giữa số và đơn vị trong `"10 kg"` đôi khi được viết bằng NBSP để tránh xuống dòng giữa `10` và `kg`.

Ba code point thường gặp:

| Code point | Tên Unicode | Đặc điểm |
| --- | --- | --- |
| `U+00A0` | NO-BREAK SPACE (NBSP) | Khoảng trắng không ngắt dòng thông dụng; HTML có thực thể `&nbsp;`. |
| `U+2007` | FIGURE SPACE | Khoảng trắng có bề rộng liên quan chữ số, không ngắt dòng. |
| `U+202F` | NARROW NO-BREAK SPACE | Khoảng trắng không ngắt dòng có bề rộng hẹp hơn; xuất hiện trong một số quy ước dàn trang. |

Chúng thường khó phân biệt với dấu cách thường bằng mắt. Khi sao chép từ HTML, tài liệu PDF, trình soạn thảo hoặc nội dung đã được dàn trang, chuỗi có thể chứa các code point này dù người dùng nghĩ chỉ có “space”.

**Lưu ý:** Các ký tự này là dữ liệu văn bản có ý nghĩa. Tùy nghiệp vụ, việc tự ý xóa hoặc đổi toàn bộ NBSP thành dấu cách thường có thể phá vỡ ý định trình bày hoặc quy tắc định dạng.

## 3. Vì sao `strip()` không loại bỏ mọi dấu cách?

Theo hợp đồng của Java, `String.strip()` xác định ký tự cần loại bỏ bằng **`Character.isWhitespace(int)`**. Trong Java 17, `Character.isWhitespace` trả về `false` đối với ba ký tự không ngắt dòng `U+00A0`, `U+2007` và `U+202F`.

Điều này khác với `Character.isSpaceChar(int)`, vốn trả về `true` cho các ký tự thuộc nhóm Unicode space separators, bao gồm ba trường hợp trên.

Bảng kết quả đáng chú ý trên Java 17:

| Ký tự | `Character.isWhitespace(cp)` | `Character.isSpaceChar(cp)` | `strip()` có thể loại bỏ ở đầu/cuối? |
| --- | --- | --- | --- |
| `U+0020` SPACE | `true` | `true` | Có |
| `U+0009` TAB | `true` | `false` | Có |
| `U+00A0` NO-BREAK SPACE | `false` | `true` | **Không** |
| `U+2007` FIGURE SPACE | `false` | `true` | **Không** |
| `U+202F` NARROW NO-BREAK SPACE | `false` | `true` | **Không** |
| `U+2003` EM SPACE | `true` | `true` | Có |
| `U+3000` IDEOGRAPHIC SPACE | `true` | `true` | Có |

Sự khác biệt thể hiện rõ qua ba đoạn mã:

```java
System.out.println("\u2003Java\u2003".strip().equals("Java")); // true
System.out.println("\u00A0Java\u00A0".strip().equals("Java")); // false
System.out.println("\u00A0Java\u00A0".strip()
        .equals("\u00A0Java\u00A0"));                       // true
```

Không thể suy ra rằng `strip()` sẽ loại bỏ một ký tự chỉ vì tên Unicode của nó có từ “SPACE” hoặc vì nó hiển thị giống một khoảng trắng.

### Một trường hợp dễ bất ngờ: hỗn hợp SPACE và NBSP

```java
String mixed = " \u00A0Java\u00A0 ";
String result = mixed.strip();

System.out.println(result.equals("\u00A0Java\u00A0")); // true
```

`strip()` bỏ dấu cách ASCII `U+0020` ngoài cùng. Sau đó, khi gặp NBSP `U+00A0` ở đầu và cuối vùng còn lại, nó **dừng lại** vì NBSP không thỏa `Character.isWhitespace`. Bởi vậy, một chuỗi đã gọi `strip()` vẫn có thể bắt đầu hoặc kết thúc bằng dấu cách không ngắt dòng.

## 4. Phân biệt các phép kiểm tra khi gặp NBSP

`strip()` chỉ cắt các code point được `Character.isWhitespace` công nhận ở hai mép; `stripLeading()` và `stripTrailing()` chỉ cắt một phía. `isBlank()` kiểm tra toàn chuỗi nhưng cùng tiêu chí Java whitespace, vì vậy cả `strip()` lẫn `isBlank()` **không coi NBSP là khoảng trắng có thể bỏ qua**. `trim()` lại cắt các đơn vị mã `char <= U+0020`, nên cũng giữ NBSP.

~~~java
String nbsp = "\u00A0";
String em = "\u2003";
System.out.println(nbsp.strip().isEmpty()); // false
System.out.println(nbsp.isBlank());         // false
System.out.println(em.strip().isEmpty());   // true
System.out.println(em.trim().isEmpty());    // false
~~~

Hai cặp kết quả giúp phân biệt **khoảng trắng không ngắt dòng** với EM SPACE và giới hạn riêng của `trim()`. Nếu chỉ cần phát hiện dữ liệu rỗng hoặc chỉ có *Java whitespace*, dùng `isBlank()`; nếu yêu cầu chấp nhận hoặc cắt thêm NBSP ở biên, áp dụng predicate theo hợp đồng nghiệp vụ như ở mục 7.

## 5. Ví dụ Java 17+ chạy độc lập, hiện rõ code point

Vì NBSP khó nhìn thấy, ví dụ dưới đây **in mã Unicode** thay vì chỉ in khoảng trắng để có kết quả kiểm chứng được.

Lưu thành file `NonBreakingSpaceDemo.java`:

```java
import java.util.stream.Collectors;

public class NonBreakingSpaceDemo {
    static String codePoints(String text) {
        return text.codePoints()
                .mapToObj(cp -> String.format("U+%04X", cp))
                .collect(Collectors.joining(" "));
    }

    public static void main(String[] args) {
        String ascii = "\t Java \n";
        String nbsp = "\u00A0Java\u00A0";
        String emSpace = "\u2003Java\u2003";
        String mixed = " \u00A0Java\u00A0 ";

        System.out.println("ASCII strip = "
                + codePoints(ascii.strip()));
        System.out.println("NBSP strip = "
                + codePoints(nbsp.strip()));
        System.out.println("EM SPACE strip = "
                + codePoints(emSpace.strip()));
        System.out.println("mixed strip = "
                + codePoints(mixed.strip()));

        System.out.println("NBSP only isBlank = "
                + "\u00A0".isBlank());
        System.out.println("EM SPACE only isBlank = "
                + "\u2003".isBlank());
        System.out.println("NBSP trim unchanged = "
                + nbsp.trim().equals(nbsp));

        int[] samples = {
            0x0020, 0x0009, 0x00A0,
            0x2007, 0x202F, 0x2003
        };
        for (int cp : samples) {
            System.out.printf(
                    "U+%04X whitespace=%s spaceChar=%s%n",
                    cp,
                    Character.isWhitespace(cp),
                    Character.isSpaceChar(cp));
        }
    }
}
```

Biên dịch và chạy bằng Java 17 trở lên:

```bash
javac -encoding UTF-8 NonBreakingSpaceDemo.java
java NonBreakingSpaceDemo
```

Đầu ra:

```text
ASCII strip = U+004A U+0061 U+0076 U+0061
NBSP strip = U+00A0 U+004A U+0061 U+0076 U+0061 U+00A0
EM SPACE strip = U+004A U+0061 U+0076 U+0061
mixed strip = U+00A0 U+004A U+0061 U+0076 U+0061 U+00A0
NBSP only isBlank = false
EM SPACE only isBlank = true
NBSP trim unchanged = true
U+0020 whitespace=true spaceChar=true
U+0009 whitespace=true spaceChar=false
U+00A0 whitespace=false spaceChar=true
U+2007 whitespace=false spaceChar=true
U+202F whitespace=false spaceChar=true
U+2003 whitespace=true spaceChar=true
```

**Giải thích:**
1. Chuỗi có TAB `U+0009`, SPACE `U+0020` và LINE FEED `U+000A` được `strip()` cắt phần trắng ở hai đầu, chỉ còn `Java`.
2. Chuỗi có NBSP ở hai đầu vẫn chứa hai `U+00A0` sau `strip()`.
3. EM SPACE `U+2003` được `Character.isWhitespace` nhận diện nên bị cắt.
4. Với `mixed`, `strip()` bỏ SPACE bên ngoài rồi giữ NBSP ngay sát chữ.
5. `"\u00A0".isBlank()` là `false` mặc dù trên giao diện chuỗi có thể trông như một khoảng trắng.

## 6. Khi nào nên giữ nguyên NBSP?

NBSP không phải lúc nào cũng là dữ liệu “bẩn”. Nó có thể được dùng để:
- Giữ số và đơn vị cùng dòng, chẳng hạn `"10 km"` hoặc `"25 °C"`.
- Giữ tiền tệ và giá trị cùng dòng theo quy ước trình bày.
- Giữ các phần của một tên riêng hoặc cụm từ được dàn trang có chủ đích.
- Biểu diễn khoảng trắng không ngắt dòng từ HTML qua thực thể `&nbsp;`.

Hãy phân biệt hai nhiệm vụ:

**Làm sạch input để tìm giá trị nghiệp vụ:** Nếu biểu mẫu cho phép người dùng dán dữ liệu chứa NBSP ở **đầu/cuối**, có thể chủ đích xem NBSP là khoảng trắng biên và loại bỏ nó.

**Bảo toàn nội dung văn bản:** Nếu ứng dụng xử lý nội dung trình bày, tài liệu hoặc bản ghi cần tính toàn vẹn, nên giữ các dấu cách đặc biệt, trừ khi yêu cầu nói rõ được phép chuẩn hóa chúng.

Không có phương thức duy nhất “đúng” cho cả hai mục đích. Trước khi chuẩn hóa cần trả lời: **Ta muốn bỏ khoảng trắng ở biên, hay thay đổi mọi khoảng trắng bên trong?** Việc thay thế bên trong chuỗi có thể thay đổi ý nghĩa dàn trang.

## 7. Cách bỏ cả NBSP ở ranh giới nếu nghiệp vụ yêu cầu

Nếu chính sách là “bỏ tất cả ký tự mà Java xem là whitespace **hoặc** Unicode space character ở hai đầu”, có thể kết hợp `Character.isWhitespace` và `Character.isSpaceChar`.

Ví dụ phương thức hỗ trợ Java 17:

```java
static boolean boundarySpace(int codePoint) {
    return Character.isWhitespace(codePoint)
            || Character.isSpaceChar(codePoint);
}

static String stripBoundarySpaces(String text) {
    java.util.Objects.requireNonNull(text, "text");

    int begin = 0;
    int end = text.length();

    while (begin < end) {
        int cp = text.codePointAt(begin);
        if (!boundarySpace(cp)) {
            break;
        }
        begin += Character.charCount(cp);
    }

    while (begin < end) {
        int cp = text.codePointBefore(end);
        if (!boundarySpace(cp)) {
            break;
        }
        end -= Character.charCount(cp);
    }

    return text.substring(begin, end);
}
```

Đây là **các phương thức cần đặt trong một lớp Java**, không phải chương trình độc lập có `main`. Ví dụ gọi bên trong `main`:

```java
String input = "\u00A0\u202FJava\u2007\u00A0";

System.out.println(input.strip().equals(input)); // true
System.out.println(stripBoundarySpaces(input)
        .equals("Java"));                         // true

String meaningful = "10\u00A0kg";
System.out.println(stripBoundarySpaces(meaningful)
        .equals(meaningful));                     // true
```

**Tại sao dùng `codePointAt` và `codePointBefore`?** Hai phương thức cho phép duyệt hai đầu chuỗi theo code point và dùng `Character.charCount(cp)` để tiến/lùi đúng số `char` UTF-16. Trong các ví dụ NBSP, code point nằm trong BMP, nhưng cách triển khai này thể hiện rõ đơn vị thao tác và có thể dùng cho mọi đầu vào Unicode trong phạm vi chính sách.

**Giới hạn cần hiểu:**
- `isWhitespace || isSpaceChar` là **một chính sách rõ ràng do ứng dụng chọn**; nó không phải định nghĩa duy nhất của “khoảng trắng” trong mọi tiêu chuẩn và mọi ngôn ngữ.
- Phương thức chỉ bỏ ký tự ở **hai đầu**, không xóa hay thay NBSP **ở giữa** như `"10\u00A0kg"`.
- Với văn bản chứa các surrogate đơn lẻ (chuỗi UTF-16 không hoàn chỉnh), phương thức không tự sửa các dữ liệu đó.
- Khi yêu cầu nghiêm ngặt theo một phiên bản Unicode hay tiêu chuẩn bên ngoài, nên xác định trực tiếp tập code point được cho phép và viết kiểm thử cho tập đó.

### Nếu chỉ muốn bỏ riêng NBSP thông thường ở hai đầu

Bạn có thể chọn một chính sách hẹp hơn `boundarySpace`, ví dụ chỉ coi `U+00A0` bổ sung vào những gì `strip()` vốn đã loại bỏ. Đó là lựa chọn thích hợp nếu ứng dụng cần giữ `U+2007` và `U+202F` vì mục đích dàn trang.

Không nên vô thức thay toàn bộ dấu cách đặc biệt bằng một dấu cách ASCII chỉ vì người dùng nhìn thấy chúng giống nhau.

## 8. Dùng Unicode regex khi hợp đồng muốn bao gồm NBSP

Nếu chính sách ghi rõ “bỏ các ký tự thuộc Unicode White_Space ở đầu/cuối”, Java regex với cờ `(?U)` là một lựa chọn. Mặc định `\s` không bao phủ NBSP, còn `(?U)\s` nhận diện thêm NBSP và một số ký tự Unicode khác.

~~~java
String nbsp = "\u00A0Hello\u00A0";
String cleaned = nbsp.replaceAll("(?U)\\A\\s+|\\s+\\z", "");
System.out.println(cleaned.equals("Hello")); // true
~~~

Hai neo `\A` và `\z` đảm bảo chỉ bỏ ở **hai đầu toàn bộ chuỗi**; không thay NBSP có ý nghĩa nằm giữa các từ. Unicode White_Space cũng khác với phép hợp `Character.isWhitespace || Character.isSpaceChar` ở mục 7: chẳng hạn U+0085 thuộc Unicode White_Space nhưng không được hai API `Character` này nhận diện. Hãy kiểm thử theo chính tập ký tự nghiệp vụ yêu cầu và giữ nguyên dữ liệu gốc khi cần phục hồi.

## 9. Có nên dùng Unicode normalization để “xóa khoảng trắng”?

`java.text.Normalizer` dùng để chuẩn hóa dạng Unicode, không phải API chuyên xóa khoảng trắng ở đầu/cuối.

Ví dụ, chuẩn hóa tương thích `NFKC` có thể biến NBSP `U+00A0` thành SPACE `U+0020`:

```java
String nbsp = "\u00A0";
String nfkc = java.text.Normalizer.normalize(
        nbsp, java.text.Normalizer.Form.NFKC);
System.out.println(nfkc.equals(" ")); // true
```

Nhưng **không nên tự động chạy NFKC cho mọi input chỉ để giải quyết NBSP**. Chuẩn hóa tương thích có thể thay đổi nhiều loại ký tự khác và làm mất sự khác biệt kiểu chữ/định dạng mà ứng dụng cần giữ.

Nếu chỉ cần xử lý khoảng trắng biên, cách thích hợp hơn là định nghĩa tập ký tự biên và loại bỏ **đúng vùng cần loại bỏ**. Nếu yêu cầu nghiệp vụ là chuẩn hóa toàn bộ văn bản theo NFKC, hãy áp dụng như một chính sách riêng và kiểm tra đầy đủ các hệ quả của nó.

## 10. Các sai lầm phổ biến

| Nhầm lẫn | Vấn đề thực tế | Hướng xử lý |
| --- | --- | --- |
| “`strip()` xóa tất cả dấu cách.” | `U+00A0`, `U+2007`, `U+202F` vẫn có thể tồn tại ở biên. | Kiểm tra `Character.isWhitespace` và chính sách ứng dụng. |
| “`trim()` chắc chắn xóa nhiều hơn `strip()`.” | `trim()` chủ yếu dựa trên code unit `<= U+0020`, không xử lý NBSP. | Dùng API theo hợp đồng, không theo tên gọi. |
| “Có NBSP nên `isBlank()` phải trả `true`.” | `isBlank()` cũng dựa trên `Character.isWhitespace`. | Nếu muốn định nghĩa blank rộng hơn, tự kiểm tra theo predicate được chọn. |
| “Thấy trống thì chuỗi rỗng.” | NBSP có độ dài thật, ví dụ `"\u00A0".length() == 1`. | In code point khi gỡ lỗi. |
| “Thay hết NBSP thành SPACE luôn an toàn.” | Có thể mất hành vi không ngắt dòng, ảnh hưởng dàn trang. | Chỉ chuẩn hóa theo chính sách rõ ràng. |
| “`\s` mặc định trong regex luôn bắt được NBSP.” | Tập ký tự phụ thuộc cờ Unicode của `Pattern`. | Dùng `(?U)` khi cần Unicode regex và đã kiểm thử. |
| “`NFKC` chỉ thay NBSP.” | Đây là phép chuẩn hóa tương thích ảnh hưởng nhiều ký tự. | Không dùng NFKC như mẹo làm sạch khoảng trắng nếu không cần. |

### Cách chẩn đoán một chuỗi trông như có dấu cách

Đừng chỉ in trực tiếp `System.out.println(text)`. Khi cần xác định ký tự đặc biệt, hãy in mã của các code point:

```java
String input = "\u00A0Hi\u202F";
input.codePoints().forEach(cp ->
        System.out.printf("U+%04X%n", cp));
```

Đầu ra:

```text
U+00A0
U+0048
U+0069
U+202F
```

Cách kiểm tra này cho biết chính xác tại sao một chuỗi **có vẻ đã được trim/strip nhưng vẫn chứa khoảng trắng ở hai đầu**.

## 11. Tự kiểm tra kiến thức

### Câu 1

```java
String s = "\u00A0";
System.out.println(s.isBlank());
System.out.println(s.strip().isEmpty());
```

**Đáp án:** `false` và `false`. `U+00A0` không thỏa `Character.isWhitespace`, nên `strip()` không xóa nó, và `isBlank()` không xem chuỗi này là blank.

### Câu 2

```java
String s = "\u2003Java\u2003";
System.out.println(s.trim().equals(s));
System.out.println(s.strip().equals("Java"));
```

**Đáp án:** `true` và `true`. `trim()` không bỏ EM SPACE `U+2003`, nhưng `strip()` có bỏ vì `Character.isWhitespace(0x2003)` là `true`.

### Câu 3

```java
String s = " \u00A0A\u00A0 ";
System.out.println(s.strip()
        .equals("\u00A0A\u00A0"));
```

**Đáp án:** `true`. `strip()` bỏ SPACE ASCII ở ngoài nhưng dừng khi gặp NBSP.

### Câu 4

Một sản phẩm cần loại bỏ NBSP ở đầu/cuối trường tên được dán từ HTML, nhưng phải giữ khoảng trắng không ngắt dòng giữa số và đơn vị trong `"10\u00A0kg"`. Nên làm gì?

**Đáp án:** Áp dụng quy tắc **chỉ bỏ ở ranh giới** (chẳng hạn hàm `stripBoundarySpaces` ở trên), không dùng `replace("\u00A0", " ")` trên toàn bộ văn bản. Kiểm thử cả trường hợp NBSP ở biên và NBSP có ý nghĩa ở giữa.

### Câu 5

Trong Java 17, `Character.isSpaceChar(0x00A0)` và `Character.isWhitespace(0x00A0)` có cùng kết quả không?

**Đáp án:** Không. `isSpaceChar(0x00A0) == true` còn `isWhitespace(0x00A0) == false`. Đây chính là nguyên nhân NBSP không bị `strip()` loại bỏ.

## 12. Tổng kết

- `String.strip()` (Java 11+) loại bỏ **`Character.isWhitespace` ở hai đầu**, không phải mọi Unicode space character.
- Ba dấu cách không ngắt dòng quan trọng là **NBSP `U+00A0`**, **FIGURE SPACE `U+2007`**, **NARROW NO-BREAK SPACE `U+202F`**; trong Java 17 chúng được `isSpaceChar` nhận diện nhưng `isWhitespace` không nhận diện.
- `trim()` cũng không loại bỏ NBSP; `isBlank()` có thể trả `false` cho chuỗi chỉ chứa NBSP.
- Nếu yêu cầu nghiệp vụ muốn bỏ thêm các khoảng trắng không ngắt dòng ở **đầu/cuối**, hãy định nghĩa rõ predicate biên hoặc dùng Unicode-aware regex với kiểm thử phù hợp.
- Không xóa NBSP bên trong chuỗi hoặc áp dụng chuẩn hóa tương thích một cách tùy tiện; dấu cách không ngắt dòng có thể mang ý nghĩa dàn trang.

## Tài liệu tham khảo

- [Java SE 17 — `String.strip()`, `stripLeading()`, `stripTrailing()` và `isBlank()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html)
- [Java SE 17 — `Character.isWhitespace` và `Character.isSpaceChar`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html)
- [Java SE 17 — `Pattern` và `UNICODE_CHARACTER_CLASS`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html)
- [Unicode Standard — Unicode Character Database](https://www.unicode.org/ucd/)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-whitespace-006">Phân biệt null, chuỗi rỗng và dữ liệu chỉ có khoảng trắng</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt null, chuỗi rỗng và dữ liệu chỉ có khoảng trắng trong Java

Trong Java, **null**, **chuỗi rỗng** (`""`) và **chuỗi chỉ chứa khoảng trắng** là ba trạng thái khác nhau. Chúng thường cùng xuất hiện khi nhận dữ liệu từ biểu mẫu, tệp cấu hình, cơ sở dữ liệu hoặc API, nhưng cách kiểm tra và ý nghĩa nghiệp vụ không giống nhau.

- **null**: biến tham chiếu không trỏ đến một đối tượng String.
- **Chuỗi rỗng `""`**: có đối tượng String hợp lệ, nhưng độ dài bằng 0.
- **Chuỗi trắng** (*blank string*): chuỗi rỗng hoặc chuỗi mà mọi code point đều được Java xem là *whitespace*. Ví dụ `"   "`, `"\t\n"` là blank; `" A "` không blank.

Chọn đúng phép kiểm tra giúp tránh **NullPointerException**, tránh chấp nhận dữ liệu chỉ có khoảng trắng và giữ nguyên sự khác biệt giữa “không cung cấp dữ liệu” và “cung cấp một chuỗi rỗng”.

## 1. Ba trạng thái: khác nhau về đối tượng và nội dung

Xem đoạn minh họa sau:

~~~java
String absent = null;
String empty = "";
String spaces = "   ";
String padded = " Java ";
~~~

| Giá trị | Có đối tượng String? | length() | isEmpty() | isBlank() |
|---|---|---:|---|---|
| `null` | Không | Không gọi được | Không gọi được | Không gọi được |
| `""` | Có | 0 | true | true |
| `"   "` | Có | 3 | false | true |
| `"\t\n"` | Có | 2 | false | true |
| `" Java "` | Có | 6 | false | false |
| `"Java"` | Có | 4 | false | false |

Các ô “không gọi được” không có nghĩa là phương thức trả về false: **gọi `absent.length()`, `absent.isEmpty()` hoặc `absent.isBlank()` khi absent là null sẽ ném NullPointerException**.

Hai String có độ dài khác 0 vẫn có thể chỉ chứa whitespace. Vì vậy, **isEmpty() không thay thế được isBlank()** trong việc kiểm tra đầu vào không có văn bản có ý nghĩa.

Một điểm cần nhớ: String là immutable. Việc gọi `strip()` hay `trim()` tạo/trả về giá trị String thích hợp, không biến đổi nội dung String gốc; `null` thì không phải String nên không có phương thức để gọi.

## 2. null: trạng thái không có tham chiếu String

Khai báo `String value = null;` tạo một biến tham chiếu có giá trị null, **không tạo một đối tượng String chứa từ “null”**.

~~~java
String value = null;

System.out.println(value == null);   // true
System.out.println(value != null);   // false
System.out.println("".equals(value)); // false
~~~

Những biểu thức so sánh tham chiếu `== null` và `!= null` là hợp lệ. Một String literal như `""` luôn là receiver không-null, nên `"".equals(value)` an toàn và trả về false nếu value null.

Ngược lại, đoạn sau gây lỗi:

~~~java
String value = null;
System.out.println(value.isEmpty()); // NullPointerException
~~~

Đây là **đoạn minh họa tình huống lỗi**, không phải một chương trình đầy đủ cần chạy chung với các ví dụ khác. Java không thể gọi phương thức instance trên tham chiếu null.

### null có đồng nghĩa dữ liệu sai không?

Không nhất thiết. Trong nhiều API, null thể hiện **vắng mặt/không được cung cấp**; ở nơi khác, null là dữ liệu không hợp lệ. Ý nghĩa do hợp đồng dữ liệu quyết định:

- Trường tùy chọn không gửi lên có thể được biểu diễn bằng null.
- Một trường bắt buộc có thể **từ chối null** bằng validation.
- Khi cập nhật từng phần (*partial update*), “không gửi trường” và “gửi trường có null” thậm chí có thể mang ý nghĩa khác nhau. Một biến String đơn lẻ không đủ biểu diễn cả ba trạng thái “không có trường”, “null” và “chuỗi có giá trị”; cần một cấu trúc/hợp đồng phù hợp nếu nghiệp vụ đòi hỏi.

**Đừng tự động chuyển null thành `""` ở mọi ranh giới hệ thống**, vì làm vậy có thể đánh mất thông tin “người dùng chưa cung cấp” so với “người dùng cố ý để rỗng”.

## 3. Chuỗi rỗng: `""`, length() và isEmpty()

Chuỗi rỗng là một đối tượng String có **0 đơn vị mã UTF-16**:

~~~java
String empty = "";
System.out.println(empty.length());  // 0
System.out.println(empty.isEmpty()); // true
System.out.println(empty.isBlank()); // true
~~~

`isEmpty()` được định nghĩa tương đương kiểm tra `length() == 0`. Nó không duyệt hay bỏ qua khoảng trắng:

~~~java
System.out.println(" ".isEmpty());   // false
System.out.println("\t".isEmpty());  // false
System.out.println("\n".isEmpty());  // false
System.out.println(" x ".isEmpty()); // false
~~~

Với bất kỳ String không-null nào, `s.isEmpty()` chỉ true khi **không có đơn vị mã nào**.

### Khác biệt giữa `""` và `"null"`

~~~java
String absent = null;
String word = "null";

System.out.println(absent == null); // true
System.out.println(word == null);   // false
System.out.println(word.isEmpty()); // false
System.out.println(word.isBlank()); // false
~~~

Chuỗi `"null"` gồm bốn chữ cái n, u, l, l; nó **không** phải giá trị null. Một số cách chuyển đổi như `String.valueOf((Object) null)` tạo chuỗi chữ `"null"`; việc hiển thị chữ này không có nghĩa dữ liệu gốc đã có giá trị văn bản từ người dùng.

## 4. Chuỗi trắng và String.isBlank() từ Java 11

`String.isBlank()` có từ Java 11. Nó trả về true khi **chuỗi rỗng hoặc chỉ bao gồm các code point mà Character.isWhitespace(int) xác định là whitespace**.

~~~java
System.out.println("".isBlank());       // true
System.out.println("   ".isBlank());    // true
System.out.println("\t\n".isBlank());   // true
System.out.println(" A ".isBlank());    // false
System.out.println("0".isBlank());      // false
~~~

`isBlank()` là kiểm tra **nội dung**, không phải thao tác xóa ký tự. Nó không thay đổi String. Một chuỗi có một chữ cái, chữ số hoặc dấu câu không thuộc tập whitespace sẽ trả về false, dù trước và sau có nhiều dấu cách.

### Những khoảng trắng không nhất thiết là dấu cách ASCII

Whitespace trong Java bao gồm nhiều ký tự như tab, xuống dòng và một số dấu cách Unicode. Khác biệt đáng chú ý:

| Ký tự | Mã Unicode | Character.isWhitespace | isBlank() cho chuỗi chỉ chứa ký tự đó |
|---|---|---|---|
| Dấu cách ASCII | U+0020 | true | true |
| Tab | U+0009 | true | true |
| Xuống dòng LF | U+000A | true | true |
| EM SPACE | U+2003 | true | true |
| NO-BREAK SPACE (NBSP) | U+00A0 | false | false |

**NBSP (U+00A0) là ví dụ quan trọng:** người dùng có thể nhìn thấy nó giống một dấu cách thông thường, nhưng `Character.isWhitespace` không coi nó là whitespace. Vì thế, **một String chỉ chứa NBSP không được `isBlank()` coi là blank**.

Điều này không phải lỗi của isBlank(); đó là quy tắc phân loại cụ thể của API. `Character.isSpaceChar` có tập nhận diện không giống `Character.isWhitespace`; đừng đánh đồng hai phương thức.

Nếu nghiệp vụ cần coi cả NBSP, zero-width space hoặc các ký tự định dạng đặc biệt là “không có dữ liệu”, phải **định nghĩa rõ danh sách/khuôn mẫu ký tự được phép bỏ qua**, đồng thời cân nhắc các rủi ro khi chuẩn hóa dữ liệu Unicode. Không có một phép kiểm tra duy nhất mặc nhiên đúng cho mọi ngôn ngữ và mọi biểu mẫu.

## 5. Phân biệt kiểm tra và biến đổi chuỗi

`isEmpty()` kiểm tra đúng độ dài 0; `isBlank()` hỏi chuỗi có rỗng hoặc chỉ gồm `Character.isWhitespace` hay không. Cả hai chỉ trả về `boolean`. Ngược lại, `trim()` và `strip()` trả về một chuỗi đã cắt **hai mép** theo hai quy tắc khác nhau; chúng không sửa đối tượng String gốc.

~~~java
String em = "\u2003";
String nbsp = "\u00A0";
System.out.println(em.isBlank());           // true
System.out.println(em.trim().isEmpty());    // false
System.out.println(em.strip().isEmpty());   // true
System.out.println(nbsp.isBlank());         // false
System.out.println(nbsp.strip().isEmpty()); // false
~~~

`trim()` xét đơn vị mã `char <= U+0020`, còn `strip()` và `isBlank()` theo Java whitespace; NBSP không thuộc tập sau. Trong bài này, điểm quan trọng hơn là **cả bốn đều cần một String không-null để gọi**. Xử lý `null` trước, rồi quyết định giữ nguyên hay chuẩn hóa dựa trên ý nghĩa của từng trường. Không nên tự biến `null` thành `""` chỉ để gọi được các phương thức này.

## 6. So sánh phương thức theo từng trạng thái

Giả sử `s` là String **không-null**:

| Trạng thái s | s.isEmpty() | s.isBlank() | s.strip().isEmpty() |
|---|---|---|---|
| `""` | true | true | true |
| `"   "` | false | true | true |
| `"\t\n"` | false | true | true |
| `" Java "` | false | false | false |
| `"\u2003"` (EM SPACE) | false | true | true |
| `"\u00A0"` (NBSP) | false | false | false |
| `"0"` | false | false | false |

Trong kiểm tra chuỗi trống hoặc chỉ chứa whitespace theo định nghĩa Java, `isBlank()` trực tiếp, ngắn và dễ đọc hơn việc chỉ kiểm tra `strip().isEmpty()`. Không cần tạo chuỗi sau khi strip nếu chỉ cần boolean.

Với **null**, mọi lời gọi instance ở bảng đều gây NullPointerException. Vì vậy, cần tách riêng kiểm tra null hoặc bảo đảm API không bao giờ trả về null.

## 7. Viết điều kiện an toàn khi dữ liệu có thể null

### 7.1. Kiểm tra null bằng `== null`

~~~java
String input = null;

if (input == null) {
    System.out.println("Missing");
}
~~~

`null` là giá trị tham chiếu đặc biệt, không phải String. Đây là trường hợp dùng `==` hợp lý. Ngược lại, khi **so sánh nội dung của hai String**, nên dùng `equals()` theo ngữ nghĩa cần thiết.

### 7.2. Null hoặc rỗng: kiểm tra kết hợp có short-circuit

~~~java
static boolean isNullOrEmpty(String s) {
    return s == null || s.isEmpty();
}
~~~

Đây là **phương thức minh họa**, cần đặt bên trong một lớp Java. Toán tử `||` có đánh giá ngắn mạch: nếu `s == null` true, Java không chạy `s.isEmpty()` nên tránh NullPointerException.

Kết quả dự kiến:

- `isNullOrEmpty(null)` → true.
- `isNullOrEmpty("")` → true.
- `isNullOrEmpty("   ")` → false.

### 7.3. Null hoặc blank: chặn dữ liệu chỉ có whitespace

~~~java
static boolean isNullOrBlank(String s) {
    return s == null || s.isBlank();
}
~~~

Hàm trả true cho null, `""` và `"   "`, nhưng trả false cho `" Java "` hoặc `"\u00A0"` (NBSP).

Nếu cần kiểm tra giá trị **có nội dung theo quy tắc whitespace của Java**, dùng:

~~~java
static boolean hasText(String s) {
    return s != null && !s.isBlank();
}
~~~

Toán tử `&&` cũng đánh giá ngắn mạch. Nếu s null thì vế `!s.isBlank()` không được chạy. Cách này thường phù hợp cho trường nhập tên, tiêu đề hoặc từ khóa tìm kiếm bắt buộc có nội dung.

### 7.4. Khi hai giá trị có thể null

`java.util.Objects.equals(a, b)` trả true khi cả hai cùng null, false khi chỉ một bên null, và so sánh theo `a.equals(b)` khi a không-null:

~~~java
System.out.println(java.util.Objects.equals(null, null)); // true
System.out.println(java.util.Objects.equals(null, ""));   // false
System.out.println(java.util.Objects.equals("", ""));     // true
~~~

Quy tắc này hữu ích khi so sánh hai trạng thái nullable. Nó **không** thay cho kiểm tra blank: `Objects.equals("", "   ")` vẫn false và `Objects.equals(null, "")` cũng false.

## 8. Chương trình Java 17+ đầy đủ

Ví dụ này kiểm tra rõ các giá trị null, rỗng, khoảng trắng ASCII, tab/xuống dòng, EM SPACE, NBSP và chuỗi có chữ. Nhãn được in riêng để không dựa vào cách terminal hiển thị ký tự vô hình.

Lưu thành tệp **BlankStringDemo.java** với mã UTF-8:

~~~java
public class BlankStringDemo {
    private static boolean isNullOrBlank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    public static void main(String[] args) {
        String[] values = {
                null, "", "   ", " \t\n", " Java ",
                "Java", "\u2003", "\u00A0"
        };
        String[] labels = {
                "null", "empty", "spaces", "tabs",
                "padded", "word", "em-space", "nbsp"
        };

        for (int i = 0; i < values.length; i++) {
            String s = values[i];
            System.out.printf(
                    "%s: null=%s empty=%s blank=%s%n",
                    labels[i], s == null,
                    s != null && s.isEmpty(),
                    s != null && s.isBlank()
            );
        }

        System.out.println("nullOrBlank(null)=" + isNullOrBlank(null));
        System.out.println("nullOrBlank(spaces)=" + isNullOrBlank("   "));
        System.out.println("hasText(Java)=" + hasText("  Java  "));
        System.out.println("hasText(spaces)=" + hasText("   "));

        String emSpace = "\u2003";
        String nbsp = "\u00A0";
        System.out.println("trim-em-empty=" + emSpace.trim().isEmpty());
        System.out.println("strip-em-empty=" + emSpace.strip().isEmpty());
        System.out.println("trim-nbsp-empty=" + nbsp.trim().isEmpty());
        System.out.println("strip-nbsp-empty=" + nbsp.strip().isEmpty());

        String input = "  Java  ";
        String clean = input.strip();
        System.out.println("original=[" + input + "]");
        System.out.println("clean=[" + clean + "]");
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac -encoding UTF-8 BlankStringDemo.java
java BlankStringDemo
~~~

Đầu ra chính xác:

~~~text
null: null=true empty=false blank=false
empty: null=false empty=true blank=true
spaces: null=false empty=false blank=true
tabs: null=false empty=false blank=true
padded: null=false empty=false blank=false
word: null=false empty=false blank=false
em-space: null=false empty=false blank=true
nbsp: null=false empty=false blank=false
nullOrBlank(null)=true
nullOrBlank(spaces)=true
hasText(Java)=true
hasText(spaces)=false
trim-em-empty=false
strip-em-empty=true
trim-nbsp-empty=false
strip-nbsp-empty=false
original=[  Java  ]
clean=[Java]
~~~

Cần quan sát:

1. Biến null được kiểm tra trước bằng `s == null`, nên không có lời gọi phương thức trên null.
2. Chuỗi rỗng cho cả `empty=true` và `blank=true`.
3. Chuỗi gồm dấu cách/tab/xuống dòng có `empty=false`, `blank=true`.
4. Chuỗi có chữ `" Java "` không được xem là blank dù có khoảng trắng đầu/cuối.
5. EM SPACE là blank và được `strip()` loại bỏ; NBSP không được `isBlank()` hay `strip()` nhận là whitespace.
6. `strip()` trả chuỗi kết quả, không sửa biến `input`.

## 9. Chọn chính sách chuẩn hóa theo nghiệp vụ

Một hệ thống thực tế thường phải quyết định **những trạng thái nào cần phân biệt**:

| Tình huống | Chính sách gợi ý | Giải thích |
|---|---|---|
| Tên hiển thị bắt buộc | Từ chối null hoặc `isBlank()` | Không chấp nhận thiếu dữ liệu, rỗng hoặc chỉ whitespace |
| Từ khóa tìm kiếm tùy chọn | Có thể coi null/blank là “không có bộ lọc” | Thuận tiện cho người dùng; phải ghi rõ hợp đồng API |
| Mã định danh hoặc mật khẩu | Không tự strip/trim nếu không được đặc tả | Khoảng trắng có thể là dữ liệu hợp lệ và có ý nghĩa |
| Văn bản người dùng tự do | Giữ nguyên nếu cần bảo toàn | Việc loại bỏ whitespace có thể làm thay đổi nội dung mong muốn |
| Cập nhật một phần dữ liệu | Phân biệt không gửi trường, null, `""` nếu cần | Ba trạng thái có thể dẫn đến hành vi cập nhật khác nhau |
| So sánh thay đổi giữa hai chuỗi nullable | Dùng `Objects.equals(old, next)` | Giữ khác biệt null và chuỗi rỗng |
| Làm sạch dữ liệu theo quy tắc Java whitespace | `strip()` sau khi bảo đảm không-null | Hỗ trợ nhiều whitespace Unicode hơn `trim()` |

Một quy trình nhập tên hiển thị có thể là: nhận nullable String → kiểm tra null → dùng `strip()` nếu sản phẩm cho phép loại bỏ whitespace đầu/cuối → kiểm tra kết quả `isEmpty()` hoặc `isBlank()` → lưu kết quả. Tuy nhiên, **không dùng một quy trình chuẩn hóa duy nhất cho mọi trường**. Với mật khẩu, đoạn văn định dạng hay dữ liệu có chữ ký số, việc thay đổi khoảng trắng có thể phá vỡ ý nghĩa và tính toàn vẹn của dữ liệu.

### Ví dụ chuẩn hóa có chủ đích

~~~java
static String normalizeOptionalTitle(String title) {
    if (title == null) {
        return null;
    }

    String cleaned = title.strip();
    return cleaned.isEmpty() ? null : cleaned;
}
~~~

Đây là **phương thức minh họa**: null giữ nguyên; chuỗi rỗng hoặc chỉ whitespace theo `strip()` được biến thành null; tên có nội dung được cắt whitespace hai đầu. Hành vi đó chỉ đúng khi **hợp đồng nghiệp vụ cho phép coi các giá trị trắng như dữ liệu vắng mặt**.

Một phương án khác là giữ chuỗi rỗng riêng và chỉ từ chối blank khi cần validation:

~~~java
static void requireNonBlank(String value) {
    if (value == null || value.isBlank()) {
        throw new IllegalArgumentException("Value must contain text");
    }
}
~~~

Cả hai đoạn trên phải được đặt trong một lớp để biên dịch; chúng minh họa **hai chính sách khác nhau**, không nên áp dụng đồng thời mà không có mục đích rõ.

## 10. Unicode: vì sao dữ liệu nhìn trống đôi khi isBlank() lại false?

Có những ký tự hiển thị ít hoặc không có dấu vết trên màn hình nhưng **không thuộc bộ ký tự whitespace mà Java dùng cho `isBlank()`**. NBSP là trường hợp thường gặp khi sao chép nội dung từ HTML. Một số ký tự định dạng không có độ rộng hiển thị cũng có thể xuất hiện trong dữ liệu.

Điều này dẫn đến ba lưu ý:

1. **Không suy luận dữ liệu là trống từ cách nó hiển thị trên màn hình.** Cần kiểm tra mã Unicode nếu kết quả bất ngờ.
2. **Không tự xóa mọi ký tự vô hình một cách tùy tiện.** Một số ký tự có tác dụng ngôn ngữ hoặc định dạng hợp lệ; xóa chúng có thể làm thay đổi dữ liệu.
3. **Cần xác định chính sách đầu vào:** ứng dụng muốn nhận dạng whitespace theo `Character.isWhitespace`, theo tập Unicode rộng hơn, theo cấu hình giao diện hay theo quy tắc chuyên biệt?

Ví dụ so sánh hai predicate:

~~~java
int nbsp = 0x00A0;
System.out.println(Character.isWhitespace(nbsp)); // false
System.out.println(Character.isSpaceChar(nbsp));   // true
~~~

`Character.isWhitespace` và `Character.isSpaceChar` có hợp đồng khác nhau. Java String.isBlank() dựa vào `isWhitespace`, nên kết quả cho NBSP là false.

Nếu phải xác định trạng thái “chỉ có các ký tự vô hình” theo chính sách riêng, hãy viết hàm kiểm tra **từng code point** và áp dụng bộ predicate do ứng dụng lựa chọn. Tránh ép index UTF-16 thành số thứ tự code point; dùng `codePoints()` hoặc phương thức duyệt code point phù hợp.

## 11. Các lỗi phổ biến

1. **Gọi `isEmpty()` trên null:** `s.isEmpty()` gây NullPointerException nếu s null. Kiểm tra trước bằng `s == null` hoặc bảo đảm hợp đồng đầu vào không-null.
2. **Chỉ dùng `isEmpty()` khi cần từ chối khoảng trắng:** `"   ".isEmpty()` là false; dùng `isBlank()` cho yêu cầu không có ký tự nội dung.
3. **Xem `""` và null là cùng một giá trị:** chuỗi rỗng là String tồn tại; null là không có tham chiếu. Chúng có thể mang ý nghĩa khác nhau trong API, JSON, database và quy tắc cập nhật.
4. **Cho rằng `strip()` luôn xử lý được mọi dấu cách nhìn thấy:** `strip()` dựa trên `Character.isWhitespace` nên không loại NBSP.
5. **Dùng `trim()` với kỳ vọng hỗ trợ tất cả whitespace Unicode:** `trim()` chỉ loại bỏ các đơn vị mã <= U+0020 ở hai đầu, không phải thuật toán phân loại whitespace Unicode.
6. **Tự chuyển null thành chữ `"null"`:** phép chuyển thành văn bản hiển thị không giữ được semantics vắng mặt; kiểm tra thiết kế dữ liệu trước khi chuyển đổi.
7. **Gọi `strip()` rồi quên nhận kết quả:** String bất biến; `input.strip();` không sửa `input`.
8. **Dùng `==` để so sánh nội dung String:** `== null` là cách kiểm tra null hợp lệ, nhưng `s == ""` không phải kiểm tra nội dung rỗng đáng tin cậy. Dùng `isEmpty()` sau null-check hoặc `"".equals(s)`.
9. **Tự cắt whitespace của mã định danh/mật khẩu:** một số hệ thống coi whitespace là thành phần quan trọng; làm sạch dữ liệu phải theo chính sách đã xác định.
10. **Mặc định chuỗi rỗng là đầu vào không hợp lệ trong mọi trường hợp:** nhiều trường cho phép giá trị rỗng có chủ đích, ví dụ mô tả tùy chọn; hãy căn cứ hợp đồng nghiệp vụ.
11. **Tin rằng dùng `isBlank()` là kiểm tra mọi dạng dữ liệu “không nhìn thấy”:** NBSP hoặc ký tự định dạng có thể làm kết quả false. Quy tắc Java whitespace khác với quy tắc hiển thị.
12. **Bỏ qua phiên bản JDK:** `isBlank()` và `strip()` có từ Java 11; mã Java 17+ dùng được. Dự án Java 8 phải tự dùng cách khác nếu cần cùng chính sách.

## 12. Bài tập tự kiểm tra

Cho đoạn:

~~~java
String a = null;
String b = "";
String c = " \t ";
String d = "\u2003";
String e = "\u00A0";
String f = " A ";
~~~

Hãy tự trả lời:

1. Giá trị nào bằng null?
2. Giá trị nào có `isEmpty() == true` nếu chỉ gọi trên đối tượng không-null?
3. Giá trị nào có `isBlank() == true`?
4. Gọi `a.isBlank()` tạo kết quả boolean nào hay gây ngoại lệ?
5. `d.trim().isEmpty()` và `d.strip().isEmpty()` khác nhau thế nào?
6. `e.isBlank()` có phải true vì NBSP trông giống dấu cách không?
7. Nếu ứng dụng coi cả null và blank là thiếu dữ liệu, nên viết điều kiện nào?
8. Vì sao không nên tự strip một mật khẩu trước khi so sánh?

**Đáp án:**

1. Chỉ **a** là null.
2. Chỉ **b** rỗng thực sự.
3. **b, c, d** là blank theo `Character.isWhitespace`.
4. **NullPointerException**, vì a không tham chiếu String.
5. `d.trim().isEmpty()` **false**, còn `d.strip().isEmpty()` **true**.
6. **false**; NBSP không được `Character.isWhitespace` xem là whitespace.
7. `value == null || value.isBlank()`, tận dụng short-circuit.
8. Khoảng trắng có thể là ký tự hợp lệ trong mật khẩu; strip làm thay đổi dữ liệu người dùng đưa vào.

**Ghi nhớ:** **null = không có đối tượng; `""` = String có độ dài 0; blank = String rỗng hoặc chỉ chứa whitespace theo định nghĩa Java.** Dùng `== null` để kiểm tra tham chiếu, `isEmpty()` để kiểm tra độ dài 0, `isBlank()` để kiểm tra nội dung trắng; luôn kiểm tra null trước khi gọi phương thức instance.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-whitespace-007">Xác định chính sách làm sạch trước khi kiểm tra đầu vào</a>

<details>
<summary>Xem chi tiết</summary>

# Xác định chính sách làm sạch trước khi kiểm tra đầu vào trong Java

## 1. Vì sao phải thiết kế chính sách trước?

Một chuỗi trông giống như `"  Alice  "` không có cùng cách xử lý trong mọi trường. Với **tên hiển thị**, việc bỏ khoảng trắng ở hai đầu có thể hợp lý. Với **mã tài khoản**, khoảng trắng có thể khiến mã sai định dạng và cần bị từ chối. Với **mật khẩu**, chính khoảng trắng ấy có thể là một phần của bí mật và không được tự ý xóa.

Vì thế, câu hỏi đầu tiên không phải là dùng `strip()` hay `trim()`, mà là: **trường dữ liệu này cho phép thay đổi những ký tự nào trước khi kiểm tra tính hợp lệ?**

Sau bài học, người học có thể:

1. Phân biệt `null`, chuỗi rỗng `""`, chuỗi trắng (*blank*) và chuỗi có nội dung.
2. Hiểu `isBlank()`, `isEmpty()`, `strip()`, `trim()` cùng những giới hạn Unicode của từng API.
3. Lựa chọn thứ tự **kiểm tra thô → biến đổi được phép → kiểm tra dạng cuối**, hoặc không biến đổi nếu yêu cầu bắt buộc giữ nguyên.
4. Xây dựng hợp đồng cho tên hiển thị, mã định danh, mật khẩu và nội dung tự do.
5. Kiểm thử ký tự ẩn, dữ liệu thô quá lớn, `null`, khoảng trắng nội bộ và chuỗi Unicode.
6. Viết mã Java 17+ không làm mất dữ liệu hoặc vô tình hợp thức hóa input sai.

## 2. Bốn tình huống khác nhau: null, empty, blank, content

| Giá trị | Ý nghĩa | `isEmpty()` | `isBlank()` | `strip()` |
| --- | --- | --- | --- | --- |
| `null` | Không có đối tượng String | Ném NPE khi gọi | Ném NPE khi gọi | Ném NPE khi gọi |
| `""` | Chuỗi có độ dài 0 | `true` | `true` | `""` |
| `"   "` | Ba dấu cách ASCII | `false` | `true` | `""` |
| `"\t\n"` | Tab và newline | `false` | `true` | `""` |
| `"  Java  "` | Văn bản có khoảng trắng hai đầu | `false` | `false` | `"Java"` |
| `"A B"` | Dấu cách bên trong nội dung | `false` | `false` | `"A B"` |

`isEmpty()` chỉ kiểm tra `length() == 0`, còn `isBlank()` kiểm tra chuỗi rỗng hoặc chỉ chứa những code point được `Character.isWhitespace()` coi là whitespace. `strip()` loại whitespace này ở **hai đầu**, không tự thay đổi các ký tự ở giữa.

`String` là immutable: kết quả của `strip()` phải được gán, trả về hoặc dùng tiếp. Gọi `raw.strip();` rồi bỏ qua giá trị trả về không thay đổi `raw`.

~~~java
String raw = "  Java  ";
raw.strip();
System.out.println("[" + raw + "]");   // [  Java  ]

String clean = raw.strip();
System.out.println("[" + clean + "]"); // [Java]
~~~

Không được tự động coi `null` và `""` như nhau. Trong một API cập nhật, `null` có thể có nghĩa là xóa giá trị, `""` có thể là giá trị rỗng có chủ đích, còn thuộc tính không xuất hiện có thể mang nghĩa giữ nguyên giá trị cũ.

## 3. Phân tích mục đích từng trường dữ liệu

Một **bảng quyết định** giúp tránh sử dụng một hàm làm sạch chung cho toàn bộ ứng dụng:

| Loại trường | Làm sạch được phép | Kiểm tra hợp lệ | Điều cần tránh |
| --- | --- | --- | --- |
| Tên hiển thị | Có thể `strip()` hai đầu, NFC nếu quy định | Không blank, cấm ký tự không phù hợp, giới hạn độ dài | Tự bỏ dấu, lowercase hoặc gộp khoảng trắng nội bộ khi chưa được phép |
| Mã tài khoản chính xác | Không biến đổi, trừ khi đặc tả quy định canonicalization | Khớp regex/format đúng từng ký tự | Tự sửa mã sai thành mã hợp lệ |
| Mật khẩu | Mặc định bảo toàn input trong chính sách minh họa | Giới hạn độ dài, kiểm tra theo chính sách xác thực | `strip()`, `trim()`, đổi case, tự cắt ngắn |
| Từ khóa tìm kiếm | Có thể `strip()` hoặc gộp khoảng trắng | Blank tương ứng bỏ lọc hay báo lỗi? | Làm mất ký tự có ý nghĩa trong truy vấn |
| Ghi chú nhiều dòng | Có thể chuẩn hóa newline nếu sản phẩm cần | Giới hạn kích thước, ký tự cấm phù hợp | Xóa tab, dấu xuống dòng, thụt lề không chủ ý |
| Mã giao thức/chữ ký | Chỉ theo quy định của chuẩn | So sánh đúng biểu diễn đã thỏa thuận | Tự ý normalize làm hỏng chữ ký hoặc định danh |

Với mỗi trường, **hợp đồng** phải công bố: có chấp nhận `null` hay không; chuỗi blank được hiểu ra sao; phép biến đổi nào được phép; giới hạn kích thước theo đơn vị nào; ký tự đặc biệt được cho phép; dữ liệu cuối được lưu, tìm kiếm và hiển thị như thế nào.

Nếu nhiều tầng xử lý khác nhau, phải dùng cùng hợp đồng. Ví dụ lúc đăng ký `strip()` mã nhưng lúc đăng nhập lại so mã thô có thể tạo bất nhất khó tìm.

## 4. Thứ tự xử lý và nguyên tắc validate trên dạng đúng

Với **trường cho phép bỏ khoảng trắng đầu/cuối**, một pipeline có thể là:

~~~text
Input thô
   │
   ├─ Kiểm tra null / giới hạn kích thước thô
   ▼
Áp dụng đúng thao tác được phép
   ├─ strip()
   ├─ NFC (nếu hợp đồng yêu cầu)
   └─ chuyển đổi khác đã định nghĩa
   ▼
Kiểm tra giá trị kết quả
   ├─ blank hoặc empty?
   ├─ giới hạn độ dài sau biến đổi?
   ├─ ký tự bị cấm?
   └─ đúng định dạng nghiệp vụ?
   ▼
Dùng/lưu giá trị đã kiểm tra
~~~

Với mã phải khớp nguyên dạng hoặc mật khẩu, bỏ hẳn bước biến đổi: **đầu vào thô → kiểm tra nguyên dạng → sử dụng nguyên dạng**.

### Tại sao không chỉ kiểm tra trước khi làm sạch?

~~~java
String raw = "  A  ";
System.out.println(raw.length());         // 5
System.out.println(raw.strip().length()); // 1
~~~

Nếu tên được yêu cầu tối thiểu 3 đơn vị độ dài **sau làm sạch**, việc chỉ kiểm tra `raw.length() >= 3` là sai. Chúng ta phải kiểm tra trên giá trị thực sự sẽ lưu.

### Tại sao vẫn nên chặn kích thước thô trước khi làm sạch?

Một chuỗi chứa hàng triệu dấu cách có thể thành `""` sau `strip()`. Nếu chỉ kiểm tra độ dài cuối, hệ thống đã phải nhận và xử lý lượng dữ liệu thô lớn. Đặt hạn mức byte của request và/hoặc giới hạn số đơn vị ký tự thô ở ranh giới nhận dữ liệu, trước những phép biến đổi tốn tài nguyên.

**Không có thứ tự chung cho mọi trường.** Đối với input được phép chuẩn hóa trước, kiểm tra sau normalize là hợp lý. Với trường bắt buộc đúng cú pháp nguyên dạng, hãy từ chối ngay nếu sai. Mỗi đường đi phải được nêu rõ trong contract.

## 5. strip(), trim(), isBlank() và các kiểu khoảng trắng Unicode

Các API Java tương tự nhau nhưng không hoàn toàn trùng nhau:

- `strip()` và các biến thể `stripLeading()`, `stripTrailing()` dùng `Character.isWhitespace()`.
- `isBlank()` kiểm tra theo định nghĩa `Character.isWhitespace()`.
- `trim()` theo đặc tả cũ loại các ký tự ở đầu/cuối có giá trị code unit nhỏ hơn hoặc bằng `U+0020`.
- Các ký tự Unicode trông như dấu cách không nhất thiết được `Character.isWhitespace()` phân loại như nhau.
- Regex `\\s` của `Pattern` còn phụ thuộc cờ; không tự cho rằng nó có đúng tập ký tự của `strip()`.

| Ký tự | Mã Unicode | Lưu ý |
| --- | --- | --- |
| Dấu cách ASCII | U+0020 | `strip()` loại bỏ khi ở đầu/cuối |
| Tab, xuống dòng | U+0009, U+000A | `strip()` loại ở đầu/cuối, không xóa bên trong |
| IDEOGRAPHIC SPACE | U+3000 | `Character.isWhitespace()` trả `true` |
| NO-BREAK SPACE (NBSP) | U+00A0 | `Character.isWhitespace()` trả `false` |
| ZERO WIDTH SPACE | U+200B | Không được `isBlank()` xem là whitespace |

~~~java
System.out.println("\u3000".isBlank()); // true
System.out.println("\u00A0".isBlank()); // false
System.out.println("\u200B".isBlank()); // false
~~~

Một chuỗi gồm NBSP có thể **được coi là không blank** dù gần như không nhìn thấy. Nếu trường đơn dòng không chấp nhận NBSP hoặc zero-width, cần kiểm tra riêng theo **chính sách trường đó**, không dựa duy nhất vào `!isBlank()`.

Đừng tự động xóa toàn bộ ký tự "vô hình": một số dấu kết hợp và ký tự định dạng có ý nghĩa trong tiếng Việt hoặc văn bản quốc tế.

### Xử lý whitespace bên trong

Nếu yêu cầu gộp nhiều **dấu cách ASCII** liên tiếp thành một:

~~~java
String raw = "An   Bình";
String compact = raw.replaceAll(" +", " ");
System.out.println(compact); // An Bình
~~~

Mẫu `" +"` chỉ áp dụng dấu cách ASCII, không tự xử lý tab, NBSP hay tất cả Unicode whitespace. Nếu cần tập ký tự rộng hơn, định nghĩa và kiểm thử tập đó. Với nội dung nhiều dòng, mã nguồn, văn bản giữ thụt lề hoặc địa chỉ, gộp khoảng trắng có thể làm mất thông tin quan trọng.

## 6. Phân biệt cleaning, normalization, validation và escaping

| Việc | Ví dụ | Mục đích |
| --- | --- | --- |
| Cleaning | `strip()` | Bỏ thành phần được phép ở biên |
| Unicode normalization | `Normalizer.normalize(s, NFC)` | Thống nhất biểu diễn Unicode theo hợp đồng |
| Validation | Độ dài, ký tự cấm, mẫu hợp lệ | Quyết định chấp nhận hay từ chối |
| Output encoding | HTML escaping, SQL parameterization | Bảo vệ hệ thống khi sử dụng dữ liệu ở ngữ cảnh cụ thể |

Hai chuỗi trông gần giống nhau có thể có biểu diễn Unicode khác nhau:

~~~java
String decomposed = "Cafe\u0301";
String nfc = java.text.Normalizer.normalize(
        decomposed, java.text.Normalizer.Form.NFC);
System.out.println(decomposed.equals("Café")); // false
System.out.println(nfc.equals("Café"));        // true
~~~

NFC **không phải** bỏ dấu tiếng Việt, không phải lọc nội dung nguy hiểm và không phải bước bắt buộc cho mọi trường. Nhiều giao thức và hệ thống xác thực yêu cầu giữ nguyên dãy ký tự.

Đổi chữ hoa/thường có quy tắc phụ thuộc locale nếu dùng overload mặc định. Khi một **mã kỹ thuật** được quy định không phân biệt case, có thể dùng quy tắc canonicalization cụ thể như `toLowerCase(Locale.ROOT)`; với tên người, mật khẩu hoặc nội dung có ý nghĩa, không tự động chuyển đổi chỉ để tiện so sánh.

Làm sạch đầu vào không thay thế việc dùng **parameterized SQL**, escape đúng ngữ cảnh đầu ra, kiểm soát đường dẫn, hạn mức tài nguyên hay phân quyền. Cũng không biến mọi chuỗi sai thành chuỗi an toàn.

## 7. Ba chính sách cụ thể: tên hiển thị, mã tài khoản và mật khẩu

### 7.1. Tên hiển thị

Hợp đồng **minh họa**:

1. Từ chối `null` và input thô dài hơn 200 UTF-16 code units.
2. Áp dụng `strip()` hai đầu và chuẩn hóa `NFC`.
3. Từ chối chuỗi sau xử lý là blank.
4. Từ chối ký tự điều khiển ISO, NBSP (U+00A0) và ZERO WIDTH SPACE (U+200B) theo quy tắc riêng của ví dụ.
5. Giới hạn tối đa 40 Unicode code points sau làm sạch.
6. Giữ nguyên các dấu cách giữa các từ, kể cả nhiều dấu cách liên tiếp.

Quy tắc cấm NBSP/zero-width và giữ lại khoảng trắng nội bộ là quyết định **minh họa**, có thể không phù hợp với mọi ngôn ngữ hay sản phẩm. Một hệ thống hỗ trợ tên đa ngôn ngữ cần cân nhắc grapheme cluster, dấu kết hợp và quy tắc nhập liệu thực tế.

### 7.2. Mã tài khoản

Giả sử quy định mã gồm 3–12 ký tự ASCII: bắt đầu bằng chữ thường, các ký tự sau có thể là chữ thường, số hoặc dấu gạch dưới:

~~~java
boolean valid = input != null
        && input.matches("[a-z][a-z0-9_]{2,11}");
~~~

`"alice"` hợp lệ, còn `" alice "`, `"Alice"`, `"a b"` phải bị từ chối. Không âm thầm `strip()` hoặc `toLowerCase()` để biến mã sai thành đúng, trừ khi đặc tả sản phẩm chủ đích cho phép và mọi tầng dùng cùng quy tắc.

### 7.3. Mật khẩu

Trong ví dụ minh họa này, chính sách mật khẩu là:

- Giữ nguyên từng ký tự nhập vào, kể cả khoảng trắng đầu/cuối.
- Không `trim()`, `strip()`, lowercase, bỏ dấu hoặc chuẩn hóa Unicode.
- Kiểm tra độ dài đã quy định mà không tự cắt ngắn.
- Dùng đúng biểu diễn đã chấp nhận trong quá trình đăng ký và xác thực.

Một hệ thống xác thực cụ thể có thể quy định chính sách Unicode riêng; nếu dùng chuẩn hóa, phải thiết kế đồng bộ cho toàn bộ vòng đời thông tin xác thực và nêu rõ cho người dùng.

## 8. Ví dụ hoàn chỉnh, chạy trên Java 17+

Tạo file **`InputCleaningPolicyDemo.java`**:

~~~java
import java.text.Normalizer;

public class InputCleaningPolicyDemo {
    record Result(boolean valid, String value, String reason) {}

    static Result displayName(String raw) {
        if (raw == null) {
            return new Result(false, null, "NULL");
        }
        if (raw.length() > 200) {
            return new Result(false, null, "RAW_TOO_LONG");
        }

        String clean = Normalizer.normalize(
                raw.strip(), Normalizer.Form.NFC);

        if (clean.isBlank()) {
            return new Result(false, null, "BLANK");
        }

        boolean forbidden = clean.codePoints().anyMatch(cp ->
                Character.isISOControl(cp)
                || cp == 0x00A0
                || cp == 0x200B);

        if (forbidden) {
            return new Result(false, null, "DISALLOWED_CHARACTER");
        }
        if (clean.codePointCount(0, clean.length()) > 40) {
            return new Result(false, null, "TOO_LONG");
        }
        return new Result(true, clean, "OK");
    }

    static Result accountId(String raw) {
        if (raw == null) {
            return new Result(false, null, "NULL");
        }
        if (!raw.matches("[a-z][a-z0-9_]{2,11}")) {
            return new Result(false, null, "FORMAT");
        }
        return new Result(true, raw, "OK");
    }

    static Result password(String raw) {
        if (raw == null) {
            return new Result(false, null, "NULL");
        }
        // Quy tắc minh họa: 8..64 đơn vị UTF-16.
        if (raw.length() < 8 || raw.length() > 64) {
            return new Result(false, null, "LENGTH");
        }
        return new Result(true, raw, "OK");
    }

    public static void main(String[] args) {
        Result name = displayName(" \u3000An   Bình\u3000 ");
        System.out.println("name.valid=" + name.valid());
        System.out.println("name.value=[" + name.value() + "]");
        System.out.println("name.spacesKept="
                + name.value().contains("   "));

        Result id = accountId(" alice ");
        System.out.println("id.valid=" + id.valid());
        System.out.println("id.reason=" + id.reason());

        Result secret = password(" pass1234 ");
        System.out.println("password.valid=" + secret.valid());
        System.out.println("password.kept="
                + " pass1234 ".equals(secret.value()));

        Result blank = displayName(" \t\u3000 ");
        System.out.println("blank.reason=" + blank.reason());

        Result newline = displayName(" A\nB ");
        System.out.println("newline.reason=" + newline.reason());

        Result nbsp = displayName("\u00A0");
        System.out.println("nbsp.reason=" + nbsp.reason());
        System.out.println("nbsp.isBlank=" + "\u00A0".isBlank());
        System.out.println("ideographic.isBlank="
                + "\u3000".isBlank());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 InputCleaningPolicyDemo.java
java InputCleaningPolicyDemo
~~~

Stdout:

~~~text
name.valid=true
name.value=[An   Bình]
name.spacesKept=true
id.valid=false
id.reason=FORMAT
password.valid=true
password.kept=true
blank.reason=BLANK
newline.reason=DISALLOWED_CHARACTER
nbsp.reason=DISALLOWED_CHARACTER
nbsp.isBlank=false
ideographic.isBlank=true
~~~

**Diễn giải:** tên được bỏ dấu cách và IDEOGRAPHIC SPACE ở hai đầu, nhưng ba dấu cách ở giữa vẫn nguyên. Mã chứa padding bị từ chối. Mật khẩu giả được giữ nguyên; chương trình chỉ in trạng thái, không in bí mật. Input toàn whitespace trả về `BLANK`. Newline nội bộ và NBSP bị kiểm tra riêng; `isBlank()` vốn không coi NBSP là whitespace.

`record Result` có sẵn trong Java 17. Trong dự án thực tế, có thể thay bằng kiểu kết quả validation của framework, nhưng phải giữ quy tắc quan trọng: **không sử dụng `value` nếu `valid` là `false`**.

## 9. Trường tùy chọn, PATCH và trạng thái vắng mặt

Trong API cập nhật, có ít nhất ba trạng thái khác nhau:

1. **Không gửi thuộc tính:** thường có thể nghĩa là giữ nguyên giá trị đã lưu.
2. **Gửi thuộc tính có `null`:** có thể nghĩa là yêu cầu xóa hoặc bị từ chối.
3. **Gửi thuộc tính `""` / blank:** có thể nghĩa là xóa, lưu rỗng hoặc báo lỗi.

Không thể suy ra thuộc tính có **xuất hiện trong request hay không** chỉ từ một tham chiếu `String` đã bị chuyển về `null`. Hãy giữ trạng thái hiện diện riêng hoặc thiết kế DTO/PATCH model phù hợp.

Đoạn minh họa (giả sử hai biến đã được giải quyết từ request):

~~~java
if (!fieldPresent) {
    // Không sửa nickname cũ.
} else if (input == null) {
    // Áp dụng chính sách cho null được gửi rõ ràng.
} else {
    // Làm sạch/kiểm tra chuỗi theo hợp đồng.
}
~~~

Đây là một ví dụ *fragment*, không phải chương trình độc lập. Ý nghĩa từng trường hợp phải nằm trong hợp đồng API và có bài kiểm thử riêng.

## 10. Giới hạn độ dài và Unicode: đừng dùng từ "ký tự" mơ hồ

Các đơn vị đo khác nhau:

- `String.length()`: số **UTF-16 code units**.
- `String.codePointCount(0, length())`: số **Unicode code points**.
- `getBytes(StandardCharsets.UTF_8).length`: số **byte UTF-8**.
- Số **grapheme clusters**: gần với đơn vị ký tự người dùng nhìn thấy, có thể chứa nhiều code points.

Ví dụ:

~~~java
String emoji = "😀";
System.out.println(emoji.length()); // 2
System.out.println(
        emoji.codePointCount(0, emoji.length())); // 1
System.out.println(emoji.getBytes(
        java.nio.charset.StandardCharsets.UTF_8).length); // 4
~~~

Nếu sản phẩm nói "40 ký tự", đội phát triển phải chỉ ra đó là code points, grapheme hay đơn vị khác. Kiểm tra raw byte để chặn payload lớn có mục đích khác giới hạn độ dài tên hiển thị. Cần tối thiểu một vài test có tiếng Việt tổ hợp và emoji để tránh nhầm `char` với ký tự hiển thị.

## 11. Lỗi phổ biến và cách sửa

| Lỗi | Hậu quả | Cách sửa |
| --- | --- | --- |
| Gọi `strip()` trên `null` | `NullPointerException` | Kiểm tra và xác định contract cho `null` |
| Gọi `strip()` nhưng không nhận kết quả | Giá trị nguồn không đổi | Gán hoặc `return` giá trị mới |
| Chỉ dùng `isEmpty()` | Dấu cách được coi là dữ liệu không rỗng | Dùng blank rule phù hợp |
| Cho rằng `isBlank()` phát hiện mọi ký tự "vô hình" | NBSP/zero-width lọt qua | Quy định tập ký tự được phép |
| Tự động `strip()` mật khẩu | Thay bí mật của người dùng | Bảo toàn đầu vào theo chính sách đã công bố |
| Hạ chữ/bỏ dấu mọi mã trước validation | Có thể hợp thức hóa mã sai hoặc làm trùng giá trị | Quy định canonicalization rõ ràng |
| Gộp whitespace nội bộ của mọi văn bản | Mất thụt lề, xuống dòng, ý nghĩa | Chỉ gộp khi trường cho phép |
| Kiểm tra độ dài raw thay vì bản đã làm sạch | Bỏ lọt tên không đủ độ dài | Kiểm tra dạng cuối được lưu |
| Chỉ kiểm tra độ dài sau `strip()` | Vẫn nhận input thô cực lớn | Đặt giới hạn tại tầng nhận dữ liệu |
| Coi `trim()` và `strip()` hoàn toàn tương đương | Sai với một số Unicode whitespace | Chọn và test API đúng |
| Tin rằng regex `\\s` là Unicode mọi nơi | Bỏ sót nhóm ký tự | Xác minh flag và tập regex |
| Coi làm sạch là thay thế bảo vệ SQL/XSS | Rủi ro ở tầng sử dụng | Parameterized SQL, output encoding đúng ngữ cảnh |

Một quy tắc làm sạch lý tưởng còn nên **idempotent** khi nghiệp vụ cho phép: chạy hai lần cho cùng nội dung như chạy một lần. Ví dụ `"  A  ".strip().strip()` có nội dung bằng `"  A  ".strip()`. Các pipeline tùy biến nhiều bước phải được kiểm thử để không tiếp tục làm biến dạng dữ liệu mỗi lần chạy.

## 12. Ma trận kiểm thử tối thiểu

| Trường hợp | Ví dụ | Điều cần xác minh |
| --- | --- | --- |
| Thiếu dữ liệu | `null` | Không NPE ngoài chủ đích |
| Rỗng | `""` | Chính sách reject/accept/convert |
| Dấu cách ASCII | `"   "` | `strip` và `isBlank` |
| Blank Unicode | `"\u3000"` | Hành vi Unicode đã biết |
| NBSP | `"\u00A0"` | Không nhầm với blank theo Java |
| Zero-width | `"\u200B"` | Chính sách riêng cho ký tự ẩn |
| Padding | `"  Alice  "` | Bản gốc và bản xử lý |
| Whitespace nội bộ | `"An   Bình"` | Có/không gộp đúng contract |
| Newline nội bộ | `"A\nB"` | Đơn dòng có từ chối không? |
| Dấu kết hợp | `"Cafe\u0301"` | NFC có được phép không? |
| Emoji | `"A😀B"` | Đơn vị đo độ dài phù hợp |
| Input cực lớn | Hàng nghìn ký tự | Giới hạn thô và cuối |
| Mã có case khác | `"Alice"` | Có sửa ngầm hay từ chối? |
| Mật khẩu có khoảng trắng | `" pass1234 "` | Phải giữ nguyên |

Kiểm tra thêm **tính nhất quán** giữa UI, backend, API, dữ liệu lưu và thao tác tìm kiếm. Các tầng không nên tự áp dụng những bước chuẩn hóa khác nhau lên cùng một trường.

## 13. Bài tập áp dụng

1. **Phân loại:** dự đoán kết quả `isBlank()`, `isEmpty()` và `strip()` cho `null`, `""`, `"  "`, `"\u3000"`, `"\u00A0"`. Giải thích vì sao `null` phải kiểm tra riêng.
2. **Đổi thứ tự validation:** tên phải tối thiểu ba code points sau `strip()`. Hãy cho thấy `"  A  "` có thể qua bước kiểm tra độ dài raw nhưng không qua bước kiểm tra dạng cuối.
3. **Hai chính sách trái ngược:** viết `validateDisplayName()` có `strip()` và `validateAccountId()` không tự sửa. Thử cùng input `" alice "`.
4. **Mật khẩu:** nhập một passphrase giả có khoảng trắng ở hai đầu. Xác nhận hàm validation không xóa hoặc thay bất cứ ký tự nào.
5. **Unicode:** so `Character.isWhitespace(0x00A0)`, `Character.isSpaceChar(0x00A0)` và `"\u00A0".isBlank()`. Vì sao chúng không nhất thiết cùng kết quả?
6. **Giới hạn kép:** thiết kế raw limit 200 UTF-16 units và final limit 40 code points; kiểm thử chuỗi dài toàn dấu cách và chuỗi emoji.
7. **PATCH:** quy định rõ không gửi nickname, gửi `null` và gửi `""`. Viết ba test để đảm bảo không bị nhập làm một trạng thái.
8. **Idempotence:** kiểm tra `normalizeName(normalizeName(s)).equals(normalizeName(s))` trên nhiều input; phân tích nếu thất bại.

## 14. Checklist trước khi đưa vào ứng dụng

- Trường có ý nghĩa gì? Người dùng nhập văn bản tự do, mã chính xác, bí mật hay nội dung đa dòng?
- Cho phép biến đổi **phần nào** của dữ liệu? Chỉ hai đầu hay cả bên trong?
- `null`, `""` và blank có ý nghĩa giống nhau không?
- `strip()`/`trim()` có đúng định nghĩa whitespace đã chọn không? NBSP, zero-width được xử lý như thế nào?
- Có chuẩn hóa Unicode hay đổi case không? Có làm mất thông tin/ngữ nghĩa không?
- Validate trên giá trị thô, dạng cuối hay cả hai?
- Giới hạn theo UTF-16, code point, grapheme hay byte? Có hạn mức payload trước xử lý?
- Có từ chối ký tự điều khiển hoặc đầu vào sai thay vì sửa ngầm không?
- Có giữ riêng trạng thái thuộc tính **không được gửi** trong PATCH không?
- Đã kiểm thử nhất quán giữa tạo, cập nhật, xác thực, lưu và đọc dữ liệu chưa?

**Quy tắc cốt lõi:** trước khi kiểm tra đầu vào, hãy **xác định chính sách làm sạch của từng trường**. Chỉ biến đổi phần dữ liệu được phép và kiểm tra trên đúng dạng mà ứng dụng sẽ sử dụng. **Không blank** chưa chắc **hợp lệ**; và làm sạch không có nghĩa là an toàn ở mọi ngữ cảnh.

## 15. Tài liệu tham khảo

- [Java SE 17 — String](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html): `strip()`, `trim()`, `isBlank()`, `isEmpty()`, `length()`, `matches()`.
- [Java SE 17 — Character](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html): `isWhitespace()`, `isSpaceChar()`, `isISOControl()`, Unicode code points.
- [Java SE 17 — Normalizer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/Normalizer.html): biểu diễn NFC/NFD/NFKC/NFKD.
- [Java SE 17 — Pattern](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html): `\\s`, cờ Unicode regex và nhóm ký tự.
- [Java SE 17 — StandardCharsets](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/StandardCharsets.html): byte UTF-8 khi tính giới hạn dữ liệu trao đổi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-whitespace-008">Vì sao không tự động xóa mọi khoảng trắng trong dữ liệu người dùng</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao không tự động xóa mọi khoảng trắng trong dữ liệu người dùng?

Khi nhận văn bản từ người dùng, lập trình viên thường muốn “làm sạch” bằng cách bỏ khoảng trắng ở đầu, cuối hoặc gộp nhiều dấu cách liên tiếp thành một dấu cách. Tuy nhiên, **khoảng trắng cũng là dữ liệu**: có trường hợp nó chỉ là lỗi nhập liệu, nhưng có trường hợp nó là một phần của nội dung mà người dùng cố ý cung cấp. Nếu tự động xóa hoặc thay đổi mọi khoảng trắng trước khi hiểu ý nghĩa của trường nhập, chương trình có thể **làm sai dữ liệu, thay đổi kết quả so sánh hoặc phá hỏng một định dạng cần giữ nguyên**.

Trong Java, `String.trim()`, `String.strip()`, `String.isBlank()` hay `replaceAll(...)` giải quyết những câu hỏi khác nhau. Bài học này giải thích cách xác định **khi nào chỉ nên kiểm tra chuỗi trắng, khi nào được chuẩn hóa, khi nào phải giữ nguyên**, và cách triển khai quy tắc rõ ràng theo từng loại dữ liệu.

## 1. Khoảng trắng trong chuỗi gồm những gì?

Từ “khoảng trắng” trong giao tiếp thường chỉ dấu cách giữa các từ. Trong dữ liệu văn bản, nó có thể gồm nhiều loại ký tự:

- **Dấu cách thường** (SPACE, `U+0020`): ví dụ `"Mai Anh"`.
- **Tab** (`\t`, `U+0009`): thường dùng để thụt lề hoặc ngăn cột.
- **Xuống dòng** (`\n`, `U+000A`), carriage return (`\r`, `U+000D`): dùng trong văn bản nhiều dòng.
- **Một số dấu cách Unicode**: ví dụ EM SPACE (`U+2003`), NO-BREAK SPACE (`U+00A0`), vốn có hành vi và ý nghĩa hiển thị riêng.
- **Một số ký tự định dạng vô hình**: ví dụ ZERO WIDTH SPACE (`U+200B`). Ký tự này không nhất thiết được coi là “whitespace” bởi các phương thức Java, dù người dùng có thể khó nhìn thấy.

Vì các tập hợp ký tự này không đồng nhất, câu “xóa tất cả khoảng trắng” **chưa đủ là một yêu cầu kỹ thuật**. Cần nói rõ loại ký tự nào, ở vị trí nào và với mục đích gì. Chẳng hạn, bỏ hai dấu cách đầu tên hiển thị khác hoàn toàn với xóa dấu cách nằm giữa các từ của một đoạn văn.

### Ba khái niệm cần tách biệt

1. **Chuỗi rỗng (empty)**: `""`, không có UTF-16 code unit nào; `length() == 0`, `isEmpty() == true`.
2. **Chuỗi trắng (blank)**: chuỗi rỗng hoặc chỉ có các code point được `Character.isWhitespace(int)` xem là khoảng trắng; `isBlank() == true` trên Java 11+.
3. **Chuỗi có dữ liệu văn bản**: có ít nhất một ký tự không thuộc tập được `isBlank()` coi là whitespace. Chuỗi này vẫn có thể bắt đầu hoặc kết thúc bằng khoảng trắng.

`"   "` không rỗng nhưng được xem là blank; `"  A  "` không blank mặc dù có dấu cách ở hai đầu; `null` là tham chiếu không có đối tượng `String` nên phải được kiểm tra riêng trước khi gọi các phương thức trên.

## 2. Vì sao tự động xóa mọi khoảng trắng có thể làm hỏng dữ liệu?

| Loại dữ liệu | Ví dụ | Nguy cơ khi tự ý xóa/gộp khoảng trắng |
| --- | --- | --- |
| Mật khẩu hoặc cụm mật khẩu | `" xanh lá "` | Khoảng trắng đầu, giữa hoặc cuối có thể thuộc bí mật người dùng đã chọn; thay đổi làm giá trị nhập khác giá trị ban đầu |
| Tên hiển thị, biệt danh | `"  Mai   Anh  "` | Gộp dấu cách có thể khác ý định định dạng; cần quy tắc sản phẩm rõ ràng trước khi chuẩn hóa |
| Địa chỉ, ghi chú giao hàng | Văn bản có xuống dòng và thụt lề | Gộp xuống dòng có thể làm mất ranh giới phần địa chỉ hoặc chỉ dẫn |
| Đoạn mã, cấu hình, YAML, Markdown | Dòng thụt lề, code fence, hai dấu cách cuối dòng | Thay đổi whitespace có thể làm đổi cấu trúc hoặc cách hiển thị |
| Dữ liệu CSV/TSV hoặc nội dung được trích dẫn | Trường chứa dấu cách, tab hoặc xuống dòng | Thao tác không hiểu định dạng có thể làm sai giá trị và vị trí trường |
| Mã định danh, khóa hoặc dữ liệu ký số | Chuỗi cần so khớp chính xác từng ký tự | Kết quả xác thực, kiểm tra chữ ký hoặc tra cứu có thể khác |
| Nội dung soạn thảo tự do | Thơ, danh sách, tin nhắn nhiều dòng | Mất nhịp dòng, bố cục và khoảng cách mà tác giả chủ ý tạo ra |

Không phải tất cả các ví dụ đều cần **bảo toàn mọi dấu cách trong mọi hệ thống**. Điều quan trọng là **quy tắc của từng trường dữ liệu phải được xác định trước**. Ví dụ, ứng dụng có thể chủ động cấm khoảng trắng trong một mã người dùng và báo lỗi; đó là chính sách hợp lệ nếu được công bố và áp dụng nhất quán. Việc âm thầm xóa các ký tự đó rồi lưu một giá trị khác dễ gây nhầm lẫn.

### Phân biệt kiểm tra với biến đổi

Một chương trình có thể **kiểm tra** xem tên hiển thị có rỗng/trắng hay không mà **không tự động viết đè** lên giá trị người dùng nhập:

```java
String displayName = "  Mai   Anh  ";

boolean missingMeaningfulText = displayName.isBlank();
String suggested = displayName.strip();

// displayName vẫn là "  Mai   Anh  ".
// suggested là "Mai   Anh"; chỉ bỏ whitespace ở hai đầu.
```

Đây là đoạn minh họa để đặt bên trong phương thức Java. Bởi `String` là bất biến, `strip()` trả về một giá trị `String` mới hoặc giá trị tương đương, chứ không sửa nội dung bên trong `displayName`. Tuy nhiên, nếu ứng dụng ghi đè biến hoặc cơ sở dữ liệu bằng kết quả này thì **giá trị nghiệp vụ được lưu đã thay đổi**. Tính bất biến của `String` không bảo đảm dữ liệu người dùng được bảo toàn qua các bước xử lý ứng dụng.

## 3. Một phép kiểm tra và hai mức thay đổi dữ liệu

`isBlank()` chỉ **kiểm tra** chuỗi (rỗng hoặc chỉ có Java whitespace), không thay nội dung. `trim()` cắt `char <= U+0020` ở hai đầu; `strip()` cắt `Character.isWhitespace` ở hai đầu. Trong khi đó, `replaceAll("\\s+", " ")` có thể **thay đổi cả bên trong** văn bản: biểu thức `\s` ở chế độ regex mặc định nhận diện tab, xuống dòng và các whitespace ASCII phổ biến, không tự có nghĩa “mọi Unicode space”.

~~~java
String raw = "  Mai   Anh  ";
String edges = raw.strip();                  // "Mai   Anh"
String collapsed = edges.replaceAll("\\s+", " "); // "Mai Anh"
System.out.println(raw.isBlank());          // false
~~~

Dù đầu ra `edges` và `collapsed` trông gọn hơn, chúng là **hai giá trị khác nhau**. Chỉ chọn bước gộp bên trong khi hợp đồng của trường cho phép; với Markdown, YAML, CSV trích dẫn hay nội dung nhiều dòng, việc gộp có thể làm mất cấu trúc. Ví dụ dưới đây cho thấy cả ba giá trị bên cạnh nhau.

## 4. Ví dụ chạy được: giữ nguyên, strip và gộp khoảng trắng

Tạo tệp `WhitespacePolicyDemo.java`:

```java
public class WhitespacePolicyDemo {
    private static void show(String label, String value) {
        System.out.printf("%s=[%s], length=%d%n",
                label, value, value.length());
    }

    public static void main(String[] args) {
        String input = "  Mai   Anh  ";

        show("original", input);
        show("strip", input.strip());
        show("collapse", input.strip().replaceAll("\\s+", " "));

        String blank = " \t\n";
        System.out.println("blank.isEmpty=" + blank.isEmpty());
        System.out.println("blank.isBlank=" + blank.isBlank());

        String passphrase = " green tea ";
        System.out.println("passphrase changed by strip="
                + !passphrase.equals(passphrase.strip()));
    }
}
```

Biên dịch/chạy với Java 17 trở lên:

```bash
javac -encoding UTF-8 WhitespacePolicyDemo.java
java WhitespacePolicyDemo
```

Kết quả chính xác:

```text
original=[  Mai   Anh  ], length=13
strip=[Mai   Anh], length=9
collapse=[Mai Anh], length=7
blank.isEmpty=false
blank.isBlank=true
passphrase changed by strip=true
```

Giải thích:

- `original` giữ đủ **hai dấu cách đầu, ba dấu cách giữa, hai dấu cách cuối**.
- `strip` loại bỏ khoảng trắng ở hai đầu, nhưng vẫn để **ba dấu cách giữa tên**.
- `collapse` còn gộp cụm dấu cách giữa hai từ thành một dấu cách.
- `blank` chứa dấu cách, tab và xuống dòng nên độ dài lớn hơn 0 nhưng `isBlank()` là `true`.
- `passphrase` bị thay đổi nếu gọi `strip()` rồi dùng kết quả để kiểm tra hoặc lưu. Với mật khẩu/cụm mật khẩu, không được thay đổi giá trị người dùng gửi mà chưa có một đặc tả xác thực nhất quán; tránh ghi giá trị bí mật ra log.

Ba kết quả `original`, `strip` và `collapse` có thể đều phù hợp **trong ba mục đích khác nhau**. Không thể chọn một dạng làm “sạch nhất” cho mọi kiểu dữ liệu.

## 5. Unicode làm thay đổi ranh giới của việc “làm sạch”

Không phải ký tự nào nhìn như khoảng trắng cũng thuộc `Character.isWhitespace`. EM SPACE (`U+2003`) được `strip()` cắt ở biên, nhưng NO-BREAK SPACE (`U+00A0`) được giữ lại; `isBlank()` cũng trả `false` cho chuỗi chỉ có NBSP. Vì thế, một trường dữ liệu “trông trống” vẫn có thể vượt qua phép kiểm tra `!isBlank()`.

~~~java
System.out.println("\u2003".isBlank()); // true
System.out.println("\u00A0".isBlank()); // false
System.out.println("\u00A0A\u00A0".strip().length()); // 3
~~~

Nếu biểu mẫu cần từ chối NBSP, FIGURE SPACE, ký tự zero-width hoặc nhóm ký tự riêng, hãy đưa chúng vào **validation của trường đó** và kiểm thử theo code point. Không tự thay thế tất cả Unicode space hoặc ký tự vô hình trước khi biết vai trò của chúng; NBSP có thể giữ số và đơn vị trên cùng một dòng, còn ký tự định dạng có thể quan trọng với ngôn ngữ hay emoji.

## 6. Các trường dữ liệu nên được xử lý khác nhau

### Trường bắt buộc có nội dung: kiểm tra trước khi sửa

Với một nội dung văn bản bắt buộc, thường chỉ cần biết chuỗi **không phải `null` và không chỉ chứa whitespace theo định nghĩa ứng dụng**:

```java
static boolean hasMeaningfulText(String value) {
    return value != null && !value.isBlank();
}
```

Đây là phương thức minh họa nằm trong một lớp Java. Nó không thay đổi dữ liệu. Sau khi xác thực, ứng dụng có thể giữ nguyên nội dung để lưu hoặc hiển thị.

Nếu mục tiêu cụ thể là loại bỏ dấu cách thừa ở hai đầu một trường tên tìm kiếm, hãy tạo giá trị chuẩn hóa **sau khi chính sách cho phép**. Không nên tự động gán mọi trường vào một hàm “cleanAllStrings”.

### Mật khẩu, cụm mật khẩu và token bí mật: giữ đúng đầu vào theo chính sách

Khoảng trắng có thể thuộc một bí mật hợp lệ. Việc âm thầm dùng `strip()`, `trim()` hoặc `replaceAll(...)` trước khi xác thực có thể làm thay đổi bí mật. Một trường hợp đặc biệt nguy hiểm là phía đăng ký và phía đăng nhập dùng **hai quy tắc khác nhau**, khiến người dùng không thể đăng nhập bằng giá trị họ đã chọn.

Nếu sản phẩm đặt quy định cấm ký tự nào đó, phải **thông báo và từ chối đầu vào không hợp lệ** hoặc áp dụng quy tắc chuẩn hóa được thiết kế chính thức cho cả hai phía; không được tùy tiện xóa ký tự rồi coi giá trị đã biến đổi là giá trị người dùng gửi. Không in mật khẩu, token hoặc nội dung bí mật vào log khi thử nghiệm cách xử lý chuỗi.

### Ghi chú nhiều dòng, Markdown và đoạn mã: bảo toàn cấu trúc

Ví dụ văn bản:

```text
Mục 1:
    Việc đầu tiên

Mục 2:
    Việc tiếp theo
```

Tab, dấu cách thụt lề và xuống dòng có thể thể hiện phân cấp, định dạng hoặc ranh giới đoạn văn. Nếu áp dụng `replaceAll("\\s+", " ")` cho toàn bộ nội dung, văn bản trên mất xuống dòng và khoảng cách thụt lề.

Trong Markdown, khoảng trắng đầu dòng tạo cấu trúc danh sách/code block; hai dấu cách cuối dòng có thể mang ý nghĩa ngắt dòng. Với YAML hay một số ngôn ngữ lập trình, indentation thậm chí quyết định cách phân tích cú pháp. **Không có một phép xóa whitespace chung** nào an toàn cho mọi định dạng.

### Tên người, địa chỉ, thông tin hiển thị: tôn trọng ý định nhập liệu

Việc xóa dấu cách đầu/cuối có thể được xem là hợp lý trong một mẫu nhập tên đơn giản, nhưng vẫn là **quyết định về dữ liệu của sản phẩm**, không phải mặc định ngôn ngữ Java.

- Không tự động gộp mọi khoảng trắng giữa các thành phần tên nếu chưa biết quy tắc nhập tên.
- Với địa chỉ nhiều dòng, xuống dòng có thể giúp giữ cấu trúc.
- Với nội dung tự do như tiểu sử, bình luận hoặc thơ, khoảng trắng là một phần trải nghiệm hiển thị.

Nếu UI muốn đề xuất bản đã chuẩn hóa, có thể hiển thị cho người dùng xem lại trước khi lưu, đặc biệt khi thay đổi có thể ảnh hưởng ý nghĩa.

### Mã định danh và trường có cú pháp chặt chẽ: xác thực có chủ đích

Một trường như mã phiếu hoặc mã khách hàng có thể được đặc tả **không cho phép dấu cách**. Khi đó, điều kiện hợp lệ phải được định nghĩa (ký tự nào cho phép, có phân biệt chữ hoa/thường không, xử lý Unicode thế nào), rồi kiểm tra đầu vào theo đặc tả. Tự động xóa mọi dấu cách có thể khiến hai mã khác nhau bị nhập nhằng hoặc khiến lỗi nhập liệu không được phát hiện.

## 7. Phân biệt bảo toàn dữ liệu gốc và tạo giá trị phục vụ tìm kiếm

Một kiến trúc rõ ràng có thể giữ **hai giá trị** với mục đích khác nhau:

- **Giá trị gốc (raw/original)**: nội dung đúng như người dùng cung cấp, dùng khi cần chỉnh sửa lại, hiển thị hoặc đối chiếu.
- **Giá trị dẫn xuất (search/display key)**: phiên bản có thể được bỏ whitespace biên, chuẩn hóa chữ hoa/thường hoặc áp dụng quy tắc khác để tìm kiếm, so sánh hay lập chỉ mục.

Không nên thay thế dữ liệu gốc bằng khóa tìm kiếm trừ khi chính sách sản phẩm cho phép. Quy tắc dẫn xuất cần được xác định rõ và sử dụng nhất quán.

Tệp `PreserveAndSearchDemo.java`:

```java
import java.util.Locale;

public class PreserveAndSearchDemo {
    static String searchKey(String userText) {
        if (userText == null) {
            return "";
        }
        return userText.strip().toLowerCase(Locale.ROOT);
    }

    public static void main(String[] args) {
        String original = "  Java CORE  ";
        String key = searchKey(original);

        System.out.println("original=[" + original + "]");
        System.out.println("key=[" + key + "]");
        System.out.println("stillOriginal=" +
                original.equals("  Java CORE  "));
        System.out.println("matches=" + key.equals("java core"));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 PreserveAndSearchDemo.java
java PreserveAndSearchDemo
```

Kết quả:

```text
original=[  Java CORE  ]
key=[java core]
stillOriginal=true
matches=true
```

Trong ví dụ này, sản phẩm **cố ý** bỏ whitespace hai đầu và chuyển chữ thường cho mục tiêu tìm kiếm không phân biệt hoa/thường đơn giản. Đây là minh họa chính sách, không phải một thuật toán so khớp ngôn ngữ tự nhiên hoàn chỉnh. Không áp dụng hàm `searchKey` cho mật khẩu, dữ liệu ký số hoặc nội dung tự do rồi lưu đè.

Nếu dữ liệu cần tìm kiếm có Unicode phức tạp, quy tắc chuẩn hóa và so khớp còn cần cân nhắc locale, normalization và nhu cầu đặc thù. `Locale.ROOT` giúp tránh phụ thuộc mặc định hệ thống, nhưng không tự biến cách so sánh này thành một giải pháp ngôn ngữ học đầy đủ.

## 8. Thứ tự xử lý đúng: hiểu dữ liệu, xác thực, rồi mới biến đổi nếu được phép

Một quy trình thiết kế thường có các bước:

1. **Xác định ý nghĩa từng trường.** Đây là mật khẩu, tên hiển thị, nội dung văn bản, địa chỉ, mã định danh hay truy vấn tìm kiếm?
2. **Xác định điều kiện hợp lệ.** Cho phép `null` không? Cho phép chuỗi rỗng không? Có cấm whitespace ở một số vị trí không? Unicode nào được chấp nhận?
3. **Tách xác thực khỏi biến đổi.** `isBlank()` và `isEmpty()` chủ yếu giúp đặt câu hỏi; `strip()` hoặc `replaceAll(...)` tạo giá trị đã thay đổi.
4. **Biến đổi đúng phạm vi được phép.** Chỉ bỏ biên nếu chính sách cho phép bỏ biên; chỉ gộp whitespace nội bộ nếu nghiệp vụ thực sự muốn.
5. **Bảo toàn nội dung gốc khi cần.** Đặc biệt với văn bản tự do, dữ liệu cần sửa lại và dữ liệu có ý nghĩa pháp lý/kỹ thuật.
6. **Áp dụng nhất quán ở các thành phần.** UI, API, bước xác thực, lưu trữ và tìm kiếm không nên âm thầm dùng các quy tắc khác nhau.
7. **Kiểm thử dữ liệu biên.** Bao gồm chuỗi rỗng, toàn dấu cách, tab/xuống dòng, dấu cách Unicode, khoảng trắng nội bộ, input `null` và văn bản nhiều dòng.

Không nhất thiết phải lưu hai phiên bản cho tất cả trường dữ liệu. Điều cần bảo đảm là mọi phép biến đổi đều có **lý do, phạm vi và hợp đồng rõ ràng**.

## 9. Lỗi thường gặp

**1. Cứ nhận chuỗi là gọi `trim()` hoặc `strip()`.** Các phương thức này thay đổi nội dung ở biên. Chỉ gọi khi sản phẩm cho phép làm vậy đối với chính trường đó.

**2. Dùng `replaceAll("\\s+", "")` để “làm sạch”.** Biểu thức này xóa toàn bộ các khoảng trắng được regex nhận diện, kể cả dấu cách giữa từ, tab và xuống dòng. Nội dung tự do và dữ liệu mang định dạng có thể mất ý nghĩa.

**3. Nhầm `isBlank()` với `isEmpty()`.** `"   ".isBlank()` là `true` nhưng `"   ".isEmpty()` là `false`. Chọn theo mục tiêu xác thực.

**4. Xem mọi ký tự vô hình là whitespace Java.** Một số ký tự trông như dấu cách (ví dụ NBSP) không thuộc tập `Character.isWhitespace`. Thiết kế chính sách Unicode cụ thể nếu yêu cầu kiểm tra nghiêm ngặt.

**5. Thay đổi bí mật người dùng cung cấp.** Không cắt đầu/cuối, thay chữ hoa/thường hay gộp dấu cách của mật khẩu chỉ vì muốn “tiện” xử lý.

**6. Tự động loại bỏ khoảng trắng để sửa lỗi mã định danh.** Việc sửa thầm có thể che giấu input không hợp lệ; đôi khi cần trả thông báo lỗi hoặc yêu cầu xác nhận.

**7. Chỉ chuẩn hóa ở một phía của hệ thống.** Nếu lưu với một cách, tra cứu hoặc xác thực với cách khác, chương trình có thể không tìm ra dữ liệu dù người dùng nhập như họ được hướng dẫn.

**8. Dùng giá trị đã chuẩn hóa để hiển thị lại mà không giữ bản người dùng nhập.** Điều này có thể khiến người dùng mất định dạng mong muốn hoặc không thể khôi phục nội dung ban đầu.

## 10. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao không nên tự động gọi `strip()` với tất cả trường `String` nhận từ người dùng?

**Đáp án:** Bởi whitespace có thể là thành phần có ý nghĩa trong mật khẩu, văn bản, địa chỉ hoặc dữ liệu cần so khớp chính xác. `strip()` sẽ bỏ một số ký tự ở hai đầu, tức là thay đổi giá trị nếu ứng dụng sử dụng kết quả của nó.

**Câu 2.** `"  Mai   Anh  ".strip()` có xóa ba dấu cách giữa `Mai` và `Anh` không?

**Đáp án:** Không. `strip()` chỉ bỏ whitespace theo định nghĩa Java ở đầu/cuối chuỗi; ba dấu cách nằm giữa được giữ nguyên.

**Câu 3.** Chuỗi `" \t\n"` rỗng hay blank?

**Đáp án:** Không rỗng vì có ký tự nên `isEmpty()` là `false`; nó là blank theo `Character.isWhitespace` nên `isBlank()` là `true`.

**Câu 4.** Vì sao `"\u00A0"` có thể trông giống một dấu cách nhưng `isBlank()` vẫn trả `false`?

**Đáp án:** `U+00A0` là NO-BREAK SPACE, được `Character.isSpaceChar` nhận diện nhưng không được `Character.isWhitespace` nhận diện; `String.isBlank()` dựa trên `Character.isWhitespace`.

**Câu 5.** Khi chỉ muốn tìm kiếm không phân biệt chữ hoa và khoảng trắng hai đầu, có nhất thiết thay đổi giá trị gốc đã lưu không?

**Đáp án:** Không. Có thể tạo một khóa tìm kiếm dẫn xuất với quy tắc đã xác định trong khi vẫn bảo toàn giá trị người dùng nhập để hiển thị và chỉnh sửa.

**Ghi nhớ:** Đừng coi whitespace là “rác” mặc định. Hãy **xác định ngữ nghĩa của trường dữ liệu trước**, phân biệt **kiểm tra** với **biến đổi**, chỉ xóa/gộp ký tự khi chính sách cho phép, và giữ dữ liệu gốc khi khoảng trắng có thể mang ý nghĩa.


</details>

- [Quay lại đầu trang](#back-to-top)
