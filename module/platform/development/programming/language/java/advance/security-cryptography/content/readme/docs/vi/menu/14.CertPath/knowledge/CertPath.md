<a id="back-to-top"></a>

# 14. Đường dẫn tin cậy

## Menu
- [14.1 Mô hình CertPath, TrustAnchor và chứng thư trung gian](#cert-path-001)
- [14.2 Phân biệt xây đường dẫn bằng CertPathBuilder với kiểm tra bằng CertPathValidator](#cert-path-002)
- [14.3 Cấu hình PKIXParameters, PKIXBuilderParameters và X509CertSelector](#cert-path-003)
- [14.4 Thu thập chứng thư hoặc CRL qua CertStore](#cert-path-004)
- [14.5 Kiểm tra thời gian, ràng buộc CA, độ dài đường dẫn và mục đích sử dụng](#cert-path-005)
- [14.6 Thu hồi bằng CRL, OCSP và PKIXRevocationChecker](#cert-path-006)
- [14.7 Chính sách khi không lấy được trạng thái thu hồi và hệ quả của SOFT_FAIL](#cert-path-007)
- [14.8 Phân tích CertPathValidatorException theo vị trí chứng thư và nguyên nhân](#cert-path-008)
- [14.9 Giới hạn của PKIX: kiểm tra danh tính máy chủ là một bước riêng](#cert-path-009)

## <a id="cert-path-001">14.1 Mô hình CertPath, TrustAnchor và chứng thư trung gian</a>

<details>
<summary>Xem chi tiết</summary>

# 14.1 Mô hình `CertPath`, `TrustAnchor` và chứng thư trung gian

## 1. Mục tiêu: một chứng thư hợp lệ về chữ ký chưa đủ để được tin cậy

Trong Java Security / PKI, **chứng thư X.509** (*certificate*) thường gắn một **public key** với một **subject** (chủ thể) và mang chữ ký của **issuer** (bên cấp). Việc một chứng thư có chữ ký kiểm tra được **không tự động** có nghĩa ứng dụng phải tin public key ấy: trước hết phải xác định issuer đó có được trao quyền cấp chứng thư hay không, rồi lần ngược qua các **chứng thư CA trung gian** đến **điểm neo tin cậy (`TrustAnchor`)** đã được ứng dụng chấp nhận từ trước.

Bài học này giải thích ba thành phần chính:

- **`CertPath`:** một **đường dẫn chứng nhận** (*certification path*), tức chuỗi chứng thư mục tiêu và các chứng thư trung gian theo quy tắc thứ tự của Java.
- **`TrustAnchor`:** điểm xuất phát được **tin cậy theo chính sách/cấu hình bên ngoài đường dẫn**, thường đại diện cho CA gốc, có thể biểu diễn bằng chứng thư CA đã được tin cậy hoặc tên CA + public key.
- **Chứng thư trung gian (`intermediate CA certificate`):** chứng thư CA nằm giữa certificate mục tiêu và điểm neo; nó **không mặc nhiên được tin cậy**, mà được xác thực bởi CA ở phía trên theo quy tắc PKIX.

Nội dung dùng mô hình **PKIX / RFC 5280** và Java 17+. Ví dụ thực nghiệm có **root → intermediate → leaf**, sử dụng `CertificateFactory`, `CertPathValidator`, `PKIXParameters`, `CertPathBuilder`, `CertStore` và `TrustAnchor`.

**Điều quan trọng nhất:** *Dữ liệu certificate chain được bên kia gửi đến không phải danh sách trusted roots.* CA gốc chỉ là trust anchor sau khi **chính ứng dụng hoặc môi trường tin cậy đã phê duyệt** nó. Không được chấp nhận bất kỳ chứng thư tự ký (*self-signed*) nào chỉ vì chữ ký tự kiểm tra thành công.

## 2. Một đường dẫn tin cậy trông như thế nào?

Giả sử dịch vụ `demo.example.invalid` có chứng thư leaf do **Intermediate CA** ký; Intermediate CA do **Root CA** ký:

```text
                          PHÍA CẤP / CẤU HÌNH TIN CẬY

         +----------------------------------------------------+
         | Root CA: TrustAnchor đã được cài và tin cậy        |
         | Subject: CN=Training Root CA                        |
         | Public key gốc được quản trị, không lấy theo peer  |
         +---------------------------+------------------------+
                                     |
                         ký chứng thư Intermediate
                                     v
         +----------------------------------------------------+
         | Intermediate CA certificate                        |
         | Subject: CN=Training Intermediate CA                |
         | Issuer:  CN=Training Root CA                        |
         | BasicConstraints: CA=true, pathLenConstraint=0     |
         | KeyUsage: keyCertSign (+ cRLSign nếu có CRL)         |
         +---------------------------+------------------------+
                                     |
                         ký chứng thư leaf
                                     v
         +----------------------------------------------------+
         | End-entity / leaf certificate                      |
         | Subject: CN=demo.example.invalid                    |
         | Issuer:  CN=Training Intermediate CA                |
         | BasicConstraints: CA=false                         |
         | Dùng theo mục đích và định danh ứng dụng            |
         +----------------------------------------------------+

Đối tượng Java CertPath (X.509) theo thứ tự:
    [leaf, intermediate]                // KHÔNG đưa root vào đây

Đầu vào tin cậy riêng:
    Set<TrustAnchor> = {root đã tin cậy}
```

**Hướng quan hệ chữ ký** là Root ký Intermediate, Intermediate ký Leaf. **Hướng sắp thứ tự trong `CertPath` của Java** lại là từ *đối tượng cần xác thực* đi dần đến *CA gần điểm neo nhất*:

```text
getCertificates().get(0)  -> leaf (target)
getCertificates().get(1)  -> intermediate
TrustAnchor               -> root (ở ngoài CertPath)
```

Tài liệu Java SE 17 về `CertPath` và *Java PKI Programmer's Guide* nêu rõ **certificate đại diện cho `TrustAnchor` không nên nằm trong certification path**. Path do `CertPathBuilder` tạo theo PKIX cũng **không chứa** root/trust anchor; lấy điểm neo qua `PKIXCertPathBuilderResult.getTrustAnchor()`.

### 2.1 Các vai trò không được đánh đồng

| Đối tượng | Nó đại diện cho gì? | Nó có sẵn đáng tin không? | Đặt ở đâu trong Java? |
| --- | --- | --- | --- |
| Leaf / end entity | Danh tính hoặc public key cần xác minh cho ứng dụng (máy chủ TLS, người ký, v.v.) | **Không**; phải có path và policy phù hợp | Chứng thư đầu tiên trong `CertPath` |
| Intermediate CA | CA ủy quyền cấp chứng thư cấp dưới | **Không tự có**; phải xác minh chuỗi chữ ký, BasicConstraints và các ràng buộc | Chứng thư tiếp theo trong `CertPath` |
| Root CA certificate | Thường là chứng thư CA tự ký (*self-signed*) chứa public key gốc | Chỉ khi được **chấp nhận/cài sẵn qua kênh đáng tin** | Thường dùng để tạo `TrustAnchor`, không đưa vào path |
| `TrustAnchor` | Thông tin CA đã được tin cậy để kết thúc kiểm tra đường dẫn | **Có, vì đã được chọn bởi chính sách của ứng dụng** | Tập `Set<TrustAnchor>` trong `PKIXParameters` |
| `CertPath` | Thứ tự các chứng thư cần được xác thực | **Chưa** cho đến khi PKIX validation thành công | `java.security.cert.CertPath` |
| `PKIXCertPathValidatorResult` | Kết quả PKIX validation thành công cùng trust anchor đã dùng | Có ý nghĩa theo **anchors + policy + thời điểm** đã truyền | Giá trị trả về sau `CertPathValidator.validate` |

**Một intermediate cũng có thể được tổ chức chủ động cấu hình làm `TrustAnchor`.** Khi đó điểm neo là intermediate đã được phê duyệt, còn đường dẫn có thể ngắn hơn. Sự khác biệt là **vai trò chính sách** (*anchor*) so với **vai trò cấu trúc** (*CA nằm trong path*), không chỉ nằm ở trường `BasicConstraints`.

## 3. Nền tảng X.509: subject, issuer, chữ ký và quyền làm CA

Một chứng thư X.509 có những trường/extension phục vụ xác minh đường dẫn:

| Trường/extension | Chức năng trong kiểm tra đường dẫn | Lưu ý |
| --- | --- | --- |
| `subject` | Định danh X.500 của chủ thể được cấp | Không đồng nghĩa đã khớp hostname/định danh ứng dụng |
| `issuer` | Định danh bên phát hành | Hỗ trợ nối với CA cấp trên; **so trùng tên không đủ** |
| `subjectPublicKeyInfo` | Public key của leaf hoặc CA | Dùng để xác minh chữ ký ở tầng dưới tương ứng |
| `signatureAlgorithm` và `signatureValue` | Thuật toán/chữ ký của issuer lên TBSCertificate | PKIX phải kiểm tra chữ ký bằng public key issuer và constraint thuật toán |
| `notBefore` / `notAfter` | Khoảng thời gian hợp lệ | Validation theo ngày đã cấu hình; không được bỏ qua |
| `BasicConstraints` | `CA=true/false`, cùng `pathLenConstraint` nếu có | Intermediate ký chứng thư cấp dưới phải có quyền CA |
| `KeyUsage` | Quyền dùng key: `keyCertSign`, `digitalSignature`, `keyEncipherment`, ... | CA ký chứng thư cần `keyCertSign` nếu extension hiện diện |
| `ExtendedKeyUsage` | Mục đích dùng end-entity: TLS `serverAuth`, `clientAuth`, `codeSigning`, ... | Ứng dụng cần xác thực đúng purpose |
| `SubjectAlternativeName` (SAN) | Tên DNS/IP/danh tính thay thế của leaf | TLS hostname matching **là bước riêng**, không chỉ validate path |
| `NameConstraints` | Giới hạn namespace CA có thể cấp | PKIX áp dụng constraints theo RFC |
| `CertificatePolicies`, `PolicyConstraints` | Kiểm soát chính sách chứng thư | Quan trọng với nhiều hệ thống PKI chuyên dụng |
| `AuthorityKeyIdentifier` (AKI), `SubjectKeyIdentifier` (SKI) | Gợi ý tìm issuer/khóa tương ứng | Hỗ trợ path building; **không phải chứng minh mật mã duy nhất** |
| CRL Distribution Points / AIA OCSP | Thông tin truy vấn trạng thái thu hồi | Không có nghĩa runtime sẽ tự truy vấn thành công |

**Ví dụ `BasicConstraints`:** root có `CA=true, pathLenConstraint=1`; intermediate có `CA=true, pathLenConstraint=0`. Intermediate `pathLen=0` vẫn có thể ký **leaf end-entity**, nhưng không cho phép thêm một CA trung gian **không self-issued** khác bên dưới nó trong một đường dẫn được phép. Path-length constraint không phải giới hạn số lượng leaf do CA cấp.

**Ví dụ `KeyUsage`:** intermediate có `keyCertSign`, root cũng có `keyCertSign`; leaf dùng `digitalSignature` theo nhu cầu ứng dụng. Không hoán đổi `digitalSignature` của leaf thành quyền ký CA. Một CA chỉ có subject tên “CA” không đủ để hợp thức hóa việc cấp chứng thư.

### 3.1 Vì sao self-signed root chưa tự đáng tin?

Chứng thư tự ký nghĩa là public key của chứng thư có thể kiểm tra chữ ký của **chính chứng thư đó**. Điều này chứng minh tính **tự nhất quán**, không chứng minh danh tính CA hoặc sự chấp thuận của ứng dụng. `TrustAnchor` đến từ tập tin cấu hình, truststore do quản trị viên cung cấp, certificate pinning theo cơ chế đã xác thực hoặc chính sách tin cậy tương đương.

Nếu attacker đưa một chuỗi:

```text
attacker leaf <- attacker self-signed root
```

thì mọi chữ ký có thể hợp lệ về toán học. Nhưng nếu attacker root **không nằm trong tập trust anchors đã phê duyệt**, PKIX validation phải không thể kết thúc ở root ấy. **Tuyệt đối không tạo `TrustAnchor` trực tiếp từ root bất kỳ nằm trong chain do peer gửi.**

### 3.2 Trust anchor không luôn là một chứng thư đầy đủ

Java `TrustAnchor` có các constructor thường gặp:

```java
// Minh họa trong phương thức có quyền truy cập trustedRoot:
TrustAnchor byCertificate = new TrustAnchor(trustedRoot, null);

// Tương đương khái niệm: đặt tên CA + public key đáng tin
TrustAnchor byNameAndKey =
        new TrustAnchor(
                trustedRoot.getSubjectX500Principal(),
                trustedRoot.getPublicKey(),
                null);
```

Tham số thứ hai/ba `null` ở đây là **name constraints tùy chọn**. Constructor dùng tên + public key không chứa toàn bộ chứng thư self-signed. Cả hai cách tạo chỉ được dùng với **dữ liệu đã tin cậy từ trước**, không biến bất kỳ certificate tải được thành CA được chấp nhận.

## 4. `CertPath` trong Java được tạo và đọc thế nào?

Các thành phần liên quan đều thuộc `java.security.cert`:

- `X509Certificate`: đại diện một chứng thư X.509, cho phép đọc subject, issuer, extension, public key, chữ ký, ngày hiệu lực.
- `CertificateFactory.getInstance("X.509")`: phân tích chứng thư DER/PEM và tạo `CertPath` theo collection/list.
- `CertPath.getCertificates()`: đọc danh sách bất biến các chứng thư của path.
- `CertPathValidator.getInstance("PKIX")`: **xác thực path đã xác định**.
- `CertPathBuilder.getInstance("PKIX")`: **tìm path phù hợp** từ nguồn chứng thư và trust anchors theo constraints.
- `PKIXParameters`: cấu hình anchors, thời điểm, policy, revocation, checkers cho validator.
- `PKIXBuilderParameters`: kế thừa `PKIXParameters`, bổ sung target selector, `CertStore` và `maxPathLength`.
- `CertStore.getInstance("Collection", ...)`: cung cấp candidate certificates (và có thể CRLs) từ bộ nhớ cho path builder.

Mô hình quan hệ:

```text
Dữ liệu đã có sẵn path
    [leaf, intermediate]
            |
            v
  CertificateFactory.generateCertPath(List)
            |
            v
  CertPathValidator("PKIX")
      + PKIXParameters(
            trusted anchors,
            date, revocation, policy, checkers
        )
            |
          success? ---- no ---> CertPathValidatorException
            |
           yes
            v
  PKIXCertPathValidatorResult
      .getTrustAnchor()
      .getPublicKey()
      .getPolicyTree()

Dữ liệu có nhiều certificate candidates, chưa chọn path
    leaf + intermediates ở CertStore
            |
            v
  CertPathBuilder("PKIX")
      + PKIXBuilderParameters(anchors, X509CertSelector)
      + CertStore chứa candidates
            |
          success?
            |
           yes
            v
  PKIXCertPathBuilderResult
      .getCertPath()      // thứ tự leaf -> intermediates; không có anchor
      .getTrustAnchor()
```

`CertificateFactory.generateCertPath(List.of(leaf, intermediate))` **chỉ tạo cấu trúc**; nó không thực hiện PKIX validation. Cũng tương tự, `X509Certificate.checkValidity()` chỉ kiểm tra thời gian riêng của chứng thư; `leaf.verify(intermediate.getPublicKey())` chỉ kiểm tra một chữ ký của leaf. **Không phép nào thay thế xác thực toàn bộ path.**

### 4.1 Thứ tự không đúng và thiếu intermediate

Trong Java X.509 `CertPath`, đường dẫn thường là:

```java
// Minh họa, leaf và intermediate là các X509Certificate đã tải:
CertificateFactory factory = CertificateFactory.getInstance("X.509");
CertPath correct = factory.generateCertPath(
        java.util.List.of(leaf, intermediate)); // leaf trước
```

Các trường hợp thường sai:

- `[intermediate, leaf]` đảo thứ tự, dù cả hai chứng thư đều hợp lệ riêng.
- `[leaf]` thiếu intermediate khi leaf không trực tiếp được root cấp.
- `[leaf, intermediate, root]` đưa root vào path đồng thời dùng root làm `TrustAnchor`, trái quy ước PKIX Java.
- `[leaf, wrongIntermediate]` issuer name/key/chữ ký hoặc constraints không khớp.
- Chỉ kiểm tra `leaf.getIssuerX500Principal().equals(intermediate.getSubjectX500Principal())` rồi chấp nhận: tên khớp chưa chứng minh chữ ký đúng, CA có quyền hay đường dẫn kết thúc ở anchor tin cậy.

Đôi khi dữ liệu TLS hoặc PEM gửi theo thứ tự khác, hoặc có nhiều cross-signed intermediates. Dùng **path builder** để tìm một đường dẫn hợp lệ thay vì giả định index/issuer name đơn giản là đủ. PKIX Builder chỉ tìm trong **các nguồn `CertStore`/cơ chế được cấu hình**; không bảo đảm tự tải hết intermediates từ Internet.

## 5. Khác nhau giữa `CertPathValidator` và `CertPathBuilder`

| Vấn đề | `CertPathValidator` | `CertPathBuilder` |
| --- | --- | --- |
| Câu hỏi | *Đường dẫn cho trước này có hợp lệ không?* | *Có thể tạo một đường dẫn hợp lệ tới trust anchor không?* |
| Đầu vào chính | `CertPath` đã có thứ tự + `PKIXParameters` | `PKIXBuilderParameters` với target `CertSelector`, `CertStore` và anchors |
| Trả về khi thành công | `PKIXCertPathValidatorResult` | `PKIXCertPathBuilderResult` |
| Có tự tìm intermediate? | Không, vì path đã được đưa vào | Có, trong tập nguồn certificates/constraints được cấu hình |
| Trust anchor có là phần tử cuối `CertPath`? | **Không** | **Không** |
| Nếu thiếu intermediate | Thường validation không thể nối path tới root đã tin | Builder có thể tìm được nếu candidate intermediate ở `CertStore` |
| Mục tiêu | Kiểm tra một path xác định | Tìm và kiểm tra một path hợp lệ |

**Câu nhớ nhanh:** *Builder tìm đường; Validator kiểm tra đường.* Một `CertPathBuilder` PKIX tạo đường dẫn đã được kiểm tra trong quy trình build, nên trả về path và trust anchor đã chọn; với validation một đường dẫn sẵn có, bạn vẫn cần chọn anchors/chính sách đúng.

## 6. Thực nghiệm Java 17: tạo ba chứng thư bằng `keytool`

Phần này dùng **fixture hoàn toàn cục bộ**, không tải private key của người khác và không cần kết nối CA thật. Mọi mật khẩu `changeit` chỉ phục vụ bài lab, tuyệt đối **không dùng cho hệ thống thực**.

### 6.1 Chuẩn bị và sinh ba cặp khóa

Mở shell có `keytool`, `javac`, `java` từ JDK 17+, tạo **thư mục bài thực hành riêng**:

```bash
mkdir pkix-lab
cd pkix-lab
```

Tạo **Root CA tự ký** với quyền CA và một intermediate ở dưới (`pathlen:1`). Lệnh có thể chạy trên shell xử lý dấu ngoặc kép thông thường (Bash, PowerShell):

```bash
keytool -genkeypair -alias root -dname "CN=Training Root CA, O=Local Demo" -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -validity 365 -keystore root.p12 -storetype PKCS12 -storepass changeit -keypass changeit -ext "BC:critical=ca:true,pathlen:1" -ext "KU:critical=keyCertSign,cRLSign" -noprompt
```

Tạo một **cặp key của Intermediate CA** và một **cặp key của leaf**. Ở thời điểm sinh keypair, `keytool` có thể gắn chứng thư tự ký tạm thời cho entry; chứng thư có quan hệ CA thực trong lab sẽ được **ký qua CSR ở bước tiếp**:

```bash
keytool -genkeypair -alias intermediate -dname "CN=Training Intermediate CA, O=Local Demo" -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -validity 365 -keystore intermediate.p12 -storetype PKCS12 -storepass changeit -keypass changeit -ext "BC:critical=ca:true,pathlen:0" -ext "KU:critical=keyCertSign,cRLSign" -noprompt
keytool -genkeypair -alias leaf -dname "CN=demo.example.invalid, O=Local Demo" -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -validity 365 -keystore leaf.p12 -storetype PKCS12 -storepass changeit -keypass changeit -noprompt
```

### 6.2 Root cấp Intermediate; Intermediate cấp leaf

**Bước 1 — CSR của Intermediate:** tạo yêu cầu chứng thư từ public key / subject của Intermediate:

```bash
keytool -certreq -alias intermediate -keystore intermediate.p12 -storepass changeit -file intermediate.csr
```

**Bước 2 — Root ký và cấp chứng thư cho Intermediate:**

```bash
keytool -gencert -alias root -keystore root.p12 -storepass changeit -infile intermediate.csr -outfile intermediate.pem -rfc -validity 365 -ext "BC:critical=ca:true,pathlen:0" -ext "KU:critical=keyCertSign,cRLSign"
```

**Bước 3 — CSR của leaf:**

```bash
keytool -certreq -alias leaf -keystore leaf.p12 -storepass changeit -file leaf.csr
```

**Bước 4 — Intermediate ký chứng thư leaf**, đánh dấu end entity, `serverAuth` và DNS SAN minh họa:

```bash
keytool -gencert -alias intermediate -keystore intermediate.p12 -storepass changeit -infile leaf.csr -outfile leaf.pem -rfc -validity 365 -ext "BC:critical=ca:false" -ext "KU:critical=digitalSignature,keyEncipherment" -ext "EKU=serverAuth" -ext "SAN=dns:demo.example.invalid"
```

**Bước 5 — Xuất chứng thư công khai của Root để làm trusted anchor trong lab:**

```bash
keytool -exportcert -alias root -keystore root.p12 -storepass changeit -file root.pem -rfc
```

Kết quả cần có:

```text
pkix-lab/
  root.p12                (có PRIVATE KEY của root: giữ kín)
  intermediate.p12        (có PRIVATE KEY của intermediate: giữ kín)
  leaf.p12                (có PRIVATE KEY của leaf: giữ kín)
  intermediate.csr        (chỉ phục vụ cấp chứng thư)
  leaf.csr
  root.pem                (public certificate; lab phê duyệt làm anchor)
  intermediate.pem        (intermediate certificate do root ký)
  leaf.pem                (leaf certificate do intermediate ký)
```

Các file `.pem` là **chứng thư X.509 công khai**, không chứa private key; `.p12` có private key và **không** được đưa lên Internet hoặc commit repository. `-rfc` của `keytool` xuất PEM (Base64 cùng header/footer). Các lệnh được dùng để tạo dữ liệu **học tập**; PKI sản xuất cần quy trình phát hành CA, bảo vệ signing keys, key ceremony, certificate policies, thu hồi và quản lý khóa nghiêm ngặt.

**Chi tiết dễ nhầm trong lab:** `intermediate.p12` ban đầu chứa keypair và chứng thư tự ký tạm; lệnh `-gencert` sử dụng **private key tương ứng** để ký `leaf.pem`. Đường dẫn cần validate trong bước tiếp chỉ dùng **chứng thư CA được Root ký là `intermediate.pem`**, không nhầm với chứng thư tự ký tạm trong entry của keystore.

## 7. Chương trình Java 17 đầy đủ: validate path, thử lỗi, rồi build path

Tạo file **`CertPathTrustModelDemo.java`** trong cùng thư mục chứa ba file PEM. Chương trình:

1. Tải `root.pem`, `intermediate.pem`, `leaf.pem` thành `X509Certificate` bằng `CertificateFactory`.
2. Kiểm tra thuộc tính CA (`getBasicConstraints()`).
3. Tạo `CertPath` theo thứ tự **`[leaf, intermediate]`**, tạo `TrustAnchor(root, null)` và dùng `CertPathValidator("PKIX")` để xác thực.
4. Thử thiếu intermediate và đảo thứ tự: kỳ vọng bị từ chối.
5. Tạo `CertStore` chứa candidate certificates và dùng `CertPathBuilder("PKIX")` để **tìm** đường dẫn.
6. Quan sát result cho thấy path có **2 certificate**, root chỉ xuất hiện ở `TrustAnchor`.

```java
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.cert.CertPath;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertStore;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXCertPathBuilderResult;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;

public final class CertPathTrustModelDemo {
    private static X509Certificate readCert(String name) throws Exception {
        try (InputStream in = Files.newInputStream(Path.of(name))) {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(in);
        }
    }

    private static PKIXParameters validationParams(X509Certificate root)
            throws GeneralSecurityException {
        PKIXParameters params = new PKIXParameters(
                Set.of(new TrustAnchor(root, null)));
        // Lab-only: no CRL/OCSP infrastructure for these training certificates.
        params.setRevocationEnabled(false);
        return params;
    }

    private static boolean isRejected(CertPath path, X509Certificate root)
            throws GeneralSecurityException {
        try {
            CertPathValidator.getInstance("PKIX").validate(
                    path, validationParams(root));
            return false;
        } catch (CertPathValidatorException expected) {
            return true;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Usage: java CertPathTrustModelDemo root.pem intermediate.pem leaf.pem");
        }
        X509Certificate root = readCert(args[0]);
        X509Certificate intermediate = readCert(args[1]);
        X509Certificate leaf = readCert(args[2]);

        System.out.println("Intermediate is CA: " +
                (intermediate.getBasicConstraints() >= 0));
        System.out.println("Leaf is CA: " +
                (leaf.getBasicConstraints() >= 0));

        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        CertPath supplied = factory.generateCertPath(
                List.of(leaf, intermediate)); // Target first; no root.
        PKIXCertPathValidatorResult checked =
                (PKIXCertPathValidatorResult)
                CertPathValidator.getInstance("PKIX").validate(
                        supplied, validationParams(root));
        System.out.println("Supplied path size: " +
                supplied.getCertificates().size());
        System.out.println("Validated trust anchor is root: " +
                root.equals(checked.getTrustAnchor().getTrustedCert()));

        CertPath missingIntermediate = factory.generateCertPath(List.of(leaf));
        System.out.println("Missing intermediate rejected: " +
                isRejected(missingIntermediate, root));

        CertPath reversed = factory.generateCertPath(
                List.of(intermediate, leaf));
        System.out.println("Wrong certificate order rejected: " +
                isRejected(reversed, root));

        X509CertSelector target = new X509CertSelector();
        target.setCertificate(leaf);
        PKIXBuilderParameters builderParams = new PKIXBuilderParameters(
                Set.of(new TrustAnchor(root, null)), target);
        builderParams.setRevocationEnabled(false); // Lab only.
        builderParams.setMaxPathLength(1);
        builderParams.addCertStore(CertStore.getInstance(
                "Collection", new CollectionCertStoreParameters(
                        List.of(leaf, intermediate))));
        PKIXCertPathBuilderResult built = (PKIXCertPathBuilderResult)
                CertPathBuilder.getInstance("PKIX").build(builderParams);
        System.out.println("Builder path size: " +
                built.getCertPath().getCertificates().size());
        System.out.println("Builder path starts with leaf: " +
                leaf.equals(built.getCertPath().getCertificates().get(0)));
        System.out.println("Builder trust anchor is root: " +
                root.equals(built.getTrustAnchor().getTrustedCert()));
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 CertPathTrustModelDemo.java
java CertPathTrustModelDemo root.pem intermediate.pem leaf.pem
```

Stdout **đã kiểm chứng** với ba chứng thư được tạo theo lệnh lab:

```text
Intermediate is CA: true
Leaf is CA: false
Supplied path size: 2
Validated trust anchor is root: true
Missing intermediate rejected: true
Wrong certificate order rejected: true
Builder path size: 2
Builder path starts with leaf: true
Builder trust anchor is root: true
```

**Giải thích các dòng:**

- `Intermediate is CA: true` vì `intermediate.pem` có BasicConstraints CA hợp lệ.
- `Leaf is CA: false`: leaf không có quyền cấp chứng thư CA.
- `Supplied path size: 2`: path chỉ chứa **leaf và intermediate**; root nằm trong tập `TrustAnchor`.
- `Validated trust anchor is root: true`: `validate` đã hoàn tất và trả về anchor chính xác theo cấu hình lab.
- `Missing intermediate rejected: true`: leaf được **intermediate** cấp, không trực tiếp bởi root; path `[leaf]` không nối được tới anchor root.
- `Wrong certificate order rejected: true`: `CertPath` đảo thứ tự sai, không phải đường từ target về anchor.
- `Builder path size: 2`: builder tìm intermediate từ `CertStore` với selector chọn leaf.
- `Builder path starts with leaf: true`: xác nhận quy ước sắp thứ tự của Java.
- `Builder trust anchor is root: true`: builder trả riêng thông tin root đáng tin.

**Giới hạn có chủ đích:** `setRevocationEnabled(false)` ở **cả validator lẫn builder** là để lab không phải thiết lập CRL/OCSP cho CA tự tạo. Trong production, **không vô hiệu hóa revocation chỉ vì môi trường thiếu mạng**. Phải triển khai chính sách thu hồi phù hợp (OCSP/CRL, timeout, caching, xử lý offline và lỗi) và kiểm thử thực tế. Tương tự, chương trình **không thực hiện xác minh hostname TLS**, không triển khai chứng chỉ CT, không tự kiểm tra mọi yêu cầu nghiệp vụ lên leaf: đây là ví dụ **PKIX path model**, không phải HTTPS client hoàn chỉnh.

Một lưu ý nữa: thời hạn fixture là **365 ngày kể từ thời điểm tạo**. Nếu để file trong nhiều năm rồi chạy lại, có thể fail vì expired; hãy tạo mới fixture hoặc thiết lập validation date theo tình huống kiểm thử có chủ đích, không “bỏ qua” ngày hiệu lực trong production.

## 8. Đặt `TrustAnchor` đúng chỗ: `PKIXParameters` và `KeyStore`

Ở cấp API có hai cách thông dụng:

### 8.1 Truyền tập anchors trực tiếp

```java
// Minh họa đặt trong phương thức throws GeneralSecurityException.
Set<TrustAnchor> anchors =
        Set.of(new TrustAnchor(trustedRootCertificate, null));
PKIXParameters params = new PKIXParameters(anchors);
```

- `PKIXParameters(Set<TrustAnchor>)` yêu cầu một tập **không rỗng**; nếu rỗng sẽ có `InvalidAlgorithmParameterException`.
- `setDate(Date)` có thể dùng để xác minh tại một thời điểm; giá trị `null` mặc định nghĩa là thời điểm hiện tại khi validate, không có nghĩa “không cần kiểm tra thời gian”.
- `setInitialPolicies`, `setExplicitPolicyRequired`, `setAnyPolicyInhibited`, `setPolicyMappingInhibited` v.v. cho phép cấu hình các ràng buộc PKIX theo policy.
- `setRevocationEnabled(boolean)` kiểm soát việc kiểm tra thu hồi; cấu hình đúng cần nguồn dữ liệu tin cậy và chính sách thất bại.
- `PKIXRevocationChecker` có thể cung cấp điều khiển OCSP/CRL và các option như `PREFER_CRLS`, `ONLY_END_ENTITY` hay `SOFT_FAIL`. `SOFT_FAIL` là quyết định an ninh cần chủ đích, **không phải cờ bật mặc định để bỏ qua sự cố**.

### 8.2 Lấy anchors từ truststore được quản trị

```java
// Minh họa trong phương thức có quyền truy cập trustStore đã load:
PKIXParameters params = new PKIXParameters(trustStore);
```

Constructor `PKIXParameters(KeyStore)` xem các mục **trusted certificate entry** trong truststore là nguồn anchors (theo đặc tả API). Nó không lấy tất cả chứng thư bất kỳ trong keypair entries làm root đáng tin. Trên JVM server, truststore có thể được cấu hình bởi hệ thống, container hoặc ứng dụng và phải được quản lý như **cấu hình an ninh**.

**Không** xây truststore theo cách tải danh sách chứng thư từ TLS peer rồi `setCertificateEntry` tất cả, vì lúc đó đã **biến dữ liệu chưa tin thành trust anchors**. Truststore nội bộ dùng cho mTLS hoặc test chỉ nên chứa CA/peer đã được phê duyệt theo chính sách.

### 8.3 `TrustAnchor` và certificate root trong chain

Nếu TLS peer gửi kèm chứng thư root, bên nhận **không mặc nhiên tin root đó**. Trust manager phải tìm một anchor trong truststore **đã được cấu hình độc lập**. Root trong peer chain có thể trùng với anchor đã tin, nhưng sự trùng dữ liệu không phải nguồn cấp quyền tin cậy; anchor được lựa chọn từ truststore.

Một application có nhiều anchors có thể tìm được **nhiều đường dẫn hợp lệ khác nhau** tới các root khác nhau khi xuất hiện cross-signing. Builder được phép chọn một path hợp lệ theo constraints và candidates của provider; ứng dụng không nên giả định “chỉ có đúng một đường”, hoặc so sánh tên root bằng chuỗi hiển thị để suy luận tin cậy.

## 9. PKIX validation kiểm tra những gì — và những gì còn ở tầng ứng dụng?

Theo RFC 5280 và các giới hạn thuật toán/chính sách của provider, việc xác minh đường dẫn thường bao gồm:

1. **Nối issuer–subject và chữ ký số:** certificate cấp dưới có thể được xác minh bằng public key của CA cấp trên tương ứng.
2. **Thời hạn hợp lệ** cho các certificate trong path tại validation date.
3. **Quyền CA:** `BasicConstraints: CA=true` ở issuer intermediates, `pathLenConstraint` thích hợp và `KeyUsage.keyCertSign` khi có extension.
4. **Name constraints** kế thừa từ các CA theo quy tắc RFC.
5. **Certificate policies/policy mappings** và các extension critical được nhận diện.
6. **Algorithm constraints** của runtime/policy, ví dụ `jdk.certpath.disabledAlgorithms` cho các thuật toán hoặc key-size bị loại bỏ.
7. **Trạng thái thu hồi** nếu được bật và có đủ thông tin/cơ chế xử lý theo cấu hình.

Nhưng một thành công của `CertPathValidator.validate()` **chưa đủ** để chấp nhận leaf cho mọi ứng dụng:

| Điều ứng dụng cần | PKIX path validation có tự làm đầy đủ không? | Phần còn phải kiểm tra |
| --- | --- | --- |
| Chứng thư có chain hợp lệ tới CA đã tin | **Có**, trong phạm vi PKIX parameters và provider | Lựa chọn anchors/policy đúng cho ứng dụng |
| Chứng thư đúng cho **hostname TLS** | **Không**, chỉ kiểm tra path không tự khớp DNS bạn đang kết nối | SAN DNS/IP và endpoint identity theo quy tắc TLS/JSSE |
| Chứng thư có quyền **TLS serverAuth** hoặc **codeSigning** | Không mặc định cho mọi purpose | EKU/KeyUsage và policy ứng dụng/JSSE |
| Chứng thư có bị thu hồi hay không | Phụ thuộc `setRevocationEnabled` và cấu hình checker/CRL/OCSP | Chính sách revocation, xử lý offline/failure |
| Root có nên được tin ở tenant này hay không | Không tự chọn chính sách trust store | Trust anchors phân vùng theo tenant/purpose |
| Chủ thể có quyền thực hiện hành động nghiệp vụ không | **Không** | Authorization, mapping identity → role/account |
| Có chống replay/đảm bảo dữ liệu kênh không bị sửa? | Không bởi riêng path | TLS handshake + record protection / protocol khác |

**Phân biệt `CertPathValidator` với HTTPS verification:** JSSE `X509TrustManager`/`X509ExtendedTrustManager` phục vụ xác thực chuỗi trong ngữ cảnh TLS; `HttpsURLConnection`/HTTP client và cấu hình endpoint identification bổ sung kiểm tra hostname/purpose theo giao thức. Một bài tập gọi trực tiếp `CertPathValidator` **không thay cho HTTPS client an toàn**.

## 10. Các lỗi hay gặp khi làm việc với intermediate và root

| Hiện tượng/lỗi | Nguyên nhân thường gặp | Cách xử lý đúng |
| --- | --- | --- |
| `CertPathValidatorException` do không nối được tới anchor | Thiếu intermediate, sai issuer/key, hoặc root chưa nằm trong tập đáng tin | Cung cấp intermediate đúng, **không tự tin root do peer gửi** |
| Dùng `CertPath [root, intermediate, leaf]` | Sai thứ tự, đưa trust anchor vào path | Tạo `[leaf, intermediate]`, truyền root riêng trong `PKIXParameters` |
| Builder không tìm được path | `CertStore` thiếu intermediate hoặc selector/ràng buộc quá chặt | Kiểm tra target `X509CertSelector`, candidate store, issuer/cert extensions |
| Chứng thư intermediate bị từ chối | `BasicConstraints CA=false`, thiếu `keyCertSign`, `pathLenConstraint` hoặc critical extensions sai | CA phải phát hành lại chứng thư trung gian hợp lệ, **không tắt check** |
| Chứng thư hết hạn | `notAfter` đã qua hoặc `notBefore` còn ở tương lai | Cấp mới/gia hạn đúng quy trình, đồng bộ đồng hồ; không bỏ qua validity |
| `InvalidAlgorithmParameterException: trustAnchors must be non-empty` | Tập anchor rỗng hoặc truststore không có trusted cert entries | Cài anchors phê duyệt rồi tạo `PKIXParameters` |
| Revocation báo lỗi do mạng/thiếu CRL | Policy bật nhưng nguồn trạng thái không khả dụng | Cấu hình OCSP/CRL, cache/timeout và quy tắc fail-open/closed có phê duyệt |
| `validate` thành công nhưng hostname sai | Code chỉ kiểm tra path, không kiểm tra server identity | Xác minh SAN/endpoint identification ở tầng TLS/ứng dụng |
| Tin tự động root được peer gửi | Vô tình mở rộng trust anchors theo dữ liệu chưa đáng tin | Root phải đến từ truststore/pinning/quản trị độc lập |
| So trùng `issuer.getName()` rồi coi là hợp lệ | Không kiểm tra chữ ký/policy/constraints | Dùng PKIX validator/builder |
| Dùng `getBasicConstraints() >= 0` như kiểm tra tất cả | Chỉ cho biết CA bit/độ sâu, **chưa chứng minh chain hợp lệ** | Bắt buộc validate toàn bộ path |
| Dùng Java certificate entry/keystore nhầm loại | `KeyStore` chứa key entries/certs khác trusted entries | Phân biệt `isCertificateEntry`/entry policy, kiểm tra truststore thực |

### 10.1 Chẩn đoán exception thay vì chấp nhận tiếp

`CertPathValidatorException` có thể cung cấp:

```java
// Đoạn minh họa trong catch (CertPathValidatorException ex):
int index = ex.getIndex();  // vị trí chứng thư lỗi trong path, có thể -1
CertPathValidatorException.Reason reason = ex.getReason();
```

Giá trị `index` có thể giúp chỉ ra leaf/intermediate nào liên quan, nhưng **không phải mọi lỗi đều gắn với một index**. Không bắt tất cả exception rồi `return true` hoặc tự fallback sang một CA tự ký không được tin. Nếu validation thất bại, **không chấp nhận certificate**.

Không dùng `jdk.certpath.disabledAlgorithms` theo cách làm yếu chính sách để “sửa” lỗi tương thích. Nếu ứng dụng đang dùng thuật toán yếu hay root không hợp lệ, cần **di trú đúng** (cấp lại certificate/khóa, cấu hình trust anchors thích hợp) thay vì tắt bảo vệ.

## 11. Bài tập kiểm tra bằng chính fixture

**Bài tập A — Nhận diện ba vai trò.** Dùng `keytool -printcert -file root.pem`, `-file intermediate.pem` và `-file leaf.pem` để quan sát subject, issuer, BasicConstraints, KeyUsage, SAN/EKU. Xác định:

- Root tự ký có subject/issuer trùng nhau; nhưng điều làm nó đáng tin là nó đã được ứng dụng chọn làm `TrustAnchor`.
- Intermediate mang subject riêng, issuer là Root, `CA=true`, `pathlen:0` và quyền `keyCertSign`.
- Leaf mang issuer là Intermediate, `CA=false`, SAN DNS theo lab.

**Bài tập B — Đổi thứ tự `CertPath`.** Thay `List.of(leaf, intermediate)` thành `List.of(intermediate, leaf)` trong đoạn validate. Vì sao cùng hai certificate vẫn không tạo đường từ target đến anchor? Dùng `getIndex()`/`getReason()` để hiểu lỗi, không dựa vào một thông điệp text cố định của provider.

**Bài tập C — Bỏ intermediate.** Chỉ truyền `[leaf]` cho validator, giữ Root là anchor. Nhận biết không thể nối leaf trực tiếp tới root. Thêm intermediate vào `CollectionCertStoreParameters` cho builder để nó tìm lại path đúng.

**Bài tập D — Sai anchor.** Sinh **một CA root khác** trong lab, thay `TrustAnchor` bằng root mới. `CertPath` vẫn có các chữ ký nội bộ đúng theo root cũ nhưng **không thể kết thúc ở anchor mới**. Đây là khác biệt giữa “chứng thư có chữ ký hợp lệ” và “ứng dụng tin issuer cuối”.

**Bài tập E — CA mất quyền cấp.** Thử thay intermediate được Root ký thành bản không có `CA=true` hoặc thiếu `keyCertSign` (khi có KeyUsage), rồi kiểm tra PKIX validator từ chối. Đừng tự thêm chứng thư ấy vào truststore để lách lỗi.

**Bài tập F — Thời gian và revocation.** Tạo fixture có thời hạn ngắn và thử sau khi hết hạn; quan sát `CertPathValidatorException`. Tìm hiểu `PKIXRevocationChecker` (cần hạ tầng OCSP/CRL) và giải thích vì sao `setRevocationEnabled(false)` chỉ dùng cho thí nghiệm kín.

**Bài tập G — Purpose và hostname.** Thay SAN của leaf bằng DNS khác nhưng giữ root/intermediate hợp lệ. Chỉ `validate()` chưa đủ kết luận máy chủ đúng hostname. Kiểm tra thêm quy tắc endpoint identification/TLS và EKU/KeyUsage.

## 12. Checklist thiết kế đường dẫn tin cậy trong Java

- [ ] **Không lấy trust anchor từ dữ liệu chưa tin cậy**; tin CA/định danh qua truststore/chính sách được quản trị.
- [ ] Phân biệt **leaf, intermediate và root**; `CertPath` X.509 theo thứ tự leaf → intermediate; root/anchor **nằm ngoài** path.
- [ ] Với `CertPathValidator("PKIX")`, truyền **đúng `PKIXParameters`**, thời gian, anchors, chính sách và revocation.
- [ ] Với `CertPathBuilder("PKIX")`, có selector **đúng target** và `CertStore` chứa intermediate candidates, không mong mọi intermediate tự tải xuống.
- [ ] Không tự viết thuật toán “so tên issuer → subject” rồi coi như path validation; phải kiểm tra chữ ký, BasicConstraints, KeyUsage, time, constraints và policy.
- [ ] Không nhầm `KeyStore` trusted cert entry với keypair/private-key entry hoặc certificates được peer trình bày.
- [ ] Không bỏ qua hostname/EKU/purpose khi dùng certificate cho TLS, mTLS, code signing hoặc danh tính nghiệp vụ.
- [ ] Revocation phải là **quyết định chính sách thực tế** có OCSP/CRL và xử lý lỗi; ví dụ tắt chỉ để lab.
- [ ] Theo dõi thuật toán/key-size bị chính sách JDK từ chối; **không vô hiệu hóa bảo vệ** để chạy certificate yếu.
- [ ] Có negative tests: thiếu intermediate, đảo thứ tự, sai root, CA=false, thiếu keyCertSign, certificate expired, hostname mismatch.
- [ ] Không để khóa CA private từ fixture/hệ thống production vào source code, log, repo, hoặc file tải công khai.

## 13. Tự kiểm tra kiến thức

**1. Trong Java, `CertPath` của root → intermediate → leaf được sắp ra sao?**  
Theo X.509 path của Java: **`[leaf, intermediate]`** từ target đến phía trust anchor. `root` cung cấp riêng dưới dạng `TrustAnchor`, không thêm vào danh sách.

**2. `TrustAnchor` là certificate tự ký nào bất kỳ phải không?**  
**Không.** Nó là thông tin CA đã được cấu hình **tin cậy ngoài đường dẫn**. Một certificate tự ký do attacker gửi không thể tự trở thành anchor.

**3. Intermediate được Root ký rồi có tự động trở thành trusted root không?**  
**Không.** Intermediate là chứng thư CA trong đường dẫn; nó phải được kiểm tra chữ ký/constraints và nối lên một anchor đáng tin đã có.

**4. `pathLenConstraint=0` trên intermediate có cấm cấp certificate leaf không?**  
**Không.** Nó cấm thêm CA trung gian **không self-issued** phía dưới trong path hợp lệ, không cấm cấp leaf.

**5. `CertificateFactory.generateCertPath` có tự kiểm tra chain không?**  
**Không.** Nó tạo representation. Dùng `CertPathValidator("PKIX").validate` để kiểm tra path; builder có thể tìm và xác thực path theo constraints.

**6. `CertPathValidator` và `CertPathBuilder` khác nhau ở đâu?**  
Validator **xác thực path đã cho**. Builder **tìm path** từ certificates/`CertStore` và trust anchors, rồi trả path hợp lệ khi build thành công.

**7. `validate()` thành công đã bảo đảm hostname trong URL trùng certificate chưa?**  
**Chưa.** Phải có kiểm tra SAN/endpoint identity cùng key usage/purpose ở tầng ứng dụng/TLS.

**8. Vì sao ví dụ đặt `setRevocationEnabled(false)` và production có nên copy nguyên không?**  
Lab không có hệ thống CRL/OCSP để kiểm thử thu hồi. Production phải có chính sách revocation phù hợp; tắt vì tiện có thể khiến chứng thư đã thu hồi vẫn được chấp nhận.

## Tài liệu chính thức

- [Java SE 17 — `CertPath`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertPath.html): tập certificate, quy tắc thứ tự X.509 và vai trò riêng của trust anchor.
- [Java SE 17 — `TrustAnchor`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/TrustAnchor.html): mô hình CA tin cậy với certificate hoặc CA name + public key, name constraints.
- [Java SE 17 — `CertPathValidator`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertPathValidator.html): engine PKIX validation và exceptions.
- [Java SE 17 — `CertPathBuilder`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertPathBuilder.html): xây dựng đường dẫn bằng target constraints, certificates nguồn và trust anchors.
- [Java SE 17 — `PKIXParameters`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/PKIXParameters.html): anchors, date, policies, revocation.
- [Java SE 17 — `PKIXBuilderParameters`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/PKIXBuilderParameters.html): target selector, CertStore, max path length.
- [Java SE 17 — `PKIXCertPathValidatorResult`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/PKIXCertPathValidatorResult.html): kết quả gồm `getTrustAnchor`, public key, policy tree.
- [Java SE 17 — `PKIXCertPathBuilderResult`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/PKIXCertPathBuilderResult.html): path được build và trust anchor riêng.
- [Java SE 17 — Java PKI Programmer's Guide](https://docs.oracle.com/en/java/javase/17/security/java-pki-programmers-guide.html): hướng dẫn chính thức về xây và xác minh certification paths.
- [Java SE 17 — `keytool` reference](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html): `-genkeypair`, `-certreq`, `-gencert`, `-ext BC`, `-ext KU`, `-exportcert`.
- [RFC 5280 — X.509 PKI Certificate and CRL Profile](https://www.rfc-editor.org/rfc/rfc5280): §4.2.1.9 BasicConstraints; §4.2.1.3 KeyUsage; §6 path validation và trust anchors.

**Kết luận:** `CertPath` là **đường đi của các chứng thư cần kiểm tra**, intermediate là **mắt xích phải được xác thực**, còn `TrustAnchor` là **điểm xuất phát được tin theo chính sách**. `CertPathValidator` kiểm tra một đường đã cho; `CertPathBuilder` tìm một đường hợp lệ từ candidate certificates. Không chấp nhận một chứng thư chỉ vì chữ ký của nó kiểm tra được — chỉ tin khi nó có **đường dẫn hợp lệ tới anchor đã phê duyệt và đáp ứng các ràng buộc của chính ứng dụng**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cert-path-002">14.2 Phân biệt xây đường dẫn bằng CertPathBuilder với kiểm tra bằng CertPathValidator</a>

<details>
<summary>Xem chi tiết</summary>

# 14.2 Phân biệt xây đường dẫn bằng CertPathBuilder với kiểm tra bằng CertPathValidator

Trong Java PKI, một certificate X.509 của máy chủ có thể được cấp bởi intermediate CA, rồi intermediate được cấp bởi root CA mà ứng dụng tin cậy. Để quyết định có thể **tin vào khóa công khai của certificate đích** hay không, ứng dụng cần một **certification path** phù hợp và phải kiểm tra path đó theo chính sách PKIX. Java phân biệt hai nhiệm vụ:

- **`CertPathBuilder` — xây đường dẫn:** từ **certificate đích**, tập **trust anchors**, tiêu chí chọn và nguồn **certificate ứng viên**, engine **tìm một đường dẫn hợp lệ** dẫn về trust anchor. Nó vừa **tìm kiếm** vừa **kiểm tra** để trả một đường dẫn đã được xác minh theo các tham số được cung cấp.
- **`CertPathValidator` — kiểm tra đường dẫn:** nhận **một đường dẫn đã được cung cấp** và tập trust anchors/chính sách, rồi **đánh giá đúng đường dẫn đó**. Nó không có nhiệm vụ tìm một chuỗi mới thay thế nếu chuỗi đưa vào thiếu intermediate, sai thứ tự hay không hợp lệ.

Đây là bài **14.2** thuộc phần **14. Đường dẫn tin cậy** của Java Advanced Security & Cryptography. Trọng tâm là hiểu đầu vào/đầu ra của hai engine JCA, vai trò của `CertStore`, `PKIXBuilderParameters`, `PKIXParameters`, `TrustAnchor`; chạy một thí nghiệm Java 17+ có root/intermediate/leaf thật do `keytool` sinh; và nhận diện đúng giới hạn PKIX so với xác thực hostname/TLS/revocation trong hệ thống sản xuất.

## 1. Một bảng để không nhầm hai engine

| Khía cạnh | `CertPathBuilder` | `CertPathValidator` |
| --- | --- | --- |
| Câu hỏi chính | “**Tìm được** một certification path hợp lệ từ mục tiêu đến trust anchor không?” | “**Certification path đã cho** có hợp lệ với trust anchors và policy không?” |
| Input chính | `CertSelector` chọn target, `TrustAnchor`, nguồn candidate (`CertStore`), `PKIXBuilderParameters` | Một `CertPath` **đã có**, `TrustAnchor`, `PKIXParameters` |
| Có cần sẵn toàn bộ chain? | **Không nhất thiết**; cần đủ candidate/intermediate có thể tra cứu để nối chain | **Có**: phải cung cấp path theo thứ tự và đủ certificate, ngoại trừ trust anchor |
| Có tìm kiếm nhiều khả năng/cross-sign không? | Có thể tìm các ứng viên/path theo implementation và policy | Không tự tìm các path thay thế; chỉ xử lý đường dẫn đã truyền |
| Có kiểm tra chữ ký/thời hạn/ràng buộc PKIX? | **Có**; đường dẫn trả về đã được xác minh theo các tham số xây dựng | **Có**; đây là nhiệm vụ chính |
| Parameters | `PKIXBuilderParameters` (extends `PKIXParameters`) | `PKIXParameters` |
| Kết quả PKIX | `PKIXCertPathBuilderResult` có `CertPath`, `TrustAnchor`, public key và policy tree | `PKIXCertPathValidatorResult` có `TrustAnchor`, public key và policy tree; **không** trả một `CertPath` mới |
| Lỗi thông dụng | `CertPathBuilderException` khi không tìm ra đường dẫn được chấp thuận | `CertPathValidatorException` khi đường dẫn được đưa vào không đạt điều kiện |
| Dùng trong tình huống | Chỉ có leaf và một kho intermediate/CA ứng viên; chain chưa đủ hoặc có nhiều khả năng nối | Đã nhận/có một chain cố định cần kiểm tra theo trust policy |
| Bảo mật quan trọng | Candidate store **không phải truststore**; một candidate đẹp chưa đủ tin cậy | Certificate path đúng về cú pháp vẫn có thể bị từ chối vì trust/policy/thời gian |

**Điểm dễ trả lời sai:** `CertPathBuilder.build(...)` **không chỉ “ghép cho đủ các chứng chỉ”**. Với thuật toán `"PKIX"`, engine tìm **một path đã được validate** theo chính sách PKIX; `PKIXCertPathBuilderResult.getCertPath()` chứa một đường dẫn đã qua kiểm tra. Có thể gọi `CertPathValidator` sau đó nếu cần kiểm tra lại ở **thời điểm/chính sách khác**, hoặc để minh họa, nhưng không phải cứ build thành công mới bắt buộc validate thêm lần nữa với y hệt parameters.

## 2. Certification path, trust anchor và certificate ứng viên

Xét topology đơn giản:

```text
                         ROOT CA
             CN=Example Root CA
             [self-signed trong lab]
             [TRUST ANCHOR do app cấu hình]
                        |
                        | ký certificate
                        v
                     ISSUING CA
              CN=Example Issuing CA
              [intermediate certificate]
              [CA=true; keyCertSign]
                        |
                        | ký certificate
                        v
                   END ENTITY / LEAF
             CN=service.example.test
             [SAN: service.example.test]
             [CA=false; serverAuth]
```

Người kiểm tra bắt đầu từ **leaf certificate** và muốn truy vết một chuỗi chữ ký/issuer phù hợp về **root CA đã được chấp thuận**. Không phải root CA nào tồn tại trong Internet đều tự trở thành trust anchor: danh sách root được chấp thuận đến từ **truststore hoặc chính sách của ứng dụng**.

### 2.1 Trong Java, CertPath gồm những gì?

Với hướng lưu trữ Java PKIX thông dụng, danh sách certificate của path được biểu diễn từ **mục tiêu trở lên**:

```text
CertPath.getCertificates():
    [0] leaf certificate                (target)
    [1] intermediate CA certificate
    [không chứa root trust anchor]

PKIXCertPathBuilderResult.getTrustAnchor():
    root CA được tin cậy
```

Đó là lý do trong ví dụ thực tế, `getCertPath().getCertificates().size()` bằng **2** dù topology có **3 certificate**. **Trust anchor thường nằm riêng**, không được thêm vào danh sách certificate của `CertPath` trả về bởi PKIX builder.

`CertPath` là một **đối tượng bất biến** chứa thứ tự certificate và loại path, có thể tạo từ `CertificateFactory.generateCertPath(...)`, được nhận từ `CertPathBuilder`, hoặc đọc một dạng encoding phù hợp. Có một đối tượng `CertPath` **không tự khẳng định nó hợp lệ**: một danh sách certificate có thể được tạo ra dù chữ ký, thời hạn hoặc trust anchors không đúng.

### 2.2 TrustAnchor là quyết định tin cậy độc lập

`java.security.cert.TrustAnchor` mô tả CA được chấp thuận làm điểm bắt đầu tin cậy trong PKIX. Nó có thể được tạo từ `X509Certificate` được tin hoặc tên CA + public key (theo API). Trong lab này ta dùng:

```java
// Đoạn mã trong phương thức đã có root là X509Certificate.
TrustAnchor anchor = new TrustAnchor(root, null);
Set<TrustAnchor> anchors = Set.of(anchor);
```

Đoạn trên là **minh họa không độc lập**, không phải một full Java class. Root certificate trong lab được ứng dụng **chủ động tin**; chỉ bởi `root` tự ký không khiến nó trở thành an toàn trong production. Trước khi thêm một root CA/CA riêng vào truststore, phải kiểm chứng **nguồn, fingerprint, phạm vi tin cậy và quyền quản trị**.

Trust anchor nằm ngoài path được validate theo cách thông thường; đừng suy ra toàn bộ thuộc tính của certificate gốc được kiểm tra giống leaf/intermediate (ví dụ các ràng buộc đối với anchor phải được quản lý ở **trust policy**). Đổi trust anchor có thể đổi hoàn toàn kết quả của cùng một path.

### 2.3 CertStore là nguồn ứng viên, không phải điểm tin cậy

`java.security.cert.CertStore` là nguồn tìm certificate và CRL. Nó có thể lấy từ một collection trong RAM, directory LDAP hay backend khác tùy provider.

```text
Trusted CA / TrustAnchor
     |
     +-- từ truststore/chính sách -> cơ sở để chấp thuận path
     |
CertStore (Collection/LDAP/...):
     +-- leaf certificates
     +-- intermediate CA certificates
     +-- CRLs ứng viên
     |
     +-- cung cấp dữ liệu cho builder/PKIX, CHƯA tạo trust
```

Một intermediate được đưa vào `CertStore` chỉ giúp builder **tìm được mắt xích**, không tự biến intermediate thành trusted root. Một chứng chỉ do attacker cung cấp có thể nằm trong candidate pool; chính builder/validator phải kiểm tra chuỗi dẫn tới **trust anchor được cấu hình trước** và các điều kiện bảo mật.

**Không nhập bừa intermediate thiếu vào truststore làm anchor** chỉ vì builder báo lỗi. Hãy tìm intermediate từ nguồn phù hợp, xác nhận issuer/chain và cung cấp nó làm **candidate**; trust anchor phải là quyết định của chính sách.

## 3. CertPathBuilder: chọn target rồi tìm path hợp lệ

Các lớp liên quan:

```text
java.security.cert.CertPathBuilder
    getInstance("PKIX")
    build(CertPathParameters)
                |
                v
java.security.cert.PKIXBuilderParameters
    trustAnchors          (CA mà bên nhận thực sự tin)
    targetCertConstraints (CertSelector chọn mục tiêu)
    CertStores            (intermediate/CRL candidates)
    date, policies, revocation, checkers, constraints...
    maxPathLength          (giới hạn intermediate)
                |
                v
java.security.cert.PKIXCertPathBuilderResult
    getCertPath()     = path đã build VÀ validate
    getTrustAnchor()  = anchor được dùng
    getPublicKey()    = khóa công khai của target
    getPolicyTree()   = kết quả policy tree (có thể null)
```

### 3.1 Input tối thiểu

```java
// Đoạn mã minh họa (không độc lập):
// root và leaf là X509Certificate đã được parse;
Set<TrustAnchor> anchors =
        Set.of(new TrustAnchor(root, null));

X509CertSelector target = new X509CertSelector();
target.setCertificate(leaf);

CertStore source = CertStore.getInstance(
        "Collection",
        new CollectionCertStoreParameters(
                List.of(leaf, intermediate)));

PKIXBuilderParameters params =
        new PKIXBuilderParameters(anchors, target);
params.addCertStore(source);
params.setRevocationEnabled(false); // Chỉ trong lab này.

PKIXCertPathBuilderResult result =
        (PKIXCertPathBuilderResult)
                CertPathBuilder.getInstance("PKIX").build(params);
```

**Các điểm cần hiểu:**

1. **Target constraints** là tiêu chí cho certificate đích. `target.setCertificate(leaf)` chọn đúng một certificate cụ thể; với một hệ thống tìm nhiều ứng viên có thể dùng `setSubject`, `setKeyUsage`, `setExtendedKeyUsage` cùng bộ tiêu chí thích hợp.
2. **Trust anchors** xác định điểm chấp thuận cuối cùng, không rút ra từ tên file hay chỉ từ chứng chỉ tự ký bất kỳ.
3. **CertStore** cung cấp intermediate/certificates/CRLs ứng viên; **builder được phép tìm** trong những nguồn provider/policy hỗ trợ. Đừng kỳ vọng mọi provider luôn tự tải AIA qua Internet; cung cấp nguồn được kiểm soát và đo trên JDK thật.
4. **PKIXBuilderParameters extends PKIXParameters**: ngoài các tham số validation còn thêm **target constraints và giới hạn độ dài path**.
5. **Revocation** trong code được tắt **chỉ để demo hoạt động offline**, không có CA phát CRL hay OCSP thật. Production phải có **chính sách revocation rõ ràng** thay vì copy `false`.
6. Builder có thể kiểm tra nhiều candidates/chains; đường dẫn cuối cùng được **validate**, không chỉ xếp nối theo `issuerDN == subjectDN`.

### 3.2 setMaxPathLength có nghĩa gì?

`PKIXBuilderParameters.setMaxPathLength(int)` giới hạn **số intermediate certificate không phải self-issued** trong path theo contract API. Với topology leaf → một intermediate → root anchor, `maxPathLength=1` là đủ; `maxPathLength=0` chỉ cho path không có intermediate tương ứng.

- Giá trị **`-1`**: không giới hạn bởi thiết lập này.
- Giá trị **`0`**: chỉ cho path có **một certificate** trước anchor.
- Nếu không đặt, **mặc định trong API là 5** theo tài liệu Java 21.
- Đây **không** phải giới hạn tổng số certificate bao gồm root anchor và leaf. Có các quy tắc riêng về `BasicConstraints.pathLenConstraint` do CA ký; policy thực tế có thể chặt hơn giới hạn builder.

Ví dụ:

```java
// Chỉ minh họa, trong phương thức có PKIXBuilderParameters params.
params.setMaxPathLength(1); // Một intermediate trong bài lab.
```

Giới hạn giúp tránh thuật toán dò tìm quá sâu và các path ngoài chính sách, **nhưng không** thay thế validation chữ ký, usage, algorithm constraints hay trust anchors.

## 4. CertPathValidator: kiểm tra một CertPath đã có

Validator nhận một **certification path xác định**:

```text
     CertPath
  [leaf, intermediate]  --------+
                                |
          PKIXParameters -------+------> CertPathValidator("PKIX")
          - trust anchors                      |
          - date, policies                     v
          - revocation                    Validate signatures,
          - checkers                      basicConstraints,
          - cert constraints             validity, policies...
                                                |
                                       +--------+---------+
                                       |                  |
                                     PASS                 FAIL
                                       |                  |
                         PKIXCertPathValidatorResult  CertPathValidatorException
                           trustAnchor, publicKey,         index/reason/cause
                           policyTree
```

### 4.1 Tạo một CertPath bằng tay

Khi **đã có chain theo đúng thứ tự**, có thể dựng `CertPath` qua `CertificateFactory`:

```java
// Đoạn mã minh họa bên trong phương thức có đủ imports/throws.
// root không nằm trong danh sách path vì nó là trust anchor.
CertificateFactory factory = CertificateFactory.getInstance("X.509");
CertPath givenPath =
        factory.generateCertPath(List.of(leaf, intermediate));

PKIXParameters params = new PKIXParameters(
        Set.of(new TrustAnchor(root, null)));
params.setRevocationEnabled(false); // CHỈ lab không CRL/OCSP.

PKIXCertPathValidatorResult checked =
        (PKIXCertPathValidatorResult)
                CertPathValidator.getInstance("PKIX")
                        .validate(givenPath, params);
```

Khác builder: validator **không lấy `CertStore` để tự sửa lại thứ tự hay bổ sung intermediate bị thiếu**. Nó có thể dùng `CertStore` khi cần **thông tin phục vụ validation**, đặc biệt CRL/revocation, nhưng điều đó **không biến** validator thành path discovery engine.

**Thứ tự quan trọng:** `CertificateFactory.generateCertPath(List)` với X.509 PKIX thường dùng thứ tự **leaf → intermediate CA(s)** theo API Java. Đừng thêm root trust anchor vào cuối danh sách chỉ để làm “đủ ba chứng chỉ”: anchor được truyền qua parameters riêng.

### 4.2 PKIXParameters mô tả chính sách xác minh

Những thuộc tính quan trọng của `PKIXParameters`:

| Thành phần | API tiêu biểu | Tác động |
| --- | --- | --- |
| Trust anchors | `new PKIXParameters(Set<TrustAnchor>)` hoặc từ trusted certificate entries của `KeyStore` | Định nghĩa CA/key nào là điểm tin cậy được chấp thuận |
| Ngày kiểm tra | `setDate(Date)` | Xác định thời điểm dùng để kiểm tra validity; không tự làm clock-stamping |
| Revocation | `setRevocationEnabled(boolean)` | Bật/tắt bước kiểm tra thu hồi theo khả năng/checker/policy |
| Certificate/CRL sources | `addCertStore(CertStore)` | Cung cấp nguồn phục vụ revocation và các tác vụ kiểm tra tương ứng |
| Policy OIDs | `setInitialPolicies(Set<String>)` | Ràng buộc certificate policies được chấp nhận theo mô hình PKIX |
| Custom checkers | `addCertPathChecker(PKIXCertPathChecker)` | Kiểm tra thêm theo extension/logic policy |
| Xác định policy | `setExplicitPolicyRequired(...)`, `setPolicyMappingInhibited(...)`... | Điều khiển xử lý policy tree |
| Danh sách trusted CA | `getTrustAnchors()` | Đối chiếu quy tắc tin cậy đã được cấu hình, không suy từ candidate pool |

Trong thực tế còn có **thuật toán bị cấm**, constraints của JDK/provider, certificate extensions, name constraints, basic constraints và key usages; định nghĩa policy cụ thể tùy môi trường. Không cố tự liệt kê một vài if-statement là đã thay được PKIX validator.

**`CertPathValidator.validate` thành công không có nghĩa mọi quy tắc TLS đã hoàn tất.** Xác thực hostname/SAN cho HTTPS, mục đích sử dụng server/client, danh tính ứng dụng, pinning và trạng thái/replay thuộc layer tiêu thụ certificate/JSSE và policy tương ứng. Phải xác minh phần đó riêng, không chỉ dựa vào thành công của API PKIX.

## 5. Các kiểu kết quả và ngoại lệ: khác nhau về hợp đồng

| API | Thành công trả về | Thất bại chính |
| --- | --- | --- |
| `CertPathBuilder.getInstance("PKIX")` | Một engine builder | `NoSuchAlgorithmException` |
| `CertPathBuilder.build(PKIXBuilderParameters)` | `CertPathBuilderResult`, thường là `PKIXCertPathBuilderResult` | `CertPathBuilderException`; configuration có thể gây lỗi tham số |
| `CertPathValidator.getInstance("PKIX")` | Một engine validator | `NoSuchAlgorithmException` |
| `CertPathValidator.validate(certPath, PKIXParameters)` | `CertPathValidatorResult`, thường là `PKIXCertPathValidatorResult` | `CertPathValidatorException`, `InvalidAlgorithmParameterException` |
| `CertificateFactory.generateCertPath(List)` | Đối tượng `CertPath` từ danh sách certificate | `CertificateException` nếu input không hợp lệ theo contract |
| `CertStore.getCertificates(selector)` | Một collection ứng viên | `CertStoreException` khi nguồn tra cứu lỗi |
| `PKIXBuilderParameters(trustAnchors, target)` | Parameters phù hợp cho builder | `InvalidAlgorithmParameterException` nếu trust anchors không có/không hợp lệ theo constructor |

**`CertPathBuilderException` không có nghĩa chắc chắn “không tìm thấy intermediate”.** Nó có thể do không có path hợp lệ tới anchor, lỗi candidate source, certificate constraints, signature, thời hạn, thuật toán bị chặn hoặc revocation. Ngược lại, **`CertPathValidatorException` không tự nói lỗi do chữ ký**; xem `getReason()`, `getIndex()`, `getCause()`, thông tin policy và logging phù hợp.

Với `CertPathValidatorException.getIndex()`, index chỉ vào certificate gây lỗi **nếu provider có thông tin đó**; có thể là **`-1`** khi không xác định. Không tự diễn giải index -1 là “root certificate lỗi” hay “toàn bộ chain an toàn”.

## 6. Lab chạy được: root CA → intermediate CA → leaf

Ta tạo **ba X.509 certificate thật trong môi trường test tạm** để builder và validator làm việc với chữ ký/extension, không dùng “pseudo certificates”. Công cụ `keytool` có sẵn cùng JDK, không cần dịch vụ certificate authority thực hay kết nối Internet.

**Chỉ chạy trong thư mục lab trống**, không chứa keystore thật. Password `changeit` và tên `service.example.test` chỉ dành cho thử nghiệm; các private keys được keytool tạo trong file `.p12`, vì vậy không đưa các file này vào Git, artifact hay môi trường production. Lệnh dưới dùng một dòng mỗi thao tác, có thể nhập lần lượt trong shell, gồm PowerShell với `keytool.exe`.

### 6.1 Tạo root CA tự ký cho lab

```bash
keytool -genkeypair -alias root -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -validity 3650 -dname "CN=Example Root CA,O=Lab,C=VN" -ext "BC:critical=ca:true,pathlen:1" -ext "KU:critical=keyCertSign,cRLSign" -keystore root.p12 -storetype PKCS12 -storepass changeit -keypass changeit -noprompt

keytool -exportcert -alias root -rfc -file root.pem -keystore root.p12 -storetype PKCS12 -storepass changeit
```

- Root CA dùng keypair RSA-2048 cho **lab**; `BasicConstraints CA=true` và `keyCertSign` mô tả vai trò CA.
- Tệp `root.pem` chứa **certificate công khai**, còn `root.p12` giữ private key của root. **Chỉ `root.pem`** sẽ được ứng dụng Java dùng làm trust anchor.
- Trong production không tự phát hành root rồi đương nhiên chấp thuận: phải quản lý trust distribution, fingerprint, private key bảo vệ và lifecycle theo chính sách CA.

### 6.2 Sinh intermediate CA rồi để root ký

```bash
keytool -genkeypair -alias intermediate -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -validity 800 -dname "CN=Example Issuing CA,O=Lab,C=VN" -ext "BC:critical=ca:true,pathlen:0" -ext "KU:critical=keyCertSign,cRLSign" -keystore intermediate.p12 -storetype PKCS12 -storepass changeit -keypass changeit -noprompt

keytool -certreq -alias intermediate -file intermediate.csr -keystore intermediate.p12 -storetype PKCS12 -storepass changeit

keytool -gencert -alias root -infile intermediate.csr -outfile intermediate.pem -rfc -validity 730 -ext "BC:critical=ca:true,pathlen:0" -ext "KU:critical=keyCertSign,cRLSign" -keystore root.p12 -storetype PKCS12 -storepass changeit

keytool -importcert -alias root -file root.pem -keystore intermediate.p12 -storetype PKCS12 -storepass changeit -noprompt

keytool -importcert -alias intermediate -file intermediate.pem -keystore intermediate.p12 -storetype PKCS12 -storepass changeit -noprompt
```

Các lệnh trên tạo **CSR**, dùng **private root CA** ký intermediate, sau đó nạp root và certificate đã được cấp vào identity keystore của intermediate. `intermediate.pem` là certificate ứng viên mà builder sẽ cần. `pathlen:0` trên intermediate giới hạn số CA phụ thuộc bên dưới (ngoại trừ các trường hợp self-issued theo chuẩn); nó vẫn được phép cấp leaf end-entity certificate.

**Phân biệt:** `keytool -importcert` vào `intermediate.p12` để hoàn thành identity certificate chain của intermediate **không** có nghĩa application bên nhận phải đặt intermediate thành trust anchor. Trong Java lab ta chỉ đặt **root** trong `Set<TrustAnchor>`.

### 6.3 Sinh leaf certificate do intermediate ký

```bash
keytool -genkeypair -alias leaf -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -validity 300 -dname "CN=service.example.test,O=Lab,C=VN" -ext "SAN=dns:service.example.test" -keystore leaf.p12 -storetype PKCS12 -storepass changeit -keypass changeit -noprompt

keytool -certreq -alias leaf -file leaf.csr -keystore leaf.p12 -storetype PKCS12 -storepass changeit

keytool -gencert -alias intermediate -infile leaf.csr -outfile leaf.pem -rfc -validity 200 -ext "BC=ca:false" -ext "KU:critical=digitalSignature,keyEncipherment" -ext "EKU=serverAuth" -ext "SAN=dns:service.example.test" -keystore intermediate.p12 -storetype PKCS12 -storepass changeit
```

Leaf có `CA=false`, `serverAuth` và SAN của domain lab. File `leaf.pem` là certificate cần được kiểm tra. Để test API `CertPathBuilder`, ứng dụng Java chỉ cần **ba file PEM certificate**; chương trình không cần truy cập private keys trong các `.p12` vừa tạo.

**Trước khi dùng thật:** certificate self-issued/self-signed và password trong demo hoàn toàn không dùng làm trust configuration production. Nếu mất các file lab, có thể sinh lại và chạy lại. Đừng copy root certificate này vào `cacerts` chung của JDK hoặc truststore production.

## 7. Java 17+: xây, kiểm tra và minh họa hai kiểu lỗi

Lưu chương trình hoàn chỉnh sau thành **`CertPathBuilderValidatorDemo.java`** trong **cùng thư mục** có `root.pem`, `intermediate.pem`, `leaf.pem`.

```java
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import java.time.*;
import java.util.*;

public final class CertPathBuilderValidatorDemo {
    static X509Certificate cert(String path) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            return (X509Certificate) cf.generateCertificate(in);
        }
    }

    public static void main(String[] args) throws Exception {
        X509Certificate root = cert("root.pem");
        X509Certificate intermediate = cert("intermediate.pem");
        X509Certificate leaf = cert("leaf.pem");

        // Trust anchor: quyết định tin root CA của lab.
        Set<TrustAnchor> anchors =
                Set.of(new TrustAnchor(root, null));

        // Chỉ rõ certificate mục tiêu cần xây đường dẫn.
        X509CertSelector target = new X509CertSelector();
        target.setCertificate(leaf);

        // Intermediate chỉ là candidate, không phải trust anchor.
        CertStore repository = CertStore.getInstance(
                "Collection",
                new CollectionCertStoreParameters(
                        List.of(leaf, intermediate)));

        PKIXBuilderParameters bp =
                new PKIXBuilderParameters(anchors, target);
        bp.setRevocationEnabled(false); // Chỉ lab, không CRL/OCSP.
        bp.addCertStore(repository);

        CertPathBuilder builder =
                CertPathBuilder.getInstance("PKIX");
        PKIXCertPathBuilderResult built =
                (PKIXCertPathBuilderResult) builder.build(bp);

        System.out.println("Builder path certificates: "
                + built.getCertPath().getCertificates().size());
        System.out.println("Builder trusted root: "
                + built.getTrustAnchor()
                        .getTrustedCert()
                        .getSubjectX500Principal()
                        .getName()
                        .contains("Example Root CA"));

        // Xác minh lại cùng path đã được build, minh họa API riêng.
        PKIXParameters vp = new PKIXParameters(anchors);
        vp.setRevocationEnabled(false); // Chỉ lab.
        CertPathValidator validator =
                CertPathValidator.getInstance("PKIX");
        PKIXCertPathValidatorResult validated =
                (PKIXCertPathValidatorResult)
                        validator.validate(
                                built.getCertPath(), vp);

        System.out.println("Validator public key matches leaf: "
                + validated.getPublicKey()
                        .equals(leaf.getPublicKey()));

        // Builder chỉ thấy leaf; không có intermediate trong kho.
        PKIXBuilderParameters missing =
                new PKIXBuilderParameters(anchors, target);
        missing.setRevocationEnabled(false);
        missing.addCertStore(CertStore.getInstance(
                "Collection",
                new CollectionCertStoreParameters(
                        List.of(leaf))));
        boolean cannotBuild = false;
        try {
            builder.build(missing);
        } catch (CertPathBuilderException expected) {
            cannotBuild = true;
        }
        System.out.println("Build without intermediate fails: "
                + cannotBuild);

        // Validator nhận path hoàn chỉnh nhưng kiểm tại ngày
        // rất xa tương lai, sau hạn của leaf/intermediate.
        PKIXParameters future = new PKIXParameters(anchors);
        future.setRevocationEnabled(false);
        future.setDate(Date.from(
                Instant.now().plus(
                        4000, java.time.temporal.ChronoUnit.DAYS)));

        boolean expired = false;
        try {
            validator.validate(built.getCertPath(), future);
        } catch (CertPathValidatorException expected) {
            expired = true;
        }
        System.out.println("Validate at future date fails: "
                + expired);
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 CertPathBuilderValidatorDemo.java
java CertPathBuilderValidatorDemo
```

Stdout được kiểm chứng với JDK có thuật toán PKIX tương thích, sau khi tạo file từ **ba bước keytool** trên:

```text
Builder path certificates: 2
Builder trusted root: true
Validator public key matches leaf: true
Build without intermediate fails: true
Validate at future date fails: true
```

### 7.1 Giải thích các kết quả

**Dòng 1 — Builder path certificates = 2**

Đường dẫn gồm **leaf, intermediate**, không có root. `PKIXCertPathBuilderResult.getTrustAnchor()` cung cấp root được chọn **riêng**. Nếu chỉ đếm danh sách path rồi thấy không có root, đó **không phải một lỗi**.

**Dòng 2 — Builder trusted root = true**

Builder nối được đường dẫn đến một trust anchor do ta cung cấp trong `anchors`. `contains("Example Root CA")` chỉ là **cách in bool minh họa** dựa trên tên cố định của certificate lab; trong production không chọn trust bằng cách kiểm tra substring trong DN.

**Dòng 3 — Validator public key matches leaf = true**

`CertPathValidator` kiểm tra **cùng path** dưới `PKIXParameters` của lab và trả `PKIXCertPathValidatorResult` với public key của target. Việc gọi lại validator ở đây là để học sự khác biệt API; builder **đã validate path** khi `build` trả thành công.

**Dòng 4 — Build without intermediate fails = true**

Khi `CertStore` chỉ chứa leaf, không có intermediate và lab không có nguồn khác được cấu hình, builder **không tìm được** path tới root. Đây là lỗi **thiếu certificate ứng viên để xây đường dẫn**. Nó không có nghĩa root thiếu trong tập trust anchors; root vẫn có mặt.

**Dòng 5 — Validate at future date fails = true**

Đường dẫn vừa xây là hợp lệ **hiện tại**, nhưng ở thời điểm cách 4000 ngày theo ví dụ sẽ vượt khỏi hiệu lực của leaf/intermediate; `CertPathValidator` từ chối đường dẫn đó. Cùng một danh sách certificates có thể có kết quả khác nếu **validation date, trust anchors hoặc chính sách** khác.

### 7.2 Điều lab cố ý chưa kiểm tra

- **Revocation:** các certificate lab không có dịch vụ CRL/OCSP; `setRevocationEnabled(false)` chỉ dùng để chạy offline. Production cần chính sách thực về kiểm tra thu hồi.
- **Hostname/SAN của TLS:** `CertPathValidator.validate` không tự biết ứng dụng đang gọi URL nào. Dù leaf có `SAN=dns:service.example.test`, đoạn Java **không so sánh với endpoint yêu cầu**.
- **Mức tin cậy bên ngoài:** chương trình **chủ động** chọn root lab làm anchor. Nó không chứng minh certificate của một CA production được tổ chức chấp thuận.
- **Toàn bộ thuật toán/cipher suite TLS:** chứng chỉ có path PKIX hợp lệ chưa bảo đảm giao thức TLS handshake, host, ciphers, certificate usage và identity policy đều đúng.
- **Những path thay thế:** lab có đúng một intermediate. Production có thể có cross-signing/nhiều CA, khiến builder phải tìm/đánh giá nhiều lựa chọn.
- **Key usage tùy nghiệp vụ:** `serverAuth` và `digitalSignature` nằm trong certificate lab, nhưng ứng dụng vẫn cần ràng buộc mục đích dùng certificate đúng giao thức/JSSE.

## 8. Builder có thể giải bài toán mà Validator không tự giải

Một hệ thống thực tế chỉ nhận **leaf certificate** từ peer; intermediate có thể nằm trong kho của ứng dụng, trong certificate chain được gửi kèm hoặc một nguồn tra cứu được cấu hình phù hợp.

```text
Dữ liệu sẵn có:
  leaf
  candidates = {intermediate A, intermediate B, cross-signed CA C, ...}
  anchors = {root X, root Y}

Builder:
  chọn target leaf
  tìm issuer candidates
  kiểm tra chữ ký/validity/constraints theo cấu hình
  nối các candidate có thể dẫn về X hoặc Y
  trả một validated CertPath + TrustAnchor

Validator:
  nhận CertPath [leaf, intermediate A] đã có sẵn
  kiểm tra CHÍNH path đó dưới trust anchors/policy
  không tự đổi intermediate A thành B vì A thất bại
```

**Trường hợp cross-signing:** cùng một subject/public key CA có thể xuất hiện dưới nhiều certificate do issuer khác nhau ký. Có thể có nhiều con đường tới các root khác nhau; builder có thể tìm một con đường theo candidates và policy của provider. Nếu bạn đã nhận một certificate path cụ thể, validator không tự “đi đường vòng” bằng một cross-signed certificate khác. **Đừng chỉ dựa vào subject DN** để coi hai certificate là tương đương; chữ ký, key, extensions, issuance và policy đều quan trọng.

### 8.1 Khi nào xây đường dẫn là cần thiết?

- Ứng dụng có leaf certificate nhưng **không nhận đủ intermediate chain** và có nguồn candidate đáng tin cậy để tìm.
- Một certificate có thể được cấp qua **nhiều chain** đến nhiều root, cần tìm path phù hợp trust policy.
- Một hệ thống PKI nội bộ lưu certificate/CRL trong directory/collection và cần xây path từ end-entity đến root.
- Một công cụ kiểm chứng tài liệu/chữ ký phải tìm issuer path cho certificate của người ký.

Dù vậy, **builder không đảm bảo nguồn tải intermediate luôn có sẵn hoặc an toàn**. Nếu dùng AIA hoặc LDAP/network, cần hạn chế endpoint, timeout, số lượng/trọng lượng dữ liệu, xử lý SSRF và cache, và không coi content nhận về là trusted chỉ vì được tải tự động. Tùy JDK/provider, tự động truy vấn AIA có thể cần option riêng hoặc không được hỗ trợ; **không mặc định mọi build sẽ tự tải Internet**.

### 8.2 Khi nào chỉ cần Validator?

- Ứng dụng đã có một chain theo đúng thứ tự từ nguồn được xác định, muốn đánh giá nó theo một bộ `PKIXParameters`.
- Một ứng dụng muốn **kiểm chứng lại** path ở **ngày khác** (ví dụ thời gian ký và bằng chứng thời gian theo policy phù hợp) hoặc bộ trust anchors khác.
- Kiểm thử một đường dẫn cố định với certificate hết hạn, CA không phù hợp, policy không thỏa hoặc chữ ký sai.
- Công cụ quản trị/diagnostics cần phân biệt lỗi chính xác của **đường dẫn được cho trước**.

Nếu thiếu intermediate, không “sửa” bằng cách thêm root vào `CertPath`. Hãy cung cấp candidate đúng cho builder hoặc bổ sung **intermediate thực sự** vào path từ nguồn đã xác minh; trust anchor vẫn độc lập.

## 9. Kiểm tra path PKIX làm gì?

PKIX validation không đơn thuần là `cert.verify(issuer.getPublicKey())` cho từng cặp. Một đường dẫn được chấp nhận phải đáp ứng các kiểm tra liên quan **chữ ký và liên kết issuer**, **hiệu lực thời gian**, **CA basic constraints**, **key usage**, **name constraints**, **certificate policies**, **thuật toán/độ dài key** và các yêu cầu khác theo chuẩn, API, provider/policy.

Một mental model:

```text
CertPath [leaf, intermediate, ...] + TrustAnchor root
                   |
            1. Chain order/issuer
                   |
            2. Verify signatures
                   |
            3. Check dates (validationDate)
                   |
            4. Check BasicConstraints + keyCertSign
                   |
            5. Process name constraints / policy constraints
                   |
            6. Algorithm policy / critical extensions
                   |
            7. Revocation checking theo cấu hình
                   |
                PASS / FAIL
```

Thứ tự thật của thuật toán PKIX phức tạp hơn sơ đồ (xem RFC 5280, Section 6); **không được dùng sơ đồ này như một implementation spec** để tự viết validator. Một provider có thể thực thi các chính sách bổ sung như `jdk.certpath.disabledAlgorithms` ở JDK phù hợp.

### 9.1 Không nhầm trust anchors với certificate candidates

| Thuộc tính | TrustAnchor | CertStore candidate |
| --- | --- | --- |
| Do ai chấp thuận? | **Operator/ứng dụng** theo trust policy | Có thể được lấy từ mạng/file/peer, không đáng tin mặc định |
| Dùng để làm gì? | Là **điểm xuất phát tin cậy** khi đánh giá chain | Nguồn certificate/CRL giúp tìm/kiểm tra chain |
| Thêm vào API nào? | `PKIXParameters`/`PKIXBuilderParameters` | `PKIXParameters.addCertStore` và tham số builder |
| Thêm một phần tử có làm rộng trust không? | **Có thể**, nếu thêm anchor ngoài chính sách | **Không trực tiếp**; candidates vẫn phải được validate đến anchor |
| Hệ quả khi bị sửa trái phép | Có thể cho phép CA giả chấp thuận certificate giả | Có thể gây DoS, nhầm lựa chọn hoặc thử path khác; validation đúng vẫn cần trust anchors |

### 9.2 Path building không phải hostname verification

Một chain hợp lệ về chữ ký từ root chấp thuận có thể kết thúc ở certificate cho `service-a.example.test` trong khi client đang kết nối `service-b.example.test`. **Chỉ PKIX builder/validator không đủ để xác minh danh tính hostname của một HTTPS request**.

Khi làm TLS, dùng HTTP client/JSSE được cấu hình **endpoint identification** đúng API và chính sách, kiểm tra SAN, EKU/usage và tên endpoint theo giao thức. Đừng tự viết một trust manager chỉ gọi `CertPathValidator.validate` rồi tuyên bố HTTPS đã xác thực đầy đủ.

## 10. Revocation: cần thiết kế riêng, không để false trong production

Trong lab, ta gọi:

```java
params.setRevocationEnabled(false);
```

Điều này **tắt bước kiểm tra thu hồi** ở parameters, cần thiết cho demo offline không có CRL/OCSP. Trong sản phẩm thực:

- Chọn cơ chế CRL, OCSP hoặc checker tương ứng theo loại certificate/giao thức/policy.
- Quy định tính tươi của dữ liệu, thời gian, cache, timeout và cách xử lý khi nguồn revocation không khả dụng.
- Kiểm tra `PKIXRevocationChecker`/provider support nếu cần tùy chỉnh.
- Xác nhận nguồn CRL/OCSP được xác thực (CRL có chữ ký và issuer phù hợp; OCSP response theo chính sách).
- Phân biệt **không có phản hồi** với **certificate không bị thu hồi**; đừng đánh đồng cả hai khi chính sách đòi fail-closed.
- Test certificate bị revoke, certificate không có thông tin revocation, CRL expired và network timeout trên JDK/provider thật.

**Việc thêm `CertStore` có CRL không tự bảo đảm revocation được kiểm tra** nếu chính sách không bật/cấu hình tương ứng. Ngược lại, bật revocation mà không cung cấp dữ liệu/nguồn hoạt động có thể khiến path bị từ chối do không thể xác định trạng thái.

## 11. Các lỗi thường gặp và cách đọc đúng

| Dấu hiệu | Điểm xuất hiện | Chẩn đoán chính |
| --- | --- | --- |
| `CertPathBuilderException: unable to find valid certification path to requested target` | `builder.build` | Không có path đáp ứng **tập anchor, candidate và chính sách hiện tại**; kiểm tra intermediate, truststore, thuật toán, constraints, ngày, revocation |
| `CertPathValidatorException: validity check failed` | `validator.validate` | Certificate trong path ngoài thời gian hợp lệ tại `validationDate`; xem index/reason |
| `CertPathValidatorException: Path does not chain with any of the trust anchors` | `validator.validate` | Path được cho không nối được tới anchor; kiểm tra root/chain/order |
| `CertPathValidatorException: signature check failed` | `validator.validate` | Chữ ký issuer không hợp lệ, sai key, path bị thay hoặc chain không phù hợp |
| `CertPathValidatorException` với `INVALID_KEY_USAGE`/constraints | `validator.validate` | Intermediate thiếu `keyCertSign`, không được là CA, vi phạm constraints hoặc policy |
| `InvalidAlgorithmParameterException` | Constructor parameters hoặc validate | Không có trust anchor phù hợp, parameters sai hoặc không được provider chấp nhận |
| `CertificateException` | `CertificateFactory.generateCertificate`/`generateCertPath` | Input certificate/encoding bị hỏng hoặc không đúng format |
| `NoSuchAlgorithmException` | `getInstance("PKIX")` | JDK/provider không có engine `PKIX` |
| `CertStoreException` | `CertStore` lấy certificates/CRLs | Nguồn candidate hỏng, network/LDAP lỗi, selector/backend không tương thích |
| `SSLHandshakeException` có lỗi PKIX | TLS/JSSE | Có thể do builder/validator bên dưới; kiểm tra đúng truststore hiệu dụng **và** hostname/chain/policy |

**Đừng kết luận “thiếu root” từ mọi lỗi builder.** Trong lab, root vẫn được cấu hình, nhưng thiếu **intermediate** cũng làm builder thất bại. Ngược lại, có đủ intermediate mà root không được tin vẫn không thể chấp thuận path.

Khi xử lý `CertPathValidatorException` trong ứng dụng:

```java
// Đoạn minh họa, không phải một class đầy đủ.
// ex là CertPathValidatorException đã được bắt.
int index = ex.getIndex(); // Có thể là -1.
CertPathValidatorException.Reason reason = ex.getReason();
// Log reason/index ở kênh nội bộ được bảo vệ, tránh leak dữ liệu nhạy cảm.
```

Có thể bật debug tạm thời trong môi trường kiểm soát, chẳng hạn `-Djava.security.debug=certpath` với JDK/implementation hỗ trợ. Log path building có thể lộ hostname, subject/issuer, truststore và chi tiết nội bộ; không bật lâu dài trong production, không đưa vào API response công khai.

## 12. Các sai lầm lớn cần loại bỏ

1. **“Builder chỉ tìm chain, validator mới xác minh.”** Sai: với PKIX, builder **trả path đã validate**, không phải một danh sách ghép tùy tiện.
2. **“Validator sẽ tự tìm intermediate nếu path không có.”** Sai: validator kiểm tra **path được cung cấp**; nó không có nhiệm vụ path discovery.
3. **“Kết quả path chỉ có 2 certificate nghĩa là bị thiếu root.”** Không: PKIX builder **loại trust anchor khỏi `CertPath`**; anchor lấy riêng qua result.
4. **“CertStore = truststore.”** Không: `CertStore` chứa **candidates**; trust anchors là quyết định độc lập.
5. **“Thêm intermediate vào truststore sẽ chữa mọi lỗi PKIX.”** Rất nguy hiểm: có thể vô tình mở rộng tập CA được tin cậy; cung cấp intermediate làm candidate đúng chức năng.
6. **“Chỉ cần subject DN của issuer trùng subject DN của cert trước.”** Không: cần kiểm tra chữ ký, key, constraints, thời hạn, policy và chain đầy đủ.
7. **“Trust anchor phải luôn là self-signed root.”** Không về mặt API: trust anchor là một quyết định trust, có thể trỏ vào CA khác theo policy; nhưng phạm vi tin cậy phải được hiểu và kiểm soát.
8. **“Builder trả thành công nghĩa là hostname chính xác.”** Không: PKIX path validation không tự biết hostname mà client đang muốn kết nối.
9. **“Bật tắt revocation không quan trọng.”** Sai: đây là phần quan trọng của threat model; demo offline tắt không được copy vào production.
10. **“Set `maxPathLength=1` nghĩa là cả path chỉ gồm một cert.”** Không: tham số này giới hạn **intermediate**; leaf và trust anchor có vai trò riêng.
11. **“Path xây thành công hôm nay sẽ luôn hợp lệ.”** Không: time, revocation, trust policy và thuật toán bị cấm có thể thay đổi.
12. **“Có certificate bất kỳ từ mạng là có thể tin.”** Không: candidates không có authority; phải nối tới **trust anchor đã xác minh trước**.
13. **“Đúng root CA là đủ chống mọi MITM.”** Không: cần endpoint identity (SAN/hostname), EKU/purpose và bảo vệ giao thức TLS.
14. **“Một lời gọi `CertPathValidator.validate` chính là toàn bộ TLS handshake.”** Không: TLS còn key agreement, proof of possession, protocol negotiation, record protection, replay policies và JSSE configuration.

## 13. Tình huống luyện tập

**Tình huống A.** Client có leaf của server và truststore chứa root; peer không gửi intermediate nhưng ứng dụng có `intermediate.pem` ở một `CertStore`. Nên dùng gì?  
**Trả lời:** Dùng **`CertPathBuilder`** nếu cần tìm path từ leaf tới trust anchor qua candidate; builder có thể tìm intermediate và validate chain. Không thêm intermediate làm trust anchor chỉ để qua lỗi.

**Tình huống B.** Ứng dụng đã có path `[leaf, intermediate]` và muốn xác minh nó có còn hợp lệ vào một thời điểm cụ thể.  
**Trả lời:** Dùng **`CertPathValidator`** với `PKIXParameters` có trust anchors và `setDate(...)` theo policy; nó kiểm tra **path đã cho**.

**Tình huống C.** Builder trả `getCertPath().getCertificates().size() == 2` trong hệ thống có root/intermediate/leaf. Có thiếu root không?  
**Trả lời:** Không. **Root là trust anchor**, lấy từ `getTrustAnchor()`, không nằm trong danh sách path.

**Tình huống D.** Có certificate chain với leaf cho `internal-a.example.test`, app kết nối tới `internal-b.example.test`. Builder/validator PKIX thành công có đủ an toàn cho HTTPS không?  
**Trả lời:** **Không.** Cần kiểm tra hostname/endpoint identity và mục đích certificate trong TLS client.

**Tình huống E.** Một intermediate xuất hiện trong `CertStore` có chữ ký hợp lệ nhưng dẫn về root chưa được tin. Có được chấp thuận không?  
**Trả lời:** Không chỉ nhờ intermediate nằm trong CertStore. Phải có path đến **trust anchor được chấp thuận** và các constraints đều thỏa.

**Tình huống F.** Builder với `setMaxPathLength(0)` thất bại vì cần một intermediate. Có thể tăng lên `1` không?  
**Trả lời:** Có thể nếu trust policy chấp thuận một intermediate và các CA constraints cho phép; cần đánh giá bảo mật. Đừng tự động cho phép vô hạn chỉ vì lỗi.

**Tình huống G.** Có đủ chain và anchor nhưng validator báo `EXPIRED` tại thời gian hiện tại. Có nên gọi builder để “tìm một chain khác” và bỏ qua expiry?  
**Trả lời:** Không được bỏ qua yêu cầu thời hạn. Builder cũng phải tôn trọng validation date; nếu thực sự có path khác hợp lệ thì chỉ khi chính sách cho phép mới có ý nghĩa.

**Tình huống H.** CertStore có nhiều CRL, nhưng `setRevocationEnabled(false)`. Certificate bị thu hồi có chắc bị từ chối không?  
**Trả lời:** Không. Thêm CRL candidates không tự bật revocation. Production phải cấu hình revocation checker/policy đúng và test.

**Tình huống I.** Một certificate tự ký của attacker được cấp vào truststore bởi một script import tự động. Builder có thể trả thành công cho leaf do attacker cấp không?  
**Trả lời:** Có thể nếu anchor bị thêm trái phép và các tham số còn lại thỏa; truststore quản trị sai **làm thay đổi ranh giới tin cậy**. Không nhập chứng chỉ chưa xác minh.

**Tình huống J.** `PKIXCertPathBuilderResult` trả path đã validate. Vì sao ví dụ còn gọi `CertPathValidator`?  
**Trả lời:** Để học hai API và minh họa **revalidation** khi thay thời điểm/chính sách. Không phải vì builder chỉ “xây mà chưa kiểm”.

## 14. Checklist khi thiết kế PKIX trong Java

- **Xác định đúng việc cần làm:** cần **tìm** path từ target → builder; đã có path muốn **kiểm tra** → validator.
- **Chọn trust anchors từ nguồn đã xác minh:** tránh import certificate vô tội vạ, không đánh đồng candidate với trust.
- **Chọn target constraints chính xác:** `X509CertSelector` phù hợp, nhận dạng certificate hoặc key/subject/usage cần dùng.
- **Cung cấp candidate stores có kiểm soát:** leaf, intermediates và CRL; giới hạn kết nối mạng và input kích thước lớn.
- **Hiểu `CertPath` ordering:** Java PKIX path thường từ leaf đến intermediate, **không chứa trust anchor**.
- **Dùng đúng parameter class:** `PKIXBuilderParameters` khi `build`, `PKIXParameters` khi `validate`; đặt date, revocation, policy, algorithm constraints phù hợp.
- **Không tắt revocation vì tiện:** phân loại demo offline với production, có chiến lược CRL/OCSP/failure handling.
- **Đừng coi PKIX là hostname check:** TLS client phải thực hiện endpoint identification/SAN và certificate purpose/usage checks.
- **Báo lỗi đúng API:** builder failure khác validator failure; ghi reason/index/cause trong log an toàn khi có thể.
- **Kiểm thử nhiều loại path:** thiếu intermediate, wrong anchor, expired, invalid signature, constrained CA, cross-sign, revocation và hostname mismatch.
- **Bám theo provider/runtime thật:** `PKIX` thường có sẵn trên JDK, nhưng certpath policies, AIA behavior, disabledAlgorithms, revocation support có thể thay đổi.
- **Không tự viết validator X.509:** RFC 5280 phức tạp; dùng JCA/JSSE và thư viện/giao thức được duy trì.

## 15. Tài liệu đối chiếu

- [Java SE 21 — `CertPathBuilder`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/CertPathBuilder.html): engine xây đường dẫn và `build`.
- [Java SE 21 — `CertPathValidator`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/CertPathValidator.html): engine kiểm tra path đã cho.
- [Java SE 21 — `PKIXBuilderParameters`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/PKIXBuilderParameters.html): target constraints, trust anchors, CertStore và `maxPathLength`.
- [Java SE 21 — `PKIXParameters`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/PKIXParameters.html): date, policies, revocation, trust anchors, CertStores.
- [Java SE 21 — `PKIXCertPathBuilderResult`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/PKIXCertPathBuilderResult.html) và [`PKIXCertPathValidatorResult`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/PKIXCertPathValidatorResult.html): kết quả thành công và ý nghĩa trust anchor.
- [Java SE 21 — `CertStore`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/CertStore.html), [`TrustAnchor`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/TrustAnchor.html), [`X509CertSelector`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/X509CertSelector.html): candidate pool, điểm tin cậy và lựa chọn target.
- [Java SE 17 — Java PKI Programmer’s Guide](https://docs.oracle.com/en/java/javase/17/security/java-pki-programmers-guide.html): ví dụ path building/validation và tập API certpath.
- [RFC 5280 — X.509 PKIX Certificate and CRL Profile](https://www.rfc-editor.org/rfc/rfc5280.html): thuật toán kiểm tra certification path, trust anchor, constraints, CRL.
- [RFC 4158 — Internet X.509 Public Key Infrastructure: Certification Path Building](https://www.rfc-editor.org/rfc/rfc4158.html): tư duy path building và lựa chọn đường dẫn.

**Điểm cần nhớ:** `CertPathBuilder` **tìm và trả một path đã được xác minh**, sử dụng target, candidates và trust anchors; `CertPathValidator` **kiểm tra một path được đưa vào**, không tự xây chain khác. **Trust anchor là quyết định tin cậy**, `CertStore` chỉ là nguồn ứng viên, và **PKIX path thành công không thay thế kiểm tra hostname, mục đích certificate hay toàn bộ TLS policy**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cert-path-003">14.3 Cấu hình PKIXParameters, PKIXBuilderParameters và X509CertSelector</a>

<details>
<summary>Xem chi tiết</summary>

# 14.3. Cấu hình `PKIXParameters`, `PKIXBuilderParameters` và `X509CertSelector` trong Java 21

Một chuỗi chứng thư số trông có vẻ hợp lý — leaf do intermediate CA ký, intermediate do root CA ký — **chưa đủ** để một ứng dụng tin rằng chứng thư được cấp cho đúng đối tượng. Ứng dụng cần chọn **trust anchor** từ nguồn tin cậy riêng, tìm hoặc nhận **certificate path**, kiểm các chữ ký và ràng buộc **PKIX**, thời hạn, basic constraints, key usage, chính sách và trạng thái thu hồi khi áp dụng. Ngoài ra, giao thức còn phải xác minh **danh tính mục tiêu**, ví dụ hostname trong HTTPS.

Java cung cấp những khối xây dựng riêng biệt: `X509CertSelector` xác định chứng thư đích cần tìm; `PKIXBuilderParameters` cấu hình **tìm và dựng đường dẫn** đến trust anchor; còn `PKIXParameters` chứa các tham số để **xác minh đường dẫn đã có**. Chúng làm việc với `CertPathBuilder`, `CertPathValidator`, `CertStore` và `TrustAnchor`.

Bài học thuộc mục **“14. Đường dẫn tin cậy”** của Java Advanced – Security & Cryptography. Mục tiêu chính là cấu hình ba lớp `PKIXParameters`, `PKIXBuilderParameters` và `X509CertSelector` **đúng vai trò**, hiểu tác động từng tham số, thực hành Java 21 với chuỗi root → intermediate → leaf và thử trường hợp CA không được tin.

## Mục tiêu học tập

Sau bài này, bạn có thể:

1. Phân biệt *certificate path building*, *path validation* và *application identity verification*; giải thích vì sao kiểm chữ ký của từng certificate không tự tạo ra sự tin cậy.
2. Sử dụng `TrustAnchor` từ CA tin cậy **được cấu hình ngoài chuỗi chứng thư chưa tin**, hiểu nguồn `KeyStore` hoặc `Set<TrustAnchor>`.
3. Tạo `X509CertSelector` với leaf cụ thể, subject, `basicConstraints` và `extendedKeyUsage`; hiểu selector chỉ là **bộ lọc chứng thư đích**, không thay thế chứng thực toàn bộ path.
4. Cấu hình `PKIXBuilderParameters`, thêm `CertStore` chứa candidate certificates, `setMaxPathLength`, `setDate`/`setRevocationEnabled` theo chính sách; gọi `CertPathBuilder.getInstance("PKIX")`.
5. Dùng `PKIXParameters` với `CertPathValidator.getInstance("PKIX")` để xác minh một `CertPath` đã xây, kiểm tra root nhận được từ kết quả.
6. Chạy chương trình Java 21 đầy đủ và giải thích đầu ra **path entries = 2**, **root excluded**, **trusted root validated**, **untrusted root rejected**.
7. Tránh các lỗi như tin root do attacker gửi, tắt revocation trong production không có đánh giá, bỏ qua hostname/SAN, nhầm candidate `CertStore` với truststore và tự triển khai TLS certificate verification.

## 1. Mô hình trust path: ba vai trò trong một chuỗi

Xét hệ thống muốn xác thực chứng thư cho `service.example.test`. Chuỗi ví dụ:

```text
             Trust anchor: Lab Root CA
          (được cấu hình tin cậy độc lập)
                         |
                         | ký, CA:TRUE, pathLen:1
                         v
             Lab Intermediate CA
                 CA:TRUE, pathLen:0
                         |
                         | ký, CA:TRUE của issuer
                         v
    Leaf: CN=service.example.test
      SAN: DNS:service.example.test
      EKU: serverAuth
      BasicConstraints: CA:FALSE
```

Trong PKIX theo cách thường dùng:

- **Leaf (end-entity certificate):** chứng thư đích cần đánh giá; có public key dịch vụ, SAN, EKU và các extension.
- **Intermediate CA:** chứng thư CA trung gian dùng để xây path; chứng thư này phải thỏa mãn điều kiện ký certificate, basic constraints, path length, validity, signature/policy constraints.
- **Trust anchor/root:** một public key hoặc certificate đã **được quyết định tin bởi ứng dụng/truststore**. Đó là điểm kết thúc chuỗi logic, không phải thứ được tự suy ra vì certificate “tự ký”.

Một `CertPath` theo PKIX có thứ tự **target → intermediates**. **Trust anchor thường không nằm trong `CertPath.getCertificates()`**; nó được cung cấp riêng bằng tham số và xuất hiện trong kết quả build/validate. Với bài lab có một intermediate, số lượng certificate **trong path là 2**, dù sơ đồ logic có 3 certificate tính cả root.

### 1.1 Ba câu hỏi khác nhau

| Bước | Câu hỏi cần trả lời | Java API |
| --- | --- | --- |
| **Chọn target** | Chứng thư end-entity nào thỏa selector (subject, EKU, key usage...)? | `X509CertSelector` |
| **Path building** | Có thể xây chuỗi target → CA trung gian → trust anchor đã cấu hình không? | `PKIXBuilderParameters` + `CertPathBuilder` |
| **Path validation** | Một chuỗi đã có có hợp lệ theo PKIX, trust anchor, thời điểm/chính sách không? | `PKIXParameters` + `CertPathValidator` |
| **Identity/authorization** | Chứng thư hợp lệ có thực sự thuộc hostname/đơn vị/dịch vụ mong đợi và được phép thực hiện hành động này không? | TLS/JSSE hostname verifier, policy ứng dụng |

**Lưu ý:** `CertPathBuilder` theo PKIX vừa tìm đường dẫn vừa thực hiện/đòi hỏi tính hợp lệ của path theo tham số. Gọi `CertPathValidator` sau đó trong ví dụ là một bước **học tập minh họa**, không phải luôn bắt buộc sau mọi `CertPathBuilder` thành công. Không sử dụng điều đó để suy luận rằng builder “không validate gì”.

## 2. `PKIXParameters`: chính sách xác minh một path đã có

`java.security.cert.PKIXParameters` là một lớp tham số cho các thuật toán PKIX, đặc biệt `CertPathValidator`. Bạn có thể tạo từ:

```java
Set<TrustAnchor> anchors = Set.of(new TrustAnchor(rootCertificate, null));
PKIXParameters parameters = new PKIXParameters(anchors);
```

Đây là **đoạn minh họa trong scope** có biến `rootCertificate` thuộc kiểu `X509Certificate`; chương trình chạy đầy đủ nằm ở phần thực hành.

### 2.1 Điều kiện quan trọng nhất: TrustAnchor đúng nguồn

`TrustAnchor` thể hiện một CA/public key mà ứng dụng **đã quyết định tin**. Có thể tạo từ `X509Certificate` của CA hoặc CA name/public key theo constructor tương ứng. *Trust anchor không tự có độ tin cậy chỉ nhờ đối tượng Java được tạo.*

**Sai rất nguy hiểm:** lấy certificate cuối chuỗi do server chưa xác thực gửi tới, rồi viết `new TrustAnchor(peerRoot, null)`. Bằng cách đó attacker chỉ cần tự tạo root và ký leaf của mình là vượt được đường dẫn PKIX do ứng dụng vừa “tự tin” vào root giả.

**Đúng:** trust anchors đến từ truststore/configuration đã được bảo vệ, CA được quản trị, pinning hoặc quy trình phân phối ngoài băng tin cậy. Trong bài lab, file root PEM được coi như một **trust anchor được cấp trước có chủ đích** để mô phỏng việc cấu hình này, không có nghĩa mọi file root PEM bất kỳ phải được chấp nhận.

Có thể tạo `PKIXParameters` từ một `KeyStore` đã `load`, khi truststore có `TrustedCertificateEntry`:

```java
PKIXParameters parameters = new PKIXParameters(trustStore);
```

Constructor `KeyStore` chỉ dùng các trusted X.509 certificate entries làm CA đáng tin, không phải ngầm xem mọi `PrivateKeyEntry` là trust anchor. Phải quản lý file truststore riêng, kiểm soát import và update CA. `new PKIXParameters(Set.of(...))` cũng yêu cầu set không rỗng và các phần tử hợp lệ.

### 2.2 Các cấu hình quan trọng

| API | Ý nghĩa | Hướng dẫn |
| --- | --- | --- |
| `getTrustAnchors()` / `setTrustAnchors(...)` | Các CA/public keys được tin | Giữ nguồn và quyền cập nhật rõ ràng; không lấy từ peer tùy tiện |
| `setDate(Date)` | Thời điểm dùng khi kiểm tra chứng thư | Dùng đồng hồ/chính sách có kiểm soát; nếu không chỉ định, thông thường lấy thời điểm hiện hành |
| `setRevocationEnabled(boolean)` | Có thực hiện revocation checking theo khả năng provider/cấu hình không | **Không tắt trong production chỉ vì lab dễ chạy** |
| `addCertStore(CertStore)` | Nguồn các candidate certificate/CRL để tìm/kiểm path | Chứa candidate **không có nghĩa đã tin** |
| `setInitialPolicies(Set<String>)` | Tập certificate-policy OID quan tâm ban đầu | Chỉ cấu hình sau khi hiểu policy PKIX và yêu cầu của domain |
| `setExplicitPolicyRequired(boolean)` | Ràng buộc explicit policy | Có thể làm path không hợp lệ nếu policy cần không được đáp ứng |
| `setPolicyMappingInhibited(boolean)` | Kiểm soát policy mapping | Một phần kiểm soát certificate policy được tiêu chuẩn hóa |
| `setAnyPolicyInhibited(boolean)` | Kiểm soát vai trò `anyPolicy` | Tránh tự đặt theo suy đoán, cần profile cụ thể |
| `addCertPathChecker(PKIXCertPathChecker)` | Kiểm tra PKIX custom ở path | Chỉ dùng khi có yêu cầu đã được đặc tả và kiểm thử |
| `setSigProvider(String)` | Provider dùng cho xác minh chữ ký khi cần | Mặc định để JCA chọn thường phù hợp; chính sách HSM/FIPS có thể khác |

Một lỗi phổ biến là đặt `setRevocationEnabled(false)` để “không còn lỗi OCSP/CRL” rồi đưa vào production. Bài lab **cố ý tắt** vì toàn bộ CA tự dựng không phát hành CRL/OCSP; đây chỉ là cách tránh phụ thuộc dịch vụ mạng trong thử nghiệm, không phải khuyến nghị bảo mật.

### 2.3 Không nhầm certificate validity và trust policy

`setDate(...)` ảnh hưởng kiểm tra thời điểm hợp lệ `NotBefore/NotAfter` của các certificate đang kiểm. Dùng một thời điểm quá khứ để xác minh lưu trữ chữ ký lịch sử cần chính sách thời gian, timestamp và revocation phù hợp; **không** tùy tiện lùi đồng hồ để “làm cert hết hạn trở nên hợp lệ”. Các thuộc tính như certificate policies, name constraints, algorithm constraints và trust anchors vẫn quan trọng.

Root được chỉ định làm **trust anchor** thường không được xử lý như một chứng thư thông thường trong path. Tổ chức phải tự quản trị trust anchor về nguồn gốc, vòng đời, alg/key strength và thay thế an toàn.

## 3. `X509CertSelector`: cách mô tả chứng thư đích

`X509CertSelector` thực hiện interface `CertSelector` và cho phép kiểm tra một certificate có khớp yêu cầu không (`match(Certificate)`). Khi build PKIX, nó được truyền vào `PKIXBuilderParameters` làm `targetConstraints`.

Ví dụ trong chương trình sắp chạy:

```java
X509CertSelector target = new X509CertSelector();
target.setCertificate(leaf);
target.setSubject(leaf.getSubjectX500Principal());
target.setBasicConstraints(-2);
target.setExtendedKeyUsage(Set.of("1.3.6.1.5.5.7.3.1"));
```

Ý nghĩa:

- `setCertificate(leaf)` yêu cầu target chính là certificate cụ thể đó. Phù hợp khi ứng dụng đã nhận/giữ leaf và muốn dựng path cho nó; **không** nhất thiết dùng nếu bạn đang tìm nhiều certificate theo đặc tính.
- `setSubject(X500Principal)` ràng buộc subject distinguished name. Trong đoạn trên, nó mang tính minh họa thêm vì đã chỉ định chính certificate bằng `setCertificate`.
- `setBasicConstraints(-2)` có nghĩa chỉ chấp nhận **end-entity certificate**, không chấp nhận certificate CA. Với API này, `-1` là không áp constraint basic constraints, còn giá trị **0 trở lên** dùng để yêu cầu certificate CA với mức path length tương ứng.
- `setExtendedKeyUsage(Set<String>)` nhận **OID** dạng chuỗi; `1.3.6.1.5.5.7.3.1` là `id-kp-serverAuth`. Nếu certificate có EKU không phù hợp, selector sẽ không chọn nó. Không nhầm tên hiển thị `serverAuth` với tên API/OID.
- Có thêm `setKeyUsage(boolean[])`, `setSubjectAlternativeNames(...)`, `setIssuer(...)`, `setSerialNumber(...)`, `setCertificateValid(...)` và các selector chuyên biệt khác, cần biết chính xác hợp đồng và cách kết hợp trước khi dùng.

**Mỗi constraint đặt vào selector thường là điều kiện bổ sung**, chứ không mang ý nghĩa “nếu một điều kiện đúng thì đủ”. Chẳng hạn có cùng subject name chưa chứng minh cert được tin; tên `CN=service.example.test` có thể bị attacker đặt vào một chứng thư tự ký.

### 3.1 Selector khác hostname verification

Chọn certificate có `subject == X` hoặc `EKU == serverAuth` chưa tự kiểm tra **hostname bạn đang kết nối** là `api.example.test` hay `service.example.test`. HTTPS cần kiểm tra identity từ SAN và quy tắc protocol; TLS JSSE client nên được cấu hình **endpoint identification/hostname verification** theo profile. Một certificate chain có thể PKIX-valid nhưng dành cho **domain khác**. Chính sách ứng dụng còn có thể yêu cầu tenant, purpose và authorization độc lập.

`setSubjectAlternativeNames(...)` của `X509CertSelector` là một **bộ lọc** hữu ích khi lựa chọn, nhưng không tự thay thế toàn bộ hostname verification chuẩn TLS (wildcard, DNS/IP, tên quốc tế, các loại SAN và bối cảnh endpoint). Tránh tự viết hostname verifier tùy tiện nếu đã có stack TLS chuẩn.

### 3.2 Selector không đồng nghĩa PKIX validation

`selector.match(cert) == true` chỉ là sự phù hợp đối với **những điều kiện đã đặt**. Chữ ký issuer, CA path length, trust anchor, CRL/OCSP và các chính sách PKIX khác phải được kiểm theo builder/validator. Đừng tạo một selector với một điều kiện đúng rồi coi đó là chứng thư đáng tin.

## 4. `PKIXBuilderParameters`: từ target đến root đáng tin

`PKIXBuilderParameters` **kế thừa** `PKIXParameters` và bổ sung ràng buộc để bộ dựng đường dẫn lựa chọn target, tìm issuer certificates và dừng ở trust anchor.

Khởi tạo:

```java
Set<TrustAnchor> anchors =
        Set.of(new TrustAnchor(root, null));
PKIXBuilderParameters builderParams =
        new PKIXBuilderParameters(anchors, target);
```

Ở đây `root` là certificate được lấy từ kho tin cậy hợp lệ, `target` là `X509CertSelector` đã thiết lập. Một instance cũng có thể được tạo từ `KeyStore` cùng `CertSelector`. Mặc định của builder có giới hạn path length; bài lab đặt tường minh để thể hiện chính sách.

### 4.1 Dùng `CertStore` làm nguồn chứng thư trung gian

Builder cần tìm các certificate có thể nối leaf với trust anchor. Trong lab, không yêu cầu gọi HTTP hay LDAP:

```java
CertStore candidates = CertStore.getInstance(
        "Collection",
        new CollectionCertStoreParameters(
                List.of(leaf, intermediate)));
builderParams.addCertStore(candidates);
```

`CollectionCertStoreParameters` nhận một collection certificate/CRL trong bộ nhớ, hữu ích cho lab và nhiều luồng dữ liệu đã nhận sẵn. **CertStore chỉ là nguồn ứng viên, không phải nguồn tin cậy!** Bạn có thể thêm certificate do peer gửi vào `CertStore` để builder tìm path, miễn trust anchors đến từ nguồn độc lập. Thêm một CA tự ký vào collection không khiến nó được tin.

Ngoài Collection, provider có thể có nguồn certificate/CRL khác; lựa chọn và hành vi lấy issuer qua AIA/network không nên dựa vào phỏng đoán. Với hệ thống thật, thiết kế rõ cách fetch issuer và CRL/OCSP, caching, timeout, offline behavior và quyền truy cập mạng.

### 4.2 `setMaxPathLength` là giới hạn intermediates

Bài lab:

```java
builderParams.setMaxPathLength(1);
```

Trong PKIXBuilderParameters, tham số này giới hạn **số lượng certificate CA trung gian không tự cấp (non-self-issued)** trong path, không đơn thuần là `chain.length` và cũng không đếm leaf hay root trust anchor như thể chúng đều là intermediate.

- `0`: không cho intermediate thông thường; path chỉ cần một certificate end-entity dưới trust anchor khi có thể.
- `1`: cho một intermediate, như `Lab Intermediate CA` trong fixture.
- `-1`: không áp giới hạn từ thuộc tính này; **không** có nghĩa bỏ qua basic constraints của CA certificate.
- Nhỏ hơn `-1`: không hợp lệ.

Các extension `BasicConstraints.pathLenConstraint` của CA còn có ràng buộc riêng và có thể chặt hơn policy builder. Ví dụ root lab cho một intermediate, intermediate cho **0 CA cấp dưới nữa**. Vì vậy không thể thêm tùy ý một CA mới chỉ vì builder cho path length lớn.

### 4.3 Builder chạy thuật toán gì?

```java
PKIXCertPathBuilderResult result =
    (PKIXCertPathBuilderResult)
        CertPathBuilder.getInstance("PKIX").build(builderParams);
```

Kết quả chứa `CertPath` đã chọn, `TrustAnchor` được dùng, policy tree/public key liên quan. Nếu không tìm được chain hợp lệ, builder ném `CertPathBuilderException` (có thể chứa nguyên nhân provider-specific). Lỗi có thể do target không có, không có intermediate, trust anchor sai, cert hết hạn, không đáp ứng constraints, revocation unavailable hoặc policy mismatch; không kết luận “password sai” hay “public key sai” chỉ từ message chung.

**Build thành công không đồng nghĩa hostname đúng hoặc người dùng có quyền.** Đó là kiểm chứng PKIX theo tập tham số được cung cấp.

## 5. Thực hành: tạo CA root → intermediate → leaf bằng `keytool`

Chúng ta tạo **tệp lab duy nhất** `lab.p12` chứa private keys của CA và dịch vụ **chỉ để phát hành chứng thư thử nghiệm**. Sau đó xuất **bốn public certificates** thành PEM để Java build PKIX; chương trình Java **không đọc private key**. Điều này giữ trọng tâm đúng ở xây dựng/kiểm tra trust path.

> Tuyệt đối không áp dụng cách đặt CA signing private key cùng file dịch vụ trong production. Một PKI thật cần cách ly CA key (HSM/offline hoặc policy phù hợp), role separation, certificate issuance audit, revocation và bảo vệ backup.

### 5.1 Chuẩn bị thư mục và password lab

Dùng một **thư mục trống** với JDK 21 `keytool` và `javac` trong PATH. Hai cách đặt biến môi trường:

PowerShell (Windows):

```powershell
$env:PKIX_DEMO_PASS = 'LabOnly-TrustChain42'
```

Bash (Linux/macOS):

```bash
export PKIX_DEMO_PASS='LabOnly-TrustChain42'
```

Chuỗi trên **chỉ dành cho lab**. `-storepass:env PKIX_DEMO_PASS` đọc password từ biến môi trường thay vì đưa trực tiếp giá trị vào command line; đây chưa phải hệ thống quản lý secret production.

Kiểm tra:

```text
java -version
javac -version
keytool -help
```

### 5.2 Tạo root CA tự ký

```text
keytool -genkeypair -alias lab-root -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -dname "CN=Lab Root CA,O=Training,C=VN" -ext "BC:critical=ca:true,pathlen:1" -ext "KU:critical=keyCertSign,cRLSign" -validity 60 -keystore lab.p12 -storetype PKCS12 -storepass:env PKIX_DEMO_PASS -keypass:env PKIX_DEMO_PASS -noprompt
```

Root CA có `BasicConstraints CA:TRUE`, `pathLen:1` và `keyCertSign`. Certificate root tự ký **không tự đáng tin**: nó chỉ trở thành trust anchor của bài lab vì chúng ta **chủ động cấp trước** trong code.

### 5.3 Tạo intermediate có chữ ký của root

```text
keytool -genkeypair -alias lab-intermediate -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -dname "CN=Lab Intermediate CA,O=Training,C=VN" -ext "BC:critical=ca:true,pathlen:0" -ext "KU:critical=keyCertSign,cRLSign" -signer lab-root -signerkeypass:env PKIX_DEMO_PASS -validity 45 -keystore lab.p12 -storetype PKCS12 -storepass:env PKIX_DEMO_PASS -keypass:env PKIX_DEMO_PASS -noprompt
```

`-signer lab-root` dùng private key ở alias root ký intermediate; tạo chain cho alias `lab-intermediate`. Extension `pathlen:0` ngăn intermediate này làm CA cấp trên cho **một CA trung gian mới** nữa, nhưng vẫn có thể cấp end-entity certificate theo policy.

### 5.4 Tạo chứng thư dịch vụ (leaf) do intermediate ký

```text
keytool -genkeypair -alias lab-service -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -dname "CN=service.example.test,O=Training,C=VN" -ext "BC:critical=ca:false" -ext "KU:critical=digitalSignature,keyEncipherment" -ext "EKU=serverAuth" -ext "SAN=dns:service.example.test" -signer lab-intermediate -signerkeypass:env PKIX_DEMO_PASS -validity 20 -keystore lab.p12 -storetype PKCS12 -storepass:env PKIX_DEMO_PASS -keypass:env PKIX_DEMO_PASS -noprompt
```

Leaf có `BasicConstraints CA:FALSE`, EKU `serverAuth` và DNS SAN `service.example.test`. Đây là certificate lab, **không dùng làm chứng thư TLS production** và không trông đợi được trình duyệt tin vì root CA tự ký chưa nằm trong truststore.

Trong `lab.p12`, `lab-service` có thể có chain length **3**: leaf + intermediate + root. Nhưng khi Java xây path đối với root làm `TrustAnchor`, kết quả `CertPath` chỉ chứa leaf + intermediate.

### 5.5 Tạo root không đáng tin để kiểm thử thất bại

```text
keytool -genkeypair -alias lab-untrusted -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -dname "CN=Untrusted Root,O=Training,C=VN" -ext "BC:critical=ca:true,pathlen:1" -ext "KU:critical=keyCertSign,cRLSign" -validity 50 -keystore lab.p12 -storetype PKCS12 -storepass:env PKIX_DEMO_PASS -keypass:env PKIX_DEMO_PASS -noprompt
```

`lab-untrusted` là một CA riêng, không ký leaf hay intermediate ở trên. Khi thay root tin cậy bằng chứng thư này, builder **phải thất bại** dù leaf và intermediate đều có chữ ký nội bộ khớp.

### 5.6 Xuất certificate PEM cho Java

Chạy bốn lệnh, chỉ xuất **public certificates**, không xuất private key:

```text
keytool -exportcert -rfc -alias lab-root -keystore lab.p12 -storetype PKCS12 -storepass:env PKIX_DEMO_PASS -file lab-root.pem
keytool -exportcert -rfc -alias lab-intermediate -keystore lab.p12 -storetype PKCS12 -storepass:env PKIX_DEMO_PASS -file lab-intermediate.pem
keytool -exportcert -rfc -alias lab-service -keystore lab.p12 -storetype PKCS12 -storepass:env PKIX_DEMO_PASS -file lab-service.pem
keytool -exportcert -rfc -alias lab-untrusted -keystore lab.p12 -storetype PKCS12 -storepass:env PKIX_DEMO_PASS -file lab-untrusted.pem
```

Kiểm tra alias service:

```text
keytool -list -v -alias lab-service -keystore lab.p12 -storetype PKCS12 -storepass:env PKIX_DEMO_PASS
```

Các dòng cấu trúc mong đợi:

```text
Alias name: lab-service
Entry type: PrivateKeyEntry
Certificate chain length: 3
Owner: CN=service.example.test, O=Training, C=VN
Issuer: CN=Lab Intermediate CA, O=Training, C=VN
...
Owner: CN=Lab Intermediate CA, O=Training, C=VN
Issuer: CN=Lab Root CA, O=Training, C=VN
...
Owner: CN=Lab Root CA, O=Training, C=VN
Issuer: CN=Lab Root CA, O=Training, C=VN
```

`keytool -list` hiển thị **chain lưu trong KeyStore**, không phải `CertPath` mà validator nhận sau này. Hai khái niệm có thứ tự liên quan nhưng **khác điểm kết thúc**.

## 6. Java 21: builder + validator + selector, kiểm cả trường hợp CA sai

Tạo file **`PkixPathDemo.java`** trong cùng thư mục với bốn tệp `*.pem`. Chương trình không dùng private key và không cần dependency ngoài JDK.

```java
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertPathValidator;
import java.security.cert.CertStore;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXCertPathBuilderResult;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;

public final class PkixPathDemo {
    static X509Certificate read(Path directory, String name) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        try (InputStream in = Files.newInputStream(directory.resolve(name))) {
            return (X509Certificate) cf.generateCertificate(in);
        }
    }

    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args.length == 1 ? args[0] : ".");
        X509Certificate root = read(directory, "lab-root.pem");
        X509Certificate intermediate = read(directory, "lab-intermediate.pem");
        X509Certificate leaf = read(directory, "lab-service.pem");
        X509Certificate otherRoot = read(directory, "lab-untrusted.pem");

        // Policy: trust is provisioned from outside the untrusted chain.
        Set<TrustAnchor> anchors = Set.of(new TrustAnchor(root, null));
        X509CertSelector target = new X509CertSelector();
        target.setCertificate(leaf);
        target.setSubject(leaf.getSubjectX500Principal());
        target.setBasicConstraints(-2); // End-entity, not a CA.
        target.setExtendedKeyUsage(Set.of("1.3.6.1.5.5.7.3.1")); // serverAuth

        CertStore issuerCandidates = CertStore.getInstance("Collection",
                new CollectionCertStoreParameters(List.of(leaf, intermediate)));
        PKIXBuilderParameters buildParams =
                new PKIXBuilderParameters(anchors, target);
        buildParams.addCertStore(issuerCandidates);
        buildParams.setMaxPathLength(1);
        buildParams.setRevocationEnabled(false); // Lab only!

        PKIXCertPathBuilderResult path =
                (PKIXCertPathBuilderResult) CertPathBuilder
                        .getInstance("PKIX").build(buildParams);
        System.out.println("path entries: " +
                path.getCertPath().getCertificates().size());
        System.out.println("root omitted from path: " +
                !path.getCertPath().getCertificates().contains(root));
        System.out.println("path starts at leaf: " +
                path.getCertPath().getCertificates().get(0).equals(leaf));

        PKIXParameters validationParams = new PKIXParameters(anchors);
        validationParams.setRevocationEnabled(false); // Lab only!
        PKIXCertPathValidatorResult verified =
                (PKIXCertPathValidatorResult) CertPathValidator
                        .getInstance("PKIX").validate(
                                path.getCertPath(), validationParams);
        System.out.println("validated with trusted root: " +
                verified.getTrustAnchor().getTrustedCert().equals(root));

        PKIXBuilderParameters wrongAnchors =
                new PKIXBuilderParameters(
                        Set.of(new TrustAnchor(otherRoot, null)), target);
        wrongAnchors.setRevocationEnabled(false); // Lab only!
        wrongAnchors.addCertStore(issuerCandidates);
        boolean rejected = false;
        try {
            CertPathBuilder.getInstance("PKIX").build(wrongAnchors);
        } catch (CertPathBuilderException expected) {
            rejected = true;
        }
        System.out.println("untrusted root rejected: " + rejected);
    }
}
```

### 6.1 Biên dịch và chạy

Khi các tệp `lab-root.pem`, `lab-intermediate.pem`, `lab-service.pem`, `lab-untrusted.pem` nằm trong thư mục hiện tại:

```text
javac --release 21 -encoding UTF-8 PkixPathDemo.java
java PkixPathDemo .
```

Stdout từ fixture đã tạo:

```text
path entries: 2
root omitted from path: true
path starts at leaf: true
validated with trusted root: true
untrusted root rejected: true
```

Đầu ra này đã được kiểm tra với compiler hỗ trợ `--release 21` và JDK provider PKIX thông dụng. Việc tạo khóa dùng `SecureRandom` và `keytool` nên nội dung certificate/fingerprint sẽ khác giữa các lần thử, nhưng các **boolean và path length** nói trên ổn định nếu fixture và chính sách được giữ đúng.

### 6.2 Diễn giải từng bước, từng dòng output

1. `CertificateFactory.getInstance("X.509")` parse PEM certificates. **Đọc được một certificate không có nghĩa là certificate đã được xác minh.** Đừng import root do attacker cung cấp làm trust anchor.
2. `new TrustAnchor(root, null)` khai báo root đáng tin **theo giả định trust được cấp trước trong lab**. Tham số `null` là name constraints bổ sung của trust anchor (không phải mật khẩu hay khóa).
3. `X509CertSelector` chọn chính leaf, yêu cầu end-entity và EKU serverAuth; đây là **target constraint** cho path builder.
4. `CollectionCertStoreParameters(List.of(leaf, intermediate))` cung cấp certificates **để tìm path**, không nâng mức tin cậy. Root không cần nằm trong `CertStore` vì đã được truyền riêng qua `anchors`.
5. `buildParams.setMaxPathLength(1)` chấp nhận một intermediate CA. Extension `BC.pathLen` của CA cũng được kiểm theo PKIX.
6. `build(...)` trả `PKIXCertPathBuilderResult`, trong đó `getCertPath().getCertificates()` gồm **leaf trước, intermediate sau**. Vì root là trust anchor ngoài path, `path entries: 2` và `root omitted from path: true`.
7. `new PKIXParameters(anchors)` và `validate(path, params)` xác minh **cert path đã có**, không tìm một path mới. `verified.getTrustAnchor()` trỏ về root được chấp nhận.
8. Thay trust anchors bằng `otherRoot` không có quan hệ cấp phát với leaf/intermediate làm builder không tìm được path hợp lệ. Chương trình bắt `CertPathBuilderException` và in `untrusted root rejected: true`.
9. `setRevocationEnabled(false)` xuất hiện ở cả build và validate **chỉ để lab offline có kết quả tái lập**. Với hệ thống thật, lập chính sách revocation và nguồn CRL/OCSP đúng profile; không sao chép cấu hình này mà thiếu phân tích bảo mật.

### 6.3 Một điểm có thể gây hiểu sai: root PEM nằm cạnh leaf PEM

Trong demo, chúng ta lưu cả bốn PEM cùng thư mục để thuận tiện. **Vị trí vật lý cùng thư mục không làm root đáng tin**; đoạn code cố ý chỉ định `lab-root.pem` làm trust anchor theo giả định root đó được cấp một cách đáng tin trước thời điểm nhận message. Nếu attacker điều khiển được thư mục rồi thay đồng thời `lab-root.pem` và `lab-service.pem`, demo sẽ có thể chấp nhận chuỗi mới do attacker tạo. Production cần truststore/kho cấu hình bảo vệ quyền ghi, pinning/chính sách quản trị root và đường dẫn filesystem phù hợp.

Không lưu khóa riêng CA trong kho mà ứng dụng validation có quyền đọc. Demo Java chỉ nhận PEM public certificates; tệp `lab.p12` dùng tạo chứng thư và nên xóa sau lab.

## 7. Thử nghiệm thất bại: biết lỗi nằm ở tầng nào

Một chương trình PKIX thực tế sẽ gặp nhiều lỗi không đơn thuần là “signature sai”.

| Thay đổi trong lab | Điều bạn nên quan sát | Giải thích |
| --- | --- | --- |
| Chỉ cấp `otherRoot` làm trust anchor | `CertPathBuilderException` | Không có chuỗi từ leaf đến root được tin |
| Xóa intermediate khỏi `CertStore` (không có nguồn khác) | Thông thường không build được | Thiếu certificate issuer để nối lên root |
| Giữ root, thay leaf bằng certificate khác | Có thể selector mismatch hoặc path khác | Target constraints xác định certificate mong muốn |
| Đặt `setMaxPathLength(0)` khi cần một intermediate | Builder từ chối path này | Policy không cho CA trung gian cần thiết |
| Dùng cert leaf hết hạn theo thời điểm validation | `CertPathValidatorException` hoặc builder failure | Date window không còn hợp lệ |
| Intermediate thiếu `CA:TRUE` / `keyCertSign` | Path không hợp lệ | BasicConstraints/KeyUsage không cho ký certificate |
| Intermediate bị ràng buộc `pathLen:0` nhưng thêm CA bên dưới | Path không hợp lệ | Vượt CA path length của issuer |
| Sai EKU ở selector | Không chọn được target | Selector target không khớp |
| Không có dịch vụ CRL/OCSP nhưng bật revocation | Có thể fail do không xác minh được trạng thái | Phải cấu hình chính sách/source, không đơn giản tắt tùy tiện |
| PKIX pass nhưng URL hostname khác SAN | PKIX có thể pass, **TLS identity check phải reject** | Path trust khác mục đích endpoint |
| CA root hợp lệ nhưng policy ứng dụng không chấp nhận loại client | PKIX có thể pass, ứng dụng phải reject | Chain validation không đồng nghĩa authorization |

Bước kiểm lỗi có hệ thống:

1. **Parsing:** kiểm đúng loại DER/PEM, certificate có đọc được không, path có trùng/thiếu phần tử?
2. **Target selector:** dùng `match(...)`/kiểm constraints để biết leaf có phù hợp điều kiện không.
3. **Candidates:** intermediate có được cung cấp/truy xuất trong `CertStore` không, có đúng issuer subject/key identifier không?
4. **Trust anchors:** root được lấy từ **nguồn quản trị đáng tin** hay do peer gửi? Có đúng root được hệ thống cho phép không?
5. **PKIX constraints:** validity date, CA basic constraints, signature algorithm, key size/algorithm constraints, policies, name constraints, revocation.
6. **Identity/application:** hostname/SAN, EKU/service role, authorization, pinning nếu dùng.
7. **Vận hành:** JDK/provider và crypto policy, network access, timeouts, chênh lệch đồng hồ, CRL/OCSP cache.

**Không trả toàn bộ diagnostic certificate chain/CA names/stack traces qua public HTTP API**. Ghi log có cấu trúc ở phía nội bộ với mức nhạy cảm hợp lý, cung cấp cho client một lỗi xác thực an toàn, và không tự động fallback sang “accept all” khi validate thất bại.

## 8. Những constructor và API dễ nhầm

### 8.1 `PKIXParameters` từ `Set<TrustAnchor>` hay `KeyStore`

Từ một set:

```java
PKIXParameters validationParams =
        new PKIXParameters(Set.of(new TrustAnchor(root, null)));
```

Từ truststore đã load (đoạn minh họa, `trustStore` phải có TrustedCertificateEntry):

```java
PKIXParameters validationParams = new PKIXParameters(trustStore);
```

Hai cách đều tạo policy trust anchors. **Không** đưa một identity keystore chứa private keys của dịch vụ vào và mặc định rằng constructor sẽ lấy certificate chain của mọi private-key alias làm root tin cậy.

### 8.2 `PKIXBuilderParameters` cần target

```java
PKIXBuilderParameters buildParams =
        new PKIXBuilderParameters(anchors, target);
```

Constructor này bắt buộc target constraints thích hợp. Với selector trống và nguồn candidates rộng, builder có thể chọn không đúng đối tượng mong muốn; ứng dụng phải chỉ rõ **ai/cert nào cần chứng minh**. Với constructor từ `KeyStore`, trust anchors cũng đến từ trusted entries như đã trình bày.

### 8.3 `X509CertSelector` chỉ lọc certificate

```java
X509CertSelector selector = new X509CertSelector();
selector.setSubject(leaf.getSubjectX500Principal());
selector.setBasicConstraints(-2);
boolean matches = selector.match(leaf);
```

Đoạn này **không độc lập**: cần biến `leaf` đã được đọc. `matches == true` chỉ xác nhận leaf đáp ứng selector; certificate tự ký có cùng subject cũng có thể match nếu selector không ràng buộc khóa/chứng thư. Chỉ PKIX path + trust policy mới quyết định certificate có nằm trên đường dẫn tin cậy không.

### 8.4 `CertPath` có sẵn khác `CertPathBuilder`

Khi đối tác gửi sẵn chain, có thể tạo `CertPath` bằng `CertificateFactory.generateCertPath(List<? extends Certificate>)` theo thứ tự phù hợp, rồi gọi `CertPathValidator` với `PKIXParameters`. Nếu không có chain hoàn chỉnh, `CertPathBuilder` kết hợp candidate `CertStore`/trust anchors để **tìm** một path thích hợp.

Không đưa root vào path rồi kỳ vọng số lượng phần tử luôn đúng như sơ đồ. Hợp đồng PKIX giả định trust anchor **tách khỏi cert path**, và builder demo thể hiện rõ điều đó.

## 9. Tăng chất lượng chính sách PKIX ngoài bài lab

### 9.1 Trust anchor governance

Quản lý trust anchors như một thành phần an ninh trọng yếu: ai được thêm/xóa CA, cập nhật truststore ra sao, audit, key strength, thời hạn sử dụng, revocation/compromise response, policy theo môi trường test/staging/prod và rollover. **Một CA được trust thường có thể ký nhiều leaf** trong phạm vi name constraints/policies, nên quyền thêm root có tác động rất rộng.

Nếu ứng dụng chỉ cần tin một service cụ thể, có thể cân nhắc pinning theo quy trình quản trị/rotate phù hợp, hoặc private PKI có name constraints và issuance policy rõ. Không tự lấy root từ handshake phía ngoài rồi biến nó thành trust anchor.

### 9.2 Revocation, CRL và OCSP

PKIX path validation thông thường có thể áp dụng revocation checking. `PKIXParameters.setRevocationEnabled(true)` là cờ quan trọng, nhưng **không bảo đảm** mọi thứ sẽ đúng nếu CRL/OCSP provider/network chưa được cấu hình. Có thể cần `PKIXRevocationChecker` từ validator/provider hoặc trust manager chính thức, `CertStore` chứa CRL, OCSP responder, cache, timeout và chính sách **hard-fail/soft-fail** theo threat model. Không tự xem cert là “good” chỉ vì gọi API mà không có nguồn trạng thái thu hồi.

Bật/tắt revocation là quyết định hệ thống, không phải mẹo xử lý exception. Nếu chỉ học offline, hãy ghi chú tường minh “LAB ONLY” như trong code. Khi triển khai, kiểm tra cách JSSE stack đang sử dụng CRL/OCSP và các system/security properties liên quan cho đúng phiên bản JDK.

### 9.3 Application identity và mục đích sử dụng

Ngay cả path tin cậy cũng phải được ràng buộc với đích:

- **HTTPS:** SAN DNS/IP phải khớp hostname, kiểm tra protocol version và EKU serverAuth đúng policy.
- **Mutual TLS:** kiểm tra certificate phía client với trust policy phù hợp, sau đó ánh xạ certificate identity đến principal/tenant và xác minh quyền thực hiện request.
- **Code signing:** mục đích key usage/EKU/signing policy khác TLS; không coi một leaf chỉ có `serverAuth` là authorized code signer.
- **Document signing:** phải biết signer/đơn vị cần tin, chính sách thời gian và chứng cứ ký; chỉ path validation vẫn không bảo đảm thẩm quyền nghiệp vụ.

Trong ứng dụng Java thực, nên để **JSSE/HTTPS** thực hiện chứng thực TLS theo chuẩn, với `SSLContext`, `TrustManagerFactory`, `PKIX` trust manager và hostname verification phù hợp; không thay TrustManager bằng loại “accept any certificate” hoặc cố kiểm CN thủ công.

### 9.4 Algorithm constraints và đồng hồ

Chữ ký RSA-3072/SHA-256 trong lab được chọn để tương thích JDK21 và mục tiêu học tập. Hệ thống thật phải áp dụng algorithm constraints của profile, cập nhật khi thuật toán/khóa bị coi là yếu, xử lý certificate quá hạn/not-yet-valid và đồng bộ đồng hồ. Không cố “sửa” lỗi bằng cách chấp nhận bất kỳ chữ ký hay tắt certificate validity checks.

`setDate` nên được dùng có mục đích (ví dụ test thời điểm hoặc profile xác minh chữ ký lịch sử được chuẩn hóa); không chuyển thời gian lùi tùy ý chỉ để vượt chứng thư hết hạn.

### 9.5 PKIX path builder không phải giao thức tin cậy đầy đủ

`CertPathBuilder` và `CertPathValidator` làm đúng nhiệm vụ PKIX, nhưng chúng không tự xử lý bảo mật kênh TLS, chứng minh possession of private key trong handshake, chống replay yêu cầu ứng dụng, authorization, thông điệp nghiệp vụ hay idempotency. Các lớp phải phối hợp:

```text
Truststore / managed CA roots
              |
              v
X509CertSelector + CertStore candidates
              |
              v
PKIXBuilderParameters ----> CertPathBuilder("PKIX")
              |                        |
              |                        v
              |                 path + trust anchor
              |                        |
              v                        v
 PKIXParameters ------------> CertPathValidator("PKIX")
                                       |
                                       v
               Hostname/SAN + EKU + endpoint/application policy
                                       |
                                       v
                       Authenticated channel / authorization
```

Trong HTTPS client thông thường, những bước này thường được framework và JSSE stack tổ chức thay cho ứng dụng; bạn không cần tự gọi `CertPathBuilder` trên mọi request. Dù vậy, hiểu ba lớp này giúp rà soát việc **chọn CA, cấu hình truststore, xử lý certificate-chain failure** và tránh lỗ hổng trust bypass.

## 10. Những sai lầm phổ biến cần nhớ

1. **“Có ba chứng thư trong chain là đủ tin cậy.”** Sai: chain đầy đủ nhưng root không nằm trong truststore được bảo vệ vẫn có thể do attacker tự ký.
2. **“Lấy root từ gói bên kia rồi `new TrustAnchor(root,null)`.”** Sai: tự cho phép attacker tạo root đáng tin.
3. **“`CertStore` là truststore.”** Sai: `CertStore` là nguồn candidate certificates/CRLs; trust anchors là policy độc lập.
4. **“`X509CertSelector` trả `true` nghĩa là PKIX đã thành công.”** Sai: match không xác minh path, trust, validity, revocation toàn diện.
5. **“`PKIXParameters` và `PKIXBuilderParameters` là như nhau.”** Sai: builder parameters thêm target và path-length policy cho xây path; validator dùng path đã có.
6. **“CertPath có leaf, intermediate, root.”** Không mặc định: trust anchor được truyền riêng, path demo chỉ có leaf và intermediate.
7. **“Giảm `setMaxPathLength` để giảm số certificate tùy ý.”** Sai: nó đếm **intermediate CA không self-issued**, không chỉ đơn giản đếm mọi cert.
8. **“Chỉ cần `setRevocationEnabled(true)` là OCSP/CRL luôn hoạt động tốt.”** Sai: phải cấu hình cơ chế/sources và failure policy.
9. **“Tắt revocation vì không có mạng thì production an toàn.”** Sai nếu không có quyết định bảo mật tương ứng; ví dụ tắt chỉ phục vụ lab.
10. **“Chứng thư do trusted CA ký chắc chắn thuộc hostname tôi định gọi.”** Sai: cần endpoint identity/SAN verification.
11. **“Mọi certificate có subject CN giống nhau đều cùng chủ thể.”** Sai: subject có thể do nhiều CA/attacker tạo; cần trust và policy.
12. **“Certificate root tự ký có thể validate bằng public key của chính nó thì đã tin.”** Sai: sự kiện self-signature đúng không nói gì về nguồn gốc root.
13. **“Vì path builder thành công nên user đã có quyền gọi API.”** Sai: quyền ứng dụng cần ánh xạ principal và authorization.
14. **“Bài lab chứng minh provider PKIX chạy trên mọi JDK/HSM.”** Sai: phải kiểm thử trên provider/runtime/chính sách và cấu hình CA/CRL/OCSP thật.

## 11. Bài tập thực hành

1. **Kiểm tra target:** bỏ `setCertificate(leaf)` và chỉ dùng subject + EKU + basic constraints. Điều gì xảy ra nếu `CertStore` có hai leaf cùng subject/EKU? Bạn phải bổ sung constraint nào hoặc chọn xác thực danh tính bằng cách nào?
2. **Không có intermediate:** loại `lab-intermediate.pem` khỏi collection `CertStore`. Với không có nguồn issuer khác, builder trả gì? Phân biệt lỗi thiếu issuer với root không tin.
3. **Sai trust anchor:** thay root được trust thành `lab-untrusted.pem`; chạy code và giải thích vì sao chữ ký trên leaf của intermediate vẫn đúng mà builder thất bại.
4. **Giới hạn path length:** đổi `setMaxPathLength(1)` thành `setMaxPathLength(0)`; path leaf→intermediate→root có được phép không? Nếu bạn có leaf ký trực tiếp bởi root thì kết quả khác thế nào?
5. **Certificate validity:** in `getNotBefore()/getNotAfter()` của leaf, dùng `setDate(...)` trong một bài test với thời điểm vượt NotAfter, kiểm chứng trạng thái path. Không thao tác đồng hồ máy production để tạo “bằng chứng” hết hạn.
6. **EKU:** đổi selector từ OID serverAuth sang OID clientAuth `1.3.6.1.5.5.7.3.2`; vì sao target leaf lab không còn khớp?
7. **Subject vs SAN:** xây path thành công cho `service.example.test`, sau đó giả sử HTTP client gọi `api.example.test`. PKIX builder có tự trả lỗi hostname mismatch không? Bạn cấu hình endpoint identification ở tầng nào?
8. **Truststore:** thiết kế một PKCS12 riêng chỉ chứa `TrustedCertificateEntry` cho root, đọc bằng `KeyStore` rồi tạo `new PKIXParameters(trustStore)`. Vì sao đưa private-key keystore do peer gửi vào constructor là sai?
9. **Revocation:** phác thảo quy trình CRL/OCSP provider được cấu hình cho hệ thống thật; chọn hard-fail hoặc soft-fail theo chính sách, và mô tả kết quả mong muốn khi responder không phản hồi.
10. **Điều tra lỗi:** một container Java gặp `CertPathBuilderException`, còn laptop xây path thành công. Liệt kê nguyên nhân khả dĩ liên quan truststore, certificates/candidates, JDK/provider, clock, revocation/network và hostname verification, không vội tắt PKIX.
11. **Ứng dụng mTLS:** giải thích riêng ba việc: validate client certificate path; ánh xạ certificate sang identity; quyết định identity có được gọi một endpoint nhạy cảm không.
12. **Chứng thư CA:** vì sao intermediate `pathLen:0` vẫn được ký leaf nhưng không được tạo một intermediate CA cấp dưới mới theo policy? Phân biệt extension `pathLenConstraint` với `setMaxPathLength`.

**Tiêu chí hoàn thành:** bạn chỉ ra được **trust anchor đến từ đâu**, leaf được chọn bằng những ràng buộc gì, intermediate nằm ở `CertStore` nào, builder giới hạn path thế nào, validator dùng chính sách gì, revocation/hostname được kiểm ra sao và vì sao **một path được xây dựng đúng không đồng nghĩa mọi mục tiêu bảo mật của ứng dụng đều được đáp ứng**.

## Tài liệu tham khảo chính thức

- [Java SE 21 — `PKIXParameters`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/PKIXParameters.html): cấu hình trust anchors, validation date, revocation, policies, CertStore.
- [Java SE 21 — `PKIXBuilderParameters`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/PKIXBuilderParameters.html): selector, giới hạn intermediate CA, constructor từ truststore/anchors.
- [Java SE 21 — `X509CertSelector`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/X509CertSelector.html): subject/issuer/EKU/basic constraints/key usage/SAN selectors.
- [Java SE 21 — `TrustAnchor`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/TrustAnchor.html): trusted CA và name constraints.
- [Java SE 21 — `CertStore`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/CertStore.html): collection certificates/CRLs; không đồng nghĩa truststore.
- [Java SE 21 — `CertPathBuilder`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/CertPathBuilder.html), [`CertPathValidator`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/CertPathValidator.html): xây và kiểm path.
- [Java SE 21 — `CertificateFactory`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/CertificateFactory.html): parse PEM/DER X.509.
- [Java SE 21 — `PKIXRevocationChecker`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/PKIXRevocationChecker.html): hỗ trợ revocation checking theo provider.
- [JDK 21 — `keytool`](https://docs.oracle.com/en/java/javase/21/docs/specs/man/keytool.html): `-genkeypair -signer`, export certificate, PKCS12, extensions.
- [RFC 5280](https://www.rfc-editor.org/rfc/rfc5280): X.509 PKI certificate and CRL profile, certificate path validation.
- [Java 21 JSSE Reference Guide](https://docs.oracle.com/en/java/javase/21/security/java-secure-socket-extension-jsse-reference-guide.html): TLS, trust managers và endpoint identity.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cert-path-004">14.4 Thu thập chứng thư hoặc CRL qua CertStore</a>

<details>
<summary>Xem chi tiết</summary>

# 14.4 Thu thập chứng thư hoặc CRL qua CertStore

Trong hệ thống PKI, **có một certificate hợp lệ về cú pháp chưa đủ để tin người gửi**, và **có một danh sách thu hồi (Certificate Revocation List — CRL) chưa đủ để kết luận certificate không bị thu hồi**. Khi xây dựng hoặc kiểm tra **đường dẫn tin cậy (certification path)**, ứng dụng Java thường phải tìm thêm certificate của CA trung gian và CRL do các CA phát hành. API chuẩn **`java.security.cert.CertStore`** giải quyết **bước tìm/thu thập các ứng viên** từ một kho dữ liệu.

Bài học này trình bày cách dùng `CertStore` và các selector `X509CertSelector`, `X509CRLSelector` trong Java 17+; cách tạo kho `"Collection"` từ certificate/CRL đã parse; cách tra cứu kiểu `"LDAP"`; cách bổ sung `CertStore` vào tham số **PKIX**; và ranh giới quan trọng giữa **tìm được dữ liệu**, **xác minh chữ ký CRL**, **xác thực chuỗi CA**, và **kiểm tra trạng thái thu hồi**. Có chương trình Java độc lập và lệnh `keytool` tạo fixture thử nghiệm để học viên tái hiện kết quả mà không cần kết nối máy chủ ngoài.

> **Nguyên tắc gốc:** `CertStore` cung cấp các **candidate certificates/CRLs**, không phải danh sách các chứng thư tự động đáng tin. **Trust anchor**, chính sách chứng thư, đường dẫn PKIX và kiểm tra thu hồi là những bước riêng phải được thực thi đúng.

## 1. CertStore nằm ở đâu trong quá trình xác minh X.509?

Một chuỗi chứng thư TLS điển hình có thể gồm:

```text
Leaf certificate (server.example)
       |
       | signed by
       v
Intermediate CA certificate
       |
       | signed by
       v
Root CA (được cấu hình là trust anchor)
```

Khi ứng dụng chỉ nhận certificate của leaf, nó có thể **thiếu intermediate certificate**. Thêm vào đó, chính sách bảo mật có thể yêu cầu tra cứu CRL để xác định certificate có bị CA thu hồi trước khi hết hạn hay không.

`CertStore` cung cấp một giao diện đọc:

```text
                  +---------------------------+
                  | CertStore (Collection,    |
                  | LDAP, provider-specific)  |
                  +-------------+-------------+
                                |
                 +--------------+--------------+
                 |                             |
      getCertificates(selector)        getCRLs(selector)
                 |                             |
       candidate certificates              candidate CRLs
                 |                             |
                 +--------------+--------------+
                                |
                PKIX CertPathBuilder / Validator
                   + trust anchors / policy
                   + signature/time checks
                   + revocation checking
                                |
                    ACCEPT / REJECT theo policy
```

**Phân biệt các lớp:**

| Khái niệm | Vai trò | Có tự tạo niềm tin không? |
| --- | --- | --- |
| `KeyStore` | Lưu/truy cập keys, private keys và trusted certificates theo provider | Một **truststore được cấu hình** có thể chứa trust anchors; không phải mọi KeyStore đều là truststore |
| `CertStore` | Truy vấn certificate và CRL từ repository | **Không**; kết quả thu thập là dữ liệu cần kiểm chứng |
| `CertificateFactory` | Parse PEM/DER thành `Certificate`, `CRL` hoặc `CertPath` | **Không**; parse được không đồng nghĩa tin cậy |
| `X509CertSelector` | Đặt tiêu chí lọc certificate X.509 | **Không**; lọc theo thuộc tính, không thay chain validation |
| `X509CRLSelector` | Đặt tiêu chí lọc CRL X.509 | **Không**; issuer/time match không chứng minh chữ ký |
| `TrustAnchor` | Root CA certificate/public key được ứng dụng chấp thuận theo trust policy | **Có vai trò nền tảng tin cậy** nếu được cấp phát đúng |
| `CertPathBuilder("PKIX")` | Tìm một chứng thực chain đáp ứng tham số PKIX | Xây đường dẫn dựa trên trust anchors và chính sách |
| `CertPathValidator("PKIX")` | Kiểm chứng một `CertPath` đã có | Xác thực đường dẫn, với cấu hình thu hồi phù hợp |
| `PKIXRevocationChecker` | Điều khiển cách kiểm tra thu hồi theo PKIX | Kết hợp CRL/OCSP, network/policy, không phải một repository |

`CertStore` là **cơ chế đọc** ứng viên. Không dùng `getCertificates(...)` thay cho `CertPathValidator.validate(...)` hoặc thay cho TLS hostname verification.

### 1.1. Tại sao “thu thập” khác “xác minh”?

Một kho LDAP hoặc file được cấu hình có thể chứa certificate **hết hạn**, certificate của CA không còn được tin, CRL cũ hoặc file bị sửa. Selector có thể lọc đúng tên subject/issuer mà **vẫn trả về certificate của kẻ tấn công có tên trùng**. Vì vậy:

- `getCertificates` chỉ xác định **ứng viên phù hợp tiêu chí**, không nói “người này là ai” ở mức tin cậy PKIX.
- `getCRLs` chỉ lấy **danh sách thu hồi ứng viên**, không xác nhận CRL thực sự do CA tin cậy ký.
- `X509CRL.isRevoked(cert)` xét một CRL đã có, không đủ để chứng minh certificate hiện tại hợp lệ cho mọi policy.
- Chỉ hệ thống **validation** với trust anchors, thời gian, thuật toán, key usage, name constraints, chính sách và revocation rules mới đưa ra kết luận tin cậy phù hợp.

## 2. API nền tảng của `java.security.cert.CertStore`

`CertStore` là một **engine class dựa trên Security Provider**. Phương thức `getInstance(...)` chọn type và cấu hình repository.

```java
CertStore store = CertStore.getInstance("Collection", parameters);
Collection<? extends Certificate> certs = store.getCertificates(certSelector);
Collection<? extends CRL> crls = store.getCRLs(crlSelector);
```

Đây là trích đoạn minh họa, không phải chương trình độc lập: các biến `parameters`, `certSelector`, `crlSelector` phải được khởi tạo; chương trình hoàn chỉnh nằm ở mục 6.

### 2.1. Các kiểu dữ liệu và phương thức chính

| API | Ý nghĩa |
| --- | --- |
| `CertStore.getInstance(String type, CertStoreParameters params)` | Tạo repository theo type/provider, với tham số khởi tạo |
| `CertStore.getInstance(type, params, provider)` | Chọn một provider cụ thể khi cần |
| `CertStore.getType()` | Type đã dùng, ví dụ `Collection` |
| `CertStore.getProvider()` | Provider triển khai repository |
| `CertStore.getCertificates(CertSelector)` | Trả collection candidate certificate khớp selector |
| `CertStore.getCRLs(CRLSelector)` | Trả collection candidate CRL khớp selector |
| `CollectionCertStoreParameters` | Chỉ định collection trong bộ nhớ làm repository |
| `LDAPCertStoreParameters` | Chỉ định LDAP host/port cho repository tương ứng |
| `CertStoreException` | Lỗi lúc truy vấn repository, bao gồm sự cố nguồn từ xa |

**Ý nghĩa của kết quả trả về:**

- Nếu không có item phù hợp, API trả **collection rỗng**, **không** trả `null` theo contract.
- `CertStoreException` thể hiện **lỗi thu thập**, khác hoàn toàn “đã thu thập thành công và không có item khớp”.
- Với repository từ xa, trả về collection có thể **không đầy đủ** khi provider không thể duyệt toàn bộ nguồn; các selector là cả **tiêu chí lọc** và **gợi ý truy vấn**.
- `CertStore.getInstance` có thể báo `NoSuchAlgorithmException` nếu type không hỗ trợ hoặc `InvalidAlgorithmParameterException` nếu cấu hình sai.
- `getCertificates(null)`/`getCRLs(null)` có thể được hỗ trợ để yêu cầu tất cả bản ghi, **nhưng không giả định** tất cả repository — nhất là LDAP — cho phép quét toàn bộ khi selector không đủ cụ thể.

### 2.2. Vì sao không dùng `KeyStore` để tìm mọi intermediate/CRL?

`KeyStore` thường truy cập một **tập dữ liệu được quản lý cho ứng dụng**: trusted root, private key, certificate chain đã nhập. Nó không phải API chung để truy vấn một hệ thống LDAP hoặc một repository có thể rất lớn với tiêu chí subject/issuer.

`CertStore` phù hợp khi ứng dụng đã có **nhiều certificate và CRL trong memory**, cần cung cấp danh sách đó cho `CertPathBuilder`, hoặc cần provider truy vấn một nguồn ngoài theo selector. Không dùng CertStore như một nơi để lưu private key; kiểu `Certificate` và `CRL` ở đây là tài liệu PKI công khai/có thể phân phối.

## 3. Lấy certificate bằng `X509CertSelector`

`X509CertSelector` triển khai `CertSelector` và cung cấp nhiều tiêu chí để chọn certificate X.509. Các tiêu chí được đặt đồng thời thường có nghĩa **certificate phải thỏa tất cả điều kiện đã cấu hình**.

### 3.1. Các tiêu chí thường gặp

- `setSubject(X500Principal)`: chọn certificate có **Subject DN** cụ thể.
- `setIssuer(X500Principal)`: chọn certificate do **Issuer DN** cụ thể phát hành.
- `setCertificate(X509Certificate)`: chọn certificate tương ứng đối tượng/chứng thư đã chỉ định theo contract.
- `setSerialNumber(BigInteger)`: chọn số seri certificate.
- `setCertificateValid(Date)`: chọn certificate có thời hạn hiệu lực (`notBefore`, `notAfter`) bao trùm thời điểm chỉ định.
- `setBasicConstraints(int)`: dùng để phân biệt CA và end-entity; **`setBasicConstraints(0)`** trong ví dụ chọn CA có basic constraints phù hợp.
- `setKeyUsage(boolean[])`: lọc các bit KeyUsage theo yêu cầu.
- `setSubjectAlternativeNames(...)` hoặc `addSubjectAlternativeName(...)`: lọc theo SAN với cấu trúc/type phù hợp.

Ví dụ lọc certificate CA với subject được biết:

```java
// Minh họa, chưa phải chương trình độc lập:
X509CertSelector selector = new X509CertSelector();
selector.setSubject(new X500Principal("CN=Training Demo CA,O=Learning"));
selector.setBasicConstraints(0);
Collection<? extends Certificate> matches = store.getCertificates(selector);
```

Phần lớn ứng dụng **không nên tự so tên DN thành string thủ công**, vì thứ tự, encoding, escaping của tên X.500 có thể khác. Dùng `X500Principal` và selector/API chuẩn giúp giảm nhầm lẫn.

### 3.2. `setSubject` khác `setIssuer`

Giả sử:

```text
Leaf:         Subject = CN=api.example       Issuer = CN=Intermediate CA
Intermediate: Subject = CN=Intermediate CA    Issuer = CN=Root CA
Root:         Subject = CN=Root CA            Issuer = CN=Root CA (self-signed)
```

Để **tìm intermediate ký leaf**, bạn thường cần một certificate **có Subject bằng Issuer của leaf**, rồi vẫn phải xác thực chữ ký leaf bằng public key của intermediate, kiểm tra CA/key usage và xây đường đến trust anchor. Đặt `setIssuer(leaf.getIssuerX500Principal())` sẽ tìm certificate **do intermediate phát hành**, không nhất thiết là intermediate certificate đang cần.

Một cách lấy ứng viên issuer:

```java
X509CertSelector findIssuer = new X509CertSelector();
findIssuer.setSubject(leaf.getIssuerX500Principal());
findIssuer.setBasicConstraints(0); // Chi tim ung vien CA
Collection<? extends Certificate> issuerCandidates =
        store.getCertificates(findIssuer);
```

Đây là trích đoạn, vì `leaf` và `store` phải tồn tại trước. **Trùng DN không chứng minh là cùng issuer key**; PKIX builder/validator còn dùng chữ ký, AuthorityKeyIdentifier, SubjectKeyIdentifier, policy, name constraints và trust anchor để đánh giá.

### 3.3. Tránh hiểu lầm khi có kết quả

Nếu selector trả **một** certificate, nó vẫn có thể không tin cậy. Nếu trả **nhiều** certificate, không chọn ngẫu nhiên phần tử đầu. Nếu trả **rỗng**, có thể là do repository thiếu intermediate hoặc selector quá chặt; không tự động chuyển sang “bỏ kiểm tra certificate chain”.

Đặc biệt, `setCertificateValid(new Date())` chỉ lọc theo **ngày hiệu lực** của certificate tại thời điểm hiện tại; nó **không kiểm tra revocation, hostname, EKU, policy hoặc chain**. Khi đang kiểm tra historical signatures, mốc thời gian hợp lệ có thể không phải “bây giờ” và phải theo policy nghiệp vụ.

## 4. Lấy CRL bằng `X509CRLSelector`

**CRL (Certificate Revocation List)** là cấu trúc X.509 do CA hoặc thực thể được ủy quyền phù hợp phát hành, chứa thông tin về những chứng thư bị thu hồi. Nó có metadata như:

- **Issuer** của CRL;
- **thisUpdate** — mốc cập nhật CRL;
- **nextUpdate** — mốc dự kiến danh sách mới;
- **CRL entries** có serial number, revocation date và có thể có reason;
- **signature**, signature algorithm và các extension như CRL Number, Authority Key Identifier, Issuing Distribution Point.

Không phải mọi CRL áp dụng cho mọi certificate cùng tên issuer: phải xét **scope**, distribution point, certificate type, reason coverage, indirect CRL và delta CRL nếu giao thức hỗ trợ chúng.

### 4.1. Các tiêu chí cơ bản

`X509CRLSelector` triển khai `CRLSelector`. Các phương thức có ích:

| API | Vai trò |
| --- | --- |
| `addIssuer(X500Principal)` | Chỉ chọn CRL có issuer DN phù hợp |
| `setIssuers(Collection<X500Principal>)` | Chọn một tập issuer DN được chấp nhận |
| `setDateAndTime(Date)` | Chỉ nhận CRL có khoảng `thisUpdate ≤ time < nextUpdate` |
| `setCertificateChecking(X509Certificate)` | Cung cấp **gợi ý** về certificate cần kiểm tra, hỗ trợ repository định vị CRL |
| `setMinCRLNumber(BigInteger)` | Chọn CRL Number không thấp hơn ngưỡng (nếu extension phù hợp) |
| `setMaxCRLNumber(BigInteger)` | Chọn CRL Number không vượt ngưỡng |
| `match(CRL)` | Kiểm tra CRL có thỏa tiêu chí filter không |

Để tìm CRL do CA cụ thể phát hành:

```java
X509CRLSelector selector = new X509CRLSelector();
selector.addIssuer(issuerCert.getSubjectX500Principal());
selector.setCertificateChecking(certBeingChecked);
selector.setDateAndTime(new Date());

Collection<? extends CRL> candidates = store.getCRLs(selector);
```

Đây là trích đoạn minh họa, cần khởi tạo `issuerCert`, `certBeingChecked`, `store` ở code thực.

**Điểm dễ nhầm:** `setCertificateChecking(cert)` **không phải điều kiện lọc riêng để hỏi “cert đã bị thu hồi?”**. Nó là **thông tin hỗ trợ tìm CRL**; nếu chỉ có `setCertificateChecking` mà không có issuer/đặc tả đủ rõ, provider có thể không truy vấn được mọi nguồn. Thực tế nên thêm issuer và thời gian phù hợp theo mục tiêu kiểm tra.

### 4.2. Tại sao `setDateAndTime` rất quan trọng?

Một CRL đã quá `nextUpdate` có thể **không còn đủ mới** để ra quyết định thu hồi tại thời điểm hiện tại. `X509CRLSelector.setDateAndTime(now)` yêu cầu:

```text
thisUpdate <= now < nextUpdate
```

Theo contract của selector X.509, **nếu CRL không có nextUpdate, tiêu chí thời gian này không match**. Bởi vậy, “không tìm thấy CRL” có thể là do CRL cũ, thiếu mốc hoặc time skew; không được suy luận “certificate không bị thu hồi”.

Hệ thống kiểm tra PKIX thực tế cần policy về clock skew, coverage, CRL freshness và nguồn thay thế (OCSP), chứ không chỉ dùng một phép chọn theo date.

### 4.3. `X509CRL.isRevoked` là gì và chưa đủ ở đâu?

Sau khi có một `X509CRL` cụ thể, `crl.isRevoked(cert)` hỏi liệu certificate đó được liệt kê là revoked trong CRL **đang cầm** hay không.

Nếu trả `false`, có ít nhất bốn khả năng:

1. Certificate thật sự chưa bị thu hồi và CRL phù hợp, còn hiệu lực đã được kiểm tra đúng;
2. CRL **không áp dụng** cho certificate đó;
3. CRL **cũ**, thiếu thông tin mới hoặc là partial/delta CRL;
4. CRL **chưa được xác minh chữ ký và chain trust** nên dữ liệu có thể bị giả mạo.

Vì vậy, **`false` không tương đương “certificate đáng tin cậy”**. Ngoài CRL còn phải xét validity, chain, BasicConstraints, KeyUsage, EKU, hostname/name constraints, thuật toán và trust policy.

### 4.4. Xác minh chữ ký CRL

`X509CRL.verify(PublicKey)` xác minh chữ ký của **một CRL** dưới public key được cung cấp. Nhưng nếu public key lấy từ một certificate chưa được xác minh, một chữ ký “đúng” vẫn không đủ để tin CRL.

Khi xây dựng hệ thống hoàn chỉnh, phải xác định **CRL signer** thích hợp, kiểm tra chain đến trust anchor, KeyUsage `cRLSign`, issuer/authority key identifier, scope/IDP, thuật toán chữ ký và validity của CRL. Java PKIX revocation checker xử lý các quy tắc và lựa chọn phức tạp hơn so với tự gọi mỗi `verify` và `isRevoked`.

## 5. Chuẩn bị fixture certificate và CRL bằng `keytool`

Để bài học **chạy độc lập không cần LDAP hay internet**, ta tạo một CA tự ký **chỉ dùng cho demo** và một CRL của CA đó. `keytool` đi kèm JDK có thể xuất certificate dạng PEM và CRL dạng PEM (`-rfc`).

**Lưu ý an toàn:** mật khẩu `samplepass` trong các lệnh sau chỉ phục vụ một fixture tạm được tạo ở thư mục học tập cô lập. Không dùng những lệnh chứa mật khẩu trên command line cho secret sản xuất, vì shell history, process list, CI log có thể ghi lại chúng. **Không nhập CA demo vào truststore dùng thật.**

Dùng thư mục **trống riêng cho bài học** rồi chạy các lệnh một dòng sau (dùng được trên shell có `keytool` trong `PATH`):

```bash
keytool -genkeypair -alias demo-ca -keyalg RSA -keysize 2048 -validity 365 -dname "CN=Training Demo CA,O=Learning" -ext "BC:critical=ca:true,pathlen:0" -ext "KU:critical=keyCertSign,cRLSign" -storetype PKCS12 -keystore demo-ca.p12 -storepass samplepass -keypass samplepass -noprompt

keytool -exportcert -rfc -alias demo-ca -keystore demo-ca.p12 -storepass samplepass -file demo-ca.pem

keytool -gencrl -rfc -alias demo-ca -keystore demo-ca.p12 -storepass samplepass -file demo-ca.crl.pem
```

Giải thích từng lệnh:

1. **`-genkeypair`:** sinh cặp khóa và certificate CA tự ký, `BasicConstraints=CA:true` và `KeyUsage` gồm `keyCertSign`, `cRLSign`. Đây là **test CA**, không nên xem là CA thực được một trình duyệt hay hệ điều hành tin cậy.
2. **`-exportcert -rfc`:** xuất **public certificate** dưới PEM vào `demo-ca.pem`; không xuất private key.
3. **`-gencrl -rfc`:** dùng private key của demo CA phát hành `demo-ca.crl.pem` có định dạng PEM, chứa CRL được ký. Nếu không chỉ định `-id`, fixture thường không có serial certificate nào bị thu hồi.

Với command trên, `demo-ca.p12` chứa **private key test**; đừng commit hoặc đưa nó lên nơi public. Sau thử nghiệm có thể xóa toàn bộ fixture trong thư mục test; để minh họa, code Java chỉ đọc **`demo-ca.pem`** và **`demo-ca.crl.pem`**.

### 5.1. Làm sao nhận biết hai file PEM?

Certificate PEM bắt đầu bằng:

```text
-----BEGIN CERTIFICATE-----
... (base64 encoding cua certificate)
-----END CERTIFICATE-----
```

CRL PEM bắt đầu bằng:

```text
-----BEGIN X509 CRL-----
... (base64 encoding cua CRL)
-----END X509 CRL-----
```

Đoạn trên chỉ minh họa **định dạng**, không phải chứng thư có giá trị để import. PEM là **cách biểu diễn dữ liệu ASN.1/DER bằng Base64 có header**, không làm certificate hoặc CRL đáng tin hơn. Với dữ liệu nhị phân DER, `CertificateFactory.getInstance("X.509")` cũng có thể parse theo khả năng provider.

### 5.2. Vì sao demo CA được dùng làm `TrustAnchor`?

Trong code mục 6, ta đăng ký certificate demo này làm một `TrustAnchor` **chỉ để cho thấy API PKIX nhận CertStore**. Trong ứng dụng thực, CA tin cậy phải được cấp phát/đánh giá theo chính sách từ nguồn tin cậy; **tuyệt đối không tự thêm CA bất kỳ tìm được qua CertStore thành TrustAnchor**. Làm thế có thể khiến kẻ tấn công đưa CA của họ vào danh sách tin cậy và ký chứng thư giả.

## 6. Chương trình Java 17+ hoàn chỉnh: `CertStore` từ `Collection`

Chương trình dưới đây:

- Parse một X.509 certificate và một X.509 CRL từ hai file PEM đã tạo;
- Tạo `CertStore.getInstance("Collection", CollectionCertStoreParameters(...))`;
- Truy vấn certificate bằng `X509CertSelector` theo Subject và CA BasicConstraints;
- Truy vấn một subject không tồn tại, cho kết quả rỗng;
- Truy vấn CRL bằng `X509CRLSelector` theo CA issuer, `setCertificateChecking` và thời gian;
- Minh họa rằng cần **xác minh chữ ký CRL** riêng với thao tác tìm;
- Gắn `CertStore` vào `PKIXParameters` trong bộ nhớ, không tự nhận là đã kiểm chứng một chain TLS thực.

Đây là **mã chạy được** với Java 17 trở lên và fixture ở mục 5.

Lưu đúng tên **CertStoreCollectionDemo.java**:

```java
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CRL;
import java.security.cert.CertStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLSelector;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import javax.security.auth.x500.X500Principal;

public class CertStoreCollectionDemo {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                "usage: CertStoreCollectionDemo cert.pem issuer.crl.pem");
        }

        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        X509Certificate ca;
        X509CRL crl;
        try (InputStream certificateStream =
                     Files.newInputStream(Path.of(args[0]));
             InputStream crlStream =
                     Files.newInputStream(Path.of(args[1]))) {
            ca = (X509Certificate) factory.generateCertificate(
                    certificateStream);
            crl = (X509CRL) factory.generateCRL(crlStream);
        }

        CertStore store = CertStore.getInstance(
                "Collection",
                new CollectionCertStoreParameters(List.of(ca, crl)));

        X509CertSelector bySubject = new X509CertSelector();
        bySubject.setSubject(ca.getSubjectX500Principal());
        bySubject.setBasicConstraints(0); // Chon CA certificate.
        Collection<? extends Certificate> certificates =
                store.getCertificates(bySubject);

        X509CertSelector missingSubject = new X509CertSelector();
        missingSubject.setSubject(
                new X500Principal("CN=Not In Store"));
        Collection<? extends Certificate> missing =
                store.getCertificates(missingSubject);

        X509CRLSelector byIssuer = new X509CRLSelector();
        byIssuer.addIssuer(ca.getSubjectX500Principal());
        byIssuer.setCertificateChecking(ca);
        byIssuer.setDateAndTime(new Date());
        Collection<? extends CRL> crls = store.getCRLs(byIssuer);

        // Chi la kiem tra chu ky tren CRL demo, KHONG thay PKIX.
        crl.verify(ca.getPublicKey());
        ca.checkValidity();

        // Demo trust anchor da tin cai. Khong lam dieu nay voi CA tu CertStore.
        PKIXParameters parameters = new PKIXParameters(
                Set.of(new TrustAnchor(ca, null)));
        parameters.addCertStore(store);

        System.out.println("CertStore type: " + store.getType());
        System.out.println("CA certificates matched: "
                + certificates.size());
        System.out.println("unknown subject matched: "
                + missing.size());
        System.out.println("current CRLs matched: " + crls.size());
        System.out.println("CRL issuer equals CA subject: "
                + crl.getIssuerX500Principal()
                     .equals(ca.getSubjectX500Principal()));
        System.out.println("CRL signature verified: true");
        System.out.println("demo CA listed as revoked: "
                + crl.isRevoked(ca));
        System.out.println("PKIX CertStores registered: "
                + parameters.getCertStores().size());
    }
}
```

Biên dịch và chạy **sau khi đã tạo fixture**:

```bash
javac -encoding UTF-8 CertStoreCollectionDemo.java
java CertStoreCollectionDemo demo-ca.pem demo-ca.crl.pem
```

Stdout kỳ vọng trên JDK với provider `Collection` và certificate/CRL demo vừa tạo:

```text
CertStore type: Collection
CA certificates matched: 1
unknown subject matched: 0
current CRLs matched: 1
CRL issuer equals CA subject: true
CRL signature verified: true
demo CA listed as revoked: false
PKIX CertStores registered: 1
```

Chương trình được kiểm tra bằng biên dịch tương thích Java 17 (`javac --release 17`) và chạy trên JDK hỗ trợ Java 17+ cùng các fixture `keytool`. **Kết quả có thể khác nếu CRL hết thời hạn `nextUpdate`, certificate hết hạn hoặc fixture đã bị thay đổi**; hãy tạo fixture mới trước mỗi lần thử.

### 6.1. Phân tích từng khối code

**Parse X.509:** `CertificateFactory.generateCertificate` và `generateCRL` tạo đối tượng Java từ dữ liệu PEM/DER. Parse thành công **không** xác minh chuỗi tin cậy hay nguồn tải. `try-with-resources` đóng các file stream.

**Repository trong memory:** `new CollectionCertStoreParameters(List.of(ca, crl))` đưa hai loại đối tượng vào một `Collection`; provider thực hiện lọc certificate và CRL phù hợp. Ở đây certificate và CRL **không** được lấy từ mạng.

**Certificate selector:** `setSubject(ca.getSubjectX500Principal())` cùng `setBasicConstraints(0)` tìm CA certificate đúng Subject. `CN=Not In Store` không khớp nên trả collection rỗng.

**CRL selector:** `addIssuer(ca.getSubjectX500Principal())` khớp issuer của CRL demo; `setDateAndTime(new Date())` chỉ chọn CRL còn nằm trong khoảng `thisUpdate/nextUpdate` tại lúc chạy. `setCertificateChecking(ca)` cung cấp hint phù hợp với fixture; nó **không kiểm tra thu hồi tự động**.

**Kiểm tra riêng:** `crl.verify(ca.getPublicKey())` xác minh chữ ký của CRL demo bằng public key CA demo; `ca.checkValidity()` kiểm tra riêng validity date của certificate. **Cả hai chưa phải PKIX path validation đầy đủ.**

**Đăng ký PKIX:** `new PKIXParameters(Set.of(new TrustAnchor(ca, null)))` chỉ tạo tham số với CA mà ta **đã chủ động chọn tin trong môi trường demo**. `parameters.addCertStore(store)` cho phép builder/validator PKIX dùng repository; trong code này **chưa gọi `CertPathBuilder.build` hoặc `CertPathValidator.validate`**.

**`crl.isRevoked(ca) == false`:** fixture CRL không chứa serial của demo CA. Kết quả này **không được áp dụng như cơ chế quyết định bảo mật của sản phẩm**; CA tự ký của demo cũng không phải mô hình hoàn chỉnh cho kiểm tra CRL end-entity.

### 6.2. Một kiểm thử âm đặc biệt quan trọng

Thay certificate/CRL bằng file khác, chọn issuer DN không khớp hoặc đặt `setDateAndTime` sau `nextUpdate` có thể làm truy vấn trả **0**. Khi đó:

- `getCRLs` trả 0 là **không có ứng viên đủ tiêu chí**, không phải xác nhận “đối tượng không bị thu hồi”.
- Một CRL được parse và trả về có thể **không được ký bởi issuer đúng** nếu thay file không đáng tin; phải kiểm chứng chữ ký và policy.
- Nếu file PEM hỏng, `generateCertificate` hoặc `generateCRL` có thể ném `CertificateException`/`CRLException`; đó là lỗi đầu vào/parse, không phải “không có CRL”.
- Nếu repository LDAP lỗi network, `CertStoreException` cũng khác với việc trả 0.

Vì lý do này, code thực phải phân biệt **FOUND**, **NO_MATCH/INSUFFICIENT_DATA**, **FETCH_ERROR**, **PARSE_ERROR**, **UNTRUSTED_DATA** và **VALIDATED** thay vì chuyển tất cả về cùng boolean “certificate valid”.

## 7. Cấp CertStore cho `CertPathBuilder` và `CertPathValidator`

Việc dùng `CertStore` có giá trị lớn nhất khi nó nằm trong một luồng **PKIX certification path**. Phần này mô tả cách ghép các API, không giả vờ rằng một truy vấn đơn lẻ là xác thực chain.

### 7.1. Tìm đường dẫn với `PKIXBuilderParameters`

Để builder tìm chuỗi từ leaf đến CA được tin, ứng dụng cần:

1. **Trust anchors được cấu hình tin cậy** từ truststore/chính sách riêng; không lấy tự động tất cả certificate trả về từ repository để làm anchors.
2. **Target constraints** xác định certificate cần xây chain bằng `X509CertSelector` (ví dụ certificate exact, subject, SAN).
3. **CertStores** cung cấp intermediate certificate và CRL ứng viên.
4. **Tham số PKIX khác**: validity date, signature/provider policy, path length, certificate policy, revocation policy và các checker cần thiết.
5. Gọi `CertPathBuilder.getInstance("PKIX").build(params)`; xử lý `CertPathBuilderException` như **không xây được chain hợp lệ theo cấu hình**, không tự động retry bằng cách bỏ tin cậy/thu hồi.

Ví dụ trích đoạn (cần `targetCertificate`, `approvedAnchors` và `store` từ cấu hình/certificate hợp lệ của ứng dụng):

```java
X509CertSelector target = new X509CertSelector();
target.setCertificate(targetCertificate);

PKIXBuilderParameters params =
        new PKIXBuilderParameters(approvedAnchors, target);
params.addCertStore(store);

CertPathBuilder builder = CertPathBuilder.getInstance("PKIX");
PKIXCertPathBuilderResult result =
        (PKIXCertPathBuilderResult) builder.build(params);
CertPath path = result.getCertPath();
```

**Đây là một fragment, không phải file Java chạy độc lập.** Nó cố ý không “tự trust certificate tìm được”. `PKIXCertPathBuilderResult` còn mang thông tin trust anchor, public key và policy tree; quy trình hoàn chỉnh phải kiểm tra danh tính đích theo yêu cầu ứng dụng (ví dụ hostname TLS) ở lớp tương ứng.

### 7.2. Kiểm tra một path đã có bằng `PKIXParameters`

Nếu ứng dụng đã có `CertPath`, dùng `CertPathValidator` với `PKIXParameters`:

```java
PKIXParameters params = new PKIXParameters(approvedAnchors);
params.addCertStore(store);
// Cấu hình thời gian/policy/revocation phù hợp trước khi validate.
CertPathValidator validator = CertPathValidator.getInstance("PKIX");
CertPathValidatorResult result = validator.validate(path, params);
```

Đây cũng là fragment, giả định `approvedAnchors` và `path` đã được cung cấp đúng. Không đặt `params.setRevocationEnabled(false)` chỉ để “tránh phải có CRL”. Khi thiếu nguồn thu hồi, cần quyết định theo chính sách fail-closed/soft-fail đã được đánh giá, không bỏ kiểm tra tùy tiện.

### 7.3. `addCertStore` có ý nghĩa gì?

`params.addCertStore(store)` thông báo repository mà PKIX implementation **có thể dùng** để tìm intermediate hoặc CRL trong quá trình xây dựng/xác minh. Nó **không**:

- Tự khởi chạy builder/validator;
- Tự tải toàn bộ certificate/CRL trong repository;
- Tự tạo trust anchor;
- Tự buộc mọi CRL tìm thấy được chấp nhận;
- Đảm bảo provider sẽ hỏi một loại repository theo cách/cùng thứ tự ở mọi JDK.

Cần thực sự gọi `build(...)`/`validate(...)` và kiểm tra kết quả/lỗi theo chính sách.

## 8. Nguồn CertStore: `Collection`, `LDAP` và repository từ xa

### 8.1. `Collection` — nguồn trong bộ nhớ

`Collection` rất hữu ích khi:

- Đã tải intermediate certificates hoặc CRLs qua kênh riêng có kiểm soát;
- Muốn có test fixture lặp lại được mà không dùng mạng;
- Cung cấp các certificate/CRL cụ thể cho PKIX builder/validator;
- Đã có một bộ dữ liệu đã parse từ PEM, DER hoặc file bundle theo trust policy của ứng dụng.

`CollectionCertStoreParameters` nhận một collection; ứng dụng cần quản lý nguồn dữ liệu, đối tượng đồng thời, kích thước, vòng đời và độ tin cậy khi tập dữ liệu có thể bị thay đổi. Một `CertStore` chứa item ở memory **không cấp cho item trạng thái đáng tin**.

### 8.2. `LDAP` — repository ngoài

Java có `LDAPCertStoreParameters` để cấu hình hostname và port; ví dụ cú pháp:

```java
// Minh họa API, không tạo kết nối thật trong bài học.
LDAPCertStoreParameters ldapParameters =
        new LDAPCertStoreParameters("ldap.example.internal", 389);

CertStore ldapStore =
        CertStore.getInstance("LDAP", ldapParameters);

X509CertSelector subjectSelector = new X509CertSelector();
subjectSelector.setSubject(
        new X500Principal("CN=Intermediate CA,O=Example"));
Collection<? extends Certificate> matches =
        ldapStore.getCertificates(subjectSelector);
```

Đây là fragment minh họa, **không chạy được khi không có LDAP server thích hợp**. Tên host là dữ liệu ví dụ; không tự gửi request đến host này trong bài học.

Các điểm phải lưu ý:

- Provider LDAP có thể cần **Subject DN, Issuer DN hoặc issuer info** đủ rõ để xác định DN/entry cần truy vấn. `getCertificates(null)` hoặc selector rỗng có thể không khả thi và dẫn đến `CertStoreException`.
- Với CRL, dùng `X509CRLSelector` và issuer, `setCertificateChecking` khi phù hợp để provider có gợi ý về CA cần hỏi.
- Các LDAP repository phải có dữ liệu và schema phù hợp với provider; không phải mọi LDAP/Active Directory setup đều có đúng certificate/CRL attributes mong muốn.
- Port 389 trong ví dụ chỉ mô tả cấu hình API; **không khẳng định kết nối được mã hóa hay xác thực**. Để dùng thực, phải thiết kế bảo vệ kết nối LDAP, certificate/TLS, authentication, credential storage và chống giả mạo nguồn theo provider/hạ tầng.
- Network failure, timeout, authentication failure và dữ liệu directory sai là những trạng thái riêng cần policy xử lý, không âm thầm coi là “không bị thu hồi”.

### 8.3. AIA và CRL Distribution Points có phải tự tải?

Certificate X.509 có thể chứa:

- **AIA — Authority Information Access:** có thể chỉ ra nơi tìm issuer certificate hoặc dịch vụ OCSP, tùy extension.
- **CRL Distribution Points (CDP):** có thể cho biết vị trí lấy CRL thích hợp.

**Không mặc định** rằng chỉ cần tạo `CertStore("Collection")` hoặc đọc một certificate có AIA/CDP là Java tự kết nối các URL đó. Việc tự động truy xuất AIA/CDP/OCSP phụ thuộc **PKIX provider, cấu hình runtime và chính sách giao thức**. Một số JDK có thuộc tính/provider behavior riêng; không dùng chúng như hợp đồng portable của `CertStore`.

Nếu ứng dụng tự tải certificate/CRL từ URI trong extension, phải coi các URI **không tin cậy**: áp allowlist domain/protocol, giới hạn size/time/redirect, bảo vệ chống SSRF, DNS rebinding, mạng nội bộ/metadata endpoints, loop và cache poisoning. Sau khi tải vẫn phải parse và PKIX-validate như thường lệ.

### 8.4. Caching và tính mới

CRL có `thisUpdate/nextUpdate`; một cache giữ CRL sau `nextUpdate` có thể gây sai chính sách thu hồi. Cần phân biệt **HTTP cache TTL, LDAP provider cache** và **validity/freshness của CRL**. Trạng thái “có dữ liệu cache” không đồng nghĩa “dữ liệu còn hợp lệ”.

Nếu app tạo `Collection` chứa dữ liệu cache riêng, app phải đảm bảo cache invalidation và rotation đúng. Nếu provider LDAP có cache nội bộ, thời gian/cách cấu hình phụ thuộc JDK/provider; không lấy cache TTL làm thay thế cho PKIX revocation validation.

## 9. Chính sách xác minh CRL, OCSP và `PKIXRevocationChecker`

### 9.1. CRL và OCSP khác nhau

- **CRL:** một danh sách thu hồi do CA/CRL signer phát hành, có thể được lưu/cached và phân phối qua LDAP/HTTP.
- **OCSP:** cơ chế hỏi trạng thái chứng thư (thường theo serial/issuer) qua OCSP responder, có ký phản hồi và metadata riêng.
- Hệ thống PKIX có thể dùng một hoặc cả hai theo chính sách. `CertStore.getCRLs` **không** phải API OCSP. Java dùng `PKIXRevocationChecker`/provider để kiểm soát revocation-checking trong PKIX.

### 9.2. `PKIXRevocationChecker`

Từ `CertPathBuilder` hoặc `CertPathValidator` loại `PKIX`, có thể lấy một checker hỗ trợ revocation:

```java
CertPathValidator validator = CertPathValidator.getInstance("PKIX");
PKIXRevocationChecker checker =
        (PKIXRevocationChecker) validator.getRevocationChecker();

// Ví dụ: ưu tiên CRL. Cần đánh giá policy/fallback trước khi dùng.
checker.setOptions(EnumSet.of(
        PKIXRevocationChecker.Option.PREFER_CRLS));

params.addCertPathChecker(checker);
CertPathValidatorResult result = validator.validate(path, params);
```

**Fragment** trên giả định `params` và `path` có sẵn, cùng trust anchors và CertStores hợp lệ. Tùy implementation, khi cài checker explicit, cơ chế revocation do checker quản lý và phải hiểu interaction với `setRevocationEnabled` theo API. Đừng lắp thêm một checker mà không đánh giá hiệu lực policy.

Các option hay gặp:

| Option | Ý nghĩa | Rủi ro/lưu ý |
| --- | --- | --- |
| `PREFER_CRLS` | Ưu tiên CRL thay vì OCSP theo hành vi provider | Chỉ ưu tiên, không đảm bảo luôn có CRL phù hợp |
| `NO_FALLBACK` | Không thử cơ chế thay thế khi cơ chế ưu tiên thất bại | Có thể tăng lỗi khi nguồn không khả dụng; phù hợp hay không phụ thuộc policy |
| `SOFT_FAIL` | Trong những điều kiện nguồn revocation không truy cập được/lỗi được chỉ rõ, có thể cho qua | **Có thể chấp nhận certificate khi trạng thái thu hồi chưa xác định**; chỉ dùng khi threat model cho phép |
| `ONLY_END_ENTITY` | Chỉ kiểm tra thu hồi end-entity theo semantics của option | Không phù hợp nếu policy yêu cầu kiểm tra intermediates |

Không nên bật `SOFT_FAIL` để “xử lý mọi lỗi certificate”. Nó **không phải** công tắc cho phép bỏ qua invalid signatures, hết hạn, chain không tin cậy hoặc certificate đã bị xác định revoked. Một số trường hợp thiếu thông tin revocation phải **fail closed** theo chính sách của môi trường.

### 9.3. Khi nào “không tìm được CRL” phải chặn?

Một hệ thống tài chính hoặc mTLS nội bộ có chính sách revocation nghiêm có thể yêu cầu **từ chối** nếu không xác định được trạng thái thu hồi. Hệ thống khác có thể có chính sách mềm hơn để đảm bảo availability. Đây là **quyết định bảo mật công khai trong thiết kế**, có monitoring và kiểm toán, không phải fallback được cài ẩn trong `catch (CertStoreException e) { return true; }`.

Phải phân biệt:

```text
Certificate explicitly revoked         => REJECT
CRL signature invalid / scope invalid  => REJECT candidate; status unresolved
No current CRL or OCSP status           => UNKNOWN; handle by policy
Validated status good/not revoked      => Continue chain & identity checks
CertPath invalid for another reason    => REJECT
```

Một certificate “không có trong CRL được lấy” không tự được coi là `GOOD` nếu CRL chưa được xác minh/đúng scope/đủ mới.

## 10. Lỗi, giới hạn tài nguyên và bảo vệ dữ liệu nguồn

### 10.1. Bảng chẩn đoán lỗi Java

| Hiện tượng | Khả năng | Hướng xử lý |
| --- | --- | --- |
| `CertStore.getInstance("LDAP",...)` báo `NoSuchAlgorithmException` | Type/provider không hỗ trợ | Kiểm tra provider/JDK và danh sách algorithm đã được phê duyệt |
| `InvalidAlgorithmParameterException` khi tạo store | Tham số không đúng type/schema | Dùng `CollectionCertStoreParameters` hoặc `LDAPCertStoreParameters` đúng type |
| `getCertificates(...)` trả rỗng | Không có match hoặc repository không tìm được theo tiêu chí | Kiểm tra selector, subject/issuer và nguồn dữ liệu; không tự trust |
| `getCRLs(...)` trả rỗng | Không có CRL đúng issuer/time/scope, hoặc dữ liệu thiếu | Kiểm tra `nextUpdate`, issuer, policy, nguồn CRL/OCSP |
| `CertStoreException` khi truy vấn | Lỗi repository/provider/network hoặc selector không đủ cụ thể | Ghi log thông tin phi bí mật, phân loại trạng thái UNKNOWN |
| `CertificateException` / `CRLException` khi parse | Dữ liệu PEM/DER hỏng hoặc không đúng loại | Kiểm tra encoding và nguồn, fail safely |
| `crl.verify(...)` thất bại | Chữ ký sai, key không khớp hoặc thuật toán không hỗ trợ | Không dùng CRL như bằng chứng revocation |
| `CertPathBuilderException` | Không tìm được chain hợp lệ theo trust/policy | Xem trust anchors, intermediates và constraints, không bỏ xác thực |
| `CertPathValidatorException` | Path không thỏa ràng buộc PKIX | Giữ lỗi an toàn, xem cause/reason và policy |
| Kết quả khác giữa các provider | Khác hỗ trợ type/query/cache/schema | Kiểm tra hợp đồng provider và test interop |

### 10.2. Input validation

Certificate/CRL có thể đến từ mạng hoặc file do bên khác tạo. Trước khi parse:

- Giới hạn dung lượng file/response, số item, số redirects, timeout và số lần retry.
- Không đưa URL từ AIA/CDP trực tiếp vào fetcher có quyền truy cập metadata service hoặc mạng nội bộ.
- Dùng TLS/LDAP channel phù hợp và xác minh nguồn phân phối khi chính sách yêu cầu; đồng thời vẫn **kiểm tra chữ ký certificate/CRL**.
- Không để attacker điều khiển tùy ý `provider`, LDAP host/port, file path hoặc trust anchor.
- Sử dụng đúng kiểu Java `X509Certificate`/`X509CRL`, parse format hạn chế và không tạo quá nhiều đối tượng chưa kiểm soát trong cache.
- Đảm bảo thread-safety cho collection nguồn nếu cập nhật đồng thời; không tự giả định backing collection mutable có thể thay đổi vô hại khi provider đọc.
- Log các trường chẩn đoán vừa đủ như issuer, serial, CRL number, thời gian và correlation ID theo chính sách; tránh log toàn bộ certificate/CRL nếu chứa thông tin cá nhân hoặc làm tăng rò rỉ dữ liệu.

### 10.3. Phòng tránh downgrade và bypass

Không sửa logic thành:

```java
try {
    return store.getCRLs(selector).isEmpty(); // SAI
} catch (CertStoreException problem) {
    return true; // SAI
}
```

Truy vấn rỗng **không chứng minh** certificate tốt, còn exception càng không chứng minh điều đó. Để trả lời “trust this certificate?” phải đi qua **PKIX validator/revocation checker** với policy rõ ràng. Đừng chuyển từ CRL checking sang “không kiểm tra gì cả” khi LDAP tạm lỗi.

## 11. Tình huống áp dụng

### 11.1. Server nhận thiếu intermediate certificate

Một server TLS gửi leaf nhưng thiếu intermediate. Java client có thể gặp lỗi xây certification path. Có thể tải intermediate qua nguồn được policy cho phép, đưa candidate đó vào `Collection CertStore` hoặc repository có sẵn cho path builder.

**Không được** thêm intermediate bất kỳ tìm được thành trust anchor chỉ để cho chain chạy. Builder phải tìm đường dẫn được ký hợp lệ đến **root vốn đã được tin**.

### 11.2. Hệ thống nội bộ phát hành chứng thư người dùng

Một CA doanh nghiệp công bố CRL qua directory. `LDAP CertStore` giúp lấy candidate CRL đúng issuer. Nhưng để quyết định certificate có còn dùng được trong mTLS, validator phải kiểm tra chữ ký, issuer/key usage, scope, thời gian CRL và revocation status. Nếu directory không truy cập được, áp chính sách UNKNOWN phù hợp, không tự cho qua.

### 11.3. Test PKIX offline không phụ thuộc mạng

CI có thể có bộ certificate/CRL fixture dành riêng cho test, được parse bằng `CertificateFactory` và đưa vào `CertStore("Collection")`. Như vậy test selector và path builder ổn định hơn khi mạng bị thay đổi.

Nếu muốn kiểm tra riêng logic “certificate revoked”, cần fixture CA + leaf có serial cụ thể và **CRL chứa serial leaf đó** cùng bằng chứng ký hợp lệ. Demo ở mục 6 cố ý **không** tự khẳng định đã kiểm thử quy trình revocation đầy đủ; nó tập trung vào **thu thập bằng CertStore**.

### 11.4. Cập nhật CRL trong cache

Một CRL sắp hết `nextUpdate` cần được refresh trước khi sử dụng cho quyết định mới. Cache lỗi phải trả trạng thái rõ ràng, không giữ “CRL cũ nhưng vẫn bảo mật”. Có thể theo dõi issuer, CRL number, `thisUpdate/nextUpdate` và nguồn; không cần lưu lại secret key vì CRL là **dữ liệu công khai được ký**.

### 11.5. Nhiều nguồn thu thập

Một PKIX builder có thể được cấp **nhiều CertStores**, ví dụ Collection chứa certificate chain có sẵn cộng repository LDAP có thể tìm intermediate hoặc CRL. Mỗi nguồn đều có thể chứa ứng viên không đáng tin; **trọng tâm vẫn là trust anchors và validation policy**, không phải số lượng nguồn.

## 12. Lỗi thường gặp và cách sửa

| Nhầm lẫn | Hậu quả | Cách đúng |
| --- | --- | --- |
| “`CertStore` giống truststore.” | Tin certificate thu thập chưa xác thực | CertStore chỉ là repository; trust anchors cấu hình riêng |
| “`getCertificates` trả một kết quả nghĩa đã tin.” | Bỏ chain/signature/identity checks | Dùng `CertPathBuilder/Validator` với PKIX |
| “CRL trả về nghĩa certificate bị thu hồi.” | Không kiểm tra serial/issuer/scope | Kiểm tra revocation đúng certificate và validation policy |
| “`crl.isRevoked(cert)==false` nghĩa certificate an toàn.” | Có thể dùng CRL giả/cũ/khác scope | Verify CRL, chain, date, scope, PKIX status |
| “`setCertificateChecking(cert)` kiểm tra revocation luôn.” | Không có quyết định thu hồi | Đây là hint để tìm CRL; dùng PKIX revocation checker |
| “Đã có `setIssuer` là đủ xác minh chữ ký CRL.” | Issuer DN trùng không bảo đảm đúng key | Verify signer và chain/authorization |
| “CRL thiếu `nextUpdate` cũng match theo date.” | Selector có thể trả 0 | Hiểu `setDateAndTime` cần time window đầy đủ |
| “LDAP truy vấn không cần selector.” | Provider có thể không biết DN cần tìm | Cung cấp subject/issuer/certificateChecking phù hợp |
| “Collection trả rỗng = LDAP bị lỗi.” | Gộp trạng thái khác nhau | Phân biệt empty collection và `CertStoreException` |
| “Đưa certificate tìm thấy vào `TrustAnchor` để fix.” | Attacker có thể cài CA giả | Trust anchors chỉ từ cấu hình tin cậy riêng |
| “`getCRLs(null)` quét được toàn internet.” | API không làm crawler và LDAP có giới hạn | Truy vấn theo selector/repository được cấu hình |
| “AIA/CDP luôn được Java tự fetch.” | Ứng dụng bỏ sót intermediate/CRL | Kiểm tra provider/config và explicit retrieval policy |
| “Tải CRL qua HTTP là đủ an toàn.” | Có thể nhận dữ liệu thay đổi | Xác minh chữ ký/issuer/scope và network policy |
| “Nếu LDAP lỗi, cho qua để tăng availability.” | Bỏ revocation check không có kiểm soát | Fail closed hoặc soft-fail theo policy được phê duyệt |
| “Chỉ dùng CRL, không cần biết OCSP.” | Bỏ nguồn revocation có thể được yêu cầu | Chọn chiến lược CRL/OCSP theo PKIX và policy |
| “`setCertificateValid(now)` là toàn bộ xác minh.” | Thiếu chain, revocation, hostname, EKU | Chỉ lọc validity, sau đó PKIX và identity checks |

## 13. Checklist review thiết kế CertStore

1. **Mục đích:** cần tìm certificate trung gian, CRL, hay cả hai? Đã tách repository search khỏi quyết định tin cậy?
2. **Nguồn:** Collection/LDAP/provider tương thích, nguồn dữ liệu và quyền truy cập đã được kiểm soát?
3. **Selector:** dùng Subject/Issuer, serial/CA/validity hoặc CRL issuer/date/scope đúng yêu cầu; hiểu `setCertificateChecking` chỉ là hint?
4. **Lỗi:** phân biệt empty matches, parsing error, repository exception và revocation status UNKNOWN?
5. **Trust:** có trust anchors cấp phát riêng, không tự nâng candidate thành root được tin?
6. **PKIX:** có cung cấp `CertStore` cho `PKIXParameters`/`PKIXBuilderParameters` và thực sự gọi validator/builder phù hợp?
7. **CRL:** đã kiểm tra chữ ký, issuer, `thisUpdate/nextUpdate`, `cRLSign`, delta/IDP/scope khi áp dụng, algorithm policy?
8. **Revocation:** policy CRL/OCSP/fallback/soft-fail đã được mô tả và kiểm thử? Khi không xác định được trạng thái có làm đúng policy?
9. **Định danh:** ứng dụng TLS có kiểm tra hostname và mục đích sử dụng chứng thư tại lớp phù hợp, không chỉ chain?
10. **Network:** có giới hạn timeouts, redirects, size, SSRF/AIA/CDP risks, cache expiry và quyền truy cập?
11. **Kiểm thử:** có fixture valid/expired/wrong issuer/bad signature/truncated file/revoked leaf/LDAP unavailable?
12. **Vận hành:** có log an toàn, monitoring freshness/cache, rotation CA/CRL, rollback và audit?

## 14. Bài tập tự kiểm tra

**Câu 1.** `CertStore.getCertificates(selector)` trả về một certificate có `Subject DN` đúng. Có chắc certificate này đáng tin không?  
**Đáp án:** **Không.** Selector chỉ tìm candidate; PKIX chain/trust anchor và identity validation còn phải thực hiện.

**Câu 2.** Khi dùng `CollectionCertStoreParameters`, có thể đặt cả `X509Certificate` và `X509CRL` vào một collection không?  
**Đáp án:** **Có.** `CertStore("Collection")` dùng `getCertificates` để lấy certificate và `getCRLs` để lấy CRL phù hợp.

**Câu 3.** Một CRL có issuer đúng nhưng `nextUpdate` đã qua. `setDateAndTime(new Date())` thường trả về CRL đó không?  
**Đáp án:** **Không.** CRL phải bao trùm thời điểm yêu cầu theo `thisUpdate ≤ date < nextUpdate`.

**Câu 4.** `setCertificateChecking(cert)` trên `X509CRLSelector` có phải “kiểm tra ngay cert bị thu hồi” không?  
**Đáp án:** **Không.** Đây là hint cho repository khi tìm CRL; phải chạy revocation validation.

**Câu 5.** `getCRLs` trả collection rỗng. Có thể coi certificate đang tốt không?  
**Đáp án:** **Không.** Không có CRL match là **thiếu dữ liệu thu hồi phù hợp**, không phải trạng thái GOOD.

**Câu 6.** Kết quả `crl.verify(key)` thành công có đủ để tin CRL đó không?  
**Đáp án:** **Chưa đủ.** Phải biết key đó thuộc signer có quyền phát hành CRL theo trust chain/policy, kiểm tra thời gian và scope.

**Câu 7.** Tìm intermediate ký leaf thì lọc `setSubject(leaf.getIssuerX500Principal())` hay `setIssuer(leaf.getIssuerX500Principal())`?  
**Đáp án:** Thường bắt đầu bằng **`setSubject`** để tìm certificate có subject trùng Issuer DN của leaf; sau đó xác minh chữ ký và PKIX path.

**Câu 8.** `CertStore("LDAP")` có tự động tải CRL từ mọi URL CDP của certificate không?  
**Đáp án:** **Không được mặc định.** LDAP store truy vấn theo cấu hình/provider; AIA/CDP retrieval có cơ chế riêng và yêu cầu an toàn mạng.

**Câu 9.** Một developer gọi `params.addCertStore(store)` rồi lập tức chấp nhận certificate. Đúng chưa?  
**Đáp án:** **Chưa.** `addCertStore` chỉ đăng ký nguồn; còn phải `build`/`validate` với trust anchors, revocation và các checks khác.

**Câu 10.** Khi repository gặp `CertStoreException`, có nên trả `true` để bỏ qua kiểm tra CRL không?  
**Đáp án:** **Không được tự ý.** Phải ghi nhận trạng thái UNKNOWN và theo fail-closed/soft-fail policy được phê duyệt.

**Câu 11.** `SOFT_FAIL` trong `PKIXRevocationChecker` có nghĩa được bỏ qua mọi chữ ký certificate sai không?  
**Đáp án:** **Không.** Option chỉ áp dụng cho các loại lỗi không xác định được trạng thái thu hồi được quy định; không cấp quyền chấp nhận chain có lỗi bảo mật khác.

**Câu 12.** Vì sao không nên nhập CA vừa tìm trong CertStore vào truststore để sửa lỗi “unable to find valid certification path”?  
**Đáp án:** Như vậy biến dữ liệu **chưa được tin** thành **trust anchor**; attacker có thể lợi dụng để thiết lập CA giả. Phải xác minh nguồn/cấp phát trust anchor theo policy chính thức.

### Tài liệu tham khảo

- [Oracle Java 21 — Java PKI Programmer's Guide](https://docs.oracle.com/en/java/javase/21/security/java-pki-programmers-guide.html): mô hình `CertStore`, selectors, PKIX builder/validator và nguồn LDAP.
- [Java SE 21 — CertStore](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/CertStore.html): `getInstance`, `getCertificates`, `getCRLs` và error contract.
- [Java SE 21 — CollectionCertStoreParameters](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/CollectionCertStoreParameters.html): nguồn collection.
- [Java SE 21 — X509CertSelector](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/X509CertSelector.html): subject, issuer, validity và BasicConstraints.
- [Java SE 21 — X509CRLSelector](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/X509CRLSelector.html): issuer, time, `setCertificateChecking`.
- [Java SE 21 — PKIXRevocationChecker](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/PKIXRevocationChecker.html): CRL/OCSP và policy.
- [RFC 5280 — Internet X.509 PKI Certificate and CRL Profile](https://www.rfc-editor.org/rfc/rfc5280): cấu trúc certificate, CRL, scope, trust path và revocation semantics.

### Ghi nhớ

**`CertStore` dùng để tìm certificate hoặc CRL; `X509CertSelector` và `X509CRLSelector` dùng để lọc ứng viên; `PKIX` và trust anchors mới quyết định certificate/path có được chấp nhận hay không.** Với CRL, tìm được dữ liệu chưa đủ: phải kiểm tra **chữ ký, issuer, thời gian, scope và chính sách thu hồi**, và không bao giờ biến “không tìm được CRL” thành “certificate hợp lệ” mà không có một chính sách bảo mật minh bạch.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cert-path-005">14.5 Kiểm tra thời gian, ràng buộc CA, độ dài đường dẫn và mục đích sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

# 14.5 Kiểm tra thời gian, ràng buộc CA, độ dài đường dẫn và mục đích sử dụng

Khi xác minh chứng thư X.509, kiểm tra rằng **chữ ký trên mỗi chứng thư đúng về mặt toán học** chỉ là một phần của bài toán. Một đường dẫn chứng thư (*certificate path*, hay *certification path*) còn phải đáp ứng nhiều **ràng buộc về thời gian, vai trò CA, số tầng CA trung gian và mục đích sử dụng khóa/chứng thư**. Nếu bỏ qua các ràng buộc này, ứng dụng có thể tin một chứng thư đã hết hạn, cho phép một chứng thư người dùng ký chứng thư khác, hoặc chấp nhận một chứng thư dùng cho email/code signing làm chứng thư máy chủ HTTPS.

Bài này tập trung vào bốn lớp chính trong **PKIX/X.509**:

1. **Thời gian hiệu lực**: `notBefore`, `notAfter`, thời điểm kiểm chứng và tính nhất quán của đồng hồ.
2. **Ràng buộc CA**: extension `BasicConstraints`, bit `cA` và `KeyUsage.keyCertSign` dành cho tổ chức phát hành.
3. **Độ dài đường dẫn**: `pathLenConstraint` giới hạn số CA trung gian **không tự cấp (non-self-issued)** được phép theo sau một CA, không phải số chứng thư bất kỳ.
4. **Mục đích sử dụng**: `KeyUsage`, `ExtendedKeyUsage` (EKU) như `serverAuth`/`clientAuth`, và kiểm tra danh tính dịch vụ riêng biệt như DNS Subject Alternative Name (*SAN*).

Trong Java 17+, `CertificateFactory` tạo đối tượng chứng thư/`CertPath`, còn `CertPathValidator` với thuật toán `PKIX` và `PKIXParameters` thực hiện phần xác thực đường dẫn theo tham số được cấu hình. **Không được đồng nhất PKIX path validation với toàn bộ HTTPS authentication:** danh tính hostname/SAN, chính sách TLS, revocation và authorization vẫn cần được áp dụng đúng nơi.

## 1. Kết quả học tập

Sau bài này, bạn có thể:

- Đọc, phân tích và kiểm tra `X509Certificate.getNotBefore()`, `getNotAfter()`, `checkValidity(Date)`, `getBasicConstraints()`, `getKeyUsage()` và `getExtendedKeyUsage()`.
- Giải thích sự khác nhau giữa **leaf certificate**, **issuing CA**, **root/trust anchor** và **certificate chain**.
- Xác định vì sao leaf phải được cấp bởi CA có quyền ký certificate và vì sao CA trong path phải tuân `BasicConstraints`, `KeyUsage`, `pathLenConstraint` và các giới hạn khác.
- Tính đúng ý nghĩa của `pathLenConstraint = 0`, phân biệt non-self-issued intermediate CAs với end-entity certificate và hiểu trường hợp trust anchor nằm ngoài path.
- Biết `serverAuth`, `clientAuth`, `codeSigning` và `emailProtection` không thể dùng thay nhau chỉ dựa vào tên thuật toán public key.
- Xây dựng một lab keytool + Java 17 tạo root CA test, leaf được ký, root không liên quan; kiểm PKIX tại thời điểm hiện tại và tương lai.
- Viết negative tests cho hết hạn, chưa tới hiệu lực, thiếu CA flag, keyCertSign sai, path quá sâu, EKU không đúng, SAN sai và issuer không được tin.
- Tránh lỗi tự bỏ qua các extension quan trọng hoặc tắt TLS peer/hostname verification để làm handshake thành công.

## 2. Mô hình PKIX: chứng thư nào nằm ở đâu?

Một ví dụ đường dẫn hợp lệ có thể có dạng:

```text
      Trust anchor được cấu hình bên ngoài đường dẫn
      Root CA (đã được trust)
             |
             | phát hành / ký
             v
      Intermediate CA 1 (CA=true, KU=keyCertSign, pathLen >= ... )
             |
             | phát hành / ký
             v
      Intermediate CA 2 (CA=true, KU=keyCertSign, pathLen >= ... )
             |
             | phát hành / ký
             v
      Leaf certificate của server HTTPS
      (CA=false; EKU=serverAuth; SAN đúng hostname)
```

Trong nhiều triển khai PKIX, `CertPath` truyền cho validator bao gồm **leaf rồi các intermediate theo thứ tự**, và **không bao gồm trust anchor**. Trust anchor được cung cấp riêng trong `PKIXParameters`. Việc trust anchor được tin không phải do nó tự ký đúng hoặc vì có mặt trong một file certificate bất kỳ, mà do **cấu hình tin cậy được kiểm soát** của ứng dụng, hệ điều hành hoặc runtime.

### Tách những bước khác nhau

| Bước | Điều được xác minh | Công cụ Java điển hình |
| --- | --- | --- |
| Parse certificate | Dữ liệu có cú pháp X.509 và có thể dựng thành object | `CertificateFactory.generateCertificate` |
| Dựng đường dẫn | Tìm các issuer/intermediate thích hợp dẫn tới trust anchor | `CertPathBuilder("PKIX")` / nguồn `CertStore` |
| Validate đường dẫn | Chữ ký, thời hạn, BasicConstraints, CA usage, path length, policies/name constraints và quy tắc PKIX đã cấu hình | `CertPathValidator("PKIX")` + `PKIXParameters` |
| Xác minh TLS endpoint | Certificate phải đại diện cho **đúng DNS/IP đích**, mục đích TLS server/client và chính sách handshake | JSSE/`X509ExtendedTrustManager`/endpoint identification |
| Thu hồi và chính sách vận hành | OCSP/CRL, distrust, algorithm constraints, truststore updates | PKIX revocation checker/JSSE/security policy |
| Authorization nghiệp vụ | Client/user/tenant có quyền làm hành động này hay không | Application authz/RBAC/ABAC |

**Không dùng** `certificate.verify(issuerPublicKey)` hoặc `certificate.checkValidity()` đơn độc để quyết định “đã tin cậy”. Hai API đó chỉ kiểm một mảnh cụ thể của bài toán.

## 3. Kiểm tra thời gian: notBefore và notAfter

Mỗi certificate X.509 có một khoảng hiệu lực:

```text
notBefore -------------------------- notAfter
           ^ t_valid
```

Tại thời điểm `t_valid`, cần xét khoảng thời gian hợp lệ theo quy tắc PKIX/ứng dụng. Certificate có `t_valid` nằm trước `notBefore` là **chưa có hiệu lực**; nằm sau `notAfter` là **đã hết hạn**. `X509Certificate.checkValidity(Date date)` có thể ném `CertificateNotYetValidException` hoặc `CertificateExpiredException` khi không hợp lệ tại thời điểm chỉ định.

Một certificate được ký bằng thuật toán mạnh và bởi CA đáng tin vẫn **không được chấp nhận làm chứng thư hiện tại** nếu thời gian sử dụng vượt khoảng hiệu lực theo policy.

### Java API và thời điểm kiểm tra

Đoạn sau là **mã minh họa trong method** đã có `X509Certificate certificate`, không phải chương trình độc lập:

```java
Date notBefore = certificate.getNotBefore();
Date notAfter  = certificate.getNotAfter();
certificate.checkValidity(new Date());
```

Đối với path validation, `PKIXParameters.setDate(Date)` cho phép chọn thời điểm mà validator dùng để đánh giá validity của các certificates trong đường dẫn:

```java
PKIXParameters params = new PKIXParameters(trustedAnchors);
params.setDate(Date.from(Instant.now()));
CertPathValidator.getInstance("PKIX").validate(certPath, params);
```

Đây là đoạn minh họa, giả sử `trustedAnchors` là tập `TrustAnchor` được kiểm soát, `certPath` đã được xây đúng cấu trúc và các import/checked exceptions đã được xử lý. **Các cấu hình khác** (đặc biệt revocation, policies, algorithm constraints) vẫn phải được thiết kế theo threat model.

### Các lưu ý tinh tế về thời gian

1. **Kiểm toàn đường dẫn, không chỉ leaf.** Issuing CA/intermediate nằm trong `CertPath` cũng có thời hạn. Việc root trust anchor được xem là đầu vào tin cậy **bên ngoài path** có thể khiến một số extension/validity của chính root không được kiểm giống như chứng thư trong path; cần chính sách quản trị và loại bỏ trust anchors không còn hợp lệ/được phép.
2. **Đồng hồ phải đáng tin.** VM/container có clock lệch, NTP lỗi, timezone nhầm hoặc đồng hồ bị rollback có thể làm chứng thư hợp lệ bị từ chối hoặc chứng thư hết hạn được chấp nhận nhầm. Thời gian X.509 về bản chất là thời điểm tuyệt đối, không phải lấy giờ địa phương của người dùng để tùy ý tính.
3. **Không tắt validity check** chỉ vì certificate hết hạn. Cần renew/reissue hoặc xử lý migration được phê duyệt, không thêm ngoại lệ vô hạn trong code.
4. **Không nhầm thời điểm hiện tại với historical validation.** Kiểm chữ ký đã phát sinh trong quá khứ, long-term signature validation hay timestamp authority có quy tắc riêng về chứng cứ thời gian và trạng thái revocation. Đừng chỉ setDate về ngày cũ do user đưa vào rồi coi chứng thư hiện tại tin được.
5. **Certificate bị thu hồi có thể chưa hết hạn.** `notAfter` không đại diện cho revocation. Ngược lại, certificate hết hạn không được xem là hợp lệ chỉ vì OCSP nói “good” tại một thời điểm khác.
6. **Cập nhật clock và CA chain** phải đồng bộ giữa các node. Một node lệch giờ có thể báo handshake lỗi khác dù cùng truststore.

### Trường hợp minh họa

| Thời điểm kiểm tra | Certificate | Kết quả kỳ vọng |
| --- | --- | --- |
| Trước `notBefore` | Chưa tới hiệu lực | Reject |
| Trong khoảng `notBefore...notAfter` | Hợp lệ về **thời gian** | Có thể đi tiếp; chưa tự tin cả chain |
| Sau `notAfter` | Hết hạn | Reject cho xác thực TLS hiện tại |
| Trong khoảng nhưng issuer đã bị distrust | Leaf vẫn còn hạn | Reject theo trust policy |
| Trong khoảng nhưng KU/EKU sai | Leaf vẫn còn hạn | Reject cho mục đích không được phép |
| Trong khoảng nhưng hostname SAN khác | Certificate vẫn còn hạn | Reject khi kiểm TLS endpoint |

Trong lab cuối bài, leaf được cấp với validity 90 ngày. Test ở hiện tại thành công và test tại **120 ngày sau** thất bại, dù root được cấp 365 ngày. Đây là phép thử tách biệt yếu tố **validity của leaf** khỏi việc root đã được tin.

## 4. BasicConstraints: ai được phép làm CA?

Extension `BasicConstraints` có hai phần logic:

- `cA`: chứng thư public key này có thuộc về một CA được phép phát hành certificate trong cấu trúc đường dẫn hay không.
- `pathLenConstraint`: nếu có, giới hạn số **non-self-issued intermediate CA certificates** có thể theo sau trong một đường dẫn hợp lệ.

Một issuing CA nằm trong đường chứng thư phải được đánh giá với `BasicConstraints` tương ứng, có chứng thư được phép ký certificate và có các ràng buộc phù hợp. Với các profile chuẩn hiện đại, BasicConstraints của CA certificate nên được đánh dấu **critical** theo RFC 5280. Nếu một extension critical không thể được hiểu hoặc xử lý, validation phải từ chối, không bỏ qua như thông tin trang trí.

### Java: getBasicConstraints()

`X509Certificate.getBasicConstraints()` trả:

- **`-1`** nếu certificate **không** phải CA theo BasicConstraints.
- Giá trị **`>= 0`** nếu CA có `pathLenConstraint` hữu hạn, chẳng hạn 0 hoặc 2.
- Một số nguyên rất lớn (ví dụ `Integer.MAX_VALUE` theo JDK API) khi certificate là CA mà **không có giới hạn pathLenConstraint được công bố**.

Vì thế, **`getBasicConstraints() == 0` không có nghĩa “đây là leaf”**. Ngược lại, `0` biểu thị certificate **là CA**, nhưng không cho phép một non-self-issued intermediate CA ở dưới nó trong path đã cho (trừ các ngoại lệ self-issued theo quy tắc PKIX).

Ví dụ:

```text
Leaf end entity:   getBasicConstraints() = -1
Intermediate CA:   getBasicConstraints() = 0  (có thể ký trực tiếp leaf)
Intermediate CA:   getBasicConstraints() = 1  (có thể có 1 non-self-issued CA dưới)
CA không giới hạn: getBasicConstraints() = rất lớn theo provider/API
```

Đây là mô tả ý nghĩa phổ biến; việc đếm thật phải theo thuật toán PKIX, có ngoại lệ self-issued và các constraints khác. Không tự viết path validator chỉ bằng phép so sánh integer.

### Không đủ nếu chỉ kiểm CA=true

Một CA certificate còn cần:

- Chữ ký từ issuer được phép hoặc là trust anchor được cấu hình đúng.
- `KeyUsage` cho phép ký certificate khi extension đó hiện diện.
- Thời hạn và thuật toán hợp lệ.
- `pathLenConstraint`, name constraints, certificate policies và các critical extensions được áp dụng đúng.
- Thứ tự và liên kết `issuer/subject`, signature verification và key usage phù hợp.

Đừng chấp nhận certificate `CA=true` do user upload và tự thêm nó thành trust anchor. **Niềm tin là chính sách bên ngoài**, không phải một boolean tự tuyên bố.

## 5. pathLenConstraint: độ dài đường dẫn không phải số certificate bất kỳ

Đây là một trong những phần dễ hiểu sai nhất của PKIX.

Nếu một CA certificate có `pathLenConstraint=0`, CA đó vẫn có thể ký **end-entity/leaf certificates**. Giá trị 0 chỉ không cho phép **non-self-issued intermediate CA certificates tiếp theo** nằm dưới nó trong đường dẫn theo quy tắc được áp dụng.

### Ví dụ trực quan

```text
Case A — được phép bởi quy tắc pathLen của CA A:

Trust anchor
    |
   CA A [pathLen=0]
    |
   Leaf service.example.test

    Non-self-issued intermediate CA dưới CA A: 0


Case B — vi phạm pathLen=0 của CA A:

Trust anchor
    |
   CA A [pathLen=0]
    |
   CA B [CA=true, non-self-issued]
    |
   Leaf service.example.test

    Non-self-issued intermediate CA dưới CA A: 1 > 0
```

Nếu `CA A` là **trust anchor được cung cấp trực tiếp** chứ không phải certificate nằm trong `CertPath`, một số constraints ghi trong **certificate đại diện cho trust anchor** không nhất thiết được PKIX validation xử lý như constraints của CA trong path. Đây là khác biệt quan trọng! Để kiểm path length trong lab thực sự, hãy đặt CA cần kiểm là **intermediate certificate trong path**, không chỉ lấy một root trusted có `pathLen=0` rồi suy rằng JCA sẽ áp hạn đó lên mọi chain.

### Các yếu tố ảnh hưởng phép đếm

- Đếm **CA trung gian không tự cấp** bên dưới CA đang bị giới hạn, không tính end entity.
- Các **self-issued certificates** có quy tắc xử lý đặc biệt; self-issued không đồng nghĩa tự ký (*self-signed*) trong mọi trường hợp. Đừng tự coi certificate subject=issuer là bằng chứng cryptographic tự ký.
- Path length giới hạn **hệ phân cấp CA**, không phải hạn chế mạng “chỉ 3 hops” hay kích thước byte certificate.
- Một CA có `pathLenConstraint=1` có thể cho phép một non-self-issued CA tiếp theo dưới nó, rồi leaf; nhưng những constraints khác có thể thu hẹp thêm.
- Root/trust anchor có thể được cấu hình bên ngoài path. Đừng lấy giá trị `getBasicConstraints()` của root và tự giả định validator luôn dùng nó như ràng buộc của CA trung gian.
- Name constraints, certificate policies, trust-anchor constraints và các quy tắc algorithm/revocation có thể tiếp tục khiến path bị từ chối dù số CA trung gian nằm trong giới hạn.

### Cấu hình path length khi tạo CA thử nghiệm

Với `keytool` trong JDK phù hợp, CA certificate có thể được tạo với extension:

```text
-ext BC:critical=ca:true,pathlen:0
```

Đây là **một đối số keytool**. Nếu CA này phát hành trực tiếp leaf thì không có intermediate dưới CA. Nếu thử tạo thêm CA B và dựng path qua CA B, cần để **CA A nằm trong CertPath**, không chỉ làm trust anchor, để kiểm chứng `pathLenConstraint` tại vị trí mong muốn.

## 6. KeyUsage: key được phép thực hiện phép toán gì?

Extension `KeyUsage` là bit string chỉ định những chức năng được phép cho public key trong certificate. Đừng đánh đồng việc key toán học **có thể** verify signature với việc certificate **được phép** đóng vai trò CA hoặc dùng cho TLS.

Các bit thường gặp:

| Bit / tên | Ý nghĩa điển hình |
| --- | --- |
| `digitalSignature` | Phục vụ xác minh chữ ký số trong ngữ cảnh được cho phép |
| `nonRepudiation` / `contentCommitment` | Ràng buộc nội dung theo profile; không tự bảo đảm thuộc tính pháp lý |
| `keyEncipherment` | Mã hóa/bọc khóa trong giao thức thích hợp |
| `dataEncipherment` | Mã hóa dữ liệu theo profile cũ/cụ thể |
| `keyAgreement` | Tham gia thiết lập bí mật chung bằng key agreement |
| `keyCertSign` | **Ký chứng thư** khi certificate là CA |
| `cRLSign` | Ký certificate revocation list |
| `encipherOnly` / `decipherOnly` | Cùng với keyAgreement và quy tắc tương ứng |

Java `X509Certificate.getKeyUsage()` trả mảng `boolean[]`; các vị trí cố định theo chuẩn:

```text
index 0 digitalSignature
index 1 nonRepudiation / contentCommitment
index 2 keyEncipherment
index 3 dataEncipherment
index 4 keyAgreement
index 5 keyCertSign
index 6 cRLSign
index 7 encipherOnly
index 8 decipherOnly
```

Nếu extension không hiện diện, `getKeyUsage()` có thể trả **null**. Không truy cập thẳng `getKeyUsage()[5]` trong một trình kiểm chứng generic trước khi kiểm null/độ dài; code lab ở mục 10 truy cập vì **chính lab chủ động phát hành certificate có extension đó**.

### CA cần điều gì để ký certificate?

Đối với một intermediate CA trong path:

- BasicConstraints phải chỉ rõ vai trò CA theo quy tắc PKIX.
- Nếu KeyUsage hiện diện, bit `keyCertSign` phải cho phép ký chứng thư.
- Certificate phải có chữ ký/issuer/path hợp lệ.
- Các hạn chế khác, gồm path length, name constraints, validity, policies, vẫn phải được áp dụng.

**Không** chỉ nhìn `keyCertSign=true` mà bỏ qua `CA=false`. Ngược lại, `CA=true` cùng `KeyUsage` **không** cho phép `keyCertSign` cũng không đủ cho issuing CA trong path. Cần đánh giá **kết hợp**.

### Leaf KeyUsage phụ thuộc TLS mode

Trong TLS hiện đại, leaf certificate thường cần `digitalSignature` cho các thao tác authentication bằng chữ ký. Trong các profile/giao thức lịch sử, một số key/cipher suite yêu cầu `keyEncipherment` hoặc `keyAgreement`. Không áp một công thức độc lập với protocol/version/algorithm cho mọi chứng thư.

Với một RSA public key, có thể verify chữ ký về mặt toán học; nhưng nếu certificate chỉ được cấp cho mục đích khác theo KeyUsage/EKU, ứng dụng **không được** diễn giải nó là TLS server certificate hợp lệ bất chấp policy.

## 7. ExtendedKeyUsage (EKU): mục đích ứng dụng

**EKU** là một tập OID mở rộng phân loại mục đích sử dụng certificate, điển hình:

| Mục đích | OID | Ví dụ |
| --- | --- | --- |
| `serverAuth` | `1.3.6.1.5.5.7.3.1` | Xác thực TLS server |
| `clientAuth` | `1.3.6.1.5.5.7.3.2` | Xác thực TLS client khi mTLS |
| `codeSigning` | `1.3.6.1.5.5.7.3.3` | Ký phần mềm/code |
| `emailProtection` | `1.3.6.1.5.5.7.3.4` | Bảo vệ email |
| `timeStamping` | `1.3.6.1.5.5.7.3.8` | Ký timestamp theo quy tắc dịch vụ |
| `OCSPSigning` | `1.3.6.1.5.5.7.3.9` | Ký OCSP responses theo profile |

Trong Java, `X509Certificate.getExtendedKeyUsage()` trả danh sách `List<String>` OIDs hoặc `null` khi extension không có. Sự **không hiện diện** không có một kết quả allow/deny phổ quát cho mọi ứng dụng; cần áp chính sách của protocol/CA profile/TLS stack. Khi extension có mặt, intended usages phải được kiểm theo RFC/ứng dụng. Các OID chung như `anyExtendedKeyUsage` có semantics đặc thù; không lấy đó làm giấy phép bỏ kiểm mục đích.

### Vì sao KeyUsage và EKU cần kết hợp?

Một leaf có:

```text
BasicConstraints: CA=false
KeyUsage: digitalSignature
ExtendedKeyUsage: serverAuth
SubjectAlternativeName: DNS:service.example.test
```

là một **hồ sơ thích hợp làm điểm khởi đầu** cho TLS server certificate trong ngữ cảnh phù hợp, nhưng vẫn phải có chain đáng tin, thời gian hợp lệ, algorithm constraints, revocation/distrust policy và **hostname thật sự khớp SAN**.

Nếu leaf có `EKU=clientAuth` nhưng endpoint đang dùng nó làm TLS server, phải từ chối theo TLS server policy. Nếu leaf có `EKU=codeSigning`, không dùng nó như TLS server certificate chỉ vì cùng thuật toán RSA/ECDSA.

**Mục đích không thay thế identity:** `serverAuth` nói loại tác vụ, **không** nói tên DNS nào được phép. `SAN`/endpoint identification là bước riêng. Bên nhận chứng thư mTLS client cũng cần map danh tính certificate đã được xác thực sang principal/tenant có quyền.

### Một cảnh báo về API

Gọi `cert.getExtendedKeyUsage()` và tự so OID trong một demo cho thấy **cách đọc dữ liệu**; nó **không đồng nghĩa** code đã thực hiện toàn bộ JSSE TLS server authentication. Trong sản phẩm thật, dùng `X509ExtendedTrustManager`/TLS stack hoặc API được audit để bảo đảm EKU, KU và hostname được kiểm **đúng giao thức**.

## 8. Name Constraints và certificate policies: những ràng buộc không được bỏ qua

Dù tên bài tập trung vào thời gian, CA, path length và usage, một path validator chuẩn còn xử lý các extension quan trọng khác.

**Name Constraints:** CA có thể bị giới hạn phạm vi định danh được phép phát hành cho những chứng thư phía dưới, chẳng hạn tập DNS/email/IP đã quy định. Chúng thường xuất hiện trong intermediate CA và phải được áp dụng theo thuật toán PKIX. **Không** thay thế bằng một phép so sánh `String.endsWith()` đơn giản: DNS suffix, punycode, wildcard, Unicode và matching rules có các chi tiết bảo mật.

**Certificate Policies / policy constraints:** CA/certificate có thể mang OID về chính sách cấp phát, explicit policy, mapping và ràng buộc inhibit; ứng dụng có threat model đặc biệt có thể yêu cầu OID nhất định. Một path có chữ ký đúng và root trusted **vẫn có thể bị loại** nếu policy yêu cầu không đáp ứng.

**Critical extensions:** RFC 5280 yêu cầu không bỏ qua critical extension mà path processing không hiểu. Nếu tự viết “verify chữ ký rồi chấp nhận” bằng cách chỉ gọi `PublicKey` và `Signature`, bạn bỏ qua BasicConstraints, KU, path length, name constraints và những extension có tính bắt buộc khác. Hãy dùng PKIX engine đã được duy trì.

## 9. Java API: đọc metadata và xác minh PKIX là hai việc khác nhau

Các API chính:

| Java API | Vai trò | Điểm cần lưu ý |
| --- | --- | --- |
| `CertificateFactory.getInstance("X.509")` | Parse X.509 DER/PEM thích hợp, tạo `CertPath` | Parse không lập trust |
| `X509Certificate.checkValidity(Date)` | Kiểm thời hạn một certificate | Không kiểm chain hay hostname |
| `X509Certificate.getBasicConstraints()` | Đọc CA/pathLen metadata | `-1` là non-CA; root anchor có semantics đặc biệt |
| `X509Certificate.getKeyUsage()` | Đọc KeyUsage bitset | Nullable, thứ tự bit chuẩn |
| `X509Certificate.getExtendedKeyUsage()` | Đọc EKU OIDs | Nullable; cần protocol-specific policy |
| `CertPathBuilder.getInstance("PKIX")` | Tìm path tới trust anchor theo tham số và certificate sources | Path xây được không thay endpoint identification |
| `CertPathValidator.getInstance("PKIX")` | Validate path đã được dựng | Cần trusted anchors, date, revocation/policy |
| `PKIXParameters` | Cấu hình trust anchors, thời điểm, revocation, cert stores, policy | Không lấy trust anchors từ user request |
| `TrustAnchor` | Đại diện root/trust anchor tin cậy | Anchor được cấu hình ngoài path; không tự suy trust từ certificate |
| `PKIXCertPathChecker` | Mở rộng xử lý path theo chuẩn API | Không viết custom checker để bỏ qua validation |
| `X509ExtendedTrustManager` | TLS chain validation với context socket/engine | Gắn với JSSE để có protocol context |

Ví dụ đọc metadata (mã minh họa, không độc lập):

```java
int basic = cert.getBasicConstraints();
boolean isCa = basic >= 0;

boolean[] ku = cert.getKeyUsage();
boolean canSignCertificates = ku != null && ku.length > 5 && ku[5];

List<String> eku = cert.getExtendedKeyUsage();
boolean explicitServerAuth = eku != null
        && eku.contains("1.3.6.1.5.5.7.3.1");

cert.checkValidity(new Date());
```

Cần import `java.util.List`, `java.util.Date` và giả sử `X509Certificate cert` có sẵn; một số lời gọi ném checked exceptions. **Đây là kiểm metadata riêng lẻ**, không thay `PKIX` path validation hoặc TLS endpoint verification. Việc `getBasicConstraints() >= 0` không chứng minh CA là một issuer được policy cấp quyền.

## 10. Lab Java 17+ tự tạo root, leaf và thử xác minh

Đây là một lab **hoàn toàn dùng keytool + JDK 17+**, không cần website hoặc certificate của đối tác. Chúng ta tạo:

- `root.pem`: self-signed **test root CA** RSA 2048, `BasicConstraints CA=true, pathLen=0`, `KeyUsage keyCertSign,cRLSign`, hiệu lực 365 ngày.
- `leaf.pem`: chứng thư dịch vụ `service.example.test` ký bởi root, hiệu lực 90 ngày, `CA=false`, `KeyUsage digitalSignature`, `EKU serverAuth`, `SAN DNS:service.example.test`.
- `other.pem`: một **test root khác** không ký leaf.

Java tạo một `CertPath` chỉ chứa leaf, cấu hình root làm trust anchor, validate tại ngày hiện tại và tại 120 ngày sau. Test root không liên quan phải thất bại. **Root pathLen=0 trong lab được đọc/hiển thị nhưng không được coi là phép thử thực thi pathLenConstraint cho intermediate**, vì root ở đây là trust anchor nằm ngoài `CertPath`.

### 10.1 Sinh certificate lab bằng keytool

Chạy trong **thư mục thử nghiệm riêng, không phải thư mục chứa keystore sản xuất**. Các key/password được sinh chỉ phục vụ lab. Mật khẩu `changeit` bên dưới là **mật khẩu demo công khai, tuyệt đối không dùng cho khóa hoặc môi trường thật**. Trong production, dùng prompt/secret manager thay vì password trên command line.

Các lệnh sau chạy với **bash/POSIX shell** (mỗi `\` cuối dòng là tiếp dòng):

```bash
# Root CA thử nghiệm, ký certificate và CRL, pathLenConstraint=0
keytool -genkeypair -alias root -keyalg RSA -keysize 2048 \
  -sigalg SHA256withRSA -dname "CN=Training Root CA" \
  -validity 365 -ext "BC:critical=ca:true,pathlen:0" \
  -ext "KU:critical=keyCertSign,cRLSign" \
  -keystore root.p12 -storetype PKCS12 \
  -storepass changeit -keypass changeit -noprompt

keytool -exportcert -rfc -alias root \
  -keystore root.p12 -storepass changeit -file root.pem

# Sinh keypair riêng cho leaf, tạo CSR
keytool -genkeypair -alias leaf -keyalg RSA -keysize 2048 \
  -sigalg SHA256withRSA -dname "CN=service.example.test" \
  -validity 90 -keystore leaf.p12 -storetype PKCS12 \
  -storepass changeit -keypass changeit -noprompt

keytool -certreq -alias leaf -keystore leaf.p12 \
  -storepass changeit -file leaf.csr

# Root ký certificate leaf với KU/EKU/SAN theo profile lab
keytool -gencert -alias root -keystore root.p12 \
  -storepass changeit -infile leaf.csr -outfile leaf.pem \
  -rfc -validity 90 -ext "BC:critical=ca:false" \
  -ext "KU:critical=digitalSignature" \
  -ext "EKU=serverAuth" \
  -ext "SAN=dns:service.example.test"

# Root khác, không ký leaf: đối chứng âm
keytool -genkeypair -alias other -keyalg RSA -keysize 2048 \
  -sigalg SHA256withRSA -dname "CN=Unrelated Root CA" \
  -validity 365 -ext "BC:critical=ca:true,pathlen:0" \
  -ext "KU:critical=keyCertSign,cRLSign" \
  -keystore other.p12 -storetype PKCS12 \
  -storepass changeit -keypass changeit -noprompt

keytool -exportcert -rfc -alias other \
  -keystore other.p12 -storepass changeit -file other.pem
```

Nếu chạy PowerShell, nhập mỗi lệnh thành một dòng hoặc dùng cú pháp tiếp dòng PowerShell phù hợp; **không** dán dấu `\` POSIX ở cuối dòng. Không import các root thử nghiệm này vào `cacerts`/truststore sản xuất. Xóa toàn bộ test key files theo chính sách sau lab. Các file `root.p12`/`leaf.p12`/`other.p12` có **private key demo**, còn `root.pem`/`leaf.pem`/`other.pem` chỉ là public certificates.

### 10.2 Java source độc lập

Lưu thành **`PkixPolicyDemo.java`** trong **cùng thư mục** chứa ba certificate PEM vừa tạo:

```java
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class PkixPolicyDemo {
    static X509Certificate readCertificate(String name) throws Exception {
        try (var stream = Files.newInputStream(Path.of(name))) {
            return (X509Certificate) CertificateFactory
                    .getInstance("X.509").generateCertificate(stream);
        }
    }

    static boolean validate(CertPath path, X509Certificate root, Date at)
            throws Exception {
        PKIXParameters params = new PKIXParameters(
                Set.of(new TrustAnchor(root, null)));
        params.setRevocationEnabled(false); // Lab only.
        params.setDate(at);
        try {
            CertPathValidator.getInstance("PKIX").validate(path, params);
            return true;
        } catch (CertPathValidatorException ex) {
            return false;
        }
    }

    static boolean hasDnsSan(X509Certificate cert, String name)
            throws Exception {
        var sans = cert.getSubjectAlternativeNames();
        if (sans == null) return false;
        for (var entry : sans) {
            if (Integer.valueOf(2).equals(entry.get(0))
                    && name.equals(entry.get(1))) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) throws Exception {
        X509Certificate root = readCertificate("root.pem");
        X509Certificate leaf = readCertificate("leaf.pem");
        X509Certificate unrelated = readCertificate("other.pem");
        CertPath path = CertificateFactory.getInstance("X.509")
                .generateCertPath(List.of(leaf));

        System.out.println("Root is CA: " + (root.getBasicConstraints() >= 0));
        System.out.println("Root pathLenConstraint: " + root.getBasicConstraints());
        System.out.println("Root keyCertSign: " + root.getKeyUsage()[5]);
        System.out.println("Leaf is CA: " + (leaf.getBasicConstraints() >= 0));
        System.out.println("Leaf digitalSignature: " + leaf.getKeyUsage()[0]);
        System.out.println("Leaf serverAuth EKU: " + leaf.getExtendedKeyUsage()
                .contains("1.3.6.1.5.5.7.3.1"));
        System.out.println("Expected DNS SAN present: " +
                hasDnsSan(leaf, "service.example.test"));
        System.out.println("Path valid today: " +
                validate(path, root, Date.from(Instant.now())));
        System.out.println("Path valid after 120 days: " +
                validate(path, root,
                        Date.from(Instant.now().plus(120, ChronoUnit.DAYS))));
        System.out.println("Path with unrelated root valid: " +
                validate(path, unrelated, Date.from(Instant.now())));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac --release 17 PkixPolicyDemo.java
java PkixPolicyDemo
```

**Stdout đã kiểm chứng trong lab sử dụng các lệnh phía trên:**

```text
Root is CA: true
Root pathLenConstraint: 0
Root keyCertSign: true
Leaf is CA: false
Leaf digitalSignature: true
Leaf serverAuth EKU: true
Expected DNS SAN present: true
Path valid today: true
Path valid after 120 days: false
Path with unrelated root valid: false
```

**Điều kiện của output:** các chứng thư phải vừa được tạo tại thời điểm chạy lab, provider/JDK hỗ trợ các extension/algorithm trong keytool và không có policy bảo mật bổ sung chặn RSA-2048/SHA256withRSA. Với chứng thư cũ được giữ trên disk đến sau 90 ngày, dòng `Path valid today` sẽ thay đổi — đó là hành vi đúng của validity check. Trên provider hoặc security policy khác, các tập constraints có thể làm validate thất bại. **Không** coi output là một chuỗi cố định cho mọi thời điểm và mọi runtime.

### 10.3 Vì sao từng dòng xuất hiện?

1. `Root is CA: true` vì root được tạo với `BasicConstraints.cA=true`.
2. `Root pathLenConstraint: 0` là metadata của root. Trong test này root là **TrustAnchor**, không phải intermediate trong path; đó **không phải bằng chứng** validator đã áp pathLen=0 lên một intermediate chain.
3. `Root keyCertSign: true` là `KeyUsage` bit 5 do keytool cấp cho root.
4. `Leaf is CA: false` vì leaf có `BasicConstraints.cA=false`; `getBasicConstraints()` trả `-1`.
5. `Leaf digitalSignature: true` là KU bit 0.
6. `Leaf serverAuth EKU: true` là đọc EKU OID `1.3.6.1.5.5.7.3.1`. **Chỉ đọc OID**, chưa phải một phép thử TLS handshake.
7. `Expected DNS SAN present: true` là tìm **chuỗi literal** trong SAN của chứng thư do lab tạo. Nó không thay thế hostname verification của JSSE cho mọi loại DNS/IP, wildcard hoặc IDN.
8. `Path valid today: true` vì leaf vừa được cấp, chữ ký từ đúng root anchor, profile hợp lệ trong điều kiện lab, revocation checking đã **tắt rõ ràng cho lab**.
9. `Path valid after 120 days: false` vì leaf chỉ được cấp 90 ngày; root 365 ngày. Việc kiểm path bị từ chối tại mốc tương lai vì validity của leaf.
10. `Path with unrelated root valid: false` vì root thứ hai không ký leaf và không có đường phù hợp tới anchor ấy.
11. `validate` chỉ bắt `CertPathValidatorException` để chuyển validation failure thành `false`. Lỗi I/O, parse, provider, sai cấu hình hoặc lỗi chương trình vẫn được truyền ra; không nên đánh đồng mọi exception với “certificate không đáng tin”.
12. `hasDnsSan` chỉ phục vụ bài học; toàn bộ chứng thư trong production phải được xác minh bằng TLS endpoint identification chính quy và chính sách CA được cấu hình an toàn.

### 10.4 Điểm bảo mật cố ý giới hạn

`PKIXParameters.setRevocationEnabled(false)` là thiết lập **chỉ phục vụ lab offline không có CRL/OCSP**. Nó **không** phải mẫu cấu hình production. Việc bật/tắt revocation và hành vi khi OCSP/CRL không truy cập được phải do threat model, policy và infrastructure quyết định.

Root CA do `keytool` sinh là **root tự ký mới**. Chỉ tin nó trong lab vì chúng ta chủ động cấu hình `TrustAnchor(root, null)`. Chữ ký tự ký hợp lệ hoặc tên `Training Root CA` không làm root đáng tin với các ứng dụng khác. **Đừng import** các root này vào default `cacerts`.

`CertificateFactory.generateCertPath(List.of(leaf))` tạo một path đã được biết trước. Với chain nhiều intermediate và path xây dựng từ certificate store, nên dùng `CertPathBuilder`/`CertStore` hoặc JSSE/TLS stack được duy trì, không tự đoán issuer từ subject string.

## 11. Thử thực thi pathLenConstraint cho intermediate đúng cách

Lab chính kiểm thời gian, CA metadata, KU/EKU và trust anchor. Muốn thử pathLenConstraint một cách **thực sự**, xây chain có ít nhất một intermediate:

```text
Root R           (TrustAnchor; ngoài CertPath)
   |
   v
Intermediate A   (BasicConstraints CA=true, pathLenConstraint=0)
   |
   +--> Leaf L1  (CA=false)        : depth OK
   |
   +--> Intermediate B (CA=true, non-self-issued)
                |
                +--> Leaf L2       : vượt pathLen A=0
```

Thiết kế test:

1. Sinh root R test, import vào trust anchors của validator (chỉ test).
2. Sinh CA A có `CA=true, pathLen=0, KU=keyCertSign`, ký bởi root R.
3. Sinh leaf L1 ký trực tiếp bởi A. Validate `[L1,A]` với R anchor: nếu mọi điều kiện khác hợp lệ, **pathLen riêng của A** cho phép vì dưới A không có non-self-issued CA.
4. Sinh intermediate CA B có `CA=true` ký bởi A và leaf L2 ký bởi B.
5. Validate `[L2,B,A]` với R anchor: trong trường hợp không có ngoại lệ self-issued và các test cert được tạo bình thường, **pathLen A=0 bị vi phạm**.
6. Làm một test đối chứng `A.pathLen=1` để xác minh path `[L2,B,A]` không bị loại vì path length (các constraints còn lại vẫn phải đúng).
7. Test một intermediate bị thiếu `BasicConstraints CA=true` hoặc thiếu `KeyUsage.keyCertSign` trong khi KU hiện diện: path có thể được ký đúng về mặt RSA/ECDSA nhưng **vẫn phải bị từ chối** vì CA không được phép ký chứng thư.
8. Đừng đặt A làm `TrustAnchor` khi muốn test ràng buộc của chính chứng thư A trong path; cách đó có thể bỏ qua việc kiểm constraints của trust anchor certificate như một intermediate.

Bài tập này cần **test certificates có chủ đích**, không thể kết luận từ một giá trị `getBasicConstraints()` duy nhất. Ưu tiên thư viện/PKIX engine chuẩn để xử lý non-self-issued CA counting, self-issued exceptions, name constraints và certificate policies.

## 12. Kiểm mục đích TLS server/client theo đúng tầng

Một handshake HTTPS không chỉ hỏi “có path từ leaf lên trusted root?” mà còn:

```text
Peer sends leaf + intermediate(s)
         |
         v
Build & validate PKIX path
         |
         v
Check server/client TLS certificate profile
  - KU appropriate for handshake/algorithm
  - EKU appropriate: serverAuth vs clientAuth
  - algorithm/key sizes/sig schemes allowed
         |
         v
Check endpoint identity (hostname/IP in SAN)
         |
         v
Check revocation/distrust policies as configured
         |
         v
TLS authentication success
         |
         v
Application authorization still required
```

Thứ tự xử lý thực tế có thể khác giữa TLS provider/handshake, nhưng **các kiểm soát cần thiết không được bị bỏ sót**.

### HTTPS client

Ví dụ client kết nối `https://api.example.test`. Server có thể trình leaf chứa:

```text
SAN DNS: api-other.example.test
EKU: serverAuth
CA/path: valid to a trusted root
```

Mặc dù chain và EKU có thể đúng, **hostname không khớp** nên phải từ chối kết nối. Không dùng `getSubjectX500Principal().getName().contains("api")` để tự xác minh tên. Với HTTPS, sử dụng endpoint identification theo TLS stack, bao gồm xử lý wildcard và IDN theo chuẩn được hỗ trợ.

### mTLS server xác thực client

Trong mTLS, server kiểm client certificate với CAs được phép và policy khác:

- Leaf phù hợp `clientAuth` và KU của TLS profile.
- Đường chứng thư hợp lệ tới trust anchor dành riêng cho client identities.
- Danh tính client trong SAN/Subject theo profile được cấp và ánh xạ đến principal tin cậy.
- **Authorization** quyết định principal có quyền làm API action, tenant/resource nào.

Thêm một CA public vào truststore dùng cho mTLS client authentication có thể mở rộng khả năng một số client certificates được chấp nhận ngoài ý định. Chỉ trust đúng issuer CAs và profile mà hệ thống cấp phép.

### Code signing và email

Một certificate có `EKU=codeSigning` hoặc `emailProtection` không tự hợp lệ làm TLS server. Các trust anchor/policy dành cho ký phần mềm/email cũng có thể khác root trust cho HTTPS. Đừng dùng một truststore chung không kiểm soát mục đích chỉ vì đều là X.509.

## 13. Thời hạn của CA và trust anchor: phân biệt policy và PKIX engine

Đây là bẫy tinh tế khi kiểm thời gian:

- **End-entity/leaf** và **intermediates trong `CertPath`** phải đáp ứng validity theo PKIX time.
- **Trust anchor** thường được cấu hình riêng và có thể không được kiểm tất cả certificate extensions/validity như một certificate nằm trong path. Trust anchor có thể được khai báo bằng CA name/public key hoặc certificate đại diện.
- Vì vậy, nếu một trusted root hết hạn hay policy đã distrust, không thể chỉ kỳ vọng mọi `CertPathValidator` invocation tự xử lý theo đúng chính sách root program của bạn. Phải quản lý truststore, root expiry, algorithm policy và revocation/distrust riêng khi thích hợp.
- Với path building, có thể tồn tại nhiều đường hợp lệ tới các anchors khác nhau; một root hết hạn/bị loại không luôn có nghĩa mọi path thất bại nếu cross-sign/alternate path vẫn được phép.
- Khi kiểm chứng lịch sử, chuẩn/chứng cứ như timestamp, archived validation data và policy cho chữ ký cũ khác với TLS live authentication.

**Điều không nên làm:** đặt root certificate do remote peer cung cấp vào `new TrustAnchor(root,null)` rồi validate leaf, và kết luận remote peer đáng tin. Đó là lỗi **“trust-on-input”**: attacker tự cung cấp cả root và chain, nên việc toán học đúng chỉ xác thực dữ liệu do attacker tự chọn.

## 14. Revocation và thời gian đánh giá: không thể bỏ qua trong threat model

Một chứng thư có thể **chưa hết hạn nhưng đã bị thu hồi** vì key lộ, CA cấp sai hoặc danh tính không còn đúng. CRL và OCSP là các cơ chế để đánh giá trạng thái thu hồi theo profile. Java PKIX có `PKIXRevocationChecker` và các tham số/cert stores liên quan, nhưng không được mặc định rằng mọi JSSE client bật revocation theo cùng cách.

Cần quyết định:

1. Bên nào cung cấp CRL/OCSP, URL và cơ chế caching?
2. Policy **fail-closed** hay **soft-fail** khi responder/CRL không truy cập được?
3. Có OCSP stapling hoặc cơ chế distrust do nhà cung cấp không?
4. Mốc kiểm chứng là hiện tại hay mốc lịch sử được ràng buộc bởi timestamp đáng tin?
5. Với private PKI, có PKI operations bảo đảm revoke/CRL issuance đúng thời hạn?
6. Có kiểm intermedates và trust anchors theo chính sách CA riêng?
7. Bảo vệ trước request gây network dependency/unbounded I/O khi path building hoặc revocation kiểm nguồn ngoài.

Nếu một hệ thống **có yêu cầu** phải kiểm revocation mà tắt `setRevocationEnabled(false)` để tránh latency/outage, đó không phải “tối ưu” chấp nhận được. Lab tắt vì tạo certificate không có hạ tầng CRL/OCSP; production phải theo policy.

## 15. Khi validate path thất bại: đọc lý do đúng cách

Java `CertPathValidatorException` có thể cung cấp `getReason()`, `getIndex()` (vị trí certificate trong path) và nguyên nhân gốc. Các lý do thường thuộc họ:

- `BasicReason.EXPIRED` hoặc `NOT_YET_VALID`
- `BasicReason.INVALID_SIGNATURE`
- `BasicReason.REVOKED` hoặc `UNDETERMINED_REVOCATION_STATUS`
- `PKIXReason.NOT_CA_CERT`
- `PKIXReason.PATH_TOO_LONG`
- `PKIXReason.INVALID_KEY_USAGE`
- `PKIXReason.INVALID_POLICY` / `INVALID_NAME` / `UNRECOGNIZED_CRIT_EXT`

Tên reason cụ thể và exception mapping có thể khác theo provider/JDK/trường hợp. Đừng dựa vào **một chuỗi exception text** như hợp đồng API ổn định; hãy log reason/index ở kênh nội bộ có kiểm soát và đối chiếu path thực tế.

### Bảng điều tra sự cố

| Lỗi/triệu chứng | Nguyên nhân có thể | Kiểm tra trước |
| --- | --- | --- |
| Certificate hết hạn | Leaf/intermediate đã qua `notAfter` | Runtime clock, validity từng cert, chain đang dùng |
| Chưa tới hiệu lực | `notBefore` trong tương lai | Clock/NTP, provisioning/rollout time |
| Not a CA | Intermediate có `CA=false` hoặc thiếu BasicConstraints đúng chuẩn | Certificate profile của issuing CA |
| Invalid key usage | Issuer KU không cho `keyCertSign` | KU bits và certificate issuing policy |
| Path too long | CA trung gian vượt `pathLenConstraint` | Đếm non-self-issued CA dưới constrained CA trong path |
| Không có valid path | Issuer thiếu/cross-sign sai/root không trusted | Truststore, intermediates, signature, path builder |
| Invalid purpose | Leaf chỉ có `clientAuth`/`codeSigning` cho TLS server | EKU và TLS certificate profile |
| Hostname mismatch | SAN không chứa endpoint DNS/IP tương ứng | URL/SNI, SAN, TLS endpoint identification |
| Revoke/OCSP lỗi | Certificate bị thu hồi hoặc trạng thái không xác định | Revocation policy, OCSP/CRL, time/caching |
| Thuật toán bị cấm | RSA key size/signature algorithm không đáp policy | Security properties, JDK policy và CA migration |
| Root trusted nhưng vẫn fail | Root không thay thế kiểm KU/EKU/time/name constraints trong path | Full chain và policy |
| Cert parse được nhưng fail | Parse không tương đương validate | Phân loại lỗi parse vs path vs TLS purpose |

Khi xảy ra lỗi, **không** bật “trust all”, tắt hostname verification, bỏ kiểm CA/KU/EKU hoặc đặt `PKIXParameters.setDate` giả để “fix”. Phải sửa chứng thư, CA chain, truststore hoặc thời gian hệ thống theo quy trình được duyệt.

## 16. Thiết kế kiểm thử toàn diện

Bộ test nên bao gồm cả **đường dẫn hợp lệ** và **đường dẫn bị từ chối có chủ đích**:

| Test | Đầu vào | Kỳ vọng |
| --- | --- | --- |
| Path hợp lệ | Leaf TLS server, issuer CA hợp lệ, đúng root | PKIX pass dưới policy đã thiết lập |
| Chưa tới hiệu lực | `date` trước `leaf.notBefore` | Reject |
| Hết hạn | `date` sau `leaf.notAfter` | Reject |
| Leaf không có trusted issuer | Dùng unrelated root làm anchor | Reject |
| CA=false dùng ký certificate | Issuer intermediate thiếu CA flag | Reject |
| CA có KU nhưng thiếu keyCertSign | Intermediate có KU không cho ký cert | Reject |
| CA A pathLen=0 ký leaf trực tiếp | `[leaf,A]` với A là intermediate | PathLen riêng của A không chặn leaf |
| CA A pathLen=0 ký CA B rồi leaf | `[leaf,B,A]`, A/B là intermediates | Reject vì path length |
| CA A pathLen=1 ký CA B rồi leaf | Một non-self-issued CA B dưới A | PathLen riêng A cho phép; còn phải qua các điều kiện khác |
| EKU serverAuth | Leaf dùng làm TLS server | Được xét tiếp nếu KU/chain/SAN đúng |
| EKU clientAuth-only | Leaf dùng cho HTTPS server | TLS server policy reject |
| SAN hostname khác | Chain và KU/EKU đúng nhưng DNS khác | TLS endpoint reject |
| CA name constraints | Leaf ngoài domain được phép | PKIX reject |
| Critical extension không nhận diện | Issuer/leaf chứa critical extension không được hỗ trợ | Reject |
| Revoked certificate | CA công bố trạng thái revoked đúng policy | Reject |
| Root không đáng tin | User tự cung cấp self-signed root | Không thêm thành trust anchor |
| Trust root hợp lệ nhưng leaf hết hạn | Root vẫn còn hạn | Reject do leaf |
| Time skew | Hai nodes lệch giờ | Phát hiện bằng monitoring; không bỏ validation |
| Alternate chain | Cùng leaf có nhiều path tới anchors được phép | Chấp nhận/từ chối dựa trên full path policy |
| mTLS wrong client tenant | Client cert hợp lệ nhưng không có quyền nghiệp vụ | Reject ở authorization |
| CA rotation | Trust bundle có Old/New trong giai đoạn chuyển đổi | Được định nghĩa theo policy, không dùng trust-all |
| Disabled revocation in lab | CA test offline, không có OCSP/CRL | Chỉ được dùng trong lab với ngoại lệ rõ ràng |

**Quan trọng:** test CA pathLen phải có **ít nhất một intermediate trong `CertPath` để kiểm chính sách của intermediate đó**. Test chỉ root (TrustAnchor) và leaf không chứng minh đã thử thuật toán path length bên dưới root.

### Golden vectors và interoperability

Không chỉ kiểm thử cùng thư viện đã tạo chứng thư. Dùng `keytool`, OpenSSL hoặc PKI lab được kiểm soát để tạo fixtures độc lập; lưu **public certificate DER/PEM** và mô tả quá trình sinh/phiên bản tool. Không commit private key thực vào repository. Các test nên dựng lại truststore đúng CA mà không sửa `cacerts` của máy developer.

## 17. Thực tế triển khai trong JSSE / HTTPS

Java HTTPS client thường sử dụng JSSE trust manager và **endpoint identification** theo API. Khi bạn viết custom `SSLContext` hoặc `X509TrustManager`, đừng vô tình làm mất bước hostname verification/mục đích TLS; một generic `CertPathValidator.validate` không đủ để tự cài HTTPS client an toàn.

Mô hình khuyến nghị:

```text
HTTPS client configured with trusted root store
        |
        v
JSSE handshake / trusted CA path
        |
        v
certificate time + CA BasicConstraints + pathLen + KeyUsage
        |
        v
TLS purpose (EKU/KU) and endpoint identification (SAN)
        |
        v
connection authenticated
        |
        v
application checks service identity, authz and resource permissions
```

Đây là sơ đồ chức năng; trình tự nội bộ thay đổi theo TLS stack/provider. Với `SSLSocket` sử dụng trực tiếp, xem `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")` theo use case. Với `java.net.http.HttpClient`, dùng cơ chế HTTPS/endpoint identity mặc định được hỗ trợ thay vì tạo trust manager “checkServerTrusted rỗng”.

**CA scope:** Nên cấp root trust trong phạm vi client/tenant/protocol thích hợp, không thêm mọi intermediate/CA do peer gửi vào truststore. Thay đổi truststore làm thay đổi những chain nào có thể được chấp nhận, không chỉ một thao tác parse file.

## 18. Những lỗi nhận thức thường gặp

1. **“Chữ ký đúng thì certificate được tin.”** Sai: phải có trust anchor đáng tin và path policy.
2. **“CA=true là đủ để ký cert.”** Sai: còn keyCertSign nếu KU hiện diện, thời gian, pathLen, name constraints, signature, algorithm policy.
3. **“pathLen=0 tức không được ký leaf.”** Sai: leaf không phải non-self-issued intermediate CA.
4. **“Độ dài đường dẫn = số certificate trong danh sách.”** Sai: tính theo các non-self-issued CA thích hợp, không tính leaf và có quy tắc self-issued.
5. **“Đặt root pathLen=0 rồi chạy path root-anchor + leaf là đã test pathLen.”** Không chứng minh được: root anchor có thể nằm ngoài path validation constraints.
6. **“Leaf có serverAuth thì hostname đúng.”** Sai: serverAuth là mục đích, SAN/endpoint identity là danh tính.
7. **“Leaf có keyCertSign là CA.”** Sai: cần BasicConstraints CA=true và full PKIX rules.
8. **“X509EncodedKeySpec là certificate.”** Sai: là public-key SPKI spec; `CertificateFactory` parse certificate.
9. **“Hết hạn nhưng vẫn ký đúng thì có thể bỏ qua ngày.”** Không được cho TLS live auth.
10. **“Root trong cacerts thì mọi cert dưới root đều hợp lệ.”** Sai: leaf/intermediate có thể fail KU/EKU/validity/hostname/algorithm/revocation.
11. **“getExtendedKeyUsage()==null nghĩa là luôn được dùng cho bất kỳ tác vụ.”** Không tự đúng; tùy certificate profile/TLS policy.
12. **“Một check getBasicConstraints() đủ kiểm path.”** Sai: phải có path processing, chữ ký và policy.
13. **“Tắt revocation là luôn an toàn vì cert chưa hết hạn.”** Sai: compromise có thể xảy ra trước expiry; revocation theo threat model.
14. **“Tự xây TrustAnchor từ root peer gửi.”** Sai: attacker có thể gửi root của mình; trust anchor phải từ cấu hình tin cậy.
15. **“Một Java call verifyIssuer là toàn bộ PKIX.”** Sai: chỉ là một kiểm chữ ký số trên một certificate.
16. **“Hostname mismatch chỉ là lỗi DNS.”** Sai: tên được kiểm giữa requested endpoint và certificate identity; có thể là dấu hiệu MITM.
17. **“Chấp nhận client certificate mTLS thì cho luôn quyền admin.”** Sai: vẫn cần mapping principal/tenant và authorization.
18. **“Tất cả JDK/provider báo cùng một exception text.”** Sai: dùng typed APIs/reasons/index và test với runtime thực tế.
19. **“Có thể dùng ngày do client truyền lên để xác minh HTTPS.”** Sai: thời điểm cần đến từ policy/nguồn tin cậy.
20. **“Bỏ critical extensions không hiểu giúp tương thích tốt hơn.”** Sai: phá ràng buộc an ninh; cần dùng PKIX implementation phù hợp.

## 19. Bài tập thực hành

### Bài 1 — Đọc metadata

Chạy lab mục 10 và giải thích tại sao `root.getBasicConstraints() == 0` nhưng `leaf.getBasicConstraints() == -1`. In `getNotBefore/getNotAfter` cho từng certificate; tự tạo `Date` trước và sau validity để quan sát `checkValidity`.

### Bài 2 — Hết hạn nhưng chữ ký đúng

Giữ nguyên leaf do root ký. Đổi thời gian test thành 120 ngày sau. Giải thích vì sao chữ ký toán học vẫn đúng nhưng PKIX từ chối. Thử thay đổi đồng hồ hệ thống trong **môi trường lab cách ly** rồi khôi phục; không thay NTP/clock trên máy production.

### Bài 3 — Issuing CA không được phép

Tạo một intermediate có `BasicConstraints CA=false` nhưng thử dùng private key của nó ký một leaf. Có thể tạo chữ ký toán học hợp lệ; điều gì khiến PKIX phải từ chối intermediate làm issuer? Thử thêm KU thiếu `keyCertSign`, so sánh lý do lỗi.

### Bài 4 — pathLenConstraint thật sự

Tạo R → A(pathLen=0) → L và R → A(pathLen=0) → B(CA=true) → L. Cấu hình **R là TrustAnchor**, giữ **A trong CertPath**, không lấy A làm TrustAnchor. Giải thích vì sao path thứ hai phải bị từ chối vì intermediate depth. Thử A(pathLen=1) và kiểm constraints khác.

### Bài 5 — EKU serverAuth và clientAuth

Tạo hai leaf có cùng issuer và SAN khác nhau: một leaf `EKU=serverAuth`, một leaf `EKU=clientAuth`. Dựng HTTPS/mTLS test bằng JSSE đúng policy. Chỉ chain PKIX pass có đủ để dùng leaf clientAuth-only làm server không? Mô tả vai trò của TLS usage checks.

### Bài 6 — SAN và hostname

Leaf có SAN `service.example.test`. Kết nối tới `other.example.test` trong lab với cùng leaf, chain vẫn hợp lệ. Đo/giải thích lỗi hostname mismatch của TLS stack, và vì sao so Subject CN bằng substring là không an toàn.

### Bài 7 — Revocation và ngày kiểm chứng

Trong private PKI lab có OCSP/CRL, cấp một leaf còn hạn rồi revoke nó. Định nghĩa policy fail-closed hoặc soft-fail, thời gian cache và kiểm tra status khi responder không truy cập được. Không gỡ revocation check ở production chỉ để làm test pass.

### Bài 8 — Tích hợp CI

Tạo bộ fixtures `root.pem`, `leaf-valid.pem`, `leaf-expired.pem`, `leaf-wrong-eku.pem`, `intermediate-no-ca.pem`, `intermediate-depth0.pem` cùng cấu hình trust anchor. Test PKIX và TLS endpoint riêng. Mọi private key test phải được sinh trong môi trường kiểm soát/không tái sử dụng cho production.

### Bài 9 — Phân tích đường dẫn cross-signed

Một intermediate có hai certificate variants được ký bởi hai roots khác nhau. Dựa trên truststore và policy, builder có thể tìm nhiều paths. Làm thế nào để bảo đảm CA constraints, validity, name constraints, algorithm requirements và leaf EKU được áp cho **path thực sự được chọn**, thay vì chỉ nhìn root name?

**Tiêu chí hoàn thành:** Bạn có thể nhìn vào một certificate path và xác định **certificate nào phải là CA, key usage nào cho phép phát hành, pathLenConstraint giới hạn intermediate nào, thời điểm nào cần kiểm và EKU/SAN nào phù hợp TLS endpoint**. Quan trọng nhất, bạn biết **dùng PKIX/JSSE đúng chuẩn**, không tự kiểm một vài field rồi gọi đó là xác minh chứng thư hoàn chỉnh.

## 20. Tài liệu tham khảo

- [RFC 5280 — Internet X.509 Public Key Infrastructure Certificate and CRL Profile](https://www.rfc-editor.org/rfc/rfc5280): `BasicConstraints`, `KeyUsage`, `ExtendedKeyUsage`, validity, pathLenConstraint và `6.1` path validation.
- [RFC 9525 — Service Identity in TLS](https://www.rfc-editor.org/rfc/rfc9525): xác minh danh tính dịch vụ qua SAN/hostname.
- [Java 17 — X509Certificate](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/X509Certificate.html): `checkValidity`, `getBasicConstraints`, `getKeyUsage`, `getExtendedKeyUsage`.
- [Java 17 — CertPathValidator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertPathValidator.html): API validate đường chứng thư.
- [Java 17 — PKIXParameters](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/PKIXParameters.html): `TrustAnchor`, `setDate`, revocation/policy options.
- [Java 17 — CertPathBuilder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertPathBuilder.html): path construction.
- [Java 17 — CertPathValidatorException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertPathValidatorException.html): reason và index của certificate lỗi.
- [Java 17 — TrustAnchor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/TrustAnchor.html): ranh giới niềm tin.
- [Java 17 — X509ExtendedTrustManager](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/X509ExtendedTrustManager.html): kiểm certificate trong ngữ cảnh TLS.
- [Java 17 — SSLParameters](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLParameters.html): endpoint identification.
- [Java 17 — keytool](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html): tạo CA và leaf test với BasicConstraints, KU/EKU/SAN.
- [Oracle Java 17 — JSSE Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-secure-socket-extension-jsse-reference-guide.html): TLS, trust managers và path validation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cert-path-006">14.6 Thu hồi bằng CRL, OCSP và PKIXRevocationChecker</a>

<details>
<summary>Xem chi tiết</summary>

# 14.6. Thu hồi bằng CRL, OCSP và `PKIXRevocationChecker`

## Mục tiêu và phạm vi

Khi kiểm tra một chứng chỉ X.509, việc thấy chữ ký của CA hợp lệ và `notBefore ≤ now ≤ notAfter` **chưa đủ**: CA có thể đã thu hồi chứng chỉ trước ngày hết hạn do private key bị lộ, cấp nhầm, thay đổi quyền sử dụng hoặc chấm dứt dịch vụ. Bài này tập trung vào **revocation checking trong đường dẫn tin cậy PKIX**, sử dụng **CRL**, **OCSP** và API `java.security.cert.PKIXRevocationChecker` của Java 17+.

Học xong, bạn có thể:

1. Phân biệt **hết hạn**, **bị thu hồi**, **chưa xác định được trạng thái**, và **đường dẫn chứng chỉ không đáng tin**.
2. Đọc CRL/X.509, hiểu CRL issuer, serial number, `thisUpdate`, `nextUpdate`, chữ ký, CRL Distribution Points (CDP) và các giới hạn của danh sách thu hồi.
3. Hiểu OCSP `good/revoked/unknown`, responder URL qua AIA, kiểm tra chữ ký response và độ tươi của trạng thái, OCSP stapling và rủi ro mạng.
4. Cấu hình `PKIXRevocationChecker` theo chính sách: `PREFER_CRLS`, `NO_FALLBACK`, `ONLY_END_ENTITY`, `SOFT_FAIL` và nguồn CRL từ `CertStore`.
5. Dùng `CertPathValidator` để kiểm tra thực sự một certificate path trên **CRL sạch** và **CRL có chứng chỉ bị thu hồi**; không nhầm kiểm tra một serial trong CRL với kiểm tra PKIX hoàn chỉnh.
6. Chọn chính sách xử lý lỗi **fail-closed/hard-fail** hay **soft-fail** một cách minh bạch, đo lường timeout và kiểm thử tích hợp TLS.

**Giới hạn:** đây là bài về **thu hồi của chứng chỉ và đường dẫn PKIX**, không phải bài phát hành CA, TLS handshake hoàn chỉnh hay giao thức ứng dụng. Ví dụ dùng khóa/chứng chỉ **lab**, không bao giờ đưa CA tự tạo trong ví dụ vào truststore production. Java code dùng JDK 17+; hành vi mạng và các thiết lập hệ thống có thể khác giữa provider/JDK/ứng dụng.

## 1. “Chứng chỉ hợp lệ” gồm nhiều điều kiện

Một chứng chỉ có thể thỏa chữ ký số của CA mà vẫn bị từ chối vì:

- Chưa đến ngày `notBefore` hoặc đã quá `notAfter` (validity period).
- Không xây được đường dẫn từ chứng chỉ lá qua intermediate đến **trust anchor được tin cậy**.
- Trật tự CA/basic constraints/key usage, thuật toán/key strength, certificate policies hoặc name constraints không đạt yêu cầu.
- Tên máy chủ/SAN, Extended Key Usage hoặc mục đích sử dụng không phù hợp với ứng dụng, đặc biệt trong TLS.
- **Bị CA thu hồi** trước khi hết hạn và có bằng chứng thu hồi hợp lệ.
- **Không xác định được trạng thái thu hồi** vì không có CRL/OCSP response đáng tin, quá hạn hoặc không liên hệ được responder, tùy chính sách.

Ba trạng thái revocation cần hiểu rõ:

| Trạng thái | Ý nghĩa | Không được hiểu thành |
| --- | --- | --- |
| **GOOD / không tìm thấy revocation trong nguồn hợp lệ** | Theo bằng chứng revocation hợp lệ cho đúng issuer/serial/phạm vi và thời điểm kiểm tra, cert chưa bị ghi nhận là thu hồi | “Chứng chỉ hoàn toàn hợp lệ cho mọi mục đích” |
| **REVOKED** | CA/OCSP responder có thẩm quyền đã công bố thu hồi certificate | “Có thể bỏ qua do mạng chập chờn” |
| **UNKNOWN / UNDETERMINED** | Chưa thể chứng minh trạng thái theo dữ liệu và policy hiện có | “Không bị thu hồi” |

**Revocation không thay thế các bước PKIX và ứng dụng.** OCSP `good` chỉ nói về trạng thái thu hồi theo nguồn trả lời; không tự chứng minh domain/identity, certificate chain, thuật toán hoặc key usage hợp lệ. Một CRL không chứa serial cũng chưa chứng minh cert an toàn nếu CRL bị giả, sai issuer, hết hạn hoặc không bao phủ đúng đối tượng.

### Ví dụ một tình huống cụ thể

CA cấp certificate `serial=0x42`, hạn 1 năm. Sau 1 tháng private key bị nghi lộ, CA thu hồi `serial=0x42`. Dù certificate còn 11 tháng hiệu lực và chữ ký CA không thay đổi, verifier phải từ chối nếu có bằng chứng thu hồi hợp lệ. Nếu OCSP server down, verifier cần **quyết định trạng thái chưa biết theo policy**, không lặng lẽ gắn nhãn GOOD.

## 2. Vị trí kiểm tra thu hồi trong đường dẫn PKIX

Giả sử:

```text
Trusted Root CA (TrustAnchor, đã được chọn qua policy tin cậy)
    |
    +-- Intermediate CA (cert được Root ký)
             |
             +-- Leaf certificate: api.example.invalid
                    |
                    v
        Kiểm tra chữ ký / chain / thời hạn / purpose
                    |
                    v
             Revocation checks
           (leaf và CA trung gian nếu policy yêu cầu)
                    |
                    v
            ACCEPT hoặc REJECT
```

Trong Java, `CertPath` thông thường liệt kê **leaf → intermediate → ...**, **không chứa trust anchor** ở cuối. Trust anchor được truyền riêng qua `PKIXParameters`/`TrustAnchor`. Không được lấy root CA trực tiếp từ chain do peer gửi rồi tự thêm vào trust anchors: peer có thể tự phát hành CA giả. Nguồn trust anchor phải nằm trong cấu hình/KeyStore hoặc trust policy đáng tin.

**Phạm vi revocation:** thường quan tâm cả leaf và intermediate; nếu intermediate bị thu hồi, nhiều leaf do CA đó phát hành cũng mất đường tin cậy. `ONLY_END_ENTITY` làm giảm kiểm tra đối với CA certificates nên chỉ cân nhắc khi có chính sách/nguồn kiểm tra thay thế rõ ràng.

**Trust anchor** thường được tin trực tiếp và không được xử lý như một certificate path element bình thường; trạng thái root CA/trust anchor cần được quản lý bằng cập nhật truststore và chính sách tin cậy. Không nghĩ “PKIX check all certs” tức tự động phát hiện mọi CA gốc bị distrust ngoài cấu hình.

## 3. CRL — Certificate Revocation List

**CRL** (RFC 5280) là một danh sách **được CA hoặc CRL issuer có thẩm quyền ký**, chứa thông tin về các serial number được thu hồi theo một phạm vi/phân phối cụ thể.

Các trường và khái niệm thường gặp:

| Trường | Ý nghĩa thực tế |
| --- | --- |
| `issuer` | Danh tính issuer của CRL; thường CA cấp certificate, nhưng PKIX còn có trường hợp indirect CRL |
| `thisUpdate` | Thời điểm trạng thái danh sách có hiệu lực/cập nhật |
| `nextUpdate` | Thời điểm dự kiến phát hành CRL tiếp theo; dùng trong chính sách về độ tươi |
| `revokedCertificates` | Danh sách serial number của các cert bị thu hồi, thường kèm thời điểm và lý do |
| `signatureAlgorithm / signature` | Bằng chứng nội dung CRL do khóa được ủy quyền ký |
| `CRL Number` | Extension giúp định danh và quản lý phiên bản CRL |
| `Authority Key Identifier (AKI)` | Giúp xác định khóa dùng ký CRL trong bối cảnh có nhiều key/certificate |
| `CRL Distribution Points (CDP)` | X.509 extension trong certificate chỉ nơi có thể tải CRL (HTTP/LDAP...) |
| `Issuing Distribution Point` | Có thể thu hẹp phạm vi CRL, ví dụ chỉ cert end-entity hoặc chỉ một tập lý do |
| `delta CRL` | Chỉ thay đổi so với base CRL; không tự xử lý như danh sách đầy đủ nếu policy cần base |

Đường dẫn ví dụ:

```text
Certificate leaf -> CRL Distribution Points URI (nếu có)
                  -> tải CRL từ điểm phân phối
                  -> kiểm tra chữ ký và issuer/ủy quyền
                  -> kiểm tra thisUpdate/nextUpdate và phạm vi
                  -> đối chiếu serial number và reason
                  -> kết luận revocation theo quy tắc PKIX
```

Một số tính chất:

- CRL có thể **cache** và dùng offline sau khi nhận được bản còn hợp lệ. Ưu điểm là không cần kết nối OCSP cho từng lần kiểm tra, nhược điểm là độ lớn CRL và độ trễ cập nhật; thời điểm thu hồi thực tế có thể nằm sau thời điểm CRL gần nhất.
- **Không có serial trong CRL không đủ để kết luận an toàn** nếu CRL không đúng issuer/scope hoặc không còn mới. Một CRL “sạch” có issuer khác cũng không làm certificate trở thành hợp lệ.
- `X509CRL.isRevoked(cert)`/`getRevokedCertificate(serial)` là **kiểm tra membership** trên object CRL đã parse, không tự thay `CertPathValidator`: đừng chỉ gọi `isRevoked` rồi bỏ qua trust/freshness/signature/chain.
- **Đừng tự tải URL từ certificate chưa đáng tin tùy tiện**: CDP từ input X.509 có thể trỏ tới mạng nội bộ hoặc địa chỉ không mong muốn. Cần egress/proxy/SSRF controls và timeout thích hợp; hành vi phụ thuộc provider.
- CRL được một CA ký có thể bị phân vùng theo distribution point, reason hoặc issuer. Không lấy một CRL bất kỳ của CA đó rồi giả định luôn đầy đủ mọi chứng chỉ.

### Java đọc CRL thủ công

Snippet sau **chỉ minh họa parse và kiểm tra vài điều kiện riêng lẻ**, không phải validator độc lập:

```java
CertificateFactory factory = CertificateFactory.getInstance("X.509");
X509CRL crl;
try (InputStream in = Files.newInputStream(Path.of("issuer.crl"))) {
    crl = (X509CRL) factory.generateCRL(in);
}
System.out.println(crl.getIssuerX500Principal());
System.out.println(crl.getThisUpdate());
System.out.println(crl.getNextUpdate());
System.out.println(crl.getRevokedCertificates());
```

Snippet cần `java.io.InputStream`, `java.nio.file.Files/Path` và `java.security.cert.CertificateFactory/X509CRL`, cùng xử lý checked exceptions. `getRevokedCertificates()` có thể trả `null` nếu không có entries; dữ liệu có thể chứa thông tin nhạy cảm vận hành nên đừng log toàn bộ serial của đối tác nếu không cần. **Chỉ parse thành công không tự chứng minh CRL trusted**.

Nếu đã có một CA certificate thực sự đáng tin, `crl.verify(issuerPublicKey)` giúp kiểm tra chữ ký CRL, nhưng còn phải xác minh issuer/khả năng ký CRL (`cRLSign`), thời hạn, phạm vi và đường dẫn tin cậy. API PKIX có trách nhiệm xử lý đầy đủ hơn; đừng tự ghép check rời rạc rồi gắn nhãn “đã validate”.

## 4. OCSP — Online Certificate Status Protocol

**OCSP** (RFC 6960, kế thừa RFC 2560) là cơ chế hỏi responder về tình trạng của **một certificate cụ thể**, thay vì tải toàn bộ CRL.

Bản tin OCSP thường gắn với `CertID` gồm nhận diện issuer (hash của issuer name/key) và **serial** của certificate. Responder trả response có chữ ký, status, thời gian và thông tin responder; verifier phải kiểm tra **quyền ký OCSP response**, chữ ký, thời gian, association đúng cert/issuer và các quy tắc khác.

| Kết quả | Nội dung | Cách diễn giải |
| --- | --- | --- |
| `good` | Responder không báo chứng chỉ bị thu hồi theo thông tin responder cung cấp | Không thay thế xác minh chữ ký certificate/chain/hostname |
| `revoked` | Có bằng chứng bị thu hồi, có thể kèm revocationTime/reason | **REJECT** khi response hợp lệ |
| `unknown` | Responder không có trạng thái đáng tin cho CertID yêu cầu | **Không mặc định coi GOOD** |
| Response bị lỗi/timeout | Không nhận được bằng chứng sử dụng được | Xử lý **UNDETERMINED / policy fallback**, không coi là `good` |
| Response có chữ ký sai/không được ủy quyền | Không có bằng chứng đáng tin | Từ chối hoặc áp dụng cơ chế kiểm tra khác theo policy, **không** coi là `good` |

Trong chứng chỉ, extension **Authority Information Access (AIA)** có thể chứa OCSP responder URL. Một cấu hình ứng dụng cũng có thể dùng responder cố định (chẳng hạn CA nội bộ). Với `PKIXRevocationChecker.setOcspResponder(URI)`, URI cấu hình sẽ **ghi đè** URL OCSP từ AIA cho checker đó; chỉ dùng URL được phê duyệt cho trust boundary cụ thể.

**Tính mới và chống replay:** quan tâm `thisUpdate`, `nextUpdate`, `producedAt` và quy tắc allowed clock skew/cache TTL. OCSP nonce là extension tùy profile giúp liên kết request/response nhưng có yếu tố tương thích và hiệu năng; không tự thêm nonce mà chưa kiểm tra hỗ trợ responder/stapling/cache. OCSP response bị stale hoặc không đúng certificate không được coi là `good`.

### OCSP stapling trong TLS

Thay vì client tự hỏi responder, server TLS có thể gửi kèm **OCSP response đã ký** trong handshake (stapling). Lợi ích có thể gồm giảm độ trễ/khả năng lộ thông tin client đến responder và giúp cache ở server. Tuy nhiên client vẫn phải **thực sự kiểm tra** chữ ký, thời gian, cert association và chính sách; không phải cứ server gửi bytes “stapled” là tin được.

Một số chứng chỉ có extension về việc bắt buộc phải stapling (TLS Feature, thường gọi Must-Staple); khả năng hỗ trợ/enforcement khác nhau giữa client. **Không tự giả định Java JSSE của bạn đang kiểm tra revocation hoặc Must-Staple** nếu chưa cấu hình, kiểm thử và xác minh chính xác implementation.

## 5. So sánh CRL và OCSP khi vận hành

| Tiêu chí | CRL | OCSP |
| --- | --- | --- |
| Đơn vị truy vấn | Danh sách cho một issuer/phạm vi | Status của một certificate (hoặc nhóm CertID) |
| Cập nhật | Theo chu kỳ CRL, có thể có delta | Theo response và thời hạn cache/responder |
| Kích thước dữ liệu | Có thể lớn khi nhiều cert bị thu hồi | Response thường nhỏ, nhưng nhiều yêu cầu |
| Hoạt động offline | Có thể dùng CRL cache hợp lệ đã tin cậy | Có thể dùng response cache/stapled còn hợp lệ |
| Riêng tư | Người tải CRL không nhất thiết tiết lộ cert nào đang kiểm tra | Client hỏi responder có thể tiết lộ cert/timing; stapling giúp giảm |
| Sự cố mạng | CRL fetch không được / CRL quá hạn -> unknown | OCSP timeout, `tryLater`, responder unreachable -> unknown |
| Điều kiện tin cậy | Chữ ký CRL/issuer/ủy quyền/phạm vi/thời gian | Chữ ký response/responder authorization/CertID/thời gian |
| Java PKIX | `CertStore` CRLs, CRLDP nếu provider hỗ trợ | `PKIXRevocationChecker`, AIA/responder, OCSP responses |

**Đừng chọn cơ chế duy nhất chỉ vì tốc độ một lần đo.** Đặt trước yêu cầu về freshness, cách xử lý mất mạng, CA ecosystem, egress, chứng chỉ trung gian, cache, khả năng đo lường và mô hình đe dọa. Một cơ chế fallback giúp khả dụng nhưng chỉ có ích khi **nguồn thay thế thật sự xác thực được** trạng thái, không phải bỏ qua lỗi.

## 6. Các lớp Java tham gia revocation checking

| API | Trách nhiệm |
| --- | --- |
| `CertificateFactory.getInstance("X.509")` | Parse certificate, CRL và tạo `CertPath` |
| `X509Certificate` | Đại diện chứng chỉ, issuer/serial/validity/extension |
| `X509CRL` | Đại diện CRL, issuer, times, entries, kiểm tra signature riêng |
| `TrustAnchor` | CA/public key được ứng dụng chọn là gốc tin cậy |
| `CertPath` | Chuỗi chứng chỉ theo hướng leaf → intermediate, không kèm trust anchor |
| `CertStore` + `CollectionCertStoreParameters` | Cung cấp CRL/certificate cho việc tìm và kiểm tra |
| `PKIXParameters` | Trust anchors, ngày kiểm tra, CertStore, policy, checker |
| `PKIXBuilderParameters` | Tương tự nhưng có target constraints để **xây** đường dẫn |
| `CertPathValidator.getInstance("PKIX")` | Kiểm tra path đã có theo PKIX |
| `CertPathBuilder.getInstance("PKIX")` | **Xây** path từ candidate certificates và trust anchors |
| `PKIXRevocationChecker` | Chọn OCSP/CRL, fallback, soft-fail, responder và OCSP responses |
| `CertPathValidatorException` | Cho biết lý do PKIX thất bại, certificate index, cause |

Phân biệt **builder** và **validator**: `CertPathBuilder` tìm đường đi từ cert lá đến trust anchor qua các candidate certificates/`CertStore`; `CertPathValidator` kiểm tra một `CertPath` đã có. **Cả hai** có thể dùng `PKIXRevocationChecker` khi được thêm vào parameters phù hợp. Khi application đã có chain từ TLS handshake, không mặc nhiên coi nó là đường dẫn hợp lệ chỉ vì peer gửi đầy đủ.

## 7. `PKIXRevocationChecker`: cấu hình chính xác

Checker không được tạo bằng `new PKIXRevocationChecker()` vì đây là lớp **abstract**. Lấy checker từ PKIX engine:

```java
CertPathValidator validator = CertPathValidator.getInstance("PKIX");
PKIXRevocationChecker checker =
        (PKIXRevocationChecker) validator.getRevocationChecker();
```

Các option chuẩn:

| Option | Ý nghĩa | Cảnh báo |
| --- | --- | --- |
| `PREFER_CRLS` | Ưu tiên CRL thay vì OCSP | **Không** vô hiệu hóa OCSP fallback nếu chưa có `NO_FALLBACK` |
| `NO_FALLBACK` | Tắt cơ chế dự phòng thứ hai | Chọn cùng `PREFER_CRLS` để chỉ ưu tiên CRL mà không chuyển OCSP |
| `ONLY_END_ENTITY` | Chỉ kiểm tra thu hồi của chứng chỉ leaf | Bỏ phần kiểm tra revocation các CA intermediate, cân nhắc rủi ro |
| `SOFT_FAIL` | Cho phép bỏ qua một số lỗi không xác định trạng thái từ mạng/OCSP `tryLater`/`internalError` theo Javadoc | **Không** biến `revoked` thành `good`; việc bỏ qua unknown có rủi ro |

Theo Java SE 17 API, khi dùng checker cụ thể, **OCSP là cơ chế ưu tiên mặc định, CRL là fallback**; `PREFER_CRLS` đảo thứ tự, `NO_FALLBACK` tắt fallback. Đây là contract của **checker API**; phần **thu hồi mặc định do system/security properties** của Oracle/SUN provider lại có cấu hình legacy riêng ở mục 11, nên không được đánh đồng hai đường cấu hình.

**Đặt options trước khi add vào parameters**. `PKIXParameters.addCertPathChecker(checker)` clone checker, nên sửa biến `checker` **sau** khi add sẽ không làm parameters đã tạo đổi theo. Nếu cần thay policy, tạo checker/parameters mới cho operation tương ứng.

### `setRevocationEnabled(false)` có nghĩa gì?

`PKIXParameters` mặc định `isRevocationEnabled()==true`. Nếu gọi `setRevocationEnabled(false)`, bạn tắt **default revocation checking mechanism** của provider. Nhưng khi **thêm một `PKIXRevocationChecker` rõ ràng** bằng `addCertPathChecker`, Javadoc quy định checker đó **vẫn được dùng** bất kể flag `RevocationEnabled`.

Điều này giải thích snippet trong demo offline:

```java
params.setRevocationEnabled(false); // Tắt checker mặc định, KHÔNG tắt checker thêm sau.
params.addCertPathChecker(checker);  // Checker này vẫn thực hiện kiểm tra.
```

Mục đích là tránh hai con đường kiểm tra trùng lặp, đồng thời chủ động kiểm soát nguồn CRL/options. **Không copy `setRevocationEnabled(false)` mà bỏ `addCertPathChecker`**: lúc đó bạn có thể vô tình **vô hiệu hóa mọi kiểm tra thu hồi** trong pipeline đó.

### `SOFT_FAIL` không tương đương “tự động an toàn”

Theo Java SE 17 API, `SOFT_FAIL` chỉ bỏ qua một số trường hợp **không thể lấy CRL/OCSP vì lỗi mạng** hoặc OCSP responder trả `internalError`/`tryLater`. Nếu cho phép fallback, thường cả hai cơ chế phải thất bại theo các điều kiện soft-fail tương ứng thì mới được cho qua theo option này. **Bằng chứng REVOKED hợp lệ không được bỏ qua.**

`checker.getSoftFailExceptions()` có thể liệt kê exception đã bị bỏ qua khi kiểm tra, nhưng lưu ý checker bị **clone** khi thêm vào `PKIXParameters`; đừng mặc định gọi getter trên **biến checker ban đầu** sau validate sẽ phản ánh internal instance mà provider đã dùng. Khi cần telemetry chính xác, thiết kế logging/instrumentation ở layer kiểm chứng, thử nghiệm provider và xác nhận số liệu trên môi trường thật.

Đối với high-assurance authentication, key signing/cấp quyền nhạy cảm, nên có chính sách **fail-closed** khi không thể xác minh revocation, kèm timeout/UX/retry có kiểm soát. Với một số hệ thống cần availability, soft-fail có thể là quyết định chấp nhận rủi ro, nhưng phải đo lường, giới hạn và phân biệt rõ trạng thái unknown. Không dùng soft-fail để “sửa” CA/CRL cấu hình sai.

## 8. Java 17: ví dụ không cần mạng để kiểm tra cấu hình checker

**`RevocationCheckerOptionsDemo.java`** có thể chạy ngay trên JDK 17+ có PKIX provider. Nó chỉ lấy checker, đặt options và một responder URI minh họa `.invalid`; **không làm request OCSP hay xác minh certificate nào**.

```java
import java.net.URI;
import java.security.cert.CertPathValidator;
import java.security.cert.PKIXRevocationChecker;
import java.util.EnumSet;

public final class RevocationCheckerOptionsDemo {
    public static void main(String[] args) throws Exception {
        CertPathValidator validator = CertPathValidator.getInstance("PKIX");
        PKIXRevocationChecker checker =
                (PKIXRevocationChecker) validator.getRevocationChecker();

        checker.setOptions(EnumSet.of(
                PKIXRevocationChecker.Option.PREFER_CRLS,
                PKIXRevocationChecker.Option.NO_FALLBACK));
        checker.setOcspResponder(URI.create("https://ocsp.example.invalid"));

        System.out.println("prefersCRL=" + checker.getOptions()
                .contains(PKIXRevocationChecker.Option.PREFER_CRLS));
        System.out.println("fallbackDisabled=" + checker.getOptions()
                .contains(PKIXRevocationChecker.Option.NO_FALLBACK));
        System.out.println("softFailEnabled=" + checker.getOptions()
                .contains(PKIXRevocationChecker.Option.SOFT_FAIL));
        System.out.println("responderConfigured=" +
                (checker.getOcspResponder() != null));
        System.out.println("noNetworkRequestWasSent=true");
    }
}
```

```bash
javac -encoding UTF-8 RevocationCheckerOptionsDemo.java
java RevocationCheckerOptionsDemo
```

**Stdout đã kiểm tra:**

```text
prefersCRL=true
fallbackDisabled=true
softFailEnabled=false
responderConfigured=true
noNetworkRequestWasSent=true
```

Bài học chính:

1. `getRevocationChecker()` phải được gọi từ PKIX validator tương thích.
2. `EnumSet.of(PREFER_CRLS, NO_FALLBACK)` chọn **CRL-only** cho checker trong ví dụ; OCSP responder URI đã đặt nhưng **sẽ không được sử dụng** nếu checker chạy chỉ với CRL.
3. `SOFT_FAIL` **không được bật**: nếu không xác định được trạng thái thì phương thức kiểm tra path sẽ không tự xem đó là thành công.
4. Các getter chỉ xác minh cấu hình **chưa phải kết quả bảo mật**: chưa có `PKIXParameters`, `CertPath`, trust anchor, CRL hay response. Dòng `noNetworkRequestWasSent=true` là khẳng định do code chỉ gọi setter/getter, không chứng minh hệ thống OCSP endpoint hoạt động.
5. Địa chỉ `example.invalid` **không phải** responder thật; khi demo lab `CRL-only`, không gửi network request đến URI đó.

## 9. Java 17: xác minh đường dẫn thực sự với CRL sạch và CRL thu hồi

Ví dụ thứ hai **`OfflineCrlValidationDemo.java`** nhận bốn file **của CA lab do bạn quản lý**: `root.pem` (trust anchor), `leaf.pem` (chứng chỉ do root cấp), `clean.crl` (CRL do root ký không thu hồi leaf) và `revoked.crl` (CRL do root ký, chứa serial leaf).

**Điều kiện dữ liệu đầu vào:** CA phải có basicConstraints CA=true và quyền ký CRL theo KeyUsage khi những ràng buộc này được áp dụng. Leaf phải do root tương ứng ký; CRL phải đúng issuer, còn thời hạn và có chữ ký hợp lệ. Đây là **fixture kiểm thử offline**, không phải một hệ thống tin cậy ngoài đời; `root.pem` được ứng dụng test tự chọn là anchor.

```java
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.*;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class OfflineCrlValidationDemo {
    private static X509Certificate certificate(String file) throws Exception {
        try (InputStream input = Files.newInputStream(Path.of(file))) {
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(input);
        }
    }

    private static X509CRL crl(String file) throws Exception {
        try (InputStream input = Files.newInputStream(Path.of(file))) {
            return (X509CRL) CertificateFactory.getInstance("X.509")
                    .generateCRL(input);
        }
    }

    private static String evaluate(X509Certificate root, X509Certificate leaf,
            X509CRL list) throws Exception {
        list.verify(root.getPublicKey());

        CertPath chain = CertificateFactory.getInstance("X.509")
                .generateCertPath(List.of(leaf));

        PKIXParameters params = new PKIXParameters(
                Set.of(new TrustAnchor(root, null)));
        params.setRevocationEnabled(false);
        params.addCertStore(CertStore.getInstance("Collection",
                new CollectionCertStoreParameters(List.of(list))));

        CertPathValidator validator = CertPathValidator.getInstance("PKIX");
        PKIXRevocationChecker checker =
                (PKIXRevocationChecker) validator.getRevocationChecker();
        checker.setOptions(EnumSet.of(
                PKIXRevocationChecker.Option.PREFER_CRLS,
                PKIXRevocationChecker.Option.NO_FALLBACK));
        params.addCertPathChecker(checker);

        try {
            validator.validate(chain, params);
            return "ACCEPT";
        } catch (CertPathValidatorException e) {
            if (e.getReason() ==
                    CertPathValidatorException.BasicReason.REVOKED) {
                return "REVOKED";
            }
            if (e.getReason() ==
                    CertPathValidatorException.BasicReason
                            .UNDETERMINED_REVOCATION_STATUS) {
                return "UNKNOWN";
            }
            throw e;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.err.println(
                    "Usage: java OfflineCrlValidationDemo root.pem leaf.pem clean.crl revoked.crl");
            return;
        }

        X509Certificate root = certificate(args[0]);
        X509Certificate leaf = certificate(args[1]);
        X509CRL clean = crl(args[2]);
        X509CRL revoked = crl(args[3]);

        System.out.println("issuerMatches=" +
                clean.getIssuerX500Principal()
                        .equals(root.getSubjectX500Principal()));
        System.out.println("cleanListsLeaf=" +
                (clean.getRevokedCertificate(leaf.getSerialNumber()) != null));
        System.out.println("revokedListsLeaf=" +
                (revoked.getRevokedCertificate(leaf.getSerialNumber()) != null));
        System.out.println("cleanPath=" + evaluate(root, leaf, clean));
        System.out.println("revokedPath=" + evaluate(root, leaf, revoked));
    }
}
```

Biên dịch và chạy khi đã có **bộ bốn file tương ứng**:

```bash
javac -encoding UTF-8 OfflineCrlValidationDemo.java
java OfflineCrlValidationDemo root.pem leaf.pem clean.crl revoked.crl
```

**Stdout đã được kiểm tra trên bộ chứng chỉ/CRL lab sinh riêng cho ví dụ:**

```text
issuerMatches=true
cleanListsLeaf=false
revokedListsLeaf=true
cleanPath=ACCEPT
revokedPath=REVOKED
```

**Đọc code theo đúng tầng kiểm tra:**

- `CertificateFactory` parse `root.pem`, `leaf.pem` và hai CRL từ file. Không mở kết nối CA hoặc OCSP.
- `crl.getRevokedCertificate(serial)` minh họa **membership**: CRL sạch không chứa serial, CRL revoked có chứa. Đây chỉ là quan sát, không tự xác minh PKIX.
- `list.verify(root.getPublicKey())` kiểm tra chữ ký CRL bằng khóa của root **đã được ứng dụng chọn làm trust anchor**. Nếu CRL do khóa khác ký không có ủy quyền, ví dụ ngắn này không phải mô hình cho mọi trường hợp CRL gián tiếp/ủy quyền; hãy dùng validation PKIX phù hợp với CA thật.
- `CertPath` gồm đúng **leaf**, vì **root là `TrustAnchor` riêng**, không được đưa root vào danh sách chain.
- `CollectionCertStoreParameters(List.of(list))` đưa CRL đã đọc vào PKIX validator, không phụ thuộc kết nối đến CDP. `PREFER_CRLS + NO_FALLBACK` bảo đảm checker dùng CRL thay vì OCSP.
- `params.setRevocationEnabled(false)` tắt **default mechanism**, nhưng `params.addCertPathChecker(checker)` đăng ký checker explicit vẫn hoạt động theo Java API; cả **ACCEPT** và **REVOKED** thực sự được thử trên PKIX engine.
- `validate` có thể ném `CertPathValidatorException`. Chỉ hai lý do đã được phân loại trong ví dụ: `REVOKED` và `UNDETERMINED_REVOCATION_STATUS`; các lỗi như chữ ký certificate, constraint hoặc thuật toán không hợp lệ được **ném lại**, không bị che thành “không thu hồi”.
- `cleanPath=ACCEPT` chỉ có nghĩa **PKIX path với trust anchor/parameters của lab vượt qua kiểm tra**. Nó không chứng minh hostname/TLS identity, quyền truy cập hay một CA ngoài đời đã đáng tin.

### Nếu file CRL không hợp lệ thì sao?

Có thể gặp `CRLException`, `SignatureException`, `InvalidKeyException` hoặc `CertPathValidatorException` tùy lỗi và bước xảy ra. Không catch tổng quát `Exception` rồi trả `ACCEPT`. Khi CRL không dùng được, bảo đảm **fail theo policy**; không tự diễn giải “không đọc được CRL” thành “CRL sạch”.

## 10. Chuẩn bị fixture lab để thử CRL thu hồi

Bài code offline cần bốn file. Một CA test do bạn tự tạo có thể sinh `root.pem`, cấp `leaf.pem`, tạo một CRL sạch và CRL chứa serial của leaf.

**Bước A — tạo CA và certificate leaf bằng lệnh `keytool` chuẩn** (giả định password lab đã được cấp an toàn dưới biến môi trường `LAB_PASS`):

```bash
# Tạo key pair CA cho lab; KHÔNG đưa CA này vào truststore production.
keytool -genkeypair -alias ca -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -dname "CN=Test Root CA" -validity 30 -ext "BC:critical=ca:true" -ext "KU:critical=keyCertSign,cRLSign" -keystore ca.p12 -storetype PKCS12 -storepass:env LAB_PASS -keypass:env LAB_PASS

# Xuất chứng chỉ công khai CA.
keytool -exportcert -rfc -alias ca -file root.pem -keystore ca.p12 -storetype PKCS12 -storepass:env LAB_PASS

# Tạo private key và CSR của chứng chỉ leaf lab.
keytool -genkeypair -alias leaf -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -dname "CN=leaf.example.invalid" -validity 30 -keystore leaf.p12 -storetype PKCS12 -storepass:env LAB_PASS -keypass:env LAB_PASS
keytool -certreq -alias leaf -file leaf.csr -keystore leaf.p12 -storetype PKCS12 -storepass:env LAB_PASS -keypass:env LAB_PASS

# CA lab ký CSR và xuất leaf.pem.
keytool -gencert -alias ca -infile leaf.csr -outfile leaf.pem -rfc -validity 30 -ext "KU:critical=digitalSignature" -ext "EKU=serverAuth" -keystore ca.p12 -storetype PKCS12 -storepass:env LAB_PASS -keypass:env LAB_PASS

# Xem serial leaf và trường issuer.
keytool -printcert -file leaf.pem
```

Những lệnh này đã được chạy thành công trong môi trường lab của bài. Một certificate `leaf.pem` có thể chưa đáp ứng mọi đòi hỏi của một CA/TLS sản phẩm thực, ví dụ name/SAN, CRLDP/AIA, certificate policies và quy trình trust thực tế. Vì đây là bài **revocation offline**, `OfflineCrlValidationDemo` không thực hiện hostname check.

**Bước B — có CRL do CA lab ký:**

- **Ưu tiên CA test tool của bạn** để phát hành một CRL mới không có revoked entry và một CRL có revoked entry của leaf, bảo đảm issuer/chữ ký/`thisUpdate`/`nextUpdate` hợp lệ. Với OpenSSL CA, tạo CRL theo cấu hình CA test và database revocation của nó; với CA enterprise, dùng chức năng quản lý revocation thử nghiệm.
- **Tùy chọn chỉ cho môi trường JDK có lệnh `keytool -gencrl`**: một số bản JDK có tiện ích không được liệt kê trong mọi trang man page public; đã xác nhận trên môi trường JDK dùng thử. Có thể tạo `clean.crl` và `revoked.crl` với CA lab:

```bash
# Chỉ khi "keytool -gencrl -help" trên JDK của bạn báo hỗ trợ.
keytool -gencrl -alias ca -file clean.crl -keystore ca.p12 -storetype PKCS12 -storepass:env LAB_PASS -keypass:env LAB_PASS

# SERIAL_DEC là serial leaf trong hệ thập phân (KHÔNG phải chuỗi hex).
# Thay SERIAL_DEC bằng giá trị tính từ Serial number của leaf.pem.
keytool -gencrl -alias ca -id SERIAL_DEC:1 -file revoked.crl -keystore ca.p12 -storetype PKCS12 -storepass:env LAB_PASS -keypass:env LAB_PASS
```

`keytool -printcert` thường hiển thị serial bằng **hexadecimal** (ví dụ `7d52...`), còn `-id` của tiện ích lab này nhận **số serial thập phân**. Khi dùng JDK hỗ trợ, chuyển hex sang decimal trước khi thay `SERIAL_DEC`. Ví dụ snippet trong JShell:

```java
import java.math.BigInteger;
new BigInteger("7d52d72e6d72b894", 16).toString();
// Ví dụ này in "9030516797235968148" cho serial hex trên.
```

**Ví dụ serial trên chỉ là số minh họa**, mỗi chứng chỉ mới có serial khác. `:1` là revocation reason code của chức năng lab này. Vì `-gencrl` không nằm trong mọi tài liệu chính thức `keytool`, **không phụ thuộc vào lệnh này trong quy trình sản xuất** và không coi nó là một CA revocation management system. Nếu JDK không hỗ trợ, dùng công cụ CA chuẩn phát hành CRL.

**Bước C — kiểm tra fixture:** dùng `keytool -printcrl -file clean.crl` và `-printcrl -file revoked.crl` để xem nội dung, sau đó chạy chương trình. Xác minh đúng CA issuer, serial của leaf trong CRL thứ hai và độ tươi trước khi dùng. Các file chỉ để test và phải được tạo/lưu ở **thư mục lab riêng**, mật khẩu lab không phải credential thật.

## 11. Tích hợp với `PKIXParameters` và dữ liệu CRL/OCSP

### Truyền CRL vào checker qua `CertStore`

Một `CertStore` kiểu `Collection` là cách tiện dụng để kiểm thử offline (đã dùng ở mục 9). Với nhiều CRL và intermediate certificates, có thể thêm vào collection:

```java
CertStore store = CertStore.getInstance(
        "Collection",
        new CollectionCertStoreParameters(
                List.of(intermediateCert, currentCrl)));
params.addCertStore(store);
```

Đây là snippet giả định `intermediateCert`, `currentCrl` và `params` đã được khai báo đúng kiểu; cần import `java.security.cert.*` và `java.util.List`. `CertStore` giúp PKIX tìm certificate/CRL, không có nghĩa mọi object thêm vào collection đều được tin. PKIX vẫn phải kiểm tra issuer, signature, constraints và thời điểm theo thuật toán của provider.

### Cấu hình OCSP responder tường minh

```java
PKIXRevocationChecker checker =
        (PKIXRevocationChecker)
                CertPathValidator.getInstance("PKIX").getRevocationChecker();

checker.setOcspResponder(URI.create("https://ocsp.example.org"));
checker.setOptions(EnumSet.noneOf(PKIXRevocationChecker.Option.class));
params.addCertPathChecker(checker);
```

Đây là snippet minh họa **không độc lập**, trong đó `params` là `PKIXParameters` đã có trust anchors và chain đang được validate; `example.org` là placeholder URL tài liệu **không phải service thật**. Với checker mặc định (không `PREFER_CRLS`), API ưu tiên OCSP, có thể dùng CRL fallback. Responder thực phải được CA/protocol phê duyệt; nếu cần delegated responder, sử dụng `setOcspResponderCert(X509Certificate)` và tin cậy responder theo quy tắc RFC/PKIX tương ứng.

Có thể đưa OCSP response đã nhận được (đã được hệ thống fetch/staple đúng cách) vào `checker.setOcspResponses(Map<X509Certificate, byte[]>)`. Đây là **DER-encoded OCSP response bytes theo certificate**, không phải mảng boolean `good`. Engine vẫn cần kiểm tra chữ ký, responder authority, CertID và time validity theo implementation. Không tự tạo `good` bằng cách đưa chuỗi “true” vào map.

**Phân biệt:** `setOcspResponder(URI)` chỉ chọn endpoint, **không khẳng định request đã được gửi hoặc response đã được kiểm chứng**. Chỉ một lần `validator.validate(certPath,params)` thực hiện actual validation (với dữ liệu và cấu hình hợp lệ).

### Sự khác nhau giữa explicit checker và security properties

Theo Java 17 PKI Programmer's Guide của Oracle/SUN implementation:

- `PKIXParameters.setRevocationEnabled(true)` bật **default revocation mechanism** của provider (mặc định là true khi tạo `PKIXParameters`).
- Một số cấu hình provider mặc định dùng security property `ocsp.enable` (trong `java.security` hoặc `Security.setProperty`): có thể khiến default mechanism dùng OCSP rồi CRL fallback khi đã bật revocation. Nếu property không bật, con đường mặc định có thể dùng CRL. 
- `PKIXRevocationChecker` được lấy trực tiếp từ PKIX validator có **contract riêng**: mặc định ưu tiên OCSP và fallback CRL theo Javadoc, bất kể cách cấu hình legacy default mechanism.

Do đó không nói “OCSP mặc định luôn bật trong toàn bộ Java” hoặc “chỉ cần `ocsp.enable=true` là mọi TLS client đã check CRL/OCSP”. **Phải xác định API path và provider cụ thể**; cách cấu hình trong một framework có thể dùng trust manager/builder khác hẳn ví dụ manual `CertPathValidator`.

### CRL Distribution Points và truy vấn mạng

Oracle PKI Programmer's Guide mô tả system property `com.sun.security.enableCRLDP=true` để bật cách tự tìm CRL theo CDP trong SUN provider; một số cấu hình tắt mặc định để tương thích. Khi đã đưa CRL qua `CertStore` và dùng `PREFER_CRLS`, bài demo **không cần mạng**, điều này giúp test có kết quả ổn định.

Tính năng tự fetch CDP/OCSP cần xem xét:

- **Network egress** và proxy: responder có thể là host ngoài Internet.
- **Timeout và retry**: thư viện có defaults riêng; `com.sun.security.crl.timeout` là ví dụ property được tài liệu Oracle nêu cho CRL, có tính chất provider-specific.
- **SSRF và metadata leakage**: certificate có thể do peer gửi, URL có thể không an toàn. Bật tự động truy vấn URL từ chứng chỉ chưa tin cần có boundary egress.
- **Cache và đồng hồ**: dữ liệu cached chỉ dùng khi phù hợp freshness/clock skew. Ngắt clock NTP có thể làm một response bị đánh giá không hợp lệ.
- **Phạm vi check**: cert lá, intermediate và chain; không chỉ một certificate tiện xem đầu tiên.

Tránh chỉnh `java.security` global chỉ để làm một test nếu có thể cấu hình qua API riêng. Việc thay security property runtime có thể ảnh hưởng nhiều component và vẫn không có hiệu lực nếu implementation đã cache settings. Test bằng một process chuyên biệt trong lab, không bật vào cả cụm production trước khi có kế hoạch.

## 12. Phân loại lỗi `CertPathValidatorException`

Đối với đường PKIX đã cấu hình revocation, `CertPathValidator.validate(...)` có thể ném `CertPathValidatorException`. Đọc `getReason()`, `getIndex()` và `getCause()` thay vì chỉ in message:

| Reason (ví dụ) | Ý nghĩa | Chính sách ứng dụng |
| --- | --- | --- |
| `BasicReason.REVOKED` | Chứng chỉ được xác nhận bị thu hồi | **Từ chối**; không fallback thành success |
| `BasicReason.UNDETERMINED_REVOCATION_STATUS` | Thiếu bằng chứng xác định trạng thái thu hồi | Từ chối hoặc soft-fail **nếu policy cho phép và được đo lường** |
| `BasicReason.EXPIRED` | Certificate hết hạn | Từ chối theo policy hiện hành; không phải “CRL lỗi” |
| `BasicReason.NOT_YET_VALID` | Chứng chỉ chưa đến thời gian hợp lệ | Từ chối |
| `BasicReason.INVALID_SIGNATURE` | Signature của certificate/path không phù hợp | Từ chối; không hạ cấp sang soft-fail |
| `BasicReason.ALGORITHM_CONSTRAINED` | Algorithm/key vi phạm security constraints | Từ chối, sửa policy/certificate chứ không bỏ check |
| `BasicReason.UNSPECIFIED` hoặc reason khác | Lỗi khác/cụ thể provider | Ghi nhận có kiểm soát và điều tra |

`getIndex()` là index certificate theo thứ tự `CertPath`, không phải row trong keystore. Giá trị `-1` có thể xảy ra khi lỗi không xác định được một chứng chỉ cụ thể. Không dùng `BasicReason.REVOKED` như cách phát hiện mọi trường hợp “invalid”; phạm vi của enum được phân biệt rõ.

**Code anti-pattern:**

```java
try {
    validator.validate(path, params);
    return true;
} catch (CertPathValidatorException error) {
    return true; // SAI: bỏ qua mọi thất bại, gồm REVOKED!
}
```

Snippet minh họa lỗi, không phải code được khuyên dùng; `validator`, `path` và `params` giả định đã khai báo. Nếu chính sách yêu cầu fail-closed, một lỗi revocation không rõ phải ngăn accept. Nếu có soft-fail được phê duyệt, chỉ cho qua đúng phân loại lỗi được policy chấp nhận, giới hạn thời gian/trạng thái và ghi telemetry.

## 13. Chính sách khi OCSP hoặc CRL unavailable

### Fail-closed / hard-fail

**Fail-closed** nghĩa là không cho phép hoạt động yêu cầu chứng chỉ tin cậy khi trạng thái thu hồi cần kiểm tra mà **không xác định được**. Lợi ích: không cho attacker vô hiệu hóa kiểm tra bằng cách chặn responder. Đánh đổi: một sự cố CA responder/egress có thể gây gián đoạn ứng dụng.

Các bước giảm ảnh hưởng: cache hợp lệ theo policy, CRL/OCSP fallback có xác minh, egress dự phòng, timeout hữu hạn, capacity planning, liveness/health monitoring, xử lý thông báo lỗi rõ ràng và runbook phục hồi.

### Soft-fail có kiểm soát

**Soft-fail** cho phép tiếp tục trong những tình huống đặc biệt **không xác định được** theo danh sách lỗi cho phép, nhưng chỉ khi đã chấp nhận rủi ro. Không phải `REVOKED`. Ví dụ một ứng dụng internet-facing có thể ưu tiên availability ở một số use case, nhưng hệ thống quản trị khóa, ký phát hành phần mềm hoặc internal mTLS nhạy cảm có thể đặt yêu cầu khác.

Nếu áp dụng `SOFT_FAIL`:

- Ghi lại **đã soft-fail** chứ không báo người vận hành “OCSP GOOD”.
- Đặt scope ứng dụng, endpoint/role và thời hạn policy rõ ràng.
- Tăng monitoring cho thời gian dài responder/CRL unavailable.
- Định nghĩa giới hạn cache TTL, retry/timeout và xử lý khi status thay đổi thành `REVOKED`.
- Xem lại security implications nếu một attacker có thể chủ động làm gián đoạn kết nối responder.

### Không có một cấu hình đúng cho mọi giao thức

| Bối cảnh | Vấn đề cần quyết định |
| --- | --- |
| mTLS nội bộ cho hệ thống quản trị/production key access | Tính toàn vẹn danh tính có thể ưu tiên hard-fail; bố trí revocation distribution nội bộ có sẵn cao |
| TLS client của ứng dụng công cộng | Cân bằng UX, OCSP availability, hiệu ứng responder outages, client/provider behavior |
| Ký số tài liệu/phần mềm dài hạn | Cần chứng cứ trạng thái tại **thời điểm ký**, timestamp/certification policy và lưu trữ OCSP/CRL evidence theo chuẩn áp dụng |
| Offline signing verification | Cần dữ liệu revocation đã thu thập từ trước và xác minh freshness/validation time phù hợp |
| Hệ thống không có kết nối mạng | Không thể “hỏi OCSP online” lúc verify; đưa CRL/OCSP evidence vào trust bundle hoặc thiết kế exception policy có phê duyệt |

Không vội nâng `SOFT_FAIL` thành mặc định trên toàn bộ máy chủ chỉ để giảm số lỗi log. Nếu revocation là yêu cầu bảo mật không thể nhượng bộ, Unknown phải được từ chối.

## 14. Tích hợp trong TLS/JSSE: đừng nhầm manual PKIX validation với handshake

Các ví dụ ở trên gọi `CertPathValidator` trực tiếp. Chúng **không tự thiết lập** cấu hình kiểm tra thu hồi cho:

- `HttpsURLConnection` / `java.net.http.HttpClient` đang sử dụng `SSLContext` mặc định.
- Server nhận mTLS qua `SSLServerSocket` hoặc framework.
- Ứng dụng dùng `X509TrustManager` riêng/được tạo qua `TrustManagerFactory`.
- Thư viện HTTP/TLS có trình xác minh riêng hoặc dùng native TLS stack.

Nếu TLS của ứng dụng cần revocation checking, phải cấu hình đúng **trust manager / PKIXBuilderParameters / CertPath policy** mà TLS stack sử dụng, kèm trust anchors, hostname verification và revocation options. Một cách tiếp cận tùy provider là khởi tạo `TrustManagerFactory` với `CertPathTrustManagerParameters` chứa `PKIXBuilderParameters` đã cấu hình; cần kiểm tra hỗ trợ của algorithm/provider và hành vi thực tế trước khi sử dụng.

Ví dụ khung cấu hình (snippet, không chạy độc lập):

```java
PKIXBuilderParameters params =
        new PKIXBuilderParameters(trustAnchors, targetSelector);
params.setRevocationEnabled(false);
params.addCertPathChecker(configuredRevocationChecker);

TrustManagerFactory tmf =
        TrustManagerFactory.getInstance("PKIX");
tmf.init(new CertPathTrustManagerParameters(params));
```

`trustAnchors`, `targetSelector`, `configuredRevocationChecker` là các đối tượng phải được tạo từ **nguồn tin cậy và policy đã phê duyệt**; ở một số provider, có thể phải cấu hình target constraints hoặc dùng các constructor khác. Không coi snippet là cách duy nhất cho mọi JSSE triển khai. Nếu dùng `SSLEngine` và custom trust manager, vẫn cần hostname verification/endpoint identification theo giao thức.

**Quan trọng:** các property như `ocsp.enable`, `com.sun.security.enableCRLDP` hoặc `-D...` không chứng minh điều gì cho TLS client/server cho đến khi bạn test end-to-end với certificate chưa thu hồi, đã thu hồi, responder timeout và certificate chain thiếu intermediate. Test bằng chính framework và JVM/runtime đưa lên production.

## 15. Kiểm thử và vận hành revocation checking

### Bộ test tối thiểu

| Test | Điều kiện | Kết quả được kỳ vọng khi hard-fail |
| --- | --- | --- |
| Happy path CRL | Leaf hợp lệ, CRL signed/fresh, không có serial | PKIX validate thành công |
| Revoked leaf | CA đưa leaf serial vào CRL hợp lệ | `REVOKED` và reject |
| Missing CRL + no OCSP | Không có nguồn xác định trạng thái | Không được accept trong hard-fail |
| Expired/stale CRL | `nextUpdate` đã qua hoặc freshness không đạt | Không coi dữ liệu cũ là bằng chứng GOOD |
| Wrong issuer CRL | CRL không do issuer được ủy quyền ký | Reject, không dùng membership |
| Forged CRL signature | Byte CRL bị sửa, chữ ký không hợp lệ | Reject hoặc fail validation theo provider |
| OCSP `good` hợp lệ | Đúng CertID, signature/responder trust, timestamp | Vượt qua **revocation layer**, tiếp tục các PKIX/TLS checks |
| OCSP `revoked` | Responder có thẩm quyền báo revoked | Reject dù có đường CRL fallback |
| OCSP `unknown` | Không có status đáng tin | Không coi unknown là good |
| OCSP network failure | Timeout/`tryLater` | Fallback nếu được phép và nguồn thay thế hợp lệ; ngược lại hard-fail |
| Chứng chỉ intermediate bị revoke | Intermediate nằm trong chain | Reject nếu policy kiểm tra CA cert |
| Kiểm tra chỉ leaf | `ONLY_END_ENTITY` | Xác định rõ rủi ro bỏ kiểm tra intermediate và test policy |
| Sai hostname/SAN | PKIX path có thể hợp lệ | TLS identity verifier vẫn phải reject |
| Thay JDK/provider | Provider khác, options/fetch/timeouts khác | Chạy lại integration tests |

Các test CRL/OCSP cần dữ liệu và responder **của môi trường lab**. Đừng tạo/chỉnh sửa CRL public CA hoặc sử dụng mTLS endpoint production làm đích thử tấn công; mục đích là tái tạo hành vi bảo mật trong môi trường bạn quản lý.

### Telemetry hữu ích, không lộ dữ liệu quá mức

- `algorithm=PKIX`, provider/JDK version, profile checker/options, loại nguồn (CRL/OCSP/cache/stapled), thời gian kiểm tra, timeout.
- Reason enum khi fail, certificate index, CA/trust anchor identifier theo policy, status responder (không log URL nhạy cảm nếu có rủi ro).
- Phân biệt **REVOKED / UNDETERMINED / OTHER_INVALID**. Ghi rõ số lần fallback và soft-fail được chấp nhận.
- Đo p50/p95/p99 latency và tỷ lệ lỗi mỗi issuer/responder để phát hiện sự cố nguồn thu hồi.
- Giới hạn log có chứa subject/SAN/serial và response bytes để tránh thu thập thông tin cá nhân/khóa/metadata quá mức.

Để debug trên Oracle/OpenJDK, có thể bật **chỉ trong môi trường kiểm soát**:

```bash
java -Djava.security.debug=certpath YourApplication
```

Đây là cơ chế debug của implementation; log có thể nhiều và chứa thông tin certificate/path. Không bật trên hệ thống thật trong thời gian dài khi chưa kiểm tra tác động logging/performance và dữ liệu nhạy cảm.

## 16. Lỗi thường gặp và cách sửa

| Lỗi | Vì sao sai | Cách làm đúng |
| --- | --- | --- |
| Chỉ kiểm tra `checkValidity()` rồi kết luận certificate trusted | Không xét chain, trust anchor, revocation, purpose | Dùng PKIX validation và verification tầng ứng dụng |
| `getRevokedCertificate(serial)==null` ⇒ GOOD ngay | CRL có thể stale, sai issuer, thiếu phạm vi hoặc giả mạo | Validate CRL/path và rule của RFC 5280 |
| Tạo trust anchor từ root CA do peer gửi | Attacker có thể tự phát hành CA giả | Trust anchor phải từ config/truststore đã phê duyệt |
| Đưa root vào `CertPath` rồi lại cấu hình như trust anchor | Hiểu sai path trust boundary | CertPath leaf/intermediates, anchor truyền riêng |
| Bật `setRevocationEnabled(false)` rồi quên checker | Vô hiệu hóa revocation theo đường mặc định | Đặt checker explicit hoặc giữ cơ chế mặc định theo policy |
| Set options sau `params.addCertPathChecker(checker)` | Parameters clone checker | Set đầy đủ trước khi add |
| Dùng `PREFER_CRLS` và tưởng không bao giờ hỏi OCSP | Vẫn có fallback mặc định | Dùng `NO_FALLBACK` nếu yêu cầu CRL-only |
| Dùng `SOFT_FAIL` và coi REVOKED được bỏ qua | Sai contract Java | REVOKED vẫn reject; soft-fail cho lỗi không xác định có giới hạn |
| Chỉ test CRL sạch, không test CRL revoked | Không chứng minh revocation thực sự hoạt động | Test cả CRL clean và revoked, xác minh reason |
| Dùng CRL từ issuer khác | Không đại diện trạng thái của cert | Match issuer/AKI/CRL scope và kiểm tra signature |
| Trộn OCSP response của serial/issuer khác | Status không áp dụng cho cert cần kiểm tra | Kiểm tra CertID, responder authority, freshness |
| Ký số OCSP response đúng nhưng responder không được ủy quyền | Signature đúng không đủ trust | Kiểm tra delegation/EKU, issuer và policy |
| Nhầm `unknown` thành `good` khi OCSP server down | Attacker có thể khai thác outage | Thực thi fail policy minh bạch, có telemetry |
| Bật CDP fetch không giới hạn egress | Rủi ro SSRF, latency, data leakage | Network policy, proxy, timeout, allowlist khi phù hợp |
| Coi `PKIXRevocationChecker` của manual validator đã bật cho toàn JSSE | Pipeline TLS có thể khác | Kiểm thử trust-manager/framework thực |
| Tin `getSoftFailExceptions` trên checker ban đầu luôn là checker đã dùng | `PKIXParameters` clone checker | Instrumentation/provider-aware diagnostics |
| Không kiểm tra intermediate revocation | CA trung gian có thể đã bị thu hồi | Tránh `ONLY_END_ENTITY` trừ khi có lý do |
| Không cập nhật chứng cứ revocation của chữ ký lưu dài hạn | Trạng thái hôm nay khác thời điểm ký lịch sử | Thiết kế timestamp/archival validation theo tiêu chuẩn phù hợp |
| Hard-fail nhưng không có cache/timeout | Sự cố CA gây outage lớn | Cache có hạn, monitoring, fallback hợp lệ, budget |
| Test với CA/CRL thật không được phép | Gây rủi ro vận hành | Sử dụng fixture và responder riêng trong môi trường lab |

## 17. Bài tập tự đánh giá

1. **Phân biệt trạng thái:** một cert còn hạn nhưng có serial trong CRL hợp lệ. Kết quả phải là gì? Nếu không thể tải CRL và OCSP cũng timeout thì khác `REVOKED` thế nào?
2. **Đọc CRL:** chỉ ra issuer, `thisUpdate`, `nextUpdate`, revoked serial và lý do trên CRL lab. Vì sao đọc `revokedCertificates` mà không xác minh CRL issuer/signature/freshness là không đủ?
3. **Hiểu OCSP:** tại sao `good` khác “TLS hostname và chain hoàn toàn hợp lệ”? Những điều kiện nào phải kiểm tra đối với delegated responder?
4. **So sánh:** thiết kế CRL distribution cho ứng dụng có hàng triệu client offline; sau đó đề xuất khi nào OCSP/stapling giúp hơn.
5. **Thực hành API:** chạy `RevocationCheckerOptionsDemo` và đổi `PREFER_CRLS` thành chỉ `NO_FALLBACK`. Cơ chế nào trở thành ưu tiên? Tại sao chỉ đặt OCSP URI không đồng nghĩa request đã được gửi?
6. **Thực hành PKIX:** với `OfflineCrlValidationDemo`, giải thích vì sao `root` là trust anchor, `leaf` ở `CertPath` và `params.setRevocationEnabled(false)` không tắt checker explicit.
7. **Revoked test:** tạo CRL có serial leaf, xác nhận `revokedListsLeaf=true` và `revokedPath=REVOKED`. Nếu sửa một byte CRL, outcome phải là accept hay reject?
8. **Policy:** đặt `SOFT_FAIL` trên một endpoint mTLS high-assurance có yêu cầu thu hồi nghiêm ngặt. Trình bày rủi ro và đề xuất runbook khác nếu responder unavailable.
9. **Intermediates:** nếu intermediate CA bị thu hồi nhưng leaf chưa bị liệt kê, tại sao `ONLY_END_ENTITY` có thể nguy hiểm?
10. **SSRF:** certificate leaf có URL CDP trỏ vào địa chỉ mạng nội bộ của server. Thiết kế egress policy và timeout khi ứng dụng dùng CRLDP auto-fetch.
11. **JSSE:** đã chạy demo manual nhưng HTTPS client vẫn chấp nhận cert đã thu hồi. Có thể thiếu bước nào? Cần kiểm thử tầng trust manager và hostname verification ra sao?
12. **Archival:** một tài liệu được ký 5 năm trước, certificate ký hiện nay đã hết hạn. Vì sao xác minh chữ ký lịch sử cần có validation time/timestamp và revocation evidence lưu trữ theo chuẩn riêng?
13. **Phân tích exception:** phân biệt `BasicReason.REVOKED`, `UNDETERMINED_REVOCATION_STATUS`, `INVALID_SIGNATURE` và `ALGORITHM_CONSTRAINED`. Trường hợp nào không thể biến thành thành công bằng retry OCSP?
14. **Đánh giá vận hành:** xác định metric về tỷ lệ revoked, unknown, soft-fail, fallback và latency theo responder. Những metric nào sẽ kích hoạt rollback cấu hình?

**Tiêu chí hoàn thành:** bạn giải thích đúng **revoked khác unknown**, biết CRL/OCSP phải được kiểm tra bằng nguồn tin cậy và độ tươi, tạo và cấu hình `PKIXRevocationChecker` theo options chính xác, xử lý path với trust anchor ngoài CertPath, kiểm thử được accept/revoked trên fixture, và hiểu rằng cần tích hợp/kiểm chứng riêng trên TLS stack production.

## Tài liệu chính thức

- [Java SE 17 — `PKIXRevocationChecker` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/PKIXRevocationChecker.html): lựa chọn OCSP/CRL, fallback, setOcspResponder và các checker methods.
- [Java SE 17 — `PKIXRevocationChecker.Option`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/PKIXRevocationChecker.Option.html): `PREFER_CRLS`, `NO_FALLBACK`, `ONLY_END_ENTITY`, `SOFT_FAIL`.
- [Java SE 17 — `PKIXParameters`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/PKIXParameters.html): trust anchors, CertStore, revocation enabled và thêm custom checker.
- [Java SE 17 — `CertPathValidatorException.BasicReason`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertPathValidatorException.BasicReason.html): `REVOKED`, `UNDETERMINED_REVOCATION_STATUS` và những lý do khác.
- [Oracle Java 17 — PKI Programmer's Guide](https://docs.oracle.com/en/java/javase/17/security/java-pki-programmers-guide.html): PKIX, CertStore, CRL Distribution Points và OCSP properties của SUN provider.
- [Java SE 17 — `X509CRL`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/X509CRL.html), [`CertificateFactory`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertificateFactory.html), [`CertStore`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertStore.html).
- [RFC 5280 — X.509 Certificate and CRL Profile](https://www.rfc-editor.org/rfc/rfc5280), đặc biệt thuật toán path/CRL processing.
- [RFC 6960 — Online Certificate Status Protocol](https://www.rfc-editor.org/rfc/rfc6960), thay thế RFC 2560 và mô tả OCSP status/responses.
- [Java SE 17 — `keytool`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html): phát hành certificate lab và lệnh đọc CRL.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cert-path-007">14.7 Chính sách khi không lấy được trạng thái thu hồi và hệ quả của SOFT_FAIL</a>

<details>
<summary>Xem chi tiết</summary>

# 14.7 Chính sách khi không lấy được trạng thái thu hồi và hệ quả của SOFT_FAIL

## Mục tiêu học tập

Sau bài này, bạn có thể:

1. Phân biệt ba tình huống: chứng thư **chưa bị thu hồi theo bằng chứng hợp lệ**, chứng thư **đã bị thu hồi**, và chứng thư **không thể xác định trạng thái thu hồi**.
2. Đánh giá lựa chọn **hard-fail/fail-closed** so với **soft-fail có điều kiện**, nhất là khi OCSP/CRL hoặc mạng đang gặp sự cố.
3. Hiểu chính xác `PKIXRevocationChecker.Option.SOFT_FAIL` trong Java 17, các trường hợp được phép bỏ qua, và tương tác với `NO_FALLBACK`, `PREFER_CRLS`, `ONLY_END_ENTITY`.
4. Cấu hình `CertPathValidator`, `PKIXParameters`, `TrustAnchor` và `PKIXRevocationChecker`; nắm vai trò của `getSoftFailExceptions()`.
5. Chạy hai bài lab Java 17: một OCSP responder không liên lạc được gây `UNDETERMINED_REVOCATION_STATUS` ở chế độ strict nhưng được soft-fail; một CRL được ký hợp lệ xác nhận `REVOKED` và vẫn bị từ chối dù bật SOFT_FAIL.
6. Thiết kế chính sách theo cấp độ rủi ro, giới hạn fallback/cache/timeout, giám sát soft-fail và kiểm thử việc không chấp nhận chứng thư đã thu hồi.

## 1. Thu hồi chứng thư khác với xác minh chữ ký, thời hạn hoặc danh tính

**X.509 certificate** có thể được ký đúng bởi CA, chưa hết hạn, có Subject Alternative Name phù hợp, nhưng **vẫn đã bị thu hồi trước `notAfter`** do private key bị xâm phạm, thiết bị bị mất, dịch vụ thay đổi chủ sở hữu, hoặc các lý do quản trị PKI khác.

```text
Leaf certificate + chain
            |
            v
Kiểm chữ ký, trust anchor, thời hạn, policy,
KeyUsage/EKU, hostname/SAN...
            |
            v
Kiểm trạng thái thu hồi bằng OCSP/CRL
            |
     +------+------+ 
     |      |      |
    good  revoked  undetermined
     |      |      |
 tiếp tục REJECT   áp dụng chính sách
```

**Undetermined không phải good.** Một timeout mạng, DNS lỗi hoặc OCSP responder không trả lời cho biết client **thiếu bằng chứng trạng thái hiện tại**, chứ không phải CA đã xác nhận chứng thư còn tốt.

| Tình trạng | Bản chất | Xử lý |
| --- | --- | --- |
| **Not-revoked theo OCSP/CRL hợp lệ** | Bằng chứng thu hồi đáng tin, đúng chứng thư, còn hiệu lực và đúng phạm vi không chỉ ra revoked | Có thể tiếp tục các điều kiện PKIX còn lại |
| **REVOKED** | Có bằng chứng hợp lệ xác nhận chứng thư bị thu hồi | **Từ chối** |
| **UNDETERMINED** | Không lấy/xác minh đủ bằng chứng để kết luận | Hard-fail hoặc soft-fail theo chính sách/API |
| **Sai chữ ký/chain/CA, hết hạn, hostname không khớp** | Lỗi độc lập với kênh thu hồi | **Từ chối**, SOFT_FAIL không hợp thức hóa |
| **Không có responder hoặc CRL hợp lệ** | Thiếu bằng chứng, có thể do cấu hình hoặc profile | Không mặc định coi là good |

`X509Certificate.checkValidity()` chỉ kiểm `notBefore` và `notAfter`; không tự truy vấn OCSP hoặc CRL. Ngược lại, revocation check không thay thế xác minh chain, identity hay các ràng buộc của chứng thư.

## 2. Cơ chế cung cấp trạng thái: OCSP và CRL

### 2.1 OCSP theo RFC 6960

**Online Certificate Status Protocol (OCSP)** cho phép hỏi một OCSP responder về trạng thái của chứng thư xác định bởi certificate ID. Cần kiểm chữ ký/ủy quyền responder, certificate ID đúng đối tượng, và độ mới của thông tin.

Khi phản hồi OCSP xử lý thành công, `SingleResponse.certStatus` có ba loại:

- **`good`**: theo phạm vi của responder, chứng thư chưa được xác nhận là revoked. RFC 6960 nêu rằng điều này **không tự chứng minh chứng thư đã từng được cấp** hoặc trust chain hợp lệ.
- **`revoked`**: chứng thư bị thu hồi; có profile cho phép trạng thái này áp dụng trường hợp non-issued. Verifier phải từ chối.
- **`unknown`**: responder không biết chứng thư/issuer được hỏi. Đây là **một trạng thái có cấu trúc**, không phải đồng nghĩa network error.

Khi OCSP response bị lỗi xử lý, `responseStatus` có thể là:

```text
successful (0)
malformedRequest (1)
internalError (2)
tryLater (3)
sigRequired (5)
unauthorized (6)
```

Trong nhóm trên, `internalError` và `tryLater` có ý nghĩa riêng trong quy tắc Java SOFT_FAIL. **Không gom `unknown`, `malformedRequest`, `unauthorized` và `tryLater` vào một kiểu “không trả lời”**.

Các mốc `thisUpdate`, `nextUpdate`, `producedAt` giúp đánh giá độ mới/chữ ký của response. Một response `good` **đã stale** không thể tự động được dùng như chứng cứ `good` hiện tại.

### 2.2 CRL theo RFC 5280

**Certificate Revocation List (CRL)** là danh sách các serial bị thu hồi được ký bởi CA hoặc CRL issuer được ủy quyền. Kiểm tra có giá trị đòi hỏi:

- Xác minh **chữ ký CRL**, authority và quyền ký `cRLSign` theo profile.
- Kiểm tra **issuer/scope**, issuing distribution point, reason masks, delta CRL nếu được dùng.
- Kiểm tra `thisUpdate`/`nextUpdate` và độ mới, không sử dụng một bản CRL quá hạn làm chứng cứ sạch.
- Tra serial chứng thư theo phạm vi issuer, không dùng “CRL bất kỳ không thấy serial” để kết luận tốt.
- Xử lý chứng thư trung gian/CA theo chính sách; không chỉ kiểm leaf nếu luồng yêu cầu mọi thành phần path.

OCSP và CRL có đặc điểm khác nhau; một hệ thống có thể ưu tiên một kênh và dự phòng bằng kênh kia. Nhưng **đều phải xác thực nguồn chứng cứ** và độ mới trước khi dùng kết quả.

## 3. Hard-fail và soft-fail phân bổ rủi ro khác nhau

### 3.1 Hard-fail / fail-closed

Nếu không thể xác định trạng thái thu hồi theo chính sách, kết nối hoặc thao tác phải **bị từ chối**.

```text
OCSP/CRL timeout
      |
      v
status undetermined
      |
      v
HARD-FAIL --> REJECT
```

Ưu điểm: đối thủ khó dùng việc chặn liên lạc tới responder để đưa một chứng thư đã bị thu hồi vào sử dụng. Nhược điểm: lỗi DNS, proxy, mạng nội bộ, CA responder hoặc outage diện rộng có thể khiến các chứng thư hợp lệ bị từ chối, gây gián đoạn dịch vụ.

### 3.2 Soft-fail / fail-open có điều kiện

Với soft-fail, **một số lỗi không lấy được trạng thái thu hồi** có thể bị bỏ qua để quá trình xác minh tiếp tục, miễn không có lỗi PKIX khác.

```text
OCSP/CRL tạm thời không truy cập được
      |
      v
status undetermined
      |
      v
SOFT-FAIL cho phép bỏ qua lỗi theo nhóm quy định
      |
      v
có thể tiếp tục xác minh / chấp nhận path
```

Ưu điểm: cải thiện availability khi responder hoặc mạng gặp trục trặc.

**Hệ quả bảo mật:** giả sử private key đã bị lộ và chứng thư tương ứng đã bị CA thu hồi. Nếu kẻ tấn công làm hỏng đường kết nối tới OCSP/CRL hoặc chờ lúc responder down, một verifier cấu hình soft-fail có thể **tiếp tục chấp nhận chứng thư cũ dù chưa thể nhận tin thu hồi**. Đây là sự đánh đổi có thật, không phải “cùng mức bảo mật nhưng ít lỗi kết nối hơn”.

| Trạng thái | Hard-fail | Soft-fail có điều kiện |
| --- | --- | --- |
| Bằng chứng hợp lệ xác nhận chưa revoked | Tiếp tục điều kiện khác | Tiếp tục điều kiện khác |
| Bằng chứng hợp lệ xác nhận **REVOKED** | **Từ chối** | **Từ chối** |
| Network error thuộc phạm vi được cho phép | **Từ chối** | Có thể tiếp tục, phải ghi nhận thiếu bằng chứng |
| OCSP `internalError` hoặc `tryLater` được phép xử lý mềm | **Từ chối** nếu hết nguồn khác | Có thể tiếp tục theo quy tắc fallback |
| OCSP `unknown` | Chưa xác nhận tình trạng | Không tự động được phân loại như network error |
| Chứng thư hết hạn, sai chữ ký, chain không trusted | **Từ chối** | **Từ chối** |

Bảng thể hiện mô hình quyết định. Kết quả thực tế phụ thuộc `PKIXRevocationChecker`, provider, tùy chọn fallback và nguồn chứng cứ sẵn có.

## 4. Semantics chính xác của Java 17 `PKIXRevocationChecker.Option.SOFT_FAIL`

Tài liệu Oracle Java SE 17 giới hạn SOFT_FAIL vào các tình huống:

1. **CRL hoặc OCSP response không lấy được do lỗi mạng**.
2. **OCSP responder trả `internalError` hoặc `tryLater`** theo giao thức.

Đây không phải cờ “bỏ qua tất cả lỗi thu hồi”. **Chứng thư biết chắc đã bị thu hồi phải bị từ chối**; lỗi chữ ký/chain/algorithm/time không tự trở thành hợp lệ.

Theo API `PKIXRevocationChecker.Option`, khi không dùng `NO_FALLBACK`, checker chỉ có thể soft-fail khi **cả OCSP và CRL đều thất bại theo một trong những nguyên nhân được cho phép** trong phạm vi cơ chế kiểm tra. Nếu nguồn dự phòng hoạt động và xác nhận revoked, phải reject.

### 4.1 Các option có ảnh hưởng trực tiếp

| Option | Ý nghĩa | Cần cân nhắc |
| --- | --- | --- |
| `SOFT_FAIL` | Bỏ qua một số lỗi không xác định do mạng/OCSP tạm thời | Có thể chấp nhận path chưa có revocation evidence |
| `NO_FALLBACK` | Vô hiệu cơ chế dự phòng | Tăng phụ thuộc kênh ưu tiên, không phù hợp mọi production |
| `PREFER_CRLS` | Ưu tiên CRL thay vì OCSP | Thay thứ tự truy cập, phụ thuộc CRL có sẵn |
| `ONLY_END_ENTITY` | Chỉ check leaf, không check trạng thái thu hồi các CA trong path | Có thể trái yêu cầu revocation của intermediate CA |

Mặc định trong mô tả API là **ưu tiên OCSP, fallback CRL**. `PREFER_CRLS` đảo ưu tiên; `NO_FALLBACK` tắt fallback.

```java
PKIXRevocationChecker checker =
        (PKIXRevocationChecker) validator.getRevocationChecker();
checker.setOptions(EnumSet.of(
        PKIXRevocationChecker.Option.SOFT_FAIL,
        PKIXRevocationChecker.Option.NO_FALLBACK));
```

Đây là **đoạn code nhúng**, cần import và biến `validator`, không phải chương trình độc lập. Cặp cờ `SOFT_FAIL + NO_FALLBACK` được dùng trong bài lab để tái hiện rõ một kênh OCSP lỗi, **không phải công thức mặc định nên áp dụng sản xuất**.

### 4.2 `setRevocationEnabled(false)` có thể **không** tắt checker

Tài liệu `PKIXRevocationChecker` nêu rõ: khi thêm checker tường minh vào `PKIXParameters` bằng `addCertPathChecker`, checker vẫn được dùng **bất kể giá trị `revocationEnabled`**.

```java
CertPathValidator validator = CertPathValidator.getInstance("PKIX");
PKIXRevocationChecker checker =
        (PKIXRevocationChecker) validator.getRevocationChecker();

PKIXParameters params = new PKIXParameters(trustAnchors);
params.setRevocationEnabled(false);
params.addCertPathChecker(checker);
validator.validate(certPath, params);
```

Đây là code minh họa từng đoạn; cần `trustAnchors`, `certPath` và imports. `setRevocationEnabled(false)` trong trường hợp này vô hiệu **luồng checker mặc định**, không vô hiệu checker đã thêm bằng API. Ngược lại, tắt flag mà **không thêm checker** có thể khiến ứng dụng không kiểm thu hồi. Cần kiểm tra cấu hình tổng thể, không nhìn một dòng.

### 4.3 `getSoftFailExceptions()` để phân biệt “good” với “bỏ qua lỗi”

`PKIXRevocationChecker.getSoftFailExceptions()` trả về danh sách **`CertPathValidatorException` đã bị bỏ qua** vì SOFT_FAIL.

- Danh sách unmodifiable và được **xóa mỗi lần checker `init`**.
- Theo API, các exception được sắp theo **certificate index**.
- Có thể dùng để ghi nhận lượt validate có bỏ qua lỗi mạng nào, khác với việc đã có revocation evidence hợp lệ.

**Caveat:** `PKIXParameters.addCertPathChecker` **clone checker** và provider có thể quản lý/khởi tạo lại các instance. Vì vậy không giả định mọi `checker` tham chiếu đều phản ánh chính xác lượt validation; test đúng JDK/provider trong production. Cũng không chia sẻ checker mutable giữa các request/threads vì API không bảo đảm thread-safe.

Một metric “`soft fail exceptions = 0`” **không chứng minh đã check revocation**: nếu ai đó vô tình tắt kiểm tra hoàn toàn, nó cũng có thể bằng 0.

## 5. Phân biệt reason trong `CertPathValidatorException`

`CertPathValidatorException.BasicReason` trên Java 17 gồm:

- `UNDETERMINED_REVOCATION_STATUS` — trạng thái thu hồi không xác định được.
- `REVOKED` — chứng thư đã bị thu hồi.
- `EXPIRED` / `NOT_YET_VALID` — ngoài khoảng validity.
- `INVALID_SIGNATURE` — chữ ký không hợp lệ.
- `ALGORITHM_CONSTRAINED` — thuật toán/key bị ràng buộc chính sách.

**Không tự viết đoạn catch “gặp undetermined thì return true”**:

```java
// PHẢN VÍ DỤ, KHÔNG DÙNG TRONG SẢN PHẨM
try {
    validator.validate(path, params);
    return true;
} catch (CertPathValidatorException e) {
    return e.getReason() ==
            CertPathValidatorException.BasicReason.UNDETERMINED_REVOCATION_STATUS;
}
```

Đây là đoạn **chống ví dụ**, không tự compile. Nó lách cơ chế phân loại của checker, có thể bỏ qua điều kiện fallback và không bảo đảm các lỗi đã có trước/sau. Hãy để checker/validator thực thi policy đã được cấu hình; chỉ chấp nhận kết quả sau **toàn bộ kiểm tra path**, không nuốt mọi exception chung.

## 6. Thực hành 1 — Java 17: OCSP không kết nối được

Ta tạo **CA và leaf certificate riêng cho lab**, rồi ép OCSP responder về `http://127.0.0.1:1/ocsp`. Trong môi trường kiểm thử, không có dịch vụ nào lắng nghe cổng 1, nên request trả lỗi kết nối. Không truy vấn hệ thống bên ngoài.

**Kết quả mong đợi trong lab:** strict trả `UNDETERMINED_REVOCATION_STATUS`; SOFT_FAIL có thể validate và ghi nhận 1 lỗi bị bỏ qua. Nếu trên máy bạn cổng 1 có service hoặc provider xử lý khác, kết quả/exception count có thể thay đổi; chọn một cổng loopback không có responder thực sự và kiểm tra cấu hình.

### 6.1 Chuẩn bị CA/leaf bằng `keytool`

Chạy trong **thư mục thử nghiệm trống** bằng JDK 17+, không dùng keystore production:

```bash
keytool -genkeypair -alias labca -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -dname "CN=Lab Root CA,O=Example Lab,C=VN" -validity 180 -ext bc:c -ext ku=keyCertSign,cRLSign -keystore ca.p12 -storetype PKCS12 -storepass changeit -keypass changeit -noprompt

keytool -exportcert -alias labca -keystore ca.p12 -storetype PKCS12 -storepass changeit -rfc -file ca.pem

keytool -genkeypair -alias leaf -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -dname "CN=lab.local,O=Example Lab,C=VN" -validity 30 -keystore leaf.p12 -storetype PKCS12 -storepass changeit -noprompt

keytool -certreq -alias leaf -keystore leaf.p12 -storetype PKCS12 -storepass changeit -file leaf.csr

keytool -gencert -alias labca -keystore ca.p12 -storetype PKCS12 -storepass changeit -infile leaf.csr -outfile leaf.pem -rfc -validity 30 -ext ku=digitalSignature -ext EKU=serverAuth -ext SAN=dns:lab.local
```

`ca.p12` và `leaf.p12` chứa **private keys chỉ để thực hành**; không commit, chia sẻ, triển khai. Password `changeit` chỉ dành cho lab. `ca.pem` là public CA certificate dùng làm trust anchor **tường minh trong lab**, còn `leaf.pem` là leaf certificate do CA lab ký. Việc import một CA tùy ý từ network làm trust anchor của sản phẩm là **nguy hiểm**.

### 6.2 Chương trình `RevocationPolicyLab.java`

```java
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.*;
import java.util.*;

public class RevocationPolicyLab {
    private static X509Certificate load(String filename) throws Exception {
        try (InputStream stream = Files.newInputStream(Path.of(filename))) {
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(stream);
        }
    }

    private static void check(X509Certificate leaf, X509Certificate root,
                              boolean allowSoftFail) throws Exception {
        CertPathValidator validator = CertPathValidator.getInstance("PKIX");
        PKIXRevocationChecker checker =
                (PKIXRevocationChecker) validator.getRevocationChecker();

        checker.setOcspResponder(URI.create("http://127.0.0.1:1/ocsp"));
        checker.setOptions(allowSoftFail
                ? EnumSet.of(PKIXRevocationChecker.Option.NO_FALLBACK,
                             PKIXRevocationChecker.Option.SOFT_FAIL)
                : EnumSet.of(PKIXRevocationChecker.Option.NO_FALLBACK));

        PKIXParameters params = new PKIXParameters(
                Set.of(new TrustAnchor(root, null)));
        params.setRevocationEnabled(false);
        params.addCertPathChecker(checker);

        CertPath certPath = CertificateFactory.getInstance("X.509")
                .generateCertPath(List.of(leaf));
        try {
            validator.validate(certPath, params);
            System.out.println((allowSoftFail ? "soft" : "strict") +
                    ": VALIDATED");
        } catch (CertPathValidatorException e) {
            System.out.println((allowSoftFail ? "soft" : "strict") +
                    ": REJECTED reason=" + e.getReason());
        }
        System.out.println("original checker ignored: " +
                checker.getSoftFailExceptions().size());
        for (PKIXCertPathChecker candidate : params.getCertPathCheckers()) {
            if (candidate instanceof PKIXRevocationChecker) {
                System.out.println("params checker ignored: " +
                        ((PKIXRevocationChecker) candidate)
                                .getSoftFailExceptions().size());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        X509Certificate leaf = load(args[0]);
        X509Certificate root = load(args[1]);
        leaf.verify(root.getPublicKey());
        System.out.println("leaf signed by trusted CA: true");
        check(leaf, root, false);
        check(leaf, root, true);
    }
}
```

Biên dịch và chạy cùng thư mục `leaf.pem`/`ca.pem`:

```bash
javac --release 17 RevocationPolicyLab.java
java RevocationPolicyLab leaf.pem ca.pem
```

Đầu ra **đã được chạy và kiểm chứng** trên JDK/provider thử nghiệm:

```text
leaf signed by trusted CA: true
strict: REJECTED reason=UNDETERMINED_REVOCATION_STATUS
original checker ignored: 0
params checker ignored: 0
soft: VALIDATED
original checker ignored: 1
params checker ignored: 1
```

**Giải thích:**

1. `leaf.verify(root.getPublicKey())` kiểm chữ ký leaf theo khóa của CA lab. Nó không tự chứng minh root trusted; chương trình **cố ý tạo trust anchor** `new TrustAnchor(root,null)`.
2. `setOcspResponder` **ghi đè** endpoint OCSP trong AIA/thuộc tính bảo mật nếu có; địa chỉ loopback là một cách giả lập lỗi mạng.
3. `NO_FALLBACK` tắt khả năng thử CRL dự phòng; nhờ đó lượt strict thất bại khi không hỏi được OCSP.
4. Lượt có `SOFT_FAIL` bỏ qua lỗi kết nối thuộc phạm vi API; path có thể được xác thực thành công dù **chưa có OCSP good**.
5. Trên JDK thử nghiệm, `getSoftFailExceptions()` ghi nhận lỗi đã bỏ qua; không giả định con số hay trạng thái object giống hệt trên mọi provider hoặc qua mọi lượt `init`.
6. `params.setRevocationEnabled(false)` không chặn checker đã thêm tường minh. Nếu check thực sự bị tắt, lượt strict đã không có reason về revocation như stdout.

Điều lab chứng minh là **sự khác biệt chính sách khi thiếu bằng chứng**. Không triển khai cứng `NO_FALLBACK` hay loopback OCSP trong ứng dụng thật. Nên cho provider lấy nguồn OCSP/CRL có thẩm quyền và áp dụng policy/monitoring đã duyệt.

## 7. Thực hành 2 — SOFT_FAIL vẫn từ chối chứng thư REVOKED

Để chứng minh SOFT_FAIL **không chấp nhận bằng chứng thu hồi rõ ràng**, dùng lại `ca.p12`/`ca.pem`/`leaf.pem` ở trên. Sinh một CRL do CA lab ký, liệt kê **serial thật** của leaf, thêm CRL vào `CertStore` và kiểm bằng `PREFER_CRLS + NO_FALLBACK + SOFT_FAIL`.

### 7.1 Tạo CRL với serial của chứng thư

`keytool -gencrl -id` nhận serial decimal. Vì serial X.509 có thể vượt `long`, ta đọc serial hex từ `keytool -printcert` và dùng `BigInteger` để đổi sang decimal.

Các lệnh sau dùng **PowerShell 5.1+** trong thư mục lab:

```powershell
$serialLine = (keytool -printcert -file leaf.pem |
    Select-String 'Serial number:' | Select-Object -First 1).ToString()
$serialHex = ($serialLine -split 'Serial number:')[1].Trim()
$serialDecimal = [System.Numerics.BigInteger]::Parse(
    ('0' + $serialHex),
    [System.Globalization.NumberStyles]::AllowHexSpecifier
).ToString()
keytool -gencrl -alias labca -keystore ca.p12 -storepass changeit -file revoked.crl -rfc -id $serialDecimal
```

`revoked.crl` chỉ là CRL lab. Thời điểm/serial thay đổi qua mỗi lần tạo. **Không dùng lệnh này với CA production**; bài lab tạo/sử dụng các khóa thử nghiệm riêng.

### 7.2 Chương trình `KnownRevokedLab.java`

```java
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.*;
import java.util.*;

public class KnownRevokedLab {
    public static void main(String[] args) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        X509Certificate leaf;
        X509Certificate ca;
        X509CRL crl;
        try (InputStream in = Files.newInputStream(Path.of(args[0]))) {
            leaf = (X509Certificate) factory.generateCertificate(in);
        }
        try (InputStream in = Files.newInputStream(Path.of(args[1]))) {
            ca = (X509Certificate) factory.generateCertificate(in);
        }
        try (InputStream in = Files.newInputStream(Path.of(args[2]))) {
            crl = (X509CRL) factory.generateCRL(in);
        }

        crl.verify(ca.getPublicKey());
        System.out.println("CRL signature verifies: true");
        System.out.println("leaf on CRL: " + crl.isRevoked(leaf));

        CertPathValidator validator = CertPathValidator.getInstance("PKIX");
        PKIXRevocationChecker checker =
                (PKIXRevocationChecker) validator.getRevocationChecker();
        checker.setOptions(EnumSet.of(
                PKIXRevocationChecker.Option.PREFER_CRLS,
                PKIXRevocationChecker.Option.NO_FALLBACK,
                PKIXRevocationChecker.Option.SOFT_FAIL));

        PKIXParameters params = new PKIXParameters(
                Set.of(new TrustAnchor(ca, null)));
        params.setRevocationEnabled(false);
        params.addCertStore(CertStore.getInstance("Collection",
                new CollectionCertStoreParameters(List.of(crl))));
        params.addCertPathChecker(checker);
        CertPath path = factory.generateCertPath(List.of(leaf));

        try {
            validator.validate(path, params);
            System.out.println("soft fail accepts revoked: true");
        } catch (CertPathValidatorException e) {
            System.out.println("soft fail rejects revoked: " +
                    (e.getReason() ==
                            CertPathValidatorException.BasicReason.REVOKED));
            System.out.println("reason: " + e.getReason());
        }
    }
}
```

Biên dịch và chạy sau khi tạo `revoked.crl`:

```bash
javac --release 17 KnownRevokedLab.java
java KnownRevokedLab leaf.pem ca.pem revoked.crl
```

Đầu ra đã kiểm chứng với CA và CRL lab:

```text
CRL signature verifies: true
leaf on CRL: true
soft fail rejects revoked: true
reason: REVOKED
```

Giải thích:

- `crl.verify(ca.getPublicKey())` xác minh chữ ký CRL; trong sản phẩm còn phải kiểm authority/scope/time/distribution point và các yêu cầu CRL khác.
- `crl.isRevoked(leaf)` xác nhận leaf được liệt kê.
- Checker đã bật `SOFT_FAIL` nhưng kết quả PKIX vẫn là `REVOKED`, vì **bằng chứng thu hồi hợp lệ không phải network error**.
- `PREFER_CRLS + NO_FALLBACK` cố ý chọn kênh CRL để test, không phản ánh toàn bộ chính sách production.

## 8. Tại sao SOFT_FAIL có thể bị khai thác qua lỗi mạng?

Một kịch bản nguy hiểm:

1. Private key của một thiết bị hoặc service bị đánh cắp.
2. CA phát hiện sự cố và công bố trạng thái `revoked` qua OCSP/CRL.
3. Kẻ tấn công vẫn có certificate cũ và private key bị lộ.
4. Đúng lúc verifier xác thực, kết nối tới nguồn revocation gặp lỗi (có thể do attacker tác động hoặc outage ngẫu nhiên).
5. Nếu chính sách soft-fail cho phép đi qua khi thiếu chứng cứ, service có thể **tiếp tục chấp nhận certificate đã bị thu hồi**.

Điều này cho thấy tính sẵn sàng của responder là một thành phần của mô hình bảo mật. Việc chặn mạng không phá khóa riêng CA nhưng có thể **làm suy yếu quyết định revocation** nếu verifier fail-open.

Rủi ro tăng lên khi:

- Soft-fail được bật ở quy mô toàn hệ thống, không phân biệt privilege.
- Không có timeout/budget; trạng thái không xác định kéo dài hàng ngày.
- Không giám sát hoặc xem mọi `validator.validate() == success` là chứng cứ not-revoked.
- Không có CRL/OCSP mirror hoặc nguồn fallback đã kiểm tra.
- Dùng `ONLY_END_ENTITY` dù chính sách cần kiểm CA trung gian.
- Cấu hình ứng dụng nuốt exception chung rồi cho phép đăng nhập bất kỳ.
- Một lỗi DNS/proxy được attacker gây có chủ đích nhưng bị xem như sự cố vận hành thông thường.

Giải pháp không nhất thiết là “mọi nơi hard-fail”. Cần đánh giá **giá trị tài sản**, tính sẵn sàng, phạm vi authority và cơ chế dự phòng sao cho soft-fail nếu có là ngoại lệ **được phê duyệt và quan sát được**.

## 9. Chính sách theo mức độ rủi ro

| Hệ thống/ngữ cảnh | Hướng thiết kế thường phù hợp | Điều kiện |
| --- | --- | --- |
| Xác thực mTLS của service có quyền quản trị, thanh toán, HSM, ký giao dịch | **Hard-fail** khi không xác định được revocation nếu policy bắt buộc | Nguồn chứng cứ dự phòng/caching còn hạn, báo lỗi rõ, tránh outage không cần thiết |
| Cấp phát chứng thư thiết bị, truy cập hệ quản trị/đặc quyền | **Hard-fail**, có phương án xử lý outage có kiểm soát | Giữ độ tin cậy key/issuer/chain, không tự bỏ kiểm tra |
| Client kết nối dịch vụ công cộng, ít đặc quyền | Có thể **soft-fail có điều kiện** theo chuẩn áp dụng/threat model | Ghi audit, giới hạn thời gian, không bỏ qua revoked |
| Máy offline theo chu kỳ, có CRL/OCSP response đã cache | Chỉ dùng bằng chứng **còn hiệu lực**; sau đó áp dụng policy offline rõ | Định nghĩa max age, thiết bị được phép và cách khôi phục |
| Mạng nội bộ có CRL mirror/OCSP proxy quản lý | Ưu tiên nguồn đáng tin cậy/có sẵn | Xác minh chữ ký và freshness, không chỉ tin endpoint vì nằm trong mạng nội bộ |
| Chuỗi CA trung gian có quyền ký nhiều service | Thường cần kiểm toàn bộ path theo policy | Không vô tình bỏ CA revocation bằng `ONLY_END_ENTITY` |

Không có một cấu hình mặc định luôn đúng cho tất cả. Quy định ngành, đặc tả giao thức hoặc policy nội bộ có thể bắt buộc hard-fail hoặc cách lấy chứng cứ cụ thể.

### 9.1 Nếu chọn soft-fail, phải định nghĩa ngoại lệ

Một quyết định soft-fail có kiểm soát cần mô tả:

- **Lỗi hợp lệ**: chỉ những nguyên nhân `SOFT_FAIL` hỗ trợ; không coi revoked/invalid response thành lỗi “nhẹ”.
- **Phạm vi**: loại workload, certificate/CA, tài khoản, quyền và thao tác được phép; ví dụ read-only có thể có policy khác thao tác cấp quyền.
- **Thời hạn**: bao lâu có thể tiếp tục khi mất bằng chứng mới; ngưỡng liên tiếp và điều kiện phục hồi.
- **Nguồn thay thế**: cơ chế fallback có ký và còn hiệu lực, mirror/cache được giám sát.
- **Đo lường**: lần validation, lý do, thời điểm, responder, tỉ lệ soft-fail và độ tuổi bằng chứng.
- **Dừng khẩn cấp**: ngưỡng chuyển hard-fail hoặc phong tỏa thao tác nhạy cảm; kiểm soát sự cố CA/key leak.
- **Người phê duyệt**: chủ quản PKI/security, lịch sử thay đổi policy và yêu cầu kiểm tra định kỳ.

**Soft-fail không đồng nghĩa return true khi mạng hỏng.** Toàn bộ chain, identity và các kiểm tra khác vẫn phải đạt.

## 10. Fallback, cache, retry và timeout

### 10.1 Fallback phải dựa trên bằng chứng có hiệu lực

Nếu OCSP không trả lời, CRL còn hiệu lực có thể là nguồn kiểm tra thay thế; ngược lại, nếu CRL không truy xuất được, OCSP hợp lệ có thể giúp xác nhận. Nhưng fallback **không được** dùng chứng cứ sai chữ ký, sai issuer, hết hạn hoặc không bao phủ certificate.

`NO_FALLBACK` giúp cô lập hành vi trong bài lab nhưng **có thể giảm availability lẫn khả năng tìm được tình trạng revoked** khi nguồn ưu tiên lỗi.

### 10.2 Cache không đồng nghĩa giữ “good” vô hạn

- OCSP có `thisUpdate`, `nextUpdate`, `producedAt`.
- CRL có `thisUpdate` và thường `nextUpdate` theo profile.
- Cache status `good` **quá hạn** không còn là bằng chứng mới đáng tin. Nếu policy cho phép tiếp tục, phải ghi rõ đó là **ngoại lệ degraded**, không phải “OCSP currently good”.
- Không tự đặt TTL cao hơn giới hạn freshness của nguồn và không coi `HTTP 200` là bằng chứng có chữ ký hợp lệ.

### 10.3 Timeout và retry có thể biến outage thành DoS

Một responder bị chậm có thể khiến hàng nghìn request/handshake cùng chờ hoặc retry. Vì vậy cần:

- Timeout DNS/TCP/HTTP/OCSP hữu hạn theo SLO và chính sách.
- Backoff/jitter, giới hạn retry và concurrency, tránh thundering herd.
- Cache/deduplicate các request đang xác thực cùng một certificate.
- Circuit breaker có kết quả **explicit** (fail closed hoặc approved soft-fail), không giả lập success.
- Kiểm soát network egress tới URL CRL/AIA vì thông tin URL có thể đến từ certificate ngoài; tránh truy xuất mạng nội bộ tùy ý.
- Theo dõi đồng hồ và độ mới của response. Clock drift làm certificate hoặc chứng cứ status bị xem sai thời hạn.

Một timeout quá ngắn làm tăng soft-fail; quá dài gây tăng latency và rủi ro DoS. Phải benchmark trên môi trường và provider thật.

## 11. OCSP stapling và must-staple là vấn đề riêng

**OCSP stapling** cho phép server cung cấp OCSP response trong quá trình TLS handshake, giúp client không cần hỏi responder trực tiếp trong nhiều trường hợp. Tuy nhiên client vẫn phải kiểm chữ ký, responder authority, đúng certificate và freshness.

**TLS Feature extension — RFC 7633** đôi khi được gọi là “must-staple”. Đây là tín hiệu/protocol constraint riêng, **không cùng ý nghĩa** với Java `PKIXRevocationChecker.Option.SOFT_FAIL`.

Không mặc định rằng một Java JSSE/HTTPS client sẽ tự enforce must-staple chỉ vì có `SOFT_FAIL`/`setRevocationEnabled`. Nếu chính sách TLS của sản phẩm yêu cầu revocation hoặc stapling, phải kiểm tra trust manager/provider, các security properties và **test end-to-end**. Bài lab `CertPathValidator` standalone chỉ chứng minh behavior của API PKIX đã cấu hình, không chứng minh mọi HTTP/TLS client trên máy đều tuân theo.

## 12. Giám sát và xử lý sự cố

Một số metric có ích:

| Metric / field đề xuất | Vai trò |
| --- | --- |
| `pkix_soft_fail_total` | Tổng lượt bỏ qua lỗi revocation do chính sách |
| `revocation_check_total{method,outcome}` | Phân biệt OCSP/CRL, good/revoked/undetermined/error |
| `ocsp_crl_error_total{endpoint,reason}` | Phân tích DNS, timeout, HTTP, responder error |
| `revocation_evidence_age_seconds` | Cảnh báo bằng chứng sắp stale/hết hạn |
| `revocation_next_update_remaining_seconds` | Quản lý thời hạn cache hợp lệ |
| `cert_validation_total{reason}` | Phân biệt `REVOKED`, `EXPIRED`, `INVALID_SIGNATURE`, `UNDETERMINED` |
| `soft_fail_duration_seconds` | Theo dõi thời gian vận hành trong trạng thái degraded |

Trong log audit có thể lưu CA/issuer ID, certificate serial/fingerprint, responder, phiên bản policy và basic reason. Cần kiểm soát quyền truy cập; không log private key/credential; tránh đưa DN hoặc serial từng người dùng vào nhãn metric cardinality cao không kiểm soát.

**Lưu ý:** metric soft-fail bằng 0 không chứng minh checker đang bật. Cần **synthetic test** với một certificate đã revoked để bảo đảm hệ thống thật từ chối nó ngay cả khi xảy ra lỗi mạng.

### 12.1 Runbook khi responder mất khả dụng

1. Xác nhận là lỗi responder/DNS/proxy/circuit hay lỗi PKIX khác; không vội tắt toàn bộ validation.
2. Kiểm tra fallback CRL/OCSP và cache **còn hạn, đúng issuer/scope**.
3. Phân nhóm flow đặc quyền cao; ưu tiên hard-fail nếu chưa có policy ngoại lệ hợp lệ.
4. Nếu một nhóm được phép soft-fail, ghi rõ chủ thể phê duyệt, thời hạn, tài sản ảnh hưởng, metrics và ngưỡng kết thúc.
5. Khôi phục responder hoặc đồng bộ chứng cứ mới; xác nhận tỉ lệ soft-fail giảm.
6. Sau outage, đối chiếu log để xác định những lượt đã được chấp nhận **khi revocation chưa xác định** và xem có cần thu hồi quyền/rotate certificate hay không.

## 13. Ma trận kiểm thử bắt buộc

| Tình huống | Kỳ vọng |
| --- | --- |
| Chain đáng tin, OCSP/CRL hợp lệ xác nhận không revoked | Tiếp tục nếu các điều kiện khác đạt |
| OCSP hợp lệ xác nhận `revoked` | REJECT dù SOFT_FAIL |
| CRL hợp lệ liệt kê leaf revoked | REJECT dù SOFT_FAIL (thực hành 2) |
| OCSP network error, `NO_FALLBACK`, strict | `UNDETERMINED_REVOCATION_STATUS` (thực hành 1) |
| OCSP network error, `NO_FALLBACK`, SOFT_FAIL | Có thể validate; audit được lỗi bỏ qua (thực hành 1) |
| OCSP lỗi nhưng CRL dự phòng còn hiệu lực | Sử dụng kết quả CRL, không bỏ qua sớm |
| OCSP `internalError` / `tryLater` | Theo phạm vi SOFT_FAIL và quy tắc fallback |
| OCSP `unknown` hoặc `unauthorized` | Không tự xem là network error |
| OCSP response ký sai, sai serial/issuer, sai thời điểm | Không chấp nhận như bằng chứng good |
| CRL quá hạn hoặc không bao phủ certificate | Không xem là revocation verified |
| Certificate hết hạn hoặc trust anchor sai | REJECT độc lập SOFT_FAIL |
| Intermediate CA bị revoked | Theo scope path/policy, không bỏ qua ngoài ý muốn |
| Outage dài / failover / đổi JDK/provider | Kiểm tra timeout, config và behavior thực tế |
| TLS client production | Test full handshake/trust manager, không chỉ unit test PKIX |

Nếu có ngoại lệ phát sinh, test phải thất bại và đưa vào quy trình điều tra chứ không chấp nhận bằng cách thêm `catch(Exception) return true`.

## 14. Sai lầm phổ biến

1. **“OCSP không liên lạc được nghĩa là không bị thu hồi.”** Sai: đó là trạng thái không xác định.
2. **“SOFT_FAIL bỏ qua mọi lỗi certificate.”** Sai: chỉ một số lỗi mạng/OCSP tạm thời theo API.
3. **“Đã biết REVOKED vẫn có thể cho qua vì SOFT_FAIL.”** Sai: REVOKED luôn reject.
4. **“OCSP unknown giống tryLater.”** Sai: một bên là status chứng thư, một bên là lỗi responder.
5. **“NO_FALLBACK càng an toàn vì ít request.”** Không mặc nhiên: mất nguồn thay thế có thể khiến không có chứng cứ.
6. **“setRevocationEnabled(false) chắc chắn không còn checker.”** Sai khi thêm checker tường minh.
7. **“getSoftFailExceptions rỗng chứng minh certificate good.”** Không; có thể checker không chạy hoặc chưa đúng instance/lượt.
8. **“Có OCSP good là tin được website.”** Không; còn PKIX chain, identity, EKU, algorithm constraints.
9. **“Cache good có thể dùng đến khi certificate hết hạn.”** Sai; response có thời hạn riêng.
10. **“Tăng retry vô hạn là tăng tính an toàn.”** Sai; gây availability/DoS, không khắc phục responder sai.
11. **“Có OCSP stapling là Java tự bắt buộc mọi client phải thực thi.”** Phụ thuộc implementation và policy.
12. **“Dùng SOFT_FAIL cho administrator và guest như nhau.”** Thiếu phân loại rủi ro.
13. **“Metric validate success chứng minh đã có revocation evidence.”** Sai: soft-fail có thể cho success khi thiếu bằng chứng.
14. **“Không tìm thấy cert trong một CRL bất kỳ nghĩa là good.”** Sai: cần scope/issuer/time/authority đúng.
15. **“Bắt UNDETERMINED ở tầng ngoài rồi trả true tương đương PKIX SOFT_FAIL.”** Sai: làm mất điều kiện/semantics bảo mật của checker.

## 15. Câu hỏi tự kiểm tra

**1.** `UNDETERMINED_REVOCATION_STATUS` và `REVOKED` có giống nhau không?  
**Đáp:** Không. Undetermined là chưa có đủ bằng chứng; revoked là đã xác nhận bị thu hồi và phải từ chối.

**2.** Java SOFT_FAIL cho phép những nguyên nhân nào?  
**Đáp:** Lỗi mạng khi lấy OCSP/CRL và OCSP `internalError`/`tryLater`, có ràng buộc fallback; không phải mọi lỗi.

**3.** SOFT_FAIL có làm certificate đã có trong CRL hợp lệ trở thành đáng tin không?  
**Đáp:** Không. Lab CRL trả reason `REVOKED` dù SOFT_FAIL đang bật.

**4.** `NO_FALLBACK` có vai trò gì?  
**Đáp:** Tắt việc chuyển sang nguồn kiểm tra thu hồi dự phòng, khiến thất bại ở kênh được chọn có thể mang tính quyết định.

**5.** `PREFER_CRLS + NO_FALLBACK` có ý nghĩa gì?  
**Đáp:** Ưu tiên CRL và không fallback sang OCSP.

**6.** Vì sao `setRevocationEnabled(false)` trong ví dụ vẫn check OCSP?  
**Đáp:** Vì một `PKIXRevocationChecker` đã được thêm tường minh vào `PKIXParameters` và API quy định nó được dùng bất kể flag đó.

**7.** `getSoftFailExceptions()` dùng để làm gì?  
**Đáp:** Đọc các lỗi mà checker bỏ qua vì SOFT_FAIL, hỗ trợ audit nếu truy cập đúng instance sau validation.

**8.** Cache OCSP `good` vượt `nextUpdate` còn có thể báo “verified now” không?  
**Đáp:** Không; phải lấy chứng cứ còn hạn hoặc quyết định ngoại lệ degraded có giới hạn.

**9.** Vì sao dịch vụ mTLS quyền cao thường nghiêng về hard-fail?  
**Đáp:** Chấp nhận một credential đã bị CA thu hồi có thể nghiêm trọng hơn outage tạm thời, nhất là khi attacker cố tình chặn responder.

**10.** Vì sao không thể suy ra toàn bộ JSSE TLS client từ một lab `CertPathValidator`?  
**Đáp:** Trust manager, cấu hình revocation, provider và policy TLS khác nhau; phải kiểm thử tại đúng đường gọi production.

## Tóm tắt

```text
OCSP/CRL:
  VALID not-revoked evidence -> tiếp tục PKIX
  REVOKED evidence           -> REJECT
  UNDETERMINED              -> cần chính sách cụ thể

Java PKIXRevocationChecker.SOFT_FAIL:
  chỉ bỏ qua lỗi thuộc phạm vi được phép
  không bỏ qua REVOKED / sai chain / invalid signature
  ảnh hưởng bởi NO_FALLBACK / PREFER_CRLS
  getSoftFailExceptions giúp quan sát lỗi bỏ qua

Trade-off:
  HARD_FAIL -> bảo vệ tốt hơn trước bypass do mạng hỏng,
               nhưng có nguy cơ outage
  SOFT_FAIL -> cải thiện availability,
               nhưng có thể chấp nhận cert đã revoked nếu
               bằng chứng bị chặn hoặc không truy cập được

Thực hành an toàn:
  phân loại quyền -> chọn policy -> fallback/caching có kiểm chứng
  -> metrics/audit -> giới hạn soft fail -> test revoked thật.
```

**Ghi nhớ quan trọng nhất:** `SOFT_FAIL` không có nghĩa “certificate đã được xác nhận tốt”. Nó là **một ngoại lệ có rủi ro khi không lấy được trạng thái thu hồi**. Nếu áp dụng, phải minh bạch về phạm vi, thời hạn, cách giám sát và cách từ chối khi chứng cứ xác nhận `REVOKED`.

## Tài liệu tham khảo chính thức

- [Oracle Java 17 — PKIXRevocationChecker](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/PKIXRevocationChecker.html): OCSP/CRL, fallback, `getSoftFailExceptions`, clone và thread-safety.
- [Oracle Java 17 — PKIXRevocationChecker.Option](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/PKIXRevocationChecker.Option.html): chính xác các tùy chọn `SOFT_FAIL`, `NO_FALLBACK`, `PREFER_CRLS`, `ONLY_END_ENTITY`.
- [Oracle Java 17 — CertPathValidatorException.BasicReason](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertPathValidatorException.BasicReason.html): `REVOKED` và `UNDETERMINED_REVOCATION_STATUS`.
- [Oracle Java 17 — PKIXParameters](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/PKIXParameters.html): trust anchors và cert-path checkers.
- [Oracle Java 17 — Java PKI Programmer's Guide](https://docs.oracle.com/en/java/javase/17/security/java-pki-programmers-guide.html): sử dụng `PKIXRevocationChecker` với validator/builder.
- [RFC 5280 — Certificate/CRL Profile](https://www.rfc-editor.org/rfc/rfc5280): CRL profile, path validation và revocation.
- [RFC 6960 — OCSP](https://www.rfc-editor.org/rfc/rfc6960): good/revoked/unknown, error responses, freshness/authorized responder.
- [RFC 7633 — TLS Feature Extension](https://www.rfc-editor.org/rfc/rfc7633): TLS feature/must-staple, một cơ chế riêng với Java SOFT_FAIL.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cert-path-008">14.8 Phân tích CertPathValidatorException theo vị trí chứng thư và nguyên nhân</a>

<details>
<summary>Xem chi tiết</summary>

# 14.8 Phân tích CertPathValidatorException theo vị trí chứng thư và nguyên nhân

## Mục tiêu

Bài học thuộc **14. Đường dẫn tin cậy** của module Java Advance – security-cryptography. Sau bài này, bạn sẽ biết cách:

1. Đọc `CertPathValidatorException` của Java bằng **`getIndex()`, `getReason()`, `getCertPath()`, `getCause()` và `getMessage()`**, thay vì chỉ nhìn vào chuỗi “PKIX path validation failed”.
2. Liên hệ **index** với đúng certificate trong `CertPath`, đặc biệt quy tắc thông thường **index 0 = leaf**, index 1 = intermediate gần leaf và **trust anchor không nằm trong path**.
3. Phân biệt mã **`BasicReason`** (hết hạn, chữ ký sai, thu hồi, thuật toán bị hạn chế...) với **`PKIXReason`** (không có trust anchor phù hợp, sai KeyUsage, không phải CA, lỗi name chaining...).
4. Nhận diện **index = -1** là “không gắn được lỗi cho một certificate tại một chỉ số hợp lệ”, **không được tự suy ra root hỏng hoặc index âm là lỗi của root**.
5. Tạo fixture X.509 và chạy chương trình **Java 17+** để xác nhận các tình huống: path hợp lệ; trust anchor không liên quan; chữ ký leaf bị thay; chữ ký intermediate bị thay; leaf hết hạn ở thời điểm kiểm định giả lập.
6. Xây dựng quy trình chẩn đoán an toàn trong hệ thống TLS, PKIX, truststore, CRL/OCSP, chain provisioning và quan sát log mà **không vô hiệu hóa xác minh**.

**Giới hạn:** Một exception không cho phép kết luận “đã bị tấn công” hay “chắc chắn lỗi ở một chứng thư” nếu không đối chiếu path, reason, params và chính sách. Các kết quả `reason/index` trong ví dụ được kiểm thử bằng JDK hỗ trợ PKIX; nhà cung cấp dịch vụ bảo mật và đầu vào khác có thể báo khác. Không dùng cấu hình **tắt revocation** của lab để thay thế chính sách production.

## 1. `CertPathValidatorException` xuất hiện tại đâu?

Trong Java Security, một chuỗi chứng thư số X.509 thường được trình bày từ **certificate end-entity (leaf)** tới một hoặc nhiều **intermediate CA** và kết thúc tại **trust anchor** mà ứng dụng đã tin từ trước. Khi dùng PKIX:

```java
CertPathValidator validator = CertPathValidator.getInstance("PKIX");
CertPathValidatorResult result = validator.validate(certPath, pkixParams);
```

Đoạn này là **snippet**, chưa phải chương trình độc lập: cần import `java.security.cert.CertPathValidator` và `CertPathValidatorResult`; `certPath` phải được dựng đúng thứ tự; `pkixParams` chứa trust anchors và các thông số kiểm định. `validate` kiểm tra path đã cung cấp theo thuật toán PKIX/chính sách, và khi phát hiện lỗi liên quan path thường ném `CertPathValidatorException`.

**Ba thao tác cần phân biệt:**

- `CertificateFactory.generateCertificate(...)` **parse chứng thư** từ DER/PEM. Parse được không đồng nghĩa tin cậy.
- `X509Certificate.verify(issuerPublicKey)` kiểm tra **chữ ký của đúng một certificate** bằng khóa issuer. Không kiểm tra cả chain/hostname/ủy quyền.
- `CertPathValidator.validate(...)` xét **cả path đã có** với trust anchors, constraints và các tham số. Nó vẫn không thay thế kiểm tra **hostname/SAN, expected issuer, mục đích nghiệp vụ và authorization** của ứng dụng.

Không nhầm `CertPathValidator` với `CertPathBuilder`: **builder** tìm/ghép đường dẫn từ certificate và nguồn chứng thư đã được cung cấp; **validator** xác minh một path cụ thể. Nếu không tìm được path, builder có thể ném **`CertPathBuilderException`** thay vì một `CertPathValidatorException` trực tiếp. Lớp lỗi TLS/JDBC/HTTP cao hơn có thể bọc các lỗi này trong `SSLHandshakeException` hoặc ngoại lệ kết nối khác.

### Ví dụ chain thông thường

```text
                 truststore của client (đã chọn trước)
          [Root CA certificate / trust anchor]
                         |
                root CA ký intermediate
                         |
             [Intermediate CA certificate]
                         |
               intermediate CA ký leaf
                         |
            [Leaf: api.example.test]
                         |
              dùng private key cho TLS
```

`TrustAnchor` có thể giữ certificate hoặc tên/khóa CA; **niềm tin bắt đầu từ cấu hình của bên kiểm tra**, không đến từ một root tự ký do peer bất kỳ gửi qua mạng. Trong `CertPath` cho PKIX thông thường, chỉ **leaf và intermediate** được liệt kê; trust anchor cung cấp riêng qua `PKIXParameters`.

## 2. Cấu trúc quan trọng của ngoại lệ

`java.security.cert.CertPathValidatorException` kế thừa `GeneralSecurityException` và cung cấp các getter rất hữu ích:

| API | Giá trị | Cách sử dụng đúng |
| --- | --- | --- |
| `e.getIndex()` | Chỉ số 0-based trong `CertPath` mà exception liên hệ, hoặc **`-1`** | Kiểm tra khoảng `0 <= index < path.size()` trước khi đọc certificate. Không coi `-1` là “root” |
| `e.getCertPath()` | Path liên quan lỗi, hoặc `null` khi không có thông tin | Dùng **path đi cùng exception** để tra certificate, thay vì đoán từ danh sách khác |
| `e.getReason()` | Một `CertPathValidatorException.Reason`, thường là `BasicReason` hoặc `PKIXReason` | Ưu tiên reason enum hơn parse message phụ thuộc provider |
| `e.getCause()` | Nguyên nhân bên dưới nếu có, có thể `null` | Hữu ích khi debug thư viện/validator khác; không tự coi message từ cause là mã lỗi bất biến |
| `e.getMessage()` | Văn bản do implementation cung cấp | Chỉ để hỗ trợ con người; không viết control flow dựa trên so khớp chuỗi |
| `e.getSuppressed()` | Những ngoại lệ bị suppressed nếu có | Kiểm tra khi công cụ bao bọc có sử dụng; không phải cách chính đọc PKIX reason |

**Cảnh báo:** `e.getIndex()` không nhất thiết “trỏ vào certificate gây ra **nguyên nhân vật lý**”. Nó báo **certificate tại đó validator ghi nhận lỗi**, với một lý do cụ thể. Ví dụ certificate leaf có `issuer` không khớp intermediate, validator có thể báo `NAME_CHAINING` tại một vị trí; có thể lỗi ban đầu là hệ thống lấy sai intermediate. Chẩn đoán cần kết hợp path, metadata và policies.

### Index nào là leaf, intermediate, root?

Với path được tạo bằng:

```java
CertificateFactory factory = CertificateFactory.getInstance("X.509");
CertPath path = factory.generateCertPath(
        List.of(leafCertificate, intermediateCertificate));
```

Đây là **snippet** cần có `leafCertificate`/`intermediateCertificate` đã parse và imports `java.security.cert.CertificateFactory`, `CertPath`, `java.util.List`. Java `generateCertPath(List)` giữ thứ tự list. Khi đó:

| Index | Certificate trong path | Vai trò |
| ---: | --- | --- |
| `0` | `leafCertificate` | End-entity/leaf |
| `1` | `intermediateCertificate` | Intermediate trực tiếp ký leaf |
| `2` (nếu có) | Intermediate cấp trên nữa | Đứng gần trust anchor hơn |
| **Không nằm trong path** | Root/trust anchor đã cấu hình | Không được tự gán index `path.size()` hoặc `-1` |

Bất kỳ điều kiện `getIndex() = -1` đều chỉ có nghĩa **index không chỉ định một certificate** (ví dụ không tìm được trust anchor phù hợp trong môi trường thử nghiệm); với path khác hoặc provider khác, tình huống có thể khác. **Index không được xem là một mức độ nghiêm trọng**: lỗi index 0 không “nhẹ hơn” lỗi index 1.

## 3. Hai nhóm `Reason` nên biết

Giá trị `getReason()` có kiểu interface `CertPathValidatorException.Reason`; Java định nghĩa các enum chính sau.

### 3.1 `CertPathValidatorException.BasicReason`

| Reason | Ý nghĩa cơ bản | Kiểm tra đầu tiên |
| --- | --- | --- |
| `UNSPECIFIED` | Không có reason cụ thể hơn được báo | Đọc cause, message, provider/debug log, path và params; **không suy đoán** |
| `EXPIRED` | Certificate bị coi là hết hiệu lực tại **ngày validation** | `NotAfter`, đồng hồ hệ thống, `PKIXParameters.getDate()` |
| `NOT_YET_VALID` | Certificate chưa tới `NotBefore` tại ngày validation | Clock skew, notBefore và thời gian được cấu hình |
| `REVOKED` | Validator đánh giá certificate đã bị thu hồi | CRL/OCSP, revocation policy, revocation date/reason (nếu có) |
| `UNDETERMINED_REVOCATION_STATUS` | Không đủ cơ sở xác định trạng thái thu hồi theo chính sách | Kết nối CRL/OCSP, timeout, responder, cache, fail-open/fail-closed |
| `INVALID_SIGNATURE` | Một chữ ký trong path không xác minh được theo khóa được chọn | Sai intermediate/issuer key, certificate bytes bị sửa, chain pha trộn |
| `ALGORITHM_CONSTRAINED` | Một thuật toán/key bị ràng buộc bởi chính sách bảo mật | JDK security policy, `jdk.certpath.disabledAlgorithms`, key size, digest, thời điểm dùng |

Danh sách này mô tả **ý nghĩa reason**, không bảo đảm một provider nhất định lúc nào cũng gán cùng enum cho cùng tình huống. Một số lỗi có thể thành `UNSPECIFIED` hoặc bị wrapper đổi dạng.

### 3.2 `java.security.cert.PKIXReason`

| Reason | Điều PKIX đang thông báo | Hướng điều tra |
| --- | --- | --- |
| `NO_TRUST_ANCHOR` | Path không nối tới một trust anchor được cung cấp phù hợp | Truststore thực tế, root key ID, CA cấu hình sai, incomplete/misordered chain |
| `NAME_CHAINING` | Liên kết tên issuer ↔ subject giữa certificate trong path không hợp lệ | Intermediate không tương ứng leaf, DN/issuer và key ID |
| `INVALID_KEY_USAGE` | Certificate/key usage không cho phép vai trò cần kiểm tra | `KeyUsage.keyCertSign` đối với CA, profile ký phù hợp |
| `INVALID_POLICY` | Certificate Policies hoặc policy constraints không thỏa mãn | Explicit policy, mapping, profile CA và params |
| `UNRECOGNIZED_CRIT_EXT` | Có critical extension không được validator hiểu/xử lý theo yêu cầu | Xem OID, policy, provider; **không tắt kiểm tra critical extension** |
| `NOT_CA_CERT` | Certificate tại vị trí CA không được phép đóng vai trò CA | `BasicConstraints: CA=true`, certificate profile |
| `PATH_TOO_LONG` | Vi phạm `pathLenConstraint` hoặc ràng buộc chiều sâu đường dẫn | Số CA trung gian (lưu ý self-issued), path builder/issuer chain |
| `INVALID_NAME` | Vi phạm ràng buộc tên, ví dụ `NameConstraints` | DNS/email/URI constraints của các CA trong path |

**`INVALID_NAME` không đồng nghĩa mọi lỗi hostname mismatch sẽ được `CertPathValidator` báo ở đây.** Hostname/SAN cho một HTTPS endpoint thường được kiểm tra **riêng** bởi JSSE/HTTP client sau hoặc trong TLS handshake; path PKIX có thể hợp lệ trong khi hostname sai.

### 3.3 Đừng sử dụng `getMessage()` làm “mã lỗi”

Ví dụ:

```java
if (e.getMessage().contains("expired")) {
    // Không an toàn để điều khiển chương trình theo văn bản provider.
}
```

Đây là minh họa **anti-pattern**, không phải cách nên sử dụng. Message phụ thuộc JDK/provider/ngôn ngữ/nguồn lỗi; có thể `null`, chứa thông tin nhạy cảm hoặc thay đổi sau nâng cấp. Thay vào đó, ưu tiên `e.getReason()` và `e.getIndex()`, rồi phân loại nội bộ. **Không** tự động sửa sai cấu hình truststore hoặc chấp nhận certificate dựa trên một reason string.

## 4. Quy trình đọc ngoại lệ theo thứ tự

Khi bắt được `CertPathValidatorException e`:

1. **Ghi bối cảnh kiểm định đã xác thực:** hệ thống nào, expected hostname/service/tenant, mục đích `serverAuth` hay `clientAuth`, thuật toán validation, JDK/provider version, thời điểm xét và chính sách revocation. Không tin “expected identity” do certificate chưa được xác minh tự khai.
2. **Lấy `reason` từ `e.getReason()`**: nó nêu loại vi phạm ở mức validator.
3. **Lấy `index` và `path` từ chính exception**: nếu index hợp lệ, lấy certificate **tại vị trí đó** để xem `Subject`, `Issuer`, `Serial`, `NotBefore/NotAfter`, BasicConstraints, KeyUsage, SKI/AKI và fingerprint.
4. Nếu **`index=-1`**, nêu rõ “lỗi không được gắn vào một certificate cụ thể”; so trust anchors, path construction, security parameters và error chain, **không gán cho root tùy ý**.
5. **Kiểm tra chain theo chiều leaf → issuer → trust anchor**: đúng intermediate? `Issuer DN` khớp? Chữ ký thật có hợp lệ? `BasicConstraints/KeyUsage` cho CA? Có thiếu intermediate? Có cross-sign/alternate chain?
6. **Kiểm tra chính sách path**: ngày validation, CA constraints, name constraints, các critical extensions, disabled algorithms, certificate policies, trust anchors và revocation.
7. **Kiểm tra endpoint identity và authorization riêng** sau path validation khi use case yêu cầu; không coi `CertPathValidator.validate` thành công là chứng minh hostname/role đúng.
8. **Khắc phục nguồn gốc và thử lại trên dữ liệu chuẩn** (đúng truststore/intermediate/certificate được phát hành lại), không chỉnh sửa chữ ký, tắt revocation hay trust-all để ép thành công.

Với exception nằm trong `SSLHandshakeException`, có thể duyệt `Throwable.getCause()` để tìm `CertPathValidatorException`; có khi lớp trên chỉ ném `CertPathBuilderException` vì không thể tìm được đường dẫn. Đừng lấy mọi lỗi handshake làm lỗi certificate: TLS còn có lỗi protocol/cipher/IO/peer alert.

## 5. Code phân tích index an toàn (snippet có thể tích hợp)

Đoạn **không độc lập** sau nằm trong một phương thức đã có `CertPathValidatorException e`, sử dụng `getCertPath()` thay vì đoán từ mảng input khác:

```java
int index = e.getIndex();
CertPath failedPath = e.getCertPath();
System.out.println("reason=" + e.getReason() + ", index=" + index);

if (failedPath != null
        && index >= 0
        && index < failedPath.getCertificates().size()) {
    X509Certificate bad = (X509Certificate)
            failedPath.getCertificates().get(index);
    System.out.println("subject=" + bad.getSubjectX500Principal());
    System.out.println("issuer=" + bad.getIssuerX500Principal());
    System.out.println("notAfter=" + bad.getNotAfter());
} else {
    System.out.println("No single path certificate index is available.");
}
```

Cần import `java.security.cert.CertPath` và `X509Certificate`. Đoạn này giả định certificate trong path là **X.509**; trong thư viện tổng quát cần kiểm tra `instanceof X509Certificate`. Khi đưa vào logging thật, không dùng `System.out`, tránh đưa nguyên chuỗi subject/issuer chứa dữ liệu cá nhân hoặc raw DER certificate vào log công khai; chỉ log trường cần thiết dưới quyền xem phù hợp.

**Điều quan trọng:** Vị trí `index` có ý nghĩa **trong `failedPath`**. Nếu bạn copy/reverse list, thêm trust anchor vào đầu, hoặc dùng chain theo thứ tự server gửi nhưng không kiểm chứng, bạn có thể báo sai “lỗi tại root” trong khi index thực là leaf.

## 6. Lab Java 17 có thể tái lập: năm kết quả PKIX

Trong lab, tạo:

```text
root.pem (self-signed CA; được chọn làm TrustAnchor)
     |
intermediate.pem (CA=true, được root ký)
     |
leaf.pem (CA=false, SAN=api.example.test, được intermediate ký)

rogue.pem (một self-signed CA độc lập, không ký chain trên)
```

Ta sẽ chạy các biến thể:

| Ca kiểm thử | Thao tác | Kết quả kỳ vọng trong JDK thử nghiệm |
| --- | --- | --- |
| **Valid chain** | Path `[leaf, intermediate]`, trust anchor root | `OK` |
| **Wrong trust anchor** | Cùng path, trust anchor là rogue | `index=-1`, `reason=NO_TRUST_ANCHOR` |
| **Tampered leaf signature** | Đổi bit cuối trong phần chữ ký DER của leaf, giữ nguyên intermediate | `index=0`, `reason=INVALID_SIGNATURE` |
| **Tampered intermediate signature** | Đổi bit cuối chữ ký DER intermediate, giữ nguyên leaf | `index=1`, `reason=INVALID_SIGNATURE` |
| **Expired leaf at future validation date** | `PKIXParameters.setDate` bằng ngày sau leaf NotAfter, trong khi intermediate còn hiệu lực | `index=0`, `reason=EXPIRED` |

Đây là **fixture thử nghiệm tự sinh** không có kết nối tới một máy chủ thực. Thao tác thay bit cuối DER chỉ để minh họa **chữ ký certificate không hợp lệ**; ngoài lab, không sửa certificate/DER để làm nó “hợp lệ”. Đoạn keytool dùng RSA 2048 bit cho sự tương thích lab, không nhằm chọn chính sách lâu dài cho mọi sản phẩm.

### 6.1 Sinh các certificate bằng `keytool` (PowerShell/Windows)

Tạo thư mục tạm riêng và chạy trọn script sau trong PowerShell với JDK 17+ có `keytool.exe` trên `PATH`. Script chỉ tạo fixture trong `%TEMP%\certpath-reason-training`; mật khẩu `changeit` là **dùng cho lab**. Không tái sử dụng keystore này trong môi trường thật.

```powershell
$ErrorActionPreference = 'Continue'
$dir = Join-Path $env:TEMP 'certpath-reason-training'
New-Item -ItemType Directory -Force -Path $dir | Out-Null
Set-Location $dir
$p = 'changeit'

function Run-Keytool([string[]]$arguments) {
    $result = & keytool.exe @arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw ("keytool error: "
            + ($arguments -join ' ') + ' : ' + ($result -join ' '))
    }
}

if (Test-Path 'certs.p12') {
    Remove-Item -LiteralPath 'certs.p12' -Force
}

Run-Keytool @("-genkeypair","-alias","root","-keyalg","RSA",
    "-keysize","2048","-validity","3650",
    "-dname","CN=Lab Root CA,O=Training",
    "-ext","BC=ca:true,pathlen:1",
    "-ext","KU=keyCertSign,cRLSign",
    "-storetype","PKCS12","-keystore","certs.p12",
    "-storepass",$p,"-keypass",$p,"-noprompt")

Run-Keytool @("-genkeypair","-alias","intermediate","-keyalg","RSA",
    "-keysize","2048","-validity","3650",
    "-dname","CN=Lab Intermediate CA,O=Training",
    "-storetype","PKCS12","-keystore","certs.p12",
    "-storepass",$p,"-keypass",$p,"-noprompt")

Run-Keytool @("-certreq","-alias","intermediate",
    "-file","intermediate.csr",
    "-keystore","certs.p12","-storepass",$p)

Run-Keytool @("-gencert","-alias","root",
    "-infile","intermediate.csr",
    "-outfile","intermediate-issued.pem",
    "-rfc","-validity","365",
    "-ext","BC=ca:true,pathlen:0",
    "-ext","KU=keyCertSign,cRLSign",
    "-keystore","certs.p12","-storepass",$p)

Run-Keytool @("-importcert","-alias","intermediate",
    "-file","intermediate-issued.pem",
    "-keystore","certs.p12","-storepass",$p,"-noprompt")

Run-Keytool @("-genkeypair","-alias","leaf","-keyalg","RSA",
    "-keysize","2048","-validity","3650",
    "-dname","CN=api.example.test,O=Training",
    "-storetype","PKCS12","-keystore","certs.p12",
    "-storepass",$p,"-keypass",$p,"-noprompt")

Run-Keytool @("-certreq","-alias","leaf",
    "-file","leaf.csr",
    "-keystore","certs.p12","-storepass",$p)

Run-Keytool @("-gencert","-alias","intermediate",
    "-infile","leaf.csr","-outfile","leaf-issued.pem",
    "-rfc","-validity","7",
    "-ext","SAN=dns:api.example.test",
    "-ext","EKU=serverAuth",
    "-ext","KU=digitalSignature",
    "-keystore","certs.p12","-storepass",$p)

Run-Keytool @("-importcert","-alias","leaf",
    "-file","leaf-issued.pem",
    "-keystore","certs.p12","-storepass",$p,"-noprompt")

Run-Keytool @("-genkeypair","-alias","rogue","-keyalg","RSA",
    "-keysize","2048","-validity","3650",
    "-dname","CN=Other Root CA,O=Training",
    "-ext","BC=ca:true,pathlen:1",
    "-ext","KU=keyCertSign,cRLSign",
    "-storetype","PKCS12","-keystore","certs.p12",
    "-storepass",$p,"-keypass",$p,"-noprompt")

foreach ($alias in @("root","intermediate","leaf","rogue")) {
    Run-Keytool @("-exportcert","-alias",$alias,
        "-rfc","-file",("$alias.pem"),
        "-keystore","certs.p12","-storepass",$p)
}
Write-Output "Four PEM certificate fixture files generated"
```

**Đầu ra cuối trên môi trường đã kiểm thử:**

```text
Four PEM certificate fixture files generated
```

Script tạo bốn file cần cho Java: `root.pem`, `intermediate.pem`, `leaf.pem` và `rogue.pem`. Các file `intermediate-issued.pem` và `leaf-issued.pem` lưu certificate replies, còn `certs.p12` lưu các private key **chỉ phục vụ lab**. Bốn file PEM được xuất bằng `-exportcert` **không chứa private key**.

**Lưu ý:** Nếu keystore chứa aliases cũ hoặc script bị ngắt giữa chừng, nên xóa toàn bộ thư mục **lab vừa tạo** theo quy trình an toàn hoặc chọn thư mục tạm mới. Không xóa tệp khác chỉ vì có tên `certs.p12` ở nơi không phải thư mục lab. Trên macOS/Linux có thể dùng `keytool` tương đương với cú pháp Bash, nhưng đoạn trên dành riêng cho PowerShell.

**Tại sao leaf chỉ có validity 7 ngày?** Để chương trình có thể chọn một validation date **sau `leaf.getNotAfter()`** nhưng vẫn trong hạn intermediate 365 ngày. Nhờ vậy test `EXPIRED` tại index 0 không bị một intermediate hết hạn che khuất.

### 6.2 Chương trình Java 17 độc lập: `CertPathFailureDemo.java`

Lưu tệp **`CertPathFailureDemo.java`** trong đúng thư mục có các PEM và chạy. Code parse các file, dựng path theo thứ tự **leaf rồi intermediate**, gọi PKIX với trust anchor cụ thể, tạo dữ liệu sai **chỉ trong bộ nhớ** để test lỗi chữ ký, và dùng `getIndex/getReason/getCertPath` để báo lỗi.

```java
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Set;

public final class CertPathFailureDemo {
    private static final CertificateFactory FACTORY = certificateFactory();

    public static void main(String[] args) throws Exception {
        X509Certificate root = load("root.pem");
        X509Certificate intermediate = load("intermediate.pem");
        X509Certificate leaf = load("leaf.pem");
        X509Certificate otherRoot = load("rogue.pem");

        CertPath valid = path(leaf, intermediate);
        System.out.println("Path order: index 0=leaf; index 1=intermediate");
        diagnose("Valid chain", valid, root, null);
        diagnose("Wrong trust anchor", valid, otherRoot, null);
        diagnose("Tampered leaf signature",
                path(tamperSignature(leaf), intermediate), root, null);
        diagnose("Tampered intermediate signature",
                path(leaf, tamperSignature(intermediate)), root, null);

        Date future = Date.from(
                leaf.getNotAfter().toInstant().plus(1, ChronoUnit.DAYS));
        diagnose("Expired leaf at future validation date",
                valid, root, future);

        System.out.println("Leaf is CA: " + (leaf.getBasicConstraints() >= 0));
        System.out.println("Intermediate is CA: "
                + (intermediate.getBasicConstraints() >= 0));
    }

    private static CertificateFactory certificateFactory() {
        try {
            return CertificateFactory.getInstance("X.509");
        } catch (GeneralSecurityException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static X509Certificate load(String file) throws Exception {
        try (InputStream input = Files.newInputStream(Path.of(file))) {
            return (X509Certificate) FACTORY.generateCertificate(input);
        }
    }

    private static X509Certificate tamperSignature(X509Certificate source)
            throws Exception {
        byte[] der = source.getEncoded();
        der[der.length - 1] ^= 1;
        try (ByteArrayInputStream input = new ByteArrayInputStream(der)) {
            return (X509Certificate) FACTORY.generateCertificate(input);
        }
    }

    private static CertPath path(X509Certificate leaf,
                                 X509Certificate intermediate)
            throws CertificateException {
        return FACTORY.generateCertPath(List.of(leaf, intermediate));
    }

    private static void diagnose(String label, CertPath path,
                                 X509Certificate trustedRoot, Date date)
            throws GeneralSecurityException {
        PKIXParameters params = new PKIXParameters(
                Set.of(new TrustAnchor(trustedRoot, null)));
        params.setRevocationEnabled(false); // Offline lab ONLY.
        if (date != null) {
            params.setDate(date);
        }

        try {
            CertPathValidator.getInstance("PKIX").validate(path, params);
            System.out.println(label + ": OK");
        } catch (CertPathValidatorException e) {
            int index = e.getIndex();
            String certificate = "none";
            CertPath failedPath = e.getCertPath();
            if (failedPath != null && index >= 0
                    && index < failedPath.getCertificates().size()) {
                X509Certificate subject = (X509Certificate)
                        failedPath.getCertificates().get(index);
                certificate = subject.getSubjectX500Principal()
                        .getName().contains("Intermediate") ?
                        "intermediate" : "leaf";
            }
            System.out.println(label + ": index=" + index
                    + " reason=" + e.getReason()
                    + " cert=" + certificate);
        }
    }
}
```

Lệnh biên dịch/chạy trên PowerShell (vẫn ở thư mục lab):

```powershell
javac --release 17 CertPathFailureDemo.java
java -cp . CertPathFailureDemo
```

**Stdout đã kiểm thử trên JDK Java 17 hỗ trợ PKIX:**

```text
Path order: index 0=leaf; index 1=intermediate
Valid chain: OK
Wrong trust anchor: index=-1 reason=NO_TRUST_ANCHOR cert=none
Tampered leaf signature: index=0 reason=INVALID_SIGNATURE cert=leaf
Tampered intermediate signature: index=1 reason=INVALID_SIGNATURE cert=intermediate
Expired leaf at future validation date: index=0 reason=EXPIRED cert=leaf
Leaf is CA: false
Intermediate is CA: true
```

Trong chương trình, phần tên `cert=leaf` hoặc `cert=intermediate` là **nhãn riêng của fixture**: helper xem subject DN có chuỗi `Intermediate` hay không; không dùng cách nhận diện tên này trong hệ thống thật. Ứng dụng production nên log subject/issuer/fingerprint được **trích từ certificate tại index hợp lệ**, và đối chiếu với expected chain. Đừng điều khiển authorization dựa trên việc subject chứa một từ như “Root” hoặc “Intermediate”.

### 6.3 Tại sao kết quả này đáng tin?

- **Valid chain: OK:** Root đã được **trực tiếp chọn** thành `TrustAnchor`, intermediate có `CA=true`/`keyCertSign` và leaf do intermediate ký. Lab không thực hiện kết nối TLS/hostname.
- **Wrong trust anchor: index=-1 reason=NO_TRUST_ANCHOR:** Root `rogue.pem` tự ký hợp lệ nhưng **không liên quan** chain này. Validator không có anchor phù hợp. **`-1` trong ca này không chỉ một certificate “root lỗi” ở index -1**.
- **Tampered leaf signature: index=0 INVALID_SIGNATURE:** Đổi một bit cuối DER khiến chữ ký leaf không còn khớp public key intermediate. Path vẫn có hai certificate; lỗi gắn ở **leaf (index 0)**.
- **Tampered intermediate signature: index=1 INVALID_SIGNATURE:** Chữ ký intermediate không còn khớp public key root. Leaf có thể vẫn có chữ ký hợp lệ theo public key intermediate; lỗi gắn ở **intermediate (index 1)**.
- **Expired leaf at future validation date: index=0 EXPIRED:** `params.setDate(future)` thay thời điểm validation, không chỉnh certificate bytes. Leaf có thời hạn 7 ngày; intermediate có 365 ngày; ngày giả lập vượt leaf NotAfter.
- **CA checks:** `leaf.getBasicConstraints()<0` và `intermediate.getBasicConstraints()>=0`, phản ánh profile leaf/CA. Không đủ để tự chứng minh trust.

**Đây là kết quả cho fixture đã kiểm thử, không phải bảng ánh xạ bắt buộc với mọi provider.** Trên JDK/provider khác, policy, thời gian tạo, extension, security properties hoặc cách dựng path có thể làm reason/exception thay đổi; **không viết unit test production bắt buộc mọi hệ thống có đúng message string**.

### 6.4 Tại sao phải dùng `getCertPath()` từ exception?

Nếu `CertPathValidator` được gọi qua một thư viện, path thật có thể là path được builder chọn, không phải danh sách certificate mà bạn nghĩ server gửi. `e.getCertPath()` (nếu có) là path được gắn trong exception; tra certificate theo `e.getIndex()` và kiểm tra giới hạn giúp tránh gắn sai lỗi cho một intermediate khác.

Với `index=-1`, code giữ `cert=none`; **không lấy phần tử cuối của path rồi gọi nó là root**, và không cố đọc `path.getCertificates().get(-1)`. Với list certificate rỗng/path null, cũng không cố truy cập theo index.

## 7. `PKIXParameters` quyết định câu trả lời của validator

Hai lần gọi `validate` với **cùng certificate path** có thể cho kết quả khác nhau khi tham số thay đổi. Các tham số quan trọng:

- **`TrustAnchors`:** root/CA key đã được provision từ nguồn tin cậy. Danh sách khác nhau giữa JDK, container, JVM, custom truststore và OS có thể làm kết quả khác.
- **`Date`:** `params.setDate(date)` cho phép kiểm định tại thời điểm xác định; nếu không đặt, thường là thời điểm hiện tại. Dùng thời gian đúng theo hợp đồng hệ thống và xem xét clock skew.
- **`RevocationEnabled` và `PKIXRevocationChecker`:** chính sách CRL/OCSP/cache/responder, độ tin cậy và khi mạng không truy cập được. Trạng thái thu hồi không thể suy ra từ hạn `NotAfter`.
- **`InitialPolicies` / explicit-policy-required:** các certificate policies/constraints được chấp nhận; khác nhau theo purpose, tenant, hệ thống.
- **`CertStores`:** nguồn intermediate/CRL để xây dựng và kiểm tra path, tùy workflow. Validator không tự “sửa” missing issuer chỉ vì certificate có URL AIA.
- **`AlgorithmConstraints`/JDK security properties:** khóa yếu, digest và thuật toán không còn được policy cho phép, có thể cho `ALGORITHM_CONSTRAINED`. Không tắt policy để chạy certificate cũ trừ khi có quyết định rủi ro được đánh giá và lộ trình thay thế.

**Cảnh báo rất quan trọng:** Lab dùng `params.setRevocationEnabled(false)` để chạy offline không cần CRL/OCSP. Trên production, cấu hình kiểm tra thu hồi phải theo chính sách bảo mật của use case. Khi gặp `UNDETERMINED_REVOCATION_STATUS` không được tự bật `false` để “sửa lỗi” nếu chính sách yêu cầu kiểm tra thu hồi.

### Validation date không bằng “ngày phát hành certificate”

Certificate có `NotBefore/NotAfter` riêng. Thời điểm `PKIXParameters.getDate()` dùng để xét hiệu lực không tự biến một certificate hết hạn thành còn hiệu lực. Không “chỉnh đồng hồ” hoặc chọn ngày tùy ý nhằm bỏ qua certificate hết hạn ngoài một use case được thiết kế rõ (ví dụ kiểm chứng dữ liệu ký lâu dài kèm timestamp/evidence được đánh giá phù hợp).

### Root trust anchor và path

Với Java PKIX thông thường, trust anchor **được cung cấp riêng** chứ không nhất thiết là phần tử trong `CertPath`. Trạng thái validity của trust-anchor certificate không tự được xử lý y hệt certificate nằm trong path; chính sách quản trị root/truststore và các bước bổ sung có thể khác. **Đừng suy ra `index=-1` nghĩa là root hết hạn**; đó chỉ là không có index cụ thể được gán trong exception.

## 8. Những reason phổ biến trong hệ thống thật

### 8.1 `NO_TRUST_ANCHOR` hoặc “PKIX path building failed”

Hãy hỏi: ứng dụng **đang chạy trên JVM nào**, tải truststore nào, trust anchors gồm những CA nào? Server có gửi **đúng intermediate** không? Có cross-signing khiến client cũ cần một đường dẫn khác không? Certificate có issuer thật được ký bởi public key nào?

- Dùng `keytool -list -v -keystore ...` hoặc công cụ quản trị truststore tương ứng để kiểm tra **có kiểm soát**. `cacerts` của JVM và truststore custom/container có thể khác nhau.
- So issuer/subject, SKI/AKI và signature link để hiểu intermediate; nhưng **chỉ so DN chưa đủ** để chứng minh issuer ký.
- Nếu server không gửi intermediate, có thể phải sửa **certificate chain server-side** hoặc provisioning ở phía ứng dụng. Không tự đưa root mới từ response của server vào truststore.
- Với các certificate tự ký của môi trường nội bộ, pin/trust anchor chỉ được cài khi danh tính/khóa đã được xác thực qua kênh đáng tin.

Cần phân biệt `CertPathBuilderException` (không tìm được path) với `CertPathValidatorException` (một path được kiểm tra và thất bại). Lỗi ở tầng TLS có thể bọc cả hai. **Đừng giả định builder thất bại có một `getIndex()`**.

### 8.2 `EXPIRED` và `NOT_YET_VALID`

Kiểm tra `NotBefore/NotAfter` của certificate **được index nêu ra**, đồng hồ máy, múi giờ/UTC và validation date thực tế. Một intermediate hết hạn có thể khiến **nhiều leaf** cùng gặp lỗi dù leaf còn hiệu lực.

Không sửa bằng cách tắt validity validation. Quy trình đúng là renew/replace certificate theo chain phù hợp, cập nhật issuer/intermediate nếu cần, kiểm tra lại key usage, SAN/EKU, chính sách và triển khai có rollback.

### 8.3 `INVALID_SIGNATURE`

Bắt đầu từ **certificate tại index**, tìm **issuer key phù hợp** trong certificate phía trên hoặc trust anchor; kiểm tra chữ ký bằng khóa đó trong môi trường debug. Các nguyên nhân hay gặp: leaf đi kèm intermediate sai, trộn chain từ hai đợt phát hành, certificate DER bị hỏng hoặc issuer key rollover. Việc message đề cập “signature” **không có nghĩa chữ ký dữ liệu ứng dụng bị sai**; ở đây là **chữ ký của X.509 certificate** trong path.

Đừng “khắc phục” bằng việc chuyển sang trust-all, bỏ qua signature link hoặc dùng public key từ payload chưa xác thực.

### 8.4 `NOT_CA_CERT`, `INVALID_KEY_USAGE`, `PATH_TOO_LONG`

Trọng tâm là **vai trò CA**:

- Intermediate phải có `BasicConstraints: CA=true` và `KeyUsage.keyCertSign` khi extension yêu cầu.
- `pathLenConstraint=0` ở một intermediate nghĩa là không có **CA intermediate không tự phát hành** thêm bên dưới (nhưng vẫn có thể cấp leaf).
- Certificate `CA=false` không được tự nâng thành CA chỉ vì private key của nó có thể ký một chữ ký kỹ thuật.

Kiểm tra bản profile được cấp bởi CA, không tự “vá” extension local vì certificate được ký: thay một byte extension làm chữ ký certificate sai. Cần phát hành lại certificate đúng quy trình.

### 8.5 `REVOKED` / `UNDETERMINED_REVOCATION_STATUS`

Certificate có thể đã bị thu hồi dù chưa tới `NotAfter`. Xem CRL/OCSP, cache, responder, network controls, nonce/stapling nếu áp dụng và chính sách khi không có kết quả. Một exception revocation có thể chứa dữ liệu chi tiết trong `cause` (ví dụ `CertificateRevokedException`); xử lý có kiểm soát, tránh lộ ra client.

`UNDETERMINED_REVOCATION_STATUS` **không đồng nghĩa certificate sạch**. Nó cho biết không xác định được trạng thái theo chính sách. Có hệ thống dùng soft-fail có đánh giá rủi ro, nhưng quyết định này **không nên phát sinh tự động từ một catch block**.

### 8.6 `ALGORITHM_CONSTRAINED`

Mật mã path có thể bị policy từ chối dù chữ ký tự thân toán học còn verify được: RSA modulus quá nhỏ, SHA-1 hoặc thuật toán không được dùng trong mục đích này, thời điểm sử dụng không thỏa policy. Kiểm tra `java.security`, `jdk.certpath.disabledAlgorithms` và thông số JDK/provider triển khai.

Phương án bền vững là phát hành lại chain theo scheme/key size được phép, cùng lộ trình tương thích. **Không** vô hiệu hóa constraints trên toàn JVM để một certificate yếu tiếp tục hoạt động.

### 8.7 `INVALID_NAME` không phải mọi lỗi hostname mismatch

`INVALID_NAME` có thể gắn với **NameConstraints** trong chain. Nhưng TLS endpoint identity (DNS SAN/IP) của certificate leaf so với tên máy bạn kết nối thường là bước **bên ngoài generic PKIX path validation**. Một path có thể `validate == OK` nhưng TLS handshake bị chặn do SAN không khớp. Đừng “sửa” bằng cách hạ expected hostname hoặc sửa `CN`.

## 9. Khi exception bị bọc trong TLS hoặc thư viện

Thường thấy stack kiểu:

```text
javax.net.ssl.SSLHandshakeException
  -> javax.net.ssl.SSLException / ValidatorException (tùy provider)
      -> java.security.cert.CertPathValidatorException
```

Hoặc:

```text
javax.net.ssl.SSLHandshakeException
  -> sun.security.provider.certpath.SunCertPathBuilderException
     (không xây dựng được đường dẫn tin cậy)
```

Hai sơ đồ là **hình minh họa cấu trúc thường gặp**, không cam kết tất cả JDK/provider có cùng chuỗi cause hay cho phép import lớp nội bộ `sun.*`. Không viết logic nghiệp vụ phụ thuộc tên lớp Sun internal. Ví dụ snippet bên dưới là **đoạn phụ trợ**, không phải chương trình độc lập:

```java
static CertPathValidatorException findValidationProblem(Throwable error) {
    Throwable current = error;
    int depth = 0;
    while (current != null && depth++ < 20) {
        if (current instanceof CertPathValidatorException found) {
            return found;
        }
        current = current.getCause();
    }
    return null;
}
```

Đặt trong một class, import `java.security.cert.CertPathValidatorException`. Giới hạn `depth` tránh loop bất thường trong exception chain; trong thư viện thực có thể xem thêm suppressed causes. Nếu trả `null`, không kết luận certificate đúng: có thể lỗi thuộc builder/hostname/protocol hoặc bị che bởi wrapper.

**Cẩn thận:** Không “catch tất cả `SSLHandshakeException` rồi kết luận `NO_TRUST_ANCHOR`”. Ghi loại lỗi thật, xác định mục đích TLS và phân loại theo các lớp thông tin có sẵn.

## 10. Logging và debug thực tế

### 10.1 Những trường có giá trị điều tra

Một log nội bộ (được hạn chế quyền) có thể ghi:

| Field | Tại sao cần? | Cách xử lý |
| --- | --- | --- |
| `correlationId` | Ghép sự kiện request với certificate failure | Không lộ dữ liệu nhạy cảm từ request |
| `validationAlgorithm`, `provider`, `javaVersion` | Tìm khác biệt giữa JVM/provider và thuật toán | Lưu phiên bản cấu hình đủ để tái hiện |
| `exceptionReason` | Phân loại ổn định hơn message text | Dùng enum, có fallback `UNSPECIFIED` |
| `pathIndex` và `pathLength` | Xác định certificate trong path theo vị trí | Nêu `-1` là không có certificate index |
| `certificateSubject/Issuer/Fingerprint` | Gắn lỗi vào certificate thực tế | Lọc PII, dùng SHA-256 fingerprint thay vì log nguyên DER |
| `expectedHostname/Role/Tenant` | So với danh tính cần xác thực | Nguồn từ policy/ngữ cảnh đã tin |
| `validationDate/NotBefore/NotAfter` | Điều tra hết hạn/đồng hồ | Dùng timezone rõ ràng |
| `trustAnchorId` / `truststoreVersion` | Phân biệt cấu hình giữa môi trường | Không ghi mật khẩu truststore hoặc key riêng |
| `revocationPolicy/Result` | Phân biệt revoked và không xác định được trạng thái | Không log OCSP secrets/nhạy cảm nếu có |

**Không log private key, keystore password, token, session secrets hoặc thông tin khách hàng không cần thiết**. Certificate public có thể chứa danh tính/địa chỉ nên vẫn phải tuân thủ chính sách log.

### 10.2 Bật debug có kiểm soát

Ví dụ khi chạy một ứng dụng Java (chỉ dành cho troubleshooting ở môi trường an toàn):

```powershell
java -Djava.security.debug=certpath -cp . CertPathFailureDemo
```

Đây là lệnh có thêm **debug trace**, không phải stdout cố định. JDK có thể in dữ liệu path/trust/algorithms rất dài; bảo vệ trace, bật có thời hạn và không để trong log public.

Với JSSE/TLS, có thể dùng option:

```powershell
java -Djavax.net.debug=ssl,handshake,trustmanager -cp . YourTlsClient
```

`YourTlsClient` chỉ là tên minh họa của một ứng dụng TLS **đã tồn tại**, không phải class do lab tạo. Một số shell/JVM yêu cầu quoting tham số khác. TLS debug có thể chứa certificate chains và metadata nhạy cảm; không mặc định để debug trong production.

### 10.3 Giới hạn của ví dụ lab

Chương trình `CertPathFailureDemo` gọi validator trực tiếp, **không làm TLS handshake**, không verify hostname trên mạng và không thực hiện revocation vì fixture offline. Nó cũng không chứng minh certificate không bị lộ private key. Để triển khai một TLS client, dùng JSSE/HTTP client với trust manager, endpoint identification và policy đúng; **không** lấy kết quả `CertPathValidator.validate(path)==OK` làm toàn bộ quyết định kết nối.

## 11. Bảng tra nhanh index + reason

| Quan sát | Không nên kết luận ngay | Cần làm |
| --- | --- | --- |
| `index=0, reason=EXPIRED` | “Root đã hết hạn” | Xem certificate index 0 trong `getCertPath()`, thường leaf |
| `index=1, reason=INVALID_SIGNATURE` | “Chữ ký ứng dụng sai” | Xem intermediate index 1 và issuer key ký certificate của nó |
| `index=-1, reason=NO_TRUST_ANCHOR` | “Certificate ở index -1 là root bị lỗi” | So trusted anchors với chain/cross-sign; không có cert index tương ứng |
| `index=0, reason=INVALID_SIGNATURE` | “Leaf chắc chắn bị hacker sửa” | Kiểm tra đúng intermediate, chain và bytes; lỗi có thể do thao tác vận hành |
| `reason=ALGORITHM_CONSTRAINED` | “Cứ bật lại thuật toán yếu” | Xem `java.security` policy, key strength và phát hành lại chain |
| `reason=UNDETERMINED_REVOCATION_STATUS` | “Certificate không bị revoke” | Kiểm tra revocation checker/responder/network và fail policy |
| `reason=NOT_CA_CERT` | “CA thiếu một nhãn hiển thị” | Kiểm tra BasicConstraints thực được ký và profile CA |
| `reason=INVALID_NAME` | “Hostname TLS sai” | Xem NameConstraints của path, đồng thời kiểm tra hostname/SAN **riêng** |
| `getCertPath()==null` | “Không có certificate nên chắc chắn không liên quan PKIX” | Path không được cung cấp trong exception; kiểm tra cause và context |
| Lỗi `CertPathBuilderException` | “Gọi `getIndex` trực tiếp được” | Builder thất bại khác validator; xem wrapper/cause/issuer source |
| `CertPathValidator.validate()==OK` | “Truy cập API tự động được phép” | Kiểm tra đúng endpoint identity, EKU, tenant/role và authorization |

## 12. Các lỗi lập trình thường gặp

1. **Reverse chain khi đọc index:** Phần tử 0 trong list demo là **leaf**, không phải root. `CertPath` có thứ tự xác định.
2. **Coi `-1` là root:** Root/trust anchor không có index trong path, và `-1` không gắn cho bất kỳ certificate cụ thể nào.
3. **Dùng `getMessage().contains(...)` để xử lý logic:** message thay đổi theo provider, locale, bản JDK và tình huống.
4. **Chỉ xét certificate tại index mà bỏ context:** đúng certificate cũng có thể bị validator gắn lỗi do thiếu issuer/truststore/params.
5. **Gán tất cả lỗi TLS cho PKIX:** certificate builder failure, hostname mismatch, protocol mismatch, TLS alert và IO khác nhau.
6. **Tắt revocation sau khi gặp `UNDETERMINED_REVOCATION_STATUS`:** đây là thay đổi chính sách bảo mật, không phải sửa lỗi an toàn mặc định.
7. **Lấy root trong handshake làm trust anchor:** attacker có thể gửi root tự ký của họ. Trust phải provision ngoài đường truyền chưa tin.
8. **Import bất kỳ issuer mới vào `cacerts`:** thay đổi vùng tin cậy của cả JVM; cần quy trình xác minh, duyệt và giới hạn.
9. **Sửa certificate DER thủ công để đổi extension/validity:** làm mất chữ ký CA. Cần CA phát hành lại.
10. **Nhầm `EXPIRED` của leaf với hệ thống thời gian sai luôn luôn:** phải so `NotAfter` với validation date; đồng hồ có thể đúng và cert thực sự hết hạn.
11. **Nhầm `INVALID_SIGNATURE` với `Signature.verify` của payload:** ở đây chủ đề là certificate signature trong PKIX.
12. **Dùng subject CN làm hostname matcher thủ công:** hostname/SAN phải theo chuẩn TLS (DNS/IP, wildcard, IDNA) và expected target.
13. **Dùng root trust anchor thử nghiệm của lab trong production:** phá ranh giới niềm tin, đặc biệt vì private key lab tồn tại trong PKCS#12 cùng mật khẩu dễ đoán.
14. **Chỉ kiểm tra `Reason` để cho phép truy cập:** lý do exception là tín hiệu thất bại, không phải token quyền. Mọi failure phải từ chối đường dẫn tương ứng.
15. **Nuốt ngoại lệ sau khi log:** nếu validation fail, không gọi tiếp nghiệp vụ như thể có certificate hợp lệ.
16. **Thử tất cả truststore đến khi có path OK:** nguy hiểm nếu tập anchors thử chứa root không được policy chấp thuận. Xác định đúng trust configuration từ trước.

## 13. Checklist cho dịch vụ Java

- [ ] Xác định đúng **expected endpoint/identity/role/tenant** từ nguồn tin cậy.
- [ ] Ghi nguồn `CertPath` đang validate và thứ tự certificate rõ ràng, không đảo list tùy tiện.
- [ ] Khi bắt lỗi, lấy `getReason()`, `getIndex()`, `getCertPath()` với kiểm tra index bounds.
- [ ] Với index hợp lệ, đối chiếu certificate **tại index trong path của exception**, không suy từ tên file.
- [ ] Với `index=-1`, giữ nhãn “không có certificate index”, kiểm tra truststore/params/cause.
- [ ] Ưu tiên `BasicReason`/`PKIXReason` cho phân loại; không dùng chuỗi `getMessage` làm mã lỗi duy nhất.
- [ ] Trust anchors được provision đáng tin; certificate server gửi không tự động biến thành trusted anchor.
- [ ] Intermediate có đúng issuer/signature, CA BasicConstraints, KeyUsage, pathLen và constraints khác.
- [ ] Time, signature schemes, key size, algorithm constraints và chính sách revocation được cấu hình nhất quán.
- [ ] Endpoint identity/SAN, EKU/application purpose và authorization kiểm tra **ngoài** generic PKIX path.
- [ ] Không tắt certificate validation, hostname verification hoặc revocation chỉ để “hết lỗi”.
- [ ] Có test happy path, wrong anchor, tampered leaf, tampered intermediate, expired leaf, missing issuer, key usage và revocation policy.
- [ ] Log có correlationId/provider/đường dẫn/path index/reason theo quyền, tránh tiết lộ secrets.
- [ ] Có quy trình cập nhật truststore, certificate rotation, CRL/OCSP monitoring và diễn tập phục hồi.

## 14. Bài tập

1. Chạy `CertPathFailureDemo.java`. Tại sao hai test `Tampered leaf` và `Tampered intermediate` đều có reason `INVALID_SIGNATURE` nhưng index lần lượt `0` và `1`?
2. Đổi thứ tự `List.of(leaf, intermediate)` thành `List.of(intermediate, leaf)`. Hãy ghi lại reason/index của JDK; vì sao không còn được đọc index theo sơ đồ ban đầu?
3. Giữ nguyên certificate path, thay trust anchor bằng `rogue.pem`. Có phải `rogue.pem` “không tự ký hợp lệ” không? Vì sao vẫn gặp `NO_TRUST_ANCHOR`?
4. Đặt validation date vào thời điểm trước `leaf.getNotBefore()`. Reason nào xuất hiện trên JDK của bạn và tại index nào? Thử so với intermediate validity.
5. Tạo intermediate có `BasicConstraints:CA=false` rồi cho nó cấp leaf, thử validate. Bạn mong chờ nhóm `PKIXReason` nào? Có thể `leaf.verify(intermediateKey)` vẫn thành công không?
6. Kiểm tra một chain server TLS thiếu intermediate. Phân biệt lỗi builder không tìm được đường dẫn với validator có đường dẫn nhưng bị lỗi chữ ký. Bạn sẽ bổ sung intermediate ở phía nào cho đúng?
7. Làm một bảng chẩn đoán cho `ALGORITHM_CONSTRAINED` khi một môi trường JDK chặn thuật toán hoặc key size mà môi trường cũ còn chấp nhận. Đề xuất biện pháp không hạ chuẩn bảo mật.
8. Với `UNDETERMINED_REVOCATION_STATUS`, thiết kế runbook kiểm tra CRL/OCSP, cache, proxy/firewall và fail policy. Vì sao không nên vô hiệu hóa revocation trong production?
9. Thêm helper lấy SHA-256 fingerprint của certificate **tại getIndex() hợp lệ**; ghi trong log nội bộ bằng hex, không in private key hay secret.
10. Một path PKIX hợp lệ cho certificate `api-b.example.test` nhưng ứng dụng gọi `api-a.example.test`. Bước kiểm tra nào vẫn có thể fail? Vì sao `CertPathValidatorException` không phải tín hiệu duy nhất cho TLS hostname mismatch?

## Tài liệu tham khảo chính thức

- [Java SE 17 — CertPathValidatorException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertPathValidatorException.html): `getIndex`, `getReason`, `getCertPath` và constructors.
- [Java SE 17 — CertPathValidatorException.BasicReason](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertPathValidatorException.BasicReason.html): `EXPIRED`, `INVALID_SIGNATURE`, `REVOKED`, `UNDETERMINED_REVOCATION_STATUS`, `ALGORITHM_CONSTRAINED`.
- [Java SE 17 — PKIXReason](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/PKIXReason.html): `NO_TRUST_ANCHOR`, `NOT_CA_CERT`, `NAME_CHAINING` và các reason PKIX khác.
- [Java SE 17 — CertPathValidator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertPathValidator.html): kiểm định đường dẫn và thuật toán PKIX.
- [Java SE 17 — CertPathBuilder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertPathBuilder.html): tìm/dựng đường dẫn và khác biệt với validation.
- [Java SE 17 — PKIXParameters](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/PKIXParameters.html): trust anchors, date, revocation, policy.
- [Java SE 17 — X509Certificate](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/X509Certificate.html): subject, issuer, validity, BasicConstraints, SAN/EKU, chữ ký.
- [JDK 17 — keytool](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html): sinh CSR, ký certificate, import reply, export PEM.
- [RFC 5280 — Internet X.509 Public Key Infrastructure Certificate and CRL Profile](https://www.rfc-editor.org/rfc/rfc5280): chứng thư X.509, trust/path, validity, BasicConstraints, Certificate Policies và CRL.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cert-path-009">14.9 Giới hạn của PKIX: kiểm tra danh tính máy chủ là một bước riêng</a>

<details>
<summary>Xem chi tiết</summary>

# 14.9. Giới hạn của PKIX: kiểm tra danh tính máy chủ là một bước riêng

## 1. Bài toán: chứng thư có CA tin cậy chưa chắc thuộc đúng server

Giả sử một ứng dụng Java định gọi API tại `https://billing.example.test`. Trên đường truyền, máy chủ trình ra một chứng thư X.509:

- Do một CA hợp lệ, nằm trong tập trust anchors mà client đã cấu hình, ký.
- Chưa hết hạn, đường dẫn chứng thư và chữ ký đều có thể được kiểm tra thành công theo PKIX.
- Nhưng **Subject Alternative Name (SAN)** của chứng thư chỉ chứa `DNS:service.example.test`, **không** chứa `billing.example.test`.

Nếu lập trình viên chỉ gọi `CertPathValidator.getInstance("PKIX").validate(...)` rồi kết luận “đúng server”, ứng dụng có thể bị kết nối đến **sai danh tính**. Đây là lỗi ghép hai bài toán khác nhau thành một:

1. **Path validation (PKIX):** chuỗi chứng thư có xây dựng được một đường tin cậy hợp lệ đến một trust anchor được chấp nhận, thỏa các ràng buộc và chính sách PKIX hay không?
2. **Endpoint identification / hostname verification:** chứng thư được chấp nhận đó có **ràng buộc với tên DNS hoặc địa chỉ IP mà ứng dụng thực sự dự định kết nối** hay không?

**Kết luận cốt lõi:** `PKIX path valid = true` **không kéo theo** `hostname matches = true`. Một kết nối TLS đúng phải thỏa **cả hai điều kiện** theo giao thức, đồng thời bắt tay TLS chứng minh server dùng được private key tương ứng với chứng thư được chấp nhận.

### Mục tiêu học tập

Sau bài này, bạn sẽ biết:

- Phân biệt `CertPathBuilder`, `CertPathValidator`, `PKIXParameters`, `TrustAnchor` với bước xác minh tên máy chủ.
- Hiểu vì sao đường tin cậy hợp lệ vẫn có thể bị dùng cho **sai hostname**.
- Nhận diện SAN `dNSName` (type 2), `iPAddress` (type 7), wildcard và giới hạn của `Common Name (CN)`.
- Viết ví dụ Java 17+ **thực sự xác minh PKIX chain** nhưng chỉ có SAN của một hostname khác, rồi thấy giới hạn của kết quả.
- Cấu hình `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")` khi sử dụng JSSE trực tiếp, và không vô hiệu hóa hostname/trust verification.
- Xây test matrix có đủ **đường tin cậy đúng/sai** và **danh tính đúng/sai**, phân biệt lỗi issuer với name mismatch.

## 2. Hai câu hỏi khác nhau trong xác thực TLS

### 2.1. PKIX xác minh đường tin cậy là gì?

**PKIX** (*Public Key Infrastructure using X.509*) là tập tiêu chuẩn dùng để xây dựng và xác minh chứng thư. Đối với một chuỗi chứng thư server thường gặp:

```text
Trust anchor / Root CA được client tin từ trước
           |
           | ký hoặc ủy quyền theo cấu trúc CA
           v
Intermediate CA (có thể không có)
           |
           | ký leaf certificate
           v
Server leaf certificate
           |
           +-- Subject Public Key
           +-- Subject Alternative Name (SAN)
           +-- KeyUsage / ExtendedKeyUsage
           +-- Validity / signature algorithm / extensions
```

Một quá trình **PKIX path validation** với chính sách phù hợp có thể xét các khía cạnh như:

- Chữ ký từng chứng thư và mối quan hệ issuer/subject trong đường dẫn.
- Đường dẫn kết thúc tại **trust anchor mà ứng dụng chủ động tin**, không phải một CA ngẫu nhiên bất kỳ.
- Hiệu lực thời gian (`notBefore`/`notAfter`) theo thời điểm kiểm tra.
- `BasicConstraints`, độ sâu CA, `KeyUsage` cho chức năng chứng thực của CA.
- Các ràng buộc tên (`NameConstraints`), certificate policies, thuật toán/độ mạnh khóa theo cấu hình và provider.
- Tình trạng thu hồi (CRL/OCSP) nếu đã cấu hình đúng và cơ chế kiểm tra tương ứng được kích hoạt.

**Không có một tập bảo đảm duy nhất cho mọi lần gọi `validate`:** kết quả phụ thuộc trust anchors, PKIX parameters, cert stores, chính sách thu hồi, thời gian, implementation/provider và ngữ cảnh ứng dụng. Trong Java, cấu hình mà bạn chọn là một phần của yêu cầu bảo mật.

### 2.2. Endpoint identification kiểm tra điều gì?

Khi ứng dụng **muốn** kết nối `billing.example.test`, client phải xác minh certificate chứa **reference identity** phù hợp với tên đó. Thông thường HTTPS sử dụng:

- **DNS-ID:** `subjectAltName` dạng `dNSName` cho hostname DNS.
- **IP-ID:** `subjectAltName` dạng `iPAddress` cho địa chỉ IP literal.
- Các quy tắc về wildcard/IDNA/so khớp được profile giao thức quy định.

**Tên mong đợi phải đến từ phía ứng dụng/giao thức đáng tin**, ví dụ hostname trong URI mà client đã chọn hoặc danh tính peer đã cấu hình. Không lấy tên mong đợi từ chứng thư vừa nhận rồi kiểm tra chứng thư ấy “có chứa tên đó” — phép kiểm sẽ trở thành vòng lặp tự xác nhận và không bảo vệ đích mà người dùng muốn gọi.

### 2.3. Điều kiện kết nối an toàn

```text
Khởi tạo kết nối tới hostname H do client tin
                    |
                    v
Nhận certificate chain của TLS peer
                    |
                    v
[1] Validate PKIX path đến trust anchor được phép?
       | không --> FAIL: không tin đường chứng thư
       | có
       v
[2] Xác minh endpoint identity H khớp SAN đúng kiểu?
       | không --> FAIL: sai server identity
       | có
       v
[3] TLS handshake/chính sách/algorithm/key proof thành công?
       | không --> FAIL
       | có
       v
Chỉ lúc này mới chấp nhận kênh TLS theo chính sách
và tiếp tục kiểm tra authorization nghiệp vụ.
```

Sơ đồ mô tả **các điều kiện logic**, không áp đặt thứ tự gọi nội bộ của tất cả TLS providers. JSSE có thể phối hợp kiểm tra path, endpoint và các tham số handshake trong cùng một quá trình. Không nên tự chia thao tác thành ba bước bằng tay nếu API TLS cấp cao đã kiểm tra chúng an toàn.

## 3. Vì sao một certificate hợp lệ lại có thể thuộc sai host?

Ví dụ ba dịch vụ:

```text
api.example.test
billing.example.test
files.example.test
```

Một CA có thể cấp **ba chứng thư đều hợp lệ** cho ba dịch vụ này. Việc client tin CA không có nghĩa mọi chứng thư CA đó cấp đều đại diện cho **mọi tên**.

Tình huống lỗi:

1. Java client kết nối `billing.example.test`.
2. Attacker có chứng thư hợp lệ cho `api.example.test` từ một CA mà client cũng tin hoặc có thể can thiệp định tuyến/địa chỉ peer.
3. Client chỉ xác thực chain: chữ ký hợp lệ, anchor được tin, validity và ràng buộc PKIX đều đạt.
4. Client **bỏ qua hostname verification** nên chấp nhận chứng thư có `DNS:api.example.test` như thể nó dùng được cho `billing.example.test`.

Ngay cả khi attacker **không thể tạo certificate giả cho billing**, họ vẫn có thể lạm dụng một certificate thật của **tên khác** nếu ứng dụng chỉ kiểm chain. Với hostname verification, client phải từ chối vì danh tính mục tiêu không khớp SAN.

**Không chỉ là vấn đề “CA giả”:** việc kiểm CA *đúng* vẫn chưa đủ nếu bạn không kiểm tên. Và việc tự pin một public key cũng phải có policy ràng buộc key với đúng endpoint và scope, không biến bất kỳ key đã pin nào thành danh tính chung cho mọi dịch vụ.

## 4. PKIX trong Java: API nào có và không có chức năng gì?

| API | Chức năng | Không thể suy ra chỉ từ lời gọi này |
| --- | --- | --- |
| `CertificateFactory.getInstance("X.509")` | Parse DER/PEM, tạo `X509Certificate` hoặc `CertPath` | Chưa xác minh trust chain hoặc hostname. |
| `X509Certificate.checkValidity()` | Kiểm tra ngày hiệu lực của đúng certificate | Không xác minh issuer, policy hoặc server identity. |
| `X509Certificate.verify(PublicKey)` | Kiểm chữ ký certificate bằng key đã cung cấp | Không tự chứng minh key đưa vào được tin, không kiểm hostname. |
| `X509Certificate.getSubjectAlternativeNames()` | Đọc danh sách GeneralName ở SAN extension | Chỉ là **getter**, không phải hostname verifier. |
| `CertPathBuilder.getInstance("PKIX")` | Tìm đường chứng thư đáp ứng tham số/target selector và trust policy | Không mặc nhiên lấy hostname URL cần bảo vệ. |
| `CertPathValidator.getInstance("PKIX")` | Xác minh đường `CertPath` cụ thể theo tham số PKIX | Không mặc nhiên so hostname/IP của kết nối TLS với SAN. |
| `PKIXParameters` / `PKIXBuilderParameters` | Cấu hình trust anchors, date, policy, revocation, cert stores, v.v. | Không có cơ chế mặc định “đây là URL billing của tôi” trong `validate`. |
| `TrustAnchor` | Biểu diễn root/CA tin cậy mà policy đã cho phép | Không tự xác minh rằng anchor do bên đáng tin cấp phát. |
| `X509ExtendedTrustManager` + JSSE endpoint algorithm | Trong TLS handshake có đủ ngữ cảnh, có thể phối hợp trust và endpoint identification | Không an toàn nếu trust manager tự viết luôn cho qua, hoặc không bật endpoint identification khi cần. |
| `HttpClient` / `HttpsURLConnection` (HTTPS) | Client TLS cấp cao với xác minh certificate/hostname theo cấu hình an toàn | Mất bảo đảm nếu custom SSL/trust/hostname configuration bỏ qua kiểm tra. |

**Điểm phân biệt giữa CertPathBuilder và CertPathValidator:** `Builder` tìm đường phù hợp từ những certificates/`CertStore` có sẵn; `Validator` kiểm **đường cụ thể** đã được cung cấp. Dù tự build hay validate, kết quả “đường hợp lệ” không tự biến thành “đúng DNS host”.

### 4.1. Trust anchor là một quyết định bảo mật

Trong ví dụ Java, chương trình sẽ tạo `new TrustAnchor(demoRootCertificate, null)`. Nghĩa là **chính chương trình tuyên bố** root đó là một anchor tin cậy. PKIX validator đánh giá chain dưới điều kiện đã cho; nó **không** xác minh bằng phép màu vì sao chương trình lại chọn root này.

Trong production, trust anchors đến từ truststore/PKI policy được quản trị: JDK/system CA store, một truststore nội bộ được provision an toàn hoặc cơ chế xác thực/pinning được duyệt. Nếu attacker có quyền thêm root CA của mình vào truststore, đường dẫn đến root đó có thể hợp lệ và phá vỡ giả định tin cậy. **Quản lý truststore** là một phần của mô hình an ninh.

### 4.2. PKIX thu hồi chứng thư không có nghĩa lúc nào cũng bật sẵn

Trong Java, `PKIXParameters` có tùy chọn `setRevocationEnabled(boolean)`. Một bài lab offline có thể vô hiệu hóa revocation vì không cung cấp CRL/OCSP và không có PKI thật; cách đó **không phải** policy phù hợp cho mọi ứng dụng production.

Khi deploy, cần quyết định rõ chế độ CRL/OCSP, nguồn trả lời, thời hạn cache, xử lý lỗi mạng, yêu cầu must-staple/nội bộ nếu áp dụng, và fail-open/fail-closed theo chính sách. Không khái quát rằng một lần `CertPathValidator.validate` thành công chắc chắn đã tra đầy đủ mọi trạng thái thu hồi trong thực tế.

### 4.3. PKIX path validation và TLS usage constraints

Generic `CertPathValidator` không biết **tất cả ngữ cảnh giao thức TLS**: hostname đang kết nối, SNI, có dùng serverAuth hay clientAuth, TLS version, handshake purpose, authType, application authorization. JSSE trust manager theo ngữ cảnh có thể áp dụng additional checks như extended key usage, thuật toán và danh tính endpoint.

Ví dụ chứng thư ký code hoặc client certificate có chain hợp lệ không tự động trở thành server certificate phù hợp cho mọi hostname. Phải kết hợp đúng **KeyUsage/EKU**, giao thức và identity verification.

## 5. Endpoint identity: DNS SAN và IP SAN là hai kiểu khác nhau

### 5.1. DNS hostname → dNSName (type 2)

Với URI `https://billing.example.test`, tên tham chiếu bình thường là `billing.example.test`, so với SAN `dNSName` của chứng thư server.

Ví dụ chứng thư chứa `DNS:service.example.test`. Chứng thư này **không** hợp lệ cho `billing.example.test` chỉ vì cùng suffix `example.test`. Một dNSName chính xác hoặc wildcard hợp lệ theo tiêu chuẩn phải khớp.

Các so sánh DNS-ID cần tuân thủ profile: tên DNS không phân biệt chữ hoa/chữ thường theo quy tắc, dạng IDN/IDNA ASCII A-label, các giới hạn wildcard; **không tự viết regex tùy tiện** để thay verifier của TLS stack.

### 5.2. IP literal → iPAddress (type 7)

Với `https://192.0.2.10` hoặc `https://[2001:db8::10]`, reference identity là IP address. Certificate cần SAN `iPAddress` có giá trị nhị phân tương ứng (4 octet cho IPv4, 16 octet cho IPv6). Dùng `DNS:192.0.2.10` không tự trở thành `IP Address:192.0.2.10`.

Không lấy kết quả reverse DNS để đổi yêu cầu kiểm IP-ID thành DNS-ID. Hai địa chỉ IPv6 có biểu diễn text khác nhau nhưng cùng 16 octet phải được hiểu theo giá trị IP; so text thuần có thể sai.

### 5.3. Common Name (CN) không thay cho SAN

Trước đây một số TLS clients có thể dùng `Subject CN` làm fallback nếu chứng thư thiếu dNSName. **RFC 9525**, thay thế RFC 6125, quy định không dùng Common Name RDN làm danh tính dịch vụ. Với thiết kế mới: cấp chứng thư có SAN phù hợp và không dựa vào fallback CN.

Một `Subject: CN=billing.example.test` **không nên** được xem là lý do cho qua khi SAN chỉ chứa `DNS:service.example.test`. Một certificate có CN đúng và SAN sai vẫn không bảo vệ danh tính cần thiết.

### 5.4. Wildcard không được tự mở rộng vô hạn

Theo profile DNS-ID, wildcard hợp lệ được giới hạn (ví dụ nguyên nhãn bên trái `*.example.test`), thông thường chỉ khớp **một nhãn**. Nó không khớp `example.test` và không khớp `a.b.example.test`. Một số hệ thống còn có chính sách hạn chế wildcard nghiêm ngặt hơn; phải theo RFC 9525 và profile đang áp dụng.

Không dùng `endsWith(".example.test")` hoặc `replace("*",".*")` để tự xác minh host. Một lỗi xử lý label, dấu chấm, Unicode/IDNA hoặc wildcard có thể tạo ra lỗ hổng xác thực endpoint.

## 6. Demo Java 17+: PKIX chain hợp lệ nhưng hostname cần kiểm tra riêng

### 6.1. Mục tiêu và ranh giới của demo

Chương trình sử dụng **hai chứng thư X.509 thử nghiệm công khai**:

- **Demo Root CA:** self-signed, với CA BasicConstraints và KeyUsage phù hợp, được chương trình chọn làm trust anchor chỉ cho bài học.
- **Leaf certificate:** được Demo Root CA ký, có SAN `DNS:service.example.test`; phần `Subject CN` là `legacy.example.test`.

Java `CertPathValidator` sẽ xác minh một đường có **leaf certificate** dẫn đến **root CA** đã được chương trình tin. Kết quả thành công **không** chứa hoặc nhận tham số `billing.example.test`; vì vậy nó không thể trả lời trực tiếp liệu hostname đó có đúng không.

Sau đó chương trình đọc SAN để **minh họa** rằng tên `service.example.test` có trong certificate, nhưng `billing.example.test` thì không. Phép tìm phần tử `List.contains` trong demo chỉ dùng vì test certificate chứa **một DNS-ID chính xác, không có wildcard hoặc IDN**. **Nó không phải hàm xác minh HTTPS hostname tổng quát.** Trong ứng dụng thật, để JSSE kiểm tra endpoint theo thuật toán `HTTPS` và PKIX trust manager thích hợp.

Certificate fixture được tạo cho mục đích học tập, chỉ có **public certificates**, **không nhúng private key**. Nó có hiệu lực dự kiến từ **31/10/2026 đến 28/10/2036** theo thời gian ghi trong certificate. Sau thời hạn đó, ví dụ PKIX **cần certificate mới**, không được tắt kiểm tra thời gian để ép pass.

Chương trình **không mở kết nối mạng** và không làm TLS handshake; nó minh họa sự khác nhau giữa hai câu hỏi ở lớp certificate.

### 6.2. Mã hoàn chỉnh PkixHostnameDemo.java

```java
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class PkixHostnameDemo {
    // Public test certificates. Never add this demo CA to a production truststore.
    private static final String CA_PEM = """
            -----BEGIN CERTIFICATE-----
            MIIDMDCCAhigAwIBAgIIdN8VOPntrL4wDQYJKoZIhvcNAQEMBQAwNjELMAkGA1UE
            BhMCVVMxEDAOBgNVBAoTB0V4YW1wbGUxFTATBgNVBAMTDERlbW8gUm9vdCBDQTAe
            Fw0yNjEwMzExMzI4NThaFw0zNjEwMjgxMzI4NThaMDYxCzAJBgNVBAYTAlVTMRAw
            DgYDVQQKEwdFeGFtcGxlMRUwEwYDVQQDEwxEZW1vIFJvb3QgQ0EwggEiMA0GCSqG
            SIb3DQEBAQUAA4IBDwAwggEKAoIBAQDVz/2xjKXNgom5vKohD6AGorxwLk3S/GZo
            KVU900y+GZG18aBjMbUIJAPZIRSykF3QCO5OfGJqHa3aqo6mctBknES2h3wGRw15
            MFjmDenGHl5HqjouG2iXAff9gKbencmgigDHKc5IWHLXx0N00TH0LWF9bf+b71Q1
            5MP8gZ3ad5v9PTNRhBGjli7ywoK68qTGhdSPUarHEYk0Ii5OW4mTtsfYlNZpsU15
            Unxz6ViASsaO96cAt/BD6gZQ7UtFhiwZc5p7B0lQiLFzv+B4YV1bRl3fN6FyV5u4
            gdol+uCSfl1g57bgYqb6JVsSb5pxchAoYLYBYqN7hNMEUwfmOLhZAgMBAAGjQjBA
            MB0GA1UdDgQWBBR8adDvW8CFs/qJKxm38W243usibTAOBgNVHQ8BAf8EBAMCAQYw
            DwYDVR0TAQH/BAUwAwEB/zANBgkqhkiG9w0BAQwFAAOCAQEAg4pxYY0c11RBf+CD
            y1bWEUQzGCKaa5zVkutxoaYSSuDhZEOnxn7+buY/EkMfAEWfttm32jI8HfvVvP5p
            dmF/PqUchpk/GwshauAWuCeWRc490aMudGB5LWgciFtK3L6opSsZYNUI47JJTAY9
            P79ybEgwUDjKm1po7h5o1aJYVzqWW9/8QgLXlb2dpz9gGYum45LpSEa17qEoAwG9
            2ADlnOcLc5OUNjt9Qv5arLzD/lUxkqc97xo4/ziKu/JUczn5qK+PPHuOcIZE39KB
            J6Xfg7dmD26RR0muKcGT80gjFzrdsPjYap6fhKfHRCiGfyhYXmEV3xBsx7a3qFcO
            T5ktkg==
            -----END CERTIFICATE-----
            """;

    private static final String SERVER_PEM = """
            -----BEGIN CERTIFICATE-----
            MIIDgDCCAmigAwIBAgIJALbt87m4xELfMA0GCSqGSIb3DQEBDAUAMDYxCzAJBgNV
            BAYTAlVTMRAwDgYDVQQKEwdFeGFtcGxlMRUwEwYDVQQDEwxEZW1vIFJvb3QgQ0Ew
            HhcNMjYxMDMxMTMyOTAwWhcNMzYxMDI4MTMyOTAwWjA9MQswCQYDVQQGEwJVUzEQ
            MA4GA1UEChMHRXhhbXBsZTEcMBoGA1UEAxMTbGVnYWN5LmV4YW1wbGUudGVzdDCC
            ASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBANhgczqgksY3gGlhNUrb619R
            vR9PuIPa7c1wDdju1jGUlBv86XfjLXNzuVL92YdBI7OGzXcrUlYYdaieyXtNdM4c
            N6hBIFhWHaNxq5O98ljKHeCXSgAixz0FyvCG8odzwjbW8VFXfVlqXT839tZMhcpm
            gayLXW6p5GmGnujV/q1TZxXp4IDqsYLDTD0YSaghm/hFRF9wlNW1eoPS7EKemACV
            i75iY9w9ImdjjPtxr36/1nWKGMhksIzNMw/ClygruwaVb+ROERx8EM8UcMcJg4jn
            w+uhYUQ7uTEM2N+i3BzERt29R6pSHDWc8zuVyB+766YkjK52D7zUnQoPOfNN1rMC
            AwEAAaOBiTCBhjAdBgNVHQ4EFgQU3FgVZYXNv6Ofpywezmb/kZpFWywwDgYDVR0P
            AQH/BAQDAgWgMB8GA1UdEQQYMBaCFHNlcnZpY2UuZXhhbXBsZS50ZXN0MB8GA1Ud
            IwQYMBaAFHxp0O9bwIWz+okrGbfxbbje6yJtMBMGA1UdJQQMMAoGCCsGAQUFBwMB
            MA0GCSqGSIb3DQEBDAUAA4IBAQBOEY35M1IhiI/F4codM6kCBQcjCM3QpB8D1nvL
            nVB4HXZENtIRnDhXVDCzzo3Xko+/SuMiobli5+j4iKDncy/wrwbnhJr/koPGN5jP
            GnpNmwBNw9m4OSH7xjgFdbfH4S0DrNYv/NsvH1dJchdzSVm3ZVFLNDb0Ao/LB7ib
            Gs72BDXNiYxLrByryRDCVdoI6KNqL57Ll7ep2yRs2LGLNvP787rWH1frObGUz4KR
            wR+l0T8A93nPteXO0/P/8K30QdJ9Eyb5bftnBqWDO+aSlDnk2mhSAZ0Trroj68uQ
            Y3IYs9CG3VKGCvopFX+mrgBvVP28BIlyNevP3glqiyO9t1f1
            -----END CERTIFICATE-----
            """;

    private static X509Certificate certificate(CertificateFactory factory, String pem)
            throws Exception {
        return (X509Certificate) factory.generateCertificate(
                new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
    }

    public static void main(String[] args) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        X509Certificate root = certificate(factory, CA_PEM);
        X509Certificate server = certificate(factory, SERVER_PEM);

        CertPath path = factory.generateCertPath(List.of(server));
        PKIXParameters policy = new PKIXParameters(Set.of(new TrustAnchor(root, null)));
        policy.setRevocationEnabled(false); // Test fixture only; not a production policy.
        PKIXCertPathValidatorResult result = (PKIXCertPathValidatorResult)
                CertPathValidator.getInstance("PKIX").validate(path, policy);
        System.out.println("PKIX path valid: " + (result.getTrustAnchor() != null));

        List<String> dnsNames = new ArrayList<>();
        Collection<List<?>> sans = server.getSubjectAlternativeNames();
        if (sans != null) {
            for (List<?> name : sans) {
                if (((Integer) name.get(0)) == 2 && name.get(1) instanceof String dns) {
                    dnsNames.add(dns);
                }
            }
        }

        System.out.println("DNS SANs: " + dnsNames);
        System.out.println("Intended service DNS listed: " +
                dnsNames.contains("service.example.test"));
        System.out.println("Different target DNS listed: " +
                dnsNames.contains("billing.example.test"));
        System.out.println("Legacy CN in DNS SAN: " +
                dnsNames.contains("legacy.example.test"));
    }
}
```

### 6.3. Biên dịch, chạy và stdout

Dùng Java 17 trở lên, không cần kết nối mạng hay file khóa riêng:

```bash
javac PkixHostnameDemo.java
java PkixHostnameDemo
```

Stdout đã kiểm thử trên JDK có hỗ trợ PKIX thông thường, trong thời hạn hiệu lực certificate fixture:

```text
PKIX path valid: true
DNS SANs: [service.example.test]
Intended service DNS listed: true
Different target DNS listed: false
Legacy CN in DNS SAN: false
```

**Đọc kết quả đúng cách:**

1. `PKIX path valid: true` cho thấy `CertPathValidator` đã kiểm tra **leaf do demo CA ký** theo trust anchor/chính sách đã chỉ định, không phải chỉ so fingerprint hoặc parse PEM.
2. `DNS SANs` chứa `service.example.test` là dữ liệu **được đọc** từ leaf certificate. Chưa thực hiện hostname matching theo RFC 9525/JSSE trong demo này.
3. `Different target DNS listed: false` minh họa hostname `billing.example.test` **không được chứng thư đại diện**. Điều này **không làm** cho `CertPathValidator.validate` ném exception, bởi vì không có tên `billing.example.test` nào được đưa vào yêu cầu path validation.
4. `Legacy CN in DNS SAN: false` là do CN được đặt khác DNS SAN. Không được dùng CN để “cứu” một name mismatch trong chính sách hiện đại.
5. Việc `setRevocationEnabled(false)` chỉ được dùng cho **fixture offline**. Từ ví dụ không được suy ra production có thể bỏ kiểm tra thu hồi hoặc rằng demo root là một public trusted root.
6. Tập PEM gồm **root và leaf public certificates**; nếu người dùng chỉ thay `SERVER_PEM` bằng một leaf không được root này ký, PKIX sẽ thất bại trừ khi có đường tin cậy hợp lệ khác được cấu hình. Thay nội dung SAN không thể làm một certificate ký sẵn vẫn có chữ ký hợp lệ.

**Điều demo không làm:** chưa có TLS network handshake, chưa có kiểm proof-of-possession từ remote server, chưa có kiểm tên bằng hostname verifier của JSSE, chưa kiểm TLS server usage profile/authorization và không chứng minh một máy chủ thực tế đang hiện diện. Đây là bài kiểm tra **ranh giới của PKIX**, không phải ví dụ khuyến khích tự kiểm tra SAN với `contains` trong production.

## 7. Cách làm đúng trong Java TLS: đưa endpoint identity vào handshake

### 7.1. Dùng HttpClient/HTTPS API cấp cao trước

Trong Java, khi dùng `java.net.http.HttpClient` với URL HTTPS, cấu hình mặc định phù hợp của client thực hiện kiểm tra TLS và danh tính máy chủ. Với ứng dụng mới, ưu tiên client cấp cao có truststore chuẩn, chính sách TLS được quản lý, không thay bằng `SSLContext` tùy tiện tin mọi chứng thư.

Đoạn mã dưới đây **chỉ minh họa cấu hình client**; hostname `.test` là ví dụ tài liệu, không có giả định tồn tại server Internet:

```java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

HttpClient client = HttpClient.newHttpClient();
HttpRequest request = HttpRequest.newBuilder(
        URI.create("https://billing.example.test/orders")).GET().build();
HttpResponse<String> response =
        client.send(request, HttpResponse.BodyHandlers.ofString());
```

Đây **không phải chương trình Java độc lập**, chỉ là đoạn mã đặt trong một phương thức có thể ném `Exception`. `HttpClient` vẫn cần DNS/network hợp lệ và server certificate được PKIX + endpoint verification chấp nhận. Chương trình `PkixHostnameDemo` ở trên không kết nối host này.

**Không** chỉnh hệ thống để tắt hostname verification khi gặp mismatch, không thay trust manager bằng “accept-all”, không dùng custom `SSLContext` hoặc property nội bộ vô hiệu hóa kiểm tra tên để làm test pass.

### 7.2. Với SSLSocket, bật endpoint identification

API `SSLSocket` cấp thấp đòi hỏi lập trình viên kiểm soát cấu hình xác minh tên. Một cách được JDK hỗ trợ là `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")`:

```java
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
try (SSLSocket socket =
        (SSLSocket) factory.createSocket("billing.example.test", 443)) {
    SSLParameters parameters = socket.getSSLParameters();
    parameters.setEndpointIdentificationAlgorithm("HTTPS");
    socket.setSSLParameters(parameters);
    socket.startHandshake();
    // Chỉ xử lý dữ liệu nếu TLS handshake và endpoint verification thành công.
}
```

Đây là **snippet minh họa**, không phải một class độc lập. Host `billing.example.test` là tài liệu, không phải server có thật. Với SSL/TLS triển khai thực tế, phải dùng trust manager có **PKIX trust validation** đúng; một trust manager luôn chấp nhận certificate hoặc các cấu hình vô hiệu hóa check có thể làm suy yếu/triệt tiêu bảo đảm, ngay cả khi đã chỉ định endpoint algorithm.

Đặt `"HTTPS"` là yêu cầu áp dụng cơ chế tên theo kiểu HTTPS trong JSSE. Không đặt thuật toán `null` hoặc `""` chỉ để đi qua lỗi tên. Nếu chứng thư có SAN sai, sửa hostname đích hoặc phát hành lại certificate có SAN đúng, không “tạm cho qua”.

### 7.3. SSLEngine, X509ExtendedTrustManager và ngữ cảnh TLS

Ở mức `SSLEngine`, cần cung cấp **peerHost** có ý nghĩa khi tạo engine và bật endpoint identification trên `SSLParameters`, đồng thời cấu hình trust manager đúng và thực hiện handshake qua transport với trạng thái SSL đầy đủ.

Một `X509ExtendedTrustManager` có phương thức `checkServerTrusted(chain, authType, SSLEngine)` và phiên bản nhận `Socket` để dùng **ngữ cảnh TLS** (khi được dùng đúng trong handshake). Điều này khác với việc gọi `X509TrustManager.checkServerTrusted(chain, "RSA")` trực tiếp ở code nghiệp vụ mà không truyền host/context: một lời gọi chỉ có `chain` và `authType` **không** tự biết URL được yêu cầu là gì.

**Sai lầm:** tự tạo `X509TrustManager` luôn chấp nhận mọi chain rồi dùng `HttpsURLConnection.setDefaultHostnameVerifier((host,session) -> true)`. Khi đó cả trust và danh tính có thể bị bỏ qua, mở đường MITM.

### 7.4. SNI không thay endpoint verification

**Server Name Indication (SNI)** là extension trong TLS ClientHello giúp server biết client muốn virtual host nào để chọn certificate phù hợp. SNI **không** thay thế bước client xác minh rằng certificate thực nhận có đúng SAN và trust chain.

Không sử dụng SNI do kẻ khác điều khiển như một nguồn danh tính thay cho URI đáng tin. Với reverse proxy, load balancer hoặc kết nối socket trực tiếp tới IP nhưng danh tính dịch vụ là DNS, cần cấu hình TLS peer host/reference identity sao cho **không đánh mất** tên DNS mà client thực sự muốn xác thực.

## 8. Khi truy cập bằng IP hoặc hệ thống có nhiều hostname

### 8.1. DNS đến IP không làm biến đổi reference identity

Nếu `billing.example.test` được DNS resolve thành `192.0.2.10`, nhưng client vẫn gọi URL theo hostname, danh tính để xác minh là `billing.example.test`, không phải số IP được resolver trả về. Bản ghi DNS có thể đổi IP theo thời gian mà certificate có SAN DNS vẫn đúng.

Nếu ứng dụng chủ động dùng `https://192.0.2.10` thì client kỳ vọng `iPAddress` SAN cho IP literal. Tên `DNS:billing.example.test` **không tự khớp** với một reference identity dạng IP. Bản ghi iPAddress dùng octet (IPv4 4, IPv6 16), không phải chuỗi text DNS.

### 8.2. Wildcard SAN và nhiều tên

Chứng thư có thể có nhiều `dNSName`:

```text
DNS:billing.example.test
DNS:reports.example.test
DNS:*.svc.example.test
IP Address:192.0.2.10
```

Chỉ tên nào khớp với **reference identity được yêu cầu** theo profile giao thức mới được chấp nhận. Wildcard `*.svc.example.test` không tự khớp `svc.example.test` hoặc `a.b.svc.example.test`. Một certificate chứa nhiều tên hợp lệ có thể được dùng cho nhiều endpoints, nhưng **private key leak** làm tăng phạm vi ảnh hưởng; cần quản lý provisioning, quyền phát hành và rotation.

### 8.3. Internationalized Domain Names (IDN)

Domain quốc tế hóa có Unicode ở giao diện người dùng, nhưng dữ liệu `dNSName` trong X.509 theo profile chứa ASCII IA5String (IDNA A-label như `xn--...` khi cần). Đảm bảo client, thư viện URI và TLS stack sử dụng cách chuẩn hóa/so khớp phù hợp với chuẩn. Không lấy `String.toLowerCase()` theo locale hoặc chỉ so suffix làm verifier.

### 8.4. Đừng lấy SAN của đối tác làm hostname để kết nối

Không nên xử lý như sau: “certificate có DNS SAN `api.example.test`, vậy ta coi đích hiện tại chính là `api.example.test`”, trong khi ứng dụng thực tế định gọi `billing.example.test`. Đây là đảo chiều của kiểm tra danh tính, biến claim trong chứng thư thành yêu cầu mà không so sánh với identity bên ngoài chứng thư.

## 9. Những yếu tố ngoài PKIX và hostname matching vẫn phải tính

### 9.1. Authorization và nhận diện dịch vụ

Ngay cả khi `billing.example.test` được chứng thư đại diện chính xác và TLS handshake an toàn, ứng dụng vẫn phải xác minh đối tác có **quyền thực hiện thao tác nghiệp vụ**: API scope, tenant, JWT audience, mutual TLS client identity, hạn mức, quyền đọc dữ liệu, chống replay/idempotency. Hostname verification không thay thế authorization.

### 9.2. mTLS và các dạng định danh khác

Với **mutual TLS**, server cũng kiểm chứng certificate của client. Identity của client có thể theo profile nội bộ: DNS SAN, URI SAN (chẳng hạn mô hình service identity), chứng thư người dùng hoặc ràng buộc khác được tổ chức định nghĩa. Không lấy một hostname ngẫu nhiên trong SAN của client certificate làm “người dùng được phép truy cập tất cả API”.

Hostname verification server HTTPS và xác thực danh tính **client mTLS** là hai hướng khác nhau của trust. PKIX chain vẫn là nền tảng, nhưng mỗi hướng cần **identity và authorization policy riêng**.

### 9.3. Certificate pinning có phải thay thế PKIX không?

Pinning chứng thư hoặc SPKI có thể bổ sung giới hạn tin cậy khi được provisioning qua kênh đáng tin, nhưng tạo bài toán rollover/cert renewal và backup pins. Không dùng “pin công khai” thu từ kết nối chưa xác thực như thể nó đã xác thực lần kết nối đầu tiên. Một pin cho tổ chức cũng không có nghĩa key được phép đại diện cho **mọi hostname/tenant**; vẫn cần binding hợp lệ theo policy.

### 9.4. Revocation và thời điểm đánh giá

Khi một private key server bị lộ, certificate còn hạn sử dụng và SAN có thể vẫn khớp. Nếu client không có cơ chế phát hiện thu hồi/compromise theo chính sách, hai kiểm tra “chain và hostname” có thể không đủ để phát hiện hiện trạng mới. Cần quản lý vòng đời chứng thư, trust updates, CRL/OCSP khi áp dụng và incident response.

### 9.5. Redirects, proxy và DNS cache

Khi HTTP client theo redirect từ `billing.example.test` sang một origin khác, phải có xác minh TLS đúng với **hostname mới**; đồng thời ứng dụng cần kiểm soát chính sách **được phép redirect sang đâu**. DNS caching, split-horizon DNS, service mesh hoặc proxy không phải lý do bỏ endpoint validation.

**Tách layer:** TLS xác nhận *endpoint identity*; ứng dụng quyết định có tin endpoint đó để xử lý dữ liệu/credentials hay không. Một redirect đến server có certificate hoàn toàn hợp lệ vẫn có thể không phù hợp chính sách gửi bearer token của ứng dụng.

## 10. Bảng quyết định: path và hostname là hai phép kiểm độc lập

| PKIX trust/path | Hostname/IP identity | Kết quả phải xử lý | Ý nghĩa |
| --- | --- | --- | --- |
| **Đạt** | **Khớp** | Có thể chấp nhận **nếu** các điều kiện TLS/policy khác đạt | Cả nguồn trust và đúng endpoint đã được kiểm. |
| **Đạt** | **Không khớp** | **Từ chối** | Certificate hợp lệ nhưng thuộc server khác. |
| **Không đạt** | **Khớp** | **Từ chối** | SAN đúng nhưng chứng thư không được tin/đã hết hạn/bị lỗi path/policy. |
| **Không đạt** | **Không khớp** | **Từ chối** | Cả trust và identity đều không đạt. |
| **Không kiểm** | **Khớp** | **Từ chối** | So hostname trên certificate chưa được tin là không đủ. |
| **Đạt** | **Không kiểm** | **Từ chối** | Bỏ hostname verification không được thay bằng PKIX. |

Không dùng cờ `ignoreSslErrors` hoặc một trust manager luôn true để chuyển các ô “từ chối” thành “chấp nhận”. Trong bài test, phải dùng CA test và server test cô lập thay vì làm yếu cấu hình toàn cục của JVM.

## 11. Trường hợp sai thường gặp khi viết Java

| Triệu chứng / anti-pattern | Vì sao chưa đúng | Cách xử lý |
| --- | --- | --- |
| Gọi `CertPathValidator.validate` xong rồi coi đúng server | Validator không nhận reference hostname HTTPS | Kết hợp endpoint verification của JSSE. |
| Gọi `X509Certificate.verify(caKey)` rồi chấp nhận | Chỉ kiểm chữ ký certificate bằng key caller đưa vào | Xác minh chain, trust anchor, usage, hostname. |
| `new TrustAnchor(anyCert, null)` với cert do remote gửi | Client tự tin một root do attacker kiểm soát | Trust anchors phải được provision đáng tin. |
| `CertificateFactory` parse được certificate | Parse không xác minh certificate chain hay name | Dùng JSSE/PKIX phù hợp, không dùng parser làm trust verifier. |
| `getSubjectAlternativeNames()` thấy một DNS name rồi chấp nhận | Chỉ liệt kê SAN, chưa so với hostname mong đợi | Sử dụng endpoint matcher theo profile. |
| SAN đúng nhưng chain fail | Root/intermediate thiếu, bị thu hồi, hết hạn, sai policy | Sửa PKI/truststore/cert chain; không tắt trust. |
| Chain đúng nhưng hostname fail | Leaf cho DNS/IP khác, wildcard không phù hợp | Sửa SAN certificate hoặc tên endpoint tin cậy. |
| Chỉ có `CN=billing.example.test` | CN không phải reference identity hợp lệ theo RFC 9525 | Phát hành SAN dNSName đúng. |
| URL dùng IP nhưng certificate chỉ có `DNS:IP-text` | Không đúng type `iPAddress` | Thêm IP SAN hoặc dùng hostname được xác thực đúng cách. |
| Gọi `SSLSocket.startHandshake` không bật endpoint identification | TLS thấp tầng có thể không so hostname mặc định | Dùng `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")`. |
| Cài custom `HostnameVerifier` luôn true | Bỏ qua mismatch, gây MITM | Gỡ bypass, sửa chứng thư và endpoint. |
| Tắt revocation trong production “vì demo chạy” | Thay đổi độ an toàn theo policy, bỏ kiểm trạng thái thu hồi | Định nghĩa CRL/OCSP và failure behavior phù hợp. |
| Dùng SNI như bằng chứng endpoint identity | SNI giúp server chọn cert, không kiểm cert phía client | Vẫn phải xác minh SAN sau handshake. |
| Coi mTLS client certificate là quyền truy cập tự động | Trust/identity khác authorization | Kiểm tra role/scope/tenant sau mTLS. |
| Kết nối đến proxy/IP và không giữ hostname logic | Peer identity sai hoặc không được kiểm | Cấu hình reference identity đáng tin cho TLS. |

### 11.1. Lỗi và dấu hiệu cụ thể

- `CertPathValidatorException` với nguyên nhân path validation: có thể do signature, validity, policy, trust anchor hoặc revocation. Không tự kết luận đây là hostname mismatch.
- `SSLHandshakeException` trong JSSE: có thể bọc lỗi PKIX **hoặc** lỗi endpoint identity. Đọc root cause và diagnostics không tiết lộ secret.
- `CertificateException` với thông tin kiểu “No subject alternative DNS name matching ...” hoặc “No subject alternative names present”: thường hướng tới lỗi tên/loại SAN, nhưng thông điệp chính xác phụ thuộc JDK/provider.
- Lỗi kiểu “unable to find valid certification path”: thường hướng tới trust anchors/chain building, **không phải** bằng chứng hostname đúng/sai.
- Nếu test chỉ gọi `CertPathValidator` và trả thành công, **không** có bằng chứng rằng hostname verification đã chạy.

Debug nên ghi **hostname/IP tham chiếu đã được chọn**, dạng SAN công khai, issuer/trust policy, trạng thái chứng thư và exception category; không log private keys, session secrets, bearer tokens hoặc dữ liệu riêng tư.

## 12. Kiểm thử hai chiều để không bỏ sót lỗ hổng

### 12.1. Test matrix tối thiểu

Dùng một test CA được tin **chỉ trong môi trường test** và các leaf certificates tùy biến. Không thêm CA test vào production truststore.

| Ca | Certificate chain | DNS/IP SAN của leaf | Đích client mong đợi | Kỳ vọng |
| --- | --- | --- | --- | --- |
| 1 | Trust hợp lệ | `DNS:billing.example.test` | `billing.example.test` | TLS được chấp nhận nếu các policy khác đạt |
| 2 | Trust hợp lệ | `DNS:service.example.test` | `billing.example.test` | TLS phải từ chối **name mismatch** |
| 3 | Self-signed không tin cậy | `DNS:billing.example.test` | `billing.example.test` | TLS phải từ chối **untrusted chain** |
| 4 | Hết hạn | `DNS:billing.example.test` | `billing.example.test` | TLS phải từ chối |
| 5 | Trust hợp lệ | `IP:192.0.2.10` | `192.0.2.10` | Khớp IP-ID nếu policy khác hợp lệ |
| 6 | Trust hợp lệ | `DNS:192.0.2.10` | `192.0.2.10` | Từ chối vì sai loại SAN |
| 7 | Trust hợp lệ | `DNS:*.svc.example.test` | `a.svc.example.test` | Có thể khớp một nhãn theo RFC/profile |
| 8 | Trust hợp lệ | `DNS:*.svc.example.test` | `a.b.svc.example.test` | Từ chối vì wildcard vượt một nhãn |
| 9 | Trust hợp lệ | Chỉ CN đúng, không có SAN | `billing.example.test` | Từ chối theo RFC 9525 |
| 10 | Chain hợp lệ cho đúng hostname | Certificate sai EKU/KeyUsage TLS server | `billing.example.test` | Từ chối theo TLS policy |

**Điểm test quan trọng nhất của bài:** tạo một chain **đạt PKIX** nhưng SAN **không** khớp hostname. Đây là ca mà unit test chỉ gọi `CertPathValidator` thường bỏ qua. Demo Java ở trên tạo ra tình huống này ở lớp certificate; integration test qua JSSE cần xác nhận handshake **bị từ chối**.

### 12.2. Test qua JSSE thật, không chỉ qua getter

Một integration test đúng phải chạy một **local TLS server có private key tương ứng leaf certificate**, đồng thời client có truststore chỉ tin test CA và hostname reference được cấu hình đúng. Khi client kết nối bằng hostname cố định trong kịch bản, kiểm tra:

- Chain đúng + hostname đúng → handshake được chấp nhận.
- Chain đúng + hostname sai → `SSLHandshakeException`/`CertificateException` tùy provider.
- Chain sai + hostname đúng → phải từ chối.
- Khi thử `SSLSocket`, bật `setEndpointIdentificationAlgorithm("HTTPS")` để kiểm tra mục tiêu của bài.

Dùng DNS test host mapping/local resolver thích hợp hoặc cấu hình peer host đúng trong SSLEngine/SSLSocket; không dùng một chứng thư self-signed không được tin để chứng minh lỗi hostname vì khi đó handshake đã có thể bị chặn **trước** name comparison.

### 12.3. Các câu hỏi bắt buộc khi QA bảo mật

- Hostname để verify đến từ đâu? Có phải đích mà ứng dụng lựa chọn, không phải field từ attacker?
- Client có sử dụng TLS trust manager đáng tin và anchor đúng không?
- Endpoint identification có thực sự bật cho từng loại client, từng đường code, từng môi trường không?
- Các API có retry/fallback sang “insecure mode” khi gặp lỗi SSL không?
- Proxy/redirect/reverse proxy có làm thay đổi reference identity hoặc gửi credentials sang origin khác không?
- Có kiểm SAN DNS type 2 và IP type 7 đúng cách không?
- Có tránh CN fallback, wildcard quá rộng, IDNA không nhất quán không?
- Có kiểm certificate validity/revocation/algorithm/EKU theo policy không?
- Có test negative chain-valid-but-host-wrong, cùng với negative host-right-but-chain-invalid không?

## 13. Bài tập và câu hỏi tự kiểm tra

1. `CertPathValidator.validate` nhận những loại input nào? Vì sao nó không thể tự biết bạn đang gọi URL `billing.example.test`?
2. Trong demo, vì sao `PKIX path valid: true` đồng thời với `Different target DNS listed: false`?
3. Nếu đưa một leaf certificate được CA tin cậy ký cho `other.example.test` vào HTTPS client kết nối `billing.example.test`, phải chấp nhận hay từ chối?
4. Phân biệt `X509Certificate.verify`, `CertPathBuilder`, `CertPathValidator`, `X509Certificate.getSubjectAlternativeNames` và JSSE endpoint identification.
5. `TrustAnchor` do ai lựa chọn? Vì sao “client tự tin root do server gửi” là sai?
6. Nếu `PKIXParameters.setRevocationEnabled(false)` dùng trong demo offline, điều đó có nói gì về an toàn khi triển khai thật?
7. Với `https://192.0.2.10`, hostname identity được kiểm với SAN loại nào? Vì sao `DNS:192.0.2.10` không thay thế?
8. Khi nào một wildcard DNS-ID có thể khớp một hostname? Vì sao không dùng `String.endsWith`?
9. `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")` phải được dùng ở đâu trong luồng `SSLSocket`?
10. Nếu dùng HTTP client cấp cao mà lập trình viên gắn trust manager luôn true và hostname verifier luôn true, còn bảo đảm gì từ PKIX/hostname?
11. Vì sao SNI không chứng minh chứng thư đã được xác minh danh tính?
12. Khi cấu hình mTLS, bước PKIX của client certificate có tự đủ để gán mọi quyền nghiệp vụ không?

**Bài tập A:** chạy `PkixHostnameDemo` và ghi lại hai kết quả `PKIX path valid`, `Different target DNS listed`. Giải thích “thành công ở lớp certificate path” và “không thỏa hostname” không hề mâu thuẫn.

**Bài tập B:** giữ nguyên test CA/leaf, nhưng giả sử endpoint khác là `service.example.test`. Mô tả vì sao bước **reference identity** thay đổi trong khi PKIX path validation vẫn như cũ. Không lấy SAN từ certificate làm URL mong muốn.

**Bài tập C:** dùng local TLS server/truststore test để tạo tình huống PKIX hợp lệ nhưng hostname sai, rồi bật/tắt `setEndpointIdentificationAlgorithm("HTTPS")` trong **môi trường lab cô lập**. Đối chiếu sự khác biệt, sau đó luôn trả cấu hình về an toàn. Không đưa chế độ tắt kiểm tra vào production.

**Bài tập D:** thiết kế test matrix có chứng thư chỉ có CN, certificate có IP SAN, cert hết hạn và certificate chain từ CA không được trust. Chỉ một trong hai điều kiện PKIX/name đạt vẫn phải bị từ chối.

## 14. Checklist trước khi deploy

- [ ] Chỉ tin các root/intermediate/trust anchors theo PKI policy của tổ chức.
- [ ] Dùng HTTPS client/JSSE tin cậy, có endpoint identification hoạt động trên **mọi** đường code cần TLS server authentication.
- [ ] Reference hostname/IP được chọn từ endpoint đáng tin, không phải từ thông tin certificate/server tự gửi.
- [ ] SAN dNSName/iPAddress đúng kiểu, wildcard đúng quy tắc, không dựa vào CN fallback.
- [ ] Không có trust-all manager, `HostnameVerifier` luôn true, insecure fallback hoặc flags vô hiệu hóa identity verification.
- [ ] Kiểm thuật toán, độ mạnh khóa, validity, usage, policies, revocation theo chuẩn và chính sách.
- [ ] Có negative test PKIX-pass/hostname-fail cùng hostname-pass/PKIX-fail.
- [ ] Phân biệt hostname verification, TLS cryptographic authentication và **authorization** nghiệp vụ.
- [ ] Có quy trình certificate rotation, cấu hình truststore, quản lý proxy/redirect/SNI và test nhiều environment.
- [ ] Không dùng root/leaf demo của bài học làm chứng thư hoặc trust anchor production.

## 15. Tài liệu chính thức

- [RFC 5280 — Internet X.509 PKI Certificate and CRL Profile](https://www.rfc-editor.org/rfc/rfc5280): đường chứng thư, trust anchors, extensions, policies và tên trong SAN.
- [RFC 9525 — Service Identity in TLS](https://www.rfc-editor.org/rfc/rfc9525): đối chiếu reference identity với presented identity, DNS-ID/IP-ID, wildcard, không dùng Common Name RDN.
- [CertPathValidator — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertPathValidator.html): `getInstance("PKIX")`, `validate(CertPath, CertPathParameters)`.
- [CertPathBuilder — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertPathBuilder.html): tìm đường chứng thư theo policy.
- [PKIXParameters — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/PKIXParameters.html): trust anchors, revocation, date và các chính sách path.
- [TrustAnchor — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/TrustAnchor.html): biểu diễn CA/root tin cậy.
- [X509Certificate — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/X509Certificate.html): SAN và metadata chứng thư.
- [SSLParameters — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLParameters.html): `setEndpointIdentificationAlgorithm("HTTPS")`.
- [X509ExtendedTrustManager — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/X509ExtendedTrustManager.html): trust checks với ngữ cảnh `Socket` hoặc `SSLEngine`.
- [HttpClient — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.net.http/java/net/http/HttpClient.html): HTTPS client và cấu hình TLS.


</details>

- [Quay lại đầu trang](#back-to-top)
