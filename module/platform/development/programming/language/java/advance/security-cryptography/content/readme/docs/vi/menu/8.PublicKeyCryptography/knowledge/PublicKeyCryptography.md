<a id="back-to-top"></a>

# 8. Khóa công khai

## Menu
- [8.1 Khóa công khai, khóa riêng và quan hệ với mã hóa hoặc chữ ký số](#public-key-cryptography-001)
- [8.2 Tạo cặp khóa bằng KeyPairGenerator với kích thước hoặc bộ tham số rõ ràng](#public-key-cryptography-002)
- [8.3 RSA, EC, EdDSA và XDH: phân biệt mục đích của từng họ thuật toán](#public-key-cryptography-003)
- [8.4 Chuyển đổi khóa bằng KeyFactory và KeySpec](#public-key-cryptography-004)
- [8.5 Nhập, xuất khóa công khai X.509 SubjectPublicKeyInfo và khóa riêng PKCS#8](#public-key-cryptography-005)
- [8.6 Phân biệt DER, PEM, khóa thô, khóa mã hóa và chứng thư](#public-key-cryptography-006)
- [8.7 RSA-OAEP, giới hạn kích thước bản rõ và mô hình mã hóa lai](#public-key-cryptography-007)
- [8.8 Cấu hình OAEPParameterSpec, digest, MGF1 và label thống nhất giữa hai phía](#public-key-cryptography-008)
- [8.9 Giới hạn của việc dùng thuật toán bất đối xứng thay cho mã hóa dữ liệu khối lớn](#public-key-cryptography-009)

## <a id="public-key-cryptography-001">8.1 Khóa công khai, khóa riêng và quan hệ với mã hóa hoặc chữ ký số</a>

<details>
<summary>Xem chi tiết</summary>

# 8.1 Khóa công khai, khóa riêng và quan hệ với mã hóa hoặc chữ ký số

## Mục tiêu học tập

Khi gặp một cặp khóa trong Java (`PublicKey` và `PrivateKey`), cần phân biệt được **ai sở hữu khóa, ai được biết khóa và khóa dùng cho phép toán gì**. Hai thao tác quan trọng nhưng phục vụ các mục tiêu khác nhau:

- **Mã hóa khóa công khai (public-key encryption):** người gửi mã hóa bằng **khóa công khai của người nhận**; người nhận giải mã bằng **khóa riêng tương ứng**. Mục tiêu chính là **tính bí mật**.
- **Chữ ký số (digital signature):** người ký tạo chữ ký bằng **khóa riêng của người ký**; người kiểm tra dùng **khóa công khai tương ứng** để xác minh. Mục tiêu là **tính toàn vẹn và xác thực nguồn gốc theo danh tính đã gắn với khóa**.

Dù đều dùng khóa bất đối xứng, **mã hóa không phải chữ ký**, còn **xác minh chữ ký không phải giải mã thông điệp**. Cụm từ “mã hóa bằng khóa riêng để ký” dễ gây hiểu nhầm và không mô tả đúng các scheme chữ ký hiện đại như RSA-PSS hoặc Ed25519.

Bài học nằm trong chủ đề **Khóa công khai**, tập trung vào vai trò hai loại khóa, quan hệ với mã hóa/chữ ký và cách dùng Java Security API. Các ví dụ chạy trên Java 17+.

## 1. Cặp khóa bất đối xứng thực chất là gì?

**Mật mã đối xứng** như AES sử dụng cùng một khóa bí mật (hoặc khóa có quan hệ trực tiếp) cho mã hóa/giải mã. **Mật mã bất đối xứng** sử dụng một **key pair** gồm:

- `public key`: được phép công bố, phân phối cho các bên cần mã hóa cho chủ sở hữu hoặc xác minh chữ ký của chủ sở hữu.
- `private key`: phải được chủ sở hữu bảo vệ, dùng cho thao tác cần chứng minh quyền kiểm soát khóa hoặc lấy lại bí mật trong scheme tương ứng.

Hai khóa được tạo cùng nhau và **liên hệ toán học**, nhưng trong một thuật toán an toàn với kích thước khóa phù hợp, việc biết public key **không đủ để tính private key trong thực tế**. “Khóa công khai” không có nghĩa có thể tin khóa đó thuộc về người bất kỳ tự xưng; **tính xác thực của liên kết giữa public key và danh tính** là một vấn đề riêng.

Ví dụ RSA dựa trên các tham số như modulus `n`, public exponent `e` và private exponent `d`; khó khăn tính toán liên quan đến cấu trúc RSA giúp bảo vệ private key. Với hệ mật dựa trên đường cong elliptic, cách xây dựng và phép toán khác RSA. Vì thế không nên tự áp dụng công thức hoặc thao tác RSA cho mọi cặp `PublicKey`/`PrivateKey`.

### 1.1 Khóa không tự nói lên mục đích

`PublicKey` và `PrivateKey` là **interface** của Java, không có nghĩa mọi khóa dùng được cho mọi engine:

| Thuật toán/cặp khóa | Mục đích thường gặp | Dịch vụ Java liên quan |
| --- | --- | --- |
| RSA | Mã hóa khóa/ngắn bằng RSA-OAEP; chữ ký với RSA-PSS hoặc scheme được cho phép | `Cipher`, `Signature` |
| Ed25519 | **Chữ ký số** EdDSA | `Signature` (`Ed25519`) |
| X25519 | **Thỏa thuận khóa** (key agreement), tạo bí mật chung rồi đưa qua KDF | `KeyAgreement` (`X25519`) |
| EC với ECDSA | Chữ ký số bằng thuật toán phù hợp | `Signature` (ví dụ `SHA256withECDSA`) |
| EC với ECDH | Thỏa thuận khóa, dùng khóa riêng bên mình và khóa công khai bên kia | `KeyAgreement` |

**Ed25519 không phải thuật toán mã hóa dữ liệu; X25519 không phải thuật toán chữ ký.** Đối với RSA, cùng kiểu khóa có thể hỗ trợ hai loại thao tác khi provider và chính sách cho phép, nhưng ứng dụng thực tế thường sử dụng **các cặp khóa riêng biệt cho mã hóa và ký** để quản lý mục đích, quyền và thời gian sống của khóa.

## 2. Quan hệ giữa public/private key và mã hóa

Giả sử **Alice** muốn gửi bí mật cho **Bob**. Bob tạo cặp khóa `(Bob.public, Bob.private)` và cung cấp public key **đã được xác thực thuộc về Bob** cho Alice.

```text
Alice (người gửi)                              Bob (người nhận)
-----------------                              ----------------
Thông điệp plaintext
        |
        | Encrypt(Bob.public, plaintext)
        v
   Ciphertext  ------------------------------->  Ciphertext
                                                      |
                                             Decrypt(Bob.private)
                                                      |
                                                      v
                                                   Plaintext
```

Với scheme mã hóa khóa công khai đúng và tham số phù hợp:

1. **Bên gửi không cần biết khóa riêng của Bob**; chỉ cần biết khóa công khai đúng của Bob.
2. **Chỉ Bob (hoặc bên nào đang nắm được private key tương ứng) mới có thể giải mã** trong mô hình an toàn của scheme.
3. Ai có được Bob.public cũng có thể tạo ciphertext gửi cho Bob. Vì vậy **mã hóa bằng khóa công khai của Bob không tự xác thực người gửi là Alice**.
4. Nếu kẻ tấn công thay Bob.public bằng khóa của mình, Alice có thể mã hóa cho kẻ tấn công. Cần xác minh nguồn gốc public key qua chứng chỉ, fingerprint đã đối chiếu, registry đáng tin hoặc một giao thức phân phối khóa được xác thực.

### 2.1 Mã hóa RSA phải dùng scheme an toàn

RSA là phép toán trên các số nguyên có cấu trúc toán học; **không dùng “raw RSA”**. Với JCA, ví dụ phổ biến là **RSAES-OAEP**:

```java
// Minh họa trong phương thức có xử lý ngoại lệ và tham số phù hợp:
Cipher cipher = Cipher.getInstance(
        "RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
```

Chữ `ECB` trong tên transformation RSA của JCE là quy ước cú pháp lịch sử của engine, **không có nghĩa RSA đang mã hóa các khối AES theo ECB**. Scheme phải nêu **OAEP hash, MGF1 hash và label/PSource** bằng tham số phù hợp để hai bên tương thích. Đặc biệt đừng đoán rằng `OAEPWithSHA-256AndMGF1Padding` tự đặt mọi MGF1 digest thành SHA-256 trên mọi provider; ví dụ runnable bên dưới truyền `OAEPParameterSpec` rõ ràng.

**Giới hạn độ dài:** RSA-OAEP chỉ mã hóa plaintext ngắn. Với RSA modulus 2048 bit (`k = 256` byte) và OAEP dùng SHA-256 (`hLen = 32` byte), giới hạn thông điệp là:

\[
mLen \leq k - 2hLen - 2 = 256 - 64 - 2 = 190\ \text{byte}.
\]

Không thể dùng lời gọi RSA-OAEP để mã hóa nguyên file lớn theo cách chia thành nhiều “block RSA” tùy tiện. Thiết kế thực tế thường dùng **hybrid encryption**: sinh khóa AES ngẫu nhiên, mã hóa dữ liệu bằng **AES-GCM**, rồi bảo vệ khóa AES bằng một cơ chế mã hóa/đóng gói khóa bất đối xứng phù hợp (ví dụ RSA-OAEP trong hệ thống áp dụng RSA). Phía nhận dùng private key lấy lại khóa AES để giải mã dữ liệu. Phải quy định format, nonce, tag, key ID và các metadata được xác thực.

### 2.2 Mã hóa cần gì để an toàn ngoài key pair?

- Public key người nhận **phải đúng người nhận**; private key người nhận được bảo vệ.
- Phải lựa chọn **scheme và tham số** an toàn thay vì `Cipher.getInstance("RSA")` rồi để provider quyết định padding.
- Một bản mã của scheme mã hóa bất đối xứng **không đồng nghĩa bằng chứng ai đã gửi**; khi cần tính xác thực nguồn, phải thêm chữ ký/giao thức có xác thực.
- Nếu dùng AES-GCM trong hybrid encryption, phải bảo đảm nonce duy nhất cho mỗi khóa AES, xử lý authentication tag và ràng buộc context/AAD cần thiết.
- Encryption alone không cung cấp cơ chế **chống replay**: bản mã hợp lệ có thể bị gửi lại nếu protocol không theo dõi bản ghi/thứ tự/nonce ứng dụng.

## 3. Quan hệ giữa public/private key và chữ ký số

Lần này Alice muốn Bob biết thông điệp đúng là do **người giữ Alice.private** ký và nội dung không bị thay đổi.

```text
Alice (người ký)                                  Bob (người kiểm tra)
---------------                                  ---------------------
Thông điệp M
     |
     | Sign(Alice.private, M)
     v
Chữ ký S và M ------------------------------------> M, S
                                                     |
                                          Verify(Alice.public, M, S)
                                                     |
                                               true / false
```

Các bước quan trọng:

1. Alice sở hữu private key phù hợp với thuật toán chữ ký và tạo `signature` trên **dữ liệu chính xác** cần chứng thực.
2. Bob đã biết public key **được gắn đáng tin với danh tính Alice**, nhận thông điệp và chữ ký, rồi gọi `verify(public, message, signature)`.
3. Nếu kiểm tra thành công, chữ ký phù hợp với **public key được cung cấp** và các byte dữ liệu đã kiểm tra. Để kết luận “Alice đã ký”, Bob còn phải **xác thực public key thật sự thuộc Alice**.
4. Thay đổi message, context quan trọng hoặc chữ ký sẽ làm verification thất bại (với scheme an toàn và triển khai đúng).
5. Chữ ký số **không mã hóa thông điệp**. Bất cứ ai đọc được message vẫn đọc được nó, kể cả chữ ký hợp lệ.

Chữ ký cung cấp nền tảng cho **toàn vẹn**, **xác thực nguồn gốc**, và hỗ trợ **chống chối bỏ (non-repudiation)** khi quản trị khóa, định danh, chứng cứ và chính sách phù hợp. Nó **không tự** cung cấp tính bí mật, bảo đảm người ký là người nào ngoài danh tính đã xác thực, hay chống gửi lại thông điệp hợp lệ.

### 3.1 Vì sao ký không phải “mã hóa bằng khóa riêng”?

Cách nói “lấy private key mã hóa, lấy public key giải mã” là hình ảnh đơn giản hóa từ một số phép toán RSA và **sai cho chữ ký số hiện đại**:

- RSA-PSS là scheme **chữ ký**, có băm, encoding, salt và kiểm tra cấu trúc được xác định; API `Signature.sign()` tạo ra signature, `Signature.verify()` kiểm tra.
- `Ed25519` ký và xác minh bằng phép toán trên đường cong; **không có thao tác “giải mã chữ ký thành thông điệp”**.
- Phần lớn scheme chữ ký kiểm tra chữ ký dựa trên **message gốc hoặc digest được định nghĩa bởi scheme**; không dùng `Cipher` với `ENCRYPT_MODE` và private key thay cho `Signature`.

Trong Java hãy sử dụng đúng engine `Signature`; với RSA-PSS nên thống nhất digest, MGF1, salt length và trailer field, tránh phụ thuộc tham số mặc định của provider.

### 3.2 Mã hóa và chữ ký có thể dùng đồng thời

Khi Alice gửi bí mật **và** muốn Bob xác minh Alice, giao thức phải có cả bảo vệ **bí mật** và **xác thực nguồn**:

```text
Bí mật của Bob:        Encrypt(Bob.public, ...)
Danh tính của Alice:   Sign(Alice.private, dữ liệu/ngữ cảnh quy định)
```

Một giao thức tổng hợp phải nêu rõ **ký dữ liệu nào** (plaintext, ciphertext hoặc envelope), xử lý danh tính và metadata thế nào, thứ tự hoạt động, các trường AAD/signature, chống replay và cách thông báo lỗi. Không tự tùy tiện nối hai primitive rồi khẳng định có mọi thuộc tính an toàn. Các chuẩn triển khai như CMS/JWE/JWS/TLS có mô hình và chi tiết bảo mật riêng; chọn chuẩn thích hợp cho bài toán thay vì tạo protocol từ phỏng đoán.

## 4. So sánh trực tiếp hai thao tác

| Câu hỏi | Mã hóa bất đối xứng | Chữ ký số |
| --- | --- | --- |
| Mục tiêu | Giữ **bí mật** nội dung/khóa phiên | Chứng minh **tính toàn vẹn và nguồn gốc theo khóa đã xác thực** |
| Ai dùng public key và để làm gì? | **Người gửi** dùng public key **của người nhận** để mã hóa | **Người kiểm tra** dùng public key **của người ký** để verify |
| Ai dùng private key và để làm gì? | **Người nhận** dùng private key của mình để giải mã | **Người ký** dùng private key của mình để sign |
| Đầu ra | Ciphertext (và metadata cần thiết) | Signature (thường gửi cùng message) |
| Message có đọc được công khai? | Không, nếu scheme/khóa/format an toàn | Có, nếu message không được mã hóa riêng |
| Bảo đảm danh tính người gửi? | Không, chỉ vì ai cũng dùng được public key người nhận | Chỉ khi public key verifier được **gắn đáng tin với danh tính người ký** |
| Chống replay? | Không tự có | Không tự có |
| Java API | `Cipher` với scheme mã hóa như RSA-OAEP | `Signature` với scheme ký như RSA-PSS/Ed25519 |

**Câu nhớ nhanh:** *Mã hóa cho Bob thì dùng public key của Bob; ký với tư cách Alice thì dùng private key của Alice.* Khi nhận, Bob dùng **private key của Bob** để giải mã và **public key của Alice** để kiểm tra chữ ký.

## 5. Java 17: mô hình `KeyPair`, `PublicKey`, `PrivateKey`

Các API liên quan nằm trong `java.security`, `java.security.spec` và `javax.crypto`:

| API | Công dụng đúng |
| --- | --- |
| `KeyPairGenerator` | Tạo cặp khóa mới theo thuật toán (ví dụ `RSA`, `Ed25519`, `X25519`). |
| `KeyPair` | Giữ cặp khóa; `getPublic()` và `getPrivate()` trả các interface tương ứng. |
| `PublicKey` / `PrivateKey` | Đối tượng khóa cho `Cipher`, `Signature`, `KeyAgreement` tùy loại thuật toán. |
| `Cipher` | Mã hóa/giải mã theo transformation như `RSA/ECB/OAEPWithSHA-256AndMGF1Padding`. |
| `Signature` | `initSign(private)`, `update`, `sign`; hoặc `initVerify(public)`, `update`, `verify`. |
| `KeyAgreement` | Thỏa thuận bí mật chung, ví dụ với X25519 hoặc ECDH; **không giống encrypt/verify**. |
| `KeyFactory` | Tái tạo đối tượng khóa từ `KeySpec` như X.509 public key hoặc PKCS#8 private key. |
| `KeyStore` / KMS / HSM | Lưu trữ hoặc truy xuất khóa theo cơ chế đã được bảo vệ; có thể dùng private key **không xuất được**. |

`KeyPairGenerator.getInstance("RSA")` chỉ xác định thuật toán tạo cặp khóa. Muốn dùng cặp khóa ấy cho **mã hóa** thì gọi `Cipher` với RSA-OAEP; muốn dùng cho **chữ ký** thì gọi `Signature` với RSA-PSS. Không dùng `KeyPairGenerator` thay cho phép mã hóa/ký.

Có thể tạo một cặp RSA minh họa như sau:

```java
// Đoạn minh họa trong phương thức có xử lý checked exception:
KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
generator.initialize(2048);
KeyPair pair = generator.generateKeyPair();
PublicKey publicKey = pair.getPublic();
PrivateKey privateKey = pair.getPrivate();
```

Số bit RSA 2048 ở đây để bài thực hành nhanh và đầu ra dễ kiểm tra; hệ thống thực tế chọn kích thước khóa, vòng đời và bảo vệ khóa theo **chính sách bảo mật áp dụng** (chẳng hạn RSA 3072 khi yêu cầu độ an toàn dài hạn cao hơn). Không chỉ chọn bit length rồi bỏ qua scheme, provider và chu kỳ luân chuyển.

## 6. Ví dụ Java 17+ hoàn chỉnh: RSA-OAEP và RSA-PSS

Chương trình sau dùng **hai cặp RSA riêng biệt** để làm rõ hai vai trò:

- `recipient`: public key mã hóa, private key giải mã.
- `signer`: private key ký, public key xác minh.

Nó cũng thiết lập OAEP **SHA-256 + MGF1(SHA-256)** và PSS **SHA-256 + MGF1(SHA-256), salt 32 byte, trailer 1** một cách tường minh. Việc thiết lập tham số nhất quán giúp tránh sai khác do default của provider. Tạo file **`AsymmetricRolesDemo.java`**:

```java
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

public final class AsymmetricRolesDemo {
    private static final OAEPParameterSpec OAEP =
            new OAEPParameterSpec(
                    "SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
                    PSource.PSpecified.DEFAULT);

    private static final PSSParameterSpec PSS =
            new PSSParameterSpec(
                    "SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
                    32, 1);

    private static byte[] encryptFor(PublicKey recipient, byte[] data)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(
                "RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, recipient, OAEP);
        return cipher.doFinal(data);
    }

    private static byte[] decryptWith(PrivateKey recipient, byte[] ciphertext)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(
                "RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.DECRYPT_MODE, recipient, OAEP);
        return cipher.doFinal(ciphertext);
    }

    private static byte[] signWith(PrivateKey signer, byte[] data)
            throws GeneralSecurityException {
        Signature signature = Signature.getInstance("RSASSA-PSS");
        signature.setParameter(PSS);
        signature.initSign(signer);
        signature.update(data);
        return signature.sign();
    }

    private static boolean verifyWith(
            PublicKey signer, byte[] data, byte[] signed)
            throws GeneralSecurityException {
        Signature signature = Signature.getInstance("RSASSA-PSS");
        signature.setParameter(PSS);
        signature.initVerify(signer);
        signature.update(data);
        return signature.verify(signed);
    }

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);

        KeyPair recipient = generator.generateKeyPair();
        KeyPair signer = generator.generateKeyPair();

        byte[] message = "Hello public-key crypto"
                .getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = encryptFor(recipient.getPublic(), message);
        byte[] recovered = decryptWith(recipient.getPrivate(), encrypted);
        System.out.println("Decryption matches: " +
                Arrays.equals(message, recovered));

        byte[] signature = signWith(signer.getPrivate(), message);
        System.out.println("Signature valid: " +
                verifyWith(signer.getPublic(), message, signature));

        byte[] changed = message.clone();
        changed[0] ^= 1;
        System.out.println("Modified message valid: " +
                verifyWith(signer.getPublic(), changed, signature));

        System.out.println("RSA ciphertext bytes: " + encrypted.length);
        System.out.println("RSA signature bytes: " + signature.length);
    }
}
```

Biên dịch và chạy bằng JDK 17+ có provider hỗ trợ OAEP/PSS với tham số đã dùng:

```bash
javac --release 17 AsymmetricRolesDemo.java
java AsymmetricRolesDemo
```

**Stdout** (các byte ciphertext và chữ ký thay đổi giữa các lần chạy vì có ngẫu nhiên, nhưng các dòng sau vẫn như nhau):

```text
Decryption matches: true
Signature valid: true
Modified message valid: false
RSA ciphertext bytes: 256
RSA signature bytes: 256
```

Giải thích chi tiết:

1. `recipient.getPublic()` được truyền vào `Cipher.ENCRYPT_MODE`; **chỉ** `recipient.getPrivate()` tương ứng mới thực hiện được `Cipher.DECRYPT_MODE` của bản ghi trong demo.
2. `signer.getPrivate()` được truyền vào `Signature.initSign`, còn `signer.getPublic()` vào `Signature.initVerify`. Code không dùng `Cipher` để ký.
3. `Signature.update(message)` cung cấp **chính xác byte** của thông điệp cho scheme ký; PSS thực hiện hashing/encoding nội bộ theo bộ tham số đã chỉ định. Không cần tự băm thông điệp rồi đưa digest vào `update` như thể đó là message gốc.
4. Đổi 1 byte thông điệp khiến `verify()` trả `false`; nếu dùng **public key của cặp khác** cũng không được xem là chữ ký hợp lệ.
5. RSA-2048 có modulus dài 256 byte, nên RSA-OAEP tạo ciphertext **256 byte**, chữ ký RSA-PSS cũng dài **256 byte**. Kích thước này phụ thuộc modulus, **không** phụ thuộc độ dài message ngắn đã chọn.
6. Hai key pair là **riêng biệt** dù đều có thuật toán RSA. Ở production cần ràng buộc mỗi khóa với identity, purpose, key usage và metadata; không mặc định mọi RSA key được phép dùng cho mọi mục đích.
7. Tên `RSASSA-PSS` và transformation OAEP có thể yêu cầu provider phù hợp. Mã chạy được trên môi trường hỗ trợ các scheme/tham số đã yêu cầu; tên có trong danh mục chuẩn không có nghĩa mọi Java SE implementation hỗ trợ mọi tổ hợp. Hãy kiểm tra JDK/provider mục tiêu.

Đây là demo **in-memory**: chương trình sinh khóa mới mỗi lần chạy và không có bên giao tiếp qua mạng. Để đưa vào dịch vụ thực, cần một cơ chế phân phối public key có xác thực, kho khóa riêng an toàn, định dạng message/signature/ciphertext có phiên bản, tham số và xử lý lỗi rõ ràng.

## 7. Lưu public key, bảo vệ private key và kiểm tra danh tính

### 7.1 Public key có thể công khai nhưng phải xác thực

Một public key chỉ nói được rằng **khóa đó có quan hệ toán học với private key nào đó**, chưa tự chứng minh thuộc Alice hay Bob. Các cách xây dựng liên kết danh tính:

- **X.509 certificate**: chứng chỉ chứa public key và dữ liệu chủ thể, được issuer ký; bên kiểm tra phải xác thực chuỗi tin cậy, thời hạn, tên/định danh, key usage/extended key usage, thuật toán và chính sách thu hồi khi áp dụng.
- **Fingerprint hoặc public key pinning**: đối chiếu trước qua kênh đáng tin, chú ý quy trình cập nhật khóa.
- **Registry do ứng dụng quản lý đáng tin**: tra `key_id` cùng chủ sở hữu, trạng thái và purpose; không chọn khóa dựa trên ID do đầu vào chưa xác thực tự khai.

**Xác minh chữ ký thành công với khóa do attacker cung cấp** không chứng minh attacker là Alice. **Mã hóa với public key của attacker bị tráo** không bảo đảm chỉ Bob đọc được dữ liệu.

### 7.2 Encoding/format của khóa trong Java

Khi public/private key có thể xuất ra dạng byte, Java thường dùng:

- **X.509 SubjectPublicKeyInfo (SPKI)** cho public key, thường là `PublicKey.getFormat() == "X.509"`.
- **PKCS#8 PrivateKeyInfo** cho private key, thường là `PrivateKey.getFormat() == "PKCS#8"` nếu private key xuất được.

Ví dụ minh họa **bên trong phương thức** sau khi đã có `pair`:

```java
byte[] publicEncoded = pair.getPublic().getEncoded();
java.security.KeyFactory factory =
        java.security.KeyFactory.getInstance("RSA");
java.security.PublicKey publicAgain = factory.generatePublic(
        new java.security.spec.X509EncodedKeySpec(publicEncoded));
```

Không bao giờ coi việc một byte array có định dạng đúng là đã được **chứng thực danh tính**. Chuỗi PEM chỉ là cách đóng gói Base64/text; public key thường chia sẻ được, **private key không được đưa vào log, source code, repository hoặc API công khai**. Khóa riêng thuộc HSM/KMS có thể **không xuất được** (`getEncoded()` trả `null`), dù object key vẫn dùng được với provider thích hợp.

### 7.3 Quản lý vòng đời khóa

Thiết kế cần gồm: tạo khóa bằng RNG phù hợp, giới hạn quyền sử dụng private key, cách sao lưu/khôi phục an toàn, phân loại khóa ký và khóa giải mã, xoay khóa, lưu public key/chứng chỉ lịch sử để kiểm tra chữ ký cũ, thu hồi khóa khi có sự cố và ghi `key_id`/algorithm ID trong định dạng dữ liệu. Không hủy private key giải mã các ciphertext cũ khi vẫn cần đọc chúng; ngược lại, việc giữ private key cũ quá lâu cũng làm tăng rủi ro.

Khi keypair dùng để ký bị lộ, kẻ tấn công có thể tạo chữ ký mới; không thể chỉ chạy `verify` rồi suy ra chữ ký nào được phát hành trước khi khóa bị lộ nếu thiếu timestamp, audit log, revocation và chính sách tin cậy.

## 8. Kết nối với key agreement và hybrid encryption

Một cặp `PrivateKey`/`PublicKey` còn có thể dùng cho **key agreement**, không thuộc trực tiếp hai thao tác trên. Ví dụ X25519:

```text
Alice.private + Bob.public    ---> shared secret
Bob.private   + Alice.public  ---> cùng shared secret
```

Kết quả thỏa thuận được đưa qua **KDF phù hợp** để sinh key cho AEAD. Không xem shared secret thô là AES key ngay khi chưa được đặc tả. Một phép thỏa thuận khóa không xác thực public key của peer có thể chịu **man-in-the-middle**; cần chữ ký/chứng chỉ hoặc giao thức xác thực thích hợp. Trong Java, engine liên quan là `KeyAgreement`, không phải `Signature.verify` hoặc `Cipher.decrypt`.

Ba pattern thường gặp:

| Bài toán | Primitive chủ đạo | Đầu ra cần dùng |
| --- | --- | --- |
| Chỉ cần gửi một bí mật cho public key người nhận | Public-key encryption hoặc hybrid encryption có xác thực | Ciphertext người nhận giải mã được |
| Cần ai cũng kiểm tra nguồn gốc tài liệu | Digital signature | Message + signature + public key/chứng chỉ đáng tin |
| Hai bên lập kênh truyền bí mật | Authenticated key agreement + KDF + AEAD | Khóa phiên, ciphertext/tag và trạng thái giao thức |

**Không tự chọn một phép toán “đối xứng” tưởng tượng giữa public/private key cho mọi mục tiêu.** Các thuật toán khác nhau có chức năng, định dạng, bảo đảm an toàn và engine Java riêng.

## 9. Sai lầm thường gặp và cách sửa

| Nhận định/sự cố | Vấn đề | Cách sửa |
| --- | --- | --- |
| “Khóa công khai của Alice có thể dùng để giải mã mọi thứ Alice ký.” | Chữ ký số không phải ciphertext của message. | Dùng `Signature.verify(Alice.public, message, signature)`. |
| “Mã hóa bằng Bob.public tức là Alice đã chứng minh mình gửi.” | Bất kỳ ai biết Bob.public cũng mã hóa được cho Bob. | Thêm xác thực nguồn bằng scheme/giao thức phù hợp. |
| “Đã `verify` thành công thì message bí mật.” | Signature không bảo vệ confidentiality. | Mã hóa riêng nếu có dữ liệu cần giấu. |
| “Public key nào ghi tên Alice cũng dùng được.” | Khóa không tự ràng buộc danh tính. | Xác thực chứng chỉ/pinning/trust registry và key usage. |
| “Dùng `Cipher` với private key là đủ cho chữ ký RSA.” | Đã bỏ qua security scheme/padding và API chữ ký. | Dùng `Signature` với RSA-PSS hoặc scheme được phê duyệt. |
| “`Cipher.getInstance("RSA")` là RSA an toàn.” | Provider có thể áp padding mặc định không như mong muốn. | Yêu cầu RSA-OAEP và tham số hash/MGF1 rõ ràng. |
| “RSA mã hóa được dữ liệu dài bất kỳ.” | RSA-OAEP có giới hạn message phụ thuộc modulus/hash. | Dùng hybrid encryption để bảo vệ khóa AES, mã hóa payload bằng AEAD. |
| “Cùng cặp khóa nên dùng cho cả ký và giải mã cho tiện.” | Trộn mục đích/chu kỳ rủi ro và quyền truy cập. | Tách key pair khi triển khai production. |
| `InvalidKeyException` khi gọi `Cipher`/`Signature`. | Dùng sai loại public/private key, size hoặc key usage/provider. | Kiểm tra vai trò key, algorithm, chính sách key và tham số `init`. |
| `Signature.verify()` trả `false` | Message, public key, signature hoặc PSS params không khớp. | Kiểm tra byte encoding, bộ tham số và khóa nguồn đáng tin. |
| `BadPaddingException` khi giải mã OAEP | Ciphertext/key/OAEP params không hợp lệ. | Từ chối bản ghi; không thử lại bằng raw RSA hay padding yếu. |
| “Chữ ký đúng nên không thể gửi lại thông điệp đó.” | Chữ ký không chống replay. | Thêm nonce/sequence/timestamp/expiry gắn trong dữ liệu đã ký và trạng thái kiểm tra. |

## 10. Câu hỏi tự kiểm tra

**1. Alice muốn Bob là người duy nhất đọc được thông điệp. Alice dùng khóa nào?**  
Public key **đã xác thực của Bob** để mã hóa theo scheme phù hợp; Bob dùng private key của Bob để giải mã.

**2. Bob muốn xác minh thông điệp do Alice ký. Bob dùng khóa nào?**  
Public key **đã xác thực của Alice** cùng message và signature. Alice dùng private key của Alice để ký.

**3. Một signature hợp lệ có đảm bảo message không bị ai đọc được không?**  
Không. Chữ ký kiểm tra tính toàn vẹn và nguồn gốc theo key; cần mã hóa để có confidentiality.

**4. Có public key nhưng không biết chủ sở hữu thực thì có thể khẳng định người gửi là ai không?**  
Không. Phải xác thực **liên kết key → identity** qua cơ chế đáng tin.

**5. Vì sao RSA-OAEP không nên mã hóa trực tiếp file dài?**  
Giới hạn dữ liệu thấp (ví dụ RSA-2048 + OAEP SHA-256 tối đa **190 byte**). Sử dụng hybrid encryption/AEAD cho payload lớn.

**6. Ed25519 và X25519 có dùng thay nhau được không?**  
Không. Ed25519 là scheme chữ ký, X25519 là key agreement; tên gần giống nhưng loại phép toán và mục tiêu khác nhau.

**7. Nếu có cả chữ ký và mã hóa, phải dùng một cặp khóa hay hai?**  
Một giao thức có thể dùng nhiều thuật toán/khóa; thực hành quản lý khóa thường **tách key pair theo chức năng** và xác thực public key của cả bên gửi/bên nhận theo vai trò.

## Tài liệu chuẩn

- [Java SE 17 — `KeyPairGenerator`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyPairGenerator.html): tạo cặp khóa và tham số sinh khóa.
- [Java SE 17 — `Signature`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Signature.html): `initSign`, `initVerify`, `update`, `sign`, `verify`.
- [Java SE 17 — `Cipher`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Cipher.html): mã hóa/giải mã, transformation và cách `init`.
- [Java SE 17 — `OAEPParameterSpec`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/OAEPParameterSpec.html): OAEP hash, MGF1 và PSource; không dựa vào default SHA-1.
- [Java SE 17 — `PSSParameterSpec`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/spec/PSSParameterSpec.html): digest, MGF, salt length và trailer field.
- [Java SE 17 — Standard Algorithm Names](https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html): tên `RSA`, `RSASSA-PSS`, `Ed25519`, `X25519` và phạm vi tên/triển khai.
- [RFC 8017 — PKCS #1: RSA Cryptography Specifications v2.2](https://www.rfc-editor.org/rfc/rfc8017): RSAES-OAEP, RSASSA-PSS và giới hạn độ dài message.
- [NIST — Glossary: Digital Signature](https://csrc.nist.gov/glossary/term/digital_signature): tính toàn vẹn/xác thực, hỗ trợ non-repudiation; không cung cấp confidentiality.
- [NIST — Glossary: Public Key](https://csrc.nist.gov/glossary/term/public_key): public key dùng để xác minh chữ ký, mã hóa khóa hoặc tham gia key agreement tùy scheme.

**Điều cần nhớ:** **Public key dùng cho thao tác mà người khác được phép thực hiện; private key dùng cho thao tác chỉ chủ sở hữu được quyền thực hiện.** Trong mã hóa là *encrypt với public key người nhận → decrypt với private key người nhận*; trong chữ ký là *sign với private key người ký → verify với public key người ký*. Hai quan hệ ấy phục vụ **mục tiêu bảo mật khác nhau**, nên phải đi kèm xác thực danh tính, scheme, tham số và quản lý khóa đúng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="public-key-cryptography-002">8.2 Tạo cặp khóa bằng KeyPairGenerator với kích thước hoặc bộ tham số rõ ràng</a>

<details>
<summary>Xem chi tiết</summary>

# 8.2 Tạo cặp khóa bằng KeyPairGenerator với kích thước hoặc bộ tham số rõ ràng

Khi tạo khóa bất đối xứng trong Java, điều quan trọng không chỉ là gọi `KeyPairGenerator.generateKeyPair()` mà còn **chủ động xác định thuật toán, kích thước hoặc bộ tham số và nguồn ngẫu nhiên thích hợp**. Những lựa chọn đó chi phối sức mạnh mật mã, khả năng tương thích, tốc độ, định dạng khóa và mục đích sử dụng.

Bài này tập trung vào engine `java.security.KeyPairGenerator` trong Java Cryptography Architecture (JCA): cách khởi tạo **RSA**, **EC/secp256r1**, **Ed25519** và **X25519** với tham số rõ ràng, kiểm tra khóa sau khi sinh, phân biệt khóa ký và khóa trao đổi bí mật, và xử lý những sai sót phổ biến.

## 1. `KeyPairGenerator` là gì?

**Cặp khóa** gồm:

- **Public key (`PublicKey`):** có thể phát cho bên cần xác minh chữ ký, mã hóa theo giao thức phù hợp hoặc tham gia trao đổi khóa.
- **Private key (`PrivateKey`):** phải kiểm soát truy cập nghiêm ngặt; dùng ký, giải mã hoặc thực hiện thỏa thuận khóa tùy thuật toán.
- **`KeyPair`:** đối tượng chứa hai thành phần qua `getPublic()` và `getPrivate()`.

Đối với mật mã khóa công khai, Java phân biệt các engine:

| Engine/API | Trách nhiệm | Ví dụ |
| --- | --- | --- |
| `KeyPairGenerator` | Sinh **cặp khóa mới** theo thuật toán/tham số. | `KeyPairGenerator.getInstance("RSA")`. |
| `KeyFactory` | Chuyển đổi một biểu diễn khóa đã có thành đối tượng khóa hoặc ngược lại. | Đọc `X509EncodedKeySpec` và `PKCS8EncodedKeySpec`. |
| `Signature` | Tạo/xác minh chữ ký với khóa thích hợp. | `SHA256withECDSA`, `Ed25519`, `RSASSA-PSS`. |
| `KeyAgreement` | Thỏa thuận bí mật chung bằng private/public key và giao thức tương ứng. | `X25519`. |
| `Cipher` | Mã hóa/giải mã khi thuật toán/phương thức cho phép. | RSA-OAEP trong các trường hợp phù hợp. |
| `KeyGenerator` | Sinh **khóa bí mật đối xứng**, không tạo cặp public/private. | `KeyGenerator.getInstance("AES")`. |

`KeyPairGenerator` **không tự tạo chứng chỉ X.509, không tự đưa khóa vào HSM/KeyStore, không xác minh chủ sở hữu public key và không quyết định cách phân phối khóa**. Đây là những phần riêng của thiết kế bảo mật.

Vòng đời tổng quát:

~~~text
Chọn mục tiêu (ký / trao đổi khóa / mã hóa đúng giao thức)
          |
          v
Chọn thuật toán + security strength + tham số rõ ràng
          |
          v
KeyPairGenerator.getInstance(algorithm)
          |
          v
initialize(keysize, SecureRandom)
     hoặc
initialize(AlgorithmParameterSpec, SecureRandom)
          |
          v
generateKeyPair() -> KeyPair
          |
          +--> PublicKey  -> công bố bằng kênh có kiểm chứng danh tính
          |
          +--> PrivateKey -> quản lý, lưu và kiểm soát truy cập an toàn
~~~

## 2. API thiết yếu: ba cách khởi tạo

~~~java
KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");

// Cách A — chỉ rõ kích thước:
generator.initialize(3072, new SecureRandom());

// Cách B — chỉ rõ bộ tham số chuyên biệt cho thuật toán:
generator.initialize(
    new RSAKeyGenParameterSpec(3072, RSAKeyGenParameterSpec.F4),
    new SecureRandom());

// Sau khi đã chọn một cách khởi tạo:
KeyPair pair = generator.generateKeyPair();
~~~

Đây là **đoạn minh họa API**; mã chạy đầy đủ được trình bày ở các phần sau. Trong chương trình thật, chọn **một chính sách khởi tạo phù hợp**, không cần gọi cả cách A và B trên cùng một generator.

Các overload quan trọng:

| Phương thức | Ý nghĩa |
| --- | --- |
| `KeyPairGenerator.getInstance("RSA")` | Tìm dịch vụ `KeyPairGenerator` cho RSA từ các provider JCA phù hợp. |
| `getInstance("RSA", "ProviderName")` | Chỉ định provider theo tên, yêu cầu provider đã đăng ký. |
| `getInstance("RSA", providerObject)` | Chỉ định một đối tượng provider; có thể hoạt động cả khi đối tượng chưa đăng ký. |
| `initialize(int keysize)` | Đặt kích thước **theo ý nghĩa riêng của thuật toán**; nguồn ngẫu nhiên được JCA chọn theo chính sách. |
| `initialize(int keysize, SecureRandom rng)` | Đặt kích thước và nguồn ngẫu nhiên cung cấp. Các tham số khác, nếu cần, có thể do provider quyết định. |
| `initialize(AlgorithmParameterSpec params)` | Khởi tạo bằng **bộ tham số** xác định; JCA cung cấp nguồn ngẫu nhiên mặc định. |
| `initialize(AlgorithmParameterSpec params, SecureRandom rng)` | Chỉ rõ cả tham số và nguồn ngẫu nhiên. |
| `generateKeyPair()` | Tạo cặp mới; gọi lại có thể tạo một cặp mới theo trạng thái/cấu hình hiện có. |
| `getProvider()` | Cho biết provider của generator, hữu ích khi kiểm tra môi trường triển khai. |

Hai lưu ý cốt lõi:

1. **Không gọi `initialize`:** `generateKeyPair()` vẫn có thể hoạt động vì provider phải cung cấp cấu hình mặc định. Tuy nhiên default **có thể khác giữa provider và bản JDK**, nên không nên dựa vào nó khi muốn chứng minh kích thước/bộ tham số của khóa.
2. **Chỉ gọi `initialize(256)`:** số `256` là một metric phụ thuộc thuật toán, **không có nghĩa mọi engine đều chọn cùng đường cong, độ mạnh, công dụng**. Với EC, muốn dùng chính xác `secp256r1` thì truyền tên curve.

`SecureRandom` là API sinh byte khó dự đoán phục vụ mật mã. `new SecureRandom()` là lựa chọn thông dụng trong các ví dụ; không đặt seed từ thời gian/`Random`/PID để “tái hiện” khóa. Khóa production cần nguồn ngẫu nhiên và chính sách vận hành được đánh giá trên nền tảng đang chạy.

## 3. Chọn thuật toán và bộ tham số dựa trên mục đích

| Mục đích | `getInstance(...)` cho KPG | Bộ tham số nên khai báo | Ví dụ engine dùng khóa |
| --- | --- | --- | --- |
| Khóa RSA phục vụ chữ ký/ứng dụng phù hợp | `"RSA"` | `RSAKeyGenParameterSpec(3072, F4)`, hoặc kích thước rõ ràng nếu không cần tùy chỉnh exponent | `Signature`: `RSASSA-PSS` hoặc phương thức được giao thức quy định |
| Chữ ký ECDSA trên đường cong NIST cụ thể | `"EC"` | `ECGenParameterSpec("secp256r1")` | `Signature.getInstance("SHA256withECDSA")` |
| Chữ ký EdDSA (Ed25519) | `"Ed25519"` | `NamedParameterSpec.ED25519` khi muốn nêu rõ tham số | `Signature.getInstance("Ed25519")` |
| Thỏa thuận khóa hiện đại X25519 | `"X25519"` | `NamedParameterSpec.X25519` | `KeyAgreement.getInstance("X25519")` |

**Không so sánh số bit giữa các thuật toán một cách trực tiếp**: RSA 3072 bit, ECDSA P-256 và Ed25519 không có cùng định dạng hay ý nghĩa “độ dài khóa”. Sức mạnh bảo mật ước lượng của chúng phải dựa trên tiêu chuẩn an ninh và tính phù hợp với giao thức, không đơn thuần so 3072 với 256.

Trong Java, việc một thuật toán/curve được hỗ trợ còn phụ thuộc **JDK, provider, chính sách và môi trường phần cứng**. Nếu ứng dụng yêu cầu tính di động, hãy kiểm thử trên môi trường triển khai mục tiêu và ghi nhận provider thực sự được dùng. Tên thuật toán trong ví dụ là các tên API chuẩn/được JDK 17 thông dụng hỗ trợ; không kết luận mọi provider hỗ trợ hết.

### Phân biệt loại khóa qua tính năng

- **RSA key** có thể phù hợp với các giao thức ký, mã hóa hoặc một số giao thức cũ, nhưng phải dùng **transformation/scheme chuẩn** và chính sách hợp lý; không “mã hóa bằng private key” để thay cho chữ ký.
- **EC key** tạo bởi `KeyPairGenerator.getInstance("EC")` với `secp256r1` dùng được cho ECDSA; không mặc định rằng mọi khóa EC của mọi curve đều dùng chung với X25519.
- **Ed25519** dùng **chữ ký số**, không phải ECDH/X25519.
- **X25519** phục vụ **thỏa thuận khóa**, không phải chữ ký. Bí mật chung thô từ `KeyAgreement` thường còn phải qua **KDF và giao thức xác thực** trước khi thành khóa ứng dụng; X25519 một mình không xác thực danh tính đối tác.

## 4. Sinh RSA với kích thước và public exponent rõ ràng

### 4.1. Hai mức chỉ định

Cách đơn giản:

~~~java
KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
generator.initialize(3072, new SecureRandom());
KeyPair pair = generator.generateKeyPair();
~~~

Cách chặt chẽ hơn khi muốn nêu **public exponent**:

~~~java
KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
generator.initialize(
    new RSAKeyGenParameterSpec(3072, RSAKeyGenParameterSpec.F4),
    new SecureRandom());
KeyPair pair = generator.generateKeyPair();
~~~

Các đoạn trên là minh họa, không phải tệp Java độc lập.

- `3072` là **modulus size** RSA đo bằng **bit**. RSA-2048 vẫn gặp trong các hệ thống hiện hành, nhưng RSA-3072 cho dư địa bảo mật cao hơn, đổi lại chi phí CPU và kích thước khóa/chữ ký lớn hơn. Số bit được chọn phải theo tuổi thọ bảo mật, yêu cầu tiêu chuẩn và tính tương thích.
- `RSAKeyGenParameterSpec.F4` tương ứng **65537**, public exponent thông dụng. Không tự chọn public exponent kỳ lạ hoặc rất nhỏ vì nghĩ “chạy nhanh hơn”.
- `RSAKeyGenParameterSpec` không phải đối tượng private key; nó chỉ khai báo tham số **đầu vào sinh khóa**.
- **Kích thước modulus không phải kích thước private key mã hóa PKCS#8** và không phải độ dài chữ ký của mọi sơ đồ (RSA signature thường có độ dài bằng modulus khi định dạng chuẩn).
- `generateKeyPair()` dùng ngẫu nhiên để tạo các thành phần bí mật; **không được tái sử dụng private key một cách vô thức cho những nhiệm vụ/giao thức không tương thích**.

### 4.2. Chương trình Java 17+: RSA-3072 và kiểm chứng cặp khóa

Tệp **`RsaParamsDemo.java`**:

~~~java
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAKeyGenParameterSpec;

public class RsaParamsDemo {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        RSAKeyGenParameterSpec parameters =
                new RSAKeyGenParameterSpec(3072, RSAKeyGenParameterSpec.F4);
        generator.initialize(parameters, new SecureRandom());
        KeyPair pair = generator.generateKeyPair();

        RSAPublicKey publicKey = (RSAPublicKey) pair.getPublic();
        System.out.println("Algorithm: " + publicKey.getAlgorithm());
        System.out.println("Modulus bits: "
                + publicKey.getModulus().bitLength());
        System.out.println("Public exponent: "
                + publicKey.getPublicExponent());

        byte[] message = "module".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(pair.getPrivate());
        signer.update(message);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(pair.getPublic());
        verifier.update(message);
        System.out.println("Signature verifies: "
                + verifier.verify(signature));
    }
}
~~~

Chạy ở thư mục chứa tệp:

~~~bash
javac RsaParamsDemo.java
java RsaParamsDemo
~~~

Đầu ra được kiểm tra:

~~~text
Algorithm: RSA
Modulus bits: 3072
Public exponent: 65537
Signature verifies: true
~~~

Kết quả chứng minh cặp khóa đã được sinh với modulus 3072 bit, public exponent F4, và hai thành phần private/public tương thích để ký/xác minh trong ví dụ. Nó **không chứng minh private key đã được lưu an toàn**, không tạo chứng chỉ và không phải bài kiểm thử toàn diện của một hệ thống PKI.

`SHA256withRSA` ở đây phục vụ kiểm chứng ví dụ nhanh và tương thích. Với thiết kế chữ ký RSA mới, cân nhắc **RSASSA-PSS với `PSSParameterSpec` rõ ràng** theo giao thức được chọn; không tự ý đổi scheme khi trao đổi với hệ thống đã quy định thuật toán khác.

Nếu giao thức cần **RSA-OAEP** để bọc khóa nhỏ, đây là trách nhiệm của `Cipher` với transformation/`OAEPParameterSpec` được chỉ rõ, **không phải nhiệm vụ của `KeyPairGenerator`**. Với dữ liệu lớn, thiết kế hybrid encryption dùng khóa đối xứng/AEAD phù hợp thay vì dùng RSA mã hóa toàn bộ plaintext.

## 5. Sinh khóa EC: chọn đường cong bằng tên, không chỉ bằng số 256

### 5.1. Vì sao `initialize(256)` chưa nói hết cấu hình?

Với `KeyPairGenerator.getInstance("EC")`, lời gọi `initialize(256)` cho biết metric kích thước, nhưng provider quyết định cách ánh xạ sang bộ tham số. Đối với giao thức bắt buộc sử dụng **đường cong NIST P-256**, hãy chỉ định trực tiếp:

~~~java
KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
generator.initialize(
    new ECGenParameterSpec("secp256r1"), new SecureRandom());
KeyPair pair = generator.generateKeyPair();
~~~

`"secp256r1"` là tên thông dụng cho đường cong **NIST P-256**; đừng giả định alias như `prime256v1` được mọi provider chấp nhận. Với yêu cầu curve khác (ví dụ `secp384r1`), phải kiểm tra hỗ trợ và khớp giao thức. Không lấy một đối tượng EC ở đường cong A rồi dùng với phía yêu cầu đường cong B.

`ECGenParameterSpec` là bộ tham số dựa vào tên curve, không phải nơi chứa điểm public key hay scalar private key. Khi sinh xong, `ECPublicKey` cho phép đọc các tham số đường cong để kiểm tra. **Field size 256 bit** chỉ là một dấu hiệu, không đủ để chứng minh tên curve đúng nếu không kiểm tra chi tiết tham số/giao thức; chương trình đặt tên curve rõ ngay từ đầu.

### 5.2. Chương trình Java 17+: EC/secp256r1 và ECDSA

Tệp **`EcCurveDemo.java`**:

~~~java
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;

public class EcCurveDemo {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(
                new ECGenParameterSpec("secp256r1"),
                new SecureRandom());
        KeyPair pair = generator.generateKeyPair();

        ECPublicKey publicKey = (ECPublicKey) pair.getPublic();
        System.out.println("Algorithm: " + publicKey.getAlgorithm());
        System.out.println("Field bits: "
                + publicKey.getParams().getCurve()
                        .getField().getFieldSize());

        byte[] message = "module".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(pair.getPrivate());
        signer.update(message);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(pair.getPublic());
        verifier.update(message);
        System.out.println("Signature verifies: "
                + verifier.verify(signature));
    }
}
~~~

~~~bash
javac EcCurveDemo.java
java EcCurveDemo
~~~

Stdout:

~~~text
Algorithm: EC
Field bits: 256
Signature verifies: true
~~~

`Signature` sử dụng private key để ký và public key để verify; `KeyPairGenerator` chỉ sinh hai khóa. Chữ ký ECDSA chứa ngẫu nhiên hoặc nonce nội bộ tùy triển khai, nên **không in hoặc giả định byte chữ ký cố định**. Dùng API chữ ký chuẩn, không tự lập trình nonce chữ ký.

Lưu ý cấu hình curve của provider có thể thay đổi khả năng tương thích với HSM/FIPS. Nếu môi trường không hỗ trợ `secp256r1`, đừng tự động chọn curve bất kỳ để “chạy được”; cần quyết định dựa trên yêu cầu bảo mật và đối tác.

## 6. Ed25519 và X25519: `NamedParameterSpec` thay cho một số bit mơ hồ

Với những thuật toán đặt tên cụ thể, Java 17 cung cấp `java.security.spec.NamedParameterSpec`, gồm các hằng:

- `NamedParameterSpec.ED25519` — bộ tham số **Ed25519**, dùng cho chữ ký.
- `NamedParameterSpec.X25519` — bộ tham số **X25519**, dùng cho trao đổi khóa.
- Các hằng khác như `ED448` và `X448` được định nghĩa nhưng chỉ dùng khi provider/giao thức hỗ trợ.

Ví dụ:

~~~java
KeyPairGenerator ed = KeyPairGenerator.getInstance("Ed25519");
ed.initialize(NamedParameterSpec.ED25519, new SecureRandom());
KeyPair signingKeys = ed.generateKeyPair();

KeyPairGenerator x = KeyPairGenerator.getInstance("X25519");
x.initialize(NamedParameterSpec.X25519, new SecureRandom());
KeyPair agreementKeys = x.generateKeyPair();
~~~

Đoạn trên là minh họa API, không phải toàn bộ chương trình. Khi thuật toán đã có tên cụ thể như `"Ed25519"` hay `"X25519"`, provider có thể có default phù hợp nếu không gọi `initialize`; ở đây ta **nêu tham số tường minh** để thể hiện điều kiện mong muốn và kiểm thử provider có hỗ trợ chúng.

**Tránh `initialize(255)`** với ý nghĩ 255 là kích thước tùy ý tương đương RSA `initialize(3072)`. Ed25519 và X25519 xác định cấu trúc theo bộ tham số tên; cách yêu cầu bằng số bit không phải hợp đồng di động giữa các provider.

### Chương trình Java 17+: ký Ed25519 và thỏa thuận X25519

Tệp **`NamedKeysDemo.java`**:

~~~java
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.NamedParameterSpec;
import java.util.Arrays;
import javax.crypto.KeyAgreement;

public class NamedKeysDemo {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator ed =
                KeyPairGenerator.getInstance("Ed25519");
        ed.initialize(NamedParameterSpec.ED25519, new SecureRandom());
        KeyPair signingPair = ed.generateKeyPair();

        byte[] data = "module".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(signingPair.getPrivate());
        signer.update(data);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(signingPair.getPublic());
        verifier.update(data);
        System.out.println("Ed25519 signature verifies: "
                + verifier.verify(signature));

        KeyPairGenerator x = KeyPairGenerator.getInstance("X25519");
        x.initialize(NamedParameterSpec.X25519, new SecureRandom());
        KeyPair a = x.generateKeyPair();
        KeyPair b = x.generateKeyPair();

        KeyAgreement sideA = KeyAgreement.getInstance("X25519");
        sideA.init(a.getPrivate());
        sideA.doPhase(b.getPublic(), true);
        byte[] secretA = sideA.generateSecret();

        KeyAgreement sideB = KeyAgreement.getInstance("X25519");
        sideB.init(b.getPrivate());
        sideB.doPhase(a.getPublic(), true);
        byte[] secretB = sideB.generateSecret();

        System.out.println("X25519 secrets equal: "
                + Arrays.equals(secretA, secretB));
        System.out.println("X25519 raw secret bytes: "
                + secretA.length);

        Arrays.fill(secretA, (byte) 0);
        Arrays.fill(secretB, (byte) 0);
    }
}
~~~

~~~bash
javac NamedKeysDemo.java
java NamedKeysDemo
~~~

Stdout:

~~~text
Ed25519 signature verifies: true
X25519 secrets equal: true
X25519 raw secret bytes: 32
~~~

Cả hai phía X25519 có cùng **shared secret thô** nhờ sử dụng private key của mình và public key của bên kia. **Không dùng `secretA` trực tiếp thành khóa AES hoặc session key của một giao thức tự chế**; hệ thống thực cần KDF, liên kết transcript/context, xác thực peer và kiểm tra khả năng chống MITM theo giao thức đã được thẩm định. Demo này không xác thực danh tính bên A hay B và không chứa toàn bộ handshake như TLS.

## 7. Xác minh rằng tham số đã được áp dụng

Một số chỉ số nên kiểm tra sau `generateKeyPair()`:

| Loại khóa | Kiểm tra API | Điều muốn chứng minh |
| --- | --- | --- |
| RSA | `RSAPublicKey.getModulus().bitLength()` | Độ dài modulus thực tế khớp chính sách (ví dụ 3072 bit). |
| RSA | `RSAPublicKey.getPublicExponent()` | Exponent thực tế phù hợp `RSAKeyGenParameterSpec.F4` (65537). |
| EC | `ECPublicKey.getParams()` | Bộ tham số đường cong thực tế phù hợp curve/giao thức mong muốn; field size chỉ là một kiểm tra sơ bộ. |
| Ed25519 | Thuật toán key + phép ký/xác minh `Signature("Ed25519")` | Khóa phục vụ chữ ký đúng cơ chế. |
| X25519 | Phép thỏa thuận hai phía qua `KeyAgreement("X25519")` | Hai key pair có thể tạo cùng secret theo đúng giao thức. |
| Mọi loại | `generator.getProvider().getName()` | Provider được JCA chọn, phục vụ kiểm tra môi trường và policy. |

**Không coi phép thử ký/xác minh một thông điệp là toàn bộ kiểm toán bảo mật khóa.** Nó chỉ kiểm tra tương thích chức năng. Hệ thống còn cần tính đúng đắn của nguồn entropy, xử lý private key, quản lý danh tính public key và vòng đời rotation.

### Phân biệt kích thước bit và mức an toàn

- RSA `3072` là **bit length của modulus**, không tương đương “3072 bit security”.
- EC `secp256r1` có field gần 256 bit; sức mạnh bảo mật ước tính thấp hơn 256 bit vì tính chất bài toán logarit rời rạc.
- Ed25519 có bộ tham số riêng, không biến thành “RSA-255” hay “ECDSA với curve mặc định”.
- X25519 thuộc họ trao đổi khóa; so trực tiếp độ dài shared secret với số bit modulus RSA không giúp chọn thuật toán đúng.

Để bảo vệ một hệ thống có thời hạn dài, cần tham chiếu chính sách tổ chức, tiêu chuẩn được chấp nhận và mức tương thích của các bên. **Không mặc định RSA mạnh hơn EC chỉ vì 3072 lớn hơn 256.**

## 8. Khi nào nên truyền `SecureRandom`?

Hai dạng phổ biến:

~~~java
// Dùng nguồn ngẫu nhiên do JCA lựa chọn:
generator.initialize(3072);

// Truyền nguồn CSPRNG rõ ràng:
generator.initialize(3072, new SecureRandom());
~~~

Nếu cần kiểm toán lựa chọn nguồn entropy, truyền đối tượng `SecureRandom` có thể thể hiện policy rõ hơn. Tuy nhiên **truyền `new SecureRandom()` không tự bảo đảm toàn bộ nền tảng đạt FIPS/hardware-backed**; nếu có yêu cầu cụ thể, phải cấu hình và xác minh provider/cơ chế được phép.

Không làm các việc sau:

- Dùng `java.util.Random` hoặc `Math.random()` để tự tạo private exponent/seed và lắp khóa bằng tay.
- Gọi `random.setSeed(System.currentTimeMillis())` với suy nghĩ tăng độ an toàn; giá trị dự đoán được không cải thiện chất lượng CSPRNG và việc cố làm key generation tái hiện là nguy hiểm.
- Tự nhúng private key vào mã nguồn, config Git hoặc bản ghi log để tiết kiệm bước triển khai.
- Tái sử dụng cùng cặp khóa mà không có chính sách: phân biệt signing key, agreement key, key wrapping, tuổi thọ và key rotation.

`SecureRandom.getInstanceStrong()` có thể phù hợp khi chính sách yêu cầu, nhưng có thể khác theo provider và hệ điều hành, thậm chí có đặc tính chờ entropy không phù hợp đường xử lý request. Không coi nó là lựa chọn bắt buộc cho mọi ứng dụng nếu chưa đánh giá.

## 9. Private key, public key, encoding và KeyStore

### 9.1. Public key không đồng nghĩa danh tính đã được xác minh

Public key thường có thể phân phối công khai. Nhưng nếu ứng dụng lấy public key từ một endpoint không xác thực, đối phương có thể **thay key** và giả mạo danh tính. Vì vậy cần cơ chế chứng chỉ, key pinning, signed metadata, trust store hoặc giao thức xác thực phù hợp. `KeyPairGenerator` chỉ tạo **cặp giá trị mật mã**, không chứng minh ai sở hữu chúng.

### 9.2. Các định dạng thường gặp

- Public key dạng `SubjectPublicKeyInfo` / `X.509`: thường `publicKey.getFormat()` trả `"X.509"` và `getEncoded()` trả bytes theo encoding phù hợp.
- Private key dạng `PKCS#8`: với software key exportable, `privateKey.getFormat()` thường là `"PKCS#8"` và `getEncoded()` có thể cung cấp encoding dạng PKCS#8.
- **Không phải mọi private key đều exportable**. Nếu key nằm trong HSM/token bảo mật, `getEncoded()` có thể trả `null`; cần dùng API/provider/keystore thích hợp.
- Base64/PEM **chỉ mã hóa thành văn bản**, không tự bảo vệ private key; xuất private key dạng base64 hoặc raw PKCS#8 vào log/disk vẫn gây lộ.
- `KeyFactory` dùng để đọc/khôi phục biểu diễn khóa khi chính sách cho phép, với `X509EncodedKeySpec` cho public và `PKCS8EncodedKeySpec` cho private. Đây là thao tác **import/export**, không phải sinh cặp khóa mới.
- `KeyStore` hoặc HSM/KMS giúp quản lý/lưu khóa tùy provider và hệ thống, nhưng khả năng export, certificate chain, password và chính sách sử dụng có yêu cầu riêng.

Trong môi trường cần chứng chỉ TLS/chữ ký doanh nghiệp, quy trình có thể là **sinh private key trong HSM**, tạo certificate signing request (CSR) hoặc định danh khóa rồi nhận chứng chỉ từ CA. Chứng chỉ được cấp là một **đối tượng khác**, không tự xuất hiện sau `generateKeyPair()`.

### 9.3. Vòng đời khóa cần chính sách

1. **Tạo** theo thuật toán và tham số được duyệt; ghi nhận metadata `keyId`, mục đích, thời điểm/phiên bản.
2. **Lưu** private key trong vùng kiểm soát truy cập, hạn chế export, mã hóa at rest và backup theo chính sách.
3. **Phân phối** public key kèm xác minh danh tính/nguồn tin cậy.
4. **Sử dụng** đúng mục đích và algorithm suite, kiểm tra provider/khả năng thao tác.
5. **Xoay vòng** (rotation) và xử lý khóa cũ có bản mã/chữ ký còn cần xác minh; không xóa khóa cũ trước khi kiểm kê phụ thuộc.
6. **Thu hồi/hủy** đúng quy trình, cập nhật chứng chỉ/khóa tin cậy và trạng thái truy cập; không chỉ loại bỏ reference trong code.

**Không log nội dung private key**, kể cả trong tình huống “debug tạm” hoặc gửi vào hệ thống theo dõi lỗi. Với demo Java, tốt nhất chỉ in thuật toán và các thuộc tính **public** có thể kiểm chứng.

## 10. Provider, khả năng tương thích và lỗi thường gặp

### 10.1. Provider quyết định những gì?

`getInstance("EC")` yêu cầu JCA lấy một engine theo tên. `initialize(new ECGenParameterSpec("secp256r1"))` yêu cầu engine dùng curve cụ thể. Một provider có thể hỗ trợ thuật toán `"EC"` nhưng không hỗ trợ curve cần thiết.

Trong ứng dụng phổ thông, để JCA tự chọn provider thường tăng tính di động. Nếu hệ thống phải sinh khóa **trong HSM/PKCS#11 hoặc provider tuân thủ chính sách cụ thể**, hãy chỉ định/configure provider theo thiết kế và kiểm tra private key được tạo tại đúng nơi. Chỉ định provider theo tên chưa tự đảm bảo private key không thể export; điều đó phụ thuộc triển khai và cấu hình thực tế.

`KeyPairGenerator.getInstance("RSA", "SomeProvider")` dùng **đúng provider đã đăng ký** theo tên; nếu tên không tồn tại sẽ có `NoSuchProviderException`. Không âm thầm fallback sang software provider nếu policy yêu cầu HSM.

### 10.2. Bảng lỗi và cách xử lý

| Vấn đề | Biểu hiện thường gặp | Cách kiểm tra/khắc phục |
| --- | --- | --- |
| Thuật toán keygen không có | `NoSuchAlgorithmException` từ `getInstance` | Kiểm tra tên thuật toán, bản JDK và provider được cài. |
| Chỉ định provider theo tên không có | `NoSuchProviderException` | Kiểm tra đăng ký provider, chính sách Java security và tên chính xác. |
| `initialize(int)` có keysize sai/không hỗ trợ | `InvalidParameterException` | Kiểm tra kích thước được provider/thuật toán hỗ trợ. |
| `initialize(AlgorithmParameterSpec)` không phù hợp | `InvalidAlgorithmParameterException` | Dùng đúng loại spec, curve name và cấu hình nhà cung cấp. |
| Đã tạo `GCMParameterSpec` để khởi tạo `KeyPairGenerator` | Không phải loại tham số phù hợp; có thể bị từ chối | GCM là tham số cho mã hóa đối xứng, không phải sinh cặp RSA/EC. |
| `initialize(256)` nhưng đối tác đòi `secp256r1` | Provider có thể chọn curve khác hoặc không hỗ trợ | Dùng `ECGenParameterSpec("secp256r1")`. |
| `initialize(3072)` cho `"Ed25519"` | Metric không tương thích/không hỗ trợ | Dùng `NamedParameterSpec.ED25519` hoặc default được quy định. |
| RSA 1024 bit cho dữ liệu mới | Không đáp ứng chuẩn an ninh hiện đại phổ biến | Chọn RSA 2048/3072+ theo policy; cân nhắc chi phí và thời hạn. |
| Tạo khóa X25519 rồi đưa vào `Signature` | Sai mục đích thuật toán | Dùng Ed25519 cho chữ ký, X25519 cho key agreement. |
| Log private key hay lưu base64 không mã hóa | Có thể lộ private key | Dùng keystore/HSM và bảo vệ theo policy, không log secret. |
| Dùng public key tải từ nguồn chưa tin cậy | Có thể bị MITM hoặc thay thế khóa | Xác minh danh tính và nguồn tin cậy public key/chứng chỉ. |
| `getEncoded()` trả `null` | Key có thể không export được | Không ép cast/đọc raw key; dùng provider/HSM API theo mục đích. |
| `KeyPairGenerator` được dùng đồng thời ở nhiều thread | Engine có state, không nên dựa vào thread safety | Tạo generator riêng theo operation/worker hoặc đồng bộ đúng cách. |
| Tự chọn provider fallback sau lỗi | Có thể rời khỏi HSM/FIPS hoặc sai policy | Quản lý lỗi rõ ràng, không lặng lẽ thay provider. |

Không bắt toàn bộ `Exception` rồi chuyển sang thuật toán/kích thước yếu hơn chỉ để thành công. Các hạn chế provider và security policy là điều kiện vận hành cần được xử lý bằng cấu hình/quy trình, không phải được vượt qua bằng fallback ngầm.

## 11. Tự kiểm tra kiến thức

**Câu 1.** Tạo `KeyPairGenerator.getInstance("EC")` rồi `initialize(256)` đã chứng minh chắc chắn khóa dùng `secp256r1` chưa?

**Đáp án:** **Chưa.** Số 256 là kích thước thuật toán định nghĩa; bộ tham số có thể là mặc định provider. Muốn yêu cầu curve cụ thể, dùng `ECGenParameterSpec("secp256r1")` và kiểm tra khóa sinh ra.

**Câu 2.** Với RSA, `new RSAKeyGenParameterSpec(3072, RSAKeyGenParameterSpec.F4)` xác định những gì?

**Đáp án:** Modulus 3072 bit và public exponent 65537. Bộ tham số không chứa private key, không tự sinh cặp.

**Câu 3.** Khóa có `getAlgorithm()` trả `"EC"` và field size 256 có đủ để chứng minh đúng curve và mục đích không?

**Đáp án:** Chưa đủ. Cần kiểm tra bộ tham số curve thực và giao thức dùng khóa; field size một mình không xác minh danh tính curve hoặc yêu cầu sử dụng.

**Câu 4.** Có thể lấy `KeyPair` Ed25519 và dùng ngay với `KeyAgreement.getInstance("X25519")` không?

**Đáp án:** Không. Ed25519 là chữ ký, X25519 là key agreement; tham số, key format và cách dùng khác nhau.

**Câu 5.** Hai phía X25519 tính ra cùng 32 byte secret. Giao thức đã chống man-in-the-middle chưa?

**Đáp án:** **Chưa.** Chỉ chứng minh phép toán agreement hoạt động; cần xác thực peer, transcript/KDF và giao thức đầy đủ.

**Câu 6.** Nếu không gọi `initialize`, `KeyPairGenerator.generateKeyPair()` sẽ luôn tạo RSA-3072?

**Đáp án:** Không. Mặc định do provider xác định; muốn chính sách kích thước rõ ràng phải gọi `initialize` thích hợp.

**Câu 7.** Vì sao `PublicKey` được công bố nhưng vẫn phải xác minh nguồn?

**Đáp án:** Public key không bí mật, nhưng **danh tính gắn với public key** là điều phải xác minh. Khóa bị tráo có thể dẫn đến giả mạo hoặc MITM.

**Câu 8.** `privateKey.getEncoded()` trả mảng byte thì có thể in Base64 để debug không?

**Đáp án:** Không. Base64 không mã hóa bí mật; đó có thể là PKCS#8 chứa private key. Không log hoặc công bố giá trị này.

**Câu 9.** Nguồn `SecureRandom` đã đủ để kết luận keygen đáp ứng FIPS/HSM chưa?

**Đáp án:** Chưa. Cần kiểm tra provider, cơ chế phần cứng, thông số, chính sách triển khai và bằng chứng vận hành liên quan.

## 12. Tài liệu tham khảo

- [Java SE 17 — KeyPairGenerator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyPairGenerator.html): overload `getInstance`, khởi tạo theo kích thước/bộ tham số, mặc định provider.
- [Java SE 17 — KeyPair](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyPair.html): biểu diễn cặp `PrivateKey` và `PublicKey`.
- [Java SE 17 — RSAKeyGenParameterSpec](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/spec/RSAKeyGenParameterSpec.html): modulus size, public exponent và `F4`.
- [Java SE 17 — ECGenParameterSpec](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/spec/ECGenParameterSpec.html): tham số đường cong EC đặt tên.
- [Java SE 17 — NamedParameterSpec](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/spec/NamedParameterSpec.html): `ED25519`, `X25519` và các tham số đặt tên.
- [Java SE 17 — SecureRandom](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/SecureRandom.html): nguồn ngẫu nhiên mật mã.
- [Java SE 17 — KeyAgreement](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/KeyAgreement.html): trao đổi bí mật bằng khóa công khai.
- [JDK 17 Providers Documentation](https://docs.oracle.com/en/java/javase/17/security/oracle-providers.html): hỗ trợ, hạn chế và default theo provider.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="public-key-cryptography-003">8.3 RSA, EC, EdDSA và XDH: phân biệt mục đích của từng họ thuật toán</a>

<details>
<summary>Xem chi tiết</summary>

# 8.3 RSA, EC, EdDSA và XDH: phân biệt mục đích của từng họ thuật toán

## Mục tiêu

Sau bài này, bạn sẽ phân biệt **mã hóa khóa công khai**, **chữ ký số** và **thỏa thuận khóa**; biết RSA, EC, EdDSA và XDH cung cấp chức năng gì; chọn đúng engine của Java Cryptography Architecture (JCA/JCE) như `Cipher`, `Signature` và `KeyAgreement`; sử dụng tên thuật toán/curve đúng trong Java 17+; và hiểu vì sao không được lấy khóa hoặc kết quả của một họ để thay thế tùy tiện cho họ khác.

Bốn tên **không cùng cấp độ trừu tượng**: `RSA` là một họ thuật toán dùng cho mã hóa hoặc chữ ký tùy scheme; `EC` là tên tổng quát của **kiểu khóa đường cong elliptic cổ điển** dùng trong ECDSA/ECDH; `EdDSA` là một **họ chữ ký số**; còn `XDH` là một **họ thỏa thuận khóa Diffie–Hellman** dùng đường cong dạng Montgomery. Nhầm lẫn này là nguyên nhân của nhiều lỗi `NoSuchAlgorithmException`, `InvalidKeyException` và thiết kế giao thức thiếu bước xác thực.

## 1. Trước tiên: ba nhiệm vụ của mật mã khóa công khai

Hệ mật mã bất đối xứng dùng **public key** (có thể phân phối) và **private key** (phải bảo vệ). Dù đều có cặp khóa, ba thao tác sau hoàn toàn khác nhau:

| Mục đích | Thao tác | Khóa/giá trị đi vào | Đầu ra | Bảo đảm được thiết kế để cung cấp |
| --- | --- | --- | --- | --- |
| **Mã hóa khóa công khai** | Người gửi mã hóa bằng public key của bên nhận; bên nhận giải mã bằng private key | Public/private key tương ứng, plaintext | Ciphertext | Chỉ chủ private key đúng có thể đọc; cần scheme mã hóa an toàn |
| **Chữ ký số** | Người ký dùng private key tạo chữ ký; bất kỳ bên tin cậy public key có thể verify | Private/public key, thông điệp | Signature bytes và kết quả verify | Tính toàn vẹn và sự xác thực của người sở hữu private key |
| **Thỏa thuận khóa** | Hai bên dùng private key của mình và public key của đối phương để tính shared secret | Hai cặp khóa được tạo phù hợp | Hai bên nhận cùng shared secret | Cơ sở để tạo khóa phiên; **chưa tự xác thực danh tính hoặc mã hóa dữ liệu** |

`Signature` không phải là phép “mã hóa dữ liệu bằng private key”; ngược lại, RSA-OAEP không phải chữ ký. `KeyAgreement` không có API “ký” và cũng không biến thông điệp thành ciphertext. Một public key chỉ có giá trị bảo mật khi **được gắn đúng với danh tính của bên còn lại** (certificate, pinning, trust store, chữ ký bắt tay, hoặc kênh xác thực thích hợp).

### Bảng chọn nhanh RSA, EC, EdDSA, XDH

| Họ / tên | Vai trò tiêu biểu | Java engine | Tên Java thông dụng | Không nên hiểu thành |
| --- | --- | --- | --- | --- |
| **RSA** | Mã hóa khóa/dữ liệu nhỏ qua **RSA-OAEP**; chữ ký **RSA-PSS** | `Cipher`, `Signature` | `RSA` cho `KeyPairGenerator`; `RSA/ECB/OAEPPadding`; `RSASSA-PSS` | “RSA” mặc nhiên chỉ là mã hóa, hoặc chữ ký là decrypt |
| **EC** | **ECDSA** (chữ ký) và **ECDH** (thỏa thuận khóa) trên các đường cong EC phù hợp | `Signature`, `KeyAgreement` | `EC`, `SHA256withECDSA`, `ECDH`, `secp256r1` | Một thuật toán tên `EC` làm mọi việc |
| **EdDSA** | Chữ ký số hiện đại | `Signature` | `Ed25519`, `Ed448`; dạng tổng quát `EdDSA` | Khóa mã hóa hay khóa `KeyAgreement` |
| **XDH** | Thỏa thuận khóa DH hiện đại | `KeyAgreement` | `X25519`, `X448`; dạng tổng quát `XDH` | Thuật toán ký số hoặc hàm mã hóa `Cipher` |

Tên trong bảng thuộc các engine khác nhau: `KeyPairGenerator.getInstance("EC")` tạo khóa, `Signature.getInstance("SHA256withECDSA")` dùng khóa EC để ký, còn `KeyAgreement.getInstance("ECDH")` dùng khóa EC để thỏa thuận. Java không có một lệnh `getInstance("EC")` chung dùng cho mọi thao tác.

## 2. RSA: một họ có thể dùng cả mã hóa lẫn chữ ký

### 2.1 RSA-OAEP: mã hóa cho người giữ private key

RSA công khai một modulus `n` và số mũ công khai `e`; private key chứa các thông số bí mật cần thiết. Các phép toán RSA nguyên thủy không đủ an toàn khi sử dụng trực tiếp. Khi cần **mã hóa bằng RSA** trong ứng dụng hiện đại, chọn scheme có padding an toàn như **RSAES-OAEP** và chỉ dùng nó để mã hóa **nội dung nhỏ hoặc khóa phiên**, sau đó mã hóa dữ liệu lớn bằng AEAD như AES-GCM.

Cặp tên Java ví dụ:

```java
Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
OAEPParameterSpec params = new OAEPParameterSpec(
        "SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
        PSource.PSpecified.DEFAULT);
cipher.init(Cipher.ENCRYPT_MODE, recipientPublicKey, params);
byte[] ciphertext = cipher.doFinal(plaintext);
```

Đây là **snippet minh họa nằm trong phương thức**, chưa phải chương trình độc lập. Nó giả định có `recipientPublicKey` và `plaintext`, và phải xử lý ngoại lệ từ `Cipher.getInstance`/`init`/`doFinal`. Trong Java, chuỗi `RSA/ECB/OAEPPadding` có từ `ECB` vì cú pháp transformation lịch sử; đây **không phải lời khuyên dùng ECB mode của AES**. Quan trọng là **OAEP** và các tham số digest/MGF.

**Tránh sự mơ hồ tham số OAEP**: chỉ ghi `OAEPWithSHA-256AndMGF1Padding` trong transformation nhưng không kiểm soát MGF1 digest có thể làm các môi trường không tương thích. Khai báo `OAEPParameterSpec` rõ ràng (trong ví dụ, digest chính SHA-256 và digest MGF1 cũng SHA-256), rồi thống nhất đúng bộ tham số với bên nhận.

RSA-OAEP giới hạn số byte plaintext: với modulus dài `k` byte và digest dài `hLen` byte, giới hạn là **`k - 2*hLen - 2`**. RSA 2048 bit dùng SHA-256 cho `k=256` và `hLen=32`, nên tối đa **190 byte** mỗi thao tác OAEP. **Không** chia tệp thành nhiều khối rồi tự mã hóa từng khối RSA. Thay vào đó, sinh một khóa AES ngẫu nhiên, mã hóa dữ liệu bằng AES-GCM và dùng RSA-OAEP bảo vệ khóa AES trong envelope có format rõ ràng.

### 2.2 RSA-PSS: chữ ký số

Để ký, dùng engine `Signature` với scheme **RSASSA-PSS**, tham số được khai báo rõ. Ví dụ dùng hash SHA-256, MGF1 với SHA-256, salt dài 32 byte và trailer field 1:

```java
PSSParameterSpec pss = new PSSParameterSpec(
        "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1);
Signature signer = Signature.getInstance("RSASSA-PSS");
signer.setParameter(pss);
signer.initSign(privateSigningKey);
signer.update(message);
byte[] signature = signer.sign();
```

Đây cũng chỉ là **snippet**: phải cung cấp private key ký, byte thông điệp, import các class liên quan và xử lý ngoại lệ. Ở phía verify, dùng `initVerify(publicSigningKey)`, cùng bộ tham số PSS và `verify(signature)`. Nếu digest, MGF1 digest, salt length hoặc scheme không thống nhất, xác minh có thể thất bại.

`SHA256withRSA` là tên chữ ký RSA với scheme **PKCS#1 v1.5**, khác **`RSASSA-PSS`**. Có thể vẫn phải xác minh chữ ký cũ tương thích, nhưng khi thiết kế mới và hai phía hỗ trợ, RSA-PSS thường được ưu tiên. **Không** dùng `Cipher.DECRYPT_MODE` để “xác minh chữ ký RSA” hoặc `Cipher.ENCRYPT_MODE` với private key để thay cho `Signature`.

RSA **không phải** một cơ chế `KeyAgreement`/Diffie–Hellman. Trong kiến trúc bảo mật hiện đại, RSA thường dùng cho xác thực/chữ ký hoặc bảo vệ khóa theo giao thức thích hợp; thỏa thuận khóa có forward secrecy được đảm nhiệm bởi các cơ chế DH khác.

### 2.3 Kích thước khóa và phân tách mục đích

RSA modulus **2048 bit** là kích thước thường thấy làm mức tối thiểu cho nhiều hệ thống hiện có; yêu cầu thực tế có thể cao hơn theo tuổi thọ dữ liệu và chính sách. Độ dài modulus, thuật toán băm, scheme padding, entropy sinh khóa và khả năng provider đều quan trọng. Trong production nên dùng **các cặp khóa RSA khác nhau** cho **mã hóa** và **chữ ký** nếu hệ thống cần cả hai, để chính sách, quyền truy cập và vòng đời khóa được phân tách rõ.

## 3. EC: kiểu khóa elliptic curve, dùng cho ECDSA hoặc ECDH

`EC` trong `KeyPairGenerator.getInstance("EC")` biểu thị khả năng tạo khóa trên một **đường cong EC**. Bạn phải xác định **named curve** phù hợp, ví dụ:

```java
KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
generator.initialize(new ECGenParameterSpec("secp256r1"));
KeyPair pair = generator.generateKeyPair();
```

Đây là snippet; cần import `java.security.KeyPairGenerator`, `java.security.KeyPair` và `java.security.spec.ECGenParameterSpec`. `secp256r1` còn thường gọi là **NIST P-256**; `secp384r1` tương ứng P-384. Provider có thể có tập đường cong được hỗ trợ khác nhau; không giả định một đường cong bất kỳ đều sẵn có.

### 3.1 ECDSA: chữ ký bằng khóa EC

Với cặp khóa EC trên P-256, một lựa chọn thường dùng là:

```java
Signature signature = Signature.getInstance("SHA256withECDSA");
signature.initSign(signingPrivateKey);
signature.update(message);
byte[] signed = signature.sign();
```

Tên `SHA256withECDSA` mô tả **ECDSA** với digest **SHA-256**. Chữ ký ECDSA có các thành phần toán học `r` và `s`; định dạng byte mà hệ thống trao đổi phải được thống nhất. Java `SHA256withECDSA` thông thường trả chữ ký ASN.1 DER; một số giao thức như JOSE yêu cầu **P1363 raw r||s** và không thể sử dụng byte DER trực tiếp mà không chuyển đúng. Java cũng có tên dạng `SHA256withECDSAinP1363Format` khi provider hỗ trợ.

ECDSA phụ thuộc rất mạnh vào tính đúng của **nonce bí mật dùng cho mỗi chữ ký**. Nonce lặp lại hoặc thiên lệch có thể làm lộ private key. Để provider JCA xử lý cơ chế ký và nguồn ngẫu nhiên; **không tự lập trình công thức ECDSA** hoặc tái sử dụng giá trị nonce.

### 3.2 ECDH: thỏa thuận shared secret bằng khóa EC

Cũng với khóa loại `EC` nhưng sử dụng engine khác:

```java
KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
agreement.init(alicePrivateKey);
agreement.doPhase(bobPublicKey, true);
byte[] sharedSecret = agreement.generateSecret();
```

Đây là snippet, cần các biến khóa đúng loại/đúng curve và import `javax.crypto.KeyAgreement`. Bên Bob có thể tính giá trị bằng `bobPrivateKey` và `alicePublicKey`: hai phía nhận **cùng shared secret** nếu khóa và các tham số tương thích.

**ECDH không tạo chữ ký, không mã hóa dữ liệu và không tự xác thực danh tính đối phương.** Nếu attacker thay public key trong trao đổi, họ có thể thực hiện man-in-the-middle. Cần chứng thực public key (certificate, chữ ký trong protocol, khóa đã ghim hoặc hệ thống danh tính được tin cậy). **Không** đưa raw ECDH output trực tiếp vào `SecretKeySpec("AES")` mà không có cơ chế dẫn xuất khóa và ràng buộc context; dùng **HKDF** hay KDF phù hợp để tạo khóa phiên.

Dùng một cặp khóa EC để minh họa hai engine trong bài học là có thể về mặt API, nhưng production nên **phân tách khóa ký và khóa thỏa thuận** theo mục đích, nhất là khi chính sách/certificate phân biệt `digitalSignature` và `keyAgreement`.

## 4. EdDSA: chuyên dùng cho chữ ký số

**EdDSA (Edwards-curve Digital Signature Algorithm)** là một họ chữ ký số; những biến thể nổi bật:

- **Ed25519**: chữ ký số trên curve Edwards25519, phổ biến cho chữ ký ứng dụng, khóa danh tính, chứng thực cấu hình.
- **Ed448**: một lựa chọn khác, kích thước và đặc tính bảo mật khác; mức hỗ trợ tùy provider.
- Java 17 hỗ trợ tên `EdDSA` tổng quát và những tên cụ thể như `Ed25519` và `Ed448` trong các engine thích hợp. Chọn tên **cụ thể** khi một giao thức quy định rõ biến thể.

Cách ký/verify Java:

```java
KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
KeyPair keys = generator.generateKeyPair();

Signature signer = Signature.getInstance("Ed25519");
signer.initSign(keys.getPrivate());
signer.update(message);
byte[] signature = signer.sign();

Signature verifier = Signature.getInstance("Ed25519");
verifier.initVerify(keys.getPublic());
verifier.update(message);
boolean valid = verifier.verify(signature);
```

Đây là snippet trong một phương thức có các imports/ngoại lệ thích hợp. Với Ed25519, chữ ký chuẩn dài **64 byte**. Một đặc tính của biến thể Ed25519 chuẩn là quá trình ký **xác định (deterministic)** từ khóa và thông điệp theo thuật toán; điểm này khác yêu cầu nonce khó đoán của ECDSA thông thường. Tuy vậy, private key, nguồn ngẫu nhiên ban đầu khi sinh khóa và kiểm soát implementation vẫn phải được bảo vệ nghiêm ngặt.

**EdDSA không cung cấp `Cipher` hoặc `KeyAgreement`.** Lệnh `Signature.getInstance("Ed25519")` là đúng; `KeyAgreement.getInstance("Ed25519")` không phải cách tạo shared secret, cũng không được gửi public key Ed25519 vào `KeyAgreement.getInstance("X25519")` và giả sử hai loại khóa tương đương.

Ed25519 và X25519 đều liên quan đến đường cong 25519 ở những biểu diễn/toán học khác nhau, nhưng có **mục đích, thuật toán, format key và ràng buộc bảo mật khác nhau**. Có những phép chuyển đổi trong một số giao thức/chuyên thư viện, song **không phải quy tắc JCA portable** và không phải lý do để dùng chung cùng vật liệu khóa. Hãy sinh **khóa Ed25519 cho ký** và **khóa X25519 cho DH** độc lập.

## 5. XDH: chuyên dùng cho thỏa thuận khóa

**XDH** là tên họ DH với các biến thể **X25519** và **X448**, dựa trên RFC 7748. Đây là kỹ thuật để hai bên tính shared secret từ private key của mình và public key đối tác:

```java
KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
KeyPair alice = generator.generateKeyPair();
KeyPair bob = generator.generateKeyPair();

KeyAgreement ka = KeyAgreement.getInstance("X25519");
ka.init(alice.getPrivate());
ka.doPhase(bob.getPublic(), true);
byte[] secretA = ka.generateSecret();
```

Đây là đoạn minh họa bên trong phương thức; Bob thực hiện tương ứng bằng private key của Bob + public key của Alice để thu shared secret giống nhau. `XDH` cũng là tên thuật toán tổng quát, có thể chọn tham số bằng `NamedParameterSpec.X25519` khi provider hỗ trợ; dùng tên `X25519` tường minh thường đơn giản hơn nếu giao thức quy định sẵn. X25519 tạo shared secret **32 byte**, nhưng **32 byte raw secret đó chưa phải khóa AES ứng dụng**.

### Từ shared secret tới khóa phiên

Trong một protocol hợp lý, hai bên:

1. Sinh hai cặp khóa DH phù hợp, ví dụ **ephemeral X25519** cho mỗi phiên.
2. Trao đổi public key qua một thông điệp bắt tay được **xác thực** (certificate/Ed25519/RSA-PSS/ECDSA hoặc cơ chế đáng tin phù hợp). Dùng đúng public key phía đối tác, kiểm tra key format và thông số.
3. Tính raw secret bằng `KeyAgreement`; nếu implementation từ chối public key yếu hoặc cho kết quả không hợp lệ, **fail closed**. RFC 7748 có trường hợp đầu ra toàn zero khi gặp input bậc nhỏ; nhiều provider kiểm tra và ném ngoại lệ, nhưng ứng dụng không nên giả định mọi provider đều giống nhau.
4. Dẫn xuất khóa qua **HKDF** với salt/info và ngữ cảnh phiên đúng format, có thể ràng buộc cả transcript, vai trò hai phía và protocol version.
5. Sử dụng khóa phiên dẫn xuất trong **AEAD** như AES-GCM/ChaCha20-Poly1305 với nonce duy nhất, và xóa/cô lập secret trung gian trong mức khả thi.

Lưu ý: Các bước `HKDF-Extract(secret)` và `HKDF-Expand(info, length)` ở đây là **mô tả quy trình**, không phải method có sẵn tên đó trong API chuẩn Java 17. Dùng một thư viện HKDF đã kiểm thử, hoặc implementation được đánh giá riêng; không tự thay KDF bằng cách cắt 16 byte đầu raw shared secret.

**X25519 không tự chứng minh đối phương là ai.** Nếu cả hai public key có thể bị thay trên đường truyền, hai bên có thể cùng tạo shared secret với attacker thay vì với nhau. Chữ ký số, certificate hoặc xác thực bắt tay là một phần khác bắt buộc của giao thức.

### Forward secrecy là đặc tính của giao thức, không chỉ tên thuật toán

Với DH **ephemeral** và khóa tạm được xóa sau khi hoàn tất, nếu khóa danh tính lâu dài bị lộ về sau thì dữ liệu phiên cũ có thể vẫn được bảo vệ theo các giả định của protocol. Điều này phụ thuộc thiết kế giao thức đầy đủ, cách xác thực, quản lý transcript, dẫn xuất khóa và việc xóa secret; chỉ dùng `X25519` trong một ứng dụng **không tự đảm bảo forward secrecy**.

## 6. Chương trình Java 17 chạy được: minh họa cả bốn họ

Chương trình dưới đây dùng **mỗi engine đúng mục đích**. Nó sinh các cặp RSA khác nhau cho **mã hóa** và **ký**, sinh khóa EC riêng cho **ECDSA** và **ECDH**, rồi tạo thêm cặp Ed25519 và hai cặp X25519. Mã minh họa các khả năng của API JCA/JCE; **không** xây dựng một giao thức mạng hoặc hệ thống lưu trữ khóa production.

Tạo tệp **`KeyFamilyDemo.java`**:

```java
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

public final class KeyFamilyDemo {
    private static final byte[] MESSAGE =
            "Java public-key roles".getBytes(StandardCharsets.UTF_8);

    public static void main(String[] args) throws Exception {
        KeyPairGenerator rsaGenerator = KeyPairGenerator.getInstance("RSA");
        rsaGenerator.initialize(2048);
        KeyPair rsaEncryption = rsaGenerator.generateKeyPair();
        KeyPair rsaSigning = rsaGenerator.generateKeyPair();

        OAEPParameterSpec oaep = new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT);
        Cipher rsaEncrypt = Cipher.getInstance("RSA/ECB/OAEPPadding");
        rsaEncrypt.init(Cipher.ENCRYPT_MODE, rsaEncryption.getPublic(), oaep);
        byte[] wrappedMessage = rsaEncrypt.doFinal(MESSAGE);
        Cipher rsaDecrypt = Cipher.getInstance("RSA/ECB/OAEPPadding");
        rsaDecrypt.init(Cipher.DECRYPT_MODE, rsaEncryption.getPrivate(), oaep);
        System.out.println("RSA-OAEP decrypt: "
                + Arrays.equals(MESSAGE, rsaDecrypt.doFinal(wrappedMessage)));

        PSSParameterSpec pssParams = new PSSParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1);
        System.out.println("RSA-PSS verify: "
                + signAndVerify("RSASSA-PSS", rsaSigning, pssParams));

        KeyPairGenerator ecGenerator = KeyPairGenerator.getInstance("EC");
        ecGenerator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair ecSigning = ecGenerator.generateKeyPair();
        KeyPair ecAlice = ecGenerator.generateKeyPair();
        KeyPair ecBob = ecGenerator.generateKeyPair();
        System.out.println("ECDSA verify: "
                + signAndVerify("SHA256withECDSA", ecSigning, null));
        System.out.println("ECDH shared secret matches: "
                + sameSecret("ECDH", ecAlice, ecBob));

        KeyPairGenerator edGenerator = KeyPairGenerator.getInstance("Ed25519");
        KeyPair edSigning = edGenerator.generateKeyPair();
        System.out.println("Ed25519 verify: "
                + signAndVerify("Ed25519", edSigning, null));

        KeyPairGenerator xGenerator = KeyPairGenerator.getInstance("X25519");
        KeyPair xAlice = xGenerator.generateKeyPair();
        KeyPair xBob = xGenerator.generateKeyPair();
        System.out.println("X25519 shared secret matches: "
                + sameSecret("X25519", xAlice, xBob));
    }

    private static boolean signAndVerify(
            String algorithm, KeyPair keyPair, PSSParameterSpec pss)
            throws Exception {
        Signature signer = Signature.getInstance(algorithm);
        if (pss != null) {
            signer.setParameter(pss);
        }
        signer.initSign(keyPair.getPrivate());
        signer.update(MESSAGE);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance(algorithm);
        if (pss != null) {
            verifier.setParameter(pss);
        }
        verifier.initVerify(keyPair.getPublic());
        verifier.update(MESSAGE);
        boolean original = verifier.verify(signature);

        verifier.initVerify(keyPair.getPublic());
        if (pss != null) {
            verifier.setParameter(pss);
        }
        verifier.update("modified".getBytes(StandardCharsets.UTF_8));
        boolean modified = verifier.verify(signature);
        return original && !modified;
    }

    private static boolean sameSecret(
            String algorithm, KeyPair alice, KeyPair bob) throws Exception {
        KeyAgreement agreementA = KeyAgreement.getInstance(algorithm);
        agreementA.init(alice.getPrivate());
        agreementA.doPhase(bob.getPublic(), true);
        byte[] a = agreementA.generateSecret();

        KeyAgreement agreementB = KeyAgreement.getInstance(algorithm);
        agreementB.init(bob.getPrivate());
        agreementB.doPhase(alice.getPublic(), true);
        byte[] b = agreementB.generateSecret();

        boolean same = MessageDigest.isEqual(a, b);
        Arrays.fill(a, (byte) 0);
        Arrays.fill(b, (byte) 0);
        return same;
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 KeyFamilyDemo.java
java KeyFamilyDemo
```

Đầu ra **trên môi trường Java 17+ có provider hỗ trợ các dịch vụ được yêu cầu**:

```text
RSA-OAEP decrypt: true
RSA-PSS verify: true
ECDSA verify: true
ECDH shared secret matches: true
Ed25519 verify: true
X25519 shared secret matches: true
```

**Ý nghĩa từng dòng:**

- `RSA-OAEP decrypt: true`: public RSA dành cho mã hóa, private RSA cùng cặp giải mã được **thông điệp ngắn**. OAEP có SHA-256/MGF1 SHA-256 được chỉ định rõ.
- `RSA-PSS verify: true`: chữ ký RSA-PSS xác minh bằng public signing key; helper còn kiểm tra chữ ký này **không** hợp lệ cho thông điệp `"modified"`.
- `ECDSA verify: true`: khóa EC trên `secp256r1` tạo/kiểm chứng chữ ký ECDSA-SHA256; helper cũng thử phát hiện thông điệp bị đổi.
- `ECDH shared secret matches: true`: Alice/Bob dùng private key của mình và public key đối tác trên cùng loại curve, tạo shared secret trùng nhau.
- `Ed25519 verify: true`: một cặp khóa chuyên ký/verify Ed25519 và kiểm thử tampering.
- `X25519 shared secret matches: true`: hai cặp X25519 sinh secret giống nhau khi DH thành công.

Các giá trị ngẫu nhiên (key, ciphertext OAEP, chữ ký RSA-PSS/ECDSA, DH raw secret) **khác nhau giữa các lần chạy**; chương trình không in chúng ra. Các dòng `true` là kiểm tra tính đúng đắn, **không chứng minh public key của đối tác đã được xác thực hoặc giao thức có bảo mật toàn diện**.

**Lưu ý về code:** `sameSecret` so sánh hai secret để phục vụ test, sau đó cố gắng ghi đè hai mảng byte cục bộ. Java và provider có thể giữ các bản sao trong bộ nhớ không thể xóa bằng thao tác này. Production phải dẫn xuất khóa phiên với HKDF và có vòng đời secret phù hợp; không đưa raw DH output thẳng vào AES. RSA-OAEP demo mã hóa trực tiếp một thông điệp nhỏ cho dễ quan sát; dữ liệu dài nên dùng mã hóa lai.

## 7. Chọn cơ chế theo bài toán thực tế

| Bài toán | Nên xem xét | Vì sao |
| --- | --- | --- |
| Ký file cấu hình, bản phát hành hoặc thông điệp | Ed25519, ECDSA hoặc RSA-PSS theo hệ sinh thái và chuẩn giao thức | Người nhận verify bằng public key; có thể quản lý trust chain |
| Mã hóa một AES content key cho người nhận có RSA public key | RSA-OAEP + AES-GCM theo format định nghĩa | RSA bảo vệ khóa ngắn, AEAD bảo vệ dữ liệu lớn |
| Hai bên tạo khóa phiên khi bắt tay | X25519 hoặc ECDH + HKDF + xác thực public keys | DH đồng ý secret, HKDF dẫn xuất khóa và cơ chế xác thực ngăn MITM |
| Cần kết hợp khóa danh tính ký và khóa phiên DH | Ed25519 cho signature và X25519 cho DH, hoặc RSA-PSS/ECDSA + ECDH | Chọn từng thuật toán theo vai trò, không dùng một loại khóa làm tất cả |
| Dùng certificate EC đang có | Kiểm tra key usage và thuật toán hỗ trợ của certificate | Certificate ECDSA không mặc nhiên dùng được để làm ECDH |
| TLS cho web/app | Dùng JSSE/TLS stack đã kiểm thử | TLS tự quản lý bắt tay, chứng thực, DH, KDF và AEAD; tránh tự chế giao thức |

**Ví dụ sơ đồ bắt tay (mô hình khái niệm):**

```text
Alice tạo X25519 ephemeral A               Bob tạo X25519 ephemeral B
         |                                              |
         +---- public(A), ngữ cảnh ---->                 |
         |                 <---- public(B), chứng thực --+
         |                                              |
 DH(privateA, publicB)                       DH(privateB, publicA)
         |                                              |
         +---- cùng raw DH secret khi hợp lệ ------------+
         |                                              |
 HKDF(secret, transcript, roles)              HKDF(secret, transcript, roles)
         |                                              |
       khóa phiên AEAD, nonce theo giao thức + kiểm tra xác thực
```

Sơ đồ cố tình lược bỏ chi tiết của TLS/HPKE. Điều **không được lược bỏ** trong triển khai thật là cách **xác thực** các ephemeral public keys và transcript, ngăn downgrade/replay, giới hạn thuật toán, kiểm tra giá trị đầu vào và dẫn xuất key/nonce tách biệt theo chiều gửi/nhận.

## 8. Các tên JCA và định dạng khóa dễ nhầm

### Engine và tên thuật toán

| Java API | Tên đề xuất | Dùng để làm gì |
| --- | --- | --- |
| `KeyPairGenerator.getInstance(...)` | `RSA`, `EC`, `Ed25519`, `X25519` | Sinh đúng kiểu cặp khóa |
| `Cipher.getInstance(...)` | `RSA/ECB/OAEPPadding` | RSA-OAEP mã hóa/giải mã đầu vào nhỏ |
| `Signature.getInstance(...)` | `RSASSA-PSS`, `SHA256withECDSA`, `Ed25519` | Ký/xác minh |
| `KeyAgreement.getInstance(...)` | `ECDH`, `X25519` | Thỏa thuận raw secret |
| `KeyFactory.getInstance(...)` | `RSA`, `EC`, `Ed25519`, `X25519` | Tạo lại đối tượng khóa từ encoding/spec phù hợp |

`getInstance` tạo engine theo **service type + algorithm name**, chọn provider hiện tại; provider nào có dịch vụ cụ thể cần thử đúng trên môi trường sẽ triển khai. `Security.getAlgorithms("Signature")` và `Security.getAlgorithms("KeyAgreement")` giúp khám phá tên đã đăng ký, nhưng không đủ để chứng minh **khóa/curve/params** của hệ thống được chấp nhận.

### X.509 và PKCS#8 không phải tên thuật toán ký

Một public key được xuất dưới dạng SubjectPublicKeyInfo thường được xử lý bằng `X509EncodedKeySpec`; private key `PrivateKeyInfo` thường dùng `PKCS8EncodedKeySpec`. Các thuật ngữ **X.509** và **PKCS#8** ở đây là *định dạng dữ liệu khóa*, còn **RSA**, **EC**, **Ed25519**, **X25519** là *thuật toán/kiểu khóa*. Chuỗi PEM có các dòng `-----BEGIN PUBLIC KEY-----` hoặc `-----BEGIN PRIVATE KEY-----` thường chỉ bọc Base64 quanh DER; phải decode, xác nhận đúng format rồi dùng `KeyFactory` với kiểu thuật toán phù hợp.

Không nhầm `X509EncodedKeySpec` với **chứng chỉ X.509**: public-key object chứa khóa, chứng chỉ còn có danh tính, tổ chức phát hành, thời hạn và chữ ký chứng thực. Import public key từ một file không tự biến nó thành danh tính đáng tin.

Không tự lấy `getEncoded()` của HSM/keystore key rồi ghi vào log: nhiều private key bảo vệ bằng hardware/keystore không thể xuất byte (`getEncoded()` có thể trả `null`), và đây là tính năng bảo mật mong muốn. Để kiểm tra khóa hỗ trợ gì, dùng JCA đúng engine và xác minh `KeyUsage`/chính sách thay vì chỉ đọc `key.getAlgorithm()`.

### Sai tên thường gặp

- `Signature.getInstance("RSA")`: muốn ký theo RSA hãy chọn scheme **`RSASSA-PSS`** (và PSS parameters), hoặc một tên chữ ký cụ thể mà protocol yêu cầu.
- `Cipher.getInstance("EC")`, `Cipher.getInstance("Ed25519")`, `Cipher.getInstance("X25519")`: không phải những cách biểu diễn hợp lệ để có mã hóa ứng dụng bằng các loại khóa đó.
- `Signature.getInstance("X25519")`: X25519 phục vụ DH, không có chữ ký X25519.
- `KeyAgreement.getInstance("Ed25519")`: Ed25519 phục vụ chữ ký, không có DH tên Ed25519.
- `Signature.getInstance("ECDH")`: ECDH là thỏa thuận khóa, không có chữ ký ECDH.
- Đưa cặp khóa Ed25519 vào `KeyAgreement("X25519")` hoặc cặp X25519 vào `Signature("Ed25519")`: sai key type, thường ném `InvalidKeyException`.
- So các byte signature ECDSA ở hai lần ký để kết luận thuật toán lỗi: ECDSA thường có randomness nên có thể khác; hãy dùng **`verify`**.
- `SHA256withRSA` và `RSASSA-PSS` là các scheme chữ ký khác nhau, không thể hoán đổi khi verify nếu metadata/protocol không chỉ rõ.

## 9. Kiểm tra khả năng hỗ trợ và xử lý lỗi theo đúng lớp

Một cách kiểm tra ý nghĩa hơn việc chỉ đọc danh sách tên là chạy thử **cùng API, curve và bộ tham số** ứng dụng sẽ sử dụng:

1. Tạo đúng `KeyPairGenerator` theo thuật toán và curve: RSA modulus, `ECGenParameterSpec("secp256r1")` hoặc `Ed25519`/`X25519`.
2. Kiểm tra `getInstance` của **đúng engine**: `Cipher`, `Signature` hay `KeyAgreement`.
3. Chạy đầy đủ `init` và phép tính: RSA encrypt/decrypt; signature sign/verify cả đúng lẫn sai message; ECDH/X25519 hai chiều và so shared secret.
4. Kiểm tra khớp với policy: provider/HSM, key usage, key ID, param OAEP/PSS, encoding, curve, quyền sử dụng khóa.
5. Tách lỗi cấu hình khỏi lỗi verify hoặc input không hợp lệ; không fallback yếu một cách ngầm định.

| Biểu hiện | Nguyên nhân nên kiểm tra |
| --- | --- |
| `NoSuchAlgorithmException` | Engine/algorithm name không được provider hỗ trợ (vd. gọi `Signature("X25519")`); kiểm tra tên chuẩn và provider |
| `NoSuchPaddingException` từ `Cipher.getInstance` | Transformation không được cung cấp; kiểm tra đúng OAEP padding và provider |
| `InvalidKeyException` khi `initSign/initVerify/init/doPhase` | Key không đúng loại, sai đường cong, thiếu quyền HSM, public key bị reject |
| `InvalidAlgorithmParameterException` | Spec đường cong/OAEP/PSS không được chấp nhận hoặc không khớp policy |
| `SignatureException` hoặc `verify == false` | Đầu vào, chữ ký, kiểu mã hóa, thuật toán, params hoặc public key không phù hợp; chữ ký giả mạo cũng có thể gây ra kết quả tương tự |
| `BadPaddingException` / `IllegalBlockSizeException` khi RSA-OAEP | Ciphertext lỗi/sai khóa/sai tham số hoặc input vượt giới hạn; không tiết lộ chi tiết cho client |
| ECDH/X25519 cho secret khác nhau | Không đúng private/public peers, curve/format bị đổi hoặc lỗi implementation; từ chối phiên |
| `KeyFactory` không đọc được public/private key | Sai format X.509/PKCS#8, sai algorithm OID, thông số hoặc provider |

**Không công bố cho client những thông tin nội bộ có thể làm lộ khóa hoặc tạo oracle.** Tại ranh giới API, áp dụng thông báo lỗi chung đối với giải mã/chữ ký không hợp lệ, kết hợp logging nội bộ có kiểm soát. Nếu một thuật toán được giao thức yêu cầu nhưng provider không hỗ trợ, nên xử lý như lỗi cấu hình hoặc fail fast; không tự chuyển RSA-OAEP thành RSA raw, hoặc chuyển XDH thành một khóa mặc định.

## 10. Quản lý khóa và lựa chọn dài hạn

- **Tách khóa theo mục đích**: khóa chữ ký khác khóa giải mã; khóa danh tính dài hạn khác khóa thỏa thuận tạm thời. Áp dụng key usage đúng trong certificate, key store và quyền KMS/HSM.
- **Tạo khóa bằng nguồn ngẫu nhiên mật mã** và provider tin cậy. Không tạo private key bằng mật khẩu hay từ các byte đoán được. Không sao chép khóa chỉ vì chúng có cùng độ dài.
- **Ràng buộc danh tính public key**: public key không phải secret, nhưng việc kẻ tấn công thay thế public key vẫn phá vỡ bảo mật của mã hóa, chữ ký hoặc DH nếu không xác thực được nguồn.
- **Chọn scheme và tham số rõ ràng**: RSA-PSS cần MGF1/salt/digest, RSA-OAEP cần digest và MGF1, EC cần named curve, XDH cần đúng X25519/X448, EdDSA cần đúng Ed25519/Ed448.
- **Dẫn xuất session key từ DH**: dùng KDF và protocol context, không cắt raw shared secret tùy tiện. Chống replay, downgrade, xác thực transcript, dùng AEAD và nonce duy nhất.
- **Kiểm tra hệ sinh thái**: khả năng tương thích của TLS, certificate, JOSE/JWT, định dạng chữ ký DER/P1363, nền tảng HSM/FIPS hay thư viện đối tác có thể buộc chọn thuật toán khác nhau.
- **Không đánh đồng kích thước byte public key/signature với mức bảo mật**: RSA-2048, P-256, Ed25519, X25519 có cấu trúc và chi phí toán học rất khác nhau; tuân theo chuẩn/chính sách để chọn mức bảo mật cần thiết.
- **Đánh giá vòng đời thuật toán**: RSA/ECC cổ điển không cung cấp an toàn hậu lượng tử. Nếu yêu cầu bảo mật dữ liệu dài hạn hoặc hệ thống hướng tới chuẩn hậu lượng tử, tham khảo lựa chọn được chuẩn hóa như KEM/chữ ký hậu lượng tử trong giao thức phù hợp; không tự trộn khóa/thuật toán ngoài một protocol được đánh giá.

## 11. Bài thực hành

1. Trong `KeyFamilyDemo.java`, chỉ ra dòng nào dùng `Cipher`, dòng nào dùng `Signature` và dòng nào dùng `KeyAgreement`. Vì sao `X25519` không thể thay thế trực tiếp cho `Ed25519`?
2. Thay `SHA256withECDSA` bằng `Ed25519` nhưng vẫn dùng một khóa `EC` cho `Signature.initSign`. Dự đoán và kiểm tra loại lỗi, rồi giải thích key type nào bị sai.
3. Xây dựng bảng policy: ứng dụng cần **ký file cấu hình**, **thỏa thuận khóa phiên**, **mã hóa file lớn**, **bảo vệ AES content key bằng RSA**. Với mỗi yêu cầu, chọn đúng scheme, engine và cách quản lý key.
4. Tại sao RSA-OAEP dùng modulus 2048 bit và SHA-256 không mã hóa tùy ý 1 MiB plaintext trong một lần? Hãy tính giới hạn 190 byte bằng công thức `k - 2*hLen - 2`.
5. Nếu raw secret ECDH và X25519 trùng nhau giữa hai phía, có thể kết luận họ đang trao đổi với đúng đối tác chưa? Đề xuất cách xác thực transcript/public key.
6. Thêm cơ chế lưu và nạp public key được tin cậy theo `X509EncodedKeySpec`. Phân biệt raw public key từ file với chứng chỉ X.509 có chuỗi tin cậy.
7. Kiểm tra khả năng hỗ trợ `RSASSA-PSS`, `Ed25519` và `X25519` trên JVM/provider triển khai. Ghi Java version, provider, params và kết quả các phép thử, **không ghi private key**.

## Tài liệu tham khảo

- [Java SE 17 — Java Security Standard Algorithm Names](https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html): tên chuẩn `RSA`, `EC`, `RSASSA-PSS`, `EdDSA`, `Ed25519`, `XDH`, `X25519` và service types.
- [Java SE 17 — JCA Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-cryptography-architecture-jca-reference-guide.html): JCA provider, `KeyPairGenerator`, `Signature`, `KeyAgreement` và `Cipher`.
- [Java SE 17 — `Signature`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Signature.html): ký và xác minh với RSA-PSS, ECDSA, EdDSA.
- [Java SE 17 — `KeyAgreement`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/KeyAgreement.html): giao thức thỏa thuận khóa và cách dùng `doPhase`/`generateSecret`.
- [Java SE 17 — `OAEPParameterSpec`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/OAEPParameterSpec.html): thông số OAEP và MGF1.
- [Java SE 17 — `PSSParameterSpec`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/spec/PSSParameterSpec.html): digest, MGF, salt length, trailer field.
- [RFC 8017 — PKCS #1 v2.2](https://www.rfc-editor.org/rfc/rfc8017): RSAES-OAEP và RSASSA-PSS.
- [RFC 8032 — Edwards-Curve Digital Signature Algorithm](https://www.rfc-editor.org/rfc/rfc8032): Ed25519/Ed448.
- [RFC 7748 — Elliptic Curves for Security](https://www.rfc-editor.org/rfc/rfc7748): X25519/X448 và vấn đề đầu ra all-zero.
- [RFC 5869 — HKDF](https://www.rfc-editor.org/rfc/rfc5869): dẫn xuất khóa từ shared secret.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="public-key-cryptography-004">8.4 Chuyển đổi khóa bằng KeyFactory và KeySpec</a>

<details>
<summary>Xem chi tiết</summary>

# 8.4 Chuyển đổi khóa bằng KeyFactory và KeySpec

Trong Java Cryptography Architecture (JCA), thuật toán bất đối xứng làm việc với `PublicKey` và `PrivateKey`, nhưng khóa có thể xuất hiện dưới nhiều hình thức: đối tượng do provider tạo ra, thành phần toán học (như modulus/exponent của RSA), mảng byte DER, hoặc chuỗi PEM trong tệp cấu hình. **`KeyFactory`** là API dùng để dựng đối tượng khóa từ **`KeySpec`**, lấy một đặc tả phù hợp từ khóa và chuyển khóa giữa các dạng mà provider hỗ trợ. Nắm chắc ranh giới giữa ba thứ — **key object**, **key specification** và **encoding** — giúp tránh nhập sai định dạng, nhầm public/private key, làm lộ khóa riêng hoặc cho rằng việc giải mã DER đã xác thực được nguồn khóa.

Bài học tập trung vào API Java 17+ cho **khóa công khai và khóa riêng bất đối xứng**. Các ví dụ tạo khóa tạm thời trong bộ nhớ để kiểm chứng round-trip bằng RSA, EC, Ed25519 và PEM; phần vận hành giải thích các trường hợp khóa không được phép xuất, chứng chỉ, provider và lỗi đầu vào.

## 1. Bốn loại đối tượng cần phân biệt

| Thành phần | Java API | Vai trò | Điều không nên suy diễn |
| --- | --- | --- | --- |
| **Đối tượng khóa** | `Key`, `PublicKey`, `PrivateKey` | Đối tượng mà `Signature`, `Cipher`, `KeyAgreement` hoặc API khác có thể sử dụng theo thuật toán | Một `Key` không nhất thiết cho phép xuất vật liệu khóa bằng `getEncoded()`. |
| **Đặc tả khóa** | `KeySpec` và các lớp triển khai | Mô tả dữ liệu dùng để dựng khóa hoặc một biểu diễn được trích ra từ khóa | `KeySpec` không đồng nghĩa key đã được provider chấp nhận hay được bảo vệ khỏi đọc. |
| **Bộ chuyển đổi** | `KeyFactory` | `KeySpec → Key`, `Key → KeySpec`, `Key → Key` qua `translateKey` | Không tạo cặp khóa ngẫu nhiên, không thực hiện chữ ký, không kiểm tra danh tính chủ sở hữu. |
| **Bộ sinh cặp khóa** | `KeyPairGenerator` | Sinh cặp `PublicKey`/`PrivateKey` mới từ tham số thuật toán | Không phải công cụ nhập một khóa đã tồn tại từ DER/PEM. |

Luồng dữ liệu hay gặp:

```text
      KeyPairGenerator("RSA")                 tệp/public API
                |                                  |
                v                                  v
        KeyPair(PublicKey, PrivateKey)         DER/PEM bytes
                |                                  |
         getEncoded()                                |
                |                                  v
          DER byte arrays             X509EncodedKeySpec (public)
                |                      PKCS8EncodedKeySpec (private)
                |                                  |
                v                                  v
     X509EncodedKeySpec                     KeyFactory("RSA")
     PKCS8EncodedKeySpec                      generatePublic
                |                            generatePrivate
                +--------------+-------------------+
                               |
                  PublicKey / PrivateKey
                               |
                       Signature / Cipher
```

**Tên thuật toán phải khớp**: `KeyFactory.getInstance("RSA")` xử lý khóa RSA; `KeyFactory.getInstance("EC")` xử lý khóa elliptic curve theo provider hỗ trợ; `KeyFactory.getInstance("Ed25519")` dành cho Ed25519 trên JDK phù hợp. `KeyFactory` không nhận tên phép ký như `SHA256withRSA` hay `SHA256withECDSA`: đó là tên dùng cho `Signature`, không phải tên factory của khóa.

### 1.1 Key khác SecretKey

`Key` là interface chung, nhưng `KeyFactory` thuộc luồng **khóa bất đối xứng** (`PublicKey`/`PrivateKey`). Đối với khóa bí mật có thể dẫn xuất từ các `KeySpec` như `PBEKeySpec`, Java có **`SecretKeyFactory`** và các cơ chế khóa đối xứng riêng. Đừng lấy `KeyFactory.getInstance("AES")` để nhập một mảng byte AES; nếu chính sách cho phép một khóa AES raw, luồng JCE thường dùng `SecretKeySpec` hoặc KeyGenerator/KMS thích hợp.

## 2. API chính của KeyFactory

```java
// Những dòng dưới đây chỉ minh họa chữ ký API và luồng sử dụng;
// key, spec, providerName phải được tạo/nhận trong chương trình thật.
KeyFactory factory = KeyFactory.getInstance("RSA");
PublicKey publicKey = factory.generatePublic(publicKeySpec);
PrivateKey privateKey = factory.generatePrivate(privateKeySpec);
RSAPublicKeySpec rsaSpec =
        factory.getKeySpec(publicKey, RSAPublicKeySpec.class);
Key translated = factory.translateKey(publicKey);
```

Các phương thức cốt lõi:

| Lời gọi | Đầu vào | Đầu ra / ngoại lệ chính |
| --- | --- | --- |
| `KeyFactory.getInstance("RSA")` | Tên thuật toán của **key factory** | `KeyFactory`; có thể ném `NoSuchAlgorithmException` nếu không có implementation. |
| `KeyFactory.getInstance("RSA", "providerName")` | Thuật toán và tên provider đã đăng ký | Có thể ném `NoSuchProviderException` nếu tên provider không tồn tại, hoặc `NoSuchAlgorithmException` nếu provider không hỗ trợ. |
| `generatePublic(KeySpec)` | Đặc tả public key thích hợp | `PublicKey`, hoặc `InvalidKeySpecException`. |
| `generatePrivate(KeySpec)` | Đặc tả private key thích hợp | `PrivateKey`, hoặc `InvalidKeySpecException`. |
| `getKeySpec(Key, Class<T>)` | Đối tượng khóa và **lớp** KeySpec mong muốn | Một `T extends KeySpec`, hoặc `InvalidKeySpecException` nếu không thể biểu diễn/chuyển đổi. |
| `translateKey(Key)` | Đối tượng khóa có thể đến từ provider khác | Đối tượng `Key` do factory/provider xử lý, hoặc `InvalidKeyException`. |
| `getProvider()` | Không có | Provider đang đứng sau factory; hữu ích khi chẩn đoán môi trường. |

Hướng đi chính:

```text
spec  --generatePublic/generatePrivate-->  key
key   --getKeySpec(Class<T>)----------->  spec
key   --translateKey------------------->  key của provider/factory đích
```

`translateKey` **không** biến thuật toán RSA thành EC, không suy ra private key từ public key và không bảo đảm xuất được khóa HSM sang bộ nhớ Java. Nó chỉ chuyển một đối tượng khóa **tương thích** về biểu diễn/provider mà factory hỗ trợ, nếu được phép.

Tùy implementation/provider, có thể tồn tại nhiều `KeySpec` hợp lệ cho cùng một khóa. Việc chọn `X509EncodedKeySpec.class` hoặc `RSAPublicKeySpec.class` nói với factory **hình thức kết quả muốn nhận**, không yêu cầu factory tạo một cặp khóa mới.

## 3. KeySpec có hai nhóm hữu ích

### 3.1 EncodedKeySpec: khóa dưới dạng byte có cấu trúc

Hai lớp thông dụng:

| Lớp | Đầu vào mong đợi | `getFormat()` | Dùng để |
| --- | --- | --- | --- |
| `X509EncodedKeySpec` | DER của **SubjectPublicKeyInfo** (SPKI) trong X.509 | `"X.509"` | Dựng **public key** bằng `generatePublic`. |
| `PKCS8EncodedKeySpec` | DER của **PrivateKeyInfo/PKCS #8** không được mã hóa ở lớp bao ngoài | `"PKCS#8"` | Dựng **private key** bằng `generatePrivate`. |

Cả hai đều triển khai `KeySpec` qua `EncodedKeySpec`. Constructor chấp nhận `byte[]`; **việc tạo spec chưa chứng minh DER đúng**. Provider thường giải mã và kiểm tra tính phù hợp của cấu trúc khi gọi `generatePublic`/`generatePrivate`. Nếu DER không hợp lệ, sai thuật toán hoặc không hỗ trợ, thường nhận `InvalidKeySpecException`.

Một khóa RSA được biểu diễn dưới dạng SPKI (public) có **lớp bao AlgorithmIdentifier** để định danh thuật toán và BIT STRING chứa phần dữ liệu khóa. PKCS #8 cho private key có cấu trúc và metadata thuật toán tương ứng. Vì vậy, dù cả hai đều là byte arrays, **không thể hoán đổi** `X509EncodedKeySpec` và `PKCS8EncodedKeySpec`.

### 3.2 TransparentKeySpec: thành phần toán học

Ví dụ RSA:

- `RSAPublicKeySpec`: số nguyên lớn `modulus` (`n`) và `publicExponent` (`e`), cùng optional algorithm parameters theo API.
- `RSAPrivateKeySpec`: `modulus` (`n`) và `privateExponent` (`d`), có thể không chứa đầy đủ thành phần tối ưu CRT.
- `RSAPrivateCrtKeySpec`: chứa các thông số RSA CRT (`p`, `q`, `dP`, `dQ`, `qInv`...) phục vụ biểu diễn khóa riêng đầy đủ.
- Các thuật toán khác có spec tương ứng, ví dụ `ECPublicKeySpec`, `ECPrivateKeySpec`; những tham số đường cong/điểm phải hợp lệ với thuật toán và provider.

Mô tả toán học hữu ích khi tích hợp định dạng chuyên biệt hoặc đối chiếu thành phần khóa. Tuy nhiên, **các `BigInteger` của khóa riêng là dữ liệu bí mật**. Không in ra log, JSON, exception message hay telemetry; không copy thủ công private key components giữa hệ thống nếu không có lý do và kiểm soát rõ ràng.

### 3.3 Opaque Key và KeySpec

Theo JCA, `Key` có `getAlgorithm()`, `getFormat()`, `getEncoded()`. Nhưng **không phải mọi khóa trả encoding**:

- `getFormat()` có thể trả `null` khi khóa không có định dạng xuất được.
- `getEncoded()` có thể trả `null` (ví dụ khóa nằm trong HSM, smart card hoặc hardware-backed keystore).
- `getKeySpec` cũng có thể **bị từ chối** nếu provider không cho xuất vật liệu khóa tương ứng.
- `translateKey` có thể ném `InvalidKeyException` khi khóa không thể chuyển sang provider đích.

Không dùng `Objects.requireNonNull(privateKey.getEncoded())` rồi tự ghi private key ra tệp như một cách xử lý mặc định. Khi thao tác với HSM/KMS, thường **giữ khóa tại provider** và thực hiện ký/giải mã theo capability được phép. Việc không thể xuất khóa riêng là tính chất bảo mật cần tôn trọng.

## 4. DER, PEM, X.509, PKCS #8 và các dạng dễ nhầm

### 4.1 DER là bytes, PEM là vỏ văn bản

**DER** là cách mã hóa nhị phân xác định của ASN.1. **PEM** thường trình bày DER ở dạng Base64 nằm giữa các nhãn `-----BEGIN ...-----` / `-----END ...-----`. Chuyển PEM sang DER là **bóc header/footer và decode Base64**, không phải giải mã mật mã.

Ví dụ nhãn PEM:

```text
-----BEGIN PUBLIC KEY-----
(Base64 cua SubjectPublicKeyInfo DER)
-----END PUBLIC KEY-----
```

```text
-----BEGIN PRIVATE KEY-----
(Base64 cua PKCS #8 khong ma hoa)
-----END PRIVATE KEY-----
```

Các ví dụ trên minh họa **cấu trúc nhãn**, không phải khóa thật và không thể mang đi chạy. Nội dung `PRIVATE KEY` vẫn là bí mật **dù được Base64 hóa**, cần bảo vệ như khóa gốc.

| Nhãn/nguồn dữ liệu | Thường đại diện | Có thể đưa trực tiếp vào `X509EncodedKeySpec` hoặc `PKCS8EncodedKeySpec`? |
| --- | --- | --- |
| `BEGIN PUBLIC KEY` | SPKI DER sau Base64 decode | **Có**, `X509EncodedKeySpec` nếu thuật toán phù hợp. |
| `BEGIN PRIVATE KEY` | PKCS #8 private-key DER chưa mã hóa ở lớp bao ngoài | **Có**, `PKCS8EncodedKeySpec` nếu thuật toán phù hợp. |
| `BEGIN RSA PUBLIC KEY` | RSA public key theo cấu trúc PKCS #1 (chỉ `n`, `e`) | **Không trực tiếp** vào X.509 SPKI; cần parser/chuyển đổi cấu trúc. |
| `BEGIN RSA PRIVATE KEY` | RSA private key theo PKCS #1 | **Không trực tiếp** vào `PKCS8EncodedKeySpec`; cần parser/chuyển đổi hợp lệ hoặc công cụ xuất lại thành PKCS #8. |
| `BEGIN EC PRIVATE KEY` | SEC1 `ECPrivateKey` | **Không trực tiếp** vào PKCS #8; cần chuyển định dạng và tham số đường cong đúng. |
| `BEGIN ENCRYPTED PRIVATE KEY` | PKCS #8 **được mã hóa** ở lớp bao ngoài | **Không trực tiếp**. Phải giải mã lớp bảo vệ bằng password/key và API/thư viện phù hợp trước khi dựng private key. |
| `BEGIN CERTIFICATE` | Chứng chỉ X.509 chứa nhiều trường và chữ ký CA, bên trong có SPKI public key | **Không trực tiếp** vào `X509EncodedKeySpec`. Dùng `CertificateFactory` parse certificate rồi lấy `getPublicKey()`; việc **xác thực chain/trust** là bước riêng. |

Cần phân biệt **`X509EncodedKeySpec` và `X509Certificate`**: spec chỉ chứa public-key encoding SPKI; certificate chứa thêm subject, issuer, validity, extensions và chữ ký. Parse public key từ SPKI **không** chứng minh người gửi hay chủ sở hữu khóa là ai.

### 4.2 PKCS #8 không đồng nghĩa khóa đã được mã hóa bảo vệ

Tên `PKCS8EncodedKeySpec` mô tả **cấu trúc private key**, không có nghĩa nó tự mã hóa bằng mật khẩu. Private key trong `BEGIN PRIVATE KEY` thường chỉ được **đóng gói**, không được bảo vệ bằng encryption ở lớp file. `BEGIN ENCRYPTED PRIVATE KEY` là một cấu trúc khác, liên quan `EncryptedPrivateKeyInfo` và quy trình giải mã an toàn.

Vì vậy:

- Không gửi tệp `PRIVATE KEY` qua email/chat/log chỉ vì nó có dạng PEM hoặc Base64.
- Không ghi string PEM private key trong source, cấu hình công khai hoặc artifact build.
- Khi cần lưu khóa lâu dài, cân nhắc Java `KeyStore`, keystore phần cứng, KMS/HSM và quyền truy cập thay vì tự viết parser và lưu private key thô.
- Mật khẩu bảo vệ keyfile không tự bảo đảm chống truy cập trái phép nếu ứng dụng còn lộ khóa sau khi giải mã vào RAM.

## 5. Ví dụ Java 17+: RSA KeyFactory round-trip và KeySpec toán học

Ví dụ sinh cặp RSA 2048 bit bằng `KeyPairGenerator`, xuất **SPKI DER** cho public key và **PKCS #8 DER** cho private key, dựng lại bằng `KeyFactory`, lấy `RSAPublicKeySpec`, rồi ký/xác minh để chứng minh các đối tượng khóa thực sự tương ứng.

Lưu tệp **`RsaKeyConversionDemo.java`**:

```java
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.util.*;

public final class RsaKeyConversionDemo {
    public static void main(String[] args) throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair original = generator.generateKeyPair();

        byte[] publicDer = original.getPublic().getEncoded();
        byte[] privateDer = original.getPrivate().getEncoded();

        KeyFactory factory = KeyFactory.getInstance("RSA");
        PublicKey restoredPublic =
                factory.generatePublic(new X509EncodedKeySpec(publicDer));
        PrivateKey restoredPrivate =
                factory.generatePrivate(new PKCS8EncodedKeySpec(privateDer));

        RSAPublicKeySpec math =
                factory.getKeySpec(restoredPublic, RSAPublicKeySpec.class);
        PublicKey fromMath = factory.generatePublic(math);
        Key translated = factory.translateKey(restoredPublic);

        Signature signature = Signature.getInstance("SHA256withRSA");
        byte[] data =
                "KeyFactory lesson".getBytes(StandardCharsets.UTF_8);
        signature.initSign(restoredPrivate);
        signature.update(data);
        byte[] signed = signature.sign();

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(fromMath);
        verifier.update(data);

        System.out.println("Public format: "
                + original.getPublic().getFormat());
        System.out.println("Private format: "
                + original.getPrivate().getFormat());
        System.out.println("Public key round-trip: "
                + Arrays.equals(publicDer, restoredPublic.getEncoded()));
        System.out.println("RSA modulus bits: "
                + math.getModulus().bitLength());
        System.out.println("RSA verify: " + verifier.verify(signed));
        System.out.println("Translated algorithm: "
                + translated.getAlgorithm());

        Arrays.fill(privateDer, (byte) 0);
    }
}
```

```bash
javac -encoding UTF-8 RsaKeyConversionDemo.java
java RsaKeyConversionDemo
```

Với JDK thông dụng có RSA KeyFactory và `SHA256withRSA`:

```text
Public format: X.509
Private format: PKCS#8
Public key round-trip: true
RSA modulus bits: 2048
RSA verify: true
Translated algorithm: RSA
```

**Phân tích cụ thể:**

1. `KeyPairGenerator` **sinh** khóa mới. `KeyFactory` **phục hồi/chuyển** khóa đã có; hai API khác mục đích.
2. `getEncoded()` trả về DER của khóa mà provider cho phép xuất; ví dụ dùng khóa phần mềm có thể export. Đừng giả định mã private key luôn tồn tại dưới dạng byte.
3. `new X509EncodedKeySpec(publicDer)` và `new PKCS8EncodedKeySpec(privateDer)` chọn **đúng spec cho đúng loại khóa**.
4. `generatePublic`/`generatePrivate` dựng lại key object từ spec. Kết quả kiểm tra ký/xác minh thành công chứng tỏ cặp tương ứng; nó **không** chứng minh khóa đó thuộc về một danh tính bên ngoài.
5. `getKeySpec(restoredPublic, RSAPublicKeySpec.class)` lấy hai thành phần toán học RSA `n` và `e`; `generatePublic(math)` dựng public key từ các thành phần này.
6. `translateKey(restoredPublic)` chuyển key thành đối tượng tương thích với factory đang dùng, ở đây vẫn là RSA. Provider được chọn mặc định nên ví dụ **không** chứng minh chuyển khóa giữa hai provider cụ thể.
7. `Arrays.equals(publicDer,...)` cho kết quả `true` với implementation thử nghiệm, nhưng **không nên dùng việc byte encoding giống hệt** làm contract liên provider cho mọi thuật toán. Chức năng xác minh chữ ký và thông số toán học phù hợp mới phản ánh hành vi khóa cần dùng.
8. `Arrays.fill(privateDer, 0)` xóa **mảng DER do chương trình sở hữu**, không xóa mọi bản sao bên trong `PKCS8EncodedKeySpec`, đối tượng `PrivateKey`, chữ ký hoặc provider. Ví dụ xuất private key chỉ để học; không ghi `privateDer`, modulus bí mật/CRT hay private PEM vào stdout.

**RSA 2048** là cỡ khóa ví dụ để chạy nhanh; cỡ khóa, padding/chế độ chữ ký (ví dụ RSA-PSS với `Signature`), quy tắc chứng chỉ và lifetime phải theo chính sách cụ thể. `KeyFactory` không quyết định liệu thuật toán ký hay cỡ khóa có đáp ứng yêu cầu bảo mật nghiệp vụ.

## 6. `RSAPublicKeySpec` có ích khi nào?

Giả sử một protocol đáng tin cung cấp hai số RSA `n` và `e` dưới dạng chuẩn đã kiểm tra. Có thể dựng một `RSAPublicKeySpec` từ chúng:

```java
// Minh họa trong phương thức đã khai báo throws GeneralSecurityException.
// n và e là BigInteger hợp lệ lấy từ input đã parse/validate.
RSAPublicKeySpec spec = new RSAPublicKeySpec(n, e);
PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(spec);
```

Đây là **đoạn minh họa, không phải chương trình độc lập**: `n`, `e` chưa được khai báo. Nó trình bày quan hệ `RSAPublicKeySpec → PublicKey`. Khi tích hợp thực tế:

- Kiểm tra định dạng và giới hạn độ dài serialized input trước khi tạo `BigInteger`; không để input cực lớn làm tăng tiêu thụ bộ nhớ/CPU.
- Kiểm tra modulus, exponent, cỡ khóa và tính hợp lệ theo chính sách. `KeyFactory.generatePublic` có thể từ chối key spec, nhưng provider không thay thế toàn bộ validation/trust policy của giao thức.
- Không nhầm `RSAPublicKeySpec` với chứng chỉ CA, khóa công khai gắn danh tính hoặc chữ ký hợp lệ. Chỉ dựng được key object không đồng nghĩa nguồn key là đáng tin.
- Không copy mẫu với `RSAPrivateKeySpec` rồi lưu `d`, `p`, `q` ra JSON/console. Thành phần RSA private phải được xử lý như vật liệu khóa bí mật.

Nếu input đã là DER SPKI tiêu chuẩn, **ưu tiên `X509EncodedKeySpec`** thay vì tự parse ASN.1 để lấy `n`/`e`. Điều này tránh sai sót về length, thuật toán/parameters và định dạng.

## 7. Ví dụ Java 17+: EC và Ed25519 cũng dùng KeyFactory

`X509EncodedKeySpec` và `PKCS8EncodedKeySpec` là **các lớp bao định dạng**, không chỉ dành cho RSA. Nhiều thuật toán public-key khác có thể dùng chúng nếu implementation JDK/provider hỗ trợ và các byte chứa đúng algorithm identifier.

Ví dụ sau tạo khóa EC với `secp256r1` và Ed25519; dựng lại rồi kiểm tra chữ ký. Nó cũng cố đưa EC public key vào RSA KeyFactory để minh họa **sai thuật toán**.

Lưu thành **`EcEdKeyConversionDemo.java`**:

```java
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;

public final class EcEdKeyConversionDemo {
    public static void main(String[] args) throws GeneralSecurityException {
        byte[] data =
                "Open standard key formats".getBytes(StandardCharsets.UTF_8);

        KeyPairGenerator ecGen = KeyPairGenerator.getInstance("EC");
        ecGen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair ecPair = ecGen.generateKeyPair();

        KeyFactory ecFactory = KeyFactory.getInstance("EC");
        PublicKey ecPublic = ecFactory.generatePublic(
                new X509EncodedKeySpec(ecPair.getPublic().getEncoded()));
        PrivateKey ecPrivate = ecFactory.generatePrivate(
                new PKCS8EncodedKeySpec(ecPair.getPrivate().getEncoded()));

        Signature ecSigner = Signature.getInstance("SHA256withECDSA");
        ecSigner.initSign(ecPrivate);
        ecSigner.update(data);
        byte[] ecSignature = ecSigner.sign();
        ecSigner.initVerify(ecPublic);
        ecSigner.update(data);
        System.out.println("EC round-trip verify: "
                + ecSigner.verify(ecSignature));

        KeyPairGenerator edGen = KeyPairGenerator.getInstance("Ed25519");
        KeyPair edPair = edGen.generateKeyPair();
        KeyFactory edFactory = KeyFactory.getInstance("Ed25519");
        PublicKey edPublic = edFactory.generatePublic(
                new X509EncodedKeySpec(edPair.getPublic().getEncoded()));
        PrivateKey edPrivate = edFactory.generatePrivate(
                new PKCS8EncodedKeySpec(edPair.getPrivate().getEncoded()));

        Signature edSigner = Signature.getInstance("Ed25519");
        edSigner.initSign(edPrivate);
        edSigner.update(data);
        byte[] edSignature = edSigner.sign();
        edSigner.initVerify(edPublic);
        edSigner.update(data);
        System.out.println("Ed25519 round-trip verify: "
                + edSigner.verify(edSignature));

        boolean rejected = false;
        try {
            KeyFactory.getInstance("RSA").generatePublic(
                    new X509EncodedKeySpec(
                            ecPair.getPublic().getEncoded()));
        } catch (InvalidKeySpecException expected) {
            rejected = true;
        }
        System.out.println("Wrong key algorithm rejected: " + rejected);
    }
}
```

```bash
javac -encoding UTF-8 EcEdKeyConversionDemo.java
java EcEdKeyConversionDemo
```

Stdout trên JDK 17+ thông dụng hỗ trợ EC/secp256r1 và Ed25519:

```text
EC round-trip verify: true
Ed25519 round-trip verify: true
Wrong key algorithm rejected: true
```

**Nhận xét:**

- EC dùng `KeyPairGenerator("EC")`, `ECGenParameterSpec("secp256r1")`, `KeyFactory("EC")` và chữ ký `SHA256withECDSA`. Tên curve là **tham số của thuật toán EC**, không phải tên của KeyFactory.
- Ed25519 có `KeyPairGenerator("Ed25519")`, `KeyFactory("Ed25519")` và `Signature("Ed25519")`. Không tự đổi Ed25519 thành ECDSA hoặc RSA bằng cách đổi string factory.
- Raw Ed25519 public key 32 byte **không phải** DER SPKI; raw seed/secret bytes cũng **không phải** PKCS #8 DER. Muốn nhập raw key cần KeySpec/API hoặc parser tương ứng, không ép byte[] vào `X509EncodedKeySpec`.
- `InvalidKeySpecException` ở case cuối cho thấy việc parse **đúng dạng SPKI** vẫn phải dùng **đúng thuật toán**. Nó khác `NoSuchAlgorithmException` khi Java không tìm được implementation của chính `KeyFactory`.
- Nếu ứng dụng dùng provider/HSM không hỗ trợ xuất khóa hoặc curve cụ thể, `getEncoded()` có thể không dùng được; không fallback sang một thuật toán khác khi chính sách yêu cầu Ed25519/EC cụ thể.

## 8. Ví dụ Java 17+: nhập public key PEM

API `KeyFactory` nhận **DER bytes**, không nhận trực tiếp chuỗi có `-----BEGIN PUBLIC KEY-----`. Ta cần kiểm tra envelope PEM, decode Base64, rồi dựng `X509EncodedKeySpec`.

Ví dụ dưới đây chỉ hỗ trợ **một public key SPKI PEM do chương trình tạo**. Nó không nhận chứng chỉ, private PEM, PKCS #1 RSA key, nhiều PEM block hoặc mọi cách ghi PEM trên thế giới. Parser production nên có giới hạn kích thước trước decode, chính sách input nghiêm ngặt và dùng thư viện tin cậy nếu phải hỗ trợ nhiều loại format.

Lưu thành **`PublicPemImportDemo.java`**:

```java
import java.security.*;
import java.security.spec.*;
import java.util.*;

public final class PublicPemImportDemo {
    private static String toPem(PublicKey key) {
        String base64 =
                Base64.getMimeEncoder(64, new byte[]{(byte)'\n'})
                        .encodeToString(key.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64
                + "\n-----END PUBLIC KEY-----\n";
    }

    private static PublicKey fromPem(String pem, String algorithm)
            throws GeneralSecurityException {
        String header = "-----BEGIN PUBLIC KEY-----";
        String footer = "-----END PUBLIC KEY-----";
        String value = pem.replace("\r\n", "\n").strip();
        if (!value.startsWith(header) || !value.endsWith(footer)) {
            throw new IllegalArgumentException("Expected PUBLIC KEY PEM");
        }
        String base64 = value.substring(
                header.length(), value.length() - footer.length())
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        return KeyFactory.getInstance(algorithm)
                .generatePublic(new X509EncodedKeySpec(der));
    }

    public static void main(String[] args) throws GeneralSecurityException {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        PublicKey key = g.generateKeyPair().getPublic();
        PublicKey restored = fromPem(toPem(key), "RSA");

        System.out.println("PEM import matches: "
                + Arrays.equals(key.getEncoded(), restored.getEncoded()));

        boolean rejected = false;
        try {
            fromPem("-----BEGIN CERTIFICATE-----\nAAA=\n"
                    + "-----END CERTIFICATE-----", "RSA");
        } catch (IllegalArgumentException ex) {
            rejected = true;
        }
        System.out.println("Certificate header rejected: " + rejected);
    }
}
```

```bash
javac -encoding UTF-8 PublicPemImportDemo.java
java PublicPemImportDemo
```

Stdout:

```text
PEM import matches: true
Certificate header rejected: true
```

**Vì sao không dùng `Base64.getMimeDecoder()` rồi bỏ qua toàn bộ ký tự ngoài alphabet?** Một decoder quá dễ dãi có thể nuốt những ký tự không mong đợi trong input; ở đây parser chủ động kiểm tra header/footer và bỏ **whitespace** trong phần thân trước khi dùng `Base64.getDecoder()`. Dù vậy, ví dụ chỉ minh họa một envelope SPKI hợp lệ và không thay thế parser PEM đã harden với toàn bộ quy tắc RFC 7468, giới hạn kích thước và kiểm tra nhiều block.

Nếu đầu vào là `-----BEGIN CERTIFICATE-----`, cách đúng là dùng `CertificateFactory.getInstance("X.509")` đọc certificate và lấy `certificate.getPublicKey()` sau đó thực hiện **xác thực certificate chain/trust** theo chính sách ứng dụng. Đừng chỉ đổi header từ `CERTIFICATE` thành `PUBLIC KEY`: byte DER vẫn là certificate, không phải SPKI.

## 9. Đặc tả khóa và định dạng phải khớp cả thuật toán, loại khóa và provider

Một quy trình nhập khóa đúng không chỉ có `Base64.decode` và `getInstance`. Nên xác định các điều kiện sau:

1. **Nguồn khóa là gì?** Có thể là file public SPKI, private PKCS #8, certificate, JWK, keystore alias hoặc HSM handle. Mỗi loại cần parser API phù hợp.
2. **Thuật toán khóa là gì?** RSA, EC, Ed25519, X25519... `KeyFactory` cần được chọn theo thuật toán; không dùng tên cipher/signature để thay thế.
3. **Loại khóa là gì?** Public key chỉ được đưa qua `generatePublic`, private key qua `generatePrivate`. Spec toán học cũng phải đúng loại.
4. **Encoding có đúng không?** DER SPKI, DER PKCS #8, PEM bao Base64, PKCS #1, SEC1 hoặc encrypted PKCS #8 không thể thay nhau chỉ bằng tên tệp.
5. **Provider có khả năng và cho phép không?** Khóa non-exportable không thể buộc `getEncoded()` trả bytes; `translateKey` có thể bị từ chối.
6. **Key có phù hợp chính sách bảo mật không?** Kiểm tra cỡ khóa, curve, algorithm parameters và usage; không chấp nhận key yếu vì parser nhận được.
7. **Nguồn key có đáng tin không?** Parse được một public key **không** xác thực chủ sở hữu. Cần certificate chain, pinned fingerprint đáng tin, trust store hoặc quá trình trao đổi khóa xác thực.
8. **Quyền xuất và lưu private key ra sao?** Không expose riêng tư do tiện debugging; sử dụng keystore/KMS/HSM và key lifecycle được duyệt.

### 9.1 KeyStore và CertificateFactory không thay thế cho KeyFactory

`KeyStore` giữ/tra cứu khóa và chứng chỉ theo alias với khả năng bảo vệ tương ứng; `CertificateFactory` đọc dạng chứng chỉ; `KeyFactory` chuyển đổi biểu diễn khóa. Có thể dùng chúng cùng nhau:

```text
Trusted KeyStore  --> Certificate --> getPublicKey() --> PublicKey
KeyStore alias    --> getKey(password) ----------------> PrivateKey/SecretKey
DER SPKI          --> X509EncodedKeySpec --> KeyFactory --> PublicKey
DER PKCS #8       --> PKCS8EncodedKeySpec --> KeyFactory --> PrivateKey
```

Việc `KeyStore.getKey` trả đối tượng `PrivateKey` **không đòi hỏi** tiếp tục export rồi gọi `KeyFactory.generatePrivate`. Nếu provider cho phép thao tác ký ngay bằng key handle, hãy dùng key trực tiếp với `Signature`. Export/import vô ích có thể phá vỡ boundary bảo mật của phần cứng.

### 9.2 Chứng chỉ và trust

Giả sử một đối tác gửi public key SPKI; bạn import bằng `KeyFactory.generatePublic` thành công. Điều đó chứng minh DER hợp lệ và provider hiểu thuật toán, **chưa chứng minh đối tác thật sự sở hữu khóa**. Một attacker cũng có thể tạo key RSA hợp lệ để lừa ứng dụng xác minh chữ ký do chính attacker tạo. Cần gắn key với danh tính qua kênh xác thực, certificate verification, pinning hoặc trust framework được xác định rõ.

Tránh coi `PublicKey.getEncoded()` như một chuỗi định danh tự thân cho mọi ngữ cảnh. Nếu dùng fingerprint, chuẩn hóa rõ loại encoding, thuật toán hash và nguồn/điểm tin cậy. Sự tương đương về toán học không nhất thiết kéo theo mọi provider xuất **mảng byte DER giống hệt nhau**.

## 10. Xử lý ngoại lệ đúng loại

| Lỗi | Điểm xuất hiện | Diễn giải và xử lý |
| --- | --- | --- |
| `NoSuchAlgorithmException` | `KeyFactory.getInstance("RSA" / "EC" / "Ed25519")` | Thuật toán không có implementation ở các provider khả dụng. Kiểm tra JDK/provider và chính sách deployment; không giả định cứ cài thư viện là tương thích. |
| `NoSuchProviderException` | Overload `getInstance(algorithm, "providerName")` | Tên provider chưa được đăng ký. Kiểm tra provider registration và cấu hình. |
| `InvalidKeySpecException` | `generatePublic`, `generatePrivate`, `getKeySpec` | Spec không phù hợp loại khóa, DER sai/không được hỗ trợ, thuật toán sai hoặc không thể xuất spec. |
| `InvalidKeyException` | `translateKey` | Provider/factory không thể chuyển key object được cung cấp. |
| `IllegalArgumentException` | Parser PEM / `Base64.getDecoder().decode` và một số validation API | Input không đúng envelope/base64 hoặc tham số ứng dụng sai. Không khẳng định tất cả API KeyFactory dùng cùng ngoại lệ. |
| `NullPointerException` | Đầu vào null tại các constructor/phương thức có yêu cầu non-null | Lỗi hợp đồng đầu vào; kiểm tra input trước khi vào crypto API. |
| Lỗi khi dùng khóa sau import | `Signature.initSign/initVerify` hoặc `Cipher.init` | Một key object parse được vẫn có thể không phù hợp cho operation, provider, scheme, key usage hoặc chính sách. |

Gợi ý phân lớp:

- **Khi startup:** nạp thuật toán/provider/khóa cấu hình; phát hiện thiếu capability hoặc key sai; fail fast và giữ `cause` trong log an toàn.
- **Khi nhận key từ bên ngoài:** giới hạn kích thước trước decode; phân biệt PEM syntax/Base64 với DER/spec và policy/chain validation. Trả thông báo lỗi input phù hợp, không trả stack trace nhạy cảm.
- **Khi khóa riêng bị khóa trong HSM:** không yêu cầu `getKeySpec(privateKey, RSAPrivateCrtKeySpec.class)` để “debug”. Dùng các operation được provider cho phép.
- **Khi định dạng bản ghi thay đổi:** lưu version + algorithm identifier, có migration rõ ràng; tránh thử ngẫu nhiên nhiều parser/thuật toán khiến lỗi khó truy vết.
- **Khi provider thay đổi:** đánh giá khả năng `getKeySpec`/`translateKey` từ môi trường thật; không suy diễn rằng mọi provider có thể chuyển khóa qua lại.

### 10.1 Một mẫu xử lý lỗi không nên dùng

```java
// Anti-pattern: làm mất nguyên nhân và có thể tạo null ở tầng sau.
try {
    return KeyFactory.getInstance("RSA")
            .generatePrivate(new PKCS8EncodedKeySpec(bytes));
} catch (Exception ex) {
    return null;
}
```

Đây là **mã minh họa bên trong một phương thức** (chưa khai báo biến `bytes` và kiểu trả về), **không phải chương trình Java độc lập**. Cách đúng là kiểm tra input, phân loại lỗi parse với lỗi cấu hình, giữ `cause` để điều tra và không trả `null` mơ hồ. Cũng không nên âm thầm chuyển sang key algorithm khác để “chạy được”.

## 11. Các giới hạn an toàn cần nhớ khi xuất, nhập khóa riêng

**Bản chất của khóa riêng:** Nếu `privateKey.getEncoded()` trả bytes PKCS #8, bất cứ ai đọc được bytes tương ứng có thể có khả năng sử dụng private key. Base64, PEM, `PKCS8EncodedKeySpec` hay một `byte[]` trong JVM không làm nó tự được mã hóa.

**Bộ nhớ:** `PKCS8EncodedKeySpec` sao chép bytes khi dựng và có thể sao chép khi gọi `getEncoded()`. Xóa một `byte[]` ban đầu không có nghĩa các bản sao trong heap, key object hay provider đã biến mất. Với yêu cầu cao, ưu tiên key non-extractable trong HSM/KMS, giới hạn lifetime và các cơ chế bảo vệ nền tảng.

**Khóa có thể được đóng gói:** `Key.getEncoded()` có thể trả `null`. Đây thường là dấu hiệu dạng khóa không được phép/không thể export, không nên chữa bằng reflection hoặc serialized object provider-specific.

**Khác biệt provider:** Một số provider nhận cùng DER nhưng đưa ra key object khác; một số chỉ hỗ trợ subset `KeySpec`; một số yêu cầu algorithm parameters cụ thể. Luôn thử đúng target runtime và xử lý `InvalidKeySpecException`/`InvalidKeyException` rõ ràng.

**Khả năng trust:** Chuyển đổi là biến đổi **biểu diễn**. Nó không kiểm tra chủ sở hữu key, không thu hồi chứng chỉ, không tự giải mã encrypted PKCS #8 và không xác nhận một key được cấp đúng mục đích.

## 12. Tự kiểm tra kiến thức qua tình huống

**Tình huống A.** Nhận chuỗi `-----BEGIN PUBLIC KEY-----` nhưng truyền nguyên cả chuỗi vào `new X509EncodedKeySpec(pem.getBytes())`. Vì sao sai?  
**Giải:** Spec cần **DER bytes**, không phải chữ Base64 kèm header/footer. Phải xác nhận loại PEM, decode Base64 lấy SPKI DER trước rồi mới gọi `generatePublic`.

**Tình huống B.** File có nhãn `-----BEGIN CERTIFICATE-----`. Có thể dùng `X509EncodedKeySpec` không?  
**Giải:** Không trực tiếp; đó là **certificate DER** chứ không phải SPKI DER. Dùng `CertificateFactory` đọc chứng chỉ, lấy `getPublicKey()`, và kiểm tra trust/chain theo chính sách.

**Tình huống C.** Một file `BEGIN RSA PRIVATE KEY` được đưa vào `PKCS8EncodedKeySpec`; xuất hiện `InvalidKeySpecException`. Có phải RSA bị thiếu?  
**Giải:** Chưa chắc. Đó thường là **PKCS #1**, khác PKCS #8. Cần chuyển đổi định dạng với parser/công cụ tin cậy trước khi dựng khóa. Chỉ khi `KeyFactory.getInstance("RSA")` thất bại mới nghi ngờ không có factory.

**Tình huống D.** Code dùng `KeyFactory.getInstance("SHA256withRSA")`.  
**Giải:** Sai loại tên thuật toán: `SHA256withRSA` dành cho `Signature`. Để dựng khóa RSA dùng `KeyFactory.getInstance("RSA")`.

**Tình huống E.** `PrivateKey` lấy từ HSM có `getEncoded() == null`. Làm sao “khắc phục”?  
**Giải:** Không cố export. Xác minh key handle và quyền sử dụng, thực hiện `Signature.initSign(privateKey)` hoặc thao tác cho phép qua provider. Không phải mọi private key đều có PKCS #8 để lấy ra.

**Tình huống F.** `KeyFactory.getKeySpec(publicKey, RSAPublicKeySpec.class)` lấy được `n`, `e`. Từ đó tạo private key được không?  
**Giải:** Không. Public components không chứa đủ thông tin của private key. `KeyFactory` không giải bài toán khôi phục private key từ public key.

**Tình huống G.** RSA private key import thành công, nhưng verify chữ ký từ đối tác vẫn không hợp lệ.  
**Giải:** Import đúng key **không** bảo đảm đó là key đúng đối tác, cũng không bảo đảm `Signature` đang dùng cùng scheme/dữ liệu/canonicalization. Kiểm tra danh tính public key, thuật toán ký, parameters và bytes được ký, không quy mọi lỗi vào `KeyFactory`.

**Tình huống H.** Muốn dựng khóa EC bằng `X509EncodedKeySpec` chứa Ed25519 public key. `KeyFactory.getInstance("EC")` có tự hiểu không?  
**Giải:** Không. Ed25519 là thuật toán có tên factory riêng; cần chọn đúng KeyFactory/provider, không coi mọi khóa elliptic curve là ECDSA/EC.

**Tình huống I.** Công cụ tạo `ENCRYPTED PRIVATE KEY`, nhưng Java gọi thẳng `generatePrivate(new PKCS8EncodedKeySpec(bytes))`.  
**Giải:** `PKCS8EncodedKeySpec` cần PKCS #8 private-key structure chưa được mã hóa ở lớp bao ngoài. Phải dùng quy trình giải mã encrypted PKCS #8 phù hợp trước, đồng thời bảo vệ password và memory.

## 13. Checklist áp dụng trong ứng dụng thật

1. **Biết format**: public SPKI/X.509, private PKCS #8, certificate, PKCS #1, SEC1 hay provider handle; có mã hóa lớp bao hay không.
2. **Chọn đúng thuật toán KeyFactory**: `RSA`, `EC`, `Ed25519`... dựa trên contract đã xác thực, không dựa vào chuỗi tự do từ người dùng.
3. **Dùng đúng spec**: `X509EncodedKeySpec` cho SPKI public; `PKCS8EncodedKeySpec` cho unencrypted PKCS #8 private; spec toán học khi thực sự cần.
4. **Kiểm tra trust**: nguồn public key, certificate chain/pinning, key usage và hạn sử dụng; parse thành công không chứng minh danh tính.
5. **Giới hạn input**: độ dài file, số block PEM, Base64/DER structure, cỡ khóa, thuật toán và parameters theo policy.
6. **Bảo vệ private key**: không log/commit/export tùy tiện; ưu tiên `KeyStore`, KMS/HSM và quyền hạn thích hợp.
7. **Kiểm thử với provider thật**: cả `getEncoded()` trả `null`, `getKeySpec`/ `translateKey` bị từ chối, thuật toán không có và key spec sai.
8. **Kiểm chứng chức năng**: ví dụ sign/verify cho keypair dự kiến; test mẫu hợp lệ/sai format/nhầm key, nhưng không sao chép key bí mật test vào production.
9. **Duy trì khả năng tương tác**: định nghĩa rõ DER/PEM, algorithm identifier, curve, key format và phiên bản khi trao đổi giữa Java/OpenSSL/đối tác.

## Tài liệu Java chính thức

- [Java SE 21 — `KeyFactory`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/KeyFactory.html): hướng chuyển Key/KeySpec, provider, `generatePublic`, `generatePrivate`, `getKeySpec`, `translateKey`.
- [Java SE 21 — `Key`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/Key.html): `getAlgorithm`, `getFormat` và `getEncoded`, bao gồm khả năng không xuất được encoding.
- [Java SE 21 — `X509EncodedKeySpec`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/spec/X509EncodedKeySpec.html): ASN.1 SubjectPublicKeyInfo public-key encoding.
- [Java SE 21 — `PKCS8EncodedKeySpec`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/spec/PKCS8EncodedKeySpec.html): private-key encoding theo PKCS #8.
- [Java SE 21 — `RSAPublicKeySpec`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/spec/RSAPublicKeySpec.html) và [`RSAPrivateCrtKeySpec`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/spec/RSAPrivateCrtKeySpec.html): thành phần toán học RSA.
- [Java SE 21 — `CertificateFactory`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/CertificateFactory.html) và [`KeyStore`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/KeyStore.html): phân biệt certificate parsing và key storage.
- [RFC 7468 — Textual Encodings of PKIX Structures](https://www.rfc-editor.org/rfc/rfc7468.html): envelope PEM cho public/private key/certificate.

**Ghi nhớ:** `KeyFactory` giúp **chuyển biểu diễn** của khóa bất đối xứng, với `KeySpec` là đầu vào hoặc đầu ra của chuyển đổi. Đúng định dạng **SPKI/PKCS #8**, đúng thuật toán và đúng provider là điều kiện để dựng khóa; **trust, bảo vệ private key và quyền sử dụng** vẫn phải được kiểm soát riêng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="public-key-cryptography-005">8.5 Nhập, xuất khóa công khai X.509 SubjectPublicKeyInfo và khóa riêng PKCS#8</a>

<details>
<summary>Xem chi tiết</summary>

# 8.5 Nhập, xuất khóa công khai X.509 SubjectPublicKeyInfo và khóa riêng PKCS#8

## 1. Mục tiêu và phạm vi

Trong Java Cryptography Architecture (JCA), một cặp khóa bất đối xứng có **khóa công khai** (`PublicKey`) và **khóa riêng** (`PrivateKey`). Khóa công khai có thể được phân phối theo quy trình tin cậy; khóa riêng phải được bảo vệ. Để lưu khóa hoặc trao đổi giữa Java, OpenSSL và các hệ thống khác, cần thống nhất **kiểu cấu trúc ASN.1**, **cách mã hóa DER/PEM** và **thuật toán**.

Java sử dụng hai dạng phổ biến:

- **X.509 SubjectPublicKeyInfo (SPKI):** định dạng chuẩn để biểu diễn *khóa công khai*, dùng với `X509EncodedKeySpec` và `KeyFactory.generatePublic(...)`.
- **PKCS#8 PrivateKeyInfo (hoặc dạng khóa riêng phù hợp mà provider chấp nhận):** định dạng chuẩn để biểu diễn *khóa riêng chưa mã hóa*, dùng với `PKCS8EncodedKeySpec` và `KeyFactory.generatePrivate(...)`.

Đây là bài về **mã hóa biểu diễn và nhập/xuất khóa**. Việc có DER hoặc PEM hợp lệ **không tự xác thực danh tính chủ khóa, độ tin cậy, quyền sử dụng, hay trạng thái thu hồi**. PEM chỉ là biểu diễn văn bản Base64 của các byte cấu trúc; `-----BEGIN PRIVATE KEY-----` **không đồng nghĩa khóa đã được mã hóa bảo vệ bằng password**.

Sau bài này, bạn cần có thể tạo khóa RSA/EC bằng `KeyPairGenerator`, xuất cấu trúc chuẩn bằng `getEncoded()`, khôi phục bằng `KeyFactory`, nhận diện đúng PEM header, tránh nhầm PKCS#1/SEC1/PKCS#12/certificate với SPKI hoặc PKCS#8, và biết cách xử lý `ENCRYPTED PRIVATE KEY`.

## 2. Ba tầng cần tách biệt: thuật toán, định dạng nhị phân, dạng văn bản

Một đối tượng Java như `RSAPublicKey` hoặc `ECPrivateKey` mang thông tin toán học của khóa và có thể có provider-specific implementation. Nếu khóa export được, `key.getEncoded()` trả **mảng byte** trong một định dạng chuẩn và `key.getFormat()` thường cho tên dạng chuẩn:

| Đối tượng Java | Định dạng biểu diễn mặc định khi có encoding | Đơn vị lưu trữ |
| --- | --- | --- |
| `PublicKey` RSA/EC/Ed25519 có thể export | X.509, thực chất cấu trúc `SubjectPublicKeyInfo` | DER bytes |
| `PrivateKey` RSA/EC/Ed25519 có thể export | PKCS#8, thực chất `PrivateKeyInfo` hoặc dạng được hỗ trợ | DER bytes |
| `SecretKey` như AES | `RAW` hoặc định dạng/provider khác | Khóa bí mật đối xứng; không áp dụng SPKI/PKCS#8 |
| Key handle trong HSM/PKCS#11 | Có thể `getEncoded() == null` | Vật liệu không được xuất khỏi provider |

**DER** (Distinguished Encoding Rules) là cách mã hóa nhị phân xác định của ASN.1. **PEM** là lớp bao text: header, Base64 của các byte DER, footer. Thay đổi xuống dòng Base64 không làm thay đổi khóa, miễn là decoder lấy lại đúng byte. Trong JCA, `X509EncodedKeySpec(byte[])` và `PKCS8EncodedKeySpec(byte[])` cần **byte DER**, không nhận trực tiếp `String` PEM hay byte của ký tự `-----BEGIN ...`.

Ví dụ về cấu trúc (sơ đồ khái niệm ASN.1, không phải mã nguồn Java):

```text
SubjectPublicKeyInfo ::= SEQUENCE {
    algorithm        AlgorithmIdentifier,
    subjectPublicKey BIT STRING
}

PrivateKeyInfo ::= SEQUENCE {
    version                   INTEGER,
    privateKeyAlgorithm       AlgorithmIdentifier,
    privateKey                OCTET STRING,
    attributes           [0]  IMPLICIT SET OF Attribute OPTIONAL
}
```

`AlgorithmIdentifier` thường chứa OID (object identifier) và có thể có tham số thuật toán. Với EC, tham số có thể xác định named curve; với RSA, algorithm identifier cho biết dạng RSA và tham số theo quy tắc tương ứng. Không được xem chỉ `BIT STRING` chứa điểm EC hoặc một cặp số RSA là *toàn bộ* SPKI: cấu trúc ngoài còn ghi algorithm và tham số để parser hiểu đúng.

Đối với khóa riêng, `privateKey` nằm trong OCTET STRING của PKCS#8; **bên trong** có thể lại chứa định dạng đặc thù thuật toán (ví dụ RSAPrivateKey hay ECPrivateKey). Vì thế “có byte RSA private key bên trong” **khác** “đang cầm một file PKCS#1 trần”. `KeyFactory.generatePrivate(new PKCS8EncodedKeySpec(bytes))` trông đợi **lớp vỏ PKCS#8 đầy đủ**.

RFC 5958 định nghĩa các cấu trúc đời mới liên quan `OneAsymmetricKey` và có thể chứa phần public key tùy chọn. Không mặc định rằng mọi biến thể tùy chọn, trường mở rộng hay mọi encoding từ provider khác đều được Java/provider đang chạy chấp nhận; hãy kiểm thử trên chính môi trường triển khai.

## 3. Nhận diện PEM header chính xác

Một nội dung PEM hợp lệ thường có các dòng:

```text
-----BEGIN PUBLIC KEY-----
<Base64 của DER SubjectPublicKeyInfo>
-----END PUBLIC KEY-----

-----BEGIN PRIVATE KEY-----
<Base64 của DER PKCS#8 PrivateKeyInfo chưa mã hóa>
-----END PRIVATE KEY-----
```

Dòng nằm giữa là **ký hiệu minh họa**, không phải Base64 hợp lệ để copy chạy. Bảng sau đặc biệt quan trọng khi nhận PEM từ OpenSSL, hệ thống Java cũ hoặc các thư viện ngoài:

| Header / loại dữ liệu | Bản chất | Import trực tiếp bằng spec nào? |
| --- | --- | --- |
| `BEGIN PUBLIC KEY` | X.509 SPKI, DER bọc Base64 | Giải mã PEM → `X509EncodedKeySpec` |
| `BEGIN PRIVATE KEY` | PKCS#8 không mã hóa, DER bọc Base64 | Giải mã PEM → `PKCS8EncodedKeySpec` |
| `BEGIN ENCRYPTED PRIVATE KEY` | PKCS#8 `EncryptedPrivateKeyInfo`, có PBE và ciphertext | Cần giải mã đúng thuật toán/password trước, rồi mới `PKCS8EncodedKeySpec` |
| `BEGIN RSA PUBLIC KEY` | RSA `RSAPublicKey` PKCS#1, không có lớp SPKI | **Không** đưa thẳng vào `X509EncodedKeySpec` |
| `BEGIN RSA PRIVATE KEY` | RSA `RSAPrivateKey` PKCS#1, không có lớp PKCS#8 | **Không** đưa thẳng vào `PKCS8EncodedKeySpec` |
| `BEGIN EC PRIVATE KEY` | EC `ECPrivateKey` SEC1 | Cần chuyển/bao thành PKCS#8 hoặc parser được thiết kế riêng |
| `BEGIN CERTIFICATE` | X.509 certificate bao gồm SPKI và metadata/chữ ký | `CertificateFactory` để parse certificate, sau đó `getPublicKey()` |
| `BEGIN CERTIFICATE REQUEST` | Certificate signing request (CSR) | Không phải raw SPKI hay PKCS#8 |
| `BEGIN OPENSSH PRIVATE KEY` | Định dạng OpenSSH riêng | Cần OpenSSH-compatible parser/converter; không phải PKCS#8 |
| `.p12`/`.pfx` | PKCS#12 container (keystore) | Dùng `KeyStore` và password/quyền thích hợp |

**Chữ “X.509” trong `X509EncodedKeySpec` không có nghĩa đối tượng này là certificate.** Certificate X.509 chứa subject/issuer, thời gian hiệu lực, extensions, chữ ký chứng thư và SPKI. `X509EncodedKeySpec` chỉ đại diện SPKI của khóa công khai; nó không có cơ chế xác thực certificate chain.

Một tệp `.der` hay `.pem` chỉ là gợi ý từ tên; phải xác định cấu trúc thực tế theo hợp đồng dữ liệu và parser. Dùng nhầm PEM header, sao chép thừa header/footer vào Base64, hoặc nhầm **PKCS#1** với **PKCS#8** là nguyên nhân phổ biến của `InvalidKeySpecException`.

## 4. Các API Java cần nhớ

| Công việc | API | Giá trị đầu vào/đầu ra |
| --- | --- | --- |
| Sinh cặp khóa | `KeyPairGenerator.getInstance("RSA")` / `"EC"` | `generateKeyPair()` → `PublicKey`, `PrivateKey` |
| Xuất dữ liệu DER | `key.getEncoded()` | `byte[]` hoặc `null` nếu không export được |
| Xem encoding | `key.getFormat()` | Thường `"X.509"` cho public, `"PKCS#8"` cho private khi export được |
| Dựng public spec | `new X509EncodedKeySpec(spkiDer)` | Các byte DER của **SPKI** |
| Dựng private spec | `new PKCS8EncodedKeySpec(privateKeyInfoDer)` | Các byte DER của **PKCS#8 chưa mã hóa** |
| Import public | `KeyFactory.getInstance("RSA").generatePublic(spec)` | `PublicKey` |
| Import private | `KeyFactory.getInstance("RSA").generatePrivate(spec)` | `PrivateKey` |
| Đóng gói PEM | `Base64.getMimeEncoder(64, new byte[]{'\n'})` | Dòng Base64 tối đa 64 ký tự |
| Mở PEM | Kiểm tra nhãn rồi `Base64.getDecoder().decode(body)` | Byte DER trước khi tạo `KeySpec` |
| Import X.509 certificate | `CertificateFactory.getInstance("X.509")` | `X509Certificate` rồi `getPublicKey()` |
| Nhập encrypted PKCS#8 | `EncryptedPrivateKeyInfo` và PBE `Cipher` | Sau khi giải mã: `PKCS8EncodedKeySpec` |

`KeyFactory` được chọn theo **thuật toán khóa** (`"RSA"`, `"EC"`, `"Ed25519"`...), không phải theo PEM header. Cả RSA và EC có thể dùng chung nhãn `BEGIN PUBLIC KEY`; bên trong `AlgorithmIdentifier` mới phân biệt. Nếu biết trước hợp đồng là RSA, dùng `KeyFactory.getInstance("RSA")`; nếu nguồn có thể là nhiều loại, phải xác định thuật toán tin cậy từ metadata/schema hoặc ASN.1 qua thư viện an toàn, chứ không đoán theo phần mở rộng file.

Mặc dù `X509EncodedKeySpec` và `PKCS8EncodedKeySpec` sao chép byte vào key spec, constructor **không phải trình kiểm tra đầy đủ thuật toán**; phân tích và validation chính xảy ra khi `KeyFactory.generatePublic/generatePrivate`. Import có thể ném `InvalidKeySpecException` do sai cấu trúc DER, thuật toán không đúng, tham số sai hoặc key data bất hợp lệ.

## 5. Chương trình chạy được: RSA và EC → SPKI/PKCS#8 → PEM → nhập lại

Chương trình Java 17+ dưới đây thực hiện **hai round-trip**:

1. Sinh RSA-2048 và EC với đường cong `secp256r1` (P-256).
2. Gọi `getEncoded()` để nhận **SPKI DER** của khóa công khai và **PKCS#8 DER** của khóa riêng.
3. Đóng gói từng DER thành PEM chuẩn `PUBLIC KEY` hoặc `PRIVATE KEY` bằng Base64 có xuống dòng 64 ký tự.
4. Kiểm tra header/footer, decode PEM trở lại DER và dùng `KeyFactory` + đúng `EncodedKeySpec` để nhập khóa.
5. Ký một thông điệp bằng private key nhập lại, xác minh bằng public key nhập lại.
6. Thử **đưa PKCS#8 private bytes vào `X509EncodedKeySpec`** để minh họa trường hợp sai cấu trúc bị từ chối.

Lưu thành **`KeyEncodingRoundTripDemo.java`**:

```java
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

public final class KeyEncodingRoundTripDemo {
    private static String toPem(String label, byte[] der) {
        String base64 = Base64.getMimeEncoder(
                64, new byte[] { '\n' }).encodeToString(der);
        return "-----BEGIN " + label + "-----\n"
                + base64 + "\n-----END " + label + "-----\n";
    }

    private static byte[] fromPem(String pem, String expectedLabel) {
        String begin = "-----BEGIN " + expectedLabel + "-----";
        String end = "-----END " + expectedLabel + "-----";
        String normalized = pem.replace("\r\n", "\n").trim();
        if (!normalized.startsWith(begin + "\n")
                || !normalized.endsWith("\n" + end)) {
            throw new IllegalArgumentException("Unexpected PEM type");
        }
        String body = normalized.substring(
                begin.length() + 1,
                normalized.length() - end.length() - 1);
        String base64 = body.replace("\n", "");
        return Base64.getDecoder().decode(base64);
    }

    private static void roundTrip(
            String name, String signatureAlgorithm, KeyPair pair)
            throws Exception {
        byte[] publicDer = pair.getPublic().getEncoded();
        byte[] privateDer = pair.getPrivate().getEncoded();
        if (publicDer == null || privateDer == null) {
            throw new IllegalStateException("Key is not exportable");
        }
        try {
            String publicPem = toPem("PUBLIC KEY", publicDer);
            String privatePem = toPem("PRIVATE KEY", privateDer);

            KeyFactory factory = KeyFactory.getInstance(name);
            PublicKey publicKey = factory.generatePublic(
                    new X509EncodedKeySpec(fromPem(publicPem, "PUBLIC KEY")));
            PrivateKey privateKey = factory.generatePrivate(
                    new PKCS8EncodedKeySpec(fromPem(privatePem, "PRIVATE KEY")));

            byte[] message = "signature test".getBytes(StandardCharsets.UTF_8);
            Signature signer = Signature.getInstance(signatureAlgorithm);
            signer.initSign(privateKey);
            signer.update(message);
            byte[] signature = signer.sign();
            Signature verifier = Signature.getInstance(signatureAlgorithm);
            verifier.initVerify(publicKey);
            verifier.update(message);

            System.out.println(name + " publicFormat=" + pair.getPublic().getFormat());
            System.out.println(name + " privateFormat=" + pair.getPrivate().getFormat());
            System.out.println(name + " publicRoundTrip="
                    + Arrays.equals(publicDer, publicKey.getEncoded()));
            System.out.println(name + " privateRoundTrip="
                    + Arrays.equals(privateDer, privateKey.getEncoded()));
            System.out.println(name + " signatureValid=" + verifier.verify(signature));
        } finally {
            Arrays.fill(publicDer, (byte) 0);
            Arrays.fill(privateDer, (byte) 0);
        }
    }

    public static void main(String[] args) throws Exception {
        KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
        rsa.initialize(2048);
        KeyPair rsaPair = rsa.generateKeyPair();
        roundTrip("RSA", "SHA256withRSA", rsaPair);

        KeyPairGenerator ec = KeyPairGenerator.getInstance("EC");
        ec.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair ecPair = ec.generateKeyPair();
        roundTrip("EC", "SHA256withECDSA", ecPair);

        KeyFactory factory = KeyFactory.getInstance("RSA");
        try {
            factory.generatePublic(new X509EncodedKeySpec(
                    rsaPair.getPrivate().getEncoded()));
            System.out.println("wrongContainerRejected=false");
        } catch (InvalidKeySpecException expected) {
            System.out.println("wrongContainerRejected=true");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 -encoding UTF-8 KeyEncodingRoundTripDemo.java
java KeyEncodingRoundTripDemo
```

Kết quả trên OpenJDK hỗ trợ RSA/EC phổ biến:

```text
RSA publicFormat=X.509
RSA privateFormat=PKCS#8
RSA publicRoundTrip=true
RSA privateRoundTrip=true
RSA signatureValid=true
EC publicFormat=X.509
EC privateFormat=PKCS#8
EC publicRoundTrip=true
EC privateRoundTrip=true
EC signatureValid=true
wrongContainerRejected=true
```

Vì mỗi lần chạy sinh khóa mới, **các byte DER và chữ ký thay đổi**, nhưng những phép kiểm tra vẫn cho kết quả trên khi provider hoạt động đúng. Ví dụ chọn RSA-2048 để dễ tương thích; lựa chọn thuật toán/kích thước khóa trong hệ thống mới phải theo yêu cầu bảo mật và thời hạn bảo vệ riêng.

### 5.1 Giải thích các điểm quan trọng của chương trình

**Đúng lớp container:** `toPem("PUBLIC KEY", spkiDer)` đi với `X509EncodedKeySpec`; `toPem("PRIVATE KEY", pkcs8Der)` đi với `PKCS8EncodedKeySpec`. Sai label bị từ chối bởi helper của ví dụ trước khi JCA parse. `wrongContainerRejected=true` đến từ `KeyFactory.generatePublic` không chấp nhận một private-key structure như SPKI.

**PEM helper có phạm vi có chủ đích:** `fromPem` xử lý một PEM block do chính `toPem` tạo ra, dùng newline LF hoặc CRLF, có header/footer chính xác và các dòng Base64 đơn giản. Đây là **parser giáo dục**, không xử lý mọi biến thể PEM thực tế (như nhiều PEM block trong một file, phần comment hoặc metadata lạ, kích thước không giới hạn). Trong sản phẩm, nên dùng parser PEM có kiểm soát về nhãn, số lượng block, encoding, giới hạn byte và validation phù hợp.

**Dấu hiệu encoding:** `getFormat()` thông thường trả `"X.509"` hoặc `"PKCS#8"` cho khóa phần mềm có thể export. Nhưng `getEncoded() == null` vẫn hợp lệ với key handle/provider không cho xuất, và `getFormat()` cũng có thể là `null`. Không lấy provider-specific bytes làm hợp đồng portable nếu không kiểm tra.

**Byte equality vs identity:** khi cùng provider round-trip DER, `Arrays.equals(originalDer, reconstructedKey.getEncoded())` thường cho `true`. Giữa các provider, hai khóa tương đương có thể được mã hóa lại khác nhau về chi tiết hợp lệ; việc *chữ ký tạo bằng private key nhập lại xác minh bằng public key nhập lại* là kiểm tra hành vi có ý nghĩa bổ sung. Đừng dùng phép so byte DER đơn thuần để chứng minh người gửi đáng tin hoặc public key thuộc đúng danh tính.

**Dữ liệu nhạy cảm:** chương trình đưa private bytes vào `byte[]` và PEM `String` trong bộ nhớ để minh họa. `Arrays.fill(privateDer, (byte)0)` chỉ xóa mảng đang giữ, **không thể xóa** mọi bản sao bên trong Java object, PEM `String` và provider. Trong sản phẩm, dùng key handle/kho khóa nếu có, tránh export private key vì mục đích kiểm tra đơn thuần, không in hoặc ghi lại PEM private key.

## 6. Nhập PEM/DER từ file thực

Các phép chuyển đổi cơ bản khi nhận dữ liệu từ file:

```text
public.pem
  -> kiểm tra đúng BEGIN PUBLIC KEY / END PUBLIC KEY
  -> decode Base64 thành DER SubjectPublicKeyInfo
  -> new X509EncodedKeySpec(der)
  -> KeyFactory.getInstance(algorithm).generatePublic(spec)

private.pem
  -> kiểm tra đúng BEGIN PRIVATE KEY / END PRIVATE KEY
  -> decode Base64 thành DER PKCS#8
  -> new PKCS8EncodedKeySpec(der)
  -> KeyFactory.getInstance(algorithm).generatePrivate(spec)

public.der / private.der
  -> dùng byte DER trực tiếp với KeySpec đúng kiểu
```

Ví dụ đọc file bằng thư viện Java chuẩn (đoạn mã **minh họa**, gọi bên trong phương thức đã khai báo exceptions và import):

```java
// public.der chứa đúng SPKI DER, private.der chứa đúng PKCS#8 DER.
byte[] spkiDer = java.nio.file.Files.readAllBytes(
        java.nio.file.Path.of("public.der"));
byte[] privateDer = java.nio.file.Files.readAllBytes(
        java.nio.file.Path.of("private.der"));
try {
    java.security.KeyFactory factory =
            java.security.KeyFactory.getInstance("RSA");

    java.security.PublicKey publicKey = factory.generatePublic(
            new java.security.spec.X509EncodedKeySpec(spkiDer));
    java.security.PrivateKey privateKey = factory.generatePrivate(
            new java.security.spec.PKCS8EncodedKeySpec(privateDer));
    // Dung publicKey/privateKey cho thao tac da duoc phep.
} finally {
    java.util.Arrays.fill(spkiDer, (byte) 0);
    java.util.Arrays.fill(privateDer, (byte) 0);
}
```

`Files.readAllBytes` chỉ thích hợp cho file khóa nhỏ và **đã giới hạn kích thước**. Với file do người dùng cung cấp, giới hạn bytes trước khi đọc, chống file cực lớn và chỉ parse **đúng định dạng**; không tự nhận chuỗi bất kỳ chứa `BEGIN` rồi dùng `replaceAll` xóa tùy tiện. Đối với file PEM, đọc bằng charset ASCII và dùng parser theo header/footer để lấy Base64 body, sau đó mới tạo `KeySpec`; **không** gọi `new PKCS8EncodedKeySpec(Files.readAllBytes("private.pem"))`.

Muốn ghi file DER hay PEM trong ứng dụng thật, phải thiết lập quyền đọc file/private key chặt chẽ **trước khi công bố/đặt vào thư mục có người khác truy cập**. POSIX permissions như `0600` không thể áp dụng y hệt trên Windows; thiết kế bảo vệ file phù hợp ACL, người dùng chạy process và môi trường. Thường nên quản lý private key bằng `KeyStore`, HSM hoặc KMS hơn là plain private.pem.

Nếu nhập **public key từ certificate** thì dùng `CertificateFactory`:

```java
// Đoạn minh họa: certDer là toàn bộ chứng thư DER (không phải SPKI).
java.security.cert.CertificateFactory certificates =
        java.security.cert.CertificateFactory.getInstance("X.509");
java.security.cert.X509Certificate certificate =
        (java.security.cert.X509Certificate) certificates.generateCertificate(
                new java.io.ByteArrayInputStream(certDer));
java.security.PublicKey publicKey = certificate.getPublicKey();
```

**Chỉ parse certificate không tự xác minh niềm tin.** Cần kiểm tra chain, trust anchor, thời gian, constraints, key usage, hostname và revocation theo giao thức sử dụng. `certificate.getPublicKey()` trả khóa công khai từ chứng thư; `certificate.getEncoded()` là encoding **toàn bộ certificate**, không phải SPKI.

## 7. PKCS#8 chưa mã hóa và PKCS#8 đã mã hóa là hai loại khác nhau

`BEGIN PRIVATE KEY` thể hiện **PKCS#8 PrivateKeyInfo chưa mã hóa**. Base64 không làm nó bí mật; bất kỳ ai đọc được file có thể lấy key material.

`BEGIN ENCRYPTED PRIVATE KEY` thể hiện **EncryptedPrivateKeyInfo** có cấu trúc đại ý:

```text
EncryptedPrivateKeyInfo ::= SEQUENCE {
    encryptionAlgorithm AlgorithmIdentifier,
    encryptedData       OCTET STRING
}
```

Để nhập dạng encrypted, phải nhận biết thuật toán PBE và các tham số của nó (ví dụ PBES2, salt, iteration count, cipher parameters), xác thực password theo yêu cầu và giải mã ra **DER PKCS#8 chưa mã hóa**; lúc ấy mới dùng `PKCS8EncodedKeySpec` với `KeyFactory`. Đưa trực tiếp `EncryptedPrivateKeyInfo.getEncoded()` vào `PKCS8EncodedKeySpec` sẽ sai định dạng.

Việc file private-key được bảo vệ bằng password **không đồng nghĩa** có cơ chế xác thực chống sửa hoặc password mạnh. Một số sơ đồ PBE lịch sử yếu, một số sơ đồ PBES2 dùng AES-CBC và phụ thuộc padding/DER validation; cần chính sách thuật toán, password đủ mạnh, kiểm tra định dạng và giới hạn tài nguyên. Password quá yếu có thể bị đoán offline ngay khi kẻ tấn công lấy được bản encrypted PKCS#8.

### 7.1 Chương trình chạy được: bảo vệ và khôi phục PKCS#8 bằng PBE/PBES2

Ví dụ Java 17+ này dùng provider phổ biến có `PBEWithHmacSHA256AndAES_256`, salt ngẫu nhiên và 210.000 vòng lặp **để minh họa API**, không coi đây là tham số bảo mật chuẩn cho mọi tình huống. Thuật toán/`AlgorithmParameters` thực tế phụ thuộc provider; ví dụ làm rõ lớp `PBES2` bên ngoài để `EncryptedPrivateKeyInfo` nhận ASN.1 AlgorithmIdentifier đúng.

Lưu thành **`EncryptedPkcs8Demo.java`**:

```java
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

public final class EncryptedPkcs8Demo {
    private static final String PBE = "PBEWithHmacSHA256AndAES_256";

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        byte[] original = pair.getPrivate().getEncoded();
        char[] password = "demo-password-only".toCharArray();
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        PBEKeySpec passwordSpec = new PBEKeySpec(password);

        try {
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(PBE);
            SecretKey pbeKey = keyFactory.generateSecret(passwordSpec);

            Cipher encrypt = Cipher.getInstance(PBE);
            encrypt.init(Cipher.ENCRYPT_MODE, pbeKey,
                    new PBEParameterSpec(salt, 210_000));
            byte[] ciphertext = encrypt.doFinal(original);

            AlgorithmParameters pbes2 = AlgorithmParameters.getInstance("PBES2");
            pbes2.init(encrypt.getParameters().getEncoded());
            EncryptedPrivateKeyInfo container =
                    new EncryptedPrivateKeyInfo(pbes2, ciphertext);
            EncryptedPrivateKeyInfo parsed =
                    new EncryptedPrivateKeyInfo(container.getEncoded());

            Cipher decrypt = Cipher.getInstance(PBE);
            decrypt.init(Cipher.DECRYPT_MODE, pbeKey,
                    parsed.getAlgParameters());
            PKCS8EncodedKeySpec plain = parsed.getKeySpec(decrypt);
            PrivateKey restored = KeyFactory.getInstance("RSA")
                    .generatePrivate(plain);

            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(restored);
            signer.update("signed data".getBytes(StandardCharsets.UTF_8));
            byte[] signature = signer.sign();

            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(pair.getPublic());
            verifier.update("signed data".getBytes(StandardCharsets.UTF_8));

            System.out.println("encryptedContainerParsed=true");
            System.out.println("restoredMatches="
                    + Arrays.equals(original, restored.getEncoded()));
            System.out.println("signatureValid=" + verifier.verify(signature));
            Arrays.fill(ciphertext, (byte) 0);
        } finally {
            passwordSpec.clearPassword();
            Arrays.fill(password, '\0');
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(original, (byte) 0);
        }
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 -encoding UTF-8 EncryptedPkcs8Demo.java
java EncryptedPkcs8Demo
```

```text
encryptedContainerParsed=true
restoredMatches=true
signatureValid=true
```

**Điểm học được:** `EncryptedPrivateKeyInfo` chứa metadata của sơ đồ bảo vệ và ciphertext. `getKeySpec(decrypt)` chỉ tạo `PKCS8EncodedKeySpec` **sau khi Cipher giải mã**. Đến `KeyFactory.generatePrivate` ta mới nhận `PrivateKey` có thể sử dụng. Không truyền private key trong phần hiển thị hoặc log; các biểu thức `Arrays.equals(...)` ở đây chỉ kiểm tra dữ liệu nội bộ.

**Giới hạn của ví dụ:** password cứng trong code chỉ là demo; sử dụng input mật khẩu an toàn và chính sách chống đoán thật. Phần `PBES2` + `PBEWithHmacSHA256AndAES_256` được kiểm thử với OpenJDK hỗ trợ các tên này; provider/phiên bản khác có thể cần bộ mã hóa PKCS#8 từ thư viện đã đánh giá hoặc `KeyStore`/KMS. `PBEWithHmacSHA256AndAES_256` mô tả KDF PRF HMAC-SHA256 và AES encryption; **không có nghĩa ciphertext có HMAC xác thực độc lập**. Với file khóa có độ nhạy cao, cân nhắc kho khóa được bảo vệ đúng chính sách, không tự thiết kế private-key file encryption protocol.

## 8. Chuyển đổi từ các định dạng phổ biến bằng công cụ chuyên dụng

Khi nhận key RSA ở `BEGIN RSA PRIVATE KEY` (PKCS#1) hoặc EC ở `BEGIN EC PRIVATE KEY` (SEC1), **không** thay tên header thành `BEGIN PRIVATE KEY` rồi giữ nguyên Base64: đổi header không đổi cấu trúc ASN.1. Phải tạo **lớp vỏ PKCS#8** bằng công cụ/thư viện hiểu thuật toán.

Ví dụ lệnh OpenSSL (lệnh minh họa cho môi trường có OpenSSL cài sẵn; hãy thay tên đường dẫn và bảo vệ tệp khóa riêng theo chính sách):

```bash
# Chuyển khóa riêng RSA PKCS#1 hoặc EC SEC1 sang PKCS#8 chưa mã hóa.
openssl pkcs8 -topk8 -nocrypt -in legacy-private.pem -out private-pkcs8.pem

# Xuất public SPKI từ một private key được OpenSSL nhận diện.
openssl pkey -in private-pkcs8.pem -pubout -out public-spki.pem

# Kiểm tra nội dung cấu trúc mà không in khóa riêng ra log:
openssl pkey -in private-pkcs8.pem -noout -text_pub
```

Lệnh chuyển sang `-nocrypt` tạo **private key chưa mã hóa**, rất nhạy cảm: dùng vị trí tạm được giới hạn quyền, xóa có kiểm soát theo mô hình lưu trữ và ưu tiên chuyển đổi ở môi trường an toàn. Tùy phiên bản OpenSSL, option `-text_pub` có thể không hỗ trợ trong tổ hợp trên; kiểm tra `openssl pkey -help` cho phiên bản cụ thể. Để tạo encrypted PKCS#8, cần công cụ cấu hình PBE an toàn và chính sách password phù hợp; việc dùng `-nocrypt` là **mở khóa thô**.

**Không** nghĩ rằng mọi lệnh xuất `openssl rsa -pubout` hay `openssl pkey -pubout` sẽ luôn tạo PEM có cùng nhãn trong mọi chế độ/phiên bản/flag; hãy **đọc header thực tế** và xác nhận cấu trúc SPKI. Các loại `RSA PUBLIC KEY` truyền thống là PKCS#1, khác `PUBLIC KEY` SPKI.

Nếu muốn trao đổi cặp khóa trong một container bảo vệ, Java `KeyStore` với PKCS12 có thể phù hợp hơn file PEM riêng rẽ; định dạng PKCS#12 bao gồm nhiều entry, certificate chain và cơ chế bảo vệ, **không phải byte PKCS#8** để đưa trực tiếp vào `PKCS8EncodedKeySpec`.

## 9. Những lỗi dễ gặp khi nhập hoặc xuất khóa

| Triệu chứng | Nguyên nhân thường gặp | Cách xử lý |
| --- | --- | --- |
| `InvalidKeySpecException`: cannot parse key | Đưa PKCS#1, SEC1, certificate, CSR hoặc sai DER vào X.509/PKCS#8 spec | Xác định cấu trúc thật; convert đúng định dạng trước |
| `InvalidKeySpecException`: algorithm mismatch | `KeyFactory("RSA")` nhận khóa EC/Ed25519, hoặc OID/params không phù hợp | Chọn algorithm từ giao thức/metadata đáng tin và provider hỗ trợ |
| `IllegalArgumentException` khi decode Base64 | Copy header/footer cùng body, ký tự lạ, thiếu Base64 | Parse PEM đúng label, decode body, áp dụng giới hạn kích thước |
| `getEncoded() == null` | Key không export được (HSM/token/provider) | Dùng `Key` trực tiếp trong provider, không tạo bytes giả |
| `NoSuchAlgorithmException` | Runtime/provider không có `RSA`/`EC`/PBE algorithm | Kiểm tra provider, JDK, tên thuật toán và tương thích triển khai |
| `InvalidKeyException` hoặc `SignatureException` khi ký | Khóa sai loại, sai chế độ, tham số signature không tương thích | Dùng đúng private/public, signature algorithm và tham số |
| `BadPaddingException` / lỗi giải mã encrypted PKCS#8 | Password, cipher parameters hoặc ciphertext sai | Từ chối khóa, xử lý lỗi thống nhất, không trả ra raw secret |
| Import certificate dưới dạng public DER | `certificate.getEncoded()` là toàn bộ X.509 certificate | Dùng `CertificateFactory` rồi `cert.getPublicKey()` |
| Nhập `BEGIN OPENSSH PRIVATE KEY` bằng PKCS#8 | OpenSSH container khác ASN.1 PKCS#8 | Convert hoặc parser đúng OpenSSH |
| Có private key DER nhưng không biết key type | PEM `PRIVATE KEY` là nhãn chung | Dựa vào ASN.1 algorithm identifier / contract, không đoán tên file |
| Chữ ký được tạo nhưng kiểm tra không hợp lệ | Key pair không cùng cặp, message khác, encoding khác hoặc signature params khác | Kiểm tra dữ liệu ký/verify byte-for-byte và đúng key |

**Không dùng việc parser đọc thành công làm bằng chứng tin cậy.** Với public key nhận từ người khác, cần quy trình gắn khóa với danh tính: certificate chain, fingerprint đã xác thực qua kênh khác, pinned public key, hoặc cơ chế định danh của giao thức. Với private key, cần kiểm tra người/ứng dụng được phép truy cập, backup, rotation, nhật ký truy cập và bảo vệ lưu trữ. Đọc file khóa riêng do người khác cung cấp luôn cần giới hạn kích thước, parse errors nhất quán và không tiết lộ dữ liệu nhạy cảm.

## 10. Những hiểu lầm quan trọng

- **“Có X.509 public key là đã có X.509 certificate.”** Không. SPKI chỉ là cấu trúc chứa public key và algorithm identifier; certificate còn có identity, issuer, validity và chữ ký.
- **“PKCS#8 là thuật toán khóa riêng.”** Không. Nó là **container** cho nhiều thuật toán; RSA/EC/Ed25519 là các thuật toán khác nhau.
- **“PEM đã mã hóa.”** Không mặc định. `BEGIN PRIVATE KEY` là unencrypted PKCS#8; `BEGIN ENCRYPTED PRIVATE KEY` mới nói tới lớp bảo vệ encrypted PKCS#8.
- **“Bỏ header Base64 rồi gọi KeyFactory với mọi loại private key.”** Không. Decode chỉ trả bytes của format gốc, không chuyển PKCS#1/SEC1 thành PKCS#8.
- **“Vì có Base64, tôi dùng String.getBytes() để tạo KeySpec.”** Không. Phải Base64 decode thành bytes DER gốc.
- **“Cứ RSA thì `KeyFactory("RSA")` có thể đọc certificate/PKCS#1 tùy ý.”** Không. Algorithm và container format đều phải phù hợp.
- **“Lưu PEM private key ở file ứng dụng là luôn an toàn.”** Không. Cần quản lý quyền truy cập, mã hóa bảo vệ khi phù hợp, KMS/HSM và chính sách vòng đời.
- **“getEncoded() luôn có bytes.”** Không. Provider có thể giữ key không thể export; `null` là trường hợp API hợp lệ.
- **“Đổi nhãn PKCS#1 thành PKCS#8 là đủ.”** Không. Phải chuyển cấu trúc ASN.1 bằng converter phù hợp.
- **“Import được public key là có thể tin và sử dụng ngay.”** Không. Nguồn khóa và danh tính cần được xác minh theo giao thức.

## 11. Câu hỏi tự kiểm tra

1. `X509EncodedKeySpec` chứa đối tượng ASN.1 nào? **`SubjectPublicKeyInfo`**, bao gồm algorithm identifier và public key bits.
2. `PKCS8EncodedKeySpec` thường dùng byte của dạng nào? **PKCS#8 PrivateKeyInfo chưa mã hóa**.
3. `BEGIN RSA PRIVATE KEY` là PKCS#8 không? **Không**; thường là PKCS#1 RSA private structure.
4. `BEGIN PUBLIC KEY` và `BEGIN CERTIFICATE` có thay thế trực tiếp cho nhau không? **Không**; một là SPKI, một là certificate đầy đủ.
5. PEM khác DER thế nào? **PEM là text armor Base64 + nhãn; DER là bytes ASN.1**.
6. Muốn import RSA public DER SPKI, hai API chính là gì? **`new X509EncodedKeySpec(der)` và `KeyFactory.getInstance("RSA").generatePublic(...)`**.
7. Nếu private key ở dạng `ENCRYPTED PRIVATE KEY`, có dùng `PKCS8EncodedKeySpec` ngay không? **Không**; phải parse/decrypt `EncryptedPrivateKeyInfo` trước.
8. `getEncoded() == null` có phải key hỏng không? **Không**; có thể là key không cho phép export.
9. Vì sao import public key thành công chưa chứng minh danh tính? Vì **key bytes không tự mang quan hệ tin cậy với người sở hữu**; cần certificate/chain/pinning/quy trình xác thực.
10. Một private key RSA PKCS#1 chuyển thành PKCS#8 bằng cách đổi PEM header có hợp lệ không? **Không**; cần chuyển cấu trúc DER thực sự.
11. Test `Arrays.equals(original.getEncoded(), restored.getEncoded())` thành công có đủ chứng minh mọi thứ? **Không**; còn cần test cryptographic operations, nguồn key, storage và provider.
12. Khi xuất PEM private key, điều cần kiểm soát đầu tiên là gì? **Không rò rỉ private key**, bảo vệ quyền file/memory, tránh log/backup ngoài ý muốn và quản lý vòng đời.

## Tài liệu chuẩn và API Java SE 21

- [X509EncodedKeySpec: SubjectPublicKeyInfo](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/spec/X509EncodedKeySpec.html)
- [PKCS8EncodedKeySpec](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/spec/PKCS8EncodedKeySpec.html)
- [KeyFactory](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/KeyFactory.html)
- [KeyPairGenerator](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/KeyPairGenerator.html)
- [Key.getEncoded/getFormat](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/Key.html)
- [EncryptedPrivateKeyInfo](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/EncryptedPrivateKeyInfo.html)
- [CertificateFactory](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/CertificateFactory.html)
- [Java KeyStore](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/KeyStore.html)
- [RFC 5280: X.509 Public Key Infrastructure, SubjectPublicKeyInfo](https://www.rfc-editor.org/rfc/rfc5280.html)
- [RFC 5958: Asymmetric Key Packages / PKCS#8](https://www.rfc-editor.org/rfc/rfc5958.html)
- [RFC 7468: PKIX Textual Encodings (PEM)](https://www.rfc-editor.org/rfc/rfc7468.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="public-key-cryptography-006">8.6 Phân biệt DER, PEM, khóa thô, khóa mã hóa và chứng thư</a>

<details>
<summary>Xem chi tiết</summary>

# 8.6 Phân biệt DER, PEM, khóa thô, khóa mã hóa và chứng thư

Trong Java Security/JCA, một file mang tên `server.pem`, `public.der`, `private.key` hoặc `certificate.crt` không tự cho biết nó chứa loại khóa nào, có mật khẩu bảo vệ hay không, và có được tin cậy hay không. **DER, PEM, raw key, encrypted key và certificate mô tả những chiều khác nhau của dữ liệu mật mã**. Nhầm các chiều này thường dẫn tới `InvalidKeySpecException`, lỗi TLS, lộ private key hoặc xác thực nhầm đối tác.

Một public key RSA có thể nằm trong cấu trúc ASN.1 **SubjectPublicKeyInfo (SPKI)** được mã hóa **DER**, rồi bọc text **PEM** với nhãn `-----BEGIN PUBLIC KEY-----`. Một chứng thư X.509 cũng thường được mã hóa DER và bọc PEM nhưng có nhãn `-----BEGIN CERTIFICATE-----`: bên trong có public key *cùng* các trường subject, issuer, validity, extension và chữ ký số. Một private key có thể là PKCS#8 không mã hóa (`BEGIN PRIVATE KEY`), PKCS#8 đã mã hóa (`BEGIN ENCRYPTED PRIVATE KEY`), hoặc dạng riêng của RSA/EC.

Bài này giải thích từ lớp byte tới lớp tin cậy, đề xuất cách chọn parser và thuật toán, có ví dụ Java 17+ biên dịch/chạy được, cùng checklist chống nhầm lẫn và thực hành bảo vệ khóa.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

1. Giải thích sự khác nhau giữa **vật liệu khóa**, **cấu trúc dữ liệu**, **binary encoding**, **text armor**, **mã hóa khi lưu** và **chứng thư/tin cậy**.
2. Nhận ra cấu trúc SPKI, PKCS#8, PKCS#1, SEC 1 và X.509 certificate từ **quy ước được công bố**, không chỉ từ đuôi file.
3. Dùng đúng các API Java: `PKCS8EncodedKeySpec`, `X509EncodedKeySpec`, `KeyFactory`, `EncryptedPrivateKeyInfo`, `CertificateFactory`, `X509Certificate` và `KeyStore`.
4. Biết vì sao **PEM không phải encryption**, **X509EncodedKeySpec không nhận certificate**, và **public key không chứng minh danh tính**.
5. Phân biệt **encryption key** (khóa dùng mã hóa) với **encrypted key** (khóa đã được mã hóa để bảo vệ) và **wrapped key**.
6. Thiết kế luồng nhập/xuất key và chứng thư có kiểm soát thuật toán, permission, trust anchor, hostname, rotation và lỗi parser.
7. Kiểm thử DER ↔ PEM và chứng minh cặp khóa khôi phục có thể ký/xác minh, đồng thời không nhầm đó với việc xác minh chứng thư.

## 2. Đừng so sánh những thứ thuộc các lớp khác nhau

Hãy đặt năm câu hỏi trước khi “chuyển đổi định dạng”:

| Câu hỏi | Ví dụ câu trả lời | Điều không suy ra |
| --- | --- | --- |
| **Đây là đối tượng gì?** | Public key, private key, symmetric key, CSR, certificate | File `.pem` không nói rõ đối tượng |
| **Cấu trúc của nó là gì?** | SPKI, PKCS#8 PrivateKeyInfo, EncryptedPrivateKeyInfo, X.509 Certificate | ASN.1 SEQUENCE không đủ để biết loại |
| **Byte được serialize thế nào?** | DER theo ASN.1; raw bytes theo một chuẩn thuật toán | DER không mặc định là certificate |
| **Có được bọc thành text/container?** | PEM/Base64, PKCS#12, JWK, OpenSSH | PEM không tự mã hóa bảo mật |
| **Ai có quyền và ai đáng tin?** | Key được giữ trong HSM; certificate có chain tới CA được phép | Parse đúng không đồng nghĩa trust |

Sơ đồ quan hệ:

```text
Vật liệu khóa hoặc thông tin chứng thư
  |
  +-- Raw bytes theo đặc tả thuật toán (nếu dùng raw representation)
  |
  +-- ASN.1 schema
        +-- SubjectPublicKeyInfo             [public key]
        +-- PrivateKeyInfo / OneAsymmetricKey [private key]
        +-- EncryptedPrivateKeyInfo           [private key đã được mã hóa]
        +-- Certificate                      [X.509 certificate]
        +-- RSAPublicKey / RSAPrivateKey     [RSA PKCS#1]
        +-- ECPrivateKey                     [SEC 1]
        |
        v
     DER binary bytes
        |
        +-- file nhị phân theo contract
        |
        +-- Base64 + PEM BEGIN/END label -> file PEM văn bản
```

Cùng một **certificate** có thể có hai file khác nhau, một file là DER bytes và file còn lại là PEM của chính những DER bytes đó. Chỉ đổi lớp armor **không** làm thay đổi tính hợp lệ hoặc độ tin cậy của certificate.

## 3. DER là gì?

**DER** (*Distinguished Encoding Rules*) là quy tắc chuẩn tắc mã hóa một giá trị ASN.1 thành byte theo ITU-T X.690. Nhiều cấu trúc khóa và chứng thư trong hệ sinh thái PKIX/PKCS được đặc tả bằng ASN.1; sau đó DER thể hiện cấu trúc đó ở mức binary.

ASN.1 mô tả **kiểu dữ liệu/schema**, DER mô tả **cách encoding**. Ví dụ, SPKI bao gồm một `AlgorithmIdentifier` và trường chứa public key. Cấu trúc PKCS#8 cũng thường có algorithm identifier nhưng chứa private key trong vị trí được quy định khác. Certificate X.509 lại có cấu trúc certificate hoàn chỉnh gồm phần dữ liệu được ký, thông tin thuật toán và chữ ký.

DER có quy tắc định dạng tag/length/value (TLV), có các ràng buộc canonical chặt hơn BER (*Basic Encoding Rules*). Tag byte `0x30` có thể báo một ASN.1 SEQUENCE, nhưng **không phải dấu hiệu đủ để phân biệt SPKI, private key hay certificate** vì cả ba loại có thể bắt đầu bằng SEQUENCE.

Những điều DER **không** làm:

- Không mã hóa dữ liệu để giữ bí mật: DER của private key PKCS#8 không mã hóa vẫn tiết lộ private key.
- Không cho biết public key thuộc ai hay issuer có đáng tin không.
- Không thay certificate chain validation, algorithm policy và authorization.
- Không biến raw key byte thành PKCS#8/SPKI bằng cách thêm vài byte tùy ý.
- Không cung cấp metadata tự mô tả đầy đủ nếu bạn thiếu ASN.1 schema cần dùng.

Không dùng `new String(derBytes, StandardCharsets.UTF_8)` để “đọc” DER. Binary DER không phải văn bản UTF-8. Khi cần debug, dùng công cụ ASN.1 và hex, bảo đảm không làm lộ private key.

**BER/CER/DER:** có nhiều ASN.1 encoding rules. Một parser có thể chấp nhận BER rộng hơn tùy implementation, nhưng khi giao thức quy định DER thì **đầu ra và validation cần tuân DER**, nhất là với dữ liệu được ký. Chuyển đổi đúng là parse theo schema và serialize theo chuẩn đích, không chỉnh sửa byte đầu theo cảm tính.

## 4. PEM là gì?

**PEM** trong ngữ cảnh key/certificate thường là văn bản chứa **Base64 của một payload nhị phân**, kèm các dòng BEGIN/END cho biết loại payload. Ví dụ dưới đây chỉ là **sơ đồ**, không phải private/public key hợp lệ để copy trực tiếp:

```text
-----BEGIN PUBLIC KEY-----
Base64 của một cấu trúc SPKI DER hợp lệ
-----END PUBLIC KEY-----
```

Để tạo file thực tế, phải lấy đúng SPKI DER bytes, encode Base64 đúng quy tắc và dùng label tương ứng. Ví dụ Java chạy được ở mục 11 thực hiện việc đó từ key đã sinh.

PEM thường sử dụng armor theo RFC 7468 và convention của từng hệ sinh thái. Một số định dạng có cấu trúc bọc khác như OpenSSH; **đừng mặc định mọi file có BEGIN/END đều là PKCS#8 hoặc SPKI**.

Đặc tính cần nhớ:

- PEM dễ dùng trong text config/email nhưng Base64 **không phải mã hóa bảo mật**; đọc được PEM không mã hóa là decode được private key.
- Nhãn có ý nghĩa: `BEGIN PRIVATE KEY` ≠ `BEGIN ENCRYPTED PRIVATE KEY` ≠ `BEGIN CERTIFICATE`.
- Một PEM file có thể chứa **nhiều block**, chẳng hạn certificate chain. Parser phải xử lý đúng số lượng, thứ tự và loại block mà protocol yêu cầu.
- Đuôi `.pem`, `.crt`, `.cer`, `.key` không đáng tin bằng **cấu trúc thực tế và hợp đồng định dạng**.
- LF, CRLF, khoảng trắng và PEM headers legacy có thể khác nhau; code học tập dạng split dòng đơn giản thường không đủ cho mọi loại input bên ngoài.
- Không đổi nhãn BEGIN/END để biến payload từ private key thành public key hay certificate. Nhãn sai không tự thay cấu trúc bên trong.

### So sánh DER và PEM

| Tiêu chí | DER | PEM |
| --- | --- | --- |
| Cách biểu diễn | Binary bytes theo quy tắc ASN.1 | Text armor chứa Base64 |
| Nội dung có thể là | SPKI, PKCS#8, X.509 certificate, v.v. | Những object đó sau khi bọc đúng label |
| Khả năng bảo mật mặc định | Không | Không |
| Dễ hiển thị trong terminal | Không | Có |
| Đọc bằng Java | Chọn parser đúng cấu trúc | Decode armor rồi chọn parser đúng cấu trúc |
| Chuyển đổi DER ↔ PEM | Giữ nguyên DER payload nếu bọc/giải bọc đúng | Không tự chuyển đổi loại key/certificate |

Nếu một certificate X.509 DER được bọc PEM, chính **DER payload** đại diện cho certificate; không nên hash cả dấu xuống dòng/armor rồi nhầm với certificate fingerprint theo DER.

## 5. Khóa thô (raw key): không có schema chung

**Raw key** là byte của vật liệu khóa theo biểu diễn của **một thuật toán cụ thể**, thay vì một cấu trúc có wrapper chung như SPKI/PKCS#8. Ý nghĩa của raw bytes thay đổi theo thuật toán, library và protocol:

| Trường hợp | Raw material thường được hiểu là gì? | Thông tin còn cần |
| --- | --- | --- |
| AES-256 | 32 byte symmetric key | Algorithm, usage, key ID, nguồn entropy, quyền |
| Ed25519 public | 32 byte public key encoding theo RFC 8032 | Algorithm/format, policy, trust binding |
| Ed25519 private | Có protocol dùng seed 32 byte; có library dùng cách biểu diễn khác | Định nghĩa seed vs private encoding; không tự coi là PKCS#8 |
| X25519 | 32 byte coordinate/secret scalar theo RFC 7748 | Public/private, endianness, clamping/handling theo API |
| EC P-256 public | Điểm trên đường cong ở dạng compressed/uncompressed | Curve/domain parameters và kiểm point |
| RSA public | Hai số nguyên `n` và `e`, hoặc một cấu trúc thuật toán-cụ-thể `RSAPublicKey` | Modulus/exponent, padding/signature scheme, key policy |
| RSA private | Các thành phần RSA, đôi khi cấu trúc `RSAPrivateKey` PKCS#1 | Kiểm nhất quán tham số, CRT, độ dài, bảo vệ |

**Cùng 32 byte không có nghĩa hai key có thể dùng thay nhau.** Raw AES-256 key và raw Ed25519 public key có thể cùng kích thước nhưng là hai đối tượng với phép toán hoàn toàn khác nhau.

Raw key không bắt buộc kém an toàn, nhưng rất dễ bị hiểu nhầm nếu truyền không có algorithm/version/curve/usage. Chỉ dùng raw representation khi một protocol có đặc tả chính xác và implement parser theo chuẩn đó.

### Raw key khác EncodedKeySpec

`PKCS8EncodedKeySpec` đòi **cấu trúc private key PKCS#8**, không nhận trực tiếp mọi raw secret scalar/seed. `X509EncodedKeySpec` đòi **cấu trúc SPKI**, không nhận trực tiếp mọi raw public point. Nếu đưa 32 byte raw Ed25519 vào `new X509EncodedKeySpec(rawBytes)`, việc Java constructor tạo object spec không chứng minh bytes hợp lệ; `KeyFactory.generatePublic` thường từ chối vì thiếu đúng wrapper.

Với AES, việc dùng `new SecretKeySpec(rawAesBytes, "AES")` là một mô hình khác: nó mô tả **symmetric key** từ byte đã có. Điều này không chứng minh key có entropy đủ hoặc được cấp phát quyền. Đối với RSA/EC từ thành phần thô, Java có những spec phù hợp từng thuật toán như `RSAPublicKeySpec`, `RSAPrivateCrtKeySpec`, `ECPublicKeySpec`, `ECPrivateKeySpec`; phải có đủ thông số và validation.

## 6. SPKI, PKCS#8, PKCS#1 và SEC 1: đúng loại cấu trúc mới dùng đúng API

Bảng nhận dạng quan trọng:

| Cấu trúc | Đối tượng | PEM label phổ biến | Java 17 |
| --- | --- | --- | --- |
| **SubjectPublicKeyInfo (SPKI)** | Public key với AlgorithmIdentifier | `PUBLIC KEY` | `X509EncodedKeySpec` rồi `KeyFactory.generatePublic` |
| **PKCS#8 PrivateKeyInfo / OneAsymmetricKey** | Private key cùng thông tin thuật toán | `PRIVATE KEY` | `PKCS8EncodedKeySpec` rồi `KeyFactory.generatePrivate` |
| **PKCS#8 EncryptedPrivateKeyInfo** | Private key đã mã hóa cùng tham số mã hóa | `ENCRYPTED PRIVATE KEY` | Giải mã bằng provider thích hợp trước, rồi parse private key |
| **PKCS#1 RSAPublicKey** | RSA public modulus + exponent | `RSA PUBLIC KEY` | Không phải SPKI, cần parse/convert đúng |
| **PKCS#1 RSAPrivateKey** | RSA private key theo cấu trúc thuật toán | `RSA PRIVATE KEY` | Không phải PKCS#8, cần parse/convert đúng |
| **SEC 1 ECPrivateKey** | EC private key kiểu truyền thống | `EC PRIVATE KEY` | Không phải PKCS#8, cần parse/convert đúng |
| **X.509 Certificate** | Public key + chủ thể/issuer/validity/extension/signature | `CERTIFICATE` | `CertificateFactory.getInstance("X.509")` |

**Chú ý tên gây nhầm:**

- `X509EncodedKeySpec` không phải parser certificate. Nó đại diện cho **SPKI public key encoding**. Tên “X.509” phản ánh xuất xứ cú pháp public-key information, không có nghĩa tham số đầu vào là nguyên chứng thư X.509.
- `PKCS8EncodedKeySpec` dùng với **private key không mã hóa**. `BEGIN ENCRYPTED PRIVATE KEY` là một container khác, phải được giải mã theo đúng format/thuật toán/password trước.
- `RSA PRIVATE KEY` có thể là PKCS#1 legacy chưa mã hóa, hoặc mang lớp PEM encryption legacy tùy headers; không vì nhãn chứa “PRIVATE” mà mặc định decode bằng PKCS#8.
- `PUBLIC KEY` theo SPKI khác `RSA PUBLIC KEY` theo PKCS#1. Cả hai có thể mô tả cùng toán học RSA public key nhưng **byte và schema không giống nhau**.

### Liên hệ với Ed25519, X25519 và EC

RFC 8410 mô tả algorithm identifiers/encoding cho các key thuật toán Ed25519/Ed448/X25519/X448 trong SPKI/PKCS#8. Nếu protocol đưa **raw 32-byte Ed25519 public key**, cần conversion đúng tiêu chuẩn, không thêm “prefix cố định” tùy tiện. Dữ liệu PKCS#8 Ed25519 private cũng không đơn giản là 32-byte seed được Base64 bọc nhãn `PRIVATE KEY`.

Với EC, một encoded point thiếu domain parameters có thể dẫn tới nhầm curve. Phải xác định chuẩn representation, public-key validation và thuật toán, không giả định mọi EC point đều thuộc P-256.

## 7. “Khóa mã hóa” có hai nghĩa khác nhau

Cụm “khóa mã hóa” đôi khi có nghĩa **encryption key** (key dùng để mã hóa dữ liệu), nhưng trong thảo luận file format có thể có nghĩa **encrypted key** (bản thân key được mã hóa để bảo vệ khi lưu/truyền).

1. **Encryption key / decryption key**: key dùng trong thuật toán, ví dụ AES key hoặc RSA public/private key cho encryption/decryption theo scheme cho phép. Key có thể lưu dạng raw, DER, PEM hay trong HSM tùy loại.
2. **Encrypted private key**: blob chứa private key được mã hóa, chẳng hạn PKCS#8 `EncryptedPrivateKeyInfo`, mở được bằng mật khẩu hoặc cơ chế khóa tương ứng theo thuật toán và policy. Không thể dùng trực tiếp ciphertext của key như object `PrivateKey`.
3. **Wrapped key**: key được bao bọc bằng *key encryption/wrapping key (KEK)* hoặc KMS wrapping operation, ví dụ AES Key Wrap hoặc envelope encryption. Không mặc định là PKCS#8 hay dùng cùng PEM label.
4. **Certificate**: chứng thư chứa **public key**, không chứa private key; nó không “mã hóa private key” và không thay thế việc bảo vệ key thật.

Hãy luôn viết chính xác “key dùng để mã hóa”, “key đã được mã hóa” hay “key được bọc (wrapped)” trong tài liệu kỹ thuật để tránh đặt sai API/quyền truy cập.

## 8. PKCS#8 được mã hóa: không đồng nghĩa với PEM

Một private key PKCS#8 chưa mã hóa thường có dạng:

```text
PrivateKeyInfo
  -> version + AlgorithmIdentifier + privateKey data
  -> DER bytes
  -> optional PEM armor "PRIVATE KEY"
```

Khi bảo vệ private key tại rest bằng password/secret:

```text
PrivateKeyInfo DER
  -> encryption/KDF/protection algorithm + parameters
  -> EncryptedPrivateKeyInfo (algorithmIdentifier, encryptedData)
  -> DER bytes
  -> optional PEM armor "ENCRYPTED PRIVATE KEY"
```

**Bảo mật nằm ở thuật toán và cách quản lý password/key**, không nằm ở việc thêm PEM armor. Mật khẩu yếu có thể bị brute-force offline nếu blob rơi vào tay attacker. Mặt khác, encrypted key không tự cho biết người được phép sử dụng hay algorithm có an toàn đủ không.

### Những yếu tố cần hiểu khi mở encrypted key

- **KDF và tham số:** salt, iteration count, thuật toán PRF hoặc profile khác; lựa chọn phù hợp workload và khuyến nghị hiện hành.
- **Thuật toán bảo vệ:** lựa chọn mạnh, không dùng profile legacy yếu không cần thiết. Cần biết liệu format có cung cấp authenticated encryption hay chỉ encryption+padding.
- **Tương thích provider:** Java có `EncryptedPrivateKeyInfo`, `Cipher` và `SecretKeyFactory`, nhưng các cách encoding PBES2/algorithm names/parameters giữa OpenSSL và JDK/provider có thể khác; không giả định mọi file OpenSSL đều chạy với một lệnh JCA generic.
- **Nguồn password:** secure input, secret manager; tránh hard-code, CLI history, log. Với KMS/HSM có thể không bao giờ có bản sao raw private key trong ứng dụng.
- **Validation:** sau decrypt, phải kiểm private key structure, algorithm và policy. Không xem byte giải mã được là key an toàn bất kể format.
- **Legacy PEM encryption:** một số `BEGIN RSA PRIVATE KEY` có PEM headers `Proc-Type: 4,ENCRYPTED`, `DEK-Info`. Đó không đồng nghĩa `BEGIN ENCRYPTED PRIVATE KEY` dạng PKCS#8. Dùng parser/migration hiểu chính xác định dạng.

### Ví dụ thao tác OpenSSL (minh họa CLI, không phải Java)

Giả sử bạn có một private key hợp lệ trong file `private.pem` và OpenSSL thích hợp:

```bash
# Trích public key dạng SPKI PEM từ private key
openssl pkey -in private.pem -pubout -out public.pem

# Mã hóa private key thành encrypted PKCS#8; công cụ yêu cầu nhập password
openssl pkcs8 -topk8 -in private.pem -out private-encrypted.pem \
  -v2 aes-256-cbc -v2prf hmacWithSHA256 -iter 200000

# Đọc metadata certificate, không cần private key
openssl x509 -in certificate.pem -noout -subject -issuer -dates
```

Các lệnh yêu cầu file đầu vào phù hợp, quyền truy cập và policy của hệ thống. Con số vòng lặp chỉ minh họa, không phải khuyến nghị dùng mãi cho mọi tải và threat model. AES-CBC trong cấu hình PBE này không đồng nghĩa một envelope authenticated AEAD hoàn chỉnh. Không ghi password trong argv hoặc commit encrypted private key với password yếu chỉ vì nghĩ “đã mã hóa là an toàn”.

## 9. Certificate X.509: một public key với các khẳng định có chữ ký

X.509 certificate chứa public key dưới cấu trúc SPKI *và* nhiều trường khẳng định về chủ thể, issuer, hiệu lực, usage. Theo cấu trúc tổng quát, certificate bao gồm phần được ký `tbsCertificate`, thông tin signature algorithm và signature bits.

| Trường | Ý nghĩa |
| --- | --- |
| Serial number, version | Số serial do issuer quản lý và version cấu trúc |
| Subject, Issuer | Chủ thể được cấp và thực thể cấp/ký |
| NotBefore / NotAfter | Khoảng hiệu lực của certificate |
| SubjectPublicKeyInfo | Public key cùng algorithm identifier |
| Subject Alternative Name (SAN) | DNS/IP/định danh khác, đặc biệt quan trọng với TLS hostname |
| Key Usage / Extended Key Usage | Mục đích sử dụng chứng thư và key được cho phép |
| Basic Constraints | Có phải CA, giới hạn path length |
| Chữ ký của issuer | Cho phép xác minh dữ liệu certificate không bị sửa với public key issuer theo trust model |

**Certificate không mang private key** tương ứng. Private key được giữ ở server, HSM/KMS hoặc keystore bảo vệ; client nhận certificate/public key phục vụ quá trình xác thực theo protocol.

### Parse được không phải trust

Ba câu hỏi cần tách biệt:

1. **Parse hợp lệ:** `CertificateFactory` dựng object `X509Certificate` từ DER/PEM certificate.
2. **Chữ ký certificate có đúng với một issuer public key cụ thể?** `certificate.verify(issuerPublicKey)` có thể giúp kiểm chữ ký với key được chỉ định, nhưng tự nó không chứng minh issuer key đáng tin.
3. **Có thể tin chủ thể để thực hiện tác vụ này?** Cần **PKIX path validation tới trust anchor được phép**, hiệu lực, algorithm constraints, basic constraints, key usage/EKU, revocation theo policy và với HTTPS còn **hostname/SAN matching**.

Chứng thư self-signed có thể tự verify chữ ký của chính nó; điều đó **không** tự đưa nó vào truststore. Chứng thư hết hạn vẫn parse được. Certificate có tên trong Subject rất giống tên công ty không tự tạo bằng chứng danh tính.

Java dùng `CertificateFactory`, `CertPathValidator`, `PKIXParameters` và các thành phần JSSE/`TrustManager` phù hợp để xác minh trust. Với HTTPS bình thường, ưu tiên client/TLS stack đáng tin với hostname verification bật, không tự xây lại toàn bộ PKIX logic từ vài lời gọi `verify`.

### CSR khác certificate

`BEGIN CERTIFICATE REQUEST` là CSR/PKCS#10: yêu cầu CA cấp chứng thư, có public key và chữ ký chứng minh possession của private key. CSR **chưa phải certificate đã được CA cấp**. Certificate từ CA có issuer, time validity, signature/usage semantics riêng. Không dùng CSR thay certificate để xác thực TLS peer.

## 10. PKCS#12/PFX và các format khác

Nhiều hệ thống lưu private key + certificate chain trong **PKCS#12** (`.p12`/`.pfx`), một **container** có thể chứa nhiều entry, password protection và metadata. Nó không phải một file DER SPKI hoặc một PEM certificate đơn thuần. Trong Java, `KeyStore.getInstance("PKCS12")` cho phép đọc các entry khi có quyền/mật khẩu phù hợp.

Các format khác cần nhận diện riêng:

- **OpenSSH**: `BEGIN OPENSSH PRIVATE KEY` và public-key dòng `ssh-ed25519 AAAA...` thuộc định dạng SSH, không mặc định là PKCS#8/SPKI PEM.
- **JWK/JWKS**: key biểu diễn bằng JSON/JSON Web Key với `kty`, `crv`, `n`, `e` hoặc `x` tùy loại; giá trị Base64url không biến JSON thành DER.
- **PKCS#1 / SEC 1 legacy**: key RSA/EC truyền thống, có thể convert sang PKCS#8 bằng công cụ hiểu schema; chỉ đổi `BEGIN` là sai.
- **PEM certificate chain**: nhiều block `CERTIFICATE` trong cùng file, phải xác định leaf/intermediate/anchor và policy. Không tự xem root do peer gửi là trust anchor.

Với PKCS#12, private key vẫn cần được bảo vệ cả khi container được password-protected: secret có thể bị sao chép và tấn công offline; certificate chain trong container cũng không tự là chain đáng tin.

## 11. Ví dụ Java 17+ hoàn chỉnh: SPKI/PKCS#8 DER ↔ PEM

Chương trình sau sinh một cặp **RSA** dùng cho minh họa, xuất private key theo **PKCS#8 DER** và public key theo **SPKI DER**, tạo PEM, đọc lại bằng `KeyFactory` và kiểm chữ ký. Nó cố ý **không tạo certificate**: key ASN.1 SPKI không phải X.509 certificate.

Lưu file đúng tên **`KeyFormatsDemo.java`**:

```java
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

public class KeyFormatsDemo {
    static String toPem(String label, byte[] der) {
        String b64 = Base64.getMimeEncoder(64, new byte[] {'\n'})
                .encodeToString(der);
        return "-----BEGIN " + label + "-----\n"
                + b64 + "\n-----END " + label + "-----\n";
    }

    // A deliberately narrow parser for one PEM block produced by toPem.
    static byte[] fromPem(String pem, String expectedLabel) {
        String begin = "-----BEGIN " + expectedLabel + "-----";
        String end = "-----END " + expectedLabel + "-----";
        String[] lines = pem.replace("\r\n", "\n").split("\n");
        if (lines.length < 3 || !begin.equals(lines[0])
                || !end.equals(lines[lines.length - 1])) {
            throw new IllegalArgumentException("Wrong PEM label or boundaries");
        }
        StringBuilder base64 = new StringBuilder();
        for (int i = 1; i < lines.length - 1; i++) {
            if (!lines[i].matches("[A-Za-z0-9+/=]+")) {
                throw new IllegalArgumentException("Invalid Base64 line");
            }
            base64.append(lines[i]);
        }
        return Base64.getDecoder().decode(base64.toString());
    }

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();

        byte[] privateDer = pair.getPrivate().getEncoded();
        byte[] publicDer = pair.getPublic().getEncoded();
        String privatePem = toPem("PRIVATE KEY", privateDer);
        String publicPem = toPem("PUBLIC KEY", publicDer);

        KeyFactory factory = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = factory.generatePrivate(
                new PKCS8EncodedKeySpec(fromPem(privatePem, "PRIVATE KEY")));
        PublicKey publicKey = factory.generatePublic(
                new X509EncodedKeySpec(fromPem(publicPem, "PUBLIC KEY")));

        Signature signature = Signature.getInstance("SHA256withRSA");
        byte[] message = "Format != trust".getBytes(StandardCharsets.UTF_8);
        signature.initSign(privateKey);
        signature.update(message);
        byte[] signed = signature.sign();

        signature.initVerify(publicKey);
        signature.update(message);
        boolean verified = signature.verify(signed);

        System.out.println("Private format: " + pair.getPrivate().getFormat());
        System.out.println("Public format: " + pair.getPublic().getFormat());
        System.out.println("Private label: " + privatePem.split("\n")[0]);
        System.out.println("Public label: " + publicPem.split("\n")[0]);
        System.out.println("Private DER SEQUENCE tag: " + (privateDer[0] == 0x30));
        System.out.println("Public DER SEQUENCE tag: " + (publicDer[0] == 0x30));
        System.out.println("Private DER round trip: "
                + Arrays.equals(privateDer, privateKey.getEncoded()));
        System.out.println("Public DER round trip: "
                + Arrays.equals(publicDer, publicKey.getEncoded()));
        System.out.println("Signature valid: " + verified);
        try {
            fromPem(publicPem, "CERTIFICATE");
            System.out.println("Certificate label accepted: true");
        } catch (IllegalArgumentException e) {
            System.out.println("Certificate label accepted: false");
        }
    }
}
```

Biên dịch/chạy với **JDK 17 trở lên**:

```bash
javac --release 17 KeyFormatsDemo.java
java KeyFormatsDemo
```

**Stdout đã kiểm chứng:**

```text
Private format: PKCS#8
Public format: X.509
Private label: -----BEGIN PRIVATE KEY-----
Public label: -----BEGIN PUBLIC KEY-----
Private DER SEQUENCE tag: true
Public DER SEQUENCE tag: true
Private DER round trip: true
Public DER round trip: true
Signature valid: true
Certificate label accepted: false
```

### Phân tích ví dụ

1. `getEncoded()` của khóa software exportable được provider tạo trong demo trả bytes theo `getFormat()` tương ứng. `PKCS#8` là private encoding, `X.509` trong `PublicKey.getFormat()` là **SPKI public-key encoding**, không phải certificate.
2. `toPem` Base64-encode DER rồi thêm label đúng. Không có password-based encryption trong bước này. File private PEM tạo được vẫn là **secret cần bảo vệ**.
3. `fromPem` được viết *cố ý giới hạn*: một block duy nhất, label đúng, Base64 line được kiểm. Đây **không phải parser PEM đầy đủ** cho file từ đối tác; production dùng thư viện/logic RFC 7468 phù hợp, giới hạn kích thước/headers và số block.
4. `KeyFactory.getInstance("RSA")` parse hai cấu trúc đúng bằng `PKCS8EncodedKeySpec` và `X509EncodedKeySpec`. Nếu thay private DER bằng encrypted PKCS#8 hoặc PKCS#1 raw, cách này **không áp dụng trực tiếp**.
5. `Signature` chỉ xác nhận cặp key vừa khôi phục ký/xác minh được trên đúng byte message. Nó **không** chứng minh bên thứ ba hoặc một certificate chain đáng tin.
6. Hai DER bytes trong demo bắt đầu bằng `0x30` vì cùng là ASN.1 SEQUENCE, nhưng là **hai schema khác nhau**. Không dùng tag đầu làm parser-type detector.
7. `PUBLIC KEY` PEM không qua được parser kỳ vọng nhãn `CERTIFICATE`; nhãn đúng chỉ là một bước, muốn dùng certificate vẫn cần parser chứng thư và trust policy.
8. Ví dụ dùng RSA-2048 để chạy phổ biến, không nhằm quy định mọi use case phải dùng RSA-2048; thuật toán/kích thước/trọn vòng đời phải tuân chính sách.

Với khóa HSM/KMS không xuất được, `getEncoded()` có thể trả `null`. Đừng coi đó là lỗi cần bypass security; thay vào đó dùng key handle/API ký hoặc giải mã được provider cho phép.

## 12. Java: parse certificate X.509 đúng API

Đoạn sau **minh họa bên trong method** có các import và exception handling thích hợp; giả sử file `certificate.pem` tồn tại và chứa một certificate X.509 hợp lệ về cú pháp:

```java
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

try (InputStream input = Files.newInputStream(Path.of("certificate.pem"))) {
    CertificateFactory factory = CertificateFactory.getInstance("X.509");
    X509Certificate certificate =
            (X509Certificate) factory.generateCertificate(input);
    PublicKey key = certificate.getPublicKey();
    String subject = certificate.getSubjectX500Principal().getName();
    String issuer = certificate.getIssuerX500Principal().getName();

    // Parsing, extracting key/subject/issuer do NOT establish trust.
}
```

Việc `getPublicKey()` trả về một `PublicKey` không thay thế chain validation. Nếu đây là certificate của endpoint TLS, cần kiểm trust anchor, chain, SAN/hostname, thời hạn và policy usage qua JSSE hoặc cơ chế PKIX đúng chuẩn. `certificate.checkValidity()` chỉ kiểm khoảng hiệu lực; `certificate.verify(somePublicKey)` chỉ kiểm chữ ký với key đã cho, không tự chứng minh `somePublicKey` thuộc issuer đáng tin.

### Phân biệt certificate DER và PEM

Hai dạng có thể chứa **cùng một certificate**:

```text
cert.der    = DER bytes của X.509 Certificate
cert.pem    = BEGIN CERTIFICATE + Base64 của chính DER bytes + END CERTIFICATE
```

Đổi dạng PEM ↔ DER **không** cấp thêm niềm tin. Certificate fingerprint thông thường hash **DER certificate bytes**, không hash cả dấu xuống dòng và PEM armor. Nếu file certificate bị thay, chữ ký/đường tin cậy cần được kiểm lại theo trust policy.

## 13. Java: PKCS#12 keystore không phải một private-key PEM

Một service có thể nhận identity dưới dạng `identity.p12` gồm private key và certificate chain. API Java minh họa (không phải chương trình độc lập, cần biến `password` và `alias` lấy từ cấu hình tin cậy và xử lý exception):

```java
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyStore;
import java.security.cert.Certificate;

KeyStore store = KeyStore.getInstance("PKCS12");
try (InputStream in = Files.newInputStream(Path.of("identity.p12"))) {
    store.load(in, password);
}
Key privateKey = store.getKey(alias, password);
Certificate[] chain = store.getCertificateChain(alias);
```

Không suy ra mọi PKCS#12 đều có private key; entry có thể là certificate hoặc key tùy cấu hình. Một private key và certificate chain trong cùng container không có nghĩa chain **được tin cậy**; bên nhận vẫn phải thực hiện validation theo yêu cầu, không lấy root peer gửi tự động làm CA.

Mật khẩu container bảo vệ thông tin ở rest theo provider/profile, **không phải khóa mã hóa dữ liệu ứng dụng**. Với HSM/KMS non-exportable key, thường service tham chiếu key handle và giữ certificate chain riêng, không cần xuất private key để tạo `.p12`.

## 14. Quy trình nhận diện file và chọn parser

Đầu tiên xác định **hợp đồng nguồn dữ liệu** rồi kiểm thực tế:

```text
Input có dòng BEGIN/END?
  |
  +-- Có:
  |   |
  |   +-- PUBLIC KEY            -> SPKI DER -> KeyFactory / X509EncodedKeySpec
  |   +-- PRIVATE KEY           -> PKCS#8 DER -> KeyFactory / PKCS8EncodedKeySpec
  |   +-- ENCRYPTED PRIVATE KEY -> giải mã PKCS#8 outer trước
  |   +-- CERTIFICATE           -> X.509 CertificateFactory + trust validation
  |   +-- RSA/EC PRIVATE KEY    -> parser legacy riêng / conversion phù hợp
  |   +-- OPENSSH ...           -> parser định dạng SSH
  |
  +-- Không:
      |
      +-- Binary DER SPKI/PKCS#8/Certificate -> parser đúng schema
      +-- Raw key bytes                     -> algorithm-specific spec/format
      +-- PKCS#12                           -> KeyStore PKCS12
      +-- JWK/JWKS JSON                      -> JWK parser + policy
```

**Không đoán chỉ từ extension**. Một ASN.1 DER blob chứa certificate có thể dùng `.cer`, `.crt`, `.der`; một file `.pem` có thể chứa nhiều certificate block. Khi nhận input không tin cậy:

- Giới hạn kích thước trước khi Base64 decode hoặc ASN.1 parse; giới hạn số block, chiều sâu/cấu trúc và thời gian xử lý.
- Từ chối label không thuộc allowlist và algorithm OID/kích thước/curve không đáp ứng chính sách.
- Nếu yêu cầu một public key SPKI thì từ chối certificate hoặc PKCS#1 RSA public key trừ khi đặc tả cho phép và parser đúng loại.
- Nếu cần certificate, không chỉ parse mà còn validate chain/identity/usage.
- Nếu cần private key, kiểm quyền và cách lưu/mở khóa, tránh xuất dữ liệu nhạy cảm ra log/heap không cần thiết.
- Nếu có nhiều block, dùng format mô tả **đúng cách chọn** leaf/intermediate và private key, không lấy ngẫu nhiên block đầu.

## 15. Các lỗi Java phổ biến và cách khắc phục

| Triệu chứng | Nguyên nhân có khả năng | Cách xử lý |
| --- | --- | --- |
| `InvalidKeySpecException` khi đọc `RSA PRIVATE KEY` bằng `PKCS8EncodedKeySpec` | PKCS#1 RSA private khác PKCS#8 | Dùng parser/convert schema chuẩn |
| `InvalidKeySpecException` với raw Ed25519 public 32 byte đưa vào `X509EncodedKeySpec` | Raw key không có SPKI wrapper | Dùng raw-key API phù hợp hoặc convert đúng RFC 8410 |
| CertificateFactory không nhận `PUBLIC KEY` | SPKI public key không phải X.509 certificate | `KeyFactory` cho SPKI; `CertificateFactory` cho certificate |
| Mở `ENCRYPTED PRIVATE KEY` như `PRIVATE KEY` | Chưa giải mã EncryptedPrivateKeyInfo | Dùng password/algorithm/provider phù hợp |
| OpenSSL encrypted key không mở được ở Java provider hiện tại | PBES2/algorithm identifiers/parameters khác hoặc unsupported | Kiểm compatibility/migrate bằng tooling an toàn |
| `getEncoded() == null` | Key không export được do HSM/provider | Dùng key handle để thực hiện crypto operation |
| Certificate parse thành công nhưng HTTPS fail | Chain/SAN/hostname/expiry/EKU/trust policy không hợp lệ | Kiểm PKIX/endpoint identification đúng cách |
| Signature verify sai sau khi convert key | Không đúng key pair, algorithm, message bytes hoặc scheme | Kiểm khóa và signed bytes trước khi quy lỗi cho PEM |
| Chỉ đổi PEM label mà parser báo lỗi | Payload ASN.1 bên trong không khớp label | Parse/serialize thực sự sang cấu trúc đích |
| PKCS#12 mở được nhưng không có private key alias kỳ vọng | Container/alias/entry khác yêu cầu | Liệt kê/kiểm entry theo policy và quyền |

Không giải quyết lỗi trust bằng cách tắt TLS verification, tắt hostname verification, tự thêm certificate bất kỳ vào truststore, hoặc fallback sang thuật toán yếu. Không giải quyết lỗi key format bằng cách in private key raw vào log để thử.

## 16. Vận hành: key lifecycle và certificate lifecycle

**Private key:**

1. Sinh bằng CSPRNG/provider tin cậy hoặc HSM/KMS theo chính sách thuật toán/độ dài.
2. Giữ bí mật và hạn chế export; nếu cần lưu file, ACL/private-key encryption/password đủ mạnh, backup và auditing.
3. Không lưu plaintext private key trong source control, issue tracker, chat, config không được bảo vệ.
4. Dùng key ID/version để quản lý rotation; không lẫn secret key material với public metadata.
5. Khi convert/backup, bảo vệ file tạm và xóa theo quy trình; việc chuyển sang PEM không tự mã hóa.
6. Với encryption key và signing key, tách mục đích; không dùng một key cho mọi giao thức không được thiết kế.
7. Nếu nghi lộ private key, thực hiện quy trình revoke/rotate và phát hành lại certificate cần thiết.

**Certificate và public key:**

1. Xác minh nguồn gốc bằng chain/trust anchor hoặc pinning có lifecycle phù hợp; không tin chỉ vì parse được.
2. Kiểm SAN/hostname và intended key usage theo ứng dụng; kiểm validity và revocation theo policy.
3. Kiểm cặp public/private key tương ứng khi triển khai server identity; certificate không chứa private key.
4. Tự động theo dõi expiry/renewal theo hệ thống vận hành, bảo đảm không phát key ra ngoài.
5. Khi chuyển DER ↔ PEM, giữ đúng bytes và kiểm lại format; không coi conversion là trust validation.
6. Với CSR, kiểm yêu cầu và quyền cấp certificate; CSR không phải certificate đã cấp.

**Key wrapping/container:**

1. Biết rõ key encryption key (KEK) và data encryption key (DEK) thuộc vùng nào, bảo vệ bằng KMS/HSM khi phù hợp.
2. Không xem encrypted blob là private key đã mở; phải qua decrypt/unwrap với quyền tương ứng.
3. Version KDF/algorithm và kiểm interoperability trước khi rotation/migration.
4. Chống downgrade profile hoặc dùng key không còn được phép do request chỉ định.
5. Không cài password trong lệnh hoặc log; không nhầm password container với algorithm key.

## 17. Bộ test kỹ thuật nên chạy

| Test | Đầu vào | Kỳ vọng |
| --- | --- | --- |
| PKCS#8 round trip | RSA private key exportable → DER → PEM → DER | Byte DER và key sau parse khớp |
| SPKI round trip | RSA public key → DER → PEM → DER | Byte DER và key sau parse khớp |
| Ký/xác minh | Private PKCS#8 và public SPKI vừa parse | Chữ ký xác minh đúng message |
| Nhãn PEM sai | SPKI `PUBLIC KEY` đưa vào parser chờ `CERTIFICATE` | Bị từ chối |
| PKCS#1/PKCS#8 nhầm | `RSA PRIVATE KEY` đưa vào PKCS#8 parser | Bị từ chối/không parse sai schema |
| Raw Ed25519 vs SPKI | Raw bytes theo RFC 8032 | Không tự parse như SPKI nếu thiếu wrapper |
| Encrypted PKCS#8 | Password đúng/sai, KDF/algorithm theo policy | Chỉ mở khóa với quyền/password đúng; lỗi được xử lý an toàn |
| Certificate parse | X.509 DER và PEM của cùng certificate | Parse được; public key/SPKI giống nhau |
| Certificate trust | Self-signed không nằm trong truststore | Có thể parse nhưng không tự được tin |
| Hostname | Certificate cho DNS A dùng truy cập DNS B | Endpoint identification từ chối |
| Expired certificate | Certificate ngoài validity | Bị từ chối theo policy dù ASN.1 parse được |
| Kích thước input | PEM/ASN.1 quá lớn hoặc quá nhiều block | Từ chối sớm theo quota |
| Container PKCS#12 | Entry/cert chain/password phù hợp | Lấy đúng entry, không tự tin chain |
| Non-exportable key | Hardware/provider key có `getEncoded() == null` | Dùng handle, không cố export |
| Định dạng nhiều ngôn ngữ | Java và OpenSSL cùng SPKI/PKCS#8 | Có thể parse và ký/verify chéo đúng chuẩn |

**Test phân biệt trust:** cho ứng dụng parse một certificate tự ký và thử tự verify bằng public key từ chính certificate. Việc toán học thành công không được tự biến đối tượng thành peer đáng tin. Hãy test đầy đủ trust anchor/chain, SAN và authorization.

## 18. Bài tập thực hành

### Bài 1 — Đọc nhãn và chọn API

Bạn nhận năm nguồn dữ liệu: `BEGIN PUBLIC KEY`, `BEGIN PRIVATE KEY`, `BEGIN ENCRYPTED PRIVATE KEY`, `BEGIN CERTIFICATE` và `BEGIN RSA PRIVATE KEY`. Với mỗi nguồn, hãy xác định payload/schema, có chắc chứa private key không, có password không, Java parser/KeySpec phù hợp và rủi ro nếu nhận từ client chưa tin cậy.

### Bài 2 — Chạy KeyFormatsDemo

Biên dịch bằng `javac --release 17`. Giải thích vì sao public format hiển thị `X.509` nhưng chương trình không hề tạo certificate. Thử thay nhãn PEM của public key thành `CERTIFICATE` mà không thay DER, rồi mô tả tại sao đó không phải conversion đúng.

### Bài 3 — Migrate khóa RSA kiểu cũ

Một đối tác gửi private key `BEGIN RSA PRIVATE KEY` không mã hóa. Thiết kế quy trình chuyển sang encrypted PKCS#8: chọn parser chuẩn, giới hạn input, kiểm public key tương ứng, mã hóa với password/algorithm theo policy, bảo vệ file tạm, kiểm compatibility và theo dõi rotation. Chỉ rõ nơi không được log secret.

### Bài 4 — Raw Ed25519

Một giao thức trả 32 byte Ed25519 public key. Bạn cần dùng Java JCA để verify. Ghi rõ chuẩn byte input, cách tạo public-key object hoặc SPKI theo spec phù hợp, algorithm allowlist và cách xác lập **key đó thuộc bên nào** (trust binding) trước khi verify message.

### Bài 5 — Certificate bị giả mạo tên

Một endpoint gửi certificate tự ký có Subject ghi tên doanh nghiệp và public key hoạt động bình thường. Vì sao `getPublicKey()` và `verify(ownPublicKey)` vẫn chưa đủ để tin API? Mô tả trust anchor, chain, hostname/SAN, validity, KU/EKU, revocation policy và quyền thực hiện tác vụ.

### Bài 6 — Vận hành server key + certificate

So sánh ba lựa chọn: (a) `PRIVATE KEY` PEM + certificate PEM, (b) password-protected PKCS#12, (c) HSM/KMS non-exportable key + certificate chain. Phân tích cách triển khai, backup, rotation, quyền và nguy cơ lộ secret. Khi certificate hết hạn, vì sao không nhất thiết phải thay đổi thuật toán/format private key mà vẫn phải tuân chính sách rotation?

**Điều kiện hoàn thành:** bạn nhận biết chính xác **kiểu dữ liệu, schema, encoding, lớp bảo vệ và trust policy**; phân biệt được “convert PEM/DER” với “convert PKCS#1/PKCS#8”, “decrypt private key” với “dùng key để mã hóa”, và “đọc certificate” với “tin cậy certificate”.

## 19. Tài liệu tham khảo

- [RFC 7468 — Textual Encodings of PKIX, PKCS, and CMS Structures](https://www.rfc-editor.org/rfc/rfc7468): PEM textual encoding và labels.
- [ITU-T X.690 — ASN.1 Encoding Rules](https://www.itu.int/rec/T-REC-X.690/): BER, CER và DER.
- [RFC 5280 — Internet X.509 PKI Certificate and CRL Profile](https://www.rfc-editor.org/rfc/rfc5280): X.509 certificate và PKIX.
- [RFC 5958 — Asymmetric Key Packages](https://www.rfc-editor.org/rfc/rfc5958): OneAsymmetricKey, EncryptedPrivateKeyInfo.
- [RFC 5208 — PKCS#8 Private-Key Information Syntax](https://www.rfc-editor.org/rfc/rfc5208): PrivateKeyInfo/EncryptedPrivateKeyInfo truyền thống.
- [RFC 8017 — PKCS#1: RSA Cryptography Specifications](https://www.rfc-editor.org/rfc/rfc8017): cấu trúc và thuật toán RSA.
- [RFC 8410 — Algorithm Identifiers for Ed25519, Ed448, X25519, X448](https://www.rfc-editor.org/rfc/rfc8410): SPKI/PKCS#8 cho thuật toán hiện đại.
- [RFC 7292 — PKCS#12](https://www.rfc-editor.org/rfc/rfc7292): container key/certificate.
- [Java SE 17 — PKCS8EncodedKeySpec](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/spec/PKCS8EncodedKeySpec.html) và [X509EncodedKeySpec](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/spec/X509EncodedKeySpec.html): các key spec cần phân biệt.
- [Java SE 17 — EncryptedPrivateKeyInfo](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/EncryptedPrivateKeyInfo.html): private key được mã hóa.
- [Java SE 17 — CertificateFactory](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertificateFactory.html), [X509Certificate](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/X509Certificate.html), [CertPathValidator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertPathValidator.html): parse và validate certificate.
- [Java SE 17 — KeyStore](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.html): PKCS#12 và keystore providers.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="public-key-cryptography-007">8.7 RSA-OAEP, giới hạn kích thước bản rõ và mô hình mã hóa lai</a>

<details>
<summary>Xem chi tiết</summary>

# 8.7. RSA-OAEP, giới hạn kích thước bản rõ và mô hình mã hóa lai

RSA là một thuật toán khóa công khai có thể dùng trong nhiều cơ chế khác nhau. Khi muốn mã hóa bằng RSA hiện đại, không lấy phép tính RSA “thuần” hay padding PKCS#1 v1.5 rồi tự áp dụng cho dữ liệu lớn. Cần dùng một **encryption scheme được chuẩn hóa**, điển hình là **RSAES-OAEP (Optimal Asymmetric Encryption Padding)**, với thuật toán băm, MGF1 và nhãn OAEP được xác định rõ.

Tuy nhiên, ngay cả RSA-OAEP đúng chuẩn cũng **không phải công cụ mã hóa trực tiếp tệp hoặc message dài**. Nó giới hạn kích thước bản rõ chỉ vài trăm byte tùy độ dài modulus và hash. Vì vậy mô hình ứng dụng thông dụng là **mã hóa lai (hybrid encryption)**: sinh một khóa đối xứng ngẫu nhiên để mã hóa dữ liệu bằng AEAD (như AES-GCM), rồi dùng RSA-OAEP **mã hóa/gói khóa đối xứng** cho người nhận.

Bài này giúp bạn trả lời bốn câu hỏi thực tế:

1. Vì sao RSA-OAEP chỉ mã hóa được payload ngắn, và tính được giới hạn chính xác?
2. OAEP gồm những tham số nào, tại sao **OAEP hash** và **MGF1 hash** phải được hai bên thống nhất?
3. Trong mã hóa lai, RSA làm gì, AES-GCM làm gì, và định dạng envelope cần lưu gì để bên nhận giải mã?
4. Cách viết Java 17+ sử dụng JCA/JCE chuẩn, xử lý lỗi khi bản rõ quá dài, sai label OAEP hoặc bị sửa ciphertext?

Sau bài học, bạn nên tự tính được `mLen ≤ k − 2hLen − 2`, thiết kế được một hybrid envelope có version/metadata rõ ràng, hiểu sự khác biệt giữa **tính bí mật**, **tính toàn vẹn** và **xác thực người gửi**, cũng như kiểm tra được một luồng encrypt/decrypt thực thi trong Java.

## 1. RSA khóa công khai: cặp khóa và mục đích

RSA dùng một cặp khóa:

- **Public key**: có thể công bố cho bên gửi; trong RSA encryption, dùng để mã hóa khóa hoặc dữ liệu ngắn dành cho người giữ private key.
- **Private key**: phải được bảo vệ; trong RSA decryption, dùng để khôi phục giá trị đã mã hóa bằng public key tương ứng.

Ở tầng toán học, RSA liên quan đến lũy thừa modulo `n`, nhưng một phép toán modular exponentiation **không phải scheme mã hóa an toàn** nếu thiếu encoding/padding và quy tắc kiểm tra phù hợp. Hãy sử dụng **RSAES-OAEP** từ thư viện crypto tin cậy, không tự ghép byte hay tự cài OAEP.

```text
             Recipient
           +--------------+
           | RSA public   |----> chia sẻ sau khi xác minh nguồn/định danh
           | RSA private  |----> lưu bí mật, chỉ môi trường nhận sử dụng
           +--------------+

Sender: data/key -- RSA-OAEP(public) --> RSA ciphertext
Recipient: RSA ciphertext -- RSA-OAEP(private) --> data/key
```

Chỉ vì bạn có một “public key” không có nghĩa nó thực sự thuộc người nhận mong đợi. Nếu đối thủ thay được public key trong quá trình phân phối, họ có thể khiến bạn mã hóa dữ liệu cho **khóa của đối thủ**. Mọi triển khai thật đều phải kiểm tra đường tin cậy: certificate chain và tên máy/chủ thể phù hợp, khóa đã pin có chính sách rotation, hoặc một kênh phân phối khóa công khai đã xác thực.

**RSA encryption khác RSA signature.** RSA-OAEP bảo vệ tính bí mật của plaintext cho người sở hữu private key; không tự tạo một chữ ký để người nhận biết ai đã gửi. Dùng RSA-PSS (hoặc scheme ký phù hợp) khi cần chữ ký số; không coi kết quả RSA-OAEP là “chữ ký ngược”.

## 2. RSA-OAEP là gì?

RSAES-OAEP được mô tả trong **RFC 8017 / PKCS #1 v2.2**. OAEP mã hóa thông điệp đầu vào thành một block có cấu trúc, sử dụng hash, mask generation function (thường là MGF1), seed ngẫu nhiên và một **label** tùy chọn, rồi mới áp dụng RSA public-key operation.

Các thành phần liên quan:

| Thành phần | Vai trò | Điều cần thống nhất |
| --- | --- | --- |
| Modulus RSA `n` | Quy định kích thước block RSA | Public/private key tương ứng, kích cỡ modulus |
| OAEP digest `Hash` | Hash trong OAEP, quyết định `hLen` | Ví dụ `SHA-256` với 32 byte digest |
| Mask generation function | Sinh mặt nạ bên trong OAEP | Thường `MGF1` |
| Digest của MGF1 | Hash dùng bên trong MGF1 | Ví dụ cũng là `SHA-256`, phải cấu hình rõ |
| Label `L` | Chuỗi byte ngữ cảnh tùy chọn | Hai bên dùng chính xác cùng byte label |
| Random seed | Làm mã hóa xác suất | Thư viện sinh ngẫu nhiên phù hợp |
| RSA public/private key | Mã hóa/giải mã | Key phải đúng người nhận |

Một cách nhìn khái niệm:

```text
Message M (byte[≤ max]) + Label L
                 |
          OAEP Encoding
      (Hash, MGF1, random seed)
                 |
     Encoded message EM (k bytes)
                 |
        RSA public operation
                 |
     RSA ciphertext C (k bytes)
                 |
     RSA private operation + OAEP Decoding
                 |
            Message M
```

Sơ đồ trên diễn tả ý tưởng; không dùng để tự implement padding.

### 2.1. OAEP là randomized encryption

Hai lần mã hóa **cùng một plaintext và cùng public key** bằng RSA-OAEP có thể tạo **ciphertext khác nhau** vì OAEP có randomness. Đây là hành vi mong muốn. Đừng kiểm thử RSA-OAEP bằng cách đòi ciphertext hai lần phải bằng nhau.

Bài kiểm tra đúng phải là:
- Sau khi encrypt rồi decrypt bằng đúng private key và tham số OAEP, plaintext khôi phục khớp byte ban đầu.
- Nếu làm sai private key, label hoặc dữ liệu ciphertext, thao tác decryption bị từ chối.
- Bản rõ dài hơn giới hạn sẽ bị từ chối **trước hoặc khi encrypt**, không được cắt bớt dữ liệu.

### 2.2. Label OAEP là gì và có bí mật không?

OAEP nhận một byte string nhãn `L`. Nó có thể để rỗng (một lựa chọn chuẩn) hoặc quy định rõ trong protocol, ví dụ:

```text
L = UTF-8("hybrid-demo:v1|CEK")
```

Label **không cần bí mật**. Nó giúp ràng buộc scheme với một miền sử dụng cụ thể nếu các bên định nghĩa thống nhất. Khi encrypt dùng label `L1`, phía decrypt phải dùng đúng byte `L1`; thay thành `L2` thường khiến kiểm tra OAEP thất bại.

Không tự động coi OAEP label là hệ thống authorization. Người nhận vẫn phải xác minh key/certificate, envelope version, ai được phép gửi, và những trường metadata nào được xác thực. Nếu dùng label trong giao thức, phải công bố rõ byte/encoding và giữ ổn định theo version.

### 2.3. Hai hash trong OAEP: thông số dễ bị bỏ qua

Khi cấu hình:

```text
OAEP digest:   SHA-256
MGF:           MGF1
MGF1 digest:   SHA-256
Label:         "hybrid-demo:v1|CEK" (UTF-8)
```

đây là **một bộ tham số cụ thể**. Có thể một thư viện dùng OAEP digest SHA-256 nhưng ngầm chọn digest khác cho MGF1 nếu không chỉ định rõ. Hai implementation “cùng RSA-OAEP SHA-256” theo tên gọi nhưng khác MGF1 digest có thể **không giải mã được cho nhau**.

Trong Java 17, dùng `OAEPParameterSpec` để cấu hình minh bạch:

```java
OAEPParameterSpec oaep = new OAEPParameterSpec(
    "SHA-256",
    "MGF1",
    MGF1ParameterSpec.SHA256,
    new PSource.PSpecified(
        "hybrid-demo:v1|CEK".getBytes(StandardCharsets.UTF_8)
    )
);
```

Khi đưa `OAEPParameterSpec` vào `Cipher.init(...)`, cả OAEP digest, MGF1 digest và label được quy định. Mô tả `"RSA/ECB/OAEPPadding"` là tên transformation JCA thường được hỗ trợ; chữ `ECB` ở đây là một thành phần tên transformation của provider, **không có nghĩa RSA đang mã hóa nhiều block ở chế độ ECB như AES**.

Không tự phỏng đoán tham số chỉ dựa vào tên transformation `"RSA/ECB/OAEPWithSHA-256AndMGF1Padding"`. Nếu cần tương thích chéo Java, .NET, OpenSSL hoặc HSM, gửi đầy đủ test vector/schema và so sánh từng tham số.

## 3. Giới hạn kích thước bản rõ RSA-OAEP

Điều quan trọng nhất của bài học là công thức từ RSAES-OAEP (RFC 8017):

\[
\boxed{\text{mLen} \le k - 2\,\text{hLen} - 2}
\]

Trong đó:
- `mLen` là **số byte** của bản rõ muốn mã hóa bằng RSA-OAEP, không phải số ký tự Java `String.length()`.
- `k` là **độ dài RSA modulus tính bằng byte**: `k = ceil(bitLength(n) / 8)`.
- `hLen` là **độ dài kết quả OAEP hash tính bằng byte**. Với SHA-256, `hLen = 32`; SHA-384 là 48; SHA-512 là 64.
- Hai số `2hLen` và `2` là overhead cấu trúc OAEP; không thể lấy toàn bộ `k` byte làm bản rõ.
- MGF1 hash phải được cấu hình thống nhất; trong công thức này `hLen` chỉ kết quả của **OAEP Hash**, không lấy độ dài ciphertext hay key byte để thay.

**Ví dụ RSA-2048 + OAEP SHA-256:**

\[
k = 2048/8 = 256 \text{ byte}
\]

\[
\text{maxM} = 256 - 2(32) - 2 = \boxed{190\text{ byte}}
\]

Nếu input là 191 byte thì **không thể mã hóa trực tiếp** bằng chính cấu hình RSAES-OAEP này. Đây là giới hạn scheme, không phải lỗi “Java không nhận được chuỗi dài”.

### 3.1. Bảng kích thước minh họa

| RSA modulus | `k` (byte) | OAEP hash | `hLen` | Bản rõ tối đa | RSA ciphertext mỗi lần |
| --- | ---: | --- | ---: | ---: | ---: |
| 2048 bit | 256 | SHA-256 | 32 | **190 byte** | 256 byte |
| 3072 bit | 384 | SHA-256 | 32 | **318 byte** | 384 byte |
| 4096 bit | 512 | SHA-256 | 32 | **446 byte** | 512 byte |
| 2048 bit | 256 | SHA-384 | 48 | **158 byte** | 256 byte |
| 2048 bit | 256 | SHA-512 | 64 | **126 byte** | 256 byte |
| 3072 bit | 384 | SHA-512 | 64 | **254 byte** | 384 byte |

Bảng chỉ nói **dung lượng dữ liệu trên một phép mã hóa OAEP**, không đánh giá một khóa RSA đủ mạnh cho mọi threat model. Việc chọn RSA-2048/3072/4096 cần tuân thủ chính sách và độ dài vòng đời dữ liệu; khóa càng lớn cũng ảnh hưởng hiệu năng, ciphertext và độ dài tối đa.

Đặc biệt: khi tăng modulus từ 2048 lên 4096 bit, dung lượng OAEP SHA-256 chỉ tăng từ **190 lên 446 byte**. Nó vẫn rất nhỏ so với tệp ảnh, JSON request hoặc dữ liệu kinh doanh thực tế.

### 3.2. Byte length khác độ dài chuỗi

Trong Java, một chuỗi tiếng Việt có thể nhiều byte UTF-8 cho một ký tự. Ví dụ, `"A"` là 1 byte UTF-8, nhưng nhiều ký tự ngoài ASCII thường cần 2–4 byte. RSA-OAEP xem **byte array**, không thấy “190 ký tự”.

Khi dữ liệu là text:

```java
byte[] plaintext = value.getBytes(StandardCharsets.UTF_8);
int lengthBytes = plaintext.length;
```

Không dùng `value.length()` làm độ dài OAEP. Java `String.length()` đếm UTF-16 code units, còn OAEP limit tính theo byte.

### 3.3. Điều gì xảy ra khi vượt giới hạn?

Một provider JCA thường ném `IllegalBlockSizeException` hoặc một ngoại lệ thuộc `GeneralSecurityException` khi `Cipher.doFinal(...)` nhận OAEP plaintext quá dài. Không được “tự sửa” bằng cách cắt bớt input hoặc biến đổi thành nhiều phép RSA theo kinh nghiệm.

Đoạn logic để tính giới hạn:

```java
int modulusBits = rsaPublicKey.getModulus().bitLength();
int k = (modulusBits + 7) / 8;
int hLen = 32; // SHA-256 output length
int maximumOaepPlaintext = k - 2 * hLen - 2;
```

Nếu `maximumOaepPlaintext` âm, bộ tham số không có đủ chỗ để encode thông điệp, cần chọn key/hash và scheme phù hợp. Không suy ra “RSA key 2048 bit” thì có thể mã hóa 256 byte dữ liệu gốc; **256 byte là chiều dài RSA ciphertext**, không phải giới hạn plaintext OAEP.

## 4. Vì sao không chia tệp thành các khối nhỏ để RSA mã hóa từng khối?

Một cách làm sai thường thấy:

```text
File dài 1 MiB
   |
chia thành khối 190 byte
   |
RSA-OAEP encrypt(block0)
RSA-OAEP encrypt(block1)
...
RSA-OAEP encrypt(blockN)
   |
nối các RSA ciphertext
```

Dù từng block có thể mã hóa hợp lệ, thiết kế này thường **không nên dùng**:

1. **Rất kém hiệu năng:** RSA public-key operations tốn CPU hơn AES trong bulk encryption; giải mã private-key còn đắt hơn. Mã hóa một tệp lớn có thể tạo ra hàng nghìn phép RSA.
2. **Dung lượng phình mạnh:** với RSA-2048 + OAEP SHA-256, mỗi 190 byte plaintext thành 256 byte ciphertext, chưa tính metadata, index và protection.
3. **Thiếu giao thức xác thực tổng thể:** OAEP là scheme mã hóa bất đối xứng, không phải AEAD cho dòng các block. Nếu thiếu cơ chế bổ sung, thay thế/đảo thứ tự/bỏ block có thể không được phát hiện đúng ý nghĩa của toàn bộ tệp.
4. **Không rõ canonical framing:** phải định nghĩa số lượng/độ dài/chỉ số của từng block, chống chèn/tráo/trùng/lặp và xử lý lỗi.
5. **Không có ưu điểm quản lý khóa:** nhiều RSA decrypt vẫn dùng cùng private key; không tận dụng được tốc độ AES và khóa ngẫu nhiên độc lập cho mỗi đối tượng.

Cách đúng cho payload dài là **mã hóa lai**: **RSA-OAEP một lần cho khóa ngẫu nhiên ngắn**, **AEAD cho toàn bộ dữ liệu** theo format chuẩn.

## 5. Mô hình hybrid encryption: khóa phiên + dữ liệu

Tách hai trách nhiệm:

- **RSA-OAEP:** bảo vệ một **content-encryption key (CEK)** ngẫu nhiên. Ví dụ CEK 32 byte để dùng làm AES-256 key. Public key bên nhận mã hóa CEK; private key bên nhận giải mã CEK.
- **AES-GCM:** dùng CEK để mã hóa nội dung với nonce độc lập, tạo **ciphertext và authentication tag**. Bên nhận kiểm tra tag khi giải mã để xác định dữ liệu/AAD/khóa/nonce phù hợp.

```text
BÊN GỬI                                      BÊN NHẬN
---------                                     ---------
Public RSA key đã xác thực                    Private RSA key an toàn
       |                                           |
SecureRandom: CEK 32 byte                           |
       |                                           |
       +---- RSA-OAEP(public, CEK) --> wrappedCEK ---+--> RSA-OAEP(private)
       |                                                  |
       |                                                  +--> CEK 32 byte
       v                                                          |
SecureRandom: nonce 12 byte                                       |
       |                                                          |
AES-256-GCM(CEK, nonce, AAD, plaintext)                            |
       |                                                          |
       +--> ciphertext + tag -----------------------------> AES-GCM decrypt
                         |                                    |
              Hybrid envelope:                             Verify tag
              wrappedCEK                                    |
              nonce                                         v
              ciphertext+tag                        Plaintext (only if valid)
              version, algorithms, keyId, AAD context
```

Một đối tác nhận cần **private key RSA khớp với wrappedCEK**, **các tham số OAEP khớp**, rồi dùng CEK đã giải mã để kiểm tra AES-GCM tag. Nếu password/login hay danh tính người dùng có liên quan, đó là lớp xác thực/quản lý quyền riêng và không thể thay bằng việc mã hóa thành công.

### 5.1. Phân biệt CEK, KEK và RSA key

Trong nhiều tài liệu:
- **CEK/DEK:** khóa đối xứng mã hóa dữ liệu (content/data encryption key).
- **KEK:** khóa mã hóa một khóa khác (key-encryption key); trong mô hình này, RSA public key được dùng trong **scheme key transport/wrapping bằng RSA-OAEP**, không phải một chuỗi 32 byte dùng làm AES KEK.
- **Wrapped key:** bản mã RSA của CEK, không phải chính CEK.

RSA-OAEP wrapping CEK **không phải** thuật toán **RSA-KEM**. Cả hai có thể thuộc họ thiết kế bảo vệ khóa/hybrid, nhưng scheme và cấu trúc byte khác nhau; không gọi RSA-OAEP là RSA-KEM hoặc cho một bên dùng RSA-KEM rồi trông chờ OAEP giải mã được.

### 5.2. Vì sao dùng AES-GCM thay AES-CBC tự thiết kế?

AES-GCM là **AEAD (Authenticated Encryption with Associated Data)**. Nó vừa mã hóa plaintext, vừa cho phép phát hiện sửa đổi ciphertext và dữ liệu AAD. Thay vì tự ghép AES-CBC + padding + MAC theo thứ tự không rõ ràng, dùng AEAD đã chuẩn hóa dễ giảm nhiều lớp lỗi.

Các thông số mẫu của bài học:
- CEK: **32 byte** (AES-256).
- GCM nonce: **12 byte** (96 bit), mới cho mỗi lần encrypt với cùng CEK.
- Tag: **16 byte** (128 bit).
- Ciphertext + tag: với plaintext `N` byte trong JCA demo, thường là `N + 16` byte.
- AAD: byte mô tả ngữ cảnh, ví dụ `"hybrid-demo:v1|AES-256-GCM"`. AAD **được xác thực nhưng không được mã hóa**.

Không được tái sử dụng cùng cặp `(CEK, nonce)` trong AES-GCM. Một CEK mới ngẫu nhiên cho mỗi object giúp cô lập các object, nhưng vẫn cần cơ chế nonce phù hợp và chính sách giới hạn số lần encrypt nếu reuse key. Không dùng salt/password KDF làm nonce một cách tùy tiện.

### 5.3. Authentication tag bảo vệ điều gì?

Ở phía nhận, AES-GCM decryption kiểm tra tag với **khóa, nonce, AAD và ciphertext**. Nếu dữ liệu bị thay đổi, giải mã sẽ từ chối với lỗi tag trong trường hợp điển hình. Tuy nhiên:

- Không được đưa plaintext chưa xác minh vào hệ thống nghiệp vụ.
- Nếu ciphertext bị sửa, key sai, nonce sai hoặc AAD sai, tag có thể đều thất bại; không khẳng định exception chỉ ra nguyên nhân duy nhất.
- Tag đảm bảo tính toàn vẹn trong khuôn khổ CEK; **không chứng minh danh tính người gửi**, vì bất kỳ ai có public key người nhận đều có thể tự sinh CEK mới, tạo AES-GCM ciphertext/tag hợp lệ rồi wrap CEK bằng RSA-OAEP.

Khi cần **xác thực tác giả**, phải tích hợp chữ ký số (ví dụ Ed25519 hoặc RSA-PSS theo chính sách), một MAC đã phân phối khóa có xác thực, hoặc một giao thức bảo mật đã định nghĩa identity binding. Chữ ký nên xác thực **envelope hoặc nội dung ngữ nghĩa đầy đủ** (gồm version, thuật toán, keyId, wrappedKey, nonce, ciphertext/tag và context) theo serialization canonical rõ ràng.

## 6. Định dạng hybrid envelope cần lưu và truyền

Một envelope có thể mô tả logic như sau:

```json
{
  "formatVersion": 1,
  "recipientKeyId": "recipient-key-2026",
  "wrap": {
    "scheme": "RSAES-OAEP",
    "oaepHash": "SHA-256",
    "mgf": "MGF1",
    "mgfHash": "SHA-256",
    "labelEncoding": "UTF-8",
    "label": "hybrid-demo:v1|CEK",
    "wrappedKeyBase64Url": "<base64url-of-rsa-ciphertext>"
  },
  "data": {
    "scheme": "AES-256-GCM",
    "nonceBase64Url": "<base64url-of-12-byte-nonce>",
    "ciphertextAndTagBase64Url": "<base64url-of-ciphertext-plus-tag>"
  },
  "aadProfile": "hybrid-demo:v1|AES-256-GCM"
}
```

Đây là **sơ đồ JSON mô tả**, không phải một wire format được chuẩn hóa hay blob có thể giải mã ngay. Các token đặt trong dấu ngoặc nhọn chỉ là mô tả kiểu dữ liệu, không phải khóa thật. Một giao thức sản xuất phải chỉ định chính xác encoding, parser, quy tắc canonicalization và trường nào được xác thực.

Những mục tối thiểu cần nhất quán:

| Trường | Mục đích | Bí mật? | Quy tắc quan trọng |
| --- | --- | --- | --- |
| `formatVersion` | Chọn một profile chính xác | Không | Không thử nhiều version tùy tiện |
| `recipientKeyId` | Tra private key theo chính sách tin cậy | Không | Không để client tự buộc thuật toán yếu |
| OAEP hash/MGF1 hash/label | Giải RSA đúng thông số | Không | Giá trị byte phải khớp hoàn toàn |
| `wrappedCEK` | Bảo vệ CEK khi truyền/lưu | Không phải CEK thuần | Ciphertext RSA có độ dài modulus |
| GCM nonce | Tham số AES-GCM | Không | Không trùng với cùng CEK |
| `ciphertextAndTag` | Dữ liệu đã mã hóa + tag | Không cần bí mật riêng nếu crypto đúng | Phải giữ trọn tag; kiểm tra trước sử dụng |
| `AAD` hoặc profile tạo AAD | Ràng buộc metadata/ngữ cảnh | Không | Encrypt/decrypt dùng cùng byte AAD |
| CEK trước/sau unwrap | Khóa đối xứng dùng AES | **Có** | Không ghi plaintext key vào log, JSON, DB |
| RSA private key | Khôi phục CEK | **Có** | Bảo vệ quyền truy cập, lưu/chạy an toàn |

**Không thể chỉ lưu ciphertext rồi bỏ nonce hoặc OAEP parameters**; không có đủ dữ liệu và quy tắc để decrypt sau này. Khi format thay đổi, cần version để phân biệt.

### 6.1. AAD nên bao phủ metadata nào?

Trong ví dụ Java, AAD là hằng `"hybrid-demo:v1|AES-256-GCM"` để dễ kiểm chứng. Trong giao thức thật, nên xây dựng AAD theo một **canonical byte format** ràng buộc đủ những thuộc tính ảnh hưởng đến ý nghĩa dữ liệu, chẳng hạn:
- `formatVersion`, algorithm suite, lựa chọn digest, độ dài tag.
- `recipientKeyId`/id khóa tin cậy, domain của ứng dụng.
- Tenant, resourceId/documentId, loại nội dung, quyền sở hữu logic.
- Các header/framing field cần bảo vệ, gồm wrappedKey hoặc một hash/framing được quy định rõ nếu thiết kế yêu cầu.

Bên nhận phải tự tái tạo **đúng AAD bytes**, không lấy toàn bộ metadata không đáng tin rồi coi nó tự xác thực. Hợp đồng cần tránh ngụy biện “tag đã kiểm tra body” trong khi route, tenant hay algorithm version nằm ngoài miền bảo vệ.

Với protocol nhiều bên nhận, cần thiết kế key-wrap entries, binding recipient và xác thực danh sách các người nhận một cách rõ ràng. Không tự động cho thay thế wrappedKey hoặc chuyển recipient chỉ vì ciphertext data “trông giống”.

### 6.2. Nonce, label và salt không cùng vai trò

Ở đây:
- **OAEP label:** context byte ảnh hưởng đến OAEP decoding, không phải IV và không cần ngẫu nhiên.
- **GCM nonce:** tham số độc lập, với yêu cầu không được lặp dưới cùng AES key.
- **Salt:** thường xuất hiện trong KDF từ password hoặc secret khác; hybrid RSA-OAEP + CEK ngẫu nhiên **không bắt buộc salt KDF**. Đừng thêm salt chỉ vì nhìn thấy nó trong ví dụ password-based encryption.
- **Authentication tag:** thông tin kiểm tra AEAD, không phải một key và không thể bỏ qua để trả plaintext.

Tạo CEK và nonce bằng `SecureRandom`; không dùng `Random` hoặc thời gian hiện tại làm nguồn cho CEK. Không lấy một số từ `System.currentTimeMillis()` để làm “random secret”.

## 7. Java 17+: ví dụ hybrid encryption chạy được

Ví dụ này dùng toàn bộ JDK chuẩn:

- `KeyPairGenerator` sinh RSA key pair **2048 bit cho mục đích demo**.
- `Cipher` với `RSA/ECB/OAEPPadding` và **`OAEPParameterSpec` tường minh**: OAEP digest SHA-256, MGF1 digest SHA-256, label `hybrid-demo:v1|CEK`.
- `SecureRandom` tạo CEK AES-256 32 byte và nonce GCM 12 byte.
- `Cipher` với `AES/GCM/NoPadding`, tag 128 bit, AAD không đổi.
- Một plaintext **2.000 byte** được mã hóa bằng AES-GCM; RSA-OAEP chỉ mã hóa CEK 32 byte.
- Kiểm tra round-trip, phát hiện sửa ciphertext, từ chối sai OAEP label và từ chối RSA-OAEP mã hóa trực tiếp payload quá dài.

Tạo tệp **`HybridEncryptionDemo.java`**:

```java
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

public class HybridEncryptionDemo {
    static final SecureRandom RNG = new SecureRandom();

    static final byte[] AAD =
            "hybrid-demo:v1|AES-256-GCM".getBytes(StandardCharsets.UTF_8);

    static OAEPParameterSpec spec(String label) {
        return new OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                new PSource.PSpecified(label.getBytes(StandardCharsets.UTF_8))
        );
    }

    static final OAEPParameterSpec OAEP = spec("hybrid-demo:v1|CEK");

    // For a simple demo only: records containing arrays are not immutable.
    record Packet(byte[] wrappedKey, byte[] nonce, byte[] ciphertextAndTag) {}

    static Packet encrypt(PublicKey publicKey, byte[] plaintext)
            throws GeneralSecurityException {
        byte[] cek = new byte[32];      // AES-256 content-encryption key
        byte[] nonce = new byte[12];    // AES-GCM 96-bit nonce

        RNG.nextBytes(cek);
        RNG.nextBytes(nonce);
        try {
            Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
            rsa.init(Cipher.ENCRYPT_MODE, publicKey, OAEP);
            byte[] wrapped = rsa.doFinal(cek);

            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(cek, "AES"),
                    new GCMParameterSpec(128, nonce)
            );
            aes.updateAAD(AAD);
            byte[] ciphertextAndTag = aes.doFinal(plaintext);
            return new Packet(wrapped, nonce, ciphertextAndTag);
        } finally {
            Arrays.fill(cek, (byte) 0);
        }
    }

    static byte[] decrypt(
            PrivateKey privateKey,
            Packet packet,
            OAEPParameterSpec parameters
    ) throws GeneralSecurityException {
        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
        rsa.init(Cipher.DECRYPT_MODE, privateKey, parameters);
        byte[] cek = rsa.doFinal(packet.wrappedKey());

        try {
            if (cek.length != 32) {
                throw new GeneralSecurityException("Invalid CEK length");
            }

            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(cek, "AES"),
                    new GCMParameterSpec(128, packet.nonce())
            );
            aes.updateAAD(AAD);
            return aes.doFinal(packet.ciphertextAndTag());
        } finally {
            Arrays.fill(cek, (byte) 0);
        }
    }

    public static void main(String[] args) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048, RNG);
        KeyPair recipient = gen.generateKeyPair();

        int modulusBytes =
                (((RSAPublicKey) recipient.getPublic()).getModulus().bitLength() + 7) / 8;
        int oaepMax = modulusBytes - 2 * 32 - 2;

        byte[] plaintext = new byte[2000];
        Arrays.fill(plaintext, (byte) 'A');

        Packet packet = encrypt(recipient.getPublic(), plaintext);
        byte[] recovered = decrypt(recipient.getPrivate(), packet, OAEP);

        System.out.println("RSA modulus bytes: " + modulusBytes);
        System.out.println("OAEP-SHA-256 plaintext limit: " + oaepMax);
        System.out.println("Payload bytes: " + plaintext.length);
        System.out.println("Wrapped CEK bytes: " + packet.wrappedKey().length);
        System.out.println("AES-GCM nonce bytes: " + packet.nonce().length);
        System.out.println("Ciphertext plus tag bytes: " + packet.ciphertextAndTag().length);
        System.out.println("Round trip: " + Arrays.equals(plaintext, recovered));

        byte[] tampered = packet.ciphertextAndTag().clone();
        tampered[0] ^= 1;
        boolean tamperRejected = false;
        try {
            decrypt(
                    recipient.getPrivate(),
                    new Packet(packet.wrappedKey(), packet.nonce(), tampered),
                    OAEP
            );
        } catch (AEADBadTagException expected) {
            tamperRejected = true;
        }
        System.out.println("Tampered ciphertext rejected: " + tamperRejected);

        boolean wrongLabelRejected = false;
        try {
            decrypt(recipient.getPrivate(), packet, spec("wrong-context"));
        } catch (GeneralSecurityException expected) {
            wrongLabelRejected = true;
        }
        System.out.println("Wrong OAEP label rejected: " + wrongLabelRejected);

        boolean oversizedRejected = false;
        try {
            Cipher directRsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
            directRsa.init(Cipher.ENCRYPT_MODE, recipient.getPublic(), OAEP);
            directRsa.doFinal(plaintext);
        } catch (GeneralSecurityException expected) {
            oversizedRejected = true;
        }
        System.out.println("Oversized direct RSA rejected: " + oversizedRejected);
    }
}
```

Biên dịch và chạy bằng JDK 17+:

```bash
javac HybridEncryptionDemo.java
java HybridEncryptionDemo
```

**Kết quả stdout:** (đã kiểm tra với JDK 24 qua JShell trên API tương thích Java 17; do RSA key pair, CEK và nonce đều sinh ngẫu nhiên nên không in ciphertext cụ thể):

```text
RSA modulus bytes: 256
OAEP-SHA-256 plaintext limit: 190
Payload bytes: 2000
Wrapped CEK bytes: 256
AES-GCM nonce bytes: 12
Ciphertext plus tag bytes: 2016
Round trip: true
Tampered ciphertext rejected: true
Wrong OAEP label rejected: true
Oversized direct RSA rejected: true
```

Trong đó:
- `OAEP-SHA-256 plaintext limit: 190` đến từ công thức `256 − 2 × 32 − 2`.
- `wrappedKey` có **256 byte** vì RSA modulus 2048 bit, dù CEK ban đầu chỉ 32 byte.
- `Ciphertext plus tag bytes: 2016` vì plaintext 2.000 byte và AES-GCM tag 16 byte; đây là output của `Cipher.doFinal` cho một bản tin không có padding thêm.
- `Round trip: true` chứng minh với cùng key pair, OAEP params, nonce/AAD và ciphertext hợp lệ thì nhận lại cùng byte plaintext.
- Sửa một bit của AES-GCM ciphertext dẫn đến **authentication tag không còn hợp lệ**, do đó việc giải mã bị từ chối.
- Dùng OAEP label `wrong-context` trong lúc decrypt không trùng label encrypt và không giải mã được wrapped CEK.
- Thử encrypt **toàn bộ 2.000 byte bằng RSA-OAEP** gây lỗi và được ghi nhận `true` ở dòng cuối. **Không** tự động cắt đầu vào thành 190 byte.

### 7.1. Những điểm Java quan trọng

**Khởi tạo RSA-OAEP với tham số tường minh:**

```java
Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
rsa.init(Cipher.ENCRYPT_MODE, publicKey, OAEP);
byte[] wrapped = rsa.doFinal(cek);
```

Hãy giữ cùng cấu hình OAEP khi decrypt. Trong Java, cấu hình bằng `OAEPParameterSpec` rõ ràng giúp tránh nhầm default giữa provider. MGF1-SHA-256 là một tham số độc lập với OAEP SHA-256 dù bài học chọn cùng hash.

**AES-GCM phải có nonce và tag:**

```java
Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
aes.init(
    Cipher.ENCRYPT_MODE,
    new SecretKeySpec(cek, "AES"),
    new GCMParameterSpec(128, nonce)
);
aes.updateAAD(AAD);
byte[] ciphertextAndTag = aes.doFinal(plaintext);
```

`GCMParameterSpec(128, nonce)` chỉ định **tag length 128 bit**, không chỉ định AES key length. AES-256 tới từ CEK 32 byte. Không nhầm tham số 128 của tag với việc dùng AES-128.

**Private key chỉ dùng ở receiver:**

```java
rsa.init(Cipher.DECRYPT_MODE, privateKey, OAEP);
byte[] cek = rsa.doFinal(packet.wrappedKey());
```

Không gửi private key qua network hoặc ghi cùng envelope. Public key phải được xác thực trước encrypt. Với hệ thống thật, private key có thể nằm trong KeyStore, HSM hoặc KMS và không nhất thiết export được ra byte.

**So sánh lỗi:** nếu OAEP unwrap thất bại, không có CEK để giải AES-GCM. Nếu unwrap thành công nhưng ciphertext/tag/AAD không phù hợp, AEAD sẽ từ chối. Đừng làm API public trả chi tiết có thể tạo padding oracle hoặc lộ thông tin về khả năng giải mã; cần xử lý lỗi và rate limit theo threat model.

### 7.2. Ranh giới demo và production

Ví dụ deliberately tối giản để học đúng hai trách nhiệm RSA-OAEP và AES-GCM; khi đem vào sản phẩm, cần xử lý thêm:

1. **Khóa RSA:** không tạo mỗi lần khởi động rồi mất private key nếu muốn giải mã dữ liệu lưu trữ lâu dài. Dùng keystore/HSM/KMS và chiến lược backup, rotation, hủy khóa theo chính sách.
2. **Chọn kích thước khóa:** 2048 bit nhằm demo nhanh; chọn 3072 bit hoặc profile khác khi chính sách/độ dài an ninh dữ liệu yêu cầu, dựa trên hướng dẫn cập nhật thay vì copy cứng số bit.
3. **Serialization:** `record Packet` mang `byte[]` có thể bị thay đổi từ bên ngoài. Trong hệ thống thật, dùng immutable copies, validate kích thước, encode/decode theo format chuẩn và giới hạn độ dài payload/envelope.
4. **AAD:** bài demo dùng hằng; nếu có header thay đổi (recipientKeyId, tenantId, resourceId), phải đưa vào AAD theo canonical schema hoặc bảo vệ toàn envelope bằng cơ chế tương đương.
5. **Độ dài/tag:** reader cần kiểm tra wrappedKey length khớp `k`, nonce 12 byte theo profile, có tag đủ độ dài, algorithm suite/version allowlist và không giải mã trước xác minh.
6. **Nonce lifecycle:** sinh nonce mới khi encrypt; tránh lặp cùng CEK. Nếu tái sử dụng CEK và gửi nhiều bản tin, quản lý uniqueness có trạng thái hoặc theo chuẩn phù hợp.
7. **Khóa tạm:** `Arrays.fill(cek,(byte)0)` giảm thời gian giữ bản sao mảng do chương trình sở hữu, nhưng không bảo đảm xóa mọi bản sao nằm trong provider/JVM. Không in CEK, plaintext bí mật, private key hoặc dump bộ nhớ vào log.
8. **Authenticated sender:** RSA-OAEP và AES-GCM trong ví dụ **không xác thực người gửi**. Bổ sung chữ ký/protocol với identity binding nếu yêu cầu.
9. **Đối tượng lớn và streaming:** không đưa toàn bộ file nhiều GB vào một mảng; cần API chunking/streaming được thiết kế đúng cho AEAD, giới hạn nonce/sequence, authentication của từng chunk và bảo vệ thứ tự/tổng số chunk. Không tự biến ví dụ một khối thành một scheme streaming không có spec.
10. **Lỗi từ bên ngoài:** validate framing/version/length trước crypto, trả lỗi không tiết lộ nguyên nhân nhạy cảm và không thử fallback xuống RSA PKCS#1 v1.5 chỉ vì OAEP failed.

## 8. Một ví dụ thiết kế ứng dụng gửi tệp cho người nhận

Giả sử ứng dụng upload một file 10 MiB chỉ người dùng B được đọc.

### 8.1. Quy trình đề xuất

**Bước 1: Xác minh public key của B.** Lấy certificate hoặc public-key mapping từ nguồn đáng tin; kiểm tra identity/keyId và policy. Nếu key bị tráo, tất cả bảo vệ của các bước sau có thể vẫn hoạt động **đối với kẻ tấn công**, không phải B.

**Bước 2: Sinh CEK mới.** Sender dùng CSPRNG tạo 32 byte CEK; không dùng password ngắn hay thời gian để thay CEK.

**Bước 3: Sinh nonce AEAD.** Tạo nonce theo profile AES-GCM; nếu mỗi file có CEK riêng, một nonce ngẫu nhiên 96 bit cho một message là pattern demo hợp lý, nhưng độ bảo đảm uniqueness phải được đánh giá theo số lượng encrypt và chính sách.

**Bước 4: Mã hóa file bằng AES-GCM.** Chọn AAD ràng buộc format version, fileId, chủ sở hữu, context. Nhận ciphertext + tag. Với file lớn, dùng thư viện streaming AEAD có spec phù hợp, không tự mã hóa từng chunk rồi ghép một cách không xác thực.

**Bước 5: Wrap CEK cho B bằng RSA-OAEP.** Khóa cần wrap là 32 byte, thấp hơn giới hạn 190 byte của RSA-2048 OAEP SHA-256. Chuẩn hóa OAEP digest, MGF1 digest và label.

**Bước 6: Lưu một envelope.** Bao gồm metadata cần thiết, wrappedCEK, nonce, ciphertext/tag, version và AAD profile. Không lưu CEK thuần hoặc private key cùng envelope.

**Bước 7: Recipient B giải mã.** B chọn private key đáng tin, unwrap CEK qua RSA-OAEP, decrypt AES-GCM, kiểm tra tag rồi mới sử dụng plaintext. Nếu sai, từ chối; không bỏ qua tag hoặc “thử một cipher khác” tùy tiện.

**Bước 8: Kiểm tra người gửi nếu có yêu cầu.** Nếu ứng dụng còn muốn B biết file thực sự do A gửi, bổ sung chữ ký A trên envelope được canonicalize và xác minh chứng thư/key của A, hoặc tích hợp một giao thức authenticated encryption dành cho nhiều bên theo chuẩn.

### 8.2. Chi phí kích thước

Với file 10 MiB (10 × 1.048.576 byte), AES-GCM với tag 16 byte thêm lượng nhỏ cho một bản tin (chưa tính nonce/header). RSA-OAEP ciphertext wrap một CEK chỉ thêm **256 byte** nếu modulus là 2048 bit.

Ngược lại, chia một file thành các block OAEP 190 byte và encrypt mỗi block sẽ phải tạo rất nhiều RSA ciphertext 256 byte, vừa tốn CPU vừa tăng overhead lớn. Không có lý do vận hành tốt để chọn block-RSA thay cho CEK + AES-GCM trong ứng dụng mã hóa tệp thông thường.

## 9. RSA-OAEP không cung cấp tất cả bảo đảm của một kênh an toàn

### 9.1. Confidentiality không đồng nghĩa sender authentication

RSA-OAEP giúp chỉ private key tương ứng có thể lấy lại CEK (theo giả định an ninh và quản lý khóa đúng). Tuy nhiên ai biết public key đều có thể:
- tự sinh CEK,
- tự mã hóa thông điệp với AES-GCM,
- tự wrap CEK bằng public key,
- gửi một envelope giải mã hợp lệ.

Vì vậy, người nhận không thể suy ra “A chắc chắn đã gửi” chỉ từ việc decrypt thành công. Thêm chữ ký số/cert/identity binding, hoặc sử dụng một protocol hoàn chỉnh có tính xác thực nguồn.

Một hybrid envelope không có chữ ký cũng không phải “bảo đảm chống replay”. Đối thủ có thể lưu lại một envelope hợp lệ rồi gửi lại; phía nhận cần id, timestamp, nonce/replay tracking và policy nghiệp vụ nếu hành động lặp nguy hiểm.

### 9.2. Forward secrecy và lộ private key sau này

Trong mô hình **RSA-OAEP wrap CEK** lưu kèm ciphertext, nếu một ngày RSA private key của B bị lộ, attacker đã thu thập được các wrappedCEK trước đó có thể unwrap CEK rồi giải mã các ciphertext lịch sử. Đây **không phải** một mô hình mặc nhiên có perfect forward secrecy.

Nếu hệ thống cần bảo vệ quá khứ trước việc lộ long-term key, cần một thiết kế **ephemeral key agreement/authenticated KEM** hoặc giao thức có forward secrecy được chứng minh và quản lý vòng đời khóa phù hợp. Không gắn nhãn “PFS” cho RSA-OAEP hybrid chỉ vì CEK ngẫu nhiên mỗi file.

### 9.3. Chống thay thế public key là một yêu cầu riêng

Một attacker làm giả kho public key có thể thay key B bằng key của attacker trước lúc sender encrypt. Khi đó ciphertext được mã hóa đúng bằng một key RSA, AES-GCM vẫn hợp lệ, nhưng attacker chính là người có thể unwrap. Không có phương trình OAEP nào tự kiểm tra tên B. Key authenticity phải đến từ PKI hoặc cơ chế tin cậy ngoài RSA encryption.

### 9.4. RSA-OAEP decryption cần xử lý lỗi kín

Không thiết kế endpoint cho phép người lạ gửi vô hạn wrappedKey rồi quan sát những lỗi quá chi tiết, thời gian phản hồi hoặc việc server có tiếp tục tới AES-GCM hay không. Chỉ dùng provider crypto đã có các biện pháp thích hợp, giới hạn request và thống nhất mức thông tin lỗi trong API public.

Không tự viết RSA raw modular exponentiation để “debug padding” trên input không tin cậy; không chuyển qua `RSA/ECB/PKCS1Padding` khi OAEP thất bại. Các kiểu oracle kiểu Bleichenbacher/padding oracle là lý do phải nghiêm túc với việc dùng API và giao thức phù hợp.

## 10. Các lỗi phổ biến và cách sửa

| Ngộ nhận/thiết kế | Tại sao sai | Hướng đúng |
| --- | --- | --- |
| “RSA-2048 mã hóa trực tiếp 256 byte plaintext” | 256 là ciphertext/modulus byte count; OAEP SHA-256 còn overhead | Dùng `max = k - 2hLen - 2`, ví dụ 190 byte |
| “String có 190 ký tự là dưới giới hạn” | OAEP nhận UTF-8 bytes, không phải Java UTF-16 length | Tính `getBytes(UTF_8).length` |
| “Chia file thành nhiều RSA blocks rồi ghép” | Chậm, phình kích thước, không bảo vệ framing/integrity file đúng cách | RSA-OAEP wrap CEK, AES-GCM encrypt bulk |
| “Dùng raw RSA/no padding cho nhanh” | Không phải encryption scheme an toàn | RSAES-OAEP qua thư viện chuẩn |
| “Hai bên cùng nói SHA-256 OAEP là đủ” | MGF1 digest/label có thể khác | Ghi rõ `OAEPParameterSpec`, test interop |
| “OAEP label là IV ngẫu nhiên” | Label là context công khai; IV/nonce của AEAD là tham số khác | Giữ đúng label byte và nonce riêng |
| “Dùng RSA OAEP encryption để chứng minh A gửi” | Bất cứ ai có public key B đều encrypt được | Dùng chữ ký/identity binding riêng |
| “AES-GCM không cần tag nếu plaintext trông đúng” | Mất kiểm tra toàn vẹn, dễ chấp nhận bản rõ bị sửa | Kiểm tra tag bắt buộc, fail closed |
| “Có GCM tag nên mọi envelope metadata đều an toàn” | Chỉ dữ liệu được bind vào AAD/ciphertext mới được kiểm tra | Canonicalize/bind các field có ý nghĩa |
| “AES-GCM có thể lặp nonce miễn plaintext khác” | Reuse cùng key/nonce là lỗi nghiêm trọng | Enforce nonce uniqueness dưới cùng key |
| “Mã hóa file bằng public key bất kỳ là đủ” | Attacker tráo public key → decrypt được | Xác thực key thuộc người nhận |
| “Tạo key pair mỗi lần chạy vẫn mở được file cũ” | Mất private key → không unwrap được CEK | Persist bảo vệ và backup/rotation theo policy |
| “Sai OAEP thì thử PKCS1 v1.5 fallback” | Làm yếu chính sách, có thể tạo oracle | Từ chối rõ theo version, không downgrade |
| “Random CEK nghĩa là có forward secrecy” | Long-term RSA private key bị lộ có thể unwrap quá khứ | Chọn protocol ephemeral phù hợp nếu cần PFS |
| “WrappedCEK là bí mật không được lưu” | Wrapped key là RSA ciphertext cần lưu/truyền | Lưu wrappedCEK, không lưu CEK thuần |
| “GCMParameterSpec(128) nghĩa là AES-128” | 128 là tag bit length; key length do CEK | Dùng CEK 32 byte cho AES-256 |
| “RSA có thể streaming trực tiếp dữ liệu lớn” | Một OAEP operation chỉ chứa payload rất nhỏ | Streaming/AEAD format chuẩn cho file |
| “Mã hóa lai là RSA-KEM bất kể scheme nào” | OAEP key transport khác RSA-KEM | Gọi đúng scheme, không trộn byte format |

## 11. Các tình huống thực hành để phân loại

**Tình huống A — API payload 8 KiB cần lưu bí mật cho một người nhận.** RSA-2048 OAEP SHA-256 giới hạn 190 byte, nên không encrypt payload trực tiếp. Hãy tạo CEK AES ngẫu nhiên, mã hóa JSON bằng AES-GCM và wrap CEK bằng RSA-OAEP. Bảo vệ AAD của context, xác thực recipient public key.

**Tình huống B — Client muốn gửi khóa AES-256 (32 byte) cho server.** RSA-OAEP có thể encrypt 32 byte CEK vì 32 ≤ 190. Cần đúng OAEP hash/MGF1/label, public key đã xác thực, envelope version và giải AES-GCM an toàn. Nhưng nếu mục đích là truyền API qua mạng thông thường, hãy cân nhắc **TLS được cấu hình đúng** thay vì tự xây application protocol nếu không cần encryption at rest riêng.

**Tình huống C — Sender và recipient đều dùng RSA-OAEP SHA-256 nhưng decryption thất bại.** Hãy kiểm tra MGF1 digest, OAEP label bytes, key pair, ciphertext encoding (hex/Base64/byte length), provider restrictions và format version. Không tự động đổi về SHA-1 hoặc RSA PKCS1 v1.5 để “thử”.

**Tình huống D — Ciphertext GCM bị sửa một byte.** Receiver vẫn có thể unwrap CEK hợp lệ, nhưng AES-GCM tag sẽ không khớp khi decrypt, nên reject. Không dùng plaintext chưa verify; không suy ra chính xác ai đã sửa và khi nào chỉ từ tag.

**Tình huống E — Wrapped CEK bị sửa một byte.** OAEP decryption rất có thể bị từ chối; hoặc nếu nhận được một CEK không phù hợp theo giả định cực hiếm, AES-GCM tag vẫn không khớp. Không nên dựa vào lỗi OAEP chi tiết để trả thông tin về private key cho client.

**Tình huống F — Lộ private key B sau năm năm.** Attacker đã lưu hybrid envelopes cũ có thể giải mã lại các wrappedCEK bằng key đó, nên dữ liệu lịch sử có thể bị ảnh hưởng. Các envelope chỉ dùng RSA-OAEP không tự có forward secrecy.

**Tình huống G — B nhận được một hybrid envelope decrypt hợp lệ, không có chữ ký.** B biết ciphertext/AAD phù hợp với CEK và định dạng, không biết chắc nguồn gửi A; mọi bên có public key B cũng có thể tạo một envelope như vậy.

**Tình huống H — Một file 50 GiB, muốn mã hóa theo chunks.** Không lặp RSA-OAEP cho từng chunk; xây dựng protocol AEAD streaming đã chuẩn hóa, mỗi chunk có nonce/sequence riêng, xác thực thứ tự và điểm kết thúc, rồi wrap một hoặc nhiều CEK theo đúng cấu trúc khóa. Không lấy code demo byte[] làm giải pháp streaming hoàn chỉnh.

## 12. Câu hỏi tự kiểm tra

**1. RSA-2048, OAEP SHA-256, plaintext tối đa là bao nhiêu byte?**  
**Đáp án:** `k=256`, `hLen=32` nên `mLen ≤ 256−64−2 = 190` byte.

**2. RSA-3072 với OAEP SHA-512 thì giới hạn là bao nhiêu?**  
**Đáp án:** `k=384`, `hLen=64`, giới hạn `384−128−2 = 254` byte.

**3. RSA ciphertext của CEK 32 byte dưới RSA-2048 dài bao nhiêu?**  
**Đáp án:** 256 byte; chiều dài ciphertext OAEP theo modulus, không bằng chiều dài CEK.

**4. Vì sao RSA-OAEP không được dùng để encrypt thẳng file 1 MiB?**  
**Đáp án:** Mỗi operation OAEP chỉ chứa payload ngắn, RSA tốn CPU và không thay thế AEAD bảo vệ toàn tệp. Dùng CEK ngẫu nhiên và AES-GCM cho dữ liệu, RSA-OAEP wrap CEK.

**5. Hai bên chọn OAEP hash SHA-256, nhưng sender MGF1-SHA-256 còn receiver MGF1-SHA-1. Có giải mã được không?**  
**Đáp án:** Không theo cùng bộ tham số. OAEP và MGF1 digest cần trùng đúng; chỉ nói “OAEP SHA-256” có thể chưa đủ mô tả interoperable spec.

**6. OAEP label `L` có phải được giữ bí mật?**  
**Đáp án:** Không; nó là context byte tùy chọn. Tuy nhiên encrypt và decrypt phải dùng cùng label và cùng encoding.

**7. RSA-OAEP + AES-GCM có chứng minh A là người gửi không?**  
**Đáp án:** Không. Người có public key B bất kỳ cũng tạo được envelope cho B. Muốn sender authentication cần ký/chứng thực nguồn hoặc protocol phù hợp.

**8. Mất GCM nonce nhưng còn private RSA key và wrapped CEK, có giải mã được file không?**  
**Đáp án:** Không thể khôi phục đúng bằng quy trình chuẩn nếu thiếu nonce cần cho GCM decryption; phải lưu nonce/metadata cần thiết.

**9. Nếu RSA private key bị lộ trong tương lai, dữ liệu cũ có PFS chỉ vì mỗi file dùng CEK mới không?**  
**Đáp án:** Không. WrappedCEK cũ được encrypt bằng cùng RSA public key có thể bị unwrap bởi private key bị lộ; cần ephemeral protocol phù hợp nếu yêu cầu forward secrecy.

**10. Trong Java, tại sao `GCMParameterSpec(128, nonce)` không nói rằng AES-128 đang được dùng?**  
**Đáp án:** `128` là độ dài tag tính bằng bit. Khóa AES được quyết định bởi số byte CEK; CEK 32 byte nghĩa là AES-256.

**11. Nếu encrypt cùng CEK với cùng GCM nonce cho hai plaintext khác nhau thì sao?**  
**Đáp án:** Vi phạm nghiêm trọng yêu cầu nonce uniqueness của GCM và có thể làm suy yếu tính bí mật/toàn vẹn; cần thiết kế quản lý nonce đúng.

**12. Có thể đổi algorithm từ OAEP sang PKCS#1 v1.5 nếu một bản tin giải mã thất bại không?**  
**Đáp án:** Không. Hợp đồng algorithm/version phải cố định, có allowlist, chống downgrade và xử lý lỗi an toàn.

## 13. Checklist triển khai RSA-OAEP hybrid trước khi phát hành

### Bên gửi

- [ ] Đã xác thực public key thuộc đúng recipient, kiểm tra vòng đời/thu hồi/algorithm policy.
- [ ] Đã thống nhất RSA modulus, OAEP hash, MGF1 digest, label encoding và version.
- [ ] Dùng `SecureRandom` tạo **CEK ngẫu nhiên 32 byte** hoặc key size theo cipher được phép.
- [ ] Dùng AEAD phù hợp (ví dụ AES-GCM) với nonce policy an toàn, AAD đã ràng buộc context.
- [ ] RSA-OAEP chỉ encrypt CEK/payload ngắn trong giới hạn `k - 2hLen - 2`.
- [ ] Lưu/truyền wrappedCEK, nonce, ciphertext, tag, version, keyId và metadata cần thiết.
- [ ] Không log CEK/private data; xóa bản sao mảng byte có thể xóa theo best effort.
- [ ] Nếu cần biết tác giả, đã có chữ ký/identity binding bên ngoài confidentiality.

### Bên nhận

- [ ] Áp allowlist version/cipher/keyId, kích thước envelope, và không downgrade khi lỗi.
- [ ] Lấy đúng private key đã cấp phép từ KeyStore/KMS/HSM, không nhận private key từ payload.
- [ ] Kiểm tra đúng OAEP digest + MGF1 digest + label; xem xét opaque error responses.
- [ ] Unwrap CEK, kiểm tra key length và data encryption algorithm đúng profile.
- [ ] Kiểm tra nonce, AAD và authentication tag; không dùng plaintext khi tag chưa hợp lệ.
- [ ] Kiểm tra sender/tenant/resource/authorization/replay theo yêu cầu nghiệp vụ.
- [ ] Không trả lỗi giải mã quá chi tiết tạo oracle hoặc rò rỉ metadata nhạy cảm.
- [ ] Có kế hoạch key rotation, backup và khả năng giải mã dữ liệu lịch sử theo quyền.

### Kiểm thử liên thông và security regression

- [ ] RSA 2048 OAEP SHA-256: encrypt CEK 32 byte thành công; thử plaintext 190 byte thành công, 191 byte bị từ chối.
- [ ] Wrong label / wrong MGF1 digest / wrong private key / malformed wrappedKey bị từ chối.
- [ ] Sai/cắt tag AES-GCM, thay đổi AAD/nonce/ciphertext bị từ chối.
- [ ] Cùng plaintext encrypt hai lần có thể tạo ciphertext khác do randomness.
- [ ] Key rotation và giải mã dữ liệu lưu trữ xử lý đúng version/keyId.
- [ ] JSON/CBOR/binary encoding, Base64URL, byte order, nonce/tag lengths có golden vectors giữa hai bên.
- [ ] Kiểm tra flow danh tính, quyền, key substitution và replay riêng khỏi việc cryptography “pass test”.
- [ ] Kiểm tra giới hạn file, streaming/chunk framing, áp lực memory/CPU và side-channel/error behavior.

## 14. Ghi nhớ bằng một sơ đồ

```text
                  RSA-OAEP SHA-256 (RSA-2048)
                  max plaintext = 190 bytes
                          |
                     CEK 32 bytes  <---- SecureRandom
                          |
         +----------------+--------------------+
         |                                     |
    RSA-OAEP(public)                       AES-256-GCM
         |                              + nonce 12 bytes
    wrappedCEK 256 bytes                 + AAD + plaintext
         |                                     |
         +----------------+--------------------+
                          |
                   Hybrid envelope
               [wrappedCEK, nonce,
                ciphertext+tag, metadata]
                          |
               RSA-OAEP(private) -> CEK
                          |
              AES-GCM decrypt + verify tag
                          |
                       Plaintext

  Key principle:
  RSA protects a short symmetric key;
  AEAD protects large data and authenticates ciphertext/AAD.
  Neither alone proves who sent the message.
```

**Kết luận:** RSA-OAEP có giới hạn bản rõ nhỏ và được dùng đúng nhất cho **thông điệp ngắn hoặc khóa đối xứng**, không phải để mã hóa hàng nghìn khối dữ liệu trực tiếp. Với RSA-2048/OAEP SHA-256, giới hạn là **190 byte**, đủ để wrap một CEK AES-256 dài 32 byte. Mã hóa lai kết hợp **RSA-OAEP cho khóa** và **AEAD cho dữ liệu** giải quyết tốt yêu cầu dung lượng, hiệu năng và toàn vẹn. Một thiết kế hoàn chỉnh còn phải có **định dạng, tham số OAEP, trust của public key, binding metadata, quản lý nonce và chính sách xác thực/ngăn replay** rõ ràng.

### Tài liệu tham khảo

- RFC 8017, *PKCS #1: RSA Cryptography Specifications Version 2.2* — RSAES-OAEP, công thức `mLen ≤ k−2hLen−2` và encoding: https://www.rfc-editor.org/rfc/rfc8017
- NIST SP 800-56B Rev. 2 — các phương pháp thiết lập khóa dựa trên integer-factorization cryptography: https://csrc.nist.gov/pubs/sp/800/56/b/r2/final
- NIST SP 800-38D — GCM/GMAC, AEAD nonce và tag: https://csrc.nist.gov/pubs/sp/800/38/d/final
- NIST SP 800-57 Part 1 Rev. 5 — quản lý khóa và ước lượng độ mạnh khóa: https://csrc.nist.gov/pubs/sp/800/57/pt1/r5/final
- Java SE 17, `OAEPParameterSpec`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/OAEPParameterSpec.html
- Java SE 17, `MGF1ParameterSpec`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/spec/MGF1ParameterSpec.html
- Java SE 17, `Cipher`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Cipher.html
- Java SE 17, `GCMParameterSpec`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html
- Java SE 17, `SecureRandom`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/SecureRandom.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="public-key-cryptography-008">8.8 Cấu hình OAEPParameterSpec, digest, MGF1 và label thống nhất giữa hai phía</a>

<details>
<summary>Xem chi tiết</summary>

# 8.8 Cấu hình OAEPParameterSpec, digest, MGF1 và label thống nhất giữa hai phía

## Mục tiêu học tập

Sau bài này, bạn sẽ:

1. Giải thích tại sao RSA-OAEP cần **bộ tham số thống nhất** chứ không chỉ thống nhất “RSA, SHA-256”.
2. Phân biệt **OAEP digest**, hàm tạo mặt nạ **MGF1**, **digest bên trong MGF1** và **label (PSource.PSpecified)**.
3. Sử dụng **javax.crypto.spec.OAEPParameterSpec** trong Java 17+ để quy định tường minh cả ba phần, thay vì trông chờ mặc định theo tên transformation hoặc provider.
4. Thực hiện Java 17 RSA-OAEP encrypt/decrypt, kiểm tra cấu hình có hiệu lực, quan sát lỗi khi sai digest, MGF1 digest hoặc label.
5. Thiết kế hợp đồng tương tác Java ↔ Java hay Java ↔ hệ thống khác, xử lý giới hạn kích thước RSA và đưa RSA-OAEP vào mô hình **hybrid encryption** an toàn.

**Điểm nhớ chính:** Hai bên chỉ giải mã RSA-OAEP tương thích nếu **khóa RSA đúng, OAEP hash đúng, MGF đúng, MGF1 hash đúng, label giống hệt từng byte**, đồng thời thống nhất kích thước khóa và định dạng ciphertext. Riêng chuỗi transformation có tên SHA-256 **không đủ để khẳng định MGF1 cũng dùng SHA-256** trên mọi provider.

## 1. Vì sao RSA phải có OAEP?

RSA là thuật toán khóa công khai: bên mã hóa dùng **public key** của người nhận, bên giải mã dùng **private key** tương ứng. RSA nguyên thủy không thể dùng trực tiếp cho dữ liệu ứng dụng với một kiểu “RSA encrypt(raw message)” tùy ý. Việc mã hóa an toàn cần **cơ chế encoding/padding được thiết kế cho RSA encryption**.

**RSAES-OAEP (Optimal Asymmetric Encryption Padding)** là lược đồ mã hóa RSA có ngẫu nhiên hóa theo PKCS #1 / RFC 8017. Nó kết hợp:

- Chuỗi byte thông điệp đầu vào M.
- Một **hash OAEP** dùng trong quá trình cấu trúc khối dữ liệu (đặc biệt tính hash của label).
- Một **mask generation function**, thường là MGF1, với thuật toán hash riêng của MGF1.
- Một label L (có thể rỗng).
- Một seed ngẫu nhiên bên trong OAEP.

Bản mã RSA-OAEP khác nhau giữa các lần mã hóa ngay cả khi public key và plaintext không đổi, bởi OAEP dùng ngẫu nhiên. Người gửi không cần private key để mã hóa; người nhận phải có private key để giải mã.

~~~text
Bên A / Sender                                 Bên B / Recipient

Public key của B                               Private key của B
Plaintext M                                    Ciphertext C
OAEP params P                                  OAEP params P
      |                                              |
  RSAES-OAEP encrypt                               RSAES-OAEP decrypt
      |                                              |
      +---------------- C ------------------------->+
                                                     |
                                               M hoặc lỗi giải mã

P = (OAEP hash, MGF=MGF1, MGF1 hash, label bytes)
~~~

Bên mã hóa và bên giải mã phải dùng **cùng OAEP params P**, nhưng key khác vai trò: public key phía mã hóa, private key phía giải mã.

**Không nhầm OAEP với chữ ký số:** RSA-OAEP là lược đồ **mã hóa** cho người nhận. Bất kỳ ai có public key cũng có thể mã hóa thông điệp cho người nhận; OAEP không tự chứng minh danh tính người gửi. RSA-PSS là cơ chế **chữ ký số** khác, cũng có MGF1 nhưng dùng **PSSParameterSpec**, không phải OAEPParameterSpec.

## 2. Bốn trường quan trọng trong OAEPParameterSpec

Trong Java, class **javax.crypto.spec.OAEPParameterSpec** nhận bốn đối số:

~~~java
new OAEPParameterSpec(
    String mdName,               // Hash chính của OAEP
    String mgfName,              // Tên MGF, thường "MGF1"
    AlgorithmParameterSpec mgfSpec, // Digest bên trong MGF1
    PSource pSrc                 // Label, thường PSource.PSpecified
);
~~~

Đây là chữ ký constructor mang tính giải thích, không phải chương trình độc lập. Để dùng thật, cần đối tượng, import phù hợp và truyền spec cho **Cipher.init(...)**.

| Tham số | Ý nghĩa | Ví dụ tường minh |
| --- | --- | --- |
| **mdName** | Hash chính trong thuật toán OAEP, dùng để tạo lHash của label và xác định độ dài hLen | "SHA-256" |
| **mgfName** | Thuật toán sinh mặt nạ; với lược đồ phổ biến là MGF1 | "MGF1" |
| **mgfSpec** | Tham số của MGF1, thường **MGF1ParameterSpec** chứa hash mà MGF1 dùng | MGF1ParameterSpec.SHA256 |
| **pSrc** | Nguồn encoding input P, trong trường hợp thông thường là **PSpecified(label bytes)** | new PSource.PSpecified(labelBytes) |

### 2.1 OAEP digest và MGF1 digest là **hai lựa chọn riêng**

Ví dụ:

~~~java
OAEPParameterSpec p = new OAEPParameterSpec(
    "SHA-256",
    "MGF1",
    MGF1ParameterSpec.SHA256,
    PSource.PSpecified.DEFAULT
);
~~~

Ở đây:

- OAEP hash = **SHA-256**.
- MGF = **MGF1**.
- MGF1 hash = **SHA-256**.
- Label = **byte[0]** (rỗng).

Cũng có cấu hình OAEP hash = SHA-256 nhưng MGF1 hash = SHA-1. Hai cấu hình này **không tương thích để giải mã bản mã của nhau**, dù cùng được mô tả lỏng lẻo là “RSA OAEP SHA-256”.

Đừng suy luận MGF1 digest bằng cách nhìn mỗi tên **OAEPWithSHA-256AndMGF1Padding**. Tên đó nêu hash OAEP và tên MGF, nhưng **không ghi rõ hash nội bộ của MGF1**.

**Cấu hình tương thích cần chỉ ra cả hai hash độc lập.** Việc dùng cả hai là SHA-256 là một lựa chọn nhất quán thường dùng cho hệ thống mới khi hai phía đều hỗ trợ, nhưng yêu cầu cao nhất vẫn là thỏa thuận theo giao thức và xác minh thực tế.

### 2.2 OAEPParameterSpec.DEFAULT có ý nghĩa gì?

Java SE 17 mô tả **OAEPParameterSpec.DEFAULT** là:

~~~text
OAEP digest = SHA-1
MGF         = MGF1
MGF1 digest = SHA-1
PSource     = PSpecified.DEFAULT = empty byte[]
~~~

Đây là **giá trị mặc định của object OAEPParameterSpec.DEFAULT**, không phải lời khẳng định rằng mọi transformation Java đều dùng các giá trị này khi không có params. Provider và transformation có thể áp dụng các mặc định riêng.

Với dịch vụ mới, **không nên lựa chọn SHA-1 chỉ vì nó là default trong spec**. Hãy thiết kế rõ thuật toán được phép và dùng các thông số an toàn phù hợp yêu cầu hiện tại, ví dụ SHA-256/MGF1-SHA-256.

Nguồn: [Oracle Java SE 17 — OAEPParameterSpec](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/OAEPParameterSpec.html).

### 2.3 “ECB” trong RSA transformation không có nghĩa là block mode ECB của AES

Java JCA có tên transformation phổ biến:

~~~text
RSA/ECB/OAEPPadding
RSA/ECB/OAEPWithSHA-256AndMGF1Padding
~~~

Đối với RSA trong các tên này, **ECB** là phần quy ước tên mode của API, không phải khuyến nghị dùng AES-ECB hoặc kiểu chia dữ liệu thành khối ECB. RSA-OAEP xử lý một thông điệp có kích thước bị giới hạn theo RSA modulus. Không dùng quy tắc ECB của mật mã khối để suy ra tính an toàn hoặc cách stream RSA.

Khi viết code có tính tương tác, có thể chọn **RSA/ECB/OAEPPadding** với OAEPParameterSpec tường minh. Vấn đề quyết định là **bộ tham số thực tế đã truyền vào Cipher.init**, hỗ trợ của provider và kiểm tra giao tiếp giữa hai bên.

## 3. MGF1 là gì và tại sao hash của nó quan trọng?

**MGF (Mask Generation Function)** sinh byte mặt nạ từ seed và độ dài yêu cầu. **MGF1** dùng một hàm hash để tạo mặt nạ có độ dài tùy yêu cầu theo cách được quy định trong PKCS #1.

Ở mức trực giác:

~~~text
MGF1(seed, length, Hash)
            |
    Hash(seed || counter0)
    Hash(seed || counter1)
    Hash(seed || counter2)
            |
  Nối byte rồi lấy đúng độ dài yêu cầu
            |
         mask bytes
~~~

Đây chỉ là sơ đồ khái niệm; ứng dụng không tự lập trình lại MGF1. **java.security.spec.MGF1ParameterSpec** mô tả hash cho MGF1, chẳng hạn **MGF1ParameterSpec.SHA256**.

Sự khác nhau giữa OAEP hash và MGF1 hash là **thành phần giao thức thực**, không phải cách viết khác của cùng tham số:

| Cấu hình | OAEP hash | MGF1 hash | Label |
| --- | --- | --- | --- |
| P1 | SHA-256 | SHA-256 | rỗng |
| P2 | SHA-256 | SHA-1 | rỗng |
| P3 | SHA-256 | SHA-256 | bytes UTF-8 của "app:v1" |
| P4 | SHA-256 | SHA-256 | bytes UTF-8 của "app:v2" |

**P1, P2, P3, P4 là các cấu hình khác nhau**; ciphertext tạo dưới một cấu hình thường không giải mã thành công dưới cấu hình khác. Điều này là cơ chế kiểm tra đúng của OAEP, không phải lỗi thư viện.

Khi hai bên trao đổi yêu cầu kỹ thuật, phải ghi rõ **mgfDigest**, không chỉ “MGF1”.

Nguồn: [Oracle Java SE 17 — MGF1ParameterSpec](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/spec/MGF1ParameterSpec.html).

## 4. Label OAEP: PSource.PSpecified và việc thống nhất từng byte

### 4.1 Label là gì?

Trong RSAES-OAEP, **label L** là chuỗi byte tùy chọn được kết hợp vào phép encoding. OAEP hash tính **lHash = Hash(L)** và đưa vào cấu trúc được kiểm tra khi giải mã. Vì vậy nếu label phía giải mã khác label phía mã hóa, kiểm tra cấu trúc OAEP sẽ thất bại.

Java dùng **PSource.PSpecified**:

~~~java
byte[] label = "app:v1".getBytes(StandardCharsets.UTF_8);

OAEPParameterSpec params = new OAEPParameterSpec(
    "SHA-256",
    "MGF1",
    MGF1ParameterSpec.SHA256,
    new PSource.PSpecified(label)
);
~~~

Đây là đoạn minh họa, cần import các class tương ứng; xem chương trình Java đầy đủ ở phần sau.

Mặc định:

~~~java
PSource.PSpecified.DEFAULT
~~~

tương ứng **label rỗng (byte[0])**. Nó **không** phải chuỗi text "DEFAULT", không phải chuỗi có một ký tự null và không phải mật khẩu bí mật.

### 4.2 Label phải giống **byte**, không chỉ giống chữ nhìn thấy

Ví dụ:

~~~text
UTF-8("app:v1")   = 61 70 70 3a 76 31
UTF-8("app:v2")   = 61 70 70 3a 76 32
UTF-8("app:v1 ")  = 61 70 70 3a 76 31 20
empty label       = (0 byte)
~~~

Chỉ cần khác một byte, kết quả OAEP của hai bên đã không tương thích.

Nếu label có Unicode, cần thống nhất **charset** (thường UTF-8), quy tắc normalization, khoảng trắng, chữ hoa/thường và định dạng. Một chuỗi nhìn giống nhau có thể có biểu diễn Unicode khác, dẫn đến **label bytes khác**.

**Đừng dùng default charset qua getBytes() không tham số** hoặc để một bên mã hóa label thành UTF-8, bên còn lại dùng UTF-16. Đừng tự trim/canonicalize một phía mà không quy định ở phía kia.

### 4.3 Label có phải secret hoặc AAD giống AES-GCM không?

**Không.** Label là tham số công khai của phép OAEP; không phải khóa hay bí mật. Nó tạo một ràng buộc giữa bản mã và chuỗi byte đã chọn, nhưng không chứng minh người gửi và **không cung cấp thay thế cho cơ chế xác thực bản tin/metadata của toàn ứng dụng**.

Đặc biệt:

- Label không mã hóa riêng bất kỳ nội dung nào.
- Label không chứng minh danh tính tác giả ciphertext; ai có public key và biết label đều có thể mã hóa.
- Label khác nhau khiến giải mã thất bại, nhưng không tự ngăn phát lại một ciphertext cũ hợp lệ.
- Với hybrid encryption, **AES-GCM AAD** bảo vệ metadata của payload theo thiết kế tương ứng; OAEP label không tự bảo vệ tất cả trường metadata của gói dữ liệu ứng dụng.
- **RFC 8017 mô tả trường hợp label rỗng** theo cấu hình cơ bản và lưu ý các cách dùng label khác nằm ngoài phạm vi sử dụng mặc định của đặc tả. Trong triển khai, chỉ chọn non-empty label khi cả hai thư viện/giao thức đã xác nhận hỗ trợ.

Nếu tích hợp với bên không hỗ trợ custom label, thường phải thỏa thuận **empty label** từ đầu, không tự ý giả định đối phương dùng string label giống phía Java.

Nguồn: [Oracle — PSource.PSpecified](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/PSource.PSpecified.html), [RFC 8017 §7.1](https://www.rfc-editor.org/rfc/rfc8017.html#section-7.1).

## 5. Một cấu hình OAEP tường minh, thống nhất

Ví dụ hợp đồng giao tiếp (wire contract) có thể ghi:

~~~text
Protocol:              encrypted-key-v1
Asymmetric algorithm:  RSAES-OAEP
RSA modulus:           2048 bits or stronger by policy
OAEP digest:           SHA-256
MGF algorithm:         MGF1
MGF1 digest:           SHA-256
Label bytes:           61 70 70 3a 76 31 (UTF-8 "app:v1")
Ciphertext encoding:   Base64 (RFC 4648 basic, padded)
Public key wire form:  X.509 SubjectPublicKeyInfo (DER or PEM)
Private key storage:   PKCS#8 or secured keystore/KMS
Error handling:        decryption failure -> reject, no plaintext use
~~~

Đây là **ví dụ hợp đồng do ứng dụng tự định nghĩa**, không phải tiêu chuẩn có sẵn mang tên encrypted-key-v1.

Điểm quan trọng là viết **cả ba phần digest/MGF1/label**, thêm cách biểu diễn byte và định dạng khóa. Nếu bên gửi chỉ truyền “SHA-256” hoặc “OAEP”, người nhận vẫn chưa có đủ thông tin để cấu hình chính xác.

### 5.1 Không để dữ liệu bên ngoài tự áp đặt thuật toán

Một bản ghi có thể chứa metadata như version/keyId để bên nhận chọn cấu hình từ **danh sách đã cho phép**. Nhưng không nên lấy một chuỗi do attacker điều khiển rồi dùng trực tiếp làm tên Cipher transformation hoặc tự chấp nhận mode yếu hơn nếu giải mã thất bại.

Tốt hơn:

1. Server có bảng **version → chính sách thuật toán cố định**.
2. Metadata version chỉ chọn trong các version được hỗ trợ.
3. Key ID được kiểm tra thuộc đúng tenant/người nhận/quyền truy cập.
4. Server từ chối version/algorithm lạ; không thử lần lượt hàng loạt cấu hình đến khi “ra plaintext”.
5. Luân chuyển key và phiên bản có quy trình rõ ràng, không bao giờ hạ cấp sang RSA không OAEP chỉ vì bản mã không giải được.

## 6. Java 17+ hoàn chỉnh: đúng spec, sai MGF1, sai label

Tạo tệp **RsaOaepSpecDemo.java** (lưu UTF-8). Chương trình sinh cặp RSA 2048 bit để minh họa, cấu hình OAEP digest SHA-256, MGF1 digest SHA-256, label **app:v1**, và thử ba cấu hình sai.

~~~java
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;
import java.util.HexFormat;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

public class RsaOaepSpecDemo {
    private static final String TRANSFORMATION = "RSA/ECB/OAEPPadding";

    private static OAEPParameterSpec spec(String digest,
                                          MGF1ParameterSpec mgfDigest,
                                          String label) {
        return new OAEPParameterSpec(
                digest,
                "MGF1",
                mgfDigest,
                new PSource.PSpecified(
                        label.getBytes(StandardCharsets.UTF_8))
        );
    }

    private static byte[] encrypt(PublicKey key,
                                  byte[] plaintext,
                                  OAEPParameterSpec params)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key, params);
        return cipher.doFinal(plaintext);
    }

    private static byte[] decrypt(PrivateKey key,
                                  byte[] ciphertext,
                                  OAEPParameterSpec params)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, params);
        return cipher.doFinal(ciphertext);
    }

    private static boolean rejected(PrivateKey key,
                                    byte[] ciphertext,
                                    OAEPParameterSpec wrongParams)
            throws GeneralSecurityException {
        try {
            decrypt(key, ciphertext, wrongParams);
            return false;
        } catch (BadPaddingException expected) {
            return true;
        }
    }

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();

        OAEPParameterSpec correct = spec(
                "SHA-256", MGF1ParameterSpec.SHA256, "app:v1");

        byte[] message = "demo RSA-OAEP"
                .getBytes(StandardCharsets.UTF_8);

        Cipher sender = Cipher.getInstance(TRANSFORMATION);
        sender.init(Cipher.ENCRYPT_MODE, pair.getPublic(), correct);

        // Đọc tham số hiệu lực sau init để tránh chỉ nhìn tên padding.
        AlgorithmParameters effective = sender.getParameters();
        OAEPParameterSpec actual =
                effective.getParameterSpec(OAEPParameterSpec.class);

        System.out.println("OAEP digest: " + actual.getDigestAlgorithm());
        System.out.println("MGF: " + actual.getMGFAlgorithm());
        System.out.println("MGF1 digest: "
                + ((MGF1ParameterSpec) actual.getMGFParameters())
                        .getDigestAlgorithm());
        System.out.println("Label hex: "
                + HexFormat.of().formatHex(
                        ((PSource.PSpecified) actual.getPSource())
                                .getValue()));

        byte[] ciphertext = sender.doFinal(message);
        byte[] restored = decrypt(
                pair.getPrivate(), ciphertext, correct);

        System.out.println("Round-trip: "
                + Arrays.equals(message, restored));
        System.out.println("Ciphertext bytes: " + ciphertext.length);

        OAEPParameterSpec wrongMgf = spec(
                "SHA-256", MGF1ParameterSpec.SHA1, "app:v1");
        OAEPParameterSpec wrongLabel = spec(
                "SHA-256", MGF1ParameterSpec.SHA256, "app:v2");
        OAEPParameterSpec wrongDigest = spec(
                "SHA-1", MGF1ParameterSpec.SHA256, "app:v1");

        System.out.println("Wrong MGF1 rejected: "
                + rejected(pair.getPrivate(), ciphertext, wrongMgf));
        System.out.println("Wrong label rejected: "
                + rejected(pair.getPrivate(), ciphertext, wrongLabel));
        System.out.println("Wrong OAEP digest rejected: "
                + rejected(pair.getPrivate(), ciphertext, wrongDigest));

        // SHA-256 hLen = 32; RSA-2048 k = 256.
        int maxMessageBytes = 256 - 2 * 32 - 2;
        System.out.println("Max message bytes: " + maxMessageBytes);

        boolean overLimitRejected;
        try {
            encrypt(pair.getPublic(),
                    new byte[maxMessageBytes + 1], correct);
            overLimitRejected = false;
        } catch (IllegalBlockSizeException expected) {
            overLimitRejected = true;
        }
        System.out.println("191 bytes rejected: " + overLimitRejected);

        Arrays.fill(message, (byte) 0);
        Arrays.fill(restored, (byte) 0);
    }
}
~~~

Chạy bằng JDK 17+ với SunJCE/RSA provider hỗ trợ các tham số này:

~~~bash
javac -encoding UTF-8 RsaOaepSpecDemo.java
java RsaOaepSpecDemo
~~~

Stdout dự kiến (những dòng sau không phụ thuộc vào ciphertext ngẫu nhiên):

~~~text
OAEP digest: SHA-256
MGF: MGF1
MGF1 digest: SHA-256
Label hex: 6170703a7631
Round-trip: true
Ciphertext bytes: 256
Wrong MGF1 rejected: true
Wrong label rejected: true
Wrong OAEP digest rejected: true
Max message bytes: 190
191 bytes rejected: true
~~~

Giải thích:

1. **actual.getDigestAlgorithm()** trả SHA-256: kiểm tra hash chính OAEP.
2. **((MGF1ParameterSpec) actual.getMGFParameters()).getDigestAlgorithm()** trả SHA-256: kiểm tra hash **bên trong MGF1**, không suy ra từ transformation.
3. **getPSource()** cho biết label thực tế. **6170703a7631** là hex của byte UTF-8 **app:v1**.
4. Public key mã hóa, private key tương ứng giải mã đúng với **cùng spec** nên Round-trip = true.
5. Cố tình đổi **MGF1 SHA-256 → SHA-1**, **label app:v1 → app:v2** hoặc **OAEP SHA-256 → SHA-1** sẽ khiến giải mã thất bại. Ví dụ bắt BadPaddingException khi padding OAEP không hợp lệ.
6. RSA 2048 có modulus dài **256 byte**, nên ciphertext dài **256 byte**. Với SHA-256 hLen = 32 byte, plaintext tối đa **190 byte**; 191 byte vượt giới hạn cho RSA-OAEP theo cấu hình này.

**Lưu ý kỹ thuật:** chương trình tạo cặp RSA ngẫu nhiên mỗi lần chạy nên ciphertext không cố định; kết quả giải mã thành công/thất bại là kết quả mong đợi của cấu hình. Các provider hỗ trợ OAEP khác nhau có thể ném lỗi hoặc từ chối tham số tại thời điểm init khác nhau. Nếu provider không hỗ trợ spec, đó là vấn đề **tương thích cần kiểm tra**, không phải lý do dùng cấu hình mặc định một cách mù quáng.

### 6.1 Tại sao dùng Cipher.doFinal thay vì tự kiểm tra padding?

**RSAES-OAEP có quy trình decode/kiểm tra cấu trúc và label bên trong implementation**. Ứng dụng dùng Cipher.doFinal để nhận plaintext hoặc lỗi; không tự parse mask/seed, kiểm tra byte cấu trúc rồi xuất chi tiết lỗi ra client.

Lỗi OAEP thường biểu hiện bằng **BadPaddingException** khi giải mã. Tùy provider và đầu vào, có thể có exception khác như InvalidKeyException, InvalidAlgorithmParameterException hoặc IllegalBlockSizeException. Trên hệ thống đối mặt với input không tin cậy:

- Từ chối chung với bản mã không hợp lệ, tránh trả các thông báo chi tiết có thể làm oracle.
- Không thử **RSA/ECB/PKCS1Padding** hoặc **RSA/ECB/NoPadding** để “cứu” một bản mã OAEP sai.
- Không coi byte sai label có thể được sửa bằng trim hoặc thay charset sau khi giải mã.
- Không in private key, raw ciphertext nhạy cảm hoặc plaintext vào log chỉ để debug.
- Giới hạn tài nguyên, xác minh định dạng ciphertext và quyền truy cập key trước khi giải mã.

### 6.2 Vì sao chương trình không in ciphertext?

OAEP dùng ngẫu nhiên nên ciphertext của cùng M vẫn khác nhau giữa các lần mã hóa. In ciphertext như một giá trị mẫu rồi bảo người học mong đợi đúng từng byte sẽ không đáng tin. Ví dụ thay bằng **độ dài** và những phép kiểm tra bất biến.

Ngoài ra, **Arrays.fill()** chỉ xóa mảng byte do code quản lý, không đảm bảo xóa mọi bản sao dữ liệu trong JVM. Khóa ví dụ được tạo trong chương trình; ứng dụng thật cần keystore, KMS/HSM và chính sách bảo vệ private key.

## 7. Giới hạn kích thước: RSA-OAEP không dùng để mã hóa file lớn

Theo RFC 8017, với RSAES-OAEP:

~~~text
mLen <= k - 2*hLen - 2

mLen = độ dài plaintext (byte)
k    = độ dài RSA modulus (byte)
hLen = độ dài output của OAEP hash (byte)
~~~

Nếu dùng OAEP SHA-256, hLen = 32:

| Modulus RSA | k (byte) | Plaintext tối đa với OAEP SHA-256 |
| --- | ---: | ---: |
| 2048 bit | 256 | 190 byte |
| 3072 bit | 384 | 318 byte |
| 4096 bit | 512 | 446 byte |

Đây là **số byte**, không phải số ký tự Java String. Một chuỗi Unicode có thể có độ dài UTF-8 lớn hơn String.length().

**MGF1 digest là một tham số riêng**, nhưng trong công thức giới hạn độ dài ở trên, **hLen là hash chính của OAEP**, không phải output length của MGF1 hash. Thay MGF1 SHA-256 bằng SHA-1 không làm công thức k - 2*hLen -2 chuyển theo MGF1.

RSA-OAEP **không dùng để chia file thành các mảnh RSA độc lập tùy ý**. Mã hóa nhiều khối RSA gây overhead và sai lầm giao thức. Thay vì vậy dùng **hybrid encryption**:

~~~text
1. Tạo ngẫu nhiên một khóa AES (DEK).
2. Mã hóa file / dữ liệu bằng AES-GCM với nonce duy nhất.
3. Mã hóa DEK nhỏ bằng public key RSA-OAEP của người nhận.
4. Lưu/gửi:
   version, keyId, OAEP spec id, wrapped DEK,
   GCM nonce, ciphertext + tag, metadata/AAD theo định dạng.
5. Người nhận:
   xác minh version/định dạng và quyền,
   giải mã DEK bằng RSA-OAEP đúng tham số,
   giải mã + xác thực AES-GCM,
   chỉ công bố plaintext khi tag hợp lệ.
~~~

Trong nhiều hệ thống thực tế, KMS/HSM có thao tác **key wrap/unwrap** hoặc envelope encryption chuyên dụng. Khi có thể, ưu tiên giao thức/thư viện mã hóa đã được đánh giá thay vì tự phát minh định dạng phức tạp. OAEP bảo vệ việc trao đổi khóa AES; AES-GCM bảo vệ payload dữ liệu lớn.

**RSA-OAEP là mã hóa với public key, không phải chữ ký số hoặc xác thực người gửi.** Nếu cần xác thực người gửi, thiết kế chữ ký số, MAC hoặc cơ chế xác thực kênh phù hợp. AES-GCM AAD có thể ràng buộc metadata nhưng không tự xác định danh tính người gửi nếu nhiều bên có cùng khóa.

Nguồn: [RFC 8017 — RSAES-OAEP](https://www.rfc-editor.org/rfc/rfc8017.html#section-7.1).

## 8. Liên thông Java ↔ Java và Java ↔ ngôn ngữ khác

### 8.1 Cần thỏa thuận thêm gì ngoài hash?

Khi hai bên nói “OAEP SHA-256”, hãy hỏi rõ:

1. RSAES-OAEP hay cơ chế RSA khác?
2. **OAEP digest** là SHA-256 hay SHA-1?
3. **MGF** có phải MGF1?
4. **MGF1 digest** là SHA-256, SHA-1 hay giá trị khác?
5. **Label** chính xác là bytes nào, hay empty byte[]?
6. **Public key** theo X.509 SubjectPublicKeyInfo, PKCS#1 RSA PUBLIC KEY hay kiểu khác?
7. **Private key** theo PKCS#8, PKCS#1 hay trong HSM/keystore?
8. **Ciphertext** là raw RSA bytes hay Base64/hex của bytes đó? Biến thể Base64 Basic hay URL-safe?
9. **Modulus length** và key ID là gì?
10. Khi lỗi, cách từ chối và không tiết lộ thông tin nội bộ là gì?

**Key encoding không phải OAEP encoding.** OAEP là cấu trúc mã hóa một thông điệp; X.509/PKCS#8/PEM là định dạng lưu/trao đổi khóa; Base64 là biểu diễn byte thành text.

### 8.2 Ví dụ ánh xạ khái niệm giữa hai bên

Giả sử bên A là Java và bên B là một dịch vụ dùng thư viện mật mã khác. Hợp đồng định nghĩa:

~~~text
RSAES-OAEP:
    hashAlgorithm = SHA-256
    mgf = MGF1
    mgf1HashAlgorithm = SHA-256
    label = hex(6170703a7631)

Ciphertext:
    256 raw bytes for RSA-2048
    transferred as Base64 Basic (no line breaks)
~~~

Bên A dùng:

~~~java
new OAEPParameterSpec(
    "SHA-256",
    "MGF1",
    MGF1ParameterSpec.SHA256,
    new PSource.PSpecified(
        "app:v1".getBytes(StandardCharsets.UTF_8))
);
~~~

Bên B phải có tham số tương đương: **OAEP hash SHA-256, MGF1(SHA-256), label bytes 6170703a7631**. Cách gọi hàm cụ thể của bên B tùy thư viện, nhưng ý nghĩa byte/thuật toán phải giống.

Chú ý: Có thư viện mặc định **label rỗng**, có thư viện không hỗ trợ label tùy chọn; một số thư viện cấu hình OAEP hash và MGF1 hash bằng **hai tùy chọn riêng**, số khác có mặc định nội bộ. Vì vậy cần **test liên thông thực tế**, không chỉ so sánh tên thuật toán trong tài liệu.

### 8.3 Tại sao không đoán MGF1 theo transformation?

Ví dụ bên Java gọi:

~~~java
Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
~~~

Chuỗi trên **không biểu đạt tường minh MGF1 digest**. Trong một số triển khai SunJCE, transformation kiểu này đã được ghi nhận dùng **MGF1 SHA-1** nếu không có OAEPParameterSpec do caller chỉ định, trong khi một provider khác có thể dùng SHA-256 cho MGF1. Chính sự khác nhau đó làm xuất hiện lỗi “cả hai đều ghi SHA-256 nhưng không giải mã được”.

Cách chắc chắn:

- Tạo **OAEPParameterSpec** với digest SHA-256 và **MGF1ParameterSpec.SHA256** nếu đó là hợp đồng.
- Truyền cùng spec vào **Cipher.init(...)** cho cả encrypt/decrypt.
- Có thể đọc lại **Cipher.getParameters().getParameterSpec(OAEPParameterSpec.class)** sau init để kiểm tra provider thực nhận gì, khi provider hỗ trợ trả về parameters.
- Kiểm thử với một ciphertext cố định được sinh từ phía khác và giải mã ngược lại; test cả sai MGF digest, sai label.
- Nếu phải tương thích hệ thống legacy có MGF1 SHA-1, chọn thông số legacy **một cách tường minh, có version và kế hoạch chuyển đổi**, không để mặc định tình cờ quyết định.

### 8.4 Label null, empty và văn bản có nghĩa khác nhau

Trong một số API ngoại ngữ, **null** có thể được thư viện diễn giải là **empty label**. Nhưng **trong Java**, cách thể hiện rõ nhất để chỉ label rỗng là **PSource.PSpecified.DEFAULT** hoặc một **new PSource.PSpecified(new byte[0])**.

Không truyền một chuỗi văn bản "null" hoặc "DEFAULT" thay cho empty label. Nếu hợp đồng sử dụng label "app:v1", phải chuyển đúng byte UTF-8; không để bên A dùng app:v1 trong khi bên B dùng app:v1 kèm newline.

### 8.5 Chuyển public/private key qua định dạng

Java có thể dùng:

~~~java
// Ví dụ minh họa phụ thuộc vào biến encodedPublic/encodedPrivate.
PublicKey pub = KeyFactory.getInstance("RSA").generatePublic(
    new X509EncodedKeySpec(encodedPublic));

PrivateKey priv = KeyFactory.getInstance("RSA").generatePrivate(
    new PKCS8EncodedKeySpec(encodedPrivate));
~~~

Đây là đoạn minh họa, không phải chương trình độc lập; cần import **java.security.KeyFactory**, **java.security.PublicKey/PrivateKey** và các KeySpec tương ứng.

**X509EncodedKeySpec** là dạng X.509 SubjectPublicKeyInfo được Java hỗ trợ phổ biến; **PKCS8EncodedKeySpec** là PKCS#8. Một số file từ bên thứ ba có nhãn PEM kiểu **BEGIN RSA PUBLIC KEY** hoặc **BEGIN RSA PRIVATE KEY** theo cấu trúc PKCS#1 thay vì SubjectPublicKeyInfo/PKCS#8; không thể tự đưa DER PKCS#1 vào X509EncodedKeySpec hoặc PKCS8EncodedKeySpec rồi mong luôn hoạt động. Cần chuyển định dạng an toàn hoặc dùng parser thích hợp.

Không ghi private key bytes vào log hoặc mã nguồn. Public key cần được xác thực nguồn gốc (certificate, trust store, pinning/management) khi có nguy cơ bị kẻ tấn công thay thế bằng public key của họ.

## 9. Ma trận kiểm thử tương thích OAEP

| Trường hợp | Kỳ vọng |
| --- | --- |
| Đúng keypair, SHA-256, MGF1 SHA-256, cùng label | Giải mã chính xác plaintext |
| Đúng keypair, OAEP SHA-256 nhưng MGF1 SHA-1 phía nhận | Giải mã thất bại |
| Đúng keypair, MGF1 SHA-256 nhưng OAEP hash SHA-1 phía nhận | Giải mã thất bại |
| Đúng hash/MGF1 nhưng label khác một byte | Giải mã thất bại |
| Bên gửi empty label, bên nhận non-empty label | Giải mã thất bại |
| Cùng nhãn text nhưng charset/Unicode normalization khác | Có thể giải mã thất bại do bytes khác |
| Sai private key | Giải mã thất bại |
| Ciphertext bị sửa hoặc cắt | Giải mã thất bại, không dùng plaintext |
| Thông điệp 190 byte, RSA-2048 + OAEP SHA-256 | Trong giới hạn độ dài đầu vào |
| Thông điệp 191 byte, RSA-2048 + OAEP SHA-256 | Từ chối vì quá giới hạn |
| RSA-2048, ciphertext raw | Dài 256 byte |
| Cùng plaintext/key/params được mã hóa hai lần | Ciphertext thường khác nhau do OAEP random seed |
| Public key được đọc bằng sai KeySpec | Cần chuyển định dạng hoặc xử lý lỗi |
| Thuật toán bên nhận không được provider hỗ trợ | Từ chối hoặc chọn provider hỗ trợ theo chính sách; không tự downgrade |

### 9.1 Test liên thông nên chạy theo hai chiều

Đối với hệ thống thực, test không chỉ Java tự mã hóa rồi tự giải mã. Cần:

1. **Java encrypt → B decrypt.**
2. **B encrypt → Java decrypt.**
3. Ciphertext được sinh với **empty label** và với **label quy định** (nếu có).
4. Hai trường hợp có **MGF1 SHA-1 và SHA-256** để xác định rõ mismatch, nhưng chỉ dùng cấu hình legacy khi hợp đồng cho phép.
5. Test key serialization, Base64 decoding và việc truyền byte qua JSON/HTTP không làm biến đổi ciphertext.
6. Test phiên bản cũ/mới của provider và tài liệu hóa provider được hỗ trợ.

Ciphertext OAEP thay đổi do seed ngẫu nhiên nên **không so sánh raw ciphertext của hai lần mã hóa** để chứng minh tương thích. Hãy kiểm tra **plaintext giải mã được và error path**.

### 9.2 Kiểm thử lỗi không tạo oracle

Không trả cho người gọi các phản hồi phân biệt chi tiết “sai MGF1”, “sai label”, “sai OAEP digest”, “sai byte padding” cho từng ciphertext không tin cậy. Một ứng dụng thường trả lỗi giải mã chung; nội bộ ghi log ở mức cần thiết và không tiết lộ key/plaintext. Tránh timing hoặc retry behavior làm lộ manh mối. Chỉ sử dụng OAEP trong giao thức có bảo vệ đầu cuối phù hợp và kiểm soát rate/permission.

## 10. Những lỗi thực tế hay gặp

1. **“OAEPWithSHA-256AndMGF1Padding nghĩa là MGF1 chắc chắn SHA-256.”** Không đủ thông tin; MGF1 digest cần spec tường minh hoặc xác minh qua provider.
2. **“Hai bên chỉ cần cùng OAEP digest.”** Sai: phải cùng MGF algorithm, MGF1 digest và label bytes.
3. **“OAEPParameterSpec.DEFAULT đang dùng SHA-256.”** Sai: Java 17 mô tả DEFAULT dùng SHA-1 cho OAEP lẫn MGF1 và empty label.
4. **“MGF1ParameterSpec.SHA256 tự đổi OAEP digest thành SHA-256.”** Sai: đó là riêng hash dùng trong MGF1; mdName là tham số khác.
5. **“Label có cùng text nhìn thấy thì chắc cùng giá trị.”** Sai: charset, normalization, khoảng trắng, newline đều ảnh hưởng byte.
6. **“PSource.PSpecified.DEFAULT là mật khẩu mặc định.”** Sai: nó là label rỗng, không phải secret.
7. **“Đổi label là cách xác thực người gửi.”** Sai: bất kỳ ai có public key và label đều có thể mã hóa; cần xác thực độc lập.
8. **“Có thể mã hóa một file 1 MB trực tiếp bằng một lần RSA-OAEP.”** Sai: độ dài plaintext bị giới hạn theo k - 2hLen -2; dùng hybrid encryption.
9. **“RSA/ECB nghĩa là nên chia payload thành nhiều block RSA kiểu AES-ECB.”** Sai: không áp dụng cách hiểu đó cho RSA.
10. **“RSA-OAEP có thể thay chữ ký số RSA-PSS.”** Sai: mã hóa với public key khác với ký bằng private key.
11. **“Ciphertext Base64 giải mã được nên RSA padding chắc chắn đúng.”** Sai: Base64 chỉ chuyển text thành byte; RSA-OAEP mới kiểm tra cấu trúc theo key/params.
12. **“Sai label thì cứ thử label rỗng hoặc đổi SHA-1 cho đến khi được.”** Nguy hiểm: dễ tạo downgrade và xóa ranh giới giao thức. Phiên bản cấu hình phải cố định/allowlisted.
13. **“Public key từ người gửi là đáng tin chỉ vì key parse được.”** Sai: nguồn gốc key phải được xác thực để tránh encrypt cho attacker.
14. **“Mã hóa bằng public key là bằng chứng người gửi có private key.”** Sai: public key được công khai.
15. **“Ciphertext RSA-OAEP có độ dài đúng thì chắc hợp lệ.”** Sai: giải mã và kiểm tra OAEP mới xác minh được.
16. **“Chỉ so Java round-trip là đủ để đảm bảo Python/Go/OpenSSL giải mã được.”** Sai: cần test giao tiếp chéo với MGF digest, label, key format và encoding thật.

## 11. Tình huống chẩn đoán

### Tình huống A — Java encrypt thành công, service khác luôn báo OAEP decoding error

Bên Java chỉ ghi “RSA/ECB/OAEPWithSHA-256AndMGF1Padding”, bên kia cấu hình OAEP hash SHA-256 và MGF1 SHA-256. Cấu hình thực tế của Java nếu dùng mặc định có thể không cùng MGF1 hash.

**Cách điều tra:** đọc giá trị OAEPParameterSpec hiệu lực phía Java, kiểm tra MGF1 digest phía B, label bytes, key pair và ciphertext raw. Thiết lập spec tường minh rồi thử ciphertext cross-language theo cả hai chiều.

### Tình huống B — Chuyển label từ rỗng sang “app:v1”

Một nhóm thay PSource.PSpecified.DEFAULT thành label app:v1 nhưng service giải mã vẫn để rỗng. Mọi ciphertext mới sẽ không được giải mã theo cấu hình cũ.

**Cách xử lý:** version protocol/key record và rollout hai phía có kiểm soát. Không dùng random fallback liên tục; nếu thật sự cần hỗ trợ hai version, server chọn theo metadata được phép và kiểm tra quyền/nguồn gốc.

### Tình huống C — Text label có Unicode

Bên A dùng chuỗi tên nghiệp vụ có dấu tiếng Việt và UTF-8, bên B dùng chuỗi nhìn tương tự nhưng Unicode normalization khác. Hash label khác làm OAEP decode fail.

**Cách xử lý:** công bố label dưới dạng **hex/Base64 của bytes chính thức** trong hợp đồng, và dùng nguyên chuỗi byte đó. Nếu label được tạo từ text thì quy định UTF-8 và normalization cụ thể.

### Tình huống D — Muốn mã hóa payload JSON 2 KB bằng RSA-2048

RSA-OAEP SHA-256 chỉ chứa tối đa 190 byte; payload 2 KB vượt giới hạn.

**Cách xử lý:** tạo khóa AES ngẫu nhiên, mã hóa payload bằng AES-GCM, dùng RSA-OAEP mã hóa khóa AES. Định nghĩa và xác thực đầy đủ nonce/AAD/tag/version theo format hybrid.

### Tình huống E — Đối tác chỉ hỗ trợ empty label

Dịch vụ Java muốn dùng label tự chọn, còn thư viện đối tác không hỗ trợ OAEP label khác empty. Không thể chỉ thay đổi một phía rồi hy vọng giải mã được.

**Cách xử lý:** nếu chính sách cho phép, thiết kế giao thức với empty label cho cả hai bên và ràng buộc context ở lớp payload/AEAD phù hợp. Không làm label trở thành cơ chế nhận dạng bí mật hoặc bỏ qua kiểm tra tag khi hybrid decrypt.

## 12. Câu hỏi tự kiểm tra

**1. OAEPParameterSpec có những trường quan trọng nào?**

Hash chính OAEP, tên MGF (thường MGF1), tham số MGF1 bao gồm hash riêng, và PSource biểu diễn label bytes.

**2. SHA-256 OAEP với MGF1 SHA-1 có giống SHA-256 OAEP với MGF1 SHA-256 không?**

Không. Đây là hai bộ tham số OAEP khác nhau và không giải mã tương thích.

**3. Chỉ nhìn transformation RSA/ECB/OAEPWithSHA-256AndMGF1Padding đã biết MGF1 digest chưa?**

Chưa thể kết luận portable. Cần cấu hình MGF1ParameterSpec tường minh và/hoặc đọc lại tham số thực tế từ provider.

**4. OAEPParameterSpec.DEFAULT của Java 17 sử dụng gì?**

OAEP hash SHA-1, MGF1 với SHA-1, label rỗng. Không đồng nghĩa mọi transformation đều dùng các default này.

**5. Label app:v1 dùng UTF-8 có hex gì?**

**6170703a7631**. Label bytes phải đúng y hệt ở hai bên; thêm một dấu cách hoặc newline sẽ làm khác.

**6. Có thể dùng OAEP label như password hoặc chứng minh danh tính người gửi không?**

Không. Label không phải secret, và public key có thể dùng bởi bất kỳ ai để mã hóa.

**7. Với RSA-2048, OAEP SHA-256, giới hạn plaintext là bao nhiêu?**

**190 byte**, vì k = 256 và hLen = 32: 256 - 64 - 2 = 190.

**8. Nếu có dữ liệu 1 MB cần mã hóa cho một người nhận RSA thì sao?**

Dùng hybrid encryption: AES-GCM mã hóa dữ liệu, RSA-OAEP mã hóa khóa AES nhỏ và quản lý metadata/nonce/AAD/tag an toàn.

**9. Nếu sai private key, MGF1 digest hoặc label thì có nên thử RSA/ECB/PKCS1Padding để giải mã được không?**

Không. Cần từ chối hoặc xử lý theo phiên bản giao thức đã cho phép; không fallback sang cơ chế yếu.

**10. Vì sao nên kiểm tra Cipher.getParameters() và làm integration test liên ngôn ngữ?**

Vì tên transformation và mặc định provider có thể không mô tả đủ tham số; các phép thử xác minh cả hash, MGF1, label, key format và ciphertext encoding giữa hai triển khai.

## Kết luận

**RSA-OAEP không được cấu hình chỉ bằng một từ “SHA-256”.** Khi tạo hoặc giải mã ciphertext, phải thống nhất **OAEP digest, MGF algorithm, MGF1 digest, label bytes, RSA key và định dạng truyền**. Trong Java 17+, **OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, new PSource.PSpecified(labelBytes))** là cách diễn đạt rõ ràng. Dùng Cipher.init với tham số đã quy định, xác minh thông số thực tế và thử liên thông giữa hai phía. Nếu ciphertext không giải mã được, hãy xem đó là lỗi phải điều tra theo hợp đồng, **không thay bằng mặc định hoặc hạ cấp cơ chế bảo mật**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="public-key-cryptography-009">8.9 Giới hạn của việc dùng thuật toán bất đối xứng thay cho mã hóa dữ liệu khối lớn</a>

<details>
<summary>Xem chi tiết</summary>

# 8.9 Giới hạn của việc dùng thuật toán bất đối xứng thay cho mã hóa dữ liệu khối lớn

**Mã hóa bất đối xứng (public-key encryption)** giải quyết bài toán quan trọng: người gửi có thể sử dụng **khóa công khai** của người nhận để mã hóa một bí mật mà chỉ người giữ **khóa riêng** tương ứng có khả năng giải mã, với điều kiện thuật toán và giao thức được dùng đúng. Nhưng điều đó **không có nghĩa** RSA hoặc các primitive khóa công khai là lựa chọn phù hợp để mã hóa trực tiếp một file vài MB/GB, một luồng video hoặc toàn bộ dữ liệu trong cơ sở dữ liệu.

Cách làm thực tế thường là **mã hóa lai (hybrid encryption)**: thuật toán bất đối xứng/KEM hoặc giao thức thỏa thuận khóa đảm nhận việc **thiết lập/bảo vệ một khóa phiên nhỏ**; thuật toán đối xứng **AEAD** như AES-GCM mã hóa và xác thực **dữ liệu lớn**. Bài này tập trung vào giới hạn kỹ thuật của bất đối xứng khi thay mã hóa khối lớn, cách tính đúng kích thước RSA-OAEP, vì sao “cắt file thành nhiều đoạn rồi RSA từng đoạn” không phải giải pháp, và cách thiết kế envelope encryption trong Java 17+.

## 1. Bất đối xứng và đối xứng đảm nhận hai công việc khác nhau

| Khía cạnh | Mã hóa bất đối xứng / cơ chế khóa công khai | Mã hóa đối xứng AEAD |
| --- | --- | --- |
| Vật liệu khóa | Khóa công khai/khóa riêng, hoặc cặp khóa và shared secret trong giao thức thỏa thuận khóa | Một khóa bí mật chia sẻ hoặc data-encryption key (DEK) |
| Công việc điển hình | Mã hóa/đóng gói khóa phiên nhỏ, trao đổi/thiết lập bí mật, chữ ký số (là primitive khác) | Mã hóa lượng dữ liệu tùy ý **trong giới hạn mỗi thuật toán/giao thức**, đồng thời xác thực ciphertext và AAD |
| Chi phí tính toán | Thường lớn hơn đáng kể mỗi phép toán; RSA private-key operation đặc biệt đắt | Thường hiệu quả cho lượng byte lớn; có hỗ trợ tăng tốc trên nhiều CPU |
| Độ dài input trực tiếp | Với RSA-OAEP bị giới hạn bởi modulus và hash/padding, thường vài trăm byte | Dựa vào mode và giới hạn nonce/message; không bị ràng buộc kiểu “190 byte RSA-2048” |
| Kích thước output | Một phép RSA encryption trả ciphertext dài bằng modulus, bất kể plaintext ngắn thế nào | Thường xấp xỉ độ dài plaintext cộng tag/nonce/metadata nếu giao thức đóng gói |
| Xác thực nguồn gửi | **RSA encryption chỉ dùng khóa công khai người nhận không xác thực danh tính người gửi** | AEAD xác thực dữ liệu dưới khóa đã biết, không tự gắn danh tính người gửi |
| Điểm cần quản lý | Tin cậy public key, key ID, chứng chỉ, private key, padding, thuật toán và key rotation | Sinh/quản lý key, nonce/IV duy nhất, AAD, tag, integrity, rotation và policy |

**Định nghĩa quan trọng:** RSA encryption, RSA signature, ECDSA signature, ECDH key agreement và KEM là các primitive **khác nhau**, dù đều nằm trong thế giới khóa công khai. Không thể lấy chữ ký số rồi gọi đó là mã hóa, hoặc gọi ECDH là hàm mã hóa dữ liệu file.

Một câu tóm tắt dễ nhớ:

> **Khóa công khai bảo vệ hoặc thiết lập khóa dữ liệu; khóa dữ liệu đối xứng bảo vệ phần lớn byte của tài liệu.**

## 2. Giới hạn kích thước plaintext của RSA-OAEP

RSA làm việc với một modulus `n`, và ciphertext của một phép RSA encryption là một giá trị được mã hóa vào đúng kích thước modulus. Với **RSA-2048**, modulus thông thường dài **2.048 bit = 256 byte**. Nhưng **đầu vào tối đa không phải 256 byte**, vì cơ chế padding RSA-OAEP cần vùng dữ liệu để đảm bảo an toàn.

### 2.1. Công thức RSAES-OAEP

Theo cơ chế RSAES-OAEP trong PKCS #1 (RFC 8017), nếu:

- `k` = số **byte** cần để biểu diễn modulus RSA;
- `hLen` = số byte đầu ra của hash dùng trong OAEP;
- `mLen` = số byte plaintext;

thì cần:

```text
mLen <= k - 2 * hLen - 2
```

Đây là **giới hạn kích thước byte**, không phải giới hạn số ký tự Unicode hoặc Java `String.length()`.

Với modulus 2.048 bit, `k = 256`. Khi OAEP dùng SHA-256, `hLen = 32`, nên:

```text
maxPlaintext = 256 - 2 * 32 - 2 = 190 bytes
RSA ciphertext length = 256 bytes
```

Nếu file chứa **191 byte**, một phép RSA-2048 + OAEP SHA-256 trực tiếp không thể mã hóa nó. Việc “RSA có khóa 2.048 bit” hoàn toàn không có nghĩa có thể nạp plaintext 2.048 bit hoặc nhiều hơn vào cùng phép toán.

### 2.2. So sánh các cấu hình

| Modulus RSA | `k` | Hash OAEP / `hLen` | Plaintext tối đa mỗi RSA-OAEP operation | Ciphertext mỗi operation |
| --- | ---: | --- | ---: | ---: |
| RSA-2048 | 256 byte | SHA-1 / 20 byte | 214 byte | 256 byte |
| RSA-2048 | 256 byte | SHA-256 / 32 byte | **190 byte** | **256 byte** |
| RSA-3072 | 384 byte | SHA-256 / 32 byte | 318 byte | 384 byte |
| RSA-4096 | 512 byte | SHA-256 / 32 byte | 446 byte | 512 byte |
| RSA-2048 | 256 byte | SHA-512 / 64 byte | 126 byte | 256 byte |

Các giá trị là giới hạn **về kích thước**, không phải khuyến nghị bảo mật cho từng tổ hợp. SHA-1 trong bảng chỉ để thấy hiệu ứng của `hLen`; thiết kế mới nên sử dụng thuật toán và profile được chính sách bảo mật hiện hành cho phép, không chọn SHA-1 chỉ để tăng kích thước input. RSA-2048 cũng không có “độ mạnh 2.048 bit”: mức bảo mật cổ điển ước lượng chỉ khoảng **112 bit**, khác hoàn toàn kích thước modulus. Chọn RSA-3072/4096 hay giải pháp khóa công khai khác phải cân nhắc chính sách, thời gian bảo vệ dữ liệu và hiệu năng.

### 2.3. Các tham số OAEP phải xác định đầy đủ

Trong Java thường thấy chuỗi transformation `"RSA/ECB/OAEPPadding"` hoặc `"RSA/ECB/OAEPWithSHA-256AndMGF1Padding"`. Chữ **ECB** trong tên transformation RSA là quy ước API, **không có nghĩa RSA đang mã hóa khối dữ liệu giống AES-ECB**; RSA không dùng chế độ ECB cho file.

Để tránh mơ hồ giữa **hash của OAEP** và **hash dùng trong MGF1**, nên chỉ định cả hai qua `OAEPParameterSpec`:

```java
OAEPParameterSpec oaep = new OAEPParameterSpec(
    "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT
);
Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
cipher.init(Cipher.ENCRYPT_MODE, recipientPublicKey, oaep);
```

Đây là **đoạn minh họa đặt tham số**, chưa phải chương trình độc lập: `recipientPublicKey` phải là `PublicKey` đã được xác minh nguồn tin cậy; các `import` sẽ có trong chương trình hoàn chỉnh phía sau.

Không phụ thuộc mặc định của provider rồi giả định mọi JVM/hệ thống sẽ tự chọn cùng một OAEP hash/MGF1 hash. Sai tham số có thể dẫn đến không giải mã được hoặc sự cố tương thích. RSA-OAEP hỗ trợ tùy chọn label `PSource.PSpecified`; phía mã hóa và giải mã cũng phải dùng đúng label theo giao thức.

### 2.4. Tăng kích thước khóa RSA có giải quyết được file lớn?

**Không.** RSA-4096 với OAEP SHA-256 vẫn chỉ xử lý tối đa **446 byte/plaintext operation**, trong khi file có thể dài hàng triệu hoặc hàng tỷ byte. Tăng modulus vừa làm key/ciphertext lớn hơn vừa thường làm phép toán private-key chậm hơn đáng kể.

Với **RSA-2048/OAEP SHA-256**, một file **1 MiB = 1.048.576 byte** nếu cố cắt thành các đoạn tối đa 190 byte sẽ cần:

```text
numberOfRSAOperations = ceil(1,048,576 / 190) = 5,519
ciphertextSize = 5,519 * 256 = 1,412,864 bytes
overhead = 1,412,864 - 1,048,576 = 364,288 bytes
```

Đây mới là **chi phí kích thước** của phép chia khối, chưa gồm framing, sequence, thuật toán, khóa, version hay bảo vệ toàn vẹn. Hệ thống còn phải thực hiện **5.519 RSA encrypt operations** và tương ứng hàng nghìn **RSA private-key decrypt operations** thay vì một phép toán RSA bọc khóa nhỏ và một lượt AES trên file.

## 3. Vì sao “chia nhỏ file rồi RSA từng chunk” là thiết kế tệ?

### 3.1. Hiệu năng không phù hợp với throughput lớn

RSA dựa trên phép toán số học modulo big integer, có chi phí lớn hơn hẳn các round cipher đối xứng trên lượng dữ liệu dài. Chia file thành 5.519 khối tạo hàng nghìn phép toán khóa công khai, tải CPU và độ trễ tăng theo số lượng chunk; khi dùng HSM/KMS còn gặp overhead RPC, giới hạn throughput và chi phí vận hành.

Đối xứng như **AES-GCM** được tối ưu cho xử lý lượng byte lớn, có hỗ trợ tăng tốc AES/CLMUL ở nhiều nền tảng. **Không cần gán một tỷ lệ tốc độ cố định** cho mọi CPU/provider: hãy benchmark trong môi trường thực, nhưng cấu trúc công việc khác nhau rất rõ.

### 3.2. Mỗi block RSA có overhead đáng kể

Một block plaintext 190 byte trở thành block ciphertext 256 byte. Nếu block cuối chỉ có 1 byte, nó vẫn chiếm **256 byte**. Padding OAEP cần ngẫu nhiên, nên mã hóa cùng plaintext bằng cùng public key thường cho ciphertext khác nhau; không thể tự cắt overhead đi sau khi mã hóa.

Với file lớn, size của hàng nghìn block cộng metadata làm truyền/lưu trữ tốn hơn so với cách mã hóa dữ liệu bằng AES-GCM chỉ thêm tag nhỏ và phần header/nonce.

### 3.3. RSA-OAEP không biến các chunk thành một file được xác thực

RSA-OAEP cung cấp cơ chế mã hóa an toàn khi dùng đúng và trong mô hình phù hợp, nhưng **không phải một giao thức AEAD cho toàn bộ file**. Nếu tự chia file thành các chunk, bạn phải tự xử lý:

- Thứ tự chunk và số thứ tự; chống đảo thứ tự/ghép chunk từ file khác.
- Chống thiếu chunk, lặp chunk, cắt cụt phần đuôi và thay thế nội dung.
- Xác thực metadata như file ID, độ dài dự kiến, content type, version.
- Phát hiện sửa đổi theo một chính sách thống nhất, không chỉ biết từng RSA operation có giải mã ra byte hay không.
- Xử lý lỗi và padding oracle/leak qua thông điệp lỗi hoặc timing.

Cài đặt thiếu một trong những lớp trên có thể tạo lỗi nghiêm trọng. Mã hóa từng block RSA và ghép lại không tự biến thành *secure file encryption*. **Đừng tự phát minh “RSA chunk mode”** để né giới hạn `mLen`.

### 3.4. Nguy cơ thiết kế/API

- Đừng dùng `RSA/ECB/NoPadding` như một mẹo để nạp được nhiều byte hơn: **raw/textbook RSA không an toàn** và không giải quyết bài toán bulk encryption.
- Đừng chuyển sang `RSA/ECB/PKCS1Padding` chỉ vì nó cho plaintext dài hơn OAEP một chút: RSAES-PKCS1-v1_5 có rủi ro padding-oracle và không phải lựa chọn mặc định cho thiết kế mới.
- Đừng dùng RSA **signature** để “mã hóa bằng private key”. Chữ ký phục vụ xác thực/tính toàn vẹn theo chuẩn chữ ký; nó **không tạo bí mật dữ liệu**.
- Đừng mã hóa một file trực tiếp bằng khóa công khai EC/ECDSA: ECDSA là chữ ký số; ECDH/X25519 là cơ chế **thỏa thuận khóa**, không phải `Cipher.doFinal(file)` để mã hóa trực tiếp.

## 4. Giải pháp đúng: Hybrid encryption / envelope encryption

Trong mô hình RSA-OAEP + AES-GCM, một file có một **data-encryption key (DEK)** ngẫu nhiên ngắn. Phần bất đối xứng **chỉ bọc DEK**; phần đối xứng mã hóa toàn bộ file.

### 4.1. Luồng mã hóa

```text
                   Bên gửi
   [PublicKey người nhận đã xác thực]
                     |
    Tạo AES-256 data key ngẫu nhiên (32 byte)
                     |
             +-------+--------+
             |                |
    RSA-OAEP encrypt(DEK)   AES-GCM encrypt(file, DEK, nonce, AAD)
             |                |
      wrappedKey (256 B)    ciphertext || tag
             |                |
             +-------+--------+
                     |
       Envelope: header + keyId + wrappedKey
                  + nonce + ciphertext + tag
                     |
              Lưu / truyền cho người nhận
```

Các bước:

1. **Xác minh public key của người nhận** bằng certificate/PKI, danh bạ khóa đã pin hoặc kênh cung cấp khóa được xác thực. Nếu đối thủ thay public key bằng của họ, toàn bộ file có thể bị mã hóa để đối thủ mở.
2. **Sinh một DEK ngẫu nhiên mới** (ví dụ AES-256 = 32 byte) bằng CSPRNG/KeyGenerator; không dùng mật khẩu hoặc `new Random()` làm key.
3. **Bọc DEK** bằng public key RSA của người nhận và RSA-OAEP có tham số xác định; output là một RSA ciphertext nhỏ, ví dụ 256 byte với RSA-2048.
4. **Sinh nonce/IV đúng yêu cầu AEAD**; với AES-GCM thường dùng 96 bit = 12 byte. Với cùng một AES key, **nonce không được lặp**. Tạo DEK mới cho mỗi file giúp đơn giản hóa chiến lược nonce, nhưng vẫn phải quản lý sao cho không reuse cặp key/nonce.
5. **Mã hóa file bằng AES-GCM** với DEK, nonce và AAD đã chuẩn hóa; kết quả gồm ciphertext và authentication tag, ví dụ tag 128 bit = 16 byte.
6. **Đóng gói envelope** có version, thuật toán, key ID, wrapped key, nonce, ciphertext, tag và AAD/metadata cần thiết; kiểm tra giới hạn độ dài và chính sách parse trước khi giải mã.

### 4.2. Luồng giải mã

```text
                  Bên nhận
     Envelope và metadata đã kiểm tra sơ bộ
                     |
    Xác định keyId + phiên bản + thuật toán hợp lệ
                     |
      PrivateKey --> RSA-OAEP decrypt(wrappedKey)
                     |           |
                     |        AES data key
                     |           |
              ciphertext, nonce, AAD
                     |           |
              AES-GCM decrypt + verify tag
                     |
           Chỉ chấp nhận plaintext khi
           authentication tag đã hợp lệ
```

**Thứ tự bảo mật:** không coi plaintext là đáng tin trước khi AEAD tag được kiểm tra; một file có tag sai phải bị từ chối toàn bộ. Nếu giải mã theo stream để ra disk, hãy cân nhắc **ghi vào file tạm cô lập** rồi chỉ công bố/rename sau khi hoàn thành xác thực, không cho các thành phần nghiệp vụ đọc trước.

### 4.3. Kích thước thật của envelope ví dụ

Với RSA-2048 OAEP SHA-256 bọc một **AES-256 key 32 byte**, và AES-GCM mã hóa đúng **1 MiB plaintext**:

| Thành phần | Số byte |
| --- | ---: |
| AES data key (chỉ tồn tại trong bộ nhớ, **không** gửi plaintext key) | 32 |
| `wrappedKey` RSA-OAEP của DEK | 256 |
| GCM nonce | 12 |
| Dữ liệu ciphertext của 1 MiB plaintext | 1.048.576 |
| GCM tag 128 bit | 16 |
| `ciphertext || tag` từ `Cipher.doFinal` | **1.048.592** |
| `wrappedKey || nonce || ciphertext || tag` | **1.048.860** |
| Header, key ID, version, lengths, AAD metadata | **Thêm tùy định dạng** |

Đối chiếu với 1.412.864 byte khi mã hóa trực tiếp 1 MiB theo 5.519 block RSA, hybrid có **overhead binary chỉ 284 byte trong ví dụ chưa tính header**, và chỉ cần **một RSA operation** để bọc DEK; khối dữ liệu lớn đi qua AES-GCM.

Không hiểu số “32 byte AES key” là một thành phần được truyền ở dạng plaintext; nó nằm bên trong `wrappedKey`. Việc nhân bản/ghi raw DEK vào envelope sẽ phá hoàn toàn tính bí mật.

### 4.4. Vấn đề cần đặt trong header và AAD

Một envelope đủ dùng thật không chỉ là nối bốn mảng byte tùy ý. Phải định nghĩa schema không nhập nhằng, ví dụ:

```text
version
suiteId / asymmetricKeyId / recipientId
wrappedKeyLength + wrappedKey
nonceLength + nonce
ciphertextLength + ciphertext || tag
associatedData (metadata được xác thực)
```

- **Định dạng ranh giới byte:** length-prefix, TLV, CBOR/protobuf schema được giới hạn và chuẩn hóa. Không đoán độ dài chỉ dựa vào delimiter trong binary.
- **AAD (additional authenticated data):** đưa các metadata cần bảo vệ tính toàn vẹn (version, algorithm suite, document ID, tenant/recipient context, hoặc key binding) vào AAD theo quy tắc canonical rõ ràng. AAD **được xác thực nhưng không mã hóa**.
- **Không tin dữ liệu điều khiển từ envelope:** `suiteId`, `keyId`, độ dài và version là input không tin cậy. Server chỉ chấp nhận allowlist thuật toán, scope key và kích thước phù hợp; không để attacker điều khiển đường dẫn đọc private key hay chọn thuật toán yếu.
- **RSA-OAEP và AES-GCM phải phối hợp thống nhất:** khi receiver unwrap đúng DEK và dùng đúng nonce + AAD, AES-GCM sẽ xác thực ciphertext/tag. Nếu metadata cần được xác thực nhưng nằm ngoài AAD thì có nguy cơ bị thay mà vẫn xác minh GCM thành công.
- **Ràng buộc người nhận:** nếu nhiều người nhận, policy và metadata liên quan danh tính/khóa cần được bảo vệ phù hợp; khóa bất đối xứng được chọn phải được xác minh trước.

## 5. Java 17+: thử giới hạn RSA-OAEP và mã hóa lai file 1 MiB

Đây là một chương trình Java 17+ **độc lập, biên dịch và chạy được**, thực hiện cả hai thử nghiệm:

- Tạo RSA-2048 và dùng OAEP với **hash SHA-256 + MGF1 SHA-256 được khai báo tường minh**. Mã hóa trực tiếp 190 byte thành công, 191 byte bị từ chối.
- Tạo AES-256 key ngẫu nhiên, **RSA-OAEP chỉ mã hóa 32 byte DEK**, còn AES-GCM mã hóa **1.048.576 byte dữ liệu**. Giải mã lại, kiểm tra round-trip và phát hiện thay đổi ciphertext.

Chương trình chỉ in các số đo và kết luận boolean, **không in khóa bí mật hoặc ciphertext**. Để minh họa đơn giản nó giữ 1 MiB trong RAM; đây **không phải code xử lý file nhiều GB hoặc định dạng envelope production**.

Lưu file **HybridCryptoDemo.java**:

```java
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

public class HybridCryptoDemo {
    static final OAEPParameterSpec OAEP = new OAEPParameterSpec(
            "SHA-256", "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT);
    static final byte[] AAD =
            "file-envelope:v1".getBytes(StandardCharsets.UTF_8);

    public static void main(String[] args) throws Exception {
        // Bên nhận: minh họa tạo cặp khóa RSA 2048 bit.
        KeyPairGenerator rsaGen = KeyPairGenerator.getInstance("RSA");
        rsaGen.initialize(2048);
        KeyPair receiver = rsaGen.generateKeyPair();

        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
        rsa.init(Cipher.ENCRYPT_MODE, receiver.getPublic(), OAEP);
        byte[] direct = rsa.doFinal(new byte[190]);
        boolean tooLargeRejected = false;
        try {
            rsa.doFinal(new byte[191]);
        } catch (IllegalBlockSizeException expected) {
            tooLargeRejected = true;
        }
        System.out.println("RSA-OAEP 190-byte input -> "
                + direct.length + "-byte ciphertext");
        System.out.println("RSA-OAEP 191-byte input rejected: "
                + tooLargeRejected);

        // Bên gửi: một khóa đối xứng mới, chỉ bọc khóa bằng RSA.
        KeyGenerator aesGen = KeyGenerator.getInstance("AES");
        aesGen.init(256);
        SecretKey dataKey = aesGen.generateKey();
        // Khởi tạo lại Cipher sau phép thử đầu vào quá dài.
        rsa.init(Cipher.ENCRYPT_MODE, receiver.getPublic(), OAEP);
        byte[] wrappedKey = rsa.doFinal(dataKey.getEncoded());

        byte[] nonce = new byte[12];
        new SecureRandom().nextBytes(nonce);
        byte[] file = new byte[1024 * 1024];
        new SecureRandom().nextBytes(file);

        Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
        gcm.init(Cipher.ENCRYPT_MODE, dataKey,
                new GCMParameterSpec(128, nonce));
        gcm.updateAAD(AAD);
        byte[] sealed = gcm.doFinal(file);

        System.out.println("AES key: "
                + dataKey.getEncoded().length + " bytes");
        System.out.println("RSA-wrapped key: "
                + wrappedKey.length + " bytes");
        System.out.println("nonce: " + nonce.length + " bytes");
        System.out.println("file: " + file.length + " bytes");
        System.out.println("AES-GCM ciphertext+tag: "
                + sealed.length + " bytes");
        System.out.println("envelope binary bytes (without header): "
                + (wrappedKey.length + nonce.length + sealed.length));

        // Bên nhận: unwrap khóa rồi xác minh/giải mã GCM.
        rsa.init(Cipher.DECRYPT_MODE, receiver.getPrivate(), OAEP);
        byte[] unwrapped = rsa.doFinal(wrappedKey);
        SecretKeySpec recoveredKey = new SecretKeySpec(unwrapped, "AES");

        gcm.init(Cipher.DECRYPT_MODE, recoveredKey,
                new GCMParameterSpec(128, nonce));
        gcm.updateAAD(AAD);
        byte[] restored = gcm.doFinal(sealed);
        System.out.println("round-trip OK: "
                + Arrays.equals(file, restored));

        // Một byte ciphertext bị sửa thì GCM không được chấp nhận.
        byte[] changed = sealed.clone();
        changed[0] ^= 1;
        gcm.init(Cipher.DECRYPT_MODE, recoveredKey,
                new GCMParameterSpec(128, nonce));
        gcm.updateAAD(AAD);
        boolean tamperRejected = false;
        try {
            gcm.doFinal(changed);
        } catch (AEADBadTagException expected) {
            tamperRejected = true;
        }
        System.out.println("tampered ciphertext rejected: "
                + tamperRejected);

        Arrays.fill(unwrapped, (byte) 0);
    }
}
```

Biên dịch và chạy bằng Java 17 trở lên:

```bash
javac -encoding UTF-8 HybridCryptoDemo.java
java HybridCryptoDemo
```

Stdout kỳ vọng (các giá trị độ dài không phụ thuộc vào byte khóa/nonce được sinh ngẫu nhiên; đã kiểm tra bằng Java):

```text
RSA-OAEP 190-byte input -> 256-byte ciphertext
RSA-OAEP 191-byte input rejected: true
AES key: 32 bytes
RSA-wrapped key: 256 bytes
nonce: 12 bytes
file: 1048576 bytes
AES-GCM ciphertext+tag: 1048592 bytes
envelope binary bytes (without header): 1048860
round-trip OK: true
tampered ciphertext rejected: true
```

### 5.1. Vì sao kết quả như vậy?

1. **190 byte:** công thức OAEP SHA-256 trên RSA-2048 có input tối đa 190 byte; `doFinal(new byte[190])` mã hóa thành 256 byte.
2. **191 byte:** `IllegalBlockSizeException` xuất hiện ở provider JDK thông dụng vì message quá lớn. Demo bắt lỗi như một thử nghiệm, không biến `doFinal` lỗi thành đường xử lý nghiệp vụ.
3. **AES-256:** `KeyGenerator.init(256)` nhận độ dài **bit**; secret key exportable trong provider phần mềm demo dài 32 byte. HSM/provider không xuất khóa có thể trả `getEncoded() == null`; ở sản phẩm không ép lấy raw key nếu API wrap/unwrap/derive có khả năng xử lý khóa trong HSM.
4. **Wrapped key:** RSA chỉ xử lý 32 byte input nên luôn dưới giới hạn 190 byte; output là 256 byte.
5. **AES-GCM:** tag 128 bit = 16 byte. Với 1 MiB plaintext, `doFinal` tạo 1.048.576 + 16 = **1.048.592 byte**. Nonce 12 byte được giữ tách riêng, không có sẵn trong output của `Cipher.doFinal()`.
6. **Tổng binary:** 256 byte wrapped key + 12 byte nonce + 1.048.592 byte GCM result = **1.048.860 byte**, chưa gồm header/schema/key ID.
7. **Xác thực:** chỉnh một byte ở ciphertext làm tag không khớp nên `AEADBadTagException` được phát ra khi giải mã. Cả ciphertext **và AAD** phải khớp bản đã dùng khi mã hóa.

### 5.2. Những giới hạn của chương trình minh họa

- Nó tạo RSA keypair và file ngẫu nhiên mỗi lần chạy; sản phẩm phải **xác thực public key người nhận**, quản lý private key và đặt rõ key ID/rotation.
- Nó giữ cả 1 MiB file, sealed và restored trong RAM; **không dùng nguyên mẫu để xử lý file khổng lồ**.
- Nó không serialize/parse envelope, không lưu version/key ID và không tạo AAD theo header động. Khi triển khai cần định dạng chuẩn và policy chặt.
- Nó chỉ dùng AAD cố định để minh họa API; trong sản phẩm AAD có thể phải chứa metadata của file và recipient, được canonicalize và đồng nhất ở hai phía.
- Nó tạo DEK mới mỗi lần chạy; không tái sử dụng key + nonce. **Trùng nonce dưới cùng khóa GCM có thể phá tính bí mật và toàn vẹn**.
- `Arrays.fill(unwrapped, (byte) 0)` chỉ dọn mảng tạm theo khả năng; không chứng minh xóa được mọi bản sao trong `SecretKeySpec`, JVM hoặc provider. Trong môi trường đòi hỏi bảo vệ khóa cao, xem xét HSM/KMS và khóa không xuất được.
- Mặc dù AES-GCM xác minh dữ liệu dưới DEK, demo **không xác minh danh tính bên gửi** và không chứng minh ai tạo envelope, vì bất kỳ ai có public key người nhận cũng có thể tự tạo một DEK mới rồi mã hóa file khác. Cần lớp xác thực người gửi riêng nếu nghiệp vụ yêu cầu.

## 6. Xử lý file thực sự lớn: streaming, bộ nhớ và kiểm tra tag

Hybrid encryption giải quyết giới hạn kích thước RSA, nhưng **“dùng AES-GCM” không đồng nghĩa hệ thống có thể cấp phát một mảng byte kích thước file vài GB**. Khi file lớn hơn bộ nhớ, thiết kế luồng I/O, giới hạn độ dài mỗi message và cách công bố plaintext là rất quan trọng.

### 6.1. Streaming encryption

Về mặt ý tưởng, hệ thống đọc file qua `InputStream` theo các buffer nhỏ, đẩy vào `Cipher.update(...)` / cơ chế stream encryption thích hợp và ghi ciphertext ra file tạm/đích theo một framing có version. Cuối stream gọi `doFinal()` để thu tag cuối và hoàn thiện envelope.

**Không sử dụng `new byte[(int)file.length()]`** làm đường đi mặc định cho file nhiều GB: có thể tràn int hoặc gây `OutOfMemoryError`. Cần giới hạn input, dùng file/stream, phân biệt metadata và ciphertext, kiểm soát I/O errors và cleanup.

Cấu trúc ý tưởng (**pseudocode**, không phải Java có thể chạy trực tiếp):

```text
create fresh DEK and unique nonce
wrap DEK with recipient's verified public key
write validated envelope header
initialize AEAD encryptor with DEK, nonce and exact AAD
while input has more bytes:
    read bounded chunk
    encrypt and write bytes (preserve order)
finalize AEAD encryption and append authentication tag
finish/commit envelope only after successful completion
```

Pseudocode không thay thế thư viện/giao thức được kiểm định. Với AES-GCM một message rất lớn, phải tuân thủ **giới hạn kích thước và giới hạn sử dụng theo khóa/nonce** của tiêu chuẩn và provider; khi vượt ngưỡng, thiết kế giao thức record/chunk AEAD thay vì nối vô hạn vào một GCM invocation.

### 6.2. Streaming decryption: không được dùng plaintext chưa xác thực

Khi giải mã AES-GCM, integrity tag thường chỉ được xác minh hoàn chỉnh **ở cuối message**. Một API stream/provider có thể trả các byte trước khi xác minh cuối cùng, hoặc giữ chúng tùy implementation; tuyệt đối **không dựa vào giả định “có output nghĩa là đã xác thực”**.

Đối với file lớn:

- Ghi plaintext tạm vào vùng không cho ứng dụng khác sử dụng, giới hạn truy cập.
- Chỉ commit, rename hoặc phân phối plaintext khi AEAD tag của **toàn bộ message** hợp lệ.
- Nếu tag sai, xóa bản tạm và báo lỗi chung; không để một file một phần được hệ thống tiêu thụ.
- Không bỏ qua exception ở `Cipher.doFinal()`, không tự chỉ kiểm tra 16 byte tag theo so sánh thủ công rồi bỏ qua dữ liệu AAD.
- Với nhiều message/chunk AEAD, **mỗi chunk phải được xác thực**, và toàn bộ chuỗi cần ràng buộc thứ tự, vị trí, file ID, tổng chunk, độ dài/cuối file để chống reorder, rollback và truncate.

### 6.3. Mã hóa theo chunk đúng cách

Nếu ứng dụng cần random-access hoặc file quá lớn so với giới hạn một message AEAD, có thể sử dụng một **định dạng mã hóa theo record/chunk** được thiết kế và kiểm định phù hợp:

- Mỗi chunk có nonce/sequence **không trùng dưới cùng khóa**.
- AAD ràng buộc **file ID, version, sequence index, total length hoặc final marker** theo thiết kế.
- Hệ thống xác minh tính toàn vẹn **toàn bộ file** và tính liên tục của chunk; “mọi chunk riêng lẻ có tag đúng” chưa đủ để chống thiếu chunk/đảo thứ tự nếu không ràng buộc dữ liệu.
- Giới hạn số lượng chunk trên một DEK, tổng dữ liệu mã hóa trên khóa và trạng thái sử dụng nonce; tuân theo giới hạn thuật toán/giao thức.
- Khi có thư viện/giao thức authenticated streaming encryption thích hợp và được hỗ trợ, ưu tiên nó thay vì tự phát minh cách đặt nonce, index, tag hay metadata.

## 7. ECC, ECDH, KEM và HPKE: vẫn cần thuật toán đối xứng cho payload lớn

Không phải mọi public-key primitive đều giống RSA encryption. Với các hệ thống hiện đại, có các cách thiết lập shared secret hoặc encapsulate key material:

| Cơ chế | Tác dụng | Có trực tiếp mã hóa file MB/GB không? |
| --- | --- | --- |
| **RSA-OAEP** | Mã hóa bí mật nhỏ/DEK cho public key người nhận | **Không phù hợp**; RSA có giới hạn `mLen` và chi phí lớn |
| **ECDH / X25519** | Hai bên tính shared secret từ khóa riêng và public key của nhau | **Không**; cần KDF và AEAD để mã hóa dữ liệu |
| **KEM (key encapsulation mechanism)** | Encapsulate bí mật và decapsulate ở người nhận | **Không**; sau KEM thường dùng KDF và AEAD |
| **HPKE (RFC 9180)** | Kết hợp KEM, KDF và AEAD theo suite và mode của chuẩn | **Có thể dùng để mã hóa ứng dụng qua giao thức**, nhưng payload lớn vẫn do AEAD xử lý; phải tuân theo giới hạn suite/context |

### 7.1. ECDH không phải “AES với khóa EC”

ECDH/X25519 cho hai bên tính ra shared secret theo thuật toán thỏa thuận khóa. **Không dùng trực tiếp raw shared secret làm AES key bằng cách cắt byte tùy tiện**; áp dụng KDF được đặc tả (thường HKDF) và context cần thiết để dẫn xuất key/nonce, đồng thời đảm bảo public key, bên nhận và giao thức được xác thực.

Nếu một thiết kế ECDH không có xác thực, **kẻ trung gian có thể thay public key** và thiết lập secret riêng với mỗi phía. Việc dùng ECDH không tự bảo đảm bạn đang nói chuyện đúng người nhận.

### 7.2. Hybrid không có nghĩa hai thuật toán đều mã hóa toàn bộ file

Một mô hình KEM + KDF + AEAD cũng là dạng hybrid:

```text
KEM.encapsulate(recipientPublicKey) -> encapsulation + sharedSecret
HKDF(sharedSecret, protocolContext) -> AEAD key/nonce material
AEAD.encrypt(file, key, nonce, AAD) -> ciphertext + tag
envelope -> encapsulation + ciphertext + tag + metadata
```

Không cần “RSA encrypt(file) rồi AES encrypt(lại file)” để có hai lớp. Điều quan trọng là **kiểm soát nguồn public key, bảo vệ/thiết lập DEK và mã hóa/xác thực payload đúng cách**. Tên gọi “hybrid encryption” có thể dùng cho nhiều bộ thuật toán; thiết kế phải ghi rõ suite và mục tiêu bảo mật.

## 8. Bảo mật hệ thống: hybrid giải quyết cái gì và không giải quyết cái gì?

### 8.1. Bí mật dữ liệu ở nơi lưu trữ

Nếu public key người nhận đáng tin và private key được bảo vệ, bọc DEK bằng RSA-OAEP kết hợp AES-GCM có thể cung cấp **bí mật của file** khi chỉ ciphertext/envelope được lưu. Để an toàn thật, không để DEK lọt vào log, cache không kiểm soát, exception, metadata hoặc nơi chứa plaintext.

Nhưng nếu private key RSA bị lộ, các DEK cũ bọc bằng key đó có thể bị unwrap từ envelope được lưu. Do đó đây **không mặc định có forward secrecy** cho dữ liệu đã lưu. Một giao thức có ephemeral secret và key erasure đúng có thể có những tính chất khác, nhưng phải phân tích theo giao thức cụ thể.

### 8.2. Kiểm chứng public key là một phần của mã hóa

Dùng public key tải từ đường dẫn do attacker cung cấp không tạo bí mật **cho đúng người nhận**. Phải có cơ chế tin cậy: chứng chỉ hợp lệ, key pinning, authenticated directory, HSM/KMS identity hoặc quy trình provisioning được bảo vệ.

Không chọn khóa bằng `kid` do file tự khai mà bỏ qua tenant/recipient scope; không tự tải URI trong header để lấy public key rồi coi là key tin cậy.

### 8.3. AEAD không tự xác thực danh tính người gửi

Một file AES-GCM có tag đúng chỉ cho biết file/metadata không bị đổi so với khóa AEAD đã sử dụng (trong giả định thuật toán đúng). Trong mô hình public-key hybrid nơi **bất cứ ai** có public key nhận cũng có thể tạo DEK mới, **một ciphertext hợp lệ không chứng minh ai gửi nó**.

Nếu nghiệp vụ đòi hỏi danh tính, cần xác thực nguồn riêng: TLS endpoint đã xác thực, chữ ký số trên nội dung/envelope có ngữ cảnh phù hợp, hoặc giao thức gửi nhận authenticated được chuẩn hóa. Chữ ký số phải có cơ chế phân phối/trust khóa công khai người ký tương ứng.

### 8.4. Mức bảo mật không bằng kích thước AES key một cách máy móc

Bộ RSA-2048 + AES-256 có thể có **điểm yếu hơn AES-256** do mức bảo mật cổ điển của RSA-2048 và rủi ro quản lý private key. Mức an toàn của toàn hệ thống là bài toán tổng thể: thuật toán yếu nhất, key length, key distribution, AEAD nonce, lifecycle, lỗi phần mềm, authorization và mô hình tấn công. Đừng chọn “AES-256” rồi coi mọi yếu tố khác đạt bảo mật 256 bit.

### 8.5. Khi có nhiều người nhận

Để gửi **cùng một file** cho nhiều người nhận, thường có thể mã hóa payload **một lần** bằng một DEK và **bọc DEK riêng** cho public key của từng người nhận:

```text
file --AEAD(DEK)--> ciphertext+tag
             DEK --recipient A public key--> wrappedKeyA
             DEK --recipient B public key--> wrappedKeyB
             DEK --recipient C public key--> wrappedKeyC
```

Bằng cách này payload lớn không bị mã hóa lại bằng RSA theo từng người nhận; overhead chủ yếu tăng theo số wrapped keys. Tuy nhiên cần ràng buộc recipients/key IDs và metadata trong thiết kế envelope và xử lý việc thu hồi người nhận. Người đã có DEK hoặc plaintext **không thể bị thu hồi quyền đọc bản sao đã lấy chỉ bằng cách xóa entry**.

## 9. Thiết kế hệ thống Java: ranh giới API, lựa chọn công cụ và vận hành

### 9.1. Xác định API theo vai trò

- **RSA:** `Cipher.getInstance("RSA/ECB/OAEPPadding")`, `OAEPParameterSpec` và public/private key từ key store/KMS đã được quản lý. Đối với DEK ngẫu nhiên 16/32 byte, RSA-OAEP phù hợp hơn mã hóa data khối lớn.
- **AES-GCM:** `Cipher.getInstance("AES/GCM/NoPadding")`, `GCMParameterSpec` và key ngẫu nhiên, nonce phù hợp; xác minh AEAD tag khi decrypt.
- **Bộ sinh khóa:** `KeyGenerator.getInstance("AES")` với kích thước bit được policy hỗ trợ; `SecureRandom` để sinh nonce và byte ngẫu nhiên khác nếu cần.
- **Quản lý khóa:** private key nên ở keystore/HSM/KMS/secret manager có kiểm soát access; ứng dụng không cần tự xuất private key nếu provider hỗ trợ phép toán cần thiết.
- **Key rotation:** envelope cần key ID và version để unwrap DEK bằng private key đúng; các file còn phải đọc được cần chiến lược giữ khóa cũ/rewrap phù hợp.

Không lưu private key RSA cạnh ciphertext trong một thư mục public. Nếu private key và dữ liệu đều bị lộ, lớp bất đối xứng mất tác dụng. Khi cần backup/restore, có chính sách giữ và bảo vệ khóa cũ đủ lâu để phục hồi tài liệu.

### 9.2. Chi phí RSA/KMS vẫn tồn tại nhưng chỉ trên dữ liệu nhỏ

Hybrid không loại bỏ hoàn toàn RSA cost; nó giảm số phép toán khóa công khai từ **hàng nghìn mỗi file** về **một hoặc vài phép toán bọc/unwrap DEK** tùy số người nhận. Khi dùng HSM/KMS, vẫn phải xét latency, quota API, failover, audit và cache policy; nhưng không đưa mỗi 190 byte file đi gọi RSA.

Với hệ thống có KMS hỗ trợ symmetric data-key/envelope encryption, có thể dùng chức năng tạo DEK và bọc DEK của dịch vụ, thay vì ứng dụng tự thực hiện RSA. Mô hình “envelope” **không bắt buộc phải dùng RSA**; KMS symmetric wrapping hoặc KEM tương ứng cũng có thể phù hợp theo kiến trúc.

### 9.3. File lớn, API phản hồi và lỗi

- Áp **giới hạn request/file size**, định dạng hợp lệ và giới hạn độ dài từng trường envelope trước khi cấp phát bộ nhớ.
- Không để các lỗi khác nhau của RSA/OAEP/GCM tạo oracle tiết lộ chi tiết bí mật qua response, log hoặc timing.
- Với GCM, lỗi tag phải là **thất bại xác thực file**; đừng dùng plaintext đã phát ra một phần.
- Tách key-encryption-key/wrapping key và data-encryption-key để dễ **rewrap DEK khi rotation** thay vì luôn giải mã/mã hóa lại cả file, nhưng chỉ thực hiện khi policy và định dạng cho phép.
- Đặt version thuật toán, key ID, suite và metadata trong một cấu trúc được kiểm tra, không dựa vào chuỗi tên transformation do client tùy ý cung cấp.
- Khi cần sửa hoặc tải một phần file theo random-access, thiết kế authenticated chunking/format chuyên dụng; **đừng tự cắt tag GCM rồi xác minh từng phần**.

## 10. Các lỗi thường gặp

| Cách nghĩ hoặc cách làm sai | Vấn đề | Cách làm đúng |
| --- | --- | --- |
| “RSA-2048 mã hóa được 2.048 bit plaintext.” | Modulus length không phải độ dài payload cho phép | Dùng công thức OAEP `k - 2*hLen - 2` |
| “Không đủ thì chia file thành nhiều đoạn RSA.” | Tốn CPU/overhead và không xác thực file tổng thể | Bọc DEK nhỏ bằng RSA, mã hóa file bằng AEAD |
| “Chọn RSA-4096 để mã hóa file 1 GB trực tiếp.” | Vẫn chỉ vài trăm byte/operation | Chọn hybrid/envelope hoặc giao thức mã hóa file phù hợp |
| “RSA encryption bằng public key chứng minh người gửi.” | Bất cứ ai có public key đều có thể mã hóa | Dùng chữ ký số hoặc authenticated channel cho sender |
| “RSA signature bằng private key là mã hóa.” | Signature không bảo mật plaintext | Phân biệt ký số và encryption |
| “EC public key có thể đưa thẳng vào AES Cipher.” | ECDH không tự mã hóa payload | Key agreement/KEM + KDF + AEAD đúng chuẩn |
| “Dùng RSA/NoPadding để bỏ giới hạn.” | Raw RSA không an toàn | Sử dụng scheme padding chuẩn và hybrid |
| “Dùng RSA/PKCS1Padding vì plaintext tối đa dài hơn.” | Có rủi ro legacy/padding-oracle | Ưu tiên RSA-OAEP có tham số chính xác cho thiết kế mới |
| “OAEPWithSHA-256 nghĩa MGF1 tự giống nhau ở mọi provider.” | Có thể sai tham số và interop | `OAEPParameterSpec` explicit cho digest, MGF1 và label |
| “Nonce GCM chỉ cần đúng 12 byte.” | Nonce lặp lại dưới cùng key nguy hiểm | Thiết kế/kiểm tra **tính duy nhất của nonce** |
| “GCM trả plaintext là đã xác minh xong.” | Có thể dùng dữ liệu trước khi tag được kiểm tra | Chỉ commit sau toàn bộ AEAD verification |
| “Header/key ID không cần MAC vì chỉ là metadata.” | Có thể bị sửa ngữ cảnh hoặc key lookup | Bind AAD/metadata và kiểm tra key scope/version |
| “AES-256 làm RSA-2048 mạnh 256 bit.” | Mức bảo mật là của hệ thống, không chỉ DEK | Đánh giá độ mạnh khóa công khai, policy và lifecycle |
| “Mã hóa file một lần là xong key rotation.” | Private key cũ vẫn cần để unwrap các DEK cũ | Có key version/rewrap/retention policy |
| “Chỉ phải kiểm tra tag mỗi chunk riêng lẻ.” | Có thể đảo, cắt, thay thế chunk | Gắn sequence, length, file ID và finalization vào authenticated structure |

## 11. Checklist trước khi triển khai bulk encryption

1. **Mục tiêu:** dữ liệu cần giữ bí mật trước đối tượng nào? Người nhận là ai, và cần xác thực danh tính người gửi không?
2. **Key trust:** public key đến từ nguồn được xác minh chưa? Private key có ở nơi được quản lý an toàn không?
3. **Thuật toán:** đã chọn một suite rõ ràng cho key encapsulation/wrapping + AEAD chưa? Có tránh raw RSA/legacy padding không?
4. **RSA capacity:** nếu dùng RSA-OAEP, chỉ đưa vật liệu khóa nhỏ vào RSA và kiểm tra `mLen <= k - 2*hLen - 2` chưa?
5. **Data key:** mỗi file/envelope có DEK ngẫu nhiên phù hợp? Có đảm bảo không log hoặc truyền DEK dạng plaintext không?
6. **AEAD nonce/tag:** nonce duy nhất trên mỗi key; tag độ dài chuẩn; AAD/metadata phải đúng byte và được xác minh?
7. **Envelope:** version/key ID/lengths/algorithm suite có parser giới hạn kích thước, chống ambiguity và đảm bảo integrity không?
8. **File lớn:** dùng streaming/chunking authenticated phù hợp, tránh tải cả file vào RAM, không công bố plaintext trước tag validation?
9. **Vận hành:** có rotation, rewrap, backup/recovery, revoke, audit, quota KMS và xử lý lỗi an toàn?
10. **Kiểm thử:** thử đúng/sai public key, sai OAEP params, byte ciphertext/tag bị sửa, nonce khác, AAD khác, missing/truncated chunk, file lớn và nhiều người nhận?

## 12. Bài tập tự kiểm tra

**Câu 1.** RSA-2048 + OAEP SHA-256 cho phép bao nhiêu byte plaintext trong một RSA operation?  
**Đáp án:** **190 byte** vì `256 - 2*32 - 2 = 190`; ciphertext dài **256 byte**.

**Câu 2.** Muốn RSA-OAEP mã hóa một chuỗi 500 ký tự Unicode trong một lần vì “500 ký tự là ít”, có đúng không?  
**Đáp án:** **Không**. Phải đếm **số byte sau encoding UTF-8** và áp công thức modulus/padding; RSA-2048 OAEP SHA-256 tối đa 190 **byte**, kể cả nội dung chỉ vài chục ký tự Unicode vẫn có thể dài nhiều byte.

**Câu 3.** Nếu có file 1 MiB, chia thành block 190 byte rồi RSA-2048 từng block cần bao nhiêu ciphertext byte?  
**Đáp án:** `ceil(1,048,576/190) = 5,519` block, mỗi block 256 byte, tổng **1.412.864 byte**, chưa metadata.

**Câu 4.** Với RSA-2048 bọc AES-256 key, AES-GCM mã hóa 1 MiB file, tag 16 byte và nonce 12 byte, cần ít nhất bao nhiêu byte cho wrappedKey + nonce + ciphertext + tag?  
**Đáp án:** **256 + 12 + 1.048.576 + 16 = 1.048.860 byte**, chưa header.

**Câu 5.** Một file được mã hóa AES-GCM thành công dưới DEK bọc bằng public key của Bob. Có chứng minh Alice là người gửi không?  
**Đáp án:** **Không**. Ai có public key Bob cũng có thể tạo DEK riêng và mã hóa file. Cần xác thực người gửi bằng cơ chế phù hợp.

**Câu 6.** Có thể lưu nonce/GCM tag bên cạnh ciphertext không?  
**Đáp án:** **Có**; nonce và tag không cần giữ bí mật. Nhưng nonce phải duy nhất với cùng key; tag và metadata phải được xử lý/xác thực chính xác.

**Câu 7.** Nếu giải mã stream trả một số plaintext trước khi kiểm tra tag cuối, có thể để chương trình khác sử dụng phần plaintext đó ngay không?  
**Đáp án:** **Không nên**. Cần giữ cách ly/không công bố cho tới khi tag toàn message hợp lệ, hoặc dùng giao thức authenticated chunk phù hợp.

**Câu 8.** Đổi RSA modulus từ 2.048 lên 4.096 bit là cách tối ưu chính để mã hóa file 5 GB?  
**Đáp án:** **Không**. Chỉ tăng input RSA-OAEP lên vài trăm byte mỗi operation và tăng chi phí phép toán; nên dùng hybrid encryption.

**Câu 9.** Tại sao hybrid dùng cùng một DEK mã hóa toàn bộ file rồi bọc DEK cho nhiều người nhận lại hiệu quả?  
**Đáp án:** Payload được mã hóa đối xứng **một lần**; chỉ việc bọc một DEK nhỏ được lặp theo người nhận. Cần bảo vệ metadata và hiểu rằng người đã có DEK có thể giữ bản sao riêng.

**Câu 10.** Nếu RSA unwrap key thành công nhưng GCM tag sai, có chấp nhận một phần dữ liệu vì “RSA đã xác minh khóa” không?  
**Đáp án:** **Không**. RSA unwrap thành công không thay thế AEAD integrity. Phải coi file không hợp lệ và không công bố plaintext.

### Ghi nhớ

**Mã hóa bất đối xứng không thay thế mã hóa dữ liệu khối lớn.** RSA-OAEP có giới hạn plaintext theo modulus và padding, chi phí phép toán cao, và không cung cấp giao thức xác thực file khi tự chia chunk. Thiết kế phù hợp là **khóa công khai/KEM để bảo vệ hoặc thiết lập DEK nhỏ + AEAD đối xứng để mã hóa payload lớn**, kết hợp kiểm chứng public key, nonce/AAD, định dạng envelope, streaming an toàn và key lifecycle.


</details>

- [Quay lại đầu trang](#back-to-top)
