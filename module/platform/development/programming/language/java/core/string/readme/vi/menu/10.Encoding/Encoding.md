# Mã hóa văn bản và Charset

Sau khi hiểu `String` lưu và xử lý văn bản theo mô hình Unicode, bước tiếp theo là đi qua ranh giới bên ngoài JVM. Tệp, gói tin mạng và giao thức kết nối cơ sở dữ liệu cuối cùng đều truyền **byte**; mã hóa văn bản là cầu nối giữa hai biểu diễn đó.

## <a id="text-vs-bytes">Văn bản và byte</a>

Một String như:

```text
"Xin chào"
```

không tự mang một “dãy byte duy nhất”. Cùng văn bản có thể được mã hóa thành byte khác nhau tùy Charset.

mô hình tư duy:

```text
String / văn bản
        ↓ mã hóa bằng Charset
byte[]
        ↓ giải mã bằng cùng Charset
String / văn bản
```

Nếu mã hóa và giải mã dùng Charset khác nhau, kết quả có thể bị sai dù byte truyền qua không hề bị mất.

### VÌ SAO — String không “có sẵn cách mã hóa byte”

`String` là giá trị văn bản trong Java. Mã hóa chỉ xuất hiện khi văn bản đi qua ranh giới cần byte:

```text
Java String
→ tệp
→ socket / nội dung HTTP
→ giao thức cơ sở dữ liệu
→ dữ liệu của hệ thống môi giới thông điệp (message broker)
```

Cùng một văn bản có thể có biểu diễn byte khác nhau:

```text
"é"

UTF-8
→ C3 A9

ISO-8859-1
→ E9
```

Vì vậy câu hỏi “String này được mã hóa theo kiểu nào?” thường sai tầng. Câu hỏi đúng là: **ranh giới này dùng Charset nào để chuyển String ↔ byte?** `String` biểu diễn văn bản; Charset mới quyết định cách văn bản được ánh xạ sang byte.

## <a id="charset-encode-decode">Mã hóa và giải mã bằng Charset</a>

Hãy chỉ rõ Charset ở ranh giới:

```java
byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
String restored = new String(bytes, StandardCharsets.UTF_8);
```

`StandardCharsets.UTF_8` làm hợp đồng rõ ràng và không phụ thuộc ngầm vào cấu hình máy chạy.

Nếu hai phía dùng Charset không khớp:

```text
văn bản
→ mã hóa UTF-8
→ byte
→ giải mã ISO-8859-1
→ mojibake / văn bản sai
```

Hệ thống truyền tải có thể đã chuyển byte hoàn toàn chính xác; lỗi nằm ở việc hai đầu không thống nhất cách diễn giải byte.

Vòng mã hóa/giải mã chỉ giữ được văn bản khi Charset và chính sách xử lý lỗi hỗ trợ toàn bộ đầu vào. Nếu Charset đích không biểu diễn được ký tự ban đầu, quá trình mã hóa có thể phải thay thế hoặc báo lỗi.

Một vòng mã hóa/giải mã đúng cần:

```text
văn bản
→ mã hóa bằng Charset A
→ byte
→ giải mã bằng Charset A
→ văn bản tương ứng
```

## <a id="default-charset-risk">Rủi ro của Charset mặc định</a>

Từ **JDK 18**, JEP 400 quy định UTF-8 là **Charset mặc định của Java**. Vì vậy, với hành vi JDK chuẩn trên Java 21, `Charset.defaultCharset()` là UTF-8 và các API được định nghĩa là dùng Charset mặc định sẽ đi theo lựa chọn đó; cấu hình tương thích đặc biệt của môi trường chạy có thể chủ động thay đổi mặc định này. Không nên suy rộng thành “mọi luồng I/O chuẩn đều dùng `Charset.defaultCharset()`”: console và một số luồng chuẩn có cơ chế Charset riêng.

Vì vậy mô hình cũ “Charset mặc định luôn phụ thuộc hệ điều hành và locale” không còn đúng mặc định cho Java 21. Tuy nhiên, hợp đồng dữ liệu bền vững vẫn nên nêu Charset rõ ràng thay vì dựa vào mặc định của môi trường chạy.

