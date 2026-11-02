<a id="back-to-top"></a>

# 13. Chứng thư X.509

## Menu
- [13.1 Chứng thư gắn khóa công khai với danh tính và vai trò của CA](#x509-certificate-001)
- [13.2 Phân biệt root, intermediate, leaf và chứng thư tự ký](#x509-certificate-002)
- [13.3 Đọc chứng thư bằng CertificateFactory từ DER hoặc PEM](#x509-certificate-003)
- [13.4 Subject, issuer, serial number, thời hạn và X500Principal](#x509-certificate-004)
- [13.5 Subject Alternative Name và danh tính DNS hoặc địa chỉ IP](#x509-certificate-005)
- [13.6 KeyUsage, ExtendedKeyUsage, BasicConstraints và extension critical](#x509-certificate-006)
- [13.7 Phân biệt checkValidity, verify chữ ký chứng thư và kiểm tra độ tin cậy](#x509-certificate-007)
- [13.8 CSR PKCS#10, phản hồi CA và chuỗi chứng thư trong quy trình keytool](#x509-certificate-008)
- [13.9 Lập bộ CA thử nghiệm và giữ ranh giới giữa chứng thư thử nghiệm với môi trường thật](#x509-certificate-009)

## <a id="x509-certificate-001">13.1 Chứng thư gắn khóa công khai với danh tính và vai trò của CA</a>

<details>
<summary>Xem chi tiết</summary>

# 13.1 Chứng thư gắn khóa công khai với danh tính và vai trò của CA

## Mục tiêu học tập và vị trí của bài

Sau bài này, bạn có thể giải thích **vì sao một public key cần được gắn với một danh tính thông qua chứng thư X.509**, **CA thực sự chứng nhận điều gì** và **vì sao chữ ký của CA mới chỉ là một bước trong quyết định tin cậy**. Bạn cũng có thể đọc cấu trúc cốt lõi của một certificate bằng Java 17, phân biệt private key, public key, chứng thư, CSR và trust anchor mà không nhầm chứng thư với một giấy phép truy cập.

Bài 13.1 là **nền tảng khái niệm** cho chương 13. Những kỹ thuật chi tiết được học ở các bài riêng: phân tầng root/intermediate/leaf (13.2), DER/PEM và `CertificateFactory` (13.3), các trường subject/issuer/serial/validity (13.4), SAN DNS/IP (13.5), KU/EKU/BC (13.6), `checkValidity`/`verify`/PKIX (13.7), quy trình keytool CSR/reply (13.8) và cách ly CA thử nghiệm (13.9). Những điều kiện được nhắc vắn tắt ở đây đủ để hiểu bức tranh lớn nhưng không thay thế quy trình chuyên sâu trong từng bài.

## 1. Bài toán: khóa công khai không tự khai báo nguồn gốc đáng tin

Alice muốn kết nối an toàn tới dịch vụ `api.example.test`. Cô nhận được một public key dùng trong giao thức mật mã. Đối thủ Mallory cũng có thể tạo một cặp khóa và tự xưng mình là `api.example.test`. **Chỉ nhận byte public key không đủ để phân biệt hai trường hợp.**

```text
Alice cần nói chuyện với api.example.test
                 |
                 +--> Public key K1 do chủ dịch vụ kiểm soát?
                 |
                 +--> Public key K2 do kẻ giả mạo đưa tới?
                 |
                 v
Vấn đề còn thiếu: bằng chứng đáng tin về
mối liên hệ giữa danh tính được mong đợi và public key.
```

**X.509 certificate** giải quyết một phần trọng yếu: một issuer ký lên **một gói dữ liệu mô tả subject, public key và các điều kiện sử dụng**. Người xác minh có thể kiểm rằng thông tin này thực sự được ký bởi khóa issuer tương ứng và không bị sửa đổi kể từ khi ký. Nhưng ai được phép cấp chứng thư cho tên đó và issuer có được tin không vẫn là câu hỏi về **chính sách PKI**.

Một public key tự sinh có thể dùng để verify chữ ký toán học, nhưng điều đó không chứng minh chủ khóa là ai. Một certificate tự ký cũng có thể có chữ ký hợp lệ về mặt mật mã mà chưa có nguồn tin cậy để xác nhận danh tính được khai báo.

## 2. Cấu trúc lời xác nhận: CA ký `TBSCertificate`, không chỉ “ký public key”

Cấu trúc X.509 v3 ở mức học thuật:

```text
Certificate
  |
  +-- tbsCertificate  (to-be-signed certificate)
  |     +-- version, serialNumber
  |     +-- issuer, validity
  |     +-- subject
  |     +-- subjectPublicKeyInfo
  |     |     +-- algorithm + PUBLIC KEY
  |     +-- extensions (SAN, KU, EKU, BC, ...)
  |
  +-- signatureAlgorithm
  +-- signatureValue   <-- ký lên DER của tbsCertificate
```

Những trường cùng nằm trong phần được ký:

| Thành phần | Vai trò với liên kết khóa–danh tính | Điều không thể tự suy ra |
| --- | --- | --- |
| `subjectPublicKeyInfo` | Chứa public key của **subject** và thuật toán liên quan | Chủ sở hữu khóa là ai nếu không có quá trình xác minh |
| `subject` | Distinguished Name của chủ thể; có thể rỗng theo profile | Subject CN là hostname TLS đã kiểm chứng |
| `subjectAltName` (SAN) | Mang định danh theo loại như DNS/IP/URI, tùy profile | Giá trị SAN tự chứng minh CA đáng tin |
| `issuer` | Nêu danh tính bên cấp chứng thư | Trùng DN với CA đã tin là đủ xác minh chữ ký |
| `serialNumber` | Định danh chứng thư trong phạm vi **issuer** | Serial duy nhất trên mọi CA |
| `notBefore`/`notAfter` | Khoảng thời gian có hiệu lực được CA tuyên bố | Chứng thư còn hạn chắc chắn chưa bị thu hồi |
| `BasicConstraints`, `KeyUsage`, `ExtendedKeyUsage` | Giới hạn vai trò CA và mục đích sử dụng khóa | Chứng thư có thể dùng cho mọi purpose hoặc cấp quyền nghiệp vụ |
| `signatureValue` | Chữ ký của issuer trên dữ liệu được bảo vệ | Issuer nhất thiết nằm trong truststore của người nhận |

Nói “CA ký public key” chỉ là một cách nói tắt. Chính xác hơn, **CA ký phần `TBSCertificate`**, trong đó public key được gắn với subject, issuer, serial, thời hạn và các extensions. Việc đổi một trường được ký làm phép kiểm chữ ký thất bại nếu không có chữ ký mới từ private key của issuer.

**Chứng thư không mã hóa dữ liệu**: thông thường certificate và public key được phân phối công khai. Private key tương ứng với subject phải được bảo vệ trong keystore, HSM hoặc hệ thống quản lý khóa thích hợp; không nằm trong certificate PEM/DER thông thường. Private key của **CA** là một khóa khác: nó ký certificate, không phải khóa mà mọi server đều được sao chép.

## 3. CA làm gì, và quyền lực của CA đến từ đâu?

**Certification Authority (CA)** là chủ thể/đơn vị vận hành quy trình phát hành chứng thư theo chính sách xác minh danh tính và bảo vệ signing key. CA có thể kiểm soát quyền đối với DNS name, tổ chức, người dùng hoặc thiết bị tùy loại chứng thư, rồi cấp X.509 với nội dung đã duyệt.

Các vai trò tách biệt:

| Vai trò | Trách nhiệm |
| --- | --- |
| **Subject / applicant** | Sinh và bảo vệ private key; đưa public key và thông tin đề nghị cấp đến CA qua CSR hoặc quy trình khác |
| **Registration Authority (RA)**, khi có | Hỗ trợ thẩm định người xin cấp/quyền sử dụng định danh và quy trình enrollment |
| **CA / issuer** | Xác minh và phê duyệt theo chính sách; đặt serial, hiệu lực, extensions; ký `TBSCertificate` bằng private key CA |
| **Relying party / verifier** | Quyết định trust anchors nào được chấp nhận; xác minh path, điều kiện sử dụng và danh tính đích cần kết nối |
| **Chủ ứng dụng** | Kiểm quyền nghiệp vụ sau khi danh tính đã được xác thực qua giao thức tương ứng |

**CA không cấp quyền đăng nhập vào mọi hệ thống** chỉ bằng chữ ký chứng thư. Ví dụ doanh nghiệp cấp client certificate cho nhân viên có thể chứng minh một danh tính mTLS; quyền đọc từng đơn hàng vẫn do IAM/ACL của ứng dụng quyết định.

Về mặt PKI, **root CA** thường là trust anchor được cấu hình tin cậy trước, **intermediate CA** nhận quyền phát hành từ CA cấp trên với ràng buộc cụ thể, còn **leaf/end-entity** được cấp cho website/người dùng/ứng dụng. Một self-signed root chỉ có giá trị trusted khi verifier **chủ động đặt nó vào tập trust anchors** theo kênh và chính sách đáng tin. Đừng suy từ `Subject=Issuer` hay `CA=true` rằng một CA mặc nhiên được tin. Vai trò, pathLen, CA constraints và sự khác biệt self-issued/self-signed được phân tích sâu ở bài 13.2.

### 3.1. Chữ ký của issuer chứng minh được điều gì?

Ví dụ issuer tên `Example Issuing CA` ký leaf với SAN `DNS:api.example.test`.

- Nếu byte `TBSCertificate` bị đổi sau khi ký, bước `verify` bằng public key của issuer phù hợp sẽ phát hiện sai khác.
- Nếu một public key **khác** được dùng để kiểm chữ ký, phép kiểm thường thất bại.
- Nếu issuer key đúng nhưng issuer **không nằm trên đường tin cậy** mà client chấp thuận, chứng thư **chưa được tin** cho kết nối.
- Nếu CA đã cấp nhầm SAN, chữ ký vẫn có thể hợp lệ về mặt mật mã, nhưng chứng thư có thể tạo rủi ro giả mạo. Bảo mật vì thế phụ thuộc cả quy trình **thẩm định và cấp** của CA.

Điều này giải thích vì sao CA cần quản trị identity validation, kiểm soát quyền truy cập signing key, audit, certificate profile, revocation/rotation và xử lý chứng thư cấp nhầm. Khả năng ký không có nghĩa quyền phát hành tùy ý.

## 4. Luồng từ khóa tới chứng thư, sau đó tới quyết định chấp nhận

```text
[1] Subject sinh cặp khóa
    private key được giữ bí mật; public key được chia sẻ
               |
               v
[2] Subject gửi CSR (PKCS#10) hoặc yêu cầu cấp tương ứng
    proof of possession + subject/public key + requested extensions
               |
               v
[3] CA/RA kiểm tra quyền đối với identity theo policy
    xác định SAN, KU, EKU, BC, validity và serial được cấp
               |
               v
[4] CA ký tbsCertificate -> phát hành X.509 leaf + issuer chain
               |
               v
[5] Peer nhận certificate và xác minh:
    issuer signatures + path tới trust anchor đáng tin
    + thời hạn, constraints, purpose, revocation policy
               |
               v
[6] Ứng dụng kiểm expected identity và giao thức chứng minh
    peer thực sự giữ private key (ví dụ TLS handshake)
               |
               v
[7] Ứng dụng kiểm quyền business riêng trước khi cho thao tác
```

Đây là **bản đồ khái niệm**, không phải chỉ dẫn rằng phải tự code từng bước kiểm TLS theo thứ tự. Trong ứng dụng Java HTTPS, JSSE/trust manager và endpoint identification phối hợp nhiều bước, áp quy tắc theo provider và chính sách. Bài 13.7 đi vào `checkValidity`, `verify`, `CertPathValidator`; bài 13.8 đi sâu từng lệnh `keytool` phát hành và nhập phản hồi CSR.

### 4.1. CSR là lời đề nghị, không phải chứng thư

**Certification Signing Request (PKCS#10)** chứa public key, subject/attributes và chữ ký do applicant tạo bằng private key tương ứng. Chữ ký của CSR cho thấy người yêu cầu điều khiển khóa trong ngữ cảnh chữ ký, **không** chứng minh họ sở hữu domain. CSR có thể yêu cầu SAN nhưng CA phải **thẩm định độc lập** trước khi cấp; nó có thể từ chối, sửa hoặc không cấp extensions đó.

**Certificate reply** đã được CA ký là đối tượng khác. Khi nhận reply cho một CSR, phải khớp public key của chứng thư với private key đã tạo CSR. Server muốn dùng chứng thư trong TLS cần lưu **leaf certificate chain cùng private key**. Thao tác keytool nhập reply vào đúng alias được học đầy đủ ở 13.8.

### 4.2. Trust anchor là quyết định của người xác minh, không phải “một root bất kỳ”

Một root CA công khai thường được hệ điều hành/JDK hoặc chính sách ứng dụng cung cấp trong truststore. Một private CA có thể do tổ chức chủ động thêm vào **truststore riêng theo mục đích**. Chứng thư tự ký có thể được chủ động pin/trust theo thiết kế hẹp sau khi kiểm nguồn gốc; tự ký **không tự cấp trust**.

Không import root do chính server chưa được xác thực gửi tới, chỉ vì `PKIX path building failed`. Nếu làm vậy, attacker có thể tạo cả root lẫn leaf tự ký hợp lệ cho tên tùy ý. Dùng trust anchors đã kiểm chứng và luôn giữ kiểm tên endpoint.

## 5. Một ví dụ dễ hình dung: chứng thư đúng nhưng danh tính chưa chắc đúng

Giả sử Alice muốn liên lạc với `payments.example.test`:

| Trường hợp | Chữ ký leaf | Trust/identity theo client | Kết luận |
| --- | --- | --- | --- |
| Leaf do CA được client tin cấp cho `payments.example.test`, SAN DNS khớp, các điều kiện khác đạt | Đúng | Path và tên đích phù hợp | Có thể chấp nhận trong TLS theo policy |
| Leaf do CA được tin cấp cho `files.example.test`, client gọi `payments.example.test` | Đúng | Path có thể đúng, **SAN sai đích** | Từ chối kết nối do sai endpoint |
| Leaf tự ký với `CN=payments.example.test` do Mallory tạo | Tự ký đúng bằng khóa Mallory | Không có trust anchor phù hợp | Từ chối; tên tự khai không tạo tin cậy |
| Leaf do CA được tin cấp nhưng đã hết hạn hoặc bị thu hồi | Chữ ký có thể đúng | Vi phạm validity/status policy | Từ chối theo chính sách |
| Client mTLS có certificate được CA nội bộ tin cấp nhưng gọi API vượt role | Chữ ký/path có thể đúng | Không đạt authorization của tài nguyên | Không cấp quyền thao tác |

Ba nhầm lẫn cần loại bỏ ngay:

1. **“Tôi đã parse được chứng thư.”** Parse chỉ giúp đọc cấu trúc; `CertificateFactory` không tự tin CA.
2. **“Tôi đã verify được chữ ký.”** `X509Certificate.verify(publicKey)` chỉ kiểm chữ ký chứng thư bằng key được cung cấp; không chọn trust anchor, không kiểm hostname, không kiểm revocation toàn diện.
3. **“Tôi đã tìm ra root.”** Nếu root lấy từ chính input không đáng tin, việc tự đặt nó làm `TrustAnchor` phá vỡ mô hình PKI.

Trong HTTPS, client thường phải đối chiếu DNS/IP **theo SAN đúng loại** của hostname/IP mà client định truy cập; Subject CN không thay thế bước nhận diện tên hiện đại. Bài 13.5 dành riêng cho DNS/IP/wildcard/IDNA và cơ chế endpoint identification.

## 6. Thực hành nền tảng Java 17+: quan sát CA certificate, không tự tạo trust

Đây là thí nghiệm nhỏ nhằm **nhìn tận mắt binding và signer**. Mã tạo một CA demo self-signed, xuất **chỉ public certificate** ra PEM và dùng Java đọc các trường. Nó **không** phát hành leaf, không tự import root vào truststore, không mở TLS. Một lab root self-signed thành công về mật mã vẫn **không có quyền trusted mặc định**.

Trong một thư mục mới chỉ dùng cho bài học, chạy `keytool` của JDK 17+. Các lệnh hỏi password tương tác; không đưa password thật lên command line:

```bash
keytool -genkeypair -alias lab-ca -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -dname "CN=Learning CA,O=Training,C=VN" -validity 30 -ext BC:critical=ca:true,pathlen:0 -ext KU:critical=keyCertSign,cRLSign -keystore ca-demo.p12 -storetype PKCS12
keytool -exportcert -alias lab-ca -rfc -file ca-demo.pem -keystore ca-demo.p12 -storetype PKCS12
```

- `ca-demo.p12` chứa **private signing key của CA**, phải giữ bí mật và chỉ dùng trong lab.
- `ca-demo.pem` chỉ chứa certificate/public key và có thể đọc được; **việc có file không đồng nghĩa client đã tin CA**.
- `BasicConstraints CA=true` và `KeyUsage keyCertSign` diễn đạt vai trò được cấp trong chứng thư. Việc sử dụng làm root trusted là quyết định riêng của verifier.
- `-validity 30` tạo một khoảng hiệu lực từ lúc lập lab; sau 30 ngày cần tạo fixture mới để test điều kiện còn hạn.

Tạo file **`CaBindingInspect.java`**:

```java
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public final class CaBindingInspect {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: java CaBindingInspect <certificate.pem>");
        }

        X509Certificate cert;
        try (InputStream in = Files.newInputStream(Path.of(args[0]))) {
            cert = (X509Certificate) CertificateFactory
                    .getInstance("X.509").generateCertificate(in);
        }

        System.out.println("X.509 parsed: " + "X.509".equals(cert.getType()));
        System.out.println("Self-issued DN: " +
                cert.getSubjectX500Principal()
                    .equals(cert.getIssuerX500Principal()));
        System.out.println("Certificate is CA: " +
                (cert.getBasicConstraints() >= 0));

        cert.checkValidity();
        System.out.println("Within current validity interval: true");
        cert.verify(cert.getPublicKey());
        System.out.println("Self-signature verifies: true");

        // Không được coi các dòng trên là phép kiểm tra trust/hostname.
        System.out.println("Issuer trusted by this program: not evaluated");
        System.out.println("Endpoint hostname verified: not evaluated");
    }
}
```

Biên dịch và chạy sau khi tạo `ca-demo.pem` (lệnh Java ở dưới là chương trình độc lập):

```bash
javac --release 17 CaBindingInspect.java
java CaBindingInspect ca-demo.pem
```

Với **fixture vừa tạo** trên JDK có `keytool` và các provider cần thiết, kết quả kỳ vọng:

```text
X.509 parsed: true
Self-issued DN: true
Certificate is CA: true
Within current validity interval: true
Self-signature verifies: true
Issuer trusted by this program: not evaluated
Endpoint hostname verified: not evaluated
```

Điều gì đã được chứng minh?

- `CertificateFactory("X.509")` đọc cấu trúc chứng thư, **không** xác định CA có được ứng dụng tin cậy.
- `Subject=Issuer` phản ánh **self-issued**; `verify(cert.getPublicKey())` xác nhận riêng chữ ký tự ký của fixture. Đây là hai thuộc tính khác nhau và sẽ được phân tích sâu hơn ở 13.2.
- `checkValidity()` chỉ cho biết ngày hiện tại nằm trong validity interval, **không** kiểm tra danh tính hoặc trạng thái thu hồi.
- Chương trình chủ ý in **not evaluated** ở hai mục trust và hostname vì nó chưa dùng trust anchors/PKIX hay kết nối HTTPS.
- Root PEM public có thể phân phối theo kênh phù hợp, nhưng không tự đặt nó thành trusted root của hệ thống hoặc browser. Từ thí nghiệm này, người học cần phân biệt **một signature đúng** với **một trust decision hợp lệ**.

**Không nhập CA demo vào truststore production/cacerts, không dùng private key lab ngoài phòng thực hành.** Bài 13.9 trình bày rõ cách cách ly một CA lab, quản lý password, truststore và các bài kiểm thử âm.

## 7. Chẩn đoán tư duy từ việc nhận một chứng thư

Khi được giao một file chứng thư mới, hãy trả lời theo thứ tự:

1. **File thật sự là gì?** Là public X.509 certificate, CSR, public key thô hay keystore có private key? Không căn cứ vào đuôi file. Bài 13.3 hướng dẫn cách đọc DER/PEM.
2. **Ai là subject, issuer, danh tính được cấp?** Đọc các trường đã được ký; issuer name không bảo đảm chữ ký hoặc trust. Bài 13.4 giải thích DN, serial và validity.
3. **CA đã tuyên bố phạm vi sử dụng nào?** SAN, KeyUsage, ExtendedKeyUsage, BasicConstraints, các critical extensions có phù hợp? Bài 13.5–13.6 kiểm từng nhóm.
4. **Ai được verifier chủ động tin?** Root/intermediate nào tạo đường PKIX hợp lệ đến trust anchor và chứng thư có bị thu hồi theo policy? Bài 13.2 và 13.7 đi sâu.
5. **Đây có phải đúng dịch vụ/role cần xác thực?** Kiểm expected identity, TLS proof-of-possession và authorization theo protocol; không lấy identity kỳ vọng từ chính chứng thư chưa xác minh.
6. **Có phải chứng thư cấp từ quy trình đúng?** CSR, CA reply, private-key alias, chain, thời hạn, review/audit và môi trường lab/prod. Bài 13.8–13.9 làm rõ.

Đây là một **bảng câu hỏi đánh giá**, không phải code thay thế bộ xác minh TLS/PKIX. Khi lập trình kết nối HTTPS thật, ưu tiên JSSE/`HttpClient`/`HttpsURLConnection` được cấu hình an toàn; không tạo `X509TrustManager` chấp nhận mọi certificate hoặc `HostnameVerifier` luôn trả true.

## 8. Tình huống cần giải thích, thay vì chỉ học thuộc định nghĩa

**Tình huống A.** CA cấp certificate với `SAN=DNS:api.example.test` cho public key của service. Kẻ tấn công đổi SAN thành `admin.example.test` trong DER đã cấp mà không biết private key issuer. Điều gì phải xảy ra?

> Phần SAN thuộc `TBSCertificate` đã được ký, nên chữ ký không còn khớp. Một verifier đúng sẽ từ chối chứng thư đã bị chỉnh sửa.

**Tình huống B.** Mallory tạo root CA tự ký hợp lệ và đặt `CN=Example Trusted CA`. Alice có nên tin chứng thư server do root đó ký?

> Không. Tên và chữ ký tự ký là thông tin do Mallory điều khiển. Alice chỉ chấp nhận root nếu nó được xác lập làm trust anchor qua kênh và chính sách độc lập đáng tin.

**Tình huống C.** Một certificate do CA đáng tin cấp cho `files.example.test`, còn client cần gọi `payments.example.test`. Có nên kết nối?

> Không nếu SAN không bao phủ đúng đích. Trust chain có thể hợp lệ nhưng endpoint identity sai.

**Tình huống D.** Một client certificate mTLS được CA tin cấp và path hợp lệ. Nó có mặc nhiên được sửa toàn bộ hóa đơn không?

> Không. Xác thực danh tính không tự cấp quyền nghiệp vụ; service phải kiểm principal/tenant/role theo từng thao tác.

**Tình huống E.** Một CSR xin được cấp `CA:true` và `DNS:service.prod.example`. CA có nên sao chép nguyên yêu cầu vì CSR signature hợp lệ?

> Không. CSR signature chỉ xác thực quyền điều khiển private key của requester; CA phải thẩm định yêu cầu, danh tính và giới hạn certificate profile trước khi ký.

## 9. Những nhầm lẫn cốt lõi cần tránh

| Nhận định sai | Vì sao sai | Cách diễn đạt chính xác |
| --- | --- | --- |
| “CA chỉ ký public key.” | Chữ ký bao phủ `TBSCertificate` chứa nhiều trường | CA ký cấu trúc dữ liệu certificate được cấp, gồm public key, identity và constraints |
| “Certificate chứa private key.” | `subjectPublicKeyInfo` là public key | Private key được giữ tách biệt trong keystore/HSM |
| “Certificate parse được nên an toàn.” | Parser chỉ đọc định dạng X.509 | Cần kiểm trust/path/purpose/identity theo tình huống |
| “Issuer DN giống CA đáng tin nên chữ ký đúng.” | Bất kỳ ai có thể đặt DN giống | Phải kiểm signature bằng CA public key phù hợp và path policy |
| “Self-signed root mặc nhiên là trust anchor.” | Self-signature chỉ là sự kiện mật mã | Trust anchor do relying party thiết lập từ nguồn đáng tin |
| “Có CA signature thì đúng hostname.” | CA có thể cấp chứng thư cho tên khác | Kiểm expected DNS/IP qua SAN và verifier chuẩn |
| “Certificate của client được CA cấp nên có quyền admin.” | CA binding liên quan identity, không phải ACL | Sau xác thực phải kiểm authorization ở ứng dụng |
| “Certificate tự nó mã hóa HTTP.” | X.509 chỉ mang public key và các thuộc tính | TLS thiết lập kênh bảo mật theo giao thức, có xác thực endpoint |

## 10. Tổng kết và ranh giới với các bài tiếp theo

Chứng thư X.509 là **một lời xác nhận có chữ ký của issuer rằng public key gắn với subject/định danh đã được cấp trong một phạm vi sử dụng nhất định**. CA phải thực hiện issuance policy; verifier phải lựa chọn trust anchor, kiểm path, mục đích và đúng endpoint; ứng dụng vẫn phải kiểm quyền sau xác thực.

Bài 13.1 giúp người học đặt đúng câu hỏi về nguồn gốc public key và vai trò CA. Các bài kế tiếp cung cấp kiểm chứng chuyên biệt: **13.2** vai trò root/intermediate/leaf, **13.3–13.6** đọc và hiểu trường X.509, **13.7** phân tách validity/signature/trust, **13.8** quy trình CSR/chain trong keytool, **13.9** mô hình CA test cô lập.

**Tài liệu chuẩn:**

- [RFC 5280 — Internet X.509 PKI Certificate and CRL Profile](https://www.rfc-editor.org/rfc/rfc5280) — cấu trúc `TBSCertificate`, issuer/subject, extensions, path validation.
- [Java SE 17 — X509Certificate](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/X509Certificate.html) — API xem các trường, chữ ký và hiệu lực.
- [Java SE 17 — CertificateFactory](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertificateFactory.html) — đọc DER/PEM thành chứng thư.
- [Java SE 17 — keytool](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html) — tạo, xuất và xem chứng thư.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="x509-certificate-002">13.2 Phân biệt root, intermediate, leaf và chứng thư tự ký</a>

<details>
<summary>Xem chi tiết</summary>

# 13.2 Phân biệt root, intermediate, leaf và chứng thư tự ký

## Mục tiêu

Bài học thuộc **13. Chứng thư X.509** trong Java Advance – security-cryptography. Bạn sẽ:

1. Phân biệt đúng **root CA certificate**, **intermediate CA certificate**, **leaf/end-entity certificate** và **self-signed certificate** theo vai trò của chúng trong một chuỗi tin cậy.
2. Hiểu sự khác nhau giữa **“chứng thư có chữ ký hợp lệ”**, **“chuỗi được xác minh tới một trust anchor”** và **“chứng thư thuộc đúng máy chủ/người dùng/dịch vụ mà ứng dụng mong đợi”**.
3. Đọc các trường X.509 quan trọng: `Subject`, `Issuer`, `BasicConstraints`, `KeyUsage`, `ExtendedKeyUsage` (EKU), `SubjectAlternativeName` (SAN), `NotBefore/NotAfter`, `SubjectKeyIdentifier` (SKI) và `AuthorityKeyIdentifier` (AKI).
4. Dùng **`keytool` và Java 17+** dựng một chuỗi giả lập root → intermediate → leaf, kiểm chứng chữ ký và chạy `CertPathValidator("PKIX")` với một trust anchor đúng/sai.
5. Nhận diện các lỗi phổ biến trong Java TLS, mTLS, PKIX và việc tin chứng thư tự ký mà chưa kiểm tra nguồn gốc.

> **Ý chính:** `self-signed` mô tả **cách chứng thư được ký**; `root`, `intermediate`, `leaf` mô tả **vai trò trong đường dẫn chứng thực**; `trusted` mô tả **chính sách tin cậy của ứng dụng**. Đó là các khái niệm liên quan, nhưng không đồng nghĩa.

## 1. Một chứng thư X.509 chứa thông tin gì?

**Chứng thư số X.509 (certificate)** là cấu trúc dữ liệu đã được ký, thường chứa:

- **Subject:** định danh chủ thể mà chứng thư mô tả; trong các use case TLS hiện đại, danh tính DNS/IP được kiểm tra chủ yếu theo **SAN**, không chỉ nhìn vào `CN`.
- **Subject public key information (SPKI):** public key và thuật toán liên quan của chủ thể; **không chứa private key**.
- **Issuer:** tên chủ thể được nêu là bên phát hành (ký) chứng thư.
- **Serial number:** số sê-ri do issuer gán trong phạm vi issuer.
- **Validity:** `NotBefore` và `NotAfter`, khoảng thời gian chứng thư dự kiến có hiệu lực.
- **Signature algorithm và signature value:** thuật toán/chữ ký của issuer lên phần **tbsCertificate** (*to-be-signed certificate*).
- **Extensions:** ví dụ `BasicConstraints`, `KeyUsage`, `ExtendedKeyUsage`, `SubjectAlternativeName`, `NameConstraints`, SKI/AKI và Certificate Policies.

Mỗi chứng thư có **public key của subject**, nhưng chữ ký chứng thư được xác minh bằng **public key của issuer**, ngoại trừ trường hợp tự ký thì hai khóa đó có thể trùng.

Khi một trình duyệt thấy chứng thư với subject là `service.example.test`, nó không chỉ kiểm tra “có chữ ký” mà cần xây dựng đường dẫn tin cậy và kiểm tra **đúng hostname**, thời hạn, mục đích sử dụng, policy, thuật toán và trạng thái thu hồi theo chính sách.

Chứng thư không phải khóa bí mật. Bạn có thể gửi chứng thư cho bên khác; **private key tương ứng phải được giữ kín** và không nằm trong PEM certificate. Đừng nhầm file `.crt/.cer/.pem` có certificate với keystore chứa private key hoặc PKCS#8 private-key file.

## 2. Sơ đồ chuỗi root → intermediate → leaf

Một cấu hình PKI phổ biến:

```text
                     TRUST STORE CỦA ỨNG DỤNG
                     +-----------------------+
                     | Root CA / Trust Anchor|
                     | ĐÃ được tin cậy trước |
                     +-----------+-----------+
                                 |
           Root private key ký certificate của Intermediate
                                 |
                                 v
                     +-----------------------+
                     | Intermediate CA       |
                     | BasicConstraints CA=1 |
                     | được Root xác nhận    |
                     +-----------+-----------+
                                 |
             Intermediate private key ký certificate của Leaf
                                 |
                                 v
                     +-----------------------+
                     | Leaf / End Entity     |
                     | service.example.test  |
                     | CA=0, SAN và EKU      |
                     +-----------------------+
                                 |
                      Leaf private key dùng
                     cho TLS hoặc chữ ký app
```

Trong mạng TLS thông thường, máy chủ gửi **leaf và các intermediate cần thiết**; root tin cậy thường đã có sẵn trong trust store phía client và **không cần được máy chủ gửi**. Chuỗi mà server gửi chưa chắc chính là đường dẫn cuối cùng được validator chọn (ví dụ khi có cross-sign); trình kiểm tra có thể phải xây hoặc chọn một path phù hợp với tập trust anchors tại client.

### Vai trò khác nhau

| Loại | Nhiệm vụ trong chuỗi | Thường ký cho ai? | Extension thường cần | Có cần là self-signed? |
| --- | --- | --- | --- | --- |
| **Root CA** | Điểm bắt đầu tin cậy (*trust anchor*) của một hệ thống PKI | Intermediate hoặc các CA khác | `BasicConstraints: CA=true`, `KeyUsage: keyCertSign` | **Không bắt buộc về mặt khái niệm trust anchor**; các root CA certificate thường tự ký |
| **Intermediate CA** | Cấp/chứng thực certificate ở tầng thấp hơn; phân quyền từ root | Intermediate khác hoặc leaf | `CA=true`, `keyCertSign`, `pathLenConstraint` nếu có | **Thường không**; nó được issuer phía trên ký |
| **Leaf / End-Entity** | Gắn public key với máy chủ, người dùng, dịch vụ hoặc ứng dụng cuối | Không đóng vai trò CA trong path đó | `CA=false`, SAN, EKU, KeyUsage phù hợp mục đích | **Thường không**; tuy nhiên có thể tự ký trong một số môi trường |
| **Self-signed certificate** | Mô tả **đặc tính chữ ký**, không phải một tầng cụ thể | Subject ký certificate của chính mình | Có thể `CA=true` hoặc `CA=false` tùy mục đích | **Theo định nghĩa là tự ký** |

Một **root CA** là vai trò theo *trust anchor được chọn*, không phải “mọi certificate có `Subject=Issuer`”. Một certificate tự ký có `CA=true` vẫn **không trở thành trust anchor chỉ vì nó tự ký**. Ngược lại, một tổ chức có thể chủ động tin một certificate/khóa không tự ký làm trust anchor nội bộ hoặc tin một intermediate làm anchor theo chính sách; `TrustAnchor` trong Java hỗ trợ cả certificate hoặc CA name/public key.

### CA khác với end entity ở đâu?

Certificate có `BasicConstraints` **CA=true** có thể đóng vai trò CA nếu các điều kiện path/KeyUsage/policy phù hợp. Một **leaf** thông thường có `BasicConstraints CA=false` hoặc không có quyền CA. Với Java `X509Certificate.getBasicConstraints()`:

- Trả **`-1`** nếu certificate không phải CA.
- Trả số **`>=0`** nếu certificate là CA; số này phản ánh mức `pathLenConstraint` được khai báo hoặc một giá trị đại diện khi không giới hạn.
- `pathLenConstraint=0` với intermediate nghĩa là không cho phép **CA trung gian không tự phát hành thêm** nằm dưới intermediate đó trong đường dẫn, nhưng vẫn có thể cấp certificate cho **leaf**.
- Trong path validation, ý nghĩa path length cần tuân theo RFC 5280, gồm quy tắc về các CA self-issued. Không tự kiểm tra path length bằng cách đếm tất cả certificate trong một mảng.

Một CA certificate còn thường có bit **`KeyUsage.keyCertSign`**, và validator phải áp dụng các constraint phù hợp. **`CA=true` đơn độc không có nghĩa ứng dụng phải tin hoặc certificate đó chắc chắn được phép cấp mọi loại certificate.**

## 3. Self-issued và self-signed không hoàn toàn giống nhau

Đây là sự phân biệt dễ bị bỏ qua:

- **Self-issued:** `Subject` và `Issuer` có cùng Distinguished Name (DN). Đó là một so sánh **tên**, không phải bằng chứng chữ ký.
- **Self-signed:** chữ ký trên certificate được tạo bằng private key tương ứng với **public key chứa trong chính certificate**, và chữ ký có thể được xác minh bằng public key đó. Thông thường subject và issuer cũng trùng tên.

Một certificate **self-issued nhưng không self-signed** có thể xuất hiện trong quá trình chuyển khóa (*CA key rollover*): tên CA không đổi nhưng certificate được ký bởi một khóa khác của cùng CA. Vì vậy không được coi `subject.equals(issuer)` là phép kiểm tra self-signed đầy đủ.

**Self-signed không đồng nghĩa self-trusted.** Ví dụ:

```text
Root-A tự ký hợp lệ. Client đã cài Root-A làm trust anchor.
=> Một path kết thúc ở Root-A có THỂ được tin nếu còn các policy khác.

Root-B tự ký hợp lệ. Client chưa hề phê duyệt/cài Root-B.
=> Root-B KHÔNG được tin chỉ vì chữ ký tự ký đúng.
```

Đối thủ có thể sinh vô số cặp khóa và certificate tự ký có chữ ký hợp lệ. Không có bài toán mật mã nào ngăn họ làm việc đó. Chính **phân phối trust anchor từ nguồn đã được kiểm soát** mới quyết định root nào đáng tin với ứng dụng.

### Vì sao root CA thường self-signed?

Đường dẫn certificate phải có điểm dừng. Root certificate tự ký tạo một định dạng đóng gói tiện lợi cho **public key và metadata của điểm tin cậy**; tuy nhiên **niềm tin không đến từ chữ ký của root lên chính nó**. Nó đến từ việc ứng dụng/hệ điều hành/tổ chức **đã chọn khóa/certificate đó làm trust anchor** thông qua cơ chế tin cậy bên ngoài.

Trong nhiều PKI, private key root được giữ offline hoặc trong HSM, intermediate thực hiện việc ký certificate thường ngày. Nếu intermediate bị lộ, nhà quản trị có thể thu hồi/thay intermediate mà không bắt buộc thay toàn bộ root; nhưng đó vẫn là sự cố nghiêm trọng ảnh hưởng các leaf đã cấp và yêu cầu quy trình revocation/rotation.

## 4. Signature link khác với một path tin cậy

Xem ba chứng thư:

```text
Root:         Subject = CN=Training Root CA
              Issuer  = CN=Training Root CA
Intermediate: Subject = CN=Training Intermediate CA
              Issuer  = CN=Training Root CA
Leaf:         Subject = CN=service.example.test
              Issuer  = CN=Training Intermediate CA
```

Các đường liên kết mật mã có thể kiểm tra:

- `intermediate.verify(root.getPublicKey())`: chữ ký của intermediate phù hợp public key root.
- `leaf.verify(intermediate.getPublicKey())`: chữ ký leaf phù hợp public key intermediate.
- `root.verify(root.getPublicKey())`: chữ ký root tự xác minh được nếu root là self-signed.

**Nhưng chỉ ba lời gọi `verify` trên chưa thiết lập được trust**:

- Bạn có thể đã lấy `root.pem` từ chính attacker. Cả ba chữ ký có thể đúng, nhưng root chưa được **tin cậy**.
- Có thể có intermediate `CA=false` hoặc `keyCertSign` không được cho phép. Từng chữ ký vẫn có thể khớp nhưng **đường dẫn không đạt constraint**.
- Certificate có thể hết hạn, chưa có hiệu lực, bị thu hồi hoặc dùng thuật toán không được policy cho phép.
- Leaf có thể hợp lệ cho hostname `service-b.example.test`, nhưng client cần kết nối `service-a.example.test`.
- Root được tin trong một **purpose/namespace** không đồng nghĩa có quyền cấp certificate cho mọi mục đích hoặc tenant.

**Xác minh một certificate signature** là thao tác cục bộ. **PKIX path validation** xét một đường dẫn certificate trong ngữ cảnh trust anchors và các constraints. **Xác minh danh tính dịch vụ/người dùng** là ràng buộc tiếp theo của ứng dụng/TLS.

## 5. Các trường X.509 nên đọc để xác định vai trò

| Field / extension | Vai trò | Điều không được suy diễn |
| --- | --- | --- |
| `Subject` | Tên chủ thể | Tên có thể được tự khai, không tự chứng minh danh tính |
| `Issuer` | Tên bên phát hành được khai báo | Trùng tên root không chứng minh certificate được root ký |
| `AuthorityKeyIdentifier` (AKI) | Hỗ trợ tìm public key/certificate của issuer thích hợp | AKI không tự là trust anchor hoặc bằng chứng path hợp lệ |
| `SubjectKeyIdentifier` (SKI) | Định danh khóa chủ thể, hỗ trợ dựng path | Cùng một SKI không chứng minh đã được tin cậy |
| `BasicConstraints` | Phân biệt CA, ràng buộc số CA ở dưới | `CA=true` không tự cấp niềm tin |
| `KeyUsage` | Các mục đích khóa như `digitalSignature`, `keyCertSign`, `keyEncipherment` | Một certificate chỉ có public key RSA không mặc nhiên cho phép mọi usage |
| `ExtendedKeyUsage` (EKU) | Mục đích như `serverAuth`, `clientAuth`, `codeSigning` | `serverAuth` không tự cấp quyền ký bản cập nhật ứng dụng |
| `SubjectAlternativeName` (SAN) | Danh tính DNS/IP/email/URI theo profile | Chỉ nhìn CN hoặc một SAN bất kỳ không đủ để khớp hostname |
| `NotBefore` / `NotAfter` | Khoảng thời gian hiệu lực | Trong hạn không đồng nghĩa chưa bị thu hồi |
| `CRLDistributionPoints` / `AuthorityInfoAccess` | Gợi ý điểm lấy CRL/OCSP/certificate issuer | Có URL trong extension không đảm bảo client đã thực hiện revocation checks |
| `CertificatePolicies` / `NameConstraints` | Ràng buộc policy, namespace | Không thể bỏ qua critical extension không hiểu mà vẫn coi path hợp lệ |

Một chain phù hợp còn phải có **chữ ký đúng giữa từng tầng**, **issuer/subject linkage**, hỗ trợ thuật toán phù hợp, các critical extensions được xử lý đúng, BasicConstraints/KeyUsage/policy và **trust anchor đã chọn**.

### Root / intermediate không chỉ dựa vào tên

Thấy subject chứa chữ `Root` không có nghĩa certificate đó là root. Thấy `Issuer=Subject` không chắc nó self-signed. Thấy một certificate `CA=true` không chắc nó nằm ở tầng intermediate; **cùng certificate CA có thể là intermediate trong một path, nhưng được tổ chức khác chủ động dùng làm trust anchor trong một path khác**. Vai trò phụ thuộc **đường dẫn/chính sách**, không phải nhãn trong DN.

### Leaf không nhất thiết là chứng thư server TLS

Leaf có thể là certificate cho **client mTLS**, ký code, ký tài liệu, xác thực người dùng hoặc thiết bị. EKU/KeyUsage và quy tắc định danh cần phù hợp đúng use case. Ví dụ `serverAuth` thuộc OID **`1.3.6.1.5.5.7.3.1`**, còn `clientAuth` là **`1.3.6.1.5.5.7.3.2`**. Một leaf có thể có hoặc không có một EKU cụ thể theo profile; không tự coi “leaf” là “web server”.

## 6. PKIX path validation trong Java

Java cung cấp API chuẩn để parse certificate và xác thực đường dẫn:

- `CertificateFactory.getInstance("X.509")`: parse PEM/DER certificate, hoặc tạo `CertPath` từ danh sách certificate.
- `X509Certificate`: đọc `getSubjectX500Principal()`, `getIssuerX500Principal()`, `getBasicConstraints()`, `getKeyUsage()`, `getExtendedKeyUsage()`, `getSubjectAlternativeNames()`, `getNotBefore()/getNotAfter()`.
- `X509Certificate.verify(issuerPublicKey)`: kiểm tra chữ ký **của certificate** bằng public key issuer; **không** kiểm tra path, trust, hostname hay revocation đầy đủ.
- `CertPathValidator.getInstance("PKIX")`: validate một **đường dẫn certificate đã có** với `PKIXParameters`.
- `CertPathBuilder.getInstance("PKIX")`: thử **xây một path** từ certificate/CertStore và trust anchor theo cấu hình; không đồng nghĩa “luôn tự lấy được intermediate trên Internet”.
- `TrustAnchor`: certificate/CA key mà ứng dụng **đã quyết định tin**, không phải certificate chỉ vì nó self-signed.
- `KeyStore` và `TrustManagerFactory`: lưu/provision trust anchors và tích hợp JSSE/TLS; không nhầm **truststore** với **keystore private key**.

**Thứ tự `CertPath` cho PKIX thông thường:** leaf trước, các intermediate tiếp theo, và **trust anchor thường không nằm trong danh sách certificate của path** vì đã được cung cấp riêng trong `PKIXParameters`. Một số hệ thống server gửi root certificate thừa; việc root có trong handshake không có nghĩa client coi nó đáng tin.

### Ba kiểm tra độc lập

```text
X509Certificate.verify(issuerKey)
         |
         v
[Certificate signature hợp lệ?]
         |
         v
PKIX path validation với trust anchors của client
         |
         v
[Root tin cậy? CA constraints? time/policy? revocation policy?]
         |
         v
Endpoint / identity verification
         |
         v
[DNS SAN/IP/issuer identity/role đúng expected target?]
         |
         v
Authorization / application policy
         |
         v
[Được phép truy cập resource/hành động này?]
```

Nếu thiếu bước **endpoint identity**, một certificate hợp lệ trong chuỗi CA có thể bị chấp nhận cho **sai máy chủ**. Nếu thiếu bước **trust anchor**, kẻ tấn công có thể tự cấp chain cho chính mình. Nếu thiếu bước **authorization**, chứng thư hợp lệ vẫn có thể thực hiện hành động vượt quyền.

## 7. Thực hành Java 17: dựng root → intermediate → leaf và một root không được tin

Bài thực hành dùng **`keytool` đi kèm JDK** và **Java 17**. Bạn tạo bốn cặp khóa **chỉ dành cho thử nghiệm**:

1. `root`: CA tự ký với `BasicConstraints CA=true` và `pathLen=1`.
2. `intermediate`: lúc sinh keypair có certificate tạm tự ký; sau đó tạo CSR, **root ký certificate mới** và import certificate reply vào cùng alias. Kết quả **không còn self-signed**.
3. `leaf`: tương tự, certificate cuối do **intermediate ký**, có `SAN=dns:service.example.test` và `EKU=serverAuth`. Leaf **không phải CA**.
4. `rogue`: CA tự ký độc lập có chữ ký hoàn toàn đúng, nhưng **không phải trust anchor được chọn** khi xác minh chain root → intermediate → leaf.

**Không dùng keystore thử nghiệm, mật khẩu `changeit`, root giả lập hoặc private key của lab trong production.** Chỉ làm trong một thư mục dùng riêng, có thể xóa sau khi kiểm thử.

### 7.1 Tạo certificate bằng PowerShell trên Windows

Mở PowerShell trong terminal có `keytool.exe` từ JDK và chạy **toàn bộ đoạn** dưới đây. Script tạo thư mục riêng trong `%TEMP%`, keystore PKCS#12 `chain.p12` và bốn file PEM. Nó không truy cập internet. Nếu chạy lại, keystore của **lab** trong thư mục tạm được tạo lại; tránh đặt tài liệu cá nhân vào cùng thư mục.

```powershell
$ErrorActionPreference = "Continue"
$qaPath = Join-Path $env:TEMP 'x509-roles-training'
New-Item -ItemType Directory -Force -Path $qaPath | Out-Null
Set-Location $qaPath
$p = "changeit"

function Invoke-Keytool([string[]]$params) {
    $result = & keytool.exe @params 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw ("keytool failed: " + ($params -join " ")
               + " :: " + ($result -join " "))
    }
}

if (Test-Path -LiteralPath 'chain.p12') {
    Remove-Item -LiteralPath 'chain.p12' -Force
}

Invoke-Keytool @("-genkeypair","-alias","root","-keyalg","RSA",
    "-keysize","2048","-validity","3650",
    "-dname","CN=Training Root CA,O=Training",
    "-ext","BC=ca:true,pathlen:1",
    "-ext","KU=keyCertSign,cRLSign",
    "-storetype","PKCS12","-keystore","chain.p12",
    "-storepass",$p,"-keypass",$p,"-noprompt")

Invoke-Keytool @("-genkeypair","-alias","intermediate","-keyalg","RSA",
    "-keysize","2048","-validity","3650",
    "-dname","CN=Training Intermediate CA,O=Training",
    "-ext","BC=ca:true,pathlen:0",
    "-ext","KU=keyCertSign,cRLSign",
    "-storetype","PKCS12","-keystore","chain.p12",
    "-storepass",$p,"-keypass",$p,"-noprompt")

Invoke-Keytool @("-certreq","-alias","intermediate",
    "-file","intermediate.csr","-keystore","chain.p12","-storepass",$p)

Invoke-Keytool @("-gencert","-alias","root",
    "-infile","intermediate.csr","-outfile","intermediate-signed.pem",
    "-rfc","-validity","600",
    "-ext","BC=ca:true,pathlen:0",
    "-ext","KU=keyCertSign,cRLSign",
    "-keystore","chain.p12","-storepass",$p)

Invoke-Keytool @("-importcert","-alias","intermediate",
    "-file","intermediate-signed.pem",
    "-keystore","chain.p12","-storepass",$p,"-noprompt")

Invoke-Keytool @("-genkeypair","-alias","leaf","-keyalg","RSA",
    "-keysize","2048","-validity","3650",
    "-dname","CN=service.example.test,O=Training",
    "-ext","SAN=dns:service.example.test",
    "-ext","EKU=serverAuth",
    "-ext","KU=digitalSignature,keyEncipherment",
    "-storetype","PKCS12","-keystore","chain.p12",
    "-storepass",$p,"-keypass",$p,"-noprompt")

Invoke-Keytool @("-certreq","-alias","leaf","-file","leaf.csr",
    "-keystore","chain.p12","-storepass",$p)

Invoke-Keytool @("-gencert","-alias","intermediate",
    "-infile","leaf.csr","-outfile","leaf-signed.pem",
    "-rfc","-validity","90",
    "-ext","SAN=dns:service.example.test",
    "-ext","EKU=serverAuth",
    "-ext","KU=digitalSignature,keyEncipherment",
    "-keystore","chain.p12","-storepass",$p)

Invoke-Keytool @("-importcert","-alias","leaf",
    "-file","leaf-signed.pem",
    "-keystore","chain.p12","-storepass",$p,"-noprompt")

Invoke-Keytool @("-genkeypair","-alias","rogue","-keyalg","RSA",
    "-keysize","2048","-validity","3650",
    "-dname","CN=Untrusted Root CA,O=Training",
    "-ext","BC=ca:true,pathlen:1",
    "-ext","KU=keyCertSign,cRLSign",
    "-storetype","PKCS12","-keystore","chain.p12",
    "-storepass",$p,"-keypass",$p,"-noprompt")

foreach ($alias in @("root","intermediate","leaf","rogue")) {
    Invoke-Keytool @("-exportcert","-alias",$alias,"-rfc",
        "-file",("$alias.pem"),"-keystore","chain.p12","-storepass",$p)
}
Get-ChildItem -Name '*.pem'
```

Kết quả cần có các file:

```text
intermediate-signed.pem
leaf-signed.pem
root.pem
intermediate.pem
leaf.pem
rogue.pem
```

Tên file được liệt kê theo cách sắp xếp của hệ thống; **không** cần tin vào thứ tự hiển thị. `intermediate-signed.pem` và `leaf-signed.pem` là certificate replies trung gian; bốn file `root.pem`, `intermediate.pem`, `leaf.pem` và `rogue.pem` được lớp Java bên dưới đọc. Phần output “Generating...”, “Certificate reply was installed...” của `keytool` phụ thuộc JDK/OS và không phải stdout cố định của bài.

**Lưu ý:** Trong bài thực hành, root/intermediate/leaf ban đầu được tạo với keystore chứa **private key** để có thể ký CSR. Sau khi thay certificate reply, private key của từng alias vẫn do keystore lưu. **Certificate PEM xuất bằng `-exportcert` chỉ chứa public certificate**, không chứa private key. Một CA thực phải có kiểm soát nghiêm ngặt riêng cho khóa root/intermediate (HSM, role, key ceremony, phát hành và revoke).

Trên Linux/macOS, ý tưởng tương tự với `keytool` và câu lệnh shell tương đương; khối bên trên là script **PowerShell dành cho Windows** nên không thể dán nguyên vào Bash.

### 7.2 Java 17: đọc certificate, kiểm tra CA và validate PKIX

Tại chính thư mục chứa bốn file PEM, tạo file **`CertificateRolesDemo.java`** có nội dung:

```java
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;

public final class CertificateRolesDemo {
    public static void main(String[] args) throws Exception {
        X509Certificate root = load("root.pem");
        X509Certificate intermediate = load("intermediate.pem");
        X509Certificate leaf = load("leaf.pem");
        X509Certificate untrustedRoot = load("rogue.pem");

        root.checkValidity();
        intermediate.checkValidity();
        leaf.checkValidity();

        System.out.println("Root self-signed: " + selfSigned(root));
        System.out.println("Intermediate self-signed: " + selfSigned(intermediate));
        System.out.println("Leaf self-signed: " + selfSigned(leaf));
        System.out.println("Other root self-signed: " + selfSigned(untrustedRoot));
        System.out.println("Root is CA: " + (root.getBasicConstraints() >= 0));
        System.out.println("Intermediate is CA: "
                + (intermediate.getBasicConstraints() >= 0));
        System.out.println("Leaf is CA: " + (leaf.getBasicConstraints() >= 0));

        intermediate.verify(root.getPublicKey());
        leaf.verify(intermediate.getPublicKey());
        System.out.println("Intermediate signature by root: true");
        System.out.println("Leaf signature by intermediate: true");

        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        CertPath path = factory.generateCertPath(List.of(leaf, intermediate));
        System.out.println("PKIX with trusted root: " + validates(path, root));
        System.out.println("PKIX with unrelated root: "
                + validates(path, untrustedRoot));

        boolean dnsMatch = leaf.getSubjectAlternativeNames().stream()
                .anyMatch(entry -> Integer.valueOf(2).equals(entry.get(0))
                        && "service.example.test".equals(entry.get(1)));
        boolean serverAuth = leaf.getExtendedKeyUsage() != null
                && leaf.getExtendedKeyUsage()
                        .contains("1.3.6.1.5.5.7.3.1");
        System.out.println("Leaf SAN contains expected DNS: " + dnsMatch);
        System.out.println("Leaf EKU allows serverAuth: " + serverAuth);
    }

    private static X509Certificate load(String name) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        try (InputStream in = Files.newInputStream(Path.of(name))) {
            return (X509Certificate) factory.generateCertificate(in);
        }
    }

    private static boolean selfSigned(X509Certificate certificate) {
        if (!certificate.getSubjectX500Principal()
                .equals(certificate.getIssuerX500Principal())) {
            return false;
        }
        try {
            certificate.verify(certificate.getPublicKey());
            return true;
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private static boolean validates(CertPath path, X509Certificate anchor)
            throws GeneralSecurityException {
        PKIXParameters params = new PKIXParameters(
                Set.of(new TrustAnchor(anchor, null)));
        // Only for an offline teaching example without CRL/OCSP.
        params.setRevocationEnabled(false);
        try {
            CertPathValidator.getInstance("PKIX").validate(path, params);
            return true;
        } catch (CertPathValidatorException e) {
            return false;
        }
    }
}
```

Biên dịch/chạy ở PowerShell (vẫn ở thư mục `x509-roles-training`):

```powershell
javac --release 17 CertificateRolesDemo.java
java -cp . CertificateRolesDemo
```

**Stdout Java đã được kiểm thử trên fixture `keytool` tạo như trên:**

```text
Root self-signed: true
Intermediate self-signed: false
Leaf self-signed: false
Other root self-signed: true
Root is CA: true
Intermediate is CA: true
Leaf is CA: false
Intermediate signature by root: true
Leaf signature by intermediate: true
PKIX with trusted root: true
PKIX with unrelated root: false
Leaf SAN contains expected DNS: true
Leaf EKU allows serverAuth: true
```

Lưu ý kết quả phụ thuộc JDK/provider hỗ trợ RSA, X.509, PKIX, keytool options và độ hiệu lực của certificates trong ví dụ; các certificate được tạo mới với thời hạn 90/600/3650 ngày nên bình thường sẽ còn hiệu lực khi chạy ngay. Bài thực hành **không** kết nối tới domain `service.example.test` và **không** đăng ký root giả lập này với hệ điều hành.

### 7.3 Vì sao mỗi dòng quan trọng?

1. **`Root self-signed: true`**: root có subject/issuer cùng tên **và** chữ ký xác minh được bằng public key của chính root. Đây là sự kiện mật mã, **không phải nguyên nhân root đáng tin**.
2. **`Intermediate self-signed: false` và `Leaf self-signed: false`**: sau khi import certificate replies, chúng được cấp bởi CA phía trên, dù lúc tạo keypair trước đó `keytool` đã tạo các certificate placeholder tự ký.
3. **`Other root self-signed: true`**: root “rogue” tự ký có chữ ký đúng; bản thân chữ ký tự ký không phân biệt được một CA được ứng dụng phê duyệt với một CA do người khác tự sinh.
4. **`Root is CA: true`; `Intermediate is CA: true`; `Leaf is CA: false`**: `getBasicConstraints()` phản ánh quyền CA trong certificate, không căn cứ vào chữ “root/intermediate/leaf” trong subject.
5. **`Intermediate signature by root: true`; `Leaf signature by intermediate: true`**: hai lời gọi `verify` đơn chứng minh *certificate signature links*, **không đủ để thay cho PKIX**.
6. **`PKIX with trusted root: true`**: path `leaf → intermediate` đạt kiểm tra PKIX khi `root` được **ứng dụng chủ động chọn** làm `TrustAnchor`.
7. **`PKIX with unrelated root: false`**: vẫn chính path đó nhưng trust anchor được thay bằng một CA tự ký khác; path không kết thúc ở root được tin, nên bị từ chối. Đây là bằng chứng bài học quan trọng nhất.
8. **`Leaf SAN contains expected DNS: true` và `Leaf EKU allows serverAuth: true`**: đọc riêng hai extension liên quan use case TLS. **Chúng không phải một bộ kiểm tra hostname/endpoint hoàn chỉnh**; code chỉ kiểm tra một DNS literal trong fixture.

**Quan trọng với `selfSigned`:** hàm ví dụ yêu cầu `Subject DN=Issuer DN` rồi gọi `certificate.verify(certificate.getPublicKey())`. Việc kiểm tra đúng sự tự ký về mặt mật mã **không** tạo trust. Với các certificate/provider có cách biểu diễn DN/khóa đặc biệt hoặc signature algorithm không được hỗ trợ, có thể cần xử lý thêm lỗi; đây là phương pháp minh họa chuẩn cho fixture đã tạo.

### 7.4 Vì sao bài thực hành tắt revocation?

Lệnh `params.setRevocationEnabled(false)` chỉ dùng để demo **offline** vì các certificate tự tạo ở đây không có hệ thống CRL/OCSP thật. Nó **không phải khuyến nghị production**. Trong môi trường thực, định nghĩa chính sách kiểm tra thu hồi phù hợp, các nguồn CRL/OCSP, timeout, cache, chế độ fail-open/fail-closed theo threat model, cũng như xử lý sự cố CA/key bị thu hồi. Mọi bước path/identity/role/freshness đều cần quyết định rõ ràng; một mã demo có `PKIX validates==true` **không tự đạt đủ tất cả yêu cầu TLS**.

**Trust anchor validity:** root certificate trong code được gọi `root.checkValidity()` tường minh. Trust anchor là đầu vào được tin cậy từ trước của `CertPathValidator`; không mặc định nó được kiểm tra tất cả các trường/điều kiện theo cùng quy tắc như certificate nằm trong path. Vòng đời root/trust store là policy riêng của ứng dụng.

## 8. Java API nào trả lời câu hỏi nào?

| API | Nó kiểm tra/làm gì? | Nó **không** làm gì? |
| --- | --- | --- |
| `CertificateFactory.generateCertificate` | Parse PEM/DER X.509 thành object | Không xác thực issuer hoặc trust |
| `X509Certificate.getSubjectX500Principal` | Trả subject DN | Không chứng minh subject là danh tính đúng |
| `X509Certificate.getIssuerX500Principal` | Trả issuer DN | Không chứng minh issuer thực sự ký |
| `X509Certificate.getBasicConstraints` | Trả CA/path length constraint, hoặc `-1` | Không quyết định CA có đáng tin |
| `X509Certificate.getKeyUsage/getExtendedKeyUsage` | Trả mục đích được extension khai báo | Không thay thế validation toàn bộ chain/policy |
| `X509Certificate.getSubjectAlternativeNames` | Đọc SAN entries theo kiểu | Không tự so hostname theo quy tắc TLS |
| `X509Certificate.checkValidity` | Kiểm tra NotBefore/NotAfter ở thời điểm xét | Không kiểm tra chữ ký, revocation, trust hoặc identity |
| `X509Certificate.verify(issuerKey)` | Kiểm tra chữ ký certificate bằng khóa issuer | Không tự dựng/validate chuỗi hoặc chọn trust anchor |
| `CertPathValidator.validate(path, params)` | Áp dụng thuật toán PKIX lên path với trust anchors/params | Không tự là bộ kiểm tra hostname, app authorization hoặc tự tạo trust |
| `CertPathBuilder.build(params)` | Tìm một path phù hợp theo chứng thư/cert stores/constraints được cấu hình | Không cam kết lấy được mọi issuer qua mạng |
| `TrustManagerFactory` / JSSE | Liên kết chính sách tin certificate chain vào TLS | Vẫn cần cấu hình endpoint identification và policy service đúng use case |

Đoạn dưới đây **chỉ minh họa khởi tạo PKIX validator**, không độc lập và chưa thực hiện đường dẫn:

```java
CertPathValidator validator = CertPathValidator.getInstance("PKIX");
// Cần CertPath, PKIXParameters và trust anchors được chuẩn bị đáng tin.
// Cũng cần identity/hostname, purpose, revocation policy theo use case.
```

Không tạo `new TrustAnchor(certFromNetwork, null)` rồi dùng nó để “xác minh” chính certificate do network gửi: như vậy đưa **dữ liệu chưa tin cậy** thành điểm tin cậy và vô hiệu hóa mục đích của PKI.

## 9. TLS, mTLS và danh tính certificate

### 9.1 HTTPS server: chain hợp lệ chưa đủ

Client HTTPS có thể gặp hai certificate đều được root đáng tin ký:

```text
CN=service-a.example.test, SAN=DNS:service-a.example.test
CN=service-b.example.test, SAN=DNS:service-b.example.test
```

Cả hai có thể có path PKIX hợp lệ. Nhưng khi client kết nối tới **`service-a.example.test`**, certificate của **service-b** không được chấp nhận chỉ vì cùng CA. **Endpoint identity verification** phải khớp DNS/IP từ URL/kết nối tới SAN với quy tắc theo chuẩn áp dụng; không chỉ so raw `CN`.

Trong JSSE, cách thực hiện endpoint identification phụ thuộc API/socket/engine và cấu hình client. Ví dụ snippet dưới đây dành cho **client `SSLSocket` hoặc `SSLEngine` đã có**, không phải chương trình độc lập:

```java
SSLParameters parameters = sslSocket.getSSLParameters();
parameters.setEndpointIdentificationAlgorithm("HTTPS");
sslSocket.setSSLParameters(parameters);
// Sau đó thực hiện TLS handshake với trust manager đúng policy.
```

Cần import `javax.net.ssl.SSLParameters`, có `sslSocket` hợp lệ và truststore an toàn. Các HTTP client tiêu chuẩn thường đã có hành vi xác thực hostname riêng; **không tắt hostname verification/trust manager để sửa lỗi certificate**. Nếu dùng hostname verifier tùy chỉnh, nó phải tuân thủ giao thức thay vì `return true`.

### 9.2 mTLS client: cert chain hợp lệ không tự cấp quyền

mTLS xác thực chứng thư ở **cả hai phía** khi cấu hình yêu cầu client certificate. Dù client cert có path hợp lệ, server vẫn phải ánh xạ danh tính được phép từ SAN/subject/PKI policy sang **tài khoản, service, role, tenant và quyền tài nguyên**. Một client có cert hợp lệ từ cùng CA không mặc nhiên được đọc dữ liệu của tenant khác.

Tương tự, `ExtendedKeyUsage=serverAuth` dùng cho TLS server khác `clientAuth` dành cho client. Một certificate chỉ phù hợp với một mục đích không tự được chấp nhận trong mục đích khác. Cần dùng trust manager/certificate policy đúng theo role, không chỉ “bất kỳ cert nào chain tới CA chung”.

### 9.3 Self-signed cert trong môi trường dev

Certificate tự ký cho localhost/dev có thể chấp nhận trong **một trust model hẹp** nếu fingerprint/public key đã được phân phối đáng tin và ứng dụng cố ý tin nó. Nhưng:

- Không dùng `X509TrustManager` luôn chấp nhận mọi chain.
- Không tắt hostname check toàn cục.
- Không import bừa certificate từ email/URL do người gửi chưa được xác thực.
- Phải giới hạn trust theo môi trường, hostname/purpose; tránh đưa CA test vào trust store production.

## 10. Chẩn đoán lỗi certificate và chain

| Triệu chứng | Nguồn gốc cần xem | Hành động phù hợp |
| --- | --- | --- |
| `PKIX path building failed` hoặc `unable to find valid certification path` | Client không tìm được path về trust anchor đã cài; intermediate thiếu/issuer sai hoặc policy | Kiểm tra truststore/trust anchors, intermediate được gửi, path constraints và log chẩn đoán; **không** tự tin root do server gửi |
| `CertPathValidatorException` với path cụ thể | Signature, validity, constraints, policies, revocation hoặc trust mismatch | Đọc lý do/`index` để debug nội bộ; xác minh chuỗi và chính sách |
| `CertificateExpiredException` / `CertificateNotYetValidException` | Thời gian `NotAfter/NotBefore` không hợp lệ với thời điểm xét | Kiểm tra hệ thống đồng hồ, renew/replace cert đúng quy trình |
| Certificate là self-signed nhưng `verify(selfPublicKey)` pass | Certificate tự ký có chữ ký đúng | **Chưa đủ để tin**; phải có pin/trust anchor được provision hợp lệ |
| `CA=false` ở intermediate | Extension BasicConstraints không cho phép cấp chứng thư tầng dưới | Phát hành lại intermediate hợp lệ; không bỏ qua constraints |
| CA có `KeyUsage` nhưng thiếu `keyCertSign` khi bị yêu cầu | Key không được cấp đúng vai trò CA | Sửa profile CA, không chỉ thêm ca vào truststore |
| Root đáng tin nhưng hostname mismatch | Certificate có SAN khác hostname đích | Sửa cấp phát SAN hoặc hostname truy cập, không tắt endpoint identification |
| Chain OK nhưng leaf không có `serverAuth` khi policy yêu cầu | EKU/mục đích sử dụng không phù hợp | Cấp leaf theo profile đúng, không coi chain là đủ |
| Chain đúng nhưng thiếu CRL/OCSP trong môi trường yêu cầu | Revocation data không khả dụng hoặc cấu hình không nhất quán | Thiết kế policy, mạng/cache/timeouts, không tự tắt toàn bộ xác minh |
| Máy khác validate, máy này không | Truststore khác, clock skew, provider, thuật toán, intermediates hoặc policy khác | So sánh JDK, trust anchors, endpoint, version/chain; không log private keys |
| Self-issued có `Subject=Issuer` nhưng `verify(selfKey)` lỗi | Không phải self-signed hoặc dữ liệu/chữ ký khác | Xét rollover/cross-sign, không kết luận chỉ từ chuỗi DN |

**Đừng trả thông tin bí mật hoặc bật tính năng “trust all” trên môi trường thật.** Log nên chứa certificate fingerprint (không phải private key), issuer/subject ở mức phù hợp, key ID, expected hostname, failure category và correlation ID. Cẩn thận với log chứa danh tính hoặc metadata nhạy cảm; làm sạch và kiểm soát quyền xem log.

## 11. Cross-signing, root rollover và trust anchor mới

Một tổ chức có thể cấp certificate cho **cùng một intermediate public key** dưới nhiều issuer khác nhau (*cross-signing*) hoặc phát hành certificate self-issued để **chuyển đổi khóa CA**. Hệ quả:

- **Không phải lúc nào cũng có một đường path duy nhất** từ leaf tới root. Các client có trust stores khác nhau có thể chọn các path hợp lệ khác nhau.
- Root có thể được thay thế qua cập nhật truststore của OS/JDK hoặc qua quá trình phân phối policy của tổ chức. Việc phân phối đó **phải đáng tin**, không dựa trên một root tự ký mới do peer bất kỳ gửi.
- Một CA **có thể đóng vai intermediate trong path A nhưng được coi là trust anchor trong path B** vì tổ chức B trực tiếp tin CA đó. Trust anchor là **đầu vào policy của validator**.
- SKI/AKI giúp chọn issuer nhưng không bảo đảm chữ ký/trust; subject/issuer DN có thể giống nhau trong key rollover. Phải xác minh đúng key và signature.
- Việc root/intermediate đổi certificate, key hay thuật toán có thể khiến thiết bị cũ chưa cập nhật truststore không xây được path, dù server vẫn có một chain hợp lệ cho client khác.
- **Chứng thư cross-signed không đồng nghĩa “hai CA được tin như nhau”**; chỉ những trust anchors thực sự được cấu hình ở client mới có quyền kết thúc path.

Khi triển khai thay CA hoặc intermediate, cần thử nhiều client/JDK/truststore, expiration và thuật toán yếu trong path, sắp xếp intermediate, backup và rollback; đừng thử nghiệm bằng cách vô hiệu hóa kiểm tra certificate rồi phát hành production.

## 12. Những lỗi nhận thức cần tránh

1. **“Subject=Issuer nên đây là root đáng tin.”** Sai: đó chỉ là dấu hiệu self-issued. Chữ ký tự ký phải kiểm tra riêng; trust anchor phải do ứng dụng chọn.
2. **“Certificate tự ký thì luôn nguy hiểm hoặc luôn không hợp lệ.”** Không chính xác: nó có thể được tin có chủ đích sau provisioning an toàn; nguy hiểm là *tự động tin một certificate mới nhận*.
3. **“Intermediate chứng nhận được leaf thì intermediate là root.”** Sai: intermediate có quyền do CA phía trên cấp, chịu path constraints; root là trust anchor theo policy.
4. **“Leaf không có quyền cấp cert thì không ký gì được.”** Sai: leaf có thể dùng private key **ký thông điệp/TLS handshake** theo KeyUsage/EKU, nhưng không được dùng như CA phát hành certificate nếu `CA=false`.
5. **“Nếu chữ ký leaf.verify(intermediateKey) đúng thì HTTPS an toàn.”** Sai: thiếu trust root, path constraints, hostname, EKU, validity, revocation và đúng chính sách ứng dụng.
6. **“Root nằm trong certificate chain server gửi nên nó là trusted root.”** Sai: trust không đến từ server. Root phải xuất phát từ truststore/policy đã được provision.
7. **“CertPathValidator kiểm tra hostname giúp tôi.”** Sai trong cách dùng API tổng quát: path validation không thay thế JSSE endpoint identification hoặc hostname verifier đúng.
8. **“`getBasicConstraints() >= 0` là đủ để CA cấp bất kỳ cert nào.”** Sai: còn KeyUsage, pathLen, name constraints, policies, signature, validity và trust.
9. **“Nếu thấy path validation fail, cứ nhập certificate lạ vào cacerts.”** Sai: việc thay đổi trust anchors là quyết định bảo mật cao, phải xác minh nguồn gốc và phạm vi tin cậy.
10. **“Thấy revocation gây lỗi thì tắt vĩnh viễn.”** Sai: đó là chính sách có rủi ro và phải được thiết kế/đánh giá, không phải cách sửa tự động.
11. **“Có cert PEM thì ứng dụng đã có private key để ký.”** Sai: public certificate không chứa private key; key material được lưu nơi khác.
12. **“X.509 certificate giống public key SPKI.”** Sai: certificate có thêm issuer, validity, signature/extensions; public key SPKI chỉ mang khóa và thông số thuật toán.
13. **“Wildcard SAN/DNS chỉ cần so chuỗi đơn giản.”** Sai: hostname verification dùng quy tắc cụ thể về DNS/IP, wildcard, encoding, IDNA; đừng tự thay thế bằng `String.contains(...)`.
14. **“Một certificate root tự ký có chữ ký hợp lệ chứng minh danh tính tổ chức.”** Sai: bất kỳ bên nào cũng có thể sinh certificate tự ký với `O`/`CN` tùy ý; nguồn trust anchor mới tạo niềm tin.

## 13. Checklist dùng trong ứng dụng Java

- [ ] Xác định **expected identity** (hostname, service name, client identity) từ ngữ cảnh đáng tin, không từ certificate/claim chưa được xác minh.
- [ ] Trust anchors đến từ truststore/policy được phân phối và quản lý đáng tin, không lấy trực tiếp từ chain peer.
- [ ] Leaf và các intermediate được parse đúng và xây path phù hợp; root/trust anchor không được tự gán theo subject string.
- [ ] Đánh giá chữ ký từng link, `BasicConstraints`, `KeyUsage`, `pathLenConstraint`, name/policy constraints và thuật toán.
- [ ] Kiểm tra `NotBefore/NotAfter` theo thời gian, kế hoạch renew và policy revocation.
- [ ] TLS server: xác thực SAN của **hostname thực sự kết nối**; không tắt JSSE endpoint identification.
- [ ] mTLS: phân biệt `serverAuth`/`clientAuth` và gắn identity đã xác thực vào tenant/role/authorization.
- [ ] Kiểm tra quy trình root/intermediate rotation, cross-sign, thay truststore giữa các client/JDK.
- [ ] Không tin certificate tự ký chỉ vì `cert.verify(cert.getPublicKey())` trả thành công.
- [ ] Không cho ứng dụng tự thêm root vào truststore từ một URL, request body hoặc chứng thư do attacker tự cung cấp.
- [ ] Kiểm tra CRL/OCSP/short-lived certificates theo chính sách, không dùng cài đặt offline demo để thay production.
- [ ] Bảo vệ khóa CA, root offline/HSM, phân quyền phát hành leaf, quản lý audit, backup, revocation và response khi key lộ.

## 14. Bài tập thực hành

1. Chạy script `keytool` và `CertificateRolesDemo`. Vì sao **`Other root self-signed: true`** vẫn đồng thời cho **`PKIX with unrelated root: false`**? Đây là minh họa cho sự khác nhau nào?
2. Sau khi tạo certificate tạm cho `intermediate` bằng `-genkeypair`, trước khi import certificate reply, nó có self-signed không? Vì sao sau khi root ký và import lại, câu trả lời đổi thành `false`?
3. Kiểm tra `root.getBasicConstraints()`, `intermediate.getBasicConstraints()` và `leaf.getBasicConstraints()`. Giải thích ý nghĩa pathLen root 1, intermediate 0, leaf -1. Vì sao intermediate vẫn có thể ký leaf?
4. Thử dùng `leaf.verify(root.getPublicKey())` thay cho intermediate public key. Dự đoán kết quả và giải thích vì sao tên issuer/link chữ ký phải khớp khóa phát hành.
5. Đổi trust anchor từ root sang một certificate tự ký mới do bạn tạo. Vì sao path validation fail dù root mới `verify(selfKey)` thành công?
6. Đổi `SAN=dns:service.example.test` thành một tên khác khi phát hành leaf. Giữ nguyên root/intermediate và kiểm tra riêng path PKIX và expected hostname; vì sao **hai kết luận khác nhau**?
7. Thử phát hành intermediate **không có BasicConstraints CA=true** rồi yêu cầu nó ký leaf. PKIX cần bảo vệ điều gì và vì sao chỉ `leaf.verify(intermediateKey)` chưa đủ?
8. Thiết kế một mTLS service cấp client cert từ cùng CA. Những bước nào biến “path hợp lệ” thành “client được phép gọi API quản trị”?
9. Viết một bản ghi trust anchor inventory gồm owner, fingerprint SPKI/cert, algorithm, mục đích, ngày hết hạn, rotation plan, cách cập nhật tin cậy và cách thu hồi.
10. Mô tả quá trình đổi intermediate trong hệ thống có hàng nghìn service và nhiều JDK: certificate chain phục vụ, truststore, compatibility, CRL/OCSP, version, thời điểm cutover và quy trình rollback.

## Tài liệu tham khảo

- [RFC 5280 — Internet X.509 Public Key Infrastructure Certificate and CRL Profile](https://www.rfc-editor.org/rfc/rfc5280): định nghĩa BasicConstraints, KeyUsage, CA/pathLen, path validation, self-issued/self-signed.
- [Java SE 17 — X509Certificate](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/X509Certificate.html): `getBasicConstraints`, `verify`, SAN, KeyUsage, EKU, validity.
- [Java SE 17 — CertificateFactory](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertificateFactory.html): parse certificate và tạo `CertPath`.
- [Java SE 17 — CertPathValidator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertPathValidator.html): kiểm tra một đường dẫn với thuật toán PKIX.
- [Java SE 17 — CertPathBuilder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertPathBuilder.html): dựng đường dẫn chứng chỉ.
- [Java SE 17 — PKIXParameters](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/PKIXParameters.html): trust anchors, revocation và tham số path validation.
- [Java SE 17 — TrustAnchor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/TrustAnchor.html): biểu diễn điểm tin cậy do ứng dụng cấu hình.
- [Java SE 17 — KeyStore](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.html): lưu trữ certificate/trust material và key entries.
- [Java SE 17 — SSLParameters](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLParameters.html): cấu hình endpoint identification.
- [JDK `keytool` reference](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html): `-genkeypair`, `-certreq`, `-gencert`, `-importcert` và `-exportcert`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="x509-certificate-003">13.3 Đọc chứng thư bằng CertificateFactory từ DER hoặc PEM</a>

<details>
<summary>Xem chi tiết</summary>

# 13.3 Đọc chứng thư bằng CertificateFactory từ DER hoặc PEM

Trong Java, `java.security.cert.CertificateFactory` là API tiêu chuẩn để **đọc (parse) chứng thư X.509 từ dữ liệu đã mã hóa**. Một chứng thư có thể được cung cấp dưới dạng **DER nhị phân** hoặc **PEM văn bản**; sau khi đọc, Java trả về đối tượng `X509Certificate` để xem subject, issuer, public key, số serial, thời gian hiệu lực và các extension.

Điểm quan trọng nhất: **đọc được chứng thư không có nghĩa là chứng thư được tin cậy**. `CertificateFactory.generateCertificate(...)` kiểm tra/giải mã cấu trúc chứng thư, nhưng **không tự xác minh chuỗi CA tin cậy, hostname, mục đích sử dụng, thu hồi hoặc chính sách TLS**. Bài này trình bày đúng phạm vi đọc DER/PEM và chỉ rõ ranh giới với kiểm tra trust để tránh nhầm lẫn.

Sau bài này, bạn có thể:

- Phân biệt DER (binary ASN.1) và PEM (`BEGIN/END CERTIFICATE` + Base64 DER) về nội dung thực.
- Dùng `CertificateFactory.getInstance("X.509")` cùng `generateCertificate(InputStream)` để đọc một chứng thư từ file DER hoặc PEM.
- Sử dụng `generateCertificates(InputStream)` khi một nguồn chứa nhiều chứng thư hoặc bộ chứng thư PKCS#7.
- Truy xuất trường X.509 một cách an toàn, phân biệt thông tin metadata với kết quả kiểm tra trust.
- Xử lý `CertificateException` và lỗi I/O đúng tầng, không mặc định rằng đuôi file quyết định định dạng.
- Kiểm chứng bằng chương trình Java 17+ có thể chạy, bao gồm so sánh cùng một chứng thư sau khi parse qua DER và PEM.

## 1. Chứng thư X.509 là gì và không phải là gì?

Chứng thư X.509 là cấu trúc dữ liệu đã được ký, thường liên kết **public key** với một tập thuộc tính của chủ thể do issuer chứng nhận theo chính sách PKI. Các thành phần điển hình:

| Trường / thành phần | Ý nghĩa |
| --- | --- |
| **Subject** | Danh tính được mô tả trong chứng thư (Distinguished Name). Với TLS, tên miền thường được kiểm tra qua **Subject Alternative Name (SAN)**, không chỉ dựa vào CN. |
| **Issuer** | Danh tính bên phát hành chứng thư (thường là CA); tên issuer một mình chưa chứng minh chứng thư do CA tin cậy cấp. |
| **Serial number** | Số định danh chứng thư do issuer gán; không phải private key. |
| **NotBefore / NotAfter** | Khoảng thời gian hiệu lực ghi trong chứng thư. |
| **Subject Public Key Info** | Public key và thuật toán/bộ tham số liên quan. |
| **Signature algorithm + signature** | Cách chứng thư được ký và giá trị chữ ký của bên phát hành. |
| **Extensions** | SAN, KeyUsage, ExtendedKeyUsage, BasicConstraints, Authority Key Identifier, Subject Key Identifier, CRL Distribution Points và các trường khác. |

Bên ngoài còn có private key tương ứng public key nhưng **private key không nằm trong chứng thư X.509**. Đọc file `.cer` hoặc `.pem` chứa **CERTIFICATE** không có nghĩa đã lấy được khóa bí mật để ký hay giải mã.

Một chứng thư có thể:
- Được parse hoàn toàn hợp lệ nhưng **hết hạn**.
- Có chữ ký toán học đúng nhưng chuỗi CA không được tin cậy.
- Thuộc CA được tin cậy nhưng **không hợp lệ cho hostname hoặc mục đích** của yêu cầu.
- Là chứng thư tự ký hợp lệ về cú pháp, nhưng không phải trust anchor của hệ thống.
- Chứa public key dùng được cho kiểm tra chữ ký, nhưng chưa thể dùng để nhận diện một người/service cho tới khi trust policy chấp nhận.

Do đó cần phân biệt rõ **parse**, **kiểm tra thời gian**, **kiểm tra chữ ký chứng thư**, **xây dựng/kiểm tra cert path**, và **ràng buộc danh tính ứng dụng**.

## 2. DER và PEM: cùng dữ liệu X.509, hai hình thức biểu diễn

### 2.1. DER — binary encoding

**DER (Distinguished Encoding Rules)** là tập quy tắc mã hóa ASN.1 xác định duy nhất biểu diễn nhị phân của một cấu trúc. Một chứng thư X.509 DER là **dãy byte nhị phân**, không phải UTF-8 văn bản có thể đọc bằng mắt.

~~~text
cert.der hoặc cert.cer
┌───────────────────────────────────────────────┐
│ Các byte ASN.1 DER của X.509 Certificate       │
│ SEQUENCE { tbsCertificate, signatureAlgorithm, │
│            signatureValue }                    │
└───────────────────────────────────────────────┘
~~~

Một vài file extension có thể gặp: `.der`, `.cer`, `.crt`. **Đuôi file không có ý nghĩa mật mã bắt buộc**: `.cer` có thể chứa DER hoặc PEM tùy nguồn, còn `.crt` cũng có thể là cả hai. Dựa vào **nội dung và giao thức cung cấp**, không dựa vào đuôi file khi quyết định parser.

Sai lầm phổ biến là đọc DER bằng `Files.readString(path)` rồi Base64-decode hoặc cố xử lý bằng regex. Binary DER phải được đưa vào `InputStream`/`byte[]` như dữ liệu nhị phân, không tự ý đổi encoding chữ.

### 2.2. PEM — text armor của DER

**PEM** trong trường hợp chứng thư là biểu diễn Base64 của **cùng byte DER**, được bao bằng đúng marker:

~~~text
-----BEGIN CERTIFICATE-----
MIIB...các dòng Base64 DER...ABCD
-----END CERTIFICATE-----
~~~

Đây là **sơ đồ minh họa**, chuỗi Base64 bị rút gọn ở trên **không phải chứng thư có thể parse**. PEM đúng thực sự phải chứa toàn bộ DER hợp lệ, được encode Base64; phần đầu và cuối phải đúng.

Đặc điểm:
- PEM chứng thư chứa **DER đã Base64 hóa** và các marker. Nó không biến public key thành private key, không thêm trust.
- Dấu xuống dòng trong phần Base64 thường dùng độ dài dòng dễ đọc (ví dụ 64 ký tự), không phải trường X.509.
- File PEM có thể chứa **một hoặc nhiều** khối `BEGIN CERTIFICATE`, tùy định dạng/giao thức.
- `-----BEGIN PUBLIC KEY-----` là biểu diễn public key riêng, **không phải nguyên một chứng thư**.
- `-----BEGIN PRIVATE KEY-----`, `-----BEGIN ENCRYPTED PRIVATE KEY-----` hoặc `-----BEGIN RSA PRIVATE KEY-----` chứa khóa private theo định dạng tương ứng, **không được đưa vào CertificateFactory** với kỳ vọng tạo `X509Certificate`.
- `-----BEGIN CERTIFICATE REQUEST-----` (CSR) cũng không phải chứng thư đã phát hành.
- `-----BEGIN PKCS7-----` hoặc file PKCS#7/`.p7b` có thể chứa **bộ chứng thư**, cần dùng đúng API/định dạng và không nhầm là DER của một certificate đơn lẻ.

**Quan hệ DER/PEM:**

~~~text
DER bytes
   |
   | Base64 encode + BEGIN/END CERTIFICATE + xuống dòng
   v
PEM text
   |
   | bỏ armor + Base64 decode theo cấu trúc
   v
DER bytes ban đầu

CertificateFactory("X.509").generateCertificate(...)
          |
          v
X509Certificate
~~~

Không cần tự viết PEM parser thủ công chỉ để đọc chứng thư. Theo hợp đồng Java SE 17, X.509 `CertificateFactory.generateCertificate(InputStream)` phải nhận chứng thư **DER binary** hoặc DER ở dạng **printable Base64** với marker `BEGIN/END CERTIFICATE` chính xác.

### 2.3. Bảng so sánh

| Tiêu chí | DER | PEM |
| --- | --- | --- |
| Dạng vật lý | Binary ASN.1 DER | ASCII text gồm Base64 DER + marker |
| Đọc trực tiếp bằng text editor | Không thích hợp | Có thể nhìn marker và Base64, nhưng không thấy ý nghĩa trường X.509 |
| Hàm đọc X.509 Java | `generateCertificate(InputStream)` | **Cùng hàm** `generateCertificate(InputStream)` |
| Có cần `Base64.getDecoder()` trước không? | Không | **Không** nếu đưa PEM chuẩn trực tiếp vào `CertificateFactory` |
| Private key có nằm sẵn không? | Không, nếu file đúng là X.509 certificate | Không, nếu khối đúng là `CERTIFICATE` |
| Trust được kiểm tra khi parse? | **Không** | **Không** |
| Dung lượng | Thường nhỏ hơn PEM vì không có Base64/marker | Thường lớn hơn DER vì Base64/marker |
| Nhiều certificate | Một hoặc nhiều DER theo định dạng/bộ dữ liệu phù hợp | Có thể nối nhiều khối CERTIFICATE trong một nguồn |

## 3. API chuẩn: `CertificateFactory` và `X509Certificate`

Hai import chính:

~~~java
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
~~~

Đọc một chứng thư, bất kể DER hay PEM chuẩn:

~~~java
CertificateFactory factory = CertificateFactory.getInstance("X.509");
X509Certificate cert = (X509Certificate) factory.generateCertificate(inputStream);
~~~

Đây là **mảnh code minh họa**, cần có `inputStream` thực và xử lý exception. Với factory loại `"X.509"`, chuẩn Java yêu cầu chứng thư trả về có thể biểu diễn bằng `X509Certificate`.

| API | Vai trò |
| --- | --- |
| `CertificateFactory.getInstance("X.509")` | Chọn certificate factory theo JCA provider lookup. Java SE yêu cầu hỗ trợ loại X.509. |
| `getInstance("X.509", providerName)` | Chọn provider theo tên đã đăng ký khi triển khai yêu cầu; không bắt buộc cho ứng dụng thông thường. |
| `factory.getProvider()` | Cho biết provider xử lý parsing, hữu ích cho kiểm thử tương thích. |
| `factory.generateCertificate(InputStream)` | Đọc **một chứng thư** từ stream; ném `CertificateException` nếu parse thất bại. |
| `factory.generateCertificates(InputStream)` | Đọc một **collection** chứng thư từ stream chứa chuỗi các chứng thư/PKCS#7, có thể là collection rỗng. |
| `factory.generateCertPath(...)` | Tạo đối tượng `CertPath` từ encoding path hoặc danh sách chứng thư; **chưa phải validate trust**. |
| `cert.getSubjectX500Principal()` | Trả `X500Principal` của subject (ưu tiên hơn API `getSubjectDN()` đã deprecated). |
| `cert.getIssuerX500Principal()` | Trả `X500Principal` của issuer. |
| `cert.getNotBefore()/getNotAfter()` | Trả giới hạn thời gian trong chứng thư. |
| `cert.getPublicKey()` | Trả public key từ chứng thư. |
| `cert.getEncoded()` | Trả binary DER của certificate; không phải PEM, không phải private key. |
| `cert.checkValidity()` | Kiểm tra **thời gian hiệu lực hiện tại**; không kiểm tra toàn bộ trust chain. |
| `cert.verify(issuerPublicKey)` | Kiểm tra chữ ký của chứng thư bằng public key đã cho; không chứng minh issuer được tin cậy. |

### 3.1. Lỗi theo API

- `getInstance("X.509")` có thể ném `CertificateException` nếu type/provider không tồn tại; `getInstance(type, name)` còn có thể ném `NoSuchProviderException`. X.509 là loại bắt buộc trên Java SE, nhưng provider cụ thể có thể không hỗ trợ.
- `generateCertificate(...)` ném `CertificateException` khi parse dữ liệu lỗi.
- `Files.newInputStream` hoặc `Files.size` có thể ném `IOException`, `NoSuchFileException`, `AccessDeniedException` — đây là lỗi **đọc file**, không phải lỗi ASN.1.
- `cert.checkValidity()` có thể ném `CertificateExpiredException` hoặc `CertificateNotYetValidException` dù việc parse đã thành công.
- Xử lý PKIX có thể ném `CertPathValidatorException` và lỗi trust/policy khác. Đừng quy mọi lỗi kiểu đó thành “DER/PEM hỏng”.

### 3.2. Đọc một file so với bundle

**Một file có một certificate**: `generateCertificate(in)` phù hợp và rõ nghĩa.

**Một file có nhiều PEM blocks** hoặc PKCS#7 certificate set: `generateCertificates(in)` thường hợp lý hơn; nó trả collection, **không hứa hẹn** thứ tự đã là chain leaf -> intermediate -> root, cũng không hứa collection đủ để tạo một trust path hợp lệ.

**Một cert path cần được xây dựng/validate**: chuẩn bị danh sách/chứng thư đúng thứ tự, trust anchors và chính sách PKIX; việc tạo đối tượng `CertPath` không tương đương kiểm tra thành công `CertPathValidator.validate(...)`.

## 4. Chương trình Java 17+: đọc file DER hoặc PEM bằng cùng một hàm

Chương trình dưới đây là ví dụ chính của bài: `ReadX509File.java` chỉ nhận **đường dẫn một file chứa một chứng thư**. Nó không dùng kiểm tra file extension; `CertificateFactory("X.509")` quyết định parse dựa trên nội dung DER/PEM hợp lệ.

**Tệp: `ReadX509File.java`**

~~~java
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HexFormat;

public class ReadX509File {
    static X509Certificate parse(InputStream in)
            throws CertificateException {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) factory.generateCertificate(in);
    }

    static void inspect(X509Certificate cert) throws Exception {
        System.out.println("Type: " + cert.getType());
        System.out.println("Version: " + cert.getVersion());
        System.out.println("Subject: " + cert.getSubjectX500Principal());
        System.out.println("Issuer: " + cert.getIssuerX500Principal());
        System.out.println("Serial (hex): "
                + cert.getSerialNumber().toString(16));
        System.out.println("Not before: "
                + cert.getNotBefore().toInstant());
        System.out.println("Not after: "
                + cert.getNotAfter().toInstant());
        System.out.println("Public key algorithm: "
                + cert.getPublicKey().getAlgorithm());

        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(cert.getEncoded());
        System.out.println("SHA-256 DER fingerprint: "
                + HexFormat.of().formatHex(digest));
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println(
                    "Usage: java ReadX509File <certificate.der|certificate.pem>");
            return;
        }

        Path path = Path.of(args[0]);
        long fileSize = Files.size(path);
        if (fileSize == 0 || fileSize > 1024L * 1024L) {
            throw new IllegalArgumentException(
                    "Empty or unexpectedly large certificate file");
        }

        try (InputStream in = new BufferedInputStream(
                Files.newInputStream(path))) {
            X509Certificate cert = parse(in);
            inspect(cert);
        }
    }
}
~~~

### 4.1. Biên dịch và chạy

~~~bash
javac ReadX509File.java
java ReadX509File certificate.der
java ReadX509File certificate.pem
~~~

Các file `certificate.der` và `certificate.pem` phải là chứng thư thực sự do bài tập/hệ thống cung cấp, không phải nội dung placeholder trong README. Nếu hai file chứa **cùng một chứng thư**, các trường đọc được và fingerprint SHA-256 trên **DER chuẩn hóa** sẽ giống nhau.

Đầu ra có cấu trúc:

~~~text
Type: X.509
Version: <phiên bản từ chứng thư, thường là 3>
Subject: <tên subject từ chứng thư>
Issuer: <tên issuer từ chứng thư>
Serial (hex): <serial thực của chứng thư>
Not before: <thời điểm trong chứng thư>
Not after: <thời điểm trong chứng thư>
Public key algorithm: <RSA, EC hoặc thuật toán thực>
SHA-256 DER fingerprint: <64 ký tự hex của digest SHA-256>
~~~

Các dòng trong dấu `<...>` là **mô tả biến động theo input**, không phải stdout cố định để chép nguyên văn. Trong mọi trường hợp parse thành công bằng X.509 factory, dòng `Type: X.509` là kết quả mong đợi.

### 4.2. Giải thích cặn kẽ

1. `CertificateFactory.getInstance("X.509")` tạo engine parse chứng thư, **không cần tạo `KeyFactory`** và không cần private key.
2. `Files.newInputStream(path)` đọc byte gốc, **không chuyển DER thành `String`**. `BufferedInputStream` hỗ trợ `mark/reset` và dùng `try-with-resources` để đóng file đúng cách.
3. `generateCertificate(in)` nhận DER binary hoặc PEM Base64 có marker hợp lệ. Khi source chỉ chứa một cert, API này phù hợp.
4. `X509Certificate` cho phép xem subject/issuer/public key/validity. `getEncoded()` trả **DER**, nên SHA-256 của cùng cert là như nhau dù lúc nhập qua DER hoặc PEM.
5. SHA-256 fingerprint là **định danh byte của chứng thư**, không phải bằng chứng cert được tin cậy. Nếu dùng pinning, phải so fingerprint với giá trị **được cấp qua kênh tin cậy**, có kế hoạch rotation, không lấy fingerprint từ chính đầu vào chưa xác thực rồi cho rằng an toàn.
6. `Files.size` chỉ kiểm tra sơ bộ dung lượng; file có thể thay đổi giữa `size` và `open` (TOCTOU). Trong production, cần giới hạn **thực sự** số byte parser nhận, kiểm soát đường dẫn/symlink và nguồn dữ liệu. Ví dụ chủ yếu minh họa API X.509.
7. Chương trình **không gọi `checkValidity` và không validate PKIX**, vì mục tiêu hiện tại là **đọc/inspect**, không cấp quyết định trust. Nếu ứng dụng cần xác minh, phải có bước riêng.

### 4.3. Nếu chỉ cần subject và public key thì sao?

Có thể dùng:

~~~java
X509Certificate cert = ...; // Kết quả parse từ CertificateFactory.
System.out.println(cert.getSubjectX500Principal());
System.out.println(cert.getPublicKey().getAlgorithm());
~~~

Nhưng **không được cấp quyền truy cập** chỉ vì subject in ra giống `CN=example.com`. Subject DN là dữ liệu trong cert; kiểm tra tên miền TLS thường dựa trên SAN, và phải có chuỗi trust hợp lệ, hạn chế ExtendedKeyUsage/KeyUsage và các điều kiện chính sách khác.

## 5. Thử nghiệm không cần file: DER -> PEM -> parse cùng một chứng thư

Để xác thực trực tiếp cách Java đọc DER và PEM mà không cần mang một chứng thư giả cố định trong mã nguồn, ví dụ tiếp theo sử dụng **một chứng thư CA công khai sẵn có trong trust anchors mặc định của JVM** làm **dữ liệu thử nghiệm**. Mục đích là dùng byte thật của một chứng thư đã parse được để kiểm thử hai dạng encoding. Đây **không phải** ví dụ thiết lập trust policy mới hoặc chứng minh certificate do người dùng cung cấp được tin cậy.

**Điều kiện chạy:** JVM phải có ít nhất một X.509 trusted issuer trong trust manager mặc định. Một JVM dùng truststore trống/custom có thể không thỏa điều kiện này; khi đó dùng cert hợp lệ khác làm fixture test, không bỏ kiểm tra trust.

**Tệp: `CertificateFormatsDemo.java`**

~~~java
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class CertificateFormatsDemo {
    static X509Certificate firstAnchorForDemonstration()
            throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((KeyStore) null);

        for (TrustManager manager : tmf.getTrustManagers()) {
            if (manager instanceof X509TrustManager x509) {
                X509Certificate[] issuers = x509.getAcceptedIssuers();
                if (issuers.length > 0) {
                    return issuers[0];
                }
            }
        }
        throw new IllegalStateException(
                "No default trust anchor available for this demo");
    }

    public static void main(String[] args) throws Exception {
        byte[] der = firstAnchorForDemonstration().getEncoded();

        String pem = "-----BEGIN CERTIFICATE-----\n"
                + Base64.getMimeEncoder(
                        64, new byte[] {'\n'}).encodeToString(der)
                + "\n-----END CERTIFICATE-----\n";

        CertificateFactory factory =
                CertificateFactory.getInstance("X.509");

        X509Certificate parsedDer = (X509Certificate)
                factory.generateCertificate(
                        new ByteArrayInputStream(der));

        X509Certificate parsedPem = (X509Certificate)
                factory.generateCertificate(
                        new ByteArrayInputStream(
                                pem.getBytes(StandardCharsets.US_ASCII)));

        System.out.println("DER type: " + parsedDer.getType());
        System.out.println("PEM type: " + parsedPem.getType());
        System.out.println("Same certificate: "
                + Arrays.equals(
                        parsedDer.getEncoded(), parsedPem.getEncoded()));

        int count = factory.generateCertificates(
                new ByteArrayInputStream(
                        (pem + pem).getBytes(StandardCharsets.US_ASCII)))
                .size();
        System.out.println("PEM bundle count: " + count);

        try {
            factory.generateCertificate(
                    new ByteArrayInputStream(new byte[] {0, 1, 2}));
            throw new AssertionError("Malformed DER accepted");
        } catch (CertificateException expected) {
            System.out.println("Malformed DER rejected: true");
        }
    }
}
~~~

Chạy bằng Java 17+ có trust anchors mặc định:

~~~bash
javac CertificateFormatsDemo.java
java CertificateFormatsDemo
~~~

Stdout đã kiểm chứng trên JVM có default trust anchors:

~~~text
DER type: X.509
PEM type: X.509
Same certificate: true
PEM bundle count: 2
Malformed DER rejected: true
~~~

### 5.1. Tại sao ví dụ này có giá trị?

- `getEncoded()` của `X509Certificate` là DER. Ta tái tạo PEM bằng `Base64.getMimeEncoder(64, new byte[]{'\n'})` và header/footer đúng, rồi đưa thẳng PEM vào `CertificateFactory`.
- Không cần `Base64.getDecoder()` trước khi gọi `generateCertificate` với PEM chuẩn, tránh lỗi gỡ marker và whitespace.
- `Arrays.equals(...)` so sánh **DER của chứng thư đã parse**, không so raw bytes của file: PEM và DER raw đương nhiên khác nhau, nhưng X.509 objects mô tả cùng certificate.
- `generateCertificates` xử lý hai khối PEM nối tiếp, trả số certificate tương ứng trong ví dụ (2). **Nó không tự sắp xếp hai certificate thành chain và không validate trust**.
- Input `{0,1,2}` không phải DER X.509; provider ném `CertificateException` khi cố parse. Ứng dụng thật cần kiểm soát message lỗi, không trả nội bộ parser cho client.

### 5.2. Có nên dùng cert từ trust manager để đọc file sản xuất?

Không. Trong bài, cert từ trust manager chỉ là **fixture thuận tiện** để có dữ liệu X.509 hợp lệ, giống như sử dụng sample cert trong test. Chương trình `ReadX509File` mới là ví dụ ứng dụng để đọc file do người dùng chỉ định. Trong hệ thống TLS thực, tin cậy peer certificate phải thông qua cơ chế JSSE/PKIX đã cấu hình, không chọn ngẫu nhiên `getAcceptedIssuers()[0]` làm trust policy.

## 6. Đọc nhiều chứng thư từ PEM bundle hoặc PKCS#7

Một số nguồn cung cấp:

- Một file PEM gồm nhiều đoạn `BEGIN CERTIFICATE`, thường chứa leaf và intermediate certificates.
- Một DER/PEM gói PKCS#7 SignedData chứa tập chứng thư.
- File chain do máy chủ TLS hoặc công cụ CA xuất ra; không nhất thiết chứa root CA.
- Một tập CA certificates dùng để nạp vào kho tin cậy.

**Không mặc định mọi file có nhiều certificate đã được sắp xếp hoặc chứng minh quan hệ issuer/subject.**

Với X.509 `CertificateFactory`:

~~~java
import java.io.InputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Collection;

CertificateFactory factory = CertificateFactory.getInstance("X.509");
try (InputStream in = /* stream của bundle đã được kiểm soát */) {
    Collection<? extends Certificate> certificates =
            factory.generateCertificates(in);
    for (Certificate item : certificates) {
        // Mỗi item có thể cast sang X509Certificate sau khi factory X.509 parse.
    }
}
~~~

Đây là **mảnh API minh họa**, không phải file Java độc lập vì `in` phụ thuộc nguồn thực. Khi viết code, nên dùng `try-with-resources` với stream đã mở, giới hạn số lượng cert/bytes và loại bỏ input rỗng nếu nghiệp vụ bắt buộc có ít nhất một certificate.

### 6.1. `generateCertificate` trên stream nhiều cert

JDK mô tả: nếu `InputStream` **không hỗ trợ `mark/reset`**, `generateCertificate` có thể tiêu thụ **toàn bộ stream**; nếu có hỗ trợ `mark/reset`, mỗi lần gọi sẽ đọc một certificate và đưa vị trí đọc tới sau marker kết thúc của certificate đó.

Vì vậy, với nhiều chứng thư:

- Ưu tiên `generateCertificates` nếu mục tiêu là lấy toàn bộ tập chứng thư từ một stream/bundle.
- Nếu cần đọc tuần tự từng cert bằng nhiều lần `generateCertificate`, dùng `BufferedInputStream` để cung cấp `mark/reset` và thiết kế điểm dừng phù hợp; đừng giả định mỗi lần gọi trên raw `FileInputStream` luôn tiêu thụ đúng một cert.
- `InputStream.available()` **không phải thước đo tổng kích thước** hoặc cách kiểm tra EOF đáng tin cậy cho mọi stream mạng. Ví dụ đọc file của JDK dùng `available` trên `BufferedInputStream`, nhưng ứng dụng streaming thực nên thiết kế EOF/protocol framing và giới hạn byte rõ ràng.
- `generateCertificates` có thể trả về collection trống trong trường hợp không có cert; phải xử lý nếu nghiệp vụ yêu cầu một hoặc nhiều certificate.

### 6.2. `CertPath` và thứ tự chain

`CertificateFactory.generateCertPath(List<? extends Certificate>)` tạo một `CertPath` với thứ tự danh sách mà ứng dụng đưa vào. Nếu cần dựng path, ứng dụng phải đảm bảo danh sách đúng **leaf -> intermediate -> ...** theo yêu cầu của PKIX/giao thức, hoặc dùng `CertPathBuilder` để dựng đường chứng thực.

**Một collection từ `generateCertificates` không mặc nhiên là trust chain hợp lệ.** Có thể có chứng thư không liên quan, trùng nhau, thiếu intermediate hoặc thứ tự khác. Việc kiểm tra chữ ký trên chain và chọn trust anchor là **bước riêng**.

### 6.3. Phân biệt các loại file dễ nhầm

| Dữ liệu đầu vào | Marker/đặc điểm | API đúng hướng |
| --- | --- | --- |
| X.509 certificate DER | Dữ liệu binary ASN.1 DER | `CertificateFactory("X.509").generateCertificate(in)` |
| X.509 certificate PEM | `BEGIN CERTIFICATE` ... `END CERTIFICATE` | **Cùng API** `generateCertificate(in)` |
| Nhiều PEM certificates | Nhiều khối `BEGIN CERTIFICATE` | `generateCertificates(in)` |
| PKCS#7 certificate set | SignedData chứa certificates | `generateCertificates(in)` hoặc `generateCertPath(in, "PKCS7")` tùy mục tiêu |
| X.509 public key SPKI | `BEGIN PUBLIC KEY` hoặc binary SPKI | `KeyFactory` + `X509EncodedKeySpec` |
| PKCS#8 private key | `BEGIN PRIVATE KEY` hoặc binary PKCS#8 | API import private key/`KeyFactory` và bảo vệ riêng; không phải CertificateFactory |
| Certificate signing request (CSR) | `BEGIN CERTIFICATE REQUEST` | Parser CSR phù hợp, không phải `generateCertificate` |
| Keystore `PKCS12/JKS` | Container lưu key/cert chain | `KeyStore` với đúng store type, credential và policy |

**Chú ý:** `X509EncodedKeySpec` có tên “X509” nhưng chứa **SubjectPublicKeyInfo**, tức **public key encoding**, không phải nguyên bản X.509 certificate. Thao tác lấy public key từ certificate đã parse là `cert.getPublicKey()`; không dùng `KeyFactory` để “đọc chứng thư” toàn phần.

## 7. Đọc metadata X.509 một cách có trách nhiệm

Sau parse, `X509Certificate` cung cấp nhiều thông tin có ích cho audit và kiểm tra cấu trúc:

### 7.1. Subject, issuer, serial, version

~~~java
X509Certificate cert = ...;
int version = cert.getVersion();
String subject = cert.getSubjectX500Principal().getName();
String issuer = cert.getIssuerX500Principal().getName();
String serialHex = cert.getSerialNumber().toString(16);
~~~

Đây là đoạn code minh họa. `getSubjectDN()` / `getIssuerDN()` cũ bị deprecated từ Java 16; nên dùng `getSubjectX500Principal()` / `getIssuerX500Principal()` để làm việc với `X500Principal`. **Không định danh/cho phép truy cập chỉ bằng chuỗi subject** nếu chưa có bước trust.

`getVersion()` trong Java X509Certificate trả version theo **cách biểu diễn 1-based** (ví dụ v3 trả 3). `getSerialNumber()` là `BigInteger`, thường hiển thị hex để đối chiếu, nhưng đừng coi serial duy nhất toàn cầu nếu không kết hợp issuer.

### 7.2. Hạn hiệu lực và SAN

~~~java
cert.checkValidity();    // Chỉ kiểm tra thời gian hiện tại.
var notBefore = cert.getNotBefore();
var notAfter = cert.getNotAfter();
var sans = cert.getSubjectAlternativeNames();
~~~

`getSubjectAlternativeNames()` có thể trả `null` nếu extension không có; dữ liệu SAN có nhiều loại như DNS, IP, URI, email. Không lấy một chuỗi subject CN rồi tự so hostname bằng `equals`: TLS hostname verification có quy tắc chứng thư và SAN riêng, bao gồm wildcard/IP/IDNA tùy giao thức. Khi làm TLS, nên để **JSSE endpoint identification/trust manager** đã được cấu hình xử lý.

`cert.checkValidity()`:
- Ném `CertificateNotYetValidException` nếu thời điểm hiện tại trước `NotBefore`.
- Ném `CertificateExpiredException` nếu sau `NotAfter`.
- Không kiểm tra issuer signature, chain trust, revocation, SAN hostname, EKU, policy hay thuật toán yếu.

### 7.3. Public key, key usage và basic constraints

Các API đáng biết:
- `cert.getPublicKey().getAlgorithm()`: tên thuật toán public key (`RSA`, `EC`, ...), không phải trực tiếp tên thuật toán chữ ký certificate.
- `cert.getSigAlgName()` / `getSigAlgOID()`: thông tin thuật toán **issuer đã dùng để ký certificate**.
- `cert.getKeyUsage()`: bitmap chính sách key usage, có thể `null`.
- `cert.getExtendedKeyUsage()`: danh sách OID EKU (hoặc `null`).
- `cert.getBasicConstraints()`: `-1` nếu không phải CA theo extension; không chỉ cần giá trị này là đủ xác nhận CA được tin cậy.
- `cert.getCriticalExtensionOIDs()` và `hasUnsupportedCriticalExtension()`: extension quan trọng cần xử lý theo quy tắc PKIX.

Không tự quyết định một chứng thư dùng được cho TLS server, code signing, email signing hoặc client auth chỉ bằng một field; cần kiểm tra **nhiều ràng buộc với chain, trust, policy và context**.

### 7.4. Fingerprint SHA-256

`SHA-256(cert.getEncoded())` giúp xác định **chính xác certificate DER**. Vì PEM và DER cùng bản chất chứa cùng DER, fingerprint của chứng thư phải giống nhau nếu nội dung thực cùng một certificate.

Tuy nhiên:
- Fingerprint không phải private key, thường có thể công bố; nó vẫn là metadata của đối tượng có thể cần kiểm soát trong hệ thống.
- Không nhầm fingerprint của **certificate** với fingerprint của **public key (SPKI)**: certificate có thể được cấp lại với cùng public key nhưng serial/validity/issuer khác, dẫn đến certificate fingerprint khác.
- Pinning fingerprint chỉ an toàn nếu nguồn giá trị kỳ vọng đáng tin cậy và có kế hoạch cập nhật/rotation. “Đọc cert từ đối thủ rồi tính SHA-256 và tin ngay” không có ý nghĩa xác thực.

## 8. Ranh giới quan trọng nhất: parse thành công KHÔNG bằng trust hợp lệ

Có năm việc dễ bị gộp sai:

| Bước | API/cơ chế | Chứng minh được | **Không chứng minh** |
| --- | --- | --- | --- |
| **Parse DER/PEM** | `CertificateFactory.generateCertificate` | Byte biểu diễn một X.509 certificate có cấu trúc parser chấp nhận. | Không chứng minh issuer đáng tin cậy hoặc cert còn hạn. |
| **Kiểm tra thời gian** | `X509Certificate.checkValidity` | Thời điểm nằm trong khoảng NotBefore–NotAfter. | Không chứng minh chữ ký/chain/hostname/CA đáng tin. |
| **Kiểm tra một chữ ký certificate** | `cert.verify(publicKey)` | Certificate đã được ký bằng private key tương ứng public key cung cấp (nếu verify thành công). | Public key cung cấp thuộc CA tin cậy hoặc đủ điều kiện cấp cert. |
| **Kiểm tra chuỗi chứng thư** | `CertPathValidator("PKIX")` + `PKIXParameters`, trust anchors, path | Đường chứng thực được validate theo tham số/chính sách cung cấp. | Hostname hoặc mọi quy tắc ứng dụng nếu chưa được cấu hình/kiểm tra. |
| **Xác thực endpoint/ứng dụng** | JSSE hostname verification, authz policy, EKU/SAN/tenant | Cert phù hợp danh tính/mục đích cụ thể sau các bước trust. | Không tự động bảo vệ mọi hành vi nghiệp vụ. |

Đối với một ứng dụng **TLS/HTTPS**, dùng `SSLContext`, `X509TrustManager` / `TrustManagerFactory` và endpoint identification phù hợp, không tự xây dựng một “trust manager chấp nhận mọi certificate” chỉ để kết nối thành công.

Đối với việc phân tích **certificate chain ngoài TLS**, chuẩn Java có:
- `CertPathValidator.getInstance("PKIX")`.
- `PKIXParameters` hoặc `PKIXBuilderParameters` cùng trust anchors được phê duyệt.
- `CertPathBuilder` để dựng path khi chưa có thứ tự/cert trung gian đầy đủ.
- Các chính sách kiểm tra key usage, critical extensions, thời gian, revocation/CRL/OCSP và algorithm constraints.

**Pseudocode**, không phải ví dụ Java độc lập, vì cấu hình trust/chuỗi chứng thư phụ thuộc môi trường:

~~~text
certificateBytes = readLimitedBytesFromApprovedSource()
certificates = CertificateFactory("X.509").generateCertificates(stream)

target = chooseLeafCertificateByProtocol()
trustedRoots = loadApprovedTrustAnchorsFromTrustStore()
path = buildOrArrangeCertPath(target, intermediates, trustedRoots)

result = CertPathValidator("PKIX").validate(
    path, PKIXParameters(trustedRoots, configuredPolicies)
)

checkExpectedIdentityAndPurpose(target, expectedHostOrService, constraints)
if all checks pass -> accept trusted certificate for this specific purpose
else -> reject
~~~

Không được gọi `cert.verify(cert.getPublicKey())` trên một chứng thư tự ký rồi suy ra “đáng tin cậy”: phép toán tự xác minh chữ ký không chứng minh chứng thư nằm trong trust store.

### 8.1. Vì sao file có tên `root-ca.pem` vẫn không phải trust anchor?

Tên file, comment PEM, subject `CN=Trusted Root` hay việc chứng thư tự ký đều do người tạo file quyết định. **Trust anchor phải đến từ kho hoặc kênh cấu hình đã tin cậy**, không phải từ file do remote peer vừa gửi. Nếu ứng dụng cho người dùng đưa arbitrary cert vào truststore, họ có thể cấp trust ngoài ý định.

### 8.2. Vì sao parse PEM chain không đủ cho HTTPS?

Một TLS server có thể gửi leaf + intermediates nhưng không gửi root. Các cert này có thể parse đúng; chain trust cần được dựng tới một CA trong truststore client và kiểm tra rằng **SAN phù hợp hostname**, EKU, thời gian và constraints. Kiểm tra số lượng cert là không đủ.

## 9. Chi tiết kỹ thuật về InputStream

### 9.1. `mark/reset` và cách Factory đọc

Java SE 17 mô tả đối với `generateCertificate`:
- Nếu stream **không hỗ trợ `mark` và `reset`**, phương thức có thể tiêu thụ **toàn bộ InputStream**.
- Nếu stream hỗ trợ chúng, mỗi lần gọi có thể đọc **một certificate** và đặt vị trí đọc ngay sau dấu kết thúc nội tại của certificate.
- Nếu dữ liệu không có dấu kết thúc nội tại khác EOF và còn trailing garbage, có thể bị từ chối bằng `CertificateException`.

Vì vậy dùng `BufferedInputStream` là lựa chọn hợp lý khi cần kiểm soát việc đọc một stream chứa nhiều chứng thư, nhưng không biến nó thành parser framing tổng quát cho protocol mạng.

### 9.2. Không dùng `available()` làm kích thước

`InputStream.available()` phản ánh số byte **có thể đọc ngay mà không bị chặn**, không nhất thiết là tổng số byte còn lại từ file/network/HTTP stream. Không viết:

~~~java
byte[] bytes = new byte[input.available()]; // SAI cho stream tổng quát.
input.read(bytes);                            // read() cũng không chắc điền đủ.
~~~

Đoạn trên là ví dụ **anti-pattern**, không phải chương trình chạy độc lập. Với dữ liệu untrusted, đặt **giới hạn kích thước và số lượng** ngay ở tầng HTTP/file/stream, dùng API đọc được kiểm soát, và từ chối input quá lớn trước khi parse ASN.1.

### 9.3. Dùng `try-with-resources` và xử lý I/O đúng tầng

~~~java
try (InputStream in = new BufferedInputStream(
        Files.newInputStream(certPath))) {
    X509Certificate cert = (X509Certificate)
            CertificateFactory.getInstance("X.509")
                    .generateCertificate(in);
    // Chỉ kiểm tra cấu trúc/metadata tại đây.
}
~~~

Đây là mảnh code minh họa; `certPath` là đối tượng `Path` đã được kiểm soát. `try-with-resources` sẽ đóng stream kể cả khi `CertificateException` xảy ra.

Phân biệt:
- File không tồn tại, permission denied, I/O lỗi: thường là `IOException` hoặc subclass.
- Byte file không phải DER/PEM X.509 hợp lệ: `CertificateException`.
- Chứng thư parse được nhưng hết hạn: `CertificateExpiredException` khi gọi `checkValidity`.
- Chứng thư parse được nhưng chain không tin cậy: lỗi trong bước PKIX/JSSE, **không phải `generateCertificate`**.

## 10. Sự cố thực tế và cách chẩn đoán

### Tình huống A — File `.cer` không phải lúc nào cũng là DER

Một đối tác gửi `partner.cer`, trong đó có `-----BEGIN CERTIFICATE-----`. Developer tự ép đọc bằng parser DER tự viết vì đuôi `.cer`, dẫn đến lỗi. Giải pháp: cho `CertificateFactory("X.509")` đọc trực tiếp stream; nó hỗ trợ binary DER và Base64 PEM với marker đúng.

### Tình huống B — PEM bị cắt một nửa

File có đầu `BEGIN CERTIFICATE` nhưng thiếu `END CERTIFICATE` hoặc Base64 không đầy đủ. Parser có thể ném `CertificateException`. Giải pháp: kiểm tra luồng truyền file và format, không bỏ qua ngoại lệ rồi coi cert là trusted.

### Tình huống C — Chỉ có `BEGIN PUBLIC KEY`

Đó là public key dạng SPKI, không phải chứng thư X.509. Cần `KeyFactory` + `X509EncodedKeySpec` nếu mục tiêu là đọc khóa; không dùng `CertificateFactory.generateCertificate` để đọc raw public key.

### Tình huống D — PEM bundle chứa ba cert, nhưng `generateCertificate` chỉ lấy một

Hệ thống bỏ sót intermediates, path validation thất bại. Dùng `generateCertificates` để lấy collection rồi xử lý chain theo giao thức; hoặc parse từng certificate theo stream hỗ trợ mark/reset. **Không tin rằng thứ tự/chain đã được validate** vì parse thành công.

### Tình huống E — Chứng thư parse được nhưng trình duyệt báo “unknown CA”

Không có mâu thuẫn: parse chỉ kiểm tra cấu trúc. Client không tìm thấy trust anchor thích hợp hoặc chain có lỗi; phải xử lý CA trust, intermediates, policies, hostname và các ràng buộc chứng thư.

### Tình huống F — `checkValidity()` thành công, HTTPS vẫn không an toàn

Chứng thư trong thời gian hiệu lực nhưng có thể được ký bởi CA không tin cậy, SAN không khớp host hoặc bị revoke. `checkValidity` không thay PKIX/hostname verification.

### Tình huống G — SHA-256 fingerprint từ file DER khác PEM

Nếu hai file chứa **cùng một chứng thư**, fingerprint tính từ `cert.getEncoded()` phải giống nhau. Nếu fingerprint **raw file bytes** thì khác là bình thường vì PEM có ASCII armor. Nếu fingerprint DER chuẩn hóa của parsed objects khác, chúng không hoàn toàn là cùng một certificate hoặc dữ liệu đã bị thay đổi.

### Tình huống H — Chuyển DER thành String rồi đọc lỗi

Binary DER không phải UTF-8. `new String(derBytes, UTF_8)` rồi `getBytes(UTF_8)` có thể làm biến đổi byte không hợp lệ. Đọc binary qua `InputStream` hoặc byte[] gốc; chỉ PEM là dạng text/Base64. Không tự sửa input tới khi parser “đồng ý”.

### Tình huống I — Đọc file từ HTTP request do client chỉ định

Một API nhận đường dẫn file tùy ý, rồi `Files.newInputStream(Path.of(request.path))`. Đây là nguy cơ path traversal/local file disclosure. Không dùng đường dẫn tùy ý từ request; dùng allowlisted path, sandboxed upload, kích thước/loại nội dung được kiểm soát và quyền tối thiểu.

### Tình huống J — In mọi chi tiết chứng thư vào public log

Certificate về bản chất thường có thể chia sẻ công khai nhưng Subject/SAN có thể chứa thông tin tổ chức, host nội bộ hay identifier nhạy cảm. Chỉ log những metadata cần thiết, bảo vệ log và tránh ghi raw certificate/payload nếu không có nhu cầu audit hợp lệ.

## 11. Bảng lỗi thường gặp

| Sai lầm | Hậu quả | Cách đúng |
| --- | --- | --- |
| Dùng `Files.readString()` cho DER | Binary bị đọc sai/biến đổi. | Đưa file qua `InputStream` nhị phân. |
| Tự Base64-decode một PEM gồm marker bằng decoder sai | Lỗi cú pháp Base64 hoặc không tách đúng nhiều blocks. | Dùng trực tiếp `CertificateFactory.generateCertificate` cho PEM chuẩn. |
| Kỳ vọng `BEGIN PUBLIC KEY` là certificate | `CertificateException` vì không đúng cấu trúc X.509 certificate. | `KeyFactory` + `X509EncodedKeySpec` nếu mục tiêu là raw public key. |
| Dùng `getInstance("RSA")` để tạo CertificateFactory | Không phải tên certificate type. | `CertificateFactory.getInstance("X.509")`. |
| Quên cast sang `X509Certificate` | Chỉ dùng được API base `Certificate`. | Cast khi factory là X.509, sau đó truy xuất metadata. |
| Gọi `generateCertificate` cho cả PEM chain rồi nghĩ đã đọc toàn bộ | Có thể chỉ lấy một cert hoặc tiêu thụ stream theo contract. | Dùng `generateCertificates` cho collection/bundle. |
| Tin collection là path đã hợp lệ | Không có kiểm tra CA trust hoặc thứ tự. | Dùng `CertPathBuilder/Validator` với trust anchors được phê duyệt. |
| `cert.checkValidity()` coi là xác thực thành công | Chỉ kiểm tra ngày hiệu lực. | Kiểm tra PKIX, host identity, EKU, revocation/policy. |
| `cert.verify(cert.getPublicKey())` rồi tự tin cậy self-signed cert | Tự kiểm tra chữ ký không chứng minh trust. | Trust anchor phải được cấp từ nguồn đáng tin. |
| Dùng extension `.cer` để tự chọn DER/PEM | Tên file không quyết định encoding. | Kiểm tra nội dung qua chuẩn certificate factory. |
| Dùng `getSubjectDN()` đã deprecated cho API mới | Kiểu/format phụ thuộc provider. | `getSubjectX500Principal()`. |
| Log full `cert.toString()` vào log không kiểm soát | Có thể lộ metadata và làm log rất lớn. | Log các trường đã được cho phép hoặc fingerprint/hash có policy. |
| Không giới hạn input size, number of certs | CPU/memory denial-of-service do parser và collection. | Giới hạn bytes, cert count, timeout và nguồn dữ liệu. |
| Nhầm `CertificateException` với lỗi file không tồn tại | Chẩn đoán sai tầng. | Phân biệt IOException, CertificateException, PKIX exception. |
| Dùng parser tự chế để “chấp nhận mọi PEM lỗi” | Có thể chấp nhận encoding mơ hồ/malformed. | Theo định dạng X.509/PEM chuẩn, từ chối đầu vào bất thường. |
| Suy ra private key từ `cert.getPublicKey()` | Không khả thi; certificate chỉ chứa public key. | Quản lý private key riêng qua keystore/HSM/KMS. |

## 12. Thực hành kiểm tra đầu vào và QA

Khi xây dựng chức năng import chứng thư trong service:

- [ ] Xác định API nhận **chứng thư đơn lẻ** hay **certificate bundle/PKCS#7**.
- [ ] Giới hạn kích thước file/stream **thật sự**, số certificate và tổng thời gian xử lý.
- [ ] Không tin path do client đưa trực tiếp; chuẩn hóa và kiểm soát upload/permission.
- [ ] Dùng `CertificateFactory.getInstance("X.509")` trên binary stream hoặc PEM text bytes; không tự sửa encoding.
- [ ] Nếu là certificate đơn lẻ, phải rõ liệu trailing bytes/các khối khác có được chấp nhận hay không theo giao thức.
- [ ] Ghi nhận loại lỗi ở đúng tầng (`IOException`, `CertificateException`, PKIX), không in nguyên input ra log.
- [ ] Sau parse, kiểm tra các trường cần thiết (SAN, EKU, KeyUsage, BasicConstraints, issuer/subject, signature algorithm) **theo mục đích**, không chỉ lấy subject.
- [ ] Nếu quyết định trust, dùng truststore/trust anchor từ nguồn phê duyệt và kiểm tra path; không chấp nhận self-signed tùy ý.
- [ ] Nếu là TLS, giữ hostname verification và JSSE trust manager đúng cấu hình.
- [ ] Test cùng một cert DER/PEM cho cùng `cert.getEncoded()` và fingerprint.
- [ ] Test PEM nhiều cert, DER hỏng, PEM marker sai, file rỗng, file quá lớn, parser provider khác nhau.
- [ ] Phân biệt một chứng thư hết hạn nhưng parse được với chứng thư DER hỏng không parse được.

### Gợi ý test case

| Test | Input | Kỳ vọng |
| --- | --- | --- |
| DER chuẩn | Binary DER X.509 hợp lệ | `generateCertificate` trả `X509Certificate`. |
| PEM chuẩn | `BEGIN/END CERTIFICATE` chứa DER Base64 hợp lệ | Trả cùng certificate với DER tương ứng. |
| DER và PEM của cùng cert | Hai luồng cùng nội dung X.509 | `Arrays.equals(certDER.getEncoded(), certPEM.getEncoded()) == true`. |
| Nhiều PEM blocks | Hai hoặc nhiều CERTIFICATE liên tiếp | `generateCertificates` lấy collection phù hợp. |
| DER hỏng | Byte không thể decode thành certificate | `CertificateException`. |
| PEM thiếu marker | Base64/armor không đúng | `CertificateException` theo nhà cung cấp. |
| File không tồn tại | Path sai | `IOException`/`NoSuchFileException`, trước parse. |
| Raw public key PEM | `BEGIN PUBLIC KEY` | Không phải X.509 certificate; từ chối hoặc dùng KeyFactory phù hợp. |
| Cert hết hạn nhưng hợp lệ ASN.1 | X.509 parse được, thời gian ngoài hạn | Parse thành công; `checkValidity()` ném lỗi hạn. |
| Chứng thư CA không tin cậy | DER/PEM hợp lệ nhưng không có trust path | Parse thành công; PKIX trust validation thất bại. |
| Cert có subject đúng nhưng SAN sai | X.509 parse được | TLS hostname verification vẫn thất bại. |

**Lưu ý khi test trên provider khác:** các thông điệp `CertificateException` có thể không giống nhau; chỉ khẳng định điều contract API bảo đảm, không kiểm tra bằng cách so nguyên chuỗi lỗi Java. Chương trình `CertificateFormatsDemo` đã thử DER/PEM/bundle/malformed trên một JVM phổ biến; với môi trường production hãy chạy thêm trên JDK/provider thực tế.

## 13. Câu hỏi tự kiểm tra

**Câu 1.** Khi có file `server.der` và `server.pem` mô tả **cùng chứng thư**, Java cần hai loại CertificateFactory không?

**Đáp án:** Không. `CertificateFactory.getInstance("X.509")` và `generateCertificate(InputStream)` xử lý được DER binary và PEM certificate Base64 có marker chuẩn.

**Câu 2.** PEM chứng thư có chứa private key không?

**Đáp án:** Không, nếu khối là `BEGIN CERTIFICATE`. PEM là format text armor; nó còn có thể dùng để biểu diễn private key, CSR hoặc public key **trong các loại khối khác**, nên phải kiểm tra header/footer.

**Câu 3.** `generateCertificate` chạy thành công. Certificate có đáng tin cậy để dùng cho HTTPS không?

**Đáp án:** Chưa biết. Thành công chỉ chứng minh dữ liệu parse thành X.509. Cần validate PKIX chain đến trust anchor đã duyệt, thời gian, mục đích, SAN/hostname và các policy liên quan.

**Câu 4.** `cert.checkValidity()` chạy thành công nhưng certificate tự ký bởi kẻ lạ. Có được xác nhận trust không?

**Đáp án:** Không. `checkValidity` chỉ xét `NotBefore` và `NotAfter`; tự ký không làm một chứng thư trở thành trust anchor.

**Câu 5.** Làm sao đọc PEM có ba khối `BEGIN CERTIFICATE` thành collection?

**Đáp án:** Dùng `factory.generateCertificates(in)`. Sau đó xác định loại, số lượng, thứ tự/quan hệ chain và validate nếu mục đích yêu cầu; không chỉ lấy cert đầu tiên.

**Câu 6.** Tại sao raw file SHA-256 của PEM khác DER nhưng `SHA256(cert.getEncoded())` lại giống?

**Đáp án:** Raw PEM có marker, Base64 và xuống dòng, raw DER là binary; chúng không cùng byte file. `cert.getEncoded()` trả DER của certificate đã parse nên giống cho cùng nội dung X.509.

**Câu 7.** `X509EncodedKeySpec` có dùng để đọc nguyên một X.509 certificate không?

**Đáp án:** Không. Nó biểu diễn public key `SubjectPublicKeyInfo`. Để đọc chứng thư dùng `CertificateFactory`; để nhập raw public key dùng `KeyFactory`.

**Câu 8.** `CertificateException` có chắc chắn nghĩa là CA không được tin cậy không?

**Đáp án:** Không. Nó thường là lỗi parse/construct certificate ở factory. CA trust/PKIX validation là bước khác với các ngoại lệ/chính sách riêng.

**Câu 9.** Có được lấy first cert từ một PEM bundle rồi dùng ngay như trust anchor không?

**Đáp án:** Không. Thứ tự bundle không bảo đảm trust; trust anchors phải đến từ truststore/kênh đã được phê duyệt.

**Câu 10.** Nếu dùng `FileInputStream` trên nguồn có nhiều cert rồi gọi `generateCertificate` nhiều lần, có chắc mỗi lần đọc một cert không?

**Đáp án:** Không. API mô tả cách tiêu thụ stream khác nhau tùy `mark/reset`. Dùng `generateCertificates` hoặc `BufferedInputStream` với framing/boundary đúng.

**Câu 11.** Chứng thư parse được có thể không có SAN không?

**Đáp án:** Có. `getSubjectAlternativeNames()` có thể trả `null`. Với xác thực tên miền TLS, phải áp dụng quy tắc danh tính/giao thức và không mặc định subject CN là đủ.

**Câu 12.** Java đọc được file có đuôi `.crt` thì có nghĩa chắc chắn là PEM hay DER không?

**Đáp án:** Không. Đuôi là quy ước người đặt, không thay cho kiểm tra định dạng thực.

## 14. Tài liệu chính thức

- [Java SE 17 — CertificateFactory](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertificateFactory.html): hợp đồng hỗ trợ X.509 DER binary và Base64 PEM với marker, `generateCertificate`, `generateCertificates`, `mark/reset` và `generateCertPath`.
- [Java SE 17 — X509Certificate](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/X509Certificate.html): subject/issuer, validity, public key, SAN, KeyUsage, BasicConstraints và chữ ký chứng thư.
- [Java SE 17 — CertificateException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertificateException.html): họ ngoại lệ liên quan certificate parsing.
- [Java SE 17 — CertPathValidator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertPathValidator.html): API kiểm tra đường chứng thực, tách khỏi parsing.
- [Java SE 17 — PKIXParameters](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/PKIXParameters.html): trust anchors và policy cho PKIX.
- [Java SE 17 — TrustManagerFactory](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/TrustManagerFactory.html): tạo trust manager phục vụ TLS.
- [Oracle Java PKI Programmer's Guide](https://docs.oracle.com/en/java/javase/17/security/java-pki-programmers-guide.html): xây dựng, validate certificate path và các thuật ngữ PKI.
- [RFC 5280 — Internet X.509 PKI Certificate and CRL Profile](https://www.rfc-editor.org/rfc/rfc5280): cấu trúc X.509, phần mở rộng và PKIX certificate profile.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="x509-certificate-004">13.4 Subject, issuer, serial number, thời hạn và X500Principal</a>

<details>
<summary>Xem chi tiết</summary>

# 13.4 Subject, issuer, serial number, thời hạn và X500Principal

## Mục tiêu học tập

Sau bài này, bạn có thể:

1. Đọc và phân biệt năm thông tin lõi của chứng thư số **X.509**: **subject**, **issuer**, **serial number**, **notBefore**, **notAfter**.
2. Dùng `javax.security.auth.x500.X500Principal` đúng cách để làm việc với **Distinguished Name (DN)**, nhận biết **RDN/AVA**, các thuộc tính như `CN`, `O`, `OU`, `C` và quy tắc escape.
3. Hiểu tại sao **subject DN không đồng nghĩa hostname**, **issuer DN không chứng minh CA đáng tin**, và **issuer + serial number** là phạm vi định danh chứng thư theo chuẩn.
4. Đọc một chứng thư thật từ **PEM/DER** bằng `CertificateFactory` và `X509Certificate` trên **Java 17**, xem subject/issuer/serial/time mà không cần API nội bộ `sun.*`.
5. Dùng `checkValidity()`/`checkValidity(Date)`, hiểu lỗi **not yet valid/expired**, UTC và giới hạn của kiểm tra thời hạn.
6. Tránh nhầm lẫn giữa **serial number của chứng thư** và thuộc tính `SERIALNUMBER` **bên trong một DN**; phân biệt **self-issued**, **self-signed** và **trusted**.

## 1. X.509 certificate có những trường gì?

Chứng thư số X.509 gắn **public key** với thông tin định danh theo mô hình PKI. Một **Certificate Authority (CA)** phát hành chứng thư bằng cách ký trên phần dữ liệu cần chứng nhận; người nhận cần xác thực chữ ký, xây dựng và đánh giá chuỗi chứng thư theo chính sách tin cậy.

Cấu trúc mức khái niệm:

```text
Certificate
├── tbsCertificate (To Be Signed Certificate)
│   ├── version                 (v1/v2/v3)
│   ├── serialNumber            (số sê-ri do CA phát hành)
│   ├── signature               (thuật toán ký TBS)
│   ├── issuer                  (DN của bên phát hành)
│   ├── validity
│   │   ├── notBefore           (thời điểm bắt đầu hiệu lực)
│   │   └── notAfter            (thời điểm hết hiệu lực)
│   ├── subject                 (DN của chủ thể, có thể trống)
│   ├── subjectPublicKeyInfo    (thuật toán + public key)
│   └── extensions              (SAN, BasicConstraints, KU, EKU, ...)
├── signatureAlgorithm
└── signatureValue              (chữ ký của CA trên TBS)
```

Phạm vi chính của bài là **issuer, subject, serial number và validity**. Các extension như **Subject Alternative Name (SAN)** được nhắc đến ở mức cần thiết để tránh diễn giải sai subject DN; kiểm tra đường dẫn tin cậy, thu hồi và chính sách TLS được giới thiệu như **ranh giới**, không thay thế bài học chứng thư/PKIX riêng.

Ví dụ một chứng thư server giả định (không phải chứng thư hợp lệ để kết nối thật):

```text
Subject:     CN=api.example.test,O=Example Services,C=VN
Issuer:      CN=Example Issuing CA,O=Example PKI,C=VN
Serial:      0x34A728...              (một BigInteger)
Not Before:  2026-01-01T00:00:00Z
Not After:   2027-01-01T00:00:00Z
Public Key:  EC P-256
SAN:         DNS:api.example.test
```

**Điều quan trọng:** mỗi dòng mô tả một khía cạnh khác nhau. Subject là tên **chủ thể**, issuer là tên **bên phát hành**, serial là định danh **trong phạm vi issuer**, thời hạn chỉ là khoảng **thời gian**. Không trường nào, đứng một mình, là bằng chứng chứng thư đáng tin.

## 2. Subject: tên của chủ thể sở hữu public key

**Subject** trong `TBSCertificate` xác định thực thể gắn với public key trong `subjectPublicKeyInfo`. Có thể là tổ chức, người dùng, CA hay dịch vụ; cách diễn giải danh tính phụ thuộc loại chứng thư, CA và chính sách kiểm chứng.

Một subject DN thường thấy:

```text
CN=api.example.test,OU=Platform,O=Example Corp,L=Hanoi,C=VN
```

| Thuộc tính DN | Nghĩa gần đúng | Lưu ý |
| --- | --- | --- |
| `CN` — Common Name | Tên thông dụng của chủ thể | Không coi là tên miền TLS đã xác minh chỉ bằng `CN` |
| `O` — Organization | Tổ chức | Không mặc định CA đã xác minh ở mọi loại profile |
| `OU` — Organizational Unit | Đơn vị trong tổ chức | Có thể không hiện diện; không dùng làm quyền truy cập |
| `C` — Country | Mã quốc gia, ví dụ `VN` | Không chứng minh nơi đặt máy chủ |
| `L` / `ST` | Địa phương / bang-tỉnh | Dữ liệu định danh, không phải nguồn tin cậy tuyệt đối |
| `DC` / `UID` | Domain component / user ID | Là **attribute type**, không đồng nghĩa SAN DNS hay tên user đã xác thực |

Tên có nhiều thuộc tính gọi là **Distinguished Name** (DN). Một DN là chuỗi có cấu trúc ASN.1 `Name`, gồm những **Relative Distinguished Name (RDN)** theo thứ tự; một RDN có thể gồm **nhiều Attribute Value Assertion (AVA)**. Dấu phẩy và dấu cộng trong biểu diễn text có ý nghĩa cú pháp, không phải ký tự dữ liệu thuần túy.

Ví dụ:

```text
CN=Doe\, Jane,O=Example Lab,C=VN
```

Dấu `\,` thể hiện dấu phẩy **nằm trong giá trị CN**, không phải dấu phân cách RDN. Do đó thao tác như `subjectString.split(",")` là **sai** nếu dùng để parse DN từ bên ngoài. Giá trị còn có thể chứa dấu `+`, `=`, `#`, khoảng trắng có ý nghĩa, mã hóa Unicode/OID; cần parser DN chuẩn.

### 2.1 Subject DN có thể **rỗng**

RFC 5280 cho phép subject được biểu diễn bằng **empty sequence**, nếu thông tin nhận diện nằm trong `subjectAltName`. Khi subject rỗng, extension SAN phải có mặt và được đánh dấu **critical** theo profile. `X509Certificate.getSubjectX500Principal()` vẫn trả về một `X500Principal` hợp lệ; `getName()` có thể trả **chuỗi rỗng**.

Không viết:

```java
// SAI: giả định mọi certificate có CN, và CN luôn là hostname.
String host = cert.getSubjectX500Principal()
        .getName().split(",")[0].substring(3);
```

Đoạn trên là **phản ví dụ**, không nên đưa vào sản phẩm: subject có thể rỗng, CN có thể không ở vị trí đầu, giá trị có ký tự escape và tên TLS cần xử lý **SAN** theo profile xác minh danh tính dịch vụ.

### 2.2 Subject CN và xác minh hostname TLS

**Subject DN không tự là bằng chứng hostname hợp lệ.** Trong TLS hiện đại, nhận diện dịch vụ được biểu diễn và đối chiếu qua **Subject Alternative Name** theo chính sách (ví dụ `dNSName`, `iPAddress`).

RFC 9525 (thay thế RFC 6125) xác định rằng danh tính dịch vụ TLS không còn được lấy từ RDN `commonName` trong subject; thực hành đúng là so tên dịch vụ dự kiến với SAN phù hợp **sau khi** xác thực chứng thư theo PKI.

Trong Java, không tự so sánh `CN=...` với hostname rồi tắt hostname verification. Dùng HTTP/TLS client và cơ chế kiểm tra tên máy chủ/trust của nền tảng hoặc thư viện được cấu hình đúng. Việc subject có chữ `CN=example.com` **không đảm bảo** kết nối `example.com` đáng tin.

## 3. Issuer: tên bên đã phát hành/ký chứng thư

**Issuer** trong `TBSCertificate` là DN của CA đã phát hành và ký chứng thư. RFC 5280 yêu cầu issuer DN **không trống** đối với chứng thư phù hợp profile.

Ví dụ chuỗi:

```text
Leaf certificate:
    subject = CN=api.example.test,O=Example Services,C=VN
    issuer  = CN=Example Issuing CA,O=Example PKI,C=VN

Intermediate CA certificate:
    subject = CN=Example Issuing CA,O=Example PKI,C=VN
    issuer  = CN=Example Root CA,O=Example PKI,C=VN

Root CA certificate:
    subject = CN=Example Root CA,O=Example PKI,C=VN
    issuer  = CN=Example Root CA,O=Example PKI,C=VN
```

Khi xây chứng thư chuỗi, **name chaining** liên hệ `issuer` của chứng thư con với `subject` của chứng thư cấp trên theo quy tắc so sánh DN. Nhưng **trùng tên mới là điều kiện cần, chưa đủ**. Kẻ tấn công có thể tự tạo chứng thư mới với DN giống CA nổi tiếng; tên giống nhau không chứng minh có khóa riêng của CA hay được hệ thống tin cậy.

Một hệ thống xác minh đầy đủ còn cần:

- Xác minh **chữ ký** chứng thư con bằng **public key thật** của CA cấp trên.
- Kiểm tra **CA BasicConstraints**, `keyCertSign` (KeyUsage), path length, thuật toán/key được phép và các ràng buộc liên quan.
- Xây và xác minh đường dẫn tới **trust anchor** được cấu hình tin cậy.
- Kiểm tra **thời hạn**, **thu hồi** (CRL/OCSP theo chính sách), policy/name constraints và định danh dịch vụ.
- Không tin CA chỉ vì tên issuer giống một chuỗi được hard-code.

### 3.1 Self-issued, self-signed và trusted không đồng nghĩa

| Tính chất | Định nghĩa | Điều **không** được suy ra |
| --- | --- | --- |
| **Self-issued** | `subject` và `issuer` DN giống nhau theo quy tắc so sánh tên | Chưa chứng minh chữ ký do public key trong chứng thư tạo ra |
| **Self-signed** | Chữ ký trên chứng thư được kiểm chứng bằng public key của **chính chứng thư** | Chưa chứng minh chứng thư nằm trong trust store |
| **Trusted** | Chứng thư/chuỗi được chính sách tin cậy chấp nhận từ một trust anchor được quản lý | Không chỉ dựa vào `subject == issuer` hay `verify(publicKey)` |

Một **self-issued certificate** không nhất thiết là **self-signed** (ví dụ một số trường hợp CA đổi khóa); và một self-signed certificate dù chữ ký hợp lệ cũng **không tự động đáng tin**. Các root certificate chỉ trở thành trust anchor khi được quản trị trong trust store/chính sách chấp nhận của bên xác minh.

Trong Java:

```java
boolean selfIssued = cert.getSubjectX500Principal()
        .equals(cert.getIssuerX500Principal());
```

Đây là **đoạn code nhúng** với biến `X509Certificate cert` đã được nạp; `selfIssued` chỉ nói về **tên**, không nói về chữ ký hay trust.

## 4. Serial number: định danh chứng thư **trong phạm vi issuer**

`serialNumber` của X.509 nằm trong `TBSCertificate`. Đây là số nguyên do **CA phát hành** gán cho **mỗi chứng thư**, khác với tên subject và khác với ID người dùng.

Theo RFC 5280:

- Serial phải là **số nguyên dương** do CA cấp cho mỗi chứng thư.
- Mỗi serial phải **duy nhất trong tập chứng thư do cùng CA/issuer phát hành**. Do đó **issuer DN + serial number** xác định một chứng thư trong phạm vi PKI được mô tả.
- Các CA tuân thủ không được phát serial dài quá **20 octets** theo profile; các triển khai cần xử lý serial lớn đến giới hạn này.
- Có chứng thư không tuân thủ với serial `0` hoặc âm trong thực tế; bên đọc phải xử lý phù hợp, không crash do giả định mọi thứ hợp lệ.

### 4.1 So sánh đúng đơn vị định danh

```text
Issuer A: CN=CA-A,O=Example,C=VN
  serial 0x01  -> certificate A-1

Issuer B: CN=CA-B,O=Example,C=VN
  serial 0x01  -> certificate B-1

Hai serial bằng nhau vẫn có thể là **hai chứng thư khác nhau**.
```

Không dùng `serial` đơn độc làm primary key **toàn cầu** của mọi chứng thư. Một hệ thống PKI nghiêm túc còn phải xác minh chuỗi/issuer thực tế, vì DN có thể được sử dụng lại và dữ liệu chứng thư có thể bị giả mạo trước khi xác thực.

Trong Java, đọc serial qua:

```java
BigInteger serial = cert.getSerialNumber();
String serialHex = serial.toString(16);
```

Đây là **đoạn nhúng** (cần `java.math.BigInteger`, biến `cert` kiểu `X509Certificate`). `getSerialNumber()` trả `BigInteger`, **không phải `long`**; không dùng `longValue()` vì có thể mất bit và gây trùng sau khi ép kiểu. `toString(16)` hữu ích cho debug/tra cứu nhưng việc so khớp chứng thư nên làm trên giá trị có kiểu và phạm vi issuer đã xác thực.

Định dạng hiển thị hex không nhất thiết giữ byte padding `00` ở đầu của ASN.1 DER; cần phân biệt **giá trị số** và **cách trình bày bytes**. Đừng giả định serial là “random token”, “secret” hoặc một ID máy chủ có thể ủy quyền.

### 4.2 Hai thứ tên “serial number” rất dễ nhầm

Một DN có thể chứa thuộc tính `SERIALNUMBER` với **OID `2.5.4.5`**:

```text
Subject: CN=Device A,SERIALNUMBER=ASSET-123,O=Factory,C=VN
```

Đó là **thuộc tính text của subject DN**, có thể biểu diễn mã thiết bị/công ty theo profile cụ thể. Nó **không phải** trường `TBSCertificate.serialNumber` do CA cấp; hai giá trị có ngữ nghĩa và nơi lưu khác nhau.

| Thuộc tính | Nằm ở đâu? | Java API |
| --- | --- | --- |
| **Certificate serial number** | `TBSCertificate.serialNumber` | `cert.getSerialNumber()` → `BigInteger` |
| **DN SERIALNUMBER attribute** | Một AVA/RDN trong `subject` hoặc `issuer` | `cert.getSubjectX500Principal()` → parse DN/OID phù hợp |

Đừng trích `SERIALNUMBER=...` từ subject string để đưa vào API CRL/revocation hoặc để xây issuer+serial ID.

## 5. Validity: `notBefore` và `notAfter`

Chứng thư chứa một trường `Validity` gồm hai thời điểm:

```asn1
Validity ::= SEQUENCE {
    notBefore Time,
    notAfter  Time
}
```

Theo RFC 5280, khoảng thời gian hiệu lực bao gồm thời điểm bắt đầu và kết thúc; về mặt trực giác:

```text
notBefore <= evaluation_time <= notAfter
```

| Trường | Nghĩa | Java API |
| --- | --- | --- |
| `notBefore` | Thời điểm sớm nhất chứng thư có hiệu lực | `getNotBefore()` → `java.util.Date` |
| `notAfter` | Thời điểm cuối cùng chứng thư có hiệu lực | `getNotAfter()` → `java.util.Date` |
| Check hiện tại | Kiểm tra “lúc này” có nằm trong khoảng không | `checkValidity()` |
| Check tại thời điểm chỉ định | Kiểm tra khoảng thời gian với thời điểm truyền vào | `checkValidity(Date date)` |

Hai ngoại lệ có ý nghĩa riêng:

- `CertificateNotYetValidException`: ngày kiểm tra **trước `notBefore`**.
- `CertificateExpiredException`: ngày kiểm tra **sau `notAfter`**.

`checkValidity()` là kiểm tra khoảng thời gian theo đồng hồ runtime; `checkValidity(Date)` kiểm tra theo thời điểm do caller truyền. Nó **không** tự verify chữ ký, trust chain, hostname, KeyUsage, EKU, CRL hay OCSP.

### 5.1 UTC, UTCTime, GeneralizedTime và múi giờ

Trong ASN.1 của X.509, `Time` có thể được mã hóa dạng `UTCTime` hoặc `GeneralizedTime`. Với profile RFC 5280:

- Những năm **1950–2049** dùng `UTCTime`.
- Năm **2050 trở đi** dùng `GeneralizedTime`.
- Giá trị được biểu diễn theo **Zulu/UTC** với giây và định dạng chuẩn tương ứng.

Java trả `Date` — tức một thời điểm tuyệt đối theo UTC về mặt instant, **không phải chuỗi timezone cụ thể**. Để log/hiển thị không bị hiểu sai do timezone máy, nên đổi:

```java
Instant start = cert.getNotBefore().toInstant();
Instant end   = cert.getNotAfter().toInstant();
System.out.println(start); // ISO-8601 UTC, dạng ...Z
```

Đây là **mã nhúng** (cần `import java.time.Instant;`). `Date.toString()` phụ thuộc múi giờ hiển thị mặc định, nên hai máy có thể in các chuỗi khác nhưng vẫn chỉ **cùng một instant**.

### 5.2 Thời hạn hết không có nghĩa “đã bị thu hồi”

Tình huống:

- Chứng thư **chưa đến notBefore**: chưa có hiệu lực về thời gian.
- Chứng thư **sau notAfter**: hết hiệu lực về thời gian.
- Chứng thư **trong hạn**: **chỉ vượt qua một điều kiện thời gian**, vẫn có thể bị thu hồi, ký bằng CA không tin cậy, có tên dịch vụ sai hoặc key usage không phù hợp.

Đừng viết logic như `if (now < notAfter) allowLogin();`. Nó bỏ qua `notBefore` và mọi điều kiện PKIX còn lại. Đồng hồ hệ thống lệch thời gian cũng có thể làm check sai; cần đồng bộ clock và dùng cơ chế xác minh của nền tảng.

### 5.3 Bản ghi hết hạn và xoay vòng chứng thư

Nếu `notAfter` sắp đến, ứng dụng nên **gia hạn/thay certificate** theo chính sách, đồng thời đảm bảo server đã triển khai leaf cùng chain thích hợp. Không “sửa ngày hết hạn” của chứng thư đã ký: `notBefore/notAfter` nằm trong **TBSCertificate được CA ký**. Thay chúng sẽ làm chữ ký không hợp lệ.

Một chứng thư gia hạn có thể có **serial mới**, validity mới và đôi khi public key mới; không mặc định rằng subject giống nhau thì hai certificate là một bản ghi. Nếu yêu cầu kiểm kê chứng thư, có thể lưu certificate fingerprint cùng issuer+serial, keyId và thời điểm hiệu lực.

## 6. `X500Principal`: kiểu Java chuẩn để xử lý Distinguished Name

API `javax.security.auth.x500.X500Principal` đại diện cho **X.500 DN**, thay cho cách xử lý chuỗi tùy tiện hoặc giao diện `getSubjectDN()/getIssuerDN()` cũ.

Hai phương thức quan trọng trên `X509Certificate`:

```java
X500Principal subject = cert.getSubjectX500Principal();
X500Principal issuer  = cert.getIssuerX500Principal();
```

Đây là **mã nhúng** trong một phương thức đã có biến `cert` và import `X500Principal`.

### 6.1 Các định dạng hiển thị

`X500Principal` có:

| API | Ý nghĩa |
| --- | --- |
| `getName()` | Trả chuỗi **RFC2253** |
| `getName(X500Principal.RFC2253)` | Định dạng RFC2253 cho tích hợp có yêu cầu text DN |
| `getName(X500Principal.RFC1779)` | Biểu diễn thân thiện/kiểu RFC1779 |
| `getName(X500Principal.CANONICAL)` | Chuỗi canonical dùng cho so sánh bình đẳng của `X500Principal` |
| `getEncoded()` | Mã hóa **ASN.1 DER** của **Name**, không phải toàn bộ certificate |
| `new X500Principal(byte[])` | Parse **DER-encoded DN** |
| `equals(...)` | So sánh theo **canonical DN** do Java định nghĩa |

Quan trọng: **RFC2253 là format text của DN, không phải thuật toán tin cậy**. Nếu bạn cần kiểm tra hai DN có bằng nhau, ưu tiên `X500Principal.equals(...)` theo semantics được cung cấp, không tự `String.equalsIgnoreCase(...)` trên hai chuỗi format khác nhau.

Chuỗi canonical hữu ích cho log/tra cứu nhưng **không biến subject/issuer thành xác thực quyền truy cập**. Hai CA có cùng chuỗi DN vẫn không phải một CA đáng tin nếu chữ ký/trust khác.

### 6.2 RDN có thứ tự; giá trị có ký tự escape

Một DN có thể chứa giá trị dấu phẩy, dấu cộng hoặc ký tự quốc tế:

```text
CN=Service\, QA,O=Example Lab,C=VN
```

Nếu bạn tách theo dấu phẩy, bạn sẽ phá vỡ giá trị `CN=Service, QA` thành hai phần không hợp lệ.

Lưu ý thêm:

- DN có thể có **RDN đa giá trị** được nối bằng `+`.
- Dấu phẩy trong giá trị phải escape đúng theo biểu diễn RFC2253.
- Một số attribute type được biểu diễn bằng **OID dạng số**, không nhất thiết có keyword dễ đọc.
- `getName(CANONICAL)` đưa ra biểu diễn canonical phục vụ so sánh; đừng dựa vào thứ tự text “đẹp” để làm chính sách identity.
- Nếu phải truy vấn riêng CN/O/OU, sử dụng parser theo chuẩn DN (ví dụ thư viện LDAP/X.500 phù hợp), **không tự split** và không làm auth dựa trên CN string.

### 6.3 Thực hành 1: tạo, biểu diễn và round-trip `X500Principal`

File **`X500PrincipalNamesDemo.java`**, chạy trên Java 17+:

```java
import javax.security.auth.x500.X500Principal;

public class X500PrincipalNamesDemo {
    public static void main(String[] args) {
        X500Principal original = new X500Principal(
                "CN=Service\\, QA,O=Example Lab,C=VN");
        X500Principal fromDer = new X500Principal(original.getEncoded());
        X500Principal different = new X500Principal(
                "CN=Another Service,O=Example Lab,C=VN");

        String rfc2253 = original.getName(X500Principal.RFC2253);
        String canonical = original.getName(X500Principal.CANONICAL);
        System.out.println("RFC2253 escaped comma: " + rfc2253.contains("\\,"));
        System.out.println("DER round trip equal: " + original.equals(fromDer));
        System.out.println("canonical stable: " +
                canonical.equals(fromDer.getName(X500Principal.CANONICAL)));
        System.out.println("different DN unequal: " + !original.equals(different));
    }
}
```

Lệnh biên dịch/chạy:

```bash
javac --release 17 X500PrincipalNamesDemo.java
java X500PrincipalNamesDemo
```

Đầu ra đã kiểm tra:

```text
RFC2253 escaped comma: true
DER round trip equal: true
canonical stable: true
different DN unequal: true
```

Giải thích:

1. Java string cần viết `\\,` để mang một dấu `\` trước dấu phẩy cho bộ parser DN; nếu thiếu escape thì dấu phẩy sẽ bị hiểu là phân cách RDN.
2. `getEncoded()` tạo byte DER của **chính DN**; xây lại `new X500Principal(bytes)` giữ semantics tên.
3. `equals` trả true cho hai object đại diện cùng DN. Nó không kiểm tra chữ ký certificate hay độ tin cậy của issuer.
4. DN `CN=Another Service,...` khác về subject → không bằng bản gốc.
5. Program không “bóc riêng CN” bằng string split vì đó không phải cách xử lý cấu trúc X.500 an toàn.

**Đừng nhầm `X500Principal.getEncoded()` với `X509Certificate.getEncoded()`**: một bên là DER của **DN**, bên kia là DER của **toàn bộ chứng thư**.

## 7. Thực hành 2: đọc certificate từ PEM bằng Java 17

Một cách thực tế để học các trường này là dùng `keytool` của JDK tạo **một chứng thư lab tự ký** rồi đọc qua `CertificateFactory.getInstance("X.509")`. Ví dụ tạo key RSA 2048 cho mục đích minh họa, subject/issuer đều là `CN=localhost,O=Example Lab,C=VN`, SAN có `DNS:localhost`, thời hạn 30 ngày từ lúc phát hành. **Không dùng chứng thư lab làm certificate production hoặc root trust.**

### 7.1 Tạo certificate fixture bằng `keytool`

Trong **thư mục trống dành cho bài lab**, chạy các lệnh sau với JDK có `keytool`:

```bash
keytool -genkeypair -alias lesson -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -dname "CN=localhost,O=Example Lab,C=VN" -validity 30 -keystore sample.p12 -storetype PKCS12 -storepass changeit -keypass changeit -ext SAN=dns:localhost -noprompt

keytool -exportcert -alias lesson -keystore sample.p12 -storetype PKCS12 -storepass changeit -rfc -file sample-cert.pem
```

- `sample.p12` chứa **private key lab** và certificate; **không commit, chia sẻ hoặc triển khai** lên hệ thống thật. Password `changeit` chỉ là password thử nghiệm, **không phải mật khẩu production**.
- `sample-cert.pem` chỉ chứa **public certificate** và có thể được `CertificateFactory` đọc.
- `-rfc` tạo biểu diễn PEM kiểu `-----BEGIN CERTIFICATE-----` + Base64 của DER; công cụ Java xử lý qua `CertificateFactory("X.509")`.
- `-validity 30` chỉ để bài lab có certificate đang trong hạn tại lúc tạo; nếu bạn chạy lại chương trình sau 30 ngày, `checkValidity()` sẽ báo hết hạn đúng dự kiến.

Có thể kiểm tra trực quan bằng:

```bash
keytool -printcert -file sample-cert.pem
```

Giá trị serial và các mốc thời gian **khác nhau giữa những lần tạo**. Đừng copy serial ngẫu nhiên từ một lần chạy làm assertion ổn định của nhiều máy.

### 7.2 Java code đọc năm trường

Tạo **`X509CertificateFieldsDemo.java`**:

```java
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import javax.security.auth.x500.X500Principal;

public class X509CertificateFieldsDemo {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: java X509CertificateFieldsDemo <cert.pem>");
        }
        X509Certificate certificate;
        try (InputStream in = Files.newInputStream(Path.of(args[0]))) {
            certificate = (X509Certificate) CertificateFactory
                    .getInstance("X.509").generateCertificate(in);
        }

        X500Principal subject = certificate.getSubjectX500Principal();
        X500Principal issuer = certificate.getIssuerX500Principal();
        String serialHex = certificate.getSerialNumber().toString(16);
        Instant notBefore = certificate.getNotBefore().toInstant();
        Instant notAfter = certificate.getNotAfter().toInstant();

        certificate.checkValidity();
        System.out.println("subject: " + subject.getName(X500Principal.RFC2253));
        System.out.println("issuer: " + issuer.getName(X500Principal.RFC2253));
        System.out.println("serial positive: " +
                (certificate.getSerialNumber().signum() > 0));
        System.out.println("serial hex nonempty: " + !serialHex.isEmpty());
        System.out.println("validity ordered: " + notBefore.isBefore(notAfter));
        System.out.println("valid now: true");
        System.out.println("self-issued names: " + subject.equals(issuer));
        certificate.verify(certificate.getPublicKey());
        System.out.println("self-signature verifies: true");
        System.out.println("SAN extension present: " +
                (certificate.getSubjectAlternativeNames() != null));
    }
}
```

Biên dịch/chạy sau khi tạo file `sample-cert.pem` trong cùng thư mục:

```bash
javac --release 17 X509CertificateFieldsDemo.java
java X509CertificateFieldsDemo sample-cert.pem
```

Đầu ra đã chạy trên JDK có keytool tương thích:

```text
subject: CN=localhost,O=Example Lab,C=VN
issuer: CN=localhost,O=Example Lab,C=VN
serial positive: true
serial hex nonempty: true
validity ordered: true
valid now: true
self-issued names: true
self-signature verifies: true
SAN extension present: true
```

**Lý giải:**

1. `CertificateFactory.getInstance("X.509").generateCertificate(...)` parse chứng thư ở dạng PEM/DER; cast về `X509Certificate` để dùng API chuyên biệt. **Parse thành công chưa tạo trust**.
2. `getSubjectX500Principal` và `getIssuerX500Principal` trả các DN đúng kiểu; fixture tự ký nên subject và issuer **trùng nhau**.
3. `getSerialNumber()` đọc `BigInteger` do `keytool` gán; ví dụ chỉ kiểm số dương và hiển thị hex có dữ liệu để stdout ổn định, không in serial cụ thể.
4. `getNotBefore().toInstant()` và `getNotAfter().toInstant()` đọc hai thời điểm; chương trình kiểm tra trật tự và `checkValidity()` để xét lúc chạy.
5. `certificate.verify(certificate.getPublicKey())` **chỉ** xác minh chữ ký bằng key có trong certificate; trong fixture tự ký, kết quả thành công. **Nó không biến certificate thành trusted root**, không kiểm tra đường dẫn PKIX/hostname/revocation.
6. `getSubjectAlternativeNames()!=null` khẳng định fixture có extension SAN; **không tự xác minh localhost theo tiêu chuẩn TLS**.

Ví dụ chủ động in các assertion có giá trị ổn định thay vì in serial và mốc thời gian chính xác, vì hai trường đó thay đổi theo lúc `keytool` tạo chứng thư. Trong ứng dụng kiểm kê, bạn có thể log **serial hex và ISO timestamp** theo chính sách dữ liệu hệ thống:

```java
String serial = certificate.getSerialNumber().toString(16);
Instant validFrom = certificate.getNotBefore().toInstant();
Instant validUntil = certificate.getNotAfter().toInstant();
```

Đây là **đoạn code nhúng** trong phương thức đã có biến `certificate` kiểu `X509Certificate`, cần import `Instant`. Không dùng các giá trị này làm quyết định cho phép truy cập nếu chưa xác minh chính sách PKIX.

## 8. Thực hành 3: kiểm tra chính xác ranh giới thời hạn

Có thể dùng `checkValidity(Date)` để kiểm tra **cùng một certificate** ở nhiều thời điểm, không phụ thuộc ngày hiện tại của máy. Với fixture 30 ngày ở bước trước, ta thử:

- 1 giây **trước notBefore** → `CertificateNotYetValidException`.
- **Điểm giữa** khoảng hiệu lực → hợp lệ về thời gian.
- 1 giây **sau notAfter** → `CertificateExpiredException`.

Tạo **`X509ValidityBoundsDemo.java`**:

```java
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

public class X509ValidityBoundsDemo {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: java X509ValidityBoundsDemo <cert.pem>");
        }
        X509Certificate cert;
        try (InputStream in = Files.newInputStream(Path.of(args[0]))) {
            cert = (X509Certificate) CertificateFactory
                    .getInstance("X.509").generateCertificate(in);
        }

        Instant from = cert.getNotBefore().toInstant();
        Instant to = cert.getNotAfter().toInstant();
        Instant midpoint = from.plusMillis(Duration.between(from, to).toMillis() / 2);

        boolean earlierRejected = false;
        try {
            cert.checkValidity(Date.from(from.minusSeconds(1)));
        } catch (CertificateNotYetValidException expected) {
            earlierRejected = true;
        }
        cert.checkValidity(Date.from(midpoint));

        boolean laterRejected = false;
        try {
            cert.checkValidity(Date.from(to.plusSeconds(1)));
        } catch (CertificateExpiredException expected) {
            laterRejected = true;
        }

        System.out.println("before notBefore rejected: " + earlierRejected);
        System.out.println("midpoint accepted: true");
        System.out.println("after notAfter rejected: " + laterRejected);
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 X509ValidityBoundsDemo.java
java X509ValidityBoundsDemo sample-cert.pem
```

Đầu ra đã kiểm tra:

```text
before notBefore rejected: true
midpoint accepted: true
after notAfter rejected: true
```

Ý nghĩa:

- Không phụ thuộc timezone hiển thị vì `Instant` và `Date.from` cùng biểu diễn **thời điểm tuyệt đối**.
- Việc bắt hai exception riêng giúp phân biệt lý do về **thời hạn**.
- `midpoint` được tạo từ chính thời hạn của certificate và nằm giữa hai biên của fixture.
- Kiểm tra **tại một thời điểm giả lập** hữu ích cho unit test và test expiry, nhưng **không nên dùng tùy ý một date trong quá khứ để “vượt hạn” certificate trong kết nối TLS hiện tại**.
- Một lần `checkValidity` thành công vẫn chưa xác minh trust chain/revocation/danh tính dịch vụ.

## 9. Từ các trường X.509 đến xác minh chứng thư thật

Một sơ đồ tối giản:

```text
certificate bytes (PEM/DER)
           |
           v
CertificateFactory: parse cấu trúc X.509
           |
           v
Đọc subject / issuer / serial / validity
           |
           v
       checkValidity
           |
           v
  Chỉ biết thông tin và mốc thời gian
           |
           +--> Chưa chứng minh chữ ký của CA đúng
           +--> Chưa tìm trust anchor đáng tin
           +--> Chưa kiểm tra policy/KU/EKU/name constraints
           +--> Chưa kiểm tra hostname/SAN
           +--> Chưa đánh giá revocation
```

Để xác minh toàn bộ đường dẫn trong Java, các API liên quan gồm `CertPathValidator`, `PKIXParameters`, `TrustAnchor` và các thành phần `TrustManager` của TLS, thường do thư viện TLS/HTTPS gọi theo cấu hình. **Đừng tự xây policy xác thực từ chuỗi DN và ngày hết hạn**.

Các ranh giới quan trọng:

1. **`getIssuerX500Principal()` không lấy public key CA.** Tên issuer chỉ là một trường. Public key CA cần lấy từ chain/trust anchor đã kiểm chứng.
2. **`certificate.verify(publicKey)` không xây chain**. Nó chỉ kiểm chữ ký của chứng thư hiện tại với **key được đưa vào**; nếu bạn tự lấy key từ input không tin cậy thì có thể kiểm chữ ký đúng nhưng vẫn không trust.
3. **`checkValidity()` không kiểm tra revocation**. Chứng thư trong hạn vẫn có thể bị CA thu hồi.
4. **Subject DN không thay SAN** trong việc kiểm tên máy chủ TLS hiện đại.
5. **Issuer + serial** hữu ích cho tham chiếu trong PKI/revocation, nhưng **không thay chứng thực issuer/trust**.
6. **X500Principal.equals(...) chỉ so sánh danh tính DN** theo canonical string; nó không kiểm public key hoặc chứng thư.

### 9.1 Làm thế nào để tra chứng thư theo issuer + serial?

Trong hệ thống inventory hoặc CRL, khóa nghiệp vụ có thể gồm:

```text
(canonical issuer DN, certificate serial BigInteger)
```

Điều này phù hợp với phạm vi uniqueness serial trên từng issuer theo RFC 5280. Tuy nhiên, với cơ sở dữ liệu đa PKI, nên kết hợp với **issuer key identifier hoặc certificate fingerprint** khi cần giảm nhầm lẫn giữa CA có tên giống nhau; việc chấp nhận chứng thư vẫn phải dựa trên chain và policy.

Nếu chỉ có chuỗi biểu diễn RFC1779/2253, đừng tự so sánh bằng `String.equalsIgnoreCase`. Dùng `X500Principal` chuẩn và key/chain metadata theo giao thức.

### 9.2 Phân biệt certificate serial với certificate fingerprint

- **Serial number** do CA gán và có uniqueness **trong phạm vi issuer**.
- **Fingerprint** (ví dụ SHA-256 trên toàn bộ DER certificate) là digest của **chính bytes certificate**. Nó hữu ích cho nhận diện/đối chiếu file hoặc pinning theo chính sách, không phải serial.
- Một certificate được parse từ PEM hay DER có cùng bytes DER nội bộ (`getEncoded()`), nên fingerprint DER đúng cách không phụ thuộc whitespace của phần bọc PEM.
- Fingerprint không mặc nhiên là trust anchor: chấp nhận một fingerprint là **quyết định chính sách** và cần quy trình cập nhật/rotation.

## 10. Sai lầm thường gặp và cách sửa

| Sai lầm | Vì sao sai? | Cách đúng |
| --- | --- | --- |
| “Issuer có tên CA nổi tiếng nên trusted.” | Tên có thể bị giả mạo; chưa kiểm chữ ký/chain. | Dùng PKIX/trust store. |
| “`subject.equals(issuer)` = certificate trusted.” | Đây chỉ là **self-issued** theo tên. | Phân biệt self-issued, self-signed, trust anchor. |
| “CN chính là DNS hostname.” | TLS hiện đại so danh tính qua SAN, không CN fallback. | Dùng TLS hostname verification theo RFC 9525. |
| “Serial toàn thế giới là duy nhất.” | Serial duy nhất **theo CA/issuer**. | Kết hợp issuer + serial và trust metadata. |
| “Serial nằm trong subject `SERIALNUMBER=...`.” | Đây là **DN attribute khác**. | `getSerialNumber()` cho certificate serial. |
| “Serial nhỏ, cast `longValue()` cho tiện.” | Có thể mất bit/trùng. | Giữ `BigInteger`. |
| “Mọi subject đều có CN.” | Subject có thể rỗng, SAN có thể mang toàn bộ định danh. | Dùng `getSubjectX500Principal`, xử lý SAN. |
| “Tách DN bằng dấu phẩy.” | Dấu phẩy có thể được escape trong giá trị. | Dùng parser X.500/DN. |
| “So sánh DN text theo ký tự.” | Các format, escape/canonical khác nhau. | Dùng `X500Principal.equals` đúng semantics. |
| “Hết hạn chỉ cần `notAfter`.” | Còn `notBefore` và nhiều yêu cầu PKIX khác. | Dùng `checkValidity` + path validation. |
| “`checkValidity()` = certificate hợp lệ toàn diện.” | Chỉ xét mốc thời gian. | Kiểm chữ ký/chain/trust/policy/revocation/identity. |
| “`Date.toString()` in giờ địa phương là ngày chuẩn trên chứng thư.” | Timezone hiển thị thay đổi, instant không đổi. | Dùng `toInstant()` và ISO-8601 UTC. |
| “Certificate tự ký bằng key đúng là root đáng tin.” | Chứng minh tự ký không đồng nghĩa trust. | Chỉ chấp nhận trust anchor được quản lý. |
| “Có thể sửa ngày/subject trong chứng thư DER.” | Những trường này được CA ký trong TBS. | Phát hành/renew chứng thư mới. |

## 11. Quy trình chẩn đoán trường hợp “certificate rejected”

Một log từ Java ghi “certificate expired”, “PKIX path building failed”, “No subject alternative names matching” có thể xuất phát từ **những lớp kiểm tra khác nhau**. Để phân tích:

1. **Parse certificate**: `CertificateFactory` có đọc đúng PEM/DER không? Có nhầm file private key với certificate không?
2. **Subject/issuer**: in DN bằng `getSubjectX500Principal().getName(RFC2253)` và tương ứng issuer; không xem tên là trust.
3. **Serial**: lấy `BigInteger`/hex để tra CA/CRL hoặc inventory, không trộn với `SERIALNUMBER` thuộc DN.
4. **Validity**: kiểm `notBefore`, `notAfter` bằng UTC; xem đồng hồ máy/server, NTP và thời điểm đang kiểm.
5. **Signature/chain**: chuỗi leaf → intermediate → trust anchor có đủ, issuer DN có nối đúng và chữ ký được verify bằng key CA chính xác không?
6. **Policy**: BasicConstraints, KeyUsage, ExtendedKeyUsage, name constraints và thuật toán có đáp ứng ứng dụng không?
7. **Revocation**: CRL/OCSP được cấu hình và có trạng thái phù hợp theo chính sách không?
8. **TLS service identity**: SAN có DNS/IP đúng tên dịch vụ truy cập không? Có tắt hostname verification không?
9. **Nếu dùng mã tùy chỉnh**: xác minh rằng nó **không** chỉ so chuỗi subject, thời hạn hay serial rồi bypass trust manager.

Không ghi private key, token hay nội dung bí mật vào log. Public certificate thường không bí mật, nhưng vẫn có thể chứa thông tin định danh; hãy áp dụng chính sách logging/quyền truy cập của tổ chức.

## 12. Bài tập tự kiểm tra

**Câu 1.** Subject và issuer khác nhau thế nào?  
**Trả lời:** Subject xác định chủ thể gắn với public key; issuer xác định thực thể CA đã phát hành/ký certificate.

**Câu 2.** `CN=example.com` trong subject có đủ để TLS Java client tin `https://example.com` không?  
**Trả lời:** Không. Cần kiểm tra trust chain/policy và định danh dịch vụ qua SAN theo cơ chế TLS hiện đại.

**Câu 3.** Hai CA khác nhau có thể cấp cùng serial number `42` không?  
**Trả lời:** Có. Serial là duy nhất **trong phạm vi mỗi issuer/CA**, không trên toàn Internet.

**Câu 4.** `SERIALNUMBER=ASSET-123` của subject DN có giống `getSerialNumber()` không?  
**Trả lời:** Không. Một bên là AVA/OID trong DN, bên kia là số serial của chứng thư do CA gán.

**Câu 5.** Khi gặp `CertificateNotYetValidException` và `CertificateExpiredException`, chúng khác gì?  
**Trả lời:** Thời điểm kiểm trước `notBefore` versus sau `notAfter`.

**Câu 6.** `cert.checkValidity()` trả về bình thường có chứng minh certificate trusted không?  
**Trả lời:** Không; mới kiểm tra khoảng thời gian hiệu lực của certificate tại thời điểm hiện tại.

**Câu 7.** Vì sao `X500Principal` tốt hơn `String.split(",")`?  
**Trả lời:** DN có cú pháp ASN.1/RDN, dấu phẩy escape, RDN đa giá trị, OID và canonical form; split chuỗi thô không hiểu cấu trúc.

**Câu 8.** Certificate có `subject == issuer` thì là gì?  
**Trả lời:** **Self-issued** về tên. Cần verify chữ ký riêng để xác định self-signed, và vẫn phải có trust policy.

**Câu 9.** `getNotAfter()` trả `Date` theo múi giờ Việt Nam hay UTC?  
**Trả lời:** Nó đại diện cho một **instant tuyệt đối**; dạng hiển thị `Date.toString()` phụ thuộc timezone máy. Chuyển `toInstant()` để hiển thị UTC rõ ràng.

**Câu 10.** Vì sao subject DN có thể trống?  
**Trả lời:** RFC 5280 cho phép chủ thể được nhận diện qua SAN; khi subject DN rỗng, SAN phải có mặt và được đánh dấu critical.

## Tổng kết

```text
X509Certificate
  ├─ getSubjectX500Principal() -> subject DN
  ├─ getIssuerX500Principal()  -> issuer DN
  ├─ getSerialNumber()         -> BigInteger (duy nhất theo issuer)
  ├─ getNotBefore()            -> Date -> Instant
  ├─ getNotAfter()             -> Date -> Instant
  └─ checkValidity([Date])     -> chỉ xét khoảng thời gian

X500Principal
  ├─ getName(RFC2253/CANONICAL) -> biểu diễn DN
  ├─ getEncoded()               -> DER của Name
  └─ equals(...)                -> so sánh DN canonical

Issuer + serial + validity + subject
  = thông tin cần thiết về chứng thư
  ≠ xác minh hoàn chỉnh chữ ký/PKIX/hostname/revocation
```

Ghi nhớ quan trọng nhất: **subject** là tên chủ thể, **issuer** là tên bên phát hành, **serial** có tính duy nhất **theo CA**, **notBefore/notAfter** xác định cửa sổ thời gian; `X500Principal` xử lý DN có cấu trúc. Khi quyết định có **tin** chứng thư hay không, phải dùng **PKIX và chính sách xác thực dịch vụ** chứ không dựa vào các trường mô tả độc lập.

## Tài liệu tham khảo chính thức

- [Java 17 — X509Certificate API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/X509Certificate.html): subject, issuer, serial, validity, SAN và `checkValidity`.
- [Java 17 — X500Principal API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/x500/X500Principal.html): DN, RFC2253/RFC1779/CANONICAL, DER và `equals`.
- [Java 17 — CertificateFactory API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertificateFactory.html): đọc certificate PEM/DER.
- [RFC 5280 `4.1.2.2–`4.1.2.6](https://www.rfc-editor.org/rfc/rfc5280#section-4.1.2.2): serial number, issuer, validity, subject và quy tắc SAN rỗng/critical.
- [RFC 9525 — Service Identity in TLS](https://www.rfc-editor.org/rfc/rfc9525): định danh dịch vụ TLS dựa trên SAN, thay thế RFC 6125.
- [Java 17 — CertPathValidator API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertPathValidator.html): kiểm tra certification path thay vì chỉ nhìn DN/Date.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="x509-certificate-005">13.5 Subject Alternative Name và danh tính DNS hoặc địa chỉ IP</a>

<details>
<summary>Xem chi tiết</summary>

# 13.5. Subject Alternative Name và danh tính DNS hoặc địa chỉ IP

## 1. SAN giải quyết vấn đề gì trong chứng thư X.509?

Khi một HTTPS client kết nối tới `https://api.example.test`, việc máy chủ trình ra **một chứng thư X.509 có chữ ký hợp lệ** chưa đủ. Client cần biết chứng thư đó có được cấp cho **đúng máy chủ mà client định truy cập** hay không. Nếu chỉ xác thực đường dẫn chứng thư (*certificate path*) đến một CA tin cậy nhưng bỏ qua **danh tính endpoint**, kẻ tấn công có thể dùng một chứng thư hợp lệ cấp cho tên **khác** để đánh lừa client.

**Subject Alternative Name (SAN)** là extension của chứng thư X.509, có OID `2.5.29.17`, dùng để chứa một hoặc nhiều định danh có kiểu rõ ràng: tên DNS, địa chỉ IP, địa chỉ email, URI và một số dạng khác. Đối với danh tính máy chủ TLS/HTTPS, hai kiểu rất quan trọng là:

- **dNSName (GeneralName type 2):** tên DNS, ví dụ `api.example.test` hoặc `*.svc.example.test`.
- **iPAddress (GeneralName type 7):** địa chỉ IPv4/IPv6 dạng **octet nhị phân** trong extension, ví dụ IPv4 `192.0.2.10` hoặc IPv6 `2001:db8::10`.

Trong Java, `java.security.cert.X509Certificate.getSubjectAlternativeNames()` cho phép **đọc** các SAN của chứng thư, nhưng **không tự thực hiện xác minh danh tính máy chủ**. Để bảo vệ kết nối thực tế, phải có đồng thời:

1. **Tin cậy chứng thư:** xác minh certificate path, CA/trust anchor, hiệu lực, key usage/EKU và các chính sách cần thiết.
2. **Khớp endpoint identity:** so hostname/IP **mà client muốn truy cập** với SAN đúng kiểu, theo quy tắc giao thức.
3. **Handshake và chính sách TLS:** kết nối thật sự dùng private key tương ứng với chứng thư được chấp nhận, các tham số TLS an toàn và không vô hiệu hóa các bước kiểm tra trên.

Bài này làm rõ vì sao **DNS SAN không thay thế IP SAN**, vì sao không nên dựa vào `Common Name (CN)`, cách đọc SAN bằng Java 17+, và cách yêu cầu JSSE/HTTP client kiểm tra danh tính đúng thay vì tự viết bộ so khớp tên đơn giản.

## 2. Cấu trúc SubjectAltName trong X.509

Theo RFC 5280, extension gồm chuỗi `GeneralName`; mỗi phần tử có **type** và **value** riêng:

```text
X.509 Certificate
├─ Subject Distinguished Name (DN)
│  └─ CN=legacy.example.test                 <-- thuộc subject, KHÔNG phải SAN
├─ Subject Public Key Info (SPKI)
├─ Issuer, serial, validity, signature...
└─ Extensions
   └─ subjectAltName (OID 2.5.29.17)
      ├─ [2] dNSName: api.example.test
      ├─ [2] dNSName: *.svc.example.test
      ├─ [7] iPAddress: 192.0.2.10
      └─ [7] iPAddress: 2001:db8::10
```

Đây là dữ liệu của **một chứng thư thử nghiệm**, không phải chứng thư của máy chủ công khai thật. Miền `.test`, IPv4 `192.0.2.0/24` và IPv6 `2001:db8::/32` được dùng cho ví dụ/tài liệu.

Một chứng thư có thể chứa **nhiều SAN cùng loại** và nhiều loại khác nhau. Việc có hai dNSName không có nghĩa một tên “ghi đè” tên kia: tùy chính sách, bất kỳ dNSName hợp lệ nào khớp với tên được mong đợi đều có thể được chấp nhận. Các chứng thư dùng chung cho nhiều hostname phải được cấp phát, kiểm soát và xoay vòng cẩn thận vì sự cố private key có thể ảnh hưởng đến tất cả các tên trên chứng thư.

### Bảng các GeneralName thường gặp

| Type trong `getSubjectAlternativeNames()` | Tên ASN.1 | Mục đích và cách hiểu |
| --- | --- | --- |
| `1` | `rfc822Name` | Địa chỉ email. Không phải DNS-ID thay thế cho HTTPS. |
| `2` | `dNSName` | Danh tính dựa trên DNS, dùng khi client truy cập theo tên miền. |
| `6` | `uniformResourceIdentifier` | URI identity ở giao thức quy định; không mặc nhiên thay thế dNSName của HTTPS. |
| `7` | `iPAddress` | Địa chỉ IPv4/IPv6, dùng khi reference identity là IP literal theo quy tắc ứng dụng. |
| `0`, `3`, `4`, `5`, `8` | OtherName, X.400, directoryName, EDI, registeredID | Các định danh có cấu trúc khác, chỉ có ý nghĩa theo giao thức và chính sách tương ứng. |

**SubjectAltName khác IssuerAlternativeName.** SAN mô tả danh tính liên quan đến **subject** của chứng thư; extension `IssuerAlternativeName` mô tả các định danh bổ sung của **issuer**. Khi đối chiếu server hostname, không tìm DNS SAN trong IssuerAlternativeName.

## 3. DNS-ID: khi client truy cập bằng hostname

Nếu ứng dụng gọi `https://api.example.test/orders`, reference identity thường là **hostname `api.example.test` từ đích kết nối đã được ứng dụng lựa chọn**, không phải chuỗi do certificate tự gợi ý, do reverse DNS ngẫu nhiên tạo ra hoặc do một HTTP header kẻ tấn công điều khiển.

Ví dụ chứng thư có:

```text
subjectAltName:
  DNS:api.example.test
  DNS:*.svc.example.test
  IP Address:192.0.2.10
```

Theo quy tắc DNS-ID hiện đại, chứng thư có thể dùng cho `api.example.test`; wildcard `*.svc.example.test` có thể khớp với **một nhãn** như `a.svc.example.test` nhưng không được hiểu là khớp mọi cấp sâu tùy ý.

### 3.1. Tên DNS được so khớp theo tên, không theo IP DNS trả về

Ví dụ DNS trả về:

```text
api.example.test  --DNS A-->  192.0.2.10
```

Client truy cập bằng URL `https://api.example.test` sẽ kiểm tra **DNS-ID cho `api.example.test`**, dù kết nối TCP tới một địa chỉ IP sau khi phân giải. Điều này là cách bình thường khi nhiều virtual host được phục vụ trên cùng IP.

Không được lấy kết quả DNS A/AAAA, rồi tự đổi yêu cầu sang IP-ID nếu người dùng thực ra đã chỉ định tên DNS. Danh tính mong đợi phải xuất phát từ thông tin đích được tin cậy trong ứng dụng/giao thức.

### 3.2. So khớp wildcard không phải String.endsWith()

Theo RFC 9525, wildcard trong DNS-ID chỉ được chấp nhận dưới dạng **nguyên nhãn nằm ngoài cùng bên trái**. Với mẫu `*.svc.example.test`:

| Reference hostname | Khớp wildcard đó? | Vì sao |
| --- | --- | --- |
| `a.svc.example.test` | Có, theo quy tắc wildcard một nhãn | `*` thay cho nhãn `a` |
| `b.svc.example.test` | Có | Một nhãn hợp lệ |
| `svc.example.test` | Không | Thiếu nhãn bên trái |
| `a.b.svc.example.test` | Không | Có **hai** nhãn bên trái |
| `not-svc.example.test` | Không | Không cùng cấu trúc nhãn |
| `192.0.2.10` | Không | IP-ID và DNS-ID là hai kiểu khác nhau |

Các chuỗi như `a*.example.test`, `*.*.example.test` hoặc dùng `*` như phép so khớp tùy ý không đáp ứng quy tắc chuẩn này. Kiểm tra còn phụ thuộc yêu cầu giao thức/ứng dụng và chính sách xử lý wildcard; **đừng tự triển khai** dựa vào `replace("*", ".*")`, regex tự chế hay kiểm tra suffix mà không hiểu chuẩn.

### 3.3. Chữ hoa, IDN và định dạng tên miền

Tên DNS được so khớp theo quy tắc DNS/IDNA thích hợp, không phải đơn giản so byte UTF-8 của text hiển thị. Các domain quốc tế hóa thường phải được biểu diễn bằng **ASCII A-label/Punycode theo IDNA** trong dNSName, thay vì bỏ trực tiếp Unicode U-label vào extension IA5String.

Ứng dụng cần dùng thư viện TLS/hostname verification và quy tắc hostname normalization được profile của giao thức quy định. Không tự gọi `toLowerCase()` theo locale rồi so suffix, không bỏ qua ký tự đặc biệt hoặc dấu chấm một cách tùy tiện, và không cho phép client chuyển danh tính mong đợi qua input không tin cậy.

## 4. IP-ID: khi client truy cập bằng địa chỉ IP

Nếu URL/endpoint được chỉ định trực tiếp là `https://192.0.2.10` hoặc `https://[2001:db8::10]`, reference identity là **IP address**, không phải DNS hostname. Theo RFC 9525, IP-ID so khớp **chính xác các octet của địa chỉ IP** với trường `iPAddress` nằm trong SAN.

Trong ASN.1 X.509:

- `iPAddress` IPv4 được mã hóa thành **4 octet**.
- `iPAddress` IPv6 được mã hóa thành **16 octet**.
- Không phải văn bản `"192.0.2.10"` được nhét vào `dNSName` rồi coi là IP-ID.
- Các cách biểu diễn text IPv6 khác nhau có thể trỏ tới **cùng 16 octet**; parser Java có thể trả lại chuỗi IP ở dạng mở rộng thay vì dạng nén ban đầu.

Ví dụ:

```text
DNS SAN: api.example.test
IP SAN: 192.0.2.10

Client A: https://api.example.test  -> cần DNS-ID api.example.test
Client B: https://192.0.2.10        -> cần IP-ID 192.0.2.10
Client C: https://192.0.2.11        -> IP không khớp, phải từ chối
```

**Sai lầm phổ biến:** chứng thư có `DNS:192.0.2.10` nhưng không có `IP Address:192.0.2.10`. Đó là một dNSName chứa các ký tự trông giống địa chỉ IP, **không phải** SAN iPAddress đúng kiểu. Không cố “cứu” bằng cách so text giữa hai type.

**Cũng sai:** khi kết nối tới `192.0.2.10`, thực hiện reverse DNS rồi cho phép bất kỳ hostname nhận được khớp với DNS SAN. Việc so IP-ID cần dựa vào IP reference được chọn, không bị thay bằng tên từ reverse DNS không đáng tin.

Nếu ứng dụng cần truy cập backend theo IP nhưng chứng thư được cấp cho DNS hostname, hãy cấu hình **hostname chuẩn được tin cậy** và kết nối theo mô hình/giao thức đúng (ví dụ kết nối TCP tới IP được chỉ định nhưng TLS endpoint identity và SNI được cấu hình cho DNS tên đã biết). Không tắt hostname verification để chữa lỗi chứng thư.

## 5. Common Name (CN) có còn được dùng làm danh tính server không?

Trước đây nhiều triển khai kiểm tra CN trong `Subject DN` khi chứng thư không chứa dNSName SAN. Thực hành này có thể gây nhầm lẫn vì CN không có kiểu ràng buộc đủ rõ như SAN, và chính sách fallback từng khác nhau giữa client/provider.

**RFC 9525 (2023), thay thế RFC 6125**, quy định **không sử dụng Common Name RDN để nhận diện dịch vụ**; server identity được biểu diễn trong các subjectAltName phù hợp. Vì thế với thiết kế mới:

- Cấp chứng thư HTTPS có **SAN dNSName và/hoặc iPAddress đúng kiểu** cho mọi endpoint được cho phép.
- **Không dựa** vào `CN` để hợp thức hóa hostname/IP khi SAN không phù hợp.
- Không dựa vào việc một client cũ có thể fallback sang CN như bằng chứng sản phẩm sẽ hoạt động an toàn trên các JDK/client khác.
- `Subject DN` vẫn có vai trò metadata của chứng thư theo PKI; câu này chỉ nói đến cách xác minh **service endpoint identity**, không nói bỏ mọi thông tin Subject.

Bộ fixture trong phần Java cố ý tạo `CN=legacy.example.test` nhưng **không** đưa tên đó vào DNS SAN. Chương trình sẽ chứng minh việc đọc SAN **không tự thêm CN**; khi dùng TLS client theo policy hiện đại, không được xem CN là một danh tính thay thế nếu không có DNS-ID đúng.

## 6. SAN matching khác với xác minh chứng thư đáng tin

Những thao tác sau có vai trò khác nhau:

| Thao tác | Nó kiểm tra gì? | Điều **chưa** được chứng minh |
| --- | --- | --- |
| `CertificateFactory.generateCertificate(...)` | Parse DER/PEM thành `Certificate` | Không chứng minh CA, trust path, hiệu lực hoặc hostname đã được chấp nhận |
| `X509Certificate.getSubjectAlternativeNames()` | Liệt kê subject SAN được mã hóa trong chứng thư | Không thực hiện hostname verification |
| `X509Certificate.getSubjectX500Principal()` | Trả Subject DN như X.500 principal | CN không phải DNS-ID/IP-ID hợp lệ theo RFC 9525 |
| `X509Certificate.checkValidity()` | Kiểm tra `notBefore`/`notAfter` theo thời gian | Không xác thực chuỗi chứng thư hoặc hostname |
| `X509Certificate.verify(issuerPublicKey)` | Kiểm tra chữ ký chứng thư với public key đã đưa vào | Không tự xây trust path hoặc xác minh tên máy chủ |
| `CertPathValidator` với PKIX policy | Xác minh đường dẫn chứng thư theo tham số/trust anchor được cung cấp | Không mặc nhiên so hostname URL với SAN |
| JSSE/HTTP TLS client cấu hình đúng | Handshake, trust manager và endpoint identification | Không thay thế business authorization/identity mapping cấp ứng dụng |

**Phải có đủ cả hai điều kiện**: certificate chain hợp lệ theo trust policy **và** SAN khớp danh tính endpoint. Nếu chứng thư tự ký chứa `DNS:api.example.test` nhưng không được một trust anchor được cấu hình tin cậy, vẫn phải bị từ chối. Ngược lại, nếu chứng thư của CA tin cậy có SAN cho `other.example.test` nhưng client đang kết nối `api.example.test`, cũng phải bị từ chối.

SAN extension **không phải chữ ký riêng**: nó nằm trong nội dung chứng thư được issuer ký. Tuy nhiên một extension SAN chứa text đúng **không tự tạo ra trust**; client phải xác minh toàn bộ chứng thư theo PKI và biết mình đang yêu cầu tên nào.

### 6.1. Chain, thời hạn, KeyUsage, EKU và Name Constraints

Ngoài SAN, hệ thống production thường cần kiểm tra:

- Chữ ký/certificate path dẫn về trust anchor được chấp nhận; CA có thẩm quyền cấp phát chứng thư tương ứng.
- `notBefore`/`notAfter` và chính sách về thời gian, certificate revocation khi profile yêu cầu.
- `BasicConstraints`, `KeyUsage` và `ExtendedKeyUsage` phù hợp với vai trò server TLS (ví dụ EKU `serverAuth` khi được quy định).
- `NameConstraints` trên CA/intermediate certificate nếu có, có thể giới hạn các subtree DNS/IP được phép.
- Algorithm policy, độ mạnh khóa, signature algorithm, key rotation và pinning nếu áp dụng.
- Kết nối mạng đến đúng endpoint và ứng dụng không bị chuyển hướng sang một origin khác mà bỏ kiểm tra.

Không cố tái hiện tất cả các kiểm tra PKIX/TLS ở lớp ứng dụng chỉ với `X509Certificate` getter. Hãy tận dụng JSSE/HTTP client và truststore hợp lệ, hoặc một TLS implementation đã được kiểm toán.

## 7. Java 17+: đọc SAN thật từ một chứng thư X.509

Trong JCA, `X509Certificate.getSubjectAlternativeNames()` trả `Collection<List<?>>` hoặc `null` nếu chứng thư không có extension SAN. Mỗi phần tử là một list hai phần:

1. `Integer` biểu diễn GeneralName type (0–8).
2. Giá trị thường là `String` cho các type như DNS `2`, IPv4/IPv6 `7`, URI `6` hoặc byte[] DER với một số type khác.

Đặc biệt, với type `7` Java trả chuỗi IP ở định dạng có thể hiển thị, **không nhất thiết giữ nguyên text IPv6 nén**. Ví dụ dùng `InetAddress` để so octet của hai **IP literal đã biết**, chỉ phục vụ khám phá dữ liệu SAN; đây **không phải** thuật toán hostname verification cho HTTPS.

### 7.1. Chứng thư thử nghiệm nhúng trong mã

Để ví dụ chạy độc lập mà không phụ thuộc máy chủ mạng, chương trình nhúng **một chứng thư self-signed công khai dành riêng cho demo** dưới dạng PEM. Fixture có:

```text
Subject DN: CN=legacy.example.test, OU=Testing, O=Demo, C=US
SAN:
  DNS:api.example.test
  DNS:*.svc.example.test
  IP Address:192.0.2.10
  IP Address:2001:db8::10
```

**Không có private key trong source**. Đây không phải chứng thư server hợp lệ trên Internet: self-signed và sử dụng tên/địa chỉ tài liệu. Chương trình **chỉ parse** chứng thư và in các SAN. Nó **không** kiểm tra path đến CA tin cậy, không tạo kết nối TLS và không chứng minh tên máy chủ được chấp nhận.

Tạo tệp `SanCertificateDemo.java`:

```java
import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public final class SanCertificateDemo {
    // Public, self-signed test certificate; no private key is embedded.
    private static final String CERT_PEM = """
            -----BEGIN CERTIFICATE-----
            MIIB+DCCAZ+gAwIBAgIJAJ14rTcMr16LMAoGCCqGSM49BAMDMEwxCzAJBgNVBAYT
            AlVTMQ0wCwYDVQQKEwREZW1vMRAwDgYDVQQLEwdUZXN0aW5nMRwwGgYDVQQDExNs
            ZWdhY3kuZXhhbXBsZS50ZXN0MB4XDTI2MTAzMTEzMjQxMVoXDTI5MDcyNzEzMjQx
            MVowTDELMAkGA1UEBhMCVVMxDTALBgNVBAoTBERlbW8xEDAOBgNVBAsTB1Rlc3Rp
            bmcxHDAaBgNVBAMTE2xlZ2FjeS5leGFtcGxlLnRlc3QwWTATBgcqhkjOPQIBBggq
            hkjOPQMBBwNCAARGSmwZAOpA+2rAEjrl4hYnRfXTYl5cG2hOHfsJs1fPt1B/fxv0
            Saplz7XtuMHGX7uCY+ftWgQlA18JsxMk4oQto2owaDAdBgNVHQ4EFgQU4oNvse+X
            n0vCSxlfkgX1Lp3Tg9cwRwYDVR0RBEAwPoIQYXBpLmV4YW1wbGUudGVzdIISKi5z
            dmMuZXhhbXBsZS50ZXN0hwTAAAIKhxAgAQ24AAAAAAAAAAAAAAAQMAoGCCqGSM49
            BAMDA0cAMEQCIEpTPuvkpgvpAdaxnl2kpOek3cBS4Jl+/xOBcTpBj/NqAiBgfrDl
            ZfNneRagyWvbB3UzpTea0ddr9XAePvhjG/e0BQ==
            -----END CERTIFICATE-----
            """;

    private static boolean hasIp(List<String> ipSans, String literal)
            throws Exception {
        byte[] expected = InetAddress.getByName(literal).getAddress();
        for (String ipSan : ipSans) {
            byte[] actual = InetAddress.getByName(ipSan).getAddress();
            if (Arrays.equals(expected, actual)) return true;
        }
        return false;
    }

    public static void main(String[] args) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        byte[] pem = CERT_PEM.getBytes(StandardCharsets.US_ASCII);
        X509Certificate certificate = (X509Certificate) factory
                .generateCertificate(new ByteArrayInputStream(pem));

        Collection<List<?>> sans = certificate.getSubjectAlternativeNames();
        List<String> dnsSans = new ArrayList<>();
        List<String> ipSans = new ArrayList<>();
        if (sans != null) {
            for (List<?> name : sans) {
                int type = ((Integer) name.get(0)).intValue();
                Object value = name.get(1);
                if (type == 2 && value instanceof String dns) dnsSans.add(dns);
                if (type == 7 && value instanceof String ip) ipSans.add(ip);
            }
        }

        System.out.println("Certificate type: " + certificate.getType());
        System.out.println("DNS SANs: " + dnsSans);
        System.out.println("IPv4 SAN present: " + hasIp(ipSans, "192.0.2.10"));
        System.out.println("IPv6 SAN present: " + hasIp(ipSans, "2001:db8::10"));
        System.out.println("Legacy CN in DNS SAN: " +
                dnsSans.contains("legacy.example.test"));
        System.out.println("DNS SAN count: " + dnsSans.size());
        System.out.println("IP SAN count: " + ipSans.size());
    }
}
```

Biên dịch và chạy với JDK 17 hoặc mới hơn:

```bash
javac SanCertificateDemo.java
java SanCertificateDemo
```

Stdout chính xác:

```text
Certificate type: X.509
DNS SANs: [api.example.test, *.svc.example.test]
IPv4 SAN present: true
IPv6 SAN present: true
Legacy CN in DNS SAN: false
DNS SAN count: 2
IP SAN count: 2
```

### 7.2. Đọc đúng kết quả

- `CertificateFactory` đã parse một chứng thư dạng X.509 — **chưa có trust decision**.
- Hai giá trị type `2` của SAN đúng thứ tự trong fixture là `api.example.test` và `*.svc.example.test`.
- Hai giá trị type `7` chứa IPv4 và IPv6. Hàm `hasIp` chuyển **IP literal** sang octet để kiểm tra sự hiện diện, do text IPv6 có thể được định dạng khác nhau dù giá trị nhị phân giống nhau.
- Tên `legacy.example.test` nằm trong **CN**, không nằm trong DNS SAN, nên kiểm tra `dnsSans.contains(...)` trả `false`.
- `getSubjectAlternativeNames()` có thể là `null` nếu không có SAN; code xử lý tình huống đó bằng danh sách rỗng. Một ứng dụng hostname verification hiện đại nên từ chối server identity không có SAN phù hợp thay vì tự lấy CN thay thế.
- Việc dữ liệu SAN được liệt kê **không nghĩa là** chương trình đã xác minh wildcard, chain, hạn dùng, key usage hoặc quyền điều khiển domain.

**Đây là demo đọc metadata, không phải hàm kiểm tra hostname.** Đặc biệt không copy `hasIp` hoặc `dnsSans.contains` làm bộ xác minh TLS tự viết: các phép kiểm này bỏ qua trust path, chính sách reference identity, wildcard, IDNA, certificate constraints và nhiều yếu tố bảo mật khác.

## 8. Để Java thực sự xác minh hostname/IP khi kết nối TLS

### 8.1. Ưu tiên HTTP client cấu hình an toàn mặc định

Với HTTPS, các API như `java.net.http.HttpClient` hoặc `HttpsURLConnection` có cơ chế TLS và xác minh server identity theo cấu hình mặc định thích hợp. Khi xây ứng dụng, hãy để hostname từ **URI/endpoint mong đợi** tham gia xác minh chứng thư, dùng truststore tin cậy và không vô hiệu hóa việc kiểm tra tên.

Ví dụ **đoạn mã minh họa**, không phải chương trình độc lập với fixture tự ký:

```java
// Chỉ gửi tới endpoint thật với chứng thư hợp lệ theo trust policy.
HttpClient client = HttpClient.newHttpClient();
HttpRequest request = HttpRequest.newBuilder(
        URI.create("https://api.example.test/orders"))
        .GET()
        .build();
HttpResponse<String> response = client.send(
        request, HttpResponse.BodyHandlers.ofString());
```

Trong code thực cần import `java.net.URI` và `java.net.http.HttpClient`, `HttpRequest`, `HttpResponse`. Host `api.example.test` ở đây **chỉ là ví dụ**, không phải tên một server công khai có sẵn. Đừng thực thi snippet này rồi diễn giải lỗi DNS hoặc lỗi trust là lỗi của SAN parser phía trên.

**Không** đặt trust manager “accept all”, không cài `HostnameVerifier` luôn trả true và không cố tắt endpoint identification để “sửa lỗi SAN”.

### 8.2. Với SSLSocket/SSLEngine, bật endpoint identification rõ ràng

Khi sử dụng socket TLS mức thấp, lập trình viên có thể cần cấu hình `SSLParameters` để kích hoạt kiểm tra danh tính endpoint. Ví dụ **đoạn mã minh họa cho một server thật**, không dùng fixture self-signed ở demo:

```java
SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
try (SSLSocket socket =
        (SSLSocket) factory.createSocket("api.example.test", 443)) {
    SSLParameters parameters = socket.getSSLParameters();
    parameters.setEndpointIdentificationAlgorithm("HTTPS");
    socket.setSSLParameters(parameters);
    socket.startHandshake(); // Trust chain + endpoint identity theo JSSE.
}
```

Cần import `javax.net.ssl.SSLSocketFactory`, `SSLSocket` và `SSLParameters`. Một `SSLContext`/truststore thích hợp và chính sách chứng thư đúng vẫn là điều kiện bắt buộc. Ví dụ được viết để giải thích cách sử dụng API; tên miền `.test` không phục vụ kết nối thật.

`setEndpointIdentificationAlgorithm("HTTPS")` là **không phải** lời gọi `X509Certificate.getSubjectAlternativeNames()`. Nó chỉ dẫn JSSE thực hiện xác minh endpoint trong handshake theo cấu hình và implementation hỗ trợ. Không đặt thuật toán thành `null` hoặc chuỗi rỗng để bỏ qua bước kiểm tra vì các tùy chọn đó có thể khiến danh tính không được xác minh.

**SNI và endpoint identification khác nhau:** SNI giúp client *nói với server* mình muốn virtual host nào để server chọn certificate; endpoint identification là việc *client kiểm tra* chứng thư nhận được có khớp với đích mong đợi không. Gửi SNI đúng **không thay thế** kiểm tra hostname.

### 8.3. Nếu client kết nối theo địa chỉ IP

Ví dụ với `https://192.0.2.10`, chứng thư cần chứa SAN type `7` tương ứng với IPv4 đó, theo policy ứng dụng. Không thay bằng `DNS:192.0.2.10` hoặc `CN=192.0.2.10`.

Với IPv6 URL, địa chỉ phải được viết trong dấu ngoặc vuông ở phần authority của URI, ví dụ `https://[2001:db8::10]/`; SAN iPAddress chứa **16 octet IP**, không chứa ký tự ngoặc vuông. Nếu thực tế kết nối bằng DNS hostname nhưng socket đi tới IP đã biết, phải giữ **hostname tin cậy** làm reference identity cho TLS và cấu hình socket/peer host phù hợp, không đơn giản dùng IP rồi tắt kiểm tra.

## 9. SAN matching, truststore, certificate pinning: quan hệ thế nào?

### Truststore và CA

Truststore chứa các trust anchors/certificates mà client được phép tin theo policy. Một CA tin cậy có thể cấp nhiều chứng thư cho các máy chủ khác nhau; vì vậy **đường tin cậy hợp lệ chưa đủ**, vẫn phải đối chiếu SAN hostname/IP của **đích client đang truy cập**.

### Certificate/public-key pinning

Pinning có thể ràng buộc kết nối với một certificate hoặc SPKI/public key đã biết từ một kênh tin cậy. Nó có thể giảm một số rủi ro thay chứng thư nhưng phát sinh bài toán **rotation**, thu hồi, pin dự phòng và cấu hình outage. Không lấy “pin” bằng cách chấp nhận nguyên certificate vừa nhận từ kênh chưa được xác thực rồi coi mọi lần kết nối đầu tiên đều an toàn. Pinning **không phải** lý do mặc định để bỏ qua hostname verification hoặc chain validation, trừ khi ứng dụng có profile tin cậy được thiết kế/kiểm toán rõ ràng.

### mTLS (mutual TLS)

Trong mTLS, **client** cũng trình chứng thư cho server. Kiểm tra danh tính của **client** thường dựa vào profile ứng dụng (ví dụ URI SAN, DNS SAN, danh tính service mesh hoặc đặc tả nội bộ) và policy authorization. Không mặc định lấy bất kỳ DNS SAN của client certificate để cho phép mọi tenant. Xác thực client và xác thực hostname server là **hai hướng khác nhau** cần quản trị identity riêng.

## 10. Các tình huống thực tế và kết quả mong đợi

| Client mong đợi | SAN của server | Kết luận ở bước khớp danh tính | Nhắc lại |
| --- | --- | --- | --- |
| `api.example.test` | `DNS:api.example.test` | Khớp DNS-ID | Vẫn cần trust chain, hiệu lực và policy |
| `API.EXAMPLE.TEST` | `DNS:api.example.test` | Phù hợp khi so sánh DNS đúng chuẩn không phân biệt hoa/thường | Không dùng locale tùy ý |
| `a.svc.example.test` | `DNS:*.svc.example.test` | Khớp wildcard một nhãn theo RFC 9525 | Không cho phép wildcard vượt một label |
| `a.b.svc.example.test` | `DNS:*.svc.example.test` | Không khớp | Không dùng suffix-only |
| `svc.example.test` | `DNS:*.svc.example.test` | Không khớp | Wildcard không có nghĩa zero label |
| `192.0.2.10` | `IP:192.0.2.10` | Khớp IP-ID | Kiểm tra octet, đúng type |
| `192.0.2.11` | `IP:192.0.2.10` | Không khớp | Không có subnet/range matching |
| `192.0.2.10` | `DNS:192.0.2.10` | Không phải IP-ID theo quy tắc chuẩn | Text giống nhau không đủ |
| `2001:db8::10` | `IP:2001:0db8:0:0:0:0:0:10` | Khớp nếu 16 octet bằng nhau | Text nén/mở rộng không đổi IP |
| `legacy.example.test` | Chỉ `CN=legacy.example.test` | Không đủ theo RFC 9525 | Không fallback CN |

Bảng này nói về **bước so danh tính theo tiêu chuẩn**, không là lời cam kết rằng mọi Java provider/version/lớp ứng dụng sẽ xử lý từng trường hợp hệt nhau. Đối với hệ thống được kiểm soát, hãy test chính xác JDK/provider/TLS stack đang deploy và tuân thủ security policy đã chọn; không vì một client legacy chấp nhận CN mà làm yếu policy.

### 10.1. Reverse proxy, load balancer và service mesh

Ứng dụng có thể kết nối tới load balancer hoặc sidecar, trong khi hostname logic của dịch vụ khác với IP node. Một bộ chứng thư có SAN DNS cho tên **dịch vụ chuẩn** vẫn là cách phù hợp nếu client xác minh đúng tên đó. Đừng tự thay “hostname dùng để xác minh” bằng nội dung header `Host`/`X-Forwarded-Host` được nhận từ nguồn không tin cậy; các header này có thể bị giả mạo ở ranh giới proxy cấu hình sai.

### 10.2. Chứng thư có nhiều SAN

Một chứng thư cho `api.example.test` và `admin.example.test` có thể được client cho hai hostname dùng chung (nếu tất cả điều kiện khác hợp lệ), nhưng gây tăng phạm vi khi private key lộ. Cần phân nhóm dịch vụ/tenant theo rủi ro, certificate rotation, cơ chế quản lý khóa và giới hạn quyền cấp phát SAN.

### 10.3. Khi không có SAN

Java API có thể trả `null`. Hãy xem việc thiếu SAN phù hợp là vấn đề phát hành chứng thư cần sửa theo tiêu chuẩn mới. Không tự chuyển sang chấp nhận mọi hostname, không so CN như cứu cánh, không trust-all để bỏ qua lỗi.

## 11. Lỗi Java/TLS thường gặp và cách khắc phục

| Hiện tượng | Nguyên nhân thường gặp | Cách xử lý |
| --- | --- | --- |
| `getSubjectAlternativeNames() == null` | Certificate không có SAN extension | Phát hành lại certificate có đúng DNS/IP SAN và trust profile |
| DNS hostname không khớp | Chứng thư chứa SAN của service khác hoặc wildcard không đúng số nhãn | Định nghĩa hostname tham chiếu đúng; sửa certificate/SAN |
| HTTPS truy cập theo IP bị lỗi dù DNS SAN hợp lệ | Thiếu `iPAddress` type 7, client kiểm IP-ID | Cấp IP SAN đúng hoặc dùng hostname tin cậy đúng cách |
| IPv6 string nhìn khác nhau | Text có thể ở dạng nén/mở rộng nhưng cùng octet | Để TLS verifier so IP theo chuẩn; không so chuỗi tùy tiện |
| `CertificateException`/PKIX path building failure | Không tin CA/chain, thiếu intermediate hoặc truststore sai | Cấu hình trust anchor/chain đúng; không tắt trust validation |
| Chứng thư parse được nhưng TLS từ chối | Parse không kiểm trust, hostname hoặc EKU | Xác định loại lỗi: chain, validity, SAN/identity, policy |
| `SSLHandshakeException` có thông tin tên không phù hợp | Endpoint identification từ chối chứng thư | Kiểm tra hostname mong đợi và SAN đúng type |
| Wildcard `*.example.test` không hợp `a.b.example.test` | Wildcard chỉ bao một nhãn | Cấp SAN cụ thể cho hostname cần phục vụ |
| CN trùng hostname nhưng client vẫn từ chối | CN fallback không được dùng theo profile hiện đại | Đưa hostname vào SAN DNS/IP đúng type |
| `X509Certificate.verify(issuerKey)` chạy được nhưng URL sai host | Kiểm signature không so hostname hoặc xác minh trust path đầy đủ | Sử dụng JSSE client endpoint verification + PKIX |
| Tự implement `HostnameVerifier` trả `true` để chữa lỗi | Vô hiệu hóa bảo vệ MITM ở lớp tên | Xóa bypass, sửa cert hoặc client reference identity |
| TLS client raw `SSLSocket` chạy nhưng không kiểm hostname | Endpoint identification chưa được bật/cấu hình | `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")` theo ngữ cảnh |
| Kết nối bằng IP trong khi chứng thư cho DNS | SNI, peer host và reference identity không khớp | Cấu hình tên logic đã xác thực và cách connect đúng; không tắt check |
| Dùng self-signed fixture trong môi trường thật | Không có chain đến trust anchor theo policy | Dùng PKI/truststore thật; fixture chỉ để đọc SAN |

**Cách debug an toàn:** ghi hostname mong đợi, loại SAN và giá trị công khai, issuer/subject phù hợp, chain path và loại exception; không log private key, credential, session secret hoặc bypass xác thực để vượt lỗi. Phân biệt lỗi **name mismatch** với **untrusted issuer** vì hai lỗi cần sửa ở hai nơi khác nhau.

## 12. Kiểm thử đúng hành vi, không chỉ kiểm thử parser

Một bộ test tốt cho client HTTPS cần bao gồm:

1. Chứng thư được tin cậy theo truststore nhưng có **DNS SAN khớp** → handshake được chấp nhận nếu mọi policy khác hợp lệ.
2. Chain hợp lệ nhưng **DNS SAN khác đích** → từ chối.
3. Chain hợp lệ, chỉ có **CN khớp** nhưng thiếu SAN → từ chối theo policy mới.
4. DNS wildcard `*.svc.example.test` với một nhãn, với zero nhãn và với hai nhãn → các kết quả phù hợp với RFC 9525.
5. Kết nối bằng IP, chứng thư có IP SAN chính xác → chấp nhận theo các điều kiện khác; chỉ có DNS SAN ghi IP text → từ chối.
6. IPv6 nén/mở rộng tương đương → so theo octet, không so text thuần.
7. Chứng thư self-signed không được trust → từ chối dù DNS/IP SAN khớp.
8. Chứng thư hết hạn, revocation/policy failure hoặc KeyUsage/EKU không phù hợp → từ chối.
9. Trường hợp SNI không đúng nhưng certificate được chọn vẫn chứa SAN mong đợi → đánh giá kết quả bằng kiểm tra thực tế chứng thư nhận được; không coi SNI đã thay thế identity check.
10. Đảm bảo raw socket TLS được bật endpoint identification và các HTTP client không có cấu hình "trust-all"/"hostname verifier always true".

**Gợi ý tạo test fixture:** trong môi trường phát triển, có thể dùng `keytool` với `-ext SAN=dns:...,ip:...` để tạo một certificate tự ký cho **unit/integration test** rồi cấp truststore **test-only**. Không đem test CA/root key vào production. Khi cần xác minh failure của PKIX/SAN, test phải chạy trong mạng/test runner cô lập hoặc local server kiểm soát được, không dựa vào hostname tài liệu `.test` có tồn tại trên Internet.

### Checklist trước khi triển khai

- [ ] Mỗi hostname của dịch vụ được liệt kê bằng **dNSName SAN type 2** đúng tên.
- [ ] Nếu khách hàng kết nối bằng IP literal, chứng thư có **iPAddress SAN type 7** chính xác.
- [ ] Không phụ thuộc CN fallback; không bỏ qua hostname verification.
- [ ] Client có reference identity lấy từ endpoint được tin cậy, không từ nguồn bị attacker điều khiển.
- [ ] Có truststore/PKIX chain validation và policy issuer, validity, EKU, key usage, revocation phù hợp.
- [ ] Wildcard giới hạn đúng chuẩn, được kiểm thử ở một và nhiều mức nhãn.
- [ ] IDN/ASCII A-label và xử lý Unicode theo quy tắc giao thức/provider.
- [ ] Có kế hoạch cập nhật SAN khi đổi DNS/service host/IP, triển khai certificate rotation không tạo downtime.
- [ ] Không dùng test certificate/self-signed demo hoặc private key test trong production.
- [ ] Logs phân biệt lỗi trust chain và lỗi tên endpoint mà không để lộ bí mật.

## 13. Câu hỏi tự kiểm tra

1. SAN extension có OID nào, khác Subject CN ở điểm gì?
2. `dNSName` và `iPAddress` thuộc GeneralName type bao nhiêu? Chúng có thể hoán đổi khi giá trị text nhìn giống địa chỉ IP không?
3. Client truy cập `https://api.example.test` nhưng DNS ánh xạ tới `192.0.2.10`: nên so DNS-ID hay IP-ID? Vì sao?
4. Client truy cập `https://192.0.2.10` nhưng chỉ có `DNS:api.example.test`: vì sao chứng thư không tự được chấp nhận theo IP-ID?
5. Nếu chứng thư có `CN=legacy.example.test` và `DNS:api.example.test`, client yêu cầu `legacy.example.test` theo RFC 9525 có được dùng CN làm fallback không?
6. Vì sao `*.svc.example.test` không khớp `a.b.svc.example.test`?
7. `X509Certificate.getSubjectAlternativeNames()` trả kiểu dữ liệu gì và trường hợp nào trả `null`?
8. Vì sao `CertificateFactory` parse thành công chưa có nghĩa chứng thư đáng tin hoặc hostname đã match?
9. `X509Certificate.verify(...)` khác gì với `CertPathValidator` và endpoint identification?
10. Nếu tắt hostname verification nhưng vẫn kiểm CA chain, còn nguy cơ nào?
11. SNI giúp server làm gì? Nó có tự kiểm tra server identity cho client không?
12. Vì sao một chứng thư chứa DNS SAN và IP SAN có thể được dùng cho nhiều cách truy cập nhưng vẫn cần quản lý rủi ro private key?

**Bài tập A:** thay tệp PEM trong demo bằng một certificate thử nghiệm khác có nhiều dNSName và IP SAN. Đọc các type 2/7 và kiểm tra các giá trị theo đúng API. Không tự suy ra trust decision từ kết quả in.

**Bài tập B:** tạo certificate chỉ có CN nhưng không có DNS SAN, và certificate khác có SAN DNS khớp. Viết integration test dùng HTTPS client với truststore test để chứng minh policy endpoint identity được áp dụng.

**Bài tập C:** kiểm tra `https://hostname` và `https://IP` trên local TLS server do bạn kiểm soát với certificate có cả DNS SAN và IP SAN. Quan sát khác biệt giữa name mismatch và untrusted CA.

**Bài tập D:** viết unit test cho wildcard một nhãn/zero nhãn/nhiều nhãn dựa trên TLS hostname verifier thật của provider, không thay bằng `String.endsWith`.

## 14. Tài liệu chính thức

- [Java SE 17 — X509Certificate.getSubjectAlternativeNames](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/X509Certificate.html#getSubjectAlternativeNames()): cấu trúc `Collection<List<?>>`, SAN type 2/7, biểu diễn giá trị và trường hợp `null`.
- [RFC 5280 — Internet X.509 Public Key Infrastructure Certificate and CRL Profile](https://www.rfc-editor.org/rfc/rfc5280): `GeneralName` và extension `subjectAltName` (OID 2.5.29.17), certificate chain và name constraints.
- [RFC 9525 — Service Identity in TLS](https://www.rfc-editor.org/rfc/rfc9525): DNS-ID, IP-ID, wildcard một nhãn, IDNA và không fallback CN; cập nhật/obsoletes RFC 6125.
- [Java SE 17 — SSLParameters](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLParameters.html): `setEndpointIdentificationAlgorithm`, cấu hình kiểm tra endpoint.
- [Java SE 17 — SSLSocket](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLSocket.html): `getSSLParameters`/`setSSLParameters` và handshake.
- [Java SE 17 — CertificateFactory](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertificateFactory.html): parse chứng thư X.509 từ DER/PEM.
- [Java SE 17 — CertPathValidator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertPathValidator.html): kiểm tra certificate path theo tham số PKIX, phân biệt với hostname matching.
- [Java SE 17 — HttpClient](https://docs.oracle.com/en/java/javase/17/docs/api/java.net.http/java/net/http/HttpClient.html): client HTTP/HTTPS và cấu hình TLS.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="x509-certificate-006">13.6 KeyUsage, ExtendedKeyUsage, BasicConstraints và extension critical</a>

<details>
<summary>Xem chi tiết</summary>

# 13.6 KeyUsage, ExtendedKeyUsage, BasicConstraints và extension critical

## 1. Vì sao chứng thư X.509 cần các extension giới hạn quyền?

Chứng thư **X.509 v3** gắn một *public key* với chủ thể, tổ chức cấp, thời hạn, chữ ký và nhiều điều kiện sử dụng. Chữ ký CA xác nhận nội dung chứng thư không bị sửa và được CA phát hành, nhưng **không tự cho phép dùng khóa cho mọi mục đích**. Một chứng thư được cấp để xác thực TLS server, ký mã ứng dụng, xác thực máy khách hoặc **ký chứng thư CA** thường cần các ràng buộc **khác nhau**.

Ba extension quan trọng trong bài:

1. **KeyUsage (KU, OID `2.5.29.15`):** những **nhóm phép toán mật mã cơ bản** mà public key trong chứng thư được phép thực hiện, biểu diễn bằng các bit như `digitalSignature`, `keyEncipherment`, `keyCertSign`.
2. **ExtendedKeyUsage (EKU, OID `2.5.29.37`):** những **mục đích ứng dụng/giao thức** được phép, biểu diễn bằng danh sách OID như TLS server authentication hoặc client authentication.
3. **BasicConstraints (BC, OID `2.5.29.19`):** chủ thể có phải **Certificate Authority (CA)** có thể phát hành chứng thư hay không và độ sâu CA con (`pathLenConstraint`).

Mỗi extension có thêm cờ **critical**. Nếu một extension được đánh dấu critical, bên xác minh phải **hiểu và xử lý đúng ngữ nghĩa** của extension đó, hoặc **từ chối chứng thư** khi không có cơ chế được phê duyệt để xử lý. Cờ này **không đồng nghĩa chứng thư đáng tin, mạnh hơn hoặc có quyền cao hơn**.

### 1.1 Một chứng thư có chữ ký hợp lệ vẫn có thể bị từ chối

Giả sử một CA được trust đã phát hành một certificate cho `service.example.test`:

- `KeyUsage` không cho phép `digitalSignature`, trong khi handshake TLS 1.3 cần server dùng khóa ký handshake.
- Hoặc EKU chỉ chứa `clientAuth`, không chứa `serverAuth` trong profile ứng dụng yêu cầu.
- Hoặc certificate có extension critical riêng mà verifier không hiểu.
- Hoặc chứng thư cấp dưới được dùng làm CA mặc dù `BasicConstraints.cA=false`.
- Hoặc SAN không khớp DNS đang truy cập; hoặc chứng thư quá hạn, bị thu hồi, chain không về trust anchor được chấp nhận.

Trong mỗi trường hợp, **chỉ kiểm tra chữ ký cryptographic trên certificate là chưa đủ** để đưa ra quyết định “cho phép dùng làm TLS server”. Cần thực hiện **PKIX path validation, identity/hostname validation, usage validation, algorithm policy và (khi cần) revocation checking** theo giao thức.

## 2. Cấu trúc extension trong X.509 v3

Mỗi extension có cấu trúc ASN.1 đại ý:

```text
Extension ::= SEQUENCE {
    extnID      OBJECT IDENTIFIER,
    critical    BOOLEAN DEFAULT FALSE,
    extnValue   OCTET STRING
}

TBSCertificate ::= SEQUENCE {
    ...,
    extensions [3] EXPLICIT Extensions OPTIONAL
}
```

- **`extnID`** xác định loại extension theo OID. Các OID chuẩn: KU `2.5.29.15`, BC `2.5.29.19`, EKU `2.5.29.37`, SAN `2.5.29.17`.
- **`critical`** mặc định **false** nếu không ghi trong DER. Có thể đánh dấu true với các extension cần bên xác minh thực thi bắt buộc.
- **`extnValue`** là một `OCTET STRING` chứa **DER encoding của giá trị bên trong** từng loại extension. Java `X509Certificate.getExtensionValue(oid)` trả byte của **DER-encoded OCTET STRING bên ngoài**, không trực tiếp trả đối tượng Java đã phân tích.

Đừng parse `getExtensionValue("2.5.29.15")` như một mảng bool thô: bên trong còn ASN.1 encoding. Với KU/EKU/BC, **dùng API typed** của `X509Certificate`; với extension tùy biến cần bộ decoder ASN.1 có kiểm soát và chính sách xử lý OID.

Một certificate có nhiều extension khác: `subjectAltName` (SAN), `authorityKeyIdentifier`, `subjectKeyIdentifier`, `nameConstraints`, `certificatePolicies`, `authorityInfoAccess`, CRL distribution points... Không được bỏ qua toàn bộ extension khác chỉ vì bài này tập trung KU/EKU/BC.

## 3. KeyUsage: từng bit nói lên quyền gì?

Java đọc `boolean[] ku = cert.getKeyUsage()`. Các bit có **chỉ số cố định** theo ASN.1 và Java API:

| Chỉ số | Bit KU | Ý nghĩa điển hình |
| ---: | --- | --- |
| 0 | `digitalSignature` | Xác minh chữ ký trên dữ liệu, giao thức xác thực thực thể, chữ ký handshake; **không** phải chữ ký cấp chứng thư/CRL |
| 1 | `nonRepudiation` / `contentCommitment` | Chữ ký mang ngữ nghĩa cam kết nội dung theo chính sách, không phải phép bảo đảm pháp lý tự động |
| 2 | `keyEncipherment` | Khóa công khai được dùng **bọc/vận chuyển key** (`key transport`), ví dụ RSA key transport lịch sử |
| 3 | `dataEncipherment` | Mã hóa trực tiếp dữ liệu người dùng bằng public key trong một số giao thức, hiếm gặp |
| 4 | `keyAgreement` | Tham gia phép thỏa thuận khóa như DH/ECDH trong các profile phù hợp |
| 5 | `keyCertSign` | Public key được dùng **xác minh chữ ký phát hành chứng thư** của CA |
| 6 | `cRLSign` | Public key được dùng **xác minh chữ ký CRL** |
| 7 | `encipherOnly` | Hạn chế mục đích “chỉ mã hóa” khi `keyAgreement` được bật |
| 8 | `decipherOnly` | Hạn chế mục đích “chỉ giải mã” khi `keyAgreement` được bật |

**Điều kiện quan trọng:**

- Nếu KU **có mặt**, khóa không được dùng ngoài các mục đích mà bit được bật cho phép. Nếu KU **không có**, `getKeyUsage() == null`; điều đó không tự có nghĩa mọi hệ thống đều nên cấp quyền, vì **EKU, profile/giao thức và policy riêng** vẫn có thể hạn chế.
- Nếu KU chứa `keyCertSign`, BC cần thể hiện chủ thể là **CA** (`cA=true`) theo RFC 5280. Nếu BC không có hoặc `cA=false`, public key **không được dùng để xác minh chữ ký chứng thư** chỉ vì nó có bit keyCertSign.
- `encipherOnly` và `decipherOnly` chỉ có ý nghĩa khi `keyAgreement` bật; dùng chúng độc lập với keyAgreement là không phù hợp profile RFC 5280.
- Khi CA dùng public key ký chứng thư/CRL, KU cần bit `keyCertSign`/`cRLSign` tương ứng. RFC 5280 yêu cầu extension KU trên chứng thư của CA có những mục đích đó và khuyến nghị đánh dấu critical.
- **TLS 1.3** dùng chữ ký trong cơ chế xác thực server bằng certificate, nên `digitalSignature` là bit KU liên quan. `keyEncipherment` xuất hiện trên nhiều chứng thư RSA để tương thích profile TLS cũ, nhưng **không thay thế `digitalSignature` trong TLS 1.3**. Sự phù hợp chính xác phải theo TLS version, key algorithm, signature scheme và profile hiện hành.

Ví dụ một leaf certificate RSA cho TLS server có thể có:

```text
KeyUsage: critical
  digitalSignature = true
  keyEncipherment  = true
  keyCertSign      = false
  cRLSign          = false
```

`keyEncipherment=true` **không** làm chứng thư trở thành CA và không chứng minh serverAuth EKU tồn tại. Ngược lại, chứng thư CA thường bật `keyCertSign` và có thể bật `cRLSign`, đồng thời cần BC `cA=true`.

### Java: đọc bit an toàn

```java
// Đoạn minh họa trong phương thức Java, với X509Certificate cert.
boolean[] usage = cert.getKeyUsage(); // null nếu thiếu extension
boolean canSignData = usage != null
        && usage.length > 0 && usage[0];
boolean canIssueCerts = usage != null
        && usage.length > 5 && usage[5];
```

Đây là ví dụ **policy nghiêm ngặt** yêu cầu bit phải có, chứ không tự diễn giải sự vắng KU là “cấm mọi thao tác” ở mọi giao thức. Với các profile cho phép KU vắng mặt, hãy triển khai quy tắc **“nếu extension có mặt thì phải phù hợp”** và các yêu cầu bắt buộc riêng của profile.

## 4. ExtendedKeyUsage: mục đích ứng dụng

`ExtendedKeyUsage` là danh sách **KeyPurposeId OID**, không phải bit array. Java trả `List<String>` từ `cert.getExtendedKeyUsage()` (và có thể ném `CertificateParsingException` nếu encoding sai).

Các OID thường gặp:

| EKU | OID | Trường hợp sử dụng |
| --- | --- | --- |
| `serverAuth` | `1.3.6.1.5.5.7.3.1` | Xác thực TLS server |
| `clientAuth` | `1.3.6.1.5.5.7.3.2` | Xác thực TLS client |
| `codeSigning` | `1.3.6.1.5.5.7.3.3` | Ký mã phần mềm |
| `emailProtection` | `1.3.6.1.5.5.7.3.4` | Bảo vệ email/S/MIME theo profile |
| `timeStamping` | `1.3.6.1.5.5.7.3.8` | Time-stamping authority |
| `OCSPSigning` | `1.3.6.1.5.5.7.3.9` | Ủy quyền ký phản hồi OCSP |
| `anyExtendedKeyUsage` | `2.5.29.37.0` | OID đặc biệt; cách chấp nhận chịu ràng buộc profile và chính sách relying party |

**Quy tắc kết hợp:** khi **cả KU và EKU có mặt**, mục đích sử dụng cần phù hợp **với cả hai**. EKU `serverAuth` không cấp quyền ký handshake nếu KU cấm digital signature trong profile có yêu cầu nó; KU `digitalSignature` cũng không vượt qua EKU chỉ ghi `clientAuth` nếu ứng dụng đang xác thực **server**.

**EKU vắng mặt:** `getExtendedKeyUsage()==null` nghĩa là chứng thư **không bị ràng buộc bởi extension EKU**; không phải certificate bị hỏng, cũng **không chứng minh quyền dùng trong mọi hệ thống**. Ứng dụng hoặc tổ chức có thể yêu cầu **EKU serverAuth phải hiện diện rõ ràng**; đó là lựa chọn policy và có thể nghiêm hơn yêu cầu chung của một số profile.

**EKU có mặt:** danh sách OID chỉ định các mục đích được cấp. EKU có thể được đánh dấu critical **hoặc non-critical** theo RFC 5280; dù non-critical, verifier **biết rõ** EKU vẫn phải thực thi ràng buộc khi xét mục đích sử dụng. Không coi non-critical là “được bỏ qua mọi lúc”.

**`anyExtendedKeyUsage`:** không phải nút “bỏ qua mọi kiểm tra”. RFC có khái niệm OID này nhưng relying party có thể đòi explicit OID mục đích và các profile riêng có quy tắc chặt hơn. Với TLS server trong một policy nội bộ yêu cầu `serverAuth`, nên yêu cầu rõ OID tương ứng thay vì tự suy diễn từ `anyExtendedKeyUsage`.

### Tình huống rõ nhất: một certificate clientAuth dùng làm server

```text
Leaf certificate:
  KeyUsage: digitalSignature
  ExtendedKeyUsage: clientAuth
  BasicConstraints: cA=false
  Signature: hợp lệ bởi CA được trust

Kết quả:
  PKIX path validation thuần: có thể PASS.
  TLS-server purpose "phải có EKU serverAuth": FAIL.
```

**Vì sao?** PKIX `CertPathValidator` kiểm tra chuỗi/điều kiện PKIX, nhưng **không tự là HTTPS hostname verifier hoặc bộ đánh giá mọi purpose nghiệp vụ**. Người lập trình phải dùng trust manager/phân hệ TLS được cấu hình đầy đủ; ví dụ bên dưới cố ý tách hai kiểm tra để người học thấy rõ.

## 5. BasicConstraints: ai có thể phát hành chứng thư?

Cấu trúc:

```text
BasicConstraints ::= SEQUENCE {
    cA                 BOOLEAN DEFAULT FALSE,
    pathLenConstraint  INTEGER (0..MAX) OPTIONAL
}
```

- `cA=false`: chủ thể không phải CA phát hành chứng thư theo profile, thường là **end entity (EE)** như một server hoặc client certificate.
- `cA=true`: chứng thư có thể được dùng làm CA **nếu** các điều kiện khác như `keyCertSign`, trust, thuật toán, path validation đều thỏa.
- `pathLenConstraint`: số lượng tối đa **chứng thư CA trung gian không tự cấp (non-self-issued)** được phép nằm sau CA này trong đường dẫn hợp lệ, trước end entity. Không tính end-entity leaf. **`pathLen=0` vẫn cho phép CA trực tiếp ký leaf**, nhưng không cho thêm intermediate CA không tự cấp bên dưới theo RFC 5280.
- Không có `pathLenConstraint` ở một certificate CA `cA=true`: không giới hạn CA-chain depth bởi **chính field này**; path vẫn phải tuân thủ các constraint/policy khác.
- RFC 5280 không cho phép khai báo pathLen trên certificate có `cA=false`; và khi CA dùng khóa để ký chứng thư, BC phải hiện diện và **critical** theo profile.

Trong Java:

```java
// Đoạn minh họa: cert là X509Certificate.
int constraint = cert.getBasicConstraints();
boolean isCa = constraint >= 0;
// constraint == -1: không phải CA (hoặc BC không có).
// constraint == 0 : CA nhưng không có non-self-issued intermediate CA sau nó.
// constraint > 0  : CA với giới hạn độ sâu tương ứng.
// constraint == Integer.MAX_VALUE : CA không khai báo pathLen.
```

**Điểm dễ nhầm:** giá trị Java `-1` xuất hiện cả khi BC **không có** lẫn khi BC có `cA=false`; muốn biết extension có hiện diện, kiểm tra `cert.getExtensionValue("2.5.29.19") != null`. `getBasicConstraints()` là một **convenience API**, không phải công cụ dump toàn bộ ASN.1.

### CA chain và trust anchor

```text
Trust anchor (CA root được cấu hình tin cậy)
    |
    +--- Intermediate CA (BC cA=true, KU keyCertSign)
             |
             +--- End entity / server (BC cA=false, EKU serverAuth)
```

**Không tự coi certificate có `cA=true` là root đáng tin.** Bất kỳ attacker nào cũng có thể tự ký một certificate `CA=true`; chỉ trust anchor được cấp qua cấu hình/PKI đáng tin mới là cơ sở trust. Trong Java `TrustAnchor(rootCert,null)` cố ý **coi rootCert được tin**; nếu nhận file root từ mạng không tin rồi lập tức đưa làm TrustAnchor, toàn bộ kiểm tra chuỗi trở nên vô nghĩa.

Trust anchor có thể được xử lý khác một intermediate certificate khi kiểm tra path: nhiều ràng buộc trong certificate tự ký của root không được áp dụng hệt như các chứng thư nằm *trong* CertPath. Phải quản lý chính sách trust anchor/PKIXParameters riêng, không lấy kết quả `CertPathValidator` làm chứng cứ root đã được kiểm duyệt.

## 6. Extension critical: “hiểu và xử lý hoặc từ chối”

Theo RFC 5280, cờ critical được đặt trong **từng extension**, không phải cờ toàn certificate.

| Tình huống | Hành vi của bộ xác minh phù hợp |
| --- | --- |
| Extension **critical** và được hiểu/được xử lý | Áp dụng ràng buộc của nó trong bối cảnh PKIX/application |
| Extension **critical** nhưng chưa có checker xử lý | **Từ chối certificate/path** |
| Extension **non-critical** không được hiểu | Có thể bỏ qua extension chưa biết theo profile |
| Extension **non-critical** đã biết và liên quan purpose (như EKU) | Không được tự ý bỏ ràng buộc cần thực thi khi xét usage |
| Có các extension critical quen thuộc, nhưng key usage/mục đích không phù hợp | **Vẫn từ chối** dù parser hiểu extension |
| Không có extension unknown critical, nhưng chain/hostname sai | **Vẫn từ chối**; critical không thay thế PKIX/identity validation |

Ví dụ:

```text
Certificate A:
  Critical: KeyUsage (2.5.29.15)
  Noncritical: ExtendedKeyUsage (2.5.29.37)
  Khả năng: verifier hiểu KU và kiểm tra purpose EKU -> có thể hợp lệ.

Certificate B:
  Critical: 1.2.3.4.5 (OID custom không ai xử lý)
  Khả năng: parser X.509 đọc được file nhưng PKIX phải từ chối path.

Certificate C:
  Noncritical: 1.2.3.4.5
  Khả năng: PKIX có thể bỏ qua OID không biết nếu các kiểm tra khác đạt.
```

### Các API Java liên quan

| API | Trả về | Lưu ý |
| --- | --- | --- |
| `getCriticalExtensionOIDs()` | `Set<String>` các OID critical; có thể `null` | Không tự nói extension có được **xử lý đúng** hay không |
| `getNonCriticalExtensionOIDs()` | `Set<String>` các OID non-critical | Sử dụng để kiểm tra nhãn, không thay định nghĩa extension |
| `hasUnsupportedCriticalExtension()` | `boolean` | Chỉ phản ánh sự hỗ trợ mà implementation báo cáo; **không đủ thay PKIX/purpose validation** |
| `getExtensionValue(oid)` | `byte[]` DER của outer OCTET STRING hoặc `null` | Không phải kết quả parse typed; phải decode ASN.1 đúng lớp |
| `getKeyUsage()` | `boolean[]` hoặc `null` | Các bit cố định 0..8 |
| `getExtendedKeyUsage()` | `List<String>` hoặc `null` | OID, không phải tên thân thiện |
| `getBasicConstraints()` | `int` | `-1` end entity/not CA; giá trị CA là path length |
| `checkValidity()` | Không trả về khi ngày hợp lệ | Chỉ thời gian, không kiểm tra chain/trust/purpose |

Cần sử dụng `CertificateFactory` để parse chứng thư, `CertPathValidator.getInstance("PKIX")` để kiểm tra một CertPath đến trust anchor đã cấu hình, và TLS trust manager/hostname verifier cho HTTPS. `certificate.verify(issuerPublicKey)` chỉ kiểm tra **chữ ký của certificate**, không tự xác minh CA chain, KU/EKU, critical extensions, SAN, thời hạn hoặc revocation.

## 7. Tạo bộ chứng thư demo với keytool (Java 17+)

Để ví dụ **không phụ thuộc CA/tệp bí mật bên ngoài**, dùng `keytool` đi kèm JDK để tạo **chứng thư test tự ký** và certificate leaf được CA test ký. Toàn bộ key material chỉ phục vụ học tập.

Sử dụng **PowerShell 5.1+ trên Windows** hoặc PowerShell 7+; chạy trong một phiên PowerShell và lưu nguồn Java của phần 8 ở file `X509ExtensionsDemo.java` trong thư mục đang làm việc.

**Không dùng password mẫu `changeit` hoặc root CA test này trong sản phẩm.** Mã demo tạo `root.p12` chứa private key CA trong một thư mục tạm; hãy bảo vệ/xóa thư mục và không commit lên repository. Những chứng thư này **không thuộc hệ thống CA công khai và không được trust ngoài chương trình demo**.

```powershell
# Tạo thư mục tạm và password DEMO. Chạy liên tiếp cùng phiên PowerShell.
$t = Join-Path $env:TEMP ("x509-extension-demo-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path $t | Out-Null
$pw = "changeit"

# CA root: BasicConstraints critical, CA=true, pathLen=0;
# KeyUsage critical, cho phép ký certificate và CRL.
keytool -genkeypair -alias root -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -dname "CN=Example Test Root" -validity 365 -storetype PKCS12 -keystore (Join-Path $t "root.p12") -storepass $pw -keypass $pw -ext "BC:critical=ca:true,pathlen:0" -ext "KU:critical=keyCertSign,cRLSign" -noprompt

# Leaf tạo khóa và CSR, chưa phải certificate do CA ký.
keytool -genkeypair -alias leaf -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -dname "CN=service.example.test" -validity 30 -storetype PKCS12 -keystore (Join-Path $t "leaf.p12") -storepass $pw -keypass $pw -noprompt
keytool -certreq -alias leaf -keystore (Join-Path $t "leaf.p12") -storepass $pw -file (Join-Path $t "leaf.csr")

# Export root certificate (không phải private key).
keytool -exportcert -alias root -keystore (Join-Path $t "root.p12") -storepass $pw -rfc -file (Join-Path $t "root.pem")

# Các tham số chung cho 4 leaf certificates do root ký.
$base = @("-gencert", "-alias", "root",
  "-keystore", (Join-Path $t "root.p12"), "-storepass", $pw,
  "-infile", (Join-Path $t "leaf.csr"), "-validity", "30", "-rfc",
  "-ext", "KU:critical=digitalSignature,keyEncipherment",
  "-ext", "BC=ca:false", "-ext", "SAN=dns:service.example.test")

# Server hợp lệ theo mục đích demo.
& keytool @base -outfile (Join-Path $t "server.pem") -ext "EKU=serverAuth"

# OID tùy biến dạng critical: validator thông thường không xử lý được.
& keytool @base -outfile (Join-Path $t "critical.pem") -ext "EKU=serverAuth" -ext "1.2.3.4.5:critical=0101FF"

# Cùng OID tùy biến dạng noncritical: validator có thể bỏ qua.
& keytool @base -outfile (Join-Path $t "noncritical.pem") -ext "EKU=serverAuth" -ext "1.2.3.4.5=0101FF"

# Chứng thư chỉ có EKU clientAuth, không có serverAuth.
& keytool @base -outfile (Join-Path $t "client.pem") -ext "EKU=clientAuth"
```

Lệnh `-gencert` cho ra các leaf certificate đã được root ký. Tham số `-ext "1.2.3.4.5:critical=0101FF"` biểu diễn một OID tùy biến giả lập cho bài học, với một giá trị DER nhỏ. Không dùng OID tùy tiện như vậy để thiết kế extension sản phẩm; cần OID/schema đã đăng ký và bộ xử lý phù hợp.

Các lệnh `keytool` có thể in thông báo tạo chứng thư trên stderr/stdout tùy JDK; **stdout chính xác** mà phần 8 ghi là output của **lệnh `java`**, không bao gồm thông báo `keytool`. Nếu môi trường không có `keytool`, cần cài JDK hỗ trợ. Khi chạy ở macOS/Linux, có thể chuyển script PowerShell sang cú pháp shell tương ứng, nhưng các tham số `keytool` và ý nghĩa certificate không thay đổi.

## 8. Chương trình Java 17+: đọc KU/EKU/BC và thử critical/noncritical

Chương trình sau dùng **chỉ Java SE** để:

1. Đọc chứng thư PEM bằng `CertificateFactory("X.509")`.
2. Kiểm tra CA `BasicConstraints.cA=true`, `pathLen=0`, KU `keyCertSign` và BC `critical`.
3. Kiểm tra leaf `cA=false`, KU `digitalSignature/keyEncipherment`, EKU `serverAuth`, KU critical, EKU noncritical.
4. Thiết lập một trust anchor CA test **cố ý tin cậy trong phạm vi demo**, tạo `CertPath` cho leaf và gọi `CertPathValidator("PKIX")`.
5. So sánh certificate có OID tùy biến **critical vs noncritical**: critical bị từ chối, noncritical được chấp nhận theo PKIX khi các điều kiện khác đúng.
6. So sánh certificate chỉ có `clientAuth`: PKIX path thuần vẫn được chấp nhận, nhưng phép kiểm tra *server purpose* riêng từ chối.

Lưu thành **`X509ExtensionsDemo.java`**:

```java
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class X509ExtensionsDemo {
    private static final String KU = "2.5.29.15";
    private static final String EKU = "2.5.29.37";
    private static final String BC = "2.5.29.19";
    private static final String SERVER_AUTH = "1.3.6.1.5.5.7.3.1";
    private static final String CUSTOM = "1.2.3.4.5";

    private static X509Certificate read(String path) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            return (X509Certificate) factory.generateCertificate(in);
        }
    }

    private static boolean bit(boolean[] bits, int index) {
        return bits != null && bits.length > index && bits[index];
    }

    private static boolean has(Set<String> oids, String oid) {
        return oids != null && oids.contains(oid);
    }

    private static boolean hasDnsSan(X509Certificate certificate,
                                     String host) throws Exception {
        Collection<List<?>> names = certificate.getSubjectAlternativeNames();
        if (names == null) return false;
        for (List<?> item : names) {
            if (item.size() >= 2 && Integer.valueOf(2).equals(item.get(0))
                    && item.get(1) instanceof String dns
                    && host.equalsIgnoreCase(dns)) {
                return true;
            }
        }
        return false;
    }

    private static boolean pkixAccepts(X509Certificate root,
                                       X509Certificate leaf) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        CertPath path = factory.generateCertPath(List.of(leaf));
        PKIXParameters params = new PKIXParameters(
                Set.of(new TrustAnchor(root, null)));
        params.setRevocationEnabled(false); // Test fixture only.
        try {
            CertPathValidator.getInstance("PKIX").validate(path, params);
            return true;
        } catch (CertPathValidatorException rejected) {
            return false;
        }
    }

    private static boolean demoServerPurpose(X509Certificate certificate)
            throws Exception {
        List<String> purposes = certificate.getExtendedKeyUsage();
        return certificate.getBasicConstraints() == -1
                && bit(certificate.getKeyUsage(), 0)
                && purposes != null
                && purposes.contains(SERVER_AUTH)
                && hasDnsSan(certificate, "service.example.test");
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            throw new IllegalArgumentException(
                    "Usage: java X509ExtensionsDemo root.pem server.pem "
                    + "critical.pem noncritical.pem client.pem");
        }
        X509Certificate root = read(args[0]);
        X509Certificate server = read(args[1]);
        X509Certificate unknownCritical = read(args[2]);
        X509Certificate unknownNoncritical = read(args[3]);
        X509Certificate client = read(args[4]);

        root.checkValidity();
        server.checkValidity();
        System.out.println("rootIsCa=" + (root.getBasicConstraints() >= 0));
        System.out.println("rootPathLen=" + root.getBasicConstraints());
        System.out.println("rootKeyCertSign=" + bit(root.getKeyUsage(), 5));
        System.out.println("rootBcCritical=" + has(
                root.getCriticalExtensionOIDs(), BC));

        System.out.println("leafIsEndEntity="
                + (server.getBasicConstraints() == -1));
        System.out.println("leafDigitalSignature="
                + bit(server.getKeyUsage(), 0));
        System.out.println("leafKeyEncipherment="
                + bit(server.getKeyUsage(), 2));
        System.out.println("leafServerAuthEku="
                + server.getExtendedKeyUsage().contains(SERVER_AUTH));
        System.out.println("leafKuCritical=" + has(
                server.getCriticalExtensionOIDs(), KU));
        System.out.println("leafEkuNonCritical=" + has(
                server.getNonCriticalExtensionOIDs(), EKU));
        System.out.println("serverPkixAccepted="
                + pkixAccepts(root, server));
        System.out.println("serverPurposeForDemo="
                + demoServerPurpose(server));

        System.out.println("unknownCriticalDeclared=" + has(
                unknownCritical.getCriticalExtensionOIDs(), CUSTOM));
        System.out.println("unknownCriticalRejected="
                + !pkixAccepts(root, unknownCritical));
        System.out.println("unknownNoncriticalDeclared=" + has(
                unknownNoncritical.getNonCriticalExtensionOIDs(), CUSTOM));
        System.out.println("unknownNoncriticalAccepted="
                + pkixAccepts(root, unknownNoncritical));

        System.out.println("clientOnlyPkixAccepted="
                + pkixAccepts(root, client));
        System.out.println("clientOnlyServerPurpose="
                + demoServerPurpose(client));
    }
}
```

Biên dịch và chạy (trong **cùng phiên PowerShell** còn giữ biến `$t`):

```powershell
javac --release 17 -encoding UTF-8 X509ExtensionsDemo.java
java X509ExtensionsDemo (Join-Path $t "root.pem") (Join-Path $t "server.pem") (Join-Path $t "critical.pem") (Join-Path $t "noncritical.pem") (Join-Path $t "client.pem")
```

**Kết quả đã kiểm tra** với JDK 17+ có provider PKIX/keytool thông dụng:

```text
rootIsCa=true
rootPathLen=0
rootKeyCertSign=true
rootBcCritical=true
leafIsEndEntity=true
leafDigitalSignature=true
leafKeyEncipherment=true
leafServerAuthEku=true
leafKuCritical=true
leafEkuNonCritical=true
serverPkixAccepted=true
serverPurposeForDemo=true
unknownCriticalDeclared=true
unknownCriticalRejected=true
unknownNoncriticalDeclared=true
unknownNoncriticalAccepted=true
clientOnlyPkixAccepted=true
clientOnlyServerPurpose=false
```

Sau khi học xong, xóa các private keys/certificates demo trong thư mục tạm:

```powershell
# Chỉ chạy khi $t đúng là thư mục DEMO vừa tạo, không chứa dữ liệu cần giữ.
if ($t -like (Join-Path $env:TEMP "x509-extension-demo-*")) {
    Remove-Item -LiteralPath $t -Recurse -Force
}
```

**Cảnh báo:** xóa file thông thường không đồng nghĩa secure erase khỏi SSD/snapshot/backup. Test fixture không nên dùng key thật; thư mục đã được tạo chỉ để thực hành.

### 8.1 Vì sao các nhóm kết quả quan trọng?

**`rootIsCa=true`, `rootPathLen=0`, `rootKeyCertSign=true`:** root test có BC CA=true, không cho trung gian CA non-self-issued sau root, nhưng vẫn có thể ký trực tiếp end-entity leaf. `rootBcCritical=true` chứng minh flag critical của BC hiện diện trong fixture.

**`leafIsEndEntity=true`:** `getBasicConstraints() == -1` chứng minh Java xem leaf không phải CA. **Không được** dùng leaf để ký chứng thư con kể cả khi khóa của nó có khả năng kỹ thuật tạo RSA signature.

**`leafDigitalSignature=true` và `leafKeyEncipherment=true`:** hai quyền KU đều bật trong fixture. Không có nghĩa leaf có quyền `keyCertSign`; đây là quyền khác ở bit 5.

**`leafServerAuthEku=true`, `leafKuCritical=true`, `leafEkuNonCritical=true`:** xác nhận EKU serverAuth được phát hành, KU được đánh dấu critical và EKU được đánh dấu non-critical. Việc EKU non-critical không làm ràng buộc mục đích bị vô hiệu.

**`serverPkixAccepted=true`:** chuỗi leaf → root **với root test đã được cấu hình như TrustAnchor** qua `PKIXParameters` vượt qua các kiểm tra chung trong runtime. Đây **không** có nghĩa root tự nhiên được Internet trust. Trong ví dụ `params.setRevocationEnabled(false)` chỉ để fixture không cần CRL/OCSP; **không nên tắt revocation mặc định trong hệ thống thực** mà không có chính sách thay thế thích hợp.

**`unknownCriticalRejected=true`:** chứa OID critical không có checker xử lý khiến CertPathValidator trả `CertPathValidatorException` và path bị loại. Chỉ parse certificate thành công bằng `CertificateFactory` **không chứng minh** nó có thể được chấp nhận.

**`unknownNoncriticalAccepted=true`:** OID không hiểu nhưng non-critical có thể được bỏ qua, miễn tất cả yêu cầu khác đạt. Điều này không có nghĩa một non-critical EKU serverAuth/clientAuth đã biết cũng có thể bỏ qua khi kiểm tra mục đích.

**`clientOnlyPkixAccepted=true` nhưng `clientOnlyServerPurpose=false`:** generic PKIX path validator **không tự xác minh mọi mục đích nghiệp vụ**. Trong fixture, leaf có EKU `clientAuth`, còn policy minh họa yêu cầu EKU `serverAuth`; do đó không cho phép dùng nó làm TLS-server certificate.

### 8.2 Những giới hạn cố ý của ví dụ

- `demoServerPurpose` chỉ kiểm tra **đúng một DNS SAN** không wildcard/IDNA, KU digitalSignature, EKU serverAuth và BC EE. Đây là **giải thích học thuật**, **không phải hostname verifier sản phẩm**. TLS phải dùng `TrustManagerFactory`, `X509ExtendedTrustManager`/JSSE và cơ chế endpoint identification phù hợp.
- `CertPathValidator` trong ví dụ chỉ xác nhận **một leaf** do root demo ký. Hệ thống thực có intermediate CA, name constraints, certificate policy, algorithm constraints, revocation, các ràng buộc trust store, EKU TLS và identity/hostname cần xử lý theo giao thức.
- `params.setRevocationEnabled(false)` chỉ cho môi trường test không có CRL/OCSP. Trong sản phẩm cần chính sách revocation được đánh giá, cache/failure mode phù hợp, có thể dùng `PKIXRevocationChecker` hoặc JSSE provider được cấu hình.
- `TrustAnchor(root, null)` giả định root đã được trust. **Không bao giờ lấy root từ người gửi không xác thực rồi tự thêm làm trust anchor.**
- `keytool` và cách nhận biết custom extension của các provider khác có thể có khác biệt. Mã và output trên đã được kiểm thử trên môi trường JDK có các thành phần trên, không hứa mọi provider sẽ có cùng diagnostic message.
- Không dùng private key test chứa trong PKCS12 demo cho bất cứ hệ thống thật. Password `changeit` dễ đoán; mọi bí mật sản phẩm phải dùng storage và lifecycle riêng.

## 9. KU/EKU/BC phối hợp trong các tình huống thực tế

| Certificate | KU quan trọng | EKU theo purpose | BC | Chú ý |
| --- | --- | --- | --- | --- |
| TLS server key ký handshake | `digitalSignature` | `serverAuth` khi profile yêu cầu | EE `cA=false` | Cần SAN/hostname + PKIX + thuật toán/signature scheme |
| TLS mutual-auth client | `digitalSignature` | `clientAuth` | EE | Không dùng làm server nếu policy yêu cầu serverAuth |
| CA phát hành certificate | `keyCertSign` (và `cRLSign` nếu ký CRL) | Theo policy CA, nhiều trường hợp không có EKU hoặc bị ràng buộc | CA `cA=true`, BC critical | Phải trust/path valid, pathLen tuân thủ |
| CA trung gian | `keyCertSign` | Nếu có EKU, cần xem giới hạn mục đích chain theo profile | CA `cA=true` | `pathLen` xác định CA con non-self-issued tối đa |
| Ký mã nguồn/phần mềm | `digitalSignature` hoặc profile thích hợp | `codeSigning` | EE | Không tự được dùng làm TLS server |
| S/MIME email | KU tùy chức năng ký/mã hóa | `emailProtection` | EE | Cần policy và kiểm tra địa chỉ/chủ thể liên quan |
| OCSP delegated responder | `digitalSignature` | `OCSPSigning` | Theo profile OCSP | Cần xác minh ủy quyền OCSP/responder chain |
| Chứng thư giả làm CA chỉ bật KU keyCertSign | `keyCertSign` | Bất kỳ | `cA=false` | **Không** được dùng cấp certificate theo RFC 5280 |

**Đừng sao chép KU/EKU template vô điều kiện** giữa các sản phẩm. Các chuẩn TLS, S/MIME, CMS, code-signing, OCSP, CA/B Forum, PKI nội bộ có yêu cầu chi tiết khác nhau và có thể thay đổi. Chọn extension theo **mục đích key, cơ chế signature/encryption và relying-party profile**.

Một chứng thư CA có `pathLen=0` có thể trực tiếp phát hành leaf; nếu cần intermediate CA mới dưới nó, phải có CA pathLen phù hợp và được cấp lại với key hierarchy thích hợp. `BasicConstraints` không phải công cụ phân quyền nghiệp vụ tất cả loại chứng thư; nó chủ yếu giới hạn **quyền phát hành certificate** và chain depth.

## 10. Phân tích lỗi và chống sai cấu hình

| Biểu hiện | Nguyên nhân thường gặp | Cách xử lý |
| --- | --- | --- |
| `InvalidCertificate` hoặc `CertificateParsingException` khi đọc EKU | DER ASN.1 của extension sai | Từ chối certificate, xác định issuer, dùng parser/provider an toàn |
| `getKeyUsage()==null` | Certificate không có KU | Phân biệt “không hạn chế bởi KU” với policy yêu cầu KU hiện diện |
| `getExtendedKeyUsage()==null` | Certificate không có EKU | Đánh giá profile/purpose, không mặc định mọi ứng dụng đều cho phép |
| `getBasicConstraints()==-1` | EE `cA=false` hoặc không có BC | Không dùng làm CA; kiểm tra presence nếu cần phân biệt |
| `Integer.MAX_VALUE` từ `getBasicConstraints` | CA không có `pathLenConstraint` | Không có giới hạn **từ field pathLen**, nhưng các constraint khác vẫn áp dụng |
| KU `keyCertSign=true` nhưng BC `cA=false` | Extension mâu thuẫn | Từ chối dùng làm CA, sửa cấp phát certificate |
| Generic PKIX PASS nhưng HTTPS fail | SAN/hostname, serverAuth EKU, KU/signature algorithm hoặc TLS policy khác | Dùng JSSE trust manager và endpoint verification đầy đủ |
| PKIX fail ở `UNRECOGNIZED_CRIT_EXT` / lỗi critical | Extension critical OID không được checker nào xử lý | Bổ sung checker hợp lệ hoặc yêu cầu issuer cấp chứng thư tương thích; không bỏ cờ critical tại runtime |
| Unknown non-critical bị bỏ qua | Đúng hành vi khi extension không ảnh hưởng purpose/profile | Không nhầm với extension được biết nhưng hạn chế usage |
| Test self-signed root “pass” dù root không tin cậy | Ứng dụng tự thêm root tùy ý làm `TrustAnchor` | Chỉ dùng trust store/anchor được quản trị qua kênh xác thực |
| `CertificateFactory` parse được certificate nhưng validation fail | Parse cú pháp không bằng xác minh chain/trust | Gọi `CertPathValidator`/TLS engine đúng nhiệm vụ |
| Leaf KU/EKU không khớp mục đích | CA dùng extension template sai | Cấp lại với purpose và thuật toán phù hợp; không bỏ qua policy |
| PKIX fail do thời hạn hoặc chính sách crypto | Chứng thư hết hạn, key yếu, algorithm disabled | Rotate/re-issue, cập nhật PKI an toàn, không tắt algorithm constraints |
| Không xác minh revocation | Không cấu hình CRL/OCSP hoặc cố ý disable để demo | Thiết kế revocation policy cho môi trường thực |

### Quy trình kiểm tra đề xuất

1. **Parse:** `CertificateFactory("X.509")`, giới hạn kích thước input, không coi file PEM hợp lệ là certificate đáng tin.
2. **Xem extension:** `getKeyUsage()`, `getExtendedKeyUsage()`, `getBasicConstraints()`, OID critical/noncritical; chú ý vắng extension và các giá trị mặc định.
3. **Xác minh chain:** cấu hình trusted anchors thực, intermediate certificates, thời hạn, algorithm constraints, policy, critical extensions, revocation theo profile.
4. **Xác minh purpose:** KU/EKU phù hợp thao tác và hệ thống, ví dụ TLS server vs client. Khi cả hai có mặt phải đồng thời phù hợp.
5. **Xác minh identity:** SAN hostname/IP hay định danh khác theo giao thức; không dùng CN đơn thuần như thay thế SAN trong hệ thống TLS hiện đại.
6. **Chỉ cho phép operation sau khi tất cả bước liên quan PASS.** Ghi audit có chọn lọc (subject, issuer, serial, fingerprint public certificate), không log private keys/tokens nhạy cảm.

## 11. Các hiểu lầm cần nhớ

**“KeyUsage critical thì certificate tự đáng tin?”** Không. Critical chỉ yêu cầu verifier hiểu và xử lý extension; trust anchor, chain, thời hạn, danh tính và purpose vẫn phải kiểm tra.

**“EKU non-critical nghĩa là được bỏ qua serverAuth?”** Không. Nếu một application verifier hiểu EKU, nó phải xét quyền sử dụng trong profile; noncritical chỉ cho phép bỏ qua **extension không hiểu** theo chính sách.

**“CA=true là root CA đáng tin?”** Không. `cA=true` là quyền phát hành về mặt cấu trúc; trust anchor cần được thiết lập ngoài certificate bằng cơ chế đáng tin.

**“pathLen=0 nghĩa là không được cấp leaf?”** Không. Nó hạn chế **non-self-issued intermediate CA** ở phía dưới, không cấm cấp end entity trực tiếp.

**“Có KU digitalSignature nghĩa là được cấp CA?”** Không. Muốn ký chứng thư cần `keyCertSign` và `BasicConstraints.cA=true`, cùng path/trust/policy phù hợp.

**“Có KU keyEncipherment nghĩa là server TLS 1.3 dùng được?”** Không đủ. TLS 1.3 certificate authentication dùng chữ ký; certificate algorithm, digitalSignature, EKU và SAN/hostname phải được đánh giá.

**“Nếu `getExtendedKeyUsage() == null` thì chứng thư không dùng được?”** Không tự động. Nó chỉ không có ràng buộc EKU được khai báo; giao thức/policy có thể yêu cầu cụ thể.

**“`certificate.verify(publicKey)` là đã kiểm PKIX?”** Không. Đó là kiểm chữ ký certificate, không làm toàn bộ path validation và hostname/purpose checks.

**“`getCriticalExtensionOIDs()` rỗng thì HTTPS an toàn?”** Không. Thiếu critical unknown chỉ loại một nhóm lỗi; các điều kiện trust/identity khác vẫn bắt buộc.

## 12. Tự kiểm tra kiến thức

1. **OID của KU, BC, EKU?** Lần lượt `2.5.29.15`, `2.5.29.19`, `2.5.29.37`.
2. **Chỉ số bit `digitalSignature`, `keyEncipherment`, `keyCertSign`?** `0`, `2`, `5`.
3. **Java trả gì nếu KU không có?** `X509Certificate.getKeyUsage()==null`.
4. **Java trả gì nếu EKU không có?** `getExtendedKeyUsage()==null`.
5. **`getBasicConstraints()==-1` có thể có mấy tình huống?** `cA=false` hoặc BC không có; đều không thể dựa vào đó để cấp quyền làm CA.
6. **`getBasicConstraints()==Integer.MAX_VALUE` thường là gì?** CA có `cA=true` nhưng không nêu `pathLenConstraint`.
7. **Root CA có `pathLen=0` có được ký leaf trực tiếp không?** Có; không được có thêm non-self-issued intermediate CA bên dưới theo ràng buộc đó.
8. **KU `keyCertSign=true` có đủ để dùng key làm CA không?** Không; cần `BasicConstraints.cA=true`, trust/path và chính sách.
9. **EKU serverAuth có thay thế KU digitalSignature cho TLS 1.3 không?** Không; khi cả hai có mặt phải phù hợp nhau.
10. **Một certificate chỉ có EKU clientAuth, generic PKIX chain có thể pass không?** Có; server-purpose verifier vẫn phải từ chối khi yêu cầu `serverAuth`.
11. **Extension critical không nhận biết có thể được bỏ qua không?** Không; verifier phù hợp phải hiểu và xử lý hoặc từ chối.
12. **Unknown non-critical có thể bỏ qua không?** Có thể, khi extension không được hỗ trợ và profile cho phép; không có nghĩa được bỏ qua EKU/KU đã biết.
13. **`getExtensionValue(oid)` trả raw nội dung extension hay bytes outer OCTET STRING?** Bytes **DER-encoded OCTET STRING** bên ngoài.
14. **Dùng `TrustAnchor(rootFromNetwork,null)` tùy tiện có an toàn không?** Không, vì đã tự tin một CA không được xác minh.
15. **`checkValidity()` có xác minh hostname/EKU/CRL không?** Không; chỉ kiểm tra thời hạn.
16. **Tại sao trong demo tắt revocation?** Để bộ fixture tạm không cần CRL/OCSP; hệ thống thực phải thiết lập chính sách revocation thích hợp.
17. **Một certificate được parse thành công bởi `CertificateFactory` có chắc được chấp nhận bởi `CertPathValidator` không?** Không, có thể fail critical, chain, signature, constraints, policy.
18. **Muốn dùng certificate cho HTTPS nên tự gọi một vài hàm KU/EKU không?** Không nên thay thế JSSE/TrustManager và hostname verification bằng checker đơn giản; phải dùng bộ xác minh giao thức đầy đủ.

## Tài liệu chuẩn và API chính thức

- [RFC 5280 — Internet X.509 PKI Certificate and CRL Profile: `4.2.1.3 Key Usage; `4.2.1.9 Basic Constraints; `4.2.1.12 EKU](https://www.rfc-editor.org/rfc/rfc5280.html)
- [X509Certificate (Java SE 21): getKeyUsage, getExtendedKeyUsage, getBasicConstraints](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/X509Certificate.html)
- [X509Extension: getCriticalExtensionOIDs, hasUnsupportedCriticalExtension, getExtensionValue](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/X509Extension.html)
- [CertificateFactory: parse X.509 certificate và CertPath](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/CertificateFactory.html)
- [CertPathValidator và PKIXParameters](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/CertPathValidator.html)
- [TrustAnchor](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/TrustAnchor.html)
- [X509CertSelector (lọc theo KU, EKU, constraints — không thay path validation)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/X509CertSelector.html)
- [JSSE Reference Guide — TLS validation và trust managers](https://docs.oracle.com/en/java/javase/21/security/java-secure-socket-extension-jsse-reference-guide.html)
- [keytool: quản lý và cấp chứng thư demo](https://docs.oracle.com/en/java/javase/21/docs/specs/man/keytool.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="x509-certificate-007">13.7 Phân biệt checkValidity, verify chữ ký chứng thư và kiểm tra độ tin cậy</a>

<details>
<summary>Xem chi tiết</summary>

# 13.7. Phân biệt checkValidity, verify chữ ký chứng thư và kiểm tra độ tin cậy

**Một chứng thư X.509 có thể còn hạn sử dụng, có chữ ký mật mã hoàn toàn hợp lệ, nhưng vẫn không đáng tin để kết nối đến máy chủ mà bạn định truy cập.** Đây là điểm cần hiểu trước khi dùng `java.security.cert.X509Certificate` trong Java.

Ba thao tác thường bị đánh đồng:

1. **`certificate.checkValidity()`**: kiểm tra **thời gian hiện tại** có nằm trong khoảng `notBefore`–`notAfter` của **một chứng thư cụ thể** không.
2. **`certificate.verify(issuerPublicKey)`**: kiểm tra **chữ ký số trên chính chứng thư đó** có hợp lệ đối với **public key được cung cấp** không.
3. **Kiểm tra độ tin cậy (certificate path validation / PKIX trust validation)**: xác định có **đường chứng thực hợp lệ từ chứng thư đích qua các CA trung gian tới một trust anchor đã được chấp nhận từ trước**, đồng thời thỏa chính sách về thời gian, ràng buộc CA, thuật toán, mục đích dùng, revocation và các giới hạn có liên quan hay không.

Khi đối tượng là máy chủ HTTPS, còn một yêu cầu **khác nữa**: chứng thư đã được xác minh phải đại diện cho **đúng tên máy chủ mà client mong muốn kết nối**, ví dụ `api.example.com`. Và trong một handshake TLS, phía server cần chứng minh có quyền dùng private key tương ứng theo giao thức. Do đó, cả ba thao tác kể trên đều **không nên được dùng riêng lẻ thay cho TLS/PKIX verification hoàn chỉnh**.

## 1. Mục tiêu và bản đồ ba tầng kiểm tra

Sau bài học, bạn có thể:

- Đọc `notBefore`, `notAfter` và áp dụng đúng `checkValidity()`/`checkValidity(Date)`.
- Dùng `verify(PublicKey)` đúng nghĩa là kiểm tra chữ ký X.509 bằng khóa của issuer, **không phải** kiểm tra chứng thư có đáng tin hay không.
- Hiểu vì sao chứng thư **self-signed** do kẻ tấn công tạo vẫn có thể vượt qua cả hai kiểm tra trên.
- Giải thích `TrustAnchor`, `KeyStore`/truststore, `CertPath`, `CertPathValidator`, `CertPathBuilder` và `PKIXParameters`.
- Phân biệt chain cryptographically valid, path trusted, certificate policy hợp lệ và application identity đúng.
- Xác định cách xử lý CA certificate, `BasicConstraints`, `KeyUsage`, `ExtendedKeyUsage`, `SubjectAlternativeName`, policy, revocation và hostname.
- Viết và chạy minh họa Java 17+ dựa trên truststore mặc định, cho thấy signature/time validity khác với trust root.
- Nhận diện các anti-pattern: trust-all, tự tin vào self-signature, tạo trust anchor từ certificate client gửi, bỏ hostname verification, tắt revocation không có đánh giá.
- Biết khi nào nên giao xác thực chứng thư cho **JSSE/HttpClient/TLS stack** thay vì viết một validator riêng.

### 1.1. Bảng phân biệt ngay từ đầu

| Câu hỏi | API hoặc cơ chế | Nó trả lời điều gì? | **Không** chứng minh |
| --- | --- | --- | --- |
| Chứng thư có nằm trong khoảng thời gian của nó? | `X509Certificate.checkValidity()` | Một ngày nằm trong `notBefore ≤ date ≤ notAfter` theo phép kiểm tra của API | Chữ ký, issuer, trust, hostname, quyền |
| Nội dung chứng thư được issuer ký hợp lệ? | `X509Certificate.verify(issuerPublicKey)` | Chữ ký trên `tbsCertificate` hợp lệ với khóa chỉ định | Khóa issuer có đáng tin, chuỗi CA đúng, chứng thư còn hạn hay hostname |
| Chứng thư đích có thể được tin theo cấu hình/policy? | `CertPathValidator("PKIX")` và `TrustAnchor`; hoặc trust manager trong TLS | Một đường PKIX thỏa các kiểm tra chứng thực tới anchor được tin | Tên dịch vụ HTTPS đúng nếu không kiểm tra danh tính endpoint, ứng dụng được cấp quyền |
| Có đúng server `api.example.com` không? | Endpoint identification/hostname validation trong TLS/JSSE | Certificate identity phù hợp với **tên client định gọi** | Người dùng/app có quyền truy cập mọi tài nguyên |
| Server có private key phù hợp với certificate không? | Chứng minh trong TLS handshake như `CertificateVerify` ở TLS 1.3 | Bằng chứng sở hữu khóa ký trong phiên và binding với handshake | Authorization nghiệp vụ, chống replay của request ứng dụng |
| Client có quyền thực hiện thao tác? | Authorization của ứng dụng | Principal đã xác thực có quyền với resource/action/tenant | Những bảo đảm này không tự đến từ `checkValidity`/`verify` |

Một câu ghi nhớ: **“Còn hạn” khác “chữ ký đúng”; “chữ ký đúng” khác “được tin”; “được tin” khác “đúng đối tượng và được phép”.**

## 2. Cấu trúc chứng thư X.509 tối thiểu cần hiểu

Một chứng thư X.509 thường có:

```text
Certificate
  tbsCertificate  (to-be-signed certificate)
    version
    serialNumber
    signature algorithm identifier
    issuer name
    validity
      notBefore
      notAfter
    subject name
    subjectPublicKeyInfo
      public key algorithm
      public key bytes
    extensions
      BasicConstraints
      KeyUsage
      ExtendedKeyUsage
      SubjectAlternativeName
      SubjectKeyIdentifier / AuthorityKeyIdentifier
      policies / name constraints / CRL or OCSP metadata...
  signatureAlgorithm
  signatureValue
```

Khi CA cấp certificate cho server, CA **ký nội dung `tbsCertificate`** bằng private signing key của CA. Server nhận certificate cùng public key được cấp; client có thể xác minh chữ ký bằng **public key của CA tương ứng** nếu có certificate issuer.

Chẳng hạn:

```text
Root CA (được lưu tin cậy từ trước)
   subject = Example Root CA
   public key = K_root_public
           |
           | ký certificate của Intermediate CA
           v
Intermediate CA
   issuer = Example Root CA
   public key = K_intermediate_public
           |
           | ký certificate của api.example.com
           v
Leaf / end-entity certificate
   issuer = Example Intermediate CA
   SAN = DNS:api.example.com
   public key = K_server_public
```

Để tin server, không đủ kiểm tra chữ ký của leaf bằng public key intermediate. Client phải kiểm tra tiếp **issuer intermediate**, chuỗi ràng buộc, anchor được tin, constraints và **đúng tên máy chủ**.

### 2.1. Self-issued, self-signed và trusted không đồng nghĩa

- **Self-issued:** subject name trùng issuer name. Điều này chỉ liên quan metadata tên; không tự chứng minh chữ ký tự ký.
- **Self-signed:** chữ ký certificate có thể được xác minh bằng **chính public key** của certificate (và trong cách dùng thông thường còn có subject/issuer phù hợp).
- **Trusted:** một chính sách tin cậy, truststore hoặc cấu hình out-of-band đã **chấp nhận** certificate/key đó làm anchor cho mục đích phù hợp.

Một root CA trong truststore thường tự ký; **kẻ tấn công cũng có thể tạo một root tự ký hợp lệ bằng khóa của họ**. Self-signature chỉ chứng tỏ sự nhất quán mật mã đối với public key đó, không chứng tỏ CA đáng tin.

### 2.2. Issuer DN khớp không đủ

Dù:

```text
leaf.issuer == intermediate.subject
```

vẫn chưa đủ. Hai CA khác nhau có thể dùng cùng tên (hoặc cùng tên qua lần thay khóa). Phải chọn đúng certificate/key ký, xác minh chữ ký và kiểm tra **toàn bộ đường** theo PKIX. Extensions `AuthorityKeyIdentifier`/`SubjectKeyIdentifier` có thể hỗ trợ path building, nhưng không thay chữ ký hoặc trust policy.

## 3. `checkValidity()` thực sự kiểm tra gì?

Java cung cấp:

```java
certificate.checkValidity();
certificate.checkValidity(someDate);
```

`checkValidity()` sử dụng thời điểm hiện tại theo đồng hồ hệ thống. `checkValidity(Date)` kiểm tra tại thời điểm người gọi chỉ định. Nếu chứng thư vượt qua, nghĩa là ngày xét **nằm trong cửa sổ thời gian `notBefore` đến `notAfter` của chính certificate**.

Các lỗi đặc trưng:
- `CertificateNotYetValidException`: thời điểm kiểm tra **trước `notBefore`**.
- `CertificateExpiredException`: thời điểm kiểm tra **sau `notAfter`**.

Ví dụ chỉ nhằm minh họa API (giả sử biến `cert` là `X509Certificate` đã parse):

```java
try {
    cert.checkValidity();
    System.out.println("Certificate is within its validity interval");
} catch (java.security.cert.CertificateExpiredException expired) {
    System.out.println("Certificate expired");
} catch (java.security.cert.CertificateNotYetValidException notYet) {
    System.out.println("Certificate is not yet valid");
}
```

Đây là **snippet phụ thuộc biến `cert`**, không phải toàn chương trình; chương trình tự chạy đầy đủ ở phần 10.

### 3.1. Đừng nhầm với việc xác định chứng thư đáng tin

`checkValidity()` **không**:
- Kiểm tra chữ ký số của certificate.
- Kiểm tra certificate có được CA nào ký hay không.
- Kiểm tra chain đi tới trust anchor đã cấu hình.
- Kiểm tra CA có `BasicConstraints: CA=true` hoặc `KeyUsage: keyCertSign`.
- Kiểm tra server name trong SAN có khớp hostname không.
- Kiểm tra chứng thư có bị thu hồi (revoked) không.
- Kiểm tra thuật toán ký hay độ dài khóa có phù hợp chính sách không.
- Kiểm tra certificate có mục đích `serverAuth` hay `clientAuth` phù hợp không.
- Chứng minh bên kia đang sở hữu private key tương ứng.

Có thể nói: **một certificate tự ký do Mallory tạo hôm nay với hiệu lực một năm sẽ vượt qua `checkValidity()` ngay lập tức**. Điều đó hoàn toàn không khiến Alice phải tin Mallory là `bank.example.com`.

### 3.2. Kiểm tra tại thời điểm khác

`checkValidity(Date)` hữu ích khi mô phỏng một thời điểm để test, nhưng thay ngày kiểm tra không tự biến một certificate đã expired thành hợp lệ cho giao thức production. Việc xác minh chữ ký lịch sử, timestamp và chính sách thời điểm ký có các yêu cầu riêng.

Ví dụ:

```java
Date reference = new Date();
cert.checkValidity(reference);
```

Với TLS đang hoạt động, client thường cần chứng thư hợp lệ theo chính sách thời điểm handshake. Nếu đồng hồ client sai, kết quả có thể sai; vận hành cần đồng bộ thời gian, không nới lỏng validation vô điều kiện.

### 3.3. Chứng thư CA cũng có hạn

Chỉ kiểm tra `leaf.checkValidity()` bỏ sót khoảng hiệu lực của **intermediate CA certificates**. PKIX path validation xét một chuỗi gồm nhiều certificate với các quy tắc cụ thể. Trust anchors có địa vị cấu hình đặc biệt và cách kiểm tra thời gian/constraints của anchor có thể khác các certificate trong path: đừng tự khẳng định “path validator luôn `checkValidity` root giống leaf”.

## 4. `verify(PublicKey)` thực sự kiểm tra gì?

`X509Certificate.verify(PublicKey key)` sử dụng **public key do người gọi cung cấp** để xác minh chữ ký trên certificate.

Đối với một leaf có issuer là intermediate:

```java
leaf.verify(intermediate.getPublicKey());
```

Đối với một intermediate do root ký:

```java
intermediate.verify(root.getPublicKey());
```

Đối với certificate tự ký:

```java
selfSignedRoot.verify(selfSignedRoot.getPublicKey());
```

Về mật mã, thao tác kiểm tra rằng `signatureValue` phù hợp với `tbsCertificate`, signature algorithm và public key cung cấp. Nó không tự hỏi “public key này được lấy từ đâu?”, “CA này có được phép ký chứng thư không?” hoặc “subject này có đúng người nhận không?”.

### 4.1. Chữ ký đúng vẫn có thể không đáng tin

Giả sử Mallory:
1. Sinh một RSA/EC/Ed25519 key pair riêng.
2. Tạo certificate tự ký cho `CN=api.example.com`, đặt `notBefore` là hôm qua và `notAfter` là năm sau.
3. Tự ký `tbsCertificate` bằng private key của mình.
4. Gửi certificate cho Alice.

Alice gọi:

```java
malloryCert.checkValidity();
malloryCert.verify(malloryCert.getPublicKey());
```

Cả hai phép kiểm tra **có thể thành công**. Nhưng không có gì ràng buộc public key Mallory với `api.example.com` theo trust policy của Alice. Nếu Alice chấp nhận sau hai dòng này, Alice đã bỏ qua **xác thực nguồn chứng thư** và có thể bị MITM.

**Lỗi nguy hiểm hơn:** ứng dụng đọc certificate do peer gửi rồi luôn gọi `verify(peerCertificate.getPublicKey())`. Với leaf được CA ký, thao tác đó thường thất bại vì leaf không tự ký; với certificate tự ký của kẻ tấn công, lại có thể thành công. Đây là sự đảo ngược logic kiểm tra tin cậy.

### 4.2. `verify` có kiểm tra thời gian không?

Không. Một certificate đã hết hạn vẫn có thể có chữ ký số hoàn toàn hợp lệ. Chữ ký mô tả nội dung certificate đã được một key ký, không quyết định certificate còn được phép dùng ở thời điểm hiện tại hay không.

Ngược lại, certificate còn hạn nhưng bị sửa `tbsCertificate` sau khi ký hoặc được kiểm tra bằng sai public key có thể bị `verify` từ chối.

### 4.3. `verify` có kiểm tra CA/KeyUsage không?

Không đủ. Nếu một certificate intermediate thực sự có chữ ký hợp lệ của một key, vẫn phải xác định issuer có được phép ký certificate theo `BasicConstraints`, `KeyUsage` và các ràng buộc path/policy khác.

Ví dụ:
- Một end-entity certificate có `BasicConstraints: CA=false` không thể tự động được dùng làm intermediate hợp lệ chỉ vì nó chứa public key có thể verify một chữ ký.
- Một chain có quá nhiều CA dưới một CA với `pathLenConstraint` có thể vi phạm policy mặc dù từng chữ ký riêng lẻ hợp lệ.
- Name constraints có thể cấm intermediate cấp certificate cho một miền tên cụ thể.

Đây là lý do **không tự làm path validation bằng vòng lặp `cert.verify(issuerKey)` rồi trả true**.

### 4.4. Chữ ký của certificate khác chữ ký trong TLS handshake

Xác minh chữ ký X.509 certificate xác định certificate đã được **issuer ký**. Nó không trực tiếp chứng minh server đang kết nối **giữ private key của leaf certificate**.

Trong TLS 1.3 dùng certificate authentication, thông điệp `CertificateVerify` chứng minh quyền sở hữu private signing key tương ứng trên ngữ cảnh handshake được protocol định nghĩa. `Finished` tiếp tục xác thực transcript bằng handshake secrets. Bởi vậy, việc tự gọi `leaf.verify(issuerPublicKey)` khi parse một PEM **không thay thế toàn bộ TLS handshake**.

## 5. Kiểm tra độ tin cậy là bài toán **path validation**

PKI dựa trên một quan hệ tin cậy cấu hình từ trước:

```text
                 TRUST CONFIGURATION
               (KeyStore / Trust Anchors)
                           |
                       Root CA
                       (trusted)
                           |
                      verifies/signs
                           |
                    Intermediate CA
                    (CA constraints)
                           |
                      verifies/signs
                           |
                      Leaf certificate
                      (server identity)
                           |
               Must additionally match:
                  requested hostname
                  intended key usage/EKU
                  application security policy
```

Trong Java, các đối tượng quan trọng:

- `KeyStore`: kho lưu certificate/key entries; truststore là cách thông dụng cấu hình các CA được tin.
- `TrustAnchor`: đại diện một root certificate hoặc CA name + public key được người vận hành **chủ động tin cậy**.
- `CertPath`: danh sách certificate **từ target/leaf hướng về issuer**, theo thứ tự mà provider mong đợi; thông thường **không bao gồm trust anchor**.
- `CertPathValidator` với thuật toán `PKIX`: kiểm tra một path đã có đối với cấu hình tin cậy và constraints.
- `CertPathBuilder`: tìm hoặc xây dựng một đường chứng thực từ certificate store/candidate chains/constraints.
- `PKIXParameters` / `PKIXBuilderParameters`: tập trust anchors, thời gian kiểm tra, certificate policies, revocation settings, checker/constraints khác.
- `X509TrustManager` / `X509ExtendedTrustManager`: thành phần JSSE thực hiện certificate-chain trust decisions trong TLS theo ngữ cảnh provider/session.

**Có ba bài toán khác nhau:** *tìm chain*, *xác minh chain*, và *đối chiếu certificate identity với dịch vụ mà ứng dụng định truy cập*. Một API không nhất thiết giải quyết cả ba.

### 5.1. Cấu trúc `CertPath` và trust anchor

Ví dụ chain:

```text
Certificate path (đưa cho validator):
    [leaf api.example.com, intermediate Example Issuer]

Trust anchor (cấu hình tin cậy bên ngoài path):
    Example Root CA
```

PKIX validator xác minh leaf bằng khóa intermediate và intermediate theo path/anchor tương ứng, đồng thời áp các ràng buộc được quy định.

**Không** đưa một certificate root từ chính gói tin của client vào:

```java
// Anti-pattern: root certificate comes only from untrusted network input.
new TrustAnchor(untrustedRootSentByPeer, null);
```

rồi tuyên bố đã có trust vì `CertPathValidator` pass. Làm vậy thực chất **tự tin một CA do đối thủ chọn**. Trust anchor phải có từ một truststore hoặc kênh cấu hình đã xác thực với policy phù hợp.

### 5.2. Điều mà PKIX có thể kiểm tra

Với cấu hình/implementation phù hợp, PKIX path validation xem xét:
- Chữ ký của các certificate trong chain và quan hệ với issuer/trust anchor.
- Thời hạn certificate trong path tại thời điểm kiểm tra.
- `BasicConstraints`, `KeyUsage` và khả năng certificate có được làm CA hay không.
- Path length constraints, name constraints và những extension được nhận diện/critical.
- Chứng thư/thuật toán/kiểu khóa có thỏa policy của validator/provider, các checker tùy chọn.
- Certificate policies và ràng buộc cụ thể khi cấu hình.
- Revocation qua CRL/OCSP hoặc cơ chế tương ứng **khi có bật và cấu hình theo policy**.

**Nhưng PKIX path validation thuần không tự biết ứng dụng muốn kết nối tới `api.example.com` hay `mail.example.com`.** Hostname/endpoint identification cần chính sách ứng dụng/TLS bổ sung.

### 5.3. Path xây dựng khác với path kiểm tra

`CertPathBuilder` có thể cần một tập candidate intermediate certificates, CA stores hoặc AIA retrieval theo provider/config. `CertPathValidator` được đưa một path đã xác định và áp luật validation.

Đừng mặc định JDK sẽ tự tải toàn bộ intermediate từ mạng cho bạn. Nếu thiếu intermediate và builder không được cấu hình nguồn/cách tải, một certificate hợp lệ về lý thuyết vẫn có thể không xây được chain.

Với cross-signing, có thể tồn tại nhiều đường tới những root khác nhau; path builder cần chọn đường phù hợp anchor/policy. So khớp issuer/subject strings không đủ để tự xây một path chính xác và an toàn.

## 6. Trust anchor không phải kết quả của `verify`

### 6.1. Trust anchor là quyết định tin cậy từ trước

Root CA trong truststore được hệ thống hoặc người quản trị đặt ở trạng thái đáng tin cho một phạm vi sử dụng. Đó là **quyết định vận hành/chính sách**, không phải hệ quả toán học của self-signature.

Một attacker có thể:
- Sinh cặp khóa và certificate X.509 tự ký.
- Đặt `notBefore`/`notAfter` hợp lệ.
- Đặt subject giống tên cơ quan có uy tín.
- Tự verify chữ ký bằng public key của mình.

Nhưng Alice không có lý do để tin certificate đó là trust anchor trừ khi có kênh phân phối/kiểm tra độc lập đáng tin.

### 6.2. Cấu hình truststore sai là một lỗi thực tế

Các anti-pattern:
- Load `cacerts` hoặc truststore rỗng, rồi nếu validation thất bại thì “tự thêm server certificate vào truststore”.
- Chấp nhận mọi self-signed certificate.
- Bắt ngoại lệ `CertificateException` rồi bỏ qua.
- Dùng một truststore toàn công ty nhưng vô tình cấp trust cho CA không đúng scope.
- Pin một public key lấy từ lần kết nối đầu tiên mà không có biện pháp xử lý MITM đầu tiên, nhưng lại tuyên bố tương đương xác thực CA.

Đối với CA nội bộ, có thể thêm CA certificate vào truststore riêng **sau khi được xác minh qua kênh quản trị an toàn**. Sau đó bắt tay vẫn phải kiểm tra chain, danh tính endpoint, key usages và rotation/revocation theo policy.

### 6.3. Cẩn thận với root do bên kia gửi kèm

TLS server có thể gửi một certificate chain và đôi khi gửi cả root. Bản sao root được truyền qua mạng **không trở thành trusted** chỉ vì server gửi nó. Client phải so khớp đường chứng thực với trust anchor đã có trong cấu hình, không “tự cấp quyền trust” cho root từ packet.

## 7. Hostname và danh tính dịch vụ: lớp khác với PKIX

Giả sử client gọi:

```text
https://payments.example.com
```

Server trình certificate có chain hoàn toàn hợp lệ tới CA công khai, nhưng `SubjectAlternativeName` chứa:

```text
DNS:files.example.com
```

Cả:
- `checkValidity()`,
- `verify(issuerPublicKey)`,
- và thậm chí **PKIX path validation** tới một trust anchor hợp lệ,

đều có thể pass, trong khi client **vẫn phải từ chối certificate cho `files.example.com`** vì ứng dụng mong đợi `payments.example.com`.

### 7.1. Expected identity phải do client xác định

Đừng lấy “expected hostname” từ một field không tin cậy do server tự khai rồi hỏi certificate có khớp hay không. Nếu client định nối `payments.example.com`, chính tên đó là reference identity từ target URL hoặc cấu hình dịch vụ đã xác thực.

Các nguyên tắc:
- DNS name thường được kiểm tra trong **SAN dNSName** theo chuẩn TLS/service identity được áp dụng.
- Kết nối tới IP literal cần xử lý quy tắc **iPAddress SAN**, không tự so sánh string CN hoặc reverse DNS để chấp nhận.
- Wildcard, IDNA và canonicalization phải tuân theo quy tắc thư viện/chuẩn; không viết `endsWith(".example.com")` tự chế.
- `CN` trong subject không nên được xem là giải pháp thay thế mặc định cho SAN hiện đại.
- SNI giúp server chọn certificate phù hợp, **không phải** bằng chứng rằng certificate đó khớp hostname hoặc đáng tin.

### 7.2. Endpoint identification trong JSSE

Khi dùng HTTPS client chuẩn, để TLS stack thực hiện certificate-chain validation và hostname validation. Ví dụ `java.net.http.HttpClient` của Java 17 theo cấu hình mặc định phù hợp dùng TLS verification; **đừng** tắt nó với các tùy chọn insecure.

Nếu tự dùng `SSLSocket` trong trường hợp đặc biệt, phải bảo đảm endpoint identification (ví dụ `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")` cho kết nối HTTPS), đúng expected host, trust manager và client/server mode. Việc trực tiếp gọi `X509TrustManager.checkServerTrusted(...)` trên một mảng certificate mà **không có ngữ cảnh endpoint** thường **không thay hostname validation**.

### 7.3. Các bước cho TLS 1.3 server authentication

Một sơ đồ khái niệm:

```text
Client wants: payments.example.com
   |
   v
TLS handshake receives certificate chain
   |
   +--> PKIX / trust manager checks:
   |      signatures, path, anchors, constraints, validity,
   |      revocation/policies according to configuration
   |
   +--> Endpoint identity check:
   |      SAN matches payments.example.com
   |
   +--> TLS handshake proof of possession / transcript:
   |      CertificateVerify and Finished as defined by TLS 1.3
   |
   +--> Security policy:
          key/algorithm rules, TLS version, application authorization
   |
   v
Only then treat channel as connected to expected server
```

**Một certificate hợp lệ không tự tạo một kênh TLS an toàn.** Còn phải bảo đảm handshake transcript, key agreement, record protection và identity theo giao thức.

## 8. Revocation: chứng thư “còn hạn” vẫn có thể bị thu hồi

Một certificate có thể được CA **thu hồi trước `notAfter`**, ví dụ do private key bị lộ, subject không còn quyền hoặc CA cấp sai.

`checkValidity()` chỉ xem cửa sổ thời gian; `verify(...)` kiểm tra chữ ký đã tồn tại; cả hai **không** tra cứu trạng thái thu hồi.

Cơ chế thường gặp:
- **CRL (Certificate Revocation List)**: danh sách chứng thư bị thu hồi do CA công bố và ký.
- **OCSP (Online Certificate Status Protocol)**: hỏi trạng thái certificate qua responder theo profile.
- **OCSP stapling** và các chính sách TLS liên quan, tùy client/server stack.
- **Short-lived certificates** hoặc private PKI policy giảm thời gian rủi ro, nhưng không tự giải quyết mọi trường hợp compromise.

### 8.1. `PKIXParameters.setRevocationEnabled(...)`

`PKIXParameters` có cờ kiểm tra revocation và các cơ chế `PKIXRevocationChecker`/`CertStore`, cấu hình/khả năng provider. **Bật một cờ không bảo đảm hệ thống đã có đầy đủ dữ liệu CRL/OCSP hoặc policy xử lý lỗi.** Ngược lại, gọi:

```java
params.setRevocationEnabled(false);
```

là **cố ý tắt** revocation check của luồng PKIX đó. Đoạn mã ví dụ trong phần 10 có dòng này **chỉ để trình diễn ngoại tuyến không phụ thuộc mạng**, nên tuyệt đối không lấy đó làm cấu hình production mặc định.

Hệ thống thật phải quyết định:
- Khi không truy vấn được OCSP/CRL, fail closed hay chấp nhận theo ngoại lệ cụ thể?
- Có giới hạn tuổi CRL/staple và caching policy không?
- Network egress từ trust validator được kiểm soát ra sao?
- Với CA nội bộ, hệ thống cấp chứng thư ngắn hạn/rotation và revoke ra sao?
- Quan sát sự cố và cảnh báo có bảo vệ dữ liệu nhận dạng/chứng thư không?

Nếu một nhóm hệ thống dùng mTLS với private CA, việc không cấu hình revocation và rotation có thể khiến chứng thư đã bị thu hồi thực tế vẫn được client chấp nhận cho tới `notAfter`.

## 9. Chính sách CA, leaf và certificate extensions

### 9.1. BasicConstraints

Một intermediate CA thường cần `BasicConstraints: CA=true`. Một leaf server certificate thường là `CA=false`. `pathLenConstraint` giới hạn số lượng CA không tự cấp dưới nó theo quy tắc chuẩn. `verify` không thay việc kiểm tra ràng buộc này.

### 9.2. KeyUsage

Một CA certificate thường có `keyCertSign` để được sử dụng ký certificate. Leaf có các key usage khác tùy scheme, chẳng hạn `digitalSignature` để chứng minh possession trong TLS 1.3. Không suy luận từ một chữ ký certificate hợp lệ rằng key usage được phép.

### 9.3. ExtendedKeyUsage (EKU)

Một certificate dành cho server TLS có thể cần mục đích `id-kp-serverAuth`; client certificate dùng mTLS có thể cần `id-kp-clientAuth`. Việc áp EKU cụ thể phụ thuộc protocol, policy và implementation. `checkValidity()` không biết service đang dùng certificate cho mục đích nào; `verify(...)` cũng không kiểm tra EKU thay bạn.

### 9.4. Critical extensions và name constraints

X.509 extensions đánh dấu **critical** cần được hiểu và xử lý theo PKIX. Validator không được âm thầm bỏ qua những critical extensions ảnh hưởng security policy. Name constraints của CA có thể giới hạn miền được cấp certificate; chỉ verify chữ ký từng bước dễ bỏ qua chúng.

### 9.5. Algorithm/key strength

Chữ ký toán học hợp lệ bằng một thuật toán đã yếu hoặc kích thước khóa không đủ không đồng nghĩa **được phép sử dụng hôm nay**. Hệ thống phải áp algorithm constraints của JDK/provider, TLS security policy, tổ chức và cập nhật khi tiêu chuẩn thay đổi. Không tự động fallback xuống MD5/SHA-1 hoặc tắt constraints để “chấp nhận chứng thư cũ”.

## 10. Java 17+: minh họa ba phép kiểm tra bằng truststore có sẵn

Đoạn chương trình sau là **một class đầy đủ có thể biên dịch và chạy với Java 17+**, không tải dữ liệu từ Internet và không ghi file.

Nó làm những điều có thể tái lập, sử dụng những CA certificates **đã nằm trong truststore mặc định** của Java runtime:

1. Tìm hai certificate CA **self-signed, còn hạn và có public keys khác nhau** trong trust manager được cấu hình mặc định.
2. Với root đầu tiên, gọi `checkValidity()` và `verify(root.getPublicKey())`.
3. Chứng minh root này hiện có trong danh sách trusted issuers, nhưng **không** có trong một trust set rỗng.
4. Thử dùng `CertPathValidator("PKIX")` xác minh certificate đầu tiên **chỉ với root thứ hai, không liên quan, làm trust anchor**: kết quả thất bại. Đây là tình huống minh họa một self-signature đúng vẫn **không nối tới anchor khác**.
5. Chỉ ra rằng chương trình **chưa hề kiểm tra hostname**. Đây không phải ví dụ hoàn chỉnh để xác thực một HTTPS server.

**Lưu ý môi trường:** chương trình giả định truststore mặc định có **ít nhất hai root CA self-signed hiện còn hạn với public keys phân biệt**. JDK thông thường có nhiều CA như vậy. Nếu môi trường dùng truststore tối giản/rỗng, chương trình báo lỗi rõ thay vì tự tải hoặc tự tin một root lạ. Thứ tự certificate trong `getAcceptedIssuers()` không cố định; demo không in subject cụ thể.

Tệp `X509ChecksDemo.java`:

```java
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class X509ChecksDemo {

    static X509TrustManager defaultTrustManager()
            throws GeneralSecurityException {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm()
        );
        factory.init((KeyStore) null); // Runtime's configured default trust store.

        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509TrustManager x509) {
                return x509;
            }
        }
        throw new GeneralSecurityException("No X509 trust manager");
    }

    static boolean selfSignedAndCurrentlyValid(X509Certificate cert) {
        try {
            cert.checkValidity();
            if (!cert.getSubjectX500Principal().equals(
                    cert.getIssuerX500Principal())) {
                return false;
            }
            cert.verify(cert.getPublicKey());
            return true;
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    static X509Certificate[] chooseRoots(X509TrustManager manager) {
        X509Certificate first = null;

        for (X509Certificate cert : manager.getAcceptedIssuers()) {
            if (!selfSignedAndCurrentlyValid(cert)) {
                continue;
            }
            if (first == null) {
                first = cert;
                continue;
            }
            if (!Arrays.equals(
                    first.getPublicKey().getEncoded(),
                    cert.getPublicKey().getEncoded())) {
                return new X509Certificate[] {first, cert};
            }
        }
        throw new IllegalStateException(
                "Need two distinct currently valid CA certificates"
        );
    }

    static boolean isInTrustSet(
            X509Certificate candidate,
            X509Certificate[] trusted
    ) throws CertificateEncodingException {
        for (X509Certificate c : trusted) {
            if (Arrays.equals(candidate.getEncoded(), c.getEncoded())) {
                return true;
            }
        }
        return false;
    }

    static boolean pkixValidWithUnrelatedAnchor(
            X509Certificate candidate,
            X509Certificate unrelated
    ) throws GeneralSecurityException {
        // Deliberately use this self-signed CA as path target,
        // with a different CA as the only configured trust anchor.
        // This is a negative trust-path test, not normal leaf validation.
        CertPath path = CertificateFactory.getInstance("X.509")
                .generateCertPath(List.of(candidate));

        PKIXParameters params = new PKIXParameters(
                Set.of(new TrustAnchor(unrelated, null))
        );
        // OFFLINE DEMO ONLY: intentionally disables revocation checking.
        params.setRevocationEnabled(false);

        try {
            CertPathValidator.getInstance("PKIX").validate(path, params);
            return true;
        } catch (CertPathValidatorException expected) {
            return false;
        }
    }

    public static void main(String[] args) throws Exception {
        X509TrustManager manager = defaultTrustManager();
        X509Certificate[] roots = chooseRoots(manager);
        X509Certificate selected = roots[0];

        selected.checkValidity();
        selected.verify(selected.getPublicKey());

        System.out.println("Certificate time validity: true");
        System.out.println("Self-signature verifies: true");
        System.out.println("Listed in configured trust anchors: " +
                isInTrustSet(selected, manager.getAcceptedIssuers()));
        System.out.println("Listed in empty trust store: " +
                isInTrustSet(selected, new X509Certificate[0]));
        System.out.println("PKIX validates against unrelated root: " +
                pkixValidWithUnrelatedAnchor(selected, roots[1]));
        System.out.println("This program checked hostname: false");
    }
}
```

Biên dịch và chạy:

```bash
javac X509ChecksDemo.java
java X509ChecksDemo
```

Stdout đã được kiểm tra bằng các API tương thích Java 17 (trên JDK có truststore đủ root phù hợp):

```text
Certificate time validity: true
Self-signature verifies: true
Listed in configured trust anchors: true
Listed in empty trust store: false
PKIX validates against unrelated root: false
This program checked hostname: false
```

### 10.1. Diễn giải kết quả đúng nghĩa

- `Certificate time validity: true`: `selected` nằm trong cửa sổ hiệu lực hiện tại. Nó không chứng minh điều gì về identity của một máy chủ.
- `Self-signature verifies: true`: chữ ký self-signed của root xác minh được bằng chính public key nó chứa; không có CA khác đứng ra bảo chứng chỉ từ phép toán này.
- `Listed in configured trust anchors: true`: root đã được **runtime cấu hình tin cậy từ trước**; điều đó khác với việc tự ký hợp lệ.
- `Listed in empty trust store: false`: nếu không có trust anchor được chấp thuận, self-signature không tự đưa certificate vào tập đáng tin.
- `PKIX validates against unrelated root: false`: một root khác dù bản thân cũng đáng tin trong runtime **không thể tùy tiện ký thay certificate này**. Path validation phụ thuộc **đúng chuỗi và đúng anchor**.
- `This program checked hostname: false`: chương trình không có expected service hostname; dù một CA root trusted, đây vẫn **không phải một bài kiểm tra TLS server**.

**Giới hạn của demo:** `isInTrustSet` chỉ kiểm tra membership trong một array để minh họa định nghĩa trust anchor; **đừng dùng nó làm certificate validation cho leaf**. `pkixValidWithUnrelatedAnchor` cố tình đưa một root self-signed thành path target để chứng minh kết quả thất bại; hệ thống bình thường sẽ kiểm tra **leaf → intermediates** tới root trusted.

### 10.2. Dấu hiệu chứng minh tách biệt time/signature/trust

Hãy nhìn ba giả thuyết:

```text
A. Certificate still within dates?
   -> checkValidity()          [certificate-local test]

B. Certificate signature matches supplied key?
   -> verify(issuerPublicKey)  [cryptographic signature test]

C. Certificate chain accepted by configured PKI?
   -> CertPathValidator/JSSE   [trust-policy/path test]
      + HTTPS hostname match   [endpoint identity test]
```

Một lỗi ở A, B hoặc C có thể xảy ra độc lập với những phần còn lại. Nối ba phương thức bằng toán tử boolean tự viết chưa thay thế mọi ràng buộc của PKIX/TLS.

## 11. Java PKIX: xác minh một chain thực khi đã có các certificates tin cậy

Giả sử ứng dụng **đã nhận và parse đúng** các biến:
- `leaf`: X.509 end-entity certificate của dịch vụ.
- `intermediate`: certificate CA trực tiếp ký leaf.
- `configuredTrustedRoot`: root CA lấy từ **cấu hình/truststore có quyền**, không lấy từ packet không tin cậy.

Đoạn sau là **snippet Java 17**, cần các biến trên và import tương ứng; không phải chương trình độc lập:

```java
// Imports for this snippet:
// import java.security.cert.*;
// import java.util.*;

CertPath path = CertificateFactory.getInstance("X.509")
        .generateCertPath(List.of(leaf, intermediate));
// Trust anchor is kept OUT of the certificate path.

Set<TrustAnchor> approvedAnchors =
        Set.of(new TrustAnchor(configuredTrustedRoot, null));

PKIXParameters params = new PKIXParameters(approvedAnchors);
// params may also define evaluation date, revocation configuration,
// trusted CertStores, policy constraints, and additional checkers.

// Do NOT disable revocation here for a real security policy.
// Configure PKIXRevocationChecker / CRL / OCSP facilities as required.

CertPathValidator validator = CertPathValidator.getInstance("PKIX");
validator.validate(path, params);

// This success means the supplied path satisfied the configured
// PKIX trust validation. It is NOT by itself HTTPS hostname validation.
```

Để tránh hiểu sai:
- Nếu leaf được root ký trực tiếp, path thường là `List.of(leaf)`, trust anchor vẫn đặt riêng.
- Nếu có hai intermediates, đưa đúng thứ tự leaf → intermediate gần leaf → intermediate cấp trên, **không** trộn root vào path.
- Nếu chain chưa có đủ intermediates, phải xây dựng đúng path qua `CertPathBuilder`/certificate stores theo chính sách; không tự điền issuer bằng cách nối subject string.
- Không tự tạo `configuredTrustedRoot` từ certificate attacker vừa gửi. Mọi kết quả phía sau sẽ mất ý nghĩa nếu anchor không có nguồn tin cậy.
- PKIX có các quy tắc về BasicConstraints, key usage, policies, extensions và revocation được cấu hình; không thay chúng bằng hai lệnh `leaf.verify(...)` và `intermediate.verify(...)`.
- Sau validation, còn cần xác định **đúng tên dịch vụ/identity**, TLS handshake, application scope và authorization.

### 11.1. Cách parse certificate PEM hoặc DER trong Java

Một `CertificateFactory` X.509 có thể đọc certificate từ stream:

```java
// Snippet, requires java.nio.file.* and java.security.cert.* imports.
try (java.io.InputStream input =
             java.nio.file.Files.newInputStream(
                     java.nio.file.Path.of("server-cert.pem"))) {
    X509Certificate leaf = (X509Certificate)
            CertificateFactory.getInstance("X.509")
                    .generateCertificate(input);
}
```

Đây chỉ là **đọc và parse certificate**, không kiểm tra trust. Không được dùng `CertificateFactory.generateCertificate` như bằng chứng server đáng tin: parser có thể parse một certificate do attacker tạo đúng cấu trúc ASN.1/X.509.

Khi xử lý PEM bundles, nhiều certificate có thể được trình cùng nhau; cần chính sách xử lý thứ tự và path building. Certificate file và private key file là những loại tài liệu khác nhau, không phải root certificate được công khai thì private signing key cũng được công khai.

## 12. Điều Java HTTPS client nên làm trong production

Khi mục tiêu là gọi HTTPS, giải pháp tốt nhất thường **không** phải một class tự gọi `checkValidity` rồi `verify`, mà là dùng **JSSE/HTTP client chuẩn**:

```java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class SecureHttpExample {
    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("https://example.com/")
        ).GET().build();

        HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString()
        );
        System.out.println("HTTP status: " + response.statusCode());
    }
}
```

Đây là **chương trình độc lập** nhưng cần có kết nối mạng, DNS và server hoạt động; **không nêu stdout cố định**. Mục đích là cho thấy có thể giao nhiệm vụ certificate chain/hostname/TLS handshake cho client chuẩn thay vì tự làm một validator. Chính sách trust thực tế còn phụ thuộc JDK, cấu hình TLS, truststore tùy chỉnh và các thiết lập hệ thống.

Không làm:

```java
// ANTI-PATTERNS: not safe for production.
// TrustManager.checkServerTrusted(...) { /* do nothing */ }
// HostnameVerifier.verify(host, session) { return true; }
// Ignore SSLHandshakeException and continue insecurely.
```

Nếu gặp `SSLHandshakeException`/`PKIX path building failed`, hãy tìm nguyên nhân: chain thiếu intermediate, truststore chưa có CA đúng, chứng thư hết hạn, tên máy chủ không khớp, ciphers/protocol policy hay revocation. Không “sửa” bằng cách tắt SSL verification.

### 12.1. Cẩn trọng với custom `SSLContext` và truststore

Nếu ứng dụng cần tin CA nội bộ:
- Import certificate CA **đã được xác thực out-of-band** vào truststore riêng hoặc dùng trust manager chuẩn đã cấu hình đúng.
- Kiểm tra rằng **chỉ đúng CA** và phạm vi được phép nằm trong truststore; không đưa tất cả certificate client gửi vào truststore.
- Vẫn giữ endpoint identification/hostname verification.
- Định nghĩa cách rotate CA, expire, revoke và quản lý truststore phiên bản.
- Kiểm thử bằng certificate chain thật và negative test wrong hostname/untrusted root.

Nếu tự thay `TrustManager` bằng implementation chấp nhận mọi certificate, bạn có thể đang **bỏ cả chain validation** mà không nhận ra.

## 13. Những tình huống phân biệt ba mức kiểm tra

### Tình huống A — Self-signed của người lạ

`notBefore` hôm qua, `notAfter` năm sau; chữ ký tự ký hợp lệ; không có trong truststore.

- `checkValidity()`: **pass**.
- `verify(cert.getPublicKey())`: **pass**.
- PKIX đối với truststore không chứa root đó: **fail**.
- Kết luận: **không được tin để đại diện server** chỉ vì time và signature đúng.

### Tình huống B — Certificate do CA trusted ký nhưng đã hết hạn

- `checkValidity()`: **fail**, ví dụ `CertificateExpiredException`.
- `verify(correctIssuerPublicKey)`: có thể **pass**; chữ ký vẫn tồn tại.
- PKIX thông thường tại thời điểm hiện tại: **fail** khi leaf ngoài validity interval.
- Kết luận: chữ ký đúng không “gia hạn” certificate.

### Tình huống C — Leaf hợp lệ, intermediate hết hạn

- `leaf.checkValidity()`: có thể pass.
- `leaf.verify(intermediate.getPublicKey())`: có thể pass.
- PKIX path validation: có thể **fail** vì certificate trong chain không hợp lệ ở thời điểm kiểm tra (trừ các điều kiện/ngoại lệ chính sách được chuẩn hóa).
- Kết luận: không kiểm tra mỗi leaf rồi quên intermediate.

### Tình huống D — Chữ ký không hợp lệ hoặc sai issuer key

- `checkValidity()`: vẫn có thể pass.
- `verify(wrongPublicKey)`: **fail**.
- PKIX path tương ứng: không được chấp nhận.
- Kết luận: validity không chứng minh certificate chưa bị sửa hoặc đúng signer.

### Tình huống E — Chain hợp lệ nhưng sai hostname

- `checkValidity()`: pass.
- `verify(issuerPublicKey)`: pass.
- PKIX path tới trusted root: pass.
- Certificate SAN chứa `files.example.com`, nhưng client gọi `payments.example.com`: **TLS identity check fail**.
- Kết luận: trusted chain không đồng nghĩa đúng peer.

### Tình huống F — Certificate bị thu hồi giữa vòng đời

- `checkValidity()`: có thể pass.
- `verify(issuerPublicKey)`: có thể pass.
- PKIX/TLS với **revocation policy thực thi đầy đủ**: phải xử lý revoked state theo policy.
- Kết luận: thời gian và chữ ký không thay trạng thái thu hồi.

### Tình huống G — Root tự ký và được pin an toàn

- Root certificate có self-signature đúng và được **cấu hình tin cậy độc lập** sau khi kiểm chứng key/fingerprint.
- Path tới root có thể được chấp nhận nếu đáp ứng constraints và chính sách.
- Khi dùng cho HTTPS, vẫn phải kiểm tra SAN hostname và TLS proof of possession.
- Kết luận: root là trust anchor nhờ **quyết định trust có căn cứ**, không nhờ tính chất “tự ký”.

### Tình huống H — Certificate đúng server nhưng application không được cấp quyền

- TLS handshake/certificate validation/hostname đều đúng.
- User gửi request tới resource không được cấp phép.
- Application phải trả authorization denial.
- Kết luận: trust vào server certificate không trao mọi quyền nghiệp vụ cho client.

### Tình huống I — Certificate chain bị kẻ tấn công thay bằng chain họ tự tạo

- Nếu ứng dụng dùng root từ **payload** làm `TrustAnchor`, path có thể “đúng” dưới root attacker.
- Nếu trust anchor chỉ từ config đã xác thực, chain attacker không nối được vào root phù hợp.
- Kết luận: nguồn trust anchor là yếu tố **bất biến trong threat model**.

### Tình huống J — CSR và certificate signing

Một CSR có chữ ký hợp lệ chứng minh thông tin request nhất quán với private key tương ứng, nhưng không tự chứng minh CA đã cấp certificate tin cậy. CSR parsing/verification không đồng nghĩa certificate path validation. Đây là ví dụ tương tự cho sự khác nhau giữa **cryptographic signature verification** và **trust decision**.

## 14. Các anti-pattern cần nhận biết

| Lỗi | Vì sao sai | Cách sửa |
| --- | --- | --- |
| Chỉ gọi `cert.checkValidity()` rồi “trusted” | Chỉ kiểm tra cửa sổ thời gian | PKIX/trust manager và expected identity |
| Chỉ gọi `cert.verify(cert.getPublicKey())` | Có thể chỉ xác minh self-signature của attacker | Dùng issuer key đúng, validate chain tới anchor tin cậy |
| Ghép các `verify` theo issuer string | Bỏ qua CA constraints, policies, revocation, path issues | Dùng `CertPathValidator/Builder` |
| Root tự ký được gửi trong TLS chain nên tự tin | Attacker có thể gửi root của họ | Trust anchors từ config độc lập |
| Đặt `new TrustAnchor(receivedRoot, null)` | Tin vào nguồn không đáng tin | Dùng pinned/configured CA hợp lệ |
| PKIX pass nhưng bỏ qua SAN hostname | Tin nhầm certificate cho domain khác | Endpoint identification |
| CN khớp nên bỏ qua SAN khác | Quy tắc hiện đại ưu tiên SAN và matching theo chuẩn | Dùng TLS hostname verification chuẩn |
| Dùng `HostnameVerifier` luôn trả true | Mất kiểm tra đúng server | Dùng default verifier |
| TrustManager luôn accept | Mất PKIX authentication, MITM | Dùng trust manager chuẩn |
| Tắt revocation vô thời hạn mà không có policy | Không phát hiện compromised cert sớm | Thiết kế CRL/OCSP/short-lived policy |
| Khóa CA key usage sai nhưng `verify` pass | Signature đúng không cấp quyền làm CA | Kiểm tra BasicConstraints/KeyUsage qua PKIX |
| Leaf certificate CA=false được dùng ký intermediate | Sai mục đích dùng | Không thay PKIX bằng signature-only checks |
| Bỏ ngoại lệ `CertificateExpiredException` | Hết hạn vẫn được chấp nhận | Kiểm soát validity theo policy |
| Bỏ qua `SSLHandshakeException` | Có thể đang bị MITM/cấu hình PKI sai | Chẩn đoán chain/trust/name đúng |
| Certificate chain valid = server sở hữu private key | Chữ ký CA không chứng minh handshake PoP | TLS certificate proof |
| PKIX pass = user được phép gọi API | Trust server khác application authorization | Phân quyền riêng |
| Log toàn certificate/key material và header nhạy cảm | Lộ dữ liệu và khó kiểm soát retention | Chỉ log metadata cần thiết theo policy |

## 15. Checklist kiểm thử cho Java certificate validation

### 15.1. Unit tests

- [ ] Certificate còn hạn: `checkValidity()` pass; expired/notYetValid được phân biệt đúng.
- [ ] Thử `checkValidity(Date)` với thời điểm trước/sau để xác minh logic thời gian, không tự bỏ qua lỗi.
- [ ] `verify(correctIssuerPublicKey)` pass, `verify(wrongIssuerPublicKey)` fail.
- [ ] Certificate self-signed mới tạo nhưng **chưa trust** không được chấp nhận cho server production.
- [ ] Certificate parser X.509 không được nhầm với trust validation.
- [ ] Validator từ chối chain sai ký, thiếu intermediate, vượt path length, CA=false ở vị trí issuer.
- [ ] Critical extensions/KeyUsage/EKU/name constraints được xử lý theo policy áp dụng.
- [ ] Trust anchor được kiểm soát từ cấu hình chứ không copy tùy tiện từ client request.

### 15.2. Integration tests HTTPS/mTLS

- [ ] Valid chain + correct SAN hostname được chấp nhận theo TLS policy.
- [ ] Valid chain + **wrong hostname** bị từ chối.
- [ ] Valid dates + self-signed untrusted bị từ chối.
- [ ] Valid leaf + expired intermediate bị từ chối theo path policy.
- [ ] Revoked certificate bị từ chối khi revocation policy yêu cầu.
- [ ] Server không chứng minh private key phù hợp trong handshake bị từ chối.
- [ ] Expired/replaced root và key rotation được kiểm tra theo hệ thống truststore.
- [ ] Custom truststore không tắt hostname verification và không “trust all”.
- [ ] Client certificate cho mTLS được map sang đúng principal, tenant/role; không cấp quyền chỉ dựa vào TLS succeeded.
- [ ] Thuật toán/chứng thư yếu bị từ chối theo security policy hiện hành.

### 15.3. Operational QA

- [ ] Truststore CA list, lịch cập nhật, quyền thay đổi và audit rõ ràng.
- [ ] Clock/NTP đồng bộ; có cảnh báo certificate sắp hết hạn.
- [ ] Có phương án cấp/rotate certificate và khôi phục nếu intermediate bị thay.
- [ ] Revocation, certificate lifetime và quy tắc soft-fail/hard-fail được thống nhất với threat model.
- [ ] Log lỗi TLS tránh lộ private key, session key, token, request body và plaintext; có correlation ID.
- [ ] Không dùng flag “insecure TLS” hoặc trust-all chỉ để khắc phục một sự cố tạm thời.
- [ ] CA nội bộ và CA công khai có phạm vi tin cậy được phân chia phù hợp.

## 16. Câu hỏi tự kiểm tra

**1. `checkValidity()` pass có chứng minh certificate được CA trusted ký không?**  
Không. Nó chỉ kiểm tra `notBefore/notAfter` của certificate tại một thời điểm.

**2. `leaf.verify(issuer.getPublicKey())` pass có chứng minh issuer nằm trong truststore không?**  
Không. Phép toán chỉ xác minh chữ ký bằng key bạn cung cấp. Key đó có được tin hoặc đủ quyền làm CA không là bài toán khác.

**3. Vì sao certificate tự ký của attacker có thể pass cả `checkValidity` và `verify`?**  
Attacker tự chọn thời hạn và tự ký bằng private key tương ứng với public key trong certificate. Không có trust anchor đã cấu hình hoặc identity binding từ bên ngoài.

**4. Nếu một CA root thật có self-signature đúng, nó được tin chỉ nhờ `verify(root.getPublicKey())`?**  
Không. Trust dựa trên cấu hình/kiểm tra out-of-band; phép tự ký không tự trao thẩm quyền.

**5. Đường chứng thực leaf → intermediate → root nên đưa những phần tử nào vào `CertPath`?**  
Thường là `List.of(leaf, intermediate)`; root được khai báo riêng dưới dạng `TrustAnchor` đã có nguồn tin cậy.

**6. PKIX path validation pass thì HTTPS hostname tự đúng không?**  
Không. Phải so expected service name với certificate identity theo quy tắc endpoint identification; TLS client chuẩn kết hợp các bước này.

**7. Vì sao không lấy root từ gói tin server gửi để tạo `TrustAnchor`?**  
Kẻ tấn công có thể tạo và gửi root của họ; tạo anchor trực tiếp từ input không tin cậy khiến kết quả validator không phản ánh trust từ client.

**8. Certificate còn hạn và chữ ký đúng nhưng bị thu hồi. Hai API nào vẫn có thể pass?**  
`checkValidity()` và `verify(...)` có thể vẫn pass. Revocation cần cơ chế/policy bổ sung.

**9. `BasicConstraints: CA=false` ở certificate intermediate ảnh hưởng điều gì?**  
Nó không được dùng làm CA trung gian theo PKIX hợp lệ; chữ ký toán học đúng không cấp quyền ký chứng thư.

**10. `verify` certificate là bằng chứng server đang giữ private key trong TLS handshake?**  
Không. Certificate signature do issuer tạo; TLS `CertificateVerify`/handshake authentication có vai trò chứng minh private key phù hợp và bind phiên.

**11. Nếu server certificate có SAN `files.example.com` nhưng client muốn `payments.example.com` thì kết luận?**  
Dù chain trusted và time/signature đúng, endpoint identity sai nên phải reject theo chính sách HTTPS.

**12. Có nên bỏ qua lỗi chứng thư trong môi trường production bằng một `TrustManager` chấp nhận mọi certificate không?**  
Không. Nó làm mất authentication kênh và tạo MITM risk; phải sửa truststore, chain, SAN, thời gian hoặc cấu hình hợp lệ.

## 17. Sơ đồ ghi nhớ

```text
                  X.509 CERTIFICATE FROM PEER
                             |
                             v
                checkValidity()?
                   /             \
               time OK       time NOT OK
                 |              reject
                 v
        verify(issuerPublicKey)?
             /              \
        signature OK      mismatch
             |              reject
             v
       PKIX path validation:
       - chain signatures / CA rules
       - constraints / validity / policies
       - trusted configured root
       - revocation as configured
             |
             v
       Endpoint identity:
       - expected HTTPS DNS/IP
       - SAN / protocol matching
             |
             v
       TLS handshake:
       - proof of key possession
       - transcript / Finished
             |
             v
       App authorization:
       - principal, tenant, resource, action
             |
             v
         ACCEPT ONLY IF POLICY SATISFIED

  WARNING: Diagram is a conceptual dependency list.
  Correct PKIX/JSSE validates many elements together;
  do not reimplement the protocol as ad-hoc sequential checks.
```

**Kết luận:** `checkValidity` kiểm tra **thời gian**, `verify` kiểm tra **chữ ký certificate với một public key**, còn “đáng tin” yêu cầu **đường PKIX tới trust anchor được cấu hình từ trước** và các ràng buộc chứng thư/ứng dụng phù hợp. Với HTTPS còn phải xác định **đúng hostname, TLS proof of possession và authorization**. Đừng để việc hai phương thức Java không ném exception bị hiểu nhầm thành “certificate đã được xác thực đáng tin”.

### Tài liệu tham khảo

- RFC 5280 — *Internet X.509 Public Key Infrastructure Certificate and CRL Profile*: https://www.rfc-editor.org/rfc/rfc5280
- RFC 9525 — *Service Identity in TLS* (định danh DNS/IP, name matching): https://www.rfc-editor.org/rfc/rfc9525
- RFC 8446 — *The Transport Layer Security (TLS) Protocol Version 1.3*: https://www.rfc-editor.org/rfc/rfc8446
- RFC 6960 — *X.509 Internet Public Key Infrastructure Online Certificate Status Protocol - OCSP*: https://www.rfc-editor.org/rfc/rfc6960
- Java SE 17, `X509Certificate`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/X509Certificate.html
- Java SE 17, `Certificate` / `verify(PublicKey)`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/Certificate.html
- Java SE 17, `CertPathValidator`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertPathValidator.html
- Java SE 17, `CertPathBuilder`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertPathBuilder.html
- Java SE 17, `PKIXParameters` / `TrustAnchor`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/PKIXParameters.html
- Java SE 17, `TrustManagerFactory`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/TrustManagerFactory.html
- Java SE 17, `SSLParameters` / endpoint identification: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLParameters.html
- Java SE 17, `HttpClient`: https://docs.oracle.com/en/java/javase/17/docs/api/java.net.http/java/net/http/HttpClient.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="x509-certificate-008">13.8 CSR PKCS#10, phản hồi CA và chuỗi chứng thư trong quy trình keytool</a>

<details>
<summary>Xem chi tiết</summary>

# 13.8 CSR PKCS#10, phản hồi CA và chuỗi chứng thư trong quy trình keytool

Một ứng dụng Java muốn dùng chứng thư X.509 thật cho TLS, mTLS hoặc ký số thường không thể dừng ở bước `keytool -genkeypair`. Lệnh đó có thể tạo **private key + chứng thư tự ký ban đầu** trong KeyStore, nhưng chứng thư được một tổ chức khác tin cậy phải đi qua **yêu cầu cấp chứng thư CSR (PKCS#10)**, quá trình **CA thẩm định và ký**, nhận **certificate reply**, rồi cài lại đúng **certificate chain** vào alias chứa private key ban đầu.

Bài này trình bày luồng hoàn chỉnh từ **khóa trong PKCS#12 → CSR PKCS#10 → CA ký → trust anchor → nhập phản hồi vào cùng alias → kiểm chain bằng Java 17+**, đồng thời giải thích chain có intermediate CA, SAN, key usage, truststore, xử lý lỗi và quản trị production. Ví dụ sử dụng **CA tự tạo chỉ trong phòng thực hành**, không giả làm chứng thư công khai được mọi trình duyệt tin cậy.

## 1. Kết quả cần đạt

Sau khi học, bạn có thể:

1. Giải thích **CSR PKCS#10** gồm những gì, không gồm những gì, và vì sao CSR có chữ ký nhưng **chưa phải certificate đã được CA ký**.
2. Phân biệt private key, public key, CSR, **CA-issued leaf certificate**, intermediate certificates, root trust anchor và chuỗi chứng thư.
3. Dùng `keytool` để tạo một PKCS#12 identity, xuất CSR, xem nội dung CSR, tạo certificate reply từ CA demo, import root và install reply vào **đúng alias**.
4. Hiểu vì sao alias chứa `PrivateKeyEntry` phải được giữ nguyên khi thay self-signed certificate bằng certificate đã được cấp.
5. Dùng Java 17+ đọc lại `KeyStore`, xác minh alias/chain, SAN, quan hệ issuer, public/private key và validate path bằng **PKIX**.
6. Nhận ra giới hạn của ví dụ: CA demo, password dòng lệnh, PKIX revocation bị tắt **chỉ để demo**, và một bài kiểm SAN không thay TLS hostname verifier.
7. Khắc phục các lỗi phổ biến `Failed to establish chain from reply`, `Public keys in reply and keystore don't match`, nhập reply nhầm alias, thiếu intermediate hoặc tin sai CA.

## 2. Các đối tượng trong một quy trình cấp chứng thư

| Đối tượng | Nội dung cốt lõi | Độ nhạy / nơi thường nằm |
| --- | --- | --- |
| **Private key** | Khóa riêng RSA/EC/... để tạo chữ ký hoặc thực hiện thao tác private key theo thuật toán | **Bí mật**, nằm trong KeyStore/HSM/KMS; không gửi cho CA |
| **Public key** | Khóa công khai tương ứng với private key | Nằm trong CSR và leaf certificate |
| **CSR PKCS#10** | Subject, SubjectPublicKeyInfo, các attributes/extension requests và **chữ ký của bên yêu cầu** | Thường có thể gửi CA; không mang private key |
| **CA-issued certificate** | Chứng thư X.509 chứa thông tin subject, public key, issuer, serial, validity, extensions và **chữ ký của CA** | Công khai; chỉ đáng tin sau khi kiểm đường dẫn và chính sách |
| **Intermediate certificate** | Chứng thư của CA trung gian do CA cấp trên ký, thường có quyền CA theo constraints | Công khai; cần cho việc dựng đường dẫn |
| **Root CA / trust anchor** | Chứng thư hoặc public-key trust anchor được cấu hình tin cậy ngoài băng | Public, nhưng việc **chọn tin** phải được bảo vệ bằng chính sách |
| **Certificate reply** | Chứng thư leaf do CA cấp, có thể kèm các intermediate/chain dưới nhiều định dạng | Công khai; dùng để **thay chain ở alias private key** |
| **Identity keystore** | Kho lưu private key cùng chain phục vụ chứng minh danh tính | Bảo vệ cẩn thận |
| **Truststore** | Kho trust anchors hoặc CA/peer cert đã được chấp nhận theo chính sách | Chỉ chứa cert công khai trong mô hình thông dụng; kiểm nguồn import |

**Ba thông điệp phải nhớ:** (1) CSR không chứa private key; (2) CA có thể ký chứng thư chỉ khi quyết định **cho phép subject/định danh** theo chính sách của mình; (3) nhận được file certificate **không tự khiến nó được trust**.

### 2.1. Sơ đồ tổng thể

~~~text
                         MÁY CHỦ / NGƯỜI XIN CẤP
   +-----------------------------------------------------------+
   | KeyStore "app.p12"                                         |
   | alias "app-server": PrivateKeyEntry                         |
   |     private key  [GIỮ BÍ MẬT]                              |
   |     self-signed cert tạm thời                              |
   +-----------------------------------------------------------+
               | keytool -certreq  (dùng private key ký CSR)
               v
          CSR PKCS#10 ----- gửi CSR, không gửi private key -----> CA
                                                               |
                                          kiểm tên/domain/chính sách
                                          thẩm định CSR + ký leaf X.509
                                                               |
               <---------- phản hồi leaf + chain (khi có) ------+
               |
               | nhập CA certificate từ nguồn ĐÃ XÁC THỰC
               | nhập signed leaf vào cùng alias "app-server"
               v
   +-----------------------------------------------------------+
   | KeyStore "app.p12"                                         |
   | alias "app-server": PrivateKeyEntry                         |
   |     cùng private key                                       |
   |     chain[0] = CA-issued leaf                              |
   |     chain[1] = CA trung gian hoặc root (tùy đường dẫn)      |
   |     ...                                                     |
   | alias "demo-ca": TrustedCertificateEntry (CA demo)         |
   +-----------------------------------------------------------+
               |
               v
        Java load + kiểm PKIX/chain; JSSE khi dùng TLS
~~~

Sơ đồ là trường hợp mô phỏng. Ở production, CA, identity store và truststore thường có các vai trò/quyền truy cập khác nhau; private key có thể **không xuất được** khỏi HSM. Root trust anchor thường được cấu hình ở phía **bên kiểm chứng (relying party)** chứ không phải chỉ cần nhập vào keystore của server rồi mọi client sẽ tin.

## 3. CSR PKCS#10 chính xác là gì?

**PKCS#10** (RFC 2986) quy định cấu trúc **CertificationRequest**. Nói đơn giản, CSR chứa:

~~~text
CertificationRequest
  certificationRequestInfo
    version
    subject (X.500 Distinguished Name)
    subjectPublicKeyInfo (public key + algorithm)
    attributes (có thể chứa extensionRequest)
  signatureAlgorithm
  signature (chữ ký bằng private key tương ứng public key trên CSR)
~~~

Chữ ký ở CSR là **bằng chứng bên xin cấp đang sở hữu private key tương ứng** khi tạo yêu cầu (proof of possession trong phạm vi kỹ thuật chữ ký); nó **không xác nhận** người đó có quyền sở hữu hostname/domain, tổ chức hoặc tài sản được khai báo. Thẩm định danh tính/domain/IP và áp chính sách issuance thuộc trách nhiệm **CA**.

Một số sự thật quan trọng:

- CSR **không chứa private key**. Không gửi `.p12`, file key, password hoặc token/HSM secret cho CA chỉ vì CA yêu cầu CSR.
- CSR **không phải certificate X.509 đã có issuer tin cậy**. Chữ ký bên xin cấp trong CSR khác chữ ký CA trên chứng thư được phát hành.
- Các trường `Subject DN` và attributes như `extensionRequest` có thể gồm **requested SAN, KeyUsage, ExtendedKeyUsage**; CA có thể chấp nhận, sửa, từ chối hoặc bỏ qua theo chính sách.
- Với HTTPS hiện đại, **DNS name/IP phải có trong Subject Alternative Name (SAN)** của leaf theo quy tắc client/chuẩn liên quan. Chỉ đặt `CN` đẹp mà không có SAN phù hợp không đủ.
- CSR thường là **DER (nhị phân ASN.1)** hoặc **PEM** có nhãn `-----BEGIN CERTIFICATE REQUEST-----` (text Base64 bọc DER). PEM chỉ là **cách biểu diễn**, không phải một loại yêu cầu mật mã khác.
- Một CSR ứng với một public key nhất định. Nếu muốn CA response gắn với **private key ở alias cũ**, public key trong reply phải khớp public key của khóa đó.

### 3.1. CSR khác certificate như thế nào?

| Điểm so sánh | CSR PKCS#10 | Chứng thư X.509 đã cấp |
| --- | --- | --- |
| Người ký | Người sở hữu **private key yêu cầu** | **CA issuer** |
| Mục đích | Yêu cầu CA cấp certificate cho public key + subject | Ràng buộc public key với identity theo chính sách CA |
| Có serial và validity do CA cấp | Chưa | Có |
| Có issuer của CA | Không theo nghĩa certificate đã phát hành | Có issuer DN và chữ ký CA |
| SAN/usage | Có thể là **yêu cầu extension** | Extension thực tế mà CA **quyết định phát hành** |
| Dùng ngay làm TLS server certificate | **Không** | Có thể, sau khi đủ chain, policy, key, thời hạn, hostname và trust validation |
| Có private key | **Không** | **Không**; private key giữ ngoài cả hai artifact |

Đừng suy luận rằng file có chữ `CERTIFICATE` trong tên đồng nghĩa chứa private key. Thông thường `.crt`/`.cer` mang certificate công khai; `.csr` mang yêu cầu; `.p12` có thể chứa private key, certificate chain và các entry khác tùy cách đóng gói. **Không kiểm nguồn chỉ bằng phần mở rộng file.**

## 4. Chain of trust: leaf, intermediate CA, root

Trong PKI phân cấp điển hình:

~~~text
Root CA (trust anchor do bên xác minh cấu hình)
    |
    | ký
    v
Intermediate CA (basicConstraints CA=true, keyCertSign)
    |
    | ký
    v
Leaf certificate (app.example.test, SAN DNS, EKU serverAuth)
    |
    | public key của leaf phải khớp private key trong app.p12
    v
TLS server/ứng dụng dùng private key để chứng minh sở hữu
~~~

Một certificate path hợp lệ thường cần:

1. Chữ ký leaf được xác minh bằng public key của intermediate CA (hoặc root nếu không có intermediate).
2. Chữ ký intermediate (và các CA tiếp theo) dẫn về một **trust anchor đã được chọn từ trước**.
3. Kiểm hạn hiệu lực, thuật toán/cỡ khóa theo chính sách, `basicConstraints`, `keyUsage`, `pathLenConstraint` và những constraint liên quan.
4. Kiểm **mục đích sử dụng**: ví dụ TLS server cần `ExtendedKeyUsage serverAuth` khi EKU được khai báo; leaf có SAN khớp hostname mà client thực sự kết nối.
5. Kiểm trạng thái thu hồi/chính sách (CRL/OCSP hoặc cơ chế của hệ thống) khi được yêu cầu; không mặc định rằng mọi CA hoặc ứng dụng đều xử lý như nhau.

**Trust anchor không được “tự chứng minh là trusted” nhờ self-signature.** Root có thể tự ký, nhưng thứ làm nó đáng tin là việc public key/certificate được đặt vào tập trust anchors bằng **quy trình phân phối đáng tin và chính sách**. Kẻ tấn công cũng có thể tự ký certificate của họ.

### 4.1. Thứ tự trong certificate chain

Trong `KeyStore.PrivateKeyEntry`, chain thường được xem như:

~~~text
chain[0] = End-entity / leaf certificate của private key
chain[1] = Issuing intermediate CA (nếu có), hoặc root (nếu root ký trực tiếp)
chain[2] = CA cấp trên, nếu có ...
~~~

**Không đặt root ở `chain[0]`** và không nhầm alias trust anchor với alias private key. Đối với TLS, server thường gửi **leaf và intermediate(s)**; root thường **không cần gửi** vì client đã có root trong truststore, tùy giao thức/triển khai. Nếu server chỉ gửi leaf mà không có intermediate và client không thể tự lấy intermediate, việc dựng đường dẫn có thể thất bại.

**Ví dụ của bài:** Root demo ký leaf trực tiếp → chain có 2 phần tử ở KeyStore: **leaf → root**. Production thường có thêm intermediate, số lượng có thể là 3 hoặc nhiều hơn.

### 4.2. Chain được tin từ đâu?

`keytool -importcert` có thể **xây certificate chain dựa trên các CA certificates đã biết** hoặc dựa trên chain được gửi trong reply. Tuy nhiên, việc import thành công và certificate reply có chữ ký CA chưa nói rằng certificate này sẽ được **mọi client công cộng tin**. Bên dựa vào chứng thư phải tự có trust anchor phù hợp trong truststore/chính sách của họ.

Không nhập “CA root” từ email hoặc cùng kênh mạng chưa được xác minh rồi coi đó là chứng cứ độc lập: attacker có thể gửi **root giả và reply giả phù hợp với nhau**. Trong ví dụ local lab, bạn tự tạo CA nên biết nguồn của nó; production phải đối chiếu certificate fingerprint, công cụ CA và quy trình tin cậy độc lập.

## 5. KeyStore và alias trong luồng CSR → reply

Đối với JDK, lệnh tạo key pair ban đầu thường tạo một **`PrivateKeyEntry`** trong PKCS#12:

~~~text
app.p12:
  alias = app-server
  PrivateKeyEntry
    private key = RSA private key
    certificate chain = self-signed placeholder (lúc mới tạo)
~~~

Sau khi CSR được ký, thao tác đúng là **cài reply trên alias `app-server` đang chứa private key**, thay **certificate chain** nhưng **giữ khóa riêng cũ**. Không tạo key pair mới chỉ để import reply, nếu không bạn sẽ có public/private key không khớp.

| Alias/lệnh | Điều xảy ra |
| --- | --- |
| `-genkeypair -alias app-server` | Tạo PrivateKeyEntry với private key và certificate tự ký ban đầu |
| `-certreq -alias app-server` | Tạo CSR ký bởi private key của **cùng alias** |
| `-importcert -alias demo-ca -file ca.crt` | Với alias CA mới, thêm `TrustedCertificateEntry` chứa CA cert (demo) |
| `-importcert -alias app-server -file app-issued.crt` | Vì `app-server` đã là key entry, lệnh xử lý file như **certificate reply**, xác minh khớp khóa và thay chain |
| `-list -v -alias app-server` | Kiểm entry type, chain length, leaf subject, issuer, SAN |
| `-list -v -alias demo-ca` | Kiểm trusted certificate entry; không có private key |

**Lưu ý:** Nếu import certificate leaf vào một alias hoàn toàn **mới** thay vì alias có private key, công cụ có thể tạo `TrustedCertificateEntry` chứ **không cài leaf vào PrivateKeyEntry**. Khi TLS server cần private key và chain, đó là lỗi thiết kế.

### 5.1. Store password và key password

- `-storepass`: password khi mở/lưu KeyStore theo provider.
- `-keypass`: password bảo vệ private key entry khi provider/type hỗ trợ.
- Một số triển khai PKCS12 yêu cầu key password **khớp store password**; demo dùng cùng giá trị `changeit-demo`.
- Lỗi password có thể biểu hiện khi `-certreq` đọc private key hoặc `-importcert` sửa chain, không chỉ lúc `-list`.

Trong production, **đừng đưa password vào terminal history, process listing, CI logs hoặc source**. Bài lab cố tình dùng literal để có thể chạy lặp trong thư mục thử nghiệm tách biệt. Hệ thống thật dùng secret manager/callback, quyền truy cập file/HSM và chính sách rotation phù hợp.

## 6. Thực hành end-to-end với `keytool`: tạo CA demo và cấp CSR

Tất cả lệnh sau đã được thử với **JDK 17+ trên thư mục tạm rỗng**. Chạy từng lệnh trong một **thư mục thực hành mới**; không chạy trong thư mục có keystore production, vì tên file được dùng lại.

Kịch bản: Tạo CA `demo-ca`, tạo identity server `app-server` cho DNS `app.example.test`, xuất CSR và nhận leaf cert do CA demo ký, import CA và reply. Đây là **phòng thực hành**, không phải hệ thống CA có quy trình thẩm định domain thực tế.

### Bước 1 — Tạo root CA tự ký cho lab

~~~bash
keytool -genkeypair -alias demo-ca -keyalg RSA -keysize 2048 -dname "CN=Training Demo Root,O=Learning,C=VN" -validity 365 -ext "bc=ca:true" -ext "ku=keyCertSign,cRLSign" -keystore ca.p12 -storetype PKCS12 -storepass changeit-demo -keypass changeit-demo -noprompt
~~~

- `-genkeypair` tạo private/public key pair và certificate tự ký ban đầu.
- `-ext "bc=ca:true"` khai báo `BasicConstraints CA=true`, điều cần có với vai trò CA.
- `-ext "ku=keyCertSign,cRLSign"` dành `KeyUsage` cho việc ký certificate và CRL.
- `-dname` là Subject DN của **CA**, không phải server.
- Key RSA 2048 và thời gian 365 ngày chỉ dùng để demo; CA production phải tuân thủ tiêu chuẩn, chính sách, cách giữ khóa riêng, bảo vệ online/offline và thời hạn riêng.

**Không dùng `ca.p12` demo làm CA production**: private key CA thử nghiệm chỉ được bảo vệ bằng password mẫu; CA thật cần chính sách issuance, audit, phân quyền, backups và thường HSM.

### Bước 2 — Xuất certificate công khai của CA

~~~bash
keytool -exportcert -alias demo-ca -keystore ca.p12 -storepass changeit-demo -rfc -file ca.crt
~~~

File `ca.crt` là **certificate public**, không có private key. `-rfc` xuất dạng PEM, dễ truyền và xem. Kiểm thông tin certificate nếu cần:

~~~bash
keytool -printcert -file ca.crt
~~~

Trong production, **phải so fingerprint SHA-256/nguồn phân phối CA qua kênh đã xác thực** trước khi tin certificate, chứ không tự đặt tin cậy vì file tên `ca.crt`.

### Bước 3 — Tạo identity PKCS12 và self-signed placeholder

~~~bash
keytool -genkeypair -alias app-server -keyalg RSA -keysize 2048 -dname "CN=app.example.test,O=Learning,C=VN" -validity 30 -ext "san=dns:app.example.test" -keystore app.p12 -storetype PKCS12 -storepass changeit-demo -keypass changeit-demo -noprompt
~~~

Lúc này alias `app-server` chứa **PrivateKeyEntry** và chain self-signed tạm. Certificate tạm **không phải leaf đã được CA demo ký**; nó chỉ cho phép `keytool` lưu identity/khóa ban đầu. `CN` đặt tên cho mẫu; HTTPS/JSSE thực tế phải kiểm **SAN DNS** theo chuẩn và chính sách TLS.

Nếu bạn chạy lại `-genkeypair` trên cùng alias khi đã tồn tại, `keytool` có thể từ chối hoặc dẫn đến yêu cầu xử lý alias; đừng xóa private key hiện hữu và sinh key mới khi đang chờ reply từ CSR cũ.

### Bước 4 — Tạo CSR PKCS#10 từ đúng alias

~~~bash
keytool -certreq -alias app-server -file app.csr -rfc -keystore app.p12 -storepass changeit-demo -ext "san=dns:app.example.test" -ext "ku=digitalSignature,keyEncipherment" -ext "eku=serverAuth"
~~~

- `-certreq` ký CSR bằng private key trong `app.p12` của alias `app-server`.
- `-file app.csr -rfc` tạo CSR dạng PEM. Đừng nhầm file này với certificate issuer trả về.
- `-ext san=dns:app.example.test` yêu cầu SAN dạng DNS.
- `ku` là yêu cầu **KeyUsage**, `eku=serverAuth` là yêu cầu **ExtendedKeyUsage** cho TLS server. CA phải quyết định cuối cùng theo chính sách; không được cấp quyền chỉ vì CSR đề xuất.
- Với TLS 1.3, `digitalSignature` là usage quan trọng cho certificate key ký handshake; `keyEncipherment` được đưa vào lab cho ví dụ RSA rộng hơn, không có nghĩa TLS 1.3 sử dụng RSA key transport.

Xem CSR:

~~~bash
keytool -printcertreq -file app.csr -v
~~~

Bạn có thể thấy Subject, algorithm, public key, signature và extension requests. Nội dung cụ thể phụ thuộc JDK/provid­er và thông tin bạn khai báo; đừng hard-code fingerprint động vào test.

**An toàn:** Có thể gửi CSR cho CA vì nó không có private key. Tuy nhiên, CSR vẫn chứa danh tính/tên máy/chi tiết tổ chức, nên chia sẻ qua đúng kênh của CA và không đưa thông tin nhạy cảm không cần thiết.

### Bước 5 — CA demo thẩm định (mô phỏng) và ký leaf certificate

~~~bash
keytool -gencert -alias demo-ca -infile app.csr -outfile app-issued.crt -rfc -keystore ca.p12 -storepass changeit-demo -validity 30 -ext "san=dns:app.example.test" -ext "ku=digitalSignature,keyEncipherment" -ext "eku=serverAuth"
~~~

Lệnh dùng private key của **demo CA** để ký certificate cho public key trong CSR.

**Điểm quyết định:** Ở demo ta cho CA cấp SAN một cách tường minh qua `-ext`. **CA production không được chỉ lấy SAN/Subject tự khai trong CSR rồi ký vô điều kiện.** CA phải kiểm quyền đối với domain/tổ chức, lựa chọn certificate profile, validate yêu cầu, đặt serial, thời hạn, extensions và ghi dấu audit theo tiêu chuẩn/policy.

`app-issued.crt` là **CA-issued leaf certificate**, không phải private key. Trong demo root CA ký trực tiếp leaf, nên chưa có intermediate. Với CA thật, phản hồi có thể là leaf đơn lẻ, **PKCS#7 chain** hoặc certificate bundle có intermediate(s).

### Bước 6 — Import root CA demo thành trusted certificate entry

~~~bash
keytool -importcert -alias demo-ca -file ca.crt -keystore app.p12 -storepass changeit-demo -noprompt
~~~

- `demo-ca` là alias **mới**, không phải `app-server`.
- Lúc này `app.p12` có thể lưu `TrustedCertificateEntry` cho root demo. Vì root được tạo **ngay trong lab**, ta có thể chủ động đặt nó làm trust anchor demo.
- `-noprompt` bỏ xác nhận tương tác; **không dùng để tự động tin CA chưa được xác minh** trong production.
- Trước khi nhập một CA certificate thực, phải đối chiếu fingerprint/nguồn cung cấp CA bằng kênh đáng tin, đặt quyền đọc/ghi hợp lý.

Tại sao nhập CA trước reply? `keytool` cần đủ thông tin về issuer chain và trust để dựng/kiểm đường dẫn certificate reply. Trong lab chỉ có root→leaf nên root đủ cho chain 2 phần tử. Với intermediate, có thể phải import certificate của intermediate hoặc cung cấp toàn bộ chain đúng format; phải kiểm chain sau khi cài.

### Bước 7 — Cài certificate reply vào **cùng alias private key**

~~~bash
keytool -importcert -alias app-server -file app-issued.crt -keystore app.p12 -storepass changeit-demo -noprompt
~~~

Đây là bước quan trọng nhất:

1. `keytool` tìm **PrivateKeyEntry** có alias `app-server`.
2. Nó kiểm tra public key trong certificate reply có khớp private key hiện có theo provider/khả năng kiểm tra.
3. Nó kiểm đường dẫn/chứng chỉ issuer cần thiết cho việc cài reply, rồi thay certificate chain của **đúng private-key entry**.
4. **Private key vẫn ở alias `app-server`**, không phải một trusted certificate alias mới.

Sau khi thành công, output lab của `keytool` cho biết certificate reply đã được cài vào KeyStore.

**Không làm sai:**

~~~bash
# SAI VỀ MỤC ĐÍCH: alias mới không nối vào private key "app-server".
keytool -importcert -alias issued-copy -file app-issued.crt -keystore app.p12 -storepass changeit-demo -noprompt
~~~

Đây chỉ là **lệnh đối chiếu minh họa lỗi logic**, không chạy nó trong bài thực hành. Alias `issued-copy` không có private key nên không thay chain của `app-server`; có thể tạo certificate-only entry hoặc dẫn tới các hành vi provider khác nhau, nhưng không tạo identity đúng.

### Bước 8 — Xem KeyStore sau khi nhập reply

~~~bash
keytool -list -v -alias app-server -keystore app.p12 -storetype PKCS12 -storepass changeit-demo
keytool -list -v -alias demo-ca -keystore app.p12 -storetype PKCS12 -storepass changeit-demo
~~~

Đối với lab vừa tạo:

- `app-server` phải là **PrivateKeyEntry**, certificate chain length **2**.
- `chain[0]` là leaf cấp cho `app.example.test` với SAN `DNSName: app.example.test`.
- `chain[1]` là root CA demo `Training Demo Root`.
- `demo-ca` là trusted certificate entry, chỉ có certificate; không chứa private key CA vì đó là **CA certificate công khai được export**.
- Private key của **demo CA** vẫn chỉ ở **`ca.p12`**, không được copy sang `app.p12`.

Nếu giá trị thực không giống, đừng bỏ qua. Kiểm file đúng, alias đúng, issuer, SAN, chain và private/public key trước khi đưa vào ứng dụng.

## 7. Java 17+: đọc lại chain, kiểm SAN, chữ ký và PKIX

Đoạn sau là **chương trình Java 17+ độc lập**, chạy sau **tám bước `keytool` ở phần 6** trong cùng thư mục có `app.p12`. Không cần thư viện ngoài JDK. Nó thực hiện những phép kiểm minh họa có ích mà không in private key hay password.

Lưu đúng tên **`CsrChainDemo.java`**:

~~~java
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class CsrChainDemo {
    public static void main(String[] args) throws Exception {
        // Mật khẩu mẫu CHỈ cho lab. Production lấy từ secret manager.
        char[] password = "changeit-demo".toCharArray();
        try {
            KeyStore store = KeyStore.getInstance("PKCS12");
            try (InputStream in =
                    Files.newInputStream(Path.of("app.p12"))) {
                store.load(in, password);
            }

            String alias = "app-server";
            String caAlias = "demo-ca";
            boolean privateEntry = store.entryInstanceOf(
                    alias, KeyStore.PrivateKeyEntry.class);
            boolean caTrusted = store.entryInstanceOf(
                    caAlias, KeyStore.TrustedCertificateEntry.class);
            KeyStore.PrivateKeyEntry entry =
                    (KeyStore.PrivateKeyEntry) store.getEntry(
                            alias, new KeyStore.PasswordProtection(password));

            Certificate[] chain = entry.getCertificateChain();
            X509Certificate leaf = (X509Certificate) chain[0];
            X509Certificate root =
                    (X509Certificate) store.getCertificate(caAlias);

            boolean issuerMatches = leaf.getIssuerX500Principal()
                    .equals(root.getSubjectX500Principal());
            boolean orderMatches = chain.length == 2
                    && MessageDigest.isEqual(chain[1].getEncoded(),
                            root.getEncoded());
            boolean sanContainsHost = leaf.getSubjectAlternativeNames()
                    .stream().anyMatch(field ->
                            field.get(0).equals(2)
                            && "app.example.test".equals(field.get(1)));

            // Chứng minh private key trong entry khớp public key
            // của certificate leaf bằng cách sign/verify dữ liệu giả.
            byte[] message =
                    "csr-chain-proof".getBytes(StandardCharsets.UTF_8);
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(entry.getPrivateKey());
            signer.update(message);
            byte[] signed = signer.sign();
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(leaf.getPublicKey());
            verifier.update(message);
            boolean keyMatchesCert = verifier.verify(signed);

            leaf.checkValidity();
            root.checkValidity();

            // CertPath không đưa trust anchor vào list chứng thư cần
            // kiểm. Lab root ký leaf trực tiếp => path chỉ có leaf.
            CertPath path = CertificateFactory.getInstance("X.509")
                    .generateCertPath(List.of(leaf));
            PKIXParameters parameters = new PKIXParameters(
                    Set.of(new TrustAnchor(root, null)));
            parameters.setRevocationEnabled(false); // CHỈ DEMO!
            CertPathValidator.getInstance("PKIX")
                    .validate(path, parameters);

            System.out.println("KeyStore type        = " + store.getType());
            System.out.println("PrivateKeyEntry      = " + privateEntry);
            System.out.println("Trusted CA entry     = " + caTrusted);
            System.out.println("Chain length         = " + chain.length);
            System.out.println("Issuer matches CA    = " + issuerMatches);
            System.out.println("Chain order correct  = " + orderMatches);
            System.out.println("Leaf SAN DNS correct = " + sanContainsHost);
            System.out.println("Private matches cert = " + keyMatchesCert);
            System.out.println("PKIX path valid      = true");
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
~~~

Biên dịch và chạy với JDK 17+:

~~~bash
javac CsrChainDemo.java
java CsrChainDemo
~~~

**Đầu ra đã kiểm chứng** sau khi thực hiện đầy đủ chuỗi lệnh `keytool` (trên PKCS12 mới):

~~~text
KeyStore type        = PKCS12
PrivateKeyEntry      = true
Trusted CA entry     = true
Chain length         = 2
Issuer matches CA    = true
Chain order correct  = true
Leaf SAN DNS correct = true
Private matches cert = true
PKIX path valid      = true
~~~

### 7.1. Giải thích chính xác từng phép kiểm

- **`PrivateKeyEntry = true`:** Alias `app-server` giữ private key và certificate chain; reply không bị import thành một trusted certificate-only alias.
- **`Trusted CA entry = true`:** Alias `demo-ca` chứa certificate được import như một trust entry; nó **không đưa private key của CA** từ `ca.p12` vào `app.p12`.
- **`Chain length = 2`:** Demo có root ký leaf trực tiếp; production có intermediate thường dài hơn. Phép kiểm này là **kỳ vọng cố định riêng cho lab**.
- **`Issuer matches CA = true`:** Subject của root trùng Issuer DN của leaf; đây chỉ là so sánh **tên DN**, **không thay kiểm chữ ký**. Bất kỳ attacker nào cũng có thể tự tạo certificate mang cùng DN.
- **`Chain order correct = true`:** Byte của certificate `chain[1]` trùng với root certificate được lấy từ alias `demo-ca` trong lab.
- **`Leaf SAN DNS correct = true`:** Kiểm X.509 SAN có DNS name đúng mẫu. Một phép tìm chuỗi trong SAN **không thay đầy đủ thuật toán hostname verification của HTTPS**, vốn còn quy định wildcard, IDNA, IP, chuẩn so khớp và cách lấy host kết nối.
- **`Private matches cert = true`:** Dùng private key trong `app-server` để ký một thông điệp test, public key trong leaf để xác minh. Đây là phép kiểm có ý nghĩa hơn việc so alias hoặc so Subject DN.
- **`PKIX path valid = true`:** `CertPathValidator("PKIX")` kiểm path leaf → **trust anchor root được chọn tường minh** trong cấu hình demo. Ví dụ không tự động chọn mọi CA trên máy, không chứng minh hostname client và **đã cố ý tắt revocation** chỉ cho lab.

**Không biến code lab thành TLS trust manager production**: Chuỗi chứng thư tin cậy thật cần policy, hostname verification của client, kiểm thu hồi khi bắt buộc, certificate transparency/pinning theo threat model, bảo vệ danh tính CA và lựa chọn trust anchor tin cậy. Chương trình demo không mở HTTPS, không làm mTLS handshake và không chứng minh CA này được các trình duyệt công cộng công nhận.

### 7.2. Vì sao PKIX CertPath chỉ chứa leaf ở demo?

Trong `PKIXParameters`, `TrustAnchor` đại diện cho **điểm tin cậy ngoài path**. Root đã được nêu là trust anchor, nên khi root ký leaf trực tiếp, list certificate cần validate là `List.of(leaf)`, không thêm root vào list.

Khi có intermediate:

~~~java
// Mảnh ghép minh họa; intermediate phải được lấy/kiểm nguồn
// và root phải là TrustAnchor được phép tin.
CertPath path = CertificateFactory.getInstance("X.509")
        .generateCertPath(List.of(leaf, intermediate));
~~~

Thứ tự path **leaf → intermediate**, còn root nằm trong tập `TrustAnchor`. Đoạn này không phải chương trình độc lập vì `leaf` và `intermediate` cần được nạp. Việc path validator thành công phụ thuộc extensions, chữ ký, basic constraints, trust anchor, thời hạn và policy.

## 8. Nếu có intermediate CA: chain dài hơn và cách import

Trong production, root private key thường không ký leaf trực tiếp. CA tổ chức phân tầng:

~~~text
Trust anchor: Example Root CA   [trusted ở client/truststore]
      |
      v
Issuing Intermediate CA        [CA=true, keyCertSign; root ký]
      |
      v
app.example.test leaf          [CA=false, SAN, serverAuth; intermediate ký]
~~~

**Điểm khác so với lab** là **CA ký CSR leaf** thường là intermediate, không phải root. Để import đúng:

1. Xác định identity alias đã tạo CSR (ví dụ `app-server`) và kiểm public key của leaf reply **khớp private key đang có**.
2. Nhận **leaf certificate** và toàn bộ **intermediate chain** do CA cung cấp qua kênh đúng.
3. Xác minh root trust anchor bằng nguồn đáng tin; lưu trong truststore phù hợp và/hoặc import vào nơi `keytool` cần để dựng chain theo provider.
4. Cài intermediate certificates theo quy tắc và định dạng mà `keytool`/provider hỗ trợ (hoặc cung cấp certificate reply dạng chain/PKCS#7 được nhận diện); **đừng giả định leaf-only file luôn đủ**.
5. Import phản hồi vào **alias private key gốc**. Sau đó kiểm chain length và thứ tự leaf → intermediate → ... root (nếu root được lưu trong chain).
6. Kiểm issuer/subject linking, chữ ký, basic constraints CA, key usage, key path length, SAN/EKU leaf và path đến trust anchor. Đừng chỉ đếm chain length.

**Quan trọng:** `KeyStore.getCertificateChain` có thể chứa root hoặc chỉ các issuer certificates tùy provider/cách cài. TLS server **thường không cần gửi root**; việc client tìm được root là do truststore, không phải do root nằm trong reply. Không viết logic production chỉ dựa vào `chain.length == 3` mà bỏ kiểm chữ ký và trust anchor.

### 8.1. CA bundle và PKCS#7

Một CA có thể trả:

| File | Nội dung thường gặp | Cần xử lý |
| --- | --- | --- |
| `app-issued.crt` / `.cer` | Một X.509 leaf, PEM hoặc DER | Cài vào alias private key sau khi issuer chain có thể dựng |
| `intermediate.crt` | X.509 của CA trung gian | Đưa vào chain building/trust workflow như yêu cầu, **không tự coi mọi intermediate là root trusted** |
| `root.crt` | Root public certificate | Chỉ đặt làm trust anchor sau xác minh ngoài băng |
| `.p7b` / PKCS#7 | Tập certificate hoặc signed-data certificate chain, thường **không** mang private key | Có thể được `keytool -importcert` dùng làm reply tùy content/provider |
| `.p12` / PKCS#12 | Có thể chứa private key và chain hoặc chỉ certificates, tùy được export như thế nào | **Không mặc định import đè identity** nếu đang giữ private key gốc; kiểm contents/type trước |

PEM có dạng:

~~~text
-----BEGIN CERTIFICATE-----
... Base64 X.509 DER ...
-----END CERTIFICATE-----
~~~

CSR PEM có nhãn khác:

~~~text
-----BEGIN CERTIFICATE REQUEST-----
... Base64 PKCS#10 DER ...
-----END CERTIFICATE REQUEST-----
~~~

**Không gửi PKCS12 chứa private key cho CA nếu quy trình chỉ yêu cầu CSR.** Không tự ý gộp certificates sai thứ tự rồi nghĩ file mới đã có chain hợp lệ. Các công cụ hỗ trợ nhập bundle khác nhau; kiểm đáp ứng của `keytool -list -v` và thực hiện validation thực tế.

### 8.2. Intermediate không phải luôn là trust anchor

Một intermediate certificate có `CA=true` và có chữ ký của root nhưng **không tự động được tin** chỉ vì được server gửi. Nó là chứng cứ để xây **certificate path**, còn trust anchor cuối phải nằm trong chính sách trust của bên xác minh. Nếu ứng dụng tùy ý thêm mọi intermediate nhận được vào root truststore, có thể vô tình mở rộng phạm vi tin cậy.

Trường `BasicConstraints CA=true` chỉ nói một certificate **được tuyên bố có vai trò CA**; quyền lực chứng thực phải đến từ **chuỗi chữ ký hợp lệ và trust anchor được chấp nhận**. Không đánh đồng bit CA=true với “CA được tất cả máy tin”.

## 9. Các extension X.509 trong CSR và certificate reply

CSR có thể **đề xuất**, CA **phê duyệt/phát hành**. Mỗi extension có ý nghĩa mật mã/vận hành riêng:

| Extension | Trên leaf TLS server | Trên CA root/intermediate |
| --- | --- | --- |
| **Subject Alternative Name (SAN)** | DNS/IP của endpoint; bên client thực hiện matching theo chính sách | Có thể liên quan name constraints, không phải nơi tự cấp hostname cho leaf |
| **BasicConstraints** | Thường CA=false hoặc không phải CA | Phải CA=true để chứng nhận CA và đáp ứng path constraints |
| **KeyUsage** | Ví dụ digitalSignature cho TLS dùng chữ ký; keyEncipherment chỉ khi profile/giao thức cần | keyCertSign cho ký certificate, cRLSign nếu ký CRL |
| **ExtendedKeyUsage (EKU)** | serverAuth cho TLS server, clientAuth cho mTLS client tùy mục đích | Thường khác leaf, phụ thuộc policy và path validation |
| **Subject Key Identifier / Authority Key Identifier** | Hỗ trợ nhận diện khóa/issuer khi xây chain | Hỗ trợ nối issuer/subject key, không thay kiểm chữ ký |
| **Certificate Policies / NameConstraints** | Tùy CA/profile | Có thể hạn chế không gian cấp chứng thư hoặc mục đích CA |
| **CRL Distribution Points / AIA** | Gợi ý nơi tra thu hồi/CA issuer | Hỗ trợ chain building và kiểm thu hồi theo thực thi |

**Đừng chỉ nhìn CN:** Tên chứng thư server phải được kiểm theo **SAN** phù hợp với hostname kết nối, xử lý DNS/IP, wildcard và IDNA theo giao thức. `CN=app.example.test` trong demo không phải nguồn duy nhất/đủ dùng cho hostname validation.

**Đừng dựa vào `-ext` trong CSR để khẳng định certificate đã có extension đó.** Sau CA ký, kiểm **certificate leaf thực tế** bằng:

~~~bash
keytool -printcert -file app-issued.crt -v
~~~

CA có thể bỏ, chỉnh hoặc thay đổi các requested extensions. Đó là một phần của CA policy chứ không phải bug của PKCS#10.

## 10. CA response có được “tin” ngay khi nhận không?

**Không.** Certificate reply là đầu ra của CA, nhưng bên triển khai phải xác minh nhiều lớp:

1. **Đúng yêu cầu:** Public key trong leaf tương ứng private key sinh CSR; alias private-key entry đúng, không đổi key pair giữa chừng.
2. **Đúng identity:** SAN/subject và mục đích sử dụng được cấp đúng hồ sơ đã duyệt.
3. **Đúng issuer:** Leaf do CA dự kiến cấp, chữ ký và chain hợp lệ tới trust anchor được chọn.
4. **Đúng thời gian/constraints:** Validity, basic constraints, key usages, EKU, signature algorithm, key size, policies.
5. **Đúng nguồn và luồng:** CA account, giao thức issuance, fingerprint/serial/chi tiết giao dịch được đối chiếu phù hợp.
6. **Đúng nơi sử dụng:** Không dùng cert serverAuth như cert clientAuth nếu profile không cho phép; không coi root demo là public CA.
7. **Đúng vận hành:** KeyStore file và private key được bảo vệ, backup/rotation/rollback có kiểm soát.

Việc `keytool -importcert` báo `Certificate reply was installed in keystore` chứng tỏ **cài reply** thành công theo kiểm tra của công cụ và provider, **không phải tuyên bố rằng mọi peer sẽ tin CA** hay TLS hostname chắc chắn hợp lệ.

**Ví dụ tấn công:** Attacker có thể gửi một `ca.crt` do chính họ tự ký và một `app-issued.crt` do họ ký; nếu bạn import cả hai mà không xác thực nguồn và cho client dùng truststore đó, path có thể hợp lệ **đối với trust anchor giả vừa import**. Đây là lý do root trust phải được phân phối/kiểm bằng kênh độc lập.

## 11. Phân biệt certificate reply, import cert và import keystore

Trong `keytool`, cùng lệnh `-importcert` có thể có ngữ nghĩa khác do **alias đã tồn tại hay chưa**:

~~~text
Alias chưa tồn tại + certificate
  → thường tạo TrustedCertificateEntry

Alias đã là PrivateKeyEntry + certificate reply
  → xử lý reply, cài/đổi certificate chain cho private key ấy
~~~

Lệnh `-importkeystore` lại là thao tác nhập **key entries hoặc certificates từ một keystore khác**; nó **không phải** bước bắt buộc khi bạn đã giữ private key gốc trong `app.p12` và chỉ nhận certificate reply từ CA.

**Không dùng `-importkeystore` một cách mù quáng** để “cài CSR reply”. Nếu file nguồn chứa private key khác, có thể ghi đè identity, thay alias hoặc làm lệch key/cert chain. Hãy đọc alias/type của cả nguồn và đích, kiểm SHA-256 public key/certificate, kế hoạch backup và password policy trước khi migration.

### 11.1. Certificate chain trong file khác chain trong đường dẫn xác minh

- `getCertificateChain(alias)` là danh sách certificates **đang lưu** trong `PrivateKeyEntry`.
- PKIX `CertPath` là danh sách certificates cần **validator kiểm tra**, thường kết thúc **trước** trust anchor do bạn cấu hình.
- TLS peer có thể nhận chain server gửi nhưng vẫn cần trust anchor riêng và hostname validation; server không thể “gửi trust” chỉ bằng cách đưa thêm root vào chain.
- CA-issued certificate có thể là leaf hợp lệ nhưng hệ thống thiếu intermediate/trust anchor làm build path thất bại; điều đó không chứng tỏ private key bị hỏng.

## 12. Chẩn đoán lỗi `keytool` theo từng giai đoạn

| Triệu chứng | Nguyên nhân khả dĩ | Cách kiểm/khắc phục |
| --- | --- | --- |
| `-certreq` báo không thấy alias/private key | Alias sai hoặc chỉ có TrustedCertificateEntry | `keytool -list -v -alias ...`, bảo đảm PrivateKeyEntry tồn tại |
| `-certreq` không mở được private key | Sai key/store password hoặc provider | Xác định đúng type/entry protection, không log password |
| CSR thiếu SAN | Không đặt requested extension hoặc lệnh/CA profile khác | `-printcertreq -v` rồi kiểm leaf sau khi CA ký |
| `Public keys in reply and keystore don't match` | Reply thuộc CSR/key pair khác, hoặc private key bị thay | So key pair/public key, đúng file và alias, không gen lại key trong khi chờ |
| `Failed to establish chain from reply` | Thiếu CA issuer/intermediate, chain sai, CA chưa được tin hoặc input format sai | Kiểm certificates CA từ nguồn tin cậy, import/đưa chain đúng format, kiểm issuer signature |
| Certificate reply được nhập nhưng alias `app-server` vẫn self-signed | Import nhầm alias hoặc file không đúng | Kiểm `-list -v -alias app-server`, import lại đúng reply vào đúng alias nếu an toàn |
| `-importcert` tạo TrustedCertificateEntry thay vì identity | Alias dùng chưa có PrivateKeyEntry | Dùng alias sinh CSR ban đầu; không xóa private key |
| Chain order sai hoặc chain dài 1 sau reply | Thiếu CA chain / response chỉ chứa leaf | Dựng chain, bổ sung intermediate, kiểm provider |
| Peer TLS báo certificate unknown | Trust anchor không có trong peer truststore hoặc chain không dựng được | Cung cấp intermediate, cấu hình truststore của peer, không ép trust-all |
| TLS báo hostname mismatch | SAN không chứa DNS/IP cần kiểm | CA cấp lại đúng SAN đã thẩm định; không tắt hostname verification |
| Certificate expired/not yet valid | Validity window sai, clock sai hoặc chưa renew | Kiểm `-printcert -v`, thời gian UTC và lịch gia hạn |
| `basicConstraints`/`keyCertSign` fail ở issuer | Intermediate không được cấp quyền CA hoặc constraints sai | CA phải phát hành đúng CA profile; không “sửa cert” sau khi ký |
| Key algorithm/signature không được chấp nhận | Policy TLS/PKIX hoặc provider không hỗ trợ | Kiểm algorithm suite, key size, JDK provider, issuance policy |
| PKCS12 không load được | Sai password, type, file hỏng hoặc incompatibility | Kiểm file/type/password, backup và provider; không thay extension để “chuyển format” |
| Chứng thư hoạt động với một client nhưng không với client khác | Truststore/certificate policy/thu hồi và intermediate khác nhau | Kiểm chain và trust mỗi client, không suy ra từ một lượt kiểm |
| Mọi lệnh thành công nhưng chain vẫn chưa tin cậy | Root CA demo chưa được bên dựa vào cài/kiểm | Phân biệt import cục bộ với trust policy của client |

**Cách điều tra an toàn:** Ghi alias, type, certificate fingerprint, issuer, SAN, chain length, các mã lỗi và **thời điểm/phiên bản JDK**, nhưng **không ghi password/private key/token**. Kiểm cả phản hồi CA thực tế, không chỉ CSR. Hạn chế hiển thị certificate Subject nếu chúng chứa thông tin nhạy cảm ngoài mục đích logging.

## 13. Trách nhiệm của CA và của bên sử dụng chứng thư

**CA issuance** gồm nhiều bước nằm ngoài `keytool`:

- **Xác minh quyền sở hữu/tác quyền đối với định danh** theo chính sách: DNS/domain, organization, device identity hoặc application principal.
- Áp profile phát hành: subject, SAN, validity, serial, key algorithm, BasicConstraints, KU/EKU, extensions khác.
- Bảo vệ private key CA và signing service, phân quyền phê duyệt, audit và log issuance.
- Thiết kế thời hạn, renewal, revocation, CRL/OCSP, incident response.
- Cung cấp **đầy đủ intermediate certificates** để bên triển khai không tự thu thập từ nguồn thiếu tin cậy.

**Bên xin cấp/chủ ứng dụng** chịu trách nhiệm:

- Tạo private key đúng nơi và bảo vệ nó; khi có HSM/KMS phải giữ non-exportable nếu yêu cầu.
- Sinh CSR từ đúng key pair, đối chiếu nội dung yêu cầu.
- Dùng CA và các thông tin trả về qua kênh chính thức; xác minh certificate được cấp khớp mục đích.
- Import reply vào **private key alias gốc**, giữ chain và cấu hình TLS đúng.
- Giới hạn quyền đọc keystore; không chia sẻ password/file cho người không cần.
- Theo dõi expiry/rotation và khả năng reload không ngắt dịch vụ.

**Bên kiểm chứng (client/peer)** chịu trách nhiệm:

- Tin đúng root/trust anchors theo policy; không tự tin mọi certificate server gửi.
- Xác minh PKIX path, định danh hostname/subject phù hợp, KU/EKU, thời hạn, revocation/các chính sách bắt buộc.
- Không dùng `TrustManager` “accept all” hoặc tắt hostname verification để chữa lỗi nhập chain.

Một chuỗi chứng thư đúng chỉ trả lời câu hỏi **public key này được những CA nào chứng nhận theo trust policy**, không tự động chứng minh ứng dụng có quyền truy cập tài nguyên. Authentication và authorization là hai tầng khác nhau.

## 14. Bảo mật quy trình issuance và vòng đời khóa

### 14.1. Không làm lộ private key khi tạo CSR

CSR và certificate reply đều có thể lưu/chuyển như artifact công khai theo policy, nhưng **private key không**. Khi dùng `-certreq`, `keytool` đọc private key tại chỗ để ký CSR. Không export `.p12` cho CA, không dán khóa vào ticket/chat, không ghi password vào audit log.

Thư mục thực hành trong bài có **ca.p12** (private key CA demo) và **app.p12** (private key server demo). Hai file phải được xem là **secret-bearing** dù file có password. Xóa bản sao lab đúng quy trình khi xong; đừng commit vào Git hoặc nộp lên hệ thống công cộng.

### 14.2. Không tự thay certificate mà mất private key

Nếu bạn đã tạo CSR từ khóa A và nhận leaf cho public key A, nhưng deploy lại sinh khóa B rồi import leaf A, kết nối ký bằng private B không tương ứng với certificate A. Giải pháp không phải bỏ kiểm khớp key; phải khôi phục private A an toàn hoặc tạo CSR mới cho B và yêu cầu CA cấp certificate mới.

Trong kế hoạch certificate rotation, có thể dùng **alias phiên bản mới** và triển khai blue/green trước khi xóa cũ. Với key rotation, tạo key pair mới + CSR mới, cập nhật certificate chain, kiểm TLS handshake, thông báo các peer pinning nếu có. Nếu chỉ renew cùng key pair, đánh giá chính sách reuse và vòng đời khóa.

### 14.3. Certificate revocation và thời gian

Certificate có thể còn hạn nhưng đã bị thu hồi vì lộ key hoặc thay đổi tổ chức. `checkValidity()` chỉ kiểm NotBefore/NotAfter, **không kiểm thu hồi**. `CertPathValidator` có các cơ chế cấu hình policy revocation; demo đặt `setRevocationEnabled(false)` để tránh phụ thuộc CA demo không có CRL/OCSP. **Trong production không sao chép giá trị false như một quyết định bảo mật mặc định.**

Khi TLS client cần kiểm certificate revocation, hãy xác định cơ chế, chính sách hard/soft-fail và nguồn CRL/OCSP theo tiêu chuẩn/điều kiện vận hành. Không suy luận rằng có `PKIX path valid = true` trong demo đồng nghĩa đã kiểm revocation, hostname, TLS chain negotiation và mọi chính sách doanh nghiệp.

### 14.4. SAN và quyết định trust có thể thay đổi qua thời gian

Một hostname mới hoặc chuyển sang mTLS có thể cần certificate mới với SAN/EKU khác; không sửa file leaf đã ký bằng cách thay text trong PEM. Chữ ký CA bảo vệ dữ liệu certificate, nên **mọi thay đổi trường phải được CA cấp lại**.

Certificate truststore của bên kiểm chứng cũng thay đổi khi CA root được thêm, gỡ, rollover hoặc cập nhật. Phải có release/rollback plan cho cả **server identity certificate** lẫn **client truststore**, chứ không chỉ chạy lại lệnh `-importcert`.

## 15. Tình huống thực tế

### 15.1. Spring Boot HTTPS muốn chứng thư CA cấp

Nhóm phát triển có `app.p12`, alias `app-server` chứa private key. Sau CSR và CA reply, cần import đúng alias, kiểm chain và cấu hình `server.ssl.key-store`, `server.ssl.key-store-type`, password và alias theo môi trường/framework được hỗ trợ. Nếu private key/certificate chain không tương ứng, HTTPS handshake có thể thất bại dù file `.p12` “mở được”.

**Chú ý:** cấu hình cụ thể của Spring Boot thay đổi theo version; bài này mô tả khái niệm KeyStore/PKI, không áp đặt thuộc tính cấu hình cho mọi version.

### 15.2. mTLS giữa hai dịch vụ Java

Service A vừa cần **identity PrivateKeyEntry** của chính mình để xuất trình cho B, vừa cần **truststore** chứa CA được phép ký certificate của B. Service B có cấu hình tương tự nhưng identity/CA authorization riêng. CSR tạo để xin certificate với **EKU clientAuth hoặc serverAuth** theo vai trò; không dùng certificate chỉ được cấp serverAuth cho mọi luồng mTLS mà không kiểm chính sách.

### 15.3. CA gửi intermediate bundle còn server chỉ import leaf

Server vẫn có private key đúng nhưng thiếu intermediate trong chain gửi cho client. Một số client có thể tìm intermediate qua cache/AIA, số khác thất bại. Cần cài **đúng intermediate chain** và xác minh từ client có trust anchor thích hợp, không ép client tắt verification.

### 15.4. Nhập root mới sửa lỗi nhưng lại tạo rủi ro trust

Một người nhận `ca.crt` qua một kênh chưa kiểm chứng, thêm vào truststore với `-noprompt` rồi thấy certificate path valid. Điều này có thể là **tin CA của attacker**. Cần xác minh fingerprint/nguồn qua kênh độc lập và giới hạn trust anchors theo chính sách.

### 15.5. Trùng Subject DN nhưng leaf ký bởi CA khác

`CN=Training Demo Root` hoặc `Issuer DN` trông giống không đủ. Hai CA có thể dùng cùng tên DN nhưng public keys khác nhau. Bên xác minh phải kiểm **chữ ký và chain đến đúng trust anchor**, không chỉ so string tên trong certificate.

### 15.6. Hệ thống dùng HSM/PKCS11 thay vì file PKCS12

Thay vì `app.p12` chứa private key, khóa riêng nằm trong token/HSM provider và có thể không export được. CSR vẫn được ký bằng private key trong token; certificate reply phải được liên kết với **key object/alias đúng** theo cơ chế provider. Các lệnh `keytool`, cách login/token PIN và import chain có thể khác, nhưng nguyên tắc **CSR cùng public key với private key, đúng trust chain và đúng identity** vẫn giữ nguyên.

## 16. Câu hỏi tự kiểm tra (có đáp án)

**Câu 1.** `keytool -certreq` có gửi private key ra file CSR không?

**Đáp án:** Không. CSR chứa public key, subject, attributes/extension requests và chữ ký được tạo bằng private key; private key vẫn được bảo vệ trong KeyStore/HSM.

**Câu 2.** CSR đã có chữ ký hợp lệ, vậy có thể dùng ngay làm certificate TLS server không?

**Đáp án:** Không. Chữ ký CSR chứng minh khả năng ký bằng key của applicant nhưng **không phải chữ ký CA** và không xác nhận quyền với hostname. Cần CA-issued X.509 leaf + chain và xác minh trust/hostname phù hợp.

**Câu 3.** Vì sao CA có quyền từ chối hoặc sửa SAN được xin trong CSR?

**Đáp án:** SAN chỉ là **yêu cầu**. CA phải thẩm định quyền đối với định danh và tuân thủ issuance policy; nếu không sẽ cho phép xin certificate cho domain không sở hữu.

**Câu 4.** Khi CA trả `app-issued.crt` cho CSR tạo bởi alias `app-server`, nên import reply vào alias nào?

**Đáp án:** **`app-server`**, tức alias PrivateKeyEntry gốc. Import vào alias mới có thể tạo certificate-only entry chứ không kết nối certificate được cấp với private key.

**Câu 5.** Chain demo có hai phần tử. Chúng là gì và thứ tự nào?

**Đáp án:** `chain[0]` là leaf `app.example.test`; `chain[1]` là root `demo-ca` ký trực tiếp leaf. Với intermediate, chain có thể thêm phần tử giữa leaf và root.

**Câu 6.** Root CA demo là self-signed. Điều gì khiến Java PKIX example chấp nhận nó?

**Đáp án:** Chương trình **chủ động chọn root làm TrustAnchor** trong `PKIXParameters`. Self-signature hay tên root không đủ khiến nó được hệ điều hành/trình duyệt khác tin cậy.

**Câu 7.** `-importcert` báo `Public keys in reply and keystore don't match`. Nên bỏ kiểm và import ép không?

**Đáp án:** Không. Phải kiểm reply có thuộc CSR đúng không, private key có bị tái sinh/đổi alias không. Dùng lại private key đúng hoặc tạo CSR mới từ key pair đang giữ rồi yêu cầu CA cấp lại.

**Câu 8.** Khi server gửi leaf và intermediate, vì sao client vẫn có thể báo “untrusted certificate”?

**Đáp án:** Client chưa có/không cho phép trust anchor phù hợp, hoặc certificate path/policy không đạt. Các certificate server gửi **không tự trao quyền tin cậy** cho client.

**Câu 9.** `checkValidity()` và `Issuer DN khớp` có đủ bảo đảm chứng thư an toàn cho HTTPS?

**Đáp án:** Không. Cần chữ ký/path PKIX, trust anchor, key usage/EKU, SAN hostname matching, revocation policy và các yêu cầu TLS khác. Subject/issuer DN không thay kiểm chữ ký.

**Câu 10.** Một CA trả file `.p7b` và một file `.p12`. Có thể coi cả hai chỉ là leaf cert mà import bừa không?

**Đáp án:** Không. PKCS#7 thường đóng gói tập certificate/chain, không mang private key; PKCS12 có thể chứa private key và entry khác. Phải kiểm contents/type, giữ private key gốc và dùng quy trình import đúng.

**Câu 11.** Code Java demo in `PKIX path valid = true`, liệu điều đó đã kiểm server hostname và revocation chưa?

**Đáp án:** Chưa. Demo kiểm SAN mẫu riêng và tắt revocation để dễ chạy; việc hostname verification thực sự do HTTPS/TLS verifier đảm nhiệm theo chính sách. Production phải cấu hình revocation/hostname phù hợp.

**Câu 12.** Tại sao không đặt root certificate được tải qua mạng chưa xác minh vào `TrustManagerFactory` chỉ để hết lỗi TLS?

**Đáp án:** Vì đó có thể là root của attacker. Trust anchor phải được chọn qua kênh/chuỗi phân phối đáng tin, có xác minh fingerprint và kiểm soát thay đổi; “hết lỗi” không chứng minh đối tác là đúng.

## 17. Checklist trước khi cài certificate reply

- [ ] CSR sinh từ **đúng private key alias**, public key/subject/extension request được xem lại.
- [ ] Private key **không** gửi cho CA; file `.p12` và password được bảo vệ, không commit vào source.
- [ ] CA thật đã thẩm định domain/identity; SAN, KU/EKU, validity, signature/profile được kiểm trong **leaf đã phát hành**.
- [ ] Đã lấy đầy đủ intermediate chain và kiểm đúng issuer/chữ ký/basicConstraints/keyCertSign.
- [ ] Root trust anchor được phân phối từ **nguồn đã xác thực**, không import CA lạ bằng `-noprompt`.
- [ ] Reply cài vào **PrivateKeyEntry alias gốc**; không nhầm với TrustedCertificateEntry alias mới.
- [ ] `keytool -list -v` xác nhận alias, leaf/chain order/length, issuer/SAN và loại entry.
- [ ] Nếu dùng Java, `KeyStore.getEntry` trả private key + chain; kiểm public/private key và path theo chính sách.
- [ ] Truststore của **bên xác minh** chứa CA được phép tin, không suy ra từ certificate server gửi.
- [ ] HTTPS/mTLS vẫn kiểm hostname/identity, expiry, KU/EKU, revocation và policy; không dùng trust-all manager.
- [ ] Quy trình có backup/rollback, lịch certificate renewal, rotation và reload; không làm mất key đang được sử dụng.
- [ ] Không dùng password demo, CA demo hoặc `setRevocationEnabled(false)` như mẫu production mặc định.
- [ ] Dùng thư mục thử nghiệm riêng, tránh chạy lại lệnh `-genkeypair`/`-importcert` trên keystore thật không kiểm soát.

## 18. Tổng kết

**CSR PKCS#10** là **yêu cầu có chữ ký từ private key của applicant**, chứa public key, identity và extensions được đề nghị; **không chứa private key và không phải chứng thư CA đã cấp**. Sau thẩm định, **CA** ký X.509 leaf (và cung cấp intermediate chain khi cần). Trong `keytool`, phải đưa certificate reply vào **chính alias PrivateKeyEntry từng tạo CSR**, để giữ private key và thay chain self-signed bằng chain đã cấp. Root trust anchor được xác minh từ nguồn đáng tin, không mặc nhiên được trust vì là self-signed hoặc vì vừa được server gửi. Java 17+ có thể kiểm chain, public/private key và PKIX path; song **PKIX demo không thay HTTPS hostname verification và revocation policy**. Đó là ranh giới giữa quy trình cài certificate thành công và việc kết nối được xác thực thực sự.

**Tài liệu nền tảng:** PKCS#10 CSR: [RFC 2986](https://www.rfc-editor.org/rfc/rfc2986); X.509 PKIX certificate/path validation: [RFC 5280](https://www.rfc-editor.org/rfc/rfc5280); `keytool` JDK: [Oracle keytool documentation](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="x509-certificate-009">13.9 Lập bộ CA thử nghiệm và giữ ranh giới giữa chứng thư thử nghiệm với môi trường thật</a>

<details>
<summary>Xem chi tiết</summary>

# 13.9 Lập bộ CA thử nghiệm và giữ ranh giới giữa chứng thư thử nghiệm với môi trường thật

## Mục tiêu học tập

Sau bài này, bạn có thể:

1. Giải thích **Certificate Authority (CA)**, **root CA**, **chứng thư đầu cuối (leaf/end-entity certificate)**, **Certificate Signing Request (CSR)**, **trust anchor**, **certificate chain** và cơ chế kiểm tra X.509.
2. Dùng **keytool trong JDK 17+** để lập một root CA phục vụ thực hành, sinh khóa server, tạo CSR, cấp chứng thư server có **Subject Alternative Name (SAN)** và **Extended Key Usage (EKU)**, lưu chứng thư vào **PKCS12** và tạo **truststore thử nghiệm riêng biệt**.
3. Phân biệt **chứng thư được CA ký** với **chứng thư đã được một client tin cậy**. Chữ ký hợp lệ chưa đủ: còn phải có trust anchor được cấp quyền tin cậy, chuỗi hợp lệ, thời hạn đúng, hostname đúng và chính sách sử dụng phù hợp.
4. Đọc, kiểm tra chứng thư bằng keytool và dùng **Java CertPathValidator/PKIX** để thử đường dẫn chứng thực tới **duy nhất root CA lab**.
5. Giữ ranh giới không thể nhầm giữa **lab/test** với **production**: không đưa CA thử nghiệm vào cacerts, hệ điều hành, thiết bị người dùng, container production hoặc shared truststore; không dùng private key thử nghiệm trong môi trường thật; không tắt xác thực TLS để vượt lỗi.
6. Thiết kế test âm (untrusted CA, sai hostname, sai EKU, chứng thư hết hạn hoặc bị thay), quy trình hủy/rotation và checklist trước khi triển khai.

**Thông điệp quan trọng:** Tự tạo một CA thử nghiệm là cách tốt để học cơ chế **PKI/X.509/TLS**, nhưng đồng thời tạo ra quyền lực rất lớn: bất kỳ client nào tin CA đó có thể tin những chứng thư mà CA cấp **theo chính sách kiểm tra của client**. Vì vậy CA lab phải có **phạm vi tin cậy riêng, thời hạn ngắn, định danh chỉ dành cho lab và không bao giờ được đẩy sang production**.

## 1. CA làm gì, và điều gì khiến một chứng thư được tin?

Trong PKI, **CA** sở hữu một cặp khóa. Private key của CA dùng để **ký** chứng thư X.509 cho các subject/khóa công khai khác; certificate của CA chứa public key và thông tin nhận dạng. Khi một TLS client nhận chứng thư server, client có thể kiểm tra chữ ký và đường dẫn chứng thực đến một **trust anchor** mà nó đã chủ động cấu hình tin.

~~~text
                       TRUST ANCHOR (được client chọn)
                           Lab Root CA certificate
                           Public key của Lab Root CA
                                      |
                               ký leaf certificate
                                      v
Server key pair ---> CSR ----> Lab Server certificate
(private key chỉ ở server)     SAN=DNS:localhost,IP:127.0.0.1
                               EKU=serverAuth
                                      |
                                  TLS handshake
                                      |
Client kiểm tra:
   chứng thư server phù hợp public/private key đang sử dụng?
   đường dẫn chữ ký có đến CA mà client TIN không?
   còn hạn? extensions và key usage đúng?
   hostname/IP có khớp SAN?
   có vi phạm chính sách trust hoặc revocation không?
~~~

**Tự ký (self-signed)** chỉ nói rằng chứng thư được ký bằng private key tương ứng public key trong chính chứng thư ấy. Nó **không tự được mọi máy tin cậy**. Chỉ khi một client chủ động tin chứng thư CA đó (hoặc cơ chế trust tương đương), nó mới trở thành **trust anchor** cho quá trình xác minh.

### 1.1 “Được ký” khác “được tin”

Giả sử root của lab cấp chứng thư server.example.com. Bằng cách nào đó attacker đưa root lab vào truststore production của một ứng dụng. Khi ấy attacker giữ private key CA lab có thể phát hành chứng thư cho tên miền mà ứng dụng truy cập. Nếu ứng dụng không có kiểm soát khác, **chuỗi cert có thể được xem là tin cậy**, tạo rủi ro giả mạo endpoint/MITM.

Vì thế, **ranh giới truststore** là ranh giới an ninh quan trọng: thêm một root CA không đơn giản là “thêm một file cert cho hết lỗi”, mà là **trao quyền phát hành chứng thư được tin** trong phạm vi sử dụng của truststore đó.

### 1.2 Những thành phần cần phân biệt

| Thành phần | Chức năng | Có private key không? | Có nên đưa vào production? |
| --- | --- | --- | --- |
| **Root CA private key của lab** | Ký các leaf certificate trong bài thực hành | **Có, cực kỳ nhạy cảm** | **Không bao giờ** |
| **Root CA certificate của lab** | Cung cấp public key để kiểm tra chữ ký và có thể làm trust anchor | Không | **Không được cài làm trusted root production** |
| **CSR** | Đề nghị cấp chứng thư, chứa public key và chủ thể đề nghị | Không chứa private key | Chỉ dùng theo luồng cấp chứng thư đã kiểm soát |
| **Leaf private key của lab server** | Server chứng minh quyền giữ khóa trong TLS | Có | Không dùng cho production identity |
| **Leaf certificate của lab** | Gắn public key server với tên/thuộc tính do lab CA chứng nhận | Không | Không phải chứng thư production đáng tin |
| **Server keystore** | Lưu keypair và certificate chain cho server | Thường có private key | Chỉ dùng trong lab |
| **Client truststore riêng** | Chứa root certificate lab được client thực hành chủ động tin | Thường chỉ có public cert | Không được dùng làm truststore chung/prod |

**Keystore và truststore đều có thể dùng PKCS12**. Khác biệt là **vai trò**: server cần khóa định danh, client cần những trust anchor nó có chủ đích tin tưởng.

## 2. Ngôn ngữ X.509 quan trọng trước khi phát hành

### 2.1 BasicConstraints: CA hay end entity?

X.509 extension **BasicConstraints** xác định subject có thể đóng vai CA hay không, kèm tùy chọn path length.

- **Root CA thử nghiệm:** BasicConstraints **CA=true**, đánh dấu **critical** theo chính sách phù hợp. Một CA có thể ký leaf; trong lab này dùng **pathLen=0** để không tạo chuỗi CA trung gian không cần thiết.
- **Server leaf:** BasicConstraints **CA=false**. Nó là end entity, **không được cấp quyền phát hành chứng thư CA**.
- **pathLen=0** là giới hạn số intermediate CA tiếp theo trong đường chứng thực, **không có nghĩa CA không được ký leaf certificate**.

Nếu một leaf có CA=false nhưng lại có KeyUsage keyCertSign, cấu hình là sai về mục đích và không được xem là CA hợp lệ theo profile. Đừng vô tình tạo CA certificate làm certificate server.

### 2.2 KeyUsage và ExtendedKeyUsage

**KeyUsage (KU)** giới hạn các hành vi dùng public key trong chứng thư:

- CA: **keyCertSign**; có thể thêm **cRLSign** nếu thiết kế xử lý danh sách thu hồi.
- TLS server với RSA: thường cần **digitalSignature** (đặc biệt TLS hiện đại); tùy giao thức/cấu hình có thể có **keyEncipherment** cho các trường hợp tương thích legacy.
- Không đặt **keyCertSign** trên leaf.

**ExtendedKeyUsage (EKU)** diễn đạt mục đích ứng dụng ở mức cao hơn. Với leaf của HTTPS server, bài lab dùng:

~~~text
EKU = serverAuth
OID = 1.3.6.1.5.5.7.3.1
~~~

Nếu muốn cấp chứng thư cho client mTLS, EKU **clientAuth** là mục đích khác; không cho rằng mọi leaf đều tự động dùng được để xác thực cả server lẫn client.

### 2.3 Subject Alternative Name và hostname verification

Ngày nay HTTPS client dùng **Subject Alternative Name (SAN)** để kiểm tra danh tính máy chủ theo quy tắc phù hợp. CN của subject **không thay thế** SAN cho một cấu hình TLS hiện đại.

Trong bài lab sẽ cấp leaf với:

~~~text
Subject: CN=localhost, O=Local Training, C=VN
SAN: DNS:localhost, IP:127.0.0.1
EKU: serverAuth
BasicConstraints: CA=false
~~~

Điều này phục vụ truy cập **https://localhost** hoặc **https://127.0.0.1** trong lab tùy server/client cấu hình. **Không thêm SAN của domain công ty thật hoặc domain production**. Nếu hostname bạn truy cập là service.prod.example.com, chứng thư lab cho localhost phải **không** khớp; đừng tắt hostname verification để che lỗi.

### 2.4 Thời hạn, serial number, issuer và trust

Mỗi X.509 certificate có:

- **Subject:** danh tính được chứng nhận, ví dụ CN=localhost; không phải toàn bộ quy tắc kiểm tra hostname.
- **Issuer:** tên CA ký.
- **Validity:** Not Before và Not After, phụ thuộc đồng hồ và chính sách.
- **Serial number:** định danh trong phạm vi issuer.
- **Signature algorithm và chữ ký CA:** dùng để xác nhận certificate bytes được CA ký.
- **Public key:** khóa công khai của subject.
- **Extensions:** SAN, KU, EKU, BasicConstraints, SKI/AKI, policy, CRL/OCSP tùy thiết kế.

**Có chữ ký hợp lệ không đồng nghĩa thuộc một chuỗi chứng thực được tin cậy**. Client cần trust anchor, kiểm tra đường dẫn, thời gian, mục đích, hostname, trạng thái thu hồi theo chính sách và ứng dụng.

## 3. Phạm vi lab và luật cách ly ngay từ đầu

**Mô hình lab dùng trong bài:**

- Một thư mục riêng **ca-lab/**, **không đồng bộ vào repo/CI artifacts**.
- Một root CA tự ký, chỉ phát hành leaf cho **localhost**.
- Root validity **30 ngày**, leaf validity **7 ngày**.
- Khóa RSA **3072 bit**, chữ ký **SHA256withRSA** cho demo, không phụ thuộc công cụ bên ngoài JDK.
- Mọi keystore/truststore do lab tạo dùng **PKCS12**.
- Mật khẩu được nhập tương tác; **không nhúng vào shell history, script, README, Dockerfile, command line**.
- Không gửi bất cứ chứng thư lab nào tới production truststore, **$JAVA_HOME/lib/security/cacerts**, OS/system trust, browser profile chung hoặc service account không thuộc lab.
- Root private key không nằm trong máy/container đang chạy server thật. Bài lab dùng chung thư mục để dễ học; thiết kế thực tế phải tách máy CA và runtime service.
- Không cấp phép public DNS domain, IP công ty hoặc SAN tùy ý từ CSR lạ.

**Giới hạn của lab:** Đây là môi trường học certificate issuance, không phải vận hành một Public CA hay Internal PKI đúng quy mô. Nó **không thiết lập** CRL/OCSP responder, CA audit, certificate transparency, HSM, approval workflow hay high availability. Không xem root của lab là CA production đủ chuẩn chỉ vì keytool đã tạo nó.

## 4. Thực hành bằng keytool JDK 17+: root → CSR → leaf → truststore

Các lệnh **keytool** dưới đây là lệnh đầy đủ, mỗi lệnh nằm trên **một dòng**, nên có thể gõ trong Bash, PowerShell hoặc terminal có keytool trên PATH. Nếu chưa có JDK 17+, cần cài JDK thích hợp; **java -version** và **keytool -help** có thể dùng để kiểm tra công cụ đang chạy.

**Không chạy các lệnh trong thư mục đang chứa keystore production**. Tên file cố ý chứa chữ lab. Không dùng giá trị mật khẩu mẫu cố định trong tài liệu; khi keytool hỏi, hãy nhập mật khẩu đủ mạnh chỉ sử dụng cho lab này. Không cần dùng chung một mật khẩu cho root, server và truststore.

### 4.1 Tạo không gian riêng

~~~bash
mkdir ca-lab
cd ca-lab
~~~

Đảm bảo thư mục nằm ngoài repo/shared drive và có quyền truy cập hạn chế. Các file sắp tạo gồm **lab-root.p12**, **lab-root.pem**, **lab-server.p12**, **lab-server.csr**, **lab-server.pem**, **lab-client-trust.p12**. Khi chạy lại từ đầu nên tạo thư mục lab khác; không overwrite những file đang chứa khóa hoặc chứng thư có giá trị.

### 4.2 Tạo root CA thử nghiệm, không phải server certificate

~~~bash
keytool -genkeypair -alias lab-root -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -dname "CN=Lab Root CA, O=Local Training, C=VN" -validity 30 -ext BC:critical=ca:true,pathlen:0 -ext KU:critical=keyCertSign,cRLSign -keystore lab-root.p12 -storetype PKCS12
~~~

Lệnh sinh một **PrivateKeyEntry** có private key RSA 3072 bit và một **self-signed X.509 root CA certificate** trong lab-root.p12.

Giải thích quan trọng:

- **BC:critical=ca:true,pathlen:0:** thể hiện certificate được dùng làm CA, không cho intermediate CA tiếp theo theo đường chứng thực thông thường.
- **KU:critical=keyCertSign,cRLSign:** root có quyền ký certificate và CRL theo bit KU, nhưng lab **chưa vận hành CRL**, không được coi là có kiểm soát revocation.
- **-validity 30:** tuổi chứng thư root 30 ngày; không có ý nghĩa thời hạn mặc định hợp lệ cho CA production.
- **-sigalg SHA256withRSA:** chỉ định thuật toán ký trong ví dụ.
- **lab-root.p12:** chứa **private key CA**; bảo vệ cẩn thận, không gửi cho client/server và không commit vào Git.

Quan sát root certificate trong keystore:

~~~bash
keytool -list -v -alias lab-root -keystore lab-root.p12 -storetype PKCS12
~~~

Kỳ vọng thấy **Entry type: PrivateKeyEntry**, issuer/subject là root lab, BasicConstraints CA:true và KeyUsage có keyCertSign. Keytool sẽ hiển thị fingerprint, serial và validity cụ thể phụ thuộc vào lần sinh.

### 4.3 Xuất **chỉ chứng thư công khai** của root

~~~bash
keytool -exportcert -rfc -alias lab-root -file lab-root.pem -keystore lab-root.p12 -storetype PKCS12
~~~

**lab-root.pem** là certificate PEM công khai, **không chứa private key**. Nó có thể được phân phối cho **client lab được cấp phép**, sau khi client xác minh fingerprint từ kênh tin cậy. Không nhầm một bản copy PEM bất kỳ với một trust anchor đã xác minh nguồn.

Kiểm tra:

~~~bash
keytool -printcert -file lab-root.pem
~~~

Nhìn vào subject, issuer, validity, BasicConstraints, KU và **SHA-256 fingerprint**. Khi nhập root này vào một truststore, người quản trị cần so sánh fingerprint với giá trị được chuyển qua **kênh xác thực độc lập**, không chỉ tin file vừa được gửi qua một kênh không đáng tin.

### 4.4 Sinh khóa server và chứng thư self-signed tạm thời

~~~bash
keytool -genkeypair -alias lab-server -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -dname "CN=localhost, O=Local Training, C=VN" -validity 7 -keystore lab-server.p12 -storetype PKCS12
~~~

Keytool tạo private key và một self-signed certificate tạm thời trong **lab-server.p12**. **Đây chưa phải chứng thư do lab-root ký.** Khi import certificate reply từ lab-root, entry dưới alias lab-server sẽ được cập nhật để mang certificate chain tương ứng.

**Private key server** ở lab-server.p12 phải được giữ trong phạm vi server lab. Bên CA cần biết **CSR**, không cần và không được lấy raw private key server.

### 4.5 Tạo Certificate Signing Request (CSR)

~~~bash
keytool -certreq -alias lab-server -file lab-server.csr -keystore lab-server.p12 -storetype PKCS12
~~~

CSR được ký bằng **private key của lab-server** và mang public key cùng thông tin subject yêu cầu. CSR **không chứa private key server**. Một CA chân chính còn phải kiểm tra requestor có quyền xin những danh tính/SAN nào.

Để xem nội dung CSR:

~~~bash
keytool -printcertreq -file lab-server.csr -v
~~~

**Cảnh báo bảo mật:** Không tự động cấp cho mọi tên miền mà CSR khai báo. Trong bài lab, **CA ký với SAN do chính quy trình cấp chứng thư quy định**, không dùng **-ext honored=all** để chấp nhận toàn bộ extension do một CSR không tin cậy yêu cầu.

### 4.6 CA ký CSR thành leaf cho localhost, có SAN và serverAuth

~~~bash
keytool -gencert -alias lab-root -keystore lab-root.p12 -storetype PKCS12 -infile lab-server.csr -outfile lab-server.pem -rfc -validity 7 -ext BC:critical=ca:false -ext KU:critical=digitalSignature,keyEncipherment -ext EKU=serverAuth -ext SAN=dns:localhost,ip:127.0.0.1
~~~

Điểm quan trọng:

- **-alias lab-root:** dùng private key của CA để ký certificate reply cho subject trong CSR.
- **-rfc:** đầu ra PEM Base64 thay vì DER.
- **-validity 7:** leaf có thời hạn ngắn; root validity nên bao phủ thời hạn leaf trong lab.
- **BC:critical=ca:false:** leaf là end entity, không thể trở thành CA.
- **KU:critical=digitalSignature,keyEncipherment:** ràng buộc mục đích sử dụng public key RSA theo các khả năng được khai báo; TLS hiện đại phụ thuộc digitalSignature.
- **EKU=serverAuth:** đánh dấu mục đích xác thực TLS server.
- **SAN=dns:localhost,ip:127.0.0.1:** danh tính chỉ thuộc local lab, không cấp chứng thư cho domain thật.

**Trong thực tế**, CA cần quy trình xác minh danh tính, giới hạn SAN, quản lý serial, kiểm soát private key, CRL/OCSP và audit. Một keytool gencert local không tạo ra các chức năng vận hành đó.

Kiểm tra leaf đã ký:

~~~bash
keytool -printcert -file lab-server.pem
~~~

Quan sát và so sánh: **Issuer = Lab Root CA**, **Subject = localhost**, SAN có localhost/127.0.0.1, BasicConstraints CA:false, KU và EKU tương ứng.

### 4.7 Thêm root công khai vào server keystore để xây certificate chain

Trước khi nhập reply của leaf, nhập **bản root certificate đã xác minh** vào keystore server, dưới alias khác alias private key:

~~~bash
keytool -importcert -alias lab-root-trust -file lab-root.pem -keystore lab-server.p12 -storetype PKCS12
~~~

Keytool có thể hỏi có tin root certificate này không. **Chỉ trả lời đồng ý sau khi kiểm tra đúng fingerprint lab root**. Trong keystore server dùng để thực hành, bản public certificate này hỗ trợ keytool dựng chain cho leaf. Nó **không** có nghĩa server nên đồng thời dùng keystore identity làm truststore chung.

Sau đó nhập certificate reply vào **đúng alias lab-server**, để thay chứng thư self-signed tạm thời:

~~~bash
keytool -importcert -alias lab-server -file lab-server.pem -keystore lab-server.p12 -storetype PKCS12
~~~

**Thứ tự import quan trọng**: phải có issuer root mà keytool có thể dùng để dựng chain trước khi nhập leaf reply đơn lẻ. Kiểm tra kết quả:

~~~bash
keytool -list -v -alias lab-server -keystore lab-server.p12 -storetype PKCS12
~~~

Kỳ vọng là **PrivateKeyEntry** với **certificate chain length = 2** trong bài lab một root + một leaf: chứng thư đầu là lab-server do root ký; chứng thư tiếp theo là root CA. Kết quả số lượng/format có thể phụ thuộc vào cách import hoặc provider; nếu không thấy chain đúng, phải chẩn đoán thay vì bỏ qua kiểm tra.

**Không copy lab-root.p12 sang máy server**. Server chỉ cần **private key của chính server** và public cert chain; CA signing key phải tách biệt.

### 4.8 Lập truststore riêng cho client thử nghiệm

~~~bash
keytool -importcert -alias lab-root-trust -file lab-root.pem -keystore lab-client-trust.p12 -storetype PKCS12
~~~

Nếu truststore chưa tồn tại, keytool có thể tạo file mới và hỏi mật khẩu. Khi hỏi có tin cert không, chỉ xác nhận sau khi xem và đối chiếu SHA-256 fingerprint của lab-root.pem qua kênh độc lập.

Kiểm tra:

~~~bash
keytool -list -v -keystore lab-client-trust.p12 -storetype PKCS12
~~~

**Kỳ vọng:** truststore có **TrustedCertificateEntry** cho alias lab-root-trust, chứa đúng root CA public certificate, **không chứa private key** và **không chứa certificate root bất kỳ khác**. Giữ truststore này độc lập với production và với truststore chung của doanh nghiệp.

### 4.9 Danh sách file cuối cùng

| File | Có gì? | Ai có thể dùng? | Quy tắc bảo vệ |
| --- | --- | --- | --- |
| **lab-root.p12** | Root CA private key + root certificate | Chỉ người vận hành CA lab | Giữ bí mật, không ship sang client/server/production |
| **lab-root.pem** | Root CA public certificate | Client lab sau xác minh fingerprint | Không được cài làm root trusted toàn máy |
| **lab-server.p12** | Server private key + leaf cert chain (và root trusted entry phục vụ dựng chain) | Chỉ server lab | Hạn chế quyền file/password |
| **lab-server.csr** | CSR với public key/subject | CA lab | Không chứa private key, vẫn phải xác minh requestor/SAN |
| **lab-server.pem** | Leaf public certificate do root lab ký | Client/server lab | Không bí mật nhưng không chứng minh tự tin cậy |
| **lab-client-trust.p12** | TrustedCertificateEntry của root lab | Chỉ client tiến trình lab | Không thay cacerts/OS trust store |

**Tuyệt đối không** đưa lab-root.p12 vào nơi có ứng dụng production. Root private key là tài sản nhạy cảm nhất trong mô hình này vì nó có khả năng cấp chứng thư mới được client tin.

## 5. Java 17+: đọc và xác minh đường dẫn tới đúng CA của lab

Lệnh keytool cho thấy nội dung store và certificate, nhưng để hiểu JCA, hãy xây một chương trình đọc hai file đã tạo và chạy **PKIX certificate path validation**.

Chương trình **LabCaCheck.java** này:

- Nhập mật khẩu keystore và truststore qua **Console.readPassword()** (không để mật khẩu trong command line).
- Load **lab-server.p12** và **lab-client-trust.p12** theo PKCS12.
- Lấy leaf từ alias lab-server và root certificate từ alias lab-root-trust.
- Kiểm tra root có BasicConstraints CA=true, leaf có CA=false, leaf có EKU serverAuth và có SAN DNS localhost.
- Gọi **CertPathValidator.getInstance("PKIX")** với **duy nhất root certificate lab** làm TrustAnchor.
- **Không** tắt hoặc thay thế PKIX signature/path validation; chỉ đặt **revocationEnabled(false)** trong lab vì ví dụ chưa triển khai CRL/OCSP.
- Không dùng truststore mặc định/cacerts và không cấp trust rộng cho toàn bộ JVM.

**Đây là ví dụ thực hành certificate path; không phải một TLS client hoàn chỉnh.** Kiểm tra SAN bằng tay ở đây chỉ chứng minh dữ liệu SAN tồn tại, không thay thế bộ quy tắc hostname verification của HTTPS; server certificate sẽ còn phải được TLS stack kiểm tra theo endpoint thật.

Tệp **LabCaCheck.java**:

~~~java
import java.io.Console;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class LabCaCheck {
    private static KeyStore openPkcs12(
            String filename, char[] password) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(Path.of(filename))) {
            store.load(input, password);
        }
        return store;
    }

    private static boolean hasLocalhostSan(X509Certificate certificate)
            throws Exception {
        Collection<List<?>> san = certificate.getSubjectAlternativeNames();
        if (san == null) {
            return false;
        }
        for (List<?> item : san) {
            // SAN type 2 = dNSName
            if (Integer.valueOf(2).equals(item.get(0))
                    && "localhost".equalsIgnoreCase(
                            String.valueOf(item.get(1)))) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) throws Exception {
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException(
                "Run from an interactive terminal (Console required)");
        }

        char[] serverPassword =
                console.readPassword("Lab server keystore password: ");
        char[] trustPassword =
                console.readPassword("Lab client truststore password: ");
        if (serverPassword == null || trustPassword == null) {
            throw new IllegalStateException("Password input aborted");
        }

        try {
            KeyStore server = openPkcs12(
                    "lab-server.p12", serverPassword);
            KeyStore trust = openPkcs12(
                    "lab-client-trust.p12", trustPassword);

            X509Certificate leaf = (X509Certificate)
                    server.getCertificate("lab-server");
            X509Certificate root = (X509Certificate)
                    trust.getCertificate("lab-root-trust");

            if (leaf == null || root == null) {
                throw new IllegalStateException(
                    "Required lab certificate alias missing");
            }

            leaf.checkValidity();
            root.checkValidity();

            boolean rootIsCa = root.getBasicConstraints() >= 0;
            boolean leafIsEndEntity = leaf.getBasicConstraints() == -1;
            List<String> eku = leaf.getExtendedKeyUsage();
            boolean serverAuth = eku != null &&
                    eku.contains("1.3.6.1.5.5.7.3.1");
            boolean localhostSan = hasLocalhostSan(leaf);

            System.out.println("Root is CA: " + rootIsCa);
            System.out.println(
                    "Leaf is end entity: " + leafIsEndEntity);
            System.out.println(
                    "Leaf allows serverAuth: " + serverAuth);
            System.out.println(
                    "Leaf SAN includes localhost: " + localhostSan);

            if (!rootIsCa || !leafIsEndEntity
                    || !serverAuth || !localhostSan) {
                throw new SecurityException(
                    "Lab certificate extensions do not match policy");
            }

            CertificateFactory factory =
                    CertificateFactory.getInstance("X.509");
            CertPath path = factory.generateCertPath(List.of(leaf));

            PKIXParameters parameters = new PKIXParameters(
                    Set.of(new TrustAnchor(root, null)));

            // Lab chỉ kiểm tra chain; CHƯA vận hành CRL/OCSP.
            // Production phải cấu hình revocation theo PKI policy.
            parameters.setRevocationEnabled(false);

            CertPathValidator.getInstance("PKIX")
                    .validate(path, parameters);
            System.out.println("PKIX chain to lab root: VALID");
        } finally {
            Arrays.fill(serverPassword, '\0');
            Arrays.fill(trustPassword, '\0');
        }
    }
}
~~~

Biên dịch và chạy **từ thư mục ca-lab** sau khi tạo các file ở mục 4:

~~~bash
javac -encoding UTF-8 LabCaCheck.java
java LabCaCheck
~~~

Chương trình hỏi hai mật khẩu tương tác. Nếu các file/alias/extension/chain đã đúng và chứng thư chưa hết hạn, phần **stdout sau hai lời nhắc** sẽ là:

~~~text
Root is CA: true
Leaf is end entity: true
Leaf allows serverAuth: true
Leaf SAN includes localhost: true
PKIX chain to lab root: VALID
~~~

Chương trình chủ động **ném exception** khi password sai, không thấy alias, certificate hết hạn, profile không phù hợp hoặc đường dẫn PKIX không xác minh được. Đây là hành vi **fail closed**, tốt hơn trả true trong mọi trường hợp.

### 5.1 Tại sao CertPath chỉ chứa leaf?

Certificate path **không cần nhét root trust anchor vào danh sách certificate cần chứng minh**; root đã được khai báo riêng qua **PKIXParameters(Set<TrustAnchor>)**. Lab này không có intermediate CA, nên đường dẫn chỉ có **leaf**. Với PKI nhiều cấp, path phải có các intermediate đúng thứ tự hoặc dùng CertPathBuilder phù hợp để tìm chain.

**TrustAnchor là đầu vào được ứng dụng tin từ trước**. Đưa bất kỳ root certificate do mạng gửi vào Set.of(new TrustAnchor(...)) mà không xác minh nguồn **sẽ phá ranh giới trust**. Trong ví dụ, root được load từ truststore riêng đã được cấp quyền và kiểm tra fingerprint.

### 5.2 Vì sao revocationEnabled(false) chỉ chấp nhận trong lab?

Chứng thư có thể cần bị thu hồi trước NotAfter do private key bị lộ, server đã nghỉ hoạt động hoặc CA phát hiện cấp sai. Trong PKI production, kiểm tra thu hồi theo chính sách có thể dùng **CRL, OCSP, cơ chế TLS được triển khai phù hợp và kiểm soát vòng đời**.

Ví dụ lab **không cung cấp CRL/OCSP**, nên nó cố ý tắt revocation trong chương trình chỉ để tập trung vào chain/signature/path validation. **Không sao chép dòng này làm mặc định bảo mật production**, và cũng không cho rằng bật một boolean là đủ nếu PKI không có cơ chế trạng thái thu hồi đúng.

### 5.3 Code có xác minh hostname đầy đủ chưa?

**Chưa.** Hàm hasLocalhostSan chỉ kiểm tra **một SAN DNS** đúng với mục tiêu lab. Nó **không** thực hiện đầy đủ các quy tắc HTTPS hostname verification (wildcards, IP SAN, IDN, normalization, policy). Khi dùng HTTPS, cần để **HTTPS/TLS library chính thống** xác minh hostname. Nếu tự dùng SSLSocket, phải cấu hình endpoint identification phù hợp.

## 6. Dùng truststore lab trong JSSE mà không mở rộng trust cho production

### 6.1 Không đưa root lab vào cacerts

**Không chạy** các biến thể nhập root lab với mục tiêu:

~~~text
$JAVA_HOME/lib/security/cacerts
keytool -importcert -cacerts ...
Windows Trusted Root Certification Authorities
macOS System Keychain trust
Browser/OS trust profile dùng chung
Container image hoặc trust bundle production
~~~

Các dòng trên là **danh sách nơi cấm đưa CA lab vào**, không phải lệnh chạy. Việc nhập root vào truststore chung sẽ khiến nhiều ứng dụng ngoài lab **có thể** tin các leaf do CA lab phát hành. Đây là loại thay đổi có thể vượt quá phạm vi thử nghiệm và tạo rủi ro MITM.

**Điểm phân biệt:** Root certificate công khai không nhất thiết bí mật, nhưng **đặt nó vào danh sách trusted roots** là một hành động cấp quyền tin cậy có tác động bảo mật lớn.

### 6.2 Cách dùng per-process: chỉ đưa truststore cho ứng dụng lab

Đối với một tiến trình Java chuyên để học TLS, có thể đặt **javax.net.ssl.trustStore** trỏ đến **lab-client-trust.p12**, kèm **javax.net.ssl.trustStoreType=PKCS12**, theo cấu hình test chuyên biệt. Nhưng **không đưa password plaintext vào command line hoặc log** trong hệ thống thật. Cách có kiểm soát hơn là **nạp truststore bằng Java và tạo SSLContext riêng** cho HTTP/TLS client lab.

Mẫu Java sau **không phải chương trình độc lập**: giả định biến **labTrust** đã load PKCS12 bằng mật khẩu đúng, và URL lab endpoint có TLS server đang chạy.

~~~java
TrustManagerFactory factory = TrustManagerFactory.getInstance(
        TrustManagerFactory.getDefaultAlgorithm());
factory.init(labTrust);

SSLContext labTls = SSLContext.getInstance("TLS");
labTls.init(null, factory.getTrustManagers(), null);

HttpsURLConnection conn = (HttpsURLConnection)
        new URL("https://localhost:8443/").openConnection();
conn.setSSLSocketFactory(labTls.getSocketFactory());
conn.connect();
// Không thay HostnameVerifier; HTTPS tiếp tục kiểm tra hostname.
~~~

Để biên dịch đoạn trên thành chương trình, cần các import **javax.net.ssl.TrustManagerFactory**, **javax.net.ssl.SSLContext**, **javax.net.ssl.HttpsURLConnection**, **java.net.URL** và phần nạp labTrust. Mẫu này minh họa **SSLContext cho riêng một connection lab**; không tạo kết nối nếu chưa có server TLS lab nghe ở cổng 8443.

**Không làm điều sau:**

~~~java
// ANTI-PATTERN: Không dùng trong lab hoặc production.
HttpsURLConnection.setDefaultHostnameVerifier(
        (hostname, session) -> true);
~~~

Thao tác đó tắt kiểm tra danh tính host cho nhiều kết nối của lớp API, làm mất một lớp bảo vệ quan trọng. Một “trust-all X509TrustManager” cũng nguy hiểm tương tự. **Sửa lỗi certificate bằng cách thêm đúng root CA vào truststore lab được cấp phép**, không bằng cách bỏ xác thực.

### 6.3 Cần nhớ: truststore và hostname validation là hai bước khác nhau

Ngay cả khi **PKIX path** đúng:

- Nếu client truy cập **https://service.company.example** nhưng cert chỉ có SAN=localhost, **hostname phải bị từ chối**.
- Nếu cert có SAN đúng nhưng không chain tới root mà client tin, **path validation phải thất bại**.
- Nếu cert hết hạn hoặc chưa có hiệu lực, **time validation phải thất bại**.
- Nếu EKU/KU không phù hợp hoặc TLS stack không chấp nhận thuật toán/chữ ký, **handshake có thể thất bại**.
- Nếu kiểm tra thu hồi được yêu cầu nhưng không thể thực hiện đúng theo policy, xử lý phải theo chính sách; không âm thầm bỏ qua.

Không nhầm “tôi có thể in ra certificate” với “TLS đã xác thực đúng đối tác”.

### 6.4 Keystore server và truststore client không nên tráo vai trò

Một server cần **KeyManagerFactory** truy cập private key và chain để chứng minh danh tính với TLS peer. Client cần **TrustManagerFactory** với CA roots/anchors để kiểm tra chứng chỉ server. Trong **mutual TLS (mTLS)**, cả hai phía có thể vừa có identity keystore vừa có truststore, nhưng trách nhiệm và quyền khác nhau.

Không nên dùng **lab-root.p12** làm server keystore: như vậy server nắm **CA signing private key**, tạo rủi ro rất lớn. Server chỉ dùng lab-server.p12 chứa private key của **leaf server**. Client chỉ nhận lab-root.pem/lab-client-trust.p12, **không nhận private key server/CA**.

## 7. Bộ kiểm thử âm để chứng minh ranh giới thực sự tồn tại

Một CA lab hoàn thành bài không phải vì “có file và chain”, mà vì **chỉ client lab được phép tin nó; các đối tượng không được phép vẫn phải từ chối**.

| Tình huống | Kết quả kỳ vọng | Điểm được kiểm chứng |
| --- | --- | --- |
| Client lab dùng đúng truststore lab và kết nối tới localhost của server lab | Handshake thành công nếu server/protocol cấu hình đúng | CA trust + SAN + key usage + thời gian |
| Client khác dùng truststore mặc định không chứa lab root | **Không tin được lab server certificate** | Không rò trust sang phạm vi ngoài lab |
| Client lab truy cập server bằng hostname không có trong SAN | **Từ chối hostname** | Không tắt hostname verification |
| Client lab tin một root test khác nhưng không tin root bài này | **Từ chối chain** | Trust anchor không phải bất kỳ self-signed cert nào |
| Thay leaf bằng cert ký bởi key không thuộc root lab | **Từ chối path** | Chữ ký và đường dẫn phải xác minh |
| Đổi byte trong nội dung chứng thư đã ký | **Từ chối** | Không thể sửa cert mà không phá chữ ký |
| Leaf hết hạn, chưa tới NotBefore hoặc đồng hồ sai đáng kể | **Từ chối theo chính sách** | Kiểm tra validity |
| Leaf thiếu EKU serverAuth khi policy yêu cầu | **Từ chối** | Kiểm tra mục đích |
| Leaf CA=true dùng làm server identity | **Không dùng cho lab server** | Phân biệt CA và end entity |
| Root CA private key xuất hiện trong container/app runtime | **Lỗi kiến trúc phải sửa** | CA signing key không phân phối |
| Có người đề nghị nhập root lab vào production cacerts | **Từ chối thay đổi** | Chặn mở rộng trust ngoài phạm vi |
| Certificate bị thu hồi nhưng vẫn còn hạn | Phải xử lý qua revocation policy khi production | Validity date không thay thế revocation |
| Gỡ truststore lab khỏi tiến trình lab | Lab certificate không còn được tin nếu không có trust từ nơi khác | Trust có thể được thu hồi theo phạm vi |

**Quan trọng:** Để làm test “truststore mặc định không tin CA lab”, phải xác định môi trường thật chưa cài CA lab trước đó. Không thể khẳng định mọi máy đều từ chối nếu CA lab đã bị import vào truststore chung từ một lần thử trước.

### 7.1 Kiểm thử danh tính sai không phải lý do để bỏ HTTPS

Một lỗi quen thuộc:

~~~text
SSLHandshakeException: PKIX path building failed
                   |
Developer: "Chắc chứng thư tự ký, bật trust-all thôi"
                   |
           Tất cả endpoint giả cũng có thể được chấp nhận
~~~

Cách sửa đúng:

1. Phân loại lỗi: **untrusted issuer**, **hostname mismatch**, **expired cert**, **EKU/KU mismatch**, **chain thiếu**, **sai key**, **revocation**.
2. Với **CA lab**, kiểm tra file truststore của **duy nhất client test**, xác minh fingerprint.
3. Với domain production, dùng chứng thư/chains được CA tin cậy theo chính sách production; **không đưa CA lab vào production để chữa lỗi**.
4. Giữ hostname verification, PKIX path validation và TLS phiên bản/cipher suites phù hợp.
5. Tạo test âm để bảo đảm sửa lỗi không làm mở rộng trust ngoài ý muốn.

### 7.2 Không cấp SAN tùy ý theo CSR

CSR chứng minh quyền kiểm soát **private key của keypair đang yêu cầu** thông qua chữ ký CSR, **không tự chứng minh quyền sở hữu DNS name/IP**. Nếu CA cứ chấp nhận -ext honored=all và bất kỳ SAN client đòi hỏi, nó có thể ký cho tên miền không thuộc phạm vi lab.

Trong bài, CA ký với SAN **localhost/127.0.0.1** bằng lệnh -gencert được quản trị rõ. Một CA nội bộ thật phải có quy trình kiểm tra danh tính, phê duyệt domain/namespace và giới hạn cấu hình issuer.

## 8. Những ranh giới buộc phải giữ giữa test và production

### 8.1 Tách biệt key material

**Không dùng cùng khóa CA hoặc leaf** giữa lab và production.

- **Lab root private key** chỉ tồn tại trong kho/tài khoản lab được phép.
- **Production CA/private key** phải được cấp phát qua CA/KMS/HSM/quy trình được phê duyệt, không lấy từ bài tập.
- Không đặt lab root key trong source, environment variable chia sẻ, artifact CI, Docker image hoặc backup production.
- Nếu cần demo mTLS qua nhiều nhóm, dùng **CA riêng theo môi trường/team hoặc namespace được phê duyệt**, không dựng một lab root toàn doanh nghiệp.

### 8.2 Tách biệt trust anchor và truststore

**Truststore production không chứa lab root**. Client test chỉ tin lab root trong phạm vi runtime test; tiến trình production chỉ dùng roots theo chính sách production.

Một truststore tách file nhưng được mount vào mọi service vẫn là **truststore chung về mặt tác động**. Cần tách cả **đường triển khai, quyền, tài khoản, CI/CD variables, container mounts và môi trường runtime**.

### 8.3 Tách biệt tên định danh và hạ tầng

Chỉ dùng **localhost, loopback hoặc DNS namespace được tổ chức dành riêng cho test**. Không đặt SAN là public company domain, không tạo root với tên giống CA thật, không cấp subject có vẻ như production để dễ copy nhầm.

Đặt tên file có prefix rõ:

~~~text
lab-root.p12
lab-root.pem
lab-server.p12
lab-server.csr
lab-server.pem
lab-client-trust.p12
~~~

Tên file chỉ là **tín hiệu vận hành**, không phải hàng rào bảo mật. Chặn rò rỉ thực tế bằng IAM, policy, review và kiểm thử truststore.

### 8.4 Thời hạn ngắn không thay thế giới hạn quyền

Certificate leaf sống 7 ngày vẫn có thể được lạm dụng nếu client production đã tin lab root. Thời hạn ngắn làm giảm thời gian rủi ro nhưng **không triệt tiêu khả năng giả mạo** trước khi hết hạn.

Root hết hạn 30 ngày cũng không bảo vệ trước mọi lỗ hổng vận hành, đặc biệt khi process không kiểm tra đúng policy hoặc vẫn giữ trust anchor cũ. Cần quản lý hiệu lực, thu hồi trust, xóa deployment sai và giám sát.

### 8.5 Không biến chứng thư lab thành production bằng cách import

**Quy trình hợp lệ để đi production không phải:**

~~~text
lab-root.pem
       |
import vào $JAVA_HOME/lib/security/cacerts
       |
"Prod dùng được"  <- LỖI PHÂN TÁCH TRUST
~~~

**Quy trình đúng:**

~~~text
Tài liệu/prototype lab
       |
Rà soát yêu cầu PKI production
       |
Xin/cấp key và certificate qua CA được phép
       |
Xác minh danh tính, SAN, EKU, chain, validity
       |
Cấp trust theo production policy
       |
Kiểm thử, audit, rotation và giám sát
~~~

Đảm bảo production không thừa hưởng lab root dưới dạng image layer, mounted volume, keystore, secret, env var hoặc config bundle.

## 9. Thu hồi, rotation và kết thúc lab

### 9.1 Khác nhau giữa xóa leaf và thu hồi trust root

Nếu chỉ xóa lab-server.p12, client lab vẫn tin root và root private key còn có thể cấp một leaf mới. **Muốn ngừng tin toàn bộ CA lab**, phải **gỡ root khỏi truststore của tất cả client lab đã cấp quyền** và thu hồi quyền phân phối truststore.

Nếu root key bị lộ, không thể “đổi password PKCS12” rồi xem mọi chứng thư CA đã cấp vẫn an toàn. Phải **ngừng tin root đó**, xây root mới nếu còn cần lab, cấp lại leaf, cập nhật truststore và đánh giá phạm vi ảnh hưởng.

### 9.2 Thu hồi chứng thư và kiểm tra trạng thái

**CRL và OCSP** là các cơ chế thường gặp để công bố/kiểm tra trạng thái certificate. Một CA lab keytool tạo bằng lệnh trên **không tự vận hành** cơ chế revocation. KeyUsage cRLSign chỉ mô tả **khả năng/quyền dùng khóa để ký CRL**, không tự tạo CRL.

Đối với production, cần chính sách rõ: có kiểm tra trạng thái hay không, responder/CRL ở đâu, hành vi khi offline, log và SLA thu hồi. Đừng dùng **PKIXParameters.setRevocationEnabled(false)** từ ví dụ lab để mặc định bỏ qua revocation trong hệ thống thật.

### 9.3 Checklist dọn dẹp sau bài học

1. Dừng tiến trình TLS server/client của lab.
2. Xác minh **không có CA lab** trong cacerts, OS trust, browser profile chung, image hoặc deployment production.
3. Thu hồi/cắt quyền truy cập tới truststore lab khi không còn dùng.
4. Xóa hoặc lưu trữ có kiểm soát **lab-root.p12/lab-server.p12**, password/backup liên quan theo chính sách tổ chức. Không upload file khóa vào ticket/chat.
5. Gỡ config per-process/truststore mount đã dùng để chạy bài.
6. Kiểm tra không còn môi trường test đang dùng certificate hết hạn/hỏng như một lý do để tắt TLS verification.
7. Nếu đã vô tình đưa root lab vào production trust, coi đây là **sự cố bảo mật cần xử lý**, lập danh sách hệ thống bị ảnh hưởng, loại root, luân chuyển thông tin cần thiết và đánh giá rủi ro giả mạo.

**Lưu ý:** Không xóa bừa cacerts hoặc truststore chung để “dọn lab”; chỉ gỡ đúng root lab **nếu đã được cài và theo quy trình thay đổi được cho phép**.

## 10. Những lỗi thường gặp cần tránh

1. **“CA do keytool tạo nên mặc nhiên được tất cả Java client tin.”** Sai: self-signed root chưa có trust anchor trong client.
2. **“Chỉ cần chữ ký leaf đúng là TLS an toàn.”** Sai: còn trust anchor, hostname, EKU/KU, thời gian, thuật toán, trạng thái thu hồi và policy.
3. **“Leaf CN=localhost là đủ, không cần SAN.”** Không phù hợp với HTTPS hostname verification hiện đại; cung cấp SAN đúng kiểu DNS/IP.
4. **“Root CA có thể dùng làm server identity.”** Không: server cần leaf private key/cert đúng mục đích; không đưa CA signing key vào server.
5. **“Root CA cho lab đã nằm trong truststore thì cho production dùng chung để tiện test.”** Sai nghiêm trọng: mở rộng quyền cấp chứng thư được tin.
6. **“Mật khẩu CA ngắn vì chỉ dùng 7 ngày không sao.”** Sai: CA signing key vẫn có thể bị lạm dụng và bị dò offline nếu file bị sao chép.
7. **“CSR đã yêu cầu SAN nào thì CA bắt buộc ký SAN đó.”** Sai: CA phải kiểm tra và quyết định cấp danh tính.
8. **“Đặt CA=true trên leaf để tiện dùng nhiều chỗ.”** Sai: làm sai ranh giới CA/end entity.
9. **“Có cRLSign nghĩa là đã tự động kiểm tra CRL.”** Sai: cần cơ chế phát hành và kiểm tra trạng thái.
10. **“PKIX demo không bật revocation, vậy production cũng tắt.”** Sai: demo không triển khai CRL/OCSP; production phải theo policy.
11. **“Dùng trust-all giải quyết lỗi SSLHandshakeException nhanh mà không ảnh hưởng bảo mật.”** Sai: bỏ xác minh identity/trust.
12. **“HostnameVerifier trả true mọi nơi là hợp lý trên localhost.”** Sai: dễ rò vào runtime khác và làm mất bảo vệ.
13. **“Nhập lab-root.pem vào cacerts chỉ ảnh hưởng app của tôi.”** Sai: có thể ảnh hưởng mọi app dùng kho trust đó.
14. **“Private key CA có thể copy vào server vì nó cùng một nhóm test.”** Sai: server bị chiếm sẽ trao cho attacker quyền phát hành certificate.
15. **“Dùng root validity 30 ngày bảo đảm root an toàn.”** Sai: expiry không giải quyết việc root key bị lộ trước khi hết hạn.
16. **“Tên file demo.p12 nói lên đây là test, không thể dùng nhầm.”** Sai: phải dùng kiểm soát IAM/deployment/trust policy.
17. **“Đã import leaf reply nên không cần kiểm tra chain.”** Sai: xác minh alias, chain, issuer, SAN, EKU và signature.
18. **“Một máy tin CA lab nghĩa là một máy khác cũng tin.”** Sai: trust configuration nằm tại từng runtime/system/profile.
19. **“Có thể dùng cùng root lab để ký cert cho domain production nhằm test staging.”** Sai: cấp theo namespace test và quy trình được phê duyệt.
20. **“Certificate hết hạn thì không cần gỡ trust root.”** Sai: root được tin vẫn có thể có tác dụng đối với certificate khác nếu còn hợp lệ hoặc các client có chính sách xử lý khác.

## 11. Bài tập tự kiểm tra

**Câu 1.** Một root CA self-signed có tự trở thành trusted root trên mọi Java runtime không?

**Trả lời:** Không. Client phải được cấp quyền tin một trust anchor xác thực nguồn gốc; tự ký không tự tạo trust.

**Câu 2.** Tại sao truststore lab phải tách khỏi cacerts hoặc truststore production?

**Trả lời:** Vì thêm root lab vào truststore sản xuất cho phép client production tin các cert do CA lab phát hành trong phạm vi quy tắc xác minh, tạo bề mặt giả mạo endpoint.

**Câu 3.** CA lab khác leaf server ở BasicConstraints và KeyUsage thế nào?

**Trả lời:** CA cần CA=true và keyCertSign phù hợp; leaf cần CA=false và key usage/EKU phù hợp với TLS server, không có quyền ký chứng thư CA.

**Câu 4.** Trong bài, tại sao root có pathLen=0 mà vẫn ký được leaf?

**Trả lời:** pathLen giới hạn số intermediate CA không self-issued theo sau CA trong một certification path; nó không chặn cấp end-entity certificate.

**Câu 5.** Chứng thư server có CN=localhost nhưng SAN chỉ chứa địa chỉ khác; HTTPS đến localhost có nên được chấp nhận không?

**Trả lời:** Không theo chính sách hostname validation hiện đại. Phải có SAN DNS:localhost phù hợp và trust/path vẫn phải đúng.

**Câu 6.** CSR chứa SAN của domain thật có tự chứng minh quyền sở hữu domain đó không?

**Trả lời:** Không. Chữ ký CSR chỉ thể hiện quyền giữ private key CSR; CA phải xác minh quyền sở hữu/tên trước khi phát hành.

**Câu 7.** Keytool tạo server keystore với self-signed certificate. Khi nào nó có chain root→leaf?

**Trả lời:** Khi CA ký CSR, root public certificate được import để dựng chain và certificate reply được import vào đúng alias private key, rồi kiểm tra chain.

**Câu 8.** Trong Java code, việc đưa lab root vào TrustAnchor mang ý nghĩa gì?

**Trả lời:** Ứng dụng chủ động tin CA đó; PKIX validator kiểm tra đường dẫn tới anchor. Không thể dùng root tùy ý lấy từ mạng mà không xác minh danh tính/fingerprint.

**Câu 9.** Lab chưa có CRL/OCSP nên setRevocationEnabled(false). Điều này có chứng minh revocation an toàn không?

**Trả lời:** Không. Đó là giới hạn cố ý của lab; production cần vận hành revocation theo policy và đánh giá hành vi khi dịch vụ trạng thái lỗi.

**Câu 10.** Nếu CA private key bị lộ, chỉ thay store password là đủ chưa?

**Trả lời:** Chưa. Kẻ tấn công có thể có private key và phát hành certificate. Cần ngừng tin CA bị lộ, cấp CA/leaf mới và đánh giá sự cố.

**Câu 11.** Đã có lab-root.pem là client có thể dùng nó như identity keystore không?

**Trả lời:** Không. PEM public root certificate không có private key; client identity (nếu mTLS) cần private key và cert chain riêng.

**Câu 12.** Một HTTPS client truy cập localhost nhưng truststore sai, có nên tắt certificate verification không?

**Trả lời:** Không. Phải tải đúng truststore lab, xác minh root fingerprint, chain, SAN và chính sách TLS; fail closed nếu không xác thực được.

## 12. Tài liệu chính thức và tiêu chuẩn

- [Oracle Java SE 17 — keytool](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html): các lệnh genkeypair, certreq, gencert, importcert, exportcert, extension BC/KU/EKU/SAN và thứ tự import chain.
- [Oracle Java SE 17 — KeyStore](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.html): API file keystore/truststore, PKCS12, alias, certificate chain.
- [Oracle Java SE 17 — PKIXParameters](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/PKIXParameters.html): TrustAnchor và tham số kiểm tra certification path.
- [Oracle Java SE 17 — CertPathValidator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertPathValidator.html): xây và kiểm tra đường dẫn chứng thực theo PKIX.
- [Oracle Java SE 17 — Java PKI Programmer's Guide](https://docs.oracle.com/en/java/javase/17/security/java-pki-programmers-guide.html): cơ chế trust anchors, PKIX và extension/checker.
- [Oracle Java SE 17 — JSSE Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-secure-socket-extension-jsse-reference-guide.html): KeyManager/TrustManager, TLS và truststore.
- [RFC 5280 — Internet X.509 PKI Certificate and CRL Profile](https://www.rfc-editor.org/rfc/rfc5280.html): BasicConstraints, KeyUsage, EKU, SAN, path validation, revocation và trust model.

## Kết luận

**Lập một CA thử nghiệm không khó về mặt lệnh keytool, nhưng quyết định CA đó được tin ở đâu mới là nội dung bảo mật cốt lõi.** Một bộ lab đúng gồm: root CA có CA=true và khóa riêng được giữ kín; leaf có CA=false, SAN=localhost, EKU=serverAuth và được ký từ CSR; server keystore chứa **private key leaf + certificate chain**; client có **truststore PKCS12 độc lập** chỉ chứa public root certificate đã xác minh fingerprint. Java có thể kiểm tra path bằng PKIX; HTTPS còn phải kiểm tra hostname, mục đích và các điều kiện TLS.

**Tuyệt đối giữ CA/truststore/key thử nghiệm ra khỏi production, không sửa cacerts hệ thống để tránh lỗi, không tắt xác thực TLS và không lấy lab root làm trust anchor chung.** Khi lên production phải dùng quy trình PKI được phê duyệt, danh tính được xác minh, chứng thư/khóa riêng và quản lý vòng đời, audit, revocation phù hợp.


</details>

- [Quay lại đầu trang](#back-to-top)
