<a id="back-to-top"></a>

# Hoa thường và chuẩn hóa

## Menu
- [toLowerCase và toUpperCase với locale tường minh](#case-normalization-001)
- [Locale.ROOT cho định danh kỹ thuật khi phù hợp](#case-normalization-002)
- [Trường hợp chữ I của tiếng Thổ Nhĩ Kỳ](#case-normalization-003)
- [Đổi hoa–thường có thể làm thay đổi độ dài chuỗi](#case-normalization-004)
- [Normalizer và các dạng NFC, NFD, NFKC, NFKD](#case-normalization-005)
- [Chuỗi nhìn giống nhau nhưng khác dãy code point](#case-normalization-006)
- [Phân biệt chuẩn hóa Unicode với loại bỏ dấu](#case-normalization-007)
- [Collator khi cần so sánh theo quy tắc ngôn ngữ](#case-normalization-008)

## <a id="case-normalization-001">toLowerCase và toUpperCase với locale tường minh</a>

<details>
<summary>Xem chi tiết</summary>

# `toLowerCase()` và `toUpperCase()` với locale tường minh trong Java

Chuyển một `String` sang chữ thường hoặc chữ hoa là thao tác quen thuộc khi chuẩn bị nội dung hiển thị, so khớp hoặc tạo khóa tìm kiếm. Trong Java, hai lời gọi `text.toLowerCase()` và `text.toUpperCase()` **phụ thuộc vào locale mặc định** của môi trường chạy. Cùng một đầu vào có thể được đổi chữ khác nhau nếu chương trình chạy với ngôn ngữ/khu vực khác.

Để hành vi rõ ràng, hãy ưu tiên overload nhận `Locale` khi yêu cầu đã xác định: `text.toLowerCase(locale)` và `text.toUpperCase(locale)`. Với dữ liệu kỹ thuật không gắn với ngôn ngữ tự nhiên, thường dùng `Locale.ROOT`; với văn bản cần quy tắc của một ngôn ngữ cụ thể, chọn locale tương ứng. Không có một locale đúng cho tất cả dữ liệu.

## 1. Hai nhóm overload và ý nghĩa

Lớp `java.lang.String` cung cấp bốn phương thức:

```java
String toLowerCase()
String toLowerCase(Locale locale)
String toUpperCase()
String toUpperCase(Locale locale)
```

Đây là chữ ký để tham khảo, không phải một lớp Java hoàn chỉnh.

- `toLowerCase()`: chuyển chữ thường theo `Locale.getDefault()` hiện tại.
- `toUpperCase()`: chuyển chữ hoa theo `Locale.getDefault()` hiện tại.
- `toLowerCase(locale)`: dùng quy tắc ánh xạ chữ thường của `locale` đã truyền.
- `toUpperCase(locale)`: dùng quy tắc ánh xạ chữ hoa của `locale` đã truyền.

Các phép chuyển trả về một giá trị `String` tương ứng; chúng **không sửa nội dung** của chuỗi nguồn, vì `String` bất biến. Không phải mọi ký tự đều có phiên bản hoa/thường. Chữ số, phần lớn dấu câu và nhiều ký hiệu giữ nguyên, nhưng một số chữ có cách biến đổi phụ thuộc ngôn ngữ.

Ví dụ cơ bản:

```java
String original = "Java Core";
String lower = original.toLowerCase(Locale.ROOT);
String upper = original.toUpperCase(Locale.ROOT);

// original: "Java Core"
// lower:    "java core"
// upper:    "JAVA CORE"
```

Đoạn này dùng `Locale.ROOT` từ `java.util.Locale` và cần đặt trong một phương thức/lớp có import phù hợp. Hai lời gọi trả về giá trị mới hoặc tham chiếu có nội dung tương ứng; không cần quan tâm kết quả có được cấp phát đối tượng mới trong mọi trường hợp hay không.

## 2. Vì sao gọi không tham số có thể gây hành vi khác nhau?

`toLowerCase()` không tham số tương đương về quy tắc lựa chọn locale với `toLowerCase(Locale.getDefault())`; tương tự đối với `toUpperCase()`. Locale mặc định có thể được JVM xác định từ thiết lập môi trường và có thể bị thay đổi trong quá trình chạy bằng API của `Locale`.

Vì vậy, hai máy có thể xử lý khác nhau một từ chứa chữ `I` dù code ứng dụng giống hệt. Ví dụ nổi tiếng là **tiếng Thổ Nhĩ Kỳ (Turkish)**:

- Trong ngữ cảnh tiếng Anh hoặc ánh xạ trung tính thông dụng: `I` → `i` khi viết thường.
- Trong tiếng Thổ Nhĩ Kỳ: `I` → `ı` (LATIN SMALL LETTER DOTLESS I).
- Ngược lại, tiếng Thổ Nhĩ Kỳ có `i` → `İ` (LATIN CAPITAL LETTER I WITH DOT) khi viết hoa.

Nếu chương trình tạo khóa tra cứu một tên trường kỹ thuật bằng `toLowerCase()` không tham số, kết quả có thể phụ thuộc máy chạy. Điều đó khó kiểm thử và có thể làm sai cách tra cứu hoặc so sánh sau khi chuyển môi trường.

Tệp `ExplicitLocaleDemo.java`:

```java
import java.util.Locale;

public class ExplicitLocaleDemo {
    public static void main(String[] args) {
        String word = "TITLE";
        Locale turkish = Locale.forLanguageTag("tr-TR");

        System.out.println("ROOT lower: " +
                word.toLowerCase(Locale.ROOT));
        System.out.println("TR lower: " +
                word.toLowerCase(turkish));
        System.out.println("ROOT upper: " +
                "file".toUpperCase(Locale.ROOT));
        System.out.println("TR upper: " +
                "file".toUpperCase(turkish));
        System.out.println("source: " + word);
    }
}
```

Lưu đúng tên tệp, biên dịch và chạy với JDK 17 trở lên:

```bash
javac -encoding UTF-8 ExplicitLocaleDemo.java
java ExplicitLocaleDemo
```

Đầu ra:

```text
ROOT lower: title
TR lower: tıtle
ROOT upper: FILE
TR upper: FİLE
source: TITLE
```

Kết quả cho thấy chỉ thay `Locale` đã khiến các ký tự `I` và `i` được chuyển khác nhau. Ví dụ không cần thay locale mặc định của máy vì đã truyền locale một cách tường minh, giúp kết quả thử nghiệm rõ ràng hơn.

## 3. Chọn `Locale.ROOT` hay locale của một ngôn ngữ?

### `Locale.ROOT`: quy tắc không gắn với một khu vực/ngôn ngữ cụ thể

`Locale.ROOT` là locale trung tính. Nó hữu ích cho những dữ liệu **do chương trình quy định**, ví dụ mã trạng thái có ký tự Latin cơ bản, tên khóa trong giao thức hoặc chuẩn hóa một nhãn kỹ thuật khi hệ thống quy định so khớp không phụ thuộc thiết lập người dùng.

```java
import java.util.Locale;

String command = "START";
String canonical = command.toLowerCase(Locale.ROOT);
// canonical bằng "start"
```

Điều quan trọng là ***đặc tả dữ liệu***: nếu giao thức quy định mã phải là ASCII hoặc một tập ký tự cố định, bạn vẫn nên xác thực đúng tập ký tự đó trước khi chuẩn hóa. `Locale.ROOT` không tự biến dữ liệu Unicode bất kỳ thành một chuỗi ASCII hợp lệ, và cũng không bảo đảm phép biến đổi là một-một.

Không nên diễn giải `Locale.ROOT` là locale “tốt hơn” đối với mọi tên người, câu chữ hoặc văn bản tự nhiên. Nó chỉ loại bỏ sự phụ thuộc vào **locale mặc định**, chứ không tự giải quyết các yêu cầu ngôn ngữ học.

### Locale tường minh theo ngôn ngữ

Khi kết quả phải phù hợp quy tắc chữ hoa/thường của nội dung/người dùng, chọn locale được định nghĩa trong yêu cầu:

```java
import java.util.Locale;

Locale turkish = Locale.forLanguageTag("tr-TR");
String lowerTurkish = "I".toLowerCase(turkish); // "ı"
String upperTurkish = "i".toUpperCase(turkish); // "İ"
```

Có thể dùng các hằng như `Locale.ENGLISH` cho nội dung tiếng Anh hoặc `Locale.forLanguageTag("vi-VN")` khi chính sách xử lý văn bản thuộc môi trường Việt Nam. Tuy nhiên, **không lấy locale theo nơi đặt máy chủ làm mặc định nghiệp vụ** nếu văn bản của người dùng thực sự thuộc ngôn ngữ khác.

Với mã nhận từ bên ngoài và cần kết quả tái lập, locale phải là **đầu vào có chủ đích** hoặc là một giá trị cố định trong đặc tả ứng dụng. `Locale.getDefault()` chỉ phù hợp nếu mục tiêu thật sự là “làm theo thiết lập ngôn ngữ mặc định hiện tại”.

## 4. Quy tắc chữ I Thổ Nhĩ Kỳ cho thấy khác biệt về locale

Cặp `I ↔ ı` và `İ ↔ i` của tiếng Thổ Nhĩ Kỳ cho thấy **locale làm thay đổi kết quả**, không chỉ thay đổi cách hiển thị. Với `tr-TR`, `"I".toLowerCase(tr)` là `"ı"`; với `Locale.ROOT` là `"i"`. Bài riêng về chữ I Thổ Nhĩ Kỳ phân tích đủ bốn code point và khác biệt với `equalsIgnoreCase()` bằng chương trình in `U+...`.

## 5. Chuyển hoa/thường có thể đổi độ dài

`"Straße".toUpperCase(Locale.ROOT)` cho `"STRASSE"` (6 → 7 `char`), và `"\u0130".toLowerCase(Locale.ROOT)` tạo `"i\u0307"` (1 → 2 `char`). Đây là phép ánh xạ chuỗi Unicode, không phải thay thế độc lập từng `char`.

```java
String before = "Straße";
String upper = before.toUpperCase(java.util.Locale.ROOT);
System.out.println(before.length() + " -> " + upper.length()); // 6 -> 7
System.out.println(before.equals(upper));                      // false
```

Đoạn lệnh đặt trong một phương thức. Hãy tính các chỉ số và giới hạn độ dài trên **kết quả sau biến đổi**, tránh tái sử dụng offset của chuỗi gốc. Bài về độ dài đi sâu vào lỗi offset `Straße`/`STRASSE`.

## 6. Chuyển case không luôn có phép đảo ngược

Nhiều chuỗi đầu vào có thể ánh xạ thành cùng chữ hoa: `"straße".toUpperCase(Locale.ROOT)` và `"STRASSE".toUpperCase(Locale.ROOT)` đều cho `"STRASSE"`. Chuyển chuỗi ấy về chữ thường thành `"strasse"` **không khôi phục** chữ `ß` ban đầu. Nếu cần giữ chính tả người dùng nhập, lưu bản gốc và tạo khóa đã đổi case riêng cho tìm kiếm.

## 7. Cách viết ổn định cho các giá trị kỹ thuật

Giả sử API nhận một trong ba mã chế độ: `READ`, `WRITE`, `ADMIN`. Nếu giao diện muốn cho phép nhập không phân biệt chữ hoa/thường nhưng ngôn ngữ mã là ASCII cố định, hãy kiểm tra chuỗi được chuyển với một locale trung tính:

Tệp `CommandNormalizer.java`:

```java
import java.util.Locale;

public class CommandNormalizer {
    static String normalizeCommand(String input) {
        if (input == null) {
            throw new IllegalArgumentException("command is null");
        }
        String normalized = input.toUpperCase(Locale.ROOT);

        if (!normalized.equals("READ")
                && !normalized.equals("WRITE")
                && !normalized.equals("ADMIN")) {
            throw new IllegalArgumentException("unsupported command");
        }
        return normalized;
    }

    public static void main(String[] args) {
        System.out.println(normalizeCommand("read"));
        System.out.println(normalizeCommand("Write"));
        System.out.println(normalizeCommand("ADMIN"));

        try {
            normalizeCommand("archive");
        } catch (IllegalArgumentException ex) {
            System.out.println("invalid command");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 CommandNormalizer.java
java CommandNormalizer
```

Kết quả:

```text
READ
WRITE
ADMIN
invalid command
```

`Locale.ROOT` làm phép chuyển không phụ thuộc locale của máy chạy. Tuy nhiên, đây chỉ là một ví dụ chính sách **chấp nhận các mã đã xác định**. Nếu giao thức đòi hỏi đầu vào phải chính xác dạng `READ`/`WRITE`/`ADMIN` và không cho phép dạng khác, đừng tự đổi chữ trước khi xác thực.

Với định danh hệ thống, mã truy cập, khóa ký số, đường dẫn hoặc dữ liệu bảo mật, cần cân nhắc rủi ro nhiều chuỗi khác nhau chuyển thành cùng một dạng và mọi quy tắc xác thực phải được thiết kế thống nhất giữa nơi tạo và nơi kiểm tra giá trị.

## 8. Chọn locale từ yêu cầu sản phẩm thay vì tự đoán

Một hàm có thể nhận rõ `Locale` từ bên gọi:

```java
import java.util.Locale;

public class LocalizedTitle {
    static String uppercaseLabel(String label, Locale locale) {
        if (label == null || locale == null) {
            throw new IllegalArgumentException("label and locale required");
        }
        return label.toUpperCase(locale);
    }

    public static void main(String[] args) {
        String label = "file";
        Locale english = Locale.ENGLISH;
        Locale turkish = Locale.forLanguageTag("tr-TR");

        System.out.println(uppercaseLabel(label, english));
        System.out.println(uppercaseLabel(label, turkish));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 LocalizedTitle.java
java LocalizedTitle
```

Đầu ra:

```text
FILE
FİLE
```

Trong trường hợp này, trách nhiệm chọn locale thuộc về **ngữ cảnh nghiệp vụ**: người dùng đang nhập một ngôn ngữ nào, nội dung gốc thuộc ngôn ngữ nào, hay ứng dụng yêu cầu biểu diễn trung tính. Locale theo IP của máy chủ, khu vực triển khai hoặc giao diện không luôn tương ứng với ngôn ngữ của nội dung.

Nếu thiếu thông tin về locale mà kết quả phải chính xác cho văn bản tự nhiên, việc **thiết kế rõ một chính sách mặc định** hữu ích hơn là ngầm lệ thuộc `Locale.getDefault()`.

## 9. Đổi case, chuẩn hóa Unicode và collation có mục đích khác nhau

- `toLowerCase(locale)` / `toUpperCase(locale)`: biến đổi chữ theo locale; **không tự** đưa các biểu diễn Unicode tương đương về cùng dạng.
- `Normalizer.normalize(s, Normalizer.Form.NFC)`: chuẩn hóa các biểu diễn Unicode tương đương chuẩn tắc; **không tự** chuyển chữ hoa thành thường.
- `Collator.getInstance(locale)`: so sánh/sắp xếp theo quy tắc ngôn ngữ và cấu hình; **không sửa** nội dung chuỗi.

Ví dụ `"\u00E9".equals("e\u0301")` là `false` dù hai chuỗi có thể nhìn như nhau; chuyển chúng sang lowercase vẫn không bảo đảm `equals`. Nếu bài toán cần canonical equivalence, chuẩn hóa **hai vế** về cùng dạng rồi so sánh. `equalsIgnoreCase()` cũng không nhận locale và không thực hiện toàn bộ Unicode normalization hay so sánh tiếng Thổ Nhĩ Kỳ.

## 10. Xử lý `null` và chuỗi rỗng

Nếu tham chiếu nhận lời gọi là `null`, việc gọi `toLowerCase(...)` hoặc `toUpperCase(...)` gây `NullPointerException`. Nếu tham số `locale` là `null`, overload nhận `Locale` cũng không có một locale hợp lệ để áp dụng và có thể ném `NullPointerException`.

```java
import java.util.Locale;

String value = null;
// value.toLowerCase(Locale.ROOT); // NullPointerException

String safe = value == null
        ? ""
        : value.toLowerCase(Locale.ROOT);
```

Đây là đoạn minh họa bên trong một phương thức; biến `safe` bằng `""` theo **quy ước tự chọn** của ví dụ. Không nên ngầm chuyển mọi `null` thành chuỗi rỗng nếu ứng dụng cần phân biệt “không có giá trị” với “giá trị người dùng nhập là rỗng”.

Với chuỗi nguồn rỗng, việc chuyển hoa/thường vẫn tạo nội dung rỗng:

```java
System.out.println("".toLowerCase(Locale.ROOT).isEmpty()); // true
System.out.println("".toUpperCase(Locale.ROOT).isEmpty()); // true
```

Khoảng trắng cũng không tự động bị cắt bỏ khi đổi chữ. Ví dụ `"  Java  ".toLowerCase(Locale.ROOT)` giữ hai dấu cách đầu/cuối và chỉ đổi các chữ cái thành `"  java  "`. Nếu ứng dụng muốn bỏ whitespace, đó là một bước xử lý khác với chính sách riêng.

## 11. Những lỗi phổ biến

**Lỗi 1 — Dùng `toLowerCase()` không tham số cho khóa hệ thống.** Kết quả phụ thuộc locale mặc định. Nếu đặc tả muốn xử lý trung tính, dùng `toLowerCase(Locale.ROOT)` và kiểm tra tập ký tự hợp lệ.

**Lỗi 2 — Luôn chọn `Locale.ROOT` cho văn bản của mọi ngôn ngữ.** Locale trung tính không thay thế quy tắc chữ của ngôn ngữ thực tế, đặc biệt khi cần xử lý chữ `I/i` trong tiếng Thổ Nhĩ Kỳ.

**Lỗi 3 — Nghĩ chữ hoa/thường luôn có ánh xạ từng ký tự một.** Các ánh xạ đầy đủ có thể tạo thêm code point hoặc phụ thuộc ngữ cảnh, khiến độ dài chuỗi thay đổi.

**Lỗi 4 — Cho rằng có thể đảo lại chuỗi gốc bằng cách đổi hoa rồi thường.** Các giá trị khác nhau có thể quy về cùng một dạng, như `straße` và `STRASSE`; không bảo đảm khôi phục nội dung gốc.

**Lỗi 5 — Tưởng chuyển hoa/thường đồng nghĩa Unicode normalization.** `toLowerCase`/`toUpperCase` không thay thế `Normalizer` và không tự giải quyết khác biệt dạng tiền tổ hợp/dấu kết hợp.

**Lỗi 6 — Coi `equalsIgnoreCase()` là cách so sánh theo locale.** Phương thức không nhận locale; nhu cầu xếp thứ tự hoặc so sánh văn bản tự nhiên theo ngôn ngữ có thể cần `Collator`.

**Lỗi 7 — Đổi chữ ngay trên dữ liệu gốc rồi lưu đè theo thói quen.** Có thể mất định dạng và cách viết người dùng cung cấp, hoặc tạo va chạm định danh. Hãy cân nhắc lưu bản gốc và tạo khóa dẫn xuất nếu chính sách cần.

**Lỗi 8 — Tự suy ra locale từ quốc gia đặt server.** Ngôn ngữ của nội dung có thể không trùng với locale của máy chủ hoặc giao diện; hãy xác định từ yêu cầu nghiệp vụ.

**Lỗi 9 — Tưởng `toUpperCase` sẽ cắt khoảng trắng và sửa mọi khác biệt kiểu chữ.** Phương thức đổi chữ theo quy tắc locale, không phải bộ làm sạch dữ liệu tổng quát.

## 12. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao nên tránh `"TITLE".toLowerCase()` khi tạo khóa tra cứu kỹ thuật cần giống nhau trên mọi máy?

**Đáp án:** Bởi nó dùng locale mặc định; tại ngữ cảnh tiếng Thổ Nhĩ Kỳ, `I` có thể trở thành `ı` thay vì `i`. Sử dụng một locale tường minh như `Locale.ROOT` giúp xác định quy tắc ngay trong mã.

**Câu 2.** `"file".toUpperCase(Locale.forLanguageTag("tr-TR"))` và `"file".toUpperCase(Locale.ROOT)` khác nhau thế nào?

**Đáp án:** Kết quả tiếng Thổ Nhĩ Kỳ là `"FİLE"` có `İ`, còn `Locale.ROOT` cho `"FILE"` có `I` thông thường.

**Câu 3.** Có thể khẳng định `text.toUpperCase(locale).length() == text.length()` cho mọi `String` không?

**Đáp án:** Không. Unicode có những ánh xạ tăng số code point, ví dụ `ß` → `SS`, làm độ dài UTF-16 có thể thay đổi.

**Câu 4.** Nếu hai chuỗi `"\u00E9"` và `"e\u0301"` cùng hiển thị giống chữ é, đổi cả hai sang chữ thường có bảo đảm `equals()` là `true` không?

**Đáp án:** Không. Chuyển hoa/thường không tự chuẩn hóa hai cách biểu diễn Unicode. Cần xác định chính sách normalization nếu ứng dụng muốn xem chúng là tương đương.

**Câu 5.** Khi hiển thị văn bản do người dùng nhập, có cần lưu đè dạng đã chuyển chữ thường để thuận tiện tìm kiếm không?

**Đáp án:** Không nhất thiết. Có thể giữ nguyên nội dung gốc và tạo một khóa tìm kiếm dẫn xuất với locale/chính sách xác định. Điều này giúp bảo toàn cách viết mà người dùng cung cấp.

**Ghi nhớ:** Hãy chọn locale **một cách tường minh** khi chuyển chữ: dùng `Locale.ROOT` cho quy tắc kỹ thuật trung tính và locale phù hợp khi nội dung cần quy tắc ngôn ngữ. `toLowerCase`/`toUpperCase` xử lý case mapping Unicode, có thể thay đổi độ dài và không phải phép đảo ngược hay Unicode normalization.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="case-normalization-002">Locale.ROOT cho định danh kỹ thuật khi phù hợp</a>

<details>
<summary>Xem chi tiết</summary>

# `Locale.ROOT` cho định danh kỹ thuật khi phù hợp trong Java

## 1. Vì sao thao tác hoa/thường cần quan tâm đến locale?

Trong Java, `String.toLowerCase()` và `String.toUpperCase()` **không có tham số** sử dụng **locale mặc định của JVM** (`Locale.getDefault()`). Quy tắc đổi hoa/thường có thể phụ thuộc ngôn ngữ: cùng một chuỗi đầu vào có thể cho kết quả khác nhau trên máy đang dùng locale tiếng Anh, tiếng Thổ Nhĩ Kỳ hoặc môi trường khác.

Điều đó là hợp lý với **văn bản hướng đến người dùng**. Tuy nhiên, đối với nhiều **định danh kỹ thuật** — như tên tùy chọn do ứng dụng tự quy định, từ khóa nội bộ, nhãn cấu hình hoặc tên trạng thái cho phép so khớp không phân biệt hoa/thường — việc kết quả phụ thuộc ngôn ngữ mặc định có thể làm hỏng tra cứu và gây khác biệt giữa môi trường phát triển, kiểm thử và triển khai.

Khi quy ước của định danh yêu cầu **quy tắc hoa/thường ổn định, không gắn với một ngôn ngữ cụ thể**, hãy cân nhắc:

```java
String normalized = input.toLowerCase(java.util.Locale.ROOT);
```

`Locale.ROOT` là locale **trung lập về ngôn ngữ và quốc gia**. Nó giúp loại bỏ sự phụ thuộc vào *locale mặc định* khi đổi hoa/thường. Nhưng nó **không có nghĩa** “định danh nào cũng phải chuyển sang chữ thường”, cũng **không tự làm Unicode trở thành ASCII**, không chuẩn hóa Unicode và không giải quyết mọi kiểu so sánh hoặc an toàn định danh.

## 2. Lỗi môi trường gây ra bởi định danh chứa chữ `I`

Cùng mã `"TITLE"`, phép `toLowerCase()` không tham số có thể tạo khóa `"title"` hoặc `"tıtle"` tùy `Locale.getDefault()`. Hai khóa **không bằng nhau theo `String.equals()`**. Với từ khóa kỹ thuật không phụ thuộc ngôn ngữ, chỉ định chính sách cố định:

```java
String raw = "TITLE";
System.out.println(raw.toLowerCase(java.util.Locale.ROOT));                 // title
System.out.println(raw.toLowerCase(java.util.Locale.forLanguageTag("tr"))); // tıtle
```

Đây là snippet trong phương thức. Bài riêng về chữ I Thổ Nhĩ Kỳ phân tích đủ `I/i/İ/ı`; ở đây điều cần rút ra là **khóa ổn định phải được chuẩn hóa bằng cùng quy tắc ở nơi ghi và nơi đọc**.

## 3. Khi nào `Locale.ROOT` phù hợp?

Đây là lựa chọn thường hữu ích khi **đồng thời** thỏa hai điều kiện: (1) hợp đồng dữ liệu cho phép hoặc yêu cầu so khớp không phân biệt hoa/thường; (2) việc đổi hoa/thường phải độc lập với ngôn ngữ của người sử dụng.

| Dữ liệu hoặc tình huống | Cách cân nhắc | Lý do |
| --- | --- | --- |
| Khóa cấu hình nội bộ do ứng dụng tự đặt và quy định không phân biệt hoa/thường | `trim/strip` nếu được phép, sau đó `toLowerCase(Locale.ROOT)` | Không bị thay đổi bởi locale mặc định |
| Tên lựa chọn hoặc từ khóa command trong DSL tự thiết kế | `Locale.ROOT` nếu quy tắc DSL cho phép Unicode case mapping, hoặc tự xử lý ASCII theo đặc tả | Phải tuân theo grammar/contract thực tế |
| Nhãn phân loại kỹ thuật như `"PENDING"`, `"RUNNING"` | Chuẩn hóa bằng `Locale.ROOT` nếu API chấp nhận các biến thể chữ hoa/thường | Tạo khóa tra cứu nhất quán trong ứng dụng |
| Dữ liệu giao thức như HTTP field name | Tuân theo chính xác quy tắc giao thức; thường chỉ có ASCII và so khớp không phân biệt hoa/thường | Có thể dùng ASCII-only comparator hoặc xử lý theo thư viện giao thức; không mặc định mọi Unicode đều hợp lệ |
| Văn bản giao diện để hiển thị bằng ngôn ngữ của người dùng | `toLowerCase(userLocale)` / `toUpperCase(userLocale)` khi thực sự cần | Quy tắc ngôn ngữ địa phương có ý nghĩa |
| Tên người, tên địa danh, nội dung do người dùng nhập | Không tự ý đổi, hoặc áp dụng chính sách ngôn ngữ/so khớp phù hợp | Chuyển case có thể làm mất thông tin hoặc sai ngôn ngữ |
| Đường dẫn, URL, tên file, khóa database, username, email | Xem đặc tả và hệ thống đích trước khi thay đổi | Các phần tử có thể phân biệt hoa/thường hoặc có quy tắc riêng |

**Quy tắc quyết định:** `Locale.ROOT` phù hợp với việc áp dụng **Unicode case mapping trung lập** cho dữ liệu mà *chính hợp đồng* cho phép chuẩn hóa theo cách đó. Nó không tạo ra quyền tự đổi bất kỳ định danh nào sang lowercase.

## 4. Phân biệt các API liên quan

| API | Cơ chế | Khi dùng |
| --- | --- | --- |
| `s.toLowerCase()` | Dùng locale mặc định của JVM | Khi thực sự muốn theo locale mặc định, dù điều này thường chưa đủ rõ cho nghiệp vụ |
| `s.toLowerCase(Locale.ROOT)` | Dùng quy tắc Unicode không theo một ngôn ngữ cụ thể | Chuẩn hóa kỹ thuật có hợp đồng cho phép |
| `s.toUpperCase(Locale.ROOT)` | Tương tự nhưng đổi sang chữ hoa | Khi hợp đồng dùng dạng hoa làm chuẩn |
| `s.toLowerCase(userLocale)` | Áp dụng quy tắc cho locale người dùng | Văn bản ngôn ngữ tự nhiên |
| `s.equals(other)` | So sánh nội dung chính xác | Khi phân biệt hoa/thường hoặc hai giá trị đã được chuẩn hóa đồng nhất |
| `s.equalsIgnoreCase(other)` | So sánh không phân biệt hoa/thường theo quy tắc của Java, không dựa vào `Locale.ROOT` | Trường hợp so sánh đơn giản phù hợp với hợp đồng |
| `Normalizer.normalize(s, Form.NFC)` | Chuẩn hóa dạng biểu diễn Unicode, **không đổi hoa/thường** | Khi cần gộp các dạng Unicode tương đương theo chuẩn NFC |
| `Collator` | So sánh/sắp xếp văn bản theo các quy tắc locale | Ngôn ngữ tự nhiên, không phải mặc định cho mã định danh kỹ thuật |

Các thao tác trên **không thay đổi chính đối tượng `String`**, vì `String` bất biến. Những phương thức chuyển đổi trả về giá trị `String`; muốn sử dụng kết quả phải gán hoặc chuyển tiếp giá trị ấy.

### `equalsIgnoreCase` không đồng nghĩa với lowercase `Locale.ROOT`

`equalsIgnoreCase` so sánh theo quy tắc riêng của `String`, không phải thao tác “đưa hai vế về lowercase bằng `Locale.ROOT` rồi dùng `equals`”. Nó cũng không thực hiện Unicode normalization hay locale-aware collation đầy đủ.

Vì vậy, **không thay thế tùy ý**:

```java
boolean same1 = left.equalsIgnoreCase(right);
boolean same2 = left.toLowerCase(Locale.ROOT)
        .equals(right.toLowerCase(Locale.ROOT));
```

Đây là hai chiến lược có thể khác nhau với một số ký tự Unicode. Trong ứng dụng có quy định chặt chẽ về so khớp định danh, hãy chọn một quy tắc có tài liệu và sử dụng thống nhất ở cả bên tạo khóa và bên tra cứu; kiểm thử với dữ liệu đa ngôn ngữ.

## 5. Kiểm thử quy tắc định danh ở đúng điểm sử dụng

Với từ khóa kỹ thuật, một ví dụ chạy được trên máy phát triển chưa chứng minh chính sách đúng. Kiểm thử đầu vào hợp lệ, ký tự ngoài grammar, khoảng trắng và hành vi khi thay locale mặc định. Những phép kiểm tra sau minh họa sự khác nhau giữa một mã **không có `I`** và một mã **có `I`**:

```java
java.util.Locale tr = java.util.Locale.forLanguageTag("tr");
System.out.println("READ".toLowerCase(tr)
        .equals("READ".toLowerCase(java.util.Locale.ROOT)));  // true
System.out.println("TITLE".toLowerCase(tr)
        .equals("TITLE".toLowerCase(java.util.Locale.ROOT))); // false
```

Nếu chỉ kiểm thử các mã như `READ`, ta **bỏ sót Turkish-I**. Hãy thêm dữ liệu thử nhắm vào ranh giới grammar. Với định danh ASCII-only, kiểm tra grammar trước khi tạo khóa và kiểm tra va chạm giữa các giá trị đầu vào được chấp nhận.

## 6. Thiết kế hàm chuẩn hóa khóa kỹ thuật

Giả sử ứng dụng của bạn có một tập **tên lựa chọn do chính bạn định nghĩa**, không phân biệt hoa/thường, cho phép khoảng trắng thừa đầu/cuối và có ký tự Unicode hợp lệ. Khi đó có thể định nghĩa một hàm chuẩn hóa thống nhất.

Đây là lớp đầy đủ có thể lưu trong `TechnicalKey.java`:

```java
import java.util.Locale;
import java.util.Objects;

public final class TechnicalKey {
    private TechnicalKey() {
    }

    /**
     * Hợp đồng của ứng dụng:
     * - Đầu vào không null;
     * - Bỏ whitespace ở hai đầu theo String.strip();
     * - Chuyển chữ thường không phụ thuộc locale mặc định.
     */
    public static String normalize(String raw) {
        Objects.requireNonNull(raw, "raw");
        return raw.strip().toLowerCase(Locale.ROOT);
    }

    public static void main(String[] args) {
        System.out.println(normalize("  ADMIN  "));
        System.out.println(normalize("Title"));
        System.out.println(normalize("TITLE"));
        System.out.println(normalize(" USER_NAME "));
        System.out.println(normalize("Title").equals(normalize("TITLE")));
    }
}
```

Chạy:

```bash
javac TechnicalKey.java
java TechnicalKey
```

Kết quả:

```text
admin
title
title
user_name
true
```

Trong ứng dụng thật, nên **dùng cùng hàm `normalize`** khi lưu khóa và khi tra cứu. Nếu chỉ chuẩn hóa khi đọc mà không chuẩn hóa lúc tạo, hai bên có thể không khớp.

**Nhưng:** đây là chính sách do ví dụ đặt ra, không phải chuẩn chung cho mọi khóa. Có hệ thống cần phân biệt `" Admin"` với `"Admin"`, cần giữ chữ hoa/thường hoặc chỉ chấp nhận ASCII. Khi đó hàm trên **không phù hợp**; hãy định nghĩa hợp đồng khác thay vì tự ý cắt khoảng trắng và lowercase.

### Nếu định danh chỉ cho phép ASCII

Nhiều định danh trong giao thức hoặc DSL được quy định bằng các ký tự ASCII như `A–Z`, `a–z`, `0–9`, `_` và `-`. Với kiểu dữ liệu này, nên **kiểm tra tập ký tự hợp lệ theo grammar trước** và xử lý case theo đúng đặc tả.

```java
static String normalizeAsciiKey(String input) {
    java.util.Objects.requireNonNull(input, "input");
    if (!input.matches("[A-Za-z][A-Za-z0-9_-]*")) {
        throw new IllegalArgumentException("Invalid ASCII key");
    }
    return input.toLowerCase(java.util.Locale.ROOT);
}
```

Đây là **phương thức minh họa để đặt trong một lớp**, không phải chương trình độc lập. Sau khi đã kiểm tra toàn bộ chuỗi là ASCII, `Locale.ROOT` cho cách lowercase dự kiến cho `A–Z` mà không bị ảnh hưởng bởi locale của máy chạy. Với quy tắc ASCII phức tạp hơn, một bộ xử lý ASCII riêng hoặc thư viện giao thức cũng có thể phù hợp.

`matches` kiểm tra theo biểu thức chính quy và được gọi mỗi lần ở ví dụ cho dễ đọc; nếu xử lý dữ liệu lớn ở đường nóng, có thể biên dịch sẵn `Pattern` hoặc dùng parser tối ưu phù hợp sau khi đo.

## 7. Giới hạn của lowercase `Locale.ROOT`

`Locale.ROOT` chọn quy tắc chuyển chữ trung tính; **nó không phải** Unicode normalization, full Unicode case folding hay cơ chế chống giả mạo định danh. Chẳng hạn `"\u00E9"` và `"e\u0301"` vẫn có thể khác nhau sau lowercase; `"\u0130"` có thể tạo dãy `"i\u0307"`. Nếu hợp đồng khóa yêu cầu NFC, hãy xác định vị trí chuẩn hóa và kiểm tra lại sau biến đổi case khi cần.

```java
String key = java.text.Normalizer.normalize(
        input.toLowerCase(java.util.Locale.ROOT),
        java.text.Normalizer.Form.NFC);
```

Đây là **hợp đồng minh họa**, không phải chuẩn chung cho username hay domain. Với Unicode case folding đầy đủ, IDN hoặc danh tính, hãy dùng đặc tả và công cụ chuyên dụng; chuẩn hóa compatibility có thể gộp ký tự mà ứng dụng cần phân biệt.

## 8. Các vấn đề khi dùng khóa chuẩn hóa trong `Map`, database và API

### 8.1. Nhất quán khi tạo và khi tra cứu

`HashMap<String, ...>` dùng `String.hashCode` và `String.equals`. Hai chuỗi chỉ khác chữ hoa/thường là **hai khóa khác nhau** nếu chưa được chuẩn hóa:

```java
java.util.Map<String, Integer> permissions = new java.util.HashMap<>();
permissions.put("admin", 1);

System.out.println(permissions.get("ADMIN")); // null
System.out.println(permissions.get(
        "ADMIN".toLowerCase(java.util.Locale.ROOT))); // 1
```

Nếu nghiệp vụ quy định khóa không phân biệt hoa/thường, phải áp dụng cùng chính sách khi thêm, tìm, so sánh và kiểm tra trùng lặp. Nếu hợp đồng phân biệt hoa/thường, giữ nguyên khóa và so sánh chính xác.

### 8.2. Chuẩn hóa ở các hệ thống khác nhau

Trong database, `LOWER(...)`, collation và Unicode normalization có thể dùng những quy tắc khác Java. Java service và cơ sở dữ liệu có thể **không đồng ý** về việc hai chuỗi có tương đương hay không. Nếu danh tính, tính duy nhất hoặc kiểm tra quyền phụ thuộc vào khóa đã chuẩn hóa, hãy quy định nơi chịu trách nhiệm xác thực/chuẩn hóa, dữ liệu nào được lưu và cách kiểm chứng trên mọi tầng.

Kết quả đổi case cũng có thể phụ thuộc vào phiên bản Unicode/JDK. Với hệ thống lưu khóa bền vững qua nâng cấp runtime, cần lưu ý tính ổn định của hợp đồng chuẩn hóa và có kế hoạch kiểm thử/migration nếu áp dụng chính sách Unicode rộng.

### 8.3. Không tự ý lowercase giá trị từ chuẩn bên ngoài

URL có nhiều thành phần với quy tắc phân biệt hoa/thường khác nhau; hostname, path và query không được phép xử lý đồng loạt bằng một lệnh lowercase. Tên tệp có thể phân biệt hoa/thường tùy hệ điều hành/filesystem. Địa chỉ email cũng có nhiều quy tắc riêng giữa domain và local part. Đối với header hoặc token của giao thức, nên dùng đúng parser/comparator theo chuẩn hoặc thư viện giao thức.

`Locale.ROOT` chỉ lựa chọn **locale cho một phép biến đổi String**, không tự định nghĩa “các byte nào là hợp lệ” hoặc “mọi thành phần có được đổi case không”. **Đặc tả của dữ liệu luôn quyết định trước.**

## 9. Lỗi phổ biến

1. **Dùng `toLowerCase()` không tham số để chuẩn hóa từ khóa máy hiểu.** Kết quả phụ thuộc locale mặc định. Nếu muốn quy tắc trung lập, chỉ định `Locale.ROOT` và kiểm tra hợp đồng khóa.
2. **Dùng `Locale.ENGLISH` cho mọi định danh kỹ thuật.** Locale tiếng Anh là một ngôn ngữ cụ thể; `Locale.ROOT` biểu thị ý định trung lập tốt hơn khi quy tắc kỹ thuật cần ngôn ngữ trung lập.
3. **Tự động lowercase username, đường dẫn, URL hay tên file.** Có thể phá vỡ ngữ nghĩa phân biệt hoa/thường. Cần đọc đặc tả/hợp đồng trước khi đổi.
4. **Giả định đổi hoa/thường không đổi `length()`.** Ví dụ `U+0130` có thể trở thành hai code point sau `toLowerCase(Locale.ROOT)`; viết hoa `ß` có thể mở rộng thành `SS`.
5. **Nhầm `Locale.ROOT` với chuẩn hóa Unicode.** Nó không làm `"\u00E9"` và `"e\u0301"` tự bằng nhau. Cần `Normalizer` nếu hợp đồng cho phép coi chúng tương đương.
6. **Nhầm lowercase với full Unicode case folding.** Hai thao tác không có cùng đặc tả. Đối chiếu nhu cầu của hệ thống trước khi chọn.
7. **Chuẩn hóa ở một phía.** Lưu bằng giá trị gốc nhưng tìm bằng giá trị đã lowercase có thể không khớp. Cần áp dụng quy tắc nhất quán tại các điểm tạo và tra cứu khóa.
8. **Gọi `toLowerCase(Locale.ROOT)` trên `null`.** Điều này ném `NullPointerException`. Hãy quyết định rõ null là lỗi, vắng giá trị hay một trạng thái hợp lệ.
9. **Cho rằng lowercase đủ chống mọi giả mạo Unicode.** Nhiều chữ khác bảng chữ cái trông giống nhau (confusables); chuẩn hóa chữ hoa/thường không phải cơ chế kiểm tra chống giả mạo định danh.
10. **Dùng một kết quả chuẩn hóa làm định danh bảo mật mà không kiểm tra va chạm.** Có thể có nhiều giá trị đầu vào trở thành cùng một khóa sau biến đổi; nếu dùng cho đăng nhập/ủy quyền, phải kiểm soát tính duy nhất và kiểm thử tính tương thích với nơi lưu trữ.

## 10. Cách chọn nhanh theo mục đích

| Mục đích | Hướng xử lý |
| --- | --- |
| Hiển thị văn bản cho người dùng theo ngôn ngữ địa phương | Chọn locale người dùng rõ ràng nếu cần case conversion |
| Tạo khóa nội bộ mà hợp đồng quy định không phân biệt hoa/thường, không phụ thuộc ngôn ngữ | `toLowerCase(Locale.ROOT)` hoặc `toUpperCase(Locale.ROOT)`, chọn một chuẩn và dùng nhất quán |
| Định danh thuộc chuẩn ASCII-only | Xác thực grammar ASCII, rồi dùng quy tắc case đúng theo chuẩn; có thể dùng `Locale.ROOT` sau bước xác thực |
| So sánh chính xác nội dung `String` | `equals` hoặc `Objects.equals` nếu có thể `null` |
| Cần so sánh Unicode không phân biệt hoa/thường theo chuẩn riêng | Chọn chiến lược case folding/collation có định nghĩa rõ, không tự suy từ `Locale.ROOT` |
| Cần gộp các biểu diễn Unicode tương đương | Xác định NFC/NFKC... theo yêu cầu trước khi chuẩn hóa |
| Identifier có ý nghĩa phân biệt chữ hoa/thường | Giữ nguyên và so sánh chính xác |

## 11. Tự kiểm tra

Hãy dự đoán kết quả:

```java
java.util.Locale tr = java.util.Locale.forLanguageTag("tr");
String source = "FILE";

System.out.println(source.toLowerCase(java.util.Locale.ROOT)
        .equals("file"));
System.out.println(source.toLowerCase(tr).equals("file"));
System.out.println("  ADMIN  ".strip()
        .toLowerCase(java.util.Locale.ROOT));
```

Kết quả:

```text
true
false
admin
```

Hai dòng đầu khác nhau do chữ `I` có cách xử lý hoa/thường đặc thù trong tiếng Thổ Nhĩ Kỳ; dòng cuối sử dụng một hợp đồng tự định nghĩa vừa bỏ whitespace đầu/cuối vừa chuẩn hóa chữ thường.

**Ghi nhớ:** Với **định danh kỹ thuật có quy ước so khớp không phân biệt hoa/thường và không theo một ngôn ngữ**, `Locale.ROOT` là cách nêu rõ ý định trong Java. Với văn bản hiển thị hoặc định danh theo chuẩn riêng, **hãy tuân thủ locale, grammar, Unicode policy và quy tắc so sánh của chính bài toán**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="case-normalization-003">Trường hợp chữ I của tiếng Thổ Nhĩ Kỳ</a>

<details>
<summary>Xem chi tiết</summary>

# Trường hợp chữ I của tiếng Thổ Nhĩ Kỳ trong Java

Chuyển chữ hoa/thường tưởng như chỉ cần gọi `toLowerCase()` hoặc `toUpperCase()`. Nhưng tiếng Thổ Nhĩ Kỳ (Turkish) có quy tắc đặc biệt với chữ **I**: chữ **I** không dấu chấm phía trên đi cặp với **ı** thường không dấu chấm; chữ **İ** hoa có dấu chấm đi cặp với **i** thường có dấu chấm. Nếu sử dụng locale không đúng, ứng dụng có thể đổi sai tên người, từ ngữ, khóa tìm kiếm hoặc mã định danh.

Trong Java, ba câu hỏi khác nhau phải được tách bạch:

1. **Chuyển chữ cho người đọc thuộc ngôn ngữ nào?** Dùng `String.toLowerCase(Locale)` / `toUpperCase(Locale)` với locale phù hợp.
2. **Chuẩn hóa chuỗi kỹ thuật không phụ thuộc ngôn ngữ?** Dùng chính sách xác định, thường là `Locale.ROOT` với dữ liệu Unicode nếu quy ước cho phép, hoặc quy tắc ASCII tường minh nếu giao thức chỉ định ASCII.
3. **Hai chuỗi có bằng nhau theo quy tắc nào?** `equals()`, `equalsIgnoreCase()` và so sánh theo ngôn ngữ có hợp đồng khác nhau. Đặc biệt, `equalsIgnoreCase()` **không thực hiện phép so sánh riêng cho tiếng Thổ Nhĩ Kỳ**.

## 1. Bốn chữ I/i rất dễ nhầm

| Ký tự | Unicode code point | Tên Unicode | Nhận biết |
| --- | --- | --- | --- |
| `I` | `U+0049` | LATIN CAPITAL LETTER I | Hoa **không có chấm** |
| `i` | `U+0069` | LATIN SMALL LETTER I | Thường **có chấm** |
| `İ` | `U+0130` | LATIN CAPITAL LETTER I WITH DOT ABOVE | Hoa **có chấm** |
| `ı` | `U+0131` | LATIN SMALL LETTER DOTLESS I | Thường **không có chấm** |

Quy tắc cặp chữ trong tiếng Thổ Nhĩ Kỳ:

~~~text
I  <-->  ı     (không có dấu chấm)
İ  <-->  i     (có dấu chấm)
~~~

Trong tiếng Anh, nhiều người quen với cặp `I ↔ i`. Áp dụng thói quen đó cho mọi ngôn ngữ sẽ làm sai chữ Thổ Nhĩ Kỳ.

Ví dụ từ `"istanbul"` được viết hoa theo locale Thổ Nhĩ Kỳ thành `"İSTANBUL"`, có chữ `İ` ở đầu. Tương tự, chữ `I` trong một từ tiếng Thổ Nhĩ Kỳ khi chuyển về chữ thường sẽ thành `ı`, không phải `i`. Không nên dựa vào hình dáng ký tự khi đọc log; có thể in code point dưới dạng `U+....` để đối chiếu.

## 2. Java toLowerCase/toUpperCase chịu ảnh hưởng của Locale

Java có hai nhóm overload chính:

~~~java
text.toLowerCase();
text.toUpperCase();

text.toLowerCase(locale);
text.toUpperCase(locale);
~~~

Đây là **các lời gọi minh họa**, giả sử `text` là `String` và `locale` là `java.util.Locale`.

- **Không truyền đối số:** `toLowerCase()` và `toUpperCase()` dùng **locale mặc định** của JVM, tức `Locale.getDefault()`. Kết quả có thể khác khi ứng dụng chạy ở máy hoặc cấu hình locale khác.
- **Truyền locale rõ ràng:** `toLowerCase(Locale)` và `toUpperCase(Locale)` dùng quy tắc hoa/thường theo locale được chỉ định. Đây là cách nên dùng khi yêu cầu kết quả nhất quán.
- `Locale.forLanguageTag("tr-TR")` tạo locale tiếng Thổ Nhĩ Kỳ dùng trong ví dụ. `Locale.ROOT` là locale trung lập ngôn ngữ, hữu ích cho nhiều thao tác xử lý chuỗi kỹ thuật.

Ví dụ:

~~~java
import java.util.Locale;

// Đoạn lệnh bên trong một phương thức Java:
Locale tr = Locale.forLanguageTag("tr-TR");
Locale root = Locale.ROOT;

System.out.println("TITLE".toLowerCase(root)); // title
System.out.println("TITLE".toLowerCase(tr));   // tıtle
System.out.println("istanbul".toUpperCase(root)); // ISTANBUL
System.out.println("istanbul".toUpperCase(tr));   // İSTANBUL
~~~

Bốn dòng `println` tạo kết quả minh họa như trong chú thích. Trong `tıtle`, ký tự thứ hai là `ı (U+0131)`. Trong `İSTANBUL`, ký tự đầu là `İ (U+0130)`.

**Không nên dùng `toLowerCase()` không đối số để sinh khóa kỹ thuật** khi cần kết quả thống nhất ở mọi nơi. Nếu JVM có locale Thổ Nhĩ Kỳ, `"TITLE".toLowerCase()` có thể thành `"tıtle"`; ở môi trường tiếng Anh, nó có thể thành `"title"`. Hai chuỗi khác nhau theo `equals()`, và mã băm `hashCode()` có thể khác. Điều này có thể ảnh hưởng đến tên khóa, định tuyến, phân quyền hoặc tra cứu nếu thiết kế phụ thuộc vào một quy tắc chữ thường cố định.

### Bảng chuyển đổi code point dưới hai locale

Các giá trị sau được xác minh bằng Java:

| Ký tự đầu vào | `toLowerCase(Locale.ROOT)` | `toLowerCase(tr-TR)` | `toUpperCase(Locale.ROOT)` | `toUpperCase(tr-TR)` |
| --- | --- | --- | --- | --- |
| `I (U+0049)` | `i (U+0069)` | `ı (U+0131)` | `I (U+0049)` | `I (U+0049)` |
| `i (U+0069)` | `i (U+0069)` | `i (U+0069)` | `I (U+0049)` | `İ (U+0130)` |
| `İ (U+0130)` | **`i + U+0307`** (2 code point) | `i (U+0069)` | `İ (U+0130)` | `İ (U+0130)` |
| `ı (U+0131)` | `ı (U+0131)` | `ı (U+0131)` | `I (U+0049)` | `I (U+0049)` |

Điểm tinh tế: `"İ".toLowerCase(Locale.ROOT)` không chỉ ra một `i` đơn lẻ; kết quả là **`i` rồi đến dấu chấm kết hợp `U+0307`**. Vì vậy chuyển chữ hoa/thường ở cấp `String` có thể **thay đổi số code point và `length()`**. Không được mặc định chuyển đổi 1 ký tự đầu vào luôn tương ứng 1 ký tự đầu ra.

## 3. Ví dụ Java 17+ chạy độc lập và xem chính xác code point

Để không phụ thuộc font của console (chữ `I/i/İ/ı` có thể khó phân biệt), chương trình in các Unicode code point dưới dạng hex. Lưu vào tệp **TurkishIDemo.java**:

~~~java
import java.util.Locale;
import java.util.stream.Collectors;

public class TurkishIDemo {
    private static String codePoints(String text) {
        return text.codePoints()
                .mapToObj(cp -> String.format(Locale.ROOT, "U+%04X", cp))
                .collect(Collectors.joining(" "));
    }

    public static void main(String[] args) {
        Locale root = Locale.ROOT;
        Locale tr = Locale.forLanguageTag("tr-TR");

        String[] letters = {"I", "i", "\u0130", "\u0131"};
        for (String letter : letters) {
            System.out.printf(
                    "%s | ROOT lower=%s | TR lower=%s | ROOT upper=%s | TR upper=%s%n",
                    codePoints(letter),
                    codePoints(letter.toLowerCase(root)),
                    codePoints(letter.toLowerCase(tr)),
                    codePoints(letter.toUpperCase(root)),
                    codePoints(letter.toUpperCase(tr)));
        }

        System.out.println("TITLE ROOT lower = "
                + codePoints("TITLE".toLowerCase(root)));
        System.out.println("TITLE TR lower = "
                + codePoints("TITLE".toLowerCase(tr)));
        System.out.println("istanbul ROOT upper = "
                + codePoints("istanbul".toUpperCase(root)));
        System.out.println("istanbul TR upper = "
                + codePoints("istanbul".toUpperCase(tr)));

        System.out.println("I ignoreCase i = "
                + "I".equalsIgnoreCase("i"));
        System.out.println("I ignoreCase dotless = "
                + "I".equalsIgnoreCase("\u0131"));
        System.out.println("I ignoreCase dottedCapital = "
                + "I".equalsIgnoreCase("\u0130"));

        System.out.println("TR lower I equals lower i = "
                + "I".toLowerCase(tr).equals("i".toLowerCase(tr)));
        System.out.println("TR lower I equals lower dotless = "
                + "I".toLowerCase(tr).equals("\u0131".toLowerCase(tr)));
        System.out.println("ROOT lower dotted capital length = "
                + "\u0130".toLowerCase(root).length());
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 TurkishIDemo.java
java TurkishIDemo
~~~

**Stdout:**

~~~text
U+0049 | ROOT lower=U+0069 | TR lower=U+0131 | ROOT upper=U+0049 | TR upper=U+0049
U+0069 | ROOT lower=U+0069 | TR lower=U+0069 | ROOT upper=U+0049 | TR upper=U+0130
U+0130 | ROOT lower=U+0069 U+0307 | TR lower=U+0069 | ROOT upper=U+0130 | TR upper=U+0130
U+0131 | ROOT lower=U+0131 | TR lower=U+0131 | ROOT upper=U+0049 | TR upper=U+0049
TITLE ROOT lower = U+0074 U+0069 U+0074 U+006C U+0065
TITLE TR lower = U+0074 U+0131 U+0074 U+006C U+0065
istanbul ROOT upper = U+0049 U+0053 U+0054 U+0041 U+004E U+0042 U+0055 U+004C
istanbul TR upper = U+0130 U+0053 U+0054 U+0041 U+004E U+0042 U+0055 U+004C
I ignoreCase i = true
I ignoreCase dotless = true
I ignoreCase dottedCapital = true
TR lower I equals lower i = false
TR lower I equals lower dotless = true
ROOT lower dotted capital length = 2
~~~

Những kết quả cốt lõi:

1. Dòng `U+0049` chứng minh `I` thành `i` với `ROOT` nhưng thành `ı` với `tr-TR`.
2. Dòng `U+0069` chứng minh `i` thành `I` với `ROOT` nhưng thành `İ` với `tr-TR`.
3. Dòng `U+0130` chứng minh phép chuyển thành chữ thường có thể tạo **hai code point** khi dùng `ROOT`.
4. Kết quả `equalsIgnoreCase()` là `true` cho cả ba so sánh có `I`, trong khi quy tắc chữ thường tiếng Thổ Nhĩ Kỳ phân biệt `I` với `i`.
5. Vì vậy, một chức năng muốn so sánh “bằng nhau không phân biệt hoa/thường theo tiếng Thổ Nhĩ Kỳ” **không thể mặc định dùng `equalsIgnoreCase()`**.

Mã nguồn sử dụng escape `\u0130` và `\u0131` để thể hiện hai ký tự đặc biệt mà không phụ thuộc khả năng gõ phím hoặc font; giá trị thực vẫn là hai Unicode code point tương ứng.

## 4. Vì sao equalsIgnoreCase() có thể gây bất ngờ?

`String.equalsIgnoreCase()` là phép so sánh nội dung **không phân biệt hoa/thường theo quy tắc Java**, không nhận `Locale` và không dùng quy tắc Thổ Nhĩ Kỳ khi so sánh. Theo hợp đồng của API, các phép chuyển chữ đơn giản của `Character` được dùng để xác định ký tự có tương đương khi bỏ qua hoa/thường hay không.

Trong Java hiện đại, cả bốn giá trị `I`, `i`, `İ`, `ı` có thể được `equalsIgnoreCase()` coi là bằng nhau từng cặp. Điều đó **khác** cặp chữ đúng của tiếng Thổ Nhĩ Kỳ:

~~~text
Theo tiếng Thổ Nhĩ Kỳ:
    I  ↔  ı
    İ  ↔  i

Với String.equalsIgnoreCase:
    I, i, İ, ı có thể được coi tương đương,
    nên phép so sánh này không tách hai cặp của tiếng Thổ Nhĩ Kỳ.
~~~

Ví dụ:

~~~java
Locale tr = Locale.forLanguageTag("tr-TR");

System.out.println("I".equalsIgnoreCase("i")); // true
System.out.println("I".toLowerCase(tr)
        .equals("i".toLowerCase(tr)));         // false
~~~

Đây là **đoạn mã minh họa**, cần đặt trong một phương thức có import `Locale`.

Không có mâu thuẫn: **mỗi API có một hợp đồng khác nhau**. `equalsIgnoreCase()` không hứa cung cấp Unicode full case folding theo từng ngôn ngữ; `toLowerCase(tr)` được thiết kế cho việc chuyển chữ theo quy tắc locale được truyền.

### Phân biệt thêm với equals(), compareToIgnoreCase()

- `equals()` so sánh dãy code unit UTF-16 **nguyên văn**. `"I".equals("i")` trả về `false`.
- `equalsIgnoreCase()` bỏ qua hoa/thường theo quy tắc Java, **không locale-sensitive**.
- `compareToIgnoreCase()` và `String.CASE_INSENSITIVE_ORDER` dùng thứ tự so sánh không phân biệt hoa/thường theo Java, **không thay thế phép sắp xếp/so khớp tiếng Thổ Nhĩ Kỳ**.
- `java.text.Collator` cung cấp cơ chế so sánh văn bản theo locale, bao gồm quy tắc sắp xếp (*collation*) của ngôn ngữ. Tuy nhiên, việc chọn mức độ so sánh `PRIMARY`, `SECONDARY` hay `TERTIARY` còn ảnh hưởng tới dấu và những khác biệt khác; không nên tự coi mọi cấu hình Collator là tương đương chính xác với “bỏ qua hoa/thường”.

Nếu yêu cầu sản phẩm là so sánh hoặc sắp xếp tên tiếng Thổ Nhĩ Kỳ, cần quy định rõ có phân biệt dấu, chuẩn hóa Unicode và phương thức collation nào phù hợp rồi **kiểm thử trên văn bản bản địa**.

## 5. Chuyển chữ cho nội dung hiển thị và cho khóa kỹ thuật

### Trường hợp A: văn bản dành cho người dùng tiếng Thổ Nhĩ Kỳ

Khi hiển thị văn bản, tạo nhãn hoặc biến đổi dữ liệu theo chính tả tiếng Thổ Nhĩ Kỳ, chọn locale rõ ràng:

~~~java
Locale turkish = Locale.forLanguageTag("tr-TR");
String upper = "istanbul".toUpperCase(turkish); // "İSTANBUL"
String lower = "I".toLowerCase(turkish);        // "ı"
~~~

Đây là **đoạn mã minh họa**, cần đặt trong phương thức Java. Đặt locale từ cấu hình ngôn ngữ thật của dữ liệu hay người dùng, không suy ra locale chỉ từ vị trí đặt máy chủ.

### Trường hợp B: khóa kỹ thuật, từ khóa giao thức hoặc tên trường không phụ thuộc locale

Ví dụ một giao thức định nghĩa các từ khóa `"TITLE"` và `"title"` tương đương bất kể ngôn ngữ người dùng. Nếu chuẩn hóa bằng locale mặc định của JVM, một máy cấu hình `tr-TR` có thể tạo `"tıtle"` và không khớp khóa `"title"`.

Giải pháp thường dùng:

~~~java
Locale root = Locale.ROOT;
String key = "TITLE".toLowerCase(root);
System.out.println(key.equals("title")); // true
~~~

**Lưu ý quan trọng:** `Locale.ROOT` là lựa chọn không phụ thuộc ngôn ngữ, nhưng nó **không tự giải quyết mọi quy tắc định danh Unicode hoặc mọi loại case folding**. Nếu chuẩn/giao thức chỉ chấp nhận ASCII, có thể tốt hơn nếu **kiểm tra dữ liệu chỉ chứa ASCII** và thực hiện quy tắc hoa/thường ASCII được đặc tả. Với tên miền, địa chỉ email, username, mã định danh bảo mật, định dạng giao thức và quy tắc xác thực, hãy tuân theo **chuẩn riêng của lĩnh vực đó**; không mặc định một lần `toLowerCase(Locale.ROOT)` là đủ để đồng nhất mọi dữ liệu Unicode.

### Trường hợp C: khóa tra cứu được định nghĩa “không phân biệt hoa/thường theo tiếng Thổ Nhĩ Kỳ”

Một cách tiếp cận đơn giản khi quy tắc ứng dụng **thật sự** là so sánh theo kết quả lowercasing Thổ Nhĩ Kỳ: tạo khóa chuẩn bằng **cùng locale** trước khi thêm và trước khi tra cứu.

~~~java
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

// Đoạn mã minh họa bên trong một phương thức:
Locale tr = Locale.forLanguageTag("tr-TR");
Map<String, String> names = new HashMap<>();

names.put("I".toLowerCase(tr), "dotless pair");
names.put("İ".toLowerCase(tr), "dotted pair");

System.out.println(names.get("ı".toLowerCase(tr))); // dotless pair
System.out.println(names.get("i".toLowerCase(tr))); // dotted pair
System.out.println(names.size());                  // 2
~~~

Cách này **phù hợp với chính sách ánh xạ khóa đã định nghĩa**: hai cặp chữ không bị trộn. Nó không phải một định nghĩa tổng quát của mọi quy tắc Unicode hay collation tiếng Thổ Nhĩ Kỳ. Nếu yêu cầu còn có các dạng Unicode chuẩn hóa khác nhau, phải quyết định thêm cách normalize, các trường hợp chữ nhiều code point và chính sách phân biệt dấu trước khi làm khóa.

Không phối hợp kiểu **ghi bằng `toLowerCase(tr)` nhưng tìm bằng `toLowerCase(Locale.ROOT)`**: từ `I` sinh ra hai chuỗi khác nhau `ı` và `i`, dẫn đến tra cứu sai.

## 6. Độ dài và ký tự kết hợp: chữ İ là ví dụ đặc biệt

Để minh họa vì sao chuyển hoa/thường không luôn là phép ánh xạ 1 `char` → 1 `char`:

~~~java
String capitalDottedI = "\u0130";
String lowerRoot = capitalDottedI.toLowerCase(Locale.ROOT);
String lowerTurkish = capitalDottedI.toLowerCase(
        Locale.forLanguageTag("tr-TR"));

System.out.println(capitalDottedI.length()); // 1
System.out.println(lowerRoot.length());      // 2
System.out.println(lowerTurkish.length());   // 1
~~~

Đây là **đoạn mã minh họa** trong phương thức Java. Với `ROOT`, kết quả là `U+0069 U+0307`: chữ `i` rồi dấu chấm kết hợp (*COMBINING DOT ABOVE*). Với `tr-TR`, kết quả là một chữ `i (U+0069)`.

Hệ quả:

- Không cấp phát bộ đệm dựa trên giả định “đổi case không đổi `String.length()`”.
- Không duyệt bằng `charAt(i)` rồi áp dụng một phép đổi chữ đơn giản trên mỗi `char` nếu muốn mô phỏng mọi quy tắc `String.toLowerCase(locale)`. Unicode case mapping có thể phụ thuộc ngữ cảnh, locale và có thể tạo nhiều code point.
- Khi tính vị trí cắt chuỗi sau phép chuyển chữ, không được tự giả định các UTF-16 offset trước và sau chuyển đổi sẽ luôn giữ nguyên.
- **Unicode normalization** (ví dụ `java.text.Normalizer`) và **locale-sensitive case conversion** là hai bước có mục đích khác nhau. Việc chuẩn hóa dạng tổ hợp Unicode không tự thay đổi quy tắc rằng `I` và `i` là hai chữ thuộc hai cặp khác nhau trong tiếng Thổ Nhĩ Kỳ.

### Character.toLowerCase() không thay thế được String.toLowerCase(Locale)

`Character.toLowerCase(char)` / `Character.toLowerCase(int)` thực hiện ánh xạ ký tự ở mức thấp, không nhận locale và không có khả năng trả lại chuỗi nhiều ký tự như `"i\u0307"` từ một lời gọi trả về `char`/`int`. Với dữ liệu dạng một từ hoặc câu, cần chuyển case theo ngôn ngữ, **ưu tiên `String.toLowerCase(Locale)`**.

## 7. Locale mặc định ảnh hưởng thế nào đến ứng dụng?

Xét mã:

~~~java
String command = "QUIT".toLowerCase();
~~~

Khi Java dùng locale mặc định Thổ Nhĩ Kỳ, chữ `I` trong `QUIT` có thể biến thành `ı`, tạo ra `"quıt"` (dotless i). Nếu chương trình tiếp tục so sánh nguyên văn với `"quit"`, phép so sánh có thể thất bại. Đây là lỗi do **phụ thuộc môi trường khi thực hiện chuyển case**, không phải do Unicode hay Java làm hỏng chuỗi.

Tránh sửa toàn bộ trạng thái ứng dụng bằng `Locale.setDefault(...)` chỉ để giải quyết một thao tác chuyển case. Locale mặc định là trạng thái chung của JVM và có thể ảnh hưởng các thành phần khác dùng locale mặc định. **Truyền locale ngay tại lời gọi** khiến hợp đồng rõ hơn và ít gây tác động phụ.

Một số tình huống kiểm tra cần thiết:

| Dữ liệu/ngữ cảnh | Mong muốn | Cách xử lý phù hợp |
| --- | --- | --- |
| Nhãn hoặc từ tiếng Thổ Nhĩ Kỳ | Tuân chính tả `I ↔ ı`, `İ ↔ i` | `toLowerCase(tr)` / `toUpperCase(tr)` |
| Từ khóa phần mềm được đặc tả không phụ thuộc ngôn ngữ | Kết quả ổn định mọi máy | Chính sách kỹ thuật cố định, có thể dùng `Locale.ROOT` khi phù hợp |
| Tìm kiếm không phân biệt hoa/thường trong tiếng Thổ Nhĩ Kỳ | Không gộp sai hai cặp I | Quy tắc so sánh/chuẩn hóa theo locale, hoặc Collator được cấu hình và kiểm thử |
| So sánh nội dung nguyên văn | Phân biệt chính xác dãy UTF-16 | `equals()` |
| Thử nghiệm môi trường | Phát hiện phụ thuộc locale ngầm | Kiểm thử với `en-US`, `tr-TR`, `Locale.ROOT` và các ví dụ I/i/İ/ı |
| Hệ thống có quy định ASCII-only | Tuân đúng tập và quy tắc ASCII | Kiểm tra ASCII, biến đổi theo quy tắc giao thức, không tùy tiện Unicode-fold |

## 8. Các lỗi phổ biến

| Sai lầm | Hậu quả | Cách sửa |
| --- | --- | --- |
| `"TITLE".toLowerCase()` để sinh khóa ổn định | Kết quả đổi theo default locale, có thể sinh `tıtle` | Chỉ định chính sách/locale tường minh |
| Coi `I` và `i` là một cặp trong mọi ngôn ngữ | Sai đối với tiếng Thổ Nhĩ Kỳ | Hiểu cả bốn chữ I/i/İ/ı |
| Dùng `equalsIgnoreCase()` cho đối sánh tiếng Thổ Nhĩ Kỳ | Có thể gộp nhầm hai cặp chữ | Dùng cơ chế so sánh theo chính sách ngôn ngữ |
| Cho rằng `Locale.ROOT` là tiếng Thổ Nhĩ Kỳ | ROOT không chứa quy tắc locale Thổ Nhĩ Kỳ | Dùng `Locale.forLanguageTag("tr-TR")` cho tiếng Thổ Nhĩ Kỳ |
| Cho rằng `Locale.ROOT` xử lý mọi vấn đề định danh Unicode | Full Unicode case folding/normalization có nhiều quy tắc | Theo chuẩn dữ liệu và chính sách định danh cụ thể |
| Giả sử `String.toLowerCase()` giữ nguyên độ dài | `İ` chuyển sang ROOT lower thành hai code point | Kiểm tra độ dài và chỉ số sau phép biến đổi |
| Dùng `Character.toLowerCase(char)` để thay mọi trường hợp `String.toLowerCase(locale)` | Mất ngữ cảnh ngôn ngữ và ánh xạ nhiều code point | Dùng API `String` với locale cho văn bản |
| Dùng `==` để so sánh chuỗi sau chuẩn hóa | `==` so sánh danh tính đối tượng | Dùng `equals()` cho giá trị chuỗi |
| Một nơi lưu khóa theo tr-TR, nơi khác tìm theo ROOT | Truy xuất thất bại hoặc trộn quy tắc khóa | Chuẩn hóa và so sánh **nhất quán** |
| Dùng phép chuyển chữ thay cho định nghĩa so sánh tiếng Thổ Nhĩ Kỳ đầy đủ | Có thể bỏ qua collation, normalization, dấu và ngữ cảnh | Định nghĩa tiêu chí rõ và kiểm thử với văn bản thật |

## 9. Checklist trước khi chuyển hoa/thường hoặc so sánh chuỗi

1. **Xác định văn bản thuộc ngôn ngữ nào** và liệu locale của người dùng có liên quan tới phép biến đổi hay không.
2. **Làm rõ mục đích:** hiển thị theo chính tả, sắp xếp tên, tra cứu không phân biệt hoa/thường, hay chuẩn hóa một mã máy đọc.
3. **Chỉ định locale rõ ràng** khi gọi `toLowerCase()` / `toUpperCase()` theo yêu cầu; tránh phụ thuộc default locale khi kết quả cần ổn định.
4. **Với tiếng Thổ Nhĩ Kỳ, thử đủ bốn chữ** `I`, `i`, `İ` và `ı`, cả chữ đơn lẻ và trong từ thực tế.
5. **Không dùng `equalsIgnoreCase()` như một trình đối sánh locale-sensitive**. Xác định rõ quy tắc bằng nhau nào cần được áp dụng.
6. **Không giả định biến đổi 1–1:** `String.toLowerCase(Locale)` có thể tạo nhiều code point, ảnh hưởng chỉ số và độ dài.
7. **Với khóa tra cứu**, áp dụng cùng một quy trình chuẩn hóa/so sánh lúc lưu và lúc tìm; tránh trộn locale giữa hai bước.
8. **Kiểm tra chính sách Unicode:** normalization, full case folding, giới hạn ASCII, xử lý ký tự gần giống nhau, theo đặc tả của ứng dụng.
9. **Tránh thay đổi `Locale.setDefault()` chỉ để sửa một đoạn mã**, vì có thể tác động các phần khác trong JVM.

## 10. Tự kiểm tra

**Câu 1.** Trong tiếng Thổ Nhĩ Kỳ, `I` đổi thành chữ thường nào? `i` đổi thành chữ hoa nào?

**Đáp án:** `I → ı (U+0131)`; `i → İ (U+0130)`.

**Câu 2.** `"TITLE".toLowerCase(Locale.ROOT)` và `"TITLE".toLowerCase(Locale.forLanguageTag("tr-TR"))` có giống nhau không?

**Đáp án:** không. ROOT cho `title`, còn `tr-TR` cho `tıtle`, trong đó chữ thứ hai là dotless `ı`.

**Câu 3.** `"I".equalsIgnoreCase("i")` trả về `true`. Điều đó có chứng minh hai chữ là một cặp hoa/thường đúng trong tiếng Thổ Nhĩ Kỳ không?

**Đáp án:** không. `equalsIgnoreCase()` không dùng locale Thổ Nhĩ Kỳ; theo tiếng Thổ Nhĩ Kỳ, `I` ghép với `ı` và `İ` ghép với `i`.

**Câu 4.** Vì sao `"\u0130".toLowerCase(Locale.ROOT).length() == 2`?

**Đáp án:** kết quả gồm `i (U+0069)` và dấu chấm kết hợp `U+0307`, tức hai UTF-16 code unit. Chuyển case có thể tăng chiều dài chuỗi.

**Câu 5.** Một ứng dụng cần chuẩn hóa từ khóa giao thức như `"QUIT"`, bất kể ngôn ngữ máy chủ. Có nên gọi `"QUIT".toLowerCase()` mà không chỉ định locale không?

**Đáp án:** không. Locale mặc định có thể là tiếng Thổ Nhĩ Kỳ khiến `QUIT` thành `quıt`. Chọn quy tắc kỹ thuật cố định và dùng locale tường minh hoặc thuật toán ASCII theo đúng chuẩn giao thức.

**Câu 6.** Có thể xử lý chính xác mọi loại so sánh văn bản Thổ Nhĩ Kỳ chỉ bằng cách lowercase rồi `equals()` không?

**Đáp án:** không nên khẳng định như vậy. Lowercasing theo locale có thể phù hợp với một **chính sách khóa cụ thể**, nhưng collation, normalization và các mức độ phân biệt dấu có thể yêu cầu công cụ và quy định bổ sung.

## Tài liệu tham khảo

- [Java SE 17 — String](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html): `toLowerCase(Locale)`, `toUpperCase(Locale)`, `equalsIgnoreCase()`, `compareToIgnoreCase()`.
- [Java SE 17 — Locale](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Locale.html): `Locale.ROOT`, `Locale.forLanguageTag()`, default locale.
- [Java SE 17 — Character](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html): ánh xạ hoa/thường mức ký tự và giới hạn so với ánh xạ chuỗi.
- [Java SE 17 — Collator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/Collator.html): so sánh văn bản có nhận biết locale.
- [Unicode SpecialCasing.txt](https://www.unicode.org/Public/UCD/latest/ucd/SpecialCasing.txt): dữ liệu ánh xạ hoa/thường đặc biệt, bao gồm locale/ngữ cảnh.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="case-normalization-004">Đổi hoa–thường có thể làm thay đổi độ dài chuỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Đổi hoa–thường có thể làm thay đổi độ dài chuỗi trong Java

Nhiều lập trình viên quen với ví dụ `"java".toUpperCase()` trả về `"JAVA"` và nghĩ rằng đổi hoa–thường chỉ thay từng ký tự bằng một ký tự khác ở cùng vị trí. **Điều đó không đúng cho Unicode.** Khi dùng `String.toUpperCase(Locale)` hoặc `String.toLowerCase(Locale)`, một ký tự Unicode có thể được ánh xạ thành **nhiều ký tự/code point**, hoặc kết quả phụ thuộc vào ngữ cảnh và ngôn ngữ. Vì vậy, `length()` và các chỉ số UTF-16 của chuỗi kết quả có thể khác chuỗi gốc.

Ví dụ nổi bật trên JDK 17 trở lên:

```java
String before = "Straße";
String after = before.toUpperCase(java.util.Locale.ROOT);

System.out.println(before);           // Straße
System.out.println(after);            // STRASSE
System.out.println(before.length());  // 6
System.out.println(after.length());   // 7
```

Chữ `ß` trong `Straße` có thể được ánh xạ thành hai chữ `SS` khi chuyển hoa. Do đó, nội dung **và cả độ dài** thay đổi. Đây là hành vi chuyển đổi Unicode có chủ đích, không phải lỗi bộ nhớ hay do `String` có thể sửa tại chỗ.

## 1. `String.length()` thật sự đếm gì?

Trong Java, `String.length()` trả về số **UTF-16 code unit** (`char`), không bảo đảm bằng số code point Unicode hay số ký tự mà con người nhận thấy trên màn hình.

Ba khái niệm cần tách:

| Đơn vị | Ý nghĩa | Liên hệ tới Java |
| --- | --- | --- |
| UTF-16 code unit | Một đơn vị 16 bit | `String.length()` và `charAt(index)` dùng đơn vị này |
| Unicode code point | Một mã điểm Unicode, có thể chiếm một hoặc hai UTF-16 code unit | `codePointCount(...)` và `codePoints()` |
| Grapheme cluster | Một cụm ký tự mà người đọc thường coi là một ký tự hiển thị | Có thể gồm nhiều code point, ví dụ chữ và dấu kết hợp; `length()` không đếm cụm này |

Một code point ngoài BMP như U+1F600 (😀) chiếm **hai** UTF-16 code unit. Ngược lại, dấu kết hợp U+0307 là một code point riêng, thường được hiển thị cùng một chữ cái đứng trước. Bởi vậy, thay đổi độ dài theo UTF-16 có thể đến từ việc chuyển một code point thành nhiều code point, hoặc từ cách biểu diễn ký tự mới.

Phép đổi hoa–thường của `String` hoạt động trên văn bản Unicode, không có hợp đồng bảo toàn độ dài tính theo bất kỳ đơn vị nào ở trên. Khi đánh giá kết quả, cần kiểm tra **giá trị kết quả**, không dự đoán kích thước chỉ từ dữ liệu nguồn.

## 2. Vì sao một chữ có thể chuyển thành nhiều chữ?

Unicode hỗ trợ cả **ánh xạ đơn giản (simple case mapping)** và **ánh xạ đầy đủ (full case mapping)**. Ở ánh xạ đơn giản, một code point thường được ánh xạ sang tối đa một code point tương ứng. Ánh xạ đầy đủ có thể tạo ra **dãy gồm nhiều code point**, và một số quy tắc còn dựa vào locale hoặc các ký tự xung quanh.

Các phương thức xử lý toàn chuỗi `String.toUpperCase(Locale)` và `String.toLowerCase(Locale)` có thể áp dụng ánh xạ đầy đủ. Vì vậy không nên hình dung kết quả luôn được tạo bằng cách duyệt từng `char` và thay một-một.

Một trường hợp dễ thấy là chữ **sharp s** (`ß`, U+00DF) của tiếng Đức:

```text
"Straße"                  : 6 UTF-16 code unit
"Straße".toUpperCase(ROOT): "STRASSE", 7 UTF-16 code unit
                                 ^^
                             ß -> SS
```

Lưu ý chữ hoa Unicode `ẞ` (U+1E9E) cũng tồn tại. Điều đó không làm thay đổi kết quả cụ thể của cách chuyển `"ß".toUpperCase(Locale.ROOT)` trong ví dụ trên: Java dùng phép ánh xạ chuỗi thành `"SS"`. Một ứng dụng có quy tắc ngôn ngữ riêng cần xác định yêu cầu trước khi áp dụng phép biến đổi này.

### Ví dụ 1: quan sát số lượng code unit trước và sau

Tạo tệp `UppercaseExpansionDemo.java`:

```java
import java.util.Locale;

public class UppercaseExpansionDemo {
    public static void main(String[] args) {
        String original = "Straße";
        String upper = original.toUpperCase(Locale.ROOT);

        System.out.println(original);
        System.out.println(upper);
        System.out.println(original.length());
        System.out.println(upper.length());
        System.out.println(original.codePointCount(0, original.length()));
        System.out.println(upper.codePointCount(0, upper.length()));
        System.out.println("ß".toUpperCase(Locale.ROOT));
    }
}
```

Biên dịch với UTF-8 và chạy bằng JDK 17+:

```bash
javac -encoding UTF-8 UppercaseExpansionDemo.java
java UppercaseExpansionDemo
```

Kết quả:

```text
Straße
STRASSE
6
7
6
7
SS
```

Trong ví dụ này, mỗi chữ của chuỗi gốc được biểu diễn bằng một UTF-16 code unit. Phép viết hoa thay `ß` bằng hai code point `S`, khiến cả số code unit và số code point tăng từ 6 lên 7. Kết quả `String` mới không sửa nội dung `original`.

## 3. Đổi sang chữ thường cũng có thể làm chuỗi dài hơn

Không chỉ `toUpperCase` mới có thể mở rộng độ dài. Chữ Latin hoa có dấu chấm phía trên `İ` (U+0130, *LATIN CAPITAL LETTER I WITH DOT ABOVE*) là ví dụ điển hình.

Trong ánh xạ không đặc thù ngôn ngữ của `Locale.ROOT`:

```text
U+0130 (İ) -> U+0069 (i) + U+0307 (COMBINING DOT ABOVE)
```

Chuỗi trước có **một** UTF-16 code unit; chuỗi kết quả có **hai** UTF-16 code unit. Trên màn hình, `i` cộng dấu kết hợp có thể trông gần giống một chữ thường có chấm, nhưng hai biểu diễn Unicode không nhất thiết có cùng số code point.

### Ví dụ 2: xác minh bằng code point thay vì chỉ nhìn chữ

Tạo tệp `LowercaseExpansionDemo.java`:

```java
import java.util.Locale;
import java.util.stream.Collectors;

public class LowercaseExpansionDemo {
    static String codePoints(String text) {
        return text.codePoints()
                .mapToObj(cp -> String.format("U+%04X", cp))
                .collect(Collectors.joining(", "));
    }

    public static void main(String[] args) {
        String original = "\u0130";
        String lower = original.toLowerCase(Locale.ROOT);

        System.out.println(original.length());
        System.out.println(lower.length());
        System.out.println(codePoints(original));
        System.out.println(codePoints(lower));
        System.out.println(original.equals(lower));
    }
}
```

```bash
javac -encoding UTF-8 LowercaseExpansionDemo.java
java LowercaseExpansionDemo
```

Kết quả:

```text
1
2
U+0130
U+0069, U+0307
false
```

Dùng `codePoints()` giúp quan sát chính xác dữ liệu, tránh nhầm lẫn do font hoặc cách trình bày dấu kết hợp. `String.equals` trả về `false` vì hai chuỗi có dãy code unit khác nhau; đương nhiên đổi hoa–thường không phải là thao tác giữ nguyên nội dung.

## 4. `Locale` có thể quyết định kết quả và độ dài

Các quy tắc viết hoa/viết thường phụ thuộc ngôn ngữ. Tiếng Thổ Nhĩ Kỳ là ví dụ kinh điển với bốn ký tự liên quan:

- `I`: Latin hoa I không có chấm.
- `i`: Latin thường i có chấm.
- `İ`: Latin hoa I có chấm.
- `ı`: Latin thường i không có chấm.

Trong tiếng Thổ Nhĩ Kỳ, `I` thường đổi thành `ı`, còn `İ` đổi thành `i`. Với `Locale.ROOT`, quy tắc Unicode mặc định của `İ` có thể cho `i` + dấu kết hợp, nên độ dài khác so với cách đổi theo locale Thổ Nhĩ Kỳ.

### Ví dụ 3: cùng đầu vào, khác locale

Tạo tệp `LocaleCaseMappingDemo.java`:

```java
import java.util.Locale;

public class LocaleCaseMappingDemo {
    public static void main(String[] args) {
        Locale turkish = Locale.forLanguageTag("tr");
        String capitalDottedI = "\u0130";

        String rootLower = capitalDottedI.toLowerCase(Locale.ROOT);
        String trLower = capitalDottedI.toLowerCase(turkish);

        System.out.println(rootLower.length());
        System.out.println(trLower.length());
        System.out.println("I".toLowerCase(Locale.ROOT).equals("i"));
        System.out.println("I".toLowerCase(turkish).equals("\u0131"));
        System.out.println("title".toUpperCase(Locale.ROOT));
        System.out.println("title".toUpperCase(turkish)
                .codePoints()
                .mapToObj(cp -> String.format("%04X", cp))
                .toList());
    }
}
```

```bash
javac -encoding UTF-8 LocaleCaseMappingDemo.java
java LocaleCaseMappingDemo
```

Kết quả:

```text
2
1
true
true
TITLE
[0054, 0130, 0054, 004C, 0045]
```

`Locale.ROOT` là lựa chọn có chủ đích cho những quy tắc độc lập với ngôn ngữ người dùng (ví dụ một số mã khóa nội bộ được thiết kế như vậy). Khi xử lý **ngôn ngữ tự nhiên**, phải chọn locale phù hợp với dữ liệu. Không có một locale nào «đúng» cho mọi văn bản và mọi yêu cầu.

Hai overload không truyền locale là:

```java
text.toUpperCase()
text.toLowerCase()
```

Chúng sử dụng locale mặc định của môi trường theo hợp đồng `String`. Bởi vậy chương trình có thể cho kết quả khác nếu locale mặc định thay đổi. Nếu đầu ra cần lặp lại ổn định giữa máy chủ và môi trường CI, hãy **truyền `Locale` tường minh** thay vì phụ thuộc cấu hình của hệ thống.

## 5. Bối cảnh xung quanh ký tự cũng ảnh hưởng tới chuyển đổi

Ánh xạ Unicode không chỉ là bảng tra một ký tự đầu vào sang một ký tự đầu ra. Một số phép viết thường phải xem **ngữ cảnh trong từ**. Chẳng hạn chữ hoa Sigma của tiếng Hy Lạp (Σ, U+03A3) có thể chuyển sang sigma thường chuẩn (σ, U+03C3) hoặc sigma thường ở cuối từ (ς, U+03C2), tùy vị trí.

Ví dụ khái niệm:

```java
String word = "\u039F\u03A3"; // Hai chữ hoa Hy Lạp.
String lower = word.toLowerCase(java.util.Locale.ROOT);
```

Kết quả gồm các code point U+03BF (ο) và U+03C2 (ς); chữ sigma cuối nhận dạng phù hợp ngữ cảnh. Đây là **đoạn minh họa**, chưa có `main`. Dù ví dụ này không đổi độ dài, nó chứng minh rằng giải thuật chuyển chữ hoa/thường không thể luôn được thay thế bằng thao tác độc lập trên từng `char`.

Vì thế, tránh viết vòng lặp tự động `Character.toLowerCase(text.charAt(i))` khi bạn cần **toàn bộ phép chuyển đổi Unicode của chuỗi**: cách xử lý từng code unit vừa bỏ lỡ ánh xạ nhiều code point, vừa không biết ngữ cảnh toàn chuỗi.

## 6. `Character.toUpperCase(char)` không tương đương `String.toUpperCase(Locale)`

`Character.toUpperCase(char)` nhận một `char` và trả về một `char`. Kiểu trả về này chỉ chứa được **một UTF-16 code unit**, nên không thể trả nguyên dãy hai chữ `"SS"` cho đầu vào `'ß'`. Các overload `Character.toUpperCase(int)` có thể làm việc với code point nhưng vẫn trả một giá trị `int` duy nhất, không biểu diễn được ánh xạ chuỗi dài tùy ý.

`String.toUpperCase(Locale)` có quyền tạo kết quả dài hơn để biểu diễn ánh xạ đầy đủ.

### Ví dụ 4: thay từng `char` có thể cho kết quả khác

Tạo tệp `CharacterVsStringDemo.java`:

```java
import java.util.Locale;

public class CharacterVsStringDemo {
    public static void main(String[] args) {
        char sharpS = '\u00DF';

        char charMapped = Character.toUpperCase(sharpS);
        String stringMapped = String.valueOf(sharpS).toUpperCase(Locale.ROOT);

        System.out.println(charMapped == sharpS);
        System.out.println(stringMapped);
        System.out.println(stringMapped.length());

        String text = "Straße";
        StringBuilder naive = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            naive.append(Character.toUpperCase(text.charAt(i)));
        }

        System.out.println(naive);
        System.out.println(text.toUpperCase(Locale.ROOT));
    }
}
```

```bash
javac -encoding UTF-8 CharacterVsStringDemo.java
java CharacterVsStringDemo
```

Kết quả:

```text
true
SS
2
STRAßE
STRASSE
```

Trong Java 17+, phép ánh xạ `Character.toUpperCase('ß')` không thể mở rộng thành hai ký tự và giữ nguyên code unit này, còn `String.toUpperCase(Locale.ROOT)` có thể mở rộng thành `SS`.

Khi yêu cầu chuyển đổi văn bản hoàn chỉnh, hãy dùng API của `String` với locale phù hợp. `Character` thích hợp cho những thuật toán cố ý thao tác trên một code point hoặc code unit, sau khi đã hiểu giới hạn của phép ánh xạ.

## 7. Thay đổi độ dài khiến chỉ số cũ không còn đáng tin

Trong Java, `substring`, `charAt`, `indexOf` và `lastIndexOf` đều sử dụng chỉ số UTF-16 trong **chuỗi đang được thao tác**. Nếu đổi hoa–thường tạo chuỗi dài hơn hoặc ngắn hơn, vị trí ký tự ở kết quả có thể dịch sang trái hoặc sang phải.

Xét:

```text
Trước : S t r a ß e
Index : 0 1 2 3 4 5

Sau   : S T R A S S E
Index : 0 1 2 3 4 5 6
```

Chữ `e` ở vị trí `5` trước khi viết hoa; sau khi `ß` thành `SS`, chữ `E` ở vị trí `6`. **Không nên lưu vị trí trong bản gốc rồi dùng nó để cắt hoặc truy cập bản đã đổi hoa–thường.**

### Ví dụ 5: lỗi dùng chỉ số trước chuyển đổi

Tạo tệp `CaseMappingIndexDemo.java`:

```java
import java.util.Locale;

public class CaseMappingIndexDemo {
    public static void main(String[] args) {
        String original = "Straße";
        int oldE = original.indexOf('e');

        String upper = original.toUpperCase(Locale.ROOT);

        System.out.println(oldE);
        System.out.println(upper.charAt(oldE));
        System.out.println(upper.indexOf('E'));
        System.out.println(upper.substring(upper.indexOf('E')));
    }
}
```

```bash
javac -encoding UTF-8 CaseMappingIndexDemo.java
java CaseMappingIndexDemo
```

Kết quả:

```text
5
S
6
E
```

Chữ ở vị trí 5 sau chuyển đổi là chữ `S` thứ hai trong `SS`, không còn là chữ `E`. Nếu dùng chỉ số cũ để cắt chuỗi mới, kết quả sai dù chương trình không ném ngoại lệ.

**Cách an toàn hơn:** tính lại vị trí dựa trên **chuỗi sau biến đổi** hoặc xử lý/tách dữ liệu trước khi biến đổi nếu ranh giới thực sự thuộc chuỗi gốc. Nếu phải giữ ánh xạ vị trí hai phía (ví dụ tô sáng kết quả tìm kiếm trên văn bản đã chuẩn hóa), cần thiết kế cơ chế *offset mapping* tường minh. Không thể chỉ cộng trừ một số cố định trong trường hợp tổng quát.

## 8. `String` vẫn bất biến dù độ dài kết quả thay đổi

`String.toUpperCase(...)` và `String.toLowerCase(...)` không sửa nội dung `String` nguồn. Chúng trả về kết quả chuyển đổi. Khi biến đổi không làm thay đổi nội dung, implementation có thể trả về chính đối tượng ban đầu; đừng dựa vào identity của kết quả trong logic nghiệp vụ.

```java
String source = "Straße";
String alias = source;
String changed = source.toUpperCase(java.util.Locale.ROOT);

// source và alias vẫn có nội dung "Straße".
// changed có nội dung "STRASSE".
```

Đây là đoạn minh họa, không phải lớp có `main`. Tính bất biến giải thích vì sao có thể giữ cả hai phiên bản trước/sau; nó **không** bảo đảm rằng kết quả có cùng `length()`.

Khi cần biết độ dài để cấp phát vùng chứa, giới hạn trường dữ liệu, kiểm tra UI hoặc tính chỉ số, hãy **tính trên chuỗi kết quả thật**:

```java
String normalizedCase = source.toUpperCase(java.util.Locale.ROOT);
int finalLength = normalizedCase.length();
```

Không cấp phát sẵn mảng ký tự với độ dài nguồn rồi mặc định kết quả chuyển đổi vừa khít. Trong nhiều trường hợp văn bản quốc tế, giả định đó sai.

## 9. Không đồng nhất đổi hoa–thường với chuẩn hóa Unicode

Hai khái niệm thường xuất hiện cạnh nhau nhưng giải quyết những vấn đề khác nhau:

- **Case conversion:** viết hoa/viết thường theo Unicode và locale, có thể thay đổi số code point.
- **Unicode normalization:** đưa các dãy code point tương đương về một dạng chuẩn như NFC hoặc NFD (theo quy tắc tương đương chuẩn tắc), có thể thay đổi số code point và code unit.
- **Case-insensitive comparison / case folding:** chính sách so sánh hoặc đối chiếu chữ không phân biệt hoa thường, có quy tắc riêng, không đơn giản là bảo đảm một phép `toLowerCase` cho mọi ngôn ngữ.

Ví dụ `é` có thể được biểu diễn bằng một code point U+00E9, hoặc bằng `e` (U+0065) kết hợp dấu sắc U+0301. Chúng có thể trông giống nhau nhưng không bằng nhau theo `String.equals` trước khi chuẩn hóa:

### Ví dụ 6: chuẩn hóa là bước riêng

Tạo `UnicodeNormalizationDemo.java`:

```java
import java.text.Normalizer;

public class UnicodeNormalizationDemo {
    public static void main(String[] args) {
        String composed = "\u00E9";
        String decomposed = "e\u0301";

        System.out.println(composed.length());
        System.out.println(decomposed.length());
        System.out.println(composed.equals(decomposed));

        String fixed = Normalizer.normalize(decomposed, Normalizer.Form.NFC);
        System.out.println(composed.equals(fixed));
        System.out.println(fixed.length());
    }
}
```

```bash
javac UnicodeNormalizationDemo.java
java UnicodeNormalizationDemo
```

Kết quả:

```text
1
2
false
true
1
```

Ví dụ này nói về **biểu diễn Unicode**, không phải một tình huống chuyển hoa–thường. Nó cho thấy độ dài có thể thay đổi ở các bước xử lý văn bản khác nhau. Không dùng `Normalizer` để thay cho `toUpperCase` hoặc `toLowerCase`: mỗi thao tác có hợp đồng và mục đích riêng. Khi nghiệp vụ cần vừa chuẩn hóa vừa so sánh không phân biệt hoa thường, phải xác định rõ thứ tự và chuẩn so sánh được chấp nhận.

## 10. Đổi hoa–thường có thể làm hỏng giả định so sánh

`equalsIgnoreCase` hữu ích khi muốn so sánh hai `String` mà không phân biệt hoa/thường theo hợp đồng riêng của Java. Tuy nhiên nó **không tương đương** với mọi phép ánh xạ Unicode nhiều ký tự hoặc so sánh ngôn ngữ tự nhiên theo locale.

Ví dụ:

```java
String a = "straße";
String b = "STRASSE";

System.out.println(a.toUpperCase(java.util.Locale.ROOT));
System.out.println(a.equalsIgnoreCase(b));
```

Đoạn minh họa cho kết quả:

```text
STRASSE
false
```

Hai chuỗi có thể trở thành cùng chữ hoa theo phép biến đổi cụ thể, nhưng `equalsIgnoreCase` không được thiết kế để coi mọi mở rộng một-thành-nhiều là bằng nhau; nó cũng yêu cầu độ dài phù hợp. Dùng `equalsIgnoreCase` là quyết định theo **hợp đồng so sánh của API**, không phải một cách tổng quát để triển khai Unicode full case folding hoặc collation theo ngôn ngữ.

Nếu xử lý tìm kiếm tiếng người dùng, sắp xếp theo ngôn ngữ, tên riêng hoặc văn bản Unicode quốc tế, hãy làm rõ các yếu tố: locale, phân biệt dấu, phân biệt hoa thường, normalization, phép ánh xạ mở rộng và chính sách khớp. Có thể cần `Collator`, thư viện Unicode chuyên dụng hoặc quy tắc domain cụ thể, tùy bài toán. Không tự động coi mọi chuỗi đã `toLowerCase(Locale.ROOT)` là khóa tìm kiếm chuẩn cho mọi miền nghiệp vụ.

Đặc biệt với mã định danh nhạy cảm hoặc chính sách an toàn, một quy tắc canonicalization bất cẩn có thể làm hai giá trị riêng biệt bị coi là một, hoặc làm cùng một giá trị được nhận diện khác nhau giữa môi trường. Cần định nghĩa quy tắc một cách thống nhất ở tất cả nơi nhập, lưu và so sánh dữ liệu.

## 11. Những lỗi thường gặp

**Lỗi 1 — mặc định `upper.length() == original.length()`.** Không đúng với Unicode. `"Straße".toUpperCase(Locale.ROOT)` tăng từ 6 lên 7 UTF-16 code unit.

**Lỗi 2 — nghĩ chỉ viết hoa mới mở rộng.** `"\u0130".toLowerCase(Locale.ROOT)` biến một code point thành hai code point `i` và dấu chấm kết hợp.

**Lỗi 3 — dùng `Character.toUpperCase(char)` để thay `String.toUpperCase(Locale)`.** Hàm trả `char` đơn không thể biểu diễn ánh xạ một thành nhiều ký tự, và vòng lặp từng `char` bỏ qua bối cảnh của cả chuỗi.

**Lỗi 4 — bỏ qua locale.** Kết quả với `I`/`İ`/`ı`/`i` trong tiếng Thổ Nhĩ Kỳ khác cách xử lý chung. Truyền locale có chủ đích; không để cấu hình máy ngầm quyết định một quy tắc nghiệp vụ ổn định.

**Lỗi 5 — tin vào chỉ số cũ sau biến đổi.** Một chữ có thể mở rộng thành nhiều đơn vị, khiến các vị trí phía sau dịch chuyển. Tính lại chỉ số hoặc giữ bản đồ offset.

**Lỗi 6 — nghĩ UTF-16 length là số chữ nhìn thấy.** Một emoji ngoài BMP dùng hai code unit; chữ kết hợp dấu có thể gồm nhiều code point. Chỉ số `String` không phải chỉ số grapheme cluster.

**Lỗi 7 — cho rằng chuẩn hóa NFC chính là đổi chữ.** Normalization và case conversion là các phép xử lý riêng. Đừng thay thế lẫn nhau chỉ vì cả hai có thể thay đổi độ dài.

**Lỗi 8 — cho rằng `equalsIgnoreCase` hỗ trợ mọi quy tắc Unicode.** API này không thực hiện mọi phép mở rộng một-thành-nhiều và không phải so sánh ngôn ngữ theo locale.

**Lỗi 9 — giả định chuyển hoa–thường có thể đảo ngược.** Khi `ß` thành `SS`, chuỗi kết quả không mang thông tin để xác định chắc chắn có bao nhiêu ký tự gốc hay dạng chữ gốc. Ánh xạ không nhất thiết một-một hoặc thuận nghịch.

**Lỗi 10 — quên rằng `String` bất biến.** Đổi hoa–thường tạo giá trị kết quả, không sửa văn bản mà các tham chiếu khác đang giữ.

## 12. Câu hỏi tự kiểm tra

### Câu 1: độ dài sau chuyển chữ hoa

Cho:

```java
String word = "Straße";
String upper = word.toUpperCase(java.util.Locale.ROOT);

System.out.println(word.length());
System.out.println(upper.length());
System.out.println(upper);
```

Kết quả:

```text
6
7
STRASSE
```

Giải thích vì sao: chữ `ß` trong chuỗi gốc ánh xạ thành hai chữ `S` liên tiếp khi viết hoa.

### Câu 2: chữ İ có độ dài ra sao?

Cho:

```java
String s = "\u0130";
String lowerRoot = s.toLowerCase(java.util.Locale.ROOT);
String lowerTr = s.toLowerCase(java.util.Locale.forLanguageTag("tr"));

System.out.println(lowerRoot.length());
System.out.println(lowerTr.length());
```

Kết quả:

```text
2
1
```

`Locale.ROOT` tạo `i` + combining dot, còn quy tắc tiếng Thổ Nhĩ Kỳ tạo `i` đơn.

### Câu 3: có dùng lại chỉ số cũ được không?

Nếu `original = "Straße"` và `upper = original.toUpperCase(Locale.ROOT)`, chỉ số của `e` gốc là 5. Vị trí của `E` trong kết quả là **6**. Vì vậy, `upper.charAt(5)` trả `'S'`, không phải `'E'`.

### Câu 4: xử lý chuỗi nhiều ngôn ngữ

Một ứng dụng lưu văn bản ở nhiều locale và cần tìm đoạn khớp trên bản đã chuyển hoa–thường. Nên làm gì?

**Hướng giải:** chọn quy tắc locale/canonicalization phù hợp, biến đổi dữ liệu theo hợp đồng nhất quán, tính chỉ số trên **bản đã biến đổi**, và nếu phải tô sáng trên bản gốc thì cần ánh xạ offset tường minh. Không dùng chỉ số hoặc độ dài cũ để suy ra vị trí mới.

## Kết luận

**Đổi hoa–thường trong Java là phép biến đổi Unicode, không đơn giản là thay từng `char` bằng một `char` khác.** `String.toUpperCase(Locale)` và `String.toLowerCase(Locale)` có thể mở rộng một ký tự thành nhiều code point; kết quả còn có thể phụ thuộc vào locale và ngữ cảnh. Vì thế, hãy tính `length()` và các chỉ số trên chính chuỗi kết quả, chọn locale tường minh, phân biệt case conversion với Unicode normalization và không giả định phép chuyển đổi là một-một hoặc đảo ngược được.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="case-normalization-005">Normalizer và các dạng NFC, NFD, NFKC, NFKD</a>

<details>
<summary>Xem chi tiết</summary>

# Normalizer và các dạng NFC, NFD, NFKC, NFKD trong Java

Hai chuỗi trông giống nhau trên màn hình chưa chắc chứa cùng dãy ký tự Unicode. Chẳng hạn, chữ **é** có thể được biểu diễn bằng một code point `U+00E9` hoặc bằng chữ **e** `U+0065` theo sau dấu sắc kết hợp `U+0301`. Với Java, `String.equals()` so sánh dãy code unit UTF-16, nên hai chuỗi ấy **không bằng nhau theo `equals()`** dù có thể hiển thị giống hệt.

Unicode định nghĩa các **dạng chuẩn hóa** (*normalization forms*) để đưa văn bản về cách biểu diễn nhất quán theo các quy tắc tương đương. Java cung cấp `java.text.Normalizer` với bốn dạng **NFC, NFD, NFKC, NFKD**. Hiểu đúng bốn dạng giúp so sánh, tìm kiếm, lưu khóa và trao đổi dữ liệu đa ngôn ngữ một cách có chủ đích, đồng thời tránh làm thay đổi ý nghĩa văn bản ngoài mong muốn.

## 1. Normalization là gì và không phải là gì?

*Unicode normalization* biến đổi **dãy code point** theo quy tắc Unicode: phân rã một số ký tự, sắp xếp lại dấu kết hợp theo thứ tự chuẩn tắc, và có thể ghép một số dãy đã phân rã thành ký tự dựng sẵn. Hai dạng dữ liệu khác nhau nhưng được Unicode coi là tương đương theo loại tương đương đã chọn sẽ có cùng biểu diễn chuẩn hóa trong phạm vi quy tắc ấy.

Ví dụ cùng biểu diễn chữ é:

```text
Dạng dựng sẵn:   U+00E9               é
Dạng tổ hợp:     U+0065 U+0301         e + ◌́

Trước khi chuẩn hóa:  hai dãy code point khác nhau
Sau khi về NFC:       cả hai thành U+00E9
Sau khi về NFD:       cả hai thành U+0065 U+0301
```

Trong bảng trên, ký hiệu `◌́` chỉ giúp nhìn thấy dấu sắc kết hợp, không phải code point bổ sung cần nhập vào dữ liệu thật.

**Normalization không đồng nghĩa với:**

- Chuyển chữ hoa thành chữ thường; `"Java"` và `"java"` vẫn là hai chuỗi khác nhau sau NFC/NFD/NFKC/NFKD.
- Bỏ dấu tiếng Việt hoặc phiên âm. `"á"` không biến thành `"a"` chỉ vì gọi `Normalizer.normalize(...)`.
- Dịch, sửa chính tả, thay dấu cách tùy ý hoặc xóa ký tự vô hình.
- So sánh theo quy tắc của một ngôn ngữ (*collation*); ví dụ cách sắp xếp từ điển là vấn đề khác.
- Bảo đảm hai chuỗi trông giống nhau sẽ bằng nhau. Các ký tự khác bảng chữ viết có thể trông giống nhau mà vẫn không tương đương Unicode; chuẩn hóa không giải quyết mọi ký tự dễ nhầm (*confusables*).

Quan trọng: chuẩn hóa làm thay đổi **chuỗi biểu diễn**, không thay thế việc chọn chính sách so sánh phù hợp cho từng bài toán.

## 2. Hai kiểu tương đương của Unicode

### 2.1. Tương đương chuẩn tắc — canonical equivalence

Hai dãy code point *canonically equivalent* nếu Unicode coi chúng là những biểu diễn chuẩn tắc khác nhau của cùng văn bản trừu tượng. Một ví dụ phổ biến là:

- `U+00E9`: chữ é dựng sẵn.
- `U+0065 U+0301`: chữ e kết hợp dấu sắc.

Hai dãy trên khác nhau về độ dài và mã, nhưng được coi là tương đương chuẩn tắc. Các dạng **NFC** và **NFD** bảo toàn loại tương đương này. Đổi giữa chúng có thể thay đổi số code point, số code unit, byte khi mã hóa, hoặc chỉ số trong chuỗi.

### 2.2. Tương đương tương thích — compatibility equivalence

Một số ký tự Unicode biểu diễn hình thức trình bày hoặc ký hiệu có ý nghĩa lịch sử/chức năng riêng nhưng có **compatibility decomposition** sang dãy ký tự thông thường. Ví dụ:

- `①` (`U+2460`, chữ số 1 trong vòng tròn) có dạng tương thích là `1` (`U+0031`).
- `ﬃ` (`U+FB03`, ligature) có dạng tương thích là ba chữ `f`, `f`, `i`.
- `Ａ` (`U+FF21`, chữ A fullwidth) có dạng tương thích là `A` (`U+0041`).

Các cặp này **không nhất thiết tương đương chuẩn tắc**. Chuyển bằng **NFKC/NFKD** có thể xóa đi sự khác biệt về kiểu trình bày hoặc tính năng của ký hiệu. Điều đó có ích khi dữ liệu cần so sánh theo dạng ký tự thông thường, nhưng cũng có thể **mất thông tin phân biệt** mà bài toán thật sự cần giữ.

Không suy diễn rằng hai ký hiệu có hình dáng gần nhau đều tương đương tương thích. Chỉ những ánh xạ theo dữ liệu và quy tắc Unicode tương ứng mới được áp dụng.

## 3. Bốn dạng NFC, NFD, NFKC, NFKD

Tên dạng có thể nhớ theo hai trục:

- **C** = *Composition*: thực hiện phân rã phù hợp, sắp xếp dấu kết hợp theo chuẩn, rồi **ghép lại** những dãy được phép.
- **D** = *Decomposition*: **phân rã** theo quy tắc, không ghép lại sau cùng.
- **K** = *Compatibility*: ngoài phân rã chuẩn tắc, còn áp dụng **phân rã tương thích**. Chữ K là một phần của tên Unicode chính thức.

| Dạng | Tên đầy đủ | Cách thực hiện ở mức khái niệm | Đặc điểm cần nhớ |
|---|---|---|---|
| **NFD** | Normalization Form D | Phân rã chuẩn tắc + sắp xếp chuẩn tắc | Giữ các khác biệt không tương đương chuẩn tắc |
| **NFC** | Normalization Form C | Phân rã chuẩn tắc + sắp xếp + ghép chuẩn tắc | Thường thuận tiện làm dạng nhất quán cho văn bản thông thường |
| **NFKD** | Normalization Form KD | Phân rã chuẩn tắc **và tương thích** + sắp xếp | Có thể đưa ký hiệu trình bày về ký tự cơ sở |
| **NFKC** | Normalization Form KC | Phân rã chuẩn tắc **và tương thích** + sắp xếp + ghép chuẩn tắc | Kết hợp dạng ghép và các phép gộp tương thích |

Ví dụ diễn giải:

| Đầu vào | NFC | NFD | NFKC | NFKD |
|---|---|---|---|---|
| `é` (U+00E9) | `é` | `e` + dấu sắc | `é` | `e` + dấu sắc |
| `e` + dấu sắc (U+0065 U+0301) | `é` | `e` + dấu sắc | `é` | `e` + dấu sắc |
| `①` (U+2460) | `①` | `①` | `1` | `1` |
| `ﬃ` (U+FB03) | `ﬃ` | `ﬃ` | `ffi` | `ffi` |
| `Ａ` (U+FF21) | `Ａ` | `Ａ` | `A` | `A` |

**Không có quy luật “NFC luôn có ít ký tự hơn NFD”.** Có những dãy phân rã mà Unicode không cho phép ghép thành một ký tự dựng sẵn. Thứ tự và quy tắc ghép còn chịu các giới hạn như *composition exclusions*. Bởi vậy chữ C là “cố gắng ghép theo quy tắc”, không phải “bảo đảm một code point cho mỗi chữ mà người dùng nhìn thấy”.

Các dạng chuẩn hóa đều có tính **idempotent**: áp dụng **cùng một dạng** lần nữa sẽ cho cùng nội dung:

```text
normalize(normalize(s, NFC), NFC) == normalize(s, NFC)
```

Dòng trên biểu thị **đẳng thức về nội dung**, không phải bảo đảm hai tham chiếu Java bằng nhau bằng `==`.

## 4. API `java.text.Normalizer` trong Java 17+

Lớp `java.text.Normalizer` nằm trong thư viện chuẩn Java; không cần dependency ngoài. Hai phương thức tĩnh cần biết:

```java
String normalized = Normalizer.normalize(input, Normalizer.Form.NFC);
boolean alreadyNormalized =
        Normalizer.isNormalized(input, Normalizer.Form.NFC);
```

Đây là **đoạn minh họa**, giả sử `input` đã được khai báo là một `String` không null. Bốn hằng enum tương ứng là:

```java
Normalizer.Form.NFC
Normalizer.Form.NFD
Normalizer.Form.NFKC
Normalizer.Form.NFKD
```

- `normalize(String source, Normalizer.Form form)` trả một `String` có nội dung ở dạng yêu cầu.
- `isNormalized(String source, Normalizer.Form form)` cho biết nội dung **đã** thỏa dạng đó chưa. Phương thức này đặc biệt hữu ích khi muốn kiểm tra dữ liệu hoặc tránh thực hiện bước chuẩn hóa không cần thiết theo luồng ứng dụng.
- `String` là immutable: không có thao tác nào ở đây sửa nội dung chuỗi nguồn. Muốn giữ kết quả, dùng biến mới hoặc gán `input = Normalizer.normalize(input, form);`.
- Một chuỗi có thể đồng thời thỏa **nhiều dạng**. Ví dụ `"abc"` đã ở cả NFC, NFD, NFKC và NFKD.
- `Normalizer` xử lý dữ liệu Unicode theo phiên bản được hỗ trợ bởi bản triển khai JDK; với các ký tự Unicode mới hơn, cần xem khả năng hỗ trợ của môi trường Java mục tiêu.

### Ví dụ 1: chữ é theo NFC và NFD

Lưu thành **`CanonicalNormalizationDemo.java`**:

```java
import java.text.Normalizer;
import java.util.stream.Collectors;

public class CanonicalNormalizationDemo {
    private static String codePoints(String s) {
        return s.codePoints()
                .mapToObj(cp -> String.format("U+%04X", cp))
                .collect(Collectors.joining(" "));
    }

    public static void main(String[] args) {
        String composed = "\u00E9";
        String decomposed = "e\u0301";

        System.out.println("raw equals: " + composed.equals(decomposed));

        String nfcA = Normalizer.normalize(composed, Normalizer.Form.NFC);
        String nfcB = Normalizer.normalize(decomposed, Normalizer.Form.NFC);
        System.out.println("NFC equals: " + nfcA.equals(nfcB));
        System.out.println("NFC points: " + codePoints(nfcB));

        String nfdA = Normalizer.normalize(composed, Normalizer.Form.NFD);
        String nfdB = Normalizer.normalize(decomposed, Normalizer.Form.NFD);
        System.out.println("NFD equals: " + nfdA.equals(nfdB));
        System.out.println("NFD points: " + codePoints(nfdA));

        System.out.println("decomposed already NFC: "
                + Normalizer.isNormalized(decomposed, Normalizer.Form.NFC));
        System.out.println("decomposed already NFD: "
                + Normalizer.isNormalized(decomposed, Normalizer.Form.NFD));

        String nfcTwice = Normalizer.normalize(nfcB, Normalizer.Form.NFC);
        System.out.println("NFC idempotent: " + nfcB.equals(nfcTwice));
    }
}
```

Chạy tại thư mục chứa tệp, dùng JDK 17+:

```bash
javac CanonicalNormalizationDemo.java
java CanonicalNormalizationDemo
```

**Stdout:**

```text
raw equals: false
NFC equals: true
NFC points: U+00E9
NFD equals: true
NFD points: U+0065 U+0301
decomposed already NFC: false
decomposed already NFD: true
NFC idempotent: true
```

`composed.equals(decomposed)` ban đầu là `false` vì khác chuỗi code unit UTF-16. Sau NFC, cả hai cùng có `U+00E9`; sau NFD, cả hai cùng có `U+0065 U+0301`. `isNormalized` kiểm tra **dạng chuẩn hóa hiện có**, không kiểm tra chuỗi có phải tiếng Việt/tiếng Anh hợp lệ, và cũng không loại bỏ dấu. `NFC idempotent` minh họa áp dụng lại một dạng đã đạt chuẩn không làm thay đổi nội dung.

## 5. Ví dụ 2: NFKC/NFKD khác NFC/NFD ở đâu?

Điểm đặc trưng của hai dạng có **K** là chúng xử lý cả ánh xạ tương thích. Để tránh sự phụ thuộc vào font hiển thị ký tự đặc biệt, ví dụ dưới đây in **mã code point Unicode** thay vì in trực tiếp glyph kết quả.

Lưu thành **`CompatibilityNormalizationDemo.java`**:

```java
import java.text.Normalizer;
import java.util.stream.Collectors;

public class CompatibilityNormalizationDemo {
    private static String points(String s) {
        return s.codePoints()
                .mapToObj(cp -> String.format("U+%04X", cp))
                .collect(Collectors.joining(" "));
    }

    private static void show(String name, String input) {
        System.out.println(name + " NFC: " + points(
                Normalizer.normalize(input, Normalizer.Form.NFC)));
        System.out.println(name + " NFD: " + points(
                Normalizer.normalize(input, Normalizer.Form.NFD)));
        System.out.println(name + " NFKC: " + points(
                Normalizer.normalize(input, Normalizer.Form.NFKC)));
        System.out.println(name + " NFKD: " + points(
                Normalizer.normalize(input, Normalizer.Form.NFKD)));
    }

    public static void main(String[] args) {
        show("circled-one", "\u2460");
        show("ligature-ffi", "\uFB03");
        show("fullwidth-A", "\uFF21");
    }
}
```

```bash
javac CompatibilityNormalizationDemo.java
java CompatibilityNormalizationDemo
```

**Stdout:**

```text
circled-one NFC: U+2460
circled-one NFD: U+2460
circled-one NFKC: U+0031
circled-one NFKD: U+0031
ligature-ffi NFC: U+FB03
ligature-ffi NFD: U+FB03
ligature-ffi NFKC: U+0066 U+0066 U+0069
ligature-ffi NFKD: U+0066 U+0066 U+0069
fullwidth-A NFC: U+FF21
fullwidth-A NFD: U+FF21
fullwidth-A NFKC: U+0041
fullwidth-A NFKD: U+0041
```

Các kết quả cho thấy:

1. NFC/NFD không tự biến `①` thành `1`, `ﬃ` thành `ffi` hoặc `Ａ` thành `A` vì các cặp này chỉ liên hệ qua **tương đương tương thích**.
2. NFKC/NFKD có thể làm mất thông tin về kiểu ký hiệu, hình thức trình bày hoặc chiều rộng ký tự. Nếu phân biệt `①` và `1` là quan trọng trong dữ liệu, **không được mặc định chọn NFKC**.
3. Với các chuỗi ví dụ không có dãy cần ghép sau phân rã tương thích, đầu ra NFKC và NFKD trùng nhau. Điều đó **không** có nghĩa hai dạng luôn như nhau: chúng khác biệt khi có thành phần cần ghép chuẩn tắc sau bước phân rã.

## 6. Ký tự tổ hợp, thứ tự dấu và những điều dễ hiểu sai

Một dãy Unicode có thể gồm ký tự cơ sở và nhiều dấu kết hợp. Chuẩn hóa không chỉ thay một ký tự dựng sẵn bằng hai ký tự hoặc ngược lại; nó còn áp dụng **thứ tự chuẩn tắc** dựa trên *Canonical Combining Class* khi quy tắc cho phép.

Vì vậy:

- Những dãy cùng nghĩa chuẩn tắc nhưng có thứ tự dấu khác nhau đôi khi được đưa về cùng thứ tự chuẩn. Không nên tùy ý hoán đổi mọi dấu; Unicode có các quy tắc và ngoại lệ cụ thể.
- Một dấu kết hợp không nhất thiết có ký tự dựng sẵn tương ứng với mọi chữ cơ sở. Sau NFC vẫn có thể còn nhiều code point.
- “Một code point” không đồng nghĩa “một ký tự người dùng thấy”. Một **grapheme cluster** có thể có nhiều code point, ngay cả sau khi đã chuẩn hóa.
- `String.length()` đếm code unit UTF-16, `codePointCount(0, length())` đếm code point, còn số cụm ký tự hiển thị là phép đo khác. Chuẩn hóa có thể thay đổi hai phép đếm đầu mà vẫn cho biểu diễn nhìn tương tự.

Ví dụ:

```text
Trước: "e" + U+0301
  UTF-16 code units = 2
  Unicode code points = 2

Sau NFC: U+00E9
  UTF-16 code units = 1
  Unicode code points = 1

Một chữ é người dùng thường nhận biết: 1 grapheme cluster
```

Nếu đang lưu offset con trỏ, ranh giới `substring`, vùng tô sáng, chỉ số tìm kiếm hay tọa độ ghi chú trên nội dung gốc, **không dùng lại chỉ số cũ sau khi chuẩn hóa**. Hãy chuẩn hóa trước khi tính offset hoặc xây cơ chế ánh xạ vị trí nguồn–đích khi cần giữ các tọa độ trên văn bản gốc.

## 7. Chuẩn hóa trước khi so sánh và tìm kiếm

`String.equals()`, `indexOf()`, `contains()` và khóa `String` trong `HashMap` không tự áp dụng Unicode normalization. Vì thế dữ liệu về cùng một từ nhưng có cách mã hóa dấu khác nhau có thể so sánh `false` hoặc không khớp khi tìm.

Quy trình phổ biến cho **tương đương chuẩn tắc**:

1. Xác định quy tắc so sánh: ở đây chấp nhận hai chuỗi *canonically equivalent* là cùng văn bản.
2. Chọn **một dạng duy nhất**, thường là NFC.
3. Chuẩn hóa **cả hai phía** trước khi so sánh hoặc khi ghi/tra cứu khóa.
4. Dùng `equals()`/`Objects.equals()` hoặc thao tác tìm kiếm trên chuỗi đã chuẩn hóa.

### Ví dụ 3: khóa `HashMap` phải có dạng nhất quán

Lưu thành **`NormalizedLookupDemo.java`**:

```java
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;

public class NormalizedLookupDemo {
    private static String nfc(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFC);
    }

    public static void main(String[] args) {
        String stored = "caf\u00E9";
        String queried = "cafe\u0301";

        Map<String, String> labels = new HashMap<>();
        labels.put(stored, "found");

        System.out.println("raw equals: " + stored.equals(queried));
        System.out.println("lookup raw: " + labels.get(queried));
        System.out.println("lookup NFC: " + labels.get(nfc(queried)));

        Map<String, String> canonical = new HashMap<>();
        canonical.put(nfc(stored), "normalized");
        System.out.println("canonical lookup: " + canonical.get(nfc(queried)));
    }
}
```

```bash
javac NormalizedLookupDemo.java
java NormalizedLookupDemo
```

**Stdout:**

```text
raw equals: false
lookup raw: null
lookup NFC: found
canonical lookup: normalized
```

Dòng `lookup NFC` thành công vì `stored` trong ví dụ đã ở NFC; **không thể tổng quát hóa** rằng chỉ chuẩn hóa phía truy vấn là đủ. Để tránh phụ thuộc vào việc phía lưu trữ tình cờ đã được chuẩn hóa, hãy dùng cùng một hàm `nfc(...)` khi **ghi khóa** và **tạo khóa tra cứu**, như `canonical` ở cuối ví dụ.

Một hệ thống đã có dữ liệu lưu theo nhiều dạng khác nhau cần kế hoạch **chuyển đổi dữ liệu** hoặc chiến lược tra cứu phù hợp. Chỉ thay đổi mã đọc có thể khiến các khóa cũ không thể tìm thấy nếu dạng chuẩn hóa của khóa lưu không đồng nhất.

### Tìm chuỗi con có dấu

Nếu muốn `contains()` hoặc `indexOf()` chấp nhận sự tương đương chuẩn tắc, có thể chuẩn hóa cả chuỗi nguồn và từ khóa về NFC trước khi tìm. Nhưng offset mà `indexOf()` trả về là offset UTF-16 **trên chuỗi đã chuẩn hóa**. Nếu cần dùng offset đó để đánh dấu **chuỗi nguồn chưa chuẩn hóa**, phải ánh xạ lại vị trí; không được đưa offset mới trực tiếp vào nguồn cũ.

Nếu ứng dụng cần tìm kiếm theo ngôn ngữ, bỏ phân biệt hoa/thường, xử lý biến thể dấu hoặc sắp xếp từ điển, chỉ NFC có thể không đủ; cần xác định thuật toán so khớp theo locale hoặc bộ máy tìm kiếm phù hợp.

## 8. Normalization và chuyển chữ hoa/thường là hai bước độc lập

NFC, NFD, NFKC, NFKD **không phải** các chế độ “lowercase” hay “case-insensitive”. Chẳng hạn:

```java
String a = "Java";
String b = "java";

System.out.println(java.text.Normalizer.normalize(
        a, java.text.Normalizer.Form.NFC).equals(b)); // false
```

Đây là **đoạn minh họa**, có thể chạy khi đặt trong một phương thức Java; kết quả in ra là `false`.

Nếu chính sách nghiệp vụ yêu cầu so sánh **không phân biệt chữ hoa/thường**, Java còn có `String.toLowerCase(Locale)`, `equalsIgnoreCase(...)` hoặc các API liên quan. Hãy chọn theo ngôn ngữ/ngữ cảnh:

- `toLowerCase(Locale.ROOT)` thuận tiện cho một số mã định danh có quy tắc **không phụ thuộc locale**, nhưng không phải lúc nào cũng tương đương thuật toán *full Unicode case folding*.
- `Locale` cụ thể có thể làm thay đổi cách chuyển hoa/thường đối với một số chữ, chẳng hạn tiếng Thổ Nhĩ Kỳ.
- Việc đổi hoa/thường có thể thay đổi dãy code point; nếu thiết kế yêu cầu dạng chuẩn hóa cố định sau biến đổi, hãy cân nhắc **chuẩn hóa lại sau bước đổi hoa/thường**.
- NFKC **không tự** thực hiện full case folding; khái niệm `NFKC_Casefold` trong một số tiêu chuẩn Unicode không đơn giản là lời gọi `Normalizer.normalize(s, NFKC)`.

Bởi vậy, không gọi NFKC rồi mặc định kết luận hai tên người, tên tài khoản hoặc chuỗi tiếng tự nhiên “bằng nhau bất kể hoa/thường”. Quy tắc so sánh cần được mô tả rõ và kiểm thử với các ngôn ngữ mà ứng dụng hỗ trợ.

## 9. Chọn dạng nào cho tình huống thực tế?

| Tình huống | Lựa chọn cân nhắc | Lý do và giới hạn |
|---|---|---|
| Muốn lưu/so sánh văn bản thông thường theo tương đương chuẩn tắc | **NFC** | Duy trì phân biệt tương thích trong khi thống nhất dạng dựng sẵn/tổ hợp |
| Thuật toán cần phân tích riêng ký tự cơ sở và dấu kết hợp | **NFD** | Phân rã chuẩn tắc để thao tác code point; tránh ngộ nhận rằng mọi dấu đều có thể loại bỏ tùy ý |
| Chuẩn hóa mã định danh với các ký tự tương thích theo đặc tả rõ ràng | **NFKC** | Có thể gộp chữ fullwidth, ligature, ký hiệu trình bày; cần chính sách chấp nhận các phép gộp |
| Xử lý từng phần tử sau phân rã tương thích | **NFKD** | Giữ phân rã tương thích để phân tích; có thể làm mất phân biệt biểu diễn |
| Văn bản pháp lý, bản chép chính xác, chữ ký số hoặc dữ liệu cần giữ byte gốc | **Không tự ý chuyển** | Chuẩn hóa có thể đổi code point và byte; chỉ làm khi giao thức/đặc tả quy định |
| Mật khẩu hoặc giá trị bí mật đưa vào KDF/hàm băm | **Theo đúng đặc tả xác thực** | Chuẩn hóa ngoài hợp đồng làm thay đổi giá trị xác thực và có thể gây không tương thích |
| Tìm kiếm nhạy theo ngôn ngữ, bỏ hoa/thường hoặc bỏ dấu | **Quy tắc tìm kiếm riêng** | Một dạng Normalizer đơn lẻ không đáp ứng trọn quy tắc ngôn ngữ |
| Cần hiển thị đồng nhất về mặt glyph/độ rộng | **Xử lý font và UI** | Normalization không kiểm soát cách renderer tạo hình glyph |

**NFC thường là lựa chọn nhất quán tương đối an toàn khi yêu cầu chỉ là canonical equivalence**, nhưng ngay cả NFC cũng có thể đổi dãy byte và offset. Không có một dạng chuẩn hóa “đúng nhất” cho mọi ứng dụng. NFKC/NFKD mạnh hơn theo nghĩa gộp thêm những khác biệt tương thích; “mạnh hơn” không đồng nghĩa “tốt hơn” cho mọi dữ liệu.

### Tránh tự động xóa dấu sau NFD

Một mẹo thường thấy là phân rã bằng NFD rồi xóa mọi ký tự có category *Mark* (ví dụ regex `\p{M}`). Cách này có thể khiến `"café"` trở thành `"cafe"`, nhưng đó là **thuật toán loại dấu riêng**, vượt ra ngoài normalization thuần túy. Với tiếng Việt và nhiều hệ chữ khác, dấu có thể mang ý nghĩa từ vựng và ngữ pháp; xóa dấu có thể tạo nhập nhằng hoặc sai nghĩa. Nếu cần tìm kiếm không dấu, nên thiết kế một chỉ mục/phép tìm kiếm riêng và vẫn giữ văn bản gốc.

## 10. Hiệu năng, phiên bản Unicode và tính ổn định

- Chuẩn hóa có chi phí duyệt/biến đổi nội dung. Với dữ liệu lớn hoặc đường đi xử lý nóng, tránh chuẩn hóa cùng một chuỗi không đổi nhiều lần nếu có thể tổ chức quy trình chuẩn hóa một lần ở ranh giới tiếp nhận/lưu dữ liệu.
- `isNormalized(...)` hỗ trợ phát hiện chuỗi đã đúng dạng, nhưng việc kiểm tra cũng phải đọc dữ liệu; không mặc định một lời gọi kiểm tra cộng một lời gọi chuẩn hóa luôn nhanh hơn việc chuẩn hóa trực tiếp. Khi hiệu năng là yêu cầu, đo bằng benchmark trên workload đại diện.
- Các API Java chuẩn trả kết quả tuân theo dữ liệu Unicode do JDK triển khai. Bộ dữ liệu và hỗ trợ ký tự có thể được cập nhật khi nâng JDK; nếu hệ thống trao đổi dữ liệu giữa nhiều runtime, hãy kiểm tra các ký tự quan trọng và chính sách cập nhật.
- Unicode normalization có các bảo đảm về **tính ổn định chuẩn hóa** theo quy tắc chuẩn, nhưng điều đó không có nghĩa mọi phiên bản JDK nhận biết mọi ký tự Unicode mới như nhau, hoặc mọi hành vi tìm kiếm/hiển thị đều đồng nhất.
- Luôn ghi rõ dạng chuẩn hóa ở các ranh giới dữ liệu quan trọng: ví dụ “khóa được lưu và truy vấn ở NFC” dễ kiểm chứng hơn quy tắc ngầm hiểu.
- Không dùng `==` để so nội dung sau chuẩn hóa: hai kết quả có cùng dãy ký tự không bắt buộc là cùng đối tượng `String`. Dùng `equals()` hoặc `Objects.equals()`.

## 11. Các lỗi phổ biến

| Hiểu nhầm hoặc lỗi | Vì sao sai? | Cách xử lý |
|---|---|---|
| `"é".equals("e\u0301")` phải `true` vì trông giống | `equals()` so chuỗi code unit, không tự chuẩn hóa | Chọn NFC/NFD và chuẩn hóa hai bên khi cần canonical equivalence |
| NFC là “xóa dấu” | NFC có thể ghép dấu vào ký tự dựng sẵn | Dùng NFC cho dạng ghép chuẩn tắc, không coi là lọc dấu |
| NFD luôn tạo một code point cho mỗi chữ | NFD là phân rã, có thể tạo nhiều code point | Phân biệt code unit, code point và grapheme cluster |
| NFC/NFD và NFKC/NFKD luôn tương đương | Các dạng có K còn thực hiện phân rã tương thích | Chọn dạng dựa trên quy tắc nghiệp vụ |
| NFKC không ảnh hưởng nội dung quan trọng | `①` thành `1`, `ﬃ` thành `ffi` có thể mất sự phân biệt cần thiết | Không mặc định NFKC cho dữ liệu cần bảo toàn khác biệt |
| Chỉ chuẩn hóa đầu vào truy vấn | Khóa lưu có thể ở dạng khác | Chuẩn hóa nhất quán tại lúc lưu và lúc tìm |
| Chuẩn hóa làm `indexOf` trên nguồn cũ đúng offset | Độ dài/code point có thể thay đổi | Tính offset trên đúng chuỗi đích hoặc ánh xạ vị trí |
| Chuẩn hóa tự giải quyết hoa/thường | Normalizer không thực hiện case folding chung | Thiết kế bước xử lý hoa/thường riêng |
| Dùng `==` để so hai chuỗi đã chuẩn hóa | `==` là so tham chiếu | Dùng `equals()` cho nội dung |
| Chuẩn hóa tự loại ký tự gây nhầm lẫn hoặc làm sạch input nguy hiểm | Unicode confusables, kiểm tra dữ liệu và an toàn đầu vào là các vấn đề khác | Dùng quy tắc xác thực riêng, không coi Normalizer là bộ lọc an toàn |
| Chuẩn hóa mà bỏ kết quả trả về | `String` là immutable | Gán lại biến hoặc dùng giá trị trả về |

## 12. Tự kiểm tra

Trước khi chạy, dự đoán các giá trị bên dưới:

**`NormalizationSelfCheck.java`**

```java
import java.text.Normalizer;

public class NormalizationSelfCheck {
    public static void main(String[] args) {
        String a = "\u00E9";
        String b = "e\u0301";

        System.out.println(a.equals(b));
        System.out.println(Normalizer.normalize(a, Normalizer.Form.NFC)
                .equals(Normalizer.normalize(b, Normalizer.Form.NFC)));

        String circledOne = "\u2460";
        String plainOne = "1";

        System.out.println(Normalizer.normalize(
                circledOne, Normalizer.Form.NFC).equals(plainOne));
        System.out.println(Normalizer.normalize(
                circledOne, Normalizer.Form.NFKC).equals(plainOne));

        String mixedCase = "Java";
        System.out.println(Normalizer.normalize(
                mixedCase, Normalizer.Form.NFKC).equals("java"));
    }
}
```

```bash
javac NormalizationSelfCheck.java
java NormalizationSelfCheck
```

**Stdout:**

```text
false
true
false
true
false
```

Giải thích:

1. `a` và `b` chưa chuẩn hóa bằng nhau về hiển thị, nhưng `String.equals()` trực tiếp vẫn là `false`.
2. Cùng đưa về NFC, cả hai trở thành dãy code point tương đương chuẩn tắc, nên kết quả `equals()` là `true`.
3. NFC không áp dụng ánh xạ tương thích `①` → `1`, nên `false`.
4. NFKC có ánh xạ tương thích ấy, vì vậy `true`.
5. NFKC không đổi `J` hoa thành `j` thường, nên chuỗi `"Java"` vẫn khác `"java"`.

**Câu hỏi để tự chọn dạng:** ứng dụng của bạn cần bảo toàn mọi ký hiệu tương thích và chỉ thống nhất các biểu diễn chuẩn tắc (NFC/NFD), hay chấp nhận gộp một số ký tự trình bày về chữ/số thông thường (NFKC/NFKD)? Bước tiếp theo là chọn dạng có cơ sở, chuẩn hóa nhất quán ở các ranh giới dữ liệu, rồi so sánh bằng `equals()` và kiểm thử dữ liệu Unicode đại diện.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="case-normalization-006">Chuỗi nhìn giống nhau nhưng khác dãy code point</a>

<details>
<summary>Xem chi tiết</summary>

# Chuỗi nhìn giống nhau nhưng khác dãy Unicode code point

## 1. Vì sao đây là một vấn đề thực tế?

Hai chuỗi có thể **trông giống hệt nhau hoặc rất giống nhau trên màn hình** nhưng vẫn chứa **các dãy Unicode code point khác nhau**. Trong Java, điều này ảnh hưởng trực tiếp đến:

- `String.equals(...)`, `compareTo(...)` và `indexOf(...)` khi đối chiếu nội dung.
- Tìm kiếm, lọc trùng, thống kê, sắp xếp và tạo khóa cho `Map`/`Set`.
- Kiểm tra username, mã định danh, tên tệp, địa chỉ hoặc dữ liệu do người dùng nhập.
- Cắt chuỗi, đếm ký tự và xử lý dấu tiếng Việt/emoji.

**Ký tự nhìn thấy** là kết quả của bộ dựng chữ (*text rendering*), font và quy tắc tổ hợp Unicode. **Giá trị `String`** là dãy đơn vị mã UTF-16. Vì vậy, sự giống nhau về hình ảnh không tự động tạo ra bằng nhau về dữ liệu.

Sau bài học, bạn sẽ phân biệt code point, UTF-16 code unit và grapheme cluster; phát hiện khác biệt bằng `codePoints()`; sử dụng bốn dạng Unicode normalization (`NFC`, `NFD`, `NFKC`, `NFKD`) qua `java.text.Normalizer`; và hiểu giới hạn của việc chuẩn hóa khi đối mặt với chữ thuộc các hệ chữ khác nhau, hoa/thường hoặc những ký tự vô hình.

## 2. Phân biệt ba khái niệm

**Unicode code point:** giá trị số như `U+0061` (Latin `a`), `U+00E9` (`é`), `U+0301` (dấu sắc kết hợp) hoặc `U+1F600` (emoji 😀).

**UTF-16 code unit:** đơn vị 16 bit mà `char` của Java lưu. Một code point thuộc Basic Multilingual Plane (BMP) thường chiếm một code unit; code point supplementary như `U+1F600` cần hai code unit tạo thành *surrogate pair*.

**Grapheme cluster:** đơn vị văn bản mà người dùng thường nhận thức như một ký tự. Một grapheme cluster có thể chứa nhiều code point, ví dụ chữ cái kèm dấu kết hợp hoặc một emoji được tạo từ nhiều code point và ký tự nối.

| Văn bản nhìn thấy | Dãy code point | Số code point | Số UTF-16 code unit |
| --- | --- | ---: | ---: |
| `A` | `U+0041` | 1 | 1 |
| `é` (dạng dựng sẵn) | `U+00E9` | 1 | 1 |
| `é` (chữ `e` + dấu sắc kết hợp) | `U+0065 U+0301` | 2 | 2 |
| `😀` | `U+1F600` | 1 | 2 |

**Lưu ý khi đọc bảng:** `é` ở hàng thứ hai và tổ hợp `e` + `U+0301` ở hàng thứ ba thường hiển thị gần như nhau. Khả năng hiển thị chính xác phụ thuộc font, phần mềm dựng chữ và môi trường.

Trong Java:

~~~java
String composed = "caf\u00E9";  // café: ký tự é dựng sẵn U+00E9
String decomposed = "cafe\u0301"; // cafe + dấu sắc kết hợp U+0301

System.out.println(composed.length());  // 4 UTF-16 code units
System.out.println(decomposed.length()); // 5 UTF-16 code units
System.out.println(composed.equals(decomposed)); // false
~~~

Hai chuỗi gần như có cùng hình ảnh nhưng khác dãy dữ liệu. `String.length()` và `equals` đang làm việc đúng theo hợp đồng API.

## 3. Khác dãy code point, nhìn giống: ví dụ `café`

Có hai biểu diễn thường gặp của chữ `é`:

1. **Precomposed:** một code point `U+00E9`, tên Unicode là *LATIN SMALL LETTER E WITH ACUTE*.
2. **Decomposed:** code point `U+0065` (*LATIN SMALL LETTER E*) đi cùng `U+0301` (*COMBINING ACUTE ACCENT*).

Tương ứng:

~~~text
Cách 1: "café"
U+0063 U+0061 U+0066 U+00E9

Cách 2: "cafe" + U+0301
U+0063 U+0061 U+0066 U+0065 U+0301
~~~

Cả hai có thể hiển thị thành chữ `café`. Nhưng:

~~~java
String a = "caf\u00E9";
String b = "cafe\u0301";

System.out.println(a.equals(b));    // false
System.out.println(a.indexOf(b));   // -1
System.out.println(a.length());     // 4
System.out.println(b.length());     // 5
~~~

Đây là sự khác biệt **canonical representation**, thuộc nhóm mà chuẩn hóa Unicode có thể giải quyết. Đừng kết luận một bên “sai Unicode”: cả hai là các dãy được Unicode cho phép. Muốn ứng dụng coi chúng như nhau, hãy định nghĩa chính sách **canonical normalization** trước khi so sánh.

### Việc viết `\uXXXX` trong mã nguồn có ảnh hưởng không?

Trong tệp `.java`, `\u00E9` là một Unicode escape của **mã nguồn**, được trình biên dịch xử lý trước bước nhận diện literal chuỗi. Nếu tệp nguồn trực tiếp chứa `é` và được đọc đúng encoding, cả hai cách viết có thể dẫn đến **cùng code point**.

Sự khác biệt quan trọng ở đây là **`U+00E9` so với `U+0065 U+0301`**, không phải “dùng escape hay gõ ký tự trực tiếp”. Tương tự, một chuỗi nhận từ file/HTTP/database có thể mang dạng chuẩn hóa khác mà ứng dụng không nhận thấy bằng mắt.

## 4. `String.equals` so sánh giá trị, không so sánh hình ảnh

`String.equals(Object)` trả `true` khi các chuỗi chứa cùng **thứ tự đơn vị mã UTF-16**; nó không tự:

- Chuẩn hóa Unicode theo NFC/NFD/NFKC/NFKD.
- Bỏ qua chữ hoa hoặc chữ thường.
- Bỏ dấu tiếng Việt.
- Loại ký tự zero-width hoặc variation selector.
- Nhận biết hai glyph nhìn giống nhau thuộc các hệ chữ khác nhau.
- Sử dụng phép so khớp theo ngôn ngữ hoặc thiết lập locale.

Ví dụ:

~~~java
String first = "caf\u00E9";
String second = "cafe\u0301";

boolean equalRaw = first.equals(second); // false
~~~

`==` lại có nghĩa **so identity tham chiếu** và càng không phù hợp để so sánh nội dung. Chuyển `==` thành `equals` chỉ giải quyết bài toán **identity vs value**; vấn đề canonical equivalence vẫn đòi hỏi bước chuẩn hóa theo chính sách ứng dụng.

Đối với `HashMap` và `HashSet`, `String.hashCode()` cũng dựa vào giá trị chuỗi. Hai cách biểu diễn `café` có thể trở thành **hai khóa khác nhau** nếu không được chuẩn hóa trước khi nhập vào cấu trúc dữ liệu.

## 5. Dùng normalization để xác định hai dãy có tương đương hay không

Khi hai chuỗi giống nhau nhưng `equals` là `false`, hãy thử **cả hai phía** với cùng một dạng:

```java
String a = "caf\u00E9";
String b = "cafe\u0301";
System.out.println(a.equals(b)); // false
System.out.println(java.text.Normalizer.normalize(a, java.text.Normalizer.Form.NFC)
        .equals(java.text.Normalizer.normalize(b, java.text.Normalizer.Form.NFC))); // true
```

`NFC/NFD` dùng **tương đương chuẩn tắc** (ví dụ `é` và `e + ◌́`), còn `NFKC/NFKD` xét thêm **tương đương tương thích** (ví dụ `ﬃ` và `ffi`). Bài này tập trung **chẩn đoán sự khác nhau**; bài `Normalizer` trình bày sâu bốn dạng và chính sách chọn dạng.

Nếu hai chuỗi **vẫn khác nhau** sau NFKC, chúng không nhất thiết sai Unicode: chữ Latin `a` (`U+0061`) và chữ Cyrillic `а` (`U+0430`) nhìn giống nhưng khác code point. Normalization không phải công cụ nhận diện tất cả confusable; cần xem code point, hệ chữ và mục đích so sánh.

## 6. Ví dụ Java 17+ đầy đủ: in code point và kiểm tra các trường hợp

Tạo tệp **`UnicodeLookalikeDemo.java`** với nội dung sau:

~~~java
import java.text.Normalizer;
import java.util.Locale;
import java.util.stream.Collectors;

public class UnicodeLookalikeDemo {
    private static String points(String s) {
        return s.codePoints()
                .mapToObj(cp -> String.format("U+%04X", cp))
                .collect(Collectors.joining(" "));
    }

    public static void main(String[] args) {
        String composed = "caf\u00E9";
        String decomposed = "cafe\u0301";

        System.out.println("composed=" + points(composed));
        System.out.println("decomposed=" + points(decomposed));
        System.out.println("rawEquals=" + composed.equals(decomposed));
        System.out.println("rawLengths=" + composed.length() + "/" + decomposed.length());
        System.out.println("codePoints=" + composed.codePointCount(0, composed.length())
                + "/" + decomposed.codePointCount(0, decomposed.length()));

        String nfc1 = Normalizer.normalize(composed, Normalizer.Form.NFC);
        String nfc2 = Normalizer.normalize(decomposed, Normalizer.Form.NFC);
        System.out.println("nfcEquals=" + nfc1.equals(nfc2));
        System.out.println("nfcPoints=" + points(nfc2));
        System.out.println("nfdEquals="
                + Normalizer.normalize(composed, Normalizer.Form.NFD)
                .equals(Normalizer.normalize(decomposed, Normalizer.Form.NFD)));

        String ligature = "\uFB03";
        System.out.println("ligature=" + points(ligature));
        System.out.println("nfcLigatureEqualsFfi="
                + Normalizer.normalize(ligature, Normalizer.Form.NFC).equals("ffi"));
        System.out.println("nfkcLigatureEqualsFfi="
                + Normalizer.normalize(ligature, Normalizer.Form.NFKC).equals("ffi"));
        System.out.println("fullwidthNfkc=" + points(
                Normalizer.normalize("\uFF21", Normalizer.Form.NFKC)));

        String latinA = "a";
        String cyrillicA = "\u0430";
        System.out.println("lookalikeRawEqual=" + latinA.equals(cyrillicA));
        System.out.println("lookalikeNfkcEqual="
                + Normalizer.normalize(latinA, Normalizer.Form.NFKC)
                .equals(Normalizer.normalize(cyrillicA, Normalizer.Form.NFKC)));
        System.out.println("lookalikePoints=" + points(latinA) + " / " + points(cyrillicA));

        String upperDottedI = "\u0130";
        System.out.println("rootLower=" + points(upperDottedI.toLowerCase(Locale.ROOT)));
        System.out.println("turkishLower=" + points(upperDottedI.toLowerCase(
                Locale.forLanguageTag("tr"))));
        System.out.println("caseStillDifferent=" + "Java".equals("java"));
    }
}
~~~

Chạy:

~~~bash
javac --release 17 -encoding UTF-8 UnicodeLookalikeDemo.java
java UnicodeLookalikeDemo
~~~

Kết quả `stdout` đã được đối chiếu bằng trình biên dịch Java và JVM:

~~~text
composed=U+0063 U+0061 U+0066 U+00E9
decomposed=U+0063 U+0061 U+0066 U+0065 U+0301
rawEquals=false
rawLengths=4/5
codePoints=4/5
nfcEquals=true
nfcPoints=U+0063 U+0061 U+0066 U+00E9
nfdEquals=true
ligature=U+FB03
nfcLigatureEqualsFfi=false
nfkcLigatureEqualsFfi=true
fullwidthNfkc=U+0041
lookalikeRawEqual=false
lookalikeNfkcEqual=false
lookalikePoints=U+0061 / U+0430
rootLower=U+0069 U+0307
turkishLower=U+0069
caseStillDifferent=false
~~~

**Ý nghĩa các nhóm kết quả:**

1. `composed` và `decomposed` khác độ dài và khác dãy code point nên `equals` trả `false`. Sau chuẩn hóa `NFC` hoặc `NFD` theo cùng dạng, chúng bằng nhau.
2. `NFC` không biến ligature `ﬃ` thành ba chữ `ffi`, còn `NFKC` thì có thể; `NFKC` cũng chuyển fullwidth `Ａ` thành `A`.
3. Chữ `a` Latin (`U+0061`) và chữ `а` Cyrillic (`U+0430`) có thể trông giống nhau nhưng `NFKC` vẫn **không** biến chúng thành cùng một code point.
4. Đổi chữ hoa/chữ thường là một phép biến đổi **khác với normalization**. Ký tự `İ` (Latin Capital I With Dot Above, `U+0130`) viết thường theo `Locale.ROOT` trở thành hai code point `U+0069 U+0307`; theo locale Thổ Nhĩ Kỳ (`tr`) thành một code point `U+0069`.

Hàm `points` dùng `String.codePoints()`, vì thế có thể in đầy đủ code point supplementary dưới dạng `U+1F600`, không chia thành hai ký tự surrogate như khi lặp theo `charAt`.

## 7. Một nhóm khác: chữ khác hệ chữ nhưng gần giống nhau

Không phải mọi cặp nhìn giống nhau đều tương đương canonical hay compatibility. Ví dụ:

| Ký tự | Hệ chữ | Code point |
| --- | --- | --- |
| `a` | Latin small letter a | `U+0061` |
| `а` | Cyrillic small letter a | `U+0430` |
| `A` | Latin capital letter A | `U+0041` |
| `Α` | Greek capital letter Alpha | `U+0391` |
| `О` | Cyrillic capital letter O | `U+041E` |
| `O` | Latin capital letter O | `U+004F` |

Các ký tự đó có thể có nét chữ tương tự, tùy font. Tuy nhiên, chúng là **code point khác nhau** trong các hệ chữ khác nhau. `Normalizer.normalize(..., NFKC)` không được thiết kế để giải quyết toàn bộ hiện tượng chữ gần giống (*confusable characters*, *homoglyphs*).

Ví dụ:

~~~java
String latin = "a";          // U+0061
String cyrillic = "\u0430"; // U+0430

System.out.println(latin.equals(cyrillic)); // false
System.out.println(
    Normalizer.normalize(latin, Normalizer.Form.NFKC).equals(
        Normalizer.normalize(cyrillic, Normalizer.Form.NFKC)
    )
); // false
~~~

Đây là rủi ro khi hệ thống cho phép tạo username/identifier “nhìn giống nhau”. Hai tài khoản có tên hiển thị gần như nhau vẫn có thể có dãy Unicode khác nhau.

**Phân biệt mục tiêu:** canonical normalization giúp đồng nhất một số cách viết tương đương của *cùng văn bản*. Chính sách chống giả mạo tên định danh cần thêm quy tắc hệ chữ, kiểm tra ký tự gây nhầm lẫn và quy trình xử lý confusables theo chuẩn như Unicode Technical Standard #39. Không dùng `NFKC` đơn lẻ như một “bộ chống giả mạo” hoàn chỉnh.

## 8. Ký tự kết hợp, zero-width, variation selector và emoji

Hai chuỗi không chỉ khác nhau bởi dấu dựng sẵn hay chữ ở các hệ chữ khác nhau. Các **code point ảnh hưởng đến hiển thị** cũng có thể gây nhầm lẫn:

- **Combining mark:** dấu kết hợp như `U+0301` nối với chữ đứng trước để tạo một grapheme cluster. Chuỗi có nhiều code point nhưng có thể xuất hiện thành một chữ có dấu.
- **Zero-width joiner (ZWJ, `U+200D`):** tham gia một số chuỗi emoji và quy tắc nối ký tự; có thể không hiển thị như một ký tự độc lập.
- **Variation selector:** ví dụ `U+FE0F` có thể yêu cầu cách trình bày dạng emoji ở những chuỗi hỗ trợ. Sự hiện diện/thiếu nó khiến dãy code point khác nhau dù các phần mềm hiển thị có thể làm kết quả trông tương tự.
- **Ký tự điều khiển hoặc định dạng vô hình:** có thể ảnh hưởng khoảng cách, thứ tự hiển thị, hướng chữ hoặc so khớp, tùy từng ký tự và môi trường.

Chẳng hạn, hai chuỗi sau có giá trị khác nhau:

~~~java
String plain = "\u2764";        // U+2764, HEAVY BLACK HEART
String emojiStyle = "\u2764\uFE0F"; // U+2764 + VARIATION SELECTOR-16

System.out.println(plain.equals(emojiStyle)); // false
System.out.println(plain.codePointCount(0, plain.length())); // 1
System.out.println(emojiStyle.codePointCount(0, emojiStyle.length())); // 2
~~~

Tùy font và nền tảng, chúng có thể đều hiển thị như trái tim hoặc có hai cách trình bày khác nhau. Không thể đánh giá `String` chỉ bằng ảnh chụp màn hình.

Các phép chuẩn hóa `NFC`/`NFD` không có mục đích xóa tùy tiện ZWJ, variation selector hay các ký tự định dạng khác. **Không tự xóa các code point vô hình** chỉ vì chúng không dễ thấy: nhiều code point trong số đó có vai trò chính đáng đối với ngôn ngữ và emoji. Hãy xác định quy tắc xử lý đúng theo loại trường dữ liệu.

## 9. Hoa/thường và locale là vấn đề riêng với chuẩn hóa

Trong nhóm kiến thức **hoa thường và chuẩn hóa**, cần phân biệt ít nhất ba cách đối chiếu:

1. **Code-unit exact equality:** `a.equals(b)`; không chuẩn hóa, không bỏ qua hoa/thường.
2. **Canonical-normalized equality:** `Normalizer.normalize(a, NFC).equals(Normalizer.normalize(b, NFC))`; vẫn phân biệt hoa/thường.
3. **So sánh không phân biệt hoa/thường hoặc theo quy tắc ngôn ngữ:** cần quyết định đúng API và locale, chẳng hạn `equalsIgnoreCase` cho một số bài toán đơn giản hoặc `Collator` theo ngôn ngữ, và có thể kết hợp normalization khi hợp đồng yêu cầu.

Ví dụ chữ `İ` (`U+0130`) trong chương trình ở phần 6 cho thấy một chuyển đổi chữ thường có thể trả **một hoặc nhiều code point** và phụ thuộc locale. Với một số ngôn ngữ, chọn locale không đúng dẫn đến sai nghĩa; với các khóa kỹ thuật không phụ thuộc ngôn ngữ, `Locale.ROOT` có thể thích hợp hơn locale máy chạy, nhưng điều đó **không tự giải quyết mọi yêu cầu Unicode case folding**.

Không nên mặc định rằng `equalsIgnoreCase` luôn xem tất cả các chuỗi tương đương về mặt ngôn ngữ hoặc về canonical normalization là bằng nhau. Tương tự, `Collator` phục vụ **so sánh văn bản theo locale** và có cấu hình phân rã/mức độ so sánh; lựa chọn đúng tùy mục tiêu, không phải một phép thay thế vô điều kiện cho `equals`.

Lưu ý: thao tác đổi hoa/thường hoặc chuẩn hóa **có thể thay đổi độ dài và chỉ số UTF-16**. Nếu đã tìm một vị trí trong chuỗi trước biến đổi, không tự áp dụng index đó lên chuỗi sau biến đổi.

## 10. Quy trình so sánh dữ liệu đáng tin cậy

Giả sử nghiệp vụ quy định hai chuỗi được xem là giống nhau nếu **canonical equivalent**, bất kể được nhập ở dạng dựng sẵn hay phân rã. Có thể viết:

~~~java
import java.text.Normalizer;
import java.util.Objects;

public class CanonicalComparison {
    static String nfc(String value) {
        return Normalizer.normalize(
                Objects.requireNonNull(value, "value"),
                Normalizer.Form.NFC);
    }

    static boolean sameCanonicalText(String left, String right) {
        return nfc(left).equals(nfc(right));
    }

    public static void main(String[] args) {
        System.out.println(sameCanonicalText("caf\u00E9", "cafe\u0301"));
    }
}
~~~

Khi biên dịch và chạy class này, chương trình in `true`. Phần xử lý `null` được định nghĩa rõ bằng `Objects.requireNonNull`; nếu nghiệp vụ coi null là một giá trị đặc biệt, bạn cần sửa hợp đồng kiểm tra cho phù hợp.

Nếu dùng chuỗi làm **khóa tra cứu**:

~~~java
// Snippet minh họa: map là Map<String, Integer> đã được tạo.
map.put(nfc(userInput), 1);
Integer value = map.get(nfc(query));
~~~

Đây là snippet minh họa, không tự chạy độc lập. Chuẩn hóa thống nhất **khi ghi và khi đọc** để cùng một dạng canonical mang về một key. Nếu lưu cả dạng gốc để hiển thị, tách biệt **original value** và **normalized lookup key**. Điều này cho phép giữ nguyên cách người dùng nhập và vẫn có thể so khớp theo quy tắc xác định.

### Chính sách chuẩn hóa cần trả lời rõ

| Loại dữ liệu | Hướng xử lý cần cân nhắc |
| --- | --- |
| Nội dung hiển thị tự do | Giữ bản gốc; chỉ chuẩn hóa nếu có lý do kỹ thuật/nghiệp vụ |
| Tên sản phẩm, từ khóa tìm kiếm | Quy định chuẩn hóa và xử lý hoa/thường, dấu theo ngôn ngữ; chỉ số tìm kiếm xây trên cùng chính sách |
| Username/mã định danh | Xác định tập ký tự, chuẩn hóa, case rules, kiểm tra confusables và uniqueness; không chỉ dựa vào hình ảnh |
| Dữ liệu có chữ kiểu mẫu hoặc ký hiệu học thuật | Thận trọng với `NFKC`/`NFKD` vì có thể làm mất phân biệt compatibility |
| Văn bản đa ngôn ngữ | Chọn chính sách phù hợp ngôn ngữ; không đồng nhất mọi quy tắc hoa/thường và collation |

Một chiến lược đơn giản cho canonical equality là chọn `NFC` và áp dụng nhất quán. Nhưng **không có một dạng chuẩn hóa tối ưu cho mọi hệ thống**: mục đích sử dụng, khả năng truy vết dữ liệu gốc và chính sách định danh mới quyết định.

## 11. Kiểm tra dãy code point khi hai chuỗi “nhìn y như nhau”

Khi xảy ra lỗi so sánh, đừng bắt đầu bằng việc đổi font rồi đoán. Hãy kiểm tra **độ dài, code point và dạng normalization**:

~~~java
String text = "cafe\u0301";

System.out.println(text.length());
System.out.println(text.codePointCount(0, text.length()));
text.codePoints().forEach(cp -> System.out.printf("U+%04X%n", cp));
~~~

Kết quả:

~~~text
5
5
U+0063
U+0061
U+0066
U+0065
U+0301
~~~

Mỗi code point bổ sung như `U+1F600` xuất hiện thành **một** giá trị `int` khi dùng `codePoints()`; nếu duyệt bằng `charAt`, nó xuất hiện thành hai `char` surrogate. Vì vậy `codePoints()` hữu ích hơn khi cần chẩn đoán vấn đề Unicode ở mức code point.

Một quy trình debug có thể gồm:

1. In code point bằng dạng `U+XXXX` cho **cả hai** chuỗi.
2. So `length()`, `codePointCount(...)` và `equals(...)`, xem khác biệt nằm ở đâu.
3. Thử chuẩn hóa cả hai về `NFC` để biết có cùng canonical form không; nếu cần nghiên cứu compatibility, thử `NFKC` **chỉ cho mục đích chẩn đoán**, tránh áp đặt chính sách biến đổi mới lên dữ liệu sản xuất.
4. Nếu vẫn khác, kiểm tra hệ chữ, dấu kết hợp, variation selector, ZWJ và ký tự định dạng; với identifiers, áp dụng kiểm tra confusable theo đặc tả riêng.
5. Xác định quy tắc ứng dụng mong muốn: *nội dung y hệt*, *canonical equivalent*, *không phân biệt hoa/thường*, *ngang nhau theo locale*, hay *trông tương tự*. Đây là các yêu cầu khác nhau.

Không sử dụng code point log chứa dữ liệu nhạy cảm trong môi trường sản xuất nếu không có kiểm soát phù hợp; khi debug, giới hạn dữ liệu được ghi theo nhu cầu thực tế.

## 12. Những lỗi thường gặp

1. **Cho rằng hai chữ nhìn giống nhau thì `equals` phải trả true.** `equals` so sánh dữ liệu, không so ảnh glyph.
2. **Cho rằng hai cách viết `café` khác nhau là một bên sai Unicode.** Cả dạng dựng sẵn lẫn dạng chữ cơ sở + dấu kết hợp đều hợp lệ; quyết định canonical equality do nghiệp vụ quy định.
3. **Nhầm `String.length()` là số ký tự nhìn thấy.** Nó đếm UTF-16 code units; `codePointCount` cũng chưa đếm grapheme clusters.
4. **Dùng `==` để so sánh nội dung.** `==` chỉ so identity; `equals` là phép so sánh giá trị, sau bước chuẩn hóa nếu cần.
5. **Chuẩn hóa một bên mà không chuẩn hóa bên còn lại.** Khi mục tiêu là canonical equality, dùng cùng `Normalizer.Form` cho cả hai.
6. **Dùng NFKC như cách sửa mọi chuỗi nhìn giống nhau.** NFKC không hợp nhất tất cả chữ Latin/Cyrillic/Greek giống nét; nó cũng có thể làm mất các khác biệt compatibility.
7. **Bỏ qua locale khi đổi hoa/thường.** Ký tự `İ` cho thấy phép lowercasing có thể cho các kết quả khác nhau theo locale và làm đổi số code point.
8. **Tùy tiện xóa dấu kết hợp, zero-width hoặc variation selector.** Có thể phá hỏng nội dung hợp lệ, đặc biệt trong tiếng Việt, văn bản đa ngôn ngữ và emoji.
9. **Dùng index cũ sau khi chuẩn hóa.** Vì normalization có thể đổi độ dài code unit, các vị trí `substring`/`charAt` thuộc chuỗi trước chuẩn hóa không luôn dùng được trên chuỗi sau.
10. **Chỉ chuẩn hóa khi truy vấn mà không chuẩn hóa khi lưu khóa.** Một map hoặc index tìm kiếm sẽ không tra đúng nếu hai bên dùng hai chính sách biểu diễn khác nhau.
11. **Tự xây cơ chế chống giả mạo bằng vài thay thế ký tự.** Confusable handling là vấn đề riêng; cần chính sách identifier và công cụ phù hợp.

## 13. Bài tập tự kiểm tra

**Câu 1.** Tại sao `"caf\u00E9".equals("cafe\u0301")` trả `false`?

**Đáp án:** vế trái có `U+00E9`, vế phải có `U+0065 U+0301`; hai dãy code point và UTF-16 code unit khác nhau dù hiển thị tương đương canonical.

**Câu 2.** Biến đổi thế nào để so sánh canonical equivalence?

**Đáp án:** chuẩn hóa **cả hai** về cùng dạng `NFC` hoặc `NFD` rồi gọi `equals`.

**Câu 3.** `NFC` có đổi `ﬃ` thành `ffi` không? `NFKC` thì sao?

**Đáp án:** `NFC` không; `NFKC` thực hiện compatibility decomposition và có thể đổi ligature `U+FB03` thành ba chữ `ffi`.

**Câu 4.** Vì sao `Normalizer.normalize("a", NFKC)` và `Normalizer.normalize("\u0430", NFKC)` vẫn khác nhau?

**Đáp án:** chúng thuộc hai hệ chữ khác nhau, Latin `U+0061` và Cyrillic `U+0430`. Giống về hình ảnh không có nghĩa tương đương Unicode normalization.

**Câu 5.** `String.length()` có đo được số grapheme cluster không?

**Đáp án:** không. Nó đếm UTF-16 code unit. `codePointCount` đếm code point, vẫn không phải grapheme cluster.

**Câu 6.** Có thể dùng `NFKC` mặc định cho mọi dữ liệu nhập không?

**Đáp án:** không. NFKC có thể gộp những khác biệt compatibility có ý nghĩa; cần lựa chọn theo mục tiêu của trường dữ liệu.

**Câu 7.** Vì sao nên giữ bản gốc khi xây khóa tìm kiếm chuẩn hóa?

**Đáp án:** khóa đã chuẩn hóa giúp tìm kiếm nhất quán, còn bản gốc giữ đúng cách người dùng nhập, hỗ trợ hiển thị và kiểm tra khi cần. Hai vai trò này không nên đánh đồng.

## 14. Tài liệu chuẩn

- [Java SE 17 — `java.text.Normalizer`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/Normalizer.html): bốn dạng chuẩn hóa và cách sử dụng trong Java.
- [Java SE 17 — `String`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html): `equals`, `length`, `codePoints`, `codePointCount` và chuyển đổi hoa/thường.
- [Unicode Standard Annex #15 — Unicode Normalization Forms](https://www.unicode.org/reports/tr15/): định nghĩa NFC, NFD, NFKC và NFKD.
- [Unicode Standard Annex #29 — Unicode Text Segmentation](https://www.unicode.org/reports/tr29/): grapheme cluster và phân đoạn văn bản.
- [Unicode Technical Standard #39 — Unicode Security Mechanisms](https://www.unicode.org/reports/tr39/): nhận diện các dạng ký tự confusable trong identifier.

**Ghi nhớ:** “nhìn giống” là đặc điểm hiển thị, còn `String` chứa **dãy UTF-16 code unit cụ thể**. Dùng `codePoints()` để xem dữ liệu thật; dùng `Normalizer` khi cần chuẩn hóa canonical/compatibility theo một **chính sách đã xác định**; và xử lý riêng trường hợp chữ gần giống khác hệ chữ, Unicode case rules cùng các grapheme cluster phức tạp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="case-normalization-007">Phân biệt chuẩn hóa Unicode với loại bỏ dấu</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt chuẩn hóa Unicode với loại bỏ dấu trong Java

## 1. Hai thao tác khác nhau, dù thường xuất hiện cạnh nhau

Trong Java, một `String` là dãy đơn vị mã UTF-16. Văn bản trông giống nhau có thể được biểu diễn bởi các dãy code point khác nhau, chẳng hạn chữ **é** có thể là:

- Dạng tiền tổ hợp: `U+00E9` (ký tự `é`, một code point).
- Dạng phân rã: `U+0065 U+0301` (chữ `e` và dấu sắc kết hợp, hai code point).

**Chuẩn hóa Unicode (Unicode normalization)** chuyển văn bản về một trong những dạng biểu diễn được Unicode quy định để xử lý các biểu diễn tương đương một cách thống nhất. Ví dụ, NFC biến `e + U+0301` thành `é` khi có dạng ghép thích hợp; NFD chuyển ngược thành `e + U+0301`. **Dấu sắc vẫn còn** trong cả hai trường hợp.

**Loại bỏ dấu (accent/diacritic stripping)** là chính sách biến đổi mang tính ứng dụng, thường nhằm tạo từ khóa tìm kiếm hoặc biểu diễn gần ASCII: `café` thành `cafe`, `Cà phê` thành `Ca phe`. Bước này **thay đổi thông tin văn bản**, có thể làm những từ khác nghĩa trở nên giống nhau. Unicode normalization theo nghĩa chuẩn **không đồng nghĩa** với loại bỏ dấu.

Ba câu hỏi phải tách biệt:
1. **Có cần thống nhất các biểu diễn Unicode tương đương không?** Dùng `java.text.Normalizer` với NFC/NFD (hoặc NFKC/NFKD nếu chủ ý dùng tương đương compatibility).
2. **Có cần bỏ một số dấu thanh, dấu phụ để tìm kiếm không?** Xác định rõ tập dấu được bỏ, script áp dụng, và cách xử lý ký tự không phân rã.
3. **Có cần so sánh/sắp xếp theo ngôn ngữ người dùng không?** Xem `java.text.Collator` với `Locale` phù hợp; không thay thế bằng xóa dấu tùy tiện.

## 2. Phân biệt chữ, code point, đơn vị UTF-16 và dấu kết hợp

`String.length()` đếm **đơn vị mã UTF-16** (`char`), không đếm số ký tự người dùng nhìn thấy. `String.codePointCount(begin,end)` đếm Unicode code point trong khoảng UTF-16. Một *grapheme cluster* có thể gồm chữ cơ sở cộng nhiều dấu kết hợp hoặc một chuỗi emoji phức tạp.

Ví dụ:

```text
Hiển thị:                  é        é
Biểu diễn:                U+00E9    U+0065 U+0301
Số code point:              1        2
Số char UTF-16:             1        2
Chuẩn hóa NFC:            U+00E9   → U+00E9
Chuẩn hóa NFD:       U+0065 U+0301 ← U+00E9
Bỏ dấu sau NFD:           "e"       "e"
```

`U+0301` là một dấu kết hợp thuộc nhóm *Unicode Mark*. Ngược lại, không phải bất kỳ ký tự “nhìn có dấu” nào cũng được tách thành một chữ Latin cơ sở bằng NFD. Chữ tiếng Việt `đ` (`U+0111`) và `Đ` (`U+0110`) là ví dụ quan trọng: chuẩn hóa NFD **không** đổi chúng thành `d`/`D`.

Ký tự ngoài BMP (như một số emoji) lại chiếm **hai** `char` và không vì thế mà chứa “hai ký tự hiển thị”. Không nên xóa dấu bằng cách duyệt `charAt` rồi đoán từ hình dáng ký tự; cần hiểu rõ dữ liệu Unicode và quy tắc của bài toán.

## 3. NFC/NFD giữ dấu; phép loại dấu cần một bước riêng

`Normalizer.normalize(text, Normalizer.Form.NFC)` đưa chuỗi về dạng ghép khi có thể, còn `NFD` đưa về dạng phân rã. **Cả hai vẫn giữ dấu**. `NFKC/NFKD` còn xét tương đương tương thích nhưng bản thân chúng cũng không phải thao tác xóa dấu. Bài riêng về `Normalizer` trình bày bảng đầy đủ cả bốn dạng.

## 4. Thử nghiệm đối chiếu canonical equivalence

```java
String a = "Caf\u00E9";
String b = "Cafe\u0301";
String nfc = java.text.Normalizer.normalize(b, java.text.Normalizer.Form.NFC);
String nfd = java.text.Normalizer.normalize(a, java.text.Normalizer.Form.NFD);
System.out.println(a.equals(b));         // false
System.out.println(nfc.equals(a));       // true
System.out.println(nfd.equals(b));       // true
System.out.println(nfd.equals("Cafe"));  // false: dấu vẫn còn
```

Đây là snippet đặt trong một phương thức. Chuẩn hóa hai phía theo cùng dạng giúp đối chiếu canonical equivalence, nhưng muốn tìm `"Cafe"` từ `"Café"` theo **chính sách bỏ dấu** thì vẫn cần bước lọc độc lập.

## 5. Loại bỏ dấu: một phép biến đổi riêng sau khi phân rã

Một cách thường dùng để tạo **khóa tìm kiếm Latin bỏ dấu** là:
1. Đưa chuỗi về `Normalizer.Form.NFD` để các chữ **có phân rã chuẩn tắc** tách thành chữ cơ sở và dấu kết hợp.
2. Xóa các code point thuộc nhóm Unicode `Mark` theo quy tắc đã định, ví dụ dùng Java regex `\p{M}+`.
3. Nếu sản phẩm cần, ánh xạ thêm những chữ **không phân rã** như `đ → d`, `Đ → D`.

Ví dụ snippet:

```java
String text = "C\u00E0 ph\u00EA s\u1EEFa \u0111\u00E1";
String decomposed = Normalizer.normalize(
        text, Normalizer.Form.NFD);
String withoutMarks = decomposed.replaceAll("\\p{M}+", "");
String asciiLike = withoutMarks
        .replace('\u0111', 'd')
        .replace('\u0110', 'D');
```

Kết quả của `withoutMarks` là **`Ca phe sua đa`**, vì `đ` vẫn được giữ nguyên. `asciiLike` có nội dung **`Ca phe sua da`** sau quy tắc ánh xạ `đ`. Chúng là **kết quả đã biến đổi**, không còn là văn bản gốc chuẩn tắc tương đương.

Trong Java regex:
- `\p{M}` là nhóm Unicode general category **Mark**: bao gồm *Nonspacing Mark* (`Mn`), *Spacing Combining Mark* (`Mc`) và *Enclosing Mark* (`Me`).
- `+` nghĩa là một hoặc nhiều phần tử `Mark` liên tiếp.
- Trong **string literal Java**, phải gõ `"\\p{M}+"` (hai dấu gạch chéo ngược trong mã Java) để truyền chuỗi regex thực `\p{M}+` cho `replaceAll`.
- `replaceAll` nhận **regex**, không phải chuỗi literal; thay bằng `""` xóa các vùng khớp.

`\p{M}` bao phủ nhiều dấu Unicode hơn riêng khối `\p{InCombiningDiacriticalMarks}` (U+0300..U+036F). Nhưng phạm vi rộng cũng đồng nghĩa với **rủi ro xóa các dấu cần thiết ở những hệ chữ khác**. Không xem nó là phép “xóa dấu tiếng Việt an toàn cho mọi ngôn ngữ”.

### Chương trình chạy được: hai bước tạo khóa tiếng Việt

Lưu thành `VietnameseAccentDemo.java`:

```java
import java.text.Normalizer;
import java.util.regex.Pattern;

public class VietnameseAccentDemo {
    private static final Pattern MARKS =
            Pattern.compile("\\p{M}+");

    static String removeMarks(String input) {
        String nfd = Normalizer.normalize(
                input, Normalizer.Form.NFD);
        return MARKS.matcher(nfd).replaceAll("");
    }

    static String vietnameseSearchKey(String input) {
        return removeMarks(input)
                .replace('\u0111', 'd')
                .replace('\u0110', 'D');
    }

    public static void main(String[] args) {
        String phrase = "C\u00E0 ph\u00EA s\u1EEFa \u0111\u00E1";
        String name = "\u0110\u1EB7ng";

        System.out.println(removeMarks(phrase));
        System.out.println(vietnameseSearchKey(phrase));
        System.out.println(removeMarks(name));
        System.out.println(vietnameseSearchKey(name));

        String bothWays = "Caf\u00E9 / Cafe\u0301";
        System.out.println(removeMarks(bothWays));
    }
}
```

```bash
javac -encoding UTF-8 VietnameseAccentDemo.java
java VietnameseAccentDemo
```

```text
Ca phe sua đa
Ca phe sua da
Đang
Dang
Cafe / Cafe
```

Hàm `removeMarks` thể hiện thao tác **xóa Mark sau NFD**. Hàm `vietnameseSearchKey` bổ sung lựa chọn **đ/Đ** dành cho tình huống tạo khóa Latin/tiếng Việt. Các hàm đều trả một `String` mới về nội dung; đầu vào bất biến không bị sửa.

`removeMarks` không có nghĩa “giữ trọn ý nghĩa tiếng Việt”: `"ma"`, `"má"`, `"mà"`, `"mã"`, `"mạ"` có thể trở thành cùng chuỗi. Vì vậy bản bỏ dấu nên là **giá trị phụ trợ tìm kiếm**, không được mặc định thay thế chuỗi gốc lưu trong cơ sở dữ liệu hoặc trình bày cho người dùng.

## 6. Chuẩn hóa *không* làm biến mất dấu; xóa dấu *không* phải chuẩn hóa chuẩn tắc

Xét ba chuỗi:

```text
A = "é"                 (U+00E9)
B = "e" + U+0301         (U+0065 U+0301)
C = "e"                 (U+0065)
```

Hai chuỗi `A` và `B` là **tương đương chuẩn tắc**. NFC có thể biến chúng thành cùng biểu diễn `é`; NFD có thể biến cả hai thành `e + dấu sắc`. Chuỗi `C` không tương đương chuẩn tắc với `A` hay `B`: nó thiếu thông tin về dấu.

Nếu viết:

```java
String a = "\u00E9";
String b = "e\u0301";
String c = "e";

System.out.println(Normalizer.normalize(
        a, Normalizer.Form.NFC).equals(
        Normalizer.normalize(b, Normalizer.Form.NFC))); // true

System.out.println(Normalizer.normalize(
        a, Normalizer.Form.NFC).equals(c)); // false
```

Đây là snippet cần `import java.text.Normalizer;`, dùng trong phương thức `main`. Chính sự khác biệt giữa `A/B` và `C` giải thích vì sao “normalize Unicode” **không phải** “convert to unaccented text”.

Bước xóa dấu có thể tạo ra **va chạm thông tin**: nhiều chuỗi khác nhau chuyển thành cùng khóa. Nếu từ ngữ hoặc tên có dấu là dữ liệu gốc, xóa dấu trước khi lưu mà không giữ lại bản gốc có thể gây mất thông tin không thể khôi phục.

## 7. Không nhầm compatibility normalization với loại dấu

`NFKC/NFKD` có thể biến `ﬃ` (`U+FB03`) thành `ffi`, `①` thành `1` và `Ａ` thành `A`. Những ví dụ đó **không phải loại dấu**. Ngược lại, một chữ tiếng Việt có dấu thanh khi chuyển NFKD vẫn chứa thông tin dấu trừ khi có quy tắc tương thích đặc biệt khác. Đừng dùng `NFKD` chỉ để hy vọng tạo chuỗi ASCII; chọn `NFD` trước bước lọc `Mark` khi chính sách tìm kiếm Latin/tiếng Việt cho phép.

## 8. Sự khác biệt giữa `Normalizer`, `toLowerCase` và `Collator`

Ba tác vụ này liên quan đến văn bản nhưng có **mục tiêu khác nhau**:

| Tác vụ | API thường dùng | Ý nghĩa |
| --- | --- | --- |
| Thống nhất biểu diễn Unicode tương đương | `Normalizer.normalize(text, NFC)` | Chuẩn hóa dạng biểu diễn; giữ phân biệt có/không có dấu |
| Loại bỏ dấu theo chính sách ứng dụng | NFD rồi lọc `Mark`, ánh xạ tùy chọn `đ/Đ` | Tạo dữ liệu đã mất thông tin dấu |
| Chuyển chữ hoa/thường | `toLowerCase(Locale.ROOT)` hoặc locale xác định | Biến đổi case theo quy tắc Java/locale; **không xóa dấu** |
| So sánh có xét ngôn ngữ | `Collator.getInstance(locale)` | So sánh/sắp xếp theo quy tắc ngôn ngữ, có mức độ phân biệt |
| So bằng code unit sau chuẩn hóa | `normalize(a, NFC).equals(normalize(b, NFC))` | Xác định bằng nhau theo biểu diễn canonical đã chọn |

`String.toLowerCase()` phụ thuộc locale mặc định. Nếu tạo khóa tìm kiếm độc lập với ngôn ngữ hệ thống, có thể dùng `Locale.ROOT` một cách tường minh, nhưng phải xác định yêu cầu nghiệp vụ: với ngôn ngữ như tiếng Thổ Nhĩ Kỳ, cách chuyển hoa/thường và mong đợi của người dùng khác với quy tắc trung lập.

`Collator` hỗ trợ so sánh theo locale; có các mức phân biệt (*strength*) như PRIMARY, SECONDARY và TERTIARY. Ở nhiều locale, PRIMARY có thể coi một số khác biệt dấu và hoa/thường là ngang hàng, nhưng **đừng đồng nhất** nó với thao tác xóa `\p{M}`. Quy tắc thật phụ thuộc locale/phiên bản dữ liệu collation và yêu cầu ngôn ngữ. `Collator.compare(a,b) == 0` chỉ nói hai chuỗi tương đương ở mức cấu hình, không có nghĩa chuỗi gốc đã mất dấu hoặc được đổi nội dung.

Đối với hệ thống có ô tìm kiếm, thường nên giữ văn bản nguyên bản cho hiển thị và xây dựng chỉ mục/khóa tìm kiếm riêng theo đặc tả rõ ràng.

## 9. Vì sao xóa `\p{M}` có thể phá văn bản ngoài nhóm Latin?

Unicode `Mark` không chỉ là các dấu sắc, huyền, hỏi, ngã, nặng của tiếng Việt. Nhiều hệ chữ dùng dấu nguyên âm, dấu phụ hoặc ký hiệu kết hợp mang vai trò **cấu thành chữ viết**.

Ví dụ:
- Trong một số hệ chữ Ấn Độ, vowel signs có thể thuộc `Mark`. Xóa tất cả `\p{M}` có thể làm thay đổi cách đọc và cả ý nghĩa từ.
- Chữ Ả Rập có dấu nguyên âm (*harakat*) thuộc các dạng mark; việc loại bỏ chúng có thể đúng trong một số bài toán tìm kiếm nhưng là lựa chọn có tính ngôn ngữ, không phải chuẩn hóa canonical.
- Variation selector của emoji có thể thuộc loại mark. Xóa nó có thể thay đổi kiểu hiển thị emoji/text của cùng ký hiệu, dù không thay đổi chữ Latin có dấu.
- Có dấu kết hợp nằm ngoài riêng khối `U+0300..U+036F`; dùng biểu thức chỉ bắt một khối có thể bỏ sót dấu ở các vùng khác của Unicode.

Vì vậy, câu lệnh `replaceAll("\\p{M}+", "")` chỉ nên xuất hiện trong **hàm ứng dụng có tên rõ ràng** (ví dụ `latinSearchKey`), sau khi đã xác định bộ ngôn ngữ/kiểu dữ liệu cho phép mất dấu. Nếu ứng dụng xử lý đa ngôn ngữ, cân nhắc bộ phân tích ngôn ngữ riêng, Unicode collation, quy tắc transliteration được chỉ định hoặc chỉ tạo khóa phụ cho một tập dữ liệu đã khoanh vùng.

### `đ/Đ` và các ngoại lệ của chuyển tự

`đ` là chữ cái riêng, không phải chữ `d` có combining mark mà NFD sẽ xóa được. Các ký tự như `ø`, `ł` hoặc `æ` cũng không phải lúc nào phân rã thành các chữ ASCII mong muốn theo NFD. Phép **transliteration** (chuyển tự) để có dạng ASCII là một chính sách khác, có thể cần bảng ánh xạ theo ngôn ngữ.

Không khẳng định rằng “NFD + xóa Mark = ASCII”: kết quả vẫn có thể chứa ký tự Unicode ngoài ASCII. Ngay cả sau khi thêm `đ → d`, dữ liệu đầu vào chưa biết ngôn ngữ vẫn có thể cần những quy tắc khác.

## 10. Lưu trữ và tìm kiếm: giữ dữ liệu gốc, tạo khóa phụ khi cần

Thiết kế đơn giản, an toàn về ý nghĩa:

```text
Dữ liệu nhập
    │
    ├── Giá trị hiển thị: giữ nguyên văn bản người dùng nhập
    │
    ├── Giá trị canonical (tùy chính sách): normalize NFC
    │
    └── Khóa tìm kiếm (tùy nhu cầu):
           NFD → bỏ Mark theo quy tắc → ánh xạ đ/Đ
           → biến đổi hoa/thường theo chính sách riêng nếu cần
```

Dùng khóa phụ giúp tìm `"ca phe"` khớp với `"cà phê"` trong trải nghiệm tìm kiếm có chủ đích, nhưng không thay nội dung lưu/hiển thị bằng bản bỏ dấu. Nếu dùng khóa bỏ dấu làm khóa duy nhất của cơ sở dữ liệu, `"ma"`, `"má"`, `"mạ"`, `"mà"` có thể xung đột ngoài ý muốn.

Ví dụ độc lập, lưu `SearchKeyDemo.java`:

```java
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public class SearchKeyDemo {
    private static final Pattern MARKS =
            Pattern.compile("\\p{M}+");

    static String searchKey(String value) {
        String canonical = Normalizer.normalize(
                value, Normalizer.Form.NFD);
        String noMarks = MARKS.matcher(canonical).replaceAll("");
        return noMarks.replace('\u0111', 'd')
                .replace('\u0110', 'D')
                .toLowerCase(Locale.ROOT);
    }

    public static void main(String[] args) {
        String displayed = "C\u00E0 ph\u00EA s\u1EEFa \u0111\u00E1";
        String typed = "ca phe sua da";

        System.out.println(displayed);
        System.out.println(searchKey(displayed));
        System.out.println(searchKey(displayed).equals(searchKey(typed)));
        System.out.println(displayed.equals(typed));
    }
}
```

```bash
javac -encoding UTF-8 SearchKeyDemo.java
java SearchKeyDemo
```

```text
Cà phê sữa đá
ca phe sua da
true
false
```

Hàm `searchKey` chỉ phù hợp với **chính sách minh họa cho dữ liệu Latin/tiếng Việt**. Nó tạo một chuỗi phụ để tra cứu gần đúng, không cung cấp thuật toán bảo mật, chuẩn hóa identifier, chuyển tự tổng quát, hay bảo đảm tương đương ngữ nghĩa giữa mọi ngôn ngữ.

Nếu kết quả dùng để kiểm tra mật khẩu, chữ ký, định danh pháp lý, tên file, quyền truy cập hoặc đối chiếu mã hệ thống, cần tuân theo quy tắc canonicalization đúng **đặc tả miền đó**. Không áp dụng tùy tiện normalization compatibility hoặc xóa dấu rồi cho rằng hai định danh là giống nhau.

## 11. Tính đúng đắn, hiệu năng và tính nhất quán

- **Xác định ranh giới chuẩn hóa**: nếu một hệ thống chuẩn hóa NFC trước khi ghi nhưng truy vấn không được chuẩn hóa cùng cách, so sánh chính xác có thể vẫn bỏ sót kết quả.
- **Giữ bản gốc khi xóa dấu**: phép xóa dấu là nhiều-thành-một; không thể phục hồi dấu tiếng Việt chính xác từ `ca phe sua da`.
- **Không dùng `String.length()` để đo số chữ hiển thị**: normalization có thể thay đổi số code point/UTF-16 code unit nhưng giữ văn bản hiển thị tương đương canonical.
- **Phân biệt thứ tự thao tác**: normalize sang NFD trước khi xóa Mark. Nếu xóa Mark trực tiếp từ `"café"` tiền tổ hợp, regex `\p{M}` không khớp chữ `é` (nó thuộc loại Letter), nên dấu không bị xóa.
- **Không mặc định mọi output chỉ có ASCII**: vì NFD không phân rã hết mọi chữ sang Latin cơ sở.
- **Hạn chế cấp phát khi lặp lại liên tục**: chuẩn hóa, regex và đổi chữ đều tạo công việc xử lý và có thể tạo chuỗi mới; khi cần truy vấn nhiều lần, cân nhắc tính khóa tại lúc nhập hoặc lập chỉ mục, nhưng luôn theo một chính sách nhất quán.
- **Hiệu năng không thay thế chuẩn định nghĩa**: đừng tối ưu bằng cách làm mất dấu, mã hóa hoặc phép so sánh nếu nghiệp vụ cần bảo tồn sự khác nhau giữa các giá trị.

Một tính chất hữu ích: chuẩn hóa đúng dạng Unicode có tính **idempotent** về nội dung khi áp dụng lặp lại: `normalize(normalize(x, NFC), NFC)` bằng `normalize(x, NFC)`. Nhưng “idempotent” chỉ nói lặp bước chuẩn hóa không làm thay đổi thêm; nó **không** có nghĩa đã bỏ dấu.

## 12. Lỗi thường gặp

| Nhầm lẫn | Vì sao sai | Cách sửa |
| --- | --- | --- |
| `Normalizer.normalize(s, NFC)` sẽ bỏ dấu | NFC tổ chức lại biểu diễn, **giữ dấu** | Dùng bước loại dấu riêng nếu nghiệp vụ yêu cầu |
| NFD làm thành chữ không dấu | NFD tách dấu thành các code point kết hợp; dấu vẫn tồn tại | Phải quyết định có xóa `Mark` hay không |
| `replaceAll("\\p{M}+", "")` áp dụng trực tiếp trên mọi chuỗi | Chữ tiền tổ hợp có dấu như `é` là Letter, không phải Mark riêng | NFD trước khi xóa Mark |
| NFD + xóa Mark biến mọi ký tự thành ASCII | `đ` và nhiều chữ khác không phân rã thành ký tự ASCII | Ánh xạ bổ sung theo ngôn ngữ, hoặc dùng công cụ chuyển tự |
| Xóa `\p{M}` an toàn với mọi hệ chữ | Mark có thể là thành phần cốt lõi của chữ viết | Giới hạn tập ngôn ngữ và đặc tả mục tiêu |
| NFKD/NFKC “giống NFD/NFC nhưng mạnh hơn” mà không có tác dụng phụ | Compatibility normalization cũng biến đổi ligature/số khoanh tròn/fullwidth | Chỉ dùng khi chủ đích gộp các biến thể đó |
| Chuỗi nhìn giống nhau thì `equals` bằng nhau | `String.equals` không chuẩn hóa Unicode | Chuẩn hóa hai vế về cùng dạng trước khi so |
| Loại dấu tương đương so sánh `Collator` | Collation là quy tắc thứ tự/tương đương theo locale và strength | Chọn `Collator` cho nhu cầu ngôn ngữ |
| Bỏ dấu rồi dùng giá trị làm khóa duy nhất | Nhiều từ khác dấu có thể trùng khóa | Giữ bản gốc và thiết kế chính sách va chạm |
| Dùng `toLowerCase()` không chỉ định locale cho khóa hệ thống | Kết quả có thể phụ thuộc locale mặc định | Chọn `Locale.ROOT` hoặc locale nghiệp vụ tường minh |
| Đếm code point và cho rằng bằng số grapheme | Dấu kết hợp, emoji ZWJ có thể tạo một ký tự hiển thị từ nhiều code point | Dùng thuật toán phân đoạn grapheme khi cần |

## 13. Bài tập và đáp án

**Bài 1.** `"Caf\u00E9"` và `"Cafe\u0301"` có `equals` bằng nhau không? Sau khi đưa cả hai về NFC thì sao?

**Bài 2.** Với `"C\u00E0 ph\u00EA"`, NFD làm dấu mất hẳn hay chỉ tách dấu thành code point riêng? Cần bước nào để tạo `"Ca phe"`?

**Bài 3.** Vì sao NFD rồi xóa `\p{M}` trên `"Đặng"` thu được `"Đang"` chứ chưa phải `"Dang"`?

**Bài 4.** Chọn NFC hay NFKC nếu chỉ cần thống nhất biểu diễn canonical của dữ liệu người dùng và **không muốn** số `①` bị gộp thành `1`?

**Bài 5.** Khi xây dựng tìm kiếm bỏ dấu cho tiếng Việt, vì sao phải lưu lại văn bản gốc và kiểm tra các xung đột khóa?

**Đáp án:**
1. So trực tiếp là `false`; sau NFC cho cả hai là `true`.
2. NFD **tách** dấu; sau đó xóa các `Mark` bằng chính sách được xác định.
3. `Đ` không bị phân rã thành `D + Mark` bởi NFD; muốn `Dang` phải ánh xạ thêm `Đ → D`.
4. Dùng **NFC** vì `NFKC` có thể thực hiện compatibility transformations như `① → 1`.
5. Vì bỏ dấu làm mất thông tin và nhiều từ khác nhau có thể trùng khóa; bản gốc cần cho hiển thị, bảo toàn ngữ nghĩa và phân biệt dữ liệu.

## 14. Kết luận thực hành

**Chuẩn hóa Unicode** là thao tác về **biểu diễn**: NFC/NFD đưa các biểu diễn tương đương chuẩn tắc về cùng dạng trong khi vẫn giữ sự khác nhau giữa chữ có dấu và chữ không dấu. **Loại bỏ dấu** là thao tác về **chính sách nội dung**: có thể dùng NFD để tách dấu rồi bỏ các `Mark` và bổ sung quy tắc tiếng Việt như `đ/Đ`, nhưng nó là phép biến đổi mất thông tin, không phải kết quả mặc định của `Normalizer`.

Nếu cần dữ liệu chính xác, hãy giữ văn bản gốc và chuẩn hóa canonical nhất quán. Nếu cần tìm kiếm bỏ dấu, hãy tạo khóa riêng với phạm vi ngôn ngữ và quy tắc rõ ràng. Nếu cần so sánh theo ngôn ngữ, hãy xem `Collator` thay vì coi việc bỏ dấu là phép so sánh Unicode tổng quát.

## Tài liệu Java SE 17

- [`java.text.Normalizer` — NFC, NFD, NFKC, NFKD](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/Normalizer.html)
- [`java.util.regex.Pattern` — Unicode categories và `\p{M}`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html)
- [`java.lang.String` — `equals`, `replaceAll`, `codePointCount`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html)
- [`java.text.Collator` — so sánh theo locale và strength](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/Collator.html)
- [`java.util.Locale` — quy tắc locale khi chuyển chữ](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Locale.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="case-normalization-008">Collator khi cần so sánh theo quy tắc ngôn ngữ</a>

<details>
<summary>Xem chi tiết</summary>

# `Collator` trong Java: so sánh chuỗi theo quy tắc ngôn ngữ

Khi người dùng xem danh sách tên người, từ ngữ hoặc nhãn sản phẩm, họ thường mong **thứ tự bảng chữ cái theo ngôn ngữ**. Thứ tự này có thể khác thứ tự UTF-16 mà `String.compareTo()` sử dụng. Java cung cấp **`java.text.Collator`** để so sánh và sắp xếp chuỗi theo những quy tắc *collation* phụ thuộc `Locale`, như tiếng Việt, tiếng Đức hay tiếng Thụy Điển.

**Ý chính:** `String.equals()` kiểm tra cùng nội dung; `String.compareTo()` so theo thứ tự code unit của chuỗi; còn **`Collator.compare(a, b)`** xác định thứ tự (và mức độ tương đương khi so sánh) theo ngôn ngữ và cấu hình. `Collator` rất hữu ích cho giao diện hoặc danh sách hướng tới người đọc, nhưng không tự động là công cụ phù hợp để kiểm tra token, mã định danh hoặc quyền truy cập.

## 1. Vì sao cần `Collator`?

Các bảng chữ cái không luôn xử lý chữ có dấu theo cùng một cách. Ví dụ, trong cách sắp xếp phổ biến của tiếng Đức, `ä` thường được xếp gần `a`; còn trong tiếng Thụy Điển, `ä` đứng sau `z`. Vì vậy một thứ tự dựa trên giá trị UTF-16 không thể đại diện cho mọi ngôn ngữ.

Java có ba cách quen thuộc:

| API | Quy tắc cơ bản | Nhu cầu phù hợp |
| --- | --- | --- |
| `a.equals(b)` | So sánh **toàn bộ nội dung `String`**; phân biệt hoa/thường và code unit | Kiểm tra mã, chuỗi chính xác |
| `a.compareTo(b)` | So thứ tự từ điển dựa trên **UTF-16 code unit**, không phụ thuộc `Locale` | Quy ước sắp xếp kỹ thuật cố định |
| `collator.compare(a, b)` | So theo **quy tắc collation của `Locale` và cấu hình** | Sắp xếp tên, từ và nhãn hiển thị |
| `a.compareToIgnoreCase(b)` | So không phân biệt hoa/thường theo cách của `String`, **không phải collation theo `Locale`** | So sánh đơn giản khi không cần quy tắc ngôn ngữ |

Ví dụ sử dụng `Collator`:

```java
import java.text.Collator;
import java.util.Locale;

Collator collator = Collator.getInstance(Locale.forLanguageTag("vi-VN"));
int result = collator.compare("An", "Bình");
```

Đây là **đoạn mã minh họa đặt trong phương thức**. `Locale.forLanguageTag("vi-VN")` mô tả ngôn ngữ và vùng mong muốn. Nhà cung cấp dữ liệu locale của JDK quyết định những quy tắc thực tế được áp dụng; khi thiếu dữ liệu chuyên biệt, môi trường có thể sử dụng quy tắc dự phòng. Vì vậy nên thử trên JDK và locale đang triển khai, đặc biệt với tên có dấu.

## 2. Lấy `Collator` theo `Locale` và đọc kết quả so sánh

`Collator` là lớp trừu tượng. Thông thường không gọi `new Collator()` mà tạo đối tượng qua:

```java
Collator collator = Collator.getInstance(Locale.US);
```

Một số cách tạo `Locale`:

```java
Locale vietnamese = Locale.forLanguageTag("vi-VN");
Locale swedish = Locale.forLanguageTag("sv-SE");
Locale german = Locale.GERMAN;
```

Ví dụ trên là các khai báo dùng trong phạm vi có `import java.util.Locale;`.

Kết quả `collator.compare(a, b)` là một số nguyên:

- **Âm**: `a` đứng trước `b` theo collator.
- **Bằng 0**: `a` và `b` được xem là **tương đương ở mức so sánh đang cấu hình**.
- **Dương**: `a` đứng sau `b`.

Không dựa vào giá trị cụ thể như `-1` hay `1` trong mọi trường hợp. Dùng `< 0`, `== 0` và `> 0`; với phần minh họa muốn in dấu so sánh, có thể dùng `Integer.signum(...)` để chuyển kết quả về `-1`, `0` hoặc `1`.

### Ví dụ: cùng ký tự, thứ tự khác nhau giữa các locale

Lưu tệp `LocaleOrderDemo.java`:

```java
import java.text.Collator;
import java.util.Locale;

public class LocaleOrderDemo {
    public static void main(String[] args) {
        Collator german = Collator.getInstance(Locale.GERMAN);
        Collator swedish = Collator.getInstance(
                Locale.forLanguageTag("sv-SE"));

        String aUmlaut = "\u00E4"; // ä

        System.out.println(Integer.signum(
                german.compare(aUmlaut, "z")));
        System.out.println(Integer.signum(
                swedish.compare(aUmlaut, "z")));
    }
}
```

Biên dịch và chạy bằng Java 17+:

```bash
javac -encoding UTF-8 LocaleOrderDemo.java
java LocaleOrderDemo
```

Với dữ liệu locale của OpenJDK sử dụng trong ví dụ, kết quả:

```text
-1
1
```

`-1` ở dòng đầu cho biết `ä` được xếp trước `z` trong ví dụ tiếng Đức; `1` ở dòng sau cho biết `ä` được xếp sau `z` trong ví dụ tiếng Thụy Điển. **Bộ quy tắc có thể biến đổi theo phiên bản JDK, cấu hình locale provider và dữ liệu ngôn ngữ**, do đó không nên dùng một danh sách ví dụ làm bảng xếp hạng bất biến cho mọi môi trường. Khi cần tái lập dữ liệu giữa hệ thống, cố định môi trường/phiên bản collation.

## 3. Điều chỉnh mức độ phân biệt bằng `setStrength()`

`Collator` cho phép xác định **độ mạnh (strength)** khi so sánh. Mức càng cao càng xét thêm những khác biệt nhỏ giữa các chuỗi.

| Strength | Ý tưởng chính | Khác biệt thường có tác dụng |
| --- | --- | --- |
| `Collator.PRIMARY` | So những khác biệt cơ bản của chữ | Trong nhiều locale, có thể coi chữ có/không dấu và hoa/thường là tương đương |
| `Collator.SECONDARY` | Xét thêm khác biệt thứ cấp | Thường thêm sự khác biệt dấu hoặc biến thể chữ |
| `Collator.TERTIARY` | Xét thêm khác biệt thứ ba | Thường thêm khác biệt hoa/thường và các biến thể khác |
| `Collator.IDENTICAL` | Mức nhận biết khác biệt tinh hơn | Hữu ích khi cần phân biệt ở mức gần với dạng biểu diễn, chịu ảnh hưởng chế độ decomposition |

**Lưu ý:** Đây là mô tả định hướng; cách một dấu, biến thể chữ hoặc quy tắc chữ ghép được xét cụ thể còn phụ thuộc **`Locale` và dữ liệu collation**. Chẳng hạn không nên khẳng định mọi locale đều bỏ qua mọi dấu tại `PRIMARY`. Nếu cần lọc “không dấu” hoặc tìm kiếm tiếng Việt theo quy tắc đặc biệt, phải kiểm thử đúng tập dữ liệu.

### Ví dụ: chữ hoa/thường và dấu

Lưu tệp `CollatorStrengthDemo.java`:

```java
import java.text.Collator;
import java.util.Locale;

public class CollatorStrengthDemo {
    public static void main(String[] args) {
        Collator collator = Collator.getInstance(Locale.US);

        collator.setStrength(Collator.PRIMARY);
        System.out.println(collator.compare(
                "resume", "r\u00E9sum\u00E9") == 0);
        System.out.println(collator.compare(
                "Java", "java") == 0);

        collator.setStrength(Collator.SECONDARY);
        System.out.println(collator.compare(
                "resume", "r\u00E9sum\u00E9") == 0);
        System.out.println(collator.compare(
                "Java", "java") == 0);

        collator.setStrength(Collator.TERTIARY);
        System.out.println(collator.compare(
                "Java", "java") == 0);
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 CollatorStrengthDemo.java
java CollatorStrengthDemo
```

Kết quả với dữ liệu locale đã kiểm tra:

```text
true
true
false
true
false
```

Giải thích:

1. Ở `PRIMARY` với `Locale.US`, `resume` và `résumé` được xem là tương đương; `Java` và `java` cũng tương đương.
2. Ở `SECONDARY`, sự khác biệt dấu sắc khiến `resume` và `résumé` không còn tương đương; khác biệt chữ hoa/thường giữa `Java` và `java` vẫn được bỏ qua trong ví dụ.
3. Ở `TERTIARY`, `Java` và `java` không còn tương đương trong ví dụ.

Vì `setStrength()` thay đổi chính sách so sánh, **không thay đổi cấu hình collator trong lúc một bộ sưu tập đã được sắp xếp hoặc đánh chỉ mục bằng collator đó đang hoạt động**; thứ tự so sánh phải nhất quán trong suốt thao tác.

### `compare(a, b) == 0` khác `a.equals(b)`

`String.equals()` chỉ trả `true` khi nội dung chuỗi giống nhau theo quy tắc của `String`. `Collator.compare(a, b) == 0` chỉ cho biết hai chuỗi không khác nhau **theo strength/locale/decomposition hiện tại**. Với strength thấp, các chuỗi khác nhau có thể được coi là tương đương.

Điều này còn ảnh hưởng tới `TreeSet` hoặc `TreeMap`: nếu dùng collator làm comparator, hai khóa có `compare() == 0` được bộ sưu tập xem như cùng một khóa về thứ tự, dù `String.equals()` trả `false`. Không dùng collator strength thấp để lưu tập khóa cần phân biệt chính xác từng ký tự.

## 4. `setDecomposition()`: chữ dựng sẵn và dấu kết hợp

Unicode cho phép một số văn bản được biểu diễn bằng nhiều dãy code point khác nhau nhưng **tương đương chuẩn (canonical equivalence)**:

- `"\u00E9"`: chữ `é` dựng sẵn (U+00E9).
- `"e\u0301"`: chữ `e` và dấu sắc kết hợp U+0301.

`String.equals()` so nội dung code unit nên thường trả `false` cho hai biểu diễn trên. Trong khi đó, bài toán so sánh ngôn ngữ có thể muốn coi chúng tương đương.

`Collator` có chế độ decomposition:

- `Collator.NO_DECOMPOSITION`: không yêu cầu bước decomposition chuẩn trước khi so sánh; thường tiết kiệm xử lý hơn nhưng cần thận trọng với các dạng Unicode tương đương.
- `Collator.CANONICAL_DECOMPOSITION`: xét sự phân rã tương đương chuẩn; thích hợp khi cần xử lý các chuỗi chữ dựng sẵn và tổ hợp dấu một cách nhất quán.
- `Collator.FULL_DECOMPOSITION`: phân rã tương thích rộng hơn, có thể coi thêm những biểu diễn khác là tương đương theo collation; **không tự chọn mức này nếu nghiệp vụ yêu cầu phân biệt các ký tự tương thích**.

### Ví dụ hoàn chỉnh về dạng Unicode

Lưu tệp `CanonicalDemo.java`:

```java
import java.text.Collator;
import java.util.Locale;

public class CanonicalDemo {
    public static void main(String[] args) {
        String composed = "Caf\u00E9";
        String decomposed = "Cafe\u0301";

        Collator collator = Collator.getInstance(Locale.US);
        collator.setStrength(Collator.TERTIARY);
        collator.setDecomposition(
                Collator.CANONICAL_DECOMPOSITION);

        System.out.println(composed.equals(decomposed));
        System.out.println(
                collator.compare(composed, decomposed) == 0);
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 CanonicalDemo.java
java CanonicalDemo
```

Kết quả:

```text
false
true
```

Cả hai chuỗi hiển thị tương tự `Café`. Tuy nhiên, `equals()` so dãy mã khác nhau; `Collator` với decomposition chuẩn xem chúng tương đương trong cấu hình ví dụ.

**Phân biệt:** `Normalizer.normalize(text, Normalizer.Form.NFC)` là thao tác *biến đổi nội dung* về dạng chuẩn. Ngược lại, `collator.compare(...)` là thao tác *so sánh* theo quy tắc; nó không thay thế hoặc chỉnh sửa hai `String` đầu vào.

## 5. Dùng `Collator` để sắp xếp danh sách

`Collator` triển khai `Comparator`, có thể truyền trực tiếp cho `List.sort(...)` hoặc `Collections.sort(...)`. Ví dụ độc lập `CollatorSortDemo.java`:

```java
import java.text.CollationKey;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

public class CollatorSortDemo {
    public static void main(String[] args) {
        Collator collator = Collator.getInstance(Locale.US);

        List<String> names = new ArrayList<>(
                List.of("Zoe", "Alice", "Bob"));
        names.sort(collator);
        System.out.println(names);

        List<String> fromKeys = names.stream()
                .map(collator::getCollationKey)
                .sorted()
                .map(CollationKey::getSourceString)
                .toList();
        System.out.println(fromKeys);

        collator.setStrength(Collator.PRIMARY);
        TreeSet<String> unique = new TreeSet<>(collator);
        unique.add("resume");
        unique.add("r\u00E9sum\u00E9");
        System.out.println(unique.size());
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 CollatorSortDemo.java
java CollatorSortDemo
```

Kết quả với cấu hình `Locale.US` đã kiểm tra:

```text
[Alice, Bob, Zoe]
[Alice, Bob, Zoe]
1
```

Hai dòng đầu minh họa sắp xếp trực tiếp bằng `Collator` và dùng khóa `CollationKey`. Dòng cuối cho thấy `TreeSet` tại `PRIMARY` chỉ giữ **một khóa theo quan hệ so sánh**, vì `resume` và `résumé` được xem là tương đương theo collator. Nó **không** chứng minh hai `String` có nội dung như nhau theo `equals()`.

### Khi nào dùng `CollationKey`?

`CollationKey` lưu khóa so sánh rút ra từ `Collator`. Với một tập chuỗi phải so sánh nhiều lần (chẳng hạn sắp xếp lặp lại cùng dữ liệu), tạo khóa một lần có thể giảm công việc phân tích từng chuỗi mỗi lần so sánh.

Quy tắc sử dụng:

1. Tạo các khóa bằng **cùng collator với cùng cấu hình `Locale`, strength và decomposition**.
2. So sánh/sắp xếp các khóa bằng `CollationKey.compareTo(...)`.
3. Dùng `getSourceString()` để lấy lại chuỗi ban đầu nếu cần.
4. Cân nhắc chi phí tạo/lưu khóa so với việc chỉ so sánh vài chuỗi; không mặc định dùng `CollationKey` cho mọi trường hợp.

Không so sánh khóa được tạo bằng các collator có quy tắc khác nhau, vì thứ tự của chúng có thể không tương thích. Khi thay đổi strength/decomposition, hãy **tạo lại khóa** nếu cần tiếp tục so sánh nhất quán.

## 6. Các quy tắc an toàn và giới hạn thực tế

### 6.1. Locale cố định hay locale của người dùng?

- **Sắp xếp dữ liệu để hiển thị:** chọn `Locale` ứng với ngôn ngữ/môi trường mà người dùng muốn xem. Ví dụ `Locale.forLanguageTag("vi-VN")` khi yêu cầu sắp xếp tên tiếng Việt.
- **Cần kết quả đồng nhất giữa máy chủ:** cố định locale, phiên bản JDK, dữ liệu collation và cấu hình; ghi nhận thay đổi khi nâng cấp runtime. Ngay cả cùng thẻ locale, dữ liệu collation của các hệ thống khác nhau có thể khác.
- **Không dùng mặc định một cách vô thức:** `Collator.getInstance()` dùng locale mặc định của máy chạy, nên kết quả có thể phụ thuộc môi trường. Khi quy tắc nghiệp vụ đã xác định, truyền `Locale` tường minh.

### 6.2. Không dùng collation ngôn ngữ để xác minh danh tính hoặc bảo mật

Các mức strength có thể xem hai chuỗi khác nhau là tương đương; điều này hữu ích cho **sắp xếp hiển thị** nhưng có thể không phù hợp cho:

- Token, API key, mã phiên, mật khẩu.
- Mã định danh phân biệt hoa/thường.
- Khóa phải so chính xác theo byte/code unit.
- Quy tắc phân quyền hoặc chữ ký dữ liệu.

Với những trường hợp trên, áp dụng phép so sánh đúng theo đặc tả của dữ liệu. `String.equals()` so nội dung chính xác của `String`, nhưng với bí mật nhạy cảm có thể cần cơ chế so sánh thời gian ổn định và bảo vệ dữ liệu chuyên biệt theo giao thức.

### 6.3. `Collator` có trạng thái cấu hình

`setStrength()` và `setDecomposition()` thay đổi cách đối tượng so sánh. Nếu nhiều luồng dùng chung và một luồng thay đổi cấu hình trong khi luồng khác đang so sánh, kết quả có thể không nhất quán. Cách đơn giản là tạo/cấu hình collator riêng cho mỗi công việc hoặc mỗi luồng, hoặc quản lý đồng bộ hóa phù hợp.

`Collator` hỗ trợ `clone()` để tạo bản sao có thể cấu hình độc lập trong những trường hợp cần tránh sửa trực tiếp một đối tượng dùng chung. Đừng thay đổi cấu hình comparator trong lúc đang sắp xếp hoặc lưu phần tử trong bộ sưu tập dựa trên comparator đó.

### 6.4. Chỉ so sánh chuỗi có dữ liệu hợp lệ

Ví dụ API `compare(String, String)` thông thường yêu cầu cả hai chuỗi không `null`. Nếu muốn sắp xếp danh sách có thể chứa null, quyết định quy tắc rõ ràng như `Comparator.nullsFirst(collator)` hoặc `Comparator.nullsLast(collator)`. Đây là chính sách của ứng dụng; bản thân collation không định nghĩa null là một từ hoặc ký tự.

Với Unicode, việc dùng decomposition/strength phù hợp giúp so sánh chữ có dấu, nhưng **không tự thay thế** mọi bài toán tách từ, tìm kiếm gần đúng, bỏ dấu, phiên âm hoặc hỗ trợ quy tắc riêng của sản phẩm.

## 7. Lỗi phổ biến cần tránh

1. **Dùng `String.compareTo()` cho danh sách tên người rồi xem đó là thứ tự bảng chữ cái chuẩn mọi ngôn ngữ.** Nó so theo UTF-16 code unit, không dùng quy tắc collation địa phương.
2. **Gọi `Collator.getInstance()` mà không chủ động chọn `Locale`.** Dùng locale mặc định chỉ khi đó là yêu cầu thực tế.
3. **Tin rằng `compare()` luôn trả chính xác `-1`, `0` hoặc `1`.** Chỉ kiểm tra dấu âm/0/dương; `Integer.signum` khi cần chuẩn hóa.
4. **Cho rằng `compare(a, b) == 0` đồng nghĩa `a.equals(b)`.** Sự tương đương theo strength có thể rộng hơn sự bằng nhau về nội dung.
5. **Dùng `PRIMARY` cho `TreeSet` khóa định danh** mà không lường trước việc khóa khác nội dung có thể bị gộp thành một phần tử.
6. **Hiểu `PRIMARY`/`SECONDARY`/`TERTIARY` như quy tắc bỏ dấu giống nhau ở mọi locale.** Quy tắc phụ thuộc ngôn ngữ và provider.
7. **Quên normalization/decomposition** khi dữ liệu có cả chữ dựng sẵn lẫn chữ + dấu kết hợp.
8. **Thay đổi strength sau khi đã tạo `CollationKey` hoặc đưa comparator vào cấu trúc sắp xếp** mà không tái lập các khóa/cấu trúc tương ứng.
9. **Dùng khóa collation khác cấu hình để so sánh.** Các khóa chỉ có nghĩa nhất quán khi xuất phát từ cùng bộ quy tắc.
10. **Dùng thứ tự collation để thay cho phân tích Unicode, tìm từ, search ranking hoặc phân quyền.** `Collator` giải quyết bài toán so sánh/sắp xếp theo ngôn ngữ, không bao trùm những chức năng khác.

## 8. Tự kiểm tra hiểu biết

- **Câu 1:** Muốn hiển thị danh sách tên tiếng Việt theo quy tắc ngôn ngữ, nên dùng gì?  
  **Đáp án:** `Collator.getInstance(Locale.forLanguageTag("vi-VN"))` rồi dùng làm comparator; kiểm tra quy tắc và dữ liệu JDK của môi trường triển khai.
- **Câu 2:** `collator.compare(a, b) < 0` nghĩa là gì?  
  **Đáp án:** `a` đứng trước `b` theo cấu hình collator hiện tại.
- **Câu 3:** `PRIMARY` có bảo đảm mọi dấu ở mọi ngôn ngữ đều bị bỏ qua?  
  **Đáp án:** Không. Hiệu lực từng dấu phụ thuộc locale và dữ liệu collation.
- **Câu 4:** Hai chuỗi khác `String.equals()` có thể bằng nhau theo `Collator.compare()` không?  
  **Đáp án:** Có, nếu quy tắc strength/decomposition coi chúng tương đương.
- **Câu 5:** Khi nào nên tạo `CollationKey`?  
  **Đáp án:** Khi so sánh lặp lại cùng tập chuỗi và lợi ích lớn hơn chi phí lưu/tạo khóa; các khóa cần chung cấu hình.
- **Câu 6:** Nên dùng `Collator` để xác minh API key không?  
  **Đáp án:** Không. Hãy dùng quy tắc so khớp chính xác và cơ chế bảo mật phù hợp với giao thức.

**Ghi nhớ:** `Collator` phục vụ **so sánh và sắp xếp theo `Locale`**. Chọn strength/decomposition dựa trên cách ứng dụng muốn xem những khác biệt chữ, dấu và biểu diễn Unicode; kiểm tra `compare` theo dấu âm/0/dương; và tách biệt **tương đương ngôn ngữ** khỏi **bằng nhau chính xác** của chuỗi hoặc khóa nghiệp vụ.


</details>

- [Quay lại đầu trang](#back-to-top)
