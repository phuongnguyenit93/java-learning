<a id="back-to-top"></a>

# Mã hóa có xác thực và AEAD

## Menu
- [Vì sao cần authenticated encryption?](#aead-purpose)
- [Nonce/IV và yêu cầu không tái sử dụng](#nonce-uniqueness)
- [AAD và authentication tag](#aad-and-authentication-tag)
- [Xác minh tag và lỗi khi giải mã](#aead-decryption-failure)

## <a id="aead-purpose">Vì sao cần authenticated encryption?</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Authenticated Encryption with Associated Data (AEAD) giải quyết cùng lúc **confidentiality + integrity/authenticity của ciphertext context**.

Thay vì “encrypt rồi hy vọng dữ liệu không bị sửa”, AEAD tạo authentication tag. Decrypt chỉ được coi là thành công khi tag verify.

~~~text
plaintext + key + nonce + optional AAD
        ↓ AEAD encrypt
ciphertext + tag
~~~

Java Cipher hỗ trợ AEAD modes như GCM qua transformation phù hợp. Với design mới, đây thường là mental model tốt hơn unauthenticated encryption khi payload vừa cần bí mật vừa cần tamper detection.

AEAD không thay thế identity/key management: nếu kẻ tấn công có key hợp lệ, họ vẫn có thể tạo ciphertext/tag hợp lệ.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nonce-uniqueness">Nonce/IV và yêu cầu không tái sử dụng</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Với GCM, **nonce/IV uniqueness dưới cùng key là requirement cực kỳ quan trọng**. Reuse nonce có thể làm lộ quan hệ giữa plaintext và phá authentication security.

Nonce thường không cần bí mật. Điều cần bảo vệ là vòng đời:

- sinh theo strategy bảo đảm không reuse dưới cùng key;
- lưu/transmit nonce cùng ciphertext;
- khi key rotation xảy ra, quản lý namespace/counter/random strategy tương ứng.

Random nonce chỉ an toàn nếu xác suất collision phù hợp scale; counter nonce chỉ an toàn nếu state không rollback/reuse. Chọn strategy theo system architecture thay vì copy một snippet chung.

Không “sửa” nonce reuse bằng cách hash nonce cũ hoặc timestamp tùy ý.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aad-and-authentication-tag">AAD và authentication tag</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

**AAD (Additional Authenticated Data)** là dữ liệu không cần encrypt nhưng cần được ràng buộc vào authentication tag, ví dụ protocol version, record id hoặc header routing.

~~~java
Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
cipher.updateAAD(headerBytes);
byte[] ciphertextAndTag = cipher.doFinal(plaintext);
~~~

Bên decrypt phải cung cấp cùng AAD bytes. Với GCM/CCM, toàn bộ AAD phải được đưa vào bằng `updateAAD(...)` **trước** khi bắt đầu xử lý ciphertext bằng `update(...)` hoặc `doFinal(...)`. Nếu header bị đổi, tag verification thất bại dù ciphertext không đổi.

AAD rất hữu ích để tránh tình huống ciphertext hợp lệ bị chuyển sang context khác. Nhưng AAD contract cần canonical bytes/version rõ ràng giống signed-data contract.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aead-decryption-failure">Xác minh tag và lỗi khi giải mã</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Khi tag không hợp lệ, decryption phải được xem là **lỗi xác thực**, không phải “dữ liệu hơi lỗi”.

Java có thể báo AEADBadTagException từ doFinal khi verification fail. Ứng dụng không nên:

- trả partial plaintext cho business logic trước khi authentication hoàn tất;
- retry với algorithm/key yếu hơn;
- log secret key/plaintext để debug;
- phân biệt quá chi tiết lỗi ra untrusted caller nếu điều đó tạo oracle.

~~~java
try {
    byte[] plaintext = decryptor.doFinal(ciphertextAndTag);
    // chỉ dùng plaintext sau khi doFinal thành công
} catch (AEADBadTagException ex) {
    // reject record/message
}
~~~

Ngữ nghĩa của trạng thái lỗi là một phần của thiết kế bảo mật, không chỉ là xử lý ngoại lệ.
AEAD giả định hai phía đã có symmetric key phù hợp; câu hỏi tiếp theo là làm thế nào các bên thiết lập shared secret đó một cách có cấu trúc.

</details>

- [Quay lại đầu trang](#back-to-top)
