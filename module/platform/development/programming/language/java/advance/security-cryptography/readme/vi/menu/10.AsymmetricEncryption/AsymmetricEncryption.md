<a id="back-to-top"></a>

# Mã hóa bất đối xứng

## Menu
- [Mô hình mã hóa bất đối xứng](#asymmetric-encryption-model)
- [Vai trò public key và private key](#public-private-key-roles)
- [Giới hạn và chi phí của mã hóa bất đối xứng](#asymmetric-encryption-limits)
- [Mô hình mã hóa lai](#hybrid-encryption-model)

## <a id="asymmetric-encryption-model">Mô hình mã hóa bất đối xứng</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Asymmetric encryption dùng public/private key pair để tách người có thể **bảo vệ dữ liệu cho receiver** khỏi người có thể **giải mã**.

Trong mô hình phổ biến:

~~~text
plaintext + receiver public key
        ↓ encrypt
ciphertext

ciphertext + receiver private key
        ↓ decrypt
plaintext
~~~

Public key có thể được phân phối; private key phải được bảo vệ. Nhưng việc có một public key chưa đủ: sender còn phải biết public key đó thực sự thuộc receiver nào. Certificate/trust là lớp giúp gắn key với identity trong nhiều protocol.

Asymmetric encryption không phải digital signature đảo chiều; mục tiêu, padding/scheme và security proof khác nhau.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="public-private-key-roles">Vai trò public key và private key</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Key role phải đi theo primitive, không theo trực giác “public/private cái nào cũng dùng được hai chiều”.

Với public-key encryption, public key thường dùng để encrypt cho bên sở hữu private key. Với signature, private key sign và public key verify. Với key agreement, mỗi bên có key pair và kết hợp private key của mình với public key của peer theo protocol.

Trong Java, public-key encryption vẫn đi qua `Cipher`; điểm khác chính là key role:

~~~java
Cipher encryptor = Cipher.getInstance(transformation);
encryptor.init(Cipher.ENCRYPT_MODE, receiverPublicKey);
byte[] ciphertext = encryptor.doFinal(plaintext);

Cipher decryptor = Cipher.getInstance(transformation);
decryptor.init(Cipher.DECRYPT_MODE, receiverPrivateKey);
byte[] recovered = decryptor.doFinal(ciphertext);
~~~

`transformation` phải đến từ protocol/scheme đã được xác định rõ. Không dùng chuỗi mơ hồ như `"RSA"` để né việc lựa chọn padding/encoding scheme.

Cùng interface PublicKey/PrivateKey không có nghĩa mọi key type hỗ trợ mọi engine. Một Ed25519 key dành cho signature không tự trở thành RSA encryption key.

Vì vậy code nên derive algorithm/protocol contract từ use case và metadata đáng tin cậy, không thử key trên engine rồi fallback ngẫu nhiên khi gặp InvalidKeyException.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="asymmetric-encryption-limits">Giới hạn và chi phí của mã hóa bất đối xứng</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Các phép toán public-key thường chậm hơn symmetric operations và có giới hạn kích thước đầu vào theo scheme/key size. Ví dụ RSA encryption không được dùng như stream cipher để encrypt file lớn từng chunk theo cách tùy ý.

Padding/encoding scheme cũng là phần bắt buộc của security. “RSA” không đủ để mô tả một protocol an toàn; transformation/scheme phải được specification quy định.

Asymmetric crypto vì thế thường dùng để bảo vệ **small secret material** hoặc thực hiện key establishment, còn payload lớn được bảo vệ bằng symmetric AEAD.

Đây là lý do hybrid encryption phổ biến: mỗi primitive làm phần việc nó phù hợp nhất.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hybrid-encryption-model">Mô hình mã hóa lai</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Hybrid encryption kết hợp asymmetric và symmetric crypto:

~~~text
generate random content-encryption key
        ↓
encrypt payload bằng symmetric AEAD
        ↓
protect/establish content key bằng public-key mechanism
        ↓
package protected key + nonce + ciphertext + metadata
~~~

Receiver dùng phép toán private-key để lấy hoặc derive content key, rồi symmetric key decrypt payload.

Mô hình này tránh dùng phép toán public-key tốn kém cho toàn bộ dữ liệu và cho phép AEAD bảo vệ payload hiệu quả.

Không nên tự thiết kế format hybrid nếu một standard protocol/container đã tồn tại. Những chi tiết như key wrapping, context binding, algorithm identifiers và versioning ảnh hưởng đến security và interoperability.
Hybrid encryption giải quyết hiệu năng và key transport, nhưng confidentiality vẫn chưa tự bảo đảm tamper detection; bước kế tiếp là authenticated encryption/AEAD.

</details>

- [Quay lại đầu trang](#back-to-top)
