<a id="back-to-top"></a>

# Mật mã học và mục tiêu bảo mật

## Menu
- [Các mục tiêu bảo mật: bí mật, toàn vẹn và xác thực](#security-goals)
- [Mật mã học giải quyết phần nào của bài toán bảo mật?](#cryptography-role)
- [Mô hình đe dọa và ranh giới của mô-đun](#threat-model-boundary)
- [Chọn cơ chế mật mã từ mục tiêu bảo mật](#primitive-by-goal)

## <a id="security-goals">Các mục tiêu bảo mật: bí mật, toàn vẹn và xác thực</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Mật mã học xuất hiện khi dữ liệu cần giữ một hoặc nhiều **bảo đảm bảo mật** trước một đối thủ có khả năng đọc, sửa hoặc giả mạo dữ liệu.

Ba mục tiêu cốt lõi của module này là:

- **tính bí mật (confidentiality)**: người không có quyền không đọc được nội dung;
- **tính toàn vẹn (integrity)**: thay đổi trái phép phải có khả năng bị phát hiện;
- **tính xác thực (authenticity)**: bên nhận có cơ sở kiểm tra dữ liệu hoặc danh tính thực sự gắn với bên nào.

Một cơ chế không tự động cung cấp cả ba mục tiêu. Mã hóa có thể che nội dung nhưng một chế độ mã hóa không xác thực vẫn có thể cho phép dữ liệu bị sửa mà không được phát hiện. Hàm băm tạo dấu vân tay nhưng không có bí mật nên không tự chứng minh ai đã tạo dữ liệu. MAC và chữ ký số giải quyết bài toán xác thực theo hai mô hình khóa khác nhau.

Mô hình tư duy quan trọng là: **xác định bảo đảm cần giữ trước, rồi mới chọn primitive và Java API**. Nếu bắt đầu bằng tên class hoặc tên thuật toán, rất dễ dùng đúng API cho sai bài toán.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cryptography-role">Mật mã học giải quyết phần nào của bài toán bảo mật?</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Mật mã học biến một số giả định bảo mật thành các phép toán có thể kiểm tra bằng khóa, dữ liệu và thuật toán. Ví dụ, `Cipher` có thể biến plaintext thành ciphertext; `Mac` tạo giá trị xác thực bằng bí mật dùng chung (shared secret); `Signature` cho phép private key tạo chữ ký để public key xác minh.

Nhưng mật mã học **không thay thế toàn bộ kiến trúc bảo mật**. Nó không tự quyết định người dùng nào được phép gọi API, không tự bảo vệ máy đã bị chiếm quyền, không tự xoay vòng secret và không tự xác định certificate nào tổ chức của bạn phải tin.

~~~text
security requirement
    ↓
cryptographic goal
    ↓
primitive + key model
    ↓
Java security API
    ↓
vòng đời khóa và dữ liệu tin cậy + chính sách của ứng dụng
~~~

Module này tập trung vào phần từ mục tiêu mật mã đến Java security API và ngữ nghĩa bảo mật. Authentication/authorization framework, IAM, vận hành secret và PKI ở hạ tầng thuộc trách nhiệm của các module hoặc hệ thống tương ứng.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="threat-model-boundary">Mô hình đe dọa và ranh giới của mô-đun</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Trước khi chọn primitive, cần hỏi **kẻ tấn công có thể làm gì**. Nếu kẻ tấn công chỉ có thể đọc bản sao database, mục tiêu chính có thể là confidentiality của dữ liệu lưu trữ. Nếu kẻ tấn công có thể sửa thông điệp trên đường truyền, integrity và authenticity cũng trở thành yêu cầu. Nếu endpoint đã bị chiếm quyền và kẻ tấn công đọc được key trong process, mã hóa dữ liệu ngay trong process không thể khôi phục ranh giới tin cậy đã mất.

Mô hình đe dọa (threat model) tối thiểu nên trả lời:

1. dữ liệu nào cần bảo vệ;
2. dữ liệu ở đâu: memory, file, database hay network;
3. ai được phép đọc hoặc tạo dữ liệu;
4. kẻ tấn công có thể đọc, sửa, replay hay giả mạo đến mức nào;
5. key/certificate nào được xem là trust anchor.

Ranh giới của module cũng cần rõ. Cơ chế Socket, DNS và HTTP thuộc Networking. Quy trình cấp phát CA, vận hành HSM, xoay vòng secret và IAM là trách nhiệm hạ tầng. Ở đây chỉ học đủ để mã Java sử dụng key, certificate và trust material đúng hợp đồng bảo mật.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="primitive-by-goal">Chọn cơ chế mật mã từ mục tiêu bảo mật</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Có thể dùng bảng sau như bước định hướng đầu tiên, không phải danh sách thuật toán bắt buộc:

| Mục tiêu | Primitive điển hình | Java API chính |
| --- | --- | --- |
| fingerprint / phát hiện thay đổi so với giá trị tham chiếu đáng tin cậy | cryptographic hash | MessageDigest |
| integrity + authenticity với bí mật dùng chung | MAC | Mac |
| authenticity với asymmetric key | digital signature | Signature |
| confidentiality | encryption | Cipher |
| confidentiality + tamper detection | AEAD | Cipher với AEAD mode |
| thiết lập bí mật dùng chung | key agreement / KEM | KeyAgreement / KEM |
| secure channel | TLS | JSSE |

Một ứng dụng thực tế thường ghép nhiều dòng. Ví dụ TLS cần certificate/trust để xác thực peer, key establishment để tạo session key và symmetric authenticated encryption để bảo vệ lưu lượng.

Lộ trình học của module đi theo đúng quan hệ phụ thuộc đó:

~~~text
mục tiêu bảo mật + phân biệt cách biểu diễn/primitive
        ↓
JCA/JCE + Provider
        ↓
SecureRandom + key material
        ↓
digest → MAC → digital signature
        ↓
symmetric/asymmetric encryption → AEAD
        ↓
key establishment → key derivation
        ↓
KeyStore → certificate path/trust
        ↓
JSSE/TLS
        ↓
thiết kế end-to-end + review lỗi
~~~

Đừng chọn cơ chế chỉ vì nó “mạnh” hoặc quen thuộc. Hãy chọn theo mục tiêu bảo mật, mô hình khóa, hợp đồng protocol và chính sách của hệ thống.
</details>

- [Quay lại đầu trang](#back-to-top)
