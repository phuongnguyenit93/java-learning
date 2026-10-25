<a id="back-to-top"></a>

# Định dạng chuỗi

## Menu
- [String.format, String.formatted và Formatter](#string-formatting-001)
- [Cú pháp chỉ số đối số, cờ, độ rộng, độ chính xác và kiểu chuyển](#string-formatting-002)
- [Các kiểu %s, %c, %b, %d, %f và %e](#string-formatting-003)
- [Căn lề, đệm ký tự và phân nhóm chữ số](#string-formatting-004)
- [%% cho dấu phần trăm và %n cho kết thúc dòng](#string-formatting-005)
- [Locale mặc định so với String.format(Locale,...)](#string-formatting-006)
- [Đối số null và các IllegalFormatException thường gặp](#string-formatting-007)
- [Phân biệt độ chính xác khi trình bày với giá trị số gốc](#string-formatting-008)
- [MessageFormat khi mẫu cần đổi thứ tự theo ngôn ngữ](#string-formatting-009)

## <a id="string-formatting-001">String.format, String.formatted và Formatter</a>

<details>
<summary>Xem chi tiết</summary>

# String.format, String.formatted và Formatter trong Java

## 1. Vì sao cần định dạng chuỗi?

Khi tạo thông báo, báo cáo, log hay dòng dữ liệu trình bày cho người đọc, ta thường muốn ghép **văn bản cố định** với những giá trị có kiểu khác nhau: chuỗi, số nguyên, số thực, ngày giờ. Nối nhiều đoạn bằng toán tử `+` có thể khiến mã dài và khó căn chỉnh. Java cung cấp ba API cùng dựa trên **cú pháp format string**:

- `String.format(...)`: tạo một `String` mới từ mẫu định dạng và các đối số.
- `String.formatted(...)`: gọi mẫu định dạng như một phương thức trên chính `String`, từ Java 15.
- `java.util.Formatter`: đối tượng định dạng có trạng thái, có thể ghi nhiều lần vào một đích như `StringBuilder` hoặc `Appendable`.

Mục tiêu của bài: đọc đúng ký hiệu `%...`, kiểm soát độ rộng và số chữ số thập phân, chọn đúng `Locale`, phân biệt ba API, biết cách xử lý lỗi format và chọn công cụ phù hợp.

## 2. String.format: định dạng một lần thành chuỗi

Hai overload quan trọng:

```java
public static String format(String format, Object... args);

public static String format(Locale l, String format, Object... args);
```

Đối số đầu là **mẫu** chứa văn bản cố định và các *format specifier* (ký hiệu bắt đầu bằng `%`). Các đối số tiếp theo cung cấp giá trị thay vào những vị trí đó.

```java
import java.util.Locale;

String text = String.format(
        Locale.US,
        "Tên: %s | Tuổi: %d | Điểm: %.2f",
        "An", 20, 8.75
);
// "Tên: An | Tuổi: 20 | Điểm: 8.75"
```

Ở ví dụ này: `%s` biểu diễn chuỗi, `%d` biểu diễn số nguyên dạng thập phân, `%.2f` hiển thị số thực với **2 chữ số sau dấu thập phân**. `Locale.US` giúp cách in dấu thập phân ổn định, ngay cả khi máy chạy sử dụng locale khác.

Nếu không truyền `Locale`, `String.format(String, Object...)` sử dụng **default FORMAT locale** của JVM (`Locale.getDefault(Locale.Category.FORMAT)`). Vì vậy cùng một mẫu định dạng số có thể tạo dấu phân cách khác nhau trên hai máy. Khi kết quả cần nhất quán trong tài liệu, bài kiểm thử hoặc giao tiếp giữa hệ thống, hãy truyền locale tường minh.

`String.format` trả chuỗi mới, không thay đổi biến mẫu, đối số hay `String` nguồn.

## 3. String.formatted: viết gọn từ mẫu chuỗi

Từ Java 15, `String` có phương thức thể hiện trực tiếp quan hệ “mẫu này được định dạng bằng các giá trị sau”:

```java
public String formatted(Object... args);
```

Ví dụ:

```java
String title = "Chào %s, bạn có %d thông báo."
        .formatted("Linh", 3);

// "Chào Linh, bạn có 3 thông báo."
```

So sánh hai cách có cùng mục đích:

```java
String a = String.format("Mã: %s, số lượng: %d", "P01", 7);
String b = "Mã: %s, số lượng: %d".formatted("P01", 7);
```

Trong cùng môi trường locale, `a` và `b` có cùng nội dung. `formatted(...)` thuận tiện khi mẫu chuỗi đã có sẵn dưới dạng biểu thức `String`, ví dụ một text block nhiều dòng. Nó dùng các quy tắc định dạng của `Formatter`, tương tự `String.format`.

**Khác biệt cần nhớ:** `String.formatted(...)` **không có overload nhận `Locale`**. Khi cần định dạng số/ngày theo locale cụ thể, chọn `String.format(locale, mẫu, đốiSố...)` hoặc `Formatter` được cấu hình locale. Cả `format` lẫn `formatted` đều tạo kết quả `String` mới.

## 4. Cấu trúc format specifier: đủ để bắt đầu

Ba API cùng sử dụng cú pháp của `Formatter`: `%[argument_index$][flags][width][.precision]conversion`. Dấu ngoặc vuông là ký hiệu mô tả thành phần tùy chọn, không phải ký tự gõ vào mẫu. Chỉ số đối số bắt đầu từ **1**; `conversion` quyết định loại giá trị, chẳng hạn `%s` (chuỗi), `%d` (số nguyên), `%f` (số thực), `%tF` (ngày). `%%` in dấu phần trăm và `%n` chèn kết thúc dòng theo nền tảng; hai ký hiệu này không tiêu thụ đối số.

```java
String line = String.format(Locale.US, "%s: %d mục, tổng %.2f", "Giỏ", 3, 12.5);
// "Giỏ: 3 mục, tổng 12.50"
```

Đây là lớp cú pháp chung giúp bạn hiểu cả ba API. Việc phân tích đầy đủ các tổ hợp chỉ số, flags và conversion là trọng tâm của các bài chuyên sâu tiếp theo trong chương.

## 5. Width, precision và flags: những khác biệt thiết yếu

`width` là **độ rộng tối thiểu** của trường xuất, không tự cắt văn bản. Dấu `-` căn trái; flag `0` đệm số 0 cho kiểu số phù hợp. `precision` phụ thuộc conversion: `%.2f` in hai chữ số thập phân, còn `%.3s` giới hạn chuỗi còn tối đa ba ký tự.

```java
String s = String.format(Locale.US, "|%-6s|%04d|%.2f|", "AB", 7, 2.5);
// "|AB    |0007|2.50|"
```

Không phải flag hay precision nào cũng tương thích với mọi conversion: ví dụ `%.2d` và `%05s` không hợp lệ. Định dạng chỉ thay đổi văn bản đầu ra, không làm tròn hoặc sửa biến dữ liệu gốc.

## 6. Chọn lại hoặc đổi thứ tự đối số

Thông thường `%s`, `%d` nhận đối số lần lượt từ trái sang phải. `%2$s` chọn đối số thứ hai, còn `%1$d` chọn đối số thứ nhất; flag `<` tái sử dụng đối số của specifier có đối số trước đó.

```java
String reordered = String.format("%2$s mua %1$d món", 3, "Lan");
// "Lan mua 3 món"
String reused = String.format("%s -> %<S", "java");
// "java -> JAVA"
```

Việc chỉ định đối số hữu ích khi câu thông báo đổi trật tự từ. Với bản địa hóa câu hoàn chỉnh theo ngôn ngữ, chương này còn giới thiệu `MessageFormat` và `ResourceBundle`.

## 7. Locale thuộc về kết quả, không thuộc về mẫu

`String.format(format, args)` và `String.formatted(args)` dùng locale mặc định cho định dạng (`Locale.Category.FORMAT`). `String.format(locale, format, args)` cho phép chọn rõ quy ước dấu thập phân, phân nhóm số hoặc chữ hoa; bản thân mẫu văn bản cố định không được dịch tự động.

```java
String us = String.format(Locale.US, "%,.2f", 12345.5);       // 12,345.50
String de = String.format(Locale.GERMANY, "%,.2f", 12345.5);  // 12.345,50
```

Chọn locale theo người đọc; khi cần dữ liệu cho máy, tuân thủ chuẩn serialization của giao thức. Không thay đổi `Locale.setDefault` theo từng request trong ứng dụng đa luồng.

## 8. Formatter: định dạng nhiều lần vào cùng đích

`Formatter` thuộc `java.util`, triển khai `Closeable` và `Flushable`. Nó có các lời gọi `format(...)` tương tự, nhưng ghi vào **đích đầu ra** được gắn với đối tượng. Đích có thể là `StringBuilder`, `Writer`, `OutputStream` hoặc các đối tượng hỗ trợ cách thức ghi tương thích, tùy constructor.

Ví dụ ghi tuần tự vào `StringBuilder`:

```java
import java.util.Formatter;
import java.util.Locale;

StringBuilder builder = new StringBuilder();

try (Formatter formatter = new Formatter(builder, Locale.US)) {
    formatter.format("Mã: %04d; ", 7);
    formatter.format("Giá: %,.2f", 1234.5);
}

System.out.println(builder);
// Mã: 0007; Giá: 1,234.50
```

`format(...)` trên `Formatter` trả chính `Formatter` nên có thể gọi nối tiếp. Trong ví dụ, cả hai lời gọi ghi vào cùng `builder`; `StringBuilder` vẫn đọc được sau khi đóng `Formatter`. Dùng `try-with-resources` để đóng `Formatter` khi không dùng nữa, nhất là khi đích là tài nguyên I/O cần quản lý.

Cần lưu ý **đóng `Formatter` có thể đóng luôn đích đầu ra** nếu đích cũng là `Closeable`. Không tự tạo một `Formatter` rồi đóng nó trên `System.out` khi bạn vẫn cần tiếp tục ghi ra console. Có thể dùng `System.out.printf(...)` hoặc `System.out.format(...)` để ghi trực tiếp vào luồng chuẩn trong trường hợp đơn giản.

`Formatter` cũng có `locale()` để biết locale liên kết và `ioException()` để kiểm tra lỗi `IOException` mà formatter ghi nhận khi thao tác với đích đầu ra. Khi ghi tệp hoặc luồng, đừng chỉ kiểm tra chuỗi kết quả mà bỏ qua trạng thái I/O. Một đối tượng `Formatter` dùng chung cũng không nên được giả định là an toàn khi nhiều luồng cùng gọi; hãy tự kiểm soát truy cập đồng thời nếu cần.

## 9. Chương trình Java 17+ chạy độc lập

Tạo tệp tên `FormattingDemo.java` với toàn bộ nội dung:

```java
import java.time.LocalDate;
import java.util.Formatter;
import java.util.Locale;

public class FormattingDemo {
    public static void main(String[] args) {
        Locale us = Locale.US;

        String product = String.format(
                us, "Sản phẩm: %s | SL: %d | Giá: %,.2f",
                "Bút", 3, 1234.5);
        System.out.println(product);

        String greeting = "Xin chào %s (%d tuổi)"
                .formatted("Mai", 21);
        System.out.println(greeting);

        System.out.println(
                String.format(us, "|%8s|%-8s|", "cat", "dog"));

        System.out.println(
                String.format(us,
                        "%2$s mua %1$d món; %2$s đã thanh toán.",
                        3, "Lan"));

        System.out.println(
                String.format(us,
                        "Ngày: %tF", LocalDate.of(2026, 10, 23)));

        System.out.println(
                String.format(us, "US: %,.2f", 12345.5));
        System.out.println(
                String.format(Locale.GERMANY,
                        "DE: %,.2f", 12345.5));

        StringBuilder builder = new StringBuilder();
        try (Formatter formatter = new Formatter(builder, us)) {
            formatter.format("A=%04d; ", 7);
            formatter.format("B=%.1f", 2.5);
        }
        System.out.println(builder);
    }
}
```

Biên dịch và chạy (đảm bảo tệp nguồn được lưu dưới UTF-8):

```bash
javac -encoding UTF-8 FormattingDemo.java
java FormattingDemo
```

Kết quả stdout chính xác:

```text
Sản phẩm: Bút | SL: 3 | Giá: 1,234.50
Xin chào Mai (21 tuổi)
|     cat|dog     |
Lan mua 3 món; Lan đã thanh toán.
Ngày: 2026-10-23
US: 12,345.50
DE: 12.345,50
A=0007; B=2.5
```

Các dòng minh họa lần lượt `String.format`, `String.formatted`, căn lề, chỉ số đối số, định dạng ngày, locale và cách dùng lại `Formatter`. Ví dụ chọn ngày xác định và `Locale` tường minh ở các chuyển đổi cần locale để kết quả dễ kiểm tra.

## 10. Ngày giờ trong Formatter

`%tF` định dạng ngày theo `yyyy-MM-dd`; `%tT` định dạng giờ theo `HH:mm:ss`, nếu đối số thực sự cung cấp các trường thời gian cần thiết. Ví dụ, `String.format(Locale.US, "%tF", LocalDate.of(2026, 10, 23))` cho `2026-10-23` (cần import `LocalDate`). `LocalDate` không có giờ để dùng với `%tT`. Các yêu cầu parse, múi giờ hoặc pattern ngày giờ phong phú nên dùng `DateTimeFormatter`.

## 11. Lỗi đầu vào và ngoại lệ định dạng

Compiler thường không kiểm tra việc khớp giữa conversion và đối số. Ví dụ `%d` với chuỗi `"42"` gây `IllegalFormatConversionException`, thiếu đối số gây `MissingFormatArgumentException`, flag/precision sai có thể gây các lớp khác của `IllegalFormatException`. Đối số `(Object) null` với `%s` hiển thị `"null"`, còn `%b` với chuỗi `"false"` lại cho `"true"` vì đó là đối tượng không null.

Với văn bản từ người dùng chứa `%`, hãy đưa văn bản đó vào đối số `%s` thay vì coi nó là format string. Chọn `%%` khi muốn in dấu phần trăm ngay trong mẫu; dùng `%n` cho kết thúc dòng theo nền tảng, hoặc `\n` khi giao thức yêu cầu LF cố định. Khi đóng `Formatter` gắn với đích I/O, nhớ kiểm tra và quản lý vòng đời của đích như ở mục 8.

## 12. Chọn API phù hợp

| Nhu cầu | Gợi ý | Lý do |
| --- | --- | --- |
| Một câu thông báo, chủ động chỉ định locale | `String.format(Locale, ...)` | Mẫu, đối số và locale rõ ràng trong một lệnh |
| Một mẫu `String` ngắn, dùng locale mặc định | `template.formatted(args)` | Đọc thuận khi mẫu là receiver |
| Xây dựng dần nhiều đoạn định dạng | `Formatter` + `StringBuilder` | Có thể gọi `format` nhiều lần vào cùng đích |
| In trực tiếp ra stdout | `System.out.printf(...)` hoặc `format(...)` | Không cần tạo chuỗi trung gian nếu chỉ cần in |
| Ngày giờ phức tạp | `DateTimeFormatter` | Kiểm soát pattern, locale và các kiểu thời gian |
| Số theo quy tắc tiền tệ/hiển thị chuyên biệt | `NumberFormat` hoặc `DecimalFormat` phù hợp | Có quy tắc biểu diễn số và tiền tệ chuyên dụng |

Các công cụ không loại trừ nhau. Ví dụ một chương trình có thể dùng `DateTimeFormatter` tạo phần ngày theo quy tắc riêng, sau đó đưa chuỗi đó vào `String.format` cùng những trường khác.

## 13. Bài tập tự luyện: chọn đúng API

**Bài 1.** Cần tạo một chuỗi báo cáo với dấu phân cách số ổn định ở Mỹ. Chọn `String.format(Locale.US, "Tổng: %,.2f", 1234.5)` để nhận `"Tổng: 1,234.50"`.

**Bài 2.** Mẫu đã nằm trong biến `String template = "Xin chào %s"`, chỉ cần locale mặc định. Dùng `template.formatted("Mai")`, kết quả `"Xin chào Mai"`. Nếu cần locale cụ thể, chuyển sang `String.format(locale, template, "Mai")`.

**Bài 3.** Cần ghi nhiều lần vào một `StringBuilder` mà không tạo nhiều chuỗi trung gian: tạo `Formatter(builder, locale)` và gọi `format(...)` liên tiếp. Sau đó đóng formatter; nếu đích là `Closeable`, việc đóng formatter có thể đóng luôn đích, nên quản lý tài nguyên có chủ đích.

## 14. Ghi nhớ

1. `String.format` là phương thức **tĩnh**, phù hợp cho một lần định dạng và có overload nhận `Locale`.
2. `String.formatted` là phương thức **instance** từ Java 15, ngắn gọn khi mẫu định dạng đã là `String`; không có overload `Locale`.
3. `Formatter` là đối tượng có thể **ghi nhiều lần vào cùng một đích** và cần chú ý vòng đời tài nguyên, đặc biệt khi đích là luồng I/O.
4. Format specifier có thứ tự `%[index$][flags][width][.precision]conversion`; **width** và **precision** có ý nghĩa khác nhau.
5. Chọn đúng conversion cho **kiểu dữ liệu thật**; sai kiểu/thiếu đối số/flag không hợp lệ có thể gây ngoại lệ runtime.
6. Với kết quả cần nhất quán, chỉ định `Locale` tường minh. Định dạng để trình bày không thay thế thao tác parse, tính toán chính xác hoặc serialization.

Tài liệu tham khảo chính thức: [Java SE 17 — String](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html), [Java SE 17 — Formatter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Formatter.html), [Java SE 17 — Locale](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Locale.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-formatting-002">Cú pháp chỉ số đối số, cờ, độ rộng, độ chính xác và kiểu chuyển</a>

<details>
<summary>Xem chi tiết</summary>

# Cú pháp chỉ số đối số, cờ, độ rộng, độ chính xác và kiểu chuyển trong Java

Khi dùng `String.format(...)`, `System.out.printf(...)` hoặc `Formatter`, ta mô tả hình thức của dữ liệu bằng **format string**. Một ký hiệu bắt đầu bằng `%` được gọi là *format specifier*: nó chọn đối số, quyết định cách căn lề/điền ký tự, giới hạn độ chính xác và chọn kiểu hiển thị. Hiểu đúng thứ tự các thành phần giúp tạo bảng, thông báo, số tiền, ngày tháng và báo cáo có kết quả nhất quán.

Bài học tập trung vào **cú pháp của một format specifier trong Java 17+**, đặc biệt là chỉ số đối số (`argument index`), cờ (`flags`), độ rộng (`width`), độ chính xác (`precision`), và kiểu chuyển (`conversion`). Các quy tắc này thuộc `java.util.Formatter`; `String.format` và `printf` sử dụng cùng hệ cú pháp.

## 1. Bắt đầu với một chuỗi định dạng

```java
String message = String.format("Hello %s, you have %d messages", "An", 3);
// "Hello An, you have 3 messages"
```

- `Hello `, dấu phẩy và ` you have ` là văn bản giữ nguyên.
- `%s` lấy đối số tương ứng và hiển thị dưới dạng chuỗi.
- `%d` lấy đối số số nguyên và hiển thị dạng thập phân.
- `String.format(...)` **trả về** một `String`; `System.out.printf(...)` **ghi** văn bản ra luồng đầu ra (và trả về `PrintStream`).

Ví dụ `String.format(Locale.US, "%,.2f", 12345.5)` trả về `"12,345.50"`. `Locale.US` được chỉ định chủ động để cố định dấu thập phân và dấu phân nhóm trong kết quả.

## 2. Công thức cú pháp đầy đủ

Với kiểu chuyển thông thường (chuỗi, ký tự, số nguyên và số thực), cú pháp là:

```text
%[argument_index$][flags][width][.precision]conversion
```

Dấu ngoặc vuông trong dòng **mô tả ngữ pháp** chỉ nghĩa là “thành phần tùy chọn”; **không gõ ngoặc vuông** vào format string thật. Những thành phần được viết theo đúng thứ tự sau:

| Thành phần | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| `%` | Bắt đầu một format specifier. | `%s` |
| `argument_index$` | Chọn vị trí đối số, tính từ **1**; có dấu `$`. | `%2$s` |
| `flags` | Các cờ điều khiển căn lề, dấu âm/dương, điền `0`, nhóm số... | `%-10s`, `%+d` |
| `width` | **Độ rộng tối thiểu** của trường đầu ra. | `%10s` |
| `.precision` | Độ chính xác hoặc giới hạn chiều dài, tùy kiểu chuyển; có dấu chấm. | `%.2f`, `%.4s` |
| `conversion` | Chỉ kiểu chuyển và quy tắc biểu diễn. | `s`, `d`, `f`, `x` |

**Đọc biểu thức `%2$-12.4s` từ trái sang phải:**

1. `%`: bắt đầu chỉ thị.
2. `2$`: lấy **đối số thứ hai**.
3. `-`: căn trái.
4. `12`: trường rộng **ít nhất 12** ký tự.
5. `.4`: với `s`, chỉ lấy **tối đa 4** ký tự của nội dung chuỗi.
6. `s`: chuyển đối số thành chuỗi.

Ví dụ `String.format("%2$-12.4s", 99, "Notebook")` tạo văn bản `"Note        "`: bốn ký tự `Note` theo sau bởi tám dấu cách. Nếu bỏ `-`, các dấu cách sẽ nằm **bên trái**.

Lưu ý có **hai nhánh cú pháp đặc biệt**:

- Ngày/giờ: `%[argument_index$][flags][width]tX` hoặc `...TX`, trong đó `t`/`T` được theo sau bởi **ký tự chỉ thành phần thời gian**, ví dụ `%1$tF`, `%1$tY`; không có `.precision`.
- Ký hiệu không nhận đối số: `%%` (một dấu phần trăm thật) và `%n` (ký tự xuống dòng theo nền tảng). Các chỉ thị này không tiêu thụ đối số dữ liệu.

Không phải flag, width hay precision nào cũng hợp lệ với mọi conversion; Java sẽ ném ngoại lệ nếu tổ hợp sai thay vì lặng lẽ bỏ qua.

## 3. Argument index: chọn và dùng lại đối số

### 3.1. Không ghi chỉ số: lấy đối số theo thứ tự

```java
String value = String.format("%s has %d points", "Mai", 42);
// "Mai has 42 points"
```

Trong ví dụ này, `%s` dùng đối số thứ nhất, `%d` dùng đối số thứ hai.

### 3.2. Chỉ số tường minh: `1$`, `2$`...

```java
String value = String.format("%2$d points belong to %1$s", "Mai", 42);
// "42 points belong to Mai"
```

`%2$d` lấy phần tử thứ hai của danh sách đối số và hiển thị số nguyên; `%1$s` lấy phần tử thứ nhất và hiển thị chuỗi. Chỉ số **bắt đầu từ 1**, không từ 0. Có thể tham chiếu một đối số nhiều lần:

```java
String value = String.format("%1$s / %1$S", "java");
// "java / JAVA"
```

`%S` dùng cách chuyển thành chữ hoa (theo locale). Đây là cách dùng cùng đối số hai lần mà vẫn định dạng khác nhau.

### 3.3. Cờ `<`: dùng lại đối số của specifier trước

```java
String value = String.format("%s -> %<S", "java");
// "java -> JAVA"
```

Ở `%<S`, `<` **không phải dấu so sánh**: đó là cờ “dùng lại đối số của specifier ngay trước có đối số”, còn `S` là conversion. Nếu dùng `%<s` mà chưa có đối số trước để tham chiếu, sẽ phát sinh `MissingFormatArgumentException`.

**Quy tắc dễ nhầm:** chỉ số tường minh (`%2$s`) và chỉ số tuần tự mặc định (`%s`) có cách theo dõi riêng; xen kẽ cả hai trong một mẫu dễ gây khó đọc. Ưu tiên dùng toàn bộ chỉ số tường minh khi cần đổi thứ tự, hoặc toàn bộ dạng tuần tự nếu không cần đổi thứ tự. Cờ `<` hữu ích để trình bày **cùng một đối số** theo nhiều cách, nhất là ngày tháng.

## 4. Flags: thay đổi cách hiển thị

Các cờ có thể xuất hiện liên tiếp **sau chỉ số đối số và trước width**. Chúng có ý nghĩa khác nhau tùy conversion.

| Flag | Tác dụng điển hình | Ví dụ và ý nghĩa |
| --- | --- | --- |
| `-` | Căn trái trong trường có width; mặc định thường căn phải. | `%-8s` |
| `+` | Luôn hiển thị dấu số: `+` hoặc `-`. | `%+d`, `%+.2f` |
| ` ` (dấu cách) | Dành vị trí trống trước số dương; với số âm vẫn có dấu trừ. | `% d` |
| `0` | Điền số `0` vào bên trái phần số để đủ width. | `%08d` |
| `,` | Nhóm chữ số theo locale, với các phép chuyển số thích hợp. | `%,d`, `%,.2f` |
| `(` | Bao số âm bằng dấu ngoặc đơn thay cho dấu trừ ở các kiểu số phù hợp. | `%(d`, `%(,.2f` |
| `#` | Dạng biểu diễn thay thế khi conversion hỗ trợ. | `%#x` tạo tiền tố `0x` |
| `<` | Dùng lại đối số trước. | `%s %<S` |

Với `Locale.US`:

```java
String a = String.format(Locale.US, "%08d", -42);      // "-0000042"
String b = String.format(Locale.US, "%+d", 42);        // "+42"
String c = String.format(Locale.US, "%,d", 1234567);  // "1,234,567"
String d = String.format(Locale.US, "%(,.2f", -1234.5);
// "(1,234.50)"
String e = String.format(Locale.US, "%#x", 255);      // "0xff"
```

**Cẩn thận với tổ hợp cờ:** `-` (căn trái) và `0` (đệm số 0) không dùng chung trong các conversion số có áp dụng hai cờ này; `+` và cờ dấu cách cũng xung đột. Với `%s` thông thường, các cờ số như `0` hoặc `,` không phù hợp. Một số flag chỉ áp dụng cho vài conversion: chẳng hạn `,` có thể dùng với `d` và nhiều dạng số thực, nhưng không dùng như cờ phân nhóm cho `x`.

## 5. Width: độ rộng **tối thiểu**

Với `%8s` và nội dung `"AB"`, kết quả có sáu dấu cách rồi đến `AB`. Với `%-8s`, kết quả là `AB` rồi sáu dấu cách:

```java
String right = String.format("|%8s|", "AB");  // "|      AB|"
String left  = String.format("|%-8s|", "AB"); // "|AB      |"
```

Width không làm ngắn một giá trị vốn dài hơn trường:

```java
String value = String.format("|%3s|", "Hello");
// "|Hello|" (không cắt xuống 3 ký tự)
```

**Width là số ký tự của trường kết quả, gồm cả dấu cộng/trừ, tiền tố số và dấu phân cách đã định dạng khi áp dụng.** Khi đệm `0` cho số âm, các chữ số 0 được chèn **sau dấu trừ**: `%08d` với `-42` cho `-0000042`.

Không nhầm `-` đứng trước width là số âm. Trong `%-10s`, dấu `-` là **flag căn trái**, còn `10` vẫn là width dương. Một số flag đòi hỏi width, ví dụ `%-s` không có độ rộng nên không hợp lệ.

## 6. Precision: không giống nhau giữa chuỗi và số

Precision được viết `.N`, với `N` là số nguyên không âm. Nó được áp dụng tùy conversion:

| Conversion | Ý nghĩa của precision | Ví dụ |
| --- | --- | --- |
| `s` / `S` (văn bản thông thường) | Số ký tự **tối đa** được hiển thị trước khi áp dụng width. | `%.3s` với `"abcdef"` → `"abc"` |
| `f` | Số chữ số sau dấu thập phân. | `%.2f` với `12.5` → `"12.50"` |
| `e` / `E` | Số chữ số sau dấu thập phân trong dạng khoa học. | `%.2e` |
| `g` / `G` | Số **chữ số có nghĩa** của trị số sau khi làm tròn. | `%.3g` |
| `d`, `x`, `c`, `tF` | **Không hỗ trợ precision**. | `%.2d` không hợp lệ |

Ví dụ kết hợp width và precision:

```java
String a = String.format(Locale.US, "|%8.3s|", "abcdef");
// "|     abc|" — cắt còn 3 ký tự rồi căn phải trong 8 vị trí

String b = String.format(Locale.US, "|%-8.3s|", "abcdef");
// "|abc     |" — cắt còn 3 ký tự rồi căn trái

String c = String.format(Locale.US, "|%8.2f|", 12.5);
// "|   12.50|" — làm tròn/hiển thị 2 chữ số sau dấu thập phân,
// rồi đệm khoảng trắng đến width 8
```

Với số thực nhị phân (`double`/`float`), kết quả làm tròn ở những giá trị ranh giới có thể chịu ảnh hưởng của cách lưu trữ số thực. Nếu là giá trị tiền tệ cần độ chính xác thập phân chặt chẽ, xem xét `BigDecimal` và quy tắc làm tròn nghiệp vụ; `%.2f` chỉ là cách **hiển thị** chứ không thay đổi dữ liệu gốc.

## 7. Conversion: chọn kiểu dữ liệu đầu ra

| Conversion | Loại giá trị thường dùng | Ví dụ/ý nghĩa |
| --- | --- | --- |
| `s` / `S` | Đối tượng bất kỳ, lấy biểu diễn chuỗi; `S` chuyển thành chữ hoa. | `%s`, `%S` |
| `b` / `B` | Boolean; `null` thành `false`, đối tượng khác `Boolean` nếu không null thành `true`. | `%b` |
| `h` / `H` | Dạng thập lục phân của hash code (không phải địa chỉ bộ nhớ). | `%h` |
| `c` / `C` | Ký tự Unicode hợp lệ (ví dụ `char`; hoặc code point trong `int`). | `%c` |
| `d` | Số nguyên thập phân. | `%d` |
| `o` | Số nguyên bát phân. | `%o` |
| `x` / `X` | Số nguyên thập lục phân, dùng chữ thường/chữ hoa. | `%x` |
| `f` | Số thực dạng thập phân cố định. | `%.2f` |
| `e` / `E` | Số thực dạng khoa học. | `%e` |
| `g` / `G` | Số thực dạng tổng quát theo số chữ số có nghĩa. | `%g` |
| `a` / `A` | Biểu diễn số thực thập lục phân (phù hợp `float`/`double`). | `%a` |
| `tX` / `TX` | Ngày giờ, **X là ký tự thành phần ngày/giờ**, ví dụ `F`, `Y`, `m`, `d`, `H`. | `%tF`, `%1$tY` |
| `%` trong `%%` | Dấu phần trăm thật; không nhận đối số. | `100%%` → `100%` |
| `n` trong `%n` | Xuống dòng theo hệ điều hành; không nhận đối số. | `Line 1%nLine 2` |

**Tương thích kiểu dữ liệu:** `%d` không nhận một `String` có nội dung `"42"` thay cho số nguyên; `%f` dành cho số thực như `double`, `float` và `BigDecimal`; `%c` không nhận một `String` nhiều ký tự thay cho một ký tự. Các phép chuyển không tương thích thường gây `IllegalFormatConversionException`.

Một số định dạng ngày thường dùng (với `LocalDate` hoặc giá trị thời gian tương thích `TemporalAccessor`):

- `%tF`: ngày ISO dạng `yyyy-MM-dd`.
- `%tY`: năm bốn chữ số; `%tm`: tháng hai chữ số; `%td`: ngày trong tháng hai chữ số.
- `%tH`: giờ (00–23); `%tM`: phút, **chỉ dùng với giá trị có trường giờ/phút**, chẳng hạn `LocalDateTime`.

Vì `tF` gồm **hai ký tự conversion** `t` và `F`, không được đọc `F` là một flag hay width. Để tái sử dụng cùng ngày:

```java
LocalDate date = LocalDate.of(2026, 10, 23);
String value = String.format(Locale.US,
        "%1$td/%1$tm/%1$tY", date);
// "23/10/2026"
```

Đây là đoạn minh họa, cần import `java.time.LocalDate` và `java.util.Locale`.

## 8. Ví dụ Java 17+ chạy độc lập

Lưu tệp tên `FormatSyntaxDemo.java` với mã:

```java
import java.time.LocalDate;
import java.util.Locale;

public class FormatSyntaxDemo {
    public static void main(String[] args) {
        String product = "Notebook";
        int quantity = 7;
        double price = 12.5;

        // Chỉ số đối số 1-based và đổi thứ tự.
        System.out.println(String.format(Locale.US,
                "Name: %1$s; qty: %2$d", product, quantity));
        System.out.println(String.format(Locale.US,
                "Reordered: %2$d / %1$s", product, quantity));

        // Căn trái, cắt chuỗi, căn phải, định dạng số thực.
        System.out.println(String.format(Locale.US,
                "|%1$-12.4s|", product));
        System.out.println(String.format(Locale.US,
                "|%1$10.2f|", price));

        // Kết hợp các cờ + và 0 với width, precision, conversion f.
        System.out.println(String.format(Locale.US,
                "%1$+010.2f", price));

        System.out.println(String.format(Locale.US,
                "%,d", 1234567));
        System.out.println(String.format(Locale.US,
                "%#x", 255));

        // < lặp lại đối số của specifier trước; S viết chữ hoa.
        System.out.println(String.format(Locale.US,
                "%1$s -> %<S", "java"));

        System.out.println(String.format(Locale.US,
                "%b", (Object) null));

        // Date/time conversion tF; không có precision.
        System.out.println(String.format(Locale.US,
                "%tF", LocalDate.of(2026, 10, 23)));

        // %% là phần trăm thực; %n là xuống dòng theo hệ thống.
        System.out.print(String.format(Locale.US,
                "100%% done%n"));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 FormatSyntaxDemo.java
java FormatSyntaxDemo
```

Đầu ra chính xác của chương trình:

```text
Name: Notebook; qty: 7
Reordered: 7 / Notebook
|Note        |
|     12.50|
+000012.50
1,234,567
0xff
java -> JAVA
false
2026-10-23
100% done
```

Những điểm cần quan sát: `%1$-12.4s` chọn đối số một, cắt `Notebook` còn `Note` rồi điền tám khoảng trắng bên phải để có width 12; `%1$10.2f` hiển thị `12.50` và thêm năm khoảng trắng bên trái; `%1$+010.2f` hiển thị dấu `+`, điền số 0 sau dấu và đạt tổng độ rộng 10. Các dấu cách bên trong cặp `|...|` ở stdout là một phần của kết quả, không phải lỗi trình bày.

## 9. Locale, các API liên quan và việc thoát ký tự

### 9.1. Locale quyết định một phần định dạng

```java
double value = 12345.5;
String us = String.format(Locale.US, "%,.2f", value);
// "12,345.50"
String de = String.format(Locale.GERMANY, "%,.2f", value);
// "12.345,50"
```

Khi không truyền `Locale`, định dạng dùng locale mặc định phục vụ việc format của môi trường chạy. Kết quả có thể khác nhau giữa máy phát triển và máy triển khai. Nếu đầu ra là dữ liệu giao tiếp giữa các hệ thống hoặc bài kiểm tra cần stdout xác định, truyền locale tường minh như `Locale.US`. Với giao diện người dùng, chọn locale theo ngôn ngữ/khu vực thích hợp.

### 9.2. `String.format`, `formatted`, `printf` và `Formatter`

- `String.format(Locale.US, "%d", 7)` tạo chuỗi `"7"`.
- `"%d".formatted(7)` cũng tạo chuỗi `"7"` (API có từ Java 15, dùng được ở Java 17+).
- `System.out.printf("%d%n", 7)` ghi trực tiếp ra màn hình.
- `Formatter` cho phép định dạng vào một `Appendable`, chẳng hạn `StringBuilder`, khi cần kiểm soát nơi nhận kết quả.

Cú pháp các specifier về cơ bản chung giữa các API trên; khác biệt chính là phương thức trả về giá trị chuỗi hay ghi ra đích.

### 9.3. Phân biệt dấu phần trăm trong format và dữ liệu

Trong chính **format string**, muốn in dấu phần trăm thật phải viết `%%`. Nhưng khi dữ liệu `"50%"` được truyền như đối số cho `%s`, **không phải nhân đôi** dấu phần trăm bên trong dữ liệu:

```java
String result = String.format("Progress: %s", "50%");
// "Progress: 50%"

String another = String.format("Progress: 50%%");
// "Progress: 50%"
```

Không lấy văn bản do người dùng cung cấp trực tiếp làm **format string** khi mục đích chỉ là hiển thị nguyên văn, vì ký hiệu `%` trong đó có thể bị phân tích như format specifier. Hãy dùng mẫu cố định như `"%s"` và truyền văn bản đó thành đối số.

## 10. Lỗi phổ biến và cách chẩn đoán

| Mẫu sai hoặc tình huống | Hậu quả thường gặp | Cách xử lý |
| --- | --- | --- |
| `String.format("%d", "42")` | `IllegalFormatConversionException`: chuỗi không phải số nguyên. | Parse sang số nếu phù hợp, hoặc dùng `%s` để hiển thị chuỗi. |
| `String.format("%.2d", 42)` | `IllegalFormatPrecisionException`: số nguyên `d` không hỗ trợ precision. | Dùng width/flag số như `%04d` nếu cần điền 0. |
| `String.format("%-s", "A")` | `MissingFormatWidthException`: `-` đòi hỏi width. | Dùng `%-8s` chẳng hạn. |
| `String.format("%2$s", "A")` | `MissingFormatArgumentException`: không có đối số thứ hai. | Kiểm tra vị trí theo chỉ số bắt đầu từ 1. |
| `String.format("%<s", "A")` | `MissingFormatArgumentException`: chưa có đối số trước để dùng lại. | Đặt một specifier có đối số trước `%<s`. |
| `String.format("%q", "A")` | `UnknownFormatConversionException`: `q` không phải conversion hợp lệ. | Chọn conversion trong bảng hỗ trợ. |
| Trộn `%s` và `%2$s` tùy tiện | Kết quả có thể khác thứ tự dự đoán. | Chọn một cách đánh chỉ số rõ ràng, kiểm tra thứ tự đối số. |
| Cho rằng `%8.3s` có nghĩa “lấy 8 ký tự, đệm 3” | Đọc sai width/precision. | Với `s`: lấy tối đa 3 ký tự, rồi đệm đến width tối thiểu 8. |
| Cho rằng `%n` là chữ `n` hoặc luôn bằng `\n` | Nhầm về ký tự xuống dòng phụ thuộc nền tảng. | Dùng `%n` cho đầu ra định dạng theo hệ thống; `\n` khi thật sự yêu cầu LF. |

Trong thực tế, các ngoại lệ trên thuộc họ `IllegalFormatException` và thường chỉ xuất hiện khi format string được phân tích lúc chạy. Tránh xây format string động nếu có thể; nếu phải làm vậy, kiểm tra đầu vào và thử các đường chạy đại diện.

## 11. Quy trình đọc bất kỳ specifier nào

Gặp một biểu thức như `%3$-14.5S`, hãy trả lời lần lượt:

1. **Đối số nào?** `3$` lấy đối số thứ ba.
2. **Có flags gì?** `-` yêu cầu căn trái.
3. **Width bao nhiêu?** `14` là mức rộng tối thiểu.
4. **Precision là gì với conversion này?** `.5` với `S` là tối đa năm ký tự.
5. **Conversion gì?** `S` chuyển chuỗi sang chữ hoa theo locale.

Khi gặp `%1$+010.2f`, câu trả lời sẽ là: đối số thứ nhất, luôn ghi dấu, điền 0, rộng tối thiểu 10 ký tự, hai chữ số sau dấu thập phân, kiểu số thực `f`. Khi gặp `%1$tF`, đọc thành: đối số thứ nhất, kiểu ngày giờ `t` với hậu tố `F`, **không có precision**.

## 12. Tự luyện tập

Cho đối số `"abcdef"`, giá trị `7` và số thực `12.5`, dự đoán:

```java
String a = String.format("|%1$8.3s|", "abcdef");
String b = String.format(Locale.US, "%1$04d", 7);
String c = String.format(Locale.US, "%1$+.1f", 12.5);
String d = String.format("%2$s-%1$s", "first", "second");
```

Đáp án: `a` là `"|     abc|"` (năm dấu cách trước `abc`), `b` là `"0007"`, `c` là `"+12.5"`, còn `d` là `"second-first"`.

**Điểm cần nhớ:** đọc specifier theo thứ tự **chỉ số → flags → width → precision → conversion**, đồng thời kiểm tra xem conversion có hỗ trợ những thành phần đã yêu cầu hay không. Với ngày giờ, xử lý thêm ký tự theo sau `t`/`T`; với `%%` và `%n`, không lấy đối số.

Tài liệu tham khảo chính thức: [`java.util.Formatter` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Formatter.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-formatting-003">Các kiểu %s, %c, %b, %d, %f và %e</a>

<details>
<summary>Xem chi tiết</summary>

# Định dạng chuỗi Java với %s, %c, %b, %d, %f và %e

## 1. Mục đích và các API sử dụng

Trong Java, `java.util.Formatter` cung cấp cú pháp để chèn giá trị vào một chuỗi theo mẫu (*format string*). Sáu **conversion specifier** thường gặp là `%s`, `%c`, `%b`, `%d`, `%f` và `%e`. Mỗi specifier quyết định cách chuyển một đối số sang văn bản; có thể bổ sung độ rộng, độ chính xác, dấu căn lề và quy tắc hiển thị.

Ba cách sử dụng phổ biến:

~~~java
import java.util.Locale;

String message = String.format(Locale.US, "Name: %s, age: %d", "Mai", 20);
System.out.printf(Locale.US, "Price: %.2f%n", 12.5);

String message2 = "Name: %s, age: %d".formatted("Mai", 20);
~~~

- `String.format(...)` **trả về một `String`**; không tự in.
- `System.out.printf(...)` **ghi vào luồng đầu ra**, tương tự `System.out.format(...)`; thường thêm `%n` khi muốn xuống dòng. `printf` trả về chính `PrintStream`.
- `"mẫu".formatted(...)` (từ Java 15) trả về một chuỗi đã định dạng và dùng locale mặc định; cú pháp các specifier vẫn theo `Formatter`.

Ở hai lời gọi đầu, `Locale.US` được truyền tường minh để ví dụ số có dấu chấm thập phân và dấu phẩy tách nhóm nhất quán. Nếu bỏ locale, kết quả định dạng số có thể phụ thuộc cấu hình vùng/ngôn ngữ của JVM.

Một format string có thể chứa văn bản thường xen kẽ nhiều specifier:

~~~java
String line = String.format(
        Locale.US,
        "%s | %c | %b | %d | %.2f | %.2e",
        "Java", 'J', true, 42, 12.5, 1250.0
);
// Java | J | true | 42 | 12.50 | 1.25e+03
~~~

Mỗi vị trí `%...` nhận một đối số tương ứng theo thứ tự, trừ khi sử dụng chỉ số đối số hoặc cú pháp tái sử dụng đối số.

## 2. Cấu trúc một chỉ thị định dạng

Dạng khái quát của `Formatter`:

~~~text
%[argument_index$][flags][width][.precision]conversion
~~~

Ý nghĩa các thành phần:

| Thành phần | Ý nghĩa | Ví dụ |
|---|---|---|
| `%` | Bắt đầu chỉ thị | `%d` |
| `argument_index$` | Chọn đối số thứ mấy, tính từ **1** | `%2$s` |
| `flags` | Căn trái, thêm dấu, đệm 0, phân nhóm... tùy conversion | `%-8s`, `%+d`, `%,d` |
| `width` | **Độ rộng tối thiểu** của trường kết quả | `%8d` |
| `.precision` | Giới hạn độ dài chuỗi hoặc số chữ số sau dấu thập phân, tùy conversion | `%.3s`, `%.2f` |
| `conversion` | Kiểu chuyển đổi | `s`, `c`, `b`, `d`, `f`, `e` |

`width` không cắt bớt giá trị dài hơn độ rộng đã yêu cầu; thông thường nó thêm khoảng trắng ở bên trái, trừ khi có flag căn trái. `precision` mang nghĩa khác nhau theo từng conversion và **không được dùng với mọi kiểu**.

Ví dụ:

~~~java
System.out.println("|" + String.format(Locale.US, "%8s", "Hi") + "|");
// |      Hi|

System.out.println("|" + String.format(Locale.US, "%-8s", "Hi") + "|");
// |Hi      |

System.out.println(String.format(Locale.US, "%.3s", "Spring"));
// Spr

System.out.println("|" + String.format(Locale.US, "%8.2f", 12.5) + "|");
// |   12.50|
~~~

Các ví dụ sử dụng chữ Latin và ký tự ASCII để số khoảng trắng dễ kiểm chứng. Độ rộng của `Formatter` không nên được hiểu là số cột hiển thị chắc chắn trong terminal đối với mọi emoji, ký tự tổ hợp hoặc chữ Đông Á.

## 3. `%s`: biểu diễn đối tượng dưới dạng chuỗi

`%s` là conversion **general/string**. Nó chấp nhận nhiều kiểu đối số, bao gồm `String`, số, boolean và đối tượng thông thường:

~~~java
System.out.println(String.format("Language: %s", "Java"));    // Language: Java
System.out.println(String.format("Count: %s", 42));           // Count: 42
System.out.println(String.format("Enabled: %s", true));       // Enabled: true
System.out.println(String.format("Value: %s", (Object) null)); // Value: null
~~~

Thông thường, Java dùng biểu diễn chuỗi của đối tượng (liên quan đến `toString()`). Với đối tượng triển khai `java.util.Formattable`, `Formatter` có thể sử dụng cơ chế định dạng chuyên biệt của interface đó. `%s` không có nghĩa là Java tự tạo JSON, CSV hoặc một định dạng trao đổi dữ liệu chuẩn.

Các tùy chọn quan trọng:

~~~java
String.format("%10s", "Java");  // "      Java": rộng ít nhất 10
String.format("%-10s", "Java"); // "Java      ": căn trái
String.format("%.2s", "Java"); // "Ja": tối đa 2 ký tự
String.format("%-6.3s", "Spring"); // "Spr   "
~~~

Khi cần định dạng cột tên người, mã sản phẩm hay nhãn văn bản, `%s` kết hợp với width/precision giúp trình bày rõ ràng. Nếu việc cắt chuỗi có thể làm mất thông tin hoặc cắt cụm ký tự Unicode, hãy xử lý quy tắc rút gọn ở tầng nghiệp vụ trước khi định dạng.

Chữ hoa `%S` áp dụng biến đổi sang chữ hoa cho kết quả theo locale (ví dụ `String.format(Locale.US, "%S", "java")` cho `JAVA`). Điều này khác với `%s` vốn không tự đổi chữ hoa.

## 4. `%c`: ký tự hoặc mã điểm Unicode

`%c` là conversion **character**, thường dùng với `char`/`Character`. Nó cũng chấp nhận `Byte`, `Short` hoặc `Integer` biểu diễn một mã điểm Unicode hợp lệ:

~~~java
System.out.println(String.format("%c", 'A')); // A
System.out.println(String.format("%c", 66));  // B (mã điểm U+0042)
System.out.println(String.format("|%3c|", 'Z')); // |  Z|
~~~

`%c` đòi hỏi đối số đúng loại; **`String` chỉ dài một ký tự vẫn không phải là `char`**:

~~~java
char letter = "Java".charAt(0);
String ok = String.format("%c", letter); // J

// String.format("%c", "J"); // Sai lúc chạy: IllegalFormatConversionException
~~~

`%c` với giá trị số nguyên nhận **mã điểm Unicode**, không có nghĩa là lấy chữ số đó để in. Mã điểm ngoài miền Unicode hợp lệ sẽ dẫn đến lỗi định dạng; ví dụ `String.format("%c", -1)` không hợp lệ. Với một mã điểm bổ sung hợp lệ (ngoài BMP), kết quả có thể chứa hai đơn vị mã UTF-16 nhưng vẫn biểu diễn một mã điểm.

Không dùng precision với `%c`; `%.2c` là format không hợp lệ. Có thể dùng width để căn cột. `%C` là biến thể in hoa phụ thuộc quy tắc locale.

## 5. `%b`: biểu diễn boolean theo quy tắc của Formatter

`%b` là conversion **boolean**, nhưng quy tắc của nó **khác** với việc ép mọi đối số thành `boolean` theo logic nghiệp vụ.

- Nếu đối số là `null`, kết quả là `false`.
- Nếu là `Boolean`, kết quả là `true` hoặc `false` theo giá trị thực.
- Nếu là **đối tượng khác và không null**, kết quả là `true`, kể cả số `0` hay chuỗi rỗng.

~~~java
System.out.println(String.format("%b", true));           // true
System.out.println(String.format("%b", false));          // false
System.out.println(String.format("%b", (Object) null));  // false
System.out.println(String.format("%b", 0));              // true
System.out.println(String.format("%b", ""));             // true
~~~

Vì vậy, `%b` **không kiểm tra “số khác 0” hay “chuỗi không rỗng”** và cũng không tự hiểu chuỗi `"false"` là giá trị boolean `false`. Để biểu diễn điều kiện nghiệp vụ, hãy tính biểu thức boolean trước:

~~~java
int quantity = 0;
System.out.println(String.format("Has stock: %b", quantity > 0));
// Has stock: false
~~~

`%B` viết hoa kết quả thành `TRUE` hoặc `FALSE`. Có thể dùng width/căn lề để trình bày; không nên dùng `%b` để thay việc kiểm tra dữ liệu đầu vào.

## 6. `%d`: số nguyên thập phân có dấu

`%d` nhận các kiểu số nguyên như `byte`, `short`, `int`, `long` và `BigInteger` (hoặc đối tượng bao tương ứng), rồi định dạng ở **hệ thập phân**.

~~~java
System.out.println(String.format(Locale.US, "%d", 42));       // 42
System.out.println(String.format(Locale.US, "%d", -42));      // -42
System.out.println(String.format(Locale.US, "%05d", 42));     // 00042
System.out.println(String.format(Locale.US, "%+d", 42));      // +42
System.out.println(String.format(Locale.US, "%,d", 1234567)); // 1,234,567
~~~

Các flag thường dùng:

- `0`: đệm số 0 vào khoảng trống bên trái sau dấu âm/dương để đủ width, ví dụ `%05d`.
- `+`: luôn hiển thị dấu dương hoặc âm, ví dụ `%+d`.
- `,`: phân nhóm chữ số theo locale, ví dụ `%,d`.
- `-`: căn trái trong trường đủ độ rộng, ví dụ `%-6d`.
- `(`: biểu diễn số âm bằng ngoặc, ví dụ `%(d` với `-42` thành `(42)`.

`%d` **không** làm tròn số thực: đưa `double` hoặc `float` cho `%d` gây `IllegalFormatConversionException`. Nếu muốn hiển thị một số thực không có phần thập phân, cần quyết định quy tắc làm tròn/chuyển kiểu trước, hoặc sử dụng `%.0f` với kiểu số thực nếu đúng yêu cầu hiển thị.

Đặc biệt, `%.3d` **không hợp lệ trong Java Formatter**: precision không được hỗ trợ cho `%d`. Muốn hiển thị `007` từ số nguyên `7`, dùng `%03d` (width + đệm 0).

## 7. `%f`: số thực ở dạng thập phân cố định

`%f` nhận `float`, `double` hoặc `BigDecimal` (và các đối tượng bao của hai kiểu nguyên thủy). Nó hiển thị số thực ở dạng thập phân thông thường, **không dùng ký hiệu số mũ**.

~~~java
System.out.println(String.format(Locale.US, "%f", 12.5));    // 12.500000
System.out.println(String.format(Locale.US, "%.2f", 12.5));  // 12.50
System.out.println(String.format(Locale.US, "%.0f", 12.5));  // 13
System.out.println(String.format(Locale.US, "%8.2f", 12.5)); // "   12.50"
System.out.println(String.format(Locale.US, "%,.2f", 1234.5)); // 1,234.50
~~~

Đối với `%f`:

- Không ghi precision: mặc định **6 chữ số sau dấu thập phân**.
- `%.2f`: hiển thị **2 chữ số sau dấu thập phân**; làm tròn để biểu diễn theo độ chính xác đó và thêm số 0 ở cuối nếu cần.
- `%.0f`: không in chữ số sau dấu thập phân, không có nghĩa làm thay đổi giá trị trong biến gốc.
- `%8.2f`: số 8 là độ rộng tối thiểu của cả trường, kể cả dấu âm hoặc dấu chấm; số 2 là số chữ số sau dấu thập phân.
- `%,.2f`: bổ sung ký tự phân nhóm, với ký tự cụ thể tùy locale.

Không dùng `%f` trực tiếp cho số nguyên `int`: `String.format("%f", 12)` bị lỗi loại đối số. Nếu mục đích là biểu diễn `12.00`, truyền `12.0`, ép sang `double` hoặc dùng `BigDecimal`.

**Về độ chính xác:** `double` lưu số theo nhị phân nên nhiều số thập phân (như `1.005`) không được biểu diễn chính xác tuyệt đối. `%.2f` quyết định cách **hiển thị**, không biến số nhị phân thành số thập phân chính xác và không thay thế quy tắc làm tròn tiền tệ. Với bài toán cần giá trị thập phân chính xác, cân nhắc `BigDecimal` tạo từ chuỗi và quy định rõ cách làm tròn trong tính toán; còn để hiển thị tiền theo vùng, `NumberFormat` có thể phù hợp hơn.

## 8. `%e`: số thực theo ký hiệu khoa học

`%e` nhận cùng nhóm đối số số thực với `%f`, nhưng dùng dạng **scientific notation**:

~~~text
mantissa e exponent
~~~

Ví dụ, `1250.0 = 1.25 × 10³`, nên:

~~~java
System.out.println(String.format(Locale.US, "%e", 1250.0));
// 1.250000e+03

System.out.println(String.format(Locale.US, "%.2e", 1250.0));
// 1.25e+03

System.out.println(String.format(Locale.US, "%.2e", 0.00125));
// 1.25e-03

System.out.println(String.format(Locale.US, "%.2E", 1250.0));
// 1.25E+03
~~~

Tương tự `%f`, precision mặc định của `%e` là **6 chữ số sau dấu thập phân trong phần hệ số (mantissa)**; `%.2e` cho hai chữ số sau dấu thập phân. `e`/`E` là ký hiệu số mũ, dấu `+`/`-` là dấu của số mũ, còn `03` có nghĩa số mũ là ba, được trình bày tối thiểu hai chữ số.

`%e` đặc biệt hữu ích khi dữ liệu rất lớn/nhỏ (thống kê, đo lường, tính toán khoa học); `%f` dễ đọc hơn khi giá trị nằm trong khoảng quen thuộc và người xem muốn một số chữ số thập phân cố định.

Ví dụ lựa chọn cách hiển thị cùng một số:

~~~java
double value = 0.0000125;
System.out.println(String.format(Locale.US, "%.8f", value));
// 0.00001250

System.out.println(String.format(Locale.US, "%.2e", value));
// 1.25e-05
~~~

Cả hai chỉ thay đổi biểu diễn văn bản của cùng `double`; không thay đổi biến `value`.

## 9. Bảng đối chiếu sáu conversion

| Conversion | Ý nghĩa | Đối số thường dùng | Ví dụ và kết quả với Locale.US |
|---|---|---|---|
| `%s` | Chuỗi/biểu diễn đối tượng | `String`, mọi đối tượng (kể cả null) | `"%s", "Java"` → `Java` |
| `%c` | Ký tự / mã điểm Unicode hợp lệ | `char`, `Character`, `byte`/`short`/`int` tương ứng | `"%c", 'A'` → `A` |
| `%b` | Boolean theo quy tắc Formatter | `boolean`, `Boolean`, bất kỳ đối tượng | `"%b", 0` → `true` |
| `%d` | Số nguyên thập phân | `byte`, `short`, `int`, `long`, `BigInteger` | `"%05d", 42` → `00042` |
| `%f` | Số thực dạng cố định | `float`, `double`, `BigDecimal` | `"%.2f", 12.5` → `12.50` |
| `%e` | Số thực dạng khoa học | `float`, `double`, `BigDecimal` | `"%.2e", 1250.0` → `1.25e+03` |

Các conversion viết hoa tương ứng `%S`, `%C`, `%B` và `%E` thay đổi cách trình bày chữ hoa; `%d` và `%f` không có dạng `%D` hoặc `%F` tương đương trong `Formatter`.

Lựa chọn theo bản chất dữ liệu: ký tự dùng `%c`; boolean dùng `%b`; số nguyên dùng `%d`; số thực cần hai chữ số thập phân dùng `%.2f`; số rất nhỏ/lớn có thể dùng `%.2e`; dữ liệu cần hiển thị dưới dạng văn bản chung dùng `%s`.

## 10. Thứ tự đối số, tái sử dụng và ký tự đặc biệt

### 10.1. Đổi thứ tự với `n$`

Theo mặc định, conversion nhận đối số theo thứ tự xuất hiện. Với `%1$s`, `%2$d`,... ta có thể chỉ định trực tiếp vị trí đối số (bắt đầu từ 1):

~~~java
String text = String.format(Locale.US,
        "%2$s has %1$d points", 95, "Lan");
System.out.println(text);
// Lan has 95 points
~~~

Đây là lựa chọn hữu ích khi bản dịch giữa các ngôn ngữ cần đảo trật tự thành phần câu. Cần đảm bảo chỉ số và kiểu conversion khớp đối số thật.

### 10.2. Tái sử dụng với `<`

Flag `<` dùng lại đối số của format specifier **trước đó**:

~~~java
System.out.println(String.format(Locale.US, "%d / %<d", 7));
// 7 / 7
~~~

Không dùng `%<d` ở vị trí đầu tiên khi chưa có đối số trước đó để tái sử dụng.

### 10.3. In dấu phần trăm và xuống dòng

`%%` biểu diễn **dấu `%` nguyên văn**; `%n` tạo dấu xuống dòng theo nền tảng. Cả hai **không sử dụng một đối số giá trị**:

~~~java
System.out.printf(Locale.US, "Completed: %d%%%n", 75);
// Completed: 75%
// (sau đó xuống dòng theo hệ điều hành)
~~~

`%n` phù hợp khi in ra console hoặc xây dựng văn bản phụ thuộc quy ước xuống dòng của nền tảng. Đối với chuỗi chuẩn giao thức yêu cầu rõ `\n`, nên sử dụng ký tự newline chuẩn mà giao thức yêu cầu, không mặc định thay bằng `%n`.

## 11. Locale và định dạng số

Cùng format `%,.2f` nhưng hai locale có thể cho hai cách viết khác nhau:

~~~java
double amount = 1234.5;

System.out.println(String.format(Locale.US, "%,.2f", amount));
// 1,234.50

System.out.println(String.format(Locale.GERMANY, "%,.2f", amount));
// 1.234,50
~~~

Locale ảnh hưởng đến dấu thập phân, phân nhóm và một số quy tắc ngôn ngữ. Đối với log, file trao đổi máy hoặc test có kỳ vọng chính xác, chọn locale tường minh phù hợp (chẳng hạn `Locale.US` hoặc `Locale.ROOT` theo quy ước định dạng mong muốn). Khi hiển thị cho người dùng, sử dụng locale phù hợp với họ.

Không tự ghép `String.format("%.2f", amount)` vào định dạng cần máy đọc nếu không kiểm soát locale: trong một locale, dấu phẩy có thể là dấu thập phân, làm người nhận hiểu sai dữ liệu. Đồng thời, `%s` và các conversion kể trên không tự mã hóa dấu nháy/escape cho CSV, JSON, SQL hay HTML.

## 12. Ví dụ hoàn chỉnh Java 17+

Tạo tệp **`FormatterConversionsDemo.java`**:

~~~java
import java.util.Locale;

public class FormatterConversionsDemo {
    public static void main(String[] args) {
        String name = "Linh";
        char grade = 'A';
        boolean active = true;
        long count = 1_234_567L;
        double price = 1234.5;
        double tiny = 0.00125;

        System.out.printf(Locale.US, "text: %s%n", name);
        System.out.printf(Locale.US, "char: %c%n", grade);
        System.out.printf(Locale.US, "bool: %b%n", active);
        System.out.printf(Locale.US, "decimal: %,d%n", count);
        System.out.printf(Locale.US, "fixed: %,.2f%n", price);
        System.out.printf(Locale.US, "scientific: %.2e%n", tiny);

        System.out.printf(Locale.US,
                "alignment: |%-8.3s|%05d|%n", "Spring", 42);
        System.out.printf(Locale.US,
                "reordered: %2$s=%1$d%n", 9, "id");

        String summary = String.format(
                Locale.US, "%s: %.2f", "Total", 12.5);
        System.out.println(summary);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac FormatterConversionsDemo.java
java FormatterConversionsDemo
~~~

Đầu ra khi dùng `Locale.US`:

~~~text
text: Linh
char: A
bool: true
decimal: 1,234,567
fixed: 1,234.50
scientific: 1.25e-03
alignment: |Spr     |00042|
reordered: id=9
Total: 12.50
~~~

Đối chiếu kết quả:

1. `%s` chèn `Linh`, `%c` chèn `A`, `%b` chèn `true`.
2. `%,d` phân nhóm số nguyên: `1,234,567`.
3. `%,.2f` biểu diễn số thực hai chữ số thập phân và phân nhóm: `1,234.50`.
4. `%.2e` chuẩn hóa số nhỏ thành hệ số và số mũ: `1.25e-03`.
5. `%-8.3s` cắt `Spring` còn `Spr` rồi thêm 5 dấu cách ở phải để rộng 8; `%05d` đệm số 0 để số `42` có độ rộng 5.
6. `%2$s=%1$d` lấy đối số thứ hai `id` trước rồi lấy đối số thứ nhất `9`; `String.format` tạo `Total: 12.50` mà không in cho tới khi gọi `println`.

Các câu lệnh `System.out.printf` và `System.out.println` tạo xuống dòng như trong đầu ra. Không cần thư viện ngoài JDK.

## 13. Lỗi phổ biến và cách xử lý

### 13.1. Conversion không phù hợp kiểu đối số

Những lời gọi này **biên dịch được nhưng ném ngoại lệ khi chạy**:

~~~java
// String.format("%d", "42");  // IllegalFormatConversionException
// String.format("%f", 42);    // IllegalFormatConversionException
// String.format("%c", "A");   // IllegalFormatConversionException
~~~

Cách sửa là truyền đúng loại dữ liệu hoặc chủ động chuyển đổi sau khi kiểm tra đầu vào; ví dụ `Integer.parseInt("42")` nếu muốn hiển thị dưới dạng số nguyên thật sự. Không thay mọi conversion bằng `%s` chỉ để giấu lỗi nếu bài toán yêu cầu định dạng số.

### 13.2. Số lượng đối số không đủ

~~~java
// String.format("%s %d", "Java");
// MissingFormatArgumentException: chưa có đối số cho %d
~~~

Kiểm tra đủ đối số, thứ tự và các chỉ số `n$`. Lưu ý `%%` và `%n` không chiếm vị trí một đối số.

### 13.3. Nhầm width với precision

- `%8s`: độ rộng **ít nhất 8**, không cắt chuỗi.
- `%.3s`: chỉ lấy tối đa 3 ký tự trong kết quả `%s`.
- `%8.2f`: độ rộng ít nhất 8, với 2 chữ số thập phân.
- `%03d`: số nguyên có trường rộng 3 và được đệm số 0.
- `%.3d`: **sai** trong Java Formatter; `%d` không hỗ trợ precision.

### 13.4. Đặt flag hoặc precision không hợp lệ

Không phải flag nào cũng hợp lệ cho mọi conversion. Ví dụ, `%05s` không hợp lệ vì flag `0` dùng để đệm số, không dùng để đệm chuỗi kiểu `%s`. `%.2c` và `%.2d` không hợp lệ vì hai conversion này không chấp nhận precision. Java sẽ phát sinh các ngoại lệ thuộc họ `IllegalFormatException` (ví dụ `FormatFlagsConversionMismatchException` hoặc `IllegalFormatPrecisionException`), thay vì bỏ qua tùy chọn sai.

### 13.5. Cho rằng `%b` là phép chuyển đổi boolean theo nghiệp vụ

`String.format("%b", 0)` cho `true` vì `Integer` là một đối tượng không null. Để hiển thị ý nghĩa “số lượng có lớn hơn 0 không”, hãy định dạng biểu thức `quantity > 0`.

### 13.6. Cho rằng định dạng thay đổi dữ liệu gốc hoặc đủ để giải quyết bài toán tiền tệ

~~~java
double x = 12.5;
String display = String.format(Locale.US, "%.2f", x);
// display = "12.50", còn x vẫn bằng 12.5
~~~

`Formatter` tạo biểu diễn văn bản. Nó không làm thay đổi `x`, không tự kiểm tra đơn vị tiền và không xử lý các quy tắc làm tròn phức tạp của nghiệp vụ. Đừng dùng chuỗi đã định dạng như nguồn sự thật để tính toán tiếp.

### 13.7. Không kiểm soát locale

`String.format("%,.2f", value)` có thể cho dấu phân nhóm và dấu thập phân khác nhau ở các máy. Với output cần so sánh nguyên văn trong test hoặc truyền giữa hệ thống, chọn locale tường minh và quy ước encoding/format cụ thể.

## 14. Cách chọn conversion khi viết chương trình

Khi định dạng giá trị, hãy xác định loại dữ liệu thật và mục đích hiển thị trước:

- Dữ liệu văn bản/đối tượng: `%s`; dùng width, alignment, precision khi cần cột hoặc rút gọn.
- Một ký tự hoặc mã điểm Unicode: `%c`; không truyền `String` thay cho `char`.
- Điều kiện boolean đã được tính: `%b`.
- Số nguyên: `%d`; dùng `%05d` cho đệm 0 theo chiều rộng, `%,d` cho phân nhóm theo locale.
- Số thực ở dạng thông thường: `%f` hoặc `%.2f`; precision kiểm soát chữ số sau dấu thập phân.
- Số thực dạng khoa học: `%e` hoặc `%.2e`; phù hợp với giá trị rất lớn/nhỏ.

Cuối cùng, kiểm tra thứ tự đối số, loại dữ liệu, locale, ý nghĩa width/precision và các dấu `%%`, `%n`. Những kiểm tra này giúp chuỗi hiển thị nhất quán và tránh các lỗi `Formatter` chỉ xuất hiện khi chạy chương trình.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-formatting-004">Căn lề, đệm ký tự và phân nhóm chữ số</a>

<details>
<summary>Xem chi tiết</summary>

# Căn lề, đệm ký tự và phân nhóm chữ số khi định dạng chuỗi Java

## 1. Bài toán và công cụ

Khi tạo báo cáo dạng văn bản, bảng điều khiển (console), hóa đơn hay dòng log, ta thường muốn **căn các cột thẳng hàng**, **thêm ký tự đệm cho đủ chiều rộng** và **phân nhóm chữ số** để dễ đọc:

```text
Tên           Số lượng        Doanh thu
Java                 42         1,234,567
Spring                7            12,500
```

Java cung cấp cơ chế định dạng bằng **format string** trong `String.format(...)`, `System.out.printf(...)` và `java.util.Formatter`. Các API này dùng chung quy tắc định dạng, nhưng khác cách nhận kết quả:

- `String.format(Locale, format, args...)` **trả về một `String`**, thuận tiện để đưa vào biến, ghi file hoặc trả về từ phương thức.
- `System.out.printf(Locale, format, args...)` **ghi trực tiếp ra standard output**; nếu muốn xuống dòng, thêm `%n` vào chuỗi format.
- `Formatter` cho phép ghi nhiều lần vào một đối tượng đích; đa số ví dụ cơ bản dùng `String.format` hoặc `printf` là đủ.
- `"Hello %s".formatted(name)` (Java 15+) cũng sử dụng ngữ nghĩa Formatter, nhưng không có tham số `Locale` trực tiếp trong cách gọi này; khi cần kết quả nhất quán giữa môi trường, ưu tiên overload có `Locale` tường minh.

Ba yêu cầu dễ bị nhầm là:

1. **Căn lề (alignment)**: nội dung nằm bên trái hay bên phải vùng có độ rộng tối thiểu.
2. **Đệm (padding)**: thêm khoảng trắng hoặc số `0` để đủ vùng đó.
3. **Phân nhóm số (grouping)**: thêm ký hiệu phân tách giữa các nhóm chữ số, do `Locale` quyết định.

Căn lề và đệm xử lý cách trình bày; phân nhóm thay đổi cách hiển thị các chữ số. Chúng có thể kết hợp, nhưng phải dùng đúng cờ định dạng.

## 2. Cú pháp một định dạng trong Java

Một conversion có dạng tổng quát:

```text
%[argument_index$][flags][width][.precision]conversion
```

Dấu ngoặc vuông ở dòng trên chỉ thành phần **tùy chọn trong mô tả**, không phải ký tự nhập nguyên xi vào format string.

| Thành phần | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| `%` | `%10s` | Bắt đầu một chỉ thị định dạng. |
| `argument_index$` | `%2$s` | Tùy chọn lấy đối số theo vị trí, đánh số từ `1`. |
| `flags` | `-`, `0`, `,`, `+`, `(` | Các hành vi như căn trái, đệm số không, phân nhóm, hiển thị dấu. |
| `width` | `10` trong `%10s` | **Chiều rộng tối thiểu** của trường xuất. |
| `.precision` | `.2` trong `%.2f` | Ý nghĩa phụ thuộc conversion: số chữ số sau dấu thập phân với `%f`, độ dài hiển thị tối đa cho `%s`. |
| `conversion` | `s`, `d`, `f` | Loại định dạng của đối số. |

Các conversion dùng nhiều trong bài:

| Conversion | Dùng cho | Ví dụ |
| --- | --- | --- |
| `%s` | Chuỗi/đối tượng hiển thị dạng chuỗi | `"%10s"` |
| `%d` | Số nguyên hệ thập phân | `"%08d"` |
| `%f` | Số thực dạng thập phân cố định | `"%,12.2f"` |
| `%%` | In ký tự phần trăm `%` | `"100%%"` |
| `%n` | Xuống dòng theo nền tảng đang chạy | `"Done%n"` |

Không phải mọi cờ đều dùng được với mọi conversion. Chẳng hạn `0` là cờ đệm số, **không phải** cờ đệm chuỗi `%s`; còn dấu phẩy phân nhóm `,` áp dụng với một số conversion số thích hợp chứ không áp dụng cho `%s`.

## 3. Căn phải và căn trái bằng chiều rộng tối thiểu

### 3.1. Căn phải mặc định

Với `%10s`, phần hiển thị chiếm **ít nhất 10 vị trí ký tự**. Nếu `"Java"` có bốn ký tự, Java thêm sáu khoảng trắng ở **bên trái**:

```java
String right = String.format("%10s", "Java");
// right = "      Java" (6 khoảng trắng rồi đến Java)
```

Đối với `%d`, căn phải cũng là mặc định:

```java
String rightNumber = String.format("%8d", 42);
// rightNumber = "      42"
```

### 3.2. Cờ `-` để căn trái

`%-10s` và `%-8d` thêm khoảng trắng ở **bên phải**:

```java
String left = String.format("%-10s", "Java");
// left = "Java      "

String leftNumber = String.format("%-8d", 42);
// leftNumber = "42      "
```

Khi in ví dụ ra console, nên đặt ký tự `|` bao quanh từng giá trị để thấy rõ khoảng trắng:

```java
System.out.printf("|%10s|%-10s|%n", "Java", "Java");
// |      Java|Java      |
```

**Cần nhớ:** `width` là chiều rộng **tối thiểu**, không phải chiều rộng tối đa. `%10s` sẽ **không cắt ngắn** chuỗi dài hơn 10 ký tự. Ví dụ `String.format("%10s", "VeryLongText")` vẫn trả về `"VeryLongText"`. Nếu cần giới hạn độ dài hiển thị của chuỗi, xem `precision` trong mục 7.

## 4. Đệm số `0`: số nguyên có độ dài cố định

Với số nguyên, cờ `0` bổ sung các số không ở **bên trái phần chữ số** để tổng chiều rộng đạt `width`:

```java
System.out.printf("|%08d|%n", 42);
// |00000042|
```

Phân biệt ba tình huống:

| Format | Giá trị | Kết quả trong `|...|` | Ghi chú |
| --- | ---: | --- | --- |
| `%8d` | `42` | `\|      42\|` | Mặc định đệm khoảng trắng bên trái. |
| `%-8d` | `42` | `\|42      \|` | Căn trái, đệm khoảng trắng bên phải. |
| `%08d` | `42` | `\|00000042\|` | Đệm số không ở bên trái số. |
| `%+08d` | `42` | `\|+0000042\|` | Dấu `+` xuất hiện trước các số `0`. |
| `%(08d` | `-42` | `\|(000042)\|` | Số âm trong ngoặc, các số `0` nằm trong ngoặc. |

Đặc điểm quan trọng: chiều rộng `8` **tính cả dấu cộng/trừ hoặc dấu ngoặc** do định dạng thêm vào. Vì thế `%+08d` của `42` là `+0000042`, có tổng cộng tám ký tự.

Cờ `0` cần một `width`; một format như `%0d` là không hợp lệ. Cờ `-` (căn trái) và `0` (đệm số không bên trái) không được kết hợp tùy tiện trong cùng chỉ thị, vì chúng xung đột về cách đệm.

Đối với mã định danh kiểu `000042`, cách đệm `%06d` có ích nếu giá trị là số nguyên. Nếu phải giữ nguyên **chuỗi định danh** như `"00A42"` hoặc số có độ dài/leading zeros vốn là dữ liệu, hãy lưu nó dưới dạng `String`; chuyển sang kiểu số có thể làm mất các số `0` gốc.

## 5. Phân nhóm chữ số bằng cờ dấu phẩy `,`

Cờ `,` trong `Formatter` yêu cầu định dạng **phân nhóm chữ số theo Locale**. Với `Locale.US`, một số nguyên lớn thường được nhóm thành từng cụm ba chữ số, ngăn bằng dấu phẩy:

```java
import java.util.Locale;

String result = String.format(Locale.US, "%,d", 1_234_567);
System.out.println(result);
// 1,234,567
```

Dấu phẩy `,` **trong format string** là một **cờ cú pháp**, không nhất thiết là ký tự sẽ xuất hiện trong kết quả. Dấu phân nhóm thực tế tùy `Locale`.

### 5.1. Kết hợp phân nhóm và căn lề

`%,12d` vừa bật phân nhóm vừa yêu cầu vùng rộng tối thiểu 12 ký tự:

```java
String result = String.format(Locale.US, "|%,12d|", 1_234_567);
System.out.println(result);
// |   1,234,567|
```

`1,234,567` có 9 ký tự, nên có 3 khoảng trắng bên trái. Với `%-,12d`, khoảng trắng được đưa sang bên phải:

```java
String result = String.format(Locale.US, "|%-,12d|", 1_234_567);
System.out.println(result);
// |1,234,567   |
```

### 5.2. Kết hợp phân nhóm và đệm số không

Có thể dùng cả `0` lẫn `,`. Ví dụ:

```java
String result = String.format(Locale.US, "|%0,12d|", 1_234_567);
System.out.println(result);
// |0001,234,567|
```

Số `0` được thêm vào vị trí đệm để đủ chiều rộng; kết quả không giống việc thêm các số `0` vào giá trị số ban đầu rồi tự động phân nhóm lại. Vì yêu cầu đầu ra thường khác nhau, cần chọn rõ:

- **Trình bày số dễ đọc:** `%,d` hoặc `%,12d`.
- **Tạo trường số có độ rộng cố định:** `%08d`.
- **Vừa phân nhóm vừa đệm `0`:** `%0,12d` nếu thực sự muốn dạng hiển thị như trên.

Khi dữ liệu vượt quá chiều rộng tối thiểu, `Formatter` vẫn in **toàn bộ** giá trị; nó không tự cắt chữ số.

## 6. Phân nhóm phần nguyên và làm tròn phần thập phân

Với `%f`, `precision` là số chữ số **sau dấu thập phân** cần hiển thị. Kết hợp:

```java
String amount = String.format(
        Locale.US,
        "%,.2f",
        1234567.5
);
System.out.println(amount);
// 1,234,567.50
```

Có thể thêm chiều rộng:

```java
String amount = String.format(
        Locale.US,
        "|%,15.2f|",
        1234567.5
);
System.out.println(amount);
// |   1,234,567.50|
```

Giải thích `%,15.2f`:

1. `,` bật phân nhóm chữ số phần nguyên.
2. `15` yêu cầu tối thiểu 15 vị trí ký tự.
3. `.2` quy định hiển thị hai chữ số sau dấu thập phân.
4. `f` chọn dạng số thực thập phân cố định.

**Đừng nhầm `%.2f` với `%.2s`:** ở `%f`, precision nói về số chữ số sau dấu thập phân; ở `%s`, precision giới hạn độ dài chuỗi được hiển thị. Với `%d`, cú pháp `%.2d` không phải cách hợp lệ để đệm hai số không trong Java Formatter. Hãy dùng `%02d` khi muốn chiều rộng hai và đệm số không.

Khi định dạng giá trị tiền tệ, cần xác định cả loại số và yêu cầu làm tròn. `String.format` tạo **biểu diễn văn bản**, không biến `double` thành kiểu số thập phân chính xác để tính toán tiền. Đối với phép tính tiền cần độ chính xác thập phân, dùng kiểu và quy tắc làm tròn phù hợp (thường là `BigDecimal`), sau đó mới định dạng để hiển thị.

## 7. Locale quyết định dấu phân nhóm và dấu thập phân

Sử dụng cùng format `%,12.2f` và cùng số `1234567.5`:

```java
System.out.printf(Locale.US, "|%,12.2f|%n", 1234567.5);
System.out.printf(Locale.GERMANY, "|%,12.2f|%n", 1234567.5);
```

Kết quả:

```text
|1,234,567.50|
|1.234.567,50|
```

Với `Locale.US`, dấu phẩy phân nhóm và dấu chấm ngăn phần thập phân. Với `Locale.GERMANY`, hai vai trò được đảo lại. Do đó, **không thể dựa vào ký tự `,` trong format để khẳng định kết quả luôn chứa dấu phẩy**.

Khi không truyền `Locale`, nhiều API định dạng dùng locale mặc định của môi trường chạy. Điều này có thể khiến cùng mã hiển thị khác nhau trên máy lập trình viên, máy CI và máy người dùng. Khi cần output ổn định (ví dụ snapshot test hoặc dữ liệu xuất có quy ước rõ), truyền `Locale` cụ thể:

```java
String stable = String.format(Locale.US, "%,.2f", 1234.5);
// "1,234.50"
```

Ngược lại, với giao diện dành cho người dùng cuối, lựa chọn `Locale` theo ngôn ngữ và khu vực của người dùng, thay vì cố định `Locale.US` cho mọi người.

Với các nhu cầu nâng cao như quy tắc tiền tệ, số chữ số nhóm khác nhau hay mẫu dấu âm riêng, có thể dùng `NumberFormat` hoặc `DecimalFormat`. Bài này tập trung vào cơ chế căn lề, đệm và phân nhóm bằng formatter string.

## 8. Ví dụ Java 17 chạy độc lập

Lưu toàn bộ nội dung dưới đây trong tệp `StringFormatLayoutDemo.java`:

```java
import java.util.Locale;

public class StringFormatLayoutDemo {
    public static void main(String[] args) {
        Locale us = Locale.US;

        System.out.printf(us, "string right: |%10s|%n", "Java");
        System.out.printf(us, "string left : |%-10s|%n", "Java");
        System.out.printf(us, "number right: |%8d|%n", 42);
        System.out.printf(us, "number left : |%-8d|%n", 42);
        System.out.printf(us, "zero padded : |%08d|%n", 42);
        System.out.printf(us, "sign + zeros: |%+08d|%n", 42);
        System.out.printf(us, "negative    : |%(08d|%n", -42);
        System.out.printf(us, "group       : |%,d|%n", 1234567);
        System.out.printf(us, "group width : |%,12d|%n", 1234567);
        System.out.printf(us, "group zeros : |%0,12d|%n", 1234567);
        System.out.printf(us, "decimal US  : |%,12.2f|%n", 1234567.5);
        System.out.printf(Locale.GERMANY,
                "decimal DE  : |%,12.2f|%n", 1234567.5);
        System.out.printf(us, "precision   : |%-12.5s|%n", "Formatting");
        System.out.printf(us, "long string : |%10s|%n", "VeryLongText");
        System.out.printf(us, "progress    : %d%%%n", 75);
    }
}
```

Biên dịch và chạy:

```bash
javac StringFormatLayoutDemo.java
java StringFormatLayoutDemo
```

Đầu ra chính xác (giữ nguyên khoảng trắng giữa `|`):

```text
string right: |      Java|
string left : |Java      |
number right: |      42|
number left : |42      |
zero padded : |00000042|
sign + zeros: |+0000042|
negative    : |(000042)|
group       : |1,234,567|
group width : |   1,234,567|
group zeros : |0001,234,567|
decimal US  : |1,234,567.50|
decimal DE  : |1.234.567,50|
precision   : |Forma       |
long string : |VeryLongText|
progress    : 75%
```

Ý nghĩa các dòng cần để ý:

- `string right`, `string left` so sánh hai phía nhận khoảng trắng.
- `zero padded`, `sign + zeros` và `negative` cho thấy cách dấu và dấu ngoặc tham gia tính chiều rộng.
- `group width` và `group zeros` minh họa rõ rằng **phân nhóm** và **đệm** là hai thao tác khác nhau.
- `decimal US` và `decimal DE` có cùng format nhưng khác `Locale`.
- `precision` dùng `%-12.5s`: chuỗi `"Formatting"` được hiển thị thành `"Forma"` (5 ký tự), rồi đệm khoảng trắng bên phải đến 12 ký tự.
- `long string` chứng minh `width` không cắt nội dung dài.
- `%%` in một dấu phần trăm thực, còn `%n` dùng để xuống dòng.

## 9. Ví dụ áp dụng: định dạng bảng console

Khi dựng bảng văn bản, đặt từng cột vào vùng có width cố định và chọn kiểu căn thích hợp:

```java
import java.util.Locale;

public class SalesTable {
    public static void main(String[] args) {
        System.out.printf(Locale.US,
                "| %-12s | %8s | %12s |%n",
                "Sản phẩm", "Số lượng", "Doanh thu");
        System.out.printf(Locale.US,
                "| %-12s | %,8d | %,12d |%n",
                "Java", 42, 1_234_567);
        System.out.printf(Locale.US,
                "| %-12s | %,8d | %,12d |%n",
                "Spring", 7, 12_500);
    }
}
```

Đây là một chương trình độc lập khác, lưu tên tệp `SalesTable.java` rồi chạy `javac SalesTable.java` và `java SalesTable`. Kết quả có cùng quy tắc căn lề: cột sản phẩm căn trái, cột số lượng/doanh thu căn phải và phân nhóm. Nếu tên sản phẩm hoặc số lớn vượt width, bảng có thể lệch vì Java không tự cắt nội dung.

Đối với output có tiếng Việt, emoji hoặc ký tự Đông Á, các ô có số ký tự tương đương chưa chắc có **độ rộng hình ảnh trên terminal** tương đương. Formatter kiểm soát số ký tự trong trường định dạng, không phải hệ thống bố trí cột theo độ rộng hiển thị của mọi font/terminal. Nếu cần bảng thật sự thẳng hàng với mọi kiểu Unicode, dùng thư viện dựng bảng hoặc giao diện có xử lý độ rộng ký tự.

## 10. Lỗi phổ biến

### 10.1. Dùng cờ không phù hợp với conversion

Các định dạng sau không phải cách viết đúng cho mục đích thường gặp:

- `%010s`: cờ `0` không dùng để đệm chuỗi. Đối với `%s`, mặc định chỉ có đệm khoảng trắng theo `width`.
- `%,s`: dấu `,` là cờ grouping cho conversion số phù hợp, không thể yêu cầu formatter phân nhóm một chuỗi tùy ý.
- `%.2d`: với số nguyên, hãy dùng `%02d` nếu cần đệm `0` đến width 2.
- `%0d`: có cờ `0` nhưng không có width.
- `%-08d`: trộn căn trái `-` với đệm số không `0`.

Những format sai có thể gây một ngoại lệ thuộc họ `IllegalFormatException` khi chạy, ví dụ `FormatFlagsConversionMismatchException`, `MissingFormatWidthException` hoặc `IllegalFormatPrecisionException`. Nếu format string được ghép động, cần kiểm tra trước hoặc xử lý lỗi phù hợp.

### 10.2. Nhầm chiều rộng với số chữ số

Với `%08d` và số `-42`, dấu trừ cũng chiếm chỗ trong tổng chiều rộng; kết quả sẽ có 8 ký tự, không phải 8 chữ số rồi thêm dấu trừ. Tương tự, dấu phẩy phân nhóm và dấu chấm thập phân **đều chiếm vị trí** trong vùng width.

### 10.3. Dùng dấu phân nhóm như một định dạng dữ liệu máy

`1,234,567` và `1.234.567` phù hợp để **người đọc** theo từng locale, nhưng không phải định dạng số trung lập để truyền giữa các hệ thống. Nếu cần dữ liệu số để máy xử lý, lưu giá trị bằng kiểu số/định dạng trao đổi được quy định rõ, sau đó chỉ phân nhóm trong lớp trình bày.

### 10.4. Nhầm ngôn ngữ hiển thị với căn lề

`Locale` thay đổi cách dùng dấu phân nhóm và dấu thập phân, nhưng không tự chọn nên căn trái hay căn phải. Những điều này thuộc hai nhóm tham số khác nhau: **flags/width** quy định căn và đệm, **Locale** quy định cách hiển thị số.

### 10.5. Đưa chuỗi do người dùng nhập làm format string

Nếu người dùng nhập ký tự `%`, hãy truyền dữ liệu đó làm **đối số**, không coi nó là format:

```java
String userText = "Sale 50%";
String safe = String.format("%s", userText);
```

Nếu dùng `String.format(userText)`, các dấu `%` trong dữ liệu có thể bị hiểu thành ký hiệu điều khiển định dạng và gây lỗi hoặc kết quả không mong muốn. Quy tắc chung: format string do ứng dụng kiểm soát; dữ liệu từ bên ngoài đi vào danh sách đối số.

## 11. Bài tập tự kiểm tra

Hãy dự đoán nội dung chính xác giữa hai ký tự `|`:

| Biểu thức (với `Locale.US` nếu có số) | Kết quả |
| --- | --- |
| `String.format("\|%6s\|", "Hi")` | `\|    Hi\|` |
| `String.format("\|%-6s\|", "Hi")` | `\|Hi    \|` |
| `String.format("\|%05d\|", 7)` | `\|00007\|` |
| `String.format("\|%+05d\|", 7)` | `\|+0007\|` |
| `String.format(Locale.US, "\|%,d\|", 123456)` | `\|123,456\|` |
| `String.format(Locale.US, "\|%,10d\|", 123456)` | `\|   123,456\|` |
| `String.format(Locale.US, "\|%,10.2f\|", 1234.5)` | `\|  1,234.50\|` |

Các dấu `\|` chỉ dùng để biểu diễn ranh giới trong bảng Markdown. Khi viết **mã Java thực tế**, dùng ký tự `|` trực tiếp trong chuỗi Java như `"|%6s|"`; dấu `\` trước `|` ở bảng chỉ để Markdown không tách nhầm cột.

Thử sửa `width` từ 10 thành 4, thay số `123456` thành `123456789`, rồi chạy lại. Quan sát vì sao kết quả có thể vượt width mà không mất chữ số. Tiếp theo, thử đổi `Locale.US` thành `Locale.GERMANY` và kiểm tra dấu phân nhóm đã thay đổi ra sao.

## 12. Ghi nhớ

- `%10s`, `%8d`: **căn phải** trong trường có chiều rộng tối thiểu; mặc định đệm khoảng trắng bên trái.
- `%-10s`, `%-8d`: **căn trái**; đệm khoảng trắng bên phải.
- `%08d`: **đệm số không**, thường dùng cho giá trị số có biểu diễn độ rộng cố định.
- `%,d`: **phân nhóm chữ số**; ký tự phân nhóm thực tế do `Locale` quyết định.
- `%,12d`, `%0,12d`, `%,15.2f`: có thể kết hợp grouping với width, đệm hoặc precision nếu conversion hỗ trợ.
- `width` là **độ rộng tối thiểu, không cắt nội dung**; `precision` có ý nghĩa khác nhau giữa `%s` và `%f`.
- Truyền `Locale` tường minh khi đầu ra cần ổn định. Dùng `%n` để xuống dòng và `%%` để in dấu phần trăm literal.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-formatting-005">%% cho dấu phần trăm và %n cho kết thúc dòng</a>

<details>
<summary>Xem chi tiết</summary>

# `%%` và `%n` khi định dạng chuỗi trong Java

## Mục tiêu

Khi sử dụng `String.format(... )`, `String.formatted(... )` hoặc `System.out.printf(... )`, bạn cần biết cách in dấu phần trăm thật và xuống dòng đúng nền tảng:

- `%%` biểu diễn **một dấu `%` trong kết quả**.
- `%n` biểu diễn **một ký tự hoặc chuỗi ký tự kết thúc dòng theo hệ điều hành**.
- Cả hai đều là **conversion đặc biệt của `java.util.Formatter`** và **không tiêu thụ đối số** trong danh sách tham số định dạng.

Hai ký hiệu này chỉ được diễn giải khi chuỗi đóng vai trò **format string**. Chúng không phải cú pháp đặc biệt của mọi đối tượng `String`.

## 1. Vì sao có hai ký hiệu này?

Trong họ API dựa trên `Formatter`, dấu `%` thông thường mở đầu một chỉ thị định dạng: `%s` để chèn chuỗi, `%d` để chèn số nguyên, `%.2f` để định dạng số thực với hai chữ số thập phân. Vì dấu `%` đã có ý nghĩa điều khiển, muốn hiển thị nó theo nghĩa thông thường cần viết `%%`.

Tương tự, `%n` yêu cầu bộ định dạng thêm dấu kết thúc dòng thích hợp với môi trường thực thi, tiện cho việc tạo báo cáo nhiều dòng và in ra console.

| Mẫu trong format string | Ý nghĩa | Đối số cần cung cấp |
| --- | --- | --- |
| `%%` | Ghi ra một dấu `%` | Không |
| `%n` | Ghi ra dấu kết thúc dòng theo nền tảng | Không |
| `%d` | Chèn một số nguyên | Có |
| `%s` | Chèn biểu diễn chuỗi của một giá trị | Có |

Ví dụ: chuỗi mẫu `"Tiến độ: %d%%%n"` được đọc thành `%d` (giá trị số), `%%` (dấu phần trăm) và `%n` (kết thúc dòng). Ba dấu `%` liên tiếp trước chữ `n` ở đây là đúng cú pháp: hai dấu đầu thuộc `%%`, dấu thứ ba bắt đầu `%n`.

## 2. `%%` — in dấu phần trăm

Với `Formatter`, `%%` cho ra đúng một dấu `%`:

```java
String result = String.format("Đã xử lý 100%%");
System.out.println(result);
```

Kết quả:

```text
Đã xử lý 100%
```

Muốn ghép số phần trăm động, hãy đặt `%%` sau chỉ thị của số:

```java
int progress = 75;
System.out.printf("Tiến độ: %d%%%n", progress);
```

Kết quả là dòng `Tiến độ: 75%` rồi xuống dòng. `%d` lấy đối số `progress`, còn `%%` không lấy đối số nào. Nếu chỉ muốn chuỗi kết quả mà chưa in ra màn hình, dùng `String.format("Tiến độ: %d%%", progress)`.

**Phân biệt với chuỗi bình thường:** `System.out.print("50%%")` in nguyên hai dấu `%%`, vì `print` không phân tích format string. Trái lại, `System.out.printf("50%%")` in `50%`.

Đặc biệt, dấu `%` đơn lẻ trong format string thường gây lỗi định dạng vì `Formatter` đang chờ tên phép chuyển đổi tiếp theo. Cách viết `"100%"` vẫn hoàn toàn hợp lệ nếu chỉ đưa vào `print`/`println` như văn bản thuần.

## 3. `%n` — kết thúc dòng theo nền tảng

`%n` không in hai ký tự `%` và `n`. Trong một format string, nó tạo dấu kết thúc dòng giống giá trị do `System.lineSeparator()` cung cấp tại môi trường chạy. Thông thường, Unix/Linux/macOS dùng LF (`\n`); Windows dùng CRLF (`\r\n`).

```java
String report = String.format("Dòng thứ nhất%nDòng thứ hai");
System.out.print(report);
```

Kết quả hiển thị:

```text
Dòng thứ nhất
Dòng thứ hai
```

Có thể dùng nhiều `%n` liên tiếp khi **chủ ý** tạo dòng trống:

```java
System.out.printf("Mục A%n%nMục B%n");
```

Kết quả:

```text
Mục A

Mục B
```

**So sánh với `\n` và `println`:**

- `\n` là escape sequence của *Java string literal*: trình biên dịch Java biến nó thành ký tự xuống dòng LF. Nó được hiểu ngay cả khi không dùng `Formatter`.
- `%n` là chỉ thị của *format string*: chỉ được biến thành kết thúc dòng khi đi qua `Formatter`, thường là lựa chọn tốt cho định dạng văn bản theo nền tảng.
- `println(... )` tự thêm kết thúc dòng sau giá trị được in. Nếu giá trị đã kết thúc bằng `%n` qua `String.format` rồi lại gọi `println`, sẽ có thêm một dòng trống ngoài ý muốn.
- `System.out.print("%n")` in nguyên văn `%n`. Muốn nó xuống dòng, gọi `System.out.printf("%n")` hoặc dùng `println()`.

Không nên giả định kết quả `%n` luôn có đúng một ký tự: trên Windows dấu kết thúc dòng thường gồm hai ký tự `\r\n`. Điều này quan trọng nếu bạn so sánh chuỗi theo byte hoặc kiểm thử văn bản đa nền tảng.

## 4. Ví dụ Java 17+ chạy hoàn chỉnh

Tạo tệp `FormatTokensDemo.java`:

```java
public class FormatTokensDemo {
    public static void main(String[] args) {
        int completed = 7;
        int total = 10;
        int percentage = completed * 100 / total;

        // %d nhận số; %% in dấu %; %n kết thúc dòng.
        System.out.printf(
                "Hoàn thành: %d/%d (%d%%)%n",
                completed, total, percentage
        );

        // String.format tạo chuỗi; println thêm xuống dòng cuối cùng.
        String report = String.format(
                "Giảm giá: 15%%%nĐơn hàng đã ghi nhận"
        );
        System.out.println(report);

        // print không diễn giải %% hoặc %n.
        System.out.print("print thông thường: 50%% và %n");
        System.out.println();
    }
}
```

Biên dịch và chạy:

```bash
javac FormatTokensDemo.java
java FormatTokensDemo
```

Kết quả hiển thị (các dòng như nhau trên mọi nền tảng; byte kết thúc dòng có thể khác):

```text
Hoàn thành: 7/10 (70%)
Giảm giá: 15%
Đơn hàng đã ghi nhận
print thông thường: 50%% và %n
```

Giải thích: biểu thức `completed * 100 / total` cho `70` bằng phép tính số nguyên; `%d` đưa số đó vào chuỗi. Cặp `%%` sau `%d` tạo dấu phần trăm, `%n` khép dòng. Trong `report`, `15%%%n` tương ứng `15%` rồi xuống dòng trước câu tiếp theo. `print` ở cuối chỉ xuất các ký tự theo đúng giá trị chuỗi truyền vào.

Khi đã có sẵn format string trong biến, bạn cũng có thể gọi `template.formatted(args)` (Java 15+); ký hiệu `%%` và `%n` vẫn có cùng ý nghĩa vì phương thức này dùng cơ chế định dạng kiểu `String.format`.

## 5. Quy tắc kết hợp và những nhầm lẫn thường gặp

| Mong muốn | Mẫu khi dùng `printf`/`String.format` | Kết quả |
| --- | --- | --- |
| In `100%` | `"100%%"` | `100%` |
| In `80%` từ số nguyên 80 | `"%d%%"` và đối số `80` | `80%` |
| In `80%` rồi kết thúc dòng | `"%d%%%n"` và đối số `80` | `80%` rồi xuống dòng |
| In hai dòng | `"A%nB"` | `A` và `B` trên hai dòng |
| In chữ `%n` theo nghĩa đen qua Formatter | `"%%n"` | `%n` |

Một số lỗi cần tránh:

1. **Thiếu dấu `%` thứ hai:** `System.out.printf("Hoàn thành 100%")` có format string không hợp lệ; phải dùng `"Hoàn thành 100%%"`. Khi chỉ in văn bản cố định, `System.out.println("Hoàn thành 100%")` cũng đúng.
2. **Nhầm với escape của Java:** `\%` không phải escape sequence hợp lệ trong Java string literal. Không dùng `"100\%"` để xử lý dấu phần trăm.
3. **Quên ngữ cảnh định dạng:** `"A%nB"` truyền trực tiếp vào `print` vẫn chứa ký tự `%n`. Chỉ truyền qua `printf`, `format` hoặc `formatted` mới diễn giải nó.
4. **Chèn xuống dòng hai lần:** `System.out.println(String.format("X%n"))` tạo một xuống dòng từ `%n` và một xuống dòng nữa từ `println`. Hãy chọn một trong hai khi chỉ muốn kết thúc một dòng.
5. **Gắn tùy chọn định dạng vào `%n`:** `%n` không nhận flags, width hay precision. Với `%%`, có thể chỉ định width và flag căn trái `-` (ví dụ `%5%` in dấu `%` trong ô rộng 5 ký tự), nhưng không dùng precision như định dạng số hoặc chuỗi.
6. **Cấp nhầm đối số:** `%%` và `%n` không lấy phần tử nào trong danh sách đối số; chỉ `%d`, `%s`… mới sử dụng đối số tương ứng. Ví dụ, `String.format("%s: 100%%%n", "Tải xuống")` chỉ cần một đối số là `"Tải xuống"`.

## 6. Tự kiểm tra

**Câu 1.** Đoạn mã sau hiển thị gì?

```java
System.out.printf("CPU: %d%%%n", 80);
```

Đáp án: in `CPU: 80%` rồi kết thúc dòng. `%d` nhận `80`; `%%` in dấu phần trăm; `%n` xuống dòng.

**Câu 2.** Vì sao hai lời gọi dưới đây cho kết quả khác nhau?

```java
System.out.print("X%nY");
System.out.printf("X%nY");
```

Đáp án: `print` hiển thị nguyên văn `X%nY`; `printf` diễn giải `%n` và hiển thị `X`, `Y` trên hai dòng. Do lời gọi `print` không tự xuống dòng, nếu chạy liên tiếp như trên thì ký tự `X` của `printf` nối ngay sau chữ `Y` đã được `print` in ra.

## Ghi nhớ

Trong **format string**, dùng `%%` để tạo dấu phần trăm hiển thị và `%n` để tạo dấu kết thúc dòng tương thích nền tảng. Chúng không chiếm đối số định dạng. Trong chuỗi được đưa thẳng tới `print`/`println`, hai ký hiệu giữ nguyên vì không có bước phân tích định dạng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-formatting-006">Locale mặc định so với String.format(Locale,...)</a>

<details>
<summary>Xem chi tiết</summary>

# Locale mặc định so với `String.format(Locale, ...)` trong Java

Khi định dạng chuỗi có chứa số, ngày giờ hoặc nội dung phụ thuộc ngôn ngữ, cùng một lời gọi `String.format` có thể tạo ra kết quả khác nhau ở các môi trường chạy. Nguyên nhân thường là **locale**: tập quy ước ngôn ngữ và vùng được áp dụng cho việc định dạng. Bài này phân biệt hai overload của `String.format`, giải thích locale mặc định và cách chọn locale tường minh để có kết quả đúng mục đích.

## 1. Locale ảnh hưởng đến định dạng như thế nào?

`java.util.Locale` mô tả các quy ước theo ngôn ngữ/vùng, chẳng hạn `Locale.US` (Mỹ), `Locale.GERMANY` (Đức) hoặc `Locale.forLanguageTag("vi-VN")` (tiếng Việt tại Việt Nam). Khi áp dụng vào `Formatter`, locale có thể chi phối:

- Dấu phân cách thập phân và dấu phân nhóm chữ số khi dùng conversion/flag tương ứng (`%f`, `%,d`, `%,.2f`, ...).
- Tên tháng, tên thứ và một số biểu diễn ngày giờ bằng `%t`/`%T`.
- Việc đổi thành chữ hoa ở một số conversion như `%S` theo quy tắc locale.

Locale **không tự dịch** phần chữ viết cố định trong mẫu. Chẳng hạn `"Total: %,.2f"` vẫn có nhãn `Total:` với mọi locale; sự khác biệt nằm ở phần số được chèn vào. Locale cũng không tự thay đổi giá trị số đầu vào, kiểu dữ liệu hay múi giờ của đối tượng ngày giờ.

## 2. Hai overload của `String.format`

~~~java
String format(String format, Object... args)
String format(Locale locale, String format, Object... args)
~~~

Đây là hai phương thức `static` của `java.lang.String`:

| Lời gọi | Locale được sử dụng | Phù hợp khi |
| --- | --- | --- |
| `String.format("... ", args)` | Locale mặc định thuộc `Locale.Category.FORMAT` của JVM tại lúc gọi | Kết quả hướng tới người dùng theo cấu hình định dạng mặc định của ứng dụng |
| `String.format(Locale.US, "...", args)` | Chính locale được truyền vào | Muốn quy ước Mỹ ổn định dù JVM chạy ở máy nào |
| `String.format(selectedLocale, "...", args)` | Locale được chọn theo người dùng/yêu cầu | Giao diện phục vụ nhiều ngôn ngữ/vùng |
| `String.format(Locale.ROOT, "...", args)` | Locale trung lập (root locale) | Định dạng không cần quy ước vùng cụ thể, khi phù hợp với giao thức/đặc tả |

**Điểm then chốt:** overload không có tham số `Locale` dùng `Locale.getDefault(Locale.Category.FORMAT)`. Không nên ngầm đồng nhất giá trị này với `Locale.getDefault()` trong mọi tình huống, vì Java cho phép cấu hình mặc định riêng theo từng `Locale.Category`.

Cú pháp mẫu vẫn giống nhau. Ví dụ, `%,.2f` yêu cầu số thực có dấu phân nhóm (`,` flag) và đúng hai chữ số phần thập phân (`.2` precision); **dấu thực tế trong kết quả được quyết định bởi locale**, không nhất thiết là ký tự dấu phẩy/dấu chấm của mẫu.

## 3. Ví dụ chạy được: mặc định so với locale tường minh

Tạo tệp `FormatLocaleDemo.java`:

~~~java
import java.util.Locale;

public class FormatLocaleDemo {
    public static void main(String[] args) {
        Locale previous = Locale.getDefault(Locale.Category.FORMAT);

        try {
            // Cố định locale mặc định để kết quả ví dụ có thể kiểm chứng.
            Locale.setDefault(Locale.Category.FORMAT, Locale.GERMANY);

            double amount = 12345.67;
            System.out.println(String.format("Default: %,.2f", amount));
            System.out.println(String.format(Locale.US, "US: %,.2f", amount));
            System.out.println(String.format(Locale.GERMANY, "Germany: %,.2f", amount));
            System.out.println(String.format(Locale.US, "US without grouping: %.2f", amount));
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, previous);
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac FormatLocaleDemo.java
java FormatLocaleDemo
~~~

Kết quả:

~~~text
Default: 12.345,67
US: 12,345.67
Germany: 12.345,67
US without grouping: 12345.67
~~~

Giải thích từng dòng:

1. `Default` dùng overload thiếu `Locale` nên lấy mặc định `FORMAT` đã tạm chuyển sang `Locale.GERMANY`: phân nhóm bằng dấu chấm, phần thập phân bằng dấu phẩy.
2. `US` truyền `Locale.US` trực tiếp nên phân nhóm bằng dấu phẩy, phần thập phân bằng dấu chấm, bất kể mặc định JVM đang là Đức.
3. `Germany` minh họa truyền tường minh locale Đức: kết quả trùng dòng đầu trong chương trình này nhưng không phụ thuộc mặc định.
4. `US without grouping` bỏ flag `,`: không thêm dấu phân nhóm; vẫn giữ hai chữ số thập phân nhờ `.2f`.

Trong ví dụ, `try/finally` khôi phục thiết lập trước khi chạy để minh họa an toàn hơn. Tuy vậy, thay đổi locale mặc định vẫn tác động **toàn tiến trình JVM** trong khoảng thời gian giữa hai lời gọi `setDefault`. Không dùng kỹ thuật thay đổi mặc định theo từng request trong server đa luồng: request đồng thời có thể quan sát cấu hình của nhau. Trong ứng dụng thực tế, hãy truyền `Locale` trực tiếp vào mỗi phép định dạng khi cần chọn quy ước cụ thể.

## 4. Mặc định FORMAT khác mặc định DISPLAY

Java phân biệt ít nhất hai nhu cầu:

- `Locale.Category.FORMAT` dành cho định dạng số, ngày giờ và dữ liệu nhạy với quy ước vùng.
- `Locale.Category.DISPLAY` dành cho cách hiển thị tên ngôn ngữ, quốc gia và các thông tin địa phương hóa khác.

Có thể đọc chúng riêng:

~~~java
Locale formatting = Locale.getDefault(Locale.Category.FORMAT);
Locale displaying = Locale.getDefault(Locale.Category.DISPLAY);
Locale general = Locale.getDefault();
~~~

Đây là **đoạn minh họa API**, không phải chương trình độc lập. Sau khi một ứng dụng tùy chỉnh từng category, `formatting` và `displaying` có thể khác nhau. `String.format(String, ...)` lấy category `FORMAT`; hãy kiểm tra chính category này khi điều tra vì sao kết quả định dạng thay đổi.

Locale mặc định ban đầu thường xuất phát từ cấu hình môi trường/JVM; có thể đổi bằng API `Locale.setDefault(...)`. Vì vậy không được suy luận rằng cứ chạy trên Windows, Linux hay một máy chủ cụ thể là mặc định sẽ luôn là `Locale.US`.

## 5. Chọn locale theo mục đích sử dụng

### Kết quả để người dùng đọc

Nếu người dùng đã chọn ngôn ngữ/vùng, dùng đúng locale của họ khi hiển thị:

~~~java
Locale viewerLocale = Locale.forLanguageTag("de-DE");
String displayed = String.format(viewerLocale, "Preis: %,.2f", 1234.5);
~~~

Đoạn này minh họa mã sử dụng trong ứng dụng, không phải chương trình đầy đủ. Kết quả có định dạng số theo quy ước Đức nhưng chữ `Preis:` là phần mẫu do lập trình viên cung cấp. Để bản địa hóa chính nhãn hoặc toàn bộ thông điệp, cần thêm cơ chế tài nguyên đa ngôn ngữ (chẳng hạn `ResourceBundle`).

### Kết quả máy cần đọc ổn định

Khi định dạng số cho khóa dữ liệu, đoạn văn bản kiểm thử, hoặc một giao thức **quy định** dấu chấm thập phân, hãy chỉ định locale phù hợp thay vì phụ thuộc hệ điều hành:

~~~java
String value = String.format(Locale.US, "%.2f", 12.5);
// value: "12.50"
~~~

Đây là đoạn mã minh họa độc lập về biểu thức. Phải tuân thủ định dạng chính thức của giao thức: nhiều giao thức nên dùng API serialize chuyên dụng (ví dụ thư viện JSON hoặc chuẩn số tương ứng), thay vì tùy tiện ghép chuỗi `String.format`. `Locale.ROOT` có thể hữu ích cho một số thao tác trung lập về ngôn ngữ nhưng không tự động là định dạng đúng của mọi giao thức.

### Không chỉnh global default theo mỗi request

~~~java
// Tránh trong web server xử lý nhiều request đồng thời:
Locale.setDefault(Locale.Category.FORMAT, requestedLocale);
String output = String.format("%,.2f", value);

// Ưu tiên:
String safeOutput = String.format(requestedLocale, "%,.2f", value);
~~~

Hai đoạn trên chỉ minh họa lựa chọn API trong ngữ cảnh đã có `requestedLocale` và `value`. Cách thứ hai làm rõ phụ thuộc ngay tại điểm sử dụng, thuận tiện cho review và kiểm thử.

## 6. Các ký hiệu định dạng thường gặp

| Mẫu | Ý nghĩa | Ghi chú về locale |
| --- | --- | --- |
| `%s` | Biểu diễn đối tượng thành chuỗi | Không tự dịch nội dung của chuỗi |
| `%S` | Như `%s` nhưng chuyển chữ hoa | Quy tắc chuyển hoa có thể phụ thuộc locale |
| `%d` | Số nguyên dạng thập phân | Có thể dùng chữ số theo hệ chữ số của locale |
| `%,d` | Số nguyên có phân nhóm hàng nghìn | Dấu phân nhóm tùy locale |
| `%.2f` | Số thực, hai chữ số phần thập phân | Dấu thập phân tùy locale |
| `%,.2f` | Số thực có phân nhóm và hai chữ số thập phân | Cả dấu nhóm và dấu thập phân tùy locale |
| `%1$tB` | Tên tháng đầy đủ của đối số thứ nhất | Tên tháng phụ thuộc locale |
| `%%` | Ký tự `%` | Không sử dụng đối số |
| `%n` | Dấu xuống dòng theo nền tảng | Do nền tảng, không phải lựa chọn theo locale |

Ví dụ các conversion ngày tháng (đoạn mã sử dụng trong một phương thức):

~~~java
java.time.LocalDate date = java.time.LocalDate.of(2026, 10, 23);
String usMonth = String.format(Locale.US, "%1$tB", date);
String germanMonth = String.format(Locale.GERMANY, "%1$tB", date);
~~~

`%1$tB` nghĩa là lấy tên tháng đầy đủ từ đối số số 1. Locale điều khiển cách thể hiện tên tháng; bản thân ngày `2026-10-23` không thay đổi. Các đối số thời gian có thông tin múi giờ cần được xử lý đúng kiểu và múi giờ riêng; chuyển `Locale` không đồng nghĩa chuyển múi giờ.

## 7. `Locale.ROOT`, `null` và phạm vi không được hiểu nhầm

- `Locale.ROOT` là locale trung lập dùng khi không muốn chọn riêng một quốc gia/ngôn ngữ. Tuy nhiên, việc định dạng vẫn theo quy tắc của `Formatter` và không phải mọi cách thể hiện đều phù hợp với chuẩn dữ liệu bên ngoài.
- Overload `String.format(Locale, String, Object...)` cũng cho phép tham số locale là `null`: khi đó **không áp dụng bản địa hóa** theo đặc tả `Formatter`. Điều này khác về ý nghĩa API với “lấy locale mặc định”; nên ưu tiên truyền locale rõ ràng khi yêu cầu định dạng đã xác định.
- `String.format` dùng để **tạo chuỗi**, không dùng để phân tích ngược chuỗi theo locale. Nếu cần đọc giá trị dạng `"12.345,67"`, xem xét `NumberFormat` với locale phù hợp và kiểm tra việc parse có tiêu thụ hết chuỗi đầu vào hay không.
- Với tiền tệ, đừng coi `%,.2f` là định dạng tiền hoàn chỉnh: ký hiệu, vị trí ký hiệu, số chữ số thập phân và cách làm tròn theo tiền tệ cần `NumberFormat.getCurrencyInstance(locale)` và kiểu số phù hợp (thường `BigDecimal` cho phép tính chính xác theo thập phân).
- Một số dấu phân nhóm (nhất là dấu cách và biến thể Unicode của nó) có thể khác theo locale/dữ liệu bản địa hóa của JDK. Vì vậy, kiểm thử đầu ra bằng locale cụ thể và tránh đoán dấu cách bằng mắt thường.

## 8. Lỗi thường gặp và cách khắc phục

**Lỗi 1 — Kết quả khác giữa máy phát triển và CI.** Mã dùng `String.format("%,.2f", amount)` nhưng kỳ vọng dấu thập phân luôn là `.`. Nguyên nhân: ngầm dùng locale mặc định `FORMAT`. Khắc phục: truyền `Locale.US` (hoặc locale được đặc tả) ở ngay lời gọi.

**Lỗi 2 — Đặt `Locale.setDefault` để phục vụ một người dùng.** Cấu hình được thay đổi cho toàn JVM, làm những request chạy song song ảnh hưởng nhau. Khắc phục: truyền `Locale` theo từng request, rồi truyền tiếp vào hàm định dạng.

**Lỗi 3 — Nhầm `%s` với bản địa hóa thông điệp.** `String.format(Locale.GERMANY, "Total: %s", "OK")` không dịch `Total` hoặc `OK`. Khắc phục: quản lý các thông điệp/nội dung dịch theo locale bằng resource bundle hoặc cơ chế tương đương.

**Lỗi 4 — So sánh chuỗi số đã định dạng để tính toán.** `"1.234,50"` được thiết kế cho hiển thị, không thể đưa trực tiếp vào `Double.parseDouble`. Khắc phục: giữ dữ liệu là số trong xử lý; chỉ format tại biên hiển thị và dùng parser đúng locale khi nhận đầu vào.

**Lỗi 5 — Nhầm locale với timezone.** `Locale.US` có thể cho tên tháng tiếng Anh, nhưng không tự chuyển thời điểm sang giờ Mỹ. Khắc phục: xác định `ZoneId`/`ZonedDateTime` hoặc múi giờ yêu cầu một cách tường minh.

**Lỗi 6 — Format specifier sai loại.** Ví dụ `String.format(Locale.US, "%d", 12.5)` gây `IllegalFormatConversionException` vì `%d` không nhận số thực `double`. Đặt locale khác không sửa được lỗi kiểu; cần chọn conversion phù hợp như `%.1f`.

## 9. Tự kiểm tra

**Câu 1.** Nếu mặc định `FORMAT` là `Locale.GERMANY`, dự đoán kết quả:

~~~java
String a = String.format("%,.2f", 1234.5);
String b = String.format(Locale.US, "%,.2f", 1234.5);
~~~

**Đáp án:** `a` là `"1.234,50"`; `b` là `"1,234.50"`.

**Câu 2.** Một API yêu cầu số thập phân dùng dấu chấm bất kể vùng địa lý của máy chủ. Nên chọn overload nào?

**Đáp án:** overload nhận `Locale`, ví dụ `String.format(Locale.US, "%.2f", value)` khi định dạng đó phù hợp với đặc tả API. Với JSON hoặc giao thức có serializer chuẩn, nên dùng serializer theo chuẩn đó.

**Câu 3.** Việc gọi `Locale.setDefault(Locale.Category.DISPLAY, Locale.US)` có bảo đảm `String.format("%,.2f", value)` dùng dấu phân cách Mỹ không?

**Đáp án:** Không. `String.format` mặc định xét `Locale.Category.FORMAT`; thay đổi riêng `DISPLAY` không xác lập `FORMAT`.

## 10. Ghi nhớ để áp dụng

Khi đọc một lời gọi `String.format`, hãy hỏi: **locale lấy từ đâu?** Nếu thiếu tham số locale, kết quả định dạng có thể phụ thuộc mặc định `FORMAT` của JVM. Nếu có `Locale` tường minh, hành vi bản địa hóa được kiểm soát tại lời gọi. Thiết kế tốt thường truyền locale người dùng khi hiển thị, truyền locale cố định theo đặc tả khi xuất dữ liệu máy đọc, và tránh thay đổi locale mặc định ở các luồng xử lý đồng thời.

Tài liệu API tham khảo: [`String.format` (Java SE 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html#format(java.util.Locale,java.lang.String,java.lang.Object...)), [`Formatter` (Java SE 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Formatter.html) và [`Locale` (Java SE 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Locale.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-formatting-007">Đối số null và các IllegalFormatException thường gặp</a>

<details>
<summary>Xem chi tiết</summary>

# Đối số `null` và các `IllegalFormatException` thường gặp trong Java

## Mục tiêu

Sau bài này, bạn có thể dự đoán cách `String.format()` xử lý đối số `null`, phân biệt lỗi do giá trị đối số với lỗi do chuỗi định dạng, nhận diện các ngoại lệ định dạng thường gặp và chọn cách xử lý thích hợp khi tạo thông báo, log hoặc chuỗi hiển thị.

## 1. Vì sao định dạng chuỗi có thể thất bại?

`String.format()`, `System.out.printf()`, `Formatter.format()` và `String.formatted()` sử dụng các quy tắc của `java.util.Formatter`. Một đặc tả thường có dạng:

```text
%[argument_index$][flags][width][.precision]conversion
```

Ví dụ: `%2$-10.3s` lấy đối số thứ hai, canh trái trong độ rộng tối thiểu 10 và lấy tối đa 3 ký tự chuỗi. `%d` dành cho số nguyên, `%f` dành cho số thực, `%s` biểu diễn chuỗi của đối tượng, `%b` biểu diễn logic; `%tY` định dạng năm của giá trị ngày/giờ. Hai đặc tả `%%` (ký tự phần trăm) và `%n` (dấu xuống dòng theo nền tảng) không tiêu thụ đối số.

`Formatter` kiểm tra cú pháp, cờ, độ rộng, độ chính xác, chỉ số đối số và tính tương thích của kiểu dữ liệu. Nếu không hợp lệ, nó ném một lớp con của `IllegalFormatException` (ngoại lệ unchecked, thuộc họ `IllegalArgumentException`). Trường hợp riêng là chuỗi format bằng `null`: ví dụ `String.format((String) null, "x")` ném `NullPointerException`, không phải `IllegalFormatException`.

## 2. Đối số `null`: kết quả cụ thể

Một hiểu lầm phổ biến là mọi đối số `null` đều gây `NullPointerException` hoặc `IllegalFormatConversionException`. Theo đặc tả `Formatter`, đối với các phép chuyển đổi tổng quát, ký tự, số và ngày/giờ, `null` thường được biểu diễn bằng chữ `null`; `%b`/`%B` là ngoại lệ đáng chú ý vì `null` được biểu diễn thành `false`/`FALSE`.

| Đặc tả | Đối số thực sự là `null` | Kết quả |
| --- | --- | --- |
| `%s` / `%S` | `(Object) null` | `null` / `NULL` |
| `%b` / `%B` | `(Object) null` | `false` / `FALSE` |
| `%h` / `%H` | `(Object) null` | `null` / `NULL` |
| `%d`, `%f`, `%c`, `%tY` | `(Object) null` | `null` |

Ví dụ đầy đủ, chạy được trên Java 17+:

```java
import java.util.Locale;

public class FormatterNullDemo {
    public static void main(String[] args) {
        Object absent = null;

        System.out.println("string=[" + String.format(Locale.ROOT, "%s", absent) + "]");
        System.out.println("boolean=[" + String.format(Locale.ROOT, "%b", absent) + "]");
        System.out.println("hash=[" + String.format(Locale.ROOT, "%h", absent) + "]");
        System.out.println("integer=[" + String.format(Locale.ROOT, "%d", absent) + "]");
        System.out.println("decimal=[" + String.format(Locale.ROOT, "%f", absent) + "]");
        System.out.println("char=[" + String.format(Locale.ROOT, "%c", absent) + "]");
        System.out.println("year=[" + String.format(Locale.ROOT, "%tY", absent) + "]");
        System.out.println("uppercase=[" + String.format(Locale.ROOT, "%S", absent) + "]");
    }
}
```

Lưu dưới tên `FormatterNullDemo.java`, rồi chạy:

```bash
javac FormatterNullDemo.java
java FormatterNullDemo
```

Kết quả:

```text
string=[null]
boolean=[false]
hash=[null]
integer=[null]
decimal=[null]
char=[null]
year=[null]
uppercase=[NULL]
```

Cần phân biệt ba điều:

- `null` **không phải** một số nguyên, số thực hay ngày hợp lệ; `Formatter` dùng quy tắc biểu diễn đặc biệt cho giá trị vắng mặt. Giá trị **khác `null` nhưng sai kiểu** vẫn có thể gây lỗi: `String.format("%d", "42")` ném `IllegalFormatConversionException` vì `"42"` là `String`, không phải kiểu số nguyên được `%d` chấp nhận.
- `%b` không thực hiện ép kiểu chuỗi sang boolean. Với đối số `Boolean.TRUE`, kết quả là `true`; với `Boolean.FALSE` hoặc `null`, kết quả là `false`; **mọi đối tượng khác `null` mà không thuộc kiểu `Boolean`** cũng được biểu diễn thành `true`. Vì vậy `String.format("%b", "false")` cho ra `true`.
- `null` có thể che giấu dữ liệu bị thiếu. Ví dụ một cột số tiền hiển thị `null` hoặc cờ tùy chọn hiển thị `false` không chứng minh dữ liệu nguồn hợp lệ.

### Lưu ý varargs: `null` đơn lẻ và mảng đối số `null`

Chữ ký `String.format(String format, Object... args)` sử dụng varargs. Khi cần **một đối số** có giá trị `null`, viết rõ `(Object) null` hoặc truyền một biến `Object` bằng `null`:

```java
String oneNull = String.format("%s", (Object) null);
String twoNulls = String.format("%s|%s", (Object[]) null);
```

`oneNull` là `"null"`. Trong `Formatter`, mảng varargs `null` ở ví dụ thứ hai được xử lý như các đối số đều `null`, nên `twoNulls` là `"null|null"`. Dù kết quả trông tương tự, **`(Object) null` và `(Object[]) null` không biểu đạt cùng một ý định ở cấp lời gọi**. Viết `String.format("%s", null)` trực tiếp có thể tạo cảnh báo của trình biên dịch về lời gọi varargs không rõ ràng; ép kiểu hoặc dùng biến rõ ràng giúp loại bỏ sự nhập nhằng.

## 3. Nhận diện các `IllegalFormatException` thường gặp

Các lớp dưới đây thuộc `java.util`; tên lớp con thường cho biết phần nào của đặc tả sai. Bảng dùng các ví dụ cố ý thất bại, độc lập với nhau.

| Ngoại lệ | Ví dụ | Nguyên nhân và hướng sửa |
| --- | --- | --- |
| `IllegalFormatConversionException` | `String.format("%d", "42")` | Chuyển đổi không chấp nhận kiểu đối số; đổi sang số `42` hoặc sử dụng `%s`. |
| `MissingFormatArgumentException` | `String.format("%s %s", "A")` | Đặc tả cần thêm đối số; truyền đủ hoặc sửa số placeholder. |
| `UnknownFormatConversionException` | `String.format("%q", 1)` | `q` không phải ký tự chuyển đổi hợp lệ; dùng `%s` hoặc loại hợp lệ khác. |
| `FormatFlagsConversionMismatchException` | `String.format("%#s", "A")` | Cờ `#` không được hỗ trợ cho `%s` với `String` thông thường; bỏ cờ. |
| `DuplicateFormatFlagsException` | `String.format("%--5s", "A")` | Cờ `-` bị lặp; đổi thành `%-5s`. |
| `IllegalFormatFlagsException` | `String.format("%-05d", 42)` | Hai cờ `-` và `0` xung đột; chỉ dùng một cách căn chỉnh. |
| `MissingFormatWidthException` | `String.format("%0d", 42)` | Cờ `0` cần độ rộng; ví dụ dùng `%05d`. |
| `IllegalFormatPrecisionException` | `String.format("%.2d", 42)` | `%d` không hỗ trợ precision; dùng `%d` hoặc tự định dạng giá trị trước. |
| `IllegalFormatWidthException` | `String.format("%5n")` | `%n` không chấp nhận width; dùng `%n`. |
| `IllegalFormatCodePointException` | `String.format("%c", 0x110000)` | Mã Unicode nằm ngoài phạm vi code point hợp lệ; kiểm tra bằng `Character.isValidCodePoint()`. |

Không nên cho rằng tên lớp ngoại lệ luôn thể hiện mọi chi tiết nguyên nhân: cùng một chuỗi định dạng có thể mắc nhiều lỗi và `Formatter` sẽ báo lỗi đầu tiên theo quá trình kiểm tra của nó. `IllegalFormatException` cũng có các lớp con ít gặp hơn như `UnknownFormatFlagsException`; không cần tự tạo các lớp ngoại lệ này khi chỉ định dạng chuỗi.

### Chạy thử và đọc đúng tên ngoại lệ

```java
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;

public class FormatErrorsDemo {
    private record Sample(String label, String format, Object[] args) {}

    public static void main(String[] args) {
        List<Sample> examples = List.of(
                new Sample("wrong-type", "%d", new Object[]{"42"}),
                new Sample("missing-arg", "%s %s", new Object[]{"A"}),
                new Sample("unknown-conversion", "%q", new Object[]{1}),
                new Sample("flag-mismatch", "%#s", new Object[]{"A"}),
                new Sample("duplicate-flag", "%--5s", new Object[]{"A"}),
                new Sample("conflicting-flags", "%-05d", new Object[]{42}),
                new Sample("missing-width", "%0d", new Object[]{42}),
                new Sample("invalid-precision", "%.2d", new Object[]{42}),
                new Sample("invalid-width", "%5n", new Object[]{}),
                new Sample("invalid-codepoint", "%c", new Object[]{0x110000})
        );

        for (Sample example : examples) {
            try {
                String.format(Locale.ROOT, example.format(), example.args());
                System.out.println(example.label() + " => OK");
            } catch (IllegalFormatException error) {
                System.out.println(example.label() + " => "
                        + error.getClass().getSimpleName());
            }
        }
    }
}
```

Lưu thành `FormatErrorsDemo.java`, biên dịch và chạy:

```bash
javac FormatErrorsDemo.java
java FormatErrorsDemo
```

Kết quả xác định:

```text
wrong-type => IllegalFormatConversionException
missing-arg => MissingFormatArgumentException
unknown-conversion => UnknownFormatConversionException
flag-mismatch => FormatFlagsConversionMismatchException
duplicate-flag => DuplicateFormatFlagsException
conflicting-flags => IllegalFormatFlagsException
missing-width => MissingFormatWidthException
invalid-precision => IllegalFormatPrecisionException
invalid-width => IllegalFormatWidthException
invalid-codepoint => IllegalFormatCodePointException
```

Chương trình trên chỉ bắt `IllegalFormatException` để phục vụ việc học. Trong ứng dụng thực tế, thường nên sửa chuỗi format ngay tại nơi viết code; không nên bắt rộng rồi âm thầm nuốt ngoại lệ khiến dữ liệu sai tiếp tục đi qua hệ thống.

## 4. Lỗi do đếm và chọn đối số

Chú ý `%s`, `%d`, `%f` đều tiêu thụ đối số; `%%` và `%n` thì không. `Formatter` có ba cách chọn đối số:

- **Tuần tự**: `"%s %d"` lần lượt dùng đối số 1 và 2.
- **Theo vị trí**: `"%2$s %1$s"` lấy đối số thứ hai trước, rồi thứ nhất; thứ tự chỉ số bắt đầu từ 1.
- **Tái dùng đối số trước**: `"%s %<s"` dùng một đối số hai lần; `%<s` đứng đầu, khi chưa có đối số trước đó, gây `MissingFormatArgumentException`.

`String.format("%s", "A", "B")` chỉ cho ra `"A"`: đối số thừa bị bỏ qua, **không** gây ngoại lệ. Ngược lại, `String.format("%2$s", "A")` ném `MissingFormatArgumentException` vì chỉ số thứ hai không tồn tại. Việc truyền `null` và việc **không truyền đủ đối số** là hai tình huống khác nhau; chỉ tình huống thứ hai tạo lỗi thiếu đối số.

Khi kiểm tra thông báo nhiều placeholder, hãy ghi số lượng, kiểu dữ liệu và chỉ số của từng đối số rồi so sánh với đặc tả. Nếu cần dấu `%` trong lời văn, dùng `%%`; nếu cần kết thúc dòng theo nền tảng, dùng `%n`; nếu đầu ra yêu cầu LF cố định, dùng ký tự `\n` trong chuỗi Java.

## 5. Cách xử lý an toàn trong code thực tế

### Xác định chính sách khi dữ liệu có thể `null`

Nếu `null` không hợp lệ về nghiệp vụ, kiểm tra nó trước khi định dạng thay vì để `Formatter` in chữ `null`:

```java
import java.util.Locale;
import java.util.Objects;

public class RequiredAmount {
    public static String label(Integer amount) {
        int present = Objects.requireNonNull(amount, "amount is required");
        return String.format(Locale.ROOT, "Amount: %d", present);
    }
}
```

Đây là phương thức minh họa (không có `main`). `Objects.requireNonNull` báo lỗi sớm bằng `NullPointerException` có thông điệp rõ ràng; nó thể hiện yêu cầu **bắt buộc có dữ liệu**, khác với một lỗi cú pháp format. Nếu `null` là trạng thái hợp lệ, hãy biểu diễn nó một cách có chủ đích:

```java
import java.util.Locale;

public class OptionalAmount {
    public static String label(Integer amount) {
        return amount == null
                ? "Amount: chưa có dữ liệu"
                : String.format(Locale.ROOT, "Amount: %d", amount);
    }
}
```

Nếu cần chuỗi từ một đối tượng bất kỳ, `String.valueOf(value)` cũng trả về `"null"` khi `value == null`; chỉ dùng nó khi biểu diễn ấy thực sự phù hợp. Với cờ logic ba trạng thái (`true`, `false`, `null`), không sử dụng `%b` để bảo toàn phân biệt giữa "chưa biết" và `false`.

### Giữ chuỗi định dạng cố định và đúng kiểu

Thay vì coi dữ liệu nhập từ bên ngoài là chính chuỗi format:

```java
// Dữ liệu nhập là nội dung, không phải cú pháp định dạng.
String userText = "giảm 20%";
String message = String.format(Locale.ROOT, "Ghi chú: %s", userText);
```

`message` là `"Ghi chú: giảm 20%"`. `%s` sẽ không diễn giải ký tự `%` bên trong `userText` thành placeholder mới. Cách này dễ kiểm soát hơn so với dùng `userText` như đối số `format`, vốn có thể dẫn tới placeholder ngoài ý muốn hoặc ngoại lệ nếu đầu vào chứa `%`.

Ưu tiên constant hoặc chuỗi format có thể kiểm tra bằng test. Dùng `Locale.ROOT`/`Locale.US` khi muốn đầu ra ổn định; dùng `Locale` thực tế của người dùng cho văn bản hiển thị có định dạng số/ngày theo vùng. Nhớ rằng `%f` áp dụng cho kiểu số thực phù hợp, trong khi `%d` áp dụng cho kiểu số nguyên phù hợp; `Formatter` không tự parse `"42"` thành số.

### Xử lý ngoại lệ ở ranh giới thích hợp

Trong thư viện tạo nội dung động thực sự phải nhận mẫu format từ cấu hình, có thể bắt `IllegalFormatException` tại ranh giới nhận cấu hình để trả về lỗi có ngữ cảnh (mẫu format, danh sách kiểu đối số, vị trí cấu hình). Tránh ghi nội dung nhạy cảm của đối số vào log. Khi lỗi phát sinh từ chuỗi format hard-code, sửa tại nguồn và bổ sung kiểm thử tốt hơn là dùng `catch` để tự động thay bằng `"null"`.

## 6. Những nhầm lẫn cần tránh

1. **“`%d` gặp `null` sẽ luôn ném `IllegalFormatConversionException`.”** Sai: `null` được biểu diễn thành `"null"`; đối số **khác null nhưng không tương thích** như `"42"` mới gây ngoại lệ này.
2. **“`%b` sẽ parse chuỗi `"false"`.”** Sai: mọi đối tượng không phải `Boolean`, nếu khác `null`, đều dẫn đến `true`.
3. **“Thêm đối số thừa sẽ gây lỗi.”** Sai: đối số không được tham chiếu bị bỏ qua, có thể khiến lỗi logic khó phát hiện.
4. **“`%n` và `%%` mỗi cái cần một đối số.”** Sai: chúng không tiêu thụ đối số.
5. **“`%.2d` có nghĩa là in số nguyên với hai chữ số.”** Sai: precision không áp dụng cho `%d`; `%02d` mới yêu cầu độ rộng tối thiểu 2 và đệm bằng số 0.
6. **“Bắt `IllegalFormatException` sẽ bắt mọi lỗi format.”** Sai: ví dụ format string bằng `null` gây `NullPointerException`; các lỗi khác của code cũng không nhất thiết thuộc họ ngoại lệ này.

## 7. Tự kiểm tra

Dự đoán đầu ra hoặc ngoại lệ của mỗi lời gọi sau, rồi kiểm chứng với `jshell`:

```java
String.format("%s", (Object) null)
String.format("%b", "false")
String.format("%d", (Object) null)
String.format("%d", "12")
String.format("%s %s", "A")
String.format("%2$s-%1$s", "A", "B")
String.format("100%% %s", "OK")
String.format("%05d", 42)
```

Đáp án theo thứ tự: `"null"`; `"true"`; `"null"`; `IllegalFormatConversionException`; `MissingFormatArgumentException`; `"B-A"`; `"100% OK"`; `"00042"`.

Tài liệu chuẩn để tra cứu thêm: [`java.util.Formatter` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Formatter.html) và [`java.util.IllegalFormatException` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/IllegalFormatException.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-formatting-008">Phân biệt độ chính xác khi trình bày với giá trị số gốc</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt độ chính xác khi trình bày với giá trị số gốc trong Java

## Mục tiêu

Khi một ứng dụng Java hiển thị số tiền, tỷ lệ, điểm số hoặc kết quả đo, ta thường muốn **hiển thị ít chữ số hơn** để dễ đọc. Chẳng hạn, một phép tính có giá trị `12.3456` nhưng giao diện chỉ hiện `12.35`. Cần phân biệt rõ:

- **Giá trị số gốc**: dữ liệu được lưu trong biến `double`, `float`, `BigDecimal` hoặc kết quả của phép tính.
- **Độ chính xác khi trình bày**: số chữ số được chọn để tạo chuỗi hiển thị, thông qua `String.format`, `System.out.printf` hoặc formatter tương tự.
- **Làm tròn giá trị để sử dụng tiếp**: phép toán thật sự tạo ra một giá trị số mới theo quy tắc làm tròn, ví dụ `BigDecimal.setScale(..., RoundingMode.HALF_UP)`.

**Định dạng thành hai chữ số sau dấu thập phân không thay đổi biến số gốc.** Kết quả định dạng là một `String`; các phép toán làm tròn là chuyện khác và cần chọn quy tắc phù hợp với nghiệp vụ.

Bài này tập trung vào Java Core, chủ đề định dạng chuỗi, đặc biệt format specifier `%.2f` và ý nghĩa của nó so với giá trị đang được giữ trong bộ nhớ.

## 1. Ví dụ đầu tiên: số vẫn nguyên, chuỗi được rút gọn

```java
import java.util.Locale;

double original = 12.3456;

String displayed = String.format(Locale.US, "%.2f", original);

System.out.println("Original: " + original);
System.out.println("Displayed: " + displayed);
System.out.println("Still original: " + original);
```

Kết quả:

```text
Original: 12.3456
Displayed: 12.35
Still original: 12.3456
```

Phân tích:

1. `original` có kiểu `double`; lệnh `String.format` đọc giá trị này để tạo văn bản.
2. Chỉ thị `%.2f` yêu cầu biểu diễn số thực kiểu thập phân cố định với **hai chữ số sau dấu phân cách thập phân**.
3. `displayed` là chuỗi `"12.35"`, không phải một `double` mới.
4. Biến `original` chưa bị gán lại nên tiếp tục được dùng cho phép tính tiếp theo ở độ chính xác mà kiểu `double` đang lưu.

**Lưu ý về `double`:** số thập phân `12.3456` không nhất thiết được biểu diễn chính xác tuyệt đối bằng nhị phân IEEE 754. Việc `Double.toString` hiện `12.3456` có nghĩa nó xuất một dạng thập phân ngắn có thể chuyển trở lại cùng giá trị `double`, không phải bằng chứng rằng bit trong bộ nhớ mã hóa chính xác phân số thập phân đó. Tuy vậy, thao tác định dạng không sửa các bit của biến `original`.

## 2. Ý nghĩa của `%.2f`, `%8.2f` và `%.3g`

Các phương thức thuộc họ `java.util.Formatter` dùng cú pháp dấu `%` để mô tả cách biểu diễn một giá trị.

Ví dụ:

```java
String a = String.format(Locale.US, "%.2f", 12.3456);
String b = String.format(Locale.US, "%8.2f", 12.3456);
String c = String.format(Locale.US, "%.3g", 12345.678);
```

Bảng so sánh:

| Format | Ý nghĩa | Ví dụ kết quả |
| --- | --- | --- |
| `%.2f` | Số dạng thập phân cố định, hai chữ số sau dấu phân cách | `12.35` |
| `%.0f` | Số dạng thập phân cố định, không có chữ số sau dấu phân cách | `12` với `12.3456` |
| `%8.2f` | Như `%.2f` nhưng độ rộng tối thiểu của trường là tám ký tự | `"   12.35"` |
| `%.3e` | Dạng khoa học, ba chữ số sau dấu phân cách ở phần định trị | `1.235e+01` với `12.3456` |
| `%.3g` | Định dạng tổng quát với ba **chữ số có nghĩa** | `1.23e+04` với `12345.678` |

Đối với chuyển đổi `f`, *precision* là số chữ số sau dấu phân cách thập phân. Đối với `g`, *precision* biểu thị số chữ số có nghĩa. Đối với `e`, nó là số chữ số sau dấu phân cách trong phần định trị của dạng khoa học. Không thể hiểu mọi loại specifier theo cùng một quy tắc.

Ví dụ `%8.2f` có hai loại tham số định dạng khác nhau: `8` là **độ rộng hiển thị tối thiểu**, còn `2` mới là độ chính xác sau dấu thập phân. Độ rộng nhỏ hơn kết quả cần hiển thị sẽ không khiến Java âm thầm cắt bớt chữ số số học.

`%.2f` làm tròn để tạo chuỗi chứ không phải thao tác cắt chuỗi theo ký tự. Giá trị `12.3456` thành `12.35`, không phải `12.34`. Cách làm tròn ở tình huống đúng nửa đơn vị cần được xét cẩn thận khi dữ liệu đến từ số dấu phẩy động nhị phân.

### Chỉ thị `%d` không có precision kiểu `%.2f`

`%d` dùng cho số nguyên. Trong Java Formatter, không dùng `%.2d` để nói rằng muốn "số nguyên có hai chữ số"; precision không được hỗ trợ cho chuyển đổi số nguyên kiểu `d`. Nếu muốn thêm số không phía trước, một cách viết là `%02d`:

```java
String padded = String.format(Locale.US, "%02d", 7);
System.out.println(padded); // 07
```

Số `7` vẫn là số nguyên 7; `07` ở đây là **chuỗi được định dạng**, phù hợp hiển thị mã có độ rộng cố định theo yêu cầu.

## 3. Ba thao tác khác nhau cần phân biệt

| Thao tác | Kiểu dữ liệu đầu ra | Có sửa biến ban đầu? | Có tạo giá trị số đã làm tròn để tính tiếp? |
| --- | --- | --- | --- |
| `String.format(Locale.US, "%.2f", x)` | `String` | Không | Không |
| `System.out.printf("%.2f", x)` | Ghi chuỗi đã định dạng ra output | Không | Không |
| `BigDecimal.setScale(2, mode)` | `BigDecimal` mới | Không vì `BigDecimal` bất biến | Có |
| `Math.round(x)` với `x` kiểu `double` | `long` | Không | Có, nhưng làm tròn đến số nguyên |
| `new BigDecimal("12.3456")` | `BigDecimal` | Không | Biểu diễn giá trị thập phân được xây dựng từ chuỗi, chưa giảm scale |

Điểm quan trọng là phải xem **bạn cần làm gì với kết quả**. Nếu chỉ cần hiển thị báo cáo có hai chữ số thập phân, tạo `String` thường là đủ. Nếu nghiệp vụ yêu cầu chốt tiền đến cent, lưu số tiền đã quy đổi hoặc thực hiện các phép tính khác trên giá trị làm tròn, cần tạo giá trị số phù hợp và chọn `RoundingMode` có chủ đích.

Không nên chuyển một chuỗi đã định dạng sang số bằng `Double.parseDouble` chỉ để làm tròn: điều này trộn trách nhiệm hiển thị với tính toán và có thể thất bại theo locale (ví dụ chuỗi `12,35`).

## 4. `BigDecimal`: kiểm soát giá trị thập phân và quy tắc làm tròn

Các bài toán tài chính hoặc quy tắc thập phân chính xác thường cần `BigDecimal` thay vì dựa vào phép tính tiền tệ với `double`.

```java
import java.math.BigDecimal;
import java.math.RoundingMode;

BigDecimal original = new BigDecimal("12.3456");
BigDecimal rounded = original.setScale(2, RoundingMode.HALF_UP);

System.out.println(original); // 12.3456
System.out.println(rounded);  // 12.35
System.out.println(original); // 12.3456
```

Trong ví dụ, constructor từ **chuỗi thập phân** giữ đúng giá trị `12.3456`. Lệnh `setScale(2, RoundingMode.HALF_UP)` tạo **một đối tượng số mới** với hai chữ số sau dấu thập phân và làm tròn theo chế độ đã chọn. `BigDecimal` là bất biến: biến `original` không bị sửa.

### Phân biệt `HALF_UP` và `HALF_EVEN`

Các chế độ làm tròn là quy tắc nghiệp vụ, đặc biệt khi giá trị nằm **đúng giữa hai kết quả có thể**:

```java
BigDecimal sample = new BigDecimal("2.345");

System.out.println(sample.setScale(2, RoundingMode.HALF_UP));
System.out.println(sample.setScale(2, RoundingMode.HALF_EVEN));
```

Kết quả:

```text
2.35
2.34
```

Ở bước làm tròn đến hai chữ số, `2.345` nằm đúng giữa `2.34` và `2.35`. `HALF_UP` làm tròn phía xa số không khi đúng nửa; `HALF_EVEN` chọn kết quả có chữ số cuối được giữ là chẵn, trong trường hợp này là `2.34`.

Không suy luận chế độ làm tròn nghiệp vụ chỉ từ những gì hiển thị bằng `%.2f`. Nếu chính sách kế toán quy định `HALF_EVEN`, `HALF_UP` hoặc chế độ khác, hãy thực hiện chính sách trên giá trị số bằng `BigDecimal`.

### Vì sao nên tránh `new BigDecimal(double)` cho thập phân cố định

`double` lưu giá trị nhị phân gần với chữ số thập phân người dùng viết. Constructor `new BigDecimal(1.005)` chuyển **chính xác giá trị nhị phân của double** sang dạng thập phân, có thể tạo kết quả dài và khác mong đợi.

Nếu nguồn là chuỗi thập phân, dùng `new BigDecimal("1.005")`. Nếu nguồn đã là `double` và cần cách biểu diễn thập phân theo `Double.toString`, `BigDecimal.valueOf(double)` thường phù hợp hơn constructor nhận `double` trực tiếp. Dù vậy, `valueOf` không thể khôi phục thông tin chính xác đã mất từ các phép tính nhị phân trước đó.

Khi yêu cầu tính tiền, nên giữ dữ liệu dưới dạng `BigDecimal` phù hợp từ lúc nhập và xác định điểm làm tròn rõ ràng. Không tạo số đã làm tròn bằng cách biến đổi kết quả hiển thị nhiều lần.

## 5. `Locale` chỉ đổi quy ước trình bày

Một giá trị số có thể được viết khác nhau theo quốc gia:

```java
double amount = 12345.678;

String us = String.format(Locale.US, "%,.2f", amount);
String de = String.format(Locale.GERMANY, "%,.2f", amount);

System.out.println(us);
System.out.println(de);
```

Kết quả:

```text
12,345.68
12.345,68
```

Giải thích:

- Với `Locale.US`, dấu phẩy nhóm hàng nghìn và dấu chấm phân cách phần thập phân.
- Với `Locale.GERMANY`, dấu chấm nhóm hàng nghìn và dấu phẩy phân cách phần thập phân.
- `%,.2f` bật nhóm chữ số (`,`) và chọn hai chữ số thập phân (`.2`).

Cả hai định dạng đọc cùng một `double amount`. Sự thay đổi dấu phân cách không làm thay đổi giá trị lưu trong biến.

**Khuyến nghị về hợp đồng API:** Đầu ra dành cho con người nên dùng `Locale` thích hợp. Dữ liệu trao đổi giữa hệ thống nên dùng biểu diễn được định nghĩa bởi chuẩn truyền tin (ví dụ JSON number theo cú pháp JSON) hoặc một schema có quy định rõ kiểu, precision và locale. Không lưu thẳng `"12.345,68"` vào trường số của API rồi mong `Double.parseDouble` hiểu đúng.

`String.format("%.2f", value)` không truyền `Locale` sẽ sử dụng locale mặc định. Nếu kết quả dùng trong test, log có cấu trúc hoặc dữ liệu trao đổi cần tính ổn định, hãy chỉ định `Locale.US` hoặc locale phù hợp với chuẩn đầu ra thay vì phụ thuộc vào máy chạy.

## 6. Chọn lúc làm tròn: trước hay sau khi tính toán?

Giả sử bạn cần tính tổng ba giá trị `0.333`. Có hai chiến lược không tương đương:

1. **Giữ độ chính xác đầu vào**, cộng ba giá trị rồi làm tròn kết quả: `0.333 + 0.333 + 0.333 = 0.999`, làm tròn hai chữ số thành `1.00`.
2. **Làm tròn từng giá trị trước khi cộng**: mỗi `0.333` thành `0.33`, sau đó tổng thành `0.99`.

Ví dụ với `BigDecimal` để tránh độ lệch nhị phân:

```java
BigDecimal each = new BigDecimal("0.333");

BigDecimal roundAtEnd = each.add(each).add(each)
        .setScale(2, RoundingMode.HALF_UP);

BigDecimal roundBeforeSum = each.setScale(2, RoundingMode.HALF_UP)
        .multiply(new BigDecimal("3"));

System.out.println(roundAtEnd);       // 1.00
System.out.println(roundBeforeSum);   // 0.99
```

Không thể quyết định quy tắc nào đúng chỉ từ cách hiển thị. Đối với hóa đơn, thuế, thống kê hoặc phép đo, quy tắc làm tròn từng dòng hay trên tổng là **yêu cầu nghiệp vụ**. API cần nói rõ điểm làm tròn, số chữ số thập phân và `RoundingMode`, bởi chúng có thể thay đổi kết quả thực tế.

Nếu chỉ muốn hiển thị hai chữ số ở từng bước nhưng giữ số gốc để tính tiếp, có thể định dạng mỗi giá trị cho giao diện mà vẫn tính tổng bằng giá trị chưa được làm tròn. Phân biệt rõ "số đã hiển thị" với "số được dùng cho phép tính".

## 7. Ví dụ Java 17+ chạy hoàn chỉnh

Tạo tệp **`PrecisionPresentationDemo.java`**:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public class PrecisionPresentationDemo {
    public static void main(String[] args) {
        double original = 12.3456;
        String displayed = String.format(Locale.US, "%.2f", original);

        System.out.println("Original: " + original);
        System.out.println("Displayed: " + displayed);
        System.out.println("Still original: " + original);
        System.out.println(
                "Width: [" + String.format(Locale.US, "%8.2f", original) + "]"
        );

        double quantity = 12345.678;
        System.out.println(
                "US: " + String.format(Locale.US, "%,.2f", quantity)
        );
        System.out.println(
                "Germany: " + String.format(Locale.GERMANY, "%,.2f", quantity)
        );

        BigDecimal amount = new BigDecimal("12.3456");
        BigDecimal rounded = amount.setScale(2, RoundingMode.HALF_UP);

        System.out.println("Decimal original: " + amount);
        System.out.println("Decimal rounded: " + rounded);
        System.out.println("Decimal original again: " + amount);

        BigDecimal halfway = new BigDecimal("2.345");
        System.out.println(
                "Half up: " + halfway.setScale(2, RoundingMode.HALF_UP)
        );
        System.out.println(
                "Half even: " + halfway.setScale(2, RoundingMode.HALF_EVEN)
        );
        System.out.println(
                "Significant: " + String.format(Locale.US, "%.3g", quantity)
        );
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 PrecisionPresentationDemo.java
java PrecisionPresentationDemo
```

Kết quả hiển thị:

```text
Original: 12.3456
Displayed: 12.35
Still original: 12.3456
Width: [   12.35]
US: 12,345.68
Germany: 12.345,68
Decimal original: 12.3456
Decimal rounded: 12.35
Decimal original again: 12.3456
Half up: 2.35
Half even: 2.34
Significant: 1.23e+04
```

Mỗi dòng chứng minh một điểm:

- `Original` và `Still original` trùng nhau; chỉ có `Displayed` dùng độ chính xác trình bày thấp hơn.
- `Width` thêm ba dấu cách phía trước để đạt chiều rộng tối thiểu tám ký tự; đây không phải thay đổi giá trị số.
- `US` và `Germany` dùng cùng số nhưng dấu phân cách theo locale.
- `BigDecimal.setScale` tạo giá trị thập phân làm tròn riêng, vẫn giữ đối tượng số gốc.
- `HALF_UP` và `HALF_EVEN` cho kết quả khác nhau ở điểm chính giữa.
- `%.3g` lấy ba chữ số có nghĩa, có thể dùng ký hiệu khoa học; khác với việc yêu cầu ba chữ số sau dấu thập phân.

## 8. Những tình huống thực tế và hướng xử lý

| Nhu cầu | Hướng xử lý | Lý do |
| --- | --- | --- |
| Hiển thị điểm trung bình thành hai chữ số | `String.format(locale, "%.2f", average)` | Chỉ thay đổi chuỗi đầu ra |
| In một bảng có cột số được căn đều | `%8.2f` hoặc `%-8.2f` | Width/alignment là yêu cầu trình bày |
| Lưu giá tiền đã chốt đến hai chữ số | `BigDecimal.setScale(2, roundingMode)` theo quy tắc nghiệp vụ | Giá trị số mới được chọn có chủ đích |
| Tính tỷ lệ rồi hiển thị phần trăm | Tính tỷ lệ, đổi sang đơn vị phần trăm nếu cần, sau đó format kết quả | `%.2f` tự nó không nhân với 100 |
| Hiển thị nhiều locale | Format trên cùng giá trị số với locale thích hợp | Dấu thập phân và dấu nhóm là quy ước ngôn ngữ |
| Viết số vào dữ liệu máy đọc | Dùng schema/serializer với biểu diễn số được quy định | Chuỗi format cho giao diện có thể không parse thống nhất |
| So sánh kết quả tính toán | Dùng giá trị số và định nghĩa epsilon/precision theo yêu cầu | Chuỗi cùng hiển thị không chứng minh giá trị số bằng nhau |

### Hai số khác nhau có thể hiển thị giống nhau

```java
double a = 1.231;
double b = 1.234;

System.out.println(String.format(Locale.US, "%.2f", a)); // 1.23
System.out.println(String.format(Locale.US, "%.2f", b)); // 1.23
System.out.println(a == b);                              // false
```

Hai chuỗi hiển thị đều là `"1.23"` nhưng giá trị số gốc không bằng nhau. Do đó không được suy luận `a == b` từ việc hai ô giao diện có cùng nội dung hai chữ số thập phân.

Ngược lại, việc hiển thị khác nhau do locale cũng không có nghĩa các số khác nhau. Khi thiết kế test, hãy phân biệt **kiểm thử định dạng** (so sánh chuỗi với locale cố định) và **kiểm thử tính toán** (so sánh giá trị, tolerance hoặc giá trị BigDecimal theo yêu cầu).

### Hai chuỗi có vẻ khác nhau nhưng biểu thị cùng giá trị

Ví dụ `"12.30"` và `"12.3"` là hai `String` khác nhau. Khi được diễn giải theo cùng quy ước thập phân, chúng đều biểu thị giá trị số 12.3. Đối với `BigDecimal`, phương thức `equals` còn xét cả scale; `new BigDecimal("12.30").equals(new BigDecimal("12.3"))` là `false`, trong khi `compareTo` trả về 0. Việc chọn so sánh "bằng nhau" theo giá trị hay theo cách biểu diễn cần khớp hợp đồng API.

## 9. Các lỗi thường gặp

1. **Cho rằng `String.format("%.2f", value)` làm tròn biến `value`.** Nó trả về chuỗi mới. Nếu tiếp tục tính toán, `value` vẫn như cũ.
2. **Gọi mọi specifier precision là "số chữ số thập phân".** Với `f` là chữ số sau dấu thập phân, với `g` là chữ số có nghĩa.
3. **Nhầm width với precision.** `%8.2f` có width 8 và precision 2; width không giới hạn số chữ số có nghĩa của giá trị.
4. **Dùng `%.2f` để làm tròn tiền tệ trong nghiệp vụ.** Nếu giá trị làm tròn phải được lưu hay tính tiếp, hãy định nghĩa kiểu số và chính sách làm tròn tường minh.
5. **Dùng `new BigDecimal(double)` khi nguồn là số thập phân người dùng nhập.** Nó giữ giá trị nhị phân của `double`, có thể khác kỳ vọng thập phân. Ưu tiên tạo từ chuỗi đã được parse hợp lệ hoặc từ nguồn `BigDecimal`.
6. **Dùng `Math.round` mà mong thu được hai chữ số thập phân.** Với `double`, `Math.round` trả về `long` nguyên; việc nhân chia với lũy thừa 10 dễ đưa sai số nhị phân vào phép tính.
7. **Bỏ qua `Locale`.** Các môi trường có thể hiển thị dấu phân cách khác nhau, khiến so sánh chuỗi hay parse ngược không ổn định.
8. **Làm tròn từng thành phần mà nghiệp vụ muốn làm tròn trên tổng.** Kết quả cuối có thể khác nhau, như ví dụ 0.333.
9. **So sánh số bằng chuỗi đã format.** Hai số khác nhau có thể cùng hiển thị thành `1.23`, và cùng một số có thể xuất hiện khác nhau ở hai locale.

## 10. Tự kiểm tra

**Câu 1.** Cho `double price = 19.999;`. Sau `String label = String.format(Locale.US, "%.2f", price);`, kiểu của `label` là gì? Biến `price` có thành 20.00 không?

**Đáp án:** `label` có kiểu `String`, mang nội dung `"20.00"`. `price` vẫn là cùng giá trị `double` trước lời gọi.

**Câu 2.** `%.3f` khác `%.3g` thế nào?

**Đáp án:** `%.3f` yêu cầu ba chữ số sau dấu phân cách thập phân trong biểu diễn cố định; `%.3g` yêu cầu ba chữ số có nghĩa trong biểu diễn tổng quát.

**Câu 3.** Vì sao `BigDecimal.setScale(2, RoundingMode.HALF_EVEN)` không giống chỉ in bằng `%.2f`?

**Đáp án:** `setScale` trả về đối tượng số `BigDecimal` mới đã được làm tròn bằng chế độ rõ ràng. `%.2f` chỉ tạo chuỗi trình bày theo quy tắc của formatter và không thay đổi giá trị số được truyền vào.

**Câu 4.** Vì sao không nên lấy chuỗi `"12.345,68"` (locale Đức) rồi đưa thẳng vào `Double.parseDouble`?

**Đáp án:** `Double.parseDouble` nhận cú pháp thập phân theo quy tắc không dùng dấu nhóm kiểu Đức; chuỗi này không phù hợp và sẽ gây `NumberFormatException`. Nếu phải đọc số định dạng theo locale, hãy dùng parser đúng locale và xác định rõ hợp đồng dữ liệu.

**Câu 5.** `0.333` được ghi ba lần. Làm tròn từng số thành `0.33` rồi cộng có giống cộng thành `0.999` rồi làm tròn không?

**Đáp án:** Không. Trường hợp đầu thành `0.99`, trường hợp sau thành `1.00` khi dùng làm tròn hai chữ số theo `HALF_UP`. Cần làm theo quy định của nghiệp vụ.

## Ghi nhớ

**Độ chính xác khi trình bày là quy tắc chuyển giá trị số sang `String`; nó không đồng nghĩa với độ chính xác của giá trị số gốc hay chính sách làm tròn dùng cho tính toán.** Dùng `String.format`/`printf` để hiển thị, dùng `BigDecimal` và `RoundingMode` khi thật sự cần giá trị thập phân đã làm tròn, và truyền `Locale` rõ ràng khi quy ước dấu phân cách có ý nghĩa.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-formatting-009">MessageFormat khi mẫu cần đổi thứ tự theo ngôn ngữ</a>

<details>
<summary>Xem chi tiết</summary>

# MessageFormat khi mẫu cần đổi thứ tự theo ngôn ngữ

## Mục tiêu

Sau bài học, bạn có thể:

- Giải thích lý do dịch một câu bằng cách nối chuỗi cố định thường gây sai thứ tự từ, giới từ và cấu trúc ngữ pháp.
- Dùng `java.text.MessageFormat` với placeholder **theo chỉ số** `{0}`, `{1}`, `{2}` để đặt cùng dữ liệu vào các vị trí khác nhau ở từng bản dịch.
- Kết hợp `ResourceBundle` và `Locale` để chọn mẫu thông điệp theo ngôn ngữ, trong khi chương trình giữ nguyên thứ tự đối số truyền vào.
- Nhận biết cú pháp `MessageFormat` cho số, ngày giờ, dấu nháy đơn, dấu ngoặc nhọn và những giới hạn quan trọng khi quốc tế hóa (i18n).

## 1. Vì sao thay thứ tự theo ngôn ngữ là cần thiết?

Giả sử ứng dụng thông báo rằng **Lan gửi PDF cho Minh lúc 09:00**. Tiếng Anh và tiếng Việt có thể diễn đạt cùng một sự kiện nhưng đặt thời gian ở vị trí khác nhau:

- Tiếng Anh: “Lan sent PDF to Minh at 09:00.”
- Tiếng Việt: “Lúc 09:00, Lan gửi PDF cho Minh.”
- Tiếng Nhật (một cách diễn đạt minh họa): “09:00にLanがMinhへPDFを送りました。”

Cách ghép cứng như sau rất khó chuyển ngữ:

~~~java
String message = sender + " sent " + item + " to " + receiver + " at " + time;
~~~

Nếu dịch bằng cách thay riêng từ `sent` thành `gửi`, các từ nối hoặc thứ tự thành phần có thể vẫn sai. Cách phù hợp hơn là **dịch toàn bộ mẫu câu**, rồi để người dịch tự chọn vị trí của từng đối số.

`MessageFormat` nhận một **pattern** chứa placeholder đánh số, cùng một mảng đối số:

~~~text
English: {0} sent {2} to {1} at {3}.
Tiếng Việt: Lúc {3}, {0} gửi {2} cho {1}.
日本語: {3}に{0}が{1}へ{2}を送りました。
~~~

Cả ba mẫu dùng cùng ánh xạ:

| Chỉ số | Ý nghĩa | Giá trị minh họa |
| --- | --- | --- |
| `{0}` | Người gửi | `Lan` |
| `{1}` | Người nhận | `Minh` |
| `{2}` | Vật/tệp được gửi | `PDF` |
| `{3}` | Thời gian | `09:00` |

**Điểm cốt lõi:** chỉ số chỉ ra **đối số nào** được sử dụng, không buộc đối số phải xuất hiện theo thứ tự tăng dần. Mẫu có thể đặt `{3}` trước `{0}`, dùng lại `{0}` nhiều lần hoặc không dùng một đối số nào đó. Thứ tự các phần tử trong mảng truyền cho `format` vẫn ổn định.

## 2. Ví dụ Java 17+ chạy độc lập: thay thứ tự theo Locale

Lưu thành tệp `MessageOrderDemo.java`:

~~~java
import java.text.MessageFormat;
import java.util.Locale;

public class MessageOrderDemo {
    public static void main(String[] args) {
        Object[] values = {"Lan", "Minh", "PDF", "09:00"};

        String english = "{0} sent {2} to {1} at {3}.";
        String vietnamese = "Lúc {3}, {0} gửi {2} cho {1}.";
        String japanese = "{3}に{0}が{1}へ{2}を送りました。";

        System.out.println(
                new MessageFormat(english, Locale.ENGLISH).format(values));
        System.out.println(
                new MessageFormat(vietnamese, Locale.forLanguageTag("vi-VN"))
                        .format(values));
        System.out.println(
                new MessageFormat(japanese, Locale.JAPANESE).format(values));
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~bash
javac -encoding UTF-8 MessageOrderDemo.java
java MessageOrderDemo
~~~

Kết quả (xem bằng terminal hỗ trợ UTF-8):

~~~text
Lan sent PDF to Minh at 09:00.
Lúc 09:00, Lan gửi PDF cho Minh.
09:00にLanがMinhへPDFを送りました。
~~~

**Điều đáng chú ý:**

1. Mảng `values` chỉ được tạo **một lần** và có thứ tự cố định: người gửi, người nhận, vật được gửi, thời gian.
2. Tiếng Anh đặt `{3}` cuối câu; tiếng Việt và tiếng Nhật có thể đặt `{3}` lên đầu.
3. Không phải đổi thứ tự phần tử trong `values` cho từng `Locale`; mỗi pattern quyết định thứ tự xuất hiện.
4. Ví dụ dùng `String` như `"09:00"` và `"PDF"` để đầu ra xác định. Ở ứng dụng thực, nên biểu diễn thời gian, tiền tệ và các giá trị có định dạng bằng kiểu dữ liệu phù hợp, sau đó định dạng theo locale của người nhận.

Đối tượng `MessageFormat` có hai thành phần độc lập: **mẫu câu** để sắp thứ tự thành phần và **Locale** để điều khiển cách định dạng những loại dữ liệu nhạy với ngôn ngữ/khu vực như số hoặc ngày. Chọn một `Locale` không tự động dịch văn bản tiếng Anh trong pattern; chương trình phải cung cấp bản dịch của mẫu.

## 3. Cách MessageFormat hiểu placeholder

Cú pháp tổng quát thường gặp:

~~~text
{argumentIndex}
{argumentIndex,formatType}
{argumentIndex,formatType,formatStyle}
~~~

Trong đó:

- `argumentIndex` là số nguyên không âm, tính từ `0`; tương ứng với phần tử cùng chỉ số trong `Object[]` truyền vào `format`.
- `formatType` có thể là `number`, `date`, `time`, `choice` hoặc được bỏ qua nếu chỉ cần giá trị văn bản.
- `formatStyle` xác định kiểu hiển thị, ví dụ `short`, `medium`, `long` hoặc `integer` tùy `formatType`.

Ví dụ các mẫu:

| Pattern | Ý nghĩa |
| --- | --- |
| `"Xin chào {0}!"` | Chèn đối số thứ 0 |
| `"{1} đã mời {0}"` | Đảo vị trí hai đối số |
| `"{0} / {0}"` | Dùng cùng một đối số hai lần |
| `"Tổng số: {0,number,integer}"` | Định dạng đối số thứ 0 dưới dạng số nguyên theo locale |
| `"Ngày: {0,date,short}"` | Định dạng đối số ngày theo kiểu ngắn của locale |

`MessageFormat` **không dùng tên biến làm khóa placeholder** theo cú pháp Java chuẩn. `{sender}` không phải cú pháp thay thế tên hợp lệ của `java.text.MessageFormat`; hãy dùng `{0}` và ghi rõ quy ước ánh xạ giữa chỉ số với ý nghĩa của tham số. Nếu đổi vị trí các tham số trong mã nhưng quên cập nhật quy ước, mẫu dịch có thể hiển thị sai mà vẫn chạy.

Khi một mẫu đang yêu cầu một chỉ số không có trong `Object[]`, `MessageFormat` không thể lấy đúng giá trị dự định. Nên kiểm tra số lượng và kiểu đối số trong chương trình hoặc kiểm thử bản dịch. Không nên coi việc biên dịch được mã Java là bằng chứng rằng tất cả tài nguyên dịch đều đúng.

## 4. Tách câu dịch bằng ResourceBundle

Ví dụ phần 2 minh họa cơ chế với các chuỗi đặt ngay trong mã. Trong ứng dụng thật, các mẫu nên nằm trong tệp bản dịch, do `ResourceBundle` chọn theo `Locale`. Nhờ đó, nhóm dịch thuật có thể chỉnh vị trí placeholder mà không sửa logic Java.

Giả sử bốn tệp nằm **cùng thư mục** khi biên dịch/chạy:

~~~text
LocalizedMessageDemo.java
messages.properties
messages_en.properties
messages_vi.properties
messages_ja.properties
~~~

Nội dung `messages.properties` — giá trị mặc định nếu không có bản dịch phù hợp:

~~~properties
sent={0} sent {2} to {1} at {3}.
~~~

Nội dung `messages_en.properties`:

~~~properties
sent={0} sent {2} to {1} at {3}.
~~~

Nội dung `messages_vi.properties`:

~~~properties
sent=Lúc {3}, {0} gửi {2} cho {1}.
~~~

Nội dung `messages_ja.properties`:

~~~properties
sent={3}に{0}が{1}へ{2}を送りました。
~~~

Lưu chương trình sau vào `LocalizedMessageDemo.java`:

~~~java
import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

public class LocalizedMessageDemo {
    private static String buildMessage(Locale locale, Object[] values) {
        ResourceBundle bundle = ResourceBundle.getBundle("messages", locale);
        String pattern = bundle.getString("sent");
        return new MessageFormat(pattern, locale).format(values);
    }

    public static void main(String[] args) {
        Object[] values = {"Lan", "Minh", "PDF", "09:00"};

        System.out.println(buildMessage(Locale.ENGLISH, values));
        System.out.println(
                buildMessage(Locale.forLanguageTag("vi-VN"), values));
        System.out.println(buildMessage(Locale.JAPANESE, values));
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 LocalizedMessageDemo.java
java LocalizedMessageDemo
~~~

Kết quả:

~~~text
Lan sent PDF to Minh at 09:00.
Lúc 09:00, Lan gửi PDF cho Minh.
09:00にLanがMinhへPDFを送りました。
~~~

Luồng xử lý:

1. `ResourceBundle.getBundle("messages", locale)` tra cứu nhóm tài nguyên có tên gốc `messages` và chọn bản dịch phù hợp. Chẳng hạn `vi-VN` có thể tìm bản quốc gia trước, sau đó dùng bản ngôn ngữ `messages_vi.properties`.
2. `bundle.getString("sent")` lấy nguyên mẫu thông điệp cho khóa `sent`. Khóa giữ ổn định; nội dung pattern có thể khác nhau ở từng ngôn ngữ.
3. `new MessageFormat(pattern, locale)` tạo bộ định dạng với locale cụ thể.
4. `format(values)` áp dụng các placeholder lên đúng phần tử của mảng, bất kể thứ tự xuất hiện trong câu.

Từ Java 9 trở lên, bộ đọc `ResourceBundle` cho `.properties` hỗ trợ UTF-8 (có cơ chế fallback lịch sử), vì vậy văn bản tiếng Việt hoặc tiếng Nhật có thể lưu trực tiếp trong các tệp tài nguyên UTF-8. Hãy kiểm soát encoding của trình biên tập và môi trường chạy để tránh hiển thị lỗi ký tự. Nếu tệp bản dịch và khóa cần thiết không tồn tại trong đường tìm kiếm, các thao tác `getBundle`/`getString` có thể ném `MissingResourceException`.

### Giữ hợp đồng về tham số khi dịch

Tạo quy ước có thể kiểm thử cho từng khóa:

~~~text
sent:
  {0} = sender / người gửi (String)
  {1} = recipient / người nhận (String)
  {2} = item / vật được gửi (String)
  {3} = time / thời gian đã được định dạng để hiển thị (String)
~~~

Người dịch được phép **đổi thứ tự placeholder trong pattern**, nhưng không được âm thầm đổi ý nghĩa của từng chỉ số. Nếu bản dịch viết nhầm `{4}` hoặc đổi `{1}` thành người gửi, kết quả sẽ sai. Hãy kiểm tra mọi locale được hỗ trợ và các giá trị thực tế, thay vì chỉ kiểm thử ngôn ngữ mặc định.

### Lưu ý về fallback

`ResourceBundle` có quy tắc tra cứu biến thể ngôn ngữ/quốc gia và có thể xét cả locale mặc định của JVM trước khi dùng bundle gốc trong một số tình huống. Bởi vậy, nên có bundle nền `messages.properties`, đặt `messages_en.properties` khi cần bản tiếng Anh xác định, và viết bài kiểm thử với locale rõ ràng. Đừng giả định thiếu một bản dịch luôn đồng nghĩa chắc chắn dùng bundle gốc ngay lập tức.

## 5. Định dạng số, ngày và thời gian theo Locale

Đổi thứ tự thành phần chỉ là một phần của bản địa hóa. Với giá trị có cấu trúc, `MessageFormat` còn có cú pháp định dạng trực tiếp:

~~~java
import java.text.MessageFormat;
import java.util.Locale;

public class LocaleNumberDemo {
    public static void main(String[] args) {
        Object[] values = {"Lan", 1234.5};

        String en = "{0}: {1,number}";
        String vi = "Số điểm của {0}: {1,number}";

        System.out.println(
                new MessageFormat(en, Locale.US).format(values));
        System.out.println(
                new MessageFormat(vi, Locale.forLanguageTag("vi-VN"))
                        .format(values));
    }
}
~~~

Lưu `LocaleNumberDemo.java` và chạy bằng `javac -encoding UTF-8 LocaleNumberDemo.java`, sau đó `java LocaleNumberDemo`. Dấu phân cách hàng nghìn và dấu thập phân trong hai dòng có thể khác nhau theo dữ liệu locale mà bản Java đang sử dụng, nên hãy quan sát chính ký tự được in ra (bao gồm khoảng trắng đặc biệt), thay vì cố định đáp án kiểu dấu phân cách.

Một số quy tắc đáng nhớ:

- `{0,number}` và `{0,number,integer}` dùng định dạng số theo `Locale`; `integer` liên quan đến **định dạng/làm tròn số để hiển thị**, không biến đầu vào thành kiểu nguyên.
- `{0,number,currency}` dùng định dạng tiền tệ theo locale của formatter. Nếu cần chỉ rõ loại tiền tệ kinh doanh (ví dụ USD so với VND), phải quản lý `Currency` và `NumberFormat` riêng để tránh nhầm ký hiệu/đơn vị.
- `{0,date,short}` và `{0,time,short}` cần đối số ngày/giờ tương thích, thường là `java.util.Date`; không truyền chuỗi ngày tùy ý rồi mong formatter tự nhận dạng.
- Các định dạng ngày/giờ truyền thống bên dưới `MessageFormat` có thể dùng múi giờ mặc định của JVM. Nếu ứng dụng phải hiển thị một múi giờ cố định, hãy kiểm soát timezone thông qua formatter thích hợp hoặc chuẩn bị giá trị đã được bản địa hóa trước.
- Với API `java.time` hiện đại, `DateTimeFormatter` hỗ trợ nhiều kiểu thời gian, locale và timezone rõ ràng hơn. Có thể dùng nó định dạng thành `String` trước khi gắn vào mẫu `MessageFormat`, đặc biệt khi làm việc với `Instant`, `ZonedDateTime` hoặc lịch trình đa múi giờ.

Cũng cần phân biệt **dịch ngữ pháp** với **định dạng giá trị**: đảo `{1}` và `{0}` giải quyết thứ tự tham số, nhưng không tự dịch tên sản phẩm, danh từ số ít/số nhiều hay biểu thức thời gian. Các dữ liệu hiển thị đó phải được bản địa hóa tương ứng khi nghiệp vụ yêu cầu.

## 6. Dấu nháy đơn và ngoặc nhọn: bẫy cú pháp MessageFormat

`MessageFormat` có cú pháp riêng: dấu nháy đơn `'` dùng để quote đoạn của pattern, còn `{...}` dùng cho placeholder. Điều này đặc biệt quan trọng khi dịch thông điệp sang ngôn ngữ có dấu apostrophe.

Ví dụ Java 17:

~~~java
import java.text.MessageFormat;
import java.util.Locale;

public class MessageQuoteDemo {
    public static void main(String[] args) {
        Object[] values = {"done"};

        System.out.println(new MessageFormat(
                "It's {0}.", Locale.ENGLISH).format(values));

        System.out.println(new MessageFormat(
                "It''s {0}.", Locale.ENGLISH).format(values));

        System.out.println(new MessageFormat(
                "Use '{' and '}' around {0}.", Locale.ENGLISH)
                .format(new Object[] {"x"}));
    }
}
~~~

Lưu `MessageQuoteDemo.java` rồi chạy `javac -encoding UTF-8 MessageQuoteDemo.java` và `java MessageQuoteDemo`. Kết quả:

~~~text
Its {0}.
It's done.
Use { and } around x.
~~~

**Vì sao dòng đầu đáng ngạc nhiên?** Trong pattern `"It's {0}."`, dấu `'` đơn có thể mở một vùng quote và làm `{0}` còn là ký tự literal, khiến placeholder không được thay. Viết `"It''s {0}."` để biểu diễn dấu apostrophe thật khi dùng cú pháp `MessageFormat`.

Để hiển thị dấu ngoặc nhọn mà không bắt đầu placeholder, có thể quote chúng trong pattern như `"'{' "` hoặc `"'}'"` (ví dụ `"Use '{' and '}' around {0}."`). Không dùng quy tắc escape của Java String hoặc regex để tự suy đoán quy tắc của `MessageFormat`; đây là một ngữ pháp khác.

Trong `.properties`, cần phân biệt escape của định dạng tệp, escape của chuỗi Java và cú pháp `MessageFormat`. Ví dụ: bản dịch có từ chứa apostrophe thật vẫn phải tuân thủ quy tắc nhân đôi `''` khi đưa vào `MessageFormat`, kể cả nếu nội dung đã được nạp từ `ResourceBundle`.

## 7. MessageFormat khác String.format như thế nào?

Cả hai đều hỗ trợ format tham số, nhưng cú pháp và mục đích thường khác nhau:

| Tiêu chí | `MessageFormat` | `String.format` / `Formatter` |
| --- | --- | --- |
| Placeholder | `{0}`, `{1}`, `{0,number}` | `%s`, `%d`, `%.2f` |
| Đổi vị trí tham số trong mẫu | Chỉ số `{2}`, `{0}` | Chỉ số đối số `%2$s`, `%1$s` |
| Mẫu trong tệp bản dịch | Dùng tốt với `ResourceBundle` và `Locale` | Cũng dùng được nhưng phải giữ đúng cú pháp `%...` |
| Số/ngày theo locale | Cú pháp `number/date/time` tích hợp | Bộ định dạng có các conversion và flag riêng |
| Dấu đặc biệt cần lưu ý | Apostrophe `'`, ngoặc `{ }` | Dấu phần trăm `%`, conversion ký tự |
| Lựa chọn thực tế | Mẫu thông báo i18n với vị trí đối số và kiểu trình bày tùy ngôn ngữ | Xuất chuỗi có đặc tả kiểu/độ rộng/chính xác, log, bảng số liệu |

`MessageFormat` không phải cách duy nhất để hoán đổi thứ tự đối số: `String.format` cũng hỗ trợ tham chiếu vị trí. Trong bài này, `MessageFormat` là lựa chọn phù hợp vì ứng dụng **đã dùng mẫu thông điệp theo locale** và có thể tận dụng cú pháp dành cho hiển thị số/ngày trong tài nguyên dịch.

Không trộn cú pháp: truyền `"%2$s -> %1$s"` cho `MessageFormat` sẽ không khiến các specifier kiểu `Formatter` được thay thế; và truyền `"{1} -> {0}"` cho `String.format` cũng không hiểu placeholder kiểu `MessageFormat`.

## 8. Các lỗi phổ biến và cách tránh

**Lỗi 1 — Ghép câu từng từ rồi mới dịch.** Việc thêm từ nối bằng toán tử `+` hoặc placeholder rời rạc có thể gây sai ngữ pháp khi ngôn ngữ đòi đảo vị trí thành phần. Hãy dịch **mẫu câu hoàn chỉnh**, để người dịch quyết định vị trí `{0}`, `{1}`.

**Lỗi 2 — Thay thứ tự dữ liệu để phục vụ bản dịch.** Sửa `Object[]` theo từng `Locale` làm mất hợp đồng của chỉ số. Giữ thứ tự đối số trong code, đổi chỉ thứ tự placeholder ở tài nguyên dịch.

**Lỗi 3 — Viết nhầm chỉ số hoặc loại đối số.** Mẫu `{2,number}` sẽ gặp vấn đề nếu đối số 2 là `"PDF"` thay vì số. Định nghĩa bảng hợp đồng tham số, kiểm thử mọi bản dịch và những giá trị có kiểu dữ liệu thực tế.

**Lỗi 4 — Nghĩ Locale tự dịch câu.** `Locale.forLanguageTag("vi-VN")` chỉ cho formatter biết quy tắc vùng/ngôn ngữ. Muốn có câu tiếng Việt, phải nạp mẫu tiếng Việt qua `ResourceBundle` hoặc hệ thống i18n tương đương.

**Lỗi 5 — Không escape apostrophe trong MessageFormat.** Dấu `'` đơn trong pattern có thể khiến placeholder không còn được xử lý. Dùng `''` khi phải hiển thị apostrophe literal và kiểm tra bản dịch có dấu nháy.

**Lỗi 6 — Chỉ dịch mẫu nhưng bỏ quên giá trị.** Nếu đưa `"Monday"`, `"a report"` hoặc chuỗi ngày kiểu Mỹ vào mẫu tiếng Việt, kết quả vẫn có nội dung không phù hợp. Phải bản địa hóa cả các giá trị mang ngôn ngữ/định dạng khu vực.

**Lỗi 7 — Dùng một MessageFormat mutable chung nhiều luồng.** `MessageFormat` có trạng thái thay đổi và không đảm bảo thread-safe. Tạo formatter mới theo thao tác hoặc dùng bản sao riêng cho từng luồng. Nếu cần hiệu năng, quản lý cache pattern/bundle và chiến lược formatter riêng có kiểm soát thay vì chia sẻ cùng đối tượng đang bị chỉnh sửa.

**Lỗi 8 — Xem thông điệp i18n là hệ thống pluralization toàn năng.** Java `MessageFormat` chuẩn hỗ trợ cú pháp `choice` cho một số tình huống số lượng, nhưng không cung cấp đầy đủ mọi quy tắc giống/biến cách/số ít số nhiều của mọi ngôn ngữ. Khi cần plural/gender phức tạp, cân nhắc thư viện i18n chuyên dụng như ICU MessageFormat và mô hình dịch phù hợp; không tự ghép hậu tố `"s"` theo logic tiếng Anh.

**Lỗi 9 — Không kiểm thử tất cả locale.** Bản tiếng Anh đúng chưa đảm bảo bản tiếng Việt hoặc tiếng Nhật đúng. Hãy chạy thử với cả giá trị thông thường, chuỗi rỗng, số/ngày khác biệt, ký tự đặc biệt, giới hạn chỗ hiển thị và thứ tự placeholder đã bị đảo.

**Lỗi 10 — Tin rằng formatter tự escape HTML hoặc mã lệnh.** `MessageFormat` chỉ tạo chuỗi hiển thị; nó không tự làm sạch HTML, SQL hay nội dung đưa vào một ngữ cảnh có rủi ro. Khi hiển thị trên web, áp dụng escaping theo chính ngữ cảnh đầu ra, và tránh sử dụng pattern không tin cậy làm nội dung điều khiển.

## 9. Thực hành: tự dự đoán kết quả

Cho dữ liệu:

~~~java
Object[] values = {"Mai", "Lan", "file.zip", "10:30"};
~~~

Và các mẫu:

~~~text
A = "{0} gửi {2} cho {1} lúc {3}."
B = "Vào {3}, {1} nhận {2} từ {0}."
C = "{0} đã gửi; người gửi là {0}."
~~~

Thử thay từng chỉ số trong đầu trước khi chạy chương trình:

~~~java
import java.text.MessageFormat;
import java.util.Locale;

public class OrderQuiz {
    public static void main(String[] args) {
        Object[] values = {"Mai", "Lan", "file.zip", "10:30"};
        String[] patterns = {
            "{0} gửi {2} cho {1} lúc {3}.",
            "Vào {3}, {1} nhận {2} từ {0}.",
            "{0} đã gửi; người gửi là {0}."
        };
        for (String pattern : patterns) {
            System.out.println(new MessageFormat(
                    pattern, Locale.forLanguageTag("vi-VN")).format(values));
        }
    }
}
~~~

Lưu `OrderQuiz.java` rồi chạy bằng `javac -encoding UTF-8 OrderQuiz.java` và `java OrderQuiz`. Kết quả:

~~~text
Mai gửi file.zip cho Lan lúc 10:30.
Vào 10:30, Lan nhận file.zip từ Mai.
Mai đã gửi; người gửi là Mai.
~~~

Ba câu cùng dùng một mảng dữ liệu. Pattern có thể **đổi chỗ**, **lặp lại** hoặc **không dùng** một đối số; người viết template kiểm soát cách diễn đạt mà không đổi logic truyền dữ liệu.

### Checklist áp dụng trong dự án

1. Đặt khóa thông điệp có ý nghĩa ổn định như `sent`, `payment.received`, `notification.invite`.
2. Ghi tài liệu chỉ số `{0}`, `{1}`, ... kèm kiểu và ý nghĩa, rồi giữ hợp đồng đó cho tất cả locale.
3. Dịch toàn câu trong từng resource bundle; cho phép đổi vị trí placeholder và điều chỉnh từ nối cho tự nhiên.
4. Nạp pattern theo `Locale`, tạo `MessageFormat(pattern, locale)` và đưa vào **cùng một thứ tự tham số**.
5. Kiểm thử bản dịch có placeholder bị thiếu/sai, dấu apostrophe, định dạng số/ngày, fallback bundle và văn bản dài hơn dự kiến.

## Tài liệu chính thức

- [Java SE 17 — MessageFormat](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/MessageFormat.html)
- [Java SE 17 — ResourceBundle](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ResourceBundle.html)
- [Java SE 17 — Locale](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Locale.html)
- [Java SE 17 — Formatter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Formatter.html)
- [Java SE 17 — DateTimeFormatter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/format/DateTimeFormatter.html)


</details>

- [Quay lại đầu trang](#back-to-top)
