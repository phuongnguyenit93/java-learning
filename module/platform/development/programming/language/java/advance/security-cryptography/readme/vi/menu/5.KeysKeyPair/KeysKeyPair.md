<a id="back-to-top"></a>

# Khóa bí mật, khóa công khai và cặp khóa

## Menu
- [Secret key, public key và private key](#key-models)
- [KeyGenerator và KeyPairGenerator](#key-generation)
- [KeyFactory, SecretKeyFactory và key specification](#key-factory-specification)
- [Ranh giới vòng đời và bảo vệ vật liệu khóa](#key-material-boundary)

## <a id="key-models">Secret key, public key và private key</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Cryptography dùng nhiều loại key vì các primitive có trust model khác nhau.

**Secret key** là dữ liệu bí mật được dùng ở cả hai phía của một symmetric primitive, ví dụ AES hoặc HMAC. Ai có key thường có khả năng thực hiện phép toán tương ứng.

**Public/private key pair** tách quyền: public key có thể phân phối rộng hơn, private key phải được bảo vệ. Tùy primitive, public key dùng để verify signature, encrypt/encapsulate hoặc tham gia key establishment; private key dùng cho phép toán đối ứng.

Trong Java, các type Key, SecretKey, PublicKey và PrivateKey biểu diễn role này. Type object không tự đảm bảo vòng đời an toàn; ứng dụng vẫn phải kiểm soát nơi key đến từ đâu, tồn tại bao lâu và ai có thể đọc/export key material.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="key-generation">KeyGenerator và KeyPairGenerator</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

**Generator tạo key material mới**, khác với factory dựng object từ material đã có.

Với symmetric key, KeyGenerator là abstraction chính:

~~~java
KeyGenerator generator = KeyGenerator.getInstance("AES");
generator.init(256);
SecretKey key = generator.generateKey();
~~~

Với asymmetric key pair, dùng KeyPairGenerator:

~~~java
KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
generator.initialize(2048);
KeyPair pair = generator.generateKeyPair();
~~~

Các con số/algorithm ở ví dụ chỉ minh họa API shape; môi trường thực tế phải theo chính sách bảo mật hiện hành và protocol requirement.

Generator thường dùng SecureRandom từ provider hoặc instance được truyền vào. Vì vậy randomness chapter đứng trước key generation: key algorithm mạnh vẫn mất ý nghĩa nếu key material có thể đoán được.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="key-factory-specification">KeyFactory, SecretKeyFactory và key specification</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Factory giải quyết bài toán khác generator: **chuyển existing material thành Key object hoặc ngược lại**.

KeyFactory thường dùng cho public/private key. Ví dụ public key encoded theo X.509 SubjectPublicKeyInfo có thể được dựng từ X509EncodedKeySpec; private key encoded theo PKCS#8 thường dùng PKCS8EncodedKeySpec.

`SecretKeyFactory` thực hiện vai trò factory cho các cách biểu diễn secret key mà thuật toán hỗ trợ, đặc biệt với password-based key material.

~~~text
brand-new key material
→ KeyGenerator / KeyPairGenerator

cách biểu diễn sẵn có / KeySpec
→ KeyFactory / SecretKeyFactory
~~~

SecretKeySpec là shortcut provider-independent cho raw secret bytes của các algorithm phù hợp, nhưng constructor của nó không xác minh mọi invariant algorithm-specific. Khi algorithm có key parameters hoặc validation riêng, factory/spec chuyên biệt thường biểu diễn contract rõ hơn.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="key-material-boundary">Ranh giới vòng đời và bảo vệ vật liệu khóa</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Key object là điểm Java code chạm vào **vật liệu bí mật có giá trị cao**. Vì vậy vòng đời quan trọng ngang với thuật toán.

Các câu hỏi cần có khi review thiết kế:

- key được generate, derive hay load từ KeyStore/HSM?
- key có export được raw bytes hay provider giữ non-exportable?
- key tồn tại trong memory bao lâu?
- log, exception hoặc serialization có vô tình làm lộ key không?
- rotation/revocation do ứng dụng hay infrastructure sở hữu?

Java type có thể hỗ trợ Destroyable ở một số phần triển khai, nhưng không nên giả định gọi destroy là xóa mọi bản sao key khỏi toàn bộ heap/provider/native memory.

Module này dạy API boundary. Chính sách cấp phát, rotation, backup, escrow và hardware protection thuộc secret/PKI infrastructure.
Khi mô hình key đã rõ, có thể bắt đầu nhóm primitive integrity/authenticity từ trường hợp đơn giản nhất: digest không dùng key.

</details>

- [Quay lại đầu trang](#back-to-top)
