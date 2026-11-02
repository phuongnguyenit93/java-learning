<a id="back-to-top"></a>

# 15. Nền tảng TLS

## Menu
- [15.1 TLS kết hợp thỏa thuận khóa, chứng thư, chữ ký và mã hóa phiên](#tls-foundation-001)
- [15.2 Luồng bắt tay, xác thực máy chủ và xác thực hai chiều mTLS](#tls-foundation-002)
- [15.3 TLS 1.2, TLS 1.3 và cách đọc cấu hình giao thức khả dụng](#tls-foundation-003)
- [15.4 Cipher suite và khác biệt về thành phần thương lượng giữa các phiên bản TLS](#tls-foundation-004)
- [15.5 Khóa tạm thời, forward secrecy và quan hệ với khóa dài hạn của chứng thư](#tls-foundation-005)
- [15.6 Phân biệt tin chuỗi chứng thư với xác minh tên máy chủ](#tls-foundation-006)
- [15.7 Session, tái sử dụng phiên và vòng đời kết nối](#tls-foundation-007)
- [15.8 SNI để chọn máy chủ ảo và ALPN để chọn giao thức ứng dụng](#tls-foundation-008)
- [15.9 Phân biệt TLS trên luồng với DTLS trên datagram](#tls-foundation-009)

## <a id="tls-foundation-001">15.1 TLS kết hợp thỏa thuận khóa, chứng thư, chữ ký và mã hóa phiên</a>

<details>
<summary>Xem chi tiết</summary>

# 15.1 TLS kết hợp thỏa thuận khóa, chứng thư, chữ ký và mã hóa phiên

**TLS (Transport Layer Security)** bảo vệ dữ liệu giữa hai đầu kết nối bằng cách **kết hợp nhiều cơ chế mật mã, mỗi cơ chế giải quyết một vấn đề khác nhau**:

1. **Thỏa thuận khóa** (thông thường ECDHE trong TLS 1.3) để hai phía thiết lập vật liệu bí mật chung mà không gửi trực tiếp khóa phiên trên đường truyền.
2. **Chứng thư X.509 và cơ chế tin cậy (PKI)** để bên nhận xác định public key của đối tác có được gắn đúng với danh tính mong đợi hay không.
3. **Chữ ký số của handshake** để xác minh bên được chứng nhận thực sự kiểm soát private key tương ứng và ràng buộc danh tính vào nội dung thương lượng.
4. **Dẫn xuất khóa và mã hóa đối xứng xác thực (AEAD)** để trao đổi application data với tính bí mật và toàn vẹn, bằng các khóa phiên riêng theo hướng gửi/nhận.

Không cơ chế nào thay thế hoàn toàn các cơ chế còn lại. **ECDHE một mình chưa xác thực máy chủ. X.509 được parse thành công chưa có nghĩa đáng tin. Chữ ký chứng minh quyền kiểm soát khóa nhưng không trực tiếp dùng để mã hóa mỗi byte dữ liệu. AES-GCM bảo vệ record nhưng phải có khóa phiên đúng và được ràng buộc với handshake an toàn.**

Bài học dùng **TLS 1.3** làm trọng tâm và so sánh vừa đủ với **TLS 1.2** để tránh nhầm thuật toán. Tại thời điểm tài liệu này, **RFC 9846 (07/2026)** là đặc tả TLS 1.3 mới thay thế RFC 8446; một số tài liệu và JDK triển khai vẫn tham chiếu RFC 8446 lịch sử. Các khái niệm handshake, CertificateVerify, Finished, HKDF và AEAD là nền tảng cần hiểu khi đọc Java JSSE.

## 1. TLS bảo vệ điều gì?

Giả sử ứng dụng Java gửi thông tin đăng nhập và dữ liệu nghiệp vụ đến một HTTPS server. Kẻ tấn công trên mạng có thể quan sát gói tin, thay đổi dữ liệu hoặc tìm cách đóng giả server.

TLS được thiết kế để cung cấp:

| Thuộc tính | Ý nghĩa | Cơ chế chính |
| --- | --- | --- |
| **Confidentiality** (bí mật) | Người quan sát đường truyền không đọc được application plaintext. | Khóa phiên đối xứng + AEAD record encryption. |
| **Integrity** (toàn vẹn) | Bản ghi bị sửa đổi không được chấp nhận như dữ liệu hợp lệ. | AEAD authentication tag, sequence/nonce và transcript verification. |
| **Server authentication** (xác thực server) | Client kiểm chứng đang nói chuyện với server mang danh tính dự kiến. | X.509 chain/trust/hostname + signature handshake + Finished. |
| **Optional client authentication** | Server có thể yêu cầu client cung cấp chứng thư và chứng minh private key. | mTLS: client Certificate/CertificateVerify, trust policy, Finished. |
| **Forward secrecy** (trong các kiểu trao đổi khóa thích hợp) | Lộ khóa ký dài hạn sau này không tự cho phép giải mã lại các phiên ECDHE đã ghi lén trước đó. | Khóa trao đổi tạm thời, hủy private ephemeral; key schedule TLS 1.3. |

**Ranh giới:** TLS không tự giải quyết quyền truy cập ứng dụng, lỗ hổng SQL injection, tài khoản bị chiếm quyền, endpoint bị cài malware, bản ghi plaintext ở server, hay việc người dùng gửi dữ liệu nhạy cảm tới server *đúng danh tính nhưng độc hại*. TLS bảo vệ **kết nối giữa các endpoint** theo danh tính/trust policy đã được cấu hình.

Đặc biệt, “đã có ổ khóa HTTPS” chỉ có nghĩa thiết lập kết nối theo các bước xác minh của client; ứng dụng vẫn cần authorization, xử lý dữ liệu và giám sát đúng.

## 2. Bốn vai trò mật mã và nơi xuất hiện

| Thành phần | Đầu vào / đối tượng | Vai trò trong TLS 1.3 | **Không phải vai trò** |
| --- | --- | --- | --- |
| **(EC)DHE key agreement** | Ephemeral private/public key shares của hai phía | Tạo shared secret đầu vào cho key schedule. | Không xác thực hostname hoặc CA một mình. |
| **X.509 certificate & chain** | Public key của identity + chứng nhận của issuer | Giúp ràng buộc public key vào danh tính thông qua PKI. | Không trực tiếp mã hóa application data. |
| **Handshake digital signature** | Private key của bên chứng thực, handshake transcript | Chứng minh quyền kiểm soát private key và chống thay đổi ngầm trong thương lượng. | Không ký riêng từng TLS record. |
| **HKDF key schedule** | DHE shared secret, transcript context, PSK khi có | Tạo handshake/application traffic secrets và keys theo hướng, giai đoạn. | Không tự xác thực CA nếu thiếu certificate/signature/trust. |
| **AEAD symmetric cipher** | Traffic key, nonce/sequence, plaintext/AAD | Mã hóa và xác thực các TLS records. | Không tự biết identity server. |
| **Finished message** | Handshake traffic secret + transcript hash | MAC chứng minh hai phía có secret thích hợp, ràng buộc toàn vẹn handshake. | Không phải chữ ký số bằng private key của certificate. |

Nên phân biệt ba khái niệm **key** khác nhau:
- **Khóa private lâu dài** của certificate (ví dụ RSA hoặc EC/Ed25519 ký handshake): phải giữ bí mật và bảo vệ theo vòng đời chứng thư.
- **Khóa private tạm thời** trong trao đổi ECDHE: dùng để tạo shared secret cho phiên; không đồng nghĩa private key của certificate.
- **Traffic keys đối xứng** dẫn xuất từ key schedule: thực sự dùng AES-GCM hoặc ChaCha20-Poly1305 mã hóa record. Mỗi hướng/giai đoạn có vật liệu khóa thích hợp.

Đọc hiểu TLS sẽ sai ngay nếu đồng nhất public key trong chứng thư với public key được gửi qua `key_share`, hay nghĩ khóa RSA của certificate trực tiếp giải mã mọi record HTTPS.

## 3. Đường đi của handshake TLS 1.3 có chứng thư server

Sơ đồ **đơn giản hóa, theo kiểu handshake 1-RTT không có HelloRetryRequest, không resumption, không client certificate**:

~~~text
             CLIENT                                        SERVER
               |                                              |
               | ClientHello                                  |
               | - supported_versions: TLS 1.3                |
               | - supported cipher suites                    |
               | - supported_groups / key_share (ECDHE)        |
               | - signature_algorithms, SNI, ALPN...          |
               |--------------------------------------------->|
               |                                              |
               | ServerHello                                  |
               | - selected version, cipher suite, key_share   |
               |<---------------------------------------------|
               |                                              |
               |  Hai phía tính shared secret ECDHE            |
               |  và dẫn xuất handshake traffic keys           |
               |                                              |
               |     {EncryptedExtensions}                     |
               |     {Certificate (server chain)}              |
               |     {CertificateVerify (digital signature)}   |
               |     {Finished (transcript MAC)}               |
               |<---------------------------------------------|
               |  [Các message trong { } được bảo vệ bằng      |
               |   handshake traffic keys]                    |
               |                                              |
               | Client xác minh chain/identity, signature,    |
               | transcript/Finished; gửi {Finished}          |
               |--------------------------------------------->|
               |                                              |
               |     Dẫn xuất application traffic keys        |
               |                                              |
               |<==== application data: AEAD records ========>|
~~~

Điểm chính:
1. `ClientHello` công bố phiên bản, cipher suites, nhóm key agreement, key shares và các extension theo giao thức. `SNI` giúp server chọn certificate theo hostname; **SNI tự nó không xác thực hostname**.
2. `ServerHello` chọn tham số thỏa thuận. Với chứng thư server và (EC)DHE, mỗi phía có thể tính shared secret tương ứng mà không truyền raw shared secret.
3. TLS 1.3 dùng **HKDF** để dẫn xuất keys; các thông điệp chứng thực chính sau `ServerHello` được mã hóa bằng handshake traffic keys.
4. Server gửi `Certificate` và `CertificateVerify`. Client xác minh certificate chain/trust, expected identity, chữ ký trên handshake transcript và `Finished`.
5. `Finished` là MAC để xác nhận toàn vẹn handshake và key confirmation; bên client cũng gửi Finished (sau các thông điệp chứng thực của client nếu được yêu cầu).
6. Sau handshake, application data truyền dưới dạng **AEAD TLS records**, dùng traffic keys dẫn xuất từ cùng quá trình đã ràng buộc handshake.

Đây không phải mô tả mọi trường hợp TLS 1.3: có `HelloRetryRequest`, resumption qua PSK, 0-RTT, client certificate và các hoạt động sau handshake. TLS 1.3 cũng có thể dùng **PSK-only key establishment** mà không trao đổi DHE; khi đó tính chất forward secrecy khác với kiểu PSK+DHE.

**Đừng nhầm “ServerHello mã hóa từ đầu”**: `ClientHello` và `ServerHello` khởi đầu ở trạng thái chưa được mã hóa bằng traffic keys TLS; lớp mã hóa handshake được thiết lập sau các thông điệp xác lập secret cần thiết. Việc mã hóa các authentication messages trong TLS 1.3 là một khác biệt quan trọng so với mô hình thường gặp của TLS 1.2.

## 4. Thỏa thuận khóa: vì sao không gửi khóa AES qua mạng?

### 4.1. ECDHE: dùng public key tạm thời, không chuyển private key

Với ECDHE, mỗi bên sinh cặp key agreement tạm thời rồi trao đổi **public key share**:

~~~text
Client:
  private ephemeral a       public A = a*G  ---> Server

Server:
  private ephemeral b       public B = b*G  ---> Client

Client tính shared secret = ECDH(a, B)
Server tính shared secret = ECDH(b, A)

=> hai bên có cùng vật liệu bí mật
=> kẻ chỉ quan sát A và B không nên suy ra secret
~~~

Đây là mô hình giải thích ECDH cổ điển; với **X25519**, các quy tắc tính toán/mã hóa điểm có đặc thù riêng. Không dùng sơ đồ này làm hướng dẫn triển khai tự chế một giao thức key exchange.

Các nhóm được sử dụng trong TLS 1.3 gồm **X25519**, **secp256r1 (P-256)** và các nhóm khác theo hỗ trợ/tiêu chuẩn. Chọn nhóm phù hợp thông qua chính sách JSSE/protocol, không chấp nhận nhóm tùy ý do một phía áp đặt.

**Key agreement không tự xác thực peer**. Một attacker MITM có thể tạo key share của chính mình với từng đầu nếu không có cơ chế xác thực/ràng buộc transcript. Vì vậy certificate, chữ ký `CertificateVerify` và MAC `Finished` là phần **cốt lõi**, không phải trang trí thêm sau ECDHE.

### 4.2. Forward secrecy gắn với khóa tạm thời, không với nhãn “TLS” chung chung

Khi phiên dùng (EC)DHE tạm thời và secrets ephemeral được xử lý đúng, việc lộ **private key chứng thư** của server về sau không tự cung cấp bí mật đã dùng để mã hóa phiên cũ. Đây là một ý chính của **forward secrecy**.

Tuy nhiên:
- Nếu máy chủ đang bị chiếm quyền khi phiên diễn ra, attacker có thể truy cập plaintext/secret đang sống hoặc can thiệp endpoint.
- Nếu ephemeral private key/traffic secrets bị log, dump hoặc giữ lại không an toàn, forward secrecy của giao thức không tự cứu được ứng dụng.
- Resumption và chế độ PSK-only có các điều kiện riêng; không hứa mọi đường thiết lập TLS đều có cùng mức forward secrecy.
- Tính chất này **không có nghĩa** dữ liệu đã lưu vào database hoặc log ứng dụng trở nên an toàn.

TLS 1.3 **không có static RSA key transport** như một số cipher suites TLS 1.2 cũ. Một server dùng chứng thư **RSA** trong TLS 1.3 vẫn có thể dùng **X25519/ECDHE để thỏa thuận khóa**; RSA private key lúc đó dùng chứng thực/chữ ký handshake (theo signature scheme được thương lượng), không trực tiếp giải mã premaster secret như RSA key exchange lịch sử.

## 5. Ví dụ Java 17: quan sát riêng thao tác X25519

Để nhận diện phần **key agreement** trong TLS, ta thử một ví dụ Java SE 17 **không triển khai TLS**. Chương trình này chỉ chứng minh hai cặp khóa X25519 tính ra shared secret giống nhau; nó **không có chứng thư, chữ ký handshake, HKDF-Expand-Label, transcript hay AEAD records**. Không lấy kết quả 32 byte đưa thẳng vào AES để tự thiết kế “TLS rút gọn”.

**Tệp `X25519RolesDemo.java`:**

~~~java
import java.security.*;
import java.security.spec.NamedParameterSpec;
import java.util.Arrays;
import javax.crypto.KeyAgreement;

public class X25519RolesDemo {
    static byte[] agreement(PrivateKey own, PublicKey peer)
            throws GeneralSecurityException {
        KeyAgreement kx = KeyAgreement.getInstance("X25519");
        kx.init(own);
        kx.doPhase(peer, true);
        return kx.generateSecret();
    }

    public static void main(String[] args) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519");
        kpg.initialize(NamedParameterSpec.X25519);

        KeyPair a = kpg.generateKeyPair();
        KeyPair b = kpg.generateKeyPair();

        byte[] fromA = agreement(a.getPrivate(), b.getPublic());
        byte[] fromB = agreement(b.getPrivate(), a.getPublic());

        System.out.println("Raw shared secret bytes: "
                + fromA.length);
        System.out.println("Agreement matches: "
                + Arrays.equals(fromA, fromB));

        Arrays.fill(fromA, (byte) 0);
        Arrays.fill(fromB, (byte) 0);
    }
}
~~~

Chạy:

~~~bash
javac X25519RolesDemo.java
java X25519RolesDemo
~~~

Đầu ra đã kiểm chứng trên Java 17 có X25519:

~~~text
Raw shared secret bytes: 32
Agreement matches: true
~~~

**Giải thích:** `getInstance("X25519")` chọn key-agreement algorithm, `NamedParameterSpec.X25519` xác định tham số key generation, `doPhase` kết hợp private key của mình với public key đối tác. Dùng `Arrays.equals` để kiểm chứng **hai bên tính cùng byte secret**; trong production không log shared secret và không tự nghĩ `Arrays.fill` bảo đảm xóa mọi bản sao của bí mật.

**Phần còn thiếu để trở thành TLS** rất lớn và bắt buộc: xác thực danh tính key share, ràng buộc transcript, HKDF theo chuẩn, kiểm tra Finished, quản lý record sequence/nonce, anti-replay, certificate trust và chống downgrade. Vì vậy **dùng JSSE để chạy TLS** thay vì ghép các JCA primitive một cách thủ công.

## 6. Chứng thư X.509: key nào được tin cậy?

### 6.1. Certificate phục vụ xác thực identity, không tạo traffic key

Trong handshake chứng thực server, bản tin `Certificate` mang chuỗi chứng thư liên quan đến danh tính server (thường leaf và intermediate). Chứng thư leaf chứa **public key cho xác thực**. Bên server giữ **private key tương ứng** để chứng minh rằng nó được phép dùng identity đó, qua `CertificateVerify`.

Tình huống ví dụ:

~~~text
Server hostname: api.example.test
Certificate leaf:
  Subject / SAN: DNS:api.example.test
  Public key: RSA (chẳng hạn)
  Issuer: Intermediate CA
  Signature: CA ký trên nội dung certificate

Client có:
  Trust store chứa root CA được tin cậy
  Chính sách PKIX và expected hostname

Client phải:
  Xây/kiểm tra certificate chain + trust anchor
  Kiểm tra validity / signature / constraints / EKU / algorithm policy
  Kiểm tra SAN/hostname api.example.test
  Xác minh CertificateVerify trên handshake transcript
~~~

Tất cả tên trên đều chỉ là **tên minh họa**, không phải một certificate thật. Điều quan trọng là **public key trong leaf certificate không nhất thiết là key share ECDHE**: một certificate RSA có thể dùng chứng thực trong khi ephemeral agreement là X25519.

### 6.2. Parse không phải trust

`CertificateFactory.getInstance("X.509")` có thể đọc DER/PEM và tạo `X509Certificate`, nhưng **parse được chỉ chứng minh cấu trúc hợp lệ**. Để xác thực TLS server cần cơ chế PKIX/trust manager và kiểm tra hostname/endpoint identity.

Phân biệt:

| Việc làm | Kết quả | Chưa được bảo đảm |
| --- | --- | --- |
| `CertificateFactory.generateCertificate(...)` | X.509 có thể parse. | CA trust, hostname, hạn, mục đích, thu hồi. |
| `cert.checkValidity()` | Thời điểm nằm trong hạn theo cert. | Chuỗi CA đáng tin, hostname đúng. |
| `cert.verify(issuerPublicKey)` | Chữ ký certificate đúng với public key được đưa vào. | Public key issuer có thuộc trust anchor đã phê duyệt không. |
| `TrustManagerFactory`/PKIX | Kiểm tra chain theo truststore/chính sách cấu hình. | Quy tắc hostname nếu ứng dụng raw socket chưa bật endpoint identification. |
| `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")` | Yêu cầu áp dụng kiểm tra danh tính endpoint kiểu HTTPS khi handshake bằng JSSE phù hợp. | Không thay thế việc nạp truststore đáng tin. |

Trong Java raw `SSLSocket`/`SSLEngine`, **không được giả định kiểm tra hostname mặc nhiên diễn ra giống mọi HTTPS client**. Nếu kết nối raw tới server, cần bật `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")` ở client theo mục đích và phiên bản JDK. Các HTTP client cao cấp thường đã quản lý trust/hostname nhưng phải tránh tùy chỉnh làm vô hiệu kiểm tra.

### 6.3. CA signature và handshake CertificateVerify là hai chữ ký khác nhau

**Chữ ký trên chứng thư (CA signature):** CA ký nội dung `TBSCertificate` của chứng thư leaf để xác nhận các thuộc tính và public key theo chính sách CA. Client kiểm tra chuỗi tới trust anchor.

**Chữ ký `CertificateVerify` (server signature):** server dùng **private key tương ứng leaf certificate** ký dữ liệu được quy định từ handshake transcript, chứng minh nó kiểm soát private key và ràng buộc phần thương lượng đã diễn ra. Nội dung/format ký phụ thuộc TLS 1.3 signature scheme.

Nếu chỉ kiểm tra chứng thư có chữ ký CA đúng nhưng **không chứng minh peer giữ private key**, kẻ tấn công có thể phát lại certificate công khai của một server hợp lệ. CertificateVerify là một phần giúp ngăn việc này.

### 6.4. Xác thực hai chiều mTLS khác kết nối HTTPS thông thường

- **Server-auth TLS:** client xác thực server; server **chưa nhất thiết** xác thực client bằng certificate. Ứng dụng thường dùng token/session/user authentication riêng.
- **Mutual TLS (mTLS):** server yêu cầu chứng thư client và kiểm tra chain/identity/purpose; client chứng minh private key qua handshake. Điều này có thể gắn với workload identity khi được triển khai đúng.
- **mTLS không tự định nghĩa authorization**: một client có certificate hợp lệ chưa mặc nhiên có quyền đọc mọi tài nguyên. Ánh xạ identity, tenant, scope và policy vẫn là việc của ứng dụng.

Một certificate self-signed được client đưa trực tiếp vào truststore có thể phục vụ **test loopback** hoặc pinning được phê duyệt. Điều này không có nghĩa “tắt kiểm tra trust”. Trong production, trust anchor phải được cung cấp qua kênh cấu hình đáng tin, quản lý rotation và kiểm tra identity tương ứng.

## 7. Chữ ký handshake, Finished và MAC record khác nhau ra sao?

Rất dễ gộp ba khái niệm:

### 7.1. `CertificateVerify`: chữ ký bằng private key danh tính

Server ký lên transcript/giá trị được chuẩn TLS xác định, client xác minh với public key certificate. Scheme chữ ký có thể là RSA-PSS, ECDSA hoặc EdDSA tùy chính sách, certificate và thương lượng.

**Không đồng nhất thuật toán chữ ký chứng thư của CA với scheme mà server dùng ở CertificateVerify.** Chứng thư có thể do CA ký bằng một scheme; key trong leaf certificate dùng scheme khác được hỗ trợ cho handshake.

### 7.2. `Finished`: MAC bằng khóa dẫn xuất, xác nhận toàn vẹn handshake

TLS 1.3 dẫn xuất `finished_key` qua **HKDF-Expand-Label** rồi tính **HMAC trên transcript hash**. Peer kiểm tra `Finished` trước khi coi handshake hoàn thành theo quy tắc giao thức.

Finished **không phải chữ ký số**, không dùng private key certificate để ký từng lần. Nó xác nhận rằng hai bên có đúng traffic secret/nhìn thấy handshake phù hợp, qua đó giúp phát hiện thay đổi handshake hoặc key mismatch.

### 7.3. AEAD authentication tag: bảo vệ từng TLS record

Sau handshake, application records được mã hóa/xác thực bằng AEAD như AES-GCM hoặc ChaCha20-Poly1305. Tag của từng record bảo vệ nội dung và dữ liệu ràng buộc theo định dạng record của TLS.

Tag này **không phải** `CertificateVerify` hay `Finished`. Application data không cần được server RSA-sign lại mỗi packet: chi phí/khái niệm khác nhau, TLS dùng traffic keys đối xứng hiệu quả cho data path.

| Câu hỏi | `CertificateVerify` | `Finished` | AEAD tag record |
| --- | --- | --- | --- |
| Dùng key gì? | Private key chứng thư (khóa bất đối xứng). | Secret/key dẫn xuất từ key schedule (MAC). | Symmetric traffic key tương ứng hướng gửi/nhận. |
| Bảo vệ đối tượng nào? | Handshake transcript theo chuẩn. | Toàn vẹn/chủ thể của handshake context. | TLS application/handshake record tương ứng. |
| Có phải chữ ký số không? | **Có**. | **Không**, là MAC. | **Không**, là authentication tag. |
| Có xuất hiện với từng application record? | **Không**. | **Không**. | **Có**, theo AEAD record format. |

### 7.4. Vì sao cần cả CertificateVerify lẫn Finished?

CertificateVerify ràng buộc **identity được cấp chứng thư** với transcript; Finished ràng buộc **key schedule/secrets được dẫn xuất** với transcript và xác nhận key possession. Kết hợp với certificate trust, TLS chống tấn công sửa đổi thương lượng và MITM hiệu quả hơn so với chỉ dùng DH thuần.

Không được lược bỏ Finished hay kiểm tra signature chỉ vì “đã có kết nối TCP” hoặc “server certificate parse được”.

## 8. Dẫn xuất khóa phiên TLS 1.3 bằng HKDF

### 8.1. Không dùng trực tiếp shared secret thô làm AES key

Đầu ra DHE/ECDHE là một **shared secret**. TLS 1.3 sử dụng **HKDF-Extract / HKDF-Expand-Label** và transcript context để tạo nhiều bí mật/khóa có công dụng khác nhau.

Sơ đồ khái niệm (không phải mã triển khai nguyên văn toàn bộ RFC):

~~~text
       Pre-shared key / PSK (nếu có)
                    |
                    v
                early secret
                    |
       ECDHE / DHE shared secret
                    |
                    v
              handshake secret
                    |
          +---------+---------+
          |                   |
  client handshake    server handshake
    traffic secret      traffic secret
          |                   |
    handshake keys       handshake keys

       handshake transcript + key schedule
                    |
                    v
                main/master secret
                    |
          +---------+---------+
          |                   |
  client application   server application
    traffic secret       traffic secret
          |                   |
  AEAD key + IV          AEAD key + IV
          |                   |
          +---- application records ----+
~~~

Tên giai đoạn và độ dài các đầu ra phải tuân thủ RFC. **Không tự tạo một khóa duy nhất rồi dùng cả hai hướng và mọi mục đích**: TLS tách biệt secrets theo vai trò, chiều truyền, epoch và transcript. Ví dụ minh họa giúp hiểu separation, không thay thế bản mô tả đầy đủ của **RFC 9846 `7 Key Schedule**.

### 8.2. Traffic keys và IV khác “private key” của certificate

- **Private key danh tính:** tồn tại qua nhiều kết nối, dùng để chứng thực theo chính sách; có thể ở HSM.
- **Private key key_share:** ephemeral, dùng tính ECDHE secret trong một handshake.
- **Client application traffic key:** mã hóa dữ liệu từ client sang server.
- **Server application traffic key:** mã hóa dữ liệu từ server sang client.
- **Traffic IV/nonce:** thông số record encryption, cùng key và sequence quy tắc để tránh nonce reuse.

Ngay cả khi cả hai hướng dùng **AES-128-GCM**, chúng không đồng nghĩa sử dụng cùng key raw hoặc nonce sequence.

### 8.3. Cipher suite TLS 1.3 **không** đồng nghĩa tất cả thành phần mật mã

Ví dụ `TLS_AES_128_GCM_SHA256` có nghĩa:
- AEAD record cipher: **AES-128-GCM**.
- Hash dùng cho transcript/HKDF: **SHA-256**.

Nó **không mã hóa sẵn tên key agreement** và **không quy định chữ ký certificate** trong chính tên suite. Key agreement group được thương lượng qua supported groups/key_share, signature scheme qua các extension tương ứng.

So với **TLS 1.2**:
- Tên suite cũ như `TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256` thường chứa gợi ý **ECDHE key exchange + RSA authentication + AES-GCM**.
- Trong **TLS 1.3**, suite `TLS_AES_128_GCM_SHA256` nói về **AEAD + HKDF hash**, không phải “TLS không còn certificate”.
- Không cố dùng suite TLS 1.2 như một suite TLS 1.3 vì hai phiên bản định nghĩa cipher suite khác nhau.

Trong TLS 1.3, các suites khác thường gặp gồm `TLS_AES_256_GCM_SHA384` và `TLS_CHACHA20_POLY1305_SHA256`; chính sách lựa chọn dựa trên hỗ trợ/provider, tiêu chuẩn tổ chức và khả năng thiết bị. Đừng suy ra kiểu certificate chỉ bằng việc nhìn tên `AES_128_GCM`.

## 9. Record encryption: tại sao TLS dùng AEAD?

Sau khi có traffic keys, TLS record layer bảo vệ message bằng mã hóa đối xứng **có xác thực (AEAD)** thay vì mã hóa mà không kiểm tra toàn vẹn.

### 9.1. Khái niệm bản ghi

~~~text
Application bytes
      |
      v
TLS record layer
  - chia thành các records theo protocol
  - chọn traffic key theo hướng/giai đoạn
  - tạo nonce từ traffic IV + sequence number
  - AEAD encrypt và sinh tag
      |
      v
Encrypted record trên TCP
      |
      v
Peer: kiểm tra tag -> chấp nhận và giải mã / từ chối
~~~

Trong TLS 1.3, record layer dùng phần header/metadata thích hợp làm **AAD** và mã hóa inner content theo đặc tả. Một bit bị sửa có thể khiến kiểm tra AEAD thất bại và kết nối bị kết thúc theo quy tắc TLS.

### 9.2. Quy tắc nonce cực kỳ quan trọng

Với AES-GCM, **không được lặp (key, nonce)** trong các lần mã hóa độc lập. TLS 1.3 quản lý nonce record bằng cơ chế được chuẩn hóa dựa trên **static IV được dẫn xuất và sequence number**, cùng giới hạn/switch keys phù hợp.

Không tự lấy `GCMParameterSpec` hay `Cipher` để mã hóa lại payload sau khi đã dùng `SSLSocket`: TLS provider đã xử lý AEAD, nonce và tag. Thêm một lớp AES tùy tiện không sửa lỗi certificate trust và có thể làm phức tạp vòng đời khóa.

`SSLSocket.getInputStream()` trả **application plaintext sau khi TLS xác thực record**; `getOutputStream()` nhận application plaintext để record layer mã hóa. Không cần ứng dụng biết raw traffic keys hoặc nonce.

### 9.3. AEAD không tự chống application replay

TLS record encryption bảo vệ trình tự record trong **một kết nối** theo cơ chế protocol. Nhưng ứng dụng vẫn có thể nhận **yêu cầu HTTP hợp lệ được gửi lại** trên phiên mới, ví dụ client retry, attacker có token hợp lệ hoặc 0-RTT. Phải có idempotency keys, nonce nghiệp vụ, hạn token/transaction checks và chính sách anti-replay theo ngữ cảnh.

**TLS 1.3 0-RTT early data** là trường hợp đặc biệt có nguy cơ replay; không dùng 0-RTT cho giao dịch có side effect không thể chấp nhận lặp lại nếu chưa có biện pháp xử lý theo chuẩn và nghiệp vụ.

## 10. TLS 1.3 so với TLS 1.2: những khác biệt quyết định

| Khía cạnh | TLS 1.2 | TLS 1.3 |
| --- | --- | --- |
| Key establishment | Có nhiều nhóm cipher suites, bao gồm RSA key transport lịch sử và ECDHE; an toàn phụ thuộc lựa chọn. | Handshake certificate thông dụng dùng (EC)DHE, không có static RSA key transport; hỗ trợ PSK modes. |
| Danh tính / chữ ký | Chứng thư, chữ ký/authentication theo luồng handshake và suite cụ thể. | `Certificate` + `CertificateVerify` gắn identity và transcript trong handshake certificate-based. |
| Cipher suite name | Thường biểu thị key exchange, authentication, bulk cipher, hash. | Định nghĩa **AEAD + HKDF hash**; key_share và signature scheme thương lượng tách biệt. |
| Dẫn xuất key | TLS 1.2 PRF/secret model riêng. | **HKDF key schedule** phân tách early/handshake/application secrets. |
| Bảo vệ handshake | Một số thông điệp handshake về chứng thư truyền trước khi bật encryption. | Phần lớn thông điệp handshake **sau ServerHello** được bảo vệ bằng handshake traffic keys. |
| Record protection | Có thể là AEAD hoặc cơ chế cũ tùy suite được phép. | Dựa trên **AEAD**. |
| Resumption / 0-RTT | Có session resumption; không có cơ chế 0-RTT TLS 1.3. | PSK/tickets và có thể 0-RTT với yêu cầu an toàn replay riêng. |
| Forward secrecy | Chỉ khi key exchange/cấu hình phù hợp (ví dụ ECDHE). | Certificate-based (EC)DHE thông dụng có forward secrecy, PSK-only cần đánh giá riêng. |

Không hiểu nhầm “TLS 1.2 luôn mất an toàn” hoặc “TLS 1.3 luôn an toàn bất chấp cấu hình”. Chính sách TLS 1.2 còn phụ thuộc cipher suite, patch level và ràng buộc triển khai; ưu tiên TLS 1.3 khi hai phía và yêu cầu tổ chức hỗ trợ.

Các phiên bản cũ như SSLv3, TLS 1.0/1.1 không phải lựa chọn cho thiết kế kết nối mới. Kiểm tra protocol list/cipher suites theo JDK thực tế thay vì hard-code fallback sang phiên bản yếu để vượt lỗi handshake.

## 11. Java dùng gì để chạy TLS? JSSE chứ không tự ráp JCA primitive

Java SE cung cấp **Java Secure Socket Extension (JSSE)** qua `javax.net.ssl`. JSSE tích hợp handshake, certificate processing, key exchange, signature verification, key schedule, record encryption và đóng kết nối.

| Thành phần Java | Nhiệm vụ |
| --- | --- |
| `SSLContext` | Cấu hình TLS provider, key managers, trust managers, `SecureRandom`. |
| `SSLSocket` / `SSLServerSocket` | Kết nối socket TCP được bảo vệ bởi TLS. |
| `SSLEngine` | TLS engine tách khỏi transport, dùng cho NIO/reactor/non-blocking. |
| `KeyManagerFactory` | Lấy key managers từ keystore/provider, giúp server chọn chứng thư/private key. |
| `TrustManagerFactory` | Lấy trust managers từ truststore để đánh giá peer certificates. |
| `SSLParameters` | Protocols, cipher suites, endpoint identification, yêu cầu client auth, SNI/ALPN liên quan. |
| `SSLSession` | Metadata của kết nối TLS đã negotiated: protocol, cipher suite, peer certificates và thông tin session. |
| `HttpsURLConnection` / `java.net.http.HttpClient` | HTTP/HTTPS APIs thường thực hiện TLS và identity checks khi cấu hình mặc định đúng. |
| `KeyStore` | Abstraction lưu key/cert/trust material; không nhất thiết đồng nghĩa HSM hay PKIX validator. |

**Điểm thiết kế:** Đối với HTTPS bình thường, ưu tiên `java.net.http.HttpClient` hoặc framework HTTP đáng tin được cập nhật, không tự viết raw TLS socket nếu không có nhu cầu đặc biệt. Với raw sockets, ứng dụng phải tự đảm bảo endpoint identification và cấu hình trust manager; **không tự cài `X509TrustManager` trả về rỗng và “trust all”**.

### 11.1. “SSLContext” không có nghĩa đang dùng SSLv3

Tên lớp `SSLContext`, `SSLSocket` có từ lịch sử API; chúng dùng được TLS hiện đại. `SSLContext.getInstance("TLS")` cho biết engine phục vụ TLS, còn protocol phiên thực được chọn thông qua supported/enabled protocols và thương lượng. Đừng coi từ “SSL” trong tên class là bằng chứng dùng giao thức SSL cũ.

### 11.2. Không cần ứng dụng tự gọi `Signature.verify` để xác minh server certificate

JSSE thực hiện các bước liên quan qua trust manager, certificate path processing và TLS handshake theo policy. Nếu ứng dụng parse certificate thủ công hoặc lấy `getPeerCertificates()`, điều đó không thay thế kiểm tra identity/chain trong handshake.

Với raw socket client, nên sử dụng:

~~~java
SSLParameters parameters = sslSocket.getSSLParameters();
parameters.setEndpointIdentificationAlgorithm("HTTPS");
sslSocket.setSSLParameters(parameters);
sslSocket.startHandshake();
~~~

Đây là mảnh minh họa; `sslSocket` là đối tượng đã tạo từ `SSLSocketFactory` phù hợp. Không dùng `setEndpointIdentificationAlgorithm(null)` để “tạm thời bỏ lỗi certificate”, vì đó là cách vô hiệu lớp kiểm tra danh tính quan trọng.

### 11.3. Tránh “trust-all” và “hostname verifier always true”

Những mã nguy hiểm:
- `X509TrustManager.checkServerTrusted(...)` có body rỗng.
- `HostnameVerifier.verify(...)` luôn trả `true`.
- `SSLContext.init(..., new TrustManager[]{trustAll}, ...)` để vượt lỗi `PKIX path building failed`.
- Bắt `SSLHandshakeException` rồi kết nối lại bằng HTTP plaintext hoặc TLS phiên bản yếu mà không có chính sách.
- Đưa một certificate từ kết nối chưa xác thực vào truststore và coi nó là “tin cậy” chỉ vì đã thấy trong response.

Sửa đúng là **cấu hình CA trust/chain, SAN hostname, certificate expiry, algorithm constraints và provider phù hợp**. Trong môi trường doanh nghiệp có CA nội bộ, trust anchor phải được phân phối từ kênh tin cậy và quản lý rotation.

## 12. Java 17+: demo handshake TLS 1.3 qua loopback có xác thực server

Ví dụ này đi qua **JSSE thực sự** thay vì chỉ chạy ECDHE/AES rời rạc. Server và client đều chạy trong một tiến trình Java, kết nối qua loopback TCP. Server dùng certificate trong PKCS#12 keystore, client chỉ tin certificate được nhập vào **truststore test riêng**, đồng thời **bật kiểm tra endpoint identity "HTTPS"**.

Cách này có mục tiêu học tập:
- **Key agreement, handshake signature, key derivation và record AES-GCM** được JSSE xử lý nội bộ.
- **Certificate + truststore + hostname verification** thực sự được áp dụng.
- Có thể quan sát `SSLSession` protocol và cipher suite, trao đổi dữ liệu `ping`/`pong` thành công.
- Không cần lộ raw private key, ECDHE secret hoặc traffic key.
- **Đây là demo local, không phải cách quản lý CA/credential production.**

### 12.1. Tạo certificate/key và truststore test bằng `keytool`

Chạy ở **thư mục thử nghiệm riêng**, chỉ dùng trên máy local, không commit các file test này. Các lệnh `keytool` dưới đây chạy với JDK 17+. `changeit` là **mật khẩu công khai của demo**, tuyệt đối không phải mật khẩu dùng cho keystore production.

~~~bash
# 1. Tạo RSA private key + chứng thư tự ký chỉ cho localhost.
keytool -genkeypair -alias localhost -keyalg RSA -keysize 2048 \
  -sigalg SHA256withRSA -dname "CN=localhost" \
  -ext "SAN=dns:localhost,ip:127.0.0.1" -validity 2 \
  -storetype PKCS12 -keystore server.p12 \
  -storepass changeit -keypass changeit -noprompt

# 2. Xuất public certificate, không xuất private key.
keytool -exportcert -rfc -alias localhost -keystore server.p12 \
  -storepass changeit -file server.crt

# 3. Đưa certificate đó vào truststore CHỈ DÀNH CHO DEMO.
keytool -importcert -alias localhost -file server.crt \
  -keystore trust.p12 -storetype PKCS12 \
  -storepass changeit -noprompt
~~~

Dấu `\` cuối dòng là kiểu nối dòng của **Bash**; với PowerShell/CMD trên Windows, nhập các lệnh thành từng **dòng đơn, không có `\`**. Các bước đã được kiểm chứng trên Windows PowerShell với cùng các tham số chính.

Tạo certificate với `SAN=dns:localhost,ip:127.0.0.1` vì client kết nối loopback. `CN=localhost` một mình không phải cách chuẩn để bỏ qua SAN hostname check. Trên máy khác, hãy cung cấp SAN đúng danh tính thực, certificate CA cấp/chứng thực đúng chính sách và truststore phù hợp.

**Không commit** `server.p12`, `trust.p12` hay private key sinh ra. Khi demo xong, xóa các file test theo chính sách. Import certificate tự ký là thao tác **trust có chủ đích** trong môi trường lab; không nhập certificate lạ từ Internet vào truststore production.

### 12.2. Chương trình `Tls13LoopbackDemo.java`

Chương trình yêu cầu biến môi trường `TLS_DEMO_PASS` chứa password của **keystore test** và hai đường dẫn PKCS#12 đã tạo. Không có private key hay mật khẩu production trong code.

~~~java
import java.io.*;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import java.util.concurrent.*;
import javax.net.ssl.*;

public class Tls13LoopbackDemo {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Expected server and trust store paths");
        }

        String str = System.getenv("TLS_DEMO_PASS");
        if (str == null) {
            throw new IllegalStateException("Missing TLS_DEMO_PASS");
        }
        char[] pass = str.toCharArray();
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (InputStream in = new FileInputStream(args[0])) {
                ks.load(in, pass);
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, pass);

            SSLContext server = SSLContext.getInstance("TLS");
            server.init(kmf.getKeyManagers(), null, new SecureRandom());

            KeyStore ts = KeyStore.getInstance("PKCS12");
            try (InputStream in = new FileInputStream(args[1])) {
                ts.load(in, pass);
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ts);

            SSLContext client = SSLContext.getInstance("TLS");
            client.init(null, tmf.getTrustManagers(), new SecureRandom());

            try (SSLServerSocket listener = (SSLServerSocket)
                    server.getServerSocketFactory().createServerSocket(
                            0, 10, InetAddress.getLoopbackAddress())) {

                listener.setEnabledProtocols(new String[]{"TLSv1.3"});
                listener.setEnabledCipherSuites(
                        new String[]{"TLS_AES_128_GCM_SHA256"});

                ExecutorService pool = Executors.newSingleThreadExecutor();
                try {
                    Future<String> task = pool.submit(() -> {
                        try (SSLSocket s = (SSLSocket) listener.accept()) {
                            s.setEnabledProtocols(new String[]{"TLSv1.3"});
                            s.setEnabledCipherSuites(
                                    new String[]{"TLS_AES_128_GCM_SHA256"});
                            s.setSoTimeout(10000);
                            s.startHandshake();

                            String request = new String(
                                    s.getInputStream().readNBytes(4),
                                    StandardCharsets.US_ASCII);
                            s.getOutputStream().write(
                                    "pong".getBytes(StandardCharsets.US_ASCII));
                            s.getOutputStream().flush();
                            return request;
                        }
                    });

                    try (SSLSocket s = (SSLSocket)
                            client.getSocketFactory().createSocket(
                                    InetAddress.getLoopbackAddress(),
                                    listener.getLocalPort())) {

                        SSLParameters p = s.getSSLParameters();
                        p.setProtocols(new String[]{"TLSv1.3"});
                        p.setCipherSuites(
                                new String[]{"TLS_AES_128_GCM_SHA256"});
                        p.setEndpointIdentificationAlgorithm("HTTPS");
                        s.setSSLParameters(p);
                        s.setSoTimeout(10000);
                        s.startHandshake();

                        SSLSession session = s.getSession();
                        s.getOutputStream().write(
                                "ping".getBytes(StandardCharsets.US_ASCII));
                        s.getOutputStream().flush();

                        System.out.println(
                                "Protocol: " + session.getProtocol());
                        System.out.println(
                                "Cipher suite: " + session.getCipherSuite());
                        System.out.println("Server authenticated: "
                                + (session.getPeerCertificates().length > 0));
                        System.out.println("Client received: " + new String(
                                s.getInputStream().readNBytes(4),
                                StandardCharsets.US_ASCII));
                    }

                    System.out.println("Server received: "
                            + task.get(10, TimeUnit.SECONDS));
                } finally {
                    pool.shutdownNow();
                }
            }
        } finally {
            Arrays.fill(pass, '\0');
        }
    }
}
~~~

### 12.3. Biên dịch/chạy và stdout

Với **Bash**:

~~~bash
export TLS_DEMO_PASS=changeit
javac Tls13LoopbackDemo.java
java Tls13LoopbackDemo server.p12 trust.p12
~~~

Với **Windows PowerShell**:

~~~powershell
$env:TLS_DEMO_PASS = "changeit"
javac Tls13LoopbackDemo.java
java Tls13LoopbackDemo server.p12 trust.p12
Remove-Item Env:TLS_DEMO_PASS
~~~

Chỉ dùng mật khẩu `changeit` vì đây là lab có secret **vô giá trị ngoài demo**. Không đưa mật khẩu production lên command line, stdout, code hoặc repository. Trong production, tích hợp secret delivery theo workload identity/secret manager và keystore/HSM policy.

Đầu ra đã kiểm chứng bằng JDK 17 với JSSE hỗ trợ TLS 1.3 và AES-128-GCM, certificate/keytool test đúng hướng dẫn:

~~~text
Protocol: TLSv1.3
Cipher suite: TLS_AES_128_GCM_SHA256
Server authenticated: true
Client received: pong
Server received: ping
~~~

**Khi môi trường không hỗ trợ TLS 1.3 hoặc cipher suite được khóa cứng trong demo**, handshake sẽ thất bại. Đây là điều cần xử lý bằng cấu hình/chính sách được hỗ trợ, không fallback im lặng sang SSL/TLS yếu. Tùy provider, thông báo exception và cách quản lý truststore có thể khác.

### 12.4. Phân tích chính xác các thành phần TLS

**Server side:**
- `KeyStore("PKCS12")` nạp certificate và private key **dùng để chứng thực server**; `KeyManagerFactory` cung cấp key material cho JSSE.
- `SSLContext.init(kmf.getKeyManagers(), null, ...)` để server có thể trình certificate và chứng minh private key.
- `SSLServerSocket` chấp nhận TLS connection. Các tham số TLS 1.3 và AEAD suite được yêu cầu rõ ràng ở listener và socket.
- Demo **không bật client certificate authentication**. Đừng nhầm đây là mTLS; server chỉ biết client đã hoàn thành handshake, **chưa được xác thực bằng chứng thư client**.

**Client side:**
- `TrustManagerFactory.init(ts)` cấu hình **truststore riêng có certificate demo**; server certificate sẽ được kiểm tra theo tin cậy đã cấu hình.
- `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")` giúp kiểm tra expected peer identity. SAN phải đúng host/IP kết nối.
- `SSLSession.getProtocol()` và `getCipherSuite()` chỉ trả metadata đã thương lượng, **không tiết lộ raw secret**.
- `session.getPeerCertificates().length > 0` ở đây dùng để minh họa server đã đưa certificate. Nó **không phải toàn bộ phép chứng minh trust** nếu dùng riêng lẻ; đảm bảo quan trọng đến từ handshake JSSE với trust manager + hostname verification cấu hình đúng, và việc `startHandshake()` hoàn tất mà không bị exception.

**Đường dữ liệu:**
- Chuỗi `ping`/`pong` được ứng dụng ghi/đọc dưới dạng plaintext ở `InputStream`/`OutputStream`.
- JSSE thực thi ECDHE, ký/verify handshake, Finished, HKDF, AEAD records, nonce/tag phía dưới. Ứng dụng không tự gọi `Cipher.getInstance("AES/GCM/NoPadding")` cho dữ liệu trong `SSLSocket`.
- Mã hóa chỉ bảo vệ **transport giữa hai đầu**. Nếu server ghi `ping` ra log hoặc chuyển plaintext qua kết nối khác không mã hóa, TLS của kết nối ban đầu không ngăn sự rò rỉ sau đó.

**Giới hạn demo:**
- Certificate chỉ có hiệu lực 2 ngày và tự ký; không dùng để phục vụ domain thật.
- Demo truststore pin trực tiếp certificate tự ký. Trong production cần một PKI/trust model và rotation đúng.
- TLS record cipher suite được **khóa cứng để tạo stdout dễ kiểm chứng**, không có nghĩa mọi hệ thống production phải bắt buộc dùng duy nhất `TLS_AES_128_GCM_SHA256`.
- Chương trình không có HTTP parser, timeout tổng thể, certificate rotation, cancellation đầy đủ hoặc thiết kế server tải cao. Nó là lab TLS thật, không phải HTTP server production.

## 13. Khi chứng thư, chữ ký, khóa và mã hóa cùng tạo nên “server authentication”

Xét hai trường hợp có cùng ECDHE và AES-GCM:

### 13.1. Có ECDHE + AES-GCM nhưng không xác thực server

~~~text
Client --key_share--> attacker --key_share--> real server
Client <--attacker share-- attacker <--server share--

Client và attacker có một secret A.
Attacker và real server có một secret B.
Attacker giải mã/sửa/mã hóa lại traffic giữa hai bên.
~~~

ECDHE bảo vệ secret trước người **chỉ quan sát thụ động**, nhưng không chặn MITM nếu không xác thực key exchange. Chữ ký trên handshake của server, certificate chain/trust và Finished ngăn kẻ chỉ có key share tự nhận là server được cấp chứng thư.

### 13.2. Certificate đúng nhưng hostname verification bị tắt

Client đã tin CA A, nhưng attacker có một chứng thư hợp lệ từ CA A cho **domain khác**. Nếu client chỉ kiểm tra “cert được CA tin” mà không kiểm tra SAN/expected hostname, kết nối có thể được chấp nhận sai danh tính.

**Bài học:** “public key được một CA tin cậy ký” chưa đủ; phải gắn với **định danh endpoint đang gọi**, mục đích sử dụng và ràng buộc giao thức.

### 13.3. Certificate parse đúng nhưng private key không khớp

Server đưa certificate leaf có public key RSA X nhưng private key của server lại thuộc RSA Y. Chữ ký `CertificateVerify` không thể xác minh với public key X, nên handshake không được chấp nhận. Đây là ví dụ lý do cần **proof of possession** của private key trong handshake.

### 13.4. Certificate và signature hợp lệ, nhưng Finished sai

Kể cả một số phép xác minh certificate/signature thành công, nếu `Finished` không khớp transcript/key material, phải **abort**, không chuyển sang application data “để thử”. Finished kiểm tra sự phù hợp của secret và handshake context.

### 13.5. Handshake hợp lệ, application tag sai

Một record AES-GCM bị sửa trên đường truyền sẽ không qua AEAD integrity check, gây lỗi kết nối theo TLS protocol. Không được trả plaintext chưa xác thực cho ứng dụng hoặc bỏ qua lỗi rồi xử lý record như hợp lệ.

## 14. Các lỗi TLS thường gặp trong Java JSSE

| Triệu chứng / ngoại lệ | Nguyên nhân khả dĩ | Điều cần kiểm tra |
| --- | --- | --- |
| `SSLHandshakeException`, đôi khi chứa `PKIX path building failed` | Server chain không xây được đến trust anchor được tin. | Truststore, missing intermediate, CA chính thức, certificate policy; không trust-all. |
| `SSLHandshakeException` do hostname mismatch | SAN không có host/IP đích hoặc endpoint identity không khớp. | URL/hostname thực, SAN, SNI, certificate rotation; không bỏ kiểm tra tên. |
| `CertificateExpiredException` (thường là cause) | Certificate ngoài hạn thời gian hiệu lực. | Đồng hồ, NotBefore/NotAfter, kế hoạch gia hạn. |
| `SSLHandshakeException` / `SSLProtocolException` | Hai bên không hỗ trợ protocol/cipher suite/key share/signature scheme tương thích. | TLS 1.3 support, cấu hình JDK/provider, thuật toán bị disabled, log negotiation đã sanitize. |
| `SSLException` khi đọc/ghi | AEAD/tag/record xử lý lỗi, peer đóng lỗi hoặc protocol/transport gặp vấn đề. | Ngữ cảnh kết nối, có thể là lỗi mạng, dữ liệu hỏng; không suy từ message duy nhất. |
| `SocketTimeoutException` | Timeout mạng/handshake/đọc theo socket policy. | Network connectivity, timeout và tải server; không mặc định là cert hỏng. |
| `UnrecoverableKeyException` / `KeyStoreException` | Keystore không đọc được private key, password sai hoặc alias/key config không đúng. | KeyManagerFactory, PKCS12/JKS/provider, key alias, mật khẩu từ secret manager. |
| `SSLHandshakeException` khi bật mTLS | Server yêu cầu cert client nhưng client thiếu cert hoặc không được tin. | Client key managers, certificate/key usage, truststore server, yêu cầu client auth. |
| Kết nối thành công với `TLSv1.2` trong khi kỳ vọng `TLSv1.3` | Enabled protocols không như dự định hoặc peer không hỗ trợ. | `SSLSession.getProtocol()`, config, hệ thống trung gian và policy. |
| Hoàn tất TLS nhưng HTTP 401/403 | TLS có thể đúng, nhưng user/token/authorization sai. | Authentication/authorization ứng dụng **khác** TLS handshake. |

**Đừng xác định nguyên nhân chỉ từ tên `SSLHandshakeException`.** Một exception tổng quát có thể bọc lỗi certificate, thuật toán, ciphers, key material hoặc protocol. Cần xem cause chain/trust manager/provider một cách có kiểm soát, không in private key, token hoặc toàn bộ handshake secrets ra log.

### 14.1. Debug bằng thuộc tính JDK nhưng phải bảo vệ log

Ở môi trường lab có thể bật debug JSSE, ví dụ:

~~~bash
java -Djavax.net.debug=ssl,handshake Tls13LoopbackDemo server.p12 trust.p12
~~~

**Không khuyến khích dùng debug này trong production hoặc đính kèm nguyên log công khai**. Handshake log có thể bao gồm hostname, certificate, key IDs, thông số nội bộ và metadata nhạy cảm; ở một số chế độ debug khác còn có dữ liệu cần bảo vệ nghiêm ngặt. Dùng cấu hình log ngắn hạn, hạn chế ACL/retention và sanitize trước khi chia sẻ.

### 14.2. SNI và ALPN không phải chữ ký

- **SNI (Server Name Indication)**: client cho server biết hostname mong muốn để server chọn certificate/virtual host; đây không phải bằng chứng certificate hợp lệ.
- **ALPN (Application-Layer Protocol Negotiation)**: hai phía thương lượng giao thức ứng dụng, ví dụ `h2` hoặc `http/1.1`. Nó **không tạo ra key exchange riêng** thay TLS, và không tự cấp quyền truy cập.
- Nếu server chọn certificate không khớp SNI/expected host, JSSE client vẫn phải kiểm tra hostname/trust theo policy.

## 15. Session resumption, PSK và 0-RTT: không phải lúc nào cũng trình certificate mới

TLS 1.3 có thể tiếp tục kết nối dựa trên **session ticket/resumption PSK**. Trong các kiểu này, một handshake được nối lại có thể **không gửi Certificate/CertificateVerify mới**, vì xác thực được kế thừa/ràng buộc thông qua trạng thái PSK/session theo thiết kế giao thức.

Điểm cần phân biệt:

| Cơ chế | Vai trò | Rủi ro cần quản lý |
| --- | --- | --- |
| **Full handshake, certificate authentication** | Xác thực server qua chain/certificate signature và thiết lập secret mới. | Chain/hostname, signature scheme, key share/trust. |
| **PSK resumption** | Tiếp tục liên kết dựa trên secret đã tạo từ kết nối/handshake trước. | Ticket key lifecycle, scope của PSK, identity binding, expiry. |
| **PSK + DHE** | Kết hợp resumption PSK với ephemeral key share. | Cấu hình đúng để hưởng forward secrecy theo mô hình. |
| **PSK-only** | Có thể không có DHE mới ở chế độ cho phép. | Không giả định có forward secrecy giống full DHE. |
| **0-RTT early data** | Cho phép gửi một số application data sớm trong resumption phù hợp. | Có **nguy cơ replay**, không phù hợp thao tác không idempotent nếu chưa chống replay. |

**Không phát biểu “TLS luôn gửi chứng thư trong mọi handshake”**. Câu đúng là: **TLS certificate-based full handshake** sử dụng certificate và chứng minh private key; handshake PSK có logic xác thực khác. Phải hiểu cấu hình và session state của hệ thống khi chẩn đoán packet capture không thấy certificate.

### Chống replay là việc cả TLS deployment và ứng dụng phải quan tâm

Một POST `/transfer-money` gửi như 0-RTT có thể mang side effects nguy hiểm nếu bị phát lại. Ngay cả khi bản ghi được mã hóa, **mã hóa không tự chứng minh một yêu cầu là duy nhất**. Triển khai cần chính sách vô hiệu 0-RTT cho mutation nhạy cảm hoặc anti-replay/idempotency đáng tin cậy, bao gồm xử lý nhiều instance và retry.

## 16. Thiết kế triển khai thực tế: kiểm soát lớp nào?

| Lớp | Câu hỏi phải trả lời | Kiểm soát khuyến nghị |
| --- | --- | --- |
| **Key generation/certificate lifecycle** | Private key certificate được tạo/lưu/rotate ở đâu? | PKI phù hợp, HSM/KMS khi cần, ownership, expiration alerts, kiểm tra chain. |
| **Trust anchors** | CA nào được phép xác thực server/client? | Truststore được phân phối qua kênh tin cậy, hạn chế custom roots. |
| **Endpoint identity** | Client dự kiến gặp hostname/service nào? | HTTPS endpoint identification, SAN match, không trust-all. |
| **Key agreement** | Group/size/version được phép là gì? | TLS 1.3, JDK/provider được hỗ trợ, chính sách disabled algorithms. |
| **Signature schemes** | Bên dùng cert có thể chứng thực bằng thuật toán được phép không? | RSA-PSS/ECDSA/EdDSA theo certificate/provider/security policy. |
| **Key schedule/records** | Ai xử lý HKDF, nonce, AEAD tag, replay record? | Dùng JSSE/TLS library đáng tin, không tự tái tạo TLS primitive. |
| **Application authentication** | Client/user nào có quyền sử dụng tài nguyên? | Token/mTLS identity mapping, authorization, tenant checks. |
| **Resumption/0-RTT** | Có side effects không? | Quản lý tickets/PSK, anti-replay, hạn chế 0-RTT theo nghiệp vụ. |
| **Telemetry** | Có xác minh TLS đang dùng gì mà không lộ secret không? | Ghi protocol, cipher suite, certificate/key ID được phép; không log raw secrets. |
| **Incident response** | Khi private key/server cert bị lộ hoặc CA bị distrust thì sao? | Revoke/rotate, cập nhật trust material, monitor, kiểm tra exposure. |

Trong Java microservice, thiết kế bảo mật tốt là dùng **HTTP client/server framework đã cấu hình TLS** thay vì để mỗi team viết cách tính X25519/HKDF/GCM riêng. Chỉ khi xây dựng thư viện mật mã nền tảng mới cần tiếp cận wire protocol cực sâu; ngay cả khi đó vẫn nên dựa trên triển khai được kiểm toán.

## 17. Những hiểu lầm phổ biến

| Hiểu lầm | Vì sao sai | Cách hiểu đúng |
| --- | --- | --- |
| “TLS dùng RSA để mã hóa tất cả dữ liệu HTTPS.” | TLS 1.3 dùng **AEAD symmetric keys** cho records, không dùng RSA encrypt payload. | Certificate RSA có thể ký handshake; X25519/ECDHE thỏa thuận secret; AES-GCM bảo vệ data. |
| “ECDHE tự xác thực server.” | Attacker có thể thỏa thuận DH riêng với từng phía. | Cần certificate/trust, CertificateVerify và Finished. |
| “Đã có X.509 certificate là server đáng tin.” | Certificate parse được vẫn có thể self-signed/unknown CA/hostname mismatch. | Kiểm tra chain, trusted root, SAN, EKU/constraints. |
| “Public key của certificate chính là key_share.” | Identity key và ephemeral agreement key có công dụng khác nhau. | Phân biệt private key ký lâu dài, ephemeral key exchange và traffic key. |
| “CertificateVerify và Finished đều là chữ ký RSA.” | Finished là MAC dựa trên key schedule, không phải chữ ký bất đối xứng. | CertificateVerify ký transcript, Finished xác nhận transcript/keys. |
| “SHA256 trong `TLS_AES_128_GCM_SHA256` nghĩa là server dùng SHA256withRSA.” | SHA256 ở đây là hash cho HKDF/transcript. | Authentication signature được thương lượng riêng. |
| “Mỗi TLS record được ký bằng private key certificate.” | Record layer dùng AEAD symmetric key và tag. | Chữ ký handshake tạo xác thực ban đầu, AEAD xử lý dữ liệu phiên. |
| “TLS mã hóa nên có thể tắt hostname verification.” | Không còn chắc chắn server đúng danh tính, dễ bị MITM. | Bật endpoint identification và trust đúng. |
| “Self-signed certificate không bao giờ dùng được.” | Nó có thể dùng trong lab hoặc pinning/trust model có kiểm soát. | Chỉ tin thông qua kênh cấu hình đáng tin, không bỏ kiểm tra danh tính. |
| “`SSLContext` nghĩa là SSLv3.” | Tên lớp mang tính lịch sử. | Chọn TLS 1.3 qua provider và enabled protocols. |
| “TLS 1.3 không cần chứng thư ở mọi trường hợp.” | Phát biểu thiếu ngữ cảnh, PSK handshake có thể không gửi cert. | Certificate-based full handshake có cert; PSK/resumption có quy tắc riêng. |
| “Cứ `verify()` một chữ ký bất kỳ là đủ chống MITM.” | Phải xác minh đúng transcript, public key đã được trust, identity và Finished. | Dùng JSSE thực hiện protocol, không tự nối các JCA calls. |
| “TLS 1.3 cipher suite định nghĩa luôn X25519 và RSA.” | Suite TLS 1.3 chỉ mô tả AEAD + hash. | Group key_share và signature schemes được thương lượng riêng. |
| “AES-GCM tự chống mọi replay HTTP.” | AEAD record integrity khác anti-replay nghiệp vụ/0-RTT. | Thêm cơ chế idempotency, request nonce và anti-replay khi cần. |
| “Socket handshake xong thì mọi lệnh đều được phép.” | TLS xác thực endpoint chưa xác thực quyền người dùng/endpoint authorization. | Thực hiện kiểm tra authorization độc lập. |

## 18. Tình huống tự kiểm tra

**Câu 1.** Một server dùng certificate RSA-2048 nhưng phiên thương lượng `TLS_AES_128_GCM_SHA256` và `X25519`. Khóa RSA đang mã hóa dữ liệu ứng dụng hay không?

**Đáp án:** Không. Trong TLS 1.3 certificate-based handshake, khóa RSA có thể dùng chữ ký chứng thực, X25519 dùng trao đổi secret tạm thời, còn AES-128-GCM dùng traffic keys đối xứng cho records.

**Câu 2.** Client và server tự xây ECDHE riêng và tính cùng shared secret. Đã ngăn MITM chưa?

**Đáp án:** Chưa. Chưa có bằng chứng identity hay ràng buộc transcript vào public key đáng tin. Cần toàn bộ handshake TLS xác thực.

**Câu 3.** `CertificateFactory` đọc thành công certificate PEM của server, và `cert.checkValidity()` không báo lỗi. Có thể bỏ `TrustManagerFactory` và hostname check không?

**Đáp án:** Không. Parse và validity chỉ là điều kiện cục bộ; cần chain/trust, issuer/signature, usage và SAN/expected hostname.

**Câu 4.** Vì sao TLS 1.3 cần cả `CertificateVerify` và `Finished`?

**Đáp án:** CertificateVerify chứng minh private key tương ứng danh tính đã được chứng nhận ký handshake context; Finished là MAC dựa trên traffic secret để xác nhận key/transcript integrity. Chúng bảo vệ các thuộc tính khác nhau.

**Câu 5.** Một record bị đổi một byte; AES-GCM vẫn giải mã được plaintext rồi app mới báo lỗi có đúng không?

**Đáp án:** Không được chấp nhận plaintext chưa xác thực. AEAD tag phải được kiểm tra theo TLS; bản ghi hỏng bị từ chối, thường làm kết nối thất bại.

**Câu 6.** Cipher suite `TLS_AES_256_GCM_SHA384` có bắt buộc certificate RSA-4096 hay group P-384 không?

**Đáp án:** Không. Suite quy định AEAD và HKDF/transcript hash; certificate signature scheme và key agreement group được thương lượng theo cơ chế riêng.

**Câu 7.** Trong demo Java, vì sao client có `trust.p12` riêng nhưng vẫn cần `setEndpointIdentificationAlgorithm("HTTPS")`?

**Đáp án:** Truststore quyết định public key/CA nào có thể tin; hostname check bảo đảm certificate thực sự dành cho endpoint đang kết nối. Hai kiểm tra bổ sung nhau.

**Câu 8.** Khi private key certificate dài hạn bị lộ sau khi một phiên ECDHE đã kết thúc, có chắc attacker giải mã được packet capture cũ không?

**Đáp án:** Không tự động. ECDHE với ephemeral key/traffic secret được bảo vệ và xóa đúng giúp forward secrecy. Tuy nhiên nếu lộ traffic secrets/endpoint memory/log khi phiên chạy thì vẫn có nguy cơ.

**Câu 9.** Vì sao không tự lấy output `KeyAgreement.generateSecret()` làm AES key rồi gọi GCM cho socket?

**Đáp án:** TLS đòi HKDF key schedule, transcript binding, nonce/sequence, handshake authentication, Finished, record framing và policy khác. Tự ghép thiếu các thành phần này có thể làm hệ thống dễ MITM/nonce reuse/record confusion.

**Câu 10.** Trong resumption TLS 1.3 không thấy `Certificate` mới trên packet trace. Có phải MITM đã xóa chứng thư?

**Đáp án:** Chưa thể kết luận. PSK resumption có thể không gửi certificate mới; phải phân tích handshake mode, ticket/PSK binding và trust của phiên gốc.

**Câu 11.** API HTTP nhận POST 0-RTT được mã hóa hoàn toàn bằng TLS 1.3. Có thể bỏ anti-replay nghiệp vụ không?

**Đáp án:** Không. 0-RTT có replay risk; cần policy hạn chế các thao tác có side effect và idempotency/anti-replay phù hợp.

**Câu 12.** Java hiển thị `SSLSession.getCipherSuite()` là `TLS_AES_128_GCM_SHA256`. Có chắc server certificate đã được hostname check không?

**Đáp án:** Không chỉ từ cipher suite. Cần xác minh trust manager và endpoint identification đã được bật/thi hành cho kết nối theo policy; cipher suite chỉ mô tả bảo vệ record/hash.

## 19. Checklist khi cấu hình TLS trong ứng dụng Java

- [ ] Ưu tiên **TLS 1.3** khi khả thi; chỉ hỗ trợ TLS 1.2 nếu cần tương thích và được security policy cho phép.
- [ ] Kiểm tra đúng provider/JDK hỗ trợ protocol, cipher suites, groups và signature schemes.
- [ ] Certificate server có **SAN hostname/IP**, hợp lệ thời gian, key usage/EKU và chain tới trust anchor đúng.
- [ ] **Không sử dụng trust-all** hoặc hostname verifier luôn trả true.
- [ ] Raw `SSLSocket` client có endpoint identification thích hợp; HTTP client framework không bị tùy chỉnh làm mất hostname check.
- [ ] Truststore/keystore/private key được cấp và bảo vệ ngoài source/repo, có rotation và giám sát hết hạn.
- [ ] Phân biệt long-term identity key, ephemeral agreement key và symmetric traffic keys.
- [ ] Không tự lập trình TLS wire protocol, HKDF record keys, AES-GCM nonce hay Finished verification.
- [ ] Nếu dùng mTLS, có client certificate policy và identity-to-authorization mapping rõ ràng.
- [ ] Kiểm soát PSK tickets/resumption/0-RTT; từ chối side-effecting early data khi chưa có anti-replay phù hợp.
- [ ] Log **protocol, cipher suite, peer identity đã chuẩn hóa, error code** theo quyền, không log private key/traffic secrets/plaintext nhạy cảm.
- [ ] Có kiểm thử lỗi certificate, hostname mismatch, không chung cipher suite, disabled protocol, stale truststore và key rotation.
- [ ] Kiểm tra cả đầu ra cuối cùng của reverse proxy/load balancer: TLS có thể terminate ở proxy, kết nối proxy -> backend cần bảo vệ riêng theo threat model.
- [ ] Sau TLS, ứng dụng vẫn thực hiện authentication, authorization và validation dữ liệu như bình thường.

## 20. Tài liệu chính thức

1. [RFC 9846 — The Transport Layer Security (TLS) Protocol Version 1.3](https://www.rfc-editor.org/rfc/rfc9846.html) (07/2026): đặc tả hiện hành thay thế RFC 8446; handshake, key agreement, `CertificateVerify`, `Finished`, HKDF/traffic key schedule và AEAD records.
2. [RFC 8446 — TLS 1.3 (2018)](https://www.rfc-editor.org/rfc/rfc8446.html): tài liệu lịch sử vẫn thường được viện dẫn trong JDK/docs; đã được RFC 9846 thay thế.
3. [RFC 9525 — Service Identity in TLS](https://www.rfc-editor.org/rfc/rfc9525.html): xác định/xác minh danh tính server/service trong TLS.
4. [RFC 5869 — HKDF](https://www.rfc-editor.org/rfc/rfc5869.html): cơ sở HKDF-Extract và HKDF-Expand.
5. [Oracle Java SE 17 — JSSE Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-secure-socket-extension-jsse-reference-guide.html): `SSLSocket`, `SSLEngine`, trust, hostname verification, TLS 1.3 và các lưu ý triển khai.
6. [Java SE 17 — SSLContext](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLContext.html): TLS engine và cách cấu hình key/trust managers.
7. [Java SE 17 — SSLParameters](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLParameters.html): enabled protocols, cipher suites, endpoint identification.
8. [Java SE 17 — SSLSocket](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLSocket.html): TLS over TCP sockets, `startHandshake` và streams.
9. [Java SE 17 — SSLSession](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLSession.html): xem protocol, cipher suite và peer certificates.
10. [Java SE 17 — TrustManagerFactory](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/TrustManagerFactory.html) và [KeyManagerFactory](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/KeyManagerFactory.html): cơ chế cung cấp trust và identity key material cho JSSE.
11. [Java SE 17 — KeyAgreement](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/KeyAgreement.html): API minh họa X25519, **không thay thế TLS**.
12. [Oracle keytool Reference](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html): tạo cặp khóa test, xuất/import public certificate và quản lý keystore.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tls-foundation-002">15.2 Luồng bắt tay, xác thực máy chủ và xác thực hai chiều mTLS</a>

<details>
<summary>Xem chi tiết</summary>

# 15.2. Luồng bắt tay, xác thực máy chủ và xác thực hai chiều mTLS

## Mục tiêu học tập

Sau bài này, bạn có thể:

1. Mô tả **TLS (Transport Layer Security)** bảo vệ một kết nối bằng cách thỏa thuận tham số mã hóa, tạo khóa phiên, xác thực danh tính và bảo vệ dữ liệu trao đổi khỏi đọc trộm/chỉnh sửa.
2. Đọc đúng thứ tự các thông điệp chính trong một **TLS 1.3 full handshake**, đặc biệt `ClientHello`, `ServerHello`, `EncryptedExtensions`, `Certificate`, `CertificateVerify`, `Finished` và `CertificateRequest` khi xác thực client.
3. Phân biệt **one-way TLS** (client xác thực server) với **mutual TLS/mTLS** (cả hai phía xác thực chứng thư của nhau), kể cả vai trò **keystore**, **truststore** và CA riêng ở mỗi phía.
4. Giải thích **xác minh chuỗi chứng thư X.509, hostname/SAN, KeyUsage/EKU, chứng minh sở hữu private key** và vì sao chỉ “kiểm chữ ký CA đúng” hoặc “có certificate” không đủ.
5. Hiểu mối liên hệ giữa **(EC)DHE key exchange, HKDF, handshake transcript, Finished, AEAD record protection, forward secrecy** và những khác biệt quan trọng giữa TLS 1.3, TLS 1.2, resumption và 0-RTT.
6. Dùng **Java 17+ JSSE** kết hợp `keytool` tạo lab CA/leaf certificates, chạy TLS server và client thực tế ở hai chế độ, **giữ hostname verification bật**, minh họa hành vi thiếu client certificate trong mTLS.
7. Nhận diện các sai lầm vận hành: `trust-all`, `HostnameVerifier` luôn true, nhầm mTLS với authorization, nhầm serverAuth/clientAuth, không kiểm certificate expiry/revocation, chứng thư đúng nhưng endpoint sai.

**Phạm vi:** Thuộc **Java Advance – security-cryptography / 15. Nền tảng TLS**. Bài tập trung vào **luồng bắt tay và hai hình thức xác thực**; các câu lệnh tạo CA/certificate và code socket dùng để học, không phải cấu hình PKI hay deployment production hoàn chỉnh.

---

## 1. TLS bảo vệ cái gì và tại sao phải “bắt tay”?

TCP cung cấp dòng byte hai chiều nhưng **không tự mã hóa dữ liệu, xác thực máy chủ hoặc chống thay đổi nội dung**. Một kẻ đứng giữa trên đường truyền có thể nghe, sửa hoặc giả mạo dữ liệu nếu ứng dụng không có lớp bảo vệ khác.

TLS đặt một giao thức mật mã lên transport phù hợp, phổ biến nhất là TCP cho HTTPS. **Handshake** tạo một bối cảnh bảo mật mà cả hai đầu thống nhất:

- **Phiên bản và tham số**: ví dụ TLS 1.3, bộ mã hóa AEAD/cipher suite và các thuật toán được phép.
- **Key agreement**: bí mật chung từ trao đổi khóa tạm thời, thường (EC)DHE, được dẫn xuất thành khóa handshake và application traffic qua key schedule; hoặc dùng PSK/resumption theo giao thức.
- **Server authentication**: client kiểm chain của chứng thư server đến trust anchor, đúng hostname/endpoint identity và thông điệp chứng minh private key hợp lệ.
- **Client authentication (tùy chọn)**: server có thể yêu cầu client certificate và xác minh nó; đây là **mTLS** khi client cũng xác thực server.
- **Handshake integrity**: những bản tóm lược handshake (transcript) được ràng buộc vào xác thực và `Finished`, phát hiện sửa đổi/giả mạo trao đổi.
- **Record protection**: application data sau handshake được mã hóa và xác thực bằng AEAD; mỗi chiều dùng traffic keys/nonce và sequence tương ứng do giao thức quản lý.

```text
Application: HTTP / gRPC / ứng dụng Java riêng
                   │
                   ▼
     TLS handshake → authenticated traffic keys
                   │
                   ▼
      TLS record protection (AEAD)
                   │
                   ▼
             TCP connection
```

**TLS không thay thế business authorization**: một client đã có TLS session hợp lệ không vì vậy được đọc mọi database record hoặc thực hiện mọi thao tác. TLS cũng **không đảm bảo dữ liệu an toàn sau khi đã giải mã trong ứng dụng**, không loại bỏ lỗi XSS/SQL injection và không che metadata như IP hoặc toàn bộ tính chất lưu lượng.

### 1.1. Các thuật ngữ dễ nhầm

| Thuật ngữ | Ý nghĩa | Không đồng nghĩa với |
| --- | --- | --- |
| **Handshake** | Trình tự thiết lập khóa, kiểm thông số, xác thực peer và hoàn tất kết nối bảo mật | HTTP request hoặc một lần mã hóa AES |
| **TLS record** | Đơn vị vận chuyển dữ liệu được mã hóa/xác thực dưới traffic keys | X.509 certificate |
| **Cipher suite (TLS 1.3)** | Định danh AEAD và hash dùng trong key schedule, ví dụ `TLS_AES_128_GCM_SHA256` | Gói đầy đủ key-exchange/authentication algorithms như cách đặt tên ở TLS 1.2 |
| **Key share** | Public value cho key agreement tạm thời, thường ECDHE | Private key nằm trong server certificate |
| **Certificate chain** | Leaf + intermediate(s) cần để xây đường đến trust anchor | Truststore đã tin sẵn mọi certificate do server gửi |
| **Keystore** | Kho chứa private key/certificate chain của chính bên này | Truststore dùng tin các CA của bên kia |
| **Truststore** | Cơ sở trust anchors/certificates được bên xác minh chấp thuận | Private key của server |
| **Server authentication** | Client xác minh server identity và proof-of-possession trong handshake | Server xác thực client |
| **Client authentication** | Server xác minh client identity và proof-of-possession | Quyền API của client |
| **mTLS** | Cả hai chiều thực hiện xác thực TLS phù hợp | Hai lớp TLS lồng nhau hoặc gửi username/password hai lần |
| **ALPN** | Thỏa thuận application protocol như `h2`/`http/1.1` | Bằng chứng danh tính của peer |
| **SNI** | Tên server client chỉ ra để hỗ trợ server chọn certificate/virtual host | Cơ chế kiểm chứng hostname thay client |
| **Session resumption** | Tái sử dụng trạng thái/PSK được TLS cho phép ở kết nối tiếp theo | Chắc chắn thực hiện lại đầy đủ certificate exchange như lần đầu |

---

## 2. One-way TLS: client xác thực server

Ở mô hình HTTPS thông thường, **server trình bày chứng thư**, còn client **không bắt buộc** trình bày client certificate. Đây là **server-authenticated TLS**, hay one-way TLS.

```text
CLIENT                                  SERVER
  |                                       |
  | ClientHello ------------------------->|
  |<------------------------- ServerHello |
  |<------------------ EncryptedExtensions|
  |<-------------------------- Certificate|
  |<-------------------- CertificateVerify|
  |<--------------------------------Finished|
  |                                       |
  | Validate server chain, SAN/hostname, |
  | certificate purpose, private-key    |
  | possession via handshake, transcript|
  |                                       |
  | Finished ---------------------------> |
  |                                       |
  |<======== protected app data =========>|
```

Sơ đồ rút gọn là **full TLS 1.3 certificate-based handshake**. Nó bỏ qua alert, optional messages, HelloRetryRequest và chi tiết record/key schedule. Các message sau `ServerHello` trong TLS 1.3 được bảo vệ bằng handshake traffic keys thích hợp (với ngoại lệ/phần nhìn thấy được theo cấu trúc bản ghi, protocol details). Không dùng sơ đồ này như một packet capture chính xác ở mọi phiên bản.

### 2.1. Bước 1 – `ClientHello`

Client gửi các khả năng hỗ trợ, ví dụ:

- Những phiên bản TLS được hỗ trợ qua extension `supported_versions`.
- Các TLS 1.3 cipher suites có thể sử dụng.
- `supported_groups` và `key_share` cho key agreement.
- `signature_algorithms` để các phía biết loại signature scheme chấp nhận.
- **SNI** cho tên server dự định và **ALPN** nếu ứng dụng cần thỏa thuận protocol.
- Có thể có thông tin PSK/session resumption, tùy kiểu handshake.

Client **không gửi raw secret key hoặc mật khẩu keystore** trong `ClientHello`. Một `key_share` chứa dữ liệu public của cơ chế thỏa thuận khóa; private ephemeral key vẫn được giữ cục bộ.

**SNI không phải “kiểm server đáng tin”:** SNI chủ yếu giúp server chọn virtual host/certificate phù hợp; client vẫn phải xác thực certificate và tên dự định kết nối.

### 2.2. Bước 2 – `ServerHello` và shared secret

Server chọn phiên bản TLS/cipher suite và key share thích hợp. Hai phía dùng private key ephemeral của mình kết hợp public key share phía kia để có shared secret tương ứng, rồi cùng chạy **key schedule** của TLS 1.3 dựa trên HKDF để dẫn xuất các traffic secrets.

```text
Client ephemeral secret + Server public key share ──┐
                                                    ├─ same key agreement secret
Server ephemeral secret + Client public key share ──┘
                                  │
                                  ▼
                  HKDF-based TLS 1.3 key schedule
                                  │
                handshake traffic keys / Finished keys
                                  │
                       application traffic keys
```

Đây là hình dung về key agreement; không phải phép nối byte/kDF mà ứng dụng nên tự lập trình. Trong TLS 1.3, RSA certificate có thể dùng **để ký xác thực**, không phải “RSA encrypt premaster secret” như một số handshake TLS 1.2 cũ. **(EC)DHE** tạo cơ sở cho **forward secrecy** khi ephemeral secrets được quản lý/xóa đúng: bị lộ server certificate private key về sau không tự cho phép giải mã các phiên TLS 1.3 đã ghi lại trước đó.

**Chú ý:** Forward secrecy không bảo vệ nếu attacker đã kiểm soát endpoint trong phiên, đọc traffic keys/endpoint plaintext, hoặc giả mạo được server lúc bắt tay. Resumption/PSK và các phương thức trao đổi khóa khác có tính chất riêng cần đánh giá đúng giao thức.

### 2.3. Bước 3 – Server gửi chứng thư và chứng minh key possession

Server gửi `Certificate` với leaf certificate (và thường intermediate certificates). Những thành phần quan trọng:

1. **SubjectAlternativeName** (SAN) phải bao phủ endpoint identity mà client dự định kết nối, như `DNS:api.example.com` hoặc SAN IP phù hợp khi kết nối bằng IP literal.
2. **Certificate chain** phải xây được path đến root/trust anchor được client chấp thuận, với ký số CA và những ràng buộc CA/BasicConstraints/name constraints, key usage và policy.
3. **Validity/revocation** được đánh giá theo thời điểm và chính sách; certificate chưa hết hạn vẫn có thể bị thu hồi.
4. **ExtendedKeyUsage** của server certificate thường cần `serverAuth` theo profile/policy; không coi clientAuth hoặc codeSigning là thay thế tùy tiện.
5. **`CertificateVerify`** chứa chữ ký do server thực hiện bằng private key tương ứng với leaf, trên transcript/cấu trúc có domain separation theo TLS 1.3. Điều này chứng minh server đang nắm private key hợp lệ cho handshake, chứ không chỉ sao chép một PEM công khai.

```text
CA trusted? ----yes----+
                       |
Chain valid? ---yes----+
                       |
Hostname SAN? --yes----+---> Có thể chấp nhận server identity
                       |     nếu các kiểm tra TLS/policy khác đều đạt
Key usage? ----yes-----+
                       |
CertificateVerify? yes-+
                       |
Finished? ----yes------+
```

**Một leaf certificate chứa public key không đủ:** bất kỳ ai cũng có thể gửi bản sao certificate của `api.example.com`. Điều làm attacker khó giả làm server là họ còn phải tạo được `CertificateVerify` hợp lệ bằng **server private key** và đáp ứng toàn bộ trao đổi khóa/handshake.

### 2.4. Bước 4 – `Finished` và application data

`Finished` bảo vệ ranh giới hoàn tất handshake bằng MAC/verify data ràng buộc với transcript và các handshake secrets phù hợp. Client chỉ coi session là bảo mật sau các phép kiểm TLS/identity cần thiết.

Sau khi handshake hoàn thành, **application data** (HTTP request/response hoặc bytes từ `SSLSocket`) được bảo vệ bằng record-layer AEAD. Mỗi chiều có traffic keys riêng theo key schedule và quản lý nonce/sequence nội bộ, **ứng dụng không tự lấy AES-GCM nonce cho TLS record**.

**Không nhầm:** server authenticated TLS cho client biết đang giao tiếp với server được phép; nó **không tự cung cấp danh tính client**. Trong one-way TLS, phía server thường cần thêm bearer token, session cookie, OAuth/OIDC hoặc xác thực ứng dụng riêng để biết requester là ai.

---

## 3. mTLS: server cũng xác thực client

**mTLS – mutual TLS** dùng cùng nền tảng TLS nhưng bổ sung bước **server yêu cầu client certificate** và xác minh danh tính phía client. Client vẫn phải xác thực server như one-way TLS; không có ngoại lệ “vì server tin client nên client có thể bỏ hostname verification”.

```text
CLIENT                                          SERVER
  |                                               |
  | ClientHello ---------------------------------> |
  |<---------------------------------- ServerHello |
  |<----------------------------- EncryptedExtensions
  |<-------------------------------- CertificateRequest
  |<-------------------------------------- Certificate
  |<-------------------------------- CertificateVerify
  |<----------------------------------------- Finished
  |                                               |
  | Verify SERVER chain + SAN/hostname + signature|
  |                                               |
  | Client Certificate --------------------------> |
  | Client CertificateVerify --------------------> |
  | Client Finished -----------------------------> |
  |                                               |
  |        SERVER verifies CLIENT certificate     |
  |        trust chain / purpose / possession     |
  |                                               |
  |<============== protected app data ===========> |
```

Đây là **luồng TLS 1.3 mTLS trong initial/full handshake**; thứ tự có thể thay theo phiên bản/kiểu handshake, và các trường hợp optional/resumption/post-handshake auth có quy tắc riêng. `CertificateRequest` là tín hiệu server yêu cầu client authentication; nếu không có certificate phù hợp client có thể gửi danh sách trống, nhưng server yêu cầu xác thực **bắt buộc** sẽ từ chối handshake.

### 3.1. Ai sở hữu khóa nào?

| Tài sản | Phía server | Phía client |
| --- | --- | --- |
| Server private key | **Giữ bí mật** trong server keystore/HSM | Không được cấp |
| Server leaf + chain | Trình bày trong handshake | Nhận để xác minh server |
| Server-trust CA/root | Không cần làm tin cậy cho chính mình | **Client truststore** chứa trust anchor được chấp thuận |
| Client private key | Không được cấp | **Giữ bí mật** trong client keystore/HSM |
| Client leaf + chain | Nhận khi server yêu cầu | Trình bày trong handshake |
| Client-trust CA/root | **Server truststore** chứa CA cấp cho clients được phép | Không mặc nhiên dùng làm server trust anchor |
| Application authorization | **Enforce** identity → role/tenant/action | Chỉ gửi yêu cầu trong quyền được cấp |

Trong lab, root CA dùng chung để giảm thao tác; trong production **có thể và thường nên phân biệt CA/profile phục vụ serverAuth với CA/profile cho clientAuth** để giảm phạm vi tin cậy. Việc server có `server.p12` chưa đồng nghĩa server đã tin `client.p12`: server phải được cấu hình riêng trust anchors và yêu cầu client authentication.

### 3.2. `CertificateRequest` và `CertificateVerify`

Khi server bật mTLS:

1. Trong TLS 1.3 full handshake, server gửi `CertificateRequest`; yêu cầu có thể mang danh sách CA chấp nhận, signature algorithms và extensions.
2. Client chọn một certificate chain/private key phù hợp; thuật toán lựa chọn phụ thuộc JSSE key manager, key type, CA hints và khả năng ký.
3. Client gửi `Certificate` và `CertificateVerify` để chứng minh sở hữu private key trên transcript phù hợp.
4. Server xác thực chain/client certificate theo trust anchors của **server**, kiểm mục đích `clientAuth` và những ràng buộc/identity policy.
5. `Finished` hoàn tất handshake integrity. Server chỉ cho client dùng kênh khi handshake authentication đáp ứng chính sách.

**Chỉ gửi một certificate công khai không đủ để xác thực mTLS.** Bất kỳ ai có bản PEM công khai đều có thể gửi nó, nhưng không thể hoàn thành proof-of-possession nếu không có private key tương ứng.

### 3.3. `needClientAuth` và `wantClientAuth`

Trong JSSE server:

- `SSLServerSocket.setNeedClientAuth(true)` hoặc `SSLEngine.setNeedClientAuth(true)`: client certificate là **bắt buộc**; nếu client không trình bày credential hợp lệ, handshake bị từ chối.
- `setWantClientAuth(true)`: server **đề nghị** client certificate nhưng có thể chấp nhận client không có certificate. Ứng dụng phải tự quyết định đường xử lý identity rỗng.
- Mặc định server thường không yêu cầu client certificate. Khi client không được yêu cầu, có sẵn `client.p12` cũng không có nghĩa client tự gửi cert.
- Khi bật need/want, phải có trust manager và CA policy dành cho client identities, không được chỉ bật một boolean rồi tin mọi client certificate.

Đối với Spring Boot, Netty, gRPC, proxy/ingress/service mesh, các tên cấu hình có thể khác nhưng ý nghĩa **require client auth** và **trusted client roots** vẫn cần rõ. Cần biết mTLS **kết thúc ở đâu** (edge proxy, sidecar hay ứng dụng JVM).

### 3.4. mTLS xác thực danh tính, không tự phân quyền

Ví dụ một client có certificate:

```text
Subject: CN=payments-worker, O=Example
SAN: URI:spiffe://company.example/ns/prod/sa/payments-worker
EKU: clientAuth
Issuer: Internal Workload CA
```

Khi handshake thành công, server có thể map identity được xác minh thành **service principal** như `payments-worker`, nhưng vẫn phải kiểm **service đó có được đọc tenant T hoặc gọi operation refund không**. Nếu mọi chứng thư được CA cấp đều được gán role admin, mTLS đã bị lạm dụng thành một chính sách authorization quá rộng.

Không lấy một Subject DN, hostname hoặc header `X-Client-Cert` chưa được proxy tin cậy bảo vệ rồi cấp quyền. Với proxy termination, cần bảo đảm header identity được **xóa/tạo lại** bởi proxy tin cậy, không cho client Internet tự khai.

---

## 4. TLS 1.3 và TLS 1.2 khác nhau ở chỗ nào?

| Khía cạnh | TLS 1.3 | TLS 1.2 |
| --- | --- | --- |
| Cơ chế key exchange | Thiết kế dùng cơ chế cho forward secrecy trong full handshake phổ biến như (EC)DHE, hoặc PSK/resumption theo chuẩn | Có thể dùng ECDHE; các suite cũ còn dùng static RSA key transport (nay không nên chọn) |
| Cipher suite naming | AEAD + hash, ví dụ `TLS_AES_128_GCM_SHA256`; key exchange/signature được thỏa thuận độc lập | Tên suite có thể gồm key exchange, authentication, symmetric cipher, MAC/hash |
| Sau `ServerHello` | Phần lớn handshake messages sau mốc này được bảo vệ bởi handshake traffic keys | Nhiều certificate/handshake messages truyền rõ theo quy trình TLS 1.2 cổ điển |
| Server certificate proof | `CertificateVerify` trong full certificate authentication | Có chữ ký trong ServerKeyExchange ở ECDHE suites; exact flow khác, không ép công thức TLS 1.3 |
| Server yêu cầu client certificate | `CertificateRequest` trong TLS 1.3 full handshake, thường sau EncryptedExtensions | `CertificateRequest` có vị trí/sequence theo TLS 1.2 |
| Key derivation | HKDF-based TLS 1.3 key schedule | TLS 1.2 PRF/key expansion theo RFC 5246 |
| 0-RTT | Có thể có early data trong PSK resumption với rủi ro replay, phụ thuộc hỗ trợ | Không phải tính năng handshake TLS 1.2 tiêu chuẩn tương tự |
| Chuẩn nên dùng | Thường **ưu tiên TLS 1.3** khi client/server tương thích | Chỉ giữ khi cần interoperability với cấu hình mạnh và policy cho phép |

**Không áp mô tả “ClientKeyExchange / ChangeCipherSpec làm khóa như TLS 1.2” cho TLS 1.3.** Trong TLS 1.3, có record `ChangeCipherSpec` tương thích giữa một số triển khai, nhưng không có vai trò chuyển khóa theo cách của TLS 1.2; keys được kích hoạt qua state của handshake/key schedule.

### 4.1. Session resumption có thể không gửi lại certificate

Một kết nối TLS 1.3 mới có thể **resume session** bằng PSK/ticket từ kết nối trước. Trong luồng resumption, không nhất thiết có `Certificate`/`CertificateVerify` mới như full certificate handshake. Vì vậy packet capture chỉ có thể giải thích đúng nếu bạn biết đây là full handshake, PSK-only, hay kết hợp PSK với fresh key exchange.

Đặc biệt với mTLS, nếu tổ chức đòi **kiểm danh tính client mới trên mọi request**, cần hiểu cách server, TLS stack, proxy và session resumption gắn identity vào session, và tác động của rotation/revocation. Không mặc nhiên “không thấy client Certificate trong capture = không có mTLS” hay “resumption luôn revalidate client certificate”.

### 4.2. 0-RTT không thay thế xác thực client

TLS 1.3 **0-RTT early data** là tùy chọn của resumption; early data có **rủi ro replay** ngay cả khi bản ghi được mã hóa. Không đặt những thao tác không idempotent hoặc chuyển tiền vào 0-RTT mà không có replay protections. Không nhầm 0-RTT với client-certificate authentication vừa được thực hiện đầy đủ trong cùng full handshake.

### 4.3. ALPN và SNI nằm ở tầng nào?

- **SNI**: gợi ý tên server mà client muốn kết nối để server/proxy chọn certificate/route. Cần tránh sai lệch giữa SNI, HTTP Host/:authority, certificate SAN và policy routing.
- **ALPN**: ví dụ thỏa thuận `h2` cho HTTP/2 hoặc `http/1.1`. ALPN không cấp client identity; nó là protocol negotiation.
- **SAN/hostname validation**: phép kiểm **riêng** mà client thực hiện cho danh tính endpoint mong muốn; không được bỏ chỉ vì SNI đã gửi.
- **HTTP Authorization**: diễn ra ở tầng ứng dụng sau TLS; thường vẫn cần ngay cả khi mTLS xác thực service identity.

---

## 5. Tại sao X.509, CA, keystore và truststore quan trọng cho handshake?

**Chứng thư X.509** là một cấu trúc có chữ ký CA liên kết public key với subject/SAN và các extensions theo chính sách. Trong TLS, chứng thư giúp client/server xác minh public key được trình bày thuộc danh tính được chấp nhận.

### 5.1. Server authentication cần những điều kiện nào?

Với `https://api.example.com`, client không chỉ làm một check:

1. Certificate có cấu trúc X.509 hợp lệ và thuật toán/key strength được chính sách chấp nhận.
2. Chain từ leaf qua intermediate(s) đến **trust anchor** mà client thực sự chấp thuận.
3. Thời hạn `notBefore/notAfter` và revocation/status theo policy phù hợp.
4. BasicConstraints/KeyUsage/EKU và path constraints, đặc biệt CA issuer phải có quyền `keyCertSign` phù hợp.
5. Định danh đích `api.example.com` khớp **DNS Subject Alternative Name** thích hợp; kết nối bằng IP literal cần SAN **iPAddress** phù hợp.
6. Chứng minh server đang sở hữu private key liên quan qua handshake signature/proof, không chỉ sở hữu bản sao leaf certificate.
7. Kiểm transcript/`Finished` và các tham số TLS không bị thay đổi.
8. Kiểm ứng dụng chọn đúng URL, trust configuration và các policy bảo mật liên quan.

`X509Certificate.verify(issuerPublicKey)` chỉ kiểm **chữ ký certificate với public key đã đưa vào**, không tự đảm bảo issuer trusted, hostname đúng hoặc peer đang sở hữu private key. Tương tự, `CertPathValidator("PKIX")` không tự biết hostname của URL nếu ứng dụng không kết hợp endpoint identity verification.

**Không dùng `CN` thay SAN một cách tùy tiện.** TLS identity hiện đại dựa trên các dạng SAN theo quy tắc service identity; JSSE/HTTPS phải làm endpoint identification đúng.

### 5.2. Khi dùng mTLS, client certificate phải có purpose phù hợp

Trong mô hình TLS server nhận client certificate:

- Server truststore có CA/trust anchors cho **client identities**, không chỉ CA dùng cấp server certificates.
- Client leaf có ExtendedKeyUsage phù hợp, thường `clientAuth` (OID `1.3.6.1.5.5.7.3.2`), KeyUsage phù hợp với signature mechanism.
- Server phải quyết định **có chấp nhận client identity này không**, không chỉ “bất kỳ chain dưới root đều được phép”.
- Các profile nội bộ có thể dùng SAN URI, DNS, email hoặc Subject DN theo policy; phải xác định identity source rõ và tránh coi CN tự do là quyền truy cập.
- Nếu certificate bị revoke, mất private key hoặc service không còn quyền, cần vòng đời key/trust/revocation và authorization cập nhật.

### 5.3. Keystore và truststore

```text
ONE-WAY TLS
===========
Server:
  server.p12 = server private key + server leaf + chain
Client:
  truststore.p12 = trusted server CA/root(s)
  (không cần client private key)

mTLS
====
Server:
  server.p12 = server private key + server leaf + chain
  truststore.p12 = trusted client CA/root(s)
Client:
  client.p12 = client private key + client leaf + chain
  truststore.p12 = trusted server CA/root(s)
```

**Truststore không phải nơi copy private key của đối tác.** Một chứng thư CA công khai không nhất thiết bí mật, nhưng **truststore integrity** là an ninh quan trọng: attacker thay trust anchors có thể khiến ứng dụng tin certificate giả.

**Keystore path đúng nhưng password sai** → JVM không load private key. **Truststore có cert riêng lẻ không được phép** → chain validation sai hoặc mở rộng trust quá rộng. **Certificate chain thiếu intermediate** → client có thể không xây được path đến root dù root đã trusted.

---

## 6. Java JSSE: API nào tương ứng với hoạt động nào?

**JSSE** (*Java Secure Socket Extension*) là nền tảng Java cho TLS. Một số API nền tảng trên Java 17:

| API | Trách nhiệm chính | Lưu ý |
| --- | --- | --- |
| `SSLContext` | Thiết lập các key managers, trust managers và TLS engine configuration | Cấu hình context sai có thể dẫn đến tin nhầm CA |
| `KeyStore` | Nạp file `PKCS12` hoặc provider store chứa certificate/private-key entries | Có file/alias không tự tạo trust |
| `KeyManagerFactory` / `X509KeyManager` | Chọn private key/certificate **của chính phía này** trình bày khi cần | mTLS client phải có client identity, server phải có server identity |
| `TrustManagerFactory` / `X509TrustManager` | Xác minh certificate chain của **peer** với trust anchors/policy | Tuyệt đối không dùng custom trust-all manager |
| `SSLSocketFactory` | Tạo client TLS sockets | Với raw `SSLSocket` phải cấu hình endpoint identification phù hợp |
| `SSLServerSocketFactory` | Tạo server TLS listen socket | Server quyết định `setNeedClientAuth`/`setWantClientAuth` |
| `SSLSocket` | TLS kết nối, `startHandshake`, ứng dụng đọc/ghi plaintext bytes | Dữ liệu trên wire được TLS record bảo vệ |
| `SSLEngine` | TLS nonblocking/transport-agnostic theo framework | Phải quản lý handshake state, buffers và task callbacks đúng |
| `SSLParameters` | Cấu hình endpoint identification, protocols, cipher suites, ALPN... | `setEndpointIdentificationAlgorithm("HTTPS")` giúp xác minh host ở raw client |
| `SSLSession` | Thông tin protocol, peer certificates/principal, cipher suite | Thông tin peer không đồng nghĩa authorization |
| `HttpsURLConnection` / `HttpClient` | HTTP(S) client trên TLS | Thường nên dùng stack HTTP chuẩn thay vì raw TLS socket |
| `SSLHandshakeException` | Lỗi trong handshake, có thể do chain/hostname/client cert/protocol | Không tắt authentication để “chạy được” |

### 6.1. `setNeedClientAuth(true)` không phải là “cài TLS cho cả hai phía”

Bật `needClientAuth` chỉ là yêu cầu **client trình bày và xác thực certificate**. Server vẫn phải có keystore chứa **server private key/cert**, phải có truststore đúng CA cấp client, và application authorization phải được enforce. Nếu server bật needClientAuth nhưng client không có cert hoặc cert không tin cậy, handshake không hoàn thành.

### 6.2. Raw `SSLSocket` cần endpoint identification rõ ràng

Ví dụ sử dụng JSSE cho client:

```java
SSLSocket socket = (SSLSocket) context.getSocketFactory()
        .createSocket("api.example.com", 443);
SSLParameters params = socket.getSSLParameters();
params.setEndpointIdentificationAlgorithm("HTTPS");
socket.setSSLParameters(params);
socket.startHandshake();
```

Đây là **đoạn minh họa không độc lập**: cần imports JSSE, `SSLContext context` đã init bằng truststore đúng, kiểm network/exception và giải phóng socket. Thuật toán `HTTPS` dùng quy tắc TLS endpoint identification của JSSE; không tự so hostname bằng `contains` hoặc disable hostname verification.

Raw `SSLSocket` **không nên được giả định tự kiểm hostname như một HTTPS client** nếu bạn chưa đặt cấu hình endpoint identification. Với Java `HttpClient`/`HttpsURLConnection`, hãy giữ xác minh TLS/hostname theo mặc định an toàn, tránh property/`HostnameVerifier` bypass.

### 6.3. mTLS ở reverse proxy hoặc service mesh

Nhiều hệ thống chấm dứt TLS tại ingress/load balancer/Envoy/service mesh. Tại đó mTLS có thể xác thực **workload ngang hàng với proxy**, trong khi Java app nhận kết nối nội bộ từ proxy. Vì vậy:

- Xác định rõ **TLS endpoint** nào kiểm client cert và truststore nằm ở đâu.
- Nếu proxy forward `X-Client-Cert`/principal headers, phải chặn client Internet tự đặt header; proxy tin cậy phải strip/rewrite và kết nối proxy→app cần được bảo vệ tương ứng.
- Không để Java app coi mọi HTTP request từ proxy là cùng identity rồi bỏ authorization theo tenant/action.
- Kiểm rotation/expiry/revocation của workload certificates trong control plane, log identity và policy giữa các hops.

---

## 7. Lab Java 17+: chuẩn bị root CA, server certificate và client certificate

Lab sau dùng **keytool có sẵn với JDK 17**. Mục tiêu là làm rõ mỗi bên sở hữu private key gì và tin CA nào. Ta dùng một **training root CA** ký trực tiếp cả server leaf và client leaf, với **ExtendedKeyUsage khác nhau**.

**Cảnh báo bảo mật:**

- Mật khẩu `changeit`, root CA và private keys tạo ở đây **chỉ dành cho môi trường học**. Không reuse trong production và không commit file `.p12` vào git.
- Các lệnh đưa store password vào command-line để thao tác dễ; trong hệ thống thật, password trên process list/shell history có thể lộ. Hãy dùng secret manager/interactive prompt/provider bảo mật theo policy.
- Chạy lệnh trong **thư mục lab trống** để không ghi đè keystore có sẵn. Không import CA demo vào `cacerts` toàn hệ thống.
- Nếu muốn chạy ví dụ song song hai mode, kết thúc server phiên trước rồi khởi động server mới trên port 8443; chương trình chỉ xử lý một kết nối mỗi lần.
- Dùng **`localhost`** làm hostname, vì chứng thư server sẽ có SAN `DNS:localhost`. Không thay bằng `127.0.0.1` mà giữ nguyên certificate, vì IP literal cần SAN dạng iPAddress.

### 7.1. Tạo training root CA

Lệnh sau sinh RSA CA key pair trong `root-ca.p12`, tự ký root certificate với `BasicConstraints CA:true` và `KeyUsage keyCertSign/cRLSign`:

```shell
keytool -genkeypair -alias lab-ca -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -dname "CN=Training TLS Root CA, O=Learning, C=VN" -validity 3650 -ext "BC:critical=ca:true,pathlen:0" -ext "KU:critical=keyCertSign,cRLSign" -keystore root-ca.p12 -storetype PKCS12 -storepass changeit -keypass changeit -noprompt
```

Export **public** CA certificate:

```shell
keytool -exportcert -alias lab-ca -keystore root-ca.p12 -storepass changeit -file root-ca.pem -rfc
```

`root-ca.p12` chứa private key của CA và phải được bảo vệ. `root-ca.pem` là public certificate có thể dùng để thiết lập trust anchor. Self-signed root **không tự đáng tin**, cho đến khi relying party đưa nó vào truststore có chủ đích. `pathlen:0` phù hợp vì lab root ký leaf trực tiếp, không tạo intermediate CA.

### 7.2. Sinh server key pair và CSR

```shell
keytool -genkeypair -alias tls-server -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -dname "CN=localhost, O=Learning, C=VN" -validity 365 -keystore server.p12 -storetype PKCS12 -storepass changeit -keypass changeit -noprompt
keytool -certreq -alias tls-server -keystore server.p12 -storepass changeit -file server.csr -ext "SAN=dns:localhost"
```

CSR chứa server public key và đề nghị cấp SAN. CA phải kiểm đúng identity/policy; việc CSR đề nghị `SAN=dns:localhost` không có nghĩa CA thực luôn phải chấp nhận mọi DNS name tùy ý.

### 7.3. CA ký server leaf với `serverAuth`

```shell
keytool -gencert -alias lab-ca -keystore root-ca.p12 -storepass changeit -infile server.csr -outfile server.pem -rfc -validity 365 -ext "SAN=dns:localhost" -ext "KU:critical=digitalSignature" -ext "EKU=serverAuth" -ext "BC=ca:false"
```

Nạp CA certificate rồi certificate reply vào `server.p12`:

```shell
keytool -importcert -alias lab-ca -file root-ca.pem -keystore server.p12 -storepass changeit -noprompt
keytool -importcert -alias tls-server -file server.pem -keystore server.p12 -storepass changeit -noprompt
```

Alias `tls-server` là private-key entry cùng certificate chain để JSSE server xuất trình. `server.pem` chỉ là public certificate, không thay private key trong `server.p12`.

### 7.4. Sinh client key pair và CSR

```shell
keytool -genkeypair -alias tls-client -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -dname "CN=demo-client, O=Learning, C=VN" -validity 365 -keystore client.p12 -storetype PKCS12 -storepass changeit -keypass changeit -noprompt
keytool -certreq -alias tls-client -keystore client.p12 -storepass changeit -file client.csr
```

Dưới đây CA ký một leaf **dành cho client TLS**, không phải server:

```shell
keytool -gencert -alias lab-ca -keystore root-ca.p12 -storepass changeit -infile client.csr -outfile client.pem -rfc -validity 365 -ext "KU:critical=digitalSignature" -ext "EKU=clientAuth" -ext "BC=ca:false"
```

Nhập root trước khi nhập certificate reply vào client keystore:

```shell
keytool -importcert -alias lab-ca -file root-ca.pem -keystore client.p12 -storepass changeit -noprompt
keytool -importcert -alias tls-client -file client.pem -keystore client.p12 -storepass changeit -noprompt
```

**So sánh:** `server.p12` chứa **server private key + server certificate (`serverAuth`)**. `client.p12` chứa **client private key + client certificate (`clientAuth`)**. Cả hai chain cùng về `lab-ca` chỉ để đơn giản lab, không có nghĩa production nên dùng một CA cấp mọi identity/purpose.

### 7.5. Tạo truststore riêng cho lab

```shell
keytool -importcert -alias lab-ca -file root-ca.pem -keystore truststore.p12 -storetype PKCS12 -storepass changeit -noprompt
```

Trong lab cả server và client đều được cấu hình tin root ở `truststore.p12`. Với one-way TLS, **client** dùng truststore này để kiểm server. Với mTLS, **server** cũng dùng để kiểm client. Truststore chứa **public root certificate**, không chứa private key của CA.

### 7.6. Kiểm nội dung chứng thư bằng keytool

```shell
keytool -printcert -file server.pem
keytool -printcert -file client.pem
keytool -list -v -keystore server.p12 -storetype PKCS12 -storepass changeit
keytool -list -v -keystore client.p12 -storetype PKCS12 -storepass changeit
keytool -list -v -keystore truststore.p12 -storetype PKCS12 -storepass changeit
```

Trong kết quả:

- Server certificate: `SAN DNSName: localhost` và `ExtendedKeyUsages serverAuth`.
- Client certificate: `ExtendedKeyUsages clientAuth`.
- KeyStore cho mỗi identity có `PrivateKeyEntry` dưới alias tương ứng.
- Truststore chứa `trustedCertEntry` cho `lab-ca`.
- Issuer của leaf tương ứng root đã ký; root có CA constraints phù hợp.

**Không có stdout cố định cho các lệnh keytool**, vì serial, fingerprints, public key, timestamps và dữ liệu ASN.1 thay đổi theo lần chạy. Hãy đối chiếu **thuộc tính có ý nghĩa** thay vì so nguyên văn output dài.

---

## 8. Java 17+ runnable: server-authenticated TLS và mTLS

Tạo file **`TlsHandshakeLab.java`** trong cùng thư mục với `server.p12`, `client.p12` và `truststore.p12`. Đây là **một Java class chạy ở hai process riêng**: process server lắng nghe cổng 8443, sau đó process client kết nối đến `localhost:8443`.

Hai mode:

- `oneway`: server trình bày server certificate; client xác thực chain và SAN hostname. **Không trình bày client certificate**.
- `mtls`: server bật `setNeedClientAuth(true)`; client nạp `client.p12` và trình bày client certificate khi được yêu cầu. Server kiểm client chain theo `truststore.p12`.
- Cả hai mode đều dùng **TLS 1.3** và cùng bộ ký tự thử `P`/`K`. Traffic keys/record encryption do JSSE thực hiện, không phải do demo tự viết AES.
- Code client raw `SSLSocket` **bật endpoint identification `"HTTPS"`** trước `startHandshake`, tránh một lỗi bảo mật rất phổ biến.

**Lưu ý:** `changeit` được hard-code trong code chỉ để chạy lab khớp lệnh `keytool`; production phải dùng phương thức secret injection/keystore access được bảo vệ. Đây là **minimal TLS learning app**, không phải server HTTP, không có certificate revocation service, IAM, ACL, audit, timeout hay rate limit production.

```java
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

public class TlsHandshakeLab {
    private static final char[] PASSWORD =
            "changeit".toCharArray(); // ONLY FOR THE TRAINING LAB
    private static final String[] PROTOCOLS = {"TLSv1.3"};
    private static final int PORT = 8443;

    private static KeyStore loadStore(String path) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            store.load(in, PASSWORD);
        }
        return store;
    }

    // identityPath == null: this endpoint has no client identity.
    private static SSLContext context(String identityPath)
            throws Exception {
        KeyManager[] identities = null;

        if (identityPath != null) {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(loadStore(identityPath), PASSWORD);
            identities = kmf.getKeyManagers();
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(loadStore("truststore.p12"));

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(identities, tmf.getTrustManagers(),
                new SecureRandom());
        return context;
    }

    private static void runServer(boolean mutual) throws Exception {
        SSLContext context = context("server.p12");
        try (SSLServerSocket listener = (SSLServerSocket)
                context.getServerSocketFactory()
                        .createServerSocket(PORT)) {
            listener.setEnabledProtocols(PROTOCOLS);
            listener.setNeedClientAuth(mutual);

            // One connection, one request byte: minimal lab only.
            try (SSLSocket socket = (SSLSocket) listener.accept()) {
                socket.setEnabledProtocols(PROTOCOLS);
                socket.startHandshake();

                boolean clientCertificateSeen;
                try {
                    clientCertificateSeen =
                            socket.getSession()
                                    .getPeerCertificates().length > 0;
                } catch (SSLPeerUnverifiedException expected) {
                    clientCertificateSeen = false;
                }

                int request = socket.getInputStream().read();
                if (request != 'P') {
                    throw new IOException("Unexpected demo request");
                }
                socket.getOutputStream().write('K');
                socket.getOutputStream().flush();

                System.out.println("mode="
                        + (mutual ? "mtls" : "oneway"));
                System.out.println("serverHandshake=success");
                System.out.println("protocol="
                        + socket.getSession().getProtocol());
                System.out.println("clientCertificateSeen="
                        + clientCertificateSeen);
                System.out.println("received=P");
            }
        }
    }

    private static void runClient(boolean mutual) throws Exception {
        // One-way client has no private-key identity to present.
        SSLContext context = context(mutual ? "client.p12" : null);
        try (SSLSocket socket = (SSLSocket)
                context.getSocketFactory()
                        .createSocket("localhost", PORT)) {
            socket.setEnabledProtocols(PROTOCOLS);

            // Critical: raw SSLSocket must check intended server name.
            SSLParameters params = socket.getSSLParameters();
            params.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(params);
            socket.startHandshake();

            boolean serverCertificateSeen =
                    socket.getSession()
                            .getPeerCertificates().length > 0;
            socket.getOutputStream().write('P');
            socket.getOutputStream().flush();

            int reply = socket.getInputStream().read();
            if (reply != 'K') {
                throw new IOException("Unexpected demo response");
            }
            System.out.println("mode="
                    + (mutual ? "mtls" : "oneway"));
            System.out.println("clientHandshake=success");
            System.out.println("protocol="
                    + socket.getSession().getProtocol());
            System.out.println("serverCertificateSeen="
                    + serverCertificateSeen);
            System.out.println("reply=K");
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2
                || !("server".equals(args[0])
                     || "client".equals(args[0]))
                || !("oneway".equals(args[1])
                     || "mtls".equals(args[1]))) {
            System.err.println(
                    "Usage: java TlsHandshakeLab server|client oneway|mtls");
            return;
        }

        boolean mutual = "mtls".equals(args[1]);
        if ("server".equals(args[0])) {
            runServer(mutual);
        } else {
            runClient(mutual);
        }
    }
}
```

**Biên dịch** một lần:

```shell
javac TlsHandshakeLab.java
```

### 8.1. Chạy one-way TLS

Mở **terminal 1** tại thư mục lab:

```shell
java TlsHandshakeLab server oneway
```

Mở **terminal 2** cùng thư mục, sau khi server đã bind/lắng nghe:

```shell
java TlsHandshakeLab client oneway
```

**Stdout kỳ vọng của terminal server:**

```text
mode=oneway
serverHandshake=success
protocol=TLSv1.3
clientCertificateSeen=false
received=P
```

**Stdout kỳ vọng của terminal client:**

```text
mode=oneway
clientHandshake=success
protocol=TLSv1.3
serverCertificateSeen=true
reply=K
```

**Diễn giải:** Client nhận server certificate, JSSE kiểm chain dựa trên root trong `truststore.p12` và endpoint hostname `localhost` nhờ `setEndpointIdentificationAlgorithm("HTTPS")`. Server **không yêu cầu** client certificate, và client không nạp private key; do đó `clientCertificateSeen=false`. Một kết nối one-way TLS vẫn mã hóa/xác thực traffic sau handshake, nhưng server **không có TLS-level client identity**.

### 8.2. Chạy mTLS

Đợi cả hai process ở lượt one-way kết thúc. Sau đó mở lại **terminal 1**:

```shell
java TlsHandshakeLab server mtls
```

Trong **terminal 2**:

```shell
java TlsHandshakeLab client mtls
```

**Stdout kỳ vọng của terminal server:**

```text
mode=mtls
serverHandshake=success
protocol=TLSv1.3
clientCertificateSeen=true
received=P
```

**Stdout kỳ vọng của terminal client:**

```text
mode=mtls
clientHandshake=success
protocol=TLSv1.3
serverCertificateSeen=true
reply=K
```

**Diễn giải:** Server bật `setNeedClientAuth(true)` nên JSSE gửi yêu cầu client certificate. Client chọn key/cert trong `client.p12`, server kiểm chain về root trong `truststore.p12` và mục đích client authentication theo JSSE/provider. Client vẫn kiểm server hostname và chain. Mỗi bên chứng minh possession qua handshake phù hợp với TLS 1.3. Hai bên cùng giao tiếp trên record layer được xác thực/mã hóa.

**Để không hiểu sai:** Dòng `clientCertificateSeen=true` chỉ là một cách **quan sát sau khi JSSE handshake thành công**; nó không phải business authorization. Production server cần lấy authenticated principal/certificate trong context được tin, map sang identity có cấu trúc và enforce ACL/IAM/tenant policy.

### 8.3. Thử các trường hợp thất bại quan trọng

**Thử 1 – Server bắt buộc mTLS nhưng client không có cert.**

Terminal server:

```shell
java TlsHandshakeLab server mtls
```

Terminal client:

```shell
java TlsHandshakeLab client oneway
```

Dự kiến: **không có cặp stdout success đầy đủ**. Server từ chối client thiếu chứng thư và ném exception handshake/protocol thích hợp; client cũng có thể thấy error trong handshake hoặc lần đọc/ghi sau đó. **Không cố định chuỗi exception** vì TLS 1.3 alert delivery, provider và thứ tự đóng kết nối có thể khác.

**Thử 2 – Client tin một CA khác.** Thay truststore bằng một truststore an toàn nhưng không chứa root `lab-ca` trong môi trường test. JSSE client không thể xây trust path cho server leaf nên handshake thất bại (thường thấy lỗi `PKIX path building failed` hoặc thông tin tương đương). Không nên giải quyết bằng `TrustManager` nhận mọi chứng thư.

**Thử 3 – Hostname sai.** Tạm thay `createSocket("localhost", PORT)` thành `createSocket("127.0.0.1", PORT)` nhưng giữ server cert chỉ có SAN `DNS:localhost`. Với endpoint identification bật, client phải từ chối vì không có SAN loại `iPAddress` thích hợp cho `127.0.0.1`. **Không** chữa bằng cách tắt hostname verification; cấp lại certificate có SAN đúng theo nhu cầu thực tế.

**Thử 4 – Client certificate `serverAuth` thay vì `clientAuth`.** Nếu server trust manager tuân theo purpose checks như JSSE chuẩn, chứng thư không phù hợp mục đích sẽ bị từ chối. Không dùng server certificate làm client identity chỉ để vượt lỗi config.

**Thử 5 – Certificate hết hạn/bị revoke.** Chứng thư hết hạn phải bị từ chối theo validity policy; kiểm trạng thái thu hồi phụ thuộc cấu hình OCSP/CRL/trust manager và cần thử riêng. Demo này **không kích hoạt kiểm revocation chính thức**. Một chứng thư chưa hết hạn không mặc nhiên được chấp nhận nếu có thông tin revoke đáng tin.

### 8.4. Demo cố ý không có những gì?

- Chưa có nhiều concurrent connections, timeouts, size limits, request parsing hoặc HTTP server.
- Không có KMS/HSM, secret manager, certificate enrollment/renewal, CA chain nhiều cấp, role-based authorization, audit logging hoặc policy revocation production.
- Password store dùng trong code chỉ hợp lệ để học; không hard-code bất kỳ password/key vào JAR triển khai.
- Client truststore và server truststore đang dùng chung root; production có thể tách trusted server CAs và trusted client CAs.
- `TlsHandshakeLab` không tự chống replay của **HTTP/business action**. TLS record layer có bảo vệ integrity/ordering trong session, nhưng một HTTP request hợp lệ có thể bị gửi lại như yêu cầu mới nếu nghiệp vụ không có transaction id/idempotency controls.
- Demo ép TLS 1.3 để luồng quan sát rõ. Với đối tác cũ phải có compatibility policy chứ không tắt bảo mật tùy tiện.

---

## 9. Từ handshake đến identity/authorization trong ứng dụng

### 9.1. Ví dụ mTLS giữa hai microservices

Giả sử service `billing-worker` gọi `payment-api`:

```text
billing-worker                      payment-api
(client)                            (server)

client truststore                   server keystore
  trusts payment CA                   private key + serverAuth cert
client keystore                     server truststore
  private key + clientAuth cert        trusts workload CA
        |                                    |
        +--- mTLS TLS 1.3 handshake --------+
        |                                    |
        | server authenticated              |
        | client authenticated              |
        |                                    |
        +--- POST /refund ------------------>|
                                              |
                                  map verified client principal
                                  check role=refund-writer
                                  check tenant and amount policy
                                              |
        |<--- authorized response ----------+
```

Nếu server chỉ kiểm certificate chain mà không check role hoặc tenant, **mọi service** được cấp cert dưới workload CA có thể bị nhầm thành trusted admin. Mục tiêu của mTLS là làm mạnh danh tính kết nối; policy nghiệp vụ vẫn do server quyết định.

### 9.2. Certificate identity có thể là gì?

Tùy PKI profile:

- **DNS SAN**: hợp với server/hostname theo service identity rules.
- **URI SAN**: dùng trong nhiều workload identity systems, ví dụ định danh SPIFFE; cần chuẩn xác thực URI và trust domain riêng.
- **Subject DN**: có thể dùng cho tổ chức/người, nhưng không nên dựa vào CN tự do nếu PKI không enforce uniqueness/identity policy.
- **OtherName/email/IP SAN**: có ngữ nghĩa theo loại; không được ép tất cả về một string `contains(...)`.
- **Certificate fingerprint/public key pinning**: có thể ràng buộc một key cụ thể cho một hệ thống, nhưng rotation phức tạp và không thay authorization.

Identity mà code đọc qua `SSLSession.getPeerPrincipal()` phải được map theo chuẩn PKI của tổ chức, không cho client tự chọn DN bất kỳ rồi được cấp quyền.

### 9.3. Tách các ranh giới bảo mật

Một request API có thể đi qua:

1. TLS termination ở public ingress (xác thực server).
2. mTLS giữa ingress và internal service.
3. mTLS giữa các sidecars trong mesh.
4. Authentication token cho người dùng cuối.
5. Authorization kiểm user/tenant/object/action.

Mỗi hop có identity/trust policies riêng. **mTLS giữa proxy và app không tự xác thực người dùng cuối**, và một bearer token hợp lệ không thay việc ứng dụng xác thực đúng server TLS khi gọi đối tác.

### 9.4. Rotation và revocation mTLS

Các chứng thư workload thường có hạn ngắn và được tự động cấp lại:

- CA có **issuance policy** xác minh workload identity trước khi cấp cert.
- Agent/proxy thay private key/cert có overlap hợp lý, reload SSLContext/keystore nếu framework cần.
- Peers cập nhật trust bundle/CA rotation có kiểm soát; không xóa CA cũ trước khi các cert phụ thuộc đã hết hiệu lực hoặc được chuyển.
- Kết nối TLS **đang tồn tại** có thể không lập tức bị kết thúc khi certificate bị revoke hoặc hết hạn sau handshake; server cần session lifecycle, reauthentication hoặc connection draining theo security policy.
- Revocation qua CRL/OCSP có tính thực tiễn khác nhau với chứng thư workload sống rất ngắn; thiết kế phải hiểu khả năng kiểm tra của đúng TLS stack.

**Rotate server certificate** không tự đóng mọi TLS sessions cũ; **rotate client certificate** không tự rút quyền của identity đã copy private key. Nếu private key bị lộ, đánh giá scope, thay identity/key material, trust policy, session invalidation và sự cố có thể đã xảy ra.

---

## 10. Bản đồ chẩn đoán lỗi TLS và mTLS trên Java

| Biểu hiện | Nguyên nhân thường gặp | Cách kiểm đúng |
| --- | --- | --- |
| `PKIX path building failed` / `unable to find valid certification path` | CA không trusted, thiếu intermediate, cấu hình truststore sai | Kiểm server chain, truststore client, CA phù hợp; không trust-all |
| `No subject alternative names matching IP address` | Kết nối IP literal nhưng chỉ có DNS SAN hoặc thiếu IP SAN | Cấp đúng iPAddress SAN hoặc dùng tên DNS được chứng nhận |
| `No name matching ... found` | Hostname endpoint không khớp SAN | Kiểm URL/SNI/host và SAN; không tắt hostname verification |
| `bad_certificate` / `certificate_unknown` | Certificate lỗi hoặc không được peer chấp nhận | Kiểm chain, alias, EKU/KU, time, CA policy |
| `certificate_required` | Server require client cert nhưng client không trình bày chứng thư phù hợp | Nạp client KeyManager và PKCS12 có private key+chain |
| `Empty client certificate chain` | Client chưa có matching credential/không có key entry | Kiểm `client.p12`, alias, CA hints và key manager |
| `handshake_failure` | Không có giao điểm TLS protocol/cipher/signature, cert/key không phù hợp hoặc lỗi auth | Kiểm `supported_versions`, schemes, provider, certificate policy |
| `protocol_version` | Một bên không hỗ trợ phiên bản mà bên kia yêu cầu | Kiểm JDK, enabled protocols và compatibility policy |
| `No appropriate protocol` | TLS 1.3 disabled hoặc policy/provider không hỗ trợ | Kiểm `jdk.tls.disabledAlgorithms` và cấu hình JSSE |
| `UnrecoverableKeyException` | Password private-key entry sai/keystore hỏng | Kiểm password bảo mật, alias/entry type/provider |
| `FileNotFoundException` khi load store | Path chạy Java khác thư mục tạo file | Kiểm cwd và tham chiếu file keystore |
| `BindException: Address already in use` | Port 8443 đang dùng/server lab cũ chưa dừng | Dừng process trước rồi chạy một server lab |
| `SSLPeerUnverifiedException` ở server one-way | Server không yêu cầu/chưa xác thực client cert | Đó là điều dự kiến với one-way; mTLS cần `needClientAuth` |
| Client handshake success nhưng API `403` | TLS identity hợp lệ nhưng không có quyền nghiệp vụ | Kiểm role/tenant/resource policy |
| TLS 1.3 resumption không thấy `Certificate` mới | PSK/session resumption thay full certificate handshake | Kiểm chế độ session, ticket/PSK và identity binding |
| Certificate valid nhưng kết nối vẫn bị chặn | Revocation, policy, hostname, algorithm, KU/EKU hoặc app authorization | Kiểm tất cả lớp thay vì chỉ validity |

### 10.1. Xem handshake debug logs

Java 17 có thể bật debug JSSE tại **môi trường lab**:

```shell
java -Djavax.net.debug=ssl,handshake TlsHandshakeLab client mtls
```

Kết quả là nhiều dòng debug phụ thuộc JDK/provider/config, **không có stdout cố định**. Có thể thấy TLS version, certificate request, selected signature algorithms, alerts và thông tin về certificate path. Tránh bật debug verbose trong production lâu dài vì logs có thể tiết lộ identity, endpoint, metadata và thông tin nhạy cảm khác.

**Làm đúng thứ tự khi debug:**

1. Xem client đang gọi **đúng hostname và port** không.
2. So chứng thư leaf/server SAN với hostname yêu cầu.
3. Kiểm trusted CA roots và chain intermediates ở **bên verify**.
4. Xem mục đích `serverAuth`/`clientAuth` và signature/key algorithms.
5. Xem server có thực sự yêu cầu mTLS (`CertificateRequest`, need vs want) không.
6. Kiểm client keystore có `PrivateKeyEntry` phù hợp và key manager chọn đúng alias.
7. Kiểm TLS version/cipher suites/trust manager, sau đó mới xem business authorization.

Không chẩn đoán bằng cách tắt hostname check, trust-all CA hoặc chấp nhận mọi client certificate chỉ để “thử cho chạy”.

### 10.2. Lỗi có thể xuất hiện không đối xứng

Khi server mTLS từ chối client thiếu cert, **server có thể nhận một TLS fatal alert và ném `SSLHandshakeException`**. Client, tùy lúc alert đến và cách TLS 1.3 buffering hoạt động, có thể quan sát lỗi khi `startHandshake`, khi ghi hoặc lúc đọc response. Do đó **không dựa vào một exception text cụ thể** để quyết định tính đúng của giao thức; log điều kiện an toàn và kiểm outcome thực tế của cả hai đầu.

### 10.3. TLS và dữ liệu application sau handshake

`startHandshake()` thành công chỉ nói rằng kênh TLS được thiết lập theo policy của JSSE. Tầng ứng dụng phải có:

- request size limits/timeouts, parser/schema validation;
- authentication/authorization ở mức user/service;
- replay controls/idempotency với thao tác có tác động;
- kiểm permission theo tenant/object;
- bảo vệ secret sau khi TLS decrypt và trước khi trả response;
- monitoring và xử lý certificate/key compromise.

---

## 11. Các mẫu cấu hình cần tránh

### 11.1. Trust-all certificate manager

**Không sử dụng đoạn mã kiểu sau trong production, kể cả “chỉ tạm thời”:**

```java
// ANTI-PATTERN: rejects nothing and destroys peer authentication.
TrustManager[] trustAll = new TrustManager[]{
    new X509TrustManager() {
        public void checkClientTrusted(
                X509Certificate[] chain, String authType) {}
        public void checkServerTrusted(
                X509Certificate[] chain, String authType) {}
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
};
```

Đây là **đoạn cố ý không độc lập để nhận diện anti-pattern**, không phải code nên dùng. Không kiểm chain nghĩa là attacker có thể dùng chứng thư của họ mà cũng được tin. Hãy sửa truststore hoặc certificate chain, không thay trust manager bằng một implementation chấp nhận mọi peer.

### 11.2. Hostname verifier luôn true

```java
// ANTI-PATTERN: do not use!
HostnameVerifier unsafe = (host, session) -> true;
```

Dùng lối tắt này sẽ bỏ xác minh host dự định, tạo cơ hội MITM ngay cả khi một CA nào đó đã ký certificate. Trong raw `SSLSocket` dùng `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")`; trong HTTP client chuẩn giữ TLS/hostname verification bật.

### 11.3. Gắn `clientAuth` certificate nhưng không enforce client identity

Một server có thể `setWantClientAuth(true)` và sau đó tin bất kỳ request nào đi qua TLS. Điều này không tạo mTLS bắt buộc; các client không có cert vẫn có thể được chấp nhận. Hãy dùng `needClientAuth`/policy tương đương khi chứng thư client là yêu cầu bắt buộc, và phải **kiểm identity→permission**.

### 11.4. Dùng một root CA làm toàn bộ authorization

Một root được phép ký certificate cho nhiều service không có nghĩa mọi service được quyền gọi endpoint quản trị. Cần certificate profiles/purpose, trust-domain segmentation và authorization ở tầng ứng dụng. Ràng buộc một service identity ổn định, tránh chỉ dựa vào Subject CN có thể tự khai trong quy trình cấp phát yếu.

### 11.5. Kiểm đúng certificate nhưng kiểm sai đối tượng

Nếu proxy kết nối đến `api.example.com` nhưng client HTTP muốn `payments.example.com`, không được tùy tiện lấy identity đã xác minh của một host để biện minh cho host khác. SAN, SNI, HTTP :authority, routing và service policy phải thống nhất theo giao thức.

---

## 12. Câu hỏi tự kiểm tra có đáp án

**1. Trong one-way TLS, client có bắt buộc gửi certificate không?**  
Không. Mô hình thông thường là client xác thực server; client identity có thể được xác thực sau TLS bằng token/cookie. Server có thể không biết client TLS principal.

**2. Trong mTLS, client có được bỏ qua server certificate validation vì server đã yêu cầu client certificate không?**  
Không. mTLS đòi **cả hai** xác thực đúng peer. Client vẫn phải tin đúng server CA và kiểm SAN/hostname.

**3. `ClientHello` có chứng minh client identity không?**  
Không. `ClientHello` quảng bá capabilities/key shares/SNI...; client certificate authentication yêu cầu handshake messages thích hợp và proof-of-possession nếu server bật.

**4. Vai trò `CertificateVerify` của TLS 1.3 là gì?**  
Bên gửi dùng private key tương ứng certificate để ký cấu trúc transcript theo giao thức, chứng minh đang kiểm soát key hợp lệ cho handshake. Gửi certificate công khai một mình không đủ.

**5. Vai trò `Finished` là gì?**  
Ràng buộc và xác minh tính toàn vẹn transcript handshake với secrets/key schedule, phát hiện sửa đổi trái phép và hoàn tất xác thực handshake.

**6. mTLS có chứng minh client có quyền refund không?**  
Không. Nó giúp xác minh service identity; server phải map principal rồi kiểm ACL/IAM/tenant/amount.

**7. Trong Java, `KeyManagerFactory` và `TrustManagerFactory` khác nhau thế nào?**  
Key manager cung cấp private key/cert **của mình** khi cần; trust manager xác minh cert **của đối tác** theo trust anchors/policy.

**8. Server bật `setNeedClientAuth(true)` nhưng không có client truststore. Điều gì có thể xảy ra?**  
Server có thể không xác minh được chain client hoặc dùng trust configuration ngoài ý muốn; cần cấu hình đúng root CA cấp client và chính sách identity.

**9. Vì sao `SSLSocket` raw phải dùng `setEndpointIdentificationAlgorithm("HTTPS")`?**  
Để JSSE kiểm server certificate khớp tên đích theo quy tắc endpoint identification. Chain valid mà hostname sai vẫn không đủ an toàn.

**10. Nếu server cert có SAN `DNS:localhost`, client kết nối bằng IP literal `127.0.0.1` có hợp lệ không?**  
Không theo kiểm đúng IP identity nếu certificate thiếu SAN `iPAddress:127.0.0.1`. Đổi sang hostname đã được cấp hoặc cấp chứng thư đúng loại SAN.

**11. Tại sao TLS 1.3 có forward secrecy trong full ECDHE handshake?**  
Traffic secrets được dẫn xuất từ ephemeral key agreement; lộ private key ký certificate sau đó không tự tái tạo ephemeral shared secrets đã được xử lý/xóa thích hợp.

**12. `setWantClientAuth(true)` có giống `setNeedClientAuth(true)` không?**  
Không. `want` là optional request; `need` bắt buộc xác thực client certificate phù hợp.

**13. Một client không gửi client certificate trong session resumption có tự động nghĩa là không có mTLS không?**  
Không. Cần xem đó có phải resumption/PSK session đã liên kết identity từ phiên trước hay một full handshake. Kiểm chính sách reauthentication của stack.

**14. 0-RTT có an toàn cho mọi thao tác POST thay đổi tiền không?**  
Không. Early data có replay risk; cần chính sách chống phát lại/idempotency và kiểm chặt nghiệp vụ.

**15. Chỉ có certificate PEM trong thư mục server có làm server bắt tay thành công không?**  
Không. Server cần **private key tương ứng** và certificate chain/key manager phù hợp; client phải tin chain và identity của server.

**16. mTLS ở reverse proxy có tự làm Java app xác thực danh tính client cuối không?**  
Không. Cần đường truyền trusted principal từ proxy vào app và bảo vệ header/context; phân biệt mTLS workload identity với user identity.

## 13. Checklist triển khai TLS/mTLS an toàn trên Java

- [ ] **Protocol:** ưu tiên TLS 1.3 theo compatibility; loại bỏ các phiên bản/cipher suites yếu theo policy.
- [ ] **Server key/cert:** private key và leaf chain hợp lệ; SAN đúng host; EKU `serverAuth`, KU/policy đúng; rotation và quyền file/KMS được quản lý.
- [ ] **Client trust:** chỉ tin các CA/trust anchors được phép, giữ JSSE chain validation và HTTPS endpoint identity; không trust-all/hostname-bypass.
- [ ] **Client identity (mTLS):** client có private key + leaf/chain `clientAuth` được chọn đúng alias; server yêu cầu cert (need vs want) và tin đúng client CA.
- [ ] **Proof-of-possession:** xác thực handshake signature, `Finished` và traffic key derivation thông qua TLS stack chuẩn; không tự mô phỏng handshake bằng Java RSA/AES thủ công.
- [ ] **Authorization:** map verified client identity → principal/role/tenant; kiểm từng API/resource/action; không chỉ tin rằng client cert tồn tại.
- [ ] **Key lifecycle:** cấp phát, quản lý PKCS12/KMS/HSM, rotate, revoke, audit, quản lý client/server roots và reauthentication của các session hiện hành.
- [ ] **Network/architecture:** xác định nơi TLS terminate; proxy headers được xác thực; ingress/backend/mesh hops được bảo vệ phù hợp.
- [ ] **Session nuances:** hiểu full handshake vs resumption, chính sách client-auth khi resume và 0-RTT/replay.
- [ ] **Logging:** ghi protocol/cipher/version/identity/outcome có kiểm soát; không log private keys, passwords hoặc decrypted payload nhạy cảm.
- [ ] **Testing:** thành công one-way và mTLS; không có client cert, sai server hostname, CA không trusted, server/client EKU sai, hết hạn, revocation policy, giả identity và permission denied.
- [ ] **Recovery:** có đường làm mới key/certificate, rollback có kiểm soát, handling CA compromise, không tắt verification khi sự cố certificate xảy ra.

**Kết luận:** Trong **one-way TLS**, client xác thực máy chủ; trong **mTLS**, server còn xác thực chứng thư và sở hữu khóa riêng của client. Dù bắt tay thành công, **application authorization**, vòng đời khóa/chứng thư, hostname identity và sự an toàn của dữ liệu sau giải mã vẫn là những trách nhiệm riêng.

## 14. Tài liệu chính thống

- [RFC 8446 – The Transport Layer Security (TLS) Protocol Version 1.3](https://www.rfc-editor.org/rfc/rfc8446): `ClientHello`/`ServerHello`, `CertificateRequest`, `CertificateVerify`, `Finished`, HKDF key schedule, record protection, resumption và early data.
- [RFC 5246 – The Transport Layer Security (TLS) Protocol Version 1.2](https://www.rfc-editor.org/rfc/rfc5246): cấu trúc handshake TLS 1.2 và khác biệt key exchange/record protection.
- [RFC 5280 – Internet X.509 PKI Certificate and CRL Profile](https://www.rfc-editor.org/rfc/rfc5280): certificate path validation, CA constraints, SAN, KeyUsage, ExtendedKeyUsage và CRL.
- [RFC 9525 – Service Identity in TLS](https://www.rfc-editor.org/rfc/rfc9525): quy tắc nhận diện máy chủ theo tên/identity khi dùng TLS.
- [Java Secure Socket Extension (JSSE) Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-secure-socket-extension-jsse-reference-guide.html): TLS/JSSE, `SSLContext`, key/trust managers, `SSLSocket`/`SSLEngine`, endpoint identification, debug.
- [Java SE 17 – SSLServerSocket](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLServerSocket.html), [SSLSocket](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLSocket.html) và [SSLParameters](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLParameters.html): cấu hình bắt tay, clientAuth và hostname endpoint identification.
- [Java SE 17 – KeyManagerFactory](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/KeyManagerFactory.html), [TrustManagerFactory](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/TrustManagerFactory.html) và [KeyStore](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.html): local TLS identity vs peer trust configuration.
- [JDK 17 keytool manual](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html): lệnh tạo PKCS12 key pairs, CSR, certificate replies và trusted roots.
- [OWASP Transport Layer Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Transport_Layer_Security_Cheat_Sheet.html): cấu hình TLS, certificate verification và những anti-pattern vận hành.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tls-foundation-003">15.3 TLS 1.2, TLS 1.3 và cách đọc cấu hình giao thức khả dụng</a>

<details>
<summary>Xem chi tiết</summary>

# 15.3 TLS 1.2, TLS 1.3 và cách đọc cấu hình giao thức khả dụng

## 1. Mục tiêu: “TLS được hỗ trợ” khác “TLS được sử dụng”

**Transport Layer Security (TLS)** bảo vệ dữ liệu khi truyền giữa hai endpoint: đàm phán cơ chế mật mã, xác thực bên liên quan theo chính sách, tạo khóa phiên, mã hóa và kiểm tra tính toàn vẹn bản ghi. Với Java 17+, phần lớn ứng dụng làm việc với TLS qua **JSSE (Java Secure Socket Extension)**: `SSLContext`, `SSLSocket`, `SSLServerSocket`, `SSLEngine`, `SSLParameters`, `SSLSession` và các `KeyManager`/`TrustManager`.

Trong vận hành thường gặp những câu hỏi khác nhau:

1. JDK/provider **biết** những phiên bản TLS nào?
2. Một `SSLContext` hoặc socket **đang cho phép** những phiên bản nào?
3. Java Security policy có **cấm** thuật toán/phiên bản nào không?
4. Peer server/client **chấp nhận** và có thể đàm phán những phiên bản nào?
5. Một kết nối **đã bắt tay thành công thực sự** sử dụng phiên bản và cipher suite nào?

Năm câu trên có thể cho **năm kết quả khác nhau**. Ví dụ trong một runtime SunJSSE đã kiểm thử, `SSLSocket.getSupportedProtocols()` chứa `TLSv1`, `TLSv1.1`, `SSLv3`, nhưng `getEnabledProtocols()` mặc định chỉ có `TLSv1.3` và `TLSv1.2`. **Không** kết luận máy đang cho phép SSLv3/TLS 1.0 vì chúng xuất hiện trong danh sách “supported”.

Các mục tiêu học tập:

- Phân biệt handshake, key schedule và cipher suite của **TLS 1.2** và **TLS 1.3**.
- Đọc đúng supported/enabled/default/disabled/negotiated từ JSSE.
- Hiểu ảnh hưởng của `jdk.tls.disabledAlgorithms`, `jdk.tls.client.protocols`, `jdk.tls.server.protocols`, `https.protocols`, `SSLParameters` và provider.
- Xem protocol/cipher suite thật **sau handshake** với `SSLSession`.
- Chẩn đoán lỗi `protocol_version`, `handshake_failure`, `SSLHandshakeException` mà không tắt các cơ chế bảo vệ.
- Tự chạy hai chương trình Java 17: inventory cấu hình và kết nối TLS 1.2/TLS 1.3 qua loopback có trust/hostname verification.

Bài này tập trung TLS trên TCP qua JSSE; **DTLS**, QUIC/TLS và giao thức ứng dụng HTTP/2, HTTP/3 có ràng buộc riêng. Không dùng thông tin từ một kết nối TLS để tự suy ra trạng thái mọi service trên máy.

## 2. So sánh TLS 1.2 và TLS 1.3

TLS 1.2 là phiên bản theo **RFC 5246**; TLS 1.3 theo **RFC 8446**. Cả hai đều dùng được cho các hệ thống tương thích và được cấu hình đúng, nhưng TLS 1.3 đơn giản hóa nhiều chi tiết và loại bỏ các phương thức mật mã lỗi thời.

| Khía cạnh | TLS 1.2 | TLS 1.3 |
| --- | --- | --- |
| RFC | `5246` | `8446` |
| Handshake đầy đủ thông thường | Thường khoảng **2 RTT** trước application data, phụ thuộc loại handshake | Thường **1 RTT**, hỗ trợ resumption và tùy chọn **0-RTT early data** theo giao thức |
| Trao đổi khóa | Phụ thuộc cipher suite: ECDHE, DHE, RSA key transport lịch sử... | Mô hình (EC)DHE/PSK key schedule được thiết kế lại; loại RSA key transport |
| Cipher suites | Tên thường mang **key exchange + authentication + symmetric encryption + hash/MAC** | Tên chủ yếu mô tả **AEAD + hash**; nhóm key exchange và signature scheme đàm phán riêng |
| Ví dụ cipher suite | `TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256` | `TLS_AES_128_GCM_SHA256` |
| Encryption cho application data | GCM/ChaCha20-Poly1305 hoặc nhiều cơ chế cũ hơn tùy suite | **AEAD** như AES-GCM hoặc ChaCha20-Poly1305 |
| Key derivation | TLS 1.2 PRF và key block/Finished theo protocol | **HKDF-based key schedule**, bí mật phân tách theo từng pha |
| Forward secrecy | Có với ECDHE/DHE; **không tự có** khi dùng RSA static key transport | Thường có với (EC)DHE; **PSK-only không có cùng tính chất forward secrecy** nếu không có fresh DHE |
| Renegotiation | Có các cơ chế renegotiation cần xử lý an toàn theo version/provider | **Không có TLS 1.2 renegotiation**, có các cơ chế post-handshake như `KeyUpdate` |
| Certificate authentication | Certificate + handshake signatures theo cipher suite/profile | Certificate + signature schemes được đàm phán riêng (như RSA-PSS/ECDSA) |
| Tương thích | Nhiều hệ thống legacy hơn | Có thể cần cập nhật proxy/load balancer/server hoặc mã cứng cipher suites |

**Lưu ý:** 0-RTT early data có **nguy cơ replay**, không phù hợp mọi thao tác (ví dụ giao dịch không idempotent) và **không đồng nghĩa** ứng dụng Java/JSSE bất kỳ cũng hỗ trợ tự động. Không bật 0-RTT chỉ vì client và server hiển thị hỗ trợ TLS 1.3.

### 2.1 Phác họa TLS 1.2 handshake

Một full handshake TLS 1.2 có certificate server và ECDHE điển hình:

```text
Client                                           Server
ClientHello ------------------------------->
                           <--------------- ServerHello
                           <--------------- Certificate
                           <--------------- ServerKeyExchange
                           <--------------- ServerHelloDone
ClientKeyExchange ------------------------->
ChangeCipherSpec / Finished --------------->
                           <--------------- ChangeCipherSpec / Finished
          <======== Protected application data ========>
```

Thông điệp có thể khác tùy cipher suite, client authentication và resumption. Đây là **sơ đồ khái niệm** để so sánh, không phải transcript byte đầy đủ hay mô tả mọi biến thể.

TLS 1.2 cho phép một số cipher suites có key exchange cũ (ví dụ RSA key transport) và cơ chế CBC/SHA-1 lịch sử. **Không nên tái bật các lựa chọn yếu chỉ để tương thích**. Đối với TLS 1.2 còn cần dùng, ưu tiên suites có **ECDHE/DHE + AEAD** được security policy cho phép, và xử lý signature/certificate đúng profile.

### 2.2 Phác họa TLS 1.3 handshake

Full handshake TLS 1.3 với key share:

```text
Client                                           Server
ClientHello + supported_versions
            + key_share -------------------->
                           <--------------- ServerHello + key_share
                           <--------------- EncryptedExtensions
                           <--------------- Certificate
                           <--------------- CertificateVerify
                           <--------------- Finished
Finished ----------------------------------->
          <======== Protected application data ========>
```

Các pha sau `ServerHello` được bảo vệ bằng handshake traffic keys. TLS 1.3 dùng HKDF để tách handshake/application secrets, cho phép forward-secure key exchange khi dùng fresh (EC)DHE và loại bỏ RSA key transport. Trong resumption dùng PSK, số bản tin và bí mật khác; có thể có HelloRetryRequest khi key share đề xuất không phù hợp.

**Đừng hiểu số RTT là cam kết thời gian kết nối:** TCP setup, DNS, proxy, network latency, crypto provider, certificate chain, OCSP, HTTP/2/ALPN, session cache và ứng dụng vẫn ảnh hưởng tốc độ thực tế.

### 2.3 Tên cipher suite của TLS 1.2 so với 1.3

```text
TLS 1.2: TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
                |      |        |          |
           key agreement  cert  AEAD    hash/PRF

TLS 1.3: TLS_AES_128_GCM_SHA256
                  |      |
                AEAD     HKDF hash
```

Với TLS 1.3, **tên cipher suite không xác định nhóm ECDHE/DHE hoặc kiểu chữ ký certificate**. Các thành phần này được thương lượng qua extensions như `supported_groups`, `key_share`, `signature_algorithms` và `signature_algorithms_cert` tùy tình huống. Một server dùng certificate RSA vẫn có thể đàm phán `TLS_AES_128_GCM_SHA256`; không cần một TLS 1.3 suite có chữ “RSA” trong tên.

**Không đưa** một TLS 1.2 suite `TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256` làm điều kiện duy nhất cho TLS 1.3 và mong kết nối 1.3 thành công. Ngược lại, chỉ cho phép suites có tên TLS 1.3 rồi cố thương lượng TLS 1.2 có thể khiến handshake thất bại.

## 3. “Supported”, “enabled”, “configured” và “negotiated” nghĩa là gì?

Đây là phần quan trọng nhất khi **đọc cấu hình giao thức khả dụng**.

| Mức | Câu hỏi | Java API/thông tin | Ý nghĩa |
| --- | --- | --- | --- |
| **Supported** | Implementation này *biết* phiên bản nào? | `SSLSocket.getSupportedProtocols()`, `SSLEngine.getSupportedProtocols()`, `SSLContext.getSupportedSSLParameters().getProtocols()` | Danh sách implementation hỗ trợ; **có thể gồm protocol đã disabled** |
| **Enabled** | Socket/engine *được cấu hình* thử những phiên bản nào? | `SSLSocket.getEnabledProtocols()`, `SSLEngine.getEnabledProtocols()`, `SSLParameters.getProtocols()` | Phạm vi phép đàm phán từ phía object; chưa chứng minh peer sẽ chấp nhận |
| **Default** | Context/provider *mặc định* cho phép gì trước khi override? | `SSLContext.getDefaultSSLParameters()` hoặc socket/engine mới | Phụ thuộc JDK, provider, role, security/system properties |
| **Security policy** | Có protocol/cipher/algorithm nào bị cấm không? | `Security.getProperty("jdk.tls.disabledAlgorithms")`, `jdk.certpath.disabledAlgorithms` | Có thể **ngăn negotiation** ngay cả khi mã đặt protocol trong enabled |
| **Peer capability** | Đối tác nhận gì? | Server/client settings hoặc handshake trace đã xác thực | Không thể suy từ `getSupportedProtocols` của máy local |
| **Negotiated** | Kết nối **đã handshake** dùng phiên bản nào? | `socket.getSession().getProtocol()`, `getCipherSuite()` | Giá trị thực của **kết nối cụ thể**, chỉ đáng tin khi handshake thành công |

Ví dụ:

```text
SunJSSE supported: [TLSv1.3, TLSv1.2, TLSv1.1, TLSv1, SSLv3, SSLv2Hello]
SunJSSE enabled:   [TLSv1.3, TLSv1.2]
Server enabled:    [TLSv1.2]
Shared usable:     [TLSv1.2]
Negotiated:        TLSv1.2 (chỉ sau khi handshake thành công)
```

Dù client hỗ trợ TLSv1.3 và bật nó, nó vẫn **có thể** thương lượng TLSv1.2 vì server không hỗ trợ/bật TLSv1.3. Nếu server chỉ bật TLSv1.1 và client chỉ bật TLSv1.2/1.3, handshake **không có protocol chung**, nhưng dấu hiệu lỗi cụ thể phụ thuộc implementation.

**Đặc biệt:** nhiều JDK vẫn hiển thị TLSv1/TLSv1.1 trong `getSupportedProtocols()`, trong khi `jdk.tls.disabledAlgorithms` cấm negotiation. Thậm chí `setEnabledProtocols(new String[]{"TLSv1"})` được object chấp nhận không đồng nghĩa handshake với TLSv1 được phép. Không xóa TLSv1/TLSv1.1/SSLv3 khỏi security disabled list chỉ để “làm test pass”.

### Quy tắc kiểm tra đúng thứ tự

1. Đọc **provider/JDK version và security properties**.
2. Đọc **supported** của chính engine đang sử dụng.
3. Đọc **enabled/default** trước khi bắt tay; xem ứng dụng có override bằng `setEnabledProtocols`/`SSLParameters` hoặc system properties không.
4. Kiểm tra **cipher suites, named groups, signature schemes**, key/certificate/trust và peer.
5. Thực hiện **handshake thật** với trust/hostname verification.
6. Sau thành công, đọc `SSLSession.getProtocol()` và `getCipherSuite()` để biết giá trị **thật sự được thương lượng**.

## 4. Các JSSE API dùng để đọc TLS

| API | Mục đích | Lưu ý |
| --- | --- | --- |
| `SSLContext.getInstance("TLS")` | Chọn generic TLS engine/provider | Không có nghĩa “chỉ TLS 1.0”; cần đọc default/supported/actual |
| `SSLContext.init(...)` | Cấp key managers, trust managers, RNG | Cấu hình trust/certificate không thể suy từ tên `TLS` |
| `SSLContext.getSupportedSSLParameters()` | Supported parameters của context | Includes supported protocols/suites nhưng không bảo đảm được negotiate |
| `SSLContext.getDefaultSSLParameters()` | Enabled-by-default parameters trong context | Có thể khác tùy SunJSSE/provider và client/server role |
| `SSLSocket.getSupportedProtocols()` | Protocols implementation có | Danh sách có thể gồm bị disabled |
| `SSLSocket.getEnabledProtocols()` | Protocols được bật trên socket hiện tại | Chỉ trạng thái **trước/cho phép handshake** |
| `SSLSocket.setEnabledProtocols(String[])` | Giới hạn protocols cho socket | Ném `IllegalArgumentException` khi protocol không được implementation hỗ trợ; policy vẫn có thể cấm |
| `SSLParameters.getProtocols()/setProtocols(...)` | Xem/đặt protocol list theo parameter object | Phải áp vào `socket.setSSLParameters(...)` hoặc engine |
| `SSLSocket.getSupportedCipherSuites()` | Danh sách suite implementation biết | Suite yếu có thể xuất hiện nhưng bị policy chặn |
| `SSLSocket.getEnabledCipherSuites()` | Suite list hiện bật | Cần tương thích phiên bản TLS, key algorithm, peer |
| `SSLSocket.startHandshake()` | Khởi động/đợi handshake | Thất bại khi protocol, trust, cipher, cert... không phù hợp |
| `SSLSession.getProtocol()` | **Phiên bản negotiated** | Chỉ xem sau successful handshake |
| `SSLSession.getCipherSuite()` | **Suite negotiated** | Không tự nói toàn bộ nhóm ECDHE hay chính sách danh tính |
| `SSLEngine.getSupportedProtocols()/getEnabledProtocols()` | Tương tự khi dùng NIO/async TLS | Handshake engine cần xử lý state `NEED_WRAP`/`NEED_UNWRAP`/`NEED_TASK` |
| `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")` | Bật identity/hostname verification theo HTTPS trên JSSE | Rất quan trọng với SSLSocket tự tạo; cần trust manager đúng |

`SSLParameters.setProtocols(...)` không tự thay đổi socket/engine đã tồn tại; phải gọi `setSSLParameters(...)` trên đúng đối tượng trước handshake. Chú ý: `getDefaultSSLParameters()` và socket factory không bắt buộc phản ánh **một danh sách chung cho mọi socket ở mọi role**. Dùng chính socket/engine mà ứng dụng thực tế tạo và in `getEnabledProtocols()` để chẩn đoán.

Với `SSLEngine`, các method có cùng khái niệm supported/enabled; nhưng handshake nonblocking yêu cầu điều khiển wrap/unwrap buffer và delegated tasks. Không coi việc tạo `SSLEngine` hoặc đọc enabled protocols là đã thực hiện handshake.

## 5. Chương trình 1 — kiểm kê TLS từ Java mà không kết nối mạng

Lưu thành **`TlsProtocolInventoryDemo.java`**, dùng Java 17 trở lên. Chương trình tạo `SSLContext("TLS")` và một `SSLSocket` **chưa kết nối**, in provider, supported/enabled/default protocols, các cờ TLS 1.2/TLS 1.3 và vài configuration properties. **Không có kết nối mạng và không có phiên bản negotiated** trong chương trình này.

```java
import java.security.Security;
import java.util.Arrays;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public final class TlsProtocolInventoryDemo {
    private static boolean has(String[] values, String target) {
        return Arrays.asList(values).contains(target);
    }

    public static void main(String[] args) throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, null, null);
        SSLParameters defaults = context.getDefaultSSLParameters();
        SSLParameters supported = context.getSupportedSSLParameters();
        SSLSocketFactory factory = context.getSocketFactory();
        try (SSLSocket socket = (SSLSocket) factory.createSocket()) {
            System.out.println("contextProtocol=" + context.getProtocol());
            System.out.println("provider=" + context.getProvider().getName());
            System.out.println("supportedProtocols="
                    + Arrays.toString(socket.getSupportedProtocols()));
            System.out.println("enabledProtocols="
                    + Arrays.toString(socket.getEnabledProtocols()));
            System.out.println("defaultProtocolsFromContext="
                    + Arrays.toString(defaults.getProtocols()));
            System.out.println("supportedProtocolsFromContext="
                    + Arrays.toString(supported.getProtocols()));
            System.out.println("supportsTLS12="
                    + has(socket.getSupportedProtocols(), "TLSv1.2"));
            System.out.println("supportsTLS13="
                    + has(socket.getSupportedProtocols(), "TLSv1.3"));
            System.out.println("enabledTLS12="
                    + has(socket.getEnabledProtocols(), "TLSv1.2"));
            System.out.println("enabledTLS13="
                    + has(socket.getEnabledProtocols(), "TLSv1.3"));
            System.out.println("disabledAlgorithmsPresent="
                    + (Security.getProperty("jdk.tls.disabledAlgorithms") != null));
            System.out.println("clientProtocolsProperty="
                    + System.getProperty("jdk.tls.client.protocols", "(not set)"));
            System.out.println("httpsProtocolsProperty="
                    + System.getProperty("https.protocols", "(not set)"));
            System.out.println("noHandshakePerformed=true");
        }
    }
}
```

Biên dịch và chạy từ thư mục chứa file:

```powershell
javac --release 17 -encoding UTF-8 TlsProtocolInventoryDemo.java
java TlsProtocolInventoryDemo
```

**Output chính xác trong môi trường SunJSSE được kiểm thử** (không phải kết quả cố định cho mọi máy):

```text
contextProtocol=TLS
provider=SunJSSE
supportedProtocols=[TLSv1.3, TLSv1.2, TLSv1.1, TLSv1, SSLv3, SSLv2Hello]
enabledProtocols=[TLSv1.3, TLSv1.2]
defaultProtocolsFromContext=[TLSv1.3, TLSv1.2]
supportedProtocolsFromContext=[TLSv1.3, TLSv1.2, TLSv1.1, TLSv1, SSLv3, SSLv2Hello]
supportsTLS12=true
supportsTLS13=true
enabledTLS12=true
enabledTLS13=true
disabledAlgorithmsPresent=true
clientProtocolsProperty=(not set)
httpsProtocolsProperty=(not set)
noHandshakePerformed=true
```

### Đọc đúng output

- `contextProtocol=TLS` nói tên engine được yêu cầu, **không phải** `SSLSession` negotiated protocol.
- `supportedProtocols` có những mục cũ: `SSLv3`, `TLSv1`, `TLSv1.1`. Chúng được provider “nhận diện”, **không** có nghĩa được phép đàm phán trong cấu hình mặc định.
- `enabledProtocols=[TLSv1.3,TLSv1.2]` là khả năng đàm phán mặc định của socket ở môi trường test. Sau khi chạy với `-D...`, sửa `java.security`, chọn provider khác hoặc đổi version JDK, danh sách có thể đổi.
- `disabledAlgorithmsPresent=true` **chỉ** nói có một giá trị cấu hình security property. Để đánh giá đầy đủ, phải đọc **nội dung thực** bằng `Security.getProperty("jdk.tls.disabledAlgorithms")` và xem thuật toán/nhóm liên quan; không chỉ kiểm `null`.
- `noHandshakePerformed=true` là lời nhắc `getEnabledProtocols()` **chưa chứng minh** kết nối TLS thật nào thành công.

Không dùng `SSLContext.init(null,null,null)` của chương trình này như “cấu hình trust hoàn chỉnh cho hệ thống thật”. Ở đây context không kết nối mạng, nên không có peer certificate để xác thực.

## 6. Chương trình 2 — xác minh *negotiated protocol* bằng TLS handshake thật

Chương trình thứ hai **tự tạo TLS server và client trên loopback `127.0.0.1`**, thực hiện một handshake chỉ dùng TLS 1.3 và một handshake chỉ dùng TLS 1.2.

Khác với inventory ở phần 5, ví dụ này kiểm tra:

- Server có `KeyManagerFactory` cung cấp certificate và private key từ PKCS12.
- Client có `TrustManagerFactory` tin **duy nhất chứng thư local demo** do bạn tạo, không tắt certificate validation.
- Client gọi `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")` để xác minh IP SAN `127.0.0.1` trong certificate, không dùng trust-all `X509TrustManager`.
- Hai phía đặt `setEnabledProtocols(new String[]{version})` trước handshake.
- Sau `startHandshake()`, đọc **`SSLSession.getProtocol()`**, **`getCipherSuite()`** và peer certificate; kiểm tra hai bên đồng thuận phiên bản.

**Chỉ sử dụng trong môi trường học tập.** Private key tự ký là test fixture, password `changeit` dễ đoán, certificate không thuộc public trust store. Kết quả thành công **không xác nhận một server Internet hay service sản phẩm nào**.

### 6.1 Tạo chứng thư test và truststore bằng keytool

Chạy trong **PowerShell**, từ thư mục chứa hai file source Java. Giữ **cùng phiên PowerShell** để dùng lại biến `$t`. Các lệnh tạo tệp chỉ trong `$env:TEMP` của máy:

```powershell
# Thư mục tạm, không đặt key demo vào repository.
$t = Join-Path $env:TEMP ("tls-protocol-lesson-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path $t | Out-Null
$pw = "changeit"  # Chỉ dùng cho demo!

# RSA test server certificate 7 ngày, SAN gồm IP và DNS cho loopback.
keytool -genkeypair -alias localserver -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -dname "CN=localhost" -ext "SAN=ip:127.0.0.1,dns:localhost" -ext "KU:critical=digitalSignature,keyEncipherment" -ext "EKU=serverAuth" -validity 7 -storetype PKCS12 -keystore (Join-Path $t "server.p12") -storepass $pw -keypass $pw -noprompt

# Export public certificate; không export server private key.
keytool -exportcert -alias localserver -keystore (Join-Path $t "server.p12") -storepass $pw -rfc -file (Join-Path $t "server.pem")

# Client chỉ tin certificate của localserver trong demo.
keytool -importcert -alias localserver -file (Join-Path $t "server.pem") -keystore (Join-Path $t "trust.p12") -storetype PKCS12 -storepass $pw -noprompt
```

**Vì sao `127.0.0.1` phải có SAN IP?** Client kết nối bằng IP string và bật endpoint identification algorithm `HTTPS`; kiểm tra danh tính phải khớp **iPAddress SAN**, không chỉ CN trong subject. Nếu bỏ SAN, TLS có thể bị từ chối do hostname/IP mismatch ngay cả khi truststore chứa certificate.

### 6.2 Mã Java hoàn chỉnh

Lưu thành **`LoopbackTlsHandshakeDemo.java`**:

```java
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

public final class LoopbackTlsHandshakeDemo {
    private static KeyStore load(Path file, char[] password) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(file)) {
            store.load(in, password);
        }
        return store;
    }

    private static SSLContext serverContext(Path keyStorePath,
                                            char[] password) throws Exception {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(load(keyStorePath, password), password);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), null, new SecureRandom());
        return context;
    }

    private static SSLContext clientContext(Path trustStorePath,
                                            char[] password) throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(load(trustStorePath, password));
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, tmf.getTrustManagers(), new SecureRandom());
        return context;
    }

    private static void run(String version, SSLContext server,
                            SSLContext client) throws Exception {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        try (SSLServerSocket listener = (SSLServerSocket)
                server.getServerSocketFactory().createServerSocket(
                        0, 1, loopback)) {
            listener.setEnabledProtocols(new String[]{version});
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<String> serverSession = executor.submit(() -> {
                    try (SSLSocket peer = (SSLSocket) listener.accept()) {
                        peer.setEnabledProtocols(new String[]{version});
                        peer.startHandshake();
                        return peer.getSession().getProtocol();
                    }
                });
                try (SSLSocket socket = (SSLSocket)
                        client.getSocketFactory().createSocket(
                                "127.0.0.1", listener.getLocalPort())) {
                    socket.setEnabledProtocols(new String[]{version});
                    SSLParameters params = socket.getSSLParameters();
                    params.setEndpointIdentificationAlgorithm("HTTPS");
                    socket.setSSLParameters(params);
                    socket.startHandshake();

                    String negotiatedClient = socket.getSession().getProtocol();
                    String negotiatedServer =
                            serverSession.get(10, TimeUnit.SECONDS);
                    System.out.println(version + " negotiated="
                            + negotiatedClient);
                    System.out.println(version + " peersAgree="
                            + (version.equals(negotiatedClient)
                            && negotiatedClient.equals(negotiatedServer)));
                    System.out.println(version + " cipherSuiteNonEmpty="
                            + !socket.getSession().getCipherSuite().isBlank());
                    System.out.println(version + " peerCertificatePresent="
                            + (socket.getSession().getPeerCertificates().length > 0));
                }
            } finally {
                executor.shutdownNow();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Usage: java LoopbackTlsHandshakeDemo server.p12 trust.p12 password");
        }
        char[] password = args[2].toCharArray();
        try {
            SSLContext server = serverContext(Path.of(args[0]), password);
            SSLContext client = clientContext(Path.of(args[1]), password);
            run("TLSv1.3", server, client);
            run("TLSv1.2", server, client);
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
```

Biên dịch và chạy trên Java 17+ (đứng tại thư mục chứa source, `$t` được tạo ở phần trên):

```powershell
javac --release 17 -encoding UTF-8 -d $t LoopbackTlsHandshakeDemo.java
java -cp $t LoopbackTlsHandshakeDemo (Join-Path $t "server.p12") (Join-Path $t "trust.p12") $pw
```

**Output chính xác đã kiểm thử** với runtime SunJSSE có TLS 1.2 và TLS 1.3:

```text
TLSv1.3 negotiated=TLSv1.3
TLSv1.3 peersAgree=true
TLSv1.3 cipherSuiteNonEmpty=true
TLSv1.3 peerCertificatePresent=true
TLSv1.2 negotiated=TLSv1.2
TLSv1.2 peersAgree=true
TLSv1.2 cipherSuiteNonEmpty=true
TLSv1.2 peerCertificatePresent=true
```

**Không in tên cipher suite cụ thể** để tránh tạo một output giả định cố định: selection phụ thuộc provider, policy, certificate, supported groups và phiên bản JDK. Chỉ kiểm `getCipherSuite()` không rỗng; khi chẩn đoán thực, in tên suite negotiated trong môi trường có quyền, sau đó đối chiếu với policy.

### 6.3 Đọc kết quả và bảo đảm an toàn

- `TLSv1.3 negotiated=TLSv1.3` là **giá trị của `SSLSession` sau successful handshake**, khác hẳn dòng `supportedProtocols` hoặc `enabledProtocols` từ inventory.
- `peersAgree=true` chứng minh server và client cùng thấy phiên bản negotiated và khớp phiên bản được cấu hình cho lần test đó.
- `peerCertificatePresent=true` cho biết SSLSession có peer certificate. Trong code, **TrustManagerFactory** đã xác minh trust theo truststore demo và **endpoint identification** được bật. Dòng boolean này **đứng riêng không đủ chứng minh** certificate chain/hostname đúng nếu code không bật các cơ chế đó.
- Demo chỉ chạy trên loopback, không kết nối host bên ngoài. Truststore chỉ tin certificate test được tạo; không import cert từ bên gửi không xác thực vào truststore sản phẩm.
- Server nhận một kết nối trong background thread (không dùng lại engine state giữa các phiên); thread executor kết thúc sau mỗi test. Với server thật, cần timeout, limit request, resource lifecycle và cấu hình TLS quản lý tập trung.
- Nếu runtime có security policy cấm TLS 1.2 hoặc 1.3, một phép test có thể fail; **đó là tín hiệu chính sách của môi trường**, không phải lý do bỏ policy. Hãy kiểm `getEnabledProtocols()` và `jdk.tls.disabledAlgorithms` trước khi chọn testcase tương ứng.

Dọn dẹp dữ liệu test:

```powershell
# Chỉ xóa đúng thư mục demo do phần 6.1 tạo, không dùng cho dữ liệu thật.
if ($t -like (Join-Path $env:TEMP "tls-protocol-lesson-*")) {
    Remove-Item -LiteralPath $t -Recurse -Force
}
```

Xóa file bình thường **không bảo đảm secure erase** khỏi SSD, snapshot, backup; vì vậy chỉ dùng test keys và password mẫu. **Không hardcode password/secret** trong ứng dụng thật.

## 7. Cấu hình JSSE: thứ tự ưu tiên và phạm vi tác động

Java có hai nhóm cấu hình quan trọng:

- **System properties** như `jdk.tls.client.protocols`, `jdk.tls.server.protocols`, `https.protocols`, thường nhận qua `-D...` khi khởi động JVM và tác động theo phạm vi của API/provider.
- **Security properties** như `jdk.tls.disabledAlgorithms` hay `jdk.certpath.disabledAlgorithms`, đọc bằng `java.security.Security.getProperty` và cấu hình trong môi trường `java.security`. Chúng có thể **cấm** protocol/algorithm ngay cả khi application đặt allowed/enabled list.

**Không nhầm `System.getProperty("jdk.tls.disabledAlgorithms")` với `Security.getProperty("jdk.tls.disabledAlgorithms")`.** Giá trị chính sách TLS của Oracle/OpenJDK JSSE là Java Security property, không phải chỉ một system property thông thường.

### 7.1 Những property phải biết

| Property/cấu hình | Phạm vi chính | Cách hiểu đúng |
| --- | --- | --- |
| `jdk.tls.disabledAlgorithms` (**Security**) | JSSE protocol, cipher, key exchange/signature/algorithm restrictions | Có thể chặn TLSv1/TLSv1.1/SSLv3 và thuật toán yếu ngay cả nếu enabled tường minh |
| `jdk.certpath.disabledAlgorithms` (**Security**) | Certificate path verification | Có thể từ chối chain dùng key size/thuật toán yếu dù TLS version có chung |
| `jdk.tls.client.protocols` (**System**) | Default client protocol list của **SunJSSE** | Giới hạn phiên bản dùng cho client defaults; ứng dụng `setEnabledProtocols` tường minh có thể override list nhưng không vượt security restrictions |
| `jdk.tls.server.protocols` (**System**) | Default server protocol list của **SunJSSE**, generic context | Có scope riêng; version-specific context hoặc explicit set có thể bỏ qua setting default |
| `https.protocols` (**System**) | Mặc định của `HttpsURLConnection` | **Không phải** công tắc toàn bộ kết nối TLS/`java.net.http.HttpClient`/Netty/gRPC |
| `jdk.tls.client.cipherSuites` / `jdk.tls.server.cipherSuites` (**System**) | Default cipher suites trong SunJSSE | Không vượt allowed algorithms, certificates, named groups, phiên bản hoặc peer |
| `jdk.tls.namedGroups` (**System**) | Named finite-field/elliptic-curve groups | Có thể làm TLS 1.3/1.2 handshake thất bại nếu peer không có group chung |
| `javax.net.ssl.keyStore` / `javax.net.ssl.trustStore` (System) | Default key/trust material cho một số factory/default managers | Không tự bảo đảm hostname/identity; explicit `SSLContext` có thể dùng store khác |
| `SSLParameters.setProtocols` / `SSLSocket.setEnabledProtocols` | Object/session client/server cụ thể | Xác định list phiên bản trực tiếp; vẫn cần peer + security policy phù hợp |
| `SSLContext.getInstance("TLS")` | JSSE context thuật toán generic | Không có nghĩa một phiên bản đã negotiated; hỗ trợ nhiều TLS versions tùy provider |

**Thứ tự cấu hình cho default enabled list** trong SunJSSE thường có thể hiểu là:

```text
Explicit application setting (socket/engine SSLParameters)
        > relevant SunJSSE system property
        > JSSE provider defaults

Sau đó vẫn chịu ràng buộc của Java Security properties
(ví dụ jdk.tls.disabledAlgorithms, jdk.certpath.disabledAlgorithms),
thuật toán/curve/certificates và peer capabilities.
```

Không nên diễn giải dấu “`>`” là cơ chế bỏ qua chính sách security. Nó chỉ nói **cấu hình enabled/default list** có ưu tiên; **mức cấm** của `jdk.tls.disabledAlgorithms` vẫn áp dụng trong negotiation.

Ví dụ khởi động demo inventory với cấu hình client default **TLSv1.3** (PowerShell):

```powershell
# Demo: cấu hình system property trước khi context được tạo.
java "-Djdk.tls.client.protocols=TLSv1.3" TlsProtocolInventoryDemo
```

Chạy lại bằng terminal/JVM mới và so `enabledProtocols`. **Không** suy mọi application đều dùng same default: chúng có thể tạo riêng `SSLContext`, gọi `setEnabledProtocols`, chạy provider khác hoặc do framework cấu hình. Một số property được JSSE đọc lúc khởi tạo provider/context; đặt giữa lúc ứng dụng đã chạy có thể không cập nhật các context hiện có.

Ví dụ với một `SSLSocket` đã tạo:

```java
// Đoạn minh họa trong phương thức Java, không phải chương trình độc lập.
String[] desired = { "TLSv1.3", "TLSv1.2" };
socket.setEnabledProtocols(desired);
// Đọc lại từ chính socket rồi mới bắt tay.
System.out.println(java.util.Arrays.toString(
        socket.getEnabledProtocols()));
```

Code trên **chưa** kiểm tra handshake. Với server có nhiều client legacy, chọn list theo chính sách khả năng tương thích thật; không bật SSLv3/TLS 1.0/TLS 1.1 hoặc weak cipher vì một lời báo lỗi “no common protocol” nếu không có ngoại lệ bảo mật được duyệt.

### 7.2 Cách đọc nhanh cấu hình JVM bằng CLI

Java JDK cung cấp:

```powershell
# Tóm tắt enabled TLS protocols/suites theo JDK/provider hiện tại.
keytool -showinfo -tls

# Trong môi trường JDK hỗ trợ tùy chọn này:
java -XshowSettings:security:tls -version
```

Lệnh `keytool -showinfo -tls` đã được kiểm thử trên runtime JDK sử dụng trong bài, phần mở đầu:

```text
Enabled Protocols
-----------------
TLSv1.3
TLSv1.2

Enabled Cipher Suites
---------------------
TLS_AES_256_GCM_SHA384
TLS_AES_128_GCM_SHA256
TLS_CHACHA20_POLY1305_SHA256
```

Danh sách còn **nhiều suite phía sau** (đã cố ý chỉ in ba dòng đầu để minh họa, **không coi đây là toàn output CLI**). Lệnh này hữu ích để xem enabled profile ở JDK process nhưng **không xác thực cấu hình cụ thể của mọi ứng dụng** vốn có thể override bằng provider/SSLContext/socket settings.

Nếu muốn xem security property **thực tế** tại runtime mà không sửa policy, trong JShell Java 17:

```java
System.out.println(java.security.Security.getProperty(
        "jdk.tls.disabledAlgorithms"));
System.out.println(java.security.Security.getProperty(
        "jdk.certpath.disabledAlgorithms"));
```

Đây là **đoạn Java trong JShell** hoặc method, không phải lệnh PowerShell độc lập. Không in property đó như một “mật khẩu”; đây là policy cấu hình, nhưng cũng không đưa toàn debug log production ra kênh công khai.

## 8. Đọc cấu hình cipher suites và các extensions liên quan

**Protocol intersection chỉ là điều kiện cần, không đủ.** Hai peer có thể cùng cho TLS 1.3 nhưng vẫn handshake fail vì:

- Không có **cipher suite** chung được phép, hoặc khóa/cipher bị policy chặn.
- Không có **named group/key share** chung: `secp256r1`, `x25519`, `ffdhe2048`... tùy provider và protocol.
- Không có **signature scheme** tương thích certificate/key manager; ví dụ certificate chỉ hỗ trợ thuật toán ký không dùng được ở TLS 1.3.
- Certificate chain không được tin, thiếu intermediate, hết hạn, không đúng SAN/hostname, EKU/KU không phù hợp hoặc bị `jdk.certpath.disabledAlgorithms` từ chối.
- Server yêu cầu client certificate và client chưa cấp đúng chứng thư/private key.
- Lỗi **SNI** routing, **ALPN** (HTTP/2 vs HTTP/1.1), proxy, load balancer hay mTLS policy.
- Unsupported critical extension, revocation failure hoặc policy TLS riêng của framework.

**ALPN không phải TLS protocol version.** Khi HTTP/2 dùng TLS, ứng dụng có thể dùng ALPN chọn `h2`; TLS version như `TLSv1.3` là lớp **khác**. `SSLParameters.setApplicationProtocols(...)` và `SSLSocket.getApplicationProtocol()` phục vụ ALPN, còn `SSLSession.getProtocol()` trả TLS version. SNI chọn danh tính/certificate server theo hostname; không phải nơi đặt TLSv1.2/TLSv1.3.

### 8.1 Tên cipher suite nhìn sao cho đúng?

| Cipher suite | Version chủ yếu | Ý nghĩa |
| --- | --- | --- |
| `TLS_AES_128_GCM_SHA256` | TLS 1.3 | AES-128-GCM AEAD, SHA-256 trong HKDF key schedule |
| `TLS_AES_256_GCM_SHA384` | TLS 1.3 | AES-256-GCM AEAD, SHA-384 |
| `TLS_CHACHA20_POLY1305_SHA256` | TLS 1.3 | ChaCha20-Poly1305 AEAD, SHA-256 |
| `TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256` | TLS 1.2 | ECDHE key exchange, RSA certificate signature/auth, AES-128-GCM |
| `TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384` | TLS 1.2 | ECDHE, ECDSA authentication, AES-256-GCM |
| `TLS_RSA_WITH_AES_128_CBC_SHA` | Legacy TLS 1.2-era | RSA key transport và CBC; **không dùng** cho cấu hình mới |

Ngay cả khi `getSupportedCipherSuites()` chứa một tên legacy, system policy có thể cấm hoàn toàn. Ưu tiên default an toàn của JDK và hạn chế suites **theo chính sách đã kiểm chứng**; một allow-list tự viết nhưng quá chặt hay không phù hợp certificate có thể ngăn TLS 1.3 dù `enabledProtocols` đúng.

TLS 1.3 sử dụng key schedule khác TLS 1.2; khi troubleshoot không mong thấy `ServerKeyExchange` theo định dạng TLS 1.2 trong TLS 1.3. Một TLS record có `legacy_record_version` hoặc một `ClientHello` có `legacy_version=0x0303` **không có nghĩa** negotiated TLS 1.2: TLS 1.3 dùng `supported_versions` extension để đàm phán version thực tế. Hãy xem `SSLSession.getProtocol()` hoặc handshake trace `supported_versions` và ServerHello đúng ngữ cảnh.

## 9. Chẩn đoán lỗi handshake theo lớp

| Triệu chứng | Khả năng gây ra | Cách kiểm tra |
| --- | --- | --- |
| `SSLHandshakeException: No appropriate protocol` | Không có version/cipher phù hợp, có thứ bị disabled bởi policy | In enabled/supported của **hai phía**, security property và cipher suites |
| `Received fatal alert: protocol_version` | Peer từ chối protocol versions được đề xuất | So TLS versions và server-side policy, không bật giao thức lỗi thời một cách vội vàng |
| `Received fatal alert: handshake_failure` | **Nhiều khả năng**: cipher, key exchange group, sig scheme, certificate, client auth, SNI | Đọc handshake trace và peer config; không kết luận chỉ từ alert |
| `PKIX path building failed` | Truststore thiếu CA/intermediate, chain sai, cert không hợp lệ | Dùng đúng truststore, chain và path validation; không trust-all |
| `No subject alternative names matching ...` | Host/IP không phù hợp SAN | Cấp lại cert đúng DNS/iPAddress SAN hoặc kết nối tới hostname thật |
| `No available authentication scheme` | Server không có certificate/key/algorithm tương thích negotiated settings | Xem KeyManagerFactory, keyStore, key algorithm và sig schemes |
| Handshake dùng TLSv1.2 dù client hỗ trợ TLSv1.3 | Server không enable 1.3, TLS terminator/proxy hoặc policy khác | Xem `SSLSession.getProtocol` ở đúng endpoint và peer config |
| `getSupportedProtocols()` có TLSv1 nhưng handshake không dùng được | TLSv1 nằm trong `jdk.tls.disabledAlgorithms` hoặc bị policy khác cấm | Kiểm tra security property; không sửa policy chỉ vì supported |
| `SSLSession.getProtocol()` chưa có ý nghĩa | Socket chưa handshake hoặc handshake fail | Gọi `startHandshake` và chỉ dùng session khi thành công |
| Java code set `SSLParameters` nhưng kết quả không đổi | Chưa gọi `socket.setSSLParameters(params)`, đặt sau handshake, hoặc dùng socket khác | In params/objects ngay trước handshake |
| HTTPS URL thành công nhưng raw SSLSocket fail | Hai đường đi có trust, hostname, default protocols khác nhau | Đọc cấu hình đúng stack/framework của từng call |
| TLS protocol đúng nhưng HTTP/2 fail | ALPN không đồng ý `h2`, server/application policy | Tách TLS `getProtocol` khỏi ALPN `getApplicationProtocol` |
| Kết nối qua LB/CDN TLS 1.3 nhưng backend cũ | TLS termination và re-encryption diễn ra ở hai connection khác | Kiểm tra từng hop, không dùng kết quả frontend để suy backend |

### Debug JSSE

Trong môi trường thử nghiệm có quyền truy cập:

```powershell
# Ví dụ bật trace JSSE cho chương trình local.
java "-Djavax.net.debug=ssl,handshake" -cp $t LoopbackTlsHandshakeDemo (Join-Path $t "server.p12") (Join-Path $t "trust.p12") $pw
```

Log JSSE có thể rất dài, chứa cấu trúc handshake, certificate metadata, hostname, thông số bảo mật và tùy flags có thể hiện byte nhạy cảm. **Không bật các chế độ dump/key logger trong production hoặc gửi nguyên log công khai**. Chỉ dùng trên test fixtures, lưu vào nơi có quyền phù hợp và xóa sau khi chẩn đoán.

Khi đọc trace, tìm tuần tự: `ClientHello`, `supported_versions`, `ServerHello`, selected cipher suite, `Certificate`/certificate validation, handshake Finished. Không nhầm `legacy_version` với negotiated protocol. Với TLS 1.3, nhiều handshake messages sau ServerHello được bảo vệ; logger có quyền trong process có thể nhìn nhiều hơn packet capture ngoài mạng.

## 10. Cấu hình thực hành theo mô hình bảo mật

**Môi trường mới có client/server hiện đại:** thường cho phép TLS 1.3 và TLS 1.2 theo chính sách tổ chức, với TLS 1.3 được ưu tiên nếu hai phía tương thích. Đừng tắt TLS 1.2 chỉ vì có TLS 1.3 nếu còn peer hợp lệ cần TLS 1.2. Tuy nhiên nếu chính sách yêu cầu **chỉ TLS 1.3**, hãy áp dụng cả client/server và kiểm thử tương thích.

**Có legacy service:** trước tiên kiểm kê endpoint, chứng thư, cipher suites, named groups, load balancer và JDK version; lên lịch upgrade. **Không** tự hạ xuống TLS 1.0/1.1, SSLv3, RC4, 3DES hay cipher suite NULL/anonymous để che lỗi negotiation. Nếu phải duy trì legacy ngoài ý muốn, cần đánh giá rủi ro và cô lập có thời hạn theo chính sách bảo mật.

**Trong code JSSE:** sử dụng generic `SSLContext("TLS")`, để provider/security defaults an toàn làm cơ sở, cấu hình rõ ràng protocols và cipher suites khi có chính sách. Xác minh trust chain, hostname, EKU/KU, key sizes và certificate lifecycle qua trust manager/JSSE. Nếu dùng `HttpClient`/Netty/gRPC/server framework, kiểm tra nơi stack đó cấu hình SSL; **không giả định** mọi `-Dhttps.protocols` đều có tác dụng.

**Giám sát thực tế:** đo negotiated TLS version/cipher suite **trên connections đã thành công**, theo endpoint/hop và khoảng thời gian. Không coi thông tin hỗ trợ do scanner trả về (hoặc JDK source code) là bằng chứng negotiated. Không ghi session secrets hoặc dữ liệu chứng thực nhạy cảm trong telemetry.

**Thay đổi cấu hình:** deploy vào staging, chạy integration handshake tests cả client/server, test protocol intersection và cert trust, quan sát lỗi theo version. Các bản update JDK/security policy có thể thay đổi defaults/disabled list, nên không hardcode một snapshot của một JDK làm “sự thật vĩnh viễn”.

## 11. Các hiểu nhầm cần loại bỏ

**“`SSLContext.getInstance("TLS")` nghĩa là TLS 1.0?”** Không. `"TLS"` là tên generic context có thể hỗ trợ nhiều versions; đọc `getEnabledProtocols` và handshake thực.

**“Supported có TLSv1.1 tức là server cho phép TLSv1.1?”** Không. Supported có thể liệt kê các versions disabled; cấu hình socket, security policy và peer mới quyết định có negotiation hay không.

**“`setEnabledProtocols({"TLSv1"})` sẽ vượt `jdk.tls.disabledAlgorithms`?”** Không. Security restrictions vẫn áp dụng, dù API chấp nhận một tên supported.

**“TLS 1.3 suite `TLS_AES_128_GCM_SHA256` dùng RSA hay ECDHE?”** Tên suite **không mã hóa** thuật toán key agreement/certificate authentication; xem negotiated groups/signature schemes/handshake.

**“TLS 1.3 luôn có forward secrecy bất kể PSK?”** Không. Cơ chế PSK-only không có cùng bảo đảm như fresh (EC)DHE; phải xem mode/protocol thật.

**“TLS 1.3 0-RTT đồng nghĩa truyền mọi request sớm an toàn?”** Không. Early data có rủi ro replay và có thể không được JSSE/app hỗ trợ; giao thức cần bảo vệ/idempotency policy.

**“Sau khi gọi `getEnabledProtocols` có thể ghi negotiated version vào logs?”** Chưa. Chỉ `SSLSession.getProtocol` sau handshake thành công mới là version thực.

**“Chỉ cần TLS 1.3 là khỏi kiểm certificate, hostname?”** Sai. TLS version không thay trust, EKU/KU, SAN/hostname, revocation hoặc policy.

**“`https.protocols` điều khiển mọi kết nối SSL/TLS Java?”** Không. Nó chủ yếu ảnh hưởng `HttpsURLConnection`, không tự áp vào mọi HTTP client, server hay thư viện.

**“Packet TLS record hiển thị 0x0303 chắc negotiated TLS 1.2?”** Không. TLS 1.3 dùng compatibility/legacy fields; phải kiểm `supported_versions` và session.

**“Thất bại handshake chắc do không có TLS 1.3?”** Không. Lỗi cũng có thể do truststore, SAN, cipher suite, named group, signature scheme, SNI, ALPN hoặc client auth.

## 12. Tự kiểm tra

1. **Ba mức protocol quan trọng nhất khi debug?** Supported, enabled và **negotiated**, cộng thêm security restrictions và peer capability.
2. **API Java nào trả negotiated TLS version?** `SSLSession.getProtocol()` sau successful handshake.
3. **`getSupportedProtocols()` có thể chứa protocol bị disabled không?** Có; SunJSSE test hiển thị TLS 1.0/1.1 và SSLv3 dù không enabled.
4. **Security property vô hiệu hóa protocol/suite là gì?** `jdk.tls.disabledAlgorithms`; đọc bằng `Security.getProperty`.
5. **`jdk.tls.client.protocols` có phải `https.protocols` không?** Không; scope khác nhau, property thứ hai tập trung `HttpsURLConnection`.
6. **TLS 1.2 cipher suite khác TLS 1.3 như thế nào?** 1.2 thường chứa key exchange/auth/symmetric/hash; 1.3 chứa AEAD + HKDF hash, nhóm/chữ ký negotiate riêng.
7. **TLS 1.3 có còn RSA key transport không?** Không; RSA certificate/signature vẫn có thể dùng, nhưng không là RSA key transport kiểu cũ.
8. **TLS 1.3 luôn có 0-RTT không?** Không, optional và có rủi ro replay.
9. **Nếu client bật TLSv1.3 và TLSv1.2, server chỉ bật TLSv1.2, có thể negotiate gì?** TLSv1.2 nếu các điều kiện khác đạt.
10. **Nếu cả hai bật TLSv1.3, handshake chắc thành công không?** Không; cipher suites, groups, sig scheme, trust, hostname và policy có thể gây fail.
11. **Tại sao demo bật `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")`?** Để xác minh IP/DNS SAN, không chỉ trust certificate.
12. **`SSLSession.getCipherSuite()` cho biết gì?** Cipher suite của kết nối thành công, không thay thế việc hiểu group/signature trong TLS 1.3.
13. **Trường `legacy_version=0x0303` của TLS 1.3 chứng minh TLSv1.2 negotiated?** Không; xem supported_versions/ServerHello và SSLSession.
14. **Có nên gỡ TLSv1 khỏi disabledAlgorithms vì code legacy test fail?** Không mặc định; cần nâng cấp hoặc phê duyệt ngoại lệ an ninh rõ ràng.
15. **Phân biệt ALPN với TLS version?** ALPN chọn application protocol (ví dụ `h2`), TLS version là `TLSv1.2`/`TLSv1.3`.
16. **Có thể lấy protocol thực từ socket chưa kết nối như chương trình inventory?** Không; chỉ có supported/enabled/default, chưa negotiated.
17. **Tại sao không đoán cả hệ thống hỗ trợ TLS theo một SSLContext?** Mỗi client/server/framework/provider/hop có cấu hình riêng.
18. **Hai phía cùng có TLSv1.2 nhưng `SSLHandshakeException` xảy ra, kiểm gì tiếp?** Trust/certificate, cipher suite, named group, signature scheme, security policy, SNI và logs.
19. **Khi nào có `protocol_version` alert?** Khi peer từ chối version được đề xuất hoặc không đàm phán được version phù hợp; không đủ để suy ra toàn bộ nguyên nhân.
20. **Cách xác minh sau deploy?** Integration handshake thật qua đúng endpoint, kiểm session protocol/cipher, certificate identity và chính sách logging/monitoring.

## Tài liệu chính thức

- [Oracle Java 17 — JSSE Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-secure-socket-extension-jsse-reference-guide.html)
- [Oracle Java 17 — JDK Providers Documentation, SunJSSE protocols và cipher suites](https://docs.oracle.com/en/java/javase/17/security/oracle-providers.html)
- [Oracle Java 17 — Standard Algorithm Names, SSLContext](https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html)
- [SSLContext (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLContext.html)
- [SSLSocket (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLSocket.html)
- [SSLParameters (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLParameters.html)
- [SSLSession (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLSession.html)
- [SSLEngine (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLEngine.html)
- [RFC 5246 — TLS 1.2](https://www.rfc-editor.org/rfc/rfc5246.html)
- [RFC 8446 — TLS 1.3](https://www.rfc-editor.org/rfc/rfc8446.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tls-foundation-004">15.4 Cipher suite và khác biệt về thành phần thương lượng giữa các phiên bản TLS</a>

<details>
<summary>Xem chi tiết</summary>

# 15.4 Cipher suite và khác biệt về thành phần thương lượng giữa các phiên bản TLS

Khi một ứng dụng Java kết nối HTTPS, gRPC hoặc một dịch vụ TCP sử dụng TLS, hai phía không chỉ “bật TLS” rồi trao đổi dữ liệu. Chúng phải thống nhất **phiên bản giao thức, thuật toán bảo vệ record, cách thiết lập khóa chung và phương thức xác thực**. Một phần của việc này được mô tả bởi **cipher suite**. Tuy nhiên, **TLS 1.2 và TLS 1.3 định nghĩa ý nghĩa cipher suite khác nhau một cách căn bản**.

Trong TLS 1.2, một tên cipher suite thường nêu cả **cơ chế trao đổi khóa / xác thực phía server**, thuật toán mã hóa record và thuật toán MAC hoặc PRF. Trong TLS 1.3, cipher suite chủ yếu chọn **AEAD cho record** và **hash dùng trong HKDF/key schedule**; **key exchange group** và **signature algorithm** được thương lượng riêng qua extension. Vì vậy, đọc tên TLS 1.3 như thể nó là tên TLS 1.2 dẫn đến cấu hình sai, phân tích log sai và đôi khi hiểu nhầm mức bảo mật.

Bài học giải thích chi tiết việc thương lượng TLS 1.2 và TLS 1.3, cách đọc tên, vai trò ClientHello/ServerHello, certificate, supported_groups/key_share/signature_algorithms, PFS, ALPN, tình huống không tương thích và cách quan sát trên Java 17+ JSSE. Hai ví dụ Java biên dịch và chạy được giúp phân biệt **supported**, **enabled** và **actually negotiated**.

## 1. TLS thương lượng những gì?

**TLS (Transport Layer Security)** bảo vệ kênh liên lạc giữa hai endpoint. Ở cấu hình thông dụng, TLS cần:

1. **Protocol version:** TLS 1.2 hay TLS 1.3, quyết định cấu trúc handshake, key schedule và record protection.
2. **Record-protection algorithm:** cách mã hóa, xác thực và phát hiện dữ liệu bị sửa, ví dụ AES-GCM hoặc ChaCha20-Poly1305.
3. **Key establishment:** cách hai phía tạo ra các traffic secrets, thường dùng ephemeral (EC)DHE; TLS 1.3 còn hỗ trợ PSK và PSK+(EC)DHE.
4. **Peer authentication:** cách bên nhận xác minh server (và tùy chọn client), thường qua chứng chỉ X.509 và chữ ký số; hoặc bằng PSK theo cấu hình.
5. **Key derivation / transcript integrity:** thuật toán dẫn xuất key, hash của transcript và Finished nhằm ràng buộc các bước handshake.
6. **Thông tin ở tầng ứng dụng:** SNI để tìm virtual host; ALPN để chọn HTTP/2, HTTP/1.1 hoặc giao thức ứng dụng khác. Chúng là extension/chính sách riêng, **không phải một phần của tên cipher suite**.

~~~text
Ứng dụng Java (HTTPS, gRPC, TCP/TLS)
                   |
             JSSE / SSLEngine
                   |
        +----------+----------------------------+
        |                                       |
   TLS handshake                            TLS records
        |                                       |
protocol + cipher suite                  traffic keys/nonce
key exchange + authentication            AEAD / record integrity
SNI + ALPN + transcript                  protected application data
        |
        v
  SSLSession: protocol và cipher suite ĐÃ chọn
~~~

**Cipher suite không đồng nghĩa với toàn bộ TLS security profile.** Một kết nối có thể ghi nhận TLS_AES_128_GCM_SHA256, nhưng log riêng còn cần xác định certificate/issuer, key exchange group, signature scheme, ALPN và chính sách TLS endpoint nếu muốn đánh giá đầy đủ.

### 1.1. Ba trạng thái thường bị nhầm

| Khái niệm | Ý nghĩa | API Java thường dùng |
| --- | --- | --- |
| **Supported** | Implementation/provider có thể hỗ trợ thuật toán hoặc phiên bản | SSLContext.getSupportedSSLParameters, SSLSocket.getSupportedCipherSuites / getSupportedProtocols. |
| **Enabled** | Những lựa chọn được bật trong cấu hình endpoint, có thể bị policy/provider giới hạn | SSLContext.getDefaultSSLParameters, SSLSocket.getEnabledCipherSuites / getEnabledProtocols. |
| **Negotiated** | **Một lựa chọn cụ thể đã được hai phía chọn sau handshake thành công** | SSLSocket.getSession().getCipherSuite(), getSession().getProtocol(). |

**Supported không chắc đã enabled; enabled không chắc đã negotiated.** Nếu peer không hỗ trợ, certificate không phù hợp, thiếu group/key share hoặc bị chính sách algorithm constraints loại bỏ, handshake có thể thất bại. Kết quả cuối còn chịu ảnh hưởng provider, JDK config, key material và policy của hai phía.

## 2. Cách đọc tên cipher suite trong TLS 1.2

Một cấu trúc phổ biến của các suite TLS 1.2:

~~~text
TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
|    |     |         |   |   |   |
|    |     |         |   |   |   +--> PRF hash: SHA-256
|    |     |         |   |   +------> AES mode GCM (AEAD)
|    |     |         |   +----------> AES key 128-bit
|    |     |         +--------------> record encryption algorithm
|    |     +------------------------> RSA-based server authentication
|    +------------------------------> Ephemeral EC Diffie–Hellman key agreement
+-----------------------------------> TLS cipher suite name prefix
~~~

Đây là cách diễn giải tên cụ thể, không phải grammar áp dụng cho mọi cipher suite lịch sử: một số suite dùng RSA key transport, static DH, PSK, CBC/HMAC hoặc các biến thể khác.

**TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256** có nghĩa:

- **ECDHE:** hai phía thực hiện **ephemeral Elliptic Curve Diffie–Hellman**, tạo shared secret. Ephemeral key agreement giúp có **forward secrecy** khi triển khai đúng.
- **RSA:** server chứng minh quyền sở hữu **private key RSA** tương ứng certificate bằng phép ký handshake (authentication). **Không có nghĩa client lấy RSA public key để mã hóa premaster secret** trong suite ECDHE_RSA.
- **AES_128_GCM:** record protection dùng AES-128-GCM thuộc họ **AEAD**, bảo vệ cả confidentiality lẫn integrity.
- **SHA256 ở cuối:** theo RFC 5289, là **hash của TLS 1.2 PRF** cho suite GCM này. **Không phải HMAC-SHA256 được tính độc lập lên từng record**, bởi GCM đã có authentication tag riêng.

### 2.1. So sánh vài suite TLS 1.2

| Tên suite TLS 1.2 | Key establishment / authentication | Bảo vệ dữ liệu | Lưu ý |
| --- | --- | --- | --- |
| **TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256** | Ephemeral ECDH, server certificate RSA | AES-128-GCM AEAD | ECDHE cho PFS; SHA256 là PRF hash. |
| **TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256** | Ephemeral ECDH, server certificate ECDSA | AES-128-GCM AEAD | ECDHE là trao đổi khóa, ECDSA là ký/xác thực. |
| **TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384** | Ephemeral ECDH, RSA authentication | AES-256-GCM AEAD | SHA384 là PRF hash theo profile. |
| **TLS_RSA_WITH_AES_128_GCM_SHA256** | RSA key transport và RSA authentication | AES-128-GCM AEAD | **Không có PFS** theo cơ chế RSA key transport; không dùng cho thiết kế mới. |
| **TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA** | Ephemeral ECDH, RSA authentication | AES-CBC + HMAC-SHA1 | Legacy CBC/MAC; đòi hỏi xử lý padding/oracle đúng, tránh khi có AEAD tốt hơn. |

Các tên trong bảng **không đương nhiên enabled** trên mọi Java runtime. Một thuật toán được provider hỗ trợ có thể bị **jdk.tls.disabledAlgorithms**, certificate policy, khuyết thiếu key material hoặc cấu hình ứng dụng vô hiệu hóa.

### 2.2. Vì sao _SHA và _SHA256 không luôn cùng ý nghĩa?

Trong **TLS 1.2 CBC suites**, phần đuôi như **_SHA** có thể xác định HMAC-SHA1 dùng cho record MAC, còn PRF của TLS 1.2 theo quy định thích hợp (thường SHA-256). Trong **TLS 1.2 AEAD/GCM suites** như ECDHE_RSA_WITH_AES_128_GCM_SHA256, **GCM authentication tag** thay chức năng record HMAC; **SHA256** chỉ định PRF hash.

**Không nhìn thấy SHA256 rồi kết luận record dùng HMAC-SHA256**, và cũng không nhìn thấy _RSA rồi kết luận đang dùng RSA key exchange. Phải phân tích **toàn bộ scheme** theo đặc tả của suite.

## 3. TLS 1.2 thương lượng key exchange và authentication như thế nào?

Một TLS 1.2 full handshake dùng certificate và ECDHE có thể được hình dung:

~~~text
Client                                           Server
  |                                                |
  | ClientHello: version, suites, groups, extensions|
  |----------------------------------------------->|
  |                                                |
  |       ServerHello: version, chọn cipher suite   |
  |       Certificate (RSA/ECDSA theo suite)        |
  |       ServerKeyExchange: ephemeral params + ký  |
  |       ServerHelloDone                           |
  |<-----------------------------------------------|
  | ClientKeyExchange: ephemeral key share          |
  | ChangeCipherSpec + Finished                     |
  |----------------------------------------------->|
  |                  ChangeCipherSpec + Finished    |
  |<-----------------------------------------------|
  |            application data protected           |
~~~

Đây là **sơ đồ đơn giản hóa** cho handshake đầy đủ TLS 1.2 dùng ECDHE; các extension, certificate request/client authentication và resumed handshakes có thể thay đổi thông điệp. Dòng **ServerKeyExchange** quan trọng với ECDHE, vì server phải ràng buộc ephemeral parameters bằng chữ ký/cơ chế xác thực thích hợp.

- **ClientHello** công bố các cipher suite chấp nhận được, version và các extension tương ứng.
- **ServerHello** chọn một suite phù hợp trong danh sách client đưa ra và policy server.
- Các phần ECDHE và certificate phải **tương thích với key exchange/authentication đã được chọn trong suite**.
- Hai phía cùng tính key material, xác minh handshake transcript/Finished; nếu không khớp hoặc certificate không tin cậy, handshake thất bại.
- **ALPN** có thể được thương lượng qua extension độc lập; một kết nối TLS 1.2 có thể chạy HTTP/2 hoặc HTTP/1.1 tùy chính sách chấp nhận và cấu hình.

Nếu server chỉ có certificate ECDSA, nó không thể đơn giản chọn suite **ECDHE_RSA** mà không có RSA key/đặc tính xác thực phù hợp. Ngược lại, TLS 1.2 suite **ECDHE_ECDSA** yêu cầu certificate/cơ chế ký tương ứng.

### 3.1. RSA key transport khác ECDHE_RSA

| Cơ chế TLS 1.2 | Vai trò RSA | Forward secrecy khi private key certificate bị lộ sau này? |
| --- | --- | --- |
| **TLS_RSA_...** | Client mã hóa premaster secret bằng RSA public key của server | **Không**, vì bản ghi handshake có thể bị lưu để giải mã về sau bằng RSA private key bị lộ. |
| **TLS_ECDHE_RSA_...** | RSA private key ký/xác thực ephemeral ECDHE handshake | **Có thể có PFS** khi ephemeral secret được xử lý/xóa an toàn. |

Do đó, **“có RSA trong tên” không đủ biết có PFS hay không**. Phân biệt **TLS_RSA_** và **TLS_ECDHE_RSA_**.

## 4. TLS 1.3 định nghĩa cipher suite lại từ đầu

RFC 8446 thay đổi quan trọng: một **TLS 1.3 cipher suite** quy định **cặp (AEAD cho record protection, hash dùng cho HKDF)**, **không quy định key exchange/authentication trong tên suite**.

~~~text
TLS_AES_128_GCM_SHA256
|   |   |   |   |
|   |   |   |   +--> Hash SHA-256 cho HKDF và handshake transcript
|   |   |   +------> GCM mode
|   |   +----------> AES key 128 bit
|   +--------------> AEAD algorithm cho TLS record
+------------------> TLS cipher suite
~~~

**TLS_AES_128_GCM_SHA256** không cho biết server đang dùng RSA, ECDSA hay Ed25519 certificate, cũng không cho biết key exchange group là X25519 hay P-256. Những thành phần này được thương lượng **riêng**, nên **cùng một TLS 1.3 suite có thể đi cùng nhiều loại certificate và group** được cho phép.

### 4.1. Những suite được RFC 8446 định nghĩa

| TLS 1.3 cipher suite | AEAD / protection | Hash cho HKDF / transcript | Nhận xét |
| --- | --- | --- | --- |
| **TLS_AES_128_GCM_SHA256** | AES-128-GCM | SHA-256 | Phổ biến, có thể tận dụng tăng tốc AES. |
| **TLS_AES_256_GCM_SHA384** | AES-256-GCM | SHA-384 | Khóa AES 256 bit; cần support/policy tương ứng. |
| **TLS_CHACHA20_POLY1305_SHA256** | ChaCha20-Poly1305 | SHA-256 | Phù hợp môi trường không có tăng tốc AES tốt. |
| **TLS_AES_128_CCM_SHA256** | AES-128-CCM | SHA-256 | Được RFC xác định, support phụ thuộc implementation. |
| **TLS_AES_128_CCM_8_SHA256** | AES-128-CCM tag 8 byte | SHA-256 | Profile tag ngắn, cần kiểm tra policy và giới hạn ứng dụng. |

Đây là **danh sách suite TLS 1.3 được RFC 8446 định nghĩa ban đầu**, không phải bảo đảm rằng Java/provider hiện tại hỗ trợ hoặc bật tất cả. Các RFC khác có thể đăng ký cipher suite bổ sung; khi đánh giá thực tế phải dựa vào registry, chuẩn liên quan và policy triển khai.

### 4.2. SHA256 trong tên TLS 1.3 dùng cho việc gì?

TLS 1.3 dùng **HKDF** để dẫn xuất **handshake traffic secrets, application traffic secrets, key/IV** từ shared secret và transcript của handshake. Hash của cipher suite cũng được dùng cho **transcript hash/Finished key schedule**.

**Không dùng SHA-256 làm record MAC rời** khi chọn AES-GCM: record đã được **AEAD** xác thực bằng authentication tag. Vì vậy, từ tên TLS_AES_128_GCM_SHA256 ta có thể biết AES-GCM và HKDF-SHA256, **không thể suy ra public key algorithm hoặc phiên bản chứng chỉ**.

### 4.3. TLS 1.3 và TLS 1.2 suite không thể hoán đổi

Theo RFC 8446:

- **TLS 1.3 cipher suite** như TLS_AES_128_GCM_SHA256 **chỉ dùng trong TLS 1.3**.
- **TLS 1.2 suite** như TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256 **không được thương lượng làm TLS 1.3 suite**.
- Dù hai tên đều chứa **AES_128_GCM_SHA256**, chúng không chỉ là hai spelling của cùng một bộ tham số. Một tên chỉ định key exchange/authentication ở TLS 1.2; tên kia chỉ AEAD/HKDF hash ở TLS 1.3.

Đây là lý do **không cấu hình một tập tên suite TLS 1.2 rồi mong TLS 1.3 tự dùng nó**. JSSE có thể có chung API setEnabledCipherSuites cho cả hai phiên bản, nhưng người lập trình phải chọn các cipher suite **phù hợp từng TLS protocol**.

## 5. TLS 1.3 thương lượng thêm những thành phần nào?

Theo RFC 8446 §4.1.1, ở kết nối certificate-based (không chọn PSK), các nhóm lựa chọn gần như độc lập:

1. **cipher_suites:** cặp AEAD/HKDF hash.
2. **supported_groups + key_share:** nhóm (EC)DHE hỗ trợ và key share đang gửi để thiết lập shared secret.
3. **signature_algorithms** (và có thể signature_algorithms_cert): thuật toán ký mà bên nhận chấp nhận cho xác thực và certificate.
4. **supported_versions:** những phiên bản TLS mà client thực sự hỗ trợ.
5. Nếu dùng PSK: **pre_shared_key + psk_key_exchange_modes** bổ sung một con đường xác thực/thiết lập khóa; thay đổi những tập lựa chọn hợp lệ.

Ngoài ra **ALPN** có thể chọn giao thức ứng dụng, **server_name (SNI)** giúp định tuyến virtual host/certificate. Chúng **không được thể hiện trong cipher suite name**.

~~~text
ClientHello TLS 1.3
 |
 +-- supported_versions: [TLS 1.3, TLS 1.2]
 +-- cipher_suites: [TLS_AES_128_GCM_SHA256, TLS_CHACHA20_POLY1305_SHA256, ...]
 +-- supported_groups: [X25519, secp256r1, ...]
 +-- key_share: key-share cho một/vài group
 +-- signature_algorithms: [RSA-PSS, ECDSA, Ed25519, ...]
 +-- signature_algorithms_cert: optional
 +-- server_name / ALPN: ví dụ api.example và h2/http1.1
 +-- PSK extensions: khi dùng PSK/resumption
 |
 v
Server selects a compatible combination
 |
 +-- TLS version
 +-- one TLS 1.3 cipher suite (AEAD + hash)
 +-- group/key share (nếu dùng DHE)
 +-- cert + handshake signature scheme (nếu dùng certificate)
 +-- ALPN (nếu ứng dụng dùng)
 |
 v
Handshake transcript -> HKDF traffic keys -> AEAD records
~~~

**Quan hệ độc lập không có nghĩa mọi tổ hợp đều chạy được**. Server phải có certificate/key tương thích với thuật toán chữ ký client chấp nhận, group và key share được hỗ trợ, cipher suite có overlap và chính sách/provider cho phép. TLS 1.3 cần certificate key phù hợp để phát ra CertificateVerify; tên suite **không thể tự giải quyết một certificate không hợp lệ**.

### 5.1. supported_groups và key_share không giống nhau

**supported_groups** là tập những nhóm key exchange client hỗ trợ; **key_share** là các giá trị public share được client thực sự gửi cho một số group.

Ví dụ client hỗ trợ **X25519 và secp256r1** nhưng chỉ gửi key_share cho X25519. Nếu server muốn dùng secp256r1 và hai bên tương thích, server có thể gửi **HelloRetryRequest** yêu cầu client gửi một key_share phù hợp. HelloRetryRequest là một nhánh handshake có bảo vệ ràng buộc transcript; không phải mặc nhiên là lỗi hay một yêu cầu “cho phép downgrade”.

Nếu không có group phù hợp cho kết nối yêu cầu (EC)DHE, handshake thất bại. Tuy nhiên, **PSK-only** mode của TLS 1.3 có logic khác: nó có thể không sử dụng (EC)DHE nên không nhất thiết đòi overlap supported_groups. Không suy ra có PFS chỉ từ cipher suite.

### 5.2. signature_algorithms và certificate trong TLS 1.3

Một server có thể dùng:

- **RSA public key** với một profile **RSA-PSS** được hỗ trợ để ký CertificateVerify.
- **ECDSA P-256 public key** với signature scheme tương thích.
- **Ed25519** với signature scheme tương ứng, nếu cả hai JSSE/provider và policy tin cậy hỗ trợ.

Cùng với các profile này, kết nối vẫn có thể thương lượng **TLS_AES_128_GCM_SHA256** cho record. Đây là thay đổi đáng nhớ so với TLS 1.2 **ECDHE_RSA** vs **ECDHE_ECDSA** được phản ánh trong tên cipher suite.

**Lưu ý chi tiết:** signature scheme cho **CertificateVerify** và signature algorithm dùng để ký **chứng chỉ trong certificate chain** là hai câu chuyện có thể chịu extension/policy khác nhau. Trong TLS 1.3, tên cipher suite không quyết định cả hai; không kết luận “SHA256 trong tên suite nghĩa là certificate phải ký SHA-256”.

### 5.3. Các sơ đồ TLS 1.3 1-RTT với certificate

~~~text
Client                                            Server
  |                                                 |
  | ClientHello + supported_versions + suites       |
  | + supported_groups + key_share + signature_algs  |
  |------------------------------------------------>|
  |                                                 |
  |            ServerHello + selected suite/key_share|
  |            {EncryptedExtensions}                |
  |            {Certificate}                        |
  |            {CertificateVerify}                  |
  |            {Finished}                           |
  |<------------------------------------------------|
  | {Finished}                                      |
  |------------------------------------------------>|
  |        [Protected application data]             |
~~~

Trong sơ đồ RFC 8446, ngoặc **{ }** biểu thị các handshake message được bảo vệ bằng **handshake traffic keys**; **[ ]** biểu thị application data bảo vệ bằng **application traffic keys**. Hình trên giản lược tùy chọn client authentication, resumption/PSK, HelloRetryRequest và các trường khác.

TLS 1.3 đưa nhiều handshake message vào vùng mã hóa sớm hơn TLS 1.2, có **HKDF-based key schedule** và bảo vệ record bằng AEAD. **ChangeCipherSpec** có thể xuất hiện ở compatibility mode nhưng không được diễn giải giống transition state trong TLS 1.2.

### 5.4. 0-RTT/PSK là ngoại lệ bảo mật cần nhìn riêng

TLS 1.3 cho phép **PSK resumption**, có thể kết hợp DHE hoặc không:

- **psk_dhe_ke:** sử dụng PSK và (EC)DHE; có thể mang tính forward secrecy cho traffic phù hợp khi triển khai đúng.
- **psk_ke:** chỉ dùng PSK, **không có forward secrecy** do thiếu ephemeral DHE.
- **0-RTT early data:** gửi dữ liệu khi handshake chưa hoàn tất, bảo vệ dựa trên PSK; **không có bảo đảm anti-replay xuyên kết nối** như 1-RTT thông thường và early-data key không có full forward secrecy.

Do đó, câu nói **“TLS 1.3 luôn có PFS”** là quá đơn giản. Với các thiết kế API có thể thay đổi trạng thái như chuyển tiền, tạo đơn, phát mã giảm giá, **không xử lý 0-RTT như request mới chắc chắn không bị lặp**. Khi sử dụng 0-RTT phải có cơ chế chống replay/idempotency và tuân thủ policy ứng dụng.

## 6. Bảng so sánh TLS 1.2 và TLS 1.3 theo thành phần thương lượng

| Hạng mục | TLS 1.2 | TLS 1.3 |
| --- | --- | --- |
| **Ý nghĩa cipher suite** | Thường nêu key exchange/authentication, record cipher, MAC/PRF | Chọn **AEAD + HKDF hash**; không đưa key exchange/certificate signature vào tên. |
| **Ví dụ suite** | TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256 | TLS_AES_128_GCM_SHA256 |
| **Version negotiation** | Trường phiên bản/extension theo TLS 1.2; không dùng quy tắc supported_versions TLS 1.3 | **supported_versions** extension xác định TLS 1.3; legacy_version có giá trị tương thích. |
| **Key establishment** | RSA transport, (EC)DHE hoặc các phương thức cũ theo suite | (EC)DHE, PSK+(EC)DHE, PSK-only theo profile/mode. |
| **Certificate/signature** | Có quan hệ chặt với phần RSA/ECDSA trong tên suite; signature_algorithms extension vẫn có vai trò | **signature_algorithms**, có thể signature_algorithms_cert; certificate/key được chọn riêng. |
| **Groups/key shares** | Supported curves/groups theo extension và thông điệp key exchange | **supported_groups** + **key_share**, HelloRetryRequest nếu cần share khác. |
| **Record protection** | Có CBC/HMAC và AEAD tùy suite | Theo RFC 8446 ban đầu là các suite **AEAD**; các RFC bổ sung cần đánh giá riêng. |
| **Key schedule** | TLS 1.2 PRF, master secret, key expansion | **HKDF** kết hợp handshake transcript và secrets theo giai đoạn. |
| **Forward secrecy** | Có với ECDHE/DHE phù hợp, không có với static RSA key transport | Có thể có với (EC)DHE; **PSK-only và 0-RTT** không có cùng bảo đảm. |
| **Handshake flow** | ServerHello, Certificate, ServerKeyExchange, ChangeCipherSpec, Finished theo profile | ServerHello, EncryptedExtensions, CertificateVerify, Finished; các phần mã hóa sớm. |
| **Renegotiation** | Có các cơ chế và rủi ro lịch sử; ứng dụng nên hạn chế/kiểm soát | **Không hỗ trợ renegotiation kiểu TLS 1.2**; có KeyUpdate và post-handshake authentication trong điều kiện thích hợp. |
| **ALPN/SNI** | Extension riêng, không nằm trong cipher suite | Vẫn là extension riêng, không nằm trong cipher suite. |
| **Resumption** | Thường session ID/ticket theo TLS 1.2 | PSK/ticket theo TLS 1.3; có thể 0-RTT nếu được phép. |

**Lưu ý về ngoại lệ:** TLS 1.3 RFC 8446 ban đầu định nghĩa bộ suite AEAD và hash; một số phần mở rộng sau này, ví dụ RFC 9150, định nghĩa integrity-only TLS 1.3 suites. Khi nói “TLS 1.3 dùng AEAD” trong HTTPS phổ thông, hiểu đó là profile bảo mật có mã hóa thông dụng; không khẳng định mọi suite đăng ký về sau đều mã hóa nội dung.

### 6.1. Cùng tên hash nhưng mục đích khác nhau

**TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256** (TLS 1.2):
- **SHA256** dùng trong TLS 1.2 PRF theo RFC 5289.
- **RSA** là cách xác thực certificate/handshake.
- **ECDHE** xác định key establishment.
- **GCM** đã đảm nhận authentication tag của record.

**TLS_AES_128_GCM_SHA256** (TLS 1.3):
- **SHA256** dùng trong HKDF/key schedule và transcript theo RFC 8446.
- Không nói certificate dùng RSA hay ECDSA.
- Không nói nhóm X25519 hay P-256.
- Vẫn dùng AES-128-GCM để bảo vệ record.

Cả hai đều có AES-128-GCM nhưng **không có khả năng hoán đổi cipher suite giữa hai phiên bản**.

## 7. ALPN, SNI, certificate và hostname: ngoài tên cipher suite

### 7.1. ALPN

**Application-Layer Protocol Negotiation (ALPN)** cho phép chọn giao thức ứng dụng qua TLS: ví dụ client quảng bá h2 và http/1.1, server chọn protocol phù hợp.

- ALPN **không phải TLS protocol version**: h2 không nghĩa TLS 1.2, http/1.1 không nghĩa TLS 1.3.
- ALPN **không phải cipher suite**: cùng TLS_AES_128_GCM_SHA256 có thể phục vụ các protocol ứng dụng được phép.
- Cấu hình ALPN phải tương thích policy ứng dụng: HTTP/2 có ràng buộc về các phiên bản/cipher suite theo chuẩn liên quan.
- Trong Java, **SSLParameters.setApplicationProtocols** cấu hình danh sách; sau handshake **SSLSocket.getApplicationProtocol** cho biết protocol đã thương lượng, nếu implementation hỗ trợ.

### 7.2. SNI

**Server Name Indication (SNI)** giúp server nhiều virtual hosts chọn certificate và cấu hình phù hợp. Không tự coi SNI là chứng minh hostname: client vẫn phải kiểm tra **certificate chain + hostname** theo endpoint identification/trust policy, nhất là raw SSLSocket/SSLEngine.

- SNI có thể ảnh hưởng certificate server chọn, từ đó ảnh hưởng signature schemes.
- Không suy rằng TLS 1.3 suite có chữ RSA nên certificate phải RSA: tên suite 1.3 không chỉ định RSA.
- Java HTTPS libraries thường có hostname verification mặc định; khi dùng raw SSLSocket cần xem xét **SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")** và trust manager đúng, không dùng TrustManager chấp nhận mọi certificate.

### 7.3. mTLS (mutual TLS)

TLS thường xác thực **server**; **client certificate** là tùy chọn theo chính sách. Trong Java, server có thể yêu cầu bằng **SSLServerSocket.setNeedClientAuth(true)** hoặc SSLParameters. mTLS bổ sung nhận diện client, nhưng vẫn cần authorization ở tầng ứng dụng: certificate hợp lệ chưa đồng nghĩa được phép gọi mọi API.

## 8. Java JSSE: các API liên quan đến cipher suite

Các class quan trọng của **javax.net.ssl**:

| API | Việc thực hiện | Lưu ý |
| --- | --- | --- |
| **SSLContext** | Cấu hình key managers, trust managers và nguồn ngẫu nhiên; tạo socket/engine | SSLContext("TLS") không đồng nghĩa buộc một version. |
| **SSLSocketFactory / SSLServerSocketFactory** | Tạo TLS client/server socket | Mỗi socket còn có enabled settings. |
| **SSLSocket / SSLServerSocket** | Lấy supported/enabled versions và suites, set policy | setEnabledCipherSuites phải dùng tên provider hỗ trợ; không vượt được algorithm constraints. |
| **SSLParameters** | Truyền cipher suites/protocols, ALPN, endpoint identification, client auth... | Các trường ảnh hưởng handshake độc lập. |
| **SSLSession** | Ghi nhận protocol và suite trên kết nối **đã thiết lập** | getProtocol/getCipherSuite chỉ là kết quả handshake, không phải toàn bộ security profile. |
| **SSLEngine** | TLS state machine không phụ thuộc blocking socket, cho async frameworks | Cũng có handshake status, supported/enabled và SSLSession. |
| **KeyManagerFactory** | Cung cấp private keys/certificates | Key/certificate ảnh hưởng signature scheme có thể chọn. |
| **TrustManagerFactory** | Định cấu hình kiểm tra certificate peer | Không thay bằng trust-all trong production. |

**Supported ≠ enabled ≠ negotiated** là bất biến khi quan sát JSSE. **SSLContext.getSupportedSSLParameters** có thể liệt kê protocol/suite nhiều hơn default. **setEnabledProtocols** hoặc **setEnabledCipherSuites** giới hạn danh sách thử, nhưng **không bảo đảm handshake thành công** nếu peer không có overlap hoặc bị chính sách bảo mật loại bỏ.

### 8.1. SSLContext.getInstance("TLS") có dùng phiên bản nào?

**"TLS"** là tên ngữ cảnh chung cho JSSE provider, có thể hỗ trợ nhiều phiên bản. Phiên bản thực tế do **enabled protocols**, supported protocols, JDK policy và peer negotiation quyết định. Sau **startHandshake**, đọc **SSLSession.getProtocol()**; không in SSLContext.getProtocol rồi coi đó là protocol phiên kết nối.

### 8.2. Có nên pin một cipher suite?

- **Lợi ích:** đáp ứng security policy, tái hiện test, loại bỏ suites yếu và kiểm soát compatibility.
- **Rủi ro:** client/server không có overlap, certificate sai loại, provider không hỗ trợ hoặc policy thay đổi sau JDK update.
- **Thực hành:** cấu hình **tập suite hiện đại được phê duyệt**, ưu tiên TLS 1.3; giữ TLS 1.2 ECDHE+AEAD khi cần tương thích. Tránh cứng hóa một suite đơn lẻ nếu không có yêu cầu rõ và kiểm thử đầy đủ.

Trong test integration có thể pin một suite để **chứng minh hai handshake khác nhau**, nhưng production thường cần policy linh hoạt cho client hợp lệ và lộ trình nâng cấp.

## 9. Java 17+: kiểm kê supported và enabled

Ví dụ đầu tiên không mở kết nối mạng, chỉ hỏi **SSLContext** trên JDK đang chạy về protocol và cipher suite. Đây là quan sát cấu hình, **không phải bằng chứng một kết nối sẽ chọn suite đó**.

Tạo file **TlsSuitesInspectDemo.java**:

~~~java
import java.security.SecureRandom;
import java.util.Arrays;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

public class TlsSuitesInspectDemo {
    private static boolean contains(String[] values, String wanted) {
        return Arrays.asList(values).contains(wanted);
    }

    public static void main(String[] args) throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, null, new SecureRandom());

        SSLParameters supported = context.getSupportedSSLParameters();
        SSLParameters enabled = context.getDefaultSSLParameters();
        System.out.println("Provider: " + context.getProvider().getName());
        System.out.println("TLSv1.3 được hỗ trợ: " +
                contains(supported.getProtocols(), "TLSv1.3"));
        System.out.println("TLSv1.2 được hỗ trợ: " +
                contains(supported.getProtocols(), "TLSv1.2"));
        System.out.println("TLS 1.3 AES-GCM được hỗ trợ: " +
                contains(supported.getCipherSuites(), "TLS_AES_128_GCM_SHA256"));
        System.out.println("TLS 1.2 ECDHE-RSA được hỗ trợ: " +
                contains(supported.getCipherSuites(),
                        "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"));
        System.out.println("TLSv1.3 đang bật mặc định: " +
                contains(enabled.getProtocols(), "TLSv1.3"));
        System.out.println("TLSv1.2 đang bật mặc định: " +
                contains(enabled.getProtocols(), "TLSv1.2"));
    }
}
~~~

Chạy bằng **JDK 17+**:

~~~bash
javac --release 17 -encoding UTF-8 TlsSuitesInspectDemo.java
java TlsSuitesInspectDemo
~~~

Kết quả **trên JDK SunJSSE đã thử** (runtime khác có thể báo provider hoặc boolean khác):

~~~text
Provider: SunJSSE
TLSv1.3 được hỗ trợ: true
TLSv1.2 được hỗ trợ: true
TLS 1.3 AES-GCM được hỗ trợ: true
TLS 1.2 ECDHE-RSA được hỗ trợ: true
TLSv1.3 đang bật mặc định: true
TLSv1.2 đang bật mặc định: true
~~~

### 9.1. Giải thích

- **SSLContext.getInstance("TLS")** chọn provider và context hỗ trợ các phiên bản TLS theo implementation; không mở TCP.
- **init(null,null,new SecureRandom())** dùng key/trust managers theo provider defaults; ở ví dụ này không phát sinh kết nối.
- **getSupportedSSLParameters** mô tả khả năng được công bố; **getDefaultSSLParameters** là tập mặc định.
- Tên suite TLS 1.2 và TLS 1.3 cùng xuất hiện trong một API Java, nhưng chúng **chỉ dùng đúng phiên bản phù hợp khi handshake**.
- Kết quả true không chứng minh key của server đã cấu hình, certificate tin cậy, group phù hợp hay ALPN được thương lượng.
- Cấu hình **jdk.tls.disabledAlgorithms**, provider và security policy có thể đổi enabled/default qua từng môi trường. Không sao chép stdout làm tiêu chuẩn chung.

Để chứng minh một kết nối thực sự đã chọn suite nào, cần thực hiện **handshake** và đọc **SSLSession**, như ví dụ tiếp theo.

## 10. Java 17+: hai handshake thật cho TLS 1.2 và TLS 1.3

Ví dụ thứ hai chạy một TLS server **chỉ trên địa chỉ loopback 127.0.0.1**, rồi dùng client JSSE tạo handshake thật. Chương trình kiểm tra **SSLSession.getProtocol()**, **getCipherSuite()** và **getApplicationProtocol()** ở cả hai phía để cho thấy TLS 1.2 và TLS 1.3 đã thương lượng **hai cipher suite khác nhau**.

Để tránh dùng một TrustManager “tin mọi certificate” (thói quen cực kỳ nguy hiểm), bài test dùng một certificate **self-signed chỉ tạo cho localhost bằng keytool**, nạp chính keystore test làm trust anchors trong client context và bật **HTTPS endpoint identification**. Đây chỉ là một minh họa **trust cục bộ được cấu hình rõ**; production phải dùng truststore/PKI của tổ chức hoặc cơ chế tin cậy thích hợp, không copy certificate test này.

### 10.1. Tạo tệp PKCS12 test bằng keytool

Tại một thư mục làm bài tập có quyền ghi, dùng **JDK 17+ keytool**:

~~~bash
keytool -genkeypair -alias localhost -keyalg RSA -keysize 2048 -validity 1 -storetype PKCS12 -keystore loopback.p12 -storepass local-demo-pass -keypass local-demo-pass -dname "CN=localhost" -ext "SAN=dns:localhost,ip:127.0.0.1" -noprompt
~~~

Câu lệnh tạo một khóa RSA **chỉ để test** và certificate self-signed có Subject Alternative Name cho localhost. **local-demo-pass chỉ là password công khai của bài tập**, không được dùng trong ứng dụng thật. Certificate có validity một ngày để giảm nguy cơ vô tình coi nó là tài sản lâu dài. Nếu file loopback.p12 đã tồn tại, làm việc trong một thư mục test mới thay vì ghi đè dữ liệu có giá trị.

### 10.2. Tạo file TlsNegotiationDemo.java

~~~java
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

public class TlsNegotiationDemo {
    private static final String ALPN = "demo/1";

    private static SSLContext[] contexts(Path file, char[] password)
            throws Exception {
        KeyStore keys = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(file)) {
            keys.load(input, password);
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keys, password);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keys);

        SSLContext server = SSLContext.getInstance("TLS");
        server.init(kmf.getKeyManagers(), null, new SecureRandom());
        SSLContext client = SSLContext.getInstance("TLS");
        client.init(null, tmf.getTrustManagers(), new SecureRandom());
        return new SSLContext[] {server, client};
    }

    private static String runOne(SSLContext serverContext,
            SSLContext clientContext, String protocol, String suite)
            throws Exception {
        InetAddress host = InetAddress.getByName("127.0.0.1");
        try (SSLServerSocket server = (SSLServerSocket)
                serverContext.getServerSocketFactory()
                        .createServerSocket(0, 8, host)) {
            server.setEnabledProtocols(new String[] {protocol});
            server.setEnabledCipherSuites(new String[] {suite});
            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                Future<String> serverResult = pool.submit(() -> {
                    try (SSLSocket peer = (SSLSocket) server.accept()) {
                        peer.setEnabledProtocols(new String[] {protocol});
                        peer.setEnabledCipherSuites(new String[] {suite});
                        SSLParameters p = peer.getSSLParameters();
                        p.setApplicationProtocols(new String[] {ALPN});
                        peer.setSSLParameters(p);
                        peer.startHandshake();
                        SSLSession session = peer.getSession();
                        return session.getProtocol() + "|"
                                + session.getCipherSuite() + "|"
                                + peer.getApplicationProtocol();
                    }
                });

                try (Socket tcp = new Socket(host, server.getLocalPort());
                     SSLSocket client = (SSLSocket)
                             clientContext.getSocketFactory().createSocket(
                                     tcp, "localhost", server.getLocalPort(), true)) {
                    client.setEnabledProtocols(new String[] {protocol});
                    client.setEnabledCipherSuites(new String[] {suite});
                    SSLParameters p = client.getSSLParameters();
                    p.setEndpointIdentificationAlgorithm("HTTPS");
                    p.setApplicationProtocols(new String[] {ALPN});
                    client.setSSLParameters(p);
                    client.startHandshake();
                    SSLSession session = client.getSession();
                    String clientState = session.getProtocol() + "|"
                            + session.getCipherSuite() + "|"
                            + client.getApplicationProtocol();
                    if (!clientState.equals(serverResult.get())) {
                        throw new IllegalStateException("TLS peers disagree");
                    }
                    return session.getProtocol() + " / "
                            + session.getCipherSuite() + " / ALPN="
                            + client.getApplicationProtocol();
                }
            } finally {
                pool.shutdownNow();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: keystore.p12 password");
        }
        char[] password = args[1].toCharArray();
        try {
            SSLContext[] both = contexts(Path.of(args[0]), password);
            System.out.println(runOne(both[0], both[1], "TLSv1.3",
                    "TLS_AES_128_GCM_SHA256"));
            System.out.println(runOne(both[0], both[1], "TLSv1.2",
                    "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"));
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }
}
~~~

### 10.3. Biên dịch và chạy

~~~bash
javac --release 17 -encoding UTF-8 TlsNegotiationDemo.java
java TlsNegotiationDemo loopback.p12 local-demo-pass
~~~

Với SunJSSE trên JDK 17 đã kiểm thử, chương trình in:

~~~text
TLSv1.3 / TLS_AES_128_GCM_SHA256 / ALPN=demo/1
TLSv1.2 / TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256 / ALPN=demo/1
~~~

**Quan sát chính:** cả hai handshake dùng **cùng certificate test RSA**, cùng lựa chọn ALPN demo/1 và có thể cùng AES-128-GCM ở tầng record. Nhưng tên suite **khác vì semantics khác giữa TLS 1.2 và TLS 1.3**. Ở TLS 1.2 tên suite nói rõ **ECDHE_RSA**; ở TLS 1.3 tên **TLS_AES_128_GCM_SHA256** không nói certificate RSA hay key exchange group.

### 10.4. Giải thích lifecycle của demo

1. **KeyStore PKCS12** được tạo từ keytool, có private key/certificate dùng cho server và trust anchor test cho client.
2. **KeyManagerFactory** đưa certificate/private key cho server SSLContext. **TrustManagerFactory** tạo trust manager chỉ chấp nhận những anchor trong keystore test, không vô hiệu xác minh.
3. ServerSocket chỉ bind **127.0.0.1** với random free port, không mở service ra Internet.
4. Trong mỗi lần **runOne**, phía server/client cùng bật một protocol và một suite đúng loại; đây là **bài test deterministic về lựa chọn**, không phải một production policy có đầy đủ fallback.
5. **SSLSocket.startHandshake()** hoàn tất TLS handshake. Sau đó **SSLSession.getProtocol**, **getCipherSuite** và **getApplicationProtocol** cho biết kết quả thực tế.
6. **SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")** buộc client kiểm tra hostname localhost so với certificate được tin cậy; dùng Socket TLS bọc TCP đã kết nối để peer host phục vụ hostname check là localhost.
7. **serverResult.get()** so sánh quan sát cả hai phía sau handshake. Không dùng handshake thành công như bằng chứng certificate public CA hợp lệ; trust được giới hạn trong file test.
8. **ExecutorService** chạy server accept ở một thread, client trong thread khác để tạo handshake loopback; session socket được đóng bằng try-with-resources.
9. **char[] password** tạm được xóa trong finally; tuy nhiên password ban đầu truyền qua **String args[1]**, nên chương trình minh họa không đạt chuẩn secret-handling production.

**Đừng dùng password test làm mật khẩu kho thật**, đừng đưa password production lên command line (có thể lộ trong process listing), và xóa/kiểm soát file test sau bài thực hành. Lấy secret từ môi trường bảo mật phù hợp như secret manager/callback khi làm ứng dụng thật.

### 10.5. Nếu demo không chạy trên máy khác

Nếu JDK/provider không hỗ trợ một protocol/suite, certificate không đúng, disabled algorithm chặn RSA key size, truststore không khớp, keytool không có trên PATH hoặc policy local không cho bind socket thì có thể nhận **SSLHandshakeException**, **NoSuchAlgorithmException**, **IllegalArgumentException** hoặc lỗi I/O phù hợp. Không tự giảm xuống TLS 1.0/1.1 hay bỏ certificate verification để “cho chạy”.

Dùng chương trình phần 9 để kiểm tra supported/default, sau đó xem chính sách **jdk.tls.disabledAlgorithms**, cấu hình provider, key material và certificate SAN. Nếu muốn bắt thêm trường hợp thương lượng TLS 1.3 với certificate ECDSA, cần tạo certificate EC test và cấu hình trust tương ứng; **tên suite TLS 1.3 có thể vẫn là TLS_AES_128_GCM_SHA256** khi profile/keys phù hợp.

## 11. Chọn cipher suite theo yêu cầu bảo mật và tương thích

Việc chọn suite cần dựa trên **phiên bản TLS + key management + security policy + loại client/server được hỗ trợ**; không chỉ dựa trên con số AES 128/256.

### 11.1. Khuyến nghị kiến trúc thực tế

1. **Ưu tiên TLS 1.3** khi tất cả các bên triển khai và đã kiểm thử; bộ suites phổ biến như AES-GCM hoặc ChaCha20-Poly1305 nên do security policy/provider xác định.
2. **Giữ TLS 1.2 khi có nhu cầu tương thích** với client được hỗ trợ, ưu tiên **ECDHE + AEAD** như TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256 hoặc ECDHE_ECDSA tương ứng certificate.
3. **Không bật lại TLS 1.0/1.1** hoặc RC4, 3DES, anonymous cipher suites chỉ để kết nối một client không được hỗ trợ; nâng cấp peer hoặc đưa ra lộ trình migration.
4. **Không dùng RSA key transport** khi thiết kế mới. Trong TLS 1.2, chọn ECDHE_RSA không đồng nghĩa đang dùng TLS_RSA; ECDHE tạo forward secrecy phù hợp.
5. **Cấu hình certificate và signature algorithms** tương thích với thực tế: RSA/ECDSA/EdDSA, certificate chain và trust manager; TLS 1.3 suite name không chọn hộ certificate.
6. **Không bật 0-RTT mặc định cho API có side effects** nếu không có chính sách replay/idempotency và đánh giá rủi ro.
7. **Không lạm dụng cipher-suite pinning**: cấu hình một tập được phê duyệt và kiểm thử với peer matrix, vì tập supported của nhà cung cấp có thể đổi khi nâng cấp JDK.
8. **Coi kết quả negotiated session là bằng chứng vận hành**, kết hợp certificate validation và authorization, không chỉ xem config.

### 11.2. Tại sao AES-256 không tự động tốt hơn toàn hệ thống?

**TLS_AES_256_GCM_SHA384** dùng AES-256-GCM và HKDF-SHA384; **TLS_AES_128_GCM_SHA256** dùng AES-128-GCM/HKDF-SHA256. Cả hai có mức bảo vệ mạnh trong profile TLS hiện đại. Một kết nối có AES-256 nhưng thiếu certificate validation, dùng PSK yếu, bị replay ở tầng nghiệp vụ hoặc lưu private key công khai **vẫn có thể không an toàn**.

Bên cạnh mức an toàn, cần xét hardware acceleration, latency, hỗ trợ client, policy chính thức và giới hạn provider. **Không được xếp hạng độ an toàn chỉ từ chuỗi cipher suite.**

## 12. Những lỗi thương lượng và cách chẩn đoán

| Triệu chứng | Kiểm tra trước tiên | Bẫy cần tránh |
| --- | --- | --- |
| **No appropriate protocol** | enabled versions, jdk.tls.disabledAlgorithms, peer versions | Không ép TLS 1.0 chỉ để loại bỏ lỗi. |
| **No cipher suites in common** | Suite enabled của hai phía, protocol tương ứng, type của certificate/key | Không cho rằng tên có AES-GCM là dùng được cho cả TLS 1.2/1.3. |
| **No available authentication scheme** | RSA/ECDSA/Ed25519 certificate, signature_algorithms, key managers | TLS 1.3 suite không nói loại certificate nên phải xem extension/key material. |
| **No appropriate key exchange group/share** | supported_groups, key_share, provider/curve policy, HelloRetryRequest | Đừng mặc định lỗi do record cipher. |
| **CertificateUnknown / PKIX path building failed** | trust anchors, certificate chain, hostname, validity và revocation policy | Không dùng trust-all TrustManager để vượt lỗi. |
| **ALPN negotiation error** | protocol ứng dụng hai bên cho phép, server preference, HTTP/2 policy | ALPN không phải cipher suite. |
| **Handshake thành công nhưng ứng dụng bị lỗi** | TLS session vs HTTP/2 policy, service authorization và protocol framing | Cipher suite đúng không chứng minh logic nghiệp vụ đúng. |
| **TLS 1.2 hoạt động, TLS 1.3 thất bại** | supported_versions, TLS 1.3 signature schemes/groups/certificates, provider support | Không so trực tiếp suite tên TLS 1.2 sang TLS 1.3. |
| **0-RTT request bị lặp** | early-data policy, replay/idempotency, state mutation | Không trông chờ signature/cipher suite tự chống replay tầng app. |

Trong Java, **SSLHandshakeException** mô tả lỗi chung; nguyên nhân gốc có thể là certificate, algorithm constraints, key exchange, ALPN, version hoặc IO. Ở môi trường kiểm thử, JSSE hỗ trợ **javax.net.debug** để quan sát handshake, ví dụ:

~~~bash
java -Djavax.net.debug=ssl,handshake TlsNegotiationDemo loopback.p12 local-demo-pass
~~~

Dòng trên chỉ minh họa cách bật log trên bài test. **Không chạy bằng mật khẩu production trên command line**, và không bật verbose handshake logs vô điều kiện trong production: log có thể chứa certificate metadata, hostnames và dữ liệu chẩn đoán nhạy cảm; phải giới hạn thời gian/đối tượng và bảo vệ log.

**Khi đọc log:**
- Xác nhận **client đã offer** TLS 1.3/TLS 1.2 và những cipher suite nào.
- Xác nhận **ServerHello chọn** suite phiên bản tương ứng.
- Với TLS 1.3, xem **supported_groups/key_share**, signature_algorithms/cert, PSK policy và ALPN **riêng**.
- Kiểm tra trust, hostname và certificate chain; khác biệt cipher suite chỉ là một phần của câu chuyện.
- Nếu peer bị downgrade hoặc không hỗ trợ TLS 1.3, kiểm tra cấu hình và policy; không chủ động tắt protections để vượt lỗi.

### 12.1. Downgrade protection và version negotiation

TLS 1.3 có **supported_versions** và cơ chế chống downgrade được đặc tả ở RFC 8446. Trường **legacy_version** và đôi khi các thông điệp compatibility **không nên được diễn giải thành việc TLS 1.2 đã được chọn** nếu supported_versions chỉ định TLS 1.3. Trong TLS 1.2 còn có các cơ chế liên quan fallback/renegotiation, ví dụ TLS_FALLBACK_SCSV; không dựa vào tự động fallback xuống phiên bản thấp để kết nối bằng mọi giá.

Lập cấu hình server/client theo **minimum TLS version được policy cho phép**, nâng cấp peer cũ thay vì giảm xuống giao thức không còn đạt yêu cầu. Không dùng packet capture hoặc các bytes legacy đơn lẻ để suy diễn version thành công; kết luận từ **handshake đã hoàn tất và SSLSession.getProtocol()**.

## 13. Bộ kiểm thử tương thích nên có

| Ca kiểm thử | Cấu hình | Kết quả mong đợi |
| --- | --- | --- |
| **TLS 1.3 happy path** | Hai phía bật TLSv1.3 + TLS_AES_128_GCM_SHA256, cert/trust hợp lệ | Handshake thành công, SSLSession trả TLSv1.3 và suite tương ứng. |
| **TLS 1.2 happy path** | Hai phía bật TLSv1.2 + ECDHE_RSA_WITH_AES_128_GCM_SHA256 | Handshake thành công với TLSv1.2 và suite tương ứng. |
| **Version mismatch** | Client chỉ TLSv1.3, server chỉ TLSv1.2 | Handshake thất bại; không âm thầm downgrade. |
| **Suite mismatch** | Không có overlap suite hợp lệ cho cùng protocol | Handshake thất bại. |
| **Certificate mismatch** | TLS 1.2 ECDHE_RSA nhưng server chỉ có ECDSA key không thể dùng RSA | Không chọn được profile đó, xử lý lỗi phù hợp. |
| **Wrong trust anchor** | Certificate self-signed không nằm trong client truststore | Từ chối; không bật trust-all. |
| **Hostname mismatch** | Certificate không có SAN của hostname được truy cập | Endpoint identification từ chối. |
| **TLS 1.3 group mismatch** | Không cùng nhóm DHE hợp lệ và không có PSK-only mode được phép | Handshake thất bại. |
| **Key share khác nhóm server thích** | Client hỗ trợ group nhưng chưa gửi share | HelloRetryRequest trong profile thích hợp hoặc failure có kiểm soát. |
| **ALPN mismatch** | Không có application protocol cùng hỗ trợ theo policy | Chọn protocol phù hợp hoặc thất bại; không suy ra cipher suite yếu. |
| **PSK-only** | TLS 1.3 cho phép psk_ke | Kiểm tra yêu cầu PFS và policy PSK riêng; không tuyên bố PFS dựa vào suite. |
| **0-RTT replay** | Gửi lại early-data request có side effect | Application phải có replay defense/idempotency hoặc từ chối 0-RTT. |
| **Provider upgrade** | Đổi JDK/security provider/HSM | Re-run supported/default/handshake tests theo suite được duyệt. |
| **Load/concurrency** | Nhiều kết nối đồng thời và chứng chỉ/ALPN khác nhau | Không trộn SSLSession, certificate/key usage, không vượt HSM capacity. |

Một test trong cùng JVM chỉ chứng minh **môi trường test**, không bảo đảm trình duyệt, mobile client, reverse proxy/CDN hoặc HSM production có cùng tập suite/extension. Cần **matrix có phiên bản JDK/client/proxy và nguồn key thực tế**.

## 14. Những hiểu lầm cần tránh

1. **TLS 1.3 cipher suite chứa loại certificate/key exchange như TLS 1.2.** Không: suite TLS 1.3 mô tả AEAD/HKDF hash.
2. **Tên có RSA nghĩa RSA key transport.** ECDHE_RSA dùng ephemeral ECDH và RSA authentication; khác TLS_RSA_.
3. **SHA256 trong tên GCM nghĩa record có HMAC-SHA256 độc lập.** Không: GCM đã có authentication tag; suffix trong TLS 1.2 GCM thường là PRF hash.
4. **TLS_AES_128_GCM_SHA256 dùng được nguyên tên trên TLS 1.2.** Sai phiên bản định nghĩa cipher suite.
5. **TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256 dùng nguyên tên trên TLS 1.3.** Tên này thuộc profile TLS 1.2.
6. **Supported nghĩa enabled và negotiated.** Supported chỉ là khả năng; cần kiểm tra cấu hình và SSLSession.
7. **Cứ setEnabledCipherSuites là server buộc chấp nhận.** Peer, certificate, group, signature và policy còn phải phù hợp.
8. **TLS 1.3 luôn có PFS trong mọi trường hợp.** PSK-only và 0-RTT có các giới hạn quan trọng.
9. **ALPN/SNI là phần của suite.** Chúng là extensions với mục đích khác.
10. **TLS 1.3 suite nói hash certificate signature là SHA256.** Cipher-suite hash dùng cho HKDF/transcript; handshake/certificate signatures được quy định riêng.
11. **Chỉ kiểm tra certificate chain là đủ.** Client còn cần hostname/identity, thời hạn, key usage, revocation policy và ứng dụng còn cần authorization.
12. **Bật TrustManager tin mọi cert để sửa handshake.** Việc này loại bỏ xác thực peer và tạo điều kiện MITM.
13. **Thấy legacy_version 0x0303 là kết luận TLS 1.2.** Trong TLS 1.3 đó có thể là giá trị tương thích; xem supported_versions và kết quả handshake.
14. **Bật TLSv1.2 là bắt buộc bật CBC/3DES.** Có thể cấu hình TLS 1.2 ECDHE+AEAD hiện đại mà không bật các suite yếu.
15. **Bật AES-256 tự giải quyết mọi rủi ro.** Còn key/cert trust, PSK, replay, implementation và application auth.
16. **Dùng một SSLSession.getCipherSuite để suy ra group, certificate type và ALPN.** Phải quan sát các thành phần khác riêng.
17. **Cho phép 0-RTT với API chuyển tiền mà không chống replay.** TLS không đảm bảo 0-RTT có anti-replay xuyên kết nối.
18. **Coi TLS encryption đồng nghĩa toàn bộ hệ thống đã an toàn.** TLS bảo vệ kênh, không thay kiểm soát người dùng và nghiệp vụ.

## 15. Câu hỏi tự kiểm tra

**1. TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256 có dùng RSA key transport không?**  
Không. **ECDHE** tạo shared secret qua ephemeral EC Diffie–Hellman; **RSA** dùng cho xác thực server/handshake theo suite.

**2. TLS_AES_128_GCM_SHA256 có chứng minh server dùng RSA certificate không?**  
Không. TLS 1.3 suite chỉ định **AES-128-GCM + SHA-256 HKDF/transcript**. Certificate/signature được chọn riêng.

**3. SHA256 trong TLS 1.2 AES-GCM suite có nghĩa mỗi record có HMAC-SHA256 không?**  
Không. Với suite GCM RFC 5289, SHA256 chỉ định **TLS PRF hash**; authentication tag đến từ AES-GCM.

**4. Java báo TLS_AES_128_GCM_SHA256 trong supported suites. Kết nối TLSv1.3 chắc chắn thành công không?**  
Không. Cần protocol enabled, overlap với peer, certificate/trust, group/key share, signature và policy tương thích.

**5. Tại sao TLS 1.3 có supported_groups và key_share riêng?**  
supported_groups công bố khả năng về nhóm key agreement; key_share chứa public shares đã gửi. Nếu thiếu key_share cho nhóm server muốn dùng, có thể có HelloRetryRequest.

**6. TLS 1.3 kết nối qua psk_ke có cùng PFS như ECDHE không?**  
Không. PSK-only không có ephemeral (EC)DHE; cần đánh giá khả năng forward secrecy theo mode thực tế.

**7. TLS 1.3 0-RTT có được tự do dùng cho giao dịch thanh toán không?**  
Không nên nếu thiếu replay protection và idempotency. Early data có rủi ro replay và bảo đảm bảo mật yếu hơn 1-RTT.

**8. Vì sao cần đọc SSLSession sau handshake?**  
Để biết **protocol và suite thực sự đã chọn**, khác danh sách supported/enabled ban đầu.

**9. ALPN h2 có phải một cipher suite hay một TLS version không?**  
Không. ALPN chọn protocol tầng ứng dụng, không phải suite/phiên bản TLS.

**10. TLS 1.2 với ECDHE_RSA và TLS 1.3 với AES_128_GCM_SHA256 có thể cùng dùng RSA certificate không?**  
Có, nếu server certificate và scheme ký đáp ứng profile/policy của cả hai; TLS 1.3 suite không ghi RSA trong tên.

## 16. Ghi nhớ và tài liệu chuẩn

**TLS 1.2:** cipher suite name thường thể hiện **key establishment/authentication + record cipher + MAC/PRF**, ví dụ **TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256**.  
**TLS 1.3:** cipher suite name chủ yếu thể hiện **AEAD + HKDF hash**, ví dụ **TLS_AES_128_GCM_SHA256**. **Group/key share, signature scheme/certificate, PSK mode và ALPN** được xử lý riêng, nên không thể đọc TLS 1.3 bằng cùng quy tắc tên TLS 1.2.

Trong Java 17+, **SSLContext và SSLParameters** dùng để khai báo chính sách; **SSLSocket/SSLEngine + SSLSession** giúp kiểm tra **kết quả thương lượng thực tế**. Cấu hình tốt cần bảo vệ **certificate validation, hostname, phiên bản cho phép, suite hiện đại, key exchange, replay và chính sách ứng dụng** cùng lúc.

Nguồn đọc chính thức:

- [RFC 5246 — TLS 1.2](https://www.rfc-editor.org/rfc/rfc5246)
- [RFC 5289 — ECC Cipher Suites for TLS 1.2](https://www.rfc-editor.org/rfc/rfc5289)
- [RFC 5288 — AES-GCM Cipher Suites for TLS](https://www.rfc-editor.org/rfc/rfc5288)
- [RFC 8446 — TLS 1.3](https://www.rfc-editor.org/rfc/rfc8446)
- [RFC 7301 — Application-Layer Protocol Negotiation](https://www.rfc-editor.org/rfc/rfc7301)
- [RFC 9150 — TLS 1.3 integrity-only cipher suites (ngoại lệ mở rộng)](https://www.rfc-editor.org/rfc/rfc9150)
- [Java SE 17 — JSSE Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-secure-socket-extension-jsse-reference-guide.html)
- [Java SE 17 — SSLContext API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLContext.html)
- [Java SE 17 — SSLParameters API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLParameters.html)
- [Java SE 17 — SSLSession API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLSession.html)
- [Java SE 17 — SSLSocket API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLSocket.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tls-foundation-005">15.5 Khóa tạm thời, forward secrecy và quan hệ với khóa dài hạn của chứng thư</a>

<details>
<summary>Xem chi tiết</summary>

# 15.5 Khóa tạm thời, forward secrecy và quan hệ với khóa dài hạn của chứng thư

## Mục tiêu học tập

Sau bài học này, bạn có thể:

1. Phân biệt **khóa định danh dài hạn gắn với chứng thư X.509**, **khóa riêng tạm thời (ephemeral)** dùng trong thỏa thuận khóa, **shared secret**, **traffic keys** và **session resumption PSK**.
2. Giải thích **forward secrecy (FS)** bảo vệ điều gì, trong mô hình kẻ tấn công nào và với điều kiện nào; không nhầm nó với bảo mật tuyệt đối hay xác thực danh tính.
3. So sánh **TLS 1.2 RSA key transport** với **TLS 1.2 (EC)DHE**, và hiểu tại sao **TLS 1.3 dùng trao đổi khóa tạm thời khi xác thực bằng chứng thư**, trong khi **PSK-only** và **0-RTT** là các trường hợp phải xem xét riêng.
4. Hiểu cách TLS ràng buộc phần trao đổi khóa tạm thời với **Certificate**, **CertificateVerify**, **Finished** và trust/hostname verification; giải thích vì sao **server có RSA certificate vẫn có thể có forward secrecy**.
5. Thực hành Java 17+ bằng **X25519 KeyAgreement** và **Ed25519 Signature** để minh họa shared secret tạm thời và chữ ký của khóa định danh, đồng thời nhận diện giới hạn: đây **không phải giao thức TLS tự xây**.
6. Kiểm tra TLS trong hệ thống Java/JSSE, tránh lựa chọn lỗi thời, quản lý vòng đời khóa và thiết kế phép thử khi khóa định danh bị lộ sau khi phiên kết thúc.

**Điểm then chốt:** **Chứng thư** giúp client xác minh **đối tác nào** đang tham gia (thông qua quyền sở hữu private key và chính sách tin cậy). **Khóa tạm thời** giúp tạo bí mật dùng cho **phiên/kết nối này** mà không cần suy ra từ private key dài hạn của chứng thư. Với một phiên (EC)DHE an toàn, khóa phiên đã được dẫn xuất và secret tạm thời đã được loại bỏ đúng cách, **kẻ đánh cắp private key định danh sau đó không thể chỉ dùng khóa ấy để giải mã bản ghi phiên cũ**.

Ngược lại, nếu hệ thống sử dụng kiểu trao đổi khóa không có forward secrecy, khóa dài hạn bị lộ có thể kéo theo nhiều bản ghi mạng được lưu từ trước. **Chữ ký số và thỏa thuận khóa là hai chức năng khác nhau; không thay thế nhau.**

## 1. Mô hình đe dọa: “hôm nay ghi lại, ngày mai lấy khóa”

Giả sử Alice dùng HTTPS truy cập máy chủ Bob.

- Ở thời điểm **T1**, Eve ghi lại một bản sao đầy đủ những packet TLS trên mạng nhưng chưa có quyền vào server.
- Tại **T2** (sau khi phiên TLS cũ đã kết thúc), Eve đánh cắp **private key dài hạn** của Bob, ví dụ file PKCS12, key backup hoặc quyền dùng một khóa định danh.
- Eve thử giải mã những packet đã ghi ở T1.

Câu hỏi của forward secrecy là:

> Khi Eve lấy được **bí mật dài hạn ở T2**, liệu cô ấy có thể giải mã **traffic của phiên trước ở T1** chỉ từ bản ghi mạng và khóa vừa đánh cắp hay không?

Trong một thiết kế có FS dựa trên **fresh (EC)DHE** và xóa keying material không còn dùng, câu trả lời kỳ vọng là **không** đối với riêng sự lộ **private key định danh dài hạn**. Traffic keys của phiên cũ được dẫn xuất qua một shared secret mà Eve không thể tính chỉ từ public shares đã quan sát và private key định danh lấy được sau này.

Sơ đồ thời gian:

~~~text
T1: Handshake dùng fresh ephemeral (EC)DHE
    Alice <---------------------------------> Bob
      |                                         |
   Ephemeral private a                     Ephemeral private b
      |                                         |
   Shared secret Z ---------------------- Shared secret Z
      |                                         |
   Derive traffic keys -> truyền dữ liệu mã hóa
      |
    Eve ghi lại ClientHello, ServerHello, certificate,
    key shares, handshake và application ciphertext.

T1 kết thúc:
    a, b và các secret không còn cần được xóa/giải phóng
    theo khả năng và chính sách của implementation.

T2: Eve có private key định danh dài hạn của Bob.
    Nếu chỉ có khóa này và bản ghi T1:
       không suy ra được Z hay traffic keys của T1
       trong mô hình (EC)DHE an toàn.
~~~

**FS không nói rằng dữ liệu luôn an toàn nếu Bob bị chiếm quyền từ T1.** Nếu Eve đã chiếm server khi phiên đang chạy, lấy được session secrets, plaintext trong RAM, key logs, hoặc kiểm soát ứng dụng, cô ấy có thể đọc dữ liệu dù handshake có (EC)DHE.

### 1.1 Điều được bảo vệ và không được bảo vệ

| Câu hỏi | Một phiên (EC)DHE có FS trả lời thế nào? |
| --- | --- |
| Sau phiên, private key định danh của server bị đánh cắp. Bản ghi traffic cũ có giải mã được bằng khóa đó không? | **Không chỉ từ khóa định danh**, nếu trao đổi khóa/thuật toán an toàn và ephemeral/session secrets không còn bị lộ |
| Client chưa xác thực certificate, liệu (EC)DHE có tự chống man-in-the-middle không? | **Không**; DH không xác thực có thể bị chèn khóa trung gian |
| Attacker lấy traffic key hoặc plaintext khi phiên đang sống? | **FS không cứu được dữ liệu đã bị lộ trong thời gian đó** |
| Attacker có private key định danh trước một handshake mới? | **Có thể giả mạo server trong tương lai**, trừ khi trust/thu hồi/khóa mới và các kiểm soát khác ngăn chặn |
| Server dùng lại khóa tạm thời rất lâu hoặc log keying material? | Bảo đảm FS có thể bị suy giảm hoặc mất |
| Ciphertext từ TLS cũ được lưu ở proxy/DB dưới dạng plaintext? | FS của kết nối mạng không bảo vệ các bản sao plaintext đó |
| Kết nối dùng PSK-only hoặc dữ liệu TLS 1.3 0-RTT? | **Không tự có bảo đảm FS giống full (EC)DHE**; cần xét PSK lifecycle và mode cụ thể |
| Một công nghệ tính toán mới phá (EC)DH hiện tại trong tương lai? | FS cổ điển **không tự bảo vệ** trước việc giải bài toán DH từ public shares bằng năng lực mới |

### 1.2 Forward secrecy và “Perfect Forward Secrecy” có khác không?

Trong tài liệu cũ và cấu hình hệ thống, thuật ngữ **Perfect Forward Secrecy (PFS)** thường được dùng cho bảo đảm này. Tài liệu tiêu chuẩn mới thường gọi là **forward secrecy**. Chữ “perfect” không nên bị diễn giải thành “không có ngoại lệ”. Bảo đảm mật mã luôn phụ thuộc giả định về thuật toán, cách sinh/xóa secret, mode handshake và mô hình kẻ tấn công.

**Định nghĩa thiết thực:** thỏa hiệp một số khóa dài hạn trong tương lai **không tiết lộ các phiên đã hoàn thành trước đó** dưới mô hình an toàn đã quy định.

## 2. Phân loại khóa trong TLS — đừng gọi tất cả là “SSL key”

| Thành phần | Vòng đời | Ai giữ? | Công việc chính |
| --- | --- | --- | --- |
| **Private key định danh dài hạn** (RSA/ECDSA/EdDSA tùy TLS/provider) | Nhiều phiên, gắn với certificate/identity và rotation | Server hoặc HSM; mTLS có thể cả client | Chứng minh quyền giữ khóa định danh, ký dữ liệu handshake phù hợp giao thức |
| **Public key trong X.509 certificate** | Công khai, có validity và chuỗi chứng nhận | Peer có thể đọc | Giúp xác minh chữ ký và ràng buộc identity sau kiểm tra PKI |
| **Ephemeral private key** của (EC)DHE | Tạo cho một handshake hoặc giới hạn thời gian theo giao thức/policy | Mỗi peer giữ riêng | Cùng public share đối tác tạo **shared secret** |
| **Ephemeral public share** | Truyền trong handshake; không bí mật | Hai peer và người nghe lén có thể thấy | Đầu vào công khai để tính shared secret bằng private share |
| **(EC)DHE shared secret** | Trung gian, ngắn hạn | Hai peer sau thỏa thuận | Đầu vào key schedule/KDF; không phải raw traffic key để dùng trực tiếp |
| **Handshake traffic secrets/keys** | Trong lúc hoàn thành handshake | Hai peer theo chiều gửi/nhận | Bảo vệ các handshake record cần mã hóa |
| **Application traffic secrets/keys** | Trong kết nối, có thể cập nhật | Hai peer theo chiều gửi/nhận | Mã hóa và xác thực application records |
| **PSK/resumption secret/session ticket keys** | Có thể tồn tại qua nhiều kết nối | Client/server/ticket infrastructure | Xác thực hoặc nối lại phiên; rủi ro FS phụ thuộc cách dùng/giữ/xóa |
| **CA signing private key** | Dài hạn ở CA, không nằm trong TLS handshake | CA/KMS/HSM | Ký chứng thư, **không** phải key tạo traffic của phiên |

**Cặp khóa của chứng thư** và **cặp khóa tạm thời** là **hai cặp khác nhau** về chức năng. Cùng được gọi “public/private key” nhưng vòng đời và mục đích rất khác.

### 2.1 Chứng thư không chứa private key

Một chứng thư X.509 chứa public key cùng subject, issuer, validity, SAN, KU/EKU, signature và các extension. **Private key không nằm trong certificate**. Private key có thể được cất trong PKCS12, HSM, OS keystore hoặc KMS. Khi nói “private key của chứng thư”, đó là cách gọi khóa riêng **tương ứng public key được chứng thư ràng buộc**, không phải một trường bí mật bên trong chứng thư.

### 2.2 Server certificate có thể là RSA, handshake vẫn có ECDHE

Điều này thường bị nhầm. Trong TLS 1.2 cipher suite **TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256**:

- **ECDHE**: phương pháp thỏa thuận khóa tạm thời.
- **RSA**: phương pháp xác thực/chữ ký bằng khóa định danh RSA của server.
- **AES_128_GCM**: cơ chế bảo vệ application records.
- **SHA256**: thành phần PRF/hash theo đặc tả của cipher suite.

Chữ **RSA trong tên ECDHE_RSA** không có nghĩa TLS đã mã hóa premaster secret bằng public key RSA của certificate. RSA được dùng cho **chữ ký xác thực**, còn shared secret đến từ **ECDHE**.

Tương tự, **TLS_ECDHE_ECDSA_...** dùng ECDHE để trao đổi khóa và ECDSA để xác thực. Một certificate có key type RSA hoặc ECDSA không tự quyết định FS; phải xem **handshake/key exchange đã đàm phán thực sự**.

## 3. Cơ chế (EC)DHE tạm thời tạo shared secret thế nào?

**Diffie-Hellman (DH)** cho phép hai phía tính được cùng một shared secret bằng private value của mình và public value của đối tác, mà không gửi shared secret trực tiếp qua mạng. “E” trong DHE/ECDHE là **ephemeral**, nghĩa là các giá trị riêng cho trao đổi khóa được tạo mới và có vòng đời ngắn.

Sơ đồ khái niệm:

~~~text
Client Alice                                     Server Bob

Tạo ephemeral private a                         Tạo ephemeral private b
Tạo public share A                              Tạo public share B
       |                                               |
       +---------------- A -------------------------->+
       +<--------------- B ---------------------------+
       |                                               |
Z = DH(a, B)                                     Z = DH(b, A)
       |                                               |
       +--------------- cùng Z ------------------------+
                             |
                 KDF/HKDF + handshake context
                             |
                 Traffic secrets, keys, IVs
                             |
             Bảo vệ dữ liệu qua AEAD record layer
~~~

**Không truyền a, b hoặc Z** trong handshake. Public shares A, B có thể bị ghi lại nhưng không đủ để tính Z theo giả định khó của (EC)DH cổ điển.

Với **ECDHE**, phép toán diễn ra trên elliptic curve. Ví dụ nhóm hiện đại có thể là **X25519** hoặc **secp256r1** khi hai phía và provider hỗ trợ. Với finite-field DHE, dùng các nhóm DH đã được tiêu chuẩn hóa và đủ mạnh; **không tự sinh tham số DH yếu hoặc chọn nhóm không được tin cậy**.

### 3.1 Tại sao phải gắn chữ “ephemeral”?

Nếu dùng một private DH value cố định qua nhiều phiên, rò rỉ value đó có thể ảnh hưởng nhiều session và làm tăng tác động của compromise. Với fresh ephemeral keypair cho từng handshake và xóa đúng lúc, một private share đã kết thúc không còn sẵn để attacker thu thập ở T2.

**Không nên hiểu “ephemeral” chỉ là đặt tên biến tempKey**: phải thực sự dùng key material được sinh an toàn theo policy, không tái sử dụng không cần thiết, không ghi vào log/key dump, không persist trái ý muốn và giải phóng khi đã kết thúc.

Các chi tiết cụ thể như khả năng pre-generate key shares, pool trong implementation, session resumption, KDF và thời điểm xóa phụ thuộc giao thức/runtime. Không tự viết một bộ thỏa thuận khóa qua socket rồi gọi nó là TLS chỉ vì có DH.

### 3.2 Shared secret không phải traffic key trực tiếp

Một **KeyAgreement.generateSecret()** có thể trả mảng byte shared secret. Trong TLS 1.3, secret này được đưa vào **key schedule sử dụng HKDF** và handshake transcript để dẫn xuất nhiều secret/khóa theo ngữ cảnh:

~~~text
(EC)DHE shared secret (+ PSK nếu handshake có PSK)
                         |
                   TLS 1.3 key schedule
              HKDF-Extract / HKDF-Expand-Label
                         |
      +------------------+------------------+
      |                                     |
Handshake traffic secrets           Application traffic secrets
      |                                     |
  handshake keys                      traffic keys + IVs
                                      theo chiều gửi/nhận
~~~

**Không dùng raw DH shared secret làm AES key ngay** hoặc tự tạo key/IV bằng cách cắt byte tùy ý. Key schedule xử lý phân tách miền, handshake context, hướng gửi/nhận, cập nhật và các bảo đảm khác; implementation TLS phải lo việc đó theo chuẩn.

### 3.3 Diffie-Hellman không xác thực thì gặp MITM

Nếu Alice chỉ chấp nhận bất kỳ public share B nào nhận từ mạng, Eve có thể thay nó bằng public share của Eve, thực hiện một trao đổi khóa riêng với Alice và một trao đổi khóa khác với Bob. Hai phía có khóa, nhưng thực ra **mỗi phía trao đổi với Eve**.

~~~text
Alice                 Eve (MITM)                 Bob
  |  public share A       |                       |
  +---------------------> |                       |
  |<------ public share E1|                       |
  |                       | --- public share E2 ->|
  |                       |<--- public share B ---|
  |                       |                       |
Shared secret Alice-Eve        Shared secret Eve-Bob
~~~

**Certificate + handshake authentication** là phần chống tấn công này: client phải kiểm tra chain/trust, hostname và bằng chứng server giữ private key định danh; chữ ký xác thực phải ràng buộc với **handshake transcript gồm phần trao đổi khóa**, không chỉ một public share tùy ý.

## 4. TLS 1.2: RSA key transport khác gì ECDHE_RSA?

### 4.1 TLS 1.2 với static RSA key exchange — không có FS

Trong kiểu **TLS_RSA_WITH_AES_...** trước đây, client tạo **premaster secret**, sau đó mã hóa nó bằng **public key RSA dài hạn trong server certificate** và gửi tới server. Server dùng private key RSA dài hạn để giải mã premaster secret và cùng client dẫn xuất session keys.

~~~text
TLS 1.2 static RSA key transport:

Client:
   Tạo premaster_secret
   RSA_encrypt(server_certificate_public_key, premaster_secret)
                      |
                 network packet
                      |
Server:
   RSA_decrypt(long_term_server_private_key, encrypted_premaster)
   -> premaster_secret -> traffic keys
~~~

Nếu Eve ghi lại encrypted premaster secret và bản ghi application traffic tại T1, sau đó đánh cắp RSA private key dài hạn tại T2, Eve **có thể giải mã premaster secret đã ghi và tính lại keying material phiên cũ** (trong điều kiện có đủ bản ghi và không có bảo vệ khác). Vì vậy cơ chế này **không có forward secrecy**.

Đó là vấn đề cấu trúc, không phải vì RSA “luôn kém an toàn”. RSA còn có thể đóng vai trò **chữ ký định danh** trong handshake ECDHE_RSA có FS.

**Lưu ý trạng thái tiêu chuẩn:** RFC 10015 (2026) đã **deprecated** cơ chế RSA key exchange và một số cơ chế lỗi thời trong (D)TLS 1.2. TLS 1.3 không có static RSA key transport. Tài liệu Java cũ vẫn có thể liệt kê một số cipher suite legacy vì lý do compatibility, nhưng việc nhận ra tên suite cũ không có nghĩa nên bật nó trong cấu hình hiện đại.

### 4.2 TLS 1.2 với ECDHE_RSA — có thể có FS

Trong **TLS_ECDHE_RSA_...**:

1. Client và server trao đổi ephemeral ECDHE shares.
2. Server chứng minh quyền giữ private key định danh RSA bằng chữ ký trên tham số handshake theo giao thức TLS 1.2 (điển hình **ServerKeyExchange**).
3. Client xác thực chuỗi chứng thư/hostname và chữ ký.
4. Cả hai dẫn xuất premaster/shared secret qua ECDHE, không cần giải mã ciphertext của premaster bằng khóa RSA dài hạn.
5. Khi ephemeral secrets biến mất và các giả định an toàn thỏa mãn, lấy RSA identity private key **sau phiên** không đủ để giải mã traffic cũ.

~~~text
TLS 1.2 ECDHE_RSA:

RSA certificate/private key dài hạn:
    xác thực/chữ ký thông điệp ECDHE trong handshake

Client ephemeral a, server ephemeral b:
    ECDHE(a, B) == ECDHE(b, A) -> premaster/master/traffic keys

Lộ RSA private key sau phiên:
    có thể giả mạo identity về sau,
    nhưng không tự cung cấp ephemeral shared secret đã dùng.
~~~

### 4.3 Bảng phân biệt TLS 1.2

| Cipher suite / cơ chế | Key exchange | Vai trò certificate/key dài hạn | FS trước việc lộ certificate private key sau phiên? |
| --- | --- | --- | --- |
| **TLS_RSA_WITH_AES_128_GCM_SHA256** | RSA key transport | RSA private key giải mã premaster secret | **Không** |
| **TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256** | ECDHE tạm thời | RSA ký/xác thực handshake | **Có, với điều kiện của ECDHE và vận hành đúng** |
| **TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256** | ECDHE tạm thời | ECDSA ký/xác thực handshake | **Có, với điều kiện tương ứng** |
| **DHE_RSA** (biến thể phù hợp) | Finite-field DHE tạm thời | RSA xác thực | **Có**, nếu dùng nhóm mạnh, ephemeral đúng |
| **DH/ECDH static** | Khóa DH dài hạn/tĩnh | Có thể liên quan identity/certificate | Không tự cung cấp FS như trao đổi ephemeral |

Tên suite quan trọng trong **TLS 1.2** vì nó chứa cơ chế key exchange/auth. Đừng áp dụng cùng cách đọc tên suite sang TLS 1.3.

## 5. TLS 1.3: trao đổi khóa tạm thời và xác thực theo chứng thư

Trong **TLS 1.3 full handshake dựa trên chứng thư**, trao đổi khóa và xác thực identity được thiết kế **tách chức năng rõ ràng**:

- **ClientHello / ServerHello, key_share:** trao đổi giá trị công khai của (EC)DHE.
- **Certificate:** server công bố chuỗi chứng thư xác thực identity.
- **CertificateVerify:** server chứng minh quyền giữ private key định danh bằng **chữ ký số ràng buộc với handshake transcript và ngữ cảnh TLS**.
- **Finished:** MAC xác nhận handshake transcript và quyền sở hữu handshake secret thích hợp; phát hiện sửa đổi handshake theo thiết kế.
- **HKDF key schedule:** dẫn xuất các secret/traffic keys từ (EC)DHE secret và ngữ cảnh handshake, tách theo giai đoạn/hướng.

Sơ đồ giản lược:

~~~text
Client                                             Server
  |                                                  |
  | ClientHello + supported_groups + key_share A    |
  +------------------------------------------------->|
  |                                                  |
  |       ServerHello + selected key_share B         |
  |<-------------------------------------------------+
  |                                                  |
  |      [Hai phía đã có Z = (EC)DHE(a,B)/(b,A)]     |
  |                                                  |
  |            EncryptedExtensions                   |
  |            Certificate (server identity)         |
  |            CertificateVerify (signature)        |
  |            Finished (handshake MAC)              |
  |<-------------------------------------------------+
  |                                                  |
  |     Validate certificate chain + hostname       |
  |     Verify CertificateVerify + Finished          |
  |                                                  |
  | Finished                                        |
  +------------------------------------------------->|
  |                                                  |
  |<======== encrypted application data ============>|
~~~

Đây là **sơ đồ mô tả**, không phải bản sao toàn bộ message-level TLS 1.3. Tùy handshake, có thể có CertificateRequest, client Certificate/CertificateVerify khi mTLS, HelloRetryRequest, resumption/PSK và các extension khác.

**Điều quan trọng:** Trong full certificate handshake, **private key của chứng thư dùng để xác thực**, không dùng làm bí mật tĩnh để giải mã shared secret (EC)DHE. Certificate không “chứa khóa phiên”, cũng không “gửi khóa phiên được mã hóa” theo cách RSA key transport TLS 1.2.

### 5.1 CertificateVerify ràng buộc điều gì?

Trong TLS 1.3, signature của CertificateVerify được tính theo định dạng có **context string** và **hash của handshake transcript** theo chuẩn, bằng thuật toán chữ ký đã thỏa thuận. Nó **không chỉ là ký chuỗi "hello"** hoặc ký mỗi key share đơn lẻ.

Việc ràng buộc transcript giúp client phát hiện kẻ giữa đường tráo các thành phần handshake, nếu signature và PKI validation được thực hiện đúng. Chữ ký xác thực rằng peer giữ private key định danh phù hợp với certificate đã được chấp nhận, còn (EC)DHE tạo secret tạm thời.

**Chữ ký không tự xác minh SAN/hostname, trust chain, thời hạn, EKU và policy.** Những kiểm tra này là trách nhiệm của certificate/path/endpoint identity validation trong TLS stack.

### 5.2 Finished là bước gì?

**Finished** xác nhận hai bên đã đi tới keying material phù hợp và ràng buộc **nội dung handshake** bằng MAC có khóa. Nếu handshake bị sửa mà không có secret đúng, Finished không xác minh thành công.

Không nên rút gọn “TLS an toàn vì có CertificateVerify”. An toàn handshake đến từ **nhiều phép kiểm tra liên kết**: version/cipher suite selection, key shares, transcript signatures, Finished, certificate validation và tính toàn vẹn record layer.

### 5.3 Certificate RSA không đồng nghĩa RSA key transport

Trong TLS 1.3, server có thể dùng RSA certificate để **ký handshake** với signature algorithm phù hợp; điều đó **không phục hồi static RSA key transport**. Một TLS 1.3 cipher suite như:

~~~text
TLS_AES_128_GCM_SHA256
~~~

nêu **AEAD cipher và hash** của bộ mã, **không mã hóa tên key exchange hoặc phương thức chữ ký** trong chính tên suite. Nhìn SSLSession.getCipherSuite() chỉ ra chuỗi trên không đủ để biết group X25519/secp256r1 hay chứng thư RSA/ECDSA mà peer đã dùng.

Với TLS 1.3, cần xem **protocol version, kiểu handshake (full/PSK), key_share/PSK mode, certificate/signature và JSSE handshake diagnostics** nếu muốn xác nhận chi tiết FS.

### 5.4 TLS 1.3 có luôn forward secrecy không?

Không được trả lời một từ “luôn” mà bỏ qua **PSK-only và 0-RTT**.

Theo **RFC 9846 (tháng 7/2026)**, tiêu chuẩn TLS 1.3 hiện hành thay thế RFC 8446, giữ các nguyên tắc quan trọng:

- Khi **public-key handshake dùng fresh (EC)DHE**, thiết kế cung cấp FS theo mô hình mật mã, nếu secret tạm thời/traffic secret được quản lý đúng.
- TLS 1.3 hỗ trợ **PSK-only (psk_ke)**; mode này **không mặc nhiên cung cấp FS trước việc lộ PSK được giữ lại**, vì không có fresh DH share đóng góp vào shared secret.
- TLS 1.3 hỗ trợ **PSK + (EC)DHE (psk_dhe_ke)**; fresh DH giúp FS cho traffic 1-RTT được dẫn xuất theo mode này.
- **0-RTT early data** dựa trên PSK và **không có bảo đảm forward secrecy đầy đủ như 1-RTT DHE**; đồng thời có vấn đề **replay giữa các kết nối**.
- Với một số cách quản lý PSK và session ticket **một lần rồi xóa mọi bản sao**, phạm vi compromise có thể hẹp hơn; đó là **thuộc tính vận hành phụ thuộc thời điểm xóa PSK**, không phải lời hứa “PSK-only luôn bằng DHE”.

Nguồn cập nhật: [RFC 9846 — The Transport Layer Security (TLS) Protocol Version 1.3](https://www.rfc-editor.org/rfc/rfc9846.html), đặc biệt §2.2–2.3 và Appendix F.

## 6. Resumption, PSK, 0-RTT và những ngoại lệ dễ bỏ sót

### 6.1 PSK là gì?

**Pre-Shared Key (PSK)** là bí mật mà hai phía đã có trước handshake (do cấp ngoài hoặc được dẫn xuất từ một kết nối trước). Trong TLS 1.3, session resumption có thể dùng resumption PSK và ticket để thiết lập kết nối mới nhanh hơn.

**PSK không phải private key X.509.** Một server có thể sử dụng certificate trong full handshake ban đầu, nhưng một resumption handshake theo PSK có thể không gửi lại certificate tương tự. Phải hiểu TLS state/session và chính sách trust của ứng dụng.

### 6.2 So sánh psk_ke và psk_dhe_ke

~~~text
TLS 1.3 psk_ke:
    PSK ----------------> key schedule -> traffic keys
    (Không có fresh key_share đóng góp)
    => Nếu PSK được giữ và lộ sau này, không có bảo đảm FS
       tương tự trao đổi fresh (EC)DHE.

TLS 1.3 psk_dhe_ke:
    PSK ------------------+
                          +--> key schedule -> traffic keys
    Fresh (EC)DHE Z ------+
    => FS của traffic 1-RTT khi bảo vệ/xóa ephemeral đúng,
       dưới các giả định của giao thức.
~~~

**Không được tự suy luận rằng tất cả các session HTTPS dùng PSK đều mất FS**: cần kiểm tra mode, thời điểm PSK bị lộ và lifecycle. Ngược lại, nhìn thấy “TLS 1.3” cũng không tự chứng minh handshake chọn fresh DHE trong mọi trường hợp.

### 6.3 0-RTT early data

Khi client có PSK từ lần kết nối trước, TLS 1.3 có thể cho phép gửi một số application data trong **first flight** của handshake mới. Điều này giảm độ trễ nhưng đánh đổi thuộc tính bảo mật:

- **Không có bảo đảm FS đầy đủ cho early data**; khóa early data được dẫn xuất từ PSK trước khi nhận fresh ServerHello key share.
- **Có nguy cơ replay** giữa nhiều kết nối; attacker có thể thử gửi lại early data đã ghi.
- Các thao tác có side effect, như **chuyển tiền, mua hàng, tạo đơn, thay đổi mật khẩu**, không nên mặc định cho phép trong 0-RTT; cần profile ứng dụng rõ ràng và biện pháp chống replay/idempotency phù hợp.
- **1-RTT application data sau handshake** có thể nhận tính bảo vệ khác khi dùng PSK+(EC)DHE; không nên gộp tất cả traffic TLS 1.3 thành một câu “forward secret” duy nhất.

RFC 9846 yêu cầu ứng dụng không bật 0-RTT nếu không có chủ đích, và không gửi early data không an toàn khi bị replay. Đối với Java/JSSE, hỗ trợ thực tế cho 0-RTT phụ thuộc bản JDK/provider/API; không giả định JSSE Java 17 phổ biến luôn bật 0-RTT.

### 6.4 Session ticket encryption key và vòng đời PSK

Một server có thể phát hành session ticket để resumption. Nếu ticket được mã hóa/xác thực bằng một **server ticket protection key** tồn tại lâu, hoặc resumption PSK được lưu trong cache/DB quá lâu, compromise hạ tầng ticket có thể ảnh hưởng các kết nối nối lại.

Kiểm soát cần có:

- Thời hạn session ticket/resumption PSK thích hợp.
- Rotation và hạn chế quyền truy cập ticket keys.
- Xóa key/PSK không còn cần; không log session secrets.
- Quy tắc single-use hoặc anti-replay nếu ứng dụng cho phép early data.
- Phân biệt full handshake, resumption psk_ke, resumption psk_dhe_ke.
- Không thiết kế “forward secrecy” chỉ dựa trên private key certificate mà quên nơi lưu PSK/session tickets.

## 7. Tấn công và giới hạn: hiểu đúng lời hứa của FS

### 7.1 Lộ private key chứng thư sau khi phiên cũ kết thúc

**Điều có thể xảy ra:**

- TLS 1.2 RSA key transport: attacker có thể giải mã premaster secret đã bị ghi trong handshake; rủi ro giải mã traffic cũ.
- TLS 1.2 ECDHE và TLS 1.3 full handshake (EC)DHE: khóa định danh không đủ để tính shared secret cũ; traffic cũ có thể được bảo vệ, miễn secret tạm thời không bị lộ.
- Trong các handshake mới, attacker đang giữ identity private key **có thể giả mạo server**, vì signature/authentication bị compromise. FS **không tự ngăn** việc giả mạo tương lai.

**Việc cần làm khi mất private key định danh:** thu hồi/rotation certificate và khóa, khóa quyền truy cập, điều tra sự cố, cập nhật trust/deployment và phạm vi ảnh hưởng. Bản ghi cũ có FS không phải lý do bỏ qua incident response.

### 7.2 Attacker có traffic secrets hoặc endpoint memory

FS **không** có khả năng xóa ảnh hưởng của dữ liệu từng được attacker đọc. Nếu attacker thu thập:

- Plaintext ở reverse proxy, application logs, traces, heap dump.
- Ephemeral private key trong lúc phiên đang hoạt động.
- (EC)DHE shared secret/handshake traffic secret.
- Application traffic keys hoặc key log export (ví dụ key log files dùng cho packet analysis).
- Một session ticket/PSK vẫn có giá trị dưới mode resumption phù hợp.

thì một số phiên hoặc dữ liệu có thể bị giải mã/giả mạo. Bảo vệ endpoint, memory dump, debug settings và secret storage vẫn quan trọng **ngay cả khi dùng TLS 1.3**.

### 7.3 KeyUpdate không phải phép trao đổi DH mới

TLS 1.3 có cơ chế **KeyUpdate** để dẫn xuất generation traffic keys mới từ secret hiện có. Điều này hữu ích cho giới hạn sử dụng khóa và bảo vệ một số thế hệ cũ nếu key cũ đã được xóa.

Nhưng **KeyUpdate không tự chạy lại (EC)DHE**. Nếu attacker đã nắm **traffic secret hiện tại**, việc chỉ dẫn xuất thế hệ mới từ secret đó **không tự tạo post-compromise security cho tương lai**. Đừng nhầm FS trước compromise với khả năng “tự hồi phục” sau khi attacker kiểm soát session secret.

Theo phân tích an toàn của RFC 9846, để giảm tác hại của một số loại compromise cho kết nối mới cần **trao đổi khóa bất đối xứng mới**, không chỉ đổi tên key.

### 7.4 Forward secrecy không phải post-quantum security

Một attacker có thể ghi lại public DH shares và ciphertext hôm nay. Nếu sau này có năng lực giải bài toán DH/ECDH hiện tại từ public shares (ví dụ năng lực lượng tử đủ mạnh theo mô hình Shor), việc private key định danh đã bị xóa **không còn là vấn đề quyết định**. Vì vậy **FS cổ điển** không đồng nghĩa an toàn trước “harvest now, decrypt later” trong mô hình lượng tử.

Giảm rủi ro HNDL trong tương lai cần đánh giá các giao thức/hybrid post-quantum key establishment đã được chuẩn hóa và triển khai phù hợp, không tự nối X25519 với một thuật toán bất kỳ trong code ứng dụng. **X25519 trong ví dụ Java của bài này là mô hình cổ điển**, không đại diện một bảo đảm post-quantum.

### 7.5 Ứng dụng lưu lại plaintext vẫn có thể làm mất ý nghĩa bảo vệ

Một TLS terminator/reverse proxy có thể giải mã traffic, sau đó chuyển plaintext vào service nội bộ hoặc lưu xuống log/database. FS chỉ có ý nghĩa cho **traffic TLS được mã hóa theo phiên**, không tự bảo vệ plaintext đã tồn tại ngoài kết nối đó.

Kiểm tra cả đường đi của dữ liệu: client → CDN/LB/proxy → app → DB/queue/log, và từng ranh giới trust. Một hệ thống nói “TLS có PFS” nhưng lưu access tokens/raw payload vào log công khai vẫn có vấn đề bảo mật nghiêm trọng.

## 8. Java 17+: mô phỏng hai vai trò, không tự xây TLS

Java có JCA/JCE cho phép học primitives bên dưới:

- **KeyPairGenerator.getInstance("X25519")** sinh một cặp khóa X25519 tạm thời.
- **KeyAgreement.getInstance("X25519")** cho hai phía tạo shared secret trùng nhau.
- **KeyPairGenerator.getInstance("Ed25519")** sinh một cặp khóa định danh mẫu.
- **Signature.getInstance("Ed25519")** ký/xác minh một transcript minh họa.

Trong ví dụ, server giữ một **khóa định danh dài hạn Ed25519**, client biết trước **public key định danh đã tin**. Hai phía sinh fresh X25519 cho từng handshake và tạo secret riêng. Server ký một transcript có chứa **cả hai public ephemeral shares**, client xác thực chữ ký bằng public key định danh đã biết.

**Ranh giới cực kỳ quan trọng:** Đây là **mô hình giáo dục**, **không phải TLS 1.3 implementation**. Trong TLS thật, CertificateVerify có định dạng ngữ cảnh và transcript do RFC quy định; cần certificate/path/hostname validation, Finished MAC, HKDF key schedule, cipher suite negotiation, AEAD record framing, replay/downgrade protections, error handling và nhiều kiểm soát khác. **Không gửi hai public shares + chữ ký dưới định dạng demo này lên mạng sản xuất**.

### 8.1 Chương trình Java 17+ hoàn chỉnh

Tạo file **EphemeralIdentityDemo.java** (UTF-8):

~~~java
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import javax.crypto.KeyAgreement;

public class EphemeralIdentityDemo {
    // Transcript mô phỏng có domain separation và độ dài từng trường.
    // Không phải Transcript/CertificateVerify format của TLS 1.3.
    private static byte[] transcript(PublicKey client, PublicKey server) {
        byte[] ctx = "educational-ephemeral-v1"
                .getBytes(StandardCharsets.UTF_8);
        byte[] c = client.getEncoded();
        byte[] s = server.getEncoded();

        return ByteBuffer.allocate(12 + ctx.length + c.length + s.length)
                .putInt(ctx.length).put(ctx)
                .putInt(c.length).put(c)
                .putInt(s.length).put(s)
                .array();
    }

    private static byte[] agree(KeyPair own, PublicKey peer)
            throws GeneralSecurityException {
        KeyAgreement a = KeyAgreement.getInstance("X25519");
        a.init(own.getPrivate());
        a.doPhase(peer, true);
        return a.generateSecret();
    }

    private static byte[] sign(PrivateKey key, byte[] message)
            throws GeneralSecurityException {
        Signature s = Signature.getInstance("Ed25519");
        s.initSign(key);
        s.update(message);
        return s.sign();
    }

    private static boolean verify(PublicKey key, byte[] message, byte[] tag)
            throws GeneralSecurityException {
        Signature s = Signature.getInstance("Ed25519");
        s.initVerify(key);
        s.update(message);
        return s.verify(tag);
    }

    public static void main(String[] args) throws Exception {
        // Mô phỏng identity dài hạn của server, ví dụ key của certificate.
        KeyPairGenerator idGen = KeyPairGenerator.getInstance("Ed25519");
        KeyPair longTermServerIdentity = idGen.generateKeyPair();

        // Mỗi handshake có hai cặp ephemeral X25519 riêng.
        KeyPairGenerator ephemeralGen =
                KeyPairGenerator.getInstance("X25519");
        KeyPair c = ephemeralGen.generateKeyPair();
        KeyPair s = ephemeralGen.generateKeyPair();

        byte[] secretC = agree(c, s.getPublic());
        byte[] secretS = agree(s, c.getPublic());

        // Server ký transcript dùng khóa định danh dài hạn.
        byte[] handshake = transcript(c.getPublic(), s.getPublic());
        byte[] signed = sign(
                longTermServerIdentity.getPrivate(), handshake);

        System.out.println("X25519 secrets agree: "
                + MessageDigest.isEqual(secretC, secretS));
        System.out.println("Shared secret bytes: " + secretC.length);
        System.out.println("Identity signature valid: "
                + verify(longTermServerIdentity.getPublic(),
                         handshake, signed));

        // Một attacker thay public share của server trong transcript.
        KeyPair fakeServerShare = ephemeralGen.generateKeyPair();
        byte[] tampered = transcript(
                c.getPublic(), fakeServerShare.getPublic());
        System.out.println("Modified share rejected: "
                + !verify(longTermServerIdentity.getPublic(),
                          tampered, signed));

        // Handshake độc lập khác dùng fresh ephemeral keypairs.
        KeyPair nextClient = ephemeralGen.generateKeyPair();
        KeyPair nextServer = ephemeralGen.generateKeyPair();
        byte[] nextSecret = agree(
                nextClient, nextServer.getPublic());
        System.out.println("Fresh exchange differs: "
                + !MessageDigest.isEqual(secretC, nextSecret));

        // Giảm vòng đời các byte[] secret do chương trình trực tiếp giữ.
        Arrays.fill(secretC, (byte) 0);
        Arrays.fill(secretS, (byte) 0);
        Arrays.fill(nextSecret, (byte) 0);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 EphemeralIdentityDemo.java
java EphemeralIdentityDemo
~~~

Stdout dự kiến với provider Java 17+ hỗ trợ **X25519** và **Ed25519**:

~~~text
X25519 secrets agree: true
Shared secret bytes: 32
Identity signature valid: true
Modified share rejected: true
Fresh exchange differs: true
~~~

**Giải thích từng dòng:**

1. **X25519 secrets agree: true** — client và server cùng tính một secret từ cặp private/public tạm thời tương ứng.
2. **Shared secret bytes: 32** — X25519 trả 32 byte shared secret.
3. **Identity signature valid: true** — transcript gốc được ký bằng private key Ed25519; public key **được giả định đã được client tin** xác minh đúng chữ ký.
4. **Modified share rejected: true** — khi server ephemeral share bị tráo trong transcript nhưng chữ ký cũ không đổi, việc xác minh **signature** thất bại.
5. **Fresh exchange differs: true** — trao đổi X25519 độc lập dùng fresh keypairs cho secret khác với xác suất áp đảo.

**Không in raw private keys/shared secrets** trong stdout; đây là dữ liệu mật mã nhạy cảm. Raw public keys có thể công khai, nhưng không được nhầm chúng với authenticated identity nếu chưa xác minh chữ ký/certificate.

**Giới hạn của Java demo:** Không có PKI, certificate chain, SAN/hostname, TLS HKDF, Finished, record layer hoặc mạng. Client tin thẳng longTermServerIdentity.getPublic() trong cùng tiến trình — điều này **chỉ minh họa chữ ký khi public key đã được tin**. Ở HTTPS thật, public key đến từ certificate phải được xác minh qua truststore và hostname policy. Mã demo cũng không phải bằng chứng hình thức rằng một giao thức mới do người học tự xây có FS hay chống MITM hoàn chỉnh.

Các array bị Arrays.fill() chỉ là buffer mà code trực tiếp sở hữu; **không bảo đảm mọi secret trong provider/JVM/native memory được xóa**. Demo vẫn tạo KeyPair chứa private key objects, Java runtime quyết định quản lý vòng đời object; đừng sao chép kỹ thuật xóa này rồi tuyên bố có bảo đảm xóa secret tuyệt đối.

### 8.2 Điều demo chứng minh và không chứng minh

| Điều quan sát | Kết luận hợp lệ |
| --- | --- |
| X25519 hai phía cho cùng 32 byte | Primitive KeyAgreement hoạt động đúng trong phép thử |
| Ed25519 xác minh transcript | Có thể ràng buộc public shares với một public key định danh đã tin |
| Đổi một public share làm chữ ký không hợp lệ | Signature bảo vệ nội dung đã được ký khỏi sửa đổi không có private signing key |
| Fresh pair cho secret mới | Dùng keypairs mới tạo secret phiên mới, với xác suất trùng cực nhỏ |
| Ghi lại transcript/public shares và về sau lộ signing key | Không có đường tính lại X25519 secret cũ **chỉ từ signing key** theo giả định X25519, nếu ephemeral private không bị lộ |
| Demo chạy mà không có TLS | **Không** đồng nghĩa tạo một kênh truyền có xác thực/an toàn production |

### 8.3 Vì sao không dùng chữ ký số thay cho ephemeral key?

Một chữ ký Ed25519 hoặc RSA có nhiệm vụ **chứng minh người ký sở hữu private signing key** tương ứng với public key được tin. Signature không trực tiếp tạo shared secret bí mật mà chỉ client/server biết. KeyAgreement lại tạo shared secret nhưng **không tự chứng minh danh tính server**.

Hai phép toán bổ trợ nhau:

~~~text
Signature của identity key:
   "Public share/handshake này thuộc phiên được server hợp lệ xác thực"
   (nếu PKI/public key trust và giao thức đều đúng)

Ephemeral KeyAgreement:
   "Hai bên cùng biết shared secret riêng phiên"
   (nhưng một mình nó chưa ngăn MITM)

TLS handshake:
   kết hợp authentication + authenticated key exchange +
   key schedule + Finished + AEAD traffic protection
~~~

Không thay KeyAgreement bằng RSA ký một random string, không gửi raw shared secret qua mạng, cũng không cố “cải tiến” TLS bằng cách tự nối vài primitive.

## 9. Quan sát TLS thật qua Java JSSE

Trong ứng dụng Java 17, **JSSE** và các API **SSLSocket, SSLEngine, SSLContext, SSLParameters, SSLSession, HttpsURLConnection/HttpClient** triển khai hoặc sử dụng TLS theo provider được cấu hình.

**Điều đầu tiên cần biết:** nhiều thuộc tính trong JVM là **khả năng được hỗ trợ** hoặc **khả năng được bật**, không phải handshake thực tế đã đàm phán. Ví dụ danh sách supported suites có thể chứa tên TLS 1.2 legacy; điều đó không chứng minh server chấp nhận chúng hay bất kỳ connection nào đã dùng chúng.

### 9.1 Xem TLS protocol và suite được hỗ trợ

Đoạn Java sau **độc lập** và không kết nối mạng; nó chỉ liệt kê **khả năng của JSSE provider đang chạy**.

Tệp **JsseCapabilitiesDemo.java**:

~~~java
import java.util.Arrays;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

public class JsseCapabilitiesDemo {
    public static void main(String[] args) throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, null, null);
        SSLEngine engine = context.createSSLEngine();

        System.out.println("TLSv1.3 supported: "
                + Arrays.asList(engine.getSupportedProtocols())
                        .contains("TLSv1.3"));
        System.out.println("TLSv1.3 enabled: "
                + Arrays.asList(engine.getEnabledProtocols())
                        .contains("TLSv1.3"));

        SSLParameters params = context.getSupportedSSLParameters();
        System.out.println("Supported suites count: "
                + params.getCipherSuites().length);
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 JsseCapabilitiesDemo.java
java JsseCapabilitiesDemo
~~~

**Output phụ thuộc JDK/provider/security properties thực tế**, nên không đưa một con số cụ thể cho Supported suites count. Trên một JDK 17 JSSE chuẩn hỗ trợ TLS 1.3, hai dòng đầu thường là true; cấu hình bị quản trị giới hạn có thể khác. Chương trình này **không thực hiện handshake**, vì vậy không có chứng minh runtime vừa dùng ECDHE, PKIX hay forward secrecy.

### 9.2 Với một handshake thực, kiểm tra những gì?

Khi có quyền quan sát một endpoint được cấp phép, hãy ghi nhận:

1. **Protocol version đã đàm phán**, không chỉ supported protocols. Ví dụ từ **SSLSession.getProtocol()** sau handshake.
2. **Cipher suite đã đàm phán**, ví dụ từ **SSLSession.getCipherSuite()**.
3. Với **TLS 1.2**, key exchange thường được thể hiện trong suite (ECDHE_RSA, DHE_RSA hay static RSA).
4. Với **TLS 1.3**, cipher suite **không chứa KEX/auth mode**; phải xem thêm handshake **key_share**, nhóm được chọn, PSK mode và tình trạng full vs resumption.
5. Đảm bảo **certificate chain/trust path, hostname, EKU/KU, thời gian và revocation policy** được kiểm tra theo ứng dụng.
6. Xác nhận **TLS 1.3 0-RTT** có được dùng không, nếu có thì chính sách anti-replay và idempotency là gì.
7. Nếu phía client dùng mTLS, kiểm tra client certificate/auth riêng, không gộp với xác thực server.
8. Kiểm tra **termination point**: TLS kết thúc ở reverse proxy hay trực tiếp tại app; có mTLS/TLS ở chặng nội bộ tiếp theo không.

**Đừng suy luận forward secrecy chỉ từ việc chứng thư là ECDSA hoặc RSA**, cũng không chỉ từ việc SSLSession trả TLS_AES_128_GCM_SHA256.

### 9.3 JSSE handshake debugging trong lab

Trong môi trường được phép, Java hỗ trợ logging phục vụ chẩn đoán JSSE, ví dụ system property:

~~~text
javax.net.debug=ssl,handshake
~~~

Nó có thể giúp quan sát version, cipher suites, key_share, handshake messages và certificate validation theo bản JDK. Đây **không phải kết quả xác nhận an toàn tự động**: cần đọc cấu hình và kết quả thật; debug có thể lộ metadata nhạy cảm trong log và gây tăng dung lượng. Không bật dài hạn trên production.

Nếu phải phân tích packet, dùng công cụ/sniffer được cấp phép trong phạm vi kiểm thử. **Không bật xuất TLS session keys** ra file rồi quên xóa: key logs có thể giúp giải mã traffic mà FS lẽ ra bảo vệ trước việc lộ identity key.

### 9.4 Nên cấu hình TLS thế nào trong ứng dụng Java 17?

Về nguyên tắc:

- **Ưu tiên TLS 1.3** trong những môi trường peer/provider hỗ trợ; khi cần TLS 1.2 để tương thích, chỉ cho phép **cipher suites có (EC)DHE và AEAD** theo policy hiện hành.
- Không bật **static RSA key transport** vì legacy compatibility mà không đánh giá rủi ro; RFC 10015 đã deprecated phương pháp này.
- Không bật SSLv3/TLS 1.0/1.1 hoặc các cipher yếu để “thử cho chạy được”.
- Đảm bảo TrustManager/KeyManager và chứng thư định danh có quyền/chain/hostname đúng.
- Không dùng **trust-all X509TrustManager** hay **HostnameVerifier luôn true**.
- Dùng provider/JDK được cập nhật bảo mật; không viết TLS 1.3 từ KeyAgreement + Signature của bài tập.
- Không hardcode một danh sách duy nhất các named groups hoặc signature algorithms cho mọi máy khi chưa đánh giá tính tương thích/chính sách. Cập nhật theo provider và crypto policy có thẩm quyền.
- Kiểm thử **1-RTT full handshake, resumption, mTLS nếu có**, không chỉ nhìn một phiên HTTPS thành công.

**Lưu ý về cipher suite naming:** Tên TLS 1.3 như TLS_AES_256_GCM_SHA384 chỉ diễn đạt AEAD/hash; **X25519, secp256r1, RSA-PSS hoặc ECDSA không xuất hiện trong tên**. Chúng là những phép chọn của handshake khác với TLS 1.2.

## 10. Vận hành để bảo đảm FS có ý nghĩa thực tế

### 10.1 Bảo vệ private key dài hạn của certificate

FS giảm hậu quả **giải mã hồi tố** sau khi lộ identity private key, nhưng không làm khóa đó kém quan trọng. Attacker có private key phù hợp certificate và có khả năng can thiệp mạng có thể **giả mạo peer trong các handshake tương lai** cho đến khi chính sách trust/thu hồi/key rotation chặn.

Do đó cần:

- Bảo vệ key bằng HSM, keystore/secret manager theo yêu cầu.
- Giới hạn quyền ký, quyền xuất khóa, phạm vi dùng và audit.
- Cấp lại certificate/key đúng chu kỳ và khi có sự cố.
- Kiểm tra certificate revocation/truststore và hostname chính xác.
- Không chia sẻ một signing key cho quá nhiều endpoint/tenant nếu không có chính sách rõ.
- Không gửi private key qua ticket/chat/log hoặc đưa vào source repository.

### 10.2 Không giữ ephemeral secret “cho tiện debug”

Khóa tạm thời, shared secret và session keys cần vòng đời ngắn nhất theo implementation và protocol. Nếu doanh nghiệp log raw session secrets, lưu memory dump mở rộng, bật TLS key logging hoặc backup session state quá dài, **bảo đảm FS trước việc chỉ mất identity key không còn diễn đạt đầy đủ rủi ro thực tế**.

Java app không nên tự lấy shared secret rồi ghi vào log “để so sánh” hoặc giữ private ephemeral key trong cache toàn cục. Dựa vào JSSE implementation chuẩn thay vì tự quản lý raw ephemeral keys trong ứng dụng.

### 10.3 Session resumption và PSK cần chính sách riêng

Làm rõ:

- Mode full handshake hay PSK resumption?
- PSK-only hay PSK+(EC)DHE?
- Có 0-RTT hay không?
- Ticket keys được lưu ở đâu, ai có quyền, thời hạn/rotation?
- Cách xóa ticket/PSK cũ và xử lý failover nhiều instance?
- Chống replay/idempotency cho 0-RTT ở tầng ứng dụng thế nào?

Bật TLS 1.3 nhưng quản trị session resumption không cẩn thận có thể tạo các đường tấn công khác với những bảo đảm của fresh (EC)DHE full handshake.

### 10.4 Không thay thế bảo mật ở các lớp khác

FS không sửa:

- SQL injection, XSS, command injection trong ứng dụng.
- Lộ JWT/API key qua log hoặc browser storage.
- Mã hóa dữ liệu lưu trữ thiếu chính sách khóa/rotation.
- Phân quyền sai giữa tenant.
- Endpoint chủ động trả plaintext cho attacker được cấp quyền nhầm.
- CA hoặc truststore bị cài root giả dẫn đến xác thực sai.
- Backup/monitoring chứa session secrets hoặc raw dữ liệu.

Vì vậy đánh giá TLS là **một** lớp của hệ thống an toàn, không phải “bật PFS là xong”.

## 11. Các hiểu nhầm phổ biến

1. **“Server certificate là RSA nên chắc chắn không có FS.”** Sai: RSA có thể chỉ dùng xác thực trong ECDHE_RSA/TLS 1.3, còn traffic key đến từ DHE.
2. **“Đã có ECDHE thì không cần chứng thư.”** Sai: DH không xác thực bị MITM; cần certificate/PSK trust và handshake authentication.
3. **“Certificate chứa ephemeral session key.”** Sai: cert chứa public key định danh, ephemeral key tạo trong handshake.
4. **“Private key định danh dùng để giải mã mọi session TLS.”** Sai với ECDHE/TLS 1.3 full certificate handshake; có thể đúng với static RSA key transport TLS 1.2 theo thiết kế legacy.
5. **“TLS 1.3 cipher suite name cho biết server dùng RSA hay ECDSA và nhóm X25519.”** Sai: TLS 1.3 suite không mang KEX/auth trong tên.
6. **“TLS 1.3 có FS tuyệt đối trong mọi kết nối, kể cả 0-RTT.”** Sai: phải phân biệt full (EC)DHE, PSK-only và early data.
7. **“0-RTT an toàn như các request thông thường.”** Sai: có replay risk và thiếu bảo đảm FS đầy đủ; phải chọn các thao tác replay-safe.
8. **“Ephemeral key phải được gửi cho đối tác để hai bên tính secret.”** Sai: chỉ **public share** được truyền; private share phải giữ bí mật.
9. **“DH shared secret dùng thẳng làm AES key.”** Sai: TLS key schedule dẫn xuất các key/IV/context riêng.
10. **“Nhìn thấy cùng shared secret trong hai phía là đã có kênh TLS an toàn.”** Sai: demo chưa có PKI, Finished, AEAD, replay, downgrade protections.
11. **“Lộ identity key sau phiên cũ thì FS còn bảo vệ mọi phiên trong tương lai.”** Sai: attacker có thể mạo danh khi bắt tay mới.
12. **“Lộ session traffic key không sao vì có FS.”** Sai: traffic/session keys bị lộ có thể mở dữ liệu được bảo vệ bởi chúng.
13. **“Dùng KeyUpdate chắc chắn hồi phục nếu key hiện tại đã bị chiếm.”** Sai: KeyUpdate không tự rerun ECDHE; hậu quả phụ thuộc loại key bị lộ.
14. **“Mỗi kết nối có secret khác nhau nghĩa là chắc chắn FS.”** Chưa đủ: nếu secret được dẫn xuất từ một bí mật dài hạn mà sau đó bị lộ, có thể tái lập các phiên.
15. **“Forward secrecy đồng nghĩa chống máy tính lượng tử tương lai.”** Không: ECDH cổ điển có thể bị phá khi giả định độ khó không còn đúng.
16. **“Chỉ test TLSv1.3 supported là chứng minh endpoint dùng TLS 1.3.”** Sai: phải quan sát **protocol negotiated** trên phiên thực.
17. **“TLS cipher suite ECDHE_RSA nghĩa là RSA encrypt premaster secret.”** Sai: RSA chứng thực chữ ký, ECDHE tạo secret.
18. **“Thời hạn cert ngắn tự tạo forward secrecy.”** Không: đổi certificate thường xuyên giới hạn hậu quả compromise, nhưng không thay thế ephemeral key exchange.
19. **“Nếu client không xác thực hostname, ECDHE vẫn chặn MITM.”** Sai: kẻ giữa đường có thể tự thực hiện key exchange với mỗi phía.
20. **“Có FS rồi có thể lưu TLS session secrets/heap dump thoải mái.”** Sai: rò secrets trực tiếp có thể làm mất bảo mật của phiên được ghi lại.

## 12. Tình huống phân tích

### Tình huống A — Máy chủ dùng RSA certificate, cần kiểm tra có FS không

Nhóm vận hành xem certificate type là RSA rồi kết luận không có FS. Cách đánh giá đúng:

- Xem **protocol phiên thật**.
- Nếu TLS 1.2, xác định suite có **ECDHE_RSA** hay **TLS_RSA_...**; hai loại có tính chất rất khác.
- Nếu TLS 1.3, xem full handshake/key share hoặc PSK mode; không suy ra từ certificate type và tên TLS_AES_... suite.
- Kiểm tra JDK/provider, security properties, cipher suites đang enabled thực tế.

**Kết luận:** Loại khóa chứng thư nói về **xác thực**, không tự quyết định key establishment và forward secrecy.

### Tình huống B — Private key của server bị lấy cắp sau sáu tháng

Attacker ghi packet TLS từ sáu tháng trước, nay có server RSA identity key.

- Phiên TLS 1.2 static RSA: có thể bị giải mã hồi tố.
- Phiên TLS 1.2 ECDHE/TLS 1.3 full DHE: không giải mã được chỉ từ RSA identity key, nếu ephemeral/session secrets cũ không bị lộ.
- Handshake mới: attacker có thể mạo danh server nếu không bị thu hồi/chặn.

**Hành động:** đánh giá exposure theo từng protocol/suite/mode và thời điểm lộ; luân chuyển key, chứng thư và xử lý incident, không khẳng định “vì TLS nên không vấn đề”.

### Tình huống C — Dịch vụ bật resumption và 0-RTT

Để giảm latency, đội hệ thống chấp nhận 0-RTT request gọi API tạo giao dịch. Attacker gửi lại một request early data đã ghi.

**Vấn đề:** 0-RTT có replay risk; FS của early data không tương đương 1-RTT fresh DHE. Kết quả có thể là thao tác bị lặp.

**Khắc phục:** không gửi thao tác có side effect trong 0-RTT nếu không có profile chống replay và idempotency thích hợp; hạn chế early data, theo dõi session tickets và thực thi anti-replay ở tầng hạ tầng/nghiệp vụ.

### Tình huống D — Lộ session key log trong quá trình debug

Một developer bật key logging để Wireshark giải mã HTTPS lab và vô tình tải key log lên artifact store dùng chung. Attacker có file logs và packet capture.

**Vấn đề:** FS trước việc lộ **long-term identity key** không bảo vệ khi **session traffic secrets đã bị xuất trực tiếp**. Cần coi file key log là secret, hạn chế truy cập, thu hồi/điều tra và không lưu qua pipeline production.

### Tình huống E — Endpoint bị chiếm khi một phiên đang sống

Attacker đọc bộ nhớ app hoặc TLS runtime, lấy session keys và plaintext. Sau đó tổ chức xoay certificate.

**Vấn đề:** rotation certificate không xóa được dữ liệu phiên đã bị thu thập, và FS không có nghĩa bảo vệ trước **đối thủ đang kiểm soát endpoint**. Cần incident response ở tầng host/app và xác định dữ liệu đã lộ.

### Tình huống F — TLS 1.3 KeyUpdate bị hiểu là “DH mới”

Một nhóm muốn phục hồi sau khi attacker lấy application traffic secret, nên chỉ yêu cầu KeyUpdate.

**Vấn đề:** KeyUpdate dẫn xuất từ traffic secret cũ, không trao đổi ephemeral DH mới; attacker có bí mật gốc có thể theo dõi các thế hệ khóa phù hợp. Cần thiết lập handshake mới với key establishment và xác thực được kiểm soát để phục hồi theo mô hình sự cố.

## 13. Test matrix và câu hỏi tự kiểm tra

### 13.1 Test matrix cho Java/TLS deployment

| Test | Cần quan sát | Kết luận |
| --- | --- | --- |
| Full TLS 1.3 certificate handshake | key_share + certificate authentication + Finished | Có cơ sở FS theo fresh (EC)DHE; vẫn cần endpoint/secret lifecycle |
| TLS 1.2 ECDHE_RSA | negotiated suite thực tế chứa ECDHE_RSA | RSA cert dùng xác thực, ECDHE tạo secret |
| TLS 1.2 TLS_RSA | negotiated suite thực tế static RSA key exchange | Không có FS, cần loại bỏ theo chính sách mới |
| TLS 1.3 PSK-only | mode psk_ke không có fresh key_share | Không mặc định có FS khi PSK còn tồn tại |
| TLS 1.3 PSK+(EC)DHE | psk_dhe_ke và fresh key share | FS của 1-RTT dưới điều kiện DHE/secret management |
| TLS 1.3 0-RTT | early_data, resumption PSK, anti-replay | Không mặc định FS hoặc replay safety |
| Sai certificate/hostname | handshake bị từ chối | Xác thực identity không bị bypass |
| Key logging/debug đang bật trên production | kiểm tra config, files, ACL, retention | Không để lộ session secrets |
| Lộ long-term identity key | kiểm kê protocol/handshake modes & lịch sử | Phân biệt traffic cũ với nguy cơ giả mạo tương lai |
| Chỉ gọi getSupportedProtocols() | có TLSv1.3 trong danh sách | **Không** đủ để xác nhận negotiated session |
| Thay certificate RSA bằng ECDSA | chỉ thay signature key algorithm | Không kết luận FS nếu key establishment chưa kiểm tra |
| TLS terminates ở reverse proxy | chặng mạng sau proxy và log/data storage | FS của TLS client-to-proxy không tự bảo vệ hop khác |

### 13.2 Câu hỏi và đáp án

**Câu 1.** Chứng thư X.509 chứa khóa tạm thời của mỗi phiên không?

**Trả lời:** Không. Chứng thư chứa public key định danh và thông tin xác thực. Ephemeral keypair được tạo cho key exchange của handshake.

**Câu 2.** RSA certificate có khiến TLS 1.3 không có forward secrecy không?

**Trả lời:** Không. RSA certificate có thể dùng xác thực bằng chữ ký; full TLS 1.3 certificate handshake có fresh (EC)DHE tạo shared secret.

**Câu 3.** TLS 1.2 TLS_RSA khác TLS_ECDHE_RSA ở điểm nào?

**Trả lời:** TLS_RSA mã hóa premaster secret bằng static RSA public key, làm mất FS nếu private key bị lộ về sau. TLS_ECDHE_RSA dùng ephemeral ECDHE để tạo premaster secret, RSA chỉ xác thực handshake.

**Câu 4.** Vì sao (EC)DHE một mình chưa chống MITM?

**Trả lời:** Vì ai cũng có thể thay public share; cần xác thực đúng peer và ràng buộc transcript bằng chữ ký/Finished theo giao thức.

**Câu 5.** Khi private key định danh của server bị lộ sau phiên cũ có FS, attacker có giải mã được packet cũ chỉ từ private key ấy không?

**Trả lời:** Không theo mô hình an toàn chuẩn, nếu fresh ephemeral key material và traffic secrets cũ không lộ, thuật toán an toàn. Nhưng attacker có thể giả mạo server trong handshake tương lai.

**Câu 6.** Session ticket/PSK ảnh hưởng FS thế nào?

**Trả lời:** PSK-only không tự bảo đảm FS trước việc PSK còn lưu và bị lộ; PSK+(EC)DHE thêm fresh DH có thể cung cấp FS cho 1-RTT. Thời gian sống/xóa PSK là yếu tố vận hành quan trọng.

**Câu 7.** 0-RTT early data có cùng FS và anti-replay như TLS 1.3 1-RTT DHE không?

**Trả lời:** Không. Early data phụ thuộc PSK và có rủi ro replay giữa kết nối; cần giới hạn những thao tác được phép và phòng ngừa tương ứng.

**Câu 8.** Vì sao CipherSuite TLS_AES_128_GCM_SHA256 không cho biết X25519 hay RSA?

**Trả lời:** TLS 1.3 tách lựa chọn key exchange và signature khỏi tên cipher suite; cần xem handshake negotiated key_share/PSK/certificate.

**Câu 9.** Demo X25519+Ed25519 của bài có thay thế JSSE không?

**Trả lời:** Không. Demo thiếu PKIX, TLS transcript format/Finished/HKDF/record layer/host validation và nhiều cơ chế an toàn bắt buộc.

**Câu 10.** KeyUpdate có đủ để phục hồi nếu attacker biết current application traffic secret không?

**Trả lời:** Không. KeyUpdate không rerun DH, chỉ dẫn xuất thế hệ mới từ secret hiện có.

**Câu 11.** Tại sao KeyAgreement.generateSecret() không được dùng thẳng làm AES key theo cảm tính?

**Trả lời:** TLS dùng key schedule HKDF để phân tách theo context, hướng traffic và giai đoạn, tránh tái sử dụng trực tiếp shared secret.

**Câu 12.** Có FS rồi thì cần bảo vệ log/proxy/database không?

**Trả lời:** Có. FS bảo vệ traffic cũ trước một loại compromise cụ thể, không tự bảo vệ các bản sao plaintext, session key logs và dữ liệu đã được endpoint giải mã.

## 14. Tài liệu tham khảo

- [RFC 9846 (July 2026) — TLS 1.3](https://www.rfc-editor.org/rfc/rfc9846.html): đặc tả TLS 1.3 hiện hành, **thay thế RFC 8446 (2018)**; §2.2–2.3 về PSK/0-RTT, §4 về handshake, §7 về key schedule, Appendix F về forward secrecy và key compromise.
- [RFC 8446 (2018) — TLS 1.3, lịch sử](https://www.rfc-editor.org/rfc/rfc8446.html): nguồn học tập cũ, hiện được RFC 9846 thay thế.
- [RFC 5246 — TLS 1.2, lịch sử](https://www.rfc-editor.org/rfc/rfc5246.html): cơ chế RSA key exchange và (EC)DHE trong TLS 1.2 (đã bị RFC 9846 thay thế ở vị trí chuẩn hiện hành).
- [RFC 10015 (2026) — Deprecating Obsolete Key Exchange Methods in TLS 1.2](https://www.rfc-editor.org/rfc/rfc10015.html): loại bỏ/khuyến cáo ngừng sử dụng RSA key transport và các phương pháp lỗi thời trong TLS 1.2.
- [Oracle Java SE 17 — JSSE Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-secure-socket-extension-jsse-reference-guide.html): SSLContext, SSLSocket/SSLEngine, TLS 1.3, handshake, cipher suites, provider và debug.
- [Oracle Java SE 17 — KeyAgreement](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/KeyAgreement.html): API tạo shared secret cho X25519.
- [Oracle Java SE 17 — Signature](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Signature.html): API tạo/kiểm tra chữ ký định danh.
- [Oracle Java SE 17 — Standard Algorithm Names](https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html): tên thuật toán X25519/Ed25519/TLS và cipher suites.
- [RFC 9525 — Service Identity in TLS](https://www.rfc-editor.org/rfc/rfc9525.html): hướng dẫn xác minh danh tính dịch vụ khi dùng TLS.

## Kết luận

**Khóa định danh dài hạn và khóa tạm thời giải quyết hai bài toán khác nhau.** Private key tương ứng certificate dùng **xác thực** đối tác và ràng buộc handshake; ephemeral (EC)DHE tạo một shared secret **không phụ thuộc vào khả năng giải mã premaster secret bằng static identity private key**. Với fresh (EC)DHE, xử lý KDF đúng và xóa secret tạm thời, việc đánh cắp private key định danh **sau phiên** không đủ để giải mã traffic cũ đã ghi lại: đó là ý nghĩa thực tế của forward secrecy.

Khi đánh giá một kết nối TLS, phải xét **protocol phiên thực, key exchange/resumption mode, 0-RTT, certificate validation và key lifecycle**, không kết luận từ loại certificate hoặc tên cipher suite một mình. **TLS 1.2 static RSA key transport không có FS; TLS 1.2 (EC)DHE và full TLS 1.3 certificate handshake dùng fresh (EC)DHE có FS trong mô hình thích hợp; TLS 1.3 PSK-only và 0-RTT là các ngoại lệ phải quản lý có chủ đích.** Trong Java 17+, hãy để **JSSE** thực hiện TLS, dùng JCA X25519/Ed25519 chỉ cho việc học primitive hoặc giao thức chuyên biệt đã được đánh giá chặt chẽ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tls-foundation-006">15.6 Phân biệt tin chuỗi chứng thư với xác minh tên máy chủ</a>

<details>
<summary>Xem chi tiết</summary>

# 15.6 Phân biệt tin chuỗi chứng thư với xác minh tên máy chủ

Khi ứng dụng Java kết nối HTTPS/TLS, hai câu hỏi bảo mật độc lập phải được trả lời đúng:

1. **Có tin được chuỗi chứng thư (certificate path/chain) này không?** Chứng thư máy chủ có nối tới một **trust anchor** mà client chủ động tin, với chữ ký, hiệu lực và ràng buộc chứng thư hợp lệ không?
2. **Chứng thư ấy có chứng minh danh tính của đúng máy chủ mà ứng dụng muốn kết nối không?** Tên tham chiếu từ URL/config (hostname hoặc IP) có khớp **Subject Alternative Name (SAN)** trên chứng thư theo quy tắc xác minh endpoint không?

Một chứng thư có chuỗi hợp lệ **vẫn có thể thuộc về một hostname khác**. Chỉ kiểm chuỗi nhưng bỏ kiểm tên biến một kênh TLS có mã hóa thành kênh **không xác thực đúng đối tác**. Ngược lại, chứng thư in đúng hostname nhưng do CA không được tin cấp cũng không hợp lệ. Một kết nối HTTPS an toàn phải thỏa **cả hai điều kiện**, cùng các kiểm tra về mục đích sử dụng khóa, chính sách TLS và trạng thái chứng thư theo yêu cầu.

Bài học đặt trong **15. Nền tảng TLS**, tập trung vào sự khác nhau giữa **trust chain** và **hostname verification**, cách Java/JSSE thực hiện, ví dụ Java 17+ chạy thực tế với CA nội bộ và SAN `DNS:localhost`, rồi rút ra checklist vận hành.

## 1. Mô hình tổng quan: “ai ký?” không đồng nghĩa “đúng ai?”

Giả sử ứng dụng gọi:

~~~text
https://payments.example.com/api/charge
~~~

Peer gửi một chứng thư có SAN `DNS:reports.example.com`, nhưng chứng thư được một CA đáng tin ký, hợp lệ về ngày tháng và đường dẫn PKIX. Nếu chỉ kiểm chữ ký/CA, client có thể tin **một chứng thư của hệ thống reports** để nói chuyện với **payments**.

Hai phép kiểm liên quan nhưng có đầu vào khác nhau:

~~~text
                  TLS handshake / peer certificates
                               |
                 +-------------+-------------+
                 |                           |
                 v                           v
        (A) PATH TRUST              (B) ENDPOINT IDENTITY
        certificate chain           intended hostname or IP
        signature/path policy       from URL/config/request
        trusted root                versus leaf SAN
                 |                           |
                 v                           v
           pass / fail                 match / mismatch
                 |                           |
                 +-------------+-------------+
                               |
                      both pass => accept
                   either fails => reject
~~~

**Phép A (path trust)** trả lời: “Theo trust anchors và chính sách đã cấu hình, public key/chứng thư này có nằm trong chuỗi tin cậy hợp lệ hay không?”

**Phép B (endpoint identity)** trả lời: “Danh tính được chứng thư leaf chứng nhận có khớp **tên đích mà ứng dụng định liên lạc** hay không?”

**Không thể đổi phép A thành phép B** bằng cách chỉ import thêm CA vào truststore. Cũng không thể đổi phép B thành phép A bằng cách viết hostname đúng vào CN/SAN của một chứng thư tự ký chưa được tin.

### 1.1. Bảng kết quả bốn tình huống

| Chuỗi chứng thư được tin | Tên máy chủ khớp | Quyết định |
| --- | --- | --- |
| Có | Có | Có thể chấp nhận sau các kiểm tra TLS/chính sách khác |
| Có | **Không** | **Từ chối**: certificate có thể hợp lệ cho một máy chủ khác |
| **Không** | Có | **Từ chối**: tên đúng nhưng certificate không được trust |
| **Không** | **Không** | **Từ chối**: cả hai điều kiện đều sai |

**“Có thể chấp nhận” không đồng nghĩa đã được phân quyền**: TLS xác thực endpoint theo chính sách, còn quyền sử dụng API/tài khoản/tenant vẫn là vấn đề authorization, token, mTLS identity mapping hoặc các bước ứng dụng khác.

## 2. Chuỗi chứng thư và trust anchor: công việc của PKIX

Chứng thư X.509 leaf có public key của server. Một certificate path thường là:

~~~text
server leaf: SAN DNS:api.example.com
      |
      | chữ ký được kiểm bằng public key của issuer
      v
issuing intermediate CA: BasicConstraints CA=true
      |
      v
root CA (trust anchor do client cấu hình)
~~~

Đối với phép kiểm đường dẫn, client cần đánh giá:

- **Chữ ký** giữa leaf, intermediates và trust anchor được phép tin. Chỉ so tên `Issuer DN` với `Subject DN` là **không đủ**; hai certificate khác khóa có thể dùng cùng chuỗi tên.
- **BasicConstraints** và `pathLenConstraint` ở chứng thư CA trung gian, cùng `KeyUsage keyCertSign` khi áp dụng.
- **NotBefore/NotAfter** và đồng hồ hiện tại; chứng thư hết hạn hoặc chưa có hiệu lực không tự trở nên hợp lệ vì CA đã được tin.
- **Thuật toán, cỡ khóa, signature constraints, certificate policies/name constraints** và cấu hình PKIX/TLS của môi trường.
- **Mục đích sử dụng leaf**: ví dụ EKU `serverAuth`/`clientAuth` theo vai trò khi có extension; `KeyUsage` tương ứng.
- **Thu hồi** (CRL/OCSP và chính sách revocation) khi yêu cầu; có một path hợp lệ về chữ ký không đồng nghĩa certificate chưa bị thu hồi.
- **Trust anchor được chọn sẵn** và nguồn gốc của nó; root tự ký không tự chứng minh đáng tin.

Trong Java, các lớp thường liên quan:

| Thành phần | Vai trò |
| --- | --- |
| `KeyStore` | Lưu các entry, có thể làm identity keystore hoặc truststore |
| `TrustedCertificateEntry` | Chứng chỉ công khai mà quản trị viên chọn làm trust anchor/đầu vào trust policy |
| `TrustManagerFactory` | Khởi tạo trust manager từ một KeyStore hoặc cấu hình trust mặc định |
| `X509TrustManager` / `X509ExtendedTrustManager` | Kiểm certificate chain theo ngữ cảnh phiên TLS và provider/chính sách |
| `CertPathValidator("PKIX")` | API xác minh đường dẫn chứng thư theo tham số trust anchors/policy |
| `CertPathBuilder` | Hỗ trợ dựng certificate path qua intermediate/trust anchors nếu có dữ liệu phù hợp |
| `KeyManagerFactory` | Lấy private key + chain của **chính** ứng dụng khi nó làm server hoặc client mTLS |

**Chú ý ranh giới:** Kiểm path bằng `CertPathValidator("PKIX")` với `TrustAnchor` biết trước **không tự cung cấp tên hostname cần so**. Đây là phần A, không thay được phần B. Chương trình demo phần 9 cố ý cho PKIX pass và sau đó để HTTPS endpoint identification chứng minh hostname mismatch.

### 2.1. Truststore không phải “danh sách mọi hostname được phép”

Một truststore chứa root `Example Internal Root` có thể tin **rất nhiều leaf certificates** do CA đó hoặc intermediate của nó ký. Nếu leaf dành cho `hr.internal`, nó không được dùng thay leaf của `payroll.internal` chỉ vì có cùng CA.

**Trusting a CA is not pinning a host.** Pinning public key/certificate (nếu chính sách yêu cầu) là một cơ chế bổ sung có vòng đời/rotation phức tạp, không tự thay HTTPS hostname validation. Đặc biệt, tin một CA nội bộ có phạm vi cấp rất rộng mà bỏ hostname check là lỗi có mức độ ảnh hưởng lớn.

### 2.2. Root, intermediate và server gửi chuỗi gì?

Server TLS thường gửi **leaf và intermediate(s)** cần để dựng path; trust anchor root được client lưu sẵn (không nhất thiết gửi từ server). Nếu thiếu intermediate, client có thể không dựng được path, dẫn đến **path building failed**, dù leaf có SAN đúng.

Nếu server gửi cả root của mình, nó **không thể ép client tin root đó**. Client phải có trust anchor từ nguồn độc lập đã xác thực và chính sách của nó. Vì vậy “server gửi đầy đủ chain” và “client trust đúng CA” là hai trách nhiệm khác nhau.

## 3. Xác minh tên máy chủ (hostname verification) là phép kiểm khác

Hostname verification so sánh **reference identity** (tên mà ứng dụng dự định liên lạc) với **presented identity** trên **leaf X.509 certificate**.

Ví dụ:

~~~text
Đích theo URL:       https://api.example.com
Reference identity:  api.example.com

Leaf SAN:
  DNS: api.example.com
  DNS: api-backup.example.com

=> tên api.example.com khớp với một DNS SAN thích hợp
~~~

Một certificate hợp lệ cho `admin.example.com` không tự hợp lệ cho `api.example.com`. Việc server cầm private key và đã được CA ký chỉ chứng minh server sở hữu khóa của **một danh tính được CA chứng nhận**; cần bước so đúng danh tính đích.

### 3.1. Lấy tên tham chiếu từ đâu?

Đối với HTTPS, reference hostname thường lấy từ **hostname trong URL/endpoint được ứng dụng cấu hình** sau khi parse và canonicalize theo quy tắc giao thức, không lấy tùy ý từ:

- `Issuer DN` hoặc `Subject CN` của certificate server;
- `Host` header mà peer gợi ý khi đã kết nối;
- DNS PTR/reverse DNS của địa chỉ IP;
- SNI do server tự phản hồi;
- Tên alias trong `KeyStore`;
- Một tên mới do attacker trả về trong response hay redirect mà ứng dụng tự ý coi là tên cũ.

Ví dụ HTTPS client đang kết nối `https://api.example.com`; DNS có thể resolve sang `203.0.113.10`, nhưng reference identity vẫn là **api.example.com**, không phải IP mà DNS trả về. Nếu người dùng **cố ý** gọi `https://203.0.113.10`, reference identity là **địa chỉ IP literal** và phải theo quy tắc IP SAN.

**Nếu ứng dụng đi qua proxy, load balancer hoặc connect-by-IP nhưng mong danh tính DNS:** cần giữ đúng **logical peer host** lúc tạo TLS/HTTP client. `SSLSocketFactory.createSocket(tcp, "api.example.com", port, true)` có thể giữ peer hostname cho SNI và endpoint check khi transport socket đã được mở tới IP; điều này phải được thiết kế theo client API và chính sách kết nối, không chọn hostname theo certificate trả về.

### 3.2. SAN là nguồn danh tính chuẩn

`Subject Alternative Name` là extension X.509 cho nhiều loại danh tính:

| SAN type | Ví dụ | Khi nào được dùng |
| --- | --- | --- |
| `dNSName` (type 2) | `DNS:app.example.com` | Khi client xác minh tên DNS qua HTTPS |
| `iPAddress` (type 7) | `IP:192.0.2.10`, `IP:2001:db8::1` | Khi URL/endpoint dùng IP literal và chính sách TLS yêu cầu so IP |
| `uniformResourceIdentifier` (type 6) | `URI:spiffe://domain/service` | Giao thức workload identity riêng có quy tắc xác minh URI; **không mặc nhiên** thay HTTPS DNS hostname |
| `rfc822Name` | Email | Không dùng như DNS hostname cho HTTPS |

**Ví dụ đối chiếu:**

| Client yêu cầu | Leaf certificate chứa | Kết quả name check |
| --- | --- | --- |
| `https://localhost` | `DNS:localhost` | Khớp |
| `https://127.0.0.1` | Chỉ `DNS:localhost` | **Không khớp** vì thiếu `IP:127.0.0.1` |
| `https://127.0.0.1` | `IP:127.0.0.1` | Khớp về loại/tên, **nếu chain được trust** |
| `https://api.example.com` | `DNS:api.example.com` | Khớp |
| `https://api.example.com` | `DNS:other.example.com` | **Không khớp** |
| `https://api.example.com` | Chỉ `CN=api.example.com` | **Không nên dựa vào CN**, phải cấp SAN đúng |
| `https://192.0.2.10` | `DNS:192.0.2.10` | **Không thay IP SAN hợp lệ** khi yêu cầu IP literal |

**IP trong DNS SAN không tương đương iPAddress SAN.** Chỉ vì chuỗi có dấu chấm và số trông như IP, không thể nhập nó dưới type DNS rồi mong HTTPS IP matching chấp nhận.

### 3.3. Common Name (CN) và legacy fallback

Một số TLS implementations lịch sử có thể fallback sang `Subject CN` khi certificate không có DNS SAN phù hợp; quy tắc cụ thể thay đổi theo platform, phiên bản và chuẩn. **Không xem fallback đó là mục tiêu thiết kế**, không dựa vào CN để cấp hoặc đánh giá chứng thư HTTPS mới.

Với chứng thư server hiện đại, chủ động yêu cầu CA phát hành **SAN DNS/IP đúng loại và đúng hostname thực tế**. Nếu môi trường nào đó vẫn chấp nhận CN khi thiếu SAN, đó là behavior cần quản lý compatibility và hardening, **không chứng minh mọi client sẽ chấp nhận**.

### 3.4. Wildcard và IDN (tên quốc tế hóa)

- `DNS:*.example.com` chỉ nên được hiểu trong giới hạn wildcard mà client/protocol chấp nhận; **không tự suy ra** nó hợp lệ cho `example.com` hoặc `a.b.example.com`.
- `*.example.com` không phải cách cấp quyền không giới hạn cho mọi cấp subdomain. Các chuẩn/client có thể áp thêm ràng buộc về wildcard/public suffix.
- Tên IDN (có chữ ngoài ASCII) thường liên quan đến **IDNA/Punycode A-label**, normalization và so khớp không phân biệt hoa thường theo quy tắc DNS; **không tự dùng `String.equalsIgnoreCase`** để thay thuật toán chuẩn.
- URL, SNI, chứng thư SAN và thư viện HTTPS cần thống nhất cách biểu diễn tên; nếu ứng dụng phải tự xử lý ở tầng thấp, ưu tiên API chuẩn hoặc thư viện đã được kiểm thử interoperability.

**Không tự viết một hàm `hostnameMatches` ngây thơ** dùng `endsWith(".example.com")`, vì dễ có lỗi ranh giới label, wildcard, internationalized names hoặc confusion của chuỗi.

## 4. Vì sao PKIX hợp lệ vẫn không đủ cho HTTPS?

Có hai tình huống đặc biệt rõ:

### 4.1. CA đáng tin ký chứng thư cho hostname khác

~~~text
Truststore client:  Root CA R

Certificate X do R ký:
  Subject/SAN = DNS:files.example.com
  Validity    = còn hạn
  Signature   = hợp lệ

Client mong kết nối:
  https://payments.example.com

Path trust  = PASS (R được tin)
Name match  = FAIL (files != payments)
Decision    = REJECT
~~~

Nếu ứng dụng tắt name check, đối tượng bất kỳ được cấp certificate hợp lệ bởi một CA được tin (chẳng hạn CA nội bộ được phép cấp nhiều hostname) có thể bị nhầm thành đúng endpoint. TLS vẫn mã hóa tới peer, nhưng **peer không được xác nhận là đúng dịch vụ mục tiêu**.

### 4.2. Hostname đúng, CA không được tin

~~~text
Certificate tự ký:
  SAN DNS:payments.example.com
  Issuer = chính nó
  Signature có thể tự kiểm

Client truststore:
  KHÔNG tin public key/root của certificate này

Name match  = PASS về tên
Path trust  = FAIL về trust anchor
Decision    = REJECT
~~~

Một attacker có thể tự tạo chứng thư mang hostname bất kỳ. Bởi vậy **khớp SAN không thay chain trust**. Lỗi HTTPS chỉ hết đúng cách khi client được cấp trust anchors hợp lệ hoặc server triển khai chain từ CA được chấp nhận và chứng thư có đúng tên.

### 4.3. Không lẫn “bật TLS” với “xác thực đúng đối tác”

Một socket dùng TLS 1.3/cipher suite mạnh và có encryption **không đảm bảo** ứng dụng đang nói chuyện với bên mong muốn nếu bỏ CA validation hoặc hostname verification.

- **Trust-all certificate**: bỏ/qua mặt xác minh path trust.
- **HostnameVerifier luôn trả true** hoặc raw SSLSocket không cấu hình endpoint identification: bỏ/qua mặt name check.
- Hai cách **đều nguy hiểm**, nhưng là hai lỗi riêng. Sửa lỗi CA không đòi tắt hostname; sửa lỗi hostname không đòi trust-all.

## 5. Java/JSSE thực hiện hai phép kiểm ở đâu?

Các API Java có hành vi mặc định khác nhau; phải phân biệt **client HTTPS cấp cao** với **raw TLS socket**.

### 5.1. `HttpsURLConnection`

`javax.net.ssl.HttpsURLConnection` được thiết kế cho HTTPS và theo mặc định thực hiện xác minh certificate/trust và hostname với các cơ chế chuẩn (trừ khi ứng dụng thay custom trust manager/hostname verifier hoặc cấu hình không an toàn). Không viết:

~~~java
// PHẢN VÍ DỤ NGUY HIỂM - KHÔNG DÙNG
connection.setHostnameVerifier((host, session) -> true);
~~~

Điều đó biến hostname check thành “luôn chấp nhận”. Nó không phải cách sửa lỗi tên trên certificate. Nếu URL `https://api.example.com` mà server chỉ có SAN `DNS:localhost`, giải pháp đúng là **đề nghị cấp chứng thư cho api.example.com** hoặc sửa URL/đích theo yêu cầu đã xác thực.

Đừng thay `SSLSocketFactory` của `HttpsURLConnection` bằng factory trust-all. Cấu hình truststore đúng root CA và để client thực hiện endpoint verification theo API.

### 5.2. `java.net.http.HttpClient` (JDK 11+)

`HttpClient` hiện đại sử dụng TLS và thực hiện HTTPS endpoint verification theo cấu hình chuẩn. Bạn có thể cấu hình `SSLContext` dùng truststore nội bộ khi cần kết nối service có CA riêng, **không tạo X509TrustManager bỏ qua mọi lỗi**.

Lưu ý các JVM flags/thuộc tính nội bộ có thể tác động việc xác minh hostname ở những implementation cụ thể; **không dùng các switch “disable hostname verification” để fix production**. Dùng test HTTPS thực tế với đúng URL hostname và CA chain như môi trường deploy.

### 5.3. Raw `SSLSocket` và `SSLEngine`

**Điểm dễ mắc lỗi:** Tạo một raw `SSLSocket` hoặc `SSLEngine` với trust manager chuẩn **không có nghĩa HTTPS hostname checking tự bật**. Các API TLS thấp hơn không biết bạn đang thực hiện giao thức ứng dụng nào hoặc reference hostname nào cần so.

Khi dùng raw `SSLSocket` như HTTPS client và muốn áp dụng thuật toán endpoint identification của HTTPS:

~~~java
SSLSocket socket = /* client socket được kết nối đúng peer */;
SSLParameters parameters = socket.getSSLParameters();
parameters.setEndpointIdentificationAlgorithm("HTTPS");
socket.setSSLParameters(parameters);
socket.startHandshake();
~~~

**Đây chỉ là trích đoạn**, không phải class độc lập. Với `SSLEngine` cũng cần cấu hình `SSLParameters` phù hợp trước handshake, đồng thời bảo đảm engine có đúng peer host và peer port theo API.

`"HTTPS"` là tên endpoint-identification algorithm của JSSE, không phải chỉ định để `Cipher.getInstance` và không phải HTTP request header. Tên tham chiếu phải lấy từ **đích đáng tin mà ứng dụng đã chọn**, không từ một giá trị do server gửi ngược lại.

### 5.4. `TrustManagerFactory` khác `HostnameVerifier`

| API | Vai trò chính | Không nên hiểu nhầm |
| --- | --- | --- |
| `TrustManagerFactory.init(trustStore)` | Tạo trust manager trên tập trust anchors | **Không tự chọn tên hostname** của URL ở ứng dụng tùy ý |
| `X509TrustManager.checkServerTrusted` | API kiểm chứng trust/cert chain theo provider và ngữ cảnh; bản interface cơ bản không nhận hostname | Không tự viết “custom check chỉ path” rồi coi là hostname check |
| `X509ExtendedTrustManager` | Trust check có phiên bản theo socket/engine; có thể kiểm endpoint identity khi JSSE được cấu hình | Không được bypass bằng override tùy tiện |
| `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")` | Yêu cầu JSSE kiểm endpoint name theo HTTPS khi handshake | Không tự thêm trust anchor đang thiếu |
| `HttpsURLConnection` | HTTPS stack có hostname verification theo mặc định | Custom `HostnameVerifier` trả true là phá chính sách |
| `CertPathValidator("PKIX")` | Đánh giá certificate path theo policy | Không biết reference DNS/IP từ URL nếu không được bổ sung ở tầng phù hợp |

Một số triển khai JSSE đặt name check trong quá trình kiểm tin cậy theo ngữ cảnh socket/engine; phép kiểm có thể đi chung call stack với chain validation. **Về mặt an ninh chúng vẫn là hai điều kiện riêng**, không phải “một lần gọi hàm là chứng chỉ tự nhiên đúng hostname”.

### 5.5. `KeyManagerFactory` không thay `TrustManagerFactory`

- Server identity keystore chứa **PrivateKeyEntry** với private key + leaf/chain, để server chứng minh mình có private key tương ứng certificate.
- Client truststore chứa root/intermediate CA được chủ động chấp nhận cho path trust.
- `KeyManagerFactory` giúp server/client mTLS xuất trình identity của chính nó.
- `TrustManagerFactory` giúp bên **đang xác minh peer** đánh giá chứng thư đối tác.
- Reference hostname được xác định theo **đường kết nối**/endpoint, không theo alias hoặc tên CN trong server keystore.

Một file PKCS12 có thể chứa nhiều entry types nhưng **trách nhiệm và chính sách** của identity/trust vẫn phải tách bạch.

## 6. Chuẩn bị phòng thực hành: một CA được tin, một SAN DNS

Để thấy rõ phép A và B khác nhau, ta dùng một CA demo **tự tạo** (được client chủ động tin) và chứng thư server do CA đó ký với SAN chỉ gồm **`DNS:localhost`**.

Sau đó kết nối **cùng một server** qua loopback TCP `127.0.0.1`, nhưng đưa hai **reference identities khác nhau** cho TLS:

- `localhost` với `HTTPS` endpoint identification → **thành công**.
- `127.0.0.1` với `HTTPS` endpoint identification → **thất bại**, vì chứng thư **không có `IP:127.0.0.1`**.
- `127.0.0.1` **không bật** hostname identification → **thành công về chain**, nhưng **không an toàn để xác thực đúng đích**; chỉ là negative control trong bài lab.

**Cần:** JDK 17+, `keytool` và quyền tạo file trong thư mục thử nghiệm mới. **Không dùng keystore production**. Các lệnh bên dưới dùng password cố định mẫu trên command line để dễ tái hiện; với production password phải lấy từ secret manager/callback, không đặt trong Git, CI log, shell history hoặc process listing.

### 6.1. Tạo root CA local

~~~bash
keytool -genkeypair -alias demo-root -keyalg RSA -keysize 2048 -dname "CN=Local Demo Root,O=Learning,C=VN" -validity 7 -ext "bc=ca:true" -ext "ku=keyCertSign,cRLSign" -keystore ca.p12 -storetype PKCS12 -storepass demo-pass-123 -keypass demo-pass-123 -noprompt
~~~

CA demo có `BasicConstraints CA=true` và `KeyUsage keyCertSign`; đây **không phải** chứng thư được trình duyệt công cộng tin. RSA 2048 và hạn 7 ngày chỉ phục vụ lab; root CA production phải theo chính sách, bảo vệ private key CA (thường HSM) và có quy trình cấp/thu hồi chứng thư.

### 6.2. Xuất root CA certificate công khai

~~~bash
keytool -exportcert -alias demo-root -keystore ca.p12 -storepass demo-pass-123 -rfc -file root.crt
~~~

`root.crt` là certificate public, **không có private key CA**. Đối với CA thật, xác minh certificate fingerprint và provenance qua kênh tin cậy độc lập trước khi import.

### 6.3. Tạo private-key identity cho server

~~~bash
keytool -genkeypair -alias server-id -keyalg RSA -keysize 2048 -dname "CN=localhost,O=Learning,C=VN" -validity 7 -ext "san=dns:localhost" -keystore server.p12 -storetype PKCS12 -storepass demo-pass-123 -keypass demo-pass-123 -noprompt
~~~

Ở bước này `server-id` chứa private key và self-signed placeholder. SAN DNS `localhost` là **điều cần so trong lab**; không có `IP:127.0.0.1`.

### 6.4. Tạo CSR từ đúng alias và CA ký leaf

~~~bash
keytool -certreq -alias server-id -file server.csr -rfc -keystore server.p12 -storepass demo-pass-123 -ext "san=dns:localhost"
~~~

~~~bash
keytool -gencert -alias demo-root -infile server.csr -outfile server-issued.crt -rfc -keystore ca.p12 -storepass demo-pass-123 -validity 7 -ext "san=dns:localhost" -ext "ku=digitalSignature,keyEncipherment" -ext "eku=serverAuth"
~~~

CSR PKCS#10 chứa public key, subject và requested extensions; private key **không được chuyển cho CA**. Lệnh `-gencert` tạo leaf thực tế với SAN DNS `localhost`, KU/EKU phù hợp lab. CA production phải kiểm quyền tên và chính sách cấp; không tự động ký mọi SAN mà CSR yêu cầu.

### 6.5. Cài CA certificate và phản hồi vào server KeyStore

~~~bash
keytool -importcert -alias demo-root -file root.crt -keystore server.p12 -storepass demo-pass-123 -noprompt
~~~

~~~bash
keytool -importcert -alias server-id -file server-issued.crt -keystore server.p12 -storepass demo-pass-123 -noprompt
~~~

Lệnh thứ hai **cài certificate reply vào đúng PrivateKeyEntry alias `server-id`**. Private key vẫn ở server.p12; leaf/chain được cập nhật thành CA-issued certificate có chain về demo root.

### 6.6. Tạo client truststore chỉ chứa root certificate demo

~~~bash
keytool -importcert -alias demo-root -file root.crt -keystore trust.p12 -storetype PKCS12 -storepass demo-pass-123 -noprompt
~~~

`trust.p12` được client sử dụng để tin root của lab. **Không** đưa private key CA trong `ca.p12` sang client. Truststore được tạo bằng certificate đã có từ môi trường thực hành tin cậy; không tự thực hiện `-noprompt` cho root bất kỳ tải từ mạng.

### 6.7. Kiểm metadata trước khi chạy Java

~~~bash
keytool -list -v -alias server-id -keystore server.p12 -storetype PKCS12 -storepass demo-pass-123
keytool -list -v -alias demo-root -keystore trust.p12 -storetype PKCS12 -storepass demo-pass-123
~~~

Cần nhìn thấy:

- `server-id` là `PrivateKeyEntry`, leaf SAN `DNSName: localhost`, issuer demo root và chain phù hợp.
- `demo-root` trong trust.p12 là `TrustedCertificateEntry`, không có private key CA.
- Leaf chỉ chứa **DNS SAN localhost**, không có **IP SAN 127.0.0.1**. Đây là **chủ ý** để test mismatch.

## 7. Java 17+ hoàn chỉnh: cùng CA tin cậy, khác kết quả tên

Lưu chương trình sau thành **`TlsNameCheckDemo.java`** trong cùng thư mục với `server.p12` và `trust.p12`. Chương trình chạy local loopback, không kết nối Internet.

### 7.1. Code

~~~java
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

public class TlsNameCheckDemo {
    static KeyStore load(String filename, char[] password) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(Path.of(filename))) {
            ks.load(in, password);
        }
        return ks;
    }

    static SSLContext serverContext(KeyStore server, char[] password)
            throws Exception {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(server, password);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, new SecureRandom());
        return ctx;
    }

    static SSLContext clientContext(KeyStore trusted) throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trusted);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, tmf.getTrustManagers(), new SecureRandom());
        return ctx;
    }

    static boolean connect(SSLContext client, int port,
                           String peerName, boolean hostnameCheck)
            throws Exception {
        // Kết nối vật lý luôn là 127.0.0.1; peerName thể hiện
        // DANH TÍNH LOGIC cần được chứng thư xác thực.
        Socket tcp = new Socket(InetAddress.getByName("127.0.0.1"), port);
        try (SSLSocket ssl = (SSLSocket) client.getSocketFactory()
                .createSocket(tcp, peerName, port, true)) {
            SSLParameters parameters = ssl.getSSLParameters();
            if (hostnameCheck) {
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
            }
            ssl.setSSLParameters(parameters);
            ssl.startHandshake();
            return true;
        }
    }

    public static void main(String[] args) throws Exception {
        char[] password = "demo-pass-123".toCharArray();
        try {
            KeyStore server = load("server.p12", password);
            KeyStore trust = load("trust.p12", password);
            X509Certificate leaf = (X509Certificate)
                    server.getCertificate("server-id");
            X509Certificate root = (X509Certificate)
                    trust.getCertificate("demo-root");

            // PATH TRUST ĐỘC LẬP: kiểm leaf tới root được cấu hình.
            // Root là trust anchor, không đưa vào CertPath list.
            CertPath path = CertificateFactory.getInstance("X.509")
                    .generateCertPath(List.of(leaf));
            PKIXParameters pkix = new PKIXParameters(
                    Set.of(new TrustAnchor(root, null)));
            pkix.setRevocationEnabled(false); // Offline demonstration only!
            CertPathValidator.getInstance("PKIX").validate(path, pkix);
            System.out.println("PKIX trusted        = true");

            SSLContext serverTls = serverContext(server, password);
            SSLContext clientTls = clientContext(trust);
            try (SSLServerSocket listener = (SSLServerSocket)
                    serverTls.getServerSocketFactory().createServerSocket(
                            0, 10, InetAddress.getByName("127.0.0.1"))) {
                Thread responder = new Thread(() -> {
                    for (int i = 0; i < 3; i++) {
                        try (SSLSocket accepted =
                                (SSLSocket) listener.accept()) {
                            accepted.startHandshake();
                        } catch (Exception ignored) {
                            // Demo: client abort on hostname mismatch is expected.
                        }
                    }
                });
                responder.start();
                int port = listener.getLocalPort();

                System.out.println("HTTPS localhost     = " +
                        connect(clientTls, port, "localhost", true));

                boolean mismatchRejected = false;
                try {
                    connect(clientTls, port, "127.0.0.1", true);
                } catch (SSLHandshakeException expected) {
                    mismatchRejected = true;
                }
                System.out.println("HTTPS IP rejected   = " +
                        mismatchRejected);

                // DELIBERATELY UNSAFE: trust chain vẫn được kiểm,
                // nhưng endpoint name verification bị bỏ qua.
                System.out.println("Chain-only IP       = " +
                        connect(clientTls, port, "127.0.0.1", false));
                responder.join();
            }
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
~~~

### 7.2. Biên dịch và chạy

~~~bash
javac TlsNameCheckDemo.java
java TlsNameCheckDemo
~~~

Đầu ra đã được kiểm chứng sau khi chạy đúng các lệnh `keytool` ở phần 6 trong **thư mục thử nghiệm rỗng**:

~~~text
PKIX trusted        = true
HTTPS localhost     = true
HTTPS IP rejected   = true
Chain-only IP       = true
~~~

**Diễn giải đúng các dòng output:**

1. `PKIX trusted = true`: leaf `server-id` được CA demo trong `trust.p12` ký và `CertPathValidator` chấp nhận path **trong điều kiện lab**. Lời gọi này **không kiểm hostname** và tắt revocation có chủ đích vì CA demo không có CRL/OCSP.
2. `HTTPS localhost = true`: client TCP tới loopback `127.0.0.1` nhưng **reference name** của TLS là `localhost`; leaf SAN `DNS:localhost` khớp; trust chain cũng hợp lệ, nên handshake thành công.
3. `HTTPS IP rejected = true`: client vẫn dùng **chính CA root ấy, chính server ấy, cùng TLS context**, nhưng reference name là IP literal `127.0.0.1`. Certificate chỉ có **DNS SAN**, không có `iPAddress SAN` đúng. JSSE từ chối qua `SSLHandshakeException` khi endpoint algorithm `HTTPS` được bật.
4. `Chain-only IP = true`: cùng IP và truststore nhưng **cố tình bỏ hostname check**, handshake thành công. **Dòng này mô phỏng anti-pattern để học**, không có nghĩa “an toàn”; nó chứng minh chain validation **không tự bảo đảm đúng endpoint**.

**Bí quyết của demo:** `createSocket(tcp, peerName, port, true)` giữ TCP connection tới `127.0.0.1` nhưng đưa tên logic `peerName` cho TLS khi kiểm endpoint identity. Việc **đổi tên logic** chỉ là bài kiểm khác biệt, **không phải cho ứng dụng tự tùy ý chọn tên mà server gợi ý**. Trong thực tế tên phải đến từ URL/config đã xác thực.

**Chương trình không in private key**; password demo được xóa ở mảng `char[]` do chương trình nắm giữ, nhưng literal/password trong dòng lệnh hoặc object của provider vẫn có thể để lại bản sao trong RAM/log. Không coi code sample là phương án lưu password production.

### 7.3. Tại sao chương trình không thực hiện HTTPS request thật?

`SSLServerSocket` chỉ tạo **local TLS handshake**, không triển khai HTTP parser hay gửi response; điều ta cần chứng minh là hai điều kiện xác thực ngay tại lớp TLS.

Một HTTPS client cấp cao có thể cũng làm URL parsing, redirects, hostname validation, connection reuse, proxy và nhiều cơ chế HTTP khác. Demo không mô phỏng toàn bộ những điều đó. Nó chỉ cho thấy sự độc lập giữa **PKIX trust** và **endpoint identification** bằng API JSSE chuẩn.

Ở nhánh mô phỏng không bật hostname check, server vẫn trình certificate có chữ ký CA, và `TrustManagerFactory` của client vẫn hoạt động. Do đó kết quả `Chain-only IP=true` là bằng chứng trực quan rằng **tin CA chưa đủ**.

### 7.4. Giới hạn an ninh của lab

- CA root và server cert được tạo trong môi trường bạn kiểm soát; không phải CA/public PKI thật.
- `PKIXParameters.setRevocationEnabled(false)` được đặt **chỉ để thử offline**. Khi triển khai thật, revocation policy và cách fail cần được quy định.
- Certificate SAN chỉ có localhost, thời hạn rất ngắn, password mẫu cố định; không phù hợp public HTTPS.
- `SSLSocket` không phải complete HTTPS/mTLS client; không chứng minh xử lý HTTP redirects, cookies, proxy, authorization hay session cache.
- Ngoài tên và chain, client vẫn cần thuật toán TLS mạnh, private key bảo mật, certificate usage phù hợp và chính sách hạn dùng/thu hồi.

## 8. Cách cấu hình đúng khi CA nội bộ và hostname không khớp

Giả sử công ty có CA nội bộ cấp chứng thư:

~~~text
Target URL: https://orders.internal.example
Server SAN: DNS:orders.internal.example
CA:        Company Issuing CA -> Company Root CA
~~~

Client phải thực hiện cả hai:

1. **Trust chain:** Nhận CA root phù hợp từ kênh quản trị đáng tin, kiểm fingerprint/chuỗi tin cậy, đưa vào client truststore hoặc cấu hình trust manager tương ứng; bảo đảm intermediate chain đầy đủ.
2. **Name match:** Truy cập qua `orders.internal.example` (đúng reference identity), certificate leaf có SAN phù hợp; giữ endpoint identification bật.

Nếu bạn cần truy cập qua IP trực tiếp, có hai lựa chọn thiết kế rõ ràng:

- **Dùng DNS name thực sự của endpoint** và để DNS/TCP route tới IP cần thiết; reference identity vẫn là hostname này.
- Nếu endpoint thật được định danh chính thức bằng IP literal, **phát hành certificate với iPAddress SAN đúng IP** và cấu hình ứng dụng kết nối theo IP đó, với trust chain hợp lệ.

**Không sửa bằng** `HostnameVerifier((host, session) -> true)`, `X509TrustManager.checkServerTrusted` rỗng, bật trust-all, hoặc chấp nhận tất cả hostname chỉ vì môi trường `dev`. Các cấu hình này thường bị copy vào staging/production và có thể dẫn tới MITM.

### 8.1. SNI không phải hostname verification

**Server Name Indication (SNI)** là extension trong TLS ClientHello để client báo hostname mong muốn, giúp một server có nhiều virtual hosts chọn certificate phù hợp.

- SNI hỗ trợ **server chọn** leaf certificate.
- **Hostname verification** là việc **client xác minh** certificate được chọn có tên khớp với endpoint mà client yêu cầu.
- Server thấy SNI đúng không đảm bảo nó trả chứng thư đúng; client vẫn phải kiểm chain và SAN.
- Với IP literal, hành vi gửi SNI khác nhau tùy API/triển khai; đừng dựa vào SNI để “cứu” chứng thư không có IP SAN.

SNI là tín hiệu định tuyến/chọn certificate, **không phải bằng chứng mật mã về danh tính server**.

### 8.2. DNS lookup, connection by IP và tên logic

Ứng dụng có thể resolve `api.example.com` sang `10.0.0.8` và mở TCP tới IP, nhưng reference identity vẫn `api.example.com`. Đây là mô hình bình thường.

Ngược lại, nếu ứng dụng chỉ dùng `https://10.0.0.8` và certificate chỉ có `DNS:api.example.com`, **không nên bỏ hostname check**; phải sửa cấu hình đích, cấp SAN IP đúng chính sách hoặc dùng DNS route.

**Cẩn thận:** Hostname trong HTTP `Host` header không tự cung cấp trust khi TLS handshake đã hoàn tất; attacker kiểm soát HTTP layer không được quyền “đổi” reference identity để hợp thức hóa cert đã được trình.

### 8.3. Redirects và connection reuse

HTTPS client có thể gặp redirect `Location: https://another.example.com/...`. Mỗi endpoint HTTPS mà client thực sự kết nối phải được xác minh với **hostname của endpoint đó** và trust chain hợp lệ.

Không lấy certificate cũ của endpoint A dùng để tin endpoint B mà bỏ hostname check. Với HTTP/2 connection coalescing hoặc pooling, client cấp cao phải thực hiện kiểm điều kiện bảo mật của các origins theo chuẩn/implementation; ứng dụng không tự bypass bằng cách chia sẻ raw socket không kiểm tên.

## 9. Phân biệt thông báo lỗi trong Java

Lỗi trust chain và lỗi hostname có thể cùng xuất hiện dưới `SSLHandshakeException` ở tầng ngoài, nhưng nguyên nhân sâu thường khác.

| Thông báo/triệu chứng điển hình | Kiểm tra đang thất bại | Cách khắc phục phù hợp |
| --- | --- | --- |
| `PKIX path building failed` | Không dựng được chain tới trust anchor chấp nhận | Kiểm truststore, root/issuer/intermediate, chain server gửi, policy |
| `unable to find valid certification path` | Không có path phù hợp trust policy | Đưa CA đúng nguồn vào truststore, cài đầy đủ intermediate |
| `No subject alternative DNS name matching ... found` | SAN DNS không khớp hostname | Cấp cert có DNS SAN đúng, hoặc sửa endpoint URL/config đúng |
| `No subject alternative names matching IP address ... found` | Thiếu `iPAddress SAN` khớp IP literal | Cấp IP SAN đúng khi dùng IP identity, hoặc dùng DNS endpoint chuẩn |
| `certificate expired` / `not yet valid` | NotAfter/NotBefore hoặc clock | Gia hạn certificate/điều chỉnh clock sau khi kiểm nguồn |
| `Extended key usage does not permit ...` | EKU không phù hợp mục đích TLS | CA phát hành leaf có EKU đúng profile |
| `Algorithm constraints check failed` | Khóa/signature/cipher bị policy/JDK từ chối | Cấp lại cert/key đạt policy, không tắt constraint bừa |
| `certificate revoked` | Revocation checker theo chính sách từ chối | Điều tra CA/revocation, xử lý key compromise theo quy trình |
| TLS handshake thành công nhưng client raw socket gọi sai hostname | Có thể **endpoint identification chưa bật** | Kiểm `SSLParameters`/client TLS stack và identity reference |
| Hết lỗi sau khi set `HostnameVerifier` luôn true | **Đã vô hiệu hóa name check**, không phải sửa cert | Bỏ bypass, sửa SAN/trust/URL theo nguyên nhân |
| Hết lỗi sau trust-all manager | **Đã vô hiệu hóa CA/path trust**, không phải sửa chain | Khôi phục trust manager chuẩn, cấp trust anchors đúng nguồn |

**Đừng bắt mọi `SSLHandshakeException` rồi retry với `SSLParameters` không có endpoint identification.** Một retry “nới lỏng bảo mật” khiến attacker có thể chủ động tạo lỗi đầu tiên rồi khiến lần sau bỏ xác minh. Nếu muốn log nguyên nhân, ghi exception class, đích URL/hostname đã cấu hình, certificate metadata đã lọc phù hợp, truststore version và policy; **không log private keys, passwords, session secrets hoặc token**.

### 9.1. Vì sao lỗi chain đôi khi che khuất lỗi tên?

Nếu server gửi certificate do CA không được trust, handshake có thể thất bại **trước khi đi đến name check**, nên bạn chỉ nhìn thấy lỗi path. Sau khi sửa truststore đúng cách, bạn mới thấy hostname mismatch. Đây không phải “sửa A gây lỗi B” mà là **hai điều kiện đã không được thỏa mãn từ đầu**.

Chiến lược chẩn đoán: xác định reference hostname/IP; kiểm chain và SAN của leaf độc lập; không tắt một kiểm tra để tìm lỗi kia trong hệ thống đang phục vụ request thật.

## 10. Kiểm tra certificate SAN/chain khi điều tra

Các thao tác kỹ thuật hữu ích trong môi trường được phép:

~~~bash
# Đọc leaf và chain đã lưu trong PKCS12:
keytool -list -v -alias server-id -keystore server.p12 -storetype PKCS12 -storepass demo-pass-123

# Đọc root được client trust trong lab:
keytool -list -v -alias demo-root -keystore trust.p12 -storetype PKCS12 -storepass demo-pass-123

# Đọc certificate leaf đã được CA ký:
keytool -printcert -file server-issued.crt -v
~~~

Ở production, tránh đặt `-storepass` trực tiếp trên command line; dùng phương pháp nhập credential an toàn theo công cụ và môi trường. Không dùng `-noprompt` để tự thêm CA root chưa xác minh.

Nếu bạn được phép kiểm server công khai/ngoại tuyến, `openssl s_client` có thể quan sát chain nhưng **phải hiểu flags**: lệnh chỉ in certificates chưa chắc đã thực hiện hostname verification; các flag như `-verify_hostname`/`-verify_ip` cùng trust CA/policy phải được cấu hình rõ nếu cần kiểm tên. Không lấy `Verify return code: 0` trong một phiên s_client chưa cấu hình hostname verification như bằng chứng tất cả HTTPS checks đã pass.

### 10.1. Không dùng `CN` hoặc alias để sửa lỗi SAN

Nếu `keytool -list` cho thấy:

~~~text
Alias: backend-key
Owner: CN=api.example.com
SubjectAlternativeName:
  DNSName: old.example.com
~~~

Không thể coi CN đúng là đủ cho mọi client, đặc biệt khi DNS SAN hiện diện nhưng **không khớp reference hostname**. Cần CA cấp certificate đúng SAN. Alias `backend-key` không phải DNS name của TLS endpoint và không cấp thêm quyền trust.

### 10.2. Kiểm đúng truststore thực sự được client dùng

Java runtime có thể dùng **truststore mặc định** (ví dụ `cacerts` tùy JDK/distribution) hoặc **truststore riêng** qua JSSE config. Việc import root vào một file mà ứng dụng **không sử dụng** không thay đổi trust decision.

Kiểm cấu hình runtime/container thực tế: đúng file/path, type, password, permissions, process restart/reload và client library có dùng SSLContext đó hay không. Đừng sửa global JVM security properties chỉ để giải quyết một endpoint, nếu một `SSLContext` truststore riêng có thể đáp ứng policy và phạm vi hẹp hơn.

**Thêm root vào truststore phải là quyết định bảo mật có kiểm soát**; nếu root bị cấp quyền quá rộng, mọi endpoint leaf do root đó cấp có thể pass path trust, làm hostname verification càng quan trọng.

## 11. HTTPS, mTLS và danh tính ở hai chiều

Trong HTTPS thông thường, **client xác minh server**. Trong mTLS, **server cũng xác minh chứng thư client**.

~~~text
HTTPS:
  Client --> kiểm chain server + hostname server của URL
  Server --> trình private key + certificate chain

mTLS:
  Client --> vẫn kiểm chain server + hostname server
  Server --> kiểm chain client + clientAuth/identity policy
  Application --> ánh xạ identity client sang quyền cụ thể
~~~

Các nguyên tắc:

- mTLS **không miễn cho client** bước hostname verification của server. Nếu client trust CA nhưng bỏ name check, vẫn có nguy cơ kết nối nhầm service hợp lệ khác.
- Server mTLS thường xác minh client certificate chain tới CA được chấp nhận, mục đích `clientAuth` và **identity được cấp quyền** (ví dụ SAN URI/DNS subject theo policy). Server không “xác minh hostname server” khi kiểm client; nó phải xác định và phân quyền **client identity** theo chính sách.
- Một SAN URI workload identity như SPIFFE có quy tắc xác minh/mapping riêng, không được nhầm với phép kiểm HTTPS DNS name.
- Nếu một CA ký nhiều certificate cho nhiều dịch vụ, server cần **authorization policy** để quyết định identity client nào được phép gọi API, không chỉ “do CA công ty ký là cho truy cập tất cả”.

Trong TLS server nhận client cert, chain trusted chưa đồng nghĩa user là admin. Trong TLS client xác minh server, hostname khớp và chain trusted cũng chưa đồng nghĩa response thuộc đúng tenant hoặc có quyền thực hiện tác vụ ứng dụng.

## 12. Certificate cho localhost/dev/staging/production

**Vì sao dev hay tắt hostname checking?** Chứng thư được cấp cho `dev.example.com` nhưng truy cập bằng `localhost`, `127.0.0.1` hoặc IP container nên mismatch.

Cách làm đúng:

- Nếu môi trường dev dùng `https://localhost`, tạo chứng thư dev với **`DNS:localhost`** và CA dev chỉ được trust trong phạm vi dev.
- Nếu dev client truy cập `https://127.0.0.1`, chứng thư cần **`IP:127.0.0.1`**; có `DNS:localhost` không đủ.
- Nếu staging dùng `staging-api.example.test`, cấp chứng thư có SAN phù hợp, không copy certificate `api.example.com` rồi tắt kiểm tên.
- Nếu production có load balancer/ingress, certificate tại **TLS termination point** phải khớp URL hostname mà client gọi. Kết nối LB→backend riêng cũng cần chính sách TLS/xác thực endpoint phù hợp nếu được bảo vệ bằng TLS.
- Khi chạy container, hostname service trong DNS của cluster có thể khác hostname công khai; phải thiết kế rõ **reference identity của từng kết nối** và SAN/CA tương ứng.

**Không chia sẻ CA dev root cho môi trường production** trừ khi có quy trình rõ ràng, vì trust rộng có thể cho phép certificates dev được nhận ở production. Hạn chế CA/truststore theo từng security domain và dùng rotation có kiểm soát.

## 13. Những lỗi bảo mật phổ biến

| Sai lầm | Điều thực sự hỏng | Cách sửa |
| --- | --- | --- |
| “Chuỗi chứng thư được CA đáng tin ký ⇒ đúng server” | Bỏ endpoint name binding | Kiểm SAN với reference hostname/IP |
| “Có `CN=api.example.com` ⇒ mọi client đều chấp nhận” | CN fallback không đủ/không ổn định | CA cấp SAN DNS/IP đúng |
| “DNS:127.0.0.1 giống IP:127.0.0.1” | Sai SAN type | Dùng `iPAddress` SAN cho IP literal |
| “Import root mới rồi hostname lỗi thì tắt name check” | Chấp nhận sai đối tác | Sửa URL/hostname hoặc xin cấp cert đúng SAN |
| “Server gửi root thì client phải trust nó” | Bỏ mô hình trust anchors | Truststore được quản lý từ nguồn tin cậy |
| “CertPathValidator pass là HTTPS verified” | PKIX path không biết URL host | HTTPS hostname identification riêng |
| “Raw SSLSocket với truststore mặc định tự check host” | Có thể không bật endpoint identification | `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")` |
| “Chỉ cần `SSLSession.isValid()` để check identity” | Session còn hiệu lực không chứng minh hostname đúng | Endpoint check lúc handshake/connection policy |
| “Dùng `HostnameVerifier` trả true để vượt lỗi” | Vô hiệu hóa name check | Dùng verifier mặc định, cấp cert phù hợp |
| “Trust-all X509TrustManager cho dev là vô hại” | Vô hiệu hóa path trust, dễ tràn vào production | Tạo CA dev riêng, truststore giới hạn |
| “Hostname lấy từ certificate peer” | Attacker quyết định tên đích thay ứng dụng | Dùng hostname từ URL/config tin cậy |
| “SNI đã đặt đúng nên certificate không cần SAN” | SNI chỉ giúp chọn certificate | Client vẫn xác minh SAN |
| “Kết nối tới IP của api.example.com thì chỉ kiểm IP” | Mất identity DNS dự định | Giữ reference hostname của URL |
| “mTLS có CA nội bộ ⇒ không cần authorization” | Tất cả leaf client cùng CA có thể được chấp nhận | Mapping identity→quyền, EKU/role checks |
| “Bỏ revocation vì path valid rồi” | Certificate bị thu hồi vẫn có thể được dùng | Áp revocation policy đúng threat model |
| “Một máy kiểm thành công ⇒ mọi máy trust” | Truststore/JDK/policy khác nhau | Test từng runtime/client được hỗ trợ |

**Anti-pattern nghiêm trọng:** Không tạo `HostnameVerifier` luôn trả `true`, không cài `X509TrustManager` không kiểm chứng, không retry handshake bằng chế độ “allow insecure”. Đây không phải các bản sửa tạm vô hại mà **vô hiệu hóa xác thực** trong TLS.

## 14. Kiểm thử ứng dụng và CI

Một bộ test tốt cần hai nhóm đầu vào **độc lập**:

### 14.1. Test path trust

1. Leaf do CA được client tin ký, chain đầy đủ ⇒ path pass.
2. Leaf do CA không được tin ký ⇒ path fail.
3. Thiếu intermediate ⇒ path building fail nếu client không có cách dựng chain hợp lệ.
4. Intermediate không có `CA=true`/`keyCertSign` cần thiết ⇒ path fail.
5. Cert expired/not-yet-valid ⇒ fail.
6. Signature/certificate algorithm bị policy cấm ⇒ fail.
7. Revoked certificate ⇒ fail khi revocation policy yêu cầu.

Không làm test bằng cách tắt hostname verification rồi kết luận ứng dụng an toàn; path tests riêng là một tầng kiểm thử kỹ thuật, còn integration test cần bật cả hai điều kiện.

### 14.2. Test endpoint identity

1. Trusted leaf SAN `DNS:localhost`, reference `localhost` ⇒ pass.
2. Cùng leaf và CA, reference `127.0.0.1` nhưng thiếu IP SAN ⇒ fail.
3. Trusted leaf SAN `IP:127.0.0.1`, reference IP đúng ⇒ pass.
4. Trusted leaf SAN `DNS:files.example.com`, reference `payments.example.com` ⇒ fail.
5. Cert chỉ có CN nhưng không có SAN tương ứng ⇒ không chấp nhận dựa vào CN fallback như yêu cầu test production.
6. Wildcard ở cấp sai hoặc hostname ngoài phạm vi wildcard ⇒ fail.
7. Trường hợp IDN/Punycode, case/label và canonicalization ⇒ được xác minh bằng HTTPS library chuẩn.
8. SNI/virtual hosting chọn certificate khác ⇒ endpoint checker bắt được nếu SAN không khớp.

**Một test rất có giá trị:** Cùng CA/truststore, cùng TCP endpoint, chỉ thay **reference identity** ⇒ một trường hợp pass và một trường hợp fail như chương trình phần 7. Nó cho phép phát hiện cấu hình client “trust chain only” trước khi deploy.

### 14.3. Test các HTTPS client thực tế

Ngoài raw socket lab, chạy integration test với API sẽ deploy thật: `HttpsURLConnection`, `HttpClient`, OkHttp/Apache HttpClient, Netty hoặc framework service. Kiểm cấu hình SSLContext, truststore, proxy, redirects, SNI và hostname verification. Nếu framework có option disable hostname verification, CI/policy nên từ chối cấu hình đó ở môi trường có dữ liệu thật.

**Không kiểm bằng mắt qua certificate Subject.** Hãy kiểm handshake với hai reference identities cụ thể, log kết quả pass/fail và không xuất private key/session secrets.

## 15. Câu hỏi tự kiểm tra và đáp án

**Câu 1.** Một leaf có SAN `DNS:billing.example.com`, CA hợp lệ và không hết hạn. Client gọi `https://pay.example.com`. Có được chấp nhận không?

**Đáp án:** **Không.** Path trust có thể pass nhưng reference hostname `pay.example.com` không nằm trong SAN phù hợp. Cần certificate đúng tên hoặc URL/đích đúng chính sách.

**Câu 2.** Leaf SAN đúng `DNS:api.example.com` nhưng certificate tự ký và client không tin root. Có thể chấp nhận vì hostname khớp không?

**Đáp án:** **Không.** Name match không thay phép kiểm chain/trust anchor. Cần CA/truststore phù hợp và xác thực nguồn tin cậy.

**Câu 3.** `DNS:localhost` có hợp lệ khi ứng dụng gọi `https://127.0.0.1` không?

**Đáp án:** **Không** theo HTTPS IP literal matching: cần SAN loại `iPAddress` khớp `127.0.0.1`, hoặc gọi bằng DNS `localhost` nếu đó là tên đích mong muốn.

**Câu 4.** Vì sao `CertPathValidator.getInstance("PKIX").validate(...)` thành công chưa chứng minh đúng server?

**Đáp án:** Nó xác minh certificate path theo trust anchors/chính sách được cấp; không có reference hostname từ URL cho bài toán HTTPS endpoint identification. Cần kiểm SAN/hostname riêng qua HTTPS/JSSE.

**Câu 5.** Một raw `SSLSocket` dùng TrustManagerFactory chuẩn và TLS 1.3, nhưng không gọi `setEndpointIdentificationAlgorithm("HTTPS")`. Có chắc HTTPS hostname đã được kiểm không?

**Đáp án:** Không. Raw socket có thể chỉ kiểm certificate trust chain. Phải cung cấp peer name đúng và bật endpoint identification theo giao thức, hoặc dùng HTTPS client cấp cao đã làm việc này.

**Câu 6.** SNI chứa `api.example.com`; server trả certificate `DNS:other.example.com`. Có thể chấp nhận vì SNI đúng không?

**Đáp án:** Không. SNI chỉ giúp chọn certificate; client vẫn phải xác minh SAN của certificate trả về khớp reference identity.

**Câu 7.** Import một CA root bất kỳ từ email vào truststore để xử lý `PKIX path building failed` có an toàn không?

**Đáp án:** Không. Root/CA phải được xác minh nguồn và fingerprint từ kênh tin cậy; nếu tin CA giả, attacker có thể cấp certificate cho endpoint mục tiêu.

**Câu 8.** mTLS server có chain client certificate hợp lệ từ CA nội bộ. Như vậy mọi client certificate cùng CA được quyền làm admin?

**Đáp án:** Không. Server còn cần mapping certificate identity (và EKU/policy) sang quyền được phép của từng client/service. Trust không tự cấp quyền ứng dụng.

**Câu 9.** Một chứng thư có `CN=api.example.com` nhưng SAN `DNS:old.example.com`. Client gọi `api.example.com` nên xử lý thế nào?

**Đáp án:** Không dựa vào CN để ghi đè SAN đã được phát hành; cần certificate có SAN tương ứng với tên đích. Quy tắc fallback legacy có thể khác nhưng không phải đường triển khai an toàn hiện đại.

**Câu 10.** Java lab in `PKIX trusted = true` và `HTTPS IP rejected = true` có mâu thuẫn không?

**Đáp án:** Không. Phép kiểm CA/path và phép kiểm endpoint name độc lập. Leaf trusted cho `localhost` nhưng **không hợp lệ cho IP literal `127.0.0.1`**.

**Câu 11.** Tại sao hostname không nên lấy từ reverse DNS của IP TCP connection?

**Đáp án:** Vì reference identity phải là đích mà ứng dụng muốn kết nối (URL/config), còn reverse DNS không phải bằng chứng rằng chứng thư được cấp cho chính đích đó; có thể sai hoặc bị kiểm soát theo threat model.

**Câu 12.** Tắt `HostnameVerifier` để “khắc phục” lỗi TLS có thể tạo lỗ hổng gì?

**Đáp án:** Chấp nhận certificate tin cậy nhưng thuộc một hostname khác, dẫn tới kết nối nhầm peer và có nguy cơ MITM. Cần sửa SAN/URL/truststore đúng nguyên nhân, không disable verify.

**Câu 13.** Server có đúng leaf và intermediate, nhưng client vẫn báo untrusted. Nguyên nhân nào còn có thể xảy ra?

**Đáp án:** Truststore client không tin root, algorithm policy không chấp nhận chain, certificate hết hạn, bị thu hồi hoặc ràng buộc PKIX khác thất bại. Server gửi root không tự khiến client chấp nhận nó.

**Câu 14.** Tại sao demo đặt `pkix.setRevocationEnabled(false)` nhưng không nên sao chép điều đó vào production?

**Đáp án:** Demo CA offline không có CRL/OCSP; tắt revocation để bài thử nghiệm có kết quả ổn định, **không phải tiêu chuẩn an toàn mặc định**. Production cần xác định cơ chế revocation và failure policy đúng nghiệp vụ/threat model.

## 16. Checklist cho ứng dụng Java TLS

- [ ] Xác định **reference hostname/IP từ URL/config tin cậy** trước khi handshake, không lấy từ certificate server.
- [ ] Kiểm certificate **path/chain tới root trust anchor được chủ động tin**; không trust-all và không tự thêm root chưa xác minh.
- [ ] Kiểm leaf **SAN đúng type**: DNS host dùng dNSName, IP literal dùng iPAddress; không dùng CN/alias thay SAN.
- [ ] Với raw `SSLSocket`/`SSLEngine`, bật **`SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")`** khi thực hiện HTTPS endpoint identity checks.
- [ ] Với `HttpsURLConnection`/`HttpClient`, giữ hostname verification mặc định; không cài custom verifier luôn true.
- [ ] Đảm bảo SNI/virtual hosting chọn certificate phù hợp **và vẫn kiểm tên** client-side.
- [ ] Kiểm chain đầy đủ/issuer signature, thời hạn, BasicConstraints, KU/EKU, algorithm constraints và revocation policy.
- [ ] Đừng dùng server-sent root làm lý do trust; truststore và CA policy phải có provenance độc lập.
- [ ] Khi hostname mismatch, **cấp lại SAN đúng hoặc sửa endpoint name**; không tắt verify để “hết lỗi”.
- [ ] Khi client truy cập IP literal, dùng IP SAN; khi client truy cập DNS host, giữ DNS reference dù TCP kết nối tới IP resolved.
- [ ] Tách TLS authentication khỏi authorization/token/tenant; với mTLS còn kiểm client identity và quyền.
- [ ] Test cả **trusted wrong-host** và **correct-host untrusted**, cùng kiểm chính sách CA/revocation.
- [ ] Không log password/private key/session secret; quản lý key/truststore/rotation và quan sát lỗi handshake an toàn.
- [ ] Có test trên chính **client API/JDK/framework phiên bản sẽ deploy**, vì raw TLS và HTTPS clients có defaults khác nhau.

## 17. Tổng kết

**Tin chuỗi chứng thư** và **xác minh tên máy chủ** giải quyết **hai câu hỏi khác nhau**. PKIX/trust manager xác định certificate path có dẫn tới trust anchor được chấp nhận, còn hostname verification đối chiếu **tên đích mà client chủ định** với **SAN phù hợp trên leaf**. Có chứng thư từ CA đáng tin chỉ chứng minh danh tính **được CA cấp trong certificate**, không có nghĩa đúng hostname bạn gọi. Trong Java, HTTPS clients cấp cao thường có cơ chế hostname verification mặc định; với raw `SSLSocket/SSLEngine` bạn cần chủ động thiết lập endpoint identification khi dùng cho HTTPS. Ví dụ JDK 17+ cho thấy rõ: **PKIX pass, localhost pass, IP mismatch bị từ chối, nhưng chain-only bỏ name check lại pass và không an toàn**.

Tài liệu nền tảng để đọc sâu: [RFC 5280 — PKIX Certificate and CRL Profile](https://www.rfc-editor.org/rfc/rfc5280), [RFC 6125 — service identity verification](https://www.rfc-editor.org/rfc/rfc6125), [RFC 9525 — Service Identity in TLS](https://www.rfc-editor.org/rfc/rfc9525), và [Java 17 JSSE Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-secure-socket-extension-jsse-reference-guide.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tls-foundation-007">15.7 Session, tái sử dụng phiên và vòng đời kết nối</a>

<details>
<summary>Xem chi tiết</summary>

# 15.7. Session, tái sử dụng phiên và vòng đời kết nối

**Một HTTPS request mới không nhất thiết tạo một TLS connection mới. Một TLS connection mới cũng không nhất thiết làm một full handshake từ đầu.** Hai cơ chế hiệu năng thường bị gọi lẫn là **connection reuse (tái sử dụng kết nối đã mở)** và **TLS session resumption (tiếp tục một phiên bảo mật trước trên kết nối mới)**. Cùng có thể giúp giảm độ trễ, nhưng chúng vận hành ở các tầng khác nhau, yêu cầu trạng thái khác nhau và có hệ quả bảo mật khác nhau.

Trong Java, sự nhầm lẫn còn xuất hiện do các lớp `SSLContext`, `SSLSession`, `SSLSessionContext`, `SSLSocket`, `SSLEngine`, `HttpClient` và cả connection pool có vòng đời riêng. Chẳng hạn `SSLSession.isValid() == true` **không chứng minh TCP socket đang mở**; `SSLSession.invalidate()` **không có nghĩa đóng ngay mọi socket**; và hai request đi cùng một kết nối HTTP/2 **không phải** hai lần TLS session resumption.

Bài này giải thích ba lớp vòng đời: **transport connection**, **TLS secure connection/handshake/session state**, và **application/HTTP request**. Sau đó phân biệt TLS 1.2 session IDs/tickets với TLS 1.3 PSK resumption, cách Java expose session cache, session timeout, ticket expiration, 0-RTT, replay và những giới hạn cần nhớ khi kiểm tra production.

> **Lưu ý tiêu chuẩn:** Tính đến tháng 10/2026, đặc tả TLS 1.3 hiện hành là **RFC 9846 (xuất bản tháng 7/2026)**, thay thế RFC 8446 với những cập nhật làm rõ và siết chặt yêu cầu. Nhiều thư viện và tài liệu Java 17 được viết dựa trên RFC 8446; mô hình session resumption vẫn tương thích, nhưng khi thiết kế mới nên tham chiếu RFC 9846 và yêu cầu của phiên bản JDK/provider thực tế.

## 1. Mục tiêu bài học

Sau khi học, bạn có thể:

- Phân biệt **TLS connection**, **TLS session/resumption state**, **TCP socket**, **HTTP keep-alive/connection pooling**, và **HTTP/2 multiplexing**.
- Giải thích tại sao cùng một `SSLContext` có thể duy trì session cache qua nhiều kết nối, còn một `SSLSocket` chỉ đại diện cho một kết nối cụ thể.
- Mô tả full handshake và resumed handshake ở TLS 1.2/TLS 1.3, gồm `NewSessionTicket`, PSK identity và PSK binder.
- Phân biệt **1-RTT PSK resumption** với **0-RTT early data**, biết vì sao 0-RTT có nguy cơ replay và có các giới hạn forward secrecy.
- Đọc đúng `SSLSession.getProtocol()`, `getCipherSuite()`, `getCreationTime()`, `getLastAccessedTime()`, `getSessionContext()`, `isValid()` và `invalidate()`.
- Cấu hình cơ bản `SSLSessionContext.setSessionTimeout()` và `setSessionCacheSize()` trong Java 17+, đồng thời hiểu rằng điều đó **không ép server chấp nhận resumption**.
- Mô tả kết thúc kết nối, đóng TLS/HTTP, idle timeout, ticket expiration, server restart/rotation và tải cân bằng.
- Xây dựng tiêu chí kiểm thử thật sự xác định được full/resumed handshake; không kết luận chỉ từ timing, HTTP status hoặc session ID.

## 2. Các khái niệm phải tách biệt

| Khái niệm | Cái gì tồn tại? | Có thể sống qua lần request sau? | Điều **không** suy ra |
| --- | --- | --- | --- |
| TCP connection | Luồng byte giữa client/server qua socket (TLS over TCP) | Có, nếu còn mở và application cho tái dùng | Không nói handshake đã resumption hay chưa |
| TLS connection | Trạng thái record-layer bảo mật, keys và sequence numbers cho một kết nối | Có, trong chính kết nối còn sống | Không mặc định chuyển nguyên traffic keys sang socket mới |
| TLS handshake | Quá trình thương lượng thông số, key agreement, authentication | Thường một lần ban đầu cho kết nối, có các trường hợp cập nhật theo protocol | Không phải “mỗi HTTP request một handshake” |
| TLS session/resumption state | Thông tin cho phép nối tiếp một sự xác thực/kết nối đã thực hiện trên **kết nối mới** | Có nếu cache/ticket/policy còn hợp lệ | Không bằng một TCP socket |
| TLS session ticket | Vật mang/định danh cho resumption state hoặc PSK theo phiên bản TLS | Có theo lifetime/chính sách | Không phải password, không phải token ứng dụng |
| HTTP request/response | Một thao tác nghiệp vụ của client | Mỗi thao tác có vòng đời riêng | Không tự sinh session TLS mới |
| HTTP persistent connection | HTTP/1.1 keep-alive hoặc HTTP/2 connection còn mở | Nhiều requests dùng cùng connection | **Không** phải TLS resumption |
| Connection pool | Bộ quản lý nhiều kết nối tới origin/route | Có trong thời gian pool giữ kết nối | Không bảo đảm server vẫn chấp nhận TLS tickets |
| Session cookie/token ứng dụng | Trạng thái đăng nhập của user/app | Có qua nhiều kết nối TLS | Không phải `SSLSession` hay TLS session ticket |

Một sơ đồ ba lớp:

```text
ỨNG DỤNG (HTTPS)
    Request #1  Request #2  Request #3
          \        |        /
           \       |       /
       HTTP/2 streams hoặc HTTP/1.1 keep-alive
                     |
        Một TLS connection đang mở
                     |
             Một TCP connection
                     |
           Socket bị đóng sau idle / EOF
                     |
            Lần sau mở socket mới
                     |
          Full hoặc resumed TLS handshake
                     |
               Request #4
```

**Điểm nhớ:** nếu Request #1 và Request #2 được gửi trên **cùng socket/TLS connection**, bạn đang quan sát **connection reuse**, không phải hai lần resumption. Nếu một socket mới được mở và handshake sử dụng trạng thái xác thực từ trước (ticket/session), đó mới là **TLS session resumption**.

### 2.1. Session không phải HTTP login session

Một người dùng có thể đăng nhập bằng cookie hoặc OAuth token trên một TLS connection, sau đó kết nối mạng được thay mới. HTTP login session có thể vẫn tiếp tục nếu cookie/token còn hiệu lực. Ngược lại, một TLS session có thể được resume giữa các TCP connections mà application-level login đã hết hạn.

Các vòng đời phải độc lập:
- **TLS:** cung cấp kênh mã hóa/xác thực server (và client khi dùng mTLS), phụ thuộc handshake và keys.
- **Authentication ứng dụng:** session cookie, bearer token, mTLS principal mapping hoặc credential.
- **Authorization:** xác định principal có quyền truy cập tài nguyên và hành động hay không, kiểm tra theo chính sách.
- **Business idempotency/replay:** yêu cầu giao dịch lặp được xử lý thế nào.

Không gắn “TLS session còn sống” với “user chắc chắn còn đăng nhập và có quyền như cũ”.

## 3. Full handshake và sử dụng lại kết nối

### 3.1. Full handshake trên một kết nối mới

Ở TLS 1.3, một full certificate-based handshake điển hình (mô tả đơn giản):

```text
Client                                                Server
  |                                                    |
  | --- ClientHello (+ key_share, versions, suites) --> |
  | <-- ServerHello (+ selected key_share) ------------ |
  | <-- EncryptedExtensions / Certificate ------------ |
  | <-- CertificateVerify / Finished ------------------ |
  | --- Finished -------------------------------------> |
  | <============== Application Data =================> |
  |                                                    |
   TCP/TLS connection tiếp tục mang các HTTP requests.
```

Trong full handshake, server thường phải chứng minh identity qua certificate và bằng chứng private key trong handshake. TLS 1.3 key schedule dẫn xuất traffic secrets theo transcript; record-layer keys có phạm vi kết nối và có thể được cập nhật theo protocol.

### 3.2. Tái sử dụng connection

Sau full handshake, client gửi nhiều HTTP requests qua connection còn mở:

```text
TCP connect + TLS full handshake
        |
     HTTP #1 ------- response #1
        |
     HTTP #2 ------- response #2
        |
     HTTP #3 ------- response #3
        |
     Connection idle / closed
```

Với HTTP/1.1 keep-alive, có thể xử lý nhiều request tuần tự trên cùng kết nối theo cơ chế client/server; với HTTP/2, nhiều streams có thể hoạt động đồng thời trên một connection. Trường hợp này không cần một handshake mới giữa Request #1 và #2.

**Không đánh đồng:** connection reuse thường tiết kiệm cả TCP setup và TLS handshake. TLS resumption chỉ giúp **rút gọn handshake khi một transport connection mới vẫn phải mở**, chứ bản thân nó không biến socket mới thành socket cũ.

## 4. TLS 1.2: session ID và session tickets

TLS 1.2 lịch sử có cơ chế full handshake và abbreviated handshake (resumption). Hai biến thể tiêu biểu:

### 4.1. Session ID + server-side session cache

```text
Lần đầu:
    ClientHello --------------> Server
    Full handshake <----------> Certificate/key exchange...
    Server caches session state with session ID
    Client remembers session ID + relevant secrets

Lần sau (new TCP connection):
    ClientHello(session_id) --> Server
    Server finds acceptable cached state?
       yes -> abbreviated handshake -> new connection keys
       no  -> full handshake (or handshake failure by policy)
```

Ưu điểm: giảm chi phí của một số thao tác full handshake. Hạn chế: server cần giữ/tra lại session state phù hợp; load balancer nhiều node có thể phải dùng sticky routing hoặc chia sẻ cache để tăng tỉ lệ resume.

### 4.2. TLS 1.2 session ticket

Cơ chế ticket từng được chuẩn hóa theo RFC 5077 (một tài liệu lịch sử được RFC 9846 thay thế). Server có thể mã hóa/xác thực session state thành opaque ticket cho client giữ. Khi reconnect, client đưa ticket; server có key phù hợp để phục hồi state mà không nhất thiết lưu mỗi session ở database.

Cần nhớ:
- Ticket không làm mất nhu cầu **bảo vệ server-side ticket-encryption key** và vòng đời rotation.
- Key rotation/không chia sẻ ticket keys qua các node có thể làm resumption thất bại và fallback full handshake.
- Chỉ “có ticket” không bắt buộc server phải chấp nhận; còn timeout, cipher suite, scope và policy.
- Không tạo cơ chế tự chế để lưu/giải mã ticket ở application thay cho TLS provider.

### 4.3. Học TLS 1.2 để hiểu, không tự thiết kế giao thức cũ

Các deployment hiện đại ưu tiên TLS 1.3 khi khả dụng; RFC 9846 đã cập nhật yêu cầu và loại bỏ khả năng thương lượng TLS 1.0/1.1. Khi tích hợp hệ thống cũ, cần cấu hình protocol allowlist đúng chính sách, không bật các phiên bản yếu chỉ để tăng tỷ lệ resumption.

TLS 1.2 session resumption và TLS 1.3 PSK resumption **không dùng cùng cú pháp handshake**, dù `SSLSession` của Java là một abstraction áp dụng cho nhiều phiên bản.

## 5. TLS 1.3: resumption bằng PSK và NewSessionTicket

Theo **RFC 9846**, TLS 1.3 gộp khái niệm session resumption vào mô hình **pre-shared key (PSK)**. PSK cho resumption thường được dẫn xuất từ bí mật của phiên trước và được liên hệ với một **ticket** do server phát sau handshake.

### 5.1. Khi nào có ticket?

```text
Full TLS 1.3 handshake
       |
       v
Server authenticates / Finished
       |
       v
Application connection ready
       |
       +--> Server MAY issue NewSessionTicket
                  |
                  +--> Client stores ticket/associated resumption state
                               |
                               v
                     Later: another TCP connection
                               |
                               v
                        ClientHello offers PSK identity
                               |
                     Server MAY accept or decline
```

Các từ **MAY** quan trọng:
- Server **có thể** phát nhiều tickets, một ticket hoặc không phát.
- Client **có thể** không lưu hoặc không offer ticket.
- Server **có thể** từ chối resume dù ticket được client offer.
- Vì `NewSessionTicket` có thể được gửi **sau khi handshake chính kết thúc**, client đóng connection quá nhanh có thể chưa nhận đủ các message cần cho resumption. Tùy framework/provider, việc đọc, cache và xử lý post-handshake messages có thể ảnh hưởng kết quả.
- Ticket có lifetime và giới hạn chính sách; theo RFC 9846, không được cache ticket vô thời hạn chỉ vì application vẫn hoạt động.

### 5.2. Handshake mới với PSK

```text
Client đã có ticket/PSK từ phiên trước        Server giữ được policy/ticket keys
                |                                           |
new TCP connection                             new TCP connection
                |                                           |
ClientHello + PSK identity + binder ---------> |
                |                        Check PSK/ticket age/scope
                |                        and selected handshake mode
                | <---------------- ServerHello + accepts PSK
                |                   (may also use fresh DHE key_share)
                | <---------------- EncryptedExtensions / Finished
Client Finished ------------------------------> |
                |                                           |
          New record-layer traffic keys for this connection
                |                                           |
              Application data over the new TLS connection
```

Trong resumption TLS 1.3, không cần lặp lại cùng một certificate authentication exchange như full certificate-based handshake; server authentication có thể dựa trên PSK được liên hệ từ kết nối trước. **Không được hiểu rằng client không cần kiểm tra tên máy chủ nữa**: implementation phải ràng buộc việc dùng PSK với identity, SNI, ALPN/ứng dụng và security policy phù hợp.

**PSK binder** giúp chứng minh phía client biết PSK phù hợp và ràng buộc phần liên quan của ClientHello. Client không tự gửi plaintext PSK hay tự tạo binder bằng `HMAC(password)` không theo TLS; quá trình này do TLS implementation xử lý.

### 5.3. PSK-only và PSK-DHE không có bảo đảm giống nhau

TLS 1.3 có các mode sử dụng PSK:
- **`psk_ke` (PSK-only):** key establishment dựa vào PSK, không bổ sung fresh (EC)DHE cho 1-RTT handshake theo mode đó.
- **`psk_dhe_ke` (PSK + ephemeral DHE):** có ephemeral key agreement mới, đóng góp forward secrecy cho traffic mới theo thiết kế protocol.

Vì vậy không phát biểu “TLS 1.3 resumed session luôn có fresh DHE” hoặc “resumption luôn bảo vệ hoàn toàn nếu ticket key/PSK lộ”. Cần biết server chọn mode nào, lifetime/rotation ra sao và threat model liên quan.

**Quan trọng với RFC 9846:** không tái sử dụng `KeyShare` giá trị giữa các kết nối khi triển khai protocol. Dùng TLS library chuẩn; không cố cache DH private/public ephemeral keys để “tăng tốc” handshake.

### 5.4. Ticket là bearer material ở tầng TLS

Một ticket không nhất thiết là bí mật thuần dạng raw key, nhưng nó là thành phần liên quan đến khả năng resumption; tài liệu ticket/PSK của TLS provider có thể rất nhạy cảm. Không log raw ticket, PSK hoặc session secrets ra application log. Hệ thống cần bảo vệ ticket key của server và client-side resumption cache, đồng thời quản lý lifetime/revocation/scope.

## 6. 0-RTT early data là gì? Vì sao không giống resumption thông thường?

Ở TLS 1.3, khi đã có PSK phù hợp, protocol **có thể** cho phép client gửi một số application data **ngay trong first flight của handshake mới** (0-RTT early data). Đây là một tính năng tùy chọn, **không đồng nghĩa** mọi TLS 1.3 resumption đều dùng 0-RTT.

```text
Resumption 1-RTT:
ClientHello(PSK offer) -------------------------->
                     <---------------- ServerHello/Finished
Client Finished ------------------------------->
Application Data <=============================>

Resumption + 0-RTT (only when explicitly enabled/accepted):
ClientHello(PSK offer) + Early Data ------------>
                     <---------------- ServerHello/Finished
Client Finished ------------------------------->
Normal Application Data <======================>
```

### 6.1. Khác biệt an toàn

RFC 9846 cảnh báo **0-RTT early data** có tính chất bảo mật yếu hơn 1-RTT data:

1. **Không có forward secrecy cho bản thân 0-RTT data** vì được bảo vệ bằng keys dẫn xuất từ offered PSK, chưa dựa trên fresh server key share cho phiên mới.
2. **Có nguy cơ replay giữa các kết nối**. Attacker có thể tìm cách gửi lại ClientHello/early data tới server hoặc cụm server trong cửa sổ cho phép.
3. Những biện pháp anti-replay ở tầng TLS có thể giảm nguy cơ nhưng **không thay thế kiểm soát lệnh nghiệp vụ**. Điều kiện mạng, multi-region và retries vẫn có thể làm nhận nhiều bản copy dữ liệu.
4. Server có thể **từ chối early data** nhưng tiếp tục handshake. Ứng dụng cần biết dữ liệu nào đã/ chưa được xử lý và cách retry an toàn.
5. Khả năng dùng 0-RTT phụ thuộc protocol/application profile, policy server, ticket và API/provider. **Không giả định Java 17 `HttpClient` tự gửi 0-RTT chỉ vì có cache**.

### 6.2. Ví dụ request được và không được dùng 0-RTT

| Request | 0-RTT mặc định nên như thế nào? | Lý do |
| --- | --- | --- |
| `GET` chỉ đọc, thực sự không có side effect và theo profile cho phép | Có thể cân nhắc | Phải chịu được replay và request authentication policy |
| `HEAD` lấy metadata | Có thể cân nhắc | Cũng cần kiểm tra tình huống logging/billing/rate limits |
| `POST /transfer-money` | **Không** | Một replay có thể kích hoạt giao dịch lặp |
| `POST /delete-account` | **Không** | Hậu quả không idempotent/replay-safe |
| `POST /issue-token` | **Không** nếu có side effect/nonce logic yếu | Replay có thể tạo nhiều credential |
| `POST /create-order` dù có requestId | Mặc định không; chỉ cân nhắc khi đã có profile và idempotency **thực sự an toàn** | Idempotency store phải atomic và scope đúng |
| API có cookie đăng nhập nhưng stateful | Không mặc định | Cookie hợp lệ không có nghĩa request replay-safe |

“HTTP GET thường idempotent” không tự động cho phép mọi GET dùng 0-RTT: có API GET thực tế gây side effect hoặc rò rỉ dữ liệu theo session state. Ứng dụng phải xác nhận tính an toàn, kiểm soát replay và yêu cầu profile cụ thể.

### 6.3. Idempotency không đồng nghĩa hết rủi ro

Dù một endpoint có `Idempotency-Key`, nếu idempotency store chỉ tồn tại trong một node thì hai early-data requests vào hai node có thể vẫn xử lý hai lần. Cần thiết kế atomicity, namespace theo principal/tenant, expiration, khóa nghiệp vụ và xử lý retry.

**Bài học:** 0-RTT là tối ưu hóa đòi hỏi thiết kế an toàn riêng; **1-RTT session resumption** đã có lợi ích hiệu năng mà không đòi client gửi dữ liệu nguy hiểm trước khi handshake hoàn thành.

## 7. Vòng đời của một TLS session/ticket

Một session không “mãi mãi hợp lệ” chỉ vì handshake từng thành công.

### 7.1. Các giai đoạn

```text
HANDSHAKE AUTHENTICATED
         |
         v
SESSION / RESUMPTION STATE CREATED
         |
         v
STORED / AVAILABLE
  client cache or ticket
  server cache or ticket keys
         |
         +---------> USED IN NEW HANDSHAKE IF POLICY ACCEPTS
         |                          |
         |                          +--> new connection, new traffic keys
         |                          +--> possibly new tickets
         |
         +---------> EXPIRES / INVALIDATED / EVICTED
         |                 |
         |                 +--> new connection needs full handshake
         |
         +---------> SERVER KEY/CONFIG ROTATION
                           |
                           +--> resumption may be declined
```

Các nguyên nhân session không được resume:
- Server không phát ticket, client chưa nhận ticket hoặc cache của client bị xóa.
- Ticket/session đã hết hạn theo thời gian protocol, chính sách server hoặc provider cache.
- Server restart mất in-memory state hoặc rotate ticket-protection keys.
- Server cluster chọn node không có cùng resumption state/keys.
- Client dùng `SSLContext` hoặc trust/identity configuration khác.
- SNI, ALPN, phiên bản TLS, cipher-suite hash hoặc chính sách authentication khác.
- Server từ chối resumption khi chính sách bảo mật thay đổi, certificate/key bị thu hồi hoặc có sự kiện an ninh.
- Session bị evicted khỏi cache vì giới hạn dung lượng.
- Resumption bị vô hiệu theo chính sách hoặc implementation.

**Không phải mọi nguyên nhân đều là bug.** Full handshake có thể là hành vi an toàn và mong muốn nếu trạng thái không còn phù hợp.

### 7.2. Timeout nào chi phối?

| Loại timeout/lifetime | Cái gì hết hạn? | Tác động |
| --- | --- | --- |
| TCP connect timeout | Chờ tạo transport connection | Socket connect fail, chưa có TLS handshake |
| TLS handshake timeout | Chờ hoàn tất handshake | Kết nối mới chưa sẵn sàng |
| Socket read timeout | Chờ đọc dữ liệu trên socket | Có thể fail I/O, tùy API không nhất thiết xóa session cache |
| HTTP request timeout | Một tác vụ HTTP | Request có thể bị hủy, không phải quy tắc TTL ticket TLS |
| HTTP idle connection timeout | Kết nối còn mở nhưng không dùng | Pool đóng socket; lần sau cần kết nối mới |
| TLS session cache timeout | Một cache entry có thể được dùng lại bao lâu | Có thể buộc full handshake lần sau |
| Ticket lifetime | Resumption ticket/PSK được offer/chấp nhận bao lâu | Hết ticket thì không resume theo ticket đó |
| Server ticket-key rotation | Khóa server giải/kiểm ticket đã phát | Ticket cũ có thể không còn được chấp nhận |
| App authentication/session expiry | User/token/cookie ứng dụng | User cần xác thực lại dù TLS session hợp lệ |

Không dùng một cấu hình `SSLSessionContext.setSessionTimeout(600)` rồi suy ra “mọi TCP socket sẽ tự đóng sau 600 giây”. Đó là **session-cache policy**, không phải connection idle timeout.

### 7.3. Hai phía có thể có giới hạn khác nhau

Client có thể còn lưu ticket nhưng server đã đổi ticket key; client có thể chủ động gửi PSK identity và bị server từ chối. Tương tự, server còn khả năng resume nhưng client đã evict ticket. Thành công cần **hai bên đồng thuận** trong handshake mới.

## 8. Java 17+ biểu diễn TLS session qua những API nào?

### 8.1. `SSLContext`

`SSLContext` chứa cấu hình TLS provider được khởi tạo với các KeyManagers, TrustManagers và nguồn ngẫu nhiên phù hợp. Từ một context có thể tạo socket/engine để thực hiện nhiều TLS connections.

`SSLContext` cũng expose:
- `getClientSessionContext()`: context quản lý các sessions phía client theo khả năng provider.
- `getServerSessionContext()`: context quản lý sessions phía server theo khả năng provider.

Không coi cùng một context là “phiên TLS duy nhất”; đây là **hạ tầng tạo connections và quản lý trạng thái**.

### 8.2. `SSLSessionContext`

Các API chính:

```java
SSLSessionContext sessions = sslContext.getClientSessionContext();

sessions.setSessionTimeout(600); // timeout in seconds
sessions.setSessionCacheSize(128); // cache entries limit

int seconds = sessions.getSessionTimeout();
int limit = sessions.getSessionCacheSize();
```

Ý nghĩa:
- `setSessionTimeout(int seconds)` điều chỉnh giới hạn thời gian session được giữ trong cache mà provider quản lý. **0** theo API nghĩa là không giới hạn thời gian bằng setting này; không có nghĩa tắt resumption.
- `setSessionCacheSize(int size)` điều chỉnh giới hạn số sessions trong cache. **0** nghĩa là không áp giới hạn kích thước bằng setting này; không có nghĩa cache trống hay bị vô hiệu.
- Giá trị âm không hợp lệ và thường dẫn tới `IllegalArgumentException` theo API.
- `getIds()` liệt kê IDs các sessions trong cache dưới khả năng của implementation; `getSession(byte[])` truy xuất theo ID khi có.
- Trong TLS 1.3, một server có thể gửi nhiều tickets và provider có nhiều trạng thái nội bộ; **không đảm bảo API `getIds()` hiển thị một ticket một-một**, hoặc một session ID có nghĩa giống TLS 1.2.
- Thời gian và kích thước cache chỉ là **một phần policy**. Server có quyền từ chối; ticket lifetime và TLS implementation có thêm ràng buộc.

**Chú ý:** không tùy tiện đặt cache size/timeouts = 0 với hy vọng “disable”; nếu mục tiêu là vô hiệu resumption vì chính sách, phải đọc đúng tài liệu provider, áp cấu hình có hỗ trợ và kiểm thử wire handshake.

### 8.3. `SSLSession`

`SSLSession` đại diện thông tin một established TLS session theo model JSSE, có các phương thức hữu ích:

| API | Có thể xem gì? | Giới hạn |
| --- | --- | --- |
| `getProtocol()` | Phiên bản như `TLSv1.3` | Không cho biết handshake vừa full hay resumed |
| `getCipherSuite()` | Suite negotiated | Không đồng nghĩa ticket được sử dụng |
| `getPeerPrincipal()` | Peer principal nếu được xác thực theo session | Có thể throw nếu không có peer authentication; không thay authorization |
| `getCreationTime()` | Thời điểm tạo session theo model/provider | Không phải thời điểm mỗi HTTP request |
| `getLastAccessedTime()` | Thời điểm được truy cập theo model/provider | Không phải network idle timer của socket |
| `getId()` | Session ID bytes theo JSSE | Không phải wire ticket ID đáng tin để chứng minh resumption |
| `getSessionContext()` | Cache context sở hữu session, có thể null | Không nói kết nối còn mở |
| `isValid()` | Trạng thái session có thể còn hợp lệ theo API | Không bảo đảm socket còn mở hoặc server sẽ accept ticket |
| `invalidate()` | Đánh dấu session không thể dùng tiếp cho resume/join theo API | Không tự đóng established TLS connection |
| `getPeerCertificates()` | Peer certificate chain nếu hiện diện | Không tự thay certificate validation/hostname hoặc authz |

Trong TLS 1.3, khái niệm session được mapping lên JSSE provider có thể khác về ticket/ID và thời điểm cập nhật. Đừng xây business state dựa trên `SSLSession.getId()`, `getCreationTime()` hoặc assumption rằng session object identity chứng minh resumed handshake.

### 8.4. `SSLSocket`, `SSLEngine` và HTTP client

- `SSLSocket`: TLS over socket truyền thống, có `startHandshake()`, `getSession()`, `close()`. Phải cấu hình đúng trust và endpoint identity.
- `SSLEngine`: TLS state machine không gắn trực tiếp I/O, được NIO/framework dùng. Application chịu trách nhiệm wrap/unwrap, handshake status, close inbound/outbound và buffer.
- `java.net.http.HttpClient`: quản lý kết nối, pooling, HTTP/2 và TLS dưới abstraction. `HttpResponse.sslSession()` có thể cung cấp thông tin phiên, **không** công khai đầy đủ quyết định reuse/resumption.
- `HttpsURLConnection`: còn có API lấy thông tin TLS; không mặc định so sánh hai response là hai handshake.

Không tự xây cơ chế “caching `SSLSocket` mãi mãi” để giữ security state. Một socket có thể đã đóng, mất mạng, hết idle timeout, hết certificate policy hoặc không còn phù hợp cho origin khác.

## 9. Java 17+: chương trình chạy offline để hiểu session cache

Ví dụ dưới đây chủ động **không kết nối mạng**; nó chỉ tạo một `SSLContext` mới, lấy session contexts của client và server, đặt timeout/cache sizes rồi kiểm tra các tham số nhận lại.

Điều này giúp phân biệt **quản lý khả năng cache session** với việc **thực sự tạo TLS handshake/session**. Demo không cố khẳng định resumption đã xảy ra khi chưa có remote server.

Tạo tệp `TlsSessionCacheDemo.java`:

```java
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSessionContext;

public class TlsSessionCacheDemo {
    static SSLContext createContext() throws GeneralSecurityException {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, null, new SecureRandom());
        return context;
    }

    public static void main(String[] args) throws Exception {
        SSLContext context = createContext();

        SSLSessionContext client = context.getClientSessionContext();
        SSLSessionContext server = context.getServerSessionContext();

        client.setSessionTimeout(600);
        client.setSessionCacheSize(128);
        server.setSessionTimeout(300);
        server.setSessionCacheSize(64);

        System.out.println("Client session timeout (s): "
                + client.getSessionTimeout());
        System.out.println("Client session cache size: "
                + client.getSessionCacheSize());
        System.out.println("Server session timeout (s): "
                + server.getSessionTimeout());
        System.out.println("Server session cache size: "
                + server.getSessionCacheSize());

        boolean badTimeoutRejected = false;
        try {
            client.setSessionTimeout(-1);
        } catch (IllegalArgumentException expected) {
            badTimeoutRejected = true;
        }

        boolean badSizeRejected = false;
        try {
            client.setSessionCacheSize(-1);
        } catch (IllegalArgumentException expected) {
            badSizeRejected = true;
        }

        System.out.println("Negative timeout rejected: " + badTimeoutRejected);
        System.out.println("Negative cache size rejected: " + badSizeRejected);
        System.out.println("Client and server cache objects differ: "
                + (client != server));

        // This program creates no SSLSocket or SSLEngine handshake.
        System.out.println("TLS handshakes performed by demo: 0");
    }
}
```

Biên dịch và chạy với Java 17+:

```bash
javac TlsSessionCacheDemo.java
java TlsSessionCacheDemo
```

Stdout đã kiểm tra trên JDK có `SunJSSE` tương thích Java 17:

```text
Client session timeout (s): 600
Client session cache size: 128
Server session timeout (s): 300
Server session cache size: 64
Negative timeout rejected: true
Negative cache size rejected: true
Client and server cache objects differ: true
TLS handshakes performed by demo: 0
```

### 9.1. Giải thích kết quả

- Các setters/getters xác nhận **cấu hình cache của context** chứ không chứng minh có session nào được tạo.
- Client và server session caches khác nhau vì có hai vai trò TLS độc lập.
- Tham số âm bị từ chối vì không có nghĩa hợp lệ cho thời gian/kích thước cache.
- Dòng cuối là tuyên bố về luồng code: chương trình không mở socket/engine handshake. **Không được viết unit test “session resumption worked” dựa trên chương trình này.**
- Một provider khác có thể có đặc điểm session-cache riêng; test trên phải được thực hiện trong môi trường thực tế nếu muốn dùng như kiểm tra tương thích. Chương trình không thiết lập chính sách certificate/hostname vì nó hoàn toàn không kết nối tới endpoint nào.

### 9.2. Điểm cần tránh khi cấu hình production

Việc đặt `setSessionTimeout(600)` không:
- Tạo TLS session ticket.
- Bảo đảm server đồng ý resume trong mười phút.
- Thiết lập HTTP keep-alive idle timeout.
- Đóng connection sau mười phút.
- Bật 0-RTT.
- Tái xác thực certificate hoặc app principal mỗi 600 giây.
- Bảo đảm server ticket key còn hiệu lực hay cache được share giữa các nodes.

Tương tự, `setSessionCacheSize(128)` là giới hạn cache theo implementation, không phải giới hạn 128 TCP connections, 128 concurrent users hay 128 HTTP requests.

## 10. Java 17+: quan sát TLS session trong HTTPS request thật

Ví dụ thứ hai dùng `HttpClient` cho một địa chỉ HTTPS có thật. Nó mở kết nối mạng và có thể cho thấy protocol/cipher suite; vì kết quả phụ thuộc mạng, server, proxy, provider và connection pool, **không có stdout cố định**.

Tệp `ObserveHttpsSession.java`:

```java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import javax.net.ssl.SSLSession;

public class ObserveHttpsSession {
    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        URI uri = URI.create("https://example.com/");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        for (int i = 1; i <= 2; i++) {
            HttpResponse<Void> response = client.send(
                    request, HttpResponse.BodyHandlers.discarding()
            );
            Optional<SSLSession> maybeSession = response.sslSession();
            System.out.println("Request: " + i);
            System.out.println("HTTP status: " + response.statusCode());
            System.out.println("TLS protocol: " + maybeSession
                    .map(SSLSession::getProtocol).orElse("not exposed"));
            System.out.println("Cipher suite: " + maybeSession
                    .map(SSLSession::getCipherSuite).orElse("not exposed"));
        }
    }
}
```

Chạy:

```bash
javac ObserveHttpsSession.java
java ObserveHttpsSession
```

Điều quan sát được:
- Hai requests cùng dùng **một `HttpClient` instance**, tăng khả năng connection pooling nhưng không bảo đảm cả hai được gửi trên cùng socket.
- Kết quả `response.sslSession()` có thể hiển thị protocol/cipher suite. Nó **không có một boolean chuẩn “isResumedHandshake”**.
- Nếu pool reuse một kết nối đã mở, request thứ hai **không thực hiện TLS handshake mới** và không có sự kiện resumption để đo.
- Nếu server/route/proxy đóng kết nối giữa hai requests, client có thể tạo connection mới; handshake mới **có thể full hoặc resumed** tùy điều kiện.
- Một server có thể phản hồi HTTP/1.1 hay HTTP/2; phiên bản HTTP không thể được suy ra chỉ từ TLS cipher suite.
- Nếu TLS handshake thất bại hoặc không có mạng, ví dụ ném exception thay vì in stdout. Không tắt certificate verification để “chạy cho được”.

**Không log raw `SSLSession.getId()`, ticket, key material hay HTTP cookie.** Nếu cần debug, nên có logging đã đánh giá bảo mật và môi trường thử nghiệm được kiểm soát.

### 10.1. Muốn chứng minh handshake đã resumed thật thì làm thế nào?

Một phép thử đáng tin phải:
1. Điều khiển client và server test, cùng TLS provider/configuration và cùng peer identity.
2. Đầu tiên tạo **full authenticated handshake**; bảo đảm session ticket được phát và được client thực sự nhận/lưu.
3. Đóng **đúng transport/TLS connection**, nhưng giữ resumption cache/PSK state có chủ đích.
4. Mở một **new transport connection** với cùng endpoint, policy và context.
5. Thu thập bằng chứng từ **TLS handshake debug/protocol trace** rằng client offered PSK và server selected PSK/resumption (hoặc TLS 1.2 selected session ID/ticket).
6. Thử lại sau khi cache evicted, ticket expired hoặc ticket key rotated; xác nhận fallback full handshake.
7. Kiểm tra certificate validation và expected identity vẫn được thực thi theo protocol/resumption policy.
8. Kiểm tra không xảy ra 0-RTT khi application không chủ động hỗ trợ.

**Không đủ chứng cứ:**
- Handshake thứ hai “nhanh hơn”.
- `SSLSession.isValid()` là true.
- `getId()` giống hoặc khác.
- HTTP status 200.
- Response thứ hai dùng cùng `HttpClient`.
- `SSLSession.getCreationTime()` vẫn cũ.
- TCP connection bị reused nhưng không hề có new handshake.

Khi phân tích trace ở TLS 1.3, nhớ rằng nhiều handshake messages/traffic đã mã hóa; phải sử dụng diagnostic/provider tooling phù hợp và tránh xuất session secrets/keys ra ngoài.

## 11. Session cache và connection pool: hai bộ nhớ khác nhau

### 11.1. Connection pool giữ thứ gì?

Một HTTP connection pool có thể giữ **sockets đã bắt tay xong**, với:
- Remote endpoint/origin, proxy route, network interface.
- Giao thức HTTP/1.1 hoặc HTTP/2, trạng thái stream.
- TLS connection đang sống.
- Idle timeout, số kết nối tối đa, keep-alive policy.
- Các giới hạn concurrency, HTTP/2 GOAWAY, connection health.

Mục đích chính: tránh phải mở socket mới và thực hiện lại handshake nếu có kết nối đang dùng được.

### 11.2. TLS session cache giữ thứ gì?

Tùy protocol/provider, TLS session cache giữ resumption state, PSK/tickets/session IDs và metadata đủ để **rút gọn handshake cho một kết nối mới**. Không tự hiểu nó là một live socket.

### 11.3. Kết hợp hai loại cache

```text
HTTP request arrives
       |
       v
HTTP connection pool contains usable live connection?
       |
   +---+---+
   |       |
  YES      NO
   |       |
reuse      open new TCP connection
existing       |
TLS conn       v
   |       TLS library checks resumption state
   |           |
   |       +---+---+
   |       |       |
   |     ticket   no ticket / rejected
   |     valid       |
   |       |          |
   |     resumed    full handshake
   |     handshake
   |       |          |
   +-------+----------+
           |
           v
       Send HTTP request
```

Vì thế, tăng connection pool reuse có thể làm **ít handshake mới hơn**, khiến tỷ lệ resumption trong số tất cả requests có vẻ thấp dù hiệu năng tốt. Ngược lại, giảm idle timeout khiến nhiều sockets mới và resumption attempts hơn; không mặc định kết quả là bảo mật/hiệu năng tốt hơn.

### 11.4. HTTP/2, HTTP/3 và QUIC

- Với **HTTPS HTTP/2 over TLS/TCP**, nhiều streams cùng chia sẻ một TLS connection; không có TLS handshake riêng cho mỗi stream.
- Với **HTTP/3**, giao vận là QUIC và TLS 1.3 được tích hợp vào QUIC, không chạy như `SSLSocket` TLS-over-TCP thông thường. QUIC có khái niệm connection migration, connection IDs và resumption/0-RTT khác với keep-alive TCP; đọc RFC 9000/9001 và stack HTTP/3 tương ứng.
- Một thư viện Java 17 `java.net.http.HttpClient` chuẩn không nên được giả định có mọi cơ chế HTTP/3/QUIC tùy deployment. Không áp các công cụ quản lý TCP socket trực tiếp cho QUIC mà không kiểm tra implementation.

## 12. Kết thúc kết nối: close socket, close_notify và session cache

Đây là phần thường gây bug về vòng đời.

### 12.1. Bốn hành động khác nhau

| Hành động | Ảnh hưởng chính | Không nhất thiết làm gì? |
| --- | --- | --- |
| Đóng HTTP response body/stream | Giải phóng tài nguyên request, giúp connection pool xử lý tái dùng hoặc đóng | Không nhất thiết xóa TLS resumption state |
| Đóng `SSLSocket` / TLS connection | Ngừng sử dụng kết nối và giải phóng tài nguyên liên quan | Không nhất thiết invalidate mọi cached sessions/tickets |
| `SSLSession.invalidate()` | Đánh dấu session không thể tiếp tục tham gia/resume theo API | Không tự đóng kết nối đã established |
| Ticket expired / evicted | Không còn khả năng resume bằng ticket đó | Không tự kill socket cũ đang truyền dữ liệu |

Ứng dụng không nên “tắt SSL session” bằng cách chỉ gọi `close()` trên một HTTP response hay “đóng socket” bằng `SSLSession.invalidate()`.

### 12.2. TLS `close_notify`

TLS có thông điệp alert `close_notify` để báo một phía không gửi thêm dữ liệu qua kênh TLS theo ngữ nghĩa protocol. Với TCP, abrupt EOF hoặc connection reset khác với orderly TLS closure.

Tuy nhiên behavior cụ thể liên quan half-close, EOF, HTTP framing và API shutdown phụ thuộc giao thức và stack. Đừng làm một chương trình đọc dữ liệu nhạy cảm rồi **chấp nhận EOF bất ngờ như bằng chứng bản tin đã hoàn chỉnh** khi protocol chưa có length/framing/integrity đúng.

Với HTTPS client chuẩn, framework quản lý TLS connection shutdown và pool. Với `SSLEngine`, ứng dụng phải chú ý `closeOutbound()`, wrap dữ liệu đóng và xử lý `closeInbound()` theo API/provider; không chỉ gọi `ByteBuffer.clear()` hay bỏ qua handshake status.

### 12.3. Invalidation không thay key rotation hay revoke certificate

Nếu một TLS session bị invalidate ở client, một server đã phát ticket có thể vẫn giữ state hoặc ticket key; bạn cần kiểm soát policy **ở cả phía phù hợp**. Nếu có sự cố private key/certificate hoặc PSK/ticket key, hệ thống thường phải đánh giá phạm vi resumption, rotation server keys, cache, revoke credentials và thời gian chấp nhận tickets; gọi `invalidate()` trên một session Java cục bộ **không phải** global revocation.

Trong các kết nối đang mở, việc đánh giá có cần đóng ngay/áp chính sách lại là quyết định an ninh và triển khai riêng.

## 13. Authentication, chứng thư và vòng đời resumption

### 13.1. Resumption dựa trên xác thực trước đó

Một TLS 1.3 PSK resumption bình thường kế thừa sự ràng buộc an ninh từ handshake trước và ticket/state được phát hợp lệ. Nó có thể không lặp lại `Certificate` và `CertificateVerify` của server ở mọi resumed handshake.

Điều đó tạo yêu cầu vận hành:
- Không kéo dài trust vào một chứng thư đã bị thu hồi hoặc key bị lộ mà không có policy.
- Giới hạn tuổi ticket, số lần nối tiếp, và key lifetimes theo security policy.
- Bảo đảm SNI, ALPN, protocol, cipher hash và principal binding phù hợp.
- Nếu certificate hoặc mTLS client principal thay đổi, cần quyết định resumption state có còn được phép hay không.
- Nếu có refresh login/authorization policy, áp ở tầng ứng dụng; không dùng resumption để bỏ qua kiểm tra quyền.

RFC 9846 có hướng dẫn giới hạn tổng thời gian duy trì resumption state từ chứng thực trước, xét certificate lifetime, khả năng bị thu hồi và thời gian từ lần chứng thực online gần nhất.

### 13.2. Hostname verification không được bị bỏ qua

Khi client tạo TLS connection mới tới `payments.example.com`, việc dùng PSK/ticket từ phiên trước cần nằm trong phạm vi identity chính xác. Không sử dụng một ticket “trông hợp lệ” để kết nối tới arbitrary server chỉ vì server đó trình cùng một loại cipher suite.

Nếu application tắt hostname verification hoặc dùng trust-all, rủi ro MITM vẫn còn. TLS resumption **không sửa sai** trust manager hoặc certificate validation của initial handshake.

### 13.3. mTLS và vòng đời client certificate

Với mTLS, server có thể đã xác minh client certificate trong full handshake. Khi resume bằng một session/ticket liên quan tới client identity, server phải bảo đảm policy còn phù hợp; quyền người dùng/ứng dụng có thể đã bị thu hồi hoặc đổi tenant.

Một service không nên tự động cấp quyền admin chỉ vì resumed connection mang trạng thái xác thực cũ:
- Kiểm tra identity/principal mapping và thời hạn chính sách.
- Gắn certificate identity và key IDs với tenant/role từ nguồn tin cậy.
- Có cơ chế revoke/session invalidation phù hợp cluster và token/key lifecycle.
- Tránh đưa thông tin quyền ứng dụng dài hạn vào TLS ticket mà không có expiry/version/revalidation policy.

## 14. KeyUpdate không phải resumption

Trong TLS 1.3, `KeyUpdate` cho phép thay đổi application traffic keys trong **một TLS connection đang tồn tại** theo protocol. Nó không:
- Mở TCP connection mới.
- Tạo HTTP request mới.
- Mặc định tái xác thực certificate.
- Bắt buộc phát session ticket mới.
- Chứng minh một resumed handshake đã xảy ra.

Cần phân biệt:

```text
Same TLS connection:
    handshake -> traffic keys A -> KeyUpdate -> traffic keys B
                                (transport connection remains)

Resumption:
    connection #1 handshake -> ticket/state
              [connection #1 closes]
    connection #2 new handshake using PSK/ticket -> new traffic keys
```

Khi phân tích debug hoặc thiết kế key rotation, đừng lấy sự kiện key update làm thước đo session resumption rate.

## 15. Load balancer và hệ thống nhiều node

Một API gateway có nhiều backend nodes có thể có:
- TLS termination ở load balancer, backend chỉ nhận HTTP/gRPC nội bộ.
- TLS termination tại từng application instance.
- Một lớp proxy/mesh thực hiện mTLS giữa services.
- Các instance giữ caches/ticket keys khác nhau.

Khi client kết nối nhiều lần:
- Nếu TLS kết thúc ở gateway, **session resumption được quyết định ở gateway**, không phải `SSLContext` của service phía sau.
- Nếu terminate ở từng node với server-side session cache cục bộ, new connection tới node khác có thể full handshake vì không có state.
- Nếu server-issued tickets dùng key chia sẻ, nhiều nodes có thể resume dễ hơn; **việc chia sẻ ticket keys tăng blast radius** nếu một node bị lộ và đòi cơ chế rotation/phân phối an toàn.
- Nếu 0-RTT được bật ở nhiều zones, cần **anti-replay coordination**; không dùng cache cục bộ đơn giản như bằng chứng chống replay toàn cụm.
- Restart/rolling deployment có thể làm cache miss/rotated ticket keys, dẫn tới tăng full handshake. Đây có thể là hành vi đúng.

Thiết kế phải cân bằng hiệu năng, khả năng mở rộng và bí mật:
- Chỉ chia sẻ ticket keys/trust material trong phạm vi được chính sách cho phép.
- Rotation có overlap hạn chế, giới hạn ticket lifetime và audit.
- Giữ cache gần nơi TLS terminate; tránh truyền TLS secret vào application logs.
- Benchmark tỉ lệ full/resumed handshake **tại đúng tầng kết thúc TLS**, không ở service không nhìn thấy handshake.

## 16. Đo lường và debug mà không tiết lộ TLS secrets

### 16.1. Những chỉ số thực sự hữu ích

- Số lượng **new TCP connections** và **TLS handshakes** trong một khoảng thời gian.
- Số **full handshakes** và **resumed handshakes** được server/provider xác nhận.
- Ticket issue rate, offer/accept/reject rate, expiration, cache hit/miss/eviction.
- TLS handshake latency theo phiên bản/cipher/route, nhưng không suy kết quả resumption chỉ từ latency.
- HTTP connection pool reuse, idle close, HTTP/2 stream concurrency.
- Handshake failures theo nhóm: certificate validation, SNI/ALPN mismatch, expired ticket, key rotation, network failures.
- 0-RTT offered/accepted/rejected, replay attempts và business idempotency outcomes nếu tính năng bật.

**Không gom tất cả thành “TLS sessions” mà không ghi rõ đơn vị.** Sessions khác connections, handshakes và requests.

### 16.2. Java TLS debug

Trong môi trường thử nghiệm, JSSE có cơ chế debug cho SSL/TLS, ví dụ option thường thấy:

```bash
java -Djavax.net.debug=ssl,handshake,session ObserveHttpsSession
```

Đây là công cụ diagnostic, **không phải cấu hình mặc định production**. Debug log có thể chứa metadata nhạy cảm như hostname, certificate subject, phiên bản, session IDs/ticket details tùy provider và mức log. Không công bố raw traces khi chưa review; tránh bất kỳ key logging/secret extraction nào không cần thiết.

Các thông tin cần nhận diện trong trace/protocol:
- Khi new connection được tạo, có `ClientHello`/`ServerHello` mới không?
- Có offered `pre_shared_key` identity/PSK binder trong TLS 1.3 không?
- Server có selected PSK/resumption không, hay fallback certificate-based full handshake?
- Có `NewSessionTicket` sau full handshake được client tiếp nhận không?
- HTTP request thứ hai thực ra có dùng lại socket đầu tiên không?

**Chỉ nhìn `CipherSuite` hoặc `SSLSession.getId()` không đủ kết luận.**

### 16.3. Đừng log ticket/raw PSK và token

Dữ liệu nhạy cảm:
- Raw ticket bytes, resumption PSK, traffic secrets.
- Credential/cookie/token tầng HTTP.
- Key material của certificate client hoặc ticket-protection key server.
- Debug traces đủ chi tiết để làm lộ mối liên hệ sessions/users/hosts.
- Application request body chứa dữ liệu cá nhân.

Dùng metrics aggregate và trace IDs không nhạy cảm. Khi có incident, áp quyền, retention và redaction cho artifacts như với dữ liệu bảo mật.

## 17. Các lỗi phổ biến và cách sửa

| Ngộ nhận / lỗi | Vì sao sai | Cách đúng |
| --- | --- | --- |
| “Hai GET liên tiếp trên cùng HttpClient là hai lần session resumption” | Pool có thể reuse cùng live connection | Phân biệt socket reuse và new handshake |
| “Không cần mở socket mới vì có session ticket” | Ticket giúp rút gọn handshake **sau khi** tạo connection mới | Kết nối mới vẫn cần transport và key establishment |
| “`SSLSession.isValid()` nghĩa là TCP socket còn mở” | Session state khác socket state | Theo dõi socket/HTTP pool riêng |
| “`SSLSession.invalidate()` đóng mọi TLS connection ngay” | Invalidation cấm resume/join trong tương lai theo API, không đồng nghĩa close | Đóng socket/engine theo lifecycle riêng |
| “`setSessionTimeout(600)` nghĩa là đóng idle TCP sau 600s” | Là cache time policy | Cấu hình HTTP/TCP idle riêng |
| “`setSessionCacheSize(0)` là tắt cache” | 0 biểu thị không giới hạn cache size theo API | Dùng provider policy đúng nếu muốn disable |
| “Resumption lúc nào cũng gồm 0-RTT” | 0-RTT là feature riêng, tùy chọn | 1-RTT PSK resumption có thể không có early data |
| “0-RTT đã có TLS nên transfer-money an toàn” | Early data có thể replay; weaker secrecy | Cấm unsafe operations, anti-replay/idempotency theo profile |
| “Dùng PSK resumption thì mọi thứ có PFS” | PSK-only khác PSK-DHE; 0-RTT không có FS | Chọn mode/policy rõ, bảo vệ ticket/PSK |
| “Client đã có ticket nên server bắt buộc resume” | Server có thể reject do policy/rotation/expiry | Chấp nhận fallback full handshake |
| “Ticket lifetime = HTTP login lifetime” | Hai lớp xác thực khác nhau | AuthN/AuthZ application có TTL riêng |
| “Server restart không ảnh hưởng resumption” | In-memory state/key rotation có thể mất | Thiết kế ticket/cache/cluster phù hợp |
| “TLS ticket có thể dùng cho bất kỳ hostname” | Identity binding/SNI/security policy quan trọng | Verify expected endpoint, scope ticket |
| “0-RTT GET nào cũng an toàn” | Có thể có side effect, token state hoặc retry nguy hiểm | Chỉ khi application profile chứng minh replay-safe |
| “Full handshake là lỗi nếu đã bật cache” | Cache miss/expiry là hành vi bình thường | Monitor rate và nguyên nhân, không downgrade |
| “KeyUpdate mở phiên TLS mới” | KeyUpdate xảy ra trong connection hiện tại | Phân biệt key rotation và resumption |
| “TLS resumption thay certificate validation” | Tin cậy phải có nền tảng từ full handshake và policy | Không tắt trust/hostname verification |
| “mTLS resumed = user còn được quyền admin” | App roles và certificates có thể đã bị revoke | Revalidate authorization/policy |
| “Chỉ cần so session ID để chứng minh resumed” | TLS 1.3 abstraction/ticket và pooling phức tạp | Dùng wire/JSSE handshake evidence đúng |
| “TLS over QUIC cũng là SSLSocket” | QUIC tích hợp TLS khác TCP/TLS | Dùng provider/API HTTP/3 và chuẩn QUIC phù hợp |

## 18. Tình huống thực hành

### Tình huống A: HTTP/2 tải 40 resources

Client mở một TLS connection tới `cdn.example.com` rồi tải 40 resources qua HTTP/2 streams. Nhóm vận hành nói đã có 40 TLS resumptions vì có 40 requests.

**Phân tích:** Không. Có thể chỉ có **một TLS handshake** và một connection, với 40 HTTP/2 streams. Connection reuse/multiplexing khác resumption. Đo ở network/TLS layer, không lấy request count.

### Tình huống B: HTTP/1.1 keep-alive hết hạn

Request #1 hoàn thành; pool đóng socket sau idle timeout. Request #2 mở TCP connection mới, client còn ticket TLS 1.3 và server chấp nhận PSK.

**Phân tích:** Có **new transport connection + resumed TLS handshake**. Request #2 không reuse socket cũ. Nếu ticket bị từ chối, vẫn có thể full handshake và xử lý HTTP bình thường.

### Tình huống C: Server vừa rotate certificate

Client còn resumption ticket gắn với handshake trước, nhưng server vừa rotate certificate hoặc thay đổi trust/policy.

**Phân tích:** Không mặc định resume tất cả tickets cũ. Phải xem ticket lifetime, chính sách binding, revocation/rotation và key resumption. Full handshake mới có thể được yêu cầu. Không giữ quyền an ninh cũ vô thời hạn chỉ để nhanh.

### Tình huống D: Giao dịch chuyển tiền trong 0-RTT

Client dùng early data gửi `POST /transfer`; kẻ tấn công ghi lại early-data flight và thử replay ở một region khác.

**Phân tích:** 0-RTT có replay risk ngay khi traffic được mã hóa. Không đưa lệnh có side effect như chuyển tiền vào early data nếu không có profile chống replay được chứng minh; biện pháp mặc định là không cho phép 0-RTT với thao tác này.

### Tình huống E: Client sửa `SSLSession.invalidate()` rồi kỳ vọng socket đóng

Ứng dụng gọi `invalidate()` nhưng tiếp tục thấy dữ liệu truyền trên `SSLSocket` cũ.

**Phân tích:** Không mâu thuẫn. Invalidation liên quan khả năng reuse/resumption state, còn established TLS connection vẫn có vòng đời riêng. Đóng socket/engine theo đúng API nếu muốn kết thúc truyền.

### Tình huống F: Server restart làm resumption giảm mạnh

Sau deploy, tỷ lệ resumed handshake từ 80% xuống 10%, full handshake tăng, nhưng HTTP vẫn hoạt động.

**Phân tích:** Có thể server mất in-memory session state hoặc rotate ticket keys, load balancer đổi route, client tickets hết hạn. Không tự suy đó là sự cố certificate hay bị tấn công; xác minh qua ticket/PSK accept diagnostics và cấu hình cluster.

### Tình huống G: Một Java service sau reverse proxy

Client ngoài kết nối HTTPS tới API gateway; gateway kết nối HTTP nội bộ tới Java backend. Java backend kiểm tra `SSLSessionContext` của mình và không thấy TLS sessions từ client.

**Phân tích:** TLS đã terminate ở gateway, backend không sở hữu sessions của client. Kiểm tra handshake ở gateway; identity propagation phải an toàn và không tin header client tự khai.

### Tình huống H: TLS 1.3 resumption nhưng không có 0-RTT

Client nhận ticket, lần sau dùng PSK và server chấp nhận; application data chỉ gửi sau bước Finished theo mode 1-RTT.

**Phân tích:** Đây vẫn là **session resumption hợp lệ**, không có early data. Phân biệt performance optimization thông thường với tính năng 0-RTT vốn yêu cầu replay-safe application semantics.

### Tình huống I: Application session expired

TLS connection vẫn sống và `SSLSession.isValid()` trả true, nhưng access token của user đã hết hạn.

**Phân tích:** Server phải từ chối hoặc yêu cầu auth lại theo chính sách. TLS state không gia hạn bearer token hoặc user privileges.

### Tình huống J: Java HTTP client đã tạo hai connections song song

Hai requests đến cùng origin nhưng pool chọn hai sockets khác nhau do concurrency/HTTP version. Handshake thứ hai có thể full dù cache còn ticket.

**Phân tích:** Số socket và khả năng resumption phụ thuộc timing nhận ticket, trust, policy và provider. Không suy từ số `HttpClient` instances; chạy tests có instrument TLS handshakes và connection reuse.

## 19. Checklist thiết kế và vận hành

### Application client

- [ ] Xác định rõ có cần connection pooling, TLS resumption hay cả hai; metric cho từng thứ.
- [ ] Sử dụng `HttpClient`/HTTP framework được hỗ trợ với trust manager và endpoint identity validation chuẩn.
- [ ] Không thay `SSLSession` cho HTTP auth session hoặc principal.
- [ ] Hạn chế số connections và idle timeouts theo tài nguyên/latency/threat model.
- [ ] Nếu custom `SSLContext`, quản lý client session cache, trust material và shutdown theo policy.
- [ ] Không tự gửi 0-RTT cho operation có side effect; kiểm tra profile/SDK hỗ trợ có chủ đích.
- [ ] Không giả định session ID/cache state chứng minh resumption hoặc peer identity.

### TLS termination server / gateway

- [ ] Chọn TLS versions, cipher suites, key-exchange modes và certificate policy cập nhật theo RFC 9846.
- [ ] Quản lý session/ticket lifetimes, resumption cache, server ticket-key rotation và secrets an toàn.
- [ ] Có chiến lược xử lý node restart, load balancing, key sharing và fallback full handshake.
- [ ] Giới hạn ticket lifetime và resumption chain theo policy, xét thay đổi certificate/CA/client identity.
- [ ] Nếu bật 0-RTT, cần anti-replay ở cluster, chỉ chấp nhận traffic replay-safe và có ứng dụng đồng thuận.
- [ ] Bảo vệ logs, traces, metric dimensions và key material; không ghi raw PSK/tickets.
- [ ] Kiểm tra session invalidation/revocation có scope thực tế, không tuyên bố một lệnh Java giải quyết toàn cluster.

### Vòng đời kết nối và tài nguyên

- [ ] Đóng `SSLSocket` hoặc `SSLEngine` theo contract khi không cần, xử lý lỗi/network EOF đúng.
- [ ] HTTP response bodies được đọc/đóng đúng để giải phóng connection pool resources.
- [ ] Không lấy timeout session cache làm socket read/HTTP request/idle timeout.
- [ ] Theo dõi pool saturation, keep-alive, idle close, HTTP/2 streams và remote GOAWAY.
- [ ] Duy trì authorization của ứng dụng độc lập với TLS resumption state.
- [ ] Có load tests, restart tests, ticket-key rotation tests, browser/mobile/network-change tests.

### Security regression

- [ ] Full handshake tới certificate hợp lệ và expected hostname thành công; sai trust/hostname fail.
- [ ] Resumed handshake thực sự được xác nhận bằng provider/wire evidence.
- [ ] Hết ticket/cache hoặc đổi trust/policy -> full handshake hoặc reject theo expected behavior.
- [ ] Raw ticket/key không xuất hiện trong logs/heap-dump artifacts không được bảo vệ.
- [ ] PSK-only/PSK-DHE policy được kiểm tra, không tái sử dụng ephemeral KeyShare.
- [ ] Không tự kích hoạt 0-RTT trên unsafe requests.
- [ ] Multi-instance anti-replay giữ đúng consistency nếu 0-RTT dùng.
- [ ] App auth expired vẫn bị từ chối dù TLS session hợp lệ.

## 20. Câu hỏi tự kiểm tra

**1. Hai HTTP requests qua cùng một TCP/TLS socket có phải session resumption không?**  
**Không.** Đó là connection reuse/HTTP pooling. TLS resumption cần một **handshake trên kết nối mới** sử dụng state từ trước.

**2. Một TCP socket bị đóng có xóa TLS session ticket không?**  
**Không nhất thiết.** TLS provider có thể tiếp tục giữ session/ticket để resume trên socket mới cho đến expiry/eviction/policy.

**3. `SSLSession.isValid() == true` có cho biết `SSLSocket` đang mở?**  
**Không.** Đây là trạng thái session, không phải trạng thái transport socket.

**4. `SSLSession.invalidate()` có đóng socket đang chạy ngay không?**  
**Không.** API invalidation nhằm ngăn phiên tham gia/resume trong tương lai; established connection có thể tiếp tục tới khi đóng.

**5. TLS 1.3 dùng gì để resume?**  
**PSK**, thường được gắn với `NewSessionTicket` từ handshake trước; client offer PSK identity + binder, server có thể accept/reject.

**6. Resumption 1-RTT có nhất thiết dùng 0-RTT không?**  
**Không.** 0-RTT là early-data mode riêng, phải được cho phép và phù hợp profile ứng dụng.

**7. Vì sao không gửi lệnh chuyển tiền bằng 0-RTT theo mặc định?**  
Vì early data có thể bị replay giữa các kết nối; mã hóa TLS không ngăn được hành động lặp.

**8. Có thể kết luận ticket được server accept chỉ từ độ trễ handshake thấp không?**  
**Không.** Cần TLS handshake trace hoặc metrics/provider evidence thể hiện PSK/resumption được chọn.

**9. Đặt `setSessionTimeout(600)` có buộc socket đóng sau mười phút?**  
**Không.** Đây là session cache setting, không phải HTTP keep-alive/connection idle timeout.

**10. Đặt `setSessionCacheSize(0)` có disable cache?**  
**Không.** Theo contract của API, 0 nghĩa là không giới hạn kích thước theo setting này; muốn tắt resumption cần policy/provider configuration khác.

**11. Nếu server có ticket key mới nhưng không nhận ticket cũ, client có phải lỗi kết nối không?**  
Không nhất thiết. Nếu policy cho phép, server/client có thể fallback sang full handshake an toàn.

**12. TLS resumption có tự gia hạn application login token không?**  
**Không.** HTTP cookie, JWT, OAuth token hoặc session ứng dụng có vòng đời riêng.

**13. Resumption và KeyUpdate khác nhau ở đâu?**  
Resumption tạo **new handshake trên new connection** dùng trạng thái cũ; KeyUpdate thay application traffic keys **trong connection hiện tại**.

**14. Một service Java sau TLS-terminating reverse proxy có nhìn thấy TLS sessions của browser không?**  
Thông thường **không**. Chúng thuộc gateway/proxy; backend nhìn thấy protocol kết nối của chính nó.

**15. TLS 1.3 resumption có bảo đảm forward secrecy mọi trường hợp không?**  
**Không**: PSK-only, PSK-DHE và 0-RTT có bảo đảm khác nhau; xét ticket/PSK compromise và kết quả DHE.

**16. RFC TLS 1.3 được dùng làm chuẩn chính vào tháng 10/2026 là gì?**  
**RFC 9846**, xuất bản tháng 7/2026, thay thế RFC 8446 nhưng giữ TLS version 1.3 và tương thích về cơ bản.

## 21. Sơ đồ ghi nhớ cuối bài

```text
                APPLICATION HTTP REQUEST
                         |
               live HTTP connection?
                    /          \
                  YES           NO
                   |             |
               reuse socket    new transport connection
                   |             |
               same TLS        TLS handshake begins
               connection         |
                   |         Resumption PSK/ticket available?
                   |               /         \
                   |              YES         NO
                   |               |           |
                   |          Server accepts? full handshake
                   |            /       \
                   |           YES       NO
                   |            |         |
                   |          resumed   full handshake
                   |            |
                   |      1-RTT normally
                   |      0-RTT only if explicitly enabled/replay-safe
                   |            |
                   +------------+----------+
                                |
                         HTTP application data
                                |
                         Close live connection
                                |
                        Ticket may survive
                                |
                         Later new connection

  Important:
    TLS session != TCP socket
    TLS session != login session
    Connection reuse != TLS resumption
    Resumption != 0-RTT
    Session invalidation != socket.close()
```

**Kết luận:** Hãy tách rõ ba vòng đời: **application request**, **transport/TLS connection đang sống**, và **session/resumption state có thể sống qua nhiều connections**. Connection pooling và HTTP/2 tiết kiệm handshake bằng cách **không mở lại kết nối**; TLS session resumption tiết kiệm handshake bằng cách **tái sử dụng trạng thái được xác thực trên một kết nối mới**. TLS 1.3 theo RFC 9846 dùng PSK/tickets cho resumption; 0-RTT là một lựa chọn có rủi ro replay, không phải mặc định của resumption. Trong Java, `SSLSessionContext` giúp cấu hình cache nhưng không chứng minh handshake đã resume, còn `SSLSession.invalidate()` không đóng socket. Một hệ thống production cần policy về lifetime, hostname/authentication, cache/ticket keys, 0-RTT, authorization, logging và đóng kết nối đúng tầng.

### Tài liệu tham khảo

- RFC 9846 (2026) — *The Transport Layer Security (TLS) Protocol Version 1.3*, thay thế RFC 8446: https://www.rfc-editor.org/rfc/rfc9846
- RFC 8446 (2018) — TLS 1.3 bản gốc, nay đã được RFC 9846 thay thế: https://www.rfc-editor.org/rfc/rfc8446
- RFC 9000 — *QUIC: A UDP-Based Multiplexed and Secure Transport*: https://www.rfc-editor.org/rfc/rfc9000
- RFC 9001 — *Using TLS to Secure QUIC*, resumption và early data: https://www.rfc-editor.org/rfc/rfc9001
- Java SE 17, `SSLContext`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLContext.html
- Java SE 17, `SSLSessionContext`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLSessionContext.html
- Java SE 17, `SSLSession`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLSession.html
- Java SE 17, `SSLSocket`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLSocket.html
- Java SE 17, `SSLEngine`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLEngine.html
- Java SE 17, `HttpClient`: https://docs.oracle.com/en/java/javase/17/docs/api/java.net.http/java/net/http/HttpClient.html
- Java SE 17, `HttpResponse.sslSession()`: https://docs.oracle.com/en/java/javase/17/docs/api/java.net.http/java/net/http/HttpResponse.html
- Oracle JSSE Reference Guide — provider, session caching and diagnostic configuration: https://docs.oracle.com/en/java/javase/17/security/java-secure-socket-extension-jsse-reference-guide.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tls-foundation-008">15.8 SNI để chọn máy chủ ảo và ALPN để chọn giao thức ứng dụng</a>

<details>
<summary>Xem chi tiết</summary>

# 15.8. SNI để chọn máy chủ ảo và ALPN để chọn giao thức ứng dụng

**SNI (Server Name Indication)** và **ALPN (Application-Layer Protocol Negotiation)** là hai cơ chế mở rộng TLS giải quyết **hai bài toán độc lập**. SNI cho server biết **tên máy chủ mà client muốn kết nối** ngay trong TLS handshake để server chọn cấu hình/chứng thư của máy chủ ảo. ALPN cho client và server **thương lượng giao thức ứng dụng** dùng bên trong kênh bảo mật, ví dụ HTTP/1.1 hay HTTP/2.

Điểm rất quan trọng: **SNI không xác thực danh tính client hoặc cấp quyền cho request**, còn **ALPN không tự cài đặt HTTP/2 hoặc bất kỳ giao thức ứng dụng nào**. Chúng cùng tham gia handshake nhưng phải phối hợp với chọn certificate, kiểm tra hostname/SAN, xây trust path, quản lý TLS session và application protocol handler. Trong Java 21, các API `SSLParameters`, `SNIHostName`, `ExtendedSSLSession`, `X509ExtendedKeyManager` và `SSLSocket` hỗ trợ thực hành cả hai cơ chế.

Bài này nằm trong chủ đề **“15. Nền tảng TLS”** của Java Advanced – Security & Cryptography. Bài lab dùng **TLS 1.3 và Java 21**, dựng một server TLS trên loopback có hai máy chủ ảo, chọn chứng thư theo SNI và thương lượng ALPN; đồng thời giải thích vì sao một kết nối TLS thành công không đồng nghĩa mọi lớp định danh/giao thức đều đã được xử lý an toàn.

## Mục tiêu học tập

Học xong, bạn có thể:

1. Trình bày SNI nằm ở đâu trong `ClientHello`, giúp chọn **virtual host/certificate** thế nào, vì sao thường cần trước HTTP `Host` hoặc HTTP/2 `:authority`.
2. Trình bày ALPN: client **đề xuất danh sách** application protocol IDs, server **chọn một giá trị được đề xuất và có hỗ trợ**, hoặc handshake không thể dùng protocol phù hợp.
3. Phân biệt **TLS protocol version** (ví dụ TLSv1.3), **cipher suite**, **SNI server name**, **ALPN application protocol** và **HTTP Host/:authority**.
4. Dùng `SSLParameters.setServerNames`, `SNIHostName`, `ExtendedSSLSession.getRequestedServerNames`, `SSLParameters.setApplicationProtocols` và `SSLSocket.getApplicationProtocol` trong Java 21.
5. Giải thích `X509ExtendedKeyManager.chooseServerAlias`/`chooseEngineServerAlias` và điều kiện để chọn đúng certificate theo hostname, đúng key type, đúng trust policy.
6. Tạo/tin cậy certificate cho hai host trong lab, bật **endpoint identification** kiểu HTTPS, đối chiếu SAN, quan sát kết quả TLS handshake.
7. Xử lý trường hợp thiếu/SNI không nhận ra, certificate sai hostname, ALPN không có phần tử chung, protocol handler không hỗ trợ giá trị đã chọn; không tự fallback mất an toàn.
8. Nhận ra vấn đề TLS termination ở reverse proxy, bảo mật thông tin tên miền, logging, kiểm thử và sự khác nhau giữa **thương lượng thành công** với **gửi HTTP thực sự**.

## 1. Hai extension giải quyết hai lớp quyết định khác nhau

Giả sử một IP/port `203.0.113.10:443` phục vụ hai domain:

- `blue.example.test` — certificate chứa DNS SAN cho `blue.example.test`.
- `green.example.test` — certificate chứa DNS SAN cho `green.example.test`.

Client kết nối cùng IP nhưng mong muốn **đúng domain**, và có thể sử dụng **HTTP/1.1** hoặc **HTTP/2**. Trước khi TLS được thiết lập, server phải quyết định certificate phù hợp; trước khi gửi application data, đôi bên phải biết chúng cùng nói giao thức ứng dụng nào.

```text
Client                                            TLS server :443
------                                            ---------------
ClientHello
  supported_versions = TLS 1.3
  server_name (SNI) = blue.example.test  --------> chọn virtual host blue
  ALPN = [http/1.1, demo/1]                        chọn certificate blue
  ...                                              chọn ALPN http/1.1
             <------- ServerHello / TLS handshake / certificate blue
Client kiểm chain + certificate hostname blue
             <------- handshake completes (TLS authenticated)
getApplicationProtocol() = "http/1.1"
             <------- application records theo protocol đã chọn
```

Đây là **sơ đồ logic**, không phải biểu diễn chính xác thứ tự byte của TLS 1.3. Trong TLS 1.3, lựa chọn ALPN của server xuất hiện trong `EncryptedExtensions`, còn certificate và Finished nằm trong các handshake messages tương ứng. Không hiểu sơ đồ như thể ALPN được gửi trong ServerHello của TLS 1.3.

| Câu hỏi | **SNI** | **ALPN** |
| --- | --- | --- |
| Mục tiêu | *Server nào trên IP/port này?* | *Giao thức ứng dụng nào chạy trên kênh TLS này?* |
| Bên cung cấp đề xuất | Client cung cấp tên server, thường DNS hostname | Client cung cấp **danh sách** protocol IDs được hỗ trợ |
| Bên quyết định | Server/terminator ánh xạ hostname → cấu hình/certificate/backend | Server chọn một application protocol được client đề xuất và server hỗ trợ |
| Ví dụ | `blue.example.test` | `http/1.1`, `h2` |
| Gắn với | Máy chủ ảo, certificate selection, virtual routing | HTTP/1.1, HTTP/2, các application protocols dùng TLS |
| Không phải | Kiểm tra hostname, client authentication, authorization | TLS version, cipher suite, framework thực thi ứng dụng |
| Java API | `SNIHostName`, `setServerNames`, `getRequestedServerNames`, key manager | `setApplicationProtocols`, `getApplicationProtocol`, protocol selector |

**Cách nhớ:** SNI quyết định **nói chuyện với host nào**; ALPN quyết định **nói bằng ngôn ngữ giao thức ứng dụng nào**. Cả hai là dữ liệu điều phối handshake, **không thay thế kiểm tra chứng thư và bảo mật kênh**.

## 2. SNI: vì sao HTTP Host đến quá muộn?

Trong HTTPS thông thường, thứ tự là:

1. Client mở kết nối TCP tới một IP và cổng.
2. TLS handshake bắt đầu, server cần **trình certificate** và chứng minh sở hữu private key tương ứng.
3. Client kiểm certificate chain, hostname và handshake.
4. Chỉ sau đó HTTP request mới được gửi qua TLS.

HTTP/1.1 `Host` và HTTP/2 `:authority` nằm trong **HTTP layer**, ở bước 4. Server không thể chờ nhận `Host` trong bản tin HTTP **mà chưa hoàn tất TLS** để biết nên trình chứng thư nào ở bước 2. Vì thế SNI được đặt trong `ClientHello`.

### 2.1 Virtual hosting một IP, nhiều chứng thư

```text
                  DNS
                  |
      blue.example.test ---\
                            -> IP:443 -> TLS ingress/virtual hosts
      green.example.test --/                    |
                                         SNI=blue?  -> key+cert blue
                                         SNI=green? -> key+cert green
                                         unknown?   -> reject/default theo policy
```

Server có thể dùng SNI để:

- Chọn **key pair + certificate chain** phù hợp tên host.
- Chọn TLS policy (chẳng hạn chứng thư/thuật toán hoặc yêu cầu mTLS tùy virtual host).
- Chọn tenant, listener, virtual server hoặc chuyển tiếp qua TLS proxy/ingress theo thiết kế.
- Ghi telemetry ở mức hostname (với chính sách riêng về thông tin nhạy cảm).

Không phải cứ có SNI là server buộc đổi certificate. Một **SAN certificate** có thể chứa nhiều DNS names, hoặc wildcard phù hợp policy có thể phục vụ nhiều tên. SNI chỉ là tín hiệu lựa chọn; **tính hợp lệ của chứng thư được client kiểm riêng**.

### 2.2 SNI là đề nghị, không phải danh tính đã xác thực

Client có thể tự điền bất kỳ hostname nào vào SNI; client độc hại có thể gửi tên không tồn tại, tên của tenant khác hoặc không gửi extension. Vì vậy:

- **Không** dùng “client gửi SNI=admin.example.test” làm bằng chứng client thuộc nhóm admin.
- **Không** bỏ qua authorization trên ứng dụng chỉ vì TLS terminator chọn đúng virtual host.
- Không cấp quyền dựa vào SNI nếu không có kiểm soát xác thực độc lập.
- Nếu SNI không hợp lệ/không được hỗ trợ, thiết kế rõ **reject**, default certificate hoặc default tenant. Đừng silently route tenant nhạy cảm sang tenant khác.
- Đối chiếu SNI với HTTP Host/:authority theo đúng proxy policy để tránh hostname/tenant confusion, request smuggling và định tuyến nhầm.

### 2.3 Chuyện gì xảy ra khi client không gửi SNI?

Khả năng thường gặp: client kết nối trực tiếp bằng địa chỉ IP, client cũ không hỗ trợ SNI, hoặc ứng dụng bỏ cấu hình `setServerNames`. Server có thể chọn default certificate, từ chối, hoặc dùng routing khác theo cấu hình; **không có đáp án duy nhất**.

Nếu server chọn certificate `blue.example.test` nhưng client mong `green.example.test`, client có bật hostname verification kiểu HTTPS **phải từ chối** chứng thư không khớp, ngay cả khi CA chain hợp lệ. Việc server “gửi được certificate” không có nghĩa client phải tin nó.

### 2.4 Quy tắc hostname thực tế

- SNI `host_name` dùng tên DNS tương thích định dạng TLS, **không chứa scheme `https://`, port `:443`, URL path hoặc token**.
- `SNIHostName` hỗ trợ tên DNS và xử lý mã hóa/ASCII theo Java API; ứng dụng cần quy ước hostname, IDN/Punycode và normalization cho dữ liệu đầu vào.
- Một địa chỉ IP không phải DNS hostname bình thường cho extension `host_name`; client kết nối IP cần kiểm tra certificate IP SAN theo policy, không mặc định lấy một DNS SNI giả.
- Certificate identity verification phải xét **DNS SAN hoặc IP SAN** theo ứng dụng/giao thức, không chỉ kiểm xem subject CN có vẻ giống.
- Wildcard và hostname dạng quốc tế cần tuân thủ quy tắc xác minh chuẩn, tránh tự so `String.endsWith` để thay TLS hostname verifier.

### 2.5 SNI có “mã hóa” không?

Trong TLS thông thường, tên SNI ở `ClientHello` thường **có thể bị quan sát** bởi thiết bị trên đường truyền; HTTPS mã hóa nội dung ứng dụng không mặc định che giấu tên server. Cơ chế **Encrypted ClientHello (ECH)** có thể bảo vệ thông tin hostname thực bên trong ClientHello trong những triển khai hỗ trợ, nhưng không nên giả định mọi JDK/client/server đều đã triển khai ECH hoặc nó luôn được bật. ALPN cũng không phải cơ chế ẩn danh. Tránh đưa thông tin định danh riêng tư vào tên virtual host hoặc log SNI không kiểm soát.

## 3. ALPN: thương lượng giao thức nào sẽ đi bên trong TLS?

**ALPN** theo RFC 7301 cho phép client cung cấp các identifiers của **application protocols** mà client hỗ trợ. Server lựa chọn **một** identifier phù hợp; lựa chọn này là cam kết dùng giao thức tương ứng trên kết nối đã xác thực.

Ví dụ client đề xuất:

```text
ALPN offered by client = ["demo/1", "http/1.1"]
Server supports        = ["http/1.1", "demo/1"]
Selected               = "http/1.1"
```

Trong bài lab, server có `http/1.1` đầu danh sách ưu tiên, vì thế kết quả là `http/1.1`. Giá trị `demo/1` là protocol ID chỉ để **minh họa negotiation**, **không phải** một chuẩn ứng dụng được triển khai trong bài.

Các giá trị quen thuộc:

- `http/1.1`: HTTP/1.1 qua TLS.
- `h2`: HTTP/2 qua TLS, cần endpoint thực sự có HTTP/2 framing, HPACK, multiplexing và các yêu cầu của giao thức.
- `h3`: HTTP/3 qua QUIC (không phải “HTTP/3 trên `SSLSocket` TCP”); QUIC dùng TLS handshake theo cách tích hợp khác JSSE socket minh họa ở đây.
- Các protocol IDs chuyên dụng khác do giao thức/profile xác định; không được tự giả mạo một ID được đăng ký cho giao thức mà server chưa hỗ trợ.

**Không đồng nhất:** `TLSv1.3` là phiên bản giao thức bảo vệ kênh; `TLS_AES_128_GCM_SHA256` là TLS 1.3 cipher suite; `http/1.1` là application protocol thông qua ALPN. Không thể cho `setApplicationProtocols(new String[]{"TLSv1.3"})` rồi kỳ vọng thương lượng phiên bản TLS.

### 3.1 Server chọn thế nào?

- Client thông báo những protocol IDs **mà nó thật sự có thể nói**.
- Server chỉ chọn một ID trong danh sách client gửi **và** server thực sự hỗ trợ.
- Nếu client và server không có giao thức chung và ALPN được yêu cầu để chọn protocol, RFC 7301 yêu cầu server kết thúc bằng alert **`no_application_protocol`**. Không lén chọn giao thức không được quảng bá.
- Sau khi chọn, endpoint **phải dùng đúng** protocol ID đã thương lượng; không đáp lại `h2` rồi thực tế gửi HTTP/1.1.
- Nếu handshake **không thương lượng ALPN**, `getApplicationProtocol()` có thể là chuỗi trống. Ứng dụng phải có chính sách rõ ràng: khi nào cho phép fallback legacy, khi nào fail-closed.

Không phải “ALPN fail thì tự hạ xuống HTTP/1.1” luôn an toàn. Với server yêu cầu HTTP/2 hoặc application protocol chuyên dụng, **không có ALPN hoặc ALPN sai phải bị từ chối** theo profile.

### 3.2 ALPN không biến một socket thành HTTP/2

`SSLSocket.getApplicationProtocol()` chỉ trả giá trị đã được TLS thương lượng. Phần code gửi/nhận HTTP/1.1, HTTP/2 hoặc protocol ứng dụng phải được framework hoặc ứng dụng thực hiện **sau** handshake. Bài lab của chúng ta chỉ mở TLS và kiểm kết quả ALPN, **không gửi bất kỳ HTTP request nào**; vì vậy không được gọi đó là “HTTP server thực tế”.

Với Java `HttpClient`, server như Netty/Jetty hoặc JSSE-based framework, ALPN thường do stack HTTP/TLS tích hợp quản lý. Không cần tự viết client/server TLS socket trong production chỉ để dùng ALPN.

## 4. Liên hệ SNI – certificate – ALPN – HTTP routing

Một kiến trúc nhiều tenant:

```text
TLS ClientHello
  SNI = blue.example.test
  ALPN offered = [http/1.1, demo/1]
           |
           v
TLS terminator / ingress
  1. Map SNI → vhost blue
  2. Select key + certificate chain của blue
  3. Select ALPN application protocol http/1.1
  4. Complete TLS handshake
           |
           v
Client verifies certificate chain + hostname blue
           |
           v
Application handler for http/1.1 (if implemented)
           |
           v
HTTP Host + route + tenant + authentication/authorization
```

Các bước này có phạm vi riêng:

| Bước | Nguồn dữ liệu | Quyết định | Nếu bỏ kiểm tra |
| --- | --- | --- | --- |
| SNI | ClientHello hostname | Chọn certificate/vhost | Có thể trình nhầm certificate hoặc route nhầm tenant |
| TLS certificate | Certificate server trình | Client tin root nào, cert có khớp hostname không? | MITM/giả server nếu bỏ trust/hostname verification |
| ALPN | Danh sách ứng dụng client + policy server | Chọn đúng handler/protocol | HTTP/1.1/HTTP/2 framing không khớp, lỗi hoặc protocol confusion |
| HTTP Host/:authority | HTTP message sau TLS | Route request đến virtual service | Domain fronting/mismatch/tenant confusion nếu tin mù quáng |
| Authentication | Client credentials, mTLS, session/token | Ai là người gọi? | SNI không chứng minh danh tính người dùng |
| Authorization | Policy ứng dụng | Người gọi được phép làm gì? | TLS bắt tay tốt nhưng vẫn có thể thực thi yêu cầu trái quyền |

### 4.1 TLS được terminate ở đâu?

Ở reverse proxy, ingress hoặc load balancer có thể có các mô hình:

- **TLS termination tại edge:** edge nhận SNI, chọn certificate, thương lượng ALPN rồi giải mã HTTP; kết nối giữa edge và backend là **một hop khác**, phải có chính sách TLS, trust, header và identity riêng.
- **TLS passthrough:** proxy không giải mã handshake/HTTP có thể định tuyến theo SNI nhìn thấy ở ClientHello nếu cấu hình cho phép; backend vẫn trình certificate và làm TLS handshake.
- **TLS re-encryption:** edge kết thúc TLS và tạo TLS mới đến backend, có **SNI/ALPN mới** cho hop backend; không mặc định giữ nguyên tên/cert/client identity ở hai hop.

Không chuyển giá trị HTTP `X-Forwarded-Host` hoặc `Host` do client tự đưa thành nguồn xác thực tenant nếu chưa có chính sách reverse proxy loại bỏ/ghi đè headers không tin cậy. Chứng thực HTTPS ở hop client-edge không tự cung cấp TLS end-to-end đến backend.

### 4.2 Chọn chứng thư trước khi xác minh danh tính server

Server đọc SNI để chọn certificate. Client **không được** lấy tên certificate server gửi rồi thay tên đích cần xác minh. Tên đích phải đến từ URI/target/endpoint mà client **dự định** truy cập; SNI thường được gửi theo chính tên ấy. Nếu server đổi sang cert hợp lệ của domain khác, hostname verification phải fail.

## 5. Java 21 API: SNI và ALPN nằm ở đâu?

| API | Dùng ở đầu nào? | Vai trò |
| --- | --- | --- |
| `SSLParameters.setServerNames(List<SNIServerName>)` | Client | Đặt SNI extension cho handshake |
| `new SNIHostName("blue.example.test")` | Client | Đóng gói DNS hostname dạng SNI |
| `ExtendedSSLSession.getRequestedServerNames()` | Server | Đọc các SNI names client yêu cầu từ handshake/session |
| `X509ExtendedKeyManager.chooseServerAlias(...)` | Server với `SSLSocket` | Chọn alias/cert phù hợp key type, issuers và SNI |
| `X509ExtendedKeyManager.chooseEngineServerAlias(...)` | Server với `SSLEngine` | Như trên nhưng dùng engine cho NIO/framework |
| `SSLParameters.setSNIMatchers(...)` | Server | Có thể ràng buộc tên SNI chấp nhận; không tự thay thế key/cert routing |
| `SSLParameters.setApplicationProtocols(String[])` | Client và server | Đặt danh sách ALPN IDs hỗ trợ (thường theo thứ tự ưu tiên) |
| `SSLSocket.getApplicationProtocol()` | Hai đầu, sau handshake | Trả ID được thương lượng, `""` nếu không dùng ALPN, `null` nếu chưa xác định |
| `SSLSocket.getHandshakeApplicationProtocol()` | Trong handshake | Xem ALPN value đang thương lượng nếu đã có |
| `SSLSocket.setHandshakeApplicationProtocolSelector(...)` | Server | Callback chọn ID trong danh sách client đề xuất |
| `SSLEngine` tương ứng | Framework non-blocking | Có API SNI/ALPN/handshake tương ứng |
| `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")` | Client | Yêu cầu kiểm server identity theo thuật toán endpoint `HTTPS` |
| `TrustManagerFactory` / `KeyManagerFactory` | Client/server | Dùng trust anchors và private keys/certificate chains hợp lệ |

### 5.1 Tên miền SNI do client đặt

Ví dụ minh họa (đoạn này **không độc lập**, giả sử biến `SSLSocket socket` đã được tạo):

```java
SSLParameters p = socket.getSSLParameters();
p.setServerNames(List.of(new SNIHostName("blue.example.test")));
p.setApplicationProtocols(new String[] {"http/1.1"});
p.setEndpointIdentificationAlgorithm("HTTPS");
socket.setSSLParameters(p);
socket.startHandshake();
String protocol = socket.getApplicationProtocol();
```

Với production, client phải cung cấp truststore/trust manager đúng, sử dụng hostname đích tin cậy, không vô hiệu hóa endpoint identification và chỉ advertise ALPN IDs mình có thể implement.

### 5.2 Server nhận SNI trong handshake

Trong `X509ExtendedKeyManager.chooseServerAlias`, có thể lấy `((SSLSocket) socket).getHandshakeSession()` và nếu đó là `ExtendedSSLSession`, đọc `getRequestedServerNames()`. Việc lựa chọn certificate còn phải đáp ứng **keyType, issuers, key algorithm, provider policy**. Không chọn alias mù quáng chỉ vì trùng hostname, rồi bỏ mọi điều kiện mật mã.

Với `SSLEngine`, không có `Socket` để xem SNI; dùng `chooseEngineServerAlias` và `engine.getHandshakeSession()`. Một key manager chỉ override `chooseServerAlias` có thể **không hoạt động đúng** khi framework dùng `SSLEngine`.

### 5.3 ALPN selector tường minh

Ví dụ **minh họa** bên trong server loop trước `startHandshake()`:

```java
connection.setHandshakeApplicationProtocolSelector((socket, offered) ->
        offered.contains("http/1.1") ? "http/1.1" : null);
```

Ở đây `offered` là danh sách ALPN do client quảng bá; `null` báo không chọn được ID. Trong ALPN, provider có thể kết thúc kết nối bằng alert `no_application_protocol`. Selector có thể override danh sách cấu hình tĩnh bằng `setApplicationProtocols`, nên **không** trả giá trị không có trong danh sách client nếu muốn tránh handshake failure. Trong ứng dụng thật, logic selector phải dựa vào capability và policy, không phải “chọn một giá trị cho đẹp log”.

Bài lab dùng `setApplicationProtocols` hai phía để minh họa thứ tự ưu tiên; không dùng callback custom trong chương trình chính.

## 6. Thực hành Java 21: hai virtual hosts, hai chứng thư, một TLS listener

Chúng ta sẽ tạo một **TLS server trên loopback** không cần internet, có hai certificate được cùng một CA lab ký:

```text
server.p12:
  lab-ca    : private key + CA certificate          (chỉ vì lab)
  blue      : private key + leaf SAN blue.example.test + CA chain
  green     : private key + leaf SAN green.example.test + CA chain

trust.p12:
  lab-ca    : TrustedCertificateEntry (chỉ certificate công khai)
```

Trong thực tế phải **tách CA private key** khỏi ứng dụng server và bảo vệ bằng hệ thống PKI/HSM/offline CA phù hợp. Fixture có CA key cùng `server.p12` chỉ để tự tạo chứng thư nhanh trong một thư mục tạm. Tuyệt đối không triển khai file lab lên production.

Client không dùng default trust-all; thay vào đó, `TrustManagerFactory` tin **chỉ CA lab đã chủ đích import**. Client bật endpoint identification `"HTTPS"`, so sánh thêm DNS SAN để dễ đọc output. Server dùng một `X509ExtendedKeyManager` bọc `SunX509` chọn alias **blue hoặc green** theo SNI.

### 6.1 Chuẩn bị JDK và password lab

Dùng JDK 21 hoặc phiên bản mới hơn có hỗ trợ `--release 21` và provider SunJSSE/SunJCE thông thường. Tạo thư mục lab riêng và mở terminal tại đó.

**PowerShell:**

```powershell
$env:TLS_DEMO_PASS = 'LabOnly-SniAlpn42'
```

**Bash:**

```bash
export TLS_DEMO_PASS='LabOnly-SniAlpn42'
```

Đây là **mật khẩu ví dụ công khai**, không phải secret đủ an toàn cho môi trường thật. `keytool` đọc password bằng `-storepass:env TLS_DEMO_PASS`, tránh đưa **giá trị** trực tiếp vào command line. Biến môi trường vẫn có rủi ro bị lộ qua môi trường chạy/log và không thay thế secret manager.

### 6.2 Tạo CA chỉ dùng để ký hai chứng thư test

Chạy một dòng:

```text
keytool -genkeypair -alias lab-ca -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -dname "CN=Local Lab CA,O=Training,C=VN" -ext "BC:critical=ca:true,pathlen:0" -ext "KU:critical=keyCertSign,cRLSign" -validity 20 -keystore server.p12 -storetype PKCS12 -storepass:env TLS_DEMO_PASS -keypass:env TLS_DEMO_PASS -noprompt
```

CA có `BasicConstraints CA:TRUE, pathLen:0`, dùng ký leaf trực tiếp. Đây là **self-signed test CA**, không được tin bởi các hệ thống công khai.

### 6.3 Tạo hai virtual host certificates với DNS SAN riêng

```text
keytool -genkeypair -alias blue -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -dname "CN=blue.example.test,O=Training,C=VN" -ext "SAN=dns:blue.example.test" -ext "KU:critical=digitalSignature,keyEncipherment" -ext "EKU=serverAuth" -signer lab-ca -signerkeypass:env TLS_DEMO_PASS -validity 12 -keystore server.p12 -storetype PKCS12 -storepass:env TLS_DEMO_PASS -keypass:env TLS_DEMO_PASS -noprompt
keytool -genkeypair -alias green -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -dname "CN=green.example.test,O=Training,C=VN" -ext "SAN=dns:green.example.test" -ext "KU:critical=digitalSignature,keyEncipherment" -ext "EKU=serverAuth" -signer lab-ca -signerkeypass:env TLS_DEMO_PASS -validity 12 -keystore server.p12 -storetype PKCS12 -storepass:env TLS_DEMO_PASS -keypass:env TLS_DEMO_PASS -noprompt
```

Với `-signer lab-ca`, `keytool` tạo mỗi leaf do CA lab ký và lưu chain trong cùng PKCS12. Aliases `blue` và `green` là **tên entry nội bộ**, khác với tên host DNS. Trong ví dụ, key manager ánh xạ DNS name sang alias cố định; ánh xạ này do ứng dụng/tenant configuration xác định.

### 6.4 Xuất CA public certificate và tạo truststore cho client

```text
keytool -exportcert -rfc -alias lab-ca -keystore server.p12 -storetype PKCS12 -storepass:env TLS_DEMO_PASS -file ca.pem
keytool -importcert -alias lab-ca -file ca.pem -keystore trust.p12 -storetype PKCS12 -storepass:env TLS_DEMO_PASS -noprompt
```

`trust.p12` chứa một `TrustedCertificateEntry` CA lab, không chứa CA private key. Việc import là **quyết định tin cậy chủ động** chỉ cho bài lab. Không import CA này vào system truststore hoặc JDK `cacerts` dùng cho ứng dụng thực.

Kiểm tra cấu trúc:

```text
keytool -list -v -alias blue -keystore server.p12 -storetype PKCS12 -storepass:env TLS_DEMO_PASS
keytool -list -v -alias green -keystore server.p12 -storetype PKCS12 -storepass:env TLS_DEMO_PASS
keytool -list -v -alias lab-ca -keystore trust.p12 -storetype PKCS12 -storepass:env TLS_DEMO_PASS
```

Hai alias `blue`/`green` là `PrivateKeyEntry` chứa leaf tương ứng cùng chain; CA trong truststore là `trustedCertEntry` (cách viết nhãn phụ thuộc JDK). Chỉ đọc metadata, không xuất private key.

## 7. Chương trình chạy đầy đủ `SniAlpnDemo.java`

Ví dụ dùng **TLSv1.3** và `SSLServerSocket` nhận hai kết nối loopback. Socket của client thực sự kết nối địa chỉ loopback; `createSocket(tcp, name, port, true)` đặt **peer hostname logic** thành `blue.example.test` hoặc `green.example.test`. Nhờ đó thử được hostname verification mà **không cần sửa DNS hoặc file hosts** trên máy.

Các điểm sẽ quan sát:

- SNI `blue.example.test` → `SniKeyManager` chọn alias `blue` → certificate SAN blue.
- SNI `green.example.test` → alias `green` → certificate SAN green.
- Client đề xuất `demo/1` trước `http/1.1`; server có thứ tự `http/1.1` trước `demo/1`. Giá trị được chọn là `http/1.1` trong provider SunJSSE đã kiểm thử.
- Client dùng `setEndpointIdentificationAlgorithm("HTTPS")` và CA lab truststore; nếu sai SAN/certificate, handshake không được xem là thành công.
- Server đọc SNI thông qua `ExtendedSSLSession` **sau** handshake và in hai tên đã nhận.
- Server không gửi HTTP; kết quả ALPN chỉ chứng minh **thương lượng tên protocol**, không chứng minh implement HTTP.

Tạo file **`SniAlpnDemo.java`** (tên class public phải khớp file):

```java
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;

public class SniAlpnDemo {
    static final class SniKeyManager extends X509ExtendedKeyManager {
        private final X509ExtendedKeyManager base;
        SniKeyManager(X509ExtendedKeyManager base) { this.base = base; }

        private String aliasFor(List<SNIServerName> requested) {
            for (SNIServerName name : requested) {
                if (name instanceof SNIHostName host) {
                    if ("blue.example.test".equalsIgnoreCase(host.getAsciiName())) return "blue";
                    if ("green.example.test".equalsIgnoreCase(host.getAsciiName())) return "green";
                }
            }
            return null;
        }
        private String selected(String keyType, Principal[] issuers,
                                List<SNIServerName> requested) {
            String required = aliasFor(requested);
            if (required == null) return null; // Do not silently use another cert.
            String[] compatible = base.getServerAliases(keyType, issuers);
            if (compatible == null) return null;
            for (String candidate : compatible) {
                if (candidate.equalsIgnoreCase(required)) return candidate;
            }
            return null;
        }
        @Override public String chooseServerAlias(String type,
                        Principal[] issuers, Socket socket) {
            if (!(socket instanceof SSLSocket ssl)) return null;
            if (!(ssl.getHandshakeSession() instanceof ExtendedSSLSession session))
                return null;
            return selected(type, issuers, session.getRequestedServerNames());
        }
        @Override public String chooseEngineServerAlias(String type,
                        Principal[] issuers, SSLEngine engine) {
            if (!(engine.getHandshakeSession() instanceof ExtendedSSLSession session))
                return null;
            return selected(type, issuers, session.getRequestedServerNames());
        }
        @Override public String[] getClientAliases(String type, Principal[] issuers) {
            return base.getClientAliases(type, issuers);
        }
        @Override public String chooseClientAlias(String[] types,
                        Principal[] issuers, Socket socket) {
            return base.chooseClientAlias(types, issuers, socket);
        }
        @Override public String chooseEngineClientAlias(String[] types,
                        Principal[] issuers, SSLEngine engine) {
            return base.chooseEngineClientAlias(types, issuers, engine);
        }
        @Override public String[] getServerAliases(String type, Principal[] issuers) {
            return base.getServerAliases(type, issuers);
        }
        @Override public X509Certificate[] getCertificateChain(String alias) {
            return base.getCertificateChain(alias);
        }
        @Override public PrivateKey getPrivateKey(String alias) {
            return base.getPrivateKey(alias);
        }
    }

    static KeyStore load(Path path, char[] password) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream stream = Files.newInputStream(path)) {
            store.load(stream, password);
        }
        return store;
    }

    static SSLContext serverContext(KeyStore identity, char[] password)
            throws Exception {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(identity, password);
        X509ExtendedKeyManager delegate = null;
        for (KeyManager manager : kmf.getKeyManagers()) {
            if (manager instanceof X509ExtendedKeyManager extended) {
                delegate = extended;
                break;
            }
        }
        if (delegate == null) throw new IllegalStateException("No X509 key manager");
        SSLContext context = SSLContext.getInstance("TLSv1.3");
        context.init(new KeyManager[]{new SniKeyManager(delegate)},
                     null, new SecureRandom());
        return context;
    }

    static SSLContext clientContext(KeyStore anchors) throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(anchors);
        SSLContext context = SSLContext.getInstance("TLSv1.3");
        context.init(null, tmf.getTrustManagers(), new SecureRandom());
        return context;
    }

    static String requestedHost(SSLSocket socket) {
        if (socket.getSession() instanceof ExtendedSSLSession session) {
            for (SNIServerName n : session.getRequestedServerNames()) {
                if (n instanceof SNIHostName host) return host.getAsciiName();
            }
        }
        return "(missing)";
    }

    static boolean sanMatches(X509Certificate leaf, String name)
            throws Exception {
        Collection<List<?>> sans = leaf.getSubjectAlternativeNames();
        if (sans == null) return false;
        for (List<?> san : sans) {
            if (Integer.valueOf(2).equals(san.get(0))
                    && name.equalsIgnoreCase((String) san.get(1))) return true;
        }
        return false;
    }

    static void connect(SSLContext context, int port, String name)
            throws Exception {
        try (Socket tcp = new Socket(InetAddress.getLoopbackAddress(), port);
             SSLSocket tls = (SSLSocket) context.getSocketFactory()
                     .createSocket(tcp, name, port, true)) {
            SSLParameters parameters = tls.getSSLParameters();
            parameters.setProtocols(new String[]{"TLSv1.3"});
            parameters.setServerNames(List.of(new SNIHostName(name)));
            parameters.setApplicationProtocols(
                    new String[]{"demo/1", "http/1.1"});
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            tls.setSSLParameters(parameters);
            tls.startHandshake();
            X509Certificate cert =
                    (X509Certificate) tls.getSession().getPeerCertificates()[0];
            System.out.println(name + ": ALPN=" + tls.getApplicationProtocol()
                    + ", certificate SAN matches=" + sanMatches(cert, name));
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException(
                "Usage: java SniAlpnDemo server.p12 trust.p12");
        String env = System.getenv("TLS_DEMO_PASS");
        if (env == null) throw new IllegalStateException("Missing TLS_DEMO_PASS");
        char[] password = env.toCharArray();
        try {
            SSLContext serverCtx = serverContext(load(Path.of(args[0]), password),
                                                 password);
            SSLContext clientCtx = clientContext(load(Path.of(args[1]), password));
            String[] seen = new String[2];
            AtomicReference<Throwable> failure = new AtomicReference<>();
            try (SSLServerSocket listener = (SSLServerSocket)
                    serverCtx.getServerSocketFactory().createServerSocket(
                            0, 2, InetAddress.getLoopbackAddress())) {
                Thread server = new Thread(() -> {
                    try {
                        for (int i = 0; i < 2; i++) {
                            try (SSLSocket connection = (SSLSocket) listener.accept()) {
                                SSLParameters p = connection.getSSLParameters();
                                p.setProtocols(new String[]{"TLSv1.3"});
                                p.setApplicationProtocols(
                                        new String[]{"http/1.1", "demo/1"});
                                connection.setSSLParameters(p);
                                connection.startHandshake();
                                seen[i] = requestedHost(connection);
                            }
                        }
                    } catch (Throwable t) {
                        failure.set(t);
                    }
                });
                server.start();
                connect(clientCtx, listener.getLocalPort(), "blue.example.test");
                connect(clientCtx, listener.getLocalPort(), "green.example.test");
                server.join();
                if (failure.get() != null) throw new Exception(failure.get());
                System.out.println("server SNI: " + String.join(", ", seen));
            }
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
```

### 7.1 Biên dịch và chạy

Sau khi có `server.p12`, `trust.p12` và `SniAlpnDemo.java` trong thư mục hiện tại:

```text
javac --release 21 -encoding UTF-8 SniAlpnDemo.java
java SniAlpnDemo server.p12 trust.p12
```

**Stdout đã kiểm thử:**

```text
blue.example.test: ALPN=http/1.1, certificate SAN matches=true
green.example.test: ALPN=http/1.1, certificate SAN matches=true
server SNI: blue.example.test, green.example.test
```

Fixture được tạo và chương trình đã được biên dịch bằng `javac --release 21`, chạy thành công với JSSE trong JDK có sẵn. Certificate, serial number, private keys, phiên TLS và session secrets thay đổi giữa các lần chạy nhưng những dòng boolean/ALPN dự kiến vẫn như trên nếu giữ cấu hình. Nếu môi trường dùng provider khác `SunX509` hoặc không hỗ trợ thuật toán, API có thể trả lỗi; xem phần troubleshooting.

### 7.2 Vì sao lại dùng `createSocket(tcp, name, port, true)`?

Kết nối TCP thực tế vào `InetAddress.getLoopbackAddress()` ở port server được cấp ngẫu nhiên. Tên peer logic `blue.example.test` dùng làm **hostname đích để TLS endpoint identification**. Thêm `new SNIHostName(name)` khiến hostname SNI rõ ràng dù kết nối vào loopback. Nhờ đó tránh cài DNS/hosts giả, nhưng **không** nên coi đây là lời khuyên bỏ qua DNS và tự giả hostname trong sản phẩm: tên đích phải đến từ cấu hình tin cậy.

### 7.3 Vì sao `SniKeyManager` phải kiểm cả `keyType`?

Trong JSSE, server certificate selection chịu ràng buộc scheme/key type, provider, issuer constraints và signatures mà hai phía hỗ trợ. Chọn alias đúng hostname nhưng key algorithm **không phù hợp** vẫn có thể làm TLS handshake thất bại hoặc gây policy violation. Wrapper dùng `base.getServerAliases(keyType, issuers)` rồi chỉ trả alias nếu có trong danh sách tương thích.

Wrapper dành cho **bài lab**, không phải bộ chọn certificate production đầy đủ: không có hot reload, wildcard matching, IDN rules, fallback policy, quyền tenant, chain expiration/rotation, logging/monitoring, cache hoặc kiểm thử mọi provider. Trong sản phẩm, ưu tiên capability của TLS server/framework được bảo trì, cấu hình key manager đáng tin và test đầy đủ.

### 7.4 Vì sao server vẫn nhận được SNI sau TLS handshake?

`ExtendedSSLSession.getRequestedServerNames()` cung cấp SNI names đã nhận. Trong `chooseServerAlias`/`chooseEngineServerAlias`, code đọc từ **handshake session** vì quyết định certificate phải được đưa ra *trước* khi handshake hoàn tất. Sau handshake, phương thức `requestedHost` của demo xem thông tin từ established session chỉ để in log.

Nếu không có SNI hoặc không có host được map, wrapper trả `null` thay vì tự chọn certificate của tenant khác. Provider có thể thử các key types khác nhau; nếu không có alias phù hợp thì handshake không được chấp nhận. Một deployment có policy default certificate rõ ràng có thể thiết kế khác, nhưng phải giữ hostname verification và tenant isolation.

### 7.5 Vì sao `getApplicationProtocol()` trả HTTP/1.1?

Client set ALPN list `["demo/1","http/1.1"]`; server set `["http/1.1","demo/1"]`. Với SunJSSE trong lab, server chọn `http/1.1` theo ưu tiên của server. **ALPN không đọc SNI để tự quyết định HTTP routing**; đây là thương lượng protocol IDs độc lập với virtual host selection.

Giá trị `"http/1.1"` **không có nghĩa chương trình đã phục vụ HTTP/1.1**. Lab không có HTTP parser hoặc response; cả hai socket chỉ hoàn thành TLS handshake rồi đóng. Một ứng dụng thật phải tiếp tục dispatch tới **protocol handler** tương ứng. Với HTTP/2, handler phải biết framing/multiplexing và những ràng buộc HTTP/2 TLS theo profile.

### 7.6 Vì sao tin CA lab nhưng vẫn cần xác minh hostname?

`TrustManagerFactory` với `trust.p12` cho client biết **CA nào được tin**. Từ đó JSSE có thể xác minh chain được server trình. Nhưng CA đó có thể cấp cả hai tên blue/green, nên **hostname verification** vẫn phải xác định certificate của **đúng DNS name mà client dự kiến**.

`setEndpointIdentificationAlgorithm("HTTPS")` yêu cầu kiểm identity thích hợp với hostname peer. Hàm `sanMatches` tự so DNS SAN để in boolean chỉ giúp người học quan sát, **không thay thế** hostname verification chuẩn (wildcard, IDN, IP và các quy tắc khác cần chuẩn TLS client).

## 8. Điều gì xảy ra khi cấu hình SNI hoặc ALPN sai?

| Tình huống | Hệ quả điển hình | Cách xử lý đúng |
| --- | --- | --- |
| Client không gửi SNI | Server chọn default hoặc từ chối theo policy; có thể chọn cert không khớp tên đích | Thiết kế default/fail-closed, bật hostname verification phía client |
| SNI là `red.example.test` nhưng server chỉ có blue/green | Không có alias/cert phù hợp trong demo; handshake có thể fail | Không tự route sang tenant khác; báo lỗi handshake an toàn |
| SNI gửi blue nhưng client định kết nối green | Chứng thư blue không khớp peer hostname green | Xác thực hostname lấy từ target, không lấy từ chứng thư được trình |
| Chain đúng root nhưng SAN không khớp | PKIX trust thành công vẫn chưa đủ xác thực endpoint | Bật `HTTPS` endpoint identification |
| Client chỉ đề xuất `h2`, server chỉ hỗ trợ `http/1.1` | ALPN không có giao thức chung, thường fatal `no_application_protocol` | Chỉ chọn ID có chung; từ chối khi profile bắt buộc |
| ALPN ID là `h2` nhưng server chỉ có HTTP/1.1 handler | Phá quy tắc ứng dụng, framing mismatch | Chỉ advertise giao thức được cài đặt và kiểm thử |
| ALPN không được thương lượng, `getApplicationProtocol()==""` | Không có protocol ID đã chọn từ ALPN | Có fallback **chỉ nếu** profile cho phép; còn lại từ chối |
| Giả HTTP Host khác SNI | Có nguy cơ cross-tenant/misdirected request nếu routing mù quáng | Ràng buộc SNI, endpoint, HTTP authority và tenant policy |
| Provider Java thiếu ALPN/SNI APIs hoặc hành vi khác | `UnsupportedOperationException`, handshake error hoặc lựa chọn khác | Kiểm runtime/provider và các khả năng được hỗ trợ |
| Hợp lệ lần đầu, reload cert/rotate key không đồng bộ | Kết nối mới có thể dùng chain cũ/sai, SNI map lỗi | Quy trình cập nhật atomic, kiểm tra từng hostname và rollback |
| Proxy terminate TLS nhưng backend tưởng có cùng SNI | Backend nhận connection TLS mới với peer host/suite khác | Định nghĩa trust và routing riêng cho mỗi TLS hop |
| SSL server chưa load đúng private key/certificate alias | Không chọn được certificate thích hợp | Kiểm tra KeyStore alias/type, key password, chain và key manager |
| Dùng IP address khi client cần domain | Không thể dựa vào SNI DNS mặc định; certificate IP SAN có rule khác | Dùng hostname đích đáng tin hoặc thiết kế identity cho IP |

### 8.1 Nguyên tắc không fallback mất an toàn

Trong một hệ thống nhiều tenant, nếu `SNI=tenant-b` nhưng không tìm được certificate tenant-b, **đừng tự chọn** tenant-a và bỏ qua kiểm certificate name. Nếu ALPN yêu cầu `h2` nhưng server chỉ implement HTTP/1.1, **đừng trả `h2`** rồi gửi dữ liệu HTTP/1.1. Nếu hostname verification thất bại, **đừng tạo trust-all manager** để test rồi giữ lại trong production.

Cơ chế bảo mật phải **fail closed** theo policy. Ngoại lệ có thể được chấp nhận khi có thiết kế explicit, ví dụ một default certificate cho domain legacy được phép, nhưng cần xác định phạm vi và kiểm thử.

## 9. Phân biệt SNI/ALPN với các chức năng TLS khác

### 9.1 SNI không phải chứng thư, chứng thư không phải SNI

SNI chỉ là **tên khách yêu cầu**. Certificate xác nhận một **public key gắn với các DNS names/IP SAN và được CA ký**. Client phải xác minh path tới trust anchor và name thuộc target.

SNI có thể bị client đổi tùy ý; không nên dùng SNI thay client certificate hoặc JWT/session. Mặt khác, một certificate có thể phục vụ nhiều SNI values (SAN multi-domain/wildcard) nên không nhất thiết một alias/host.

### 9.2 ALPN không phải TLS cipher negotiation

TLS cipher suite xác định cơ chế mã hóa/kỹ thuật bảo vệ TLS records; ALPN chọn **ứng dụng** chạy trên những records ấy. TLS 1.3 và HTTP/2 không cùng tầng. Một server có thể hỗ trợ TLS 1.3 nhưng chỉ ứng dụng HTTP/1.1, hoặc chọn HTTP/2 trên TLS 1.3 nếu có handler và profile phù hợp.

### 9.3 ALPN không phải HTTP URL routing

`ALPN=http/1.1` cho biết byte stream sau handshake tuân theo HTTP/1.1; lựa chọn service cụ thể (`/api/v1`) vẫn dựa vào HTTP routing và authorization. `SNI=blue.example.test` có thể ảnh hưởng virtual host, nhưng không thay thế phân tích HTTP Host/:authority và quyền ứng dụng.

### 9.4 ECH, session resumption và HTTP/2 connection reuse

Khi đánh giá hệ thống thật:

- **ECH** có thể che giấu hostname SNI thực; phải xem deployment và client/server support, không dựa vào SNI ngoài plaintext để quyết định danh tính/chính sách trong mọi môi trường.
- **TLS session resumption/PSK** có các ràng buộc về virtual host và application protocol; không mặc định có một full certificate exchange mới ở mọi kết nối được resumed.
- **HTTP/2** có thể multiplex nhiều request/stream và có những quy tắc coalescing/origin; server phải xử lý authority và chứng thư hợp lệ, không chỉ dựa vào SNI của handshake đầu tiên.
- Với reverse proxy đa tenant, cần kiểm thử sự nhất quán giữa original target, SNI, certificate, ALPN, Host/:authority và upstream destination khi reuse/reconnect.

Đây là các vấn đề **thiết kế giao thức và triển khai**; không thể kết luận chỉ từ một giá trị `SSLSession.getProtocol()` hoặc một log SNI.

## 10. Debug và quan sát trong môi trường Java

Khi một kết nối thành công bằng `curl` nhưng Java bị `SSLHandshakeException`, hãy phân loại:

1. **DNS/peer host:** client có đang nối đúng target? Layered socket có truyền đúng peer hostname để endpoint identification?
2. **SNI:** client có gửi `SNIHostName` hợp lệ? Server có nhận tên đó, chọn đúng key alias/cert không?
3. **Chứng thư:** root/intermediate chain có đúng, truststore chứa trust anchor hợp lệ, leaf SAN khớp hostname, thời hạn/algorithm/key usage phù hợp?
4. **ALPN:** client đề xuất IDs nào? Server hỗ trợ/chọn ID nào? Application handler có thực thi protocol đã chọn?
5. **TLS policy:** có TLS version/cipher suite chung không? Client auth/mTLS có yêu cầu thêm certificate phía client không?
6. **Proxy:** TLS kết thúc ở edge hay backend? Có hai TLS hops khác nhau không?
7. **Runtime:** Java version/provider/key manager có hỗ trợ SNI/ALPN theo cách dự kiến không?

Để debug lab, JSSE có các tùy chọn `javax.net.debug`; ví dụ chỉ bật **trong môi trường test cô lập**:

```text
java -Djavax.net.debug=ssl,handshake -cp . SniAlpnDemo server.p12 trust.p12
```

Log handshake có thể rất dài và chứa metadata nhạy cảm (hostname, certificate chain, thông tin cấu hình), nên **không bật rộng rãi trong production**, không sao chép log có secret sang nơi công khai. Debug chỉ nhằm xác định extension/nhánh handshake, không làm thay đổi trust policy.

Khi cần công cụ bên ngoài trong môi trường mà bạn **được quyền kiểm thử**, có thể dùng `openssl s_client` với `-servername` và `-alpn` để quan sát SNI/ALPN từ một server thật. Trong bài lab, listener là cổng loopback ngẫu nhiên và kết thúc sau hai kết nối, nên không phải một server sống để debug thủ công sau khi chương trình hoàn thành.

## 11. Kiểm thử có hệ thống

| Mục kiểm thử | Mong đợi |
| --- | --- |
| SNI blue với certificate blue | Handshake thành công, SAN và hostname blue hợp lệ |
| SNI green với certificate green | Handshake thành công, SAN và hostname green hợp lệ |
| SNI không tồn tại, không có default policy | Không chọn cert/virtual host sai; handshake từ chối |
| Certificate blue dùng cho target green | Hostname verification từ chối |
| Chứng thư ký bởi CA không nằm truststore | PKIX/trust manager từ chối |
| Client ALPN `demo/1,http/1.1`; server ưu tiên `http/1.1` | Chọn `http/1.1` và chỉ chạy HTTP/1.1 handler nếu thực sự tồn tại |
| Client chỉ có `h2`, server chỉ có `http/1.1` | Từ chối theo chính sách ALPN, không chọn protocol lạ |
| Không có ALPN ở một phía | Giá trị không thương lượng theo API; xử lý theo profile, không suy đoán |
| Thay HTTP Host sang tenant khác | Routing/authorization không tự mở quyền chéo tenant |
| Proxy terminate + re-encrypt | Đúng certificate/trust/SNI/ALPN riêng mỗi hop |
| Mỗi alias reload certificate mới | Chọn đúng chain, hostname, key type sau khi rotate |
| Một ứng dụng báo ALPN `h2` nhưng không implement HTTP/2 | Bị test bắt lỗi; không đưa vào production |
| Client dùng IP SAN thay DNS SAN | Kiểm đúng identity policy và case không có DNS SNI |
| KeyManager chỉ xử lý `SSLSocket` nhưng app dùng `SSLEngine` | Phải có `chooseEngineServerAlias` hoặc cấu hình framework đúng |

### 11.1 Bài tập

1. **Vẽ đường đi:** một HTTPS client kết nối `blue.example.test` trên IP chung với green. Tại thời điểm nào SNI được gửi, certificate nào phải được chọn và tại sao HTTP Host không thể dùng để chọn certificate ở đầu handshake?
2. **Nhận diện API:** trong `SniAlpnDemo`, xác định method cấu hình SNI client, method đọc SNI server khi chọn key, method chọn ALPN và method lấy giá trị ALPN cuối cùng.
3. **Thay thứ tự ALPN:** đảo danh sách server từ `["http/1.1","demo/1"]` thành `["demo/1","http/1.1"]` và xem provider chọn gì. Sau đó giải thích vì sao chỉ đổi label không có nghĩa ứng dụng đã hỗ trợ giao thức mới.
4. **SNI không nhận dạng:** nghĩ cách sửa `aliasFor` để xử lý hostname chưa cấu hình. Vì sao chọn default alias theo tên gần giống có thể gây vấn đề tenant isolation?
5. **Endpoint identity:** bỏ `setEndpointIdentificationAlgorithm("HTTPS")` và trả lời rủi ro bảo mật (không khuyến nghị bỏ trong code production). Phân biệt CA chain được tin và tên server đúng target.
6. **Một certificate nhiều SAN:** giả sử blue và green dùng chung một certificate có cả hai SAN; SNI còn có giá trị gì cho routing và policy?
7. **HTTP/2:** nếu client gửi `ALPN=h2`, server phải có thêm các thành phần ứng dụng nào? Nếu server chỉ có HTTP/1.1 handler, kết quả an toàn cần là gì?
8. **Hai TLS hops:** edge chọn cert theo SNI bên ngoài rồi kết nối backend TLS; hãy xác định backend SNI/ALPN, cách xác thực backend và vai trò HTTP Host/:authority.
9. **Quan sát:** trên môi trường test, bật JSSE handshake debug và tìm ClientHello SNI, danh sách ALPN, certificate đã chọn; tránh chia sẻ dữ liệu nhạy cảm trong log.
10. **Privacy:** vì sao TLS 1.3 mã hóa dữ liệu ứng dụng vẫn không nhất thiết giấu được tên SNI? ECH thay đổi điều kiện nào?
11. **Phân quyền:** một request tới hostname `admin.example.test` dùng chứng thư hợp lệ và HTTP/2. Vì sao user vẫn cần authentication và authorization?
12. **Provider portability:** giải thích vì sao `KeyManagerFactory.getInstance("SunX509")` và mapping alias của ví dụ cần kiểm tra nếu chuyển sang một provider khác hoặc HSM.

**Tiêu chí hoàn thành:** bạn có thể tự trả lời ba câu hỏi cho một kết nối HTTPS đa domain: **(1) SNI nào được gửi và certificate/vhost nào được chọn? (2) Client đã kiểm trust anchor + hostname của certificate chưa? (3) ALPN chọn ứng dụng nào và phía server/client có handler thực sự cho ứng dụng ấy không?** Nếu thiếu bất kỳ lớp nào, TLS có thể thành công một phần nhưng chức năng định tuyến hoặc bảo mật ứng dụng vẫn sai.

## 12. Tài liệu chính thức để đối chiếu

- [Java SE 21 – `SSLParameters`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/net/ssl/SSLParameters.html): `setServerNames`, `setApplicationProtocols`, `setSNIMatchers` và `setEndpointIdentificationAlgorithm`.
- [Java SE 21 – `SNIHostName`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/net/ssl/SNIHostName.html) và [`SNIServerName`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/net/ssl/SNIServerName.html): cấu trúc tên server trong TLS.
- [Java SE 21 – `ExtendedSSLSession`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/net/ssl/ExtendedSSLSession.html): `getRequestedServerNames` và handshake/session metadata.
- [Java SE 21 – `X509ExtendedKeyManager`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/net/ssl/X509ExtendedKeyManager.html): `chooseServerAlias`, `chooseEngineServerAlias`, certificate chain/private key.
- [Java SE 21 – `SSLSocket`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/net/ssl/SSLSocket.html): `getApplicationProtocol`, `getHandshakeApplicationProtocol`, protocol selector, `getHandshakeSession` và handshake behavior.
- [Java SE 21 – `SSLServerSocket`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/net/ssl/SSLServerSocket.html) và [`SSLEngine`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/net/ssl/SSLEngine.html): TLS server listener và engine non-blocking.
- [Java SE 21 – JSSE Reference Guide](https://docs.oracle.com/en/java/javase/21/security/java-secure-socket-extension-jsse-reference-guide.html): TLS configuration, ALPN, SNI, key/trust managers và debug.
- [RFC 6066](https://www.rfc-editor.org/rfc/rfc6066): TLS Extension Definitions, `server_name` (SNI).
- [RFC 7301](https://www.rfc-editor.org/rfc/rfc7301): ALPN extension, giao thức chọn và alert `no_application_protocol`.
- [RFC 8446](https://www.rfc-editor.org/rfc/rfc8446): TLS 1.3 ClientHello, EncryptedExtensions, certificate và session semantics.
- [RFC 9113](https://www.rfc-editor.org/rfc/rfc9113): HTTP/2 và HTTP/2 over TLS, ALPN `h2`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tls-foundation-009">15.9 Phân biệt TLS trên luồng với DTLS trên datagram</a>

<details>
<summary>Xem chi tiết</summary>

# 15.9 Phân biệt TLS trên luồng với DTLS trên datagram

## 1. Học gì trong bài này?

**TLS (Transport Layer Security)** và **DTLS (Datagram Transport Layer Security)** đều dùng handshake và lớp record để bảo vệ trao đổi dữ liệu qua mạng không tin cậy. Chúng có chung những mục tiêu như bảo mật nội dung, xác thực bản tin và (khi cấu hình phù hợp) xác thực peer, nhưng **có giả định vận chuyển hoàn toàn khác nhau**:

- **TLS trên luồng:** thường chạy trên **TCP**, nhận một **byte stream tin cậy, đúng thứ tự**. TCP chịu trách nhiệm đưa byte đến đúng thứ tự hoặc báo kết nối đã hỏng; TLS bảo vệ nội dung byte stream đó.
- **DTLS trên datagram:** thường chạy trên **UDP**, không đòi mọi datagram đều phải đến hoặc đến theo thứ tự. DTLS bổ sung xử lý handshake bị mất/đảo, số thứ tự record và cơ chế chống replay để bảo vệ dữ liệu mà **vẫn giữ datagram semantics**.

Câu nói cần nhớ: **TLS/TCP bảo vệ một luồng byte có thứ tự; DTLS/UDP bảo vệ các datagram có thể mất, trùng, đến muộn hoặc đảo thứ tự.** DTLS **không biến UDP thành TCP**.

Bài này nằm trong chủ đề **Nền tảng TLS** và tập trung vào phân biệt transport/record semantics, xử lý handshake, chi phí mạng, cách chọn API Java 17+ và kiểm thử. Ví dụ runnable ở phần 8 là **demo TCP/UDP + khám phá JSSE TLS/DTLS**, không phải hai kết nối bảo mật đã hoàn thành TLS/DTLS handshake; sự khác nhau được nêu rõ để không gây ảo tưởng bảo mật.

### Mục tiêu sau bài

1. Phân biệt **TCP stream**, **UDP datagram**, **TLS record** và **DTLS record**.
2. Giải thích vì sao UDP không thể chỉ “nhét TLS vào” mà không thay đổi giao thức.
3. Biết DTLS xử lý **handshake retransmission, fragmentation, reordering, epoch/sequence và replay detection**.
4. Chọn đúng **`SSLSocket`/`SSLServerSocket` cho TLS/TCP** hoặc **`SSLContext("DTLS")` + `SSLEngine` + datagram I/O** trong Java.
5. Nhận biết DTLS không mặc nhiên bảo đảm **độ tin cậy, thứ tự và xử lý giao dịch đúng một lần**.
6. Đánh giá khi nào chọn TLS, DTLS hoặc QUIC/TLS 1.3 cho bài toán mạng.

## 2. Mô hình phân tầng: đừng trộn transport và security

```text
        TLS/TCP thông dụng                      DTLS/UDP thông dụng
  +-----------------------------+        +------------------------------+
  | Ứng dụng: HTTP, RPC, ...     |        | Ứng dụng: realtime, sensor...|
  +-----------------------------+        +------------------------------+
  | TLS: handshake + records     |        | DTLS: handshake + records    |
  | encrypt, integrity, identity |        | encrypt, integrity, identity |
  +-----------------------------+        +------------------------------+
  | TCP: stream byte tuần tự     |        | UDP: datagrams độc lập       |
  | tin cậy trong kết nối        |        | có thể mất/đảo/trùng         |
  +-----------------------------+        +------------------------------+
  | IP                           |        | IP                           |
  +-----------------------------+        +------------------------------+
```

**Ứng dụng** quyết định “một request là gì”; **transport** quyết định cách vận chuyển byte/gói tin; **TLS/DTLS** quyết định cách bắt tay, chọn khóa và bảo vệ dữ liệu. Không protocol nào tự suy ra người dùng có quyền thanh toán, ghi dữ liệu, hay dùng lại một request đã thực hiện hay không.

| Đơn vị | Có ý nghĩa gì? | Có giữ ranh giới ứng dụng không? |
| --- | --- | --- |
| TCP **byte stream** | Một chuỗi byte liên tục giữa hai endpoint | **Không**. Hai lần `write` có thể được đọc thành một hoặc nhiều lần `read` |
| TCP **segment** | Đơn vị truyền tải trên mạng của TCP | Không tương ứng với message ứng dụng |
| **TLS record** | Đơn vị framing/bảo vệ của TLS | Không nên dùng record boundary làm application message boundary |
| UDP **datagram** | Một gói tin có độ dài/địa chỉ nguồn đích riêng | Có ranh giới datagram, nhưng không có reliability hay order |
| **DTLS record** | Đơn vị bảo vệ của DTLS có metadata record/epoch | Được đóng gói vào datagram; không có bảo đảm delivery/in-order ở tầng ứng dụng |

**Lưu ý về UDP:** nếu một datagram lớn hơn bộ đệm `DatagramPacket`, nội dung có thể bị **truncation**; chương trình phải dùng `packet.getLength()` và kiểm tra giới hạn. Một UDP datagram không tự động đồng nghĩa một giao dịch nghiệp vụ, và DTLS record không tự động đồng nghĩa một request.

## 3. Bảng đối chiếu TLS stream và DTLS datagram

| Khía cạnh | TLS trên stream, thường TCP | DTLS trên datagram, thường UDP |
| --- | --- | --- |
| Yêu cầu transport | Reliable + ordered byte stream | Datagram không tin cậy, có thể out-of-order |
| Dữ liệu phía ứng dụng | Byte stream sau giải mã | Dữ liệu bảo vệ theo datagram/record |
| `write(A)`, `write(B)` | Nhận byte theo thứ tự `AB` nếu chuyển thành công; không giữ hai boundary | Có thể nhận `A,B`, `B,A`, chỉ `B`, lặp hoặc mất |
| Truyền lại gói mất | **TCP** retransmit | **DTLS handshake** có retransmission; application data **không tự retransmit** |
| Handshake | Dựa trên transport tin cậy, thứ tự | Có số thứ tự/fragment, buffering, timeout, retransmit |
| Record ordering | Dựa trên thứ tự stream và ngữ cảnh TLS record | Có record sequence/epoch để nhận ra record và reordering |
| Anti-replay | Chống sửa record/trộn thứ tự trong stream theo scheme | DTLS có cơ chế replay window phù hợp version/policy, nhưng không giải quyết business replay |
| MTU/packet size | TCP segmentation giúp che giấu chi tiết packet khỏi ứng dụng | Phải tính record overhead và path MTU, tránh IP fragmentation |
| Head-of-line | Mất một đoạn TCP có thể chặn byte sau trên cùng connection | Không bị TCP stream buộc giao đúng thứ tự; ứng dụng có thể tự giữ hàng đợi nếu cần |
| Định danh peer | Certificate/PSK + trust/hostname policy | Tương tự, nhưng phải kiểm soát UDP peer/session/DoS |
| Java JSSE | `SSLSocket`, `SSLServerSocket`, `SSLEngine` | `SSLContext.getInstance("DTLS")`, `SSLEngine`, tự quản lý UDP I/O |
| Độ trễ | Tùy congestion, packet loss, server, handshake, RTT | Tùy packet loss, MTU, NAT, retransmission handshake, logic ứng dụng; **không mặc nhiên nhanh hơn** |

**Bảo mật mật mã không đồng nghĩa độ tin cậy truyền tải.** DTLS có thể xác minh được một record nhưng **không ép được mạng phải gửi record đó tới nơi**. Việc thiết lập session thành công cũng không có nghĩa ứng dụng đã nhận mọi dữ liệu phía gửi đã phát.

## 4. TLS/TCP: một luồng byte, không phải hàng đợi thông điệp

Giả sử sender gọi:

```text
write("one")
write("two")
```

TCP cho bên nhận một chuỗi 6 byte:

```text
o n e t w o
```

Receiver có thể `read()` được `"o"`, `"net"`, `"wo"`; hoặc `"onetwo"`; hoặc bất kỳ cách chia hợp lệ khác. Việc sender `write` hai lần **không** buộc receiver `read` đúng hai lần. Điều này vẫn đúng khi thay socket thường bằng `SSLSocket` và dùng **TLS over TCP**.

### 4.1 Phải có framing ở tầng ứng dụng

Một giao thức ứng dụng trên TLS stream cần cách phân định message:

- **Length prefix:** trường độ dài (ví dụ 4 byte unsigned/giới hạn ứng dụng) rồi đúng số byte body.
- **Delimiter có escaping:** có quy tắc phân tách và escape đủ mạnh.
- **Framing chuẩn sẵn có:** HTTP/2, WebSocket, gRPC và các giao thức có cấu trúc riêng.

Đoạn dưới đây **không độc lập**: `tlsSocket` là `SSLSocket` đã được xác thực và handshake thành công, hai bên đồng thuận length-prefix:

```java
byte[] message = "ping".getBytes(java.nio.charset.StandardCharsets.UTF_8);
java.io.DataOutputStream output =
        new java.io.DataOutputStream(tlsSocket.getOutputStream());
output.writeInt(message.length);
output.write(message);
output.flush();

// Ở bên nhận, trên một SSLSocket khác đã xác thực:
java.io.DataInputStream input =
        new java.io.DataInputStream(peerSocket.getInputStream());
int length = input.readInt();
if (length < 0 || length > 65536) {
    throw new java.io.IOException("Invalid frame length");
}
byte[] received = new byte[length];
input.readFully(received);
```

Việc **kiểm tra upper bound trước khi cấp phát** là bắt buộc trong một hệ thống nhận dữ liệu từ peer chưa đáng tin hoàn toàn. Không viết code chỉ gọi `read()` một lần rồi mặc định đã đủ body.

### 4.2 TCP và TLS chịu trách nhiệm khác nhau

- TCP: vận chuyển **byte** đúng thứ tự, retransmission, congestion/flow control, phát hiện kết nối hỏng.
- TLS: handshake, lựa chọn các tham số mật mã, xác thực peer khi cấu hình đúng, bảo vệ ciphertext/integrity; không thay thế framing ứng dụng.
- JSSE `SSLSocket`: kết nối hai lớp đó và cung cấp input/output stream sau khi đã xử lý TLS.

**TLS record không phải TCP segment**, và **TLS record không phải application frame**. JSSE có thể đóng gói một message ứng dụng trong nhiều records hoặc gom dữ liệu theo nhu cầu implementation.

## 5. DTLS/UDP: bảo vệ datagram nhưng vẫn cho phép loss/reorder

UDP gửi từng datagram với source/destination address và độ dài; không đảm bảo giao đủ, chỉ một lần, theo thứ tự. DTLS thêm bảo vệ mật mã cho record trên mô hình này:

```text
Ứng dụng tạo A ------> DTLS record A -----> UDP datagram A -----+
                                                               |
Ứng dụng tạo B ------> DTLS record B -----> UDP datagram B --+ |
                                                            | |
                                                   Mạng: mất/trễ/đảo/trùng
                                                            | |
                                         DTLS kiểm tra record, epoch, seq, tag
                                                   |
                                         Trả dữ liệu hợp lệ cho ứng dụng
```

Khi network deliver hai datagrams theo thứ tự `B` rồi `A`, DTLS có thể xử lý dữ liệu hợp lệ mà không bắt buộc xếp lại thành một **TCP-like stream**. Khi `A` mất, DTLS **không tự phát lại application record A**. Cần tránh hiểu nhầm rằng “datagram được bảo vệ mật mã” đồng nghĩa “datagram chắc chắn được giao”.

### 5.1 Vì sao không đơn giản dùng TLS records qua UDP?

RFC 6347 và RFC 9147 chỉ ra các khác biệt nền tảng:

1. **TLS records trên reliable stream** dựa vào thứ tự; khi mất hoặc reorder datagrams, các thông số thứ tự implicit có thể không còn đồng bộ. **DTLS** mang record number/epoch cần thiết để nhận dạng record và giải mã tương ứng.
2. **TLS handshake** có trình tự logic nhất định và giả định vận chuyển hỗ trợ thứ tự; **DTLS handshake** phải có message sequence, bộ đệm và cách tái sắp xếp.
3. Handshake messages như certificate chain có thể lớn hơn một datagram: **DTLS hỗ trợ phân mảnh và ghép lại handshake**.
4. Với UDP, attacker có thể giả địa chỉ nguồn và làm server phát nhiều byte đến nạn nhân: **DTLS có return-routability/cookie mechanisms** để giảm chi phí/anti-amplification trước khi tin kết nối.

DTLS còn phải xử lý **key epoch** khi đổi trạng thái khóa, bản tin handshake trễ và anti-replay record. Các chi tiết record layout **khác nhau giữa DTLS 1.2 và DTLS 1.3**, nên không tự suy rằng các trường bit trên wire giống nhau.

### 5.2 Sequence number và epoch

```text
Khái niệm:
   (epoch 1, record 100): protected(A)
   (epoch 1, record 101): protected(B)

Receiver có thể thấy record 101 trước record 100.
DTLS xác định record bằng metadata, không lấy vị trí gói đến làm thứ tự duy nhất.
```

**Epoch** phân biệt các thế hệ key/state khác nhau. **Sequence number/record number** giúp nhận diện record để xử lý reordering, kiểm tra integrity và chống replay theo scheme. Không dùng record sequence number như `transactionId` của thanh toán hoặc `messageId` của ứng dụng; hai lớp có threat model khác nhau.

### 5.3 Anti-replay là hạn chế record lặp, không phải exactly-once

Một implementation DTLS có thể dùng **sliding replay window**: nhận diện record đã thấy hoặc quá cũ, và loại bỏ. Tùy version/policy, replay detection có thể tùy chọn, giới hạn window khác nhau và việc gói đến trễ có thể khiến record hợp lệ bị bỏ.

Nếu client tạo một **record mới** chứa cùng payload `"transfer=42"`, record ấy có sequence mới và có thể vượt qua anti-replay DTLS; đây vẫn là một yêu cầu nghiệp vụ trùng. Do đó cấp ứng dụng cần **idempotency key**, sequence nghiệp vụ, expiry, nonces, state và policy chống replay riêng khi hành động có side effects.

### 5.4 DTLS handshake retransmission khác data retransmission

Trong DTLS, **handshake phải hoàn tất** để hai bên thống nhất khóa và xác thực. Nếu một flight handshake mất, protocol có **timeout và retransmission**. Bên ứng dụng JSSE phải quản lý timer của `SSLEngine`, sau đó kích hoạt việc tạo lại các handshake packets theo contract.

Ngược lại, với **application datagrams đã handshake xong**, DTLS không tự bảo đảm ACK/retransmission/ordering. Ứng dụng thời gian thực có thể chủ động bỏ dữ liệu cũ; hệ thống cần bảo đảm delivery phải tự bổ sung reliability hoặc dùng transport tin cậy.

**Chỉ trong lớp handshake** DTLS cần reliability để hoàn tất đàm phán. Đừng kết luận “UDP + DTLS luôn có TCP-like retransmission”.

### 5.5 Fragmentation: handshake có quy tắc riêng

DTLS có fragmentation/reassembly đối với **handshake messages** lớn hơn kích thước packet. Một DTLS record được đóng gói vừa một datagram theo mục tiêu của protocol; nhưng UDP datagram lớn vẫn có thể gây **IP fragmentation** ở lớp dưới nếu vượt PMTU. Không coi việc DTLS xử lý handshake fragments là chức năng chia/gộp **toàn bộ file ứng dụng** hoặc một thông điệp business rất lớn.

Đối với payload lớn cần đúng thứ tự/đủ byte, dùng TLS/TCP hoặc giao thức đáng tin cậy được thiết kế rõ; nếu buộc UDP thì phải có segmentation, reassembly, per-message identification, max size, timeout, resend/ACK và chống cạn kiệt bộ nhớ.

## 6. TLS/DTLS security: giống về mục tiêu nhưng khác thuộc tính vận chuyển

Các cặp protocol thường gặp:

| Protocol | RFC | Kiểu transport |
| --- | --- | --- |
| TLS 1.2 | RFC 5246 | Reliable byte stream |
| TLS 1.3 | RFC 8446 | Reliable byte stream |
| DTLS 1.2 | RFC 6347 | Datagrams |
| DTLS 1.3 | RFC 9147 | Datagrams |

DTLS 1.3 dựa trên TLS 1.3, nhưng RFC 9147 đặc biệt lưu ý sự khác biệt về **order protection / non-replayability** vì datagram semantics. Đừng nói rằng DTLS bảo đảm mọi gói tới đúng thứ tự chỉ vì nó cung cấp confidentiality và integrity.

### 6.1 Java version khác giao thức version

Java 17 có **standard names** cho `SSLContext`: `TLS`, `TLSv1.2`, `TLSv1.3`, `DTLS`, `DTLSv1.0`, `DTLSv1.2`. Việc tên được chuẩn hóa **không bảo đảm** mọi Java runtime/provider triển khai mọi phiên bản. Java 17 có thể có DTLS 1.2 nhưng **không tự suy ra có DTLS 1.3** chỉ vì RFC DTLS 1.3 đã được công bố.

Ba câu hỏi cần tách:

- **Supported protocols?** `SSLEngine.getSupportedProtocols()` — provider có implementation/đường hỗ trợ?
- **Enabled protocols?** `SSLEngine.getEnabledProtocols()` — phiên bản nào được cho phép trên engine?
- **Negotiated protocol?** `engine.getSession().getProtocol()` **sau khi handshake thành công** mới phản ánh phiên được chọn.

**Không tự fallback** sang TLSv1.0/DTLSv1.0 hay vô hiệu hóa thuật toán khi bắt tay không tương thích. Cần kiểm tra policy bảo mật và lựa chọn một phiên bản được phê duyệt.

### 6.2 QUIC không phải DTLS

**HTTP/3 dùng QUIC trên UDP**, nhưng **không dùng DTLS**. RFC 9001 định nghĩa QUIC sử dụng **TLS 1.3 handshake/key schedule** để thiết lập bảo mật, còn QUIC tự xử lý packet protection, reliable streams và congestion control. QUIC không vận chuyển TLS application records theo cách TLS/TCP và cũng không đơn giản dùng `SSLContext.getInstance("DTLS")`.

Do đó:

- HTTPS HTTP/1.1/2 trên TCP: **TLS/TCP**.
- UDP application cần bảo vệ datagrams: **DTLS/UDP** có thể thích hợp.
- HTTP/3: **QUIC + TLS 1.3 integration** qua QUIC stack.

## 7. Java JSSE: `SSLSocket` khác `SSLEngine`

Java Secure Socket Extension (**JSSE**) có hai phong cách triển khai:

| Java API | Sử dụng phù hợp |
| --- | --- |
| `SSLSocket` / `SSLServerSocket` | TLS trên kết nối **stream**, thực hiện I/O qua socket TCP tương ứng |
| `SSLEngine` | TLS/DTLS crypto và handshake **tách biệt khỏi I/O**, ứng dụng tự chuyển ciphertext |
| `SSLContext.getInstance("TLS")` | Tạo TLS context theo provider |
| `SSLContext.getInstance("DTLS")` | Tạo DTLS context theo provider hỗ trợ |
| `DatagramSocket` / `DatagramChannel` | Gửi/nhận **UDP datagrams**; tự nó không cung cấp mã hóa/xác thực |
| `SSLParameters.setMaximumPacketSize(...)` | Hỗ trợ cấu hình kích thước network packet/record mong muốn, quan trọng với DTLS |
| `SSLEngineResult` | Status, handshake status và số byte đã tiêu thụ/sinh ra khi `wrap/unwrap` |

### 7.1 TLS stream: API ngắn gọn hơn

Đoạn sau là **mảnh code**, chỉ có thể chạy nếu `hostname` được cung cấp và server phù hợp:

```java
// Trong phương thức có throws IOException, trên Java 17+:
javax.net.ssl.SSLSocketFactory factory =
        (javax.net.ssl.SSLSocketFactory)
        javax.net.ssl.SSLSocketFactory.getDefault();
try (javax.net.ssl.SSLSocket socket =
        (javax.net.ssl.SSLSocket) factory.createSocket(hostname, 443)) {
    javax.net.ssl.SSLParameters p = socket.getSSLParameters();
    p.setEndpointIdentificationAlgorithm("HTTPS");
    socket.setSSLParameters(p);
    socket.startHandshake();
    // Phía ứng dụng đọc/ghi từ InputStream/OutputStream qua framing riêng.
}
```

Phải xác minh server certificate qua trust manager và endpoint identity, chọn version/cipher suites theo chính sách, cấu hình SNI/ALPN khi cần. **Không tạo trust manager tin mọi chứng thư** để cho demo chạy bằng mọi giá.

### 7.2 DTLS: `SSLContext("DTLS")` + `SSLEngine` + UDP transport

```text
Ứng dụng gửi plaintext
       |
  SSLEngine.wrap()   -- tạo DTLS protected record(s)
       |
DatagramSocket.send() -- gửi UDP datagram
       |
       mạng
       |
DatagramSocket.receive() -- nhận đúng datagram bytes
       |
  SSLEngine.unwrap() -- kiểm tra và giải mã record
       |
Ứng dụng xử lý plaintext hợp lệ
```

Khởi tạo context/engine là **chưa đủ** cho bảo mật. Với handshake thực cần **key managers / trust managers / SecureRandom** thích hợp, identity/peer selection, UDP read/write loop, state machine, retransmission timer và packet-size policy.

```java
// Mảnh minh họa: chỉ tạo engine, CHƯA handshake, CHƯA bảo vệ dữ liệu.
javax.net.ssl.SSLContext dtls = javax.net.ssl.SSLContext.getInstance("DTLS");
dtls.init(keyManagers, trustManagers, secureRandom);
javax.net.ssl.SSLEngine engine = dtls.createSSLEngine(peerHost, peerPort);
engine.setUseClientMode(true);
javax.net.ssl.SSLParameters p = engine.getSSLParameters();
p.setMaximumPacketSize(1200);
engine.setSSLParameters(p);
engine.beginHandshake();
// Cần một loop xử lý SSLEngineResult + DatagramSocket đúng chuẩn.
```

Đây là **fragment tích hợp**, không phải chương trình độc lập: `keyManagers`, `trustManagers`, `secureRandom`, `peerHost` và `peerPort` là dữ liệu/cấu hình của hệ thống, `engine.beginHandshake()` không tự giao tiếp với peer. Client không thể tự giải mã UDP datagrams nếu chưa hoàn thành handshake.

## 8. Ví dụ Java 17 chạy được: stream, datagrams và khả năng JSSE

Chương trình **`TlsDtlsTransportDemo.java`** cho phép quan sát: (1) TLS và DTLS context được provider hỗ trợ, (2) DTLS packet-size parameter, (3) hai TCP writes được đọc như một stream, (4) hai UDP datagrams được nhận thành hai gói riêng trên **loopback local**.

**Giới hạn bắt buộc:** TCP và UDP ở đây là **plain sockets, chưa được mã hóa**. Việc khởi tạo `SSLContext`/`SSLEngine` chỉ nhằm khám phá API; **không có TLS/DTLS handshake**, không có certificate verification và không có ciphertext trên network. Để thật sự bảo vệ payload phải dùng `SSLSocket` hoặc `SSLEngine.wrap/unwrap` sau handshake hoàn tất.

```java
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

public final class TlsDtlsTransportDemo {
    private static boolean supports(SSLEngine engine, String protocol) {
        return Arrays.asList(engine.getSupportedProtocols()).contains(protocol);
    }

    public static void main(String[] args) throws Exception {
        SSLContext tls = SSLContext.getInstance("TLS");
        SSLContext dtls = SSLContext.getInstance("DTLS");
        tls.init(null, null, null);
        dtls.init(null, null, null);

        SSLEngine tlsEngine = tls.createSSLEngine();
        SSLEngine dtlsEngine = dtls.createSSLEngine();
        tlsEngine.setUseClientMode(true);
        dtlsEngine.setUseClientMode(true);

        System.out.println("TLSv1.2 supported: " +
                supports(tlsEngine, "TLSv1.2"));
        System.out.println("DTLSv1.2 supported: " +
                supports(dtlsEngine, "DTLSv1.2"));

        SSLParameters parameters = dtlsEngine.getSSLParameters();
        parameters.setMaximumPacketSize(1200);
        dtlsEngine.setSSLParameters(parameters);
        System.out.println("DTLS max packet bytes: " +
                dtlsEngine.getSSLParameters().getMaximumPacketSize());

        InetAddress loopback = InetAddress.getLoopbackAddress();
        try (ServerSocket listener = new ServerSocket(0, 1, loopback);
             Socket client = new Socket(loopback, listener.getLocalPort());
             Socket peer = listener.accept()) {
            client.getOutputStream().write("one".getBytes(StandardCharsets.UTF_8));
            client.getOutputStream().write("two".getBytes(StandardCharsets.UTF_8));
            client.getOutputStream().flush();
            byte[] six = peer.getInputStream().readNBytes(6);
            System.out.println("TCP stream bytes: " +
                    new String(six, StandardCharsets.UTF_8));
        }

        try (DatagramSocket receiver =
                     new DatagramSocket(new InetSocketAddress(loopback, 0));
             DatagramSocket sender = new DatagramSocket()) {
            receiver.setSoTimeout(3000);
            byte[] first = "one".getBytes(StandardCharsets.UTF_8);
            byte[] second = "two".getBytes(StandardCharsets.UTF_8);
            sender.send(new DatagramPacket(first, first.length,
                    loopback, receiver.getLocalPort()));
            sender.send(new DatagramPacket(second, second.length,
                    loopback, receiver.getLocalPort()));

            String[] messages = new String[2];
            for (int i = 0; i < messages.length; i++) {
                byte[] buffer = new byte[64];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                receiver.receive(packet);
                messages[i] = new String(packet.getData(), packet.getOffset(),
                        packet.getLength(), StandardCharsets.UTF_8);
            }
            System.out.println("UDP datagrams received: " + messages.length);
            System.out.println("UDP payloads (this local run): " +
                    String.join(" | ", messages));
        }
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 TlsDtlsTransportDemo.java
java TlsDtlsTransportDemo
```

**Stdout đã quan sát trong môi trường kiểm thử JDK có provider DTLS 1.2 và network loopback:**

```text
TLSv1.2 supported: true
DTLSv1.2 supported: true
DTLS max packet bytes: 1200
TCP stream bytes: onetwo
UDP datagrams received: 2
UDP payloads (this local run): one | two
```

**Phân tích:**

1. `TLSv1.2 supported` và `DTLSv1.2 supported`: là kết quả **capability của provider**. Nếu runtime không có DTLS, `SSLContext.getInstance("DTLS")` có thể ném `NoSuchAlgorithmException`, hoặc supported protocols khác.
2. `DTLS max packet bytes: 1200`: xác nhận `SSLParameters` nhận cấu hình. Nó **không** chứng minh mọi IP path đều có MTU 1200 byte hay rằng handshake đã hoàn tất.
3. TCP gửi `"one"` rồi `"two"`, `readNBytes(6)` trả `"onetwo"`, minh họa stream không có message boundary của lệnh `write`.
4. UDP hai lần `send` tạo hai datagrams riêng; hai lần `receive` lấy hai datagram. Loopback trong lần chạy giữ thứ tự `"one"`, `"two"`, nhưng UDP **không bảo đảm** điều này trên mọi mạng.
5. `setSoTimeout(3000)` chỉ chống treo vô hạn khi một UDP datagram không đến; trên mạng có packet loss, chương trình có thể ném `SocketTimeoutException`. Đó là kết quả **hợp lệ của transport không đáng tin**, không phải lỗi trong định nghĩa DTLS.

Chương trình dùng **localhost/loopback** nên không cần server ngoài hoặc certificate fixture. Cũng vì vậy nó **không kiểm tra bảo mật của TLS/DTLS**, chỉ kiểm tra những khác biệt vận chuyển và khả năng tạo engine. Không mang mẫu raw UDP ấy vào ứng dụng cần encryption mà không triển khai `SSLEngine` đầy đủ.

## 9. Cách xử lý DTLS `SSLEngine` đúng chuẩn

DTLS engine tách phần **mật mã/handshake** khỏi phần **UDP I/O**. Một ứng dụng DTLS thực cần:

1. `SSLContext.getInstance("DTLS")`, `init` với credentials/trust đáng tin và entropy.
2. `createSSLEngine(peerHost, peerPort)`, `setUseClientMode(true/false)`.
3. Cấu hình protocols, cipher suites, packet size, peer identity và client auth policy nếu cần.
4. `beginHandshake()`.
5. Xử lý `SSLEngineResult.HandshakeStatus`: **`NEED_TASK`**, **`NEED_WRAP`**, **`NEED_UNWRAP`**, **`NEED_UNWRAP_AGAIN`**, `NOT_HANDSHAKING` và `FINISHED`.
6. Đưa `wrap` output vào **UDP datagram(s)**; đưa bytes của **datagram nhận thực tế** vào `unwrap`.
7. Khi handshake đang `NEED_UNWRAP` và mạng timeout, application timer có thể phải **gọi `wrap` để kích hoạt retransmission handshake flight**, theo hướng dẫn DTLS JSSE.
8. Xử lý `BUFFER_OVERFLOW`, `BUFFER_UNDERFLOW`, `CLOSED`, `SSLException`, delegated tasks, peer validation, close alerts và giới hạn tài nguyên.
9. Chỉ đưa app data vào kênh bảo mật khi handshake/policy thành công.

Bảng `HandshakeStatus`:

| Trạng thái | Hành động |
| --- | --- |
| `NEED_WRAP` | Gọi `wrap`, gửi network output đúng định dạng |
| `NEED_UNWRAP` | Chờ UDP data, có timeout; có thể trigger retransmit ở DTLS handshake |
| `NEED_UNWRAP_AGAIN` | Gọi `unwrap` lần nữa trên dữ liệu **đã được engine buffer**, không đợi datagram mới |
| `NEED_TASK` | Chạy các delegated tasks từ `getDelegatedTask` |
| `FINISHED` | Hoàn thành handshake được báo qua kết quả thao tác |
| `NOT_HANDSHAKING` | Engine hiện không trong handshake |

Bảng `SSLEngineResult.Status`:

| `Status` | Ý nghĩa |
| --- | --- |
| `OK` | Thao tác thành công theo trạng thái |
| `BUFFER_UNDERFLOW` | Input chưa đủ bytes xử lý |
| `BUFFER_OVERFLOW` | Buffer output không đủ chỗ |
| `CLOSED` | Engine/direction đóng |

Không viết vòng lặp gọi `unwrap()` liên tục khi chưa có input mới và không phải `NEED_UNWRAP_AGAIN`, tránh **busy spin**. Không nối tùy tiện datagrams khác nhau thành một TCP stream rồi đưa vào một lần unwrap. JSSE hướng dẫn DTLS `SSLEngine.wrap()` tạo **tối đa một DTLS record trong một lần gọi** để tiện chuyển thành UDP packet phù hợp.

Đây là sơ đồ **giả mã**, không phải chương trình JSSE chạy độc lập:

```text
context = DTLS context initialized with appropriate key/trust managers
engine  = context.createSSLEngine(peer identity)
engine.setUseClientMode(client or server)
configure maximum packet size, ciphers/protocols, authentication policy
engine.beginHandshake()

while handshake not complete:
  NEED_TASK          => run delegated tasks
  NEED_WRAP          => wrap(empty app buffer) => send ciphertext datagram
  NEED_UNWRAP        => UDP receive with timeout => unwrap(received datagram)
                        timeout => retransmit handshake flight via engine as documented
  NEED_UNWRAP_AGAIN  => unwrap engine-buffered data (no network receive)
  errors/buffer state=> react safely; close/fail handshake if invalid

after authenticated handshake:
  outgoing application data => wrap => send datagrams
  received UDP datagram     => unwrap => process authenticated app data
  application handles replay of commands, packet ordering/loss if required
```

**Tại sao chỉ là pseudocode?** Một server DTLS an toàn còn cần key/certificate/PSK provisioning, peer/source-address association, cookie/anti-amplification protection, bounds, retransmission policy, receive queues, duplicate/reordered packets, concurrency và graceful close. Không nên rút gọn chúng thành code 10 dòng rồi tuyên bố đó là DTLS server production.

## 10. Packet size, path MTU và tránh IP fragmentation

Với TCP, tầng transport quản lý segmentation. Với UDP, một datagram quá lớn có thể bị **IP fragmentation**, không qua được đường mạng hoặc mất toàn bộ datagram vì chỉ một fragment bị mất. DTLS encryption overhead cũng làm tăng kích thước so với plaintext.

Trong Java, dùng:

```java
// Mảnh code trong phương thức có engine DTLS đã tạo:
javax.net.ssl.SSLParameters p = engine.getSSLParameters();
p.setMaximumPacketSize(1200);
engine.setSSLParameters(p);
```

`setMaximumPacketSize` nêu **kích thước packet/record tối đa mong muốn**; trong triển khai thực phải tính **IP + UDP headers, tunnel/VPN overhead, DTLS record header/tag**, PMTU và version/cipher suite. Con số **1200** trong bài chỉ là cấu hình minh họa, không phải quy định mọi mạng hay bảo đảm không bị fragmentation. Theo JSSE guidance, đừng đặt quá thấp (chẳng hạn dưới 256 byte) vì handshake messages nhỏ có thể cũng cần fragment.

**Handshake fragmentation** do DTLS hỗ trợ khác với **application fragmentation/reassembly**: nếu gửi một message ứng dụng vài megabyte, bạn cần một protocol chuyên biệt hoặc reliable transport; không thể chỉ trông chờ DTLS tự chia/gộp và retransmit hết payload.

## 11. NAT, identity và DoS cần được nghĩ đến

- **NAT rebinding/source tuple:** UDP source IP:port có thể đổi, nhưng cryptographic association phải vẫn kiểm soát đúng. **Không** giả định mọi JSSE provider hỗ trợ DTLS Connection ID (CID) hay migration.
- **Spoofing/amplification:** UDP cho phép tấn công giả source; ứng dụng/server phải giới hạn handshake allocation, rate, bytes gửi trước khi xác minh peer và session state; cookie/return-routability không thay thế quản lý DoS.
- **Trust anchors/hostname/PSK identity:** DTLS có cùng nhu cầu xác thực peer giống TLS. Không dùng trust-all certificate manager hay bỏ qua endpoint identity.
- **Datagram replay:** DTLS record anti-replay không thay thế idempotency và anti-replay ở nghiệp vụ.
- **Record errors:** dữ liệu không qua AEAD/MAC/tag verification phải bị từ chối; không dùng plaintext tạm/partial từ input chưa xác thực.
- **Fallback:** nếu `SSLContext("DTLS")` hoặc cipher suite không được runtime hỗ trợ, phải cấu hình một provider/giao thức được phê duyệt thay vì âm thầm gửi UDP plaintext.
- **0-RTT/early data:** với giao thức/version có hỗ trợ, cần threat model replay riêng; không dùng cho thao tác nhạy cảm không idempotent nếu không có bảo vệ phù hợp.

## 12. Chọn TLS, DTLS hay QUIC trong tình huống thực tế

| Trường hợp | Thường chọn | Giải thích |
| --- | --- | --- |
| HTTPS/REST trên TCP | TLS/TCP | Hạ tầng chuẩn, byte stream, certificate/hostname trust |
| gRPC/HTTP/2 | TLS/TCP | HTTP/2 tự framing trên stream |
| Truyền file đúng/đủ và theo thứ tự | TLS/TCP hoặc reliable transport phù hợp | Tránh tự phát minh reliability trên UDP |
| Trò chơi truyền state vị trí liên tục | DTLS/UDP có thể thích hợp | Gói cũ có thể bỏ qua, application tự quản lý sequence |
| Telemetry UDP, báo cáo realtime | DTLS/UDP có thể thích hợp | Giữ datagrams, bảo vệ integrity/confidentiality |
| Real-time voice/video | SRTP và cơ chế thiết lập khóa phù hợp (ví dụ DTLS-SRTP) | **Không** đồng nghĩa mọi gói media đi qua DTLS |
| HTTP/3 | QUIC/UDP + TLS 1.3 integration | QUIC không phải DTLS, có transport/packet protection riêng |
| Mạng bị firewall chặn UDP | TLS/TCP hoặc fallback protocol đúng chuẩn | DTLS không tự vượt mọi chính sách mạng |
| Giao dịch quan trọng cần exactly-once | Kênh bảo mật + idempotency/replay state ở application | Không protocol TLS/DTLS nào thay kiểm soát nghiệp vụ |

DTLS có thể giảm head-of-line blocking ở lớp vận chuyển cho một số ứng dụng, nhưng **không phải bảo đảm latency thấp nhất**. Với packet loss cao, handshake retransmission, thiếu PMTU, NAT/firewall hoặc logic ứng dụng phức tạp, hệ thống có thể chậm hoặc kém ổn định hơn một TCP/TLS stack tốt.

## 13. Lỗi phổ biến và khắc phục

| Hiện tượng/nhận định | Lý do | Cách xử lý |
| --- | --- | --- |
| "`DatagramSocket` nghĩa là DTLS" | UDP socket thuần không mã hóa | Kết hợp `SSLEngine` và handshake |
| "`SSLContext("DTLS")` đủ tạo secure channel" | Context/engine mới tạo chưa có authenticated peer | Init managers, handshake, wrap/unwrap datagrams |
| "`SSLSocket` chạy trực tiếp trên UDP" | Socket API đó dùng stream | Dùng `SSLEngine` cho DTLS |
| "UDP luôn giữ thứ tự trên loopback nên production cũng vậy" | Local observation không phải network guarantee | Kiểm thử loss/reorder/duplicate |
| "DTLS retransmit application data" | Protocol chỉ có cơ chế retransmission handshake | App tự resend/ACK hoặc reliable transport |
| "DTLS replay window đủ chống giao dịch trùng" | Record hợp lệ mới vẫn có thể chứa cùng message | Application idempotency |
| `NoSuchAlgorithmException` với DTLS | Runtime/provider không triển khai DTLS | Chọn provider được phê duyệt, không fallback sang plaintext |
| DTLS packet lỗi kích thước | PMTU, buffer, IP fragmentation | Set `MaximumPacketSize`, chọn MTU budget và max input |
| `NEED_UNWRAP_AGAIN` bị kẹt | Đợi mạng dù bytes nằm trong engine | Gọi `unwrap` lại không cần UDP receive |
| Handshake treo ở `NEED_UNWRAP` | Mất handshake flight hoặc chưa có timer | Quản lý timeout/backoff, trigger retransmission theo JSSE |
| `BUFFER_UNDERFLOW`/`BUFFER_OVERFLOW` | Input/buffer sai kích thước hoặc trạng thái | Cấp đúng packet data, điều chỉnh buffers, kiểm tra `SSLEngineResult` |
| `SSLHandshakeException` | Cert/key/trust/version/suite/peer error | Chẩn đoán cấu hình, không tắt verification |
| TCP `read()` cho ít hơn message | Stream không có message boundary | Length prefix + `readFully` và giới hạn |
| HTTP/3 bị gọi là DTLS | QUIC dùng TLS 1.3 handshake nhưng không DTLS record | Chọn QUIC implementation |
| JDK có TLSv1.3 nên tưởng có DTLSv1.3 | Khác protocol/version support | Đọc provider supported/enabled/negotiated |

## 14. Checklist thiết kế

- [ ] Xác định nhu cầu **reliable in-order byte stream** hay **best-effort datagrams**.
- [ ] Phân biệt rõ TLS/DTLS với TCP/UDP và application message framing.
- [ ] TLS stream có length-prefix/delimiter/framing hợp lệ; không suy `write` = `read`.
- [ ] DTLS có packet boundary, MTU budget, giới hạn input/buffer, timeout policy.
- [ ] Chọn Java API đúng: `SSLSocket` cho TLS stream, `SSLEngine` + datagrams cho DTLS.
- [ ] Cấu hình `SSLContext`, trust/keys, endpoint identity, versions/ciphers đúng policy.
- [ ] DTLS engine loop xử lý `NEED_WRAP`, `NEED_UNWRAP`, `NEED_UNWRAP_AGAIN`, `NEED_TASK`, buffer statuses và close.
- [ ] Có **handshake retransmission** nhưng không kỳ vọng application retransmission tự động.
- [ ] Anti-replay record không thay idempotency/transaction dedupe.
- [ ] Có tests về mất gói, reorder, duplicate, malformed datagrams, MTU reduction, NAT rebinding.
- [ ] Có DoS limits: packet/session/rate/memory/time và chống spoofing/amplification.
- [ ] Không mặc định RFC DTLS 1.3 đồng nghĩa mọi Java provider đã có DTLS 1.3.
- [ ] Không dùng `DatagramSocket` plain demo để truyền thông tin nhạy cảm.

## 15. Bài tập và câu hỏi tự đánh giá

**1.** Tại sao hai lần `SocketOutputStream.write("A")`, `write("B")` không bắt buộc tạo hai lần `read()` ở peer?  
Vì TCP/TLS là byte stream không có message boundary ứng dụng; receiver đọc được chuỗi byte theo cách hệ thống phân chia.

**2.** DTLS có tự biến datagrams bị mất thành stream đầy đủ như TCP không?  
**Không.** DTLS handshake có retransmission; application data có thể bị mất mà không có retransmission tự động.

**3.** Nếu UDP datagram `B` đến trước `A`, điều gì giúp DTLS nhận ra record không theo thứ tự?  
Record sequence/epoch và trạng thái crypto; việc dữ liệu ứng dụng nên chờ hay bỏ là quyết định protocol/app.

**4.** `setMaximumPacketSize(1200)` có chắc 1200 byte ứng dụng sẽ không bị IP fragmentation không?  
Không. Còn overhead IP/UDP/DTLS/tag/tunnel và PMTU; đây là giới hạn network record được cấu hình.

**5.** Tại sao `SSLContext.getInstance("DTLS")` và `DatagramSocket` chưa đủ để bảo mật?  
Chưa có key/trust managers, peer validation, handshake, DTLS wrap/unwrap và I/O pipeline.

**6.** `NEED_UNWRAP_AGAIN` nghĩa là nên gọi `DatagramSocket.receive()` ngay không?  
Không. Nó báo engine còn buffered handshake data; cần gọi `unwrap` tiếp không đợi gói mới.

**7.** DTLS record anti-replay có chống lặp một giao dịch chuyển tiền có chữ ký hợp lệ không?  
Không. Một record mới có thể chứa lại cùng command; cần idempotency key hoặc application replay state.

**8.** HTTP/3 có dùng DTLS không?  
Không. HTTP/3 dùng QUIC, QUIC tích hợp TLS 1.3 handshake nhưng dùng packet protection/stream/reliability riêng.

**9.** Tại sao một DTLS server phải cân nhắc cookie và rate limit?  
UDP cho phép giả source, tấn công spoofing/amplification hoặc làm cạn trạng thái server.

**10.** Có nên chọn DTLS chỉ vì “nhanh hơn TLS” không?  
Không. Chọn dựa vào datagram semantics, loss tolerance, latency trade-offs, MTU, firewall/NAT, tính sẵn có của implementation và chi phí vận hành.

## 16. Tài liệu tham khảo chuẩn

- [Java SE 17 — JSSE Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-secure-socket-extension-jsse-reference-guide.html): phần `SSLEngine`, DTLS handshake, `NEED_UNWRAP_AGAIN`, retransmission, maximum packet size.
- [Java SE 17 — Standard Algorithm Names](https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html): tên `SSLContext`/protocol cho TLS và DTLS.
- [Java SE 17 — `SSLContext`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLContext.html): tạo TLS/DTLS context.
- [Java SE 17 — `SSLEngine`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLEngine.html): `wrap`, `unwrap`, handshake và đóng engine.
- [Java SE 17 — `SSLEngineResult`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLEngineResult.html): operation status và handshake status.
- [Java SE 17 — `SSLParameters`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLParameters.html): packet-size và endpoint-identification configuration.
- [Java SE 17 — `DatagramSocket`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/DatagramSocket.html): UDP I/O.
- [RFC 5246 — TLS 1.2](https://www.rfc-editor.org/rfc/rfc5246), [RFC 8446 — TLS 1.3](https://www.rfc-editor.org/rfc/rfc8446).
- [RFC 6347 — DTLS 1.2](https://www.rfc-editor.org/rfc/rfc6347): record/epoch/sequence, handshake retransmission và replay.
- [RFC 9147 — DTLS 1.3](https://www.rfc-editor.org/rfc/rfc9147): thiết kế datagram, loss/reorder, handshake/record layer, replay considerations.
- [RFC 9001 — QUIC uses TLS](https://www.rfc-editor.org/rfc/rfc9001): QUIC dùng TLS 1.3 handshake nhưng không dùng DTLS record.
- [OpenJDK JEP 219 — Datagram Transport Layer Security](https://openjdk.org/jeps/219): tích hợp DTLS trong JSSE.

**Tổng kết:** **TLS/TCP** bảo vệ **byte stream tin cậy, theo thứ tự**; **DTLS/UDP** bảo vệ **datagrams có thể mất hoặc đảo thứ tự**, bổ sung handshake retransmission và record sequence/epoch nhưng **không cam kết application data reliability, order hay exactly-once**. Trong Java, chọn `SSLSocket` hoặc `SSLEngine` theo mô hình I/O thực và không nhầm hỗ trợ provider với một kết nối bảo mật đã handshake.


</details>

- [Quay lại đầu trang](#back-to-top)
