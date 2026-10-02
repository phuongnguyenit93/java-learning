<a id="back-to-top"></a>

# Thiết lập bí mật dùng chung

## Menu
- [Bài toán thiết lập bí mật dùng chung](#key-establishment-purpose)
- [Mô hình KeyAgreement](#key-agreement-model)
- [Mô hình Key Encapsulation Mechanism](#kem-model)
- [KeyAgreement và KEM khác nhau thế nào?](#agreement-vs-kem)

## <a id="key-establishment-purpose">Bài toán thiết lập bí mật dùng chung</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Hai bên muốn dùng symmetric crypto nhưng chưa có shared secret gặp bài toán **thiết lập key material**. Gửi raw secret key qua kênh mà kẻ tấn công đọc được sẽ phá toàn bộ mục tiêu.

Key establishment dùng asymmetric/public information để hai bên đạt được secret material mà không truyền secret đó trực tiếp dưới dạng plaintext.

Có hai mental model trong Java 21:

- **KeyAgreement**: các bên đóng góp key material để tính cùng shared secret;
- **KEM**: sender encapsulate để tạo secret key + encapsulation message, receiver decapsulate bằng private key để lấy cùng secret key.

Sau bước này, protocol thường còn cần derivation/context binding trước khi có session keys cuối cùng.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="key-agreement-model">Mô hình KeyAgreement</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

KeyAgreement engine biểu diễn family như Diffie-Hellman/ECDH.

Flow khái niệm:

~~~text
Alice private + Bob public
        ↓
shared secret

Bob private + Alice public
        ↓
same shared secret
~~~

Java API thường có `getInstance`, `init(privateKey)`, `doPhase(peerPublicKey, true)`, rồi `generateSecret()`:

~~~java
KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
agreement.init(alicePrivateKey);
agreement.doPhase(bobPublicKey, true);
byte[] sharedSecret = agreement.generateSecret();
~~~

Raw shared secret thường chỉ là vật liệu đầu vào cho bước derivation/key schedule của protocol; không nên mặc định lấy bytes đó dùng trực tiếp làm key của ứng dụng nếu specification chưa định nghĩa như vậy.

Public key exchange **không tự xác thực peer**. Nếu kẻ tấn công thay public key ở giữa và protocol không có certificate, signature hoặc authenticated channel, hai phía có thể thiết lập bí mật với kẻ tấn công thay vì với nhau.

Vì vậy key agreement và peer authentication là hai vấn đề riêng nhưng phải được kết hợp đúng trong protocol như TLS.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="kem-model">Mô hình Key Encapsulation Mechanism</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Java 21 có KEM API cho Key Encapsulation Mechanism. Mental model khác KeyAgreement:

~~~text
receiver có public/private key pair

sender:
public key
  ↓ encapsulate
secret key + encapsulation message

receiver:
private key + encapsulation message
  ↓ decapsulate
same secret key
~~~

Encapsulation message có thể truyền công khai; private key cho phép receiver recover secret. Flow Java 21 có hình dạng trực tiếp:

~~~java
KEM kem = KEM.getInstance("DHKEM");

KEM.Encapsulated encapsulated =
        kem.newEncapsulator(receiverPublicKey).encapsulate();

SecretKey senderSecret = encapsulated.key();
byte[] message = encapsulated.encapsulation();

SecretKey receiverSecret =
        kem.newDecapsulator(receiverPrivateKey).decapsulate(message);
~~~

`encapsulate()` mặc định trả một `SecretKey` mang algorithm name `Generic`; secret này thường được đưa tiếp vào KDF/key schedule của protocol. Concrete KEM algorithm và provider support vẫn phải theo protocol bạn triển khai.

KEM đặc biệt hữu ích khi protocol được thiết kế quanh cơ chế encapsulation thay vì interactive agreement. Nếu không khóa provider ở `KEM.getInstance(...)`, Java 21 còn cho phép provider được lựa chọn khi tạo encapsulator/decapsulator dựa trên key và parameter tương ứng; không nên giả định chỉ việc tạo `KEM` object đã cố định provider cho mọi phép toán.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="agreement-vs-kem">KeyAgreement và KEM khác nhau thế nào?</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

KeyAgreement và KEM đều dẫn đến shared secret nhưng interaction model khác:

| | KeyAgreement | KEM |
| --- | --- | --- |
| input chính | private key + peer public key | receiver public/private key + encapsulation |
| output | shared secret | secret key + encapsulation message |
| interaction | thường hai phía có key contribution | sender encapsulate cho receiver |
| Java engine | KeyAgreement | KEM |

Không nên chọn dựa trên “API nào mới hơn”. Protocol specification, khả năng của peer và thiết kế bảo mật mới là những yếu tố quyết định cơ chế.

Cả hai cũng không tự giải quyết certificate trust, identity, replay hay vòng đời khóa. Những vấn đề đó được kết hợp ở lớp protocol.
Shared secret vừa thiết lập thường còn phải đi qua KDF/key schedule hoặc được dẫn xuất từ input khác như password; vì vậy bước kế tiếp là key derivation.

</details>

- [Quay lại đầu trang](#back-to-top)
