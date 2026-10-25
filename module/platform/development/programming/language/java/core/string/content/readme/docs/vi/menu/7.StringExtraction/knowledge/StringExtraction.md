<a id="back-to-top"></a>

# Trích xuất chuỗi

## Menu
- [substring từ một vị trí đến hết chuỗi](#string-extraction-001)
- [substring với biên đầu bao gồm và biên cuối không bao gồm](#string-extraction-002)
- [Chuỗi rỗng khi hai biên bằng nhau](#string-extraction-003)
- [subSequence và hợp đồng CharSequence](#string-extraction-004)
- [Trường hợp biên âm, vượt độ dài hoặc đảo thứ tự](#string-extraction-005)
- [Cắt theo code point thay vì chia đôi surrogate pair](#string-extraction-006)
- [Cắt theo cụm ký tự bằng BreakIterator](#string-extraction-007)
- [Không phụ thuộc vào việc kết quả có dùng chung đối tượng hoặc bộ nhớ nội bộ](#string-extraction-008)

## <a id="string-extraction-001">substring từ một vị trí đến hết chuỗi</a>

<details>
<summary>Xem chi tiết</summary>

# `String.substring(beginIndex)`: Trích xuất chuỗi từ một vị trí đến hết chuỗi trong Java

## 1. Bài toán và ý nghĩa của `substring(beginIndex)`

Khi đã có một chuỗi nhưng chỉ cần giữ phần **bắt đầu từ một vị trí xác định đến ký tự cuối cùng**, Java cung cấp phương thức:

```java
public String substring(int beginIndex)
```

Phương thức này thuộc lớp `java.lang.String`. Nó trả về một `String` đại diện cho **phần còn lại của chuỗi**, bắt đầu **tại** `beginIndex`, bao gồm ký tự ở vị trí đó (nếu có) và toàn bộ ký tự phía sau.

```java
String text = "Java Core";
String part = text.substring(5);

System.out.println(part); // Core
```

Chuỗi `"Java Core"` có các vị trí như sau:

```text
Ký tự:  J  a  v  a     C  o  r  e
Chỉ số: 0  1  2  3  4  5  6  7  8
```

Chỉ số `5` trỏ đến chữ `C`. Vì vậy `text.substring(5)` trả về `"Core"`.

**Cốt lõi:** `beginIndex` là chỉ số **bắt đầu lấy**, được tính từ `0`. Với phiên bản nhận **một tham số**, bạn không cần chỉ định điểm kết thúc: Java tự lấy đến hết chuỗi.

## 2. Cú pháp và giá trị trả về

Dạng thường dùng:

```java
String original = "abcdef";
String suffix = original.substring(2); // "cdef"
```

- **Đối tượng nhận:** `original` có kiểu `String`, không phải `null`.
- **Tham số:** `beginIndex` là một số nguyên `int`, biểu thị vị trí bắt đầu.
- **Kết quả:** Một giá trị `String` chứa các ký tự từ vị trí `beginIndex` đến cuối.
- **Chuỗi gốc:** Không bị thay đổi, vì `String` là đối tượng **bất biến** (*immutable*).

Trong cách nói bằng khoảng chỉ số, với `length = original.length()`, phần được chọn có khoảng **`[beginIndex, length)`**: có lấy vị trí bắt đầu, còn `length` là mốc kết thúc *không bao gồm*. Đây là ký hiệu khoảng nửa mở, không có ký tự ở vị trí `length`.

### Chỉ số hợp lệ

Phương thức yêu cầu:

\[
0 \leq \texttt{beginIndex} \leq \texttt{length()}
\]

| `beginIndex` | Kết quả | Lý do |
|---|---|---|
| `0` | Toàn bộ nội dung chuỗi | Bắt đầu từ ký tự đầu tiên |
| `1` đến `length() - 1` | Hậu tố bắt đầu từ chỉ số tương ứng | Bỏ qua phần đứng trước |
| `length()` | Chuỗi rỗng `""` | Không còn ký tự phía sau mốc bắt đầu |
| Số âm | Ném `StringIndexOutOfBoundsException` | Nằm trước đầu chuỗi |
| Lớn hơn `length()` | Ném `StringIndexOutOfBoundsException` | Vượt quá điểm kết thúc |

**Đừng nhầm `length()` với chỉ số của ký tự cuối.** Với chuỗi không rỗng, chỉ số của ký tự cuối là `length() - 1`. Nhưng `substring(length())` **vẫn hợp lệ** và trả về chuỗi rỗng.

Ví dụ, chuỗi `"Java"` có độ dài `4`:

```java
String s = "Java";

s.substring(0); // "Java"
s.substring(1); // "ava"
s.substring(2); // "va"
s.substring(3); // "a"
s.substring(4); // ""
// s.substring(5); // StringIndexOutOfBoundsException
```

Các dòng trên minh họa biểu thức bên trong một phương thức; chúng **không phải** một tệp Java độc lập.

## 3. Cơ chế từng bước

Xét:

```java
String word = "Programming";
String remainder = word.substring(7);
```

Đánh số theo chỉ số từ `0`:

```text
P  r  o  g  r  a  m  m  i  n  g
0  1  2  3  4  5  6  7  8  9  10
                     ^
                   index 7: m
```

`word.substring(7)` trả về `"ming"` (gồm các ký tự ở chỉ số 7, 8, 9 và 10). Phần `"Program"` đứng trước mốc bắt đầu không nằm trong kết quả. `word` vẫn giữ nguyên `"Programming"`.

Có thể hiểu bằng ba bước:

1. Xác định độ dài chuỗi (`word.length()` là `11`).
2. Kiểm tra `beginIndex` có nằm trong khoảng từ `0` đến `11`, kể cả `11`, hay không.
3. Trả về phần nội dung từ `beginIndex` đến hết; nếu bắt đầu tại `11`, kết quả là `""`.

Không cần gọi `.substring(beginIndex, word.length())` để thực hiện trường hợp phổ biến này; cách gọi một tham số thể hiện rõ ý định **lấy đến cuối**.

## 4. Ví dụ Java 17+ hoàn chỉnh, có thể chạy

Tạo tệp **`SubstringToEndDemo.java`**:

```java
public class SubstringToEndDemo {
    public static void main(String[] args) {
        String text = "Java Core";

        String suffix = text.substring(5);
        System.out.println("part=" + suffix);
        System.out.println("original=" + text);

        System.out.println("all=" + text.substring(0));
        System.out.println("atEnd=[" + text.substring(text.length()) + "]");

        String fileName = "report.csv";
        int lastDot = fileName.lastIndexOf('.');
        String extension = lastDot == -1
                ? "(none)"
                : fileName.substring(lastDot + 1);
        System.out.println("extension=" + extension);

        String path = "/users/42";
        int lastSlash = path.lastIndexOf('/');
        String lastSegment = path.substring(lastSlash + 1);
        System.out.println("lastSegment=" + lastSegment);

        String unicode = "A😀B";
        String afterA = unicode.substring(1);
        System.out.println("utf16Length=" + unicode.length());
        System.out.println("suffixCodePoints="
                + afterA.codePointCount(0, afterA.length()));

        try {
            text.substring(text.length() + 1);
        } catch (StringIndexOutOfBoundsException ex) {
            System.out.println("badIndex="
                    + ex.getClass().getSimpleName());
        }
    }
}
```

Trong thư mục chứa tệp, biên dịch và chạy với JDK 17 trở lên:

```bash
javac -encoding UTF-8 SubstringToEndDemo.java
java SubstringToEndDemo
```

**Stdout dự kiến:**

```text
part=Core
original=Java Core
all=Java Core
atEnd=[]
extension=csv
lastSegment=42
utf16Length=4
suffixCodePoints=2
badIndex=StringIndexOutOfBoundsException
```

Giải thích các điểm đáng chú ý:

- `text.substring(5)` trả `"Core"`. Việc in lại `text` cho thấy chuỗi gốc không đổi.
- `text.substring(0)` lấy toàn bộ nội dung.
- `text.substring(text.length())` trả `""`, vì vậy khi kẹp trong `[...]` ta thấy `[]`.
- `lastIndexOf('.')` trả về vị trí dấu chấm cuối; `substring(lastDot + 1)` lấy phần **sau** dấu chấm, được `"csv"`.
- `lastIndexOf('/')` giúp xác định dấu phân tách cuối cùng của một đường dẫn minh họa, sau đó `substring(lastSlash + 1)` lấy `"42"`.
- Chuỗi `"A😀B"` có **4 UTF-16 code unit** (`A` = 1, emoji = 2, `B` = 1). Hậu tố sau `A` chứa **2 Unicode code point** (emoji và `B`).
- Chỉ số lớn hơn độ dài gây `StringIndexOutOfBoundsException`; ví dụ bắt ngoại lệ để phần trình diễn tiếp tục chạy.

> Chú ý: Ví dụ dùng `/` trong một chuỗi đường dẫn đơn giản để dạy cách trích xuất. Việc phân tích URL thật, đường dẫn hệ điều hành hay đường dẫn đã mã hóa cần API chuyên dụng tương ứng, tùy ngữ cảnh.

## 5. Trích xuất hậu tố sau ký tự phân cách

Một cách dùng thực tế là **tìm điểm bắt đầu rồi lấy toàn bộ phần còn lại**. Ví dụ:

```java
String record = "ID:12345";
int colon = record.indexOf(':');

if (colon >= 0) {
    String value = record.substring(colon + 1);
    System.out.println(value); // 12345
}
```

Tại sao phải `+ 1`? `indexOf(':')` trả về vị trí của dấu `:`. Nếu gọi `substring(colon)`, kết quả sẽ còn cả dấu phân cách (`":12345"`). Dùng `colon + 1` để bắt đầu ở ký tự kế tiếp.

Phải phân biệt hai mục tiêu:

```java
String text = "key=value";
int equal = text.indexOf('=');

String includingSeparator = text.substring(equal);     // "=value"
String excludingSeparator = text.substring(equal + 1); // "value"
```

Đây là những đoạn minh họa nằm trong phương thức, với chuỗi đầu vào chứa dấu `=` như đã biết. Nếu dấu phân cách **không chắc chắn tồn tại**, luôn kiểm tra kết quả tìm kiếm.

### Xử lý trường hợp không tìm thấy dấu phân cách

`indexOf(...)` và `lastIndexOf(...)` trả về `-1` khi không tìm thấy ký tự hoặc chuỗi cần tìm. Thông thường phải kiểm tra:

```java
static String valueAfterColon(String input) {
    int colon = input.indexOf(':');
    if (colon < 0) {
        return "";
    }
    return input.substring(colon + 1);
}
```

Phương thức trên trả `""` khi không có dấu `:`. Đây chỉ là **một lựa chọn thiết kế**; tùy bài toán, bạn cũng có thể muốn trả toàn bộ chuỗi, `Optional<String>` hoặc thông báo dữ liệu sai định dạng.

Một lỗi tinh tế cần tránh: nếu viết `substring(indexOf(':') + 1)` mà không kiểm tra, khi không tìm thấy dấu phân cách thì `indexOf` trả `-1`, cộng `1` thành `0`, và phương thức âm thầm trả về **toàn bộ chuỗi**. Nó không ném ngoại lệ nên dễ làm che giấu dữ liệu đầu vào sai.

## 6. Sử dụng độ dài để lấy N ký tự cuối

Để lấy **`n` UTF-16 code unit ở cuối**, điểm bắt đầu thường là:

\[
\texttt{start = text.length() - n}
\]

Ví dụ:

```java
String code = "INV-2026";
String year = code.substring(code.length() - 4);

System.out.println(year); // 2026
```

Vì `"INV-2026"` dài `8` code unit, `start = 8 - 4 = 4`; hậu tố từ chỉ số `4` là `"2026"`.

Để an toàn, cần đảm bảo `0 <= n <= text.length()`:

```java
static String lastUnits(String text, int n) {
    if (n < 0 || n > text.length()) {
        throw new IllegalArgumentException("n nằm ngoài độ dài chuỗi");
    }
    return text.substring(text.length() - n);
}
```

Phương thức `lastUnits` xử lý **code unit**, không đảm bảo luôn trích đúng `n` ký tự Unicode theo cảm nhận của con người. Với emoji hoặc ký tự bổ trợ được biểu diễn bằng cặp surrogate, điểm cắt có thể nằm giữa cặp. Phần tiếp theo giải thích cách tránh lỗi này.

## 7. Chỉ số của `String` sử dụng UTF-16

`String.length()` và các offset của `substring` đếm **UTF-16 code unit**, không nhất thiết là số Unicode code point hay ký tự hiển thị. Chẳng hạn, `"A😀B"` có độ dài `4`: emoji chiếm hai code unit. `substring(1)` bắt đầu đúng tại emoji, còn `substring(2)` bắt đầu giữa cặp surrogate và có thể để lại một nửa surrogate đứng lẻ.

Khi cần lấy đoạn theo **code point**, hãy chuyển số lượng code point thành offset UTF-16 bằng `offsetByCodePoints` trước khi cắt:

```java
String text = "A😀B";
int start = text.offsetByCodePoints(0, 2);
System.out.println(text.substring(start)); // B
```

Đây là ví dụ minh họa trong phương thức. Cắt theo code point vẫn có thể chia cụm chữ có dấu kết hợp hoặc emoji nhiều code point; những trường hợp hiển thị đó cần phân đoạn grapheme riêng.

## 8. Những trường hợp biên và lỗi thường gặp

### Lỗi A: Nghĩ rằng chỉ số bắt đầu từ 1

**Sai về ý định:**

```java
String s = "Java";
System.out.println(s.substring(1)); // ava
```

Để lấy cả `"Java"`, bắt đầu từ **`0`**, không phải `1`.

### Lỗi B: Cho rằng chỉ số bằng `length()` là không hợp lệ

```java
String s = "abc";
System.out.println("[" + s.substring(3) + "]"); // []
```

Chỉ số bằng độ dài là hợp lệ và trả chuỗi rỗng. Chỉ số **lớn hơn** `length()` mới vượt ranh giới.

### Lỗi C: Bỏ qua giá trị trả về

```java
String s = "abcdef";
s.substring(3);
System.out.println(s); // abcdef
```

`String` bất biến nên `substring` **không sửa tại chỗ**. Nếu muốn giữ hậu tố:

```java
s = s.substring(3);
System.out.println(s); // def
```

Việc gán lại thay đổi giá trị biến `s`, chứ không sửa nội dung đối tượng `String` ban đầu.

### Lỗi D: Dùng kết quả `indexOf` mà không kiểm tra

```java
String s = "no-separator";
int index = s.indexOf(':'); // -1
String part = s.substring(index + 1); // Chính là "no-separator"
```

Không có dấu `:` nhưng phép cộng `-1 + 1` thành `0`. Cần kiểm tra `index >= 0` nếu việc không có dấu phân cách là trường hợp cần xử lý riêng.

### Lỗi E: Truyền chỉ số âm hoặc vượt độ dài

```java
String s = "abc";
// s.substring(-1); // Ném StringIndexOutOfBoundsException
// s.substring(4);  // Ném StringIndexOutOfBoundsException
```

Có thể kiểm tra ranh giới trước khi gọi:

```java
static String fromIndex(String text, int beginIndex) {
    if (beginIndex < 0 || beginIndex > text.length()) {
        throw new IllegalArgumentException("beginIndex không hợp lệ");
    }
    return text.substring(beginIndex);
}
```

Phương thức bọc này minh họa cách chọn ngoại lệ của ứng dụng. Nếu gọi trực tiếp `substring` với chỉ số sai, Java ném `StringIndexOutOfBoundsException`.

### Lỗi F: Gọi phương thức trên `null`

```java
String s = null;
// s.substring(0); // Ném NullPointerException
```

`null` không phải một đối tượng `String`, nên không thể gọi phương thức trên nó. Đây là lỗi **khác** với chỉ số không hợp lệ. Kiểm tra đầu vào nếu `null` được phép xuất hiện trong thiết kế của chương trình.

### Lỗi G: Nhầm `substring(int)` với `substring(int, int)`

Hai cách gọi:

```java
String s = "abcdefgh";

s.substring(3);    // "defgh": từ 3 đến hết
s.substring(3, 6); // "def": lấy các chỉ số 3, 4, 5; không gồm 6
```

Dạng hai tham số cho phép chỉ định riêng điểm kết thúc (`endIndex` không bao gồm). Dạng một tham số **luôn** lấy tới hết, nên chọn dạng này khi đúng ý định và không phải tính toán chỉ số cuối một cách thừa thãi.

## 9. Phân biệt với những thao tác gần giống

| Bài toán | Phương thức phù hợp | Ví dụ |
|---|---|---|
| Lấy phần từ một chỉ số đến cuối | `substring(beginIndex)` | `"abcdef".substring(3)` → `"def"` |
| Lấy phần giữa hai mốc | `substring(beginIndex, endIndex)` | `"abcdef".substring(1, 4)` → `"bcd"` |
| Lấy vị trí bắt đầu của một đoạn | `indexOf(...)` | `"a:b".indexOf(':')` → `1` |
| Lấy vị trí xuất hiện cuối | `lastIndexOf(...)` | `"a:b:c".lastIndexOf(':')` → `3` |
| Tìm số lượng UTF-16 code unit | `length()` | `"Java".length()` → `4` |
| Trích từ vị trí tính theo code point | `offsetByCodePoints(...)` rồi `substring(...)` | Tránh cắt vào giữa surrogate pair |

`substring` chỉ **trích xuất một phạm vi đã xác định**. Nếu chưa biết điểm bắt đầu nằm ở đâu, thường kết hợp với `indexOf`/`lastIndexOf` hoặc một thuật toán tìm vị trí theo quy tắc dữ liệu.

## 10. Lưu ý về hiệu năng và đối tượng trả về

`substring` trả về một giá trị `String` bất biến; không có lời hứa công khai rằng kết quả luôn là instance mới, luôn là instance nguồn hay có cơ chế bộ nhớ cụ thể. Một triển khai có thể tối ưu trường hợp lấy toàn bộ chuỗi hoặc chuỗi rỗng. Vì vậy, khi so nội dung, hãy dùng `equals` thay vì `==`.

Đối với xử lý lượng lớn chuỗi, nên đo hiệu năng và mức cấp phát trên JDK cùng dữ liệu thực tế. Không tạo thêm bản sao chỉ vì giả định `substring` nhất định dùng chung hoặc sao chép vùng nhớ.

## 11. Bài tập và đáp án

**Bài 1.** Với `String s = "Hello World";`, hãy lấy `"World"`.

**Đáp án:**

```java
String result = s.substring(6);
```

Dấu cách nằm ở chỉ số `5`, chữ `W` ở chỉ số `6`.

**Bài 2.** Với `"ABC"`, ba kết quả của `substring(0)`, `substring(2)` và `substring(3)` là gì?

**Đáp án:** Lần lượt là `"ABC"`, `"C"` và `""`.

**Bài 3.** Với `"token:abc:def"`, lấy mọi ký tự sau **dấu `:` đầu tiên**.

**Đáp án:**

```java
String input = "token:abc:def";
int p = input.indexOf(':');
String value = p < 0 ? "" : input.substring(p + 1);
// value = "abc:def"
```

Nếu cần lấy sau dấu `:` **cuối cùng**, đổi `indexOf(':')` thành `lastIndexOf(':')`, khi đó giá trị là `"def"`.

**Bài 4.** Vì sao `"A😀B".substring(2)` cần thận trọng?

**Đáp án:** Chỉ số `2` rơi vào giữa hai UTF-16 code unit dùng để biểu diễn emoji, có thể làm vỡ surrogate pair. Nên xác định ranh giới code point (hoặc grapheme, tùy bài toán) trước khi cắt.

**Bài 5.** Đoạn sau in gì?

```java
String s = "12345";
s.substring(3);
System.out.println(s);
```

**Đáp án:** `12345`. Giá trị `"45"` được trả về nhưng đã bị bỏ qua, còn chuỗi gốc không đổi.

## 12. Ghi nhớ

`String.substring(beginIndex)` có thể được đọc là: **“Lấy chuỗi từ chỉ số `beginIndex` (bao gồm) đến hết”**. Chỉ số bắt đầu từ `0`, miền hợp lệ là **`0` đến `length()` (bao gồm cả hai đầu)**, bắt đầu ở `length()` cho chuỗi rỗng, còn ra ngoài miền thì ném `StringIndexOutOfBoundsException`. Kết quả là một giá trị `String` cần được lưu hoặc sử dụng, chuỗi gốc vẫn bất biến. Khi dữ liệu có emoji hoặc ký tự Unicode bổ trợ, hãy nhớ chỉ số `String` đo theo **UTF-16 code unit**, không mặc nhiên theo ký tự hiển thị.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-extraction-002">substring với biên đầu bao gồm và biên cuối không bao gồm</a>

<details>
<summary>Xem chi tiết</summary>

# `String.substring()`: biên đầu bao gồm, biên cuối không bao gồm trong Java

Phương thức `substring()` dùng để **trích xuất một phần văn bản** từ `String`. Quy tắc trọng tâm của overload hai tham số là: **lấy ký tự từ vị trí `beginIndex` (bao gồm) đến ngay trước vị trí `endIndex` (không bao gồm)**.

```java
String text = "ABCDE";
String part = text.substring(1, 4); // "BCD"
```

Chỉ số của `String` bắt đầu từ **0**. Trong `"ABCDE"`, các chỉ số 1, 2, 3 lần lượt ứng với `B`, `C`, `D`; vị trí 4 (`E`) là biên kết thúc và **không nằm trong kết quả**. Có thể mô tả khoảng bằng ký hiệu nửa mở **`[beginIndex, endIndex)`**.

Đây là quy tắc nền tảng để cắt chuỗi đúng, tránh lỗi lệch một vị trí (*off-by-one*) và tránh ngoại lệ khi xử lý dữ liệu.

## 1. Hai overload của `substring()`

| API | Phạm vi được lấy | Ví dụ với `"ABCDE"` |
| --- | --- | --- |
| `substring(int beginIndex)` | Từ `beginIndex` (bao gồm) **đến hết chuỗi** | `substring(2)` → `"CDE"` |
| `substring(int beginIndex, int endIndex)` | Từ `beginIndex` (bao gồm) tới **trước** `endIndex` | `substring(1, 4)` → `"BCD"` |

`substring(beginIndex)` tương đương **về nội dung** với `substring(beginIndex, text.length())`. Cả hai trả về một `String`; `String` gốc không bị sửa vì `String` trong Java là **bất biến** (*immutable*).

### Hình dung chỉ số

```text
Ký tự:       A    B    C    D    E
Vị trí:      0    1    2    3    4    5
             |----|----|----|----|----|
substring(1, 4)
                  B    C    D
             [    1 ------------ 4    )
```

Đối với `"ABCDE"`, `length() == 5`. Chỉ số **5** là vị trí ngay **sau** ký tự cuối, hoàn toàn hợp lệ với vai trò **biên cuối**; nó không phải chỉ số của một ký tự có thể gọi `charAt(5)`.

## 2. Quy tắc chính xác cho khoảng `[beginIndex, endIndex)`

Với chuỗi `s` có `n = s.length()`, hai chỉ số của overload hai tham số phải thỏa:

```text
0 <= beginIndex <= endIndex <= n
```

- **Biên đầu bao gồm:** ký tự tại vị trí `beginIndex` được lấy, miễn là khoảng không rỗng.
- **Biên cuối không bao gồm:** ký tự tại vị trí `endIndex` không được lấy.
- **Độ dài kết quả:** luôn bằng `endIndex - beginIndex`, tính theo **UTF-16 code unit**.
- **Hai biên bằng nhau:** `substring(k, k)` trả về chuỗi rỗng `""` đối với mọi `k` trong khoảng `0..n`.
- **Lấy toàn bộ:** `substring(0, n)` có cùng nội dung với `s`.
- **Lấy đến hết:** `substring(k, n)` hoặc `substring(k)`.
- **Lấy một code unit ở vị trí `i`:** `substring(i, i + 1)` nếu `0 <= i < n`.

Với overload một tham số, điều kiện là **`0 <= beginIndex <= n`**. Vì thế `s.substring(s.length())` hợp lệ và trả về chuỗi rỗng; `s.substring(s.length() + 1)` không hợp lệ.

### Vì sao thiết kế biên cuối không bao gồm lại tiện?

Quy tắc nửa mở giúp làm việc với chỉ số mà không phải cộng hoặc trừ 1 trong nhiều tình huống:

- **Đếm đơn vị trong đoạn:** `end - begin`.
- **Đoạn rỗng:** `begin == end`.
- **Chia liền kề:** `s.substring(0, k)` và `s.substring(k, s.length())` bao phủ cả chuỗi, không trùng và không mất code unit.
- **Cắt phần cuối:** không cần đoán chỉ số ký tự cuối; dùng `s.length()` làm biên cuối.
- **Lặp theo khoảng:** tương ứng quy tắc thường gặp `for (int i = begin; i < end; i++)`.

Ví dụ, `"ABCDE".substring(0, 2)` lấy `"AB"` và `"ABCDE".substring(2, 5)` lấy `"CDE"`. Điểm cắt **2** là đầu của đoạn sau và cuối loại trừ của đoạn trước.

## 3. Ví dụ Java 17+ chạy độc lập

Lưu nội dung sau vào `SubstringBasics.java`:

```java
public class SubstringBasics {
    public static void main(String[] args) {
        String text = "ABCDE";

        System.out.println(text.substring(1, 4));
        System.out.println(text.substring(0, 1));
        System.out.println(text.substring(4, 5));

        System.out.println(text.substring(2, 2).isEmpty());
        System.out.println(text.substring(text.length()).isEmpty());

        System.out.println(text.substring(2));
        System.out.println(text.equals("ABCDE"));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 SubstringBasics.java
java SubstringBasics
```

Kết quả chính xác:

```text
BCD
A
E
true
true
CDE
true
```

Giải thích:

1. `substring(1, 4)` lấy `B`, `C`, `D`, **không** lấy `E`.
2. `substring(0, 1)` lấy đúng phần tử tại chỉ số 0.
3. `substring(4, 5)` lấy đúng phần tử tại chỉ số 4, cũng là code unit cuối.
4. `substring(2, 2)` lấy đoạn dài 0 nên `isEmpty()` là `true`.
5. `substring(text.length())` bắt đầu ngay sau phần tử cuối, cũng trả về chuỗi rỗng.
6. `substring(2)` lấy toàn bộ phần còn lại (`CDE`).
7. `text` gốc vẫn là `"ABCDE"`; `substring()` không xóa, di chuyển hoặc chỉnh sửa ký tự trong `text`.

## 4. Lỗi chỉ số và ngoại lệ

Khi các biên nằm ngoài điều kiện hợp lệ, `substring()` ném `IndexOutOfBoundsException`; trong OpenJDK, bạn thường nhìn thấy lớp cụ thể `StringIndexOutOfBoundsException`. Thông điệp ngoại lệ có thể khác giữa các JDK, nên ứng dụng không nên phụ thuộc vào đúng câu thông báo.

Ví dụ các lời gọi **không hợp lệ** với `"ABCDE"`:

| Lời gọi | Nguyên nhân |
| --- | --- |
| `substring(-1, 2)` | `beginIndex` âm |
| `substring(2, 6)` | `endIndex` lớn hơn `length() == 5` |
| `substring(4, 3)` | `beginIndex > endIndex` |
| `substring(6)` | Biên đầu lớn hơn độ dài |
| `substring(0, -1)` | Biên cuối âm |

Một đoạn rỗng **không phải lỗi** khi hai biên bằng nhau và trong phạm vi hợp lệ: `substring(5, 5)` là hợp lệ với chuỗi dài 5. Tương tự, `substring(0, 0)` cũng trả về chuỗi rỗng.

### Kiểm tra trước khi cắt

Khi các chỉ số do người dùng hoặc API khác cung cấp, hãy xác minh trước:

```java
public static String slice(String text, int begin, int end) {
    if (text == null) {
        throw new IllegalArgumentException("text must not be null");
    }
    if (begin < 0 || end < begin || end > text.length()) {
        throw new IllegalArgumentException("invalid substring bounds");
    }
    return text.substring(begin, end);
}
```

Đây là một **phương thức minh họa** cần đặt trong một class; nó không phải chương trình `main` độc lập. Nếu `text` là `null`, gọi trực tiếp `text.substring(...)` sẽ gây `NullPointerException`; vì thế kiểm tra `null` là trách nhiệm tách biệt với kiểm tra phạm vi.

Có hai chính sách xử lý thường dùng:

- **Từ chối biên sai:** ném ngoại lệ rõ nghĩa khi API yêu cầu đoạn phải chính xác.
- **Giới hạn trong phạm vi hợp lệ:** chỉ thực hiện khi nghiệp vụ chấp nhận cắt ngắn, ví dụ lấy preview tối đa ba code unit bằng `text.substring(0, Math.min(3, text.length()))` trên chuỗi không `null`.

Không nên âm thầm dùng `Math.min` để chữa mọi `endIndex` sai, vì lỗi lập trình có thể bị che khuất và dữ liệu trả về có thể khác mong muốn.

## 5. Ứng dụng: trích xuất các trường có độ rộng cố định

Khoảng nửa mở đặc biệt hữu ích khi dữ liệu quy định **vị trí đầu và số code unit** cần lấy. Nếu một trường bắt đầu tại `start` và có `count` code unit, biên cuối là `start + count`. Trước khi cộng, hãy kiểm tra `count <= text.length() - start` để tránh vượt biên hay tràn số nguyên.

Ví dụ, chuỗi `"20261023ACTIVE"` chứa ngày tám chữ số theo sau bởi trạng thái sáu chữ cái. Lưu thành `FixedFieldSliceDemo.java`:

```java
public class FixedFieldSliceDemo {
    static String field(String text, int start, int count) {
        if (start < 0 || start > text.length()
                || count < 0 || count > text.length() - start) {
            throw new IndexOutOfBoundsException("Invalid fixed-width field");
        }
        return text.substring(start, start + count);
    }

    public static void main(String[] args) {
        String record = "20261023ACTIVE";
        System.out.println("date=" + field(record, 0, 8));
        System.out.println("status=" + field(record, 8, 6));
        System.out.println("empty=" + field(record, record.length(), 0).isEmpty());
    }
}
```

```bash
javac -encoding UTF-8 FixedFieldSliceDemo.java
java FixedFieldSliceDemo
```

```text
date=20261023
status=ACTIVE
empty=true
```

Ví dụ này minh họa trực tiếp công thức `end = start + count` và lý do biên cuối loại trừ. Khi trường được định nghĩa theo **ký tự Unicode** hoặc grapheme thay vì code unit, phải chuyển đổi ranh giới trước khi gọi `substring`.

## 6. Lưu ý quan trọng: chỉ số của `substring()` là UTF-16

`length()` và hai biên của `substring` tính theo **UTF-16 code unit**. Emoji ngoài BMP thường chiếm hai code unit; vì vậy `"A😀B".length() == 4` dù chuỗi có ba code point. Cắt `substring(1, 3)` lấy trọn emoji, còn `substring(1, 2)` chỉ giữ high surrogate.

Nếu yêu cầu là cắt theo code point, hãy tìm biên bằng `offsetByCodePoints` rồi mới truyền kết quả vào `substring`. Nếu phải giữ cả dấu kết hợp hoặc emoji nhiều code point, cần ranh giới grapheme; kiểm tra số học `0 <= begin <= end <= length` chỉ bảo vệ offset chứ không bảo đảm cụm hiển thị không bị tách.

## 7. Các sai lầm phổ biến

1. **Tưởng biên cuối được lấy:** `"ABCDE".substring(1, 4)` là `"BCD"`, không phải `"BCDE"`.
2. **Lấy nhầm một ký tự:** muốn lấy code unit tại index `i` bằng `substring(i, i + 1)`, không phải `substring(i, i)` (chuỗi rỗng). Với Unicode cần cân nhắc code point/grapheme.
3. **Tự trừ 1 khi lấy hết chuỗi:** `substring(0, length() - 1)` sẽ **bỏ ký tự cuối**. Muốn lấy toàn bộ thì dùng `substring(0, length())`.
4. **Nghĩ `endIndex == length()` là sai:** đây là trường hợp hợp lệ và rất thông dụng.
5. **Nhầm số lượng ký tự với chỉ số cuối:** để lấy `count` code unit từ `start`, biên cuối là `start + count` (chỉ khi còn nằm trong phạm vi). Chẳng hạn lấy ba code unit từ chỉ số 2 thì dùng `substring(2, 5)`, không dùng `substring(2, 3)`.
6. **Quên kiểm tra `indexOf() == -1`:** nếu dấu phân cách không tồn tại, dùng `-1` làm chỉ số cắt dễ dẫn tới lỗi.
7. **Nhầm `substring()` với `split()`:** `split()` sử dụng regex và trả về mảng chuỗi; `substring()` dùng biên vị trí và trả về một chuỗi. Muốn tách theo một dấu đơn giản thường có thể kết hợp `indexOf` và `substring`.
8. **Nghĩ `substring()` sửa chuỗi gốc:** `String` bất biến; kết quả trả về được gán cho biến mới hoặc gán lại biến hiện có, không làm nội dung đối tượng `String` gốc thay đổi.
9. **Dùng `substring()` với chỉ số do người dùng nhập nhưng không kiểm tra biên:** các giá trị âm, đảo thứ tự hoặc vượt `length()` đều có thể gây ngoại lệ.
10. **Cắt chuỗi Unicode ở ranh giới code unit:** có thể phá cặp surrogate hoặc cụm ký tự người dùng nhận thấy. Chọn đơn vị đếm dựa trên yêu cầu.

## 8. Bài tập kiểm tra nhanh

Giả sử `s = "ABCDEFG"` (`length() == 7`). Hãy tự dự đoán kết quả trước khi đọc đáp án.

| Biểu thức | Kết quả | Lý do |
| --- | --- | --- |
| `s.substring(0, 3)` | `"ABC"` | Bao gồm 0, 1, 2; không lấy 3 |
| `s.substring(3, 7)` | `"DEFG"` | Lấy từ D đến hết, biên 7 hợp lệ |
| `s.substring(3)` | `"DEFG"` | Overload một tham số lấy phần đuôi |
| `s.substring(0, 0)` | `""` | Biên bằng nhau cho đoạn rỗng |
| `s.substring(7, 7)` | `""` | Đoạn rỗng ở cuối chuỗi |
| `s.substring(3, 2)` | Ngoại lệ | Biên đầu lớn hơn biên cuối |
| `s.substring(0, 8)` | Ngoại lệ | Biên cuối vượt độ dài 7 |

Đặc biệt, `s.substring(0, 3).length()` bằng **3**, đúng bằng `3 - 0`. Nếu trích hai đoạn `left = s.substring(0, 3)` và `right = s.substring(3, s.length())` thì `left + right` có cùng nội dung với `s`.

## 9. Checklist khi sử dụng `substring()`

1. Xác định đoạn cần lấy theo **chỉ số UTF-16** hay theo code point/grapheme của Unicode.
2. Chọn hai mốc `begin` và `end`; nhớ rằng **đầu lấy, cuối không lấy**.
3. Kiểm tra `0 <= begin <= end <= text.length()`.
4. Dùng `substring(begin, end)` cho đoạn giới hạn hai đầu hoặc `substring(begin)` cho phần còn lại.
5. Nếu biên tính từ dấu phân cách, kiểm tra kết quả `indexOf`/`lastIndexOf` trước khi sử dụng.
6. Thử các trường hợp mép: chuỗi rỗng, `begin == end`, `end == length()`, không tìm thấy dấu mốc và Unicode nhiều code unit.

**Ghi nhớ:** `substring(beginIndex, endIndex)` lấy khoảng **`[beginIndex, endIndex)`**. Biên đầu **bao gồm**, biên cuối **không bao gồm**, nên độ dài đoạn luôn là **`endIndex - beginIndex`** theo đơn vị UTF-16 của `String`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-extraction-003">Chuỗi rỗng khi hai biên bằng nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Chuỗi rỗng khi hai biên bằng nhau: `String.substring(beginIndex, endIndex)`

Khi trích xuất một đoạn của `String` trong Java, hai tham số `beginIndex` và `endIndex` xác định phạm vi ký tự cần lấy. Nếu **hai biên bằng nhau và cùng nằm trong phạm vi hợp lệ**, đoạn được chọn có độ dài bằng 0, nên `substring(beginIndex, endIndex)` trả về **chuỗi rỗng `""`**. Đây là hành vi đúng theo hợp đồng API, không phải lỗi, cũng không phải `null`.

Ví dụ:

```java
String text = "Java";
String empty = text.substring(2, 2);

System.out.println(empty.length());  // 0
System.out.println(empty.isEmpty()); // true
System.out.println(empty.equals("")); // true
```

`substring(2, 2)` không chọn ký tự nào, dù vị trí 2 là một ranh giới nằm giữa chuỗi có nội dung. Cần phân biệt điều này với một phạm vi **không hợp lệ** như `substring(5, 5)` trên `"Java"` dài 4: hai số bằng nhau vẫn không cứu được chỉ số nằm ngoài giới hạn.

## 1. Quy tắc của hai biên: bắt đầu bao gồm, kết thúc không bao gồm

Chữ ký phương thức:

```java
public String substring(int beginIndex, int endIndex)
```

Phương thức lấy các UTF-16 code unit tại các chỉ số thuộc khoảng **`[beginIndex, endIndex)`**:

- `beginIndex` là chỉ số đầu, **được tính vào** kết quả.
- `endIndex` là chỉ số kết thúc, **không được tính vào** kết quả.
- Số UTF-16 code unit trong kết quả là `endIndex - beginIndex`.
- Phải thỏa mãn đồng thời `0 <= beginIndex <= endIndex <= text.length()`.

Với chuỗi `"Java"`, các ký tự có chỉ số 0, 1, 2 và 3, nhưng có **năm vị trí biên** từ 0 đến 4:

```text
Vị trí biên:  0   1   2   3   4
              | J | a | v | a |
Chỉ số char:    0   1   2   3
```

Ví dụ các phạm vi:

| Lời gọi | Phạm vi lấy | Kết quả | Độ dài |
| --- | --- | --- | ---: |
| `text.substring(0, 2)` | `[0, 2)` | `"Ja"` | 2 |
| `text.substring(2, 4)` | `[2, 4)` | `"va"` | 2 |
| `text.substring(1, 3)` | `[1, 3)` | `"av"` | 2 |
| `text.substring(0, 0)` | `[0, 0)` | `""` | 0 |
| `text.substring(2, 2)` | `[2, 2)` | `""` | 0 |
| `text.substring(4, 4)` | `[4, 4)` | `""` | 0 |

Khi `beginIndex == endIndex`, khoảng nửa mở có **không phần tử**, nên không có ký tự nào được đưa vào chuỗi kết quả. Quy tắc này nhất quán với cách Java mô tả các đoạn bằng chỉ số bắt đầu bao gồm và kết thúc loại trừ.

## 2. Ví dụ hoàn chỉnh: mọi vị trí biên hợp lệ đều có thể tạo chuỗi rỗng

Tạo tệp `EmptySubstringDemo.java`:

```java
public class EmptySubstringDemo {
    public static void main(String[] args) {
        String text = "Java";

        String atStart = text.substring(0, 0);
        String inMiddle = text.substring(2, 2);
        String atEnd = text.substring(text.length(), text.length());
        String fromEnd = text.substring(text.length());

        System.out.println("start=[" + atStart + "]");
        System.out.println("middle=[" + inMiddle + "]");
        System.out.println("end=[" + atEnd + "]");
        System.out.println("fromEnd=[" + fromEnd + "]");
        System.out.println("length=" + inMiddle.length());
        System.out.println("isEmpty=" + inMiddle.isEmpty());
        System.out.println("equalsEmpty=" + inMiddle.equals(""));
    }
}
```

Biên dịch/chạy trên JDK 17 trở lên:

```bash
javac -encoding UTF-8 EmptySubstringDemo.java
java EmptySubstringDemo
```

Kết quả chính xác:

```text
start=[]
middle=[]
end=[]
fromEnd=[]
length=0
isEmpty=true
equalsEmpty=true
```

Dấu ngoặc vuông trong kết quả do chương trình **chủ động nối vào** để nhìn thấy ranh giới; chúng không nằm trong chuỗi rỗng. Nếu viết `System.out.println(inMiddle);`, chương trình chỉ in một dòng trống.

Các trường hợp `atStart`, `inMiddle`, `atEnd` đều trả về giá trị bằng `""`. Đồng thời, overload một tham số `substring(beginIndex)` hiểu rằng lấy từ `beginIndex` đến hết chuỗi; do đó gọi `text.substring(text.length())` cũng trả về chuỗi rỗng.

## 3. Hai biên bằng nhau vẫn phải nằm trong phạm vi hợp lệ

Điều kiện quan trọng là:

```text
0 <= beginIndex <= endIndex <= text.length()
```

Nếu `beginIndex == endIndex == k` thì lời gọi hợp lệ **chỉ khi** `0 <= k <= text.length()`.

| Ví dụ với `text = "Java"`, `length() = 4` | Hợp lệ? | Vì sao |
| --- | --- | --- |
| `substring(0, 0)` | Có | Cả hai biên đều là 0, nằm trong [0, 4] |
| `substring(2, 2)` | Có | Ranh giới số 2 nằm trong [0, 4] |
| `substring(4, 4)` | Có | Biên cuối 4 bằng `length()` và được cho phép |
| `substring(-1, -1)` | Không | Biên âm |
| `substring(5, 5)` | Không | Biên 5 lớn hơn `length()` |
| `substring(3, 2)` | Không | Biên bắt đầu lớn hơn biên kết thúc |

Với tham số không hợp lệ, `String.substring` ném ngoại lệ `StringIndexOutOfBoundsException` (thuộc họ `IndexOutOfBoundsException`). Không nên phụ thuộc vào nội dung thông báo của ngoại lệ vì cách mô tả có thể khác theo phiên bản hoặc triển khai JDK.

Tệp `EmptyRangeValidation.java` minh họa hành vi thành công/thất bại mà không phụ thuộc thông báo ngoại lệ:

```java
public class EmptyRangeValidation {
    public static void main(String[] args) {
        String text = "Java";
        int[][] ranges = {
            {0, 0},
            {2, 2},
            {4, 4},
            {-1, -1},
            {5, 5},
            {3, 2}
        };

        for (int[] range : ranges) {
            int begin = range[0];
            int end = range[1];

            try {
                String part = text.substring(begin, end);
                System.out.printf("[%d,%d) -> length=%d%n",
                        begin, end, part.length());
            } catch (IndexOutOfBoundsException ex) {
                System.out.printf("[%d,%d) -> invalid%n", begin, end);
            }
        }
    }
}
```

Lưu đúng tên tệp rồi chạy:

```bash
javac -encoding UTF-8 EmptyRangeValidation.java
java EmptyRangeValidation
```

Kết quả:

```text
[0,0) -> length=0
[2,2) -> length=0
[4,4) -> length=0
[-1,-1) -> invalid
[5,5) -> invalid
[3,2) -> invalid
```

Trường hợp `[4,4)` hợp lệ dù chuỗi chỉ có các chỉ số **ký tự** từ 0 đến 3, vì `endIndex` biểu diễn **vị trí biên ngay sau ký tự cuối**. Ngược lại, `[5,5)` không phải biên tồn tại trong chuỗi dài 4.

## 4. Chuỗi nguồn rỗng cũng có một phạm vi trích xuất hợp lệ

Với `String emptySource = "";`, `emptySource.length() == 0`. Phạm vi hợp lệ duy nhất của overload hai tham số là `[0, 0)`:

```java
String emptySource = "";

System.out.println(emptySource.substring(0, 0).isEmpty()); // true
System.out.println(emptySource.substring(0).isEmpty());    // true
// emptySource.substring(1, 1); // Ngoại lệ vì vượt length() = 0
```

Các lệnh trên là đoạn minh họa nằm trong một phương thức Java, không phải chương trình độc lập. Trường hợp nguồn rỗng **không có ngoại lệ đặc biệt**: quy tắc biên và điều kiện hợp lệ vẫn giữ nguyên như với chuỗi không rỗng.

## 5. Chuỗi rỗng không phải `null` và không phải chuỗi chứa dấu cách

Cần phân biệt ba giá trị:

- `""`: đối tượng `String` có độ dài `0`, `isEmpty() == true`.
- `" "`: chuỗi chứa một dấu cách thông thường, `length() == 1`, `isEmpty() == false`.
- `null`: tham chiếu **không trỏ đến đối tượng `String` nào**; không thể gọi `length()` hoặc `isEmpty()` trực tiếp trên tham chiếu đó.

`substring(k, k)` với `k` hợp lệ luôn trả về một giá trị kiểu `String` có nội dung rỗng. Phương thức không trả về `null` để diễn tả “không chọn ký tự”.

Ví dụ:

```java
String text = "A B";
String part = text.substring(1, 1);

System.out.println(part == null);   // false
System.out.println(part.length());  // 0
System.out.println(part.isEmpty()); // true

String space = text.substring(1, 2);
System.out.println(space.length());  // 1
System.out.println(space.isEmpty()); // false
```

Đoạn trên là các lệnh minh họa đặt trong một phương thức. Mặc dù `part` và `space` có thể đều trông “không có chữ” khi in trên console, chúng có giá trị và độ dài khác nhau.

Nếu cần so sánh nội dung trích xuất với chuỗi rỗng, dùng `part.isEmpty()` hoặc `part.equals("")`. Không dùng `part == ""` để dựa vào danh tính đối tượng: kể cả khi một triển khai có thể tái sử dụng tham chiếu đến chuỗi rỗng, hợp đồng cần quan tâm là **nội dung bằng nhau**.

## 6. Ứng dụng: trích xuất phần trước/sau dấu phân cách

Chuỗi rỗng thường là kết quả **hợp lệ** khi dấu phân cách nằm ngay đầu hoặc cuối dữ liệu. Ví dụ chuỗi dạng `key=value`:

- `"=value"` có phần khóa rỗng ở trước dấu `=`.
- `"key="` có phần giá trị rỗng ở sau dấu `=`.
- `"="` có cả hai phần rỗng.
- `"name=Lan"` có cả khóa và giá trị không rỗng.

Tệp `DelimiterExtraction.java`:

```java
public class DelimiterExtraction {
    static void showParts(String line) {
        int separator = line.indexOf('=');

        if (separator < 0) {
            System.out.println("No separator: " + line);
            return;
        }

        String key = line.substring(0, separator);
        String value = line.substring(separator + 1, line.length());

        System.out.println("key=[" + key + "], value=[" + value + "]");
    }

    public static void main(String[] args) {
        showParts("name=Lan");
        showParts("=value");
        showParts("key=");
        showParts("=");
        showParts("missing");
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 DelimiterExtraction.java
java DelimiterExtraction
```

Đầu ra:

```text
key=[name], value=[Lan]
key=[], value=[value]
key=[key], value=[]
key=[], value=[]
No separator: missing
```

Giải thích kỹ hai trường hợp quan trọng:

- Khi `line = "=value"`, `separator == 0` nên `line.substring(0, separator)` chính là `substring(0, 0)`: **khóa rỗng**, không phải lỗi.
- Khi `line = "key="`, `separator == line.length() - 1` nên phần giá trị bắt đầu tại `separator + 1 == line.length()`; hai biên bằng nhau tại cuối chuỗi nên giá trị rỗng.

Kiểm tra `separator < 0` là cần thiết: `indexOf('=')` trả về `-1` nếu không tìm thấy; dùng trực tiếp giá trị này làm biên cho `substring` có thể gây lỗi hoặc phân tích dữ liệu sai. Với yêu cầu nghiệp vụ mà khóa/giá trị **không được phép rỗng**, cần xác thực thêm sau khi trích xuất; không nên coi chuỗi rỗng tự động là ngoại lệ API.

## 7. Áp dụng trong thuật toán dùng cửa sổ [begin, end)

Nhiều thuật toán quét văn bản lưu một cặp chỉ số `begin` và `end`, trong đó `begin` là nơi bắt đầu của đoạn hiện tại và `end` là vị trí ngay sau ký tự cuối đã duyệt. Khi hai chỉ số bằng nhau, cửa sổ có độ dài 0.

Ví dụ, một hàm lấy đoạn giữa hai vị trí biên:

```java
static String extractWindow(String text, int begin, int end) {
    return text.substring(begin, end);
}
```

Với `extractWindow("ABCDE", 3, 3)`, kết quả là `""`. Không cần viết điều kiện riêng `if (begin == end) return "";` để tránh ngoại lệ: nếu hai biên **hợp lệ**, `substring` đã xử lý đúng. Tuy nhiên, nếu thuật toán mong đợi **ít nhất một ký tự**, điều kiện `begin == end` có thể báo hiệu rằng cửa sổ chưa có dữ liệu; quy tắc nghiệp vụ phải được kiểm tra riêng.

Những tình huống hay gặp:

1. **Cắt phần tiền tố** bằng `substring(0, boundary)`: nếu `boundary == 0`, tiền tố rỗng.
2. **Cắt phần hậu tố** bằng `substring(boundary, length())`: nếu `boundary == length()`, hậu tố rỗng.
3. **Cắt giữa hai mốc** bằng `substring(left, right)`: nếu hai mốc trùng nhau, không có ký tự giữa chúng.
4. **Bước lặp hoặc quét không tiêu thụ ký tự mới**: cửa sổ `[cursor, cursor)` có thể hợp lệ nhưng cần quyết định thuật toán nên tiếp tục hay dừng để tránh lặp vô hạn.

Điểm cuối rất quan trọng: một `substring` trả chuỗi rỗng không tự di chuyển chỉ số `cursor`. Nếu thiết kế vòng lặp dựa vào việc mỗi vòng phải tiến ít nhất một ký tự, cần đảm bảo phần cập nhật chỉ số phù hợp.

## 8. Chỉ số của `substring` tính theo UTF-16 code unit

Một phạm vi rỗng `[k, k)` không lấy bất kỳ code unit nào, **kể cả khi `k` nằm giữa hai nửa surrogate pair**. Chẳng hạn, với `"A😀B"`, `substring(2, 2)` vẫn trả `""`, trong khi `substring(1, 2)` chỉ lấy nửa đầu emoji và không biểu diễn một code point hoàn chỉnh.

Việc hai biên bằng nhau giải quyết câu hỏi “có dữ liệu nào trong đoạn?”; khi đoạn **không rỗng** và chứa Unicode bổ sung, phải chọn biên code point hoặc grapheme thích hợp cho nghiệp vụ.

## 9. `substring` với `subSequence` và đoạn rỗng

`String` triển khai `CharSequence`, nên `text.subSequence(k, k)` cũng cho dãy có độ dài `0` với `k` hợp lệ. Ví dụ:

```java
String text = "Java";
CharSequence empty = text.subSequence(3, 3);
System.out.println(empty.isEmpty()); // true
```

`substring` khai báo trả về `String`, còn `subSequence` khai báo trả về `CharSequence`. Đây là khác biệt về kiểu API; với nguồn `String`, cả hai cùng diễn đạt đoạn rỗng theo quy tắc nửa mở.

## 10. Lỗi thường gặp

- **`substring(k, k)` luôn hợp lệ?** Không: `k` vẫn phải nằm trong `[0, length()]`. `substring(5, 5)` trên `"Java"` dài bốn code unit vẫn ném ngoại lệ.
- **Đoạn rỗng đồng nghĩa `null`?** Không: `""` có `length() == 0`, gọi được `isEmpty()`; `null` không phải đối tượng `String`.
- **Một dấu cách có rỗng không?** Không: `" "` có độ dài một và `isEmpty() == false`. `isBlank()` hỏi một câu khác về nội dung khoảng trắng.
- **Có nên so sánh với `== ""`?** Không: `==` so identity, dùng `isEmpty()` hoặc `equals("")` cho nội dung.
- **Vòng lặp không tiến do kết quả rỗng?** `substring` không di chuyển con trỏ; thuật toán phải bảo đảm tiến độ hoặc quy tắc dừng riêng.

## 11. Câu hỏi tự kiểm tra

**Câu 1.** Với `String s = "abc";`, kết quả của `s.substring(1, 1)` và `s.substring(3, 3)` là gì?

**Đáp án:** Cả hai là `""`, vì hai biên trùng nhau và `1` lẫn `3` đều nằm trong phạm vi biên hợp lệ `[0, 3]`.

**Câu 2.** `s.substring(4, 4)` với `s = "abc"` có trả chuỗi rỗng không?

**Đáp án:** Không. Phương thức ném ngoại lệ vì `4 > s.length() == 3`. Hai biên bằng nhau không đủ để một khoảng trở nên hợp lệ.

**Câu 3.** `"abc".substring("abc".length())` trả về gì?

**Đáp án:** `""`. Overload một tham số lấy từ chỉ số bắt đầu đến hết chuỗi; bắt đầu ngay tại biên cuối nên không còn ký tự nào.

**Câu 4.** Chuỗi `"x="` có phần sau dấu bằng dài bao nhiêu khi trích bằng `substring(separator + 1, length())`?

**Đáp án:** `0`. Dấu `=` ở cuối nên `separator + 1 == length()`; hai biên hợp lệ bằng nhau, kết quả là chuỗi rỗng.

**Câu 5.** `substring(k, k)` tại vị trí giữa high surrogate và low surrogate có gây ra một surrogate đứng lẻ không?

**Đáp án:** Không, vì không lấy code unit nào. Nhưng nếu trích đoạn khác rỗng và cắt một nửa surrogate pair thì mới có nguy cơ tạo một chuỗi UTF-16 chứa surrogate đứng lẻ.

**Ghi nhớ:** Với `String.substring(beginIndex, endIndex)`, nếu `0 <= beginIndex == endIndex <= length()`, kết quả luôn là **`""`** và có **độ dài 0**. Chỉ số phải hợp lệ; chuỗi rỗng là kết quả bình thường của một khoảng `[k, k)`, không phải `null` và không phải ngoại lệ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-extraction-004">subSequence và hợp đồng CharSequence</a>

<details>
<summary>Xem chi tiết</summary>

# `subSequence` và hợp đồng `CharSequence` trong Java

Khi cần lấy một phần của văn bản, Java có cả `String.substring(start, end)` và `subSequence(start, end)`. Điểm đáng học ở `subSequence` không chỉ là hai chỉ số cắt chuỗi: đây là **phương thức của giao diện `CharSequence`**, nên cùng một lời gọi có thể áp dụng cho `String`, `StringBuilder`, `StringBuffer`, `CharBuffer` hoặc lớp khác triển khai giao diện này.

Điều quan trọng là phân biệt **hợp đồng chung của `CharSequence`** với **hành vi cụ thể của từng implementation**. Giao diện quy định những ký tự mà đoạn con phải biểu diễn và ý nghĩa của chỉ số; nó **không hứa** rằng kết quả luôn là `String`, luôn là bản sao độc lập, luôn là view hay luôn bất biến.

## 1. `CharSequence` là gì?

`java.lang.CharSequence` là giao diện trừu tượng cho một chuỗi có thể đọc được theo **các đơn vị `char` (UTF-16 code unit)**. Các thao tác cốt lõi là:

| Phương thức | Ý nghĩa |
| --- | --- |
| `int length()` | Số đơn vị `char` (UTF-16 code unit), không nhất thiết bằng số ký tự Unicode mà người đọc cảm nhận |
| `char charAt(int index)` | Đọc đơn vị `char` ở chỉ số `index`, đánh số từ 0 |
| `CharSequence subSequence(int start, int end)` | Lấy một dãy con theo khoảng chỉ số `[start, end)` |
| `String toString()` | Tạo biểu diễn `String` có các đơn vị ký tự theo đúng thứ tự của dãy |
| `boolean isEmpty()` | Với Java 17+, kiểm tra `length() == 0`; đây là phương thức mặc định của giao diện |
| `IntStream chars()` / `IntStream codePoints()` | Duyệt các UTF-16 code unit hoặc Unicode code point; ý nghĩa không giống nhau với ký tự ngoài BMP |

Các lớp thường gặp:

- `String`: nội dung bất biến; thích hợp cho kết quả văn bản ổn định.
- `StringBuilder`: nội dung thay đổi được, không bảo đảm thread-safe.
- `StringBuffer`: nội dung thay đổi được, nhiều thao tác được đồng bộ hóa; điều này không có nghĩa mọi workflow nhiều bước tự động nguyên tử.
- `CharBuffer` trong `java.nio`: bộ đệm ký tự, có `position`/`limit` và có thể tạo các đoạn nhìn vào vùng dữ liệu dùng chung.
- Một lớp do ứng dụng tự viết cũng có thể triển khai `CharSequence`.

Ví dụ phương thức nhận `CharSequence`:

```java
static void show(CharSequence text) {
    System.out.println("Length: " + text.length());
    System.out.println(text.subSequence(0, Math.min(3, text.length())));
}
```

Đây là **đoạn minh họa phương thức**, cần đặt trong một lớp để biên dịch. Lời gọi `show("Java")` hoặc `show(new StringBuilder("Java"))` đều hợp lệ. Khi chỉ cần đọc các ký tự, khai báo tham số là `CharSequence` giúp API nhận nhiều kiểu đầu vào. Nếu cần các tính năng riêng của `String`, hãy khai báo `String` hoặc chuyển đổi có chủ đích.

## 2. Hợp đồng của `subSequence(start, end)`

Trong giao diện, phương thức có chữ ký:

```java
CharSequence subSequence(int start, int end);
```

Khoảng được hiểu là **nửa mở** (*half-open interval*):

- `start` là chỉ số ký tự **được lấy**, bắt đầu từ 0.
- `end` là vị trí **dừng, không lấy ký tự tại đó**.
- Độ dài đoạn con bằng `end - start`.
- Ký tự thứ `i` của đoạn con tương ứng với ký tự thứ `start + i` của dãy gốc, xét tại thời điểm dữ liệu không có thay đổi gây xung đột.
- Khoảng hợp lệ cần `0 <= start <= end <= length()`.
- Nếu chỉ số không hợp lệ, lời gọi ném `IndexOutOfBoundsException` hoặc ngoại lệ con của nó.
- `start == end` là hợp lệ và tạo dãy con rỗng, kể cả khi cả hai bằng `length()`.

Ví dụ với `"ABCDE"`, đánh số:

```text
Ký tự:    A   B   C   D   E
Chỉ số:   0   1   2   3   4
Ranh giới:0   1   2   3   4   5

subSequence(1, 4) --> "BCD"
subSequence(0, 5) --> "ABCDE"
subSequence(2, 2) --> ""
subSequence(5, 5) --> ""
subSequence(4, 2) --> lỗi
```

Sự nhất quán của khoảng `[start, end)` giúp tính độ dài và cắt các đoạn liên tiếp dễ dàng: `text.subSequence(0, cut)` và `text.subSequence(cut, text.length())` phủ toàn bộ dãy nếu `cut` hợp lệ, không lặp và không bỏ vị trí nào.

### Ví dụ 1: kiểm tra các ranh giới

Tạo tệp `SubSequenceBoundsDemo.java`:

```java
public class SubSequenceBoundsDemo {
    public static void main(String[] args) {
        CharSequence text = "ABCDE";

        CharSequence middle = text.subSequence(1, 4);
        CharSequence empty = text.subSequence(5, 5);

        System.out.println(middle);
        System.out.println(middle.length());
        System.out.println(empty.isEmpty());
        System.out.println("[" + empty + "]");

        try {
            text.subSequence(4, 2);
        } catch (IndexOutOfBoundsException exception) {
            System.out.println("Invalid range");
        }
    }
}
```

Chạy trên JDK 17 trở lên:

```bash
javac SubSequenceBoundsDemo.java
java SubSequenceBoundsDemo
```

Kết quả:

```text
BCD
3
true
[]
Invalid range
```

Không nên phụ thuộc vào chính xác tên lớp ngoại lệ con hay thông điệp lỗi do `String`, `CharBuffer` hoặc implementation khác tạo ra. Nếu cần tự kiểm tra dữ liệu đầu vào để báo lỗi riêng, điều kiện `0 <= start && start <= end && end <= text.length()` là điều kiện cốt lõi.

## 3. Với `String`, `subSequence` và `substring` liên quan thế nào?

`String` triển khai `CharSequence`. Với một `String`, hai phương thức:

```java
text.substring(start, end)
text.subSequence(start, end)
```

trả về cùng **nội dung** đoạn chuỗi. API `String.subSequence` được quy định hoạt động như `String.substring`; trong Java 17, kết quả thực tế của `subSequence` trên `String` là một đối tượng kiểu `String`, dù chữ ký khai báo trả về `CharSequence`.

Điểm khác dễ nhìn thấy nhất ở nơi gọi là **kiểu trả về tại thời điểm biên dịch**:

- `String.substring(int, int)` khai báo trả về `String`; có thể gọi tiếp `String` API trực tiếp.
- `String.subSequence(int, int)` khai báo trả về `CharSequence`; chỉ những thành viên của `CharSequence` và `Object` được nhìn thấy qua kiểu này, trừ khi chuyển đổi kiểu phù hợp.

### Ví dụ 2: nội dung bằng nhau nhưng kiểu khai báo khác nhau

Tạo `StringSubSequenceDemo.java`:

```java
public class StringSubSequenceDemo {
    public static void main(String[] args) {
        String source = "Programming";

        String bySubstring = source.substring(3, 7);
        CharSequence bySubSequence = source.subSequence(3, 7);

        System.out.println(bySubstring);
        System.out.println(bySubSequence);
        System.out.println(bySubstring.equals(bySubSequence));
        System.out.println(bySubSequence instanceof String);

        String converted = bySubSequence.toString();
        System.out.println(converted.toUpperCase());
    }
}
```

```bash
javac StringSubSequenceDemo.java
java StringSubSequenceDemo
```

Kết quả:

```text
gram
gram
true
true
GRAM
```

Từng bước:

1. `substring(3, 7)` đọc các chỉ số `3, 4, 5, 6` của `Programming`, cho ra `"gram"`.
2. `subSequence(3, 7)` trên chính `String` cho ra dãy tương đương.
3. `bySubstring.equals(bySubSequence)` trả về `true` vì đối tượng thực tế ở phía `bySubSequence` là `String` có cùng nội dung trong ví dụ này.
4. `instanceof String` đúng **đối với implementation `String` này**. Nó không phải một bảo đảm cho mọi kết quả `CharSequence.subSequence`.
5. `toString()` cung cấp giá trị `String` nếu nơi sử dụng cần API như `toUpperCase`.

Dùng `substring` thường trực quan hơn khi biết chắc mình đang thao tác trên `String` và cần một `String`. Dùng `subSequence` khi thiết kế API tổng quát có ý nghĩa trên các lớp triển khai `CharSequence`.

## 4. Kết quả có phải bản sao, view hay bất biến không?

**Không có một câu trả lời chung cho mọi `CharSequence`.** Đây là điểm quan trọng của hợp đồng giao diện.

Một số triển khai tạo chuỗi mới, trong khi triển khai khác có thể trả về một đối tượng biểu diễn **vùng nhìn (view)** trên dữ liệu có thể thay đổi. Do vậy, không được suy luận «đã gọi `subSequence` thì dữ liệu chắc chắn tách khỏi nguồn» hoặc «đoạn con luôn phản ánh mọi thay đổi của nguồn».

Ví dụ về hành vi cụ thể:

| Đối tượng nguồn | Hành vi đáng biết trong Java 17 | Có thể coi kết quả là bản sao `String` độc lập? |
| --- | --- | --- |
| `String` | `subSequence` tương đương `substring` về nội dung, trả về `String` bất biến | Có thể dựa vào nội dung bất biến; không dựa vào identity hoặc cấu trúc bộ nhớ nội bộ |
| `StringBuilder` | Implementation chuẩn trả về kết quả giống `substring`, là `String` | Có đối với implementation này; đừng áp đặt quy tắc đó cho mọi `CharSequence` |
| `StringBuffer` | Implementation chuẩn trả về `String` từ đoạn được chỉ định | Có đối với implementation này |
| `CharBuffer` | `subSequence` tạo buffer mới chia sẻ nội dung với buffer nguồn (view theo vùng tương ứng) | **Không**; nguồn/buffer dùng chung có thể bị sửa |
| `CharSequence` tự cài đặt | Do implementation quyết định trong giới hạn hợp đồng | Không được giả định nếu chưa biết hợp đồng cụ thể |

**Cẩn thận với tính bất biến:** dù giao diện có chữ `CharSequence`, nó **không đồng nghĩa với `String`**. Tham chiếu có kiểu `CharSequence` có thể trỏ tới một đối tượng mutable.

### Ví dụ 3: `StringBuilder` và `CharBuffer` có thể cho trải nghiệm khác nhau

Tạo `SubSequenceImplementationsDemo.java`:

```java
import java.nio.CharBuffer;

public class SubSequenceImplementationsDemo {
    public static void main(String[] args) {
        StringBuilder builder = new StringBuilder("ABCDE");
        CharSequence builderSlice = builder.subSequence(1, 4);

        builder.setCharAt(2, 'x');
        System.out.println(builder);
        System.out.println(builderSlice);

        char[] chars = {'A', 'B', 'C', 'D', 'E'};
        CharBuffer buffer = CharBuffer.wrap(chars);
        CharSequence bufferSlice = buffer.subSequence(1, 4);

        chars[2] = 'x';
        System.out.println(buffer);
        System.out.println(bufferSlice);
    }
}
```

```bash
javac SubSequenceImplementationsDemo.java
java SubSequenceImplementationsDemo
```

Kết quả trên JDK 17:

```text
ABxDE
BCD
ABxDE
BxD
```

Với `StringBuilder`, đoạn `builderSlice` là kết quả `String` của lời gọi `subSequence` trước khi builder bị sửa, nên vẫn hiện `BCD`. Với `CharBuffer.wrap(chars)`, buffer giữ vùng dữ liệu dùng chung với `chars`; đoạn `bufferSlice` là một buffer khác nhìn vào một phần vùng dữ liệu ấy. Khi `chars[2]` đổi từ `'C'` thành `'x'`, view cũng thấy `BxD`.

Đừng khái quát kết quả «`builderSlice` giữ nguyên» thành quy tắc bất biến cho toàn giao diện. Cũng đừng khái quát kết quả «`bufferSlice` thay đổi» thành quy tắc mọi `subSequence` đều trả về view.

**Nếu cần một văn bản bất biến ổn định**, chuyển đổi đoạn cần dùng thành `String` ở ranh giới phù hợp:

```java
static String stableSlice(CharSequence source, int start, int end) {
    return source.subSequence(start, end).toString();
}
```

Đây là **đoạn minh họa phương thức**, không phải chương trình độc lập. Với `StringBuilder` hoặc `CharBuffer` chuẩn, `toString()` trả về giá trị `String` biểu diễn nội dung tại thời điểm chuyển đổi. Nếu nguồn có thể bị các luồng khác sửa đồng thời, việc tạo snapshot nhất quán cần cơ chế đồng bộ phù hợp với nguồn; giao diện `CharSequence` không tự bảo đảm an toàn luồng.

## 5. Chỉ số `CharSequence` là UTF-16 code unit

Hợp đồng `CharSequence` mô tả dãy `char`, tức các UTF-16 code unit. `subSequence(start, end)` có thể tạo đoạn chứa surrogate lẻ nếu một biên cắt giữa hai nửa của code point ngoài BMP. Ví dụ, `"A😀B".subSequence(1, 2)` chỉ chứa high surrogate, còn `subSequence(1, 3)` chứa đủ cặp.

Khác với `String`, giao diện `CharSequence` **không cung cấp trực tiếp** `offsetByCodePoints`. Nếu một thuật toán cần ranh giới code point trên đầu vào tổng quát, có thể chuyển nội dung sang `String` rồi dùng API code point của `String`; với nguồn mutable, xác định rõ lúc lấy snapshot và cách đồng bộ khi có truy cập đồng thời. Ranh giới code point vẫn chưa chắc là ranh giới cụm ký tự hiển thị.

## 6. `CharSequence` không tự quy định cách so sánh nội dung

Một sai lầm khác là dùng `equals` của hai đối tượng bất kỳ triển khai `CharSequence` rồi mặc định đây là phép so sánh dãy ký tự. Giao diện `CharSequence` **không đặt ra một hợp đồng `equals` chung dựa trên nội dung** cho mọi implementation.

Ví dụ `String` ghi đè `equals` để so sánh với **một `String` khác** có cùng nội dung. Trong khi đó, `StringBuilder` dùng `equals` theo identity (kế thừa từ `Object`).

### Ví dụ 5: cùng nội dung chưa chắc `equals` bằng nhau

Tạo `CharSequenceEqualityDemo.java`:

```java
public class CharSequenceEqualityDemo {
    public static void main(String[] args) {
        CharSequence a = "abc";
        CharSequence b = new StringBuilder("abc");

        System.out.println(a.toString().equals(b.toString()));
        System.out.println(a.equals(b));
        System.out.println("abc".contentEquals(b));
        System.out.println(a.length() == b.length());
    }
}
```

```bash
javac CharSequenceEqualityDemo.java
java CharSequenceEqualityDemo
```

Kết quả:

```text
true
false
true
true
```

Hai lần `toString()` tạo ra giá trị `String` có cùng nội dung nên `equals` trả `true`. Nhưng `a.equals(b)` là lời gọi tới `String.equals`, và `b` là `StringBuilder`, nên nhận `false`. `String.contentEquals(CharSequence)` dùng để so sánh nội dung với một `CharSequence`, trả về `true` trong ví dụ.

Chỉ kiểm tra `length()` bằng nhau thì **chưa đủ**: hai dãy có cùng độ dài vẫn có thể chứa ký tự khác nhau. Nếu cần so sánh nội dung của hai `CharSequence` tổng quát, có thể dùng `CharSequence.compare(a, b) == 0` trên Java 17 (so sánh từ điển theo UTF-16 code unit), hoặc chuyển sang `String` và so sánh nội dung khi phù hợp với hiệu năng và mục đích. Cần quy định xử lý `null` riêng.

## 7. Thiết kế API khi muốn nhận `CharSequence`

Giả sử cần lấy mã ngắn từ một đầu vào văn bản. Nếu chỉ đọc chiều dài, lấy ký tự và trích đoạn, chữ ký này đủ tổng quát:

```java
static String extractCode(CharSequence text, int start, int end) {
    if (text == null) {
        throw new IllegalArgumentException("text must not be null");
    }
    if (start < 0 || end < start || end > text.length()) {
        throw new IndexOutOfBoundsException("Invalid range");
    }
    return text.subSequence(start, end).toString();
}
```

Đây là **phương thức minh họa** có thể đặt trong lớp Java. Các quyết định thiết kế:

1. Nhận `CharSequence` để cho phép `String`, `StringBuilder` và các implementation tương thích.
2. Kiểm tra đầu vào và khoảng cắt có chủ đích; thông điệp lỗi có thể được tùy chỉnh theo API của ứng dụng.
3. Trả về `String` nếu hợp đồng của phương thức hứa một **giá trị văn bản bất biến** thay vì một đoạn `CharSequence` có thể phản ánh thay đổi ở dữ liệu nguồn.
4. Không suy luận rằng implementation của tham số luôn là `String`. Ép kiểu `(String) text.subSequence(...)` có thể ném `ClassCastException` với implementation trả về `CharBuffer` hoặc một loại `CharSequence` khác.

Nếu API chủ đích giữ một **view** để tránh sao chép, hãy mô tả rõ thời hạn sử dụng, trách nhiệm sở hữu dữ liệu, khả năng thấy thay đổi, và an toàn luồng theo implementation cụ thể. Không hứa hiệu năng O(1), không cấp phát, hoặc tách bộ nhớ cho `subSequence` khi kiểu nguồn chưa được xác định: hợp đồng giao diện không áp đặt các chi tiết đó.

### Ví dụ 6: một phương thức làm việc với nhiều implementation

Tạo tệp `GenericSubSequenceDemo.java`:

```java
import java.nio.CharBuffer;

public class GenericSubSequenceDemo {
    static String extractCode(CharSequence text, int start, int end) {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        if (start < 0 || end < start || end > text.length()) {
            throw new IndexOutOfBoundsException("Invalid range");
        }
        return text.subSequence(start, end).toString();
    }

    public static void main(String[] args) {
        String text = "ID-4567";
        StringBuilder builder = new StringBuilder("ID-4567");
        CharBuffer buffer = CharBuffer.wrap("ID-4567".toCharArray());

        System.out.println(extractCode(text, 3, 7));
        System.out.println(extractCode(builder, 3, 7));
        System.out.println(extractCode(buffer, 3, 7));
    }
}
```

```bash
javac GenericSubSequenceDemo.java
java GenericSubSequenceDemo
```

Kết quả:

```text
4567
4567
4567
```

Cùng hợp đồng `CharSequence.subSequence` cho ra dãy ký tự đúng từ các loại đầu vào khác nhau. Việc **chuyển đoạn con sang `String`** ở bước trả kết quả tạo ra cách sử dụng thống nhất, không buộc phía gọi phải quan tâm lớp hiện thực cụ thể của `subSequence`.

## 8. Lỗi thường gặp và cách tránh

**Lỗi 1 — tưởng `end` được lấy:** `subSequence(1, 3)` lấy chỉ số `1` và `2`; không lấy `3`. Kiểm tra bằng độ dài `end - start`.

**Lỗi 2 — cắt quá giới hạn:** `subSequence(0, text.length() + 1)` không hợp lệ. Nếu vị trí được tính từ dữ liệu ngoài, hãy xác thực điều kiện ranh giới và xử lý ngoại lệ có mục đích.

**Lỗi 3 — giả định mọi kết quả là `String`:** phương thức có kiểu trả về `CharSequence`, và implementation khác nhau có thể trả về các lớp khác nhau. Khi thật sự cần `String`, sử dụng `toString()` theo hợp đồng; không ép kiểu mù.

**Lỗi 4 — giả định mọi kết quả là view:** `StringBuilder` chuẩn có thể trả về `String` snapshot. Không dựa vào thay đổi của nguồn sẽ phản ánh trên đoạn con nếu hợp đồng implementation không nói rõ.

**Lỗi 5 — giả định mọi kết quả bất biến:** `CharBuffer` có thể trả về view chia sẻ dữ liệu mutable. Nếu cần giữ văn bản bất biến, chuyển đổi thành `String` và xác định chính sách đồng bộ/ownership phù hợp.

**Lỗi 6 — nhầm độ dài với Unicode code point:** `length()` và chỉ số của `subSequence` theo UTF-16 code unit. Khi xử lý emoji hoặc ký tự ngoài BMP, hãy tính ranh giới code point cẩn thận; grapheme cluster còn có thể phức tạp hơn.

**Lỗi 7 — dùng `equals` giữa các kiểu `CharSequence` để kết luận hai dãy bằng nhau:** không có hợp đồng equality chung. Dùng `String.contentEquals`, `CharSequence.compare` hay chuyển sang `String` khi phù hợp.

**Lỗi 8 — hiểu `subSequence` tự bảo đảm thread-safe:** giao diện không cung cấp bảo đảm đó. Nếu dữ liệu nguồn mutable được sửa bởi luồng khác, phải dựa vào cơ chế đồng bộ và tính chất cụ thể của implementation.

**Lỗi 9 — dùng `subSequence` thay cho `substring` chỉ vì tên «tổng quát hơn»:** khi kiểu nguồn đã là `String` và cần `String`, `substring` thường trực tiếp và rõ hơn; `subSequence` hữu ích khi muốn lập trình theo giao diện.

## 9. Tự kiểm tra

### Bài 1 — ranh giới nửa mở

Cho `CharSequence s = "spring";`. Dự đoán:

```java
System.out.println(s.subSequence(0, 3));
System.out.println(s.subSequence(3, 6));
System.out.println(s.subSequence(6, 6).isEmpty());
```

Kết quả:

```text
spr
ing
true
```

Chỉ số `end` không được lấy; hai đoạn `[0,3)` và `[3,6)` ghép lại đúng `spring`. Lấy đoạn rỗng ở vị trí cuối là hợp lệ.

### Bài 2 — nội dung có ổn định không?

Với Java 17:

```java
StringBuilder builder = new StringBuilder("hello");
CharSequence sub = builder.subSequence(1, 4);
builder.replace(1, 4, "XYZ");

System.out.println(builder);
System.out.println(sub);
```

Kết quả:

```text
hXYZo
ell
```

Trong implementation `StringBuilder` chuẩn, `subSequence` tạo kết quả `String` của đoạn `"ell"` ở thời điểm gọi. Đừng áp dụng giả định snapshot này cho mọi implementation của `CharSequence`.

### Bài 3 — code point và code unit

Cho:

```java
String text = "A\uD83D\uDE00B";
System.out.println(text.length());
System.out.println(text.subSequence(1, 3).length());
```

Kết quả:

```text
4
2
```

Đoạn `subSequence(1, 3)` chứa một code point U+1F600 nhưng hai UTF-16 code unit.

### Bài 4 — thiết kế chữ ký

Một phương thức cần chấp nhận `String`, `StringBuilder` và `CharBuffer`, đồng thời trả về một giá trị văn bản bất biến. Nên khai báo gì?

**Gợi ý:** tham số `CharSequence source`; kết quả `String`; dùng `source.subSequence(start, end).toString()` sau khi kiểm tra chỉ số. Nếu đầu vào có thể bị sửa đồng thời, phải xác định thêm cách đồng bộ và thời điểm lấy dữ liệu.

## Kết luận

**`subSequence(start, end)` là hợp đồng đọc một đoạn theo chỉ số UTF-16 nửa mở `[start, end)` trên giao diện `CharSequence`.** Hợp đồng cho biết đoạn con cần chứa những ký tự nào, nhưng không bảo đảm lớp kết quả, snapshot hay view, tính bất biến hoặc chi phí thực hiện. Với `String`, nó tương đương `substring` về nội dung; với các implementation khác, cần dựa vào đặc điểm cụ thể. Thiết kế API đúng phải phân biệt **nội dung đoạn con**, **kiểu trả về**, **quyền sở hữu dữ liệu** và **khả năng thay đổi của nguồn**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-extraction-005">Trường hợp biên âm, vượt độ dài hoặc đảo thứ tự</a>

<details>
<summary>Xem chi tiết</summary>

# Trường hợp biên âm, vượt độ dài hoặc đảo thứ tự khi trích xuất chuỗi

Khi trích xuất một phần nội dung bằng `String.substring(...)` trong Java, lỗi dễ gặp nhất không nằm ở ký tự cần lấy mà ở **phạm vi chỉ số**: vị trí bắt đầu âm, vị trí kết thúc vượt quá độ dài hoặc hai vị trí bị đảo ngược. Những trường hợp này cần được phân biệt với các phạm vi **hợp lệ nhưng rỗng**, cũng như phạm vi hợp lệ theo UTF-16 nhưng cắt giữa một ký tự Unicode.

Bài học tập trung vào quy tắc biên của `substring(beginIndex)`, `substring(beginIndex, endIndex)` và `subSequence(start, end)`, cách đọc lỗi, cách kiểm tra dữ liệu đầu vào và những tình huống thực tế như lấy hậu tố, dùng kết quả `indexOf()` hoặc trích xuất văn bản có emoji.

## 1. Quy ước chỉ số cần giữ trước khi kiểm tra lỗi

`substring(begin, end)` lấy khoảng nửa mở `[begin, end)` tính theo UTF-16 code unit: lấy `begin`, bỏ `end`, và có độ dài `end - begin` khi hợp lệ. Với nguồn dài `n`, các **biên** hợp lệ có thể nhận giá trị từ `0` đến `n`, dù chỉ số ký tự cuối là `n - 1`.

```java
String s = "Java";
System.out.println(s.substring(1, 3)); // av
System.out.println(s.substring(4, 4).isEmpty()); // true
```

Trọng tâm xử lý lỗi là phân biệt **biên hợp lệ nhưng rỗng** với biên âm, vượt độ dài và đảo thứ tự. `substring(begin)` tương đương về nội dung với `substring(begin, s.length())` khi hợp lệ.

## 2. Ba điều kiện bắt buộc của phạm vi `[begin, end)`

Công thức hợp lệ cho `text.substring(begin, end)` là:

```text
0 <= begin <= end <= text.length()
```

Chỉ cần **một** trong các điều kiện sau xảy ra, phạm vi đã sai:

1. **Chỉ số âm:** `begin < 0`, hoặc `end < 0`. Ví dụ `substring(-1, 2)`, `substring(0, -1)`.
2. **Vượt độ dài:** `end > length()` hoặc `begin > length()`. Ví dụ chuỗi dài 4 nhưng yêu cầu `substring(0, 5)`, `substring(5)`.
3. **Đảo thứ tự:** `begin > end`, kể cả khi cả hai đều nằm trong khoảng từ 0 đến `length()`. Ví dụ `substring(3, 1)`.

Java báo lỗi **`IndexOutOfBoundsException`** khi vi phạm hợp đồng chỉ số của `substring`. Chi tiết thông báo ngoại lệ có thể khác nhau theo phiên bản JDK; không nên viết logic nghiệp vụ dựa vào chuỗi thông báo đó. Ngoại lệ xảy ra **tại lời gọi trích xuất**, trước khi nhận được bất kỳ chuỗi kết quả nào.

Để kiểm tra nhanh, có thể dùng biểu thức:

```java
boolean valid = begin >= 0
        && begin <= end
        && end <= text.length();
```

Đây là **đoạn mã minh họa**, giả sử `text != null`. Nếu cả ba mệnh đề đều đúng thì `begin` và `end` đều nằm trong ranh giới hợp lệ; không cần thêm điều kiện `begin <= text.length()` vì nó đã suy ra từ `begin <= end <= text.length()`. Nếu `text == null` thì truy cập `text.length()` sẽ ném `NullPointerException`, một lỗi khác lỗi chỉ số.

### Không đồng nhất phạm vi sai với phạm vi rỗng

- `begin == end` là **hợp lệ**, cho kết quả `""`.
- `begin > end` là **không hợp lệ**, Java ném ngoại lệ.
- Chuỗi nguồn `""` có độ dài 0 nên chỉ có một phạm vi hợp lệ: `substring(0, 0)`; riêng phiên bản một tham số thì `substring(0)`.
- `substring(0, length())` luôn là một phạm vi hợp lệ nếu chuỗi nguồn không null.
- `substring(length(), length())` cũng luôn hợp lệ; nhiều thuật toán cần trường hợp này để mô tả “phần đuôi không có ký tự”.

## 3. Ví dụ Java 17+ kiểm tra từng trường hợp biên

Lưu tệp sau thành **`SubstringBoundsDemo.java`**:

```java
public class SubstringBoundsDemo {
    private static void test(String source, int begin, int end) {
        try {
            String part = source.substring(begin, end);
            System.out.println("[" + begin + "," + end + ") -> <"
                    + part + "> (length=" + part.length() + ")");
        } catch (IndexOutOfBoundsException ex) {
            System.out.println("[" + begin + "," + end + ") -> INVALID");
        }
    }

    public static void main(String[] args) {
        String value = "Java";
        System.out.println("source length: " + value.length());

        test(value, 0, 4);
        test(value, 1, 3);
        test(value, 2, 2);
        test(value, 4, 4);

        test(value, -1, 2);
        test(value, 0, -1);
        test(value, 0, 5);
        test(value, 5, 5);
        test(value, 3, 2);

        test("", 0, 0);
    }
}
```

Biên dịch và chạy tại thư mục chứa tệp:

```bash
javac SubstringBoundsDemo.java
java SubstringBoundsDemo
```

**Stdout:**

```text
source length: 4
[0,4) -> <Java> (length=4)
[1,3) -> <av> (length=2)
[2,2) -> <> (length=0)
[4,4) -> <> (length=0)
[-1,2) -> INVALID
[0,-1) -> INVALID
[0,5) -> INVALID
[5,5) -> INVALID
[3,2) -> INVALID
[0,0) -> <> (length=0)
```

Trong ví dụ, chỉ bắt `IndexOutOfBoundsException` để **minh họa phân loại** các trường hợp. Trong ứng dụng thật, tốt hơn nên kiểm tra tham số hoặc xác định chính sách lỗi trước khi gọi `substring()`; bắt rồi bỏ qua mọi ngoại lệ có thể che mất lỗi lập trình.

## 4. So sánh miền chỉ số để tránh dùng sai API

| API | Điều kiện hợp lệ | Điểm cần kiểm tra |
| --- | --- | --- |
| `s.substring(begin)` | `0 <= begin <= n` | `begin == n` cho chuỗi rỗng |
| `s.substring(begin, end)` | `0 <= begin <= end <= n` | Thứ tự hai biên phải đúng |
| `s.subSequence(begin, end)` | `0 <= begin <= end <= n` | Trả kiểu khai báo `CharSequence` |
| `s.charAt(index)` | `0 <= index < n` | `charAt(n)` không hợp lệ |

Cùng một offset `n` có thể hợp lệ cho **ranh giới** của `substring` nhưng không hợp lệ cho `charAt`. Tương tự, `substring(k, k)` không đọc code unit ở `k`; nó trả đoạn rỗng, còn `substring(k, k + 1)` chỉ lấy một code unit nếu `k < n`. Các phương thức không tự điều chỉnh chỉ số sai.

## 5. Lỗi off-by-one: `end` bị loại trừ

Lỗi lệch một đơn vị (*off-by-one*) thường xảy ra vì nhầm `end` là vị trí ký tự cuối **được lấy**. Ví dụ muốn lấy ký tự ở vị trí 1, 2 và 3 của `"Java"`:

```java
"Java".substring(1, 3); // "av": chỉ lấy vị trí 1 và 2
"Java".substring(1, 4); // "ava": lấy vị trí 1, 2, 3
```

Mọi biểu thức trên là đoạn mã minh họa.

Nếu chỉ số kết thúc **bao gồm** theo yêu cầu bên ngoài là `lastInclusive`, phép trích xuất có thể dùng `substring(begin, lastInclusive + 1)` **chỉ sau khi** đã xác minh `lastInclusive` là chỉ số phần tử có thật, tức `0 <= lastInclusive < length`, và `begin <= lastInclusive`. Không nên tự cộng 1 cho một giá trị bất kỳ vì có thể vượt `length()` hoặc gây tràn số nguyên với giá trị rất lớn.

Một kiểu yêu cầu khác là “lấy **count** code unit từ vị trí **start**”. Dạng tự nhiên là `substring(start, start + count)`, nhưng cần bảo vệ:

- `start >= 0`;
- `count >= 0`;
- `start <= length()`;
- `count <= length() - start`.

Cách kiểm tra bằng `count <= length() - start` tránh phải tính `start + count` trước khi bảo đảm không tràn số nguyên. Chỉ tính tổng sau khi đã xác thực.

## 6. Kiểm tra đầu vào trước khi trích xuất

Có hai cách hợp lý tùy hợp đồng nghiệp vụ: **từ chối phạm vi sai** bằng ngoại lệ có thông điệp rõ ràng, hoặc **chấp nhận điều chỉnh phạm vi** nếu yêu cầu bài toán cho phép. Không nên âm thầm ép mọi chỉ số sai vào khoảng hợp lệ khi người gọi cần được biết dữ liệu đầu vào có lỗi.

### 6.1. Chính sách nghiêm ngặt: phạm vi sai phải báo lỗi

**`StrictSliceDemo.java`**

```java
import java.util.Objects;

public class StrictSliceDemo {
    private static String slice(String text, int begin, int end) {
        Objects.requireNonNull(text, "text");

        if (begin < 0 || end < begin || end > text.length()) {
            throw new IllegalArgumentException(
                    "Invalid slice: [" + begin + "," + end
                    + ") for length " + text.length());
        }

        return text.substring(begin, end);
    }

    public static void main(String[] args) {
        System.out.println("<" + slice("Java", 1, 3) + ">");
        System.out.println("<" + slice("Java", 4, 4) + ">");
        System.out.println("<" + slice("", 0, 0) + ">");

        try {
            slice("Java", 3, 2);
        } catch (IllegalArgumentException ex) {
            System.out.println("Rejected reversed bounds");
        }

        try {
            slice("Java", -1, 2);
        } catch (IllegalArgumentException ex) {
            System.out.println("Rejected negative begin");
        }
    }
}
```

```bash
javac StrictSliceDemo.java
java StrictSliceDemo
```

**Stdout:**

```text
<av>
<>
<>
Rejected reversed bounds
Rejected negative begin
```

Trong phương thức `slice`, điều kiện gộp đã đủ để chặn `begin` âm, `end` nằm trước `begin`, hoặc `end` vượt độ dài. Nếu `begin > length` thì không thể đồng thời thỏa `end >= begin` và `end <= length`, nên cũng bị chặn. Ở đây mã **chủ động** ném `IllegalArgumentException` cho tham số không đạt hợp đồng của hàm trợ giúp; đây không phải lời khẳng định `substring` tự ném ngoại lệ đó. Một API có thể lựa chọn loại ngoại lệ hoặc kiểu kết quả khác cho phù hợp thiết kế.

### 6.2. Chính sách giới hạn phạm vi: chỉ khi đề bài cho phép

Giả sử yêu cầu là “lấy tối đa `maxUnits` code unit đầu tiên”; nếu `maxUnits` lớn hơn độ dài thì chỉ lấy toàn bộ chuỗi, nhưng giá trị âm vẫn là đầu vào không hợp lệ:

**`PrefixLimitDemo.java`**

```java
import java.util.Objects;

public class PrefixLimitDemo {
    private static String prefixAtMost(String text, int maxUnits) {
        Objects.requireNonNull(text, "text");
        if (maxUnits < 0) {
            throw new IllegalArgumentException("maxUnits must be >= 0");
        }

        int end = Math.min(maxUnits, text.length());
        return text.substring(0, end);
    }

    public static void main(String[] args) {
        System.out.println("<" + prefixAtMost("Java", 2) + ">");
        System.out.println("<" + prefixAtMost("Java", 10) + ">");
        System.out.println("<" + prefixAtMost("Java", 0) + ">");
        System.out.println("<" + prefixAtMost("", 3) + ">");
    }
}
```

```bash
javac PrefixLimitDemo.java
java PrefixLimitDemo
```

**Stdout:**

```text
<Ja>
<Java>
<>
<>
```

`Math.min(maxUnits, text.length())` phù hợp vì tên và hợp đồng của hàm nói rõ **“tối đa”**. Nếu người gọi yêu cầu **chính xác 10 ký tự** mà chuỗi chỉ dài 4, cắt còn 4 một cách lặng lẽ có thể che lỗi dữ liệu. Không nên đảo `begin/end`, lấy trị tuyệt đối chỉ số âm hoặc tự ý đưa mọi phạm vi về giới hạn mà không được phép theo yêu cầu nghiệp vụ.

## 7. Trường hợp âm xuất hiện từ phép tính và API tìm kiếm

### 7.1. Lấy `n` ký tự cuối khi `n` lớn hơn độ dài

Mẫu quen thuộc:

```java
String tail = text.substring(text.length() - n);
```

Chỉ đúng khi `0 <= n <= text.length()`. Nếu `text = "Java"` và `n = 5`, điểm bắt đầu là `4 - 5 = -1`, gây ngoại lệ. Nếu `n = -1`, điểm bắt đầu là `5`, cũng vượt chiều dài. Với `n = 0`, kết quả hợp lệ là `""`.

Bản có kiểm tra nghiêm ngặt:

**`SuffixBoundsDemo.java`**

```java
import java.util.Objects;

public class SuffixBoundsDemo {
    private static String lastUnits(String text, int n) {
        Objects.requireNonNull(text, "text");
        if (n < 0 || n > text.length()) {
            throw new IllegalArgumentException("Invalid suffix length: " + n);
        }
        return text.substring(text.length() - n);
    }

    public static void main(String[] args) {
        System.out.println("<" + lastUnits("Java", 2) + ">");
        System.out.println("<" + lastUnits("Java", 4) + ">");
        System.out.println("<" + lastUnits("Java", 0) + ">");

        try {
            lastUnits("Java", 5);
        } catch (IllegalArgumentException ex) {
            System.out.println("Rejected suffix longer than source");
        }
    }
}
```

```bash
javac SuffixBoundsDemo.java
java SuffixBoundsDemo
```

**Stdout:**

```text
<va>
<Java>
<>
Rejected suffix longer than source
```

Tên `lastUnits` nói rõ `n` là **số code unit UTF-16**, chưa chắc là số ký tự mà người dùng nhìn thấy; ở phần Unicode bên dưới sẽ chỉ ra giới hạn này.

### 7.2. `indexOf()` trả `-1` khi không tìm thấy

Nhiều lỗi biên âm xuất phát từ việc lấy kết quả `indexOf()` rồi đưa thẳng vào `substring()`:

```java
String line = "name=value";
int separator = line.indexOf(':'); // -1 vì không có dấu ':'
String head = line.substring(0, separator); // sai: end = -1
```

Đây là đoạn mã minh họa cố tình sai, không phải chương trình hoàn chỉnh. `-1` là **giá trị báo không tìm thấy**, không phải một chỉ số hợp lệ. Hãy xử lý trước:

**`DelimiterDemo.java`**

```java
public class DelimiterDemo {
    private static void showAfterColon(String line) {
        int separator = line.indexOf(':');
        if (separator < 0) {
            System.out.println("No delimiter");
            return;
        }

        String value = line.substring(separator + 1);
        System.out.println("Value=<" + value + ">");
    }

    public static void main(String[] args) {
        showAfterColon("user:admin");
        showAfterColon("user:");
        showAfterColon("user=admin");
        showAfterColon(":start");
    }
}
```

```bash
javac DelimiterDemo.java
java DelimiterDemo
```

**Stdout:**

```text
Value=<admin>
Value=<>
No delimiter
Value=<start>
```

Khi dấu phân cách nằm ở cuối, `separator + 1 == length()` vẫn là ranh giới hợp lệ, cho chuỗi rỗng. Khi ở đầu, `substring(1)` hợp lệ nếu chuỗi có một hoặc nhiều code unit. Chỉ khi không tìm thấy mới cần trả trạng thái khác hoặc báo lỗi theo yêu cầu ứng dụng.

## 8. Biên số học đúng chưa chắc là biên Unicode đúng

Kiểm tra `0 <= begin <= end <= length` chỉ bảo vệ phạm vi UTF-16. Với `"A😊B"`, `length() == 4` và `substring(1, 2)` hoàn toàn **hợp lệ về chỉ số**, nhưng chỉ lấy high surrogate của emoji. Nếu muốn lấy trọn một code point tại vị trí UTF-16 `1`, có thể viết:

```java
String text = "A😊B";
int end = text.offsetByCodePoints(1, 1);
String emoji = text.substring(1, end); // lấy đúng emoji
```

Khi xác thực đầu vào, hãy tách câu hỏi **“offset có nằm trong phạm vi hợp lệ?”** khỏi câu hỏi **“offset có chia đôi code point hoặc grapheme cluster không?”**. API code point xử lý surrogate pair; phân đoạn grapheme giải quyết thêm dấu kết hợp và emoji nhiều code point.

## 9. Cách thiết kế và kiểm thử các trường hợp biên

### 9.1. Xác định chính sách trước khi triển khai

Trước mỗi hàm trích xuất, nên trả lời:

1. Tham số là **offset UTF-16**, số code point hay số cụm ký tự hiển thị?
2. Vị trí kết thúc là **exclusive** hay người gọi đang cung cấp chỉ số cuối **inclusive**?
3. Nếu đầu vào âm, vượt độ dài hoặc đảo chiều, hàm sẽ **ném ngoại lệ**, trả kết quả thể hiện thất bại, hay cắt tới biên gần nhất?
4. Chuỗi rỗng và phạm vi `begin == end` có hợp lệ theo yêu cầu hay không?
5. Nếu ranh giới số học đúng nhưng cắt nửa surrogate/grapheme, hàm có phải từ chối hay điều chỉnh ranh giới không?

Cùng một `substring` có thể được dùng cho API nghiêm ngặt hay API “lấy tối đa”; chính sách đầu vào do hàm bao ngoài quyết định. Nếu văn bản đến từ người dùng hay hệ thống ngoài, cần tránh biến một chỉ số không tin cậy thành ngoại lệ không được xử lý. Nếu chỉ số đến từ mã nội bộ mà đáng lẽ luôn đúng, ngoại lệ có thể là tín hiệu phát hiện lỗi lập trình hữu ích.

### 9.2. Ma trận kiểm thử nhanh

Với `text = "Java"`, `n = 4`, các ca kiểm tra thiết yếu gồm:

| Trường hợp | Input | Kết quả mong đợi |
|---|---|---|
| Toàn bộ chuỗi | `[0, 4)` | `"Java"` |
| Đoạn giữa | `[1, 3)` | `"av"` |
| Phạm vi rỗng giữa chuỗi | `[2, 2)` | `""` |
| Phạm vi rỗng tại cuối | `[4, 4)` | `""` |
| Chỉ số âm ở đầu | `[-1, 2)` | Ngoại lệ |
| Chỉ số âm ở cuối | `[0, -1)` | Ngoại lệ |
| Vượt chiều dài ở cuối | `[0, 5)` | Ngoại lệ |
| Cả hai vượt chiều dài | `[5, 5)` | Ngoại lệ |
| Đảo thứ tự | `[3, 2)` | Ngoại lệ |
| Chuỗi rỗng | `""` và `[0, 0)` | `""` |
| Chuỗi rỗng, index 1 | `""` và `[0, 1)` | Ngoại lệ |
| Ký tự ngoài BMP | `"A😊B"`, `[1, 2)` | Phạm vi hợp lệ, nhưng cắt đôi surrogate |

Các kiểm thử này kiểm tra **ranh giới**, **trường hợp rỗng**, **độ dài**, và **tính đúng đắn Unicode** riêng biệt. Đừng coi việc `substring()` không ném ngoại lệ là bằng chứng phần cắt phù hợp với ý nghĩa “ký tự” của sản phẩm.

### 9.3. Bẫy hay gặp khi sửa lỗi

- **Tự đảo tham số** nếu `begin > end`: không thể biết người gọi gõ nhầm hay cố ý truyền phạm vi đảo; sửa âm thầm có thể thay đổi ý nghĩa kết quả.
- **Đổi chỉ số âm thành 0** khi lỗi đến từ `indexOf() == -1`: điều đó có thể khiến chương trình trích dữ liệu sai thay vì nhận ra dấu phân cách không tồn tại.
- **Cắt tối đa `length`** trong hàm yêu cầu chính xác số ký tự: dữ liệu thiếu sẽ bị che giấu. Chỉ clamp khi hợp đồng hàm cho phép.
- **Dùng `charAt(length)` vì `substring(length)` hợp lệ**: hai API có miền chỉ số khác nhau.
- **Quên kết thúc loại trừ**: `substring(0, length - 1)` bỏ ký tự cuối; `substring(0, length + 1)` vượt biên.
- **Dùng `length()` làm số “ký tự hiển thị”**: chuỗi có surrogate pair hoặc dấu kết hợp làm phép tính lệch với cách người đọc cảm nhận.

## 10. Tự kiểm tra

Đoán kết quả hoặc trường hợp ném ngoại lệ khi `s = "ABCD"`:

```java
String s = "ABCD";

System.out.println("<" + s.substring(1, 1) + ">");
System.out.println("<" + s.substring(s.length()) + ">");
System.out.println("<" + s.substring(0, s.length()) + ">");

// Xét riêng từng dòng bên dưới:
// s.substring(-1, 2);
// s.substring(2, 5);
// s.substring(3, 2);
// s.substring(4, 4);
// s.charAt(4);
```

**Đáp án:**

- Ba lệnh `println` lần lượt in `<>`, `<>` và `<ABCD>`.
- `substring(-1, 2)` sai vì bắt đầu âm.
- `substring(2, 5)` sai vì `end = 5 > length = 4`.
- `substring(3, 2)` sai vì `begin > end`.
- `substring(4, 4)` hợp lệ, trả `""`.
- `charAt(4)` sai vì miền hợp lệ của `charAt` chỉ là `0..3`.

**Quy tắc nhớ nhanh:**

```text
substring(begin, end):
    0 <= begin <= end <= length

substring(begin):
    0 <= begin <= length

charAt(index):
    0 <= index < length
```

Cần nhớ thêm một bước: các bất đẳng thức trên bảo vệ **phạm vi UTF-16**; muốn trích đúng một code point hoặc cụm ký tự hiển thị, phải dùng ranh giới Unicode tương ứng. Khi ứng dụng phân biệt rõ chỉ số âm, chỉ số vượt độ dài, thứ tự đảo, phạm vi rỗng và giới hạn Unicode, các thao tác trích xuất trở nên dễ kiểm tra và khó phát sinh lỗi dữ liệu âm thầm.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-extraction-006">Cắt theo code point thay vì chia đôi surrogate pair</a>

<details>
<summary>Xem chi tiết</summary>

# Cắt chuỗi theo code point thay vì chia đôi surrogate pair trong Java

Khi trích xuất một phần chuỗi `String` chứa emoji, chữ viết hiếm hoặc ký tự Unicode ngoài Basic Multilingual Plane (BMP), cắt theo chỉ số `char` một cách tùy ý có thể tách đôi một **surrogate pair**. Kết quả vẫn là một đối tượng `String` hợp lệ ở cấp API Java nhưng chứa dãy UTF-16 không hoàn chỉnh, dễ bị hiển thị thành ký tự thay thế hoặc xử lý sai khi mã hóa.

Giải pháp là xác định ranh giới bằng **Unicode code point**, sau đó chuyển các vị trí code point thành **chỉ số UTF-16** phù hợp trước khi gọi `substring()`. Bài này tập trung vào cơ chế, cách cắt an toàn và giới hạn của cách tiếp cận đó.

## 1. Phân biệt `char`, UTF-16 code unit và Unicode code point

Java `String` cung cấp nhiều API đánh chỉ số bằng **UTF-16 code unit**, không phải bằng số ký tự mà người dùng nhìn thấy.

- **Unicode code point** là một giá trị từ `U+0000` đến `U+10FFFF`; các giá trị dành riêng cho surrogate (`U+D800`–`U+DFFF`) không phải *Unicode scalar value* hợp lệ để đại diện cho một ký tự độc lập.
- **UTF-16 code unit** là một đơn vị 16 bit. Giá trị Unicode thuộc BMP và không phải surrogate được mã hóa bằng một code unit. Giá trị nằm ngoài BMP được mã hóa bằng hai code unit, tạo thành một **surrogate pair**.
- **`char` trong Java** là kiểu giá trị 16 bit, biểu diễn **một code unit UTF-16**. Một `char` không đủ để giữ riêng một code point ngoài BMP.
- **`int`** thường được dùng khi làm việc với giá trị Unicode code point, ví dụ qua `String.codePointAt()`, `String.codePoints()` hoặc `Character.toChars()`.

Hai nửa của một surrogate pair thuộc các dải:

| Thành phần | Phạm vi |
| --- | --- |
| High surrogate (nửa đầu) | `U+D800`–`U+DBFF` |
| Low surrogate (nửa sau) | `U+DC00`–`U+DFFF` |
| Code point cần một cặp surrogate trong UTF-16 | `U+10000`–`U+10FFFF` |

Ví dụ emoji 😀 là code point `U+1F600`, được mã hóa UTF-16 bằng hai code unit `D83D DE00`. Vì vậy chuỗi `"A😀B"` có **4 code unit UTF-16** nhưng chỉ có **3 code point**: `A`, `😀`, `B`.

```text
Nội dung:          A         😀                B
Code point:      U+0041   U+1F600           U+0042
UTF-16 unit:     0041     D83D    DE00       0042
UTF-16 index:      0        1       2          3       (end = 4)
Code-point index:  0        1                  2       (end = 3)
```

Các vị trí của code point là **chỉ số logic theo code point**; `String.substring()` **không nhận trực tiếp** kiểu chỉ số đó.

## 2. Vì sao `length()`, `charAt()` và `substring()` có thể gây nhầm?

`String.length()` trả về số **code unit UTF-16**, còn `charAt(index)` trả về một `char` tại vị trí code unit tương ứng. `substring(beginIndex, endIndex)` cũng dùng hai chỉ số UTF-16, với `beginIndex` bao gồm và `endIndex` không bao gồm.

Với `"A😀B"`:

```java
String text = "A" + new String(Character.toChars(0x1F600)) + "B";

System.out.println(text.length());                           // 4
System.out.println(text.codePointCount(0, text.length()));    // 3
System.out.println(Character.isHighSurrogate(text.charAt(1))); // true
System.out.println(Character.isLowSurrogate(text.charAt(2)));  // true
```

Đây là **đoạn minh họa trong thân một phương thức**. `Character.toChars(0x1F600)` tạo đúng hai `char` cho emoji; không cần phụ thuộc vào khả năng nhập emoji trực tiếp trong tệp mã nguồn.

### Lỗi: coi chỉ số code point là chỉ số UTF-16

Giả sử muốn lấy **hai code point đầu** (`A` và `😀`), rồi viết:

```java
String unsafe = text.substring(0, 2); // SAI nếu mục tiêu là hai code point
```

`substring(0, 2)` chỉ lấy code unit ở index 0 và 1: ký tự `A` cộng **high surrogate** `D83D`. Nó loại bỏ low surrogate `DE00`, làm chuỗi kết quả chứa một surrogate đơn lẻ. Java không tự sửa lỗi này khi `substring()`.

Tương tự, muốn lấy chỉ emoji mà dùng `text.substring(1, 2)` thì chỉ thu được high surrogate; khoảng đúng theo UTF-16 phải là `[1, 3)`.

Không nên chứng minh lỗi bằng cách in trực tiếp surrogate đơn lẻ ra console: cách nó hiển thị còn tùy encoding và terminal. Thay vào đó, có thể kiểm tra:

```java
String unsafe = text.substring(0, 2);

System.out.println(unsafe.length()); // 2
System.out.println(Character.isHighSurrogate(
        unsafe.charAt(unsafe.length() - 1))); // true
```

## 3. API Java phục vụ việc cắt theo code point

| API | Vai trò | Hệ chỉ số |
| --- | --- | --- |
| `s.length()` | Đếm code unit UTF-16 | Số lượng code unit |
| `s.charAt(i)` | Lấy một code unit | `i` là UTF-16 index |
| `s.substring(start, end)` | Trích xuất khoảng ký tự | `start`, `end` là UTF-16 index |
| `s.codePointCount(start, end)` | Đếm code point trong một khoảng UTF-16 | Tham số là UTF-16 index |
| `s.offsetByCodePoints(index, offset)` | Di chuyển `offset` code point từ một UTF-16 index | Trả về UTF-16 index |
| `s.codePointAt(index)` | Đọc code point bắt đầu ở vị trí UTF-16 `index` | Tham số là UTF-16 index; trả về `int` |
| `Character.charCount(cp)` | Số UTF-16 code unit để biểu diễn một code point | Thường 1 hoặc 2 |
| `s.codePoints()` | Duyệt các giá trị code point của chuỗi qua `IntStream` | Không cung cấp trực tiếp UTF-16 index |
| `StringBuilder.appendCodePoint(cp)` | Thêm giá trị code point vào builder | Không cần tự ghép nửa surrogate |

Điểm đặc biệt hữu ích là `offsetByCodePoints`. Ví dụ:

```java
String text = "A" + new String(Character.toChars(0x1F600)) + "B";
int startUtf16 = text.offsetByCodePoints(0, 1);
int endUtf16 = text.offsetByCodePoints(0, 2);

System.out.println(startUtf16); // 1
System.out.println(endUtf16);   // 3
```

`startUtf16 = 1` đứng trước high surrogate, `endUtf16 = 3` đứng **sau** low surrogate. Do vậy `text.substring(1, 3)` trích xuất nguyên emoji và không phá cặp surrogate.

`offsetByCodePoints` có thể nhận `offset` âm để di chuyển lùi, nhưng vị trí và khoảng dịch chuyển phải hợp lệ; Java ném `IndexOutOfBoundsException` khi phạm vi vượt giới hạn.

## 4. Hàm cắt theo khoảng code point an toàn

Ta định nghĩa API rõ ràng: nhận `fromInclusive`, `toExclusive` theo **chỉ số code point tính từ đầu chuỗi**, cả hai theo quy tắc đầu bao gồm/cuối không bao gồm. Đây là lựa chọn phù hợp khi bên gọi cần trích xuất đoạn chuỗi theo số code point.

```java
import java.util.Objects;

public final class CodePointSlices {
    private CodePointSlices() {
    }

    public static String sliceByCodePoints(
            String source, int fromInclusive, int toExclusive) {

        Objects.requireNonNull(source, "source");

        int total = source.codePointCount(0, source.length());
        if (fromInclusive < 0
                || toExclusive < fromInclusive
                || toExclusive > total) {
            throw new IndexOutOfBoundsException(
                    "Invalid code-point range [" + fromInclusive
                    + ", " + toExclusive + "), total=" + total);
        }

        int startUtf16 = source.offsetByCodePoints(0, fromInclusive);
        int endUtf16 = source.offsetByCodePoints(
                startUtf16, toExclusive - fromInclusive);

        return source.substring(startUtf16, endUtf16);
    }
}
```

Đây là **một lớp tiện ích độc lập** có thể lưu trong tệp `CodePointSlices.java`. Cách hoạt động:

1. `codePointCount(0, source.length())` lấy tổng số code point trong toàn chuỗi.
2. Kiểm tra `0 <= fromInclusive <= toExclusive <= total` để tránh cắt vượt giới hạn và xác định rõ hành vi khi chỉ số không hợp lệ.
3. `offsetByCodePoints(0, fromInclusive)` chuyển vị trí code point đầu thành UTF-16 index.
4. Dịch thêm `toExclusive - fromInclusive` code point từ vị trí bắt đầu để có UTF-16 index cuối.
5. Dùng `substring(startUtf16, endUtf16)` trên các **ranh giới UTF-16 đúng**.

Điều kiện cho phép `fromInclusive == toExclusive`; khi đó trả về chuỗi rỗng. `source == null` được xử lý rõ bằng `NullPointerException` từ `Objects.requireNonNull`. Hàm không âm thầm sửa hay cắt bớt chỉ số không hợp lệ.

**Lưu ý về phạm vi bảo đảm:** Với chuỗi đầu vào có các surrogate pair hợp lệ, hàm sẽ không cắt **giữa hai code unit của một pair**. Nó không xác nhận hay sửa một chuỗi UTF-16 đã bị lỗi sẵn (có surrogate đơn lẻ). Phần 8 giải thích thêm.

## 5. Chương trình Java 17 đầy đủ và kết quả chính xác

Để có thể biên dịch và chạy trực tiếp một tệp, sử dụng phiên bản sau trong tệp `SafeCodePointSliceDemo.java`. Phần kết quả cố ý in code point dạng hexadecimal để không phụ thuộc cách terminal hiển thị emoji.

```java
import java.util.Objects;

public class SafeCodePointSliceDemo {
    static String sliceByCodePoints(
            String source, int fromInclusive, int toExclusive) {

        Objects.requireNonNull(source, "source");

        int total = source.codePointCount(0, source.length());
        if (fromInclusive < 0
                || toExclusive < fromInclusive
                || toExclusive > total) {
            throw new IndexOutOfBoundsException("Invalid code-point range");
        }

        int start = source.offsetByCodePoints(0, fromInclusive);
        int end = source.offsetByCodePoints(
                start, toExclusive - fromInclusive);
        return source.substring(start, end);
    }

    public static void main(String[] args) {
        String text = "A" + new String(Character.toChars(0x1F600)) + "B";

        System.out.println("UTF-16 length = " + text.length());
        System.out.println("code points = "
                + text.codePointCount(0, text.length()));

        String broken = text.substring(0, 2);
        System.out.println("broken length = " + broken.length());
        System.out.println("broken ends high surrogate = "
                + Character.isHighSurrogate(
                        broken.charAt(broken.length() - 1)));

        int emojiStart = text.offsetByCodePoints(0, 1);
        int emojiEnd = text.offsetByCodePoints(0, 2);
        System.out.println("emoji UTF-16 range = ["
                + emojiStart + ", " + emojiEnd + ")");

        String emoji = sliceByCodePoints(text, 1, 2);
        System.out.printf("emoji code point = U+%X%n", emoji.codePointAt(0));

        String firstTwo = sliceByCodePoints(text, 0, 2);
        System.out.println("first two code points = "
                + firstTwo.codePointCount(0, firstTwo.length()));
        System.out.println("last code point = "
                + sliceByCodePoints(text, 2, 3));
        System.out.println("whole string unchanged = "
                + sliceByCodePoints(text, 0, 3).equals(text));
        System.out.println("empty slice length = "
                + sliceByCodePoints(text, 1, 1).length());
    }
}
```

Chạy với JDK 17 hoặc mới hơn:

```bash
javac SafeCodePointSliceDemo.java
java SafeCodePointSliceDemo
```

Kết quả chuẩn:

```text
UTF-16 length = 4
code points = 3
broken length = 2
broken ends high surrogate = true
emoji UTF-16 range = [1, 3)
emoji code point = U+1F600
first two code points = 2
last code point = B
whole string unchanged = true
empty slice length = 0
```

Giải thích: Chuỗi gốc có 4 code unit nhưng 3 code point. Cắt `substring(0, 2)` để lại high surrogate đơn. Chuyển hai mốc code point `1` và `2` thành UTF-16 index `1` và `3` giúp lấy trọn emoji; các đoạn hợp lệ không phá cặp surrogate.

## 6. Các cách duyệt theo code point

Nếu không cần `substring` theo một khoảng xác định, có thể duyệt tuần tự bằng `codePointAt` và `Character.charCount`.

```java
String text = "A" + new String(Character.toChars(0x1F600)) + "B";

for (int utf16Index = 0; utf16Index < text.length();) {
    int cp = text.codePointAt(utf16Index);
    System.out.printf("U+%X%n", cp);
    utf16Index += Character.charCount(cp);
}
```

Kết quả:

```text
U+41
U+1F600
U+42
```

Lý do phải tăng `utf16Index` bằng `Character.charCount(cp)`: giá trị ngoài BMP chiếm hai code unit. Nếu dùng `utf16Index++` như vòng lặp `char`, bạn sẽ đi vào low surrogate và xử lý sai ranh giới.

Một cách khác ngắn gọn hơn là `String.codePoints()` (có từ Java 9):

```java
text.codePoints().forEach(cp -> System.out.printf("U+%X%n", cp));
```

Kết quả ba dòng giống ví dụ trên. `codePoints()` cho các giá trị kiểu `int` nên thuận tiện để kiểm tra, lọc hoặc xây dựng đầu ra. Tuy nhiên, nếu muốn biết UTF-16 index của từng code point, bạn vẫn cần theo dõi độ dài code unit hoặc dùng `offsetByCodePoints`.

### Tạo chuỗi từ các code point mà không ghép surrogate bằng tay

```java
String text = "A" + new String(Character.toChars(0x1F600)) + "B";
StringBuilder result = new StringBuilder();

text.codePoints()
        .limit(2)
        .forEach(cp -> result.appendCodePoint(cp));

String prefix = result.toString(); // "A" + emoji U+1F600
System.out.println(prefix.codePointCount(0, prefix.length())); // 2
```

`appendCodePoint` làm phần mã hóa UTF-16 cho giá trị code point, tránh tự tạo hai nửa surrogate. Trong trường hợp chỉ cần cắt một khoảng, `offsetByCodePoints + substring` vẫn thường rõ ý định hơn; ví dụ với stream minh họa phương án *xây dựng* chuỗi theo code point.

## 7. Những trường hợp biên cần kiểm tra

| Đầu vào / yêu cầu | Kết quả nên hiểu |
| --- | --- |
| `source = ""`, cắt `[0, 0)` | Trả về `""` |
| `source = "ABC"`, cắt `[1, 3)` | Trả về `"BC"` |
| `source = "A😀B"`, cắt `[1, 2)` | Trả về trọn emoji 😀 |
| `source = "A😀B"`, cắt `[0, 2)` | Trả về `A` và trọn emoji 😀 |
| `source = "A😀B"`, cắt `[2, 3)` | Trả về `B` |
| `source = "A😀B"`, cắt `[2, 2)` | Trả về `""` |
| Chỉ số âm, đầu lớn hơn cuối, hoặc vượt tổng code point | Ném `IndexOutOfBoundsException` trong hàm ví dụ |
| `source = null` | Ném `NullPointerException` với thông báo liên quan `source` |

**Không trộn lẫn hai hệ chỉ số.** Nếu hệ thống lưu offset do `String.indexOf(...)` hoặc `Matcher.start()` trả về, đó thường là **UTF-16 index**; không đem giá trị này truyền như thể là thứ tự code point vào `sliceByCodePoints`. Ngược lại, `offsetByCodePoints` nhận **vị trí khởi đầu UTF-16** và **số code point muốn di chuyển**, không nhận hai code-point index thuần túy.

Nếu API bên ngoài dùng số “ký tự” thì trước hết phải làm rõ họ định nghĩa “ký tự” là UTF-16 code unit, Unicode code point hay **grapheme cluster** (ký tự được người dùng cảm nhận).

## 8. Hai giới hạn quan trọng: UTF-16 hỏng và grapheme cluster

### 8.1. Cắt theo code point không sửa chuỗi đã có surrogate lẻ

Java `String` có thể chứa code unit high surrogate hoặc low surrogate không ghép thành pair hợp lệ, ví dụ tạo một chuỗi chứa một `char` đơn:

```java
String malformed = new String(new char[] {'A', '\uD83D'});
System.out.println(malformed.length()); // 2
System.out.println(malformed.codePointCount(0, malformed.length())); // 2
```

API `codePointCount` tính surrogate chưa ghép thành một đơn vị khi đếm; `codePoints()` cũng sẽ trả về giá trị surrogate đơn đó dưới dạng `int` thay vì tự tạo code point emoji hợp lệ. Do đó, chọn ranh giới theo code point chỉ tránh **tự tạo thêm** pair bị chia đôi từ một pair hợp lệ. Nếu dữ liệu gốc đã lỗi, cần chính sách **kiểm tra, từ chối hoặc thay thế** dữ liệu không hợp lệ trước khi xử lý, tùy hợp đồng đầu vào.

Không nên dùng `new String(Character.toChars(...))` với một code point ngoài phạm vi Unicode. `Character.toChars(int)` sẽ ném `IllegalArgumentException` cho giá trị không phải code point hợp lệ; còn việc coi surrogate code point là ký tự độc lập cũng không tạo Unicode scalar value hợp lệ. Hãy xác định tiêu chuẩn hợp lệ của dữ liệu đầu vào.

### 8.2. Một code point chưa chắc là một “ký tự nhìn thấy”

Một số ký tự hiển thị được hình thành từ **nhiều code point**. Ví dụ chữ `e` cộng dấu sắc kết hợp:

```java
String combined = "e\u0301";
System.out.println(combined.length()); // 2
System.out.println(combined.codePointCount(0, combined.length())); // 2
```

Nội dung này thường hiển thị gần giống chữ “é”, nhưng là hai code point `U+0065` và `U+0301`. Cắt theo code point `[0, 1)` chỉ lấy `e` và tách dấu sắc kết hợp ra ngoài. Tương tự, nhiều emoji có skin-tone modifier, variation selector, hoặc chuỗi nối bằng zero-width joiner chứa nhiều code point dù được người dùng xem là **một cụm hiển thị**.

Vì vậy cần phân biệt hai mục tiêu:

- **Không chia đôi surrogate pair**: dùng các API code point như `offsetByCodePoints` để lấy ranh giới UTF-16 đúng.
- **Không chia cụm ký tự mà người dùng nhìn thấy**: cần một thuật toán phân đoạn **grapheme cluster** phù hợp với quy tắc Unicode và phiên bản Unicode của môi trường chạy. Đừng giả định cắt theo code point đủ cho thao tác chọn/xóa ký tự trong editor hoặc giới hạn số ký tự hiển thị.

`java.text.BreakIterator.getCharacterInstance(...)` có thể phục vụ việc xác định ranh giới văn bản theo API JDK, nhưng đặc điểm hỗ trợ grapheme phức tạp và emoji có thể phụ thuộc phiên bản JDK/dữ liệu Unicode. Nếu cần tuân thủ đầy đủ **extended grapheme cluster** và các chuỗi emoji hiện đại, hãy xác minh kết quả trên bộ dữ liệu thực tế hoặc dùng thư viện phân đoạn Unicode thích hợp.

## 9. Lưu ý về hiệu năng và thiết kế API

`offsetByCodePoints` có thể phải duyệt qua những code unit kể từ điểm bắt đầu để tìm vị trí tiếp theo; `codePointCount` cũng phải duyệt khoảng được đếm. Với vài phép cắt hoặc chuỗi thông thường, sự rõ ràng của hàm `sliceByCodePoints` là ưu tiên tốt. Nếu thực hiện rất nhiều truy vấn ngẫu nhiên trên cùng chuỗi rất dài, hãy đo hiệu năng và cân nhắc xây dựng trước bảng ánh xạ **code-point index → UTF-16 index** để tránh duyệt lại nhiều lần.

Khi công bố API, đặt tên tham số rõ ràng, ví dụ `fromCodePointIndex` và `toCodePointIndexExclusive`. Nếu lấy chỉ số UTF-16, đặt tên `beginUtf16Index`/`endUtf16Index`. Việc đặt tên rõ giúp tránh lỗi trộn chỉ số từ những API khác nhau.

Với các thao tác chỉ chọn **đầu/cuối theo code point**, có thể tối ưu lời gọi bằng `offsetByCodePoints` từ vị trí bắt đầu thích hợp. Không dùng phép toán `utf16Index + numberOfCodePoints` nếu đoạn đi qua emoji hoặc ký tự ngoài BMP, vì số code point không bằng số UTF-16 code unit.

## 10. Lỗi phổ biến và cách sửa

1. **Dùng `substring(0, n)` để lấy `n` code point.** `n` đang bị diễn giải thành số code unit. Sửa bằng `substring(0, s.offsetByCodePoints(0, n))` sau khi kiểm tra phạm vi hợp lệ.
2. **Dùng `s.length()` làm số lượng code point.** `length()` đếm UTF-16 code unit. Muốn đếm code point, dùng `s.codePointCount(0, s.length())`.
3. **Tăng chỉ số vòng lặp `i++` sau `codePointAt(i)`.** Với code point ngoài BMP, phải tăng theo `Character.charCount(cp)` để bỏ qua cả pair.
4. **Gọi `charAt(i)` rồi coi kết quả luôn là một ký tự Unicode đầy đủ.** Một `char` có thể chỉ là một nửa surrogate pair; dùng `codePointAt(i)` tại ranh giới code point.
5. **Đếm code point rồi truyền index vào `substring`.** Đây là hai hệ tọa độ khác nhau; chuyển bằng `offsetByCodePoints`.
6. **Cho rằng xử lý theo code point luôn giữ nguyên ký tự hiển thị.** Combining mark và emoji nhiều code point yêu cầu phân đoạn grapheme cluster nếu đó là mục tiêu.
7. **Cho rằng cắt theo code point khắc phục mọi UTF-16 không hợp lệ.** Nó không chữa surrogate đơn đã có trong đầu vào; cần kiểm tra riêng khi dữ liệu phải là Unicode scalar values hợp lệ.
8. **Không xác nhận biên và giá trị `null`.** Thông báo lỗi rõ và quy tắc khoảng nửa mở giúp các bên gọi API tránh lỗi off-by-one.

## 11. Tự kiểm tra

Với chuỗi `"X😀YZ"` gồm code point `X`, `😀`, `Y`, `Z`:

- `length()` bằng **5** (1 + 2 + 1 + 1 code unit).
- `codePointCount(0, length())` bằng **4**.
- Khoảng code point `[1, 3)` phải trả về **emoji 😀 rồi `Y`**.
- Tương ứng, hai chỉ số UTF-16 truyền cho `substring` là **1** và **4**.
- `substring(1, 3)` chỉ lấy emoji 😀 vì khoảng UTF-16 `[1, 3)` gồm hai nửa surrogate.
- `substring(0, 2)` sẽ lấy `X` cùng high surrogate và bỏ low surrogate: đây là kết quả cần tránh.

**Ghi nhớ:** `String` được đánh chỉ số bằng **UTF-16 code unit**, trong khi bài toán có thể đòi hỏi **Unicode code point**. Muốn trích xuất theo code point, hãy **chuyển biên code point sang UTF-16 index bằng `offsetByCodePoints`, rồi mới gọi `substring`**. Nếu cần giữ trọn ký tự người dùng cảm nhận, phải xét thêm ranh giới **grapheme cluster**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-extraction-007">Cắt theo cụm ký tự bằng BreakIterator</a>

<details>
<summary>Xem chi tiết</summary>

# Cắt theo cụm ký tự bằng BreakIterator trong Java

Khi cần giới hạn một dòng văn bản theo **số ký tự mà người đọc cảm nhận** — ví dụ cắt nhãn hiển thị, lấy một phần tên người dùng, di chuyển con trỏ hoặc chọn đoạn chữ — chỉ số của `String.substring()` có thể khiến ta cắt sai vị trí. Trong Java, `String` được đánh chỉ số bằng **đơn vị mã UTF-16**; một “ký tự” được người dùng nhận thấy có thể bao gồm **nhiều đơn vị mã** và **nhiều Unicode code point**.

`java.text.BreakIterator.getCharacterInstance(...)` cung cấp cách tìm **ranh giới cụm ký tự** (*character/grapheme boundaries*) cho văn bản. Ta lấy các chỉ số ranh giới mà iterator trả về, rồi dùng `substring(start, end)` tại đúng các vị trí đó. Cách này giúp tránh cắt đôi nhiều tổ hợp chữ có dấu và cặp surrogate; hành vi cụ thể, nhất là với emoji phức tạp, còn phụ thuộc phiên bản JDK và bộ quy tắc Unicode mà nó triển khai.

## 1. Vì sao `substring` thông thường có thể cắt sai?

Xét chuỗi được tạo từ:

- Chữ `A`: một UTF-16 code unit, một code point.
- Dấu sắc kết hợp `U+0301`: thêm một code point/UTF-16 code unit; cùng `A` tạo thành cụm mà mắt thường nhìn thấy như `Á`.
- Emoji 😀 (`U+1F600`): một code point nhưng **hai** UTF-16 code units (cặp surrogate).
- Chữ `B`: một code unit, một code point.

Trong mã Java:

```java
String text = "A\u0301" + new String(Character.toChars(0x1F600)) + "B";
```

Cách chia theo mã UTF-16:

```text
UTF-16 index:  0     1         2       3       4       5
               |  A  | U+0301  | high  | low   |   B   |
               |--- cụm 1 ----|--- cụm 2 -----| cụm 3 |
Ranh giới:     0               2               4       5
```

Giá trị cần phân biệt:

| Phép đo | Kết quả | Đang đếm gì? |
|---|---:|---|
| `text.length()` | 5 | UTF-16 code units |
| `text.codePointCount(0, text.length())` | 4 | Unicode code points, với surrogate pair hợp lệ đếm một |
| Số cụm ký tự theo character `BreakIterator` (ví dụ này) | 3 | `A` + dấu sắc, emoji 😀, `B` |

Nếu gọi `text.substring(0, 1)`, kết quả chỉ gồm `A`, **mất dấu kết hợp**. Nếu gọi `text.substring(0, 3)`, kết quả sẽ chứa `A`, dấu kết hợp và **nửa đầu của surrogate pair**, tạo một chuỗi UTF-16 không well-formed. Dùng `offsetByCodePoints` giúp giữ ranh giới *code point*, nhưng vẫn có thể cắt giữa chữ cái và dấu kết hợp. Vì thế, bài toán “lấy N ký tự mà người dùng nhìn thấy” cần một phép chia cấp cao hơn code unit/code point.

### Grapheme cluster có đồng nghĩa một glyph không?

Không hoàn toàn. *Grapheme cluster* là **đơn vị phân đoạn văn bản theo quy tắc Unicode**, gần với khái niệm “một ký tự” của người dùng. Một cụm có thể chứa chữ cái cộng dấu, một emoji có bộ điều chỉnh màu da, cờ ghép từ regional indicators hoặc emoji nối bằng ZWJ. Tuy nhiên cách font hiển thị thành một hay nhiều hình (*glyph*) còn phụ thuộc môi trường render. Vì vậy, ranh giới grapheme không phải phép đo chiều rộng hiển thị hay số glyph chính xác.

## 2. `BreakIterator` làm gì?

`BreakIterator` trong package `java.text` là iterator **có trạng thái**, định vị các **ranh giới** trong văn bản. Nó không trực tiếp trả về `List<String>`. Các giá trị mà nó trả về là **offset UTF-16** thích hợp để truyền vào `String.substring(...)`.

Khởi tạo cho ranh giới kiểu “ký tự”:

```java
import java.text.BreakIterator;
import java.util.Locale;

BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.ROOT);
iterator.setText("A\u0301B");
```

- `getCharacterInstance(locale)`: chọn **character boundaries**; đừng nhầm với `getWordInstance` (tách từ), `getLineInstance` (khả năng xuống dòng), `getSentenceInstance` (câu).
- `Locale.ROOT` giúp ví dụ không phụ thuộc default locale của môi trường. Với ứng dụng có ngôn ngữ cụ thể, có thể truyền locale phù hợp, chẳng hạn `Locale.forLanguageTag("vi-VN")`. Các provider, phiên bản JDK và dữ liệu Unicode vẫn có thể ảnh hưởng kết quả.
- `setText(text)`: đặt văn bản đang duyệt và đưa iterator về ranh giới đầu. Tạo mới iterator hoặc gọi lại `setText` khi xử lý một chuỗi mới.

Các thao tác quan trọng:

| Phương thức | Tác dụng |
|---|---|
| `first()` | Trả về ranh giới đầu, thường là offset `0` với `String`. |
| `next()` | Trả về ranh giới kế tiếp hoặc `BreakIterator.DONE` nếu hết. |
| `last()` | Trả về ranh giới cuối, bằng `text.length()` với `String` đã đặt. |
| `previous()` | Di chuyển về ranh giới phía trước; trả `DONE` nếu không còn. |
| `following(i)` | Tìm **ranh giới đầu tiên lớn hơn** offset `i`, không phải lớn hơn hoặc bằng. |
| `preceding(i)` | Tìm **ranh giới cuối cùng nhỏ hơn** offset `i`. |
| `isBoundary(i)` | Kiểm tra offset `i` có đúng ranh giới được iterator chấp nhận hay không. |
| `current()` | Lấy offset ranh giới hiện tại của iterator. |
| `BreakIterator.DONE` | Giá trị đánh dấu hết, **không** phải vị trí dùng làm chỉ số `substring`. |

Chú ý `following`, `preceding` và `isBoundary` có thể thay đổi vị trí của iterator. Khi duyệt toàn bộ từ đầu, hãy gọi `first()` rồi `next()` theo thứ tự; đừng thực hiện kiểm tra ranh giới xen giữa mà quên trạng thái đã thay đổi.

## 3. Duyệt từng cụm ký tự

Mẫu vòng lặp thường dùng:

```java
BreakIterator it = BreakIterator.getCharacterInstance(Locale.ROOT);
it.setText(text);

int start = it.first();
for (int end = it.next(); end != BreakIterator.DONE; end = it.next()) {
    String cluster = text.substring(start, end);
    // Dùng cluster để hiển thị, đếm hoặc xử lý
    start = end;
}
```

Đây là **đoạn mã minh họa**, giả định `text` đã được khai báo. Thứ tự là:

1. `first()` trả ranh giới đầu tiên và gán vào `start`.
2. `next()` trả ranh giới kế tiếp và gán vào `end`.
3. `substring(start, end)` lấy một đoạn giữa **hai ranh giới liên tiếp**; `start` tính vào, `end` không tính vào.
4. Dời `start = end` để chuẩn bị cụm tiếp theo.
5. Dừng khi `next()` trả `DONE`; không gọi `substring` với `DONE`.

Duyệt như vậy giữ lại mọi khoảng trắng và dấu câu thành các đoạn theo quy tắc character boundary; nó **không loại bỏ** khoảng trắng hay tách “từ”. Với chuỗi rỗng, iterator có một ranh giới `0` và không có đoạn nội dung nào để duyệt.

## 4. Ví dụ hoàn chỉnh: tách và cắt theo chỉ số cụm ký tự

Chương trình sau đặt toàn bộ ranh giới vào danh sách `offsets`. Nhờ thế có thể:

- Tách chuỗi thành danh sách cụm bằng `splitClusters(text)`.
- Lấy đoạn từ cụm `from` (**bao gồm**) đến cụm `to` (**không bao gồm**) bằng `sliceClusters(text, from, to)`.
- Lấy `N` cụm đầu bằng `sliceClusters(text, 0, N)`.

Lưu tệp **`GraphemeCutDemo.java`**:

```java
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GraphemeCutDemo {
    // Kết quả gồm cả hai ranh giới đầu và cuối, theo chỉ số UTF-16.
    public static List<Integer> boundaries(String text) {
        BreakIterator iterator =
                BreakIterator.getCharacterInstance(Locale.ROOT);
        iterator.setText(text);

        List<Integer> offsets = new ArrayList<>();
        for (int i = iterator.first();
                i != BreakIterator.DONE;
                i = iterator.next()) {
            offsets.add(i);
        }
        return offsets;
    }

    public static List<String> splitClusters(String text) {
        List<Integer> offsets = boundaries(text);
        List<String> result = new ArrayList<>();

        for (int i = 0; i + 1 < offsets.size(); i++) {
            result.add(text.substring(offsets.get(i), offsets.get(i + 1)));
        }
        return result;
    }

    // Cắt khoảng [from, to), đơn vị là cụm ký tự.
    public static String sliceClusters(String text, int from, int to) {
        List<Integer> offsets = boundaries(text);
        int clusterCount = offsets.size() - 1;

        if (from < 0 || to < from || to > clusterCount) {
            throw new IndexOutOfBoundsException(
                    "Invalid cluster range: [" + from + ", " + to + ")");
        }

        return text.substring(offsets.get(from), offsets.get(to));
    }

    public static void main(String[] args) {
        String text = "A\u0301"
                + new String(Character.toChars(0x1F600))
                + "B";

        System.out.println("UTF-16 units: " + text.length());
        System.out.println("Code points: "
                + text.codePointCount(0, text.length()));
        System.out.println("Offsets: " + boundaries(text));
        System.out.println("Clusters: " + splitClusters(text).size());
        System.out.println("Lengths: "
                + splitClusters(text).stream().map(String::length).toList());

        System.out.println("First: " + sliceClusters(text, 0, 1).length());
        System.out.println("First 2: " + sliceClusters(text, 0, 2).length());
        System.out.println("Last 2: " + sliceClusters(text, 1, 3).length());
        System.out.println("Empty: " + sliceClusters(text, 2, 2).length());

        BreakIterator probe =
                BreakIterator.getCharacterInstance(Locale.ROOT);
        probe.setText(text);

        System.out.println("Boundary at 1: " + probe.isBoundary(1));
        System.out.println("Boundary at 3: " + probe.isBoundary(3));
        System.out.println("Following 2: " + probe.following(2));
    }
}
```

Biên dịch và chạy bằng **JDK 17 trở lên**:

```bash
javac -encoding UTF-8 GraphemeCutDemo.java
java GraphemeCutDemo
```

Đầu ra với phân đoạn cơ bản trong ví dụ:

```text
UTF-16 units: 5
Code points: 4
Offsets: [0, 2, 4, 5]
Clusters: 3
Lengths: [2, 2, 1]
First: 2
First 2: 4
Last 2: 3
Empty: 0
Boundary at 1: false
Boundary at 3: false
Following 2: 4
```

Giải thích:

- Danh sách ranh giới `[0, 2, 4, 5]` tạo ra **3** đoạn có độ dài UTF-16 `[2, 2, 1]`.
- `sliceClusters(text, 0, 1)` lấy **cả `A` và dấu kết hợp**, nên có độ dài UTF-16 là 2.
- `sliceClusters(text, 0, 2)` còn gồm **toàn bộ cặp surrogate của emoji**, nên có độ dài 4.
- `sliceClusters(text, 1, 3)` trả emoji cùng `B`, tổng cộng 3 UTF-16 code units.
- `sliceClusters(text, 2, 2)` trả chuỗi rỗng vì đoạn cắt nửa mở `[2, 2)` không có cụm nào.
- Offset `1` rơi vào giữa chữ `A` và dấu kết hợp; offset `3` rơi vào giữa surrogate pair: cả hai không phải ranh giới.
- `following(2)` trả `4` vì phải tìm ranh giới **lớn hơn** `2`.

Dữ liệu mẫu được dựng từ escape Unicode và `Character.toChars` để kết quả đếm không phụ thuộc việc terminal có font hỗ trợ emoji hay không. Đây là chương trình hoàn chỉnh có thể biên dịch; các đoạn mã ngắn ở các mục khác nhằm minh họa cách dùng API.

### Vì sao cần hai danh sách: ranh giới và cụm?

`boundaries` chứa **offset**, có `m + 1` phần tử cho `m` cụm. Dùng hai offset liên tiếp làm giới hạn `substring` sẽ không mất phần nào của chuỗi. Danh sách `splitClusters` có đúng `m` đoạn, nối tất cả các đoạn theo thứ tự sẽ tạo lại nội dung ban đầu:

```java
String original = "A\u0301B";
String reconstructed = String.join("", splitClusters(original));
System.out.println(original.equals(reconstructed)); // true
```

Lưu ý đoạn cuối là **minh họa cách dùng**, giả định gọi trong cùng lớp hoặc truy cập `GraphemeCutDemo.splitClusters(...)` từ lớp khác.

## 5. Cắt N cụm đầu và xử lý chỉ số không hợp lệ

Gọi:

```java
String firstTwo = sliceClusters(text, 0, 2);
```

nếu `text` có ít nhất hai cụm. Hàm trong ví dụ dùng **quy tắc chặt**:

- `from == to` hợp lệ nếu nằm trong khoảng `0` đến số cụm; kết quả là chuỗi rỗng.
- `from < 0`, `to < from` hoặc `to` lớn hơn số cụm đều báo `IndexOutOfBoundsException`.
- `sliceClusters("", 0, 0)` hợp lệ và trả `""`.
- `sliceClusters("", 0, 1)` không hợp lệ vì chuỗi rỗng có 0 cụm.

Với giao diện giới hạn “tối đa N cụm” (ví dụ preview văn bản), có thể chọn chính sách **cắt tới hết nếu N vượt quá số cụm**:

```java
static String takeUpToClusters(String text, int maxClusters) {
    if (maxClusters < 0) {
        throw new IllegalArgumentException("maxClusters must be >= 0");
    }

    BreakIterator it = BreakIterator.getCharacterInstance(Locale.ROOT);
    it.setText(text);

    int end = it.first();
    for (int n = 0; n < maxClusters; n++) {
        int next = it.next();
        if (next == BreakIterator.DONE) {
            break;
        }
        end = next;
    }
    return text.substring(0, end);
}
```

Đây là **phương thức bổ sung** có thể đặt vào `GraphemeCutDemo`, không phải chương trình chạy độc lập. Nó chỉ giữ offset kết thúc và có chi phí bộ nhớ bổ sung rất nhỏ; phù hợp khi chỉ cần lấy prefix, không cần lưu mọi ranh giới. Nếu `maxClusters` bằng 0, kết quả là chuỗi rỗng; nếu vượt số cụm, kết quả là toàn bộ chuỗi.

Hai chính sách đều hợp lý, nhưng phải được đặt tên và ghi rõ: **`sliceClusters` kiểm tra range**, còn **`takeUpToClusters` giới hạn tối đa**.

## 6. JDK 17 và JDK 20+: lưu ý emoji phức tạp

Đây là giới hạn quan trọng nhất khi dùng `BreakIterator`.

Trong **JDK 20**, triển khai character boundary mặc định được cập nhật để tuân theo quy tắc **Unicode Extended Grapheme Cluster** (UAX #29). Trước thay đổi đó, đặc biệt với **emoji ZWJ** và **cờ ghép từ hai regional indicators**, một số bản JDK cũ có thể trả các ranh giới khiến tổ hợp hiển thị bị tách ra.

Ví dụ tổ hợp:

```text
🇺🇸 👨‍👩‍👧‍👦
```

- Trên triển khai đã hỗ trợ Extended Grapheme Cluster theo quy tắc hiện hành, hai tổ hợp trên được tách thành **hai cụm** (nếu viết liền nhau, không có dấu cách).
- Trên một số JDK trước 20, character `BreakIterator` có thể chia thành nhiều đoạn nhỏ hơn, thậm chí tách các thành phần nối bằng **ZWJ (U+200D)** hoặc các ký hiệu regional indicator.

Do đó, **không dùng output số cụm emoji phức tạp từ một bản JDK làm hợp đồng chung cho mọi bản**. Cần kiểm tra phiên bản JVM, provider, dữ liệu Unicode và nhu cầu của hệ thống. Nếu ứng dụng yêu cầu phân đoạn Unicode nghiêm ngặt, nhất quán giữa nhiều phiên bản JDK, hãy chọn và kiểm thử một bộ triển khai rõ ràng (chẳng hạn ICU4J nếu phù hợp) cùng phiên bản dữ liệu Unicode được quản lý.

Bài demo ở mục 4 cố ý dùng tổ hợp **chữ + dấu kết hợp** và một **code point emoji bổ sung** thay vì emoji ZWJ phức tạp để trình bày nguyên tắc ranh giới và có kết quả dễ kiểm tra trên các JDK thông dụng.

Điều này cũng cho thấy “cụm ký tự” là kết quả của **quy tắc phân đoạn**, không phải chỉ việc đếm code point hoặc cắt mỗi `char`.

## 7. Dùng `following`, `preceding` và `isBoundary` khi có offset con trỏ

Một ứng dụng soạn thảo có thể đang lưu vị trí con trỏ dưới dạng **UTF-16 offset**. Trước khi cắt, di chuyển hoặc xóa phần văn bản, cần tránh để offset nằm trong một cụm:

```java
BreakIterator it = BreakIterator.getCharacterInstance(Locale.ROOT);
it.setText(text);

boolean valid = it.isBoundary(cursorUtf16Offset);
int nextBoundary = it.following(cursorUtf16Offset);
int previousBoundary = it.preceding(cursorUtf16Offset);
```

Đoạn trên minh họa API, với `text` và `cursorUtf16Offset` đã được khai báo. Cần lưu ý:

- `isBoundary(i)` chỉ nhận offset trong miền của văn bản; offset ngoài miền có thể ném `IllegalArgumentException`.
- `following(i)` là ranh giới **sau** `i`, kể cả khi `i` vốn đã là ranh giới. Không dùng nó nếu muốn giữ nguyên một con trỏ đã đúng ranh giới.
- `preceding(i)` là ranh giới **trước** `i`, kể cả khi `i` vốn đã là ranh giới.
- `following(text.length())` hoặc `preceding(0)` trả `BreakIterator.DONE`. Phải kiểm tra `DONE` trước khi biến kết quả thành chỉ số `substring`.
- Những lệnh tìm kiếm ranh giới có thể thay đổi trạng thái iterator; không giả định sau `isBoundary` thì `current()` vẫn giữ nguyên vị trí cũ.

Trong giao diện, việc “đẩy con trỏ về ranh giới bên trái hay bên phải” là quyết định trải nghiệm người dùng, không phải một quy tắc cố định của API. Tùy thao tác và hướng kéo, chọn `preceding`, `following` hoặc giữ nguyên `i` khi `isBoundary(i)` là `true`.

## 8. Locale, Unicode normalization và tính đúng của đầu vào

**Locale:** `BreakIterator.getCharacterInstance(locale)` nhận `Locale` để chọn dịch vụ phân đoạn. Điều này **không biến chuỗi đầu vào thành ngôn ngữ đó** và không dịch nội dung. `Locale.ROOT` thuận tiện cho ví dụ; trong ứng dụng quốc tế hóa, dùng locale của văn bản và kiểm thử các script thực tế.

**Unicode normalization:** `BreakIterator` tìm ranh giới, **không chuẩn hóa** chuỗi. Hai biểu diễn `"\u00C1"` (một code point Á) và `"A\u0301"` (A + dấu sắc) có thể nhìn giống nhau nhưng khác dãy code point, độ dài UTF-16 và kết quả `equals()`. Cả hai thường là một cụm khi phân đoạn, nhưng sau khi `substring` chúng vẫn giữ nguyên các đơn vị mã gốc. Nếu ứng dụng cần so sánh dạng chuẩn, cân nhắc `java.text.Normalizer` với chính sách riêng; **đừng âm thầm chuẩn hóa chỉ để thực hiện thao tác cắt**.

**UTF-16 không well-formed:** `String` Java có thể chứa high/low surrogate không ghép cặp. Nếu đầu vào bị lỗi, đừng suy luận rằng tìm được vài boundary nghĩa là dữ liệu Unicode đã hợp lệ. Cần một bước xác thực riêng khi dữ liệu sẽ được mã hóa hoặc trao đổi với hệ thống yêu cầu Unicode scalar values.

**Không bảo đảm hiển thị đẹp:** Giữ ranh giới grapheme vẫn không đảm bảo dòng văn bản vừa kích thước pixel, ký tự có font hỗ trợ, hoặc dòng không vượt chiều rộng. Đo chiều rộng cần công cụ layout/font; ngắt dòng dùng thuật toán tương ứng, không dùng `getCharacterInstance` thay thế đo chiều rộng.

## 9. Hiệu năng và quản lý iterator

- **Chọn đúng nhu cầu:** nếu chỉ lấy vài cụm đầu, thuật toán dùng một iterator và giữ `end` (mục 5) tránh cấp phát danh sách tất cả boundaries. Nếu cần truy vấn nhiều đoạn trên cùng một chuỗi bất biến, tính và tái sử dụng danh sách boundaries có thể tiết kiệm việc phân đoạn lại.
- **Bộ nhớ:** `splitClusters` tạo danh sách và các chuỗi đoạn; `boundaries` tạo danh sách offset theo số cụm. `takeUpToClusters` chỉ giữ một vài biến, ngoài đối tượng iterator.
- **Trạng thái:** một `BreakIterator` có vị trí duyệt và văn bản đang xét; không chia sẻ cùng một instance đang bị thay đổi giữa các thao tác hoặc luồng mà không đồng bộ. Dùng instance riêng cho thao tác độc lập.
- **Chỉ số UTF-16:** dù API tìm ranh giới theo đơn vị gần với ký tự của người dùng, **giá trị trả về vẫn là UTF-16 offset**. Đừng đem số thứ tự cụm dùng trực tiếp làm đối số của `substring`.
- **Chuỗi thay đổi:** nếu văn bản đã sửa, danh sách offset từ lần phân đoạn cũ không còn chắc chắn đúng. Tính lại boundaries sau khi cập nhật chuỗi. `String` bản thân bất biến nhưng biến `text` có thể tham chiếu tới chuỗi khác.
- **Test Unicode:** chuẩn bị ca kiểm thử bao gồm chữ có dấu kết hợp, ký tự ngoài BMP, emoji ZWJ, cờ, một chuỗi rỗng, ngắt dòng và script trong tập người dùng thực tế. Khóa phiên bản thư viện/JDK nếu cần kết quả phân đoạn tái lập.

## 10. Lỗi thường gặp

| Sai lầm | Vì sao có vấn đề | Cách làm đúng |
|---|---|---|
| `substring(0, n)` để lấy `n` ký tự hiển thị | `n` được hiểu là UTF-16 offset | Tìm boundary thứ `n` rồi mới gọi `substring(0, boundary)` |
| Dùng `codePointCount` thay số cụm | Một cụm có thể có nhiều code point | Duyệt character `BreakIterator` |
| Gọi `substring(start, BreakIterator.DONE)` | `DONE` không phải offset hợp lệ | Dừng duyệt trước khi gọi `substring` |
| Gọi `following(i)` để giữ nguyên `i` khi đã đúng boundary | `following` tìm **lớn hơn** `i` | Kiểm tra `isBoundary(i)` trước hoặc áp dụng chính sách rõ ràng |
| Tách bằng `getWordInstance()` | Đang chia theo từ, không phải cụm ký tự | Dùng `getCharacterInstance()` |
| Mặc định emoji ZWJ luôn là một cụm trên JDK 17 | Các quy tắc được cập nhật đáng kể từ JDK 20 | Kiểm tra JDK và phiên bản quy tắc phân đoạn |
| Coi grapheme là độ rộng một ký tự monospace | Rendering và grapheme segmentation khác nhau | Dùng API đo layout khi cần chiều rộng |
| Tái sử dụng iterator khi đã đổi nội dung văn bản | Ranh giới thuộc chuỗi đã đặt bằng `setText` | Gọi lại `setText` hoặc tạo instance mới |
| Nghĩ phân đoạn đồng nghĩa chuẩn hóa Unicode | BreakIterator chỉ tính ranh giới | Xử lý normalization riêng nếu nghiệp vụ yêu cầu |

## 11. Bài tập tự kiểm tra

1. Với `String s = "A\u0301B";`, `s.length()` và số cụm theo `getCharacterInstance` trong ví dụ là bao nhiêu?
2. Nếu danh sách boundaries là `[0, 2, 4, 5]`, `substring(2, 4)` lấy cụm nào? Vì sao `substring(2, 3)` có thể gây vấn đề?
3. Trong hàm `sliceClusters(text, from, to)`, vì sao phải chấp nhận `from == to` nhưng từ chối `to > clusterCount`?
4. Vì sao mã cắt theo code point vẫn có thể tách `A` và dấu kết hợp `U+0301`?
5. Bạn nên kiểm thử gì trước khi dùng character `BreakIterator` trên JDK 17 để cắt emoji gia đình nối bằng ZWJ?

**Gợi ý đáp án:** (1) `length() == 3` và có 2 cụm (`A` + dấu, `B`). (2) `substring(2, 4)` lấy emoji ngoài BMP; `substring(2, 3)` chỉ giữ high surrogate, làm hỏng cặp. (3) Đoạn rỗng là hợp lệ; vượt ngoài số cụm thì không có boundary đích để cắt. (4) Code point khác grapheme cluster. (5) Kiểm thử ranh giới thực tế trên JDK đang triển khai; nếu yêu cầu extended grapheme cluster, cần bản triển khai phù hợp như JDK 20+ hoặc giải pháp khác được kiểm chứng.

## 12. Tài liệu tham khảo

- [Oracle Java SE 17: `java.text.BreakIterator`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/BreakIterator.html) — contract `first`, `next`, `following`, `preceding`, `isBoundary` và mô tả character boundary.
- [Oracle JDK 20 Release Notes: Grapheme Support in BreakIterator (JDK-8291660)](https://www.oracle.com/java/technologies/javase/20-relnote-issues.html) — thay đổi để hỗ trợ Extended Grapheme Cluster và ví dụ cờ/emoji ZWJ.
- [Unicode Standard Annex #29 — Unicode Text Segmentation](https://www.unicode.org/reports/tr29/) — nguyên tắc phân đoạn và ranh giới grapheme clusters.

**Ghi nhớ:** `String` đánh chỉ số bằng UTF-16; `codePoints()` xử lý code point; `BreakIterator.getCharacterInstance(...)` tìm ranh giới cụm ký tự theo quy tắc của bản triển khai. Để cắt an toàn theo các cụm, hãy **tìm hai offset boundary → gọi `substring(start, end)`**, và kiểm tra hành vi emoji phức tạp theo phiên bản JDK thực tế.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-extraction-008">Không phụ thuộc vào việc kết quả có dùng chung đối tượng hoặc bộ nhớ nội bộ</a>

<details>
<summary>Xem chi tiết</summary>

# Không phụ thuộc vào việc kết quả trích xuất chuỗi có dùng chung đối tượng hoặc bộ nhớ nội bộ

## 1. Mục tiêu và vấn đề cần hiểu

Khi gọi `String.substring(...)`, điều ứng dụng **được phép dựa vào** là **chuỗi ký tự kết quả**, phạm vi trích xuất và tính bất biến của `String`. Ứng dụng **không được suy luận** rằng kết quả luôn là một đối tượng `String` mới, luôn dùng chung đối tượng với chuỗi gốc, luôn chia sẻ mảng bên trong hay luôn sở hữu một bản sao bộ nhớ riêng.

Điều này liên quan trực tiếp đến bài toán **trích xuất chuỗi**. Ví dụ:

~~~java
String source = "order:12345";
String id = source.substring(6); // "12345"
~~~

Phần nghiệp vụ có thể khẳng định:

- `id` chứa đúng năm ký tự `"12345"`.
- `id.length() == 5`.
- `id.equals("12345")` là `true`.
- Không thể thay đổi nội dung `source` hoặc `id` bằng cách “sửa ký tự bên trong String”.

Tuy nhiên **không cần** và **không nên** khẳng định `source` và `id` có sử dụng chung một mảng `char[]`/`byte[]` hoặc có địa chỉ lưu trữ cụ thể. Các yếu tố đó thuộc cách JVM/JDK triển khai và tối ưu hóa.

Sau bài học, bạn sẽ biết đọc đúng hợp đồng của `substring`, tránh dùng `==` cho so sánh nội dung, phân biệt copy/alias/bất biến, hiểu hệ quả bộ nhớ của các phiên bản Java, và tổ chức mã ổn định khi thay đổi JDK.

## 2. Hợp đồng của `substring` quy định những gì?

Hai overload có hợp đồng **nội dung** rõ ràng: `s.substring(begin)` lấy từ `begin` đến hết, còn `s.substring(begin, end)` lấy `[begin, end)`. Với `n = s.length()`, điều kiện lần lượt là `0 <= begin <= n` và `0 <= begin <= end <= n`; độ dài kết quả hai tham số là `end - begin` UTF-16 code unit. Biên bằng nhau hợp lệ trả chuỗi rỗng; phạm vi sai ném `IndexOutOfBoundsException`.

```java
String s = "programming";
String prefix = s.substring(0, 7);  // "program"
String suffix = s.substring(7);     // "ming"
String empty = s.substring(7, 7);   // ""
```

Đây là các **giá trị ký tự** có thể dựa vào. Hợp đồng không đòi kết quả dùng cùng instance, instance khác, mảng sao chép hay mảng chia sẻ. `String.subSequence(begin, end)` có nội dung tương đương trên đối tượng `String` nhưng khai báo kiểu trả về là `CharSequence`.

## 3. Ba khái niệm khác nhau: giá trị, identity và backing storage

**Giá trị (value):** dãy code unit UTF-16 của chuỗi. Đây là những gì `equals`, `length`, `charAt`, `substring` và nhiều API công khai làm việc cùng.

**Identity (định danh đối tượng):** hai biến có trỏ đến cùng một instance `String` hay không; dùng `==` giữa hai tham chiếu để kiểm tra. Đây là quan hệ đối tượng, **không phải quan hệ nội dung**.

**Backing storage (vùng lưu trữ nội bộ):** cơ chế JDK dùng để biểu diễn ký tự, chẳng hạn mảng byte kèm thông tin mã hóa nội bộ ở một số triển khai. Hai đối tượng khác nhau có thể dùng chung tài nguyên nội bộ; cùng một đối tượng thì hiển nhiên dùng trạng thái nội bộ của chính nó. `==` không cho biết hai instance khác nhau có dùng chung backing array hay không.

| Câu hỏi | Công cụ phù hợp | Kết luận cho chương trình |
| --- | --- | --- |
| Hai chuỗi có **cùng nội dung** không? | `a.equals(b)` | Phù hợp để so sánh giá trị |
| Hai tham chiếu có cùng **đối tượng** không? | `a == b` | Chỉ có ý nghĩa khi thực sự cần identity |
| Hai đối tượng có cùng backing array không? | Không có hợp đồng công khai của `String` để yêu cầu việc này | Không thể suy luận từ `equals` hay `==` |
| `substring` có giữ cả vùng lưu trữ lớn không? | Phân tích phiên bản JDK, heap profiler và thử nghiệm | Quyết định tối ưu dựa trên bằng chứng từng môi trường |

Ví dụ:

~~~java
String a = new String(new char[] {'a', 'b', 'c'});
String b = new String(new char[] {'a', 'b', 'c'});

System.out.println(a.equals(b)); // true
System.out.println(a == b);      // false: hai lần tạo đối tượng String mới
~~~

Hai chuỗi có cùng nội dung dù là hai đối tượng khác nhau. Ngược lại, nếu một triển khai trả về chính `this` khi gọi `substring(0, length())`, điều đó không làm thay đổi **ý nghĩa giá trị** của kết quả. Bạn không cần nhánh xử lý riêng cho tình huống đó.

### Đừng lấy kết quả của `==` làm bằng chứng về cách lưu trữ

~~~java
String source = "ABCD";
String whole = source.substring(0, source.length());
String half = source.substring(0, 2);

// Đừng viết điều kiện nghiệp vụ phụ thuộc các so sánh identity này.
boolean wholeSameObject = (whole == source);
boolean halfSameObject = (half == source);

// Nội dung được xác định bởi API.
boolean wholeCorrect = whole.equals("ABCD"); // true
boolean halfCorrect = half.equals("AB");     // true
~~~

`wholeSameObject` và `halfSameObject` không giúp kiểm tra dữ liệu trích xuất có đúng không. Chúng cũng không chứng minh có sao chép bộ đệm hoặc giữ tham chiếu đến bộ đệm lớn hay không.

## 4. Lợi ích của tính bất biến: kết quả không bị sửa ngầm

`String` là immutable: sau khi tạo, **dãy ký tự của instance đó không bị chỉnh sửa** qua API công khai. Vì vậy ứng dụng có thể truyền một `String` trích xuất sang hàm khác mà không sợ hàm đó sửa trực tiếp nội dung chuỗi.

~~~java
String source = "hello-world";
String part = source.substring(6); // "world"

source = "changed";   // gán biến sang một String khác
System.out.println(part); // "world"
~~~

Phép gán `source = "changed"` chỉ thay **tham chiếu trong biến `source`**. Nó **không** chỉnh sửa instance nguồn cũ, không thay đổi `part`. Kết quả này không chứng minh `part` có mảng bộ nhớ riêng: dù dữ liệu được copy hay chia sẻ an toàn, **giá trị công khai** vẫn ổn định do các `String` là bất biến.

Với một mảng `char[]` có thể sửa, `String.toCharArray()` tạo **mảng mới** chứa ký tự hiện có:

~~~java
String part = "world";
char[] editable = part.toCharArray();
editable[0] = 'W';

System.out.println(new String(editable)); // "World"
System.out.println(part);                 // "world"
~~~

Thay đổi `editable` chỉ tác động đến mảng mới. Đây là **hợp đồng rõ ràng của `toCharArray`**, khác với suy luận mơ hồ về mảng nội bộ mà `substring` có thể dùng.

Ngay cả `StringBuilder` là đối tượng **mutable**, khi lấy kết quả `String` bằng `substring` thì kết quả có giá trị tại thời điểm trích xuất:

~~~java
StringBuilder builder = new StringBuilder("mutable");
String saved = builder.substring(0, 4); // "muta"

builder.setCharAt(0, 'M');
System.out.println(builder); // "Mutable"
System.out.println(saved);   // "muta"
~~~

Ở đây `saved` là `String`; các sửa đổi tương lai trên `builder` không làm nội dung `saved` thay đổi. Đó là điều cần cho nghiệp vụ. Không cần biết cách hai kiểu đối tượng quản lý mảng nội bộ.

## 5. Java từng thay đổi cách lưu trữ chuỗi — không biến nó thành hợp đồng API

Cách tổ chức `String` đã thay đổi theo phiên bản JDK:

- **Một số JDK cũ trước Java 7u6** triển khai `substring` bằng cách giữ một view gồm offset/length vào mảng ký tự lớn của nguồn. Nếu ứng dụng giữ lại đoạn con rất nhỏ nhưng bỏ chuỗi lớn, mảng lớn **có thể** bị giữ lại lâu hơn dự kiến.
- **Các bản OpenJDK/JDK hiện đại**, bao gồm Java 17, không còn cơ chế view `String` kiểu offset/count đó. Trong mã nguồn OpenJDK 17, nhánh `substring(0, length())` có thể trả về chính đối tượng `this`; các nhánh khác tạo biểu diễn chuỗi con qua helper tương ứng.
- **Java 9 trở lên** có cơ chế *Compact Strings* ở các triển khai OpenJDK phổ biến: biểu diễn nội bộ có thể dùng `byte[]` và thông tin coder. Bởi vậy không nên mô tả mọi `String` Java hiện đại là “một mảng `char[]`”.
- Java có thể tiếp tục thay đổi tối ưu hóa, chia sẻ dữ liệu nội bộ hoặc cách cấp phát. `java.lang.String` API **không hứa** lượng bộ nhớ chính xác, identity của mỗi kết quả `substring` hay khả năng thu gom một mảng cụ thể tại một thời điểm cụ thể.

Điểm cần giữ khi chuyển JDK: **logic chương trình dựa vào ký tự và API**. Nếu yêu cầu là giảm bộ nhớ, phải kiểm tra thông tin triển khai của **JDK đang chạy** và đo đúng tải sử dụng.

### Vì sao đoạn con nhỏ có thể liên quan đến bộ nhớ lớn?

Tưởng tượng xử lý một bản ghi văn bản dài, chỉ giữ vài ký tự:

~~~java
String largeRecord = loadRecord();
String key = largeRecord.substring(0, 8);
// Các bước khác chỉ giữ key.
~~~

*`loadRecord()` là hàm giả định; đoạn trên minh họa luồng dữ liệu, không phải một chương trình độc lập.*

Trên một triển khai dùng chung backing array, `key` có thể giữ gián tiếp dữ liệu lớn. Trên một triển khai sao chép dải con, khả năng giữ bộ nhớ và chi phí copy khác đi. Nhưng **không có một quy tắc cấp phát cho mọi JVM**, và `key.equals(...)` vẫn đúng ở cả hai.

Với các hệ thống xử lý lượng lớn dữ liệu, quan sát heap dump, allocation profile, khả năng sống lâu của object và benchmark đại diện. Tránh đưa thêm các bản sao chỉ vì nghe nói `substring` “luôn chia sẻ mảng” hoặc “luôn tạo mảng riêng”.

## 6. Một ví dụ Java 17+ có thể chạy và kiểm tra được

Tệp **`SubstringIndependenceDemo.java`**:

~~~java
public class SubstringIndependenceDemo {
    public static void main(String[] args) {
        String source = "prefix:alpha|suffix:omega";

        int start = source.indexOf(':') + 1;
        int end = source.indexOf('|', start);
        String part = source.substring(start, end);
        String other = new String(new char[] {'a', 'l', 'p', 'h', 'a'});

        System.out.println("part=" + part);
        System.out.println("equalContent=" + part.equals(other));
        System.out.println("length=" + part.length());
        System.out.println("prefix=" + source.substring(0, 6));
        System.out.println("empty=" + source.substring(6, 6).isEmpty());

        char[] editableCopy = part.toCharArray();
        editableCopy[0] = 'X';
        System.out.println("arrayFirst=" + editableCopy[0]);
        System.out.println("partAfterArrayEdit=" + part);

        source = "reassigned";
        System.out.println("partAfterSourceReassign=" + part);

        StringBuilder builder = new StringBuilder("mutable");
        String snapshot = builder.substring(0, 4);
        builder.setCharAt(0, 'M');
        System.out.println("builder=" + builder);
        System.out.println("snapshot=" + snapshot);

        String withEmoji = "A" + Character.toString(0x1F600) + "B";
        int afterTwoCodePoints = withEmoji.offsetByCodePoints(0, 2);
        String prefix = withEmoji.substring(0, afterTwoCodePoints);
        System.out.println("utf16End=" + afterTwoCodePoints);
        System.out.println("prefixCodePoints="
                + prefix.codePointCount(0, prefix.length()));
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac -encoding UTF-8 SubstringIndependenceDemo.java
java SubstringIndependenceDemo
~~~

Kết quả `stdout` đã kiểm tra với trình biên dịch và JVM thực tế:

~~~text
part=alpha
equalContent=true
length=5
prefix=prefix
empty=true
arrayFirst=X
partAfterArrayEdit=alpha
partAfterSourceReassign=alpha
builder=Mutable
snapshot=muta
utf16End=3
prefixCodePoints=2
~~~

Giải thích:

1. `substring(start, end)` lấy đúng ký tự giữa `:` và `|`, nên `part` là `"alpha"`. `part.equals(other)` đúng dù `other` được tạo qua constructor khác.
2. `source.substring(6, 6)` là chuỗi rỗng vì hai ranh giới bằng nhau. Đây là kết quả hợp lệ; không cần đoán có reuse instance `""` hay không.
3. Chỉnh `editableCopy[0]` thành `'X'` không thể làm `part` đổi nội dung vì `toCharArray` trả mảng mới.
4. Gán `source` sang `"reassigned"` không làm đổi `part`.
5. Đổi nội dung `StringBuilder` thành `"Mutable"` không làm chuỗi snapshot `"muta"` đổi theo.
6. `offsetByCodePoints(0, 2)` trả index UTF-16 `3` cho hai code point đầu của `"A😀B"`. `substring(0, 3)` tạo đoạn có hai code point, tránh cắt giữa surrogate pair.

Chú ý: chương trình **không in identity của `substring`**, vì identity không cần để chứng minh tất cả kết quả mong muốn ở trên.

## 7. Chọn đúng phép so sánh trong nghiệp vụ

Sai khi mục tiêu là so sánh nội dung:

~~~java
String input = getInput();
String token = input.substring(0, 3);

if (token == "ABC") {
    // Sai: phụ thuộc identity của token và literal.
}
~~~

*`getInput()` là hàm minh họa, không định nghĩa trong snippet; không phải chương trình chạy độc lập.*

Đúng:

~~~java
String input = "ABC-123";
String token = input.substring(0, 3);

if ("ABC".equals(token)) {
    System.out.println("Đúng mã ABC");
}
~~~

`"ABC".equals(token)` không phụ thuộc token có được intern hay có chung đối tượng với literal. Đặt literal bên trái cũng tránh `NullPointerException` nếu token có thể null, dù `substring` của một `String` không null tự nó sẽ trả về một `String`.

Nếu bài toán cần so sánh không phân biệt chữ hoa/chữ thường, cân nhắc `equalsIgnoreCase` theo ngữ nghĩa ứng dụng; đó là quyết định **so sánh giá trị**, không liên quan đến object identity. Nếu cần nhận diện chính xác một đối tượng được chia sẻ như một sentinel do ứng dụng sở hữu, `==` có thể có ý nghĩa, nhưng đó là một hợp đồng khác hẳn so với “hai chuỗi có cùng văn bản”.

### `intern()` có làm thay đổi quy tắc trên không?

`String.intern()` trả về một biểu diễn canonical trong string pool với cơ chế quy ước identity của chính API `intern`. Nhưng **không cần gọi `intern()` để so sánh văn bản**; `equals` hoạt động trực tiếp trên nội dung.

`intern` cũng không phải một mẹo chung để tiết kiệm bộ nhớ khi trích xuất: nó ảnh hưởng đến vòng đời/tài nguyên của string pool và phải được đánh giá trên kiểu dữ liệu, cardinality và tải thực tế. Đừng tạo phụ thuộc mới vào `intern` chỉ để khiến phép `==` tình cờ đúng.

## 8. Cẩn thận về bộ nhớ và hiệu năng

Một số đánh đổi không thể suy ra chỉ từ cú pháp `substring`:

| Câu hỏi thực tế | Điều có thể nói chắc | Cần làm gì nếu quan trọng? |
| --- | --- | --- |
| Trích xuất 100 ký tự có cấp phát mảng 100 ký tự không? | API không cam kết cách cấp phát | Kiểm tra JDK và allocation profiler |
| Giữ đoạn 10 ký tự có kéo dài vòng đời một buffer 1 MB không? | Tùy cơ chế lưu trữ và tham chiếu | Heap dump, theo dõi retained size |
| `substring(0, length())` có trả `this` không? | Giá trị bằng nguồn; identity không nên là điều kiện nghiệp vụ | Không branch theo `==` |
| `new String(existing)` có ép sao chép backing array không? | Constructor tạo một object `String` mới biểu diễn cùng nội dung, nhưng không hứa **deep copy** backing storage | Không dùng để “ép giải phóng buffer” một cách mù quáng |
| Dùng `toCharArray()` có tạo mảng mới không? | **Có** theo tài liệu API, với các UTF-16 code unit của chuỗi | Chỉ dùng khi thật sự cần mảng `char[]` độc lập và có thể sửa |
| Cắt chuỗi nhiều lần có nhanh hơn giữ offset không? | Tùy cách dùng, bộ nhớ, khả năng tối ưu của JVM | Benchmark với JDK, dữ liệu và tải thật |

Đừng nhầm “không phụ thuộc vào nội bộ” với “không quan tâm hiệu năng”. Nghiệp vụ nên thiết kế theo giá trị; còn tối ưu hiệu năng được quyết định bởi **bằng chứng đo lường**. Nếu phải tương tác với API yêu cầu một `char[]` có thể sửa, `toCharArray()` là cách có hợp đồng rõ ràng. Nếu chỉ cần nội dung chuỗi, thường không cần tạo bản sao thêm.

## 9. Lưu ý ranh giới Unicode khi trích xuất

Cách lưu bộ nhớ và tính bất biến không giúp sửa **chỉ số UTF-16 sai mục tiêu**. Chẳng hạn, `substring(0, 2)` trên `"A😀B"` cắt sau high surrogate, còn muốn lấy hai code point đầu phải chuyển biên bằng `s.offsetByCodePoints(0, 2)` (trả offset `3`) trước khi gọi `substring(0, 3)`.

Với dấu kết hợp hoặc emoji gồm nhiều code point, cần xét ranh giới grapheme cluster khi sản phẩm đòi “ký tự người dùng nhìn thấy”. Đó là vấn đề **chọn đúng dãy ký tự**, độc lập với identity và backing storage của kết quả.

## 10. Các lỗi tư duy cần tránh

1. **“Kết quả `substring` chắc chắn là object mới.”** Sai như một giả định có thể mang sang mọi JDK/cách gọi. Giá trị mới trích xuất là điều API quy định; identity là chuyện khác.
2. **“Hai chuỗi `==` nhau thì là cách duy nhất để biết nội dung bằng nhau.”** Sai. `==` so tham chiếu; `equals` so nội dung.
3. **“Hai chuỗi khác object thì chắc chắn không thể chia sẻ mảng.”** Không suy ra được backing storage từ identity hai `String`.
4. **“String bất biến nên JVM chắc chắn đã copy toàn bộ buffer.”** Tính bất biến cho phép nhiều chiến lược tối ưu khác nhau, kể cả dùng chung dữ liệu nội bộ an toàn.
5. **“`new String(s)` luôn ép backing array được sao chép sâu.”** Không có bảo đảm đó từ constructor nhận một `String`; ví dụ OpenJDK 17 có thể tái sử dụng biểu diễn nội bộ.
6. **“Thấy một bản JDK copy thì mọi JDK đều copy và chi phí không đổi.”** Chiến lược cấp phát và biểu diễn có thể thay đổi theo phiên bản/runtime.
7. **“Cắt từ index 0 đến 2 luôn là hai ký tự Unicode.”** Không đúng với code point bổ sung; chỉ số `substring` tính theo UTF-16 code unit.
8. **“Đổi biến nguồn sẽ làm đoạn trích xuất đổi theo.”** Phép gán lại biến nguồn không làm nội dung `String` cũ hay kết quả trích xuất thay đổi.
9. **“Chạy một lần thấy nhanh/chậm là đủ kết luận.”** Không. Benchmark cần warmup, tải đại diện, nhiều mẫu và quan sát bộ nhớ khi kết quả được giữ sống lâu.

## 11. Tự kiểm tra

**Câu 1:** Với `String s = "Java"; String t = s.substring(0, 4);`, phải kiểm tra `t.equals(s)` hay `t == s` nếu chỉ quan tâm kết quả có đúng nội dung không?

**Đáp án:** `t.equals(s)`. Việc `t` có là cùng instance `s` không liên quan đến tính đúng của kết quả.

**Câu 2:** Vì sao thay `char[] copy = extracted.toCharArray(); copy[0] = 'X';` không sửa `extracted`?

**Đáp án:** `toCharArray()` tạo mảng mới; `String` vẫn bất biến.

**Câu 3:** Nếu một JVM cũ dùng chung backing array giữa chuỗi nguồn dài và đoạn ngắn, ứng dụng nghiệp vụ có cần kiểm tra identity để bảo vệ nội dung không?

**Đáp án:** không. Dù cùng hay khác backing array, nội dung của `String` giữ nguyên. Điều có thể cần đánh giá riêng là **retained heap memory** trên JVM cũ đó.

**Câu 4:** `new String(existing)` có phải cách bảo đảm bản sao vật lý hoàn toàn độc lập của mảng bên trong không?

**Đáp án:** không. Constructor có thể tạo object mới cùng giá trị nhưng chiến lược backing storage là chi tiết triển khai.

**Câu 5:** Lấy hai code point đầu từ `"A😀B"` bằng cách nào?

**Đáp án:** `int end = s.offsetByCodePoints(0, 2); String first = s.substring(0, end);`. `end` bằng `3` tính theo UTF-16.

**Câu 6:** Khi hiệu năng trích xuất bất thường trên bản JDK cụ thể, nên sửa logic thành so sánh `==` hay làm gì?

**Đáp án:** giữ logic đúng theo giá trị; đo cấp phát, retained size và profiling/benchmark trên workload và JDK cụ thể để xác định tối ưu.

## 12. Tài liệu tham khảo

- [Java SE 17 — `String.substring`, `String.equals`, `String.toCharArray`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html): hợp đồng nội dung, biên chỉ số, tính bất biến và sao chép mảng `char[]`.
- [Java SE 17 — `StringBuilder.substring`, `setCharAt`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StringBuilder.html): kết quả `String` của chuỗi nguồn mutable.
- [OpenJDK 17 — mã nguồn `String.java`](https://github.com/openjdk/jdk17/blob/master/src/java.base/share/classes/java/lang/String.java): ví dụ chi tiết thực thi có thể trả `this` cho toàn chuỗi hoặc tạo biểu diễn kết quả cho đoạn con; chỉ dùng để tìm hiểu triển khai, không dùng thay hợp đồng API.
- [JEP 254 — Compact Strings](https://openjdk.org/jeps/254): lý do và kỹ thuật biểu diễn bộ nhớ `String` thay đổi trong OpenJDK.

**Ghi nhớ:** `substring` phải cho ra **đúng dãy ký tự yêu cầu**, còn identity của đối tượng và việc chia sẻ/copy bộ nhớ nội bộ không phải cơ sở thiết kế nghiệp vụ. So sánh bằng `equals`, dựa vào tính bất biến của `String`, dùng index Unicode phù hợp và chỉ kết luận về hiệu năng sau khi đo trên JDK thực tế.

</details>

- [Quay lại đầu trang](#back-to-top)
