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

## <a id="charset-encode-decode">Encode và Decode bằng Charset</a>

Hãy chỉ rõ charset ở ranh giới:

```java
byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
String restored = new String(bytes, StandardCharsets.UTF_8);
```

`StandardCharsets.UTF_8` làm hợp đồng rõ ràng và không phụ thuộc cấu hình máy chạy.

Một round-trip đúng cần:

```text
text
→ encode bằng charset A
→ bytes
→ decode bằng charset A
→ text tương ứng
```

## <a id="default-charset-risk">Rủi ro của Default Charset</a>

API dùng charset mặc định của platform có thể cho kết quả khác giữa máy developer, CI, container và production.

Ví dụ mã kiểu:

```java
text.getBytes()
new String(bytes)
```

để charset ngầm định. Nếu dữ liệu đi qua persistent/network ranh giới, tốt hơn là chỉ rõ charset để hợp đồng không phụ thuộc environment.

## <a id="malformed-input">Malformed và Unmappable Input</a>

Decode/encode không phải lúc nào cũng thành công hoàn hảo.

- **malformed đầu vào**: byte sequence không hợp lệ theo charset;
- **unmappable character**: ký tự không thể biểu diễn trong target charset.

`CharsetDecoder`/`CharsetEncoder` cho phép chọn chính sách như report, replace hoặc ignore. Với dữ liệu quan trọng, silently replace có thể che mất lỗi dữ liệu; chính sách nên được chọn có chủ ý.

Sau encoding, câu hỏi tiếp theo là: **bên trong Java, `char` có thật sự tương ứng một ký tự Unicode mà người dùng nhìn thấy không?**
