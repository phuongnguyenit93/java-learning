# Text Block và tổng hợp mô hình xử lý String

Text Block giúp viết String nhiều dòng dễ đọc hơn trong mã nguồn. Nó thay đổi **cú pháp biểu diễn String literal trong mã nguồn**, không tạo một kiểu dữ liệu mới khi chạy.

## <a id="text-block-syntax">Cú pháp Text Block</a>

```java
String json = """
    {
      "name": "Java"
    }
    """;
```

Kết quả vẫn là `java.lang.String` bình thường.

Text Block đặc biệt hữu ích cho JSON, SQL, HTML hoặc văn bản mẫu nhiều dòng vì giảm nhiễu do ký tự thoát và phép nối chuỗi.

### Text Block là cú pháp mã nguồn, không phải kiểu dữ liệu mới khi chạy

Hai cách viết có thể tạo cùng một giá trị String:

```java
String a = "hello\nworld\n";

String b = """
        hello
        world
        """;

a.equals(b); // true
```

Mọi quy tắc về tính bất biến, so sánh bằng nhau, mã hóa và Unicode của String vẫn áp dụng y hệt.

### Dấu mở đầu

Sau dấu mở `"""`, Text Block cần ký tự kết thúc dòng theo cú pháp Java. Nội dung thực sự bắt đầu ở các dòng sau, giúp mã nguồn nhiều dòng có cấu trúc rõ ràng.

## <a id="incidental-whitespace">Thụt lề phát sinh trong mã nguồn</a>

Trình biên dịch xử lý một phần thụt lề chỉ phục vụ trình bày mã nguồn để Text Block có thể đặt đẹp trong mã mà không bắt buộc đầu ra giữ toàn bộ khoảng trắng đầu dòng đó.

Mô hình tư duy:

```text
thụt lề trong mã nguồn để code dễ đọc
        ↓ trình biên dịch xác định phần thụt lề phát sinh
loại phần thụt lề phát sinh
        ↓
nội dung String
```

Vị trí dấu đóng có thể ảnh hưởng lượng thụt lề được xem là phát sinh, nên dấu đóng và nội dung cần được đặt nhất quán.

Khoảng trắng bên trong văn bản vẫn quan trọng. Khi đầu ra phải chính xác, hãy kiểm tra String thực tế thay vì suy luận bằng mắt từ thụt lề của mã nguồn.

Khoảng trắng ở cuối dòng cũng được xử lý để tránh định dạng vô hình trong mã nguồn vô tình trở thành dữ liệu. Nếu cần giữ khoảng trắng cuối dòng có chủ ý, dùng ký tự thoát như `\s` hoặc cách biểu diễn rõ ràng.

### Dòng mới cuối cùng và dấu đóng

Hai cách đặt dấu đóng có thể cho kết quả khác về dòng mới cuối cùng:

```java
String withNewline = """
        hello
        """;

String withoutNewline = """
        hello""";
```

Khi đầu ra chính xác quan trọng, kiểm tra bằng `length()` hoặc hiển thị dạng có ký tự thoát thay vì chỉ nhìn mã nguồn bằng mắt.

### String.indent và stripIndent

Văn bản nhiều dòng không chỉ xuất hiện dưới dạng Text Block literal. `String` cũng có API để làm việc trực tiếp với thụt lề:

```java
String text = "alpha\nbeta\n";

String indented = text.indent(4);
String stripped = indented.stripIndent();
```

Mô hình tư duy:

```text
thụt lề phát sinh của Text Block
→ trình biên dịch xử lý khi tạo literal từ mã nguồn

String.indent / stripIndent
→ thao tác String khi chương trình chạy
```

Hai tầng này liên quan về mục đích xử lý văn bản nhiều dòng, nhưng không phải cùng một cơ chế.

## <a id="escape-processing">Ký tự thoát và ký tự kết thúc dòng</a>

Text Block vẫn xử lý chuỗi ký tự thoát theo quy tắc của Java và có ngữ nghĩa riêng cho ký tự kết thúc dòng/dấu đóng.

Không phải mọi `\` đều biến mất, và Text Block không có nghĩa là “chuỗi thô (raw string)”. Nếu cần chính xác hành vi của dấu gạch chéo ngược hoặc ký tự xuống dòng, hãy kiểm tra giá trị String cuối cùng.

### `\s` giữ một khoảng trắng có chủ ý

```java
String value = """
        red  \s
        green\s
        """;
