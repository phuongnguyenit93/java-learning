<a id="back-to-top"></a>

# Kiểm thử và lỗi

## Menu
- [Ma trận null, rỗng, trắng, một ký tự và chuỗi dài](#string-testing-001)
- [Kiểm thử chỉ số đầu–cuối và khoảng trích xuất](#string-testing-002)
- [Kiểm thử surrogate pair, surrogate đơn và dấu kết hợp](#string-testing-003)
- [Kiểm thử trim, strip, NBSP và các loại xuống dòng](#string-testing-004)
- [Kiểm thử ==, equals, intern và biểu thức hằng](#string-testing-005)
- [Kiểm thử regex với dấu chấm, gạch chéo, đô la và nhóm](#string-testing-006)
- [Kiểm thử split giữ phần rỗng và splitWithDelimiters](#string-testing-007)
- [Kiểm thử định dạng theo locale và chuyển mã hai chiều](#string-testing-008)
- [Phân biệt lỗi nội dung, lỗi chỉ số, lỗi regex và lỗi mã hóa](#string-testing-009)

## <a id="string-testing-001">Ma trận null, rỗng, trắng, một ký tự và chuỗi dài</a>

<details>
<summary>Xem chi tiết</summary>

# Ma trận kiểm thử String: null, rỗng, trắng, một ký tự và chuỗi dài

## 1. Mục tiêu: kiểm thử các lớp đầu vào dễ gây lỗi

Với Java `String`, chỉ thử vài chuỗi “bình thường” là chưa đủ. Một hàm nhận chuỗi có thể hoạt động tốt với `"hello"` nhưng sai hoặc ném ngoại lệ khi nhận `null`, `""`, `" "`, một ký tự, ký tự Unicode ngoài BMP, hoặc một chuỗi rất dài. Bài này trình bày cách xây dựng **ma trận kiểm thử theo lớp tương đương và giá trị biên**, đọc đúng kết quả API Java và chọn trường hợp có giá trị thay vì thử ngẫu nhiên.

Các lớp đầu vào cốt lõi:

1. **`null`:** không có đối tượng `String` để gọi phương thức.
2. **Rỗng `""`:** đối tượng `String` tồn tại nhưng `length() == 0`.
3. **Chỉ gồm whitespace:** có ít nhất một ký tự, có thể gồm dấu cách, tab, LF hoặc ký tự Unicode khác; “blank” tùy vào quy tắc API.
4. **Một ký tự:** ranh giới nhỏ nhất của chuỗi không rỗng; cần phân biệt một `char` UTF-16 với một Unicode code point.
5. **Chuỗi dài:** kiểm tra tính đúng đắn, hiệu năng, cấp phát và các giả định về độ dài, nhưng không dùng dữ liệu quá lớn gây cạn bộ nhớ trong test thường ngày.

Việc phân lớp phải dựa trên **hợp đồng đầu vào của hàm đang kiểm thử**. Một chương trình có thể coi `null` là lỗi, một chương trình khác cho phép `null` như giá trị “chưa cung cấp”. Không có quy tắc chung buộc tất cả ứng dụng biến `null` thành `""`.

## 2. Ma trận đầu vào chuẩn

| Trường hợp | Ví dụ Java | `length()` | `isEmpty()` | `isBlank()` | Ý nghĩa |
| --- | --- | ---: | --- | --- | --- |
| Không có chuỗi | `null` | Không gọi được | Không gọi được | Không gọi được | Phải kiểm tra `null` trước |
| Chuỗi rỗng | `""` | 0 | `true` | `true` | Có đối tượng nhưng không có ký tự |
| Một dấu cách | `" "` | 1 | `false` | `true` | Chuỗi trắng nhưng không rỗng |
| Tab và LF | `"\t\n"` | 2 | `false` | `true` | Hai ký tự whitespace |
| Một chữ cái ASCII | `"Z"` | 1 | `false` | `false` | Một UTF-16 code unit và một code point |
| Một emoji `😀` | `"\uD83D\uDE00"` | 2 | `false` | `false` | Hai UTF-16 code units nhưng một code point |
| EM SPACE | `"\u2003"` | 1 | `false` | `true` | Unicode whitespace được `Character.isWhitespace` nhận diện |
| NBSP | `"\u00A0"` | 1 | `false` | `false` | Non-breaking space không được `isBlank()` tính là blank |
| Chuỗi dài | `"a".repeat(100_000)` | 100000 | `false` | `false` | Kiểm tra độ dài, thao tác và tài nguyên |

Bảng trên áp dụng cho **Java 17+** và quy tắc `String.isBlank()` của Java. Các ô ghi “không gọi được” có nghĩa gọi trực tiếp `s.length()`, `s.isEmpty()` hoặc `s.isBlank()` khi `s == null` sẽ gây `NullPointerException`; `null` không phải một biến thể của `String` có độ dài bằng không.

**Đừng đánh đồng** `null`, `""` và `" "`: chúng có ý nghĩa khác nhau ngay cả khi UI đôi khi hiển thị cả ba giống như không có chữ.

## 3. Phép phân loại an toàn khi giá trị có thể `null`

Kiểm tra `null` trước khi gọi bất kỳ instance method nào:

```java
static String state(String s) {
    if (s == null) return "NULL";
    if (s.isEmpty()) return "EMPTY";
    if (s.isBlank()) return "BLANK";
    return "TEXT";
}
```

Đây là **đoạn mã phương thức**, có thể đặt trong một class Java. Trật tự điều kiện rất quan trọng:

- Nếu `s == null`, trả trạng thái `NULL` và không gọi phương thức của đối tượng.
- Nếu `s.isEmpty()` là `true`, trả `EMPTY`.
- Nếu độ dài dương nhưng `s.isBlank()` là `true`, trả `BLANK`.
- Các giá trị còn lại là `TEXT`. Trạng thái `TEXT` ở đây chỉ nghĩa “không blank theo quy tắc `String.isBlank()`”, không nhất thiết là văn bản hợp lệ về mặt nghiệp vụ.

Ví dụ, một mã đơn hàng `"   "` có thể bị từ chối theo chính sách đầu vào; một mật khẩu chỉ chứa dấu cách có thể hợp lệ hoặc không, tùy sản phẩm. Không tự gọi `strip()` trước khi phân loại nếu nghiệp vụ coi khoảng trắng là dữ liệu cần giữ.

### Phân biệt các cách so sánh

- `s == null` là cách kiểm tra tham chiếu có `null` hay không.
- `"".equals(s)` là cách kiểm tra chuỗi rỗng an toàn khi `s` có thể null.
- `Objects.equals(a, b)` là so sánh nội dung an toàn với `null` ở hai phía.
- `a.equals(b)` có thể gây `NullPointerException` nếu `a == null`.
- `a == b` với hai tham chiếu `String` kiểm tra **cùng đối tượng**, không phải nội dung bằng nhau; không dùng nó để xác nhận kết quả xử lý chuỗi.

Nếu đặc tả cho phép `null` và `""` mang cùng một nghĩa, nên nói rõ điều đó trong API rồi chuẩn hóa có chủ đích. Không tự dùng `Objects.toString(value, "")` ở mọi nơi, vì thao tác ấy làm mất sự khác biệt giữa hai trạng thái.

## 4. Chọn đúng quy tắc whitespace trước khi kiểm thử

`isEmpty()` chỉ hỏi `length() == 0`; `isBlank()` nhận chuỗi rỗng hoặc toàn các code point thỏa `Character.isWhitespace`. `trim()` loại mã ký tự từ `U+0000` đến `U+0020` ở hai đầu, còn `strip()` loại Unicode whitespace ở hai đầu theo `Character.isWhitespace`. Vì vậy `"\u2003"` (EM SPACE) là blank và được strip nhưng không được trim; `"\u00A0"` (NBSP) không được `isBlank()` coi là blank và không bị `strip()` loại bỏ.

~~~java
String emWrapped = "\u2003Z\u2003";
System.out.println(emWrapped.trim().equals("Z"));  // false
System.out.println(emWrapped.strip().equals("Z")); // true
System.out.println("\u00A0".isBlank());            // false
~~~

Trong ma trận đầu vào, kiểm tra ít nhất SPACE, TAB, LF, EM SPACE và NBSP theo **quy tắc nghiệp vụ được công bố**; không tự coi tất cả ký tự trông giống khoảng trắng là tương đương. Khi cần phân biệt `lines()` và regex `\R` hay xử lý các kiểu xuống dòng, dùng các ca riêng cho CR/LF/CRLF và Unicode line separators.

## 5. Một ký tự và ranh giới UTF-16

Với chuỗi một code unit `"Z"`, `charAt(0)` hợp lệ, `charAt(1)` ném `StringIndexOutOfBoundsException`, và `substring(1)` hợp lệ nhưng trả `""`. Với chuỗi rỗng `""`, `charAt(0)` không hợp lệ trong khi `substring(0, 0)` trả rỗng. Hãy kiểm tra cả **phần tử cuối**, **ranh giới ngay sau phần tử cuối** và **khoảng rỗng**.

~~~java
String single = "Z";
System.out.println(single.substring(1).isEmpty()); // true
String emoji = "\uD83D\uDE00";
System.out.println(emoji.length()); // 2 UTF-16 code units
System.out.println(emoji.codePointCount(0, emoji.length())); // 1
~~~

Một code point ngoài BMP có thể dùng hai UTF-16 code units, còn một chữ có dấu kết hợp có thể gồm nhiều code point. Chọn đơn vị đo rõ ràng (code units, code points, graphemes hoặc byte) trước khi viết giới hạn độ dài hay phép cắt; chỉ `length()` là chưa đủ cho mọi yêu cầu hiển thị.

## 6. Chuỗi dài: kiểm thử độ đúng, bộ nhớ và thời gian

Một ca dài có thể là:

```java
String longText = "a".repeat(100_000);

boolean correctLength = longText.length() == 100_000;
boolean prefix = longText.startsWith("aaa");
boolean suffix = longText.endsWith("aaa");
```

Ở đây, `100_000` là **kích thước thử nghiệm có kiểm soát**, không phải giới hạn tối đa của `String` hay giới hạn chung của JVM. Giới hạn thực tế còn phụ thuộc biểu diễn chuỗi, runtime, bộ nhớ, giới hạn đối tượng/mảng và tác vụ đang thực hiện. Một chuỗi cực lớn có thể gây `OutOfMemoryError` hoặc khiến thử nghiệm mất quá nhiều thời gian. Không nên tạo chuỗi hàng gigabyte trong bộ test chạy hàng ngày chỉ để nói rằng “có test chuỗi dài”.

Các câu hỏi có ích khi kiểm thử chuỗi dài:

1. Hàm có trả **kết quả chính xác** cho đầu vào lớn hơn bình thường, hoặc có cắt cụt/làm mất ký tự cuối?
2. Có giới hạn nghiệp vụ được công bố không (ví dụ số ký tự tối đa), và kết quả khi vượt giới hạn là gì?
3. Có lỗi chỉ số, tràn tính toán độ dài trung gian hoặc lỗi xử lý đầu/cuối không?
4. Có thuật toán lặp lại `+=` hoặc `substring`/`replace` làm sao chép nhiều lần dẫn đến chi phí quá lớn không?
5. Có vô tình ghi toàn bộ chuỗi nhạy cảm hoặc rất lớn vào log, phản hồi lỗi hay bộ nhớ cache không?

Với giới hạn nghiệp vụ `maxLength`, nên có các ca **`maxLength - 1`, `maxLength`, `maxLength + 1`** nếu phù hợp, cộng ca rỗng, một ký tự và số lượng Unicode code point/grapheme khi đó là đơn vị giới hạn. Đừng tự suy luận giới hạn theo byte nếu hợp đồng ghi “ký tự”: UTF-8 có thể cần nhiều byte cho cùng một số code point.

Với các thao tác nối nhiều phần tử, cân nhắc `StringBuilder`, `String.join` hoặc stream collector nếu đúng bài toán. Đo hiệu năng bằng benchmark phù hợp (ví dụ JMH) khi hiệu năng thực sự là tiêu chí; một phép đo `System.nanoTime()` đơn lẻ dễ sai lệch do JIT, GC và tối ưu runtime. Test chức năng không cần cố chứng minh thời gian tuyệt đối trên mọi máy.

## 7. Chương trình kiểm thử Java 17+ độc lập

Tạo file `StringBoundaryMatrix.java`:

```java
import java.util.Objects;

public class StringBoundaryMatrix {
    static String state(String s) {
        if (s == null) return "NULL";
        if (s.isEmpty()) return "EMPTY";
        if (s.isBlank()) return "BLANK";
        return "TEXT";
    }

    static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        String[] names = {
            "null", "empty", "space", "tabsLF", "oneASCII",
            "emoji", "emSpace", "nbsp", "long"
        };
        String[] values = {
            null, "", " ", "\t\n", "Z",
            "\uD83D\uDE00", "\u2003", "\u00A0", "a".repeat(100_000)
        };

        System.out.printf("%-10s | %-5s | %6s | %8s%n",
                "Case", "State", "length", "codePts");
        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            String length = value == null ? "-" : "" + value.length();
            String codePoints = value == null ? "-"
                    : "" + value.codePointCount(0, value.length());
            System.out.printf("%-10s | %-5s | %6s | %8s%n",
                    names[i], state(value), length, codePoints);
        }

        require(!Objects.equals(null, ""), "null != empty");
        require("".isEmpty() && "".isBlank(), "empty is blank");
        require(!" ".isEmpty() && " ".isBlank(), "space is blank");
        require("\t\n".isBlank(), "tab and LF are blank");
        require(!"\u00A0".isBlank(), "NBSP is not isBlank whitespace");
        require("\u2003".isBlank(), "EM SPACE is blank");
        require("\u2003Z\u2003".strip().equals("Z"), "strip Unicode");
        require(!"\u2003Z\u2003".trim().equals("Z"), "trim differs");
        require("Z".charAt(0) == 'Z', "one ASCII char");
        require("\uD83D\uDE00".length() == 2, "emoji uses UTF-16 pair");
        require("\uD83D\uDE00".codePointCount(0, 2) == 1, "one code point");
        require(values[8].length() == 100_000, "long length");
        require(values[8].startsWith("aaa"), "long prefix");
        require(values[8].endsWith("aaa"), "long suffix");
        System.out.println("Checks: PASS");
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 -encoding UTF-8 StringBoundaryMatrix.java
java StringBoundaryMatrix
```

Stdout đã kiểm chứng:

```text
Case       | State | length |  codePts
null       | NULL  |      - |        -
empty      | EMPTY |      0 |        0
space      | BLANK |      1 |        1
tabsLF     | BLANK |      2 |        2
oneASCII   | TEXT  |      1 |        1
emoji      | TEXT  |      2 |        1
emSpace    | BLANK |      1 |        1
nbsp       | TEXT  |      1 |        1
long       | TEXT  | 100000 |   100000
Checks: PASS
```

Các dấu `-` ở trường `length`, `codePts` của ca `null` có nghĩa **không áp dụng**, không phải số âm hay độ dài bằng 0. Chương trình không in hết chuỗi 100.000 ký tự: nó chỉ in thông số, tránh làm nhiễu console và làm chậm test. Phép `require` chủ động ném `AssertionError` nếu bất kỳ điều kiện nào sai; không cần bật tùy chọn JVM `-ea` như với từ khóa `assert` thông thường.

Giải thích kết quả đáng chú ý:

- `empty`: `isEmpty()` và `isBlank()` đều đúng; phân loại ưu tiên `EMPTY` để không mất thông tin độ dài 0.
- `space` và `tabsLF`: có ký tự thật, được phân loại `BLANK`.
- `emoji`: độ dài theo UTF-16 là 2 nhưng chỉ có 1 code point.
- `emSpace` là blank, `nbsp` không blank, đúng theo `Character.isWhitespace`.
- `long` dài 100000 code units và code points vì chỉ lặp chữ ASCII `a`; dữ liệu Unicode ngoài BMP không nhất thiết có hai số bằng nhau.

## 8. Biến ma trận thành bộ kiểm thử có khả năng bắt lỗi

Một bảng kiểm thử tốt nên liên hệ từng ca với một **nguy cơ cụ thể**:

| Đầu vào | Lỗi hay bị lộ | Điều cần xác nhận |
| --- | --- | --- |
| `null` | `NullPointerException` ngoài ý muốn | Hợp đồng cho phép, từ chối hay chuẩn hóa `null` |
| `""` | `charAt(0)` / `substring` sai biên | Nhánh rỗng, kết quả mặc định và thông báo lỗi |
| `" "` / `"\t\n"` | Nhầm blank với empty | Phân loại, validate, chuẩn hóa có chủ đích |
| Unicode whitespace | Chỉ xử lý ASCII hoặc tin `trim` giống `strip` | Quy tắc `Character.isWhitespace`, NBSP, EM SPACE |
| Một ký tự `"Z"` | Giả định chuỗi có ít nhất hai ký tự | Chỉ số 0, chỉ số 1, cắt đầu/cuối |
| Emoji hoặc dấu kết hợp | Đồng nhất UTF-16 units với ký tự hiển thị | `codePointCount`, code point boundaries, grapheme nếu cần |
| Chuỗi dài | Cắt cụt, copy lặp, giới hạn sai đơn vị | Đầu/cuối, độ dài, tài nguyên và giới hạn đã công bố |

Cùng một input có thể kiểm tra nhiều phương thức, nhưng **kỳ vọng phải cụ thể**: “không ném ngoại lệ” chưa chứng minh hàm trả đúng. Với parser, thêm kiểm tra nội dung các trường; với validator, xác nhận mã lỗi; với formatter, kiểm tra chính xác ký tự whitespace và Unicode; với API web, thử cả field vắng mặt và field chứa chuỗi rỗng trong payload.

### Kiểm thử các cặp biên quan trọng

- **Có giá trị / không có giá trị:** `null` so với `""`.
- **Rỗng / trắng:** `""` so với `" "`.
- **Một / hai ký tự:** `"Z"` so với `"ZZ"` để bắt lỗi chỉ số và phép cắt chuỗi.
- **ASCII / Unicode:** `"Z"` so với `"😀"` hoặc ký tự có dấu kết hợp.
- **Whitespace thường / whitespace Unicode:** `" "`, `"\t"`, `"\u2003"`, `"\u00A0"`.
- **Sát giới hạn / vượt giới hạn:** `n-1`, `n`, `n+1` theo đơn vị mà hợp đồng công bố.

Khi một lỗi được tìm thấy, hãy giữ lại một ca **regression test** nhỏ nhất có thể tái hiện lỗi. Không cần tạo hàng nghìn biến thể dài giống nhau nếu chúng không khám phá thêm một nguy cơ độc lập.

## 9. Thiết kế hàm nhận String để dễ kiểm thử

Trước khi viết test, định nghĩa các chính sách:

1. `null` có được phép không? Nếu không, hàm ném loại ngoại lệ nào, ở ranh giới nào? Có dùng `Objects.requireNonNull` không?
2. `""` có hợp lệ hay là “bỏ trống”? Có giá trị mặc định nào không?
3. Khoảng trắng đầu/cuối có ý nghĩa không? Có nên `strip` hay giữ nguyên?
4. “Độ dài” đếm UTF-16 code units, Unicode code points, grapheme hay số byte sau khi mã hóa?
5. Có giới hạn đầu vào không, ví dụ `maxLength`? Nếu vượt giới hạn, trả lỗi gì và tránh cấp phát quá lớn ra sao?
6. Kết quả có phải bất biến theo locale/charset không? So sánh chữ thường/hoa hay encode/decode cần chọn quy tắc tường minh.

Ví dụ một chính sách “tên hiển thị không được chỉ chứa whitespace, giới hạn 100 code point” có thể kiểm tra `null`, blank, `strip()`, `codePointCount` theo thứ tự; nhưng nếu nghiệp vụ lại muốn giới hạn 100 **byte UTF-8**, phép kiểm tra phải đổi thành đếm byte đã encode. Không thể thay một chính sách bằng chính sách khác chỉ vì cùng dùng từ “độ dài”.

Với trường hợp xử lý dữ liệu nhạy cảm, tránh in toàn bộ input khi test thất bại; log nhãn case và độ dài là đủ trong nhiều tình huống. Với dữ liệu dài, đặt mức trần test để bộ kiểm thử chạy ổn định trong CI.

## 10. Lỗi thường gặp khi lập ma trận String

1. **Gọi `null.isEmpty()` hoặc `null.isBlank()`:** `null` không phải đối tượng String; phải kiểm tra null trước.
2. **Coi `""` và `" "` như nhau:** cái đầu có độ dài 0, cái sau có độ dài 1.
3. **Cho rằng `isBlank()` nhận diện mọi thứ trông giống khoảng trắng:** NBSP là ví dụ phản chứng.
4. **Dùng `trim()` và `strip()` thay thế nhau hoàn toàn:** chúng dùng tập ký tự khác nhau.
5. **Dùng `length()` để đếm emoji hoặc ký tự người dùng thấy:** `length()` đếm UTF-16 code units; `codePointCount` cũng chưa phải phép đếm grapheme tổng quát.
6. **Truy cập `charAt(0)` trước khi kiểm tra chuỗi rỗng:** gây `StringIndexOutOfBoundsException`.
7. **Dùng `==` để so nội dung `String`:** so tham chiếu, không phải bằng nhau về nội dung.
8. **Chỉ thử chuỗi dài rồi tuyên bố hiệu năng tốt:** phải đo trong điều kiện có kiểm soát, có giới hạn tài nguyên và workload đại diện.
9. **Chỉ thử số ký tự, quên số byte:** khi I/O hoặc giao thức giới hạn byte, cần kiểm tra độ dài sau encode theo charset đúng.
10. **Coi một ca “không lỗi” là test pass:** vẫn cần khẳng định output, hiệu ứng phụ, thứ tự và chính sách xử lý đầu vào.

## 11. Checklist khi bổ sung test cho hàm xử lý String

- Xác định hợp đồng cho `null`, rỗng, blank và whitespace đầu/cuối.
- Thêm ca một ký tự, hai ký tự và ca Unicode ngoài BMP nếu logic liên quan chỉ số/độ dài.
- Ghi rõ “độ dài” nghĩa là UTF-16, code point, grapheme hay byte; kiểm thử đúng đơn vị.
- Thêm chuỗi dài vừa đủ và các giá trị sát giới hạn được công bố.
- Khẳng định **kết quả mong đợi**, không chỉ “chạy được”; giữ test xác định và có thể chạy lặp lại.
- Khi phát hiện lỗi, thêm ca regression nhỏ nhất để ngăn lỗi tái diễn.

**Ghi nhớ:** ma trận **`null` → rỗng → blank → một ký tự → dài** là khung khởi đầu để kiểm thử `String`, không phải bộ ca cố định cho mọi nghiệp vụ. Giá trị của nó nằm ở việc phân biệt rõ các trạng thái và kiểm chứng những ranh giới mà mã Java rất dễ xử lý sai.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-testing-002">Kiểm thử chỉ số đầu–cuối và khoảng trích xuất</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử chỉ số đầu–cuối và khoảng trích xuất trong Java String

## 1. Mục tiêu và vấn đề cần kiểm thử

Khi tách một phần văn bản bằng `substring`, `subSequence`, `charAt` hoặc chép ký tự với `getChars`, chỉ cần lệch một đơn vị chỉ số là có thể thu được nội dung sai, làm mất ký tự hoặc phát sinh ngoại lệ. Lỗi thường không xuất hiện ở trường hợp bình thường mà lộ ra với chuỗi rỗng, chỉ số bằng độ dài, khoảng có hai đầu bằng nhau hoặc ký tự Unicode dùng nhiều UTF-16 code unit.

Bài này hướng dẫn cách **định nghĩa khoảng chỉ số**, **tạo bộ ca kiểm thử bao phủ ranh giới**, **kiểm tra cả kết quả lẫn ngoại lệ**, và **kiểm thử một hàm trích xuất tự viết**. Đối tượng nghiên cứu chính là `java.lang.String` trên Java 17+, có mở rộng sang `CharSequence` và các thao tác liên quan khi cần.

Sau bài học, bạn có thể:

- Diễn giải chính xác quy ước `[beginIndex, endIndex)`.
- Phân biệt phạm vi hợp lệ của `substring`, `charAt`, `codePointAt` và `getChars`.
- Thiết kế các ca kiểm thử cho **đầu, giữa, cuối, rỗng, vượt biên, đảo đầu–cuối**.
- Đối chiếu độ dài theo **UTF-16 code unit** với Unicode code point.
- Viết chương trình kiểm thử độc lập, mở rộng thành unit test JUnit và suy luận các thuộc tính đúng cho một khoảng trích xuất.

## 2. Cơ sở: chỉ số trong String là chỉ số UTF-16

Trong Java, `String.length()` trả **số UTF-16 code unit**. Với văn bản ASCII như `"ABCDE"`, mỗi ký tự chiếm một code unit:

```text
Chuỗi:     A  B  C  D  E
Chỉ số:    0  1  2  3  4
Ranh giới: 0  1  2  3  4  5
                                 length() = 5
```

Phần tử tại chỉ số `i` được truy cập bằng `charAt(i)`; chỉ số cuối cùng có thể truy cập là `length() - 1` khi chuỗi không rỗng. Trong khi đó, một **ranh giới cắt** hoàn toàn có thể nằm tại vị trí `length()` (ngay sau ký tự cuối).

Không được đồng nhất hai loại chỉ số:
- **Chỉ số phần tử:** phải có `0 <= i < length()`.
- **Chỉ số ranh giới khoảng:** có thể có `0 <= boundary <= length()`.

Chính vì vậy `"ABCDE".charAt(5)` không hợp lệ nhưng `"ABCDE".substring(5, 5)` lại hợp lệ và trả `""`.

**UTF-16 không đồng nhất với ký tự người dùng nhìn thấy.** Một code point ngoài Basic Multilingual Plane, chẳng hạn emoji `😀`, cần **hai code unit** (cặp surrogate). Kết quả của `length()` và vị trí mà `substring` nhận vào đều tính theo code unit, không tính theo số code point hay grapheme cluster.

## 3. Hợp đồng API quan trọng

### 3.1. substring(beginIndex, endIndex)

Chữ ký:

```java
public String substring(int beginIndex, int endIndex);
```

Khoảng được lấy là **nửa mở**, ký hiệu toán học `[beginIndex, endIndex)`: **bao gồm beginIndex, loại trừ endIndex**.

Điều kiện hợp lệ:

```text
0 <= beginIndex <= endIndex <= s.length()
```

Kết quả chứa `endIndex - beginIndex` UTF-16 code unit, bắt đầu ở `beginIndex` và dừng trước `endIndex`.

```java
String s = "ABCDE";

System.out.println(s.substring(1, 4)); // BCD
System.out.println(s.substring(0, 5)); // ABCDE
System.out.println(s.substring(3, 3).isEmpty()); // true
System.out.println(s.substring(5, 5).isEmpty()); // true
```

`substring(1, 4)` lấy vị trí 1, 2, 3; **không lấy** vị trí 4. Nếu muốn lấy cả phần tử cuối ở vị trí 4, truyền `endIndex = 5`.

Một khoảng **rỗng** như `[3, 3)` hoàn toàn hợp lệ; kết quả là `""`. Hai đầu bằng nhau không phải lỗi.

### 3.2. substring(beginIndex)

Overload một tham số:

```java
public String substring(int beginIndex);
```

Lấy từ `beginIndex` đến hết chuỗi:

```java
String s = "ABCDE";

System.out.println(s.substring(2));  // CDE
System.out.println(s.substring(5));  // Dòng trống vì kết quả là ""
```

Điều kiện hợp lệ: `0 <= beginIndex <= s.length()`. Giá trị bằng `length()` được phép và trả chuỗi rỗng; giá trị lớn hơn `length()` hoặc âm thì không hợp lệ.

### 3.3. subSequence(start, end)

`String` triển khai `CharSequence` và hỗ trợ:

```java
public CharSequence subSequence(int start, int end);
```

Phạm vi **`[start, end)`** giống `substring(start, end)`. Khi gọi trên đối tượng `String`, nội dung kết quả bằng với nội dung được `substring` trả về:

```java
String s = "ABCDE";
CharSequence part = s.subSequence(1, 4);
System.out.println(part); // BCD
```

Tuy nhiên, biến `part` có kiểu khai báo `CharSequence`; phương thức của những lớp cài đặt `CharSequence` khác không nhất thiết có cùng cách lưu bộ nhớ hoặc vòng đời như `String`. Kiểm thử theo **hợp đồng của đối tượng thực sự đang dùng**.

### 3.4. charAt(index)

`charAt` truy cập một UTF-16 code unit **tại một vị trí**, không trích xuất khoảng:

```java
String s = "ABCDE";
System.out.println(s.charAt(0)); // A
System.out.println(s.charAt(4)); // E
// s.charAt(5); // StringIndexOutOfBoundsException
```

Điều kiện hợp lệ là `0 <= index < s.length()`. Trên chuỗi rỗng, không có chỉ số hợp lệ cho `charAt`.

### 3.5. Ngoại lệ khi chỉ số không hợp lệ

`substring` và những thao tác liên quan thuộc họ ngoại lệ `IndexOutOfBoundsException` khi biên vượt phạm vi. OpenJDK/HotSpot thường ném `StringIndexOutOfBoundsException` đối với nhiều lỗi `String`, nhưng **bài kiểm thử portable** có thể kiểm tra lớp ngoại lệ theo hợp đồng API (`IndexOutOfBoundsException`) thay vì phụ thuộc từng chi tiết thông báo.

Thông báo của ngoại lệ (ví dụ `begin 6, end 5, length 5`) có thể thay đổi theo phiên bản JDK. Không nên so khớp nguyên văn exception message khi chỉ cần xác nhận ranh giới sai bị từ chối.

## 4. Ma trận ca kiểm thử chuẩn cho substring

Dùng chuỗi `"ABCDE"` có `length() = 5` để đối chiếu:

| Trường hợp | Lời gọi | Kỳ vọng |
| --- | --- | --- |
| Đầu chuỗi | `substring(0, 1)` | `"A"` |
| Giữa chuỗi | `substring(1, 4)` | `"BCD"` |
| Ký tự cuối | `substring(4, 5)` | `"E"` |
| Toàn bộ chuỗi | `substring(0, 5)` | `"ABCDE"` |
| Khoảng rỗng đầu | `substring(0, 0)` | `""` |
| Khoảng rỗng giữa | `substring(3, 3)` | `""` |
| Khoảng rỗng cuối | `substring(5, 5)` | `""` |
| Suffix từ giữa | `substring(2)` | `"CDE"` |
| Suffix rỗng | `substring(5)` | `""` |
| Begin âm | `substring(-1, 2)` | Ném `IndexOutOfBoundsException` |
| End âm | `substring(0, -1)` | Ném `IndexOutOfBoundsException` |
| Begin lớn hơn end | `substring(4, 3)` | Ném `IndexOutOfBoundsException` |
| End lớn hơn length | `substring(1, 6)` | Ném `IndexOutOfBoundsException` |
| Begin lớn hơn length | `substring(6)` | Ném `IndexOutOfBoundsException` |

Ma trận phải có **hai loại kiểm tra**: kết quả chính xác cho khoảng hợp lệ và ngoại lệ cho khoảng sai. Việc chỉ kiểm tra “không bị lỗi” chưa đủ để phát hiện lệch một ký tự.

Một cách tổ chức thực tế là luôn chọn các giá trị quanh biên `0` và `length()`: `-1`, `0`, `1`, `length()-1`, `length()`, `length()+1` (khi chúng có ý nghĩa). Với chuỗi rỗng, `length()-1 = -1` là chỉ số bất hợp lệ; đây chính là ca cần kiểm thử.

## 5. Chuỗi rỗng và các khoảng có hai đầu trùng nhau

Đây là nguồn lỗi phổ biến khi code tự thêm hoặc bớt `1`:

```java
String empty = "";

System.out.println(empty.substring(0, 0).isEmpty()); // true
System.out.println(empty.substring(0).isEmpty());    // true

// empty.charAt(0);      // Không hợp lệ: không có phần tử
// empty.substring(0, 1); // Không hợp lệ: end > length
```

Với một chuỗi bất kỳ `s`, tất cả khoảng dạng `s.substring(i, i)` đều cho chuỗi rỗng **nếu** `0 <= i <= s.length()`. Điều này có nghĩa khoảng rỗng ở cuối chuỗi có thể hợp lệ.

Cần phân biệt **khoảng rỗng** với **giá trị nguồn rỗng**:
- `"ABCDE".substring(2, 2)` rỗng dù nguồn không rỗng.
- `"".substring(0, 0)` rỗng vì cả nguồn lẫn khoảng rỗng.
- `"".substring(1, 1)` vẫn sai vì ranh giới 1 nằm ngoài độ dài 0.

Hàm tạo slice có thể cho phép khoảng rỗng (thường hợp lý), hoặc cấm theo quy tắc nghiệp vụ. Nếu cấm, phải ghi rõ đó là **ràng buộc riêng của hàm nghiệp vụ**, không gán nhầm cho `String.substring`.

## 6. Tự viết một hàm trích xuất với quy tắc kiểm tra rõ ràng

Giả sử muốn viết hàm `slice` xử lý theo khoảng `[begin, end)`:

```java
static String slice(String source, int begin, int end) {
    if (source == null) {
        throw new IllegalArgumentException("source must not be null");
    }

    int length = source.length();
    if (begin < 0 || end < begin || end > length) {
        throw new IndexOutOfBoundsException(
                "Invalid range [" + begin + ", " + end
                        + ") for length " + length);
    }

    return source.substring(begin, end);
}
```

Đây là **đoạn phương thức minh họa**, cần đặt bên trong một lớp Java trước khi biên dịch. Hàm chủ động ném `IllegalArgumentException` khi `source == null`. Quyết định này thuộc thiết kế `slice`; bản thân `String.substring` được gọi trên một tham chiếu null sẽ gặp `NullPointerException` trước khi vào phương thức.

Điều kiện kiểm tra gộp chính xác ba lỗi:
- `begin < 0`: chỉ số bắt đầu âm.
- `end < begin`: khoảng bị đảo ngược; cũng bao phủ một số giá trị end âm.
- `end > length`: điểm kết thúc vượt khỏi chuỗi; đồng thời ngăn begin lớn hơn length nếu `end >= begin`.

Có thể kiểm tra tường minh thêm `begin > length` để thông báo dễ hiểu, nhưng về tính hợp lệ, ba mệnh đề trên đã đủ do điều kiện `begin <= end`.

Lưu ý quan trọng khi **tự tạo hàm chuyển khoảng inclusive thành exclusive**: nếu end-inclusive bằng `Integer.MAX_VALUE`, cộng `1` có thể tràn số nguyên. Cần xác nhận end nằm trong phạm vi chuỗi **trước khi cộng** hoặc dùng cách kiểm tra không gây overflow.

## 7. Chương trình Java 17+ kiểm thử tự động, độc lập

Ví dụ sau có bộ kiểm thử nhỏ không phụ thuộc JUnit. Nó giúp thấy rõ cách kiểm tra giá trị trả về, ngoại lệ và độ dài Unicode.

Tạo tệp `StringRangeTest.java`, lưu bằng UTF-8:

```java
import java.util.Objects;

public class StringRangeTest {
    private static int passed = 0;

    private static void equal(Object expected, Object actual, String name) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(
                    name + " expected=" + expected + ", actual=" + actual);
        }
        passed++;
    }

    private static void check(boolean condition, String name) {
        if (!condition) {
            throw new AssertionError(name);
        }
        passed++;
    }

    private static void throwsType(
            Class<? extends Throwable> type,
            Runnable action,
            String name) {
        try {
            action.run();
        } catch (Throwable error) {
            if (type.isInstance(error)) {
                passed++;
                return;
            }
            throw new AssertionError(name + " wrong exception", error);
        }
        throw new AssertionError(name + " did not throw");
    }

    public static void main(String[] args) {
        String s = "ABCDE";

        equal("BCD", s.substring(1, 4), "middle");
        equal("ABCDE", s.substring(0, 5), "whole");
        equal("", s.substring(3, 3), "empty middle");
        equal("", s.substring(5, 5), "empty end");
        equal("", "".substring(0, 0), "empty source");
        equal("CDE", s.substring(2), "one-argument suffix");
        equal("BCD", s.subSequence(1, 4).toString(), "subSequence");
        equal('E', s.charAt(4), "last char");

        throwsType(IndexOutOfBoundsException.class,
                () -> s.substring(-1, 2), "negative begin");
        throwsType(IndexOutOfBoundsException.class,
                () -> s.substring(1, 6), "end after length");
        throwsType(IndexOutOfBoundsException.class,
                () -> s.substring(4, 3), "reversed range");
        throwsType(IndexOutOfBoundsException.class,
                () -> s.substring(6), "one-argument invalid begin");
        throwsType(IndexOutOfBoundsException.class,
                () -> s.charAt(5), "charAt at length");

        String unicode = "A😀B";
        equal("😀", unicode.substring(1, 3), "full surrogate pair");
        equal(2, unicode.substring(1, 3).length(),
                "emoji code-unit length");
        equal(1, unicode.codePointCount(1, 3),
                "emoji code-point count");
        check(Character.isHighSurrogate(
                unicode.substring(1, 2).charAt(0)),
                "partial surrogate boundary");

        System.out.println("PASS: " + passed + " tests");
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 StringRangeTest.java
java StringRangeTest
```

Kết quả stdout nếu mọi kiểm tra thành công:

```text
PASS: 17 tests
```

Chương trình sử dụng bộ so sánh `Objects.equals` để kiểm tra cả giá trị trả về, dùng `throwsType` để chấp nhận ngoại lệ **đúng họ** và dùng `check` cho thuộc tính Unicode. Không cần bật cờ `-ea`, vì các helper chủ động ném `AssertionError` khi thất bại.

Một test bị lỗi sẽ ném `AssertionError` và dừng chương trình, nhờ đó kết quả “PASS” không che giấu sai lệch ở các bước trước. Trên dự án thật, nên sử dụng framework unit test (ví dụ JUnit 5) để thu thập báo cáo đầy đủ và chạy tự động trong CI.

## 8. Kiểm thử UTF-16 và khoảng cắt qua surrogate pair

Xét chuỗi chứa emoji ngoài BMP:

```java
String text = "A😀B";
System.out.println(text.length()); // 4
```

Vị trí UTF-16 như sau:

```text
index 0 : 'A'
index 1 : high surrogate của 😀
index 2 : low surrogate của 😀
index 3 : 'B'
boundary: 0 | 1 | 2 | 3 | 4
```

Vì vậy:
- `text.substring(1, 3)` lấy **đủ hai code unit** và biểu diễn emoji hoàn chỉnh.
- `text.substring(1, 2)` lấy **một high surrogate đơn lẻ**. Lời gọi hợp lệ về mặt chỉ số, nhưng kết quả chứa một surrogate chưa ghép cặp.
- `text.substring(2, 3)` chỉ lấy low surrogate, cũng tạo chuỗi không hoàn chỉnh về mặt cặp surrogate.
- `text.substring(3, 4)` lấy `"B"`.

**Java String cho phép chứa các surrogate đơn lẻ.** `substring` chỉ thực thi hợp đồng chỉ số UTF-16, không tự xác minh rằng điểm cắt trùng với ranh giới code point hay ranh giới ký tự hiển thị.

Nếu yêu cầu là “lấy hai Unicode code point đầu” thay vì “lấy hai code unit đầu”, hãy đổi chỉ số theo code point:

```java
String text = "A😀B";
int end = text.offsetByCodePoints(0, 2);
String firstTwoCodePoints = text.substring(0, end);

System.out.println(end); // 3
System.out.println(firstTwoCodePoints); // A😀
```

`offsetByCodePoints(beginIndex, codePointOffset)` giúp dịch một số lượng code point thành một chỉ số UTF-16. Thao tác này vẫn **không đồng nghĩa tách grapheme cluster**: ký tự + dấu kết hợp, chuỗi emoji có ZWJ hoặc cờ emoji có thể gồm nhiều code point nhưng được hiển thị như một cụm. Với yêu cầu theo *ký tự người dùng nhìn thấy*, cần thuật toán phân đoạn văn bản phù hợp với Unicode, locale và nhu cầu thực tế.

## 9. getChars: kiểm tra khoảng nguồn và vị trí đích

Ngoài `substring`, `String.getChars(srcBegin, srcEnd, dst, dstBegin)` chép **vùng `[srcBegin, srcEnd)`** sang mảng đích:

```java
String s = "ABCDE";
char[] dst = {'_', '_', '_', '_', '_'};

s.getChars(1, 4, dst, 1);

System.out.println(String.valueOf(dst)); // _BCD_
```

Khoảng nguồn `[1,4)` dài **3**; nó được chép vào `dst[1]`, `dst[2]` và `dst[3]`. Kiểm thử `getChars` phải xét **cả hai hệ chỉ số**:

- Nguồn: `0 <= srcBegin <= srcEnd <= s.length()`.
- Đích: `dstBegin >= 0` và `dstBegin + (srcEnd - srcBegin) <= dst.length`, với phép cộng được thực hiện an toàn để không tràn số nếu tự viết hàm kiểm tra.
- Mảng `dst` phải tồn tại; nếu null, việc sử dụng mảng có thể gây `NullPointerException`.

Để tránh tràn số trong logic kiểm tra tự viết, sau khi xác nhận `0 <= dstBegin <= dst.length` có thể kiểm tra `copyCount <= dst.length - dstBegin`.

Các ca kiểm thử đáng có:
1. Sao chép vào giữa mảng và xác nhận các vị trí ngoài vùng không bị thay đổi.
2. Sao chép vùng rỗng với `srcBegin == srcEnd`.
3. `srcEnd > s.length()` hoặc `srcBegin < 0`.
4. `dstBegin < 0` hoặc vùng đích không đủ sức chứa.
5. Kết quả với surrogate pair, vì `getChars` cũng chép theo UTF-16 code unit.

Điểm khác biệt: `substring` tạo ra một kết quả `String`, trong khi `getChars` **thay đổi nội dung mảng đích** theo vùng chọn. Vì vậy phải kiểm tra cả tính chính xác của vùng chép và tính toàn vẹn của những ô mảng nằm ngoài vùng.

## 10. Trích xuất dựa trên indexOf: đừng tự tạo lỗi +1/-1

Trường hợp thực tế thường tìm ranh giới động:

```java
String input = "user=alice;role=admin";
int begin = input.indexOf('=') + 1;
int end = input.indexOf(';');
String user = input.substring(begin, end);

System.out.println(user); // alice
```

Ví dụ hoạt động với định dạng cụ thể này, nhưng **không an toàn cho mọi đầu vào**. Nếu không có dấu `=`, `indexOf('=')` trả `-1` và cộng 1 thành `0`; lỗi thiếu delimiter có thể bị che khuất. Nếu thiếu dấu `;`, `end = -1` và `substring` ném ngoại lệ.

Thiết kế kiểm tra trước khi trích xuất:

```java
static String extractBeforeSemicolon(String input) {
    if (input == null) {
        throw new IllegalArgumentException("input is null");
    }

    int equal = input.indexOf('=');
    if (equal < 0) {
        throw new IllegalArgumentException("Missing '='");
    }

    int begin = equal + 1;
    int end = input.indexOf(';', begin);
    if (end < 0) {
        throw new IllegalArgumentException("Missing ';'");
    }

    return input.substring(begin, end);
}
```

Đây là phương thức minh họa, cần đặt bên trong lớp Java. Với quy tắc này, đầu vào `"user=;role=admin"` cho chuỗi rỗng hợp lệ vì `begin == end`; nếu nghiệp vụ cấm giá trị rỗng thì phải thêm kiểm tra riêng.

Nên kiểm thử:
- Delimiter ở vị trí bình thường.
- `=` ngay trước `;` (giá trị rỗng).
- Thiếu `=`, thiếu `;`.
- Dấu `;` xuất hiện trước `=`: nhờ `indexOf(';', begin)`, hàm tìm dấu kết thúc **sau** dấu `=`.
- Đầu vào không có ký tự hoặc `null`.
- Có nhiều dấu `=` hoặc `;`: xác định rõ quy tắc chọn delimiter đầu tiên/hợp lệ của nghiệp vụ.

Đừng viết test chỉ cho một chuỗi “đẹp”; điều cần xác minh là **giả định về vị trí delimiter** trước khi truyền chỉ số cho `substring`.

## 11. Unit test với JUnit 5

Trong dự án có **JUnit Jupiter** trên test classpath, có thể dùng `assertEquals`, `assertThrows` và `assertAll`:

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class StringBoundsTest {
    @Test
    void extractsHalfOpenRange() {
        String s = "ABCDE";

        assertEquals("BCD", s.substring(1, 4));
        assertEquals("", s.substring(5, 5));
        assertEquals("E", s.substring(4, 5));
    }

    @Test
    void rejectsInvalidBounds() {
        String s = "ABCDE";

        assertThrows(IndexOutOfBoundsException.class,
                () -> s.substring(-1, 2));
        assertThrows(IndexOutOfBoundsException.class,
                () -> s.substring(4, 3));
        assertThrows(IndexOutOfBoundsException.class,
                () -> s.substring(0, 6));
    }

    @Test
    void countsUtf16UnitsNotCodePoints() {
        String s = "A😀B";

        assertEquals(4, s.length());
        assertEquals("😀", s.substring(1, 3));
        assertEquals(2, s.substring(1, 3).length());
    }
}
```

Đây là **lớp test dành cho dự án đã cấu hình JUnit 5**, không phải một chương trình độc lập chỉ cần `javac` mặc định. JUnit phải có trong dependencies/classpath và test được chạy bằng Maven, Gradle hoặc IDE theo thiết lập của dự án. Phần Java chạy độc lập không cần dependency đã có tại mục 7.

Kiểm thử tham số hóa với `@ParameterizedTest` và `@CsvSource` phù hợp khi cùng một quy tắc được áp dụng cho nhiều cặp begin/end. Tuy nhiên, hãy giữ riêng nhóm **ca hợp lệ** và **ca ném ngoại lệ**, để thông báo thất bại rõ ràng.

## 12. Kiểm thử theo thuộc tính (property-based thinking)

Ngoài các ví dụ đơn lẻ, hãy kiểm tra những **bất biến** sau cho mọi `String s` và mọi cặp `begin, end` hợp lệ:

1. **Độ dài:** `s.substring(begin, end).length() == end - begin`.
2. **Phần tử khớp vị trí:** với mọi `i` trong `[0, end - begin)`, `slice.charAt(i) == s.charAt(begin + i)`.
3. **Khoảng rỗng:** `s.substring(i, i).isEmpty()` cho mọi `0 <= i <= s.length()`.
4. **Ghép lại giữ nội dung:** `s.substring(0, begin) + s.substring(begin, end) + s.substring(end)` bằng `s` về nội dung.
5. **Khoảng toàn bộ:** `s.substring(0, s.length()).equals(s)`.
6. **Bảo toàn chuỗi nguồn:** gọi `substring` không sửa `s` vì `String` immutable.

Các thuộc tính này cũng áp dụng cho chuỗi có surrogate pair nếu đo và chọn ranh giới theo **UTF-16 code unit**. Chúng không đảm bảo mọi lát cắt có cặp surrogate đầy đủ, vì đó là yêu cầu bổ sung ở tầng Unicode.

Có thể tạo các ca **randomized tests** với generator sinh `String` và hai chỉ số hợp lệ, ví dụ chọn `begin` trong `[0, length]` rồi chọn `end` trong `[begin, length]`. Với ca bất hợp lệ, hãy sinh các cặp ra ngoài phạm vi. Khi dùng random, lưu seed khi phát hiện lỗi để tái lập được.

Để tránh bài test “kiểm tra bằng chính hàm đang kiểm tra”, phần lớn các thuộc tính nên được xác minh độc lập bằng `length()`, `charAt()` và một mô hình oracle đơn giản, hoặc đối chiếu với quy tắc dữ liệu ban đầu. Không cần dùng random cho một hàm cực nhỏ nếu ma trận biên đã đủ; đây là kỹ thuật bổ sung hữu ích cho bộ tách nội dung tự viết.

## 13. Chỉ số inclusive của nghiệp vụ và API exclusive của Java

Một số tài liệu nghiệp vụ nói “lấy từ vị trí 2 **đến cả** vị trí 4”. Nếu hai đầu đều inclusive thì cần chuyển sang Java `substring(2, 5)`, không phải `substring(2, 4)`.

Ví dụ:

```java
String s = "ABCDEFG";
int firstInclusive = 2;
int lastInclusive = 4;

String result = s.substring(firstInclusive, lastInclusive + 1);
// CDE
```

Cách cộng 1 chỉ an toàn sau khi xác nhận `lastInclusive` là vị trí ký tự hợp lệ, nghĩa là `0 <= lastInclusive < s.length()`. Kiểm thử logic chuyển đổi phải bao gồm:
- `firstInclusive == lastInclusive` (lấy đúng một ký tự).
- `lastInclusive == s.length() - 1` (lấy đến ký tự cuối).
- `firstInclusive > lastInclusive` (phạm vi sai).
- `lastInclusive < 0` hoặc `lastInclusive >= s.length()` (ngoài phạm vi).
- Chuỗi rỗng: **không có phần tử** nào để áp dụng khoảng inclusive gồm ít nhất một ký tự.

Một nguồn lỗi tương tự là **đếm vị trí từ 1** trong tài liệu hoặc giao diện, trong khi Java dùng chỉ số từ 0. Nếu người dùng chọn “ký tự thứ 3 đến thứ 5” theo cách đếm 1-based và bao gồm cả hai đầu, khoảng Java tương ứng là `substring(2, 5)`. Các quy tắc quy đổi phải được viết rõ trong API và test.

## 14. Phân biệt khoảng nguồn và khoảng mảng, offset/count

Không phải mọi API Java dùng cặp `begin, end`. Một số API dùng **offset và count**: điểm bắt đầu và **số phần tử**. Ví dụ:

```java
char[] chars = {'A', 'B', 'C', 'D', 'E'};

String a = String.valueOf(chars, 1, 3); // "BCD"
String b = new String(chars, 1, 3);      // "BCD"
```

Ở hai lệnh trên, tham số thứ ba là `count = 3`, **không phải `endIndex = 3`. Nếu nhầm hai quy ước, một bài test có thể đậu ngẫu nhiên ở trường hợp `offset = 0` nhưng hỏng ngay khi `offset > 0`.

| API | Tham số chỉ số | Ý nghĩa |
| --- | --- | --- |
| `s.substring(begin, end)` | `begin, end` | Chọn `[begin, end)` |
| `s.subSequence(start, end)` | `start, end` | Chọn `[start, end)` |
| `s.getChars(srcBegin, srcEnd, dst, dstBegin)` | `srcBegin, srcEnd` | Chọn `[srcBegin, srcEnd)` của nguồn |
| `String.valueOf(chars, offset, count)` | `offset, count` | Chọn `count` ký tự từ `offset` |
| `new String(chars, offset, count)` | `offset, count` | Chọn `count` ký tự từ `offset` |
| `s.charAt(index)` | `index` | Chọn một UTF-16 code unit tại `index` |

Khi thiết kế test, trước hết xác định **tham số thứ hai mang ý nghĩa gì**; không lấy công thức `end - begin` áp dụng cho `count` hoặc ngược lại.

## 15. Sai lầm phổ biến trong bài kiểm thử chỉ số

**1. Tưởng `endIndex` được bao gồm.** Điều này làm mất phần tử cuối hoặc gây `end + 1` sai. Với `substring`, điểm cuối luôn **exclusive**.

**2. Nghĩ `substring(length, length)` là lỗi.** Đây là khoảng rỗng hợp lệ. Nhưng `charAt(length)` vẫn là lỗi.

**3. Bỏ qua nguồn rỗng.** Trên `""`, chỉ `substring(0, 0)` hoặc `substring(0)` là các cách lấy khoảng rỗng hợp lệ trong phạm vi này.

**4. Test chỉ trường hợp có dữ liệu.** Lệch `+1/-1` thường lộ ở biên, khoảng một ký tự hoặc khoảng rỗng.

**5. So sánh thông báo exception nguyên văn.** Dạng message có thể khác giữa JDK; ưu tiên kiểm tra kiểu ngoại lệ theo hợp đồng và điều kiện gây lỗi.

**6. Dùng `length()` như số ký tự người dùng nhìn thấy.** Đây là số UTF-16 code unit. Cắt giữa hai surrogate hợp lệ về mặt index nhưng có thể hỏng dữ liệu hiển thị.

**7. Không kiểm tra `indexOf` trả `-1`.** Cộng 1 quá sớm biến `-1` thành `0`, khiến một số lỗi thiếu delimiter trở nên khó phát hiện.

**8. Bỏ qua null.** `source.substring(...)` trên tham chiếu null ném `NullPointerException` trước khi kiểm tra khoảng. Hàm wrapper có thể chủ động xác định ngoại lệ/giá trị mặc định riêng.

**9. Nhầm offset/count với begin/end.** Truyền `String.valueOf(chars, 2, 5)` như thể muốn đến chỉ số 5 thực ra yêu cầu lấy **5** phần tử từ 2, có thể vượt biên.

**10. Kiểm tra ngoại lệ quá rộng.** Bắt mọi `Exception` có thể che lỗi hoàn toàn khác. Khi kiểm thử hành vi cụ thể, kiểm tra họ ngoại lệ thích hợp và không bỏ qua sai lệch.

## 16. Checklist kiểm thử trước khi hoàn thành

Với mỗi hàm tách chuỗi, xác nhận các câu hỏi dưới đây:

- Khoảng sử dụng **`[begin, end)`**, **inclusive/inclusive** hay **offset/count**?
- `length()` được hiểu là UTF-16 code unit hay chương trình thực sự yêu cầu code point/grapheme?
- Các mốc **`0`**, **`length()`**, **`length()-1`** đã được kiểm tra đúng vị trí chưa?
- Có test cho chuỗi rỗng, khoảng rỗng đầu/giữa/cuối, ký tự đầu/cuối và toàn chuỗi không?
- Đã test begin âm, end vượt biên, begin lớn hơn end, và ô đầu vào null nếu API nhận tham chiếu chưa?
- Nếu ranh giới được tính từ delimiter, đã kiểm tra kết quả `indexOf == -1` và delimiter trùng nhau/chồng lấn chưa?
- Nếu mảng đích bị thay đổi, đã kiểm tra **nội dung ngoài vùng ghi vẫn giữ nguyên** chưa?
- Nếu cần Unicode chính xác, đã test surrogate pair và định nghĩa đơn vị “ký tự” chưa?
- Khi sai phạm vi, test kiểm tra **kiểu lỗi phù hợp**, và khi hợp lệ, test so sánh **nội dung chính xác** chứ không chỉ “không lỗi” chưa?

## 17. Ghi nhớ

- Java `String.substring(begin, end)`, `subSequence(start, end)` và `getChars(srcBegin, srcEnd, ...)` dùng **khoảng nửa mở `[begin, end)`**.
- **Khoảng hợp lệ:** `0 <= begin <= end <= length()`. **Ký tự hợp lệ:** `0 <= index < length()`.
- `end == length()` và `begin == end` là các trường hợp **hợp lệ** của `substring`; dữ liệu trả có thể rỗng.
- Kiểm thử phải bao phủ **kết quả đúng** và **ngoại lệ đúng** ở cả hai đầu phạm vi.
- Chỉ số của `String` dựa trên **UTF-16 code unit**; một emoji có thể dài 2 code unit và có thể bị cắt giữa cặp surrogate.
- Các API dùng `offset, count` phải được kiểm thử khác với API dùng `begin, end`.
- Khi tính biên từ `indexOf`, luôn xử lý giá trị `-1` trước khi cắt.
- Với hàm trích xuất tự viết, dùng các thuộc tính như độ dài `end - begin`, bảo toàn nội dung và kiểm tra ngoại lệ để ngăn lỗi lệch một đơn vị.

Tài liệu tham khảo chính thức: [Java SE 17 — String](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html), [Java SE 17 — CharSequence](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/CharSequence.html), [Java SE 17 — Character](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html), [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-testing-003">Kiểm thử surrogate pair, surrogate đơn và dấu kết hợp</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử surrogate pair, surrogate đơn và dấu kết hợp trong Java String

Một chuỗi có thể hiển thị bình thường nhưng chứa các cấu trúc Unicode khiến phép đếm độ dài, cắt chuỗi, mã hóa UTF-8, so sánh và kiểm thử hoạt động ngoài dự đoán. Ba nhóm đầu vào cần kiểm tra riêng là **surrogate pair hợp lệ**, **surrogate đứng đơn** và **dấu kết hợp** (combining mark). Chúng không đồng nghĩa:

- Surrogate pair hợp lệ là cách UTF-16 biểu diễn **một Unicode code point ngoài BMP** bằng **hai đơn vị mã**.
- Surrogate đứng đơn (unpaired surrogate) là một đơn vị mã surrogate thiếu phần ghép tương ứng, tạo thành **chuỗi UTF-16 không hợp lệ** dù Java `String` vẫn có thể chứa nó.
- Dấu kết hợp là Unicode code point hợp lệ có thể kết hợp với ký tự đứng trước để tạo một chữ nhìn thấy; nhiều code point có thể thuộc **cùng một cụm chữ** (*grapheme cluster*).

Mục tiêu của bài là lập được bộ ca kiểm thử phân biệt ba trường hợp, xác nhận hành vi của `String`/`Character`, xử lý đầu vào mã hóa không hợp lệ một cách chủ động và tránh phá vỡ văn bản khi cắt hoặc chuẩn hóa.

## 1. Bốn đơn vị phải phân biệt trước khi viết test

| Đơn vị | API điển hình | Ví dụ với `"A😀B"` |
| --- | --- | ---: |
| **UTF-16 code unit** (đơn vị mã) | `s.length()`, `s.charAt(i)`, chỉ số `substring` | 4 |
| **Unicode code point** | `s.codePointCount(0, s.length())`, `s.codePoints()` | 3 |
| **Grapheme cluster** (cụm chữ hiển thị) | Bộ phân đoạn Unicode phù hợp, ví dụ `BreakIterator` trong phạm vi nó hỗ trợ | 3 trong ví dụ đơn giản này |
| **Byte sau mã hóa** | `s.getBytes(StandardCharsets.UTF_8).length` | 6 byte UTF-8 |

`String.length()` đếm đơn vị mã UTF-16 theo hợp đồng Java, **không đếm code point, cụm chữ hay byte UTF-8**. Java có thể tối ưu cách lưu trữ `String` bên trong JVM, nhưng ý nghĩa của `length()` không đổi.

Một bộ test đáng tin nên quyết định ngay từ đầu yêu cầu đang đặt giới hạn lên đơn vị nào. Ví dụ: “tối đa 20 byte UTF-8” cần đo byte đã mã hóa, “không cắt giữa emoji” đòi hỏi giữ ranh giới cụm chữ, còn “vị trí `charAt`” là chỉ số đơn vị mã UTF-16.

## 2. Surrogate pair hợp lệ

Các giá trị surrogate được chia thành:

- **High surrogate:** `U+D800`–`U+DBFF`.
- **Low surrogate:** `U+DC00`–`U+DFFF`.

Một high surrogate theo ngay sau bởi một low surrogate đúng thứ tự biểu diễn một Unicode code point từ `U+10000` đến `U+10FFFF`. Ví dụ `😀` (`U+1F600`) được viết trong một literal Java là `"\uD83D\uDE00"`. Hai `char` liên tiếp này tạo thành **một** code point.

Đoạn mã minh họa (đặt trong phương thức Java):

~~~java
String s = "A\uD83D\uDE00B";

System.out.println(s.length());                       // 4
System.out.println(s.codePointCount(0, s.length()));   // 3
System.out.println(Character.isHighSurrogate(s.charAt(1))); // true
System.out.println(Character.isLowSurrogate(s.charAt(2)));  // true
System.out.println(Character.isSurrogatePair(
        s.charAt(1), s.charAt(2)));                    // true
System.out.printf("U+%X%n", s.codePointAt(1));         // U+1F600
~~~

Ở đây chỉ số `1` trỏ đến high surrogate và `2` trỏ đến low surrogate. `codePointAt(1)` ghép hai đơn vị mã thành `U+1F600`, còn `charAt(1)` chỉ trả về **một** `char` (high surrogate).

**Lỗi cần bắt khi cắt chuỗi:**

~~~java
String broken = s.substring(1, 2); // chỉ chứa high surrogate
String intact = s.substring(1, 3); // chứa đủ cặp surrogate
~~~

`substring(begin, end)` dùng chỉ số đơn vị mã UTF-16. Vì vậy cả hai câu lệnh đều hợp lệ về mặt chỉ số, nhưng `broken` có UTF-16 không hợp lệ. Nếu cắt tại ranh giới code point, hãy cân nhắc `offsetByCodePoints`:

~~~java
int end = s.offsetByCodePoints(0, 2); // end == 3
String firstTwoCodePoints = s.substring(0, end); // "A😀"
~~~

Đây vẫn là thao tác theo **code point**. Với emoji gồm nhiều code point nối bằng ZWJ, code point boundary chưa chắc trùng ranh giới grapheme cluster.

## 3. Surrogate đứng đơn: `String` vẫn có thể chứa đầu vào sai

Hai chuỗi sau được Java cho phép tạo, dù chúng không đại diện cho chuỗi Unicode scalar value hợp lệ:

~~~java
String highOnly = "\uD83D";
String lowOnly = "\uDE00";
~~~

Cả hai có `length() == 1`. Bất ngờ hơn, `codePointCount(0, 1)` vẫn trả về `1`, và `codePointAt(0)` có thể trả về giá trị surrogate chưa ghép, ví dụ `0xD83D`. **Không được dùng `codePointCount` như bộ xác thực UTF-16.** `Character.isHighSurrogate` và `Character.isLowSurrogate` giúp kiểm tra loại đơn vị mã; cần xác nhận chúng xuất hiện thành cặp đúng thứ tự.

| Đầu vào | UTF-16 hợp lệ? | `length()` | `codePointCount(...)` | Điều đáng kiểm thử |
| --- | --- | ---: | ---: | --- |
| `"\uD83D\uDE00"` | Có | 2 | 1 | Cặp high/low đúng thứ tự |
| `"\uD83D"` | Không | 1 | 1 | High không có low theo sau |
| `"\uDE00"` | Không | 1 | 1 | Low đứng đơn |
| `"\uD83D\uD83D"` | Không | 2 | 2 | High theo sau bởi high |
| `"\uDE00\uD83D"` | Không | 2 | 2 | Low trước high |
| `"X\uD83D\uDE00Y"` | Có | 4 | 3 | Surrogate pair nằm giữa chuỗi |

Một bộ kiểm tra đơn giản duyệt từng `char`: khi thấy high surrogate phải có low surrogate ngay sau, rồi bỏ qua cả cặp; nếu gặp low surrogate khi không đang ghép, đầu vào sai. Chương trình đầy đủ ở phần 6 triển khai chính xác quy tắc này.

### Điều gì xảy ra khi mã hóa UTF-8?

`String.getBytes(StandardCharsets.UTF_8)` có thể **thay thế dữ liệu mã hóa không hợp lệ** bằng chuỗi byte thay thế; đó không phải giải pháp kiểm tra đầu vào nghiêm ngặt và có thể làm mất thông tin. Nếu yêu cầu từ chối surrogate đứng đơn, dùng `CharsetEncoder` với `CodingErrorAction.REPORT` để nhận `CharacterCodingException`.

~~~java
StandardCharsets.UTF_8.newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .encode(CharBuffer.wrap(highOnly)); // báo CharacterCodingException
~~~

Đoạn mã này thuộc ví dụ trong phương thức có import phù hợp ở phần 6. Tránh khẳng định mảng byte thu được từ mã hóa thay thế “vẫn chứa ký tự cũ”: surrogate chưa ghép **không có biểu diễn UTF-8 hợp lệ**. Cần xác định chính sách ứng dụng: từ chối, làm sạch bằng quy tắc đã thống nhất, hoặc thay thế có thông báo.

## 4. Dấu kết hợp và Unicode normalization

Ví dụ chữ `é` có ít nhất hai biểu diễn Unicode phổ biến:

- **Composed/precomposed:** `"\u00E9"` gồm một code point `U+00E9`.
- **Decomposed:** `"e\u0301"` gồm `U+0065` (e) và `U+0301` (dấu sắc kết hợp).

Hai chuỗi thường **hiển thị giống nhau**, nhưng `String.equals` so sánh biểu diễn các đơn vị mã và trả về `false`:

~~~java
String decomposed = "e\u0301";
String composed = "\u00E9";

System.out.println(decomposed.length()); // 2
System.out.println(composed.length());   // 1
System.out.println(decomposed.equals(composed)); // false
System.out.println(Normalizer.normalize(
        decomposed, Normalizer.Form.NFC
).equals(composed)); // true
~~~

Đây là đoạn mã minh họa dùng `java.text.Normalizer`. Khi được mã hóa UTF-8, `decomposed` chiếm 3 byte còn `composed` chiếm 2 byte. Việc chuẩn hóa theo NFC có thể làm thay đổi cả số đơn vị mã và số byte trong khi hình thức hiển thị gần như giữ nguyên.

**Surrogate pair và dấu kết hợp cần các kiểm thử khác nhau.** `"e\u0301"` là UTF-16 hoàn toàn hợp lệ, gồm hai code point nhưng thường tạo thành **một cụm chữ**. Cắt theo `substring(0, 1)` giữ lại `"e"` không tạo UTF-16 sai, nhưng làm **mất dấu sắc**. Ngược lại, cắt giữa high/low surrogate tạo UTF-16 không hợp lệ.

`Normalizer.Form.NFC` thực hiện canonical composition; nếu hệ thống cần canonical decomposition, dùng `NFD`. `NFKC`/`NFKD` còn bao gồm biến đổi tương thích có thể làm đổi ý nghĩa biểu diễn với nhiều ký tự; không dùng tùy tiện. Chuẩn hóa **không phải bước sửa tất cả malformed surrogate**; cần kiểm tra UTF-16 hợp lệ riêng.

Khi chuẩn hóa trước lưu trữ, so sánh, tìm kiếm, tạo checksum hoặc ký số, các bên phải thống nhất **chính xác dạng chuẩn hóa và thời điểm áp dụng**. Không âm thầm normalize sau khi tính hash hoặc chữ ký.

## 5. Ma trận kiểm thử nên có

| Trường hợp | Chuỗi kiểm thử | Cần xác nhận |
| --- | --- | --- |
| ASCII | `"ABC"` | `length` = code point count = số byte UTF-8 |
| Pair hợp lệ | `"A\uD83D\uDE00B"` | 4 units, 3 code points, 6 byte UTF-8; cả cặp hợp lệ |
| Pair bị cắt | `"A\uD83D\uDE00B".substring(1, 2)` | Tạo high surrogate đứng đơn, bộ kiểm tra phải báo sai |
| High đơn | `"\uD83D"` | Chuỗi UTF-16 sai; strict UTF-8 encoder phải từ chối |
| Low đơn | `"\uDE00"` | Chuỗi UTF-16 sai; strict UTF-8 encoder phải từ chối |
| Sai thứ tự | `"\uDE00\uD83D"` | Không ghép được; phát hiện low đứng đơn và high đứng đơn |
| Hai high | `"\uD83D\uD83D"` | Không phải cặp hợp lệ |
| Dấu kết hợp | `"e\u0301"` | 2 code points, một cụm chữ trong tình huống này; 3 byte UTF-8 |
| Chữ dựng sẵn | `"\u00E9"` | 1 code point, 2 byte UTF-8; so với dạng decomposed |
| Chuỗi rỗng | `""` | Không lỗi, các phép đếm bằng 0 |
| Ranh giới đầu/cuối | `"\uD83D\uDE00A"` và `"A\uD83D\uDE00"` | Không sai chỉ số; tránh cắt nửa cặp |
| Emoji nối / variation | Các chuỗi có ZWJ, variation selector hoặc regional indicator | Test phân đoạn **grapheme cluster** riêng, không giả định một code point là một emoji |

Nhóm kiểm thử đầu tiên kiểm tra đúng hợp đồng Java UTF-16. Nhóm thứ hai kiểm tra chính sách xử lý dữ liệu hỏng. Nhóm thứ ba kiểm tra hành vi hiển thị/chuẩn hóa. Khi viết test tự động, nên có cả ví dụ cố định và ca đầu vào được tạo có kiểm soát.

## 6. Chương trình Java 17+ kiểm thử tự động

Tạo file `UnicodeEdgeCasesTest.java`. Đây là chương trình **độc lập, không cần JUnit**: mỗi phép `check` tăng biến đếm và ném `AssertionError` khi một điều kiện sai, nên test vẫn hoạt động ngay cả khi không bật JVM flag `-ea`. Ngoài các phép đếm, chương trình kiểm tra valid/invalid UTF-16, strict UTF-8 encoding, NFC, phân đoạn dấu kết hợp và khả năng round trip.

~~~java
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.BreakIterator;
import java.text.Normalizer;
import java.util.Locale;

public class UnicodeEdgeCasesTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        testSurrogatePair();
        testUnpairedSurrogates();
        testCombiningMarks();
        testUtf8RoundTrip();
        System.out.println("PASS: " + assertions + " checks");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static boolean wellFormedUtf16(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= s.length() ||
                        !Character.isLowSurrogate(s.charAt(i + 1))) return false;
                i++;
            } else if (Character.isLowSurrogate(c)) {
                return false;
            }
        }
        return true;
    }

    private static int strictUtf8Size(String s) throws CharacterCodingException {
        ByteBuffer buffer = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(s));
        return buffer.remaining();
    }

    private static void expectMalformed(String s) throws Exception {
        try {
            strictUtf8Size(s);
            throw new AssertionError("Expected malformed UTF-16");
        } catch (CharacterCodingException expected) {
            check(true, "Malformed UTF-16 rejected by encoder");
        }
    }

    private static int graphemes(String s) {
        BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.ROOT);
        iterator.setText(s);
        int count = 0;
        for (int end = iterator.first(), next = iterator.next();
                next != BreakIterator.DONE;
                end = next, next = iterator.next()) {
            count++;
        }
        return count;
    }

    private static void testSurrogatePair() {
        String text = "A\uD83D\uDE00B";
        check(text.length() == 4, "UTF-16 units");
        check(text.codePointCount(0, text.length()) == 3, "Code points");
        check(Character.isHighSurrogate(text.charAt(1)), "High surrogate");
        check(Character.isLowSurrogate(text.charAt(2)), "Low surrogate");
        check(Character.isSurrogatePair(text.charAt(1), text.charAt(2)), "Pair");
        check(text.codePointAt(1) == 0x1F600, "Combined code point");
        check(text.offsetByCodePoints(0, 2) == 3, "Code point index");
        check(wellFormedUtf16(text), "Valid text");
        check(wellFormedUtf16(text.substring(1, 3)), "Safe substring");
        check(!wellFormedUtf16(text.substring(1, 2)), "Split pair");
    }

    private static void testUnpairedSurrogates() throws Exception {
        String high = "\uD83D";
        String low = "\uDE00";
        check(high.length() == 1 && low.length() == 1, "Single units");
        check(high.codePointCount(0, 1) == 1, "High counted");
        check(low.codePointCount(0, 1) == 1, "Low counted");
        check(high.codePointAt(0) == 0xD83D, "Unpaired high codePointAt");
        check(low.codePointAt(0) == 0xDE00, "Unpaired low codePointAt");
        check(!wellFormedUtf16(high) && !wellFormedUtf16(low), "Invalid UTF-16");
        expectMalformed(high);
        expectMalformed(low);
    }

    private static void testCombiningMarks() {
        String decomposed = "e\u0301";
        String composed = "\u00E9";
        check(decomposed.length() == 2, "Two UTF-16 units");
        check(decomposed.codePointCount(0, decomposed.length()) == 2, "Two points");
        check(composed.length() == 1, "Single precomposed code unit");
        check(!decomposed.equals(composed), "Different code units");
        check(Normalizer.normalize(decomposed, Normalizer.Form.NFC).equals(composed), "NFC");
        check(decomposed.getBytes(StandardCharsets.UTF_8).length == 3, "3 bytes");
        check(composed.getBytes(StandardCharsets.UTF_8).length == 2, "2 bytes");
        check(wellFormedUtf16(decomposed), "Combining well-formed");
        check(graphemes(decomposed) == 1, "One grapheme");
        check(decomposed.substring(0, 1).equals("e"), "Split combining sequence");
    }

    private static void testUtf8RoundTrip() throws Exception {
        String valid = "A\uD83D\uDE00B";
        byte[] bytes = valid.getBytes(StandardCharsets.UTF_8);
        check(bytes.length == 6, "UTF-8 encoded bytes");
        check(new String(bytes, StandardCharsets.UTF_8).equals(valid), "Round trip");
        check(strictUtf8Size(valid) == 6, "Strict encoding");
        String loneHigh = "\uD83D";
        check(!new String(loneHigh.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8).equals(loneHigh), "Replacement loses malformed unit");
    }
}
~~~

Biên dịch và chạy với JDK 17+:

~~~bash
javac -encoding UTF-8 UnicodeEdgeCasesTest.java
java UnicodeEdgeCasesTest
~~~

Đầu ra chính xác khi tất cả điều kiện đều đạt:

~~~text
PASS: 32 checks
~~~

**Giải thích cách test hoạt động:**

1. `testSurrogatePair` xác nhận `charAt` trả từng phần high/low, `Character.isSurrogatePair` nhận diện cặp, `codePointAt` trả `U+1F600`, và `substring(1, 2)` gây surrogate đứng đơn. `offsetByCodePoints(0, 2)` trả chỉ số UTF-16 là `3`.
2. `testUnpairedSurrogates` cố ý tạo high và low đơn. `codePointCount` vẫn đếm chúng nhưng `wellFormedUtf16` trả `false`, và encoder UTF-8 cấu hình `REPORT` từ chối.
3. `testCombiningMarks` tách biệt “hợp lệ về mặt Unicode” với “cùng hình thức hiển thị”: `decomposed` và `composed` không `equals`; NFC đưa chúng về cùng dạng, kích thước UTF-8 ban đầu khác nhau.
4. `testUtf8RoundTrip` xác nhận chuỗi có surrogate pair hợp lệ có thể đi qua UTF-8 và giải mã trở lại nguyên vẹn; với high surrogate đứng đơn, đường mã hóa có thay thế không giữ lại chuỗi gốc.

Hàm `wellFormedUtf16` duyệt từng đơn vị mã, **bỏ qua low surrogate khi đã xác thực một high surrogate**, và từ chối low đứng đơn. Phương thức `strictUtf8Size` mã hóa sang UTF-8 bằng `CharsetEncoder`: với dữ liệu hợp lệ, `ByteBuffer.remaining()` là số byte; với dữ liệu hỏng, `CharacterCodingException` cho phép test xác nhận lỗi thay vì bỏ qua.

Hàm `graphemes` minh họa `BreakIterator.getCharacterInstance(Locale.ROOT)` trên **trường hợp đơn giản** `e` + dấu sắc kết hợp. `BreakIterator` phụ thuộc dữ liệu/quy tắc phân đoạn của phiên bản JDK và không mặc nhiên là lời giải đầy đủ cho tất cả chuỗi emoji ZWJ hoặc những quy tắc grapheme Unicode mới nhất. Nếu sản phẩm yêu cầu cắt/đếm cụm chữ cho emoji phức tạp, hãy chọn công cụ phân đoạn đáp ứng phiên bản Unicode và các ca kiểm thử thực tế (ví dụ một thư viện như ICU4J khi phù hợp), rồi kiểm thử chéo với dữ liệu mẫu.

## 7. Kiểm thử chỉ số và ranh giới khi cắt chuỗi

Với chuỗi `"A😀B"`, phép thử hữu ích nhất là tạo **cả kết quả đúng và kết quả hỏng**:

~~~java
String input = "A\uD83D\uDE00B";

String bad = input.substring(1, 2);  // chỉ high surrogate
String good = input.substring(1, 3); // đủ surrogate pair
~~~

Cả hai lời gọi `substring` đều hợp lệ về mặt API và **không tự ném exception**. Lỗi xuất hiện khi dữ liệu hỏng được chuyển mã hoặc xử lý sau này. Với chỉ số do người dùng/chương trình tính toán, cần kiểm tra:

- Không vượt `0..length()` và không đảo thứ tự `begin/end`.
- Không nằm giữa high và low surrogate khi yêu cầu bảo toàn code point.
- Không tách dấu kết hợp khỏi ký tự cơ sở hoặc tách một chuỗi ZWJ khi yêu cầu bảo toàn **cụm chữ hiển thị**.
- Phép round trip `UTF-16 String → UTF-8 bytes → String` giữ nguyên với đầu vào hợp lệ và charset đúng; xử lý có quy tắc với đầu vào không hợp lệ.

Có thể tính ranh giới theo code point bằng `offsetByCodePoints` nhưng cần hiểu rằng **lời gọi này không xác thực surrogate đơn** và cũng không đảm bảo ranh giới grapheme. Chính sách hiển thị và chính sách mã hóa là hai tầng kiểm thử riêng.

## 8. Kiểm thử mã hóa: `getBytes`, encoder nghiêm ngặt và decoder nghiêm ngặt

Đối với văn bản hợp lệ:

~~~java
String input = "A\uD83D\uDE00B";
byte[] utf8 = input.getBytes(StandardCharsets.UTF_8);
String restored = new String(utf8, StandardCharsets.UTF_8);

// utf8.length == 6; restored.equals(input) == true
~~~

Mã ví dụ này nằm trong phương thức có import `StandardCharsets`. Nó kiểm tra kết quả theo **byte UTF-8**, không dựa vào `String.length()`.

Khi mã hóa surrogate đứng đơn, `getBytes(StandardCharsets.UTF_8)` thông thường thực hiện thay thế, dẫn đến **không bảo toàn** giá trị ban đầu. Khi yêu cầu dữ liệu Unicode phải hợp lệ, dùng encoder `REPORT` như chương trình ở phần 6.

Tương tự, khi **nhận byte UTF-8 từ ngoài**, `new String(bytes, UTF_8)` có thể thay thế byte sai bằng ký tự thay thế. Để từ chối, sử dụng decoder nghiêm ngặt:

~~~java
String strictDecoded = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString();
~~~

Đây là đoạn mã trong phương thức đã có `bytes` và các import cần thiết; nếu byte đầu vào hỏng, nó có thể ném `CharacterCodingException`. Không nhầm hai trường hợp: **surrogate đứng đơn trong Java String là UTF-16 hỏng**, còn **byte sequence UTF-8 hỏng** là lỗi tại bước giải mã; cả hai đều cần ca kiểm thử thích hợp tại ranh giới nhận/xuất dữ liệu.

## 9. Kiểm thử tương đương ngữ nghĩa và normalization

`String.equals` so sánh biểu diễn chuỗi, vì vậy:

~~~java
String a = "e\u0301";
String b = "\u00E9";
boolean rawEqual = a.equals(b); // false

String nfcA = Normalizer.normalize(a, Normalizer.Form.NFC);
String nfcB = Normalizer.normalize(b, Normalizer.Form.NFC);
boolean normalizedEqual = nfcA.equals(nfcB); // true
~~~

Đây là đoạn mã minh họa dùng `Normalizer`. Test nên xác định rõ một trong các chính sách:

- **So sánh byte/biểu diễn chính xác:** hai cách viết không tương đương nếu các đơn vị mã khác nhau; ví dụ dữ liệu đã ký hoặc định danh được đặc tả theo byte.
- **So sánh sau chuẩn hóa Unicode:** thống nhất dạng (NFC/NFD/... theo hợp đồng), chuẩn hóa trước khi so sánh và trước các thao tác liên quan như tạo khóa hay checksum.
- **So sánh gần đúng cho tìm kiếm người dùng:** có thể cần nhiều quy tắc thêm ngoài normalization, ví dụ case folding hoặc collator theo locale; không lấy `Normalizer` làm bộ so sánh ngôn ngữ hoàn chỉnh.

Đừng cho rằng hai chuỗi hiển thị giống nhau luôn có cùng `length()`, cùng byte UTF-8 hoặc cùng thứ tự code point. Test cần thể hiện đúng hợp đồng của hệ thống thay vì áp một quy tắc chung cho mọi dữ liệu.

## 10. Những lỗi thường gặp trong test Unicode

**Lỗi 1 — Chỉ chạy test với ASCII.** ASCII khiến `length()`, code point count và số byte UTF-8 thường bằng nhau, nên bỏ sót đa số vấn đề Unicode. Bổ sung emoji, ký tự ngoài Latin-1 và dấu kết hợp.

**Lỗi 2 — Test `codePointCount` để khẳng định “UTF-16 hợp lệ”.** Surrogate đơn vẫn được đếm như một code point theo hành vi của các API duyệt chuỗi Java. Cần bộ kiểm tra cặp surrogate hoặc encoder cấu hình `REPORT`.

**Lỗi 3 — Thấy `substring` không lỗi thì coi văn bản vẫn nguyên vẹn.** Cắt giữa surrogate pair có thể tạo chuỗi hỏng dù lời gọi thành công. Cắt giữa base và dấu kết hợp có thể tạo chuỗi hợp lệ nhưng mất ý nghĩa hiển thị.

**Lỗi 4 — Chuẩn hóa trong test nhưng không chuẩn hóa ở production.** Kết quả test không phản ánh hành vi thật. Xác định rõ normalization đặt ở bước nào và kiểm thử chính đường xử lý đó.

**Lỗi 5 — Coi một code point là một ký tự trên màn hình.** `e\u0301` gồm 2 code point nhưng thường hiện như một chữ. Emoji phức tạp cũng có thể nhiều code point; cần test phân đoạn grapheme cluster khi liên quan đến con trỏ, chiều dài hiển thị hay cắt văn bản.

**Lỗi 6 — Đếm ký tự bằng `String.length()` cho hạn mức byte UTF-8.** Với `"A😀B"`, `length()` là 4 nhưng UTF-8 cần 6 byte. Hãy đo mảng byte thực sự sau khi áp dụng cách mã hóa của giao thức.

**Lỗi 7 — Kiểm thử decoder thay thế mà nghĩ hệ thống đã từ chối byte sai.** Chế độ thay thế và chế độ `REPORT` là hai hợp đồng khác nhau; cần test cả kết quả thành công và trường hợp ném lỗi theo đúng yêu cầu.

**Lỗi 8 — Phụ thuộc font, terminal hoặc ảnh chụp màn hình để kết luận.** Hiển thị phụ thuộc font và renderer. Test các code point, độ dài, byte UTF-8 và normalization bằng giá trị có thể so sánh; chỉ dùng kiểm thử hiển thị bổ sung cho nhu cầu UI.

## 11. Câu hỏi tự kiểm tra

**1.** Với `"\uD83D\uDE00"`, `length()` và `codePointCount` trả bao nhiêu?

**Đáp án:** Lần lượt **2** đơn vị mã UTF-16 và **1** code point (`U+1F600`).

**2.** Với `"\uD83D"`, vì sao `codePointCount(0, 1) == 1` không chứng minh dữ liệu hợp lệ?

**Đáp án:** API có thể tính surrogate đứng đơn như một phần tử khi duyệt; một high surrogate không có low ghép cặp là UTF-16 không hợp lệ. Cần kiểm tra cấu trúc surrogate.

**3.** Vì sao `"e\u0301".substring(0, 1)` khác với việc cắt giữa surrogate pair?

**Đáp án:** `"e"` vẫn là chuỗi Unicode hợp lệ nhưng mất dấu kết hợp; nửa surrogate là **UTF-16 không hợp lệ**. Đúng về encoding và đúng về grapheme là hai tiêu chí riêng.

**4.** Có nên dùng `String.equals` để khẳng định `"e\u0301"` và `"\u00E9"` bằng nhau?

**Đáp án:** Không: `equals` trực tiếp trả `false`. Nếu hợp đồng cho phép canonical equivalence, chuẩn hóa cùng dạng, ví dụ NFC, rồi so sánh.

**5.** Khi ứng dụng từ chối surrogate không ghép cặp trước khi xuất UTF-8, chọn `String.getBytes(UTF_8)` hay `CharsetEncoder` cấu hình `REPORT`?

**Đáp án:** Chọn encoder nghiêm ngặt với `REPORT` để phát hiện lỗi thay vì âm thầm thay thế.

## 12. Checklist áp dụng vào dự án

Khi sửa lỗi ký tự hỏng, độ dài sai, search không tìm ra văn bản hoặc emoji bị cắt, hãy xác định lần lượt: nguồn dữ liệu ở dạng UTF-16 hay byte UTF-8; quy tắc từ chối/thay thế đầu vào sai; đơn vị đo mong muốn (code unit, code point, grapheme hay byte); thời điểm chuẩn hóa; ranh giới cắt; và các ca kiểm thử bằng giá trị Unicode cụ thể. Bộ kiểm thử ở phần 6 có thể chạy bằng JDK 17+, sau đó bổ sung trường hợp đặc trưng của dữ liệu ứng dụng.

Tài liệu tham khảo: [`String` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html), [`Character` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Character.html), [`CharsetEncoder`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/charset/CharsetEncoder.html), [`Normalizer`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/text/Normalizer.html), [`BreakIterator`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/text/BreakIterator.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-testing-004">Kiểm thử trim, strip, NBSP và các loại xuống dòng</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử `trim`, `strip`, NBSP và các loại xuống dòng trong Java

## 1. Vấn đề: “khoảng trắng” không có một định nghĩa duy nhất

Trong ứng dụng Java, dữ liệu nhận từ biểu mẫu, CSV, file, API, clipboard hoặc văn bản đa ngôn ngữ có thể chứa nhiều ký tự trông giống khoảng trắng. Hai chuỗi nhìn trên màn hình gần như giống nhau vẫn có thể khác về giá trị Unicode và hành vi của `trim()`, `strip()` hoặc phép tách dòng.

Ba khái niệm cần phân biệt:

- **Khoảng trắng theo `String.trim()`:** các `char` ở **hai đầu** có giá trị không lớn hơn `U+0020`. Đây là quy tắc lịch sử, không phải toàn bộ Unicode whitespace.
- **Khoảng trắng theo `String.strip()`:** các code point ở **hai đầu** được `Character.isWhitespace(int)` xem là whitespace. Quy tắc rộng hơn cho nhiều ký tự Unicode, nhưng **cố ý không bao gồm** ba loại khoảng trắng không ngắt: NBSP (`U+00A0`), FIGURE SPACE (`U+2007`), NARROW NO-BREAK SPACE (`U+202F`).
- **Dấu kết thúc dòng:** tùy API có thể là LF, CR, CRLF hoặc các dấu xuống dòng Unicode như NEL, LINE SEPARATOR và PARAGRAPH SEPARATOR. Xóa whitespace đầu/cuối và *tách dòng* là hai bài toán khác nhau.

Khi kiểm thử, cần làm rõ mình muốn **bỏ whitespace ở hai đầu**, **phân tách các dòng**, hay **chuẩn hóa ký tự xuống dòng**. Không được coi ba việc này là tương đương.

## 2. `trim()` và `strip()`: cơ chế và ví dụ

`String.trim()` có từ các phiên bản Java cũ; nó trả về chuỗi bỏ dãy `char` có giá trị `<= U+0020` ở **đầu** và **cuối**. Những ký tự nằm giữa chuỗi không bị đụng tới. Vì tiêu chí dựa trên mã `char`, `trim()` có thể loại bỏ cả một số ký tự điều khiển không nhất thiết được người dùng gọi là dấu cách.

`String.strip()` (Java 11+) loại bỏ các code point đầu/cuối thỏa `Character.isWhitespace(int)`. Đây thường là lựa chọn phù hợp hơn với văn bản Unicode, nhưng cũng **không** có nghĩa là “loại bỏ mọi ký tự nhìn có vẻ trống”.

| Đầu/cuối chuỗi là | Mã | `trim()` | `strip()` | Giải thích |
| --- | --- | --- | --- | --- |
| SPACE thông thường | `U+0020` | Loại bỏ | Loại bỏ | Cả hai nhận diện. |
| TAB, LF, CR | `U+0009`, `U+000A`, `U+000D` | Loại bỏ | Loại bỏ | Các ký tự điều khiển trong cả hai tập. |
| EM SPACE | `U+2003` | Giữ | Loại bỏ | `Character.isWhitespace` trả `true`. |
| IDEOGRAPHIC SPACE | `U+3000` | Giữ | Loại bỏ | Một ví dụ không phải ASCII được `strip` loại bỏ. |
| NBSP (NO-BREAK SPACE) | `U+00A0` | Giữ | **Giữ** | `Character.isWhitespace` trả `false`. |
| FIGURE SPACE | `U+2007` | Giữ | **Giữ** | Thuộc nhóm non-breaking theo API. |
| NARROW NO-BREAK SPACE | `U+202F` | Giữ | **Giữ** | Thuộc nhóm non-breaking theo API. |
| LINE SEPARATOR | `U+2028` | Giữ | Loại bỏ | Là whitespace theo `Character.isWhitespace`, nhưng không phải delimiter của `String.lines()`. |
| NEL (NEXT LINE) | `U+0085` | Giữ | Giữ | Được regex `\R` nhận diện là line break, không nằm trong định nghĩa Java whitespace của `strip`. |

**Điểm rất dễ nhầm:** `Character.isSpaceChar(codePoint)` và `Character.isWhitespace(codePoint)` là hai phép kiểm tra **khác nhau**. NBSP, FIGURE SPACE và NARROW NO-BREAK SPACE được `isSpaceChar` nhận diện nhưng bị loại trừ có chủ đích khỏi `isWhitespace`. Vì `strip` dựa vào `isWhitespace`, chúng không bị `strip` xóa.

Các API hữu ích khác (Java 11+):

- `stripLeading()`: chỉ loại bỏ whitespace theo `Character.isWhitespace` ở **đầu**.
- `stripTrailing()`: chỉ loại bỏ ở **cuối**.
- `isBlank()`: trả `true` nếu chuỗi rỗng hoặc toàn bộ code point là Java whitespace. Ví dụ chuỗi chỉ chứa NBSP **không** được xem là blank bằng `isBlank()`.
- `String.stripIndent()` / text blocks giải quyết thụt lề trong những ngữ cảnh khác; không thay cho việc xác định ký tự cần làm sạch trong dữ liệu đầu vào.

Đối với dữ liệu nghiệp vụ, nếu đặc tả yêu cầu loại bỏ **cả NBSP** ở hai đầu, hãy viết quy tắc rõ ràng và kiểm thử riêng. Không âm thầm đổi định nghĩa “blank” trên toàn ứng dụng chỉ vì một giao diện người dùng hiển thị NBSP giống SPACE.

## 3. Dấu xuống dòng: LF, CR, CRLF và Unicode

Các dòng văn bản không nhất thiết sử dụng cùng chuỗi ký tự kết thúc:

| Tên | Biểu diễn Java / mã Unicode | Thường gặp |
| --- | --- | --- |
| LF (Line Feed) | `"\n"` / `U+000A` | File Unix/Linux, nhiều API, Git |
| CR (Carriage Return) | `"\r"` / `U+000D` | Dữ liệu cũ, một số nguồn giao tiếp |
| CRLF | `"\r\n"` / `U+000D U+000A` | Nhiều file/định dạng Windows và giao thức |
| VT (Vertical Tab) | `U+000B` | Dữ liệu điều khiển/format đặc biệt |
| FF (Form Feed) | `"\f"` / `U+000C` | Phân trang hoặc dữ liệu cũ |
| NEL (Next Line) | `U+0085` | Một số nguồn Unicode/cũ |
| LINE SEPARATOR | `U+2028` | Dấu phân cách dòng Unicode |
| PARAGRAPH SEPARATOR | `U+2029` | Dấu phân cách đoạn Unicode |

**Phân biệt API:**

- `String.lines()` (Java 11+) **chỉ** tách tại LF, CR, CRLF. `"\r\n"` là **một** dấu kết thúc dòng. Dòng trả về không chứa ký tự xuống dòng.
- `String.lines()` **không** tách tại `U+0085`, `U+2028` hoặc `U+2029`. Nó cũng không tạo một dòng rỗng bổ sung sau ký tự kết thúc dòng cuối cùng; chuỗi rỗng tạo **zero lines**.
- Trong `java.util.regex.Pattern`, biểu thức `\R` nhận diện một chuỗi xuống dòng: `\r\n` hoặc một ký tự trong nhóm LF, VT, FF, CR, NEL, LS, PS. Trong **chuỗi Java** phải viết `"\\R"` để truyền ký hiệu regex `\R`.
- `text.split("\\R", -1)` tách theo nhóm dấu xuống dòng Unicode và **giữ cả phần tử trống ở cuối** nhờ giới hạn âm `-1`. Nếu dùng overload `split("\\R")` (limit = 0), các phần tử rỗng ở cuối sẽ bị loại bỏ.
- `text.replaceAll("\\R", "\n")` chuẩn hóa các chuỗi kết thúc dòng được regex nhận diện thành LF. Đừng áp dụng bừa lên dữ liệu nhị phân, chuỗi được ký số hoặc những format mà dạng xuống dòng nguyên gốc mang ý nghĩa riêng.

**Đặc biệt:** `\s`, `\h`, `\v`, `\R` và `Character.isWhitespace` có các định nghĩa riêng; dùng `split("\\s+")` để xử lý xuống dòng hoặc NBSP mà không kiểm tra cờ regex có thể cho kết quả ngoài dự kiến. `\R` biểu diễn **cả một chuỗi kết thúc dòng**, trong đó CRLF được xử lý cùng nhau, nên là lựa chọn rõ ràng khi muốn tách nhiều kiểu xuống dòng.

## 4. Bài kiểm thử Java 17+ chạy độc lập

Tạo file **`StringWhitespaceChecks.java`**, lưu mã nguồn bằng UTF-8. Chương trình dùng các phép so sánh thực tế thay vì dựa vào mắt thường; hàm `cp(int)` tạo ký tự từ code point để NBSP và các dấu xuống dòng ẩn không bị editor tự thay thế.

```java
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class StringWhitespaceChecks {
    private static int passed;

    private static void check(String name, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(
                    name + " | expected=" + expected + ", actual=" + actual);
        }
        passed++;
    }

    private static String cp(int value) {
        return new String(Character.toChars(value));
    }

    public static void main(String[] args) {
        String em = cp(0x2003);
        String nbsp = cp(0x00A0);
        String figure = cp(0x2007);
        String narrow = cp(0x202F);
        String ls = cp(0x2028);
        String ps = cp(0x2029);
        String nel = cp(0x0085);

        // Kiểm thử trim/strip tại hai đầu.
        String ascii = " \tA\r\n ";
        check("trim ASCII", "A", ascii.trim());
        check("strip ASCII", "A", ascii.strip());

        String emWrapped = em + "A" + em;
        check("trim keeps EM SPACE", emWrapped, emWrapped.trim());
        check("strip removes EM SPACE", "A", emWrapped.strip());

        String nbspWrapped = nbsp + "A" + nbsp;
        check("trim keeps NBSP", nbspWrapped, nbspWrapped.trim());
        check("strip keeps NBSP", nbspWrapped, nbspWrapped.strip());

        String figureWrapped = figure + "A" + figure;
        String narrowWrapped = narrow + "A" + narrow;
        check("strip keeps FIGURE SPACE", figureWrapped, figureWrapped.strip());
        check("strip keeps NARROW NBSP", narrowWrapped, narrowWrapped.strip());

        check("NBSP isWhitespace", false, Character.isWhitespace(0x00A0));
        check("NBSP isSpaceChar", true, Character.isSpaceChar(0x00A0));
        check("FIGURE SPACE isWhitespace",
                false, Character.isWhitespace(0x2007));
        check("NARROW NBSP isWhitespace",
                false, Character.isWhitespace(0x202F));
        check("EM SPACE isWhitespace", true, Character.isWhitespace(0x2003));

        check("stripLeading", "A" + em, emWrapped.stripLeading());
        check("stripTrailing", em + "A", emWrapped.stripTrailing());
        check("trim does not change internal whitespace",
                "A \t B", (" A \t B ").trim());

        // Kiểm thử các kiểu xuống dòng.
        String crLfOnly = "a\r\nb\nc\rd";
        check("String.lines CRLF LF CR",
                List.of("a", "b", "c", "d"),
                crLfOnly.lines().toList());

        String unicodeLineBreaks = "a" + ls + "b" + ps + "c" + nel + "d";
        check("lines keeps Unicode separators",
                List.of(unicodeLineBreaks),
                unicodeLineBreaks.lines().toList());
        check("regex R splits Unicode separators",
                List.of("a", "b", "c", "d"),
                Arrays.asList(unicodeLineBreaks.split("\\R", -1)));

        String mixed = "A\r\nB\nC\rD" + ls + "E" + nel + "F" + ps + "G";
        check("regex R splits mixed line endings",
                List.of("A", "B", "C", "D", "E", "F", "G"),
                Arrays.asList(mixed.split("\\R", -1)));
        check("split negative limit keeps trailing empty",
                List.of("x", ""),
                Arrays.asList("x\n".split("\\R", -1)));
        check("String.lines does not add trailing empty",
                List.of("x"), "x\n".lines().toList());
        check("String.lines keeps actual blank line",
                List.of("a", "", "b"), "a\n\nb".lines().toList());
        check("empty string has no lines",
                List.of(), "".lines().toList());

        String lsWrapped = ls + "A" + ls;
        check("trim keeps LINE SEPARATOR", lsWrapped, lsWrapped.trim());
        check("strip removes LINE SEPARATOR", "A", lsWrapped.strip());

        check("normalize all recognized line endings",
                "A\nB\nC\nD\nE\nF\nG",
                mixed.replaceAll("\\R", "\n"));

        System.out.println("PASS: " + passed + " checks");
    }
}
```

Biên dịch, chạy:

```bash
javac -encoding UTF-8 StringWhitespaceChecks.java
java StringWhitespaceChecks
```

**Stdout khi tất cả kiểm thử đạt** trên Java 17+:

```text
PASS: 27 checks
```

Nếu hành vi khác với kỳ vọng, `check` ném `AssertionError` kèm tên kiểm thử đầu tiên bị lỗi và chương trình dừng trước khi in dòng `PASS`. Những giá trị như NBSP, LS, NEL được tạo bằng code point nên không lệ thuộc việc terminal có thể hiển thị chúng hay không.

### Điểm học được từ bộ kiểm thử

- SPACE và TAB ở biên được cả `trim` và `strip` xóa; EM SPACE chỉ bị `strip` xóa.
- NBSP và hai biến thể non-breaking không bị cả `trim` lẫn `strip` xóa. `isSpaceChar` có thể là `true` trong khi `isWhitespace` là `false`.
- `String.lines()` xử lý CR/LF/CRLF; `"\\R"` xử lý thêm dấu xuống dòng Unicode.
- Dòng rỗng **ở giữa** các dấu kết thúc dòng có thể là dữ liệu có nghĩa; dòng rỗng phát sinh **sau** một dấu kết thúc cuối cùng được `String.lines()` và `split` với limit `-1` xử lý khác nhau.
- Chuẩn hóa xuống dòng nên là một bước có chủ đích, độc lập với việc làm sạch whitespace ở biên.

## 5. Thiết kế kiểm thử theo ma trận thay vì chỉ thử dấu cách thông thường

Đừng chỉ test `" text ".trim()` rồi suy ra mọi dữ liệu đều an toàn. Hãy bao phủ ít nhất các chiều sau:

| Trục kiểm thử | Ca cần có | Điều xác minh |
| --- | --- | --- |
| Vị trí | Đầu, cuối, cả hai đầu, ở giữa chuỗi | Chỉ vị trí định xóa bị thay đổi. |
| Độ dài | Chuỗi rỗng, chỉ whitespace, nhiều ký tự liên tiếp | Không mất dữ liệu ngoài phạm vi. |
| Whitespace ASCII | SPACE, TAB, LF, CR, CRLF | `trim`/`strip` xử lý đúng đầu/cuối. |
| Whitespace Unicode | EM SPACE, IDEOGRAPHIC SPACE, LINE SEPARATOR | Phân biệt `trim` và `strip`. |
| Non-breaking | NBSP, FIGURE SPACE, NARROW NBSP | Các ký tự được giữ theo đúng hợp đồng. |
| Kiểu xuống dòng | LF, CR, CRLF, NEL, LS, PS | Lựa chọn đúng giữa `lines` và regex `\R`. |
| Dòng rỗng | Hai dấu kết thúc liền nhau, đầu chuỗi, cuối chuỗi | Không bỏ/thêm dòng do hiểu nhầm `split`/`lines`. |
| Dữ liệu Unicode | Tiếng Việt, emoji, ký tự kết hợp | Không vô tình xóa/sửa nội dung bên trong. |

Với JUnit 5, có thể chuyển các dòng `check` thành các ca `@ParameterizedTest` hoặc `@MethodSource`, dùng `assertEquals(expected, actual)` và đặt tên ca có cả code point (ví dụ `U+00A0`). Đây là hướng mở rộng test suite; chương trình ở trên chạy độc lập bằng JDK 17+ và **không cần thêm dependency**.

Một số kiểm thử cần chạy trên nhiều môi trường, đặc biệt khi dữ liệu đọc từ file hoặc HTTP. Tuy nhiên, nếu chuỗi `String` đã có đúng các code point, quy tắc `trim`, `strip` và `lines` phải theo hợp đồng API, không được phụ thuộc tùy tiện vào dấu xuống dòng của hệ điều hành.

## 6. Khi đọc file hoặc nhận dữ liệu từ bên ngoài

Một file Windows thường có CRLF, file Unix thường có LF; nhưng không phải mọi file đều tuân theo quy ước môi trường chạy. Không nên dùng `System.lineSeparator()` để đoán delimiter của file bất kỳ. `System.lineSeparator()` cho biết dấu xuống dòng **mặc định của nền tảng hiện tại**, chủ yếu có ích khi **tạo** văn bản cho nền tảng đó; nó không tự phát hiện loại xuống dòng của dữ liệu nguồn.

Ví dụ thao tác chuẩn hóa **minh họa** (đặt trong hàm đã có biến `text`):

```java
String normalized = text.replaceAll("\\R", "\n");
List<String> lines = Arrays.asList(normalized.split("\n", -1));
```

Đoạn trên dùng `java.util.Arrays` và `java.util.List`. `-1` bảo toàn phần tử rỗng cuối cùng nếu văn bản kết thúc bằng LF. Đây là lựa chọn khi dữ liệu nghiệp vụ cần lưu thông tin về dấu kết thúc dòng cuối; nếu không cần, có thể chọn chính sách khác và viết kiểm thử tương ứng.

Khi làm sạch đầu/cuối từng trường:

```java
String cleaned = field.strip();  // NBSP vẫn được giữ
```

Đây là **một quy tắc theo Java whitespace**, không phải bộ lọc tổng quát cho mọi khoảng trắng. Nếu yêu cầu sản phẩm nói “NBSP cũng được xóa”, có thể xây một hàm theo predicate công khai:

```java
// Mã minh họa: dùng bên trong một class và truyền vào String hợp lệ.
static boolean isBoundarySpace(int cp) {
    return Character.isWhitespace(cp)
            || cp == 0x00A0
            || cp == 0x2007
            || cp == 0x202F;
}

static String stripIncludingNbsp(String value) {
    int start = 0;
    int end = value.length();
    while (start < end) {
        int cp = value.codePointAt(start);
        if (!isBoundarySpace(cp)) break;
        start += Character.charCount(cp);
    }
    while (start < end) {
        int cp = value.codePointBefore(end);
        if (!isBoundarySpace(cp)) break;
        end -= Character.charCount(cp);
    }
    return value.substring(start, end);
}
```

Đoạn minh họa sử dụng **code point**, nên không cắt đôi surrogate pair. Hàm chỉ xóa ở hai đầu, bảo toàn phần nội dung giữa chuỗi. Chính sách xóa NBSP là lựa chọn của ứng dụng và cần kiểm thử riêng; không gán nó cho hành vi mặc định của `String.strip()`.

Nếu dữ liệu được giải mã sai charset trước khi vào `String`, `strip` hoặc `trim` không thể chữa mojibake. Cần biết encoding thật của file/giao thức (thường dùng UTF-8 khi do mình định nghĩa), giải mã đúng rồi mới xử lý whitespace.

## 7. Các lỗi phổ biến cần phát hiện sớm

**Lỗi 1 — cho rằng `strip()` loại bỏ NBSP.** Dữ liệu sao chép từ website hoặc tài liệu có thể chứa `U+00A0` ở đầu/cuối; `strip()` vẫn trả về chuỗi còn NBSP. Kiểm tra code point thực tế thay vì nhìn vào khoảng trắng trên màn hình.

**Lỗi 2 — dùng `trim()` thay `strip()` cho whitespace Unicode.** `trim()` giữ `U+2003` và `U+3000`. Nếu đầu vào cần xử lý các dấu cách này, `strip()` thường thích hợp hơn.

**Lỗi 3 — lấy `String.lines()` để tách mọi loại xuống dòng Unicode.** `lines()` chỉ có ba loại delimiter LF/CR/CRLF. Dùng `split("\\R", -1)` hoặc parser chuyên dụng nếu đặc tả yêu cầu NEL, LS, PS.

**Lỗi 4 — nghĩ CRLF là hai dòng rỗng.** `String.lines()` và regex `\R` đều nhận CRLF là **một** terminator, nên `"A\r\nB"` có hai dòng `A` và `B`.

**Lỗi 5 — quên tham số `-1` của `split`.** `"A\n".split("\\R")` bỏ phần tử rỗng cuối, còn `split("\\R", -1)` giữ. Cần quyết định đầu ra mong muốn trước khi viết test.

**Lỗi 6 — xóa toàn bộ whitespace mà không xét ngữ nghĩa.** Trong tên, địa chỉ, văn bản pháp lý, số lượng có dấu phân cách hoặc dữ liệu có định dạng, NBSP và dòng rỗng có thể mang ý nghĩa trình bày. Tự động thay mọi whitespace bằng SPACE có thể làm thay đổi dữ liệu cần bảo toàn.

**Lỗi 7 — kiểm tra bằng console.** Ký tự U+00A0, U+2003, LF và CR khó phân biệt khi in trực tiếp. Dùng biểu diễn `U+XXXX` hoặc test `codePointAt`/`equals`; không kết luận chỉ từ hình thức.

**Lỗi 8 — phụ thuộc nền tảng khi sinh dữ liệu test.** Viết literal `"\n"`/`"\r\n"` hoặc tạo code point là cách kiểm thử deterministic. Tránh tạo dữ liệu tham chiếu bằng `System.lineSeparator()` khi mục tiêu là so sánh các loại xuống dòng xuyên hệ điều hành.

## 8. Checklist khi đưa vào sản phẩm

1. Xác định rõ chính sách: loại bỏ whitespace kiểu `trim`, kiểu `strip`, hay thêm NBSP/nhóm ký tự riêng.
2. Liệt kê code point cần hỗ trợ, đặc biệt `U+00A0`, `U+2007`, `U+202F`, `U+2003`, `U+0085`, `U+2028`, `U+2029`.
3. Chọn API tách dòng theo yêu cầu: `lines()` cho CR/LF/CRLF; `"\\R"` khi cần các kiểu Unicode rộng hơn.
4. Quyết định chính sách giữ/bỏ dòng rỗng cuối; nếu dùng `split`, đặt limit tường minh.
5. Test cả dữ liệu ở đầu, giữa và cuối chuỗi, bao gồm chuỗi rỗng và nhiều delimiter liên tiếp.
6. Khi chuẩn hóa, chỉ thay ký tự có chủ đích và giữ bản gốc nếu dữ liệu quan trọng.

## Tài liệu chính thức

- [Java 17 `String.trim`, `strip`, `lines`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html).
- [Java 17 `Character.isWhitespace` và `Character.isSpaceChar`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html).
- [Java 17 `Pattern`: `\R`, ký tự kết thúc dòng và `split`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-testing-005">Kiểm thử ==, equals, intern và biểu thức hằng</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử `==`, `equals`, `intern` và biểu thức hằng trong Java String

Hai biến `String` có thể **chứa cùng văn bản** nhưng **trỏ tới hai đối tượng khác nhau**. Ngược lại, nhiều biến có thể cùng trỏ tới một đối tượng nhờ quy tắc chia sẻ string literal và cơ chế `intern()`. Khi kiểm thử, cần phân biệt đúng **nội dung chuỗi**, **định danh đối tượng**, **biểu thức hằng lúc biên dịch** và **kết quả được tạo ở runtime**.

Ví dụ cốt lõi:

```java
String literal = "java";
String anotherLiteral = "java";
String fresh = new String("java");

System.out.println(literal == anotherLiteral); // true
System.out.println(literal == fresh);          // false
System.out.println(literal.equals(fresh));     // true
System.out.println(literal == fresh.intern());  // true
```

Cả bốn kết quả đều có lý do cụ thể. Một bộ kiểm thử tốt phải xác nhận **đúng điều mà API cam kết**, thay vì đoán kết quả `==` chỉ dựa trên việc văn bản giống nhau.

## 1. Bốn khái niệm cần phân biệt

| Cách kiểm tra | Thực chất kiểm tra | Nên dùng khi |
| --- | --- | --- |
| `a == b` | Hai biến tham chiếu có trỏ tới **cùng một đối tượng** (hoặc cùng `null`) không | Kiểm tra định danh hoặc quy tắc pooling một cách có chủ đích |
| `a.equals(b)` | `a` và `b` có **cùng nội dung** theo `String.equals` không | Hầu hết bài toán so sánh giá trị văn bản |
| `a.intern()` | Trả về chuỗi **canonical** đại diện cho nội dung của `a` | Khi thực sự cần chuẩn hóa tham chiếu chuỗi |
| Biểu thức hằng `"ab" + "cd"` | Kết quả được tính ở **compile time**; chuỗi tương ứng được intern như literal | Kiểm thử quy tắc hằng của ngôn ngữ |

Với `String`:

- `equals` so sánh chuỗi ký tự theo đúng thứ tự và giá trị (phân biệt chữ hoa/chữ thường); không dựa trên tham chiếu.
- `==` trên hai biến kiểu `String` so sánh tham chiếu; không đọc từng ký tự.
- `intern()` không thay đổi nội dung hay “biến đổi” đối tượng gốc; nó trả về một **tham chiếu** đến đại diện canonical có cùng nội dung.
- Không phải mọi chuỗi có nội dung giống literal đều **tự động** được intern khi được tạo ở runtime.

`String` là immutable, nhưng **immutable không có nghĩa mọi instance cùng nội dung đều là một instance**.

## 2. Tại sao hai string literal thường có `==` bằng `true`?

Theo Java Language Specification, string literal có cùng nội dung biểu diễn cùng một instance `String`. Vì vậy:

```java
String first = "coffee";
String second = "coffee";
System.out.println(first == second); // true
```

Đây là hành vi **được đặc tả**, phù hợp để viết một bài kiểm thử giải thích cơ chế literal.

Một biểu thức gồm các hằng `String`, toán tử `+` và các thành phần là *constant expression* cũng được tính ở **compile time**. Kết quả chuỗi của biểu thức hằng được intern:

```java
String word = "coffee";
String folded = "co" + "ffee";
System.out.println(word == folded); // true
```

Trình biên dịch có thể lưu literal `"coffee"` dùng chung trong class file; không cần tạo một đối tượng mới chỉ để ghép hai đoạn `"co"` và `"ffee"` khi chương trình chạy.

**Điểm quan trọng:** `==` bằng `true` ở đây không chứng minh rằng **mọi** phép nối chuỗi đều dùng chung đối tượng. Ta phải kiểm tra xem biểu thức có thật sự là **biểu thức hằng theo ngôn ngữ Java** hay không.

## 3. Biểu thức hằng, biến `final` và phép nối runtime

### Trường hợp A — tất cả phần của phép nối là hằng

```java
String a = "coffee";
String b = "co" + "ffee";

System.out.println(a == b); // true
```

`"co" + "ffee"` là biểu thức hằng kiểu `String`. Trình biên dịch gộp nội dung thành `"coffee"`; `b` dùng tham chiếu canonical.

### Trường hợp B — biến hằng `final String`

```java
final String PREFIX = "co";
String a = "coffee";
String b = PREFIX + "ffee";

System.out.println(a == b); // true
```

Một biến `final` kiểu nguyên thủy hoặc kiểu `String` được khởi tạo bằng **constant expression** là *constant variable*. `PREFIX` trong ví dụ là constant variable, vì vậy `PREFIX + "ffee"` vẫn là constant expression và có thể gộp khi biên dịch.

### Trường hợp C — biến không phải hằng

```java
String prefix = "co";
String a = "coffee";
String b = prefix + "ffee";

System.out.println(a == b);      // false
System.out.println(a.equals(b)); // true
```

`prefix` không phải constant variable vì nó không khai báo `final`. Phép nối là thao tác runtime, tạo kết quả với cùng nội dung nhưng khác tham chiếu với literal. Trình biên dịch và JVM có thể chọn kỹ thuật nối chuỗi khác nhau, song **không nên viết chương trình dựa vào `==` để kiểm tra nội dung của phép nối runtime**.

### Trường hợp D — `final` nhưng giá trị khởi tạo không phải biểu thức hằng

```java
final String prefix = new String("co");
String a = "coffee";
String b = prefix + "ffee";

System.out.println(a == b);      // false
System.out.println(a.equals(b)); // true
```

Từ khóa `final` chỉ cấm gán lại biến `prefix`. Nó **không khiến `new String("co")` trở thành compile-time constant**. Bởi vậy `prefix + "ffee"` vẫn được đánh giá ở runtime.

| Biểu thức tạo `b` | Là compile-time constant expression? | `"coffee" == b` |
| --- | --- | --- |
| `"co" + "ffee"` | Có | `true` |
| `final String p = "co"; b = p + "ffee";` | Có | `true` |
| `String p = "co"; b = p + "ffee";` | Không | `false` |
| `final String p = new String("co"); b = p + "ffee";` | Không | `false` |
| `new String("coffee")` | Không | `false` |
| `new StringBuilder().append("co").append("ffee").toString()` | Không | `false` |

Bảng này nói về những **đoạn mã cụ thể** có giá trị bằng `"coffee"` và cách tạo đối tượng được mô tả. Đối với dữ liệu thực tế, hãy xác nhận nội dung bằng `equals`.

## 4. `new String(...)` và `StringBuilder.toString()`: cùng nội dung, khác đối tượng

`new String("coffee")` biểu thị yêu cầu tạo một đối tượng `String` mới; dù nó nhận giá trị từ literal đã intern, kết quả vẫn là một instance riêng:

```java
String literal = "coffee";
String fresh = new String("coffee");

System.out.println(literal == fresh);      // false
System.out.println(literal.equals(fresh)); // true
```

Tương tự, chuyển nội dung từ một `StringBuilder` thành `String` cho kết quả văn bản hợp lệ; không nên giả định rằng chuỗi này được intern:

```java
String literal = "coffee";
String built = new StringBuilder()
        .append("co")
        .append("ffee")
        .toString();

System.out.println(literal == built);      // false
System.out.println(literal.equals(built)); // true
```

**Quy tắc kiểm thử:** nếu chức năng của hệ thống chỉ yêu cầu cùng văn bản, một test như `assertEquals("coffee", actual)` có giá trị; một test như `assertSame("coffee", actual)` có thể áp đặt thêm ràng buộc **về định danh đối tượng** mà chức năng không hề yêu cầu.

## 5. `intern()` bảo đảm điều gì?

`String.intern()` trả về tham chiếu đến **đối tượng chuỗi canonical** có nội dung bằng chuỗi được gọi. Nếu một chuỗi có nội dung tương đương đã ở trong bảng intern, tham chiếu tương ứng được trả về; nếu chưa có, cơ chế intern thiết lập một đại diện canonical theo hợp đồng của API.

Bảo đảm hữu ích nhất:

```java
String x = new String("coffee");
String y = new StringBuilder()
        .append("co")
        .append("ffee")
        .toString();

System.out.println(x == y);                 // false
System.out.println(x.equals(y));            // true
System.out.println(x.intern() == y.intern()); // true
```

Vì `x` và `y` có cùng nội dung, kết quả `intern()` của chúng là **cùng tham chiếu**. Với literal đã biết:

```java
String literal = "coffee";
String fresh = new String("coffee");

System.out.println(fresh == literal);          // false
System.out.println(fresh.intern() == literal); // true
System.out.println(fresh == literal);          // false
```

Lời gọi `fresh.intern()` **không làm biến `fresh` tự đổi sang tham chiếu canonical**. Nếu muốn sử dụng kết quả intern về sau, phải gán hoặc dùng trực tiếp tham chiếu trả về:

```java
String canonical = fresh.intern();
```

### Không kiểm tra chi tiết triển khai bằng `==`

Một số ví dụ ngoài thực tế dựa trên giả định “lần đầu gọi `intern()` trên một chuỗi tạo ở runtime sẽ luôn trả về chính instance đó”. Bảo đảm mà code ứng dụng nên dùng là **những chuỗi bằng nội dung sẽ có kết quả `intern()` trùng tham chiếu**; không xây dựng logic quanh chi tiết đối tượng nào được lưu trước hay thời điểm JVM khởi tạo pool.

Việc interning còn có chi phí quản lý tài nguyên. Không cần gọi `intern()` chỉ để so sánh nội dung: `equals` thường trực tiếp, dễ hiểu và đúng mục đích hơn.

## 6. Bài kiểm thử Java 17+ chạy độc lập

Tạo file `StringComparisonDemo.java`. Chương trình tự kiểm tra 16 điều kiện và **ném `AssertionError` khi bất kỳ điều kiện nào sai**; vì vậy nó có thể chạy như một test độc lập mà không cần JUnit hoặc bật JVM assertion.

```java
import java.util.Objects;

public class StringComparisonDemo {
    private static int checks;

    private static void check(
            String name, boolean actual, boolean expected) {
        if (actual != expected) {
            throw new AssertionError(name + " expected "
                    + expected + " but got " + actual);
        }
        checks++;
    }

    public static void main(String[] args) {
        String literal = "coffee";
        String repeated = "coffee";
        String folded = "co" + "ffee";

        final String constantPrefix = "co";
        String viaConstant = constantPrefix + "ffee";

        String variablePrefix = "co";
        String viaVariable = variablePrefix + "ffee";

        final String nonconstantPrefix = new String("co");
        String viaFinalObject = nonconstantPrefix + "ffee";

        String fresh = new String("coffee");
        String built = new StringBuilder()
                .append("co")
                .append("ffee")
                .toString();

        String nullable = null;

        check("repeated literals", literal == repeated, true);
        check("literal folding", literal == folded, true);
        check("constant variable folding", literal == viaConstant, true);
        check("runtime variable concatenation",
                literal == viaVariable, false);
        check("final reference is not always constant",
                literal == viaFinalObject, false);
        check("new String identity", literal == fresh, false);
        check("new String content", literal.equals(fresh), true);
        check("Objects.equals content",
                Objects.equals(literal, fresh), true);
        check("StringBuilder identity", literal == built, false);
        check("StringBuilder content", literal.equals(built), true);
        check("intern canonical result", literal == fresh.intern(), true);
        check("intern both strings",
                fresh.intern() == built.intern(), true);
        check("intern does not change fresh identity",
                literal == fresh, false);
        check("null-safe one null",
                Objects.equals(nullable, literal), false);
        check("null-safe two nulls",
                Objects.equals(nullable, null), true);
        check("non-null equals(null)",
                literal.equals(nullable), false);

        System.out.println("PASS: " + checks + " checks");
    }
}
```

Biên dịch và chạy:

```bash
javac StringComparisonDemo.java
java StringComparisonDemo
```

Kết quả chính xác:

```text
PASS: 16 checks
```

Đây là test tự kiểm tra hành vi thực, không chỉ in giá trị rồi yêu cầu người đọc tự so sánh. Mỗi lời gọi `check` có một kỳ vọng minh bạch; thất bại sẽ cho biết trường hợp kiểm thử nào sai. Chương trình **không dùng câu lệnh `assert` của Java**, vì `assert` bị tắt mặc định nếu không bật tùy chọn `-ea`.

Nếu muốn kiểm tra nhanh một trường hợp, có thể in biểu thức như ở các mục trước; khi tự động hóa, nên dùng điều kiện kiểm thử gây thất bại thật nếu kết quả khác kỳ vọng.

## 7. Viết kiểm thử bằng JUnit 5

Trong dự án đã có JUnit Jupiter, có thể dùng các assertion diễn đạt rõ mục đích:

- `assertEquals(expected, actual)`: kiểm tra **giá trị/nội dung** bằng logic `equals`.
- `assertSame(expected, actual)`: kiểm tra **cùng một tham chiếu**.
- `assertNotSame(unexpected, actual)`: kiểm tra **khác tham chiếu**.
- `assertTrue`, `assertFalse`: kiểm tra một điều kiện có mục đích rõ ràng.
- `assertNull` và `assertNotNull`: kiểm tra tham chiếu null.

Ví dụ file kiểm thử trong dự án dùng JUnit 5 (đoạn này **không chạy độc lập nếu chưa có dependency JUnit Jupiter**):

```java
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.Objects;

class StringComparisonTest {

    @Test
    void literalAndConstantExpressionShareReference() {
        String literal = "coffee";
        final String constantPrefix = "co";

        assertSame(literal, "co" + "ffee");
        assertSame(literal, constantPrefix + "ffee");
    }

    @Test
    void runtimeStringHasSameContentButDifferentReference() {
        String literal = "coffee";
        String generated = new String("coffee");

        assertNotSame(literal, generated);
        assertEquals(literal, generated);
    }

    @Test
    void internReturnsCanonicalReference() {
        String literal = "coffee";
        String generated = new String("coffee");

        assertSame(literal, generated.intern());
        assertNotSame(literal, generated);
    }

    @Test
    void nullComparisonIsSafeWhenRequested() {
        String absent = null;
        String present = "coffee";

        assertFalse(Objects.equals(absent, present));
        assertTrue(Objects.equals(absent, null));
        assertFalse(present.equals(absent));
    }
}
```

Những assertion dùng `assertSame` trong ví dụ chỉ kiểm tra **hành vi được đặc tả**: literal/constant expression được intern và kết quả từ `intern()` có cùng tham chiếu canonical. Khi kiểm thử một hàm chỉ có hợp đồng “trả văn bản chính xác”, hãy dùng `assertEquals`.

**Tránh:** dùng `assertSame("OK", service.getStatus())` nếu service không hứa trả về instance interned; dù giá trị đúng, test có thể thất bại do một instance mới hoàn toàn hợp lệ.

## 8. Kiểm thử với `null`: `equals` không tự bảo vệ biến nhận lời gọi

`==` so sánh tham chiếu và không ném `NullPointerException` chỉ vì một hoặc cả hai toán hạng là `null`:

```java
String a = null;
String b = null;

System.out.println(a == b); // true
```

Nhưng gọi phương thức instance qua tham chiếu `null` sẽ gây lỗi:

```java
String value = null;

// value.equals("ready") sẽ ném NullPointerException.
boolean safe1 = "ready".equals(value);  // false
boolean safe2 = Objects.equals(value, "ready"); // false
```

`"ready".equals(value)` phù hợp khi vế trái là literal chắc chắn khác null. `Objects.equals(a, b)` phù hợp khi **cả hai biến đều có thể null**: hai null được coi là bằng nhau, một null và một giá trị khác null là không bằng.

### `equals` và `equalsIgnoreCase` không có cùng tiêu chí

`String.equals` phân biệt chữ hoa/thường:

```java
System.out.println("Java".equals("java"));           // false
System.out.println("Java".equalsIgnoreCase("java")); // true
```

Nếu yêu cầu là so sánh không phân biệt chữ hoa/thường, viết test trực tiếp cho hành vi đó. Không nên dùng `==` như một cách “tối ưu hóa” thay cho bất kỳ kiểu so sánh nội dung nào.

## 9. Các lỗi kiểm thử thường gặp

1. **Dùng `==` để kiểm tra đầu ra nghiệp vụ của một hàm xử lý chuỗi.** Kết quả có thể cùng nội dung nhưng là instance khác; dùng `equals` hoặc `assertEquals` nếu hợp đồng chỉ nói về văn bản.
2. **Cho rằng `final` luôn tạo constant expression.** `final String p = new String("co")` không phải constant variable; `p + "ffee"` vẫn tính ở runtime.
3. **Không phân biệt nối hằng và nối runtime.** `"co" + "ffee"` là biểu thức hằng; `String prefix = "co"; prefix + "ffee"` thì không.
4. **Cho rằng `new String(literal)` được intern tự động.** Constructor tạo đối tượng mới; nội dung vẫn có thể bằng literal, nhưng tham chiếu khác.
5. **Cho rằng gọi `intern()` làm thay đổi biến gốc.** Nó trả về tham chiếu; `fresh` vẫn trỏ tới instance ban đầu nếu không gán lại.
6. **Phụ thuộc vào “lần intern đầu tiên trả chính object này”.** Không nên viết test buộc JVM dùng một chiến lược intern cụ thể vượt quá hợp đồng canonical hóa.
7. **Quên `null` khi gọi `equals`.** `nullable.equals(...)` có thể ném exception; dùng literal ở vế trái hoặc `Objects.equals` khi phù hợp.
8. **Nhầm `assertEquals` với `assertSame` của JUnit.** Assertion trước kiểm tra nội dung; assertion sau kiểm tra tham chiếu.
9. **Dùng `assert` rồi chạy Java không có `-ea`.** Một chương trình in “test passed” dù `assert` bị tắt là kết quả gây hiểu nhầm; JUnit hoặc bộ kiểm tra ném `AssertionError` tường minh phù hợp hơn.
10. **Kiểm tra identity ở mọi nơi vì nghĩ rằng pool tiết kiệm bộ nhớ.** Quy tắc tối ưu bộ nhớ không tự biến thành hợp đồng chức năng của hệ thống. Chỉ kiểm tra identity khi thật sự muốn kiểm chứng literal, constant expression hoặc `intern()`.

## 10. Tự luyện tập

**Câu 1.** Dự đoán hai kết quả:

```java
String a = "cat";
String b = "c" + "at";
System.out.println(a == b);
System.out.println(a.equals(b));
```

Đáp án: `true`, `true`. Biểu thức ghép hai literal được tính lúc biên dịch.

**Câu 2.** Dự đoán với biến không `final`:

```java
String prefix = "c";
String a = "cat";
String b = prefix + "at";
System.out.println(a == b);
System.out.println(a.equals(b));
```

Đáp án: `false`, `true`. Đây là phép nối ở runtime, nhưng nội dung vẫn bằng `cat`.

**Câu 3.** `final` có đủ để phép nối trở thành constant expression không?

Không. Cần biến `final` có kiểu nguyên thủy hoặc `String` và **được khởi tạo bằng constant expression**. Ví dụ `final String p = "c";` có thể được gộp; `final String p = new String("c");` thì không.

**Câu 4.** Phân biệt hai phép thử:

```java
String x = new String("cat");
String y = new String("cat");
System.out.println(x == y);
System.out.println(x.intern() == y.intern());
```

Đáp án: `false` rồi `true`. Hai đối tượng tạo riêng, nhưng kết quả intern cùng nội dung là một tham chiếu canonical.

**Câu 5.** Một hàm trả `new String("OK")` khi thành công. Nên kiểm tra `assertEquals("OK", actual)` hay `assertSame("OK", actual)`?

Nếu hợp đồng chỉ yêu cầu giá trị `OK` thì dùng `assertEquals`. `assertSame` sẽ đòi hỏi cùng một tham chiếu và không phản ánh đúng yêu cầu.

**Câu 6.** Khi cả hai biến `String` có thể `null`, cách so sánh nội dung an toàn là gì?

`Objects.equals(a, b)`. Nó cho `true` nếu cả hai `null`, `false` nếu chỉ một bên `null`, và dùng `equals` nếu cả hai khác null.

## 11. Checklist trước khi kết luận test

- **Bạn đang kiểm tra văn bản hay identity?** Văn bản dùng `equals`/`assertEquals`; identity dùng `==`/`assertSame`.
- **Biểu thức có thực sự là hằng ở compile time?** Kiểm tra xem mỗi biến tham gia có phải constant variable hay không.
- **Có `new String`, `StringBuilder` hoặc nối runtime không?** Khi đó không tự coi kết quả là interned.
- **Có dùng `intern()` không?** So sánh **tham chiếu trả về** và đừng nhầm với biến ban đầu.
- **Có khả năng null không?** Dùng `Objects.equals` hoặc literal ở bên trái lời gọi `equals`.
- **Kiểm thử có thật sự thất bại khi sai không?** Dùng JUnit, điều kiện ném `AssertionError` hoặc chạy Java assertion với `-ea` đúng cách.

**Ghi nhớ:** `==` và `assertSame` nói về **đối tượng**; `equals` và `assertEquals` nói về **nội dung**; `intern()` chuẩn hóa tham chiếu; *constant expression* quyết định trường hợp chuỗi được tính và intern ở compile time. Viết kiểm thử theo hợp đồng hành vi, sau đó mới quan tâm đến cơ chế pool nếu đó thực sự là mục tiêu kiểm thử.

## Tài liệu chính thức

- [`String.equals`, `String.intern` — Java SE 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html)
- [Java Language Specification, 3.10.5 — String Literals](https://docs.oracle.com/javase/specs/jls/se21/html/jls-3.html#jls-3.10.5)
- [Java Language Specification, 15.29 — Constant Expressions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.29)
- [Java Language Specification, 4.12.4 — final Variables](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.12.4)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-testing-006">Kiểm thử regex với dấu chấm, gạch chéo, đô la và nhóm</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử regex với dấu chấm, gạch chéo, đô la và nhóm trong Java

Khi viết regex trong Java, dấu **chấm `.`**, **gạch chéo `/`**, **dấu đô la `$`** và **ngoặc nhóm `(...)`** có ý nghĩa khác nhau tùy chúng đang nằm trong **mẫu regex**, **chuỗi Java** hay **chuỗi thay thế (replacement)**. Một biểu thức có vẻ đúng đôi khi khớp cả dữ liệu sai, trích xuất nhầm nhóm hoặc tạo kết quả thay thế ngoài ý muốn.

Bài này tập trung vào cách **kiểm thử các ký hiệu đó** bằng `java.util.regex.Pattern` và `Matcher` trên Java 17+, với test âm/dương, các đoạn mã chạy được và cách đọc kết quả. Hai nguyên tắc xuyên suốt: phân biệt **cú pháp regex** với **escape trong Java String literal**; phân biệt **mẫu tìm kiếm** với **cú pháp chuỗi thay thế**.

## 1. Bản đồ ý nghĩa của bốn nhóm ký hiệu

| Ký hiệu trong **regex** | Ý nghĩa khi đứng ở vị trí thông thường | Muốn khớp ký tự thật |
| --- | --- | --- |
| `.` | Khớp một ký tự bất kỳ, ngoại trừ line terminator nếu không bật `DOTALL` | `\.` |
| `/` (forward slash) | Dấu gạch chéo xuôi thông thường; Java `Pattern` không dùng `/` để bao regex | Cứ viết `/` |
| `\` (backslash) | Dấu escape hoặc mở đầu lớp viết tắt như `\d`; hữu ích khi kiểm thử đường dẫn Windows | Regex `\\` |
| `$` | Neo vị trí cuối đầu vào/vùng, có quy tắc đặc biệt quanh kết thúc dòng | `\$` |
| `(...)` | Tạo nhóm bắt; dùng để gom và truy xuất nội dung | `\(` và `\)` nếu muốn khớp ngoặc thật |
| `(?:...)` | Nhóm **không bắt**, chỉ gom các phần | Không tạo chỉ số nhóm |
| `(?<name>...)` | Nhóm bắt có tên | Truy xuất bằng `group("name")` |

**Quan trọng:** Bảng trên nói về **mẫu regex**, không phải cách viết chuỗi Java. Trong mã nguồn Java, dấu `\` của regex phải viết thành `\\`. Ví dụ regex `\.` phải truyền vào Java bằng `"\\."`. Còn regex `/` chỉ cần Java string `"/"`.

Trong tài liệu Java, `Pattern.compile(regex)` biên dịch mẫu, `Matcher` thực hiện khớp, và `String.matches(regex)` cũng kiểm tra **toàn bộ** chuỗi. Để tránh khó hiểu khi soạn test, nên lưu rõ regex và input, rồi xác định mong muốn **toàn chuỗi** hay **đoạn con** trước khi chạy.

## 2. Dấu chấm: wildcard `.` khác dấu chấm thật `\.`

Trong regex, `.` là một **metacharacter**. Mẫu `report.txt` có nghĩa là các chữ `report`, **một ký tự bất kỳ** rồi `txt`. Vì thế nó có thể khớp cả `reportXtxt`, `report-txt` hoặc `report/txt` khi dùng `matches()`.

Để chỉ chấp nhận dấu chấm thật, escape thành `\.` trong regex và viết `"\\."` trong Java:

~~~java
Pattern.matches("report.txt",  "reportXtxt"); // true: dấu . là wildcard
Pattern.matches("report\\.txt", "report.txt"); // true: dấu chấm thật
Pattern.matches("report\\.txt", "reportXtxt"); // false
~~~

Ba dòng trên **chỉ là lời gọi minh họa** (cần import `java.util.regex.Pattern` và đặt trong phương thức Java).

Dấu `.` mặc định **không khớp line terminator** như `\n`. Cờ `Pattern.DOTALL` (hoặc `(?s)` trong regex) cho phép `.` khớp cả line terminator:

~~~java
Pattern.matches("a.b", "a\nb"); // false
Pattern.compile("a.b", Pattern.DOTALL)
       .matcher("a\nb").matches(); // true
~~~

Đừng nhầm `DOTALL` với `MULTILINE`: `DOTALL` thay đổi cách `.` khớp xuống dòng; `MULTILINE` thay đổi cách neo `^` và `$` áp dụng với các dòng.

Một biến thể viết dấu chấm thật là lớp ký tự `[.]`. Cả regex `\.` và `[.]` đều khớp một ký tự `.` theo cách đang xét. Khi muốn tách file name theo dấu chấm, cũng phải nhớ `String.split(...)` nhận **regex**:

~~~java
String[] parts = "a.b".split("\\."); // ["a", "b"]
~~~

Đây là đoạn minh họa. Viết `"a.b".split(".")` khiến regex `.` khớp các ký tự thay vì đóng vai trò dấu phân cách `.`; kết quả không phải mảng mong muốn.

### Test tối thiểu cho dấu chấm

| Regex viết trong Java | Input | `matches()` | Vì sao |
| --- | --- | --- | --- |
| `"file.txt"` | `fileXtxt` | `true` | `.` nhận `X` |
| `"file\\.txt"` | `file.txt` | `true` | `\.` chỉ nhận dấu chấm |
| `"file\\.txt"` | `file-txt` | `false` | Thiếu dấu chấm thật |
| `"a.b"` | `a` + newline + `b` | `false` | `.` mặc định không nhận xuống dòng |
| `"(?s)a.b"` | `a` + newline + `b` | `true` | Bật DOTALL |

Nếu đầu vào chứa **ký tự người dùng cung cấp** như tên file `report.2026`, đừng tự thay mọi dấu chấm bằng các chuỗi escape thủ công trong một regex phức tạp; `Pattern.quote(userText)` phù hợp hơn để trích dẫn nguyên văn toàn bộ giá trị (phần 8).

## 3. Dấu gạch chéo: `/` không phải delimiter trong Java regex

Một số ngôn ngữ hoặc công cụ viết mẫu theo dạng `/pattern/flags` (ví dụ JavaScript regex literal), khiến người học tưởng mọi regex đều cần hai dấu `/` bao ngoài. Trong Java, `Pattern.compile("api/v1/users")` dùng **trực tiếp** nội dung chuỗi như mẫu, vì vậy dấu `/` đơn lẻ khớp đúng dấu slash trong đường dẫn.

~~~java
Pattern.matches("api/v\\d+/users", "api/v2/users"); // true
Pattern.matches("api/v\\d+/users", "api\\v2\\users"); // false
~~~

Hai dòng này là lời gọi minh họa. Regex `api/v\d+/users` có dấu slash thường, `\d+` mới là phần regex đặc biệt. Viết thêm hai dấu `/` bao ngoài, chẳng hạn `Pattern.compile("/api/v1/")`, sẽ khiến Java tìm **cả các dấu slash đó như nội dung của mẫu**; nó không tự hiểu đây là cặp delimiter.

Cần phân biệt **hai loại gạch chéo**:

- `/` (*forward slash*): thường không cần escape trong Java regex, rất phổ biến trong URL/path kiểu Unix.
- `\` (*backslash*): là dấu escape của regex **và** của chuỗi Java. Để khớp **một** ký tự backslash thật, regex cần `\\`; Java string literal truyền cho `Pattern` phải viết `"\\\\"`.

Ví dụ minh họa đường dẫn Windows trong Java:

~~~java
String path = "C:\\temp\\x.txt";              // dữ liệu: C:\temp\x.txt
String regex = "C:\\\\temp\\\\x\\.txt";        // regex tìm dấu \ thật và dấu . thật
boolean correct = Pattern.matches(regex, path); // true
~~~

Đoạn trên dùng `Pattern` sau khi import. Nếu viết `"C:\temp\x.txt"` trong Java source, chuỗi có thể chứa escape ngoài ý định (như `\t`) hoặc không biên dịch vì escape không hợp lệ. Khi xử lý **tên đường dẫn dạng văn bản**, cân nhắc `Path`/`Files` thay vì regex nếu mục tiêu là thao tác file; dấu phân cách phụ thuộc ngữ cảnh hệ điều hành và nguồn dữ liệu.

## 4. Dấu đô la: neo kết thúc `$` hay ký tự tiền tệ `\$`?

Trong **mẫu regex**, `$` là **neo vị trí**, không tiêu thụ ký tự. Ví dụ:

~~~java
Pattern.matches("^AB\\d{2}$", "AB12");  // true
Pattern.matches("^AB\\d{2}$", "xAB12"); // false
~~~

`^` neo đầu và `$` neo cuối theo quy tắc Java regex. Lệnh `matches()` vốn đã yêu cầu **toàn chuỗi** khớp; trong tình huống này neo giúp người đọc dễ thấy ý định nhưng không phải điều kiện bắt buộc. Ngược lại, khi dùng `find()`, dấu `^`/`$` thay đổi những vị trí đoạn con có thể khớp.

Nếu muốn kiểm tra **dấu `$` thật** của tiền tệ, viết regex `\$` và Java literal `"\\$"`:

~~~java
Pattern.matches("total=\\$\\d+", "total=$25"); // true
~~~

Regex ở ví dụ là `total=\$\d+`: tìm `total=`, dấu `$` thật, rồi một hoặc nhiều chữ số. Nếu bỏ backslash trước `$`, mẫu không còn nghĩa tương tự.

**Cẩn thận với xuống dòng cuối:** `$` có thể nhận vị trí trước một line terminator kết thúc chuỗi; với `Pattern.MULTILINE`, nó còn phù hợp ở cuối từng dòng. `\z` biểu diễn **cuối đầu vào tuyệt đối** khi cần kiểm soát vị trí thật chặt trong regex; dấu `\z` cần viết thành `"\\z"` trong Java. `Pattern.matches(...)` và `Matcher.matches()` vẫn là cách rõ ràng để kiểm tra **toàn bộ** input.

### Kiểm thử `MULTILINE`

~~~java
Pattern p = Pattern.compile("^cat$", Pattern.MULTILINE);
boolean foundLine = p.matcher("cat\nbird").find();    // true
boolean wholeText = p.matcher("cat\nbird").matches(); // false
~~~

`find()` tìm riêng dòng `cat` vì `^`/`$` được diễn giải theo từng dòng. `matches()` không cho phép bỏ phần `\nbird` còn lại.

## 5. Nhóm bắt và nhóm không bắt: `(...)`, `(?:...)` và `(?<name>...)`

Ngoặc tròn có hai vai trò chính:

1. **Gom phần mẫu** để lượng từ hay phép chọn `|` tác động lên cả nhóm, ví dụ `(ab)+` khác `ab+`.
2. **Bắt (capture) đoạn khớp** để lấy lại bằng `Matcher.group(...)` hoặc tham chiếu trong chuỗi thay thế.

Nhóm bắt mặc định được đánh số **1, 2, ...** theo thứ tự dấu `(` mở của nhóm bắt, tính từ trái sang phải. Nhóm `0` là **toàn bộ lần khớp**. `(?:...)` chỉ gom, **không tăng số thứ tự nhóm**. Nhóm đặt tên `(?<ext>txt|csv)` vừa là nhóm bắt vừa có thể truy xuất theo `"ext"`.

Ví dụ file đơn giản:

~~~java
Pattern pattern = Pattern.compile(
        "^(?<base>[a-z]+)\\.(?<ext>txt|csv)$");
Matcher m = pattern.matcher("notes.txt");

if (m.matches()) {
    System.out.println(m.group(0));      // notes.txt
    System.out.println(m.group("base")); // notes
    System.out.println(m.group("ext"));  // txt
}
~~~

Đây là đoạn minh họa (cần import `Pattern`/`Matcher`). Lưu ý `m.group("base")` là lời gọi API với **tên nhóm đã khai báo trong regex**. Mẫu `(?<base>[a-z]+)` dùng dấu ngoặc để bắt `notes`, còn `\\.` trong Java dùng để khớp dấu chấm trước phần mở rộng.

**Chỉ gọi `group()`, `start()`, `end()` sau khi `find()` hoặc `matches()` thành công.** Nếu không có match hiện hành, `Matcher` ném `IllegalStateException`. Với nhóm tùy chọn không tham gia, `group(n)` có thể là `null`, `start(n)`/`end(n)` là `-1`. Các chỉ số `start/end` là **chỉ số UTF-16**, và `end` là vị trí exclusive.

Khi chỉ cần gom mà không trích xuất, dùng nhóm không bắt, chẳng hạn `(?:txt|csv)`. Việc này giúp giảm nguy cơ vô tình thay đổi vị trí `group(1)` khi sửa regex:

~~~java
Pattern noCapture = Pattern.compile("^[a-z]+\\.(?:txt|csv)$");
boolean okay = noCapture.matcher("notes.txt").matches(); // true
~~~

## 6. Tên nhóm và dấu đô la trong replacement: quy tắc khác regex

Một trong những bẫy phổ biến nhất: trong **mẫu regex**, `$` nghĩa là **neo**; trong **chuỗi thay thế** của `Matcher.replaceAll`, `$1` hay `${name}` nghĩa là **chèn nội dung nhóm bắt**.

| Bối cảnh | Cú pháp | Nghĩa |
| --- | --- | --- |
| Regex | `$` | Neo cuối |
| Regex | `\$` | Dấu đô la thật |
| Regex | `(abc)` | Bắt nội dung vào nhóm 1 |
| Regex | `(?<name>abc)` | Bắt nhóm tên `name` |
| Replacement của `Matcher`/`String.replaceAll` | `$1` | Đưa nội dung nhóm 1 vào kết quả |
| Replacement | `${name}` | Đưa nội dung nhóm có tên vào kết quả |
| Replacement | `\$` | Escape một dấu `$` trong **chuỗi replacement**, không có nghĩa là neo |
| Replacement | `Matcher.quoteReplacement(text)` | Dùng `text` theo nghĩa nguyên văn, kể cả `$` và `\` |

Ví dụ:

~~~java
String result = Pattern.compile("id=(\\d+)")
        .matcher("id=42; id=7")
        .replaceAll("item[$1]");
// item[42]; item[7]
~~~

Mẫu `(\\d+)` (trong Java literal) bắt một dãy chữ số thành nhóm 1. Replacement `"item[$1]"` lấy lại nội dung nhóm đó. **`$1` chỉ có tác dụng đặc biệt trong replacement**, không phải cách viết tham chiếu ngược bên trong mẫu regex. Nếu cần tham chiếu lại nội dung nhóm trong regex, dùng `\1` (Java literal `"\\1"`) hoặc `\k<name>` (Java literal `"\\k<name>"`).

Với nhóm có tên:

~~~java
String reordered = Pattern.compile("(?<word>[a-z]+)-(?<num>\\d+)")
        .matcher("abc-12")
        .replaceAll("${num}:${word}");
// 12:abc
~~~

**Nếu dữ liệu replacement do người dùng cung cấp**, đưa thẳng `"$9\\temp"` vào `replaceAll` có thể khiến regex engine cố tham chiếu nhóm không tồn tại hoặc xem `\` là escape. Dùng `Matcher.quoteReplacement(...)`:

~~~java
String rawReplacement = "$9\\temp";
String safe = Pattern.compile("TOKEN")
        .matcher("path=TOKEN")
        .replaceAll(Matcher.quoteReplacement(rawReplacement));
// path=$9\temp
~~~

Trong Java, `String.replace(old, new)` thay các chuỗi **nguyên văn**; `String.replaceAll(regex, replacement)` dùng **regex** và cú pháp replacement. Nếu chỉ muốn thay dấu chấm bằng dấu gạch ngang, `text.replace(".", "-")` đơn giản hơn `text.replaceAll("\\.", "-")`.

## 7. Chương trình Java 17: kiểm thử dấu chấm, slash và đô la

Chương trình sau cố tình gồm cả trường hợp **đáng chấp nhận** và **đáng bác bỏ**, tránh chỉ kiểm thử đầu vào đẹp:

~~~java
// RegexSymbolsDemo.java
import java.util.regex.Pattern;

public class RegexSymbolsDemo {
    static void check(String name, String regex, String input) {
        System.out.println(name + " = " + Pattern.matches(regex, input));
    }

    public static void main(String[] args) {
        check("literal dot", "report\\.txt", "report.txt");
        check("reject fake dot", "report\\.txt", "reportXtxt");
        check("wildcard dot", "report.txt", "reportXtxt");
        check("slash path", "api/v\\d+/users", "api/v2/users");
        check("reject backslashes", "api/v\\d+/users", "api\\v2\\users");
        check("literal dollar", "total=\\$\\d+", "total=$25");
        check("end anchor", "^AB\\d{2}$", "AB12");
        check("reject prefix", "^AB\\d{2}$", "xAB12");
        check("dot not newline", "a.b", "a\nb");

        boolean dotAll = Pattern.compile("a.b", Pattern.DOTALL)
                .matcher("a\nb")
                .matches();
        System.out.println("dotall newline = " + dotAll);

        Pattern line = Pattern.compile("^cat$", Pattern.MULTILINE);
        System.out.println("multiline find = "
                + line.matcher("cat\nbird").find());
        System.out.println("multiline matches = "
                + line.matcher("cat\nbird").matches());
    }
}
~~~

Lưu thành `RegexSymbolsDemo.java`, sau đó chạy:

~~~bash
javac --release 17 RegexSymbolsDemo.java
java RegexSymbolsDemo
~~~

Stdout:

~~~text
literal dot = true
reject fake dot = false
wildcard dot = true
slash path = true
reject backslashes = false
literal dollar = true
end anchor = true
reject prefix = false
dot not newline = false
dotall newline = true
multiline find = true
multiline matches = false
~~~

**Cách đọc test:** `"wildcard dot = true"` là kết quả chạy **đúng theo ngữ nghĩa regex** nhưng lại là **hành vi sai nếu yêu cầu nghiệp vụ chỉ nhận dấu chấm thật**. Đây là lý do test regex phải có **negative cases** ngoài positive cases. `"multiline find"` và `"multiline matches"` không mâu thuẫn: chúng hỏi hai câu khác nhau về phạm vi khớp.

## 8. Chương trình Java 17: nhóm, replacement và dữ liệu cần escape

~~~java
// RegexGroupsAndReplacementDemo.java
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexGroupsAndReplacementDemo {
    public static void main(String[] args) {
        Pattern file = Pattern.compile(
                "^(?<base>[a-z]+)\\.(?<ext>txt|csv)$");
        Matcher m = file.matcher("notes.txt");

        System.out.println("matches = " + m.matches());
        System.out.println("full = " + m.group(0));
        System.out.println("base = " + m.group(1));
        System.out.println("extension = " + m.group("ext"));
        System.out.println("base range = "
                + m.start("base") + ".." + m.end("base"));

        String byNumber = Pattern.compile("id=(\\d+)")
                .matcher("id=42; id=7")
                .replaceAll("item[$1]");
        System.out.println("by number = " + byNumber);

        String byName = Pattern.compile(
                "(?<word>[a-z]+)-(?<num>\\d+)")
                .matcher("abc-12")
                .replaceAll("${num}:${word}");
        System.out.println("by name = " + byName);

        String literal = "$9\\temp";
        String safe = Pattern.compile("TOKEN")
                .matcher("path=TOKEN")
                .replaceAll(Matcher.quoteReplacement(literal));
        System.out.println("safe replacement = " + safe);
    }
}
~~~

Lệnh chạy:

~~~bash
javac --release 17 RegexGroupsAndReplacementDemo.java
java RegexGroupsAndReplacementDemo
~~~

Stdout:

~~~text
matches = true
full = notes.txt
base = notes
extension = txt
base range = 0..5
by number = item[42]; item[7]
by name = 12:abc
safe replacement = path=$9\temp
~~~

Ở chương trình này, `group(0)` trả về toàn bộ `notes.txt`, nhóm `base` trả về `notes`, còn `ext` trả về `txt`. Kết quả `base range = 0..5` là khoảng **bắt đầu gồm, kết thúc không gồm**. Test cho replacement cho thấy muốn đưa ký tự `$`/backslash nguyên văn vào kết quả thì `Matcher.quoteReplacement` tránh các quy tắc thay thế nhóm.

## 9. Chương trình Java 17: kiểm thử chuỗi literal động và backslash

Nếu một giá trị do người dùng nhập vào chỉ cần tìm **nguyên văn** trong dữ liệu, hãy dùng `Pattern.quote(...)`. Việc viết:

~~~java
Pattern.matches("v1.2/$", "v1.2/$");
~~~

không đảm bảo đúng theo nghĩa nguyên văn, vì dấu `.` đang là wildcard và `$` là neo (không nhận một ký tự đô la thật). Dùng `Pattern.quote` khi cả chuỗi cần coi như literal:

~~~java
// RegexLiteralInputDemo.java
import java.util.regex.Pattern;

public class RegexLiteralInputDemo {
    public static void main(String[] args) {
        String literal = "v1.2/$";
        String regex = "^" + Pattern.quote(literal) + "$";
        System.out.println("literal = "
                + Pattern.matches(regex, "v1.2/$"));

        String windows = "C:\\temp\\x.txt";
        String windowsRegex = "C:\\\\temp\\\\x\\.txt";
        System.out.println("windows = "
                + Pattern.matches(windowsRegex, windows));

        String price = "Cost: $25";
        String changed = Pattern.compile("\\$")
                .matcher(price)
                .replaceAll("USD ");
        System.out.println("changed = " + changed);

        String kept = Pattern.compile("\\$")
                .matcher(price)
                .replaceAll("\\$");
        System.out.println("kept = " + kept);
    }
}
~~~

Lệnh chạy:

~~~bash
javac --release 17 RegexLiteralInputDemo.java
java RegexLiteralInputDemo
~~~

Stdout:

~~~text
literal = true
windows = true
changed = Cost: USD 25
kept = Cost: $25
~~~

`Pattern.quote` biến `v1.2/$` thành mẫu tìm kiếm literal hoàn chỉnh cho phần dữ liệu được quote; cặp `^...$` bên ngoài thể hiện ý định ràng buộc toàn chuỗi (dù `matches()` đã thực hiện việc đó). Ví dụ đường dẫn Windows kiểm tra cách escape đúng qua **hai tầng**. Hai dòng cuối cùng cố tình dùng `\\$` trong mẫu để tìm dấu đô la thật, rồi thực hiện **hai chính sách replacement khác nhau**.

## 10. Thiết kế test regex có ý nghĩa

Trước khi chấp nhận một regex, nên phân loại test theo các câu hỏi:

| Loại test | Input mẫu | Điều cần xác minh |
| --- | --- | --- |
| **Đúng định dạng** | `report.txt`, `api/v2/users` | Mẫu nhận văn bản hợp lệ |
| **Ký tự đặc biệt khác nghĩa** | `reportXtxt`, `api\v2\users` | Dấu chấm/slash không bị đọc sai |
| **Đô la thật so với neo** | `total=$25`, `total=25` | `\$` nhận tiền tệ, `$` giới hạn vị trí |
| **Tiền/hậu tố** | `xAB12`, `AB12x` | Kiểm tra toàn chuỗi hay đoạn con |
| **Xuống dòng** | `a\nb`, `cat\nbird` | Hiệu ứng DOTALL/MULTILINE đúng yêu cầu |
| **Nhóm bắt** | `notes.txt`, `notes.exe` | `matches()` và `group(...)` trả đúng nội dung |
| **Nhóm tùy chọn** | Có và không có phần tùy chọn | Xử lý `null`, không dùng group sai |
| **Replacement không tin cậy** | Chuỗi chứa `$` và `\` | Không thay thế nhóm ngoài ý định hoặc phát sinh lỗi |
| **Unicode, input rỗng/dài** | Tên có dấu, chuỗi rỗng, chuỗi rất dài | Nhận đúng theo đặc tả, không giả định ASCII hoặc chạy quá chậm |

Một vài quy tắc thực tế:

1. **Viết rõ mong đợi:** test tên `shouldRejectWrongExtensionSeparator` tốt hơn test mơ hồ `testRegex`.
2. **Dùng `matches()` cho định dạng toàn chuỗi, `find()` cho trích xuất**; nếu kiểm thử cả hai, đặt tên kết quả rõ ràng.
3. **Tránh khẳng định chuỗi “khớp” chỉ vì `find()` trả true** nếu nghiệp vụ đòi mọi ký tự hợp lệ.
4. **Kiểm tra cặp dấu đóng/mở của nhóm** và thứ tự nhóm; khi thay đổi `(...)` thành `(?:...)`, số nhóm có thể đổi.
5. **Kiểm tra cả regex lẫn replacement**: khớp đúng vẫn có thể tạo ra đầu ra sai nếu `$1`, `${name}` hoặc backslash trong replacement bị hiểu ngoài dự kiến.
6. **Không nối trực tiếp input chưa tin cậy vào regex** nếu chỉ muốn tìm nguyên văn. Dùng `Pattern.quote`, kiểm soát giới hạn độ dài/độ phức tạp và chú ý khả năng backtracking rất chậm của mẫu không chặt chẽ.
7. **Dùng dữ liệu thực và test hồi quy** sau khi thay đổi mẫu: regex ngắn không có nghĩa là dễ đoán trong mọi trường hợp.

### Minh họa kiểm thử bằng JUnit 5

Nếu dự án đã có JUnit 5, các assertion có thể được viết gọn như sau. **Đây là phương thức test nằm trong lớp test của dự án**, không phải chương trình Java độc lập; cần dependency JUnit 5 và các import phù hợp.

~~~java
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.regex.Pattern;

class RegexSymbolsTest {
    @Test
    void literalDotMustNotAcceptOtherSeparators() {
        String regex = "file\\.txt";
        assertTrue(Pattern.matches(regex, "file.txt"));
        assertFalse(Pattern.matches(regex, "fileXtxt"));
    }

    @Test
    void slashAndDollarAreDifferentFromRegexAnchors() {
        assertTrue(Pattern.matches("api/v\\d+", "api/v2"));
        assertTrue(Pattern.matches("price=\\$\\d+", "price=$12"));
        assertFalse(Pattern.matches("price=\\$\\d+", "price=12"));
    }

    @Test
    void namedGroupCapturesCorrectExtension() {
        var matcher = Pattern.compile(
                "^(?<stem>[a-z]+)\\.(?<ext>txt|csv)$")
                .matcher("notes.csv");
        assertTrue(matcher.matches());
        assertEquals("notes", matcher.group("stem"));
        assertEquals("csv", matcher.group("ext"));
    }
}
~~~

Test đọc dễ hơn khi **mỗi assertion kiểm tra đúng một đặc điểm**. Tránh test chỉ chứa những ví dụ hợp lệ vì dấu `.` wildcard rất dễ “lọt” những chuỗi gần đúng.

## 11. Lỗi phổ biến cần tránh

- **Viết `"\\. "` thay vì `"\\."`** (thừa dấu cách) hoặc `"."` (chấm wildcard) trong lúc muốn tìm dấu chấm thật. Chuỗi Java phải tạo ra đúng regex dự kiến.
- **Nhầm cách viết `/regex/` trong JavaScript với Java `Pattern`:** ở Java, hai slash đó được tính vào nội dung mẫu; không phải delimiter.
- **Nhầm dấu gạch xuôi và gạch ngược:** `/` thường là literal; `\` bắt đầu escape trong regex, và phải được escape tiếp trong Java string.
- **Dùng `$` để biểu diễn đô la tiền tệ:** trong pattern `$` là anchor, muốn dấu thật dùng `\$`; trong Java literal là `"\\$"`.
- **Dùng `"$1"` trong pattern với kỳ vọng tham chiếu nhóm:** `$1` là cú pháp của **replacement**, không phải backreference regex. Trong pattern, tham chiếu nhóm theo số dùng `\1` (Java `"\\1"`).
- **Dùng dữ liệu replacement có `$`/`\` trực tiếp:** `replaceAll` xử lý cú pháp nhóm/escape; cần `Matcher.quoteReplacement` cho literal.
- **Lấy `group(1)` trước khi có match:** dẫn tới `IllegalStateException`; kiểm tra thành công của `matches()` hoặc `find()` trước.
- **Vô tình thêm nhóm bắt:** số nhóm thay đổi làm `$1`/`group(1)` mang ý nghĩa khác. Dùng `(?:...)` nếu chỉ gom.
- **Quên thử xuống dòng:** DOTALL và MULTILINE là hai tùy chọn khác nhau; đừng suy ra từ một đầu vào chỉ có một dòng.
- **Đoán đúng đắn từ một kết quả true:** test phải có cả chuỗi sai sát biên, chuỗi có dấu đặc biệt, dòng mới, nhóm tùy chọn và dữ liệu replacement.
- **Phân tích cấu trúc phức tạp chỉ bằng regex:** đường dẫn/URL/JSON/HTML có quy tắc cú pháp riêng; khi bài toán cần hiểu cấu trúc hoàn chỉnh, chọn parser hoặc API chuyên dụng.

## 12. Bài luyện tập

**Bài 1.** Tại sao regex `a.b` nhận `a/b` khi dùng `matches()`? Viết Java regex chỉ nhận đúng `a.b`.

**Đáp án:** `.` là wildcard, nhận cả slash. Cần `Pattern.matches("a\\.b", "a.b")`.

**Bài 2.** Viết regex Java kiểm tra toàn chuỗi dạng `/user/123` (chính xác hai slash xuôi, chữ `user`, và ba chữ số).

**Đáp án:** `Pattern.matches("/user/\\d{3}", "/user/123")`. Slash xuôi không phải ký hiệu escape đặc biệt.

**Bài 3.** Với regex Java `"^\\$\\d+$"`, `"$15"` có khớp không? `"15$"` có khớp không?

**Đáp án:** `"$15"` khớp, `"15$"` không khớp. Regex yêu cầu dấu đô la **đầu** chuỗi, rồi một hoặc nhiều chữ số đến hết.

**Bài 4.** Với mẫu `(?<first>[a-z]+)-(?<second>\d+)` trong regex (Java literal cần `\\d`), muốn output `"42:abc"` từ `"abc-42"` thì nên dùng replacement nào?

**Đáp án:** `"${second}:${first}"`. Tên nhóm trong replacement nằm sau dấu `$`, được bao bằng cặp `{...}`.

**Bài 5.** Chuỗi replacement đến từ người dùng là `"$2\folder"`. Vì sao không truyền thẳng vào `Matcher.replaceAll(...)`?

**Đáp án:** `$2` có thể bị hiểu là tham chiếu nhóm 2 và `\` có ý nghĩa escape trong replacement. Dùng `Matcher.quoteReplacement(userReplacement)` để chèn nguyên văn.

**Bài 6.** Vì sao mẫu `"report\\.txt"` qua `find()` vẫn có thể trả true đối với `"backup_report.txt.old"`?

**Đáp án:** `find()` tìm được một **đoạn con** `report.txt` trong chuỗi dài hơn. Nếu kiểm tra **đúng toàn bộ tên file**, dùng `matches()` hoặc ràng buộc phù hợp trong mẫu.

## Ghi nhớ

- **`.` trong regex**: wildcard; **`\.`**: dấu chấm thật; Java literal của mẫu sau là `"\\."`.
- **`/` trong Java regex**: ký tự thường, không phải dấu bao mẫu. **`\`**: ký tự escape trong cả regex và Java source.
- **`$` trong regex**: neo cuối; **`\$`**: dấu đô la thật. **`$1` hoặc `${name}` trong replacement**: nội dung nhóm bắt.
- **`(...)`**: nhóm bắt; **`(?:...)`**: nhóm không bắt; **`(?<name>...)`**: nhóm có tên.
- **Kiểm thử thực tế**: kết hợp đầu vào đúng/sai, `matches()`/`find()`, xuống dòng, replacement đặc biệt và những chuỗi chứa dấu câu.

Có thể đối chiếu chi tiết tại [Java 17 Pattern API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html) và [Java 17 Matcher API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Matcher.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-testing-007">Kiểm thử split giữ phần rỗng và splitWithDelimiters</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử split giữ phần rỗng và splitWithDelimiters trong Java

## 1. Bài toán và mục tiêu kiểm thử

Trong Java, `String.split(regex)` và `String.split(regex, limit)` dùng **biểu thức chính quy** để phân tách chuỗi. Kết quả là `String[]` gồm các phần văn bản nằm giữa những lần regex khớp. Một nguồn lỗi phổ biến là tưởng rằng kết quả luôn giữ mọi trường rỗng hoặc mọi dấu phân cách.

Ví dụ dữ liệu giống CSV đơn giản:

```text
name,age,city,
```

Dấu phẩy cuối có thể biểu thị một trường cuối **rỗng**. Nếu viết:

```java
String[] fields = "name,age,city,".split(",");
```

thì mặc định `split` **bỏ phần tử rỗng ở cuối**, khiến kết quả chỉ còn `["name", "age", "city"]`. Nếu cần xác nhận chính xác số trường, phải chọn `limit` phù hợp.

Từ **Java 21**, Java còn cung cấp `String.splitWithDelimiters(regex, limit)` và `Pattern.splitWithDelimiters(input, limit)` để giữ **cả chuỗi khớp với delimiter** xen giữa các trường. Đây là một API khác với `split`: có ích khi cần phân tích, kiểm thử hoặc tái dựng dữ liệu có dấu phân cách. **Java 17 không có `splitWithDelimiters`**; các ví dụ sử dụng API này yêu cầu JDK 21 trở lên.

Sau bài này, bạn sẽ biết kiểm thử: các phần rỗng **ở đầu, giữa và cuối**; các giá trị `limit` bằng 0, âm và dương; delimiter lặp; ký tự đặc biệt của regex; delimiter khớp độ dài 0; và sự khác nhau giữa `split` với `splitWithDelimiters`.

## 2. Cơ chế split(regex, limit)

Hai overload của `String`:

```java
String[] split(String regex)
String[] split(String regex, int limit)
```

`split(regex)` tương đương `split(regex, 0)`. Giá trị `limit` quyết định **số lần áp dụng delimiter và việc giữ các chuỗi rỗng cuối mảng**.

| `limit` | Số lần phân tách | Phần tử rỗng ở cuối |
| --- | --- | --- |
| `0` (mặc định) | Áp dụng regex nhiều lần nhất có thể | **Bỏ** những phần tử `""` liên tiếp ở cuối |
| Số âm, ví dụ `-1` | Áp dụng regex nhiều lần nhất có thể | **Giữ** các phần tử `""` ở cuối |
| Số dương `n` | Áp dụng regex tối đa `n - 1` lần, tối đa `n` phần tử | Giữ phần cuối còn lại, kể cả `""` khi có |

Đối số `limit` **không** phải số ký tự và **không** phải số dấu phân cách xuất hiện trong chuỗi. `limit = 2` nghĩa là chỉ chia tại tối đa **một** lần khớp delimiter; phần còn lại trở thành phần tử cuối.

```java
String text = "a,b,,";

System.out.println(java.util.Arrays.toString(text.split(",")));      // [a, b]
System.out.println(java.util.Arrays.toString(text.split(",", -1)));  // [a, b, , ]
System.out.println(java.util.Arrays.toString(text.split(",", 2)));   // [a, b,,]
```

`Arrays.toString` không đặt dấu nháy quanh từng giá trị, vì vậy các chuỗi rỗng nhìn dễ nhầm. Khi viết test, hãy dùng `Arrays.equals` với mảng kỳ vọng hoặc hàm hiển thị có dấu nháy như ở phần chương trình đầy đủ.

### Phân biệt ba loại trường rỗng

- **Ở đầu:** `",a"` có một delimiter khớp độ dài dương ngay tại vị trí 0 → trường rỗng `""` ở đầu **được giữ**, kể cả `limit = 0`.
- **Ở giữa:** `"a,,b"` có hai dấu phẩy kề nhau → trường rỗng `""` nằm giữa hai delimiter **được giữ**.
- **Ở cuối:** `"a,b,"` có dấu phẩy cuối → `limit = 0` bỏ phần tử rỗng cuối; `limit = -1` giữ.

Bảng ví dụ cụ thể:

| Dữ liệu | Lệnh | Kết quả chính xác |
| --- | --- | --- |
| `"a,b,,"` | `split(",")` | `["a", "b"]` |
| `"a,b,,"` | `split(",", 0)` | `["a", "b"]` |
| `"a,b,,"` | `split(",", -1)` | `["a", "b", "", ""]` |
| `"a,b,,"` | `split(",", 2)` | `["a", "b,,"]` |
| `",a,"` | `split(",")` | `["", "a"]` |
| `",a,"` | `split(",", -1)` | `["", "a", ""]` |
| `"a,,b,"` | `split(",", -1)` | `["a", "", "b", ""]` |
| `""` | `split(",", -1)` | `[""]` |

**Cẩn thận với chuỗi rỗng:** Khi input hoàn toàn rỗng và regex như `","` không khớp, kết quả chứa **một phần tử chuỗi rỗng** `[""]` chứ không phải mảng độ dài 0. Vì thế nên khẳng định cả độ dài và nội dung phần tử trong những test này.

## 3. splitWithDelimiters hoạt động thế nào?

Từ Java 21:

```java
String[] splitWithDelimiters(String regex, int limit)
```

Phương thức này cũng dùng regex làm dấu phân cách, nhưng thay vì bỏ delimiter như `split`, nó trả cả **các đoạn văn bản và các delimiter khớp regex**, đúng thứ tự xuất hiện.

Ví dụ:

```java
String input = "boo:::and::foo";
String[] parts = input.splitWithDelimiters(":+", -1);

// ["boo", ":::", "and", "::", "foo"]
```

`":+"` là regex khớp **một hoặc nhiều** dấu hai chấm liền kề, vì vậy `":::"` và `"::"` được giữ như hai delimiter nguyên vẹn. Chúng không bị phân tách thành từng dấu `":"`.

Kết quả thường có dạng:

```text
[phần văn bản, delimiter, phần văn bản, delimiter, phần văn bản]
```

Dấu phân cách cuối được giữ nếu có, và `limit = -1` giữ cả phần văn bản rỗng ở cuối. Ví dụ:

```java
String[] parts = "a,,b,".splitWithDelimiters(",", -1);

// ["a", ",", "", ",", "b", ",", ""]
```

Kết quả phản ánh chính xác cả dấu phẩy liên tiếp và dấu phẩy cuối.

### Bảng so sánh split và splitWithDelimiters

Với chuỗi `"a,,b,"` và regex `","`:

| Phương thức | `limit` | Kết quả |
| --- | --- | --- |
| `split` | `0` | `["a", "", "b"]` |
| `split` | `-1` | `["a", "", "b", ""]` |
| `splitWithDelimiters` | `0` | `["a", ",", "", ",", "b", ","]` |
| `splitWithDelimiters` | `-1` | `["a", ",", "", ",", "b", ",", ""]` |
| `splitWithDelimiters` | `2` | `["a", ",", ",b,"]` |
| `splitWithDelimiters` | `1` | `["a,,b,"]` |

Với `limit = 0`, `splitWithDelimiters` **loại các chuỗi rỗng ở cuối mảng**, dù đó là trường hay delimiter rỗng. Các dấu phân cách có nội dung (ví dụ `","`) vẫn xuất hiện trong mảng.

### limit dương của splitWithDelimiters

Giả sử `limit = n > 0`: regex được dùng để chia tối đa `n - 1` lần, vì vậy kết quả có **không quá `2n - 1` phần tử**. Phần cuối được giữ nguyên sau delimiter cuối đã dùng để chia.

```java
System.out.println(
    java.util.Arrays.toString("boo:::and::foo".splitWithDelimiters(":+", 2))
);
// [boo, :::, and::foo]
```

Ở ví dụ này, chỉ delimiter `":::"` đầu tiên được sử dụng; `"and::foo"` là phần chưa chia. Không suy ra rằng `limit = 2` sẽ giữ đúng 2 phần tử: mảng có thể dài 3 vì nó chứa cả delimiter.

Có một API tương đương trên `java.util.regex.Pattern`:

```java
java.util.regex.Pattern pattern =
        java.util.regex.Pattern.compile(":+");

String[] parts = pattern.splitWithDelimiters("boo:::and::foo", -1);
```

Cách dùng `Pattern` phù hợp khi cùng một regex được sử dụng nhiều lần hoặc cần các flags như `CASE_INSENSITIVE`. Cả `Pattern.splitWithDelimiters` và `String.splitWithDelimiters` đều được giới thiệu từ Java 21.

## 4. Trường hợp delimiter ở đầu, ở cuối và liên tiếp

Delimiter **khớp độ dài dương ở đầu input** tạo phần tử `""` đầu tiên:

```java
String[] result = ",a,".splitWithDelimiters(",", -1);

// ["", ",", "a", ",", ""]
```

Với `split(",", -1)`, kết quả tương ứng chỉ còn `["", "a", ""]` vì các delimiter không được đưa vào mảng.

Nếu có nhiều delimiter liền nhau, vẫn phải xem regex khớp như thế nào:

```java
String a = "a:::b";

System.out.println(java.util.Arrays.toString(
        a.splitWithDelimiters(":", -1)));
// [a, :, , :, , :, b]

System.out.println(java.util.Arrays.toString(
        a.splitWithDelimiters(":+", -1)));
// [a, :::, b]
```

Với regex `":"`, có các đoạn rỗng giữa những delimiter kề nhau. Với regex `":+"`, cả chuỗi `":::"` là một delimiter duy nhất và **không còn các đoạn rỗng ở giữa các lần khớp** đó.

Nếu yêu cầu nghiệp vụ là phân tách chính xác mỗi dấu phẩy, đừng vô tình dùng regex `",+"` vì nó gộp các dấu phẩy kề nhau và sẽ che mất các cột rỗng. Hãy khẳng định trong test rằng số lượng trường và vị trí trường rỗng đúng như định dạng quy định.

## 5. Regex có ý nghĩa đặc biệt và zero-width matches

`split` và `splitWithDelimiters` đều nhận **regex**, không phải ký tự phân cách literal. Dấu chấm `.` trong regex nghĩa là “một ký tự bất kỳ” (theo quy tắc của regex), không phải dấu chấm thật.

Ví dụ muốn chia theo dấu chấm literal:

```java
String input = "a.b.";
String[] items = input.split("\\.", -1);

// ["a", "b", ""]
```

`"\\."` là cách escape regex `\.` trong **Java string literal**. Nếu dấu phân cách đến từ người dùng và cần hiểu hoàn toàn như văn bản literal, có thể dùng:

```java
String delimiter = ".";
String regex = java.util.regex.Pattern.quote(delimiter);
String[] items = "a.b.".split(regex, -1);
```

Khi regex là `"\\s+"`, mỗi chuỗi gồm một hay nhiều ký tự whitespace được xem là **một delimiter**. Với `splitWithDelimiters`, toàn bộ chuỗi whitespace đã khớp sẽ nằm trong phần tử delimiter; điều này hữu ích khi cần bảo toàn khoảng trắng để phân tích lại dữ liệu gốc.

### Regex khớp độ dài bằng 0

Các regex như lookahead `(?=b)` hay word boundary `\\b` có thể khớp tại một vị trí **không tiêu thụ ký tự**. Quy tắc Java có một ngoại lệ quan trọng:

- Khi delimiter có **độ dài dương** và khớp ở đầu input, Java thêm trường rỗng `""` ở đầu.
- Khi delimiter khớp **độ dài 0 ở đầu input**, Java **không thêm** trường rỗng đầu, và `splitWithDelimiters` cũng không thêm delimiter rỗng đầu tương ứng.

Ví dụ trên Java 21+:

```java
String[] positive = "abc".splitWithDelimiters("a", -1);
// ["", "a", "bc"]

String[] zeroWidthAtStart =
        "abc".splitWithDelimiters("(?=a)", -1);
// ["abc"]
```

`(?=a)` là positive lookahead khớp vị trí trước chữ `a` mà không tiêu thụ chữ `a`. Do đó nó không tạo delimiter có nội dung ở vị trí 0.

Ở cuối chuỗi, regex `$` cũng có thể tạo match độ dài 0:

```java
String[] keep = "abc".splitWithDelimiters("$", -1);
// ["abc", "", ""]

String[] dropTrailing = "abc".splitWithDelimiters("$", 0);
// ["abc"]
```

Hai phần tử cuối của `keep` lần lượt là delimiter rỗng và phần văn bản rỗng cuối. Với `limit = 0`, các phần tử rỗng cuối bị loại. Regex khớp độ dài 0 khá tinh tế: hãy kiểm thử với đầu/cuối chuỗi và input rỗng, tránh giả định rằng mọi match đều lấy mất một ký tự.

## 6. Chương trình Java 21+ kiểm thử chính xác phần tử rỗng

Để kết quả dễ kiểm chứng, ví dụ sau in **dấu nháy kép** quanh từng phần tử, thay vì dùng `Arrays.toString` vốn khiến chuỗi rỗng khó phân biệt.

Lưu thành tệp `SplitEmptyAndDelimitersDemo.java` bằng UTF-8:

```java
import java.util.StringJoiner;
import java.util.regex.Pattern;

public class SplitEmptyAndDelimitersDemo {
    private static String show(String[] items) {
        StringJoiner output = new StringJoiner(", ", "[", "]");
        for (String item : items) {
            String visible = item.replace("\\", "\\\\")
                                 .replace("\n", "\\n");
            output.add("\"" + visible + "\"");
        }
        return output.toString();
    }

    private static void print(String label, String[] items) {
        System.out.println(label + " = " + show(items));
    }

    public static void main(String[] args) {
        String csv = "a,b,,";
        print("split default", csv.split(","));
        print("split zero", csv.split(",", 0));
        print("split minus", csv.split(",", -1));
        print("split two", csv.split(",", 2));

        print("initial default", ",a,".split(","));
        print("initial minus", ",a,".split(",", -1));
        print("middle empty", "a,,b,".split(",", -1));
        print("empty input", "".split(",", -1));
        print("literal dot", "a.b.".split("\\.", -1));

        String repeated = "a,,b,";
        print("with -1", repeated.splitWithDelimiters(",", -1));
        print("with 0", repeated.splitWithDelimiters(",", 0));
        print("with 2", repeated.splitWithDelimiters(",", 2));
        print("with 1", repeated.splitWithDelimiters(",", 1));

        print("positive start",
                "abc".splitWithDelimiters("a", -1));
        print("zero width start",
                "abc".splitWithDelimiters("(?=a)", -1));

        Pattern runsOfColon = Pattern.compile(":+");
        print("colon all",
                runsOfColon.splitWithDelimiters("boo:::and::foo", -1));
        print("colon limit 2",
                runsOfColon.splitWithDelimiters("boo:::and::foo", 2));
    }
}
```

Biên dịch và chạy bằng **JDK 21 trở lên**:

```bash
javac -encoding UTF-8 SplitEmptyAndDelimitersDemo.java
java SplitEmptyAndDelimitersDemo
```

Kết quả chuẩn, trong đó `""` biểu thị một phần tử chuỗi rỗng:

```text
split default = ["a", "b"]
split zero = ["a", "b"]
split minus = ["a", "b", "", ""]
split two = ["a", "b,,"]
initial default = ["", "a"]
initial minus = ["", "a", ""]
middle empty = ["a", "", "b", ""]
empty input = [""]
literal dot = ["a", "b", ""]
with -1 = ["a", ",", "", ",", "b", ",", ""]
with 0 = ["a", ",", "", ",", "b", ","]
with 2 = ["a", ",", ",b,"]
with 1 = ["a,,b,"]
positive start = ["", "a", "bc"]
zero width start = ["abc"]
colon all = ["boo", ":::", "and", "::", "foo"]
colon limit 2 = ["boo", ":::", "and::foo"]
```

Chương trình xác nhận đồng thời bốn điểm: `split(regex)` bằng `split(regex, 0)`; limit âm giữ phần rỗng cuối; `splitWithDelimiters` giữ cả các delimiter đã khớp; và các match zero-width ở đầu không tạo phần tử rỗng mở đầu.

## 7. Viết assertion thay vì chỉ nhìn output

Khi kiểm thử tự động, nên so sánh **mảng theo giá trị và đúng thứ tự**, gồm cả chuỗi rỗng. Nếu dùng JUnit 5, có thể sử dụng `assertArrayEquals(expected, actual)`. Với Java thuần, `Arrays.equals` cũng đủ để minh họa:

```java
import java.util.Arrays;

public class SplitAssertions {
    private static void check(String name, String[] expected, String[] actual) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(name + ": expected "
                    + Arrays.toString(expected)
                    + " but got " + Arrays.toString(actual));
        }
        System.out.println(name + " = PASS");
    }

    public static void main(String[] args) {
        check("trailing fields",
                new String[] {"a", "b", "", ""},
                "a,b,,".split(",", -1));

        check("default removes trailing",
                new String[] {"a", "b"},
                "a,b,,".split(","));

        check("middle empty",
                new String[] {"a", "", "b"},
                "a,,b,".split(","));

        check("retain delimiters",
                new String[] {"a", ",", "", ",", "b", ",", ""},
                "a,,b,".splitWithDelimiters(",", -1));

        check("limit two",
                new String[] {"a", ",", ",b,"},
                "a,,b,".splitWithDelimiters(",", 2));

        check("empty source",
                new String[] {""},
                "".split(",", -1));
    }
}
```

Chạy bằng JDK 21+:

```bash
javac -encoding UTF-8 SplitAssertions.java
java SplitAssertions
```

Output:

```text
trailing fields = PASS
default removes trailing = PASS
middle empty = PASS
retain delimiters = PASS
limit two = PASS
empty source = PASS
```

Đây là một chương trình kiểm thử độc lập, không cần cài JUnit. Điểm cần giữ khi chuyển sang JUnit là **assert cả toàn bộ mảng**, không chỉ kiểm tra `array.length`. Hai mảng có cùng số phần tử nhưng vị trí phần tử rỗng khác nhau vẫn là dữ liệu khác nhau.

Nếu ứng dụng phải hỗ trợ Java 17, có thể giữ các assertion về `split` trên Java 17; nhưng phải bỏ hoặc thay cách kiểm thử `splitWithDelimiters` vì API này chưa tồn tại ở bản JDK đó.

## 8. Bộ test biên nên có trong dự án

Đối với code dùng `split` để parse một định dạng đơn giản, nên có các nhóm kiểm thử song song:

| Trường hợp | Input và regex điển hình | Điều cần assert |
| --- | --- | --- |
| Không có delimiter | `"abc"`, `","` | Chỉ một phần tử `"abc"` |
| Input rỗng | `""`, `","` | `[""]` nếu regex không khớp |
| Delimiter đầu | `",abc"`, `","` | Phần rỗng đầu được giữ nếu match có độ dài dương |
| Delimiter cuối | `"abc,"`, `","` | So sánh khác biệt `limit = 0` và `-1` |
| Hai delimiter liên tiếp | `"a,,b"`, `","` | Có trường rỗng giữa |
| Nhiều delimiter cuối | `"a,b,,"`, `","` | `limit = -1` giữ đủ các trường rỗng cuối |
| Limit dương | `"a,b,c"`, `","`, `2` | Phần cuối `"b,c"` không bị chia tiếp |
| Regex có metacharacter | `"a.b"`, dấu chấm literal | Dùng `"\\."` hoặc `Pattern.quote(".")` |
| Regex gộp delimiter | `"a:::b"`, `":+"` | Delimiter `":::"` giữ nguyên thành một phần tử khi dùng `splitWithDelimiters` |
| Lookahead đầu | `"abc"`, `"(?=a)"` | Không tạo phần rỗng đầu |
| Regex cuối chuỗi | `"abc"`, `"$"` | Kiểm tra chuỗi rỗng cuối theo `limit` |
| Unicode | Chuỗi chứa ký tự đa đơn vị UTF-16 | Regex và kỳ vọng không vô tình tách surrogate pair |
| Delimiter không hợp lệ | `"a[b"` với regex `"["` | Xác minh `PatternSyntaxException` được xử lý tại ranh giới nhập liệu |

Khi kiểm thử dữ liệu có tính nghiệp vụ, hãy xác định rõ liệu “một dấu phân cách cuối” biểu thị **một trường rỗng hợp lệ**, một dòng không hợp lệ hay chỉ là dấu kết thúc bản ghi. Java `split` không thể tự quyết định chính sách đó.

**Đặc biệt với CSV thật:** `split(",")` hoặc `split(",", -1)` **không phải CSV parser hoàn chỉnh**. CSV có thể cho phép dấu phẩy bên trong trường được đặt dấu nháy, dấu nháy escape và newline trong trường. Khi dữ liệu có các quy tắc đó, hãy dùng parser CSV tương thích định dạng, sau đó kiểm thử các trường rỗng theo hợp đồng parser. `split` chỉ phù hợp khi delimiter là đơn giản và không cần ngữ cảnh trích dẫn.

## 9. Tái dựng chuỗi và kiểm tra delimiter

Với `splitWithDelimiters(regex, -1)`, kết quả giữ mọi chuỗi văn bản và delimiter theo thứ tự, nên với những regex và input phù hợp, ghép chúng lại có thể tái dựng nguyên văn input:

```java
String original = "left==right;next";
String[] tokens = original.splitWithDelimiters("==|;", -1);

String restored = String.join("", tokens);

System.out.println(original.equals(restored)); // true
```

Kiểu kiểm tra này hữu ích khi viết tokenizer nhẹ có yêu cầu không mất thông tin. Nó kiểm tra **khả năng bảo toàn văn bản**, nhưng chưa chứng minh việc phân loại token phù hợp ngữ nghĩa nghiệp vụ: vẫn cần assert các phần tử tương ứng và delimiter cụ thể.

Một test tốt có thể kiểm tra cả hai điều:

```java
String original = "a,,b,";
String[] tokens = original.splitWithDelimiters(",", -1);

boolean sameContent = String.join("", tokens).equals(original);
boolean sameStructure = java.util.Arrays.equals(tokens,
        new String[] {"a", ",", "", ",", "b", ",", ""});

System.out.println(sameContent);   // true
System.out.println(sameStructure); // true
```

Nếu cần tách **token có loại** (text, delimiter, vị trí bắt đầu/kết thúc, whitespace), `splitWithDelimiters` chỉ cung cấp chuỗi kết quả, không cung cấp offset; khi cần vị trí match, dùng `Matcher.find()` và `start()/end()` để thu thập vị trí một cách tường minh.

## 10. Lỗi phổ biến

1. **Dùng `split(",")` để đếm số cột có thể rỗng ở cuối.** Overload mặc định loại bỏ rỗng cuối. Dùng `split(",", -1)` khi định dạng coi chúng là cột.
2. **Tin vào `Arrays.toString` để đếm dấu rỗng bằng mắt.** Hãy in có dấu nháy hoặc dùng `assertArrayEquals`.
3. **Dùng `limit = 1` rồi mong có hai phần.** Limit dương `1` cho tối đa một phần, thường là nguyên input.
4. **Nghĩ `limit` của `splitWithDelimiters` cũng là số phần tử tối đa.** Với limit dương `n`, mảng có thể dài tới `2n - 1` do giữ delimiter.
5. **Nhầm regex với literal.** Dấu `.` hoặc `|` có ý nghĩa regex; dùng escape đúng hoặc `Pattern.quote`.
6. **Nhầm delimiter lặp thành nhiều trường rỗng dù regex là `,+`.** Regex có thể gộp cả đoạn delimiter thành một match.
7. **Nhầm `splitWithDelimiters` với capturing group của `split`.** Java `split` không tự đưa nhóm capture vào kết quả; muốn giữ delimiter dùng API mới từ Java 21 hoặc tự quét `Matcher`.
8. **Quên match zero-width ở đầu.** Quy tắc này không sinh phần rỗng dẫn đầu như match có độ dài dương.
9. **Bỏ qua `PatternSyntaxException`.** Nếu regex được nhập động, cần có chính sách validate/parse rõ ràng.
10. **Viết `splitWithDelimiters` rồi chạy JDK 17.** Phương thức không tồn tại ở Java 17; cần Java 21+ hoặc giải pháp tương thích phiên bản cũ.
11. **Dùng split cho CSV/định dạng có quote phức tạp.** Những trường chứa delimiter hoặc xuống dòng được quote phải được parser tương thích xử lý.

## 11. Câu hỏi và bài tập tự luyện

**Bài 1.** Với `"x,y,,"`, hai lệnh dưới đây khác nhau thế nào?

```java
"x,y,,".split(",");
"x,y,,".split(",", -1);
```

**Đáp án:** Lệnh đầu trả `["x", "y"]`; lệnh sau trả `["x", "y", "", ""]`. Hai phần cuối biểu thị hai trường rỗng do hai dấu phẩy cuối.

**Bài 2.** Với `"a,,b,".splitWithDelimiters(",", 2)`, có bao nhiêu phần tử?

**Đáp án:** Ba phần tử: `["a", ",", ",b,"]`. `limit = 2` chỉ cho áp dụng delimiter tối đa một lần rồi giữ nguyên phần còn lại.

**Bài 3.** `"a::b".splitWithDelimiters(":+", -1)` cho gì?

**Đáp án:** `["a", "::", "b"]` vì regex `:+` khớp hai dấu hai chấm như **một** delimiter.

**Bài 4.** Vì sao `"abc".splitWithDelimiters("(?=a)", -1)` không bắt đầu bằng `""`?

**Đáp án:** `(?=a)` là match độ dài 0 ở vị trí đầu; API không thêm phần rỗng đầu hay delimiter rỗng đầu cho trường hợp đó.

**Bài 5.** Java 17 có gọi trực tiếp `String.splitWithDelimiters` được không?

**Đáp án:** Không. Phương thức được giới thiệu từ Java 21. Trên Java 17, dùng `split` khi không cần delimiter hoặc tự quét bằng `Pattern`/`Matcher` nếu cần vừa giữ văn bản vừa giữ delimiter.

## Tài liệu tham khảo

- [Java SE 21 — String](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html): hai overload `split` và `splitWithDelimiters`, định nghĩa `limit`, quy tắc match zero-width.
- [Java SE 21 — Pattern](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/regex/Pattern.html): `split(input, limit)`, `splitWithDelimiters(input, limit)` và các ví dụ regex.
- [Java SE 17 — String](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html): hợp đồng `split` trên bản Java trước khi `splitWithDelimiters` xuất hiện.

**Ghi nhớ:** Nếu cần bảo toàn các **trường rỗng cuối**, dùng `split(regex, -1)`. Nếu cần bảo toàn cả **delimiter** và có JDK 21+, dùng `splitWithDelimiters(regex, -1)`; khi kiểm thử phải xác nhận cả nội dung, độ dài và thứ tự từng phần tử của mảng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-testing-008">Kiểm thử định dạng theo locale và chuyển mã hai chiều</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử định dạng theo locale và chuyển mã hai chiều trong Java

Ứng dụng Java thường cần trình bày số, ngày tháng và văn bản theo ngôn ngữ/vùng miền (**Locale**), đồng thời chuyển đổi giữa **String (ký tự Unicode)** và **byte[] (dữ liệu có mã hóa Charset)**. Hai loại chuyển đổi này dễ gây lỗi âm thầm: chuỗi hiển thị đúng ở máy phát triển nhưng sai trên máy khác; dấu thập phân bị đọc nhầm; ký tự tiếng Việt hoặc emoji biến thành dấu hỏi; dữ liệu chỉ được phân tích một phần mà chương trình vẫn coi là hợp lệ.

Bài học tập trung vào **kiểm thử có tính tái lập**, **round-trip (chuyển đổi rồi chuyển ngược)** và cách phân biệt **khôi phục được dữ liệu gốc**, **khôi phục được giá trị ngữ nghĩa**, hoặc **mất thông tin không thể phục hồi**. Các ví dụ dùng Java 17+ và cố ý chọn Locale/Charset cụ thể để không phụ thuộc cài đặt mặc định của máy.

## 1. Hai loại chuyển đổi cần kiểm thử riêng

| Luồng xử lý | API Java thường dùng | Giá trị đầu vào và đầu ra | Điều cần kiểm thử |
| --- | --- | --- | --- |
| Số → văn bản theo vùng miền | NumberFormat, DecimalFormat, String.format(Locale, ...) | Number/BigDecimal → String | Dấu thập phân, phân nhóm, làm tròn, số chữ số, ký hiệu âm |
| Văn bản theo vùng miền → số | NumberFormat.parse, DecimalFormat.parse | String → Number | Đúng Locale, dùng hết đầu vào, độ chính xác, lỗi dữ liệu |
| Ngày → văn bản theo vùng miền | DateTimeFormatter.withLocale(...) | LocalDate/... → String | Thứ tự ngày-tháng, tên tháng, độ dài và vùng miền |
| Văn bản ngày → ngày | LocalDate.parse(..., formatter) | String → LocalDate | Ngày hợp lệ, đúng formatter, không mất thông tin cần thiết |
| Chuỗi Unicode → byte | String.getBytes(Charset), CharsetEncoder | String → byte[] | Charset đích biểu diễn được ký tự, có thay thế ký tự hay không |
| Byte → chuỗi Unicode | new String(bytes, Charset), CharsetDecoder | byte[] → String | Charset giải mã đúng, byte lỗi, không âm thầm làm mất dữ liệu |

**Locale không phải Charset.** Locale điều khiển quy tắc biểu diễn/thông dịch văn bản theo ngôn ngữ và vùng miền (ví dụ 1.234,50 so với 1,234.50). Charset quy định cách biến nội dung ký tự thành byte và ngược lại (ví dụ UTF-8 hoặc ISO-8859-1). Chọn Locale.GERMANY không tự biến dữ liệu thành một bộ mã hóa nào đó; chọn UTF-8 cũng không quyết định cách hiển thị số thập phân.

## 2. Chọn điều kiện kiểm thử có thể lặp lại

Một bài kiểm thử tốt cần tránh phụ thuộc môi trường chạy. Nên làm rõ:

1. **Locale cụ thể:** ví dụ Locale.US, Locale.GERMANY, Locale.FRANCE hoặc Locale.forLanguageTag("vi-VN"), thay vì tùy tiện lấy Locale.getDefault().
2. **Charset cụ thể:** ví dụ StandardCharsets.UTF_8, StandardCharsets.ISO_8859_1, StandardCharsets.US_ASCII; tránh mã hóa mặc định nếu đang kiểm thử giao thức/tệp.
3. **Quy tắc định dạng:** số chữ số thập phân, phân nhóm hàng nghìn, kiểu làm tròn, định dạng ngày và timezone khi có thời điểm tuyệt đối.
4. **Miền dữ liệu:** giá trị 0, số âm, biên lớn, số có phần thập phân, dấu phân nhóm, chuỗi có dấu tiếng Việt, ký tự ngoài BMP (emoji), chuỗi rỗng và dữ liệu sai.
5. **Hợp đồng round-trip:** cần bằng nhau **từng ký tự**, **từng byte** hay chỉ **giá trị số/ngày**?

Các API của Java dùng dữ liệu vùng miền từ JDK (bao gồm nhà cung cấp dữ liệu locale). Chuỗi hiển thị cụ thể, tên tháng hoặc dấu cách phân nhóm có thể thay đổi tùy Locale, cấu hình và phiên bản JDK. Vì vậy, các bài test hợp đồng nên kết hợp: dữ liệu có quy tắc cố định do ứng dụng kiểm soát; và kiểm tra **round-trip** thay vì cố định mọi chuỗi bản địa hóa.

## 3. Kiểm thử định dạng và phân tích số theo Locale

Ở Mỹ, chuỗi biểu diễn số có thể dùng dấu phẩy phân nhóm và dấu chấm thập phân; ở Đức thường đảo ngược hai dấu. Nếu một chuỗi được định dạng theo Locale này rồi phân tích bằng Locale khác, chương trình có thể đọc sai giá trị hoặc chỉ đọc được một phần chuỗi.

Với dữ liệu tiền tệ hoặc độ chính xác thập phân quan trọng, **BigDecimal** thường an toàn hơn double trong các phép tính yêu cầu giá trị thập phân chính xác. DecimalFormat có thể cấu hình để parse thành BigDecimal.

### Ví dụ Java 17: format → parse và kiểm tra toàn bộ đầu vào

Tạo tệp **LocaleNumberRoundTrip.java**:

~~~java
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParsePosition;
import java.util.Locale;

public class LocaleNumberRoundTrip {
    private static void verify(Locale locale, BigDecimal original) {
        DecimalFormat format = new DecimalFormat(
                "#,##0.00",
                DecimalFormatSymbols.getInstance(locale)
        );
        format.setParseBigDecimal(true);

        String rendered = format.format(original);
        ParsePosition position = new ParsePosition(0);
        Number parsed = format.parse(rendered, position);

        boolean consumedAll = parsed != null
                && position.getErrorIndex() < 0
                && position.getIndex() == rendered.length();

        boolean sameValue = parsed instanceof BigDecimal value
                && value.compareTo(original) == 0;

        System.out.println(locale.toLanguageTag() + ": " + rendered);
        System.out.println("full parse: " + consumedAll);
        System.out.println("same value: " + sameValue);
    }

    public static void main(String[] args) {
        BigDecimal number = new BigDecimal("1234.50");
        verify(Locale.US, number);
        verify(Locale.GERMANY, number);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 LocaleNumberRoundTrip.java
java LocaleNumberRoundTrip
~~~

Kết quả với các dấu phân cách của Locale.US và Locale.GERMANY:

~~~text
en-US: 1,234.50
full parse: true
same value: true
de-DE: 1.234,50
full parse: true
same value: true
~~~

**Giải thích:** DecimalFormatSymbols xác định dấu thập phân/phân nhóm cho mỗi Locale; mẫu "#,##0.00" yêu cầu hai chữ số phần lẻ khi format. ParsePosition cho biết parser đã dùng tới vị trí nào. Kiểm tra **position.getIndex() == rendered.length()** ngăn trường hợp chỉ phân tích được một tiền tố.

**BigDecimal.compareTo(...) == 0** xác nhận giá trị toán học tương đương. BigDecimal.equals(...) còn xét cả scale, ví dụ 1.0 và 1.00 có giá trị số bằng nhau nhưng equals có thể khác nhau. Trong hệ thống cần giữ nguyên chính xác scale của đầu vào, format → parse có thể không đáp ứng hợp đồng đó nếu quy tắc định dạng đã làm tròn hoặc thay đổi số chữ số thập phân.

### Tại sao phải kiểm tra đã đọc hết chuỗi?

Một số parser định dạng số có thể chấp nhận tiền tố hợp lệ và dừng trước phần còn lại. Giả sử input là "1,234.50abc": parser có thể đọc được 1,234.50 nhưng không dùng hết các ký tự "abc". Việc parse trả về Number **không chứng minh** cả chuỗi đều hợp lệ.

Đoạn minh họa dưới đây (chưa phải chương trình độc lập) dùng cùng DecimalFormat đã cấu hình:

~~~java
String input = "1,234.50abc";
ParsePosition position = new ParsePosition(0);
Number number = format.parse(input, position);
boolean valid = number != null
        && position.getErrorIndex() < 0
        && position.getIndex() == input.length();
~~~

Kết quả valid là **false** nếu parser chỉ đọc được tiền tố số. Tùy chính sách ứng dụng, có thể cần kiểm tra nghiêm ngặt hơn về dấu phân nhóm, ký hiệu âm, khoảng trắng hoặc chuẩn hóa trước khi chấp nhận dữ liệu nhập. Kiểm tra dùng hết đầu vào **chưa bảo đảm** chuỗi đúng mọi quy tắc trình bày mong muốn, vì một số parser có tính dễ dãi (lenient).

## 4. Không phải mọi phép format → parse đều giữ nguyên giá trị

Nếu DecimalFormat chỉ hiển thị hai chữ số phần lẻ, giá trị có nhiều chữ số hơn có thể bị làm tròn:

~~~java
BigDecimal original = new BigDecimal("1.2345");
DecimalFormat format = new DecimalFormat(
        "0.00",
        DecimalFormatSymbols.getInstance(Locale.US)
);
format.setParseBigDecimal(true);

String text = format.format(original);  // "1.23" theo rounding mặc định ở trường hợp này
Number parsed = format.parse(text);    // 1.23, không phải 1.2345
~~~

Đoạn trên là minh họa cơ chế mất độ chính xác do định dạng. Khi bài toán yêu cầu kết quả làm tròn cụ thể, **đặt rõ RoundingMode** thay vì phụ thuộc giá trị mặc định của formatter.

Vì vậy phải chọn rõ hợp đồng:

- **Round-trip chính xác:** parse(format(x)) bằng x đối với mọi x được chấp nhận; chỉ có thể bảo đảm trong miền đầu vào và định dạng không mất thông tin.
- **Round-trip trong sai số:** kết quả có thể lệch trong ngưỡng nghiệp vụ khi chấp nhận làm tròn; dùng kiểm tra giới hạn thích hợp cho giá trị floating-point.
- **Round-trip chuẩn hóa:** format(parse(text)) có thể biến chuỗi thành cách biểu diễn chuẩn; không đòi hỏi từng ký tự văn bản giống đầu vào ban đầu.
- **Chỉ hiển thị:** nếu định dạng đã cố ý làm tròn/chuyển đơn vị, không nên dùng lại chuỗi hiển thị làm nguồn dữ liệu tính toán chính.

**Tránh biến dữ liệu nghiệp vụ thành chuỗi rồi parse trở lại chỉ để truyền giữa các lớp.** Giữ giá trị số/ngày ở dạng kiểu dữ liệu thích hợp, chỉ format ở ranh giới hiển thị hoặc xuất tệp có hợp đồng cụ thể.

## 5. String.format và DateTimeFormatter cũng cần Locale rõ ràng

String.format(Locale, ...) có thể định dạng số theo Locale. Ví dụ:

~~~java
String us = String.format(Locale.US, "%,.2f", 1234.5);
String de = String.format(Locale.GERMANY, "%,.2f", 1234.5);
~~~

Với hai Locale trên, kết quả tương ứng thông thường là **1,234.50** và **1.234,50**. Nhưng **String.format** tạo văn bản, không cung cấp riêng một parser đảo ngược hoàn toàn của chính cú pháp format; muốn round-trip cần định nghĩa parser thích hợp.

Với ngày, **java.time.format.DateTimeFormatter** cho phép tạo formatter bản địa hóa và dùng cùng formatter để phân tích. Ví dụ đầy đủ, tệp **LocaleDateRoundTrip.java**:

~~~java
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

public class LocaleDateRoundTrip {
    public static void main(String[] args) {
        LocalDate original = LocalDate.of(2026, 10, 23);
        DateTimeFormatter formatter =
                DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
                        .withLocale(Locale.FRANCE);

        String text = formatter.format(original);
        LocalDate parsed = LocalDate.parse(text, formatter);

        System.out.println("date round-trip: " + original.equals(parsed));
    }
}
~~~

~~~bash
javac -encoding UTF-8 LocaleDateRoundTrip.java
java LocaleDateRoundTrip
~~~

~~~text
date round-trip: true
~~~

Không cố định chính xác tên tháng tiếng Pháp trong output mẫu vì dữ liệu locale của môi trường có thể thay đổi cách hiển thị; ví dụ chỉ kiểm tra **giá trị ngày sau khi phân tích lại**. Với thời điểm có múi giờ, cần xác định ZoneId và loại giá trị (Instant, ZonedDateTime, LocalDateTime...), vì một chuỗi chỉ ghi ngày hoặc giờ có thể không mang đủ thông tin để phục hồi một Instant. Khi cần dữ liệu trao đổi độc lập với ngôn ngữ/vùng miền, nên ưu tiên định dạng ISO hoặc định dạng hợp đồng rõ ràng.

## 6. Chuyển mã hai chiều: String ⇄ byte[]

**Charset** mô tả quy tắc ánh xạ từ chuỗi ký tự sang bytes và từ bytes trở lại ký tự. Hai thao tác phổ biến:

~~~java
byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
String decoded = new String(bytes, StandardCharsets.UTF_8);
~~~

Đây là đoạn minh họa; các biến và import cần được khai báo trong chương trình sử dụng. **Phải dùng đúng charset ở cả hai chiều**. Nếu bên gửi mã hóa UTF-8 nhưng bên nhận giải mã ISO-8859-1, những ký tự nhiều byte có thể biến thành chuỗi sai (mojibake).

Với **UTF-8**, một String gồm các Unicode scalar value hợp lệ (không chứa surrogate đơn lẻ chưa ghép cặp) có thể round-trip mà không mất thông tin: **decode(encode(s, UTF-8), UTF-8).equals(s)**. Không nên xem đó là cam kết cho mọi dãy char Java có thể tạo được, vì String có thể chứa surrogate bị lỗi.

Tệp **CharsetRoundTripDemo.java**:

~~~java
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class CharsetRoundTripDemo {
    public static void main(String[] args) {
        String original = "Tiếng Việt 😀";
        byte[] utf8 = original.getBytes(StandardCharsets.UTF_8);
        String decoded = new String(utf8, StandardCharsets.UTF_8);

        System.out.println("UTF-8 round-trip: "
                + original.equals(decoded));
        System.out.println("bytes stable: "
                + Arrays.equals(utf8, decoded.getBytes(StandardCharsets.UTF_8)));

        String word = "café";
        byte[] encoded = word.getBytes(StandardCharsets.UTF_8);
        String wrong = new String(encoded, StandardCharsets.ISO_8859_1);
        System.out.println("wrong charset preserved: "
                + word.equals(wrong));
    }
}
~~~

~~~bash
javac -encoding UTF-8 CharsetRoundTripDemo.java
java CharsetRoundTripDemo
~~~

~~~text
UTF-8 round-trip: true
bytes stable: true
wrong charset preserved: false
~~~

Ở đây chuỗi có dấu và emoji kiểm tra đường đi có các ký tự nhiều byte. Dòng thứ ba cố ý giải mã sai charset và kiểm tra thất bại; đây là kiểm thử âm (negative test). Kiểm tra bytes sau khi giải mã lại cũng hữu ích khi hợp đồng cần **byte-level round-trip**, nhưng nếu chuỗi được chuẩn hóa hoặc định dạng truyền dữ liệu cho phép nhiều cách mã hóa tương đương thì phải định nghĩa lại tiêu chí kiểm thử.

## 7. Chuyển mã có mất thông tin và chế độ lỗi nghiêm ngặt

Một số charset không thể biểu diễn mọi ký tự Unicode. Ví dụ **US_ASCII** chỉ biểu diễn tập ký tự ASCII; nó không mã hóa trực tiếp được toàn bộ chữ tiếng Việt. Các phương thức tiện dụng như String.getBytes(Charset) thường **thay ký tự không biểu diễn được bằng replacement bytes**, khiến việc encode có vẻ thành công nhưng dữ liệu gốc đã mất.

Đừng chỉ gọi encode rồi mặc định thành công nghĩa là an toàn: phải **đối chiếu round-trip** hoặc dùng **CharsetEncoder** với CodingErrorAction.REPORT để yêu cầu báo lỗi.

Tạo tệp **StrictCharsetDemo.java**:

~~~java
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public class StrictCharsetDemo {
    public static void main(String[] args) {
        String original = "Tiếng Việt";

        byte[] lossyBytes = original.getBytes(StandardCharsets.US_ASCII);
        String lossy = new String(lossyBytes, StandardCharsets.US_ASCII);
        System.out.println("ASCII round-trip equal: "
                + original.equals(lossy));

        CharsetEncoder asciiEncoder = StandardCharsets.US_ASCII.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            asciiEncoder.encode(CharBuffer.wrap(original));
            System.out.println("strict ASCII rejected: false");
        } catch (CharacterCodingException ex) {
            System.out.println("strict ASCII rejected: true");
        }

        byte[] malformedUtf8 = {(byte) 0xC3, (byte) 0x28};
        CharsetDecoder utf8Decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            utf8Decoder.decode(ByteBuffer.wrap(malformedUtf8));
            System.out.println("malformed UTF-8 rejected: false");
        } catch (CharacterCodingException ex) {
            System.out.println("malformed UTF-8 rejected: true");
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 StrictCharsetDemo.java
java StrictCharsetDemo
~~~

~~~text
ASCII round-trip equal: false
strict ASCII rejected: true
malformed UTF-8 rejected: true
~~~

**Giải thích:** round-trip ASCII không khôi phục được chuỗi có dấu. Ở chế độ nghiêm ngặt, CharsetEncoder báo lỗi khi gặp ký tự không biểu diễn được; CharsetDecoder báo lỗi với byte UTF-8 sai cú pháp. Nếu ứng dụng cần an toàn dữ liệu, phải xác định cách xử lý khi lỗi phát sinh: báo lỗi cho người dùng, từ chối bản ghi, hoặc thay thế có ghi nhận rõ ràng. Không âm thầm chuyển sang charset khác rồi giả định sẽ luôn sửa được dữ liệu.

**Malformatted và unmappable khác nhau:** malformed biểu thị dãy byte/ký tự đầu vào không hợp lệ theo bộ mã; unmappable là ký tự hợp lệ nhưng charset đích không ánh xạ được. CharsetEncoder và CharsetDecoder có thể được cấu hình riêng để REPORT, REPLACE hoặc IGNORE theo hợp đồng dữ liệu. Khi yêu cầu bảo toàn văn bản, REPLACE/IGNORE làm mất thông tin.

## 8. Kiểm thử hai chiều không có nghĩa mọi dạng văn bản là một

Hai chuỗi có thể trông giống nhau nhưng không có cùng các đơn vị Unicode. Chẳng hạn, một chữ é có thể được biểu diễn bằng một code point **U+00E9**, hoặc bằng chữ e **U+0065** cộng dấu sắc tổ hợp **U+0301**. UTF-8 round-trip sẽ giữ đúng **biểu diễn ban đầu** của mỗi chuỗi hợp lệ; nó **không tự chuẩn hóa Unicode**.

Ví dụ tệp **NormalizationDemo.java**:

~~~java
import java.text.Normalizer;
import java.nio.charset.StandardCharsets;

public class NormalizationDemo {
    public static void main(String[] args) {
        String composed = "\u00E9";
        String decomposed = "e\u0301";

        System.out.println("equal initially: "
                + composed.equals(decomposed));
        System.out.println("equal after NFC: "
                + Normalizer.normalize(composed, Normalizer.Form.NFC)
                        .equals(Normalizer.normalize(
                                decomposed, Normalizer.Form.NFC)));

        String decoded = new String(
                decomposed.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
        System.out.println("UTF-8 keeps representation: "
                + decomposed.equals(decoded));
    }
}
~~~

~~~bash
javac -encoding UTF-8 NormalizationDemo.java
java NormalizationDemo
~~~

~~~text
equal initially: false
equal after NFC: true
UTF-8 keeps representation: true
~~~

Đây là ba phép kiểm tra khác nhau: **bằng nhau theo Java String.equals**, **tương đương sau chuẩn hóa NFC**, và **khôi phục chính xác code unit sau encode/decode UTF-8**. Không nên tùy tiện chuẩn hóa mọi dữ liệu khi chưa có yêu cầu nghiệp vụ: chuẩn hóa có thể thay đổi biểu diễn nguyên bản quan trọng với chữ ký số, so sánh bytes hoặc định dạng giao tiếp.

Nếu kiểm thử tìm kiếm không phân biệt dấu/hoa thường, còn phải xác định quy tắc collation/case folding theo Locale; chỉ thay đổi charset không giải quyết được vấn đề so sánh ngôn ngữ.

## 9. Thiết kế test theo bất biến (property) và trường hợp biên

Thay vì chỉ chụp một chuỗi hiển thị duy nhất, có thể đặt các bất biến rõ ràng:

| Loại kiểm thử | Bất biến đáng kiểm tra | Trường hợp biên |
| --- | --- | --- |
| Format/parse số trong miền an toàn | parse(format(x)) giữ giá trị số và dùng hết chuỗi | Số âm, số 0, rất lớn, phần lẻ, có làm tròn |
| Format/parse ngày với đầy đủ thông tin | parse(format(date)) bằng date | Ngày nhuận, cuối tháng, thay đổi Locale |
| UTF-8 round-trip | decode(encode(s)) bằng s với Unicode hợp lệ | Tiếng Việt, emoji, chuỗi rỗng, ký tự tổ hợp |
| Byte-level round-trip | encode(decode(bytes)) khôi phục bytes hợp lệ khi charset/hợp đồng bảo đảm | Dãy byte biên, BOM, dữ liệu sai |
| Negative test mã hóa | Charset không hỗ trợ phải báo lỗi hoặc round-trip thất bại rõ ràng | US_ASCII với chữ có dấu, surrogate đơn |
| Negative test parse | Chuỗi sai bị từ chối, không chỉ chấp nhận một tiền tố | Văn bản thừa, sai dấu phân cách, số không hợp lệ |
| Normalization | Bằng nhau sau quy tắc chuẩn hóa nếu hợp đồng yêu cầu | Ký tự dựng sẵn và tổ hợp |

Với input ngẫu nhiên hoặc property-based testing, hãy giới hạn miền sinh dữ liệu theo **hợp đồng thực sự**. Ví dụ, không thể yêu cầu mọi BigDecimal đều round-trip chính xác với format chỉ in hai chữ số phần lẻ; không thể yêu cầu US_ASCII giữ nguyên mọi ký tự Unicode. Test nên cố tình có cả **trường hợp thành công** và **trường hợp mong đợi thất bại**.

### Một bài kiểm thử tích hợp Java 17 không cần framework

Tệp **LocaleCharsetContractTest.java**:

~~~java
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParsePosition;
import java.util.Locale;

public class LocaleCharsetContractTest {
    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        DecimalFormat format = new DecimalFormat(
                "#,##0.00",
                DecimalFormatSymbols.getInstance(Locale.US)
        );
        format.setParseBigDecimal(true);

        BigDecimal value = new BigDecimal("1234.50");
        String formatted = format.format(value);
        ParsePosition pos = new ParsePosition(0);
        Number parsed = format.parse(formatted, pos);

        check(parsed instanceof BigDecimal, "Expected BigDecimal");
        check(pos.getIndex() == formatted.length(), "Partial parse");
        check(((BigDecimal) parsed).compareTo(value) == 0, "Value changed");

        String text = "Hà Nội 😀";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        check(text.equals(new String(bytes, StandardCharsets.UTF_8)),
                "UTF-8 round-trip failed");

        String wrong = new String(
                text.getBytes(StandardCharsets.US_ASCII),
                StandardCharsets.US_ASCII
        );
        check(!text.equals(wrong), "Expected lossy ASCII conversion");

        System.out.println("PASS: locale numeric round-trip");
        System.out.println("PASS: UTF-8 text round-trip");
        System.out.println("PASS: negative ASCII test");
    }
}
~~~

~~~bash
javac -encoding UTF-8 LocaleCharsetContractTest.java
java LocaleCharsetContractTest
~~~

~~~text
PASS: locale numeric round-trip
PASS: UTF-8 text round-trip
PASS: negative ASCII test
~~~

Phương thức check ném AssertionError nếu hợp đồng bị vi phạm, nên phép kiểm tra chạy được mà không phụ thuộc vào tùy chọn -ea của Java. Khi dự án đã dùng JUnit, có thể chuyển các mệnh đề này thành assertEquals/assertThrows và chạy qua nhiều bộ tham số Locale, Charset, trường hợp âm; nhưng cốt lõi vẫn là **định nghĩa rõ điều kiện được chấp nhận**.

## 10. Những lỗi phổ biến trong hệ thống thực tế

| Triệu chứng | Nguyên nhân có thể gặp | Cách kiểm tra và xử lý |
| --- | --- | --- |
| Định dạng số thay đổi theo máy | Dùng Locale mặc định hoặc dữ liệu vùng miền khác nhau | Truyền Locale tường minh, cố định formatter theo hợp đồng |
| Chuỗi số bị đọc sai hoặc một phần bị bỏ | Locale format và parse khác nhau; parser chỉ ăn tiền tố | Kiểm tra dấu, ParsePosition và tính hợp lệ toàn chuỗi |
| Mất phần lẻ khi chuyển đổi hai chiều | Định dạng làm tròn hoặc giới hạn số chữ số | Xác định miền round-trip, dùng BigDecimal và quy tắc rounding rõ |
| Ngày đổi cách viết theo JDK | Dữ liệu bản địa hóa/tên tháng khác nhau | Kiểm thử giá trị round-trip; chỉ đóng băng output nếu đặc tả yêu cầu |
| Tiếng Việt thành dấu hỏi | Charset không biểu diễn được ký tự và âm thầm thay thế | Chọn UTF-8 khi phù hợp, bật REPORT với yêu cầu không mất dữ liệu |
| Chữ bị lỗi kiểu mojibake | Bên encode/decode dùng Charset khác nhau | Theo dõi bytes tại ranh giới, thống nhất Charset end-to-end |
| Byte lỗi nhưng chương trình vẫn chạy | Decoder mặc định thay thế ký tự | Dùng CharsetDecoder với CodingErrorAction.REPORT nếu cần nghiêm ngặt |
| Emoji hoặc surrogate gây lỗi | String có surrogate chưa ghép cặp hoặc hệ thống không hỗ trợ | Tạo bộ dữ liệu Unicode hợp lệ, kiểm thử input malformed riêng |
| Hai chuỗi trông giống nhau nhưng equals false | Khác cách tổ hợp code point, ví dụ NFC và NFD | Xác định quy tắc normalization, không nhầm với charset |
| Test pass ở máy này nhưng fail ở CI | Locale/charset/timezone mặc định hoặc dữ liệu JDK khác | Chỉ định mọi tham số, khóa môi trường cần thiết và test bất biến |
| Dữ liệu gốc khác sau format rồi parse | Chuỗi hiển thị vốn là dạng mất thông tin | Chuyển qua kiểu dữ liệu chính xác; không dùng output cho tính toán chính |

## 11. Nguyên tắc triển khai an toàn

- **Dùng định dạng bản địa hóa cho UI, không dùng làm định dạng trao đổi mặc định.** Giao thức nên định nghĩa chuẩn rõ ràng, chẳng hạn số ở dạng quy định hoặc ngày ISO-8601; giao diện trình bày theo Locale người dùng.
- **Khi đọc văn bản từ file/API, xác định Charset đúng chỗ.** Nếu tệp hoặc giao thức chỉ định UTF-8, hãy dùng UTF-8 cả khi ghi và đọc, tránh phụ thuộc mặc định của hệ điều hành.
- **Tách lỗi định dạng và lỗi mã hóa.** Số sai do dấu phẩy/thập phân khác với bytes không thể giải mã; cách phát hiện và khắc phục cũng khác.
- **Không dùng phương thức parse dễ dãi làm xác thực duy nhất.** Kiểm tra đã xử lý hết đầu vào, miền giá trị và cú pháp do nghiệp vụ quy định.
- **Đặt quy tắc xử lý lỗi rõ ràng.** Nếu dữ liệu phải được bảo toàn, ưu tiên chế độ REPORT rồi xử lý ngoại lệ thay vì REPLACE/IGNORE.
- **Không giả định round-trip luôn giữ mọi thuộc tính.** Định dạng có thể làm tròn, bỏ timezone, thay scale; Unicode normalization và charset loss có thể làm thay đổi chính bytes hoặc nội dung.

## 12. Câu hỏi tự kiểm tra

**Câu 1.** Một số được format theo Locale.GERMANY thành 1.234,50. Có nên parse lại bằng Locale.US không?

**Trả lời:** không, nếu không có một hợp đồng chuyển đổi rõ ràng. Dấu chấm và dấu phẩy có vai trò khác nhau. Dùng cùng Locale/format hoặc định dạng trao đổi được chuẩn hóa, và kiểm tra parser đã đọc hết đầu vào.

**Câu 2.** Gọi parse một chuỗi như "123abc" trả ra 123 có đủ chứng minh đầu vào hợp lệ không?

**Trả lời:** không. Cần kiểm tra toàn bộ chuỗi đã được tiêu thụ; có thể cần thêm điều kiện cú pháp/miền dữ liệu do nghiệp vụ đặt ra.

**Câu 3.** Vì sao encode rồi decode tiếng Việt bằng US_ASCII có thể trả về String khác ban đầu?

**Trả lời:** US_ASCII không biểu diễn được nhiều ký tự có dấu. Các API tiện dụng có thể thay bằng ký tự đại diện; muốn từ chối mất dữ liệu thì dùng encoder/decoder strict với REPORT.

**Câu 4.** Hai chuỗi é dạng dựng sẵn và e + dấu tổ hợp có thể cùng hiển thị, nhưng String.equals trả false. UTF-8 round-trip có tự làm chúng trở nên giống nhau không?

**Trả lời:** không. UTF-8 giữ được biểu diễn Unicode hợp lệ đã có; việc chuẩn hóa cần được thực hiện có chủ đích theo hợp đồng.

## 13. Ghi nhớ

Kiểm thử chuyển đổi hai chiều cần xác định **cái gì phải được giữ nguyên**. Với số/ngày, thường quan trọng nhất là **giá trị ngữ nghĩa** sau format rồi parse, đồng thời phải kiểm soát rounding, Locale, độ chính xác và việc phân tích hết chuỗi. Với văn bản, quan trọng là **ký tự Unicode** hoặc thậm chí **bytes** có được phục hồi, Charset ở hai chiều có nhất quán hay không, và phần nào của dữ liệu có thể bị thay thế.

Locale quy định **cách trình bày và diễn giải văn bản theo vùng miền**; Charset quy định **cách mã hóa nội dung thành bytes**. Đặt Locale/Charset tường minh, kiểm tra cả trường hợp đúng và sai, và dùng round-trip với điều kiện rõ ràng sẽ giúp bài kiểm thử ổn định hơn trên môi trường phát triển, máy chủ và CI.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-testing-009">Phân biệt lỗi nội dung, lỗi chỉ số, lỗi regex và lỗi mã hóa</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt lỗi nội dung, lỗi chỉ số, lỗi regex và lỗi mã hóa khi xử lý String trong Java

Một lỗi liên quan đến chuỗi không nhất thiết là “Java xử lý sai ký tự”. Cùng triệu chứng *kết quả không như mong đợi* có thể xuất phát từ **nội dung thực tế khác nhau**, **truy cập sai vị trí**, **cú pháp biểu thức chính quy (regex)** hoặc **chuyển đổi byte và bảng mã**. Cách xử lý phải bắt đầu bằng việc xác định lớp lỗi; nếu chẩn đoán nhầm, cách sửa có thể che triệu chứng mà vẫn làm hỏng dữ liệu.

Bài học này tập trung vào `String` trong Java 17+: dấu hiệu nhận biết, ngoại lệ thường gặp, bước tái hiện, cách kiểm tra và cách sửa đúng cho bốn nhóm lỗi **nội dung – chỉ số – regex – mã hóa**. Các ví dụ được tách rõ thành đoạn minh họa và một chương trình Java độc lập có đầu ra xác định.

## 1. Bản đồ chẩn đoán nhanh

| Nhóm lỗi | Câu hỏi cần hỏi | Dấu hiệu tiêu biểu | Hướng kiểm tra |
| --- | --- | --- | --- |
| **Nội dung** | Hai chuỗi có **thực sự chứa cùng dữ liệu** không? | `equals` trả `false`; khoảng trắng/dấu Unicode khó thấy; `==` sai khi so nội dung; chuẩn hóa/hoa-thường khác nhau | In chuỗi ở trong dấu phân cách, kiểm tra `length`, code points, so sánh `equals`, quan sát Unicode |
| **Chỉ số** | Vị trí hoặc đoạn con có **nằm trong biên hợp lệ** không? | `StringIndexOutOfBoundsException`, lỗi lệch một đơn vị, cắt trúng surrogate | In `length`, `begin`/`end`, xác nhận chỉ số tính theo UTF-16 code units |
| **Regex** | Mẫu có đang bị hiểu là **cú pháp regex** không? | `PatternSyntaxException`; `split` chia quá nhiều; `replaceAll` thay ngoài ý muốn; lỗi `$`/`\` trong replacement | Tách rõ string literal, regex pattern, replacement; dùng `Pattern.quote` / `Matcher.quoteReplacement` khi cần |
| **Mã hóa** | Byte đã được **mã hóa và giải mã bằng cùng charset** chưa? | Chữ biến thành `�`, `?` hoặc mojibake (ví dụ `Ã©`); round-trip thất bại; byte hỏng | In hex của `byte[]`, ghi rõ `Charset`, dùng encoder/decoder ở chế độ `REPORT` |

**Ưu tiên kiểm tra:** xác định giá trị đầu vào và đầu ra thực tế; nếu có exception, đọc **loại ngoại lệ và vị trí phát sinh** trước khi đổi mã. Việc thấy ký tự `?` hoặc `�` không tự động cho biết lỗi ở phía gửi hay phía nhận: có thể dữ liệu đã bị thay thế trong một bước trước đó.

## 2. Nhận diện từng loại bằng một ca nhỏ có thể tái hiện

Nếu không có ngoại lệ nhưng kết quả sai, trước tiên **so nội dung thật và xác nhận API đang dùng**. Dưới đây là bốn tình huống có kết quả khác nhau dù đều liên quan đến `String`:

~~~java
// Nội dung: hai đối tượng khác nhau nhưng cùng chữ.
String a = "OK";
String b = new String("OK");
System.out.println(a == b);      // false
System.out.println(a.equals(b)); // true

// Chỉ số: endIndex vượt length() = 3.
// "abc".substring(2, 4); // IndexOutOfBoundsException

// Regex: dấu chấm là wildcard; thay literal dùng replace.
System.out.println("a.b".replaceAll(".", "X")); // XXX
System.out.println("a.b".replace(".", "_"));    // a_b

// Mã hóa: bên gửi dùng UTF-8, bên đọc dùng charset khác.
byte[] bytes = "é".getBytes(java.nio.charset.StandardCharsets.UTF_8);
String wrong = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
System.out.println("é".equals(wrong)); // false
~~~

- Với **nội dung**, xem `equals`, `length`, các code point và ký tự vô hình; `"e\u0301"` và `"\u00E9"` có thể giống mắt nhìn nhưng khác chuỗi, chỉ chuẩn hóa Unicode khi hợp đồng yêu cầu.
- Với **chỉ số**, kiểm tra `0 <= begin <= end <= length()` trước `substring(begin,end)`. `charAt(index)` yêu cầu `index < length()`; chỉ số là UTF-16, nên đừng cắt giữa surrogate pair nếu muốn giữ code point.
- Với **regex**, phân biệt `Pattern.compile`/`replaceAll`/`split` với thay thế literal `replace`. `Pattern.quote` bảo vệ văn bản trong pattern; `Matcher.quoteReplacement` bảo vệ văn bản trong replacement, đặc biệt khi chứa `$` hoặc backslash.
- Với **mã hóa**, kiểm tra byte gốc theo hex, charset tại hai đầu và quy tắc xử lý lỗi. `new String(bytes, UTF_8)` có thể thay byte sai bằng `U+FFFD`; nếu phải từ chối, dùng `CharsetDecoder` cấu hình `CodingErrorAction.REPORT`. Khi ký tự không biểu diễn được ở charset đích, dùng encoder `REPORT`.

Các kiểm thử chuyên biệt của từng nhóm ở các bài trước cho biết ma trận biên sâu hơn. Bài này dùng kết quả quan sát để **quyết định tiếp theo cần kiểm tra tầng nào**, tránh sửa lỗi nội dung bằng cách nuốt exception hoặc tự đổi charset.

## 3. Chương trình Java 17+ phân biệt bốn nhóm lỗi

Lưu nội dung dưới đây thành tệp `StringErrorKindsDemo.java`:

```java
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

public class StringErrorKindsDemo {
    public static void main(String[] args) {
        // 1. Nội dung: so tham chiếu và Unicode normalization.
        String same = new String("java");
        System.out.println("== same content: " + (same == "java"));
        System.out.println("equals: " + same.equals("java"));

        String composed = "\u00E9";
        String decomposed = "e\u0301";
        System.out.println("unicode equals: "
                + composed.equals(decomposed));
        System.out.println("unicode NFC equals: "
                + composed.equals(Normalizer.normalize(
                        decomposed, Normalizer.Form.NFC)));

        // 2. Chỉ số: ngoại lệ và UTF-16 code units.
        try {
            "abc".substring(2, 4);
        } catch (StringIndexOutOfBoundsException e) {
            System.out.println("index error: "
                    + e.getClass().getSimpleName());
        }

        String emoji = "A\uD83D\uDE00";
        System.out.println("UTF-16 units: " + emoji.length());
        System.out.println("code points: "
                + emoji.codePointCount(0, emoji.length()));
        System.out.println("high surrogate: "
                + Character.isHighSurrogate(emoji.charAt(1)));

        // 3. Regex: dấu chấm có nghĩa đặc biệt.
        String dotted = "a.b";
        System.out.println("regex dot: "
                + dotted.replaceAll(".", "X"));
        System.out.println("literal dot: "
                + dotted.replace(".", "_"));
        System.out.println("quoted regex: "
                + Pattern.compile(Pattern.quote("."))
                        .matcher(dotted).replaceAll("_"));

        try {
            Pattern.compile("[");
        } catch (PatternSyntaxException e) {
            System.out.println("invalid regex: "
                    + e.getClass().getSimpleName());
        }

        // 4. Mã hóa: sai charset, mất dữ liệu và decoder strict.
        byte[] utf8 = "\u00E9".getBytes(StandardCharsets.UTF_8);
        System.out.println("utf8 hex: "
                + HexFormat.of().formatHex(utf8));
        System.out.println("wrong decode equal: "
                + "\u00E9".equals(new String(
                        utf8, StandardCharsets.ISO_8859_1)));

        byte[] latin = "\u20AC".getBytes(
                StandardCharsets.ISO_8859_1);
        System.out.println("Latin-1 replacement hex: "
                + HexFormat.of().formatHex(latin));
        System.out.println("round trip euro: "
                + "\u20AC".equals(new String(
                        latin, StandardCharsets.ISO_8859_1)));

        try {
            StandardCharsets.ISO_8859_1.newEncoder()
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap("\u20AC"));
        } catch (CharacterCodingException e) {
            System.out.println("strict encode: "
                    + e.getClass().getSimpleName());
        }

        byte[] invalidUtf8 = {(byte) 0xC3, (byte) 0x28};
        String lossy = new String(
                invalidUtf8, StandardCharsets.UTF_8);
        String codePoints = lossy.codePoints()
                .mapToObj(cp -> String.format("U+%04X", cp))
                .collect(Collectors.joining(" "));
        System.out.println("lossy decode code points: " + codePoints);

        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(invalidUtf8));
        } catch (CharacterCodingException e) {
            System.out.println("strict decode: "
                    + e.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac -encoding UTF-8 StringErrorKindsDemo.java
java StringErrorKindsDemo
```

Đầu ra xác định của chương trình:

```text
== same content: false
equals: true
unicode equals: false
unicode NFC equals: true
index error: StringIndexOutOfBoundsException
UTF-16 units: 3
code points: 2
high surrogate: true
regex dot: XXX
literal dot: a_b
quoted regex: a_b
invalid regex: PatternSyntaxException
utf8 hex: c3a9
wrong decode equal: false
Latin-1 replacement hex: 3f
round trip euro: false
strict encode: UnmappableCharacterException
lossy decode code points: U+FFFD U+0028
strict decode: MalformedInputException
```

Các trường hợp đã kiểm tra:

- **Nội dung:** cùng chữ `java` nhưng tham chiếu khác; dạng Unicode composed/decomposed không bằng nhau cho đến khi chuẩn hóa NFC.
- **Chỉ số:** `substring(2, 4)` vượt quá độ dài `3` và phát sinh `StringIndexOutOfBoundsException`; emoji chiếm hai code units nhưng một code point.
- **Regex:** `.` khớp ba ký tự, còn `replace` hoặc `Pattern.quote` giữ nghĩa dấu chấm nguyên văn; mẫu `[` không hợp lệ.
- **Mã hóa:** `é` thành `c3a9` trong UTF-8; giải mã sai bằng Latin-1 không giữ nguyên chuỗi; dấu euro bị thay khi encode ISO-8859-1; strict encoder/decoder báo lỗi đúng loại.

Các giá trị trong ví dụ chứng minh **hành vi xác định** và nhóm nguyên nhân. Trong dự án thật, cần tái hiện trên dữ liệu và đường I/O đang gây lỗi.

## 4. Quy trình debug có thể áp dụng lặp lại

### Bước 1 — Thu nhỏ trường hợp lỗi

Giữ một ví dụ đầu vào ngắn nhất vẫn làm lỗi xuất hiện: `"a.b"` cho regex dấu chấm, `"abc"` với `charAt(3)` cho lỗi biên, `"é"` cho sai UTF-8/Latin-1. Thu nhỏ giúp phân biệt nguyên nhân mà không bị logic khác che khuất.

### Bước 2 — Phân loại theo kết quả hay ngoại lệ

- **Trả kết quả sai nhưng không ném ngoại lệ:** xét nội dung thật, dùng đúng API literal/regex hay không, charset đã đồng bộ chưa.
- **`StringIndexOutOfBoundsException`:** kiểm tra `length` và công thức `begin/end`, giá trị `-1` từ tìm kiếm, code point so với code unit.
- **`PatternSyntaxException`:** in mẫu regex (đã bảo vệ dữ liệu nhạy cảm) và xác minh escape, dấu ngoặc, quantifier; nếu pattern là văn bản nguyên vẹn thì dùng `Pattern.quote`.
- **`CharacterCodingException` khi encoder/decoder strict:** xác định byte hoặc code point không hợp lệ, charset và vị trí trong pipeline.
- **`NullPointerException`:** kiểm tra hợp đồng null của API và nguồn vào. Đây là tình huống cần kiểm tra riêng, không tự kết luận thuộc một trong bốn nhóm chính.

### Bước 3 — Quan sát dữ liệu đúng lớp

**Nếu lỗi nội dung:** quan sát độ dài, `equals`, code points, khoảng trắng. **Nếu lỗi chỉ số:** log `begin`, `end`, `length()` cùng điều kiện kiểm tra biên. **Nếu lỗi regex:** log pattern đã xử lý escape, phân biệt toàn chuỗi (`matches`) với tìm một đoạn (`find`), xem ý nghĩa của replacement. **Nếu lỗi encoding:** quan sát byte hex **trước** khi decode, charset nhận/cho và có bước chuyển mã nào ở giữa.

Để tái hiện và giải thích, có thể dùng các thao tác quan sát sau:

```java
// Chỉ là các ví dụ độc lập, giả sử các biến có sẵn.
System.out.println("length=" + input.length());
System.out.println("hex=" + HexFormat.of().formatHex(bytes));
System.out.println("index=" + index + ", end=" + end);
```

Khi code này đưa vào sản phẩm, cần tránh log dữ liệu nhạy cảm/secret; ghi nhận những thuộc tính tối thiểu cần cho việc chẩn đoán.

### Bước 4 — Đặt quy tắc mong muốn trước khi sửa

Quy tắc phải xác định dữ liệu nào hợp lệ: có bỏ khoảng trắng không? chấp nhận Unicode dạng composed/decomposed không? chỉ số tính theo code points hay code units? pattern có là regex do người dùng cấu hình hay chỉ chuỗi literal? chấp nhận dữ liệu lỗi bị thay thế hay buộc từ chối?

Khi đã rõ hợp đồng, lựa chọn sửa sẽ tương ứng: `equals`, xử lý normalized input, kiểm tra biên, `Pattern.quote`, `Matcher.quoteReplacement`, charset tường minh, strict decoder hoặc thông báo lỗi hợp lệ.

### Bước 5 — Viết kiểm thử cho trường hợp biên

Kiểm thử tối thiểu nên có:

| Nhóm | Trường hợp bình thường | Trường hợp biên / lỗi |
| --- | --- | --- |
| Nội dung | Hai chuỗi giống hệt và `equals` đúng | Thừa space, `null`, dấu kết hợp, khác locale/casing |
| Chỉ số | Lấy substring tại đoạn hợp lệ | Chuỗi rỗng, `begin=end`, `-1` từ `indexOf`, `end>length`, surrogate pair |
| Regex | Pattern đơn giản khớp | Dấu `.` nguyên văn, dấu `$` trong replacement, pattern `[` sai, chuỗi rỗng khi split |
| Encoding | UTF-8 round-trip với tiếng Việt | Charset không biểu diễn được ký tự, byte UTF-8 bị hỏng, decode khác charset, emoji |

Không cần tạo hệ thống test phức tạp chỉ để kiểm tra hành vi của JDK; giá trị thực sự là **kiểm tra hàm của ứng dụng phản ứng đúng khi đầu vào biến đổi**.

## 5. Các anti-pattern cần tránh

### Bắt ngoại lệ rồi tiếp tục với dữ liệu sai

```java
// Không nên dùng như cách sửa mọi lỗi chỉ số:
try {
    returnValue = input.substring(start, end);
} catch (IndexOutOfBoundsException e) {
    returnValue = "";
}
```

Đây là đoạn minh họa. Trả chuỗi rỗng có thể biến một lỗi lập trình thành dữ liệu hợp lệ giả. Tốt hơn: xác định contract cho `start/end`, validate trước khi gọi và báo lỗi rõ nghĩa nếu không hợp lệ.

### Ép tất cả chuỗi qua `replaceAll`

Nếu chỉ cần thay ký tự/đoạn nguyên văn, dùng `String.replace`. Dùng regex khi thực sự cần ngôn ngữ pattern, và kiểm soát pattern + replacement theo hai quy tắc escape khác nhau.

### “Sửa encoding” bằng `new String(old.getBytes(...), ...)` tùy tiện

Nếu `old` đã là văn bản bị mojibake hoặc đã mất ký tự thay thế, chuỗi chuyển qua lại nhiều charset có thể làm hỏng thêm. Nên tìm **byte gốc** và thông tin charset gốc của nguồn, xác định chính xác giai đoạn encode/decode không khớp. Chỉ áp dụng chuyển mã sửa sai khi đã kiểm chứng được dữ liệu và thuật toán biến đổi tương ứng.

### So sánh chuỗi bằng `==` để “nhanh hơn”

`==` là phép so tham chiếu; thay `equals` bằng `==` làm thay đổi ý nghĩa kiểm tra. Nếu cần cải thiện hiệu năng, đo trước và chọn cách biểu diễn/thuật toán phù hợp mà vẫn giữ semantics.

### Trộn quy tắc index byte và index Unicode

Byte offset của file UTF-8, vị trí UTF-16 code unit và số code points là ba hệ đo khác nhau. Đừng dùng một số nguyên cho cả ba mà thiếu bước chuyển đổi rõ ràng.

## 6. Bài tập tự kiểm tra

**Câu 1.** `"name ".equals("name")` trả `false` mà không có exception. Thuộc nhóm nào?

**Trả lời:** **Lỗi nội dung**: chuỗi bên trái có thêm một dấu cách. Kiểm tra và quyết định theo quy tắc dữ liệu liệu dấu cách có ý nghĩa hay không.

**Câu 2.** `"abc".charAt(3)` gây lỗi nào?

**Trả lời:** **Lỗi chỉ số**, vì index hợp lệ từ 0 đến 2. `charAt(3)` gây `StringIndexOutOfBoundsException`.

**Câu 3.** Vì sao `"a.b".replaceAll(".", "X")` trả `"XXX"`?

**Trả lời:** **Lỗi dùng regex sai ý định**: dấu chấm trong pattern có ý nghĩa đặc biệt. Nếu muốn thay dấu chấm nguyên văn, dùng `replace(".", "X")` hoặc pattern `"\\."`.

**Câu 4.** Một dịch vụ gửi chuỗi `"é"` theo UTF-8, đầu nhận giải mã bằng ISO-8859-1. Lỗi gì?

**Trả lời:** **Sai charset khi giải mã**. Byte UTF-8 `c3 a9` bị hiểu theo bộ mã khác; hãy decode bằng UTF-8 và ghi rõ charset giữa hai phía.

**Câu 5.** Tại sao `"e\u0301".equals("\u00E9")` trả `false` nhưng vẫn có thể nhìn như cùng chữ?

**Trả lời:** **Lỗi giả định về nội dung Unicode**. Hai chuỗi có code point sequence khác nhau; chuẩn hóa NFC khi chính sách dữ liệu yêu cầu.

**Câu 6.** Một regex `[A-Z]+` hợp lệ nhưng `matcher.find()` trả `false`. Đây có phải `PatternSyntaxException` không?

**Trả lời:** Không. Pattern hợp lệ và không có match là **kết quả tìm kiếm bình thường**. Chỉ pattern sai cú pháp mới gây `PatternSyntaxException` khi compile.

**Câu 7.** Khi decode byte UTF-8 sai định dạng, có thể buộc Java ném lỗi thay vì tự thay ký tự không?

**Trả lời:** Có. Dùng `StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)`; với byte lỗi thích hợp, `decode` sẽ ném một lớp con của `CharacterCodingException`.

## 7. Checklist khi một ca lỗi String xuất hiện

1. **Lỗi thuộc lớp nào?** Giá trị khác kỳ vọng, truy cập biên, pattern không hợp lệ hay sai encoding?
2. **Có dữ liệu gốc không?** Giữ lại đầu vào ngắn có thể tái hiện; nếu liên quan I/O, giữ byte gốc ở môi trường kiểm thử.
3. **Đang so cái gì?** Tham chiếu hay nội dung, UTF-16 index hay code point, literal hay regex, String hay byte?
4. **Khi nào dữ liệu đổi?** Xác định bước đọc → giải mã → chuẩn hóa → xử lý → encode; kiểm tra từng ranh giới.
5. **Đã sửa đúng hợp đồng chưa?** Đừng xóa ký tự, nuốt ngoại lệ hoặc chọn charset bằng suy đoán.
6. **Kiểm thử hồi quy có đủ trường hợp biên chưa?** Bao gồm chuỗi rỗng, Unicode, regex literal, byte hỏng và index đầu/cuối.

Điều quan trọng nhất là **quan sát đúng đơn vị dữ liệu**. Nếu đang gặp lỗi nội dung, nhìn vào code points; nếu lỗi chỉ số, nhìn vào biên UTF-16; nếu regex, nhìn vào pattern và replacement; nếu mã hóa, nhìn vào **byte gốc và charset**. Khi phân loại đúng, cách sửa thường rõ ràng hơn và kiểm thử cũng nhắm đúng nguyên nhân.

### Tài liệu chính thức

- [Java SE 17 — `String`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html).
- [Java SE 17 — `Pattern` và `Matcher`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html).
- [Java SE 17 — `Charset`, `CharsetEncoder` và `CharsetDecoder`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/Charset.html).

</details>

- [Quay lại đầu trang](#back-to-top)