Ví dụ mã kiểu:

```java
text.getBytes();
new String(bytes);
```

để Charset ngầm định. Nếu dữ liệu đi qua ranh giới lưu trữ hoặc mạng, tốt hơn là chỉ rõ Charset để hợp đồng không phụ thuộc môi trường chạy.

### Vì sao vẫn nên chỉ rõ Charset?

Không phải vì Java 21 thường chọn Charset khác nhau giữa Windows/Linux, mà vì **mã hóa là một phần của hợp đồng dữ liệu**:

```text
phía tạo dữ liệu
→ byte UTF-8
→ phía đọc dữ liệu

hợp đồng phải nói UTF-8
```

Chỉ rõ `StandardCharsets.UTF_8` làm mục đích đọc được ngay trong mã nguồn, tránh phụ thuộc cấu hình tương thích/triển khai và giúp việc rà soát giao thức hoặc định dạng tệp rõ ràng hơn.

Charset mặc định vẫn hợp lý khi hợp đồng API thật sự định nghĩa “dùng Charset mặc định của môi trường chạy”; giao thức lưu trữ hoặc mạng thường nên chỉ rõ Charset.

## <a id="malformed-input">Đầu vào sai định dạng và ký tự không thể ánh xạ</a>

Giải mã/mã hóa không phải lúc nào cũng thành công hoàn hảo.

- **đầu vào sai định dạng (malformed input)**: chuỗi đầu vào không hợp lệ về cấu trúc đối với thao tác mã hóa/giải mã đang thực hiện, ví dụ byte UTF-8 sai cấu trúc khi giải mã hoặc surrogate đứng riêng khi mã hóa;
- **ký tự không thể ánh xạ (unmappable character)**: ký tự không thể biểu diễn trong Charset đích.

`CharsetDecoder`/`CharsetEncoder` cho phép chọn chính sách như `REPORT`, `REPLACE` hoặc `IGNORE`. Với dữ liệu quan trọng, thay thế âm thầm có thể che mất lỗi dữ liệu; chính sách nên được chọn có chủ ý.

### Sai định dạng khác với không thể ánh xạ

```text
đầu vào sai định dạng
→ cấu trúc đầu vào không hợp lệ cho bộ giải mã/bộ mã hóa
→ ví dụ byte UTF-8 sai cấu trúc hoặc surrogate UTF-16 đứng riêng

ký tự không thể ánh xạ
→ ký tự đầu vào hợp lệ nhưng Charset đích không biểu diễn được
```

Bộ giải mã UTF-8 có thể gặp dãy byte sai định dạng. Bộ mã hóa có thể gặp một surrogate đứng riêng và coi đó là đầu vào sai định dạng. Ngược lại, ký tự `é` là Unicode hợp lệ nhưng khi mã hóa sang US-ASCII lại là trường hợp **không thể ánh xạ**, vì ASCII không biểu diễn được ký tự đó.

### API tiện dụng và kiểm tra nghiêm ngặt

`getBytes(Charset)` và `new String(bytes, charset)` phù hợp với nhiều trường hợp sử dụng, nhưng hợp đồng của hai phiên bản phương thức tiện dụng này là **thay thế (REPLACE)** đầu vào sai định dạng (malformed) hoặc không thể ánh xạ (unmappable) bằng giá trị thay thế của Charset thay vì báo lỗi cho bên gọi. Vì vậy, nếu cần phát hiện dữ liệu không hợp lệ, chọn `IGNORE`, hoặc tùy chỉnh chính sách xử lý, hãy dùng `CharsetEncoder` / `CharsetDecoder` với `CodingErrorAction.REPORT`, `REPLACE` hoặc `IGNORE` có chủ ý.

```text
tính toàn vẹn dữ liệu quan trọng
→ REPORT thường giúp báo lỗi ngay

hiển thị theo khả năng tốt nhất
→ REPLACE có thể phù hợp

IGNORE
→ dễ làm mất dữ liệu âm thầm, cần lý do rõ
```

Sau khi hiểu ranh giới văn bản ↔ byte, chương tiếp theo chuyển sang **biểu thức chính quy (Regex)** để mô tả, tìm kiếm và kiểm tra các mẫu văn bản.