```

`\s` được chuyển thành một khoảng trắng sau bước xử lý thụt lề phát sinh, nên hữu ích khi khoảng trắng cuối dòng là dữ liệu thật.

### Nối dòng trong mã nguồn

Dấu gạch chéo ngược (backslash) ở cuối dòng vật lý có thể loại bỏ ký tự kết thúc dòng đó:

```java
String sentence = """
        hello \
        world
        """;
```

Đây là ngữ nghĩa ký tự thoát của mã nguồn, không phải thao tác sửa String khi chạy.

### Thứ tự xử lý quan trọng

Ở mức mô hình tư duy:

```text
chuẩn hóa ký tự kết thúc dòng
→ loại khoảng trắng phát sinh
→ xử lý chuỗi ký tự thoát
→ giá trị String
```

Điều này giải thích vì sao `\s` có thể bảo toàn khoảng trắng mà khoảng trắng trình bày trong mã nguồn thông thường có thể bị loại.

### translateEscapes

`translateEscapes()` hữu ích khi **String lúc chương trình đang chạy** chứa ký hiệu ký tự thoát và ứng dụng có chủ ý muốn diễn giải chúng:

```java
String escaped = "line1\\nline2";
String translated = escaped.translateEscapes();

System.out.println(translated);
```

```text
văn bản khi chạy "\\n"
→ translateEscapes()
→ ký tự xuống dòng
```

Điều này khác với xử lý ký tự thoát của literal trong mã nguồn Java: ký tự thoát trong mã nguồn được trình biên dịch xử lý khi biên dịch, còn `translateEscapes()` là một thao tác String khi chương trình chạy. Đặc biệt, nó **không diễn giải Unicode escape dạng `\\uXXXX`**; chuỗi như `"\\u0041"` không biến thành `"A"` qua `translateEscapes()` mà là đầu vào escape không hợp lệ cho API này.

Không chạy `translateEscapes()` trên đầu vào người dùng tùy ý chỉ vì thấy dấu gạch chéo ngược; việc diễn giải ký hiệu ký tự thoát phải là một phần rõ ràng của hợp đồng đầu vào.

## <a id="text-block-not-template">Text Block và nội suy chuỗi</a>

Text Block không tự nội suy biến:

```java
"""
Hello ${name}
"""
```

không tự thay `${name}` thành giá trị.

Muốn chèn dữ liệu vẫn cần định dạng, nối chuỗi hoặc API/cơ chế mẫu (template) phù hợp.

```java
String template = """
        Hello %s
        """;

String message = template.formatted(name);
```

Text Block chỉ làm cách biểu diễn trong mã nguồn dễ đọc hơn; nó không biến `%s`, `${name}` hay ký hiệu tùy ý thành nội suy nếu không có API khác xử lý.

## <a id="string-synthesis">Tổng hợp mô hình xử lý String</a>

Kết thúc mô-đun, mô hình tư duy nên là:

```text
String bất biến
→ có thể chia sẻ/dùng pool
→ so sánh nội dung theo giá trị, không dựa vào định danh tham chiếu trong pool
→ StringBuilder/StringBuffer dùng khi cần bộ đệm xây chuỗi có thể thay đổi
→ char / điểm mã / cụm ký tự là các tầng biểu diễn khác nhau
→ văn bản / byte cần Charset
→ Regex mô tả mẫu
→ Text Block chỉ cải thiện cú pháp trong mã nguồn
```

Khi gặp một bài toán xử lý văn bản thực tế, hãy xác định đúng tầng trước khi chọn API:

```text
cần biểu diễn giá trị văn bản trong Java
→ String

cần xây nội dung tăng dần qua nhiều bước
→ StringBuilder / StringBuffer khi thực sự cần đặc tính đồng bộ

cần xử lý ký tự Unicode đúng tầng
→ đơn vị mã / điểm mã / cụm ký tự theo yêu cầu

cần đưa văn bản qua tệp, mạng hoặc giao thức byte
→ Charset + mã hóa/giải mã rõ ràng

cần mô tả mẫu tìm kiếm/kiểm tra
→ Regex khi String API đơn giản không đủ

cần viết String literal nhiều dòng dễ đọc hơn
→ Text Block
```

Nếu yêu cầu chuyển sang sắp xếp, so sánh hoặc phân tách văn bản theo ngôn ngữ/vùng miền, bài toán đã đi sang mô-đun **Bản địa hóa (Localization)**. Nếu yêu cầu tập trung vào đọc/ghi tệp, luồng hoặc kênh byte, phần sâu hơn thuộc mô-đun **I/O**.
