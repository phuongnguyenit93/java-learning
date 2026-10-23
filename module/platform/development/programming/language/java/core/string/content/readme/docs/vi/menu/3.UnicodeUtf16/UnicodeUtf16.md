<a id="back-to-top"></a>

# Unicode và UTF-16

## Menu
- [Phân biệt code unit, code point và cụm ký tự hiển thị](#unicode-utf16-001)
- [String dùng chỉ số theo đơn vị char của UTF-16](#unicode-utf16-002)
- [BMP, ký tự bổ sung và surrogate pair](#unicode-utf16-003)
- [Độ dài của tiếng Việt, dấu kết hợp và emoji](#unicode-utf16-004)
- [String có thể chứa surrogate không ghép cặp](#unicode-utf16-005)
- [Character.toChars, charCount và các thao tác trên code point](#unicode-utf16-006)
- [UTF-16 của mô hình String khác với charset của tệp](#unicode-utf16-007)
- [Vì sao số char không luôn bằng số ký tự người dùng nhìn thấy](#unicode-utf16-008)

## <a id="unicode-utf16-001">Phân biệt code unit, code point và cụm ký tự hiển thị</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt code unit, code point và cụm ký tự hiển thị

Trong Java, từ “ký tự” có thể chỉ ba cấp độ khác nhau: **đơn vị mã UTF-16**, **Unicode code point** và **cụm ký tự hiển thị (grapheme cluster)**. Với chữ `A`, cả ba số đếm đều bằng 1. Với emoji, chữ có dấu kết hợp hoặc cờ, chúng có thể khác nhau. Bài này xây dựng bản đồ khái niệm để chọn đúng đơn vị ngay từ đầu; các bài sau sẽ đi sâu vào chỉ số `String`, cặp surrogate, tiếng Việt và thao tác trên code point.

## 1. Ba tầng của cùng một văn bản

| Tầng | Chính xác là gì? | Công cụ tương ứng trong Java | Khi nào cần? |
| --- | --- | --- | --- |
| **UTF-16 code unit** | Một giá trị 16 bit trong dãy mã UTF-16 | `char`, `String.length()`, `charAt()`, chỉ số `substring()` | Giao tiếp với API chuỗi hoặc offset |
| **Unicode code point** | Một giá trị mã từ `U+0000` đến `U+10FFFF` | `int`, `codePointAt()`, `codePoints()`, `codePointCount()` | Phân loại, duyệt hoặc xử lý từng điểm mã |
| **Extended grapheme cluster** | Một hoặc nhiều code point được nhóm theo quy tắc phân đoạn Unicode | Regex `\\X` và `Matcher` | Cắt văn bản theo cụm mà người dùng thường cảm nhận như một ký tự |

Một `char` là **một code unit**, không bảo đảm biểu diễn trọn một code point. Một code point ngoài BMP cần **hai code unit** liên tiếp (surrogate pair). Một grapheme cluster có thể bao gồm **nhiều code point**, dù mỗi code point đã được đọc đúng. Ngược lại, số grapheme cũng không phải số glyph hoặc độ rộng hiển thị tính bằng pixel: font và hệ thống dựng chữ quyết định hình dạng cuối.

Dữ liệu Unicode có các code point dành riêng cho surrogate (`U+D800`–`U+DFFF`), nhưng từng giá trị đó không phải Unicode scalar value. Java vẫn có thể chứa surrogate đứng lẻ trong `String`; do đó phép đếm code point của Java không phải phép xác thực văn bản Unicode well-formed.

## 2. Nhìn qua một số trường hợp

| Dãy văn bản | UTF-16 units | Code points | Extended grapheme clusters | Nguyên nhân |
| --- | ---: | ---: | ---: | --- |
| `A` | 1 | 1 | 1 | Một mã BMP thông thường |
| `😊` (`U+1F60A`) | 2 | 1 | 1 | Một code point ngoài BMP có cặp surrogate |
| `é` (`U+00E9`) | 1 | 1 | 1 | Ký tự dựng sẵn |
| `e\u0301` | 2 | 2 | 1 | Chữ cơ sở và dấu kết hợp |
| `🇻🇳` | 4 | 2 | 1 | Hai regional indicator |
| `👩‍💻` | 5 | 3 | 1 | Hai emoji nối bằng `U+200D` (ZWJ) |

Bảng mô tả những **chuỗi mẫu cụ thể** theo quy tắc extended grapheme cluster; số liệu không phải công thức cho mọi emoji. Emoji mới hoặc quy tắc phân đoạn khác phiên bản có thể cần thử trên JDK thực tế.

Hai chuỗi `"\u00E9"` và `"e\u0301"` có thể trông như cùng chữ “é” nhưng khác dãy code unit. `String.equals()` không tự chuẩn hóa Unicode. Nếu nghiệp vụ cần so sánh theo tương đương chuẩn, cân nhắc `java.text.Normalizer`, ví dụ NFC, với chính sách nhất quán cho đầu vào. Chuẩn hóa không biến mọi grapheme cluster thành một code point.

## 3. Hợp đồng của API Java trước khi chọn cách đếm

- `String.length()` trả **số code unit UTF-16**; các vị trí `charAt(index)` và `substring(begin, end)` cũng tính theo UTF-16.
- `codePointCount(0, s.length())` đếm các code point mà API Java nhận diện khi duyệt dãy UTF-16; surrogate lẻ vẫn được đếm như đơn vị riêng.
- `s.codePoints()` cho luồng `int` theo code point, ghép surrogate pair hợp lệ; `s.chars()` cho luồng các code unit.
- `Pattern.compile("\\X")` khớp từng extended grapheme cluster trong regex Java; `Matcher.start()` và `Matcher.end()` trả lại **offset UTF-16**, có thể dùng làm biên `substring`.
- `s.getBytes(StandardCharsets.UTF_8).length` là số **byte sau khi mã hóa**, không phải bất kỳ một trong ba phép đếm ở trên.

Phân biệt “độ dài” và “vị trí”: `codePointAt(2)` có đối số là **offset UTF-16**, không có nghĩa “code point thứ ba”. Nếu cần chuyển thứ tự code point thành offset UTF-16, dùng `offsetByCodePoints`. Nếu cần cắt theo ký tự hiển thị, ranh giới code point chưa đủ: phải phân đoạn grapheme.

## 4. Chương trình đối chiếu ba đơn vị

Lưu thành `UnicodeLevelsOverview.java` (JDK 17+):

~~~java
import java.util.regex.Pattern;

public class UnicodeLevelsOverview {
    private static final Pattern CLUSTERS = Pattern.compile("\\X");

    private static void show(String label, String text) {
        int units = text.length();
        int points = text.codePointCount(0, units);
        long clusters = CLUSTERS.matcher(text).results().count();
        System.out.printf("%s: units=%d, points=%d, clusters=%d%n",
                label, units, points, clusters);
    }

    public static void main(String[] args) {
        show("A", "A");
        show("smile", "\uD83D\uDE0A");
        show("composed", "\u00E9");
        show("decomposed", "e\u0301");
        show("flag", "\uD83C\uDDFB\uD83C\uDDF3");
    }
}
~~~

~~~bash
javac -encoding UTF-8 UnicodeLevelsOverview.java
java UnicodeLevelsOverview
~~~

Kết quả mong đợi với quy tắc phân đoạn hỗ trợ các chuỗi mẫu:

~~~text
A: units=1, points=1, clusters=1
smile: units=2, points=1, clusters=1
composed: units=1, points=1, clusters=1
decomposed: units=2, points=2, clusters=1
flag: units=4, points=2, clusters=1
~~~

Ở `smile`, khác biệt xảy ra **giữa code unit và code point**. Ở `decomposed`, hai code point được nhóm thành **một grapheme cluster**. Ở `flag`, cả hai cơ chế cùng xuất hiện: hai code point ngoài BMP thành bốn UTF-16 units, nhưng quy tắc phân đoạn nhóm thành một cụm.

## 5. Chọn đơn vị từ yêu cầu, không từ hình dáng chữ

Nếu API yêu cầu offset để gọi `substring`, dùng **UTF-16 offset**. Nếu bài toán cần kiểm tra từng giá trị Unicode, dùng **code point** với lưu ý về surrogate lẻ. Nếu giới hạn trường nhập “tối đa 20 ký tự người dùng cảm nhận”, dùng **grapheme clusters**, kèm quy tắc Unicode/version nhất quán ở client và server. Nếu lưu payload giới hạn 255 byte, phải **mã hóa đúng charset rồi đếm byte**. Độ rộng ô trên màn hình vẫn cần công cụ đo chữ của UI.

Một ứng dụng có thể áp dụng đồng thời số grapheme tối đa và kích thước byte tối đa. Dùng `length()` cho cả hai sẽ sai đối với nhiều văn bản đa ngôn ngữ.

### Tự kiểm tra

Với `String s = "A\uD83D\uDE0Ae\u0301";`, hãy dự đoán ba số đếm. Đáp án: **5 UTF-16 units**, **4 code points**, **3 grapheme clusters** (A, 😊, e + dấu sắc). `s.substring(1, 2)` chỉ lấy high surrogate của emoji và cắt hỏng cặp. `s.substring(1, 3)` lấy nguyên emoji, nhưng một lát cắt theo code point vẫn có thể chia đôi chữ với dấu kết hợp. Việc chọn ranh giới luôn phụ thuộc đơn vị mà yêu cầu thật sự nói đến.

Các bài kế tiếp lần lượt làm rõ chỉ số UTF-16, BMP và surrogate pair, normalization tiếng Việt, dữ liệu lỗi, API `Character`, charset tệp và chính sách thao tác theo ký tự hiển thị.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="unicode-utf16-002">String dùng chỉ số theo đơn vị char của UTF-16</a>

<details>
<summary>Xem chi tiết</summary>

# `String` dùng chỉ số theo đơn vị `char` của UTF-16

## 1. Mục tiêu và ý chính

Trong Java, các chỉ số của `String` như tham số của `charAt`, `substring`, `indexOf` và `codePointAt` được tính theo **UTF-16 code unit** (đơn vị `char` 16 bit), không mặc nhiên theo Unicode code point hoặc ký tự mà người đọc nhìn thấy.

Ví dụ, chuỗi `"A🚀B"` có 3 Unicode code point, nhưng `length() == 4`, vì ký tự `🚀` cần hai code unit UTF-16. Do đó, vị trí của `B` trong chuỗi là **3**, không phải **2**.

Bài học giúp bạn đọc và thao tác đúng với chỉ số Java, tránh cắt đôi surrogate pair và chuyển đổi vị trí khi muốn xử lý theo code point.

## 2. Ba khái niệm cần phân biệt

- **UTF-16 code unit:** một đơn vị 16 bit. Kiểu `char` của Java chứa đúng một code unit, giá trị từ `0x0000` đến `0xFFFF`.
- **Unicode code point:** giá trị nguyên dùng để nhận diện một vị trí mã Unicode, ví dụ `U+0041` (`A`) và `U+1F680` (`🚀`). Một code point thuộc dải bổ sung `U+10000..U+10FFFF` được biểu diễn bằng **hai** code unit UTF-16 gọi là *surrogate pair*.
- **Grapheme cluster:** cụm ký tự người đọc thường xem như một ký tự hiển thị. Một cluster có thể chứa nhiều code point, ví dụ chữ cái + dấu kết hợp hoặc emoji ghép bằng dấu nối.

| Văn bản | Số code unit UTF-16 (`length`) | Số code point | Ý nghĩa |
| --- | ---: | ---: | --- |
| `"ABC"` | 3 | 3 | Mỗi ký tự ASCII chiếm một `char`. |
| `"A🚀B"` | 4 | 3 | `🚀` cần hai `char`. |
| `"é"` (U+00E9) | 1 | 1 | Dấu có sẵn trong một code point. |
| `"e\u0301"` (U+0065 + U+0301) | 2 | 2 | Chữ `e` và dấu sắc kết hợp; có thể nhìn giống `é`. |

Java lưu chuỗi bất biến và cung cấp API thao tác dựa trên **các đơn vị UTF-16**. Chú ý: cách lưu vật lý nội bộ của JVM có thể được tối ưu (ví dụ Compact Strings), nhưng **hành vi chỉ số công khai vẫn theo UTF-16**.

## 3. Sơ đồ chỉ số: `"A🚀B"`

Mã Unicode của rocket là `U+1F680`, được mã hóa bằng cặp surrogate `\uD83D` và `\uDE80`:

| Chỉ số `char` (UTF-16) | Giá trị `charAt(index)` | Ý nghĩa |
| ---: | --- | --- |
| 0 | `U+0041` | `A` |
| 1 | `U+D83D` | *High surrogate* của `🚀` |
| 2 | `U+DE80` | *Low surrogate* của `🚀` |
| 3 | `U+0042` | `B` |

Ranh giới để cắt chuỗi theo các code point hoàn chỉnh là `0 | A | 1 | 🚀 | 3 | B | 4`. **Vị trí 2 nằm giữa surrogate pair** và không phải ranh giới an toàn để chia rocket thành hai chuỗi.

Từ ví dụ này:
- `text.length()` → `4`: số code unit UTF-16.
- `text.codePointCount(0, text.length())` → `3`: số code point.
- `text.charAt(1)` → một `char` có giá trị `U+D83D`; **không phải** toàn bộ rocket.
- `text.codePointAt(1)` → `U+1F680`, vì chỉ số 1 bắt đầu một surrogate pair hợp lệ.
- `text.codePointAt(2)` → `U+DE80`: bắt đầu ở low surrogate nên **không ghép ngược** với phần tử trước.
- `text.indexOf("B")` → `3`: chỉ số UTF-16 của `B`.
- `text.substring(1, 3)` → `"🚀"`: vùng `[1, 3)` lấy trọn hai code unit.

Nếu sử dụng `substring(1, 2)`, kết quả chỉ có high surrogate. Java vẫn cho phép tạo chuỗi chứa surrogate đơn lẻ, nhưng văn bản này không biểu diễn một Unicode scalar value hoàn chỉnh; cách hiển thị, mã hóa khi xuất ra ngoài có thể không như mong muốn.

## 4. Các API dùng chỉ số UTF-16

| API | Đối số hoặc kết quả theo đơn vị nào? | Ví dụ với `"A🚀B"` |
| --- | --- | --- |
| `length()` | Trả về **số `char`** UTF-16. | `4` |
| `charAt(index)` | `index` là vị trí `char` trong `[0, length())`. | `charAt(1) == '\uD83D'` |
| `substring(beginIndex, endIndex)` | Hai đầu mút là vị trí UTF-16, đầu cuối loại trừ. | `substring(1, 3)` là `🚀` |
| `subSequence(start, end)` | Hai đầu mút là vị trí UTF-16. | `subSequence(1, 3)` là `🚀` |
| `indexOf(String)` và `lastIndexOf(String)` | Trả về vị trí **`char`** của chuỗi khớp, hoặc `-1`. | `indexOf("B") == 3` |
| `indexOf(int codePoint)` | Đối số là code point nhưng **kết quả là chỉ số UTF-16**. | `indexOf(0x1F680) == 1` |
| `codePointAt(index)` | `index` là vị trí UTF-16; đọc code point bắt đầu tại đó khi có cặp hợp lệ. | `codePointAt(1) == 0x1F680` |
| `codePointBefore(index)` | `index` là ranh giới UTF-16; đọc code point ngay trước đó. | `codePointBefore(3) == 0x1F680` |
| `codePointCount(begin, end)` | Phạm vi `[begin, end)` được chỉ định **theo UTF-16**; kết quả là số code point. | `codePointCount(0, 4) == 3` |
| `offsetByCodePoints(index, offset)` | Nhận `index` UTF-16 và khoảng dịch **số code point**; trả về **chỉ số UTF-16** mới. | `offsetByCodePoints(0, 2) == 3` |

Một số API có overload nhận `fromIndex`, như `indexOf(ch, fromIndex)` hoặc `lastIndexOf(str, fromIndex)`. `fromIndex` cũng là chỉ số UTF-16. Các API thao tác vùng như `StringBuilder.delete(start, end)` và các vị trí được báo về từ `java.util.regex.Matcher.start()/end()` cũng thường dùng chỉ số UTF-16.

Không nên mặc định rằng một chỉ số bất kỳ là ranh giới code point. `offsetByCodePoints` dịch qua các code point tính từ vị trí bạn cung cấp; nếu vị trí đầu đã nằm *giữa* surrogate pair, việc dịch chuyển đó không tự sửa đoạn chuỗi đã bị tách đôi.

## 5. Chương trình Java 17+ minh họa

Lưu file `Utf16IndexDemo.java` với nội dung:

```java
import java.util.Arrays;

public class Utf16IndexDemo {
    public static void main(String[] args) {
        String text = "A🚀B";

        System.out.println("length = " + text.length());
        System.out.println("code points = "
                + text.codePointCount(0, text.length()));

        for (int i = 0; i < text.length(); i++) {
            System.out.printf("charAt(%d) = U+%04X%n",
                    i, (int) text.charAt(i));
        }

        System.out.printf("codePointAt(1) = U+%X%n",
                text.codePointAt(1));
        System.out.printf("codePointAt(2) = U+%X%n",
                text.codePointAt(2));
        System.out.println("indexOf(B) = " + text.indexOf("B"));
        System.out.println("indexOf(rocket) = " + text.indexOf("🚀"));

        int begin = text.offsetByCodePoints(0, 1);
        int end = text.offsetByCodePoints(begin, 1);
        System.out.println("slice = " + text.substring(begin, end));
        System.out.println("slice range = " + begin + ".." + end);

        String brokenHalf = text.substring(1, 2);
        System.out.println("high surrogate only = "
                + Character.isHighSurrogate(brokenHalf.charAt(0)));
        System.out.println("codePoints array = "
                + Arrays.toString(text.codePoints().toArray()));
    }
}
```

Biên dịch và chạy trong môi trường hiển thị UTF-8:

```bash
javac -encoding UTF-8 Utf16IndexDemo.java
java Utf16IndexDemo
```

Kết quả mong đợi:

```text
length = 4
code points = 3
charAt(0) = U+0041
charAt(1) = U+D83D
charAt(2) = U+DE80
charAt(3) = U+0042
codePointAt(1) = U+1F680
codePointAt(2) = U+DE80
indexOf(B) = 3
indexOf(rocket) = 1
slice = 🚀
slice range = 1..3
high surrogate only = true
codePoints array = [65, 128640, 66]
```

**Điều cần quan sát:** cặp `charAt(1)` và `charAt(2)` là hai nửa của một code point; `substring(1, 3)` hợp lệ vì chứa cả hai, còn `substring(1, 2)` cắt đôi cặp. `indexOf("🚀")` và `indexOf(0x1F680)` đều chỉ tới vị trí UTF-16 **1**, còn `indexOf("B")` trả **3**.

## 6. Cắt chuỗi theo code point mà không cắt đôi surrogate pair

Khi yêu cầu là “lấy `count` code point, bắt đầu từ code point thứ `from`”, **không** gọi trực tiếp `substring(from, from + count)`. Trước tiên cần đổi chỉ số tính theo code point thành chỉ số UTF-16.

Phương thức hỗ trợ sau là **một phần của lớp Java** (ví dụ đặt bên trong `Utf16IndexDemo`), chưa phải file chạy độc lập:

```java
static String sliceByCodePoints(String text, int from, int count) {
    java.util.Objects.requireNonNull(text, "text");

    int total = text.codePointCount(0, text.length());
    java.util.Objects.checkFromIndexSize(from, count, total);

    int beginUtf16 = text.offsetByCodePoints(0, from);
    int endUtf16 = text.offsetByCodePoints(beginUtf16, count);
    return text.substring(beginUtf16, endUtf16);
}
```

Ví dụ gọi bên trong `main`:

```java
String text = "A🚀B";
System.out.println(sliceByCodePoints(text, 1, 1)); // 🚀
System.out.println(sliceByCodePoints(text, 1, 2)); // 🚀B
System.out.println(sliceByCodePoints(text, 3, 0).isEmpty()); // true
```

Giải thích:
1. `codePointCount` tính tổng code point, dùng để xác nhận đoạn `[from, from + count)` nằm trong phạm vi hợp lệ.
2. `offsetByCodePoints(0, from)` tìm ranh giới UTF-16 sau `from` code point đầu tiên.
3. `offsetByCodePoints(beginUtf16, count)` tìm ranh giới sau `count` code point nữa.
4. `substring(beginUtf16, endUtf16)` cắt bằng **đúng loại chỉ số** mà API này yêu cầu.

Hàm giữ nguyên code point bổ sung khi chuỗi đầu vào có surrogate pair hợp lệ. Nếu đầu vào đã chứa surrogate đơn lẻ, Java có thể đếm surrogate đơn lẻ như một code point khi dùng các API này; hàm không phải bộ kiểm tra tính well-formed của văn bản Unicode.

## 7. Duyệt chuỗi: `char` hay code point?

**Duyệt theo UTF-16 code unit** khi xử lý các vị trí thấp cấp hoặc tương tác API đòi chỉ số `char`:

```java
String text = "A🚀B";
for (int i = 0; i < text.length(); i++) {
    char unit = text.charAt(i);
    // Một vòng lặp có thể nhận riêng high/low surrogate.
}
```

**Duyệt theo Unicode code point** khi cần xử lý các điểm mã nguyên vẹn:

```java
String text = "A🚀B";
text.codePoints().forEach(cp ->
        System.out.printf("U+%X%n", cp));
```

Kết quả đoạn thứ hai:

```text
U+41
U+1F680
U+42
```

`text.chars()` trả về `IntStream` của **các code unit UTF-16**, còn `text.codePoints()` trả về `IntStream` của **các code point** (kết hợp surrogate pair hợp lệ). Vì thế, `chars()` và `codePoints()` không thể hoán đổi cho nhau khi chuỗi chứa ký tự ngoài BMP.

Nếu cần duy trì **cả vị trí UTF-16 lẫn code point**, duyệt theo bước nhảy:

```java
String text = "A🚀B";
for (int i = 0; i < text.length(); ) {
    int cp = text.codePointAt(i);
    System.out.printf("UTF-16 index %d -> U+%X%n", i, cp);
    i += Character.charCount(cp);
}
```

Kết quả:

```text
UTF-16 index 0 -> U+41
UTF-16 index 1 -> U+1F680
UTF-16 index 3 -> U+42
```

`Character.charCount(cp)` trả về `2` khi code point nằm ngoài BMP, ngược lại là `1`. Cách duyệt này giữ chỉ số ở đầu mỗi code point nếu bắt đầu từ ranh giới hợp lệ.

## 8. “Một ký tự hiển thị” có thể vẫn là nhiều code point

Không nên thay mọi bài toán “ký tự” bằng cách đếm code point một cách máy móc. Ví dụ:

```java
String composed = "\u00E9"; // é: một code point
String decomposed = "e\u0301"; // e + dấu sắc kết hợp: hai code point

System.out.println(composed.length()); // 1
System.out.println(decomposed.length()); // 2
System.out.println(decomposed.codePointCount(0, decomposed.length())); // 2
System.out.println(composed.equals(decomposed)); // false
```

Hai chuỗi có thể hiển thị giống nhau nhưng khác chuỗi code unit và code point. Nếu cần so sánh các dạng Unicode tương đương, có thể cân nhắc chuẩn hóa bằng `java.text.Normalizer`, ví dụ `Normalizer.normalize(text, Normalizer.Form.NFC)`. Chuẩn hóa không biến các API chỉ số thành API grapheme cluster.

Nếu nghiệp vụ cần di chuyển con trỏ hoặc giới hạn độ dài **theo ký tự người dùng nhìn thấy**, cần thuật toán phân đoạn grapheme cluster tuân theo quy tắc Unicode tương ứng. Không lấy `length()` hoặc `codePointCount()` làm câu trả lời mặc định cho mọi ngôn ngữ và emoji ghép.

## 9. Lỗi thường gặp và cách phòng tránh

| Lỗi | Hệ quả | Cách xử lý |
| --- | --- | --- |
| Giả định `length()` là số code point. | Đếm thừa với `🚀`, `😀` và nhiều ký tự ngoài BMP. | Dùng `codePointCount` nếu thật sự cần số code point. |
| Lấy `charAt(i)` để duyệt “từng ký tự Unicode”. | Surrogate pair bị chia thành hai lần đọc. | Dùng `codePointAt` cùng bước nhảy `Character.charCount` hoặc `codePoints()`. |
| Dùng chỉ số code point trực tiếp cho `substring`. | Cắt sai vị trí hoặc cắt đôi surrogate pair. | Đổi ranh giới bằng `offsetByCodePoints` rồi mới `substring`. |
| Nghĩ `indexOf` trả về vị trí code point. | Chỉ số bị lệch sau các ký tự bổ sung. | Xem kết quả là chỉ số UTF-16, tương thích với `substring` / `charAt`. |
| Gọi `codePointAt` tại low surrogate của cặp hợp lệ. | Chỉ nhận riêng giá trị low surrogate. | Trỏ tới high surrogate (đầu code point) hoặc duyệt từ đầu. |
| Giả định “1 code point = 1 ký tự hiển thị”. | Xử lý sai dấu kết hợp, emoji có bộ chọn biến thể/dấu nối. | Xác định bài toán theo code unit, code point hay grapheme cluster trước khi chọn API. |

Các lời gọi như `charAt(-1)`, `charAt(length())` và `substring` với chỉ số không hợp lệ sẽ ném ngoại lệ chỉ số (`IndexOutOfBoundsException` hoặc lớp con). Dù một chỉ số nằm trong giới hạn, nó **vẫn có thể nằm giữa hai surrogate**, nên kiểm tra giới hạn chưa đủ bảo đảm ranh giới Unicode hợp lý.

## 10. Bài tập tự kiểm tra

Cho đoạn mã:

```java
String s = "Hi😀!";
```

1. `s.length()` là bao nhiêu? **5**: `H` và `i` là 2 `char`, `😀` là 2 `char`, `!` là 1 `char`.
2. `s.codePointCount(0, s.length())` là bao nhiêu? **4**.
3. `s.indexOf("!")` trả về bao nhiêu? **4**, vì đây là chỉ số UTF-16.
4. `s.substring(2, 4)` trả về gì? **`"😀"`**, gồm đúng hai code unit của surrogate pair.
5. `s.substring(2, 3)` có lấy trọn emoji không? **Không**, chỉ lấy high surrogate.
6. `s.offsetByCodePoints(0, 3)` là bao nhiêu? **4**, ranh giới sau code point thứ ba.
7. Nếu muốn lấy code point thứ ba (chỉ số code point bằng `2`), nên làm gì? Dùng `int start = s.offsetByCodePoints(0, 2); int end = s.offsetByCodePoints(start, 1);` rồi `s.substring(start, end)`.

## Tài liệu tham khảo

- [Java SE 17 — `String` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html)
- [Java SE 17 — `Character` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html)
- [Unicode Standard Annex #29 — Unicode Text Segmentation](https://www.unicode.org/reports/tr29/)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="unicode-utf16-003">BMP, ký tự bổ sung và surrogate pair</a>

<details>
<summary>Xem chi tiết</summary>

# BMP, ký tự bổ sung và surrogate pair trong Java String

Java lưu và xử lý văn bản theo mô hình Unicode, trong đó mỗi **code point** được nhận diện bằng một giá trị như `U+0041` (chữ A), `U+4E2D` (chữ 中) hoặc `U+1F600` (😀). Tuy nhiên, một phần lớn API `String` của Java sử dụng **UTF-16 code unit** làm đơn vị đếm và chỉ số. Vì thế, một ký tự Unicode có thể chiếm **hai phần tử `char`** trong `String`.

Hiểu rõ Basic Multilingual Plane (BMP), ký tự bổ sung (supplementary character) và surrogate pair giúp bạn xử lý đúng `length()`, `charAt()`, `substring()`, vòng lặp và việc chuyển đổi giữa `char`, `int` và `String`.

## 1. Phân biệt code point, code unit và ký tự người dùng nhìn thấy

- **Unicode code point**: một vị trí mã trong không gian `U+0000` đến `U+10FFFF`; thường viết dạng `U+XXXX` hoặc `U+XXXXX`/`U+XXXXXX`. Trong Java, nên lưu một code point bằng `int`.
- **Unicode scalar value**: code point **không nằm trong** vùng surrogate `U+D800`–`U+DFFF`. Mỗi scalar value có thể được biểu diễn hợp lệ bằng UTF-16.
- **UTF-16 code unit**: đơn vị 16 bit. Kiểu nguyên thủy `char` của Java chứa **một** UTF-16 code unit (giá trị `0x0000`–`0xFFFF`). Một `char` không nhất thiết tương đương một code point hoàn chỉnh.
- **Grapheme cluster** (cụm ký tự hiển thị): thứ người đọc thường nhận là một ký tự; có thể được tạo từ nhiều code point, ví dụ `e` + dấu sắc kết hợp hoặc một emoji ghép bằng ký tự nối.

Ba phép đếm này không luôn bằng nhau. `String.length()` đếm **UTF-16 code unit**, `String.codePointCount(...)` đếm **code point theo cách xử lý UTF-16 của Java**, còn số cụm ký tự hiển thị cần quy tắc phân đoạn grapheme chuyên biệt.

Ví dụ nhanh:

| Văn bản | Các code point | UTF-16 code unit | `String.length()` |
| --- | --- | ---: | ---: |
| `A` | `U+0041` | 1 | 1 |
| `中` | `U+4E2D` | 1 | 1 |
| `😀` | `U+1F600` | 2 | 2 |
| `é` (chữ e + dấu sắc kết hợp) | `U+0065`, `U+0301` | 2 | 2 |

Hàng cuối có hai code point trong BMP nhưng thường hiển thị thành một chữ có dấu. Vì vậy **đếm code point vẫn không đồng nghĩa đếm ký tự mà người dùng cảm nhận**.

## 2. BMP và supplementary planes

Unicode chia không gian code point thành **17 plane**, mỗi plane có khả năng chứa 65.536 vị trí mã:

| Phân vùng | Dải giá trị | Ý nghĩa |
| --- | --- | --- |
| BMP (plane 0) | `U+0000`–`U+FFFF` | Chứa phần lớn chữ cái, ký hiệu phổ biến và cả vùng mã dành riêng cho surrogate |
| Các supplementary planes (plane 1–16) | `U+10000`–`U+10FFFF` | Chứa nhiều chữ viết lịch sử, ký hiệu âm nhạc, emoji và các bộ ký tự khác |
| Surrogate code points (nằm trong BMP) | `U+D800`–`U+DFFF` | Giá trị **dành riêng cho UTF-16**, không được dùng làm Unicode scalar value độc lập |

**BMP không có nghĩa mọi vị trí trong BMP đều là ký tự hợp lệ để hiển thị.** Các vị trí surrogate là code point được dành riêng và không phải Unicode scalar value; ngoài ra còn có vị trí chưa gán hoặc không dành cho ký tự văn bản thông thường.

Một Unicode scalar value thuộc BMP (trừ vùng surrogate) được mã hóa bằng **một UTF-16 code unit**. Một code point thuộc supplementary planes được mã hóa bằng **hai UTF-16 code unit**, gọi là **surrogate pair**. Không có code point Unicode hợp lệ lớn hơn `U+10FFFF`.

Ví dụ:

- `U+0061` (a), `U+00E9` (é), `U+4E2D` (中): đều thuộc BMP; mỗi code point chiếm một `char`.
- `U+1F600` (😀), `U+1D11E` (𝄞, ký hiệu khóa Sol âm nhạc): đều thuộc supplementary planes; mỗi code point cần hai `char`.

## 3. Surrogate pair được ghép như thế nào?

UTF-16 dùng hai nhóm mã dành riêng:

- **High surrogate (leading surrogate):** `U+D800`–`U+DBFF`.
- **Low surrogate (trailing surrogate):** `U+DC00`–`U+DFFF`.

Với code point `cp` từ `U+10000` đến `U+10FFFF`, quá trình mã hóa được mô tả như sau:

1. Tính `x = cp - 0x10000` (giá trị 20 bit).
2. Lấy 10 bit cao: `high = 0xD800 + (x >> 10)`.
3. Lấy 10 bit thấp: `low = 0xDC00 + (x & 0x3FF)`.
4. Lưu lần lượt **high surrogate rồi low surrogate**.

Để giải mã một cặp hợp lệ:

```text
codePoint = 0x10000
          + ((high - 0xD800) << 10)
          +  (low  - 0xDC00)
```

Ví dụ với emoji 😀 (`U+1F600`):

```text
Code point           : U+1F600
UTF-16 high surrogate: U+D83D
UTF-16 low surrogate : U+DE00
Trong Java String    : [\uD83D] [\uDE00]  (2 code unit/char)
```

Chỉ khi **high surrogate đứng ngay trước low surrogate** thì Java mới có thể kết hợp hai đơn vị thành một supplementary code point. Một high surrogate đứng lẻ, low surrogate đứng lẻ, hoặc hai surrogate sai thứ tự là **chuỗi UTF-16 không hợp lệ về mặt cặp mã** (ill-formed UTF-16). Kiểu `String` của Java vẫn có khả năng chứa các code unit lẻ như vậy; ứng dụng không được mặc định mọi `String` đều có UTF-16 được ghép cặp đầy đủ.

Các API liên quan trong `Character`:

| Phương thức | Công dụng |
| --- | --- |
| `Character.isHighSurrogate(char)` | Kiểm tra một `char` thuộc vùng high surrogate |
| `Character.isLowSurrogate(char)` | Kiểm tra một `char` thuộc vùng low surrogate |
| `Character.isSurrogatePair(high, low)` | Kiểm tra đúng thứ tự cặp high + low |
| `Character.toCodePoint(high, low)` | Ghép **cặp surrogate hợp lệ** thành code point dạng `int` |
| `Character.toChars(int)` | Mã hóa một code point thành mảng `char[]` dài 1 hoặc 2 |
| `Character.charCount(int)` | Trả về 2 nếu code point thuộc supplementary range, ngược lại trả về 1 |

Lưu ý: `Character.toCodePoint()` giả định đầu vào là cặp phù hợp; nếu nhận dữ liệu không chắc chắn, hãy gọi `isSurrogatePair()` trước.

## 4. Ví dụ Java: cùng một code point nhưng có hai char

Tệp `UnicodeBasics.java` (Java 17+):

```java
public class UnicodeBasics {
    public static void main(String[] args) {
        String bmp = "\u4E2D"; // U+4E2D: một ký tự thuộc BMP
        String supplementary = new String(Character.toChars(0x1F600));

        System.out.printf(
                "BMP: length=%d, codePoints=%d%n",
                bmp.length(), bmp.codePointCount(0, bmp.length())
        );
        System.out.printf(
                "Supplementary: length=%d, codePoints=%d%n",
                supplementary.length(),
                supplementary.codePointCount(0, supplementary.length())
        );

        char high = supplementary.charAt(0);
        char low = supplementary.charAt(1);

        System.out.printf("charAt(0)=U+%04X%n", (int) high);
        System.out.printf("charAt(1)=U+%04X%n", (int) low);
        System.out.println("valid pair=" + Character.isSurrogatePair(high, low));
        System.out.printf("codePointAt(0)=U+%X%n",
                supplementary.codePointAt(0));
        System.out.printf("toCodePoint=U+%X%n",
                Character.toCodePoint(high, low));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 UnicodeBasics.java
java UnicodeBasics
```

Kết quả chuẩn:

```text
BMP: length=1, codePoints=1
Supplementary: length=2, codePoints=1
charAt(0)=U+D83D
charAt(1)=U+DE00
valid pair=true
codePointAt(0)=U+1F600
toCodePoint=U+1F600
```

Ở đây `String.length()` của emoji trả về **2**, bởi nó đếm hai UTF-16 code unit. Trong khi đó `codePointCount(0, length())` trả về **1** vì cặp surrogate hợp lệ là một code point.

`charAt(0)` và `charAt(1)` chỉ trả về từng phần của cặp mã. Không được giả định một `charAt(index)` luôn là ký tự Unicode hoàn chỉnh. `String.codePointAt(index)` có thể ghép cặp khi `index` đứng tại **high surrogate** và ngay sau là **low surrogate**; nếu gọi bắt đầu từ low surrogate, nó không tự nhìn lùi để ghép với phần high phía trước.

Có thể tạo cùng emoji bằng escape trong string literal:

```java
String smile = "\uD83D\uDE00";
```

Chuỗi escape trên cung cấp hai UTF-16 code unit. Dùng `new String(Character.toChars(0x1F600))` thường giúp thể hiện rõ hơn rằng ta bắt đầu từ một **code point**.

## 5. Chỉ số char khác vị trí code point

Các API như `charAt(int)`, `substring(int, int)`, `indexOf(...)` theo các overload nhận/trả chỉ số, và `String.length()` làm việc với **chỉ số UTF-16 code unit**. Nhưng một code point supplementary chiếm hai chỉ số liên tiếp.

Xét chuỗi `A😀B`:

```text
Chỉ số UTF-16:  0         1          2         3
Code unit    : 'A'    high D83D   low DE00    'B'
Code point   : U+0041        U+1F600         U+0042
Vị trí code point: 0              1             2
```

Tổng cộng `length() == 4` nhưng chỉ có **3 code point**.

Đây là ví dụ duyệt đúng theo code point bằng `codePointAt()` kết hợp `Character.charCount()`:

Tệp `CodePointIteration.java`:

```java
public class CodePointIteration {
    public static void main(String[] args) {
        String text = "A" + new String(Character.toChars(0x1F600)) + "B";

        System.out.println("length=" + text.length());
        System.out.println("codePoints=" +
                text.codePointCount(0, text.length()));

        for (int charIndex = 0; charIndex < text.length();) {
            int codePoint = text.codePointAt(charIndex);
            System.out.printf("charIndex=%d -> U+%X%n", charIndex, codePoint);
            charIndex += Character.charCount(codePoint);
        }

        int afterTwoCodePoints = text.offsetByCodePoints(0, 2);
        System.out.println("offsetByCodePoints(0, 2)=" + afterTwoCodePoints);

        String emojiOnly = text.substring(1, 3);
        System.out.printf("substring(1, 3)=U+%X%n",
                emojiOnly.codePointAt(0));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 CodePointIteration.java
java CodePointIteration
```

Kết quả:

```text
length=4
codePoints=3
charIndex=0 -> U+41
charIndex=1 -> U+1F600
charIndex=3 -> U+42
offsetByCodePoints(0, 2)=3
substring(1, 3)=U+1F600
```

Vòng lặp tăng chỉ số UTF-16 lên **1** cho code point thuộc BMP, và lên **2** cho code point supplementary. Lời gọi `offsetByCodePoints(0, 2)` chuyển vị trí **hai code point** tính từ chỉ số UTF-16 số 0 thành chỉ số UTF-16 số **3**; kết quả này hữu ích khi cần cắt chuỗi tại ranh giới code point.

Có thể duyệt ngắn gọn bằng `text.codePoints()`, trả về `IntStream` chứa giá trị dạng `int`:

```java
text.codePoints().forEach(cp ->
        System.out.printf("U+%X%n", cp)
);
```

Đoạn trên là phần minh họa cần đặt trong phương thức có biến `text` đã khai báo. Với chuỗi `A😀B`, nó lần lượt in `U+41`, `U+1F600`, `U+42` (mỗi giá trị trên một dòng).

**Phân biệt `chars()` và `codePoints()`:**

- `text.chars()` tạo luồng các giá trị `int` đại diện **từng UTF-16 code unit**. Với `A😀B`, luồng có **4** phần tử: `0x41`, `0xD83D`, `0xDE00`, `0x42`.
- `text.codePoints()` cố gắng ghép **các surrogate pair hợp lệ**; cùng ví dụ trên, luồng có **3** phần tử: `0x41`, `0x1F600`, `0x42`.

Nếu chuỗi có surrogate đứng lẻ, `codePoints()` vẫn có thể phát ra chính giá trị surrogate chưa ghép dưới dạng `int`; đừng mặc định tất cả phần tử đều là Unicode scalar value hợp lệ.

## 6. Cắt chuỗi mà không làm hỏng surrogate pair

Hai chỉ số của `substring(beginIndex, endIndex)` là vị trí UTF-16, **begin bao gồm, end không bao gồm**. Với chuỗi `A😀B`, `substring(1, 3)` giữ trọn emoji; còn `substring(1, 2)` chỉ lấy **high surrogate** và tạo ra chuỗi UTF-16 bị tách cặp.

Nếu nghiệp vụ cần lấy **n code point đầu tiên**, hãy chuyển số code point sang chỉ số UTF-16 trước khi gọi `substring()`:

```java
public class SafeSubstring {
    static String firstCodePoints(String text, int count) {
        int end = text.offsetByCodePoints(0, count);
        return text.substring(0, end);
    }

    public static void main(String[] args) {
        String text = "A" + new String(Character.toChars(0x1F600)) + "B";
        String firstTwo = firstCodePoints(text, 2);

        System.out.println("UTF16 length=" + firstTwo.length());
        System.out.println("codePoints=" +
                firstTwo.codePointCount(0, firstTwo.length()));
        System.out.printf("lastCodePoint=U+%X%n",
                firstTwo.codePointBefore(firstTwo.length()));
    }
}
```

Lưu thành `SafeSubstring.java`, biên dịch và chạy bằng `javac -encoding UTF-8 SafeSubstring.java` và `java SafeSubstring`. Kết quả:

```text
UTF16 length=3
codePoints=2
lastCodePoint=U+1F600
```

`offsetByCodePoints()` có thể ném `IndexOutOfBoundsException` khi số code point cần dịch nằm ngoài phạm vi hợp lệ. Ví dụ sử dụng thực tế nên kiểm tra `count >= 0` và `count <= text.codePointCount(0, text.length())` trước khi cắt, hoặc quy định rõ cách xử lý đầu vào không hợp lệ.

Giải pháp trên bảo vệ ranh giới **code point**, nhưng chưa chắc bảo toàn một **grapheme cluster**. Ví dụ cắt giữa `e` và dấu kết hợp `U+0301` vẫn có thể phá trải nghiệm hiển thị mặc dù không cắt surrogate pair.

## 7. Kiểm tra surrogate đứng lẻ và giá trị mã không hợp lệ

Một `String` có thể lưu riêng một high surrogate mà không có low surrogate kèm theo:

```java
String broken = "\uD83D";
System.out.println(broken.length());                     // 1
System.out.println(Character.isHighSurrogate(broken.charAt(0))); // true
System.out.printf("U+%X%n", broken.codePointAt(0));      // U+D83D
```

Đây là các câu lệnh minh họa để đặt trong một phương thức Java. `codePointAt()` trả về giá trị code unit chưa ghép khi không có một cặp surrogate hợp lệ, vì vậy kết quả `U+D83D` ở đây **không phải Unicode scalar value**. Khi ghi dữ liệu dạng này sang một encoding hoặc một hệ thống khác, cách xử lý ký tự lỗi có thể phụ thuộc encoder và cấu hình xử lý lỗi. Không nên dựa vào việc surrogate đứng lẻ được truyền đi nguyên dạng.

Để nhận biết surrogate pair khi duyệt bằng `char`:

```java
static boolean hasWellFormedUtf16(String text) {
    for (int i = 0; i < text.length(); i++) {
        char current = text.charAt(i);

        if (Character.isHighSurrogate(current)) {
            if (i + 1 >= text.length() ||
                    !Character.isLowSurrogate(text.charAt(i + 1))) {
                return false;
            }
            i++; // Bỏ qua low surrogate vừa ghép cùng high surrogate
        } else if (Character.isLowSurrogate(current)) {
            return false; // Low surrogate đứng lẻ
        }
    }
    return true;
}
```

Đây là **phương thức minh họa**, cần đặt bên trong một lớp. Nó kiểm tra tính đúng đắn của **các cặp UTF-16**, không kiểm tra việc một code point đã được Unicode gán ký tự cụ thể hay chưa.

Ngoài ra:

- `Character.isValidCodePoint(cp)` chỉ kiểm tra số nguyên nằm trong dải `U+0000`–`U+10FFFF`; kiểm tra này **không loại riêng vùng surrogate**. Muốn nhận một Unicode scalar value, phải loại thêm `U+D800`–`U+DFFF`.
- `Character.isSupplementaryCodePoint(cp)` nhận biết dải `U+10000`–`U+10FFFF`.
- `Character.toChars(cp)` sẽ từ chối giá trị ngoài dải Unicode bằng `IllegalArgumentException`, nhưng không nên dùng nó một mình như bộ kiểm tra scalar value. Với giá trị surrogate, nó vẫn có thể tạo ra một `char` surrogate đứng lẻ.
- Ký tự nằm trong BMP không đồng nghĩa đã được Unicode gán ý nghĩa hiển thị. Mặt khác, sự tồn tại của glyph còn phụ thuộc font và môi trường hiển thị.

## 8. Không đồng nhất code point với độ dài byte hay ký tự hiển thị

Cần phân biệt ba lớp biểu diễn:

| Câu hỏi | API/khái niệm phù hợp |
| --- | --- |
| Chuỗi có bao nhiêu UTF-16 code unit? | `text.length()` |
| Có bao nhiêu code point theo quy tắc ghép surrogate của Java? | `text.codePointCount(0, text.length())` hoặc `text.codePoints().count()` |
| Một code point ở chỉ số UTF-16 nào? | `text.codePointAt(charIndex)` |
| Dịch n code point từ một chỉ số UTF-16? | `text.offsetByCodePoints(charIndex, n)` |
| UTF-8 của chuỗi có bao nhiêu byte? | `text.getBytes(StandardCharsets.UTF_8).length` với lưu ý dữ liệu UTF-16 lỗi có thể bị thay thế khi encode |
| Người dùng thấy bao nhiêu “ký tự”? | Phân đoạn grapheme cluster theo quy tắc Unicode phù hợp |

Ví dụ `😀` chiếm **2 UTF-16 code unit** nhưng **4 byte UTF-8** khi mã hóa hợp lệ. `String.length()` không phải số byte của file hay request HTTP; cũng không phải kích thước bộ nhớ nội bộ của đối tượng `String`. JVM có thể tối ưu lưu trữ chuỗi bằng biểu diễn nội bộ khác, nhưng hợp đồng công khai của API vẫn dựa trên chỉ số UTF-16.

Với dấu kết hợp:

```java
String decomposed = "e\u0301"; // e + COMBINING ACUTE ACCENT
String composed = "\u00E9";   // é dạng tiền tổ hợp

System.out.println(decomposed.length()); // 2
System.out.println(composed.length());   // 1
```

Hai chuỗi có thể hiển thị tương tự nhưng khác chuỗi code point và không `equals()` nhau nếu chưa chuẩn hóa theo quy tắc thích hợp. **Unicode normalization** là chủ đề liên quan nhưng độc lập với việc ghép surrogate pair.

## 9. Lỗi phổ biến

**Lỗi 1 — Coi `char` là một ký tự Unicode hoàn chỉnh.** `char` chỉ là code unit 16 bit. Một supplementary code point cần hai `char` liên tiếp.

**Lỗi 2 — Dùng `length()` làm số code point.** Khi có ký tự bổ sung, `length()` lớn hơn số code point; hãy dùng `codePointCount()` để đếm code point theo mô hình của Java.

**Lỗi 3 — Tăng chỉ số `i++` rồi gọi `codePointAt(i)`.** Vòng lặp có thể dừng tại low surrogate và xử lý nhầm từng nửa của ký tự. Tăng bằng `Character.charCount(codePoint)` hoặc duyệt `codePoints()`.

**Lỗi 4 — Cắt `substring()` giữa cặp surrogate.** Sử dụng `offsetByCodePoints()` để chuyển vị trí code point sang chỉ số UTF-16 trước khi cắt. Nếu thao tác hiển thị yêu cầu giữ cụm grapheme, cần giải pháp phân đoạn ở mức cao hơn.

**Lỗi 5 — Tin rằng surrogate nào cũng thuộc một cặp hợp lệ.** `String` có thể chứa surrogate đứng lẻ. Dữ liệu nhập từ bên ngoài cần được xác thực hoặc quy định cách xử lý ký tự lỗi khi cần.

**Lỗi 6 — Nhầm số code unit, code point, grapheme cluster và byte.** Bốn đại lượng phục vụ các nhu cầu khác nhau: chỉ số API Java, mã Unicode, trải nghiệm văn bản và lưu trữ/truyền tải.

## 10. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao `"😀".length()` bằng 2 nhưng `"😀".codePointCount(0, "😀".length())` bằng 1?

**Trả lời:** Emoji `U+1F600` là supplementary code point, nên UTF-16 biểu diễn bằng hai code unit `U+D83D` và `U+DE00`. `length()` đếm hai code unit; `codePointCount()` nhận ra một cặp hợp lệ và đếm một code point.

**Câu 2.** `charAt(1)` trong chuỗi `A😀B` trả về gì?

**Trả lời:** High surrogate `U+D83D`, không phải toàn bộ code point `U+1F600`. Để đọc toàn bộ emoji, dùng `codePointAt(1)` và nhận giá trị `int`.

**Câu 3.** Tại sao `substring(1, 2)` trên `A😀B` có vấn đề, còn `substring(1, 3)` thì không?

**Trả lời:** Chỉ số của `substring()` tính theo UTF-16 code unit. Đoạn `[1,2)` chỉ lấy high surrogate; đoạn `[1,3)` lấy đủ cả high và low surrogate.

**Câu 4.** Một `String` có 5 code point có chắc hiển thị thành 5 ký tự không?

**Trả lời:** Không. Các dấu kết hợp, variation selector hoặc chuỗi emoji ghép có thể chứa nhiều code point nhưng hiển thị thành ít cụm grapheme hơn.

**Ghi nhớ:** BMP scalar value dùng **một** UTF-16 code unit; supplementary code point dùng **hai** code unit tạo thành **high + low surrogate**. Trong Java, `char` và chỉ số của `String` thuộc mức UTF-16 code unit; khi cần xử lý trọn vẹn code point, ưu tiên `int`, `codePointAt()`, `codePointCount()`, `offsetByCodePoints()`, `Character.charCount()` hoặc `codePoints()`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="unicode-utf16-004">Độ dài của tiếng Việt, dấu kết hợp và emoji</a>

<details>
<summary>Xem chi tiết</summary>

# Độ dài của tiếng Việt, dấu kết hợp và emoji trong Java

## 1. Vì sao đếm ký tự lại khó?

Khi người dùng nhập **“Việt”**, **“ắ”**, **“😊”** hoặc **“👩‍💻”**, mỗi hình thức hiển thị có vẻ gồm những chữ hoặc biểu tượng rõ ràng. Tuy nhiên, một chuỗi có thể được đo theo nhiều đơn vị khác nhau. Trong Java, gọi **String.length()** không đồng nghĩa với “đếm bao nhiêu chữ người dùng nhìn thấy”.

Ví dụ:

- Chữ **“ắ”** viết sẵn dưới dạng Unicode U+1EAF có **1 đơn vị mã UTF-16**, **1 code point**, **1 cụm ký tự hiển thị**.
- Chữ có hình thức tương tự nhưng được tạo từ **a + dấu breve + dấu sắc** (**a\u0306\u0301**) có **3 đơn vị mã**, **3 code point**, nhưng vẫn là **1 cụm ký tự hiển thị**.
- Emoji **“😊”** gồm **1 code point** nhưng cần **2 đơn vị mã UTF-16**.
- Emoji **“👩‍💻”** gồm **3 code point** liên kết thành **1 biểu tượng**; trong Java, chuỗi này dài **5 đơn vị mã UTF-16**.

Vì vậy, trước khi giới hạn tên người dùng, cắt văn bản, định vị con trỏ hay đo dung lượng byte, phải xác định **“độ dài” đang nói đến loại đơn vị nào**.

## 2. Bốn đơn vị cần phân biệt

| Đơn vị | Diễn giải | Cách đo trong Java | Dùng khi nào? |
| --- | --- | --- | --- |
| **UTF-16 code unit** | Một đơn vị 16 bit trong giao diện chuỗi Java | **s.length()**; chỉ số của **charAt()**, **substring()** | Tương thích API, lấy chỉ số UTF-16 |
| **Unicode code point** | Giá trị mã Unicode, ví dụ U+1F60A | **s.codePointCount(0, s.length())** hoặc **s.codePoints().count()** | Duyệt/kiểm tra các giá trị Unicode |
| **Extended grapheme cluster** | Cụm code point thường được người dùng cảm nhận như một ký tự | Regex **\\X** hoặc thư viện phân đoạn Unicode | Giới hạn số ký tự hiển thị, cắt gần với thao tác nhập liệu |
| **Byte sau khi mã hóa** | Số byte của chuỗi ở một charset xác định | **s.getBytes(StandardCharsets.UTF_8).length** | Kích thước payload, tệp, giới hạn lưu trữ |

**UTF-16 code unit không phải byte UTF-8.** Một Java **char** chứa một code unit UTF-16; với ký tự ngoài Basic Multilingual Plane (BMP), một code point phải dùng **hai char** là *surrogate pair*. Ngược lại, một grapheme có thể chứa nhiều code point, chẳng hạn chữ có dấu kết hợp hoặc emoji ghép.

Từ Java 9, triển khai String có thể dùng cơ chế *compact strings* để tối ưu bộ nhớ. Điều đó **không làm thay đổi** quy tắc của các API tính theo UTF-16 code unit. Không suy ra kích thước vật lý của đối tượng String từ **length()**.

### Bảng giá trị minh họa

Các ví dụ dưới đây là những chuỗi cụ thể, với dấu tiếng Việt trong cột NFC được dùng ở dạng dựng sẵn:

| Chuỗi | UTF-16 units | Code points | Grapheme clusters | UTF-8 bytes |
| --- | ---: | ---: | ---: | ---: |
| **“Việt”** (NFC) | 4 | 4 | 4 | 6 |
| **“Việt”** (NFD: chữ **ệ** tách thành **e + dấu nặng + dấu mũ**) | 6 | 6 | 4 | 8 |
| **“ắ”** (NFC) | 1 | 1 | 1 | 3 |
| **“a\u0306\u0301”** (NFD; a + hai dấu kết hợp) | 3 | 3 | 1 | 5 |
| **“😊”** | 2 | 1 | 1 | 4 |
| **“👍🏽”** (ngón tay cái + tông da) | 4 | 2 | 1 | 8 |
| **“👩‍💻”** (người phụ nữ + ZWJ + máy tính) | 5 | 3 | 1 | 11 |
| **“🇻🇳”** (hai regional indicator) | 4 | 2 | 1 | 8 |

Số grapheme ở bảng theo quy tắc **extended grapheme cluster** của Unicode áp dụng cho các tổ hợp được nêu. Bộ dữ liệu Unicode trong JDK hoặc thư viện cụ thể có thể ảnh hưởng việc nhận diện những emoji mới xuất hiện sau phiên bản được hỗ trợ.

## 3. Tiếng Việt: dấu dựng sẵn và dấu kết hợp

### 3.1. Hai cách mã hóa có thể nhìn giống nhau

Một chữ tiếng Việt có dấu đôi khi là **một code point dựng sẵn (precomposed)**, đôi khi là **chữ cơ sở cộng các combining mark**.

Với chữ **“ắ”**:

- Dạng dựng sẵn NFC: **U+1EAF** — LATIN SMALL LETTER A WITH BREVE AND ACUTE.
- Dạng tách NFD: **U+0061** (a) + **U+0306** (COMBINING BREVE) + **U+0301** (COMBINING ACUTE).

Cả hai thường được hiển thị như cùng một chữ **ắ**. Tuy nhiên:

~~~java
String composed = "\u1EAF";
String decomposed = "a\u0306\u0301";

System.out.println(composed.length());        // 1
System.out.println(decomposed.length());      // 3
System.out.println(composed.equals(decomposed)); // false
~~~

Đây là **đoạn mã minh họa** đặt trong một phương thức. Phép **equals()** so sánh nội dung theo các code unit, không tự nhận diện rằng hai cách mã hóa tương đương về chuẩn Unicode.

Trong văn bản tiếng Việt, các chữ có tổ hợp dấu như **ắ, ậ, ệ, ỡ, ự** có thể có dạng dựng sẵn và dạng tách. Với từ **“Việt”**, chuỗi NFC có 4 code point; khi chuyển sang NFD, **ệ** trở thành **e + dấu nặng (U+0323) + dấu mũ (U+0302)**, đưa tổng số lên 6 code point. Số chữ mà người đọc nhận thấy vẫn là 4.

Dấu có thể trông khác đôi chút tùy phông, hệ thống vẽ chữ và quy tắc kết hợp dấu; chỉ nhìn hình ảnh không phải là cách xác nhận nội dung Unicode.

### 3.2. Unicode normalization

Lớp **java.text.Normalizer** cung cấp phép chuẩn hóa có định nghĩa rõ ràng:

- **NFC** (*Normalization Form C*): dùng canonical decomposition rồi canonical composition, giúp biểu diễn những chuỗi tương đương chuẩn theo dạng kết hợp khi có thể.
- **NFD** (*Normalization Form D*): tách các ký tự có canonical decomposition thành thành phần cơ sở và dấu kết hợp, đồng thời sắp xếp canonical combining mark theo quy tắc Unicode.
- **NFKC/NFKD**: còn thực hiện compatibility normalization, có thể biến đổi những khác biệt mang ý nghĩa trình bày hoặc tương thích. Chỉ dùng khi bài toán thực sự yêu cầu.

Ví dụ:

~~~java
import java.text.Normalizer;

String left = "\u1EAF";
String right = "a\u0306\u0301";

String normalizedLeft = Normalizer.normalize(left, Normalizer.Form.NFC);
String normalizedRight = Normalizer.normalize(right, Normalizer.Form.NFC);
System.out.println(normalizedLeft.equals(normalizedRight)); // true
~~~

**NFC không bỏ dấu tiếng Việt.** Chuẩn hóa cũng không phải phép chuyển tiếng Việt sang không dấu (*transliteration*). Nếu cần so sánh hoặc tìm kiếm bỏ dấu, phải thiết kế nghiệp vụ riêng vì việc xóa combining mark có thể làm mất thông tin và không giải quyết đầy đủ mọi chữ (ví dụ **đ** không tự trở thành **d** chỉ bằng NFD).

**Không chuẩn hóa một cách tùy tiện** đối với chữ ký số, tên tài nguyên yêu cầu đối chiếu byte chính xác, dữ liệu phải giữ nguyên bản gốc hoặc hợp đồng API có quy tắc cụ thể. Nếu nghiệp vụ định chuẩn hóa, hãy làm nhất quán tại ranh giới nhập dữ liệu, lưu trữ và so sánh.

## 4. Emoji và surrogate pair

### 4.1. Emoji gồm một code point nhưng hai code unit

Emoji **“😊”** là code point **U+1F60A**, nằm ngoài BMP. Trong UTF-16, nó được mã hóa bằng surrogate pair **U+D83D U+DE0A**:

~~~java
String emoji = "😊";

System.out.println(emoji.length());                           // 2
System.out.println(emoji.codePointCount(0, emoji.length()));  // 1
System.out.println(Character.isHighSurrogate(emoji.charAt(0))); // true
System.out.println(Character.isLowSurrogate(emoji.charAt(1)));  // true
System.out.printf("U+%X%n", emoji.codePointAt(0));              // U+1F60A
~~~

Mỗi lệnh trong đoạn trên phù hợp khi đặt vào một phương thức Java. **charAt(0)** trả về phần đầu của surrogate pair, **không phải emoji hoàn chỉnh**. Cắt **emoji.substring(0, 1)** tạo String chỉ chứa một high surrogate không ghép cặp, có thể gây hiển thị hoặc chuyển mã sai.

Để đi qua từng code point, có thể dùng **codePoints()**, **codePointAt(...)** hoặc **offsetByCodePoints(...)**. Tuy vậy, đi theo code point vẫn chưa đảm bảo không chia cắt một emoji do nhiều code point ghép lại.

### 4.2. Modifier, dấu nối ZWJ và quốc kỳ

Một số emoji gồm **nhiều code point**:

- **“👍🏽”** gồm U+1F44D (thumbs up) và U+1F3FD (skin tone modifier). Hai code point, bốn code unit UTF-16, thường hiển thị một biểu tượng.
- **“👩‍💻”** gồm U+1F469 (woman), U+200D (ZERO WIDTH JOINER, ZWJ) và U+1F4BB (laptop). Ba code point nhưng là một extended grapheme cluster theo quy tắc liên quan.
- **“🇻🇳”** là cặp regional indicator U+1F1FB và U+1F1F3. Hai code point, bốn UTF-16 code unit, thường hiển thị một lá cờ.
- Một số trình tự emoji còn dùng variation selector như U+FE0F để yêu cầu cách trình bày emoji. Variation selector cũng là code point và góp vào độ dài.

Vì thế, ngay cả **codePointCount()** cũng có thể cho kết quả lớn hơn số biểu tượng người dùng nhìn thấy. Hình dạng emoji thật sự phụ thuộc hỗ trợ của font, nền tảng và phiên bản dữ liệu Unicode; một cụm hợp lệ có thể hiện thành nhiều hình riêng khi môi trường không hỗ trợ ghép.

## 5. Grapheme cluster: đơn vị gần với ký tự người dùng

Unicode Standard Annex #29 định nghĩa quy tắc *grapheme cluster boundaries*. Một **extended grapheme cluster** có thể gồm chữ cái và dấu kết hợp, base emoji cộng modifier, trình tự ZWJ hoặc cặp regional indicator.

Java 17 hỗ trợ ký hiệu regex **\\X**, khớp một extended grapheme cluster theo dữ liệu Unicode được JDK sử dụng. Ví dụ:

~~~java
import java.util.regex.Pattern;

Pattern grapheme = Pattern.compile("\\X");
String text = "A👩‍💻B";

long visibleUnits = grapheme.matcher(text).results().count();
System.out.println(visibleUnits); // 3
~~~

Phương thức **Matcher.results()** trả về Stream các kết quả khớp; mỗi kết quả của regex này ứng với một grapheme cluster. Cách đếm này phù hợp với nhiều giới hạn ký tự ở giao diện, nhưng *grapheme cluster* vẫn là một đơn vị phân đoạn Unicode, **không phải** bảo đảm rằng hình hiển thị có bề rộng một cột hay đúng một glyph. Về chiều rộng trên terminal/UI, còn phải xét font, shaping, layout và grapheme rendering.

**BreakIterator.getCharacterInstance(...)** là một API phân đoạn khác trong Java. Khả năng phân đoạn các chuỗi emoji phức tạp và phiên bản dữ liệu Unicode cần được kiểm tra theo JDK thực tế. Khi cần kết quả nhất quán giữa web, mobile và server, hoặc emoji mới nhất, hãy quy định phiên bản Unicode và cân nhắc thư viện chuyên dụng như ICU4J; chạy bộ kiểm thử với các chuỗi thực tế.

## 6. Ví dụ Java 17+ hoàn chỉnh: so sánh cả bốn loại độ dài

Lưu chương trình sau thành tệp **UnicodeLengthDemo.java** với mã hóa UTF-8.

~~~java
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.regex.Pattern;

public class UnicodeLengthDemo {
    private static final Pattern GRAPHEME = Pattern.compile("\\X");

    private static void describe(String label, String value) {
        int units = value.length();
        int points = value.codePointCount(0, units);
        long clusters = GRAPHEME.matcher(value).results().count();
        int bytes = value.getBytes(StandardCharsets.UTF_8).length;

        System.out.printf(
                "%s | units=%d | points=%d | clusters=%d | UTF8=%d%n",
                label, units, points, clusters, bytes);
    }

    public static void main(String[] args) {
        String vietnameseNfc = "Việt";
        String vietnameseNfd = Normalizer.normalize(
                vietnameseNfc, Normalizer.Form.NFD);

        String aNfc = "\u1EAF";
        String aNfd = "a\u0306\u0301";

        describe("VIET_NFC", vietnameseNfc);
        describe("VIET_NFD", vietnameseNfd);
        describe("A_NFC", aNfc);
        describe("A_NFD", aNfd);
        describe("SMILE", "😊");
        describe("TONE", "👍🏽");
        describe("ZWJ", "👩‍💻");
        describe("FLAG", "🇻🇳");

        System.out.println("plain equals: " + aNfc.equals(aNfd));
        String normalizedA = Normalizer.normalize(
                aNfc, Normalizer.Form.NFC);
        String normalizedB = Normalizer.normalize(
                aNfd, Normalizer.Form.NFC);
        System.out.println(
                "NFC equals: " + normalizedA.equals(normalizedB));

        String emoji = "😊";
        System.out.println(
                "emoji high surrogate: " +
                Character.isHighSurrogate(emoji.charAt(0)));
        System.out.printf("emoji point=U+%X%n", emoji.codePointAt(0));
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 UnicodeLengthDemo.java
java UnicodeLengthDemo
~~~

Kết quả stdout cho các chuỗi mẫu với quy tắc grapheme nêu ở trên:

~~~text
VIET_NFC | units=4 | points=4 | clusters=4 | UTF8=6
VIET_NFD | units=6 | points=6 | clusters=4 | UTF8=8
A_NFC | units=1 | points=1 | clusters=1 | UTF8=3
A_NFD | units=3 | points=3 | clusters=1 | UTF8=5
SMILE | units=2 | points=1 | clusters=1 | UTF8=4
TONE | units=4 | points=2 | clusters=1 | UTF8=8
ZWJ | units=5 | points=3 | clusters=1 | UTF8=11
FLAG | units=4 | points=2 | clusters=1 | UTF8=8
plain equals: false
NFC equals: true
emoji high surrogate: true
emoji point=U+1F60A
~~~

**Giải thích kết quả:**

1. **VIET_NFC** và **VIET_NFD** giữ bốn chữ người dùng nhìn thấy; dạng NFD tăng số code unit, code point và byte vì dấu của **ệ** được tách riêng.
2. **A_NFC** và **A_NFD** hiển thị tương đương nhưng có độ dài code unit khác nhau. **equals()** trả về **false** trước chuẩn hóa, **true** sau NFC.
3. Emoji đơn **😊** có 2 code unit nhưng 1 code point. **👍🏽**, **👩‍💻** và **🇻🇳** có nhiều code point, song mỗi chuỗi mẫu là 1 grapheme cluster.
4. **UTF8** là độ dài của dữ liệu *sau khi mã hóa UTF-8*, không phải **String.length()**; với trình tự ZWJ trong ví dụ, đó là 11 byte.
5. **codePointAt(0)** đọc toàn bộ code point U+1F60A khi vị trí 0 là đầu một surrogate pair hợp lệ.

Lưu ý: file nguồn phải được lưu dưới UTF-8 để literal tiếng Việt và emoji trong chương trình giữ đúng giá trị. Tham số **-encoding UTF-8** quy định cách trình biên dịch đọc file nguồn.

## 7. Cắt chuỗi theo grapheme thay vì vị trí UTF-16

Giả sử đầu vào là **“A👩‍💻B”**. Chuỗi gồm 7 code unit UTF-16: A (1) + emoji ZWJ (5) + B (1), nhưng gồm **3 grapheme cluster**.

Cắt **substring(0, 2)** sẽ không giữ nguyên biểu tượng emoji; nó chọn A và một phần surrogate pair của ký tự đầu trong tổ hợp. Cắt theo 2 code point cũng không phải giữ đủ emoji “👩‍💻”.

Nếu yêu cầu sản phẩm là “giữ hai ký tự người dùng nhìn thấy đầu tiên”, nên tìm ranh giới grapheme. Lưu đoạn dưới trong **GraphemeTruncateDemo.java**:

~~~java
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GraphemeTruncateDemo {
    private static final Pattern GRAPHEME = Pattern.compile("\\X");

    static String firstGraphemes(String value, int maxClusters) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        if (maxClusters < 0) {
            throw new IllegalArgumentException("negative limit");
        }

        Matcher matcher = GRAPHEME.matcher(value);
        int end = 0;

        for (int i = 0; i < maxClusters && matcher.find(); i++) {
            end = matcher.end();
        }

        return value.substring(0, end);
    }

    public static void main(String[] args) {
        String original = "A👩‍💻B";

        System.out.println(original.length());
        System.out.println(GRAPHEME.matcher(original).results().count());
        System.out.println(firstGraphemes(original, 2));
        System.out.println(firstGraphemes(original, 0).isEmpty());
        System.out.println(firstGraphemes(original, 99));
    }
}
~~~

~~~bash
javac -encoding UTF-8 GraphemeTruncateDemo.java
java GraphemeTruncateDemo
~~~

~~~text
7
3
A👩‍💻
true
A👩‍💻B
~~~

Phương thức **Matcher.end()** trả về vị trí cuối của kết quả khớp trong **chỉ số UTF-16** — chính loại chỉ số mà **substring(0, end)** mong đợi. Việc lấy ranh giới từ kết quả phân đoạn giúp không cắt giữa cluster đối với các chuỗi mà regex nhận diện đúng. Nếu giới hạn là 0, chuỗi trả về rỗng; nếu giới hạn lớn hơn số cluster, kết quả là chuỗi gốc.

Đây là phương pháp phân đoạn văn bản, không phải biện pháp rút gọn để vừa số byte. Với payload có giới hạn byte, cần một thuật toán khác vừa tôn trọng ranh giới grapheme vừa đo bytes sau encoding.

## 8. Chọn đúng đơn vị độ dài theo yêu cầu nghiệp vụ

| Bài toán | Đơn vị thường phù hợp | Lưu ý |
| --- | --- | --- |
| API Java yêu cầu vị trí cho **substring**, **charAt** | UTF-16 code unit | Không được đưa chỉ số code point vào API như thể chúng là offset UTF-16. |
| Duyệt các scalar Unicode hoặc phân tích code point | Code point | Chuỗi Java có thể chứa surrogate lẻ; cần chính sách xử lý dữ liệu không hợp lệ. |
| Form nhập “tối đa 20 ký tự người dùng nhìn thấy” | Grapheme cluster | Đồng bộ quy tắc phân đoạn giữa client/server và xác định Unicode version. |
| HTTP body hoặc cột lưu trữ có giới hạn byte | Byte theo charset được xác định | Đếm **getBytes(UTF_8).length** sau khi quyết định normalization. |
| Tìm kiếm tên tiếng Việt tương đương chuẩn | Chọn normalization phù hợp, thường NFC | Chuẩn hóa trước so sánh theo hợp đồng; cân nhắc cả case, locale và diacritics. |
| Trích đoạn giao diện | Grapheme boundary + quy tắc layout | Tránh cắt giữa dấu kết hợp/emoji; chiều rộng hiển thị vẫn do renderer quyết định. |

Một hệ thống có thể cần **hai giới hạn cùng lúc**: tối đa 30 grapheme cho trải nghiệm nhập liệu và tối đa 255 byte UTF-8 để tương thích giao thức. Các giới hạn này độc lập; không thể thay thế một giới hạn bằng phép đo của giới hạn kia.

Nếu ứng dụng lưu dữ liệu và kiểm tra độ dài ở cả client lẫn server, cần định nghĩa rõ: có chuẩn hóa NFC trước khi kiểm tra không, dùng quy tắc phân đoạn Unicode phiên bản nào, tính emoji ghép như thế nào và phần nào chịu trách nhiệm kiểm tra byte.

## 9. Các lỗi thường gặp

**Lỗi 1 — Dùng String.length() để giới hạn số chữ người dùng nhập.** Một chữ tiếng Việt NFD có thể cần 2–3 code unit; một emoji nhiều thành phần có thể cần 4–11 code unit hoặc nhiều hơn. Giới hạn theo **length()** có thể từ chối đầu vào hợp lý hoặc cắt không trọn cụm. Hãy chọn đơn vị đo dựa trên yêu cầu cụ thể.

**Lỗi 2 — Tưởng codePointCount() cho số emoji hiển thị.** **“👩‍💻”** là ba code point nhưng thường được vẽ thành một biểu tượng. Với giao diện nhập liệu, cần phân đoạn grapheme.

**Lỗi 3 — Cắt giữa surrogate pair.** **substring()** nhận UTF-16 offset; giá trị cắt tùy ý có thể tạo String chứa surrogate lẻ. Khi thao tác theo code point, dùng **offsetByCodePoints()**; khi thao tác theo ký tự hiển thị, dùng ranh giới grapheme.

**Lỗi 4 — So sánh hai chữ tiếng Việt nhìn giống nhau bằng equals() mà không xét normalization.** NFC và NFD có thể khác số code unit nên **equals()** trả về **false**. Chỉ chuẩn hóa nếu chính sách dữ liệu yêu cầu; phải bảo toàn bản gốc nếu có yêu cầu về byte chính xác.

**Lỗi 5 — Đổi NFD rồi xóa mọi dấu để “chuẩn hóa”.** Việc này là **bỏ dấu**, có thể làm mất ý nghĩa từ tiếng Việt. NFC/NFD đúng nghĩa Unicode không có mục tiêu xóa dấu; các thao tác so khớp bỏ dấu cần quy định riêng.

**Lỗi 6 — Coi String.length() là số byte UTF-8.** Emoji **😊** có **length()==2** nhưng UTF-8 là **4 byte**. Để kiểm tra giới hạn truyền/lưu, hãy mã hóa bằng charset xác định rồi đếm byte.

**Lỗi 7 — Tin rằng mọi môi trường đều hiển thị emoji giống nhau.** Unicode định nghĩa code point và nhiều quy tắc phân đoạn; hình ảnh cuối cùng phụ thuộc font, trình dựng chữ và hỗ trợ emoji của nền tảng. Cần thử nghiệm các chuỗi ZWJ, modifier, cờ và ký tự tổ hợp ở môi trường mục tiêu.

**Lỗi 8 — Đồng nhất code point với mọi scalar Unicode hợp lệ.** Java String có thể chứa **unpaired surrogate** do thao tác sai hoặc dữ liệu không hợp lệ. **codePointCount()** vẫn xử lý số vị trí theo API, nhưng điều đó không chứng minh chuỗi Unicode được mã hóa hợp lệ. Khi cần chuẩn Unicode nghiêm ngặt, phải kiểm tra đầu vào và chính sách thay thế/từ chối.

## 10. Câu hỏi tự kiểm tra

1. Tại sao hai cách biểu diễn chữ **ắ** có thể có **length()** bằng 1 và 3? — Vì một dạng dùng code point dựng sẵn, một dạng gồm chữ cơ sở và hai combining mark.
2. Vì sao **"😊".length() == 2** nhưng **codePointCount(...) == 1**? — Vì code point ngoài BMP dùng một surrogate pair trong UTF-16.
3. Vì sao **"👩‍💻"** có 3 code point nhưng có thể là 1 grapheme? — Vì các thành phần được nối bằng ZWJ thành một extended grapheme cluster.
4. Khi nào chọn **Normalizer.Form.NFC**? — Khi nghiệp vụ cần quy về dạng Unicode canonical nhất quán trước so sánh/xử lý; cần quy định cùng chính sách cho mọi dữ liệu liên quan.
5. Giới hạn 200 byte UTF-8 có nên dùng **s.length() <= 200** không? — Không. Cần đo byte UTF-8 sau khi áp dụng chuẩn hóa (nếu chính sách có).
6. Regex Java nào dùng để tìm các extended grapheme cluster? — **Pattern.compile("\\X")**, sau đó duyệt từng **Matcher.find()** hoặc dùng **results()**.
7. Vì sao phải quan tâm Unicode version của JDK? — Vì dữ liệu thuộc tính và phân đoạn có thể thay đổi khi Unicode cập nhật, nhất là với emoji mới.

## 11. Ghi nhớ

- **length() = UTF-16 code unit**, phù hợp với chỉ số của các API String nhưng không biểu thị đúng số ký tự nhìn thấy.
- **codePointCount() = số code point được đếm theo biểu diễn UTF-16**, chưa phải số biểu tượng emoji hay grapheme.
- **Grapheme cluster** là đơn vị hữu ích khi xử lý dấu tiếng Việt, emoji modifier, ZWJ và cờ trong giao diện nhập liệu.
- **NFC/NFD** có thể làm thay đổi độ dài của văn bản tiếng Việt mà vẫn giữ biểu diễn tương đương chuẩn về Unicode.
- **UTF-8 byte length** là phép đo riêng biệt, cần cho giới hạn truyền tải và lưu trữ.

Tài liệu kỹ thuật nền tảng: **java.lang.String**, **java.text.Normalizer**, **java.util.regex.Pattern** của Java SE 17 và **Unicode Standard Annex #29 — Unicode Text Segmentation**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="unicode-utf16-005">String có thể chứa surrogate không ghép cặp</a>

<details>
<summary>Xem chi tiết</summary>

# Java String có thể chứa surrogate không ghép cặp

Trong Java, `String` lưu một **dãy đơn vị mã UTF-16 (UTF-16 code units)**. Mỗi đơn vị mã tương ứng một `char` 16 bit. Java **không bắt buộc** mọi `String` đều biểu diễn một chuỗi ký tự Unicode hợp lệ: một chuỗi vẫn có thể chứa **high surrogate** hoặc **low surrogate** đứng riêng lẻ, không ghép đúng cặp. Tình trạng này gọi là *unpaired surrogate* hoặc *unpaired surrogate code unit*.

Đây là điểm quan trọng khi cắt chuỗi, duyệt chuỗi, tính độ dài, so sánh và chuyển mã sang UTF-8. Một `String` có thể hoàn toàn hợp lệ về **kiểu Java**, nhưng chứa một dãy UTF-16 **không well-formed** (*ill-formed UTF-16*).

## 1. Phân biệt `char`, UTF-16 code unit, code point và Unicode scalar value

- **`char` trong Java:** giá trị nguyên không dấu 16 bit, từ `0x0000` đến `0xFFFF`. `String.length()` đếm số đơn vị mã `char` này, không đếm số ký tự hiển thị.
- **Unicode code point:** vị trí trong không gian mã `U+0000` đến `U+10FFFF`. Java thường biểu diễn code point bằng `int`.
- **Unicode scalar value:** code point **không thuộc** dải surrogate `U+D800`–`U+DFFF`; đây là những giá trị có thể mã hóa thành một ký tự Unicode riêng trong UTF-8.
- **UTF-16:** scalar value ở BMP không thuộc vùng surrogate dùng một đơn vị mã; scalar value từ `U+10000` đến `U+10FFFF` dùng **hai đơn vị mã**, lần lượt là high surrogate và low surrogate.

Các khoảng surrogate:

| Nhóm | Khoảng giá trị UTF-16 | Vai trò |
|---|---|---|
| High surrogate (*leading surrogate*) | `U+D800`–`U+DBFF` | Đơn vị mã đầu tiên của cặp |
| Low surrogate (*trailing surrogate*) | `U+DC00`–`U+DFFF` | Đơn vị mã thứ hai của cặp |
| Cặp hợp lệ | High rồi đến Low | Cùng biểu diễn một scalar value ngoài BMP |
| Không ghép cặp | High không theo sau bởi Low; hoặc Low không có High đi ngay trước | Không tạo được một scalar value từ đơn vị mã đó |

Ví dụ emoji 😀 có code point `U+1F600`. Trong UTF-16, nó dùng hai `char`: `U+D83D` (*high*) và `U+DE00` (*low*). Vì thế, một `String` chỉ chứa emoji này có `length() == 2`, nhưng `codePointCount(0, length()) == 1`.

Công thức UTF-16 khi mã hóa code point ngoài BMP (chỉ để hiểu cơ chế):

```text
x    = codePoint - 0x10000
high = 0xD800 + (x >>> 10)
low  = 0xDC00 + (x & 0x3FF)
```

Các code unit surrogate **không đại diện cho ký tự độc lập**. High `U+D83D` đứng một mình không phải là emoji 😀. Không nên nhầm mã surrogate `U+D83D` với code point emoji `U+1F600`.

## 2. Vì sao Java vẫn cho phép một `String` chứa surrogate lẻ?

API `String` hoạt động dựa trên chuỗi đơn vị mã UTF-16, và một số thao tác nhận/trả về các đơn vị mã đó mà không bắt buộc kiểm tra tính well-formed của cả chuỗi. Một `char` có thể có giá trị nằm trong khoảng surrogate; Java không cấm đưa `char` đó vào `String`.

Có ít nhất ba cách làm xuất hiện surrogate không ghép cặp:

### Tạo trực tiếp từ một `char`

```java
char high = (char) 0xD83D;
char low = (char) 0xDE00;

String onlyHigh = String.valueOf(high);
String onlyLow = new String(new char[] {low});
```

Mỗi chuỗi trên dài một đơn vị mã, nhưng không tạo thành cặp surrogate hoàn chỉnh.

### Cắt giữa một cặp đúng

```java
String emoji = new String(Character.toChars(0x1F600)); // Hai UTF-16 code units
String firstHalf = emoji.substring(0, 1);              // High surrogate đứng riêng
String secondHalf = emoji.substring(1, 2);             // Low surrogate đứng riêng
String restored = firstHalf + secondHalf;               // Cặp được ghép lại đúng thứ tự
```

Cả `substring` lẫn `charAt` dùng **chỉ số đơn vị mã**. Chúng không bảo đảm ranh giới trùng với ranh giới code point. `firstHalf` và `secondHalf` đều là `String` có thể được tạo và lưu bình thường, dù mỗi chuỗi là UTF-16 không well-formed. Khi ghép đúng thứ tự, `restored` lại chứa cặp hợp lệ.

### Thêm đơn vị mã bằng `StringBuilder` hoặc nhận dữ liệu đã có sẵn

```java
StringBuilder builder = new StringBuilder();
builder.append((char) 0xD83D);
builder.append('X');
String malformed = builder.toString();
```

Trong `malformed`, high surrogate đi trước `X`, không có low surrogate tương ứng. Dữ liệu cắt từ hệ thống khác hoặc các API thao tác theo `char` cũng có thể mang tình trạng này. Không phải việc tạo thành công đối tượng `String` là đã xác nhận Unicode well-formed.

Lưu ý: sau khi cắt hoặc ghép chuỗi, cùng một đơn vị mã có thể chuyển từ trạng thái lẻ sang hợp lệ hoặc ngược lại tùy **hai đơn vị mã liền kề**, vì điều kiện ghép cặp phụ thuộc vào thứ tự.

## 3. Các API đếm và duyệt chuỗi có hành vi gì?

| API | Đơn vị và hành vi | Điều cần nhớ |
|---|---|---|
| `length()` | Số UTF-16 code units | Một code point ngoài BMP thường chiếm 2 |
| `charAt(i)` | Lấy một code unit ở chỉ số `i` | Có thể trả về một nửa cặp |
| `substring(begin, end)` | Cắt theo chỉ số code unit | Có thể tách đôi cặp |
| `chars()` | Stream các giá trị `char` mở rộng thành `int` | Không ghép surrogate pair |
| `codePointAt(i)` | Ghép High+Low khi có cặp hợp lệ tại vị trí đó; nếu không, trả về giá trị của một code unit | Có thể trả về giá trị thuộc dải surrogate |
| `codePoints()` | Ghép các cặp hợp lệ thành một `int`; với đơn vị mã không ghép cặp, phát ra chính giá trị code unit đó | **Không** phải phép kiểm tra Unicode well-formed |
| `codePointCount(begin, end)` | Đếm cặp hợp lệ là một, code unit không ghép cặp là một | Kết quả vẫn được trả về khi chuỗi ill-formed |
| `offsetByCodePoints(index, delta)` | Di chuyển theo cách Java nhận diện code point trong chuỗi UTF-16 | Hữu ích để tránh cắt cặp hợp lệ nhưng không sửa dữ liệu đã hỏng |

Một điểm dễ nhầm: `Character.isValidCodePoint(0xD83D)` trả về `true` vì giá trị nằm trong khoảng `0x0000`–`0x10FFFF` được API này chấp nhận. Tuy nhiên `U+D83D` **không phải Unicode scalar value** vì nó thuộc vùng surrogate. Do đó, **chỉ kiểm tra `isValidCodePoint` chưa đủ** để xác nhận có thể mã hóa như một ký tự Unicode độc lập.

Có thể kiểm tra một giá trị `int` là scalar value bằng điều kiện:

```java
static boolean isUnicodeScalarValue(int cp) {
    return Character.isValidCodePoint(cp)
            && !(cp >= 0xD800 && cp <= 0xDFFF);
}
```

Điều kiện đó áp dụng cho **một code point dạng `int`**. Với một `String`, phải duyệt và kiểm tra sự ghép cặp của các đơn vị mã UTF-16.

## 4. Kiểm tra một `String` có well-formed UTF-16 hay không

Quy tắc kiểm tra từ trái sang phải:

1. Gặp **high surrogate**, phải có ký tự kế tiếp và ký tự đó phải là **low surrogate**; tiêu thụ cả hai.
2. Gặp **low surrogate** mà chưa đi cùng high ngay trước nó, chuỗi **không hợp lệ**.
3. Gặp `char` không thuộc hai nhóm trên thì đi tiếp bình thường.
4. Hết chuỗi mà không có lỗi thì dãy UTF-16 well-formed; chuỗi rỗng cũng hợp lệ.

Với `n = s.length()`, thuật toán chạy `O(n)` và sử dụng `O(1)` bộ nhớ bổ sung.

### Ví dụ Java 17+ chạy độc lập

Lưu toàn bộ chương trình vào **`UnpairedSurrogateDemo.java`**:

```java
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;

public class UnpairedSurrogateDemo {
    public static boolean isWellFormedUtf16(String s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);

            if (Character.isHighSurrogate(ch)) {
                if (i + 1 == s.length()
                        || !Character.isLowSurrogate(s.charAt(i + 1))) {
                    return false;
                }
                i++; // Đã xử lý cả high và low surrogate
            } else if (Character.isLowSurrogate(ch)) {
                return false; // Low surrogate đứng riêng
            }
        }
        return true;
    }

    private static String units(String s) {
        StringJoiner result = new StringJoiner(" ");
        for (int i = 0; i < s.length(); i++) {
            result.add(String.format("U+%04X", (int) s.charAt(i)));
        }
        return result.toString();
    }

    private static String codePointValues(String s) {
        StringJoiner result = new StringJoiner(" ");
        s.codePoints().forEach(cp ->
                result.add(String.format("U+%04X", cp)));
        return result.toString();
    }

    private static String utf8EncodingStatus(String s) {
        CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            encoder.encode(CharBuffer.wrap(s));
            return "OK";
        } catch (CharacterCodingException ex) {
            return "malformed input";
        }
    }

    public static void main(String[] args) {
        String emoji = new String(Character.toChars(0x1F600));
        String high = emoji.substring(0, 1);
        String low = emoji.substring(1, 2);
        String restored = high + low;
        String mixed = "A" + high + "B";

        System.out.println("emoji.length(): " + emoji.length());
        System.out.println("emoji.codePointCount(): "
                + emoji.codePointCount(0, emoji.length()));
        System.out.println("emoji units: " + units(emoji));
        System.out.println("high units: " + units(high));
        System.out.println("low units: " + units(low));
        System.out.println("high codePoints: " + codePointValues(high));
        System.out.println("emoji well-formed: " + isWellFormedUtf16(emoji));
        System.out.println("high well-formed: " + isWellFormedUtf16(high));
        System.out.println("low well-formed: " + isWellFormedUtf16(low));
        System.out.println("restored well-formed: " + isWellFormedUtf16(restored));
        System.out.println("mixed well-formed: " + isWellFormedUtf16(mixed));
        System.out.println("UTF-8 emoji: " + utf8EncodingStatus(emoji));
        System.out.println("UTF-8 high: " + utf8EncodingStatus(high));
        System.out.println("high isValidCodePoint: "
                + Character.isValidCodePoint(high.charAt(0)));
        System.out.println("high isSurrogate: "
                + Character.isSurrogate(high.charAt(0)));
    }
}
```

Lệnh biên dịch và chạy:

```bash
javac -encoding UTF-8 UnpairedSurrogateDemo.java
java UnpairedSurrogateDemo
```

Kết quả:

```text
emoji.length(): 2
emoji.codePointCount(): 1
emoji units: U+D83D U+DE00
high units: U+D83D
low units: U+DE00
high codePoints: U+D83D
emoji well-formed: true
high well-formed: false
low well-formed: false
restored well-formed: true
mixed well-formed: false
UTF-8 emoji: OK
UTF-8 high: malformed input
high isValidCodePoint: true
high isSurrogate: true
```

**Đọc kết quả:** `high.codePoints()` vẫn tạo được một phần tử số nguyên `0xD83D`; điều đó **không xác nhận chuỗi well-formed**. Bộ mã hóa UTF-8 ở chế độ `REPORT` từ chối surrogate lẻ vì nó không biểu diễn một Unicode scalar value. Khi `high` và `low` được ghép đúng thứ tự, cùng hai code unit lại tạo một cặp hợp lệ.

Hàm `isWellFormedUtf16` kiểm tra tính hợp lệ của **chuỗi đơn vị mã**, chứ không kiểm tra dữ liệu có ý nghĩa ngôn ngữ hoặc có hiển thị được dưới font hiện tại hay không.

## 5. Hậu quả khi mã hóa sang UTF-8 hoặc trao đổi dữ liệu

UTF-8 mã hóa các Unicode scalar value. Một surrogate đứng riêng lẻ trong `String` không có biểu diễn UTF-8 hợp lệ như một ký tự độc lập. Khi chuyển đổi, kết quả phụ thuộc **API và chính sách xử lý dữ liệu lỗi**:

- **Chính sách `REPORT`:** bộ mã hóa báo lỗi `MalformedInputException` (một loại `CharacterCodingException`), như chương trình trên. Đây là lựa chọn phù hợp khi cần phát hiện dữ liệu không hợp lệ.
- **Chính sách `REPLACE`:** bộ mã hóa thay phần không hợp lệ bằng các byte thay thế được cấu hình; lúc đó dữ liệu sau chuyển đổi không còn giữ nguyên surrogate lẻ. Byte thay thế của encoder không nên tự giả định luôn là một ký tự Unicode cố định.
- **Các hàm tiện ích như `getBytes(StandardCharsets.UTF_8)`:** có thể thay thế dữ liệu lỗi thay vì báo lỗi; không dùng kết quả thành công của hàm này làm bằng chứng rằng chuỗi ban đầu well-formed.

Ở chiều ngược lại, khi **giải mã** byte UTF-8 lỗi, bộ giải mã có thể *report* hoặc thay bằng ký tự `U+FFFD` (*replacement character*) tùy API/chính sách. **U+FFFD là một scalar value hợp lệ**, khác hoàn toàn với high surrogate/low surrogate đứng riêng. Không được gọi surrogate lẻ chính là U+FFFD.

Quá trình tuần tự hóa sang JSON, XML, giao tiếp API hay lưu trữ cũng cần xét hợp đồng của thư viện nhận dữ liệu. Một số thư viện có thể từ chối, thay thế, hoặc biểu diễn escape theo quy tắc riêng; không giả định mọi hệ thống sẽ tự xử lý giống nhau. Nếu hệ thống yêu cầu Unicode well-formed, hãy kiểm tra hoặc đặt chính sách xử lý lỗi rõ ràng **trước ranh giới truyền dữ liệu**.

### Có nên tự động thay surrogate lẻ?

Tùy yêu cầu nghiệp vụ:

- **Từ chối** dữ liệu không well-formed: phù hợp nếu giữ nguyên nghĩa và tính nhất quán dữ liệu quan trọng.
- **Thay mỗi đơn vị mã không ghép cặp bằng U+FFFD:** phù hợp khi cần tiếp tục hiển thị và chấp nhận mất thông tin; phải làm đúng từng cặp/đơn vị mã.
- **Giữ nguyên** dãy code unit để điều tra hoặc xử lý nội bộ: chỉ nên làm khi các bước kế tiếp hiểu rõ đặc tính UTF-16 không well-formed.

Không sửa bằng cách xóa *mọi* `char` thuộc dải surrogate: làm vậy sẽ xóa cả **cặp hợp lệ** và phá hỏng mọi ký tự ngoài BMP. Cũng không nên nối tùy ý surrogate lẻ với bất kỳ surrogate nào ở xa: một cặp hợp lệ phải là **high ngay trước low**.

## 6. Cắt chuỗi an toàn theo ranh giới code point

Nếu chuỗi đầu vào đã well-formed và cần cắt một số code point đầu tiên, `offsetByCodePoints` giúp tìm chỉ số UTF-16 đúng ranh giới cặp:

```java
String text = "A" + new String(Character.toChars(0x1F600)) + "B";
int end = text.offsetByCodePoints(0, 2);
String prefix = text.substring(0, end); // Chứa A và emoji 😀
System.out.println(prefix.codePointCount(0, prefix.length())); // 2
```

Nếu thay bằng `text.substring(0, 2)`, kết quả gồm `A` và **high surrogate của emoji**, thiếu low surrogate: đây là chuỗi ill-formed. Một phép cắt theo số code point cũng **không tự sửa** chuỗi đầu vào vốn đã có surrogate lẻ.

Ngoài ra, *code point không đồng nhất với ký tự người dùng nhìn thấy* (*grapheme cluster*). Ví dụ chữ cái kèm dấu kết hợp hoặc một emoji được ghép từ nhiều code point có thể cần xử lý phân đoạn văn bản cấp cao hơn. `offsetByCodePoints` giải quyết **ranh giới UTF-16 surrogate pair**, chưa giải quyết toàn bộ quy tắc phân đoạn grapheme.

## 7. Những nhầm lẫn phổ biến

| Khẳng định/giả định | Vì sao sai | Cách nghĩ đúng |
|---|---|---|
| “`String` luôn chứa Unicode hợp lệ.” | `String` vẫn chứa được surrogate lẻ. | Khi cần tính hợp lệ Unicode, kiểm tra UTF-16 well-formed riêng. |
| “`char` luôn là một ký tự hiển thị.” | Nó chỉ là một code unit 16 bit. | Ký tự ngoài BMP có hai `char`; grapheme có thể có nhiều code point. |
| “`length()` đếm số code point.” | `length()` đếm UTF-16 code units. | Dùng `codePointCount` cho cách đếm của API Java; lưu ý surrogate lẻ vẫn được đếm. |
| “`codePoints()` chỉ phát Unicode scalar value.” | Stream này có thể phát giá trị surrogate lẻ. | Kiểm tra well-formed UTF-16 hoặc loại dải `D800`–`DFFF` khi xét scalar value. |
| “`Character.isValidCodePoint` là đủ để xác thực.” | Nó trả về `true` cho số trong dải surrogate. | Loại trừ `U+D800`–`U+DFFF` khi kiểm tra một scalar value. |
| “Cắt `substring` luôn ở giữa các ký tự đầy đủ.” | Ranh giới được tính theo code unit. | Cân nhắc `offsetByCodePoints` nếu mục tiêu là code point. |
| “Emoji biến thành `?`/`�` chỉ là vấn đề font.” | Có thể đã xảy ra thay thế dữ liệu trong lúc chuyển mã lỗi. | Phân biệt code unit gốc, byte được mã hóa, decoder và font. |
| “Xóa mọi surrogate là sửa dữ liệu.” | Xóa cả các cặp hợp lệ. | Duyệt từng cặp, xử lý riêng surrogate **không ghép cặp**. |

## 8. Tình huống luyện tập

Cho một chuỗi được tạo như sau:

```java
String face = new String(Character.toChars(0x1F600));
String a = face.substring(0, 1);
String b = face.substring(1, 2);
String c = "x" + a + b + "y";
String d = a + "x" + b;
```

1. `a.length()`, `b.length()` và `face.length()` lần lượt là bao nhiêu?
2. Các chuỗi `face`, `a`, `b`, `c` và `d` có well-formed UTF-16 không?
3. `c.codePointCount(0, c.length())` có giá trị bao nhiêu?
4. Nếu mã hóa `d` sang UTF-8 bằng một `CharsetEncoder` với `CodingErrorAction.REPORT`, thao tác có thành công không?

**Đáp án:** (1) `1`, `1`, `2`. (2) `face` và `c` well-formed; `a`, `b` và `d` không well-formed vì chứa surrogate lẻ. (3) `3` code point: `x`, emoji, `y`. (4) Không; high và low bị ngăn bởi `x` nên không tạo cặp, và encoder báo malformed input.

**Ghi nhớ:** Kiểu `String` của Java chứa một chuỗi UTF-16 code units; sự tồn tại của chuỗi **không đảm bảo** chuỗi đó là UTF-16 well-formed. Một cặp surrogate hợp lệ cần đúng thứ tự **high + low liền kề**. Khi cắt hoặc duyệt, biết API đang xử lý `char` hay code point; khi chuyển sang UTF-8 hay giao tiếp với hệ thống khác, xác định rõ chính sách **báo lỗi, thay thế hoặc từ chối** surrogate không ghép cặp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="unicode-utf16-006">Character.toChars, charCount và các thao tác trên code point</a>

<details>
<summary>Xem chi tiết</summary>

# `Character.toChars`, `charCount` và thao tác trên Unicode code point trong Java

## 1. Học xong bài này làm được gì?

Bạn sẽ chuyển đổi đúng giữa **Unicode code point** (`int`) và **UTF-16 code unit** (`char`), dùng `Character.toChars`/`Character.charCount` để xây dựng và duyệt chuỗi, rồi chọn đúng các API `String`, `Character` và `StringBuilder` khi xử lý văn bản có ký tự ngoài Basic Multilingual Plane (BMP). Bài cũng giải thích các lỗi phổ biến: dùng `charAt` như thể luôn trả về một ký tự đầy đủ, tăng index thêm 1 khi đi qua emoji, cắt đôi surrogate pair, và nhầm số code point với số ký tự người dùng nhìn thấy.

## 2. Nền tảng: code point khác `char`

Unicode gán **code point** có dạng `U+XXXX` cho các giá trị trong khoảng `U+0000` đến `U+10FFFF`. Java thường dùng `int` để giữ một giá trị code point. Trong khi đó, mỗi `char` là một **đơn vị mã UTF-16 rộng 16 bit** (code unit), có giá trị từ `0x0000` đến `0xFFFF`.

Trong UTF-16:

| Dải giá trị | Số `char` để biểu diễn | Ví dụ |
| --- | ---: | --- |
| BMP: `U+0000` đến `U+FFFF` (không tính tính hợp lệ riêng của surrogate khi dùng như ký tự độc lập) | 1 code unit | `U+0041` → `A`, `U+03A9` → `Ω` |
| Supplementary: `U+10000` đến `U+10FFFF` | 2 code unit tạo thành **surrogate pair** | `U+1F600` → 😀 |
| Giá trị ngoài `0x0000..0x10FFFF` | Không phải Unicode code point hợp lệ | `0x110000` |

Hai code unit của một surrogate pair có vai trò khác nhau:

- **High surrogate** nằm trong `U+D800..U+DBFF`.
- **Low surrogate** nằm trong `U+DC00..U+DFFF`.
- Cặp high trước, low sau mã hóa **một** supplementary code point. Ví dụ `U+1F600` → `U+D83D U+DE00`.
- Các code point ở dải `U+D800..U+DFFF` là **surrogate code points**: Java `Character.isValidCodePoint` vẫn coi giá trị số đó nằm trong dải code point hợp lệ, nhưng một surrogate đứng riêng **không phải Unicode scalar value**, không thể đại diện cho một ký tự Unicode độc lập hợp lệ khi trao đổi văn bản.

Phân biệt ba cách đếm:

| Câu hỏi | Java API | Với chuỗi `"A😀B"` |
| --- | --- | ---: |
| Có bao nhiêu UTF-16 code unit? | `s.length()` | 4 |
| Có bao nhiêu Unicode code point trong vùng? | `s.codePointCount(0, s.length())` | 3 |
| Người dùng nhìn thấy bao nhiêu cụm ký tự (grapheme cluster)? | Cần thuật toán phân cụm phù hợp | 3 trong ví dụ này |

Ví dụ `"e\u0301"` có hai code point (chữ `e` và dấu sắc kết hợp) dù thường hiển thị thành **một cụm ký tự**. Do đó code point không đồng nghĩa với grapheme cluster; `String.length()` càng không phải “độ dài theo ký tự người đọc”.

## 3. `Character.toChars(int codePoint)`: từ code point sang UTF-16

Phương thức:

~~~java
public static char[] Character.toChars(int codePoint)
~~~

Trả về một mảng `char[]` biểu diễn code point ở UTF-16:

- Nếu thuộc BMP, trả về mảng dài **1** với một code unit bằng giá trị code point.
- Nếu thuộc supplementary plane, trả về mảng dài **2**, theo thứ tự **high surrogate rồi low surrogate**.
- Nếu `codePoint` nằm ngoài `U+0000..U+10FFFF`, ném `IllegalArgumentException`.
- Trường hợp số code point là một giá trị surrogate BMP, phương thức vẫn trả về một `char` surrogate đơn lẻ; nó không tự kiểm tra yêu cầu “Unicode scalar value” trong dữ liệu cần trao đổi.

Ví dụ ngắn:

~~~java
char[] a = Character.toChars(0x0041);   // {'A'}, length = 1
char[] omega = Character.toChars(0x03A9); // {'Ω'}, length = 1
char[] face = Character.toChars(0x1F600); // {0xD83D, 0xDE00}, length = 2

String emojiText = new String(face);    // một code point, hai char UTF-16
String alsoEmoji = Character.toString(0x1F600); // có từ Java 11
~~~

Đừng ép `(char) 0x1F600` để “đổi emoji sang `char`”: phép ép làm mất các bit cao, không tạo surrogate pair đúng. Nếu mục tiêu là một `String`, sử dụng `new String(Character.toChars(cp))` hoặc `Character.toString(cp)` (Java 11+) thuận tiện hơn.

### Ghi vào mảng `char[]` có sẵn

Overload thứ hai:

~~~java
public static int Character.toChars(int codePoint, char[] dst, int dstIndex)
~~~

Nó ghi **1 hoặc 2** đơn vị UTF-16 bắt đầu tại `dstIndex` và trả về **số đơn vị thực sự đã ghi**, không trả về code point:

~~~java
char[] buffer = new char[4];
buffer[0] = '[';

int written = Character.toChars(0x1F600, buffer, 1); // written == 2
buffer[1 + written] = ']';

String wrapped = new String(buffer); // "[😀]"
~~~

Phải bảo đảm mảng đích có đủ chỗ, nhất là với supplementary code point. Overload này có thể ném `NullPointerException` nếu `dst` null, `IndexOutOfBoundsException` nếu index/capacity không hợp lệ và `IllegalArgumentException` nếu code point ngoài khoảng hợp lệ. Cách này thích hợp khi xây dựng bộ đệm UTF-16 có kiểm soát; với văn bản bình thường, `StringBuilder.appendCodePoint` thường đơn giản hơn.

## 4. `Character.charCount(int codePoint)`: cần bao nhiêu `char`?

`Character.charCount(cp)` trả về:

- `1` nếu `cp < 0x10000`.
- `2` nếu `cp >= 0x10000`.

Nó **không xác thực** `cp` có thuộc dải Unicode code point hay không:

~~~java
Character.charCount(0x0041);   // 1
Character.charCount(0x1F600);  // 2
Character.charCount(0x110000); // 2: vẫn trả về 2 dù giá trị không hợp lệ
Character.isValidCodePoint(0x110000); // false
~~~

Vì vậy `charCount` là hàm xác định **độ rộng UTF-16 dự kiến**, không phải hàm kiểm tra tính hợp lệ. Nếu `cp` lấy từ nguồn bên ngoài, hãy kiểm tra `Character.isValidCodePoint(cp)` trước khi gọi `toChars` hoặc `appendCodePoint`.

Khi `cp` được lấy bằng `s.codePointAt(offset)` tại một ranh giới code point hợp lệ, có thể dùng `offset += Character.charCount(cp)` để tiến sang code point tiếp theo.

## 5. Ví dụ Java 17+ đầy đủ, có thể biên dịch và chạy

Tạo tệp **`CodePointDemo.java`**:

~~~java
public class CodePointDemo {
    public static void main(String[] args) {
        int emoji = 0x1F600; // U+1F600, GRINNING FACE
        char[] pair = Character.toChars(emoji);
        String value = "A" + new String(pair) + "B";

        System.out.println("valid=" + Character.isValidCodePoint(emoji));
        System.out.println("supplementary=" + Character.isSupplementaryCodePoint(emoji));
        System.out.println("charCount=" + Character.charCount(emoji));
        System.out.printf("pair=U+%04X U+%04X%n", (int) pair[0], (int) pair[1]);
        System.out.println("pairValid=" + Character.isSurrogatePair(pair[0], pair[1]));
        System.out.printf("restored=U+%X%n", Character.toCodePoint(pair[0], pair[1]));

        System.out.println("length=" + value.length());
        System.out.println("codePointCount=" + value.codePointCount(0, value.length()));
        System.out.printf("at1=U+%X%n", value.codePointAt(1));
        System.out.printf("at2=U+%X%n", value.codePointAt(2));
        System.out.println("nextIndex=" + value.offsetByCodePoints(1, 1));

        for (int i = 0; i < value.length();) {
            int cp = value.codePointAt(i);
            System.out.printf("index=%d cp=U+%X%n", i, cp);
            i += Character.charCount(cp);
        }

        StringBuilder copy = new StringBuilder();
        value.codePoints().forEach(copy::appendCodePoint);
        System.out.println("roundTrip=" + value.contentEquals(copy));

        String decomposed = "e\u0301";
        System.out.println("combiningCodePoints="
                + decomposed.codePointCount(0, decomposed.length()));
    }
}
~~~

Lệnh chạy:

~~~bash
javac -encoding UTF-8 CodePointDemo.java
java CodePointDemo
~~~

`stdout` đã kiểm tra khi biên dịch/chạy:

~~~text
valid=true
supplementary=true
charCount=2
pair=U+D83D U+DE00
pairValid=true
restored=U+1F600
length=4
codePointCount=3
at1=U+1F600
at2=U+DE00
nextIndex=3
index=0 cp=U+41
index=1 cp=U+1F600
index=3 cp=U+42
roundTrip=true
combiningCodePoints=2
~~~

Giải thích:

1. `0x1F600` là supplementary code point hợp lệ. `toChars` tạo hai code unit: high `D83D` và low `DE00`. `isSurrogatePair` xác nhận thứ tự đúng; `toCodePoint` ghép cặp thành `1F600`.
2. Với `"A😀B"`, các **chỉ số UTF-16** là `0: A`, `1: high surrogate`, `2: low surrogate`, `3: B`. Vì vậy `length() == 4` nhưng `codePointCount(...) == 3`.
3. `codePointAt(1)` đứng ở **high surrogate** nên ghép với low tiếp theo, trả `U+1F600`. `codePointAt(2)` đứng ở **low surrogate** nên chỉ trả giá trị `U+DE00`; API không tìm lùi để ghép cặp.
4. `offsetByCodePoints(1, 1)` di chuyển **một code point** từ chỉ số UTF-16 `1` và trả chỉ số UTF-16 `3`; do đó vòng lặp có index `0`, `1`, `3`.
5. `String.codePoints()` cung cấp luồng `IntStream` theo code point; `StringBuilder.appendCodePoint` mã hóa từng `int` trở lại UTF-16. `roundTrip=true` cho thấy chuỗi gốc được tái tạo.
6. `e` cộng dấu sắc kết hợp tạo hai code point dù mắt người thường thấy một cụm ký tự.

## 6. Các API đi cùng code point và cách chọn

| API | Input/chỉ số | Ý nghĩa |
| --- | --- | --- |
| `Character.isValidCodePoint(cp)` | `int` | `true` khi `0 <= cp <= 0x10FFFF`; **không loại trừ riêng surrogate code points** |
| `Character.isBmpCodePoint(cp)` | `int` | Trong `U+0000..U+FFFF`, bao gồm dải surrogate |
| `Character.isSupplementaryCodePoint(cp)` | `int` | Trong `U+10000..U+10FFFF` |
| `Character.toChars(cp)` | `int` | Code point → mảng 1 hoặc 2 `char` UTF-16 |
| `Character.charCount(cp)` | `int` | Trả 1/2 theo ngưỡng `0x10000`; không validate |
| `Character.isHighSurrogate(ch)` / `isLowSurrogate(ch)` | `char` | Kiểm tra từng nửa cặp surrogate |
| `Character.isSurrogatePair(hi, lo)` | hai `char` | Xác thực cặp high-low |
| `Character.toCodePoint(hi, lo)` | hai `char` | Ghép thành `int`; **không tự xác thực cặp** |
| `Character.highSurrogate(cp)` / `lowSurrogate(cp)` | `int` supplementary | Lấy hai nửa UTF-16; với cp không phải supplementary, kết quả không được quy định để sử dụng |
| `s.charAt(i)` | UTF-16 index | Trả **một code unit**, có thể chỉ là nửa surrogate |
| `s.codePointAt(i)` | UTF-16 index | Đọc code point bắt đầu tại `i`; ghép cặp nếu `i` là high có low liền sau |
| `s.codePointBefore(i)` | UTF-16 index **ngay sau** ký tự cần đọc | Đọc code point liền trước `i`; ghép nếu ngay trước là low có high liền trước |
| `s.codePointCount(begin, end)` | Hai UTF-16 offset, `end` exclusive | Đếm code point trong khoảng; surrogate lẻ tính thành một |
| `s.offsetByCodePoints(i, delta)` | UTF-16 index + số code point cần dịch | Trả về **UTF-16 index mới**, `delta` có thể âm |
| `s.chars()` / `s.codePoints()` | Không nhận index | `IntStream` lần lượt theo **code unit** / **code point** |
| `sb.appendCodePoint(cp)` | `int` | Nối code point vào `StringBuilder` thành 1/2 code unit |

### Chuyển từ surrogate pair về code point

Khi đã có hai `char`:

~~~java
char high = '\uD83D';
char low = '\uDE00';

if (Character.isSurrogatePair(high, low)) {
    int cp = Character.toCodePoint(high, low); // 0x1F600
}
~~~

`Character.toCodePoint` **không kiểm tra** cặp đầu vào. Nếu không chắc hai `char` đúng thứ tự high-low, gọi `isSurrogatePair` trước để tránh tạo ra số sai.

Công thức toán học đằng sau việc ghép cặp hợp lệ:

~~~text
codePoint = 0x10000
          + (high - 0xD800) * 0x400
          + (low  - 0xDC00)
~~~

Thông thường nên dùng API tiêu chuẩn thay vì tự viết công thức và xử lý các ca biên.

### Duyệt theo chiều ngược

Nếu cần đi từ cuối chuỗi đến đầu chuỗi theo code point, `codePointBefore` và `charCount` cho một mẫu đối xứng:

~~~java
String s = "A" + Character.toString(0x1F600) + "B";

for (int end = s.length(); end > 0;) {
    int cp = s.codePointBefore(end);
    System.out.printf("U+%X%n", cp);
    end -= Character.charCount(cp);
}
~~~

Đoạn này in lần lượt `U+42`, `U+1F600`, `U+41`. Chú ý tham số `end` của `codePointBefore` nằm **sau** đơn vị cần đọc, còn `codePointAt` nhận vị trí bắt đầu.

## 7. Cắt chuỗi và xử lý chỉ số cho đúng

Mọi index của `String` trong các API thông thường như `charAt`, `substring` và `length` vẫn là **vị trí UTF-16**. “Lấy hai code point đầu” không đồng nghĩa với `substring(0, 2)` nếu phần đầu có ký tự supplementary.

Ví dụ:

~~~java
String s = "A" + Character.toString(0x1F600) + "B";

// Hai code point đầu: 'A' và U+1F600.
int end = s.offsetByCodePoints(0, 2);
String firstTwoCodePoints = s.substring(0, end); // "A😀", end == 3
~~~

Mẫu tổng quát khi các chỉ số code point không âm và nằm trong phạm vi chuỗi:

~~~java
static String sliceByCodePoints(String s, int from, int count) {
    int start = s.offsetByCodePoints(0, from);
    int end = s.offsetByCodePoints(start, count);
    return s.substring(start, end);
}
~~~

Hàm trên sử dụng trực tiếp hợp đồng của API: nếu `from` hay `count` gây truy cập ngoài biên, `offsetByCodePoints` ném `IndexOutOfBoundsException`. Với yêu cầu bảo vệ dữ liệu đầu vào, hãy xác thực thêm `from >= 0` và `count >= 0` theo nghiệp vụ. Đầu mút cắt được tính từ ranh giới code point, tránh cắt ngang một cặp surrogate hợp lệ. Đây vẫn **không phải** thuật toán cắt theo grapheme cluster.

**Cảnh báo biên phạm vi:** `codePointCount(begin, end)` hoạt động trên đoạn UTF-16 cho sẵn. Nếu `begin` hoặc `end` cắt ngay giữa high/low surrogate, API chỉ thấy phần trong đoạn và có thể đếm surrogate lẻ như một code point. Khi dùng với giao diện theo code point, hãy chuyển đổi và giữ các ranh giới hợp lệ trước.

## 8. Dữ liệu sai chuẩn: validation và surrogate không ghép cặp

Các phương thức trong `Character` không có cùng chính sách lỗi.

~~~java
System.out.println(Character.isValidCodePoint(0x110000)); // false
System.out.println(Character.charCount(0x110000));        // 2, không validate
// Character.toChars(0x110000); // ném IllegalArgumentException

System.out.println(Character.isValidCodePoint(0xD800));   // true
System.out.println(Character.isSurrogate((char) 0xD800));  // true
~~~

Dải surrogate `0xD800..0xDFFF` nằm trong khoảng số code point mà `Character.isValidCodePoint` chấp nhận, nhưng từng giá trị đơn lẻ **không phải Unicode scalar value**. Để từ chối cả số ngoài miền Unicode và surrogate code points khi hệ thống yêu cầu một scalar value:

~~~java
static boolean isUnicodeScalarValue(int cp) {
    return Character.isValidCodePoint(cp)
            && (cp < 0xD800 || cp > 0xDFFF);
}
~~~

Một chuỗi Java có thể chứa `char` surrogate **không được ghép cặp** (ví dụ dữ liệu bị cắt ngang hoặc do tạo trực tiếp). `codePointAt`, `codePointBefore`, `codePoints()` và `codePointCount` có quy tắc xử lý chúng: các surrogate lẻ được đọc hoặc đếm như từng giá trị `char` đơn lẻ, **không tự sửa chuỗi thành UTF-16 chuẩn**. Nếu ứng dụng cần xuất dữ liệu Unicode nghiêm ngặt, cần quy tắc kiểm tra/thay thế dữ liệu lỗi theo giao thức đích.

Ngoài ra, `Character.highSurrogate(cp)` và `lowSurrogate(cp)` chỉ nên dùng khi `Character.isSupplementaryCodePoint(cp)` đúng; với đầu vào không supplementary, API không bảo đảm một nửa surrogate có ý nghĩa. Tương tự, trước khi `toCodePoint(high, low)` nên xác thực bằng `isSurrogatePair`.

## 9. Lỗi thường gặp và cách sửa

**Lỗi 1 — duyệt `String` bằng `charAt(i)` rồi coi mỗi `char` là một ký tự Unicode.**

Sai khi có emoji hoặc chữ ở supplementary plane, vì `charAt` chỉ trả một code unit. Nếu mục đích là duyệt code point:

~~~java
for (int i = 0; i < s.length();) {
    int cp = s.codePointAt(i);
    // Xử lý cp như một Unicode code point
    i += Character.charCount(cp);
}
~~~

Hoặc dùng `s.codePoints().forEach(cp -> ...)` nếu không cần chỉ số UTF-16.

**Lỗi 2 — dùng `new String(new char[] {(char) cp})` cho mọi `cp`.** Phép ép xuống `char` chỉ giữ 16 bit; dùng `new String(Character.toChars(cp))` để hỗ trợ supplementary code point.

**Lỗi 3 — dùng `charCount(cp)` để xác nhận đầu vào.** Hàm này không kiểm tra hợp lệ; áp dụng `isValidCodePoint` và, nếu cần, loại surrogate code points riêng.

**Lỗi 4 — kiểm tra loại ký tự bằng overload `char`.** Ví dụ `Character.isLetter(char)` không thể xem toàn bộ một code point supplementary. Với văn bản có Unicode ngoài BMP, ưu tiên overload `Character.isLetter(int cp)` sau khi đọc bằng `codePointAt` hoặc `codePoints()`.

**Lỗi 5 — đếm code point để suy ra “số ký tự hiển thị” hoặc giới hạn độ dài tên người dùng.** Tổ hợp chữ+dấu, emoji biến thể, chuỗi nối ZWJ và cờ có thể gồm nhiều code point nhưng hiển thị thành một grapheme cluster. Nếu yêu cầu là cắt/đếm theo cụm ký tự người dùng nhận biết, dùng thuật toán phân đoạn grapheme theo Unicode thích hợp; `codePointCount` giải quyết bài toán khác.

**Lỗi 6 — ghép cặp surrogate mà không kiểm tra thứ tự.** `Character.toCodePoint` không xác minh cặp; hãy gọi `Character.isSurrogatePair` trước hoặc đọc trực tiếp code point qua `String`.

## 10. Câu hỏi tự kiểm tra

1. **`Character.toChars(0x1F600).length` và `Character.charCount(0x1F600)` trả về gì?**  
   Đều là `2`; một code point supplementary cần hai UTF-16 code unit.
2. **Vì sao `"A😀B".length()` là 4 nhưng `codePointCount(0, length)` là 3?**  
   Hai chữ BMP chiếm 1 `char` mỗi chữ, emoji chiếm 2 `char` nhưng chỉ một code point.
3. **`Character.charCount(0x110000)` trả về gì và có bảo đảm số hợp lệ không?**  
   Trả `2`; **không**. `Character.isValidCodePoint(0x110000)` là `false`, `toChars` ném ngoại lệ.
4. **`String.codePointAt(i)` nhận chỉ số code point hay chỉ số `char`?**  
   Nhận **chỉ số UTF-16**. Với high surrogate, nó có thể ghép cặp với low kế tiếp; bắt đầu ở low surrogate thì không ghép lùi.
5. **Khi cần cắt 5 code point đầu trong `String`, dùng gì?**  
   `int end = s.offsetByCodePoints(0, 5);` rồi `s.substring(0, end)`, với điều kiện chuỗi đủ 5 code point.
6. **`Character.isValidCodePoint(0xD800)` có đủ để coi ký tự là Unicode scalar không?**  
   Không. Giá trị nằm trong dải surrogate và phải được loại riêng nếu cần scalar value.

## 11. Tài liệu chuẩn

- [Java SE 17 — `Character` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html): `toChars`, `charCount`, `isValidCodePoint`, `isSurrogatePair`, `toCodePoint` và các overload nhận `int`.
- [Java SE 17 — `String` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html): `codePointAt`, `codePointBefore`, `codePointCount`, `offsetByCodePoints`, `chars` và `codePoints`.
- [Java SE 17 — `StringBuilder` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StringBuilder.html): `appendCodePoint`.
- [Unicode Standard — Encoding Forms](https://www.unicode.org/versions/latest/core-spec/chapter-3/): UTF-16, surrogate pairs, code points, scalar values.

**Ghi nhớ:** `char` là **một code unit UTF-16**, còn code point được biểu diễn bằng `int` và có thể cần **một hoặc hai** `char`. Dùng `toChars` để mã hóa, `charCount` để dịch chuyển đúng số code unit, `String.codePointAt`/`codePoints` để đọc và `offsetByCodePoints` để chuyển chỉ số trước khi cắt chuỗi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="unicode-utf16-007">UTF-16 của mô hình String khác với charset của tệp</a>

<details>
<summary>Xem chi tiết</summary>

# UTF-16 của mô hình String khác với charset của tệp trong Java

Khi học Java, người ta thường nói “`String` dùng UTF-16”, nhưng tệp văn bản lại có thể mang charset UTF-8, UTF-16LE hoặc một charset khác. **Hai phát biểu này mô tả hai tầng khác nhau**: mô hình thao tác với ký tự trong Java và cách biểu diễn văn bản thành **byte** khi lưu ra tệp hay truyền qua mạng. Nhầm lẫn hai tầng dẫn đến lỗi đếm độ dài, cắt chuỗi, đọc sai tiếng Việt, lỗi ký tự thay thế và chọn sai charset.

## 1. Phân biệt Unicode, mô hình String và charset

| Khái niệm | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| Unicode code point | Giá trị định danh một phần tử ký tự Unicode | `U+0041` là A, `U+00E9` là é, `U+1F600` là 😀 |
| UTF-16 code unit | Đơn vị mã 16 bit; một code point có thể chiếm một hoặc hai đơn vị | Emoji 😀 cần cặp surrogate `D83D DE00` |
| Mô hình `String` Java | Chỉ số và các API như `length()`, `charAt()` được định nghĩa dựa trên UTF-16 code unit | `"😀".length() == 2` |
| Charset của tệp | Quy tắc mã hóa/giải mã văn bản thành/từ các byte | UTF-8, UTF-16LE, UTF-16BE, Windows-1252 |

**Mô hình `String`** là cách API Java nhìn nhận chuỗi. Một `char` là một đơn vị mã UTF-16; `length()` tính số đơn vị mã; chỉ số `charAt()` và `substring()` cũng tính theo đơn vị mã này. **Charset của tệp** là lựa chọn tại điểm chuyển đổi từ chuỗi sang byte (encode) hoặc từ byte sang chuỗi (decode).

Vì thế, cùng một đối tượng `String` có thể được lưu thành tệp UTF-8 hoặc UTF-16LE. Khi đọc hai tệp bằng charset tương ứng, ta có thể khôi phục **cùng nội dung `String`**. Charset từng dùng để lưu tệp không trở thành thuộc tính cố định của `String`.

### “String dùng UTF-16” không có nghĩa “bộ nhớ luôn là char[]”

Đừng suy diễn rằng mọi JVM luôn giữ bên trong đối tượng `String` một mảng gồm hai byte cho mỗi `char`. OpenJDK từ Java 9 có tối ưu **Compact Strings**, cho phép biểu diễn gọn hơn đối với các chuỗi phù hợp Latin-1. Chi tiết bố trí bộ nhớ nội bộ tùy triển khai, còn các hành vi quan sát qua `String.length()`, `charAt()`, `codePointCount()` vẫn theo hợp đồng API liên quan UTF-16. Đặc biệt, byte nội bộ của `String` **không phải** byte ghi ra tệp.

## 2. Minh họa: một chuỗi nhưng ba cách đếm

Xét chuỗi:

~~~java
String text = "Aé😀";
~~~

| Ký tự | Unicode code point | UTF-16 code unit | Byte UTF-8 |
| --- | --- | --- | --- |
| A | `U+0041` | `0041` (1 unit) | `41` (1 byte) |
| é | `U+00E9` | `00E9` (1 unit) | `C3 A9` (2 byte) |
| 😀 | `U+1F600` | `D83D DE00` (2 unit) | `F0 9F 98 80` (4 byte) |
| **Tổng** | **3 code point** | **4 UTF-16 unit** | **7 byte UTF-8** |

Do đó:

- `text.length()` bằng **4**, vì UTF-16 cần bốn đơn vị mã.
- `text.codePointCount(0, text.length())` bằng **3**.
- `Character.isHighSurrogate(text.charAt(2))` bằng `true`: vị trí 2 là nửa đầu của cặp surrogate của emoji.
- `text.getBytes(StandardCharsets.UTF_8).length` bằng **7**.
- `text.getBytes(StandardCharsets.UTF_16LE).length` bằng **8** (4 đơn vị nhân 2 byte).

**Đừng đồng nhất code point với ký tự người đọc thấy.** Một cụm ký tự hiển thị có thể bao gồm nhiều code point, ví dụ chữ cái cộng dấu kết hợp hoặc emoji ghép. Với dữ liệu Unicode, đơn vị mã UTF-16, code point và cụm ký tự hiển thị (*grapheme cluster*) là ba khái niệm riêng.

## 3. Luồng chuyển đổi từ String sang tệp và ngược lại

~~~text
Nội dung Unicode, ví dụ "Aé😀"
               |
               v
     Java String (API UTF-16)
     length() / charAt() / substring()
               |
               | ENCODE: getBytes(charset)
               | hoặc Files.writeString(path, text, charset)
               v
     Dãy byte theo charset đã chọn
     UTF-8   : 41 C3 A9 F0 9F 98 80
     UTF-16LE: 41 00 E9 00 3D D8 00 DE
               |
               v
         Tệp trên ổ đĩa
               |
               | DECODE: new String(bytes, charset)
               | hoặc Files.readString(path, charset)
               v
     Java String (API vẫn theo UTF-16)
~~~

Encoding và decoding là **các thao tác chuyển đổi dữ liệu**, không phải thao tác thay đổi “loại” của một `String`. Byte được tạo từ UTF-8 và UTF-16LE có thể khác hoàn toàn, nhưng khi giải mã **đúng charset và dữ liệu hợp lệ** sẽ thu lại cùng chuỗi.

Đuôi tệp `.txt` không tự bảo đảm charset. Cần biết charset từ đặc tả định dạng, giao thức, metadata hoặc thỏa thuận với hệ thống tạo dữ liệu. Không thể suy ra một charset luôn chính xác cho mọi dãy byte.

## 4. Ví dụ Java 17+ chạy độc lập: kiểm tra UTF-16 và byte thực tế

Tạo tệp **StringCharsetDemo.java**:

~~~java
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;

public class StringCharsetDemo {
    public static void main(String[] args) throws Exception {
        String text = "Aé😀";

        System.out.println("UTF-16 units = " + text.length());
        System.out.println("Unicode code points = "
                + text.codePointCount(0, text.length()));
        System.out.println("high surrogate at index 2 = "
                + Character.isHighSurrogate(text.charAt(2)));

        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        byte[] utf16le = text.getBytes(StandardCharsets.UTF_16LE);
        byte[] utf16 = text.getBytes(StandardCharsets.UTF_16);
        HexFormat hex = HexFormat.ofDelimiter(" ").withUpperCase();

        System.out.println("UTF-8 bytes = " + utf8.length);
        System.out.println("UTF-8 hex = " + hex.formatHex(utf8));
        System.out.println("UTF-16LE bytes = " + utf16le.length);
        System.out.println("UTF-16LE hex = " + hex.formatHex(utf16le));
        System.out.println("UTF-16 bytes = " + utf16.length);
        System.out.println("UTF-16 hex = " + hex.formatHex(utf16));

        String decoded = new String(utf8, StandardCharsets.UTF_8);
        System.out.println("UTF-8 decode equals original = "
                + text.equals(decoded));

        Path tempFile = Files.createTempFile("string-charset-", ".txt");
        try {
            Files.writeString(tempFile, text, StandardCharsets.UTF_8);
            String loaded = Files.readString(tempFile, StandardCharsets.UTF_8);

            System.out.println("file round-trip = " + text.equals(loaded));
            System.out.println("file bytes equal UTF-8 = "
                    + Arrays.equals(Files.readAllBytes(tempFile), utf8));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
~~~

Biên dịch bằng JDK 17 trở lên và chạy:

~~~bash
javac -encoding UTF-8 StringCharsetDemo.java
java StringCharsetDemo
~~~

**Kết quả chuẩn:**

~~~text
UTF-16 units = 4
Unicode code points = 3
high surrogate at index 2 = true
UTF-8 bytes = 7
UTF-8 hex = 41 C3 A9 F0 9F 98 80
UTF-16LE bytes = 8
UTF-16LE hex = 41 00 E9 00 3D D8 00 DE
UTF-16 bytes = 10
UTF-16 hex = FE FF 00 41 00 E9 D8 3D DE 00
UTF-8 decode equals original = true
file round-trip = true
file bytes equal UTF-8 = true
~~~

Giải thích từng kết quả:

1. **4 UTF-16 units** phản ánh mô hình API của `String`, không phải 4 byte trong tệp.
2. **7 byte UTF-8** và **8 byte UTF-16LE** đều mã hóa cùng ba code point.
3. Với `StandardCharsets.UTF_16`, bộ mã hóa Java ghi BOM `FE FF`, sau đó ghi UTF-16 big-endian: `00 41 00 E9 D8 3D DE 00`. Do đó tổng cộng **10 byte**. BOM nằm trong **đầu ra byte**, không phải hai byte ẩn mà mọi `String` đều có.
4. `new String(utf8, StandardCharsets.UTF_8)` giải mã về văn bản, nên `equals()` trả về `true`.
5. `Files.writeString` lưu **byte UTF-8** theo charset được truyền; chương trình đọc tệp bằng UTF-8, đối chiếu nội dung và cả byte thực tế, rồi xóa tệp tạm trong `finally`.

Cờ `javac -encoding UTF-8` chỉ báo cho trình biên dịch rằng **tệp mã nguồn Java** được lưu bằng UTF-8. Nó không đặt charset tự động cho mọi tệp dữ liệu do chương trình tạo; charset tệp trong ví dụ đến từ đối số `StandardCharsets.UTF_8` của API I/O.

## 5. UTF-16 của API và charset UTF-16 của tệp khác nhau ở đâu?

| Charset | Khi xuất dữ liệu thành byte | Chi tiết cần chú ý |
| --- | --- | --- |
| UTF-8 | Mỗi code point thường dùng 1–4 byte | Không có byte order của đơn vị 16-bit |
| UTF-16LE | Mỗi UTF-16 code unit dùng 2 byte, byte thấp trước | `U+0041` thành `41 00`; encoder Java không tự ghi BOM |
| UTF-16BE | Mỗi UTF-16 code unit dùng 2 byte, byte cao trước | `U+0041` thành `00 41`; encoder Java không tự ghi BOM |
| UTF-16 (`StandardCharsets.UTF_16`) | Có quy ước dùng BOM để xác định thứ tự byte lúc giải mã | Java mã hóa mặc định có BOM `FE FF` và nội dung big-endian; decoder dùng big-endian khi thiếu BOM |

**BOM** (*byte order mark*) là dấu byte dùng để nhận biết thứ tự byte ở một số cách mã hóa. Chẳng hạn, `FE FF` biểu thị big-endian trong UTF-16 và `FF FE` biểu thị little-endian. Điều đó không có nghĩa mọi tệp UTF-8 hay UTF-16LE đều phải có BOM. Cần tuân thủ quy ước của nguồn dữ liệu và API sử dụng.

Ví dụ chữ A được biểu diễn khác nhau:

- `UTF-8`: `41`.
- `UTF-16LE`: `41 00`.
- `UTF-16BE`: `00 41`.
- `UTF-16` do encoder Java ghi: `FE FF 00 41` (bao gồm BOM).

Cả bốn trường hợp đều có thể giải mã thành `String "A"` với `length() == 1`. Charset UTF-16 dùng để **lưu/đọc byte** có thêm mối quan tâm thứ tự byte, khác với hợp đồng `charAt()`, `length()` khi thao tác trên `String`.

## 6. Đọc và ghi tệp với charset tường minh

Ví dụ dùng `Files.writeString()` và `Files.readString()`, có từ Java 11 trở lên. Tạo tệp **Utf8FileExample.java**:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Utf8FileExample {
    public static void main(String[] args) throws IOException {
        Path path = Path.of("notes-utf8.txt");

        String original = "Xin chào Java 😀";
        Files.writeString(path, original, StandardCharsets.UTF_8);

        String restored = Files.readString(path, StandardCharsets.UTF_8);
        System.out.println(original.equals(restored));
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 Utf8FileExample.java
java Utf8FileExample
~~~

Stdout:

~~~text
true
~~~

Chương trình tạo/ghi đè `notes-utf8.txt` tại thư mục chạy. Khi dùng trong ứng dụng thật cần kiểm soát đường dẫn, quyền truy cập và chính sách ghi đè. Nội dung lúc còn ở `String original` vẫn có ngữ nghĩa UTF-16 theo API Java; nó **được mã hóa thành UTF-8 tại lời gọi ghi tệp**.

Với tệp đã biết mang UTF-16LE, đoạn mã sau phải đọc theo UTF-16LE:

~~~java
String text = Files.readString(
        Path.of("external-utf16le.txt"),
        StandardCharsets.UTF_16LE);
~~~

Đây chỉ là **đoạn code minh họa** trong một phương thức có xử lý hoặc khai báo `IOException` và có import `Files`, `Path`, `StandardCharsets`. Chọn đúng charset theo dữ liệu thực tế của tệp. Khi đã giải mã, `text` là `String` Java bình thường.

**Lưu ý về mặc định:** Hai overload `Files.readString(path)` và `Files.writeString(path, text)` sử dụng UTF-8 theo hợp đồng API Java 11+. Nhưng các API khác như `FileReader`, `InputStreamReader`, `FileWriter` hoặc `String.getBytes()` có overload dùng **default charset** nếu không chỉ định. Từ JDK 18, JEP 400 chuẩn hóa UTF-8 làm default charset cho phần lớn trường hợp trong cấu hình chuẩn, song điều đó không đảm bảo mọi dữ liệu cũ hoặc dữ liệu do hệ thống khác cung cấp đều là UTF-8. Để kết nối ổn định, **truyền charset tường minh theo hợp đồng dữ liệu**.

## 7. Lỗi đọc sai charset: mojibake và ký tự thay thế

Giả sử tệp lưu chuỗi `café` dưới UTF-8. Chữ `é` được mã hóa thành `C3 A9`. Nếu áp dụng bộ giải mã Windows-1252 lên các byte này, người dùng có thể thấy `cafÃ©`. Hiện tượng này gọi là **mojibake**: byte được hiểu bằng quy tắc khác với charset đã dùng để tạo nó. Chuỗi Java thu được vẫn là `String` với ngữ nghĩa API UTF-16; **nội dung bị sai ngay ở bước giải mã**.

Có hai loại lỗi:

- Dữ liệu byte hợp lệ theo charset **sai**: bộ giải mã có thể tạo ra các ký tự khác, không ném lỗi.
- Byte không hợp lệ theo charset đang dùng: decoder có thể thay ký tự lỗi bằng `U+FFFD` (ký tự thay thế), hoặc báo ngoại lệ, tùy API và cấu hình.

Nếu cần phát hiện byte UTF-8 lỗi **thay vì thay thế âm thầm**, dùng `CharsetDecoder` và cấu hình `CodingErrorAction.REPORT`:

~~~java
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

// Đoạn code đặt trong một phương thức Java:
byte[] invalidUtf8 = {(byte) 0xC3, (byte) 0x28};
try {
    String decoded = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(invalidUtf8))
            .toString();
    System.out.println(decoded);
} catch (CharacterCodingException ex) {
    System.out.println("Invalid UTF-8");
}
~~~

Stdout của đoạn mã khi thực thi trong phương thức:

~~~text
Invalid UTF-8
~~~

Byte `C3` bắt đầu một chuỗi UTF-8 cần byte tiếp nối, nhưng `28` không phải byte tiếp nối hợp lệ. Cấu hình `REPORT` giúp phát hiện dữ liệu hỏng. Nếu dùng API tiện lợi `new String(bytes, StandardCharsets.UTF_8)`, các chuỗi byte lỗi thường được thay bằng ký tự thay thế theo hợp đồng constructor, vì vậy không nên coi việc không ném lỗi là bằng chứng dữ liệu gốc hợp lệ.

### Cắt chuỗi có thể phá surrogate pair

Đoạn mã sau chứng minh `substring()` cũng dùng chỉ số UTF-16:

~~~java
String s = "A😀B";
System.out.println(s.length()); // 4
System.out.println(s.substring(1, 3).equals("😀")); // true
System.out.println(s.substring(1, 2).length()); // 1, chi lay high surrogate
~~~

Đây là **đoạn code minh họa** đặt trong một phương thức. Cắt `[1, 3)` giữ cả hai đơn vị UTF-16 của emoji; cắt `[1, 2)` lấy một surrogate lẻ nên không còn emoji hoàn chỉnh. Nếu phạm vi cần thao tác theo code point, tham khảo `codePointCount()`, `offsetByCodePoints()` và `codePoints()`. Nếu yêu cầu không tách cụm ký tự người dùng nhìn thấy, cần thêm cơ chế phân đoạn theo *grapheme cluster*.

## 8. Lỗi phổ biến và cách sửa

| Nhầm lẫn hoặc thao tác sai | Hậu quả | Cách sửa |
| --- | --- | --- |
| “`String` dùng UTF-16 nên tệp nhất định là UTF-16” | Chọn sai cách đọc/ghi byte | Chỉ định đúng charset khi I/O |
| Dùng `s.length()` làm số byte UTF-8 | Sai kích thước với dấu, emoji và nhiều ký tự khác | `s.getBytes(StandardCharsets.UTF_8).length` |
| Dùng `substring()` tùy tiện giữa surrogate pair | Tạo chuỗi có surrogate lẻ | Đặt ranh giới phù hợp với code point hoặc cụm hiển thị |
| Ghi UTF-8 nhưng đọc bằng UTF-16LE | Văn bản sai hoặc lỗi giải mã | Dùng charset tương ứng với byte gốc |
| Dùng `new String(bytes)` khi không biết default charset | Kết quả có thể khác giữa cấu hình/môi trường hoặc dữ liệu không đúng charset | `new String(bytes, expectedCharset)` |
| Tưởng `javac -encoding UTF-8` quyết định charset của tệp lúc chạy | Dữ liệu đầu ra có thể khác dự kiến | Charset mã nguồn và charset dữ liệu là hai quyết định riêng |
| Tưởng BOM bắt buộc cho mọi UTF-8/UTF-16LE | Ghi thêm/thiếu BOM so với giao thức | Tuân theo đặc tả định dạng và tên charset |
| Tin rằng đuôi `.txt` xác định charset | Giải mã sai tệp không có metadata | Lấy charset từ thỏa thuận hoặc định dạng |
| Tin rằng `char` luôn là một ký tự trọn vẹn | Cắt hỏng code point ngoài BMP | Dùng API code point khi thích hợp |
| Coi byte nội bộ của JVM là dữ liệu tệp | Phụ thuộc chi tiết triển khai | Chỉ dựa vào API encode/decode |

## 9. Quy trình kiểm tra khi gặp lỗi tiếng Việt hoặc emoji trong tệp

1. **Làm rõ nguồn byte:** tệp được tạo từ chương trình nào, có charset nào, có BOM hoặc metadata hay không?
2. **Kiểm tra đoạn đọc:** `Files.readString(path, charset)` hoặc `InputStreamReader(input, charset)` có dùng đúng charset nguồn không?
3. **Kiểm tra `String` sau khi decode:** so sánh code point, `equals()` và các giá trị mong đợi; đừng dùng `length()` như kích thước byte.
4. **Kiểm tra đoạn ghi:** `Files.writeString(path, content, charset)` hoặc `getBytes(charset)` có dùng charset mà hệ thống nhận yêu cầu không?
5. **Kiểm tra byte thật:** in hex hoặc đọc byte bằng `Files.readAllBytes()`, quan sát BOM và chuỗi byte UTF-8/UTF-16.
6. **Round-trip:** với mẫu có chữ ASCII, tiếng Việt có dấu, emoji, thử encode rồi decode với cùng charset; kiểm tra `original.equals(restored)`.
7. **Quy định cách báo lỗi:** với tệp quan trọng, quyết định việc dùng decoder báo lỗi nghiêm ngặt thay vì lặng lẽ thay ký tự hỏng.

## 10. Tự kiểm tra

**Câu 1.** `String s = "😀";` thì `s.length()` bằng mấy và lưu s dưới UTF-8 (không BOM) cần bao nhiêu byte?

**Đáp án:** `length() == 2` vì emoji dùng hai UTF-16 code unit; UTF-8 của emoji dùng **4 byte**, `F0 9F 98 80`.

**Câu 2.** Chuỗi `"Aé"` có hai Unicode code point. Số byte UTF-8 có bằng 2 không?

**Đáp án:** không. A chiếm 1 byte, é chiếm 2 byte; tổng cộng **3 byte**.

**Câu 3.** Sau khi `Files.writeString(path, s, StandardCharsets.UTF_8)`, biến `s` có đổi loại thành “String UTF-8” không?

**Đáp án:** không. Chỉ đầu ra byte được mã hóa theo UTF-8; API `String` vẫn làm việc với đơn vị UTF-16.

**Câu 4.** Tại sao bytes `FE FF 00 41` đọc qua charset UTF-16 cho ra chuỗi có nội dung A?

**Đáp án:** `FE FF` là BOM chỉ big-endian, còn `00 41` là UTF-16 code unit của A.

**Câu 5.** Đọc một tệp lưu bằng UTF-8 nhưng dùng `StandardCharsets.UTF_16LE` có đảm bảo khôi phục đúng không?

**Đáp án:** không; mã hóa và giải mã phải sử dụng cùng quy ước (hoặc các quy ước tương thích theo đặc tả). Kết quả sai charset có thể tạo ký tự sai hoặc bị coi là dữ liệu không hợp lệ.

## Tài liệu tham khảo

- [Java SE 17 — String](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html): quy ước UTF-16 trong API chuỗi, `length`, `charAt`, `getBytes`, `codePointCount`.
- [Java SE 17 — StandardCharsets](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/StandardCharsets.html): tên và đặc tính charset chuẩn.
- [Java SE 17 — Files](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html): `writeString`, `readString`, `readAllBytes`.
- [Java SE 17 — CharsetDecoder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/CharsetDecoder.html): xử lý byte lỗi và `CodingErrorAction`.
- [JEP 254 — Compact Strings](https://openjdk.org/jeps/254): tối ưu biểu diễn nội bộ `String`.
- [JEP 400 — UTF-8 by Default](https://openjdk.org/jeps/400): thay đổi charset mặc định kể từ JDK 18.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="unicode-utf16-008">Vì sao số char không luôn bằng số ký tự người dùng nhìn thấy</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao số `char` không luôn bằng số ký tự người dùng nhìn thấy?

Một nút “xóa ký tự”, bộ đếm trong ô nhập tên và phần xem trước 30 ký tự thường phải làm việc với **cụm ký tự người dùng cảm nhận**, trong khi `String.length()` của Java chỉ trả số **UTF-16 code unit**. Bài này chuyển sự khác biệt đó thành một chính sách xử lý văn bản có thể kiểm thử trong sản phẩm.

## 1. Một giới hạn, nhiều cách hiểu

Xét ba nội dung nhập:

- `😊`: 2 `char` nhưng 1 code point và thường 1 cụm hiển thị vì emoji ngoài BMP được UTF-16 mã hóa bằng cặp surrogate.
- `e\u0301`: 2 `char`, 2 code point nhưng thường 1 cụm vì dấu kết hợp đi cùng chữ cơ sở.
- `👩‍💻`: 5 `char`, 3 code point nhưng 1 extended grapheme cluster nhờ chuỗi ZWJ.

Vì vậy, thay `length()` bằng `codePointCount()` chỉ giải quyết trường hợp surrogate pair; vẫn có thể cắt giữa các thành phần của cùng một emoji hoặc giữa chữ và dấu. Khi xử lý từng ký tự hiển thị, ta cần **grapheme boundaries**.

Extended grapheme cluster theo Unicode Standard Annex #29 là đơn vị phân đoạn gần với kỳ vọng của người dùng. Nó **không đồng nghĩa** một glyph, một ô terminal hoặc một độ rộng pixel. Phông chữ có thể không ghép một emoji ZWJ thành một biểu tượng, và ngôn ngữ phức tạp còn chịu ảnh hưởng của shaping/biên tập văn bản.

## 2. Đặc tả đầu vào trước khi viết code

Giả sử form có hai ràng buộc: tối đa **6 grapheme cluster** cho một nhãn ngắn và tối đa **18 byte UTF-8** cho payload. Ta cần trả lời riêng:

| Câu hỏi | Quy tắc sản phẩm |
| --- | --- |
| “Một ký tự” là gì? | Một extended grapheme cluster theo Unicode segmentation |
| Giới hạn máy chủ là gì? | Số grapheme và byte UTF-8 phải cùng thỏa |
| Chuẩn hóa Unicode trước khi đếm không? | Có hay không phải do hợp đồng API quyết định; ví dụ dưới đây **giữ nguyên** đầu vào |
| Surrogate lẻ xử lý thế nào? | Từ chối vì đầu vào không well-formed UTF-16 |
| Khi quá giới hạn? | Giữ phần tiền tố dài nhất chứa các cluster trọn vẹn và không vượt giới hạn byte |
| Sự nhất quán đa nền tảng? | Kiểm thử client và server trên bộ chuỗi mẫu, cố định/kiểm soát phiên bản Unicode khi cần |

Lưu ý: **cắt để tạo preview** và **xác thực dữ liệu để lưu** có thể khác nhau. Với tên tài khoản, việc tự cắt có thể gây mất dữ liệu hoặc trùng tên; nên trả lỗi rõ ràng thay vì im lặng cắt. Với preview, việc lấy tiền tố trọn cluster thường là lựa chọn phù hợp.

## 3. Tìm ranh giới grapheme trong Java

Java 17+ hỗ trợ `Pattern.compile("\\X")` để khớp từng extended grapheme cluster. `Matcher.find()` đi qua các cụm theo thứ tự; `Matcher.end()` trả **UTF-16 offset** ngay sau cụm vừa khớp. Có thể dùng offset này trong `substring(0, end)` mà không cần tự cộng độ dài emoji.

Khi có thêm giới hạn byte, mỗi cluster UTF-16 **well-formed** có thể mã hóa sang UTF-8 và cộng số byte để quyết định có nhận cluster đó không. Ta không cắt theo số byte tùy ý, vì thao tác đó có thể cắt giữa dãy UTF-8 của một code point.

### Ví dụ đầy đủ: preview thỏa cả số cluster và số byte

Tệp `GraphemePreviewPolicy.java`, JDK 17+:

~~~java
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GraphemePreviewPolicy {
    private static final Pattern CLUSTER = Pattern.compile("\\X");

    static String preview(String text, int maxClusters, int maxUtf8Bytes) {
        if (text == null) {
            throw new IllegalArgumentException("text is null");
        }
        if (maxClusters < 0 || maxUtf8Bytes < 0) {
            throw new IllegalArgumentException("negative limit");
        }

        // Java codePoints() có thể phát surrogate lẻ; từ chối trước khi encode.
        if (text.codePoints().anyMatch(
                cp -> cp >= 0xD800 && cp <= 0xDFFF)) {
            throw new IllegalArgumentException("unpaired surrogate");
        }

        Matcher matcher = CLUSTER.matcher(text);
        int usedBytes = 0;
        int endUtf16 = 0;
        int usedClusters = 0;

        while (usedClusters < maxClusters && matcher.find()) {
            int clusterBytes = matcher.group()
                    .getBytes(StandardCharsets.UTF_8).length;

            if (clusterBytes > maxUtf8Bytes - usedBytes) {
                break;
            }

            usedBytes += clusterBytes;
            usedClusters++;
            endUtf16 = matcher.end();
        }
        return text.substring(0, endUtf16);
    }

    public static void main(String[] args) {
        String text = "Vi\u1EC7t \uD83D\uDC69\u200D\uD83D\uDCBB!";

        System.out.println(preview(text, 6, 18)
                .equals("Vi\u1EC7t \uD83D\uDC69\u200D\uD83D\uDCBB"));
        System.out.println(preview(text, 6, 17).equals("Vi\u1EC7t "));
        System.out.println(preview(text, 5, 50).equals("Vi\u1EC7t "));
        System.out.println(preview(text, 0, 50).isEmpty());
        System.out.println(preview("\uD83D\uDC69\u200D\uD83D\uDCBB", 1, 10)
                .isEmpty());
    }
}
~~~

~~~bash
javac -encoding UTF-8 GraphemePreviewPolicy.java
java GraphemePreviewPolicy
~~~

Stdout:

~~~text
true
true
true
true
true
~~~

“Việt ” chứa **5 cluster và 7 byte UTF-8**; `👩‍💻` là cluster thứ sáu, thêm **11 byte**, vừa đúng giới hạn 18 byte. Với ngân sách 17 byte, chương trình phải dừng **trước toàn bộ emoji**. Không được lấy riêng emoji người phụ nữ rồi bỏ ZWJ và laptop để đủ byte, vì điều đó đổi nội dung biểu tượng.

Ví dụ này mã hóa từng cluster thành mảng byte tạm; với nhãn ngắn, cách làm đơn giản và dễ kiểm tra. Nếu xử lý văn bản cực dài hoặc chạy với tần suất lớn, có thể tối ưu bằng encoder tăng dần và đo hiệu năng. Thuật toán chỉ bảo toàn grapheme theo **quy tắc mà JDK đang hỗ trợ**; không bảo đảm mọi nền tảng hiển thị giống nhau.

## 4. Kiểm thử thực tế trên nhiều loại văn bản

| Mẫu đầu vào | Điểm dễ sai | Phép thử nên có |
| --- | --- | --- |
| `A😀B` | UTF-16 offset của B là 3 | Không cắt high/low surrogate ở giữa |
| `e\u0301` và `\u00E9` | Hai dãy code point khác nhau | Cùng một cluster nhưng `equals()` chưa chắc bằng nhau |
| `Vi\u1EC7t` và dạng NFD | Dấu tiếng Việt được tách | Đếm cluster theo quy tắc, byte theo biểu diễn thật |
| `🇻🇳` | Hai regional indicator tạo cờ | Không cắt riêng regional indicator đầu |
| `👍🏽` | Base emoji + skin tone | Không tách modifier |
| `👩‍💻` | Emoji + ZWJ + emoji | Không chẻ chuỗi nối thành các biểu tượng riêng |
| High surrogate đứng lẻ | `String` vẫn tạo được, nhưng UTF-16 lỗi | Thông báo lỗi thay vì âm thầm thay byte |
| Văn bản có ký tự điều khiển | Grapheme count không phải quy tắc cho phép nội dung | Kiểm tra thêm chính sách ký tự được phép |

Thêm kiểm thử tại **đúng ranh giới**: hạn mức 0, vừa khít, kém 1 byte, ít hơn 1 cluster và chuỗi rỗng. Nếu client dùng JavaScript còn server dùng Java, hãy kiểm tra bằng các chuỗi Unicode cụ thể thay vì mặc định hai runtime luôn phân đoạn giống nhau. Với input method (IME) tiếng Việt, cân nhắc trạng thái **đang soạn thảo/composition** trước khi áp đặt giới hạn tức thì, để không ngăn người dùng hoàn tất dấu.

## 5. Chuẩn hóa, con trỏ và chỉnh sửa văn bản

`Normalizer.normalize(text, Normalizer.Form.NFC)` có thể làm các dãy tương đương chuẩn nhất quán khi so sánh hoặc tìm kiếm, nhưng **thay đổi code unit và offset**. Nếu lưu vị trí con trỏ trước chuẩn hóa rồi dùng offset cũ sau chuẩn hóa, vị trí có thể sai. Vì thế cần định nghĩa rõ thứ tự: nhận đầu vào, chuẩn hóa (nếu hợp đồng yêu cầu), tính giới hạn, lưu dữ liệu và cập nhật vị trí con trỏ.

Trình sửa văn bản tương tác còn cần tính tới vùng chọn, phím Backspace/Delete, văn bản hai chiều, IME và quy tắc shaping. `\\X` cung cấp **ranh giới cluster**, không tự cung cấp đầy đủ một text editor. Một grapheme cluster cũng không phải đơn vị số cột terminal; canh lề văn bản vẫn cần công cụ đo độ rộng phù hợp.

## 6. Checklist ra quyết định

Trước khi dùng `length()` làm “số ký tự”, xác định mục đích: chỉ số `String` theo UTF-16, xử lý từng code point, giới hạn theo grapheme, giới hạn byte hay chiều rộng giao diện. Sau đó chọn API tương ứng và thử tối thiểu ba lớp ca: **surrogate pair**, **dấu kết hợp**, **emoji nhiều code point**. Nếu phải lưu Unicode nghiêm ngặt, kiểm tra surrogate lẻ trước encode.

### Tự kiểm tra

1. `"👩‍💻".length()` bằng bao nhiêu? **5**, dù chỉ một cluster theo quy tắc hỗ trợ trình tự đó.
2. `"e\u0301".codePointCount(0, 2)` bằng bao nhiêu? **2**, còn grapheme count thường là **1**.
3. Một cụm emoji cần 11 byte UTF-8 nhưng ngân sách còn 10 byte: có được lấy nửa cụm không? **Không**, theo chính sách preview đã định.
4. Đếm theo grapheme có tự giới hạn số byte hoặc đo độ rộng pixel không? **Không**; đây là ba phép đo độc lập.
5. Tại sao phải thử cùng bộ mẫu trên client/server? Vì quy tắc Unicode và phiên bản dữ liệu phân đoạn của các runtime có thể khác nhau.

Nguồn kỹ thuật: [Unicode Text Segmentation — UAX #29](https://www.unicode.org/reports/tr29/), [Java `Pattern`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html), [Java `String`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html).

</details>

- [Quay lại đầu trang](#back-to-top)
