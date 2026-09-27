# Text Block

Text block giúp viết multiline String dễ đọc hơn trong mã nguồn. Nó thay đổi **cú pháp biểu diễn literal trong source**, không tạo một runtime type mới.

## <a id="text-block-syntax">Cú pháp Text Block</a>

```java
String json = """
    {
      "name": "Java"
    }
    """;
```

Result vẫn là `java.lang.String` bình thường.

Text block đặc biệt hữu ích cho JSON, SQL, HTML hoặc text mẫu nhiều dòng vì giảm escape/concatenation noise.

### Text block là source syntax, không phải runtime type mới

Hai cách viết có thể tạo cùng String value:

```java
String a = "hello\nworld\n";

String b = """
        hello
        world
        """;

a.equals(b); // true
```

Mọi rule về immutability, equality, encoding và Unicode của String vẫn áp dụng y hệt.

### Opening delimiter

Sau opening `"""`, text block cần line terminator theo syntax của Java. Nội dung thực sự bắt đầu ở các line sau, giúp source multiline có structure rõ ràng.

## <a id="incidental-whitespace">Incidental Indentation</a>

Compiler xử lý một phần indentation mang tính “trình bày source” để text block có thể đặt đẹp trong mã mà không bắt buộc đầu ra giữ toàn bộ khoảng trắng đầu dòng đó.

Mental model:

```text
source indentation để code đẹp
        ↓ compiler xác định incidental indent
strip phần incidental
        ↓
String content
```

Vị trí closing delimiter có thể ảnh hưởng lượng indentation được xem là incidental, nên delimiter/content cần đặt nhất quán.

Whitespace bên trong text vẫn quan trọng. Khi đầu ra phải exact, hãy kiểm tra rendered string thay vì suy luận bằng mắt từ indentation của source.

Trailing whitespace trên line cũng được xử lý để tránh invisible source formatting vô tình trở thành data. Nếu cần giữ trailing space có chủ ý, dùng escape như `\s` hoặc strategy rõ ràng.

### Trailing newline và closing delimiter

Hai shape có thể cho result khác về newline cuối:

```java
String withNewline = """
        hello
        """;

String withoutNewline = """
        hello""";
```

Khi exact output quan trọng, test bằng `length()` hoặc hiển thị escaped form thay vì nhìn source bằng mắt.

### String.indent và stripIndent

Multiline text không chỉ xuất hiện dưới dạng text block literal. `String` cũng có API để làm việc trực tiếp với indentation:

```java
String text = "alpha\nbeta\n";

String indented = text.indent(4);
String stripped = indented.stripIndent();
```

Mental model:

```text
text block incidental indentation
→ compiler xử lý khi tạo literal từ source

String.indent / stripIndent
→ runtime String operations
```

Hai tầng này liên quan về mục đích đọc multiline text, nhưng không phải cùng một mechanism.

## <a id="escape-processing">Escape và Line Terminator</a>

Text block vẫn xử lý escape sequence theo quy tắc của Java và có ngữ nghĩa riêng cho line terminator/closing delimiter.

Không phải mọi `\` đều biến mất, và text block không có nghĩa “raw string”. Nếu cần exact backslash hoặc newline hành vi, hãy kiểm tra Java string value cuối cùng.

### `\s` giữ một space có chủ ý

```java
String value = """
        red  \s
        green\s
        """;
```

`\s` được translate thành space sau bước xử lý incidental whitespace, nên hữu ích khi trailing space là data thật.

### Line continuation

Backslash ở cuối physical line có thể suppress line terminator:

```java
String sentence = """
        hello \
        world
        """;
```

Đây là escape semantics của source, không phải String runtime mutation.

### Thứ tự xử lý quan trọng

Ở mức mental model:

```text
normalize line terminators
→ strip incidental whitespace
→ process escape sequences
→ String value
```

Điều này giải thích vì sao `\s` có thể bảo toàn space mà source whitespace thông thường có thể bị strip.

### translateEscapes

`translateEscapes()` hữu ích khi **runtime String** chứa escape notation và application có chủ ý muốn interpret chúng:

```java
String escaped = "line1\\nline2";
String translated = escaped.translateEscapes();

System.out.println(translated);
```

```text
runtime text "\\n"
→ translateEscapes()
→ newline character
```

Đây khác với escape processing của Java source literal: source escapes được compiler xử lý khi compile, còn `translateEscapes()` là một runtime String operation.

Không chạy `translateEscapes()` trên arbitrary user input chỉ vì thấy backslash; việc interpret escape notation phải là một phần rõ ràng của input contract.

## <a id="text-block-not-template">Text Block và String Template</a>

Text block không tự interpolation variable:

```java
"""
Hello ${name}
"""
```

không tự thay `${name}` thành value.

Muốn chèn dữ liệu vẫn cần formatting, concatenation hoặc API/template mechanism phù hợp.

```java
String template = """
        Hello %s
        """;

String message = template.formatted(name);
```

Text block chỉ làm representation trong source dễ đọc hơn; nó không biến `%s`, `${name}` hay ký hiệu tùy ý thành interpolation nếu không có API khác xử lý.

Kết thúc module, mô hình tư duy nên là:

```text
String immutable
→ có thể chia sẻ/pool
→ equality dùng value, không dựa vào pool identity
→ builder dùng khi cần mutable construction
→ text/bytes cần Charset
→ char/code point/grapheme là các tầng biểu diễn khác nhau
→ regex mô tả pattern
→ text block chỉ cải thiện cú pháp trong mã nguồn
```
