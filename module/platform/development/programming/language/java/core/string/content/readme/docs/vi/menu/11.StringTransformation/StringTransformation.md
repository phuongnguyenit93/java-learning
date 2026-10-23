<a id="back-to-top"></a>

# Biến đổi nội dung

## Menu
- [replace(char,char) và thay mọi ký tự khớp](#string-transformation-001)
- [replace(CharSequence,CharSequence) với chuỗi nguyên văn](#string-transformation-002)
- [Phân biệt replace với replaceFirst và replaceAll](#string-transformation-003)
- [repeat: số lần lặp, trường hợp rỗng và số âm](#string-transformation-004)
- [transform và xây dựng chuỗi bước xử lý](#string-transformation-005)
- [Thay đổi độ dài sau biến đổi và tác động đến chỉ số cũ](#string-transformation-006)
- [Phân biệt phép biến đổi giữ nguyên ý nghĩa với phép làm mất dữ liệu](#string-transformation-007)
- [Giữ chuỗi gốc khi cần đối chiếu hoặc hiển thị lại](#string-transformation-008)

## <a id="string-transformation-001">replace(char,char) và thay mọi ký tự khớp</a>

<details>
<summary>Xem chi tiết</summary>

# `String.replace(char, char)` — thay mọi ký tự khớp trong Java

## 1. Mục tiêu và ý nghĩa

`String.replace(char oldChar, char newChar)` là phương thức dùng để **thay tất cả lần xuất hiện** của một đơn vị ký tự `char` bằng một `char` khác trong chuỗi Java. Chẳng hạn, `"banana".replace('a', 'o')` có kết quả `"bonono"`: cả ba chữ `a` đều được thay. Phương thức không dừng ở kết quả đầu tiên và không cần vòng lặp tự tìm từng vị trí.

Bốn điểm cần nắm:

1. `oldChar` và `newChar` là hai đối số kiểu **`char`**, viết bằng dấu nháy đơn như `'a'`; chúng biểu diễn đơn vị mã UTF-16, không phải một chuỗi ký tự tùy ý.
2. `replace(char,char)` tìm và thay **mọi phần tử khớp chính xác**. Phép tìm phân biệt hoa/thường: `'a'` khác `'A'`.
3. `String` **bất biến**. Phương thức trả về kết quả kiểu `String`; không sửa chuỗi đã gọi. Muốn lưu kết quả, phải gán nó vào biến hoặc truyền cho nơi cần dùng.
4. Đây là thao tác thay **ký tự literal**, không phải biểu thức chính quy (*regex*). Muốn thay toàn bộ đoạn văn bản có nhiều `char`, dùng overload `replace(CharSequence, CharSequence)`; muốn xử lý regex, dùng `replaceAll(String, String)`.

Bài này đi từ cú pháp đến ví dụ chạy được, phân biệt overload, Unicode và những lỗi thường gặp khi xử lý dữ liệu văn bản.

## 2. Cú pháp, kiểu dữ liệu và kết quả trả về

Khai báo API trong Java SE 17:

```java
public String replace(char oldChar, char newChar)
```

| Thành phần | Ý nghĩa |
| --- | --- |
| `oldChar` | Đơn vị `char` cần tìm trong chuỗi nguồn |
| `newChar` | Đơn vị `char` sẽ thay cho mỗi vị trí khớp |
| Kết quả | Một giá trị `String` có nội dung sau khi thay các vị trí khớp |
| Quy tắc khớp | So sánh chính xác giá trị `char`; không tự bỏ qua hoa/thường, dấu hoặc chuẩn hóa Unicode |
| Phạm vi | Toàn bộ chuỗi, từ đầu đến cuối; không có tham số giới hạn số lần thay |

Ví dụ biểu thức (đặt trong một phương thức, như `main`):

```java
"banana".replace('a', 'o');   // "bonono"
"aaaa".replace('a', 'b');     // "bbbb"
"aba".replace('a', 'b');      // "bbb"
"Java".replace('a', 'A');     // "JAvA"
"Java".replace('A', 'x');     // "Java" (không có 'A')
```

Những dòng trên là **các biểu thức minh họa**, chưa tạo thành file Java độc lập. Trong Java, một literal `char` có đúng một đơn vị mã UTF-16; `'a'` là `char`, còn `"a"` là `String`. Việc truyền hai `String` vào `replace("a","o")` vẫn hợp lệ, nhưng Java chọn **overload nhận `CharSequence`**, không phải overload đang học.

Phương thức không nhận tham số `boolean` điều khiển số lần thay và không có phiên bản `replace(char,char,int limit)` trong `String`. Nếu chỉ muốn thay lần đầu tiên hoặc thay một vùng, cần chọn thuật toán/API khác.

## 3. Chương trình đầy đủ: tất cả vị trí khớp đều được thay

Lưu thành `ReplaceCharDemo.java`:

```java
public class ReplaceCharDemo {
    public static void main(String[] args) {
        String original = "banana";
        String changed = original.replace('a', 'o');

        System.out.println(original);
        System.out.println(changed);
        System.out.println("aaaa".replace('a', 'b'));
        System.out.println("aba".replace('a', 'b'));
        System.out.println("a.b.a".replace('.', '-'));
        System.out.println("a$a$a".replace('$', '#'));
        System.out.println("Java".replace('x', 'y'));
        System.out.println("Java".replace('a', 'a'));
        System.out.println("Java".replace('a', 'A'));
        System.out.println("Java".replace('A', 'x'));
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac -encoding UTF-8 ReplaceCharDemo.java
java ReplaceCharDemo
```

Kết quả chuẩn:

```text
banana
bonono
bbbb
bbb
a-b-a
a#a#a
Java
Java
JAvA
Java
```

Giải thích:
- `original` vẫn là `banana`; biến `changed` giữ kết quả `bonono`.
- `"aaaa".replace('a','b')` thay cả bốn vị trí, và `"aba".replace('a','b')` cho `bbb`, không chỉ thay chữ `a` đầu tiên.
- Dấu `'.'` và `'$'` được xem như ký tự bình thường. Chúng **không mang ý nghĩa regex** trong overload `char,char`.
- Không có `'x'` trong `Java` nên nội dung giữ nguyên. `oldChar == newChar` cũng không làm nội dung thay đổi.
- Phép thay phân biệt hoa/thường: chữ `a` trong `Java` khớp, chữ `A` không có trong `Java`.

## 4. Tại sao đây là thao tác đồng thời theo nội dung nguồn?

Điều kiện khớp được xét theo **chuỗi nguồn ban đầu** trong một lần gọi. Ký tự vừa được thay vào không được xem là mục tiêu mới để “thay dây chuyền” ngay trong chính lần gọi đó.

Ví dụ:

```java
System.out.println("aba".replace('a', 'b')); // bbb
```

Từ `aba`, hai vị trí `a` được thay bằng `b`; chữ `b` gốc vẫn là `b`. Không có một bước thứ hai trong lần gọi này để tiếp tục xử lý các chữ `b`. Do đó kết quả là `bbb`.

Tuy nhiên, **nối nhiều lần gọi `replace`** là một chuỗi các phép biến đổi khác nhau; phép sau nhìn thấy kết quả của phép trước:

```java
System.out.println(
        "aba".replace('a', 'b').replace('b', 'c')); // ccc
System.out.println(
        "aba".replace('b', 'c').replace('a', 'b')); // bcb
```

Đây là snippet đặt trong `main`. Ở dòng đầu, `aba` thành `bbb`, rồi toàn bộ `b` thành `c`. Dòng sau bắt đầu bằng `aca`, rồi thay `a` bằng `b` để thành `bcb`. **Thứ tự các lời gọi quan trọng**; không được giả định hai phép thay ký tự luôn giao hoán.

Nếu muốn thực hiện đồng thời một bảng chuyển đổi nhiều ký tự khác nhau mà không để ký tự mới bị xử lý bởi quy tắc tiếp theo, có thể duyệt chuỗi nguồn một lần, quyết định ký tự thay thế cho từng vị trí và xây dựng kết quả riêng. Không nên ghép các lời gọi `replace` khi phép thay thứ hai không được phép ảnh hưởng ký tự mà phép thứ nhất vừa tạo ra.

## 5. `String` bất biến: kết quả phải được giữ lại

Một lỗi thường gặp:

```java
String text = "banana";
text.replace('a', 'o'); // Có tính kết quả, nhưng kết quả bị bỏ qua.
System.out.println(text); // banana
```

`replace` **không sửa** đối tượng `String` mà `text` đang trỏ tới. Để sử dụng nội dung sau khi thay:

```java
String text = "banana";
text = text.replace('a', 'o');
System.out.println(text); // bonono
```

Lệnh gán sau đó chỉ khiến **biến** `text` tham chiếu tới kết quả; chuỗi bất biến `"banana"` ban đầu không bị đổi nội dung. Nếu có biến khác vẫn trỏ tới chuỗi cũ, biến đó tiếp tục thấy `banana`.

Trường hợp ký tự cần tìm không xuất hiện, tài liệu `String.replace(char,char)` cho phép trả về tham chiếu tới chính `String` đã gọi. Vì vậy **không được suy luận rằng một lời gọi `replace` lúc nào cũng cấp phát một đối tượng mới**. Điều được cam kết ở mức lập trình là **nội dung kết quả** và **tính bất biến**. Để so chuỗi, dùng `equals`, không dùng `==` nhằm phỏng đoán có diễn ra thay thế hay không.

Một biến `final String` không được gán lại:

```java
final String text = "banana";
String changed = text.replace('a', 'o'); // Hợp lệ.
// text = changed; // Lỗi biên dịch: không thể gán lại biến final.
```

Đây là snippet minh họa. `final` của biến ngăn gán lại tham chiếu, còn tính bất biến của `String` ngăn sửa nội dung đối tượng; đó là hai khái niệm khác nhau.

## 6. Chọn đúng API khi thao tác với một `char`

`String` có nhiều dạng thay thế. Bài này dùng **`replace(char,char)`** khi đầu vào và đầu ra đều là *một đơn vị UTF-16*; hai đối số trong dấu nháy đơn được xử lý như ký tự nguyên văn. Ba lựa chọn gần tên có mục đích khác:

| API | Điều được tìm | Số lần thay |
| --- | --- | --- |
| `replace(char, char)` | Một `char` chính xác | Tất cả vị trí khớp |
| `replace(CharSequence, CharSequence)` | Đoạn chuỗi literal, có thể dài hơn một `char` | Tất cả lần khớp không chồng lấn |
| `replaceFirst(String, String)` | Regex | Match đầu tiên |
| `replaceAll(String, String)` | Regex | Tất cả match |

Ví dụ đặt trong `main`:

~~~java
System.out.println("a.b".replace('.', '/'));      // a/b
System.out.println("a.b".replace(".", "/"));      // a/b
System.out.println("a.b".replaceAll(".", "/"));   // ///
System.out.println("a.b".replaceFirst(".", "/")); // /.b
~~~

Với regex, dấu `.` có nghĩa đặc biệt; với cả hai overload `replace`, nó chỉ là dấu chấm. `replaceAll` và `replaceFirst` còn diễn giải `$1` và backslash trong chuỗi replacement theo cú pháp regex, còn `replace` chèn replacement nguyên văn. Nếu chỉ đổi dấu phân cách đơn lẻ, overload `char,char` thể hiện đúng ý định nhất; bài so sánh ba API ở phần sau của chương đi sâu vào nhóm bắt, escape và lựa chọn regex.

## 7. Khi nào nên chọn `replace(char,char)`?

Chọn overload `char,char` khi:
- Mục tiêu là **đúng một** đơn vị UTF-16 có giá trị cố định hoặc được truyền qua biến `char`.
- Muốn thay **mọi** vị trí khớp một cách trực tiếp, ví dụ đổi dấu phân cách `','` thành `';'` hoặc đổi khoảng trắng ASCII `' '` thành `'_'`.
- Không cần regex, capture group, kiểm tra nhiều ký tự liên tiếp hay giới hạn số lần thay.
- Muốn kết quả vẫn có cùng số đơn vị UTF-16 với nguồn (vì mỗi `char` được thay bằng đúng một `char`).

Một ứng dụng thông dụng là chuẩn hóa dấu phân tách theo đúng ký tự được biết trước:

```java
String csvLike = "red,green,blue";
String separated = csvLike.replace(',', ';');
System.out.println(separated); // red;green;blue
```

Đây là snippet trong `main`. Phép thay không hiểu cú pháp CSV: nếu dấu phẩy nằm trong trường được đặt trong dấu nháy kép, thay tất cả dấu phẩy bằng dấu chấm phẩy có thể phá cấu trúc dữ liệu. Hãy dùng parser CSV nếu cần hiểu ngữ nghĩa định dạng.

Tương tự, `replace(' ', '_')` chỉ thay **dấu cách U+0020**; nó không thay mọi dạng whitespace như tab, xuống dòng, non-breaking space. Nếu cần quy tắc dựa trên lớp ký tự, hãy chọn regex Unicode phù hợp hoặc xử lý code point rõ ràng.

## 8. Phạm vi khớp: phân biệt hoa/thường, dấu và Unicode

`replace(char,char)` so khớp **chính xác từng `char`**. Nó không biết hai chữ là biến thể hoa/thường hay dấu tiếng Việt của cùng một chữ cơ sở:

```java
System.out.println("Java".replace('a', 'o')); // Jovo
System.out.println("Java".replace('A', 'o')); // Java
System.out.println("café".replace('é', 'e')); // cafe
```

Đây là snippet minh họa. Chữ `é` dạng tiền tổ hợp là một mã Unicode thuộc BMP, có thể chứa trong một `char`. Nhưng Unicode cũng cho phép biểu diễn chữ có dấu bằng **chữ cơ sở + dấu kết hợp**; hai cách biểu diễn có thể nhìn giống nhau nhưng dữ liệu `char` khác nhau.

Chương trình độc lập, lưu `ReplaceUnicodeFormDemo.java`:

```java
public class ReplaceUnicodeFormDemo {
    public static void main(String[] args) {
        String composed = "caf\u00E9";       // café: U+00E9
        String decomposed = "cafe\u0301";    // e + dấu sắc U+0301

        System.out.println(composed.replace('\u00E9', 'e'));
        System.out.println(decomposed.replace('\u00E9', 'e')
                .equals(decomposed));
        System.out.println(composed.equals(decomposed));
    }
}
```

```bash
javac -encoding UTF-8 ReplaceUnicodeFormDemo.java
java ReplaceUnicodeFormDemo
```

```text
cafe
true
false
```

Vì `decomposed` **không chứa phần tử `char` có giá trị U+00E9**, thay `'é'` không tác động gì lên chuỗi đó. Nếu nghiệp vụ cần xử lý các cách biểu diễn Unicode tương đương, cân nhắc chuẩn hóa bằng `java.text.Normalizer` trước khi áp dụng quy tắc thay; chọn dạng chuẩn hóa phù hợp và hiểu rằng chuẩn hóa cũng có thể làm thay đổi dãy code point.

## 9. `char` là đơn vị UTF-16, không phải mọi ký tự hiển thị

Java `char` có kích thước 16 bit, chứa **một code unit UTF-16**. Các Unicode code point ngoài BMP (ví dụ nhiều emoji) cần **hai** `char`: high surrogate và low surrogate. Một literal `char` không thể biểu diễn **toàn bộ** emoji như `🙂` bằng một giá trị `char` duy nhất.

Giả sử chuỗi có hai emoji:

```java
String emoji = "A🙂B🙂";
System.out.println(emoji.length()); // 6 đơn vị char
System.out.println(emoji.codePointCount(0, emoji.length())); // 4 code point
```

Không thể dùng `emoji.replace('🙂', '*')` như một cách thay nguyên emoji: ký tự emoji ngoài BMP không vừa trong một literal `char`. Hãy chọn overload nhận chuỗi để tìm đủ cả cặp surrogate:

```java
String result = emoji.replace("🙂", "*");
System.out.println(result); // A*B*
```

Chương trình hoàn chỉnh, lưu `ReplaceEmojiDemo.java`:

```java
public class ReplaceEmojiDemo {
    public static void main(String[] args) {
        String text = "A🙂B🙂";

        System.out.println(text.length());
        System.out.println(text.codePointCount(0, text.length()));
        System.out.println(text.replace("🙂", "*"));
        System.out.println(text.replace('A', 'X'));
        System.out.println(text);
    }
}
```

```bash
javac -encoding UTF-8 ReplaceEmojiDemo.java
java ReplaceEmojiDemo
```

```text
6
4
A*B*
X🙂B🙂
A🙂B🙂
```

Phép `replace("🙂", "*")` thay cả **đoạn chuỗi** gồm hai code unit; `replace('A','X')` chỉ thay code unit `A` đơn lẻ. Cả hai không sửa `text`.

**Nguy cơ với surrogate:** nếu gọi `replace(char,char)` lên riêng high surrogate hoặc low surrogate của một code point ngoài BMP, có thể phá cặp UTF-16 hợp lệ và tạo dãy surrogate lẻ. Khi cần biến đổi theo Unicode code point, dùng `String.codePoints()` và `StringBuilder.appendCodePoint()` hoặc phương thức xử lý chuỗi có ranh giới đúng; khi yêu cầu là “một ký tự người đọc thấy”, còn phải cân nhắc *grapheme cluster* (ví dụ chữ có dấu kết hợp hay emoji nối bằng ZWJ).

## 10. Viết hàm nhận `char` làm tham số và quản lý kết quả

Một hàm hỗ trợ thay ký tự có thể rất ngắn:

```java
import java.util.Objects;

public class ReplaceUtility {
    public static String replaceEvery(
            String input, char from, char to) {
        Objects.requireNonNull(input, "input");
        return input.replace(from, to);
    }

    public static void main(String[] args) {
        String original = "a-a-a";
        String updated = replaceEvery(original, '-', '_');

        System.out.println(updated);
        System.out.println(original);
    }
}
```

Lưu thành `ReplaceUtility.java` rồi chạy:

```bash
javac -encoding UTF-8 ReplaceUtility.java
java ReplaceUtility
```

```text
a_a_a
a-a-a
```

`Objects.requireNonNull` giúp báo lỗi rõ tại ranh giới hàm khi `input` là `null`; bản thân lời gọi `input.replace(...)` cũng không thể hoạt động nếu `input` là `null`. Hai tham số `char` là giá trị nguyên thủy nên không có giá trị `null`. Hàm trả một chuỗi mới về **giá trị**; các tham chiếu khác tới `original` tiếp tục thấy nội dung gốc.

Có thể truyền biến `char` thay cho literal:

```java
char delimiter = ',';
char newDelimiter = ';';
String converted = "a,b,c".replace(delimiter, newDelimiter);
// converted có nội dung "a;b;c".
```

Đây là snippet độc lập về biểu thức nhưng cần đặt vào thân phương thức Java. Nếu hệ thống cần lựa chọn ký tự tùy ý từ người dùng, kiểm tra rằng giá trị đầu vào có đúng **một đơn vị UTF-16** trước khi chuyển thành `char`; đừng dùng `input.charAt(0)` rồi lặng lẽ bỏ phần còn lại của một chuỗi có nhiều code unit.

## 11. Chi phí, nhiều phép thay và chọn công cụ phù hợp

Về mặt thuật toán, để xác định tất cả vị trí khớp, Java phải xem xét nội dung chuỗi cần xử lý. Với một lần thay trên chuỗi dài `n` code unit, có thể coi chi phí quét là **O(n)**; việc tạo kết quả và tối ưu cấp phát phụ thuộc phiên bản/cài đặt JDK. Không dựa vào giả định `replace` luôn tạo đối tượng mới, cũng không dùng so sánh tham chiếu để đo xem có thay thế.

Khi cần **nhiều loại chuyển đổi liên tiếp**, mã như sau dễ hiểu nhưng mỗi lần gọi là một phép xử lý độc lập:

```java
String normalized = "a.b-c".replace('.', '_').replace('-', '_');
// normalized: "a_b_c"
```

Đối với lượng dữ liệu rất lớn và rất nhiều quy tắc ký tự, bạn có thể duyệt một lần rồi ghi vào `StringBuilder` hoặc mảng, tránh quét lặp; lựa chọn theo độ phức tạp thực tế và đo hiệu năng. Ví dụ, nếu nhiều ký tự nguồn khác nhau đều cần được thay thành `'_'`, một vòng lặp `switch` trên từng `char` có thể biểu diễn phép thay đồng thời rõ hơn; với Unicode phức tạp, thay vì duyệt từng `char` hãy xem xét duyệt code point.

Đừng tự tối ưu quá sớm: `replace(char,char)` ngắn gọn, dễ đọc, truyền tải rõ ý định và thường phù hợp cho các phép thay ký tự đơn lẻ.

## 12. Các lỗi thường gặp

| Lỗi | Vì sao sai | Cách sửa |
| --- | --- | --- |
| Gọi `text.replace('a','o')` rồi bỏ kết quả | `String` bất biến; biến `text` không đổi | Gán lại `text = text.replace(...)` hoặc giữ biến kết quả |
| Tưởng chỉ thay lần xuất hiện đầu tiên | `replace(char,char)` thay **tất cả** vị trí khớp | Dùng thuật toán thay lần đầu nếu đó là yêu cầu |
| Dùng `replaceAll(".", "_")` để tìm dấu chấm literal | `.` trong regex khớp nhiều ký tự | Dùng `replace('.', '_')` hoặc `replace(".", "_")` |
| Tưởng `'a'` và `'A'` là cùng ký tự | Phương thức so khớp chính xác | Áp dụng chính sách hoa/thường riêng nếu cần |
| Dùng `char` cho emoji ngoài BMP | Một emoji có thể cần hai đơn vị UTF-16 | Dùng overload `replace(CharSequence,CharSequence)` với chuỗi đầy đủ |
| Giả định `'é'` khớp mọi cách viết chữ e có dấu | Dữ liệu Unicode có thể tiền tổ hợp hoặc kết hợp | Chuẩn hóa Unicode nếu yêu cầu |
| Ghép nhiều `replace` nhưng muốn xử lý đồng thời | Phép sau xử lý cả ký tự do phép trước tạo ra | Viết phép ánh xạ từng vị trí theo nguồn gốc |
| Kiểm tra `result == original` để biết có thay | `==` kiểm tra identity, không phải nội dung | Dùng `equals` hoặc so nội dung theo yêu cầu |
| Cho rằng `replace(' ', '_')` thay mọi whitespace | Chỉ thay space U+0020 | Chọn xử lý whitespace theo đặc tả |
| Dùng thay ký tự đơn để xử lý định dạng có cú pháp | Không hiểu dấu nháy/escape/grammar của CSV, URL, JSON... | Dùng parser/encoder tương ứng |
| Phá cặp surrogate bằng cách thay một nửa | `char` không luôn là code point hoàn chỉnh | Chuyển đổi theo code point hoặc chuỗi hoàn chỉnh |

## 13. Bài tập tự kiểm tra

**Bài 1.** Với `String s = "aAaA";`, kết quả của `s.replace('a','x')` là gì? Vì sao hai chữ `A` giữ nguyên?

**Bài 2.** Dự đoán kết quả:
```java
String value = "abba";
String result = value.replace('a', 'b');
System.out.println(value);
System.out.println(result);
```

**Bài 3.** Vì sao `"a.b".replace('.', '_')` và `"a.b".replaceAll(".", "_")` cho hai kết quả khác nhau?

**Bài 4.** Nếu cần đổi mọi đoạn `"cat"` thành `"dog"` trong `"cat-cat"`, bạn sẽ dùng overload nào? Vì sao `replace(char,char)` không đáp ứng trực tiếp?

**Bài 5.** Với `String source = "A🙂A";`, hãy giải thích tại sao `replace('A','X')` hoạt động như mong đợi, còn thay emoji bằng một literal `char` không phải lựa chọn hợp lệ.

Đáp án:
- Bài 1: `"xAxA"` vì `'a'` khác `'A'`.
- Bài 2: in `abba` rồi `bbbb`; mảng ký tự không liên quan, đây là hai giá trị `String` bất biến.
- Bài 3: `replace` theo `char` tìm đúng dấu chấm nên ra `a_b`; regex `.` của `replaceAll` khớp ba ký tự nên ra `___`.
- Bài 4: `"cat-cat".replace("cat", "dog")` cho `"dog-dog"`; cần thay cả một đoạn ba `char` bằng đoạn khác.
- Bài 5: `A` là một đơn vị UTF-16; emoji `🙂` cần một cặp surrogate, phải thay bằng chuỗi `"🙂"` hoặc xử lý code point.

## 14. Ghi nhớ

- `String.replace(char oldChar, char newChar)` thay **mọi `char` trùng `oldChar`** trong chuỗi, không dùng regex và không giới hạn ở lần đầu.
- Kết quả là một `String`; nội dung chuỗi nguồn vẫn bất biến. Muốn dùng kết quả, **giữ giá trị trả về**.
- `char` biểu diễn một đơn vị UTF-16. Chọn đúng overload theo đơn vị cần thay: `char` đơn lẻ, đoạn `CharSequence` literal hay một mẫu regex.
- Phép so khớp là chính xác, có phân biệt hoa/thường và biểu diễn Unicode; nếu yêu cầu nghiệp vụ khác, phải định nghĩa rõ cách chuẩn hóa/xử lý.
- Nhiều lời gọi `replace` liên tiếp được thực hiện theo thứ tự; phép sau nhìn thấy kết quả phép trước.

## Tài liệu Java SE 17

- [`String.replace(char,char)`, `replace(CharSequence,CharSequence)` và `replaceAll` — Oracle Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html)
- [`Character`, UTF-16 và surrogate — Oracle Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html)
- [`Normalizer` — Oracle Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/Normalizer.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-transformation-002">replace(CharSequence,CharSequence) với chuỗi nguyên văn</a>

<details>
<summary>Xem chi tiết</summary>

# `String.replace(CharSequence, CharSequence)` — thay thế chuỗi nguyên văn trong Java

Khi cần thay một đoạn văn bản bằng đoạn khác trong `String`, Java cung cấp overload **`replace(CharSequence target, CharSequence replacement)`**. Phương thức tìm các lần xuất hiện **liên tiếp, khớp chính xác và không chồng lấn** của `target` rồi thay chúng bằng `replacement`. Các chuỗi được hiểu là **văn bản nguyên văn (literal)**, không phải biểu thức chính quy (regex).

Ví dụ:

```java
String text = "Java Java Core";
String updated = text.replace("Java", "Kotlin");

System.out.println(updated); // Kotlin Kotlin Core
System.out.println(text);    // Java Java Core
```

Đây là thao tác **biến đổi nội dung** của chuỗi: kết quả trả về là một `String` mang nội dung mới; đối tượng `String` ban đầu vẫn giữ nguyên vì `String` là kiểu bất biến (*immutable*).

## 1. Cú pháp và ý nghĩa của hai tham số

Chữ ký API:

```java
public String replace(CharSequence target,
                      CharSequence replacement)
```

- **`target`:** dãy ký tự cần tìm. Ví dụ `"old"` hoặc `" "`. Các lần xuất hiện được tìm theo **nội dung chính xác**, phân biệt chữ hoa/thường.
- **`replacement`:** dãy ký tự đưa vào thay cho mỗi lần khớp. Nó được dùng **nguyên văn**; các ký tự như `$` và `\` trong replacement không có ý nghĩa đặc biệt của regex.
- **Giá trị trả về:** chuỗi kết quả sau thay thế tất cả các lần khớp **không chồng lấn** của `target`. Nếu không có đoạn khớp, nội dung kết quả bằng nội dung ban đầu.
- **Đối tượng nhận:** chuỗi gốc, ví dụ `text` trong `text.replace(target, replacement)`.
- **Kiểu tham số:** `CharSequence` nên không chỉ nhận `String` mà còn có thể nhận `StringBuilder` và các kiểu triển khai `CharSequence` khác.

Các trường hợp thường gặp:

| Chuỗi gốc | `target` | `replacement` | Nội dung kết quả |
| --- | --- | --- | --- |
| `"red blue red"` | `"red"` | `"green"` | `"green blue green"` |
| `"2026/10/22"` | `"/"` | `"-"` | `"2026-10-22"` |
| `"a.b.c"` | `"."` | `"-"` | `"a-b-c"` |
| `"hello"` | `"ll"` | `"LL"` | `"heLLo"` |
| `"hello"` | `"xyz"` | `"?"` | `"hello"` |
| `"AB"` | `"a"` | `"x"` | `"AB"` |

Chú ý `"a.b.c".replace(".", "-")` thay đúng hai ký tự dấu chấm. Dấu `.` ở đây **không** phải toán tử regex có nghĩa “bất kỳ ký tự nào”.

### Ví dụ đầy đủ chạy bằng Java 17+

Lưu tệp `ReplaceBasicDemo.java`:

```java
public class ReplaceBasicDemo {
    public static void main(String[] args) {
        String original = "Java Java Core";
        String updated = original.replace("Java", "Kotlin");

        System.out.println(updated);
        System.out.println(original);
        System.out.println("a.b.c".replace(".", "-"));
        System.out.println("a.b.c".replace("\\.", "-"));

        System.out.println(
                "abc".replace("xyz", "?").equals("abc"));
        System.out.println("Java".replace(
                new StringBuilder("av"),
                new StringBuilder("123")));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 ReplaceBasicDemo.java
java ReplaceBasicDemo
```

Kết quả:

```text
Kotlin Kotlin Core
Java Java Core
a-b-c
a.b.c
true
J123a
```

Lý giải:

1. Hai lần xuất hiện `"Java"` đều được thay bằng `"Kotlin"`. `original` không thay đổi.
2. `replace(".", "-")` khớp dấu chấm thật; `replace("\\.", "-")` tìm **hai ký tự** là dấu gạch chéo ngược và dấu chấm, không có trong `"a.b.c"` nên trả nội dung như cũ.
3. Không có `"xyz"` trong `"abc"` nên kết quả vẫn có cùng nội dung với `"abc"`. Ví dụ dùng `equals()` để kiểm tra **nội dung**, không dựa vào việc hai tham chiếu có trùng nhau hay không.
4. `new StringBuilder("av")` và `new StringBuilder("123")` được chấp nhận vì triển khai `CharSequence`. Trong chuỗi `Java`, đoạn `"av"` thay thành `"123"`, tạo `"J123a"`.

## 2. Tất cả lần khớp *không chồng lấn*, xét từ trái sang phải

`replace(target, replacement)` thay tất cả các lần xuất hiện **không chồng lấn** của `target`. Nếu có những lần khớp giao nhau, lần khớp sớm hơn được sử dụng trước; vị trí đã sử dụng không tiếp tục được lấy cho lần khớp khác.

Ví dụ với `"aaaaa"`, tìm `"aa"`:

```text
Chuỗi:   a a a a a
Chỉ số:  0 1 2 3 4
Khớp 1:  [0,2)  -> X
Khớp 2:      [2,4) -> X
Còn lại:          a
Kết quả: XXa
```

Đặc biệt, `"aaa".replace("aa", "b")` trả **`"ba"`** vì dùng cặp `aa` ở vị trí 0–1, rồi giữ nguyên chữ `a` cuối; không có lần khớp thứ hai không chồng lấn.

### Ví dụ biên và thay thế không lặp vô hạn

Lưu `ReplaceEdgesDemo.java`:

```java
public class ReplaceEdgesDemo {
    public static void main(String[] args) {
        System.out.println("aaaaa".replace("aa", "X"));
        System.out.println("aaa".replace("aa", "b"));
        System.out.println("banana".replace("ana", "X"));

        System.out.println("aba".replace("a", "aa"));
        System.out.println("aba".replace("", "-"));
        System.out.println("".replace("", "X"));
        System.out.println("abc".replace("b", ""));

        System.out.println(
                "ab".replace("b", "$1\\").equals("a$1\\"));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 ReplaceEdgesDemo.java
java ReplaceEdgesDemo
```

Kết quả:

```text
XXa
ba
bXna
aabaa
-a-b-a-
X
ac
true
```

Chi tiết cần hiểu:

- `"banana".replace("ana", "X")` thành `"bXna"`: `"ana"` đầu tiên từ chỉ số 1 đến trước 4 được thay, phần `"na"` còn lại giữ nguyên. Hai lần xuất hiện tiềm năng của `"ana"` chồng lên nhau nên không thay cả hai.
- `"aba".replace("a", "aa")` thành `"aabaa"`. **Không tiếp tục tìm `"a"` trong chính nội dung `"aa"` vừa thay vào**; nếu tìm lại liên tục như vậy sẽ khác hoàn toàn với một lượt thay thế.
- `"abc".replace("b", "")` thành `"ac"`: replacement rỗng có tác dụng **xóa** các lần xuất hiện của target.
- `"ab".replace("b", "$1\\")` tạo đúng `"a$1\\"` dưới dạng giá trị chuỗi Java. Dấu `$` và dấu gạch chéo ngược trong replacement là **ký tự literal**; không diễn giải group hay escape theo cú pháp regex.

### Trường hợp `target` rỗng

`""` là dãy con rỗng và có vị trí khớp ở các **ranh giới** của chuỗi. Trong trường hợp ASCII đơn giản:

```java
System.out.println("aba".replace("", "-")); // -a-b-a-
System.out.println("".replace("", "X"));    // X
```

Điều này thường gây bất ngờ: **target rỗng không có nghĩa là không thay gì**. Nếu `target` do người dùng cung cấp, cần quyết định rõ việc cho phép chuỗi rỗng hay không:

```java
if (target == null || target.isEmpty()) {
    throw new IllegalArgumentException(
            "target must be non-empty");
}
String updated = original.replace(target, replacement);
```

Đây là đoạn mã minh họa **bên trong một phương thức**, giả định `original` và `replacement` đã hợp lệ. Đặc biệt với Unicode, đừng dùng thao tác chèn tại mọi đơn vị chuỗi để thay cho thuật toán phân đoạn ký tự mà người dùng nhìn thấy; một emoji có thể chiếm nhiều đơn vị UTF-16.

## 3. Ranh giới giữa chuỗi literal và regex

`replace(CharSequence, CharSequence)` coi **cả `target` lẫn `replacement` là văn bản nguyên văn**. `replaceFirst(regex, replacement)` và `replaceAll(regex, replacement)` dùng biểu thức chính quy để tìm; `replacement` của chúng có cú pháp riêng cho `$1` và backslash.

~~~java
String source = "a.b.c";
System.out.println(source.replace(".", "-"));       // a-b-c
System.out.println(source.replaceAll(".", "-"));    // -----
System.out.println(source.replaceAll("\\.", "-")); // a-b-c
System.out.println("ab".replace("b", "$1\\"));       // a$1\
~~~

Ở overload literal, `"."` khớp đúng dấu chấm và `"$1\\"` được chèn nguyên văn. Với regex, phải escape dấu chấm thành `"\\."`; nếu pattern và replacement lấy từ dữ liệu động, `Pattern.quote(target)` và `Matcher.quoteReplacement(replacement)` giúp coi các phần tương ứng là literal. Bài so sánh `replace`, `replaceFirst`, `replaceAll` riêng trong chương trình bày sâu cách escape, nhóm bắt và lỗi regex. Khi không cần regex, chọn trực tiếp `replace(target, replacement)`.

## 4. `String` bất biến và sử dụng giá trị trả về

Phương thức tìm và tạo **giá trị kết quả**, không sửa `String` nhận lời gọi:

~~~java
String original = "red red";
String updated = original.replace("red", "blue");
System.out.println(original); // red red
System.out.println(updated);  // blue blue
~~~

Nếu chỉ viết `original.replace("red", "blue");` rồi bỏ giá trị trả về, biến `original` vẫn chứa `"red red"`. Có thể gán lại một biến khi không cần dùng bản gốc; nếu cần hiển thị hay đối chiếu nội dung đã nhận, giữ `original` và `updated` riêng. Với nhiều phép thay nối tiếp, phép sau chạy trên kết quả của phép trước; thứ tự quy tắc có thể thay đổi kết quả.

## 5. Kiểm tra null và dữ liệu đầu vào

Không có đối tượng để gọi `replace()` nếu biến nhận là `null`. Ngoài ra, đối với overload `replace(CharSequence, CharSequence)`, Java yêu cầu **cả target và replacement đều khác `null`**.

```java
String text = "hello";

// Các lời gọi sau sẽ ném NullPointerException:
// text.replace((CharSequence) null, "x");
// text.replace("l", (CharSequence) null);

String missing = null;
// missing.replace("a", "b");
```

Nếu đầu vào có thể vắng mặt, cần chọn chính sách rõ ràng: từ chối bằng ngoại lệ, giữ nguyên, hoặc trả về giá trị khác theo yêu cầu nghiệp vụ. Ví dụ utility trả về `null` nếu chuỗi gốc `null`, còn hai tham số thay thế phải hợp lệ:

```java
import java.util.Objects;

public class SafeReplaceDemo {
    public static String replaceOrNull(
            String text, String target, String replacement) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(replacement, "replacement");
        return text == null
                ? null
                : text.replace(target, replacement);
    }

    public static void main(String[] args) {
        System.out.println(
                replaceOrNull("a/b/c", "/", "-"));
        System.out.println(
                replaceOrNull(null, "/", "-") == null);
    }
}
```

Chạy `javac -encoding UTF-8 SafeReplaceDemo.java` và `java SafeReplaceDemo`, kết quả:

```text
a-b-c
true
```

Phương thức ví dụ cố ý chọn chính sách trả về `null` khi `text` thiếu. Trong API khác, ném `IllegalArgumentException` hoặc `NullPointerException` theo hợp đồng có thể phù hợp hơn. **Đừng tự biến `null` thành chuỗi `"null"`** khi nghiệp vụ chưa quy định cách xử lý.

## 6. Unicode và phân biệt chữ hoa/thường

`String.replace(CharSequence, CharSequence)` so khớp dãy ký tự **chính xác theo nội dung UTF-16**, không tự áp dụng chuẩn hóa Unicode, không bỏ dấu và không so khớp theo văn hóa/ngôn ngữ.

Hai biểu diễn nhìn giống nhau có thể không có cùng dãy code unit:

- `"\u00E9"` là ký tự `é` dựng sẵn (U+00E9).
- `"e\u0301"` gồm `e` (U+0065) và dấu sắc kết hợp U+0301.

Ví dụ tệp `ReplaceUnicodeDemo.java`:

```java
import java.text.Normalizer;

public class ReplaceUnicodeDemo {
    public static void main(String[] args) {
        String composed = "Caf\u00E9";
        String decomposed = "Cafe\u0301";

        System.out.println(
                composed.replace("e\u0301", "X"));

        String normalized = Normalizer.normalize(
                decomposed, Normalizer.Form.NFC);
        System.out.println(
                normalized.replace("\u00E9", "X"));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 ReplaceUnicodeDemo.java
java ReplaceUnicodeDemo
```

Kết quả:

```text
Café
CafX
```

Chuỗi đầu không chứa chuỗi hai code point `e + dấu sắc kết hợp`, nên nội dung không đổi. Sau khi chuẩn hóa chuỗi phân rã về `NFC`, chuỗi có ký tự dựng sẵn và thay `é` bằng `X` thành công.

Nếu yêu cầu xử lý tương đương Unicode, **chuẩn hóa cả nguồn và target** về cùng một dạng phù hợp trước khi thay; cần nhớ normalization có thể làm thay đổi code point và offset. Nếu yêu cầu là tìm kiếm không phân biệt hoa/thường, cân nhắc phép biến đổi chữ theo `Locale` hoặc thuật toán Unicode case folding phù hợp; `replace()` mặc định **không bỏ qua** chữ hoa/thường.

Cần thận trọng với emoji, dấu kết hợp và surrogate pairs: phương thức tìm đoạn theo UTF-16, **không cam kết** giữ ranh giới *grapheme cluster* (cụm ký tự người dùng cảm nhận). Nếu mục tiêu là thay một ký tự hiển thị hoàn chỉnh, cần xác định ranh giới Unicode theo quy tắc ứng dụng.

## 7. Khi nào nên dùng `replace()`?

Một số tình huống hợp lý:

- **Đổi ký hiệu phân cách cố định:** `"a/b/c".replace("/", "-")` → `"a-b-c"`.
- **Thay token văn bản đã được nhận diện:** `"Hello, {name}!".replace("{name}", "Lan")` → `"Hello, Lan!"`, trong ngữ cảnh đầu vào và thứ tự thay thế đã được kiểm soát.
- **Loại bỏ một đoạn cụ thể:** `"a--b".replace("--", "")` → `"ab"`.
- **Chuẩn hóa cách viết một cụm cố định trong văn bản nội bộ:** `"ERROR: ERROR:".replace("ERROR", "WARN")` → `"WARN: WARN:"`.

Lưu ý, **thay thế nguyên văn không phải thao tác parse**: thay `"cat"` trong `"catalog"` cũng có thể làm thay một phần từ lớn hơn. Với token nghiệp vụ, mã định danh, placeholder có escape/nesting hoặc dữ liệu cấu trúc, nên dùng parser hoặc cơ chế template chuyên dụng để tránh thay nhầm.

Với văn bản dài, nhiều mục tiêu thay thế hoặc gọi `replace()` lặp trong vòng lặp, hiệu năng phụ thuộc cách xử lý và dữ liệu. Mỗi lần gọi trả về một `String` theo ngữ nghĩa API, nhưng số lượng cấp phát thực tế là chi tiết triển khai và có thể được JVM tối ưu. Khi cần ghép nhiều phần trong vòng lặp, `StringBuilder` thường phù hợp; khi có nhiều mẫu thay thế hoặc yêu cầu tránh các lần thay lẫn nhau, nên thiết kế một phép xử lý duy nhất dựa trên quy tắc thay thế cụ thể.

Ví dụ sự khác nhau giữa **một lần gọi** và **hai lần gọi nối tiếp**:

```java
String text = "ab";
System.out.println(text.replace("a", "b"));   // bb
System.out.println(text.replace("a", "b")
                       .replace("b", "c"));     // cc
```

Trong hai lần gọi nối tiếp, lần thứ hai tìm cả `"b"` cũ lẫn `"b"` do lần thứ nhất tạo ra. Điều này **khác** với một lần thay thế `target`: kết quả vừa chèn **không bị xử lý lại trong chính lần gọi đó**.

## 8. Các lỗi thường gặp

1. **Dùng `replaceAll()` khi target là văn bản nguyên văn:** dấu `.`, `*`, `[`, `(` hoặc `\` có thể mang nghĩa regex và dẫn đến kết quả sai hoặc ngoại lệ `PatternSyntaxException`. Dùng `replace(CharSequence, CharSequence)` cho văn bản literal.
2. **Nghĩ chỉ thay lần đầu tiên:** `replace()` thay **tất cả** lần khớp không chồng lấn; với regex, `replaceFirst()` là API riêng cho lần đầu.
3. **Không lưu giá trị trả về:** `text.replace(...)` không biến đổi đối tượng `text` vì `String` bất biến.
4. **Đặt `$1` trong replacement rồi chờ group substitution:** overload `replace(CharSequence, CharSequence)` **không có** group capture. `$1` được chèn nguyên văn.
5. **Tưởng target rỗng luôn vô tác dụng:** `"ab".replace("", "-")` có thể chèn thêm ký tự ở các ranh giới; cần kiểm tra/định nghĩa chính sách đầu vào.
6. **Quên null:** target hoặc replacement null gây `NullPointerException`; biến chuỗi nhận null cũng không thể gọi phương thức.
7. **Nhầm khớp chuỗi con với khớp từ:** `"catalog".replace("cat", "dog")` trở thành `"dogalog"` vì `replace()` không hiểu ranh giới từ.
8. **Trông chờ không phân biệt chữ hoa/thường:** `"Java".replace("java", "X")` vẫn là `"Java"`.
9. **Chỉ test ASCII:** dữ liệu có dấu, Unicode normalization, emoji và dấu kết hợp có thể đòi hỏi quy tắc xử lý thêm.
10. **Thay thế nhiều lần mà không xét thứ tự:** đầu ra từ phép thay thứ nhất có thể trở thành target của phép thứ hai nếu gọi `replace()` nối tiếp.
11. **Dựa vào `==` để kiểm tra kết quả:** nên dùng `equals()` để so nội dung thay vì so tham chiếu.

## 9. Tự kiểm tra

Với `String s = "aaaa";`:

| Lời gọi | Kết quả | Giải thích |
| --- | --- | --- |
| `s.replace("a", "X")` | `"XXXX"` | Có bốn lần khớp literal `a` |
| `s.replace("aa", "b")` | `"bb"` | Hai đoạn `aa` không chồng lấn |
| `s.replace("aaa", "Z")` | `"Za"` | Một đoạn `aaa` từ trái sang phải |
| `s.replace("a", "")` | `""` | Xóa tất cả `a` |
| `s.replace("A", "X")` | `"aaaa"` | Phân biệt hoa/thường |
| `s.replace("b", "X")` | `"aaaa"` | Không có target |
| `s.replace("", "-")` | `"-a-a-a-a-"` | Thay target rỗng tại ranh giới |

Bạn có thể tự tạo file Java cho các ví dụ trên và dùng `System.out.println(...)` để kiểm tra. Nếu đang xử lý dữ liệu đến từ người dùng, hãy thử thêm chuỗi rỗng, `null`, target dài hơn nguồn, ký tự đặc biệt như `.`/`$`/`\` và chuỗi tiếng Việt có dấu.

**Ghi nhớ:** `replace(CharSequence target, CharSequence replacement)` thực hiện thay thế **mọi lần xuất hiện literal, không chồng lấn**; **target và replacement đều được hiểu nguyên văn**. `replaceAll()` dùng **regex** và có quy tắc thay thế khác. Luôn sử dụng giá trị `String` trả về, kiểm tra đầu vào `null`/rỗng theo yêu cầu, và chỉ dùng `equals()` khi cần so nội dung chuỗi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-transformation-003">Phân biệt replace với replaceFirst và replaceAll</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt replace, replaceFirst và replaceAll trong Java

Ba phương thức xử lý chuỗi `replace()`, `replaceFirst()` và `replaceAll()` đều tạo kết quả biến đổi từ một `String`. Điểm khác biệt quyết định là **phần cần tìm có được coi là văn bản nguyên vẹn (literal) hay regex**, và **thay một hay nhiều lần**. Ngoài ra, với hai phương thức regex, chuỗi thay thế có cú pháp đặc biệt dành cho các nhóm bắt.

**Quy tắc cốt lõi:** `replace()` tìm literal và thay **tất cả**; `replaceFirst()` tìm regex và thay **khớp đầu tiên**; `replaceAll()` tìm regex và thay **tất cả các khớp không chồng lấp**.

## 1. So sánh API

| Phương thức | Cách hiểu phần cần tìm | Số lần thay | Cách hiểu nội dung thay thế |
|---|---|---|---|
| `replace(char, char)` | Một ký tự `char` theo UTF-16 | Mọi lần xuất hiện | Literal |
| `replace(CharSequence, CharSequence)` | Chuỗi con literal | Mọi lần xuất hiện không chồng lấp | Literal, kể cả dollar và backslash |
| `replaceFirst(String regex, String replacement)` | Regex | Khớp đầu tiên | Có cú pháp nhóm bắt như `$1` |
| `replaceAll(String regex, String replacement)` | Regex | Tất cả các khớp | Có cú pháp nhóm bắt như `$1` |

Ví dụ:

~~~java
String text = "a.b.a";
System.out.println(text.replace(".", "-"));      // a-b-a
System.out.println(text.replaceFirst(".", "-")); // -.b.a
System.out.println(text.replaceAll(".", "-"));   // -----
~~~

Trong `replace()`, dấu chấm là ký tự chấm thật. Trong regex, dấu chấm thường có nghĩa là khớp một ký tự bất kỳ (trừ một số ký tự kết thúc dòng nếu không bật chế độ DOTALL), nên `replaceAll(".", "-")` thay cả năm ký tự.

Với `String` bất biến, ba phép gọi đều **không sửa đối tượng `text`**. Muốn giữ kết quả, phải gán lại biến hoặc chuyển trực tiếp kết quả sang bước xử lý tiếp theo.

## 2. replace(): thay mọi chuỗi con literal

Hai overload của `String` là:

~~~java
public String replace(char oldChar, char newChar)
public String replace(CharSequence target, CharSequence replacement)
~~~

Đây là **chữ ký API minh họa**, không phải một file Java chạy độc lập.

Đặc tính:

- `replace(char,char)` thay mọi code unit bằng `oldChar` bằng `newChar`.
- `replace(CharSequence,CharSequence)` tìm một đoạn literal và thay mọi lần xuất hiện không chồng lấp.
- Pattern và replacement đều **không phải regex**. Dấu chấm, dấu sao, dollar, ngoặc vuông hoặc backslash mang nghĩa văn bản thuần.
- Tên `replace` không có chữ `All`, nhưng phương thức này vẫn thay **tất cả lần xuất hiện** của literal.

~~~java
System.out.println("a.b".replace(".", "-"));         // a-b
System.out.println("a.b".replace('.', '_'));         // a_b
System.out.println("a.b".replace(".", "$1"));        // a$1b
System.out.println("red red".replace("red", "blue")); // blue blue
System.out.println("aaaa".replace("aa", "X"));       // XX
~~~

Ở ví dụ `"aaaa"`, các đoạn `"aa"` được chọn tại vị trí 0 và 2, nên cho `"XX"`. Những lần khớp **không chồng lấp**; phương thức không duyệt mọi đoạn có thể bắt đầu ở từng vị trí.

Với emoji như 🚀, một ký tự hiển thị có thể gồm hai UTF-16 `char`, nên muốn thay trọn emoji nên chọn overload nhận `CharSequence`:

~~~java
System.out.println("🚀 + 🚀".replace("🚀", "⭐")); // ⭐ + ⭐
~~~

Nếu `target` là chuỗi rỗng, phép thay có thể chèn chuỗi thay vào đầu, giữa các code unit và cuối: `"ab".replace("", "-")` cho `"-a-b-"`. Cần xác thực giá trị tìm kiếm rỗng nếu không muốn hành vi đó.

## 3. replaceFirst(): chỉ thay kết quả regex đầu tiên

Chữ ký:

~~~java
public String replaceFirst(String regex, String replacement)
~~~

Tham số đầu được biên dịch như regex. Java tìm khớp đầu tiên bên trong chuỗi rồi thay đúng một lần. Nó không yêu cầu toàn bộ chuỗi khớp regex.

~~~java
String text = "Mã 12, mã 345, mã 67";
System.out.println(text.replaceFirst("\\d+", "#"));
// Mã #, mã 345, mã 67

System.out.println("red red".replaceFirst("red", "blue")); // blue red
System.out.println("abc123def".replaceFirst("\\d+", "#")); // abc#def
System.out.println("hello".replaceFirst("\\d+", "#")); // hello
~~~

Regex `\\d+` biểu diễn một hoặc nhiều chữ số. Vì cần truyền backslash vào regex engine, **mã nguồn Java** phải viết `"\\d+"`, không thể viết ký tự escape Java không hợp lệ. `replaceFirst()` dùng việc tìm *đoạn con* tương tự `Matcher.find()`, khác `String.matches()` kiểm tra **toàn bộ** chuỗi.

Để thay dấu chấm đầu tiên theo nghĩa literal bằng API regex, cần escape dấu chấm:

~~~java
System.out.println("a.b.c".replaceFirst("\\.", "/")); // a/b.c
~~~

Nếu chỉ có một từ cần thay theo nghĩa literal, có thể dùng `Pattern.quote(target)` trước khi gọi `replaceFirst()`, và `Matcher.quoteReplacement(replacement)` khi chuỗi thay có dấu dollar hoặc backslash.

## 4. replaceAll(): thay mọi kết quả regex

Chữ ký:

~~~java
public String replaceAll(String regex, String replacement)
~~~

Nó tìm các match regex không chồng lấp và thay tất cả. Regex linh hoạt hơn literal vì có thể mô tả nhóm ký tự, số lần lặp, ranh giới và các điều kiện khác.

~~~java
String text = "Mã 12, mã 345, mã 67";
System.out.println(text.replaceAll("\\d+", "#"));
// Mã #, mã #, mã #

System.out.println("A  B   C".replaceAll(" +", " ")); // A B C
System.out.println("abc123def456".replaceAll("\\d+", "#")); // abc#def#
System.out.println("a,b;c".replaceAll("[,;]", "|")); // a|b|c
~~~

Regex `\\d+` gom một **chuỗi chữ số liên tiếp** thành một match. Regex `" +"` tìm cụm một hoặc nhiều dấu cách. Nếu cần nhóm whitespace rộng hơn, cân nhắc `\\s+` nhưng phải hiểu quy tắc lớp ký tự và Unicode flags của Java; nó không tự tương đương mọi tiêu chí Unicode whitespace trong ứng dụng.

Nếu chỉ thay mọi lần xuất hiện một đoạn cố định, `replace()` thường phù hợp hơn `replaceAll()` vì không phải diễn giải regex.

## 5. Escape: có hai tầng cú pháp khác nhau

Khi viết Java source, phải phân biệt:

1. **Java string literal:** dấu backslash là dấu escape cho Java.
2. **Regex pattern:** Java regex xử lý các toán tử như `.`, `*`, `+`, `[]`, `()`, `^`, dollar và các character class.
3. **Regex replacement:** dấu dollar đi cùng chỉ số nhóm như `$1`, `$2` có ý nghĩa thay bằng nhóm được bắt; backslash có thể escape ký tự trong replacement.

### 5.1. Pattern có ký tự đặc biệt

| Mục đích | Java String literal dùng làm regex |
|---|---|
| Một hoặc nhiều chữ số | `"\\d+"` |
| Dấu chấm thật | `"\\."` |
| Dấu dollar thật | `"\\$"` |
| Một hoặc nhiều dấu cách ASCII | `" +"` |
| Chuỗi con literal do người dùng nhập | `Pattern.quote(userText)` |

Chú ý trong Java 17, Java escape `"\s"` tạo **một dấu cách** trước khi tới regex engine. Nếu muốn regex `\\s`, phải viết `"\\s"` ở source. Đây là một lỗi dễ lọt qua quá trình biên dịch vì `"\s"` là Java String literal hợp lệ trên Java hiện đại nhưng không có nghĩa regex whitespace class.

### 5.2. Replacement có `$1` và các nhóm bắt

Ví dụ đổi thứ tự họ và tên được ngăn cách bởi dấu phẩy:

~~~java
String name = "Nguyen, An";
String result = name.replaceAll("(\\w+),\\s*(\\w+)", "$2 $1");
System.out.println(result); // An Nguyen
~~~

Regex có hai nhóm bắt: nhóm 1 là `Nguyen`, nhóm 2 là `An`. Trong replacement, `$2 $1` lấy nhóm 2 trước rồi đến nhóm 1. Nếu muốn nhóm chỉ để gom cấu trúc mà không đánh số, dùng dạng `(?:...)`; nhóm có tên được khai báo với cú pháp `(?<name>...)` và tham chiếu bằng tên theo tài liệu `Matcher`.

Trong `replace()`, replacement `"$1"` là literal, không tham chiếu nhóm. Trong `replaceAll()` hoặc `replaceFirst()`, replacement `"$1"` có nghĩa đặc biệt; nếu không có nhóm bắt tương ứng, có thể ném exception. Không trộn hai ngữ nghĩa.

### 5.3. Dữ liệu replacement có dollar/backslash

Nếu replacement đến từ người dùng và cần giữ nguyên tất cả ký tự, dùng `Matcher.quoteReplacement()`:

~~~java
import java.util.regex.Matcher;

String replacement = "$1\\temp";
String result = "abc".replaceAll(
        "b", Matcher.quoteReplacement(replacement));

System.out.println(result.equals("a$1\\tempc")); // true
~~~

`quoteReplacement()` bảo đảm dollar và backslash **không bị hiểu như cú pháp replacement**. Hàm này **không** biến regex trong tham số tìm kiếm thành literal.

### 5.4. Dữ liệu tìm kiếm có dấu regex

Nếu muốn tìm dấu chấm do người dùng nhập, truyền trực tiếp dấu chấm vào `replaceAll()` sẽ gây khớp rộng. Dùng `Pattern.quote()` để bảo vệ bên tìm kiếm:

~~~java
import java.util.regex.Matcher;
import java.util.regex.Pattern;

String source = "a.b";
String find = ".";
String put = "$5\\x";
String converted = source.replaceAll(
        Pattern.quote(find),
        Matcher.quoteReplacement(put));

System.out.println(converted.equals("a$5\\xb")); // true
~~~

`Pattern.quote()` bảo vệ cú pháp regex **pattern**; `Matcher.quoteReplacement()` bảo vệ cú pháp **replacement**. Nếu cả hai đầu đều literal, viết `source.replace(find, put)` đơn giản và rõ ràng hơn.

## 6. Trường hợp biên quan trọng

### Match rỗng hoặc có độ rộng bằng 0

~~~java
System.out.println("ab".replace("", "-"));         // -a-b-
System.out.println("ab".replaceFirst("", "-"));    // -ab
System.out.println("ab".replaceAll("", "-"));      // -a-b-
~~~

Regex có thể khớp tại **vị trí** không tiêu thụ ký tự, chẳng hạn regex rỗng hay lookahead. Regex engine có quy tắc tiến vị trí để tránh lặp vô hạn trong lúc thay. Vì vậy cần kiểm tra input tìm kiếm rỗng trước nếu đó không phải kết quả mong đợi.

### Không thay các match chồng lấp

~~~java
System.out.println("aaaa".replace("aa", "X"));       // XX
System.out.println("aaaa".replaceFirst("aa", "X"));  // Xaa
System.out.println("aaaa".replaceAll("aa", "X"));    // XX
~~~

Ở đây regex hoặc phép tìm literal xét các match không chồng lấp. Nếu cần một thuật toán đặc biệt xử lý cả các vị trí chồng lấp, hãy thiết kế bài toán riêng với lookaround, vị trí hoặc logic quét chuỗi; không mặc định những API trên làm việc đó.

### Replacement mới không bị thay tiếp trong cùng lượt

~~~java
System.out.println("aaa".replace("a", "aa")); // aaaaaa
~~~

`replace()` thực hiện một lượt trên dữ liệu gốc: ba ký tự `a` được thay thành sáu ký tự `a`. Nó không tự lặp thay trên phần văn bản mới sinh ra cho đến vô hạn.

### Không tự thay đổi String ban đầu

~~~java
String s = "red red";
s.replace("red", "blue");
System.out.println(s); // red red
s = s.replace("red", "blue");
System.out.println(s); // blue blue
~~~

Nhớ sử dụng giá trị trả về do `String` immutable.

### Null và regex không hợp lệ

- Tham chiếu nhận `String` là null: gọi phương thức sẽ ném `NullPointerException`.
- Target/pattern/replacement cần dữ liệu không null. Phải xác thực trước; không dùng null để diễn đạt “bỏ qua thay thế”.
- Regex sai cú pháp, ví dụ `"["`, có thể ném `PatternSyntaxException`.
- Replacement có tham chiếu nhóm không tồn tại có thể ném exception; `Matcher.quoteReplacement()` là cách giải quyết khi muốn literal.

~~~java
try {
    "abc".replaceAll("[", "x");
} catch (java.util.regex.PatternSyntaxException e) {
    System.out.println("Invalid regex"); // Invalid regex
}
~~~

### Unicode và ký tự hiển thị

Overload `char` làm việc theo UTF-16 code unit; emoji thường gồm hai `char`. Dùng overload `CharSequence` khi muốn thay một emoji nguyên vẹn. Đồng thời, `replace()` và regex **không tự chuẩn hóa Unicode**. Hai chuỗi nhìn giống nhau, như `é` dựng sẵn và `e` cộng dấu sắc, có thể không khớp theo dãy code point; nếu cần đối chiếu biểu diễn tương đương, sử dụng `Normalizer` một cách có chủ đích.

## 7. Hiệu năng và an toàn khi xử lý dữ liệu động

Với các literal ngắn hoặc từ khóa cố định, `replace()` thường rõ nghĩa và tránh công việc xử lý regex không cần thiết. Nếu phải áp dụng **một regex lặp lại nhiều lần**, có thể compile `Pattern` một lần rồi dùng `Matcher` trên từng chuỗi:

~~~java
import java.util.regex.Pattern;

Pattern numbers = Pattern.compile("\\d+");
System.out.println(numbers.matcher("A12").replaceAll("#"));  // A#
System.out.println(numbers.matcher("B345").replaceAll("#")); // B#
~~~

`Pattern` có thể chia sẻ và tái sử dụng; `Matcher` thay đổi trạng thái và không nên dùng đồng thời giữa nhiều thread nếu không quản lý an toàn. Đừng khẳng định regex luôn chậm hơn trong mọi trường hợp; đo bằng profiler/JMH với dữ liệu đại diện khi có yêu cầu hiệu năng.

**Regex nhận từ người dùng** có thể bị lỗi cú pháp và một số mẫu có thể gây xử lý rất chậm với input phù hợp (*ReDoS*). Nếu người dùng chỉ muốn tìm literal, tránh đưa input vào regex nguyên trạng: dùng `replace()` hoặc `Pattern.quote()`. Nếu thật sự cung cấp tính năng regex, giới hạn độ dài input/pattern, kiểm thử worst case và đặt các chính sách bảo vệ tài nguyên. `Pattern.quote()` khiến pattern trở thành literal, không phải cách “làm regex tùy ý an toàn” mà vẫn giữ chức năng regex.

Khi thay liên tiếp trên một `String` lớn trong nhiều vòng lặp, tạo `String` trung gian có thể tốn bộ nhớ; cân nhắc `StringBuilder` hoặc thuật toán xử lý theo luồng tùy bài toán.

## 8. Chương trình Java 17 hoàn chỉnh

Lưu đoạn sau vào file UTF-8 **`ReplaceMethodsDemo.java`**. Khác với các snippet riêng lẻ ở phần trên, đây là một chương trình chạy độc lập.

~~~java
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReplaceMethodsDemo {
    public static void main(String[] args) {
        String source = "a.b.a";
        System.out.println("literal=" + source.replace(".", "-"));
        System.out.println("regexFirst=" + source.replaceFirst(".", "-"));
        System.out.println("regexAll=" + source.replaceAll(".", "-"));
        System.out.println("escapedFirst=" + source.replaceFirst("\\.", "-"));
        System.out.println("escapedAll=" + source.replaceAll("\\.", "-"));

        String repeated = "aa aa aa";
        System.out.println("repeatLiteral=" + repeated.replace("aa", "X"));
        System.out.println("repeatFirst=" + repeated.replaceFirst("aa", "X"));
        System.out.println("repeatAll=" + repeated.replaceAll("aa", "X"));

        String digits = "AB12-CD034";
        System.out.println("digitsFirst=" + digits.replaceFirst("\\d+", "#"));
        System.out.println("digitsAll=" + digits.replaceAll("\\d+", "#"));

        String name = "Nguyen, An";
        System.out.println("group=" +
                name.replaceAll("(\\w+),\\s*(\\w+)", "$2 $1"));

        String find = ".";
        String replace = "$5\\x";
        String safe = "a.b".replaceAll(Pattern.quote(find),
                Matcher.quoteReplacement(replace));
        System.out.println("safeLiteral=" + safe.equals("a$5\\xb"));

        System.out.println("overlap=" + "aaaa".replace("aa", "X"));
        System.out.println("emptyTarget=" + "ab".replace("", "-"));
        System.out.println("original=" + source);
    }
}
~~~

Biên dịch và chạy trên JDK 17 hoặc mới hơn:

~~~bash
javac -encoding UTF-8 ReplaceMethodsDemo.java
java ReplaceMethodsDemo
~~~

Đầu ra:

~~~text
literal=a-b-a
regexFirst=-.b.a
regexAll=-----
escapedFirst=a-b.a
escapedAll=a-b-a
repeatLiteral=X X X
repeatFirst=X aa aa
repeatAll=X X X
digitsFirst=AB#-CD034
digitsAll=AB#-CD#
group=An Nguyen
safeLiteral=true
overlap=XX
emptyTarget=-a-b-
original=a.b.a
~~~

`regexAll` cho năm dấu `-` vì regex `.` khớp toàn bộ từng ký tự của `a.b.a`. `safeLiteral=true` xác minh cả dollar và backslash của replacement được giữ nguyên nhờ `Matcher.quoteReplacement()`. `original=a.b.a` cho thấy các phương thức không đổi nội dung String nguồn.

## 9. Lỗi thường gặp

| Lỗi | Vì sao | Cách xử lý |
|---|---|---|
| `replaceAll(".", "X")` để thay dấu chấm | Dấu chấm là toán tử regex | Dùng `replace(".", "X")` hoặc regex dấu chấm đã escape |
| Nghĩ `replace()` chỉ thay một lần | Tên phương thức dễ gây nhầm | `replace()` thay tất cả literal |
| Tìm văn bản từ người dùng qua regex nguyên trạng | Dấu ngoặc/sao/chấm có nghĩa đặc biệt | `replace()` hoặc `Pattern.quote()` |
| Replacement từ người dùng có dollar | Có thể bị giải nghĩa như nhóm regex | `Matcher.quoteReplacement()` |
| Viết Java regex `"\d+"` | Dấu escape của Java không hợp lệ | `"\\d+"` |
| Viết `"\s+"` để tìm whitespace regex | Trong Java mới, `\\s` ở mức Java literal có thể là dấu cách | Viết `"\\s+"` |
| Không dùng giá trị trả về | String bất biến | Gán lại hoặc chuyển tiếp kết quả |
| Dùng `replace(char,char)` cho cả emoji | Một emoji có thể gồm hai code unit | Dùng `replace(CharSequence,CharSequence)` |
| Tưởng thay thế diễn ra đệ quy | Một lượt match trên chuỗi đầu vào | Nếu cần nhiều pha, xây dựng rõ các bước |
| Chấp nhận regex tùy ý không giới hạn | Có thể lỗi syntax hoặc tiêu tốn CPU | Validate, giới hạn và kiểm thử |

## 10. Checklist chọn đúng phương thức

1. **Thay literal tại mọi nơi:** chọn `replace()`.
2. **Thay match regex đầu tiên:** chọn `replaceFirst()`.
3. **Thay tất cả match regex:** chọn `replaceAll()`.
4. **Có dấu chấm, dollar, backslash hoặc dấu ngoặc trong input:** phân biệt ký tự thuộc *pattern* hay *replacement*; dùng quote phù hợp nếu phải đi qua regex.
5. **Input rỗng, null hoặc Unicode phức tạp:** kiểm tra và quyết định ngữ nghĩa từ trước.
6. **Regex được áp dụng nhiều lần:** có thể compile `Pattern` một lần; dùng `Matcher` mới hoặc được quản lý đúng cho mỗi tác vụ.
7. **Có nhu cầu tối ưu hay xử lý dữ liệu không tin cậy:** đo hiệu năng và kiểm soát kích thước/độ phức tạp regex.

**Ghi nhớ:** `replace()` thay mọi literal; `replaceFirst()` thay regex match đầu tiên; `replaceAll()` thay mọi regex match. Với regex, **pattern và replacement có cú pháp khác nhau**, và còn phải escape đúng khi viết Java source.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-transformation-004">repeat: số lần lặp, trường hợp rỗng và số âm</a>

<details>
<summary>Xem chi tiết</summary>

# `String.repeat(int)`: số lần lặp, chuỗi rỗng và giá trị âm trong Java

## 1. `repeat()` dùng để làm gì?

Khi cần lặp lại **nguyên vẹn một chuỗi** nhiều lần và ghép các bản sao liền nhau, lớp `java.lang.String` cung cấp phương thức:

```java
public String repeat(int count)
```

Phương thức `String.repeat(int)` có từ **Java 11** và dùng được trên Java 17 trở lên. Nó tạo ra **giá trị chuỗi kết quả** bằng cách lặp toàn bộ nội dung của chuỗi nhận lời gọi đúng `count` lần. Không có dấu phân cách nào được tự thêm vào giữa các lần lặp.

Ví dụ, trong phần thân một phương thức Java:

```java
String word = "ha";
String result = word.repeat(3);

System.out.println(result); // hahaha
```

Cách hiểu trực quan:

```text
"ha".repeat(3)
    = "ha" + "ha" + "ha"
    = "hahaha"
```

`repeat` đặc biệt tiện để tạo các dấu phân cách, ký tự đệm, đường kẻ minh họa, hoặc chuỗi mẫu với số lần lặp đã biết. Nó **không sửa nội dung của chuỗi gốc** vì `String` là bất biến (*immutable*).

**Quy tắc quan trọng nhất:** tham số `count` phải **không âm**. Giá trị `0` là hợp lệ và cho chuỗi rỗng; số âm khiến phương thức ném `IllegalArgumentException`.

## 2. Bảng hành vi theo số lần lặp

Giả sử chuỗi nhận lời gọi là `"ab"`:

| Biểu thức | Kết quả hoặc hành vi | Ý nghĩa |
|---|---|---|
| `"ab".repeat(0)` | `""` | Lặp không lần nào |
| `"ab".repeat(1)` | `"ab"` | Giữ cùng nội dung, một lần |
| `"ab".repeat(2)` | `"abab"` | Nối hai bản sao |
| `"ab".repeat(3)` | `"ababab"` | Nối ba bản sao |
| `"ab".repeat(-1)` | `IllegalArgumentException` | Số lần lặp âm là không hợp lệ |

Điều này khác cách một số người mới học có thể phỏng đoán:

- `repeat(0)` **không** trả về chuỗi gốc và cũng **không** trả `null`. Nó trả về chuỗi rỗng `""`.
- `repeat(1)` cho **nội dung giống chuỗi gốc**. Không nên dựa vào phép so sánh tham chiếu `==` để kiểm tra kết quả, dù triển khai JDK có thể tái sử dụng đối tượng trong trường hợp này.
- `repeat(2)` trở lên nối các bản sao **không chèn khoảng trắng, dấu phẩy hay xuống dòng** giữa chúng.
- `repeat(-1)` không có nghĩa là “bỏ một phần tử” hoặc “lặp ngược”. Đó là đối số sai, nên Java ném ngoại lệ.

Một chuỗi nhiều ký tự luôn được lặp **cả cụm**:

```java
System.out.println("Java ".repeat(3)); // Java Java Java 
System.out.println("ab".repeat(3));    // ababab
System.out.println("-=".repeat(3));    // -=-=-=
```

Trong ví dụ đầu, khoảng trắng nằm **sẵn trong** chuỗi `"Java "`. Do đó kết quả có cả dấu cách cuối cùng. Nếu gọi `"Java".repeat(3)`, kết quả sẽ là `"JavaJavaJava"`.

## 3. Trường hợp chuỗi nhận lời gọi rỗng

Một **chuỗi rỗng** được biểu diễn bằng `""` và có độ dài bằng `0`. Khi nội dung gốc rỗng, lặp bao nhiêu lần **không âm** thì kết quả vẫn rỗng:

```java
"".repeat(0) // ""
"".repeat(1) // ""
"".repeat(5) // ""
```

Bởi vì nối các chuỗi rỗng không tạo thêm ký tự nào:

```text
"" + "" + "" = ""
```

Dù chuỗi nhận lời gọi có nội dung hay không, quy tắc đối với **số âm** vẫn giữ nguyên:

```java
"".repeat(-2); // Ném IllegalArgumentException
```

Đây là điểm cần nhớ: **chuỗi rỗng không làm cho số lần lặp âm trở nên hợp lệ**.

### Chuỗi rỗng khác với `null`

```java
String empty = "";
System.out.println(empty.repeat(3).isEmpty()); // true

String missing = null;
// missing.repeat(3); // NullPointerException
```

`empty` tham chiếu đến một đối tượng `String` hợp lệ có `length() == 0`; vì vậy `repeat` gọi được. `missing` là tham chiếu `null`, nên không có đối tượng nào để nhận lời gọi phương thức. Lỗi này khác với `IllegalArgumentException` khi truyền `count` âm cho một chuỗi hợp lệ.

## 4. Số âm và ngoại lệ `IllegalArgumentException`

`repeat(int count)` yêu cầu `count >= 0`. Khi `count < 0`, Java ném một ngoại lệ kiểu `IllegalArgumentException`.

```java
String line = "-";
line.repeat(-1); // IllegalArgumentException
```

Để bắt ngoại lệ trong tình huống thử nghiệm:

```java
try {
    String result = "ab".repeat(-3);
    System.out.println(result);
} catch (IllegalArgumentException ex) {
    System.out.println("count phải không âm");
}
```

**Stdout:**

```text
count phải không âm
```

Không nên dùng `catch (Exception ignored) {}` để âm thầm bỏ lỗi nếu giá trị `count` đến từ công thức hay đầu vào người dùng. Thường tốt hơn là làm rõ quy tắc nghiệp vụ trước khi gọi:

```java
static String repeatSafely(String text, int count) {
    if (count < 0) {
        throw new IllegalArgumentException("count phải >= 0");
    }
    return text.repeat(count);
}
```

Đây là một phương thức minh họa đặt bên trong một lớp. Phép kiểm tra giúp thông báo lỗi dễ hiểu hơn trong mã ứng dụng, nhưng bản thân `repeat` đã kiểm tra số lần lặp âm.

### Khi `count` được tính bằng biểu thức

Một tình huống thực tế là tính số dấu cách cần thêm để đạt độ rộng mong muốn:

```java
String label = "Java";
int width = 3;
int padding = width - label.length(); // -1
// String result = label + " ".repeat(padding); // Lỗi vì count = -1
```

Nếu ý định là **không thêm đệm khi nội dung đã dài**, có thể chặn về `0`:

```java
int padding = Math.max(0, width - label.length());
String result = label + " ".repeat(padding);
```

Ở đây `Math.max(0, ...)` phản ánh **quy tắc nghiệp vụ của việc đệm chuỗi**. Không nên tự động biến mọi `count` âm thành `0` trong mọi tình huống: đôi khi số âm là lỗi dữ liệu cần được thông báo rõ ràng.

## 5. Ví dụ Java 17+ hoàn chỉnh

Lưu nội dung sau vào tệp **`StringRepeatDemo.java`**:

```java
public class StringRepeatDemo {

    static void show(String label, String value) {
        System.out.println(label + "=[" + value + "], length=" + value.length());
    }

    public static void main(String[] args) {
        String unit = "ab";

        show("zero", unit.repeat(0));
        show("once", unit.repeat(1));
        show("twice", unit.repeat(2));
        show("three", unit.repeat(3));

        show("emptyFive", "".repeat(5));
        show("emptyZero", "".repeat(0));
        show("emojiTwice", "😀".repeat(2));

        System.out.println("original=" + unit);
        unit.repeat(3); // Giá trị trả về bị bỏ qua
        System.out.println("discarded=" + unit);

        String saved = unit.repeat(3);
        System.out.println("saved=" + saved);

        try {
            "".repeat(-1);
        } catch (IllegalArgumentException ex) {
            System.out.println("negative=" + ex.getClass().getSimpleName());
        }

        show("dashes", "-".repeat(6));
    }
}
```

Biên dịch và chạy bằng **JDK 17 trở lên**:

```bash
javac -encoding UTF-8 StringRepeatDemo.java
java StringRepeatDemo
```

**Stdout:**

```text
zero=[], length=0
once=[ab], length=2
twice=[abab], length=4
three=[ababab], length=6
emptyFive=[], length=0
emptyZero=[], length=0
emojiTwice=[😀😀], length=4
original=ab
discarded=ab
saved=ababab
negative=IllegalArgumentException
dashes=[------], length=6
```

### Phân tích kết quả

1. `repeat(0)` trả `""` nên phần nội dung trong dấu `[]` trống và `length()` là `0`.
2. `repeat(1)`, `repeat(2)` và `repeat(3)` tạo nội dung tương ứng `"ab"`, `"abab"` và `"ababab"`.
3. `""` lặp `5` lần hoặc `0` lần vẫn là chuỗi rỗng.
4. `"😀"` là **một Unicode code point** nhưng được biểu diễn bằng **hai UTF-16 code unit**; hai emoji có `String.length() == 4`.
5. `unit.repeat(3);` tính kết quả rồi bỏ qua, nên biến `unit` vẫn giữ `"ab"`. Khi lưu kết quả vào `saved`, giá trị `"ababab"` được giữ lại.
6. `"" .repeat(-1)` (trong code viết không có dấu cách giữa `""` và `.repeat`) vẫn ném `IllegalArgumentException`, cho thấy đối số âm bị từ chối ngay cả khi chuỗi rỗng.
7. `"-".repeat(6)` tạo đúng sáu dấu gạch nối, không có dấu phân cách bổ sung.

Ví dụ dùng `show` để kẹp nội dung trong `[]`: nhờ vậy chuỗi rỗng dễ nhận biết hơn khi in ra màn hình.

## 6. `repeat()` và tính bất biến của `String`

Giống `trim()`, `replace()`, `substring()` hay `toUpperCase()`, `repeat()` là phương thức tạo **giá trị trả về** và không sửa nội dung đối tượng `String` nhận lời gọi.

**Sai nếu mục đích là cập nhật biến:**

```java
String marker = "*";
marker.repeat(4);
System.out.println(marker); // *
```

Lời gọi `marker.repeat(4)` đã trả về `"****"`, nhưng kết quả bị bỏ qua. Để giữ kết quả, có thể:

**Gán lại biến:**

```java
marker = marker.repeat(4);
System.out.println(marker); // ****
```

**Hoặc lưu vào biến khác:**

```java
String original = "*";
String repeated = original.repeat(4);

System.out.println(original); // *
System.out.println(repeated); // ****
```

**Hoặc dùng ngay làm đối số:**

```java
System.out.println("*".repeat(4)); // ****
```

Không có lý do gì để giả định `repeat()` làm tăng độ dài của chính đối tượng `String` cũ. Java chỉ cho phép thay đổi **giá trị của biến tham chiếu** bằng phép gán lại; đối tượng `String` vốn bất biến.

### Không dựa vào `==` để nhận diện kết quả

Khi `count == 1`, nội dung kết quả giống nội dung gốc. Triển khai JDK có thể trả về cùng một đối tượng `String`, nhưng đây không phải điều kiện cần để sử dụng phương thức.

```java
String source = new String("xy");
String once = source.repeat(1);

System.out.println(source.equals(once)); // true
```

Dùng `.equals(...)` để kiểm tra nội dung; dùng `==` trên hai `String` là so **tham chiếu đối tượng**, không phải so văn bản.

## 7. Độ dài kết quả và lưu ý về bộ nhớ

Nếu chuỗi ban đầu có `m = source.length()` (đo bằng **UTF-16 code unit**) và `count = n` là số nguyên không âm, độ dài của chuỗi kết quả về mặt toán học là:

\[
L = m \times n
\]

Ví dụ:

```java
String a = "abc";
String b = a.repeat(4);

System.out.println(b);          // abcabcabcabc
System.out.println(b.length()); // 12 = 3 * 4
```

Với chuỗi rỗng, `m == 0`, kết quả luôn có độ dài `0` cho mọi `n >= 0`. Với `n == 0`, kết quả cũng có độ dài `0` bất kể chuỗi gốc.

### Không nên tạo chuỗi khổng lồ

`repeat` có thể yêu cầu cấp phát bộ nhớ tương ứng với kích thước kết quả. Một `count` dương rất lớn kết hợp với chuỗi dài có thể gây vấn đề về bộ nhớ hoặc làm chương trình chậm. Nếu yêu cầu kích thước vượt khả năng biểu diễn hoặc cấp phát của JVM, thao tác có thể thất bại, chẳng hạn bằng **`OutOfMemoryError`**.

Không chạy thử ví dụ nguy hiểm như sau trong một ứng dụng bình thường:

```java
// Minh họa không nên thực thi: có nguy cơ tiêu thụ bộ nhớ rất lớn.
String huge = "abcdefghij".repeat(Integer.MAX_VALUE);
```

Đây là vấn đề **kích thước/bộ nhớ**, khác với lỗi `IllegalArgumentException` do `count` âm. Đừng dựa vào một thông báo lỗi cụ thể hoặc vào việc JVM luôn cấp phát được chuỗi chỉ vì `count >= 0`.

Khi dữ liệu từ người dùng quyết định số lần lặp, hãy đặt giới hạn phù hợp. Ví dụ, phương thức dưới đây giới hạn tổng độ dài kết quả bằng một mức do ứng dụng đặt ra:

```java
static String boundedRepeat(String text, int count, int maxUnits) {
    if (text == null) {
        throw new IllegalArgumentException("text không được null");
    }
    if (count < 0 || maxUnits < 0) {
        throw new IllegalArgumentException("count và maxUnits phải không âm");
    }

    long expectedUnits = (long) text.length() * count;
    if (expectedUnits > maxUnits) {
        throw new IllegalArgumentException("Kết quả vượt giới hạn");
    }

    return text.repeat(count);
}
```

Phép ép `(long)` trước phép nhân giúp tránh **tràn số nguyên `int`** khi kiểm tra kích thước. Kiểm tra này giới hạn số UTF-16 code unit theo chính sách ứng dụng, chứ không bảo đảm mọi hoạt động cấp phát bộ nhớ luôn thành công.

## 8. Unicode và ký tự nhiều đơn vị UTF-16

`repeat` không thao tác riêng từng ký tự để chọn hoặc cắt. Nó lặp **toàn bộ chuỗi gốc**, nên những cặp surrogate hợp lệ ở bên trong một bản sao vẫn được sao chép cùng nhau.

Ví dụ:

```java
String emoji = "😀";
String repeated = emoji.repeat(3);

System.out.println(repeated); // 😀😀😀
System.out.println(repeated.length()); // 6
System.out.println(
        repeated.codePointCount(0, repeated.length())
); // 3
```

`String.length()` tính UTF-16 code unit; mỗi emoji ở đây gồm hai code unit, nên ba emoji có độ dài `6` nhưng chỉ có `3` Unicode code point.

Tương tự, với ký tự tiếng Việt được biểu diễn bằng nhiều code point (ví dụ chữ cái cộng dấu kết hợp), `repeat` vẫn sao chép **đúng chuỗi mã đầu vào**. Phương thức **không chuẩn hóa Unicode**, không đổi chữ hoa/thường và không tự gộp các ký tự kết hợp.

Nếu chuỗi nguồn vốn chứa một surrogate không ghép cặp hợp lệ, `repeat` cũng không tự sửa hoặc kiểm tra tính hợp lệ của dữ liệu Unicode ấy. Nó chỉ lặp chuỗi theo nội dung đã có.

## 9. Khi nào nên và không nên sử dụng `repeat()`?

### Tạo đường kẻ hoặc dấu nhấn văn bản

```java
String line = "-".repeat(12);
System.out.println(line); // ------------
```

Đơn giản và dễ hiểu hơn việc tự viết vòng lặp để nối một ký tự cố định.

### Tạo phần đệm có số lượng xác định

```java
String label = "OK";
int width = 6;

String padded = label + " ".repeat(Math.max(0, width - label.length()));
System.out.println("[" + padded + "]"); // [OK    ]
```

Ví dụ tính độ rộng theo `String.length()` (UTF-16 code unit). Nếu cần canh cột chính xác theo **độ rộng hiển thị của terminal**, `length()` không đủ cho mọi emoji, ký tự kết hợp hoặc chữ có độ rộng khác nhau.

### Tạo nhiều dòng trống hoặc ký tự xuống dòng

```java
String twoNewlines = "\n".repeat(2);
```

Kết quả chứa hai ký tự xuống dòng. Khi in chuỗi này trực tiếp, các dòng trống có thể khó nhận diện bằng mắt; đây là lý do các ví dụ QA thường in thêm nhãn hoặc độ dài.

### Phân biệt với việc nối có dấu phân cách

```java
String result = "Java".repeat(3);
// "JavaJavaJava"
```

Nếu muốn `"Java,Java,Java"`, cần tự xây dựng logic về **dấu phân cách**. Một cách đơn giản là dùng `String.join` với danh sách các phần tử:

```java
String joined = String.join(",", java.util.Collections.nCopies(3, "Java"));
System.out.println(joined); // Java,Java,Java
```

`repeat` tự nó **không** có đối số cho dấu phân cách; `String.join` thực hiện một bài toán khác là nối các phần tử bằng ký tự phân tách.

### Trường hợp vòng lặp hoặc `StringBuilder` phù hợp hơn

`repeat` phù hợp khi muốn lặp **cùng một mẫu** đúng số lần. Nếu nội dung ở mỗi bước khác nhau, chẳng hạn `"1, 2, 3"` hay mỗi dòng chứa một số thứ tự mới, hãy dùng vòng lặp và `StringBuilder` hoặc những API tập hợp thích hợp.

```java
StringBuilder builder = new StringBuilder();
for (int i = 1; i <= 3; i++) {
    if (i > 1) {
        builder.append(", ");
    }
    builder.append(i);
}
System.out.println(builder); // 1, 2, 3
```

Phương thức `String.repeat` không nhận một callback để sinh nội dung mới ở từng lượt. Nó luôn lặp chính chuỗi nhận lời gọi.

## 10. Những lỗi phổ biến

| Cách hiểu hoặc mã | Vấn đề | Cách sửa / nguyên tắc |
|---|---|---|
| `"ab".repeat(0)` sẽ trả `"ab"` | Sai: số lần lặp `0` tạo chuỗi rỗng | Dự đoán kết quả `""` |
| `"".repeat(5)` sẽ báo lỗi | Sai: chuỗi rỗng là đầu vào hợp lệ | Kết quả vẫn `""` |
| `"".repeat(-1)` sẽ trả `""` | Sai: `count < 0` bị từ chối | Xử lý hoặc báo lỗi `IllegalArgumentException` |
| `text.repeat(3);` tự sửa `text` | `String` bất biến | Gán lại hoặc sử dụng giá trị trả về |
| `"ha".repeat(3)` tự thêm dấu cách | Không có dấu phân cách mặc định | Đặt dấu cách vào mẫu nếu cần |
| `String value = null; value.repeat(2);` | Gọi phương thức trên `null` | Kiểm tra đầu vào, tránh `NullPointerException` |
| `repeat` chấp nhận số thực `2.5` | Tham số phải là `int` | Chọn số nguyên theo quy tắc nghiệp vụ |
| `repeat` luôn an toàn với `Integer.MAX_VALUE` | Chuỗi đầu ra có thể quá lớn | Giới hạn `count` và kích thước dự kiến |
| `"😀".repeat(2).length()` bằng `2` | `length()` đếm UTF-16 code unit | Kết quả ví dụ là `4`, tương ứng 2 code point |

### Chú ý khi kiểm tra ngoại lệ

Đừng kiểm tra thông báo (`ex.getMessage()`) như một chuỗi cố định. Thông điệp có thể khác tùy JDK hoặc đường đi thực thi. Khi dạy khái niệm, điều cần xác nhận ở trường hợp số âm là **kiểu ngoại lệ `IllegalArgumentException`** và nguyên nhân `count < 0`.

## 11. Kiểm thử các trường hợp biên

Bạn có thể tạo một phương thức tự kiểm tra, đặt bên trong lớp Java:

```java
static void testRepeat() {
    if (!"".equals("abc".repeat(0))) {
        throw new AssertionError("repeat(0) phải trả chuỗi rỗng");
    }
    if (!"abc".equals("abc".repeat(1))) {
        throw new AssertionError("repeat(1) phải giữ nội dung");
    }
    if (!"abcabc".equals("abc".repeat(2))) {
        throw new AssertionError("repeat(2) phải ghép hai bản sao");
    }
    if (!"".equals("".repeat(100))) {
        throw new AssertionError("Chuỗi rỗng lặp vẫn rỗng");
    }

    boolean negativeRejected = false;
    try {
        "".repeat(-1);
    } catch (IllegalArgumentException ex) {
        negativeRejected = true;
    }
    if (!negativeRejected) {
        throw new AssertionError("count âm phải bị từ chối");
    }
}
```

Có thể gọi `testRepeat();` từ `main`. Nếu đúng hết, phương thức kết thúc bình thường mà không in gì. Các khẳng định này dùng `AssertionError` được ném trực tiếp nên không phụ thuộc cờ chạy `-ea`.

Bộ kiểm thử tập trung vào **đặc tả của phương thức**: `0`, `1`, số dương, chuỗi rỗng và số âm. Đây là những ca cần có khi bạn viết hàm tiện ích dựa trên `repeat()`.

## 12. Câu hỏi tự kiểm tra

**Câu 1.** `"xy".repeat(3)` tạo chuỗi gì và độ dài bằng bao nhiêu?

**Đáp án:** `"xyxyxy"`, độ dài `6` UTF-16 code unit.

**Câu 2.** `"-".repeat(0)` và `"".repeat(10)` có trả cùng nội dung không?

**Đáp án:** Có. Cả hai đều trả chuỗi rỗng `""`.

**Câu 3.** Vì sao `String x = "a"; x.repeat(4);` rồi in `x` vẫn ra `"a"`?

**Đáp án:** `String` bất biến; kết quả `"aaaa"` đã bị bỏ qua. Muốn giữ kết quả cần `x = x.repeat(4);` hoặc lưu vào biến khác.

**Câu 4.** `String.repeat(-5)` và `null.repeat(5)` có gây cùng lỗi không?

**Đáp án:** Không. Gọi trên một `String` hợp lệ với `count = -5` gây `IllegalArgumentException`; cố gọi phương thức trên tham chiếu `null` gây `NullPointerException`.

**Câu 5.** Tại sao không nên thực hiện `"abc".repeat(count)` khi `count` do người dùng nhập mà không giới hạn?

**Đáp án:** `count` quá lớn có thể yêu cầu lượng bộ nhớ rất lớn. Nên kiểm tra tính hợp lệ, giới hạn số lần lặp hoặc tổng độ dài dự kiến theo yêu cầu ứng dụng.

**Câu 6.** `"ab".repeat(3)` và `String.join("-", Collections.nCopies(3, "ab"))` khác nhau thế nào?

**Đáp án:** `repeat` tạo `"ababab"`; `String.join` với danh sách ba phần tử và dấu nối `"-"` tạo `"ab-ab-ab"`. `repeat` không tự chèn dấu phân cách.

## 13. Ghi nhớ

`String.repeat(int count)` lặp **toàn bộ nội dung chuỗi** đúng `count` lần, liền nhau và không thêm dấu phân cách. Với `count = 0`, kết quả là `""`; với `count = 1`, nội dung không đổi; với `count > 1`, các bản sao được nối liên tục. Chuỗi rỗng lặp một số lần **không âm** vẫn rỗng, nhưng **mọi `count` âm đều gây `IllegalArgumentException`**, kể cả với chuỗi rỗng.

Vì `String` bất biến, bạn phải **sử dụng giá trị trả về** của `repeat` nếu muốn giữ kết quả. Khi số lần lặp đến từ dữ liệu ngoài hoặc phép tính, hãy xác thực số âm và giới hạn tổng độ dài trước khi cấp phát chuỗi lớn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-transformation-005">transform và xây dựng chuỗi bước xử lý</a>

<details>
<summary>Xem chi tiết</summary>

# String.transform() và xây dựng chuỗi bước xử lý trong Java

Khi xử lý dữ liệu văn bản, một chương trình thường phải thực hiện nhiều thao tác liên tiếp: **cắt khoảng trắng ở hai đầu → chuẩn hóa khoảng trắng → đổi chữ hoa/thường → tách, kiểm tra hoặc chuyển kiểu**. Nếu viết tất cả trong một biểu thức lồng nhiều lời gọi, mã dễ khó đọc; nếu tạo hàng loạt biến trung gian, ý đồ luồng xử lý có thể bị phân tán.

Từ **Java 12**, `java.lang.String` cung cấp phương thức `transform(Function)`. Nó cho phép truyền **toàn bộ chuỗi hiện tại** cho một hàm biến đổi rồi nhận kết quả của hàm đó. Ghép nhiều lời gọi `transform(...)` tạo thành một *pipeline* xử lý mạch lạc, được thực thi **theo thứ tự từ trái sang phải**.

Điểm quan trọng: `transform` **không tự sửa nội dung, không tự chuẩn hóa chuỗi và không tự kích hoạt xử lý bất đồng bộ**. Nó chỉ gọi hàm do người lập trình cung cấp. Hàm có thể trả một `String` khác, cũng có thể trả **kiểu dữ liệu hoàn toàn khác** như `Integer`, `String[]` hoặc một đối tượng miền nghiệp vụ.

## 1. API của String.transform()

Chữ ký khái niệm từ `java.lang.String`:

```java
public <R> R transform(
        java.util.function.Function<? super String, ? extends R> f)
```

Trong đó:

- **`f`** là hàm nhận một `String` (hoặc kiểu cha phù hợp như `Object`) và trả một kết quả kiểu `R`.
- **`R`** là kiểu kết quả do trình biên dịch suy luận hoặc do ngữ cảnh gán xác định; nó **không bị giới hạn** ở `String`.
- **`? super String`** cho phép một hàm có kiểu đầu vào phù hợp với `String`; **`? extends R`** cho phép kết quả là kiểu `R` hoặc subtype của `R`.
- Phương thức là **phương thức instance**: gọi trên một đối tượng `String`, không phải phương thức static.
- Có từ **Java 12**; chạy bằng **Java 17+** không cần thêm thư viện.

Về ngữ nghĩa, một lời gọi thông thường tương đương với việc áp dụng hàm trực tiếp:

```java
String input = "  Hello  ";
String result1 = input.transform(String::strip);
String result2 = String::strip.apply(input); // Không hợp lệ: method reference không có apply trực tiếp
```

Dòng thứ hai chỉ nhằm minh họa **một lỗi cú pháp thường gặp**; nó không biên dịch được. Muốn viết tương đương chính xác, hãy tạo đối tượng `Function`:

```java
java.util.function.Function<String, String> cleanup = String::strip;
String result1 = input.transform(cleanup);
String result2 = cleanup.apply(input);
System.out.println(result1.equals(result2)); // true
```

Ba dòng trong khối hợp lệ khi `input` được khai báo như bên trên. Mỗi lần gọi `transform` sẽ thực thi tương tự `f.apply(input)` ngay lúc được gọi; không có cơ chế trì hoãn như một số thao tác trung gian của `Stream`.

> **Giữ sự đơn giản:** Nếu chỉ muốn gọi `strip()` thì viết `input.strip()` thường dễ hiểu hơn `input.transform(String::strip)`. `transform` đặc biệt hữu ích khi cần đưa chuỗi qua một hàm tùy biến, chuyển sang kiểu khác hoặc ghép nhiều bước mà cách gọi trực tiếp không có dạng chuỗi phương thức thuận tiện.

## 2. Pipeline transform hoạt động thế nào?

Một pipeline thường đọc theo luồng:

```text
String đầu vào
   |
   v
Bước 1: strip()             -> String
   |
   v
Bước 2: normalizeSpaces()   -> String
   |
   v
Bước 3: toUpperCase(ROOT)   -> String
   |
   v
Kết quả: "JAVA CORE"
```

Java cho phép nối lời gọi vì **kết quả của một bước còn là `String`**, nên có thể gọi tiếp `.transform(...)`:

```java
String result = raw
        .transform(String::strip)
        .transform(StringTransformDemo::normalizeSpaces)
        .transform(s -> s.toUpperCase(Locale.ROOT));
```

Đây là đoạn **minh họa bên trong lớp `StringTransformDemo`** ở mục 5, nơi `raw` và `normalizeSpaces` được định nghĩa. Nó thể hiện:

1. `raw` là chuỗi ban đầu.
2. `String::strip` bỏ khoảng trắng theo `Character.isWhitespace` ở hai mép.
3. `normalizeSpaces` (do ta định nghĩa) gộp các nhóm dấu cách ASCII và tab liên tiếp thành một dấu cách.
4. Lambda `s -> s.toUpperCase(Locale.ROOT)` chuyển chữ hoa theo locale xác định.

Các bước chạy **eager**, từng bước hoàn tất rồi mới tới bước tiếp. Nếu bước 2 ném exception, bước 3 không chạy. Nếu một bước trả `null`, lời gọi `.transform(...)` tiếp theo trên giá trị đó gây `NullPointerException`. Chương trình không tự bỏ qua lỗi hay hoàn tác các side effect trong hàm.

### Vì sao thứ tự bước quan trọng?

`strip` trước rồi gộp khoảng trắng giúp đầu ra sạch hai mép. Với ví dụ chỉ sử dụng dấu cách và tab, một số thứ tự có thể cho cùng giá trị; **không nên vì thế kết luận mọi phép biến đổi đều giao hoán**.

Chẳng hạn, “kiểm tra độ dài rồi cắt” không giống “cắt rồi kiểm tra độ dài”; “parse số rồi định dạng” cũng khác “định dạng rồi parse”. Nếu bước chuyển kiểu kết quả không còn là `String`, bạn chỉ có thể tiếp tục gọi các phương thức được hỗ trợ bởi **kiểu kết quả mới**.

## 3. transform không giới hạn kết quả ở String

### Chuỗi sang số nguyên

```java
int count = " 42 "
        .transform(String::strip)
        .transform(Integer::parseInt);

System.out.println(count); // 42
```

`String::strip` trả về `String`, nên bước tiếp theo được phép gọi `String.transform`. `Integer::parseInt` trả về `int`, được Java tự động boxing thành `Integer` khi thích hợp để đáp ứng `Function`; biến `count` nhận giá trị `int` nhờ unboxing. Nếu chuỗi sau khi cắt không phải số nguyên thập phân hợp lệ, `Integer.parseInt` ném `NumberFormatException`: `transform` không tự bắt lỗi này.

### Chuỗi sang mảng

```java
String[] items = " red,green,blue "
        .transform(String::strip)
        .transform(s -> s.split(","));
```

Sau bước thứ hai, kiểu trả về là `String[]`. Bạn có thể gọi phương thức của mảng hoặc các API làm việc với mảng, nhưng **không thể** gọi `.transform(...)` tiếp trên mảng, vì mảng không có phương thức instance `String.transform`.

### Chuỗi sang số lượng ký tự

```java
Integer length = "  hello  "
        .transform(String::strip)
        .transform(String::length);

System.out.println(length); // 5
```

`String::length` trả số **đơn vị mã UTF-16** sau khi strip. Nếu yêu cầu số **code point**, hãy dùng:

```java
int codePoints = "A\u0301"
        .transform(s -> s.codePointCount(0, s.length()));
```

Đoạn này trả 2 code point (chữ A và dấu kết hợp), không nhất thiết là 2 ký tự người dùng cảm nhận. Phương thức `transform` không làm thay đổi các quy tắc Unicode của `length` hoặc `codePointCount`.

## 4. Method reference, lambda và các hàm tùy biến

Tham số của `transform` là một đối tượng thực thi giao diện hàm `Function`, vì vậy có thể cung cấp nhiều cách viết:

| Cách truyền hàm | Ví dụ | Khi hữu ích |
|---|---|---|
| Method reference của instance method | `.transform(String::strip)` | Phương thức có sẵn nhận chính chuỗi hiện tại |
| Lambda một biểu thức | `.transform(s -> s.replace("-", "_"))` | Có tham số bổ sung hoặc logic ngắn |
| Method reference của static method | `.transform(Integer::parseInt)` | Chuyển String sang kiểu khác |
| Method reference đến hàm tự định nghĩa | `.transform(MyCleaner::clean)` | Tái sử dụng một bước nghiệp vụ |
| Biến `Function` | `.transform(cleanupPipeline)` | Cấu hình, kiểm thử, kết hợp và tái sử dụng nhiều bước |

Không phải cứ có dấu `::` là có thể thay lambda trong mọi hoàn cảnh. Method reference phải tương thích với kiểu hàm mong đợi; ví dụ `String::strip` tương thích với `Function<String, String>`, còn các phương thức cần nhiều tham số có thể không phù hợp nếu chưa được đóng gói bằng lambda.

Ví dụ một phương thức tùy biến:

```java
static String normalizeSpaces(String input) {
    return input.replaceAll("[\\t ]+", " ");
}
```

Đây là **phương thức đặt bên trong một lớp**. Regex `[\\t ]+` tìm một hoặc nhiều dấu tab hoặc dấu cách ASCII và thay bằng đúng một dấu cách. Nó **không được định nghĩa để xử lý mọi loại Unicode whitespace**. Đây là lựa chọn cố ý để ví dụ có quy tắc đơn giản và kiểm thử rõ ràng.

Lưu ý **hai lớp escape**: mã Java `"[\\t ]+"` tạo regex `[\t ]+`; ký tự `\` trong Java literal phải được escape thành `\\` để gửi dấu backslash tới regex engine. Với dữ liệu Unicode phức tạp, chọn regex/`Pattern` và chính sách chuẩn hóa phù hợp với yêu cầu thực tế.

## 5. Chương trình Java 17+ hoàn chỉnh

Lưu vào tệp **`StringTransformDemo.java`**:

```java
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Function;

public class StringTransformDemo {
    // Chuẩn hóa các nhóm dấu cách ASCII và TAB liên tiếp.
    static String normalizeSpaces(String input) {
        return input.replaceAll("[\\t ]+", " ");
    }

    // Chỉ dùng để in rõ TAB và dấu cách; không đổi dữ liệu nguồn.
    static String visible(String input) {
        return input.replace("\t", "<TAB>").replace(" ", "_");
    }

    public static void main(String[] args) {
        String raw = " \tjava   core\t ";

        String normalized = raw
                .transform(String::strip)
                .transform(StringTransformDemo::normalizeSpaces)
                .transform(s -> s.toUpperCase(Locale.ROOT));

        System.out.println("raw: [" + visible(raw) + "]");
        System.out.println("normalized: [" + normalized + "]");
        System.out.println("raw unchanged: "
                + raw.equals(" \tjava   core\t "));

        String[] tokens = "  red,green,blue "
                .transform(String::strip)
                .transform(s -> s.split(","));
        System.out.println("tokens: " + Arrays.toString(tokens));

        Integer length = "  hello  "
                .transform(String::strip)
                .transform(String::length);
        System.out.println("length: " + length);

        int parsed = " 42 "
                .transform(String::strip)
                .transform(Integer::parseInt);
        System.out.println("number: " + parsed);

        String maybeNull = "input".transform(s -> null);
        System.out.println("may return null: " + (maybeNull == null));

        Function<String, String> cleanup = String::strip;
        cleanup = cleanup
                .andThen(StringTransformDemo::normalizeSpaces)
                .andThen(s -> s.toLowerCase(Locale.ROOT));

        System.out.println("function pipeline: "
                + "   A   B  ".transform(cleanup));
    }
}
```

Biên dịch và chạy bằng JDK 17 hoặc mới hơn:

```bash
javac -encoding UTF-8 StringTransformDemo.java
java StringTransformDemo
```

Đầu ra:

```text
raw: [_<TAB>java___core<TAB>_]
normalized: [JAVA CORE]
raw unchanged: true
tokens: [red, green, blue]
length: 5
number: 42
may return null: true
function pipeline: a b
```

Đầu ra được viết bằng ASCII nên không phụ thuộc cách terminal hiển thị dấu tab. Trong hàm `visible`, `<TAB>` biểu thị **một tab thật** và ký tự `_` biểu thị **một dấu cách** trong chuỗi đầu vào. Hàm chỉ giúp nhìn thấy nội dung; `raw` vẫn giữ nguyên.

**Giải thích các dòng đáng chú ý:**

- Chuỗi gốc chứa một dấu cách, một tab, `java`, ba dấu cách, `core`, một tab và dấu cách cuối. `strip` bỏ whitespace ở mép; bước tiếp theo gộp các dấu cách giữa `java` và `core`; cuối cùng đổi thành `JAVA CORE`.
- Hai biến `raw` và `normalized` độc lập về giá trị xử lý. `String` bất biến; một lời gọi `transform` không sửa `raw`.
- `split(",")` tạo mảng ba chuỗi, nên `transform` có thể kết thúc với kiểu `String[]`.
- `String::length` tạo kết quả kiểu số (qua `Integer` trong giao diện `Function`).
- `Integer::parseInt` thể hiện một phép chuyển kiểu có thể thất bại đối với input không hợp lệ.
- Hàm `s -> null` minh họa `transform` **không áp đặt rằng kết quả phải khác null**.
- `Function.andThen` tạo một **hàm tổng hợp** rồi được áp dụng bằng một lần `"   A   B  ".transform(cleanup)`.

## 6. Kết hợp nhiều bước bằng Function.andThen() và compose()

Nếu cần tái sử dụng pipeline ở nhiều nơi, có thể lưu nó dưới dạng một `Function<String, String>`:

```java
Function<String, String> trimSides = String::strip;
Function<String, String> collapse = StringTransformDemo::normalizeSpaces;
Function<String, String> uppercase =
        s -> s.toUpperCase(Locale.ROOT);

Function<String, String> pipeline =
        trimSides.andThen(collapse).andThen(uppercase);

String value = " \tjava   core  ".transform(pipeline);
```

Các dòng trên là **đoạn minh họa bổ sung** (đặt trong lớp có thể truy cập `StringTransformDemo.normalizeSpaces` và import `java.util.function.Function`, `java.util.Locale`). Khi dùng `andThen`, hàm trước chạy rồi đến hàm sau, tương đương:

```text
pipeline.apply(text)
= uppercase.apply(collapse.apply(trimSides.apply(text)))
```

`Function.compose(before)` có thứ tự đọc **ngược** với `andThen(after)`:

```java
Function<String, String> pipeline2 =
        uppercase.compose(collapse).compose(trimSides);
```

Biểu thức `pipeline2.apply(text)` vẫn chạy **trimSides → collapse → uppercase**. Khi người đọc cần thấy rõ luồng xử lý, `andThen` thường thuận tiện hơn vì các bước được liệt kê từ trái sang phải theo thứ tự thực thi.

Phân biệt:

- `source.transform(pipeline)` **áp dụng** pipeline cho **một chuỗi cụ thể** và trả kết quả.
- `f.andThen(g)` **tạo một đối tượng Function kết hợp** có thể tái sử dụng.
- `source.transform(f).transform(g)` **áp dụng ngay** từng function lên kết quả của bước trước.

Chúng gần nhau về ý nghĩa với các hàm thuần, nhưng có khác biệt thiết kế quan trọng: nếu function có **side effect**, ném exception, dùng trạng thái mutable hoặc trả `null`, hành vi của pipeline phụ thuộc chính những function đó. Không nên coi `transform` như cơ chế đảm bảo transaction hoặc xử lý lỗi tự động.

## 7. Pipeline đổi kiểu: khi nào nên dừng chuỗi transform?

Một ví dụ hữu ích là tiền xử lý chuỗi xong rồi **chuyển kết quả sang một kiểu miền nghiệp vụ**:

```java
record ProductCode(String value) {}

ProductCode code = "  prd-042  "
        .transform(String::strip)
        .transform(s -> s.toUpperCase(Locale.ROOT))
        .transform(ProductCode::new);
```

Đây là đoạn mã **cần đặt trong môi trường có khai báo `record ProductCode`** (Java 17 hỗ trợ record). Kết quả là `ProductCode` có `value()` bằng `"PRD-042"`; sau bước cuối, kiểu static là `ProductCode` nên không thể gọi tiếp `String.transform` trên nó.

Một cách khác là dùng `Optional` để thể hiện rằng kết quả có thể vắng mặt:

```java
import java.util.Optional;

Optional<Integer> maybeNumber = " 12 "
        .transform(String::strip)
        .transform(s -> {
            try {
                return Optional.of(Integer.parseInt(s));
            } catch (NumberFormatException ex) {
                return Optional.<Integer>empty();
            }
        });
```

Đây là **đoạn mã minh họa**: `Optional` được trả về **do lambda tự tạo**, không phải vì `transform` hiểu hoặc tự bắt lỗi phân tích số. Với đầu vào `" 12 "`, kết quả là `Optional.of(12)`. Nếu muốn trả thông tin lỗi chi tiết thay vì chỉ trả rỗng, hãy thiết kế kiểu kết quả hoặc exception phù hợp.

**Giữ bước xử lý có trách nhiệm rõ ràng:** Một pipeline dễ đọc thường gồm những hàm tên đủ rõ, nhận một giá trị và tạo một kết quả xác định. Không nên gộp xác thực, lưu database, gửi HTTP, format giao diện và logging vào một chuỗi dài chỉ vì tất cả có thể được đóng gói bằng `Function`.

## 8. Null, exception và dữ liệu không hợp lệ

`String.transform` có các điểm cần chú ý:

| Tình huống | Điều xảy ra | Hướng xử lý |
|---|---|---|
| Tham chiếu receiver `source` là `null` | Gọi `source.transform(...)` gây `NullPointerException` | Xác thực `source` trước khi gọi |
| Tham số `f` là `null` | Không thể áp dụng hàm; ném `NullPointerException` | Truyền function hợp lệ |
| Hàm trả `null` | `transform` có thể trả `null` | Quy định null-contract trước khi nối thêm bước |
| Hàm ném `NumberFormatException`, `IllegalArgumentException`, v.v. | Exception lan ra cho caller theo quy tắc Java | Bắt ở ranh giới nghiệp vụ hoặc để caller xử lý |
| Một bước biến đổi làm mất thông tin | Không có hoàn tác tự động | Kiểm chứng yêu cầu về bảo toàn dữ liệu |
| Một hàm có side effect | Side effect xảy ra khi hàm được gọi | Ưu tiên hàm thuần, kiểm thử rõ ràng |
| Kết quả đổi kiểu khỏi `String` | Không còn gọi được `String.transform` trên kết quả | Tiếp tục bằng API của kiểu mới hoặc dùng function cho toàn pipeline |

Ví dụ null receiver:

```java
String raw = null;
// raw.transform(String::strip); // Nếu chạy, gây NullPointerException.
String safe = raw == null ? "" : raw.strip();
```

Dòng bị chú thích chỉ để minh họa lỗi; ba dòng tạo `raw` và `safe` có thể biên dịch. Việc thay `null` thành chuỗi rỗng là **quyết định nghiệp vụ**, không nên luôn áp dụng mặc định: đôi khi cần báo lỗi đầu vào còn thiếu.

Cũng cần lưu ý một function có thể trả `null` dù đầu vào hoàn toàn hợp lệ:

```java
String result = "hello".transform(s -> null);
System.out.println(result == null); // true
```

Sau đó gọi `result.transform(...)` sẽ ném `NullPointerException`. Nếu yêu cầu không được phép null, có thể chốt điều kiện hậu kiểm (ví dụ `Objects.requireNonNull`) hoặc chọn kiểu kết quả thể hiện rõ khả năng thiếu giá trị.

## 9. transform khác gì Stream.map(), replace() và các API String khác?

| API | Điểm khác biệt |
|---|---|
| `String.transform(f)` | Áp dụng **một Function cho toàn bộ String** và trả một giá trị kiểu `R` bất kỳ |
| `String.replace(...)` | Thay thế ký tự hoặc chuỗi con theo quy tắc của API; kết quả luôn là `String` |
| `String.replaceAll(regex, replacement)` | Thay theo regular expression; chú ý regex/escape và hiệu năng |
| `String.strip()` | Loại bỏ whitespace theo quy tắc Java ở hai mép; là một phép biến đổi cụ thể |
| `Stream.map(f)` | Áp dụng function **cho từng phần tử của Stream**, mang tính lazy ở giai đoạn trung gian |
| `Function.andThen(...)` | Kết hợp các function để **sử dụng về sau**; bản thân việc kết hợp chưa áp dụng chúng vào một String |
| `String.formatted(...)` | Định dạng chuỗi mẫu theo đối số được truyền; mục đích khác với function pipeline |

Ví dụ `"java".transform(String::toUpperCase)` là một bước biến đổi cho **toàn bộ chuỗi**. Trong khi đó, `Stream.of("java", "core").map(String::toUpperCase)` là phép biến đổi **từng chuỗi trong Stream** khi pipeline của Stream được thực thi bởi terminal operation.

Một ứng dụng cần đọc **nhiều dòng** có thể phối hợp cả hai cơ chế:

```java
List<String> lines = " a \n\n b "
        .transform(String::lines)
        .map(String::strip)
        .filter(s -> !s.isEmpty())
        .toList();
```

Đây là đoạn minh họa, cần import `java.util.List`. `String::lines` trả `Stream<String>`, nên **sau lời gọi `transform`, chúng ta đang gọi `Stream.map/filter/toList`**, không còn gọi `String.transform`. Với Java 17+, kết quả của ví dụ là danh sách `["a", "b"]`. Lệnh `toList()` là terminal operation của Stream.

## 10. Thiết kế một pipeline dễ bảo trì

Khi xây dựng chuỗi xử lý văn bản, nên phân định các bước rõ ràng:

1. **Chuẩn hóa cú pháp đầu vào:** cắt whitespace không mong muốn, gộp dấu phân tách, chuyển encoding khi thật sự cần.
2. **Chuẩn hóa nội dung theo quy tắc nghiệp vụ:** chuyển chữ hoa/thường với `Locale` rõ ràng, chuẩn hóa định danh, xử lý ký tự đặc biệt.
3. **Kiểm tra:** xác thực rỗng, độ dài, pattern, phạm vi ký tự được phép; báo lỗi với ngữ cảnh cụ thể.
4. **Chuyển kiểu cuối cùng:** từ `String` thành `Integer`, `record`, `List`, cấu trúc phân tích cú pháp hoặc một kiểu kết quả miền nghiệp vụ.

Đây là gợi ý **thiết kế**, không phải yêu cầu bắt buộc có bốn giai đoạn trong mọi chương trình. Một bước xử lý không hữu ích thì không cần thêm vào để tạo pipeline dài.

Các nguyên tắc áp dụng:

- **Hàm nhỏ, đặt tên rõ:** `normalizeSpaces`, `parseProductCode` và `validateInput` dễ hiểu hơn lambda nhiều lệnh, lồng điều kiện dày đặc.
- **Đơn vị kiểm thử ổn định:** có thể kiểm tra từng `Function` độc lập và sau đó kiểm tra pipeline tổng thể.
- **Không biến đổi âm thầm dữ liệu quan trọng:** Unicode normalization, thay đổi dấu tiếng Việt, xử lý không phân biệt hoa/thường và loại bỏ ký tự vô hình đều có thể thay đổi ý nghĩa dữ liệu. Chỉ áp dụng khi quy tắc nghiệp vụ yêu cầu.
- **Locale rõ ràng:** `toUpperCase(Locale.ROOT)`/`toLowerCase(Locale.ROOT)` cho kết quả không phụ thuộc default locale; nhưng ngôn ngữ cụ thể có thể cần locale cụ thể (như xử lý chữ cái tiếng Thổ Nhĩ Kỳ).
- **Giữ quy tắc regex được mô tả:** biểu thức `[\\t ]+` của bài chỉ gộp tab/space ASCII; để xử lý nhóm Unicode whitespace, cần xác định chính xác tập ký tự nào được phép và kiểm thử.
- **Không lạm dụng transformation có side effect:** `transform` chạy ngay và không đảm bảo rollback; các thao tác ngoại vi hoặc cập nhật shared state cần kiểm soát riêng.
- **Tối ưu sau khi có đo đạc:** nhiều bước biến đổi có thể tạo các đối tượng `String` trung gian; trong đường xử lý hiệu năng cao, xem xét việc gộp bước hoặc dùng `StringBuilder`/`Pattern` được tái sử dụng. Đừng đổi thiết kế chỉ vì giả định mọi lần gọi `transform` chắc chắn tạo một object mới.

### Có cần luôn dùng transform thay biến trung gian?

Không. Cả hai cách đều hợp lệ:

```java
String stripped = raw.strip();
String compact = normalizeSpaces(stripped);
String upper = compact.toUpperCase(Locale.ROOT);
```

Cách này thuận tiện nếu cần debug từng kết quả hoặc dùng lại kết quả trung gian. Dạng pipeline:

```java
String upper = raw
        .transform(String::strip)
        .transform(MyTextUtils::normalizeSpaces)
        .transform(s -> s.toUpperCase(Locale.ROOT));
```

là đoạn minh họa cần lớp `MyTextUtils` có static method tương ứng, và phù hợp khi muốn nhìn ngay **trình tự xử lý** trong một biểu thức. Chọn cách nào giúp nhóm đọc, kiểm thử và bảo trì rõ nhất.

## 11. Những lỗi thường gặp

| Lỗi/hiểu nhầm | Vì sao sai | Cách sửa |
|---|---|---|
| Nghĩ `transform` chỉ trả về `String` | Chữ ký dùng generic `<R>` | Có thể trả `Integer`, `String[]`, `List`, `Optional` hoặc kiểu riêng |
| Nghĩ `transform` mặc nhiên là lazy | `Function.apply` chạy ngay | Đừng suy luận theo `Stream.map` |
| Gọi `.transform(...)` tiếp khi bước trước đã trả `Integer` | `Integer` không có `String.transform` | Sử dụng API của `Integer` hoặc viết lại hàm |
| Bỏ kết quả của `source.transform(...)` mà chờ `source` đổi | `String` bất biến | Gán kết quả vào biến hoặc trả từ phương thức |
| Đầu vào `null` | Không có receiver hợp lệ | Xử lý null ở ranh giới |
| Hàm có thể trả `null` nhưng pipeline tiếp tục | Dẫn đến `NullPointerException` | Xác định hợp đồng null hoặc dùng `Optional` |
| Mong `transform` tự bắt lỗi `parseInt` | Exception do function phát ra vẫn lan ra | Thiết kế cơ chế báo lỗi phù hợp |
| Lạm dụng regex `\\s+` để “xóa mọi Unicode whitespace” | Tập ký tự mà regex nhận phụ thuộc cờ và quy tắc cụ thể | Chọn regex với định nghĩa ký tự đúng theo yêu cầu |
| Dùng `==` để kiểm tra chuỗi sau chuyển đổi | `==` so identity, không so nội dung | Dùng `equals` |
| Viết function vừa sửa dữ liệu vừa gửi HTTP/log nhiều nơi | Tạo side effect khó kiểm soát và khó test | Tách side effect khỏi bước chuẩn hóa khi có thể |

## 12. Tự kiểm tra

Cho:

```java
String original = "  blue,green  ";

String[] colors = original
        .transform(String::strip)
        .transform(s -> s.split(","));

int number = "  007  "
        .transform(String::strip)
        .transform(Integer::parseInt);

String upper = original
        .transform(String::strip)
        .transform(s -> s.toUpperCase(Locale.ROOT));
```

Hãy trả lời:

1. `colors.length` bằng bao nhiêu và các phần tử chứa gì?
2. `number` bằng bao nhiêu? Có còn giữ dạng chuỗi `"007"` sau bước `parseInt` không?
3. `upper` chứa nội dung nào và `original` có bị thay đổi không?
4. Nếu nối thêm `.transform(String::strip)` sau `Integer::parseInt` thì có biên dịch không?
5. Nếu đổi input của số thành `"  seven  "` thì `transform` có trả về 0, `null` hay ném lỗi?

**Đáp án:**

1. `colors.length == 2` với các phần tử `"blue"` và `"green"`.
2. `number == 7`; kiểu kết quả là số nguyên, không còn định dạng chuỗi có các số 0 đứng trước.
3. `upper` là `"BLUE,GREEN"`; `original` vẫn là `"  blue,green  "`.
4. Không, vì sau `parseInt` không còn receiver kiểu `String`.
5. `Integer.parseInt("seven")` ném `NumberFormatException`; `transform` để exception đi ra ngoài trừ khi lambda tự xử lý nó.

## 13. Tài liệu chính thức

- [Java SE 17 — `String.transform(Function)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html#transform(java.util.function.Function)): chữ ký, kiểu kết quả và hợp đồng áp dụng function.
- [Java SE 17 — `Function`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/function/Function.html): `apply`, `andThen`, `compose` và `identity`.
- [Java SE 17 — `String`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html): các API `strip`, `replaceAll`, `split`, `lines` và `length`.

**Ghi nhớ:** `String.transform(f)` truyền **chuỗi hiện tại** cho `f` và trả **chính kết quả của hàm**, có thể là kiểu bất kỳ. Muốn viết pipeline rõ ràng, nối các bước trả lại `String` hoặc dùng `Function.andThen`; khi đã chuyển sang kiểu khác, tiếp tục bằng API của kiểu mới. `transform` không sửa chuỗi gốc, không tự bỏ null/exception và không thay người lập trình quyết định quy tắc chuẩn hóa dữ liệu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-transformation-006">Thay đổi độ dài sau biến đổi và tác động đến chỉ số cũ</a>

<details>
<summary>Xem chi tiết</summary>

# Thay đổi độ dài sau biến đổi `String` và tác động đến chỉ số cũ

## 1. Mục tiêu

Trong Java, `String` là **bất biến**: các thao tác như `replace()`, `toUpperCase()`, `strip()` hoặc Unicode normalization trả về giá trị chuỗi kết quả, thay vì chỉnh sửa trực tiếp nội dung đối tượng chuỗi cũ. **Chuỗi kết quả có thể dài hơn hoặc ngắn hơn chuỗi ban đầu**.

Nếu bạn ghi nhớ chỉ số của một ký tự, token hoặc vùng chọn từ chuỗi ban đầu rồi tiếp tục sử dụng nó trên chuỗi sau biến đổi, chỉ số đó có thể **trỏ sai ký tự, cắt sai vùng hoặc vượt giới hạn**.

Ví dụ ngắn:

```java
import java.util.Locale;

String original = "Stra\u00DFe|END";          // Straße|END
int oldSeparator = original.indexOf('|'); // 6

String changed = original.toUpperCase(Locale.ROOT);
// "STRASSE|END": ß biến thành SS; có thêm 1 char.

System.out.println(oldSeparator);              // 6
System.out.println(changed.indexOf('|'));       // 7
System.out.println(changed.charAt(oldSeparator)); // E
```

Chỉ số `6` vẫn hợp lệ về mặt giới hạn nhưng **không còn trỏ đến dấu `|`**. Đây là lỗi nguy hiểm hơn chỉ số vượt mảng, bởi chương trình có thể chạy bình thường nhưng trả kết quả sai.

Sau bài học, bạn có thể:
- Phân biệt độ dài `String.length()`, số Unicode code point và số ký tự hiển thị.
- Nhận biết các phép biến đổi có thể thay đổi số UTF-16 code unit.
- Hiểu sự lệch chỉ số khi ký tự **trước** vị trí đã lưu được thêm, xóa hoặc mở rộng.
- Viết code tìm lại ranh giới trên chuỗi đã biến đổi và biết khi nào cần mô hình ánh xạ vị trí có chủ đích.
- Tránh nhầm việc chuyển đổi chỉ số theo code point với việc ánh xạ vị trí **qua hai phiên bản chuỗi khác nhau**.

## 2. Chỉ số trong `String` là chỉ số UTF-16

`String.length()` trả về số **UTF-16 code unit** (đơn vị `char` 16 bit). Các API như `charAt(index)`, `substring(begin, end)`, `indexOf(...)` và vị trí trả về từ `Matcher.start()/end()` cũng sử dụng chỉ số UTF-16.

Một Unicode code point có thể chiếm **một hoặc hai `char`**; một ký tự người dùng nhìn thấy (*grapheme cluster*) có thể gồm nhiều code point.

| Chuỗi | `length()` (UTF-16 units) | Số code point | Vì sao? |
| --- | ---: | ---: | --- |
| `"ABC"` | 3 | 3 | Mỗi chữ ASCII cần một `char`. |
| `"A🚀B"` | 4 | 3 | `🚀` là code point ngoài BMP, cần hai `char`. |
| `"\u00E9"` (é) | 1 | 1 | Dạng dựng sẵn U+00E9. |
| `"e\u0301"` (e + dấu sắc) | 2 | 2 | Một chữ nhìn thấy được có thể dùng hai code point. |

Vì vậy, **“độ dài thay đổi” phải được hiểu theo đơn vị đo**. Nếu một phép biến đổi đổi một code point thành hai code point (`ß` → `SS`), `length()` tăng một. Nếu một chuỗi ghép dấu được chuẩn hóa thành ký tự dựng sẵn (`e` + dấu sắc → `é`), `length()` giảm một. Nếu thay `"A"` bằng `"🚀"`, số code point vẫn là một nhưng `length()` tăng từ `1` lên `2`.

## 3. Những phép biến đổi có thể làm thay đổi độ dài

| Phép biến đổi | Ví dụ | Tác động có thể xảy ra |
| --- | --- | --- |
| `replace(CharSequence, CharSequence)` | `"abc".replace("b", "XYZ")` → `"aXYZc"` | Một đoạn nguồn có thể thành đoạn dài/ngắn hơn. |
| `replaceAll(regex, replacement)` | `"a  b".replaceAll(" +", " ")` → `"a b"` | Các lần khớp regex có thể làm độ dài thay đổi; cần chú ý cú pháp regex và replacement. |
| `toUpperCase(Locale)` | `"Straße"` → `"STRASSE"` với `Locale.ROOT` | Một ký tự có thể ánh xạ thành nhiều ký tự. |
| `toLowerCase(Locale)` | Một số biến thể Unicode hoặc quy tắc locale | Biến đổi toàn chuỗi có thể tạo số code point khác trước. |
| `strip()`, `stripLeading()`, `stripTrailing()` | `"  Java  ".strip()` → `"Java"` | Xóa các code point phù hợp ở biên. |
| `trim()` | `"  Java  ".trim()` → `"Java"` | Xóa các code unit có giá trị `<= U+0020` ở biên. |
| `Normalizer.normalize(..., NFC)` | `"e\u0301"` → `"\u00E9"` | Ghép các biểu diễn tương đương, có thể rút ngắn. |
| `Normalizer.normalize(..., NFD)` | `"\u00E9"` → `"e\u0301"` | Tách ký tự, có thể kéo dài. |
| `substring(begin, end)` | `"ABCDE".substring(1, 3)` → `"BC"` | Tạo chuỗi của một vùng được chọn; chỉ số mới được tính từ `0`. |
| `repeat(int)` | `"AB".repeat(3)` → `"ABABAB"` | Độ dài có thể tăng theo số lần lặp. |

**Chú ý:** `String` nguồn vẫn giữ nguyên nội dung, do đó chỉ số cũ vẫn có cùng ý nghĩa **trên chính nguồn cũ**. Nó trở thành vấn đề khi áp dụng cho **chuỗi kết quả mới**.

Một phép biến đổi cụ thể đôi khi không làm đổi độ dài. Ví dụ `"java".toUpperCase(Locale.ROOT)` vẫn dài 4. Không được vì một vài ví dụ đơn giản mà suy ra mọi phép chuyển hoa/thường đều bảo toàn độ dài.

## 4. Cơ chế lệch vị trí: thêm, xóa hoặc thay trước chỉ số

Giả sử trong chuỗi gốc, bạn lưu một vị trí `p`. Một phép biến đổi chỉ xảy ra **trước** vị trí đó, thay đoạn nguồn dài `oldSize` UTF-16 code unit bằng đoạn mới dài `newSize`. Nếu phép biến đổi là một lần thay thế đơn giản và không đụng đến chính ký tự mục tiêu, vị trí mới là:

```text
newIndex = oldIndex + (newSize - oldSize)
```

Ví dụ nguồn `"ABC|END"`, chỉ số của `|` là `3`:
- Thay `"ABC"` bằng `"ABCDE"`: chèn thêm hai code unit trước dấu `|`, vị trí mới là `5`.
- Thay `"ABC"` bằng `"X"`: giảm hai code unit, vị trí mới là `1`.
- Sửa một vùng **nằm hoàn toàn sau** dấu `|`: vị trí của dấu `|` không đổi, nếu không xảy ra biến đổi khác trước nó.

**Công thức trên chỉ mô tả một thay đổi đơn giản với vùng thay thế được biết rõ.** Nếu có nhiều vùng được thay, quy tắc tổng quát là cộng các độ lệch của những lần thay **trước** vị trí mục tiêu; nếu phép biến đổi xóa, thay hoặc tái sắp xếp chính mục tiêu, không thể luôn xác định một chỉ số mới duy nhất bằng cách cộng độ lệch.

Với normalization Unicode, chuyển hoa/thường phụ thuộc ngữ cảnh, xử lý regex hay thay thế có nhiều điểm khớp, tự “cộng một độ lệch chung” thường không đủ. Cần xác định lại vị trí trên văn bản đầu ra hoặc giữ một bản đồ ánh xạ sinh ra cùng phép biến đổi.

## 5. Trường hợp 1: đổi chữ hoa có thể mở rộng `ß` thành `SS`

Trên Java 17:

```java
String original = "Stra\u00DFe|END";
int oldPos = original.indexOf('|'); // 6

String upper = original.toUpperCase(java.util.Locale.ROOT);

System.out.println(original.length()); // 10
System.out.println(upper.length());    // 11
System.out.println(oldPos);            // 6
System.out.println(upper.indexOf('|'));// 7
System.out.println(upper.charAt(6));   // E
```

Giải thích chỉ số:

| Chuỗi | Nội dung | Chỉ số của `|` |
| --- | --- | ---: |
| Gốc | `Straße|END` | 6 |
| Sau biến đổi | `STRASSE|END` | 7 |

Vùng `"Straße"` dài 6 code unit, còn `"STRASSE"` dài 7. Thêm một code unit trước `|` đã làm tất cả vị trí theo sau lệch thêm một đơn vị.

Điểm cần lưu ý:
- `toUpperCase(Locale.ROOT)` thực hiện chuyển chữ hoa theo quy tắc không phụ thuộc locale mặc định, nhưng **không bảo đảm giữ nguyên độ dài**.
- `toLowerCase(Locale)` cũng có thể phụ thuộc locale, ngữ cảnh và biến đổi độ dài. Ví dụ một số phép biến đổi chữ `İ` (Latin capital I with dot) có thể tạo ra nhiều code point với locale thích hợp.
- Việc chuyển hoa/thường không tạo một **bản đồ vị trí gốc → kết quả** cho người gọi; chỉ biết văn bản kết quả chưa đủ để khôi phục mọi quan hệ khi ký tự bị mở rộng hoặc gộp.

## 6. Trường hợp 2: Unicode normalization có thể rút ngắn hoặc kéo dài

Unicode có nhiều cách mã hóa tương đương về mặt chuẩn tắc. Chữ `é` có thể là:
- Một code point dựng sẵn `U+00E9`.
- Hai code point `U+0065` (`e`) và `U+0301` (combining acute accent).

`Normalizer.Form.NFC` có thể ghép chúng thành một code point; `Normalizer.Form.NFD` có thể tách ra.

```java
import java.text.Normalizer;

String decomposed = "Cafe\u0301|OK";
int separatorBefore = decomposed.indexOf('|'); // 5

String normalized = Normalizer.normalize(
        decomposed, Normalizer.Form.NFC);

System.out.println(decomposed.length());     // 8
System.out.println(normalized.length());     // 7
System.out.println(separatorBefore);         // 5
System.out.println(normalized.indexOf('|'));  // 4
System.out.println(normalized.charAt(5));     // O
```

Dấu `|` ở chỉ số 5 trên chuỗi gốc chuyển về chỉ số 4 trên chuỗi NFC. Nếu lấy vùng phía sau dấu bằng `substring(separatorBefore + 1)` trên chuỗi đã chuẩn hóa, bạn sẽ bỏ mất ký tự đầu tiên của vùng cần lấy.

**Normalization không đồng nghĩa xóa dấu**. NFC giữ ý nghĩa Unicode chuẩn tắc; nó chỉ đổi cách biểu diễn. `Normalizer.Form.NFKC/NFKD` còn có phép chuẩn hóa tương thích, có thể tác động đến những khác biệt ký tự khác. Hãy chọn dạng chuẩn hóa theo yêu cầu dữ liệu và chấp nhận rằng độ dài có thể thay đổi.

## 7. Trường hợp 3: thay thế và thay đổi độ dài UTF-16 khi có emoji

`String` dùng UTF-16, vì vậy thay một ký tự BMP bằng một code point ngoài BMP có thể tăng `length()` dù số code point không đổi.

```java
String source = "A\uD83D\uDE80B"; // A🚀B
int oldB = source.indexOf("B");   // 3

String changed = source.replace("A", "AA");

System.out.println(source.length());  // 4
System.out.println(changed.length()); // 5
System.out.println(oldB);             // 3
System.out.println(changed.indexOf("B")); // 4
```

Chính ký tự `🚀` chiếm **hai** code unit, và phép thay thế thêm một `A` trước `B`, nên `B` dịch từ chỉ số 3 sang 4.

Một ví dụ khác, thay `"A"` bằng `"🚀"`:

```java
String source = "AB";
String changed = source.replace("A", "\uD83D\uDE80");

System.out.println(source.length());          // 2
System.out.println(changed.length());         // 3
System.out.println(source.codePointCount(0, source.length()));   // 2
System.out.println(changed.codePointCount(0, changed.length())); // 2
```

Ở đây **số code point vẫn bằng 2**, còn độ dài UTF-16 tăng từ 2 lên 3. Vì vậy, chỉ nhìn số code point trước/sau chưa đủ để tính độ lệch của các API nhận chỉ số `char`.

## 8. Chương trình Java 17+ chạy độc lập

Lưu nội dung sau thành file `StringIndexAfterTransformDemo.java`:

```java
import java.text.Normalizer;
import java.util.Locale;

public class StringIndexAfterTransformDemo {
    public static void main(String[] args) {
        String original = "Stra\u00DFe|END";
        int oldSeparator = original.indexOf('|');
        String upper = original.toUpperCase(Locale.ROOT);

        System.out.println("original = " + original);
        System.out.println("upper = " + upper);
        System.out.println("original length = " + original.length());
        System.out.println("upper length = " + upper.length());
        System.out.println("old separator = " + oldSeparator);
        System.out.println("new separator = " + upper.indexOf('|'));
        System.out.println("char at old index = "
                + upper.charAt(oldSeparator));
        System.out.println("correct right part = "
                + upper.substring(upper.indexOf('|') + 1));

        String decomposed = "Cafe\u0301|OK";
        String nfc = Normalizer.normalize(
                decomposed, Normalizer.Form.NFC);

        System.out.println("NFD-like length = "
                + decomposed.length());
        System.out.println("NFC length = " + nfc.length());
        System.out.println("old NFC separator = "
                + decomposed.indexOf('|'));
        System.out.println("new NFC separator = " + nfc.indexOf('|'));

        String emoji = "A\uD83D\uDE80B";
        String inserted = emoji.replace("A", "AA");

        System.out.println("emoji old length = " + emoji.length());
        System.out.println("emoji new length = " + inserted.length());
        System.out.println("emoji old B index = " + emoji.indexOf("B"));
        System.out.println("emoji new B index = " + inserted.indexOf("B"));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 StringIndexAfterTransformDemo.java
java StringIndexAfterTransformDemo
```

Kết quả:

```text
original = Straße|END
upper = STRASSE|END
original length = 10
upper length = 11
old separator = 6
new separator = 7
char at old index = E
correct right part = END
NFD-like length = 8
NFC length = 7
old NFC separator = 5
new NFC separator = 4
emoji old length = 4
emoji new length = 5
emoji old B index = 3
emoji new B index = 4
```

Bản `String` cũ không hề bị chỉnh sửa. Hai biến tham chiếu `original`/`upper`, `decomposed`/`nfc`, `emoji`/`inserted` lần lượt chứa các phiên bản khác nhau của nội dung. Mọi chỉ số phải được giải thích **trên đúng phiên bản chuỗi sinh ra nó**.

## 9. Sai lầm phổ biến: dùng lại `substring`, `Matcher` hoặc vị trí con trỏ cũ

Giả sử phân tích chuỗi thành hai phần qua dấu `|`:

```java
String input = "Stra\u00DFe|END";
int cut = input.indexOf('|');

String upper = input.toUpperCase(java.util.Locale.ROOT);

// SAI: cut được tính trên input, không phải upper.
String wrong = upper.substring(cut + 1);

// ĐÚNG: tìm lại ranh giới trên chuỗi kết quả.
int newCut = upper.indexOf('|');
String correct = upper.substring(newCut + 1);
```

`wrong` ở ví dụ này trả `"|END"`, còn `correct` trả `"END"`, bởi sau khi mở rộng `ß`, vị trí của dấu phân cách đã tăng lên 1.

Các nơi khác cũng có thể mắc lỗi này:
- Lưu `start`/`end` từ `Matcher.find()` trên chuỗi gốc, rồi dùng các chỉ số đó để cắt chuỗi sau khi `replaceAll` hoặc normalize.
- Lưu vị trí dấu phân cách bằng `indexOf`, rồi gọi `stripLeading` khiến tiền tố bị xóa.
- Lưu điểm bắt đầu vùng được bôi chọn (selection) trong một editor, rồi chuẩn hóa hoặc thay thế văn bản trước vùng đó.
- Lưu ranh giới cắt theo `length()` gốc rồi chuyển chữ hoa/thường toàn chuỗi.
- Ghi chỉ số UTF-16 trong một hệ thống nhưng truyền nó như chỉ số code point cho API khác.

**Nguyên tắc:** Một chỉ số không tự tồn tại độc lập; nó luôn đi kèm **phiên bản văn bản + đơn vị chỉ số + ý nghĩa của vị trí**.

## 10. Các cách xử lý an toàn

### Cách A — Biến đổi trước, tìm chỉ số sau

Khi có thể xác định ranh giới bằng dấu phân cách, mẫu hoặc token sau biến đổi, hãy xử lý chuỗi trước rồi tìm vị trí trong chính kết quả:

```java
String normalized = java.text.Normalizer.normalize(
        input, java.text.Normalizer.Form.NFC);
int separator = normalized.indexOf('|');

if (separator < 0) {
    throw new IllegalArgumentException("Missing separator");
}
String right = normalized.substring(separator + 1);
```

Cách này đơn giản, nhưng cần kiểm tra nếu phép biến đổi có thể thay đổi hoặc loại bỏ dấu phân cách. Với dấu/từ khóa xuất hiện nhiều lần, `indexOf` không tự cho biết đâu là occurrence ban đầu cần theo dõi; hãy dùng cấu trúc dữ liệu ổn định hơn (ID, vị trí token trong cây cú pháp, hoặc offset map).

### Cách B — Trích xuất vùng có ý nghĩa trước rồi biến đổi từng vùng

Khi các phần văn bản được tách bằng một ranh giới rõ ràng, thường thuận tiện hơn nếu bạn **chia trước**:

```java
import java.util.Locale;

String input = "Stra\u00DFe|END";
int separator = input.indexOf('|');

String left = input.substring(0, separator)
                   .toUpperCase(Locale.ROOT);
String right = input.substring(separator + 1)
                    .toUpperCase(Locale.ROOT);

String result = left + "|" + right;
System.out.println(result); // STRASSE|END
```

Lúc này, ranh giới được xác định trên chuỗi gốc đúng nơi nó có ý nghĩa; phép biến đổi sau đó diễn ra riêng trong mỗi thành phần. Tất nhiên, nếu bạn vẫn cần trỏ tới ký tự bên trong `left` hoặc `right` sau biến đổi, vị trí bên trong thành phần đó vẫn phải được tính lại.

### Cách C — Ánh xạ vị trí khi tự kiểm soát các phép chỉnh sửa

Nếu bạn chủ động thực hiện **một lần** thay đoạn `[start, end)` của chuỗi gốc bằng `replacement`, có thể xác định độ lệch của các vị trí nằm **sau** đoạn thay:

```java
int oldLength = end - start;
int newLength = replacement.length();
int delta = newLength - oldLength;

// Áp dụng cho một vị trí p nằm SAU hoặc TẠI end,
// và chỉ khi đây là phép sửa duy nhất ảnh hưởng trước p.
int newPosition = p + delta;
```

Nếu `p < start`, vị trí không đổi (trong trường hợp không có sửa nào khác). Nếu `p` nằm **bên trong** vùng bị thay, không có quy tắc chung để biết “ký tự tương ứng” ở kết quả; phải định nghĩa chính sách con trỏ (ví dụ gắn về đầu hoặc cuối đoạn thay) tùy ứng dụng.

Với nhiều thay đổi, hãy tạo mapping từ chuỗi sửa đổi đã thực hiện: lưu mỗi khoảng nguồn, khoảng đích và độ lệch lũy kế. Các phép biến đổi không bảo toàn một-một, như chuẩn hóa hoặc case mapping, cần quy tắc mapping riêng cho vị trí trong phần bị gộp/mở rộng. Không nên suy luận mapping bằng một tỷ lệ độ dài chung.

### Cách D — Nếu sửa nhiều vùng ở vị trí gốc, cân nhắc sửa từ phải sang trái

Khi có nhiều **vùng không giao nhau được xác định trước trên chuỗi gốc**, thực hiện các phép thay thế từ vị trí lớn xuống nhỏ có thể giữ cho các vị trí gốc **ở bên trái** chưa bị dịch.

Ví dụ:

```java
StringBuilder text = new StringBuilder("abcd");

// Vùng cuối trước, rồi mới vùng đầu; hai vùng không giao nhau.
text.replace(2, 4, "XYZ"); // abXYZ
text.replace(0, 2, "P");   // PXYZ

System.out.println(text); // PXYZ
```

Đây là một kỹ thuật hữu ích với nhiều lần sửa do ứng dụng tự xác định vị trí; nó **không tự ánh xạ** con trỏ hoặc token xuyên qua một phép chuẩn hóa Unicode toàn chuỗi, và không áp dụng bừa bãi khi các vùng chồng lấn nhau.

## 11. `offsetByCodePoints` giúp đổi đơn vị, không giúp ánh xạ qua phép biến đổi

API `String.offsetByCodePoints(int index, int codePointOffset)` nhận **chỉ số UTF-16** trong **một chuỗi cụ thể**, di chuyển theo số code point và trả về chỉ số UTF-16 trong **cùng chuỗi đó**.

Ví dụ:

```java
String text = "A\uD83D\uDE80B"; // A🚀B
int beforeRocket = text.offsetByCodePoints(0, 1);
int afterRocket = text.offsetByCodePoints(beforeRocket, 1);

System.out.println(beforeRocket); // 1
System.out.println(afterRocket);  // 3
System.out.println(text.substring(beforeRocket, afterRocket)); // 🚀
```

Phương thức này rất hữu ích khi cắt chuỗi theo số code point. Nhưng nếu bạn đã chuyển đổi `text` thành một `String` khác có độ dài/nội dung thay đổi, không thể truyền các chỉ số cũ vào chuỗi mới và giả định chúng còn đúng.

Một số phép biến đổi có thể làm thay đổi **ngay cả số code point** (`ß` → `SS`, `e + ◌́` → `é`). Vì vậy đổi sang chỉ số code point **không loại bỏ nhu cầu ánh xạ giữa phiên bản gốc và phiên bản kết quả**.

## 12. Kiểm thử khi một ứng dụng lưu vị trí văn bản

Đối với chức năng soạn thảo, highlight, chú thích, phân tích cú pháp hoặc trích xuất dữ liệu, nên kiểm thử tối thiểu các nhóm sau:

| Dữ liệu kiểm thử | Điều cần kiểm chứng |
| --- | --- |
| ASCII đơn giản, không đổi độ dài | Các chỉ số vẫn đúng khi phép biến đổi thật sự bảo toàn đơn vị và thứ tự. |
| Ký tự có case expansion như `ß` | Chỉ số sau vùng mở rộng tăng. |
| Chuỗi gồm dấu kết hợp + NFC/NFD | Chỉ số có thể giảm/tăng, một vùng cũ có thể không còn tương ứng từng `char`. |
| Ký tự ngoài BMP như `🚀` | Chỉ số UTF-16 khác số code point. |
| Chuỗi có khoảng trắng ở đầu + `stripLeading` | Mọi vị trí phía sau phần bị xóa cần tính lại trên chuỗi mới. |
| Thay regex nhiều lần, thay đổi độ dài ở nhiều điểm | Một độ lệch duy nhất thường không đủ; cần tái tính/mapping có hệ thống. |
| Dấu phân cách hoặc token bị thay/xóa | Không thể chỉ tìm lại bằng chuỗi cũ; cần chiến lược nhận diện token. |
| Chỉ số nằm trong vùng bị thay hoặc ngay tại ranh giới | Chính sách neo vị trí phải được định nghĩa rõ. |

Khi lưu chỉ số ở API hoặc database, hãy ghi rõ đơn vị (**UTF-16 code unit**, **code point** hoặc **grapheme cluster**) và phiên bản văn bản mà vị trí đó tham chiếu. Chỉ số có thể vẫn nằm trong phạm vi hợp lệ nhưng biểu thị một phần hoàn toàn khác của chuỗi sau biến đổi.

## 13. Các lỗi thường gặp

**Lỗi 1 — “`String` bất biến nên chỉ số không đổi.”** Đối tượng gốc bất biến; chuỗi sau phép biến đổi là một giá trị khác. Chỉ số cũ áp dụng cho đối tượng gốc.

**Lỗi 2 — “Chuyển hoa/thường luôn đổi một ký tự thành một ký tự.”** Một số ánh xạ Unicode như `ß` → `SS` thay đổi số code point và `char`. Không dùng giả định này cho văn bản quốc tế.

**Lỗi 3 — “Chuẩn hóa Unicode không thay đổi độ dài.”** NFC/NFD có thể ghép/tách code point, nên vị trí ranh giới thay đổi dù ý nghĩa văn bản tương đương.

**Lỗi 4 — “Số code point không đổi thì chỉ số UTF-16 không đổi.”** Thay `"A"` bằng `"🚀"` giữ một code point nhưng thêm một UTF-16 code unit.

**Lỗi 5 — “Nếu `index < newString.length()` thì dùng được.”** Kiểm tra giới hạn chỉ xác nhận một vị trí hợp lệ về mặt số học; không bảo đảm nó còn trỏ tới token/ký tự cũ.

**Lỗi 6 — “Tìm lại bằng `indexOf` luôn ánh xạ chính xác vị trí gốc.”** Nếu token xuất hiện nhiều lần, bị thay đổi hoặc bị xóa trong phép biến đổi, cần nhận diện ổn định hoặc mapping được tạo cùng thao tác.

**Lỗi 7 — “`offsetByCodePoints` chuyển chỉ số từ chuỗi cũ sang chuỗi mới.”** API này chỉ di chuyển theo code point **trong cùng chuỗi**.

## 14. Bài tập tự kiểm tra

### Bài 1 — Vị trí dấu phân cách sau case mapping

```java
String a = "Stra\u00DFe|OK";
int p = a.indexOf('|');
String b = a.toUpperCase(java.util.Locale.ROOT);
```

Hỏi `p` và `b.indexOf('|')`?

**Đáp án:** `p == 6`, `b.indexOf('|') == 7`. Chuỗi `"Straße"` đổi thành `"STRASSE"` dài hơn một UTF-16 code unit.

### Bài 2 — Vị trí dấu phân cách sau NFC

```java
String a = "e\u0301|B";
String b = java.text.Normalizer.normalize(
        a, java.text.Normalizer.Form.NFC);
```

Hỏi dấu `|` ở vị trí nào trước và sau?

**Đáp án:** Trước ở chỉ số `2` (hai code point `e` và dấu sắc), sau ở chỉ số `1` (chữ `é` dựng sẵn).

### Bài 3 — Chỉ số code point và UTF-16

```java
String before = "AB";
String after = before.replace("A", "\uD83D\uDE80");
```

Hỏi `after.length()` và số code point?

**Đáp án:** `after.length() == 3`, còn `after.codePointCount(0, after.length()) == 2`. `🚀` là một code point nhưng hai `char`.

### Bài 4 — Sửa lỗi dùng offset cũ

```java
String input = "Stra\u00DFe|END";
int oldPos = input.indexOf('|');
String upper = input.toUpperCase(java.util.Locale.ROOT);

// Cần lấy "END", nhưng code sau lấy sai:
String result = upper.substring(oldPos + 1);
```

**Cách sửa:** Tính `int newPos = upper.indexOf('|');` rồi `String result = upper.substring(newPos + 1);`. Nếu có nhiều dấu phân cách, hãy xác định rõ dấu cần dùng để tránh chỉ chọn nhầm occurrence.

### Bài 5 — Có thể cập nhật một offset bằng chênh lệch `length()` không?

**Đáp án:** Chỉ trong điều kiện cụ thể: bạn biết các vùng sửa và chúng nằm trước vị trí cần cập nhật, không thay/xóa chính vị trí đó. Với nhiều vùng sửa hoặc biến đổi Unicode toàn chuỗi, phải theo dõi mapping hoặc xác định lại vị trí trên kết quả; `newLength - oldLength` toàn chuỗi không đủ để ánh xạ tùy ý.

## Tài liệu tham khảo

- [Java SE 17 — `String` API: `length`, `substring`, `replace`, `toUpperCase`, `offsetByCodePoints`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html)
- [Java SE 17 — `Normalizer`: NFC, NFD, NFKC, NFKD](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/Normalizer.html)
- [Java SE 17 — `Character`: Unicode code point và surrogate](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html)
- [Unicode Standard Annex #15 — Unicode Normalization Forms](https://www.unicode.org/reports/tr15/)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-transformation-007">Phân biệt phép biến đổi giữ nguyên ý nghĩa với phép làm mất dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt phép biến đổi `String` giữ nguyên ý nghĩa với phép làm mất dữ liệu

Một chương trình có thể cần chuẩn hóa tên người dùng trước khi tìm kiếm, loại bỏ khoảng trắng thừa, đổi chữ hoa/chữ thường hoặc thay một ký tự. Những thao tác này thường được gọi chung là *biến đổi chuỗi* (*string transformation*). Tuy nhiên, chúng không có cùng tính chất: có phép biến đổi **giữ nguyên toàn bộ dữ liệu để khôi phục**, có phép chỉ **giữ ý nghĩa theo một quy tắc cụ thể**, và có phép **loại bỏ thông tin vĩnh viễn**.

Ví dụ, `"Cafe\u0301"` (chữ `e` và dấu sắc kết hợp) và `"Caf\u00E9"` (chữ `é` dựng sẵn) là hai biểu diễn Unicode tương đương về mặt chuẩn hóa chính tắc. Chuẩn hóa NFC có thể biến dạng thứ nhất thành dạng thứ hai, nhưng các đơn vị mã ban đầu **không còn giống hệt**. Ngược lại, việc xóa dấu trong tên riêng có thể khiến nhiều tên khác nhau thành cùng một kết quả: không thể từ chuỗi mới đoán chính xác chuỗi ban đầu.

Bài học này giúp bạn xác định rõ **thông tin nào phải được giữ lại**, **thế nào là “cùng ý nghĩa” trong bài toán**, và **khi nào nên giữ bản gốc cùng với chuỗi đã chuẩn hóa**.

## 1. Hai câu hỏi khác nhau: giữ nguyên dữ liệu hay giữ nguyên ý nghĩa?

Giả sử có phép biến đổi `T` từ `String` đầu vào sang `String` đầu ra. Trước khi đánh giá `T`, hãy hỏi:

1. **Giữ nguyên dữ liệu (*lossless / reversible*)?** Từ kết quả và thông tin cần thiết kèm theo, có khôi phục **chính xác** dãy dữ liệu gốc hay không? Nếu `T(x) == T(y)` với hai đầu vào khác nhau mà không lưu thêm thông tin, phép biến đổi không thể đảo ngược hoàn toàn.
2. **Giữ nguyên ý nghĩa (*semantic equivalence*)?** Sau biến đổi, dữ liệu có còn được coi là “giống nhau” theo **quy tắc nghiệp vụ đã xác định** hay không?

Hai câu hỏi **không đồng nghĩa**. Một phép biến đổi có thể không phục hồi được nguyên văn, nhưng vẫn bảo toàn ý nghĩa cho một tác vụ cụ thể như tìm kiếm không phân biệt hoa/thường. Ngược lại, có thể mã hóa dữ liệu theo một dạng khác rồi giải mã chính xác, dù biểu diễn trung gian không được dùng trực tiếp như văn bản.

### Ba mức độ thường gặp

| Mức độ | Ví dụ | Điều đảm bảo và giới hạn |
| --- | --- | --- |
| **Bảo toàn dữ liệu, có thể hoàn nguyên** | Mã hóa byte UTF-8 thành Base64 rồi giải mã với đúng quy trình | Có thể lấy lại dãy byte ban đầu; nếu mã hóa `String`, cần bảo đảm chuỗi hợp lệ với encoding đã chọn và không bị thay thế ký tự lỗi. |
| **Bảo toàn ý nghĩa theo quy tắc, có thể đổi biểu diễn** | Chuẩn hóa Unicode dạng NFC để so khớp văn bản tương đương chính tắc | Có thể đổi dãy đơn vị mã UTF-16; không hứa khôi phục nguyên văn đầu vào. |
| **Có thể mất thông tin hoặc thay đổi ý nghĩa** | Xóa dấu, xóa khoảng trắng, cắt chuỗi, thay nhiều ký tự thành một | Các đầu vào khác nhau có thể cho cùng đầu ra hoặc thông tin cần thiết bị loại bỏ. |

**Không tồn tại một định nghĩa “giữ nguyên ý nghĩa” áp dụng cho mọi ứng dụng.** `" A "` và `"A"` có thể tương đương trong ô tìm kiếm, nhưng **khác nhau** nếu đang lưu mật khẩu, nội dung mã nguồn, dữ liệu văn bản cần kiểm toán hoặc một định danh có quy tắc phân biệt khoảng trắng.

## 2. `String` immutable không có nghĩa phép biến đổi là lossless

Đối tượng `String` của Java bất biến: khi gọi `strip()`, `replace()`, `toLowerCase()` hoặc `substring()`, nội dung của đối tượng gốc không bị sửa. Phương thức trả về kết quả chuỗi, có thể là đối tượng mới hoặc chính đối tượng hiện tại nếu nội dung không đổi.

```java
String original = "  Java  ";
String transformed = original.strip();

System.out.println("original = [" + original + "]");
System.out.println("transformed = [" + transformed + "]");
```

Kết quả:

```text
original = [  Java  ]
transformed = [Java]
```

**Chuỗi gốc vẫn còn nguyên** trong biến `original`, nhưng riêng `transformed` không chứa thông tin về số khoảng trắng đã bị bỏ. Nếu chương trình chỉ lưu `transformed` rồi loại bỏ `original`, sự khác biệt ở hai đầu đã bị mất.

Nói cách khác:

- **Tính bất biến**: thao tác không chỉnh sửa đối tượng `String` đã tồn tại.
- **Tính đảo ngược**: có thể khôi phục chính xác dữ liệu đầu vào từ đầu ra không?
- **Tính tương đương ngữ nghĩa**: ứng dụng có coi đầu ra và đầu vào là cùng một giá trị không?

Đây là ba thuộc tính độc lập cần phân biệt khi thiết kế dữ liệu.

## 3. Chuẩn hóa Unicode: giữ tương đương chính tắc nhưng đổi biểu diễn

Unicode cho phép một số văn bản có nhiều biểu diễn. Chẳng hạn:

- `"\u00E9"`: ký tự `é` dựng sẵn, một UTF-16 code unit.
- `"e\u0301"`: ký tự `e` theo sau bởi dấu sắc kết hợp, hai UTF-16 code units.

Hai biểu diễn này là **canonically equivalent** theo Unicode; với `String.equals()`, chúng vẫn **không bằng nhau** vì `equals()` so sánh dãy đơn vị mã chính xác.

Java có lớp `java.text.Normalizer`:

- `Normalizer.Form.NFC`: biểu diễn chuẩn hóa kết hợp theo quy tắc chính tắc.
- `Normalizer.Form.NFD`: biểu diễn chuẩn hóa phân rã theo quy tắc chính tắc.
- `Normalizer.Form.NFKC` và `NFKD`: chuẩn hóa tương thích, có thể hợp nhất nhiều ký tự **vốn mang khác biệt về hình thức hoặc ý nghĩa sử dụng**.

Ví dụ:

```java
String decomposed = "Cafe\u0301";
String composed = "Caf\u00E9";

String nfc = java.text.Normalizer.normalize(
        decomposed, java.text.Normalizer.Form.NFC);

System.out.println(decomposed.equals(composed)); // false
System.out.println(nfc.equals(composed));        // true
```

Chuẩn hóa NFC/NFD thường phù hợp để so sánh theo **tương đương chính tắc**. Tuy nhiên, dù người dùng xem đó là cùng chữ, dãy code units có thể thay đổi; nếu hệ thống cần lưu chính xác dữ liệu đã nhập, vẫn phải giữ **giá trị gốc**.

### Trường hợp cần đặc biệt thận trọng: NFKC / NFKD

Chuẩn hóa tương thích có thể xóa những phân biệt mà ứng dụng cần giữ. Ví dụ ký tự số được khoanh `①` có thể được chuẩn hóa dạng NFKC thành `1`:

```java
String circled = "\u2460"; // ①
String compatibility = java.text.Normalizer.normalize(
        circled, java.text.Normalizer.Form.NFKC);

System.out.println(compatibility);           // 1
System.out.println(circled.equals(compatibility)); // false
```

Nếu văn bản là nội dung cần in chính xác, một biểu tượng liệt kê, công thức hoặc mã nhận diện đặc biệt, thay `①` thành `1` có thể thay đổi thông tin hữu ích. Đừng chọn NFKC chỉ vì muốn “chuẩn hóa mạnh hơn”; hãy xác định hợp đồng dữ liệu trước.

**Lưu ý:** Chuẩn hóa Unicode không đồng nghĩa chuyển hết về ASCII, bỏ dấu, dịch ngôn ngữ hay tự phát hiện ký tự người dùng nhìn giống nhau. Một số ký tự trông tương tự nhưng không tương đương theo chuẩn Unicode.

## 4. Đổi chữ hoa/thường: thuận tiện cho tra cứu, có thể mất dữ liệu

`toLowerCase()` và `toUpperCase()` thường dùng để tạo **khóa tìm kiếm**:

```java
String displayName = "Java API";
String lookupKey = displayName.toLowerCase(java.util.Locale.ROOT);

System.out.println(displayName); // Java API
System.out.println(lookupKey);   // java api
```

Trong hệ thống tìm kiếm được định nghĩa **không phân biệt chữ hoa/thường**, hai giá trị có thể cùng một ý nghĩa tra cứu. Nhưng `lookupKey` không giữ thông tin chữ nào vốn được viết hoa. Ví dụ các chuỗi `"Java"`, `"JAVA"` và `"java"` có thể biến thành cùng khóa `"java"`. Do đó đây **không phải phép biến đổi đảo ngược** từ kết quả đơn lẻ.

Ngoài ra, chuyển hoa/thường có quy tắc Unicode và phụ thuộc ngôn ngữ:

```java
String capitalI = "I";
System.out.println(
    capitalI.toLowerCase(java.util.Locale.ROOT)); // i
System.out.println(
    capitalI.toLowerCase(java.util.Locale.forLanguageTag("tr"))); // ı
```

Với tiếng Thổ Nhĩ Kỳ, `I` chuyển thường có thể thành `ı` (chữ i không chấm), khác với quy tắc dùng `Locale.ROOT`. Một số phép đổi chữ còn có thể làm **thay đổi số code points**.

Cách chọn:

- Dùng `Locale.ROOT` khi cần quy tắc độc lập ngôn ngữ để tạo khóa kỹ thuật **và** quy tắc đó thật sự phù hợp dữ liệu.
- Dùng locale tương ứng khi xử lý văn bản người dùng theo ngôn ngữ cụ thể.
- Cân nhắc `equalsIgnoreCase()` khi mục đích chỉ là so sánh bỏ qua hoa/thường theo hợp đồng Java, nhưng **đừng đồng nhất nó với toàn bộ thuật toán Unicode case folding hoặc so khớp ngôn ngữ tự nhiên**.
- Đừng tự động `toLowerCase()` khi lưu mã định danh, mật khẩu hoặc chữ ký số nếu hợp đồng yêu cầu giữ nguyên chữ hoa/thường.

## 5. Loại khoảng trắng: khi nào là làm sạch, khi nào là xóa dữ liệu?

Các API thường gặp:

| API | Tác động | Rủi ro về thông tin |
| --- | --- | --- |
| `strip()` (Java 11+) | Bỏ ký tự whitespace ở **đầu và cuối** theo `Character.isWhitespace` | Mất chính xác số và loại whitespace đã bỏ. |
| `trim()` | Bỏ các đơn vị mã ở đầu/cuối có giá trị không lớn hơn U+0020 | Tiêu chí khác `strip()`; không phải quy tắc Unicode whitespace tổng quát. |
| `stripLeading()` / `stripTrailing()` | Chỉ bỏ ở một phía | Có thể xóa khoảng trắng có nghĩa ở phía đó. |
| `replace("  ", " ")` | Thay đúng hai dấu cách liên tiếp bằng một dấu cách mỗi lần khớp | Mất phân biệt một hay hai dấu cách; không phải thuật toán gộp mọi loại whitespace. |
| `replaceAll(regex, replacement)` | Thay các chuỗi khớp biểu thức chính quy | Có thể xóa nhiều ký tự khác nhau; cần hiểu cú pháp regex và escape. |

Ví dụ `"  A  B  ".strip()` trả `"A  B"`. Việc bỏ hai dấu cách hai bên có thể chấp nhận được nếu đó là lỗi nhập liệu; nhưng nếu văn bản thuộc định dạng cố định theo cột, khoảng trắng đầu/cuối có thể là một phần dữ liệu.

Nếu tiếp tục `"A  B".replace("  ", " ")`, kết quả là `"A B"`. Không còn cách phân biệt chắc chắn nó xuất phát từ `"A  B"` hay `"A B"` nếu chỉ còn đầu ra `"A B"`.

Không nên mặc định “whitespace vô nghĩa”. Với một đoạn mã nguồn, tệp văn bản, chuỗi trong báo cáo hoặc mật khẩu, khoảng trắng có thể mang ý nghĩa quan trọng. Với trường tên hiển thị, tìm kiếm hoặc tag, quy tắc loại bỏ whitespace có thể phù hợp **nếu sản phẩm đã xác định rõ**.

### Phân biệt `isEmpty()` và `isBlank()`

`isEmpty()` kiểm tra `length() == 0`; `isBlank()` kiểm tra chuỗi rỗng hoặc chỉ chứa code points mà `Character.isWhitespace()` coi là whitespace. Hai hàm chỉ **kiểm tra**, không biến đổi chuỗi.

```java
String spaces = " \t";
System.out.println(spaces.isEmpty()); // false
System.out.println(spaces.isBlank()); // true
```

Trong nhiều biểu mẫu, `isBlank()` thích hợp để phát hiện đầu vào chỉ chứa whitespace. Nhưng tiêu chí `Character.isWhitespace()` không bao trùm tất cả ký tự có hình dạng giống khoảng trắng (chẳng hạn một số non-breaking spaces); nếu cần chuẩn hóa toàn bộ khoảng trắng trong văn bản quốc tế, phải đặc tả thêm chính sách.

## 6. Cắt, xóa, thay thế và rút gọn: các phép biến đổi dễ mất dữ liệu

Những phương thức như `substring()`, `replace()`, `replaceAll()` có thể giúp tạo định dạng mới, nhưng tính đảo ngược **không được đảm bảo**.

### Cắt chuỗi

```java
String code = "ORDER-2026-001";
String suffix = code.substring(code.length() - 3);

System.out.println(suffix); // 001
```

Nếu chỉ lưu `"001"`, không thể biết mã gốc là `"ORDER-2026-001"`, `"USER-001"` hay một giá trị khác. Đây là phép **chỉ giữ một phần thông tin**. Một ví dụ khác là giới hạn hiển thị bằng `substring(0, max)`; nó có thể bỏ đi phần nội dung cần thiết.

**Chỉ số `substring()` tính theo UTF-16 code units**. Không cắt tùy tiện giữa hai phần của surrogate pair (ví dụ nhiều emoji), vì điều đó có thể tạo chuỗi chứa surrogate đơn lẻ. Nếu mục tiêu là giới hạn số ký tự người dùng nhìn thấy, cần xử lý grapheme clusters, không chỉ `length()`.

### Xóa hoặc hợp nhất ký tự

```java
String withDash = "AB-12";
String noDash = withDash.replace("-", "");

System.out.println(noDash); // AB12
```

Từ `"AB12"` không thể xác định liệu đầu vào đã chứa dấu gạch ngang hay chưa, hoặc dấu đó ở vị trí nào. Việc xóa dấu chỉ giữ “ý nghĩa” nếu **quy tắc mã** nói dấu phân cách không mang ý nghĩa và không cần khôi phục.

Ví dụ xóa dấu tiếng Việt để tìm kiếm cũng có thể làm nhiều từ khác nhau trở thành cùng một khóa. Bỏ dấu có thể phù hợp cho **chỉ mục tìm kiếm phụ**, nhưng không nên thay dữ liệu gốc nếu tên riêng và dấu thanh có ý nghĩa.

### `replace()` khác `replaceAll()`

`String.replace(CharSequence, CharSequence)` tìm chuỗi con **nguyên văn**; `replaceAll(String regex, String replacement)` hiểu đối số thứ nhất là **biểu thức chính quy**.

```java
String value = "a.b";
System.out.println(value.replace(".", ""));    // ab
System.out.println(value.replaceAll(".", "")); // chuỗi rỗng
```

Với `replaceAll(".", "")`, dấu chấm trong regex có nghĩa là khớp gần như mọi ký tự đơn, nên toàn bộ các ký tự `a.b` bị loại. Đây là lỗi điển hình khi nhầm chuỗi nguyên văn với regex.

Hãy kiểm tra kỹ quy tắc thay thế (đặc biệt khi tham số đến từ người dùng), vì một regex hoặc replacement không đúng có thể xóa nhiều nội dung ngoài dự kiến. Thao tác xóa dữ liệu cần được xem là một quyết định nghiệp vụ có chủ đích.

## 7. Mã hóa và giải mã có thể hoàn nguyên, nhưng phải xác định miền dữ liệu

Một phép mã hóa như Base64 chỉ đổi biểu diễn của **dãy byte**: khi mã hóa và giải mã đúng cách, các byte được giữ nguyên. Đối với `String`, cần chọn charset nhất quán (thường là UTF-8) trước và sau bước Base64.

```text
String → UTF-8 bytes → Base64 text
                    ← decode Base64 ←
String ← UTF-8 bytes ←
```

Với một `String` chứa dãy Unicode well-formed và không có lỗi chuyển mã, bước này có thể khôi phục chính xác văn bản ban đầu. Nhưng Java `String` có thể chứa surrogate đơn lẻ không hợp lệ; một encoder UTF-8 mặc định có thể **thay thế** dữ liệu lỗi, khiến hành trình chuyển mã không còn hoàn nguyên. Nếu yêu cầu lưu toàn bộ **mọi** dãy UTF-16 code units có thể có trong Java (bao gồm cả chuỗi lỗi), phải chọn biểu diễn/protocol và chính sách lỗi hỗ trợ mục tiêu đó.

Base64 **không phải mã hóa bảo mật**; bất cứ ai có chuỗi Base64 đều có thể giải mã. Nó chỉ là dạng biểu diễn có thể khôi phục byte.

So sánh với:

- **Hash** là phép ánh xạ một chiều theo mục đích sử dụng, không phải công cụ khôi phục văn bản gốc.
- **Cắt chuỗi** loại bỏ nội dung nên không thể khôi phục khi không còn bản gốc.
- **Chuẩn hóa Unicode** có thể làm nhiều biểu diễn gốc khác nhau thành một dạng chuẩn; hữu ích cho so sánh nhưng không bảo đảm tái tạo chuỗi nguyên trạng.
- **Chuyển hoa/thường** có thể phục vụ tra cứu nhưng làm mất thông tin cách viết nếu không lưu riêng.

## 8. Ví dụ Java 17+ số 1: chuẩn hóa, xóa khoảng trắng và hợp nhất giá trị

Tạo tệp `StringTransformationDemo.java`:

```java
import java.text.Normalizer;
import java.util.Locale;

public class StringTransformationDemo {
    public static void main(String[] args) {
        String decomposed = "Cafe\u0301";
        String composed = "Caf\u00E9";

        String normalized = Normalizer.normalize(
                decomposed, Normalizer.Form.NFC);

        System.out.println("rawEquals = " + decomposed.equals(composed));
        System.out.println(
                "nfcEquals = " + normalized.equals(composed));
        System.out.println("rawLength = " + decomposed.length());
        System.out.println("nfcLength = " + normalized.length());

        String input = "  A  B  ";
        String stripped = input.strip();
        String collapsed = stripped.replace("  ", " ");

        System.out.println("inputLength = " + input.length());
        System.out.println("stripped = [" + stripped + "]");
        System.out.println("collapsed = [" + collapsed + "]");

        String display = "Java";
        String lookup = display.toLowerCase(Locale.ROOT);
        System.out.println("display = " + display);
        System.out.println("lookup = " + lookup);

        String compat = Normalizer.normalize(
                "\u2460", Normalizer.Form.NFKC);
        System.out.println("nfkcCircledOne = " + compat);
        System.out.println("nfkcSameText = " + "\u2460".equals(compat));
    }
}
```

Biên dịch và chạy:

```bash
javac StringTransformationDemo.java
java StringTransformationDemo
```

Kết quả:

```text
rawEquals = false
nfcEquals = true
rawLength = 5
nfcLength = 4
inputLength = 8
stripped = [A  B]
collapsed = [A B]
display = Java
lookup = java
nfkcCircledOne = 1
nfkcSameText = false
```

Giải thích:

- `Normalizer.Form.NFC` (được dùng qua `Normalizer.Form.NFC` trong mã) chuyển biểu diễn Unicode chính tắc về một dạng để so sánh. Nội dung hiển thị tương đương, nhưng độ dài theo UTF-16 giảm từ 5 xuống 4.
- `strip()` và `replace()` đều **không sửa** đối tượng `String input`; chúng trả kết quả không còn chứa một phần thông tin whitespace.
- Chuyển `Java` thành `java` giúp tạo khóa tra cứu theo quy tắc bỏ hoa/thường, nhưng không thể từ `java` biết bản gốc viết hoa chữ nào.
- NFKC chuyển `①` thành `1`. Vì hai biểu diễn có thể quan trọng khác nhau trong văn bản gốc, phép này cần lựa chọn có chủ đích.

## 9. Ví dụ Java 17+ số 2: phép hoàn nguyên và phép tạo va chạm

Tạo tệp `RoundTripVsLossDemo.java`:

```java
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

public class RoundTripVsLossDemo {
    public static void main(String[] args) {
        String original = "A\nB,\uD83D\uDE00";

        byte[] bytes = original.getBytes(StandardCharsets.UTF_8);
        String encoded = Base64.getEncoder().encodeToString(bytes);
        byte[] decodedBytes = Base64.getDecoder().decode(encoded);
        String restored = new String(decodedBytes, StandardCharsets.UTF_8);

        System.out.println(
                "sameBytes = " + Arrays.equals(bytes, decodedBytes));
        System.out.println("sameString = " + original.equals(restored));

        String first = "A,B";
        String second = "AB";
        String transformedFirst = first.replace(",", "");
        String transformedSecond = second.replace(",", "");

        System.out.println(
                "originalsEqual = " + first.equals(second));
        System.out.println(
                "transformedEqual = "
                + transformedFirst.equals(transformedSecond));
    }
}
```

Biên dịch và chạy:

```bash
javac RoundTripVsLossDemo.java
java RoundTripVsLossDemo
```

Kết quả:

```text
sameBytes = true
sameString = true
originalsEqual = false
transformedEqual = true
```

Ý nghĩa:

- Dãy byte UTF-8 được Base64 mã hóa/giải mã và giữ lại **đúng từng byte**, nên với chuỗi Unicode hợp lệ trong ví dụ, `String` được khôi phục chính xác.
- `"A,B"` và `"AB"` vốn là **hai đầu vào khác nhau**; sau khi xóa dấu phẩy, cả hai thành `"AB"`. Đây là **va chạm do biến đổi** (*collision*). Từ đầu ra `"AB"` không thể biết ban đầu có dấu phẩy hay không.

Nếu có khả năng nhiều đầu vào khác nhau cùng thành một đầu ra, đừng dùng đầu ra đó như một bản ghi dữ liệu gốc có khả năng khôi phục, trừ khi bạn **lưu thêm đủ thông tin để phân biệt**.

## 10. Thiết kế quy trình lưu trữ và tra cứu an toàn

Một cách làm phổ biến là lưu **giá trị gốc** và sinh thêm **giá trị chuẩn hóa phục vụ tác vụ**:

```text
Dữ liệu nhập ──────────────► original (lưu để hiển thị / kiểm toán)
           │
           └─► kiểm tra chính sách ─► chuẩn hóa có chủ đích
                                      │
                                      └─► searchKey (tìm kiếm / chỉ mục)
```

Ví dụ minh họa bằng một hàm tạo khóa tìm kiếm:

```java
import java.text.Normalizer;
import java.util.Locale;

public final class SearchKey {
    private SearchKey() {}

    public static String of(String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        return Normalizer.normalize(value, Normalizer.Form.NFC)
                .strip()
                .toLowerCase(Locale.ROOT);
    }
}
```

Tệp `SearchKey.java` trên biên dịch được bằng `javac SearchKey.java` và không có `main()`; nó là **lớp tiện ích**, được gọi từ chương trình khác.

Giải thích quy trình:

1. **Kiểm tra `null`** thay vì mặc định coi `null` là `""`: tùy nghiệp vụ, hai trạng thái này có thể khác nhau.
2. **Chuẩn hóa NFC** để biểu diễn Unicode tương đương chính tắc được xử lý nhất quán.
3. **`strip()`** bỏ khoảng trắng biên theo chính sách đã chọn. Không dùng nếu khoảng trắng biên có ý nghĩa.
4. **`toLowerCase(Locale.ROOT)`** tạo khóa không phân biệt một số khác biệt hoa/thường theo một quy tắc độc lập locale người chạy. Nó không phải giải pháp tổng quát cho mọi ngôn ngữ và mọi tiêu chuẩn so khớp Unicode.

Đây chỉ là **ví dụ quy tắc cho chức năng tìm kiếm**. Không tự động áp dụng chuỗi biến đổi này cho mật khẩu, chữ ký số, nội dung tài liệu chính xác hoặc định danh có quy tắc so khớp khác. Nếu lưu khóa để index, cần lưu/đối chiếu bản gốc và xác định rõ chính sách xử lý trường hợp **nhiều bản gốc cùng khóa**.

### Có nên áp dụng biến đổi trước hay sau khi kiểm tra dữ liệu?

Phụ thuộc điều kiện kiểm tra. Ví dụ, một trường bắt buộc “không blank” có thể kiểm tra giá trị nhập rồi quyết định có cho phép `strip()`. Nhưng nếu một định danh cấm khoảng trắng hoàn toàn, tự `strip()` trước rồi chấp nhận có thể che mất lỗi nhập.

**Không dùng chuẩn hóa để che lỗi dữ liệu chưa được định nghĩa.** Hãy viết rõ: cái gì được tự sửa, cái gì phải báo lỗi, cái gì được lưu nguyên trạng và cái gì chỉ dùng để tìm kiếm.

## 11. Các thuộc tính cần kiểm thử khi viết hàm biến đổi chuỗi

Bên cạnh ví dụ input/output, có thể kiểm tra:

| Thuộc tính | Câu hỏi kiểm thử |
| --- | --- |
| **Giữ nguyên bản gốc** | Sau khi gọi API, biến `String` và nội dung gốc còn nguyên không? |
| **Hoàn nguyên** | Nếu yêu cầu lossless, `decode(encode(s)).equals(s)` có đúng cho **toàn miền đầu vào được hỗ trợ** không? |
| **Không có va chạm không mong muốn** | Có hai đầu vào khác nhau `a`, `b` mà `T(a).equals(T(b))` không? Nếu có, nghiệp vụ có chấp nhận gộp chúng không? |
| **Tính idempotent** | Khi mong đợi chuẩn hóa ổn định, `T(T(s)).equals(T(s))` có đúng không? |
| **Unicode** | Có xử lý dấu kết hợp, emoji, ký tự ngoài BMP, khoảng trắng đặc biệt và locale không? |
| **Biên** | `null`, `""`, toàn whitespace, chuỗi rất dài, dấu phân cách lặp lại được xử lý theo chính sách nào? |
| **Chuẩn so sánh** | Cần so sánh nguyên văn, NFC, bỏ hoa/thường, so sánh theo locale hay so sánh theo một quy tắc nghiệp vụ khác? |

Tính **idempotent** chỉ nói rằng áp dụng nhiều lần cho cùng kết quả như áp dụng một lần; nó **không chứng minh phép biến đổi không mất dữ liệu**. Ví dụ `strip()` thường idempotent nhưng vẫn xóa khoảng trắng đầu/cuối. Tương tự, một phép biến đổi có thể đảo ngược được trên một miền dữ liệu hẹp, nhưng làm mất thông tin nếu mở rộng sang các ký tự khác.

## 12. Các lỗi tư duy và cách tránh

1. **“`String` immutable nên `replace()` không làm mất thông tin.”** Sai. Đối tượng gốc không bị thay đổi, nhưng kết quả có thể làm mất dữ liệu nếu không lưu đầu vào.
2. **“Cứ chuẩn hóa là bảo toàn mọi ý nghĩa.”** Sai. NFC/NFD là chuẩn hóa theo tương đương chính tắc, còn NFKC/NFKD có thể gộp những phân biệt mà ứng dụng muốn giữ.
3. **“Chữ thường chỉ là cách hiển thị khác của chữ hoa, luôn đảo ngược được.”** Sai. Nhiều cách viết hoa/thường có thể cho cùng dạng chuẩn hóa; quy tắc còn phụ thuộc locale.
4. **“Khoảng trắng luôn vô nghĩa.”** Sai. Khoảng trắng có thể là dữ liệu trong mật khẩu, nội dung mã nguồn, CSV, văn bản hoặc định dạng có quy tắc cố định.
5. **“Chỉ cần bỏ dấu là tìm kiếm tiếng Việt sẽ luôn đúng.”** Không chắc. Bỏ dấu gây va chạm giữa từ khác nhau; ngôn ngữ tự nhiên còn có quy tắc phát âm, phân đoạn và so khớp riêng.
6. **“`replaceAll(".", "")` chỉ xóa dấu chấm.”** Sai. Dấu `.` trong regex thường khớp gần như mọi ký tự; dùng `replace(".", "")` để thay dấu chấm nguyên văn.
7. **“Base64 là mã hóa bảo mật.”** Sai. Base64 là biểu diễn có thể giải mã dễ dàng, không bảo đảm bí mật.
8. **“`getBytes(UTF_8)` rồi tạo lại `String` sẽ luôn hoàn nguyên mọi dãy UTF-16.”** Không đúng với đầu vào chứa surrogate lỗi và cơ chế thay thế dữ liệu mã hóa mặc định.
9. **“Một phép biến đổi idempotent chắc chắn lossless.”** Sai. Cắt và strip có thể idempotent nhưng làm mất dữ liệu.
10. **“Một khóa tìm kiếm bằng nhau nghĩa là hai bản gốc giống hệt.”** Sai nếu quy tắc tạo khóa gộp nhiều đầu vào, chẳng hạn bỏ hoa/thường, bỏ dấu hoặc whitespace.

## 13. Bài tập tự kiểm tra

**Bài 1.** Kết quả của `" A ".strip()` là `"A"`. Phép biến đổi có giữ nguyên dãy ký tự không?

Không. Hai dấu cách đầu/cuối bị bỏ; nếu chỉ còn `"A"`, không thể biết đầu vào có khoảng trắng hay không. Trong một biểu mẫu xem whitespace biên là không đáng kể, nó có thể **giữ nguyên ý nghĩa theo quy tắc của biểu mẫu**, nhưng vẫn không giữ nguyên dữ liệu.

**Bài 2.** `"Cafe\u0301"` và `"Caf\u00E9"` có `String.equals()` là `true` không? Sau khi chuẩn hóa cả hai bằng NFC thì sao?

Trước chuẩn hóa, `equals()` là `false` do khác dãy UTF-16. Sau NFC, chúng so sánh bằng nhau theo `equals()`. Điều đó không có nghĩa bạn có thể từ chuỗi NFC tái tạo chính xác biểu diễn đầu vào trước chuẩn hóa.

**Bài 3.** `"ADMIN"` và `"admin"` cùng biến thành `"admin"` sau `toLowerCase(Locale.ROOT)`. Có nên dùng kết quả ấy làm dữ liệu hiển thị chính?

Chỉ khi sản phẩm **muốn mất phân biệt hoa/thường khi hiển thị**. Nếu cần giữ kiểu viết tên, nên lưu giá trị gốc và chỉ dùng chuỗi chuyển thường làm khóa tìm kiếm.

**Bài 4.** Hai mã `"AB-12"` và `"AB12"` cùng thành `"AB12"` sau khi xóa dấu gạch ngang. Có đủ thông tin để khôi phục mã gốc không?

Không. Có **va chạm** vì hai đầu vào khác nhau cùng một đầu ra. Việc xóa dấu chỉ an toàn cho định danh nếu hợp đồng hệ thống chấp nhận hai dạng là cùng mã và không cần khôi phục dạng nhập.

**Bài 5.** Một dữ liệu cần được lưu để in lại **từng ký tự y nguyên**, nhưng hệ thống cũng muốn tìm kiếm không phân biệt hoa/thường. Thiết kế nào phù hợp?

Lưu **bản gốc** phục vụ hiển thị/in lại; tạo riêng **khóa tìm kiếm được chuẩn hóa theo chính sách rõ ràng**. Khi kết quả tìm kiếm có nhiều bản gốc cùng khóa, phải có cách phân biệt chúng trong dữ liệu chính.

## Kết luận

Đừng đánh giá phép biến đổi chuỗi chỉ bằng việc đầu ra “trông có vẻ giống” đầu vào. Hãy xác định rõ:

- **Phải phục hồi đúng dữ liệu gốc?** Chọn biểu diễn/quy trình có khả năng hoàn nguyên trên toàn miền dữ liệu hỗ trợ, kiểm tra round-trip và giữ đúng encoding.
- **Chỉ cần tương đương cho một tác vụ?** Định nghĩa quy tắc tương đương đó (Unicode, hoa/thường, khoảng trắng, dấu phân cách, locale) rồi chuẩn hóa nhất quán.
- **Có thể làm mất thông tin?** Giữ bản gốc hoặc chủ động chấp nhận mất mát theo yêu cầu nghiệp vụ; kiểm thử va chạm và dữ liệu ở biên.

Bởi `String` là immutable, các biến đổi trả kết quả riêng mà không sửa đối tượng gốc. Nhưng **immutable không có nghĩa mọi kết quả là lossless**: tính đúng đắn nằm ở hợp đồng dữ liệu và mục tiêu của từng phép biến đổi.

## Tài liệu tham khảo

- [Java SE 21 API — `java.lang.String`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html): `strip`, `trim`, `toLowerCase`, `replace`, `replaceAll`, `substring`, `equals`.
- [Java SE 21 API — `java.text.Normalizer`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/text/Normalizer.html): NFC, NFD, NFKC, NFKD và Unicode normalization.
- [Java SE 21 API — `java.nio.charset.StandardCharsets`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/charset/StandardCharsets.html): các charset chuẩn, gồm UTF-8.
- [Java SE 21 API — `java.util.Base64`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Base64.html): biểu diễn/giải mã Base64 cho byte.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-transformation-008">Giữ chuỗi gốc khi cần đối chiếu hoặc hiển thị lại</a>

<details>
<summary>Xem chi tiết</summary>

# Giữ chuỗi gốc khi cần đối chiếu hoặc hiển thị lại

Khi biến đổi nội dung văn bản trong Java, việc giữ **chuỗi ban đầu** và tạo **giá trị đã biến đổi** riêng là một cách thiết kế quan trọng. Dữ liệu gốc có thể cần để hiển thị đúng như người dùng đã nhập, đối chiếu với phiên bản xử lý, giải thích lỗi xác thực, hoàn nguyên thao tác hoặc đáp ứng quy tắc nghiệp vụ. Bản đã biến đổi lại thuận tiện cho tìm kiếm, so sánh, phân loại hay chuẩn hóa.

**Ý chính:** String là *immutable* (bất biến). Các phương thức như strip(), trim(), replace(), toLowerCase(), toUpperCase() và phép nối tạo/trả về giá trị String kết quả, không thay đổi nội dung đối tượng String ban đầu. Tuy nhiên, nếu lập trình viên **gán đè biến tham chiếu** hoặc chỉ lưu bản đã chuẩn hóa, ứng dụng vẫn có thể **đánh mất cách truy cập dữ liệu gốc**.

## 1. Phân biệt nội dung String bất biến và biến đang trỏ đến nó

Xét đoạn mã Java sau:

~~~java
String original = "  Java  ";
String cleaned = original.strip();

System.out.println("original=[" + original + "]");
System.out.println("cleaned=[" + cleaned + "]");
~~~

Kết quả:

~~~text
original=[  Java  ]
cleaned=[Java]
~~~

Lời gọi strip() không xóa hai dấu cách ở đầu và cuối của đối tượng original. Nó trả về String biểu diễn nội dung đã bỏ whitespace ở hai đầu. Biến cleaned giữ kết quả, còn biến original vẫn giữ giá trị cũ.

Điều thường gây nhầm lẫn:

~~~java
String text = "  Java  ";
text = text.strip();

System.out.println("[" + text + "]"); // [Java]
~~~

Đối tượng String ban đầu vẫn bất biến, nhưng biến text giờ trỏ tới kết quả đã strip(). Nếu không còn tham chiếu hữu ích nào khác đến nội dung đầu vào, chương trình không thể lấy lại chính xác cách người dùng đã nhập từ biến text. **Bất biến không đồng nghĩa ứng dụng tự động giữ lịch sử.**

Có thể giữ lại bản gốc bằng một biến khác:

~~~java
String original = textFromUser;
String normalized = original == null ? null : original.strip();
~~~

Đây là **đoạn minh họa**, giả định biến textFromUser đã được khai báo. Trong Java, original và normalized chỉ là các tham chiếu tới những giá trị String; lưu hai biến không bắt buộc phải nhân bản đối tượng gốc.

## 2. Khi nào phải giữ dữ liệu gốc?

| Tình huống | Vai trò của bản gốc | Vai trò của bản biến đổi |
|---|---|---|
| Ô nhập tên hiển thị | Giữ cách viết, khoảng trắng hoặc dấu theo yêu cầu UI | Tạo khóa tìm kiếm hay phiên bản đã cắt whitespace |
| Tìm kiếm không phân biệt hoa/thường | Hiển thị nguyên văn văn bản | Dùng chuỗi chuẩn hóa nhất quán để tìm |
| Kiểm tra và báo lỗi dữ liệu | Cho biết đầu vào nào không hợp lệ | Đưa dữ liệu qua bước xử lý hoặc đối chiếu |
| Nhập dữ liệu từ tệp hay API | Phục vụ đối chiếu hoặc phát hiện bất đồng nếu chính sách cho phép lưu | Tạo định dạng nội bộ cần thiết |
| Chỉnh sửa nội dung có chức năng “Hoàn tác” | Lưu phiên bản trước khi biến đổi | Dùng bản hiện tại để hiển thị hoặc xử lý |
| Mã định danh hoặc giá trị theo quy tắc nghiêm ngặt | Bảo toàn byte/ký tự theo hợp đồng, nếu cần | Chuẩn hóa chỉ khi hệ thống cho phép |
| Phân tích sự khác biệt Unicode | Phân biệt biểu diễn người dùng đưa vào | Áp dụng chuẩn hóa Unicode để so sánh theo yêu cầu |

Giữ dữ liệu gốc **không có nghĩa lưu mọi chuỗi vô thời hạn**. Với mật khẩu, token, thông tin cá nhân hoặc dữ liệu nhạy cảm, lưu nhiều bản có thể làm tăng rủi ro. Hãy giữ bản gốc khi có mục đích, thời hạn và quyền truy cập phù hợp; đừng đưa dữ liệu nhạy cảm nguyên văn vào log để “dễ đối chiếu”.

## 3. Dùng tên biến hoặc record để thể hiện rõ hai mục đích

Đối với logic nhỏ, hai biến có tên rõ ràng thường là đủ:

~~~java
String rawTitle = "  Java Basics  ";
String displayTitle = rawTitle.strip();

System.out.println("raw=[" + rawTitle + "]");       // [  Java Basics  ]
System.out.println("display=[" + displayTitle + "]"); // [Java Basics]
~~~

Khi dữ liệu đi qua nhiều tầng ứng dụng, có thể dùng một **record** Java 17 để đưa các biểu diễn thành một giá trị có cấu trúc, ví dụ gồm raw, display và searchKey. Cách này giúp phương thức nhận dữ liệu biết nó đang thao tác với phiên bản nào.

| Trường | Ý nghĩa |
|---|---|
| original / raw | Dữ liệu đầu vào được giữ nguyên theo hợp đồng |
| display | Giá trị dùng để trình bày, có thể đã bỏ whitespace đầu/cuối nếu nghiệp vụ cho phép |
| searchKey | Giá trị chuẩn hóa dùng để tra cứu, thường không được trình bày thay bản gốc |
| validationMessage | Kết quả đánh giá đầu vào, tách riêng khỏi chuỗi đầu vào |

Không nhất thiết mọi chương trình đều phải giữ cả ba bản. Hãy xác định cần bản nào từ yêu cầu nghiệp vụ rồi đặt tên để tránh truyền nhầm searchKey vào nơi muốn hiển thị nguyên văn.

### Chương trình Java 17+ đầy đủ: original, display và searchKey

Lưu tệp **KeepOriginalStringDemo.java**:

~~~java
import java.util.Locale;

public class KeepOriginalStringDemo {
    record TextVersions(String original, String display, String searchKey) {}

    static TextVersions prepare(String input) {
        if (input == null) {
            return new TextVersions(null, null, null);
        }

        String display = input.strip();
        String searchKey = display.toLowerCase(Locale.ROOT);
        return new TextVersions(input, display, searchKey);
    }

    public static void main(String[] args) {
        String received = "  Hello  JAVA  ";
        TextVersions versions = prepare(received);

        System.out.println("original=[" + versions.original() + "]");
        System.out.println("display=[" + versions.display() + "]");
        System.out.println("searchKey=[" + versions.searchKey() + "]");

        System.out.println("originalLength=" + versions.original().length());
        System.out.println("displayLength=" + versions.display().length());
        System.out.println("originalUnchanged="
                + received.equals(versions.original()));

        TextVersions missing = prepare(null);
        System.out.println("missing=" + (missing.original() == null));
    }
}
~~~

Biên dịch và chạy với JDK 17+:

~~~bash
javac KeepOriginalStringDemo.java
java KeepOriginalStringDemo
~~~

Kết quả chính xác:

~~~text
original=[  Hello  JAVA  ]
display=[Hello  JAVA]
searchKey=[hello  java]
originalLength=15
displayLength=11
originalUnchanged=true
missing=true
~~~

Trong ví dụ:

1. original giữ đủ **hai dấu cách đầu, hai dấu cách giữa Hello và JAVA, hai dấu cách cuối**.
2. display chỉ bỏ whitespace hai đầu. strip() không tự xóa hai dấu cách nằm giữa.
3. searchKey chuyển nội dung display sang chữ thường theo Locale.ROOT, phù hợp khi ứng dụng đã **chọn** chính sách tra cứu không phụ thuộc locale.
4. Lời gọi prepare(null) không thực hiện phương thức instance trên null. Từ đây, ứng dụng có thể phân biệt “không có văn bản” với một String rỗng.
5. Việc giữ các giá trị không tự làm việc chuẩn hóa trở thành phép biến đổi khả nghịch. Nếu chỉ lưu searchKey thì không thể khôi phục chính xác cách viết hoa và khoảng trắng của original.

**Chú ý về Locale.ROOT:** nó giúp tránh phụ thuộc vào locale mặc định của máy khi tạo khóa kỹ thuật. Tuy nhiên, chuyển hoa/thường vẫn có thể thay đổi độ dài hoặc quan hệ giữa ký tự ở một số trường hợp Unicode; đối chiếu ngôn ngữ tự nhiên cần quy tắc riêng, không mặc định toLowerCase() là giải pháp cho mọi locale.

## 4. Không gán đè đầu vào khi cần đối chiếu

Đoạn sau dễ mất ngữ cảnh:

~~~java
String input = "  AbC  ";
input = input.strip();
input = input.toLowerCase(java.util.Locale.ROOT);
System.out.println(input); // abc
~~~

Từng phép biến đổi đều hợp lệ và không sửa đối tượng String cũ. Vấn đề là tên input không còn nói đúng **giá trị đang được giữ**: chuỗi ban đầu có khoảng trắng và chữ viết hoa đã không còn trong tham chiếu input.

Viết rõ từng bước giúp kiểm chứng:

~~~java
String rawInput = "  AbC  ";
String trimmedInput = rawInput.strip();
String lookupInput = trimmedInput.toLowerCase(java.util.Locale.ROOT);

System.out.println("raw=[" + rawInput + "]");         // [  AbC  ]
System.out.println("trimmed=[" + trimmedInput + "]"); // [AbC]
System.out.println("lookup=[" + lookupInput + "]");   // [abc]
~~~

Bản raw giữ nguyên, mỗi bản sau có mục đích rõ. Nếu kết quả tìm kiếm không đúng, có thể biết vấn đề đến từ nội dung gốc, quy tắc strip hay quy tắc lowercasing, miễn là hệ thống cho phép quan sát các bản tương ứng.

Một điểm khác cần lưu ý: **biến final không khiến thao tác strip trở thành phép biến đổi tại chỗ**. Bạn có thể dùng:

~~~java
final String original = "  AbC  ";
final String key = original.strip().toLowerCase(java.util.Locale.ROOT);
~~~

Cả hai biến giữ tham chiếu ổn định, còn đối tượng String đã bất biến từ đầu. final chủ yếu giúp giảm khả năng vô tình gán lại các biến đó.

## 5. Khi đối chiếu, cần xác định thế nào là “bằng nhau”

Không có một quy tắc so sánh duy nhất cho mọi tình huống. Hai phiên bản có thể bằng nhau theo **nội dung chính xác** nhưng khác nhau theo một quy tắc chuẩn hóa, hoặc ngược lại.

| Mục tiêu | Cách kiểm tra | Ý nghĩa |
|---|---|---|
| So sánh chính xác hai String | original.equals(other) khi receiver không-null | So sánh dãy đơn vị mã UTF-16, phân biệt hoa/thường và khoảng trắng |
| So sánh hai String có thể null | java.util.Objects.equals(a, b) | null/null được coi bằng nhau; null/chuỗi rỗng vẫn khác nhau |
| Bỏ whitespace ở hai đầu rồi so sánh | a.strip().equals(b.strip()), sau null-check | Chỉ phù hợp khi nghiệp vụ cho phép coi whitespace hai đầu là không đáng kể |
| So sánh sau chuẩn hóa Unicode | Normalizer.normalize(a, Form.NFC).equals(Normalizer.normalize(b, Form.NFC)) | Gộp các dạng biểu diễn Unicode tương đương theo NFC |
| So sánh không phân biệt hoa/thường | Chính sách case-folding/collation phù hợp | Không chỉ đơn giản là bỏ mọi khác biệt ngôn ngữ |

String.equals() **không tự gọi strip(), trim(), normalize() hoặc toLowerCase()**. Nếu muốn đối chiếu theo quy tắc chuẩn hóa, hãy thực hiện bước đó một cách có chủ đích, giữ bản gốc khi cần.

### Ví dụ Unicode: văn bản nhìn giống nhau nhưng mã khác

`"Caf\u00E9"` có ký tự `é` dựng sẵn, còn `"Cafe\u0301"` gồm `e` và dấu sắc kết hợp. `String.equals()` phân biệt hai dãy mã; nếu nghiệp vụ muốn xem chúng tương đương theo chuẩn chính tắc, hãy chuẩn hóa nhất quán về NFC; trong ví dụ, `composed` vốn đã ở dạng NFC:

~~~java
String original = "Cafe\u0301";
String composed = "Caf\u00E9";
String normalized = java.text.Normalizer.normalize(
        original, java.text.Normalizer.Form.NFC);
System.out.println(original.equals(composed));   // false
System.out.println(normalized.equals(composed)); // true
System.out.println(original.length());           // 5
System.out.println(normalized.length());         // 4
~~~

`Normalizer` trả về giá trị mới và không sửa `original`. Khi chỉ lưu bản NFC, không thể tái tạo chắc chắn biểu diễn người dùng đã nhập. Ví dụ này minh họa trực tiếp lý do giữ cả **raw** và bản **so sánh**; bài về tính bảo toàn dữ liệu trước đó giải thích chi tiết tính đảo ngược và va chạm.

## 6. Lưu ý với trim(), strip(), replace() và biểu diễn để hiển thị

Các phép biến đổi thường dùng có thể làm mất thông tin có ý nghĩa với việc hiển thị lại:

- **strip()** loại whitespace đầu/cuối theo Character.isWhitespace của Java. Với chuỗi chỉ có whitespace thuộc tập này, có thể trả về String rỗng.
- **trim()** loại đơn vị mã ở hai đầu có giá trị <= U+0020; tập bị loại khác strip().
- **replace()** thay nội dung theo quy tắc đã chỉ định; bản kết quả không cho biết chính xác phần nào đã được thay khi không giữ thông tin ban đầu.
- **toLowerCase()/toUpperCase()** có thể thay đổi cách viết hoa, thậm chí số lượng đơn vị mã trong một số trường hợp.
- **Normalizer.normalize()** có thể thay đổi biểu diễn Unicode trong khi hình thức hiển thị tương tự.

Ví dụ khi người dùng nhập mã định danh **" AB-01 "**, nếu hệ thống lưu **"AB-01"** sau strip() thì sau đó không thể chắc chắn người dùng đã nhập một hay nhiều dấu cách ở hai đầu. Điều này có thể không quan trọng cho tìm kiếm, nhưng có thể quan trọng khi điều tra lỗi nhập, hiển thị bản nháp hoặc thực hiện chức năng hoàn tác.

### Hiển thị lại bản gốc có nghĩa là giữ nguyên nội dung, không bỏ qua an toàn đầu ra

Khi trình bày văn bản do người dùng cung cấp trên HTML, terminal, CSV hoặc log, mỗi môi trường có quy tắc escape riêng. **Giữ bản gốc không có nghĩa ghép thẳng nội dung chưa được escape vào HTML hoặc câu lệnh SQL.** Bạn có thể bảo toàn original trong mô hình dữ liệu, rồi dùng cơ chế render an toàn của giao diện, tham số hóa câu lệnh SQL hoặc công cụ xuất dữ liệu đúng định dạng.

Tương tự, không tự động ghi nguyên văn token, mật khẩu hay dữ liệu cá nhân vào log chỉ để so sánh. Hãy kiểm tra yêu cầu lưu trữ và chỉ ghi thông tin thực sự cần thiết.

## 7. StringBuilder và bộ đệm mutable: khác với việc giữ String gốc

String bất biến cho phép bạn giữ hai tham chiếu đến cùng đối tượng mà không sợ một lời gọi replace()/strip() làm đối tượng ban đầu thay đổi:

~~~java
String a = "Java";
String b = a;

a = a.concat(" 17");

System.out.println(a); // Java 17
System.out.println(b); // Java
~~~

Với **StringBuilder**, hai biến có thể trỏ đến cùng một đối tượng mutable:

~~~java
StringBuilder first = new StringBuilder("Java");
StringBuilder second = first;

first.append(" 17");

System.out.println(first);  // Java 17
System.out.println(second); // Java 17
~~~

Vì vậy, giữ thêm một **tham chiếu** đến StringBuilder **không phải** là giữ snapshot nội dung. Nếu đang xây dựng bằng StringBuilder và cần giữ một phiên bản ổn định, hãy gọi toString() tại thời điểm cần chụp lại:

~~~java
StringBuilder draft = new StringBuilder("version 1");
String snapshot = draft.toString();

draft.replace(0, draft.length(), "version 2");

System.out.println(snapshot); // version 1
System.out.println(draft);    // version 2
~~~

String snapshot giữ giá trị bất biến; những thay đổi sau đó trong builder không làm snapshot đổi theo.

Tuy nhiên, **không giả định rằng bản String luôn có thể tái tạo nguyên trạng toàn bộ đầu vào dạng byte**. Nếu dữ liệu nguồn là tệp UTF-8, việc giải mã, chuẩn hóa newline hoặc xử lý ký tự không hợp lệ có thể khiến một String không còn đủ để khôi phục byte ban đầu. Nếu yêu cầu lưu vết ở cấp byte, phải bảo toàn byte nguồn theo cơ chế phù hợp, độc lập với bản String được dùng để thao tác văn bản.

## 8. Quy trình xử lý đầu vào có thể kiểm tra lại

Một luồng xử lý thường dễ bảo trì khi chia rõ các bước:

1. **Nhận đầu vào:** xác định dữ liệu có thể null hay không, có cần giữ nguyên số dấu cách, newline và biểu diễn Unicode hay không.
2. **Giữ raw:** chỉ lưu đúng phạm vi yêu cầu, với chính sách bảo mật và thời gian giữ phù hợp.
3. **Biến đổi vào biến mới:** gọi strip(), replace(), normalize() hoặc chuyển hoa/thường tùy quy tắc, không gán đè biến raw nếu còn cần đối chiếu.
4. **Kiểm tra:** xác thực bản raw, bản biến đổi hoặc cả hai tùy hợp đồng nghiệp vụ; chẳng hạn mã đăng nhập có thể đòi hỏi cú pháp gốc rất nghiêm ngặt.
5. **Sử dụng đúng phiên bản:** raw để đối chiếu, display để hiển thị, searchKey để tìm kiếm. Đừng vô tình thay searchKey cho raw trong chức năng “hiển thị lại”.
6. **Làm rõ quyền lưu trữ:** tránh giữ nhiều bản chứa dữ liệu nhạy cảm khi không cần.

Ví dụ hàm nhận đầu vào có thể null:

~~~java
record DisplayValue(String original, String visible) {}

static DisplayValue prepareDisplay(String value) {
    if (value == null) {
        return new DisplayValue(null, null);
    }
    return new DisplayValue(value, value.strip());
}
~~~

Đây là **đoạn mã minh họa**, cần đặt trong một lớp Java. Nó phân biệt null với chuỗi rỗng và giữ được cả bản raw lẫn bản đã strip. Một record riêng giúp tránh đặt hai phiên bản vào hai biến dễ bị truyền nhầm trong nhiều phương thức.

Một thiết kế khác là giữ raw trong đối tượng bất biến và **tính ra bản đã chuẩn hóa khi cần**, thay vì lưu tất cả phiên bản. Lựa chọn dựa trên tần suất truy cập, chi phí chuẩn hóa, tính ổn định của chính sách chuẩn hóa và yêu cầu lưu trữ. Quan trọng là giá trị raw vẫn có thể truy cập khi nghiệp vụ thật sự cần.

## 9. Những lỗi thường gặp

1. **Tưởng String được sửa tại chỗ:** gọi raw.strip() hoặc raw.replace() không làm raw đổi; phải dùng giá trị trả về cho bản đã biến đổi.
2. **Gán đè biến trước khi lưu raw:** text = text.strip() hợp lệ nhưng mất tham chiếu ban đầu qua text; nếu cần so sánh hãy giữ rawInput và normalizedInput riêng.
3. **Cho rằng hai tham chiếu String khác nhau là không bằng nhau:** sử dụng equals() để so nội dung thay vì ==. Hai bản có nội dung giống nhau có thể là cùng hoặc khác đối tượng.
4. **Tự động strip mọi đầu vào:** khoảng trắng có thể là dữ liệu cần bảo toàn, đặc biệt với mật khẩu, văn bản tự do hoặc chuỗi định dạng.
5. **Dùng bản searchKey để hiển thị:** chuẩn hóa để tìm kiếm có thể làm mất hoa/thường, dấu hoặc định dạng ban đầu; hiển thị từ bản gốc/bản display đúng chính sách.
6. **Tưởng normalize Unicode luôn giữ nguyên length():** NFC có thể gộp một chuỗi code point thành biểu diễn khác; ví dụ "Cafe\u0301" và "Café".
7. **Bỏ qua null:** raw.strip() khi raw null gây NullPointerException. Kiểm tra null trước mọi lời gọi instance.
8. **Giữ tham chiếu StringBuilder để chụp nội dung:** builder là mutable; muốn snapshot văn bản hãy gọi toString().
9. **Nhầm giữ String với giữ byte nguồn:** String là biểu diễn văn bản sau giải mã; yêu cầu khôi phục tệp chính xác byte cần giữ byte gốc.
10. **Tự lưu tất cả raw input vào log:** lưu bản gốc có thể tăng nguy cơ lộ dữ liệu nhạy cảm; chỉ lưu trong thời hạn và phạm vi thật sự cần.
11. **Giả định mọi trường hợp tìm kiếm đều dùng cùng quy tắc chuẩn hóa:** Locale.ROOT và NFC là các lựa chọn thường gặp cho khóa kỹ thuật, nhưng không thay thế chính sách collation/ngôn ngữ phù hợp.
12. **Dùng bản raw và normalized không nhất quán trong so sánh:** khi chính sách yêu cầu chuẩn hóa, cả hai phía phải theo cùng một quy trình rõ ràng.

## 10. Câu hỏi tự kiểm tra

Với đoạn mã:

~~~java
String raw = "  JaVa  ";
String display = raw.strip();
String key = display.toLowerCase(java.util.Locale.ROOT);
~~~

Hãy dự đoán:

1. raw và display lần lượt có nội dung gì?
2. raw.length() và display.length() bằng bao nhiêu?
3. raw.equals(display) trả về gì? Vì sao?
4. key có nội dung gì? Nếu chỉ lưu key, có thể biết chắc cách viết hoa của raw không?
5. Nếu sau đó gán raw = key, đối tượng chứa "  JaVa  " có bị sửa không?
6. Vì sao muốn giữ snapshot StringBuilder phải gọi toString(), không chỉ tạo biến tham chiếu thứ hai?

**Đáp án:**

1. raw vẫn là **"  JaVa  "**, display là **"JaVa"**.
2. raw có độ dài **8** (hai dấu cách + bốn chữ + hai dấu cách); display có độ dài **4**.
3. **false**, vì nội dung hai chuỗi khác nhau.
4. key là **"java"**; chỉ từ đó không khôi phục chắc chắn cách viết hoa và khoảng trắng gốc.
5. **Không**. Lệnh gán chỉ khiến biến raw trỏ sang String khác, không thay đổi nội dung đối tượng cũ. Tuy nhiên nếu không còn tham chiếu nào khác, ứng dụng có thể không truy cập lại được bản gốc.
6. Hai biến tham chiếu cùng StringBuilder vẫn quan sát các thay đổi trên một đối tượng mutable; toString() tạo giá trị String bất biến tại thời điểm chụp.

**Ghi nhớ:** Khi văn bản được biến đổi để phục vụ tìm kiếm, chuẩn hóa hoặc hiển thị, hãy **giữ chuỗi đầu vào theo đúng nhu cầu nghiệp vụ và tạo bản kết quả riêng**. Tính bất biến của String bảo vệ nội dung từng giá trị; còn **việc lưu được bản gốc, chọn đúng phiên bản khi sử dụng và quản lý dữ liệu nhạy cảm** là trách nhiệm của thiết kế chương trình.


</details>

- [Quay lại đầu trang](#back-to-top)
