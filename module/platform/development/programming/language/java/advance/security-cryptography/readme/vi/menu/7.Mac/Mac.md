<a id="back-to-top"></a>

# Mã xác thực thông điệp với MAC

## Menu
- [MAC giải quyết bài toán gì?](#mac-model)
- [Vai trò của khóa bí mật dùng chung](#mac-shared-secret)
- [Luồng tạo và xác minh MAC](#mac-workflow)
- [MAC khác digest và chữ ký số thế nào?](#mac-comparison)

## <a id="mac-model">MAC giải quyết bài toán gì?</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Message Authentication Code (MAC) thêm **secret key** vào bài toán integrity/authenticity. Bên nhận chỉ chấp nhận tag nếu tag được tạo từ message và shared secret phù hợp.

~~~text
message + shared secret
       ↓ MAC
authentication tag
~~~

Nếu kẻ tấn công sửa thông điệp nhưng không biết secret, họ không thể thực tế tạo tag hợp lệ mới theo các giả định bảo mật của thuật toán.

Điểm khác với digest là reference không cần được giữ ở kênh riêng: authenticity được gắn vào shared secret. Điểm khác với signature là mọi bên biết secret đều có thể tạo MAC, nên MAC không phân biệt “ai trong nhóm secret holders” đã tạo message.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mac-shared-secret">Vai trò của khóa bí mật dùng chung</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Shared secret là nền tảng trust của MAC. Hai service A và B có cùng HMAC key thì A có thể verify message từ B, nhưng A cũng có thể tự tạo một message/tag trông như đến từ B.

Điều này phù hợp khi trust domain đã chấp nhận shared authority, ví dụ hai internal components cùng giữ integration key. Nó không phù hợp khi verifier phải **không có khả năng giả mạo signer**; khi đó asymmetric signature thường hợp lý hơn.

Key phải có entropy và size phù hợp với MAC algorithm. Không nên dùng trực tiếp password người dùng làm HMAC key; nếu source là password, derive key trước bằng cơ chế password-based phù hợp.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mac-workflow">Luồng tạo và xác minh MAC</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Java dùng javax.crypto.Mac:

~~~java
Mac mac = Mac.getInstance("HmacSHA256");
mac.init(secretKey);
mac.update(headerBytes);
byte[] tag = mac.doFinal(payloadBytes);
~~~

Bên verify chạy cùng algorithm/key trên **đúng canonical bytes** rồi so tag. Với byte-array tag, có thể dùng `MessageDigest.isEqual(expectedTag, receivedTag)` thay vì tự viết vòng lặp dừng ngay khi gặp byte khác:

~~~java
byte[] expectedTag = verifier.doFinal(payloadBytes);
boolean valid = MessageDigest.isEqual(expectedTag, receivedTag);
~~~

`MessageDigest.isEqual(...)` kiểm tra toàn bộ bytes của digest đầu tiên; thời gian tính không phụ thuộc vào nội dung hai mảng. Nếu hai bên serialize JSON khác thứ tự field, normalize newline khác nhau hoặc dùng charset khác, MAC vẫn khác dù “nội dung logic” có vẻ giống.

Vì vậy protocol cần định nghĩa chính xác bytes nào được authenticate.

Không log shared key hoặc full secret material khi debug mismatch. Hãy log non-secret metadata như algorithm, message id, length và protocol version.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mac-comparison">MAC khác digest và chữ ký số thế nào?</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Có thể phân biệt ba cơ chế bằng câu hỏi “ai có thể tạo giá trị hợp lệ?”:

| Cơ chế | Key khi tạo | Ai có thể tạo |
| --- | --- | --- |
| digest | không key | bất kỳ ai có data |
| MAC | shared secret | mọi holder của secret |
| signature | private key | holder của private key |

MAC cung cấp integrity + shared-secret authenticity, nhưng không cung cấp confidentiality. Message vẫn đọc được nếu không có encryption.

Trong protocol mới cần cả confidentiality và integrity, authenticated encryption thường đơn giản và ít lỗi composition hơn việc tự encrypt rồi tự thêm MAC, trừ khi protocol specification đã định nghĩa composition cụ thể.
MAC giải quyết authenticity trong trust domain chia sẻ secret; khi verifier không được có quyền tạo giá trị hợp lệ, mô hình tiếp theo cần tách signing authority bằng digital signature.

</details>

- [Quay lại đầu trang](#back-to-top)
