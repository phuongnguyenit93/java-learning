<a id="back-to-top"></a>

# Chữ ký số

## Menu
- [Mô hình chữ ký số](#signature-model)
- [Luồng ký và xác minh](#sign-and-verify)
- [Dữ liệu đầu vào và hợp đồng xác minh](#signature-data-contract)
- [Chữ ký số khác MAC và mã hóa thế nào?](#signature-comparison)

## <a id="signature-model">Mô hình chữ ký số</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Digital signature dùng asymmetric key pair để tách quyền **ký** và **xác minh**.

~~~text
message + private key
       ↓ sign
signature

message + signature + public key
       ↓ verify
true / false
~~~

Private key phải được giữ bí mật; public key có thể phân phối cho verifier. Signature tạo authenticity/integrity dựa trên việc verifier tin rằng public key thực sự thuộc signer dự kiến.

Đó là lý do certificate/trust xuất hiện sau chapter này: signature math có thể đúng nhưng nếu public key được gắn với sai identity, ứng dụng vẫn có thể tin nhầm bên.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="sign-and-verify">Luồng ký và xác minh</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Java dùng Signature engine với hai state chính: sign và verify.

~~~java
Signature signer = Signature.getInstance("SHA256withRSA");
signer.initSign(privateKey);
signer.update(message);
byte[] signature = signer.sign();

Signature verifier = Signature.getInstance("SHA256withRSA");
verifier.initVerify(publicKey);
verifier.update(message);
boolean valid = verifier.verify(signature);
~~~

Algorithm string mô tả signature scheme/digest composition theo provider contract. Môi trường thực tế không nên chọn algorithm chỉ vì ví dụ chạy được; phải theo protocol và chính sách bảo mật hiện hành.

Signature object có mutable state. Giống MessageDigest/Mac, tránh share instance không kiểm soát giữa concurrent requests.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="signature-data-contract">Dữ liệu đầu vào và hợp đồng xác minh</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Signature bảo vệ **bytes được ký**, không bảo vệ “ý nghĩa mà con người nghĩ là đã ký”.

Nếu bên tạo dữ liệu ký JSON bytes nhưng bên nhận parse rồi serialize lại trước khi verify, khác biệt về whitespace, thứ tự field hoặc cách biểu diễn Unicode có thể làm verification thất bại. Ngược lại, nếu protocol chỉ ký một tập field nhưng business logic tin toàn object, kẻ tấn công có thể sửa field không nằm trong dữ liệu đã ký.

Protocol cần xác định:

- exact payload hoặc canonical form;
- domain/context/version để tránh dùng signature ở ngữ cảnh khác;
- key/certificate identity nào được phép ký;
- cách xử lý replay nếu replay là threat.

Signature API không tự thiết kế data contract thay ứng dụng.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="signature-comparison">Chữ ký số khác MAC và mã hóa thế nào?</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Signature không phải “encryption bằng private key”. Đây là hai phép toán bảo mật khác nhau dù một số hệ mật mã dùng cùng họ key.

- **signature**: private key tạo proof, public key verify; mục tiêu authenticity/integrity;
- **asymmetric encryption**: thường public key bảo vệ secret/data để private key holder decrypt; mục tiêu confidentiality;
- **MAC**: shared secret vừa tạo vừa verify.

Cách nói “encrypt with private key” dễ dẫn đến protocol design sai và bỏ qua encoding/padding/signature-scheme semantics thực sự.

Nếu cần vừa bí mật vừa có signer identity, hãy dùng protocol kết hợp các primitive theo chuẩn, không thay signature bằng một thao tác Cipher đảo chiều tùy ý.
Signature giải quyết authenticity nhưng không che nội dung; từ đây module chuyển sang confidentiality, bắt đầu bằng symmetric encryption.

</details>

- [Quay lại đầu trang](#back-to-top)
