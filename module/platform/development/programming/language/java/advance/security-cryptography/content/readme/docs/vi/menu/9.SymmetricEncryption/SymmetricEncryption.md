<a id="back-to-top"></a>

# Mã hóa đối xứng

## Menu
- [Mô hình mã hóa đối xứng](#symmetric-encryption-model)
- [Cipher transformation, mode và padding](#cipher-transformation)
- [IV và tham số thuật toán](#iv-and-parameters)
- [Luồng mã hóa và giải mã](#symmetric-encryption-workflow)

## <a id="symmetric-encryption-model">Mô hình mã hóa đối xứng</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Symmetric encryption dùng **cùng secret key cho phép toán encrypt/decrypt tương ứng**. Nó phù hợp với lượng dữ liệu lớn vì thường có chi phí thấp hơn asymmetric encryption.

~~~text
plaintext + secret key
        ↓ encrypt
ciphertext

ciphertext + same secret key
        ↓ decrypt
plaintext
~~~

Bảo mật của hệ thống không chỉ nằm ở algorithm. Secret key phải được sinh hoặc dẫn xuất đúng, phân phối an toàn, xoay vòng theo chính sách và không bị log/export ngoài ý muốn.

“Symmetric” không có nghĩa một protocol bắt buộc dùng **một key duy nhất cho cả hai hướng truyền**. Nhiều protocol derive key riêng cho client→server và server→client để tách phạm vi sử dụng key, dù mỗi key riêng lẻ vẫn là symmetric key.

Symmetric encryption giải quyết confidentiality. Nếu mode không có authentication, ứng dụng vẫn cần cơ chế phát hiện tampering. Vì vậy chapter này xây nền Cipher model; chapter AEAD sẽ chỉ ra cách confidentiality và integrity được gắn thành một contract.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cipher-transformation">Cipher transformation, mode và padding</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Java Cipher thường được tạo bằng transformation:

~~~text
algorithm / mode / padding
~~~

Ví dụ AES/GCM/NoPadding hoặc AES/CBC/PKCS5Padding. Ba phần quyết định hành vi khác nhau, nên chỉ viết "AES" có thể để provider default chọn phần còn lại theo cách không phù hợp chính sách bảo mật.

**Algorithm** xác định primitive lõi. **Mode** xác định cách block cipher xử lý nhiều block và tham số như IV/nonce. **Padding** xác định cách xử lý input không khớp block size khi mode cần padding.

Code review nên nhìn transformation như một security contract, không phải string cấu hình vô hại.

Trong design mới cần tamper detection, ưu tiên mode AEAD phù hợp thay vì dùng unauthenticated mode rồi tự nghĩ cách ghép integrity.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="iv-and-parameters">IV và tham số thuật toán</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Nhiều mode cần **IV hoặc nonce** ngoài secret key. Giá trị này thường không cần giữ bí mật, nhưng phải thỏa property mà mode yêu cầu: có mode cần unpredictability, có mode đặc biệt cần uniqueness.

Không được suy “IV không phải secret nên dùng gì cũng được”. Reuse IV/nonce có thể phá security nghiêm trọng.

Java biểu diễn parameter qua các spec như IvParameterSpec hoặc GCMParameterSpec. Khi encrypt, ứng dụng thường lưu/transmit parameter cần thiết cùng ciphertext để decrypt bên kia có đủ context.

~~~text
stored/transmitted package
= algorithm/version metadata
+ IV/nonce
+ ciphertext
(+ authentication tag tùy mode)
~~~

Key là secret; IV/nonce thường là public parameter nhưng vòng đời vẫn phải đúng protocol.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="symmetric-encryption-workflow">Luồng mã hóa và giải mã</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Luồng xử lý tổng quát của Cipher:

~~~java
Cipher cipher = Cipher.getInstance(transformation);
cipher.init(Cipher.ENCRYPT_MODE, key, parameters);
byte[] ciphertext = cipher.doFinal(plaintext);

Cipher decryptor = Cipher.getInstance(transformation);
decryptor.init(Cipher.DECRYPT_MODE, key, parameters);
byte[] recovered = decryptor.doFinal(ciphertext);
~~~

Không hard-code ví dụ này thành công thức cho môi trường thực tế nếu transformation/parameters chưa được protocol quy định.

Cipher có state và init mode cụ thể. Một instance đã init cho encryption không phải immutable service để share tùy ý giữa threads.

Quan trọng hơn, decrypt thành công không luôn đồng nghĩa ciphertext đáng tin. Với unauthenticated encryption, dữ liệu có thể bị biến đổi. Chapter AEAD sẽ thêm authentication tag để biến việc chỉnh sửa thành **lỗi xác minh**.
Symmetric encryption hiệu quả cho payload nhưng đặt ra bài toán phân phối/bảo vệ shared key; asymmetric encryption bổ sung một mô hình key khác để giải quyết các flow phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)
