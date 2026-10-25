<a id="back-to-top"></a>

# Thực hành tổng hợp

## Menu
- [Xây dựng bộ làm sạch đầu vào theo chính sách tường minh](#string-practice-001)
- [Tìm và trích xuất trường từ chuỗi có cấu trúc đơn giản](#string-practice-002)
- [Ghép báo cáo văn bản bằng StringBuilder hoặc StringJoiner](#string-practice-003)
- [Tách dữ liệu có phần tử rỗng và giữ đúng dấu phân cách](#string-practice-004)
- [Đếm ký tự theo char, code point và cụm hiển thị](#string-practice-005)
- [So sánh tên tiếng Việt sau chuẩn hóa phù hợp](#string-practice-006)
- [Tái hiện bẫy null, overload, intern và phép nối với số](#string-practice-007)
- [Đối chiếu hiệu năng các cách ghép chuỗi trên cùng dữ liệu](#string-practice-008)
- [Lập bảng lựa chọn API và tự đánh giá các tình huống xử lý chuỗi](#string-practice-009)

## <a id="string-practice-001">Xây dựng bộ làm sạch đầu vào theo chính sách tường minh</a>

<details>
<summary>Xem chi tiết</summary>

# Xây dựng bộ làm sạch đầu vào theo chính sách tường minh trong Java

Khi nhận dữ liệu từ biểu mẫu, API, tệp CSV hoặc nguồn bên ngoài, lập trình viên thường viết một chuỗi thao tác như `trim()`, thay nhiều khoảng trắng bằng một, đổi thành chữ thường hoặc xóa ký tự lạ. Cách làm này dễ tạo lỗi khi mỗi trường dữ liệu có **ý nghĩa và yêu cầu khác nhau**: tên đăng nhập, tên hiển thị, mật khẩu, địa chỉ email, nội dung bài viết và đường dẫn tệp không thể dùng chung một quy tắc.

Bộ làm sạch đầu vào (*input cleaner*) đáng tin cậy phải nêu rõ **dữ liệu nào được chấp nhận, biến đổi nào được phép, trường hợp nào phải từ chối và thứ tự áp dụng**. Bài thực hành xây dựng bộ làm sạch Java 17+ với chính sách bất biến cho hai loại đầu vào:

- **Tên đăng nhập (username):** chuẩn hóa Unicode theo NFKC, bỏ khoảng trắng ở hai đầu, đổi chữ thường độc lập ngôn ngữ, chỉ chấp nhận chữ ASCII, số và dấu gạch dưới theo cấu trúc xác định.
- **Tên hiển thị (display name):** chuẩn hóa NFC, gom nhiều khoảng trắng thành một, cho phép chữ cái và dấu kết hợp Unicode, giữ nguyên chữ hoa/thường; không tự ý loại bỏ ký tự sai mà báo lỗi.

Đây là **ví dụ chính sách cho một ứng dụng cụ thể**, không phải bộ lọc được phép áp dụng nguyên xi cho mọi ngôn ngữ, tên người hoặc hệ thống.

## 1. Phân biệt làm sạch, chuẩn hóa, xác thực và mã hóa đầu ra

| Khái niệm | Mục đích | Ví dụ |
| --- | --- | --- |
| **Chuẩn hóa (normalization)** | Đưa những biểu diễn được ứng dụng coi là tương đương về một dạng thống nhất. | Chuyển chuỗi có dấu kết hợp sang dạng NFC. |
| **Làm sạch (cleaning/canonicalization)** | Áp dụng các phép biến đổi được chính sách công bố rõ ràng. | Thu gọn khoảng trắng trong tên hiển thị thành một dấu cách. |
| **Xác thực (validation)** | Kiểm tra dữ liệu sau khi biến đổi có phù hợp hợp đồng của trường không. | Tên đăng nhập phải khớp `[a-z][a-z0-9_]{2,15}`. |
| **Mã hóa/escape theo ngữ cảnh đầu ra** | Bảo vệ dữ liệu khi đưa vào HTML, JSON, URL, câu lệnh SQL, shell và các ngữ cảnh khác. | Escape văn bản khi hiển thị HTML; dùng tham số cho câu lệnh SQL. |

**Làm sạch đầu vào không thay thế cơ chế bảo vệ ở nơi sử dụng dữ liệu.** Ví dụ, một chuỗi đã khớp quy tắc tên hiển thị vẫn phải được escape khi chèn vào HTML; mọi truy vấn SQL nhận dữ liệu từ người dùng nên dùng prepared statement/tham số thay cho nối chuỗi. Tránh đặt tên một hàm chung kiểu `sanitizeEverything()` và giả định rằng đầu ra an toàn trong mọi ngữ cảnh.

Cũng cần phân biệt:

- **Từ chối dữ liệu lỗi:** Báo cho người gọi biết chính xác loại vi phạm, không tự làm mất thông tin.
- **Biến đổi dữ liệu hợp lệ theo quy tắc:** Ví dụ bỏ khoảng trắng ở đầu/cuối được sản phẩm chấp nhận.
- **Sửa lỗi tùy tiện:** Ví dụ âm thầm bỏ mọi ký tự không thuộc allowlist có thể biến `ad!min` thành `admin`; hai định danh khác nhau có thể trở thành một. Ví dụ này chọn **từ chối** để tránh trường hợp đó.

## 2. Viết chính sách trước khi viết thuật toán

Chính sách nên xác định từng chiều quyết định:

| Quyết định | Username | Display name |
| --- | --- | --- |
| Đầu vào `null` | Từ chối | Từ chối |
| Unicode normalization | `Normalizer.Form.NFKC` | `Normalizer.Form.NFC` |
| Khoảng trắng ở đầu/cuối | Bỏ bằng `strip()` | Bỏ bằng `strip()` sau khi thu gọn |
| Khoảng trắng bên trong | Không thay đổi; mẫu ký tự cuối cùng sẽ từ chối | Quy đổi chuỗi khoảng trắng về một dấu cách ASCII |
| Chữ hoa/chữ thường | `toLowerCase(Locale.ROOT)` | Giữ nguyên |
| Điều khiển/định dạng Unicode bất thường | Từ chối | Từ chối, ngoại trừ khoảng trắng được phép quy đổi |
| Chuỗi không hợp lệ về cấu trúc UTF-16 | Từ chối | Từ chối |
| Độ dài sau biến đổi | 3–16 code point | 1–40 code point |
| Ký tự được phép | `[a-z][a-z0-9_]{2,15}` | Chữ cái và dấu kết hợp Unicode, các từ cách nhau một dấu cách |

Giải thích phạm vi:

- Với username, `[a-z][a-z0-9_]{2,15}` yêu cầu ký tự đầu tiên là chữ thường ASCII, sau đó có từ 2 đến 15 ký tự thuộc `a-z`, `0-9` hoặc `_`. Tổng chiều dài là 3–16.
- Với display name, biểu thức `[\p{L}][\p{L}\p{M}]*(?: [\p{L}][\p{L}\p{M}]*)*` sử dụng nhóm Unicode Letter/Mark. Nó cố ý **không cho phép** chữ số, dấu gạch nối, dấu nháy, emoji hay nhiều cách viết tên khác. Ứng dụng thực tế phải điều chỉnh allowlist theo đối tượng người dùng; không nên dùng chính sách minh họa để tuyên bố tên của người dùng là “không hợp lệ”.
- `NFC` gộp các biểu diễn Unicode tương đương theo quy tắc chuẩn hóa dạng canonical; `NFKC` còn áp dụng biến đổi compatibility, ví dụ chữ Latin fullwidth `Ａ` thành `A`. NFKC có thể thay đổi ý nghĩa hiển thị của một số ký tự: chỉ dùng nếu hợp đồng định danh muốn coi các biểu diễn đó là tương đương.
- `Locale.ROOT` tránh kết quả chữ hoa/chữ thường thay đổi theo locale máy chạy. Đây là *lowercasing*, không phải thuật toán Unicode *case folding* đầy đủ. Với username vốn chỉ nhận ASCII sau xử lý, quy tắc minh họa đủ rõ.
- Bài này tính độ dài bằng **Unicode code point**, không dùng độ dài UTF-16 của `String.length()`. Code point vẫn không luôn bằng một ký tự mà con người cảm nhận (grapheme cluster); khi giới hạn tên hiển thị theo “ký tự nhìn thấy”, cần chọn định nghĩa khác phù hợp.

## 3. Xác định thứ tự pipeline

Một pipeline có thể kiểm chứng cần chạy theo thứ tự đã công bố:

~~~text
Input String
    |
    v
1. Kiểm tra null và giới hạn dữ liệu thô ở biên hệ thống
    |
    v
2. Chuẩn hóa Unicode NFC hoặc NFKC theo chính sách
    |
    v
3. Duyệt code point; xử lý khoảng trắng hoặc từ chối control/format/surrogate
    |
    v
4. Bỏ khoảng trắng đầu/cuối khi chính sách cho phép
    |
    v
5. Đổi chữ thường bằng Locale.ROOT nếu là username
    |
    v
6. Kiểm tra độ dài và allowlist trên giá trị kết quả
    |
    +---- không đạt ----> REJECT: trả lỗi có thể xử lý
    |
    v
7. Trả chuỗi đã làm sạch để dùng trong nghiệp vụ
~~~

**Vì sao phải kiểm tra sau biến đổi?** `ＮＡＭＥ` có thể biến thành `NAME` qua NFKC rồi thành `name` qua lowercase; cấu trúc và độ dài phải được đánh giá trên giá trị **thực sự được lưu hoặc so sánh**. Cũng nên giới hạn kích thước yêu cầu/chuỗi thô **trước** khi gọi các thao tác tốn CPU/bộ nhớ, tránh phải xử lý một chuỗi đầu vào không giới hạn chỉ để cuối cùng báo quá dài. Ví dụ Java bên dưới tập trung vào quy tắc trường dữ liệu; giới hạn kích thước thông điệp cần được cấu hình ở tầng nhận dữ liệu của ứng dụng.

Các quyết định đặc biệt theo trường phải tường minh. Chẳng hạn, pipeline tên hiển thị cho phép biến tab hoặc xuống dòng thành dấu cách; trường HTTP header hay một định danh dòng đơn có thể cần **từ chối CR/LF** ngay từ đầu. Không tái sử dụng chính sách tên hiển thị cho các ngữ cảnh đó.

## 4. Chương trình Java 17+ hoàn chỉnh

Lưu đoạn sau thành **InputCleanerDemo.java** (UTF-8). `record Policy` tập trung tất cả lựa chọn thành một đối tượng cấu hình, hai hằng `USERNAME` và `DISPLAY_NAME` là các hợp đồng đầu vào riêng biệt. `Pattern` được biên dịch một lần cho mỗi hợp đồng, còn `Matcher` được tạo mới khi kiểm tra từng chuỗi.

~~~java
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public class InputCleanerDemo {
    record Policy(
            Normalizer.Form normalization,
            boolean trim,
            boolean collapseWhitespace,
            boolean lowercaseRoot,
            int minCodePoints,
            int maxCodePoints,
            Pattern allowed) {}

    static final Policy USERNAME = new Policy(
            Normalizer.Form.NFKC, true, false, true, 3, 16,
            Pattern.compile("[a-z][a-z0-9_]{2,15}"));

    static final Policy DISPLAY_NAME = new Policy(
            Normalizer.Form.NFC, true, true, false, 1, 40,
            Pattern.compile(
                "[\\p{L}][\\p{L}\\p{M}]*(?: [\\p{L}][\\p{L}\\p{M}]*)*"));

    static boolean whitespace(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint);
    }

    static String clean(String raw, Policy policy) {
        if (raw == null) {
            throw new IllegalArgumentException("null");
        }

        String normalized =
                Normalizer.normalize(raw, policy.normalization());
        StringBuilder builder = new StringBuilder();
        boolean previousSpace = false;

        for (int offset = 0; offset < normalized.length();) {
            int cp = normalized.codePointAt(offset);
            offset += Character.charCount(cp);

            if (policy.collapseWhitespace() && whitespace(cp)) {
                if (!previousSpace) builder.append(' ');
                previousSpace = true;
                continue;
            }

            // Reject control/format characters and malformed UTF-16.
            if (Character.isISOControl(cp)
                    || Character.getType(cp) == Character.FORMAT
                    || (cp >= 0xD800 && cp <= 0xDFFF)) {
                throw new IllegalArgumentException(
                        "control/format/surrogate");
            }

            builder.appendCodePoint(cp);
            previousSpace = false;
        }

        String value = builder.toString();
        if (policy.trim()) value = value.strip();
        if (policy.lowercaseRoot()) {
            value = value.toLowerCase(Locale.ROOT);
        }

        int size = value.codePointCount(0, value.length());
        if (size < policy.minCodePoints()
                || size > policy.maxCodePoints()) {
            throw new IllegalArgumentException("length");
        }
        if (!policy.allowed().matcher(value).matches()) {
            throw new IllegalArgumentException("not allowed");
        }
        return value;
    }

    static void show(String label, String value, Policy policy) {
        try {
            System.out.println(
                    label + " -> " + clean(value, policy));
        } catch (IllegalArgumentException ex) {
            System.out.println(
                    label + " -> REJECT (" + ex.getMessage() + ")");
        }
    }

    public static void main(String[] args) {
        show("username fullwidth",
                "  \uFF21lice_12  ", USERNAME);
        show("username invalid", "abc!", USERNAME);
        show("username control", "al\0ice", USERNAME);

        show("display NFC",
                "  Ca\u0300   phe\u0302  ", DISPLAY_NAME);
        show("display whitespace",
                "  Cà  \t phê \n", DISPLAY_NAME);
        show("display emoji", "Lan 🙂", DISPLAY_NAME);
        show("display empty", " \t  ", DISPLAY_NAME);
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 InputCleanerDemo.java
java InputCleanerDemo
~~~

Đầu ra:

~~~text
username fullwidth -> alice_12
username invalid -> REJECT (not allowed)
username control -> REJECT (control/format/surrogate)
display NFC -> Cà phê
display whitespace -> Cà phê
display emoji -> REJECT (not allowed)
display empty -> REJECT (length)
~~~

**Phân tích từng tình huống:**

1. `\uFF21` là chữ Latin `Ａ` ở dạng fullwidth; NFKC chuyển thành `A`, `strip()` bỏ khoảng trắng hai đầu, sau đó `Locale.ROOT` chuyển thành `alice_12`. Tên này hợp lệ theo regex username.
2. `abc!` đủ độ dài, nhưng dấu `!` không nằm trong allowlist nên bị từ chối; chương trình **không tự biến** thành `abc`.
3. `al\0ice` chứa ký tự điều khiển NUL; việc kiểm tra `Character.isISOControl` từ chối trước khi kiểm tra regex.
4. Chuỗi `Ca\u0300   phe\u0302` chứa các dấu kết hợp; NFC chuẩn hóa thành `Cà   phê` rồi bộ gom khoảng trắng tạo `Cà phê`.
5. Chuỗi tên hiển thị có tab và xuống dòng được gộp thành một dấu cách vì **chính sách riêng của display name cho phép**. Không suy ra rằng mọi loại input đều được phép chuyển CR/LF như vậy.
6. Emoji `🙂` không nằm trong regex Letter/Mark được chọn; input bị từ chối rõ ràng, không bị xóa ngầm. Sản phẩm hỗ trợ emoji trong tên phải thay đổi chính sách.
7. Chuỗi chỉ gồm khoảng trắng trở thành chuỗi rỗng; kiểm tra độ dài phát hiện và từ chối.

## 5. Giải thích các quyết định kỹ thuật quan trọng

### Unicode normalization: NFC và NFKC

`java.text.Normalizer.normalize(input, form)` trả một chuỗi theo dạng chuẩn hóa được yêu cầu. Với `NFC`, các chuỗi ký tự tương đương theo chuẩn Unicode canonical được đưa về dạng thống nhất (khi có dạng ghép). Với `NFKC`, các ký tự compatibility còn được biến đổi rộng hơn. Điều này hữu ích với định danh nhưng có thể làm **hai đầu vào ban đầu khác nhau trở thành một giá trị**.

Nếu username được dùng làm khóa tra cứu hoặc ràng buộc unique trong cơ sở dữ liệu, hệ thống phải thực hiện chuẩn hóa **trước kiểm tra trùng lặp và trước khi lưu khóa định danh**, theo cùng một chính sách ở mọi đường vào dữ liệu. Khi thay đổi chính sách trong tương lai, cần đánh giá va chạm với các username đã tồn tại; không thể đơn giản đổi cách chuẩn hóa mà coi dữ liệu cũ không ảnh hưởng.

Normalization **không** giải quyết mọi ký tự giống nhau về hình dáng (*confusables*, ví dụ một số chữ Latin và Cyrillic), cũng không thay thế kiểm tra nghiệp vụ. Với các định danh công khai hoặc có giá trị bảo mật, cần cân nhắc chống nhầm lẫn ký tự ở cấp sản phẩm.

### Khoảng trắng không chỉ là ký tự `' '`

`String.strip()` loại bỏ hai đầu dựa trên `Character.isWhitespace`, khác `trim()` vốn có quy tắc cũ dựa trên giá trị ký tự. Một số khoảng trắng Unicode như non-breaking space không được `isWhitespace` nhận diện. Ví dụ đã chọn `Character.isWhitespace(cp) || Character.isSpaceChar(cp)` để nhận diện rộng hơn **trong chính sách thu gọn khoảng trắng**, đổi về dấu cách ASCII rồi mới `strip()`.

Bộ gom dùng `codePointAt`, `Character.charCount` và `StringBuilder.appendCodePoint` để không cắt đôi surrogate pair hợp lệ. Ký tự UTF-16 surrogate lẻ bị từ chối thay vì được tự động biến thành ký tự thay thế. Trường hợp `Character.FORMAT` cũng bị từ chối, gồm một số dấu điều khiển định dạng vô hình. Tuy nhiên, một số hệ chữ dùng các ký tự định dạng như zero-width joiner một cách hợp lệ; chính sách này cần được nới/điều chỉnh nếu sản phẩm hỗ trợ chúng. Không áp dụng lệnh xóa toàn bộ ký tự định dạng một cách không xét ngữ cảnh.

### Dùng allowlist sau khi xử lý

`Pattern.matcher(value).matches()` xác nhận **toàn bộ giá trị** phù hợp regex, khác `find()` chỉ tìm một đoạn khớp. Đây là lý do chuỗi `abc!` không được nhận dù có đoạn `abc` phù hợp. Regex là một phần của hợp đồng dữ liệu và phải đi kèm kiểm tra độ dài; không nên coi một regex duy nhất là đủ để bảo vệ hệ thống.

Quy tắc ký tự tên hiển thị ở ví dụ chỉ là tập con để minh họa. Tên thực tế có thể chứa dấu nối, dấu nháy, chữ số, chữ viết có quy tắc phức tạp hoặc emoji. Người thiết kế phải quyết định rõ cần chấp nhận những dạng nào, bổ sung kiểm thử cho các hệ chữ đó và ghi lại lý do từ chối.

### Khi nào nên báo lỗi thay vì âm thầm sửa?

Trong demo, các biến đổi được phép đều ghi thành thuộc tính của `Policy`. Các trường hợp ngoài chính sách được từ chối bằng `IllegalArgumentException` và nhãn nguyên nhân ổn định ở mức minh họa: `null`, `control/format/surrogate`, `length`, `not allowed`. Ứng dụng production nên chuyển lỗi này thành **mã lỗi có cấu trúc** (ví dụ enum/record kết quả) rồi trả thông báo phù hợp cho người dùng. Tránh phơi bày nguyên chuỗi nhạy cảm trong log hoặc error response.

Bởi việc biến đổi có thể gây mất phân biệt giữa nhiều input, đôi khi cần lưu **giá trị gốc** cho mục đích nghiệp vụ có quyền truy cập phù hợp và dùng **giá trị chuẩn hóa** để so sánh/tìm kiếm. Không giữ giá trị gốc nếu trường đó là bí mật hoặc chính sách bảo mật không cho phép.

## 6. Kiểm thử: những ca cần có ngoài ví dụ chạy tay

Bộ làm sạch nên có automated tests cho từng chính sách, nhất là tại ranh giới Unicode và chiều dài. Tập ca kiểm thử nên bao phủ:

| Nhóm tình huống | Input/điều kiện | Mong đợi |
| --- | --- | --- |
| Giá trị không có | `null` | Từ chối, không ném `NullPointerException` ngoài dự kiến. |
| Trống sau xử lý | `" \t "` cho display name | Từ chối vì độ dài nhỏ hơn 1. |
| Dạng Unicode tương đương | `"Ca\u0300"` và `"Cà"` trong display name | Cùng kết quả `"Cà"` sau NFC. |
| Dạng compatibility | `"\uFF21lice_12"` cho username | Thành `"alice_12"` sau NFKC và lowercase. |
| Ký tự không được phép | `"abc!"` cho username | Từ chối; không tự cắt `!`. |
| Control/format | NUL, tab trong username, ký tự định dạng ẩn | Từ chối theo chính sách username. |
| Khoảng trắng nhiều loại | Space, tab, xuống dòng, non-breaking space trong display name | Gom thành một dấu cách; loại bỏ ở hai đầu. |
| Độ dài | 2, 3, 16, 17 ký tự ASCII của username theo cấu trúc | Đúng các ranh giới, không sai lệch `<`/`<=`. |
| Unicode ngoài BMP | Emoji/supplementary code point | Xử lý nguyên code point; cuối cùng chấp nhận/từ chối theo policy. |
| Thay đổi chính sách | Cùng input, hai policy khác nhau | Kết quả theo từng policy; không có ngoại lệ “ẩn”. |
| Tính ổn định | `clean(clean(x, p), p)` với dữ liệu được chấp nhận | Kết quả bằng `clean(x, p)` đối với chính sách minh họa. |

Cần kiểm tra thêm giới hạn dữ liệu thô ở API, tải lớn và hành vi đối với đầu vào không tin cậy. Lưu ý kiểm thử **idempotence** nêu trên là tính chất được kỳ vọng của pipeline này, không phải thuộc tính tự động của mọi phép chuẩn hóa/làm sạch; chính sách mới cần tự chứng minh lại.

## 7. Đưa vào ứng dụng: điểm đặt và giới hạn trách nhiệm

Tại lớp biên nhận dữ liệu (controller, DTO mapper, import parser), chọn chính sách theo **trường dữ liệu** thay vì gọi một hàm chung không có ngữ cảnh:

~~~java
// Đoạn minh họa trong ứng dụng đã có InputCleanerDemo.
String username = InputCleanerDemo.clean(
        requestUsername, InputCleanerDemo.USERNAME);
String displayName = InputCleanerDemo.clean(
        requestDisplayName, InputCleanerDemo.DISPLAY_NAME);
~~~

Đoạn này là minh họa, **không phải chương trình chạy độc lập**: `requestUsername` và `requestDisplayName` do controller cung cấp. Chính sách trong lớp demo là package-private; khi tách thành thư viện ở package khác, công khai API thích hợp và kiểm soát phiên bản chính sách. Nên tổ chức việc validate ở một nơi dùng chung để tất cả lối vào (web, batch, migration) áp dụng nhất quán.

Đối với **mật khẩu**, không trim, lowercase, NFKC hoặc âm thầm thay thế ký tự trừ khi giao thức xác thực đã định nghĩa chính xác phép biến đổi và áp dụng nhất quán; đổi chuỗi đầu vào có thể đổi bí mật mà người dùng muốn sử dụng. Đối với **email, URL, tên tệp, địa chỉ đường dẫn, văn bản HTML**, cần parser, luật chuẩn hóa và biện pháp bảo vệ theo riêng từng loại. Ví dụ, chặn path traversal đòi hỏi kiểm soát đường dẫn sau khi resolve trong thư mục được phép; gom khoảng trắng không giải quyết được vấn đề.

Với chuỗi dùng trong **SQL**, hãy dùng tham số của `PreparedStatement` hoặc ORM; với **HTML**, escape theo ngữ cảnh khi xuất; với **lệnh shell**, tránh nối lệnh từ chuỗi người dùng; với **log**, xử lý ký tự điều khiển và dữ liệu nhạy cảm theo chính sách ghi log. Không gán cho `clean()` trách nhiệm thay thế các cơ chế đó.

## 8. Các lỗi thiết kế thường gặp

| Sai lầm | Rủi ro | Hướng khắc phục |
| --- | --- | --- |
| Dùng chung một quy tắc cho username, tên hiển thị, mật khẩu và nội dung | Mất dữ liệu, đổi ý nghĩa hoặc từ chối dữ liệu hợp lệ. | Chính sách tường minh theo từng trường và hợp đồng sản phẩm. |
| `replaceAll("[^a-z0-9]", "")` để xóa ký tự không hợp lệ | Có thể tạo va chạm định danh và che mất lỗi nhập. | Từ chối khi không thuộc allowlist; chỉ biến đổi đã thỏa thuận. |
| Kiểm tra regex trước normalize/lowercase, sau đó lưu chuỗi đã đổi | Dữ liệu cuối cùng có thể không còn cùng tính chất đã kiểm tra. | Xác thực trên biểu diễn cuối cùng sau tất cả phép biến đổi. |
| Dùng `String.length()` và cho rằng đó là số ký tự hiển thị | UTF-16 surrogate pair và grapheme cluster khiến số đếm khác. | Chọn code point hoặc grapheme cluster theo yêu cầu thật. |
| Dùng `toLowerCase()` phụ thuộc locale cho định danh | Có thể cho kết quả khác ở môi trường khác nhau. | Dùng `Locale.ROOT` nếu chính sách cho phép lowercase độc lập locale. |
| Áp dụng NFKC mặc định cho mọi chuỗi | Compatibility folding có thể thay đổi ý nghĩa dữ liệu. | Chọn NFC/NFKC theo từng loại dữ liệu và test va chạm. |
| Xóa hết ký tự Unicode thuộc loại FORMAT | Làm hỏng tên/văn bản trong một số hệ chữ. | Chỉ từ chối/biến đổi nếu hợp đồng trường cho phép. |
| Tin chuỗi đã clean là tự động an toàn cho HTML/SQL/URL | Các bề mặt tấn công có cơ chế diễn giải khác nhau. | Dùng prepared statements, output escaping và parser theo ngữ cảnh. |
| Chỉ giới hạn độ dài sau khi xử lý một input khổng lồ | Có thể tiêu tốn CPU/bộ nhớ không cần thiết. | Giới hạn kích thước thô ở ingress, rồi mới normalize và validate. |
| Không kiểm tra trùng sau chuẩn hóa username | Hai biểu diễn khác nhau có thể trùng một khóa định danh. | Chuẩn hóa trước uniqueness check và áp dụng nhất quán khi lưu/truy vấn. |

## 9. Bài tập mở rộng

1. Thêm chính sách **mã sản phẩm**: chỉ cho phép chữ ASCII in hoa, số và dấu gạch ngang, chiều dài 6–20; không thu gọn khoảng trắng bên trong. Viết kiểm thử input đúng/sai, cùng input sau normalize.
2. Thay `IllegalArgumentException` bằng `record CleanResult(String value, String errorCode)` hoặc mô hình lỗi có kiểu, bảo đảm người gọi nhận được mã lý do rõ ràng và không vô tình sử dụng chuỗi sai.
3. Tách chính sách display name hỗ trợ tên có dấu gạch nối và dấu nháy, viết kiểm thử đa ngôn ngữ trước khi mở rộng regex.
4. Thêm giới hạn độ dài chuỗi thô trước `Normalizer.normalize`, đồng thời ghi rõ giới hạn này áp dụng cho số code unit hay byte khi nhận qua HTTP.
5. Tạo kiểm thử tính ổn định `clean(clean(input, policy), policy)` và kiểm thử va chạm sau NFKC đối với tập định danh mẫu.

**Ghi nhớ:** Một bộ làm sạch đầu vào tốt bắt đầu từ **chính sách công khai về phép biến đổi và lỗi**, sau đó mới đến API Java. Chuẩn hóa đúng thứ tự, duyệt Unicode an toàn, kiểm tra độ dài và allowlist trên giá trị kết quả, từ chối trường hợp ngoài chính sách, và dùng biện pháp bảo vệ đúng ngữ cảnh khi lưu/hiển thị/sử dụng dữ liệu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-practice-002">Tìm và trích xuất trường từ chuỗi có cấu trúc đơn giản</a>

<details>
<summary>Xem chi tiết</summary>

# Tìm và trích xuất trường từ chuỗi có cấu trúc đơn giản trong Java

## 1. Mục tiêu và phạm vi

Rất nhiều dữ liệu thực tế có cấu trúc nhẹ nhưng được truyền dưới dạng một `String`: `id=42;name=Lan;active=true`, `INFO|user_1|125ms`, `2026-10-23 | INFO | Started` hoặc `code:200;message:OK`. Bài này hướng dẫn cách xác định ranh giới trường, trích xuất giá trị, kiểm tra lỗi và chuyển dữ liệu thành kiểu Java thích hợp mà vẫn bảo toàn ý nghĩa của từng trường.

Sau khi học, bạn có thể:
- Chọn `indexOf`, `substring`, `split` hay `Pattern`/`Matcher` theo cấu trúc đầu vào.
- Phân biệt dấu phân cách giữa **các trường** và dấu nối giữa **khóa và giá trị**; giữ nguyên các dấu phân cách hợp lệ nằm trong giá trị.
- Phát hiện trường bắt buộc bị thiếu, khóa trùng, đầu vào thừa, sai kiểu và giá trị rỗng.
- Xây dựng ví dụ Java 17+ chạy độc lập, có kết quả có thể kiểm chứng; thiết kế kiểm thử cho đầu vào chuẩn và đầu vào lỗi.
- Nhận ra giới hạn của cách tách thủ công: JSON, CSV có dấu nháy, URL query, log tùy ý cần parser theo đúng đặc tả.

**Phạm vi quan trọng:** ví dụ sử dụng chuỗi có **hợp đồng định dạng rõ ràng**. Dữ liệu đầu vào là văn bản đã được giải mã thành Java `String`. Bài này không tìm cách tự nhận diện bảng mã byte và không xây parser tổng quát cho mọi định dạng.

## 2. Đọc cấu trúc trước khi viết mã

Xét bản ghi:

```text
orderId=42;item=book;quantity=3;note=a=b
```

Ta định nghĩa hợp đồng đơn giản:

| Thành phần | Quy tắc |
| --- | --- |
| Phân cách các trường | Dấu chấm phẩy `;` |
| Phân cách khóa và giá trị | Dấu bằng `=` **đầu tiên** trong mỗi trường |
| Khóa | Ký tự ASCII A–Z/a–z ở đầu; sau đó được dùng chữ, chữ số hoặc `_` |
| Giá trị | Chuỗi bất kỳ không chứa `;`; có thể chứa thêm `=` |
| Khoảng trắng | Bỏ ở hai đầu khóa và giá trị bằng `strip()` |
| Trường bắt buộc | `orderId`, `item`, `quantity` |
| Trường tùy chọn | `note` |
| Khóa trùng | Từ chối để không âm thầm lấy nhầm giá trị |
| Giá trị số | `orderId` và `quantity` là số nguyên dương |

Với hợp đồng đó, `note=a=b` có nghĩa **khóa `note` với giá trị `a=b`**, không phải ba phần bằng nhau. Ta cần tách trường theo `;` và chỉ tìm dấu `=` đầu tiên trong từng trường.

Trước khi viết parser, trả lời năm câu hỏi:
1. Dấu nào là ký hiệu cấu trúc? Nó có được xuất hiện trong giá trị không?
2. Trường có thứ tự cố định hay xác định bằng tên khóa?
3. Khoảng trắng nào có ý nghĩa? Giá trị rỗng có hợp lệ không?
4. Khi trường bị thiếu, trùng hoặc sai kiểu, cần báo lỗi hay dùng giá trị mặc định?
5. Đầu vào có trích dẫn/escape/lồng nhau không? Nếu có, `split` đơn giản có thể không đủ.

Một parser tốt đưa các quy tắc trên thành điều kiện kiểm tra tường minh. Nếu định dạng nguồn chưa được thống nhất, việc chọn thuật toán trước thường dẫn tới giả định sai.

## 3. `indexOf` và `substring`: tìm chính xác ranh giới

`String.indexOf(char)` tìm vị trí đầu tiên của ký tự hoặc trả về `-1` nếu không thấy. `String.indexOf(String)` tìm chuỗi con; overload có `fromIndex` tìm từ một vị trí. `lastIndexOf` tìm lần xuất hiện cuối. `substring(beginIndex, endIndex)` trích phần [beginIndex, endIndex), tức là có đầu nhưng không có vị trí cuối.

Ví dụ Java 17+ chạy độc lập, lưu thành `IndexExtractDemo.java`:

```java
public class IndexExtractDemo {
    public static void main(String[] args) {
        String input = "status=OK;detail=a=b";
        int delimiter = input.indexOf(';');
        if (delimiter < 0) {
            throw new IllegalArgumentException("Thiếu dấu ;");
        }

        String left = input.substring(0, delimiter);
        String right = input.substring(delimiter + 1);

        int leftEquals = left.indexOf('=');
        int rightEquals = right.indexOf('=');
        if (leftEquals < 1 || rightEquals < 1) {
            throw new IllegalArgumentException("Trường thiếu khóa hoặc dấu =");
        }

        String status = left.substring(leftEquals + 1);
        String detail = right.substring(rightEquals + 1);
        System.out.println("status=" + status);
        System.out.println("detail=" + detail);
    }
}
```

```bash
javac -encoding UTF-8 IndexExtractDemo.java
java IndexExtractDemo
```

```text
status=OK
detail=a=b
```

Trong trường hợp `detail=a=b`, tìm dấu `=` **đầu** rồi lấy phần bên phải giữ nguyên `a=b`. Nếu dùng `lastIndexOf('=')` để phân cách khóa và giá trị, kết quả sẽ không đúng hợp đồng.

**Kiểm tra trước khi cắt:** `indexOf` có thể trả về `-1`; không được dùng chỉ số đó trực tiếp cho `substring` hoặc `charAt`. Các ngoại lệ như `StringIndexOutOfBoundsException` là dấu hiệu lỗi xử lý ranh giới, không phải chính sách xác thực dữ liệu mong muốn.

Các chỉ số trên `String` tính theo **đơn vị mã UTF-16**. `indexOf(String)` và `substring` vẫn hợp lệ với dữ liệu Unicode, nhưng không nên tự giả định rằng mỗi `char` tương ứng một ký tự người dùng nhìn thấy.

## 4. Dùng `split` khi ranh giới trường đơn giản

`String.split(regex)` nhận **regex**; khi delimiter do ứng dụng cung cấp là ký tự nguyên văn như `|` hoặc `.`, dùng `Pattern.quote(delimiter)` để tránh diễn giải nhầm dấu đặc biệt. Nếu cần giữ trường rỗng cuối, chọn `split(regex, -1)`, bởi `split(regex)` tương đương `split(regex, 0)` và bỏ các trường rỗng ở đuôi.

Với một cặp `key=value`, chỉ được cắt tại dấu `=` đầu tiên nếu nội dung value có thể chứa thêm `=`:

```java
String[] pair = "note=a=b".split("=", 2);
System.out.println(pair[0]); // note
System.out.println(pair[1]); // a=b
```

Cần kiểm tra `pair.length == 2`, khóa không rỗng và hợp đồng xử lý whitespace. Trong parser ở mục tiếp theo, `indexOf('=')` làm bước kiểm tra rõ hơn; `split(";", -1)` lại bảo toàn trường cuối rỗng để phát hiện dấu `;` thừa. Khi cần giữ chính xác từng dấu phân cách và tái tạo chuỗi gốc, xem bài **“Tách dữ liệu có phần tử rỗng và giữ đúng dấu phân cách”**, nơi giải thích đầy đủ `limit`, delimiter dạng regex và `Matcher`.

## 5. Ví dụ tổng hợp: parse các trường `key=value`

Ví dụ sau trích trường, kiểm tra khóa và kiểu dữ liệu, phát hiện khóa trùng, bảo toàn dấu `=` trong giá trị, sau đó in kết quả. Lưu thành `SimpleRecordParser.java`:

```java
import java.util.LinkedHashMap;
import java.util.Map;

public class SimpleRecordParser {
    private static Map<String, String> parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Bản ghi trống");
        }

        Map<String, String> fields = new LinkedHashMap<>();

        for (String rawEntry : input.split(";", -1)) {
            String entry = rawEntry.strip();
            int equalsIndex = entry.indexOf('=');
            if (equalsIndex <= 0) {
                throw new IllegalArgumentException(
                        "Trường thiếu khóa hoặc dấu =: " + entry);
            }

            String key = entry.substring(0, equalsIndex).strip();
            String value = entry.substring(equalsIndex + 1).strip();

            if (!key.matches("[A-Za-z][A-Za-z0-9_]*")) {
                throw new IllegalArgumentException("Khóa không hợp lệ: " + key);
            }
            if (fields.containsKey(key)) {
                throw new IllegalArgumentException("Khóa trùng: " + key);
            }
            fields.put(key, value);
        }
        return fields;
    }

    private static String required(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Thiếu giá trị: " + key);
        }
        return value;
    }

    private static int positiveInt(Map<String, String> fields, String key) {
        String raw = required(fields, key);
        final int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    key + " phải là số nguyên", ex);
        }
        if (value <= 0) {
            throw new IllegalArgumentException(key + " phải > 0");
        }
        return value;
    }

    public static void main(String[] args) {
        String input = "orderId=42;item=book;quantity=3;note=a=b";
        Map<String, String> fields = parse(input);

        int orderId = positiveInt(fields, "orderId");
        String item = required(fields, "item");
        int quantity = positiveInt(fields, "quantity");
        String note = fields.getOrDefault("note", "");

        System.out.println("orderId=" + orderId);
        System.out.println("item=" + item);
        System.out.println("quantity=" + quantity);
        System.out.println("note=" + note);
        System.out.println("fieldCount=" + fields.size());
    }
}
```

```bash
javac -encoding UTF-8 SimpleRecordParser.java
java SimpleRecordParser
```

```text
orderId=42
item=book
quantity=3
note=a=b
fieldCount=4
```

### Phân tích cách hoạt động

1. `split(";", -1)` giữ cả đoạn rỗng cuối để đầu vào `a=1;` không bị coi là hợp lệ một cách âm thầm: đoạn cuối rỗng sẽ vi phạm điều kiện `equalsIndex <= 0`.
2. `indexOf('=')` chỉ chọn dấu phân cách đầu tiên. Phần còn lại, kể cả `=`, thuộc giá trị.
3. `strip()` loại bỏ khoảng trắng Unicode ở hai đầu theo tiêu chí `Character.isWhitespace`; nếu khoảng trắng đầu/cuối là dữ liệu có ý nghĩa, **không nên strip giá trị**.
4. Biểu thức `[A-Za-z][A-Za-z0-9_]*` định nghĩa **khóa ASCII** theo hợp đồng ví dụ. Nó không ngầm đại diện mọi định danh Unicode hợp lệ.
5. `containsKey` phát hiện trùng khóa. Nếu dùng `Map.put` trực tiếp, trường xuất hiện sau có thể âm thầm ghi đè trường trước.
6. `required(...)` phân biệt tên khóa phải có nội dung với khóa tùy chọn. `positiveInt(...)` kiểm tra được cả sai cú pháp số, tràn phạm vi `int` và số không dương.
7. `LinkedHashMap` giữ thứ tự chèn khi cần xem lại trường theo thứ tự nguồn. Nếu không cần thứ tự, có thể chọn `HashMap`; không nên dựa vào thứ tự duyệt của `HashMap`.

**Giới hạn tường minh:** parser này không cấm khóa lạ ngoài `orderId`, `item`, `quantity` và `note`; không xử lý escape cho `;`; không kiểm tra giới hạn kích thước chuỗi/giá trị; và chỉ minh họa một schema nhỏ. Ứng dụng thật nên bổ sung giới hạn theo hợp đồng dữ liệu, whitelist khóa nếu cần, kiểm tra độ dài, chính sách logging an toàn và kiểm thử lỗi tương ứng.

### Trường hợp đầu vào lỗi

| Đầu vào | Hành vi của parser trên |
| --- | --- |
| `orderId=42;quantity=3;item=book` | Hợp lệ, `note` tùy chọn rỗng |
| `orderId=42;item=book;quantity=3;` | Báo lỗi vì có một trường rỗng cuối |
| `orderId=42;orderId=99;item=book;quantity=3` | Báo lỗi khóa `orderId` trùng |
| `=42;item=book;quantity=3` | Báo lỗi trường thiếu khóa |
| `orderId=42;item=book;quantity=zero` | Lỗi khi gọi `positiveInt` cho `quantity` |
| `orderId=42;item=book` | Lỗi thiếu giá trị `quantity` khi được yêu cầu |
| `orderId=42;item=;quantity=3` | Lỗi khi gọi `required` cho `item` |
| `orderId=42;item=book;quantity=3;note=a=b` | Hợp lệ, `note` giữ nguyên `a=b` |

Lưu ý: `parse(...)` tách và xác thực **cú pháp các trường**, còn `required(...)`/`positiveInt(...)` xác thực **schema và kiểu** sau khi tách. Thiết kế tách hai trách nhiệm này giúp kiểm thử rõ ràng và tái sử dụng logic phù hợp hơn.

## 6. Trích xuất trường theo vị trí từ bản ghi có delimiter cố định

Không phải chuỗi có cấu trúc nào cũng có khóa. Ví dụ một log đơn giản được định nghĩa gồm **ba trường** ngăn bởi ` | `:

```text
2026-10-23 | INFO | Started successfully
```

Có thể tìm hai dấu phân cách đầu tiên rồi cắt từng phần. Cách này giữ nguyên delimiter ` | ` nếu vô tình xuất hiện trong thông điệp phía cuối, vì thông điệp là toàn bộ phần còn lại.

Lưu thành `FixedFieldsDemo.java`:

```java
public class FixedFieldsDemo {
    private static final String SEPARATOR = " | ";

    public static void main(String[] args) {
        String line = "2026-10-23 | INFO | Started | background task";

        int first = line.indexOf(SEPARATOR);
        int second = first < 0
                ? -1 : line.indexOf(SEPARATOR, first + SEPARATOR.length());

        if (first < 0 || second < 0) {
            throw new IllegalArgumentException("Thiếu trường log");
        }

        String date = line.substring(0, first);
        String level = line.substring(
                first + SEPARATOR.length(), second);
        String message = line.substring(second + SEPARATOR.length());

        if (date.isBlank() || level.isBlank() || message.isBlank()) {
            throw new IllegalArgumentException("Trường bắt buộc trống");
        }

        System.out.println("date=" + date);
        System.out.println("level=" + level);
        System.out.println("message=" + message);
    }
}
```

```bash
javac -encoding UTF-8 FixedFieldsDemo.java
java FixedFieldsDemo
```

```text
date=2026-10-23
level=INFO
message=Started | background task
```

Cách này phù hợp khi **hai trường đầu cố định, phần cuối được phép chứa delimiter**. Nếu ngược lại phần đầu được phép chứa delimiter và phần cuối có cấu trúc cố định, cần xác định ranh giới khác hoặc tìm từ cuối bằng `lastIndexOf` theo hợp đồng.

`String.split(" \\| ", 3)` cũng có thể tách tối đa ba phần, nhưng `indexOf` làm rõ ranh giới và điều kiện thiếu delimiter. Nếu ngày, level hay message có quy tắc riêng, hãy xác thực sau khi lấy được trường, ví dụ `LocalDate.parse(date)` cho định dạng ngày ISO hoặc tập giá trị level được cho phép.

## 7. Dùng regex khi cần kiểm tra mẫu và trích nhiều trường cùng lúc

Khi cấu trúc có phần chữ cố định, số, dấu cách tùy chọn hoặc tập giá trị hợp lệ, regex có thể vừa xác nhận toàn chuỗi vừa trích nhóm. `Pattern` biểu diễn regex đã biên dịch; `Matcher` là đối tượng so khớp cho một chuỗi đầu vào.

Ví dụ định dạng theo hợp đồng: `LEVEL|userName|durationMs`, trong đó:
- `LEVEL` thuộc `INFO`, `WARN` hoặc `ERROR`.
- `userName` chỉ gồm chữ ASCII ở đầu rồi chữ, số, dấu gạch dưới.
- Thời lượng là một hoặc nhiều chữ số thập phân, theo sau bởi chữ `ms`.
- Cả dòng phải khớp; không chấp nhận tiền tố/hậu tố tùy ý.

Lưu thành `RegexFieldsDemo.java`:

```java
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexFieldsDemo {
    private static final Pattern LINE = Pattern.compile(
            "(?<level>INFO|WARN|ERROR)\\|"
            + "(?<user>[A-Za-z][A-Za-z0-9_]*)\\|"
            + "(?<duration>\\d+)ms");

    public static void main(String[] args) {
        String input = "INFO|linh_2|125ms";
        Matcher matcher = LINE.matcher(input);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Sai định dạng dòng log");
        }

        String level = matcher.group("level");
        String user = matcher.group("user");
        int duration = Integer.parseInt(matcher.group("duration"));

        System.out.println("level=" + level);
        System.out.println("user=" + user);
        System.out.println("durationMs=" + duration);
    }
}
```

```bash
javac -encoding UTF-8 RegexFieldsDemo.java
java RegexFieldsDemo
```

```text
level=INFO
user=linh_2
durationMs=125
```

Dấu `\\|` trong literal Java tạo regex `\|` để khớp dấu gạch đứng. `\\d+` tạo regex cho một hoặc nhiều chữ số. `(?<level>...)` khai báo **named capturing group**; `group("level")` trích phần tương ứng sau khi `matches()` thành công.

### `matches()` và `find()` khác nhau

- `matcher.matches()` yêu cầu **toàn bộ chuỗi đầu vào** phù hợp với mẫu. Nên dùng khi xác thực một bản ghi hoàn chỉnh.
- `matcher.find()` tìm **một đoạn con phù hợp** bắt đầu từ vị trí quét hiện tại. Phù hợp khi lấy nhiều lần xuất hiện trong văn bản dài; gọi lại `find()` sẽ tiếp tục tìm.
- `matcher.group(...)` chỉ hợp lệ sau khi so khớp thành công. Nếu lấy group khi chưa `matches()`/`find()` thành công, sẽ phát sinh trạng thái lỗi của `Matcher`.

Ví dụ chỉ để minh họa cú pháp gọi `find()` (đoạn code đặt trong phương thức Java):

```java
Matcher m = Pattern.compile("id=(\\d+)").matcher("id=10; id=20");
while (m.find()) {
    System.out.println(m.group(1));
}
```

```text
10
20
```

Đối với dữ liệu phức tạp hoặc input không tin cậy, tránh regex lồng nhiều lượng tử tham lam có thể gây chi phí backtracking lớn. `Pattern` bất biến và có thể tái sử dụng; `Matcher` có trạng thái riêng, không nên dùng chung một thể hiện giữa nhiều luồng.

## 8. Chọn `indexOf`, `split` hay regex?

| Cấu trúc đầu vào | Lựa chọn gợi ý | Ví dụ |
| --- | --- | --- |
| Lấy nội dung sau dấu `=` đầu tiên | `indexOf` + `substring` | `message=a=b=c` |
| Tách vài phần theo dấu phân cách cố định | `split` với regex đã escape; hoặc `indexOf` | `red;green;blue` |
| Cần giữ trường trống cuối | `split(regex, -1)` | `A|B|` |
| Chỉ tách thành đúng hai phần | `split(regex, 2)` hoặc `indexOf` | `key=value=extra` |
| Bản ghi có trường theo vị trí, phần cuối chứa delimiter | Hai lần `indexOf` hoặc `split(regex, 3)` | `date | level | long message` |
| Mẫu gồm tên, số, tập enum đơn giản | `Pattern` + `Matcher.matches` | `INFO|user_1|23ms` |
| Quét nhiều kết quả nằm giữa văn bản | `Matcher.find` | `id=10; id=20` |
| CSV có trường đặt trong dấu nháy / JSON / URL query | Parser tương ứng | `"Lan, Nguyễn",21`, JSON hoặc query có percent-encoding |

Không có phương pháp nào mặc định đúng cho mọi chuỗi. Chọn cách đơn giản nhất vẫn đáp ứng đầy đủ hợp đồng đầu vào, điều kiện lỗi và trường hợp delimiter bên trong giá trị.

## 9. Kiểm tra dữ liệu sau khi trích xuất

**Tách chuỗi không đồng nghĩa với dữ liệu hợp lệ.** Sau khi lấy giá trị, xác minh theo kiểu mong đợi:

- **Số nguyên:** `Integer.parseInt(...)` ném `NumberFormatException` nếu không phải số nguyên thập phân hợp lệ hoặc vượt miền `int`. Nếu cần số lớn, cân nhắc `Long.parseLong` hoặc `BigInteger`.
- **Boolean:** `Boolean.parseBoolean(...)` trả về `true` duy nhất cho chuỗi tương đương `"true"` khi bỏ qua hoa/thường; phần còn lại, kể cả chuỗi sai như `"tru"`, trả về `false`. Nếu input phải là `true/false` hợp lệ, hãy xác minh **trước** khi chuyển.
- **Ngày ISO:** `LocalDate.parse(...)` xác thực cú pháp/ngày hợp lệ và ném `DateTimeParseException` khi sai. Không chỉ kiểm tra ngày bằng regex `\d{4}-\d{2}-\d{2}` rồi giả định lịch đúng.
- **Trường tùy chọn:** quyết định rõ giữa *khóa không có*, *khóa có giá trị rỗng*, và *khóa có giá trị mặc định*. Ví dụ `Map.getOrDefault` chỉ trả về mặc định khi khóa không tồn tại; nếu có khóa với giá trị rỗng, kết quả vẫn là chuỗi rỗng.
- **Mã định danh:** đặt giới hạn ký tự và chiều dài theo yêu cầu nghiệp vụ; đừng cho rằng `trim` hay `strip` có thể thay cho kiểm tra định dạng.

Ví dụ boolean nghiêm ngặt (đoạn phương thức độc lập có thể đặt trong một class):

```java
static boolean parseStrictBoolean(String raw) {
    if ("true".equalsIgnoreCase(raw)) {
        return true;
    }
    if ("false".equalsIgnoreCase(raw)) {
        return false;
    }
    throw new IllegalArgumentException("Giá trị boolean không hợp lệ: " + raw);
}
```

Tương tự, nếu `quantity` phải lớn hơn 0, `parseInt` thành công chưa đủ: vẫn phải kiểm tra miền giá trị sau chuyển đổi.

## 10. Lỗi thường gặp và cách tránh

### 10.1. Nhầm delimiter với regex

`"A|B|C".split("|")` không tách theo dấu gạch đứng theo nghĩa literal vì `|` có ý nghĩa đặc biệt trong regex. Dùng `split("\\|")` hoặc `split(Pattern.quote("|"))`.

Tương tự, `split(".")` không tách đúng dấu chấm; sử dụng `split("\\.")` hoặc `Pattern.quote(".")`. Lỗi có thể âm thầm tạo các phần tử không mong muốn hoặc một mảng rỗng.

### 10.2. Dùng `split("=")` cho giá trị có dấu `=`

`note=a=b` phải tách tại dấu `=` đầu tiên. Dùng `indexOf('=')` và `substring`, hoặc `split("=", 2)`. Nếu định dạng có escaping phức tạp, cả hai cách đơn giản có thể không đủ.

### 10.3. Truy cập phần tử mảng mà không kiểm tra

Mã `input.split(";")[1]` sẽ có thể ném `ArrayIndexOutOfBoundsException` khi thiếu dấu phân cách. Luôn kiểm tra số trường và chính sách trường trống trước khi truy cập.

### 10.4. Đánh rơi trường trống cuối

`"A;B;".split(";")` mặc định bỏ đoạn trống cuối. Nếu dữ liệu bắt buộc có ba cột và cột thứ ba rỗng là thông tin có ý nghĩa, dùng `split(";", -1)`.

### 10.5. Lấy giá trị mà không kiểm tra khóa

Với `"name=Lan;role=admin"`, nếu yêu cầu trường `role`, không nên coi “phần tử thứ hai” chắc chắn là `role` trừ khi thứ tự cố định là một phần của hợp đồng. Dữ liệu hoán đổi thứ tự vẫn nên đọc đúng theo tên khóa nếu nguồn khai báo cấu trúc theo tên.

### 10.6. Ghi đè khóa trùng

`id=1;id=2` đặt ra câu hỏi: lấy giá trị đầu, giá trị cuối hay từ chối? Nếu không có chính sách rõ, `Map.put` có thể che dấu dữ liệu bất thường. Ví dụ tổng hợp chọn từ chối khóa trùng.

### 10.7. Coi mọi chuỗi có dấu phẩy là CSV đơn giản

Dòng CSV `"Nguyen, Lan",21` có dấu phẩy **bên trong giá trị được quote**. `split(",")` sẽ tách sai; CSV còn có escaping dấu nháy và xuống dòng trong trường. Dùng thư viện/parser CSV tuân thủ đúng dialect thay vì một regex nhanh.

### 10.8. Tự parse query string hoặc JSON

Query string URL có thể có percent-encoding, dấu `=`/`&` trong dữ liệu đã escape, key trùng và quy tắc encoding. JSON có string escape, dấu ngoặc lồng nhau và kiểu dữ liệu. Dùng API hoặc parser dành riêng cho định dạng đó. Bài này áp dụng cho cấu trúc đơn giản **do ứng dụng kiểm soát**.

### 10.9. Không giới hạn đầu vào

Nếu nhận chuỗi rất lớn hoặc hàng triệu trường, `split` và regex có thể tiêu tốn thời gian/bộ nhớ đáng kể. Giới hạn chiều dài đầu vào, số trường và độ dài giá trị theo hợp đồng; chỉ ghi log thông tin cần thiết, tránh lộ dữ liệu riêng tư. Với luồng dữ liệu lớn, cân nhắc parser streaming.

### 10.10. Tách trường thành công nhưng bỏ qua dữ liệu thừa

Regex với `find()` có thể nhận một đoạn hợp lệ bên trong một dòng không hợp lệ. Khi một bản ghi **phải khớp toàn phần**, dùng `matches()`, kiểm tra đúng số trường hoặc các điều kiện kết thúc rõ ràng.

## 11. Thiết kế kiểm thử cho parser đơn giản

Kiểm thử ít nhất những nhóm sau. Việc parser trả về kết quả đúng với một ví dụ đẹp chưa đủ chứng minh sự vững chắc.

| Nhóm | Đầu vào ví dụ | Điều cần xác minh |
| --- | --- | --- |
| Bình thường | `id=12;name=Lan` | Đúng hai trường và giá trị |
| Đổi thứ tự khóa | `name=Lan;id=12` | Lookup theo khóa không phụ thuộc thứ tự |
| Rỗng toàn chuỗi | `""` | Bị từ chối hoặc xử lý theo hợp đồng |
| Thiếu dấu phân cách cặp | `id12` | Báo lỗi có nguyên nhân rõ |
| Khóa rỗng | `=12` | Bị từ chối |
| Giá trị rỗng | `name=` | Xử lý khác với khóa không có, theo schema |
| Giá trị chứa `=` | `note=a=b` | Không làm mất đoạn `=b` |
| Trường rỗng cuối | `id=12;` | Không bị bỏ qua âm thầm |
| Khóa trùng | `id=12;id=13` | Phát hiện theo chính sách |
| Sai kiểu | `quantity=three` | Lỗi chuyển kiểu được xử lý |
| Số ngoài phạm vi | `quantity=99999999999999` | Không tràn mà không báo |
| Ký tự Unicode | `name=Hà Nội` | Đúng chuỗi Unicode và chính sách whitespace |
| Chuỗi rất dài | Nhiều trường/giá trị dài | Có giới hạn tài nguyên |
| Delimiter trong dữ liệu | `note=a;b` | Bị từ chối theo định dạng đơn giản hoặc chuyển sang cú pháp có escape |

Nên chia kiểm thử thành ba cấp: kiểm thử **tách cú pháp**, kiểm thử **xác thực schema/kiểu**, và kiểm thử **hành vi ứng dụng** khi một bản ghi không hợp lệ. Đặc biệt, tránh nuốt ngoại lệ bằng cách trả về chuỗi rỗng hoặc giá trị `0` khi đó có thể bị hiểu là dữ liệu thật.

## 12. Bài tập thực hành

**Bài tập 1 — key/value:** viết hàm `extractValue(String record, String wantedKey)` cho định dạng `a=1;b=2;c=3`. Hàm trả về `Optional<String>` hoặc giá trị có chính sách “không tìm thấy” rõ ràng. Xử lý đúng `note=a=b` và khóa `a` so với `aa`. Không dùng phép kiểm tra `contains("a=")` đơn giản vì có thể khớp nhầm.

**Bài tập 2 — bản ghi cố định:** phân tích `"2026-10-23|WARN|Disk nearly full"` thành ngày, level và thông điệp. Kiểm tra hai dấu phân cách đầu tiên, xác thực ngày bằng `LocalDate.parse` và level thuộc một tập giá trị được cho phép.

**Bài tập 3 — kiểm tra giá trị số:** từ `"user=linh;age=21;active=true"`, trích các trường và chuyển kiểu. Từ chối `age=-3`, `age=abc` và `active=maybe` nếu nghiệp vụ yêu cầu tuổi không âm và boolean tường minh.

**Bài tập 4 — Regex:** viết `Pattern` để khớp toàn chuỗi `"HTTP 200 /health"`, trích mã trạng thái ba chữ số và đường dẫn bắt đầu bằng `/`. So sánh hành vi `matches()` và `find()` khi thêm văn bản không hợp lệ ở đầu dòng.

**Bài tập 5 — giới hạn của `split`:** thử `"id=1;note=red;blue"`. Vì `;` trong giá trị không được escape, parser đơn giản sẽ không biết chính xác phần nào là ranh giới. Hãy đề xuất một **hợp đồng dữ liệu mới** (ví dụ escape, độ dài trường hoặc định dạng chuẩn như JSON) và giải thích vì sao cần parser riêng cho hợp đồng đó.

## 13. Ghi nhớ khi áp dụng

Hãy bắt đầu bằng **hợp đồng đầu vào**: delimiter là gì, giá trị có được chứa delimiter hay không, trường nào bắt buộc và khi nào báo lỗi. Với cấu trúc cố định nhỏ, `indexOf` và `substring` là cách rõ ràng. Với danh sách phân cách đều, `split` tiện lợi nhưng phải nhớ regex và tham số `limit`. Với mẫu có nhiều điều kiện và nhóm cần lấy, `Pattern`/`Matcher` hữu ích; `matches()` xác thực toàn bộ bản ghi còn `find()` dò đoạn con.

Luôn kiểm tra vị trí trước khi cắt, kiểm tra số lượng trường trước khi truy cập, không ghi đè khóa trùng ngoài ý muốn và xác thực kiểu sau khi tách. Khi dữ liệu đã có đặc tả phức tạp như CSV, URL query hoặc JSON, chọn parser đúng đặc tả thay vì kéo dài một chuỗi các phép `split`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-practice-003">Ghép báo cáo văn bản bằng StringBuilder hoặc StringJoiner</a>

<details>
<summary>Xem chi tiết</summary>

# Ghép báo cáo văn bản bằng `StringBuilder` hoặc `StringJoiner`

## Mục tiêu bài thực hành

Trong chương trình Java, báo cáo văn bản có thể gồm tiêu đề, các dòng dữ liệu, bảng căn cột, ghi chú, tổng cộng và danh sách tên hoặc trạng thái. Hai công cụ trong `java.lang` và `java.util` hỗ trợ các dạng ghép này:

- `StringBuilder` phù hợp khi bạn **xây dựng chuỗi theo nhiều bước**, xen kẽ văn bản cố định, các trường dữ liệu, điều kiện, tiêu đề, newline và những dòng được định dạng.
- `StringJoiner` phù hợp khi bạn **ghép một danh sách phần tử** bằng một dấu phân cách thống nhất, có thể thêm tiền tố (`prefix`) và hậu tố (`suffix`).

Sau bài học, bạn sẽ tự tạo báo cáo bán hàng có cột, số lượng và tổng tiền; tạo phần danh sách người phụ trách bằng `StringJoiner`; xử lý danh sách rỗng/`null`; kiểm soát newline, locale và escaping; cuối cùng chọn công cụ phù hợp với từng phần của báo cáo.

## 1. Hiểu vai trò của hai API

### `StringBuilder`: xây dựng nội dung theo thứ tự

`StringBuilder` giữ một dãy ký tự thay đổi được. Bạn có thể thêm giá trị bằng `append()`, chèn bằng `insert()`, thay đoạn bằng `replace()` hoặc chỉnh độ dài. `append()` trả về chính builder để viết chuỗi lời gọi:

```java
StringBuilder report = new StringBuilder();
report.append("BAO CAO").append('\n');
report.append("So muc: ").append(3).append('\n');
String result = report.toString();
```

`result` chứa:

```text
BAO CAO
So muc: 3
```

Trong ví dụ này, `'\n'` là xuống dòng LF có chủ đích. Nếu báo cáo là văn bản dùng nội bộ trên hệ điều hành hiện tại, bạn có thể dùng `System.lineSeparator()`; nếu báo cáo cần so sánh chính xác giữa các môi trường hoặc được truyền qua giao thức yêu cầu LF, hãy quy định rõ `"\n"`.

Builder có `length()` (số đơn vị mã UTF-16 đang chứa) và `capacity()` (khả năng chứa trước khi cần mở rộng). Nó **không tự căn cột**, tự thêm dấu phân cách giữa các phần tử hay tự escaping dữ liệu. Bạn phải viết quy tắc trình bày.

### `StringJoiner`: một phần tử, một dấu phân cách

`StringJoiner` có constructor:

```java
new StringJoiner(delimiter)
new StringJoiner(delimiter, prefix, suffix)
```

Ví dụ:

```java
import java.util.StringJoiner;

StringJoiner names = new StringJoiner(", ", "[", "]");
names.add("An").add("Binh").add("Chi");
System.out.println(names); // [An, Binh, Chi]
```

Dấu `", "` chỉ xuất hiện **giữa** các phần tử; không có dấu phẩy thừa ở cuối. `prefix` và `suffix` bao quanh toàn bộ kết quả. Đối tượng `StringJoiner` cũng có `length()`, `setEmptyValue()`, `merge()` và `toString()`.

`StringJoiner` thích hợp với tên thành viên, các nhãn, danh sách mã, một dòng CSV đã escaped từng trường, hoặc những dòng/bản ghi được tách bởi newline cố định. Nó không có API căn cột, tính tổng tiền hoặc tự quản lý các khối nội dung phức tạp.

### So sánh trước khi chọn

| Tiêu chí | `StringBuilder` | `StringJoiner` |
| --- | --- | --- |
| Đơn vị bổ sung | Một mẩu văn bản, số, ký tự hoặc đối tượng | Một phần tử dạng `CharSequence` |
| Dấu phân cách giữa các phần tử | Tự thêm bằng `append` hoặc điều kiện | Tự thêm khi `add` |
| Tiền tố/hậu tố cho cả danh sách | Tự ghép | Hỗ trợ ngay trong constructor |
| Báo cáo nhiều section, tiêu đề, dòng tổng | Linh hoạt và thường dễ đọc hơn | Có thể dùng cho từng danh sách trong section |
| Danh sách rỗng | Kết quả do logic của người viết quyết định | Có `setEmptyValue` |
| Căn cột/số/ngày | Kết hợp `String.format` hoặc `Formatter` | Định dạng phần tử trước khi gọi `add` |
| Đặt trước dung lượng | `new StringBuilder(initialCapacity)`, `ensureCapacity` | Không có constructor capacity công khai |
| An toàn khi nhiều luồng cùng chỉnh sửa | Không bảo đảm | Không bảo đảm |

Cả hai đều có thể tạo `String` cuối cùng. Đừng chọn công cụ theo giả định rằng một công cụ luôn nhanh hơn mọi trường hợp; cấu trúc dữ liệu và khối lượng ghép quyết định lựa chọn hợp lý.

## 2. Thiết kế đầu ra trước khi viết code

Giả sử báo cáo bán hàng cần những trường sau:

- Tiêu đề `BAO CAO BAN HANG`.
- Bảng có cột tên, số lượng, đơn giá, thành tiền.
- Mỗi dòng thành tiền bằng `quantity × unitPrice`.
- Tổng cộng của các dòng.
- Căn trái cho tên và căn phải cho số.
- Dấu chấm thập phân ổn định để ví dụ chạy giống nhau ở mọi máy.

Kết quả mong muốn:

```text
BAO CAO BAN HANG
-----------------
Ten       SL  Don gia Thanh tien
Sach       2    12.50      25.00
But        3     1.20       3.60
TONG: 28.60
```

Để số tiền có dạng thập phân chính xác, dùng `BigDecimal` thay vì dựa vào phép cộng `double` rồi hy vọng kết quả không gặp sai số biểu diễn nhị phân. Để định dạng số theo cách ổn định, dùng `Locale.ROOT`. Với ứng dụng thực tế, muốn hiển thị dấu phân nhóm hoặc ký hiệu tiền tệ theo quốc gia thì chọn locale theo yêu cầu sản phẩm.

## 3. Bài thực hành 1: báo cáo nhiều cột bằng `StringBuilder`

Lưu toàn bộ chương trình dưới tên **`SalesReportDemo.java`**:

```java
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

public class SalesReportDemo {
    record Sale(String name, int quantity, BigDecimal unitPrice) {}

    public static void main(String[] args) {
        List<Sale> sales = List.of(
                new Sale("Sach", 2, new BigDecimal("12.50")),
                new Sale("But", 3, new BigDecimal("1.20"))
        );

        StringBuilder report = new StringBuilder(256);
        report.append("BAO CAO BAN HANG\n");
        report.append("-----------------\n");
        report.append(String.format(
                Locale.ROOT,
                "%-8s %3s %8s %10s",
                "Ten", "SL", "Don gia", "Thanh tien"
        )).append('\n');

        BigDecimal total = BigDecimal.ZERO;

        for (Sale sale : sales) {
            BigDecimal amount = sale.unitPrice().multiply(
                    BigDecimal.valueOf(sale.quantity()));
            total = total.add(amount);

            report.append(String.format(
                    Locale.ROOT,
                    "%-8s %3d %8.2f %10.2f",
                    sale.name(),
                    sale.quantity(),
                    sale.unitPrice(),
                    amount
            )).append('\n');
        }

        report.append(String.format(
                Locale.ROOT, "TONG: %.2f", total)).append('\n');

        System.out.print(report.toString());
    }
}
```

Biên dịch và chạy bằng Java 17+:

```bash
javac SalesReportDemo.java
java SalesReportDemo
```

Stdout (nội dung các dòng):

```text
BAO CAO BAN HANG
-----------------
Ten       SL  Don gia Thanh tien
Sach       2    12.50      25.00
But        3     1.20       3.60
TONG: 28.60
```

### Giải thích từng điểm

**Mô hình dữ liệu.** `record Sale` chứa tên, số lượng và đơn giá. `BigDecimal` nhận từ chuỗi thập phân `"12.50"` và `"1.20"`; tránh khởi tạo giá trị tiền tệ từ literal `double` khi cần biểu diễn số thập phân đúng theo ý định.

**Tính toán độc lập với trình bày.** `amount` được tính bằng `unitPrice.multiply(BigDecimal.valueOf(quantity))`; `total` được cộng dồn dưới dạng `BigDecimal`. Bạn chỉ định dạng tiền thành chuỗi khi đưa vào báo cáo, để các phép tính sau đó vẫn thao tác trên số.

**Căn cột.** Trong `"%-8s %3d %8.2f %10.2f%n"`:

- `%-8s`: chuỗi căn trái, độ rộng tối thiểu 8.
- `%3d`: số nguyên căn phải, độ rộng tối thiểu 3.
- `%8.2f`: số thập phân căn phải, độ rộng tối thiểu 8, hiển thị hai chữ số sau dấu thập phân.
- `%10.2f`: số thập phân tương tự, độ rộng tối thiểu 10.
- Sau mỗi phần đã định dạng, `append('\n')` thêm một dấu xuống dòng LF thống nhất.

**Độ rộng chỉ là tối thiểu.** Nếu `sale.name()` dài hơn 8 đơn vị định dạng, Formatter không cắt ngắn nó mặc định mà mở rộng trường. Bảng có thể mất căn thẳng hàng khi dữ liệu thực tế dài, chứa emoji, ký tự kết hợp hoặc ký tự có độ rộng màn hình khác nhau. Các width của `Formatter` không phải phép tính độ rộng cột trực quan của mọi terminal; khi cần bảng Unicode chuẩn chỉnh, cần thêm chính sách giới hạn/escape/đo độ rộng hiển thị hoặc công cụ chuyên dụng.

**Dung lượng khởi tạo 256.** Đây là ước lượng, không phải giới hạn độ dài báo cáo. Nếu báo cáo vượt capacity, builder tự mở rộng. Đối với báo cáo nhỏ, constructor mặc định `new StringBuilder()` cũng thường đủ. Khi dữ liệu có tổng kích thước dự đoán được, ước lượng theo tổng `length()` của các phần và phần định dạng giúp giảm mở rộng; tránh tạo capacity quá lớn.

**Dấu xuống dòng.** Chương trình này dùng `'\n'` ở **tất cả các dòng**, bao gồm sau `String.format`. Vì vậy kết quả trong bộ nhớ có LF ổn định giữa Windows và Unix, thuận tiện cho kiểm thử hoặc tệp yêu cầu LF. Nếu báo cáo cho terminal theo quy tắc nền tảng, có thể chọn `%n` hoặc `System.lineSeparator()` một cách nhất quán, nhưng không trộn các kiểu newline trong cùng định dạng đầu ra.

### Khi nào dùng `Formatter` trực tiếp với builder?

`String.format(...)` tạo một `String` trung gian rồi `append` nó vào builder. Với báo cáo lớn có rất nhiều dòng, có thể dùng `Formatter` để định dạng trực tiếp vào một `StringBuilder`:

```java
import java.util.Formatter;
import java.util.Locale;

StringBuilder out = new StringBuilder(256);
try (Formatter formatter = new Formatter(out, Locale.ROOT)) {
    formatter.format("%-8s %3d%n", "Sach", 2);
    formatter.format("%-8s %3d%n", "But", 3);
}
String report = out.toString();
```

Đây là **đoạn minh họa**, không phải file Java độc lập; nó có thể đặt trong một phương thức. `Formatter` nhận builder như một `Appendable` và ghi trực tiếp vào đó. `StringBuilder` không bị đóng bởi `Formatter.close()` vì bản thân nó không phải resource có `close()`. Dù vậy, việc tránh `String` trung gian không tự bảo đảm chương trình nhanh hơn trong mọi workload; hãy đo nếu đây là đường chạy quan trọng.

## 4. Bài thực hành 2: danh sách, tiền tố và hậu tố với `StringJoiner`

Lưu thành **`JoinerReportDemo.java`**:

```java
import java.util.StringJoiner;

public class JoinerReportDemo {
    public static void main(String[] args) {
        StringJoiner people = new StringJoiner(", ", "Phu trach: ", ".");
        people.add("An").add("Binh").add("Chi");
        System.out.println(people);

        StringJoiner empty = new StringJoiner(", ", "Phu trach: ", ".");
        System.out.println("empty-default=" + empty);
        empty.setEmptyValue("Phu trach: (chua co).");
        System.out.println("empty-custom=" + empty);

        StringJoiner tasks = new StringJoiner(
                "\n", "MUC CONG VIEC\n", "\nKET THUC");
        tasks.add("1. Doc du lieu");
        tasks.add("2. Tao bao cao");
        System.out.println(tasks);
    }
}
```

Chạy:

```bash
javac JoinerReportDemo.java
java JoinerReportDemo
```

Stdout:

```text
Phu trach: An, Binh, Chi.
empty-default=Phu trach: .
empty-custom=Phu trach: (chua co).
MUC CONG VIEC
1. Doc du lieu
2. Tao bao cao
KET THUC
```

Khi chưa gọi `add()`, `StringJoiner` trả về `prefix + suffix` mặc định: trường hợp trên là `"Phu trach: ."`, dù chưa có người nào. `setEmptyValue` cho phép dùng một chuỗi thay thế **chỉ khi joiner vẫn trống**. Sau khi có phần tử, giá trị được tạo từ `prefix + elements-with-delimiter + suffix`; `setEmptyValue` không tự chèn phần tử.

`StringJoiner.length()` đo độ dài dạng chuỗi hiện tại, bao gồm prefix/suffix hoặc empty value tương ứng. Đừng dùng `StringJoiner.length() == 0` để xác nhận "chưa add phần tử": joiner chưa thêm phần tử nhưng có prefix/suffix vẫn có độ dài lớn hơn 0. Với luồng xử lý tự viết, bạn có thể giữ bộ đếm mục riêng nếu cần biết số phần tử.

### Mỗi phần tử có thể là một dòng đã định dạng

Nếu mỗi bản ghi được tạo trước thành một dòng `String` hoàn chỉnh, `StringJoiner("\n")` có thể nối các dòng mà không cần kiểm tra phần tử đầu/ cuối:

```java
StringJoiner rows = new StringJoiner("\n");
rows.add("Sach,2,25.00");
rows.add("But,3,3.60");

String body = rows.toString(); // hai dòng, không có newline sau dòng cuối
```

Ngược lại, với báo cáo nhiều section cần các ký tự khác nhau ở đầu/cuối từng khối hoặc các dòng tổng, `StringBuilder` thường làm cấu trúc hiển thị rõ hơn. Có thể **kết hợp**: `StringJoiner` sinh một dòng nhãn, rồi `StringBuilder` append dòng đó vào báo cáo chính.

## 5. Kết hợp cả hai trong một báo cáo

Một báo cáo có thể gồm: tiêu đề, nội dung chính, tổng cộng và danh sách người phụ trách. Sử dụng builder cho bố cục chung và joiner cho nhóm phần tử đồng nhất:

```java
import java.util.List;
import java.util.StringJoiner;

public class MixedReportDemo {
    public static void main(String[] args) {
        List<String> reviewers = List.of("An", "Binh", "Chi");
        StringJoiner reviewerList = new StringJoiner(", ");
        reviewers.forEach(reviewerList::add);

        StringBuilder report = new StringBuilder();
        report.append("BAO CAO\n");
        report.append("So nguoi duyet: ").append(reviewers.size()).append('\n');
        report.append("Nguoi duyet: ").append(reviewerList).append('\n');

        System.out.print(report);
    }
}
```

Chạy:

```bash
javac MixedReportDemo.java
java MixedReportDemo
```

Stdout:

```text
BAO CAO
So nguoi duyet: 3
Nguoi duyet: An, Binh, Chi
```

Ở đây `StringJoiner` loại bỏ nhu cầu tự theo dõi dấu phẩy giữa các tên; `StringBuilder` quyết định thứ tự section, nhãn và newline. Với một danh sách đơn giản đã có sẵn, `String.join(", ", reviewers)` còn ngắn gọn hơn, nhưng khi cần xây dựng từng phần tử, tiền tố/hậu tố hoặc giá trị đặc biệt cho danh sách trống, `StringJoiner` thường tiện hơn.

## 6. Phần tử `null`, dữ liệu rỗng và thứ tự đầu vào

### Giá trị `null` không tự động bị bỏ qua

`StringBuilder.append((String) null)` thêm văn bản `"null"`. `StringJoiner.add(null)` cũng thêm biểu diễn `"null"`. `String.join` với phần tử `null` có thể đưa chữ `"null"` vào kết quả. Đây là hành vi của API, không nhất thiết là cách ứng dụng muốn mô tả dữ liệu thiếu.

Thay vì mặc định in `null` trong báo cáo, hãy **đặt chính sách hiển thị**:

```java
String rawName = null;
String safeName = rawName == null ? "(khong co ten)" : rawName;

StringJoiner names = new StringJoiner(", ");
names.add(safeName);
```

Đây là đoạn minh họa trong một phương thức. Trong báo cáo nghiệp vụ, một giá trị thiếu có thể cần hiển thị `"N/A"`, để trống có chủ đích, hoặc gây lỗi validation; các lựa chọn này có ý nghĩa khác nhau.

Một phần tử rỗng `""` vẫn là **một phần tử đã được thêm**. Chẳng hạn joiner có phần tử rỗng và một phần tử `"B"` có thể trả về `", B"`. `setEmptyValue` không xử lý trường hợp đã `add("")`. Nếu muốn loại phần tử trống, lọc trước khi thêm:

```java
for (String name : namesFromInput) {
    if (name != null && !name.isBlank()) {
        joiner.add(name);
    }
}
```

Các biến `namesFromInput` và `joiner` được giả định đã khai báo. `isBlank()` khác `isEmpty()`: chuỗi toàn khoảng trắng Unicode cũng có thể được coi là blank. Không lọc khi vị trí trường rỗng có ý nghĩa, ví dụ trường thứ ba của một dòng CSV.

### Giữ thứ tự có ý nghĩa

`StringJoiner` tôn trọng thứ tự `add`. Nhưng nếu dữ liệu nguồn đến từ một collection không bảo đảm thứ tự như `HashSet` hoặc `HashMap`, báo cáo có thể có thứ tự khác nhau giữa các lần chạy. Khi yêu cầu báo cáo tái lập được, hãy sắp xếp hoặc dùng cấu trúc có thứ tự trước khi ghép; đừng kỳ vọng joiner tự sắp xếp.

Các giá trị dữ liệu được đưa trực tiếp vào báo cáo có thể chứa newline, tab hoặc dấu phân cách, khiến bố cục thay đổi. Nếu báo cáo được dùng để kiểm toán hoặc truyền dữ liệu có cấu trúc, hãy có chính sách escaping và validation rõ ràng.

## 7. Dấu phân cách không phải là escaping: ví dụ CSV

`StringJoiner(",")` chỉ thêm dấu phẩy giữa các phần tử. Nếu một trường chứa dấu phẩy, dấu ngoặc kép hoặc newline, việc `add(value)` nguyên xi **không tạo ra CSV hợp lệ**. Với CSV, mỗi ô phải được escaping theo quy tắc của định dạng: trường có dấu phẩy, ngoặc kép, LF hoặc CR nên được đặt trong ngoặc kép; ngoặc kép trong giá trị được nhân đôi.

Lưu chương trình **`CsvJoinerDemo.java`**:

```java
import java.util.StringJoiner;

public class CsvJoinerDemo {
    public static void main(String[] args) {
        String[] columns = {
                "An",
                "Ghi chu, quan trong",
                "Noi \"A\""
        };

        StringJoiner row = new StringJoiner(",");
        for (String value : columns) {
            row.add(csvCell(value));
        }

        System.out.println(row);
    }

    private static String csvCell(String value) {
        if (value == null) {
            return "";
        }

        boolean quote = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;

        if (!quote) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
```

Chạy:

```bash
javac CsvJoinerDemo.java
java CsvJoinerDemo
```

Stdout:

```text
An,"Ghi chu, quan trong","Noi ""A"""
```

Trong ví dụ này, **escaping xảy ra trong `csvCell`**; `StringJoiner` chỉ nối những trường đã xử lý. Đây là phiên bản minh họa các quy tắc CSV phổ biến, không thay thế một thư viện CSV đã được kiểm chứng khi cần xử lý toàn bộ quy định định dạng, charset/BOM, các kiểu newline, quoting policy hoặc dữ liệu từ nguồn không tin cậy.

Tương tự, đừng tự xây dựng JSON bằng `StringBuilder.append("{\"name\":\"").append(value)...` khi có dữ liệu tùy ý; cần JSON serializer để xử lý dấu nháy, dấu gạch chéo và các ký tự điều khiển đúng quy tắc. Một bản báo cáo dễ đọc cho người xem và một payload dữ liệu có cấu trúc cần hợp đồng khác nhau.

## 8. Các lựa chọn định dạng ảnh hưởng kết quả báo cáo

### Quy ước newline và charset

Nếu kết quả chỉ in console, `System.out.print(report)` ghi nguyên chuỗi; `System.out.println(report)` thêm một newline ở cuối. Nếu báo cáo vốn đã kết thúc bằng newline, `println` có thể tạo một dòng trống không mong muốn. `%n` của `Formatter` dùng newline nền tảng, trong khi `"\n"` luôn là LF.

Khi ghi ra file text, nên chọn UTF-8 rõ ràng:

```java
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

Path path = Path.of("report.txt");
Files.writeString(path, report.toString(), StandardCharsets.UTF_8);
```

Đoạn trên là mã minh họa trong một phương thức có xử lý hoặc khai báo `IOException`. `report` được giả định đã có. Đầu nhận nên dùng cùng charset: `Files.readString(path, StandardCharsets.UTF_8)`. Ghi file không làm thay đổi nội dung `StringBuilder` ban đầu.

### Locale và số liệu

`String.format(Locale.ROOT, "%.2f", amount)` dùng kiểu hiển thị ổn định hơn cho ví dụ. Nếu báo cáo cho người dùng Việt Nam cần dấu nhóm, ký hiệu tiền và thông lệ địa phương, chọn `NumberFormat` hoặc `DecimalFormat` với locale thích hợp, đồng thời xác định rõ quy tắc làm tròn. Với báo cáo máy đọc, tốt nhất có giao thức tách biệt phần dữ liệu và phần hiển thị.

Với dữ liệu dạng bảng, `%-8s` chỉ quy định **chiều rộng tối thiểu của trường định dạng**, không phải độ rộng hiển thị chắc chắn của tiếng Việt, emoji hay tổ hợp ký tự. Nếu tên dài hơn cột, nó không bị cắt theo mặc định và có thể phá bố cục. Đừng coi bảng text căn cột là dữ liệu truyền không mơ hồ; nếu cần trao đổi giữa hệ thống, dùng CSV/JSON hoặc định dạng chuyên dụng.

### Giới hạn độ dài và dung lượng

Khi số dòng lớn, `StringBuilder` có thể phải mở rộng nhiều lần. Có thể ước lượng ban đầu theo số dòng và kích thước trung bình: `new StringBuilder(estimatedUnits)`, với `estimatedUnits` là số **đơn vị UTF-16**, không phải số byte UTF-8. Nếu không có ước lượng đáng tin, bắt đầu mặc định và chỉ tối ưu khi profiler/benchmark cho thấy cần thiết.

Không tạo toàn bộ báo cáo khổng lồ trong RAM nếu kích thước không được giới hạn. Với dữ liệu rất lớn, viết tuần tự ra `BufferedWriter` hoặc `OutputStream` sẽ phù hợp hơn. `StringBuilder` và `StringJoiner` phục vụ xây dựng một kết quả trong bộ nhớ; lựa chọn xuất trực tiếp là một bài toán tài nguyên khác.

## 9. Các lỗi thường gặp và cách sửa

| Lỗi | Hậu quả | Cách sửa |
| --- | --- | --- |
| Tự `append(", ")` sau mọi phần tử | Dư dấu phẩy ở cuối | Dùng `StringJoiner` hoặc kiểm tra `i > 0` |
| Trộn `println`, `%n` và `\n` tùy tiện | Byte newline hoặc dòng trống không nhất quán | Quy định newline đầu ra một lần |
| Dùng `double` cho phép tính tiền quan trọng | Sai số nhị phân có thể tích lũy | Dùng `BigDecimal` và quy tắc làm tròn cụ thể |
| Tin rằng `StringJoiner` tự loại `null`/`""` | Xuất chữ `null` hoặc phần tử rỗng | Validate/lọc/ánh xạ theo chính sách |
| Ghép trường CSV chỉ bằng dấu phẩy | Dấu phẩy/ngoặc kép trong dữ liệu phá cấu trúc | Escape trường hoặc dùng thư viện CSV |
| Dùng dấu cách và `%8s` cho bảng Unicode tùy ý | Terminal có thể hiển thị lệch cột | Dùng chính sách đo/giới hạn chuỗi hoặc renderer bảng |
| Gọi `toString()` trong mỗi vòng lặp để nối tiếp | Tạo nhiều chuỗi trung gian | Giữ builder đến khi kết thúc |
| Không cố định thứ tự dữ liệu nguồn | Báo cáo thay đổi thứ tự ngoài ý muốn | Sắp xếp hoặc dùng collection có thứ tự |
| Dùng một builder được nhiều thread sửa đồng thời | Race condition, kết quả hỏng | Sở hữu riêng từng luồng/tác vụ hoặc đồng bộ thích hợp |

Cả `StringBuilder` lẫn `StringJoiner` đều là đối tượng mutable và **không thread-safe**. Với tác vụ tạo báo cáo độc lập, thường tốt nhất là tạo chúng như biến cục bộ trong phương thức. `StringBuffer` có các phương thức được đồng bộ hóa nhưng không tự giải quyết mọi logic truy cập đa bước; không cần chuyển sang nó khi không có nhu cầu chia sẻ.

## 10. Bài tập mở rộng

**Bài 1 — Chọn công cụ.** Một báo cáo chỉ cần dòng `"Tags: java, core, string"` từ danh sách tên tag động. Nên dùng công cụ nào? Nếu chưa có tag, cần hiển thị `"Tags: (trong)"` thì xử lý ra sao?

**Bài 2 — Báo cáo tổng hợp.** Bổ sung vào `SalesReportDemo` một cột `quantity` tổng cộng, rồi thêm dòng `"TONG SO LUONG: 5"` sau dòng tổng tiền. Giữ phép cộng số nguyên và tiền tệ riêng nhau.

**Bài 3 — Không mất vị trí cột.** Cho một mảng dữ liệu chứa `"A"`, `""` và `"C"`. Nếu đây là ba cột CSV, có nên lọc phần tử rỗng trước khi ghép không? Tại sao?

**Bài 4 — Thứ tự ổn định.** Danh sách tên lấy từ `HashSet`. Làm sao tạo chuỗi tag theo thứ tự từ điển để hai lần chạy cùng dữ liệu cho cùng kết quả?

**Bài 5 — Dự đoán chính xác đầu ra.** Đọc code:

```java
StringJoiner j = new StringJoiner(" | ", "<", ">");
System.out.println(j);
j.setEmptyValue("(empty)");
System.out.println(j);
j.add("x").add("y");
System.out.println(j);
```

Đáp án theo thứ tự:

```text
<>
(empty)
<x | y>
```

**Gợi ý:** (1) `StringJoiner(", ", "Tags: ", "")`; muốn hiển thị riêng trạng thái rỗng, gọi `setEmptyValue("Tags: (trong)")`. (2) Duy trì biến `int quantityTotal` và cập nhật trong vòng lặp. (3) Không, vì trường rỗng có vị trí hợp lệ và phải được giữ. (4) Sao chép sang `List` rồi `sort` hoặc dùng nguồn đã có thứ tự; xác định comparator phù hợp. (5) `setEmptyValue` chỉ tác động khi chưa thêm phần tử.

### Tài liệu tham khảo

- [`StringBuilder` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StringBuilder.html)
- [`StringJoiner` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/StringJoiner.html)
- [`Formatter` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Formatter.html)
- [`BigDecimal` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-practice-004">Tách dữ liệu có phần tử rỗng và giữ đúng dấu phân cách</a>

<details>
<summary>Xem chi tiết</summary>

# Tách dữ liệu có phần tử rỗng và giữ đúng dấu phân cách trong Java

## Mục tiêu và bài toán

Khi đọc một dòng dữ liệu có cấu trúc, **phần tử rỗng vẫn là một phần tử hợp lệ**. Ví dụ chuỗi `,A,,B,` phân cách bằng dấu phẩy có **5 trường**, theo thứ tự: `""`, `"A"`, `""`, `"B"`, `""`. Dấu phẩy đầu tạo trường đầu rỗng; hai dấu phẩy kề nhau tạo trường giữa rỗng; dấu phẩy cuối tạo trường cuối rỗng. Bỏ bất kỳ trường nào có thể làm lệch vị trí cột.

Có hai nhu cầu cần phân biệt:

1. **Giữ các trường rỗng** trong mảng kết quả, trong khi dấu phân cách có thể bỏ đi. Dùng `String.split(regex, -1)`.
2. **Giữ nguyên cả dấu phân cách đã xuất hiện** (chẳng hạn chuỗi có cả `,` và `;`, hoặc số lượng/ký tự phân cách mang ý nghĩa). Dùng `Matcher` để lấy riêng các trường và các chuỗi phân cách, hoặc `splitWithDelimiters` trên Java 21+.

Không nên mặc định rằng `split` vừa giữ trường cuối rỗng, vừa trả về các dấu phân cách. Đó là hai thuộc tính khác nhau.

## 1. Hiểu chính xác `String.split` và tham số `limit`

`String.split(String regex)` tương đương `split(regex, 0)`: các trường rỗng **ở cuối** bị loại khỏi kết quả. Trường rỗng ở đầu do một kết quả khớp có độ dài dương và trường rỗng ở giữa vẫn được giữ.

| Lệnh | Kết quả (viết rõ chuỗi rỗng) | Ý nghĩa |
| --- | --- | --- |
| `",A,,B,".split(",")` | `["", "A", "", "B"]` | Mất trường rỗng ở cuối |
| `",A,,B,".split(",", 0)` | `["", "A", "", "B"]` | Giống lời gọi một tham số |
| `",A,,B,".split(",", -1)` | `["", "A", "", "B", ""]` | Giữ cả trường cuối rỗng |
| `"A,,B,".split(",", 2)` | `["A", ",B,"]` | Tách tối đa 1 lần; phần còn lại giữ nguyên |
| `",,".split(",", -1)` | `["", "", ""]` | Hai dấu phân cách tạo ba trường rỗng |
| `"abc".split(",", -1)` | `["abc"]` | Không có dấu phân cách |
| `"".split(",", -1)` | `[""]` | Chuỗi đầu vào rỗng, kết quả có một phần tử |

Ba chế độ `limit`:

- `limit > 0`: tối đa `limit - 1` lần tách; phần tử cuối chứa toàn bộ phần chưa tách. Dùng khi cố tình muốn giữ phần còn lại, ví dụ phân tách `key=value=more` bằng `split("=", 2)`.
- `limit == 0`: tách hết và **loại các trường rỗng liên tiếp ở cuối**.
- `limit < 0`: tách hết và **giữ mọi trường rỗng**, kể cả ở cuối. `-1` là giá trị quen dùng; `-2` có cùng hành vi về việc giữ trường rỗng.

**Quy tắc thực hành:** dữ liệu dạng bản ghi mà số thứ tự trường cần chính xác thường nên dùng `split(delimiterRegex, -1)`. Đừng `trim()` hoặc `filter(s -> !s.isEmpty())` sau khi tách nếu khoảng trắng/trường rỗng có ý nghĩa.

## 2. Dấu phân cách được hiểu là regex, không mặc định là ký tự literal

Đối số thứ nhất của `split` là **biểu thức chính quy**. Vì vậy:

- Dấu phẩy `,` và dấu chấm phẩy `;` không có ý nghĩa regex đặc biệt trong ngữ cảnh đơn giản này.
- Dấu gạch đứng `|`, dấu chấm `.`, dấu sao `*`, dấu ngoặc vuông... có thể mang ý nghĩa regex. Viết `text.split("|", -1)` để tách theo dấu `|` là sai.
- Với dấu phân cách **cố định/literal**, đặc biệt lấy từ cấu hình hoặc người dùng, dùng `Pattern.quote(delimiter)`. Ví dụ `"a|b||".split(Pattern.quote("|"), -1)` trả về `["a", "b", "", ""]`.
- Với dấu phân cách **là quy tắc regex có chủ đích**, viết regex rõ ràng: `"a,b;c".split("[,;]", -1)` tách theo từng dấu phẩy hoặc chấm phẩy. Nhưng mảng kết quả không cho biết tại vị trí nào đã dùng dấu nào.
- Khi muốn gộp chuỗi dấu phân cách liên tiếp thành **một** dấu tách, regex `[,;]+` sẽ bỏ cấu trúc trường rỗng giữa các dấu. Chỉ dùng nếu nghiệp vụ thật sự cho phép.

Regex có thể khớp chuỗi có độ dài bằng 0 (zero-width match), dẫn đến cách phân tách không trực quan. Với bài toán tách bản ghi có dấu phân cách hữu hình, nên chọn pattern luôn tiêu thụ ít nhất một ký tự. Cũng không thể lấy đúng toàn bộ dấu phân cách ban đầu chỉ từ `String[]` đã tách bằng regex cho nhiều ký tự phân cách khác nhau.

## 3. Ví dụ Java 17+: giữ trường rỗng và giữ chính xác dấu phân cách

Chương trình dưới đây minh họa cả hai hướng: `split(..., -1)` cho dấu phân cách cố định, và `Matcher.find()` khi cần giữ từng dấu đã khớp. Lớp `Tokenized` giữ hai danh sách theo thứ tự; với `n` dấu phân cách sẽ có `n + 1` trường, kể cả trường rỗng.

Tạo tệp `SplitPreservingDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SplitPreservingDemo {
    public static void main(String[] args) {
        String line = ",A,,B,";
        String[] defaultFields = line.split(",");
        String[] allFields = line.split(",", -1);

        System.out.println("Default: " + quoted(defaultFields));
        System.out.println("Preserved: " + quoted(allFields));
        System.out.println("Field count: " + allFields.length);
        System.out.println("Same text: " + String.join(",", allFields).equals(line));

        String pipeLine = "red|blue||";
        String[] pipeFields = pipeLine.split(Pattern.quote("|"), -1);
        System.out.println("Pipe: " + quoted(pipeFields));

        String mixed = "A,,B;C;";
        Tokenized tokens = tokenize(mixed, Pattern.compile("[,;]"));
        System.out.println("Fields: " + quoted(tokens.fields.toArray(new String[0])));
        System.out.println("Separators: " + quoted(tokens.separators.toArray(new String[0])));
        System.out.println("Mixed same text: " + rebuild(tokens).equals(mixed));
    }

    // Phục vụ minh họa: làm rõ vị trí các chuỗi rỗng trong output.
    private static String quoted(String[] fields) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append('"').append(fields[i]).append('"');
        }
        return out.append(']').toString();
    }

    private static Tokenized tokenize(String text, Pattern separatorPattern) {
        Matcher matcher = separatorPattern.matcher(text);
        List<String> fields = new ArrayList<>();
        List<String> separators = new ArrayList<>();
        int from = 0;

        while (matcher.find()) {
            if (matcher.start() == matcher.end()) {
                throw new IllegalArgumentException(
                    "Separator pattern must consume at least one character"
                );
            }
            fields.add(text.substring(from, matcher.start()));
            separators.add(matcher.group());
            from = matcher.end();
        }
        fields.add(text.substring(from));
        return new Tokenized(fields, separators);
    }

    private static String rebuild(Tokenized tokens) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < tokens.fields.size(); i++) {
            out.append(tokens.fields.get(i));
            if (i < tokens.separators.size()) {
                out.append(tokens.separators.get(i));
            }
        }
        return out.toString();
    }

    private static class Tokenized {
        final List<String> fields;
        final List<String> separators;

        Tokenized(List<String> fields, List<String> separators) {
            this.fields = fields;
            this.separators = separators;
        }
    }
}
```

Biên dịch và chạy:

```bash
javac SplitPreservingDemo.java
java SplitPreservingDemo
```

Kết quả chuẩn:

```text
Default: ["", "A", "", "B"]
Preserved: ["", "A", "", "B", ""]
Field count: 5
Same text: true
Pipe: ["red", "blue", "", ""]
Fields: ["A", "", "B", "C", ""]
Separators: [",", ",", ";", ";"]
Mixed same text: true
```

Diễn giải:

- `split(",", -1)` trả về đúng 5 trường; ghép lại bằng **chính dấu phẩy cố định** tái tạo chuỗi đầu vào.
- `Pattern.quote("|")` bảo đảm `|` được hiểu là một dấu phân cách literal; hai dấu `|` cuối làm phát sinh hai phần tử rỗng ở cuối (sau `blue`).
- Trong `"A,,B;C;"`, trường thứ hai và trường cuối rỗng. Danh sách `separators` giữ nguyên từng dấu `,`/`;`, nên `rebuild` có thể khôi phục đúng chuỗi gốc.
- Với pattern tiêu thụ ký tự, `matcher.start()` là chỉ số đầu dấu phân cách, `matcher.end()` là vị trí ngay sau nó; `substring(from, matcher.start())` nhận chính xác trường trước dấu tách, kể cả chuỗi rỗng. `matcher.group()` chứa **chuỗi khớp thực tế**, hữu ích cả khi dấu phân cách dài hơn một ký tự.
- Hàm `quoted` chỉ nhằm hiển thị minh bạch các phần tử trong ví dụ; nếu dữ liệu có dấu nháy hoặc ký tự điều khiển, cần cách escape khi hiển thị phù hợp.

### Vì sao cần giữ danh sách dấu phân cách?

Ví dụ `a,b;c` dùng regex `[,;]` và tách được `["a", "b", "c"]`, nhưng nếu ghép lại bằng dấu phẩy sẽ thành `a,b,c`, mất dấu chấm phẩy gốc. `String.join` chỉ bảo toàn bản gốc khi **mọi dấu phân cách thực sự là cùng một chuỗi cố định** và các trường đã được giữ đầy đủ, không có quy tắc escape/quote phức tạp.

Trong đoạn `Matcher` ở trên, `fields.size() == separators.size() + 1` là bất biến đáng kiểm tra. Không chuẩn hóa khoảng trắng, không đổi dấu và không bỏ trường rỗng nếu mục tiêu là lưu/tái tạo dữ liệu nguyên trạng.

## 4. Java 21+: `String.splitWithDelimiters`

Java 21 bổ sung `String.splitWithDelimiters(String regex, int limit)` và `Pattern.splitWithDelimiters(CharSequence input, int limit)`. Kết quả **xen kẽ trường, dấu phân cách, trường, dấu phân cách...**, thay vì chỉ trả các trường.

Ví dụ Java 21 (đoạn minh họa, đặt trong một phương thức):

```java
String input = "A,,B;";
String[] pieces = input.splitWithDelimiters("[,;]", -1);
// pieces: ["A", ",", "", ",", "B", ";", ""]
boolean same = String.join("", pieces).equals(input); // true
```

Dùng `limit = -1` để giữ cả phần rỗng cuối; `limit = 0` vẫn bỏ phần rỗng ở cuối theo quy tắc API. Với `limit > 0`, tối đa `limit - 1` lần tách và phần cuối giữ phần chưa tách; mảng có tối đa `2 * limit - 1` phần tử. API này **không có trong Java 17**; trên Java 17 dùng cách `Matcher` phía trên.

Nếu cần thao tác nghiệp vụ theo trường, cặp danh sách `fields`/`separators` thường dễ hiểu hơn mảng xen kẽ; nếu chỉ cần tái tạo nguyên văn, mảng xen kẽ của Java 21 rất tiện.

## 5. Các bẫy làm hỏng dữ liệu

1. **Bỏ `-1`**: trường cuối rỗng biến mất, khiến số cột không còn đúng. Hãy thử `"id,name,"`.
2. **Dùng regex sai**: `split(".")` không có nghĩa tách theo dấu chấm; hãy dùng `split(Pattern.quote("."), -1)` hoặc regex escape đúng.
3. **Dùng `[,;]+` thay `[,;]`**: các dấu liền nhau thành một lần tách, làm mất trường rỗng ở giữa.
4. **Xóa trường rỗng sau tách**: `filter(s -> !s.isBlank())` cũng loại các trường chỉ chứa khoảng trắng; làm thay đổi cả dữ liệu lẫn vị trí cột.
5. **Tự động `trim()`**: `" A "` và `"A"` không giống nhau nếu giao thức xem khoảng trắng là dữ liệu.
6. **Ghép bằng một dấu cố định dù nguồn dùng nhiều dấu**: không thể bảo đảm round-trip khi nguồn trộn `,` và `;`.
7. **Nhầm dữ liệu phân cách đơn giản với CSV hoàn chỉnh**: `A,"B,C",D` có dấu phẩy **nằm trong trường được quote**; `split(",", -1)` vẫn cắt sai. CSV thật có escaping, quote đôi và thậm chí xuống dòng trong trường. Hãy dùng parser tuân theo định dạng (chẳng hạn thư viện Apache Commons CSV), đồng thời chỉ định dialect/quote/escape rõ ràng.
8. **Tin vào cách in mặc định của mảng**: `Arrays.toString` có thể hiển thị phần tử rỗng khó phân biệt; khi kiểm tra hãy in `length` và bọc từng giá trị trong dấu nháy.

`String.split` cũng có quy tắc riêng với regex khớp zero-width tại đầu chuỗi: nó **không** tạo trường rỗng đầu giống một dấu phân cách có độ dài dương. Vì thế không suy diễn kết quả của zero-width regex từ các ví dụ về dấu phẩy.

## 6. Checklist và bài tự kiểm tra

Với mỗi dòng dữ liệu phân cách, hãy trả lời bốn câu:

1. Dấu phân cách là **chuỗi cố định** hay một **regex** được chủ ý thiết kế?
2. Trường rỗng ở **đầu, giữa, cuối** có phải giữ lại hay không?
3. Có cần bảo toàn **ký tự và độ dài của dấu phân cách thực tế** để khôi phục chuỗi ban đầu không?
4. Đầu vào có **quote/escape/nested syntax** khiến tách regex đơn giản không đúng đặc tả không?

Tự kiểm tra: với `"|a||"` và dấu literal `|`, `split(Pattern.quote("|"), -1)` phải cho `["", "a", "", ""]`, số trường là 4. Với `"a|b;c|"`, nếu yêu cầu giữ nguyên dấu đã gặp, phải ghi nhớ lần lượt `"|", ";", "|"`; chỉ lưu các trường là chưa đủ.

**Kết luận kỹ thuật:** dùng `split(Pattern.quote(delimiter), -1)` khi cần giữ tất cả trường rỗng với một delimiter cố định. Khi cần giữ chính xác từng dấu phân cách hỗn hợp, dùng `Matcher` (Java 17+) hoặc `splitWithDelimiters(regex, -1)` (Java 21+), rồi xác minh bằng phép tái tạo chuỗi gốc.

## Tài liệu API tham khảo

- Java 17 `String.split`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html
- Java 17 `Pattern.quote` và `Matcher`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html
- Java 21 `Pattern.splitWithDelimiters`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/regex/Pattern.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-practice-005">Đếm ký tự theo char, code point và cụm hiển thị</a>

<details>
<summary>Xem chi tiết</summary>

# Đếm ký tự theo char, code point và cụm hiển thị trong Java

Trong Java, câu hỏi **“chuỗi có bao nhiêu ký tự?”** không có một đáp án duy nhất nếu chưa xác định cách đếm. Một chữ Việt có dấu có thể được viết bằng một code point hoặc nhiều code point kết hợp; emoji có thể nằm ngoài vùng Unicode BMP và cần hai **UTF-16 code unit**; một hình emoji gồm nhiều thành phần có thể được nối lại thành **một cụm hiển thị**.

Bài thực hành này hướng dẫn đếm, so sánh và cắt chuỗi theo ba mức:

1. **char / UTF-16 code unit**: đơn vị mà các API như **String.length()** và **charAt()** làm việc.
2. **Unicode code point**: điểm mã Unicode, đếm bằng **codePointCount()** hoặc **codePoints()**.
3. **Cụm hiển thị (extended grapheme cluster)**: nhóm code point thường được coi là một ký tự khi người dùng di chuyển con trỏ hoặc lựa chọn văn bản; có thể phân đoạn bằng regex **\X** trong Java.

## 1. Nền tảng: char không đồng nghĩa “một ký tự nhìn thấy”

### UTF-16 code unit và String.length()

Trong Java, mỗi **char** có kích thước 16 bit và biểu diễn **một UTF-16 code unit**. **String.length()** trả về số code unit của chuỗi, chứ không phải số byte UTF-8 và cũng không luôn bằng số ký tự hiển thị.

Ví dụ:

~~~java
System.out.println("ABC".length());           // 3
System.out.println("Vi\u1EC7t".length());      // 4
System.out.println("\uD83D\uDE42".length());  // 2
~~~

Đoạn mã này là snippet để đặt trong phương thức, trong đó **Vi\u1EC7t** tương đương **Việt** và **\uD83D\uDE42** tương đương **🙂**.

Vì sao **🙂** có **length() = 2**? Unicode code point U+1F642 nằm ngoài **Basic Multilingual Plane (BMP)**; UTF-16 biểu diễn nó bằng **hai char liên tiếp**: *high surrogate* và *low surrogate*. Gọi **charAt(0)** chỉ lấy phần đầu của cặp, không phải toàn bộ emoji.

Trong các JVM hiện đại, String có thể dùng **Compact Strings** để lưu byte[] hiệu quả hơn. Điều này **không làm thay đổi** ý nghĩa của **String.length()**: API vẫn trả về số UTF-16 code unit.

### Unicode code point

**Code point** là giá trị được xác định bởi Unicode, ví dụ:

- **A**: U+0041.
- **ệ**: U+1EC7.
- **🙂**: U+1F642.
- Ký tự nối **ZWJ**: U+200D.

Một code point thuộc BMP thường ứng với một UTF-16 code unit. Một code point ngoài BMP dùng **hai** UTF-16 code unit. Do đó **codePointCount** có thể nhỏ hơn **length**.

**String.codePointCount(beginIndex, endIndex)** nhận **chỉ số code unit UTF-16**, không nhận chỉ số code point; giá trị của nó là số code point trong phạm vi UTF-16 đã chọn:

~~~java
String smile = "\uD83D\uDE42";
System.out.println(smile.length());                             // 2
System.out.println(smile.codePointCount(0, smile.length()));     // 1
System.out.println(smile.codePoints().count());                 // 1
~~~

**Lưu ý về dữ liệu không hợp lệ:** với một surrogate đơn lẻ không ghép thành cặp, Java API có thể đếm nó như một code point theo quy tắc của **codePointCount** dù nó không phải Unicode scalar value hợp lệ. Đừng mặc định mọi String bất kỳ đều chứa các cặp surrogate hợp lệ.

### Cụm hiển thị / grapheme cluster

**Extended grapheme cluster** là một đoạn gồm một hoặc nhiều code point theo quy tắc phân đoạn Unicode, thường gần với đơn vị “một ký tự” trong giao diện người dùng. Ví dụ:

- **e + dấu sắc kết hợp**: hai code point nhưng thường hiển thị thành **é** như một cụm.
- **👩‍💻**: **👩** + **ZWJ** + **💻**; ba code point nhưng thường là **một cụm emoji**.
- **🇻🇳**: hai *regional indicator* code point tạo thành một cụm cờ.
- **👍🏽**: emoji bàn tay và modifier màu da tạo thành một cụm.

**Một grapheme cluster không bắt buộc là đúng một glyph vật lý trên màn hình.** Việc hiển thị còn phụ thuộc font, shaping engine, hệ điều hành và phiên bản Unicode/emoji. Đếm cụm grapheme là một quy tắc phân đoạn văn bản, không phải phép đếm ảnh glyph mà màn hình thực sự vẽ.

## 2. Các API Java phù hợp với từng cách đếm

| Mục tiêu | API / cách làm | Đơn vị |
| --- | --- | --- |
| Đếm UTF-16 code unit | **s.length()** | char/code unit |
| Đếm code point | **s.codePointCount(0, s.length())** | code point |
| Duyệt code point | **s.codePoints()** | stream giá trị int |
| Lấy code point tại vị trí UTF-16 | **s.codePointAt(index)** | code point |
| Di chuyển n code point | **s.offsetByCodePoints(index, n)** | kết quả là chỉ số UTF-16 |
| Đếm grapheme | **Pattern.compile("\\X").matcher(s)** và đếm các lần match | extended grapheme cluster |
| Duyệt grapheme | Mỗi lần **Matcher.find()**, dùng **group()**, **start()**, **end()** | đoạn String/cặp chỉ số UTF-16 |

Trong regex của Java, **\X** biểu diễn một **Unicode extended grapheme cluster**. Vì regex nằm trong Java String literal, phải viết **"\\X"**, không phải **"\X"**; cách sau không phải escape hợp lệ của Java String.

**Matcher.results().count()** (Java 9+) hoặc vòng lặp **while (matcher.find())** đều có thể đếm số lần khớp **\X**. **Pattern** có thể dùng lại cho nhiều chuỗi; tạo một **Matcher** mới cho mỗi chuỗi cần xử lý. Không dùng chung một Matcher có trạng thái để xử lý đồng thời nhiều luồng.

## 3. Bài thực hành Java 17+ hoàn chỉnh

Lưu thành **CharacterCountingDemo.java**:

~~~java
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CharacterCountingDemo {
    private static final Pattern GRAPHEME = Pattern.compile("\\X");

    private static void report(String label, String text) {
        int codeUnits = text.length();
        int codePoints = text.codePointCount(0, codeUnits);
        long clusters = GRAPHEME.matcher(text).results().count();

        System.out.println(label + ": units=" + codeUnits
                + ", points=" + codePoints
                + ", clusters=" + clusters);
    }

    public static void main(String[] args) {
        String precomposed = "Vi\u1EC7t";
        String decomposed = "Vie\u0323\u0302t";
        String smile = "\uD83D\uDE42";
        String zwj = "\uD83D\uDC69\u200D\uD83D\uDCBB";

        report("ASCII", "ASCII");
        report("Vietnamese NFC", precomposed);
        report("Vietnamese NFD", decomposed);
        report("Emoji", smile);
        report("Emoji ZWJ", zwj);
        report("Flag", "\uD83C\uDDFB\uD83C\uDDF3");
        report("Skin tone", "\uD83D\uDC4D\uD83C\uDFFD");
        report("CRLF", "A\r\nB");
        report("Empty", "");

        System.out.println("equal before NFC: "
                + precomposed.equals(decomposed));
        System.out.println("equal after NFC: "
                + precomposed.equals(
                        Normalizer.normalize(
                                decomposed, Normalizer.Form.NFC)));

        System.out.println("smile high surrogate: "
                + Character.isHighSurrogate(smile.charAt(0)));
        System.out.println("smile code point: U+"
                + Integer.toHexString(smile.codePointAt(0))
                        .toUpperCase(Locale.ROOT));

        int onePointEnd = zwj.offsetByCodePoints(0, 1);
        System.out.println("ZWJ first code point units: "
                + zwj.substring(0, onePointEnd).length());

        Matcher matcher = GRAPHEME.matcher(zwj);
        if (matcher.find()) {
            System.out.println("ZWJ first cluster units: "
                    + matcher.group().length());
        }
    }
}
~~~

Chương trình dùng **Unicode escape** để biểu diễn chính xác dữ liệu Việt và emoji, tránh phụ thuộc font/encoding của trình soạn thảo hoặc terminal. Tên các mẫu và thông báo đều dùng ASCII, nên kết quả console dễ đối chiếu.

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 CharacterCountingDemo.java
java CharacterCountingDemo
~~~

Stdout mẫu đã kiểm chứng trên JDK hỗ trợ quy tắc grapheme cho các trường hợp đã chọn:

~~~text
ASCII: units=5, points=5, clusters=5
Vietnamese NFC: units=4, points=4, clusters=4
Vietnamese NFD: units=6, points=6, clusters=4
Emoji: units=2, points=1, clusters=1
Emoji ZWJ: units=5, points=3, clusters=1
Flag: units=4, points=2, clusters=1
Skin tone: units=4, points=2, clusters=1
CRLF: units=4, points=4, clusters=3
Empty: units=0, points=0, clusters=0
equal before NFC: false
equal after NFC: true
smile high surrogate: true
smile code point: U+1F642
ZWJ first code point units: 2
ZWJ first cluster units: 5
~~~

Ý nghĩa ba cột:

- **units** = số UTF-16 code unit, cũng là **String.length()**.
- **points** = số code point theo Java API.
- **clusters** = số lần regex **\X** khớp, theo triển khai quy tắc Unicode của JDK đang chạy.

Kết quả phụ thuộc bảng Unicode của JDK với các cụm Unicode/emoji mới; các ví dụ phổ biến trên đã được kiểm chứng trong môi trường Java kiểm tra. Khi áp dụng cho bộ emoji mới hoặc bản JDK khác, hãy chạy lại để xác nhận sự tương thích.

## 4. Phân tích từng trường hợp trong bảng

### 4.1. ASCII: ba đại lượng bằng nhau

**"ASCII"** gồm năm ký tự A, S, C, I, I. Mỗi ký tự chiếm một UTF-16 code unit, là một code point và tự tạo một grapheme cluster.

Vì vậy: **units=5, points=5, clusters=5**.

Trường hợp này thường làm lập trình viên tưởng rằng **length() luôn là số ký tự**, vì ở dữ liệu ASCII đơn giản ba kết quả trùng nhau. Kết luận ấy không còn đúng với Unicode tổng quát.

### 4.2. Tiếng Việt dạng dựng sẵn: đa số vẫn dùng một unit

**"Việt"** với chữ **ệ** dựng sẵn (U+1EC7) có bốn chữ và bốn code point, đều nằm trong BMP; mỗi code point dùng một UTF-16 unit. Trong phép phân đoạn đã chọn, mỗi chữ là một grapheme cluster.

Kết quả: **units=4, points=4, clusters=4**.

**Không suy ra** từ đây rằng mọi văn bản tiếng Việt luôn có ba đại lượng bằng nhau. Cùng một chữ mang dấu có thể được viết bằng nhiều code point tổ hợp, như ví dụ sau.

### 4.3. Tiếng Việt dạng phân rã: nhiều code point cho một chữ

Chuỗi **Việt** được khai báo bằng:

~~~java
String decomposed = "Vie\u0323\u0302t";
~~~

Nó có sáu code point: **V**, **i**, **e**, dấu chấm dưới, dấu mũ và **t**. Dấu chấm dưới và dấu mũ kết hợp với chữ e, nên bộ phân đoạn grapheme tạo ra bốn cụm.

- **units=6** vì cả sáu code point đều nằm trong BMP.
- **points=6** vì không có surrogate pair.
- **clusters=4** vì e cùng hai dấu tạo **một cụm**, không phải ba cụm riêng.

Trong cùng chương trình:

~~~java
String composed = "Vi\u1EC7t";
String decomposed = "Vie\u0323\u0302t";
System.out.println(composed.equals(decomposed)); // false
System.out.println(
        composed.equals(Normalizer.normalize(
                decomposed, Normalizer.Form.NFC))); // true
~~~

Đây là snippet để đặt trong phương thức và import **java.text.Normalizer**. Java String.equals so sánh nội dung mã hóa thực tế, nên trước chuẩn hóa hai chuỗi không bằng nhau; chuẩn hóa NFC chuyển dạng tổ hợp về dạng tương đương khi có dạng dựng sẵn thích hợp.

**Grapheme count bằng nhau không kéo theo equals bằng nhau.** Nếu yêu cầu nghiệp vụ coi các dạng chuẩn hóa Unicode tương đương là cùng dữ liệu, cần chuẩn hóa trước khi so sánh hoặc làm khóa collection.

### 4.4. Emoji đơn: hai char nhưng một code point

Với **🙂** (U+1F642):

- **units=2**: surrogate pair gồm hai UTF-16 unit.
- **points=1**: một code point.
- **clusters=1**: một grapheme cluster.

**smile.charAt(0)** trả về high surrogate; **smile.codePointAt(0)** trả về U+1F642. Với chuỗi hợp lệ và ở vị trí đúng, codePointAt kết hợp hai char của surrogate pair để cho code point hoàn chỉnh.

### 4.5. Emoji ZWJ: nhiều code point thành một cụm

Chuỗi **👩‍💻** gồm:

1. **👩**: U+1F469, hai UTF-16 unit.
2. **ZWJ**: U+200D, một UTF-16 unit.
3. **💻**: U+1F4BB, hai UTF-16 unit.

Tổng cộng **5 unit, 3 point, 1 cluster**.

Đây là ví dụ cho thấy **đếm theo code point** vẫn không đủ khi yêu cầu là “một ký tự người dùng nhìn thấy”. Nếu chỉ lấy code point đầu tiên, ta lấy **👩**, không lấy toàn cụm **👩‍💻**.

### 4.6. Cờ và emoji có màu da

**🇻🇳** gồm hai regional indicator code point ngoài BMP, nên **4 UTF-16 unit**, **2 code point**, thường là **1 grapheme cluster**.

**👍🏽** gồm emoji bàn tay và emoji modifier màu da, nên **4 unit**, **2 point** và **1 cluster** trong trường hợp này.

Không phải mọi emoji đều có cùng cách cấu thành. Có emoji một code point, emoji có variation selector, modifier, cờ, chuỗi ZWJ và chuỗi dài hơn. Không thể suy “một emoji = hai char” hoặc “một emoji = một code point”.

### 4.7. Xuống dòng CRLF và chuỗi rỗng

**"A\r\nB"** gồm A, CR, LF, B:

- **length() = 4**.
- **codePointCount() = 4**.
- **\X** nhóm CRLF thành **một grapheme cluster** theo quy tắc phân đoạn, nên tổng cộng **3** cụm: A, CRLF, B.

CRLF không phải một biểu tượng hiển thị, minh họa rằng **grapheme cluster là đơn vị phân đoạn văn bản**, không bắt buộc mỗi cụm tương ứng một hình glyph nhìn thấy.

Chuỗi rỗng có **0** unit, **0** code point và **0** grapheme cluster. Không nên gọi **charAt(0)** trên chuỗi rỗng.

## 5. Không cắt chuỗi giữa surrogate pair

Sai lầm điển hình khi xử lý emoji:

~~~java
String smile = "\uD83D\uDE42";
String broken = smile.substring(0, 1);
~~~

**broken** chỉ gồm high surrogate, là một mảnh không hoàn chỉnh của emoji. Khi mã hóa ra UTF-8, API có thể dùng replacement để biểu diễn mã không hợp lệ; không nên kỳ vọng có thể tạo lại emoji ban đầu.

Nếu yêu cầu là **lấy n code point**, dùng **offsetByCodePoints** để chuyển từ số code point sang chỉ số UTF-16:

~~~java
String smile = "\uD83D\uDE42";
int end = smile.offsetByCodePoints(0, 1);
String firstPoint = smile.substring(0, end);

// end == 2; firstPoint chứa toàn bộ 🙂.
~~~

**substring** vẫn nhận offset UTF-16; **offsetByCodePoints** chỉ giúp tính đúng vị trí kết thúc theo code point. Đoạn này tránh cắt surrogate pair hợp lệ, nhưng vẫn có thể cắt một grapheme có nhiều code point.

Ví dụ với **👩‍💻**, lấy một code point đầu tiên chỉ nhận emoji **👩** (2 UTF-16 unit), bỏ lại ZWJ và emoji 💻.

## 6. Cắt chuỗi theo grapheme cluster

Nếu yêu cầu là **lấy một cụm grapheme đầu tiên**, có thể dùng **Matcher.find()**:

~~~java
Pattern grapheme = Pattern.compile("\\X");
String value = "\uD83D\uDC69\u200D\uD83D\uDCBB";

Matcher matcher = grapheme.matcher(value);
if (matcher.find()) {
    String firstCluster = matcher.group();
    int begin = matcher.start();
    int end = matcher.end();

    // firstCluster là toàn bộ chuỗi 👩‍💻 trong trường hợp này.
    // begin == 0, end == 5 (offset UTF-16).
}
~~~

Đây là snippet, cần import **java.util.regex.Pattern**, **Matcher** và đặt trong phương thức.

**Matcher.start()**, **end()** cũng trả về **chỉ số UTF-16**, không phải số code point. Nhờ vậy có thể truyền các chỉ số đó trực tiếp cho **substring** để lấy toàn bộ cụm theo phép phân đoạn.

Muốn lấy tối đa **n cụm đầu**, dùng vòng lặp đếm các lần find, nhận **end()** của cụm thứ n rồi cắt ở vị trí đó. Với đầu vào rỗng, xử lý riêng để không giả định Matcher tìm được cụm đầu.

Một hàm tiện ích (snipppet cần đặt trong lớp):

~~~java
static String takeClusters(String text, int limit) {
    if (limit < 0) {
        throw new IllegalArgumentException("limit must be >= 0");
    }
    if (limit == 0 || text.isEmpty()) {
        return "";
    }

    Matcher matcher = Pattern.compile("\\X").matcher(text);
    int end = 0;
    int taken = 0;

    while (taken < limit && matcher.find()) {
        end = matcher.end();
        taken++;
    }
    return text.substring(0, end);
}
~~~

Nếu có ít hơn **limit** cụm, vòng lặp chạy đến hết và trả về cả chuỗi. Với ứng dụng gọi thường xuyên, nên tái sử dụng **Pattern** thay vì compile mỗi lần.

**Giới hạn quan trọng:** cụm grapheme theo quy tắc Unicode/phiên bản JDK không đồng nghĩa chắc chắn với một glyph được render bằng font thực tế. Đây vẫn là cách cắt văn bản phù hợp hơn nhiều so với substring theo char khi cần tránh chia các thành phần của một grapheme.

## 7. String.length, codePointCount và grapheme count phục vụ bài toán nào?

| Bài toán | Cách đếm nên ưu tiên | Lý do |
| --- | --- | --- |
| API Java cần chỉ số char của substring/charAt | UTF-16 unit | Phần lớn index String sử dụng UTF-16 offset |
| Ghi log chỉ số **char** hoặc phân tích mã UTF-16 | **length()** | Đúng đơn vị của dữ liệu đang khảo sát |
| Duyệt Unicode scalar/code point của văn bản hợp lệ | **codePoints()** / **codePointCount()** | Không tách surrogate pair hợp lệ |
| UI giới hạn ký tự người dùng cảm nhận | Grapheme cluster | Dấu kết hợp/emoji ZWJ cần được tính chung thành cụm |
| Cắt một chuỗi emoji ZWJ còn nguyên cụm | Matcher regex **\X** | Tránh chia giữa các thành phần của cụm |
| Giới hạn kích thước HTTP/file UTF-8 | **getBytes(UTF_8).length** | Byte mã hóa không bằng ba loại “ký tự” ở trên |
| So sánh hai cách viết chữ có dấu | Chuẩn hóa Unicode rồi so nội dung | Đếm bằng nhau chưa có nghĩa chuỗi bằng nhau |
| Vẽ glyph trên UI | Hệ thống shaping/rendering | Một grapheme có thể được vẽ bằng nhiều glyph và phụ thuộc font |

Không có API nào trong ba phép đếm tự giải quyết mọi bài toán Unicode. Phải lựa chọn đơn vị khớp với yêu cầu dữ liệu hoặc giao diện.

## 8. Vấn đề phiên bản Unicode và Java BreakIterator

**Pattern "\\X"** là giải pháp có sẵn trong Java regex cho **extended grapheme cluster**. Nó tránh phụ thuộc thư viện ngoài trong nhiều bài thực hành và làm việc với các ví dụ Unicode phổ biến của bài.

Tuy nhiên, bảng quy tắc Unicode/emoji do JDK triển khai có thể khác nhau theo phiên bản. Các chuỗi emoji mới, chuỗi kết hợp phức tạp và trình tự ZWJ mới cần được kiểm tra lại trên JDK của sản phẩm. Không dựa vào số cụm cứng của một ví dụ rồi cho rằng mọi JDK đều phân đoạn mọi emoji giống hệt.

**BreakIterator.getCharacterInstance()** cũng cung cấp cơ chế phân đoạn ký tự, nhưng không nên mặc định kết quả của nó trên mọi phiên bản JDK/locale đều bằng **Pattern "\\X"** hoặc luôn xử lý đúng mọi chuỗi emoji ZWJ. Với hệ thống cần tương thích Unicode UAX #29 nghiêm ngặt hoặc tập emoji mới, có thể dùng công cụ/thư viện phân đoạn grapheme phù hợp như ICU4J và kiểm thử trên bộ dữ liệu mục tiêu.

Cũng không nhầm việc **có một cụm** với việc *người dùng chắc chắn nhìn thấy một hình*. Hỗ trợ font và bộ dựng chữ có thể khác nhau giữa trình duyệt/hệ điều hành.

## 9. Hiệu năng của ba phép đếm

**String.length()** trả trực tiếp số UTF-16 unit đã được biểu diễn trong String; đây là phép lấy độ dài, thường có chi phí hằng số.

**codePointCount()** phải xét nội dung để nhận diện surrogate pair trong phạm vi được yêu cầu; chi phí phụ thuộc số UTF-16 unit đang duyệt.

**Đếm grapheme bằng regex \X** phải chạy bộ máy phân đoạn trên chuỗi; nhìn chung tốn công hơn chỉ đọc độ dài. Nếu thao tác nhiều lần với cùng văn bản, hãy cân nhắc lưu kết quả đếm hoặc chỉ số cụm khi dữ liệu bất biến, thay vì tính lại sau từng lần render.

Không so sánh một phép **length()** với một phép **Matcher.find()** duy nhất rồi kết luận chắc chắn về thời gian của ứng dụng. Muốn benchmark các phương pháp trên dữ liệu lớn:

1. Chuẩn bị đầu vào ASCII, tiếng Việt dựng sẵn, dấu tổ hợp, emoji đơn, emoji ZWJ với cỡ mẫu và tỷ lệ xác định.
2. Ghi rõ so sánh theo số **UTF-16 unit**, **code point** hay **grapheme** tương đương.
3. Dùng JMH với warm-up, nhiều fork và sử dụng kết quả (Blackhole) để tránh JIT loại bỏ công việc.
4. Ghi phiên bản Java, Unicode, cấu hình JVM, kích thước chuỗi, đơn vị ns/op hoặc ops/s.
5. Đo thêm allocation nếu vòng lặp tạo Matcher/stream liên tục là vấn đề của ứng dụng.

**Dùng Pattern tái sử dụng** khi đếm nhiều chuỗi bằng cùng regex. Pattern có thể chia sẻ giữa các thread; Matcher cần tạo riêng cho từng thao tác vì giữ trạng thái khi find.

## 10. Những lỗi thường gặp

| Lỗi | Vì sao sai | Hướng sửa |
| --- | --- | --- |
| **"🙂".length() là 1** | Emoji ngoài BMP cần hai code unit | Kiểm tra **length=2**, **codePointCount=1** |
| **substring(0, 1) luôn lấy ký tự đầu tiên** | Có thể cắt giữa surrogate pair | Dùng offsetByCodePoints hoặc cắt theo grapheme |
| **Một code point luôn là một chữ hiển thị** | Dấu kết hợp và emoji ZWJ có thể ghép nhiều điểm mã | Phân đoạn grapheme với **\X** |
| **codePointCount(0, n)** với n là số code point | Các chỉ số đầu/cuối là UTF-16 offset | Dùng **s.length()** làm cận cuối khi đếm cả chuỗi |
| **"\\X"** bị gõ thành **"\X"** | Java String cần escape dấu gạch chéo ngược | Viết **"\\X"** trong mã nguồn Java |
| Chuỗi tiếng Việt nhìn giống mà **equals=false** | Dạng Unicode dựng sẵn/phân rã | Chuẩn hóa NFC khi yêu cầu nghiệp vụ |
| Đếm grapheme coi CRLF là hai chữ hiển thị | CRLF được phân đoạn thành một cụm dù không phải glyph | Hiểu grapheme là đơn vị phân đoạn, không phải hình |
| Đếm theo grapheme rồi dùng số cụm làm offset substring | substring dùng UTF-16 offset | Lấy **Matcher.start()/end()** hoặc chuyển đổi offset đúng |
| Số cụm của emoji mới thay đổi giữa JDK | Phiên bản dữ liệu Unicode/quy tắc phân đoạn khác nhau | Kiểm thử các mẫu emoji cụ thể trên JDK dùng thực tế |
| Dùng length để giới hạn byte upload | byte UTF-8 khác số UTF-16 unit | Đo **getBytes(UTF_8).length** |

## 11. Bài tập củng cố

**Bài 1: Phân biệt đơn vị**

Dự đoán ba phép đếm cho chuỗi **"A🙂B"**: **String.length()**, **codePointCount()** và regex **\X**. Đáp án: **4 UTF-16 unit**, **3 code point**, **3 grapheme cluster** (A, 🙂, B). Nếu gọi **substring(0, 2)**, kết quả chứa A và nửa đầu surrogate của emoji, không hợp lệ để coi là chuỗi hai ký tự đầy đủ.

**Bài 2: Dấu kết hợp**

Với **"a\u0301"** (a rồi dấu sắc kết hợp), số unit là **2**, code point là **2** nhưng số grapheme cluster là **1**. Thử so sánh với chữ **á** U+00E1 bằng equals trước và sau **Normalizer.Form.NFC**.

**Bài 3: Lấy cụm đầu của emoji nối**

Với **"👩‍💻"**, **offsetByCodePoints(0, 1)** trả chỉ số kết thúc sau code point đầu là **2** UTF-16 unit; trong khi một lần match **\X** cho cụm đầu dài **5** UTF-16 unit. Giải thích vì sao hai cách cắt phục vụ hai yêu cầu khác nhau.

**Bài 4: Thử dữ liệu thật**

Tạo các chuỗi gồm tiếng Việt NFC/NFD, cờ, emoji có modifier, emoji ZWJ và CRLF. Chạy chương trình ở phần 3, rồi thêm một dòng **report** cho mỗi chuỗi. Ghi lại phiên bản JDK, ba số đếm và nhận xét trường hợp số đơn vị khác nhau.

**Bài 5: Giới hạn 10 ký tự giao diện**

Viết chức năng kiểm tra đầu vào theo tối đa **10 grapheme cluster** thay vì **10 char**; sử dụng Matcher regex **\X** như phần 6. Kiểm thử ít nhất một chuỗi emoji ZWJ để tránh cắt dở cụm.

## 12. Tổng kết kiến thức

Khi xử lý Unicode trong Java, hãy luôn hỏi **đang đếm đơn vị nào?**

- **String.length()** đếm UTF-16 code unit, phù hợp với mô hình chỉ số String.
- **codePointCount()** đếm Unicode code point, tránh coi surrogate pair hợp lệ là hai điểm mã.
- **Pattern "\\X"** đếm extended grapheme cluster, thường phù hợp hơn khi làm việc với ký tự người dùng cảm nhận, nhất là tiếng Việt có dấu tổ hợp và emoji ZWJ.

Một văn bản nhìn như **một** emoji có thể cần **nhiều char và nhiều code point**. Lựa chọn đúng đơn vị để đếm, để cắt và để kiểm thử mới tránh lỗi làm hỏng dữ liệu Unicode.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-practice-006">So sánh tên tiếng Việt sau chuẩn hóa phù hợp</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh tên tiếng Việt sau chuẩn hóa phù hợp trong Java

## 1. Bài toán: tên trông giống nhau nhưng `String.equals()` trả về `false`

Tên tiếng Việt chứa các chữ có dấu như **Hòa**, **Thảo**, **Nguyễn**, **Đỗ**. Trong Unicode, một chữ có dấu có thể được biểu diễn bằng một code point dựng sẵn hoặc bằng chữ cơ sở kèm các **combining mark** (dấu kết hợp). Hai cách viết có thể hiển thị giống nhau nhưng chứa các code point khác nhau.

Ví dụ, `"Hòa"` và `"Ho\u0300a"` thường trông như nhau:

~~~java
String composed = "H\u00F2a";     // Hòa: chữ ò đã dựng sẵn
String decomposed = "Ho\u0300a"; // Ho + combining grave + a

System.out.println(composed.equals(decomposed)); // false
~~~

`String.equals()` so sánh **dãy UTF-16 code unit**, nên trả về `false` khi hai biểu diễn khác nhau. Điều này có thể làm tìm kiếm, phát hiện tên trùng hoặc kiểm thử chuỗi thất bại dù người dùng nhìn thấy cùng một tên.

**Cách giải quyết cho trường hợp tương đương Unicode theo chuẩn:** chuyển cả hai chuỗi về cùng một **Unicode normalization form**, thường là **NFC**, rồi so sánh bằng `equals()`:

~~~java
import java.text.Normalizer;

String left = Normalizer.normalize("H\u00F2a", Normalizer.Form.NFC);
String right = Normalizer.normalize("Ho\u0300a", Normalizer.Form.NFC);

System.out.println(left.equals(right)); // true
~~~

Tuy nhiên, **chuẩn hóa Unicode không đồng nghĩa bỏ dấu, bỏ phân biệt hoa/thường hoặc khẳng định hai con người là một**. Cần xác định quy tắc so sánh dựa trên mục đích nghiệp vụ trước khi viết API.

## 2. Các cấp độ “bằng nhau” của tên

| Mục đích | Ví dụ | Quy tắc phù hợp |
| --- | --- | --- |
| So sánh chính xác nội dung chuỗi | `"Hòa"` với chính `"Hòa"` | `String.equals()` |
| Coi hai biểu diễn Unicode tương đương là cùng chữ | `"Hòa"` và `"Ho\u0300a"` | Chuẩn hóa **NFC** cả hai rồi `equals()` |
| Bỏ qua hoa/thường để tìm kiếm | `"HÒA"` và `"hòa"` | Quy tắc case được thống nhất, rồi chuẩn hóa |
| Bỏ qua khoảng trắng thừa theo chính sách ứng dụng | `" Hòa  "` và `"Hòa"` | Chuẩn hóa khoảng trắng **nếu nghiệp vụ cho phép** |
| Sắp xếp tên theo ngôn ngữ | `"Hoa"`, `"Hòa"`, `"Đỗ"` | `Collator` theo locale tiếng Việt với cấu hình rõ ràng |
| Tìm gần đúng, chấp nhận thiếu dấu | `"Hoa"` và `"Hòa"` | Chiến lược tìm kiếm gần đúng hoặc accent-insensitive riêng |
| Xác định **cùng một người** | Hai hồ sơ cùng tên | Cần mã định danh và dữ liệu xác minh; tên không đủ |

Chẳng hạn, `"Hoa"` và `"Hòa"` là **hai chuỗi khác nhau** ngay cả sau NFC. Tự động bỏ tất cả dấu tiếng Việt có thể làm gộp các tên hợp lệ khác nhau. Tương tự, chữ **`đ`** và **`d`** không phải cùng một ký tự; NFC không biến ký tự này thành ký tự kia.

Với tên người, nên phân biệt rõ:

1. **Tên hiển thị:** giữ chính tả và cách viết người dùng cung cấp, có thể lưu kèm dạng Unicode thống nhất.
2. **Khóa so sánh:** giá trị được tạo bằng chính sách đã công bố, phục vụ tìm kiếm hoặc loại trùng theo quy ước.
3. **Định danh hồ sơ:** dùng ID duy nhất, không dùng tên đã chuẩn hóa để quyết định hai bản ghi thuộc cùng một người.

## 3. Unicode normalization: NFC, NFD, NFKC và NFKD

Java cung cấp `java.text.Normalizer` với bốn dạng:

| Dạng | Ý nghĩa chính | Cân nhắc khi xử lý tên |
| --- | --- | --- |
| **NFC** | Phân rã chuẩn tắc rồi ghép lại theo dạng tổ hợp chuẩn | Lựa chọn thực dụng cho so sánh tương đương Unicode của tên |
| **NFD** | Phân rã chuẩn tắc | Có thể dùng trước một số bước xử lý dấu, nhưng chưa có nghĩa là bỏ dấu |
| **NFKC** | Chuẩn hóa **compatibility** rồi tổ hợp | Có thể biến đổi thêm các ký tự tương thích; cần chính sách nghiệp vụ |
| **NFKD** | Phân rã **compatibility** | Tăng phạm vi biến đổi so với NFD; không tự động phù hợp với tên riêng |

Hai chuỗi **canonically equivalent** sẽ bằng nhau sau NFC hoặc NFD. Việc gọi NFC nhiều lần trên cùng một chuỗi đã chuẩn hóa không làm đổi kết quả (**idempotent**).

~~~java
String input = "Ho\u0300a";
String nfc = Normalizer.normalize(input, Normalizer.Form.NFC);

System.out.println(nfc.equals("H\u00F2a")); // true
System.out.println(Normalizer.isNormalized(
        nfc, Normalizer.Form.NFC));          // true
~~~

**Không mặc định dùng NFKC cho mọi tên người.** Chuẩn hóa compatibility có thể làm mất khác biệt kiểu chữ hoặc biểu diễn mà hệ thống muốn giữ nguyên. Nếu cần loại trùng theo một chuẩn cụ thể, tài liệu hệ thống phải ghi rõ **dạng chuẩn hóa** và giai đoạn áp dụng.

Cũng không thể suy ra hai chuỗi “bằng nhau” chỉ vì chúng hiển thị gần giống. Ký tự thuộc bảng chữ cái khác, ký tự vô hình hay cách ghép emoji có thể tạo văn bản nhìn tương tự mà không canonically equivalent.

## 4. Thiết kế chính sách chuẩn hóa tên tiếng Việt

Một chính sách tối thiểu thường phù hợp cho **so sánh tên đầy đủ chính xác nhưng chấp nhận khác biệt biểu diễn Unicode** là:

- Quyết định rõ có cho phép bỏ khoảng trắng ở đầu/cuối và gộp nhiều khoảng trắng liên tiếp hay không.
- Chuyển về **NFC**.
- Giữ nguyên **dấu tiếng Việt**, chữ `đ`, chữ hoa/thường và các chữ cái khác, trừ khi nghiệp vụ yêu cầu khác.
- So sánh hai kết quả với `equals()`.

Ví dụ riêng: khi sản phẩm cho phép gộp mọi cụm whitespace theo chính sách nhập liệu, kể cả một số ký tự khoảng trắng Unicode, có thể chuẩn hóa bằng:

~~~java
String collapsed = input.replaceAll("[\\p{Z}\\s]+", " ").strip();
String normalized = Normalizer.normalize(
        collapsed, Normalizer.Form.NFC);
~~~

Trong regex Java, `\p{Z}` là nhóm Unicode separator; `\s` (trong chế độ mặc định) bao phủ các whitespace phổ biến như dấu cách, tab và xuống dòng. Hai lớp được phối hợp ở đây nhằm chuyển các khoảng phân cách tương ứng thành **một dấu cách ASCII**, rồi `strip()` bỏ dấu cách đầu/cuối.

**Đây là một quyết định ứng dụng**, không phải chức năng bắt buộc của NFC. Nó có thể làm thay đổi tên chứa khoảng trắng đặc biệt hoặc xuống dòng. Trong dữ liệu pháp lý, tên hiển thị hoặc tài liệu cần lưu nguyên văn, chỉ nên tạo **khóa phụ** đã chuẩn hóa và vẫn bảo toàn dữ liệu gốc.

### Tìm kiếm không phân biệt hoa/thường

Nếu yêu cầu là tìm `"HÒA"` bằng `"hòa"`, có thể tạo khóa tìm kiếm bằng chữ thường:

~~~java
import java.util.Locale;

String searchKey = Normalizer.normalize(
        normalized.toLowerCase(Locale.ROOT),
        Normalizer.Form.NFC);
~~~

`Locale.ROOT` giúp tránh phụ thuộc vào default locale của máy chạy khi quy tắc nghiệp vụ là **khóa tìm kiếm kỹ thuật ổn định**. Tuy nhiên, lowercase không phải Unicode **full case folding** và không thay thế toàn bộ quy tắc so sánh văn bản theo ngôn ngữ. Nếu sản phẩm cần case-insensitive matching đa ngôn ngữ với yêu cầu nghiêm ngặt, phải chọn và kiểm thử thuật toán theo đặc tả đó.

Không nên hạ chữ thường hoặc bỏ dấu trên **bản tên hiển thị** chỉ để tìm kiếm. Giữ nguyên tên gốc và tạo search key riêng.

## 5. Ví dụ Java 17+ hoàn chỉnh: so sánh, tìm kiếm và loại trùng theo chính sách

Lưu vào file `VietnameseNameComparisonDemo.java`:

~~~java
import java.text.Collator;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class VietnameseNameComparisonDemo {
    // Chính sách ví dụ: gom whitespace, bỏ khoảng trắng biên, chuẩn hóa NFC.
    static String canonicalName(String input) {
        Objects.requireNonNull(input, "input");
        String spaced = input.replaceAll("[\\p{Z}\\s]+", " ").strip();
        return Normalizer.normalize(spaced, Normalizer.Form.NFC);
    }

    // Khóa tìm kiếm chấp nhận khác biệt hoa/thường nhưng giữ dấu.
    static String searchKey(String input) {
        String lowered = canonicalName(input).toLowerCase(Locale.ROOT);
        return Normalizer.normalize(lowered, Normalizer.Form.NFC);
    }

    public static void main(String[] args) {
        String composed = "H\u00F2a";      // Hòa
        String decomposed = "Ho\u0300a";  // Ho + dấu huyền + a
        String withSpaces = "  H\u00F2a\u00A0  ";

        System.out.println("rawEquals=" +
                composed.equals(decomposed));
        System.out.println("nfcEquals=" +
                canonicalName(composed).equals(canonicalName(decomposed)));
        System.out.println("spacesEquals=" +
                canonicalName(withSpaces).equals(canonicalName(composed)));
        System.out.println("caseSearchEquals=" +
                searchKey("H\u00D2A").equals(searchKey(decomposed)));
        System.out.println("accentPreserved=" +
                !searchKey("Hoa").equals(searchKey(composed)));

        Set<String> exactKeys = new HashSet<>();
        for (String name : List.of(
                composed, decomposed, withSpaces, "Hoa")) {
            exactKeys.add(canonicalName(name));
        }
        System.out.println("exactKeyCount=" + exactKeys.size());

        Set<String> searchKeys = new HashSet<>();
        for (String name : List.of(
                composed, "h\u00F2a", "H\u00D2A", "Hoa")) {
            searchKeys.add(searchKey(name));
        }
        System.out.println("searchKeyCount=" + searchKeys.size());

        Collator vi = Collator.getInstance(
                Locale.forLanguageTag("vi-VN"));
        vi.setDecomposition(Collator.CANONICAL_DECOMPOSITION);
        vi.setStrength(Collator.TERTIARY);

        System.out.println("collatorCanonical=" +
                (vi.compare(composed, decomposed) == 0));
        System.out.println("nfcIdempotent=" +
                canonicalName(decomposed)
                        .equals(canonicalName(canonicalName(decomposed))));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 VietnameseNameComparisonDemo.java
java VietnameseNameComparisonDemo
~~~

`stdout` kỳ vọng trên JDK Java 17+ hỗ trợ locale tiếng Việt:

~~~text
rawEquals=false
nfcEquals=true
spacesEquals=true
caseSearchEquals=true
accentPreserved=true
exactKeyCount=2
searchKeyCount=2
collatorCanonical=true
nfcIdempotent=true
~~~

**Giải thích theo thứ tự:**

1. Hai biểu diễn `Hòa` khác nhau ở cấp độ code unit nên `rawEquals=false`.
2. NFC đưa chúng về dạng tương đương nên `nfcEquals=true`.
3. Chính sách gom separator và bỏ khoảng trắng biên làm cho tên có NBSP (`\u00A0`) khớp tên chuẩn, nên `spacesEquals=true`.
4. Hạ chữ thường để tạo search key cho phép `HÒA` khớp `Hòa`, nhưng **không bỏ dấu**, nên `Hoa` vẫn khác `Hòa`.
5. Tập `exactKeys` và `searchKeys` đều còn hai khóa: nhóm tên `Hòa` và tên `Hoa`.
6. `Collator` với decomposition chuẩn tắc nhận thấy hai biểu diễn canonically equivalent của cùng chữ là bằng nhau.
7. NFC áp dụng lặp lại không làm đổi giá trị.

Đây là ví dụ **chính sách so sánh được chọn**, không phải hàm duy nhất “đúng” cho mọi nghiệp vụ. Nếu hệ thống cần giữ tất cả khoảng trắng gốc hoặc phân biệt chính xác chữ hoa/thường, cần điều chỉnh bước tạo khóa và cập nhật test tương ứng.

## 6. `Collator`: khi cần so sánh tên theo tiếng Việt để sắp xếp

`String.compareTo()` so sánh thứ tự code unit UTF-16; nó **không** phải bộ sắp xếp tên theo quy tắc ngôn ngữ tiếng Việt. Khi hiển thị danh sách tên theo bảng chữ cái, dùng `java.text.Collator` với locale phù hợp:

~~~java
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

Collator vi = Collator.getInstance(Locale.forLanguageTag("vi-VN"));
vi.setDecomposition(Collator.CANONICAL_DECOMPOSITION);
vi.setStrength(Collator.TERTIARY);

List<String> names = new ArrayList<>(
        List.of("Đỗ", "Hoa", "Hòa", "An"));

names.sort(vi);
~~~

Đoạn này minh họa code **bên trong một phương thức**. `Collator` có thể được dùng làm `Comparator<String>` cho sắp xếp. Không nên khẳng định cứng một thứ tự tên cụ thể áp dụng trên mọi bản JDK hoặc provider: kết quả collation có thể thay đổi theo dữ liệu locale và implementation.

### Strength, decomposition và tác động tới phép so sánh

`Collator` có các mức `PRIMARY`, `SECONDARY`, `TERTIARY` và `IDENTICAL`. Mức càng thấp thì càng có thể bỏ qua một số khác biệt khi so sánh; mức cao hơn xét nhiều chi tiết hơn. Cụ thể **dấu và chữ hoa/thường được coi là khác nhau ở mức nào** phụ thuộc bộ quy tắc collation của locale được dùng.

Đối với tên tiếng Việt:

- Nếu cần giữ sự khác biệt về dấu, **đừng tự giả định** rằng `PRIMARY` sẽ phù hợp; cần kiểm thử tập tên thực tế, gồm cả `d`/`đ` và các dấu thanh.
- Nếu muốn phép so sánh cân nhắc các chi tiết chữ viết, `TERTIARY` là lựa chọn ban đầu có thể thử nghiệm, sau đó đánh giá yêu cầu nghiệp vụ.
- `CANONICAL_DECOMPOSITION` hỗ trợ so sánh những biểu diễn Unicode tương đương chuẩn tắc; việc NFC trước khi đưa vào bộ sắp xếp cũng làm dữ liệu nhất quán hơn.
- `Collator` có trạng thái cấu hình và không nên chia sẻ một instance mutable không đồng bộ giữa nhiều thread; tạo hoặc quản lý instance theo vòng đời phù hợp.

**Quan trọng với collection:** `TreeSet` và `TreeMap` dùng `compare(a,b) == 0` để xác định hai khóa được coi là tương đương. Trong khi đó, `HashSet` và `HashMap` dựa trên `equals/hashCode`. Khi comparator có quy tắc khác `String.equals`, hai loại collection có thể quyết định trùng lặp khác nhau. Chọn comparator để **sắp xếp** không đồng nghĩa được phép dùng nó làm bằng chứng rằng hai hồ sơ là cùng một người.

## 7. Có nên bỏ dấu khi tìm tên?

Một ô tìm kiếm có thể muốn người dùng gõ `Hoa` vẫn thấy `Hòa`; đó là **tìm kiếm gần đúng/không phân biệt dấu**, không phải tương đương chuẩn tắc NFC. Cần đặt tên và công bố chính sách riêng.

Cách “phân rã NFD rồi xóa tất cả combining mark” có thể làm mất thông tin tiếng Việt, tạo trùng tên không mong muốn; riêng chữ **`đ`** không tự biến thành `d` chỉ qua NFD. Nếu nghiệp vụ thực sự cần tìm không dấu:

1. Giữ **dữ liệu tên gốc** và **khóa NFC** để so sánh chính xác.
2. Tạo **chỉ mục tìm kiếm phụ** theo chính sách bỏ dấu được tài liệu hóa; cân nhắc cách xử lý `đ`/`d`.
3. Dùng chỉ mục phụ để đưa ra **kết quả gợi ý**, sau đó hiển thị tên chính xác để người dùng xác nhận.
4. Tránh dùng khóa bỏ dấu làm primary key hoặc căn cứ duy nhất để gộp hồ sơ.

Không có quy tắc “bỏ dấu tiếng Việt” duy nhất phù hợp đồng thời cho tìm kiếm, phân tích ngôn ngữ, pháp lý và định danh người dùng.

## 8. Lưu dữ liệu gốc, khóa chuẩn hóa và định danh riêng

Một thiết kế dữ liệu thực tế có thể lưu:

| Trường | Ví dụ | Công dụng |
| --- | --- | --- |
| `person_id` | `8742` | Định danh duy nhất, ổn định của hồ sơ |
| `display_name` | `Hòa` | Văn bản hiển thị theo lựa chọn người dùng |
| `canonical_name` | `Hòa` ở NFC | So sánh tương đương Unicode và khoảng trắng theo chính sách |
| `search_name_key` | `hòa` | Tìm kiếm không phân biệt hoa/thường nhưng giữ dấu |
| `accent_insensitive_key` (nếu cần) | Giá trị theo thuật toán tìm gần đúng đã duyệt | Tìm kiếm phụ, không định danh |

Tên người **không phải khóa duy nhất đáng tin cậy**. Hai người khác nhau có thể trùng họ tên, còn một người có thể thay đổi tên hoặc cách nhập. Dù khóa chuẩn hóa trùng, hành động gộp hồ sơ thường cần thêm ID, thuộc tính nghiệp vụ và bước xác nhận tương ứng.

Cũng cần thống nhất quy tắc giữa frontend, backend và cơ sở dữ liệu. SQL collation có thể có quy tắc tương đương khác với `Normalizer` và `Collator` của Java. Nếu có unique constraint trên một trường tên, hãy kiểm thử rằng cơ chế so sánh tại database thực hiện đúng điều ứng dụng kỳ vọng.

## 9. Những lỗi thường gặp

| Sai lầm | Tác hại | Cách xử lý |
| --- | --- | --- |
| Dùng `equals()` trực tiếp với hai biểu diễn NFC/NFD khác nhau | Tên trông giống nhau nhưng không khớp | Chuẩn hóa cùng NFC trước khi so sánh |
| Nghĩ NFC tự bỏ dấu | Gộp sai chính sách so sánh | Phân biệt canonical equivalence với accent-insensitive search |
| Biến `Hòa` thành `Hoa` rồi coi là cùng người | Mất sự khác biệt tên hợp lệ | Giữ dấu cho so sánh chính xác; bỏ dấu chỉ cho tìm kiếm được thiết kế riêng |
| Cho rằng `đ` và `d` tương đương do NFD | Hiểu sai normalization | Kiểm tra quy tắc riêng nếu cần bỏ dấu |
| Chỉ chuyển chữ thường ở một phía | Kết quả phụ thuộc nguồn dữ liệu | Áp dụng cùng thuật toán tạo khóa ở cả lúc lưu và tra |
| Dùng `toLowerCase()` không chỉ định locale | Có thể phụ thuộc default locale | Chọn locale rõ ràng theo mục đích |
| Dùng `Collator` mà không cố định locale/strength | Khó tái hiện hành vi qua môi trường | Cấu hình và kiểm thử locale, strength, decomposition |
| Dùng `TreeSet` để loại trùng hồ sơ | Comparator có thể coi tên khác nhau là trùng | Dùng ID và chính sách trùng lặp nghiệp vụ |
| Ghi đè tên gốc bằng search key | Mất chính tả, dấu, cách viết hiển thị | Lưu `display_name` riêng |
| Tin vào tên đã chuẩn hóa như một định danh | Có thể gộp hai người khác nhau | Dùng ID ổn định và dữ liệu xác minh bổ sung |

### Tình huống Unicode cần test

- `"Hòa"` dựng sẵn và `"Ho\u0300a"` tổ hợp dấu.
- `"HÒA"`, `"Hòa"` và `"hòa"` khi tìm kiếm không phân biệt hoa/thường.
- `"Hoa"` so với `"Hòa"` khi chính sách **giữ dấu**.
- `"Đỗ"` so với `"Dỗ"`: không được tự đồng nhất chữ `đ` và `d`.
- Tên có khoảng trắng đầu/cuối, nhiều dấu cách hoặc NBSP.
- Tên có combining mark đặt ở thứ tự hợp lệ nhưng biểu diễn khác nhau.
- Ký tự vô hình, dấu câu và dấu gạch nối trong tên; không tự xóa nếu chưa có quy tắc.
- Các bộ dữ liệu tên thực tế được sắp xếp bởi `Collator` với phiên bản JDK và provider dự kiến.
- Giá trị `null`, chuỗi rỗng và tên chỉ chứa whitespace; phải quyết định rõ chấp nhận hay từ chối.

## 10. Checklist thiết kế và triển khai

1. Xác định đây là **so sánh hiển thị chính xác**, **tìm kiếm**, **sắp xếp** hay **định danh con người**.
2. Chọn dạng chuẩn hóa Unicode (thông thường NFC) và cách xử lý whitespace theo chính sách.
3. Quyết định có phân biệt hoa/thường, dấu tiếng Việt và `đ`/`d` hay không; không ngầm coi chúng tương đương.
4. Viết một hàm tạo khóa, dùng thống nhất ở phía lưu và phía tìm kiếm.
5. Giữ tên hiển thị gốc, lưu khóa phụ nếu cần, dùng ID để quản lý hồ sơ.
6. Với việc sắp xếp, cấu hình locale và strength cho `Collator`, tránh giả định rằng thứ tự không bao giờ đổi giữa các môi trường.
7. Đồng bộ quy tắc ứng dụng với database collation, unique index và chức năng tìm kiếm.
8. Kiểm thử các chuỗi cùng hình thức hiển thị nhưng khác dãy code point, cũng như những tên **thực sự khác nhau** chỉ bởi dấu.

**Kết luận:** Để so sánh tên tiếng Việt hợp lý, hãy dùng **NFC** nhằm giải quyết khác biệt biểu diễn Unicode, và viết rõ các bước xử lý khoảng trắng, chữ hoa/thường hoặc tìm kiếm không dấu theo yêu cầu thực tế. `String.equals()` sau chuẩn hóa phù hợp cho khóa so sánh nội dung; `Collator` hỗ trợ sắp xếp theo ngôn ngữ; còn nhận diện **cùng một người** phải dựa vào định danh và quy tắc nghiệp vụ, không dựa duy nhất vào tên.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-practice-007">Tái hiện bẫy null, overload, intern và phép nối với số</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành tổng hợp: tái hiện bẫy null, overload, intern và phép nối String với số

## 1. Mục tiêu và cách học

Bài thực hành này yêu cầu **dự đoán kết quả trước khi chạy**, sau đó kiểm tra trên Java 17+ để nhận diện bốn nhóm bẫy thường gặp:

1. **`null`**: tham chiếu rỗng có thể được nối thành chữ `"null"`, gây `NullPointerException` hoặc khiến lời gọi overload không xác định.
2. **Overload**: Java chọn phương thức theo **kiểu của biểu thức lúc biên dịch**, không dựa vào tên lớp thực tế của đối tượng ở runtime để chọn một overload mới.
3. **String pool và `intern()`**: `==` kiểm tra **cùng đối tượng**, còn `equals()` kiểm tra **cùng nội dung**. Literal và biểu thức hằng có cơ chế chia sẻ khác chuỗi được tạo lúc chạy.
4. **Phép nối với số và `char`**: dấu `+` vừa là phép cộng số vừa là nối chuỗi; thứ tự đánh giá và dấu ngoặc ảnh hưởng kết quả.

Mỗi phần gồm đoạn mã, kết quả cần dự đoán, giải thích và cách viết an toàn. Không nên chạy đồng thời những dòng được đánh dấu **lỗi biên dịch** vì chúng được đưa ra để minh họa tình huống sai. Với các ví dụ gây ngoại lệ lúc chạy, đặt lời gọi trong `try/catch` nếu muốn các câu lệnh phía sau tiếp tục thực thi.

## 2. Bẫy null: nối chuỗi không giống gọi phương thức

`String` là **kiểu tham chiếu**. Một biến kiểu `String` có thể chứa tham chiếu `null`, nghĩa là biến đó **không trỏ đến đối tượng String nào**. Điều này khác với chuỗi rỗng `""`.

~~~java
String missing = null;
String empty = "";

System.out.println(missing == null); // true
System.out.println(empty == null);   // false
System.out.println(empty.isEmpty()); // true
~~~

### 2.1. Nối với null tạo chữ "null"

Trong ngữ cảnh nối chuỗi, một tham chiếu `null` được chuyển thành văn bản `"null"`:

~~~java
String value = null;

System.out.println("id=" + value); // id=null
System.out.println(value + "!");   // null!
~~~

Việc này **không chứng minh** biến `value` chứa đối tượng chuỗi có bốn ký tự `n-u-l-l`. Nó vẫn là tham chiếu `null`; phép nối chỉ tạo giá trị chuỗi mới để hiển thị.

Nếu không muốn xuất chữ `null` ra giao diện, xử lý dữ liệu vắng mặt có chủ đích:

~~~java
String label = value == null ? "(chưa có)" : value;
System.out.println("id=" + label); // id=(chưa có)
~~~

Cách này an toàn khi quy ước nghiệp vụ là thay null bằng thông báo cụ thể. Nếu null là dữ liệu lỗi cần từ chối, nên phát hiện và báo lỗi ở nơi phù hợp thay vì im lặng thay thế.

### 2.2. Gọi phương thức trên null gây NullPointerException

~~~java
String value = null;

// value.length();           // NullPointerException khi chạy
// value.concat("!");        // NullPointerException khi chạy
// value.equals("ready");    // NullPointerException khi chạy
~~~

Ngược lại, lời gọi trên một đối tượng có thật có thể so sánh với tham chiếu null:

~~~java
String value = null;

System.out.println("ready".equals(value)); // false
System.out.println(java.util.Objects.equals(value, "ready")); // false
System.out.println(java.util.Objects.equals(null, null)); // true
~~~

Vì thế, với một hằng chuỗi và biến có thể null, `"ready".equals(value)` thường an toàn và dễ đọc. Khi cả hai vế có thể null, `Objects.equals(a, b)` cung cấp so sánh nội dung có xử lý trường hợp null.

Cần phân biệt ý nghĩa: `Objects.equals(null, null)` trả `true` vì **hai tham chiếu đều null**, không có nghĩa tồn tại hai chuỗi có cùng nội dung.

### 2.3. String.valueOf(null) có một bẫy overload

`String.valueOf` có nhiều overload, bao gồm:

~~~java
String.valueOf(Object obj)
String.valueOf(char[] data)
~~~

Hai lời gọi nhìn gần giống nhau nhưng có hành vi khác:

~~~java
System.out.println(String.valueOf((Object) null)); // null

try {
    String.valueOf((char[]) null);
} catch (NullPointerException ex) {
    System.out.println("char[] null => NPE");
}
~~~

Khi truyền `(Object) null`, phương thức `valueOf(Object)` trả chuỗi `"null"`. Khi truyền `(char[]) null`, overload mảng ký tự cần truy cập mảng và ném `NullPointerException`.

Bẫy thú vị hơn:

~~~java
// String.valueOf(null);
~~~

Dòng này **biên dịch được** nhưng Java chọn `valueOf(char[])`, vì `char[]` là kiểu con của `Object` và là overload cụ thể hơn trong các lựa chọn áp dụng. Kết quả là **`NullPointerException` lúc chạy**.

Muốn hành vi chuyển tham chiếu null thành chữ `"null"`, viết rõ:

~~~java
String text = String.valueOf((Object) null);
~~~

Hoặc nếu muốn giá trị thay thế:

~~~java
String text = java.util.Objects.toString(null, "(trống)");
// (trống)
~~~

Thông điệp thực tế: **đừng kết luận từ tên phương thức**. Hãy xem overload nào được chọn theo kiểu đối số.

## 3. Bẫy overload: Java quyết định ở thời điểm biên dịch

Một lớp có thể có nhiều phương thức cùng tên nhưng khác danh sách tham số:

~~~java
static String classify(Object value) {
    return "Object";
}

static String classify(String value) {
    return "String";
}
~~~

Khi gọi:

~~~java
System.out.println(classify("Java")); // String
System.out.println(classify(null));   // String
System.out.println(classify((Object) null)); // Object
~~~

Giải thích:

- Chuỗi `"Java"` có kiểu `String` nên overload nhận `String` khớp cụ thể nhất.
- Literal `null` có thể chuyển sang các kiểu tham chiếu; `String` cụ thể hơn `Object` nên overload `String` được chọn.
- `(Object) null` có **kiểu biểu thức lúc biên dịch là Object**, nên overload nhận `Object` được chọn.

Hàm `classify` trên là phương thức `static` ở ví dụ minh họa. Với các phương thức overload thông thường, Java vẫn phải quyết định **chữ ký phương thức** dựa vào kiểu tại thời điểm biên dịch; *override* (ghi đè phương thức) là cơ chế khác, có thể phân phối phương thức theo loại đối tượng thực lúc chạy.

### 3.1. Hai overload không có kiểu cụ thể hơn: lỗi biên dịch

~~~java
static String inspect(String value) {
    return "String";
}

static String inspect(Integer value) {
    return "Integer";
}

// System.out.println(inspect(null)); // LỖI BIÊN DỊCH: ambiguous
~~~

`String` và `Integer` không phải kiểu con của nhau. Literal `null` áp dụng được cho cả hai, nên compiler không thể chọn overload tốt hơn. Sửa bằng cách nêu rõ ý định:

~~~java
System.out.println(inspect((String) null));  // String
System.out.println(inspect((Integer) null)); // Integer
~~~

Ép kiểu ở đây **chỉ giúp chọn overload**, không tạo đối tượng mới và không biến null thành nội dung chuỗi. Nếu phương thức thật truy cập nội dung của tham số mà không kiểm tra null, vẫn có thể ném ngoại lệ.

### 3.2. Vì sao System.out.println(null) gây mơ hồ?

`PrintStream` có các overload chẳng hạn:

~~~java
println(String x)
println(char[] x)
println(Object x)
~~~

Lời gọi:

~~~java
// System.out.println(null); // LỖI BIÊN DỊCH: ambiguous
~~~

`String` và `char[]` đều cụ thể hơn `Object` nhưng không kiểu nào cụ thể hơn kiểu còn lại. Compiler không chọn được.

Các lời gọi sau có ý nghĩa rõ ràng:

~~~java
System.out.println((String) null); // null
System.out.println((Object) null); // null
~~~

Trong khi đó:

~~~java
// System.out.println((char[]) null); // NullPointerException khi chạy
~~~

Sự khác biệt là overload nhận mảng ký tự định xử lý **phần tử của mảng**, không đơn thuần chuyển đối tượng mảng thành văn bản.

### 3.3. StringBuilder.append(null) cũng có bẫy tương tự

`StringBuilder` có nhiều overload cho `String`, `CharSequence`, `Object` và `char[]`, trong số các kiểu khác.

~~~java
// new StringBuilder().append(null); // LỖI BIÊN DỊCH: ambiguous
~~~

Lời gọi `null` chưa ép kiểu có thể khớp các overload tham chiếu không thể sắp thứ tự “cụ thể nhất”. Hãy nêu rõ kiểu và hành vi mong muốn:

~~~java
System.out.println(new StringBuilder().append((String) null));
// null

System.out.println(new StringBuilder().append((Object) null));
// null
~~~

Nếu dùng overload `append(char[])` với mảng null, sẽ có `NullPointerException` khi chạy. Đây là ví dụ rõ cho nguyên tắc: **lỗi biên dịch do overload mơ hồ và ngoại lệ lúc chạy do truyền null vào phương thức đã chọn là hai loại lỗi khác nhau**.

## 4. String pool, intern() và hai kiểu so sánh

Trong Java:

- `a == b`, nếu `a` và `b` đều là tham chiếu đến đối tượng, kiểm tra **có cùng tham chiếu** hay không.
- `a.equals(b)` với hai chuỗi không null kiểm tra **nội dung chuỗi bằng nhau**.
- `String` là bất biến; hai đối tượng khác nhau hoàn toàn có thể giữ cùng nội dung.

### 4.1. Literal và new String()

~~~java
String a = "java";
String b = "java";
String c = new String("java");

System.out.println(a == b);      // true
System.out.println(a == c);      // false
System.out.println(a.equals(c)); // true
~~~

Các literal `"java"` trong cùng môi trường Java được intern/chia sẻ theo quy tắc của ngôn ngữ, nên `a` và `b` cùng trỏ đến một đối tượng chuỗi pooled. `new String("java")` yêu cầu tạo một đối tượng `String` mới; `c` có cùng nội dung nhưng không cùng tham chiếu với `a`.

Sử dụng `equals()` khi kiểm tra mã trạng thái, username, tên sản phẩm, lựa chọn menu hoặc nội dung nhận từ API/file/database:

~~~java
if ("ACTIVE".equals(status)) {
    // Xử lý khi nội dung status bằng "ACTIVE".
}
~~~

Không dùng `status == "ACTIVE"` để so sánh giá trị nghiệp vụ vì kết quả có thể phụ thuộc việc đối tượng có được chia sẻ hay không.

### 4.2. intern() trả về tham chiếu chuẩn trong string pool

~~~java
String literal = "java";
String created = new String("java");

System.out.println(created == literal);          // false
System.out.println(created.intern() == literal); // true
System.out.println(created.equals(literal));     // true
~~~

`intern()` trả về một tham chiếu chuẩn cho nội dung đó trong *string pool*. Nếu pool đã có một chuỗi bằng nội dung thì `intern()` trả tham chiếu của chuỗi đã có; nếu chưa có, việc quản lý tham chiếu chuẩn diễn ra theo quy tắc của JVM.

Không lạm dụng `intern()` như “mẹo tăng tốc mọi phép so sánh”. Việc đưa nhiều chuỗi riêng biệt vào pool có thể tăng áp lực bộ nhớ/chi phí quản lý; hiệu quả phải xét theo số lượng giá trị lặp lại, vòng đời và yêu cầu ứng dụng. `equals()` vẫn là API đúng để so nội dung, kể cả khi một hoặc cả hai chuỗi đã được intern.

### 4.3. Constant folding và phép nối lúc chạy

Phân biệt biểu thức hằng với biểu thức có biến:

~~~java
String literal = "java";
String folded = "ja" + "va";

String part = "ja";
String runtime = part + "va";

System.out.println(literal == folded);  // true
System.out.println(literal == runtime); // false
System.out.println(literal.equals(runtime)); // true
~~~

`"ja" + "va"` là **biểu thức hằng** nên compiler có thể gộp thành literal `"java"` và dùng tham chiếu interned. `part + "va"` là nối chuỗi tại runtime; kết quả mới có cùng nội dung nhưng không tự động trở thành tham chiếu pooled chỉ vì nội dung trùng một literal.

Nếu `part` là **biến hằng compile-time** đủ điều kiện:

~~~java
final String part = "ja";
String value = part + "va";
System.out.println(value == "java"); // true
~~~

Vì `part` là `final String` được gán bởi một biểu thức hằng, biểu thức nối này cũng là hằng tại compile time. Đừng diễn giải rằng **mọi biến `final` đều tạo biểu thức hằng**: nếu giá trị nhận từ phương thức hoặc input runtime, nó không trở thành compile-time constant chỉ nhờ từ khóa `final`.

**Quy tắc thực hành:** dự đoán `==` để hiểu cơ chế pool; trong mã ứng dụng, dùng `equals()` hoặc `Objects.equals()` để so giá trị.

## 5. Dấu + với số: thứ tự trái sang phải quyết định kết quả

Dấu `+` có hai vai trò:

- Khi cả hai toán hạng phù hợp phép cộng số, Java thực hiện **cộng số** theo quy tắc numeric promotion.
- Khi một toán hạng là `String`, Java thực hiện **nối chuỗi**, chuyển toán hạng còn lại thành biểu diễn văn bản.

Các toán tử cộng/nối cùng độ ưu tiên được liên kết **từ trái sang phải**. Dấu ngoặc làm thay đổi nhóm biểu thức:

~~~java
System.out.println(1 + 2 + "3");   // 33
System.out.println("1" + 2 + 3);   // 123
System.out.println("1" + (2 + 3)); // 15
System.out.println((1 + 2) + "3"); // 33
~~~

Giải thích từng bước:

1. `1 + 2 + "3"`: `1 + 2` là cộng số tạo `3`; sau đó `3 + "3"` tạo chuỗi `"33"`.
2. `"1" + 2 + 3`: bắt đầu bằng String nên `"1" + 2` thành `"12"`; tiếp tục `"12" + 3` thành `"123"`.
3. `"1" + (2 + 3)`: biểu thức trong ngoặc cho `5` trước, rồi nối thành `"15"`.
4. `(1 + 2) + "3"` cho cùng nội dung `"33"` như ví dụ đầu vì hai số đã được cộng trước.

Với một biến số:

~~~java
int price = 10;
int fee = 5;

System.out.println("Total: " + price + fee);   // Total: 105
System.out.println("Total: " + (price + fee)); // Total: 15
~~~

Dấu ngoặc làm rõ ý định phép cộng. Đây là lỗi tính đúng của chương trình, không phải vấn đề hiệu năng.

### 5.1. char cộng số: thường ra mã số, không ra ký tự kế tiếp

`char` là một kiểu số nguyên 16 bit không dấu. Trong phép cộng số với `int`, `char` được nâng kiểu để tính toán:

~~~java
System.out.println('A' + 1);     // 66
System.out.println("A" + 1);     // A1
System.out.println("char=" + 'A' + 1); // char=A1
~~~

`'A' + 1` không in chữ `B`: biểu thức có kiểu `int` và giá trị `65 + 1 = 66`. Nếu thật sự muốn ký tự tiếp theo trong phạm vi phù hợp:

~~~java
char next = (char) ('A' + 1);
System.out.println(next); // B
~~~

Cách này chỉ là phép toán trên giá trị UTF-16 và **không phải** thuật toán tăng “ký tự Unicode hiển thị” nói chung.

### 5.2. Phép nối với null và số cùng lúc

~~~java
String missing = null;

System.out.println("x=" + missing + 1);  // x=null1
System.out.println("x=" + (1 + 2));      // x=3
System.out.println((Object) null + "3"); // null3
~~~

Chọn rõ dấu ngoặc và cách thay thế null. Ví dụ `"x=" + (missing == null ? 0 : Integer.parseInt(missing)) + 1` vẫn sẽ nối `1` sau khi bắt đầu bằng String; để tính tổng cần bao toàn bộ phép tính: `"x=" + ((missing == null ? 0 : Integer.parseInt(missing)) + 1)`.

## 6. StringBuilder.append() cũng phân biệt kiểu dữ liệu

`StringBuilder` có nhiều overload; kết quả có thể khác nhau theo biểu thức truyền vào:

~~~java
StringBuilder a = new StringBuilder();
a.append('A').append(1);
System.out.println(a); // A1

StringBuilder b = new StringBuilder();
b.append('A' + 1);
System.out.println(b); // 66
~~~

Ở trường hợp `a`, `append(char)` in chữ `A` và `append(int)` nối ký hiệu số `1`. Ở trường hợp `b`, phép cộng `'A' + 1` thực hiện **trước khi gọi `append`**, tạo số nguyên `66`, nên overload `append(int)` hiển thị `66`.

Dùng hai lời gọi riêng khi muốn nối ký tự và số, dùng phép cộng rõ ràng khi muốn tính toán:

~~~java
String message = new StringBuilder()
        .append("Result: ")
        .append(1 + 2)
        .toString();
// Result: 3
~~~

Với vài phần cố định, `"Result: " + (1 + 2)` còn ngắn hơn. Không thay `+` bằng `StringBuilder` chỉ để tránh bẫy thứ tự phép toán: cả hai đều cần thể hiện đúng ý định.

## 7. Chương trình Java 17+ chạy hoàn chỉnh

Tạo tệp **`StringPitfallsDemo.java`**:

~~~java
import java.util.Objects;

public class StringPitfallsDemo {
    static String classify(Object value) {
        return "Object";
    }

    static String classify(String value) {
        return "String";
    }

    static String inspect(String value) {
        return "String";
    }

    static String inspect(Integer value) {
        return "Integer";
    }

    public static void main(String[] args) {
        String missing = null;
        System.out.println("plus null: " + ("id=" + missing));
        System.out.println("safe equals: " + "ok".equals(missing));
        System.out.println("both null: " + Objects.equals(null, null));

        System.out.println(
                "valueOf(Object): " + String.valueOf((Object) null));

        try {
            String.valueOf((char[]) null);
        } catch (NullPointerException ex) {
            System.out.println("valueOf(char[]): NPE");
        }

        System.out.println("overload(null): " + classify(null));
        System.out.println(
                "overload((Object)null): " + classify((Object) null));
        System.out.println(
                "inspect((String)null): " + inspect((String) null));

        // Không bỏ comment: đây là lỗi overload mơ hồ khi biên dịch.
        // inspect(null);
        // System.out.println(null);
        // new StringBuilder().append(null);

        String literal = "java";
        String created = new String("java");
        System.out.println("literal == new: " + (literal == created));
        System.out.println(
                "literal equals new: " + literal.equals(created));
        System.out.println(
                "intern identity: " + (literal == created.intern()));

        String folded = "ja" + "va";
        String part = "ja";
        String runtime = part + "va";
        System.out.println("folded identity: " + (literal == folded));
        System.out.println("runtime identity: " + (literal == runtime));
        System.out.println(
                "runtime equality: " + literal.equals(runtime));

        System.out.println("numeric first: " + (1 + 2 + "3"));
        System.out.println("string first: " + ("1" + 2 + 3));
        System.out.println("grouped: " + ("1" + (2 + 3)));
        System.out.println("char plus int: " + ('A' + 1));
        System.out.println("char then string: " + ("A" + 1));

        StringBuilder builder = new StringBuilder();
        builder.append('A').append(1);
        System.out.println("builder: " + builder);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac StringPitfallsDemo.java
java StringPitfallsDemo
~~~

Đầu ra:

~~~text
plus null: id=null
safe equals: false
both null: true
valueOf(Object): null
valueOf(char[]): NPE
overload(null): String
overload((Object)null): Object
inspect((String)null): String
literal == new: false
literal equals new: true
intern identity: true
folded identity: true
runtime identity: false
runtime equality: true
numeric first: 33
string first: 123
grouped: 15
char plus int: 66
char then string: A1
builder: A1
~~~

### Vì sao kết quả như vậy?

- `missing` là tham chiếu null, được nối thành chữ `null`; nhưng `"ok".equals(missing)` vẫn là `false` và không gọi phương thức trên null.
- `Objects.equals(null, null)` so sánh hai giá trị null nên cho `true`.
- `String.valueOf((Object) null)` tạo chuỗi `"null"`, trong khi `String.valueOf((char[]) null)` ném `NullPointerException` vì overload mảng.
- `classify(null)` chọn overload `String` là overload cụ thể hơn `Object`; ép kiểu `(Object) null` chuyển lựa chọn sang overload `Object`.
- `inspect(null)` được để ở dạng comment vì `String` và `Integer` không có quan hệ kiểu cụ thể hơn; thêm cast chọn overload được mong muốn.
- Literal, biểu thức nối hằng và `intern()` liên hệ đến cùng tham chiếu pool; đối tượng `new String(...)` và kết quả nối runtime là các đối tượng khác trong ví dụ này, dù `equals()` đều so nội dung thành `true`.
- Các biểu thức `+` cho `33`, `123` và `15` tương ứng với nhóm phép cộng/nối.
- `'A' + 1` cho số 66; `"A" + 1` cho chuỗi `A1`; `append('A').append(1)` cũng cho `A1`.

## 8. Bài tập dự đoán và tự kiểm tra

Hãy ghi dự đoán **giá trị**, **kiểu kết quả** và liệu lỗi xảy ra **lúc biên dịch** hay **lúc chạy** trước khi xem đáp án.

### Bài 1: null và equals

~~~java
String x = null;
System.out.println("x=" + x);
System.out.println("go".equals(x));
System.out.println(x == null);
~~~

**Đáp án:** lần lượt `x=null`, `false`, `true`. `"go".equals(x)` gọi trên literal không null, còn phép `== null` kiểm tra tham chiếu.

### Bài 2: null và overload

~~~java
// A
String.valueOf((Object) null);

// B
String.valueOf((char[]) null);

// C
String.valueOf(null);
~~~

**Đáp án:** A trả `"null"`; B ném `NullPointerException`; C **biên dịch được nhưng cũng ném `NullPointerException`** vì chọn overload nhận `char[]`. Đây là trường hợp dễ nhầm giữa lời gọi `valueOf(Object)` và `valueOf(char[])`.

### Bài 3: overload mơ hồ

~~~java
static void f(String x) {}
static void f(Integer x) {}

// f(null);
~~~

**Đáp án:** `f(null)` không biên dịch vì cả hai overload áp dụng và không overload nào cụ thể hơn. `f((String) null)` và `f((Integer) null)` hợp lệ về mặt lựa chọn overload.

### Bài 4: so sánh String

~~~java
String x = "ab";
String y = "a" + "b";
String p = "a";
String z = p + "b";

System.out.println(x == y);
System.out.println(x == z);
System.out.println(x.equals(z));
~~~

**Đáp án:** `true`, `false`, `true`. `y` là biểu thức hằng; `z` được tạo bằng nối lúc chạy.

### Bài 5: cộng số hay nối chuỗi?

~~~java
System.out.println(10 + 5 + " USD");
System.out.println("USD " + 10 + 5);
System.out.println("USD " + (10 + 5));
~~~

**Đáp án:** `15 USD`, `USD 105`, `USD 15`. Dấu ngoặc quyết định hai số được cộng trước hay lần lượt chuyển thành chuỗi.

### Bài 6: char và StringBuilder

~~~java
System.out.println('0' + 1);

StringBuilder sb = new StringBuilder();
sb.append('0').append(1);
System.out.println(sb);
~~~

**Đáp án:** `49` và `01`. `'0'` có mã số 48; `'0' + 1` là cộng số. Hai lần `append` lần lượt thêm ký tự và chữ số.

### Bài 7: hai kiểu lỗi khác nhau

~~~java
// System.out.println(null);     // A
// System.out.println((char[]) null); // B
~~~

**Đáp án:** A không biên dịch vì mơ hồ giữa overload `String` và `char[]`. B chọn rõ overload `char[]` nhưng ném `NullPointerException` lúc chạy. Ép kiểu xử lý **lựa chọn overload**, không đảm bảo truyền null là hợp lệ.

### Bài 8: final và biểu thức hằng

~~~java
final String a = "ja";
String x = a + "va";
String y = "java";
System.out.println(x == y);
~~~

**Đáp án:** `true`. `a` là biến hằng compile-time của kiểu `String`, được gán bằng biểu thức hằng; nối với `"va"` cũng là biểu thức hằng. Không áp dụng quy tắc này cho mọi biến `final` mà giá trị chỉ được biết lúc chạy.

## 9. Phân biệt compile-time error, runtime error và lỗi logic

| Tình huống | Kết quả | Nguyên nhân |
|---|---|---|
| `String.valueOf(null)` | `NullPointerException` khi chạy | Chọn overload `char[]` cụ thể hơn `Object` |
| `String.valueOf((Object) null)` | Chuỗi `"null"` | Chọn overload `Object` |
| `System.out.println(null)` | Lỗi biên dịch | Overload `String` và `char[]` mơ hồ |
| `new StringBuilder().append(null)` | Lỗi biên dịch | Các overload kiểu tham chiếu không có lựa chọn cụ thể duy nhất |
| `missing.length()` khi `missing == null` | `NullPointerException` | Gọi phương thức trên tham chiếu null |
| `new String("java") == "java"` | `false` | Hai đối tượng khác nhau |
| `new String("java").equals("java")` | `true` | Nội dung bằng nhau |
| `"Total: " + 10 + 5` | `"Total: 105"` | Nối chuỗi từ trái sang phải |
| `"Total: " + (10 + 5)` | `"Total: 15"` | Cộng số trong ngoặc trước |
| `'A' + 1` | `int` 66 | Phép cộng số và numeric promotion |

Điều quan trọng nhất khi gỡ lỗi là phân loại đúng:

- **Lỗi biên dịch:** JVM chưa chạy chương trình; thay đổi cast/chữ ký để compiler biết overload nào được gọi.
- **Ngoại lệ lúc chạy:** code đã biên dịch nhưng một tham chiếu null bị sử dụng theo cách không hợp lệ; kiểm tra dữ liệu và contract.
- **Sai logic:** code chạy bình thường nhưng kết quả không đúng yêu cầu, chẳng hạn dùng `==` so nội dung hay quên ngoặc khi cộng số.

Đừng dùng `try/catch` để “sửa” một lỗi overload mơ hồ: lỗi đó xảy ra trước khi chương trình có thể chạy.

## 10. Cách viết mã an toàn trong dự án thực tế

### 10.1. So sánh nội dung bằng equals

~~~java
boolean accepted = "OK".equals(status);
boolean same = java.util.Objects.equals(firstName, secondName);
~~~

Cách viết giảm rủi ro `NullPointerException` do gọi `equals` trên tham chiếu có thể null. Để kiểm tra cùng tham chiếu cho mục đích nhận diện đối tượng, `==` vẫn phù hợp; vấn đề là không dùng nhầm nó như phép so sánh nội dung chuỗi.

### 10.2. Giải quyết null theo yêu cầu nghiệp vụ

~~~java
String displayName = java.util.Objects.toString(userName, "(ẩn danh)");
~~~

Nếu null là dữ liệu thiếu cần thông báo, truyền giá trị thay thế có ý nghĩa. Nếu đầu vào bắt buộc không null, xác thực ở ranh giới nhận dữ liệu, ví dụ dùng `Objects.requireNonNull` với thông báo rõ ràng.

### 10.3. Làm rõ overload bằng kiểu và tên

Khi overload có thể dẫn đến mơ hồ với literal null, tốt hơn nên thiết kế API với tên phương thức khác nhau cho hai hành vi khác nhau nếu điều đó giúp mã dễ hiểu:

~~~java
static String fromText(String text) {
    return text == null ? "" : text;
}

static String fromNumber(Integer number) {
    return number == null ? "" : number.toString();
}
~~~

Nếu vẫn sử dụng overload, truyền biến có kiểu tĩnh rõ ràng hoặc cast phù hợp; không dùng cast tùy tiện chỉ để làm mất cảnh báo biên dịch.

### 10.4. Viết ngoặc rõ cho phép tính

~~~java
int quantity = 2;
int unitPrice = 50;

String total = "Total: " + (quantity * unitPrice);
~~~

Dù `*` có độ ưu tiên cao hơn `+`, dấu ngoặc vẫn giúp người đọc thấy phần tính toán riêng với phần nối chuỗi. Với phép cộng hỗn hợp, dấu ngoặc còn quyết định đúng/sai:

~~~java
String totalWithFee = "Total: " + (quantity * unitPrice + 10);
~~~

### 10.5. Hiểu intern mà không phụ thuộc intern

Không cần intern thủ công mọi chuỗi chỉ để so sánh:

~~~java
String source = new String("READY");
boolean sameContent = "READY".equals(source); // true
~~~

`equals` giữ đúng ngữ nghĩa kể cả khi chuỗi đến từ file, HTTP, database, đối tượng mới hoặc string pool. Nếu có yêu cầu tối ưu bộ nhớ cho nhiều chuỗi trùng lặp, cần đo và thiết kế riêng; không biến giả định về tham chiếu pooled thành logic nghiệp vụ.

## 11. Checklist gỡ lỗi khi kết quả gây bất ngờ

Khi gặp một biểu thức String trong Java:

1. **Xác định kiểu của từng biểu thức ở compile time.** Với null, hỏi overload nào hợp lệ và overload nào cụ thể nhất.
2. **Xác định null là giá trị dữ liệu hay tham chiếu không có đối tượng.** Phép nối `+`, `String.valueOf` và lời gọi phương thức có thể xử lý khác nhau.
3. **Hỏi đang so sánh tham chiếu hay nội dung.** `==` là nhận diện tham chiếu, `equals` là so sánh nội dung.
4. **Xác định chuỗi được tạo khi nào.** Literal/biểu thức hằng và nối runtime khác nhau về quy tắc intern.
5. **Thêm ngoặc để nhìn rõ phép cộng.** Một toán hạng String làm thay đổi ý nghĩa `+` thành nối chuỗi.
6. **Đọc compiler error trước khi sửa.** Ambiguous overload là lỗi chọn chữ ký, khác hoàn toàn NullPointerException.
7. **Chạy một chương trình tối thiểu có đầu ra xác định.** Kiểm tra cả giá trị, kiểu và trường hợp lỗi; không suy luận chỉ từ tên API.
8. **Viết test bảo vệ hành vi.** Các giá trị null, chuỗi rỗng, literal/new String, compile-time constant, số và char là những tình huống nên có ví dụ kiểm thử tương ứng.

## 12. Ghi nhớ

Bốn nhóm bẫy có chung nguyên nhân: **cùng một cách viết gần giống nhau nhưng Java áp dụng quy tắc khác theo kiểu, ngữ cảnh và thời điểm tạo giá trị**.

- `null` trong phép nối có thể thành chữ `"null"`; gọi phương thức trên null có thể ném `NullPointerException`.
- Overload được xác định theo **kiểu đối số lúc biên dịch**; cast làm rõ lựa chọn nhưng không tự sửa dữ liệu null.
- `intern()` và constant folding liên quan đến **đối tượng được chia sẻ**; để kiểm tra nội dung String, dùng `equals()`.
- `+` vừa cộng số vừa nối chuỗi; thứ tự trái sang phải và dấu ngoặc quyết định kết quả.

Cách học hiệu quả nhất là tự đoán **biên dịch được không, nếu chạy sẽ in gì và vì sao**, rồi đối chiếu với các ví dụ Java 17+ trước khi áp dụng quy tắc vào chương trình lớn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-practice-008">Đối chiếu hiệu năng các cách ghép chuỗi trên cùng dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Đối chiếu hiệu năng các cách ghép chuỗi trên cùng dữ liệu trong Java

## 1. Mục tiêu thực hành

Trong Java, có nhiều cách tạo một chuỗi từ nhiều phần: toán tử `+`/`+=`, `StringBuilder`, `StringJoiner`, `String.join` và `Collectors.joining`. Những API này có cùng khả năng tạo kết quả văn bản, nhưng **số lần tạo đối tượng, thao tác sao chép, lượng cấp phát, overhead của luồng xử lý và khả năng tối ưu của JVM** có thể khác nhau.

Một bài so sánh đáng tin cần trả lời **hai câu hỏi tách biệt**:

1. **Đúng chức năng không?** Các cách ghép phải tạo *cùng một chuỗi*, cùng dấu phân cách, cùng thứ tự, xử lý giống nhau với mảng rỗng/một phần tử và các giá trị đầu vào đã định nghĩa.
2. **Hiệu năng có khác không?** Đo nhiều lần trong môi trường kiểm soát được, trên cùng dữ liệu, cùng công việc và cùng điều kiện chạy; phân biệt thời gian với lượng cấp phát.

**Mục tiêu bài này:** xây dựng bộ dữ liệu tái lập được, triển khai sáu cách ghép, xác nhận kết quả đồng nhất, sau đó đưa vào benchmark JMH để tự đối chiếu. Không có một con số “`StringBuilder` luôn nhanh hơn X lần” đúng cho mọi Java/JVM/kích thước dữ liệu.

## 2. Chọn đúng bài toán chung trước khi so sánh

Ta có mảng `String[] values` không chứa `null`, gồm các phần tử có định dạng `v0_0`, `v1_1`, ... Đầu ra mong muốn là:

```text
values = ["v0_0", "v1_1", "v2_2"]
result = "v0_0|v1_1|v2_2"
```

Quy tắc cố định:

- Dùng dấu `|` **chỉ giữa hai phần tử**; không có dấu phân cách ở đầu hoặc cuối.
- Giữ đúng thứ tự ban đầu và toàn bộ ký tự của từng phần tử.
- Mảng rỗng cho `""`; mảng một phần tử cho chính phần tử đó.
- Bài thực hành dùng phần tử **không null**; cách xử lý `null` trong dữ liệu thật phải được quy định riêng, không được để một phương pháp có chính sách khác mà vẫn tính là “cùng công việc”.
- Không tính thời gian **sinh mảng dữ liệu** vào thời gian ghép. Với `StringBuilder` có dung lượng ước tính, phải nói rõ chi phí tính dung lượng được tính trong bài thử chức năng hay được chuẩn bị trước trong benchmark.

Đầu vào được tạo theo công thức xác định, không lấy ngẫu nhiên trong đoạn mã đo thời gian:

```java
static String[] input(int size) {
    String[] values = new String[size];
    for (int i = 0; i < size; i++) {
        values[i] = "v" + i + "_" + (i % 17);
    }
    return values;
}
```

Đây là phương thức minh họa, được đưa vào chương trình độc lập ở phần sau. Việc mọi phương pháp cùng đọc một mảng `String[]` giúp loại bỏ khác biệt do đọc file, truy vấn cơ sở dữ liệu, tạo chuỗi ngẫu nhiên hoặc chuẩn hóa dữ liệu.

## 3. Sáu phương pháp được đối chiếu

| Cách | Cơ chế chính | Điểm cần chú ý khi đo |
| --- | --- | --- |
| `+=` trong vòng lặp | Mỗi bước yêu cầu một giá trị `String` mới theo ngữ nghĩa nối | Có thể sao chép lại phần kết quả tích lũy qua nhiều bước |
| `StringBuilder` mặc định | Thêm nhiều đoạn vào bộ đệm chuỗi có thể thay đổi | Bộ đệm có thể mở rộng nhiều lần |
| `StringBuilder` có `capacity` | Chuẩn bị sức chứa dự kiến rồi `append` | Cần biết/ước tính đúng dung lượng; tính toán capacity cũng có chi phí |
| `StringJoiner` | Thêm các phần tử với delimiter do API quản lý | Khớp trực tiếp bài toán “nối có phân cách” |
| `String.join` | API tiện lợi trên mảng/`Iterable` | Có thể có overhead riêng tùy JVM và triển khai |
| `Arrays.stream(...).collect(Collectors.joining(...))` | Đường ống stream, collector ghép chuỗi | Có thể phát sinh thêm chi phí tạo và vận hành stream |

**Quan trọng:** `StringBuilder` là **mutable**, còn `String` là **immutable**. Nhưng từ bytecode `invokedynamic` (trên `javac` Java 9+) không thể suy ra chính xác lượng cấp phát của mọi phép `+`. JVM có thể tối ưu mạnh trong các ngữ cảnh nhất định; cần benchmark thay vì kết luận hoàn toàn bằng trực giác.

Trong một biểu thức ngắn như `"id=" + id + ",name=" + name`, compiler hiện đại thường có khả năng tối ưu tốt. **`+=` lặp nhiều lần để tăng một chuỗi tích lũy** là bài toán khác, thường có nguy cơ lặp lại công việc sao chép khi kích thước kết quả tăng.

## 4. Chương trình Java 17+ xác minh tất cả phương pháp cho cùng kết quả

Tạo tệp `ConcatCases.java`:

```java
import java.util.Arrays;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public class ConcatCases {
    static String plusLoop(String[] values) {
        String result = "";
        for (int i = 0; i < values.length; i++) {
            if (i > 0) result += "|";
            result += values[i];
        }
        return result;
    }

    static String builder(String[] values) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) out.append('|');
            out.append(values[i]);
        }
        return out.toString();
    }

    static String builderSized(String[] values) {
        int capacity = Math.max(0, values.length - 1);
        for (String value : values) capacity += value.length();
        StringBuilder out = new StringBuilder(capacity);
        for (int i = 0; i < values.length; i++) {
            if (i > 0) out.append('|');
            out.append(values[i]);
        }
        return out.toString();
    }

    static String joiner(String[] values) {
        StringJoiner out = new StringJoiner("|");
        for (String value : values) out.add(value);
        return out.toString();
    }

    static String joining(String[] values) {
        return String.join("|", values);
    }

    static String stream(String[] values) {
        return Arrays.stream(values).collect(Collectors.joining("|"));
    }

    static String[] input(int size) {
        String[] values = new String[size];
        for (int i = 0; i < size; i++) values[i] = "v" + i + "_" + (i % 17);
        return values;
    }

    public static void main(String[] args) {
        for (int n : new int[] {0, 1, 8, 128, 1024}) {
            String[] data = input(n);
            String expected = String.join("|", data);
            String[] actual = {
                plusLoop(data), builder(data), builderSized(data),
                joiner(data), joining(data), stream(data)
            };
            for (String candidate : actual) {
                if (!expected.equals(candidate)) {
                    throw new AssertionError("Different output at n=" + n);
                }
            }
            System.out.println("n=" + n + " methods=6 identical=true chars="
                    + expected.length());
        }
    }
}
```

Chạy tại thư mục chứa file:

```bash
javac --release 17 -encoding UTF-8 ConcatCases.java
java ConcatCases
```

Kết quả **đã kiểm chứng bằng biên dịch và thực thi**, không phải số liệu benchmark:

```text
n=0 methods=6 identical=true chars=0
n=1 methods=6 identical=true chars=4
n=8 methods=6 identical=true chars=39
n=128 methods=6 identical=true chars=834
n=1024 methods=6 identical=true chars=7501
```

Chương trình báo `methods=6 identical=true` vì **sáu cách đều cho giá trị `String` khớp từng ký tự** với kết quả chuẩn ở từng kích thước. Điều này loại được lỗi phổ biến như dấu `|` thừa ở cuối, sai thứ tự phần tử hoặc bỏ qua phần tử đầu tiên. Đây là *kiểm tra chức năng*, chưa phải chứng cứ cách nào nhanh nhất.

### Vì sao phải kiểm tra cả `n = 0` và `n = 1`?

- `n=0` có kết quả `""`. Một hàm ghép sai có thể tự thêm delimiter dù không có phần tử.
- `n=1` không được thêm bất cứ delimiter nào. Một số cách triển khai ghép thủ công dễ để thừa dấu `|` ở đầu/cuối.
- `n=8` giúp quan sát khối dữ liệu nhỏ thường gặp trong biểu mẫu, thông báo hoặc một dòng log.
- `n=128` và `n=1024` kiểm tra khi kết quả dài dần, phát hiện chi phí tích lũy và thay đổi cấu trúc bộ đệm.

Khi dữ liệu thực tế có `null`, Unicode dài, dấu phân cách bên trong giá trị hay giới hạn chiều dài, cần thêm lớp test tương ứng và **thống nhất kết quả mong muốn** giữa các phương pháp trước khi đo.

## 5. Vì sao kết quả nối đúng chưa đủ để so hiệu năng?

Một cách ghép có thể cho cùng kết quả nhưng:

- Tốn nhiều CPU hơn để sao chép đoạn kết quả tích lũy.
- Tạo nhiều đối tượng trung gian hoặc bộ đệm hơn, làm tăng áp lực garbage collector (GC).
- Phải mở rộng dung lượng bộ đệm theo từng giai đoạn.
- Có overhead do tạo stream, collector hoặc lớp điều phối API.
- Được JIT tối ưu khác nhau sau khi chạy nóng; cách đo quá ngắn dễ đánh giá sai.

Có thể hình dung với `n` đoạn có độ dài tương tự, nếu mỗi bước nối `+=` phải sao chép lại toàn bộ nội dung đã tích lũy, lượng dữ liệu cần sao chép có thể tăng theo tổng:

\[
1 + 2 + \cdots + n = \frac{n(n+1)}{2}
\]

Đây là giải thích trực quan cho nguy cơ tăng **xấp xỉ bậc hai theo số phần tử** trong cách nối tích lũy ngây thơ, khi độ dài mỗi phần tử tương tự nhau. Nó **không phải một cam kết về độ phức tạp thực tế của mọi trình biên dịch và JVM**: compiler/JIT có thể thực hiện một số tối ưu, đặc biệt trong ngữ cảnh xác định được.

Trong khi đó, `StringBuilder` có thể tích lũy vào một vùng đệm rồi tạo `String` kết quả, nên thường hạn chế việc tạo lại toàn bộ chuỗi qua từng bước. Khi đặt `capacity` đủ lớn trước, nó còn tránh một số lần mở rộng buffer. Nhưng nếu phải quét tất cả giá trị để tính độ dài, lần quét đó cũng là **chi phí**; không nên tính toàn bộ chi phí cho một phương pháp mà lại loại khỏi phương pháp khác khi mục tiêu là so tổng chi phí công việc từ đầu đến cuối.

## 6. Đo có kiểm soát với JMH

Để so hiệu năng microbenchmark trong Java, nên dùng **JMH (Java Microbenchmark Harness)** thay vì lấy hiệu `System.nanoTime()` quanh một lần nối. JMH hỗ trợ warmup, nhiều phép đo, fork JVM và tiêu thụ kết quả hợp lý để giảm tác động của dead-code elimination và tối ưu không mong muốn.

### Tạo dự án JMH

Cần cài JDK và Maven, dùng archetype JMH (ví dụ phiên bản `1.37`):

```bash
mvn archetype:generate -DinteractiveMode=false \
  -DarchetypeGroupId=org.openjdk.jmh \
  -DarchetypeArtifactId=jmh-java-benchmark-archetype \
  -DarchetypeVersion=1.37 \
  -DgroupId=org.example \
  -DartifactId=concat-bench \
  -Dversion=1.0
```

Khối lệnh trên dùng ký hiệu nối dòng `\` của shell POSIX (Linux/macOS). Trên PowerShell, có thể viết **một dòng duy nhất** với các tham số tương tự và bỏ dấu `\` nối dòng. Archetype thiết lập phần cần thiết để JMH chạy như một dự án Maven. Đặt class benchmark `ConcatBenchmark.java` trong `concat-bench/src/main/java/org/example/`, dùng package `org.example` như ví dụ sau.

### Class JMH benchmark đầy đủ

```java
package org.example;

import java.util.Arrays;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.openjdk.jmh.annotations.*;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class ConcatBenchmark {
    @Param({"8", "128", "1024"})
    public int n;

    private String[] data;
    private int exactCapacity;

    @Setup(Level.Trial)
    public void setup() {
        data = new String[n];
        int chars = Math.max(0, n - 1);
        for (int i = 0; i < n; i++) {
            data[i] = "v" + i + "_" + (i % 17);
            chars += data[i].length();
        }
        exactCapacity = chars;

        String expected = String.join("|", data);
        if (!expected.equals(plusLoop())
                || !expected.equals(builder())
                || !expected.equals(builderSized())
                || !expected.equals(joiner())
                || !expected.equals(joining())
                || !expected.equals(streamJoining())) {
            throw new IllegalStateException("Different concatenation outputs");
        }
    }

    @Benchmark
    public String plusLoop() {
        String result = "";
        for (int i = 0; i < data.length; i++) {
            if (i > 0) result += "|";
            result += data[i];
        }
        return result;
    }

    @Benchmark
    public String builder() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            if (i > 0) out.append('|');
            out.append(data[i]);
        }
        return out.toString();
    }

    @Benchmark
    public String builderSized() {
        StringBuilder out = new StringBuilder(exactCapacity);
        for (int i = 0; i < data.length; i++) {
            if (i > 0) out.append('|');
            out.append(data[i]);
        }
        return out.toString();
    }

    @Benchmark
    public String joiner() {
        StringJoiner out = new StringJoiner("|");
        for (String value : data) out.add(value);
        return out.toString();
    }

    @Benchmark
    public String joining() {
        return String.join("|", data);
    }

    @Benchmark
    public String streamJoining() {
        return Arrays.stream(data).collect(Collectors.joining("|"));
    }
}
```

Điểm thiết kế quan trọng của benchmark:

- `@State(Scope.Thread)` cung cấp dữ liệu cho từng thread benchmark.
- `@Param` chạy cả ba kích thước dữ liệu trong các trường hợp đo riêng.
- `@Setup(Level.Trial)` tạo mảng và tính `exactCapacity` **ngoài đoạn đo**. Nhờ vậy đo riêng chi phí nối, không lẫn việc tạo dữ liệu.
- `builderSized()` được dùng một dung lượng đã biết trước, phản ánh kịch bản có thể biết trước chiều dài kết quả. Đây là **lợi thế có điều kiện**: nếu ứng dụng thực phải quét mảng tính dung lượng, hãy tạo thêm benchmark end-to-end tính cả chi phí đó.
- Mỗi `@Benchmark` trả về `String` kết quả, giúp harness quan sát giá trị trả về và tránh một số hình thức loại bỏ phép tính khi kết quả không được dùng.
- `@Fork(2)` chạy ở hai JVM fork độc lập; `@Warmup` và `@Measurement` giảm sự nhiễu của giai đoạn khởi động JIT.
- Không in console, đọc file, sinh mảng hoặc tạo dữ liệu ngẫu nhiên trong phương thức benchmark, vì đó không phải chi phí đang cần đo.

### Chạy JMH

Từ thư mục dự án `concat-bench`:

```bash
mvn clean verify
java -jar target/benchmarks.jar ConcatBenchmark
```

Mỗi combination giữa kích thước và phương pháp sẽ cho thống kê thời gian riêng. Các tùy chọn `@Warmup`, `@Measurement` và `@Fork` ở trên tương đối tốn thời gian vì cần làm nóng và đo nhiều lượt; khi thử nhanh có thể giảm số lần bằng tùy chọn command line, rồi dùng cấu hình đầy đủ cho báo cáo chính thức.

Ví dụ chạy thêm **GC allocation profiling**:

```bash
java -jar target/benchmarks.jar ConcatBenchmark -prof gc
```

Tùy profiler/JDK, output có thể gồm `gc.alloc.rate.norm` (byte cấp phát chuẩn hóa trên mỗi operation) và các metric GC liên quan. Số liệu cấp phát có thể phản ánh tối ưu của JIT và độ lớn output; không nên suy ra tổng heap retained chỉ từ số byte cấp phát. Nếu profiler không khả dụng trong môi trường đang dùng, chỉ báo phần này chưa đo, không tự điền số.

**Không đưa số μs/op cố định vào bài học**: JMH phải chạy trên máy, JDK, heap, CPU, trạng thái GC và workload cụ thể. Một bảng “kết quả” điền số trước khi đo sẽ khiến người học tin vào thứ hạng không được chứng minh.

## 7. Cách trình bày bảng đối chiếu kết quả

Sau khi chạy, hãy ghi rõ ít nhất `java -version`, loại CPU, JMH version, JVM options, số fork, số lần warmup/measurement, dataset, độ dài kết quả và đơn vị đo. Dùng một bảng cho **cùng một `n`**:

| Phương pháp | Avg time (μs/op) | Error/CI | Alloc (B/op) nếu có | Ghi chú |
| --- | ---: | ---: | ---: | --- |
| `plusLoop` | Điền từ JMH | Điền từ JMH | Điền từ `-prof gc` | Nối tích lũy `+=` |
| `builder` | Điền từ JMH | Điền từ JMH | Điền từ `-prof gc` | Dung lượng mặc định |
| `builderSized` | Điền từ JMH | Điền từ JMH | Điền từ `-prof gc` | Dung lượng chuẩn bị sẵn |
| `joiner` | Điền từ JMH | Điền từ JMH | Điền từ `-prof gc` | API delimiter |
| `joining` | Điền từ JMH | Điền từ JMH | Điền từ `-prof gc` | API `String.join` |
| `streamJoining` | Điền từ JMH | Điền từ JMH | Điền từ `-prof gc` | Stream + collector |

Đây là **mẫu bảng kết quả cần đo**, không phải phép đo giả lập. Với chế độ `AverageTime`, số `μs/op` **thấp hơn** nghĩa là tốn ít thời gian hơn trong workload đo. Cột độ bất định hoặc error giúp tránh suy luận từ khác biệt rất nhỏ nằm trong sai số.

Khi so nhiều kích thước `n=8`, `128` và `1024`, không gộp tất cả thành một con số “trung bình” để xếp hạng. Xu hướng có thể đổi khi kích thước thay đổi: một cách ngắn gọn với chi phí khởi tạo nhỏ có thể phù hợp dữ liệu ít phần tử, trong khi cách tích lũy vào một buffer thường có lợi thế hơn ở workload dài.

### Ví dụ đọc kết quả cho đúng

Giả sử một kết quả thực nghiệm cụ thể cho thấy `builderSized` ít μs/op hơn `builder` khi `n=1024`, bạn có thể kết luận **trong benchmark đó**, dung lượng đã biết trước giúp giảm chi phí. Chưa thể kết luận toàn bộ ứng dụng sẽ nhanh hơn, vì thực tế có thể phải tính capacity, parse input, truy cập mạng hoặc ghi file.

Nếu `streamJoining` gần bằng `joining` trong cùng sai số, không đủ căn cứ gọi một cách nhanh hơn. Cũng đừng suy luận chỉ số `B/op` thấp luôn đồng nghĩa latency thấp; CPU, cấp phát, bộ nhớ và GC liên quan nhưng không phải cùng một đại lượng.

## 8. Những cái bẫy làm benchmark thiếu công bằng

1. **Không cùng đầu ra:** một phiên bản có delimiter cuối, một phiên bản không; hoặc một phiên bản bỏ qua phần tử rỗng. Phải so khớp kết quả trước khi đo.
2. **Tạo dữ liệu trong một benchmark nhưng tạo sẵn ở benchmark khác:** sẽ vô tình đo cả sinh dữ liệu, làm sai phép so.
3. **Một bên biết trước capacity, bên kia phải tự đi tính:** có thể là hai kịch bản hợp lệ, nhưng phải tách rõ benchmark đo **chỉ phép nối** với benchmark đo **tổng chi phí tạo output**.
4. **Dùng `System.nanoTime()` một lần rồi xếp hạng:** nhiễu từ JIT compilation, class loading, GC, scheduler và CPU có thể lớn hơn sự khác biệt thực.
5. **Không tiêu thụ kết quả:** JVM có thể loại bỏ phép tính không ảnh hưởng đến hành vi quan sát được. JMH hỗ trợ return value hoặc `Blackhole`.
6. **Để console I/O trong đoạn đo:** lệnh `System.out.println` có chi phí lớn, làm phép so không còn tập trung vào nối chuỗi.
7. **Benchmark trên mảng quá ngắn hoặc quá dài mà không đại diện ứng dụng:** chỉ một kích thước chưa đủ để rút ra quy luật.
8. **Lẫn độ dài String và byte UTF-8:** `String.length()` đo UTF-16 code units; số byte UTF-8 thay đổi theo nội dung. Nếu workload có Unicode thực tế, tạo tập dữ liệu tương ứng.
9. **Chỉ nhìn trung bình mà bỏ sai số và GC:** cần quan sát độ biến thiên, allocation/GC và lặp lại với cấu hình hợp lý.
10. **Thay một biểu thức `+` ngắn thành nối trong vòng lặp:** đó là hai workload khác nhau. Không dùng benchmark `+=` trong vòng lặp để kết luận mọi toán tử `+` đều chậm.
11. **Quên cấu hình JVM/JDK:** compiler phát bytecode và JIT runtime ảnh hưởng lớn. Nên ghi version của cả JDK biên dịch và JDK chạy benchmark.
12. **Kết luận từ một lần chạy trên máy đang tải nặng:** chạy các fork độc lập, ưu tiên môi trường ổn định, báo rõ giới hạn khi có nhiễu.

### Đối chiếu cả đầu ra lẫn độ phức tạp

Khi kết quả tương đương, hãy hỏi thêm về chuỗi kết quả **dài thêm như thế nào**. Một vòng lặp `+=` với `n` giá trị càng dài có thể khiến lượng nội dung trung gian cần sao chép tăng mạnh. Với `StringBuilder`, nhiều `append` thường làm việc trên một vùng đệm có thể tăng kích thước; do đó mức tăng chi phí thường dễ kiểm soát hơn. Nhưng những mô tả này là **mô hình giải thích**, không thay cho số liệu JMH của ứng dụng đang sử dụng.

## 9. Nên chọn cách nào trong mã Java thật?

- **Nối vài biểu thức cố định:** ưu tiên `+` vì dễ đọc. `javac` Java 9+ thường biểu diễn nhiều phép nối động qua `invokedynamic` và `StringConcatFactory`; không tự chuyển sang `StringBuilder` nếu chưa có lý do.
- **Tích lũy nhiều phần trong vòng lặp:** `StringBuilder` là lựa chọn rõ ràng, tránh phụ thuộc vào việc JVM nhận diện và tối ưu được phép `+=` lặp.
- **Có delimiter và danh sách/mảng sẵn:** `String.join(delimiter, values)` rất dễ đọc; `StringJoiner` phù hợp khi thêm từng phần tử theo quá trình xử lý.
- **Đang ở pipeline stream:** `Collectors.joining(delimiter)` tích hợp tự nhiên; cần đo nếu đoạn này là hot path, đặc biệt khi chỉ nối các phần tử mà không có bước biến đổi stream khác.
- **Đã biết tương đối chính xác độ dài kết quả:** có thể đặt capacity cho `StringBuilder` để giảm mở rộng bộ đệm; tính luôn chi phí chuẩn bị capacity khi đánh giá end-to-end.
- **Dữ liệu rất lớn:** cân nhắc streaming output trực tiếp vào `Writer`/`OutputStream` theo charset phù hợp nếu không bắt buộc tạo toàn bộ `String` trong bộ nhớ. Đây là bài toán khác với trả về một chuỗi duy nhất và phải benchmark riêng.

Người đọc mã nên ưu tiên **đúng ngữ nghĩa, dễ bảo trì, rồi đo các đoạn thực sự là bottleneck**. JMH giúp nhìn rõ ảnh hưởng của từng kỹ thuật, nhưng một tối ưu nhỏ của nối chuỗi không cứu được thiết kế vốn tốn phần lớn thời gian ở I/O, truy vấn hoặc xử lý khác.

## 10. Checklist hoàn thành bài thực hành

- Sáu cách đều nhận **cùng mảng dữ liệu** và tạo chính xác một chuỗi có `|` giữa các phần tử.
- Đã xác nhận `n=0`, `1`, `8`, `128`, `1024` cho cùng nội dung, không chỉ cùng độ dài.
- Dataset được tạo ngoài đoạn đo JMH; mọi thử nghiệm có chung logic tạo phần tử.
- Có các lần warmup, measurement, fork JVM và phương thức tiêu thụ kết quả.
- Báo cáo riêng từng kích thước, đơn vị `μs/op`, độ bất định và nếu có thì `B/op`.
- Phân biệt benchmark `builderSized` có capacity biết trước với kịch bản phải tính capacity trong mỗi lần xử lý.
- Tránh suy luận hiệu năng của mọi biểu thức `+` từ benchmark `+=` trong vòng lặp.
- Chỉ kết luận nhanh/chậm theo **bằng chứng đo thực tế**, có môi trường và giới hạn rõ ràng.

**Ghi nhớ:** một phép so sánh hiệu năng ghép chuỗi chỉ có ý nghĩa khi **sáu phương pháp cho đầu ra giống nhau trước khi đo**. Với công việc nối nhiều phần, `StringBuilder` và các API nối có delimiter là lựa chọn thực tế cần đối chiếu; `+=` trong vòng lặp có nguy cơ tốn chi phí sao chép đáng kể. Thứ hạng và mức chênh lệch cuối cùng phải được xác nhận bằng benchmark đúng cách trên workload phù hợp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-practice-009">Lập bảng lựa chọn API và tự đánh giá các tình huống xử lý chuỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Lập bảng lựa chọn API và tự đánh giá các tình huống xử lý chuỗi Java

## 1. Mục tiêu thực hành

Trong Java, cùng một tác vụ xử lý văn bản có thể thực hiện bằng nhiều API: `String`, `StringBuilder`, `StringBuffer`, `CharSequence`, `String.split`, `Pattern`/`Matcher`, `String.lines`, `Stream`, `Charset`, `Normalizer`, `Comparator`... Chọn đúng API phụ thuộc vào **yêu cầu thực tế**, không chỉ vào việc viết ít dòng code.

Sau bài này, bạn cần có khả năng:

1. Chuyển một yêu cầu xử lý chuỗi thành các tiêu chí: có dùng regex không, cần giữ dữ liệu rỗng không, yêu cầu Unicode ở mức nào, có thay đổi/ghép nhiều lần không, có lưu làm khóa không, có lưu lâu hay chia sẻ giữa luồng không.
2. Dùng **bảng quyết định** để chọn API phù hợp và giải thích vì sao các lựa chọn khác có thể sai.
3. Tự giải các tình huống về tách chuỗi, tìm/thay, xây dựng văn bản, Unicode, kiểm tra bằng nhau, sắp xếp, đọc dòng và mã hóa.
4. Chạy chương trình Java 17+ có kiểm tra kết quả thật, sau đó tự chấm bằng đáp án và thang đánh giá.

Đây là bài thực hành tổng hợp theo ngữ cảnh **Java Core — String**. Các đoạn minh họa ngắn trong bảng là biểu thức hoặc mảnh code đặt trong phương thức Java; chương trình hoàn chỉnh nằm ở phần 5.

## 2. Trước khi lựa chọn: xác định hợp đồng xử lý

Hãy trả lời các câu hỏi sau trước khi chọn API:

- **Tìm nguyên văn hay theo mẫu?** Ví dụ dấu chấm trong dữ liệu là dấu chấm thật hay wildcard trong regex?
- **Chỉ cần đọc, hay tạo kết quả mới nhiều lần?** Một phép `replace` trả `String` mới có thể đủ; nối nhiều đoạn trong vòng lặp phù hợp với `StringBuilder`.
- **Đối tượng có mutable không?** `CharSequence` có thể là `StringBuilder` hoặc view của `CharBuffer`; muốn lưu giá trị ổn định phải định nghĩa snapshot.
- **Thế nào là “một ký tự”?** UTF-16 code unit, Unicode code point hay cụm grapheme hiển thị? `String.length()` không luôn là số ký tự người dùng nhìn thấy.
- **Chuỗi rỗng, null, khoảng trắng và xuống dòng có ý nghĩa không?** Ví dụ CSV giản lược cần giữ trường rỗng cuối thì dùng `split(delimiter, -1)`.
- **Bình đẳng và thứ tự có cùng quy tắc không?** `equals` phân biệt hoa/thường, trong khi `CASE_INSENSITIVE_ORDER` có thể coi hai chuỗi khác nhau là cùng khóa trong `TreeSet`.
- **Tập ký tự và locale là gì?** Mã hóa bytes cần nêu `Charset`; so sánh/định dạng theo ngôn ngữ có thể cần `Locale` và `Collator`.

Khi đề chưa nói rõ một điều kiện quan trọng, hãy **đặt giả định tường minh** hoặc hỏi người thiết kế yêu cầu. Ví dụ “tách dữ liệu CSV” không đủ để kết luận có thể dùng `split(",")` vì CSV đầy đủ có dấu nháy kép, dấu phẩy bên trong trường và quy tắc escape.

## 3. Bảng lựa chọn API theo nhiệm vụ

| Nhu cầu cụ thể | API nên cân nhắc | Điều kiện và điểm dễ sai |
| --- | --- | --- |
| Giữ một giá trị văn bản ổn định | `String` | Immutable; phù hợp trả về, cache, khóa map; `StringBuilder`/`CharSequence` không tự bất biến |
| Ghép nhiều mảnh trong **một luồng xử lý** | `StringBuilder.append(...)`, `toString()` | Tránh tạo chuỗi trung gian liên tiếp; builder mutable, không thread-safe |
| Ghép trên đối tượng có nhiều phương thức đồng bộ hóa | `StringBuffer` | Có đồng bộ hóa từng phương thức; không thay cho giao thức đồng bộ toàn thuật toán nhiều bước |
| API chỉ đọc văn bản từ nhiều implementation | `CharSequence` | Không bảo đảm immutable hay `equals` theo nội dung; snapshot bằng `toString()` khi cần lưu ổn định |
| So sánh chính xác hai `String` | `equals`, `Objects.equals` khi có null | Phân biệt hoa/thường; `Objects.equals` không biến mọi `CharSequence` thành so sánh nội dung |
| So sánh String với một `CharSequence` | `String.contentEquals(...)` | Thích hợp khi muốn so sánh ký tự hiện tại, vẫn phải xét thay đổi đồng thời |
| Bỏ qua hoa/thường với String | `equalsIgnoreCase`, `compareToIgnoreCase` | Không phải quy tắc collation theo mọi locale; không thay đổi `String.equals` |
| Sắp xếp bỏ qua hoa/thường | `String.CASE_INSENSITIVE_ORDER` | `compare == 0` có thể xảy ra khi `equals == false`; `TreeSet`/`TreeMap` coi đó là cùng khóa |
| Sắp xếp bỏ qua hoa/thường **nhưng giữ mọi String khác nhau** | `String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder())` | Tie-breaker làm comparator nhất quán với `String.equals` |
| So sánh theo ngôn ngữ cụ thể | `Collator.getInstance(locale)` | Cần cấu hình mức độ nhạy dấu/chữ hoa và kiểm thử ngôn ngữ mục tiêu |
| Tìm chuỗi con nguyên văn | `contains`, `indexOf`, `startsWith`, `endsWith` | Không diễn giải regex; `indexOf` trả `-1` nếu không tìm thấy |
| Lấy một đoạn có chỉ số UTF-16 | `substring(start, end)` | `start` bao gồm, `end` không bao gồm; kiểm tra giới hạn |
| Thay văn bản nguyên văn | `String.replace(target, replacement)` | Không cần escape dấu `.`, `$` hoặc `\` theo regex/replacement |
| Thay theo regex có chủ đích | `replaceAll(regex, replacement)`, `replaceFirst(...)` | Regex và replacement có **cú pháp khác nhau**; `$1` có thể tham chiếu nhóm |
| Thay bằng `replaceAll` nhưng dữ liệu hai phía là văn bản nguyên văn | `Pattern.quote(target)` và `Matcher.quoteReplacement(value)` | Quote **cả hai phía**; chỉ quote regex chưa bảo vệ replacement chứa `$` hoặc `\` |
| Kiểm tra regex nhiều lần trên nhiều đầu vào | `Pattern.compile(regex)`, `pattern.matcher(text)` | `Pattern` tái sử dụng được; `Matcher` có trạng thái, không chia sẻ tùy tiện giữa luồng |
| Tách đơn giản theo regex | `split(regex)` hoặc `split(regex, limit)` | `limit=0` bỏ token rỗng cuối; `-1` giữ chúng; regex có thể khớp zero-width |
| Phân tích **CSV đầy đủ** | Thư viện CSV chuyên dụng | `split(",")` không xử lý dấu phẩy trong trường được quote, dấu nháy escape hoặc newline trong trường |
| Ghép các phần với một separator | `String.join(separator, ...)`, `Collectors.joining(...)` | `joining` thuận tiện khi đã có stream; kiểm tra dữ liệu null theo hợp đồng |
| Tách các dòng văn bản | `String.lines()` | Nhận `\n`, `\r`, `\r\n`; không tự sinh dòng rỗng cuối chỉ vì có ký tự kết thúc dòng |
| Bỏ whitespace hai đầu | `strip()`, `stripLeading()`, `stripTrailing()` | Dựa theo `Character.isWhitespace`; khác `trim()` (các code unit ≤ U+0020) |
| Kiểm tra toàn khoảng trắng | `isBlank()` | Khác `isEmpty()`: chuỗi có space không rỗng nhưng có thể blank |
| Duyệt UTF-16 code unit | `chars()`, `charAt(...)` | Surrogate pair bị biểu diễn thành hai `char` |
| Duyệt Unicode code point | `codePoints()`, `codePointAt(...)` | Code point có thể cần hai code unit; dựng lại bằng `StringBuilder.appendCodePoint(...)` |
| Chuẩn hóa biểu diễn Unicode | `Normalizer.normalize(s, Form.NFC)` | Chuỗi hiển thị giống nhau có thể khác chuỗi code point; không tự thay cho so sánh theo ngôn ngữ |
| Chuyển bytes và String | `getBytes(StandardCharsets.UTF_8)`, `new String(bytes, StandardCharsets.UTF_8)` | Chỉ rõ charset ở cả hai chiều; đừng dựa vào charset mặc định để trao đổi dữ liệu |
| Tạo biểu diễn có tham số | `String.format(Locale,...)` hoặc `"..." .formatted(...)` | Cân nhắc locale khi có số/ngày; với text nối đơn giản `StringBuilder`/`join` có thể rõ ràng hơn |

Bảng không có nghĩa mỗi API chỉ phục vụ một loại bài toán; điều cần đánh giá là **hợp đồng đúng với dữ liệu thực tế**. Ví dụ `replaceAll` hoàn toàn dùng được để thay dấu chấm thật nếu regex escape đúng, nhưng khi không cần regex, `replace` ngắn và ít nguy cơ lỗi hơn.

## 4. Bảng tình huống để tự chọn API

Trước khi xem đáp án ở phần 6, hãy tự điền **API, lý do, rủi ro/kiểm thử** cho mỗi tình huống. Độ khó tăng dần.

| Mã | Tình huống | Câu hỏi cần tự trả lời |
| --- | --- | --- |
| T1 | Bạn có chuỗi `"a.b.c"`, muốn thay mọi **dấu chấm nguyên văn** thành dấu `/`. | Chọn hàm nào? Vì sao `replaceAll(".", "/")` không đúng? |
| T2 | Người dùng tìm từ khóa có dấu `[` hoặc `$`, thay bằng chuỗi chứa `$1` và backslash, nhưng tất cả đều là văn bản nguyên văn. Hệ thống bắt buộc dùng `replaceAll`. | Phải quote phía tìm kiếm và phía thay thế bằng hàm nào? |
| T3 | Chuỗi dữ liệu `"a,b,,"` biểu diễn 4 trường, trong đó hai trường cuối rỗng. | Dùng `split` với limit bao nhiêu? Có nên dùng làm parser CSV đầy đủ không? |
| T4 | Muốn loại emoji 🚀 khỏi `"A🚀B"` mà không làm hỏng surrogate pair. | Dùng `chars()` hay `codePoints()`? Dựng lại String thế nào? |
| T5 | Cần ghép hàng nghìn mảnh text theo vòng lặp ở một luồng. | Dùng phép `+` tích lũy hay `StringBuilder`? Vì sao? |
| T6 | API nhận `CharSequence` để lưu làm tiêu đề trong cache. Người gọi có thể truyền `StringBuilder` rồi sửa. | Phải giữ tham chiếu hay tạo `String` snapshot? |
| T7 | Cần so sánh `"Java"` và `"java"` như cùng một từ khóa, nhưng hệ thống khác cần phân biệt hai khóa theo `equals`. | Khi nào dùng `equalsIgnoreCase`, khi nào dùng `String.equals`? |
| T8 | Dùng `TreeSet` sắp xếp không phân biệt hoa/thường nhưng vẫn muốn giữ cả `"Java"` và `"java"`. | Cần thêm comparator phụ nào? |
| T9 | Nhập hai giá trị `"é"` và `"e"` + dấu sắc kết hợp; cần chuẩn hóa dạng lưu trước khi so sánh chính xác. | Chọn công cụ nào? Có nên chỉ gọi `toLowerCase()` không? |
| T10 | Gửi văn bản tiếng Việt qua mạng, hai phía phải giải mã thống nhất. | Cần khai báo Charset ở API nào? |
| T11 | Muốn tách nhiều dòng có thể dùng CRLF hoặc LF, loại bỏ ký tự kết thúc dòng. | `lines()` hay `split("\n")` dễ diễn đạt hơn? |
| T12 | Muốn kiểm tra trường nhập chỉ gồm space/tab/newline là không có dữ liệu hữu ích. | Chọn `isEmpty()` hay `isBlank()`? |
| T13 | Muốn sắp xếp tên người theo quy tắc tiếng Việt hoặc một locale xác định. | Dùng `CASE_INSENSITIVE_ORDER` đã đủ chưa? |
| T14 | Dữ liệu nhận từ người dùng thật ra là một biểu thức regex có nhóm bắt, cần thay số `42` bằng `[42]`. | Khi nào phải **giữ** `(\\d+)` và `$1` thay vì quote chúng? |

**Cách làm:** ghi câu trả lời độc lập trước, rồi đối chiếu phần 6. Với mỗi tình huống cần nói cả lựa chọn **và điều kiện khiến lựa chọn đúng**. Ví dụ T3 phải nêu “giữ trường rỗng cuối” thay vì chỉ viết `split(",")`.

## 5. Chương trình kiểm thử Java 17+ chạy độc lập

Lưu tệp **StringApiDecisionPractice.java**. Đây là bài kiểm tra các lựa chọn cốt lõi, không cần JUnit hoặc thư viện ngoài. Các hàm `same` và `check` ném `AssertionError` nếu kết quả không đúng; không dựa vào lệnh Java `assert` có thể bị tắt khi thiếu cờ `-ea`.

~~~java
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringApiDecisionPractice {
    private static int passed = 0;

    static void same(String caseName, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(caseName + ": expected <" + expected
                    + "> but got <" + actual + ">");
        }
        passed++;
    }

    static void check(String caseName, boolean condition) {
        if (!condition) throw new AssertionError(caseName);
        passed++;
    }

    public static void main(String[] args) {
        same("strip", "Java", " \tJava \n".strip());
        check("isBlank", " \t\n".isBlank());

        same("literal replace", "a/b/c", "a.b.c".replace(".", "/"));
        same("quoted regex and replacement", "a$1\\tmpb",
                "a.b".replaceAll(Pattern.quote("."),
                        Matcher.quoteReplacement("$1\\tmp")));

        check("split preserving empties",
                Arrays.equals(new String[]{"a", "b", "", ""},
                        "a,b,,".split(",", -1)));
        check("split dropping trailing empties",
                Arrays.equals(new String[]{"a", "b"},
                        "a,b,,".split(",")));

        same("join", "a-b-c", String.join("-", List.of("a", "b", "c")));
        same("lines", List.of("one", "two"), "one\ntwo\n".lines().toList());

        StringBuilder builder = new StringBuilder();
        "A\uD83D\uDE80B".codePoints()
                .filter(cp -> cp != 0x1F680)
                .forEach(builder::appendCodePoint);
        same("code point filter", "AB", builder.toString());

        String message = "C\u00E0 ph\u00EA";
        byte[] encoded = message.getBytes(StandardCharsets.UTF_8);
        same("UTF-8 round trip", message,
                new String(encoded, StandardCharsets.UTF_8));

        same("Unicode NFC", "\u00E9",
                Normalizer.normalize("e\u0301", Normalizer.Form.NFC));

        check("equalsIgnoreCase", "Java".equalsIgnoreCase("java"));
        check("String.equals case sensitive", !"Java".equals("java"));

        TreeSet<String> folded = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        folded.add("Java");
        folded.add("java");
        same("TreeSet collapses keys", 1, folded.size());

        StringBuilder mutable = new StringBuilder("x");
        CharSequence reference = mutable;
        String snapshot = mutable.toString();
        mutable.append("y");
        same("immutable snapshot", "x", snapshot);
        same("mutable CharSequence reference", "xy", reference.toString());

        check("contentEquals", "cat".contentEquals(new StringBuilder("cat")));
        same("regex capture", "id=[42]", "id=42".replaceAll("(\\d+)", "[$1]"));
        same("indexOf literal", 2, "a.b.c".indexOf("b"));
        same("substring bounds", "b.c", "a.b.c".substring(2, 5));

        System.out.println("splitCount=" + "a,b,,".split(",", -1).length);
        System.out.println("treeSetSize=" + folded.size());
        System.out.println("codePointsFiltered=" + builder);
        System.out.println("utf8RoundTrip=" + message.equals(
                new String(encoded, StandardCharsets.UTF_8)));
        System.out.println("snapshot=" + snapshot);
        System.out.println("PASS: " + passed + " checks");
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 StringApiDecisionPractice.java
java StringApiDecisionPractice
~~~

Kết quả stdout chính xác nếu 20 phép kiểm tra thành công:

~~~text
splitCount=4
treeSetSize=1
codePointsFiltered=AB
utf8RoundTrip=true
snapshot=x
PASS: 20 checks
~~~

Những kiểm tra này chứng minh nhiều lựa chọn quan trọng hơn việc chương trình chỉ không gặp lỗi: `split(",", -1)` thật sự giữ 4 trường; `Pattern.quote` kết hợp `Matcher.quoteReplacement` giữ nguyên cả dấu `$` lẫn `\`; `codePoints()` lọc trọn emoji; `UTF-8` round-trip đúng; `TreeSet` xác định khóa tương đương theo comparator, và snapshot `String` giữ giá trị cũ khi builder thay đổi.

## 6. Đáp án và lý giải từng tình huống

| Tình huống | Đáp án/API phù hợp | Giải thích ngắn gọn |
| --- | --- | --- |
| **T1** | `"a.b.c".replace(".", "/")` | Dấu `.` trong `replaceAll` là wildcard, còn `replace` tìm đúng văn bản. Kết quả `a/b/c`. |
| **T2** | `text.replaceAll(Pattern.quote(target), Matcher.quoteReplacement(value))` | Target và replacement có **hai cú pháp độc lập**. Không quote replacement có thể khiến `$1` bị hiểu là group reference. |
| **T3** | `"a,b,,".split(",", -1)` | Kết quả `["a", "b", "", ""]`. Mặc định `split(",")` bỏ token rỗng cuối. CSV đầy đủ cần parser chuyên dụng. |
| **T4** | `text.codePoints().filter(cp -> cp != 0x1F680).forEach(builder::appendCodePoint)` | Một emoji bổ sung là một code point nhưng có hai UTF-16 code unit; xử lý theo `chars()` dễ cắt nửa surrogate pair. |
| **T5** | `StringBuilder.append(...)` rồi `toString()` | Mutable buffer giảm việc tạo String trung gian trong vòng lặp nhiều lần; không đưa cùng builder vào `parallel()` để append đồng thời. |
| **T6** | `String snapshot = Objects.requireNonNull(input).toString()` | `CharSequence` có thể mutable. Snapshot giúp nội dung ổn định sau khi phương thức trả về; đồng bộ hóa nếu nguồn bị sửa đồng thời. |
| **T7** | `equalsIgnoreCase` cho tương đương bỏ qua hoa/thường; `equals` để phân biệt khóa chính xác | Hai quy tắc này không thay thế nhau; `HashMap`/`HashSet` sử dụng `equals` và `hashCode`. |
| **T8** | `String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder())` | So sánh phụ giải quyết trường hợp compare bỏ qua hoa/thường bằng 0; hai String khác nhau theo `equals` vẫn khác khóa. |
| **T9** | `Normalizer.normalize(s, Normalizer.Form.NFC)` rồi so sánh theo hợp đồng | Hai cách biểu diễn Unicode có thể khác code point. `toLowerCase` không phải phép chuẩn hóa Unicode NFC. |
| **T10** | `text.getBytes(StandardCharsets.UTF_8)` và `new String(bytes, StandardCharsets.UTF_8)` | Hai chiều dùng cùng charset; cần thỏa thuận giao thức về UTF-8. |
| **T11** | `String.lines()` | Nêu trực tiếp ý định duyệt các dòng và xử lý các dấu kết thúc dòng thông dụng; `split("\n")` đơn giản có thể để sót `\r` với CRLF. |
| **T12** | `isBlank()` | `" \t\n".isEmpty()` là false còn `isBlank()` là true. `isBlank` dựa theo `Character.isWhitespace`, không phải mọi ký tự Unicode trông giống dấu cách đều được coi là whitespace. |
| **T13** | Xem xét `Collator.getInstance(locale)` và kiểm thử locale thực tế | `CASE_INSENSITIVE_ORDER` là so sánh bỏ qua hoa/thường của String, không đồng nghĩa với sắp xếp theo ngôn ngữ. |
| **T14** | `"id=42".replaceAll("(\\d+)", "[$1]")` | Đây là regex và tham chiếu nhóm **được dùng có chủ đích**; không được quote regex hay replacement khi cần chức năng nhóm bắt. Kết quả `id=[42]`. |

Đối với **T4**, ví dụ mã là một mảnh pipeline bên trong phương thức sau khi có biến `text` và một `StringBuilder builder`; thao tác loại emoji và kết quả `AB` đã được kiểm tra trong chương trình hoàn chỉnh.

Đối với **T13**, một `Collator` có thể coi các chuỗi khác nhau theo `String.equals` là tương đương theo mức độ nhạy. Nếu dùng làm comparator của `TreeMap`/`TreeSet`, vẫn phải quyết định xem `compare(a,b)==0` có được phép gộp khóa hay không.

## 7. Thang tự đánh giá và cách ôn lại

Mỗi tình huống từ T1 đến T14 tối đa **2 điểm**:

- **1 điểm**: chọn đúng API (hoặc phương án tương đương đúng) với ví dụ khớp yêu cầu.
- **1 điểm**: giải thích được **cơ chế/rủi ro liên quan**, ví dụ zero-width regex, token rỗng cuối, độ rộng UTF-16, mutable snapshot, comparator không nhất quán với equals, hoặc lỗi do không quote replacement.

Tổng tối đa **28 điểm**. Đây là thang học tập gợi ý, không phải chứng nhận năng lực:

| Điểm | Gợi ý diễn giải | Hành động tiếp theo |
| --- | --- | --- |
| 0–13 | Cần củng cố khái niệm và cú pháp API | Thực hành lại các ví dụ đầu, chạy chương trình và dự đoán output trước khi xem |
| 14–21 | Đã chọn được phần lớn API nhưng còn thiếu giải thích điều kiện biên | Ôn nhóm có câu sai, viết thêm test thay vì học thuộc tên phương thức |
| 22–26 | Nắm chắc nhiều tình huống phổ biến | Thử biến thể dữ liệu Unicode, null, regex đặc biệt, collection và charset |
| 27–28 | Tự lý giải đầy đủ bảng lựa chọn này | Tự đề xuất API cho một chức năng nhỏ, nêu hợp đồng và tự thiết kế test |

### Tự mở rộng bài tập

1. Thay dữ liệu `"a,b,,"` bằng `",,a,"`. Dự đoán kết quả `split(",", 0)`, `split(",", -1)` và `split(",", 2)`; đối chiếu mảng thực tế.
2. Thay dấu chấm bằng ký tự `|` hoặc `[` trong target, replacement bằng `$1\log`. Thử lựa chọn `replace` và lựa chọn quote cả hai phía trong `replaceAll`; kiểm tra kết quả `equals`.
3. Thay `TreeSet` từ `CASE_INSENSITIVE_ORDER` sang comparator có tie-breaker; kiểm tra số phần tử và thứ tự với `"JAVA"`, `"Java"`, `"java"`.
4. Thay emoji 🚀 bằng emoji khác có code point bổ sung; kiểm tra `length()`, `codePoints().count()` và việc lọc/bổ sung bằng `appendCodePoint`.
5. Thử đầu vào có `CharSequence` là `StringBuilder` mutable: nhận tham chiếu so với chụp `String` rồi sửa builder; mô tả khác biệt.
6. Thử text có cả `\r\n`, `\n` và `\r` bằng `lines()`. Tự định nghĩa rõ trường hợp dòng trống ở đầu/cuối.
7. Đưa một chuỗi tiếng Việt có dấu về NFC; đoán xem `equals` trước và sau chuẩn hóa có thể khác nhau thế nào.

Mỗi bài mở rộng nên được ghi thành **input → API được chọn → expected output → actual output → lý do**. Đó là cách xác định lựa chọn API đã đúng với hợp đồng, không chỉ đúng tên hàm.

## 8. Những bẫy thường khiến chọn API sai

- **Đồng nhất văn bản và regex:** `replace`/`contains` tìm nguyên văn, còn `replaceAll`/`split` nhận regex. Dấu `.` hoặc `|` có thể đổi hoàn toàn ý nghĩa.
- **Quên tham số limit của split:** `split(regex)` là `split(regex, 0)`; các trường rỗng ở cuối bị loại. Ngoài ra, khớp regex có độ rộng 0 tại đầu chuỗi không tạo token rỗng đầu, khác với khớp có độ rộng dương.
- **Coi `String.length()` là số chữ hiển thị:** Java dùng UTF-16; code point khác grapheme cluster. API `codePoints()` vẫn không nhóm các cụm emoji nối hoặc ký tự kết hợp dấu.
- **Coi tất cả CharSequence có equals theo nội dung:** `StringBuilder` không override `equals` như String, còn CharBuffer có trạng thái view; phải định nghĩa snapshot và so sánh.
- **Dùng comparator bỏ qua hoa/thường cho TreeSet mà không muốn gộp khóa:** `compare == 0` có thể làm mất các biến thể chữ hoa/thường.
- **Dùng `toLowerCase()` như một phép chuẩn hóa cho mọi ngôn ngữ:** vấn đề locale, Unicode normalization và case folding là các yêu cầu khác nhau.
- **Xử lý bytes không nêu charset:** dữ liệu có dấu có thể bị đọc sai nếu hai phía dùng bộ mã khác nhau. Ghi rõ `UTF-8` tại biên chuyển mã.
- **Chỉ kiểm tra output bằng mắt:** chuỗi rỗng, dấu backslash, dấu kết hợp Unicode và sự tương đương comparator cần phép assert/số phần tử cụ thể.

## 9. Kết luận thực hành

Không có một API String duy nhất tối ưu cho mọi tình huống. Chọn API phải bắt đầu từ **hợp đồng dữ liệu**: văn bản nguyên văn hay regex; mutable hay snapshot; mã UTF-16 hay code point; tương đương theo nội dung hay comparator; có giữ dữ liệu rỗng không; charset/locale nào áp dụng. Sau khi chọn, kiểm thử ít nhất một trường hợp thường và một **trường hợp biên có thể làm sai giả định**. Khi giải thích được cả lựa chọn lẫn rủi ro, bạn đã có thể dùng bảng này để tự đánh giá và thiết kế những đoạn xử lý chuỗi Java dễ kiểm chứng.


</details>

- [Quay lại đầu trang](#back-to-top)
