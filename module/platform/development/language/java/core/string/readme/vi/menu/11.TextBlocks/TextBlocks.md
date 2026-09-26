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

## <a id="incidental-whitespace">Incidental Indentation</a>

Compiler xử lý một phần indentation mang tính “trình bày source” để text block có thể đặt đẹp trong mã mà không bắt buộc đầu ra giữ toàn bộ khoảng trắng đầu dòng đó.

Whitespace bên trong text vẫn quan trọng. Khi đầu ra phải exact, hãy kiểm tra rendered string thay vì suy luận bằng mắt từ indentation của source.

## <a id="escape-processing">Escape và Line Terminator</a>

Text block vẫn xử lý escape sequence theo quy tắc của Java và có ngữ nghĩa riêng cho line terminator/closing delimiter.

Không phải mọi `\` đều biến mất, và text block không có nghĩa “raw string”. Nếu cần exact backslash hoặc newline hành vi, hãy kiểm tra Java string value cuối cùng.

## <a id="text-block-not-template">Text Block và String Template</a>

Text block không tự interpolation variable:

```java
"""
Hello ${name}
"""
```

không tự thay `${name}` thành value.

Muốn chèn dữ liệu vẫn cần formatting, concatenation hoặc API/template mechanism phù hợp.

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
