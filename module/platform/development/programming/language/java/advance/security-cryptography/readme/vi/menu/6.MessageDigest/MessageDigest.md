<a id="back-to-top"></a>

# Hàm băm mật mã với MessageDigest

## Menu
- [Mô hình cryptographic digest](#digest-model)
- [Luồng sử dụng MessageDigest](#message-digest-workflow)
- [Digest và giá trị tham chiếu đáng tin cậy](#trusted-digest-reference)
- [Giới hạn của digest khi không có khóa](#digest-limitations)

## <a id="digest-model">Mô hình cryptographic digest</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

`MessageDigest` biểu diễn hàm băm mật mã. Dữ liệu đầu vào có thể dài tùy ý; đầu ra là digest có kích thước do thuật toán quyết định.

Mental model:

~~~text
bytes
  ↓ hash
fixed-size digest
~~~

Digest **không có key**. Vì vậy hai bên có cùng dữ liệu đầu vào và thuật toán đều có thể tạo cùng digest. Điều này rất hữu ích cho fingerprint và so sánh, nhưng cũng là lý do digest thuần không chứng minh danh tính bên gửi.

Một hash function an toàn cho use case cụ thể cần các property như khó tìm preimage và collision ở mức chấp nhận được. Không nên suy “có chữ SHA” là đủ; chính sách thuật toán thay đổi theo thời gian, và một số algorithm cũ có thể bị JDK/chính sách bảo mật hạn chế.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="message-digest-workflow">Luồng sử dụng MessageDigest</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Luồng xử lý của MessageDigest là nạp dữ liệu rồi kết thúc bằng digest():

~~~java
MessageDigest md = MessageDigest.getInstance("SHA-256");
md.update(headerBytes);
md.update(payloadBytes);
byte[] digest = md.digest();
~~~

Có thể gọi `digest(byte[])` cho dữ liệu đầu vào đơn giản hoặc `update(...)` nhiều lần khi dữ liệu đến theo stream/chunk.

Sau `digest()`, object trở về trạng thái ban đầu theo contract của `MessageDigest` và có thể dùng lại. Dù vậy, mã concurrent không nên chia sẻ một mutable digest instance giữa nhiều request nếu không có quyền sở hữu và synchronization rõ ràng.

Khi dữ liệu là text, luôn biến text thành bytes với charset explicit như StandardCharsets.UTF_8. “Cùng String” nhưng encoding khác có thể tạo digest khác.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="trusted-digest-reference">Digest và giá trị tham chiếu đáng tin cậy</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Digest chỉ phát hiện thay đổi có ý nghĩa khi bên kiểm tra có **reference đáng tin cậy**.

Ví dụ package repository công bố SHA-256 qua một kênh đã được xác thực. Client tải file từ mirror và so digest của file với giá trị tham chiếu đó. Nếu kẻ tấn công chỉ sửa được file, mismatch bị phát hiện.

Nhưng nếu kẻ tấn công sửa được cả file và giá trị digest tham chiếu:

~~~text
modified file
    +
digest(modified file)
~~~

thì comparison vẫn pass. Vì vậy digest không tạo authenticity từ con số 0.

Khi so hai digest trong security-sensitive code, MessageDigest.isEqual(...) cung cấp API comparison dành cho digest bytes thay vì tự viết vòng lặp dừng ngay khi gặp byte khác.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="digest-limitations">Giới hạn của digest khi không có khóa</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Ba nhầm lẫn phổ biến:

1. **digest = encryption** — sai vì hash không có decrypt;
2. **digest = authentication** — sai nếu reference không được bảo vệ;
3. **hash password bằng SHA-256 một lần là đủ** — sai vì password có entropy thấp và kẻ tấn công có thể thử các giá trị đoán rất nhanh.

Nếu cần authenticity với shared secret, chuyển sang MAC. Nếu cần public verification, dùng digital signature. Nếu cần password-derived key, dùng password-based derivation với salt/work factor phù hợp.

MessageDigest vẫn là primitive nền tảng quan trọng; vấn đề không nằm ở API mà ở việc gán cho nó security guarantee nó không có.
Giới hạn lớn nhất của digest là không có secret để xác thực bên tạo dữ liệu; đó chính là động lực chuyển sang MAC.

</details>

- [Quay lại đầu trang](#back-to-top)
