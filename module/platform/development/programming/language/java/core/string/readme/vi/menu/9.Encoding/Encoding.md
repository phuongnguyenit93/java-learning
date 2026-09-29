# Encoding và Charset

`String` biểu diễn text trong Java, còn file, network packet và database wire protocol cuối cùng đều truyền **byte**. Encoding là cầu nối giữa hai thế giới đó.

## <a id="text-vs-bytes">Text và Byte khác nhau</a>

Một String như:

```text
"Xin chào"
```

không tự mang một “dãy byte duy nhất”. Cùng text có thể được encode thành byte khác nhau tùy charset.

mô hình tư duy:

```text
String / text
        ↓ encode bằng Charset
byte[]
        ↓ decode bằng cùng Charset
String / text
```

Nếu encode và decode dùng khác charset, kết quả có thể bị sai dù byte truyền qua không hề bị mất.

### WHY — String không “có sẵn encoding”

`String` là text value trong Java. Encoding chỉ xuất hiện khi text đi qua boundary cần byte:

```text
Java String
→ file
→ socket / HTTP body
→ database protocol
→ message broker payload
```

Cùng một text có thể có byte representation khác nhau:

```text
"é"

UTF-8
→ C3 A9

ISO-8859-1
→ E9
```

Vì vậy câu hỏi “String này encoding gì?” thường sai tầng. Câu hỏi đúng là: **boundary này dùng Charset nào để chuyển String ↔ bytes?**

## <a id="charset-encode-decode">Encode và Decode bằng Charset</a>

Hãy chỉ rõ charset ở ranh giới:

```java
byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
String restored = new String(bytes, StandardCharsets.UTF_8);
```

`StandardCharsets.UTF_8` làm hợp đồng rõ ràng và không phụ thuộc cấu hình máy chạy.

Nếu charset mismatch:

```text
text
→ encode UTF-8
→ bytes
→ decode ISO-8859-1
→ mojibake / text sai
```

Transport có thể đã chuyển byte hoàn toàn chính xác; lỗi nằm ở việc hai đầu không thống nhất cách interpret byte.

Round-trip chỉ giữ được text khi charset và error policy hỗ trợ toàn bộ input. Nếu target charset không represent được character ban đầu, encode có thể cần replacement hoặc fail.

Một round-trip đúng cần:

```text
text
→ encode bằng charset A
→ bytes
→ decode bằng charset A
→ text tương ứng
```

## <a id="default-charset-risk">Rủi ro của Default Charset</a>

Từ **JDK 18**, Java SE chuyển default charset của các API chuẩn sang UTF-8 theo JEP 400. Trong Java 21, `Charset.defaultCharset()` là UTF-8 trừ khi implementation thay đổi theo cơ chế riêng.

Vì vậy mental model cũ “default charset luôn phụ thuộc OS và locale” không còn đúng mặc định cho Java 21.

Ví dụ mã kiểu:

```java
text.getBytes()
new String(bytes)
```

để charset ngầm định. Nếu dữ liệu đi qua persistent/network ranh giới, tốt hơn là chỉ rõ charset để hợp đồng không phụ thuộc environment.

### Vậy tại sao vẫn nên explicit Charset?

Không phải vì Java 21 thường chọn charset khác nhau giữa Windows/Linux, mà vì **encoding là data contract**:

```text
producer
→ UTF-8 bytes
→ consumer

contract phải nói UTF-8
```

Explicit `StandardCharsets.UTF_8` làm intent đọc được ngay trong code, tránh phụ thuộc compatibility/implementation configuration và giúp protocol/file-format review rõ ràng hơn.

Default charset vẫn hợp lý khi API contract thật sự định nghĩa “dùng default charset của runtime”; persistent/network protocol thường nên explicit.

## <a id="malformed-input">Malformed và Unmappable Input</a>

Decode/encode không phải lúc nào cũng thành công hoàn hảo.

- **malformed đầu vào**: byte sequence không hợp lệ theo charset;
- **unmappable character**: ký tự không thể biểu diễn trong target charset.

`CharsetDecoder`/`CharsetEncoder` cho phép chọn chính sách như report, replace hoặc ignore. Với dữ liệu quan trọng, silently replace có thể che mất lỗi dữ liệu; chính sách nên được chọn có chủ ý.

### Malformed khác unmappable

```text
malformed input
→ byte/code-unit sequence không hợp lệ theo encoding đang đọc

unmappable character
→ input character hợp lệ nhưng target charset không biểu diễn được
```

UTF-8 decoder có thể gặp byte sequence malformed; encoder sang US-ASCII có thể gặp `é` hợp lệ trong Unicode nhưng không represent được bằng ASCII.

### Convenience API và strict validation

`getBytes(Charset)` và `new String(bytes, charset)` phù hợp với nhiều use case. Khi cần kiểm soát invalid data chính xác, dùng `CharsetEncoder` / `CharsetDecoder` với `CodingErrorAction.REPORT`, `REPLACE` hoặc `IGNORE` có chủ ý.

```text
data integrity quan trọng
→ REPORT thường giúp fail fast

best-effort display
→ REPLACE có thể phù hợp

IGNORE
→ dễ làm mất dữ liệu âm thầm, cần lý do rõ
```

Sau encoding, câu hỏi tiếp theo là: **bên trong Java, `char` có thật sự tương ứng một ký tự Unicode mà người dùng nhìn thấy không?**
