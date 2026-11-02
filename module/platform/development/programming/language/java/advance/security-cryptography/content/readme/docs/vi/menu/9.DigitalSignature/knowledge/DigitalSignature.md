<a id="back-to-top"></a>

# 9. Chữ ký số

## Menu
- [9.1 Chữ ký số bảo vệ tính toàn vẹn và xác thực nguồn dữ liệu như thế nào](#digital-signature-001)
- [9.2 Chu trình Signature.initSign, update, sign và initVerify, verify](#digital-signature-002)
- [9.3 Phân biệt ký bằng khóa riêng với mã hóa bằng khóa công khai](#digital-signature-003)
- [9.4 RSA PKCS#1 v1.5, RSASSA-PSS, ECDSA và EdDSA](#digital-signature-004)
- [9.5 PSSParameterSpec, digest, MGF1 và độ dài salt của chữ ký](#digital-signature-005)
- [9.6 Thống nhất dữ liệu ký và tránh băm hai lần khi API đã kết hợp digest](#digital-signature-006)
- [9.7 Định dạng chữ ký ECDSA DER và P1363 khi trao đổi với hệ thống khác](#digital-signature-007)
- [9.8 Phân biệt chữ ký sai, dữ liệu sai định dạng và ngoại lệ khi xác minh](#digital-signature-008)
- [9.9 Giới hạn của verify thành công khi khóa công khai chưa được gắn với danh tính tin cậy](#digital-signature-009)

## <a id="digital-signature-001">9.1 Chữ ký số bảo vệ tính toàn vẹn và xác thực nguồn dữ liệu như thế nào</a>

<details>
<summary>Xem chi tiết</summary>

# 9.1. Chữ ký số bảo vệ tính toàn vẹn và xác thực nguồn dữ liệu như thế nào?

## 1. Bài toán: biết dữ liệu có bị sửa và có đến từ đúng nguồn không?

Giả sử hệ thống A gửi cho hệ thống B một yêu cầu thanh toán: `order=42;amount=120000`. B cần phân biệt ba câu hỏi:

1. **Tính toàn vẹn (integrity):** các byte dữ liệu có bị thay đổi sau khi người ký tạo chữ ký không?
2. **Tính xác thực nguồn (source authentication):** chữ ký có được tạo bởi bên nắm giữ **khóa riêng tương ứng với khóa công khai đáng tin cậy** hay không?
3. **Tính bí mật (confidentiality):** người không được phép có đọc được nội dung trên đường truyền không?

**Chữ ký số** giải quyết hai câu hỏi đầu **trong các điều kiện mật mã và tin cậy phù hợp**. Nó không tự mã hóa hoặc che giấu thông điệp. Muốn dữ liệu được giữ bí mật, sử dụng TLS, mã hóa dữ liệu phù hợp hoặc cơ chế bảo mật kênh/lưu trữ khác.

Sự phân biệt quan trọng: **xác minh toán học chữ ký hợp lệ** không đồng nghĩa **đã biết người ký là ai**. Nếu kẻ tấn công tự sinh một cặp khóa, tự ký thông điệp và đưa luôn khóa công khai của mình cho người nhận, việc verify có thể trả `true`. Người nhận chỉ có bằng chứng “ai đó giữ private key đi cùng public key này đã ký”, chứ chưa có bằng chứng đó là đối tác A. Cần cơ chế **gắn public key với danh tính/đối tác được tin cậy**.

### Mục tiêu học tập

Sau bài này, bạn có thể giải thích luồng ký–xác minh, thực hiện ký/xác minh với JCA `Signature` trong Java 17+, phân biệt **integrity**, **source authentication**, **identity/trust**, **confidentiality**, chống phát lại và các giới hạn khác; biết vì sao phải ký **đúng chuỗi byte**, kiểm soát khóa công khai, thuật toán và ngữ cảnh ứng dụng.

## 2. Nguyên lý chữ ký số bất đối xứng

Chữ ký số hiện đại sử dụng một **cặp khóa bất đối xứng**:

- **Private key (khóa riêng):** do bên ký giữ bí mật và dùng khi tạo signature. Người nhận không cần biết khóa này.
- **Public key (khóa công khai):** dùng để kiểm tra chữ ký. Có thể công bố, nhưng **nguồn gốc và quyền sử dụng của nó phải được xác thực**.

Quá trình ở mức khái niệm:

```text
BÊN KÝ (được phép ký)                         BÊN NHẬN / XÁC MINH

Thông điệp M (byte chính xác)                Nhận M' + signature + keyId...
          |                                                |
          v                                                v
Signature.initSign(privateKey)               Tìm publicKey tin cậy của bên ký
Signature.update(M)                                         |
Signature.sign() -> chữ ký S                                v
          |                                   Signature.initVerify(publicKey)
          +---------- M + S ------------>    Signature.update(M')
                                              Signature.verify(S)
                                                         |
                                            true: chữ ký hợp lệ cho M' với key đó
                                            false/lỗi: từ chối, không tin dữ liệu
```

Trong các lược đồ như Ed25519, RSA-PSS hoặc ECDSA, thuật toán ký có **cơ chế băm/biểu diễn thông điệp** riêng được quy định trong thuật toán. Không nên tự nghĩ “chỉ cần SHA-256 rồi mã hóa digest bằng private key” là đủ để tự chế chữ ký số. Cần dùng thuật toán chữ ký được chuẩn hóa qua JCA, cùng tham số, encoding và policy ở cả hai phía.

Tại sao việc sửa dữ liệu bị phát hiện? Chữ ký được ràng buộc mật mã với **nội dung thông điệp** và **private key**. Khi kẻ tấn công sửa một byte của thông điệp được kiểm tra nhưng giữ nguyên chữ ký, phép `Signature.verify` với public key của bên ký hợp lệ sẽ thất bại, trừ khả năng phá vỡ giả định an toàn mật mã hoặc một lỗi trong triển khai/protocol.

**Không cần giữ thông điệp bí mật** để có chữ ký hợp lệ: trong nhiều ứng dụng thông điệp và chữ ký đều được gửi ở dạng rõ. Giá trị của chữ ký là khả năng phát hiện thay đổi và ràng buộc nguồn ký với một public key được xác thực.

## 3. “Bảo vệ tính toàn vẹn” nghĩa là gì?

### 3.1. Chữ ký ràng buộc các byte được ký

Nếu bên A ký byte sequence `M`, bên B kiểm tra đúng `M` với chữ ký `S` và public key tin cậy, kết quả hợp lệ chứng tỏ theo giả định mật mã rằng nội dung `M` chưa bị sửa kể từ khi chữ ký được tạo bởi người nắm khóa riêng.

Nếu B thay `M` bằng `M'` (khác một byte), chữ ký cũ thông thường không còn hợp lệ. Điều này áp dụng cho **từng byte**, không phải chỉ ý nghĩa ngôn ngữ. Ví dụ:

- `"amount=120000"` khác `"amount=120001"`.
- UTF-8 với dấu xuống dòng `LF` khác `CRLF`.
- Hai đối tượng JSON có cùng dữ liệu nhưng thứ tự trường khác nhau thường tạo byte array khác nhau.
- Unicode có thể biểu diễn các ký tự trông giống nhau bằng các chuỗi code point khác nhau.
- Base64 của cùng byte có thể có biến thể format; quy tắc ký **chuỗi Base64** khác quy tắc ký **byte đã decode**.

Do đó, ứng dụng phải định nghĩa **chính xác cái gì được ký**, chuyển đổi thế nào thành `byte[]` và bên nhận dựng lại byte để verify ra sao.

### 3.2. Chữ ký chỉ bảo vệ những trường thật sự nằm trong phạm vi ký

Giả sử thông điệp có các trường `amount`, `currency`, `tenantId` và `expiresAt`. Nếu bên ký chỉ ký `amount` mà không đưa các trường còn lại vào payload được ký, việc thay `tenantId` hoặc `expiresAt` **không nhất thiết làm chữ ký sai**.

Một thiết kế an toàn phải đưa vào phạm vi ký những trường ảnh hưởng tới **ý nghĩa nghiệp vụ**, định tuyến, quyền truy cập, đơn vị tiền, loại thao tác, phiên bản giao thức, thời hạn và định danh yêu cầu — theo định dạng byte rõ ràng.

**Lỗi nguy hiểm:** kiểm tra signature trên phần thân nhưng lại thực thi một số trường header hoặc query string **chưa được ký**, ví dụ chọn tài khoản nhận tiền bằng `accountId` ở URL không được ràng buộc với payload.

### 3.3. Chữ ký không chứng minh nội dung là đúng sự thật

Một tài liệu ký hợp lệ vẫn có thể chứa giá sai, thông tin gian dối hoặc lệnh mà người ký không có thẩm quyền đưa ra. `Signature.verify(...) == true` **không** là kiểm tra business rules, authorization, số dư, hạn mức, tính hợp lệ của hóa đơn hay đồng ý của người dùng.

Tính toàn vẹn ở đây nghĩa là nội dung **được đối chiếu chính xác với thứ đã ký**, chứ không phải “dữ liệu chắc chắn đúng về mặt thực tế”.

## 4. “Xác thực nguồn dữ liệu” được hiểu như thế nào?

### 4.1. Mật mã chứng minh liên hệ với khóa riêng

Với public key `K_pub`, việc chữ ký hợp lệ trên `M` cho thấy có một bên có khả năng sử dụng private key `K_priv` tương ứng đã tạo chữ ký (theo giả định lược đồ chữ ký an toàn). Không thể từ public key và signature hợp lệ suy ngược chắc chắn **tên người, tổ chức, chức danh hoặc sự cho phép hiện tại** của bên ký.

Phải phân biệt:

- **Cryptographic verification:** thuật toán xác minh byte `M`, chữ ký `S` và public key `K_pub` có hợp lệ không?
- **Key authenticity:** vì sao B tin `K_pub` thuộc về đối tác A chứ không phải khóa do kẻ tấn công gài?
- **Authorization:** khóa này được phép ký loại dữ liệu/tenant/hành động nào, trong giai đoạn nào?
- **Attribution/audit:** ai đã sử dụng private key, trong hoàn cảnh nào và có bằng chứng quản trị hỗ trợ hay không?

Thiếu bước **key authenticity**, kẻ tấn công có thể thực hiện *public-key substitution*: tự tạo khóa, tự ký, rồi thay khóa công khai được dùng để verify. Kết quả `true` chỉ xác thực chữ ký của **khóa giả**, không xác thực đối tác mong đợi.

### 4.2. Các cách gắn public key với nguồn đáng tin

Tùy hệ thống:

1. **Pinning/allowlist:** máy nhận lưu fingerprint hoặc public key của đối tác đã phân phối qua kênh tin cậy; chỉ sử dụng key đã đăng ký và chưa bị thu hồi.
2. **Chứng thư số X.509 (PKI):** chứng thư liên kết một public key với danh tính thuộc tính được xác nhận bởi tổ chức cấp chứng thư; bên xác minh phải kiểm tra **chuỗi chứng thư** tới trust anchor đáng tin, hiệu lực, chính sách, tên/định danh, mục đích sử dụng khóa và tình trạng thu hồi khi cần.
3. **Directory/JWKS được bảo vệ:** dùng nguồn public key có xác thực, TLS, cache/rotation và policy; không chấp nhận URL public key tùy ý từ request không tin cậy mà không kiểm tra miền và trust.
4. **HSM/KMS và quyền ký:** private key ở kho khóa được bảo vệ, quyền gọi sign được kiểm soát và ghi audit. Điều này giảm rủi ro lộ khóa và tăng bằng chứng quy trình, nhưng không biến mọi chữ ký hợp lệ thành ủy quyền vô điều kiện.

Trong Java, `X509Certificate.getPublicKey()` chỉ **trả public key**; bản thân lời gọi đó **không thực hiện đầy đủ xác thực trust path** cho chứng thư. Để tin vào danh tính trong môi trường PKI, cần dùng cơ chế xác minh chuỗi/đường chứng thư thích hợp như `CertPathValidator` với policy tin cậy đúng. Không tự xem “chứng thư parse được” là “chứng thư đáng tin”.

### 4.3. Khóa bị lộ và quyền sử dụng sai

Nếu private key bị đánh cắp hoặc bên không được phép được quyền gọi hệ thống ký, kẻ tấn công có thể tạo chữ ký hợp lệ. Phép `Signature.verify` không thể tự phát hiện key compromise. Cần quản lý vòng đời khóa, phân quyền, giám sát, rotation, thời hạn chứng thư, thu hồi khóa và quy trình ứng phó sự cố.

Vì vậy phát biểu chuẩn xác là: **chữ ký hợp lệ + public key được gắn đúng nguồn và được tin cậy theo policy + private key chưa bị xâm phạm** cho bằng chứng mạnh về tính toàn vẹn và nguồn ký. Không nên phát biểu “cứ verify trả true là chắc chắn do người X ký”.

## 5. Java 17+: tạo, ký và xác minh với Signature (Ed25519)

JCA cung cấp engine `java.security.Signature`. Trong ví dụ dùng `Ed25519` (thuộc họ EdDSA) vì cú pháp rõ ràng và được JDK hiện đại hỗ trợ; đây là thuật toán chữ ký, **không phải thuật toán mã hóa dữ liệu**.

Luồng API chuẩn:

- `KeyPairGenerator.getInstance("Ed25519")` và `generateKeyPair()` tạo cặp khóa phù hợp cho demo.
- `Signature.getInstance("Ed25519")` lấy engine theo thuật toán chữ ký.
- `initSign(privateKey)` thiết lập khóa riêng; `update(messageBytes)` nạp đúng byte đã định; `sign()` tạo chữ ký.
- `initVerify(publicKey)` thiết lập khóa công khai; `update(messageBytes)` nạp chính xác cùng byte; `verify(signatureBytes)` trả boolean hoặc ném lỗi cho trường hợp không hợp lệ ở mức API/provider.

**Lưu ý về engine state:** một `Signature` sau khi `initSign` đang ở chế độ ký; sau `initVerify` đang ở chế độ xác minh. Không dùng chung một instance mutable để ký/verify đồng thời ở nhiều thread, và không quên khởi tạo lại cho một thông điệp mới. API `update` cho phép nạp nhiều mảnh; thứ tự các mảnh phải giống hoàn toàn với byte được bên kia kiểm tra.

### 5.1. Chương trình độc lập: DigitalSignatureDemo.java

Ví dụ sinh keypair cho sender và một keypair **khác** để kiểm tra sai khóa. Bên ký tạo signature cho một chuỗi byte có tiền tố `app=orders;v=1;...`; bên nhận xác minh đúng, rồi thử sửa message, sửa signature và thay public key. Không in key hay signature thực tế.

```java
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

public final class DigitalSignatureDemo {
    static byte[] sign(PrivateKey privateKey, byte[] message)
            throws GeneralSecurityException {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(privateKey);
        signer.update(message);
        return signer.sign();
    }

    static boolean verify(PublicKey publicKey, byte[] message, byte[] signature)
            throws GeneralSecurityException {
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(publicKey);
        verifier.update(message);
        return verifier.verify(signature);
    }

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        KeyPair sender = generator.generateKeyPair();
        KeyPair stranger = generator.generateKeyPair();

        byte[] message = "app=orders;v=1;order=42;amount=120000"
                .getBytes(StandardCharsets.UTF_8);
        byte[] signed = sign(sender.getPrivate(), message);
        byte[] changedMessage = message.clone();
        changedMessage[changedMessage.length - 1] ^= 1;

        byte[] changedSignature = signed.clone();
        changedSignature[changedSignature.length - 1] ^= 1;

        System.out.println("Signature length: " + signed.length + " bytes");
        System.out.println("Valid signature: " +
                verify(sender.getPublic(), message, signed));
        System.out.println("Modified message accepted: " +
                verify(sender.getPublic(), changedMessage, signed));
        System.out.println("Wrong public key accepted: " +
                verify(stranger.getPublic(), message, signed));
        System.out.println("Modified signature accepted: " +
                verify(sender.getPublic(), message, changedSignature));
        System.out.println("Same bytes again: " +
                verify(sender.getPublic(), message.clone(), signed));
    }
}
```

Biên dịch và chạy bằng JDK 17+ có provider hỗ trợ Ed25519:

```bash
javac DigitalSignatureDemo.java
java DigitalSignatureDemo
```

Stdout chính xác của ví dụ:

```text
Signature length: 64 bytes
Valid signature: true
Modified message accepted: false
Wrong public key accepted: false
Modified signature accepted: false
Same bytes again: true
```

### 5.2. Giải thích kết quả theo thuộc tính bảo mật

1. **Signature length: 64 bytes:** Ed25519 xuất chữ ký 64 byte theo định dạng thuật toán; đây là một đặc trưng riêng của Ed25519, **không** phải độ dài chữ ký của mọi thuật toán (ví dụ RSA-PSS phụ thuộc cỡ modulus).
2. **Valid signature: true:** message giữ nguyên; public key đúng cặp với private key ký; phép kiểm tra thành công. Trong ví dụ, public key được lấy trực tiếp từ `sender` nên **chưa mô phỏng vấn đề phân phối khóa tin cậy**.
3. **Modified message accepted: false:** chỉ thay byte cuối của message, không tạo chữ ký mới. Điều này minh họa liên kết chữ ký với **byte đã ký**.
4. **Wrong public key accepted: false:** chuyển sang public key của một keypair độc lập thì chữ ký không hợp lệ; chứng tỏ chữ ký phụ thuộc vào khóa tương ứng.
5. **Modified signature accepted: false:** thay một byte của signature mà giữ nguyên message/key cũng khiến kiểm tra thất bại trong ví dụ.
6. **Same bytes again: true:** tạo mảng mới bằng `message.clone()`, nội dung byte không đổi nên verify thành công. **Reference object** của array không ảnh hưởng; chữ ký bảo vệ nội dung byte.

Các biến `sender`/`stranger` được sinh ngẫu nhiên ở mỗi lần chạy nên byte khóa/signature sẽ khác, nhưng các dòng stdout về độ dài và kết quả boolean vẫn như trên. Chương trình cố ý đơn giản để hiểu **cơ chế ký và verify**, không phải ứng dụng production: không lưu khóa, không tạo/trust chứng thư, không truyền thông qua mạng và không tự giải quyết chống replay.

**Với signature hỏng định dạng**, tùy thuật toán/provider, `Signature.verify` có thể trả `false` **hoặc ném `SignatureException`. Không biến exception thành chấp nhận; lớp ứng dụng cần coi cả `false` lẫn lỗi xác minh/malformed input là không hợp lệ, xử lý/log theo chính sách mà không lộ thông tin nhạy cảm.

## 6. Vì sao băm dữ liệu rồi lưu SHA-256 là chưa đủ để xác thực nguồn?

Một phép hash như `SHA-256(message)` có thể phát hiện thay đổi **khi digest kỳ vọng đến từ nguồn được tin cậy và được bảo vệ**. Nhưng nếu kẻ tấn công có thể sửa cả message **và** digest đi kèm, họ tính lại SHA-256 mà không cần bí mật nào. Hash đơn lẻ không tạo bằng chứng về người gửi.

HMAC dùng **khóa bí mật chung**. Ai có khóa đều có thể tạo và xác minh HMAC; vì vậy HMAC phù hợp khi các bên cùng chia sẻ khóa, nhưng bên thứ ba không thể độc lập phân biệt bên nào trong nhóm cùng giữ khóa đã tạo tag.

Chữ ký số dùng **khóa riêng để ký** và **khóa công khai để xác minh**. Nhiều bên có thể xác minh mà không cần biết private key. Khi public key được liên kết với nguồn tin cậy và private key chỉ người ký có quyền dùng, chữ ký mang bằng chứng xác thực nguồn mạnh hơn mô hình shared-secret ở những hệ thống có nhiều bên xác minh.

| Thuộc tính | SHA-256 digest đơn lẻ | HMAC-SHA256 | Chữ ký Ed25519 / RSA-PSS / ECDSA |
| --- | --- | --- | --- |
| Có cần bí mật để tạo mã kiểm tra? | Không | Có, shared key | Có, private key |
| Bên xác minh cần gì? | Digest kỳ vọng đã đáng tin | Cùng khóa bí mật | Public key đáng tin |
| Phát hiện sửa dữ liệu nếu kiểm tra đúng? | Có khi digest kỳ vọng được bảo vệ | Có | Có |
| Xác thực nguồn như một bên giữ khóa? | Không | Có, trong nhóm chia sẻ secret | Có, bên có quyền dùng private key tương ứng |
| Bên xác minh có thể tự tạo mã hợp lệ? | Ai cũng tính digest được | Có, vì cùng giữ secret | **Không**, nếu chỉ có public key |
| Chứng minh danh tính bên ký mà không có trust binding? | Không | Không | **Không** |
| Mã hóa / che giấu nội dung? | Không | Không | Không |
| Chống replay tự động? | Không | Không | Không |

Không nhầm lẫn thuật ngữ: **digital signature** không có nghĩa là “mã hóa bằng private key rồi giải mã bằng public key”. Cách giải thích dân gian đó gây hiểu sai về padding, hash, thuật toán và mục tiêu an ninh. Luôn dùng API ký/xác minh chuẩn `Signature` và lược đồ được duyệt.

## 7. Thiết kế thông điệp ký: phải ký đúng byte và ngữ cảnh

### 7.1. Chọn định dạng byte có thể tái tạo chính xác

Bên ký và bên xác minh phải thống nhất:

- Encoding: dùng UTF-8 hoặc một định dạng nhị phân được quy định, **không** dựa vào charset mặc định.
- Thứ tự field và tên field; loại dữ liệu; quy tắc số (ví dụ `120000` khác `120000.0`); cách mã hóa timestamp/timezone.
- Quy tắc escaping, Unicode normalization, newline và padding; có/không whitespace.
- Version của serialization/canonicalization, và cách xử lý field không biết.
- Nếu dùng JSON/XML, phải ký **byte đã chuẩn hóa theo quy ước rõ ràng** hoặc ký **byte gốc đã nhận/lưu**, thay vì tùy tiện parse rồi tự xuất lại theo cách khác.
- Nếu truyền signature dưới dạng Base64, định nghĩa rõ **ký payload bytes** hay **ký chuỗi Base64 textual representation**; thông thường ký byte payload trước khi encode signature.

Đặc biệt, **nối field độ dài biến đổi không có ranh giới** tạo nguy cơ ambiguity: ký `"ab" + "c"` cho cùng byte như `"a" + "bc"` mặc dù hai cặp trường khác nhau. Dùng binary framing, length prefix, tag–length–value, hoặc canonical format để biểu diễn không mơ hồ.

### 7.2. Domain separation và scope của chữ ký

Nếu cùng một khóa được dùng ở nhiều ứng dụng/giao thức, chữ ký trên chuỗi `"approve=1"` có thể được kẻ tấn công **chuyển ngữ cảnh** sang quy trình khác cũng tin public key đó, nếu cả hai nơi cho thông điệp ký/verify giống byte và không kiểm tra mục đích.

Một cách phòng vệ là **gắn ngữ cảnh vào byte được ký**, ví dụ:

```text
applicationId = orders
schemaVersion = 1
operation = authorize-payment
tenantId = shop-7
orderId = 42
amountMinorUnits = 120000
currency = VND
expiresAt = 2026-12-01T00:00:00Z
requestId = unique-id
```

Đây là **mô tả các trường logic**, không phải byte serialization đã chuẩn hóa. Hãy định nghĩa format chính xác và ký toàn bộ phiên bản byte đã mã hóa. `app=orders;v=1` trong demo minh họa domain prefix, **không** phải sự thay thế cho một schema production có ranh giới trường, quyền, hạn mức và version chặt chẽ.

Dùng **khóa khác nhau theo mục đích** khi phù hợp, ví dụ tách khóa ký giao dịch khỏi khóa ký gói cập nhật; cả bên nhận và bên ký phải kiểm soát loại thao tác được phép thực hiện.

### 7.3. Ký file lớn hoặc dữ liệu dạng stream

`Signature.update(byte[])` có thể gọi nhiều lần với các chunk liên tiếp rồi `sign()` hoặc `verify(...)`. Khi cập nhật từng chunk, **thứ tự và toàn bộ byte** phải đúng như khi ký một lần. Tránh tự băm bên ngoài rồi lại yêu cầu `Ed25519` ký digest khi protocol thực tế quy định ký **message nguyên bản**: điều đó thay đổi nội dung được ký (và với Ed25519 không đồng nghĩa tự động chuyển sang chế độ Ed25519ph).

Không kiểm tra một bản sao file rồi thực thi/tin **bản sao khác có thể bị thay đổi giữa hai bước**. Với artefact nhạy cảm, đảm bảo dữ liệu được verify là chính dữ liệu sẽ sử dụng; cân nhắc stream/buffer immutable, atomic storage, file handle và giới hạn TOCTOU theo môi trường.

## 8. Chọn thuật toán và làm việc với provider Java

### 8.1. Thuật toán ký phổ biến

| Thuật toán | Đặc điểm cần lưu ý |
| --- | --- |
| **Ed25519** | Thuộc EdDSA, thường đơn giản để dùng, chữ ký cố định 64 byte; có sẵn trong các JDK 17 hiện đại khi provider hỗ trợ. |
| **SHA256withECDSA** | ECDSA trên đường cong phù hợp, với SHA-256; kích thước/định dạng public key và signature phải thống nhất. Java `Signature` thường biểu diễn chữ ký ECDSA bằng mã hóa ASN.1 DER cho cặp `r,s`. |
| **RSASSA-PSS** | Chữ ký RSA-PSS; cần thống nhất digest, MGF1 digest, salt length, trailer field, độ dài modulus; tránh các cấu hình không nhất quán hoặc bị downgrade. |
| **SHA256withRSA** | Thường biểu thị RSA PKCS#1 v1.5 với SHA-256 trong JCA; có thể cần vì tương thích cũ, nhưng thiết kế mới thường cân nhắc Ed25519 hoặc RSA-PSS theo tiêu chuẩn/chính sách tương thích. |

Không đồng nhất “ECDSA” với “ECDH”: ECDH là thỏa thuận khóa, không phải chữ ký. Không dùng khóa mã hóa RSA tùy tiện để thay cho khóa ký nếu mục đích, key usage và quản trị khóa tách biệt.

**Không bao giờ cho request không đáng tin tự chọn mọi algorithm/provider mà hệ thống sẽ chấp nhận.** Header `alg` hoặc thông tin tương đương có thể là dữ liệu giúp định tuyến theo một allowlist/khóa đã cấu hình; phải kiểm tra thuật toán thực sự được phép cho đúng key và use case để tránh algorithm-confusion/downgrade.

### 8.2. JCA Signature là API theo trạng thái

Với `Signature`, quy trình gồm `getInstance` → `initSign` hoặc `initVerify` → nhiều lần `update` → `sign`/`verify`. Những lỗi thường gặp:

- Gọi `update` trước `initSign/initVerify`: sai trạng thái engine.
- Lấy `Signature.getInstance("Ed25519")` nhưng public key không đúng loại hoặc chữ ký được tạo bằng lược đồ khác.
- Dùng sai tham số PSS giữa hai phía.
- Xác minh trên byte đã bị parse/serialize lại không giống bản đã ký.
- Giả định `Signature` thread-safe để dùng chung nhiều request mà không đồng bộ.
- Coi `Signature.verify(...)` thành công là **chứng thực danh tính** dù public key chỉ lấy từ request.

### 8.3. Lưu, trao đổi và xoay vòng khóa

Trong Java, `PublicKey.getEncoded()` của nhiều khóa thông thường dùng định dạng `X.509 SubjectPublicKeyInfo (SPKI)`; `PrivateKey.getEncoded()` (nếu khóa có thể export) thường là `PKCS#8`. **Không** hiểu định dạng key encoding là chứng thư X.509 đã được xác nhận: SPKI chỉ biểu diễn thông tin public key.

Private key cần lưu trong `KeyStore`, HSM, KMS hoặc hệ thống khóa được bảo vệ phù hợp. Hạn chế xuất private bytes, quyền sign và việc ghi vào log/config. Public key có thể được phân phối nhưng phải kiểm tra identity binding và key rotation; khi thay key, không mặc định bỏ mọi chữ ký cũ hay chấp nhận key cũ vô thời hạn. Thiết kế keyId, thời gian hiệu lực, phiên bản và quy tắc thu hồi cho dữ liệu ký lâu dài.

## 9. Các giới hạn bảo mật mà chữ ký số không giải quyết tự động

### 9.1. Không cung cấp tính bí mật

Người thấy `message + signature` vẫn đọc được message nếu nó truyền dạng rõ. Cần TLS/AEAD hoặc mã hóa thích hợp khi payload nhạy cảm. **Ký rồi mã hóa** hoặc **mã hóa rồi ký** đều có thể xuất hiện trong các giao thức chuẩn, nhưng không tự ghép các bước này mà thiếu thiết kế vì có nhiều vấn đề liên quan đối tượng được ký và cách xác minh.

### 9.2. Không tự chống replay

Một request ký hợp lệ ở thời điểm T có thể được gửi lại nguyên vẹn ở T+1; chữ ký vẫn hợp lệ với cùng byte message. Nếu nghiệp vụ yêu cầu chống phát lại:

- Ký `requestId`/nonce nghiệp vụ duy nhất, timestamp/expiry và các trường ngữ cảnh cần thiết.
- Bên nhận kiểm tra thời hạn với độ lệch đồng hồ hợp lý.
- Lưu trạng thái xử lý `requestId` hoặc sequence number để từ chối trùng.
- Dùng giao thức idempotency khi hành động có thể được retry hợp lệ.

Lưu ý **timestamp được ký** mới bảo vệ không bị sửa tùy ý; timestamp *không* tự chặn replay nếu server không áp dụng cửa sổ thời gian hoặc cơ chế chống dùng lại.

### 9.3. Không tự đảm bảo quyền và tính chính xác của nghiệp vụ

Bên ký đã xác thực có thể không được phép thay đổi tenant khác, vượt hạn mức hoặc ký một loại thông điệp ngoài quyền hạn. Sau khi verify, server vẫn phải kiểm tra **authorization**, policy theo keyId/identity, trạng thái tài khoản, hạn mức, schema và điều kiện nghiệp vụ.

### 9.4. Không chứng minh tuyệt đối “không thể chối bỏ”

Chữ ký số có thể hỗ trợ bằng chứng về nguồn ký, nhưng **non-repudiation** ở góc độ pháp lý/vận hành còn phụ thuộc vào việc kiểm soát private key, nhận dạng người ký, chứng thư, chứng cứ thời gian, quy trình cấp quyền, audit và khả năng khóa bị lộ. Nếu một private key được chia sẻ cho nhiều nhân viên hoặc dịch vụ, verify không phân biệt chính xác cá nhân nào đã dùng khóa đó.

Chữ ký hợp lệ không chứng minh con người đã đọc, hiểu hoặc đồng ý với toàn bộ điều khoản, cũng không chứng minh nội dung không bị tạo ra bởi máy có quyền ký nhưng bị xâm phạm.

### 9.5. Không xác minh trust chain thay cho ứng dụng

Nếu ứng dụng tải một certificate bất kỳ từ mạng rồi lấy `certificate.getPublicKey()` để verify, việc trả `true` không thay thế xác minh certificate chain, trust anchor, name/identity, key usage, effective validity/revocation và policy. Tương tự, một key ID trong header mà client điều khiển chỉ nên dùng làm *gợi ý tra khóa dưới policy*, không phải chứng cứ danh tính.

## 10. Quy trình xác minh ở ứng dụng thực tế

Một hệ thống nhận chữ ký nên thực hiện các bước có thứ tự:

1. **Đọc envelope dưới giới hạn an toàn:** kích thước message, length của signature, encoding Base64, version, keyId, content type; loại bỏ malformed input. Không phân tích JSON thiếu giới hạn hoặc tin trường do client cung cấp.
2. **Xác định key và thuật toán được phép:** tra allowlist/key registry/truststore; nếu dùng X.509, kiểm tra trust path và policy; xác định quyền ký loại thông điệp này.
3. **Chuẩn hóa hoặc lấy byte được ký theo đúng giao thức:** không reformat âm thầm; include version, purpose, tenant, amount, currency và hạn sử dụng nếu protocol yêu cầu.
4. **Xác minh mật mã:** `Signature.initVerify(trustedPublicKey)`, `update(exactMessageBytes)`, `verify(signatureBytes)`. Chỉ coi thành công nếu trả true và không có lỗi.
5. **Xác minh ngữ cảnh và nghiệp vụ:** key còn được phép, schema/authorization, signed timestamp/expiry, replay/idempotency, trạng thái tài nguyên.
6. **Chỉ sau đó** chấp nhận, lưu hoặc thực thi dữ liệu; audit việc xác minh và key identifier bằng metadata cần thiết, không log private key hay dữ liệu nhạy cảm.

**Thứ tự có thể cần một số bước parse sơ bộ trước khi verify** để tìm keyId/version, nhưng các trường sơ bộ này phải luôn được xem là **chưa xác thực** cho đến khi chữ ký và policy thành công. Tránh quyết định có tác dụng không thể đảo ngược trước bước xác minh.

### Ví dụ lỗi: xác minh đúng chữ ký nhưng sai đối tượng dữ liệu

Giả sử phần JSON body `{"amount":120000}` được ký, trong khi server lấy `currency=USD` từ query string chưa ký. Bên tấn công có thể giữ nguyên body+signature rồi thay query string từ `VND` sang `USD` mà chữ ký vẫn hợp lệ cho body. Khắc phục: **ràng buộc currency vào dữ liệu ký** và dùng cùng nguồn dữ liệu đã ký khi thực thi.

### Ví dụ lỗi: tin public key do người gửi tự đưa

Request chứa `payload`, `signature`, `publicKey`. Server chạy `verify(publicKey, payload, signature)` và cho phép thao tác nếu true. Đối thủ tự tạo keypair, ký payload của chính mình, gửi cả ba và được chấp nhận. Khắc phục: đối chiếu public key với **registry/trust anchor do server quản lý**, rồi kiểm tra quyền hạn theo identity/scope; không dựa vào public key trôi nổi trong request.

## 11. Bảng kiểm nhanh về bảo đảm và giới hạn

| Nhận định | Kết luận chính xác |
| --- | --- |
| Chữ ký số có thể phát hiện thông điệp đã bị sửa sau khi ký không? | **Có**, nếu ký/xác minh trên đúng byte và thuật toán/khóa đủ an toàn. |
| Verify true có xác thực danh tính người ký không? | **Chỉ khi** public key đã được gắn với đúng danh tính và policy đáng tin, có xét nguy cơ khóa bị lộ. |
| Ai có public key đều có thể tạo chữ ký hợp lệ không? | **Không** theo giả định an toàn của chữ ký; cần private key hoặc quyền sử dụng nó. |
| Verify true có đảm bảo message đúng về mặt nghiệp vụ không? | **Không**; cần validation và authorization. |
| Chữ ký có mã hóa dữ liệu không? | **Không**; signed payload vẫn có thể đọc được. |
| Chữ ký có chống replay không? | **Không tự động**; cần dữ liệu chống replay được ký và kiểm tra phía server. |
| Chữ ký có bảo vệ các trường ngoài phần byte được ký không? | **Không**; phải đưa trường có ý nghĩa vào phạm vi ký. |
| SHA-256 checksum và chữ ký số có tương đương không? | **Không**; hash không có private key để xác thực nguồn. |
| HMAC và chữ ký có cùng mô hình phân phối khóa không? | **Không**; HMAC dùng khóa chung, chữ ký dùng private/public key. |
| Chứng thư X.509 parse được có nghĩa nó đáng tin không? | **Không**; cần xác minh trust path và các ràng buộc thích hợp. |
| Chữ ký hợp lệ có chứng minh chắc chắn một người cụ thể tự tay thao tác không? | **Không**; cần chứng cứ kiểm soát khóa/quy trình và các yếu tố liên quan. |

## 12. Lỗi phổ biến khi học và triển khai

- **“Sign là encrypt bằng private key.”** Sai mô hình; dùng `Signature` và các lược đồ chuẩn, không xây thuật toán ký thủ công.
- **Tự ký rồi tin public key tự gửi.** Chỉ xác thực một cặp khóa do request cung cấp; không chứng minh nguồn được ủy quyền.
- **Ký thiếu trường nghiệp vụ.** Chữ ký không bảo vệ currency, tenant, account, expiry hoặc action nếu chúng không ở trong byte ký.
- **Sign và verify chuỗi được serialize khác nhau.** Byte mismatch gây verify false dù đối tượng có vẻ giống nhau; chuẩn hóa format rõ ràng.
- **Dùng key sign sai algorithm/parameter.** Không nhất quán Ed25519/ECDSA/RSA-PSS, signature encoding hoặc hash khiến xác minh lỗi.
- **Không phân biệt lỗi format với chữ ký đúng.** `Signature.verify` có thể false hoặc exception; cả hai đều không được chấp nhận.
- **Tin verify success là đủ để thực thi.** Vẫn cần kiểm tra policy, quyền ký, chống replay, hạn dùng và nghiệp vụ.
- **Nhầm non-repudiation thành bảo đảm tuyệt đối.** Private key có thể bị chia sẻ, bị đánh cắp, hoặc dịch vụ ký có thể bị lạm dụng.
- **Không bảo vệ private key.** Đặt key ở repo, log, container image, CI artifacts hoặc file world-readable làm suy yếu nguồn xác thực.
- **Bỏ qua key rotation/revocation.** Chấp nhận key đã bị hủy hoặc không còn quyền ký vô thời hạn.
- **Gọi `Signature.update` trên các chunk sai thứ tự.** Chữ ký gắn với concatenation byte chính xác.
- **Nhầm signature với confidentiality.** Dữ liệu ký có thể bị đọc; cần bảo vệ bí mật độc lập.

## 13. Tự kiểm tra và bài tập thực hành

1. Nếu người gửi tự sinh keypair và gửi `message, signature, publicKey`, verify true chứng minh **được** và **chưa chứng minh được** điều gì?
2. Nếu thay một byte `amount` nhưng giữ chữ ký cũ, phép kiểm tra bằng public key chuẩn phản ứng ra sao? Vì sao?
3. Hai hệ thống dùng cùng thông tin JSON nhưng một hệ thống đổi thứ tự field trước khi verify. Có thể xảy ra lỗi nào? Cách định nghĩa canonical byte là gì?
4. Vì sao phải ký `currency`, `tenantId`, `operation` và `expiresAt` nếu chúng ảnh hưởng đến quyền hoặc kết quả xử lý?
5. Bên xác minh sở hữu public key nhưng không có private key. Họ có thể kiểm tra chữ ký không? Có thể tạo chữ ký mới hợp lệ không?
6. Digest SHA-256, HMAC-SHA256 và Ed25519 khác nhau ở yêu cầu khóa và xác thực nguồn ra sao?
7. Bản ghi ký hợp lệ từ hôm qua được phát lại hôm nay. Vì sao `Signature.verify` có thể vẫn trả true, và ứng dụng cần bổ sung gì?
8. Một certificate X.509 có public key nhưng do CA không tin cậy phát hành. `Signature.verify` true có đủ để gắn danh tính không?
9. Vì sao private key bị lộ làm suy yếu kết luận về nguồn ký dù thuật toán Ed25519 vẫn hoạt động chính xác?
10. Tại sao chữ ký hợp lệ **không** thay thế kiểm tra authorization hoặc không tự đảm bảo confidentiality?

**Thực hành 1:** chạy `DigitalSignatureDemo`, đổi byte đầu/byte cuối của message; xác nhận verify false. Đổi `"amount=120000"` sang `"amount=120001"` trước khi verify nhưng giữ signature cũ.

**Thực hành 2:** mở rộng message với `tenantId`, `currency`, `expiresAt` và serialize bằng một format không mơ hồ; viết test mà đổi mỗi trường đều khiến signature sai. Đảm bảo sender và receiver có test vector cùng byte UTF-8.

**Thực hành 3:** mô phỏng key registry có hai public key cho hai đối tác khác nhau. Chỉ cho phép đối tác A dùng public key A để ký `orders`. Verify với key B phải thất bại hoặc bị policy từ chối; không cho người gửi tự thay key tùy ý.

**Thực hành 4:** thêm `requestId` và `expiresAt` vào nội dung ký và tạo state cache kiểm soát request đã xử lý; quan sát rằng `verify` không tự phát hiện trùng, nhưng application có thể từ chối lần replay thứ hai.

## 14. Tham khảo chính thức

- [Signature — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Signature.html): `getInstance`, `initSign`, `initVerify`, `update`, `sign`, `verify`, lỗi và lifecycle.
- [KeyPairGenerator — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyPairGenerator.html): sinh cặp public/private key.
- [Java Security Standard Algorithm Names — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html): tên thuật toán như `Ed25519`, `SHA256withECDSA` và `RSASSA-PSS` theo provider/spec.
- [X509Certificate — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/X509Certificate.html): truy cập public key và thuộc tính chứng thư; không phải kiểm tra trust chain hoàn chỉnh.
- [CertPathValidator — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertPathValidator.html): xác thực certification path tới trust anchor bằng policy phù hợp.
- [JCA Reference Guide — Java SE 17](https://docs.oracle.com/en/java/javase/17/security/java-cryptography-architecture-jca-reference-guide.html): nền tảng provider, public key cryptography và signature engine.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="digital-signature-002">9.2 Chu trình Signature.initSign, update, sign và initVerify, verify</a>

<details>
<summary>Xem chi tiết</summary>

# 9.2 Chu trình `Signature.initSign`, `update`, `sign` và `initVerify`, `verify`

## Mục tiêu học tập

`java.security.Signature` là **engine API của Java Security Architecture (JCA)** để tạo và kiểm tra **chữ ký số**. Một đối tượng `Signature` vận hành theo chu trình có trạng thái: phải chọn thuật toán, khởi tạo đúng **khóa** và **chiều thao tác**, cung cấp đầy đủ các byte thông điệp, rồi gọi `sign()` hoặc `verify()`.

Kết thúc bài, bạn có thể:

- Phân biệt hai chu trình **ký bằng private key** và **xác minh bằng public key**.
- Hiểu từng lệnh `getInstance`, `initSign`, `initVerify`, `update`, `sign` và `verify`, thứ tự hợp lệ và việc **reset trạng thái** sau thao tác.
- Đưa một thông điệp vào `update()` theo **nhiều chunk** mà vẫn đảm bảo tất cả byte được ký và xác minh đúng thứ tự.
- Thiết lập tham số của thuật toán chữ ký như `RSASSA-PSS` rõ ràng và thực hành trên **Java 17+**.
- Phân biệt kết quả `verify() == false` với **exception**, và tránh các lỗi liên quan đến encoding, nhầm key, nội dung thiếu, provider hoặc xử lý dữ liệu chưa xác thực.

> **Phạm vi:** Bài này hướng dẫn API `Signature` và chu trình thao tác chữ ký trong Java. Chữ ký số **không mã hóa dữ liệu**: người khác vẫn đọc được thông điệp nếu ứng dụng không mã hóa riêng. Việc xác thực chữ ký với một public key chỉ trở thành xác thực *danh tính người ký* khi public key được gắn đáng tin với danh tính ấy.

## 1. Bức tranh tổng thể: ai ký, ai xác minh?

Giả sử Alice gửi thông điệp cho Bob:

```text
ALICE (người ký)                            BOB (người kiểm tra)
-----------------                          --------------------
Message M (bytes)                          Nhận Message M + Signature S
      |                                                |
Private key của Alice                         Public key của Alice đã xác thực
      |                                                |
Signature.initSign(private)                    Signature.initVerify(public)
      |                                                |
Signature.update(M)                            Signature.update(M)
      |                                                |
Signature.sign()  -> chữ ký S                   Signature.verify(S) -> true/false
      |                                                |
Gửi M và S -------------------------------------------->|
```

- `initSign(privateKey)` yêu cầu **private key** có quyền ký, phù hợp với thuật toán đã chọn.
- `initVerify(publicKey)` yêu cầu **public key** tương ứng với khóa mà người ký đã dùng; bên xác minh cần **tin cậy cách public key được cung cấp**.
- `update(...)` cung cấp **message bytes** theo đúng thứ tự. Hai bên phải tạo ra **cùng chuỗi byte**: thay nội dung, thứ tự chunk, encoding hoặc metadata đã ký làm thay đổi đối tượng được xác minh.
- `sign()` trả `byte[]` chứa chữ ký, **không** trả ciphertext của thông điệp.
- `verify(signatureBytes)` thường trả `true` hoặc `false` nếu chữ ký được phân tích hợp lệ; ngoại lệ có thể xảy ra nếu trạng thái hoặc định dạng chữ ký không hợp lệ.

Đối với một tài liệu thực tế, bạn thường gửi/lưu **(message, signature, key identifier, algorithm ID / parameters)** trong một định dạng có phiên bản. Không truyền private key cùng chữ ký.

## 2. Signature là một state machine có ba giai đoạn

Theo Java SE 17 API, đối tượng `Signature` có ba trạng thái khái niệm:

| Trạng thái | Được tạo khi nào? | Thao tác hợp lệ chính |
| --- | --- | --- |
| **UNINITIALIZED** | Vừa `Signature.getInstance(...)`, chưa chỉ định key/operation | `initSign(...)` hoặc `initVerify(...)`; chưa được `update`, `sign`, `verify`. |
| **SIGN** | Sau `initSign(privateKey)` | `update(...)` một hoặc nhiều lần rồi `sign()`. |
| **VERIFY** | Sau `initVerify(publicKey)` / `initVerify(certificate)` | `update(...)` một hoặc nhiều lần rồi `verify(signature)`. |

```text
                       Signature.getInstance("RSASSA-PSS")
                                      |
                                      v
                               UNINITIALIZED
                                 /         \
                 initSign(private)      initVerify(public)
                             |               |
                             v               v
                           SIGN             VERIFY
                             |               |
                       update(...)       update(...)
                             |               |
                             v               v
                          sign()          verify(S)
                             |               |
                      sinh byte[] S       true / false
                             |               |
             Reset về trạng thái SIGN    Reset về VERIFY
                 (cùng key đã init)        (cùng key đã init)
```

**Reset sau khi hoàn thành:** Đặc tả API cho biết `sign()` đưa object về trạng thái như sau `initSign` thành công trước đó, còn `verify()` đưa object về trạng thái như sau `initVerify` thành công trước đó. Vì thế có thể gọi `update()` và `sign()`/`verify()` cho **thông điệp mới** mà không khởi tạo lại cùng key, miễn provider/algorithm cho phép theo API contract. Ví dụ ở phần 6 sẽ kiểm chứng điều này.

**Reset không có nghĩa xóa key, không có nghĩa tự chuyển qua chiều còn lại.** Nếu cần đổi key, đổi từ ký sang xác minh, hoặc thay bộ tham số, phải thực hiện khởi tạo/cấu hình phù hợp. Trong server, cách dễ quản lý là **mỗi operation dùng một đối tượng riêng** và không chia sẻ mutable `Signature` giữa nhiều thread/request.

Không nên gọi `sign()` khi object đang ở `VERIFY`, hay `verify()` khi object đang ở `SIGN`. `SignatureException` thường báo trạng thái không phù hợp.

## 3. Pha ký: `getInstance` → `initSign` → `update` → `sign`

### 3.1 `Signature.getInstance(algorithm)` chọn scheme chữ ký

```java
// Minh họa đặt trong phương thức có xử lý GeneralSecurityException:
Signature signer = Signature.getInstance("SHA256withRSA");
```

- `getInstance` chọn **scheme chữ ký** qua JCA provider registry, **chưa ký hoặc xác minh**. `Signature` **không có thuật toán mặc định**; tên phải được chỉ rõ.
- `SHA256withRSA` là scheme chữ ký RSA với SHA-256 theo standard algorithm name (thường gắn với RSASSA-PKCS1-v1_5).
- `RSASSA-PSS` là scheme chữ ký RSA-PSS có tham số digest/MGF1/salt length; `Ed25519` là scheme EdDSA có thuật toán khác. Không thay qua lại tên chỉ vì đều dùng public/private keys.
- Java SE 17 **yêu cầu** các implementation có `SHA256withRSA` trong tập `Signature` tối thiểu; tên `RSASSA-PSS` hoặc `Ed25519` cần kiểm tra hỗ trợ JDK/provider đích theo yêu cầu triển khai.

Nếu cố định provider bằng `Signature.getInstance(name, "ProviderName")`, mã nguồn phụ thuộc vào provider đó được đăng ký và hỗ trợ scheme; chỉ định provider có chủ đích khi HSM/chính sách bắt buộc.

### 3.2 `initSign(privateKey)` chọn chiều ký và khóa

```java
signer.initSign(privateKey);
```

`privateKey` phải thuộc thuật toán/scheme phù hợp; khóa RSA private không tự động dùng được để ký Ed25519. `initSign` có overload `initSign(privateKey, secureRandom)` nếu scheme cần randomness do ứng dụng cung cấp. Hầu hết ứng dụng nên dùng nguồn ngẫu nhiên mật mã an toàn từ provider/JDK; không truyền `java.util.Random`.

**Không dùng public key để `initSign`.** Nếu khai báo nhầm khóa, code thường không biên dịch vì kiểu yêu cầu là `PrivateKey`; nếu khóa private sai loại/kích thước/quyền sử dụng, có thể nhận `InvalidKeyException`.

Khóa có thể được sinh bởi `KeyPairGenerator`, đọc từ keystore, KMS/HSM qua provider, hoặc được cung cấp theo cơ chế đáng tin. Trong ví dụ, tạo khóa RSA-2048 **mới trong bộ nhớ** để thao tác có thể chạy độc lập.

### 3.3 `update(...)` nạp byte thông điệp

```java
signer.update(headerBytes);
signer.update(payloadBytes, 0, payloadBytes.length);
byte[] signature = signer.sign();
```

Mỗi lần `update` đóng góp byte vào **cùng một thông điệp logic**, theo **thứ tự các lệnh gọi**. Về mặt nội dung ký, các lệnh trên tương đương một lần `update()` với `headerBytes || payloadBytes`.

Các overload:

| API | Ý nghĩa |
| --- | --- |
| `update(byte value)` | Cấp một byte. |
| `update(byte[] data)` | Cấp toàn bộ mảng. |
| `update(byte[] data, int off, int len)` | Cấp đúng `len` byte bắt đầu từ offset `off`; `len` là **số byte**, không phải chỉ số kết thúc. |
| `update(ByteBuffer data)` | Cấp các byte trong vùng `remaining()`, từ `position()` tới `limit()`; khi trả về, `position` đến `limit`. |

`update()` ở trạng thái chưa init sẽ ném `SignatureException`. Nó **không** trả chữ ký, không lấy message từ `String` tự động. Vì vậy phải chọn charset/encoding một cách tường minh, ví dụ `message.getBytes(StandardCharsets.UTF_8)`.

Với các scheme như `SHA256withRSA` hoặc RSA-PSS cấu hình SHA-256, engine thường thực hiện băm nội bộ. **Không tự băm message bằng `MessageDigest` rồi đưa digest thay message vào `Signature.update()`**, trừ khi giao thức/thuật toán được chọn yêu cầu đúng kiểu đó. Làm vậy thường dẫn đến ký trên **digest như một message mới**, gây sai phạm vi dữ liệu hoặc “double hashing”.

### 3.4 `sign()` kết thúc và trả chữ ký

```java
byte[] result = signer.sign();
```

- Kết quả là **chữ ký nhị phân**, có thể mã hóa Base64 khi cần vận chuyển qua JSON/text nhưng Base64 **không cung cấp bảo mật**.
- Độ dài/encoding phụ thuộc scheme và khóa. Với RSA modulus 2048 bit, chữ ký RSA-PSS dài **256 byte**, không phụ thuộc message ngắn/dài; nhiều scheme khác có định dạng/kích thước khác.
- Một số scheme có randomness (như PSS) nên **ký lại cùng message/key có thể tạo chữ ký khác**, dù cả hai xác minh đúng. Không dùng so sánh raw signature bytes như bằng chứng hai message tương đương.
- Sau khi `sign()`, engine trở về **SIGN initialized state**, dữ liệu vừa `update()` không tự trở thành message tiếp theo. Muốn ký message mới phải cấp lại byte của message đó.
- Gọi `sign()` mà không `update()` có thể ký **thông điệp rỗng**, không phải bản sao message trước đó. Đây là lỗi logic khó phát hiện nếu ứng dụng mong “dùng lại dữ liệu cũ”.

Overload `sign(outbuf, offset, len)` ghi vào một vùng của mảng output và trả số byte thực ghi; kiểm tra buffer size và phạm vi chính xác. Với đa số ví dụ, `byte[] sign()` rõ ràng hơn.

## 4. Pha xác minh: `getInstance` → `initVerify` → `update` → `verify`

```java
// Minh họa trong phương thức có xử lý GeneralSecurityException:
Signature verifier = Signature.getInstance("SHA256withRSA");
verifier.initVerify(trustedPublicKey);
verifier.update(receivedMessageBytes);
boolean valid = verifier.verify(receivedSignatureBytes);
```

### 4.1 `initVerify(publicKey)` khởi tạo bằng khóa công khai

Dùng **public key tương ứng người ký**, đã được xác thực qua hệ thống tin cậy của ứng dụng. Nếu bên gửi dùng `private_A`, bên nhận dùng `public_A`; `public_B` thuộc người khác sẽ không xác minh chữ ký A (với cặp khóa và scheme an toàn).

Có overload `initVerify(Certificate certificate)` đọc public key từ chứng chỉ. **Đừng hiểu rằng chỉ gọi `initVerify(certificate)` là đã xác thực chuỗi chứng chỉ, hostname, trạng thái thu hồi hoặc danh tính ứng dụng.** Việc xác minh độ tin cậy/path validation của X.509, subject identity và mục đích key usage phải được xử lý theo quy tắc của hệ thống; `Signature.verify` chỉ kiểm tra chữ ký dưới khóa cung cấp. `initVerify(certificate)` còn có thể từ chối khóa/chứng chỉ không phù hợp với mục đích ký.

### 4.2 `update` ở bên nhận cũng nạp *toàn bộ* message

`verifier.update(...)` phải có **chính xác chuỗi byte đã được ký**, dù bên ký và bên nhận chia chunk khác nhau:

```text
Bên ký:      update([A B]) ; update([C D E])    => A B C D E
Bên kiểm tra: update([A]) ; update([B C D E])    => A B C D E
                                              (tương đương)

Bên kiểm tra: update([C D E]) ; update([A B])    => C D E A B
                                              (khác message)
```

Không cần cùng kích thước chunk; **cần cùng thứ tự và cùng byte**. Đừng chỉ ký phần body rồi tin header chưa được ký: dữ liệu quan trọng như version, message type, algorithm/key ID, tenant, user, amount, recipient, expiry hoặc context phải được đưa vào dữ liệu ký với encoding không mơ hồ.

### 4.3 `verify(signature)` trả boolean, không trả dữ liệu

`verify()` kiểm tra chữ ký với message đã `update` và public key đã `initVerify`:

- **`true`:** chữ ký hợp lệ **đối với chính message bytes, scheme, params và public key đang sử dụng**.
- **`false`:** chữ ký đã phân tích được nhưng không khớp với message/key/params; phải từ chối.
- **`SignatureException`:** object chưa init đúng, chữ ký có format/loại không hợp lệ, hoặc provider không thể xử lý; cũng **phải từ chối**. Không nên giả định mọi input chữ ký sai đều trả `false`.
- **Sau `verify()`:** engine reset về trạng thái **VERIFY đã init**; lần tiếp theo muốn kiểm tra message mới thì `update()` lại message mới và `verify()` trên chữ ký mới.

`verify()` **không giải mã** dữ liệu và **không tự đánh giá danh tính người ký**. Nếu public key do request tự cung cấp thì attacker có thể tự tạo key pair, ký message và đưa public key của mình để `verify == true`. Hệ thống cần ràng buộc public key với identity/quyền sử dụng trước khi xem chữ ký là bằng chứng từ một chủ thể cụ thể.

## 5. Chu trình và lỗi trạng thái cần ghi nhớ

| Tình huống | Kết quả hoặc rủi ro | Cách dùng đúng |
| --- | --- | --- |
| `Signature.getInstance(...)` rồi `update(...)` ngay | Chưa init; thường `SignatureException` | Gọi `initSign(private)` hoặc `initVerify(public)` trước. |
| `initSign(private)` rồi `verify(signature)` | Sai chiều thao tác | Gọi `sign()` ở chiều SIGN; hoặc `initVerify(public)` rồi nạp message và `verify()`. |
| `initVerify(public)` rồi `sign()` | Sai chiều thao tác | Dùng chiều VERIFY. |
| `initSign(private)` → `update(A)` → `sign()` → `update(B)` → `sign()` | Hai chữ ký cho **A** và **B**; message không cộng dồn qua lần `sign` | Đây là reset được tài liệu hóa; có thể `initSign` lại để tách operations rõ hơn. |
| `initVerify(public)` → `update(A)` → `verify(SA)` → `update(B)` → `verify(SB)` | Hai lượt verify liên tiếp; object reset về VERIFY sau mỗi lượt | Dùng object riêng cho mỗi request nếu có concurrency. |
| Bên ký update `A\|\|B`, bên nhận update `B\|\|A` | Chữ ký không hợp lệ vì byte khác | Chuẩn hóa layout, thứ tự, encoding. |
| Bên nhận chỉ update body, bỏ header đã được ký | Verify sai; hoặc thiếu kiểm tra tính xác thực metadata nếu header vốn không được ký | Ký/xác minh **đủ byte của envelope đã định nghĩa**. |
| Thay đổi params PSS giữa ký và verify | Có thể verify thất bại hoặc lỗi tham số | Quy định digest, MGF1, salt length và trailer field giống nhau. |
| Dùng thuật toán ký không phù hợp public/private key | `InvalidKeyException` hoặc lỗi provider | Kiểm tra key algorithm, purpose, provider và scheme. |
| Chia sẻ cùng instance `Signature` giữa các request/thread | Mutable state trộn bytes và khóa | Tạo object riêng theo operation; không cho nhiều thread `update` đồng thời. |

## 6. Ví dụ Java 17+ hoàn chỉnh: RSA-PSS, nhiều `update` và tái sử dụng đúng chu trình

Chương trình **`SignatureLifecycleDemo.java`** dưới đây:

1. Sinh key pair RSA-2048; tạo engine `RSASSA-PSS` với tham số **SHA-256**, **MGF1(SHA-256)**, **salt dài 32 byte** và **trailer 1**.
2. Ký `header || body` bằng hai lần `update`.
3. Dùng public key cùng cặp và cùng parameters để `verify()`.
4. Thử đổi body và đảo thứ tự chunk: đều không còn hợp lệ.
5. Ký/xác minh thông điệp thứ hai trên cùng objects sau khi phương thức đầu đã reset state.
6. Kiểm tra `update` khi object chưa init bị từ chối.

```java
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;

public final class SignatureLifecycleDemo {
    private static final PSSParameterSpec PSS =
            new PSSParameterSpec(
                    "SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
                    32, 1);

    private static Signature createPss() throws GeneralSecurityException {
        Signature engine = Signature.getInstance("RSASSA-PSS");
        engine.setParameter(PSS);
        return engine;
    }

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();

        byte[] header = "order=42;".getBytes(StandardCharsets.UTF_8);
        byte[] body = "amount=100".getBytes(StandardCharsets.UTF_8);

        Signature signer = createPss();
        signer.initSign(pair.getPrivate());
        signer.update(header);
        signer.update(body);
        byte[] signature = signer.sign();

        Signature verifier = createPss();
        verifier.initVerify(pair.getPublic());
        verifier.update(header);
        verifier.update(body);
        System.out.println("First verification: " + verifier.verify(signature));

        verifier.update(header);
        verifier.update("amount=101".getBytes(StandardCharsets.UTF_8));
        System.out.println("Modified content accepted: " + verifier.verify(signature));

        verifier.update(body);
        verifier.update(header);
        System.out.println("Reordered chunks accepted: " + verifier.verify(signature));

        byte[] secondMessage = "second document".getBytes(StandardCharsets.UTF_8);
        signer.update(secondMessage);
        byte[] secondSignature = signer.sign();
        verifier.update(secondMessage);
        System.out.println("Second verification: " + verifier.verify(secondSignature));

        System.out.println("Signature bytes: " + signature.length);

        Signature uninitialized = createPss();
        boolean rejected = false;
        try {
            uninitialized.update(header);
        } catch (SignatureException expected) {
            rejected = true;
        }
        System.out.println("Uninitialized update rejected: " + rejected);
    }
}
```

Biên dịch và chạy với JDK 17+ có provider hỗ trợ **RSA-PSS**:

```bash
javac --release 17 SignatureLifecycleDemo.java
java SignatureLifecycleDemo
```

Kết quả kỳ vọng khi chạy chương trình (chữ ký nhị phân bên trong là ngẫu nhiên nhưng stdout sau ổn định):

```text
First verification: true
Modified content accepted: false
Reordered chunks accepted: false
Second verification: true
Signature bytes: 256
Uninitialized update rejected: true
```

**Giải thích kết quả:**

- Hai lần `signer.update(header)` và `signer.update(body)` tạo message đúng bằng hai mảng byte nối theo thứ tự đó. `verifier.update` cấp cùng dữ liệu nên dòng đầu `true`.
- `amount=101` khác `amount=100`, dù chỉ khác một byte; chữ ký cũ không khớp nên dòng thứ hai `false`.
- Đảo thứ tự hai mảng byte làm thay message thành `body || header`; chữ ký cũ không hợp lệ.
- `signer.sign()` **reset về trạng thái SIGN** và `verifier.verify()` **reset về VERIFY**, vì vậy ở thông điệp thứ hai có thể gọi `update` và kết thúc mà không init lại. **Không dùng lại cùng instance cho các request chạy đồng thời.**
- Với RSA-2048, modulus dài 256 byte, chữ ký RSA-PSS cũng dài 256 byte; đó không phải bằng chứng “độ dài message = 256”.
- `Signature` chưa init không thể nhận `update`; lỗi `SignatureException` được bắt và in `true` cho kiểm tra cuối.
- RSA-PSS dùng salt nên byte signature có thể khác giữa các lần ký cùng message; cách đánh giá đúng là gọi `verify`, **không so sánh byte giữa hai chữ ký mới**.
- Bài dùng `setParameter(PSS)` **trước init** và bộ tham số giống hệt hai phía. Các provider/HSM khác nhau có thể có yêu cầu về thời điểm set parameters hoặc khả năng PSS; cần đọc tài liệu provider đích và kiểm thử `getInstance → setParameter → initSign/initVerify → sign/verify`. Nếu không hỗ trợ, báo lỗi cấu hình; không tự hạ cấp scheme một cách âm thầm.

**Giới hạn demo:** private key chỉ sống trong RAM; chạy lại chương trình sẽ sinh key pair mới nên chữ ký cũ không tự xác minh được bằng public key mới. Môi trường thực phải có private key được bảo vệ, public key/chứng chỉ đáng tin, key ID/version, policy kiểm tra và định dạng thông điệp nhất quán.

## 7. Ký/xác minh dữ liệu lớn theo luồng với `update`

Với file nhiều megabyte, không cần `Files.readAllBytes()` chỉ để gọi `Signature.update()`. Có thể đưa byte từ stream theo chunk:

```java
// Đoạn minh họa trong phương thức có throws IOException, GeneralSecurityException;
// signer đã được initSign(privateKey), path là Path đã chọn.
try (java.io.InputStream input = java.nio.file.Files.newInputStream(path)) {
    byte[] buffer = new byte[8192];
    int count;
    while ((count = input.read(buffer)) != -1) {
        if (count > 0) {
            signer.update(buffer, 0, count);
        }
    }
}
byte[] signature = signer.sign();
```

Bên verify thực hiện cùng logic đọc **cùng byte file**, nhưng engine phải `initVerify(trustedPublicKey)` và kết thúc bằng `verify(signature)`:

```java
// verifier đã initVerify(trustedPublicKey).
try (java.io.InputStream input = java.nio.file.Files.newInputStream(path)) {
    byte[] buffer = new byte[8192];
    int count;
    while ((count = input.read(buffer)) != -1) {
        if (count > 0) {
            verifier.update(buffer, 0, count);
        }
    }
}
boolean accepted = verifier.verify(signatureBytes);
```

Hai đoạn này là **mảnh code tích hợp**, không phải chương trình standalone: `path`, các engine, key và chữ ký phải được cung cấp từ logic ứng dụng. `update(data, off, len)` phải dùng **số byte thực đọc `count`**, không dùng toàn bộ `buffer.length` cho chunk cuối, vì phần cuối buffer có thể chứa dữ liệu cũ.

**Chi phí bộ nhớ:** cung cấp dữ liệu theo chunk giúp không phải giữ toàn bộ file trong `byte[]` ở lớp ứng dụng; không được khẳng định mọi provider đều xử lý nội bộ theo streaming với mức bộ nhớ tuyệt đối cố định. Nếu đọc dữ liệu từ file có thể thay đổi đồng thời, phải chốt một snapshot/nguồn bất biến hoặc kiểm tra dữ liệu cuối trước khi công nhận kết quả để tránh TOCTOU.

Đối với `update(ByteBuffer)`, phương thức xử lý toàn bộ `remaining()` và dịch `position()` tới `limit()`. Nếu muốn dùng lại buffer cho mục đích khác, phải quản lý `flip`/`rewind`/`duplicate` đúng; không gọi `update(buffer)` hai lần với buffer đã hết `remaining()` rồi tưởng đã đưa cùng message hai lần.

## 8. Chính xác *byte nào* được ký? Encoding và phạm vi tin cậy

Một chữ ký hợp lệ chỉ bảo đảm tính toàn vẹn của **chuỗi byte thực đã nạp**. Không bảo đảm nội dung hiển thị là “giống nhau” nếu encoding/normalization khác. Những lỗi dễ gặp:

- Bên ký gọi `text.getBytes()` theo charset mặc định; bên verify dùng UTF-8: cùng ký tự có thể ra byte khác. Dùng `StandardCharsets.UTF_8` hoặc giao thức đã định.
- Bên ký tạo JSON với một thứ tự field, bên nhận parse rồi serialize lại thứ tự khác: byte đã thay đổi dù object có nội dung giống nhau. Cần canonical serialization được quy định hoặc xác minh trên **raw signed bytes**.
- Bên ký nối nhiều chuỗi không có length-prefix: `"ab" || "c"` trùng byte với `"a" || "bc"`. Nếu ý nghĩa field quan trọng, cần schema có version, tag/length, thứ tự cố định và context.
- Bên ký chỉ đưa payload vào `update()` nhưng ứng dụng xác nhận cả `amount`, `recipient`, `tenant`, `expiry` nằm ở header chưa ký: attacker có thể đổi header mà verify vẫn đúng với payload cũ. **Ký đầy đủ dữ liệu tác động tới quyết định nghiệp vụ.**
- Bên ký thực hiện normalization Unicode còn bên nhận thì không (hoặc ngược lại): khác byte. Chuẩn hóa và ký theo encoding được ghi trong protocol.
- Nếu ký nội dung chứa `algorithmId`, `keyId` hoặc type/version, cần tránh trường chưa xác thực điều khiển lựa chọn thuật toán yếu; parser phải dùng allowlist và chính sách chữ ký phù hợp.

Một cấu trúc message có thể được định nghĩa như:

```text
signed_input = domain_separator
            || format_version
            || type_length || message_type
            || payload_length || payload
            || context_length || context
```

Chỉ là sơ đồ khái niệm: phải quy định cụ thể từng trường (bao nhiêu byte, unsigned/signed, endianness, byte encoding, max length). **Domain separator** như `invoice-signature-v1` giúp ngăn chữ ký có hiệu lực ở protocol này bị dùng sai sang protocol khác nếu cùng key và cùng byte payload.

Chữ ký không tự chống **replay**. Một thông điệp đã ký hợp lệ vẫn có thể bị phát lại nhiều lần. Nếu use case cần “chỉ dùng một lần”, phải kiểm tra transaction ID, nonce, sequence, expiration, recipient và duy trì trạng thái đã sử dụng; dữ liệu này cần nằm trong phần đã ký.

## 9. Thuật toán, provider và tham số trong chu trình `Signature`

| Scheme Java | Thuộc tính đáng lưu ý | Vai trò của `Signature` |
| --- | --- | --- |
| `SHA256withRSA` | Dùng SHA-256 cùng scheme chữ ký RSA kiểu PKCS#1 v1.5; thuộc mức yêu cầu tối thiểu của `Signature` trên Java SE 17 | `initSign(RSA private)`, `update(message)`, `sign()` và `initVerify(RSA public)`, `update(message)`, `verify(sig)`. |
| `RSASSA-PSS` | RSA-PSS sử dụng salt, digest, MGF và trailer parameters; cần thống nhất cả phía ký và verify | Cùng lifecycle; đặt `PSSParameterSpec` rõ. |
| `SHA256withECDSA` | ECDSA; chữ ký thường có định dạng ASN.1 DER trong Java khi dùng tên chuẩn, tùy scheme/biến thể | Lifecycle vẫn vậy nhưng loại key là EC, không phải RSA. |
| `Ed25519` | EdDSA; không dùng PSS params; key và signature scheme khác RSA | `Signature.getInstance("Ed25519")` với private/public key Ed25519 khi provider hỗ trợ. |

Không tự đổi `SHA256withRSA` sang `RSASSA-PSS` chỉ bằng tên phía verify mà bỏ qua encoding/params; chữ ký v1.5 không phải chữ ký PSS. Phải có **algorithm/suite ID được phép**, parameters, key ID và version trong hệ thống lưu trữ/giao tiếp.

`Signature.getInstance()` chọn provider thực thi, nhưng **không phải giấy chứng nhận** rằng key, scheme, params và môi trường chạy đã an toàn. Có thể lỗi ở `initSign`, `setParameter`, `update`, `sign`, `initVerify` hoặc `verify`. Phải kiểm thử cả chu trình trên provider dự kiến, nhất là khi dùng khóa từ HSM/KMS hoặc runtime không phải JDK đã dùng phát triển.

`Signature` dùng API `java.security` để ký/xác minh; các engine khác có mục tiêu khác:

- `MessageDigest` chỉ băm dữ liệu, không có private key: không xác thực chủ thể.
- `Mac` dùng khóa bí mật đối xứng chung: các bên nắm khóa cùng tạo/kiểm tra MAC, không tương đương public-key signature.
- `Cipher` mã hóa/giải mã: bí mật không đồng nghĩa nguồn gốc được chứng thực.
- `KeyAgreement` thỏa thuận shared secret: không tự tạo một chữ ký số.

## 10. Ngoại lệ và cách xử lý an toàn

| Loại vấn đề | Khi nào xảy ra | Hướng xử lý |
| --- | --- | --- |
| `NoSuchAlgorithmException` | `Signature.getInstance("RSASSA-PSS")` khi runtime/provider không có scheme | Kiểm tra version/provider/thuật toán được yêu cầu; không tự fallback sang scheme khác không được giao thức phê duyệt. |
| `InvalidKeyException` | `initSign(private)`/`initVerify(public)` dùng khóa sai loại, sai quyền hoặc không hợp lệ | Kiểm tra key algorithm, size, key usage, key storage/HSM và policy. |
| `InvalidAlgorithmParameterException` | `setParameter(PSS)` không được provider chấp nhận | Kiểm tra digest/MGF1/salt/trailer, khả năng triển khai và cấu hình hai bên. |
| `SignatureException` ở `update` | Chưa gọi init phù hợp, object sai state hoặc provider không xử lý được | Khởi tạo đúng chiều, tạo engine mới cho operation khi cần. |
| `SignatureException` ở `sign`/`verify` | Trạng thái sai, signature encoding không phù hợp hoặc lỗi provider | Từ chối operation; bảo đảm lỗi được log an toàn và phân loại theo kiểm soát. |
| `verify(...) == false` | Chữ ký không khớp thông điệp/public key/params được cung cấp | Từ chối; không tự “sửa” message hoặc bỏ qua metadata để thử lại. |
| `verify(...) == true` nhưng sai danh tính | Public key chưa được xác thực hoặc bị attacker thay thế | Xác minh nguồn, chuỗi tin cậy, identity và key usage trước khi gán người ký. |
| `verify(...) == true` nhưng thao tác bị phát lại | Chữ ký không chống replay tự động | Có expiry/id/sequence trong dữ liệu đã ký và policy/state kiểm tra replay. |

Không nên xử lý ngoại lệ bằng cách cho phép dữ liệu đi qua. Luồng an toàn là **fail closed**: nếu bước xác minh không thành công, không thực hiện giao dịch hoặc chấp nhận dữ liệu như đã xác thực.

## 11. Kiểm thử chu trình và checklist review

Bài kiểm thử nên bao gồm:

1. **Happy path:** cùng message bytes, đúng public key, cùng scheme/params → `verify == true`.
2. **Dữ liệu bị sửa:** đổi một byte trong message đã ký → `verify == false`.
3. **Sai thứ tự hoặc thiếu chunk:** ký `A||B`, verify `B||A` hay chỉ `A` → fail.
4. **Sai public key:** dùng public key của cặp khác → không xác minh hợp lệ.
5. **Tham số scheme sai:** PSS digest/MGF1/salt khác → fail hoặc lỗi params.
6. **Không init:** `update`/`sign`/`verify` trước init → `SignatureException` thích hợp.
7. **Sau `sign`/`verify`:** thử message mới với `update` lại, không dựa vào dữ liệu message trước.
8. **Encoding và canonicalization:** ký và verify trên nhiều runtime/language bằng cùng test vectors, UTF-8, layout và key/params được đặc tả.
9. **Metadata:** sửa `tenant`, `role`, `amount`, `expiry`, `audience` trong phần đã ký → fail; sửa metadata không nằm trong phần ký là điểm cần cải thiện protocol.
10. **Replay và trust:** chữ ký hợp lệ nhưng đã hết hạn/ID đã dùng/public key chưa được phê duyệt → nghiệp vụ phải từ chối.

Checklist khi review code:

- [ ] `getInstance` dùng **tên thuật toán chữ ký rõ ràng**, không lấy tùy ý từ dữ liệu chưa được xác thực.
- [ ] `initSign` nhận **private key** đúng người ký/mục đích; `initVerify` dùng **public key đáng tin** đúng người ký.
- [ ] Mọi byte quan trọng đều được `update` đúng thứ tự, đúng charset/schema; khi streaming, `len` là số byte thực đọc.
- [ ] `sign()` và `verify()` chỉ chạy sau init/update hợp lệ; biết engine reset sau operation, không chia sẻ giữa các thread.
- [ ] PSS và các params khác được chỉ định/tương thích; key length và provider đáp ứng chính sách.
- [ ] Kết quả `verify` **phải được kiểm tra**; `false` và mọi exception liên quan đều dẫn tới từ chối.
- [ ] Chứng chỉ, định danh và key usage được xác thực **ngoài** chữ ký; thông điệp ký không được mặc định là confidential hay chống replay.
- [ ] Có test signature altered, content altered, wrong key, stale/replayed message, wrong params và cross-provider khi cần.

## 12. Tự kiểm tra kiến thức

**Câu 1:** `Signature.getInstance("SHA256withRSA")` rồi `update` được ngay không?  
**Không.** Phải gọi `initSign(privateKey)` hoặc `initVerify(publicKey)` trước.

**Câu 2:** Bên ký `update(A)` và `update(B)`, bên verify `update(A||B)` có được không?  
**Có**, nếu byte nối chính xác cùng thứ tự và cùng scheme/params. Kích thước chunk không làm thay đổi message.

**Câu 3:** `Signature.sign()` trả ciphertext hay signature?  
**Signature bytes.** Message không được mã hóa bởi `Signature`.

**Câu 4:** Vì sao thay `amount=100` thành `amount=101` làm `verify` thất bại?  
Vì chữ ký đã ràng buộc với **byte message đã nạp**. Nội dung mới không còn khớp chữ ký cũ.

**Câu 5:** Sau `verify()` có được nạp message khác không?  
**Có** theo contract: đối tượng reset về initialized VERIFY state với key cũ; phải `update` lại message mới. Trong code concurrency nên dùng engine riêng cho mỗi operation.

**Câu 6:** `verify() == true` đã đủ khẳng định “Alice ký” chưa?  
**Chưa.** Cần xác thực public key thật sự thuộc Alice và được phép dùng ký; cần thêm kiểm tra expiry, replay và context theo ứng dụng.

**Câu 7:** RSA-PSS ký lại cùng message bằng cùng private key có bắt buộc ra đúng cùng byte chữ ký không?  
**Không.** PSS dùng randomness (salt); kiểm chứng bằng `Signature.verify`, không dựa vào byte equality.

## Tài liệu gốc và đọc thêm

- [Java SE 17 — `java.security.Signature`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Signature.html): tài liệu chính cho **ba giai đoạn**, state `UNINITIALIZED`/`SIGN`/`VERIFY`, `update`, `sign`, `verify` và reset sau khi hoàn thành.
- [Java SE 17 — `PSSParameterSpec`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/spec/PSSParameterSpec.html): SHA-256, MGF1, salt length, trailer.
- [Java SE 17 — `MGF1ParameterSpec`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/spec/MGF1ParameterSpec.html): digest sử dụng bên trong MGF1.
- [Java SE 17 — Java Cryptography Architecture Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-cryptography-architecture-jca-reference-guide.html): Signature engine, provider, service architecture và ví dụ sử dụng.
- [Java SE 17 — Java Security Standard Algorithm Names](https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html): tên chuẩn và nhóm thuật toán `Signature` bắt buộc/khả dụng.
- [RFC 8017 — PKCS #1 v2.2](https://www.rfc-editor.org/rfc/rfc8017): RSASSA-PSS, encoding, salt và mô hình xác minh.

**Ghi nhớ:** `initSign(private) → update(message bytes) → sign()` tạo chữ ký; `initVerify(trustedPublic) → update(same message bytes) → verify(signature)` kiểm tra. Hai phía phải thống nhất **byte, thuật toán, tham số và danh tính khóa**. `Signature` quản lý tính toàn vẹn và chứng thực theo khóa, không cung cấp bảo mật bí mật hay chống replay một cách tự động.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="digital-signature-003">9.3 Phân biệt ký bằng khóa riêng với mã hóa bằng khóa công khai</a>

<details>
<summary>Xem chi tiết</summary>

# 9.3. Phân biệt ký bằng khóa riêng với mã hóa bằng khóa công khai

**Chữ ký số** và **mã hóa khóa công khai** đều thuộc mật mã bất đối xứng, nhưng giải quyết **hai vấn đề khác nhau**. Chữ ký số dùng **khóa riêng của người ký** để tạo chữ ký, rồi dùng **khóa công khai của chính người ký** để kiểm tra nguồn gốc và tính toàn vẹn. Mã hóa khóa công khai dùng **khóa công khai của người nhận** để mã hóa, rồi dùng **khóa riêng của người nhận** để giải mã nhằm giữ bí mật nội dung.

Trong Java 21, hai tác vụ này cũng có **API, thuật toán và yêu cầu tham số riêng**: `java.security.Signature` dành cho ký/xác minh, còn `javax.crypto.Cipher` có thể thực hiện mã hóa/giải mã RSA-OAEP. Bài học tập trung vào cách tư duy đúng, tránh nhầm “ký là mã hóa bằng private key”, và cách áp dụng an toàn trong giao thức thật.

## Mục tiêu học tập

Sau bài này, bạn có thể:

1. Trả lời dứt khoát: **ai dùng khóa nào, để làm gì, và ai xác minh/giải mã được**.
2. Giải thích vì sao chữ ký số bảo vệ **nguồn gốc và toàn vẹn** nhưng không che giấu thông điệp; và vì sao mã hóa bằng public key của người nhận **không tự chứng minh danh tính người gửi**.
3. Phân biệt `Signature`, `Cipher`, `KeyPairGenerator`, `PublicKey` và `PrivateKey` trong Java 21; nhận ra Ed25519, RSA-PSS và RSA-OAEP là **những scheme khác nhau**.
4. Biên dịch/chạy các chương trình Java 21 minh họa: ký Ed25519 → xác minh, sửa message → signature fail; mã hóa RSA-OAEP → giải mã, người thứ ba vẫn có thể mã hóa bằng public key; ký RSA-PSS với tham số tường minh.
5. Chọn phương án khi ứng dụng cần **bí mật**, **chống sửa đổi**, **chứng minh người ký** hoặc **đồng thời cả hai**.
6. Tránh sai lầm trong quản lý khóa, chứng thư/public-key trust, padding, dữ liệu được ký, kích thước message, chống phát lại và mã hóa lai.

## 1. Bốn khóa cần phân biệt trong tình huống Alice gửi cho Bob

Giả sử Alice gửi tài liệu cho Bob:

- **Alice** có cặp khóa ký `(sk_A, pk_A)`: `sk_A` là private key chỉ Alice được quyền sử dụng; `pk_A` là public key tương ứng mà Bob phải xác thực nguồn.
- **Bob** có cặp khóa mã hóa `(sk_B, pk_B)`: `sk_B` là private key chỉ Bob giữ; `pk_B` là public key mà Alice cần xác thực là của Bob.

Chữ ký:

```text
Alice: signature = Sign(sk_A, message)
Alice → Bob: message, signature
Bob: Verify(pk_A, message, signature) → true / false
```

Mã hóa khóa công khai:

```text
Alice: ciphertext = Encrypt(pk_B, message)
Alice → Bob: ciphertext
Bob: message = Decrypt(sk_B, ciphertext)
```

**Hai mũi tên không đối xứng về mục đích:** public key để **xác minh chữ ký** phải thuộc *người ký*; public key để **mã hóa bảo mật** phải thuộc *người nhận*. Cả hai đều mang tên “public key”, nhưng không phải cùng một khóa, cùng thuật toán, hoặc cùng quyền sử dụng.

| Câu hỏi | Chữ ký số | Mã hóa khóa công khai |
| --- | --- | --- |
| Mục tiêu chính | Xác minh thông điệp do bên nắm signing private key tạo ra và chưa bị sửa | Giữ kín nội dung với người không có decryption private key phù hợp |
| Bên tạo | **Người ký**, dùng **private key của người ký** | **Người gửi**, dùng **public key của người nhận** |
| Bên kiểm tra/khôi phục | Người xác minh, dùng **public key của người ký** | **Người nhận**, dùng **private key của người nhận** |
| Dữ liệu truyền | Message (có thể đọc được) + chữ ký | Ciphertext (và metadata/khóa bọc cần thiết nếu mã hóa lai) |
| Kết quả API | `Signature.sign()` → bytes chữ ký; `verify()` → boolean | `Cipher.doFinal()` → ciphertext hoặc plaintext |
| Người khác có public key | Có thể kiểm tra chữ ký, **không thể tạo chữ ký hợp lệ nếu thiếu private key** | Có thể **tự mã hóa** dữ liệu gửi cho người nhận, nhưng không giải mã ciphertext đã nhận nếu thiếu private key |
| Chỉ riêng cơ chế đó có giữ bí mật? | **Không** | Có, nếu dùng scheme/cấu hình an toàn và quản lý khóa đúng |
| Chỉ riêng cơ chế đó có chứng minh người gửi? | Có điều kiện: cần xác minh public key thực sự thuộc người ký | **Không**; bất kỳ ai biết public key đều mã hóa được |
| Tự chống replay không? | **Không** | **Không** |

> Câu nhớ nhanh: **Ký = private key người ký → public key người ký kiểm tra. Mã hóa = public key người nhận → private key người nhận giải mã.**

## 2. Vì sao chữ ký số không phải là “mã hóa bằng private key”?

Bạn có thể gặp lời giải thích cổ điển: *“RSA ký bằng cách mã hóa hash bằng khóa riêng, người nhận giải mã hash bằng khóa công khai.”* Đây là phép ví von dễ nhớ về RSA toán học thô, nhưng **không phải định nghĩa đúng và không phải cách lập trình mật mã an toàn**.

### 2.1 Hai primitive/scheme có thiết kế khác nhau

- **RSA encryption** sử dụng scheme mã hóa như **RSAES-OAEP**. Mục tiêu là tính bí mật của bản rõ; gồm quy tắc đệm, hàm hash/MGF và xử lý lỗi riêng.
- **RSA signature** sử dụng scheme ký như **RSASSA-PSS** hoặc, để tương thích chuẩn/hệ thống hiện hữu, **RSASSA-PKCS1-v1_5**. Mục tiêu là tạo cấu trúc chữ ký có thể xác minh công khai và chống giả mạo.
- Ngay cả khi hai scheme cùng sử dụng nền tảng toán học RSA, **OAEP khác PSS**; không thể chuyển `Cipher` sang `ENCRYPT_MODE` bằng private key rồi coi kết quả là chữ ký RSA-PSS hợp lệ.
- **Ed25519/ECDSA** là thuật toán **chữ ký**; không có thao tác “mã hóa bằng public key Ed25519” tương đương. **X25519/ECDH** là cơ chế trao đổi/thỏa thuận khóa, không phải chữ ký hay mã hóa thông điệp độc lập.

**Do đó:** sử dụng `Signature` để ký/xác minh; sử dụng scheme mã hóa được thiết kế cho confidentiality. Không dùng raw RSA (`NoPadding`), “private-key encryption” hoặc tự xây padding/khối toán học RSA để tạo chữ ký.

### 2.2 “Có thể xác minh” khác “có thể đọc nội dung”

Một chữ ký số được gửi cùng message; public key chỉ giúp người nhận xác định chữ ký có hợp lệ đối với **đúng bytes message** hay không. Chữ ký không làm message trở nên bí mật: kẻ nghe lén vẫn thấy message nếu message được gửi bản rõ.

Ngược lại, RSA-OAEP ciphertext được tạo bằng public key của Bob, nhưng vì public key đó công khai, **cả Alice, Carol và một kẻ tấn công đều có thể mã hóa một thông điệp để gửi Bob**. Bob giải mã được không có nghĩa là thông điệp đến từ Alice.

### 2.3 Ví dụ tài liệu cần ký nhưng không cần mã hóa

Một nhà phát hành công khai file cài đặt và chữ ký đi kèm. Ai cũng được phép tải file và có thể đọc nội dung, nhưng chỉ chấp nhận nếu chữ ký đúng và public key được xác nhận là của nhà phát hành. Ở đây cần **authenticity/integrity**, không yêu cầu confidentiality của file công khai.

Ngược lại, thông điệp chứa thông tin cá nhân chỉ dành cho Bob cần **confidentiality**, và nếu Bob phải xác minh thực sự Alice gửi, nên thêm chữ ký hoặc cơ chế xác thực người gửi phù hợp.

## 3. Điều kiện tin cậy của public key

Giả sử attacker thay `pk_A` bằng public key của chính mình và tự ký một message giả. Nếu Bob tin nhầm public key đó là của Alice, lệnh `verify()` vẫn trả `true` — **chữ ký đúng về toán học nhưng danh tính bị giả**.

Do đó một hệ thống phải có cách **ràng buộc public key với danh tính/chủ thể đáng tin**:

- Chứng thư X.509 và kiểm tra chuỗi tin cậy, tên đích/danh tính, hiệu lực, key usage và chính sách thu hồi phù hợp.
- Một public key được phân phối qua kênh đã xác thực, hoặc key fingerprint được kiểm tra qua kênh độc lập.
- Registry khóa tin cậy theo `keyId`, tenant, principal, issuer, thuật toán và phiên bản, có vòng đời rotate/revoke.
- Với mã hóa, Alice cũng phải xác minh `pk_B` thật sự của Bob. Nếu attacker tráo thành public key của attacker, Alice sẽ mã hóa **cho attacker**, làm mất bí mật trước khi Bob nhận được.

**Private key** cần giữ bí mật; lưu trong keystore, KMS hoặc HSM theo yêu cầu, hạn chế quyền sign/decrypt riêng, kiểm toán sử dụng. Không đặt private key trong Git, source code, log hoặc trả ra API. Chỉ có public key mà không xác minh nguồn **không đủ** để thiết lập tin cậy.

**Chống chối bỏ** thường được nhắc như một lợi ích của chữ ký số, nhưng là thuộc tính có điều kiện: bảo vệ private key, xác định người sở hữu và chính sách/biên bản nghiệp vụ mới giúp quy kết trách nhiệm. Một byte signature hợp lệ đơn lẻ không tự tạo thành bằng chứng pháp lý tuyệt đối.

## 4. API nào của Java 21 dùng cho từng việc?

| API / tên thuật toán | Vai trò đúng | Đầu vào khóa |
| --- | --- | --- |
| `KeyPairGenerator.getInstance("Ed25519")` | Sinh key pair phục vụ chữ ký Ed25519 | Không có key sẵn; sinh cặp mới |
| `Signature.getInstance("Ed25519")` | Ký và xác minh Ed25519 | `initSign(PrivateKey)` / `initVerify(PublicKey)` |
| `KeyPairGenerator.getInstance("RSA")` | Sinh cặp khóa RSA | Kích thước phù hợp như 3072 bit trong ví dụ |
| `Signature.getInstance("RSASSA-PSS")` | Ký và xác minh RSA-PSS | Private key người ký / public key người ký, cùng tham số PSS |
| `Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")` | RSA-OAEP mã hóa một payload nhỏ / khóa phiên | `ENCRYPT_MODE` với public key người nhận; `DECRYPT_MODE` với private key người nhận |
| `OAEPParameterSpec`, `MGF1ParameterSpec` | Chỉ định hash, MGF và parameter cho OAEP | Cùng cấu hình hai đầu |
| `PSSParameterSpec` | Chỉ định digest, MGF1, độ dài salt và trailer cho PSS | Cùng cấu hình hai đầu |

Các thuật toán này được hỗ trợ bởi các provider của Oracle/OpenJDK 21 thông dụng như **SunEC**, **SunRsaSign**, **SunJCE**. Provider khác, HSM hoặc môi trường ràng buộc FIPS có thể có tập thuật toán/chính sách khác; luôn kiểm tra trên runtime mục tiêu. “Có API `Signature`” không có nghĩa provider hỗ trợ bất cứ scheme nào bạn gõ vào.

**Đọc transformation RSA đúng:** chữ “`ECB`” trong tên transformation RSA `RSA/ECB/OAEPWithSHA-256AndMGF1Padding` chủ yếu là thành phần cú pháp theo cách đặt tên của một số provider; **đừng nhầm với AES-ECB**, một mode mã hóa khối không nên dùng cho dữ liệu nhạy cảm. Với RSA, cần đặc biệt chú ý **OAEP padding** và tham số MGF1.

## 5. Thực hành Java 21: Ed25519 ký, RSA-OAEP mã hóa

Đoạn demo tạo **hai cặp khóa riêng biệt**:

- `senderKeys` dùng cho **chữ ký Ed25519** của Alice. Cặp khóa Ed25519 không dùng để RSA-encrypt.
- `recipientKeys` dùng cho **mã hóa RSA-OAEP** gửi Bob. Ví dụ chỉ mã hóa một message ngắn, không phải file lớn.

Đây là chương trình độc lập, không yêu cầu thư viện ngoài JDK chuẩn 21. Tạo file **`SignVsEncryptDemo.java`**:

```java
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

public class SignVsEncryptDemo {
    // Explicit OAEP: hash SHA-256 va MGF1-SHA256 o ca hai dau.
    private static final OAEPParameterSpec OAEP_SHA256 =
            new OAEPParameterSpec("SHA-256", "MGF1",
                    MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    public static void main(String[] args) throws Exception {
        byte[] message = "Transfer 100".getBytes(StandardCharsets.UTF_8);
        byte[] altered = "Transfer 900".getBytes(StandardCharsets.UTF_8);

        // 1) Ed25519: nguoi ky dung private key.
        KeyPair senderKeys = KeyPairGenerator.getInstance("Ed25519")
                .generateKeyPair();
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(senderKeys.getPrivate());
        signer.update(message);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(senderKeys.getPublic());
        verifier.update(message);
        System.out.println("signature valid: " + verifier.verify(signature));

        verifier.initVerify(senderKeys.getPublic());
        verifier.update(altered);
        System.out.println("altered message valid: " +
                verifier.verify(signature));

        // 2) RSA-OAEP: nguoi gui dung public key cua nguoi nhan.
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        KeyPair recipientKeys = generator.generateKeyPair();

        Cipher encryptor =
                Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        encryptor.init(Cipher.ENCRYPT_MODE,
                recipientKeys.getPublic(), OAEP_SHA256);
        byte[] ciphertext = encryptor.doFinal(message);

        Cipher decryptor =
                Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        decryptor.init(Cipher.DECRYPT_MODE,
                recipientKeys.getPrivate(), OAEP_SHA256);
        byte[] recovered = decryptor.doFinal(ciphertext);
        System.out.println("decryption restored original: " +
                Arrays.equals(message, recovered));

        // Bat ky ai co public key cua Bob deu co the ma hoa gui Bob.
        encryptor.init(Cipher.ENCRYPT_MODE,
                recipientKeys.getPublic(), OAEP_SHA256);
        byte[] outsiderCiphertext = encryptor.doFinal(altered);
        decryptor.init(Cipher.DECRYPT_MODE,
                recipientKeys.getPrivate(), OAEP_SHA256);
        System.out.println("outsider can encrypt: " +
                Arrays.equals(altered, decryptor.doFinal(outsiderCiphertext)));
    }
}
```

Biên dịch/chạy trên JDK 21, hoặc compiler hỗ trợ `--release 21` với runtime đủ mới:

```text
java -version
javac -version
mkdir out
javac --release 21 -encoding UTF-8 -d out SignVsEncryptDemo.java
java -cp out SignVsEncryptDemo
```

Stdout:

```text
signature valid: true
altered message valid: false
decryption restored original: true
outsider can encrypt: true
```

Giải thích từng dòng:

1. **`signature valid: true`**: `signer.initSign(senderKeys.getPrivate())` tạo chữ ký cho đúng bytes `message`; `verifier.initVerify(senderKeys.getPublic())` dùng public key **của người ký** để xác minh.
2. **`altered message valid: false`**: thay “100” bằng “900” nhưng giữ chữ ký ban đầu làm xác minh thất bại. Đây là minh họa **tính toàn vẹn và xác thực bằng chữ ký**, không phải phát hiện nhờ mã hóa.
3. **`decryption restored original: true`**: Bob dùng **private key của Bob** và đúng `OAEPParameterSpec` để giải mã ciphertext được tạo bằng public key của Bob.
4. **`outsider can encrypt: true`**: bất kỳ bên thứ ba nào có public key của Bob đều có thể mã hóa message khác cho Bob. Vì vậy việc Bob giải mã thành công **không chứng minh người gửi là Alice**.

Chương trình sinh khóa và ciphertext ngẫu nhiên trong mỗi lần chạy, nhưng không in các giá trị ngẫu nhiên nên các dòng output luôn có thể dự đoán khi provider hỗ trợ thuật toán. Public key được lấy từ cùng một cặp khóa trong demo, nên chương trình **chưa kiểm thử** vấn đề phân phối hoặc xác thực public key trên mạng.

### 5.1 Lưu ý quan trọng về các tham số OAEP

Code truyền **tường minh** `OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)`. Đây là cách làm rõ **hash OAEP** và **hash dùng trong MGF1** đều là SHA-256; không mặc định rằng chuỗi tên `OAEPWithSHA-256AndMGF1Padding` buộc mọi provider tự chọn cùng một MGF digest nếu không được cấu hình. Hai đầu mã hóa và giải mã phải dùng cấu hình tương thích.

Đừng dùng `RSA/ECB/NoPadding`, “RSA thô”, hoặc tùy tiện thay OAEP bằng padding lỗi thời. Nếu ciphertext, khóa hoặc tham số sai, giải mã có thể ném `BadPaddingException` hay exception liên quan; xử lý lỗi nội bộ đồng nhất, tránh tạo **decryption oracle** qua response, thời gian hoặc log.

Với RSA-OAEP, **kích thước plaintext giới hạn bởi modulus và hash**. Theo PKCS #1, giới hạn tối đa là:

```text
messageLength <= k - 2*hLen - 2
k    = do dai RSA modulus tinh bang byte
hLen = so byte cua OAEP hash (SHA-256 = 32)
```

RSA 3072 bit có `k = 384` byte; với SHA-256, tối đa **384 - 64 - 2 = 318 byte**. Đây là giới hạn của scheme, không phải “độ dài file có thể mã hóa”. Để mã hóa file hoặc message dài, dùng **mã hóa lai** (xem phần 8).

## 6. Cùng họ RSA nhưng scheme ký riêng: RSA-PSS

Nếu ứng dụng dùng **RSA cho chữ ký** thay vì Ed25519, hãy chọn `Signature.getInstance("RSASSA-PSS")` và chỉ định `PSSParameterSpec`. Việc cả RSA-PSS lẫn RSA-OAEP cùng có tên RSA **không biến hai thao tác thành một**.

Tạo file **`RsaPssSignatureDemo.java`**:

```java
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;

public class RsaPssSignatureDemo {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        KeyPair pair = generator.generateKeyPair();
        byte[] message = "Order 123".getBytes(StandardCharsets.UTF_8);

        // Digest SHA-256, MGF1 SHA-256, salt 32 byte, trailerField=1.
        PSSParameterSpec params = new PSSParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1);

        Signature signer = Signature.getInstance("RSASSA-PSS");
        signer.setParameter(params);
        signer.initSign(pair.getPrivate());
        signer.update(message);
        byte[] tag = signer.sign();

        Signature verifier = Signature.getInstance("RSASSA-PSS");
        verifier.setParameter(params);
        verifier.initVerify(pair.getPublic());
        verifier.update(message);
        System.out.println("RSA-PSS valid: " + verifier.verify(tag));
    }
}
```

```text
javac --release 21 -encoding UTF-8 RsaPssSignatureDemo.java
java RsaPssSignatureDemo
```

Stdout:

```text
RSA-PSS valid: true
```

Trong ví dụ, `Signature` tự xử lý băm và encoding theo scheme RSA-PSS, sau đó xác minh bằng public key. Bạn **không** viết `Cipher.init(ENCRYPT_MODE, privateKey)` để thay thế. Đối với RSA-PSS, cả hai đầu phải tương thích ở **digest, MGF, salt length, trailer field** và scheme đã chọn. Nếu muốn chữ ký tương thích hệ thống khác, phải quy định parameter và định dạng chữ ký rõ ràng.

*Ghi nhớ:* không nhất thiết một cặp RSA dùng cho cả ký và mã hóa. Thực hành tốt là **tách khóa theo mục đích**, giới hạn quyền private key, quản lý rotation và chọn scheme phù hợp với chính sách. Chứng thư X.509 còn có thuộc tính `keyUsage` hoặc `extendedKeyUsage` để kiểm soát cách sử dụng khóa trong bối cảnh liên quan.

## 7. Khi nào cần ký, khi nào cần mã hóa?

| Tình huống | Cần gì? | Lý do |
| --- | --- | --- |
| Nhà phát hành công khai bản cập nhật và chữ ký | **Chữ ký số** | File được phép công khai, người tải cần biết file chưa bị sửa và đúng nhà phát hành |
| Gửi bí mật cho một người nhận đã có public key đáng tin | **Mã hóa khóa công khai / hybrid encryption** | Người khác không đọc được nội dung khi thiếu private key của người nhận |
| Lệnh giao dịch vừa nhạy cảm vừa cần chứng minh người ký | **Ký + mã hóa hoặc kênh bảo vệ được xác thực** | Cần cả nguồn gốc/toàn vẹn lẫn confidentiality |
| Chứng minh ai chấp thuận một văn bản dùng để kiểm toán | **Chữ ký số + ràng buộc danh tính, thời gian, hồ sơ phê duyệt** | Mã hóa chỉ che giấu, không chứng minh người ký |
| Giao tiếp HTTPS API có TLS và xác thực client | **TLS + cơ chế xác thực ứng dụng đúng yêu cầu** | TLS có chức năng bảo vệ kênh; chữ ký message-level là quyết định bổ sung tùy threat model |
| Mã hóa một tập tin lớn | **AES-GCM với khóa phiên + cơ chế bọc/thỏa thuận khóa** | RSA-OAEP không mã hóa trực tiếp file lớn |
| Chữ ký hợp lệ bị phát lại gây thao tác trùng | **Ràng buộc nonce, timestamp, request ID và replay state** | Ký đúng không có nghĩa là yêu cầu mới |

**Không có cơ chế duy nhất tự đáp ứng mọi mục tiêu.** Lựa chọn phải xuất phát từ thuộc tính bảo mật và threat model: ai có thể đọc, ai có thể gửi, ai được phép xác minh và ai được phép mở dữ liệu.

### 7.1 Chữ ký và MAC/HMAC có giống nhau không?

Cả chữ ký số và MAC có thể kiểm tra integrity/authenticity theo giả định về khóa, nhưng:

- **MAC/HMAC** dùng **khóa bí mật đối xứng**: các bên cùng biết khóa đều có thể tạo tag. Không có kiểm chứng công khai tương tự chữ ký số; phân định trách nhiệm giữa hai bên giữ cùng khóa rất khác.
- **Chữ ký số** dùng **private key để ký**, **public key để xác minh**. Người xác minh thường không có khả năng tạo chữ ký hợp lệ; nó hữu ích khi nhiều người cần xác minh nhưng chỉ một chủ thể được quyền ký.
- **Mã hóa public key** che giấu message với người không có decryption private key; bản thân nó không làm được công việc của chữ ký hoặc HMAC.

## 8. Nếu vừa cần bí mật vừa cần xác thực người gửi: thiết kế envelope

Giả sử Alice phải gửi cho Bob một đơn hàng bí mật và Bob cần kiểm tra chính Alice đã phát hành nó. Thiết kế thường gồm:

1. Alice tạo payload có **phiên bản giao thức, senderId, recipientId, requestId, thời gian, nội dung**; thống nhất quy tắc serialize/canonicalize.
2. Alice dùng **signing private key của Alice** ký các bytes đã thống nhất. Chữ ký phải bao phủ dữ liệu ảnh hưởng đến ý nghĩa giao dịch và ngữ cảnh.
3. Tạo một **session key ngẫu nhiên** cho **AES-GCM**, mã hóa payload và signature bằng AES-GCM với nonce/IV riêng, AAD phù hợp. Đây là bước bảo vệ bí mật và tính toàn vẹn của ciphertext.
4. Dùng **public key mã hóa của Bob** và RSA-OAEP để **bọc session key** (không bọc cả file lớn), hoặc dùng cơ chế KEM/HPKE được triển khai theo chuẩn. Gửi envelope gồm metadata, wrapped key, nonce, ciphertext và GCM tag.
5. Bob dùng **decryption private key của Bob** mở session key, giải mã/kiểm tra AEAD tag, lấy message và signature.
6. Bob lấy **signing public key đáng tin của Alice**, xác minh signature rồi mới xử lý nghiệp vụ. Nếu giao dịch không được phép lặp, thêm **anti-replay state/idempotency**.

```text
Alice                                 Bob
------                                ------
Sign(sk_A, payload)                   trust(pk_A)
  |                                      ^
  v                                      |
payload + signature                  Verify(pk_A, payload, signature)
  |                                      ^
Encrypt_AES_GCM(random sessionKey)       |
  |                                      |
Wrap_RSA_OAEP(pk_B, sessionKey)          |
  |                                      |
  +------------ envelope ------------> Unwrap(sk_B), AES-GCM decrypt
```

Đây là **sơ đồ kiến trúc**, không phải đặc tả protocol hoặc mã độc lập để tự triển khai. Thực tế cần xác định chính xác AAD, version, key ID, canonical bytes, quy tắc nonce/IV, các phản hồi lỗi và biện pháp chống replay. Đặc biệt **AES-GCM IV/nonce không được tái sử dụng với cùng khóa**. Khi dùng thư viện/giao thức chuẩn có sẵn, thường an toàn hơn tự sáng tạo một envelope mới.

Các lựa chọn như **X25519** cung cấp *key agreement*, không phải phép “mã hóa bằng public key” trực tiếp; khóa chia sẻ sau đó cần KDF và AEAD. **HPKE** là một thiết kế chuẩn tích hợp các bước KEM/KDF/AEAD. Việc chọn RSA-OAEP, HPKE hay TLS còn tùy giao thức, khả năng tương thích và chính sách hệ thống.

### 8.1 Ký trước hay sau khi mã hóa?

Ký plaintext rồi mã hóa cả message + signature, hoặc ký một envelope chứa ciphertext và metadata, đều có những ràng buộc về danh tính, thông tin lộ ra, phạm vi người xác minh và khả năng chuyển tiếp. Không có một quy tắc “ký trước/mã hóa sau” đúng cho mọi giao thức.

Khi đánh giá, hỏi rõ: **ai cần kiểm tra chữ ký và ở bước nào**, metadata nào phải được ký/AAD-protect, liệu người nhận có thể chuyển signature sang ngữ cảnh khác, liệu người ký có đang bị biến thành signing oracle, và liệu chữ ký được kiểm tra **trước khi có tác động nghiệp vụ**. Nếu dùng chuẩn có sẵn như CMS/JWS/JWE, hãy tuân thủ đúng profile và quy tắc xử lý của chuẩn thay vì tự ghép một thứ tự tùy ý.

## 9. Tính đúng đắn phụ thuộc vào bytes và ngữ cảnh được ký

Chữ ký xác minh **chuỗi byte**, không “ý định con người”. Ví dụ JSON:

```json
{"amount":100,"currency":"VND"}
```

và

```json
{ "currency": "VND", "amount": 100 }
```

Có thể biểu diễn cùng dữ liệu logic nhưng **khác bytes UTF-8**; nếu hệ thống ký bytes của chuỗi JSON, signature của phiên bản đầu **không** tự áp dụng cho phiên bản sau. Nếu có canonicalization, cả bên ký và bên xác minh phải thống nhất một đặc tả rõ ràng.

Khi ký một request, nên xác định phạm vi bao gồm:

- Version và loại message để ngăn nhầm protocol/thuật toán (*domain separation*).
- Principal/sender, intended recipient/audience, tenant/keyId/issuer nếu liên quan.
- HTTP method, URL/path/query/headers có ý nghĩa hoặc định danh nghiệp vụ.
- Body bytes hoặc canonical representation đúng thỏa thuận.
- Thời gian và nonce/requestId/sequence nếu giao thức cần hạn sử dụng và chống phát lại.

**Chữ ký đúng có thể bị replay nguyên gói.** Attacker không cần giả chữ ký: chỉ cần thu lại message + signature rồi gửi lần nữa. Chống replay là trách nhiệm riêng của giao thức, ví dụ lưu nonce/requestId đã xử lý trong phạm vi principal/khóa, thực hiện check-and-claim atomic và kiểm tra thời gian được ký. **Mã hóa** cũng không tự loại bỏ ciphertext cũ khi người nhận giải mã lần thứ hai.

## 10. Phân biệt khóa, algorithm, format và chứng thư trong thực tế

### 10.1 Khóa và thuật toán phải phù hợp mục đích

Một đối tượng `PrivateKey` có thể đại diện Ed25519, RSA hoặc EC tùy `getAlgorithm()` và provider; nó không có nghĩa mọi `Signature`/`Cipher` chấp nhận khóa đó.

- **Ed25519**: chủ yếu để ký/xác minh; không dùng public key Ed25519 để RSA-OAEP encrypt.
- **RSA**: có các scheme ký (RSA-PSS, RSA PKCS#1 v1.5 signature) và scheme mã hóa (RSA-OAEP). Cần định nghĩa rõ kiểu khóa/cặp khóa dành riêng cho vai trò.
- **X25519/ECDH**: thỏa thuận bí mật chung; không ký và không xuất trực tiếp bản mã.
- **AES-GCM**: mã hóa đối xứng có xác thực, dùng `SecretKey` và IV/nonce; không dùng `PublicKey`/`PrivateKey`.

Trong một ứng dụng có nhiều key, map `keyId` → thuật toán → mục đích → danh tính → public-key trust. Không cho phép request tự tuyên bố thuật toán rồi hệ thống tin theo vô điều kiện. Phải pin/allowlist những scheme và parameter được phép cho đúng loại message.

### 10.2 Key encoding không phải encryption

Public key có thể xuất dưới dạng **X.509 SubjectPublicKeyInfo** (`getEncoded()` với `X.509`), còn private key có thể ở dạng **PKCS#8** (`getEncoded()` với `PKCS#8`) tùy provider và khả năng xuất khóa. Đây là **định dạng serialize**, không tự động mã hóa hoặc kiểm tra người sở hữu; file PKCS#8 chứa private key không được mã hóa có thể làm lộ bí mật. Khi xuất private key bắt buộc bảo vệ bằng cơ chế phù hợp, tốt hơn dùng key store/HSM/KMS nếu có.

**Chứng thư số X.509** chứa public key cùng thông tin nhận dạng/chữ ký CA; không phải bản thân public key là chứng thư hợp lệ. Cần validate chain, trust anchors, key usage và bối cảnh (ví dụ hostname hoặc signer identity). Có thể gặp tình huống public key khớp về toán học nhưng certificate đã hết hạn, bị thu hồi hoặc không đúng mục đích sử dụng.

### 10.3 Tách khóa và quyền

Một hệ thống có thể dùng key pair riêng cho **ký yêu cầu** và **giải mã tài liệu**. Điều này cho phép:

- Thu hồi/rotate signing key mà không ảnh hưởng tài liệu đã mã hóa bằng key giải mã.
- Giới hạn quyền KMS/HSM: dịch vụ ký không có quyền decrypt dữ liệu khách hàng, dịch vụ decrypt không có quyền ký thay tổ chức.
- Áp chính sách thời hạn, sử dụng key, kiểm toán và backup/khôi phục cho từng mục tiêu.
- Tránh thiết kế phụ thuộc một key pair đa mục đích mà policy provider hoặc certificate không hỗ trợ.

## 11. Sai lầm thường gặp và cách sửa

| Nhầm lẫn | Vì sao sai | Cách nghĩ/cách làm đúng |
| --- | --- | --- |
| “Ký bằng khóa riêng = mã hóa dữ liệu bằng private key” | Scheme ký và scheme mã hóa có encoding, kiểm tra, padding, mục tiêu khác nhau | `Signature.initSign`/`initVerify` cho signature; RSA-OAEP/AEAD cho encryption |
| “Có public key là xác minh được Alice” | Attacker có thể tráo public key | Xác minh key ownership qua cert/registry/fingerprint đáng tin |
| “Bob giải mã được nên Alice chắc đã gửi” | Ai có `pk_B` đều mã hóa được | Xác thực người gửi riêng bằng chữ ký hoặc cơ chế tương đương |
| “Chữ ký làm người khác không đọc nội dung” | Message thường gửi bản rõ kèm chữ ký | Thêm encryption khi cần confidentiality |
| “RSA-OAEP mã hóa trực tiếp file lớn” | RSA có giới hạn kích thước plaintext và tốn chi phí | Hybrid encryption với AES-GCM + key encapsulation/wrapping |
| “RSA-PSS cũng chỉ là đổi mode của RSA Cipher” | Ký và mã hóa là scheme khác nhau | Dùng `Signature` và `Cipher` đúng API và parameters |
| “Ed25519 public key có thể dùng để encrypt” | Ed25519 là signature algorithm | Chọn cơ chế encryption/KEM/HPKE thích hợp, key riêng |
| “Bất kỳ chữ ký hợp lệ đều là request mới” | Replay nguyên message giữ nguyên chữ ký | Signed nonce/timestamp/requestId + kiểm tra trạng thái |
| “Cùng text JSON thì luôn cùng signature” | Serialization/bytes có thể khác | Ký đúng bytes hoặc quy định canonical form |
| “Chỉ cần dùng `SHA256withRSA` hay `RSASSA-PSS` không cần xem policy” | Scheme, parameters, provider, certificate key usage phải thống nhất | Chọn chuẩn, tường minh params và kiểm tra end-to-end |
| “Dùng RSA/NoPadding để hiểu cơ chế rồi đưa production” | Raw RSA không có scheme bảo vệ an toàn | Dùng OAEP/PSS được chuẩn hóa, thư viện được kiểm thử |
| “Đã dùng TLS thì không bao giờ cần chữ ký message” | TLS bảo vệ kênh theo session; có hệ thống cần kiểm toán/ủy quyền message nhiều chặng | Phân tích threat model, ràng buộc chữ ký với thao tác khi cần |

## 12. Gợi ý tự kiểm tra

1. **Hai cặp khóa:** Alice ký file để Bob xác minh. Chỉ rõ khóa nào được dùng để `sign()` và khóa nào được dùng để `verify()`. Nếu Carol có public key của Alice thì Carol có đọc được file gốc gửi kèm chữ ký không? Carol có tự tạo signature mới hợp lệ được không?
2. **Mã hóa:** Alice muốn Bob là người duy nhất đọc được message. Alice dùng public key của ai? Bob dùng private key của ai? Nếu Carol có public key của Bob, Carol có gửi ciphertext riêng để Bob giải mã được không?
3. **Mô hình hóa:** giải thích vì sao `Sign(sk_A, m)` và `Encrypt(pk_B, m)` không thể gộp thành “một phép mã hóa đảo chiều” dù đều thuộc asymmetric cryptography.
4. **Java lab:** chạy `SignVsEncryptDemo.java` với `--release 21` và giải thích bốn dòng stdout. Đổi `altered` về bằng `message` rồi giải thích vì sao phép verify sẽ trả `true`.
5. **RSA:** chạy `RsaPssSignatureDemo.java`, chỉ rõ `PSSParameterSpec` làm gì và tại sao `OAEPParameterSpec` không phải parameter cho signature PSS.
6. **Payload lớn:** tính kích thước plaintext tối đa với RSA-OAEP 3072 bit, SHA-256 (đáp án 318 byte). Lập sơ đồ mã hóa lai để gửi file 10 MB mà không mã hóa cả file trực tiếp bằng RSA.
7. **Public-key substitution:** vẽ kịch bản attacker thay public key Alice tại Bob rồi ký dữ liệu giả. Nêu cách PKI hoặc fingerprint độc lập ngăn attack này.
8. **Ứng dụng hóa:** một API thanh toán cần nội dung không lộ, biết ai tạo lệnh và không trừ tiền hai lần. Nêu từng thuộc tính cần cơ chế nào: confidentiality, signature/authentication, anti-replay và idempotency.
9. **Code review:** gặp `Cipher.getInstance("RSA/ECB/NoPadding")` và `cipher.init(ENCRYPT_MODE, privateKey)` dùng để “ký”. Trình bày vấn đề và thay bằng `Signature` với scheme ký chuẩn.
10. **Trust & lifecycle:** phân biệt việc “signature verify trả true” với kết luận “đúng tổ chức cần tin đã ký” khi chứng thư hết hạn, key bị thu hồi hoặc public key không được xác thực.

Bạn hoàn thành bài khi có thể trả lời được ngay ba câu hỏi: **(1) Dữ liệu cần bí mật hay cần xác thực? (2) Khóa công khai này thuộc người ký hay người nhận? (3) Java API và cryptographic scheme nào phù hợp với mục tiêu đó?**

## Tài liệu tham khảo chính thức

- [Java SE 21 — `java.security.Signature`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/Signature.html): `initSign`, `initVerify`, `update`, `sign` và `verify`.
- [Java SE 21 — `javax.crypto.Cipher`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Cipher.html): `ENCRYPT_MODE`, `DECRYPT_MODE`, transformation và `doFinal`.
- [Java SE 21 — `OAEPParameterSpec`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/spec/OAEPParameterSpec.html): OAEP, MGF, PSource.
- [Java SE 21 — `PSSParameterSpec`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/spec/PSSParameterSpec.html): tham số RSASSA-PSS.
- [Java SE 21 — Standard Algorithm Names](https://docs.oracle.com/en/java/javase/21/docs/specs/security/standard-names.html): Ed25519, RSASSA-PSS và tên transformation RSA.
- [Java 21 — JDK Providers Documentation](https://docs.oracle.com/en/java/javase/21/security/oracle-providers.html): phạm vi hỗ trợ SunEC/SunRsaSign/SunJCE.
- [RFC 8017 — PKCS #1 v2.2](https://www.rfc-editor.org/rfc/rfc8017): phân biệt RSAES-OAEP và RSASSA-PSS, điều kiện và độ dài message.
- [RFC 8032 — Edwards-Curve Digital Signature Algorithm](https://www.rfc-editor.org/rfc/rfc8032): Ed25519 và Ed448.
- [NIST CSRC — Digital Signature](https://csrc.nist.gov/glossary/term/digital_signature): khái niệm, mục tiêu xác thực/toàn vẹn và giới hạn confidentiality.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="digital-signature-004">9.4 RSA PKCS#1 v1.5, RSASSA-PSS, ECDSA và EdDSA</a>

<details>
<summary>Xem chi tiết</summary>

# 9.4 RSA PKCS#1 v1.5, RSASSA-PSS, ECDSA và EdDSA

Chữ ký số là cơ chế mật mã bất đối xứng giúp bên nhận kiểm tra một chuỗi byte có còn nguyên vẹn và có được ký bởi khóa riêng tương ứng với **khóa công khai được tin cậy** hay không. Trong ứng dụng Java, bốn lựa chọn quan trọng là **RSA PKCS#1 v1.5 signature**, **RSASSA-PSS**, **ECDSA** và **EdDSA**. Chúng đều dùng private key để ký và public key để xác minh, nhưng **không thay thế lẫn nhau**: thuật toán hash, tham số, kiểu khóa, định dạng chữ ký và quy tắc verify khác nhau.

Bài này giải thích đầy đủ điểm mạnh và giới hạn, cách chọn scheme, lập trình với **java.security.Signature** trên Java 17+, những vấn đề tương thích giữa Java và hệ thống khác, rồi thực hành bằng hai chương trình chạy độc lập. Đề bài thuộc phần **chữ ký số**; vì vậy “RSA PKCS#1 v1.5” ở đây chỉ **RSASSA-PKCS1-v1_5 để ký**, không phải sơ đồ **RSAES-PKCS1-v1_5 để mã hóa**.

## 1. Mô hình ký và xác minh

~~~text
                  BÊN KÝ                             BÊN NHẬN
message bytes M, private key SK                message bytes M', public key PK
            |                                              |
            v                                              v
  Signature.initSign(SK)                         Signature.initVerify(PK)
  Signature.update(M)                            Signature.update(M')
  Signature.sign() -> S                          Signature.verify(S)
            |                                              |
            +------------- gửi M và S --------------------->|
                                                           |
                                         true: hợp lệ theo key/profile
                                         false: không được chấp nhận
~~~

Một chữ ký hợp lệ chỉ có giá trị khi **public key được xác minh là thuộc đúng chủ thể**. Nếu người gửi tự cung cấp một public key chưa được xác thực cùng message và signature, verify true chỉ chứng minh chữ ký khớp **khóa người gửi tự chọn**. Cần xác thực key từ chứng chỉ hợp lệ, cấu hình tin cậy, JWKS đáng tin hoặc quy trình phân phối khóa được kiểm soát.

**Chữ ký số hỗ trợ:**

- **Integrity:** sửa byte của thông điệp thường khiến chữ ký cũ không còn hợp lệ.
- **Origin authentication:** có cơ sở tin thông điệp được ký bằng private key tương ứng với public key đã xác thực.
- **Bằng chứng kỹ thuật:** hữu ích để kiểm chứng gói phần mềm, thông báo, tài liệu; khả năng quy trách nhiệm/pháp lý còn phụ thuộc quản trị khóa, danh tính, thời gian và quy trình.

**Chữ ký không tự mang lại:** bí mật nội dung, phân quyền thao tác, bảo vệ khỏi replay hoặc bằng chứng chắc chắn về thời điểm ký. Phải kết hợp TLS, authorization, hạn hiệu lực, transaction ID và quy trình quản lý khóa tùy bài toán.

### 1.1. Phân biệt hash, MAC và signature

| Cơ chế | Khóa | Tính chất | Hạn chế |
| --- | --- | --- | --- |
| SHA-256 | Không | Fingerprint của dữ liệu | Kẻ sửa dữ liệu cũng tính lại được hash; không xác thực nguồn. |
| HMAC-SHA256 | Bí mật chung | Xác thực bên biết secret và phát hiện sửa message | Hai phía cùng giữ secret, không phân biệt ai thực sự tạo tag; không mã hóa. |
| RSA/ECDSA/EdDSA signature | Private key ký, public key verify | Integrity và quan hệ với private key đã xác thực danh tính | Không giữ bí mật message; không tự chặn replay. |
| AES-GCM | Khóa đối xứng | Bảo mật plaintext và xác thực ciphertext/AAD | Không phải chữ ký công khai. |

Với một lược đồ chữ ký có tính ngẫu nhiên, ký cùng message hai lần có thể tạo hai chữ ký **khác bytes nhưng cùng hợp lệ**. Vì thế phải dùng **Signature.verify**, không so sánh chữ ký nhận được với chữ ký mới tạo bằng Arrays.equals.

## 2. So sánh bốn scheme

| Scheme | Primitive / khóa | JCA Signature name | Đặc trưng |
| --- | --- | --- | --- |
| **RSASSA-PKCS1-v1_5** | RSA | **SHA256withRSA** | Hash + DigestInfo + EMSA-PKCS1-v1_5 padding. Signature deterministic theo message/key. Phổ biến ở hệ thống hiện hữu. |
| **RSASSA-PSS** | RSA | **RSASSA-PSS** | PSS padding dùng salt và MGF; hash/MGF/saltLength/trailer phải thống nhất. Thường được ưu tiên cho hệ thống RSA mới. |
| **ECDSA** | EC Weierstrass curve (ví dụ P-256) | **SHA256withECDSA** | Chữ ký là cặp số (r,s); nonce ký k phải được xử lý an toàn; có DER và P1363 encoding. |
| **EdDSA** | Edwards curves (Ed25519, Ed448) | **Ed25519**, **Ed448** | Pure EdDSA deterministic với key/message, output gọn; variant pure/prehash/context không được đánh đồng. |

Các kích thước thường được so sánh **RSA-3072, ECDSA P-256 và Ed25519** ở mức bảo mật cổ điển khoảng **128 bit**, nhưng chi phí CPU, kích thước key/signature và khả năng hỗ trợ phụ thuộc provider, giao thức và chính sách. Không suy từ độ dài output rằng một phần mềm hoặc một khóa production đã an toàn.

### 2.1. Điều gì phải khớp giữa hai phía?

Một bên Java ký và bên Go, Node.js, OpenSSL hoặc Java provider khác xác minh được khi cả hai có cùng hợp đồng:

1. Scheme **v1.5/PSS/ECDSA/EdDSA**; cùng hash và variant.
2. Public key/curve/key ID đúng chủ thể, private key tương ứng được bảo vệ.
3. Tham số đặc thù (PSS hash, MGF, saltLength, trailer; ECDSA curve; Ed25519/Ed448).
4. **Bytes đã ký**: UTF-8, canonical JSON, raw body, newline, thứ tự field và dấu phân cách chính xác.
5. Encoding chữ ký **DER, P1363, RSA raw bytes, Ed25519 raw bytes** phù hợp với giao thức.
6. Envelope/version và các kiểm tra bổ sung như timestamp, issuer, key usage, replay.

Cùng tên thuật toán **không đủ** nếu input bytes hoặc encoding khác.

## 3. RSASSA-PKCS1-v1_5: RSA signature với padding cổ điển

### 3.1. Cơ chế

Khi dùng **SHA256withRSA**, JCA thực hiện sơ đồ chữ ký RSASSA-PKCS1-v1_5:

1. Băm đúng bytes thông điệp bằng **SHA-256**.
2. Đưa hash và định danh thuật toán vào cấu trúc **DigestInfo** dạng ASN.1 DER.
3. Dùng **EMSA-PKCS1-v1_5** tạo encoded message có cấu trúc padding kiểm tra nghiêm ngặt.
4. Thực hiện phép toán ký RSA bằng **private key**; verify thực hiện quy tắc đối ứng bằng public key.

~~~text
M -> SHA-256(M) -> DigestInfo
                   |
                   v
           EMSA-PKCS1-v1_5 encoding
                   |
                   v
             RSA signature
~~~

Khi cùng key và input bytes, scheme tạo cùng chữ ký toán học; implementation có thể dùng RSA blinding khi tính toán mà không đổi kết quả cuối. Chữ ký RSA có kích thước bằng kích thước **modulus** làm tròn lên byte, ví dụ RSA-3072 tạo **384 byte**.

### 3.2. Tại sao không dùng Cipher với private key?

**RSAES-PKCS1-v1_5** và **RSASSA-PKCS1-v1_5** dùng các quy tắc padding khác nhau. Viết **Cipher.getInstance("RSA/ECB/PKCS1Padding")**, “mã hóa” hash bằng private key rồi tự đối chiếu không phải cách triển khai một sơ đồ chữ ký an toàn được chuẩn hóa. Dùng **Signature.getInstance("SHA256withRSA")** để provider xử lý cả hash identifier, padding, kiểm tra lỗi và phép toán ký/xác minh.

### 3.3. Nên dùng ở đâu?

- Hỗ trợ giao thức legacy có profile **RSASSA-PKCS1-v1_5** đã quy định rõ.
- Hệ thống hiện hữu có nhà cung cấp/khóa/cert chỉ hỗ trợ profile tương thích.
- Với thiết kế RSA mới, cân nhắc **RSASSA-PSS** nếu cả hai bên hỗ trợ. RFC 8017 yêu cầu PSS cho các ứng dụng RSA mới trong phạm vi đặc tả và giữ PKCS#1 v1.5 vì compatibility.

Dùng tên thuật toán hash cụ thể như **SHA256withRSA**; không chọn **SHA1withRSA** hay **MD5withRSA** cho scheme mới. Verifier phải kiểm tra nghiêm ngặt cả cấu trúc padding, không tự viết parser “tìm hash ở cuối block”.

## 4. RSASSA-PSS: RSA signature với salt và mask generation

RSA-PSS dùng **EMSA-PSS** để tạo encoded message từ hash thông điệp, salt và **mask generation function (MGF)**. Salt ở PSS là tham số cho từng phép ký, **khác với salt của password hashing**. Salt PSS không cần được lưu riêng cạnh chữ ký theo profile chuẩn; verifier kiểm tra trong cấu trúc encoded message.

~~~text
message M ----> Hash(M) --------+
                               |
salt ngẫu nhiên ----------------+--> EMSA-PSS-ENCODE
                                    (MGF1, saltLength, trailer)
                                                |
                                                v
                                      RSA private-key operation
                                                |
                                                v
                                           PSS signature
~~~

PSS thường tạo ra **chữ ký khác nhau** khi ký cùng message/key vì salt thay đổi; vẫn verify đúng với cùng profile tham số.

### 4.1. Các tham số cần chốt chính xác

| Tham số | Profile minh họa | Ý nghĩa |
| --- | --- | --- |
| Hash | **SHA-256** | Hash thông điệp dùng trong PSS. |
| MGF | **MGF1** | Mask generation function. |
| MGF digest | **SHA-256** | Hash của MGF1; phải khai báo riêng. |
| Salt length | **32 byte** | Thường bằng SHA-256 output trong profile này; không mặc định cho mọi profile. |
| Trailer field | **1** | Giá trị tham số JCA tương ứng trailer 0xBC. |
| RSA modulus | 3072 bit | Kích thước RSA đủ cho profile đã chọn. |

Ví dụ trong một method đã có rsaPrivateKey và messageBytes:

~~~java
PSSParameterSpec pss = new PSSParameterSpec(
        "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1);

Signature signer = Signature.getInstance("RSASSA-PSS");
signer.setParameter(pss);
signer.initSign(rsaPrivateKey);
signer.update(messageBytes);
byte[] signature = signer.sign();
~~~

Đây là **mảnh minh họa**, không phải lớp Java độc lập; cần import java.security.* và java.security.spec.*. Code chạy hoàn chỉnh ở phần thực hành.

Trong Java, **PSSParameterSpec** có thứ tự **digestAlgorithm, mgfAlgorithm, mgfParameters, saltLen, trailerField**. **RSASSA-PSS** là tên algorithm, **không đủ** tự quy định SHA-256/MGF1-SHA256/salt 32/trailer 1. Cần cấu hình đầy đủ ở bên ký và verify, lưu profile theo version và key ID; không dựa vào default của provider.

### 4.2. Không tự fallback PSS params

Nếu chữ ký được tạo với saltLength=32 nhưng verifier mong saltLength=20, xác minh phải thất bại. Tùy provider, có thể nhận **verify(false)** hoặc lỗi tham số. Không tự thử giảm salt hoặc chuyển sang v1.5 cho đến khi kết quả khớp. Nếu buộc phải hỗ trợ nhiều profile legacy, server phải có **allowlist cố định theo key/version**, không cho message chưa xác thực quyết định mức bảo mật.

### 4.3. Dùng cùng một cặp khóa RSA cho PSS và v1.5?

Một RSA key có thể tương thích cả hai scheme nếu key usage/provider cho phép, nhưng **chữ ký không tương thích**. Thiết kế nên tách hoặc ràng buộc **key ID ↔ scheme/hash/params/purpose** để tránh algorithm confusion. HSM có thể chặn một số scheme/saltLength ngay cả khi khóa về mặt toán học là RSA.

## 5. ECDSA: chữ ký trên elliptic curve

### 5.1. Curve, hash và nonce ký

**ECDSA** dùng private scalar và public point trên một **elliptic curve Weierstrass**. Một profile phổ biến:

- Key algorithm: **EC**.
- Curve: **secp256r1** (NIST P-256).
- Hash: **SHA-256**.
- Signature algorithm: **SHA256withECDSA**.
- Chữ ký toán học: một cặp số nguyên **(r,s)**.

Khi tạo chữ ký, ECDSA cần một giá trị **nonce ký k**, bí mật và được tạo đúng theo yêu cầu của thuật toán. **Tái sử dụng k giữa các message khác nhau dưới cùng private key, hoặc k bị đoán/bias**, có thể dẫn tới lộ private key. Đừng tự triển khai nonce bằng timestamp, Random hoặc counter ứng dụng; dùng provider mật mã được kiểm chứng. Một số ECDSA implementation sử dụng nonce xác định theo RFC 6979, nhưng Java không bảo đảm mọi provider đều ký deterministic theo RFC 6979.

~~~text
message M + private key d + nonce k trên curve
              |
              v
       ECDSA signature (r,s)
              |
              v
   encode r,s thành DER hoặc P1363 bytes
~~~

**Không tự băm message trước** khi gọi **Signature.getInstance("SHA256withECDSA")**, vì tên đó đã gồm SHA-256. Nếu đưa SHA256(message) vào update, bạn đang ký một **message khác** và tạo ra hành vi double-hash không đúng profile thông thường.

Đoạn minh họa trong một method có biến messageBytes, import java.security.* và java.security.spec.*:

~~~java
KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
gen.initialize(new ECGenParameterSpec("secp256r1"));
KeyPair keys = gen.generateKeyPair();

Signature signer = Signature.getInstance("SHA256withECDSA");
signer.initSign(keys.getPrivate());
signer.update(messageBytes);
byte[] derSignature = signer.sign();
~~~

**Curve quan trọng:** một public key thuộc P-256 không tự nhiên tương thích với curve P-384 hoặc Ed25519. Không suy ra mọi khóa có tên “EC” đều dùng cho mọi curve/profile. Signature.verify cần public key đúng scheme/curve và bytes của message.

### 5.2. Hai encoding của chữ ký ECDSA: DER và IEEE P1363

Đây là nguồn lỗi liên hệ thống rất thường gặp. Cặp toán học (r,s) có thể được mã hóa thành:

| Encoding | Cấu trúc | P-256 | Java Signature name |
| --- | --- | --- | --- |
| **ASN.1 DER** | SEQUENCE chứa hai INTEGER DER là r và s | Độ dài thường quanh 70–72 byte, **có thể thay đổi** | **SHA256withECDSA** |
| **IEEE P1363** | Hai số nguyên big-endian fixed-width, nối **r\|\|s** | **32 byte r + 32 byte s = 64 byte** | **SHA256withECDSAinP1363Format** |

DER INTEGER sử dụng biểu diễn có dấu và có thể cần byte 0x00 ở đầu để không bị coi là âm; vì vậy **không thể chia đôi một chữ ký DER** để lấy r/s. DER còn có các trường length và SEQUENCE. Hãy dùng parser ASN.1 nghiêm ngặt nếu cần chuyển encoding, hoặc chọn đúng JCA algorithm name có **inP1363Format** khi cả phía triển khai hỗ trợ.

JWS **ES256** trong JOSE dùng ECDSA P-256 + SHA-256 nhưng chữ ký trên wire là **P1363 raw 64 byte**; Java **SHA256withECDSA** thường trả về DER. Nếu hai bên không quy định rõ định dạng, verify sẽ thất bại dù khóa, curve và message đúng.

### 5.3. Tính không duy nhất và quy tắc low-S

Trong một số profile ECDSA, cả (r,s) và (r,n−s) đều có thể là chữ ký toán học hợp lệ cho cùng một message (với n là order của nhóm). Một số giao thức yêu cầu **low-S canonicalization** để giới hạn biến thể chữ ký. Không giả định Signature.verify của mọi Java provider tự áp quy tắc này. Nếu giao thức cần low-S, phải kiểm tra theo **quy định cụ thể của giao thức** và có negative tests.

## 6. EdDSA: Ed25519 và Ed448

### 6.1. EdDSA khác ECDSA ở đâu?

**EdDSA** (RFC 8032) sử dụng Edwards curves. Hai profile nổi bật:

| Profile | Curve | Raw public key (thuật toán) | Signature | Ghi chú |
| --- | --- | --- | --- | --- |
| **Ed25519** | edwards25519 | 32 byte | 64 byte | Mức bảo mật cổ điển khoảng 128-bit theo profile. |
| **Ed448** | edwards448 | 57 byte | 114 byte | Mức bảo mật cổ điển cao hơn, khoảng 224-bit. |

Đây là kích thước **raw key theo RFC**; key khi lưu dạng **SubjectPublicKeyInfo/X.509** có thêm metadata ASN.1 nên byte file không cùng độ dài raw key. **Ed25519** không phải **X25519**: Ed25519 dùng cho signature, X25519 dùng cho key agreement. Tên cùng có “25519” không có nghĩa khóa và thuật toán có thể thay thế.

**Pure EdDSA deterministic:** ký cùng message bytes với cùng private key theo Ed25519 pure sẽ tạo cùng signature bytes. Thuộc tính này hạn chế một lớp lỗi nonce ngẫu nhiên kiểu ECDSA, nhưng vẫn cần **CSPRNG khi sinh khóa**, bảo vệ khóa riêng, an toàn implementation và xác thực nguồn public key.

### 6.2. Cách dùng Java 17+

Đoạn minh họa trong method có messageBytes và import java.security.*:

~~~java
KeyPairGenerator gen = KeyPairGenerator.getInstance("Ed25519");
KeyPair pair = gen.generateKeyPair();

Signature signer = Signature.getInstance("Ed25519");
signer.initSign(pair.getPrivate());
signer.update(messageBytes);
byte[] signature = signer.sign();

Signature verifier = Signature.getInstance("Ed25519");
verifier.initVerify(pair.getPublic());
verifier.update(messageBytes);
boolean valid = verifier.verify(signature);
~~~

Không cần khai báo **SHA256withEdDSA** hay **PSSParameterSpec**. Tên **Ed25519** đã mô tả scheme cụ thể. Có thể kiểm tra hỗ trợ **Ed448** theo tên Java chuẩn nếu provider và policy cho phép; không đổi từ Ed25519 sang Ed448 một cách im lặng trong giao thức.

### 6.3. Pure Ed25519, Ed25519ph và Ed25519ctx

RFC 8032 còn có:

- **Ed25519 (pure):** ký message theo profile chuẩn.
- **Ed25519ph:** prehash variant theo đúng domain separation/quy tắc RFC.
- **Ed25519ctx:** contextualized variant, có context string tham gia vào phép ký.

Chúng **không tự tương thích với nhau**. Đặc biệt, **SHA-512(message)** rồi gọi **Signature("Ed25519")** không biến phép ký thành Ed25519ph; đó chỉ là ký một chuỗi message khác bằng **pure Ed25519**. Nếu giao thức thực sự yêu cầu ph/ctx, cần implementation hỗ trợ variant đó chính xác.

**Ed25519 deterministic** không có nghĩa phải kiểm tra signature bằng cách ký lại. Nếu người nhận không giữ private key, họ vẫn dùng **Signature.verify** với public key. Chỉ bên giữ private key được phép gọi initSign.

## 7. Tham số, kích thước và format phải phù hợp

| Profile | Key generation | JCA signing | Dạng signature | Điều cần giữ |
| --- | --- | --- | --- | --- |
| **RSA-v1.5 + SHA-256** | RSA 3072-bit | SHA256withRSA | 384 byte với RSA-3072 | Scheme v1.5, digest, key ID. |
| **RSA-PSS + SHA-256** | RSA 3072-bit | RSASSA-PSS | 384 byte với RSA-3072 | SHA-256, MGF1/SHA-256, salt 32 byte, trailer 1. |
| **ECDSA P-256 DER** | EC / secp256r1 | SHA256withECDSA | ASN.1 DER, độ dài thay đổi | Curve P-256, SHA-256, DER. |
| **ECDSA P-256 P1363** | EC / secp256r1 | SHA256withECDSAinP1363Format | Raw r\|\|s = 64 byte | Curve P-256, SHA-256, P1363. |
| **Ed25519 pure** | Ed25519 | Ed25519 | 64 byte | Đúng variant Ed25519 pure, message bytes. |
| **Ed448 pure** | Ed448 | Ed448 | 114 byte | Đúng variant Ed448 pure, message bytes. |

Kích thước RSA được nói bằng **bit modulus**, vì vậy RSA-3072 sinh chữ ký 384 **byte**, không phải 3072 byte. Với ECDSA, format DER có thể dài khác nhau giữa các chữ ký; độ dài đó không phải tiêu chí phân biệt khóa mạnh/yếu.

### 7.1. Tên scheme không phải tên key algorithm

Trong Java:

- **KeyPairGenerator("RSA")** tạo khóa RSA; **Signature("SHA256withRSA")** hoặc **Signature("RSASSA-PSS")** dùng các scheme khác nhau trên khóa RSA phù hợp.
- **KeyPairGenerator("EC")** với **ECGenParameterSpec("secp256r1")** tạo EC key; **Signature("SHA256withECDSA")** ký.
- **KeyPairGenerator("Ed25519")** tạo Ed25519 key; **Signature("Ed25519")** ký.
- **X25519** thuộc KeyAgreement, không phải Signature.

Không dùng “RSA” hoặc “EC” đơn lẻ thay cho một hợp đồng thuật toán chữ ký đã đầy đủ.

## 8. Public key có đúng danh tính không?

**Signature.verify(true)** chỉ cho biết một kết quả **đúng theo public key được cung cấp**. Nếu client gửi public key tự tạo và server lập tức tin đó là ngân hàng hoặc dịch vụ thanh toán, việc verify không có giá trị xác thực danh tính.

Nguồn khóa tin cậy thường là:

1. X.509 certificate với đường chứng thực, key usage, thời hạn, danh tính và chính sách tin cậy được xác minh.
2. JWKS hoặc endpoint khóa từ issuer đã được cấu hình và truy cập qua kênh phù hợp, với quy tắc key ID/rotation.
3. Public key được pin theo đối tác trong cấu hình deployment, secret manager hoặc kho cấu hình đã phân quyền.
4. KMS/HSM quản lý private key ký, còn verifier nhận public key tương ứng qua kênh đáng tin.

**Định dạng khóa và định dạng signature khác nhau.**

| Loại dữ liệu | API hoặc encoding thường gặp | Điều cần nhớ |
| --- | --- | --- |
| Public key xuất/nhập | **X509EncodedKeySpec**, X.509 SubjectPublicKeyInfo | Chuỗi BEGIN PUBLIC KEY thường chứa SPKI Base64; không phải chứng chỉ đầy đủ. |
| Private key có thể xuất | **PKCS8EncodedKeySpec** | Private key cực nhạy cảm, có thể không xuất được từ HSM/KMS. |
| Chứng chỉ X.509 | **CertificateFactory**, X509Certificate | Cần xác minh chain/purpose/danh tính, không chỉ đọc public key. |
| Chữ ký ECDSA DER | **Signature("SHA256withECDSA")** | ASN.1 (r,s), không phải encoding của public key. |
| Chữ ký Ed25519 | **Signature("Ed25519")** | 64-byte raw signature; raw key có định dạng và chiều dài khác. |

**Key.getEncoded() có thể trả null** với khóa không xuất được từ hardware provider. Không giả định mọi khóa Java đều di chuyển được giữa các provider. Với khóa có thể xuất, nhập phải dùng đúng **KeyFactory**, định dạng, thuật toán và chính sách.

## 9. Bytes được ký quyết định kết quả verify

Java **Signature.update(byte[])** nhận byte sequence thực tế; Java object có cùng giá trị nghiệp vụ chưa chắc có cùng bytes.

~~~text
JSON A: {"order":42,"amount":250000}
JSON B: { "amount": 250000, "order": 42 }

Hai biểu diễn có thể mô tả cùng đối tượng,
nhưng byte sequence A và B khác nhau.
Chữ ký trên A không mặc nhiên xác minh được trên B.
~~~

Các lỗi tiêu biểu:

- Bên ký dùng **UTF-8**, bên xác minh chuyển String theo charset khác.
- Bên ký trên **raw HTTP body**, bên nhận parse JSON rồi serialize lại.
- Một phía giữ **newline cuối file**, phía kia loại bỏ.
- Một bên dùng Unicode normalization **NFC**, phía còn lại giữ dạng tổ hợp ký tự.
- Header timestamp, tenant, action hoặc key ID **không nằm trong bytes đã ký** nhưng server lại tin chúng.
- Bên nhận coi **Base64 signature text** như raw signature bytes thay vì decode đúng.
- Bên ký tự **SHA-256 message trước** dù Signature algorithm đã tự hash.

Hãy định nghĩa **canonicalization**, thứ tự trường, delimiter, format version, cách encode timestamp và byte sequence chính xác theo giao thức. Với webhook yêu cầu ký raw request body, phải giữ raw bytes. Không thử hàng loạt cách serialize đến khi tìm được cách verify true.

### 9.1. JWS/JWT và chữ ký Java

| JWS alg | Scheme | Ánh xạ Java |
| --- | --- | --- |
| **RS256** | RSA v1.5 + SHA-256 | **SHA256withRSA** |
| **PS256** | RSA-PSS + SHA-256 | **RSASSA-PSS** với profile SHA-256 / MGF1 SHA-256 / salt 32 byte |
| **ES256** | ECDSA P-256 + SHA-256 | **SHA256withECDSAinP1363Format** cho wire raw r\|\|s nếu provider hỗ trợ; DER thông thường phải chuyển đổi đúng |
| **EdDSA** với Ed25519 | Ed25519 | **Signature.getInstance("Ed25519")** |

JWS signature bảo vệ **bytes của signing input được chuẩn xác theo JWS**, thường gồm encoded header, dấu chấm và encoded payload. Không chỉ ký mỗi JSON payload rồi gọi đó là một chữ ký JWS. Khi triển khai JWT, nên dùng **JOSE/JWT library được duy trì** với allowlist algorithm, key ID, issuer, audience, expiry và chính sách token thay vì tự chế parser hoặc tin header **alg** từ nguồn không đáng tin.

Nếu đã chọn expected algorithm và public key theo policy server, **không để message chưa xác thực điều khiển algorithm fallback** (ví dụ từ PS256 xuống RS256 hay "none") hoặc điều khiển nguồn key một cách tùy ý.

## 10. Java 17+: thực hành ký và verify bốn scheme

Chương trình đầu tiên tạo ba cặp khóa dùng trong một lần chạy: **RSA-3072**, **EC P-256** và **Ed25519**. Nó ký đúng byte của **order=42;amount=250000** rồi thử verify trên message đã sửa thành **order=42;amount=250001**. Mỗi phép ký và verify dùng một **Signature instance riêng**, tránh trộn trạng thái.

Phần cuối còn kiểm tra pure **Ed25519 deterministic** và tình huống **RSA-PSS verify sai saltLength**. Để kết quả stdout không phụ thuộc bytes chữ ký có thể thay đổi, chương trình in boolean và nhãn profile.

Tạo file **SignatureSchemesDemo.java**:

~~~java
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Arrays;

public class SignatureSchemesDemo {
    private static final byte[] MESSAGE =
            "order=42;amount=250000".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ALTERED =
            "order=42;amount=250001".getBytes(StandardCharsets.UTF_8);
    private static final PSSParameterSpec PSS_SHA256 =
            new PSSParameterSpec(
                    "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1);

    private static Signature engine(String algorithm, boolean pss)
            throws Exception {
        Signature signature = Signature.getInstance(algorithm);
        if (pss) {
            signature.setParameter(PSS_SHA256);
        }
        return signature;
    }

    private static byte[] sign(String algorithm, boolean pss,
            KeyPair keys, byte[] message) throws Exception {
        Signature signature = engine(algorithm, pss);
        signature.initSign(keys.getPrivate());
        signature.update(message);
        return signature.sign();
    }

    private static boolean verify(String algorithm, boolean pss,
            KeyPair keys, byte[] message, byte[] signed) throws Exception {
        Signature verifier = engine(algorithm, pss);
        verifier.initVerify(keys.getPublic());
        verifier.update(message);
        return verifier.verify(signed);
    }

    private static void check(String label, String algorithm, boolean pss,
            KeyPair keys) throws Exception {
        byte[] signed = sign(algorithm, pss, keys, MESSAGE);
        boolean ok = verify(algorithm, pss, keys, MESSAGE, signed);
        boolean changed = verify(algorithm, pss, keys, ALTERED, signed);
        System.out.println(label + ": hợp lệ=" + ok +
                ", sửa nội dung=" + changed);
    }

    public static void main(String[] args) throws Exception {
        KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
        rsa.initialize(3072);
        KeyPair rsaKeys = rsa.generateKeyPair();

        KeyPairGenerator ec = KeyPairGenerator.getInstance("EC");
        ec.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair ecKeys = ec.generateKeyPair();

        KeyPairGenerator ed = KeyPairGenerator.getInstance("Ed25519");
        KeyPair edKeys = ed.generateKeyPair();

        check("PKCS1-v1_5", "SHA256withRSA", false, rsaKeys);
        check("PSS", "RSASSA-PSS", true, rsaKeys);
        check("ECDSA", "SHA256withECDSA", false, ecKeys);
        check("Ed25519", "Ed25519", false, edKeys);

        byte[] signedEd = sign("Ed25519", false, edKeys, MESSAGE);
        byte[] signedAgain = sign("Ed25519", false, edKeys, MESSAGE);
        System.out.println("Ed25519 lặp lại cùng chữ ký: " +
                Arrays.equals(signedEd, signedAgain));

        Signature incompatiblePss = Signature.getInstance("RSASSA-PSS");
        incompatiblePss.setParameter(new PSSParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 20, 1));
        incompatiblePss.initVerify(rsaKeys.getPublic());
        incompatiblePss.update(MESSAGE);
        byte[] pssTag = sign("RSASSA-PSS", true, rsaKeys, MESSAGE);
        System.out.println("PSS sai salt length: " +
                incompatiblePss.verify(pssTag));
    }
}
~~~

Biên dịch với JDK có hỗ trợ các profile chuẩn của Java 17:

~~~bash
javac --release 17 -encoding UTF-8 SignatureSchemesDemo.java
java SignatureSchemesDemo
~~~

Stdout trên JDK/provider đã kiểm thử:

~~~text
PKCS1-v1_5: hợp lệ=true, sửa nội dung=false
PSS: hợp lệ=true, sửa nội dung=false
ECDSA: hợp lệ=true, sửa nội dung=false
Ed25519: hợp lệ=true, sửa nội dung=false
Ed25519 lặp lại cùng chữ ký: true
PSS sai salt length: false
~~~

### 10.1. Vì sao ví dụ có giá trị học tập?

1. **RSA-v1.5:** Signature("SHA256withRSA") kết hợp hash + scheme v1.5, dùng RSA key.
2. **RSA-PSS:** Signature("RSASSA-PSS") có **PSSParameterSpec** cụ thể; tham số này được đặt trước khi ký hoặc verify.
3. **ECDSA:** KeyPairGenerator("EC") được khởi tạo trên **secp256r1**, còn Signature dùng **SHA256withECDSA**.
4. **Ed25519:** KeyPairGenerator và Signature cùng profile **Ed25519**, không cần gửi PSS params.
5. **Tamper test:** byte message thay đổi khiến verify false với cùng chữ ký và public key.
6. **Deterministic test:** pure Ed25519 với cùng key/message cho chữ ký giống nhau; việc xác minh hợp lệ vẫn phải dùng Signature.verify.
7. **Wrong PSS params:** bên ký salt 32 byte, bên xác minh salt 20 byte. Trong provider đã chạy, verify false; provider khác có thể từ chối thông số hoặc khóa bằng exception. Không tự fallback.
8. **Private key test-only:** khóa sinh tạm trong bộ nhớ và không in/ghi ra; production cần KeyStore, KMS/HSM, key usage và public-key trust.

**Giới hạn:** không dùng một lần chạy để khẳng định provider nào cũng hỗ trợ. Các khóa mới được tạo ở mỗi lần chạy nên signatures cụ thể có thể khác; chương trình in **trạng thái kiểm tra**, không in signature bytes. Không dùng các khóa được sinh ngẫu nhiên trong demo để đại diện khóa identity production.

## 11. Java 17+: kiểm tra ECDSA DER và P1363

Chương trình thứ hai minh họa trực tiếp sự khác nhau giữa **SHA256withECDSA** (DER) và **SHA256withECDSAinP1363Format** (raw r||s) trên cùng EC P-256 key và cùng message. Mỗi chữ ký được verify bằng chính algorithm có encoding tương ứng. Không so sánh hai chữ ký với nhau vì nonce ECDSA có thể khác.

Tạo file **EcdsaFormatDemo.java**:

~~~java
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;

public class EcdsaFormatDemo {
    private static final byte[] MESSAGE =
            "payload-v1".getBytes(StandardCharsets.UTF_8);

    private static byte[] sign(String algorithm, KeyPair pair)
            throws Exception {
        Signature signer = Signature.getInstance(algorithm);
        signer.initSign(pair.getPrivate());
        signer.update(MESSAGE);
        return signer.sign();
    }

    private static boolean verify(
            String algorithm, KeyPair pair, byte[] signed)
            throws Exception {
        Signature verifier = Signature.getInstance(algorithm);
        verifier.initVerify(pair.getPublic());
        verifier.update(MESSAGE);
        return verifier.verify(signed);
    }

    public static void main(String[] args) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = gen.generateKeyPair();

        byte[] der = sign("SHA256withECDSA", pair);
        byte[] raw = sign("SHA256withECDSAinP1363Format", pair);

        System.out.println("DER ECDSA đúng: " +
                verify("SHA256withECDSA", pair, der));
        System.out.println("P1363 ECDSA đúng: " +
                verify("SHA256withECDSAinP1363Format", pair, raw));
        System.out.println("P1363 P-256 dài 64 byte: " +
                (raw.length == 64));
        System.out.println("DER bắt đầu ASN.1 SEQUENCE: " +
                ((der[0] & 0xff) == 0x30));
    }
}
~~~

~~~bash
javac --release 17 -encoding UTF-8 EcdsaFormatDemo.java
java EcdsaFormatDemo
~~~

Stdout:

~~~text
DER ECDSA đúng: true
P1363 ECDSA đúng: true
P1363 P-256 dài 64 byte: true
DER bắt đầu ASN.1 SEQUENCE: true
~~~

### 11.1. Cách hiểu đúng

- **SHA256withECDSA** tạo/chấp nhận signature theo **DER ASN.1** với r/s là INTEGER, do đó tổng độ dài có thể biến thiên.
- **SHA256withECDSAinP1363Format** tạo/chấp nhận **raw r||s**. Với P-256, mỗi số nguyên có kích thước 32 byte nên signature dài **64 byte**.
- Byte đầu DER dạng SEQUENCE là **0x30**, nhưng chỉ kiểm tra byte đầu **không phải bộ kiểm tra ASN.1** và không được dùng như security validation. Signature.verify của provider mới là bước xác minh.
- Khi tích hợp với hệ thống khác yêu cầu ES256 raw, không được tự cắt một DER signature thành hai nửa; dùng thư viện DER/P1363 conversion chuẩn hoặc chọn đúng API P1363.
- Không giả định hai lần ký ECDSA trên cùng message phải trả cùng signature bytes.

**Hai chương trình trên là ví dụ hoàn chỉnh có main** và được biên dịch với **javac --release 17**; chỉ những đoạn code nhỏ ở phần lý thuyết mới là mảnh minh họa nằm trong method.

## 12. JCA provider, quản lý khóa và khả năng tương thích

**JCA/JCE là lớp trừu tượng**: Signature, KeyPairGenerator và KeyFactory là engine API; provider thực thi thuật toán. Cùng tên thuật toán không bảo đảm mọi JVM, provider hoặc HSM hỗ trợ **cùng kiểu khóa, curve, PSS params, encoding và thông lượng**.

### 12.1. Những lỗi provider có thể trả

| Triệu chứng Java | Nguyên nhân có thể | Cách kiểm tra |
| --- | --- | --- |
| **NoSuchAlgorithmException** | Provider/JDK không cung cấp tên Signature hoặc KeyPairGenerator | Xác nhận Java version, provider order, tên chuẩn và thuật toán được policy cho phép. |
| **InvalidAlgorithmParameterException** | Curve, saltLength, PSSParameterSpec hoặc tham số khác không được hỗ trợ | Đối chiếu profile chuẩn và khả năng provider/HSM. |
| **InvalidKeyException** | Key sai loại, yếu, từ provider khác hoặc key usage bị cấm | Kiểm tra key type, key size, alias, HSM permissions và chính sách. |
| **SignatureException** | Sai format/chữ ký chưa được khởi tạo/operation không hợp lệ | Kiểm tra DER/P1363, bytes, init mode, input length và provider. |
| **verify(false)** | Sai message, key, params hoặc signature hợp lệ về format nhưng không khớp | Tìm chính xác khác biệt của test vector; không mặc định do tấn công. |
| **Lỗi chỉ xảy ra dưới tải** | Chia sẻ Signature mutable, provider lock/session cạn, key bị rotate giữa request | Tách engine per operation, quản lý phiên HSM và key version nhất quán. |

Khi cần chẩn đoán trong môi trường tin cậy, **Signature.getProvider()** và **Signature.getAlgorithm()** cho biết implementation và tên đã được chọn; nhớ rằng một số engine/provider có thể chọn cài đặt theo khóa khi init. Ghi log tên thuật toán/key ID theo chính sách, tránh log private key, mật khẩu keystore, raw sensitive message và chữ ký chứa thông tin nhạy cảm từ giao thức.

### 12.2. Quy tắc quản lý khóa

1. **Sinh khóa** theo curve, kích thước và nguồn ngẫu nhiên phù hợp; RSA size và EC curve phải được phê duyệt.
2. **Lưu private key** trong cơ chế bảo vệ phù hợp (KeyStore được bảo vệ, HSM/KMS, secret manager) với quyền tối thiểu; không commit private key vào repository hoặc container image.
3. **Phân phối public key** qua kênh có xác thực và gắn đúng issuer/đối tác; không chấp nhận key tùy ý do request gửi đến.
4. **Ràng buộc key ID ↔ thuật toán/scheme/hash/curve/params**; nếu thay khóa, record/token cần xác định version để verify đúng.
5. **Tách mục đích dùng khóa** giữa signature, key agreement và encryption khi chính sách yêu cầu. Đừng dùng Ed25519 như X25519 hoặc dùng RSA private key cho bất kỳ operation nào client chỉ định.
6. **Luân chuyển và thu hồi** với hạn chấp nhận chữ ký cũ rõ ràng. Một chữ ký cũ có thể verify đúng về toán học nhưng vẫn bị từ chối theo thời hạn hoặc revoke.
7. **Bảo vệ key operation** bằng giới hạn tốc độ, giám sát bất thường và xử lý khi khóa bị lộ.

### 12.3. Vì sao cần kiểm tra public key khi dùng certificate?

Một certificate X.509 có public key và thông tin danh tính, nhưng chỉ trích xuất public key rồi gọi Signature.verify **không đồng nghĩa certificate đã đáng tin**. Cần kiểm tra certificate path theo trust anchor, thời gian, mục đích sử dụng, revocation theo chính sách, và danh tính dịch vụ/chủ thể được kỳ vọng. Với JWS/JWT, issuer/audience và trusted JWKS là một phần của quy trình riêng.

## 13. Signature là engine stateful: không dùng chung instance giữa thread

Một đối tượng **java.security.Signature** thay đổi trạng thái qua **initSign/initVerify → update → sign/verify**. Nếu đặt một Signature trong static field của web singleton rồi nhiều request cùng chạy, thao tác của A và B có thể xen lẫn:

~~~text
Không an toàn:
shared.initVerify(keyA)
shared.update(messageA)
                           shared.initVerify(keyB)
                           shared.update(messageB)
shared.verify(sigA)  -> trạng thái không còn thuộc messageA/keyA

Mẫu đơn giản:
request A -> Signature instance riêng -> initVerify A -> update A -> verify A
request B -> Signature instance riêng -> initVerify B -> update B -> verify B
~~~

Tạo **một Signature riêng cho từng lần ký hoặc verify** như hai ví dụ. Không suy diễn rằng vì SecureRandom thread-safe thì Signature cũng có thể chia sẻ. Dùng synchronized từng phương thức riêng lẻ **không bảo vệ cả chuỗi thao tác**; nếu tuần tự hóa toàn bộ chuỗi, lại làm mất lợi ích của concurrency.

Một public key object/key handle có thể được tái sử dụng nếu provider cho phép, nhưng khác hoàn toàn với chia sẻ **engine đang giữ message state**. Với HSM, còn cần session pool, timeout và backpressure; không nên “tối ưu” bằng cách tái sử dụng Signature mutable một cách không kiểm soát.

## 14. Kiểm tra message và signature trước khi xử lý nghiệp vụ

Một quy trình nhận webhook có chữ ký, gói phát hành hoặc token có thể gồm:

1. **Giới hạn kích thước request**, parse envelope/version, nhận signature bytes và key ID đúng định dạng.
2. Xác định **issuer/đối tác mong đợi và thuật toán được phép** từ cấu hình server. Không dùng alg từ dữ liệu chưa xác thực để tự mở rộng allowlist.
3. Lấy **trusted public key** theo key ID được phép; kiểm tra trạng thái và quyền sử dụng khóa.
4. Chuẩn bị **message bytes** theo giao thức đã quy định, giữ nguyên raw body hoặc canonical encoding; bảo vệ metadata ảnh hưởng nghiệp vụ.
5. Dùng **Signature.getInstance** đúng algorithm/params/encoding, initVerify, update bytes và verify.
6. Nếu verify false hoặc dữ liệu/format/algorithm không hợp lệ, **từ chối**; không thực hiện giao dịch hoặc phát quyền.
7. Nếu verify true, kiểm tra **thời hạn, issuer, audience, replay/idempotency, phân quyền và trạng thái nghiệp vụ** trước tác động.
8. Log request ID, key ID, profile và lý do kỹ thuật ở mức cho phép; phản hồi bên ngoài không làm lộ bí mật hoặc trạng thái nhạy cảm không cần thiết.

**Cảnh báo về replay:** kẻ tấn công có thể gửi lại nguyên payload và signature hợp lệ. Chữ ký sẽ tiếp tục verify đúng; ứng dụng phải có cơ chế freshness, nonce, thời gian, transaction ID và idempotency theo yêu cầu. Đặc biệt, timestamp hoặc nonce dùng chống replay cần được gắn với bytes đã ký hoặc ngữ cảnh tin cậy.

## 15. Quy tắc chọn scheme

| Bài toán | Lựa chọn thực tiễn | Tiêu chí quyết định |
| --- | --- | --- |
| Tích hợp hệ thống RSA legacy | **SHA256withRSA / RSASSA-PKCS1-v1_5** nếu policy cho phép | Hệ thống đối tác hỗ trợ profile v1.5 và có kế hoạch migration. |
| Thiết kế API signature RSA mới | **RSASSA-PSS** | Hai bên thống nhất đầy đủ SHA/MGF/saltLength/trailer, key size và provider. |
| Hệ sinh thái ECDSA P-256 | **SHA256withECDSA**, DER hoặc P1363 phù hợp | Curve, hash, encoding on-wire, quản lý nonce và provider. |
| API hoặc gói phần mềm hỗ trợ Ed25519 | **Signature("Ed25519")** | Pure Ed25519 đúng chuẩn, key distribution, provider/HSM compatibility. |
| Hệ thống có FIPS/PKI/HSM cụ thể | Scheme được policy/phần cứng cho phép | Chứng nhận, key usage, thuật toán, key size và latency triển khai. |

Không chọn theo một bảng xếp hạng “thuật toán mới luôn tốt hơn”; cần đối chiếu **giao thức bắt buộc, khuyến nghị chuẩn, yêu cầu an toàn và khả năng bảo vệ khóa**. RSA-PSS là lựa chọn mạnh cho ứng dụng RSA mới; Ed25519 gọn và tiện cho nhiều ứng dụng hiện đại nhưng không thể thay thế một profile RSA/ECDSA mà bên nhận đã cố định.

**Không tự chuyển sang NONEwithRSA, NONEwithECDSA, băm tay rồi ký raw, hoặc dùng Cipher để giả lập Signature** chỉ vì hai bên đang có lỗi tương thích.

## 16. Bộ kiểm thử cần có

| Ca kiểm thử | Input hoặc tình huống | Kết quả mong đợi |
| --- | --- | --- |
| Đúng profile | Message, signature, public key, params khớp | **verify true**. |
| Message bị sửa | Thay một byte, giữ signature cũ | **verify false**. |
| Chữ ký bị sửa | Thay byte signature hoặc dùng signature của message khác | Từ chối hoặc lỗi format được xử lý an toàn. |
| Public key sai | Dùng key cùng scheme nhưng của issuer khác | Không chấp nhận. |
| PKCS1-v1_5 vs PSS | Ký SHA256withRSA, verify RSASSA-PSS hoặc ngược lại | Không chấp nhận. |
| PSS salt sai | Ký salt=32, verify salt=20 | Từ chối/false hoặc lỗi tham số. |
| PSS MGF sai | Một bên SHA-256 với MGF1-SHA256, bên kia dùng MGF1-SHA1 | Không chấp nhận. |
| ECDSA DER vs P1363 | DER gửi cho verifier expecting raw r\|\|s | Từ chối; cần conversion theo chuẩn. |
| ECDSA malformed | DER r/s không hợp lệ, wrong length | Từ chối với lỗi kiểm soát, không dùng parser lỏng lẻo. |
| Ed25519 deterministic | Ký pure Ed25519 hai lần trên cùng key/message | Cùng signature bytes. |
| Ed25519 variant sai | Message raw vs message đã prehash tùy tiện | Không tương thích. |
| Unicode/JSON | Khác whitespace, charset, normalization | Chữ ký raw bytes cũ không khớp nếu bytes thay đổi. |
| Provider matrix | Ký Java A, verify Go/OpenSSL/Java B với cùng vector | Phải cùng kết quả theo profile chuẩn. |
| Key rotation | Key ID cũ/mới, revoked key | Chấp nhận/từ chối theo version và policy. |
| Replay | Gửi lại message+signature đã hợp lệ | Signature có thể vẫn true; nghiệp vụ phải xử lý idempotent. |
| Concurrency | Nhiều requests song song với keys/messages khác nhau | Không trộn Signature state; throughput được kiểm soát. |
| Key permissions | Key handle không export hoặc bị chặn operation | Xử lý lỗi đúng, không dùng private key không được phép. |

**Test vector có giá trị** nên giữ message bytes gốc dưới dạng hex/file, algorithm, hash, curve/PSS params, public key test, signature bytes đúng encoding và expected result. Có thể tái chạy trên nhiều provider để tìm lỗi interoperability. Không đưa private key thật vào test repo.

### 16.1. Phân biệt lỗi đúng nghĩa

- **verify(false)**: message/key/params/signature không khớp trong phép kiểm tra đã thực hiện.
- **NoSuchAlgorithmException / InvalidKeyException**: thuật toán hoặc khóa không được provider chấp nhận; cần chẩn đoán kỹ thuật, không tự hạ thuật toán.
- **SignatureException**: có thể do trạng thái engine, input/encoding hoặc cài đặt; không suy luận chắc chắn có tấn công chỉ từ exception.
- **Certificate/replay/policy failure**: verify có thể true nhưng hệ thống vẫn phải từ chối vì danh tính hoặc quyền sử dụng không đáp ứng.

Với lỗi chữ ký, không log private key, raw token nhạy cảm, payload bí mật hoặc phản hồi chi tiết cho client khiến lộ trạng thái nội bộ.

## 17. Các sai lầm hay gặp

1. **Nhầm RSASSA-PKCS1-v1_5 với RSAES-PKCS1-v1_5.** Ký bằng Signature API, không dùng Cipher để tự làm RSA signature.
2. **Dùng SHA256withRSA để xác minh PSS.** Hai scheme khác nhau dù cùng RSA key.
3. **Không khai báo đầy đủ PSSParameterSpec.** Hash/MGF/salt/trailer có thể khác provider hoặc bên gửi.
4. **Tự động giảm PSS saltLength khi verify lỗi.** Đây là thay đổi chính sách an toàn ngoài giao thức.
5. **Chọn SHA1withRSA/MD5withRSA cho thiết kế mới.** Không dùng hash yếu khi đã có profile phù hợp hơn.
6. **Tự sinh nonce ECDSA bằng Random hoặc timestamp.** Lỗi nonce có thể làm mất private key.
7. **Cắt DER signature thành hai nửa rồi gọi P1363.** ASN.1 DER không phải raw r||s.
8. **Băm message trước SHA256withECDSA hoặc Ed25519.** Tạo input khác hoặc sai variant, thường dẫn đến mismatch.
9. **Nhầm Ed25519 với X25519.** Signature và key agreement có key type/operation riêng.
10. **Dùng public key tự gửi từ client như danh tính đáng tin.** Verify true chưa chứng minh thuộc đối tác cụ thể.
11. **Tin alg trong JWT vô điều kiện.** Không có allowlist scheme và key policy dẫn đến algorithm confusion.
12. **Không ký metadata nghiệp vụ.** Có thể thay tenant/action/timestamp bên ngoài phạm vi xác thực.
13. **So chữ ký bằng equals với chữ ký vừa tạo.** PSS/ECDSA có thể ngẫu nhiên; Signature.verify mới đúng.
14. **Chia sẻ Signature instance ở singleton.** init/update/verify có thể bị xen lẫn giữa nhiều thread.
15. **Lưu private key trong source, log hoặc file công khai.** Kẻ lấy được khóa có thể tạo chữ ký đúng.
16. **Không xử lý replay/authorization.** Chữ ký hợp lệ không chứng minh lệnh được phép hay chưa xử lý.
17. **Không lưu format version, key ID và params.** Khi đổi provider hoặc rotate khóa dễ không đọc/verify được dữ liệu cũ.

## 18. Tự kiểm tra kiến thức

**Câu 1.** Java **SHA256withRSA** dùng v1.5 hay PSS?  
**Trả lời:** RSASSA-PKCS1-v1_5 với SHA-256. PSS dùng **RSASSA-PSS** và PSSParameterSpec.

**Câu 2.** RSA-PSS SHA-256 ký với saltLength=32; verifier dùng saltLength=20 và đúng RSA public key. Có thể chấp nhận không?  
**Trả lời:** Không. **Tham số scheme khác nhau**; phải từ chối hoặc xử lý lỗi theo provider, không tự hạ tham số.

**Câu 3.** Vì sao hai chữ ký ECDSA trên cùng message có thể khác nhau?  
**Trả lời:** Do nonce ký k; nhiều implementation chọn k mới mỗi lần ký. Cả hai đều có thể hợp lệ và phải dùng Signature.verify.

**Câu 4.** Dịch vụ JWS ES256 đòi 64 bytes raw r||s nhưng Java tạo DER. Cần làm gì?  
**Trả lời:** Chọn **SHA256withECDSAinP1363Format** nếu hỗ trợ hoặc dùng converter DER/P1363 đúng chuẩn, đồng thời giữ cùng curve/hash/bytes.

**Câu 5.** Với pure Ed25519, vì sao không SHA-512(message) trước khi gọi Signature("Ed25519")?  
**Trả lời:** Vì Ed25519 đã có xử lý hash nội bộ. Prehash thủ công sẽ ký một message khác, không tự biến nó thành Ed25519ph.

**Câu 6.** Server nhận message, signature và public key do cùng client gửi, verify true; đã đủ để coi client là tổ chức X chưa?  
**Trả lời:** Chưa. Phải xác thực public key thực sự gắn với tổ chức X theo trust policy.

**Câu 7.** Chữ ký gói thanh toán đúng nhưng bị phát lại từ hôm qua; kiểm tra chữ ký có tự chặn không?  
**Trả lời:** Không. Thêm timestamp/nonce, transaction ID, freshness và idempotency được ràng buộc với thông điệp.

**Câu 8.** RSA-3072 có chữ ký dài khoảng bao nhiêu?  
**Trả lời:** **384 byte** với cả RSA v1.5 và PSS. 3072 là **bit modulus**.

**Câu 9.** Một Signature object có được dùng cho cả request A và B đồng thời không?  
**Trả lời:** Không nên: object có trạng thái init/update/sign/verify. Mỗi tác vụ sở hữu engine riêng.

**Câu 10.** Ed25519 và X25519 khác nhau ở đâu?  
**Trả lời:** Ed25519 là chữ ký số, X25519 là trao đổi khóa; không tự coi chúng là một key type hoặc một giao thức.

## 19. Kết luận và nguồn tham khảo

**PKCS#1 v1.5** giải quyết nhu cầu tương thích RSA signature với profile cổ điển; **RSA-PSS** cần bộ tham số chính xác và được ưu tiên cho ứng dụng RSA mới; **ECDSA** yêu cầu nonce ký an toàn, curve và **DER/P1363** đúng format; **EdDSA** có các profile **Ed25519/Ed448** khác ECDSA và có pure variant deterministic. Tất cả chỉ có giá trị khi **message bytes, algorithm/params, trust của public key, quản lý private key và xử lý replay/authorization** đều đúng.

Nguồn kỹ thuật:

- [RFC 8017 — PKCS #1 v2.2, RSASSA-PKCS1-v1_5 và RSASSA-PSS](https://www.rfc-editor.org/rfc/rfc8017.html)
- [RFC 8032 — EdDSA (Ed25519, Ed448)](https://www.rfc-editor.org/rfc/rfc8032.html)
- [NIST FIPS 186-5 — Digital Signature Standard](https://csrc.nist.gov/pubs/fips/186-5/final)
- [Java SE 17 — Standard Algorithm Names](https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html)
- [Java SE 17 — Signature API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Signature.html)
- [Java SE 17 — PSSParameterSpec](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/spec/PSSParameterSpec.html)
- [Java SE 17 — ECGenParameterSpec](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/spec/ECGenParameterSpec.html)
- [RFC 7518 — JWA, RS256/PS256/ES256](https://www.rfc-editor.org/rfc/rfc7518.html)
- [RFC 8037 — EdDSA in JOSE](https://www.rfc-editor.org/rfc/rfc8037.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="digital-signature-005">9.5 PSSParameterSpec, digest, MGF1 và độ dài salt của chữ ký</a>

<details>
<summary>Xem chi tiết</summary>

# 9.5. PSSParameterSpec, digest, MGF1 và độ dài salt của chữ ký

## Mục tiêu học tập

Trong Java Cryptography Architecture (JCA), `RSASSA-PSS` là cơ chế **chữ ký số RSA** dùng phép đệm xác suất (Probabilistic Signature Scheme) được chuẩn hóa trong PKCS #1 (RFC 8017). Để hai bên tạo và kiểm tra chữ ký tương thích, chỉ biết “RSA” hoặc “SHA-256” **chưa đủ**: phải thống nhất cả **digest của thông điệp, hàm MGF, digest bên trong MGF1, số byte salt và trailer field**.

Sau bài này bạn có thể:

- Đọc từng trường của `java.security.spec.PSSParameterSpec` và phân biệt **message digest** với **MGF1 digest**.
- Tạo hồ sơ **RSA-PSS SHA-256 / MGF1-SHA256 / salt 32 byte / trailer 1** trên Java 17+.
- Dùng `Signature.getInstance("RSASSA-PSS")`, `setParameter(...)`, `initSign/initVerify`, `update` và `sign/verify` cho một thông điệp byte chính xác.
- Tính giới hạn độ dài salt theo `modulusBits` của RSA và độ dài digest; biết vì sao thay salt length có thể làm xác minh thất bại.
- Chẩn đoán sai khác giữa Java và OpenSSL/JWS/X.509 hoặc giữa các provider; phân biệt RSA-PSS với RSA PKCS#1 v1.5.

Ví dụ dùng khóa RSA 2048 bit tạo **ngay trong tiến trình chỉ để học API**. Trong ứng dụng thực tế, khóa private phải được quản lý an toàn (KeyStore/KMS/HSM theo kiến trúc), nguồn dữ liệu được ký phải có định dạng/canonicalization nhất quán và phải xác minh public key thực sự đáng tin.

## 1. RSA-PSS hoạt động khác chữ ký RSA PKCS#1 v1.5 như thế nào?

Cả hai đều dùng RSA private key để ký và RSA public key để kiểm tra. Sự khác nhau nằm ở **cách encode thông điệp trước phép toán RSA**:

| Thành phần | RSA PKCS#1 v1.5 (thường `SHA256withRSA` trong JCA) | RSASSA-PSS (`RSASSA-PSS` trong JCA) |
| --- | --- | --- |
| Padding/encoding chữ ký | EMSA-PKCS1-v1_5 | EMSA-PSS |
| Tính ngẫu nhiên | Với message/key cố định, chữ ký theo scheme cơ bản là xác định | Thường dùng **salt ngẫu nhiên mới mỗi lần ký** |
| Tham số cần thống nhất | Hash và scheme, cộng quy tắc về key | Hash, MGF, MGF digest, salt length, trailer field và key |
| Khả năng tương thích | Không thể kiểm tra chữ ký PSS bằng verifier v1.5 | Không thể kiểm tra chữ ký v1.5 bằng verifier PSS |
| Ví dụ JCA | `Signature.getInstance("SHA256withRSA")` | `Signature.getInstance("RSASSA-PSS")` rồi `setParameter(PSSParameterSpec)` |

Tên `SHA256withRSA` **không** đồng nghĩa `RSA-PSS với SHA-256`. Bên ký chọn PSS thì bên xác minh cũng phải chọn PSS với cấu hình khớp. Tên như `SHA256withRSAandMGF1` có thể xuất hiện ở provider/thư viện riêng, nhưng không nên dựa vào alias đó cho code JCA độc lập provider; chọn `RSASSA-PSS` và khai báo `PSSParameterSpec` rõ ràng.

**Chữ ký số không phải mã hóa dữ liệu:** `Signature.sign()` tạo chữ ký cho input đã cung cấp; nó **không** giữ bí mật thông điệp. Verify thành công chỉ xác nhận chữ ký phù hợp với public key và message, còn việc public key có thuộc đúng chủ thể/tổ chức hay không là bước quản lý tin cậy khác (chứng chỉ, trust store, pinning, chính sách định danh...).

## 2. Năm trường của `PSSParameterSpec`

Constructor đầy đủ:

```java
PSSParameterSpec pss = new PSSParameterSpec(
        "SHA-256",                   // message digest
        "MGF1",                      // mask generation function
        MGF1ParameterSpec.SHA256,    // digest dùng bên trong MGF1
        32,                          // salt length tính bằng BYTE
        PSSParameterSpec.TRAILER_FIELD_BC
);
```

Đây là snippet; cần import `java.security.spec.PSSParameterSpec` và `java.security.spec.MGF1ParameterSpec`.

| Trường | API getter | Ý nghĩa | Ví dụ SHA-256 PSS |
| --- | --- | --- | --- |
| Message digest | `getDigestAlgorithm()` | Hàm hash dùng để băm **message** và trong encoding EMSA-PSS | `"SHA-256"` |
| MGF algorithm | `getMGFAlgorithm()` | Hàm tạo mask, thường là **MGF1** | `"MGF1"` |
| Tham số MGF | `getMGFParameters()` | Với MGF1: một `MGF1ParameterSpec` chỉ ra digest dùng trong MGF1 | `MGF1ParameterSpec.SHA256` |
| Salt length | `getSaltLength()` | Số **byte** ngẫu nhiên dùng cho mỗi lần PSS encoding khi ký | `32` byte |
| Trailer field | `getTrailerField()` | Mã nhận diện trailer theo PKCS#1; giá trị `1` ứng với trailer octet `0xBC` | `PSSParameterSpec.TRAILER_FIELD_BC` (giá trị 1) |

**Ba nhầm lẫn cần tránh:**

1. `"SHA-256"` và `MGF1ParameterSpec.SHA256` mô tả **hai vai trò hash** khác nhau. Mặc dù nên khớp theo nhiều profile hiện đại, thiết kế PSS về mặt khái niệm không đồng nhất hai trường; verifier cần đọc chính xác **cả hai**.
2. `saltLength=32` có nghĩa **32 byte = 256 bit**, không phải 32 bit. Nó cũng **không phải** độ dài public/private RSA key và không quyết định kích thước chữ ký RSA.
3. `TRAILER_FIELD_BC` là giá trị constructor **1**; đừng truyền số `0xBC` (188) thay cho tham số trailer. Giá trị 1 biểu thị byte đệm cuối `0xBC` theo định nghĩa scheme.

### Cảnh báo về giá trị mặc định

`PSSParameterSpec.DEFAULT` là profile **lịch sử**: message digest `SHA-1`, `MGF1` với `SHA-1`, salt dài **20 byte**, trailer `1`. Constructor ngắn `new PSSParameterSpec(int saltLen)` cũng dựa trên các mặc định SHA-1/MGF1-SHA1, chỉ thay độ dài salt.

Vì vậy, **`new PSSParameterSpec(32)` không tự chuyển thành SHA-256-PSS**. Trong hệ thống mới, hãy tạo constructor năm tham số dựa trên profile bảo mật đã chọn, không phụ thuộc vào `DEFAULT` hoặc việc provider tự chọn giá trị. SHA-1 trong defaults chỉ giúp đọc legacy/API, không phải một đề xuất cho chữ ký mới.

### `MGF1ParameterSpec` không phải chính `MGF1`

`MGF1ParameterSpec.SHA256` là một đối tượng chứa **tên hash dùng để tạo mask**, không tự ký/hashing dữ liệu. `MGF1` (Mask Generation Function 1) sinh mask có độ dài cần thiết từ seed bằng các lần gọi hàm hash với counter. Việc mã hóa PSS dùng mask đó để che phần dữ liệu encoded; digest của MGF1 **không thay thế** message digest mà signer dùng trên message ban đầu.

## 3. Đi sâu vừa đủ vào cơ chế EMSA-PSS

RFC 8017 mô tả encoding trước phép toán RSA với `emBits = modBits - 1` và `emLen = ceil(emBits / 8)`. Với message `M`:

```text
M (message bytes)
   |
   v
mHash = Hash(M)                  Hash = message digest (ví dụ SHA-256)
   |
   +----------- random salt (sLen byte)
   |                 |
   v                 v
M' = 0x00 (8 byte) || mHash || salt
   |
   v
H = Hash(M')                    Cùng message digest của profile PSS
   |
   v
dbMask = MGF1(H, targetLength)  MGF1 dùng digest riêng từ MGF1ParameterSpec
   |
   v
maskedDB = DB XOR dbMask
   |
   v
EM = maskedDB || H || 0xBC      encoded message trước RSA operation
```

Ở bước này `DB` có dạng `PS || 0x01 || salt`, với `PS` là chuỗi byte zero đệm đủ độ dài; một số bit cao của `maskedDB` được điều chỉnh theo `emBits`. Phép toán RSA sau đó biến `EM` thành signature bytes. Bên verify dùng public key lấy lại encoding, tái tạo/kiểm tra mask, trích salt và đối chiếu hash.

Ý nghĩa của từng thành phần:

- **`mHash`:** hash của **chính xác bytes mà signer nhận qua `Signature.update(...)`**.
- **`salt`:** một chuỗi byte được sinh cho mỗi lần PSS ký, không phải salt dùng cho password hashing; có thể làm hai chữ ký của cùng message/key **khác bytes**. Salt được mã hóa trong phần EMSA-PSS mà verifier kiểm tra; không cần truyền salt ở một trường riêng nếu protocol dùng RSASSA-PSS thông thường.
- **`MGF1`:** sinh mask từ `H`; mask không phải key hay IV của một thuật toán mã hóa.
- **`trailer 0xBC`:** marker cuối của encoding; getter/setter Java dùng mã `1`.
- **`RSA key`:** độ dài modulus ràng buộc tổng không gian cho digest, salt và các byte overhead.

Không tự viết lại EMSA-PSS/MGF1/RSA primitive trong ứng dụng. Dùng Java `Signature` và chỉ hiểu cấu trúc để chọn tham số, điều tra lỗi, cấu hình định dạng chữ ký.

## 4. Vì sao signer và verifier phải khớp toàn bộ profile?

Một bên ký với:

```text
algorithm=RSASSA-PSS
messageDigest=SHA-256
MGF=MGF1
mgfDigest=SHA-256
saltLength=32 bytes
trailerField=1
```

Bên xác minh có thể có đúng public key và message nhưng vẫn **không verify** nếu kỳ vọng saltLength=20, MGF1 dùng SHA-1, message digest=SHA-384 hoặc scheme thành `SHA256withRSA`. Với key/type/params hợp lệ và signature không phù hợp, `Signature.verify` thường trả `false`; nếu thuật toán, provider hoặc tham số không được hỗ trợ, có thể ném exception trước hoặc trong verify. Đừng nuốt mọi lỗi thành một lý do duy nhất.

**Khuyến nghị thực hành:** định nghĩa profile tại một nơi có tên rõ ràng (ví dụ `PSS_SHA256_MGF1_SHA256_SALT32`), dùng cùng profile cho signer và verifier, và có test giao tiếp qua hai tiến trình/thư viện. Trong môi trường phải tương thích chuẩn bên ngoài, profile **phải theo chuẩn đó**, không tự chọn số salt tùy ý.

Cách kiểm tra profile trong ứng dụng Java:

```java
AlgorithmParameters params = signer.getParameters();
if (params != null) {
    PSSParameterSpec actual =
            params.getParameterSpec(PSSParameterSpec.class);
    System.out.println(actual.getDigestAlgorithm());
    System.out.println(actual.getSaltLength());
}
```

Snippet dùng `java.security.AlgorithmParameters` và giả định `signer` là `Signature` đã `setParameter`. `Signature.getParameters()` có thể trả `null` nếu implementation không hỗ trợ xuất đối tượng parameters theo API. Vì vậy nếu cần kiểm chứng tuyệt đối cấu hình protocol, giữ bản `PSSParameterSpec` mà code đã truyền vào, dùng test xác minh thực tế và không suy diễn từ `null` rằng provider đã dùng defaults gì.

## 5. Java 17: ký và xác minh RSASSA-PSS với tham số rõ ràng

Lưu toàn bộ mã sau vào **`RsaPssDemo.java`**. Chương trình tạo một key pair RSA 2048 bit (mục đích demo), ký chuỗi `hello PSS` bằng **hai lần `update`**, sau đó xác minh đủ bốn trường hợp: đúng profile, message bị sửa, salt length khác và MGF digest khác.

```java
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;

public final class RsaPssDemo {
    private RsaPssDemo() {}

    private static PSSParameterSpec profile(int saltBytes,
            MGF1ParameterSpec mgfDigest) {
        return new PSSParameterSpec("SHA-256", "MGF1", mgfDigest,
                saltBytes, PSSParameterSpec.TRAILER_FIELD_BC);
    }

    private static boolean verify(PublicKey key, PSSParameterSpec spec,
            byte[] message, byte[] signature) throws GeneralSecurityException {
        Signature verifier = Signature.getInstance("RSASSA-PSS");
        verifier.initVerify(key);
        verifier.setParameter(spec);
        verifier.update(message);
        return verifier.verify(signature);
    }

    public static void main(String[] args) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();

        PSSParameterSpec spec = profile(32, MGF1ParameterSpec.SHA256);
        byte[] first = "hello ".getBytes(StandardCharsets.UTF_8);
        byte[] second = "PSS".getBytes(StandardCharsets.UTF_8);
        byte[] whole = "hello PSS".getBytes(StandardCharsets.UTF_8);
        byte[] modified = "hello Pss".getBytes(StandardCharsets.UTF_8);

        Signature signer = Signature.getInstance("RSASSA-PSS");
        signer.initSign(pair.getPrivate());
        signer.setParameter(spec);
        signer.update(first);
        signer.update(second);
        byte[] signature = signer.sign();

        PSSParameterSpec actual = signer.getParameters()
                .getParameterSpec(PSSParameterSpec.class);

        System.out.println("modulusBits=" +
                ((RSAPublicKey) pair.getPublic()).getModulus().bitLength());
        System.out.println("signatureBytes=" + signature.length);
        System.out.println("digest=" + actual.getDigestAlgorithm());
        System.out.println("mgf=" + actual.getMGFAlgorithm());
        System.out.println("mgfDigest=" +
                ((MGF1ParameterSpec) actual.getMGFParameters())
                        .getDigestAlgorithm());
        System.out.println("saltBytes=" + actual.getSaltLength());
        System.out.println("verified=" + verify(pair.getPublic(), spec,
                whole, signature));
        System.out.println("modifiedMessageAccepted=" +
                verify(pair.getPublic(), spec, modified, signature));
        System.out.println("wrongSaltAccepted=" +
                verify(pair.getPublic(),
                        profile(20, MGF1ParameterSpec.SHA256),
                        whole, signature));
        System.out.println("wrongMgfAccepted=" +
                verify(pair.getPublic(),
                        profile(32, MGF1ParameterSpec.SHA1),
                        whole, signature));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 RsaPssDemo.java
java RsaPssDemo
```

**Stdout trên JDK 24/OpenJDK provider đã kiểm tra** (các trường hợp verify sai tương ứng có thể bị provider khác từ chối bằng exception khi xử lý tham số thay vì trả `false`):

```text
modulusBits=2048
signatureBytes=256
digest=SHA-256
mgf=MGF1
mgfDigest=SHA-256
saltBytes=32
verified=true
modifiedMessageAccepted=false
wrongSaltAccepted=false
wrongMgfAccepted=false
```

Điểm học tập:

1. `KeyPairGenerator.getInstance("RSA")` và `initialize(2048)` tạo khóa đủ cho ví dụ, và `Signature.getInstance("RSASSA-PSS")` yêu cầu scheme PSS. Bảo mật thực tế còn phụ thuộc kích thước khóa theo chính sách, nguồn ngẫu nhiên, quyền truy cập private key và vòng đời key.
2. `signer.initSign(privateKey)` chọn private key; `setParameter(spec)` thiết lập digest/MGF/salt/trailer. `update(first)` rồi `update(second)` nạp đúng bytes `hello PSS`, không tự chèn dấu phân cách.
3. `signer.sign()` hoàn tất phép ký, sinh salt ngẫu nhiên theo profile; chữ ký có **256 byte** vì modulus RSA là **2048 bit**, **không** phải vì SHA-256 hoặc sLen=32.
4. `verifier.initVerify(publicKey)` + `verifier.setParameter(spec)` + `update(whole)` + `verify(signature)` trả true khi message/key/profile khớp. Hai lần `update` của signer và một lần của verifier tương đương vì **chuỗi bytes** giống nhau.
5. `modifiedMessageAccepted=false` cho thấy sửa `PSS` thành `Pss` làm thông điệp thay đổi. Đây là hiệu ứng trên bytes sau UTF-8, không có “so sánh chữ tương đương” tự động.
6. `wrongSaltAccepted=false`: verifier kỳ vọng **20 byte salt** nhưng signer đã ký với **32 byte**.
7. `wrongMgfAccepted=false`: verifier kỳ vọng MGF1-SHA1 thay vì MGF1-SHA256. Mặc dù message digest vẫn SHA-256, **MGF digest khác** khiến không khớp.
8. `signer.getParameters()` cho biết cấu hình thực tế trên provider của demo. Ví dụ assume provider hỗ trợ trả về `AlgorithmParameters`; nếu gặp `null` trên provider khác, tự lưu spec đã dùng và chẩn đoán theo contract của provider.

**Không được** lưu private key trong source, không in private key để debug, không chấp nhận public key tùy ý do người gửi dữ liệu không đáng tin cung cấp rồi tuyên bố người gửi “hợp lệ”. Demo tạo cả hai key trong cùng tiến trình chỉ nhằm chứng minh luồng API.

## 6. Digest của message và digest của MGF1 khác nhau ở đâu?

`PSSParameterSpec.getDigestAlgorithm()` cho biết hàm hash tính `mHash = Hash(M)` và `H = Hash(0x00... || mHash || salt)`. `MGF1ParameterSpec.getDigestAlgorithm()` cho biết hash dùng **bên trong MGF1** để tạo mask từ `H`.

| Profile | Message digest | MGF | MGF digest | Salt bytes | Ý nghĩa |
| --- | --- | --- | --- | ---: | --- |
| SHA-256 PSS thường dùng | SHA-256 | MGF1 | SHA-256 | 32 | Tương thích profile PS256 trong JOSE khi các quy tắc khác phù hợp. |
| SHA-384 PSS thường dùng | SHA-384 | MGF1 | SHA-384 | 48 | Profile PS384. |
| SHA-512 PSS thường dùng | SHA-512 | MGF1 | SHA-512 | 64 | Profile PS512. |
| SHA-256 + MGF1-SHA1 | SHA-256 | MGF1 | SHA-1 | 32 | Về mặt cấu trúc PSS có thể mô tả; không phải profile PS256 và khả năng hỗ trợ phụ thuộc provider. |
| `PSSParameterSpec.DEFAULT` | SHA-1 | MGF1 | SHA-1 | 20 | Legacy defaults, không nên ngầm dùng cho chữ ký mới. |

Trong nhiều protocol hiện đại, message digest và MGF digest **phải là cùng loại**; ví dụ RFC 7518 định nghĩa **JOSE PS256** là SHA-256 cho cả hai, saltLength=32, trailer theo mặc định PSS, và key RSA ít nhất 2048 bit. Tương tự PS384 dùng 48 byte salt, PS512 dùng 64 byte salt.

**Không có quy tắc “cứ SHA-256 thì Java tự chọn salt 32 byte” khi bạn chỉ gọi `getInstance("RSASSA-PSS")`.** Độ dài salt là **tham số độc lập**, và yêu cầu protocol cần được khai báo đầy đủ. Nếu phải verify một chữ ký có salt dài 20 dù dùng SHA-256, chỉ dùng profile đó khi đúng giao thức/hệ thống legacy và đã xem xét chính sách chấp nhận.

### Có nên tự gọi `MessageDigest` trước khi `Signature.update`?

**Không** khi dùng `Signature.getInstance("RSASSA-PSS")` với `PSSParameterSpec("SHA-256",...)`. `Signature.update(message)` nhận **message bytes**; engine tự thực hiện hash theo profile. Nếu bạn:

```java
byte[] prehashed = MessageDigest.getInstance("SHA-256").digest(message);
signer.update(prehashed);
```

thì signer PSS sẽ hash **32 byte prehashed** như một thông điệp mới; chữ ký đó **không** là chữ ký trực tiếp trên original `message` theo giao thức thông thường. Code trên chỉ là snippet minh họa lỗi (giả định `message` là `byte[]`, `signer` đã init, đã khai báo/catch exception); không dùng trong luồng chuẩn. Đừng “tối ưu” bằng cách prehash vì engine PSS đã bao gồm hash. Một API prehashed chuyên biệt, nếu tồn tại ở provider/protocol, phải tuân thủ contract riêng và không được nhầm với `Signature` RSASSA-PSS thông thường.

## 7. Salt length bị giới hạn bởi digest và RSA modulus

RFC 8017, EMSA-PSS encoding quy định:

```text
modBits = số bit của RSA modulus n
emBits  = modBits - 1
emLen   = ceil(emBits / 8)       // byte của encoded message
hLen    = số byte output của message digest
sLen    = số byte của salt

Yêu cầu cơ bản: emLen >= hLen + sLen + 2
Hay: sLen <= emLen - hLen - 2
```

Ví dụ **RSA modulus 2048 bit**:

- `emBits=2047`, `emLen=256 byte`.
- SHA-256: `hLen=32`, nên **sLen tối đa = 256 - 32 - 2 = 222 byte** về mặt kích thước encoding.
- SHA-512: `hLen=64`, sLen tối đa **190 byte**.
- Khi sLen=32 với SHA-256, còn nhiều khoảng trống padding, và profile **hoàn toàn vừa** vào RSA-2048.

**Salt length tối đa không có nghĩa đó là lựa chọn cần dùng.** Mỗi protocol có thể yêu cầu salt bằng digest length (ví dụ PS256 sLen=32); tăng lên 222 byte không tự làm chữ ký “mạnh hơn 6,9 lần” và có thể phá tương thích. Một số provider hoặc key RSA-PSS có ràng buộc riêng nghiêm hơn giới hạn toán học; luôn kiểm tra trên implementation và protocol thực tế.

Bảng tính nhanh (khóa RSA có modulus đúng số bit ghi):

| RSA modulus | Message digest | hLen | emLen | sLen tối đa theo công thức |
| ---: | --- | ---: | ---: | ---: |
| 1024 bit | SHA-256 | 32 byte | 128 byte | 94 byte |
| 2048 bit | SHA-256 | 32 byte | 256 byte | 222 byte |
| 2048 bit | SHA-512 | 64 byte | 256 byte | 190 byte |
| 3072 bit | SHA-256 | 32 byte | 384 byte | 350 byte |
| 4096 bit | SHA-512 | 64 byte | 512 byte | 446 byte |

Dòng RSA-1024 chỉ để làm **bài toán kích thước**, **không** là khuyến nghị sử dụng RSA-1024 cho chữ ký mới. Dùng RSA key size theo tiêu chuẩn và chính sách đang áp dụng.

### Java tính budget thay vì đoán

Lưu **`PssSaltBudgetDemo.java`**:

```java
import java.security.MessageDigest;

public final class PssSaltBudgetDemo {
    private PssSaltBudgetDemo() {}

    private static int maxSaltBytes(int modulusBits, int hashBytes) {
        if (modulusBits < 2 || hashBytes < 0) {
            throw new IllegalArgumentException("Invalid dimensions");
        }
        int emBits = modulusBits - 1;
        int emLen = (emBits + 7) / 8;
        return emLen - hashBytes - 2;
    }

    public static void main(String[] args) throws Exception {
        int sha256Bytes = MessageDigest.getInstance("SHA-256")
                .getDigestLength();
        int sha512Bytes = MessageDigest.getInstance("SHA-512")
                .getDigestLength();

        System.out.println("SHA256Bytes=" + sha256Bytes);
        System.out.println("RSA2048_SHA256_MaxSalt=" +
                maxSaltBytes(2048, sha256Bytes));
        System.out.println("RSA2048_SHA512_MaxSalt=" +
                maxSaltBytes(2048, sha512Bytes));
        System.out.println("RSA3072_SHA256_MaxSalt=" +
                maxSaltBytes(3072, sha256Bytes));
        System.out.println("salt32FitsRSA2048_SHA256=" +
                (32 <= maxSaltBytes(2048, sha256Bytes)));
        System.out.println("salt223FitsRSA2048_SHA256=" +
                (223 <= maxSaltBytes(2048, sha256Bytes)));
    }
}
```

```bash
javac -encoding UTF-8 PssSaltBudgetDemo.java
java PssSaltBudgetDemo
```

**Stdout:**

```text
SHA256Bytes=32
RSA2048_SHA256_MaxSalt=222
RSA2048_SHA512_MaxSalt=190
RSA3072_SHA256_MaxSalt=350
salt32FitsRSA2048_SHA256=true
salt223FitsRSA2048_SHA256=false
```

Chương trình chỉ thực hiện **phép tính giới hạn encoding từ RFC**, không thử hỗ trợ của provider, không chứng minh algorithm policy nào được chấp nhận và không tạo chữ ký. Với key có sẵn, lấy bit length thực từ `((RSAPublicKey) publicKey).getModulus().bitLength()`, không giả định key luôn có đúng modulus size như mô tả trong cấu hình.

### Điều gì xảy ra khi salt vượt giới hạn?

Với RSA-2048/SHA-256, yêu cầu sLen=223 làm `hLen+sLen+2=257 > emLen=256`. PSS không thể encode thông điệp theo tham số đó; JCA/provider có thể từ chối tại `setParameter`, `initSign` hoặc `sign` bằng exception phù hợp. Đừng giả định duy nhất một loại exception ở mọi provider. Cần **validate sLen** trước khi thử ký, chọn key đủ lớn và profile đáp ứng protocol.

`sLen=0` được chuẩn định dạng PSS cho phép về mặt encoding; khi đó salt rỗng và tính ngẫu nhiên của chính scheme không còn từ salt. Đây không phải mặc định profile PS256/PS384/PS512; chỉ dùng khi một giao thức/chính sách xác định cụ thể, không tùy tiện đặt 0 để được chữ ký có thể so sánh bytes.

## 8. Sử dụng đúng `Signature`: thứ tự gọi và byte đầu vào

Vòng đời một thao tác ký:

```text
Signature.getInstance("RSASSA-PSS")
   |
initSign(privateKey, [SecureRandom])
   |
setParameter(profile phù hợp)
   |
update(messageByteChunk1)
update(messageByteChunk2)
   |
sign() -> signatureBytes
```

Vòng đời xác minh:

```text
Signature.getInstance("RSASSA-PSS")
   |
initVerify(trustedPublicKey)
   |
setParameter(CÙNG profile)
   |
update(CÙNG message bytes theo thứ tự)
   |
verify(signatureBytes) -> true/false hoặc exception
```

Tùy provider, `setParameter` có thể được phép gọi trước `initSign/initVerify` hoặc chỉ sau khi engine được khởi tạo với key; ví dụ bài dùng thứ tự **init trước, setParameter sau** đã chạy trên provider kiểm thử. Với RSA-PSS key có ràng buộc thuật toán sẵn, engine có thể từ chối profile trái ràng buộc của key.

**Một số lưu ý cần nhớ:**

- `Signature.update(...)` **không** tạo chữ ký từng chunk. Nó chỉ tiếp nhận bytes vào thông điệp đang ký/xác minh. Chia `"hello PSS"` thành hai lần update hay một lần update cho cùng bytes thì kết quả xác minh vẫn đúng.
- `Signature.sign()` trả signature bytes có độ dài theo RSA modulus (RSA-2048 là 256 byte), không bằng SHA-256 digest 32 byte hoặc salt 32 byte.
- Mỗi lần ký PSS thường sinh salt mới; **cùng message và key có thể cho chữ ký bytes khác nhau**. Đừng viết test yêu cầu hai signature PSS phải giống byte. Hãy kiểm tra `verify(...) == true` và xử lý sự khác biệt dự kiến của randomness.
- `initSign(privateKey, SecureRandom random)` có overload cho phép cấp RNG; cần CSPRNG phù hợp, tránh seed bằng hằng số hoặc lộ RNG state. Trong code tiêu chuẩn, provider có cơ chế sinh ngẫu nhiên riêng; không tự tạo và đưa salt như tham số ngoài.
- `Signature` có trạng thái mutable và không nên chia sẻ cùng instance giữa nhiều thread/request; mỗi operation dùng instance riêng hoặc quản lý chặt chẽ.
- Khi chuyển `String` thành bytes, chỉ định `StandardCharsets.UTF_8`. Dữ liệu JSON/XML cần protocol đảm bảo **chuỗi bytes đã chuẩn hóa** giống nhau; ký JSON nguyên gốc rồi verify JSON được sắp xếp/serialize lại có thể làm verify false dù “nội dung logic” tương đương.
- Khi xác minh, public key phải được chọn theo **nguồn tin cậy**; không tin tên thuật toán hoặc đường dẫn key do input không tin cậy yêu cầu. Kiểm tra metadata key/algorithm và chính sách chấp nhận từ cấu hình/định danh đáng tin.

## 9. Liên thông chuẩn JOSE/JWT, X.509 và thư viện khác

### JWS/JWT: PS256 không phải RS256

RFC 7518 `3.5 quy định:

| JWS `alg` | Scheme | Message digest | MGF1 digest | Salt length |
| --- | --- | --- | --- | ---: |
| `PS256` | RSASSA-PSS | SHA-256 | SHA-256 | 32 byte |
| `PS384` | RSASSA-PSS | SHA-384 | SHA-384 | 48 byte |
| `PS512` | RSASSA-PSS | SHA-512 | SHA-512 | 64 byte |
| `RS256` | RSASSA-PKCS1-v1_5 | SHA-256 | Không dùng MGF1 | Không có PSS salt |

Trong JWS, input thực tế được ký là **JWS Signing Input** theo chuẩn (chẳng hạn chuỗi ASCII `BASE64URL(UTF8(protectedHeader)) || "." || BASE64URL(payload)` với các điều kiện `b64` extension tương ứng), không phải tùy tiện đưa JSON đã parse/serialize lại vào `Signature.update`. Nếu áp dụng `PS256`, `PSSParameterSpec` của mục 2 đúng về digest/MGF/salt/trailer, nhưng ứng dụng vẫn phải thực hiện **toàn bộ các quy tắc JWS**, không chỉ gọi raw `Signature` lên phần payload.

**Không để header `alg` chưa tin cậy tự mở quyền chọn scheme/key.** Verifier cần có danh sách thuật toán được phép theo ứng dụng và key cụ thể, không tự động cho phép mọi algorithm do token yêu cầu. Library JWT/JWS có chức năng verify phù hợp sẽ giúp kiểm tra format, key và chính sách; `Signature` trực tiếp thích hợp khi xây giao thức riêng có spec rõ ràng.

### X.509 và thông số được mã hóa

Chữ ký RSASSA-PSS có thể có **algorithm identifier và tham số ASN.1** đi cùng chứng chỉ/cấu trúc giao thức. Java `AlgorithmParameters` có thể xuất thông số PSS từ provider, nhưng những raw `byte[] signature` do `Signature.sign()` trả **không tự chứa mọi trường profile được khai báo trong một header tự mô tả**. Do đó khi truyền chữ ký “thô” cùng file/message, bạn phải định nghĩa/biết trước thuật toán, digest/MGF/saltLen/trailer và khóa tương ứng.

Nếu dùng public key cấu hình cho riêng RSASSA-PSS hoặc certificate có ràng buộc tham số, không được tự đổi digest/salt tùy ý; dùng khóa và thuật toán theo metadata/chính sách được xác minh của chứng chỉ. Không nên tự viết ASN.1 encoder/decoder khi thư viện chuẩn đã có.

### Đối chiếu với OpenSSL

Một bên dùng OpenSSL có thể chọn `rsa_padding_mode:pss`, `rsa_mgf1_md:sha256` và `rsa_pss_saltlen:32` cho RSA-PSS SHA-256. Nếu OpenSSL đang dùng chế độ tự suy salt length (`auto`), max salt hoặc khác chính sách, kết quả có thể không tương thích với verifier yêu cầu đúng saltLength=32. Khi debug, ghi rõ **năm trường profile và độ dài key**, rồi thử verify với test vector đã biết.

Không quy mọi lỗi `verify=false` thành lỗi encoding text. Kiểm tra **cả** input bytes, key, padding mode, digest chính, MGF1 digest, salt length và trailer.

## 10. Checklist chẩn đoán khi chữ ký không xác minh được

| Hiện tượng | Khả năng nguyên nhân | Cách kiểm tra |
| --- | --- | --- |
| Java ký được, OpenSSL/JWS verify false | MGF1 digest hoặc salt length khác | Ghi rõ SHA message/MGF, sLen và protocol profile; đối chiếu ký và verify. |
| `verify=false` dù digest chính cùng SHA-256 | MGF1-SHA1 ở một phía, hoặc sLen 20 vs 32 | Đọc `PSSParameterSpec` và đặt rõ từng trường ở cả hai bên. |
| RSA key đúng nhưng `SHA256withRSA` verify false | PKCS#1 v1.5 khác scheme PSS | Chọn `RSASSA-PSS` và đầy đủ params. |
| `InvalidAlgorithmParameterException` | Provider không chấp nhận profile | Kiểm tra MGF, digest, key constraints và provider. |
| `InvalidKeyException` hoặc `SignatureException` khi ký | RSA key không phù hợp, salt quá dài hoặc key restrictions | Xem modulusBits, emLen/hLen/sLen và thông báo lỗi. |
| `verify=false` chỉ với message có Unicode | Byte/charset/canonicalization khác | So byte input thực trước `Signature.update`; thống nhất UTF-8 và canonicalization. |
| Ký cùng message hai lần cho signature bytes khác | PSS sinh salt ngẫu nhiên | Verify từng chữ ký thay vì so byte signature. |
| Dùng `PSSParameterSpec.DEFAULT` rồi bên ngoài yêu cầu PS256 | DEFAULT dùng SHA-1/MGF1-SHA1/sLen20 | Dùng constructor năm tham số SHA-256/MGF1-SHA256/sLen32/trailer1. |
| Salt length đặt bằng 256 vì “SHA-256 là 256” | Nhầm bit và byte | SHA-256 output 256 bit = 32 byte; sLen=32 cho PS256. |
| Salt length quá lớn với RSA key nhỏ | Không đủ encoding space | Tính `emLen >= hLen+sLen+2`; đổi profile/key theo chuẩn. |
| Lấy `Signature.update(digestBytes)` thay vì message | Engine PSS hash digest như message mới | Đưa original signed-input bytes vào Signature. |
| API trả `getParameters()==null` | Provider không expose AlgorithmParameters | Lưu/kiểm tra spec của ứng dụng và thử interoperability trên provider thật. |
| Verify “đúng” với public key do attacker cung cấp | Chưa xác minh danh tính/key origin | Dùng trust store/certificate chain/chính sách khóa đã được tin cậy. |

**Độ nhạy về lỗi:** khi xử lý chữ ký từ dữ liệu bên ngoài, xử lý input không hợp lệ có kiểm soát. Đừng coi `verify=false` và exception là hai mức “gần đúng” của chữ ký; cả hai đều **không được chấp nhận** nếu chưa được xác minh đầy đủ.

## 11. Bài tập thực hành

1. **Đọc profile:** giải thích từng getter `getDigestAlgorithm`, `getMGFAlgorithm`, `getMGFParameters`, `getSaltLength` và `getTrailerField`. Vì sao `MGF1ParameterSpec.SHA256` không thay thế trường message digest?
2. **Chạy ví dụ:** biên dịch `RsaPssDemo.java`, giải thích vì sao signature có 256 byte trong khi digest và salt đều dài 32 byte.
3. **Thay message:** đổi một chữ cái của `hello PSS` phía verify rồi mô tả kết quả. Giữ nguyên key/profile.
4. **Thay MGF digest:** giữ SHA-256 ở `PSSParameterSpec.getDigestAlgorithm` nhưng đổi MGF1-SHA1 ở verifier; theo RFC và test, vì sao điều này làm chữ ký không khớp? Nếu provider từ chối param khác, đó là lỗi ở bước nào?
5. **Thay sLen:** ký bằng sLen32, verify bằng sLen20. Sau đó ký mới **với cùng sLen20 ở cả hai bên** và giải thích vì sao có thể verify true nhưng không còn là profile JOSE PS256.
6. **Giới hạn encoding:** với RSA-2048/SHA-256, tính emBits, emLen và maxSalt; thử sLen=223 với signer và ghi nhận exception trên provider đang chạy. Đừng dùng tham số vô nghĩa này trong production.
7. **Đổi message digest:** với RSA-2048/SHA-512, tính hLen=64 và maxSalt=190; đề xuất `PSSParameterSpec` tương ứng PS512 (SHA-512, MGF1-SHA512, salt64, trailer1).
8. **Tách chunk:** signer gọi `update("hello ")` và `update("PSS")`, verifier gọi `update("hello PSS")`. Vì sao vẫn verify true? Trường hợp `hello` vs `hello ` khác nhau thế nào?
9. **JWS:** mô tả đủ PS256 và RS256 khác nhau ở đâu, bao gồm message input bytes theo JWS, scheme, MGF và salt length.
10. **Thiết kế API:** phác thảo hàm `verify(byte[] message, byte[] signature, PublicKey trustedKey, PSSParameterSpec approvedProfile)` và nêu vì sao profile phải đến từ chính sách tin cậy, thay vì message chưa xác minh.
11. **Test interoperability:** tạo một test vector qua hai provider/ứng dụng. Ghi metadata algorithm, modulus bit length, hash, mgf, mgf digest, salt length, trailer và chính xác bytes được ký; phân loại từng lỗi `verify=false`/exception.

**Tiêu chí đạt:** bạn tạo được profile PSS rõ ràng, thực hiện `sign/verify` với đúng bytes, phân biệt message digest với MGF1 digest, chọn salt length theo protocol và kiểm tra giới hạn RSA, đồng thời nêu được ít nhất ba nguyên nhân khiến chữ ký PSS không liên thông dù dùng đúng public key.

## Tài liệu chính thức

- [Java SE 17 `PSSParameterSpec`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/spec/PSSParameterSpec.html)
- [Java SE 17 `MGF1ParameterSpec`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/spec/MGF1ParameterSpec.html)
- [Java SE 17 `Signature` — `initSign`, `initVerify`, `setParameter`, `update`, `sign`, `verify`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Signature.html)
- [RFC 8017 — PKCS #1 v2.2, `8.1 RSASSA-PSS` và `9.1 EMSA-PSS`](https://www.rfc-editor.org/rfc/rfc8017.html)
- [RFC 7518 3.5 — JWS algorithms PS256, PS384, PS512](https://www.rfc-editor.org/rfc/rfc7518.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="digital-signature-006">9.6 Thống nhất dữ liệu ký và tránh băm hai lần khi API đã kết hợp digest</a>

<details>
<summary>Xem chi tiết</summary>

# 9.6. Thống nhất dữ liệu ký và tránh băm hai lần khi API đã kết hợp digest

## Mục tiêu học tập

Sau bài này, bạn có thể:

1. Phân biệt **thông điệp gốc (message bytes)**, **digest**, **chữ ký số** và **biểu diễn dữ liệu cần ký**; hiểu chính xác chuỗi byte nào được bảo vệ.
2. Giải thích tại sao `Signature.getInstance("SHA256withRSA")` đã thực hiện SHA-256 như một phần thuật toán chữ ký và vì sao gọi `MessageDigest.digest(...)` trước `Signature.update(...)` có thể vô tình **băm hai lần**.
3. Thiết kế một **hợp đồng dữ liệu ký** rõ ràng: thứ tự trường, kiểu dữ liệu, encoding UTF-8, Unicode, số thập phân, ngày giờ, phân tách trường, domain separation, phiên bản, metadata và canonicalization.
4. Dùng Java 17+ để ký và xác minh **đúng cùng chuỗi byte**, kiểm tra dữ liệu bị sửa, minh họa lỗi double hashing và nhận diện khác biệt giữa RSA/PKCS#1 v1.5, RSA-PSS, ECDSA và Ed25519.
5. Tích hợp với đối tác, HSM, KMS hoặc API chỉ nhận digest mà không nhầm **API băm tích hợp** với **API ký digest đã băm trước**, đồng thời tách xác minh chữ ký khỏi tin cậy danh tính, phân quyền và chống phát lại.

**Phạm vi:** Bài học thuộc chủ đề **9. Chữ ký số** trong Java Advance – security-cryptography. Trọng tâm là tính nhất quán đầu vào và ngữ nghĩa của API ký; ví dụ ký bằng khóa mới chỉ minh họa cơ chế, không thay thế PKI hoặc hệ thống tin cậy production.

---

## 1. Chữ ký số thực sự ký dữ liệu nào?

Chữ ký số không ký “một object Java” theo nghĩa trừu tượng. API chữ ký xử lý **một chuỗi byte xác định**. Với thuật toán có digest tích hợp như `SHA256withRSA`, luồng khái niệm là:

```text
Dữ liệu nghiệp vụ (order, payload, file, yêu cầu...)
                         │
                         ▼
          Serialization/canonicalization
                         │
                         ▼
                 Message bytes M
                         │
                         ▼
       Signature("SHA256withRSA").update(M)
                         │
                         ▼
           SHA-256(M)  [nằm trong engine]
                         │
                         ▼
          Cơ chế ký RSA / PKCS#1 v1.5
                         │
                         ▼
                 Signature bytes S

Người nhận: xây lại đúng M → Signature.verify(S) bằng public key
```

Đây là sơ đồ minh họa ngữ nghĩa; provider quản lý chi tiết băm, encoding digest, padding và phép toán ký theo thuật toán cụ thể. Với `SHA256withRSA`, SHA-256 đã nằm trong chữ “SHA256with...”. Với `SHA256withECDSA` hay `RSASSA-PSS` được cấu hình SHA-256, API tương ứng cũng xử lý digest theo scheme. Với `Ed25519`, thuật toán EdDSA có xử lý hash nội bộ **theo chính đặc tả Ed25519**, không có nghĩa bạn được thay thông điệp bằng `SHA-256(M)`.

Điểm quan trọng:

- **Message bytes (`M`)**: byte đã được hai phía thống nhất dùng làm input.
- **Digest (`H(M)`)**: đầu ra của hàm băm như SHA-256, thường dài 32 byte; có thể dùng để kiểm tra fingerprint/so sánh nội dung hoặc nằm *bên trong* signature scheme.
- **Signature (`S`)**: kết quả của thuật toán ký sử dụng private key và input; thông thường người xác minh dùng public key và **chính byte M**.
- **Xác thực danh tính**: chữ ký chỉ chứng minh sự nhất quán với một public key và thuật toán. Để kết luận “ai đã ký”, cần quy trình tin cậy public key/certificate và policy.

Cách sử dụng `Signature` thông thường là **init → update(message bytes) → sign/verify**. Đừng tự băm trước chỉ vì nghe nói chữ ký số dùng hàm băm.

## 2. Băm một lần và băm hai lần khác nhau về ngữ nghĩa

Gọi `H` là SHA-256 và `M` là byte nội dung đã thống nhất.

### 2.1. Đúng khi dùng thuật toán tích hợp digest

```text
Signature("SHA256withRSA").update(M)
           └── engine thực hiện H(M)
           └── tạo chữ ký RSA trên biểu diễn digest theo scheme

Verifier("SHA256withRSA").update(M)
           └── engine thực hiện cùng H(M)
           └── verify chữ ký trên đúng thông điệp M
```

Java:

```java
Signature signer = Signature.getInstance("SHA256withRSA");
signer.initSign(privateKey);
signer.update(messageBytes);
byte[] signature = signer.sign();

Signature verifier = Signature.getInstance("SHA256withRSA");
verifier.initVerify(publicKey);
verifier.update(messageBytes);
boolean ok = verifier.verify(signature);
```

Đây là **đoạn minh họa chưa độc lập**: cần `import java.security.Signature;`, hai khóa tương ứng và `messageBytes`, cùng xử lý checked exception. Ví dụ độc lập hoàn chỉnh nằm ở mục 6.

### 2.2. Sai khác khi tự băm trước rồi gọi thuật toán có digest

```java
byte[] digest = MessageDigest.getInstance("SHA-256")
        .digest(messageBytes);

Signature signer = Signature.getInstance("SHA256withRSA");
signer.initSign(privateKey);
signer.update(digest); // Ký H(M) như thể nó là toàn bộ message.
byte[] signature = signer.sign();
```

Đây là **mẫu lỗi cho một giao thức yêu cầu ký M**. Với `SHA256withRSA`, engine tiếp tục tính SHA-256 trên **32 byte digest** được truyền vào, nên chữ ký có hiệu lực đối với `H(H(M))` ở tầng digest nội bộ, thay vì `H(M)` dự kiến.

```text
M ── SHA-256 ──> H(M) ── Signature(SHA256withRSA) ── SHA-256 ──> H(H(M))
^                                                ^
Dữ liệu hai bên muốn ký                           Engine coi H(M) là message
```

Hệ quả thực tế:

- Người nhận truyền **M nguyên bản** vào `SHA256withRSA` sẽ không xác minh được chữ ký gửi từ phía **đã truyền H(M)**.
- Nếu cả hai phía đều vô tình truyền **H(M)** vào `SHA256withRSA`, `verify` có thể trả **true**; **true không chứng minh hai bên thực hiện đúng giao thức mong muốn**.
- Double hashing không tự động có nghĩa attacker lập tức làm giả được RSA; vấn đề trực tiếp là **ký sai thông điệp/ngữ nghĩa, không tương thích giữa các bên, gây nhầm lẫn về bảo vệ dữ liệu**, có thể dẫn tới lỗi hệ thống khi các thành phần áp dụng quy tắc khác nhau.
- Đổi sang `Signature.getInstance("NONEwithRSA")` hoặc `NONEwithECDSA` để “sửa nhanh” là **nguy hiểm**: các mode không băm có hợp đồng, padding/encoding và giới hạn provider riêng; không phải API chung nhận digest rồi lập tức ký theo mọi scheme.

**Quy tắc:** khi `Signature` đã tích hợp digest, truyền **byte dữ liệu chuẩn hóa cần ký**, trừ khi giao thức được thiết kế rõ ràng rằng chính một digest là *thông điệp* cần ký.

### 2.3. Có nên tự tính `MessageDigest` ở đâu?

Có, nếu bạn cần mục đích **khác**:

- Tạo fingerprint để hiển thị, đối chiếu audit hoặc phát hiện nội dung thay đổi.
- Kiểm tính toàn vẹn tải xuống trong một kênh tin cậy với digest đã được xác thực riêng.
- Tích hợp dịch vụ ký **có hợp đồng prehashed input** (ví dụ API HSM/KMS nhận digest chuẩn), khi đó bạn phải biết chính xác byte và cơ chế nhận digest, xem mục 8.
- Ký **một bản manifest chứa các digest** của nhiều file: lúc này manifest chính là thông điệp M được ký; cần cấu trúc/encoding/version rõ ràng. Đây không phải “sửa double hashing” mà là thiết kế giao thức ký manifest.

Băm riêng để log không làm thay đổi byte truyền vào `Signature.update` trong giao thức ký trực tiếp.

## 3. Vì sao cùng “nội dung” nhưng chữ ký lại khác?

Hai phía có thể cùng nhìn thấy một đơn hàng hiển thị “125.50 VND” nhưng tạo ra **mảng byte khác nhau**. Thuật toán chữ ký nhận byte, không hiểu object, field name hay semantics; chỉ cần một byte khác là xác minh thông thường sẽ thất bại.

### 3.1. Những khác biệt hay xuất hiện

| Nguồn dữ liệu | Bên A | Bên B | Vì sao ký khác? |
| --- | --- | --- | --- |
| Encoding | UTF-8 | UTF-16LE hoặc platform default | Mảng byte khác dù text có vẻ giống |
| Unicode | Ký tự có dấu ở dạng NFC | Dạng NFD (ký tự nền + combining mark) | Hiển thị giống nhưng UTF-8 khác |
| Newline | `\n` (LF) | `\r\n` (CRLF) | Byte ký khác |
| BOM | Không có UTF-8 BOM | Có BOM | Byte đầu vào khác |
| JSON | `{"a":1,"b":2}` | `{"b":2,"a":1}` | Thứ tự field khác |
| JSON whitespace | `{"a":1}` | `{ "a": 1 }` | Byte khác; chữ ký không tự bỏ whitespace |
| Decimal | `125.50` | `125.5` hoặc `1.255E2` | Biểu diễn khác |
| Timestamp | UTC `2026-10-31T12:00:00Z` | `2026-10-31T19:00:00+07:00` | Cùng instant nhưng string khác |
| Collection | HashMap không có thứ tự chuẩn | SortedMap/sắp theo schema | Chuỗi serialization khác |
| Boolean/null | `false`/null được ghi rõ | Trường bị bỏ | Schema khác |
| HTTP request | Query param/percent encoding khác | Header whitespace/path khác | Signature input khác |
| File | Byte gốc | Text được parse rồi ghi lại | Parser có thể đổi encoding/newline/định dạng |

**Chú ý:** Không phải mọi hệ thống cần canonicalize để làm giống nhau. Nếu bạn ký **raw file bytes**, mọi byte của file chính là dữ liệu có chủ đích được bảo vệ: không được chuẩn hóa newline/Unicode khi verify, vì làm vậy thay thông điệp đã được tác giả ký.

### 3.2. Canonicalization là một hợp đồng giao thức

Các phía cần chốt **quy tắc biểu diễn dữ liệu** *trước khi ký*: chọn byte nào, thứ tự và định dạng ra sao, chuẩn hóa hay giữ nguyên byte gốc, dùng UTF-8 nào, schema version, xử lý dữ liệu thiếu và các loại số.

Hai lựa chọn khác nhau:

1. **Ký raw bytes:** thích hợp file nhị phân, nội dung HTTP body nguyên bản, firmware. Hai phía giữ nguyên byte; không parse/reserialize.
2. **Ký một biểu diễn canonical của dữ liệu có cấu trúc:** thích hợp đơn hàng, giao dịch, thông điệp nghiệp vụ. Cả hai phải sử dụng **cùng thuật toán canonicalization** để tạo lại đúng byte trước khi verify.

Nếu giao thức yêu cầu ký JSON, đừng dựa vào `Map.toString()`, sự may mắn của thứ tự `HashMap`, cách serializer tùy cấu hình hoặc thứ tự keys “thường gặp”. Có thể tham khảo một chuẩn canonical JSON cụ thể như **RFC 8785 (JCS)** khi phù hợp, cùng hạn chế của chuẩn về dạng số và tập dữ liệu. Java SE 17 **không cung cấp sẵn một API canonical JSON phổ dụng**.

Canonicalization phải được **version hóa**, được test trên các trường hợp Unicode, null/optional, số lớn, escape, time zone và serializer đa ngôn ngữ; không nên chỉ dựa vào một ví dụ JSON đơn giản.

## 4. Thiết kế hợp đồng dữ liệu ký chắc chắn

Một giao thức ký giao dịch nên xác định ít nhất:

| Thành phần | Quy tắc cần ghi rõ |
| --- | --- |
| **Purpose/domain** | Ví dụ `com.example.payment.approve/v1`; phân biệt chữ ký approve với refund/cancel |
| **Message schema/version** | Tên trường, kiểu, trường bắt buộc, optional/null, phiên bản thuật toán serialize |
| **Field order** | Thứ tự cố định hoặc canonical ordering đã định |
| **Binary frame** | Length-prefix, fixed width, endian, phân tách/escape tránh nhập nhằng |
| **Text encoding** | UTF-8; có hay không Unicode NFC/NFD; xử lý newline và BOM |
| **Numbers** | Đơn vị tiền, số chữ số thập phân, bỏ/giữ leading/trailing zero |
| **Date/time** | UTC timestamp format hoặc số epoch với unit rõ ràng |
| **Algorithm** | `SHA256withRSA`/`RSASSA-PSS`/`Ed25519`; tham số PSS, format signature |
| **Key identity** | Key ID/certificate và chính sách tin cậy riêng; không tin keyId do client tự khai |
| **Context** | User/tenant, object ID, action, API version, nonce, expiry hoặc sequence number |
| **Verification** | Lấy *đúng byte đã ký*, kiểm signature, key trust và authorization/replay |

### 4.1. Tránh nối chuỗi không phân ranh giới

Mẫu nguy hiểm:

```text
serialize(a,b) = UTF8(a + b)

("ab", "c") -> "abc"
("a", "bc") -> "abc"
```

Hai cặp trường khác nhau biến thành **cùng message bytes**, do đó **cùng chữ ký hợp lệ** có thể bị diễn giải thành hai giao dịch khác nhau nếu một bên cho phép thay đổi ranh giới trường. Đây là lỗi *ambiguous serialization*, không phải lỗi thuật toán SHA-256.

Một cách rõ ràng hơn:

```text
VERSION || len(field1) || field1 || len(field2) || field2 || ...
```

Trong đó `len` tính theo **byte đã encode**, không theo `String.length()` của Java; dùng fixed-width integer (ví dụ 4 byte big-endian), có giới hạn độ dài cho mỗi trường, thứ tự định sẵn, domain marker và schema version. Người nhận phải parse đúng schema tương ứng và không chấp nhận field trùng hoặc encoding mơ hồ.

### 4.2. Domain separation: chữ ký cho hành động nào?

Giả sử cùng một key dùng cho hai thao tác `approve` và `refund`. Nếu hai API ký cùng một JSON chung không có operation code, một signature có thể bị đem đến endpoint khác (tùy logic từng endpoint) để được hiểu sai nghĩa.

Hãy đưa **domain/purpose** đã thống nhất vào chuỗi byte cần ký, ví dụ:

```text
"com.example.payment/approve/v1" || orderId || amount || currency || tenantId
```

Các field cần encoding/framing không nhập nhằng; đây chỉ là ký hiệu khái niệm. Bên verify phải kiểm đúng **domain** cho endpoint đang xử lý, đúng tenant, đúng action, đúng object và các thuộc tính có thể thay đổi kết quả nghiệp vụ.

Không nên lấy cùng message bytes/signature cho một protocol khác mà không có chủ đích. Domain separation bảo vệ **ý nghĩa** của chữ ký, ngoài integrity byte-level.

### 4.3. Tính thứ tự và đơn vị của tiền/ngày giờ

Đừng ký kiểu `double` cho tiền nếu hai hệ thống cần biểu diễn văn bản độc lập. Chọn `BigDecimal` với scale/rounding rule chính xác hoặc dùng số nguyên đơn vị nhỏ nhất như cent, đồng. Ví dụ `BigDecimal("125.50").setScale(2, RoundingMode.UNNECESSARY).toPlainString()` cho ra `"125.50"`; không dùng locale-dependent `NumberFormat` trong hợp đồng byte.

Ngày giờ nên có một quy ước rõ ràng như epoch milliseconds UTC theo `long`, hoặc ISO-8601 UTC đã cố định độ chính xác và định dạng. `Instant.toString()` trong các trường hợp khác nhau có thể thay độ dài phần fractional; nếu đây là giao thức liên thông, cần test vector, không suy luận mọi string ISO là giống nhau.

### 4.4. Unicode và normalization phải được thống nhất

Trong Java:

```java
byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
```

Đây là đoạn minh họa, cần `import java.nio.charset.StandardCharsets;` và `text` đã được định nghĩa. Việc gọi `getBytes()` không tham số phụ thuộc charset mặc định theo phiên bản JDK/môi trường; với protocol cross-platform luôn ghi rõ UTF-8.

Unicode có thể biểu diễn một chuỗi nhìn giống nhau bằng code point khác nhau, ví dụ ký tự được tạo từ một code point dựng sẵn so với ký tự nền kèm dấu kết hợp. Nếu hợp đồng yêu cầu NFC, hai phía phải gọi một quy tắc NFC tương đương **trước khi encode**. Tuy nhiên **không tự chuẩn hóa nội dung raw file hay identifier** nếu giao thức không cho phép: normalization có thể đổi dữ liệu/hành vi định danh.

## 5. Chính xác nên gọi `java.security.Signature` như thế nào?

Với `SHA256withRSA`, quy trình chuẩn là:

1. Chọn thuật toán đã được giao thức/chính sách cho phép.
2. Tạo byte ký **duy nhất** từ canonical serializer hoặc raw bytes.
3. `Signature.getInstance("SHA256withRSA")`.
4. `initSign(privateKey)`, sau đó `update(messageBytes)` rồi `sign()`.
5. Gửi/lưu signature kèm thông tin cần để tìm đúng message/schema/algorithm/key theo format được xác thực.
6. Bên nhận xây lại đúng `messageBytes`, `initVerify(publicKey)`, `update(messageBytes)` và `verify(signature)`.
7. Chỉ sau verify thành công và key identity đáng tin cậy mới áp dụng authorization, thời hạn, replay controls và business policy.

`Signature.update(byte[])` nhận **message bytes**, không phải “digest placeholder”. Kết quả `verify` là boolean; tùy trường hợp chữ ký sai format/provider có thể ném `SignatureException` thay vì trả false, nên cần xử lý lỗi an toàn.

### 5.1. `MessageDigest` là API khác

`MessageDigest.getInstance("SHA-256")` cung cấp hash không khóa, dùng để tính digest/fingerprint của dữ liệu. Nó không chứng minh signer sở hữu private key; bất kỳ ai cũng có thể băm dữ liệu công khai.

```java
byte[] digest = MessageDigest.getInstance("SHA-256")
        .digest(messageBytes);
```

Đoạn này cần `import java.security.MessageDigest;`. Sau khi tính digest cho mục tiêu audit, nếu chữ ký algorithm là `SHA256withRSA`, vẫn đưa **messageBytes** vào `Signature.update`; không đưa `digest` trừ khi giao thức quy định đang ký một **digest-as-message** với ý nghĩa riêng.

### 5.2. Không có “default signature algorithm”

Java yêu cầu `Signature.getInstance(algorithm)`. Java 17 không có một chế độ ký mặc định phù hợp mọi loại key. Chọn tên theo key type và policy, ví dụ:

| Thuật toán `Signature` | Digest do scheme xử lý? | Đặc điểm quan trọng |
| --- | --- | --- |
| `SHA256withRSA` | **Có**, SHA-256 | RSA PKCS#1 v1.5 signature scheme; thường dùng cho tương thích |
| `RSASSA-PSS` + `PSSParameterSpec` SHA-256 | **Có**, theo cấu hình PSS | MGF1 hash, salt length, trailer phải thống nhất; PSS có ngẫu nhiên trong chữ ký |
| `SHA256withECDSA` | **Có**, SHA-256 | Chữ ký ECDSA, định dạng thường DER theo provider/API; có biến thể P1363 riêng |
| `Ed25519` | Có xử lý hash nội bộ theo EdDSA, **không phải SHA256with...** | Ký trực tiếp message bytes theo Ed25519; không tự băm SHA-256 trước |
| `NONEwithRSA` / `NONEwithECDSA` | Không có digest tích hợp theo cách tên thuật toán quy định | Chỉ dùng khi giao thức/provider xác định chính xác input và encoding; không phải cách sửa tự động |

**Không sử dụng MD5/SHA-1 để tạo chữ ký mới.** Các thuật toán được provider hỗ trợ không mặc nhiên được chính sách bảo mật hiện đại cho phép; xem những danh sách vô hiệu và yêu cầu compliance của môi trường.

## 6. Chương trình Java 17+: chữ ký đúng, double hash sai và Ed25519

Chương trình dưới đây minh họa một giao thức ký **duy nhất** cho yêu cầu phê duyệt thanh toán. Ta xây chuỗi byte từ **domain + orderId + amount + currency**, rồi ký bằng `SHA256withRSA`. Đồng thời chương trình cố tình ký dữ liệu đã được băm trước để cho thấy chữ ký đó chỉ verify khi phía nhận cũng nhầm input theo cùng cách.

**Lưu ý:** RSA-2048 chỉ được tạo trong bộ nhớ để demo; public key được lấy từ cùng cặp khóa. Chương trình chưa giải quyết certificate trust, key storage, authorization, replay, signature envelope versioning hay thanh toán thật. Không có giá trị key/signature cố định được in ra.

Tạo file **`SigningBytesDemo.java`**:

```java
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.text.Normalizer;
import java.util.Arrays;

public class SigningBytesDemo {
    private static void writeField(DataOutputStream out, String value)
            throws IOException {
        byte[] utf8 = Normalizer.normalize(value, Normalizer.Form.NFC)
                .getBytes(StandardCharsets.UTF_8);
        // Big-endian 4-byte length prefix + UTF-8 data.
        out.writeInt(utf8.length);
        out.write(utf8);
    }

    // Trả về canonical bytes cho một action thuộc domain đã xác định.
    private static byte[] canonicalPayment(
            String orderId, BigDecimal amount, String currency)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        writeField(out, "com.example.payment/approve/v1");
        writeField(out, orderId);
        writeField(out, amount.setScale(2, RoundingMode.UNNECESSARY)
                .toPlainString());
        writeField(out, currency);
        out.flush();
        return bytes.toByteArray();
    }

    private static byte[] sign(PrivateKey key, byte[] message)
            throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(key);
        signer.update(message); // Engine tự thực hiện SHA-256(message).
        return signer.sign();
    }

    private static boolean verify(
            PublicKey key, byte[] message, byte[] signed)
            throws Exception {
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(key);
        verifier.update(message);
        return verifier.verify(signed);
    }

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();

        byte[] message = canonicalPayment(
                "ORDER-104", new BigDecimal("125.50"), "VND");
        byte[] rebuilt = canonicalPayment(
                "ORDER-104", new BigDecimal("125.5"), "VND");

        byte[] signature = sign(pair.getPrivate(), message);
        System.out.println("canonicalBytesEqual=" +
                Arrays.equals(message, rebuilt));
        System.out.println("signatureValid=" +
                verify(pair.getPublic(), rebuilt, signature));

        // Cùng schema, khác số tiền => bytes khác => verify thất bại.
        byte[] changed = canonicalPayment(
                "ORDER-104", new BigDecimal("125.51"), "VND");
        System.out.println("changedAmountRejected=" +
                !verify(pair.getPublic(), changed, signature));

        // LỖI: gọi SHA-256 bên ngoài rồi lại ký bằng SHA256withRSA.
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(message);
        byte[] doubleHashedSignature = sign(pair.getPrivate(), digest);

        System.out.println("wrongDigestSignatureRejectsOriginal=" +
                !verify(pair.getPublic(), message, doubleHashedSignature));
        System.out.println("wrongDigestSignatureAcceptsDigest=" +
                verify(pair.getPublic(), digest, doubleHashedSignature));
        System.out.println("originalSignatureRejectsDigest=" +
                !verify(pair.getPublic(), digest, signature));

        // Ed25519 cũng nhận canonical message bytes; không prehash.
        KeyPairGenerator edGenerator =
                KeyPairGenerator.getInstance("Ed25519");
        KeyPair edKeys = edGenerator.generateKeyPair();
        Signature edSigner = Signature.getInstance("Ed25519");
        edSigner.initSign(edKeys.getPrivate());
        edSigner.update(message);
        byte[] edSignature = edSigner.sign();

        Signature edVerifier = Signature.getInstance("Ed25519");
        edVerifier.initVerify(edKeys.getPublic());
        edVerifier.update(message);
        System.out.println("ed25519Valid=" +
                edVerifier.verify(edSignature));
    }
}
```

Biên dịch/chạy với JDK 17+ thông dụng hỗ trợ RSA và Ed25519:

```shell
javac SigningBytesDemo.java
java SigningBytesDemo
```

**Stdout kỳ vọng:**

```text
canonicalBytesEqual=true
signatureValid=true
changedAmountRejected=true
wrongDigestSignatureRejectsOriginal=true
wrongDigestSignatureAcceptsDigest=true
originalSignatureRejectsDigest=true
ed25519Valid=true
```

### 6.1. Vì sao các dòng trên ổn định?

- `canonicalBytesEqual=true`: giá trị `125.50` và `125.5` đều trở thành `125.50` nhờ `setScale(2, RoundingMode.UNNECESSARY)`. `writeField` áp dụng cùng quy tắc NFC, UTF-8, thứ tự trường và length-prefix.
- `signatureValid=true`: RSA ký canonical message, và verifier sử dụng chính canonical message được tái tạo; byte giống nhau.
- `changedAmountRejected=true`: thay `125.50` thành `125.51` tạo message khác; chữ ký hợp lệ cho giá trị gốc không xác minh cho nội dung thay đổi.
- `wrongDigestSignatureRejectsOriginal=true`: chữ ký sai cách (ký digest bằng thuật toán đã SHA-256) **không hợp lệ** khi verifier làm đúng và cung cấp original message bytes.
- `wrongDigestSignatureAcceptsDigest=true`: cả hai phía cùng truyền digest như message vào thuật toán `SHA256withRSA`; verify vẫn **true**, minh họa vì sao “test xanh” không chứng minh triển khai khớp giao thức.
- `originalSignatureRejectsDigest=true`: chữ ký chuẩn của message không xác minh khi phía verify đưa digest bytes sai đầu vào.
- `ed25519Valid=true`: Ed25519 nhận đúng canonical message bytes; thuật toán tự thực hiện quy trình nội bộ theo đặc tả, không có bước `MessageDigest("SHA-256")` do ứng dụng áp thêm.

Vì RSA, Ed25519 keypair được sinh ngẫu nhiên mỗi lần chạy, không đưa signature bytes cụ thể vào expected stdout. Những boolean đều có ý nghĩa độc lập với giá trị key và chữ ký thực tế khi JDK/provider triển khai đúng chuẩn.

### 6.2. Bài demo chưa phải canonical protocol production

`writeField` và `canonicalPayment` chỉ là **một format minh họa**. Bản thật cần kiểm tối đa kích thước field, validate kiểu/mã tiền, null/empty, Unicode normalization thống nhất và xử lý phiên bản. Với thanh toán thật phải đưa thêm account/tenant, identifier, action, replay nonce/expiry và mọi trường có ảnh hưởng nghiệp vụ vào dữ liệu ký.

Sử dụng tên domain mẫu không biến một ứng dụng thành hệ thống thanh toán an toàn; phần quyết định còn gồm: ai cấp và tin public key, signing authority, authorization, key storage, chống replay, audit, giới hạn thời hạn và protocol interoperability.

## 7. Xử lý file lớn và streaming với `Signature.update`

Với file lớn, không cần đọc toàn bộ vào RAM và **không cần tự SHA-256 file rồi đưa digest vào `SHA256withRSA`**. Java cho phép gọi `update` nhiều lần trên các phần byte liên tiếp:

```java
Signature signer = Signature.getInstance("SHA256withRSA");
signer.initSign(privateKey);

try (InputStream in = Files.newInputStream(path)) {
    byte[] buffer = new byte[8192];
    int read;
    while ((read = in.read(buffer)) != -1) {
        if (read > 0) {
            signer.update(buffer, 0, read);
        }
    }
}
byte[] signature = signer.sign();
```

Đây là **đoạn minh họa không độc lập**: cần imports `java.security.Signature`, `java.io.InputStream`, `java.nio.file.Files` và biến `privateKey`, `path` hợp lệ trong method xử lý exception. Bên verify mở **chính file byte gốc** và gọi `Signature.update(buffer, 0, read)` theo thứ tự, sau đó `verify(signature)`. Chia thành các chunk 8192 byte hay kích thước khác **không thay đổi** message stream nếu thứ tự/byte identical; engine duy trì trạng thái digest nội bộ khi thuật toán có digest.

Quan trọng khi ký file:

- Không bỏ qua các byte 0, newline, BOM hoặc metadata thực sự nằm trong phạm vi file đã ký.
- Không đọc file một lần để tính hash rồi vô tình đọc một file đã bị thay đổi để ký: nếu có snapshot/concurrency threat, bảo đảm dữ liệu ký là cùng phiên bản với dữ liệu phát hành.
- Không phát hành signature cho file A rồi publish file B có nội dung thay đổi sau khi ký.
- Với file nhị phân, ưu tiên ký raw bytes; nếu ký manifest chứa hash, định nghĩa rõ nội dung manifest và cách xác thực kết quả khi verify.

### 7.1. Ký hash của file trong manifest khác với ký file trực tiếp

Ví dụ bạn có danh sách artifact:

```text
manifestVersion = 1
artifactA.sha256 = H(rawArtifactA)
artifactB.sha256 = H(rawArtifactB)
metadata = product, version, size, path, ...
signature = Sign(canonicalManifestBytes)
```

Ở đây, **canonicalManifestBytes** là message được ký; manifest chứa digest của file. Nếu `Sign` dùng `SHA256withRSA`, nó tiếp tục SHA-256 **manifest**, không phải double hashing lỗi nếu đây đúng là giao thức đã thống nhất.

Người nhận phải: xác minh chữ ký manifest bằng public key tin cậy → parse manifest an toàn → tính lại digest của từng artifact và so sánh → kiểm các ràng buộc đường dẫn, phiên bản, phạm vi và policy. Chỉ verify manifest mà không đối chiếu artifact là chưa hoàn thành quy trình integrity.

## 8. Ngoại lệ thực tế: HSM/KMS hoặc API ký prehashed input

Có những giao diện yêu cầu **message bytes**, cũng có giao diện yêu cầu **digest đã tính trước**. Đặc biệt khi làm việc với PKCS#11, HSM, KMS, API cloud hoặc thư viện bên thứ ba, cần đọc **hợp đồng cụ thể** thay vì đoán theo tên method `sign`.

### 8.1. Hai loại API không được trộn

**Loại A – ký message:**

```text
Input: raw/canonical M
Algorithm: SHA256withRSA (SHA-256 tính bên trong)
Output: chữ ký theo scheme
```

**Loại B – ký digest đã băm trước:**

```text
Input: H(M), độ dài chính xác theo thuật toán
Algorithm/service: API quy định input là PREHASHED DIGEST
Output: chữ ký ứng với M theo scheme xác định
```

Ở loại B, dịch vụ *có thể* xây dựng đúng `DigestInfo`/padding hoặc PSS encoding cho digest mà client gửi, hoặc có contract provider-specific khác; không được suy ra rằng nó đơn giản gọi một `Signature("SHA256withRSA").update(digest)` phía server. Phải xác định **exact digest scheme, digest length, parameter spec, encoding, padding, key type, wire format** và test vector từ nhà cung cấp.

Một trường `messageType = DIGEST` ở API nào đó chỉ có nghĩa khi tài liệu thật quy định hành vi xử lý digest. Không tự gán tên field đó cho Java `Signature`, vì JCA `update(...)` **không có flag “input đang là digest”**.

### 8.2. Đặc biệt với RSA/PKCS#1 v1.5

RSA signature kiểu PKCS#1 v1.5 với SHA-256 thường có **DigestInfo** bao quanh SHA-256 digest trước khi thực hiện signature primitive. **Raw RSA modular exponentiation của một digest 32 byte không tương đương `SHA256withRSA`**. Tự băm trước rồi chọn một thuật toán `NONEwithRSA` không bảo đảm provider sẽ mã hóa DigestInfo đúng hay tương thích với bên kia.

Cách an toàn trong JCA thường là: **truyền original message bytes vào `SHA256withRSA`**. Khi bắt buộc dùng API nhận digest, giao thức phải nêu rõ SDK có tạo DigestInfo và padding hay không, hoặc phần nào do HSM thực hiện. Đừng tự viết RSA padding hay encoding nếu có thư viện/scheme đã được kiểm chứng.

### 8.3. RSA-PSS: tham số cần đồng nhất

RSA-PSS không chỉ cần biết “SHA-256”. Cả signer và verifier phải thống nhất:

- digest của message, ví dụ SHA-256;
- thuật toán MGF (thường MGF1) và digest MGF1;
- độ dài salt, ví dụ 32 byte;
- trailer field và định dạng ký PSS.

Ví dụ cấu hình Java 17 mang tính minh họa:

```java
PSSParameterSpec spec = new PSSParameterSpec(
        "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1);
Signature pss = Signature.getInstance("RSASSA-PSS");
pss.setParameter(spec);
pss.initSign(rsaPrivateKey);
pss.update(canonicalBytes);
byte[] signature = pss.sign();
```

Đây **không phải chương trình độc lập**: cần `java.security.Signature`, `java.security.spec.PSSParameterSpec`, `java.security.spec.MGF1ParameterSpec`, RSA private key và message bytes; provider có thể có yêu cầu về hỗ trợ/thứ tự cấu hình. Đặc biệt, với `RSASSA-PSS` đã cấu hình SHA-256, ứng dụng **không tự băm M trước khi gọi update**. Bên verify dùng `initVerify` và **cùng tham số** với canonical bytes giống hệt.

Hai chữ ký RSA-PSS của cùng message có thể có **signature bytes khác nhau** do salt ngẫu nhiên, trong khi verify đều true. Vì vậy test “chữ ký PSS phải bằng hệt byte ký lần trước” không phải kiểm đúng; test bằng verify và các vector chuẩn có kiểm soát.

### 8.4. ECDSA: cùng digest nhưng encoding signature có thể khác

`SHA256withECDSA` xử lý SHA-256 của message nội bộ theo scheme. Tuy nhiên signature representation có thể dùng **ASN.1 DER** (cặp `r, s` được encode theo DER) hoặc **IEEE P1363 fixed-width** theo tên thuật toán/contract. Hai bên có thể ký cùng M, cùng key và SHA-256 nhưng **khác định dạng signature bytes**.

Khi tích hợp giữa Java, OpenSSL, JWT/JWS, HSM hay các SDK khác, đừng chỉ hỏi “ECDSA SHA256 hay không”; phải kiểm **DER vs P1363/raw `r||s`**, curve, key format và contract. **Không** dùng `NONEwithECDSA` thay cho `SHA256withECDSA` khi không hiểu yêu cầu raw/prehashed của provider.

### 8.5. Ed25519: không tự thêm SHA-256

`Ed25519` là một scheme EdDSA định nghĩa cụ thể cách xử lý message và băm bên trong thuật toán. `Ed25519ph` là biến thể *prehash* có domain separation khác, **không tương đương** việc lấy SHA-256 của M rồi gọi Java `Ed25519.update(digest)`. Java 17 `Signature.getInstance("Ed25519")` thông thường nhận trực tiếp M; nếu giao thức yêu cầu prehashed variant, cần đúng chuẩn/triển khai có hỗ trợ thật, không chế bằng cách gọi `MessageDigest`.

**Câu hỏi bắt buộc cho tích hợp:** API nhận **message** hay **digest**? Thuật toán chính xác, params, prehash variant, signature encoding và byte canonicalization có trùng không?

## 9. Không được tách thuật toán khỏi cấu trúc dữ liệu và quyền kiểm tra

Một chữ ký cần một **verification context** rõ ràng. Tối thiểu, một signed envelope có thể bao gồm:

```text
version
message-type/domain
canonicalization-id
payload or stable payload reference
signature-algorithm and parameters
signing-key-id / certificate chain reference
signature bytes
timestamp / nonce / request-id if protocol requires
```

Các trường bên ngoài có thể cần thiết để chọn cách parse, nhưng **không vì thế mà tự được bảo vệ toàn vẹn**. Nếu attacker có thể sửa `algorithm`, `domain`, `keyId`, `canonicalizationId` hoặc payload reference để đổi cách verify/xử lý, thiết kế phải có **allowlist và ràng buộc xác thực metadata**, thường đưa các trường quan trọng vào chính message cần ký hoặc bảo vệ chúng theo cơ chế định dạng được chuẩn hóa.

### 9.1. Verifier xác minh nhiều hơn chữ ký

Quy trình kiểm chứng đúng:

1. Kiểm cấu trúc packet/giới hạn kích thước và version được chấp nhận, tránh parse ambiguity/DoS.
2. Xác định **domain/action** mong đợi từ endpoint thực, không tin claim “operation=approve” do client tùy ý đổi.
3. Dựng lại **đúng message bytes** theo canonicalization version; xác định mọi field quan trọng có nằm trong input không.
4. Chọn thuật toán và public key theo **server-side policy/allowlist**, xác định keyId/certificate thuộc danh tính được chấp thuận.
5. Gọi `Signature.initVerify`, `update(M)`, `verify(S)`; xử lý mọi sai lệch/exception như verification failure phù hợp.
6. Kiểm **trust chain, validity, certificate usage, revocation policy** khi dùng PKI; chữ ký đúng với một public key vô danh chưa chứng minh ai là người ký.
7. Kiểm business authorization (người ký có quyền approve khoản này không), tenant/object binding, expiry, nonce/sequence/replay control.
8. Chỉ sau các bước thích hợp mới thực hiện side effect/ghi dữ liệu; ghi audit ở mức cần thiết, không tiết lộ private key.

**Chữ ký không tự chống replay**: cùng một message và signature hợp lệ có thể được gửi lại. Việc đưa timestamp/nonce vào payload ký phải đi cùng **kiểm tra thời hạn và trạng thái chưa dùng** phía verifier.

### 9.2. Chữ ký không mã hóa dữ liệu

Chữ ký kiểm tính toàn vẹn/xác thực sở hữu private key, **không bảo mật nội dung message**. Một payload JSON đã ký vẫn có thể đọc được nếu truyền công khai. Nếu cần confidentiality, dùng TLS hoặc encryption/AEAD phù hợp, tách biệt với signature.

## 10. Tích hợp ký HTTP request/JSON: hợp đồng cụ thể

Một giao thức HTTP signing có thể ký:

```text
METHOD + "\n" +
CANONICAL_PATH + "\n" +
CANONICAL_QUERY + "\n" +
CANONICAL_HEADERS + "\n" +
BODY_DIGEST + "\n" +
TIMESTAMP + "\n" +
NONCE
```

**Đây là ví dụ cấu trúc khái niệm, không phải tiêu chuẩn HTTP signature để dùng trực tiếp.** Protocol cần xác định rõ canonical path, percent-encoding, query key sorting, duplicate query params, cách trim header, host, newline, charset, method case, chữ ký trên body bytes hay digest, giới hạn timestamp, nonce và header nào được bảo vệ.

Nếu protocol **đã định nghĩa `BODY_DIGEST = SHA256(rawBody)` là một field**, ký canonical request có field này bằng `SHA256withRSA` là hợp lệ: signer ký **request canonical bytes**, engine SHA-256 toàn bộ request. Điều này **khác** với việc lấy `SHA256(canonicalRequest)` rồi truyền digest vào một engine đã SHA-256.

Tương tự, nếu đã thống nhất ký JSON canonical theo RFC 8785, signer và verifier cần cùng chuẩn/phiên bản và đặc biệt thận trọng với dữ liệu số ngoài phạm vi JSON number mà chuẩn hỗ trợ. Với dữ liệu tài chính, có thể nên ký schema bytes riêng với money ở dạng decimal string/đơn vị nhỏ nhất, thay vì áp canonical JSON chung không xem xét giới hạn.

### 10.1. Đồng bộ test vector xuyên hệ thống

Để kiểm hệ thống Java ↔ JavaScript/Python/Go/HSM, chuẩn bị bộ test vector đã thống nhất gồm:

- **Input business fields** và ý nghĩa của từng trường.
- **Canonical bytes** dưới dạng hex/Base64 và chiều dài byte.
- **Digest của canonical bytes** dưới dạng hex chỉ để chẩn đoán, không làm input cho combined `Signature`.
- **Algorithm/key/public key/cert** test cố định, tham số RSA-PSS hoặc encoding ECDSA nếu có.
- **Expected signature** khi scheme deterministic và điều kiện phù hợp, hoặc **expected verification result** với scheme có randomness.
- Trường hợp negative: khác 1 byte, khác order/amount/tenant/domain, Unicode NFC vs NFD, LF vs CRLF, null/empty, JSON key order, timestamp format, unknown version, altered keyId, replay.

Khi mismatch, so sánh **canonical bytes trước**, sau đó algorithm/params/key và signature format. Đừng tốn thời gian nghi ngờ provider khi một bên ký UTF-8 LF còn bên kia ký UTF-16LE CRLF.

## 11. Các lỗi thường gặp và hướng chẩn đoán

| Triệu chứng | Cần kiểm | Nguyên nhân điển hình |
| --- | --- | --- |
| Java ký xong tự verify true, hệ thống khác false | Canonical byte array của **hai phía**, algorithm/params, signature encoding | Mỗi bên ký byte khác nhau hoặc bên này prehash, bên kia không |
| `SHA256withRSA` nhận 32 byte digest và verify phía khác thất bại | Ai đã gọi `MessageDigest` trước `Signature.update`? | Double hashing/inconsistent input |
| Cùng payload hiển thị, verify sai | UTF-8, Unicode NFC, newline, BOM, số/date, JSON ordering | Nội dung hiển thị giống nhưng byte khác |
| Chữ ký ECDSA không verify | Signature DER hay P1363; curve/format/key | Signature wire format khác nhau |
| RSA-PSS báo sai chữ ký | Digest, MGF1 hash, salt length, trailer, provider | PSS params không trùng hoặc ký sai input |
| Ed25519 trong Java không khớp phía khác | Ed25519 thuần hay Ed25519ph? Có tự SHA-256 trước không? | Nhầm scheme/prehash variant |
| `InvalidKeyException` | Loại private/public key và algorithm được chọn | RSA key với ECDSA algorithm hoặc key/provider không phù hợp |
| `NoSuchAlgorithmException` | Runtime/provider và tên thuật toán | Provider không hỗ trợ tên thuật toán cụ thể |
| `SignatureException` khi verify | Signature bytes, length, format/provider | Dữ liệu ký sai format hoặc signature encoding sai |
| Verify true nhưng nghiệp vụ sai | Các field đã ký và quyền người ký | Bỏ sót amount, tenant, action, expired request, replay |
| Thử sửa một field mà verify vẫn true | Field đó có thực sự nằm trong canonical bytes? | Ký thiếu dữ liệu nghiệp vụ quan trọng |
| Test so sánh hai chữ ký PSS/ECDSA thành false | Thuật toán có randomness trong nonce/salt ký | Nên test verify, không so signature bytes |
| Chữ ký hợp lệ với một key lạ | Trust store/PKI, binding key↔identity | Chưa xác thực public key thuộc chủ thể có quyền |

### 11.1. Cách debug hiệu quả

1. In **byte length** của canonical message ở hai phía, và hash fingerprint của canonical bytes **chỉ trong môi trường kiểm soát**. Đừng log plaintext nhạy cảm, secret/key/token.
2. Khi debug trong test, so sánh canonical bytes dạng hex/Base64 hoặc binary diff để xác định byte đầu tiên sai; dữ liệu test cần vô hại.
3. Kiểm tên `Signature.getAlgorithm()`, `getProvider()` và `PSSParameterSpec` khi áp dụng.
4. Kiểm kiểu public key, curve, certificate chain và signature encoding.
5. Viết negative tests cho các field ảnh hưởng nghiệp vụ để bảo đảm chúng thật sự nằm trong dữ liệu ký.
6. Nếu chỉ lỗi với dữ liệu Unicode hoặc số thập phân, kiểm normalization, encoding và canonical schema trước khi đổi thuật toán.

**Không** “sửa verify false” bằng cách băm thêm một lần ở phía nhận cho đến khi verify true. Việc đó có thể che giấu một sai lệch giao thức. Thay vào đó, xác định input chuẩn mà giao thức muốn bảo vệ, rồi sửa cả signing/verification contract cho đồng nhất.

## 12. Câu hỏi tự kiểm tra và đáp án

**1. Với `Signature("SHA256withRSA")`, `update` phải nhận message hay digest?**  
Trong sử dụng thông thường, nhận **message bytes nguyên bản/canonical**, vì engine đã xử lý SHA-256. Chỉ truyền digest nếu digest thực sự được định nghĩa là message cần ký, hoặc API riêng có contract prehash rõ ràng; không tự coi `Signature.update` là prehash API.

**2. Nếu signer và verifier cùng gọi `SHA256(M)` rồi dùng `SHA256withRSA`, verify true có chứng minh đúng không?**  
Không. Hai bên có thể nhất quán trong việc ký `H(M)` như message và do đó nhận `H(H(M))` ở tầng digest nội bộ, nhưng trái với hợp đồng ký M nếu hợp đồng yêu cầu như vậy.

**3. Vì sao không được nối `orderId + amount + currency` rồi ký?**  
Ranh giới trường có thể nhập nhằng, định dạng số không cố định; cần thứ tự, length-prefix/escaping, schema và representation rõ ràng.

**4. Có nên ký JSON text từ `HashMap.toString()` không?**  
Không. Dựa vào cách hiển thị object và thứ tự Map không tạo giao thức serialization ổn định. Chọn canonical serializer hoặc ký raw bytes theo hợp đồng.

**5. Hai timestamp cùng instant nhưng khác offset có verify được cùng signature không?**  
Không nếu hai chuỗi ký byte khác nhau. Muốn tương đương phải định nghĩa canonical time representation trước khi ký.

**6. `RSASSA-PSS` với SHA-256 có cần `MessageDigest.digest(M)` riêng không?**  
Không khi dùng Java `Signature("RSASSA-PSS")` với `PSSParameterSpec` quy định SHA-256; engine xử lý digest theo PSS scheme.

**7. Ed25519 có phải `SHA256withEd25519` không?**  
Không. Ed25519 là scheme riêng với xử lý nội bộ; không tự hash SHA-256 trước khi gọi `Signature("Ed25519")`.

**8. Chữ ký đúng với public key lấy từ request có chứng minh người gửi được tin không?**  
Không. Cần xác thực key/cert binding và authorization; nếu attacker tự tạo keypair rồi gửi public key của họ, verify vẫn true cho chữ ký họ tự tạo.

**9. Chữ ký có chống replay không?**  
Không. Cần ký nonce/timestamp/sequence và kiểm trạng thái/thời gian ở phía nhận.

**10. Ký manifest chứa file hashes có phải luôn là double hashing sai?**  
Không. Manifest là thông điệp M khác; digest của file là **data field** trong manifest. Đừng đánh đồng ký manifest với truyền SHA256(manifest) vào API có digest tích hợp.

**11. Vì sao cùng payload nhưng ký PSS hai lần có signature bytes khác nhau?**  
PSS dùng salt/ngẫu nhiên trong quá trình ký. So sánh raw signature bytes không phải test đúng; verify theo cùng thuật toán/params/key/message.

**12. Nếu cần ký digest qua HSM thì dùng `NONEwithRSA` luôn được chứ?**  
Không. Phải tuân hợp đồng HSM/PKCS#11 cụ thể về message hay digest, padding/DigestInfo/PSS encoding, độ dài digest và thuật toán. Dùng SDK chuẩn và test vector của provider.

## 13. Checklist đưa chữ ký số vào production

1. **Định nghĩa chính xác M:** raw bytes hay canonical schema? Test vector xác định byte length, hex và encoding.
2. **Ký đủ ngữ cảnh:** domain/purpose, version, user/tenant, action, resource, amount, timestamp, nonce/expiry nếu cần.
3. **Không nhập nhằng serialization:** tránh nối string đơn giản; quy định field order, type, numeric/time format, Unicode và length-prefix.
4. **Khớp thuật toán với key:** `SHA256withRSA`, `RSASSA-PSS`, `SHA256withECDSA`, `Ed25519` và params/encoding phù hợp.
5. **Không băm hai lần ngoài ý muốn:** với API `Signature` tích hợp digest, truyền message bytes; đừng đưa `MessageDigest.digest(M)` vào `update`.
6. **Không dùng raw signature scheme như shortcut:** prehashed APIs phải có hợp đồng rõ về padding/encoding và test vector.
7. **Xác minh signer identity:** trust public key/certificate, algorithm allowlist, keyId binding, validity/revocation policy và authorization.
8. **Chống replay:** sign nonce/time/sequence và thực thi policy kiểm tra ở verifier.
9. **An toàn khi vận hành:** bảo vệ private key, ưu tiên keystore/KMS/HSM theo nhu cầu; không log key/message nhạy cảm, không tạo digest debug như dữ liệu công khai nếu payload cần bí mật.
10. **Kiểm thử xuyên hệ thống:** success, sửa 1 byte, đổi domain/tenant/amount, sai key, sai params, Unicode, newline, JSON ordering, replay và downgrade.

## Tài liệu tham khảo chính thống

- [Java SE 17 – Signature](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Signature.html): các giai đoạn `initSign/initVerify → update → sign/verify`, tên thuật toán và trạng thái engine.
- [Java SE 17 – MessageDigest](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/MessageDigest.html): hash độc lập và các phép `digest/update`.
- [Java Cryptography Architecture Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-cryptography-architecture-jca-reference-guide.html): provider, engine, chữ ký/digest và mô hình API Java.
- [Java SE 17 – PSSParameterSpec](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/spec/PSSParameterSpec.html) và [MGF1ParameterSpec](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/spec/MGF1ParameterSpec.html): các tham số cần thống nhất với RSA-PSS.
- [Java Security Standard Algorithm Names](https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html): tên `SHA256withRSA`, `SHA256withECDSA`, `RSASSA-PSS`, `Ed25519` và các biến thể.
- [RFC 8017 – PKCS #1 v2.2](https://www.rfc-editor.org/rfc/rfc8017): các signature schemes RSA và quy tắc EMSA encoding.
- [RFC 8032 – Edwards-Curve Digital Signature Algorithm (EdDSA)](https://www.rfc-editor.org/rfc/rfc8032): Ed25519, Ed25519ph và quy định prehash/domain separation.
- [RFC 8785 – JSON Canonicalization Scheme (JCS)](https://www.rfc-editor.org/rfc/rfc8785): một cách xác định canonical JSON, kèm phạm vi áp dụng và ràng buộc biểu diễn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="digital-signature-007">9.7 Định dạng chữ ký ECDSA DER và P1363 khi trao đổi với hệ thống khác</a>

<details>
<summary>Xem chi tiết</summary>

# 9.7 Định dạng chữ ký ECDSA DER và P1363 khi trao đổi với hệ thống khác

## Mục tiêu học tập

Sau bài này, bạn có thể:

1. Giải thích vì sao cùng thuật toán **ECDSA** và cùng khóa công khai vẫn có thể gặp lỗi xác minh khi hai hệ thống dùng **hai cách mã hóa chữ ký** khác nhau.
2. Phân biệt chữ ký **ASN.1 DER** dạng `SEQUENCE { INTEGER r, INTEGER s }` và **IEEE P1363** dạng byte thô `r || s`, có độ dài cố định.
3. Tính chính xác độ dài P1363 theo **số bit bậc nhóm (curve order)**, kể cả P-521 có độ dài thành phần 66 byte.
4. Dùng **Java 17 `Signature`** để ký/xác minh ở cả hai định dạng; chuyển đổi DER ↔ P1363 có kiểm tra chuẩn tắc.
5. Kết nối đúng với giao thức **JWS/JWT ES256, ES384, ES512**, phân biệt Base64URL và encoding chữ ký.
6. Chẩn đoán các lỗi sai curve, sai hash, khác byte dữ liệu ký, nhầm chữ ký DER với key/certificate DER, sai padding số nguyên và vấn đề low-S.

## 1. ECDSA tạo cặp số (r, s), định dạng là bước tiếp theo

**ECDSA (Elliptic Curve Digital Signature Algorithm)** là sơ đồ chữ ký số trên đường cong elliptic. Người ký dùng **private key**, người kiểm tra dùng **public key**. Sau khi xử lý hash của dữ liệu cần ký và phép toán ECDSA, kết quả logic là **hai số nguyên**:

```text
                 private key d
                      |
Message bytes -> Hash -> ECDSA sign -> (r, s)
                                        |
                                 MÃ HÓA / SERIALIZE
                                  /               \
                                 v                 v
                       ASN.1 DER bytes       P1363 raw bytes
                        SEQUENCE(r,s)           r || s
```

**`(r, s)` mới là ý nghĩa toán học của chữ ký; DER và P1363 là hai cách biến chính cặp số đó thành byte.** Việc đổi định dạng **không thay khóa**, **không đổi curve**, **không đổi hash**, **không tạo một chữ ký mới**. Nếu giải mã và mã hóa lại chính xác, hệ thống bên kia vẫn đang xác minh cùng chữ ký ECDSA.

Một quy trình xác minh thành công còn yêu cầu hai bên thống nhất:

- **Bytes được ký**: đúng từng byte, kể cả UTF-8, xuống dòng, khoảng trắng, JSON canonicalization hay Base64URL signing input.
- **Thuật toán digest**: ví dụ SHA-256 trong `SHA256withECDSA`.
- **Curve và public key**: ví dụ P-256, P-384 hay P-521; không được tráo khóa.
- **Signature encoding**: ASN.1 DER hay IEEE P1363 raw; một số giao thức có thêm wrapper hoặc yêu cầu canonicalization.
- **Chính sách giao thức**: danh sách thuật toán được phép, key ID, chữ ký của đúng đối tượng và các trường ngữ cảnh cần xác thực.

Việc gửi đúng chữ ký với nhãn sai encoding sẽ khiến verifier lỗi hoặc từ chối ngay cả khi dữ liệu, khóa và thuật toán toán học đều đúng.

## 2. Hai định dạng: DER và P1363

| Tiêu chí | ECDSA ASN.1 DER | IEEE P1363 raw |
| --- | --- | --- |
| Cấu trúc | ASN.1 `SEQUENCE` chứa hai `INTEGER` là `r` và `s` | Hai số `r` và `s` nối trực tiếp: `r \|\| s` |
| Biểu diễn số | Số nguyên **có dấu** theo DER (giá trị ECDSA phải dương), big-endian, độ dài tối thiểu | Số nguyên **không dấu**, big-endian, **đệm zero bên trái đủ độ rộng** |
| Byte mở đầu | Thường `0x30` (ASN.1 SEQUENCE) | Byte đầu là dữ liệu của `r`, **không có tag chuẩn** |
| Kích thước | Thay đổi theo giá trị `r`, `s` và byte độ dài ASN.1 | **Cố định theo curve order** |
| Java 17 | `SHA256withECDSA` (định dạng DER tiêu chuẩn của JCA) | `SHA256withECDSAinP1363Format` (nếu provider hỗ trợ) |
| Thực tế trao đổi | Nhiều API crypto, X.509/TLS và công cụ theo truyền thống ASN.1 | JWS/JOSE ES256, ES384, ES512; một số SDK/API theo IEEE P1363 |

Không nên gọi P1363 là “DER không có header”. P1363 yêu cầu **chiều rộng cố định cho cả r và s**, trong khi DER cho phép mỗi INTEGER có số byte thay đổi và đôi khi thêm byte `00` để tránh bị hiểu là số âm. Chỉ cắt các byte `30, 02` không tạo ra P1363 hợp lệ.

### 2.1 DER: hai ASN.1 INTEGER bên trong một SEQUENCE

Trong một chữ ký ECDSA DER thông thường, cấu trúc logic là:

```asn1
Ecdsa-Sig-Value ::= SEQUENCE {
    r INTEGER,
    s INTEGER
}
```

Byte biểu diễn theo **DER (Distinguished Encoding Rules)**:

```text
30 <SEQUENCE length>
   02 <r length> <r bytes, signed-positive, minimal>
   02 <s length> <s bytes, signed-positive, minimal>
```

- `0x30`: tag của ASN.1 SEQUENCE.
- `0x02`: tag của INTEGER.
- Các trường length dùng DER length encoding (short form nếu dưới 128, long form nếu từ 128 trở lên).
- INTEGER dùng big-endian, được mã hóa theo biểu diễn có dấu hai phần bù, **không có các zero thừa**.
- Vì `r, s > 0` trong chữ ký hợp lệ, nếu bit cao nhất của byte có nghĩa đầu tiên bằng `1`, DER **phải thêm một byte `0x00` phía trước** để không biến số dương thành số âm.

Ví dụ chỉ nhằm giải thích **mã hóa**, không phải chữ ký ECDSA thật của một thông điệp/khóa:

```text
r = 1, s = 2

DER:
30 06  02 01 01  02 01 02

30 06       : SEQUENCE, nội dung dài 6 byte
02 01 01    : INTEGER r = 1
02 01 02    : INTEGER s = 2
```

Với một giá trị có byte đầu `0x80` (bit dấu bật), DER INTEGER cần:

```text
Giá trị r = 128 = 0x80
INTEGER DER = 02 02 00 80
                   ^^ byte 00 bắt buộc để giữ số dương
```

Ngược lại, `02 02 00 01` là DER **không chuẩn tắc** vì giá trị 1 chỉ cần `02 01 01`. Khi xử lý dữ liệu không tin cậy, cần từ chối INTEGER âm, độ dài không đúng, zero thừa, dữ liệu dư hay SEQUENCE không chuẩn.

**Không dùng độ dài DER cố định.** Với ECDSA P-256, thường thấy chữ ký quanh 70–72 byte, nhưng còn có thể ngắn hơn do r/s bỏ zero đầu; cần parse theo cấu trúc chứ không suy luận bằng độ dài. Với P-521, phần nội dung SEQUENCE thường vượt 127 byte nên DER dùng **long-form length**, ví dụ dạng `30 81 ...`. Parser chỉ hỗ trợ “một byte length” sẽ hỏng khi gặp curve lớn.

### 2.2 P1363: hai số unsigned với độ dài cố định

Với IEEE P1363:

```text
P1363 = I2OSP(r, width) || I2OSP(s, width)

width = ceil(bitLength(curve_order) / 8)
length(P1363) = 2 * width
```

`I2OSP` ở đây nghĩa là chuyển số nguyên **không dấu** thành đúng `width` byte, big-endian; nếu số có ít byte có nghĩa hơn thì **đệm `00` ở bên trái**. Không thêm ASN.1 tag, length hoặc byte báo dấu. Nửa đầu là `r`, nửa cuối là `s`; **không đảo thứ tự**.

| Curve thường dùng | Số bit của order | Width mỗi phần | Tổng P1363 |
| --- | ---: | ---: | ---: |
| NIST P-256 (`secp256r1`) | 256 | 32 byte | **64 byte** |
| NIST P-384 (`secp384r1`) | 384 | 48 byte | **96 byte** |
| NIST P-521 (`secp521r1`) | 521 | 66 byte | **132 byte** |

Chú ý **P-521 là 521 bit**, nên mỗi số phải dùng `ceil(521/8) = 66` byte, không phải 65. Tổng độ dài chữ ký raw là `132` byte; 7 bit cao chưa dùng của byte đầu vẫn phải được biểu diễn đúng.

Một cặp số minh họa `r=1, s=2` với P-256:

```text
r: 00 00 ... 00 01   (tổng 32 byte)
s: 00 00 ... 00 02   (tổng 32 byte)
=> r || s dài đúng 64 byte
```

Khi chuyển từ DER sang P1363, bỏ byte `00` **dấu dương** của DER nếu có, nhưng sau đó **phải thêm đủ leading zero** để mỗi phần đúng `width`. Khi chuyển ngược lại, bỏ các zero dư của raw, rồi thêm `00` nếu byte cao nhất có bit dấu, mã hóa INTEGER/SEQUENCE DER.

## 3. Vì sao hai hệ thống có thể trả “invalid signature”?

Giả sử Java ký:

```java
Signature signer = Signature.getInstance("SHA256withECDSA");
```

Đây là **mã nhúng** (cần import, khóa và các lệnh `initSign/update/sign`). Trong JDK thông dụng, nó trả **DER-encoded** ECDSA `SEQUENCE(INTEGER r, INTEGER s)`.

Một API khác ghi trong hợp đồng “ECDSA SHA-256, signature is IEEE P1363 r||s”, hoặc yêu cầu **JWS ES256**. Nếu bạn gửi thẳng byte DER cho bên đó, họ nhận khoảng 70 byte thay vì **64 byte**. Hai bên vẫn dùng SHA-256/P-256 nhưng **khác hợp đồng serialization** nên sẽ lỗi.

Chiều ngược lại cũng vậy: đưa raw `r||s` 64 byte cho verifier chờ ASN.1 DER thì parser không tìm thấy `SEQUENCE` và hai `INTEGER`. Tùy provider, Java có thể trả `false` hoặc ném `SignatureException` với lỗi encoding. Không nên bắt exception rồi coi “signature valid” hay mặc định thử cả hai định dạng trên input không được gắn nhãn.

Luồng tích hợp đúng:

```text
Bên A tạo (r, s)
       |
       +--> serialize DER ----> giao thức mong DER
       |
       +--> serialize P1363 --> giao thức mong raw r||s

Bên B đọc theo định dạng đã quy định
       |
       v
Tách r, s và kiểm tra 0 < r,s < order
       |
       v
ECDSA.verify(hash(bytes), publicKey, r, s)
```

Nếu phải chuyển encoding, hãy làm **một lần tại ranh giới hệ thống**, ghi rõ kỳ vọng; lưu test vector và fail closed với input sai thay vì sử dụng heuristic “byte đầu 0x30 thì chắc DER” vì raw P1363 cũng có thể tình cờ bắt đầu `0x30`.

## 4. Java 17 có thể làm trực tiếp cả hai định dạng

Java 17 định nghĩa các tên algorithm gồm:

- `SHA256withECDSA` → chữ ký ECDSA dùng SHA-256, encoding ASN.1 DER thông dụng.
- `SHA256withECDSAinP1363Format` → cùng hashing/ECDSA, encoding IEEE P1363.
- Tương ứng `SHA384withECDSA` / `SHA384withECDSAinP1363Format` cho P-384.
- Tương ứng `SHA512withECDSA` / `SHA512withECDSAinP1363Format` cho P-521.

**Tên trong Java không tự buộc dùng đúng curve**. Ví dụ `SHA256withECDSA` mô tả digest/ECDSA, còn curve đến từ **EC key**. Giao thức ES256 lại yêu cầu **P-256 + SHA-256**, nên phải kiểm tra cả curve lẫn algorithm; không chỉ nhìn tên.

Các thuật toán có trong danh sách **standard algorithm names** và được SunEC cung cấp trên nhiều JDK; khả năng sẵn có phụ thuộc provider/cấu hình runtime. Ưu tiên `Signature.getInstance(name)` và kiểm tra khả năng hỗ trợ khi khởi động, tránh gắn chặt provider name nếu không cần.

### 4.1 Thực hành 1: tạo chữ ký DER và P1363, xác minh bằng API tương ứng

Tạo file **`EcdsaSignatureFormatsDemo.java`** (Java 17 trở lên):

```java
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;

public class EcdsaSignatureFormatsDemo {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair();
        byte[] message = "document:v1".getBytes(StandardCharsets.UTF_8);

        Signature derSigner = Signature.getInstance("SHA256withECDSA");
        derSigner.initSign(pair.getPrivate());
        derSigner.update(message);
        byte[] der = derSigner.sign();

        Signature p1363Signer =
                Signature.getInstance("SHA256withECDSAinP1363Format");
        p1363Signer.initSign(pair.getPrivate());
        p1363Signer.update(message);
        byte[] p1363 = p1363Signer.sign();

        Signature derVerifier = Signature.getInstance("SHA256withECDSA");
        derVerifier.initVerify(pair.getPublic());
        derVerifier.update(message);

        Signature rawVerifier =
                Signature.getInstance("SHA256withECDSAinP1363Format");
        rawVerifier.initVerify(pair.getPublic());
        rawVerifier.update(message);

        int componentBytes =
                ((((ECPublicKey) pair.getPublic()).getParams()
                        .getOrder().bitLength()) + 7) / 8;
        System.out.println("component bytes: " + componentBytes);
        System.out.println("DER starts with SEQUENCE: " +
                (der.length > 0 && (der[0] & 0xff) == 0x30));
        System.out.println("P1363 bytes: " + p1363.length);
        System.out.println("DER verifies: " + derVerifier.verify(der));
        System.out.println("P1363 verifies: " + rawVerifier.verify(p1363));

        Signature changed = Signature.getInstance("SHA256withECDSAinP1363Format");
        changed.initVerify(pair.getPublic());
        changed.update("document:v2".getBytes(StandardCharsets.UTF_8));
        System.out.println("changed message verifies: " + changed.verify(p1363));
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 EcdsaSignatureFormatsDemo.java
java EcdsaSignatureFormatsDemo
```

Đầu ra đã kiểm tra:

```text
component bytes: 32
DER starts with SEQUENCE: true
P1363 bytes: 64
DER verifies: true
P1363 verifies: true
changed message verifies: false
```

Giải thích:

1. `KeyPairGenerator("EC")` tạo khóa với curve `secp256r1` (NIST P-256); mỗi ECDSA signature dùng cùng cặp khóa nhưng được ký **riêng hai lần**.
2. `SHA256withECDSA` trả DER; `SHA256withECDSAinP1363Format` trả raw fixed-width. `der[0]==0x30` là **quan sát cấu trúc trong ví dụ**, không phải quy tắc nhận diện định dạng từ dữ liệu bất kỳ.
3. `ECPublicKey.getParams().getOrder().bitLength()` cho biết chiều rộng thành phần từ **group order**. P-256 có 32 byte mỗi phần, nên raw dài 64.
4. Mỗi verifier nhận đúng encoding mà `Signature.getInstance(...)` của nó yêu cầu, nên xác minh thành công.
5. Khi bytes thông điệp thay đổi từ `document:v1` sang `document:v2`, chữ ký không còn hợp lệ.

**Hai chữ ký ký riêng có thể khác nhau về `r, s` do giá trị nonce nội bộ ECDSA.** Bài này không so sánh trực tiếp mảng DER với mảng P1363 của hai lần ký độc lập; muốn chứng minh conversion bảo toàn *cùng một* chữ ký, dùng thực hành tiếp theo.

## 5. Chuyển DER ↔ P1363 một cách đúng đắn

Trong ứng dụng, ưu tiên chọn **đúng format ngay khi gọi `Signature`**. Nếu provider hoặc SDK chỉ trả định dạng còn lại, conversion tại ranh giới API là hợp lý, nhưng **DER là một định dạng có quy tắc canonical nghiêm ngặt**.

### 5.1 Quy trình DER → P1363

1. Đọc một ASN.1 SEQUENCE tag `0x30` và length **đúng**, không có trailing bytes.
2. Trong SEQUENCE phải có **đúng hai** INTEGER theo thứ tự `r, s`, không thêm trường.
3. DER INTEGER phải dương, biểu diễn tối thiểu, không có dấu âm hay prefix `00` dư.
4. Kiểm tra các scalar `1 <= r,s < n`, trong đó `n` là bậc nhóm EC lấy từ curve của public key.
5. Chuyển mỗi số sang unsigned big-endian với đúng `ceil(bitLength(n)/8)` byte. Thêm zero ở **bên trái** nếu số ít byte có nghĩa.
6. Ghép hai mảng không có separator và giữ đúng thứ tự `r||s`.

### 5.2 Quy trình P1363 → DER

1. Yêu cầu **chính xác** `2 * width` byte; từ chối raw bị cắt hoặc thừa byte.
2. Chia thành `rBytes` và `sBytes`, mỗi phần dài `width`.
3. Tạo `BigInteger(1, bytes)` để đọc **unsigned**; nếu dùng `new BigInteger(bytes)` không có `signum=1` thì các giá trị bắt đầu bằng bit `1` có thể bị hiểu thành số âm.
4. Kiểm tra `1 <= r,s < n` theo curve thực.
5. Chuyển từng số thành DER INTEGER tối thiểu: loại zero dư, **thêm một byte `00`** nếu cần giữ dấu dương.
6. Đóng gói hai INTEGER vào một SEQUENCE; dùng DER short/long length đúng quy tắc.

**Không ghép/xóa byte theo offset cố định kiểu “`DER.substring(4)`”**. DER P-256 có thể chứa số byte khác nhau; P-521 còn có trường length long-form. Parser cần chống DER không chuẩn tắc, length overflow, trailing data, INTEGER âm/zero, thừa zero và scalar vượt bậc nhóm. Việc tuân thủ canonical encoding còn giúp các hệ thống khác nhau không diễn giải cùng một giá trị theo nhiều dạng byte tùy tiện.

### 5.3 Thực hành 2: bộ chuyển định dạng tối giản có kiểm tra chuẩn tắc

Ví dụ sau là **một chương trình Java 17 độc lập**. Nó:

- Parse DER theo quy tắc tối thiểu cần thiết cho ECDSA `SEQUENCE(INTEGER r, INTEGER s)`.
- Mã hóa DER length ở cả short và long form, hoạt động với P-521.
- Kiểm tra r/s trong miền hợp lệ của group order thay vì chỉ dựa vào số bit.
- Dùng `BigInteger(1, bytes)` cho raw và xử lý đúng zero dành cho dấu DER.
- Ký một lần bằng DER, chuyển sang P1363, chuyển về DER, rồi kiểm chữ ký bằng **cả hai API Java**.
- Từ chối một DER INTEGER có leading `00` không cần thiết.

**Giới hạn:** đây là codec học tập cho chữ ký ECDSA, không thay một ASN.1/cryptography library đã được audit khi xử lý các giao thức nhiều cấu trúc phức tạp. Không dùng `sun.security.util.*` hoặc reflection vào JDK internals.

Tạo file **`EcdsaDerP1363Bridge.java`**:

```java
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;

public class EcdsaDerP1363Bridge {
    private static int width(BigInteger order) {
        return (order.bitLength() + 7) / 8;
    }

    private static void checkScalar(BigInteger x, BigInteger order) {
        if (x.signum() <= 0 || x.compareTo(order) >= 0) {
            throw new IllegalArgumentException("ECDSA scalar outside (0, n)");
        }
    }

    private static void requireTag(byte[] in, int[] pos, int end, int tag) {
        if (pos[0] >= end || (in[pos[0]++] & 0xff) != tag) {
            throw new IllegalArgumentException("Wrong DER tag");
        }
    }

    private static int readLength(byte[] in, int[] pos, int end) {
        if (pos[0] >= end) throw new IllegalArgumentException("Missing length");
        int first = in[pos[0]++] & 0xff;
        if (first < 128) return first;
        int count = first & 0x7f;
        if (count == 0 || count > 4 || count > end - pos[0]) {
            throw new IllegalArgumentException("Invalid DER length");
        }
        if (in[pos[0]] == 0) throw new IllegalArgumentException("Non-minimal length");
        long length = 0;
        for (int i = 0; i < count; i++) {
            length = (length << 8) | (in[pos[0]++] & 0xff);
        }
        if (length < 128 || length > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid long length");
        }
        return (int) length;
    }

    private static BigInteger readInteger(byte[] in, int[] pos, int end) {
        requireTag(in, pos, end, 0x02);
        int length = readLength(in, pos, end);
        if (length < 1 || length > end - pos[0]) {
            throw new IllegalArgumentException("Invalid INTEGER length");
        }
        int first = in[pos[0]] & 0xff;
        if ((first & 0x80) != 0) {
            throw new IllegalArgumentException("Negative INTEGER");
        }
        if (length > 1 && first == 0 &&
                (in[pos[0] + 1] & 0x80) == 0) {
            throw new IllegalArgumentException("Redundant 00 prefix");
        }
        BigInteger out = new BigInteger(
                1, Arrays.copyOfRange(in, pos[0], pos[0] + length));
        pos[0] += length;
        return out;
    }

    private static byte[] fixed(BigInteger n, int width) {
        byte[] bytes = n.toByteArray();
        int start = bytes.length > 1 && bytes[0] == 0 ? 1 : 0;
        int bytesToCopy = bytes.length - start;
        if (bytesToCopy > width) {
            throw new IllegalArgumentException("Scalar too wide");
        }
        byte[] result = new byte[width];
        System.arraycopy(bytes, start, result, width - bytesToCopy, bytesToCopy);
        return result;
    }

    public static byte[] derToP1363(byte[] der, BigInteger order) {
        int[] pos = {0};
        requireTag(der, pos, der.length, 0x30);
        int sequenceLength = readLength(der, pos, der.length);
        if (sequenceLength != der.length - pos[0]) {
            throw new IllegalArgumentException("Trailing data or bad SEQUENCE length");
        }
        BigInteger r = readInteger(der, pos, der.length);
        BigInteger s = readInteger(der, pos, der.length);
        if (pos[0] != der.length) {
            throw new IllegalArgumentException("Wrong SEQUENCE contents");
        }
        checkScalar(r, order);
        checkScalar(s, order);
        int width = width(order);
        byte[] result = new byte[2 * width];
        System.arraycopy(fixed(r, width), 0, result, 0, width);
        System.arraycopy(fixed(s, width), 0, result, width, width);
        return result;
    }

    private static byte[] derLength(int length) {
        if (length < 128) return new byte[] {(byte) length};
        int count = 0;
        for (int n = length; n != 0; n >>>= 8) count++;
        byte[] bytes = new byte[1 + count];
        bytes[0] = (byte) (0x80 | count);
        for (int i = count; i > 0; i--) {
            bytes[i] = (byte) length;
            length >>>= 8;
        }
        return bytes;
    }

    private static byte[] tlv(int tag, byte[] value) {
        byte[] len = derLength(value.length);
        byte[] result = new byte[1 + len.length + value.length];
        result[0] = (byte) tag;
        System.arraycopy(len, 0, result, 1, len.length);
        System.arraycopy(value, 0, result, 1 + len.length, value.length);
        return result;
    }

    public static byte[] p1363ToDer(byte[] raw, BigInteger order) {
        int width = width(order);
        if (raw.length != width * 2) {
            throw new IllegalArgumentException("P1363 must be 2*width bytes");
        }
        BigInteger r = new BigInteger(1, Arrays.copyOfRange(raw, 0, width));
        BigInteger s = new BigInteger(1, Arrays.copyOfRange(raw, width, 2 * width));
        checkScalar(r, order);
        checkScalar(s, order);
        byte[] ri = tlv(0x02, r.toByteArray());
        byte[] si = tlv(0x02, s.toByteArray());
        byte[] both = new byte[ri.length + si.length];
        System.arraycopy(ri, 0, both, 0, ri.length);
        System.arraycopy(si, 0, both, ri.length, si.length);
        return tlv(0x30, both);
    }

    public static void main(String[] args) throws Exception {
        String[] curves = {"secp256r1", "secp384r1", "secp521r1"};
        String[] hashes = {"SHA256", "SHA384", "SHA512"};
        byte[] data = "signed-record-v1".getBytes(StandardCharsets.UTF_8);

        for (int i = 0; i < curves.length; i++) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec(curves[i]));
            KeyPair pair = generator.generateKeyPair();
            BigInteger order = ((ECPublicKey) pair.getPublic()).getParams().getOrder();

            Signature signer = Signature.getInstance(hashes[i] + "withECDSA");
            signer.initSign(pair.getPrivate());
            signer.update(data);
            byte[] der = signer.sign();

            byte[] raw = derToP1363(der, order);
            byte[] derAgain = p1363ToDer(raw, order);

            Signature verifyRaw =
                    Signature.getInstance(hashes[i] + "withECDSAinP1363Format");
            verifyRaw.initVerify(pair.getPublic());
            verifyRaw.update(data);

            Signature verifyDer =
                    Signature.getInstance(hashes[i] + "withECDSA");
            verifyDer.initVerify(pair.getPublic());
            verifyDer.update(data);

            System.out.println(curves[i] + ": P1363=" + raw.length +
                    ", both verify=" + (verifyRaw.verify(raw) && verifyDer.verify(derAgain)) +
                    ", DER exact=" + Arrays.equals(der, derAgain));
        }

        // Chỉ dùng để test DER parser; đây không phải curve order sản xuất.
        BigInteger exampleOrder = BigInteger.ONE.shiftLeft(256)
                .subtract(BigInteger.valueOf(189));
        byte[] malformed = {0x30, 0x07, 0x02, 0x02, 0x00, 0x01,
                0x02, 0x01, 0x01};
        boolean invalidDerRejected;
        try {
            derToP1363(malformed, exampleOrder);
            invalidDerRejected = false;
        } catch (IllegalArgumentException expected) {
            invalidDerRejected = true;
        }
        System.out.println("non-minimal DER rejected: " + invalidDerRejected);
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 EcdsaDerP1363Bridge.java
java EcdsaDerP1363Bridge
```

Đầu ra đã kiểm tra với provider hỗ trợ P-256/P-384/P-521 và các tên JCA tương ứng:

```text
secp256r1: P1363=64, both verify=true, DER exact=true
secp384r1: P1363=96, both verify=true, DER exact=true
secp521r1: P1363=132, both verify=true, DER exact=true
non-minimal DER rejected: true
```

**Giải thích các kết quả:**

- Mỗi curve tạo **một chữ ký DER**, sau đó chuyển byte của **chính cặp `r,s` đó** sang P1363; verifier P1363 chấp nhận chữ ký, chứng minh không cần ký lại.
- Chuyển ngược P1363 → DER và so sánh `Arrays.equals(der, derAgain)` trả `true`, vì DER chuẩn tắc có biểu diễn duy nhất cho cặp số này.
- P-521 có `width=66` nên P1363 132 byte; codec xử lý DER long-form length thay vì giả định một byte length.
- Kiểm tra `malformed` sử dụng một `INTEGER 00 01` với leading zero **không cần thiết**. Parser từ chối; biến `exampleOrder` chỉ tạo miền đủ lớn cho **test cú pháp**, hoàn toàn không dùng làm khóa/curve order cho ký thật.
- Các kết quả boolean là đầu ra xác định khi môi trường có thuật toán; byte DER thực tế và chiều dài DER có thể thay đổi từ lần ký này sang lần khác.

**Không thay converter bằng một phép chia chuỗi theo số byte DER cố định.** Ngoài việc parse đúng, sản phẩm cần test parser với dữ liệu sai cố ý và ràng buộc kích thước đầu vào để ngăn xử lý ASN.1 không an toàn. Khi dùng thư viện ASN.1 chuyên dụng, vẫn cần kiểm soát **canonical DER** và **range của r/s**, chứ không chỉ gọi parse để được hai BigInteger.

## 6. JWS/JWT ES256, ES384, ES512 dùng P1363 raw

Đây là nguồn sai lệch định dạng phổ biến khi ứng dụng Java giao tiếp với SDK/identity provider. Trong **JSON Web Signature (JWS)**, các giá trị:

| JOSE `alg` | Curve bắt buộc | Hash | Chữ ký JWS (trước Base64URL) | Signature segment Base64URL không padding |
| --- | --- | --- | ---: | ---: |
| **ES256** | P-256 | SHA-256 | **64 byte** | 86 ký tự |
| **ES384** | P-384 | SHA-384 | **96 byte** | 128 ký tự |
| **ES512** | P-521 | SHA-512 | **132 byte** | 176 ký tự |

**ES512** không có nghĩa “curve P-512”; nó dùng **P-521** và **SHA-512** theo RFC 7518. Chữ ký JWS được định nghĩa là `r||s` cố định, không phải DER. Vì thế cách làm:

```text
Base64URL(ASN1_DER_ECDSA_SIGNATURE)
```

**không** tạo JWS ES256 hợp lệ, dù nhìn bên ngoài giống một đoạn Base64URL. Phải lấy raw 64 byte `r||s` (hoặc chuyển DER → raw đúng cách) rồi mới Base64URL không padding.

### 6.1 Dữ liệu được ký trong JWS là “signing input”

Một JWS Compact có ba segment:

```text
base64url(header).base64url(payload).base64url(raw_ECDSA_signature)
```

Signature được tạo trên **ASCII bytes của chuỗi**:

```text
base64url(header) + "." + base64url(payload)
```

chứ không phải ký nguyên văn JSON payload riêng lẻ. Hai đối tượng JSON tương đương ngữ nghĩa nhưng khác thứ tự trường, dấu cách, cách escape hoặc cách Base64URL có thể tạo **signing input khác**.

Việc cấu hình `"alg": "ES256"` trong header không tự chứng minh có thể tin header đó. Khi xác minh JWS, server phải xác định **các thuật toán được phép, khóa tin cậy, issuer/audience/expiry và ngữ cảnh ứng dụng** theo chính sách, không để attacker chọn thuật toán hoặc khóa chỉ bằng các trường header không tin cậy.

### 6.2 Thực hành 3: Java 17 ký đúng JWS ES256 raw P1363

Ví dụ tạo **một signing input JWS** tối giản, ký bằng `SHA256withECDSAinP1363Format` và xác minh đúng bytes. Đây là demo về **encoding và Signature API**, chưa phải một thư viện JWT và không xử lý claims, trust, issuer hoặc expiry cho đăng nhập thực tế.

Tạo **`EcdsaJwsEs256Demo.java`**:

```java
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

public class EcdsaJwsEs256Demo {
    private static String b64url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair();

        String header = b64url(
                "{\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String payload = b64url(
                "{\"sub\":\"demo\"}".getBytes(StandardCharsets.UTF_8));
        byte[] signingInput = (header + "." + payload)
                .getBytes(StandardCharsets.US_ASCII);

        Signature signer =
                Signature.getInstance("SHA256withECDSAinP1363Format");
        signer.initSign(pair.getPrivate());
        signer.update(signingInput);
        byte[] raw = signer.sign();
        String signatureSegment = b64url(raw);

        Signature verify = Signature.getInstance("SHA256withECDSAinP1363Format");
        verify.initVerify(pair.getPublic());
        verify.update(signingInput);
        boolean valid = verify.verify(Base64.getUrlDecoder().decode(signatureSegment));

        Signature changed = Signature.getInstance("SHA256withECDSAinP1363Format");
        changed.initVerify(pair.getPublic());
        changed.update((header + "." + b64url(
                "{\"sub\":\"changed\"}".getBytes(StandardCharsets.UTF_8)))
                .getBytes(StandardCharsets.US_ASCII));
        boolean tamperedValid = changed.verify(raw);

        System.out.println("signature bytes: " + raw.length);
        System.out.println("signature segment chars: " + signatureSegment.length());
        System.out.println("correct input verifies: " + valid);
        System.out.println("changed input verifies: " + tamperedValid);
    }
}
```

Lệnh chạy:

```bash
javac --release 17 EcdsaJwsEs256Demo.java
java EcdsaJwsEs256Demo
```

Đầu ra đã kiểm tra:

```text
signature bytes: 64
signature segment chars: 86
correct input verifies: true
changed input verifies: false
```

Chuỗi `signatureSegment` thực tế thay đổi vì `r,s` thay đổi, nhưng **raw luôn dài 64 byte**, và Base64URL không padding từ 64 byte luôn dài **86 ký tự**. Cấu trúc ký đúng signing input với dấu chấm phân tách hai đoạn mã hóa; sửa payload làm xác minh thất bại.

Trong hệ thống thật, sử dụng **thư viện JOSE/JWT có kiểm thử và chính sách thuật toán/khóa rõ ràng**. Đừng tự viết JWT authorization bằng cách nối chuỗi từ ví dụ này. Ở đây, trọng tâm là: **JOSE ECDSA signature segment = Base64URL(P1363 raw)**.

## 7. ASN.1 DER của chữ ký không phải mọi loại “DER”

Trong Java và PKI, từ “DER” xuất hiện ở nhiều nơi:

| Đối tượng | Ví dụ Java / cấu trúc | Nội dung |
| --- | --- | --- |
| **ECDSA signature value** | `SEQUENCE { INTEGER r, INTEGER s }` | Chỉ hai số của chữ ký |
| **X.509 certificate** | Cấu trúc `Certificate` DER | Chứa TBS certificate, thuật toán ký, `signatureValue` và nhiều trường |
| **X.509 public key (SPKI)** | `publicKey.getEncoded()`, thường `X.509` | Chứa thuật toán/curve và bitstring public key, **không phải chữ ký** |
| **PKCS#8 private key** | `privateKey.getEncoded()` (nếu exportable) | Cấu trúc khóa riêng được mã hóa ASN.1, **không phải chữ ký** |
| **PEM** | Base64 bao quanh DER/khóa/chứng chỉ kèm marker | Cách bọc nội dung, không xác định DER vs P1363 của một chữ ký API |

Ví dụ, khi X.509 certificate mang ECDSA signature, `signatureValue` là **BIT STRING chứa encoding DER của ECDSA-Sig-Value**. Đừng lấy toàn bộ certificate DER hoặc toàn bộ BIT STRING wrapper rồi đưa nguyên cho `Signature.verify(...)` vốn đang chờ chỉ **signature bytes**.

Tương tự, gọi `publicKey.getEncoded()` cho X.509/SPKI chỉ cho byte mã hóa **key**, không có ý nghĩa “signature được mã hóa X.509”. Việc parse signature và việc parse public key phải dùng đúng API khác nhau.

## 8. Curve order, r/s hợp lệ và vấn đề high-S/low-S

### 8.1 Vì sao dùng `order` thay vì độ dài key file?

ECDSA hoạt động trên nhóm điểm có bậc `n`. Hai số chữ ký phải nằm trong miền:

```text
1 <= r < n
1 <= s < n
```

Trong Java, với `ECPublicKey`, đọc:

```java
BigInteger order = ((ECPublicKey) publicKey)
        .getParams().getOrder();
int width = (order.bitLength() + 7) / 8;
```

Đây là **đoạn mã nhúng** trong hàm đã có biến `publicKey` và import `ECPublicKey`/`BigInteger`. Độ dài P1363 được tính bằng **order bit length**, không phải độ dài DER của key hay hash. Với các curve P thường dùng, width trùng với độ dài thường gặp của tọa độ đường cong, nhưng quy tắc đúng là theo miền số `r/s`.

Nếu API đối tác gửi một P1363 raw dài 64 byte nhưng yêu cầu P-384, đừng “bổ sung một ít zero” để ép dùng: phải từ chối sai độ dài và xác nhận rõ curve mà giao thức quy định.

### 8.2 DER canonical encoding khác low-S canonicalization

ECDSA có một dạng **signature malleability**: với chữ ký hợp lệ `(r,s)` theo công thức chuẩn, `(r, n-s)` cũng có thể xác minh thành công, khi `s` nằm trong miền hợp lệ. Một số hệ sinh thái yêu cầu **low-S**, thường `s <= n/2`, nhằm chọn một đại diện duy nhất cho một lớp tương đương toán học.

Cần phân biệt:

- **Canonical DER**: mỗi cặp số `(r,s)` có **một encoding DER chuẩn tắc** (không zero thừa, không length bất chuẩn).
- **Low-S policy**: một ràng buộc **trên giá trị `s`**, không phải cách đặt tag/length của DER.
- **DER ↔ P1363 conversion**: chỉ đổi biểu diễn của **cùng `r,s`**; không tự đổi `s` thành `n-s`.

Đừng tự “normalize low-S” trong bộ chuyển nếu giao thức không quy định. Việc đổi `s` tạo **giá trị chữ ký khác**, dù có thể xác minh cùng message/key; nó có thể ảnh hưởng đối chiếu byte, key attestation, transaction ID hoặc quy tắc canonical hóa của hệ thống khác. Nếu giao thức yêu cầu low-S, xử lý có chủ đích và kiểm thử bằng test vector chính thức.

**Không tái sử dụng nonce bí mật `k` khi ký ECDSA.** Đó là lỗi an toàn khóa riêng nghiêm trọng, độc lập với DER hay P1363. Để crypto provider đáng tin cậy quản lý randomness/deterministic signing; không tự đặt `k` qua ứng dụng. RFC 6979 mô tả deterministic ECDSA; một chữ ký sử dụng deterministic `k` vẫn có thể mã hóa dưới DER hoặc P1363.

## 9. Checklist tích hợp hai hệ thống

Khi một đội Java nhận lỗi “signature invalid” từ hệ thống khác, hỏi theo thứ tự:

1. **Định dạng bytes?** Hợp đồng nhận DER `SEQUENCE(INTEGER r,s)` hay raw `r||s`? Nếu là JOSE ES256, phải raw 64 byte **trước** Base64URL.
2. **Curve?** P-256/P-384/P-521 có đúng không? Kiểm tra key thực tế, không chỉ tên algorithm Java.
3. **Hash?** `SHA256withECDSA` hay SHA384/SHA512 theo giao thức? Không tự hash trước rồi lại đưa vào API có tích hợp hashing.
4. **Dữ liệu ký có trùng byte-for-byte?** JSON whitespace, UTF-8, newline `LF/CRLF`, thứ tự trường, compression, encoding của JWS signing input.
5. **Độ dài raw?** 64/96/132 byte tùy curve; kiểm thử trước khi gọi verify.
6. **DER parser chấp nhận canonical encoding không?** Không có INTEGER âm, leading zero thừa, trailing data, length sai, r/s ngoài miền hợp lệ.
7. **Base64 hay Base64URL?** JWS dùng URL-safe alphabet `-` và `_`, thường không padding; không nhập nhằng với hex/PEM.
8. **Đúng public key?** Kiểm tra key ID, nguồn tin cậy, chuỗi chứng chỉ/metadata/permission; không tin khóa do payload không đáng tin đề xuất.
9. **Chính sách low-S?** Nếu một bên từ chối high-S, phải biết đó là **quy tắc giao thức**, không phải DER parser sai.
10. **Provider/version?** Java 17 support tên P1363 trong chuẩn, nhưng provider đích có cài thuật toán/curve đó không? Log tên thuật toán và provider trong môi trường kiểm thử, không log private key.
11. **Lỗi được xử lý fail closed?** `Signature.verify(...)` có thể trả `false` hoặc ném exception khi encoding sai. Bắt lỗi để **từ chối**, không tự thử nhiều cách không được giao thức cho phép.
12. **Có test vector chia sẻ?** Cả hai bên cần cùng public key, message/signing input, format và signature bytes. Test cả hai chiều, byte đầu có bit dấu, r/s có leading zero, P-521 length long-form và dữ liệu malformed.

### 9.1 Những anti-pattern cần tránh

- **“DER và P1363 đều ECDSA nên byte giống nhau.”** Sai: chúng biểu diễn cùng `r,s` bằng hai cấu trúc khác nhau.
- **“DER P-256 luôn 70/71/72 byte.”** Sai: DER có độ dài biến thiên; không nên hard-code.
- **“Bỏ hai byte đầu DER rồi chia đôi.”** Sai: còn tag INTEGER, length, signed-zero và long-form length.
- **“Cắt leading zero ở raw để chữ ký ngắn hơn.”** Sai: P1363 cần **fixed width**.
- **“Dùng `BigInteger(rawBytes)` là đủ.”** Sai: constructor có dấu có thể đọc số dương thành âm; dùng `BigInteger(1, rawBytes)` và kiểm tra miền.
- **“Chỉ cần `SHA256withECDSA` là ES256.”** Chưa đủ: ES256 buộc **P-256 + SHA-256 + JWS P1363 encoding** và ký đúng signing input.
- **“Base64URL(DER) là P1363.”** Sai: Base64URL chỉ mã hóa văn bản từ byte, không chuyển ASN.1.
- **“Public key trả DER nên chữ ký cũng phải DER.”** Sai: key encoding và signature encoding độc lập.
- **“Đổi format cũng phải ký lại.”** Sai: conversion chính xác không cần private key; chỉ cần `r,s` và curve order để quyết định width/miền.
- **“Low-S là một kiểu DER.”** Sai: low-S giới hạn scalar, DER quy định serialization.
- **“Chữ ký khác byte có nghĩa không hợp lệ.”** ECDSA có thể dùng nonce khác khi ký cùng message/key; hãy **verify**, không chỉ so sánh hai chữ ký sinh ở hai lần ký độc lập.
- **“Verifier lỗi parser nên thử tất cả encodings và chấp nhận kết quả đầu tiên.”** Không nên: phạm vi thuật toán/encoding phải đến từ hợp đồng đáng tin, tránh làm tăng bề mặt diễn giải không mong muốn.

## 10. Bài tập tự kiểm tra

**1.** ECDSA tạo ra hai giá trị nào trước khi serialize?  
**Đáp:** Hai scalar dương `r` và `s` trong miền `1..n-1`.

**2.** Với P-256, một chữ ký P1363 raw dài bao nhiêu?  
**Đáp:** `32 + 32 = 64` byte, dù r/s có bao nhiêu leading zero.

**3.** Vì sao DER có thể thêm byte `00` vào đầu một INTEGER?  
**Đáp:** Để số có bit cao bằng `1` vẫn được đọc là **số dương** theo ASN.1 INTEGER có dấu.

**4.** P-521 dùng mỗi phần `r`/`s` 65 hay 66 byte?  
**Đáp:** `ceil(521/8) = 66` byte; toàn chữ ký raw 132 byte.

**5.** `SHA256withECDSAinP1363Format` khác `SHA256withECDSA` về mặt nào?  
**Đáp:** Chủ yếu **mã hóa output/input chữ ký** P1363 vs DER; cùng primitive hash + ECDSA khi dùng cùng EC key.

**6.** Muốn gửi chữ ký từ Java DER sang JWS ES256, làm thế nào?  
**Đáp:** Parse DER lấy `r,s`, encode hai số unsigned big-endian mỗi số 32 byte, ghép thành raw 64 byte, rồi **Base64URL không padding**.

**7.** Có thể dùng hash SHA-256 với khóa P-384 rồi tự nhận đó là ES384 không?  
**Đáp:** Không. ES384 yêu cầu **P-384 và SHA-384** theo chuẩn JOSE.

**8.** Một verifier có thể phân biệt DER và P1363 chỉ bằng byte đầu `0x30` không?  
**Đáp:** Không đáng tin. P1363 raw cũng có thể bắt đầu `0x30`; phải dựa vào hợp đồng và parse đúng cấu trúc.

**9.** “Canonical DER” có bảo đảm low-S không?  
**Đáp:** Không. DER chuẩn tắc chỉ đảm bảo cách mã hóa duy nhất cho cặp `r,s`; low-S là chính sách giới hạn giá trị `s`.

**10.** Tại sao verify JWS trên raw JSON payload có thể sai?  
**Đáp:** JWS ký trên **ASCII của `base64url(header).base64url(payload)`**, không phải raw JSON.

## Ghi nhớ

Mô hình quan trọng nhất khi tích hợp ECDSA là:

```text
ECDSA math -> (r, s) -> signature encoding -> bytes on wire

ASN.1 DER: SEQUENCE(INTEGER r, INTEGER s), signed-positive/minimal, variable length
IEEE P1363: unsigned fixed-width r || s, length from curve order

Java: SHA256withECDSA  <->  SHA256withECDSAinP1363Format
JWS ES256: P-256 + SHA-256 + raw 64-byte P1363 + Base64URL segment
```

Bằng cách **thống nhất curve, hash, chính xác bytes được ký và encoding chữ ký**, rồi kiểm tra `r,s` và DER canonical tại ranh giới, bạn có thể trao đổi chữ ký giữa Java, thư viện ASN.1 và hệ thống JOSE mà không cần ký lại hoặc thay khóa.

## Nguồn tham khảo chính thức

- [Java 17 — Security Standard Algorithm Names](https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html): các tên `SHA256withECDSA` và `...inP1363Format`, cấu trúc chữ ký Java.
- [Java 17 — Signature API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Signature.html): vòng đời `initSign/update/sign` và `initVerify/update/verify`.
- [Java 17 — Oracle JDK Providers](https://docs.oracle.com/en/java/javase/17/security/oracle-providers.html): phạm vi hỗ trợ SunEC và hành vi provider.
- [RFC 3279 2.2.3 — ECDSA Signature Algorithm](https://www.rfc-editor.org/rfc/rfc3279#section-2.2.3): ECDSA-Sig-Value là DER `SEQUENCE(r INTEGER, s INTEGER)`.
- [RFC 5759 4.2 — ECDSA signatureValue in X.509](https://www.rfc-editor.org/rfc/rfc5759#section-4.2): INTEGER dương/byte `00` và BIT STRING chứa DER chữ ký.
- [RFC 7518 3.4 — ECDSA for JWS](https://www.rfc-editor.org/rfc/rfc7518#section-3.4): ES256/384/512, byte-width P1363 và JWS signature length.
- [RFC 6979 — Deterministic ECDSA](https://www.rfc-editor.org/rfc/rfc6979): cách sinh nonce ký xác định, độc lập với encoding DER/P1363.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="digital-signature-008">9.8 Phân biệt chữ ký sai, dữ liệu sai định dạng và ngoại lệ khi xác minh</a>

<details>
<summary>Xem chi tiết</summary>

# 9.8 Phân biệt chữ ký sai, dữ liệu sai định dạng và ngoại lệ khi xác minh

Khi xác minh chữ ký số trong Java, **“chữ ký không hợp lệ”**, **“dữ liệu đầu vào sai định dạng”** và **“quá trình xác minh phát sinh ngoại lệ”** là những tình huống khác nhau. Chúng có thể dẫn đến cùng quyết định nghiệp vụ là **không chấp nhận yêu cầu**, nhưng khác nhau về nguyên nhân, vị trí lỗi, cách xử lý, khả năng khắc phục và thông tin cần ghi nhận để vận hành.

Điểm cốt lõi: `java.security.Signature.verify(byte[])` có hai kết quả theo hợp đồng API: **`true`/`false`** khi thực hiện xong phép kiểm tra, hoặc **`SignatureException`** khi engine không thể xác minh như bình thường (ví dụ sai trạng thái, chữ ký sai encoding/loại hoặc provider không xử lý được). Trước khi tới `verify` còn có các lớp parse, giải mã Base64, nạp public key và chọn thuật toán, mỗi lớp có ngoại lệ riêng.

Bài này giúp bạn phân loại lỗi đúng tầng, viết nhánh xử lý an toàn và kiểm chứng hành vi bằng Java 17+.

## 1. Đặt đúng câu hỏi: đã đi tới bước xác minh mật mã chưa?

Quy trình nhận một chữ ký từ bên ngoài thường có nhiều bước:

~~~text
Payload / request từ bên ngoài
  |
  v
[1] Validate request schema, giới hạn kích thước, trường bắt buộc
  |  lỗi -> MALFORMED_INPUT (chưa tới Signature)
  v
[2] Decode Base64/Base64URL/hex/PEM theo contract
  |  lỗi -> MALFORMED_ENCODING (chưa tới Signature)
  v
[3] Nạp public key đúng định dạng, kiểm tra nguồn tin cậy
  |  lỗi parse -> INVALID_KEY_FORMAT
  |  lỗi trust/certificate -> TRUST_FAILURE
  v
[4] Chọn algorithm + parameters và gọi Signature.getInstance(...)
  |  lỗi -> ALGORITHM_OR_CONFIGURATION_ERROR
  v
[5] Signature.initVerify(trustedPublicKey)
  |  lỗi -> INVALID_KEY_OR_PROVIDER_ERROR
  v
[6] Signature.update(EXACT_MESSAGE_BYTES)
  |  lỗi state/processing -> SignatureException
  v
[7] Signature.verify(signatureBytes)
  |
  +-- true  -> VALID_CRYPTOGRAPHIC_SIGNATURE
  +-- false -> NOT_VERIFIED
  +-- SignatureException -> VERIFICATION_PROCESSING_ERROR
                               (có thể liên quan format chữ ký)
~~~

**Không có bảo đảm rằng mọi chữ ký sai định dạng đều bị phát hiện trước `verify`.** Một số định dạng chỉ được provider giải mã trong `verify`. Tùy scheme/provider, đầu vào bất thường có thể cho `false` hoặc ném `SignatureException`. Muốn phân biệt format và sai phép toán mật mã với độ tin cậy cao, cần **contract encoding rõ ràng và kiểm tra những điều kiện cấu trúc có thể kiểm tra an toàn trước khi verify**.

Ngược lại, `verify(...) == true` chỉ chứng minh **chữ ký toán học phù hợp với message bytes, public key và bộ tham số đã dùng**. Nó không tự chứng minh public key thực sự thuộc người dùng mong muốn, chứng chỉ còn hiệu lực hay yêu cầu chưa bị phát lại.

## 2. Phân loại ba nhóm kết quả chính

| Nhóm | Ví dụ | Kết quả API điển hình | Ý nghĩa chính xác |
| --- | --- | --- | --- |
| **Chữ ký không khớp** | Sửa nội dung, ký bằng private key của người khác, truyền sai chữ ký hợp lệ về cấu trúc | `Signature.verify(sig) == false` | Phép kiểm tra với **message/key/algorithm/parameters hiện tại** không thành công. |
| **Đầu vào sai định dạng** | Base64 hỏng, JSON/PEM không hợp lệ, public key DER hỏng, chữ ký không đúng representation | `IllegalArgumentException` từ Base64, `InvalidKeySpecException` từ KeyFactory, hoặc `false`/**`SignatureException`** từ Signature | Cần chỉ ra **tầng parse/format** xảy ra lỗi; không gộp tất cả thành `verify == false`. |
| **Ngoại lệ trong quá trình xác minh** | Key không tương thích, engine chưa `initVerify`, thuật toán/provider không có, parameters sai, provider không xử lý được | `InvalidKeyException`, `SignatureException`, `NoSuchAlgorithmException`, `InvalidAlgorithmParameterException`... | Có thể là lỗi cấu hình, input, trạng thái hoặc provider; phân loại theo **API và ngữ cảnh**, không chỉ tên exception. |

**Điểm cần nhớ:** phân loại này mô tả **vị trí và ý nghĩa vận hành**. Không phải ba nhánh loại trừ hoàn toàn ở mức mã nguồn: một chữ ký sai định dạng có thể chính là nguyên nhân khiến `Signature.verify` ném `SignatureException`. Ngoại lệ **không mặc nhiên** chứng minh hệ thống bị lỗi nội bộ; đó cũng có thể là phản ứng hợp lệ trước đầu vào của kẻ tấn công.

### Các câu hỏi chẩn đoán

1. Request có parse được và vượt qua giới hạn kích thước chưa?
2. Chuỗi chữ ký có đúng encoding mà giao thức đã thỏa thuận không?
3. Public key có đúng định dạng, loại khóa và **nguồn tin cậy** không?
4. Engine có được khởi tạo bằng `initVerify` và update đúng byte đã ký không?
5. Với đầu vào đã qua các tầng trên, `verify` trả **`false`** hay phát sinh **exception** nào, tại dòng nào?

## 3. `Signature.verify`: `true`, `false` và `SignatureException`

Chu trình chuẩn khi xác minh:

~~~java
Signature verifier = Signature.getInstance("SHA256withRSA");
verifier.initVerify(trustedPublicKey);
verifier.update(messageBytes);
boolean accepted = verifier.verify(signatureBytes);
~~~

Đây là **đoạn API minh họa**, không chạy độc lập; ví dụ đầy đủ ở mục 5.

- **`true`:** chữ ký phù hợp với **đúng các byte** đã truyền bằng `update`, theo thuật toán và public key đã chọn.
- **`false`:** chữ ký không được xác minh. Đầu vào sai message, sai public key **cùng loại**, chữ ký bị thay đổi hoặc không khớp scheme/parameters có thể dẫn đến `false`.
- **`SignatureException`:** engine gặp lỗi khi xử lý. API nêu các khả năng như engine chưa khởi tạo, signature encoding/loại không hợp lệ hoặc phép xác minh không thể xử lý input. Có thể phải từ chối yêu cầu đồng thời ghi lại lỗi đã được chuẩn hóa ở phía server.

Không xem mọi `SignatureException` là sự cố JVM; cũng **không khẳng định đầu vào có cấu trúc hợp lệ chỉ vì `verify` trả `false`**. Các provider có thể quyết định khác nhau ở biên dữ liệu hỏng.

Ví dụ, RSA 2048-bit có chữ ký dạng byte với độ dài **256 byte** đối với cơ chế chuẩn. Nếu nhận 255 byte, một provider có thể ném `SignatureException` vì sai chiều dài; không được dựa vào việc một provider đã trả exception để kết luận mọi provider sẽ làm như vậy. Một tầng validation có thể kiểm tra độ dài theo public key RSA, nhưng vẫn cần để `Signature` kiểm tra mật mã.

### Phải xác minh trên byte-for-byte cùng thông điệp

~~~text
Ký:       bytes("amount=100", UTF-8) -> signature
Xác minh: bytes("amount=100", UTF-8) -> true (nếu key/scheme khớp)
Xác minh: bytes("amount=900", UTF-8) -> false
Xác minh: bytes("amount=100\n", UTF-8) -> false
~~~

Chữ ký số áp dụng lên **byte**, không áp dụng lên “ý nghĩa logic” của chuỗi. Những thay đổi sau có thể làm chữ ký không khớp dù màn hình hiển thị tương tự:

- JSON đổi thứ tự field, khoảng trắng, cách biểu diễn số hoặc escaping.
- LF và CRLF, UTF-8 và encoding khác.
- Unicode có hai cách biểu diễn tương đương về hiển thị nhưng khác chuỗi byte.
- Dữ liệu đã nén/giải nén, URL-encoding, thay đổi header trong phần thông điệp có ký.
- Xác minh **raw payload** trong khi bên ký đã ký hash/canonical form khác, hoặc ngược lại.

Nếu giao thức yêu cầu canonicalization, hai phía phải dùng **cùng một quy tắc đã chuẩn hóa**. Không tự `trim()`, sắp xếp JSON hoặc chuẩn hóa Unicode khi verify nếu giao thức không quy định: hành động đó có thể làm thay đổi dữ liệu đã ký và gây false hoặc tạo khác biệt ngữ nghĩa khó kiểm soát.

## 4. Kiểm tra input format trước khi vào crypto

### 4.1. Lỗi Base64 và Base64URL

Một giao thức thường gửi chữ ký qua JSON/HTTP dưới dạng văn bản. Phải chọn **một encoding đã thỏa thuận**:

~~~java
// Base64 chuẩn có '+' và '/':
byte[] sigBytes = Base64.getDecoder().decode(signatureText);

// Base64URL có '-' và '_':
byte[] sigUrlBytes = Base64.getUrlDecoder().decode(signatureText);
~~~

Hai dòng trên là hai **lựa chọn khác nhau**, không phải phải gọi cả hai. Nếu text chứa ký tự không hợp lệ, decoder có thể ném `IllegalArgumentException`. Đây là lỗi **input encoding trước xác minh**, không phải `SignatureException`.

Đừng mặc định `Base64.getMimeDecoder()` cho mọi API vì MIME decoder có chính sách bỏ qua một số ký tự ngoài alphabet; nếu giao thức cần encoding chặt chẽ, dùng decoder đúng loại, giới hạn kích thước và cấu trúc trước khi parse. Độ dài chuỗi Base64 không nói được chữ ký hợp lệ về mặt mật mã.

### 4.2. Public key format và certificate format

Một public key RSA được xuất thành DER **SubjectPublicKeyInfo** (SPKI) có thể đọc bằng:

~~~java
byte[] spkiBytes = ...; // Byte DER hợp lệ do nguồn tin cậy cung cấp.
PublicKey publicKey = KeyFactory.getInstance("RSA")
        .generatePublic(new X509EncodedKeySpec(spkiBytes));
~~~

Đây chỉ là mảnh minh họa. Tên `X509EncodedKeySpec` khiến nhiều người nhầm: nó biểu diễn **SPKI public key**, **không phải toàn bộ certificate X.509**. Với certificate X.509 cần `CertificateFactory.getInstance("X.509")` đọc cấu trúc chứng chỉ, sau đó dùng `getPublicKey()` và **xác thực chuỗi tin cậy, hiệu lực, chính sách chứng chỉ, thu hồi** bằng cơ chế phù hợp.

Nếu `spkiBytes` không đúng encoding/algorithm, `KeyFactory.generatePublic` có thể ném **`InvalidKeySpecException`**. Trong khi đó, certificate hỏng có thể ném **`CertificateException`** từ certificate parser. Không gọi những lỗi này là “chữ ký sai” khi chưa có phép `Signature.verify` nào chạy.

### 4.3. Chữ ký nhị phân cũng có định dạng

Không phải mọi chữ ký đều chỉ là mảng byte độ dài tùy ý:

| Signature scheme | Biểu diễn thường gặp qua Java JCA | Hệ quả khi parse/verify |
| --- | --- | --- |
| `SHA256withRSA` (PKCS#1 v1.5) | Chữ ký RSA dạng khối có **độ dài bằng modulus** (ví dụ 256 byte cho RSA-2048) | Chữ ký ngắn/dài bất thường có thể bị từ chối bằng `SignatureException` hoặc kết quả không verify tùy triển khai. |
| `RSASSA-PSS` | Chữ ký RSA cũng có độ dài theo modulus; cần đúng **hash, MGF, saltLength, trailerField** | Khác tham số với phía ký có thể gây fail hoặc lỗi cấu hình. |
| `SHA256withECDSA` | Thường là cấu trúc ASN.1 **DER SEQUENCE của hai INTEGER `r` và `s`** | Chữ ký DER hỏng có thể làm provider báo `SignatureException`; chiều dài DER **không luôn cố định**. |
| `SHA256withECDSAinP1363Format` | Biểu diễn IEEE P1363: byte dạng **`r \|\| s`**, mỗi số có độ rộng cố định | Với P-256, thường 64 byte; **không tương thích byte-for-byte** với DER mặc định. |
| `Ed25519` | Chữ ký Ed25519 thông thường **64 byte** | Đúng chiều dài vẫn chưa chứng minh chữ ký đúng; chỉ verify mới chứng minh được. |

Lưu ý tên thuật toán Java phải khớp với **signature representation**. Ví dụ gọi `SHA256withECDSA` trên một chữ ký `r||s` 64 byte từ thư viện khác thường không hoạt động nếu chưa chuyển định dạng đúng, hoặc dùng đúng thuật toán `...inP1363Format` được provider hỗ trợ. Đừng cắt hoặc thêm byte tùy tiện để “đủ độ dài”.

Đối với RSA chữ ký phải giữ nguyên **byte đầu giá trị 0** nếu có: chuyển qua `BigInteger` rồi serialize tùy tiện có thể làm mất leading zero hoặc tạo byte dấu, khiến đầu vào sai chiều dài.

## 5. Java 17+: phân biệt chữ ký đúng, sai message, sai key và chữ ký bị cắt

Tệp **`SignatureOutcomesDemo.java`** tạo cặp khóa RSA 2048-bit để học, ký chuỗi `"amount=100"`, rồi xác minh các tình huống. Trong ví dụ, **`SHA256withRSA`** là scheme RSA PKCS#1 v1.5 cho mục tiêu quan sát API; với thiết kế chữ ký RSA mới, cân nhắc RSASSA-PSS nếu giao thức cho phép và quy định đầy đủ tham số.

~~~java
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;

public class SignatureOutcomesDemo {
    static boolean check(
            PublicKey key, byte[] data, byte[] signature)
            throws GeneralSecurityException {
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(key);
        verifier.update(data);
        return verifier.verify(signature);
    }

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        KeyPair other = generator.generateKeyPair();

        byte[] data = "amount=100".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(pair.getPrivate());
        signer.update(data);
        byte[] signed = signer.sign();

        System.out.println("Correct signature: " +
                check(pair.getPublic(), data, signed));

        System.out.println("Changed data: " +
                check(pair.getPublic(),
                        "amount=900".getBytes(StandardCharsets.UTF_8),
                        signed));

        System.out.println("Other RSA key: " +
                check(other.getPublic(), data, signed));

        byte[] truncated = Arrays.copyOf(signed, signed.length - 1);
        boolean rejected;
        try {
            rejected = !check(pair.getPublic(), data, truncated);
        } catch (SignatureException expected) {
            rejected = true;
        }
        System.out.println("Truncated signature rejected: " + rejected);
    }
}
~~~

Chạy bằng Java 17+:

~~~bash
javac SignatureOutcomesDemo.java
java SignatureOutcomesDemo
~~~

Stdout:

~~~text
Correct signature: true
Changed data: false
Other RSA key: false
Truncated signature rejected: true
~~~

Phân tích:
1. Đúng **message bytes + public key + scheme + signature** → `true`.
2. Đổi nội dung thành `amount=900`, giữ chữ ký cũ → `false` (chữ ký không chứng minh message mới).
3. Dùng public key RSA khác, vẫn đúng **loại** khóa → thường `false` vì không khớp private key đã ký.
4. Cắt mất một byte signature RSA → **bị từ chối**. Ví dụ chấp nhận **hai cách provider có thể báo từ chối**: `verify` trả `false` hoặc ném `SignatureException`. Dòng stdout cuối kiểm tra **không chấp nhận**, thay vì gán cố định một loại exception cho mọi JVM.

Ở ví dụ trên, bắt `SignatureException` chỉ để minh họa đối với chữ ký **bị cắt trong thử nghiệm**; dịch vụ thật không nên nuốt mọi ngoại lệ bằng `return false` rồi mất khả năng phân biệt lỗi provider hay lập trình. Tầng service cần ghi nhận loại nguyên nhân nội bộ, còn bên ngoài chỉ thấy kết quả từ chối được chuẩn hóa.

## 6. Java 17+: ngoại lệ trước và trong `verify`

Tệp **`VerificationErrorsDemo.java`** lần lượt tạo những lỗi cố ý tại các tầng khác nhau. Các byte hỏng trong ví dụ **chỉ dùng để kiểm thử parser**, không phải khóa/chữ ký hợp lệ.

~~~java
import java.security.*;
import java.security.spec.*;
import java.util.Base64;

public class VerificationErrorsDemo {
    public static void main(String[] args) throws Exception {
        try {
            Base64.getDecoder().decode("not_base64!!!");
            throw new AssertionError("Base64 should have failed");
        } catch (IllegalArgumentException expected) {
            System.out.println("Invalid Base64: IllegalArgumentException");
        }

        try {
            KeyFactory.getInstance("RSA").generatePublic(
                    new X509EncodedKeySpec(new byte[] {0x30, 0x00}));
            throw new AssertionError("Invalid key was accepted");
        } catch (InvalidKeySpecException expected) {
            System.out.println("Invalid public key DER: InvalidKeySpecException");
        }

        KeyPairGenerator ec = KeyPairGenerator.getInstance("EC");
        ec.initialize(new ECGenParameterSpec("secp256r1"));
        PublicKey ecKey = ec.generateKeyPair().getPublic();

        try {
            Signature.getInstance("SHA256withRSA").initVerify(ecKey);
            throw new AssertionError("EC key was accepted for RSA");
        } catch (InvalidKeyException expected) {
            System.out.println("Wrong key type: InvalidKeyException");
        }

        try {
            Signature uninitialized = Signature.getInstance("SHA256withRSA");
            uninitialized.verify(new byte[256]);
            throw new AssertionError("Uninitialized verifier was accepted");
        } catch (SignatureException expected) {
            System.out.println("Uninitialized verifier: SignatureException");
        }

        try {
            Signature.getInstance("NotARealSignatureAlgorithm");
            throw new AssertionError("Unsupported algorithm was accepted");
        } catch (NoSuchAlgorithmException expected) {
            System.out.println("Unknown algorithm: NoSuchAlgorithmException");
        }
    }
}
~~~

~~~bash
javac VerificationErrorsDemo.java
java VerificationErrorsDemo
~~~

Stdout trên JDK có các thuật toán chuẩn RSA và EC P-256:

~~~text
Invalid Base64: IllegalArgumentException
Invalid public key DER: InvalidKeySpecException
Wrong key type: InvalidKeyException
Uninitialized verifier: SignatureException
Unknown algorithm: NoSuchAlgorithmException
~~~

**Điểm khác nhau theo từng dòng:**

- `IllegalArgumentException` ở `Base64.decode`: input vận chuyển sai cú pháp; **chưa hề khởi tạo engine `Signature`**.
- `InvalidKeySpecException` ở `KeyFactory.generatePublic`: byte DER public key không chuyển được thành key hợp lệ; không phải `verify == false`.
- `InvalidKeyException` ở `initVerify`: public key EC không tương thích với scheme `SHA256withRSA`. Nếu cung cấp một RSA public key khác nhưng hợp lệ, `initVerify` có thể thành công và `verify` mới trả `false`.
- `SignatureException` ở `verify` khi chưa `initVerify`: **sai vòng đời API của ứng dụng**, khác với chữ ký mật mã sai thông thường.
- `NoSuchAlgorithmException` tại `Signature.getInstance`: tên thuật toán giả không tồn tại; đây là lỗi chọn engine/configuration, không phải kết quả xác minh.

Không mặc định chính xác *thông điệp* exception của provider; log có thể khác theo JDK. Ví dụ chỉ in **tên lớp lỗi** mà đã biết, tránh khóa vào text implementation-specific.

## 7. Bảng ánh xạ lỗi theo API Java

| Giai đoạn/API | Ngoại lệ/kết quả | Ý nghĩa | Hướng xử lý |
| --- | --- | --- | --- |
| Parse JSON/HTTP/request schema | Exception của thư viện parser; lỗi validation | Dữ liệu request thiếu field, kiểu sai, encoding/size sai | Trả lỗi input; không đưa nội dung chưa hợp lệ xuống JCA. |
| `Base64.Decoder.decode` | `IllegalArgumentException` | Chuỗi Base64 không phù hợp loại decoder đã chọn | Từ chối encoding; giới hạn kích thước để chống abuse. |
| Đọc file/key/cert qua stream | `IOException` | Tệp/transport không đọc được | Phân biệt vấn đề I/O với chữ ký không khớp. |
| `KeyFactory.generatePublic` | `InvalidKeySpecException` | Public key encoded format/algorithm không hợp lệ hoặc không được hỗ trợ | Kiểm tra SPKI/DER, thuật toán, nguồn cấp key. |
| `CertificateFactory.generateCertificate` | `CertificateException` | Chứng chỉ parse không hợp lệ | Kiểm tra định dạng cert, sau đó xử lý trust chain riêng. |
| `Signature.getInstance` | `NoSuchAlgorithmException` | Engine/algorithm không được cung cấp | Kiểm tra allowlist, provider và môi trường. |
| `Signature.getInstance(algorithm, name)` | `NoSuchProviderException` | Tên provider yêu cầu chưa được đăng ký | Kiểm tra cấu hình, không tự fallback nếu policy bắt buộc provider. |
| `Signature.initVerify(publicKey)` | `InvalidKeyException` | Key sai loại, format, tham số hoặc không phù hợp provider | Kiểm tra thuật toán/cặp khóa/trust, phân biệt key cùng loại nhưng sai chủ thể. |
| `Signature.setParameter(spec)` | `InvalidAlgorithmParameterException` | Parameters không phù hợp scheme/provider | Kiểm tra PSS hash/MGF/saltLength và yêu cầu giao thức. |
| `Signature.update(data)` | `SignatureException` | Engine sai trạng thái hoặc không thể xử lý input | Kiểm tra lifecycle, provider, input stream và trạng thái. |
| `Signature.verify(signatureBytes)` | **`true`** | Kiểm tra mật mã thành công | Chỉ chấp nhận sau khi kiểm tra khóa tin cậy, policy, context. |
| `Signature.verify(signatureBytes)` | **`false`** | Chữ ký không được xác minh với input hiện tại | Từ chối; không tự động tuyên bố “định dạng hợp lệ”. |
| `Signature.verify(signatureBytes)` | `SignatureException` | Engine không thể xử lý/verify; có thể do state hoặc signature encoding/type | Từ chối; phân tích có kiểm soát vì nguyên nhân phụ thuộc provider. |
| Các API runtime/provider khác | `ProviderException` hay lỗi không kiểm tra | Có thể biểu thị vấn đề provider/hạ tầng | Từ chối và ghi nhận cảnh báo vận hành; không chuyển sang thuật toán yếu. |

Các ngoại lệ được liệt kê là **điển hình**, không phải danh mục tuyệt đối cho mọi thư viện/protocol. Ví dụ certificate chain có thể parse thành công nhưng **không được tin cậy** vì hết hạn, không phù hợp KeyUsage, thiếu chain, bị thu hồi hoặc hostname không hợp lệ; đó là vấn đề **trust/policy**, không nhất thiết là `CertificateException` ở bước parse ban đầu.

### `initVerify` với certificate khác `CertificateFactory`

`Signature.initVerify(Certificate)` là overload có thể lấy public key từ certificate; với chứng chỉ X.509, API có thể kiểm tra ràng buộc KeyUsage liên quan chữ ký số. Tuy nhiên **nó không tự thay thế** quy trình PKIX path validation, kiểm tra chứng chỉ tin cậy và các ràng buộc của giao thức. Phải kiểm chứng trust **trước khi** coi `verify == true` là bằng chứng danh tính.

## 8. RSASSA-PSS: tham số sai có thể trông giống chữ ký sai

Các scheme ký RSA không thể bị coi là tương đương chỉ vì đều dùng RSA public/private key:

- `SHA256withRSA` thường là RSASSA-PKCS1-v1_5 kết hợp SHA-256.
- `RSASSA-PSS` là scheme RSA-PSS cần xác định **digest**, **MGF**, **salt length** và **trailer field**.
- Hai bên phải thống nhất chính xác algorithm + parameters; nếu không sẽ dẫn đến `false` hoặc lỗi khởi tạo/xử lý tùy nguyên nhân và provider.

### Ví dụ Java 17+: ký/xác minh PSS với parameters thống nhất

Tệp **`PssParameterDemo.java`**:

~~~java
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;

public class PssParameterDemo {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();

        PSSParameterSpec parameters = new PSSParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1);
        byte[] data = "contract=v1".getBytes(StandardCharsets.UTF_8);

        Signature signer = Signature.getInstance("RSASSA-PSS");
        signer.setParameter(parameters);
        signer.initSign(pair.getPrivate());
        signer.update(data);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance("RSASSA-PSS");
        verifier.setParameter(parameters);
        verifier.initVerify(pair.getPublic());
        verifier.update(data);
        System.out.println("PSS with matching parameters: " +
                verifier.verify(signature));
    }
}
~~~

~~~bash
javac PssParameterDemo.java
java PssParameterDemo
~~~

Stdout:

~~~text
PSS with matching parameters: true
~~~

Ví dụ chọn **SHA-256**, `MGF1(SHA-256)`, **salt length 32 byte** và **trailerField 1**. Đây là các tham số **của chữ ký PSS**, không phải salt mật khẩu. Cần chọn tham số theo giao thức, không chỉ vì demo chạy thành công.

Nếu verifier dùng `PSSParameterSpec` khác với signer, đừng tự suy ra chắc chắn `SignatureException` hay `false`: có thể bị từ chối khi `setParameter/initVerify` hoặc đến `verify` tùy loại khác biệt. **Quy tắc vận hành đúng** là xác minh chính xác danh tính scheme/parameters đã được phê duyệt, không fallback tùy tiện giữa RSA-PSS và PKCS#1 v1.5.

Đầu vào `algorithm` hoặc `saltLength` từ request **không nên được tin cậy và áp dụng trực tiếp**: phải tra từ key policy, phiên bản giao thức hoặc allowlist cố định. Điều này ngăn algorithm-confusion/downgrade.

## 9. Có được dùng “chữ ký sai”, “dữ liệu sai định dạng”, “lỗi server” làm ba response HTTP riêng?

**Quyết định bảo mật bên ngoài thường giống nhau:** không chấp nhận thao tác nhạy cảm nếu chưa xác minh thành công. Nhưng hệ thống cần **phân loại nội bộ rõ ràng** để vận hành, thay vì nuốt hết exception.

Một mô hình xử lý có thể là:

| Trạng thái nội bộ | Tầng phát hiện | Hành động |
| --- | --- | --- |
| `VERIFIED` | `verify` trả `true` | Tiếp tục kiểm tra trust, context, expiry, replay và policy trước khi phê duyệt. |
| `NOT_VERIFIED` | `verify` trả `false` | Từ chối; đếm số lần thất bại và giám sát hành vi bất thường. |
| `MALFORMED_REQUEST` | Parser/schema/size | Từ chối dữ liệu; ghi nhận loại lỗi format, không log payload nhạy cảm. |
| `MALFORMED_ENCODING` | Base64/PEM/DER/key parse | Từ chối; chỉ định nội bộ tầng parse đã lỗi. |
| `VERIFICATION_EXCEPTION` | `Signature.update/verify` ném `SignatureException` | Từ chối; lưu thông tin đã chuẩn hóa (stage/algorithm/provider/loại exception) để chẩn đoán. |
| `SECURITY_CONFIGURATION_ERROR` | Algorithm/provider/key/PSS parameters không hợp lệ | Từ chối; cảnh báo cấu hình, không tự downgrade hoặc bỏ kiểm tra chữ ký. |
| `UNTRUSTED_KEY_OR_CONTEXT` | PKI/key registry/policy | Từ chối ngay cả khi phép toán chữ ký có thể trả `true`. |

### Bảo vệ tính riêng tư và tránh lỗi oracle

Bên ngoài có thể trả lỗi chung kiểu **“Yêu cầu xác thực không hợp lệ”**, với status HTTP phù hợp quy ước API, vì không nên đưa thông điệp provider, chi tiết private key, internal key ID hoặc logic kiểm tra cho đối phương để dò cấu hình. Không phải mọi lỗi đều phải giả làm HTTP 401: request sai cú pháp có thể dùng 400; lỗi cấu hình/hạ tầng có thể dùng 5xx. Mấu chốt là **không chấp nhận yêu cầu** và hạn chế tiết lộ thông tin không cần thiết.

Ở log nội bộ, nên ghi có cấu trúc: `stage`, `outcome`, `algorithm` thuộc allowlist, provider, public-key identifier đã được rút gọn hoặc băm, correlation ID, kiểu ngoại lệ và số byte sau kiểm tra size. Không log private key, token, thông điệp chứa dữ liệu cá nhân hay raw chữ ký trong log phổ thông; tránh dùng exception string không được sanitize làm trường log công khai.

Đừng viết đoạn tương đương:

~~~java
try {
    return verifier.verify(signatureBytes);
} catch (Exception ignored) {
    return true; // cực kỳ nguy hiểm: fail-open
}
~~~

Và đừng **silently** chuyển tất cả exception thành `false` mà không còn log/correlation trong dịch vụ cần chẩn đoán. Nhánh sai định dạng, provider configuration và sai mật mã có nguyên nhân khác nhau; dù đều dẫn đến từ chối, chúng cần tín hiệu vận hành riêng.

## 10. Cách viết luồng verify có kiểm tra chính xác theo tầng

Đây là **pseudocode định hướng**, không phải một tệp Java độc lập, vì parser, key registry, schema và trust-policy là thành phần phụ thuộc ứng dụng:

~~~text
1. validateRequestSizeAndSchema(request)
   -> nếu không hợp lệ: MALFORMED_REQUEST

2. algorithmPolicy = resolveApprovedSignatureSuite(request.context)
   -> không lấy scheme nguy hiểm/trống tùy ý từ request

3. signatureBytes = strictDecode(request.signatureText, contract.encoding)
   -> lỗi decode: MALFORMED_ENCODING

4. trustedPublicKey = trustedKeyRegistry.lookup(request.keyId, context)
   -> không thấy/không tin cậy: UNTRUSTED_KEY_OR_CONTEXT
   -> lỗi key format: MALFORMED_KEY_OR_CONFIG

5. validateKnownFormatConstraints(signatureBytes, trustedPublicKey, algorithmPolicy)
   -> nếu có vi phạm rõ ràng: MALFORMED_SIGNATURE

6. messageBytes = exactSignedBytes(request, contract)
   -> canonicalization/encoding theo giao thức, không làm khác bên ký

7. verifier = Signature.getInstance(algorithmPolicy.algorithm)
   -> nếu cần: verifier.setParameter(algorithmPolicy.parameters)
   verifier.initVerify(trustedPublicKey)
   verifier.update(messageBytes)
   result = verifier.verify(signatureBytes)

8. result == true:
     cần kiểm tra thêm trust, expiry, context, anti-replay... rồi mới ACCEPT
   result == false:
     NOT_VERIFIED -> REJECT
   SignatureException:
     VERIFICATION_EXCEPTION -> REJECT và chẩn đoán có kiểm soát
   key/algorithm/provider/parameter exception:
     SECURITY_CONFIGURATION_ERROR -> REJECT + ALERT
~~~

**Lưu ý khi thiết kế format validation:** chỉ kiểm tra các thuộc tính được đảm bảo bởi **format đã chọn**. RSA signature length bằng kích thước modulus theo byte là ví dụ hợp lý; ECDSA DER là cấu trúc có độ dài biến thiên, không được bắt buộc bằng 64 byte. Không viết một parser DER/PKCS#1/EdDSA tự chế để “phòng lỗi” nếu provider/thư viện đã có API kiểm tra được kiểm toán.

**Lưu ý khi dùng `Signature`:** đây là đối tượng có **trạng thái**. Với mỗi phép xác minh, tạo mới hoặc quản lý riêng instance và lifecycle; không chia sẻ tùy tiện cùng đối tượng `Signature` cho nhiều thread hoặc gọi `update` sau khi đã verify mà giả định state luôn giống ban đầu.

## 11. Case study: vì sao cùng “signature invalid” có nhiều nguyên nhân?

### Case A — byte message thay đổi ở đường truyền

Client ký byte UTF-8 của `{"amount":100}`; server serialize lại JSON thành `{"amount":100.0}` rồi verify. Chuỗi byte khác nên `verify` thường trả `false`, dù ý nghĩa nghiệp vụ có thể tương tự. Cách sửa là định nghĩa dữ liệu cần ký (raw bytes, canonical JSON hoặc structured signing protocol) và kiểm thử nhất quán ở cả hai đầu.

### Case B — Base64URL được decode bằng Base64 chuẩn

Chuỗi có dấu `-` hoặc `_` theo Base64URL nhưng server dùng `Base64.getDecoder()`. Decoder chuẩn có thể ném `IllegalArgumentException`; lỗi xảy ra **trước** `Signature`. Cách sửa là đưa encoding vào hợp đồng giao thức, dùng decoder đúng và validate đầu vào.

### Case C — ECDSA ký ở hệ thống khác bằng P1363, Java chờ DER

Bên ký gửi 64 byte `r||s` của P-256; phía Java dùng `Signature.getInstance("SHA256withECDSA")` mặc định mong representation DER. Dữ liệu bị diễn giải sai, dẫn tới `false` hoặc `SignatureException` tùy provider. Cách sửa là thống nhất algorithm-format, dùng `SHA256withECDSAinP1363Format` khi có hỗ trợ hoặc chuyển representation một cách chuẩn xác qua thư viện tin cậy.

### Case D — Public key RSA khác nhưng vẫn hợp lệ

`initVerify(otherRsaPublicKey)` vẫn có thể thành công vì đúng **loại khóa RSA**. Tuy nhiên signature không tương ứng nên `verify` trả `false`. Điều này khác việc đưa **EC public key** vào RSA verifier, nơi `initVerify` thường ném `InvalidKeyException`.

### Case E — Chứng chỉ hết hạn nhưng signature vẫn verify

Một public key lấy từ certificate đã hết hạn vẫn có thể thực hiện phép RSA/ECDSA verify thành công nếu private key tương ứng đã ký đúng. **Signature.verify không thay thế certificate path validation**. Cần tách “cryptographic signature valid” và “certificate trusted & valid for intended purpose”.

### Case F — RSA-PSS sai saltLength/MGF

Client dùng `RSASSA-PSS` với MGF1/SHA-256, salt 32 byte; server cấu hình khác. Tùy provider và điểm lỗi, phương thức có thể trả false hoặc ném exception. Cách sửa là đồng bộ bộ tham số theo giao thức (như mục 8), không tự thử nhiều cấu hình suy đoán nếu chính sách không cho phép.

### Case G — Đổi khóa nhưng chưa cập nhật key ID

Signature đúng với public key cũ; server dùng public key mới vì bỏ qua phiên bản `kid` được xác thực hoặc xử lý key rotation sai. Kết quả `false` không chứng minh client đã cố ý giả mạo. Cần quản lý key ID, trust binding, rotation và thời gian hiệu lực hợp lý.

## 12. Ma trận kiểm thử nên có

| Test | Input | Kết quả dự kiến theo tầng |
| --- | --- | --- |
| Happy path | Đúng signature, public key, message, scheme/params | `verify == true`; sau đó kiểm tra context/trust. |
| Sửa một byte message | Giữ signature cũ, thay message | `verify == false`. |
| Thay public key RSA cùng loại | Dùng key khác cặp | `verify == false`. |
| Cắt một byte RSA signature | Truncated signature bytes | Bị từ chối; `verify == false` hoặc `SignatureException` tùy provider. |
| Hỏng Base64 | Ký tự không thuộc alphabet cho phép | `IllegalArgumentException` ở decoder; chưa gọi verify. |
| DER public key hỏng | Bytes không đúng SPKI | `InvalidKeySpecException` khi load key. |
| EC key vào RSA verifier | `initVerify(ecPublicKey)` | `InvalidKeyException`. |
| Chưa `initVerify` | Gọi `verify` ở trạng thái chưa chuẩn bị | `SignatureException`. |
| Tên algorithm không tồn tại | `Signature.getInstance("NotARealSignatureAlgorithm")` | `NoSuchAlgorithmException`. |
| Parameters PSS thống nhất | Hai bên cùng hash/MGF/salt/trailer | `verify == true`. |
| ECDSA DER/P1363 mismatch | Bên ký và verify dùng representation khác | Từ chối; có thể `false` hoặc `SignatureException`. |
| Certificate không tin cậy/hết hạn | Chữ ký toán học vẫn đúng | **Trust/policy từ chối** dù `verify` có thể `true`. |
| Metadata ký bị thiếu | AAD/context/claims không hợp lệ theo giao thức | Từ chối ở tầng policy/context, không suy từ chữ ký. |
| Input rất lớn | Request hoặc signature quá kích thước | Từ chối sớm, giới hạn bộ nhớ/CPU, không chạy crypto vô hạn. |

Kiểm thử nên chạy trên **provider thực tế** được dùng ở production, vì cách báo lỗi sai encoding, hỗ trợ tham số và tên thông điệp có thể khác giữa các provider. Không viết test bám vào **chuỗi tiếng Anh** của exception, chỉ dựa trên loại lỗi, vị trí và kết quả được contract bảo đảm.

## 13. Những sai lầm thường gặp

| Sai lầm | Vì sao sai | Cách đúng |
| --- | --- | --- |
| “`verify == false` nghĩa là Base64/DER hợp lệ” | Một số provider có thể trả false ngay cả khi chữ ký có vấn đề format. | Validate format có thể kiểm tra trước; coi false là **not verified**. |
| “Mọi signature hỏng luôn ném `SignatureException`” | Không có bảo đảm đồng nhất giữa scheme/provider. | Xử lý cả false và exception; không phụ thuộc một cách báo lỗi. |
| “Mọi `SignatureException` là chữ ký của hacker” | Có thể sai state, provider không xử lý được, lỗi nội bộ. | Ghi stage và context; phân biệt input với hạ tầng. |
| “Base64 error là `SignatureException`” | Decoder chạy **trước** Signature. | Catch `IllegalArgumentException` đúng tầng. |
| “`X509EncodedKeySpec` chứa cả certificate” | Nó biểu diễn public key SPKI, không phải certificate hoàn chỉnh. | Dùng `CertificateFactory` cho chứng chỉ và PKIX validation cho trust. |
| “`verify == true` là đủ xác nhận người gửi” | Chỉ xác nhận quan hệ toán học giữa message/key/signature. | Xác minh public key identity, policy, expiry, anti-replay. |
| Tự thay đổi JSON trước verify | Byte không còn giống dữ liệu đã ký. | Dùng raw/canonical bytes đúng giao thức. |
| RSA và ECDSA dùng cùng encoding chữ ký | Format khác nhau; ECDSA có DER/P1363. | Cố định algorithm + representation, chuyển đổi qua API tin cậy khi cần. |
| Không cố định tham số RSA-PSS | MGF/hash/salt có thể khác nhau giữa hai phía. | Dùng `PSSParameterSpec` rõ ràng, giao thức thống nhất. |
| Bắt `Exception` rồi trả `true` | **Fail-open**, có thể chấp nhận chữ ký chưa verify. | Mọi lỗi phải từ chối; chỉ chấp nhận khi kiểm tra/policy đều thành công. |
| Bắt hết exception rồi không log | Mất bằng chứng để phân biệt payload hỏng với cấu hình server. | Phân loại stage/status và log có kiểm soát; response công khai phù hợp. |
| Tự fallback từ thuật toán mạnh sang yếu | Gây downgrade/algorithm confusion. | Chọn scheme theo allowlist và key policy, xử lý lỗi rõ ràng. |

## 14. Câu hỏi tự kiểm tra

**1.** `Signature.verify` trả `false`. Có thể kết luận chữ ký chắc chắn đúng định dạng DER không?

**Đáp án:** Không. Tùy format/provider, một chữ ký sai định dạng cũng có thể cho false; chỉ kết luận là **không xác minh thành công**.

**2.** Decoder `Base64.getDecoder()` ném `IllegalArgumentException`. Có phải chữ ký RSA không khớp không?

**Đáp án:** Chưa có phép RSA verify nào xảy ra. Đây là lỗi **transport encoding** và cần từ chối ở tầng decode.

**3.** Khi `KeyFactory.generatePublic(new X509EncodedKeySpec(bytes))` ném `InvalidKeySpecException`, có nên thử `Signature.verify` tiếp không?

**Đáp án:** Không, chưa có public key hợp lệ. Phải sửa nguồn/định dạng key và xác minh trust; không thể chạy phép verify với đối tượng khóa chưa tạo được.

**4.** Đưa một EC public key hợp lệ vào `SHA256withRSA.initVerify` sẽ trả false hay exception?

**Đáp án:** Thường ném **`InvalidKeyException` tại `initVerify`** vì sai loại khóa; không phải `verify == false`. Nếu dùng RSA key khác cặp nhưng hợp lệ thì có thể init thành công rồi verify false.

**5.** Chữ ký ECDSA 64 byte theo `r||s` được gửi sang Java `SHA256withECDSA` mặc định. Có đảm bảo verify được không?

**Đáp án:** Không. JCA thông thường mong DER cho tên này; cần thống nhất dạng P1363 hay DER và scheme tương ứng.

**6.** Một signature verify thành công với public key từ certificate tự ký chưa được tin cậy. Có được cấp quyền luôn không?

**Đáp án:** Không. Cần kiểm tra chain/trust/policy/identity và ngữ cảnh nghiệp vụ. `verify == true` chỉ là bằng chứng mật mã.

**7.** `SignatureException` ở `verify` khi chưa `initVerify` nói điều gì?

**Đáp án:** Đối tượng engine được dùng sai lifecycle; cần sửa luồng lập trình, không quy kết lỗi payload.

**8.** Vì sao service không nên trả toàn bộ exception message của JCA cho client?

**Đáp án:** Có thể tiết lộ tên provider, chi tiết scheme/key/validation và tạo các tín hiệu oracle không cần thiết. Client cần kết quả từ chối rõ, log nội bộ mới chứa stage và mã lỗi để chẩn đoán.

**9.** RSA-PSS giữa signer và verifier khác `PSSParameterSpec` nhưng dùng cùng RSA public/private key. Có bảo đảm chữ ký đúng không?

**Đáp án:** Không. Hash/MGF1/salt length/trailer field phải khớp giao thức; tùy lỗi mà provider có thể trả false hoặc ném exception.

## 15. Tài liệu chính thức

- [Java SE 17 — `Signature` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Signature.html) — `initVerify`, `update`, `verify` và các ngoại lệ.
- [Java SE 17 — `SignatureSpi` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/SignatureSpi.html) — hợp đồng `engineVerify`, sai encoding/type và trạng thái engine.
- [Java SE 17 — `KeyFactory` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyFactory.html) — chuyển đổi public key từ key specification.
- [Java SE 17 — `X509EncodedKeySpec` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/spec/X509EncodedKeySpec.html) — DER SubjectPublicKeyInfo, không phải certificate nguyên bản.
- [Java SE 17 — `PSSParameterSpec` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/spec/PSSParameterSpec.html) — bộ tham số RSA-PSS.
- [Java 17 Security Standard Algorithm Names](https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html) — thuật toán Signature, RSA, ECDSA DER và `inP1363Format`.
- [Oracle Java Cryptography Architecture Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-cryptography-architecture-jca-reference-guide.html) — engine, provider và chữ ký số.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="digital-signature-009">9.9 Giới hạn của verify thành công khi khóa công khai chưa được gắn với danh tính tin cậy</a>

<details>
<summary>Xem chi tiết</summary>

# 9.9 Giới hạn của verify thành công khi khóa công khai chưa được gắn với danh tính tin cậy

## Mục tiêu học tập

Sau bài này, bạn sẽ giải thích chính xác ý nghĩa của `Signature.verify(...) == true` trong Java; nhận ra vì sao **chữ ký hợp lệ về mặt toán học chưa đồng nghĩa dữ liệu đến từ người/tổ chức mong đợi**; phân biệt **khóa công khai**, **danh tính**, **chứng chỉ**, **chuỗi tin cậy** và **quyền được phép thực hiện hành động**; mô phỏng lỗi tin vào public key do đối thủ tự gửi; và xây dựng thứ tự xác thực đúng cho thông điệp, API, file cập nhật hoặc JWT.

Bài thuộc **9. Chữ ký số** của Java Advance – security-cryptography. Trọng tâm không phải cách tự triển khai Ed25519/RSA/ECDSA, mà là **giới hạn của bước verify** và cách **ràng buộc khóa xác minh với danh tính tin cậy**. Ví dụ có mã Java 17 chạy độc lập; phần liên quan chứng chỉ/PKIX giải thích các bước kiểm tra ở mức hệ thống, không giả vờ rằng một phương thức `verify` có thể thay thế chúng.

## 1. `verify == true` thực sự nói lên điều gì?

Chữ ký số thường được mô tả qua bốn thành phần:

- `M`: **chính xác dãy byte** thông điệp được ký, không phải “nội dung nhìn có vẻ giống nhau”.
- `S`: dãy byte chữ ký.
- `PK`: public key dùng để kiểm tra chữ ký, ứng với một private key bí mật.
- `algorithm/parameters`: scheme như `Ed25519`, `SHA256withECDSA`, hoặc `RSASSA-PSS` kèm tham số PSS phù hợp.

Phép xác minh có thể ký hiệu:

```text
Verify(algorithm, publicKey, messageBytes, signatureBytes) = true/false
```

Với giả định thuật toán/implementation an toàn và khóa bí mật được bảo vệ, **`true` cho biết chữ ký phù hợp với `PK` và `M` dưới scheme đã chọn**. Nó không tự suy ra rằng `PK` thuộc về “ngân hàng A”, “nhà phát hành phần mềm B”, “tài khoản C” hay “server example.com”.

Hai câu hỏi phải tách biệt:

1. **Cryptographic verification:** “Chữ ký này có hợp lệ cho thông điệp này, theo khóa công khai đang dùng không?”
2. **Identity/trust binding:** “Khóa công khai đó có thực sự được cấp phép đại diện cho đúng bên mà ứng dụng đang tin tưởng, trong đúng vai trò và ngữ cảnh không?”

Chỉ trả lời “có” câu 1 **chưa hoàn thành** câu 2.

Thêm hai tầng nữa thường cần để đưa ra quyết định chấp nhận:

3. **Authorization:** “Bên đã xác thực có được phép thực hiện *hành động này* với *đối tượng này* không?”
4. **Freshness/protocol context:** “Thông điệp có đúng người nhận, mục đích, phiên bản, thời hạn và chưa bị phát lại không?”

Chữ ký hợp lệ không đồng nghĩa “an toàn để thực thi”. Các khái niệm này phải được kiểm tra **kết hợp** trong một policy cụ thể.

### Ví dụ đối chiếu kết luận đúng/sai

| Hiện tượng | Kết luận có căn cứ | Kết luận chưa có căn cứ |
| --- | --- | --- |
| `Signature.verify(signature)` trả `true` | Chữ ký phù hợp byte thông điệp và public key đã cung cấp | “Thông điệp chắc chắn do Alice gửi” |
| Public key nằm trong payload cùng chữ ký | Key tồn tại dưới dạng bytes có thể parse được | Key đó được Alice/CA/đơn vị phát hành phê duyệt |
| Chứng chỉ tự ký `cert.verify(cert.getPublicKey())` thành công | Chữ ký tự ký của certificate hợp lệ theo public key trong chính certificate | Chứng chỉ được một trust anchor của tổ chức tin cậy |
| Chuỗi chứng chỉ được chấp nhận theo một tập trust anchors | Có liên kết chữ ký và ràng buộc đường dẫn phù hợp chính sách kiểm tra được áp dụng | Chứng chỉ nhất thiết thuộc **đúng hostname/tenant/role** ứng dụng mong đợi |
| Chữ ký file update hợp lệ theo key đã pin | Bytes file khớp key update được pin | File chưa bị rollback, phiên bản mới hơn hoặc hành động cài đặt luôn được cho phép |

## 2. Lỗ hổng kinh điển: cho phép người gửi tự chọn khóa verify

Giả sử một API nhận dữ liệu có hình dạng:

```json
{
  "sender": "trusted-publisher",
  "message": "publish-version-42",
  "publicKey": "<public-key-do-nguoi-gui-cung-cap>",
  "signature": "<signature-do-nguoi-gui-cung-cap>"
}
```

Trong cấu trúc minh họa này, `publicKey`, `sender` và `signature` **đều do client điều khiển**. Nếu server thực hiện:

```text
publicKey = parse(request.publicKey)
verified  = Verify(publicKey, request.message, request.signature)
if (verified) accept(request.sender, request.message)
```

thì việc gọi `Verify` có thể đúng về mật mã nhưng quy tắc **chấp nhận** lại sai:

1. Người tấn công sinh cặp khóa mới của chính họ.
2. Họ ký bất kỳ thông điệp họ muốn bằng private key của mình.
3. Họ gửi chữ ký, public key của mình và tự nhận `sender = "trusted-publisher"`.
4. Bên nhận parse khóa do họ cung cấp, gọi `verify` và nhận `true`.
5. Ứng dụng **nhầm khóa của attacker thành khóa của nhà phát hành**.

Không hề cần phá Ed25519, RSA hay ECDSA. Điểm bị phá nằm ở **việc xác định public key được tin cậy**, không phải trong phép kiểm tra chữ ký.

**Quy tắc cốt lõi:** Public key có thể được chuyển qua mạng, nhưng **nguồn và quyền sử dụng** của public key phải được xác thực bằng một kênh/quy trình tin cậy độc lập. Trường `sender` chỉ là **claim** chưa được chứng minh. Trong một giao thức đúng, public key dùng để xác minh phải đến từ trust store đã provision, certificate chain đã được xác minh và gắn đúng identity, hoặc một cơ chế ràng buộc tương đương.

### Ba tình huống khác nhau

- **Key đã tin cậy, chữ ký không hợp lệ:** từ chối vì phép `verify` không đạt.
- **Chữ ký hợp lệ, key chưa đáng tin hoặc gắn sai danh tính:** **vẫn phải từ chối** dù `verify` đạt.
- **Chữ ký hợp lệ, key đáng tin, nhưng yêu cầu ngoài quyền hoặc đã hết hạn:** **vẫn phải từ chối** bởi policy/authorization/freshness không đạt.

Một hệ thống phải đạt **tất cả điều kiện bắt buộc** trước khi coi một thông điệp là hợp lệ ở tầng nghiệp vụ.

## 3. Ví dụ Java 17 chạy được: verify đúng, danh tính giả vẫn bị chặn

Chương trình sau sử dụng **Ed25519** chỉ để minh họa (vấn đề áp dụng tương tự cho chữ ký RSA-PSS/ECDSA). Nó sinh hai cặp khóa độc lập:

- `trusted`: cặp khóa đại diện cho một **danh tính đã được ứng dụng tin cậy từ trước** trong mô phỏng.
- `attacker`: cặp khóa do người tấn công kiểm soát.
- `trustedPin`: SHA-256 fingerprint của **SubjectPublicKeyInfo (SPKI) DER** của public key trusted. Ở ví dụ tự chứa, pin được tạo tại thời điểm khởi tạo để biểu diễn một trust anchor đã có; **đây chưa phải quy trình phân phối niềm tin thật**.
- `verifyWithPinnedKey`: chỉ chấp nhận chữ ký khi public key ứng viên khớp pin đã chọn **và** verify chữ ký thành công.

Tạo tệp **`SignatureIdentityDemo.java`**:

```java
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

public final class SignatureIdentityDemo {
    private static final String SIGNATURE_ALGORITHM = "Ed25519";

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator =
                KeyPairGenerator.getInstance(SIGNATURE_ALGORITHM);
        KeyPair trusted = generator.generateKeyPair();
        KeyPair attacker = generator.generateKeyPair();

        byte[] message = "transfer=100&to=alice"
                .getBytes(StandardCharsets.UTF_8);
        byte[] changedMessage = "transfer=900&to=alice"
                .getBytes(StandardCharsets.UTF_8);

        byte[] trustedSignature = sign(trusted, message);
        byte[] attackerSignature = sign(attacker, message);

        KeyFactory factory =
                KeyFactory.getInstance(SIGNATURE_ALGORITHM);
        PublicKey trustedFromSpki = factory.generatePublic(
                new X509EncodedKeySpec(trusted.getPublic().getEncoded()));
        PublicKey attackerFromSpki = factory.generatePublic(
                new X509EncodedKeySpec(attacker.getPublic().getEncoded()));
        byte[] trustedPin = spkiSha256(trustedFromSpki);

        System.out.println("Trusted signature verifies: "
                + verify(trustedFromSpki, message, trustedSignature));
        System.out.println("Attacker signature with attacker key verifies: "
                + verify(attackerFromSpki, message, attackerSignature));
        System.out.println("Attacker signature with trusted key verifies: "
                + verify(trustedFromSpki, message, attackerSignature));
        System.out.println("Attacker key matches trusted pin: "
                + MessageDigest.isEqual(trustedPin,
                        spkiSha256(attackerFromSpki)));
        System.out.println("Policy accepts attacker message: "
                + verifyWithPinnedKey(attackerFromSpki, trustedPin,
                        message, attackerSignature));
        System.out.println("Policy accepts trusted message: "
                + verifyWithPinnedKey(trustedFromSpki, trustedPin,
                        message, trustedSignature));
        System.out.println("Policy accepts changed message: "
                + verifyWithPinnedKey(trustedFromSpki, trustedPin,
                        changedMessage, trustedSignature));
        System.out.println("Replay cryptographic verification succeeds: "
                + verify(trustedFromSpki, message, trustedSignature));
    }

    private static byte[] sign(KeyPair pair, byte[] message)
            throws GeneralSecurityException {
        Signature signer = Signature.getInstance(SIGNATURE_ALGORITHM);
        signer.initSign(pair.getPrivate());
        signer.update(message);
        return signer.sign();
    }

    private static boolean verify(PublicKey publicKey, byte[] message,
                                  byte[] signatureBytes)
            throws GeneralSecurityException {
        Signature verifier = Signature.getInstance(SIGNATURE_ALGORITHM);
        verifier.initVerify(publicKey);
        verifier.update(message);
        return verifier.verify(signatureBytes);
    }

    private static byte[] spkiSha256(PublicKey publicKey)
            throws GeneralSecurityException {
        byte[] spki = publicKey.getEncoded();
        if (spki == null) {
            throw new GeneralSecurityException(
                    "This public key cannot be exported as SPKI");
        }
        return MessageDigest.getInstance("SHA-256").digest(spki);
    }

    private static boolean verifyWithPinnedKey(
            PublicKey candidate, byte[] trustedPin,
            byte[] message, byte[] signatureBytes)
            throws GeneralSecurityException {
        if (!MessageDigest.isEqual(trustedPin, spkiSha256(candidate))) {
            return false;
        }
        return verify(candidate, message, signatureBytes);
    }
}
```

Biên dịch và chạy trên JDK 17 trở lên có triển khai Ed25519:

```bash
javac --release 17 SignatureIdentityDemo.java
java SignatureIdentityDemo
```

**Stdout thực tế đã kiểm thử** (giá trị khóa/chữ ký được sinh ngẫu nhiên nhưng chương trình chỉ in kết quả boolean):

```text
Trusted signature verifies: true
Attacker signature with attacker key verifies: true
Attacker signature with trusted key verifies: false
Attacker key matches trusted pin: false
Policy accepts attacker message: false
Policy accepts trusted message: true
Policy accepts changed message: false
Replay cryptographic verification succeeds: true
```

**Giải thích từng kết quả:**

1. `Trusted signature verifies: true`: chữ ký tạo bằng private key trusted phù hợp public key trusted đã import lại từ SPKI.
2. `Attacker signature with attacker key verifies: true`: **đây là điểm nguy hiểm** nếu ứng dụng cho client tự gửi public key rồi coi `true` là danh tính đã được tin. Kết quả này hoàn toàn đúng về mật mã nhưng không xác thực người gửi là trusted publisher.
3. `Attacker signature with trusted key verifies: false`: chữ ký attacker không khớp public key đã tin cậy; việc lựa chọn đúng key làm thay đổi quyết định.
4. `Attacker key matches trusted pin: false` và `Policy accepts attacker message: false`: kiểm tra ràng buộc key trước khi chấp nhận chữ ký loại được key giả.
5. `Policy accepts trusted message: true`: cả key lẫn chữ ký phù hợp với trust record trong ví dụ.
6. `Policy accepts changed message: false`: chữ ký cho `transfer=100` không hợp lệ đối với nội dung đã đổi thành `transfer=900`.
7. `Replay cryptographic verification succeeds: true`: gửi lại nguyên thông điệp/chữ ký hợp lệ vẫn qua phép `verify`. Chữ ký **không tự chống phát lại**.

**Phân biệt mô phỏng và hệ thống thật:** `trustedPin` của demo được tính từ một public key **mới sinh ngay trong tiến trình**, nên **không chứng minh key này thật sự thuộc về một tổ chức bên ngoài**. Trong production, pin hoặc public key đáng tin phải được **cung cấp từ trước qua kênh được xác thực** (ví dụ ứng dụng phát hành có khóa hardcoded theo quy trình kiểm soát release, trust store đã kiểm duyệt, cấu hình có xác thực). Không được tính pin lần đầu từ chính `candidate` mà request chưa tin cậy mang đến rồi lập tức dùng pin đó để chấp nhận request: làm vậy quay lại đúng lỗ hổng ban đầu.

`getEncoded()` thường trả **SPKI DER** cho public key phần mềm; code minh họa có kiểm tra `null` vì không phải mọi key/provider đều cho xuất encoding. Hash SHA-256 của SPKI **không phải chứng chỉ X.509**, cũng không tự tạo niềm tin. `MessageDigest.isEqual` giúp so byte của hai fingerprint, nhưng bài toán chính là **nguồn gốc của fingerprint trusted**, không phải phép so sánh byte.

## 4. Ràng buộc khóa công khai với danh tính tin cậy

Một key binding được tin tưởng có thể mô tả bằng bộ:

```text
(expectedIdentity, allowedPurpose, trustedPublicKey,
 algorithmPolicy, validity/rotationPolicy, provenance)
```

Ví dụ một ứng dụng cập nhật phần mềm phải biết trước **khóa nào được phép ký bản cập nhật**, cho **sản phẩm nào**, có hiệu lực ở **giai đoạn nào**, và **ai có quyền thay đổi key registry**. Lưu một public key mà không kèm identity/purpose dễ dẫn đến nhầm các nhà phát hành hoặc môi trường staging/production.

### Các cách thiết lập niềm tin

| Cơ chế | Niềm tin ban đầu đến từ đâu? | Khi nào phù hợp? | Rủi ro vận hành cần xử lý |
| --- | --- | --- | --- |
| **Public-key pinning** | SPKI bytes/fingerprint được provision qua kênh đã tin cậy | Ứng dụng đóng, bản cập nhật có một số publisher xác định | Rotation, khóa dự phòng, thu hồi, sửa phần mềm nếu pin cũ lỗi |
| **Trust store/allowlist** | Danh sách key/identity đã phê duyệt và có kiểm soát thay đổi | Hệ thống nội bộ, nhiều publisher/tenant | Phân tách quyền quản trị, trùng key ID, hết hạn policy, xóa key cũ |
| **PKI / X.509** | CA/trust anchors được tin cậy trước, rồi kiểm tra đường dẫn chứng chỉ và tên/role | TLS, mTLS, cấp chứng chỉ tổ chức, máy chủ/người dùng | Thu hồi, thay đổi CA, hostname/SAN, EKU/KeyUsage, chính sách path |
| **TOFU (trust on first use)** | Public key thấy lần đầu được ghi nhớ khi chưa có kênh chứng thực khác | Một số workflow SSH/peer-to-peer theo chính sách | Lần đầu có thể bị MITM; phải cảnh báo khi key thay đổi và xác minh ngoài băng |
| **Khóa công bố qua kênh xác thực** | Endpoint/metadata đã gắn với issuer được tin, bảo vệ bằng HTTPS/TLS, policy hoặc chữ ký tin cậy khác | JWKS/JWT, hệ thống federation | Không lấy URL từ payload tùy ý, ràng buộc issuer/tenant/kid và caching |

Không có một cơ chế universal tốt nhất: lựa chọn phụ thuộc số lượng bên ký, chính sách danh tính, quy mô rotation, chuẩn giao thức và mô hình đe dọa. **Mọi cơ chế đều phải có điểm bắt đầu của niềm tin**. Nếu đối thủ kiểm soát đồng thời payload, signature, public key và “danh sách pin”, kiểm tra mật mã không thể tự khôi phục đúng danh tính.

### Key pinning: pin cái gì?

Có ít nhất hai khái niệm dễ lẫn:

- **SPKI pin:** SHA-256 trên byte DER `SubjectPublicKeyInfo` của public key, tức liên kết với **public key** và thuật toán liên quan.
- **Certificate fingerprint:** SHA-256 trên **toàn bộ certificate DER**; đổi/chứng chỉ mới có thể đổi fingerprint kể cả khi public key giữ nguyên.

Cả hai giá trị đều có thể hiển thị dưới dạng hex/Base64; chỉ nhìn vào chuỗi hash **không phân biệt được** chúng. Nếu thiết kế dùng SPKI pin, phải nói rõ source bytes là `publicKey.getEncoded()` ở định dạng SPKI, chứ không phải `certificate.getEncoded()`. Pin **không** là secret, nhưng tính đúng đắn/kênh provision của pin phải được bảo vệ.

Với trust store nhiều khóa, nên lookup theo **expected identity** và **key ID** hợp lệ rồi verify bằng key được tìm thấy. Trường `kid` đến từ request có thể được dùng như **gợi ý tìm kiếm trong allowlist**, không được xem như “bằng chứng khóa đã tin cậy” hay dùng tùy ý để tải khóa từ một URL do người gửi chỉ định.

## 5. Chứng chỉ X.509: chứng minh đường dẫn tin cậy khác với chứng minh danh tính

Certificate có thể mang public key, subject, Subject Alternative Name (SAN), thời hạn, issuer, Key Usage, Extended Key Usage và chữ ký của issuer. Nhưng chỉ parse certificate hoặc gọi `certificate.verify(issuerPublicKey)` là **chưa đủ**.

### Những bước cần có khi dựa vào PKI

1. **Chọn trust anchor phù hợp** từ trust store đã tin cậy. Không chấp nhận một CA root chỉ vì nó tự ký hợp lệ. “Trusted root” là quyết định phân phối tin cậy từ trước.
2. **Xác minh đường dẫn chứng chỉ** (issuer→subject, chữ ký, Basic Constraints, path length, thời hạn, policy, thuật toán/độ mạnh khóa và các ràng buộc path theo tiêu chuẩn). Java hỗ trợ `CertPathValidator.getInstance("PKIX")` và `PKIXParameters` có trust anchors do ứng dụng cấu hình.
3. **Kiểm tra đúng danh tính ứng dụng mong đợi**, không chỉ “bất kỳ certificate nào của một CA đáng tin”. Với HTTPS/TLS server, kiểm tra hostname trong SAN theo quy tắc endpoint identification; với client mTLS, ánh xạ certificate identity đến tài khoản/service được cấp quyền.
4. **Kiểm tra mục đích/role và chính sách khóa**: KeyUsage, ExtendedKeyUsage (EKU), tên service, tenant hoặc namespace, certificate policy và hạn chế tổ chức. Certificate được phép dùng để ký server TLS **không đương nhiên** có quyền ký bản cập nhật sản phẩm hoặc hóa đơn.
5. **Xem xét hiệu lực và revocation**: notBefore/notAfter, CRL/OCSP hay cơ chế revoke khác theo cấu hình/độ tin cậy cần thiết. Không giả định một phép `CertPathValidator.validate(...)` luôn đã làm revocation đúng trong mọi môi trường; policy/provider và nguồn dữ liệu ảnh hưởng bước này.
6. **Verify chữ ký của thông điệp bằng public key của đúng end-entity certificate**, đúng scheme và byte đầu vào, sau khi xác định key phù hợp identity/usage. Thành công ở bước này mới kết hợp được **chữ ký hợp lệ** với **một danh tính được chấp nhận theo policy**.

**Điểm thường bị bỏ sót:** Xác minh PKIX path **không tự động bảo đảm hostname hoặc subject identity khớp đối tượng bạn muốn**. Path validation chứng minh một cấu trúc tin cậy được kiểm tra; kiểm tra tên chủ thể là quyết định **phụ thuộc ngữ cảnh ứng dụng**. Một certificate hợp lệ cho `service-b.example` không thể được dùng thay cho `service-a.example` chỉ vì cùng được CA cấp.

Đối với HTTPS, nên dùng TLS/JSSE đã cấu hình kiểm tra certificate chain **và endpoint identity** theo đúng giao thức; tránh tự xây trust manager luôn chấp nhận certificate. Trong Java, chỉ thêm certificate vào `KeyStore` mà không có logic kiểm tra danh tính/ủy quyền thích hợp cho use case riêng cũng chưa đủ.

### Tự ký không có nghĩa đáng tin hoặc không đáng tin tuyệt đối

Một certificate tự ký có thể được **tin cậy có chủ ý** khi public key/fingerprint đã được cài đặt an toàn làm trust anchor cho một hệ thống giới hạn. Ngược lại, certificate tự ký do người tấn công gửi đến có thể vượt qua `cert.verify(cert.getPublicKey())`, vì họ có cả private key và certificate của mình. **Điều khác biệt là nguồn phân phối/trust anchor được phê duyệt**, không phải việc self-signed hay CA-signed như một nhãn đơn lẻ.

## 6. `Signature` trong Java không kiểm tra trust store

Các bước cốt lõi khi verify chữ ký Ed25519/RSA/ECDSA bằng JCA tương tự:

```java
Signature verifier = Signature.getInstance("Ed25519");
verifier.initVerify(candidatePublicKey);
verifier.update(exactMessageBytes);
boolean cryptographicallyValid = verifier.verify(signatureBytes);
```

Đây là **đoạn minh họa**, không phải chương trình độc lập: cần import `java.security.Signature`, có `candidatePublicKey`/`exactMessageBytes`/`signatureBytes` phù hợp và xử lý checked exceptions. Không có tham số “Alice”, “đúng tenant” hay “domain đáng tin” trong API này.

Lưu ý:

- `Signature.initVerify(PublicKey)` kiểm tra một số điều kiện của **loại khóa và provider**, không chứng minh danh tính. `Signature.initVerify(Certificate)` cũng **không thay thế** quy trình PKIX/path/identity validation; API có thể kiểm tra hạn chế KeyUsage nhất định, nhưng không tự biến certificate chưa tin cậy thành tin cậy.
- `Signature.verify(...)` có thể trả `false` hoặc ném `SignatureException` khi chữ ký/format có vấn đề tùy thuật toán/provider. Đừng coi mọi lỗi thành “công ty X gian lận”; coi input là không hợp lệ và báo lỗi phù hợp.
- `RSA-PSS` phải thống nhất hash, MGF1, salt length và các tham số. `ECDSA` cần đúng curve và format signature (ví dụ DER hoặc IEEE P1363 tùy giao thức). `Ed25519` là một scheme chữ ký cụ thể; không chọn algorithm chỉ theo độ dài signature hoặc trường do attacker đề xuất.
- `KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(bytes))` chỉ kiểm tra/decode **dữ liệu khóa**, không kiểm tra nguồn cung cấp dữ liệu đó.
- Việc `getInstance` thành công chỉ chứng tỏ provider có engine; không liên quan đến danh sách khóa/danh tính đáng tin của ứng dụng.

Nếu dùng `X509EncodedKeySpec` hoặc certificate public key load từ file/HTTP, câu hỏi đầu tiên vẫn phải là **file/HTTP response đó đến từ đâu và có được xác thực không?**.

## 7. Thứ tự xác minh đúng cho một thông điệp ký số

Dưới đây là luồng khái niệm cho **thông điệp được gửi kèm chữ ký**. Tên trường chỉ là ví dụ, không phải một định dạng giao thức chuẩn có thể copy nguyên vào production:

```text
Input:
  claimedIssuer, keyId, algorithm, payload, signature, context
       |
       v
[Kiểm tra giới hạn kích thước, encoding, schema, phiên bản]
       |
       v
[Xác định expectedIssuer/expectedRole từ ngữ cảnh tin cậy]
       |
       v
[Tra trusted key registry / validate certificate path + identity]
       |
       +-- khóa không tin cậy/sai role/sai issuer --> REJECT
       |
       v
[Chỉ cho phép algorithm/params đã quy định trong policy]
       |
       v
[Chuẩn hóa/đối chiếu chính xác bytes được ký + context]
       |
       v
[Signature.verify bằng public key đã được chấp nhận]
       |
       +-- false/exception ------------------------> REJECT
       |
       v
[Kiểm tra expiry, nonce/replay, audience, action authorization]
       |
       +-- policy không đạt -----------------------> REJECT
       |
       v
                 ACCEPT
```

Các bước có thể được triển khai ở thứ tự kỹ thuật khác nhau nhằm tối ưu hiệu suất hoặc hạn chế thông tin lộ ra, nhưng **quyết định ACCEPT phải phụ thuộc đầy đủ vào cả trust binding, cryptographic verification và policy**. Không nên thực thi tác dụng phụ trước khi những điều kiện bắt buộc đã đạt.

### Expected identity phải đến từ đâu?

Ví dụ một dịch vụ chỉ nhận cập nhật từ publisher A: `expectedIssuer="publisher-A"` phải đến từ **cấu hình/chính sách đã được phê duyệt** hoặc ngữ cảnh xác thực độc lập, **không được lấy nguyên từ claim `sender` rồi tin ngay**.

Nếu một hệ thống hỗ trợ nhiều publisher, registry có thể ánh xạ:

```text
publisher-A / signing-v3  -> trusted public key A, allowed for product X
publisher-B / signing-v7  -> trusted public key B, allowed for product Y
```

Khi request mang `keyId=signing-v7`, server chỉ dùng nó tra cứu trong **registry tin cậy** rồi kiểm tra claimed issuer/purpose khớp bản ghi. Server không chấp nhận URL tùy ý từ request để tải một key mới và cho rằng đó là key của publisher A. Khi quay vòng key, có quy trình quản trị xác thực, giai đoạn overlap và thu hồi rõ ràng.

### Khóa không tự cấp quyền

Một người ký có thể thực sự là một service đã tin cậy nhưng chỉ được quyền **gửi báo cáo**, không được quyền **phê duyệt chuyển tiền**. Chữ ký hợp lệ xác nhận nguồn theo key binding; authorization phải được thực thi độc lập dựa trên action, resource, tenant, amount, scope hoặc role.

## 8. Với JWT/JWS và metadata khóa: những cạm bẫy tương tự

Giao thức như JSON Web Signature (JWS) hoặc JSON Web Token (JWT) thường có trường `alg`, `kid`, `iss`, `aud` và chữ ký. Đây là use case hay gặp lỗi **“chữ ký pass nhưng identity không hợp lệ”**:

- `kid` chỉ là **key selection hint**; không chứng minh key thuộc về đúng issuer. Chỉ lookup key thuộc **tập khóa của issuer đã cấu hình tin cậy**.
- `alg` là thông tin trong dữ liệu do người gửi kiểm soát; triển khai phải áp dụng **allowlist algorithm** và gắn thuật toán với loại key/chính sách. Không để token ép chuyển sang thuật toán không an toàn, tắt chữ ký, dùng shared secret sai mục đích hoặc gây algorithm confusion.
- `jku`/`x5u` hoặc địa chỉ JWKS từ token **không được** trở thành URL tùy ý để tải và tin khóa. Nếu cần JWKS, lấy từ endpoint của issuer đã được thiết lập tin cậy và bảo vệ bởi cấu hình, xác thực TLS, giới hạn chuyển hướng/địa chỉ và cache/rotation hợp lý.
- Sau khi chữ ký hợp lệ, vẫn phải kiểm tra **issuer (`iss`)**, **audience (`aud`)**, thời gian (`exp`, `nbf`), subject/tenant, scope và policy của hệ thống. Một token ký hợp lệ bởi **issuer B** không thể tự động được nhận như token của **issuer A**.
- JSON canonicalization, detached payload và encoding base64url có thể ảnh hưởng byte ký. Nên dùng thư viện JOSE/JWT đã kiểm thử, không tự build/parse JWS bằng chuỗi ghép thủ công.

Một ví dụ nhỏ: server cấu hình chấp nhận token từ issuer `https://id.example.org` nhưng client gửi token hợp lệ được ký bằng khóa do một issuer khác cấp. Nếu server tra JWKS không giới hạn theo issuer mà client gợi ý, token có thể `verify == true`; dù vậy **ứng dụng vẫn phải từ chối** vì issuer/key binding không đúng.

## 9. Chữ ký hợp lệ vẫn không bảo vệ mọi ngữ cảnh

### 9.1 Replay

Đã thấy trong chương trình: `verify` của cùng thông điệp và chữ ký lặp lại vẫn `true`. Nếu một lệnh thanh toán đã ký được gửi lại, thuật toán chữ ký không biết nó là giao dịch cũ.

Để chống replay khi cần, giao thức phải đưa **transaction ID, sequence number, timestamp, nonce hoặc expiration** vào **những byte được ký**, sau đó kiểm tra trạng thái đã sử dụng/cửa sổ thời gian tại bên nhận. Chỉ thêm timestamp vào payload nhưng không đưa nó vào phần được ký sẽ không tạo ràng buộc bảo mật.

### 9.2 Cross-protocol / cross-tenant

Một chữ ký cho thông điệp “approve 100” có thể bị dùng sai chỗ nếu thiếu ràng buộc bên nhận, hệ thống, tenant hoặc mục đích. Giao thức nên ký bản tin có **domain separation** rõ ràng, ví dụ bao gồm phiên bản, loại hành động, audience, tenant, canonical resource ID và toàn bộ trường quan trọng.

Đừng tái sử dụng một key cho nhiều protocol không liên quan rồi giả định chữ ký của protocol A không thể được dùng trong protocol B. Phân tách key theo purpose hoặc ràng buộc purpose chặt trong signed data, với các quy tắc verification rõ ràng.

### 9.3 Dữ liệu “trông giống nhau” không phải cùng byte

`Signature.update(byte[])` xử lý **byte**, không hiểu text/JSON. Các chuỗi có UTF-8 khác, newline `\n`/`\r\n`, BOM, thứ tự trường JSON, Unicode normalization hoặc spacing khác nhau dẫn đến thông điệp ký khác. Khi một bên ký raw bytes còn bên khác verify JSON được serialize lại, có thể fail dù “nội dung” nhìn như nhau.

Nếu cần ký dữ liệu cấu trúc, định nghĩa encoding **không mơ hồ** hoặc dùng chuẩn canonicalization của protocol. Cũng cần chống parser differential: hai chương trình không được có cách hiểu khác nhau về cùng bytes có chữ ký hợp lệ.

### 9.4 Khóa bị lộ hoặc signer bị xâm phạm

Chữ ký hợp lệ không tự chứng minh private key vẫn độc quyền thuộc về chủ sở hữu hợp pháp. Nếu private key bị đánh cắp hoặc một signer bị chiếm quyền, attacker có thể tạo chữ ký hợp lệ bằng **đúng trusted public key**. Cần chính sách phát hiện, thu hồi, rotation, audit, HSM/KMS, giới hạn quyền signer và xác định phạm vi sự cố. Chữ ký số cũng **không mã hóa nội dung**; người quan sát vẫn có thể đọc nội dung đã ký nếu dữ liệu được gửi ở dạng plaintext.

## 10. Đưa bước xác thực danh tính vào hệ thống Java

### 10.1 Trusted key registry nội bộ

Với một số ít publisher/thiết bị, thiết lập registry bằng cấu hình được quản lý, ví dụ `issuer -> keyId -> publicKey + allowedPurposes + algorithmPolicy`. Key material phải được provision ngoài băng, bằng đường dẫn được chứng thực, artifact cài đặt đáng tin hoặc thay đổi quản trị được xác nhận. Sau đó:

- Xác định **expected issuer** từ policy/ngữ cảnh.
- Chỉ chấp nhận public key được ánh xạ cho issuer đó; có thể dùng fingerprint SPKI để phát hiện thay thế.
- Chỉ `Signature.verify` với key trong registry.
- Kiểm tra thời hạn, key rotation, trạng thái revoked và authorization.
- Lập audit log về key ID, issuer đã được chọn, version, kết quả; không ghi private key hoặc dữ liệu nhạy cảm không cần thiết.

Nếu key registry bị thay đổi bởi chính request cần verify, đây không còn là registry đáng tin.

### 10.2 PKIX và TLS

Java hỗ trợ `CertificateFactory`, `CertPathValidator`/`CertPathBuilder`, `PKIXParameters`, `TrustAnchor` và `KeyStore`. Với TLS, nên ưu tiên JSSE/`SSLContext` cùng trust manager và **endpoint identification** đã cấu hình đúng; với mTLS cần map certificate identity của client tới đúng tài khoản và role sau khi xác nhận chain.

Để tránh ngộ nhận, đoạn sau chỉ minh họa việc **khởi tạo engine**, không thực hiện xác minh đầy đủ:

```java
CertPathValidator validator = CertPathValidator.getInstance("PKIX");
// Cần CertPath/PKIXParameters/trust anchors được chuẩn bị đáng tin
// và kiểm tra danh tính, mục đích, trạng thái theo use case.
```

Không suy diễn rằng việc gọi `validator.validate(...)` với bất kỳ trust anchor do client gửi vào sẽ có giá trị. Quan trọng nhất là **trust anchors do ứng dụng lựa chọn trước**, cùng policy đúng use case. Kiểm tra hostname/SAN không phải mặc định của việc gọi trực tiếp `Signature.verify(...)` hoặc `CertPathValidator.validate(...)`.

### 10.3 Xử lý lỗi an toàn

- `Signature.verify == false`: chữ ký không hợp lệ cho key/message đã chọn; từ chối.
- `SignatureException`/`InvalidKeyException`/`NoSuchAlgorithmException`: kiểm tra định dạng/algorithm/provider/key policy; từ chối input không hợp lệ hoặc xử lý lỗi cấu hình. Không “thử từng key bất kỳ cho đến khi có key trả true” rồi chấp nhận danh tính do request tự khai.
- `CertificateException`, `CertPathValidatorException`: xem lại trust path/constraints, không tắt kiểm tra chain để “chạy được”.
- Thông báo public nên tránh tiết lộ quá chi tiết trạng thái key, certificate hoặc hệ thống nội bộ khi không cần. Các log nội bộ cần có correlation ID, key ID, kết quả bước **trust**, kết quả bước **signature** và lý do authorization, với dữ liệu được kiểm soát.

## 11. Bảng quyết định khi verify đã thành công

| Trường hợp | `verify` | Trust binding | Policy/freshness | Chấp nhận? |
| --- | --- | --- | --- | --- |
| Alice ký; key Alice đã provision và đúng purpose; request hợp lệ | `true` | Đạt | Đạt | **Có** |
| Mallory ký bằng khóa riêng rồi gửi kèm public key tự chọn | `true` với key Mallory | **Không đạt** | Không cần xét | **Không** |
| Alice ký nhưng verifier dùng key Bob | `false` | Dù Bob có tin cậy cũng sai đối tượng | Không cần xét | **Không** |
| File được ký bởi publisher đáng tin nhưng signature bị thay | `false` | Đạt | Không cần xét | **Không** |
| Chữ ký đúng nhưng yêu cầu đã hết hạn/nonce trùng | `true` | Đạt | **Không đạt** | **Không** |
| Certificate chain hợp lệ nhưng hostname/scope sai | Có thể `true` | **Không đúng expected identity** | Không cần xét | **Không** |
| Đúng key và thông điệp nhưng signer chỉ có role đọc, request yêu cầu xóa | `true` | Đạt | **Không có quyền** | **Không** |
| Khóa trusted đã bị thu hồi sau sự cố | Có thể `true` | Trust policy hiện tại từ chối key | Không cần xét | **Không** |

**Mô hình tư duy cần nhớ:** `valid signature != trusted signer != authorized operation`. Bước verify là **một điều kiện cần trong nhiều hệ thống**, không phải điều kiện đủ để đưa ra quyết định chấp nhận.

## 12. Checklist trước khi đưa chữ ký số vào production

- [ ] Danh tính/issuer được kỳ vọng xác định từ cấu hình hoặc ngữ cảnh xác thực độc lập, **không tự tin claim người gửi**.
- [ ] Public key đến từ trust store/PKIX/pin đã được provision qua kênh tin cậy; không lấy key tùy ý trong payload rồi chấp nhận.
- [ ] Mỗi key có `keyId`, issuer, purpose, thuật toán được cho phép, policy rotation/revocation và ranh giới tenant.
- [ ] Nếu dùng certificate: kiểm tra path tới trust anchor, validity, constraints, purpose, SAN/endpoint identity, revocation theo policy.
- [ ] `Signature.verify` dùng đúng scheme/parameters, đúng public key và **đúng dãy byte đã ký**.
- [ ] Nếu dữ liệu qua format cấu trúc, encoding/canonicalization là quy tắc của protocol và được kiểm thử hai chiều.
- [ ] Claims như audience, expiration, nonce/sequence, signed context và quyền hành động được kiểm tra **ngoài** chữ ký.
- [ ] Trường `alg`/`kid` của token chỉ được dùng trong allowlist/trusted key namespace; không tự tải key từ URL tùy ý.
- [ ] Không thực thi thao tác có tác dụng phụ trước khi hoàn tất trust + verify + policy.
- [ ] Có test cho chữ ký đúng, chữ ký bị sửa, message bị sửa, key bị thay, issuer sai, replay, key revoked và authorization sai.

## 13. Bài luyện tập

1. Giải thích vì sao dòng `Attacker signature with attacker key verifies: true` trong `SignatureIdentityDemo` không phải lỗi của Ed25519.
2. Xóa bước kiểm tra pin khỏi `verifyWithPinnedKey`. Điều gì xảy ra nếu một request tự gửi cả `attackerFromSpki` lẫn `attackerSignature`? Việc sửa code có “tạo ra lỗ hổng” hay chỉ làm lộ lỗi trust binding vốn đã tồn tại?
3. Nếu một public key X.509/SPKI được tải từ một URL trên Internet, những bằng chứng bổ sung nào cần có để tin nó thuộc đúng publisher? Một hash SHA-256 do chính URL đó trả về có đủ không?
4. Một certificate tự ký có thể đi qua phép `cert.verify(cert.getPublicKey())`; hãy giải thích vì sao điều này chưa chứng minh nó được công ty tin cậy.
5. Mô tả các bước bắt buộc để xác thực một server TLS: từ trust anchors, chain, validity/purpose đến **hostname SAN**. Bước nào không nằm trong `Signature.verify`?
6. Thiết kế hệ thống cập nhật phần mềm dùng key rotation. Nếu keyId cũ bị revoke sau khi bị lộ, ứng dụng phải xử lý file có chữ ký toán học còn hợp lệ thế nào?
7. Thiết kế một signed transfer request có tenant, amount, payee, unique transaction ID, timestamp và audience; chỉ rõ trường nào phải nằm trong byte được ký, và kiểm tra replay/authorization được thực hiện ở đâu.
8. Trong JWT multi-tenant, vì sao kiểm tra chữ ký đúng với một JWKS của issuer B **không đủ** để nhận token như issuer A? Hãy đề xuất một registry ánh xạ issuer → allowlisted JWKS keys và algorithm policy.

## Tài liệu tham khảo

- [Java SE 17 — `Signature`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Signature.html): `initVerify`, `update`, `verify` và ngoại lệ, kể cả overload nhận `Certificate`.
- [Java SE 17 — `KeyFactory`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyFactory.html): parse khóa bằng `X509EncodedKeySpec` (chỉ giải mã format, không xác thực danh tính).
- [Java SE 17 — `X509EncodedKeySpec`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/spec/X509EncodedKeySpec.html): định dạng SubjectPublicKeyInfo của public key.
- [Java SE 17 — `CertPathValidator`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertPathValidator.html), [`PKIXParameters`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/PKIXParameters.html): xác minh certification path tới trust anchors do ứng dụng chọn.
- [Java SE 17 — `X509Certificate`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/X509Certificate.html): SAN, KeyUsage, EKU, validity và chữ ký certificate.
- [RFC 5280 — Internet X.509 Public Key Infrastructure Certificate and CRL Profile](https://www.rfc-editor.org/rfc/rfc5280): PKIX path validation và các ràng buộc chứng chỉ.
- [RFC 8032 — EdDSA](https://www.rfc-editor.org/rfc/rfc8032): thuật toán Ed25519.
- [RFC 7515 — JSON Web Signature](https://www.rfc-editor.org/rfc/rfc7515) và [RFC 7519 — JSON Web Token](https://www.rfc-editor.org/rfc/rfc7519): `alg`, `kid`, `iss`, `aud` và xác minh nội dung token.


</details>

- [Quay lại đầu trang](#back-to-top)
