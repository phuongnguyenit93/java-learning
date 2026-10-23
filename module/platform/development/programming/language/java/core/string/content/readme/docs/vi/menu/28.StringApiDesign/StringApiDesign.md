<a id="back-to-top"></a>

# Thiết kế API

## Menu
- [Chọn String hay CharSequence cho tham số](#string-api-design-001)
- [CharSequence không mặc nhiên bất biến hoặc có equals theo nội dung](#string-api-design-002)
- [Quy định rõ cách xử lý null, rỗng và trắng](#string-api-design-003)
- [Quy định đơn vị chỉ số hoặc giới hạn độ dài](#string-api-design-004)
- [Tách dữ liệu nguyên bản, dữ liệu chuẩn hóa và bản hiển thị](#string-api-design-005)
- [Phân biệt văn bản thông thường với cấu trúc có ngữ pháp](#string-api-design-006)
- [Dùng bộ xử lý đúng định dạng cho JSON, CSV, đường dẫn hoặc URI](#string-api-design-007)
- [Giữ trách nhiệm mã hóa và escape tại đúng ranh giới](#string-api-design-008)

## <a id="string-api-design-001">Chọn String hay CharSequence cho tham số</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn `String` hay `CharSequence` cho tham số API Java

## 1. Nguyên tắc: chọn theo hợp đồng mà phương thức cần

Java có hai cách quen thuộc để mô tả tham số văn bản: `String` là một lớp cụ thể, nội dung **bất biến**; `CharSequence` là một **interface** mô tả dãy ký tự có thể đọc. `String implements CharSequence`, nên bất kỳ đối tượng `String` nào cũng có thể truyền cho tham số `CharSequence`.

**Nên dùng `CharSequence`** khi phương thức chỉ cần duyệt, đếm hoặc phân tích các ký tự và có thể làm đúng với nhiều loại nguồn như `String`, `StringBuilder`, `StringBuffer`, `CharBuffer`. **Nên dùng `String`** khi phương thức thật sự cần một giá trị bất biến, API bên trong yêu cầu `String`, cần lưu trạng thái lâu dài hoặc dùng nhiều thao tác riêng của lớp `String`.

Ví dụ ý định hai API:

```java
// Chỉ đọc ký tự ngay trong lời gọi:
int countAsciiDigits(CharSequence text);

// Lưu lại nhãn ở dạng giá trị bất biến:
void setPermanentLabel(String label);
```

Đây là các chữ ký minh họa, không phải chương trình độc lập. Quyết định nằm ở **ngữ nghĩa sử dụng**, không đơn thuần là chọn kiểu tổng quát nhất. `CharSequence` không hứa bất biến, không tự mang lại an toàn đa luồng và cũng không mặc nhiên tránh việc sao chép dữ liệu.

## 2. So sánh đặc điểm quan trọng

| Tiêu chí | `String` | `CharSequence` |
| --- | --- | --- |
| Loại | Lớp `final` cụ thể. | Interface cho dãy ký tự. |
| Dữ liệu đầu vào | Giá trị `String` (hoặc `null` nếu API cho phép). | `String`, `StringBuilder`, `StringBuffer`, `CharBuffer`, triển khai riêng. |
| Bất biến | Nội dung đối tượng `String` không thay đổi. | **Không bảo đảm**: đối tượng thực tế có thể mutable. |
| Thao tác chung | `length()`, `charAt()`, `subSequence()`, `toString()`. | Các thao tác trên cùng, cùng `chars()`, `codePoints()` trong Java hiện đại. |
| Thao tác riêng của `String` | `strip()`, `startsWith()`, `substring()`, `replace()`, `split()`, ... | Không có trong hợp đồng của interface. |
| So sánh giá trị | `String.equals`/`hashCode` nhất quán theo nội dung với `String`. | Interface không áp đặt mọi triển khai `equals`/`hashCode` phải theo nội dung. |
| Lưu giá trị về sau | Phù hợp nhờ tính bất biến. | Phải xem xét biến đổi của nguồn, thường chụp `String` trước khi lưu. |
| Chi phí | Đã là `String`; không cần ép kiểu trước khi gọi API cần `String`. | Có thể đọc trực tiếp nhiều nguồn, nhưng đôi khi cần `toString()` và cấp phát. |

**Lưu ý:** Biến có kiểu `String` vẫn có thể được gán tham chiếu tới một `String` khác; chính **nội dung của từng đối tượng `String`** mới bất biến. Ngoài ra, tham số kiểu `CharSequence` không làm cho nguồn `StringBuilder` trở thành chỉ đọc đối với các phần mã khác vẫn giữ tham chiếu tới nó.

## 3. Ví dụ Java 17+ hoàn chỉnh

Tạo file `CharSequenceApiDemo.java`:

```java
import java.nio.CharBuffer;
import java.util.Locale;
import java.util.Objects;

public class CharSequenceApiDemo {
    // Phương thức chỉ cần đọc dãy ký tự theo interface.
    static int countAsciiDigits(CharSequence text) {
        Objects.requireNonNull(text, "text");
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= '0' && ch <= '9') {
                count++;
            }
        }
        return count;
    }

    // Chuyển sang String khi cần một giá trị ổn định để xử lý.
    static String normalizedCode(CharSequence text) {
        String stable = Objects.requireNonNull(text, "text").toString();
        return stable.strip().toUpperCase(Locale.ROOT);
    }

    public static void main(String[] args) {
        String fixed = "A7B";
        StringBuilder builder = new StringBuilder("x2");
        CharBuffer buffer = CharBuffer.wrap("p45");

        System.out.println("String: " + countAsciiDigits(fixed));
        System.out.println("StringBuilder: " + countAsciiDigits(builder));
        System.out.println("CharBuffer: " + countAsciiDigits(buffer));

        String captured = normalizedCode(builder);
        builder.append("9");
        System.out.println("Captured: " + captured);
        System.out.println("Current: " + normalizedCode(builder));

        System.out.println("Content equals: "
                + "key".contentEquals(new StringBuilder("key")));
        System.out.println("Object equals: "
                + Objects.equals("key", new StringBuilder("key")));
    }
}
```

Biên dịch và chạy:

```bash
javac CharSequenceApiDemo.java
java CharSequenceApiDemo
```

Đầu ra:

```text
String: 1
StringBuilder: 1
CharBuffer: 2
Captured: X2
Current: X29
Content equals: true
Object equals: false
```

`countAsciiDigits` nhận ba kiểu văn bản và chỉ đọc `length()`, `charAt()`. `normalizedCode` chụp dữ liệu thành `String` trước khi gọi `strip()` và `toUpperCase(Locale.ROOT)`; chuỗi `captured` giữ nguyên `"X2"` dù bên gọi sửa `StringBuilder` thành `"x29"` sau đó. Đây là minh họa khác biệt giữa **nhận tham số rộng** và **giữ giá trị bất biến**.

## 4. Trường hợp `CharSequence` thích hợp hơn

Một phương thức **xử lý đồng bộ ngay trong lời gọi**, chỉ dùng `length()`, `charAt()` và `subSequence()` để đọc, thường có thể nhận `CharSequence`. API linh hoạt hơn cho người gọi đang giữ dữ liệu trong `StringBuilder` hoặc `CharBuffer`.

Chẳng hạn:

```java
static boolean containsAtSign(CharSequence input) {
    Objects.requireNonNull(input, "input");
    for (int i = 0; i < input.length(); i++) {
        if (input.charAt(i) == '@') {
            return true;
        }
    }
    return false;
}
```

Đây là một hàm **minh họa**, không phải file chạy độc lập, và kiểm tra dấu `@` không đồng nghĩa xác thực email. Kiểu `CharSequence` nói đúng điều thuật toán cần: đọc từng đơn vị ký tự.

Các API có sẵn của Java cũng thể hiện cách thiết kế này: `Pattern.matcher(CharSequence)` chấp nhận nguồn đầu vào trừu tượng để khớp regex; `Appendable.append(CharSequence)` chấp nhận dữ liệu văn bản tổng quát. Không nhất thiết mọi thao tác văn bản đều phải yêu cầu `String`.

**Giới hạn cần nói rõ:** Nếu `StringBuilder` bị một luồng khác sửa đồng thời lúc hàm gọi `length()` hoặc `charAt()`, kết quả có thể không nhất quán; thậm chí có thể gặp lỗi chỉ số nếu độ dài đổi. Interface này **không cung cấp** cơ chế đồng bộ. Hợp đồng của API cần yêu cầu caller không sửa trong khi đọc, đồng bộ phù hợp, hoặc snapshot thành `String`.

Việc nhận `CharSequence` cũng không tự động làm code hiệu quả hơn. Một `CharBuffer` có `position`/`limit` thay đổi vùng quan sát, các triển khai có chi phí đọc khác nhau, và thuật toán phụ thuộc `String` có thể phải gọi `toString()` về sau.

## 5. Trường hợp `String` thích hợp hơn

Nên chọn `String` nếu:

- **Lưu lâu dài:** lưu trường thành viên, cache, hàng đợi, job bất đồng bộ hoặc kết quả mà caller có thể giữ sau khi lời gọi kết thúc.
- **Giá trị phải ổn định:** validation, hashing và xử lý nghiệp vụ về sau phải dựa trên cùng nội dung đã nhận.
- **Dùng phương thức riêng `String`:** `strip()`, `replace()`, `split()`, `startsWith()` hoặc chuyển tiếp đến thư viện yêu cầu `String`.
- **So sánh hoặc lưu khóa theo nội dung:** `String.equals`, `hashCode` và tính bất biến của `String` giúp giá trị khóa có hành vi ổn định.
- **Hợp đồng public có chủ đích hẹp:** API chỉ hỗ trợ một giá trị text đã chuẩn hóa thành `String`, hoặc cần giữ tương thích phiên bản cũ.

Thậm chí có thể nhận `CharSequence` ở ranh giới công khai rồi chụp thành `String` ngay khi cần lưu:

```java
import java.util.Objects;

final class Label {
    private final String value;

    Label(CharSequence input) {
        this.value = Objects.requireNonNull(input, "input").toString();
    }

    String value() {
        return value;
    }
}
```

Đây là **đoạn minh họa một lớp**, không phải chương trình `main` độc lập. Sau khi constructor nhận `StringBuilder`, đối tượng `Label` giữ một giá trị `String` ổn định, không thay đổi theo `StringBuilder` nguồn. Nếu chỉ muốn cho phép `String` ngay từ đầu, khai báo tham số `String` cũng là thiết kế hợp lệ.

Nếu vừa **kiểm tra dữ liệu rồi lưu**, nên chụp `String` trước, kiểm tra trên **chính bản đã chụp** và lưu đúng bản đó. Cách kiểm tra trực tiếp một `CharSequence` mutable rồi về sau chụp/lưu có thể khiến giá trị đã kiểm tra khác giá trị được dùng.

## 6. Quy tắc ownership khi dùng \`CharSequence\`

\`CharSequence\` cho phép API đọc nhiều loại nguồn, nhưng không biến \`StringBuilder\` thành bất biến. Nếu chỉ đọc ngay trong lời gọi, tài liệu hóa yêu cầu nguồn không bị thay đổi đồng thời. Nếu cần giữ giá trị cho cache, hàng đợi hoặc xử lý bất đồng bộ, hãy **snapshot một lần** khi nhận:

~~~java
final class Label {
    private final String value;

    Label(CharSequence source) {
        this.value = java.util.Objects.requireNonNull(source, "source")
                .toString();
    }

    String value() { return value; }
}
~~~

Bản \`String\` đã chụp không đổi sau khi bên gọi sửa nguồn. Tuy nhiên việc gọi \`toString()\` trong khi một nguồn mutable **đang bị sửa đồng thời** không mặc nhiên là snapshot nguyên tử; cần thỏa thuận chuyển giao quyền sở hữu hoặc đồng bộ hóa. \`subSequence()\` cũng có thể là view chia sẻ dữ liệu tùy triển khai. Khi API trả view, phải công bố vòng đời và cơ chế cập nhật.

## 7. So sánh nội dung cần ngữ nghĩa được chỉ định

\`String.equals(Object)\` so sánh nội dung với \`String\`, nhưng các implementation của \`CharSequence\` không có quy tắc \`equals/hashCode\` thống nhất. Khi chỉ muốn so sánh văn bản hiện tại:

~~~java
CharSequence candidate = new StringBuilder("key");
System.out.println("key".contentEquals(candidate)); // true
System.out.println("key".equals(candidate));        // false
~~~

Với khóa lưu dài hạn, dùng \`String\` snapshot và \`Map<String, ...>\` khi nghiệp vụ muốn equality theo văn bản. Không giả định \`HashMap<CharSequence, ...>\` sẽ gộp các kiểu nguồn cùng ký tự hoặc bảo đảm khóa ổn định. Phân tích chi tiết \`StringBuilder\`, \`StringBuffer\`, \`CharBuffer\` và \`subSequence\` thuộc bài chuyên sâu ngay sau bài chọn kiểu tham số.

## 8. Unicode và đơn vị đếm ký tự

`String` và `CharSequence` trong Java chủ yếu biểu diễn dãy **đơn vị mã UTF-16**:

- `length()` đếm số đơn vị UTF-16, không nhất thiết là số Unicode code point.
- `charAt(i)` trả một `char` 16-bit; code point nằm ngoài BMP có thể cần một **cặp surrogate** và chiếm hai vị trí.
- `codePoints()` có thể duyệt các code point, nhưng “ký tự” người dùng nhìn thấy (*grapheme cluster*) có thể gồm nhiều code point kết hợp.

Ví dụ Java trong một phương thức:

```java
String emoji = "😀";
System.out.println(emoji.length()); // 2
System.out.println(emoji.codePointCount(0, emoji.length())); // 1
```

Nếu API cần đếm “10 ký tự người dùng nhìn thấy”, cả `String.length()` lẫn `CharSequence.length()` đều chưa đủ cho mọi Unicode; cần thuật toán hoặc thư viện phân đoạn grapheme phù hợp. Việc chọn `String` hay `CharSequence` không tự quyết định đơn vị đếm cho nghiệp vụ.

## 9. Tương thích khi sửa chữ ký API đã phát hành

Giả sử thư viện hiện có:

```java
public void submit(String text) {
    // Logic đang tồn tại.
}
```

Đổi thẳng thành `submit(CharSequence text)` có thể vẫn cho phép **mã nguồn gọi bằng `String`** biên dịch lại vì `String implements CharSequence`. Nhưng chữ ký ở mức **JVM bytecode descriptor** thay đổi: chương trình đã biên dịch trước đó tìm `submit(String)` có thể không chạy với phiên bản thư viện chỉ còn `submit(CharSequence)` và gặp `NoSuchMethodError`.

Để xem xét tương thích nhị phân, có thể giữ phương thức cũ và **thêm overload**:

```java
public void submit(String text) {
    // Giữ hành vi hiện có.
}

public void submit(CharSequence text) {
    submit(Objects.requireNonNull(text, "text").toString());
}
```

Đây là đoạn minh họa trong lớp với `import java.util.Objects;`. Khi có cả hai overload, lời gọi bằng biến có kiểu biên dịch `String` chọn overload `String` do cụ thể hơn. Các overload khác có thể gây mơ hồ hoặc đổi cách phân giải lời gọi; cần thử API từ góc nhìn caller. Thêm overload cũng tăng việc phải duy trì nhất quán hành vi. Nếu API cũ vốn đáp ứng nhu cầu, giữ `String` có thể đơn giản hơn.

## 10. Xử lý `null`, hiệu năng và hợp đồng API

**Chính sách `null`:** cả `String` lẫn `CharSequence` là kiểu tham chiếu, nên đều có thể nhận `null`. Chọn rõ trường hợp hợp lệ/không hợp lệ và biểu diễn bằng tài liệu, annotation phù hợp hoặc `Objects.requireNonNull(text, "text")` nếu yêu cầu bắt buộc.

**Không giả định `toString()` miễn phí:** Với `String`, `toString()` trả chính đối tượng; với `StringBuilder` và các nguồn khác, nó có thể tạo/copy chuỗi mới. Nếu chỉ duyệt ký tự ngắn hạn và nguồn không đổi trong lúc gọi, đọc `CharSequence` trực tiếp có thể tránh chuyển đổi không cần thiết. Nếu tính ổn định quan trọng, tạo snapshot là chi phí có chủ đích.

**Không lưu nguyên tham chiếu mutable khi xử lý bất đồng bộ:** Trước khi đưa dữ liệu vào thread/task/queue cần xác định ai sở hữu nguồn và khi nào nguồn có thể bị sửa. Chụp `String` tại ranh giới nếu cần giữ đúng nội dung đã nhận. Đừng dựa vào việc kiểu tham số `CharSequence` có vẻ chỉ đọc để suy ra thread-safe.

**Không ràng buộc mọi API vào interface bằng mọi giá:** Một thư viện nên chọn kiểu tham số phản ánh đúng mô hình dữ liệu. Nếu mọi bước của phương thức đều cần `String` và caller đã có `String`, tham số `String` là lựa chọn rõ nghĩa; nếu thuật toán chỉ cần duyệt ký tự của nhiều nguồn, `CharSequence` là lựa chọn tự nhiên.

Một checklist quyết định:

| Câu hỏi | Gợi ý |
| --- | --- |
| Chỉ đọc ký tự ngay trong phương thức, không giữ nguồn? | Xem xét `CharSequence`. |
| Cần nhận trực tiếp `StringBuilder`, `CharBuffer`? | `CharSequence` đem lại tính linh hoạt. |
| Cần lưu và sử dụng sau? | Dùng `String` hoặc nhận `CharSequence` rồi snapshot. |
| Cần phương thức chỉ `String` mới có? | Chọn `String` hoặc chuyển đổi ở ranh giới rõ ràng. |
| Cần khóa theo nội dung? | `String` có equality/hash và tính bất biến phù hợp. |
| Thay chữ ký public của thư viện? | Xem xét source/binary compatibility trước. |

## 11. Câu hỏi thực hành

1. Vì sao `countAsciiDigits(CharSequence)` nhận được `StringBuilder` mà không cần caller đổi thành `String`? Trong trường hợp nào vẫn phải lưu ý thay đổi đồng thời nguồn?
2. Sau khi `normalizedCode(builder)` trả `"X2"`, vì sao `builder.append("9")` không sửa `captured`?
3. Vì sao `String.contentEquals(CharSequence)` có thể cho kết quả khác `String.equals(Object)` với `StringBuilder`?
4. Khi tạo job bất đồng bộ nhận `CharSequence`, khi nào nên chụp `String` để job xử lý đúng dữ liệu tại thời điểm enqueue?
5. Nếu đổi `submit(String)` thành `submit(CharSequence)`, vì sao một ứng dụng đã compile với phiên bản cũ có thể lỗi mặc dù lời gọi trong mã nguồn vẫn hợp lệ?
6. Vì sao `length()` không luôn bằng số Unicode code point hoặc số ký tự người dùng nhìn thấy?

**Kết luận:** Dùng `CharSequence` khi hợp đồng chỉ cần **đọc dãy ký tự** và có lợi khi hỗ trợ nhiều nguồn văn bản. Dùng `String` khi cần **giá trị bất biến, so sánh/lưu trữ ổn định hoặc các API chuyên biệt**. Khi nhận `CharSequence` mà cần giữ lâu, hãy chụp một `String` tại ranh giới phù hợp và xử lý quyền sở hữu, đồng bộ nguồn cho đúng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-api-design-002">CharSequence không mặc nhiên bất biến hoặc có equals theo nội dung</a>

<details>
<summary>Xem chi tiết</summary>

# CharSequence không mặc nhiên bất biến hoặc có equals theo nội dung

## 1. Vấn đề trong thiết kế API

Khi viết một API thao tác chuỗi, ta dễ chọn tham số kiểu `CharSequence` thay vì `String` để chấp nhận được nhiều loại dữ liệu. Ví dụ, một phương thức nhận `CharSequence` có thể được gọi với `String`, `StringBuilder`, `StringBuffer` hay `CharBuffer`. Đây là một điểm mạnh của thiết kế theo giao diện, nhưng **`CharSequence` không hứa rằng đối tượng là bất biến hoặc `equals` so sánh nội dung**.

Hai điều cần nhớ từ đầu:

- **Bất biến:** `String` là immutable; `StringBuilder` và `StringBuffer` có thể thay đổi, còn `CharBuffer` có thể là một view có vị trí, giới hạn và vùng lưu trữ thay đổi. Tham số khai báo là `CharSequence` không đóng băng đối tượng bên dưới.
- **Bình đẳng:** giao diện `CharSequence` không bắt buộc các implementation cài `equals`/`hashCode` theo một quy tắc bình đẳng chung. Hai đối tượng có dãy `char` giống nhau có thể trả `equals == false`; hành vi của `equals` còn tùy loại đối tượng cụ thể.

Vì thế, khi API phải **lưu lâu**, **chia sẻ giữa luồng**, **dùng làm khóa map/set**, **cache**, hoặc **kiểm tra bình đẳng theo văn bản**, cần xác định hợp đồng dữ liệu rõ ràng và thường phải chụp một bản `String` bất biến tại ranh giới API.

## 2. Giao diện CharSequence thực sự bảo đảm gì?

`java.lang.CharSequence` biểu diễn **một dãy `char` có thể đọc**. Những thao tác cơ bản gồm:

| Phương thức | Ý nghĩa |
| --- | --- |
| `length()` | Số lượng UTF-16 code unit trong dãy hiện tại |
| `charAt(index)` | Đọc UTF-16 code unit tại vị trí tương ứng |
| `subSequence(start, end)` | Lấy một `CharSequence` đại diện cho đoạn con |
| `toString()` | Lấy nội dung dãy dưới dạng `String` |
| `chars()` và `codePoints()` | Duyệt qua UTF-16 code unit hoặc Unicode code point bằng `IntStream` |

**Không có** lời hứa tổng quát rằng `length()`, `charAt()` hoặc kết quả `subSequence()` luôn bất biến theo thời gian. `subSequence()` có thể trả một **view chia sẻ dữ liệu** với đối tượng gốc; loại khác có thể trả bản sao. API sử dụng `CharSequence` phải xem xét điều đó.

`length()` đếm **đơn vị UTF-16**, không đếm số ký tự hiển thị hay Unicode code point. Emoji bổ sung, ví dụ 🚀 (U+1F680), cần hai UTF-16 code unit. Vì vậy chuỗi `"A🚀"` có `length() == 3` nhưng `codePoints().count() == 2`. Khi xử lý Unicode, đừng coi mỗi `charAt(i)` là một ký tự Unicode hoàn chỉnh.

## 3. Bốn implementation thường gặp

| Implementation | Nội dung có thể đổi? | Bình đẳng `equals` mặc định của loại đó | Lưu ý khi đưa vào API |
| --- | --- | --- | --- |
| `String` | Không; giá trị String bất biến | Theo **nội dung**, giữa hai đối tượng `String` | Hợp lý để lưu trữ và làm khóa bất biến |
| `StringBuilder` | Có: `append`, `setCharAt`, `delete`… | Theo **danh tính đối tượng** vì không override `Object.equals` | Hai builder có cùng ký tự vẫn `equals == false` |
| `StringBuffer` | Có; các thao tác riêng lẻ được đồng bộ hóa | Theo **danh tính đối tượng**, không so sánh nội dung | Đồng bộ hóa từng lời gọi không biến nó thành immutable hay bảo đảm snapshot qua nhiều lời gọi |
| `CharBuffer` | Có thể thay đổi nội dung hoặc trạng thái vị trí/giới hạn; cũng có thể là buffer chỉ đọc | Theo nội dung **các ký tự còn lại** giữa `position` và `limit`, khi đối số cũng là `CharBuffer` | `equals`/`hashCode` phụ thuộc trạng thái buffer và có thể thay đổi |

Một `CharBuffer` chỉ đọc vẫn có thể thay đổi **position** của chính nó (trừ khi bên gọi không làm thế); bất biến nội dung vùng nhớ và bất biến đối tượng không phải một khái niệm. Thay đổi vị trí có thể làm dãy “còn lại” ngắn đi, khiến `toString()`, `equals` và `hashCode` thay đổi dù các ký tự ở vùng lưu trữ không đổi.

Đặc biệt, `StringBuilder` và `StringBuffer` không cài một quy tắc `equals` so sánh nội dung như `String`. Suy luận “cùng implements CharSequence nên có thể so sánh nội dung bằng equals” là sai.

## 4. Ví dụ: cùng nội dung nhưng equals khác nhau

Đây là đoạn mã minh họa đặt bên trong một phương thức:

~~~java
String a = "cat";
StringBuilder b = new StringBuilder("cat");
StringBuffer c = new StringBuffer("cat");

System.out.println(a.equals(b));                   // false
System.out.println(a.contentEquals(b));            // true
System.out.println(b.equals(new StringBuilder("cat"))); // false
System.out.println(c.equals(new StringBuffer("cat")));  // false
~~~

Giải thích:

1. `String.equals(Object)` chỉ cho kết quả true khi đối tượng khác cũng là `String` có cùng ký tự. `StringBuilder` không được coi là cùng kiểu.
2. `String.contentEquals(CharSequence)` chủ ý kiểm tra **các ký tự trong CharSequence hiện tại** so với chuỗi String. Nó thích hợp khi một phía có kiểu `String` và mục đích thực sự là so sánh văn bản.
3. `StringBuilder.equals` và `StringBuffer.equals` kế thừa ngữ nghĩa bình đẳng theo **tham chiếu** từ `Object`; hai instance khác nhau không trở thành bằng nhau vì ký tự giống nhau.
4. `Objects.equals(a, b)` chỉ xử lý an toàn trường hợp null và sau đó gọi `a.equals(b)`; nó **không biến** phép equals thành so sánh nội dung xuyên implementation.

`CharBuffer.wrap("cat").equals(CharBuffer.wrap("cat"))` cho `true` vì hai buffer có cùng dãy ký tự còn lại. Nhưng `"cat".equals(CharBuffer.wrap("cat"))` vẫn là false. Do đó không thể viết một quy tắc “CharSequence.equals luôn theo nội dung” áp dụng cho mọi implementation.

### Khi cần so sánh theo văn bản

Nếu một phía đã là `String`, có thể sử dụng `expected.contentEquals(actual)` khi `actual` là `CharSequence` không null. Nếu cần so sánh nhất quán ở biên API, tạo snapshot rõ ràng:

~~~java
static boolean sameText(CharSequence first, CharSequence second) {
    java.util.Objects.requireNonNull(first, "first");
    java.util.Objects.requireNonNull(second, "second");
    return first.toString().equals(second.toString());
}
~~~

Đây là **phương thức minh họa**, không phải một chương trình độc lập. Nó định nghĩa quy tắc so sánh bằng nội dung `String` thu được lúc gọi; không áp đặt kiểu `equals` của từng implementation. Giả định quan trọng là nội dung nguồn ổn định trong khi hai snapshot được lấy. Nếu đối tượng bị thay đổi đồng thời, phải đồng bộ hóa theo hợp đồng ứng dụng.

## 5. Mutability: giữ tham chiếu không có nghĩa là giữ nội dung

Xét ví dụ:

~~~java
StringBuilder builder = new StringBuilder("cat");
CharSequence savedReference = builder;
String savedSnapshot = builder.toString();

builder.append("s");

System.out.println(savedReference); // cats
System.out.println(savedSnapshot);  // cat
~~~

`savedReference` vẫn trỏ tới **chính builder mutable**, nên đọc sau khi sửa sẽ thấy `"cats"`. `savedSnapshot` là một `String` đã được lấy từ giá trị `"cat"` trước khi sửa, nên không đổi. Tính bất biến đến từ **đối tượng được giữ**, không đến từ kiểu biến dùng để tham chiếu nó.

Điều này ảnh hưởng đến các API kiểu:

~~~java
final class NoteStore {
    private CharSequence title;

    void setTitle(CharSequence title) {
        this.title = title; // Giữ tham chiếu tới nguồn mutable
    }

    CharSequence getTitle() {
        return title;
    }
}
~~~

Đoạn `NoteStore` là **mẫu phản ví dụ**: bên gọi có thể truyền `StringBuilder`, rồi chỉnh builder sau khi lưu. Khi đó `getTitle()` trả nội dung khác lúc gọi `setTitle` mà `NoteStore` không hề sửa gì.

Thiết kế an toàn hơn nếu hợp đồng muốn giữ “giá trị tại thời điểm truyền vào”:

~~~java
final class StableNoteStore {
    private String title;

    void setTitle(CharSequence value) {
        title = java.util.Objects.requireNonNull(value, "value").toString();
    }

    String getTitle() {
        return title;
    }
}
~~~

Dùng `String` cho trạng thái lưu trữ giúp xác định rõ đây là **giá trị bất biến**, chứ không phải một dãy ký tự có thể đổi. Lưu ý: `toString()` lấy nội dung hiện có vào `String`; nếu implementation bị biến đổi đồng thời trong lúc snapshot, cần cơ chế khóa hoặc thỏa thuận ownership phù hợp. Không có một lệnh `toString()` tổng quát bảo đảm mọi implementation đều thread-safe.

## 6. subSequence có thể là view sống

Không nên cho rằng gọi `subSequence` luôn tạo ra một bản sao bất biến. `CharBuffer` là minh họa thực tế:

~~~java
char[] data = {'c', 'a', 't'};
java.nio.CharBuffer buffer = java.nio.CharBuffer.wrap(data);
CharSequence tail = buffer.subSequence(1, 3);
String stableTail = tail.toString();

data[1] = 'o';

System.out.println(tail);       // ot
System.out.println(stableTail); // at
~~~

`tail` là một view liên kết với mảng ký tự `data`. Thay đổi mảng nền từ `a` sang `o` được phản ánh trong view, còn `stableTail` vẫn giữ nội dung cũ.

**Kết luận về thiết kế:** trả về `CharSequence` từ một API có thể là hợp lý khi chủ đích cung cấp view, nhưng người dùng cần biết **ai sở hữu bộ đệm**, **view sống được bao lâu**, **ai được phép thay đổi**, và **khi nào cần snapshot**. Nếu yêu cầu trả về một giá trị ổn định lâu dài, trả một `String` thường đơn giản hơn.

## 7. Bẫy khi dùng CharSequence làm khóa collection

Khi làm khóa trong `HashMap`/`HashSet`, đối tượng cần thỏa mãn quy tắc `equals` và `hashCode` ổn định trong lúc còn nằm trong collection.

- Một khóa `String` bất biến có `equals`/`hashCode` theo nội dung rõ ràng, phù hợp để làm khóa.
- Một khóa `StringBuilder` so sánh theo danh tính đối tượng; dù có cùng văn bản, hai builder riêng là **hai khóa khác nhau**. Thay đổi builder không làm `Object.hashCode` trở thành phép băm nội dung.
- Một khóa `CharBuffer` có `equals`/`hashCode` dựa trên phần ký tự còn lại. Việc thay `position`, `limit` hoặc nội dung nền có thể làm thay đổi ngữ nghĩa bình đẳng và giá trị hash trong lúc đối tượng đang là khóa. Khi đó việc tra cứu dựa trên bucket cũ có thể hoạt động sai kỳ vọng.

Ví dụ minh họa:

~~~java
java.util.Set<CharSequence> keys = new java.util.HashSet<>();
keys.add("cat");
keys.add(new StringBuilder("cat"));
keys.add(java.nio.CharBuffer.wrap("cat"));

System.out.println(keys.size()); // 3
~~~

Ba đối tượng trên có cùng văn bản hiển thị nhưng mang các kiểu `equals` khác nhau, nên đều có thể tồn tại riêng. **Không dùng `CharSequence` làm khóa khi định nghĩa khóa nghiệp vụ thực chất là “cùng nội dung văn bản” mà chưa chuẩn hóa hoặc snapshot.** Hãy lấy `String normalizedKey = incoming.toString()` trước khi đưa vào `Map<String, ...>`/`Set<String>`, nếu muốn bình đẳng đúng theo `String.equals`.

Đừng viết kiểm thử kiểu “sửa CharBuffer xong HashMap.get luôn trả null”: kết quả có thể phụ thuộc hash bucket và trạng thái cụ thể. Thay vào đó, kiểm tra trực tiếp rằng `equals`/`hashCode` của buffer có thể đổi theo `position` và áp dụng quy tắc **không biến đổi khóa ảnh hưởng đến băm**.

## 8. Chương trình Java 17+ hoàn chỉnh

Tệp **CharSequenceApiDemo.java** bên dưới kiểm tra trực tiếp các khác biệt vừa thảo luận: `String.equals`, `contentEquals`, identity equality, bình đẳng `CharBuffer`, mutable builder, mutable view, snapshot, Unicode và null.

~~~java
import java.nio.CharBuffer;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class CharSequenceApiDemo {
    private static int passed;

    private static void check(String name, boolean ok) {
        if (!ok) {
            throw new AssertionError("FAILED: " + name);
        }
        passed++;
    }

    static String snapshot(CharSequence text) {
        return Objects.requireNonNull(text, "text").toString();
    }

    public static void main(String[] args) {
        String text = "cat";
        StringBuilder builder = new StringBuilder("cat");
        StringBuffer buffer = new StringBuffer("cat");
        CharBuffer charBuffer1 = CharBuffer.wrap("cat");
        CharBuffer charBuffer2 = CharBuffer.wrap("cat");

        check("String.equals checks type", !text.equals(builder));
        check("String.contentEquals", text.contentEquals(builder));
        check("Objects.equals not content comparison",
                !Objects.equals(text, builder));
        check("StringBuilder identity equality",
                !builder.equals(new StringBuilder("cat")));
        check("StringBuffer identity equality",
                !buffer.equals(new StringBuffer("cat")));
        check("CharBuffer content equality",
                charBuffer1.equals(charBuffer2));
        check("CharBuffer versus String",
                !charBuffer1.equals(text) && !text.equals(charBuffer1));
        check("equal CharBuffers have same hash",
                charBuffer1.hashCode() == charBuffer2.hashCode());

        Set<CharSequence> mixed = new HashSet<>();
        mixed.add(text);
        mixed.add(builder);
        mixed.add(charBuffer1);
        check("mixed set has three keys", mixed.size() == 3);

        String fixed = snapshot(builder);
        builder.append("s");
        check("snapshot is immutable", fixed.equals("cat"));
        check("builder changes", builder.toString().equals("cats"));

        charBuffer1.position(1);
        check("CharBuffer remaining content changed",
                charBuffer1.toString().equals("at"));
        check("CharBuffer equality changed",
                !charBuffer1.equals(charBuffer2));
        check("CharBuffer hash changed",
                charBuffer1.hashCode() != charBuffer2.hashCode());

        char[] array = {'c', 'a', 't'};
        CharBuffer view = CharBuffer.wrap(array);
        CharSequence sub = view.subSequence(1, 3);
        check("initial subSequence", sub.toString().equals("at"));
        String subCopy = snapshot(sub);
        array[1] = 'o';
        check("backed view reflects mutation", sub.toString().equals("ot"));
        check("snapshot of view is stable", subCopy.equals("at"));

        CharSequence unicode = new StringBuilder("A\uD83D\uDE80");
        check("UTF-16 units differ from code points",
                unicode.length() == 3 && unicode.codePoints().count() == 2);
        check("null rejection", rejectsNull());

        System.out.println("String.equals(StringBuilder)="
                + text.equals(new StringBuilder("cat")));
        System.out.println("String.contentEquals(StringBuilder)="
                + text.contentEquals(new StringBuilder("cat")));
        System.out.println("HashSet.size=" + mixed.size());
        System.out.println("BuilderSnapshot=" + fixed);
        System.out.println("BuilderNow=" + builder);
        System.out.println("CharBufferAfterPosition=" + charBuffer1);
        System.out.println("ViewAfterArrayChange=" + sub);
        System.out.println("ViewSnapshot=" + subCopy);
        System.out.println("PASS: " + passed + " checks");
    }

    static boolean rejectsNull() {
        try {
            snapshot(null);
            return false;
        } catch (NullPointerException expected) {
            return true;
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 CharSequenceApiDemo.java
java CharSequenceApiDemo
~~~

Stdout chính xác nếu 19 kiểm tra thành công:

~~~text
String.equals(StringBuilder)=false
String.contentEquals(StringBuilder)=true
HashSet.size=3
BuilderSnapshot=cat
BuilderNow=cats
CharBufferAfterPosition=at
ViewAfterArrayChange=ot
ViewSnapshot=at
PASS: 19 checks
~~~

Hàm `check` ném `AssertionError` nếu bất kỳ trường hợp nào sai, nên không có dòng `PASS` giả khi kết quả không đúng. Không dùng `assert` của Java, vì `assert` bị vô hiệu hóa theo mặc định nếu không bật `-ea`.

Điểm đáng chú ý trong ví dụ: `CharBuffer.equals` và `hashCode` phản ánh phần văn bản còn lại từ `position` tới `limit`; sau `charBuffer1.position(1)`, dãy hiện tại của nó đổi từ `"cat"` thành `"at"` mà mảng ký tự ban đầu không nhất thiết thay đổi. Ngược lại, bản `String` trả từ `snapshot` vẫn chứa giá trị trước lúc đối tượng được sửa.

## 9. Cách chọn tham số và kiểu trả về

| Mục đích API | Gợi ý kiểu | Cách xử lý |
| --- | --- | --- |
| Đọc đầu vào ngay trong lời gọi, không giữ lại | `CharSequence` | Có thể chấp nhận nhiều nguồn dữ liệu; ghi rõ yêu cầu nguồn không bị sửa trong lúc xử lý |
| Lưu text, cache hoặc chuyển sang xử lý bất đồng bộ | Nhận `CharSequence` nếu cần linh hoạt; **lưu `String`** | Lấy snapshot một lần tại ranh giới ownership |
| Dùng khóa Map/Set theo nội dung văn bản | `String` | Chuẩn hóa nếu cần, rồi lưu/so sánh bằng `String.equals` và `hashCode` |
| Trả về một giá trị text ổn định | `String` | Bên nhận biết đây là giá trị bất biến |
| Trả về view có thể phản ánh nguồn | `CharSequence` / `CharBuffer` | Tài liệu hóa backing storage, vòng đời, position/limit và quy tắc cập nhật |
| Sắp xếp theo ký tự | `Comparator` được xác định rõ | Không tự đồng nhất `compare == 0` với `equals` của mọi CharSequence |

Nếu API muốn **giữ nguyên cả kiểu dữ liệu mutable bên ngoài**, phải ghi rõ quy định thay đổi, thread-safety và thời gian sống của dữ liệu. Nếu API cần **giá trị snapshot độc lập**, đầu vào linh hoạt và nội bộ immutable là lựa chọn dễ chứng minh tính đúng hơn.

### Null và hợp đồng đầu vào

Một tham số `CharSequence` vẫn có thể là `null`. Hãy quyết định null **bị từ chối**, **là chuỗi rỗng**, hay **được xử lý theo chính sách riêng**. Ví dụ từ chối rõ ràng:

~~~java
static String snapshot(CharSequence input) {
    return java.util.Objects.requireNonNull(input, "input").toString();
}
~~~

Đây là đoạn phương thức minh họa có phiên bản chạy trong chương trình ở trên. Tránh tùy tiện gọi `String.valueOf((Object) input)` khi null có ý nghĩa lỗi: nó chuyển null thành văn bản `"null"`, có thể che giấu sai dữ liệu.

### Concurrency và ownership

`StringBuilder` không thread-safe. `StringBuffer` đồng bộ hóa nhiều phương thức riêng lẻ, nhưng một quy trình gồm `length()`, rồi lặp `charAt()`, hoặc ghép nhiều lời gọi không được bảo đảm là một snapshot nguyên tử nếu luồng khác cùng sửa dữ liệu. `CharBuffer` cũng có trạng thái mutable. Nếu nhiều luồng có thể thay đổi đầu vào, cần **khóa hợp tác**, **chuyển giao ownership**, hoặc **bản sao ổn định được tạo trước khi chia sẻ**.

Ngay cả khi API khai báo `CharSequence` như tham số `final`, từ khóa `final` chỉ ngăn **gán lại biến tham chiếu** trong phương thức; nó không làm đối tượng bất biến.

## 10. Ma trận kiểm thử cho API nhận CharSequence

| Trường hợp | Kiểm thử cần có |
| --- | --- |
| Hai `String` cùng nội dung | `equals` và `hashCode` giống nhau |
| `String` và `StringBuilder` cùng nội dung | `String.equals` trả false; phép so sánh nội dung tường minh trả true |
| Hai `StringBuilder` cùng nội dung | Không được giả định chúng `equals` |
| Hai `StringBuffer` cùng nội dung | Không được giả định chúng `equals` |
| Hai `CharBuffer` có cùng nội dung còn lại | So sánh theo nội dung còn lại |
| `CharBuffer` đổi `position` | Nội dung logic, `equals` và `hashCode` có thể đổi |
| `CharBuffer` view của `char[]` | Đổi mảng nền có thể đổi kết quả quan sát view |
| `StringBuilder` thay đổi sau khi truyền | Bản tham chiếu đổi theo, snapshot `String` giữ nguyên |
| Đối tượng làm khóa collection | Đúng quy tắc bình đẳng mong muốn; không biến đổi phần tham gia `hashCode` |
| Nội dung có emoji bổ sung | `length()` đếm UTF-16 code unit; `codePoints()` đếm code point |
| Input `null` | Chính sách xử lý rõ ràng, có kiểm thử |
| Dữ liệu bị sửa từ nhiều luồng | Snapshot hoặc đồng bộ hóa theo hợp đồng; không giả định mọi CharSequence thread-safe |

## 11. Sai lầm thường gặp và cách tránh

1. **Viết `a.equals(b)` để so sánh hai `CharSequence` bất kỳ:** phép so sánh phụ thuộc implementation. Nếu yêu cầu là “cùng văn bản”, chuẩn hóa sang String snapshot hoặc dùng API so sánh nội dung thích hợp.
2. **Giữ `CharSequence` lâu dài rồi coi nó như immutable:** bên ngoài vẫn có thể sửa builder hoặc backing array. Lưu `String` khi cần trạng thái ổn định.
3. **Dùng CharBuffer như khóa hash rồi thay position:** giá trị hash có thể thay đổi, làm vi phạm điều kiện của hash collection.
4. **Cho rằng `subSequence` luôn copy:** có implementation trả view chia sẻ nguồn. Khi cần snapshot chắc chắn, lấy một `String`.
5. **Tin `StringBuffer` khiến mọi chuỗi thao tác trở thành atomic:** đồng bộ hóa từng phương thức không bao trùm cả thuật toán nhiều bước.
6. **Dùng `final CharSequence` để “chống sửa”:** `final` không đóng băng đối tượng.
7. **Sử dụng `length()` để đếm ký tự hiển thị:** UTF-16 code unit khác code point; code point lại khác grapheme cluster.
8. **Gọi `Objects.equals` rồi tưởng có so sánh nội dung:** nó vẫn gọi `equals` của đối tượng cụ thể; chỉ thêm xử lý null.
9. **Dùng `toString()` nhiều lần trong cùng một phép tính trên nguồn mutable:** có thể nhận các snapshot khác nhau. Nếu cần một ảnh chụp nhất quán, lấy một lần ở ranh giới phù hợp rồi xử lý String đó.

## 12. Tự kiểm tra kiến thức

**Câu 1.** Một phương thức nhận `CharSequence input` và gán `this.value = input`. Người gọi truyền `StringBuilder("abc")`, sau đó append `"d"`. Nội dung đọc từ `value` là gì?

Đáp án: theo tham chiếu tới cùng builder, nội dung hiện tại sẽ là `"abcd"`. Để lưu đúng `"abc"` tại thời điểm gọi, phải lấy bản snapshot `String`.

**Câu 2.** Vì sao `new StringBuilder("x").equals(new StringBuilder("x"))` là false?

Đáp án: `StringBuilder` không override `Object.equals` theo nội dung, nên hai đối tượng khác danh tính không bằng nhau.

**Câu 3.** Vì sao hai `CharBuffer.wrap("x")` có thể bằng nhau, nhưng `"x".equals(CharBuffer.wrap("x"))` là false?

Đáp án: `CharBuffer.equals` so sánh nội dung còn lại với một `CharBuffer` khác. `String.equals` chỉ coi đối tượng kiểu `String` có cùng nội dung là bằng nhau.

**Câu 4.** Nếu API muốn cache chuỗi do người gọi truyền trong nhiều giờ thì nên lưu `CharSequence` hay `String`?

Đáp án: nếu cần nội dung ổn định theo thời điểm nhận, lưu `String` snapshot (kèm chính sách null và đồng bộ hóa nếu nguồn có thể bị sửa đồng thời).

**Câu 5.** Một `CharBuffer` đã nằm trong `HashSet`. Có an toàn khi di chuyển position rồi dùng bộ đệm đó như khóa không?

Đáp án: không nên; `equals`/`hashCode` của CharBuffer phụ thuộc phần dữ liệu còn lại, việc thay position có thể khiến quy tắc tra cứu khóa không còn ổn định.

## 13. Ghi nhớ

**`CharSequence` mô tả khả năng đọc một dãy UTF-16 code unit; nó không bảo đảm immutable và không thống nhất equals theo nội dung giữa các implementation.** Dùng `CharSequence` để linh hoạt nhận dữ liệu đầu vào, nhưng xác định rõ ownership, thời gian sống và cơ chế đồng bộ. Khi cần một **giá trị văn bản ổn định**, đặc biệt để cache hoặc dùng làm khóa collection, hãy tạo `String` snapshot và định nghĩa rõ phép so sánh theo nội dung.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-api-design-003">Quy định rõ cách xử lý null, rỗng và trắng</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế API String: Quy định rõ cách xử lý null, rỗng và trắng

## 1. Vấn đề cốt lõi

Một API Java nhận `String` thường phải giải quyết ba trạng thái dễ bị đánh đồng:

1. **`null`**: không có đối tượng chuỗi. Tùy hợp đồng, có thể là “không truyền”, “không biết”, một giá trị được phép, hoặc dữ liệu không hợp lệ.
2. **Chuỗi rỗng `""` (empty)**: có đối tượng `String`, nhưng độ dài bằng 0. Có thể là giá trị người dùng đã nhập rồi xóa hết, hoặc nội dung hợp lệ như ghi chú trống.
3. **Chuỗi chỉ gồm ký tự trắng (blank)**: có ký tự nhưng toàn bộ được `String.isBlank()` coi là whitespace, ví dụ `"   "` hoặc `"\t\n"`. Chuỗi rỗng cũng được `isBlank()` trả `true`.

Ngoài ra, **chuỗi có nội dung** như `" A "` có thể chứa khoảng trắng đầu/cuối mà ứng dụng muốn giữ hoặc chuẩn hóa. Bài học này nằm trong chủ đề **thiết kế API**, nên trọng tâm là định nghĩa rõ ý nghĩa và hành vi tại ranh giới API, không chỉ gọi đúng phương thức thư viện.

Ví dụ API tạo tài khoản nhận tên hiển thị, mật khẩu và ghi chú:

- Tên hiển thị bắt buộc, `null`/rỗng/trắng đều không hợp lệ; có thể `strip()` rồi kiểm tra.
- Mật khẩu không cho phép `null` hoặc rỗng nhưng **không được tự động `trim()`/`strip()`**, vì dấu cách có thể là ký tự thật của mật khẩu theo chính sách hệ thống.
- Ghi chú có thể cho phép `null` (“chưa cung cấp”), `""` (“đã cung cấp nhưng trống”) và `"   "` (“nội dung gồm dấu cách”); nếu muốn gộp các trạng thái, phải ghi rõ.
- Với API cập nhật từng phần, trường *vắng mặt* và trường *có mặt với giá trị null* có thể có ý nghĩa khác nhau, ví dụ “không đổi” và “xóa giá trị”.

**Quy tắc nền tảng:** Không chuẩn hóa mọi chuỗi đầu vào bằng `null -> ""` hay `strip()` theo thói quen. Mỗi tham số phải có hợp đồng dữ liệu phù hợp.

## 2. Cách Java kiểm tra từng trạng thái

`String` trong Java 17 có các API sau:

| Biểu thức | Khi `s == null` | `s = ""` | `s = "   "` | `s = " hi "` |
| --- | --- | --- | --- | --- |
| `s == null` | `true` | `false` | `false` | `false` |
| `s.isEmpty()` | Ném `NullPointerException` | `true` | `false` | `false` |
| `s.isBlank()` | Ném `NullPointerException` | `true` | `true` | `false` |
| `s.length() == 0` | Ném `NullPointerException` | `true` | `false` | `false` |
| `s.strip().isEmpty()` | Ném `NullPointerException` | `true` | `true` với dấu cách thông thường | `false` |

Từ Java 11, `String.isBlank()` xác định liệu chuỗi trống hoặc chỉ chứa các code point mà `Character.isWhitespace(int)` nhận diện hay không. `isEmpty()` chỉ xét độ dài bằng 0 và **không cần duyệt từng ký tự**.

Khi `null` có thể xuất hiện, kiểm tra trước khi gọi phương thức:

```java
static boolean isNullOrBlank(String value) {
    return value == null || value.isBlank();
}
```

Phép `||` đánh giá ngắn mạch: nếu `value == null` là `true`, Java không gọi `isBlank()` nữa. Đây là **đoạn phương thức** đặt trong class Java, không phải chương trình độc lập.

Lưu ý: `isBlank()` trả `true` cho chuỗi rỗng, vì vậy không thể dùng riêng nó để phân biệt **empty** với **blank nhưng không empty**. Khi phân loại, nên kiểm tra theo thứ tự: `null` → `isEmpty()` → `isBlank()` → có nội dung.

## 3. Định nghĩa hợp đồng API trước khi lập trình

Một tài liệu API tốt cần trả lời rõ các câu hỏi: `null` có được phép không, `""` có nghĩa gì, khoảng trắng có được giữ không, có chuẩn hóa Unicode không, dữ liệu sai báo lỗi thế nào, và kết quả có bảo toàn đầu vào không.

Ví dụ bảng hợp đồng cho một hệ thống:

| Trường API | `null` | `""` | Chỉ có whitespace | Khoảng trắng đầu/cuối | Hành vi đề xuất |
| --- | --- | --- | --- | --- | --- |
| `displayName` | Không hợp lệ | Không hợp lệ | Không hợp lệ | `strip()` và lưu bản đã chuẩn hóa | Trả thông báo lỗi rõ ràng khi tên không hợp lệ |
| `password` | Không hợp lệ | Không hợp lệ | Có thể hợp lệ nếu chính sách cho phép | **Giữ nguyên** | Không sửa nội dung bí mật ngoài yêu cầu tường minh |
| `searchQuery` | Không lọc | Không lọc | Không lọc | `strip()` | Chuẩn hóa thành “không có bộ lọc” khi trống/trắng |
| `comment` | Chưa cung cấp | Đã cung cấp ghi chú rỗng | Giữ hoặc từ chối theo nghiệp vụ | Mặc định giữ nguyên | Không gộp ba trạng thái một cách ngầm định |
| `update.bio` | Có thể mang nghĩa xóa dữ liệu | Có thể mang nghĩa đặt bio rỗng | Có thể là dữ liệu hợp lệ | Theo chính sách | Phải tách khỏi trường vắng mặt trong yêu cầu cập nhật |

Bảng này là **ví dụ thiết kế**, không phải luật chung của Java. Hợp đồng cần được ghi trong Javadoc, OpenAPI/schema, validation, tài liệu client hoặc nơi thích hợp của dự án.

Một số điều kiện cần thống nhất ở cả **client, server, database**: một chuỗi rỗng có được lưu thành `""` hay thành `NULL`; có cắt khoảng trắng khi validate nhưng không cắt khi lưu; lỗi trả về loại gì; và cách biểu diễn một trường không hiện diện trong JSON.

## 4. Chiến lược xử lý: Reject, Normalize, Preserve

### 4.1. Reject — từ chối các trạng thái không hợp lệ

Dùng cho trường bắt buộc có dữ liệu có ý nghĩa, ví dụ tên hiển thị:

```java
import java.util.Objects;

static String requireNonBlankName(String raw) {
    Objects.requireNonNull(raw, "name must not be null");
    String normalized = raw.strip();

    if (normalized.isEmpty()) {
        throw new IllegalArgumentException("name must not be blank");
    }
    return normalized;
}
```

`Objects.requireNonNull` chủ động ném `NullPointerException` với thông điệp khi `raw` là null. `IllegalArgumentException` báo giá trị không phù hợp điều kiện nghiệp vụ. Đây là cách thiết kế cho một utility nội bộ; tại ranh giới HTTP, ứng dụng thường chuyển lỗi validation thành phản hồi 4xx có cấu trúc.

**Quan trọng:** Kiểm tra `raw.isBlank()` có thể từ chối blank trước khi chuẩn hóa, nhưng ví dụ trên kiểm tra chuỗi **sau khi strip** vì hợp đồng yêu cầu lưu chuỗi đã bỏ khoảng trắng đầu/cuối. Hai cách thống nhất với nhau khi dùng cùng quy tắc whitespace.

### 4.2. Normalize — chủ động gộp các trạng thái

Một ô tìm kiếm tùy chọn có thể định nghĩa “không có bộ lọc” khi query bằng `null`, rỗng hoặc toàn dấu cách:

```java
static String normalizeOptionalQuery(String raw) {
    if (raw == null) {
        return null;
    }
    String normalized = raw.strip();
    return normalized.isEmpty() ? null : normalized;
}
```

Bây giờ cả `null`, `""` và `" \t "` đều cho `null`, còn `"  java  "` cho `"java"`. Đây là **quyết định nghiệp vụ cố ý**, không phải chuẩn hóa bắt buộc cho mọi tham số String. Nếu cần lưu vết người dùng đã chủ động gửi chuỗi rỗng, không được dùng cách gộp này.

Nếu hợp đồng muốn **mọi trường đầu ra đều khác null**, có thể trả `""` để biểu diễn “không có nội dung”, nhưng phải ghi rõ điều đó và hiểu rằng đã mất phân biệt giữa absent và empty.

### 4.3. Preserve — giữ nguyên, chỉ kiểm tra điều kiện cần

Với mật khẩu (trong một chính sách cho phép dấu cách), không tự động strip:

```java
static String requireNonEmptyPassword(String raw) {
    Objects.requireNonNull(raw, "password must not be null");
    if (raw.isEmpty()) {
        throw new IllegalArgumentException("password must not be empty");
    }
    return raw; // Giữ nguyên cả khoảng trắng và các ký tự Unicode.
}
```

Trong hệ thống thực, có thể yêu cầu độ dài, độ phức tạp, danh sách ký tự hợp lệ và giới hạn kích thước theo chính sách xác thực. Ví dụ này chỉ minh họa **hợp đồng chuỗi**, không khuyến nghị mật khẩu chỉ có khoảng trắng.

Tương tự, mã định danh, token, chữ ký, nội dung được hash, văn bản giàu định dạng hoặc khoảng trắng có ý nghĩa **không nên** được tự ý `strip()`, `toLowerCase()` hay chuẩn hóa Unicode.

## 5. Ví dụ Java 17+ hoàn chỉnh

Tạo file **`StringInputPolicyDemo.java`**:

```java
import java.util.Objects;

public class StringInputPolicyDemo {
    enum Kind { NULL, EMPTY, BLANK, TEXT }

    static Kind kind(String value) {
        if (value == null) {
            return Kind.NULL;
        }
        if (value.isEmpty()) {
            return Kind.EMPTY;
        }
        return value.isBlank() ? Kind.BLANK : Kind.TEXT;
    }

    static String requireNonBlankName(String raw) {
        Objects.requireNonNull(raw, "name must not be null");
        String normalized = raw.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return normalized;
    }

    static String requireNonEmptyPassword(String raw) {
        Objects.requireNonNull(raw, "password must not be null");
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("password must not be empty");
        }
        return raw;
    }

    static String normalizeOptionalQuery(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    public static void main(String[] args) {
        System.out.println("null=" + kind(null));
        System.out.println("empty=" + kind(""));
        System.out.println("blank=" + kind(" \t "));
        System.out.println("text=" + kind(" Java "));

        System.out.println("name=["
                + requireNonBlankName(" \t Lan \n") + "]");
        System.out.println("password-length="
                + requireNonEmptyPassword("   ").length());
        System.out.println("query-is-null="
                + (normalizeOptionalQuery(" \t ") == null));

        String unicodeSpace = "\u2003Java\u2003";
        System.out.println("strip-length=" + unicodeSpace.strip().length());
        System.out.println("trim-length=" + unicodeSpace.trim().length());
        System.out.println("nbsp-isBlank=" + "\u00A0".isBlank());

        try {
            requireNonBlankName(" \t ");
        } catch (IllegalArgumentException ex) {
            System.out.println("name-error=" + ex.getMessage());
        }
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 StringInputPolicyDemo.java
java StringInputPolicyDemo
```

Stdout:

```text
null=NULL
empty=EMPTY
blank=BLANK
text=TEXT
name=[Lan]
password-length=3
query-is-null=true
strip-length=4
trim-length=6
nbsp-isBlank=false
name-error=name must not be blank
```

Các kết quả đã được đối chiếu trên JDK với API có trong Java 17:

- `kind` kiểm tra `null` trước, rồi `isEmpty`, rồi `isBlank` nên không bị `NullPointerException` và giữ đủ bốn trạng thái.
- Tên được `strip` thành `Lan`; mật khẩu gồm ba dấu cách vẫn giữ độ dài 3, bởi hợp đồng hiện tại chỉ cấm rỗng.
- Query blank được hợp đồng gộp thành `null`.
- `\u2003` là EM SPACE được `strip()` nhận diện; `trim()` chỉ loại một số ký tự có mã nhỏ hơn hoặc bằng U+0020 nên không loại nó.
- `\u00A0` là NO-BREAK SPACE: trong Java, `Character.isWhitespace` không coi nó là whitespace, nên `isBlank()` trả `false`. Cần hiểu rõ định nghĩa “trắng” trong hợp đồng thay vì suy ra theo cảm nhận hiển thị.
- Một tên toàn whitespace thông thường bị từ chối và nhận lỗi có thông điệp.

## 6. Phân biệt trim(), strip() và isBlank() trong Unicode

### 6.1. trim() có tiêu chí lịch sử hẹp hơn

`String.trim()` loại ký tự ở hai đầu có mã `<= U+0020` theo đặc tả Java. `strip()` (Java 11+) dựa trên `Character.isWhitespace`; vì thế kết quả khác với một số dấu cách Unicode.

```java
String input = "\u2003Java\u2003";

System.out.println(input.trim().length());  // 6
System.out.println(input.strip().length()); // 4
```

Không phải tất cả ký tự thường gọi là “space” đều được `Character.isWhitespace` coi là whitespace: **NO-BREAK SPACE (U+00A0)** là ví dụ. Nếu hệ thống cần định nghĩa “trắng” theo quy tắc đặc thù (ví dụ coi U+00A0 là blank), hãy xây dựng validator chuẩn hóa theo một đặc tả được thống nhất và kiểm tra độc lập.

### 6.2. isBlank() không làm thay đổi chuỗi

`"  A  ".isBlank()` là `false`, vì có ký tự `A`. `isBlank()` không loại bỏ khoảng trắng ở hai đầu; `strip()` mới trả chuỗi mới sau chuẩn hóa. Đừng nhầm **kiểm tra** với **biến đổi**.

```java
String value = "  A  ";
System.out.println(value.isBlank());       // false
System.out.println("[" + value.strip() + "]"); // [A]
System.out.println("[" + value + "]");       // [  A  ]
```

`String` là immutable: `strip()` không thay đổi biến `value`. Muốn lưu bản chuẩn hóa, phải sử dụng giá trị trả về.

### 6.3. Không dùng isEmpty() để kiểm tra “người dùng chưa nhập”

Chuỗi `"    "` không rỗng, vì có ký tự. Nếu một form coi chuỗi toàn dấu cách là không hợp lệ, dùng `isBlank()` (sau kiểm tra null) hoặc `strip().isEmpty()`. Ngược lại, với dữ liệu nhạy với whitespace, chỉ dùng `isEmpty()` nếu hợp đồng yêu cầu.

## 7. Null trong tham số, giá trị trả về và trạng thái cập nhật

### 7.1. Tham số bắt buộc và tùy chọn

Với tham số bắt buộc, API nên nêu rõ “non-null” và điều kiện rỗng/trắng. Một hàm nhận tên hiển thị có thể dùng Javadoc:

```java
/**
 * Lưu tên hiển thị sau khi strip khoảng trắng hai đầu.
 *
 * @param name giá trị non-null, sau strip phải có ít nhất một ký tự
 *             không phải whitespace theo Character.isWhitespace
 * @return tên đã chuẩn hóa, khác null và không blank
 * @throws NullPointerException nếu name là null
 * @throws IllegalArgumentException nếu name blank
 */
static String validateDisplayName(String name) {
    Objects.requireNonNull(name, "name");
    String normalized = name.strip();
    if (normalized.isEmpty()) {
        throw new IllegalArgumentException("blank name");
    }
    return normalized;
}
```

Đây là đoạn phương thức độc lập về ý nghĩa nhưng cần đặt trong một class để biên dịch. Đối với API công khai, có thể chọn cùng một kiểu lỗi validation cho cả null và blank, miễn là **hợp đồng và test thể hiện nhất quán**; ví dụ trên cố ý phân biệt lỗi do null và do giá trị không hợp lệ.

Với giá trị **trả về**, nên thống nhất liệu `null` biểu thị “không tìm thấy” hay ngoại lệ, `""` có phải giá trị hợp lệ không. Có thể dùng `Optional<String>` cho kết quả có thể không hiện diện:

```java
static java.util.Optional<String> normalizedQuery(String raw) {
    if (raw == null || raw.isBlank()) {
        return java.util.Optional.empty();
    }
    return java.util.Optional.of(raw.strip());
}
```

`Optional.empty()` cho phép thể hiện “không có kết quả” rõ ràng; nhưng nó cũng **gộp** null và blank theo chính sách. `Optional` không tự thay thế việc quyết định rỗng/trắng, và không cần dùng ở mọi field hoặc tham số chỉ để né thiết kế hợp đồng.

### 7.2. API cập nhật từng phần cần phân biệt vắng mặt với null

Giả sử một request cập nhật hồ sơ có trường `bio`. Ba trạng thái đầu vào có thể mang nghĩa:

| Trạng thái request | Ý nghĩa ví dụ |
| --- | --- |
| Không có thuộc tính `bio` | Giữ nguyên bio hiện tại |
| Có `bio: null` | Xóa/clear bio (nếu API cho phép) |
| Có `bio: ""` | Đặt bio thành chuỗi rỗng |
| Có `bio: "   "` | Đặt thành ba dấu cách hoặc từ chối theo quy định |

Một đối tượng chỉ có field `String bio` đôi khi không thể phân biệt **vắng mặt** với **có mặt nhưng null** sau khi deserialization. Nếu hợp đồng cần phân biệt, request model phải mang cả trạng thái **field present** và giá trị của nó, hoặc dùng một cơ chế cập nhật có ngữ nghĩa định nghĩa rõ như JSON Merge Patch. Không tự coi `null` là “không cập nhật” nếu protocol quy định `null` là “xóa”.

### 7.3. Đừng biến null thành empty ở quá nhiều tầng

Nếu HTTP binder, service và repository mỗi nơi tự dùng một quy tắc khác nhau, dữ liệu có thể thay đổi ý nghĩa giữa các tầng. Nên chuẩn hóa ở một **ranh giới có hợp đồng rõ**, rồi truyền giá trị đã được validate vào lớp sau dưới invariant được mô tả. Ví dụ service nhận `displayName` đã non-null và non-blank; repository không tự gọi `strip()` một lần nữa theo một tiêu chí khác.

## 8. Lỗi và cách thông báo cho caller

Một API tốt nêu rõ phản ứng khi dữ liệu không hợp lệ:

- **Java method nội bộ:** `Objects.requireNonNull` hoặc kiểm tra thủ công; `IllegalArgumentException` cho vi phạm giá trị; thông điệp xác định tên trường và yêu cầu. Thống nhất loại lỗi trong cùng nhóm API.
- **REST/HTTP:** một lỗi xác thực input thường được map sang mã lỗi phía client (thường là HTTP 400 hoặc mã phù hợp quy ước API) kèm field, reason/code và thông điệp rõ ràng. Không phơi bày dữ liệu bí mật trong lỗi hoặc log.
- **Validation ở DTO/schema:** khai báo điều kiện phù hợp (`non-null`, `non-empty`, `non-blank`) rồi đảm bảo cấu hình binder và validation thực sự chạy. Trong Jakarta Bean Validation, các annotation thường dùng là `@NotNull`, `@NotEmpty` và `@NotBlank`; đây là **thư viện phụ thuộc**, không phải annotation Java SE 17 có sẵn.
- **Database:** kiểm tra khả năng lưu `NULL`/`""`, ràng buộc cột và quy tắc so sánh. Một số hệ quản trị có cách xử lý chuỗi rỗng khác nhau; đừng dựa vào giả định giữa các database.

Không trả “invalid” chung chung nếu client cần biết trường nào sai. Nhưng khi field chứa password, token hoặc dữ liệu nhạy cảm, thông điệp và log không nên chép toàn bộ input.

## 9. Thiết kế test theo bảng trạng thái

Đối với một tham số, các trường hợp test nên bao phủ cả **trạng thái** và **biến thể ký tự trắng**. Ví dụ hợp đồng `requireNonBlankName`:

| Đầu vào | Kết quả mong đợi |
| --- | --- |
| `null` | `NullPointerException` theo hợp đồng ví dụ |
| `""` | `IllegalArgumentException` |
| `"   "` | `IllegalArgumentException` |
| `"\t\n"` | `IllegalArgumentException` |
| `" A "` | Trả `"A"` |
| `"A B"` | Giữ khoảng trắng nội bộ, trả `"A B"` |
| `"\u2003A\u2003"` | Trả `"A"` |
| `"\u00A0"` | Không được coi là blank bằng `isBlank()` mặc định; cần quyết định nghiệp vụ |
| Chuỗi quá dài | Có thể bị từ chối nếu hợp đồng đặt giới hạn độ dài |

Nếu có giới hạn độ dài, nêu rõ **đơn vị đo**: `String.length()` đếm **UTF-16 code unit**, không nhất thiết bằng số Unicode code point hoặc số ký tự người dùng nhìn thấy. Ví dụ một emoji ngoài BMP thường có `length() == 2` và `codePointCount(...) == 1`. Không dùng một tiêu chí độ dài để diễn giải thành tiêu chí khác.

Tránh viết test chỉ chứng minh `String.isBlank()` hoạt động theo JDK; test có giá trị là xác nhận **hợp đồng nghiệp vụ của API**, gồm cả lỗi, chuẩn hóa và hành vi bảo toàn whitespace.

## 10. Những sai lầm phổ biến

1. **Gọi `s.isBlank()` khi `s` có thể là null.** Kết quả là `NullPointerException` ngoài ý muốn. Kiểm tra null trước hoặc đặt invariant non-null ngay đầu method.
2. **Coi `isEmpty()` và `isBlank()` như nhau.** `"  "` không empty nhưng blank.
3. **Coi null và chuỗi rỗng là một.** Có thể làm mất ý nghĩa “chưa cung cấp” so với “cung cấp rỗng”, nhất là trong cập nhật.
4. **Luôn strip mọi input.** Có thể thay đổi mật khẩu, token, định danh, chữ ký hoặc nội dung được yêu cầu giữ nguyên.
5. **Chỉ kiểm tra blank rồi không dùng bản chuẩn hóa.** Nếu yêu cầu đầu ra phải bỏ khoảng trắng hai đầu, phải trả/lưu giá trị của `strip()`.
6. **Nhầm `trim()` và `strip()`.** Hai phương thức khác nhau ở một số Unicode whitespace; không phải tất cả ký tự trông như khoảng trắng đều bị loại bởi `strip()`.
7. **Tự động biến mọi blank thành null mà không có thỏa thuận.** Có thể làm mất dữ liệu người dùng đã chủ động gửi.
8. **Định nghĩa null khác nhau giữa Java service và HTTP/JSON.** Tham số String không đủ thể hiện mọi trạng thái field-present; phải giữ trạng thái hiện diện nếu cần.
9. **Giả định annotation validation tự hoạt động.** `@NotBlank` yêu cầu framework/provider được cấu hình và thực thi; Java SE không tự áp dụng ràng buộc đó.
10. **Trả lỗi không nhất quán.** Một API từ chối null bằng NPE, API khác âm thầm đổi thành rỗng; người dùng và hệ thống tích hợp khó dự đoán.
11. **Bỏ qua chuỗi Unicode đặc biệt.** U+00A0, U+2003, zero-width characters và normalization có thể dẫn đến kết quả khác với kỳ vọng. Hãy định nghĩa nhóm ký tự được chấp nhận nếu có yêu cầu bảo mật hoặc nghiệp vụ.
12. **Hiểu sai kích thước String.** `length()` đo đơn vị UTF-16; API có quy định số ký tự hiển thị nên chọn tiêu chí và test thích hợp.

## 11. Bài tập tự kiểm tra

### Bài 1 — Phân loại chính xác bốn trường hợp

Viết `kind(String)` để phân loại `null` → `NULL`, `""` → `EMPTY`, `" \t"` → `BLANK`, `"x"` → `TEXT`. Giải pháp có trong chương trình `StringInputPolicyDemo`: kiểm tra null trước, tiếp đến empty, rồi blank. Nếu kiểm tra `isBlank` trước `isEmpty` thì chuỗi rỗng sẽ bị xếp chung vào BLANK và làm mất một trạng thái cần phân biệt.

### Bài 2 — Thiết kế chính sách nhập tag

Đề bài: tag phải có nội dung sau khi loại khoảng trắng đầu/cuối, được viết thường (cho tag ASCII), null không hợp lệ.

```java
static String normalizeAsciiTag(String raw) {
    java.util.Objects.requireNonNull(raw, "tag must not be null");
    String tag = raw.strip();
    if (tag.isEmpty()) {
        throw new IllegalArgumentException("tag must not be blank");
    }
    return tag.toLowerCase(java.util.Locale.ROOT);
}
```

Đây là đoạn phương thức. `Locale.ROOT` giúp phép biến đổi chữ hoa/thường độc lập locale mặc định, nhưng không tự bảo đảm đúng mọi quy tắc định danh Unicode phức tạp. Chỉ dùng chuẩn hóa chữ thường khi hợp đồng tag thật sự không phân biệt chữ hoa.

### Bài 3 — Dữ liệu trống trong PATCH

Một request không có `nickname`, một request có `nickname: null`, một request có `nickname: ""`. Hãy thiết kế ý nghĩa cho từng trường hợp trước khi viết DTO. Gợi ý: “giữ nguyên”, “xóa”, “đặt rỗng” là một cách phân biệt; nếu cả null và rỗng đều có nghĩa là xóa, đó phải là quy tắc được ghi trong hợp đồng.

### Bài 4 — Password có dấu cách

Với `password = "  secret  "`, tại sao gọi `strip()` trước khi đối chiếu hash có thể sai? Vì đã thay đổi dữ liệu mà người dùng nhập. Hệ thống xác thực phải dùng quy tắc chuẩn hóa được công bố và nhất quán từ lúc thiết lập đến lúc kiểm tra, chứ không tự bỏ ký tự.

### Bài 5 — Trường hiển thị toàn whitespace Unicode

Một form nhận `"\u2003"` (EM SPACE) và `"\u00A0"` (NO-BREAK SPACE). `String.isBlank()` coi trường hợp đầu là blank nhưng không coi trường hợp thứ hai là blank. Hãy quyết định rõ trường hợp thứ hai được phép hay cần validator tùy biến; kết quả không thể suy ra chỉ từ “trông như khoảng trắng”.

## 12. Checklist khi công bố API

Trước khi triển khai một tham số hoặc field kiểu `String`, hãy kiểm tra:

- Có cho phép **null**, **empty** và **blank** một cách độc lập không?
- Có phân biệt **field absent** với **field present = null** trong thao tác cập nhật không?
- Có giữ hay loại whitespace ở đầu/cuối, khoảng trắng bên trong và ký tự Unicode đặc biệt không?
- Chuẩn hóa được thực hiện ở đâu, trước hay sau validation, và giá trị nào được truyền sang tầng tiếp theo?
- Giá trị không hợp lệ tạo lỗi gì, có thể dự đoán được không, có rò dữ liệu nhạy cảm không?
- Kết quả có bất biến/invariant nào, ví dụ “tên đã normalize, non-null, non-blank”?
- Độ dài tối đa được đo bằng UTF-16 code unit, Unicode code point hay quy tắc khác?
- Các ví dụ, schema, test và tài liệu client có dùng cùng chính sách không?

**Kết luận:** `null`, `""` và chuỗi chỉ chứa whitespace có ý nghĩa khác nhau. Java cung cấp `isEmpty()`, `isBlank()`, `trim()`, `strip()` và `Objects.requireNonNull()`, nhưng **chính API phải quyết định lúc nào từ chối, chuẩn hóa hay giữ nguyên**. Viết hợp đồng rõ ràng giúp caller dự đoán chính xác dữ liệu được chấp nhận, lưu giữ và trả về, đặc biệt khi có Unicode, cập nhật từng phần và nhiều tầng hệ thống.

**Tài liệu tham khảo:** [Java SE 17 — String](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html), [Java SE 17 — Character.isWhitespace](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html#isWhitespace(int)), [Java SE 17 — Objects.requireNonNull](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Objects.html#requireNonNull(T,java.lang.String)), [Java SE 17 — Optional](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Optional.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-api-design-004">Quy định đơn vị chỉ số hoặc giới hạn độ dài</a>

<details>
<summary>Xem chi tiết</summary>

# Quy định đơn vị chỉ số hoặc giới hạn độ dài trong API xử lý `String` Java

## 1. Vấn đề: “vị trí 3” và “tối đa 20 ký tự” chưa đủ rõ

Một API nhận `String` dễ có tham số như `start`, `end`, `maxLength` hay `maxChars`. Nếu không mô tả **đơn vị**, người gọi có thể hiểu nhầm là số ký tự nhìn thấy trên màn hình, còn mã Java lại thực hiện `String.length()` hoặc `substring()` theo **UTF-16 code unit**.

Ví dụ, xét chuỗi gồm chữ `A`, emoji `😃` và chữ `e` có dấu sắc kết hợp (`e\u0301`):

~~~text
Nhìn thấy:          A | 😃 | é
UTF-16 code unit:   1 +  2 + 2 = 5
Unicode code point: 1 +  1 + 2 = 4
UTF-8 byte:         1 +  4 + 3 = 8
Grapheme cluster:   1 +  1 + 1 = 3 (theo phân đoạn Unicode thông thường)
~~~

Vì vậy, cùng một dữ liệu có thể được gọi là dài **5**, **4**, **8** hoặc **3** tùy tiêu chí. Nếu tài liệu API chỉ ghi “độ dài tối đa 4 ký tự”, người dùng sẽ không biết chuỗi này hợp lệ hay bị từ chối.

**Nguyên tắc thiết kế:** mọi chỉ số, khoảng cắt, vị trí con trỏ và giới hạn độ dài phải được gắn với một **đơn vị đếm được định nghĩa tường minh**.

## 2. Bốn đơn vị thường gặp

| Đơn vị | Ý nghĩa | API Java liên quan | Dùng khi |
| --- | --- | --- | --- |
| **UTF-16 code unit** | Mỗi `char` 16 bit của `String` | `length()`, `charAt()`, `substring()`, `indexOf()` | Tương thích API `String`, vị trí nội bộ Java |
| **Unicode code point** | Mã Unicode biểu thị bằng `int`; ký tự ngoài BMP dùng hai code unit | `codePoints()`, `codePointCount()`, `codePointAt()`, `offsetByCodePoints()` | Quy tắc xử lý theo từng code point |
| **Grapheme cluster** | Một cụm ký tự mà người dùng thường nhận thức như một ký tự | Thư viện phân đoạn Unicode theo grapheme boundary | Cắt/chọn/xóa ký tự trong giao diện người dùng |
| **Byte theo encoding** | Số byte sau khi mã hóa, chẳng hạn UTF-8 | `getBytes(StandardCharsets.UTF_8)`, `CharsetEncoder` | Giao thức, mạng, lưu trữ, giới hạn payload |

**Lưu ý:** một grapheme cluster có thể gồm nhiều code point (chữ + dấu kết hợp; emoji có ZWJ, biến thể hoặc màu da). Ngược lại, số cột hiển thị trên terminal không nhất thiết bằng số grapheme cluster; việc hiển thị còn tùy font, terminal và môi trường.

Nếu một API dùng `int` cho cả chỉ số UTF-16 và số code point, **kiểu `int` không thể hiện đơn vị**. Phải dùng tên biến, tên phương thức, tài liệu hợp đồng hoặc kiểu giá trị riêng để làm rõ.

## 3. Cách Java `String` đánh chỉ số: UTF-16 code unit

Hầu hết API chỉ số của `String` dùng **đơn vị UTF-16 code unit**:

~~~java
String s = "A\uD83D\uDE03B"; // A😃B
System.out.println(s.length());          // 4
System.out.println(s.charAt(0) == 'A');   // true
System.out.println(s.charAt(3) == 'B');   // true
~~~

Hai `char` ở chỉ số 1 và 2 lần lượt là **high surrogate** và **low surrogate** của `😃`. Vì thế `substring(1, 2)` chỉ lấy một nửa cặp surrogate, khiến chuỗi kết quả chứa surrogate đứng lẻ:

~~~java
String broken = s.substring(1, 2);
System.out.println(broken.length());                  // 1
System.out.println(Character.isHighSurrogate(
        broken.charAt(0)));                           // true
~~~

`substring(beginIndex, endIndex)` lấy vùng **nửa mở** `[beginIndex, endIndex)`: bao gồm đầu, không bao gồm cuối, và hai tham số là chỉ số UTF-16, **không phải chỉ số code point hoặc grapheme**.

Một API nên mô tả rõ cả **đơn vị** lẫn **quy tắc biên**:

> `sliceUtf16(String input, int beginUtf16Inclusive, int endUtf16Exclusive)` nhận offset theo UTF-16 code unit và dùng khoảng [begin, end). Người gọi phải chọn ranh giới phù hợp nếu cần bảo toàn một code point hoặc grapheme.

Nếu API dành cho người dùng cuối, việc buộc họ truyền offset UTF-16 có thể không phù hợp; hãy cân nhắc API theo code point hoặc grapheme.

## 4. Khi hợp đồng yêu cầu code point

`String.codePointCount(beginIndex, endIndex)` đếm số code point trong một vùng **UTF-16 index**. `String.codePoints().count()` đếm theo toàn chuỗi. Trong cả hai cách, một cặp surrogate hợp lệ được tính là **một** code point; surrogate lẻ được tính riêng, không tự biến mất.

~~~java
String s = "A\uD83D\uDE03B";
System.out.println(s.length());                     // 4
System.out.println(s.codePointCount(0, s.length())); // 3
System.out.println(s.codePoints().count());         // 3
~~~

Để biến chỉ số code point thành **chỉ số UTF-16** dùng cho `substring`, dùng `offsetByCodePoints`:

~~~java
String s = "A\uD83D\uDE03B";

int beginUtf16 = s.offsetByCodePoints(0, 1);
int endUtf16 = s.offsetByCodePoints(beginUtf16, 1);

String secondCodePoint = s.substring(beginUtf16, endUtf16);
System.out.println(secondCodePoint.equals("\uD83D\uDE03")); // true
~~~

`beginUtf16` là 1, `endUtf16` là 3: đúng cặp surrogate của emoji. Không được mặc định rằng `substring(1, 2)` sẽ lấy “ký tự thứ hai”.

### Xây dựng API cắt theo số code point

Đoạn sau là ví dụ phương thức độc lập, có thể đặt trong một class Java:

~~~java
static String takeCodePoints(String input, int maxCodePoints) {
    java.util.Objects.requireNonNull(input, "input");
    if (maxCodePoints < 0) {
        throw new IllegalArgumentException("maxCodePoints < 0");
    }

    int count = input.codePointCount(0, input.length());
    int numberToKeep = Math.min(maxCodePoints, count);
    int endUtf16 = input.offsetByCodePoints(0, numberToKeep);
    return input.substring(0, endUtf16);
}
~~~

Tên `maxCodePoints` nêu rõ đơn vị. Hàm không cắt đôi một cặp surrogate hợp lệ, nhưng vẫn có thể cắt **giữa các code point thuộc cùng một grapheme cluster** (ví dụ chữ `e` và dấu kết hợp). Khi yêu cầu là “không làm hỏng ký tự hiển thị”, cần phân đoạn grapheme riêng.

**Đừng nhầm:** tham số thứ hai của `offsetByCodePoints(index, codePointOffset)` là số code point cần di chuyển; giá trị nó trả về vẫn là **offset UTF-16**.

## 5. Khi hợp đồng yêu cầu grapheme cluster

Ví dụ `"e\u0301"` gồm **hai code point** (`e` và dấu sắc kết hợp) nhưng thường hiển thị thành **một** chữ có dấu. Một số emoji gia đình cũng gồm nhiều code point nối bằng ZWJ, trong khi người dùng nhìn thấy một biểu tượng.

Vì vậy:

- `String.length()` **không** đếm số ký tự nhìn thấy.
- `codePoints().count()` cũng **không** luôn đếm số ký tự nhìn thấy.
- `substring` hoặc việc lấy đúng `n` code point có thể cắt giữa các thành phần của grapheme.

Với giới hạn độ dài ô nhập, thao tác xóa một ký tự theo phím Backspace hay vị trí con trỏ UI, nên xác định liệu **extended grapheme cluster** có phải đơn vị mong muốn hay không.

Trong Java, có `java.text.BreakIterator.getCharacterInstance()`, nhưng khả năng phân đoạn grapheme theo Unicode phụ thuộc phiên bản và quy tắc được hỗ trợ; không được mặc định nó khớp đầy đủ quy tắc extended grapheme cluster mới nhất trên mọi JDK. Nếu yêu cầu đòi hỏi chính xác theo Unicode UAX #29, hãy dùng thư viện có hỗ trợ grapheme boundary rõ ràng (ví dụ ICU4J), ghi nhận phiên bản Unicode của thư viện và kiểm thử với dữ liệu thực.

Một API có thể đặt tên tường minh như:

~~~java
// Chữ ký thiết kế minh họa; implementation cần bộ phân đoạn grapheme.
int countGraphemeClusters(String text);

// Trả về tối đa N grapheme cluster, không cắt giữa cluster.
String takeGraphemeClusters(String text, int maxClusters);
~~~

Hai chữ ký trên chỉ minh họa **hợp đồng API**, không phải chương trình chạy độc lập.

## 6. Khi giới hạn được đo bằng byte

Các giao thức mạng, trường lưu trữ hoặc định dạng file thường ràng buộc độ dài theo **byte**. Khi đó `String.length()` không trả lời đúng câu hỏi.

Trong UTF-8, `A` dùng 1 byte, `😃` dùng 4 byte, `e` dùng 1 byte và dấu sắc kết hợp U+0301 dùng 2 byte. Chuỗi `A😃e\u0301` vì thế chiếm **8 byte UTF-8** dù `length()` chỉ bằng 5.

~~~java
String value = "A\uD83D\uDE03e\u0301";
int bytes = value.getBytes(
        java.nio.charset.StandardCharsets.UTF_8).length;

System.out.println(bytes); // 8
~~~

Định nghĩa một giới hạn nên ghi đầy đủ, ví dụ:

- `maxUtf8Bytes = 255`: tối đa 255 byte **sau khi mã hóa UTF-8**.
- `maxCodePoints = 50`: tối đa 50 code point trong `String`.
- `maxUtf16Units = 100`: tối đa 100 UTF-16 code unit; tương đương tiêu chí `String.length() <= 100`.

Nếu API gửi dữ liệu dạng JSON, giới hạn “byte của **payload JSON đã serialize và encode UTF-8**” có thể khác giới hạn “byte UTF-8 của giá trị chuỗi trước serialize”: dấu nháy kép, escape, tên thuộc tính và phần cấu trúc JSON có thể chiếm thêm byte.

**Encoding là một phần của hợp đồng.** “255 byte” mà không nói UTF-8, UTF-16 hay định dạng truyền dẫn là mô tả chưa đủ.

### Đầu vào có surrogate không hợp lệ

`String` Java vẫn có thể chứa surrogate lẻ. `getBytes(StandardCharsets.UTF_8)` có thể dùng ký tự thay thế khi không mã hóa được đầu vào, vì thế đếm byte bằng `getBytes` không đồng nghĩa với **kiểm tra UTF-16 hợp lệ**. Nếu hệ thống cần từ chối dữ liệu lỗi, hãy xác thực surrogate pair trước hoặc dùng `CharsetEncoder` với `CodingErrorAction.REPORT`, xử lý `CharacterCodingException` theo chính sách của ứng dụng.

Không nên cắt trực tiếp mảng byte UTF-8 tại một vị trí bất kỳ rồi giải mã lại: việc cắt có thể rơi giữa một chuỗi byte mã hóa code point. Muốn giới hạn byte mà vẫn bảo toàn ký tự, cần cắt tại **ranh giới code point hoặc grapheme** và kiểm tra số byte sau mã hóa.

## 7. Thiết kế hợp đồng API rõ nghĩa

### 7.1. Đặt tên chứa đơn vị

So sánh:

~~~java
// Mơ hồ: giới hạn nào, chỉ số loại nào?
String truncate(String value, int limit);

// Rõ hơn: tối đa bao nhiêu Unicode code point?
String takeCodePoints(String value, int maxCodePoints);

// Rõ hơn: vị trí theo UTF-16.
String sliceUtf16(String value,
                  int beginUtf16Inclusive,
                  int endUtf16Exclusive);

// Rõ hơn: dữ liệu phải vừa giới hạn byte UTF-8.
boolean fitsUtf8Bytes(String value, int maxUtf8Bytes);
~~~

Các chữ ký này diễn đạt **ý định thiết kế**; code implement phải tuân thủ đơn vị đã ghi, thay vì đặt tên `maxCodePoints` rồi kiểm tra bằng `value.length()`.

Nên mô tả thêm:

1. **Giới hạn có bao gồm biên không?** “tối đa 20” thường nghĩa là `<= 20`.
2. **Chỉ số bắt đầu từ 0 hay 1?** Trong Java `String`, chỉ số là 0-based.
3. **Khoảng `[begin,end)` hay `[begin,end]`?** `substring` dùng khoảng nửa mở.
4. **Cắt hay từ chối?** Ví dụ validation trả lỗi khi quá giới hạn, còn truncate chủ ý cắt. Đừng âm thầm cắt dữ liệu khi hợp đồng chỉ nói “validate”.
5. **Xử lý `null`, giới hạn âm, chỉ số ngoài phạm vi và surrogate lỗi ra sao?** Quy định ngoại lệ hoặc kết quả rõ ràng.
6. **Có chuẩn hóa đầu vào trước khi đo không?** Ví dụ Unicode NFC, trim hoặc case conversion có thể làm thay đổi độ dài; phải xác định thứ tự xử lý.

### 7.2. Dùng kiểu riêng khi có nhiều đơn vị

Nếu cùng một hệ thống thường xử lý cả byte offset và UTF-16 offset, có thể tách kiểu để tránh truyền nhầm:

~~~java
record Utf16Index(int value) {}
record CodePointIndex(int value) {}
record Utf8ByteLimit(int value) {}
~~~

Đây là các khai báo kiểu **minh họa** (Java 17). Bản thân record không tự bảo đảm chỉ số hợp lệ; cần kiểm tra phạm vi khi dùng với chuỗi cụ thể. Nhưng chữ ký nhận `Utf16Index` sẽ khó bị nhầm với `CodePointIndex` hơn là khi tất cả đều nhận `int`.

### 7.3. Ghi quy tắc trong tài liệu API và ví dụ

Ví dụ hợp đồng có thể viết:

> `takeCodePoints(text, n)` trả về tiền tố gồm tối đa `n` Unicode code point của `text`, giữ thứ tự ban đầu, không cắt đôi surrogate pair hợp lệ. `n` phải không âm. Phương thức không hứa hẹn giữ nguyên extended grapheme cluster, không chuẩn hóa Unicode và không kiểm soát số byte UTF-8.

Mô tả này có thể kiểm tra được qua unit test. Câu “lấy tối đa n ký tự” không thể hiện đầy đủ các cam kết như vậy.

## 8. Ví dụ Java 17+ đầy đủ có thể biên dịch và chạy

Lưu tệp `StringUnitContractDemo.java`. Ví dụ dùng biểu diễn Unicode escape trong source để không phụ thuộc bộ gõ emoji khi sao chép mã:

~~~java
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class StringUnitContractDemo {
    static String takeCodePoints(String input, int maxCodePoints) {
        Objects.requireNonNull(input, "input");
        if (maxCodePoints < 0) {
            throw new IllegalArgumentException("maxCodePoints < 0");
        }
        int available = input.codePointCount(0, input.length());
        int endUtf16 = input.offsetByCodePoints(
                0, Math.min(maxCodePoints, available));
        return input.substring(0, endUtf16);
    }

    static boolean fitsUtf8Bytes(String input, int maxUtf8Bytes) {
        Objects.requireNonNull(input, "input");
        if (maxUtf8Bytes < 0) {
            throw new IllegalArgumentException("maxUtf8Bytes < 0");
        }
        return input.getBytes(StandardCharsets.UTF_8).length
                <= maxUtf8Bytes;
    }

    static boolean isWellFormedUtf16(String input) {
        Objects.requireNonNull(input, "input");
        for (int i = 0; i < input.length(); i++) {
            char unit = input.charAt(i);
            if (Character.isHighSurrogate(unit)) {
                if (i + 1 == input.length()
                        || !Character.isLowSurrogate(input.charAt(i + 1))) {
                    return false;
                }
                i++; // Bỏ qua low surrogate của cặp đã kiểm tra.
            } else if (Character.isLowSurrogate(unit)) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) {
        // A + emoji 😃 + e + dấu sắc kết hợp U+0301
        String text = "A\uD83D\uDE03e\u0301";

        System.out.println("utf16Units=" + text.length());
        System.out.println("codePoints=" + text.codePoints().count());
        System.out.println("utf8Bytes=" +
                text.getBytes(StandardCharsets.UTF_8).length);

        int beginUtf16 = text.offsetByCodePoints(0, 1);
        int endUtf16 = text.offsetByCodePoints(beginUtf16, 1);
        System.out.println("emojiEquals=" + text.substring(
                beginUtf16, endUtf16).equals("\uD83D\uDE03"));

        System.out.println("prefixEquals=" +
                takeCodePoints(text, 2).equals("A\uD83D\uDE03"));

        String broken = text.substring(0, 2);
        System.out.println("brokenWellFormed=" +
                isWellFormedUtf16(broken));
        System.out.println("originalWellFormed=" +
                isWellFormedUtf16(text));

        System.out.println("within4CodePoints=" +
                (text.codePointCount(0, text.length()) <= 4));
        System.out.println("within7Utf8Bytes=" +
                fitsUtf8Bytes(text, 7));

        System.out.printf("emojiCodePoint=U+%X%n",
                text.codePointAt(1));
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 StringUnitContractDemo.java
java StringUnitContractDemo
~~~

Kết quả `stdout`:

~~~text
utf16Units=5
codePoints=4
utf8Bytes=8
emojiEquals=true
prefixEquals=true
brokenWellFormed=false
originalWellFormed=true
within4CodePoints=true
within7Utf8Bytes=false
emojiCodePoint=U+1F603
~~~

**Giải thích:**

- `length()` cho `5` vì emoji chiếm hai `char`, còn chữ `e` và dấu sắc kết hợp là hai code unit riêng.
- `codePoints().count()` cho `4`: `A`, emoji, `e`, dấu sắc kết hợp.
- `getBytes(UTF_8).length` cho `8`; do đó giới hạn **7 byte** bị vượt, mặc dù giới hạn **4 code point** vẫn vừa.
- Dùng `offsetByCodePoints` để tính hai mốc UTF-16 giúp lấy đầy đủ emoji.
- `substring(0, 2)` vô tình kết thúc ngay sau **high surrogate**, nên `isWellFormedUtf16` phát hiện dữ liệu bị cắt hỏng.
- Kiểm tra UTF-16 hợp lệ trong ví dụ là bước riêng, không được thay thế bởi kiểm tra `length()` hay số code point.

Hàm `fitsUtf8Bytes` minh họa đo byte với đầu vào hợp lệ; nếu ứng dụng yêu cầu nghiêm ngặt với đầu vào lỗi, hãy gọi `isWellFormedUtf16` hoặc dùng encoder báo lỗi trước khi chấp nhận dữ liệu.

## 9. Thứ tự chuẩn hóa, validation và lưu trữ

Một quy trình xử lý giá trị chuỗi nên xác định thứ tự các bước trước khi đặt giới hạn:

1. **Giải mã đầu vào** theo encoding đã thỏa thuận (ví dụ UTF-8), với chính sách rõ ràng cho dữ liệu lỗi.
2. **Xác thực** ký tự hoặc quy tắc nghiệp vụ; xác định có chấp nhận surrogate lỗi không.
3. **Chuẩn hóa nếu hợp đồng yêu cầu**: ví dụ Unicode NFC hoặc quy tắc xử lý whitespace nhất quán.
4. **Đo theo đơn vị đã công bố**: code point, grapheme hay byte UTF-8 sau mã hóa.
5. **Từ chối hoặc cắt** theo chính sách của API, rồi lưu/truyền theo định dạng mong muốn.

Không nên cho rằng `Normalizer.normalize`, `strip()`, `toLowerCase()` hay serializer là các thao tác “không ảnh hưởng độ dài”. Chúng có thể thay đổi số code point, UTF-16 unit hoặc byte. Nếu kiểm tra độ dài trên giá trị **trước xử lý** nhưng lưu giá trị **sau xử lý**, hợp đồng phải giải thích rõ đơn vị và giai đoạn đo.

Ví dụ, `"é"` dạng U+00E9 và `"e\u0301"` (U+0065 + U+0301) có thể trông giống nhau nhưng trước NFC có **1** và **2** code point tương ứng; số byte UTF-8 cũng khác. Nếu hai dạng phải được chấp nhận như cùng dữ liệu, chuẩn hóa trước validation là một quyết định thiết kế cần nêu tường minh.

## 10. Các trường hợp kiểm thử thiết yếu

| Dữ liệu | UTF-16 unit | Code point | UTF-8 byte | Điểm cần xác minh |
| --- | ---: | ---: | ---: | --- |
| `"ABC"` | 3 | 3 | 3 | ASCII: mọi đơn vị trùng nhau |
| `"é"` (U+00E9) | 1 | 1 | 2 | BMP nhưng UTF-8 cần hai byte |
| `"e\u0301"` | 2 | 2 | 3 | Hai code point thường tạo một grapheme |
| `"😃"` | 2 | 1 | 4 | Supplementary code point dùng surrogate pair |
| `"A😃e\u0301"` | 5 | 4 | 8 | Các giới hạn theo đơn vị khác nhau |
| Chuỗi rỗng `""` | 0 | 0 | 0 | Trường hợp biên |
| Surrogate đứng lẻ | Tùy đầu vào | Được `codePoints()` xuất riêng | Phụ thuộc chính sách encoder | Cần validation cho dữ liệu Unicode lỗi |

Để kiểm thử một API cắt chuỗi, còn cần các trường hợp:

- Giới hạn bằng 0, bằng độ dài, nhỏ hơn và lớn hơn độ dài.
- Giá trị giới hạn âm và chỉ số ngoài phạm vi.
- Điểm cắt ngay **trước**, **giữa** và **sau** surrogate pair.
- Điểm cắt giữa chữ cơ sở và dấu kết hợp.
- Emoji nhiều code point có ZWJ/variation selector; kiểm thử theo phiên bản Unicode đã chọn.
- Dữ liệu với ký tự có độ dài UTF-8 1, 2, 3 và 4 byte.
- Chuỗi có Unicode normalization khác nhau nhưng hình thức hiển thị giống nhau.
- Biên của giới hạn byte tính trên **giá trị thực sự được gửi hoặc lưu**.

## 11. Checklist khi công bố API

- **Đơn vị nào?** `utf16Unit`, `codePoint`, `graphemeCluster`, `utf8Byte` hay số byte payload sau serialize?
- **Chỉ số nào?** 0-based/1-based và khoảng đóng/mở?
- **Đếm ở bước nào?** Trước hay sau normalize, trim, encode, escape hoặc serialize?
- **Quá giới hạn thì sao?** Trả lỗi hay cắt? Có giữ nguyên grapheme không?
- **Dữ liệu lỗi thì sao?** Surrogate lẻ, encoding không hợp lệ, `null`, giới hạn âm?
- **Có tương thích API khác?** `substring()` cần offset UTF-16; bộ phân đoạn grapheme có phiên bản Unicode cụ thể.
- **Đã test trên Unicode thực tế chưa?** Supplementary, combining mark, emoji ghép, các ký tự nhiều byte.

**Kết luận:** trong API Java, từ “ký tự” thường quá mơ hồ để làm đơn vị chỉ số hoặc giới hạn. `String.length()` và `substring()` dùng **UTF-16 code unit**; `codePoints()` dùng **Unicode code point**; độ dài hiển thị cần xem **grapheme cluster**; giới hạn truyền/lưu thường cần **byte theo encoding**. Hãy đặt tên và viết hợp đồng thể hiện chính xác đơn vị, quy tắc biên và thứ tự chuẩn hóa, rồi kiểm thử những chuỗi Unicode mà mỗi đơn vị cho kết quả khác nhau.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-api-design-005">Tách dữ liệu nguyên bản, dữ liệu chuẩn hóa và bản hiển thị</a>

<details>
<summary>Xem chi tiết</summary>

# Tách dữ liệu nguyên bản, dữ liệu chuẩn hóa và bản hiển thị khi thiết kế API String

## Mục tiêu

Trong ứng dụng thực tế, cùng một chuỗi có thể đảm nhận ba vai trò khác nhau: bằng chứng về những gì người dùng gửi, giá trị đã được chuẩn hóa để tìm kiếm/đối chiếu, và văn bản được hiển thị cho người đọc. Khi gộp cả ba vào một biến `String` rồi liên tục ghi đè, ứng dụng dễ mất dữ liệu, đối chiếu sai hoặc hiển thị không đúng ý người dùng.

Sau bài này, bạn sẽ biết cách:

- Phân biệt **giá trị nguyên bản (raw/original)**, **giá trị chuẩn hóa (normalized/canonical key)** và **giá trị hiển thị (display)**.
- Chọn rõ chính sách cho `strip()`, chuẩn hóa Unicode bằng `Normalizer`, chuyển hoa/thường bằng `Locale` và giới hạn của các phép biến đổi này.
- Thiết kế API Java 17 với dữ liệu bất biến, không đánh mất nguồn gốc.
- Xử lý các tình huống tìm kiếm, kiểm tra trùng, lưu trữ, thay đổi chính sách và bảo mật mà không tự làm hỏng văn bản.
- Viết kiểm thử cho chuỗi Unicode trông giống nhau, khoảng trắng và các giá trị đặc biệt.

## 1. Ba vai trò dữ liệu không nên bị trộn lẫn

Ví dụ người dùng nhập tên hiển thị có dấu cách đầu/cuối và chữ `é` theo dạng chữ `e` cộng dấu kết hợp:

~~~text
Dữ liệu nhận được: "  Café  "
                      ↓
Raw/original:     "  Café  "      // giữ nguyên bản String đã nhận
                      ↓
Normalized key:   "café"          // chính sách ví dụ: strip + NFC + lowercase ROOT
                      ↓
Display value:    "Café"          // chính sách ví dụ: strip + NFC, giữ chữ hoa ban đầu
~~~

Trong ví dụ, `"Café"` dùng hai code point cho `e` và dấu sắc, còn `"Café"` dùng một code point `é`. Mắt thường có thể thấy giống nhau dù chuỗi mã khác nhau.

| Tầng | Câu hỏi mà giá trị trả lời | Điều thường cần bảo toàn |
| --- | --- | --- |
| **Raw** | “Hệ thống đã nhận đúng những gì?” | Ký tự, khoảng trắng, chữ hoa/thường, dấu, thứ tự code unit sau khi giải mã |
| **Normalized** | “Hai giá trị có được coi tương đương cho nghiệp vụ này không?” | Quy tắc chuẩn hóa xác định, có thể tái lập, phù hợp khóa tra cứu |
| **Display** | “Người đọc nên thấy gì trong ngữ cảnh này?” | Dễ đọc, phù hợp locale/ngữ cảnh, không phá dữ liệu gốc |

Đây là **các vai trò**, không phải ba bước bắt buộc cho mọi trường dữ liệu. Một số trường hiển thị đúng nguyên bản; một số khóa định danh không được phép bỏ chữ hoa; một số dữ liệu nhạy cảm phải so khớp chính xác từng byte. API cần định nghĩa chính sách riêng cho từng loại trường.

**Raw trong bài này là `String` sau khi ứng dụng đã giải mã đầu vào.** Nếu yêu cầu là lưu nguyên byte phục vụ chữ ký số, kiểm tra mã hóa hoặc pháp lý, hãy giữ thêm byte gốc và thông tin charset. Một `String` đã được giải mã không bảo đảm tái tạo 100% byte ban đầu khi nguồn có dữ liệu không hợp lệ hoặc dùng nhiều cách mã hóa.

## 2. Vì sao không nên chuẩn hóa đè lên biến gốc?

Cách viết dễ gây mất thông tin:

~~~java
String name = requestName;
name = name.strip();
name = name.toLowerCase();
save(name); // Chỉ còn phiên bản biến đổi; dữ liệu người dùng nhập đã mất.
~~~

Các vấn đề có thể xảy ra:

- `strip()` bỏ khoảng trắng theo `Character.isWhitespace` ở hai đầu. Điều này có thể đúng với tên hiển thị, nhưng sai với nội dung văn bản tự do, mã bảo mật hoặc trường có khoảng trắng mang ý nghĩa.
- `toLowerCase()` không chỉ thay chữ hoa thành chữ thường một cách đơn giản; hành vi mặc định phụ thuộc `Locale.getDefault()` của môi trường, có thể tạo kết quả khác nhau giữa máy hoặc người dùng.
- Chuẩn hóa Unicode có thể thay đổi chuỗi code unit; một số phép chuẩn hóa tương thích còn thay đổi cách trình bày ký tự.
- Khi người dùng muốn xem lại hoặc chỉnh sửa chính xác phần họ đã nhập, bạn không còn nguồn ban đầu.

Mô hình dễ kiểm soát hơn:

~~~java
String raw = requestName;
String normalized = normalizeForLookup(raw);
String display = prepareForDisplay(raw);
~~~

Không nên coi `normalized` là “chuỗi đúng” trong mọi tình huống. Nó là kết quả **theo một chính sách cụ thể**, chẳng hạn khóa tìm người dùng hoặc khóa so sánh tên nhãn. Mỗi chính sách cần mục đích, thứ tự biến đổi và kiểm thử rõ ràng.

## 3. Ví dụ Java 17+: raw, normalized và display

Chính sách minh họa cho **tên nhãn không rỗng**:

1. Giữ nguyên `String` người dùng nhập.
2. Khi tạo giá trị hiển thị, loại khoảng trắng theo `strip()` ở hai đầu và chuẩn hóa Unicode về **NFC**; vẫn giữ cách viết hoa/thường người dùng chọn.
3. Khi tạo khóa tìm kiếm, lấy giá trị đã xử lý ở bước 2 rồi gọi `toLowerCase(Locale.ROOT)` để có khóa ổn định theo locale trung lập.

Lưu chương trình sau vào `TextLayersDemo.java`:

~~~java
import java.text.Normalizer;
import java.util.Locale;

public class TextLayersDemo {
    static String toDisplay(String raw) {
        return Normalizer.normalize(raw.strip(), Normalizer.Form.NFC);
    }

    static String toLookupKey(String raw) {
        return toDisplay(raw).toLowerCase(Locale.ROOT);
    }

    public static void main(String[] args) {
        String raw = "  Cafe\u0301  ";

        String display = toDisplay(raw);
        String key = toLookupKey(raw);

        System.out.println("Raw length: " + raw.length());
        System.out.println("Display: " + display);
        System.out.println("Lookup key: " + key);
        System.out.println("Raw retained: " + raw.equals("  Cafe\u0301  "));
        System.out.println("Same key: "
                + key.equals(toLookupKey("CAFÉ")));
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 TextLayersDemo.java
java TextLayersDemo
~~~

Kết quả:

~~~text
Raw length: 9
Display: Café
Lookup key: café
Raw retained: true
Same key: true
~~~

Giải thích:

- `"  Cafe\u0301  "` có 9 đơn vị mã UTF-16: hai dấu cách, `C`, `a`, `f`, `e`, một dấu kết hợp và hai dấu cách.
- `strip()` bỏ khoảng trắng đầu/cuối theo chính sách ví dụ.
- `Normalizer.normalize(..., Normalizer.Form.NFC)` hợp nhất dạng `e` + dấu sắc thành `é` khi có dạng tổ hợp chuẩn tương ứng.
- `toLowerCase(Locale.ROOT)` cho `"café"` từ `"Café"`, không phụ thuộc locale mặc định của máy.
- Giá trị `raw` **không bị thay đổi**, vì `String` bất biến; mọi phép biến đổi trả về `String` mới hoặc giá trị có thể dùng lại nếu không cần thay đổi.
- `"CAFÉ"` và `"  Cafe\u0301  "` có cùng khóa theo chính sách hiện tại, nhưng bản hiển thị của chúng vẫn có thể khác nhau.

`Locale.ROOT` phù hợp cho nhiều chính sách khóa không gắn với ngôn ngữ. Nó **không** đồng nghĩa với phép *full Unicode case folding* hoàn chỉnh hay so sánh ngôn ngữ học. Khi tìm kiếm tên người theo tiếng Việt, Thổ Nhĩ Kỳ hoặc ngôn ngữ cụ thể, hãy đánh giá chính sách dựa trên yêu cầu tìm kiếm và collation thực tế.

## 4. Chuẩn hóa Unicode: NFC và NFKC khác nhau thế nào?

Java cung cấp `java.text.Normalizer` với các dạng `NFC`, `NFD`, `NFKC` và `NFKD`:

| Dạng | Mục đích tổng quát | Ví dụ giá trị sử dụng |
| --- | --- | --- |
| `NFC` | Phân rã chuẩn tắc rồi tổ hợp chuẩn tắc | Chuẩn hóa biểu diễn chuỗi văn bản khi muốn bảo toàn tương đương chuẩn tắc |
| `NFD` | Phân rã chuẩn tắc | Phân tích dấu kết hợp hoặc xử lý có yêu cầu riêng |
| `NFKC` | Phân rã tương thích rồi tổ hợp | Khóa có chính sách gộp cả ký tự tương thích |
| `NFKD` | Phân rã tương thích | Xử lý đặc thù cần tách các ký tự tương thích |

**NFC** thường là điểm bắt đầu an toàn hơn cho văn bản thông thường vì hướng tới tương đương chuẩn tắc. **NFKC** có thể gộp các ký tự vốn khác nhau về dạng trình bày/chức năng, nên cần quyết định nghiệp vụ trước khi sử dụng.

Ví dụ Java 17:

~~~java
import java.text.Normalizer;

public class UnicodeFormDemo {
    public static void main(String[] args) {
        String decomposed = "Cafe\u0301";
        String composed = "Café";

        System.out.println(decomposed.equals(composed));
        System.out.println(Normalizer.normalize(
                decomposed, Normalizer.Form.NFC).equals(composed));

        String circledOne = "①";
        System.out.println(Normalizer.normalize(
                circledOne, Normalizer.Form.NFC));
        System.out.println(Normalizer.normalize(
                circledOne, Normalizer.Form.NFKC));
    }
}
~~~

Lưu `UnicodeFormDemo.java` và chạy:

~~~bash
javac -encoding UTF-8 UnicodeFormDemo.java
java UnicodeFormDemo
~~~

Kết quả:

~~~text
false
true
①
1
~~~

Hai cách viết `é` không `equals` nhau ban đầu nhưng bằng nhau sau NFC; `NFKC` biến ký tự số một trong vòng tròn thành `1`, trong khi `NFC` giữ nguyên. Nếu `①` và `1` là hai mã sản phẩm khác nhau, NFKC làm chúng va chạm khóa — vì vậy không áp dụng NFKC rộng rãi mà thiếu chính sách.

### Thứ tự biến đổi có quan trọng không?

Có. `strip`, chuẩn hóa Unicode, chuyển hoa/thường, xóa dấu và thay khoảng trắng không tự động giao hoán trong mọi trường hợp. Hãy **định nghĩa một pipeline chính thức**, kiểm thử nhiều loại dữ liệu và giữ phiên bản của pipeline nếu khóa đã được lưu lâu dài. Ví dụ, nếu chuyển hoa/thường tạo thêm ký tự Unicode, có hệ thống chọn chuẩn hóa về NFC một lần nữa ở cuối để bảo đảm dạng đầu ra mong muốn.

Với ví dụ đơn giản của bài:

~~~text
raw
  ├── raw: giữ nguyên
  ├── display: strip → NFC
  └── lookupKey: strip → NFC → lowercase(Locale.ROOT)
~~~

Đây là một **quy tắc ứng dụng minh họa**, không phải thứ tự bắt buộc do Java áp đặt.

## 5. Tách biến đổi để dùng đúng mục đích

### Trường hợp A: tên hiển thị và khóa tìm kiếm

Một ứng dụng danh mục có thể muốn hiển thị `"Café"` nhưng tìm được khi người dùng gõ `"café"`. Hãy lưu bản đã nhập, hiển thị theo chính sách của sản phẩm và đối chiếu bằng khóa riêng.

~~~text
original    = "  Café "
display     = "Café"
searchKey   = "café"
~~~

Không xóa dấu theo mặc định. Nếu cần tìm không phân biệt dấu, hãy thiết kế thêm **một chỉ mục tìm kiếm** riêng và kiểm thử với ngôn ngữ được hỗ trợ. Xóa toàn bộ dấu kết hợp sau NFD có thể làm thay đổi danh tính từ trong nhiều ngôn ngữ, không bảo đảm tạo cách viết phù hợp cho mọi chữ có dấu.

### Trường hợp B: mã định danh có định dạng quy định

Nếu mã hàng yêu cầu chính xác `[A-Z0-9_-]+`, quy tắc có thể là từ chối khoảng trắng, chữ thường hoặc Unicode không hợp lệ thay vì âm thầm `strip` và viết hoa. Với trường như mã đơn hàng, **validate trước và canonicalize có chủ đích** để tránh hai mã hợp lệ khác nhau bị gộp ngoài ý muốn.

~~~java
static boolean isValidCode(String candidate) {
    return candidate != null
            && candidate.matches("[A-Z0-9_-]+");
}
~~~

Chính sách này coi `"ID_42"` là hợp lệ, còn `" id_42 "` không hợp lệ; đây là **quyết định nghiệp vụ**, không phải giới hạn của API `String`.

### Trường hợp C: nội dung văn bản tự do

Tin nhắn, chú thích, địa chỉ hoặc ghi chú có thể cần bảo toàn xuống dòng, dấu cách và cách viết hoa. Khi đó, raw và display có thể cùng là chuỗi gốc. Một khóa tìm kiếm hoặc chỉ mục全文 có thể được tạo độc lập nhưng không nên ghi đè nội dung tác giả.

### Trường hợp D: mật khẩu, token và dữ liệu ký

Không tự động `trim`, `strip`, chuyển lowercase hoặc Unicode normalize mật khẩu/token nếu giao thức xác thực không quy định rõ. Thay đổi ký tự có thể biến hai bí mật khác nhau thành một, khiến chữ ký không kiểm tra được hoặc phá tương thích. Nơi nào cần xử lý ở mức byte thì giữ đúng biểu diễn byte/protocol; mật khẩu phải được xử lý và lưu bằng cơ chế băm chuyên dụng, không lưu raw trong trường hợp không cần thiết.

## 6. Thiết kế kiểu giá trị bất biến để tránh nhầm lớp dữ liệu

Một cách giảm sai sót là cho đối tượng chứa các lớp thông tin với tên rõ nghĩa. Ví dụ này chọn chính sách của **nhãn danh mục**; không dùng nguyên xi cho mọi loại chuỗi trong hệ thống.

Lưu thành `LabelValueDemo.java`:

~~~java
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

final class LabelValue {
    private final String raw;
    private final String normalizedKey;
    private final String display;

    private LabelValue(String raw) {
        this.raw = Objects.requireNonNull(raw, "raw");

        String prepared = Normalizer.normalize(
                raw.strip(), Normalizer.Form.NFC);

        if (prepared.isEmpty()) {
            throw new IllegalArgumentException("Label must not be blank");
        }

        this.display = prepared;
        this.normalizedKey = prepared.toLowerCase(Locale.ROOT);
    }

    public static LabelValue of(String raw) {
        return new LabelValue(raw);
    }

    public String raw() {
        return raw;
    }

    public String normalizedKey() {
        return normalizedKey;
    }

    public String display() {
        return display;
    }
}

public class LabelValueDemo {
    public static void main(String[] args) {
        LabelValue first = LabelValue.of("  Cafe\u0301 ");
        LabelValue second = LabelValue.of("CAFÉ");

        System.out.println(first.raw().length());
        System.out.println(first.display());
        System.out.println(first.normalizedKey());
        System.out.println(second.display());
        System.out.println(first.normalizedKey()
                .equals(second.normalizedKey()));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 LabelValueDemo.java
java LabelValueDemo
~~~

Kết quả:

~~~text
8
Café
café
CAFÉ
true
~~~

Ở đây `raw()` luôn trả về dữ liệu ứng dụng đã nhận. `display()` chuẩn bị để hiển thị theo chính sách nhãn; `normalizedKey()` phục vụ đối chiếu, không thay thế bản hiển thị. Các trường `final` giúp giữ ba giá trị nhất quán từ lúc khởi tạo. Không có setter âm thầm đổi một lớp dữ liệu trong khi các lớp còn lại chưa cập nhật.

Đối tượng ví dụ minh họa quy tắc kiểm tra `prepared.isEmpty()`. Trong API thật, bạn còn phải quyết định về độ dài tối đa, ký tự điều khiển, dấu cách không ngắt dòng, các định dạng Unicode đặc biệt và lỗi xác thực. `String.strip()` không loại mọi loại khoảng trắng theo cách con người có thể tưởng tượng; ví dụ một số ký tự `NO-BREAK SPACE` không được `Character.isWhitespace` xem là khoảng trắng để bỏ đi.

**Đừng dùng `normalizedKey` thay cho khóa định danh toàn cục một cách vô điều kiện.** Nếu hai nhãn khác nhau tạo cùng normalized key, chúng có thể được coi trùng trong chức năng tìm kiếm nhưng vẫn là hai bản ghi khác nhau cần định danh riêng bằng ID.

## 7. Thiết kế hợp đồng API: đặt tên và phân tách rõ ràng

Tránh một trường tên chung chung `value` khi sản phẩm có ba mục đích khác nhau. Có thể dùng DTO:

~~~java
public record LabelResponse(
        String original,
        String normalizedKey,
        String displayText) {}
~~~

Đoạn trên là khai báo record Java 17 hợp lệ để minh họa **cấu trúc dữ liệu**; nó không phải chương trình chạy độc lập và không bắt buộc mọi trường đều được trả về cho client.

Một số nguyên tắc thiết kế:

- **Request đầu vào:** dùng tên như `submittedText` hoặc `rawText` khi cần lưu chính xác phần người dùng gửi.
- **Domain/value object:** một factory có chính sách rõ tạo `normalizedKey`; coi chính sách đó là một phần của hợp đồng nghiệp vụ.
- **Response cho giao diện:** thường chỉ gửi `displayText` và ID; không gửi raw nếu raw có thể chứa dữ liệu nhạy cảm hoặc hệ thống không cho phép người xem truy cập.
- **Search/lookup:** có thể nhận truy vấn của người dùng, chuẩn hóa **truy vấn bằng cùng phiên bản chính sách** rồi so khớp với khóa lưu.
- **Audit:** nếu cần chứng minh nguồn nhập, lưu raw theo quy định và kiểm soát truy cập/retention; không nhét dữ liệu bí mật vào log chỉ vì muốn “bảo toàn nguyên bản”.

Một API cũng nên ghi rõ: trả về Unicode theo dạng nào, khóa có phân biệt hoa/thường và dấu hay không, có cho phép chuỗi rỗng không, và chuẩn hóa xảy ra tại thời điểm nhận dữ liệu hay khi tìm kiếm.

## 8. Lưu trữ và tính nhất quán lâu dài

Khi cùng lưu raw, normalized và display trong cơ sở dữ liệu, cần quyết định tầng nào là nguồn gốc và khi nào tái tính:

| Trường dữ liệu | Ví dụ | Nên dùng cho |
| --- | --- | --- |
| `raw_text` | `"  Café  "` | Truy vết dữ liệu đã nhập; chỉnh sửa/cập nhật |
| `normalized_key` | `"café"` | Tìm kiếm, lọc, kiểm tra trùng theo chính sách |
| `display_text` | `"Café"` | Xuất giao diện nếu giá trị đã chuẩn bị được lưu |
| `normalization_version` | `"label-v1"` | Biết khóa được tạo theo chính sách nào khi thay đổi thuật toán |

Trong nhiều hệ thống, có thể **không cần lưu riêng display** nếu nó luôn được suy ra nhanh, xác định từ raw cùng một chính sách. Ngược lại, nếu bản hiển thị là nội dung do biên tập viên chỉnh tay, bản dịch theo `Locale` hoặc kết quả từ dữ liệu nguồn khác, cần mô hình rõ nguồn và vòng đời của nó.

### Khi thay đổi chính sách

Giả sử trước đây khóa chỉ được `strip`, sau này thêm lowercase và NFC:

1. Xác định các khóa mới có tạo **va chạm** hay không: `"Café"`, `"Café"`, `"CAFÉ"` có thể cùng thành `"café"`.
2. Kiểm tra các ràng buộc duy nhất (*unique constraints*) và quyết định cách giải quyết bản ghi va chạm trước khi triển khai.
3. Tái tạo `normalized_key` hoặc xây chỉ mục mới theo phiên bản mới, đồng thời đảm bảo các luồng ghi/đọc dùng đúng phiên bản.
4. Không ghi đè `raw_text` chỉ để cập nhật khóa tìm kiếm.
5. Nếu logic truy vấn và logic lưu dùng khác chính sách, kết quả tìm kiếm sẽ không đáng tin; cần đồng bộ và kiểm thử migration.

**Idempotence** là đặc tính hữu ích: chuẩn hóa một khóa đã chuẩn hóa bằng cùng chính sách nên cho cùng kết quả khi có thể. Tuy nhiên, đừng mặc định mọi chuỗi thao tác tùy biến đều idempotent; hãy kiểm thử, nhất là khi trộn nhiều lần thay dấu/khoảng trắng và chuyển chữ.

### Khoảng cách giữa Java và cơ sở dữ liệu

Java `String.equals` so sánh nội dung code unit; cơ sở dữ liệu có thể dùng collation không phân biệt hoa/thường hoặc dấu. Vì vậy, ngay cả khi `normalizedKey` khác nhau theo Java, unique index theo collation của database vẫn có thể xem chúng như nhau (hoặc ngược lại). Chọn rõ ràng cách lưu, collation, ràng buộc unique và việc chuẩn hóa ở phía ứng dụng. Nếu khóa thực sự yêu cầu so sánh nhị phân xác định, cần cấu hình storage/index phù hợp với hệ quản trị cụ thể.

## 9. Bản hiển thị còn phụ thuộc ngữ cảnh

`displayText` không nhất thiết chỉ là `strip()` rồi NFC. Nó có thể cần:

- Ngôn ngữ hoặc locale người xem: tên ngôn ngữ, số, ngày, tiền tệ và thông điệp có thể khác nhau.
- Quy tắc định dạng giao diện: thêm dấu phân cách, rút gọn chuỗi dài, định dạng đa dòng, hiển thị phiên bản song ngữ.
- Phân quyền: có trường cần che một phần khi hiển thị, trong khi raw được giữ trong vùng dữ liệu bảo vệ.
- Bối cảnh HTML, JSON hay log: mỗi nơi có quy tắc **escaping khi xuất** khác nhau.

Nên phân biệt **biến đổi nội dung mang ý nghĩa hiển thị** với **escape cho ngữ cảnh đầu ra**. Ví dụ, khi đưa `displayText` lên web, giao diện phải bảo đảm dữ liệu văn bản được escape phù hợp để tránh HTML/script injection. Không lưu chuỗi HTML đã escape làm dạng “chuẩn hóa” chung; khi đó hiển thị trên nhiều kênh khác có thể bị double escaping.

Tương tự, dùng prepared statement để truyền giá trị khi truy vấn SQL. Unicode normalization hoặc lowercase không thay thế kiểm soát dữ liệu đầu ra, validation hay phân quyền.

## 10. Các lỗi thường gặp

**1. Chỉ giữ normalized và mất raw.** Người dùng không thể khôi phục cách họ nhập; hệ thống khó truy vết lỗi hoặc áp dụng chính sách mới. Giữ raw khi sản phẩm và quy định lưu trữ cho phép, đồng thời kiểm soát quyền truy cập.

**2. Dùng normalized key để hiển thị.** Nếu `"Nguyễn Văn An"` thành lowercase hoặc bỏ dấu ở khóa tra cứu, hiển thị chính khóa đó sẽ làm sai tên người. Dùng display theo chính sách riêng.

**3. Coi trim/strip là thao tác vô hại.** Khoảng trắng có thể có ý nghĩa trong ghi chú, mã hoặc token; `strip` còn khác `trim` ở tập ký tự xử lý. Xác định quy tắc theo trường dữ liệu, không tạo một hàm `normalizeEverything` cho cả ứng dụng.

**4. Không chỉ định Locale khi chuyển hoa/thường.** `toLowerCase()` dùng locale mặc định và có thể khác nhau giữa môi trường. Với khóa trung lập theo locale, dùng `toLowerCase(Locale.ROOT)`; với văn bản ngôn ngữ cụ thể, đánh giá đúng `Locale` và collation.

**5. Nhầm NFC với NFKC.** NFKC có thể biến ký tự tương thích như `①` thành `1`, làm mất phân biệt không mong muốn. Chọn dạng Unicode theo chính sách, kiểm thử ví dụ gần giống nhưng mang ý nghĩa khác.

**6. Coi lowercasing là case folding/collation đầy đủ.** `toLowerCase(Locale.ROOT)` không tự giải mọi tình huống Unicode/ngôn ngữ. Khi cần so khớp theo ngôn ngữ, cân nhắc `Collator` hoặc công nghệ chỉ mục tìm kiếm phù hợp.

**7. Áp dụng chính sách mới mà không phát hiện khóa trùng.** Việc thêm Unicode normalization và lowercase có thể làm nhiều bản ghi khác nhau cùng khóa. Kiểm tra va chạm, unique index và quá trình chuyển đổi trước khi thay chính sách.

**8. Chuẩn hóa mật khẩu/token/chữ ký tự phát.** Làm vậy thay dữ liệu bí mật hoặc phá chữ ký. Chỉ biến đổi khi đặc tả giao thức yêu cầu.

**9. Tách raw/normalized/display nhưng quên đồng bộ sau khi edit.** Khi người dùng cập nhật raw, phải cập nhật hoặc vô hiệu hóa mọi dẫn xuất tương ứng. Factory bất biến và logic chuyển đổi tập trung giúp giảm lỗi này.

**10. Log cả dữ liệu người dùng mà không chọn lọc.** Raw có thể chứa thông tin cá nhân. Khi cần đo lỗi chuẩn hóa, ưu tiên log mã lỗi, loại biến đổi và thông tin an toàn; quyết định lưu raw theo phân quyền và chính sách dữ liệu.

## 11. Bài tập thực hành và kiểm thử

Với chính sách `strip() → NFC → toLowerCase(Locale.ROOT)` cho lookup, còn display là `strip() → NFC`, dự đoán kết quả:

| Raw đầu vào | Display | Lookup key | Ghi chú |
| --- | --- | --- | --- |
| `"  Café  "` | `"Café"` | `"café"` | Bỏ dấu cách đầu/cuối |
| `"Cafe\u0301"` (dạng tách dấu) | `"Café"` | `"café"` | NFC hợp nhất dạng chuẩn tắc |
| `"CAFÉ"` | `"CAFÉ"` | `"café"` | Giữ hoa ở display |
| `"  "` | `""` | `""` | Ví dụ value object ở trên từ chối display rỗng |
| `"Alice"` | `"Alice"` | `"alice"` | Không phân biệt hoa/thường trong lookup |
| `"alice"` | `"alice"` | `"alice"` | Trùng lookup key với `Alice` |

Có thể dùng chương trình tự kiểm tra sau (lưu vào `TextLayerChecks.java`):

~~~java
import java.text.Normalizer;
import java.util.Locale;

public class TextLayerChecks {
    static String display(String raw) {
        return Normalizer.normalize(raw.strip(), Normalizer.Form.NFC);
    }

    static String key(String raw) {
        return display(raw).toLowerCase(Locale.ROOT);
    }

    public static void main(String[] args) {
        String[] inputs = {"  Café  ", "Cafe\u0301", "CAFÉ",
                "  ", "Alice", "alice"};

        for (String input : inputs) {
            System.out.println("[" + display(input) + "] -> ["
                    + key(input) + "]");
        }
    }
}
~~~

Chạy bằng `javac -encoding UTF-8 TextLayerChecks.java` và `java TextLayerChecks`. Kết quả:

~~~text
[Café] -> [café]
[Café] -> [café]
[CAFÉ] -> [café]
[] -> []
[Alice] -> [alice]
[alice] -> [alice]
~~~

Bài mở rộng: tạo một bộ kiểm thử bổ sung cho `null`, dấu cách không ngắt dòng, mã định danh phân biệt hoa/thường, ký tự `①` khi dùng NFC/NFKC, và dữ liệu tiếng Việt có dấu tách. Ghi rõ trường nào được chuẩn hóa, trường nào phải giữ nguyên, trường nào được hiển thị khác raw.

## Checklist trước khi công bố API

1. Định nghĩa chính xác **raw** là chuỗi đã giải mã hay cả byte gốc, dữ liệu đó có cần lưu/audit hay không.
2. Đặt tên và kiểu riêng cho **normalized key**; viết tài liệu về thứ tự pipeline, Unicode form, Locale và phiên bản chính sách.
3. Quyết định **display** dựa trên raw, trên giá trị do người dùng biên tập, hay trên tài nguyên dịch; đảm bảo không vô tình hiển thị khóa lookup.
4. Kiểm tra trùng lặp và collation ở cả Java và database; chuẩn bị cách xử lý va chạm khi thay thuật toán.
5. Đồng bộ dẫn xuất sau khi cập nhật raw, kiểm thử idempotence, Unicode, khoảng trắng, null, giá trị rỗng và chuỗi tương đương.
6. Với mật khẩu, token, dữ liệu ký và thông tin nhạy cảm, tuân thủ hợp đồng bảo mật riêng; không áp dụng pipeline tên hiển thị một cách máy móc.
7. Escape theo ngữ cảnh xuất (HTML/JSON/SQL), phân quyền dữ liệu raw, và tránh log nội dung không cần thiết.

## Tham khảo chính thức

- [Java SE 17 — String](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html)
- [Java SE 17 — Normalizer và các Unicode normalization form](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/Normalizer.html)
- [Java SE 17 — Locale](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Locale.html)
- [Java SE 17 — Collator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/Collator.html)
- [Java SE 17 — Character](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-api-design-006">Phân biệt văn bản thông thường với cấu trúc có ngữ pháp</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt văn bản thông thường với cấu trúc có ngữ pháp khi thiết kế API Java

## Mục tiêu

Một biến Java có kiểu `String` chỉ cho biết nó chứa một chuỗi ký tự Unicode. Kiểu `String` **không cho biết** nội dung đó là câu văn để hiển thị, mã đơn hàng, địa chỉ URL, biểu thức chính quy, truy vấn SQL hay tài liệu JSON. Những loại nội dung này có thể cùng được truyền qua `String`, nhưng mỗi loại có **quy tắc cấu trúc, ý nghĩa và cách xử lý khác nhau**.

Khi thiết kế API, cần nhận ra ranh giới quan trọng:

- **Văn bản thông thường (plain/free text):** chủ yếu là nội dung dành cho con người, ví dụ tên hiển thị, nhận xét, mô tả sản phẩm. Những ký tự như dấu phẩy, dấu ngoặc hoặc dấu `*` có thể chỉ là chính chúng.
- **Văn bản có ngữ pháp (structured text):** dãy ký tự được đọc bởi một bộ phân tích cú pháp, ví dụ `ORD-0042` theo quy tắc mã đơn hàng, `https://example.com?q=java` theo cú pháp URI, hoặc `a.*b` theo cú pháp regular expression.
- **Biểu diễn dữ liệu có cấu trúc:** dữ liệu có trường, phần tử, kiểu và quy tắc ràng buộc, ví dụ JSON hoặc CSV. Ở Java, có thể nhận dữ liệu dưới dạng văn bản, nhưng phải phân tích theo đúng ngữ pháp mới hiểu được cấu trúc.

Bài học giúp bạn xác định khi nào cần giữ nguyên chuỗi, khi nào cần parse và validate, và khi nào phải dùng API tạo cấu trúc thay vì ghép chuỗi thủ công.

## 1. Cùng là `String`, nhưng không cùng ý nghĩa

Hãy xét:

```java
String comment = "Tôi thích ký hiệu .* trong ví dụ";
String regex = ".*";
String productCode = "ORD-0042";
String jsonText = "{\"name\":\"Java\",\"count\":3}";
```

Bốn biến đều là `String`. Tuy nhiên:

| Giá trị | Vai trò | Cách xử lý đúng |
| --- | --- | --- |
| `comment` | Văn bản tự do để hiển thị hoặc lưu trữ | Giữ nguyên nội dung theo hợp đồng API; escape đúng khi đưa vào môi trường hiển thị có cú pháp |
| `regex` | Biểu thức chính quy | Đưa qua `Pattern.compile` nếu ứng dụng thực sự muốn diễn giải regex |
| `productCode` | Mã định danh có mẫu hợp lệ | Kiểm tra toàn bộ chuỗi theo quy tắc nghiệp vụ; cân nhắc value object riêng |
| `jsonText` | Tài liệu JSON | Dùng JSON parser để kiểm tra cú pháp và dựng kiểu dữ liệu; không tự tách bằng `split` đơn giản |

**Dấu câu không có ý nghĩa cố định cho mọi `String`.** Dấu `.` trong câu văn là dấu chấm, nhưng trong regex nó có thể đại diện cho một ký tự bất kỳ. Dấu `&` là một ký tự trong tên người dùng; khi đặt trong query string URL, nó thường ngăn cách các tham số. Ký tự `"` trong câu trích dẫn không đồng nghĩa với dấu mở/đóng chuỗi trong JSON nếu chưa đi qua bộ mã hóa JSON.

Nói cách khác, **ý nghĩa đến từ hợp đồng API và parser**, không phải chỉ từ việc giá trị có kiểu `String`.

## 2. Thiết kế API: nêu rõ đầu vào là nội dung hay cú pháp

Một API khó dùng thường gom mọi thứ vào tham số tên chung chung:

```java
void process(String value); // value là nội dung, URL, biểu thức hay mã?
```

Các tên và kiểu có ý nghĩa giúp người gọi hiểu rõ kỳ vọng:

```java
void saveDisplayName(String displayName);
void addOrderByCode(OrderCode code);
void fetchDocument(java.net.URI documentUri);
void searchLiteralText(String literalText);
void searchByPattern(java.util.regex.Pattern pattern);
```

Đây là **chữ ký minh họa**, không phải lớp Java chạy độc lập. `OrderCode` trong ví dụ là một value object do ứng dụng định nghĩa.

Một số quy tắc thiết kế có ích:

1. Với dữ liệu **hiển thị**, xác định có cần bảo toàn chính xác chữ hoa/thường, Unicode, dấu cách, xuống dòng và ký tự đặc biệt hay không. Đừng tự sửa văn bản gốc chỉ vì nó chứa ký tự trùng với dấu cú pháp của một định dạng khác.
2. Với **mã định danh**, định nghĩa tập ký tự, độ dài, phần tiền tố, chính sách hoa/thường, cách báo lỗi và việc có chuẩn hóa hay không.
3. Với **cú pháp dành cho máy**, ưu tiên loại đối tượng đã được parse khi thích hợp: `URI`, `Path`, `Pattern`, `LocalDate`, enum hoặc record giá trị đã kiểm chứng.
4. Với tài liệu **JSON/XML/CSV**, xác định parser/serializer và mô hình dữ liệu. Một JSON string bất kỳ chưa chắc là JSON hợp lệ.
5. Với dữ liệu người dùng được đưa vào **cú pháp khác**, hiểu cách encode hoặc parameterize đúng ngữ cảnh. Không đặt dữ liệu tùy ý vào vị trí mã lệnh bằng nối chuỗi.

API nên tách riêng hai trường hợp: **người dùng nhập các ký tự theo nghĩa nguyên văn** và **người dùng chủ động cung cấp một biểu thức theo cú pháp đã được công bố**. Ví dụ ô tìm kiếm thông thường không nên âm thầm hiểu dấu `*` hoặc `.` thành regex nếu giao diện chưa nói rõ điều đó.

## 3. Phân biệt xác thực dữ liệu với phân tích cú pháp

Ba thao tác liên quan nhưng không đồng nghĩa:

- **Validation:** kiểm tra giá trị có thỏa ràng buộc đã định không. Ví dụ mã đơn hàng phải khớp `ORD-` và bốn chữ số ASCII.
- **Parsing:** diễn giải chuỗi theo ngữ pháp và chuyển thành cấu trúc hoặc kiểu có ý nghĩa. Ví dụ `LocalDate.parse("2026-10-23")` tạo đối tượng ngày hoặc báo lỗi cú pháp.
- **Encoding/escaping:** chuyển ký tự dữ liệu thành biểu diễn hợp lệ ở một ngữ cảnh đầu ra, như tham số URL, chuỗi JSON, văn bản HTML. Việc này không tự chứng minh nội dung đầu vào là hợp lệ theo quy tắc nghiệp vụ.

Ví dụ `"ORD-0042"` có thể là mã đơn hàng hợp lệ theo chính sách ứng dụng, còn `"ORD-42 abc"` không hợp lệ. Dùng `Pattern.matcher(value).matches()` để yêu cầu **khớp toàn bộ chuỗi**:

```java
import java.util.regex.Pattern;

Pattern orderCode = Pattern.compile("ORD-[0-9]{4}");

System.out.println(orderCode.matcher("ORD-0042").matches()); // true
System.out.println(orderCode.matcher("ORD-42 abc").matches()); // false
```

`find()` tìm một đoạn con khớp mẫu; `matches()` yêu cầu cả input khớp. Với mã định danh, dùng `find()` có thể vô tình cho phép tiền tố/hậu tố thừa.

Cần phân biệt **hợp lệ về cú pháp** và **hợp lệ về nghiệp vụ**. `ORD-0042` đúng định dạng nhưng có thể không tồn tại trong cơ sở dữ liệu, không thuộc người gọi hoặc đã hết hiệu lực. Regex không thể thay thế kiểm tra quyền, trạng thái và tính duy nhất.

## 4. Regex: văn bản literal và ngôn ngữ biểu thức chính quy

Regex là ví dụ dễ thấy nhất cho việc chuỗi có ngữ pháp. Trong regex:

- `.` có nghĩa là một ký tự phù hợp, thay vì dấu chấm văn bản;
- `*` là toán tử lặp cho biểu thức đứng trước;
- `[ab]` là lớp ký tự gồm `a` hoặc `b`;
- `|` có nghĩa là lựa chọn giữa hai nhánh.

Nếu người dùng muốn tìm đúng chuỗi `a.b`, thì:

```java
Pattern wrong = Pattern.compile("a.b");
System.out.println(wrong.matcher("axb").find()); // true
```

Regex `a.b` cũng khớp `axb`. Đó không phải hành vi tìm kiếm literal.

Khi nhận **nội dung cần tìm nguyên văn**, dùng `Pattern.quote(literal)` để tạo một regex khớp đúng các ký tự của chuỗi literal:

```java
String literal = "a.b";
Pattern exactText = Pattern.compile(Pattern.quote(literal));

System.out.println(exactText.matcher("read a.b now").find()); // true
System.out.println(exactText.matcher("read axb now").find()); // false
```

Không cần biến mọi thao tác tìm chuỗi thành regex. Nếu yêu cầu chỉ là tìm đoạn con theo đúng ký tự, `String.contains` hoặc `indexOf` thường trực tiếp và dễ hiểu hơn:

```java
System.out.println("read a.b now".contains("a.b")); // true
```

**Bẫy của `String.split`:** tham số của `split(regex)` là **regex**, không phải dấu phân cách literal. Ví dụ `"A|B|C".split("|")` không thể được hiểu đơn giản là tách theo dấu `|`, vì `|` là toán tử regex. Để tách theo đúng một dấu phân cách từ biến:

```java
String delimiter = "|";
String[] parts = "A|B|C".split(Pattern.quote(delimiter), -1);
```

Tham số `-1` giữ cả các phần tử rỗng cuối chuỗi. Với định dạng CSV, chỉ dùng `split(",")` vẫn chưa đủ nếu có dấu phẩy bên trong trường được bao bởi dấu nháy; trường hợp đó cần parser CSV đúng quy tắc.

## 5. Khi chuỗi chứa URI, ý nghĩa các dấu phân cách đến từ cú pháp

Một người dùng có thể gõ \`An & Binh\` như tên hiển thị, nhưng khi chuỗi ấy trở thành **giá trị query** thì \`&\` có thể bị hiểu là dấu phân cách tham số nếu được nối trực tiếp. Với form-style query value, \`URLEncoder\` xử lý dữ liệu theo đúng quy tắc:

~~~java
String value = "An & Binh";
String encoded = java.net.URLEncoder.encode(
        value, java.nio.charset.StandardCharsets.UTF_8);
System.out.println(encoded); // An+%26+Binh
~~~

\`URLEncoder\` không phải bộ mã hóa chung cho **toàn URI, path segment hoặc fragment**. Nếu hợp đồng yêu cầu địa chỉ, dùng \`java.net.URI\`/URI builder phù hợp, phân tích scheme, host, path và kiểm tra đích sau khi resolve. Parse thành công không chứng minh địa chỉ được phép truy cập.

## 6. JSON và CSV là tài liệu có cấu trúc, không phải một danh sách ký tự

Với JSON, dấu \`",\`, backslash, cặp ngoặc, array/object và các kiểu dữ liệu mang nghĩa cú pháp. Với CSV, một dấu phẩy hoặc newline **có thể nằm bên trong trường được quote**, còn \`""\` là cách diễn tả dấu nháy trong dialect thông dụng. Vì vậy \`split(",")\` và \`split("\n")\` không đủ để phân tích tài liệu tổng quát.

~~~json
{"title":"Java, Strings","completed":true}
~~~

~~~csv
name,note
"Nguyễn, An","Câu nói ""Java"" rất hay"
~~~

Dùng JSON parser/serializer như Jackson hoặc CSV parser/writer theo dialect đã thống nhất; Java SE 17 không tích hợp serializer JSON tổng quát. Truyền object/record đã parse cho domain thay vì truyền JSON/CSV string mơ hồ đi qua nhiều tầng. Bài kế tiếp trình bày riêng các edge case và cách dùng thư viện.

## 7. Những cú pháp có thể thực thi cần tách biệt dữ liệu

Với SQL, truyền giá trị bằng \`PreparedStatement\` thay vì nối thành câu truy vấn. Với HTML, render văn bản bằng text node hoặc template có auto-escaping đúng vị trí; tránh ghép nội dung người dùng thành markup. Với lệnh hệ thống, ưu tiên \`ProcessBuilder\` truyền executable và từng đối số thay vì đưa cả chuỗi người dùng qua shell.

~~~java
String sql = "SELECT id FROM users WHERE name = ?";
try (java.sql.PreparedStatement ps = connection.prepareStatement(sql)) {
    ps.setString(1, userInput); // userInput là dữ liệu, không phải cú pháp SQL
}
~~~

Đây là đoạn code minh họa, giả định có \`java.sql.Connection connection\` và \`String userInput\`. Tham số SQL không thể thay tên bảng/cột; phần cấu trúc động vẫn cần allowlist. Trong HTML, JavaScript, CSS, SQL hoặc command, mỗi ranh giới có cú pháp riêng: một phép escape chung cho tất cả đều không đủ. Bài cuối chương đi sâu vào **trách nhiệm của từng output adapter**, bao gồm double escaping và decode nhiều lần.

## 8. Một kiểu dữ liệu riêng giúp API rõ hợp đồng

Một value object là lựa chọn hữu ích nếu chuỗi được dùng nhiều nơi với cùng cấu trúc. Ví dụ mã đơn hàng `ORD-` cộng bốn chữ số ASCII:

```java
import java.util.Objects;
import java.util.regex.Pattern;

record OrderCode(String value) {
    private static final Pattern FORMAT = Pattern.compile("ORD-[0-9]{4}");

    OrderCode {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid order code");
        }
    }
}
```

Đây là định nghĩa `record` Java 17 hợp lệ để đặt ở cấp top-level không `public` hoặc lồng trong lớp. Sau khi tạo thành công, đối tượng `OrderCode` đại diện cho giá trị đã được kiểm tra **theo mẫu quy định**. Những API cần mã đơn hàng có thể nhận `OrderCode` thay vì `String` không rõ ý nghĩa. Lưu ý không dùng mã hợp lệ về mặt cú pháp làm bằng chứng rằng đơn hàng thực sự tồn tại.

Không cần tạo một lớp mới cho mọi chuỗi thông thường. Các trường tên hiển thị, nội dung ghi chú, tiêu đề bài viết có thể vẫn là `String` nếu hợp đồng đủ rõ. Value object đem lại lợi ích lớn hơn khi một chuỗi có ngữ pháp/ràng buộc lặp lại ở nhiều API hoặc dễ bị nhầm với loại chuỗi khác.

## 9. Ví dụ Java 17+ chạy hoàn chỉnh

Ví dụ tổng hợp cách:

- giữ văn bản hiển thị đúng như đầu vào;
- xác thực toàn bộ chuỗi mã bằng ngữ pháp quy định;
- tìm literal chứa dấu chấm mà không coi nó là regex;
- mã hóa **giá trị query form**;
- tách bằng một delimiter literal có ý nghĩa đặc biệt trong regex.

Tạo file **`TextVsSyntaxDemo.java`**:

```java
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Pattern;

public class TextVsSyntaxDemo {

    private static final Pattern ORDER_CODE =
            Pattern.compile("ORD-[0-9]{4}");

    public static void main(String[] args) {
        String display = "  Mon an #42  ";
        System.out.println("Display: [" + display + "]");

        System.out.println(
                "Valid code: " + ORDER_CODE.matcher("ORD-0042").matches()
        );
        System.out.println(
                "Invalid code: " + ORDER_CODE.matcher("ORD-42 extra").matches()
        );

        String literal = "a.b";
        Pattern literalPattern = Pattern.compile(Pattern.quote(literal));

        System.out.println(
                "Literal found: "
                        + literalPattern.matcher("read a.b now").find()
        );
        System.out.println(
                "Not a regex wildcard: "
                        + literalPattern.matcher("read axb now").find()
        );

        String parameterValue = "An & Binh";
        String formEncoded = URLEncoder.encode(
                parameterValue,
                StandardCharsets.UTF_8
        );
        System.out.println("Query component: name=" + formEncoded);

        String[] parts = "A|B|C".split(Pattern.quote("|"), -1);
        System.out.println("Parts: " + Arrays.toString(parts));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 TextVsSyntaxDemo.java
java TextVsSyntaxDemo
```

Đầu ra:

```text
Display: [  Mon an #42  ]
Valid code: true
Invalid code: false
Literal found: true
Not a regex wildcard: false
Query component: name=An+%26+Binh
Parts: [A, B, C]
```

Giải thích kết quả:

- `display` được in nguyên vẹn, gồm hai dấu cách ở hai đầu. Giá trị là nội dung dành cho hiển thị nên không tự cắt hoặc parse thành ngữ pháp khác.
- `matches()` chấp nhận mã `ORD-0042` và từ chối `ORD-42 extra`, bởi regex bắt buộc toàn bộ mã có tiền tố và đúng bốn chữ số.
- `Pattern.quote("a.b")` khiến dấu chấm được tìm đúng như ký tự thật; nó không tùy ý khớp chữ `x`.
- `URLEncoder` xử lý dấu cách và `&` theo form encoding của giá trị tham số.
- `Pattern.quote("|")` giúp `split` hiểu `|` là dấu phân cách nguyên văn, không phải toán tử regex.

## 10. Bảng quyết định khi thiết kế một tham số `String`

| Câu hỏi cần hỏi | Nếu câu trả lời là… | Hướng thiết kế |
| --- | --- | --- |
| Giá trị có được hiển thị nguyên văn không? | Tên, mô tả, bình luận | Bảo toàn nội dung, xác định chính sách whitespace/Unicode riêng; escape khi xuất ra định dạng khác |
| Giá trị có một mẫu hợp lệ rõ ràng không? | Mã đơn, định danh phiên bản | Validate toàn bộ, báo lỗi rõ; cân nhắc value object |
| Giá trị là cú pháp do người dùng chủ động nhập không? | Regex, filter expression | Công bố ngữ pháp, parse/catch lỗi; phân biệt với tìm kiếm literal |
| Giá trị mang cấu trúc nhiều trường không? | JSON, CSV | Dùng parser/serializer phù hợp; không split hoặc nối tay |
| Giá trị được đặt vào một văn bản có cú pháp khác không? | URL, SQL, HTML, shell | Dùng encoding, parameterization hoặc API dựng cấu trúc đúng theo ngữ cảnh |
| Giá trị được so sánh trong collection không? | Key của map/set | Quyết định có chuẩn hóa hay giữ nguyên; kiểm tra collisions |
| Giá trị qua nhiều tầng API không? | HTTP input → service → repository | Tách ranh giới parse/validate ở tầng thích hợp, ưu tiên truyền kiểu đã kiểm chứng vào nội bộ |

Cần nhớ rằng **validation, escaping và authorization là ba việc độc lập**. Chuỗi đúng cú pháp vẫn có thể đề cập đến tài nguyên mà người dùng không có quyền truy cập; một giá trị đã được HTML-escape vẫn có thể vi phạm quy tắc dữ liệu; và giá trị hợp lệ trong JSON không có nghĩa sẽ an toàn nếu sau đó đưa nguyên văn vào câu SQL.

## 11. Lỗi thường gặp

1. **Xem mọi `String` là plain text.** Truyền văn bản người dùng vào `Pattern.compile`, SQL hay biểu thức template khiến các dấu có nghĩa điều khiển.
2. **Xem mọi dấu câu là cấu trúc.** Tự tách dấu phẩy, dấu chấm hoặc ký tự `&` trong tên/nhận xét dù chúng chỉ là văn bản hiển thị.
3. **Dùng `split` thay parser chuẩn.** JSON/CSV/URL có quy tắc quoting, escaping, nesting hoặc encoding; tách chuỗi đơn giản làm hỏng dữ liệu hợp lệ.
4. **Dùng `matches()` và `find()` lẫn lộn.** Mã định danh cần hợp lệ toàn bộ; tìm một đoạn nội dung thì cần semantics khác.
5. **Dùng một dạng escaping cho nhiều ngữ cảnh.** SQL parameter, JSON escaping, URL encoding và HTML escaping giải quyết các ngữ pháp khác nhau.
6. **Đổi nội dung hiển thị khi tạo khóa.** Việc chuẩn hóa để so khớp có thể cần thiết, nhưng nên giữ văn bản gốc nếu người dùng cần xem lại đúng chuỗi đã nhập.
7. **Cho rằng parse thành công đồng nghĩa hợp lệ hoàn toàn.** URI đúng cú pháp chưa chắc có scheme/host hợp lệ cho nghiệp vụ; mã đơn hàng đúng mẫu chưa chắc tồn tại.
8. **Xây API nhận `String` mà không ghi hợp đồng.** Người gọi không biết nên gửi văn bản nguyên dạng, mã đã chuẩn hóa, JSON raw hay đối tượng đã parse.

## 12. Tự kiểm tra

**Câu 1.** API tìm kiếm cho người dùng nhập `"a.b"` và muốn tìm chính xác ba ký tự `a`, `.`, `b`. Có nên gọi `Pattern.compile(userInput)` không?

**Đáp án:** Không. Đây là tìm kiếm literal; có thể dùng `String.contains(userInput)` hoặc `Pattern.compile(Pattern.quote(userInput))` nếu cần regex API.

**Câu 2.** Vì sao `"A,B"` không đủ để xác định nó có hai cột CSV?

**Đáp án:** Một chuỗi văn bản có dấu phẩy có thể chỉ là nội dung thông thường. Đối với CSV, số cột còn phụ thuộc quy tắc quoting, delimiter và cú pháp toàn bản ghi. Chỉ parser CSV mới xác định đúng cấu trúc theo dialect được chọn.

**Câu 3.** `ORD-0042` khớp regex yêu cầu. Điều đó chứng minh gì?

**Đáp án:** Chỉ chứng minh chuỗi thỏa mẫu cú pháp. Nó chưa chứng minh có đơn hàng tương ứng, giá trị là duy nhất hoặc người gọi có quyền truy cập.

**Câu 4.** Nếu một tên người dùng chứa `&`, có thể đặt trực tiếp vào query URL bằng cách nối chuỗi không?

**Đáp án:** Không nên. Khi đặt làm giá trị tham số, phải dùng cơ chế mã hóa đúng thành phần, ví dụ form encoding trong ngữ cảnh tương ứng; dấu `&` phải được bảo toàn như dữ liệu thay vì bị diễn giải làm dấu ngăn tham số.

## Ghi nhớ

**`String` là nơi chứa ký tự; hợp đồng API mới quyết định ký tự đó là nội dung hay cú pháp.** Với plain text, bảo toàn nội dung và xử lý đúng khi xuất. Với structured text, xác định ngữ pháp, parse/validate bằng công cụ chuyên dụng và chuyển sang kiểu dữ liệu phù hợp khi có thể. Khi dữ liệu đi vào SQL, URL, JSON, HTML, regex hay shell, phải giữ ranh giới giữa **dữ liệu** và **cú pháp điều khiển** bằng API đúng ngữ cảnh.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-api-design-007">Dùng bộ xử lý đúng định dạng cho JSON, CSV, đường dẫn hoặc URI</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng bộ xử lý đúng định dạng cho JSON, CSV, đường dẫn hoặc URI

## 1. Một String có thể chứa nhiều ngôn ngữ dữ liệu khác nhau

Trong Java, kiểu `String` chỉ lưu một dãy ký tự. Nó **không tự cho biết** dãy đó là JSON, bản ghi CSV, đường dẫn tệp hay URI. Mỗi định dạng có **ngữ pháp và quy tắc escape riêng**. Nếu lấy thao tác chuỗi phổ thông như `split`, `substring`, `replace` hoặc regex để thay thế bộ phân tích định dạng, kết quả thường chỉ đúng với dữ liệu đơn giản.

Ví dụ bốn chuỗi nhìn có vẻ dễ xử lý:

```text
{"message":"Lan said \"Hello\"","count":2}
"Lan, A.","Dòng đầu
Dòng thứ hai"
reports/2026/../summary.csv
https://example.com/search?q=Java%20%26%20Spring#results
```

- JSON có chuỗi được quote, ký tự escape, kiểu số, `null`, array và object.
- CSV có dấu phân cách nằm **bên trong trường được quote**, dấu nháy kép được escape bằng hai dấu nháy liên tiếp, thậm chí một trường có thể chứa newline.
- Đường dẫn có phân đoạn, quy tắc tương đối/tuyệt đối, hệ thống tệp và các ký hiệu `.`, `..`.
- URI có scheme, authority, path, query, fragment, percent-encoding và ký tự dành riêng.

Một API tốt cần nói rõ **đầu vào thuộc định dạng nào**, dùng bộ xử lý phù hợp để tạo ra kiểu dữ liệu có cấu trúc, rồi mới thực hiện logic nghiệp vụ. Không nên biến mọi thứ thành `String.split(...)` chỉ vì mọi đầu vào đều bắt đầu từ chuỗi.

## 2. Bảng chọn API theo loại dữ liệu

| Dữ liệu | Công cụ phù hợp trong Java | Đầu ra mong đợi | Tránh cách làm |
| --- | --- | --- | --- |
| JSON | Jackson `ObjectMapper`, Gson hoặc thư viện JSON tương thích | Object/record, cây JSON, hoặc luồng token | Tự tách theo dấu phẩy, dấu hai chấm hay dấu ngoặc |
| CSV | Apache Commons CSV, OpenCSV hoặc parser có cấu hình đúng dialect | Bản ghi và các trường, kể cả giá trị rỗng | `split(",")` hoặc `split("\n")` mù quáng |
| Đường dẫn tệp | `java.nio.file.Path`, `Files` | Các phân đoạn đường dẫn, đối tượng truy cập file | Tự nối đường dẫn bằng `"/"` hoặc `"\\"` |
| URI | `java.net.URI` và URI builder phù hợp | Scheme, host, path, query, fragment | Cắt chuỗi dựa trên `"://"`, `"/"`, `"?"`, `"&"` |
| Query tham số form | URL/form encoder hoặc URI builder ở đúng ngữ cảnh | Giá trị query được encode đúng | Dùng `URLEncoder` cho mọi thành phần URI |

**Chọn parser là một phần của hợp đồng API**, không chỉ là vấn đề tiện lợi. Nó giúp xử lý đúng dữ liệu hợp lệ phức tạp, phân biệt input sai, và tránh việc mã hóa hai lần hoặc làm mất thông tin.

## 3. JSON: parser hiểu cấu trúc, kiểu dữ liệu và escaping

### 3.1. Vì sao split theo dấu phẩy hoặc dấu hai chấm sẽ sai?

Ví dụ:

```json
{
  "name": "Lan, A.",
  "address": {"city": "Hà Nội"},
  "tags": ["Java", "Spring"],
  "enabled": true,
  "note": null
}
```

Nếu gọi `split(",")`, dấu phẩy trong `"Lan, A."` sẽ bị coi là dấu phân cách trường, trong khi nó chỉ là **nội dung chuỗi**. Dấu phẩy giữa các phần tử của array cũng không nằm ở cùng cấp với dấu phẩy giữa các thuộc tính của object. Tương tự, `split(":")` không phân biệt dấu hai chấm của thuộc tính với ký tự bên trong giá trị chuỗi.

JSON còn có escape `\"`, `\\`, `\n`, `\uXXXX`; phân biệt `42` (number) với `"42"` (string), `true` (boolean), `null` và trường không tồn tại. Việc tự viết một loạt `replace` hiếm khi xử lý đủ các trường hợp.

### 3.2. Parse bằng Jackson

Với ứng dụng có thư viện Jackson Databind trong classpath, có thể ánh xạ JSON vào một kiểu Java:

```java
import com.fasterxml.jackson.databind.ObjectMapper;

record Product(String sku, int quantity) {}

ObjectMapper mapper = new ObjectMapper();
String json = """
        {"sku":"SKU-01","quantity":3}
        """;

Product product = mapper.readValue(json, Product.class);
System.out.println(product.sku());      // SKU-01
System.out.println(product.quantity()); // 3

String serialized = mapper.writeValueAsString(product);
System.out.println(serialized); // {"sku":"SKU-01","quantity":3}
```

**Đây là đoạn minh họa để đặt trong một lớp/phương thức Java**, không phải tệp độc lập. Cần khai báo dependency `com.fasterxml.jackson.core:jackson-databind` với phiên bản phù hợp do dự án quản lý; Java 17 **không có Jackson sẵn trong JDK**. Record cần phiên bản Jackson có hỗ trợ Java records.

Jackson cũng hỗ trợ `JsonNode` để duyệt JSON linh hoạt, hoặc streaming API cho đầu vào lớn. Khi dữ liệu có schema cố định, chuyển thành record/class giúp logic nghiệp vụ có kiểu rõ ràng; khi cấu trúc động, cây JSON thường phù hợp hơn.

Lưu ý: **deserialize thành công chưa thay thế kiểm tra nghiệp vụ**. Ví dụ `quantity` có thể cần lớn hơn 0; định dạng JSON hợp lệ chưa bảo đảm giá trị hợp lệ. Các tùy chọn Jackson về field thiếu, field lạ và ép kiểu (coercion) phải phù hợp hợp đồng API; không nên dựa vào giá trị mặc định của trường primitive rồi im lặng chấp nhận thiếu trường.

### 3.3. Tạo JSON bằng serializer, không tự ghép dấu nháy

Cách ghép chuỗi thủ công:

```java
String json = "{\"message\":\"" + userText + "\"}";
```

sẽ sai khi `userText` có dấu nháy kép, backslash hoặc các ký tự điều khiển. Nếu nguồn dữ liệu không đáng tin cậy, việc ghép JSON sai còn gây lỗi dữ liệu hoặc làm thay đổi cấu trúc.

Thay vào đó, để serializer chịu trách nhiệm escape:

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

ObjectMapper mapper = new ObjectMapper();
String result = mapper.writeValueAsString(
        Map.of("message", "Lan said \"Hello\"")
);
// result là JSON hợp lệ với dấu nháy trong giá trị được escape.
```

Đây cũng là đoạn minh họa có dependency Jackson. Khi output bắt buộc hợp lệ theo một schema, hãy kết hợp serializer với schema validation hoặc kiểm tra domain trước khi gửi.

## 4. CSV: tách bản ghi và trường theo đúng dialect

### 4.1. Dấu phẩy chưa chắc là dấu phân cách

CSV thường được mô tả ngắn là “các giá trị cách nhau bằng dấu phẩy”, nhưng trường được đặt trong dấu nháy kép có thể **chứa dấu phẩy, newline hoặc dấu nháy**. Ví dụ CSV sau có một dòng tiêu đề và một dòng dữ liệu:

```csv
name,note
"Lan, A.","Said ""hello"""
```

Bản ghi dữ liệu có **hai trường**:

1. `Lan, A.`
2. `Said "hello"`

`split(",")` sẽ tách sai trường đầu. Một ví dụ khác:

```csv
id,note
1,"First line
Second line"
```

Trường `note` chứa newline, nhưng vẫn thuộc **một bản ghi CSV**. Vì vậy, `split("\n")` để đếm số dòng rồi `split(",")` để lấy trường không phải bộ đọc CSV tổng quát.

Các file mang đuôi `.csv` còn có thể dùng dấu chấm phẩy `;`, tab, escape riêng, encoding và quy tắc header khác nhau. Cần xác định **dialect** (delimiter, quote, escape, header, record separator, chính sách ô rỗng) từ nguồn dữ liệu.

### 4.2. Parse bằng Apache Commons CSV

Khi dự án đã thêm dependency `org.apache.commons:commons-csv`, có thể dùng:

```java
import java.io.StringReader;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

String input = """
        name,note
        "Lan, A.","Said ""hello"""
        """;

try (CSVParser parser =
             CSVFormat.RFC4180.parse(new StringReader(input))) {
    for (CSVRecord record : parser) {
        System.out.println(record.get(0) + " | " + record.get(1));
    }
}
```

Đây là **đoạn minh họa** đặt trong một phương thức có thể xử lý `IOException`, và cần thư viện Commons CSV trên classpath. Nó đọc cả dòng tiêu đề như bản ghi đầu tiên; nếu muốn truy cập cột theo **tên header**, cần cấu hình header theo API của phiên bản Commons CSV đang sử dụng.

Cấu hình đúng phụ thuộc nguồn CSV: không phải mọi tập tin đều tuân thủ RFC 4180, và không nên giả định mọi dấu `;` hoặc tab trong dữ liệu đều là sai.

### 4.3. Ghi CSV bằng printer và giữ đủ trường

Khi xuất CSV, hãy dùng chức năng ghi của thư viện để quote và escape trường. Không tự ghép:

```java
String line = name + "," + note; // Có thể làm hỏng CSV.
```

Các bộ ghi CSV chuyên dụng biết khi nào phải thêm dấu nháy và biến dấu `"` bên trong thành `""`. Nếu số cột là cố định, phải kiểm tra số trường sau khi parser xử lý, kể cả trường rỗng ở cuối.

Ví dụ `A,B,` biểu thị **ba trường** trong mô hình CSV có dấu phân cách literal: `A`, `B` và trường cuối rỗng. Dùng `"A,B,".split(",")` trong Java sẽ bỏ trường rỗng cuối; `split(",", -1)` giữ được trường đó với **CSV cực kỳ đơn giản**, nhưng vẫn **không xử lý** các trường quoted, embedded comma hoặc newline. `-1` không biến `split` thành CSV parser.

Khi xuất CSV cho ứng dụng bảng tính, cần xem xét dữ liệu người dùng bắt đầu bằng `=`, `+`, `-` hoặc `@`: một số phần mềm có thể diễn giải chúng như **công thức** khi mở tệp. Việc CSV được quote đúng cú pháp không đồng nghĩa an toàn khi hiển thị trong spreadsheet; cần chính sách xuất dữ liệu theo ứng dụng đích để giảm nguy cơ công thức được thực thi.

## 5. Đường dẫn: dùng Path thay cho nối chuỗi

### 5.1. Đường dẫn là cấu trúc của hệ thống tệp

Trong Java 17, `java.nio.file.Path` biểu diễn đường dẫn tệp theo **file-system provider** đang dùng. Nó hỗ trợ phân đoạn, resolve, normalize, truy xuất tên tệp và thao tác thông qua `Files`:

```java
import java.nio.file.Path;

Path root = Path.of("reports");
Path target = root.resolve(Path.of("2026", "summary.csv"));

System.out.println(target.getFileName()); // summary.csv
```

Tránh tự nối:

```java
String path = rootText + "/" + fileName;
```

vì dấu phân cách và cách biểu diễn ổ đĩa/thư mục gốc phụ thuộc nền tảng; nhiều dấu `/`, `\\`, đường dẫn tuyệt đối hoặc các phân đoạn `..` còn có thể làm ý nghĩa đường dẫn khác dự định.

Cùng một đường dẫn có thể được biểu diễn bằng nhiều cách text khác nhau. `Path.normalize()` loại các thành phần `.` và xử lý các cặp phân đoạn `name/..` **theo cú pháp**, chưa cần truy cập hệ thống tệp.

### 5.2. Dùng resolve và normalize đúng mục đích

```java
Path root = Path.of("documents").toAbsolutePath().normalize();
Path relative = Path.of("2026", "report.csv");
Path target = root.resolve(relative).normalize();

System.out.println(target.getFileName()); // report.csv
System.out.println(target.startsWith(root)); // true
```

Nếu người dùng cung cấp `../secret.txt` và ứng dụng chỉ cho phép đặt tệp dưới `root`, cần kiểm tra:

```java
Path attempt = root.resolve(Path.of("..", "secret.txt")).normalize();
boolean inside = attempt.startsWith(root);
System.out.println(inside); // false
```

`Path.startsWith(Path)` so sánh **các thành phần đường dẫn**, khác với `String.startsWith`. Ví dụ đường dẫn `/data/reports-other` không nằm trong root `/data/reports` dù chuỗi ký tự có chung tiền tố.

**Lưu ý bảo mật quan trọng:** kiểm tra `normalize()`/`startsWith(root)` chỉ là **kiểm tra cấu trúc đường dẫn theo cú pháp**. Nó không ngăn trường hợp symlink bên trong root trỏ đến một vị trí bên ngoài, và không tự khắc phục race condition giữa kiểm tra với lúc mở tệp. Với yêu cầu giới hạn truy cập nghiêm ngặt, cần thiết kế quy trình filesystem phù hợp: kiểm tra đường dẫn thực bằng `toRealPath` khi có tệp, xử lý symlink và quyền truy cập, cân nhắc thao tác gắn với directory handle/secure directory stream tùy hệ thống tệp. Không dùng một phép kiểm tra text đơn lẻ như bằng chứng “đã chống mọi path traversal”.

### 5.3. File Path không đồng nghĩa URI

`Path.of("data", "report.csv")` là đường dẫn tệp tương đối. `URI.create("https://example.com/report.csv")` là URI HTTPS. Chúng có cú pháp và cơ chế truy cập khác nhau.

`Path.of(uri)` được dùng cho URI chỉ hệ thống tệp được provider hỗ trợ, phổ biến là `file:`, **không dùng để chuyển URL HTTP/HTTPS thành đường dẫn cục bộ**. Nếu cần tải tài nguyên HTTP, dùng HTTP client (`java.net.http.HttpClient`) hoặc thư viện phù hợp, rồi lưu kết quả bằng `Files` dưới một `Path` do ứng dụng kiểm soát.

## 6. URI: phân tách thành phần và mã hóa theo ngữ cảnh

### 6.1. URI có cấu trúc riêng

Ví dụ:

```text
https://example.com/search?q=Java%20%26%20Spring#results
└scheme └authority┘ └path┘ └────query─────┘ └fragment
```

Các thành phần có ý nghĩa khác nhau:

| Thành phần | Mục đích |
| --- | --- |
| Scheme | `https`, `http`, `file`, v.v. |
| Authority | Có thể chứa user info, host và port. |
| Path | Các phân đoạn tài nguyên. |
| Query | Phần sau `?` theo cú pháp của dịch vụ. |
| Fragment | Phần sau `#`; không phải phần request path gửi máy chủ theo nghĩa thông thường. |

Java cung cấp `java.net.URI` để parse và xử lý cú pháp URI:

```java
import java.net.URI;

URI uri = URI.create(
        "https://example.com/search?q=Java%20%26%20Spring#results");

System.out.println(uri.getScheme());    // https
System.out.println(uri.getHost());      // example.com
System.out.println(uri.getPath());      // /search
System.out.println(uri.getRawQuery());  // q=Java%20%26%20Spring
System.out.println(uri.getFragment());  // results
```

`getRawQuery()` giữ percent-encoding; `getQuery()` trả dữ liệu query đã được decode theo quy tắc của `URI`. Nhưng `URI` **không tự biến một query thành map nhiều tham số theo chuẩn riêng của ứng dụng**.

### 6.2. Mã hóa query value không giống mã hóa path segment

Trong chuỗi `?q=Java%20%26%20Spring`, `%26` thể hiện ký tự `&` thuộc **giá trị tham số**, không phải dấu phân tách hai tham số. Nếu ghép `?q=` với text tùy ý chưa encode, một dấu `&` trong dữ liệu có thể bị hiểu thành phần cú pháp query.

Với **giá trị query được mã hóa theo application/x-www-form-urlencoded**, có thể dùng:

```java
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

String queryValue = "Java & Spring";
String encoded = URLEncoder.encode(queryValue, StandardCharsets.UTF_8);
URI search = URI.create("https://example.com/search?q=" + encoded);

System.out.println(search.getRawQuery());
// q=Java+%26+Spring
```

Dấu cách được form-encode thành `+`, dấu `&` thành `%26`. Đây là cách mã hóa phù hợp cho **form-style query parameter value**; không dùng `URLEncoder` cho toàn bộ URL hoặc toàn bộ path. Trong URI path, ký tự `+` có thể mang nghĩa literal, còn `/` phân tách path segment. Để tạo URI với nhiều path segments và query params tùy ý, dùng URI builder đáng tin cậy, biết encode **từng thành phần đúng ngữ cảnh**.

Nếu dùng `new URI(scheme, authority, path, query, fragment)`, vẫn phải phân biệt các dấu reserved có ý nghĩa bên trong thành phần: constructor mã hóa ký tự bất hợp lệ nhưng không thể đoán rằng dấu `/` trong một path string là dữ liệu thuộc **một segment** hay dấu phân tách segment. Mã hóa đúng theo **ý nghĩa thành phần** vẫn là trách nhiệm của API gọi.

### 6.3. URI.resolve: xây đường dẫn tương đối, nhưng kiểm tra nguồn

```java
URI base = URI.create("https://example.com/api/");
URI userEndpoint = base.resolve("users/42");

System.out.println(userEndpoint.getPath());
// /api/users/42
```

Dấu `/` cuối ở base ảnh hưởng cách resolve đường dẫn tương đối. Nếu base là `https://example.com/api` (không có slash cuối), chuỗi `users/42` có thể thay phân đoạn `api` thay vì được thêm dưới nó.

**Không dùng `base.resolve(userSuppliedUri)` rồi mặc định đích vẫn nằm trên cùng host**. Chuỗi đầu vào bắt đầu bằng `https://...` hoặc `//other-host/...` có thể dẫn đến URI đích khác. Khi xây dựng URL cho request ra ngoài, phải kiểm tra scheme, host, port, path và các chính sách truy cập sau khi resolve; khi theo redirect còn cần xem lại đích mới. Việc parser tạo thành công `URI` không bảo đảm URL an toàn để gọi mạng.

## 7. Ví dụ Java 17 độc lập, không cần thư viện ngoài

Tạo tệp `StructuredFormatDemo.java`:

```java
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class StructuredFormatDemo {
    public static void main(String[] args) {
        String userInput = "Java & Spring";
        String encoded =
                URLEncoder.encode(userInput, StandardCharsets.UTF_8);
        URI search = URI.create(
                "https://example.com/search?q=" + encoded);

        URI base = URI.create("https://example.com/api/");
        URI endpoint = base.resolve("users/42");

        Path root = Path.of("documents")
                .toAbsolutePath().normalize();
        Path safe = root.resolve(
                Path.of("2026", "report.csv")).normalize();
        Path escape = root.resolve(
                Path.of("..", "secret.txt")).normalize();

        System.out.println("query: " + search.getRawQuery());
        System.out.println("path: " + endpoint.getPath());
        System.out.println("file name: " + safe.getFileName());
        System.out.println("inside root: " + safe.startsWith(root));
        System.out.println("escape rejected: " +
                !escape.startsWith(root));
        System.out.println("uri absolute: " + search.isAbsolute());

        String tricky = "Lan,\"said \"\"hello\"\"\"";
        System.out.println("naive CSV parts: " +
                tricky.split(",").length);
    }
}
```

Biên dịch và chạy:

```bash
javac StructuredFormatDemo.java
java StructuredFormatDemo
```

Kết quả:

```text
query: q=Java+%26+Spring
path: /api/users/42
file name: report.csv
inside root: true
escape rejected: true
uri absolute: true
naive CSV parts: 2
```

Các dòng đầu xác nhận việc mã hóa giá trị query và sử dụng `URI.resolve`, `Path.resolve`/`normalize` đúng ngữ cảnh. Dòng cuối cố tình cho thấy `split(",")` tạo hai đoạn vì nó gặp một dấu phẩy nằm trong chuỗi quoted; với CSV hợp lệ `Lan,"said ""hello"""`, dấu phẩy là delimiter, nhưng các đoạn thô do `split` để lại **chưa được giải mã quote/escape** và không phải các giá trị trường CSV đúng nghĩa. Với một trường quoted chứa dấu phẩy như `"Lan, A.","hello"`, `split(",")` còn tạo **sai số lượng trường**.

Ví dụ `escape rejected: true` chỉ chứng minh thao tác kiểm tra đường dẫn **về mặt cú pháp**; để kiểm tra đường dẫn thật trên hệ thống tệp vẫn cần xét symlink và các vấn đề truy cập nêu ở mục 5.

## 8. Thiết kế API: nhận dữ liệu có ý nghĩa, trả kiểu có cấu trúc

Hạn chế tối đa việc để các tầng nghiệp vụ phải đoán định dạng của một chuỗi chung chung.

Thay vì API mơ hồ như:

```java
void process(String data);
```

hãy thiết kế đường đi rõ ràng, ví dụ:

```text
HTTP body JSON --JSON parser--> OrderRequest --validation--> domain command
CSV upload ----CSV parser----> CSV records ---mapping-----> domain rows
Path input ----Path/Files-----> normalized/checkable file path
URI input -----URI parser-----> URI components -> validated outbound request
```

Tại ranh giới hệ thống, nên tách thành các bước:

1. **Xác định định dạng** từ hợp đồng, Content-Type hoặc cấu hình đáng tin, không đoán bằng `contains(",")` hay `startsWith("{")`.
2. **Parse bằng bộ xử lý đúng** để tôn trọng quoting, escape, nested structures và cú pháp thành phần.
3. **Kiểm tra tính hợp lệ về cấu trúc và nghiệp vụ**: trường bắt buộc, kiểu, số cột, giới hạn độ dài, phạm vi giá trị, path root hoặc destination host.
4. **Sử dụng kiểu dữ liệu có ý nghĩa** trong phần còn lại của chương trình, thay vì đưa raw string đi khắp nơi.
5. **Serialize/encode khi xuất** bằng đúng công cụ cho định dạng đích; tránh tự ghép lại dữ liệu bằng dấu phân cách.

Ở bước parse, lỗi phải được chuyển thành thông báo rõ ràng cho người gọi, ví dụ “JSON sai cú pháp”, “CSV thiếu cột”, “đường dẫn nằm ngoài thư mục cho phép”, “URI có scheme không được chấp nhận”. Không nuốt mọi ngoại lệ rồi trả một chuỗi rỗng, vì chuỗi rỗng đôi khi lại là dữ liệu hợp lệ.

## 9. Những bẫy thiết kế thường gặp

### 9.1. Dùng một regex cho định dạng có cấu trúc lồng nhau

Regex vẫn hữu ích để kiểm tra **mẫu đơn giản**, tìm token hoặc xác nhận một trường văn bản có pattern hẹp. Nhưng JSON có object/array lồng nhau và escape; CSV có quote/newline và trạng thái parser; URI có nhiều thành phần độc lập. Khi vấn đề là **phân tích toàn bộ định dạng**, chọn parser thay vì liên tục bổ sung regex cho từng phản ví dụ.

### 9.2. Nhầm escaping của định dạng này với định dạng khác

- JSON escape dấu `"` và `\` theo luật JSON.
- CSV escape dấu `"` trong quoted field bằng `""` ở dialect thông dụng.
- URI percent-encode theo vị trí và quy tắc từng thành phần.
- File path dùng quy tắc phân đoạn của file system; một số ký tự hợp lệ trên hệ này lại không hợp lệ trên hệ khác.

`String.replace("\"", "\\\"")` chỉ xử lý một trường hợp của JSON, không tạo serializer JSON hoàn chỉnh; `URLEncoder` không tạo đường dẫn file hợp lệ; `Pattern.quote` chỉ phục vụ regex literal, không tạo CSV hay JSON hợp lệ.

### 9.3. Nhầm parse thành công với dữ liệu đáng tin cậy

Một object JSON parse được vẫn có thể có giá trị bất hợp lệ; một CSV có cấu trúc hợp lệ vẫn có thể chứa công thức khi xuất vào bảng tính; một `Path` parse được vẫn có thể trỏ ngoài vùng được phép; một `URI` hợp lệ vẫn có thể trỏ tới đích mạng bị cấm.

Parser trả lời “**cú pháp có hiểu được không?**”. Việc dữ liệu có được phép xử lý hay không là **quyết định kiểm tra tiếp theo** dựa trên hợp đồng nghiệp vụ và chính sách truy cập.

### 9.4. Không kiểm soát kích thước và tài nguyên

JSON/CSV rất lớn có thể tiêu tốn bộ nhớ, thời gian xử lý và tài nguyên I/O. Đặt giới hạn kích thước input, số trường/cột, độ sâu cấu trúc, thời gian xử lý theo nhu cầu; với luồng lớn ưu tiên parser dạng streaming. Khi mở reader, parser hoặc file, dùng `try-with-resources` nếu đối tượng giữ tài nguyên cần đóng.

### 9.5. Dựa vào phần mở rộng tệp hoặc chuỗi URL để suy đoán

Tên tệp `.json` hoặc `.csv` chưa chứng minh nội dung đúng định dạng. Đuôi file chỉ là gợi ý, không thay thế parser và xác thực. Tương tự, kiểm tra `url.startsWith("https://trusted.example")` bằng text có thể đánh đồng host hợp lệ với `trusted.example.evil.test`. Hãy parse URI, so sánh host/scheme theo cấu trúc và áp dụng chính sách kết nối phù hợp.

## 10. Bài tập vận dụng

**Bài 1 — JSON:** Tạo record `Order(String id, int quantity)`; dùng JSON parser để đọc một JSON hợp lệ, sau đó thử giá trị `quantity` âm, thiếu trường và một chuỗi chứa dấu nháy/Unicode. Phân biệt lỗi parse với lỗi nghiệp vụ.

**Bài 2 — CSV:** Dùng CSV parser đọc bản ghi có `"Lan, A."`, trường `"said ""hello"""` và một trường chứa newline. In **đúng số cột** cùng giá trị đã bỏ quoting. Thử file có header và field rỗng cuối.

**Bài 3 — Path:** Nhận tên tệp tương đối do người dùng nhập, resolve từ một root cố định, kiểm tra cấu trúc phân đoạn, sau đó thảo luận cách symlink ảnh hưởng kiểm tra truy cập thực tế. Thử `summary.csv`, `2026/summary.csv`, `../outside.txt` và một absolute path.

**Bài 4 — URI:** Xây URI tìm kiếm từ từ khóa `"Java & Spring"` và xác nhận `&` nằm trong **giá trị** query. Thử resolve một relative path và một absolute URI; xác nhận scheme/host của kết quả trước khi gửi request.

## 11. Ghi nhớ

- **String chỉ là phương tiện chứa ký tự; định dạng mới quyết định cách parse**.
- JSON cần JSON parser/serializer; CSV cần CSV parser/printer được cấu hình đúng dialect.
- File path dùng `Path`, `resolve`, `normalize`, `Files`; kiểm tra root theo cú pháp chưa loại hết rủi ro symlink.
- URI dùng `URI` và URI builder/encoder phù hợp **từng thành phần**; `URLEncoder` dành cho form-style query value, không dành cho toàn URL.
- `split` và `replace` hữu ích cho chuỗi đơn giản nhưng không thay thế công cụ hiểu quoting, escape, nested structure, encoding hay semantics của định dạng.
- Sau bước parse vẫn cần kiểm tra nghiệp vụ, giới hạn tài nguyên và chính sách truy cập; khi xuất dữ liệu hãy dùng serializer/encoder đúng định dạng đích.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-api-design-008">Giữ trách nhiệm mã hóa và escape tại đúng ranh giới</a>

<details>
<summary>Xem chi tiết</summary>

# Giữ trách nhiệm mã hóa và escape tại đúng ranh giới trong thiết kế API Java String

## 1. Vấn đề cốt lõi

Cùng một **String** có thể đi qua nhiều hệ thống: request HTTP, service Java, cơ sở dữ liệu, JSON, HTML, URL, log hay lệnh hệ điều hành. Giá trị nghiệp vụ có thể giống nhau ở mọi nơi, nhưng **cách biểu diễn an toàn và hợp lệ** tại từng ranh giới lại khác nhau.

Ví dụ người dùng nhập:

~~~text
Cà phê & trà "đặc biệt"
~~~

Các thành phần có những quy tắc riêng:

- Trên đường truyền dạng byte, ta cần biết **charset**, ví dụ UTF-8, để biến các ký tự thành byte và giải mã lại.
- Trong nội dung JSON, dấu nháy kép và ký tự điều khiển cần được **JSON serializer** thể hiện theo cú pháp JSON.
- Trong phần văn bản HTML, **&** và **<** cần được **HTML text encoder** xử lý theo quy tắc của HTML.
- Trong giá trị một tham số truy vấn URL, dấu cách, **&**, **+** và các ký tự dành riêng cần được **component encoder** mã hóa theo đúng loại thành phần URL.
- Trong câu lệnh SQL, giá trị dữ liệu nên đi qua **tham số truy vấn**, không nối chuỗi để tự escape SQL.

**Không tồn tại một hàm escape chung an toàn cho mọi đích đến.** Lỗi thường gặp là escape quá sớm trong tầng nghiệp vụ, để một tầng khác escape thêm lần nữa, hoặc dùng phép escape dành cho ngữ cảnh này tại ngữ cảnh khác.

Mục tiêu của bài: xác định **ai chịu trách nhiệm chuyển đổi**, **chuyển đổi tại ranh giới nào**, **giữ dạng dữ liệu gì trong API**, và **làm sao tránh mã hóa hai lần** mà không làm mất nội dung gốc.

## 2. Phân biệt bốn khái niệm thường bị trộn lẫn

| Khái niệm | Câu hỏi cần trả lời | Ví dụ Java và ứng dụng |
| --- | --- | --- |
| **Charset encoding/decoding** | Ký tự được chuyển sang byte và ngược lại bằng charset nào? | **getBytes(StandardCharsets.UTF_8)**, **new String(bytes, UTF_8)** |
| **Escaping theo cú pháp/ngữ cảnh** | Những ký tự nào phải được thể hiện khác khi nhúng vào ngôn ngữ đích? | JSON string escaping, HTML text/attribute escaping, Java string literal |
| **Percent/form encoding của URL** | Dữ liệu chiếm vị trí nào trong URL và cần biểu diễn ký tự ra sao? | **URLEncoder.encode(value, UTF_8)** cho dữ liệu form |
| **Ràng buộc tham số hoặc API có cấu trúc** | Có thể chuyển dữ liệu riêng khỏi phần cú pháp để khỏi ghép chuỗi hay không? | JDBC **PreparedStatement.setString(...)**, serializer JSON, HTML template engine |

Cụ thể:

- **Encoding** có nhiều nghĩa tùy ngữ cảnh. “UTF-8 encoding” chuyển ký tự sang byte; “URL encoding” thường ám chỉ percent-encoding. Hai thao tác này không thể hoán đổi.
- **Escaping** là biểu diễn ký tự theo quy tắc của cú pháp đích. Ví dụ Java source code viết **"A\nB"** để chứa ký tự xuống dòng trong một String, còn JSON serializer phải xuất một JSON string token với dấu nháy và escape phù hợp.
- **Base64** là cách biểu diễn byte bằng bảng chữ cái ASCII, hữu ích cho vận chuyển dữ liệu nhị phân trong một số giao thức. **Base64 không phải cơ chế escape an toàn cho HTML/SQL/URL**, cũng không tự mã hóa bảo mật dữ liệu.
- **Validation** kiểm tra giá trị có hợp lệ theo nghiệp vụ hay không. **Escaping/parameter binding** bảo đảm dữ liệu không trở thành cú pháp của ngôn ngữ đích. Hai trách nhiệm này bổ sung cho nhau, không thay thế nhau.

Ví dụ một tên hiển thị có dấu **<** có thể được cho phép theo nghiệp vụ nhưng vẫn phải được HTML-escape khi chèn vào **HTML text context**. Không cần biến giá trị nghiệp vụ thành chuỗi escape từ lúc nhận vào.

## 3. Nguyên tắc: giữ dữ liệu ngữ nghĩa, chuyển đổi khi đi qua ranh giới

Một thiết kế dễ kiểm soát:

~~~text
HTTP input (byte + quy tắc protocol)
        |
        v
HTTP framework giải mã đúng lần vào String
        |
        v
Controller -> Service -> Domain: String nguyên nghĩa
        |
        +--> JDBC parameter binding -> Database
        |
        +--> JSON serializer -> HTTP application/json
        |
        +--> HTML template engine -> HTML text/attribute
        |
        +--> URL component encoder -> giá trị query/path tương ứng
        |
        +--> Logger với chính sách hiển thị/ghi log riêng
~~~

**“Nguyên nghĩa”** không có nghĩa là bỏ mọi validation hoặc giữ nguyên byte thô. Nó có nghĩa String trong service đại diện cho giá trị của người dùng, ví dụ **Cà phê & trà**, thay vì mã escape phục vụ một output chưa được chọn.

Trách nhiệm thường chia như sau:

1. **Input adapter** biết charset/protocol và giải mã input thành giá trị Java; kiểm tra giới hạn kích thước, hình thức dữ liệu và hợp đồng API.
2. **Service/domain** xử lý ý nghĩa của giá trị. Đừng gắn HTML entities hoặc percent-encoded query component vào thuộc tính domain chỉ để “an toàn chung”.
3. **Output adapter/serializer** biết ngữ cảnh đích và thực hiện escape/encode tương ứng tại thời điểm xuất.
4. **Database adapter** dùng tham số truy vấn, không dựa vào dữ liệu đã được escape thủ công.
5. **Giao diện API và tài liệu hợp đồng** phải nói rõ khi nào tham số là giá trị thô, khi nào là một chuỗi đã mã hóa theo chuẩn xác định.

Đây là **quy tắc thiết kế mặc định**, không phải cấm tuyệt đối mọi kiểu dữ liệu đã mã hóa. Một hệ thống có thể cố ý lưu **HTML sanitized** hoặc nội dung đã ở dạng **JSON document**; khi đó cần mô hình/kiểu API riêng, chính sách rõ ràng và không nhầm nó với văn bản thông thường.

## 4. Java String khác byte: charset là trách nhiệm của ranh giới I/O

Một **String** Java biểu diễn nội dung ký tự bằng UTF-16 theo API của Java; không nên gọi nó là “String được UTF-8 hóa”. UTF-8 là cách biểu diễn khi chuyển nội dung ký tự sang byte.

~~~java
import java.nio.charset.StandardCharsets;

String value = "Cà phê";
byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
String restored = new String(bytes, StandardCharsets.UTF_8);

System.out.println(restored.equals(value)); // true
~~~

Đây là một vòng đi và về **giữa String và byte** qua UTF-8. Nếu lúc mã hóa dùng UTF-8 nhưng lúc giải mã dùng charset không tương ứng, các ký tự có dấu có thể bị lỗi (mojibake).

**Quy tắc thực hành:**

- Khi API nhận hoặc trả **byte[]**, chỉ rõ charset tại ranh giới chuyển từ/đến String.
- Khi xử lý file text, dùng API có đối số charset, như **Files.readString(path, StandardCharsets.UTF_8)** và **Files.writeString(path, text, StandardCharsets.UTF_8)** khi hợp đồng yêu cầu UTF-8.
- Với request/response HTTP, cấu hình và tuân theo Content-Type/charset cùng quy tắc framework; tránh giải mã một giá trị đã được framework giải mã.
- Không gọi **new String(bytes)** hoặc **getBytes()** không đối số nếu tính đúng đắn của giao tiếp yêu cầu charset cụ thể.
- Không giả định độ dài **String.length()** bằng số byte UTF-8 hoặc số code point Unicode. Java String tính theo đơn vị UTF-16.

**Tách biệt:** chuyển sang UTF-8 **không** làm cho nội dung phù hợp để chèn thẳng vào HTML, JSON string, SQL hay URL. Đây là các ranh giới tiếp theo với ngữ pháp riêng.

## 5. Mã hóa URL: chỉ mã hóa thành phần dữ liệu đúng vị trí

URL có nhiều thành phần: scheme, host, path, query, fragment. Cùng một ký tự có thể có ý nghĩa khác nhau trong từng thành phần. Ví dụ dấu **/** thường phân tách path segments; dấu **&** phân tách các cặp query parameters trong một số quy ước; dấu **+** có ý nghĩa đặc biệt khi xử lý **application/x-www-form-urlencoded**.

Trong Java:

~~~java
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

String keyword = "cà phê & trà";
String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8);

String url = "https://example.org/search?q=" + encoded;
~~~

**URLEncoder** được thiết kế cho quy tắc mã hóa **application/x-www-form-urlencoded**, thường dùng với **giá trị form/query parameter**. Nó thể hiện khoảng trắng dưới dạng **+**. Vì vậy, **không dùng URLEncoder để mã hóa toàn bộ URL**, URL path hoặc cả chuỗi query đã ghép sẵn.

Ví dụ trên, chỉ **keyword** là giá trị dữ liệu cần mã hóa. Các ký tự cấu trúc của URL, như **?**, **=**, dấu **&** giữa nhiều tham số, phải được tạo ra bởi bộ xây dựng URL hoặc code có hợp đồng rõ.

### 5.1. Giá trị query khác cả đường dẫn

~~~text
Đúng ý định:
  https://example.org/search?q=<giá trị q đã form-encode>

Sai ý định:
  form-encode toàn bộ "https://example.org/search?q=..." rồi coi đó là URL
~~~

Đối với **path segment**, không đánh đồng dấu cách cần percent-encoding với dấu **+** theo form encoding. Nên dùng URI/URL builder của framework hoặc thư viện HTTP với phương thức **encode path segment** rõ ràng. Một hàm cho “mọi URL” dễ làm thay đổi dấu phân cách và semantics.

Lưu ý: percent-encoding thể hiện dữ liệu cho URL, **không tự ngăn path traversal** hoặc cấp quyền truy cập. Đường dẫn tệp/tài nguyên vẫn phải được xác thực, chuẩn hóa và kiểm tra phạm vi được phép ở nơi sử dụng.

### 5.2. Mã hóa hai lần và giải mã hai lần

Một lỗi thường gặp:

~~~java
String raw = "A&B";
String once = URLEncoder.encode(raw, StandardCharsets.UTF_8);
String twice = URLEncoder.encode(once, StandardCharsets.UTF_8);

System.out.println(once);  // A%26B
System.out.println(twice); // A%2526B
~~~

Trong **twice**, dấu **%** của dữ liệu đã percent-encode trước đó bị mã hóa thành **%25**. Nếu bên nhận giải mã một lần, giá trị còn **A%26B** thay vì **A&B**.

Giải pháp thiết kế:

- Phương thức nhận **rawQueryValue** và **chính nó** chịu trách nhiệm encode đúng một lần.
- Hoặc phương thức nhận **encodedQueryComponent** với hợp đồng rõ và **không encode lại**. Tránh để cùng một tham số String mơ hồ có thể thuộc cả hai trạng thái.
- Không tự động decode mọi chuỗi có **%**. Chuỗi **"50% off"** có dấu phần trăm hợp lệ như dữ liệu thông thường.
- Không decode lặp để “đạt dạng dễ đọc” vì có thể thay đổi ý nghĩa, phá validation và gây lỗi tại các tầng khác.

## 6. JSON: giao cho serializer thay vì nối chuỗi thủ công

Một JSON string là token có cú pháp riêng. Dữ liệu chứa dấu nháy kép, backslash, newline hoặc ký tự điều khiển phải được serializer thể hiện hợp lệ.

**Không nên ghép thủ công:**

~~~java
// KHÔNG AN TOÀN/CHÍNH XÁC khi userName chứa quote hoặc xuống dòng:
String json = "{\"name\":\"" + userName + "\"}";
~~~

Giả sử **userName** là **A" B**, việc nối trực tiếp có thể phá JSON string. Cách giải quyết không phải viết một danh sách thay thế ký tự đơn giản rồi dùng cho mọi trường hợp; hãy dùng serializer JSON của môi trường ứng dụng, ví dụ Jackson trong dự án có dependency tương ứng.

Ví dụ **đoạn minh họa cần thư viện Jackson** (không phải chương trình Java SE chạy độc lập):

~~~java
import com.fasterxml.jackson.databind.ObjectMapper;

record Profile(String displayName) {}

ObjectMapper mapper = new ObjectMapper();
Profile value = new Profile("A\"B\nC");
String json = mapper.writeValueAsString(value);

// Serializer chịu trách nhiệm dấu nháy, backslash và newline trong JSON.
~~~

Khi đọc JSON, **JSON parser** biến JSON string token trở về giá trị Java tương ứng. Service nên làm việc với **displayName** dạng String theo ngữ nghĩa, không cần tự thêm backslash escape để lưu dữ liệu thông thường.

**Phân biệt source literal với dữ liệu runtime:**

- Trong **Java source code**, **"A\nB"** là cú pháp diễn tả một String có ký tự LF.
- Trong **JSON text**, dấu xuống dòng trong một chuỗi JSON phải được thể hiện theo cú pháp JSON, ví dụ chuỗi ký tự **\n** trong JSON wire text.
- Đừng tự lấy chuỗi source của Java và cho rằng nó đã trở thành JSON hợp lệ.

Khi dữ liệu JSON được nhúng vào **HTML**, ranh giới HTML/script có thêm quy tắc riêng. Một JSON serializer đơn thuần không phải bảo đảm toàn bộ việc nhúng nội dung vào **<script>** an toàn. Thiết kế tốt là trả JSON qua response **application/json** và để frontend parse; nếu thật sự cần nhúng vào HTML, phải dùng giải pháp escape đúng ngữ cảnh nhúng đó.

## 7. HTML: escape theo context, không có một “HTML escape” dùng mọi nơi

HTML có nhiều context: **text node**, **attribute**, **URL attribute**, JavaScript trong script, CSS trong style. Các quy tắc escape và rủi ro khác nhau.

Ví dụ dữ liệu nghiệp vụ:

~~~text
A&B <Admin>
~~~

Nếu đưa vào **HTML text node**, các ký tự mang ý nghĩa cú pháp như **&**, **<** cần được thể hiện an toàn để người dùng nhìn thấy đúng dữ liệu mà nội dung không trở thành thẻ HTML.

Nguyên tắc:

1. Khi tạo HTML, dùng **template engine có auto-escaping phù hợp với vị trí chèn** hoặc API DOM với thao tác **textContent** khi chèn văn bản.
2. Với **HTML attribute**, dùng cơ chế escape dành riêng cho attribute; không lấy hàm chỉ dành cho HTML text rồi mặc định dùng an toàn trong mọi trường hợp.
3. Với **href/src**, ngoài escape HTML attribute, còn cần **kiểm soát URL scheme và nguồn đích**. Escape dấu nháy không vô hiệu hóa một URL có scheme nguy hiểm.
4. Với **JavaScript/CSS context**, tránh nhúng chuỗi động trực tiếp; sử dụng cách truyền dữ liệu riêng hoặc encoder dành cho context chính xác.
5. Nếu mục tiêu là **cho phép nội dung HTML có giới hạn**, cần quy trình **sanitization** theo allowlist và policy rõ ràng. Escaping toàn bộ thì hiển thị markup như văn bản; sanitization có mục tiêu khác.

**Phòng XSS bằng output encoding là trách nhiệm của nơi render.** Một service Java không nên biến mọi tên người dùng thành chuỗi chứa HTML entities rồi dùng chuỗi ấy cho email, JSON, SQL và log.

### 7.1. Double escaping trong HTML

Nếu tầng service đã thay **&** bằng **&amp;**, rồi template engine escape tiếp dấu **&**, người dùng có thể nhìn thấy chữ **&amp;** thay vì dấu **&**.

Đó không phải lỗi “thiếu unescape ở frontend”; lỗi thiết kế là **hai tầng cùng cho rằng mình sở hữu trách nhiệm escape**.

Nên giữ mô hình:

~~~text
Domain String:       "A&B"
Template nhận:       "A&B"
HTML renderer xuất:  "A&amp;B"
Trình duyệt hiển thị: A&B
~~~

Nếu phải lưu rich HTML an toàn, hãy mô hình hóa nó khác với String văn bản thường, và khai báo rõ renderer có được xuất như markup hay phải escape như text.

## 8. SQL: dùng parameter binding, không tự escape giá trị

Các toán tử và cú pháp SQL cần được giữ tách biệt khỏi dữ liệu do người dùng nhập. **Không** xây query bằng nối chuỗi:

~~~java
// Ví dụ không nên dùng:
String sql = "SELECT id FROM users WHERE email = '" + email + "'";
~~~

Đây là rủi ro SQL injection và lỗi xử lý dấu nháy. Cách đúng là dùng tham số JDBC:

~~~java
// Đoạn minh họa: giả định có java.sql.Connection connection.
String sql = "SELECT id FROM users WHERE email = ?";

try (java.sql.PreparedStatement ps = connection.prepareStatement(sql)) {
    ps.setString(1, email);
    try (java.sql.ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
            long id = rs.getLong("id");
            // Xử lý bản ghi theo nghiệp vụ.
        }
    }
}
~~~

**PreparedStatement.setString(...)** truyền giá trị như dữ liệu, không để dấu nháy trong **email** trở thành cú pháp SQL. Nó cũng giúp không phải viết cách escape thủ công phụ thuộc database.

Cần nhớ:

- Tham số có thể thay **giá trị dữ liệu**, không thay tên bảng, cột hoặc từ khóa SQL. Nếu tên cột cần chọn động, dùng **allowlist identifier** và dựng cú pháp từ các lựa chọn tin cậy.
- Điều kiện **LIKE** còn có ký tự pattern của SQL như **%** và **_**. Parameter binding ngăn injection qua cú pháp SQL nhưng **không tự biến chúng thành ký tự literal trong LIKE pattern**; nếu muốn tìm dấu **%** thật, cần quy tắc escape LIKE và cú pháp **ESCAPE** thích hợp với database.
- Dữ liệu trước khi gửi database vẫn có thể chứa **<**, **&** hoặc dấu nháy. Đừng HTML-escape để “chống SQL injection”; đây là hai ranh giới khác nhau.

Một cột chứa tên người dùng nên lưu **tên người dùng theo ngữ nghĩa**. Khi ứng dụng đọc nó và đưa vào trang HTML, lúc đó renderer HTML chịu trách nhiệm HTML-escape.

## 9. Regex, shell, CSV và log có ranh giới riêng

Khi nhập **từ khóa để tìm nguyên văn**, \`Pattern.compile(input)\` có thể biến dấu \`.\` hoặc \`*\` thành toán tử regex. Nếu vẫn dùng regex API, quote đúng phần dữ liệu:

~~~java
String literalText = "a.b";
java.util.regex.Pattern pattern =
        java.util.regex.Pattern.compile(
                java.util.regex.Pattern.quote(literalText));
System.out.println(pattern.matcher("x a.b y").find()); // true
System.out.println(pattern.matcher("x acb y").find()); // false
~~~

Khi gọi tiến trình, truyền executable và các đối số riêng qua \`ProcessBuilder\`, kiểm soát argument được công cụ đích hiểu là option; tránh đưa input vào shell script ghép chuỗi. Khi ghi CSV, dùng CSV writer với quoting/escape đúng dialect, và xem xét **formula injection** nếu tệp sẽ mở trong spreadsheet. Khi ghi log, để logging sink quyết định escape cho cấu trúc log và kiểm soát CR/LF, ký tự điều khiển, dữ liệu nhạy cảm; parameterized log message không mặc nhiên loại mọi ký tự gây log injection.

Điểm chung: \`Pattern.quote\` chỉ phục vụ regex, CSV writer chỉ phục vụ CSV, logging formatter chỉ phục vụ log. Những phép chuyển đổi đó **không** phải giá trị để ghi ngược vào domain \`String\` và tái sử dụng ở một đích khác. Phần giải thích cú pháp chi tiết và ví dụ parser/serializer nằm tại hai bài về text có ngữ pháp và xử lý định dạng.

## 10. Ví dụ Java 17+ độc lập: đúng ranh giới, tránh mã hóa hai lần

Tạo tệp **EncodingBoundaryDemo.java**:

~~~java
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

public class EncodingBoundaryDemo {
    public static void main(String[] args) {
        String raw = "cà phê & trà";

        byte[] utf8 = raw.getBytes(StandardCharsets.UTF_8);
        String restored = new String(utf8, StandardCharsets.UTF_8);
        System.out.println("UTF-8 round trip: " + restored.equals(raw));

        String formValue = URLEncoder.encode(raw, StandardCharsets.UTF_8);
        System.out.println("Form value: " + formValue);
        System.out.println("Form round trip: "
                + URLDecoder.decode(formValue, StandardCharsets.UTF_8).equals(raw));

        String twice = URLEncoder.encode(formValue, StandardCharsets.UTF_8);
        System.out.println("Double encoding contains %25: "
                + twice.contains("%25"));

        String base64 = Base64.getEncoder().encodeToString(utf8);
        byte[] decodedBytes = Base64.getDecoder().decode(base64);
        System.out.println("Base64 round trip: "
                + new String(decodedBytes, StandardCharsets.UTF_8).equals(raw));

        String keyword = "a.b";
        Pattern literal = Pattern.compile(Pattern.quote(keyword));
        System.out.println("Regex literal matches dot: "
                + literal.matcher("x a.b y").find());
        System.out.println("Regex literal rejects wildcard match: "
                + !literal.matcher("x acb y").find());
    }
}
~~~

Biên dịch, chạy:

~~~bash
javac EncodingBoundaryDemo.java
java EncodingBoundaryDemo
~~~

Kết quả:

~~~text
UTF-8 round trip: true
Form value: c%C3%A0+ph%C3%AA+%26+tr%C3%A0
Form round trip: true
Double encoding contains %25: true
Base64 round trip: true
Regex literal matches dot: true
Regex literal rejects wildcard match: true
~~~

Giải thích:

1. **UTF-8** xử lý String thành byte và ngược lại. Nó không tự escape cho HTML/SQL.
2. **URLEncoder** đổi giá trị của một form/query parameter; dấu cách trở thành **+**, dấu **&** trong giá trị thành **%26**.
3. **URLDecoder** nhận đầu vào đúng loại **form-encoded value**, giải mã trở về String theo ngữ nghĩa.
4. Encode lại **formValue** làm percent sign trong các chuỗi **%XX** thành **%25**: đây là dấu hiệu của tình huống mã hóa hai lần, không phải lớp bảo vệ bổ sung.
5. **Base64** xử lý byte thành văn bản ASCII và giải mã lại. Nó không phải phép escape HTML/SQL.
6. **Pattern.quote** bảo toàn ý nghĩa literal của dấu chấm trong regex.

**Chỉ đưa String đã encode vào đúng vị trí và đúng giao thức mà nó được tạo ra cho.** Không gán **formValue** vào domain field tên **displayName** và sau đó đem field đó lưu database hoặc trả JSON như tên người dùng.

## 11. Một thiết kế API dễ mắc lỗi và cách sửa

### 11.1. Thiết kế dễ double escaping

~~~java
// Tình huống xấu mang tính minh họa:
String saveDisplayName(String submittedName) {
    return submittedName.replace("&", "&amp;");
}
~~~

Nếu kết quả được lưu như tên hiển thị, trang HTML có thể escape lần nữa; JSON API có thể trả tên đã biến dạng thành **&amp;**; tìm kiếm tên người dùng cũng không còn so sánh trên dữ liệu nguyên nghĩa.

### 11.2. Thiết kế rõ trách nhiệm hơn

Giữ giá trị gốc trong API nghiệp vụ:

~~~java
record UserProfile(String displayName) {}

UserProfile profile = new UserProfile("A&B");
~~~

Khi xuất dữ liệu:

- HTTP JSON response: **JSON serializer** đọc **profile.displayName()** và tạo JSON text hợp lệ.
- HTML view: **template engine** đặt giá trị vào text/attribute context với output encoding phù hợp.
- Query đến dịch vụ khác: **HTTP client/URI builder** mã hóa giá trị trong thành phần URL chính xác.
- JDBC: **PreparedStatement.setString(...)** truyền chuỗi dạng dữ liệu.
- Logging: **logger** serialize/format theo sink, có quy tắc chống log injection và lộ thông tin riêng.

**Không phải tạo một biến tên escapedDisplayName dùng chung cho tất cả đích xuất.**

Nếu API thật sự nhận hoặc trả văn bản đã encode, hãy diễn tả điều đó trong tên và hợp đồng, ví dụ **rawQueryValue** khác **encodedFormComponent**, kèm quy định charset/dialect. Với dự án lớn có thể dùng các value object/wrapper type riêng để tránh trộn trạng thái.

## 12. Không giải mã quá mức ở input

Nguyên tắc “escape ở output” không đồng nghĩa “decode mọi thứ nhiều lần khi input”.

Hãy phân biệt:

- HTTP framework thường đã decode query parameter theo protocol. Controller nhận **String parameter** và không cần **URLDecoder.decode(...)** lần nữa.
- JSON parser đã giải mã escape trong JSON string. Service nhận giá trị String đã parse và không cần tự **unescape JSON**.
- HTML entities trong một chuỗi người dùng có thể là **văn bản họ muốn nhập**. Không tự chuyển **&amp;** thành **&** chỉ vì nhìn giống HTML entity.
- Nếu một endpoint **cố ý** nhận toàn bộ form-encoded payload ở dạng raw, adapter xử lý việc parse/decode **đúng một lần** theo Content-Type và charset đã định nghĩa.

Giải mã nhiều lần có thể phá validation hoặc tạo ra ký tự phân cách mới chỉ sau vòng decode thứ hai. Vì vậy, **hợp đồng API phải xác định trạng thái dữ liệu tại mỗi bước** và không dùng “thử decode đến khi hết ký tự đặc biệt”.

Một ví dụ kiểm soát:

~~~text
Raw HTTP request: q=A%26B
Framework parse query: parameter q = "A&B"
Service nhận: "A&B"
Service KHÔNG tự URLDecode(q) nữa
~~~

Nếu dữ liệu thực sự chứa dấu phần trăm, nó vẫn là dữ liệu hợp lệ. Việc kiểm tra hợp lệ phải dựa trên giá trị theo hợp đồng của API, không dựa trên phỏng đoán rằng mọi ký hiệu percent luôn là escape.

## 13. Validation, canonicalization và bảo vệ ranh giới

Mã hóa/escape đúng context **không thể thay thế** các kiểm tra khác:

- **Giới hạn độ dài và kiểu dữ liệu:** kiểm tra trước khi xử lý để tránh input không hợp lệ và sử dụng tài nguyên quá mức.
- **Unicode normalization:** đôi khi cần NFC/NFKC để so sánh danh tính/tìm kiếm nhất quán, nhưng phải có chính sách nghiệp vụ; normalization không phải một loại HTML escape.
- **Authorization:** URL encoding không làm một đường dẫn hoặc ID trở thành tài nguyên người dùng có quyền truy cập.
- **Path validation:** chuẩn hóa và kiểm tra đường dẫn trong thư mục gốc, xem xét symbolic link/race condition; percent-encoding không thay thế các kiểm tra này.
- **HTML sanitization:** cần khi cho phép một phần markup từ bên ngoài; HTML escaping toàn bộ sẽ hiển thị markup thành văn bản.
- **SQL parameterization:** dùng cho giá trị; tên bảng/cột động cần allowlist.
- **Content-Type:** đặt đúng media type để bên nhận biết bộ parser và quy tắc encode/decode.

Trình tự kiểm soát có thể là: **parse đúng giao thức → xây dữ liệu có kiểu → validate ý nghĩa và quyền truy cập → xử lý nghiệp vụ → serialize/encode đúng output context**. Đừng gộp các thao tác này thành một hàm mang tên chung **sanitizeString** rồi gọi ở mọi tầng.

## 14. Thiết kế test theo ranh giới

Để kiểm tra hệ thống thực sự phân công trách nhiệm đúng, hãy thử những String chứa các ký tự “khó”:

~~~text
A&B
A<B
A"B
A'B
a+b
a%2Bb
Dòng 1
Dòng 2
cà phê
中文
😀
~~~

Những test có giá trị:

1. **UTF-8 round-trip:** một String Unicode bất kỳ chuyển sang byte rồi về String vẫn bằng đầu vào.
2. **Form encoding round-trip:** giá trị chứa **&**, **+**, dấu cách, dấu **%** được encode/decode **đúng một lần** tại đúng parser.
3. **JSON round-trip:** serialize rồi parse lại cho ra cùng giá trị String, kể cả dấu nháy kép, backslash và newline.
4. **HTML rendering:** giá trị chứa **<** được hiển thị như text, không tạo phần tử HTML ngoài ý định; attribute và URL được kiểm tra theo context riêng.
5. **SQL parameter binding:** giá trị chứa dấu nháy đơn vẫn được lưu/tìm như dữ liệu và không thay đổi cấu trúc câu SQL.
6. **No-double-escape:** renderer nhận **A&B** từ service và hiển thị **A&B**; không hiển thị **&amp;**.
7. **Input framework:** endpoint không decode lần thứ hai một tham số mà framework đã parse.
8. **Cross-layer contract:** giá trị đọc từ database, trả qua JSON và render HTML vẫn giữ cùng **ý nghĩa**, dù byte representation và escape representation khác nhau.

Lưu ý: so sánh nội dung trên wire phải đối chiếu **đúng lớp**. Chuỗi JSON có thể chứa backslash escaping mà giá trị String sau parse không chứa backslash ấy. HTML source có thể chứa entities mà text hiển thị không chứa các chuỗi entity.

## 15. Lỗi thường gặp và cách khắc phục

| Lỗi | Hệ quả | Hướng xử lý |
| --- | --- | --- |
| Lưu tên người dùng đã HTML-escape vào domain/database | Double escaping hoặc tên bị biến dạng ở JSON/email | Lưu giá trị String theo ngữ nghĩa; escape tại HTML view |
| Dùng **URLEncoder** cho cả URL | Các dấu phân cách cấu trúc bị mã hóa sai | Encode riêng query/form value hoặc path segment bằng builder phù hợp |
| Encode một query value hai lần | **%** đổi thành **%25**, kết quả hiển thị sai | Quy định rõ raw value và encoded component |
| Tự **URLDecoder** trên parameter đã được framework parse | Double decode, thay đổi ý nghĩa/validation | Để input adapter decode đúng một lần |
| Ghép dấu nháy vào JSON bằng nối String | JSON không hợp lệ hoặc dữ liệu sai | Dùng serializer JSON |
| Dùng HTML escape để chống SQL injection | Không xử lý rủi ro SQL | JDBC PreparedStatement/parameterized SQL |
| Tự replace dấu nháy đơn trong SQL | Dễ sai theo dialect/cấu hình | Bind tham số, allowlist identifier |
| Dùng JSON escape cho HTML text/attribute | Không bảo đảm an toàn ngữ cảnh HTML | HTML encoder/template engine đúng context |
| Coi Base64 là mã hóa bảo mật | Không tạo tính bí mật hay xác thực | Dùng cơ chế bảo mật riêng nếu cần |
| Coi Java **String.length()** là độ dài byte UTF-8 | Sai giới hạn kích thước truyền dẫn | Đo trên byte được encode đúng charset |
| Dùng **Pattern.compile(userInput)** khi cần tìm literal | Ký tự regex biến thành toán tử | Dùng **Pattern.quote(userInput)** |
| Dùng **escape()** chung cho mọi tầng | Không rõ context, dễ double-escape | API có tên/hợp đồng theo từng ranh giới |

## 16. Checklist áp dụng trong code review

- [ ] Dữ liệu ở tầng domain/service còn mang **giá trị theo ngữ nghĩa** hay đã bị mã hóa cho một output cụ thể?
- [ ] API input ghi rõ dữ liệu là raw String hay đã ở một encoding/format cụ thể?
- [ ] Charset tại ranh giới byte/String có được chỉ rõ và khớp hai đầu không?
- [ ] JSON và HTML có serializer/renderer chịu trách nhiệm phù hợp với **context** không?
- [ ] URL chỉ encode **component dữ liệu** cần thiết, không encode toàn chuỗi hoặc mã hóa trùng?
- [ ] SQL có dùng parameter binding? Identifier động có được giới hạn bằng allowlist?
- [ ] Các giá trị đã parse bởi framework có bị decode lặp lại không?
- [ ] Có test round-trip và test nội dung chứa **&**, **+**, **%**, dấu nháy, newline, Unicode không?
- [ ] Có kiểm tra validation, phân quyền và cấu trúc dữ liệu độc lập với output encoding không?
- [ ] Nếu buộc phải giữ chuỗi encoded, kiểu/tên biến/hợp đồng có làm trạng thái ấy rõ ràng không?

## 17. Ghi nhớ

**Một String không tự “an toàn” cho mọi đích.** Giá trị ở tầng nghiệp vụ nên giữ nguyên ý nghĩa; khi chuyển sang byte, HTML, JSON, URL, SQL, regex hoặc log, dùng **bộ chuyển đổi dành riêng cho đích đến** tại ranh giới thực sự:

- **Byte/String:** charset đã định, ví dụ UTF-8.
- **JSON:** serializer và parser JSON.
- **HTML:** encoder/template đúng text, attribute, URL hoặc context tương ứng.
- **URL:** encoder cho đúng query value/path segment; decode theo protocol một lần.
- **SQL:** parameter binding và kiểm soát identifier.
- **Regex:** **Pattern.quote** nếu dữ liệu là literal.
- **Log/CSV/shell:** API cấu trúc hoặc serializer phù hợp với format/command đích.

Cách làm này vừa giữ đúng dữ liệu vừa hạn chế **double encoding**, **double escaping**, lỗi cú pháp và rủi ro injection do trách nhiệm bị đặt sai chỗ.

### Tài liệu tham khảo

- Java SE 17, **java.lang.String** và **java.nio.charset.StandardCharsets**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html
- Java SE 17, **java.net.URLEncoder**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLEncoder.html
- Java SE 17, **java.net.URLDecoder**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLDecoder.html
- Java SE 17, **java.sql.PreparedStatement**: https://docs.oracle.com/en/java/javase/17/docs/api/java.sql/java/sql/PreparedStatement.html
- Java SE 17, **java.util.regex.Pattern**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html
- OWASP, **Cross Site Scripting Prevention Cheat Sheet**: https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html
- OWASP, **SQL Injection Prevention Cheat Sheet**: https://cheatsheetseries.owasp.org/cheatsheets/SQL_Injection_Prevention_Cheat_Sheet.html


</details>

- [Quay lại đầu trang](#back-to-top)
